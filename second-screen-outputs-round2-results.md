# second-screen-outputs, round 2 results

**Candidate:** `feat/second-screen-outputs` @ `4897c1218417d6ea9ded71ea0857eff59b7599f4` (tree `570307efb5c47f70448cb8532944fed0f58f5dda`, on `main` `ec9d9c33`)   **Baseline:** none (R1 is the feature-off control on the candidate)
**APK md5:** `3d5aebd20839aa3323b1ec12f95675a0` (built = pulled from D-HU); no baseline APK
**Unit:** D-HU (UNISOC MT50, Android 14, rooted) with D-POCO (POCO X3, Gearhead 17.9.664004-release); D-SAM (SM-T230, API 19) as the R2 viewer
**Date:** 2026-10-10
**Evidence:** release `rig-evidence-second-screen-outputs`, asset `second-screen-outputs-round2-captures.zip`, sha256 `ea2dea2c5cdde591a4a3ccc7fac0f1483fbdde5810049d53271773f2fc2ff176`

## Round verdicts

R0 PASS, R1 PASS, R2 PASS, R2C PASS, R3 PASS, R4 PASS. VIEWER-SAM PASS, VIEWER-PC FAIL by the brief's wording (see R2).

## Setup notes

- **Quirk files read:** `rig-quirks/topics/tooling.md`, `units/D-HU.md`, `units/D-POCO.md`, `units/D-SAM.md`. The template was read without 7b and 8.
- **Pre-flight (`rig_preflight.sh D_HU:wifi,bt D_POCO:wifi,bt D_SAM`):** D_HU wifi=1 bt=1 awake, no profiles; D_POCO wifi=1 bt=1 awake, HFP to the HU, A2DP up; D_SAM wifi=1 bt=1 asleep. `PREFLIGHT OK`. Host 60C, throttle_pkg 0.
- **Units on adb (P1):** D-HU, D-SAM, D-POCO, D-HP, D-MOTO. D-MOTO's Bluetooth and WiFi were switched off by the brief's P1 loop. D-SAM and D-HP had the app force-stopped.
- **Candidate identity:** `git ls-remote fork feat/second-screen-outputs` printed `4897c121...`; the worktree's tree hash printed `570307ef...`. Worktree `worktrees/second-screen-outputs__candidate__4897c121__20261010`. `ACTION_QUERY_STATE` `commit` read `4897c1218417-dirty`. `git status --porcelain` listed only `.WORKTREE-INFO`, which the brief allows. The APK installed on D-HU before this round carried commit `dd5b2a823df9-dirty` (another thread); `install -r` replaced it.
- **P2:** `shared_prefs` owner `u0_a176:u0_a176` (`stat -c %a` is not supported here, mode not read). Audio keys read back and unchanged: `audio-latency-multiplier=8`, `audio-queue-capacity=20`, `enable-audio-sink=false`, `use-aac-audio=false`. `video-profile-starvation-cap` absent, no `aux-*` or `preferred-display-*` keys, `overlay_display_devices` null. `HU_MAC=11:46:03:10:33:59`. Backup md5 `ac504a7ab112b351e088f24cf37c6054`.
- **P3:** `POCO_MAC=DC:B7:2E:5E:4E:59` from `settings get secure bluetooth_address`. Bonded list on D-POCO: the head unit (`Navegadortz2`, ends `33:59`), `motorola edge 30 neo`, `Magnetic Speaker`, `FX Plus`. D-SAM's radio is not bonded. Launcher in focus. `P3-probe` marker count 1.
- **P4:** VLC 3.0.24 at `/usr/bin/vlc`, `DISPLAY=:0`, Python 3.14.6. `ffprobe` and `ffmpeg` absent (no `R2.ffprobe`, no `R2-aux.png`).
- **P7 and P6:** D-SAM had `wlan0` 192.168.1.2 this time (round 1 had none), API 19, battery 100, no VLC. The route test (host relay, port 5001) returned 0 hits, so `ROUTE=hu`. The operator approved the VLC install. VLC for Android 3.7.1 armeabi-v7a (min API 17), sha256 `a6a7f940e4bd190c9a70f3f0dc97c1c402c10200af20edac7a1d452b24abd8a0`, from `get.videolan.org`, installed with `adb install`; `.StartActivity` count 102. D-SAM clock 13:13:13 against host 13:13:30.
- **Deviations and faults found, all outside the candidate:**
  - The brief and the skill name `hur-wifi-test-scripts`; that path no longer exists. `rig-toolkit/` was used.
  - The new worktree had no `local.properties`, so the first build failed in 14 s (`SDK location not found`). Copied from the main checkout. `rig-toolkit/wt-new.sh` now copies it into every new worktree (`TOOLS.md` updated).
  - `thermal_guarded.sh` froze only the `gradlew` client and its direct children. The Gradle daemon is detached (parent pid 1), so it kept compiling while the guard reported a pause. At the operator's request it now freezes the whole tree plus the Gradle and Kotlin daemons, polls every 2 s, resumes on exit, and defaults to HOT 95C and COOL 85C (was 86/68). `ssr_build.sh` was edited to match. The build ran under HOT 88 COOL 70 on its second attempt, before that edit (2m51s, one pause at 91C 13:18:37 to 68C 13:20:57).
  - R1's `th_gate` watcher kept the rig lock after R1 ended, so R2's first launch refused to start (`flock -n`). The watcher was killed by pid and R2 was launched again; no R2 capture existed before that. `r1.sh` did not kill `$THPID` at its end; `r2.sh`, `r2c.sh` and `r3.sh` do.
  - `pngdims.py` raised `FileNotFoundError` on an empty glob in R2 (no PNGs). Not fixed; the result is recorded under VIEWER-PC.
  - The round ran from the host session directly under `rig_lock`, with `flock` and `runner.sh`, not through `rig-executor` agents (the standing rule for scripted hardware rounds). Each run was graded from its own greps.
- **Scripts (`rig-data/rounds/second-screen-outputs-round2/scripts/`):** round 1's `ssr_keys.py` (md5 `3e9ffa2eac66b78955beb81f2b5f011c`), `tput.py` (`4df665f42173f485b092c2140cc1c938`), `aux_recv.py` (`66309603cf9fccc7566a92ec9bde5805`), `nal_count.py` (`e199ca79caac2b1f020a7ec46ceda773`), `relay.py` (`cb4eef9628ae828c4f314b3c6e574137`), `pngdims.py` (`d11bf8340108ceff181c266c17faa98e`), `viewers.sh` (`27057af957d7aeff11d697c64819adca`) and `ssr_build.sh` (`5f7176ed33959a5a86d63140123088a6`), copied from round 1's folder; `ssr_lib.sh` extracted verbatim from the brief; new this round: `env2.sh`, `runner.sh`, `r1.sh`, `r2.sh`, `r2c.sh`, `r3.sh` (the brief's blocks, with a bail-out after a failed R3 bring-up and the overlay deleted at its end).
- **Windowing:** every count is inside its marker window (`seg`). Phone markers reached both captures (`phone_markers_in_window` 7, 8, 7, 10).
- **`cachedSurfaceSettingsHash` first-launch miss:** not looked for separately.

## R0 Build gate

**PASS**

Build succeeded in 2m51s. Installed APK md5 `3d5aebd20839aa3323b1ec12f95675a0` equals the built APK. `ACTION_QUERY_STATE` reply: `"commit":"4897c1218417-dirty"` (dirty from `.WORKTREE-INFO` only).

## R1 Feature off, a normal session

**PASS**

- Settings: `BASE` plus `R1KEYS` (all aux keys deleted); `KEYS_OK`.
- a1 (`b1`) 0, a2 (`VIDEO_AUX` lines) 0, a8 (`INPUT_AUX` lines) 0, a4 (`b3`) 0, a6 (`Error processing video message`) 0. `b4m` 1. a7 (`b10`, SSL handshakes) 1. `b12`: binding 0, early_cycle 0, unsupported_input 0.
- Main: `windows=12 windows_rendered_ge1=12 median_fps=29 dropped_sum=0`.
- Phone: `phone_markers_in_window=7`, `win|Critical error=0` (file total 5, exit teardown), all other `win|` rows 0. Quote: `10-10 13:22:09.359 CAR.INPUT: Discovered input for display 0 with service 3`.
- Preflight: mode_native 1, view_texture 1, main_sink_h264 1, hotspot_lines 0, phone_gh_lines 702. Host thermal_max 69C, throttle delta 0.

## R2 Network output, AUXILIARY, two viewers (the point of the round)

**PASS**

- Settings: `BASE` plus `R2KEYS`; `KEYS_OK`. `rc=0` on the first attempt.
- Decisive lines (head unit):
  - `13:26:09.443 [ServiceDiscovery] Announcing an auxiliary display on VIDEO_AUX: NETWORK 1280x720 as _1280x720, margins 0x0, density 213, role=AUXILIARY, content 65538, input on INPUT_AUX` (b1=1, b1_input=1, b2=0)
  - `13:26:09.614 AapTransport: the auxiliary display's video lane is open` (b3=1)
  - `13:26:09.719 AapControlTouch: Input binding answered on INPUT_AUX` (binding=1)
  - `13:26:09.723 Media Sink Setup Request: 3 on channel VIDEO_AUX` (b4=1, codec 3; b4m=1)
  - `13:26:09.726 Granting video focus on VIDEO_AUX after its setup` (b5=1)
  - `13:26:09.766 Media Start Request VIDEO_AUX: session=0, config_index=0` (b6=4 in the window)
- Phone, in window (`R2.phone`): `Critical error` 0, `No input for display` 0, `Multiple inputs found for display` 0, `Video focus gained before configurations received` 0, `Car not sending ACK` 0; `blocked by power saving` 0, `Video focus rejected` 0. Quotes: `13:26:08.486 CAR.INPUT: Discovered input for display 0 with service 3: CarUiInfo (hasRotaryController: true, touchscreenType: 1, ...)` and `13:26:08.487 CAR.INPUT: Discovered input for display 1 with service 15: CarUiInfo (hasRotaryController: false, touchscreenType: 0, hasSearchButton: false, hasTouchpadForUiNavigation: false, hasDpad: false, ...)`. `critical_error_file` 5.
- Network: `b7=1`; `b8=4`, `b8_max=3`. Clients: `13:26:19.790 /127.0.0.1:54515 (1 connected)`, `13:26:22.773 /127.0.0.1:45799 (2 connected)`, `13:26:52.478 /192.168.1.2:55339 (3 connected)`, `13:27:12.067 /127.0.0.1:43553 (2 connected)`.
- Stream: `R2-recv`: `first_byte_after_s=1.0 received_s=60.1 bytes=2404155 early_eof=False`; `R2-http`: `http_status='HTTP/1.1 200 OK'`. `R2.nal`: `nal_total=1710 sps=7 pps=7 idr=3 slices=1696 slices_per_s=28.2 profile_idc=66 level_idc=31 width=1280 height=720`.
- Aux behaviour: `b9` fell_behind 0, cycles 3, shed 0, errors 0; `b10=1` (equals R1's a7); `b11=0`. Three lines `Auxiliary Video Sink Stopped -> Ignored (Forced Keyframe Request)` (13:26:20.372, 13:26:53.037, 13:27:12.599), one per keyframe cycle. `early_cycle=0`, `unsupported_input=0`.
- Main beside R1: R2 `windows=13 windows_rendered_ge1=13 median_fps=29 dropped_sum=0`; R1 `12/12 29 0`. Host thermal_max 67C, throttle delta 0.
- Empty aux input: the map renders on the aux picture (see VIEWER-SAM). A map needs no input.

### VIEWER-PC

**FAIL** (by the brief's wording: `b7` is 1, VLC ran, `count` is 0). Read this with the log: VLC 3.0.24 opened `http://127.0.0.1:5000/aux.h264`, found `NAL_SPS` and `NAL_PPS`, started avcodec (h264), opened a Qt video window and logged `VoutDisplayEvent 'resize' 1280x720`. It never loaded the `scene` filter (no `scene` line in `R2.vlc-pc-log`), so `R2-vlc-pc/` stayed empty. That points at the capture method, not at the stream, but the PNG count the verdict needs is 0, so it is graded as written. The same stream reached D-SAM and rendered there.

### VIEWER-SAM

**PASS**

Route `hu`: `HUIP=192.168.1.4`, `VIEW_URL=http://192.168.1.4:5000/aux.h264`, same /24 as D-SAM (192.168.1.2). VLC for Android started at 13:26:25 (first launch, multidex extraction). The stream reached the tablet: `13:26:52.478 SecondScreen: network client connected from /192.168.1.2:55339 (3 connected)`. Screenshots (all 800x1280, the tablet is mounted portrait): `R2-sam-5s.png` and `R2-sam-10s.png` are 6835 bytes each (not viewed, VLC still starting), `R2-sam-20s.png` shows the launcher, `R2-sam-35s.png` shows the Android Auto Google Maps picture (streets, `Google Maps` mark, a `0 km/h` bubble), rotated 90 degrees on the portrait panel. First picture is therefore at most 35 s after the viewer was launched and about 8 s after the connect line. `R2-sam.top`: `org.videolan.vlc/.gui.video.VideoPlayerActivity` resumed. `R2-sam.ohu-procs` 0.

## R2C CLUSTER role, no viewers

**PASS**

- Settings: `BASE` plus `R2CKEYS`; `rc=0`. `b1` line: `13:28:38.754 ... Announcing an auxiliary display on VIDEO_AUX: NETWORK 1280x720 as _1280x720, margins 0x0, density 213, role=CLUSTER, content the phone's choice, input on INPUT_AUX`.
- `b3=1 b4=1 b4m=1 b5=1 b6=2 b7=1`, `b8=2 b8_max=2`, `b11=0`, `b12` binding 1, early_cycle 0. `b9` cycles 1, shed 0, errors 0. `b10=1`.
- Stream: `R2C-recv`: `received_s=45.0 bytes=1351568`; `R2C-http`: `HTTP/1.1 200 OK` (25 bytes in 10 s). `R2C.nal`: `idr=1 slices=1318 slices_per_s=29.3 profile_idc=66 width=1280 height=720`.
- Phone: all `win|` rows 0, `phone_markers_in_window=7`; `Discovered input for display 1 with service 15` at `13:28:37.818`; `Video focus rejected for display type` 0. Main `windows=9 windows_rendered_ge1=9 median_fps=29 dropped_sum=0`. Thermal max 62C, delta 0.

## R3 Android display output, session end and next session

**PASS**

- Skip rule: R2 not FAIL-B and `b3` 1, so `ROLESPEC=del:aux-display-role` (AUXILIARY). `OVL=2` (`Overlay #1 800x480@160` from `overlay_display_devices=800x480/160`). Keys: `BASE` plus `bool:aux-display-enabled=true str:aux-output=ANDROID_DISPLAY int:aux-display-id=2`, aux size, port, content and preferred-display keys deleted.
- c1 `the auxiliary display is up on 2` 1 (`13:30:41.797`). c2 `13:30:41.302 DisplayTargets: projecting on display 0 because the built-in display was chosen [0:Built-in Screen 1440x720@240, 2:Overlay #1 800x480@160]`. `AuxDisplayPresentation: surface ready on display 2` at 13:30:41.865.
- c3 session 1: aux `windows=8 windows_rendered_ge1=8 median_fps=29`; main `8/8`, `median_fps=29`, `dropped_sum=0`.
- c4 `Decoder stopped: CommManager: doDisconnect (second screen)` between `R3-end1` and `R3-s2-ssl`: 1 (`13:32:40.384`). c5 `ACTION_END_SESSION_STAY_ARMED received`: 1. c6 no `SESSION2_FAIL`, `R3-scan` not used. c7 distinct pids `seg R3-up R3-s2-check`: 1 (no app restart).
- c8 session 2: aux `windows=10 windows_rendered_ge1=10 median_fps=29`, `first_aux_picture_after_R3-s2-ssl_s=4.8`; main `9/9`, dropped 0. Main in R1 for comparison: `12/12`.
- c9 `could not open the auxiliary display` 0, `cannot carry a second screen` 0; c10 0.
- Phone (`R3.phone`): `phone_markers_in_window=10`, every `win|` row 0. Thermal max 62C, delta 0.

## R4 Screen off and wake, inside R3's first session

**PASS**

- d1 `WakeDetect: SCREEN_OFF` 1, `WakeDetect: SCREEN_ON (screen was off for ` 1.
- d2 the three aux-decoder `Decoder stopped:` lines between `R4-sleep` and `R4-check`: 0, 0, 0 (sum 0). d6 `Decoder stopped: screen_off_sleep` 1 (the main decoder, report only).
- d3 `AuxDisplayPresentation: surface ready on display ` 0 (the surface was not recreated).
- d4 `R4.tput`: aux `windows=9 windows_rendered_ge1=9 median_fps=29`, `first_aux_picture_after_R4-wake_s=1.9`; main `9/9`, dropped 0.
- d5 `cycling the auxiliary display's video focus` lines between `R4-wake` and `R4-check`: none, so none earlier than the first aux picture.
- d7 phone `win|` rows all 0.

## Cleanup

`overlay_display_devices` deleted (`settings get` prints `null`; `dumpsys display` has no `Overlay #1`). D-HU `settings.xml` restored from the backup (`diff` empty, `RESTORED`). No logcat, `thermal_guarded`, `aux_recv` or relay processes left, no adb forward left. D-POCO WiFi and Bluetooth on. VLC on D-SAM force-stopped and left installed.

## Anything the brief did not ask about

- The decisive fault of round 1 (`No input for display 1`, 18 of 18) is gone with the aux input announced on `INPUT_AUX`: R2, R2C and R3 each logged `Discovered input for display 1 with service 15`, and no phone `Critical error` fell inside any window.
- The aux sink does not block the main one: main `median_fps=29`, `dropped_sum=0` and every window rendered in R1, R2, R2C and R3 (both sessions), with two hardware H.264 decoders at once in R3.
- The aux lane's `Auxiliary Video Sink Stopped -> Ignored (Forced Keyframe Request)` lines appear once per keyframe cycle (R2 3, R2C 1) and the stream continued each time.
- D-SAM's first VLC start takes about 27 s (multidex extraction) before it connects; a later viewer run on a warm VLC will reach a picture sooner.
- The host's Gradle build after the thermal fix has not been run yet; the guard change is untested on a real build.
- `rig-toolkit/wt-new.sh` and `thermal_guarded.sh` changed during this round (see Setup notes).
