# mic-uplink, round 3 brief: the service starts in the background and still gets the microphone

**Candidate (C):** `fork/fix/mic-uplink` @ `dcef0500` (8 commits on top of `fix/audio-start-and-teardown`).

```bash
git fetch fork
git checkout -B mic-uplink fork/fix/mic-uplink   # must print dcef0500...
git rev-parse HEAD
```

**One arm. There is no baseline this round.** M6a's failure was a regression against `main`, and
round 1 already measured `main` behaving correctly at that point, so an arm-A build would only
re-measure a known-good state. One APK.

---

## 0. Why this round exists, and why it is short

Round 2 validated the microphone work end to end and left one thing outstanding: **M6a, which failed
5 out of 5 in round 1 and blocks the branch.** A background start that claimed the microphone
foreground-service type was refused by Android 14 with `SecurityException: ... the app must be in the
eligible state/exemptions`, **with `RECORD_AUDIO` granted**. The type is while-in-use, so the
permission was the right question asked at the wrong moment.

`dcef0500` moves the claim. The service now starts with `CONNECTED_DEVICE or MEDIA_PLAYBACK`, exactly
as `main` does and exactly what cannot be refused, and the microphone type is added when capture
actually opens, by which time the projection is on screen and the app is eligible. It is dropped
again when capture closes.

Four runs, all on one APK, all short. This round exists to prove the failure is gone without having
introduced a new one in its place.

**Not re-run:** anything from round 2. M1 to M5 and G1 all passed and none of them is touched by this
change except through the microphone still opening, which R2 below covers.

---

## 1. What changed in the code

| Piece | What it does now |
|---|---|
| `ForegroundServiceTypePolicy` | Splits into `baseTypeMask` (the start, no while-in-use type) and `withMicrophone` (capture). The old `typeMask` is gone; its inputs were the bug |
| `AapService.onCreate` / `onStartCommand` | Both claim `baseTypeMask`, so the two agree and neither can be refused |
| `AapService.promoteForMicrophone` / `demoteAfterMicrophone` | Re-call `startForeground` with and without the microphone type |
| `MicRecorder.start` / `stop` | Claims before opening capture, releases after closing it. A refusal is the new `ERROR_NO_FOREGROUND_TYPE` (-6) |
| `AapControl` | Unchanged. Its existing `capture did not start (code -6)` branch declines to the phone |

---

## 2. Settings keys

| Key | Type | Value | Runs |
|---|---|---|---|
| `log-level` | int | `1` (DEBUG) | R1, R3, R4 |
| `log-level` | int | `0` (VERBOSE) | R2 |
| `log-source` | int | `0` (LOGCAT) | all |
| `use-head-unit-microphone` | bool | `true` | all |
| `mic-sample-rate` | int | `16000` | all |
| `mic-input-source` | int | `0` (DEFAULT) | all |
| `enable-audio-sink` | bool | `true` | all |
| `wifi-connection-mode` | int | `3` (Native AA) | all |
| `native-ap-transport` | int | `0` (WiFi Direct) | all |
| `key-codes` | (string set) | **absent** | all |

Auto-start must be on and pointed at the Motorola, or R1 has nothing to trigger. Round 2 found
`auto-start-bt-macs` and `auto-start-bt-name` still holding the old phone's identity; confirm both
name the Motorola (`A0:46:5A:97:E4:95`) before starting, and report what they held.

**Do not use `set_hu_pref.sh`** (`TESTING-TEMPLATE.md` §7a): it relaunches through
`install_and_launch.sh SKIP_BUILD=1` and can reinstall the wrong APK. Edit `settings.xml` directly.

---

## 3. The lines that decide the round

New on this build, INFO:

```
AapService: claimed the microphone foreground-service type for this capture
AapService: the microphone foreground-service type was not asked for; the permission or the setting says no
AapService: could not claim the microphone foreground-service type
AapService: could not drop the microphone foreground-service type
```

The failure this round exists to retire, and the lines around it:

```
ForegroundServiceStartNotAllowedException/Exception caught in onCreate:
ForegroundServiceStartNotAllowedException/Exception caught in onStartCommand:
java.lang.SecurityException
Starting FGS with type microphone
MATCH! Starting AapService via Bluetooth Auto-start
```

The setup steps `onCreate` used to skip, which are how you tell a promoted service from a stopped
one without reading the whole capture:

```
AapService: filled in this device's Bluetooth address
Handshake: SSL handshake complete
Mic request: open: true
mic uplink started
mic uplink |
Mic request: capture did not start (code -6)
```

Grep everything with `-a`, no exceptions.

---

## 4. Runs

### R0. Gate

Build and unit-test the one arm. Expected **836** tests, up 3 from round 2's 833:
`ForegroundServiceTypePolicyTest` goes from 5 to **8**. A different number means the wrong SHA.

Report the APK md5 and keep the APK; every run below uses the same one.

### R1. The point of the round: five background starts

`log-level=1`. Five cycles, each from a genuinely stopped service.

Per cycle:

```bash
adb shell am force-stop com.andrerinas.headunitrevived
# confirm the service is gone before triggering
adb shell dumpsys activity services com.andrerinas.headunitrevived | grep -c AapService
# then cycle the HEAD UNIT's own Bluetooth adapter, not the phone's (§7a)
adb shell svc bluetooth disable      # self-reverts in ~14s on this rig
```

Round 1 established that cycling the **phone's** radio produces no `ACL_CONNECTED` here and cycling
the head unit's own does. Corroborate each trigger from the phone's own
`GH.WifiBluetoothRcvr: Connection action: ... ACL_CONNECTED` so the run is not resting on our own
receiver to prove our own receiver fired.

Per cycle, count:

```bash
grep -ac "MATCH! Starting AapService"                       r1-<n>.txt
grep -ac "caught in onCreate"                               r1-<n>.txt
grep -ac "caught in onStartCommand"                         r1-<n>.txt
grep -ac "Starting FGS with type microphone"                r1-<n>.txt
grep -ac "filled in this device's Bluetooth address"        r1-<n>.txt
```

- **PASS:** all five cycles show `MATCH!` and **zero** of the three failure counts. This is the whole
  point of the round.
- **FAIL:** any `SecurityException` or `caught in onCreate` on any cycle. Attach the whole capture and
  stop; the rest of the round is moot.
- **Also report, and this is the half round 1 could not see:** whether the service stayed up. Report
  `dumpsys activity services com.andrerinas.headunitrevived` for each cycle, specifically whether
  `AapService` is listed as foreground, and whether a session formed (`SSL handshake complete`).
  Round 1's failing build formed a session anyway on a service that had called `stopSelf()`, so
  "a session formed" is **not** on its own evidence the fix worked. The absence of the exception is.

`filled in this device's Bluetooth address` is not expected to fire on this rig (round 1 and round 2
both found `BluetoothHelper.getBluetoothMacAddress()` returns empty here, which is the intended
leave-it-alone branch). It is listed because it runs immediately after the catch that used to
`return`, so if it ever does fire it proves `onCreate` ran to completion. Report it either way.

### R2. The microphone still opens on a background-started session

`log-level=0`. One cycle, started the same way as R1, then one assistant session using round 2's
route 1:

```bash
adb shell am broadcast \
  -n com.andrerinas.headunitrevived/com.andrerinas.openheadunit.app.RemoteControlReceiver \
  -a com.android.music.musicservicecommand --es command voice
```

Speak a sentence. Remember one trigger can produce several sessions on this phone (§7a); count
`START`/`STOP` pairs, not broadcasts.

- **PASS:** `Mic request: open: true`, then
  `AapService: claimed the microphone foreground-service type for this capture`, then
  `mic uplink started` and a `mic uplink |` summary with a non-zero frame count.
- **FAIL:** `could not claim the microphone foreground-service type`, or
  `capture did not start (code -6)`, on a session where the projection was on screen. That is the fix
  claiming the type too late or in the wrong state, and it is the risk this design carries.
- Report the claim line's position relative to `Mic request: open: true`. It must come after.

### R3. The type is released again

Free from R2's capture; no extra session needed.

- **PASS:** every `mic uplink |` summary is followed by a `demote` with no error, meaning no
  `could not drop the microphone foreground-service type` appears anywhere, and the service is still
  listed as foreground in `dumpsys activity services` after the session ends.
- Report the count of `claimed the microphone foreground-service type` against the count of assistant
  sessions. They should match: one claim per capture, not one for the whole run.
- **FAIL:** the service is no longer foreground after a microphone session, which would mean the
  demote dropped it entirely rather than narrowing it.

### R4. The decline path

`log-level=1`. One cycle, then deny the app-op before triggering the assistant:

```bash
adb shell appops set com.andrerinas.headunitrevived RECORD_AUDIO ignore
# ... run one assistant session ...
adb shell appops set com.andrerinas.headunitrevived RECORD_AUDIO foreground
```

`pm revoke` does not work on this ROM (§7a) and `foreground` is the pre-round default, not `allow`.

- **PASS:** the session forms and stays up; the microphone request is declined with a
  `Mic request: capture did not start (code -3)` (the app-op denial is caught by `MicRecorder`'s own
  permission check, which runs **before** the claim, so -3 and not -6); and the service is still
  foreground afterwards.
- **FAIL:** the service stops, or no `Mic request:` decline is sent at all, which would leave the
  phone waiting on a stream that never arrives.
- Report which code appeared. If it is -6 rather than -3 the two checks are in the wrong order, which
  is worth knowing even though both decline correctly.

---

## 5. Report back

1. **R1's three counts, per cycle.** Zero exceptions across five cycles is the result that unblocks
   the branch. Say explicitly whether the service was foreground each time, separately from whether a
   session formed.
2. **R2:** whether the claim line appeared, and whether `mic uplink |` followed it.
3. **R3:** claims against sessions, and whether the service survived the demote.
4. **R4:** which decline code the phone was sent.

Setup notes as always: scripts used, the `settings.xml` delta at round start, what `auto-start-bt-*`
held before you changed it, any discard-rule hits with their cause, and anything substituted for a
step here.
