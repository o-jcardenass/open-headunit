# surfaceview-crop - round 1 results

**Candidate:** fork/fix/surfaceview-crop @ ff21d2716 (on main c2c0e95aa)       **Baseline:** none (one build; TextureView sessions in the same build are the reference)
**APK md5:** 437bd99dbb44d4bc3fdb430c96e797cb (T) / none
**Unit:** D-SAM SM-T230, Android 4.4.2 (API 19), panel 800x1280 d213, run at override 1280x800 (rotation 1), head unit; D-POCO phone (API 35); D-HU and D-MOTO radios off
**Date:** 2026-10-30
**Operator interventions:** 1 (the opening yes; the S5 hand step is counted by the session in `interventions.txt`)

## Setup notes

- Quirk files read: topics/tooling, units/D-SAM, units/D-SAM-and-D-HP, units/D-POCO. topics/projection was not read.
- Pre-flight, all radios on at start: D-SAM wifi 1 bt 1; D-POCO 1 1; D-HU 1 1; D-MOTO 1 1 (HFP-bonded). PREFLIGHT OK. The session then switched D-HU Bluetooth and D-MOTO WiFi and Bluetooth off (`check --fix`).
- Driven by `rig_session.py run` from the pinned `rig-lib` (0f6b5a7a1). No executor agent was used. Build: `build_hur_cool.sh` in a `wt-new.sh` worktree at ff21d2716 (HEAD verified, `merge-base --is-ancestor c2c0e95aa` true), one build, Gradle stopped afterwards.
- D-SAM power: cabled to the PC. `dumpsys battery` read AC powered true, USB powered false, level 100, status 5 (FULL), 4324 mV, 30.0 C, identical across two reads 20 s apart. This contradicts the D-SAM quirk ("USB port is data-only, does not charge it"; USB powered true, AC false, 9%). The brief's operator line about a separate supply therefore did not apply. Quirk updated in this commit.
- Clocks (`clock-offsets.txt`; my earlier "matches the host" read of D-SAM was wrong, the ~27 s gap was the real offset): D-HU +756 ms, D-MOTO -517 ms, D-POCO -350 ms, D-SAM -26824 ms, left uncorrected (no root shell). Subtract it from D-SAM timestamps. All grades are within-unit.
- D-POCO bonds: no D-HU bond (D-SAM, D-MOTO and audio devices only), so the Queue's "forget D-HU" step was not needed.
- Operator confirmed D-POCO had projected to D-SAM before.
- Device files: 0 copied, 0 left. Cleanup restored D-SAM settings and rotation keys (0 differences).
- Host grade: S2's single failing condition is real (box edges measured directly, below); no grade overridden.

## Rsetup - install and gate

**PASS** (6/6) - T installed (md5 match), D-SAM reports commit ff21d2716, wifi-connection-mode 3 read back, settings backed up, API 19, rotation keys saved.

## RTX - TextureView reference

**PASS** (10/10)

- Lit box 957 permille (min and median), equal to round 1's 957 (deviation 0); L-USE-T 3, L-FALL 0, no L-FIT line, L-NORM scale 1.5005862,1.3508443 (same as round 1); no Critical error, no FATAL EXCEPTION.

## RS1 - SurfaceView, three sessions (the point)

**PASS** (18/18)

- Every S1 lit box at least 991 permille of TX's median (S1 min 949 against TX 957); worst edge deviation from TX 4 px (limit 8).
- Each of 3 sessions: last L-FIT equals the fit TX's scale gives; SurfaceView layer at the fit position within 2 px and size within 4 px; layer default-size 1280x800 (setFixedSize took).
- L-SURF 1280x800 in all sessions (6 lines), no L-SURF wider than the panel (the leak would read 1921x1081), L-MISM 0, L-NEWSURF 3, no crash.
- Round 1 of the previous thread read 396 permille for the same view mode on main; here SurfaceView lights what TextureView lights.

## RS2 - Normal screen mode, layer under the status bar

**FAIL** - COND 7: lit box edges within 8 px of TN's, measured 18 px (box 987 permille, TN 965).

- Settings: fullscreen-mode 0, screen-orientation landscape, one TextureView session (TN) then one SurfaceView session; both rendered (ssl=1).
- Control held: the SurfaceView layer (-319,-123 1920x1081) overlaps the bar layer (0,0,1280,33). Last L-FIT 1921x1081@-320,-157/1280x767.
- Bar pixels: 4 permille of 42240 differ from TN by more than 16 (limit 20), so the bar itself looks the same.
- Lit box on the 800x1280 raw frame: TN cols 28-798 (permille 965); SurfaceView cols 10-798 (permille 987), both rows 0-1279. The SurfaceView picture reaches 18 px further toward the bar side than TextureView's. Cause unexplained from this round: only the geometry above was measured.
- Brief's decision rule: FAIL means picture shows through or beside a bar, so add the `contentFillsScreen` gate in round 2. That is the coding side's call; the box difference is 18 px on one edge, the bar pixels are unchanged.
- Crash checks clean (Critical error 0, FATAL EXCEPTION 0).

## RS4 - Inset left 100

**PASS** (11/11) - last fit 1919x1079@-269,-119/1180x760, left edge -269 (< 100), top -119 (< 40); layer -268,-118 1923x1079; box 836 permille equals TI's 836 (2 px worst edge); 0 lit strip lines over SurfaceView (TI 0).

## RS5 - Same inset delivered by a move (hand step)

**PASS** (13/13) - ACTION_SET_SETTINGS imported both inset keys; L-DRIFT 1 and L-RESIZE 1 after the move; L-SURF 1180x760; box 836 permille equals TM's 836 (2 px); 0 lit strip lines. Levers: service_call for both arms.

## RS6 - Rotation

**PASS** (11/11) - L-RESIZE from 1280x800 to 800x1280; last L-FIT 800x1280@0,0/800x1280; last L-SURF and layer default-size equal the new buffer (800x1280); session renders after rotation (L-THR 3); re-announce lines after rotation: drift 1, uicfg 1, moved 0, mismatch 0.

## Rcleanup

**PASS** (3/3) - L-GIVE: D-SAM got its network back; settings and rotation keys equal the backups.

## Round verdict: FAIL (S2); the point of the round, S1, is PASS

Evidence: `rig-evidence-surfaceview-crop`, asset `surfaceview-crop-round1-captures.zip`, sha256 ddb5118e96473085d86504ef683c26f1abe4cf44164b5b3898f37bd699dba2e6.

## Anything the brief did not ask about

- D-SAM now charges from the PC port and reads AC powered; the brief's operator line and the quirk file were out of date.
- S2 is the only place the SurfaceView picture differs from TextureView's, and it is in a mode with a status bar. S4 and S5 (insets) match their TextureView references.
