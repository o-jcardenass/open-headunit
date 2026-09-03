# headunit-info, round 1 brief: what this head unit tells the phone it is

**Both arms are commits on one branch, `fork/fix/mic-and-vehicle-type`.** It replaces
`fork/fix/mic-uplink` and `fork/fix/headunit-info-and-vehicle-type`, which this brief used to name:
those were 41 commits behind `main` and no longer compile against it. Do not build them.

**Baseline (A):** `fork/fix/mic-and-vehicle-type~2` @ `2aa8ab12`, the whole stack up to and including
`Mic: say what the off switch actually does`.
**Candidate (C):** `fork/fix/mic-and-vehicle-type` @ `21d098dc`, the same plus the two protocol commits.

```bash
git fetch fork
git checkout -B hui-cand fork/fix/mic-and-vehicle-type   # must print 21d098dc...
git rev-parse HEAD
git checkout -B hui-base fork/fix/mic-and-vehicle-type~2 # must print 2aa8ab12...
git rev-parse HEAD
```

`git log --oneline main..hui-cand` must show 13 commits, and `hui-base` the first 11 of them.

Two APKs. **`set_hu_pref.sh` reinstalls whatever is newest in `apks/`, so it must never be used to
switch arms** (round 3 setup note). Build both first, keep them apart, install by name.

**Phone-side logcat is required for every run in this round.** Nothing here is visible from the head
unit; the whole round is read off the phone.

---

## 0. Why this round exists

Turning **Head unit microphone** off leaves Android Auto's assistant with nothing at all. Round 2's
M5 measured the same thing from the other side: the head unit declined cleanly twelve times, the
session stayed up, and the phone timed out both spoken attempts rather than falling back to its own
microphone.

The mechanism is now known, from Gearhead `17.5.663204`. Android Auto chooses its recorder **once**,
in the projection-start routine, before any `MicrophoneRequest` exists:

```
usePhoneMic = (vehicleType == VEHICLE_TYPE_MOTORCYCLE) && (ICarAudio transaction 2 returns an empty int[])
```

It then logs `Using phone microphone` or `Not using phone mic` and keeps that recorder for the whole
session. Nothing sent in reply to a microphone request can move it.

This head unit has never sent a vehicle type, so the phone records `VEHICLE_TYPE_UNSPECIFIED` and the
first half can never be true. The candidate sends one.

**The second half is the thing this round is for.** `ICarAudio` is implemented inside Play services,
Gearhead calls transaction 2 from exactly this one place, and its siblings have no callers to read a
naming off, so what the empty check means could not be settled from the APK. Either it is "the input
configurations the head unit declared", in which case announcing the microphone service keeps it
non-empty and this route is closed for good, or it is something already empty here, in which case the
vehicle type alone opens it. **R2 answers that in one line.**

A second defect turned up on the way and is the other half of the candidate. `HeadUnitInfo`'s field
numbers were wrong, so every value in it has been arriving under the wrong name. R1 measures that
directly.

---

## 1. What changed in the code

| Piece | What it does now |
|---|---|
| `common.proto` `HeadUnitInfo` | Renumbered to `make`, `model`, `year`, `vehicle_id`, `head_unit_make`, `head_unit_model`, `head_unit_software_build`, `head_unit_software_version`. It was `head_unit_make` first |
| Package placement | The rebase moved seven files out of `aap/`, which holds AAP protocol only: `MicrophonePolicy`, `MicChunkAccumulator`, `MicPcmDecimator`, `MicCaptureRatePolicy` and `AudioPrerollPolicy` to `decoder/audio/`, `ForegroundServiceTypePolicy` to `app/`, `BluetoothAddressSeedPolicy` to `utils/`. Behaviour is unchanged; it is named here because it is most of the diff against the branches this brief used to name |
| `SettingsBackupManager` | `use-head-unit-microphone` was never registered. It is now in both the type map and `projectionRestartKeys`, so it survives export/import and prompts for a restart |
| `common.proto` `HeadUnitInfo` | Gains field 9, `vehicle_type` (`int32`) |
| `VehicleTypePolicy` | New pure object. `CAR` (1) when the head unit microphone is on, `MOTORCYCLE` (3) when it is off |
| `ServiceDiscoveryResponse` | Sets `vehicle_type` from that policy. Its eight setter calls are unchanged |
| `Common.java` | Regenerated with protoc 25.1 |

Nothing else moves. The microphone decline path, the foreground-type work and every service
announcement are exactly as round 3 left them.

---

## 2. Settings keys

The six identity strings are set to distinct probe values on purpose: that is what makes R1 readable
by eye and by grep. **Set them identically on both arms.**

| Key | Type | Value | Runs |
|---|---|---|---|
| `vehicle-make` | string | `MAKE1` | all |
| `vehicle-model` | string | `MODEL2` | all |
| `vehicle-year` | string | `YEAR3` | all |
| `vehicle-id` | string | `VEHID4` | all |
| `head-unit-make` | string | `HUMAKE5` | all |
| `head-unit-model` | string | `HUMODEL6` | all |
| `log-level` | int | `1` (DEBUG) | all |
| `log-source` | int | `0` (LOGCAT) | all |
| `wifi-connection-mode` | int | `3` (Native AA) | all |
| `native-ap-transport` | int | `0` (WiFi Direct) | all |
| `enable-audio-sink` | bool | `true` | all |
| `use-head-unit-microphone` | bool | `true` | R1 |
| `use-head-unit-microphone` | bool | `false` | R2, R3 |

`key-codes` must stay absent from `settings.xml` for R3, or the assistant broadcast arrives
remapped, and `AapProjectionActivity` must be resumed when it is sent (round 2 §4a).

`head_unit_software_build` is the literal `1` and `head_unit_software_version` the literal `0.1.0`;
both are hardcoded and neither is a setting.

**None of the probe values may end in `truck`.** Android Auto's fallback path, the one it uses when a
head unit sends no `headunit_info` at all, decides truck-versus-car by that suffix on the model.

Restore the pre-round `settings.xml` at the end and verify with `diff`, as always.

---

## 2a. Pre-registered setup hazard: the phone may see a new car

The candidate changes what the phone records as this head unit's manufacturer, model, model year and
vehicle id. Android Auto keys its per-head-unit records on those values, so **the first candidate
connection may be treated as a car this phone has never seen** and may want its first-run consent
walked through by hand. Round 2 hit the same class of thing with `noteHandsFreePokeSkip` on a new
pairing, and there is no scriptable trigger for Android Auto's first-run.

If that happens: complete it once by hand, note it in Setup notes, and carry on. **It is not a FAIL.**
The same may occur again on the first arm-A connection after a candidate run, in the other direction.

---

## 3. Raising the phone's tags

Do this before the first launch of each arm, with the head unit stopped.

```bash
# Android Auto's developer settings must already be on (round 2 §4b).
for t in CAR.SETUP.SERVICE GH.CarInfoCache CAR.SERVICE \
         GH.Assistant.Recorder GH.CarMicRecorder GH.PhoneMicRecorder CAR.GAL.MIC; do
  adb -s <phone> shell setprop log.tag.$t VERBOSE
done
adb -s <phone> shell am force-stop com.google.android.projection.gearhead
```

`GH.Assistant.Recorder` is new to this round and is the tag that carries the two lines the whole
round turns on. Round 2 raised `GH.CarMicRecorder` and `GH.PhoneMicRecorder` for them, which are the
recorders' own tags and not where the choice is logged. Keep all three raised.

---

## 4. The lines that decide the round

**On the phone, `CAR.SETUP.SERVICE`:**

```
Got car info [dbId=…,manufacturer=…,model=…,headUnitProtocolVersion=…,modelYear=…,vehicleId=…,vehicleType=…,…,headUnitMake=…,headUnitModel=…,headUnitSoftwareBuild=…,headUnitSoftwareVersion=…,…]
```

That single line carries everything R1 and R2's first half need. `vehicleType` resolves to
`VEHICLE_TYPE_UNSPECIFIED`, `VEHICLE_TYPE_CAR`, `VEHICLE_TYPE_TRUCK`, `VEHICLE_TYPE_MOTORCYCLE`, or
`UNKNOWN(n)`. Capture the line verbatim for every run.

**On the phone, `GH.Assistant.Recorder`:**

```
Using phone microphone
Not using phone mic
```

Exactly one of the two, once per session.

**On the head unit:**

```
Mic request: the head unit microphone is off in Settings. Declining and sending nothing, so a Bluetooth headset keeps this microphone. The phone does not switch to its own, so the assistant will not answer
AapTransport: not taking the microphone (setting useHeadUnitMicrophone=false, available=true)
MicRecorder: Initializing AudioRecord with source:
AapTransport: mic uplink started
MicRecorder: capture summary |
```

The last three must be **absent** on every microphone-off run, on both arms.

---

## 5. Runs

### R0. Gate

Build both arms and run the unit tests on each. **This is the stack's first compile since it was
rebased onto `main`**, so R0 is a real gate this time rather than a formality: the rebase reconciled
eight files that both sides had edited, moved six files into the packages `main`'s decoder split
created, and fixed two stale imports. Nothing here has been built.

- **A:** 897 expected.
- **C:** 900 expected. `VehicleTypePolicyTest` = 3.

Those two totals are predicted by counting `@Test` annotations, not read off a run, so treat arm A's
actual number as the baseline and **the hard requirement as `C == A + 3`**. A different absolute on
both arms is fine and worth reporting; a delta that is not 3 is not.

Report both md5s and confirm which APK is installed before each run below. **A build failure on
either arm stops the round.**

### R0a. The assistant gate, before anything that needs speech

R3 and the second half of R2 depend on the paired phone's assistant reaching its backend, and that is
not a variable this brief can assume. Round 1 of mic-uplink lost a whole visit to it: all three
trigger routes reached the app and opened a real voice session, and no `Mic request: open: true` ever
followed, because that phone's Android Auto invokes Gemini and Gemini could not connect. The phone
held a validated LTE connection throughout, so a working data path is not evidence.

Before spending the visit, run one cheap session on arm C with the microphone **on** and trigger the
assistant once. `Mic request: open: true` on the head unit means the round can proceed. A voice
session that starts and stops with no such line means it cannot, and R2's recorder line is still worth
capturing but R3 is untestable.

Report the phone's assistant identity (Assistant or Gemini) as a field either way.

### R1. The field numbers, and the ordinary vehicle type

One clean session per arm, microphone **on**, nothing else to do but connect and let it settle.
Capture the phone's `Got car info` line on each.

Predicted, and this is the whole check:

| Label in the phone's line | Arm A (baseline) | Arm C (candidate) |
|---|---|---|
| `manufacturer=` | `HUMAKE5` | `MAKE1` |
| `model=` | `HUMODEL6` | `MODEL2` |
| `modelYear=` | `MAKE1` | `YEAR3` |
| `vehicleId=` | `MODEL2` | `VEHID4` |
| `headUnitMake=` | `YEAR3` | `HUMAKE5` |
| `headUnitModel=` | `1` | `HUMODEL6` |
| `headUnitSoftwareBuild=` | `VEHID4` | `1` |
| `headUnitSoftwareVersion=` | `0.1.0` | `0.1.0` |
| `vehicleType=` | `VEHICLE_TYPE_UNSPECIFIED` | `VEHICLE_TYPE_CAR` |

- **PASS:** both columns come out as predicted.
- **FAIL:** arm C's column is anything else. Report arm A's column whatever it says: if it does not
  match either, the reading of Android Auto's numbering is wrong and that is the finding.
- If `vehicleId=` prints redacted or hashed, mark that one row inconclusive and decide on the other
  eight. Do not call the run FAIL for it.

Both arms must also reach a working session: `SSL handshake complete` present, and no
`No audio/mic` anywhere in the phone's capture.

### R2. The point of the round: the microphone off, on a motorcycle

Arm **C** only, `use-head-unit-microphone=false`. One clean session. No need to speak yet.

- The phone's `Got car info` line reads `vehicleType=VEHICLE_TYPE_MOTORCYCLE`.
- `GH.Assistant.Recorder` prints **exactly one** of `Using phone microphone` or `Not using phone mic`.
  Report which, with its timestamp relative to the session start. **Either answer is a result, not a
  failure.** This is the question the round exists to answer.
- The head unit's three capture lines are absent, and `AapTransport: not taking the microphone` is
  present.
- The session forms and stays up: `SSL handshake complete` present, no `No audio/mic`.

Also report, as an observation rather than a verdict: **whether the projected dashboard appears at
projection start.** Android Auto suppresses it on motorcycles, and that is the visible cost of the
claim. §0 of the template applies here, so describe what you saw and keep it out of the PASS
condition.

### R3. Only if R2 said `Using phone microphone`

Same arm and same settings as R2. Trigger the assistant by round 2 §4a's route 1 and speak
"navigate to the nearest petrol station".

- **PASS:** the assistant answers, and the head unit stays silent throughout (`Initializing
  AudioRecord`, `mic uplink started` and `capture summary` all still zero).
- **FAIL:** it times out, or the head unit recorded anything.

**If R2 said `Not using phone mic`, skip R3 entirely and say so.** The route is closed and the
candidate's second commit does not survive the round.

**If this round runs on a rig with a Bluetooth helmet intercom paired to the phone**, R3 is worth
more than it looks and should be run through the intercom rather than the phone's own body. That is
the topology the whole thread exists for (issue #818, and the intercom findings doc), and it is the
only configuration where the answer is visible without reading a log: speak into the helmet, and
hear the reply in the helmet. Report it as a plain sentence alongside the log lines. On a rig with
no intercom, R3 still answers the protocol question and the helmet half stays untested.

### R4. Optional, and only with an intercom on the phone: the actual riding configuration

Not part of the numbering or recorder questions, and it cannot FAIL the round. Arm **C**, with
`use-head-unit-microphone=false` **and `enable-audio-sink=false`**, which is the pairing the
findings doc's §6 recipe asks a rider to use and which no round has ever run together.

Report, as observations: whether music and turn-by-turn prompts arrive in the helmet, whether the
head unit stays silent, whether the assistant works, and whether the session survives a
disconnect and reconnect. This is the first end-to-end look at the configuration the feature is for,
so a surprise here is a finding even though it is not a verdict.

---

## 6. Do not re-run

Everything from mic-uplink rounds 1 to 3. M6a is fixed and measured, the frame layout is measured,
and nothing in this candidate touches either.

---

## 7. Report back

1. **R1's two columns**, verbatim from the phone, side by side. This is the numbering result.
2. **R2's `GH.Assistant.Recorder` line**, exactly as printed, plus the `vehicleType=` it ran with.
   One line decides whether the motorcycle route exists.
3. **R3 if it ran:** whether the assistant answered, and the head unit's three capture counts.
   With an intercom on the phone, say plainly whether you heard the reply in the helmet.
4. **Whether the phone demanded Android Auto's first-run again** on either arm, per §2a.
5. **The dashboard observation** from R2.
6. **R4 if it ran:** the four observations, as prose.

Setup notes as always: scripts used, the `settings.xml` delta at round start, any discard-rule hits
with their cause, and anything substituted for a step here.
