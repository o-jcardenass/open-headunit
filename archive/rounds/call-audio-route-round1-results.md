# call-audio-route, round 1 results

**Candidate:** none, measurement on `main` @ `d2d0a6bf71217e8658545314de63ed87c0031451`       **Baseline:** none (arms of one build)
**APK md5:** `ad2ea4ac724d7fa9f13d17a0c75f4029` (`main-d2d0a6bf.apk`, versionName 3.5.0-beta4, versionCode 117; `apk_identity.sh` MATCH on D-HU)
**Unit:** D-HU (MT50, Android 14, `wifi-connection-mode=3`) as head unit; D-MOTO (Motorola edge 30 neo, Android 14, Gearhead `18.0.164204-release.daily`) as phone; D-POCO as the call's far end; KY Pro intercom
**Date:** 2026-10-13 23:57 to 2026-10-14 00:20 (local)

## Setup notes

**Evidence:** release `rig-evidence-call-audio-route`, asset `call-audio-route-round1-captures.zip`, sha256 `621d7f67de7a8e235224bd4ecd6eb20c2c3f54c6c8226e788533d6453d3f5cea`. Phone numbers (the far end's and D-MOTO's own) are replaced by `X` in the zip; the callee number file is not in it.

**Quirk files read:** `rig-quirks/topics/tooling.md`, `bt.md`, `gearhead.md`, `rig-quirks/units/D-HU.md`, `D-MOTO.md`. The skill's `hur-wifi-test-scripts/` path does not exist; `rig-toolkit/` (same folder after the 2026-10-09 rename) was used. Scripts used: `rig_devices.sh`, `rig_preflight.sh`, `rig_thermal.sh`, `wt-new.sh`, `build_hur_cool.sh`, `run_unit_tests.sh`, `apk_identity.sh`, `set_hu_settings_host.py`. No toolkit script was added. Round scripts (`cra_lib.sh`, `run.sh`, `r0.sh`, `cra_keys.sh`, `pick_sim.py`) are in `rig-data/rounds/call-audio-route-round1/scripts/`.

**Pre-flight** (first attempt FAILED, D-MOTO WiFi off and an idle Gradle daemon; operator turned WiFi on, daemon killed by pid; second attempt PREFLIGHT OK):

| Role | WiFi | BT | Screen | Profiles |
|---|---|---|---|---|
| D-HU | 1 | 1 | Awake | HFP:none A2DP:none |
| D-MOTO | 1 | 1 | Awake | HFP:none A2DP:none |
| D-POCO | 1 | 1 | Awake | HFP:none A2DP:none |

**Build and tests.** Worktree at `d2d0a6bf`, `HEAD` read back as the full SHA. `build_hur_cool.sh` was used (the brief's `--max-workers=2` is not in either build script, not edited); host peaked at 86 C and `thermal_guarded.sh` stopped and resumed Gradle twice, build rc 0. Unit tests: 3241 run, 0 failures, 0 errors, 0 skipped. `ACTION_QUERY_STATE` replied `commit` `d2d0a6bf7121-dirty`: the prefix matches, the suffix comes from the untracked `.WORKTREE-INFO` file in the worktree (only untracked file; not chased further).

**Settings.** D-HU's shell is already uid 0 (`su` is absent), so root edits used plain `adb shell`. Backup 9690 bytes, md5 `8d77a54bc54478ab9d19031230eb9f00`, `shared_prefs` and `settings.xml` both `u0_a176:u0_a176`. The install changed nothing (diff empty). Three keys were wrong against the brief's section 4 and were corrected before R0 with `set_hu_settings_host.py` (app stopped): `enable-audio-sink` false to true; `native-poke-bt-macs` held D-POCO's MAC, now D-MOTO's; `last-connected-native-mac` held D-POCO's MAC, now D-MOTO's. `bt-address` already equalled D-HU's own address, `key-codes` absent, `wifi-connection-mode` 3, `connection-modes` {wifi, self}, `native-poke-all-paired` true (left). The arm keys (`use-head-unit-microphone`, `vehicle-type`, `log-level=2`) were written per run by `cra_keys.sh`. At the end the backup was copied back as root; the live file reads byte-identical to the backup (diff empty), so the three corrections above are undone too.

**Deviation 1, the calls go out from D-MOTO.** D-MOTO is the only phone with a call allowance (now in `rig-quirks/units/D-MOTO.md`). The brief's "operator rings D-MOTO" became: D-MOTO dials D-POCO with `am start -a android.intent.action.CALL`, D-POCO is answered by `input keyevent KEYCODE_CALL` on D-POCO, the call ends with `KEYCODE_ENDCALL` on D-MOTO. The brief's `RING` and `HANG UP` cues were not used. D-POCO's second SIM (the one with service) took the calls; its first slot reads ABSENT. D-MOTO is dual SIM, and the dialer asks "Choose SIM for this call"; the operator ruled "always Tigo, no persistent change". Extras to preselect the slot (`com.android.phone.force.slot`, `com.android.phone.extra.slot`) were inert (void attempts kept in `void-attempts-sim-chooser/`). The script therefore taps the TIGO row of the chooser from a `uiautomator dump` (bounds parsed, 603,1337): **injected taps: R0 1, H-car 1 (plus 1 in the void H-car attempt), H-moto 1, I-moto 0 (no chooser appeared; `Target PhoneAccount ... (TIGO)` appears 6 times in its telecom dump), I-car 1.** No D-MOTO setting was changed; the dialer's own "recently used" hint is the only state left. Every call connected on phone account id 1 (`SET_CONNECTING ... 1,` for TC@40 to TC@45), the account the dumps label TIGO.

**Deviation 2, the hands-free link was not up and had to be made.** The brief says D-HU holds a hands-free link to D-MOTO in every run. In the first H-car attempt it did not: `BluetoothLinkMonitor: at session start: hfpClient=off`, D-MOTO's `HeadsetService` showed D-HU as state 0, `mActiveDevice: null`, and the call stayed on D-MOTO's earpiece with no Bluetooth route at all (kept as `out/nolink-attempt/`, not graded). The rig note (a phone drops its Bluetooth link at the Native AA handoff) was applied: after the session came up the script toggles **D-HU's** adapter (`svc bluetooth disable`, 6 s, `enable`) and waits for D-HU's `HeadsetClientStateMachine` to read `curState=Connected`. It came up in 12 s (H-car), 12 s (H-moto), 9 s (I-moto), 12 s (I-car). Every graded run below includes this step; the brief did not have it.

**Deviation 3, the route is read from the Telecom log, not from one dump.** The brief's `ROUTE_RE` terms (`activeBluetoothDevice` and similar) do not appear in this build's `dumpsys telecom`; `Current state: <X>Route` does, so `ROUTE_RE='Current state: [A-Za-z]*Route'` was used. But the dump is taken 5 s after the callee answers, and the route had already left Bluetooth by then in all four graded runs (see below), so each run's route is graded from the timeline in the D-MOTO capture (Telecom `AUDIO_ROUTE, Entering/Leaving state Active*Route` and `HeadsetStateMachine ... audio state changed`). No `SCO_RE` could be derived: R0 had no link, and in the session the D-HU hands-free section differs between `session` and `in-call` dumps only in timestamped record lines. The D-HU side therefore rests on D-MOTO's SCO transitions, as the brief allows.

**Deviation 4, Gearhead 18.0 prints none of the brief's phone-side strings.** Counts in all four graded captures: `GH.Assistant.Recorder: Using phone microphone` 0, `Not using phone mic` 0, `GH.PhoneMicRecorder` 0, `GH.CarMicRecorder` 0, `GH.CallManager: No high priority audio routes available` 0. The assistant trigger did start (`GH.Assistant.Controller: startVoiceSearchMainThread trigger=3`, `handleVoiceSessionStart`). Validity rule 4 was therefore applied through `HeadsetService: startVoiceRecognition: device=` (present on the moto arm, absent on car arms) together with D-HU's own `Head unit microphone is off in Settings. Skipping the microphone` and `AapTransport: not taking the microphone`. `Mic request:` is 0 on the car arms too: the voice session ended about 4.5 s after it started and no microphone request reached D-HU.

**Other notes.** D-POCO's Bluetooth was switched off in R0 (read back `state: OFF`), held off for every run, and switched back on at the end (`state: ON`). D-HU's own `ACTION_LOG_MARKER` was skipped in R0 (`HU_MARK=0`) so the app stayed stopped; phone-side `RIGMARK` markers were written throughout. R0's first launch was refused by its own key readback (the device `grep` does not take `\|`; readback rewritten) before any capture; one further R0 attempt and one manual extras trial did not place a call (SIM chooser); all are void and nothing in them is graded. The phone's radios stayed up between runs (brief section 3). Host thermal: max 62 C, `throttle_pkg=0` in every run. Captures end within 1 to 2 s of the kill time. `logcat` was killed by pid after each run; none left running at the end. The one operator ask beyond the brief: a second D-POCO question (did its speaker play your voice) was never answered.

**Discard rules**, all five graded runs: `MATCH! Starting AapService` 0, `createGroup SUCCESS` 1, one `p2p-wlan0-N` interface per run (N = 0, 1, 2, 3, 4 across R0's absent group, H-car, H-moto, I-moto, I-car: each run force-stops and re-forms its own group), `Magic Garbage` 0, one `SSL handshake complete`. The mid-run Bluetooth toggle raised no second session.

## R0 (gate and calibration, no session)

**PASS**

- Settings written: `use-head-unit-microphone=true`, `vehicle-type=1`, `log-level=2` (read back).
- Radio state: D-POCO Bluetooth off; D-HU app stopped; D-MOTO and D-HU radios on.
- Calls: `00:02:40 dial`, `00:03:03 answered` (`calls.tsv`), D-POCO rang and was answered by keyevent.
- Route: `Current state: ActiveEarpieceRoute`, `active=mActiveDevice: null` at idle, pre-call and in-call; no Bluetooth route (no hands-free link with the app stopped).
- KY Pro: `D0:D9:4F:A0:88:AF` in D-MOTO's bonded list. D-HU's own address `11:46:03:10:33:59`. D-MOTO `A0:46:5A:97:E4:95`.

## R1, H-car (control, intercom off)

**PASS**

- Settings written: `use-head-unit-microphone=true`, `vehicle-type=1`, `log-level=2`.
- Counts: `SSL handshake complete` 1; mic-off line 0; `not taking the microphone` 0; `Mic request:` 0; `BT MAC Address is empty` 0; `startVoiceRecognition: device=` 0; `PhoneMicRecorder` 0 (string absent in this Gearhead).
- `active=` at session, pre-call, in-call: D-HU (`11:46:03:10:33:59`) in all three.
- Call route timeline: `00:08:40.591 Entering state ActiveBluetoothRoute`; SCO `AudioOn` to D-HU `00:08:40.99`; `00:08:51.441` SCO `AudioOn -> Connected`; `00:08:51.468 Entering state ActiveEarpieceRoute`. Bluetooth window 10.9 s. Callee answer logged `00:08:53` (poll interval 2 s).
- 5 s after answer the telecom dump reads `ActiveEarpieceRoute`.

## R2, H-moto (intercom off)

**PASS**

- Settings written: `use-head-unit-microphone=false`, `vehicle-type=3`, `log-level=2`.
- Counts: `SSL handshake complete` 1; `Head unit microphone is off in Settings. Skipping the microphone` 1; `AapTransport: not taking the microphone` 1; `Mic request:` 0; `BT MAC Address is empty` 0; `PhoneMicRecorder` 0 and `CarMicRecorder` 0 (strings absent).
- Assistant SCO device, quoted: `10-14 00:11:33.243 I/HeadsetService: startVoiceRecognition: device=11:46:03:10:33:59, uid/pid=10161/0` (D-HU).
- `active=`: D-HU at session, pre-call, in-call (same as H-car, twin rule met).
- Call route timeline: `00:11:58.825` SCO connecting to D-HU, `00:11:59.001 Entering state ActiveBluetoothRoute`; `00:12:09.693` SCO `AudioOn -> Connected`; `00:12:09.709 Entering state ActiveEarpieceRoute`. Bluetooth window 10.7 s. Callee: `00:12:05 ringing`, `00:12:09 answered`.

## R3, I-moto (the point of the round)

**PASS**

- Settings written: `use-head-unit-microphone=false`, `vehicle-type=3`, `log-level=2`. KY Pro connected for hands-free in D-MOTO's `HeadsetService` before the run (gate).
- Counts: `SSL handshake complete` 1; mic-off line 1; `not taking the microphone` 1; `Mic request:` 0; `BT MAC Address is empty` 0.
- Assistant SCO device, quoted: `10-14 00:15:14.899 I/HeadsetService: startVoiceRecognition: device=11:46:03:10:33:59, uid/pid=10161/0` (D-HU, not the intercom).
- `active=`: D-HU at session, pre-call, in-call. The KY Pro was connected but never the active device.
- Call route timeline: `00:16:04.431` SCO connecting to D-HU, `00:16:04.765 Entering state ActiveBluetoothRoute`; `00:16:14.615` SCO closed; `00:16:14.642 Entering state ActiveEarpieceRoute`. Bluetooth window 9.9 s. Callee: `00:16:10 ringing`, `00:16:14 answered`.
- Operator (sensory, not counted): "I heard the call in D-MOTO, not on the intercom." The question whether D-POCO's speaker played the operator's voice was not answered.

## R4, I-car (twin of R3)

**PASS**

- Settings written: `use-head-unit-microphone=true`, `vehicle-type=1`, `log-level=2`. KY Pro connected for hands-free (gate).
- Counts: `SSL handshake complete` 1; mic-off line 0; `not taking the microphone` 0; `Mic request:` 0; `startVoiceRecognition: device=` 0; `BT MAC Address is empty` 0.
- `active=`: D-HU at session, pre-call, in-call, identical to I-moto (twin rule met on the first try).
- Call route timeline: `00:19:18.958` SCO connecting to D-HU, `00:19:19.300 Entering state ActiveBluetoothRoute`; `00:19:30.019` SCO closed; `00:19:30.054 Entering state ActiveEarpieceRoute`. Bluetooth window 10.7 s. Callee: `00:19:26 ringing`, `00:19:32 answered`.
- Operator (sensory, not counted): "I heard the call in D-MOTO, not on the intercom." D-POCO speaker question not answered.

## Report back (brief section 9)

1. **I-car against I-moto:** the same. Both: `active=` D-HU at all three reads; the call goes to D-HU over Bluetooth from dial for 10.7 s (I-car) and 9.9 s (I-moto), then to D-MOTO's earpiece. The motorcycle claim does not move the call. The intercom stayed connected but never became the active hands-free device.
2. **H-car against H-moto:** the same. Bluetooth to D-HU for 10.9 s (H-car) and 10.7 s (H-moto), then earpiece. D-HU's in-call SCO state could not be read from its own dump (no field distinguishes idle from in-call); on D-MOTO's side SCO to D-HU was `AudioOn` only inside those windows.
3. **H-moto's assistant SCO device:** D-HU (`startVoiceRecognition: device=` D-HU at `00:11:33.243`). On the car arms there was no `startVoiceRecognition` line.
4. **Operator sensory report, apart from the counts:** in both I runs the call was heard in D-MOTO and not on the intercom, which agrees with the earpiece route the logs show once the callee answered.

## Anything the brief did not ask about

- **In all four graded runs the call leaves Bluetooth at the moment the far end answers**, and what the user hears for the answered part is D-MOTO's earpiece. The Bluetooth window (9.9 to 10.9 s) is dial and ringing only. The drop coincides with the logged answer to within the 2 s poll in H-moto, I-moto and I-car (H-car logged the answer 1.5 s after the drop). The four windows also share a length near 10 s, so a fixed timer cannot be told apart from the answer with these runs. A run that holds the answer back about 25 s would separate them. Not run, the brief did not ask.
- **The call's route is not decided by the vehicle claim or the microphone setting at all**; it follows the hands-free link, which exists only after a head-unit Bluetooth toggle. Without the toggle the call never touches Bluetooth (`out/nolink-attempt/`).
- **D-HU's Native AA session does not keep a hands-free link** (`hfpClient=off` at session start), contrary to the brief's premise. The toggle is a rig lever, not something the app does.
- **The assistant opened SCO to D-HU even in I-moto**, because D-HU held the active slot. The brief's "the intercom, if it is the active device" case was never reached.
- **Gearhead 18.0 changed its phone-side strings** (none of the five recorder or call-manager strings appear). `gearhead.md` should note 18.0.164204 next to the 17.5 and 17.3 entries.
- **D-HU's wake list pointed at D-POCO** at round start (both `native-poke-bt-macs` and `last-connected-native-mac`), and `enable-audio-sink` was `false`: leftovers from earlier threads, the same trap `bt.md` describes.
- **D-MOTO's dialer chooser** appeared in four of five dials; in I-moto it did not, with no change in state between runs that was found. The script treats an absent chooser as proceed.
