# headunit-info, round 2 brief: the microphone service withheld

**One arm.** `fork/fix/mic-and-vehicle-type` @ `40390cf7`, one commit past round 1's candidate
`21d098dc`. There is no baseline this time: round 1 already measured the branch without this commit,
and that measurement is the comparison.

```bash
git fetch fork
git checkout -B hui-r2 fork/fix/mic-and-vehicle-type   # must print 40390cf7...
git rev-parse HEAD
```

`git log --oneline main..hui-r2` must show 14 commits.

**Phone-side logcat is required for every run.** As in round 1, almost nothing here is visible from
the head unit.

**Round 1's candidate APK (`686ab532…`) is still installed on the rig.** Install this one by name and
verify the md5 on-device before the first run; never switch arms with a relaunch script.

---

## 0. Why this round exists

Round 1 concluded that the motorcycle route is closed. That conclusion was correct about what it saw
and wrong about why, and the reason matters enough to reopen it.

Round 1's arm sent `vehicle_type = 3`, the phone stored `vehicleType=VEHICLE_TYPE_MOTORCYCLE`, and on
an assistant trigger the phone still built `GH.CarMicRecorder`, asked the head unit for the
microphone, took 0 bytes and stopped. Android Auto's recorder condition has two halves:

```
usePhoneMic = (vehicleType == MOTORCYCLE) && (ICarAudio transaction 2 returns an empty int[])
```

The first half was satisfied. The second was not, and round 1 read that as permanent because the head
unit announces a microphone service unconditionally and we believed it had to. **That belief was
wrong.**

The reason it was announced unconditionally: Gearhead's required-service check was thought to tear the
session down with `No audio/mic` if the microphone were missing. Read out of `17.5.663204`, the check
does something else. Its loop over three endpoint slots exits on the **first non-null** slot, so the
error fires only when **all three** are null, and the three slots are **audio sinks**. They are filled
by one method gated on the announcement's `mediaSinkService` field, which never reads
`mediaSourceService`. The microphone is a different object, built lazily when a recording session
starts, and is not registered in that check at all. The `/mic` in the string is stale.

The consistency check on that reading is this app's own behaviour: with the audio sink off we
announce only the system sink, which fills one slot, and that configuration works. So the check
tolerates two empty slots today, in the field.

**This round therefore asks the question round 1 could not: with no microphone service announced at
all, does the phone record with its own microphone?** Either answer ends the thread. If it does, the
motorcycle claim and this commit both ship. If it does not, both come out and the branch cuts at
`eb8f9577`.

**One thing the analysis explicitly did not rule out.** It proves no teardown from *that* check. It
did not audit whether something later throws when a voice session tries to build a recorder and finds
no microphone channel. That would be a failure at assistant-invocation time, not at connect, so a
session that forms cleanly is not the result. R2 and R3 are where it would show.

---

## 1. What changed in the code

One commit, `40390cf7`.

| Piece | What it does now |
|---|---|
| `ServiceDiscoveryResponse` | The microphone service block is wrapped in `if (settings.useHeadUnitMicrophone)`. With the setting off the channel is not announced and a line says so, the way the audio-sink and Bluetooth branches beside it already do |
| `MicrophonePolicy`, `VehicleTypePolicy` KDoc | Corrected. They asserted the refuted claim, and `VehicleTypePolicy` also said an absent vehicle type reads as unspecified, which round 1 measured as CAR |
| `AudioConfigs` | A comment recording that `ID_AU2`'s 16 kHz mono config is required rather than preferred. With the audio sink off it is the only sink left, so anything else there empties all three slots and genuinely does end the session |

No behaviour changes with the microphone **on**. The decline path is untouched: it still answers a
phone that asks anyway.

**Adds no tests.** R0's expected total is 900, delta 0 against round 1's arm C.

---

## 2. Settings keys

Identity strings as round 1, with one deliberate change.

| Key | Type | Value | Runs |
|---|---|---|---|
| `vehicle-make` | string | `MAKE1` | all |
| `vehicle-model` | string | `MODEL2` | all |
| `vehicle-year` | string | `YEAR3` | all |
| `vehicle-id` | string | **`VEHID4C`** | all |
| `head-unit-make` | string | `HUMAKE5` | all |
| `head-unit-model` | string | `HUMODEL6` | all |
| `use-head-unit-microphone` | bool | `false` | R1, R2, R3, R4 |
| `enable-audio-sink` | bool | `true` | R1, R2, R3 |
| `enable-audio-sink` | bool | **`false`** | R4 |
| `log-level` | int | `1` | all |

`VEHID4C` is new on purpose. See §3.

---

## 3. The operational thing round 1 got wrong, and this round must not

Round 1's deviation 3 is the most valuable thing in its results and it changes how this round is set
up.

Android Auto keys its per-head-unit record on the head unit's **Bluetooth MAC** for the
pre-connection lookup, and it does **not** update a stored `vehicleType` when the same identity
reconnects with a different one. Round 1's R1-C run recorded this head unit as `VEHICLE_TYPE_CAR`
(`dbId=1`); R2's first two attempts then kept reading CAR off that cached record rather than the live
session, and only a changed `vehicle-id` made Android Auto create a fresh record that read
MOTORCYCLE.

That left round 1 unable to fully exclude that the projection-start recorder decision read the stale
CAR. **This round removes that doubt rather than working around it.**

Preferred, and worth the cost: **forget this head unit on the phone before the first run** (Android
Auto settings, "Forget all cars" or the per-car entry) so no stored record exists at all. It costs a
re-consent walkthrough on the next connect. Say in the results whether you did it.

If that is not available, `VEHID4C` is the fallback: an identity neither round 1 run used, so Android
Auto has to create a fresh record.

Either way, **report the `dbId` and the `vehicleType` of the record the run actually used**, from
`adb shell dumpsys activity service ...gearhead` as in round 1's deviation 1. A run whose record
reads CAR has not tested anything and should be re-run, not reported.

---

## 4. Substitutions round 1 established, carried forward

Do not spend time re-discovering these.

- **`CAR.SETUP.SERVICE` does not log `Got car info` on this Gearhead.** Use the parsed
  `CarInfoInternal` record from `dumpsys activity service ...gearhead`, which carries every label.
- **`GH.Assistant.Recorder` does not log `Using phone microphone` / `Not using phone mic` on this
  build.** The observable recorder decision is `GH.CarMicRecorder: Start new recording` against a
  `GH.PhoneMicRecorder` equivalent. **`GH.PhoneMicRecorder` has never been seen on this rig**, so its
  first appearance is the headline result of this round.
- Trigger route 1 for the assistant, as round 1 used:
  `am broadcast -n …/RemoteControlReceiver -a com.android.music.musicservicecommand --es command voice`,
  with `AapProjectionActivity` resumed and `key-codes` absent.

---

## 5. Runs

### R0. Gate

Build and run the unit tests.

- **900 expected, 0 failures.** This commit adds no tests, so a delta against round 1's arm C of
  anything but 0 is the finding.
- This is `40390cf7`'s first compile. **A build failure stops the round**; send the error rather than
  working around it.

Report the md5 and confirm it is installed before each run.

### R1. The session forms with no microphone announced

`use-head-unit-microphone = false`, one clean session, nothing to do but connect and let it settle.
This is the run that tests the analysis directly.

- **PASS:** `SSL handshake complete` present, video rendering, and **no `No audio/mic` anywhere in
  the phone's capture**.
- **FAIL:** the session does not form, or `No audio/mic` appears. That refutes the reading, and the
  round stops there: report it and nothing below is worth running.

Confirm on the head unit that the new line is present (`Skipping the microphone service`) and that
the microphone service is genuinely absent from the announcement.

Report the `CarInfoInternal` record per §3, including `dbId` and `vehicleType`.

### R2. The point of the round: which recorder does the phone build

Same settings, same session if it is still up. Trigger the assistant once. No need to speak yet.

- **The result is which of `GH.CarMicRecorder` or `GH.PhoneMicRecorder` appears**, with its timestamp
  relative to the trigger. Report the line verbatim. Either answer is a result, not a failure.
- Report the `vehicleType=` the session ran with, so the answer is attributable.
- **Watch the phone log for a throw or a caught exception around the voice session**, per §0. If the
  assistant fails in a new way rather than the round 1 way, that is the finding and the exact stack
  or error line is what matters.
- Head unit side: the three capture lines (`Initializing AudioRecord`, `mic uplink started`,
  `capture summary`) must all still be absent, and no microphone request should arrive at all now
  that nothing is announced. Report whether `Mic request:` appears; round 1 saw it and this round
  should not.

Also report, as an observation and not a verdict: **whether the projected dashboard appears at
projection start.** Round 1 could not check it. Android Auto suppresses it on motorcycles, and it is
the visible cost of the claim.

### R3. Only if R2 printed `GH.PhoneMicRecorder`

Same settings. Trigger the assistant and speak "navigate to the nearest petrol station".

- **PASS:** the assistant answers, and the head unit's three capture lines are still all absent.
- **FAIL:** it times out, or the head unit recorded anything.

If R2 printed `GH.CarMicRecorder`, skip R3 and say so. The route is then closed for a second and
better-controlled time, and the branch cuts at `eb8f9577`.

### R4. The riding configuration, with an intercom on the phone

Not a verdict run and it cannot FAIL the round. `use-head-unit-microphone = false` **and
`enable-audio-sink = false`** together, intercom paired to the **phone**, not to the head unit. This
is the pairing the findings doc's recipe asks a rider to use and no round has ever run it.

**Expect the assistant to be unavailable unless R2 printed `GH.PhoneMicRecorder`.** That is round 1's
measured result, not a fault, and it should not be reported as one.

Most of this run is observed rather than scripted, which the standing template allows only if the
brief says so. It does: give the scriptable evidence and the sensory observation side by side, and
mark clearly which is which.

Report, as prose:
- Whether music arrives in the helmet, and whether turn-by-turn prompts do.
- Whether the head unit's own speaker stays silent throughout.
- Whether the assistant works, and if not, how it fails.
- Whether the session survives a disconnect and reconnect.

Scriptable evidence to capture alongside: the head unit's audio channel lines (with the sink off,
`Audio sink is off in Settings` should be present and no media or speech channel should be set up),
`SSL handshake complete`, and a throughput window or two showing video still rendering.

**R4 can also be run on round 1's installed candidate (`686ab532…`) if you want to start before this
build lands.** It asks where the audio goes, not which microphone the phone picks, and that half is
identical in both builds. Say which APK it ran on.

---

## 6. Do not re-run

Round 1's R0a, R1 and the field-numbering question. The renumbering is verified, 8 of 9 rows on both
arms, and nothing in this commit touches it. The assistant gate is also answered: this rig's phone
runs Gemini and it reaches a real voice session.

Everything from mic-uplink rounds 1 to 3.

---

## 7. Report back

1. **R1's verdict**, and whether `No audio/mic` appeared. This is the analysis being right or wrong.
2. **R2's recorder line**, verbatim, with the `vehicleType=` and the `dbId` it ran with. One line
   decides whether the motorcycle route exists.
3. **Whether a microphone request arrived at all** with nothing announced.
4. **Any new exception or failure mode** around the voice session, per §0.
5. **Whether you forgot the car** on the phone before starting, per §3.
6. **The dashboard observation** from R2, still outstanding from round 1.
7. **R3 if it ran.** With an intercom on the phone, say plainly whether you heard the reply in the
   helmet.
8. **R4's four observations**, as prose, and which APK it ran on.

Setup notes as always: scripts used, the `settings.xml` delta at round start, any discard-rule hits
with their cause, and anything substituted for a step here.
