# fps-overlay-compact, round 1 results

**Candidate:** `feat/fps-overlay-compact` @ `10905b633f4c065a48f4945fd0325ce99cfde1db` (tree `98bd0d721fe0cce8b0fc30fddb6f9858e90ef0ef`, per the addendum)   **Baseline:** none (`main` @ `145a0c762f0a87386ca63b54518ea5e861b290be`, not built)
**APK md5:** `af8865f8944eda051ab57fe62dd47947` / none
**Unit:** D-HU (UNISOC MT50, Android 14, `wm size` 720x1440, Native AA) with D-POCO (POCO X3 NFC, Gearhead `17.9.664004-release`) as the phone
**Date:** 2026-10-06 (R5, U1, U2 finished 2026-10-07 00:01 to 00:05)
**Evidence:** release `rig-evidence-fps-overlay-compact`, asset `fps-overlay-compact-round1-captures.zip`, sha256 `cbf85bb3c8e96e602cc33466996d9a8960e93af6215c0204fa9db9f394a74f4e` (APKs left out)

Verdicts: R0 PASS, R1 PASS, R2 PASS, R3 PASS, R4 PASS, R5 PASS, U1 PASS, U2 PASS.

## Setup notes

- **Brief and addendum.** Run from the brief plus `fps-overlay-compact-round1-addendum.md`. The four `git` checks gave `10905b633f4c065a48f4945fd0325ce99cfde1db`, tree `98bd0d721fe0cce8b0fc30fddb6f9858e90ef0ef`, merge base `145a0c762f0a87386ca63b54518ea5e861b290be`, and 2 commits. The brief's own `398b54b1` and `3` are superseded by the addendum.
- **Quirk files read:** `rig-quirks/topics/tooling.md`, `rig-quirks/units/D-HU.md`, `rig-quirks/units/D-POCO.md`. The template without sections 7b and 8 was read in full.
- **Pre-flight failed twice before it passed, both rig faults.** First: D-POCO was not in `adb devices` under `4f4027e9` (only reachable over wireless adb, which the airplane-mode steps would have cut). Then D-POCO's Bluetooth was off (`bluetooth_on=0`). The operator replugged it and switched Bluetooth on; nothing on the rig was changed by the host. Final table:

  | ROLE | ADB | WIFI | BT | SCREEN | BT-PROFILES |
  |---|---|---|---|---|---|
  | D_HU | ok | 1 | 1 | Awake | HFP:none A2DP:none |
  | D_POCO | ok | 1 | 1 | Awake | HFP:XX:XX:XX:XX:33:59 A2DP:up |

  D-POCO's bonded list includes `Navegadortz3` (D-SAM's radio) beside `Navegadortz2`; every session still connected on the first try, so the stale-bond quirk did not bite. D-MOTO was on the rig and not used; its radios were not touched.
- **No `su` on D-HU.** The brief's `su -c '...'` for the backup and the owner check fails with `su: inaccessible or not found`, because `adbd` already runs as root (`uid=0`, context `u:r:su:s0`). Both commands were run without `su -c`. The owner read `u0_a176:u0_a176` for `/data/data/<pkg>` and for `shared_prefs`, so no `chown` was needed. `hu_put` never used `su` and worked as is.
- **`DEST`.** `aapt2` is not on the rig (`ANDROID_HOME` empty). The id came from the build's `R.txt`: `performanceOverlaySettingsFragment` is `0x7f090247`, so `DEST=2131296839`.
- **Build.** `HUR_DIR=../fps-overlay-compact-r1` (scratch worktree, `local.properties` copied), `GRADLE_OPTS=-Dorg.gradle.workers.max=2`, `rig_thermal.sh wait 75` first (56C). `build_hur.sh` exit 0, APK copied to the round folder at once. `run_unit_tests.sh` exit 0; JUnit XML count: 2755 tests, 0 failures, 0 errors, 0 skipped, `PerformanceOverlayPolicyTest` 25 tests. DEX identity: `PerformanceOverlaySettingsFragment=21 PerformanceOverlayField=4 PerformanceOverlaySource=3 entrySummary=5`. The host reached 77C right after the build and was back at 69C before R1.
- **Install and identity.** `adb install -r` (Success). `apk_check`: `live=af8865f8944eda051ab57fe62dd47947 want=af8865f8944eda051ab57fe62dd47947`. `ACTION_QUERY_STATE`: `"commit":"10905b633f4c"`, `"versionCode":116`, `"wifiMode":"NATIVE"`. The install did not touch `settings.xml` (mtime 23:14, before the install).
- **Baseline `settings.xml`** (`settings-backup-HU.xml`, 9636 bytes, taken before the install). Round-relevant keys as found: `show-fps-counter=true`, `overlay-position=1`, no `overlay-fields`, `hud_mirroring=false`, `wifi-connection-mode=3`, `log-level=2`, `onboarding-version=2`, `view-mode=2`, `connection-modes={wifi,self}`, `native-driver-selection-mode=1`, `native-poke-all-paired=true`, `wifi-direct-stable-identity=true`. Audio keys, recorded and never written: `use-aac-audio=false`, `audio-latency-multiplier=8`, `audio-queue-capacity=20`, `enable-audio-sink=false`. `SYSTEM_ALERT_WINDOW: default`. **Delta against the last D-HU round: not stated.** No earlier D-HU backup comes from a round with the same keys, and I did not diff against an unrelated thread's file. `connection-modes` does not name Native AA by that word; the mode under test is selected by `wifi-connection-mode=3`, and every session run formed a Native AA session, so the combination works.
- **Scripts.** Folder `hur-wifi-test-scripts/fps-overlay-compact-round1/`: `ohu_lib.sh`, `ohu_setkeys.py`, `ptr_lib.sh` (copied from `projection-teardown-and-relays-round3/`), `fo_lib.sh`, `fo_ui.py`, `fo_check.py` (verbatim from the brief), plus scripts the host added: `session_env.sh` (the brief's variable block, `DEST`, `SKEYS`, `UKEYS`, `WANT_MD5`), `run_one.sh` (one session run under the rig lock, refuses unless the phone is on its launcher, writes `script exit=N`), `chain345.sh` (R3 to R5 in order; used for R3, then relaunched for R4 and R5), `u_runs.sh` (U1 a to e, U2a, U2b, restore, in the brief's order and with its commands). Generated files `new.xml` and `hu_put.sh` are `hu_put`'s scratch. Existing scripts used: `rig_preflight.sh`, `rig_thermal.sh`, `build_hur.sh`, `run_unit_tests.sh`. No executor agent was used; the host ran every step itself.
- **Two launch failures, no data lost.** (1) My first R2 launch did nothing: R1's thermal watcher (`th_watch`, started by `th_gate` inside the one-shot wrapper) was still running and held the rig-lock descriptor, so `flock -n` refused. I killed it by pid, patched `run_one.sh` to kill its own watcher on exit, and launched R2 again; the first attempt created no capture and no log. (2) In the first R3-to-R5 chain, R4 and R5 both reported `LOCK_FAIL` because R3's watcher left a `sleep 20` child holding the lock for a few seconds. R3 had run fine; R4 and R5 were relaunched with `flock -w 90` and ran once each. Neither failed attempt produced a capture. R1's thermal file carries extra lines after its run ended (the stale watcher ran 12 minutes); `th_report` for R1 was taken at the run's end and the figures below come from that.
- **Windows.** Every session capture starts after `logcat -G 16M` and `logcat -c` and is one run long, so counts are over the whole capture file. D-HU honours `logcat -c` (no pre-run lines seen).
- **`cap_stop` printed 1** where the brief says it must print 0, in R1 only. `ps` showed no `adb logcat` process afterwards; the count matched the host's own shell command lines. At the close `ps -eo cmd | grep logcat` was empty.
- **D-POCO** showed `NotificationShade` as `mCurrentFocus` before R1; `run_one.sh` sends HOME first and refuses unless the focus reads the launcher, which it did in every run.
- **No tap, no scroll, no hand step.** Launch, `am start` into settings (`extra_search_query`, `extra_destination`), `input keyevent 4`, `uiautomator dump`, `screencap` only. Save, Discard and the in-projection Quick Settings toggle were not exercised, as the brief says. `ACTION_LOG_MARKER` worked ungated (`AutomationMarker: R2-start` at 23:53:11.967, `R2-end` at 23:53:38.176).
- **Every session run** met the common conditions: `ssl=1`, `rendered` 12 or 13, `resume=1`, `upd_fail=0`, `fatal=0`, `match=0`, `garbage=0`, `ph_crit=0`, `ph_lines` above 29000. All dumps were `DUMP_OK` on the first try. I re-counted `attach` and `SSL handshake complete` with my own `grep -ac` for R1 and R2 and `attach` for R4 and R5; the counts matched.

## R0, Gate

**PASS**

- Git checks as above, build md5 `af8865f8944eda051ab57fe62dd47947`, 2755 tests and 0 failures, `PerformanceOverlayPolicyTest` 25, all four DEX strings at 1 or more, `DEST=2131296839`, `commit` `10905b633f4c`, `shared_prefs` owner `u0_a176:u0_a176`.

## R1, Upgrade default: overlay on, no `overlay-fields` key

**PASS**

- Settings written: `show-fps-counter=true`, `overlay-fields` and `overlay-position` deleted, plus the session keys. Read back: `hud_mirroring=false`, `show-fps-counter=true`, `wifi-connection-mode=3`.
- Discard-rule check: clean, run once.
- Decisive line: `10-06 23:39:53.174 ... PerformanceOverlay: fields=FPS,CPU,TEMP,FRAME sources=cpu,temp` (attach 1).
- Overlay, both dumps `ok=1`, one view, bounds `[20,20,221,118]`, left. d1: `FPS: 29`, `CPU: app 7% / sys 45%`, `Temp: 68C`, `Frame: 69ms`. d2: `FPS: 30`, `CPU: app 7% / sys 47%`, `Temp: 67C`, `Frame: 33ms`. `fps_numeric=1`.
- `avc_cpu=0`, `avc_thermal=0`. Counts: `ssl=1 resume=1 rendered=13`. `th_report`: `thermal_max=65C throttle_delta=0`.

## R2, FPS only (the point of the round)

**PASS**

- Settings written: `show-fps-counter=true`, `overlay-fields=1`, `overlay-position=0`.
- Discard-rule check: clean, run once (after the launch failure described in Setup notes, which created no capture).
- Decisive line: `10-06 23:52:34.753 ... PerformanceOverlay: fields=FPS sources=none` (attach 1).
- Overlay text from both dumps: exactly one view, one line, `FPS: 30`, no newline, side left, `ok=1`.
- `avc_cpu=0` (R1 also 0, so this proves nothing either way, as the brief warns). `ssl=1 resume=1 rendered=12`. `thermal_max=62C throttle_delta=0`.

## R3, No line chosen, toggle on

**PASS**

- Settings written: `show-fps-counter=true`, `overlay-fields=0`, `overlay-position=0`.
- Discard-rule check: clean, run once.
- Decisive line: `10-06 23:55:47.664 ... PerformanceOverlay: fields=none sources=none` (attach 1).
- `overlay_nodes=0 ok=1` in d1 and d2. Screenshot `R3-d1.png`: neither top corner shows a dark box; the top left carries Android Auto's own settings, volume and compass buttons and the top right is map.
- `ssl=1 resume=1 rendered=12`. `thermal_max=65C throttle_delta=0`.

## R4, CPU and frame age, on the right

**PASS**

- Settings written: `show-fps-counter=true`, `overlay-fields=10`, `overlay-position=1`.
- Discard-rule check: clean, run once (relaunched after `LOCK_FAIL`, see Setup notes).
- Decisive line: `10-06 23:59:13.287 ... PerformanceOverlay: fields=CPU,FRAME sources=cpu` (attach 1).
- Overlay, both dumps `ok=1`, side right. d1: `CPU: app 7% / sys 44%`, `Frame: 43ms`. d2: `CPU: app 8% / sys 52%`, `Frame: 13ms`. CPU precedes Frame, which is both the policy's order and ascending bit order (2 then 8), so this run cannot show that the order is independent of the int's bit order, as the brief hoped; the position moved right and CPU was sampled without TEMP (`sources=cpu`).
- `ssl=1 resume=1 rendered=13`. `thermal_max=60C throttle_delta=0`.

## R5, Toggle off

**PASS**

- Settings written: `show-fps-counter=false`, `overlay-fields=15`, `overlay-position=0`.
- Discard-rule check: clean, run once (relaunched after `LOCK_FAIL`).
- `attach` 0 (`grep -ac 'PerformanceOverlay: fields='` is 0). `overlay_nodes=0 ok=1` in d1 and d2. `rendered=12 ssl=1`. `thermal_max=66C throttle_delta=0`.

## U1, The Debug row, five stored states

**PASS**

| State | Keys | Row value | `entry_rows` / `old_toggle` / `old_position` / `debug_header` | `uend` |
|---|---|---|---|---|
| a | on, 1, left | `FPS (left)` | 1 / 0 / 0 / 1, `ok=1` | `fatal=0 keys_changed=0` |
| b | off, 15, left | `Off` | 1 / 0 / 0 / 1, `ok=1` | `fatal=0 keys_changed=0` |
| c | on, 10, right | `CPU, Frame age (right)` | 1 / 0 / 0 / 1, `ok=1` | `fatal=0 keys_changed=0` |
| d | on, 0, left | `No lines (left)` | 1 / 0 / 0 / 1, `ok=1` | `fatal=0 keys_changed=0` |
| e | on, key absent | `FPS, CPU, Temperature, Frame age (left)` | 1 / 0 / 0 / 1, `ok=1` | `fatal=0 keys_changed=0` |

- `app-language=en` took: all labels read in English. Phone radios were taken down with `phone_air_on` before U1a and restored with `phone_air_off` after U2b (WiFi and Bluetooth confirmed on by `dumpsys`).

## U2, The new screen shows the stored state; Back with no change writes nothing

**PASS**

- U2a (off, 15, left): `Show_Performance_Overlay=false/true FPS=true/false CPU=true/false Temperature=true/false Frame_age=true/false position="Left" save=True/false ok=1`, `uend`: `fatal=0 keys_changed=0`. Screenshot `U2a.png`: the four line rows and the position row render greyed, the Save button is present.
- U2b (on, 1, right): `Show_Performance_Overlay=true/true FPS=true/true CPU=false/true Temperature=false/true Frame_age=false/true position="Right" save=True/false ok=1`.
- Back dump after `input keyevent 4`: `back: toggle=0 unsaved=0` (it shows the main Settings list, no dialog). `uend U2b`: `fatal=0 keys_changed=0`.

## Close

- `hu_put "$BASEXML"` with no spec, read back as `settings-restored-HU.xml`: `diff` against `settings-backup-HU.xml` is empty (`restore_diff=0`). No logcat process left. Phone radios on.

## Anything the brief did not ask about

- **`su` is gone from this brief's commands on D-HU.** Any brief that writes `su -c` for D-HU needs the plain form (adbd is root); worth a line in `rig-quirks/units/D-HU.md`.
- **A one-shot wrapper around `ptr_lib.sh` leaks the thermal watcher.** `th_gate` backgrounds `th_watch` and only kills the previous one on the next gate in the same shell, so a script that exits after one run leaves a subshell that keeps the rig lock and writes to the run's `.thermal` file forever. Any per-run wrapper must kill `$THPID` on exit and wait for the lock (`flock -w`), as `run_one.sh` now does.
- **The projection screenshot carries a translucent grey circle at the top centre** (also on the settings screen in `U2a.png`). It is on every capture from D-HU and looks like a touch or pointer indicator of the unit, not of the app; I did not investigate it.
- **The overlay's CPU `sys` figure was 44% to 52% and the temperature 67C to 68C on D-HU in R1 and R4**, with FPS 29 to 30 and frame age 13 ms to 69 ms. Reported, never graded.
- **The Native AA bring-up was uneventful on all five runs:** one `SSL handshake complete` per run, no self-inflicted wake-up, no garbage header, with D-POCO's Bluetooth and WiFi toggled by `phone_air_on` and `phone_air_off`.
