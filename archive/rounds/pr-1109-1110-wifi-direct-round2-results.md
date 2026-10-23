# pr-1110-wifi-direct-wedge (with the banded-refusal memory change) - round 2 results

**Candidate:** `review/pr-1109-1110` @ `fc3469823c693a708ec03ead6022dc26d57fbf98` (tree `ecdeaaa3d300ebcecc3a8efa03abde76fd94fe49`)   **Baseline:** none (settings are the controls)
**APK md5:** `aa23f8178d040ae61795a9b92fa5c5fc` (C, one arm). Live APK on D-HU hashed equal before every run (`apk-check.log`).
**Unit:** D-HU (UNISOC MT50, Android 14, 1440x720 landscape, uid 0, no `su`, `ro.adb.secure=1`) with D-POCO (POCO X3 NFC, Gearhead 18.0.664204-release) as the phone. D-MOTO Bluetooth off all round (already OFF at pre-flight, disabled again and read back).
**Date:** 2026-10-21. D-HU's own clock reads 06-17 in its logcat; times below are that clock.

Verdicts: R1 PASS, R2 PASS, R5a PASS, R5b INCONCLUSIVE, R5c UNTESTABLE, R3 PASS (second attempt), R4 PASS, R4b UNTESTABLE.

## Setup notes

- **Sync.** `rig_sync.py` reported one `IN` path (`rig-sdlc/rig-env/settings.hooks.json`, a session-end hook scoped to StudioProjects); committed and pushed as `Rig SDLC: scope session-end status hook to StudioProjects`, second sync clean. No restart needed.
- **Transfer worktree.** `transfer/rig-rounds` was already checked out in a clean scratch worktree (`worktrees/rig-sync__scratch__8228cb07__20261015`), so `git checkout -B` refused in the main checkout. That worktree was fast-forwarded and used; the main checkout stayed on `main` throughout.
- **Read first:** `rig-quirks/topics/tooling.md`, `wifi.md`, `lifecycle.md`, `projection.md`, `units/D-HU.md`, `units/D-POCO.md`; the template without 7b and 8; the TOOLS.md "Pick a tool" table. Tools used: `rig_devices.sh`, `rig_preflight.sh`, `rig_cleanup.sh`, `rig_thermal.sh`, `wt-new.sh`, `build_hur_cool.sh`. No toolkit script was added.
- **Opening pre-flight** (`rig_preflight.sh D_HU:wifi,bt D_POCO:wifi,bt`): PREFLIGHT OK. D_HU wifi=1 bt=1 Awake HFP:none A2DP:none; D_POCO wifi=1 bt=1 Awake HFP:XX:XX:XX:XX:33:59 A2DP:up. `rig_cleanup.sh list`: no strays, lock free.
- **Build.** Worktree `pr-1109-1110-wifi-direct__scratch__fc346982__20261021` detached at `fc3469823`, tree matched `ecdeaaa3`. `rig_thermal.sh wait 70` (69C), `build_hur_cool.sh` with `MAX_WORKERS=2` (about 4 min, warm caches). Unit tests not run on the rig. Dex checks: `banded refusal remembered` x1, `WifiStackRecovery` x13. `ACTION_QUERY_STATE` returned `"commit":"fc3469823c69"`. `ACTION_LOG_MARKER` is not in `AutomationCommandPolicy` CONFIGURING, so no `allow-external-configuration` write was needed.
- **Brief script extraction.** `extract_brief_blocks.py` only matches backticked block names and this brief writes `**name.sh**`, so the blocks were extracted with the same logic and a relaxed regex into `scripts/`. `lib.sh` `OUT` was made absolute. `p_facts.sh` was run by hand. Wrapper `run.sh` sources `lib.sh` and `tap.sh`, exports `WANT_MD5`.
- **First R1/R2 launch aborted before any device command**: the wrapper had not exported `WANT_MD5`, so `apk_check` refused (`APK_MISMATCH`). Fixed, the stray thermal watcher it left was killed by pid, relaunched. No data from the aborted attempt.
- **Pre-flight facts (P).** uid 0, `su_rc=1`, `ro.adb.secure=1`, `PORT5555_AT_P=0`, `wm size` 720x1440, `groupFormed: false` at rest, `shared_prefs` owner `u0_a176:u0_a176` (the app's uid; `stat -c '%a'` is unsupported on this toybox, owner printed), fingerprint `UNISOC/uis7861_6h10_Natv/uis7861_6h10:14/UP1A.231005.007/eng.wming.20260518.231016:user/release-keys` (no `|` or `&`). D-POCO `isGroupOwner` false; it was showing Spotify, so `KEYCODE_HOME` was sent (launcher3 after).
- **DEVIATION: D-HU's station WiFi was on but not joined all round** (`SSID: <unknown ssid>`, `Frequency: -1`, `DISCONNECTED`) at P, after install and in R1, R4 and R4 live. Round 1 had a joined station. So `sta` and `sta_live` have no value and the R4 comparison of the no-band group's frequency with the station's has no station side.
- **Settings.** Baseline saved as `settings-backup.xml` (9636 bytes). Recorded, unchanged: `connection-modes` = {`wifi`, `self`}; `native-poke-bt-macs` = {`DC:B7:2E:5E:4E:59`}; `native-poke-all-paired` true; `native-driver-selection-mode` 1; `wifi-direct-stable-identity` true; `narrow-band-profile-cap` true; `video-profile-starvation-cap` absent; `wifi-direct-band` 1 as found (the base set 0 for every run). Base block and per-run table applied through the brief's `hu_kv`/`base`. Settings delta: see the closing diff at the end of the round (section below).
- **Windowing.** Every count is windowed by `AutomationMarker` lines (HU clock) or by the phone-marks (phone clock). Two window artifacts: (1) in R5a the banner line (05:07:23.294) prints 0.6 s before the `R5a-launch` marker (23.908), so the brief's `waitafter` timed out and `cand` read `banner=0`; graded on the whole-capture line and the screenshot. (2) In R4 the phone's `Creating rfcomm socket` line (phone 16:39:12.010) is 1 s before the `R4-phone` mark (16:39:13), so `rf` read 0 in the window and 1 over the whole capture.
- **Tap budget bug in the brief.** `RUN=R5 th_gate || exit 1` is a prefix assignment, so `RUN` stayed empty and the tap budget file was `taps-.n`. R5 attempt 1 used 2 injected taps (R5a banner, R5b banner) plus one VOID dump (R5c), all within 4. R4 was run with `RUN=R4` exported (own budget): 0 taps sent (both rows VOID). Injected taps this round: attempt 1 R5a banner at (720,123), attempt 1 R5b banner at (720,123), attempt 2 R5a banner at (720,123). No Allow tap was needed.
- **R5 was run as one shell** (`r5all.sh` sources `r5a.sh`, `r5b.sh`, `r5c.sh` unmodified) so `HOLDPID` persists across them.
- **Bluetooth profile link and the re-runs.** After R5 attempt 1 and R3 attempt 1 the mid-round pre-flight read D_HU HFP:none A2DP:none and D_POCO HFP:none A2DP:none (opening table had D_POCO HFP and A2DP up). In both runs the session never came back after the phone's BT returned: R3 attempt 1 `rf=0` (the phone's Gearhead logged nothing wireless), R5b attempt 1 HU pokes `Poke via HFP-AG/HSP-AG ... failed: read failed` at 05:08:26, 05:08:41, 05:09:29, 05:09:54 and the phone's RFCOMM to the HU timed out (`RFC_MX_EVENT_TIMEOUT state:RFC_MX_STATE_SABME_WAIT_UA`, phone clock 16:22:46). The documented lever, a head-unit Bluetooth toggle with the app stopped (`svc bluetooth disable; sleep 5; svc bluetooth enable; sleep 20`), brought D_POCO HFP and A2DP back, read back with `rig_preflight.sh`. This was done between runs as rig-state maintenance, not inside any run. R3 was then re-run once (second attempt formed a session in 8.0 s). A one-off extra HU BT toggle also happened before R4 because my check logic misread the table; the link was already up.
- **Idle Gradle daemon** (left by the build) was the only stray at the mid-round pre-flight; `rig_cleanup.sh kill`, pre-flight re-run clean.
- **R5 re-run (attempt 2).** Attempt 1 files are kept as `R5try1-*` (`R5try1-a-*` is R5a, `R5try1-b-*` is R5b, renamed with plain `mv`; `r5try1.log`, `taps-try1.log`). Attempt 2 restored the link by an HU BT toggle (app stopped), brought up a session first (`R5pre` capture, 20 s), then ran the brief's R5a/R5b/R5c unmodified with `RUN=R5`. Its `link` line in `r5.log` read D_POCO wifi/bt 0 only because R4's end had switched the phone off before my check. By then `PORT5555` was open from attempt 1, so attempt 2's R5a tap took the ADB route (not the panel route), see R5b.
- **Discard-rule checks.** R12: `createGroup SUCCESS` x1, `MATCH! Starting AapService` x0, `p2p-wlan0-1` only. R3 attempt 2: `createGroup SUCCESS` x4 (1 launch plus 3 toggles, expected), `MATCH!` x0, one `SSL handshake complete`. R4: x1, x0, `p2p-wlan0-1`. R5 attempt 1 R5b: first-group x1 before the restart then one after, `MATCH!` x0.
- **Settings delta: zero.** `settings-backup.xml` was restored as root (`cp`, then `chown` to the app's uid 10176 and `chmod 660`), read back, and `diff settings-backup.xml settings-final.xml` printed nothing.
- **End of round.** App stopped; `adb -s <D-HU> usb` (port 5555 was closed at P), `id -u` 0 on the first try, port count 0; D-HU `svc wifi enable` (`enable_tries=1`), `groupFormed: false`, `svc power stayon false`; D-POCO WiFi and Bluetooth on, `isGroupOwner: false`; D-MOTO Bluetooth OFF as found. No logcat or thermal watcher left (`ps`), `rig_cleanup.sh list`: no strays, lock free. Armed monitors had all ended on their own.
- **Closing pre-flight:** PREFLIGHT OK. D_HU wifi=1 bt=1 Awake HFP:none A2DP:none; D_POCO wifi=1 bt=1 Awake HFP:none A2DP:none. The opening table had D_POCO HFP:..33:59 A2DP:up; the profile link is not restored at the end (it drops after every handoff and phone BT cycle, see the re-run note), radios themselves are in the opening state.
- **Evidence:** release `rig-evidence-pr-1110-wifi-direct-wedge`, asset `pr-1110-wifi-direct-wedge-round2-captures.zip`, sha256 `bf287888ed2ce516279b272712b0de95095356521f4a6055b44663ab17936de7` (17678056 bytes; APKs excluded, md5 above). Build worktree removed.

## R1 - positive control (first bring-up, banded create works)

**PASS**

- Settings written: base block; `auto-restart-wifi-p2p-stuck` true; `connection-issue-p2p-stuck`, `connection-issue-dismissed-at`, `wifi-direct-banded-refused-on` deleted. `R1 stuck=absent banded=absent band=0 optin=true`.
- Radios: phone `svc wifi disable` plus `svc bluetooth disable` before launch, back on after the group (`ph_on`).
- Decisive lines: `04:59:48.930 WifiDirectManager: 5GHz createGroup SUCCESS!`; `04:59:49.107 WifiDirectManager: Native AA WiFi Direct: 5GHz (5805 MHz), 5GHz requested, SSID=DIRECT-QS-MT50YT610E4GFPSLU`.
- Counts (window `R1-start` to `R1-end`): `banded_req=1 banded_ok=1 recorded=0 skip=0 wedged=0 restart=0 cannot=0 held=0 banner=0 forget=0`, `edges=0 fails=0 creates=1`, `msr=2 sdr=1 thr=4 ssl=1`, phone `rf=1 crit=0`, `session_wait=15` s.
- Measurements: `freq=5805` MHz, `sta=Frequency: -1` (not joined), `linkCapped=none`.

## R2 - three force-stops inside a session

**PASS**

- Settings as R1 (opt-in on). R2-3 left its session up.
- Per rep (`R2-n-kill` to `R2-n-end`): all three `wedged=0 restart=0 restarted=0 cannot=0 held=0 recorded=0 skip=0 forget=0 banner=0`, `stuck=absent banded=absent band=0`, `afterkill groupFormed: true`, `adopt=1 groups=0 creates=0 edges=0 fails=0`, `thr=5`, `crit=0`.

| Rep | sess_s | msr | sdr | ssl | rf | tcp |
|---|---|---|---|---|---|---|
| R2-1 | 9.5 | 2 | 1 | 1 | 5 | 0 |
| R2-2 | 11.0 | 2 | 1 | 1 | 6 | 0 |
| R2-3 | 11.4 | 2 | 1 | 1 | 6 | 0 |

- Decisive line (each rep): `05:01:00.306 WifiDirectManager: a group named DIRECT-QS-MT50YT610E4GFPSLU is already up from before this bring-up; reading it instead of tearing it down.`
- Reach, as the brief asks: `edges=0 fails=0 creates=0` in every rep, so the banded-refusal path was not reachable in R2 and only the false-wedge half of the wedge change was tested.

## R5a - the stuck banner, port 5555 closed

**PASS**

- Settings written: base; `auto-restart-wifi-p2p-stuck` false; `advanced-settings-active` true; `connection-issue-p2p-stuck` = D-HU epoch ms (`1781690837000`); dismissed-at and banded key deleted. `afterkill groupFormed: true` (the group survived the force-stop).
- Radios: `ph_off`, `ph_hold` (phone WiFi held down).
- Decisive lines: `05:07:23.294 MainActivity: showing the connection issue banner for WIFI_DIRECT_STACK_STUCK` (whole-capture count 1; before the marker, see Setup notes); banner text in `R5try1-R5a-banner.png` reads `WiFi Direct on this unit is stuck and only a WiFi restart frees it. Tap to restart WiFi.` After the tap: `R5a focus=... com.android.settings/com.android.settings.panel.SettingsPanelActivity`.
- Counts: `restart=0 restarted=0 cannot=0`, `panel_fail=0` (no `could not open the WiFi panel`), `adopt_at_launch=1`.

## R5b - the same banner with port 5555 open (ADB route)

**INCONCLUSIVE**

- ROUTE=ADB (`R5b port=1`, then `port=4` listener lines in attempt 2). Settings as R5a, opt-in off.
- **Finding on the dialog: NOT REPRODUCED.** `dialog=0` in both attempts, focus after the banner tap was `MainActivity` (no `UsbDebugging` activity), `adb_ok=1`. The app's key was already trusted on D-HU or the unit did not ask; `R5b-dialog.png` does not exist.
- **Restart (attempt 1, `R5try1-b-hu.txt`):** PASS. `05:08:27.817 AutomationMarker: R5b-tap`; `05:08:30.781 WifiStackRecovery: restarting WiFi via ADB (banner tap).`; `05:08:34.384 AdbManager: Command 'svc wifi disable && sleep 3 && svc wifi enable' executed successfully via Self-ADB.`; `05:08:34.386 WifiStackRecovery: WiFi restarted via ADB.` `to_ok_s=3.6`, `adb_fail=0 adb_timeout=0 rfail=0 cannot=0`, `wifi_back=1`. Toast `Restarting WiFi...` visible in `R5try1-R5b-1s.png`.
- **Group back:** PASS. `05:08:34.703 P2P enabled, auto-starting Native AA quiet host`; `05:08:35.364 5GHz createGroup SUCCESS!`; `05:08:35.458 Native AA WiFi Direct: 5GHz (5200 MHz), 5GHz requested`. `group_s=1.0`, `rearm=1`, `scan_needed=0`. Attempt 2 repeated this from its R5a tap on the open port: `05:29:42.144 restarting WiFi via ADB (banner tap).`, `05:29:45.374 ... executed successfully via Self-ADB`, `05:29:45.377 WiFi restarted via ADB.`, `05:29:45.673 P2P enabled, auto-starting Native AA quiet host`, `05:29:46.342 5GHz createGroup SUCCESS!` (5745 MHz), again 0.97 s after the restart returned, again no dialog.
- **Session back:** not measurable. Attempt 1: `sess_s=none msr=0 sdr=0 thr=0 ssl=0`, phone `rf=1`; the BT profile link was down at the start (same state as R3 attempt 1, which formed a session once the link was restored). Attempt 2: the first restart consumed the stuck record (`stuck=0` afterwards), so the R5b banner tap was VOID (`matches=0`), `waitafter` for the restart line therefore timed out and the script sent `ACTION_START_WIRELESS_SCAN` (`05:33:00.868`), which created a second group (`05:33:01.643`, 5200 MHz); the session then formed at `05:33:11.702` (`sess_s=7.2 msr=2 thr=3 ssl=1 rf=0 crit=0`). That session is not evidence about the group the restart created, so "session back without the WiFi button" is still unmeasured, and so is whether the re-armed group serves a phone.
- Verdict reasoning: no FAIL condition was met (restart line present, `rfail=0`, `adb_fail=0`); the PASS condition on the session could not be produced either time on this rig state.

## R5c - the opt-in toast

**UNTESTABLE**

- `row_offscreen_or_void`: `tapnode` found `matches=0` for `settingSwitch` in the row `Restart WiFi when WiFi Direct gets stuck` in the first-screen dump, in both attempts; `R5try1-R5c-1s.png` shows the General rows (Auto-Optimize Settings, Permissions, Connection mode) only. No scroll by rule, no tap sent.
- `optin` in `R5-end` reads `false` (never saved), `stuck=0`.

## R3 - WiFi toggles with the port open (false-wedge guard)

**PASS**

- Second attempt (attempt 1, `R3a-*`, had no BT profile link and no session: `sess_s=none rf=0`, its false-wedge counts were the same: `wedged=0 restart=0 adb_ok=0 edges=3 fails=0`).
- Settings: base, opt-in true. Radios: phone off until toggles end. Cycles c1 8 s off, 12 s on; c2 and c3 3 s off, 5 s on; every `hu_wifi_on` read `enable_tries=1`.
- Window all (`R3-start` to `R3-end`): `wedged=0 restart=0 restarted=0 cannot=0 held=0 recorded=0 skip=0 forget=0 banner=0`, `adb_ok=0`, `R3 stuck=absent banded=absent band=0 optin=true`, `rearm=3`, `nosess_ssl=0`.
- Session: `sess_s=8.0 msr=2 sdr=1 thr=3 ssl=1`, phone `rf=1 crit=0`.
- Reach: in the toggle window `edges=3 fails=0 creates=3 groups=3`, so the wedge concern (`edges>=2` and `fails>=1`) was not reachable on this unit.
- Decisive lines: `05:21:43.217`, `05:22:08.662`, `05:22:22.125 WifiDirectManager: WIFI_P2P_STATE_CHANGED_ACTION state=1`.

## R4 - the remembered-refusal skip route (synthetic)

**PASS**

- Settings: base; opt-in true; `advanced-settings-active` false; `wifi-direct-banded-refused-on` = D-HU's fingerprint. `R4 group_at_start=groupFormed: false`. `R4-pre` and `R4-live` both read `banded=` equal to the fingerprint, `band=0 optin=true stuck=absent`.
- Decisive lines: `05:25:42.614 WifiDirectManager: this unit refused every 5GHz group request on this firmware before and then hosted the no-band group, so the band request is skipped. Changing the WiFi Direct band setting asks again.`; `05:25:42.859 WifiDirectManager: Native AA WiFi Direct: 5GHz (5180 MHz), standard (banded refusal remembered), SSID=DIRECT-QS-MT50YT610E4GFPSLU`.
- Counts (`R4-start` to `R4-session-end`): `skip=1 remembered=1 banded_req=0 std_ok=1 forget=0 recorded=0 wedged=0 restart=0 cannot=0 held=0 banner=0 adopt=0 creates=1 groups=1 fails=0 edges=0`, `msr=2 sdr=1 thr=4 ssl=1`, phone `crit=0`, `rf=0` in the marker window and 1 over the whole capture (window artifact, Setup notes; phone line `16:39:12.010 Creating rfcomm socket for device: 11:46:03:10:33:59`).
- **Key measurement, one line:** no-band group `freq=5180` MHz; `sta=-1`, `sta_live=-1` (station not joined); `linkCapped=none` (`05:25:57.793 [RES_CAP] resolutionId=2 ... chosen=_1280x720 capped=_1280x720 ... linkCapped=none`); `video-profile-starvation-cap` absent; R1 `freq=5805`. `freq` > 4000, so the review concern (no-band group on 2.4 GHz turning on the narrow-band cap) is **NOT REPRODUCED on D-HU**. The station reference was missing, so "same channel as the station" is not assessed.

## R4b - the clear on a band change

**UNTESTABLE**

- `row_offscreen_or_void`: `R4 band5 VOID matches=0`; the dump (`R4-band5-ui.xml`) has no `WiFi Direct band` text. No scroll by rule, no Save sent, so `R4b` rows are absent. `banded` stays equal to the fingerprint until the closing restore.

## Anything the brief did not ask about

- **A WiFi restart on this unit re-arms the Native AA group within about 1 s**, both times (0.97 s and 1.0 s after `WiFi restarted via ADB.`), at a different frequency each time (5200, 5745 MHz) and always a banded 5 GHz create. The restart does not add a second group, and the app's `P2P enabled, auto-starting Native AA quiet host` is what builds it.
- **The banner tap consumes the stuck record.** `connection-issue-p2p-stuck` read `0` after the first ADB restart, so a second tap in the same session finds no banner (R5b attempt 2 VOID). A brief that taps twice has to reseed.
- **The BT profile link is the gating state for any phone-driven run after a phone BT cycle**; the first R5b and R3 attempts failed identically with the link down and R3 passed 8.0 s after a link restore. The brief's R5 and R3 both switch the phone's BT off for minutes, then expect a session. Worth putting the HU BT toggle into the brief's helpers.
- **R2 relaunches adopt the surviving group** (`adopt=1 creates=0` in all three reps), as in round 1, so the banded-refusal write path stays out of reach in any force-stop run.
- **The pre-flight `rig_preflight.sh` fails on an idle Gradle daemon** left by the build, which the skill already covers; it cost one `rig_cleanup.sh kill`.
- **The R4 no-band group landed on a 5 GHz channel** (5180 MHz) here; other units may differ.
