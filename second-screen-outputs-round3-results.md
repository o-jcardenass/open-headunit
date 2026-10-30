# second-screen-outputs, round 3 results

**Candidate:** C = fork/feat/second-screen-outputs @ 2812a70e22159bcc4282f045eb3843ec29be6542 (tree c0d0a04b5a1da4b8c573a45c517bc26866a8c8d7)   **Baseline:** none (O1 is the feature-off control on C)
**APK md5:** C 5df3502bcdba424042879a1bd5462238
**Unit:** D-HU (head unit), D-POCO (phone, bonded to D-HU), D-MOTO radios off   **Date:** 2026-10-29   **Operator interventions:** 1 (the opening yes)

## Setup notes

- C built in a candidate worktree by the host (tree hash checked), back to back with the rtp-push arm, before any capture.
- Session by rig_session.py (lib 871881d26); every run graded by its grade script and passed with no retry. The D-POCO to D-HU bond left by the previous brief was still in force, so no pairing was asked.
- Condition counts are the grade scripts' own; no condition was overridden.


## R0 Identity and prerequisites

**PASS**

5/5 conditions

- PASS the live APK md5 equals apk/C.apk: 1 eq 1
- PASS the DEX holds RtpStreamOutput: 9 ge 1
- PASS the DEX holds AaPresentationSession: 7 ge 1
- PASS ACTION_QUERY_STATE data= carries 2812a70e2215: 1 ge 1
- PASS POCO_MAC is a real address (DC:B7:2E:5E:4E:59): 1 eq 1

## O1 Feature-off control

**PASS**

15/15 conditions

- PASS ssl: 1 eq 1
- PASS ann: 0 eq 0
- PASS lane: 0 eq 0
- PASS rtp_open: 0 eq 0
- PASS rend: 8 ge 3
- PASS quit: 1 eq 1
- PASS stop_main: 1 eq 1
- PASS q2s in tenths of a second: 0 le 100
- PASS cleanup_fail: 0 eq 0
- PASS pids: 1 eq 1
- PASS destroy: 0 eq 0
- PASS fatal: 0 eq 0
- PASS anr: 0 eq 0
- PASS phone Critical error in the hold window (file: 5): 0 eq 0
- PASS phone markers in the window: 2 ge 1

## P1 Second screen output (the point)

**PASS**

47/47 conditions

- PASS s1 ssl: 1 ge 1
- PASS s1 ann: 1 ge 1
- PASS s1 ann_off: 0 eq 0
- PASS s1 lane: 1 ge 1
- PASS s1 rtp_open: 1 ge 1
- PASS s1 rtp_sent: 3 ge 2
- PASS s1 start_fail: 0 eq 0
- PASS s1 rend: 6 ge 3
- PASS s1 vid_err: 0 eq 0
- PASS x1 quit: 1 eq 1
- PASS x1 stop_main: 1 eq 1
- PASS x1 q2s in tenths: 0 le 100
- PASS x1 rtp_stopped: 1 ge 1
- PASS x1 closed: 1 ge 1
- PASS x1 cleanup_fail: 0 eq 0
- PASS s2 ssl: 1 ge 1
- PASS s2 ann: 1 ge 1
- PASS s2 ann_off: 0 eq 0
- PASS s2 lane: 1 ge 1
- PASS s2 rtp_open: 1 ge 1
- PASS s2 rtp_sent: 3 ge 2
- PASS s2 start_fail: 0 eq 0
- PASS s2 rend: 6 ge 3
- PASS s2 vid_err: 0 eq 0
- PASS ll quit: 1 eq 1
- PASS ll stop_main: 1 eq 1
- PASS ll q2s in tenths: 0 le 100
- PASS ll rtp_stopped: 1 ge 1
- PASS ll closed: 1 ge 1
- PASS ll cleanup_fail: 0 eq 0
- PASS LL_QUIT_AFTER_S is a number: 16 ge 0
- PASS s3 ssl: 1 ge 1
- PASS s3 ann: 1 ge 1
- PASS s3 ann_off: 0 eq 0
- PASS s3 lane: 1 ge 1
- PASS s3 rtp_open: 1 ge 1
- PASS s3 rtp_sent: 3 ge 2
- PASS s3 start_fail: 0 eq 0
- PASS s3 rend: 6 ge 3
- PASS s3 vid_err: 0 eq 0
- PASS pids: 1 eq 1
- PASS destroy: 0 eq 0
- PASS fatal: 0 eq 0
- PASS anr: 0 eq 0
- PASS phone Critical error in the hold windows (file: 13): 0 eq 0
- PASS phone No input for display: 0 eq 0
- PASS phone markers in the windows: 6 ge 3

## A1 Smoke after the three rebases

**PASS**

19/19 conditions

- PASS up1: 1 ge 1
- PASS open_fail: 0 eq 0
- PASS carry: 0 eq 0
- PASS session 1 aux windows_rendered_ge1: 6 ge 1
- PASS session 1 main windows_rendered_ge1: 6 ge 3
- PASS quit: 1 eq 1
- PASS stop_main: 1 eq 1
- PASS stop_aux: 1 eq 1
- PASS q2s in tenths: 0 le 100
- PASS cleanup_fail: 0 eq 0
- PASS session 2 aux windows_rendered_ge1: 7 ge 1
- PASS first aux picture after A1-x1-end, tenths: 165 le 900
- PASS session 2 main windows_rendered_ge1: 7 ge 3
- PASS pids: 1 eq 1
- PASS destroy: 0 eq 0
- PASS fatal: 0 eq 0
- PASS anr: 0 eq 0
- PASS phone Critical error in the hold windows (file: 10): 0 eq 0
- PASS phone No input for display: 0 eq 0

## Rcleanup

**PASS**

2/2 conditions

- PASS D-HU settings.xml equals the R0 backup: 1 eq 1
- PASS overlay_display_devices is null: 1 eq 1

## Anything the brief did not ask about

- Nothing outside the brief's conditions was observed; no run needed a host override.

- Evidence: release `rig-evidence-second-screen-outputs`, asset `second-screen-outputs-round3-captures.zip` (16103835 bytes), sha256 `638d5dd083ffefd9d5194159b2509b05df5b73113ebdfd6e55057cce66d727da` (moved from `rig-evidence-second-screen-outputs-round3` on 2026-10-30).
