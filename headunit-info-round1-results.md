# headunit-info — round 1 results

**Candidate (C):** `fork/fix/mic-and-vehicle-type` @ `21d098dc`
**Baseline (A):** `fork/fix/mic-and-vehicle-type~2` @ `2aa8ab12`
**APK md5:** C `686ab5324c44fd37e1929679747eca12` / A `b381c7e0eca2ec24188c0e35c1539e33`
**Unit:** UNISOC MT50 (`MT50_YT610E4GFPSL_U`, Android 14) head unit; phone: **Motorola edge 30 neo
`ZY22GC3BM4`**, Android Auto **17.5.663204-release** (Gemini as the assistant)
**Date:** 2026-08-28

## Headline

- **R0 PASS** — the rebased stack's first compile. A **897/0**, C **900/0**, delta **exactly +3**,
  `VehicleTypePolicyTest` = 3. Absolutes match the brief.
- **R0a PASS** — assistant is **Gemini**, it reached a real voice session and consumed ~10 s of mic
  uplink (`Mic request: open: true`). R3 was clearable.
- **R1 PASS both arms** — 8 of 9 label rows on each arm come out **exactly** as the brief predicted,
  so the field-renumbering fix does what it says. The `vehicleId` row is inconclusive on both arms
  (Android Auto hashes that one field). One prediction miss, reported as a finding not a failure:
  arm A's `vehicleType` reads `VEHICLE_TYPE_CAR`, not `VEHICLE_TYPE_UNSPECIFIED` — AA defaults an
  **absent** vehicle_type to CAR.
- **R2 — the point of the round — the vehicle type reaches AA and is stored as MOTORCYCLE, but it
  does not move the recorder.** With the mic off the candidate sends `vehicle_type = 3` and Android
  Auto records `vehicleType=VEHICLE_TYPE_MOTORCYCLE`. On an assistant trigger against that
  motorcycle session the phone still picks **`GH.CarMicRecorder`** (the head-unit mic) and asks the
  head unit for the microphone; the head unit declines, the assistant gets 0 bytes and stops. This
  is brief §0's pessimistic branch: the head unit still announces its microphone service, so
  Android Auto's `ICarAudio`-empty condition is not met and the motorcycle claim alone does not
  hand the microphone to the phone.
- **R3 SKIPPED** per the brief (the phone did not switch to its own mic).
- **R4 UNTESTABLE** — no Bluetooth helmet intercom on this rig.

**Shipping read:** commit 1 (field renumbering) is verified and correct — ship it. Commit 2's
vehicle-type field is transmitted and read correctly, but its stated purpose (let the phone fall
back to its own mic on a motorcycle) is **not achieved on this rig** with the microphone service
still announced. Per the brief's own R2/R3 wording, "the claim comes back out" unless a follow-up
round with the mic service withheld (or the phone's prior CAR record for this head unit cleared —
see Setup notes) shows otherwise.

## Setup notes

### Scripts

- `hur-wifi-test-scripts/` inventory taken at round start (recorded in
  `round-headunit-info-r1/DRAFT-notes.md`). Used: `build_hur.sh` (both arm builds — copied each APK
  into `round-headunit-info-r1/` immediately after building, per §7a, since `build_hur.sh` deletes
  the previous one), `run_unit_tests.sh` (both R0 gates), `set_hu_prefs.sh` (every multi-key
  settings write, no relaunch until told — the brief's own instruction and §5).
- **New script added:** `round-headunit-info-r1/run_session.sh` — one clean Native AA session with
  dual capture (HU + phone), optional assistant trigger (route 1). Left in the round folder.
- APKs installed by name with `adb install -r` and md5-verified on-device before every run. Never
  via a relaunch script (`set_hu_pref.sh` / `install_and_launch.sh`), per §7a.

### `settings.xml` delta at round start

`wifi-connection-mode` (3) and `native-ap-transport` (0) already correct. Changed for the round:
`log-level` 2→1; `enable-audio-sink` false→true; `use-head-unit-microphone` false→true (R0a/R1) /
false (R2/R3); `vehicle-make` `Google`→`MAKE1`; `head-unit-make` `Google`→`HUMAKE5`;
`vehicle-model`/`vehicle-year`/`vehicle-id`/`head-unit-model` absent→`MODEL2`/`YEAR3`/`VEHID4`/`HUMODEL6`.
`log-source` already 0. `key-codes` confirmed **absent** throughout (R3 precondition). Restored to
the exact pre-round backup at the end (pushed the backup via `run-as sh -c 'cat >'` — inline `cp`
avoided per §7a); `diff` against a fresh read is **clean / byte-identical**.

### Deviations

1. **`CAR.SETUP.SERVICE` "Got car info" does not exist on this Gearhead (17.5.663204).** Neither
   `CAR.SETUP.SERVICE` nor `CAR.SETUP.SERVICE.LITE` logs a "Got car info [...]" line under any tag
   searched (same wall headunit-info round 1 hit). Substituted the phone's parsed
   `CarInfoInternal` record from `adb shell dumpsys activity service ...gearhead`, which carries
   every label the brief's line does (`manufacturer=…,model=…,modelYear=…,vehicleId=…,
   vehicleType=…,headUnitMake=…,headUnitModel=…,headUnitSoftwareBuild=…,headUnitSoftwareVersion=…`).
   Records verbatim in `round-headunit-info-r1/carinfo-records.txt`.
2. **`GH.Assistant.Recorder` never logs "Using phone microphone" / "Not using phone mic"** on this
   build — only process-info lines under that tag. The observable recorder decision is
   `GH.CarMicRecorder: Start new recording` (car mic) vs a `GH.PhoneMicRecorder` equivalent
   (never seen). Reported R2's recorder question on that.
3. **R2 needed a distinct `vehicle-id` (`VEHID4B`) to be readable.** The brief sets identity
   strings identically across R1 and R2. R1-C recorded this identity as `vehicleType=CAR`
   (`dbId=1`, `known=true`), and Android Auto's pre-connection `getCarInfoInternal` lookup is keyed
   on the head unit's Bluetooth MAC and keeps returning that record regardless of the `vehicle-id`
   field. R2 attempts 1 and 2 (identity `VEHID4`) therefore both read `vehicleType=CAR` — the
   cached R1-C value, not the live session. Attempt 3 with `vehicle-id=VEHID4B` made Android Auto
   create a fresh record (`dbId=2`), which read `VEHICLE_TYPE_MOTORCYCLE`. **Brief bug:** with R1
   and R2 sharing an identity, R2's vehicle-type cannot be read off a phone that just ran R1 on the
   same car. R3's "same settings as R2" was honoured with the `VEHID4B` id.
4. **Connection was the round's bottleneck.** `wifi-direct-band` was `1` (5 GHz only) in the
   pre-round `settings.xml`; the Motorola will not join this head unit's 5 GHz Wi-Fi Direct group,
   so a session only forms when a 2.4 GHz "Standard createGroup" fallback happens. The candidate
   carries no #760 poke-loop fix, so the P2P group churns (~1 new group / 60 s; `p2p-wlan0-N` ran
   from 0 to 11 over the round; 0–2 `MATCH! Starting AapService` self-wakes per connect). The
   automatic poke is **skipped every cycle** (`NativeAA: Not poking … this head unit already holds
   a Bluetooth hands-free link` — `noteHandsFreePokeSkip`), so the phone must self-reconnect on its
   own ~65 s retry cadence. Each connect took 30 s – 2.5 min and the **operator nudged each one
   manually** from the phone's Bluetooth settings (user elected to keep `wifi-direct-band=1` rather
   than override it). Not contamination of any measurement — every value below was read off a
   single stable session after the churn settled — but recorded here because it makes this rig +
   this un-poke-fixed branch slow to bring up.
5. **§2a first-run:** no manual Android Auto first-run/consent screen appeared on any arm-C or
   arm-A connection. Arm A is a genuinely new car to Android Auto (its scrambled field order gives
   AA a different make/model/year), and it still connected without a consent walkthrough.

### Rig / environment

- The prior headunit-info round 1 note that "`ZY22GC3BM4` has never run 17.5" is **stale** — the
  Motorola is now on `17.5.663204-release`. The POCO X3 `4f4027e9` was not attached this session;
  the whole round ran on the Motorola.
- HU BT name "Navegadortz2" (`11:46:03:10:33:59`), bonded to the Motorola (`A0:46:5A:97:E4:95`),
  bond intact throughout.
- Candidate APK (`686ab532…`) left installed on the head unit at round end.
- Log captures (gzipped) + decisive excerpts + `carinfo-records.txt` in
  `hur-wifi-test-scripts/round-headunit-info-r1/`. Not committed here (per channel convention).

## R0 — Gate — PASS

- Build: `assembleGithubDebug` clean on **both** arms (kapt + Kotlin + Java + native all executed
  on the arm-A rebuild; md5s differ).
- Tests (`testGithubDebugUnitTest`):
  - **A `2aa8ab12`: 897 / 0 failures / 0 errors.**
  - **C `21d098dc`: 900 / 0 / 0.** `VehicleTypePolicyTest` = **3** ("the numbers are Android
    Auto's own", "a head unit that records is a car", "a head unit that will not record claims to
    be a motorcycle").
- **Delta = +3, exactly as required.** Absolutes match the brief's predictions (897 / 900).
- This is the stack's first compile since the rebase onto `main`; it is clean.

## R0a — assistant gate — PASS

- Live arm-C session, `use-head-unit-microphone=true`. Assistant identity: **Gemini**
  (`czoq: not opening mic early for auto, gemini micboost!`, `updateGeminiLiveState`).
- Trigger route 1 (`am broadcast -n …/RemoteControlReceiver -a com.android.music.musicservicecommand
  --es command voice`), `AapProjectionActivity` resumed, `key-codes` absent:

```
18:30:05.497  Voice Session Notification: START
18:30:06.037  AapControlMedia.micRequest | Mic request: open: true          <- the gate signal
18:30:06.051  MicRecorder: Initializing AudioRecord ... SampleRate: 16000
18:30:06.557  AapTransport: mic uplink started (channel MIC, type 0, ... 4096B messages)
18:30:16.540  MicRecorder: capture summary | ... elapsed=10421ms bytes=327680 (98% of expected) emptyReads=0 peak=2088/32767
18:30:17.570  Voice Session Notification: STOP
```

- The phone requested the microphone and consumed ~10 s of real uplink → **R3 was clearable.**
  (`peak=2088/32767` — quiet room, non-zero.)

## R1 — the field numbers, and the ordinary vehicle type — PASS (both arms)

Read from the phone's `CarInfoInternal` record (see Deviation 1). Full records in
`carinfo-records.txt`.

| Label in the phone's record | Arm A predicted | Arm A actual | Arm C predicted | Arm C actual |
|---|---|---|---|---|
| `manufacturer=` | HUMAKE5 | **HUMAKE5** | MAKE1 | **MAKE1** |
| `model=` | HUMODEL6 | **HUMODEL6** | MODEL2 | **MODEL2** |
| `modelYear=` | MAKE1 | **MAKE1** | YEAR3 | **YEAR3** |
| `vehicleId=` | MODEL2 | `e51991c52e6aff4d` (hashed) | VEHID4 | `a6e50b8340b97c53` (hashed) |
| `headUnitMake=` | YEAR3 | **YEAR3** | HUMAKE5 | **HUMAKE5** |
| `headUnitModel=` | 1 | **1** | HUMODEL6 | **HUMODEL6** |
| `headUnitSoftwareBuild=` | VEHID4 | **VEHID4** | 1 | **1** |
| `headUnitSoftwareVersion=` | 0.1.0 | **0.1.0** | 0.1.0 | **0.1.0** |
| `vehicleType=` | VEHICLE_TYPE_UNSPECIFIED | **VEHICLE_TYPE_CAR** | VEHICLE_TYPE_CAR | **VEHICLE_TYPE_CAR** |

- **8 of 9 rows on each arm match the brief's prediction exactly.** The candidate's renumbering is
  correct: on arm C the values land under the names Android Auto reads; on arm A they are scrambled
  in precisely the predicted way. The reading of Android Auto's numbering is right.
- **`vehicleId` — inconclusive both arms** (per the brief's rule). Android Auto hashes this one
  field; every other string field, including `headUnitSoftwareBuild=VEHID4`, passes through raw, so
  this is field-specific privacy hashing, not general redaction.
- **`vehicleType` on arm A — prediction miss, reported as a finding:** the brief predicted
  `VEHICLE_TYPE_UNSPECIFIED` for a head unit that sends no vehicle_type; Android Auto actually
  records **`VEHICLE_TYPE_CAR`** by default. (This does not weaken R2 — arm C mic-off still
  produces `VEHICLE_TYPE_MOTORCYCLE`, which only field 9 can do.)
- Both arms reached a working session: `SSL handshake complete` present (C at 18:28:37 after a
  2.4 GHz fallback group; A at 18:44:36), video rendering 220–235 frames/window, **0** `No audio/mic`
  in either phone capture.

## R2 — the microphone off, on a motorcycle — the vehicle type is delivered; the recorder is not moved

Arm C, `use-head-unit-microphone=false`, `vehicle-id=VEHID4B` (Deviation 3). One session.

- **`vehicleType=VEHICLE_TYPE_MOTORCYCLE`.** Android Auto created a fresh record
  `CarInfoInternal[dbId=2, …, vehicleId=11e6d11f9c0d8f6b, vehicleType=VEHICLE_TYPE_MOTORCYCLE]`, and
  the active-session dump during the assistant trigger showed the same. So the candidate's field-9
  `vehicle_type` **is transmitted and Android Auto reads and stores it.**
- **Recorder choice: the phone picked the car (head-unit) recorder.** On the assistant trigger
  against that motorcycle session:

```
phone  18:41:25.046  czoq: not opening mic early for auto, gemini micboost!
phone  18:41:25.860  GH.CarMicRecorder: Start new recording.
phone  18:41:25.913  GH.CarMicRecorder: Recording finished: no more data available
phone  18:41:25.913  GH.CarMicRecorder: ... total bytes sent: 0
phone  18:41:26.854  GH.AssistantUtils: Event: ASSISTANT_TO_GEARHEAD_OPEN_MICROPHONE
HU     18:41:26.729  Voice Session Notification: START
HU     18:41:27.501  Mic request: the head unit microphone is off in Settings. Declining and sending nothing ...
HU     18:41:27.535  Mic request: open: false
HU     18:41:28.505  Voice Session Notification: STOP
```

  No `GH.PhoneMicRecorder`, no "Using phone microphone". `GH.CarMicRecorder: Start new recording`
  is the observable equivalent of the brief's "Not using phone mic" on this build. The assistant
  asked the head unit for the mic, got 0 bytes, and gave up in ~1.8 s.
- **Head unit decline path — correct.** `AapTransport: not taking the microphone (setting
  useHeadUnitMicrophone=false, available=true)` present; `Mic request: … Declining and sending
  nothing` present; **none** of `MicRecorder: Initializing AudioRecord` / `AapTransport: mic uplink
  started` / `MicRecorder: capture summary` (the three lines the brief requires absent).
- **Session stayed up:** `SSL handshake complete` present, video rendering ~230 frames/window, no
  `No audio/mic`.
- **Dashboard observation:** not obtained. Checking whether the projected dashboard is suppressed
  needs a look at the projected display, which this round did not script (no display teardown or
  screenshot step). Recorded as **not observed**, per §0.
- **Caveat.** The R1-C `vehicleType=CAR` record (`dbId=1`) was never cleared, and Android Auto's
  BT-MAC-keyed pre-connection lookup keeps returning it, so it is not fully excluded that the
  projection-start recorder decision read CAR rather than the live MOTORCYCLE. The active session
  ran as MOTORCYCLE and the recorder was still Car, which is the strong signal; a definitive test
  needs `pm clear com.google.android.projection.gearhead` (wipes AA, re-consent) or "forget car",
  neither scriptable here.
- **Answer to the question the round exists for:** vehicle_type MOTORCYCLE works on the wire and in
  Android Auto's record, but **does not** move the recorder to the phone while the head unit still
  announces its microphone service — i.e. Android Auto's second condition ("`ICarAudio` transaction
  2 returns empty") is **not** already satisfied here; announcing the mic keeps it non-empty. Brief
  §0's "route is closed for good" branch.

## R3 — SKIPPED (per brief)

The brief: "If R2 said `Not using phone mic`, skip R3 entirely and say so. The route is closed and
the candidate's second commit does not survive the round." The phone chose `GH.CarMicRecorder`
(= not using phone mic), so **R3 is skipped**. The helmet-intercom half is untested regardless —
no BT intercom on this rig.

## R4 — UNTESTABLE

No Bluetooth helmet intercom paired to the phone on this rig. The `use-head-unit-microphone=false`
+ `enable-audio-sink=false` end-to-end riding configuration cannot be exercised here.

## Report-back items

1. **R1's two columns** — table above. Both arms 8/9 as predicted; the renumbering is correct.
2. **R2's recorder line** — `GH.CarMicRecorder: Start new recording … total bytes sent: 0`, run
   with `vehicleType=VEHICLE_TYPE_MOTORCYCLE`. The motorcycle route does **not** open here.
3. **R3** — did not run (skipped per brief). Head unit capture counts for the three lines: all
   **0** (mic declined, nothing recorded). Helmet: no intercom, untested.
4. **First-run** — the phone did **not** demand Android Auto's first-run on either arm.
5. **Dashboard observation (R2)** — not observed (no scripted display check this round).
6. **R4** — did not run (no intercom).

## Anything the brief did not ask about

- **Android Auto hashes only the `vehicleId` field**, not the whole record — worth knowing for any
  future round that wants to read `vehicleId` back (it can't, on this Gearhead).
- **Android Auto defaults an absent `vehicle_type` to `VEHICLE_TYPE_CAR`**, not `UNSPECIFIED`
  (arm A). So "the head unit has never sent a vehicle type" does not leave the phone at UNSPECIFIED
  — it leaves it at CAR, which happens to be what commit 2's first paragraph wants anyway.
- **Android Auto's per-head-unit record is keyed on the Bluetooth MAC for the pre-connection
  lookup**, and it does not update a stored `vehicleType` when the same identity reconnects with a
  different value — the R1-C CAR record shadowed R2 until the `vehicle-id` was changed. Real-world
  consequence for commit 2: a rider who has ever connected this head unit with the mic **on** will
  have a CAR record that a later MOTORCYCLE claim does not overwrite on the same identity.
- **The automatic poke never fires on this rig** for this phone (`noteHandsFreePokeSkip` every
  cycle — the HFP link is already up), so the whole self-wake / group-churn cost here comes from
  the phone's own reconnect racing the 60 s group re-create, not from the poke.
