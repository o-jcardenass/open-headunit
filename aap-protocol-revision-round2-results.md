# aap-protocol-revision, round 2 results

**Candidate:** B = A = 947b3ae9fae9834aa8e4145ee91751ed743c2ae0 (A* adds versionName 3.4.0)   **Baseline:** none for R6
**APK md5:** B 429f16d81ed5adc41183e69f6e4e4981, A* a0fd89c80398101cbe4bf44dad3d7bcb (round 1's APKs, reused; equal the brief). T and U not used (R6 only).
**Unit:** D-HU (head unit), D-POCO (phone, off the link), D-SAM (API 19, R6-SAM); D-MOTO Bluetooth off   **Date:** 2026-10-30   **Operator interventions:** 0 asks during the sessions beyond the opening yes (C2 to C4); the session printed three device-file OPERATOR lines, one per launch

## Setup notes

- rig-lib 2c7a270e0. Quirk files read: tooling.md, units/D-HU.md, units/D-POCO.md. D-SAM.md was not read.
- **Three session launches; only the third counts.** Launches 1 and 2 were my errors, and their grades are in `voided-launch1/` and `voided-launch2/`:
  - Launch 1 (08:24:53): `RIG_TOOLKIT` was unset in the host shell, so `rl_thermal_gate` ran `/rig_thermal.sh` (`rig_lib.sh: line 144: /rig_thermal.sh: No such file or directory`) and R6-HU and R6-SAM were graded UNTESTABLE (host thermal) within 1 s, at a host temperature of 59 C.
  - Launch 2 (08:25:57): I skipped the brief's Prepare step that copies the build's `res` to `$EV/res`. `R6-SAM-strings.txt` held empty `row=`, `apk=`, `rel=`, `title=`, so both runs searched for an empty row (ROW_NOT_FOUND). `res` was then copied from round 1's folder and verified identical to the 947b3ae9 tree (`diff -rq`, no output).
  - Launch 3 (08:35:14 to 08:40:55): strings read `row=Check for updates`, `q=updates`.
- Pre-flight before launch 3: OK (D-HU, D-POCO, D-SAM, D-MOTO). The earlier pre-flights failed on the root cron sleep and the operator's `rig_watch.sh` tail, neither killed.
- No `CLOCKS` line or `clock-offsets.txt` was written.
- Device files: 3 `LEFT` entries ("listing failed, not checked"): D-HU Pictures/Screenshots and DCIM/Screenshots, D-POCO DCIM/Screenshots. Four session files were copied and removed.

## pre
**PASS**

1/1 conditions (graded 08:24:58 in launch 1; kept, launch 3 skipped it).

## R6-HU
**INCONCLUSIVE**

`c1 never reached the dialog: R6-HU-c1 ROW_NOT_FOUND; c2 never reached the dialog: R6-HU-c2 ROW_NOT_FOUND` (graded 08:38:18). The saved dump `R6-HU-c1-list.xml` (08:36:52) shows `com.android.launcher3` windows and no `com.andrerinas.headunitrevived` node, so Settings did not come to the front on D-HU. Cause unexplained; the script starts it with `run-as ... am start --user 0 --es extra_search_query updates`.

## R6-SAM (the point of the round)
**INCONCLUSIVE**

Same two ROW_NOT_FOUND lines (graded 08:40:33). Here the screen is the app's Settings: `R6-SAM-c1-list.xml` (08:39:39) contains `text="Check for updates"` with `resource-id=...:id/settingName`, bounds [450,310][1175,348], and `python3 -I scripts/ui.py find R6-SAM-c1-list.xml :id/settingName "Check for updates"` run afterwards prints `812 329` with exit 0. The script's 30 s wait loop still logged ROW_NOT_FOUND. Why the loop's own call did not match is unexplained. No button was pressed, so the update dialog was not reached and the point of the round (the "Download APK" button on API 19) is not measured.

## cleanup
**PASS**

2/2 conditions (graded 08:40:53).

## Anything the brief did not ask about

- The R6 wait loop is the thing to fix before another round: on D-SAM the row is in the dump and the loop misses it. On D-HU Settings never appeared.
- Round 1 also ended with R6 "settings row not found", so this is the second round lost to the same step.
- Evidence: release asset `rig-evidence-aap-protocol-revision-round2` / `aap-protocol-revision-round2-captures.zip`, sha256 `2dbe866582e77ae73968da69b74cdc7a7b9ef6ea9f5adc6910760982ecb8548b`. Closing check: `rig_cleanup.sh list` clean; baseline left D-HP and D-SAM Bluetooth unreadable (API 19).
