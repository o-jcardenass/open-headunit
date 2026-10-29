# pr-1146-video-preflight, round 1 results

**Candidate:** upstream `pull/1146/head` @ `90b3d74bd0b0f0c7ffce36f718f25bbea6c8c96f` (tree `e8aca7a51a477706de40ce3a36a322bcb492b8fb`)   **Baseline:** none (one build)
**APK md5:** C `b0720ed832356bbedcdc9e8856183dfa` (23615192 bytes); DEX `VideoPreflightRunner` count 73; `ACTION_QUERY_STATE` commit `90b3d74bd0b0`, versionCode 120, flavor github
**Unit:** D-HU (UNISOC MT50, Android 14, rooted, panel 1440x720 landscape, `wm size` 720x1440), D-POCO (phone), D-SAM (SM-T230, Android 4.4.2, wizard only)
**Date:** 2026-10-23
**Evidence:** release `rig-evidence-pr-1146-video-preflight`, asset `pr-1146-video-preflight-round1-captures.zip`, sha256 `dd79d9bee422be980680b162d0f039e5ef594eb1ac41d3a9960d1c341ac46c69`

Round verdict: the scripted runs are done; Stage O (O1 to O4) was not run (operator skipped it). Three of the four review findings (R-A, R-B, R-D) are confirmed on hardware; R-C could not be decided.

## Setup notes

- Resolved from a transfer-branch SHA. `transfer/rig-rounds` is held by worktree `rig-sync__scratch__8228cb07__20261015` (clean), so the fast-forward (32745f8c to 904f595e), `rig_sync.py sync` ("in sync") and this commit were done there. `CLAUDE.md` unchanged, no restart.
- Quirk files read: `topics/tooling.md`, `topics/audio.md`, `units/D-HU.md`, `units/D-SAM.md`, `units/D-POCO.md`. `TOOLS.md` "Pick a tool" table.
- Pre-flight before anything touched a device (`rig_preflight.sh D_HU:wifi,bt D_POCO:wifi,bt D_SAM`): D_HU wifi 1 bt 1 Awake; D_POCO wifi 1 bt 1 Dozing; D_SAM wifi 1 bt 1 Asleep; PREFLIGHT OK, no strays, lock free. Closing pre-flight, same roles: all wifi 1 bt 1 Awake, PREFLIGHT OK. An idle Gradle daemon was killed with `rig_cleanup.sh kill` before the closing check.
- Fetch gate: commit and tree matched. Build: `build_hur_cool.sh` in a worktree at 90b3d74b, exit 0. No unit tests on the rig (the dev host ran 3560, 0 failures, per brief). `ACTION_LOG_MARKER` is not in `CONFIGURING` on C, so markers worked with `allow-external-configuration=false`.
- Harness files in `$OUT`: `ptr_lib.sh` (copied from automation-screen-disconnect round 1), `sx.py` (automation-screen-disconnect round 2 brief s5), `cover_lib.sh` and `luma.py` (pr-1141 brief s5), `uiq.py`, `ph.py`, `pf_lib.sh`, `base.sh`, `w1/w2/w3/h1/h2/h3/s1/x.sh` (this brief), plus my wrapper `scripts/run.sh` (sources the libs, runs one script, ends with `script exit=N`). `extract_brief_blocks.py` needs backticked block names and this brief uses plain bold, so a one-off python extractor was used. All scripts were run directly by the host in the main session, not by `rig-executor`, and the rig lock was held by a `flock sleep` pid for the round.
- P3: `stat -c '%U:%G %a'` fails on D-HU (no `%a`). `ls -ld` read `drwxrwx--x u0_a176 u0_a176 shared_prefs`; `settings.xml` OWN `u0_a176:u0_a176`. Correct, no chown. `projection-health.xml` absent at start. Install left `settings.xml` byte-identical on D-HU and D-SAM.
- P4: D-MOTO airplane mode enabled, Bluetooth `enabled: false` (read back; restored at the end, wifi and Bluetooth back on). D-POCO rebooted: Bluetooth enabled, Wi-Fi enabled, `isGroupOwner: false`, Gearhead 18.0.664204-release, POCO_BT DC:B7:2E:5E:4E:59, bonds Navegadortz2=1 Navegadortz3=0. The 60 s poll hit its cap with Gearhead not yet running; it was on NotificationShade and needed wake, `wm dismiss-keyguard` and HOME. D-SAM clock 22 s behind host.
- P5: D-HU Bluetooth on, POCO_BT bonded, `SYSTEM_ALERT_WINDOW` allow. P6: 0 app processes.
- Injected taps (all by `tapid`, resource-id resolved in a fresh dump, exactly one match): W1 5, H1 1, H2 1, S1 5 (try 2). Taps in S1 resolved at (1200,752) on D-SAM.
- Brief flaw, W3 and the other session runs: the launch arms Native AA mode 3 and D-POCO joins by itself before the brief's poke, so `session()`'s SSL wait (counted from the poke) can miss the handshake. It hit both W3 tries and not W2 or H3. See W3.
- Brief flaw, H3: the home screen cannot be held in front during a live session (see H3).
- D-SAM: `logcat -G 16M` is refused (`klogctl: Operation not permitted`), harmless. S1 try 1 was void: a Bluetooth pairing dialog ("Confirm passkey is 770548 to pair with POCO X3 NFC", likely caused by D-POCO's reboot) covered the wizard (`S1 s0 onb_next NO_TARGET matches 0`). Cancelled with BACK and re-run once. The run's own `keys $SAM after` read returned a transient `Permission denied` (108-byte `S1-after.xml`); the file was re-read afterwards (`S1-after.keys.reread`).
- Thermal: host max 74C (W1, per the 20 s watcher), no throttle in any run (`throttle_delta=0`).
- Stage O: skipped at the operator's request.

## P. Precheck

**PASS**

- Build gate, root check, install and identity as above. `RUN=P; state`: `"commit":"90b3d74bd0b0","versionCode":120,"flavor":"github","wifiMode":"NATIVE"`.

## W1. The video test on D-HU, re-run on an existing install

**PASS**

- Settings written (read back): `$BASE`, `onboarding-version=0`, `has-completed-setup-wizard=true`, `connection-modes=wifi`, `video-codec=H.265`, `resolutionId=1`, `view-mode=2`, `screen-orientation=0`, `fps-limit=60`. One try, no void.
- Decisive lines:
  - `10-23 10:33:39.340 VideoPreflight: backend=TEXTURE clip=h264-480p fed=150 rendered=150 drawn=150 samples=15 bad=0 failure=NONE`
  - `10-23 10:33:44.757 VideoPreflight: backend=TEXTURE clip=h264-720p fed=150 rendered=150 drawn=150 samples=15 bad=0 failure=NONE`
  - `10-23 10:33:50.188 VideoPreflight: backend=TEXTURE clip=h265-1080p fed=150 rendered=150 drawn=149 samples=15 bad=0 failure=NONE`
  - `10-23 10:33:50.200 VideoPreflight: recommended codec=H.264 resolution=720p viewMode=TEXTURE verified=true notes=[]`
- Marker `W1-wiz` 10:33:14.813 to the recommendation: 35.4 s. 3 backend lines (my grep matched the block's count). `vt=0`, `apply=0`, FATAL 0.
- Keys before: video-codec H.265, resolutionId 1, view-mode 2, screen-orientation 0, fps-limit 60. After: video-codec H.264, resolutionId 2 (720p), view-mode 1 (TEXTURE), screen-orientation 2, fps-limit 60, `pending-renderer-confirm=true` written. The three keys equal the recommendation.
- Left at its seed: `fps-limit` (60), so it proves nothing about the apply. Screenshot `ui/W1-result.png` is in the asset.

**R-A (orientation): FAIL (finding confirmed).** `screen-orientation` went from 0 to 2 although the test does not measure it.

**R-B (H.265 replaced): FAIL (finding confirmed).** Panel width 720 (at most 1920). The `h265-1080p` clip read `failure=NONE` and `video-codec` after is H.264 although the seed was H.265.

## W2. A session on W1's settings, and its health record

**PASS**

- Valid: `wrapped=no`, `ssl=0`, `live=2`, `MATCH! Starting AapService` 0.
- `10-23 10:35:10.683 Media Sink Setup Request: 3 on channel VIDEO` (H.264, matches the stored codec).
- `10:35:10.276 Projection backend: viewMode=TEXTURE`; `10:35:10.285 [RES_CAP] resolutionId=2 ... chosen=_1280x720 ... linkCapped=none`.
- `rend` in live..hold: 18. Fed sum live..x: 2702.
- `ph_read end`: `count=1`, `session 1792769815918|H.264|TEXTURE|2|60|0|0|0|3095|0|0|3093`.
- Phone `Critical error` 0, FATAL 0.
- The renderer confirmation banner showed at 10:35:11.790 (`the renderer confirmation banner is up (the setup wizard changed the renderer)`); not tapped.

## W3. Leave the projection for the home screen, then end the session (R-C)

**UNTESTABLE**

- Both tries read `ssl=1`, which the brief makes UNTESTABLE. Try 1: SSL at 10:37:47.198, 11 s after `W3-start` and 5.5 s before the poke at 10:37:52.711. Try 2 (after rebooting D-POCO): SSL at 10:43:49.134, 4.6 s before the poke at 10:43:53.706. The session formed from the launch alone; the poke then found it live.
- For the record, not as a verdict: `live=27` and `26`, fed sum before HOME 1800 and 1803, `Decoder stopped: activity_stopped` after HOME (10:41:09.374 and 10:47:06.293), `stop=0`, and the health count moved 1 to 2 (try 1) and 2 to 3 (try 2). Stored lines: `...|H.264|TEXTURE|2|60|0|0|0|620|0|0|6265` and `...|621|0|0|6097`. So a record was stored after leaving to the home screen on both tries, which does not support R-C; the fed counter in the record (620) is far below the rendered counter (6265). fsum home..x 737.
- Try 1 captures are in `W3-try1/` in the asset.

## H1. A codec suggestion, Apply, and the next launch (R-D)

**PASS**

- Seeds: two sessions `H.265|TEXTURE|0|60|1|1|0|3000|0|0|2950`. `H1.card1` 1, message `Your last connections had trouble decoding H.265. Switch the video to H.264?`. `10:48:31` tap `projection_health_apply` at (1338,126), `tap=0`. `video-codec` after: H.264. `ph_read after`: `count=2 dismissed=CODEC_H264`. FATAL 0.

**R-D (stale suggestion): FAIL (finding confirmed).** `H1.card2` is 1 on the next launch with the same two stored sessions: `The picture froze or went black in your last connections. Switch View mode to SurfaceView?`

## H2. A frame-rate suggestion, Not now, and it stays hidden

**PASS**

- Seeds: two sessions `H.264|SURFACE|0|60|0|0|0|3000|0|0|2000`. `H2.card1` 1, message `The picture stuttered in your last connections. Limit it to 30 fps?`. `tap=0`, `H2.gone` 0, `dismissed=FPS_30`, `fps-limit` still 60, `H2.card2` 0, FATAL 0.

## H3. No suggestion while a session is live

**INCONCLUSIVE**

- Valid run: `ssl=0`, `live=2`, `MATCH` 0, phone `Critical error` 0, FATAL 0. `H3.pre` 1 and `H3.after` 1 hold.
- `H3.focus` named `AapProjectionActivity`, not `MainActivity`, so the live check (`H3.live` 0) is void per the brief. `10:51:23.450 START ... MainActivity` was followed by `10:51:23.565 START ... AapProjectionActivity` from the app's own pid 15166, 115 ms later: the app raises the projection over the home screen while a session is live, so the banner could not be observed either way.

## S1. The video test on D-SAM (Android 4.4)

**FAIL**

- Try 2 (try 1 void, see Setup notes). Seed on D-SAM's own backup: `$WIZ`, `video-codec=H.264`, `resolutionId=1`, `view-mode=2`, `screen-orientation=2`, `log-level=2`.
- Backend lines:
  - `10:54:00.213 backend=TEXTURE clip=h264-480p fed=150 rendered=148 drawn=112 samples=15 bad=0 failure=FEW_FRAMES`
  - `10:54:06.639 backend=SURFACE clip=h264-480p fed=150 rendered=149 drawn=-1 samples=0 bad=0 failure=NONE`
  - `10:54:12.845 backend=SURFACE clip=h264-720p fed=150 rendered=149 drawn=-1 samples=0 bad=0 failure=NONE`
  - `10:54:19.281 backend=SURFACE clip=h264-1080p fed=150 rendered=148 drawn=-1 samples=0 bad=0 failure=NONE`
- Recommendation: `codec=H.264 res=1080p view=SURFACE verified=false notes=[RENDERER_CHANGED, PICTURE_UNVERIFIED]`. `vt=0`, FATAL 0.
- Failed conditions: no `backend=TEXTURE` or `GLES` line with `failure=NONE` (TEXTURE drew 112 of 150; GLES was never tried), and `apply=1`: the result dump has no `onb_video_apply` node, only the summary `TextureView did not draw the picture, so the test recommends SurfaceView`. Met: the keys after (re-read) are `video-codec` H.264, `resolutionId` 3 (1080p), `view-mode` 0 (SURFACE), equal to the recommendation, and `pending-renderer-confirm=true`. The SurfaceView rows with `samples=0` are the expected unverified picture. Screenshot `ui/S1-result.png` is in the asset.

## O1 to O4. Stage O

**UNTESTABLE** (not run)

- The operator skipped Stage O. No hand step was started, nothing was marked and no capture was taken for it. O1 (untick keeps the settings), O2 (sound answers), O3 (nothing heard and the channel list), O4 (by ear on D-SAM) and the optional radio-choice tail remain unmeasured.

## X. Restore

**PASS**

- D-HU `settings.xml` restored with `put` and compared against `backup-D-HU.xml`: no difference. `projection-health.xml` removed (it was absent at the start). D-SAM `settings.xml` restored from `backup-D-SAM.xml` (diff empty) and its health file removed. D-MOTO out of airplane mode, Bluetooth enabled true and Wi-Fi enabled. C stays installed on D-HU and D-SAM. All logcat captures stopped by pid; closing pre-flight OK.

## Anything the brief did not ask about

- The wizard's Next on the Display step changed `screen-orientation` (R-A) and set `pending-renderer-confirm`, which made the first session show the one-time renderer bar.
- On D-SAM the apply box does not exist in the result dump when the recommendation is a change the test could not verify, yet the recommendation is written on Next. If the box is meant to be visible there, it is not.
- The health record's fed counter (620, 621) is far below its rendered counter (6265, 6097) for a session that went to the home screen: the decoder-stop reset the brief predicted for fed frames did not stop a record from being stored. The mismatch itself is worth a look in the record's fed and rendered fields.
- A Bluetooth pairing prompt appeared on D-SAM for D-POCO after D-POCO's reboot; the bond list on D-POCO still has Navegadortz3=0 after cancelling it.
- Gearhead was not running on D-POCO after a reboot until the first poke; pre-flight polling for it hits its 60 s cap.
