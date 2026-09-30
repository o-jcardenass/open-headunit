# headunit-info — round 3: the path a real user takes

**Candidate:** `fork/fix/mic-and-vehicle-type` @ `283659f4` (16 commits `main..HEAD`)
**Baseline:** none. Round 2 measured `40390cf7`, two commits back, and that is the comparison.
**Needs:** the intercom is **not** needed for R1 to R4. Keep it disconnected until R5.

---

## 0. Why there is a round 3 when round 2 passed everything

Round 2 was right about what it measured and wrong about what that proved. Its arm was the whole
branch, so the microphone service was withheld **and** a motorcycle was claimed at the same time, and
it could not tell which one moved the recorder. It also forgot the car on the phone before starting,
which is what this round exists to stop doing.

The Gearhead bytecode has since answered both questions, and the answers change the branch:

- **The phone-microphone decision is a conjunction whose first term is the vehicle type.** On
  anything but a motorcycle the microphone list is never even queried, so withholding the service is
  invisible. Motorcycle is required.
- **Withholding the service is only legal under a motorcycle.** Connection setup aborts with
  `No audio/mic` when the head unit announces no microphone service under any other type, and that
  abort fires before projection start. The two changes are inseparable.
- **The phone records a vehicle type the first time it sees a head unit and never updates it.** The
  key is make, model, year and vehicle id. Only an INSERT writes the type; the reconnect UPDATE omits
  it and the stored value is stamped back over whatever we declare.

That last one is the defect. A user who has already paired with the microphone on has a stored `CAR`
record. Turning the microphone off makes us withhold the service and claim a motorcycle, the phone
throws our claim away, reads `CAR`, and aborts. **Both previous rounds hid this by clearing the
record first**, so nobody has ever run the sequence a user would.

The branch now announces **one vehicle id per vehicle type**, so each type is inserted once under its
own entry. A car keeps the id the user set. This round is that fix, and it is the only round where
**not** clearing the phone's record is the whole point.

There is no negative control available. The old failure can no longer be produced from settings,
because the microphone setting now overrides the vehicle type. Reproducing it would need a scratch
build, and the bytecode already says what it does.

---

## 1. What changed since `40390cf7`

Two commits.

`1a320aae` — the vehicle type becomes the user's choice with the microphone setting overriding it to
motorcycle, and each announced type carries its own vehicle id (`VehicleIdentityPolicy`: a car keeps
the base id, a truck gets `-truck`, a motorcycle gets `-moto`). Four comments that stated the wrong
mechanism are corrected.

`283659f4` — a Car / Truck / Motorcycle picker in Vehicle Information, a `vehicle-type` preference
included in settings backups, a banner shown while the microphone is off, and the two microphone
strings rewritten because both halves of what they told the user were false.

---

## 2. Settings

Start from the round 2 backup and change only these.

| key | value | why |
|---|---|---|
| `log-level` | `1` | verbose, as every round |
| `enable-audio-sink` | `true` | R5 turns it off |
| `use-head-unit-microphone` | `true` | **R1 establishes the car record first** |
| `vehicle-type` | absent | the new key, an `int`; absent means Car |
| `vehicle-make` / `vehicle-model` / `vehicle-year` / `head-unit-make` / `head-unit-model` | as round 2 | the identity must not move |
| `vehicle-id` | `VEHID4C` | **the same id round 2 used**, deliberately |
| `wifi-direct-band` | **`0`** | see below |

**`wifi-direct-band` must not stay at `1` this round.** Every run here is a reconnect, and round 2's
reconnect came back INCONCLUSIVE because the Motorola would not rejoin a 5 GHz-only group. `0` is the
unrestricted ladder with a fallback. If reconnects still need a Bluetooth nudge, that is fine and
expected: log each one. If you change the value again, record what you used.

**`vehicle-id` stays `VEHID4C` on purpose.** Round 2 left a record under it. R1 overwrites the type on
that record by connecting as a car, which is the starting state a real user is in.

---

## 3. Do not forget the car

**This is the one instruction that matters.** Do not forget the car in Android Auto at any point in
R1 to R4, do not `pm clear` Gearhead, and do not invent a fresh `vehicle-id`. Both previous rounds
did one of those and that is why this defect survived two rounds.

If a run fails in a way that seems to need a clear, stop and report rather than clearing. A failure
here is the result.

---

## 4. Carried forward from round 2

- `CAR.SETUP.SERVICE: Got car info` does not print on this Gearhead. Read the identity from
  `dumpsys activity service com.google.android.projection.gearhead` and its `CarInfoInternal[...]`.
- `GH.Assistant.Recorder: Using phone microphone` **does** log on `17.5.663204`.
- `GH.PhoneMicRecorder` throws `IOException: EPIPE` on every teardown after the bytes are delivered.
  Known, Gearhead-side, not a finding again.
- The head unit's own bundled `gearhead:car` memory-cycles about every 60 s. Harmless noise.
- `round-headunit-info-r2/session_up.sh` is the poll-and-nudge connect script from last round.

---

## 5. The runs

### R0 — gate

Build `283659f4` and run the unit tests. **Expected 906 / 0**, up 6 from round 2's 900:
`VehicleTypePolicyTest` goes from 3 to 5, and `VehicleIdentityPolicyTest` is new with 4. Gradle prints
no totals without `--info`, so parse `app/build/test-results/testGithubDebugUnitTest/*.xml` as last
round did. Record the APK md5 and confirm it on-device before R1.

A build failure stops the round. This is these two commits' first compile.

### R1 — establish the car record

`use-head-unit-microphone=true`, `vehicle-type` absent. Connect.

- Session forms, video renders.
- `CarInfoInternal` shows **`vehicleType=VEHICLE_TYPE_CAR`**. Record its `dbId` and `vehicleId` hash.
- Trigger the assistant: the phone builds **`GH.CarMicRecorder`** and the head unit logs
  `Mic request: open: true`, `Initializing AudioRecord`, `mic uplink started`, `capture summary`.

This is the ordinary configuration and it is the state a user is in before they change anything.
**FAIL here stops the round** and is a regression, because nothing in these two commits should change
what a car with a microphone announces.

### R2 — turn the microphone off without touching the phone

`use-head-unit-microphone=false`. Everything else unchanged. **Do not forget the car.** Reconnect.

This is the run the whole round is for.

- **The session must form.** `No audio/mic` must be absent. Before the fix this is exactly where it
  would abort.
- `Head unit microphone is off in Settings. Skipping the microphone service ...` present, and the
  announcement carries no MIC channel.
- `CarInfoInternal` shows a **new record**: a different `dbId`, a different `vehicleId` hash, and
  **`vehicleType=VEHICLE_TYPE_MOTORCYCLE`**. Quote both records, R1's and this one. Two entries for
  this head unit in the phone's companion app is the expected, designed outcome, not a bug.
- The phone may ask for consent for the new entry. If it does, accept it and say so.
- `GH.Assistant.Recorder: Using phone microphone` at projection start, and
  **`GH.PhoneMicRecorder`** on the trigger. Zero `GH.CarMicRecorder`. `Mic request:` zero at the head
  unit.

### R3 — turn it back on

`use-head-unit-microphone=true`. Reconnect, still without forgetting anything.

- The session forms and `CarInfoInternal` shows **R1's original record again** — same `dbId`, same
  hash, `vehicleType=VEHICLE_TYPE_CAR`.
- `GH.CarMicRecorder` and the head unit's three capture lines are back.

R2 and R3 together are the round trip. If R3 lands on R2's record instead of R1's, the identity split
is not doing its job and that is a FAIL worth the full capture.

### R4 — the picker

`use-head-unit-microphone=true`, `vehicle-type=2` (Truck). Reconnect.

- A **third** record, `vehicleType=VEHICLE_TYPE_TRUCK`, its own `dbId` and hash.
- The microphone still works: `GH.CarMicRecorder`, capture lines present. A truck announces its
  microphone like a car does.

Then set `vehicle-type=1` (Car) and reconnect once more: R1's record again.

**Dashboard, while you are here.** In R2's motorcycle session, grep the phone capture for
`Not showing dashboard during projection start on motorcycles`. Round 2 saw no dashboard and could
not say whether the vehicle type caused it, because a narrow layout produces the same result with no
log line. That one string settles it. Then say whether the dashboard is visibly back in R3 or R4.
A screenshot of each is enough; this is an observation, not a verdict.

### R5 — the riding configuration again, on the fixed build

`use-head-unit-microphone=false` **and** `enable-audio-sink=false`, intercom connected to the phone.
Round 2 ran this and it worked; repeat it only to confirm the identity change did not disturb it.

Same four observations as round 2: music in the helmet, the assistant reachable through the intercom,
the head unit silent, music ducks and returns. Then **disconnect and reconnect**, which round 2 could
not complete. With `wifi-direct-band=0` this should now be answerable, and it is the last untested
thing about this configuration.

---

## 6. Do not re-run

Round 2 settled these and repeating them costs time without adding anything: that withholding the
service does not end the session under a motorcycle, that the recorder moves, that a spoken query is
transcribed and answered, and that the helmet hears music and the assistant. R5 revisits the last of
those only because the identity change touches every session.

---

## 7. Report back

Beyond the standing format:

1. R1, R2, R3 verdicts, and for each the **full `CarInfoInternal` line**, quoted. The `dbId` and
   `vehicleId` values across the three runs are the measurement this round exists for.
2. Whether R2 formed a session **without the car being forgotten**. State it plainly either way.
3. Whether the phone asked for consent on the new entries, and how many entries the companion app
   lists for this head unit at the end.
4. Whether R3 returned to R1's exact record or created another one.
5. R4: the truck record, whether the microphone still worked under it, and whether Car returned to
   R1's record.
6. The dashboard: is `Not showing dashboard during projection start on motorcycles` present in R2,
   and is the dashboard visibly back afterwards.
7. R5's disconnect and reconnect, which is the sub-question round 2 left open, plus which
   `wifi-direct-band` value you ended up running.
8. The R0 count. 906 is the expectation; if it differs, say by how much and which class.
