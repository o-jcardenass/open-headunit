# headunit-info round 3 addendum: the FAIL was the brief's, not the branch's

**Written after** `headunit-info-round3-results.md`, which stands as measured. Nothing in the runs is
disputed here. What changes is which log line answers the question the round asked, and three
conclusions the results drew from the wrong field.

**Source:** three independent bytecode passes over `com.google.android.projection.gearhead`
`17.5.663204-release`, the same APK round 2's analysis used. Every claim below names the method it
came from.

---

## The short version

R3 and R4b were **not failures**. The brief told the operator to read
`GH.WIRELESS.SETUP: headUnitCarInfoInternal`, and that line does not carry the vehicle type. R3's
own observable evidence, the full app rail, a car puck on the map, `GH.CarMicRecorder` ×4 and a
working head-unit capture, is what a correctly resolved car session looks like, and it is what the
round should have been asked for.

The identity split works. The branch is unchanged as a result of this addendum.

---

## A1. There are two lookups, keyed on two different things

| | Reconnect lookup | Vehicle-type lookup |
|---|---|---|
| logged as | `getCarInfoInternal for BT device`, `headUnitCarInfoInternal` | nothing quotable; `Vehicle found.` / `Vehicle not found!` |
| entry | `Lpaj;->a(String)` → `Lphk;->a` → `Ljlo;->c` | `Ljoh;->run()` → `Ljlo;->a` |
| SQL | `SELECT * FROM allowedcars ORDER BY connectiontime DESC`, **selection args null** | `WHERE manufacturer = ? AND model = ? AND modelyear = ? AND vehicleid = ?` |
| key | **Bluetooth address only**, compared in Java against `CarInfoInternal.f` | make, model, model year, and the **hash** of the announced vehicle id |
| feeds | wireless setup: credentials, `known`, `projectionAllowed` | the vehicle type in effect for the session |

There is no `WHERE` and no `LIMIT` on the first one; the Java-side first match makes it an effective
`LIMIT 1` after filtering. The announced `vehicle_id` is not read anywhere on that path.

**The stamp of stored type over declared type is in `Ljoh;->run()` and happens only when the hash
lookup hits** (`0x1f6`: `iget CarInfo->r` from the stored row, `iput` onto the live one). On a miss it
logs `Car does not exist in the db` and stamps nothing.

So a `-moto` id changes the hash, the hash lookup misses, and our declared motorcycle survives to
projection start. That is R2b. R3 announcing the base id hits the car row and is stamped CAR, which
is exactly why R3 behaved as a car.

## A2. The 16-hex `vehicleId` is a random number, not a hash of anything we send

`CarInfoInternal.toString()` prints `,vehicleId=` from `CarInfo.d`, which is the column
`vehicleidclient`. `Ljlo;->w` mints it at every INSERT:

```
0014: new-instance v1, Ljava/security/SecureRandom;
001e: invoke-virtual v1, Ljava/security/SecureRandom;->nextLong()J
0026: invoke-static v1, v2, Ljava/lang/Long;->toHexString(J)
```

The real hash is the `vehicleid` column and the same `toString()` prints it as
`,hashedVehicleId=[REDACTED]]`. So the results' "the announced `vehicleId` does not hash stably,
`VEHID4C` produced three different hashes" is three INSERTs producing three random 64-bit values. It
is not evidence about our id at all, and the conclusion drawn from it does not follow.

**What the real hash is** (`Ldsg;->J`): `base64(SHA-256(vehicle_id ‖ android_id ‖ subject))`, where
`android_id` is `Settings.Secure.getString("android_id")` on the phone and `subject` is the head
unit's TLS certificate Subject DN, taken from `SSLSession.getPeerCertificates()`. No salt and no
nonce; I looked. The announced id is the only one of the three we control, which is precisely why
`VehicleIdentityPolicy` can move the lookup.

## A3. `ORDER BY connectiontime DESC` explains R4a

The results flagged R4a as unexplained: it announced truck with `dbId=1` and `dbId=2` both present
and the BT lookup returned the *older* row. `connectiontime` has exactly two writers, `Ljlo;->w` (the
INSERT) and `Ljlo;->a` (the UPDATE inside `Ljoh;->run()`), and the second selects its row by **hash**,
not by Bluetooth address. So the row whose timestamp gets bumped during a session is often not the
row that session's BT lookup returned, and the two diverge by one run:

| Run | BT lookup returns | hash lookup does | newest row afterwards |
|---|---|---|---|
| R1 | `dbId=1` | hits 1, bumps it | 1 |
| R2b | 1 | misses, INSERTs 2 | 2 |
| R3 | **2** ✔ as logged | hits 1, bumps it | 1 |
| R4a | **1** ✔ as logged | misses, INSERTs 3 | 3 |
| R4b | **3** ✔ as logged | | |

The rule is "most recently stamped", not "newest inserted". Every observed value falls out of it.

## A4. `No audio/mic` has two implementations and only one reads the microphone

This is why the branch's own comments have described it two different ways. Both strings exist in
`classes.dex`; neither analysis was wrong about its own site.

**`Ljon;->run()`** (modern). Two consecutive gates. A sink gate over `Lirb;->w [Liqa;`, three slots,
first non-null passes, failure logs `No audio playback service`. Then a **single null test** on
`Liwz;->c Lixa;`, failure logs `No audio/mic`. Both gates take `Liuc;->e()Z` as an escape, and that
method is exactly `CarInfo.r == 3`:

```
0018: iget v1, v1, Lcom/google/android/gms/car/CarInfo;->r I
001c: const/4 v0, 3
001e: if-ne v1, v0, +004h
```

`Liwz;->c` is written in one straight-line block in `Liwz;->a(Lxnz;)` from `Lxnz;->g Lxkp;`, the
**audio source** submessage, guarded by the log `car microphone already discovered.` and the
`car_save_mic` preference. It is null exactly when a head unit omits the microphone service.

**`Lqso;->c()`** (legacy, hosted by `CarChimeraService`). `No audio/mic` fires when all three **audio
sink** slots `Lqqv;->e [Lrek;` are null. No microphone reference, no `CarInfo.r` read, no vehicle-type
escape anywhere near it.

Nothing static in the APK says which service is selected at runtime. **The head unit therefore keeps
claiming motorcycle whenever it withholds the microphone: required under `Ljon;`, inert under
`Lqso;`.**

## A5. The recorder decision is confirmed, and it has no back door

One decision site in the whole APK, `Lwxq;->a()V`, one write to `GhMicrophoneContentProvider.d`, one
occurrence of `Using phone microphone`. The vehicle-type term is genuinely first and genuinely
short-circuits: `const/4 v7, 3` / `if-eq`, and the fall-through sets the result false and jumps past
the `ICarAudio` query entirely.

Two things worth having on the record because they came up reading R5:

- **The HFP/SCO route is downstream, not a cause.** `Lkxr;` is the only class in the APK that calls
  `BluetoothHeadset.startVoiceRecognition` and `AudioManager.startBluetoothSco`, and it is
  constructed *inside* the phone-recorder branch. R5's `HeadsetService: startVoiceRecognition` is a
  consequence of having already chosen the phone recorder.
- **Every car-path failure lands on a no-op recorder, never the phone.** `SecurityException` and the
  `CarNotSupported` / `CarNotConnected` family all resolve to `Lkxi;`, which logs and returns null.

Also: Gearhead must itself hold `RECORD_AUDIO` or the choice is bypassed for `Lkxi;`; and the
model-string fallback in `Ldsg;->C` can only ever synthesise car or truck, never motorcycle, so
`HeadUnitInfo` field 9 is the only way to claim one.

## A6. R5's stored record was not TRUCK

R5 logged `Using phone microphone`, which by A5 requires `CarInfo.r == 3` at projection start. So the
`dbId=3 TRUCK` the results quote is the BT lookup's row, not what was in effect. **R5 does not refute
A4**, and the riding configuration passed for the reason the round thought it did.

---

## What is still open, and what would close it

All four run against captures already on the rig. No session, no rebuild, no new APK.

1. **Which car service ran.** `grep -c "No audio playback service"`. The string exists only in
   `Ljon;`. Corroborate with `createAndDiscoverServices` (modern) against
   `bluetooth endpoint is missing.` (legacy).
2. **Whether the stamp fired, per run.** `grep -n "Vehicle found\.\|Vehicle not found!\|Car does not exist in the db"`.
   Expect a miss on R2b and R5 and a hit on R1, R3 and R4b. That is the direct measurement of the
   identity split doing its job, and it is the line the round 3 brief should have asked for.
3. **Whether our TLS subject is stable.** `grep -n "Saving vehicle ID"` counts INSERTs. If they
   outnumber the announced-id changes, our certificate is varying and the hash rotates on its own.
4. **R5's full app rail** where R2b on the same announced id showed the stripped one. `Not showing
   dashboard during projection start on motorcycles` was absent from every capture including R2b, so
   R2b's stripped rail may be a first-session artifact rather than the vehicle type. Cosmetic either
   way.

## What changed on the branch because of this

Comments, KDoc, one log line and one settings string, no logic. `fix/mic-and-vehicle-type` is now
five commits (regrouped by component, tree unchanged) with the mechanism stated correctly, and the
nine strings this work added or rewrote now ship in all twenty language directories instead of
English only.
