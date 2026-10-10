# second-screen-outputs, round 1 results

**Candidate:** feat/second-screen-outputs @ 331b0a8ce852a00b878e7978739dff0036e0dec1 (tree ca2dba4c)   **Baseline:** none (R1 is the feature-off control on the candidate)
**APK md5:** 14fb275ad843370b2d208a3a3d8e8c5f (host build and pulled live APK agree)
**Unit:** D-HU (UNISOC MT50, Android 14) with D-POCO (Gearhead 17.9.664004-release); D-SAM was meant as a viewer
**Date:** 2026-10-09
**Evidence:** release `rig-evidence-second-screen-outputs`, asset `second-screen-outputs-round1-captures.zip`, sha256 `f56ebcd5435ba5a60b94d3702f9ea09b0a88ec0e49424cb669d61c2b19ca4e53`

## Setup notes

- **Round stopped after R2 on the brief's own outcome table.** R2 graded FAIL-B, so R2C was not run (the brief runs it only on FAIL-A or FAIL-C). The brief skips R3 and R4 when channel 14 opened in neither R2 nor R2C; R2C did not run, so the brief did not decide R3/R4 for this case. The operator chose to stop and report. R3 and R4 were not run and are recorded INCONCLUSIVE (the phone never opened `VIDEO_AUX` in R2).
- **Quirk files read:** `rig-quirks/topics/tooling.md`, `units/D-HU.md`, `units/D-POCO.md`, `units/D-SAM.md`.
- **Pre-flight (rig_preflight.sh):** D_HU wifi=1 bt=1 awake; D_POCO wifi=1 bt=1 awake, HFP to the HU, A2DP up; D_SAM wifi=1 bt=1; D_MOTO wifi=0 bt=0 (switched off in P1); D_HP wifi=1 bt=1 (OHU force-stopped). First run failed on an idle Gradle daemon stray; `rig_cleanup.sh kill` cleared it, second run PREFLIGHT OK. All five units were on adb.
- **Scripts:** the brief's eight files were saved unchanged except as listed, under `rig-data/rounds/second-screen-outputs-round1/scripts/` (`hur-wifi-test-scripts` no longer exists; the toolkit is `rig-toolkit`). Added: `ssr_env.sh` (BASE and per-run key strings, `POCO_MAC`), `r1.sh`, `r2.sh` (the brief's run blocks), `ssr_build.sh` (build_hur.sh equivalent with `--max-workers=2`, thermal guarded, since build_hur.sh takes no arguments).
- **Fixes to the brief's scripts:**
  1. `th_pkg` matched both `pkg=` and `throttle_pkg=` in `rig_thermal.sh status` and returned two lines, so the host thermal gate was not effective for R1 and the first lines of `thermal.log` are garbled. Fixed with ` pkg=` anchoring before R2. Reported peaks: R1 70C, R2 64C, throttle delta 0 in both.
  2. `preflight` required `hotspot_lines=0`, but the app logs `HotspotManager: Setting hotspot enabled=false` on every start (it switches the hotspot off, it does not use it). The check now ignores lines containing `hotspot enabled=false`. R1 was graded with that line explained; its preflight file still says FAIL for that reason only, the other four checks passed.
  3. The brief's `critical_error` was a whole-file count. The exit teardown (`ACTION_EXIT`) makes Android Auto log 5 `Critical error` lines in R1 after the `R1-end` marker, so a whole-file count of 0 can never be met. Added `crit_win`, which counts lines before the run's `-end` marker. Both counts are reported below.
- **P2:** `shared_prefs` owner `u0_a176:u0_a176`. Audio settings read back: `use-aac-audio` false, `audio-latency-multiplier` 8, `audio-queue-capacity` 20, `enable-audio-sink` false. No `aux-*` or `preferred-display-*` keys in the backup; `overlay_display_devices` null. HU_MAC 11:46:03:10:33:59.
- **P3:** D-POCO's `dumpsys bluetooth_manager` address grep came back empty. `POCO_MAC=DC:B7:2E:5E:4E:59` came from `adb -s <phone> shell settings get secure bluetooth_address` and matches the D-POCO entry in D-HU's bonded list. D-POCO is bonded to D-HU (Navegadortz2) and D-SAM (Navegadortz3) and to three non-rig devices; both sessions still formed.
- **P4:** VLC 3.0.24 at `/usr/bin/vlc`, `DISPLAY=:0`. ffprobe and ffmpeg not present. Python 3.14.6.
- **P7:** D-SAM API 19, battery 100, `wlan0` UP with address 0.0.0.0, `dumpsys wifi` shows supplicant SCANNING / DISCONNECTED with no network, VLC for Android not installed (`pm path` empty). No route test or VLC install was attempted: with no WiFi address the brief makes the viewer UNTESTABLE. P6 was skipped. D-SAM's clock read 22:23:44 against the host's 22:23:51.
- **Worktree:** `worktrees/second-screen-outputs__candidate__331b0a8c__20261009`. The app reports `commit: 331b0a8ce852-dirty` only because `wt-new.sh` leaves an untracked `.WORKTREE-INFO`; `git status` shows nothing else. Build: `BUILD SUCCESSFUL in 4m 51s`. `local.properties` was copied from the main checkout.
- **Cleanup:** `settings.xml` restored byte-identical to the backup (diff clean), `overlay_display_devices` null, no logcat left running, D-POCO WiFi and Bluetooth on.
- **Discard rules:** R1 clean (one `createGroup SUCCESS`, `p2p-wlan0-0`, 0 `MATCH! Starting AapService`, one SSL). R2 is not clean by the discard rules (18 SSL handshakes) and that is the finding, not contamination.
- **No tap, no hand step** was used.

## R0. Build gate

**PASS**

- Build succeeded, host and live md5 `14fb275ad843370b2d208a3a3d8e8c5f`.
- `ACTION_QUERY_STATE` reply: `"commit":"331b0a8ce852-dirty","versionName":"3.5.0-beta4","versionCode":117,"flavor":"github"`.

## R1. Feature off, a normal session

**PASS**

- Settings written: BASE plus R1KEYS (all `aux-*` and `preferred-display-*` deleted); `KEYS_OK`.
- Counts on `R1-hu.txt`: a1 0, a2 0, a3 0, a4 0, a5 0, a6 0, a7 1.
- `R1.tput`: `TPUT main windows=12 windows_rendered_ge1=12 median_fps=29 dropped_sum=0`; aux windows 0.
- Phone `critical_error`: whole file 5, all after `R1-end` (22:31:32.004), first at 22:31:32.797 `GH.WirelessStartup: Critical error encountered`, then four `CAR.SERVICE: Critical error` (`reason:1`, `io error` x2, `Failed to read message`) at 22:31:33.4. Before the end marker: 0.
- Preflight: mode_native 1, view_texture 1, main_sink_h264 1, phone_gh_lines 695, hotspot_lines 1 (`Setting hotspot enabled=false`, see Setup notes).

The PASS depends on reading `critical_error` as before the exit marker. Read as a whole-file count (5) it would be FAIL, but every one of those lines is the teardown our own `ACTION_EXIT` causes.

## R2. Feature on, network output

**FAIL** (FAIL-B: the feature breaks the main session)

- Settings written: BASE plus R2KEYS (`aux-display-enabled` true, `aux-output` NETWORK, `aux-network-size` 1); `KEYS_OK` on both attempts.
- Attempts: bring-up attempt 1 printed `RAISE_VERB_USED R2` and `SESSION_FAIL_NO_FRAMES R2`; attempt 2 failed the same way. The brief's preflight then failed on `main_sink_h264=0`. I did not re-run, because the cause is the same in all 18 cycles.
- b1: `22:34:44.298 [ServiceDiscovery] Announcing an auxiliary display on VIDEO_AUX: NETWORK 1280x720 as _1280x720, margins 0x0, density 213, role=AUXILIARY, content 65538` (18 times in the file, about every 9 s, last at 22:37:21.099).
- b2 0, b3 0, b5 0, b6 0, b7 0, b8 0, b9 0/0/0/0, b11 0. No `Media Sink Setup Request` of any kind in the capture (the main video sink never got set up either).
- b10: 18 `SSL handshake complete` against 1 in R1, so the session re-formed 17 times.
- Phone `critical_error`: 18, every one the same line: `22:34:45.500 CAR.SERVICE: Critical error 2 detail: 26 msg: No input for display 1` (then 22:34:54.856, 22:35:03.361, 22:35:12.764, ...). The phone's reader reports `GH.CarConnSession: Reader thread stuck` and `FRAMER_READ_IO_EXCEPTION` before them.
- Head unit side of the first cycle: service discovery answered at 22:34:44.254 to .326, `Watchdog: No video received yet` 22:34:45.117, `AapRead: Connection closed (EOF). Disconnecting.` 22:34:45.198, `Decoder stopped: CommManager: doDisconnect` 22:34:45.208.
- Host: `R2-recv.txt` `AUX_RECV result=no_data attempts=90 waited_s=90.4`; `R2-http.txt` `no_data attempts=30`; `R2.nal` bytes=0, idr 0, slices 0. `R2.tput` could not be computed (no `R2-up` marker, the run never reached a picture). Main median_fps and dropped_sum unmeasurable.

The announced second display has no input source, and Android Auto ends the session over it every time: "No input for display 1". Nothing past service discovery was reached, so none of b3 to b8 could be tested. Android Auto 17.9 on this phone does this; an earlier or later version might differ.

### VIEWER-PC

**INCONCLUSIVE** (no stream existed: b7 is 0 and port 5000 never opened). VLC 3.0.24 ran for 45 s against `http://127.0.0.1:5000/aux.h264`, `R2.vlc-pc` count 0, no PNGs. The brief's FAIL wording ("VLC ran and count is 0") would apply, but there was no stream for VLC to fail on.

### VIEWER-SAM

**UNTESTABLE** (D-SAM has no WiFi address and no VLC installed). `R2-sam.ohu-procs` not produced.

## R2C. CLUSTER role

**UNTESTABLE** (not run: the brief runs it only on R2 FAIL-A or FAIL-C, and R2 was FAIL-B).

## R3. Android display output, session end and the next session

**INCONCLUSIVE** (not run; the phone never opened `VIDEO_AUX` in R2, b3 is 0). R2C was not run, so the brief's "neither role opened it" test was not fully applied. The overlay display was never created and `overlay_display_devices` stayed null.

## R4. Sleep and wake on the Android display

**INCONCLUSIVE** (not run, same reason as R3).

## Anything the brief did not ask about

- The phone rejects the session when a second video display is announced without an input source. A fix on the candidate probably means announcing an input channel for the aux display, or not announcing the aux sink until that exists. The JVM tests cannot see this: it is Gearhead's rule.
- The brief's R3 and R4 skip rule needs R2C, which R2 FAIL-B excludes. A later brief should say what R3 and R4 do in that case.
- With the feature off, a normal session still forms in under a minute with no `VIDEO_AUX` and no `SecondScreen:` lines, and 12 of 12 main windows render at about 29 fps on the idle map.
- Both bring-up attempts for R2 each took about 2 minutes (the 40 s and 30 s frame waits, plus the `ACTION_RAISE_PROJECTION` fallback) before failing.
- `phone_grades` prints up to 20 very long lines from the phone capture; `cut -c1-260` was added.
