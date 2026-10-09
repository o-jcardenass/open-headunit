# headunit-info — round 2 results

**Candidate:** `fork/fix/mic-and-vehicle-type` @ `40390cf7`   **Baseline:** none (round 1 measured the branch at `21d098dc`, one commit back — that is the comparison)
**APK md5:** `8746a797b162a6dbc1dc55d527d7930f` (`com.andrerinas.headunitrevived_3.3.0-beta3_debug.apk`)
**Unit:** UNISOC MT50 (`MT50_YT610E4GFPSL_U`, Android 14) head unit, adb-root; phone **Motorola edge 30 neo `ZY22GC3BM4`**, Android Auto **17.5.663204-release**, assistant = **Gemini**. Intercom: **Cardo/KY-Pro-class "KY Pro"** (`D0:D9:4F:A0:88:AF`), paired to the **phone**.
**Date:** 2026-08-28

## Headline

- **R0 PASS** — `assembleGithubDebug` clean, **900 tests / 0 failures / 0 errors** (89 classes). Delta **0** vs round 1 arm C's 900, as the brief predicted (commit adds no tests). `VehicleTypePolicyTest` = 3, `MicrophonePolicyTest` = 4.
- **R1 PASS** — the session forms with no microphone service announced, and **no `No audio/mic` anywhere in the phone capture**. The refuted premise is confirmed refuted: withholding the microphone service does **not** end the session.
- **R2 — the point of the round — the recorder MOVES to the phone.** At projection start the phone logs **`GH.Assistant.Recorder: Using phone microphone`** (first time ever on this rig), and on the assistant trigger it builds **`GH.PhoneMicRecorder`** — zero `GH.CarMicRecorder` in the whole capture. **No `Mic request:` ever reaches the head unit.** `vehicleType=VEHICLE_TYPE_MOTORCYCLE`, `dbId=1`.
- **R3 PASS** — spoken *"navigate to the nearest petrol station"* was transcribed and answered: the projected screen showed fuel-station results (Primax 2.6 km, Terpel-Zenu 1.1 km) and `StartNavigationExecutor` fired. The head unit's three capture lines stayed absent.
- **R4 (not a verdict run)** — with sink **and** mic off and the intercom on the phone: **music plays in the helmet, the assistant records from the intercom's SCO mic and its spoken reply is heard in the helmet, the head unit stays silent, music ducks and returns.** The disconnect→reconnect sub-question is **INCONCLUSIVE** — the phone would not rejoin the head unit's 5 GHz group (`wifi-direct-band=1`, a rig setting the operator kept), not a candidate issue.
- **One new failure mode, non-blocking:** `GH.PhoneMicRecorder` throws `IOException: EPIPE (Broken pipe)` on **every** recording teardown (~9 s in, *after* the bytes are delivered). Recognition is unaffected (R3/R4 prove it). Gearhead-side teardown race, worth a mention.

**Shipping read:** both answer-commits are validated. `40390cf7` (withhold the microphone service) lets the phone take its own microphone on a motorcycle; `21d098dc` (claim motorcycle when the mic is off) is the other half and was exercised in every run here. **The motorcycle route is open. Ship the branch — all 14 commits.** The route is not closed and the cut at `eb8f9577` is not needed.

## Setup notes

### Scripts

- `hur-wifi-test-scripts/` inventoried at round start. Used: `build_hur.sh` (candidate build — APK copied to `round-headunit-info-r2/cand-40390cf7.apk` immediately, per §7a, since the script deletes the previous APK); `run_unit_tests.sh` (R0 — gradle prints no test totals without `--info`, so counts were parsed from `app/build/test-results/testGithubDebugUnitTest/*.xml`: 89 classes, tests=900, failures=0, errors=0, skipped=0); `set_hu_prefs.sh` (every settings write, one relaunch-free pass).
- **New script:** `round-headunit-info-r2/session_up.sh` — adapted from round 1's `run_session.sh`. Brings up one Native AA session with dual HU+phone capture (`stdbuf -oL`), then **polls up to N s for `SSL handshake complete` while the operator nudges** the phone's Bluetooth, rather than round 1's fixed 110 s wait. No assistant trigger (fired by hand per run). Derives its capture dir from its own path. Left in the round folder.
- APK installed by name with `adb install -r`, md5 verified on-device before the first run (`8746a797…`). Never via a relaunch script.

### `settings.xml` delta at round start

Fresh backup taken (`round-headunit-info-r2/settings-backup-preround.xml`, md5 `8941d47d2deb94ecadcaa73f3c076bf7`) — byte-identical to what round 1 restored. Changed for the round via `set_hu_prefs.sh`:

| key | round-start | R1–R3 | R4 |
|---|---|---|---|
| `log-level` | 2 | **1** | 1 |
| `enable-audio-sink` | false | **true** | **false** |
| `use-head-unit-microphone` | false | false | false |
| `vehicle-make` | `Google` | **`MAKE1`** | `MAKE1` |
| `head-unit-make` | `Google` | **`HUMAKE5`** | `HUMAKE5` |
| `vehicle-model` / `vehicle-year` / `vehicle-id` / `head-unit-model` | absent | **`MODEL2` / `YEAR3` / `VEHID4C` / `HUMODEL6`** | same |

Unchanged and verified: `wifi-connection-mode=3`, `native-ap-transport=0`, `wifi-direct-band=1`, `log-source=0`, `key-codes` **absent** (R2 assistant precondition). **Restored byte-identical at round end** — pushed the backup file and `cp`'d it into place (adb shell is root on this unit, no `run-as` needed), md5 `8941d47d…`, `diff` clean.

### Operator actions (house rules: minimum needed, all logged)

1. **Forgot the head unit car in Android Auto** on the phone before R1 (per brief §3, the preferred option). The phone→head-unit **Bluetooth bond survived** the forget — checked both sides before starting. Fresh `CarInfoInternal` record created cleanly (`dbId=1`).
2. **Nudged the phone's Bluetooth on each connect** — `wifi-direct-band=1` (5 GHz only), the Motorola will not join the head unit's 5 GHz Wi-Fi Direct group, so a session only forms after a nudge (and sometimes a group-retry cycle). Operator elected in round 1 to keep band=1; kept for round 2.
3. **Connected "KY Pro" to the phone** for R4 (A2DP + HFP both `Connected`), disconnected... n/a (left connected). Disconnected for R1–R3.
4. **Spoke the assistant queries** for R3 and R4 — this rig has no scriptable way to play audio into the phone's (or intercom's) microphone, so the recognition half rests on the operator's report plus the phone's `GH.TranscriptionCtrl` / `StartNavigationExecutor` log lines.

### Deviations

1. `session_up.sh`'s poll-and-nudge window replaces round 1's fixed wait (band=1 manual connect).
2. R3 and R4 required operator speech (no scriptable TTS-into-mic). Marked clearly below which evidence is scriptable and which is the operator's report.
3. **R4 disconnect→reconnect is INCONCLUSIVE** — after `headunit://disconnect` the phone would not rejoin the head unit's 5 GHz group across ~10 min and several BT+WiFi nudges; the head unit kept rebuilding 5 GHz groups with no 2.4 GHz fallback. `wifi-direct-band=1` blocks the rejoin on this phone; not a candidate defect. R1–R3 (one continuous session, ~17 min, two assistant triggers + spoken multi-turn nav) and the first R4 connect all succeeded and were stable.
4. **`CAR.SETUP.SERVICE` "Got car info" still absent** on this Gearhead (round 1 dev 1). Used `dumpsys activity service com.google.android.projection.gearhead` → `CarInfoInternal[…]` for `dbId` + `vehicleType`.
5. **Contrary to round 1 dev 2**, `GH.Assistant.Recorder: Using phone microphone` **does** log on this build (`17.5.663204`) — seen at every projection start in R1–R4. `Not using phone mic` was never seen (the phone never took that branch this round). `GH.PhoneMicRecorder` / `GH.CarMicRecorder` remain the reliable recorder-choice tags.

### Discard-rule check

- **R1/R2/R3 capture (`r1r2`)** — pre-connection group retry, not mid-session contamination: `createGroup SUCCESS` ×2 (both 5 GHz — `p2p-wlan0-0` torn down `reason=REQUESTED` by the app's own retry, then `p2p-wlan0-1` which the phone joined), **one** `AP-STA-CONNECTED`, **one** `WirelessServer: Incoming connection detected`, **one** SSL handshake (logged on two lines: `AapSslContext.performHandshake` + `AapTransport.handshake`). `MATCH! Starting AapService` ×3 (phone's own BT reconnect). `Magic Garbage` 0. Every measurement below is from the single settled session — same methodology round 1 used for its band=1 captures.
- **R4 capture (`r4`)** — clean: `createGroup SUCCESS` ×1 (`p2p-wlan0-2`), one Incoming, one SSL handshake, `Magic Garbage` 0. (`MATCH! Starting AapService` ×2 — phone BT reconnect, no group churn attached.)

### Rig / environment

- adb shell on the head unit runs as **root** (`uid=0`, no `su` binary). The memory note about `shared_prefs` being root-owned and silently blocking the *app's own* writes does not affect this round — settings were pre-written with the app force-stopped.
- The head unit has its **own `com.google.android.projection.gearhead`** installed. Its `:car` service is OS-memory-killed (`Killing … cached idle & background restricted`) and restarted (`Scheduling restart of crashed service … GearheadCarStartup`) **every ~60 s** throughout an OHU session, from 19:39 onward. Pre-existing rig condition, **no visible effect** on the OHU session.
- Log captures (gzipped) + excerpts + `settings-backup-preround.xml` in `hur-wifi-test-scripts/round-headunit-info-r2/`. Not committed here (channel convention). Candidate APK left installed.

---

## R0 — Gate — PASS

- `git checkout -B hui-r2 fork/fix/mic-and-vehicle-type` → HEAD `40390cf7`, `git rev-list --count main..HEAD` = **14**. Tip = "Mic: do not announce a microphone this head unit will not use".
- `assembleGithubDebug` **clean** — the commit's first compile.
- `testGithubDebugUnitTest`: **tests=900, failures=0, errors=0, skipped=0** (89 classes). Delta vs round 1 arm C (900) = **0**, exact.
- `VehicleTypePolicyTest` = 3, `MicrophonePolicyTest` = 4 (KDoc-only changes in the commit; no test files touched).
- md5 `8746a797b162a6dbc1dc55d527d7930f`, confirmed installed on-device before R1.

## R1 — the session forms with no microphone announced — PASS

Operator forgot the car first. `use-head-unit-microphone=false`, `enable-audio-sink=true`. One session, connect 19:38:15.

- **PASS conditions met:**
  - `SSL handshake complete` — 19:38:15.229 (`Session id: nvqc/Y8fCzo87cNpsUEWpg…`).
  - Video rendering — **45–48 fps, `dropped=0`** for the first ~7 minutes (through 19:45:07), `c2.unisoc.hevc.decoder`, `decodeLatency` 8–9 ms.
  - **`No audio/mic` — 0** in the entire phone capture.
- **The new line is present**, verbatim (19:38:15.994, `Companion.makeProto`):
  > `Head unit microphone is off in Settings. Skipping the microphone service - the phone is told this head unit cannot record, no voice request will arrive here, and this is not a fault`
- **The microphone service is genuinely absent from the announcement.** `Media Sink Setup Request` on channels **VIDEO, AUDIO2, AUDIO1, AUDIO** — **no MIC channel**, no `ID_MIC`.
- **`CarInfoInternal`** (from `dumpsys …gearhead`), fresh record:
  > `CarInfoInternal[dbId=1, manufacturer=MAKE1, model=MODEL2, modelYear=YEAR3, vehicleId=6cd8d9af6961e561, vehicleType=VEHICLE_TYPE_MOTORCYCLE, headUnitMake=HUMAKE5, headUnitModel=HUMODEL6, headUnitSoftwareBuild=1, headUnitSoftwareVersion=0.1.0, known=true, projectionAllowed=true, …]`
  - **`dbId=1`, `vehicleType=VEHICLE_TYPE_MOTORCYCLE`** — a clean fresh record reading MOTORCYCLE, no CAR shadow. The forget + `VEHID4C` did what §3 asked.
- Discard-rule check: pre-connection group retry only (see Setup notes); measurements from the settled session.

## R2 — which recorder does the phone build — the recorder MOVES to the phone

Same session. Assistant trigger route 1 sent host **19:40:35.984**; head unit `Voice Session Notification: START` **19:40:36.096** (phone clock runs ~1.2 s behind).

- **Projection-start recorder decision** (19:38:19.182, phone):
  > `GH.Assistant.Recorder: Using phone microphone`

  **First appearance ever on this rig.** Round 1 called this line's first appearance "the headline result of this round."
- **Recorder built on the trigger** (phone clock 19:40:34.857):
  > `GH.PhoneMicRecorder: Start new recording`
  > `GH.PhoneMicRecorder: Start new recording to output stream: android.os.ParcelFileDescriptor$AutoCloseOutputStream@48d4926`

  **`GH.CarMicRecorder` count in the whole capture: 0.** All 20 `GH.*Recorder` lines are `GH.PhoneMicRecorder`.
- **`vehicleType` the session ran with:** `VEHICLE_TYPE_MOTORCYCLE`, `dbId=1` (active-session `CarInfoInternal` dump matched the pre-connection record).
- **Head unit — no microphone request arrived at all:**
  - `Mic request:` count = **0** (round 1 saw it; this round does not, exactly as predicted).
  - `Initializing AudioRecord` **0**, `mic uplink started` **0**, `capture summary` **0**.
  - (`AapTransport: not taking the microphone (setting useHeadUnitMicrophone=false, available=true)` printed **once** at session init, 19:38:15.120 — a startup log, not a response to a request.)
  - Head unit only saw `Voice Session Notification: START` (19:40:36.096) / `STOP` (19:40:46.363) — the control-channel notification, not a mic request.
- **New failure mode** (brief §0 asked to watch for exactly this):
  > `19:40:44.050 W/GH.PhoneMicRecorder: Recording finished: failed to write to output stream`
  > `19:40:44.050 W/GH.PhoneMicRecorder: java.io.IOException: write failed: EPIPE (Broken pipe)`
  > `    at libcore.io.IoBridge.write(IoBridge.java:651)`
  > `    at kxl.handleMessage(SourceFile:62)` …
  > `19:40:44.051 I/GH.PhoneMicRecorder: Clean up recording … total bytes sent: 278528`

  Fires **~9 s** into the recording, **after 278 528 bytes were delivered**. Recurs on every recording cycle in R2, R3 and R4. Read: the assistant closes its read end at the ~10 s no-speech timeout while the recorder's `HandlerThread` does one more write. **Recognition is unaffected** — R3 and R4 both got results. Gearhead-side, not the head unit's code, and not a connect-time failure. Non-blocking but should be recorded.
- **Dashboard observation** (screenshot `r2-projection.png`, ~2 min into the session): the projected AA Coolwalk UI is the **app rail** (Maps / Spotify / Phone / Settings / mic / launcher) + near-full-width **Google Maps** + a Search overlay. **No persistent dashboard / widget-column panel** is visible. This is not a controlled car-vs-motorcycle comparison (no CAR session to compare against this round) — recorded as an observation per §0.
- **Session stayed up:** SSL handshake count 1, `ByeBye` 0, video ~47 fps continuing after the trigger.

## R3 — spoken query — PASS

Same settings. Trigger sent host **19:43:13.899**; head unit `Voice Session Notification: START` 19:43:13.841 → `STOP` 19:44:16.903 (**63 s**, Gemini multi-turn). Operator spoke 3 times.

- **PASS conditions met:**
  - **The assistant answered.** Three `GH.PhoneMicRecorder: Start new recording` cycles (19:43:12 / 19:43:37 / 19:44:04; bytes sent 221 184 / 286 720 / 155 648). Each → `GH.TranscriptionCtrl: shouldDisplayTranscription() true` (transcription happened, `GhTranscription` virtual display projected to the head unit) → `GH.ToolIntentResultPr: Processing tool status update intent for Google Maps` + `StartNavigationExecutor: … about to launch intent for package` (×3).
  - **Projected screen** (`r3-during-nav.png`): fuel-station results — **"Primax  8 min · 2.6 km"**, **"Terpel - Zenu  5 min · 1.1 km"**. The assistant understood *"navigate to the nearest petrol station"*.
  - **Head unit three lines still all absent:** `Mic request:` 0, `Initializing AudioRecord` 0, `mic uplink started` 0, `capture summary` 0. No `No audio/mic`.
- **The visible cost:** video throughput fell from ~47 fps to **4–9 fps** starting ~19:45:10 (after the 3rd nav launch), held low for ~5 min, brief recovery to 30 fps at 19:47:42. **Phone-side:** `fed` == `rendered` == `presented`, `dropped=0`, head-unit `decodeLatency=8 ms`, `inputWait` rising — the head unit decoder is idle-waiting; the phone is only sending ~5 fps. On the phone at the time: `com.google.android.apps.maps` 42 % CPU + `gearhead:car` 35 % CPU (≈ 280 % total), phone not thermally throttled (`mStatus=0`, 39–40 °C). This is Android Auto's adaptive frame-rate on a static/slow projected scene (parked car, `0 km/h`) plus a mid-range phone rendering a 3D turn-by-turn view. **Not related to the microphone change** — it would happen identically with the mic on. Session never dropped (`ByeBye` 0, one SSL handshake).

## R4 — riding configuration, intercom on the phone — observed (not a verdict run)

`use-head-unit-microphone=false` **and `enable-audio-sink=false`**, `vehicle-id=VEHID4C`. **"KY Pro" connected to the phone** — `A2dpStateMachine state=Connected` and `HeadsetStateMachine state=Connected`, `mActiveDevice=D0:D9:4F:A0:88:AF` for both. Connect 19:54:27 — **clean single group** (`createGroup SUCCESS` ×1, `p2p-wlan0-2`, one Incoming, one SSL handshake).

### Scriptable evidence

- **`Audio sink is off in Settings. Skipping the media and speech audio channels - the phone will not send audio and this is not a fault`** — present (19:54:28.186).
- **`Head unit microphone is off in Settings. Skipping the microphone service …`** — present (still withheld).
- **Channels set up:** VIDEO + **AUDIO2 (SYSTEM sink) only**. `Media Start Request` — **VIDEO only**. No AUDIO1 (speech), no AUDIO (media), no MIC. Exactly the sink-off shape.
- `SSL handshake complete` 19:54:27.382. Video **46–47 fps** at connect; **44–47 fps** throughout the later assistant/nav exchange.
- **Assistant → intercom mic:** `HeadsetService: startVoiceRecognition: device=D0:D9:4F:A0:88:AF` (×2) — Gearhead ran **SCO voice recognition on the KY Pro intercom**. SCO connected (`HeadsetStateMachine: AudioConnecting … Sco connected`). `GH.PhoneMicRecorder` ×6 cycles (multi-turn), transcription projected, `Navigation Focus Request: NAV_FOCUS_2` on the head unit (nav took projection focus).
- Music: `requestAudioFocus() … AA=USAGE_ASSISTANT/CONTENT_TYPE_SPEECH` → Spotify `onAudioFocusChange(-2)` (duck) → `abandonAudioFocus` → Spotify `onAudioFocusChange(1)` (regain).

### Operator report (helmet, "KY Pro" — sensory, not scriptable)

- **Music plays in the helmet:** **yes.**
- **Head unit itself silent:** **yes** (the rig has no speaker; operator confirmed nothing unexpected).
- **Assistant's spoken reply / nav prompt heard in the helmet:** **yes.**
- **Music ducked during the assistant and came back after:** **yes.**
- **Assistant works:** **yes** — recorded from the intercom's SCO mic, transcribed, responded audibly, nav took focus.

### Disconnect / reconnect — INCONCLUSIVE

`headunit://disconnect` 19:59:29 → `AapService: Native AA user exit. Stopping active launcher.` Operator then cycled the phone's Bluetooth **and** WiFi; the phone showed "Android Auto available" but **would not rejoin**. The head unit kept rebuilding 5 GHz groups (`DIRECT-*-Navegadortz2`, `p2p-wlan0-3` / `-4`), listeners open (`NativeAA: ACTIVELY LISTENING on Android Auto UUID`), **no 2.4 GHz fallback** fired. No second SSL handshake after ~10 min + several nudges.

**`wifi-direct-band=1` (5 GHz only) blocks the phone's rejoin** — a rig setting the operator chose to keep, not a candidate defect. R1–R3 (one continuous session surviving two assistant triggers + spoken multi-turn nav over ~17 min) and the first R4 connect all succeeded and were stable, so session *stability* is well demonstrated; only the explicit user-exit → re-handshake is unverified on this rig.

**APK for R4:** the candidate (`8746a797`) throughout.

---

## Report-back items (brief §7)

1. **R1 verdict + `No audio/mic`:** **PASS**, `No audio/mic` = **0**. The analysis is **right** — withholding the microphone service does not end the session.
2. **R2 recorder line:** `GH.PhoneMicRecorder: Start new recording` (19:40:34.857 phone clock), run with **`vehicleType=VEHICLE_TYPE_MOTORCYCLE`**, **`dbId=1`**. Zero `GH.CarMicRecorder`. **The motorcycle route exists.**
3. **Microphone request with nothing announced:** **none** — `Mic request:` = 0 across R1/R2/R3/R4.
4. **New exception:** `GH.PhoneMicRecorder` → `IOException: EPIPE (Broken pipe)` at `kxl.handleMessage` on **every** recording teardown, ~9 s in, **after** bytes are delivered. Recognition unaffected. Gearhead-side teardown race, non-blocking.
5. **Forgot the car:** **yes**, before R1 (Android Auto → forget). Phone→head-unit Bluetooth bond survived.
6. **Dashboard observation:** projected AA Coolwalk UI = app rail + Google Maps, **no persistent dashboard/widget-column panel visible** (screenshot). Not a controlled comparison (no CAR session this round).
7. **R3:** ran, **PASS**. Spoken *"navigate to the nearest petrol station"* → transcribed → fuel-station navigation results on the projected screen (`StartNavigationExecutor` ×3). Head unit three lines absent. (Helmet audio was tested in R4; the intercom was disconnected for R3.)
8. **R4 four observations** — music in helmet **yes**; nav prompts / assistant reply in helmet **yes**; head unit speaker silent **yes**; assistant works (via intercom SCO mic) **yes**; session survives disconnect+reconnect **INCONCLUSIVE** (band=1 rejoin block, not the candidate). Ran on the **candidate** APK (`8746a797`).

## Anything the brief did not ask about

- **`GH.Assistant.Recorder: Using phone microphone` does log on `17.5.663204`** — contra round 1's note that it never appears. Seen at every projection start this round. `Not using phone mic` was never seen (the phone never took that branch).
- **Navigation on a parked car collapses projected video to ~5 fps** (phone-side adaptive frame-rate + Maps 3D render load). Not new to this branch, but the first round to drive a full spoken multi-turn navigation session on this rig. Recovered on its own.
- **The head unit's own bundled `com.google.android.projection.gearhead:car`** memory-cycles every ~60 s during an OHU session. Harmless, noisy in the log.
- **Band=1 connects are variable** — R1's connect took a group-retry cycle (~90 s + nudge), R4's was a clean single group first try.
