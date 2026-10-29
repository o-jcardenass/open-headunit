# pr-merge-train, round 1 results: video and audio (stage A)

**Candidate:** `fork/test/pr-merge-train` @ `3ad7c78c8860d7da3f4a2429157931a36a03129f` (tree `596afde2259ce8e5e71247a581e5f7296117b599`)
**Baseline:** `main` @ `f9a72d480d51105870f62df6f0a40a1276e98a34` (tree `ff8fe6c2ea0a30f9e44276c6e9d3152591173969`)
**APK md5:** A `f365e15aa2dbaf31972c68d09859d278` / B `7dbddf19ee63fa0d0772dcf32878b337`
**Unit:** D-HU (UNISOC MT50, Android 14, arm64-v8a, rooted adbd) as head unit; D-POCO (POCO X3 NFC) as phone, Spotify playlist source.
**Date:** 2026-10-26

## Setup notes

- Evidence: release `rig-evidence-pr-merge-train` on the fork, asset `pr-merge-train-round1-captures.zip`, sha256 `e53547c29fba2dbff017861432dac4954a943a3d0f7576dad7d82fcdef08d0a2`.
- Rounds 1 to 3 ran back to back in one session; settings, radios and worktree were restored once, after round 3: D-HU `settings.xml` restored from round 1's backup (byte-identical, owner `u0_a176:u0_a176`), build worktree removed, no logcat left. Closing pre-flight: D_HU wifi=1 bt=1 Awake HFP:none A2DP:none; D_POCO wifi=1 bt=1 Awake HFP:none A2DP:none (opening read D_POCO HFP:..33:59 A2DP:up, the links had dropped by the end, not restored); `rig_cleanup.sh`: no strays, lock free. D-SAM Bluetooth was never changed (still on).
- Resolved commit 261a7bfb as pr-merge-train, adds rounds 1-3; took round 1 (queue order). Rounds 2/3 not built.
- Quirk files read: topics/tooling.md, topics/audio.md, units/D-HU.md, units/D-POCO.md. TOOLS.md "Pick a tool" table read.
- rig_preflight.sh role names are D_HU/D_POCO (D-HU fails "invalid variable name"). Opening pre-flight: D_HU wifi=1 bt=1 Awake HFP:none A2DP:none; D_POCO wifi=1 bt=1 Awake HFP:..33:59 A2DP:up. rig_cleanup.sh: no strays, lock free.
- Brief get_apk.sh uses `ls -d worktrees/*` (empty); used wt-new.sh instead: WT in $EV/wt.txt. No usb-native-loop-round2 B APK on hand, so B is built here.
- fork/test/pr-merge-train tip is now 927edb5f (round 3 T); A 3ad7c78c checked out detached.
- capture.sh disables D-SAM Bluetooth (brief: stays off). Prior state of D-SAM BT not cleanly readable (dsam-bt-before.txt is btif log lines only).
- Built B f9a72d48 (tree ff8fe6c2) and A 3ad7c78c (tree 596afde2) in worktree /home/oscar/Coding/StudioProjects/worktrees/pr-merge-train__scratch__3ad7c78c__20261026; md5s in prepare.md5. Both builds exit 0.
- Brief capture.sh lines: su -c stat/cat and getprop removed (D-HU adbd is root, 'su' absent; did them by plain adb: shell_prefs owner u0_a176:u0_a176, abi arm64-v8a). D-SAM (KitKat) has no 'svc bluetooth'; capture.sh line failed harmlessly. Added scripts/run_session.sh (pre-checks + flock via caller). Phone: Settings screen was open (HOME pressed), Spotify playing queue started via monkey+keyevent 126 (media session PLAYING, playlist item 32). Spotify then sent HOME. connection-modes baseline = {wifi,self}; audio-latency-multiplier=8; use-aaudio-output/audio-matching-mode absent; video-profile-starvation-cap=false.
- D-SAM Bluetooth could not be switched off (KitKat, no svc bluetooth): bluetooth_on=1, but its headunit app is not running (ps count 0), so nothing listens.
- Identity: B/A checked by apk_identity.sh md5 + dex symbol count (A 9, B 0); ACTION_QUERY_STATE commit check not run (app not running between installs). D-HU focus showed Settings activity (stale) before R1; harmless, session script force-stops/launches MainActivity.
- R1 first launch refused by pre-check (Spotify paused after R0 session end); resumed with keyevent 126 and relaunched, no run data lost.
- Preflight 'disabled due to previous underrun' was 0 in R0 and R1 and no MediaAnalyticsItem underrunframes line printed (grep -c underrunframes hu.txt = 0), so underrun is graded on L-UND=0 and mixer counters only. AudioMixer lines carry compressedFrames=3792 (R0) / 3600 (R1); no AAC cap line, use-aac-audio=false.
- Scripts: lib.sh and capture.sh extracted verbatim from the brief (capture.sh minus the `su -c` and getprop lines), plus one-round `run_session.sh`, `keys_R01.sh`, `keys_R2.sh` in `rig-data/rounds/pr-merge-train-round1/scripts/`. Toolkit: `wt-new.sh`, `rig_thermal.sh`, `build_hur_cool.sh`, `apk_identity.sh`, `set_hu_settings_host.py`, `rig_preflight.sh`, `rig_cleanup.sh`.
- Settings keys written per brief section 4 and read back; settings.xml backup is `settings-backup-hu.xml` (restored at close). shared_prefs owner `u0_a176:u0_a176` (correct, app-writable).
- Music was running on D-POCO before each session; no music fallback tap was needed in any run (no `music tap` or `NO_MUSIC` in marks.txt).
- Host thermal: max package 70C (R0), 67C (R1), 67C (R2); throttle counter 0 in all three.

## R0

**PASS**

- Settings: section 4 R0/R1 column. Radios: phone wifi and bluetooth off then on by `ph_off`/`ph_on`.
- Discard rules: clean (MATCH! Starting AapService 0, createGroup SUCCESS 1, p2p-wlan0-0 only, Magic Garbage 0, one SSL handshake).
- Window 13:03:06 to 13:06:21. L-SSL 1, L-CODEC `Media Sink Setup Request: 3 on channel VIDEO` (13:03:13.298), last L-TP `rendered=151 (30fps)` (13:06:19.317), L-MUS 3, L-UND 0, L-CAP 0, FATAL 0, phone Critical error 0.
- L-MIX: 18 lines, largest `concealedFrames=0`, largest `staleFrames=0`.

## R1

**PASS**

- Same settings and radio steps as R0, APK A.
- Discard rules: clean (same counts as R0). Session formed 78 s after launch (13:08:23 start, `SSL handshake complete` 13:09:41.167), the first launch after the install; still inside the 120 s stop rule. The first launch attempt was refused by the script pre-check (Spotify paused), no data lost.
- Window 13:08:23 to 13:12:50. L-SSL 1, L-CODEC 3 (13:09:41.996), last L-TP `rendered=150 (30fps)` (13:12:48.072), L-MUS 1, L-UND 0, L-CAP 0, FATAL 0, phone Critical error 0.
- L-MIX: 18 lines, largest `concealedFrames=0`, largest `staleFrames=0`.
- Against R0: L-UND 0 vs 0 (limit R0+1), largest staleFrames 0 vs 0 (limit 50). No regression reproduced. Caveat: the rig has no speaker and the user's stutter is on their own unit, so this says only that the counters did not move.

## R2

**PASS**

- Section 4 R2 column (`force-software-decoding` true, `software-video-decoder` 1, `video-codec` H.264), APK A. D-HU abi `arm64-v8a`.
- Discard rules: clean.
- Window 13:13:41 to 13:16:54:
  - `13:13:45.532 SSL handshake complete`
  - `13:13:47.029 Configuring bundled FFmpeg H.264/AVC decoder for 1920x1080`
  - `13:13:47.033 Bundled FFmpeg H.264/AVC decoder initialized`
  - `13:13:47.063 First bundled software H.264/AVC frame rendered`
- L-SSL to L-FF3: 1.53 s. L-CODEC reads 3. Last L-TP `rendered=150 (29fps)`, `codec=ffmpeg`. FAIL lines 0, phone Critical error 0.

## Round verdict: PASS

R0 PASS, R1 PASS, R2 PASS. No FATAL EXCEPTION in any run.

## Anything the brief did not ask about

- The brief's capture.sh uses `su -c`, which does not exist on D-HU (adbd is already root); the brief's `get_apk.sh` assumes an existing worktree. Both are brief errata.
- D-SAM's Bluetooth could not be turned off from adb (KitKat), so the brief's "D-SAM Bluetooth stays off" was not met; its app was not running.
- AudioMixer lines report `compressedFrames=` thousands in both arms; no AAC cap line printed and `use-aac-audio` was false, so it was read as a counter name, not as AAC music. Not verified further.
- R0 saw three `Media Start Request AUDIO:` (sessions 0 to 2) and R1/R2 one; mixer line count was 18 in every window.
