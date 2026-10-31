# surfaceview-crop - round 2 results

**Candidate:** fork/fix/surfaceview-crop @ ff21d27165c125e4d3d93e51744e335eed4a9588 (tree 6715bb42ff7bc375c71bc24e00c765f120671c49, on main c2c0e95aa)       **Baseline:** none (one build; TextureView sessions in the same build are the reference)
**APK md5:** 3e4b3a383ef92b4e81483aac494da4ed (T) / none
**Unit:** D-SAM SM-T230, Android 4.4.2 (API 19), run at override 1280x800, head unit; D-POCO phone; D-HU and D-MOTO radios off
**Date:** 2026-10-30
**Operator interventions:** 1 (the opening yes; the session raised no OPERATOR or ESCALATE line)

## Setup notes

- Quirk files read: none in full. The session is scripted and the host read the brief only through `rig_session.py view`; the template and quirk files were sized but not read this round. Round 1's Setup notes cover D-SAM, D-POCO and the clocks.
- Pre-flight before the build: D-SAM wifi 1 bt 1, D-POCO wifi 1 bt 1, PREFLIGHT OK. The session's `check --fix` then switched D-HU Bluetooth and D-MOTO WiFi and Bluetooth off (`requirements.ok`, 23:19:57).
- Driven by `rig_session.py run` from the pinned `rig-lib` (0f6b5a7a1). No executor agent. Build: `build_hur_cool.sh` in a `wt-new.sh` worktree at ff21d2716 (HEAD and tree read back and match the brief), one build, Gradle stopped afterwards.
- **The APK is a fresh build of the same commit, not round 1's file:** md5 3e4b3a38... here against 437bd99d... in round 1. Both are ff21d2716; the md5 differs and the host did not investigate why. The setup run's commit check (D-SAM reports ff21d2716) passed.
- Clocks (`clock-offsets.txt`): D-HU +753 ms, D-MOTO -525 ms, D-POCO -372 ms, D-SAM -26852 ms, left uncorrected (no root shell). All grades are within-unit.
- Operator confirmed D-POCO had projected to D-SAM before (C1: yes).
- Device files: 0 copied, 0 left. Cleanup found no saved network id and D-SAM settings equal the backup.
- Host grade: no grade overridden; the host read the COND lines of all three runs. No retries (attempts 1 each), no STALL.

## Rsetup - install and gate

**PASS** (5/5) - T installed (md5 match), D-SAM reports commit ff21d2716, wifi-connection-mode 3 read back, settings backed up, API 19.

## RS2 - SurfaceView against TextureView, each session's own margins taken out

**PASS** (14/14)

Round 1 graded raw box edges and failed on an 18 px width difference. This round models each session's own margins first. Measured (`S2-sessions.txt`):

| Session | View | Bottom margin (B) | Picture top / bottom | Midpoint | Height |
|---|---|---|---|---|---|
| tn1 | TextureView | 116 | 59 / 772 | 415.5 | 714 |
| sv1 | SurfaceView | 105 | 43 / 789 | 416.0 | 747 |
| tn2 | TextureView | 105 | 42 / 788 | 415.0 | 747 |
| sv2 | SurfaceView | 116 | 60 / 773 | 416.5 | 714 |
| tn3 | TextureView | 105 | 42 / 788 | 415.0 | 747 |
| sv3 | SurfaceView | 116 | 60 / 773 | 416.5 | 714 |

- Margin model: scale 1.409, K 1.5014, bottom margins 105 and 116. TextureView E spread 1 px; worst SurfaceView E offset 3 px.
- SurfaceView picture midpoints within 2 px of TN's median 415.0 (limit 8; round 1: 416.0 against 415.5). Left and right edges within 0 px of TN's median (limit 8).
- Matched pairs (4 with equal bottom margins): raw bottom edges within 2 px (limit 8).
- Bar pixels that differ from the TN session before: worst 7 permille (limit 20; round 1: 4).
- L-USE-S in 3 of 3 SurfaceView sessions; L-FALL 0 (no TextureView session fell back to SurfaceView). Control: the SurfaceView layer overlaps a bar layer in 3 of 3 SurfaceView sessions, layer -319,-123 1920x1081 against default 1280x767.
- Phone: Critical error count 0; phone capture holds the same 19 S2 markers as D-SAM's. No FATAL EXCEPTION for com.andrerinas on D-SAM (0).
- Sessions left out at admission 0, clipped at a bar 0.

## Rcleanup - give back

**PASS** (2/2) - no network id was saved so nothing to give back; D-SAM settings equal the backup.

## Round verdict: PASS

## Anything the brief did not ask about

- The picture height alternates between 747 and 714 px by session in both views and follows the bottom margin (105 gives 747 in tn2, tn3, sv1; 116 gives 714 in tn1, sv2, sv3). It is a property of the session, not of the view, which is why the round 1 raw comparison failed.
- The md5 difference against round 1 for the same commit (Setup notes) is unexplained.

Evidence: `rig-evidence-surfaceview-crop`, asset `surfaceview-crop-round2-captures.zip`, sha256 09ef519b84553b0a5e64720d37f6bd2c8b7ac7b3a97ec9f67634aa3e69ce81d5.
