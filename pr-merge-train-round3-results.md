# pr-merge-train, round 3 results: settings screen, logs and projection touch (stage C)

**Candidate:** `fork/test/pr-merge-train` @ `927edb5f191f37e3b976ba23ace1e1750ac2cac8` (tree `e395dca82ba0dcb7abeddd18029c187903f302ec`)
**Baseline:** none (one build)
**APK md5:** T `892812235b9ea3154cd7a86357cdefbe` (dex symbol count 42), installed on D-HU and D-SAM and confirmed by `apk_identity.sh`
**Unit:** D-HU (UNISOC MT50, Android 14, 1440x720 landscape display) head unit; D-POCO (POCO X3 NFC) phone; D-SAM (SM-T230, Android 4.4.2) settings screen only.
**Date:** 2026-10-26

## Setup notes

- Evidence: release `rig-evidence-pr-merge-train` on the fork, asset `pr-merge-train-round3-captures.zip`, sha256 `fb6ae98097f20ca7b442a763841ccf980ac4a9c1d4411dae39fbfdf7cbe85d60`.
- Rounds 1 to 3 ran back to back in one session; settings, radios and worktree were restored once, after round 3: D-HU `settings.xml` restored from round 1's backup (byte-identical, owner `u0_a176:u0_a176`), build worktree removed, no logcat left. Closing pre-flight: D_HU wifi=1 bt=1 Awake HFP:none A2DP:none; D_POCO wifi=1 bt=1 Awake HFP:none A2DP:none (opening read D_POCO HFP:..33:59 A2DP:up, the links had dropped by the end, not restored); `rig_cleanup.sh`: no strays, lock free. D-SAM Bluetooth was never changed (still on).
- `ACTION_QUERY_STATE` commit check not run; identity by md5 and dex symbols. Scripts: `lib.sh` (round 1's plus the brief's lib3 `shot` and `focus`), `s1.sh`, `p1.sh` (void attempt), `p1r.sh` (graded), `keys_R01.sh`, `keys_P1.sh` in `rig-data/rounds/pr-merge-train-round3/scripts/`.
- Quirk files read for this round: units/D-SAM.md, units/D-SAM-and-D-HP.md. Pre-flight (opening, D_HU D_POCO D_SAM): FAILED once on an idle gradle daemon from my builds; `rig_cleanup.sh kill`, re-run OK. D-SAM screen Asleep: KEYCODE_WAKEUP/224 did nothing, KEYCODE_POWER (26) woke it (one press, only when Asleep).
- D-SAM clock was 24 s behind the host (13:44:34 vs 13:44:58 at first read, 13:45:26 vs 13:45:50 later); no correction used (S1 does not compare timestamps).
- Brief errata: lib3 `focus` greps `mResumedActivity`, absent on API 34 (D-HU prints `topResumedActivity`); changed the grep to accept both. S1 on D-SAM: the 6 s screenshot was blank (cold start; first draw ~4.6 s after process, BufferQueue lines), so the screenshot is from a relaunch at 10 s and 25 s; the screen is rotated 90 degrees in the portrait buffer (quirk). First S1 attempt void (D-SAM asleep, black screenshot), kept in s1-attempt1/.
- p1.sh errata: tap used `wm size` (720x1440 physical) on a 1440x720 landscape display, so (360,720) was the bottom edge and no touch was sent (L-IN 0 in P1). Export check used `su -c ls` and `/sdcard/.../Download/` (no su; exports live in `/storage/emulated/0/Android/data/<pkg>/files/`). Fixed in p1r.sh (tap from screenshot size 1440x720 -> 720,360; plain adb; `find -mmin -5`) and re-ran once as P1r. First P1 kept in p1-attempt1/.
- D-HU settings: log-level 2 for S1, 0 for P1/P1r (written by set_hu_settings_host.py, read back). D-SAM settings were not written (onboarding-version already 2, log-level 2); file identical to backup after the round.
- The HU's own FPS/CPU/Temp stats box is drawn over the video in all P1 screenshots, also before any tap or night mode; it is the app's pre-existing stats overlay (setting present in baseline), not a new overlay.
- D-HU baseline settings.xml backup for restore is round 1's `settings-backup-hu.xml` (identical to the pre-round-1 file); round 3's own backup differs from it only because round 1/2 wrote keys.
- Host thermal for P1r: max 69C, throttle counter 0.

## S1

**PASS**

- D-HU (log-level 2, keys as round 1), launch 13:46:19 to 13:46:27, 0 FAIL lines in its capture:
  - `topResumedActivity=ActivityRecord{f39b9b0 u0 com.andrerinas.headunitrevived/com.andrerinas.openheadunit.main.SettingsActivity t2082 d0}`
  - `S1-hu.png`: Material 3 settings screen, header row (back arrow, Settings, Basic/Advanced, search, reset, Save), category rail (General, Wireless Connection, Automation, More Features, Navigation, Graphics) and a list (Auto-Optimize Settings, Permissions, Connection mode "WiFi, Self Mode").
- D-SAM (Android 4.4.2, settings unchanged: `onboarding-version` 2, `log-level` 2), launch 13:47:10 to 13:47:17 and a relaunch for the late screenshots, 0 FAIL lines (FATAL EXCEPTION 0, ANR 0):
  - `mResumedActivity: ActivityRecord{4282c798 u0 com.andrerinas.headunitrevived/com.andrerinas.openheadunit.main.SettingsActivity t14}`
  - `S1-sam-t10.png` (10 s) and `S1-sam-t25.png` (25 s): the same screen as on D-HU, header and list drawn, rotated 90 degrees inside the portrait 800x1280 buffer as the unit does for landscape content. `S1-sam.png`, taken 6 s after launch as the brief says, is the wallpaper only: the first frame is drawn about 4.6 s after process start (BufferQueue lines at 13:47:25.313).
- The only error lines on D-SAM are `Could not find class 'android.media.MediaCodec$CodecException'` (VFY, known) and BufferQueue `dequeueBuffer: min undequeued buffer count (1) exceeded`, five lines at the first draw.

## P1

**PASS**

- Graded run is P1r; the first attempt (`P1`, window 13:49:04 to 13:50:01) is void: its tap went to y=720 of a 720-high display, so no touch message was sent (L-IN 0 in its window) and nothing else was gradable. `log-level` 0 read back in both attempts.
- P1r window 13:51:56 to 13:52:47, one session (one `AapSslContext.performHandshake` line; `SSL handshake complete` also prints from `AapTransport.handshake`), last L-TP `13:52:42.676 ... rendered=150 (29fps)`.
- L-IN between `P1r-tap` and `P1r-tapped`: 2, `13:52:20.461 AapTransport.enqueueMessage | INPUT Input Event type: 32769 flags: 11 size: 30` and `13:52:20.466` the same.
- L-NIGHT: 2 (`13:52:25.446` night, `13:52:32.018` day).
- Export: newest file `OHU_Log_20261026_135237_156.txt` (3,509,949 bytes, 13:52) under `/storage/emulated/0/Android/data/com.andrerinas.headunitrevived/files/`; no `HUR_Log_` file newer than the round (the newest is `HUR_Log_20261017_232347_018.txt`).
- Screenshots `P1r-before.png`, `P1r-after.png`, `P1r-night.png`: the projection (the phone's launcher with the map and the player) with no clock or maneuver card drawn by the app. The yellow FPS/CPU/Temp box in the top right is the app's own `fps-counter` (true in the baseline settings) and is present before the tap. The night screenshot shows the phone's map in dark mode, as the verb asks.
- FATAL EXCEPTION 0, ANR 0, phone Critical error 0.

## Anything the brief did not ask about

- The brief's `p1.sh` fails three ways on this rig (tap coordinate from `wm size`, `su -c` and the `Download/` export folder); `lib3`'s `focus` greps a field Android 14 does not print. All fixed in the scripts above.
- D-SAM's first settings frame takes about 4.6 s after process start, so the brief's 6 s delay catches it blank about half the time; this unit needs a longer wait or a retake.
- Spotify on D-POCO needed a play key and 8 s before its session read PLAYING after every run.
