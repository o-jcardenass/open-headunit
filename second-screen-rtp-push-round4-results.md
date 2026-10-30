# second-screen-rtp-push, round 4 results

**Candidate:** C = commit 8d4af793b5e87a7b44f1e0ac01976a8db21766f6   **Baseline:** none (S4 is the control, a settings change on the same APK)
**APK md5:** C 72f6c98975476f1c9212add2cf166637 (round 3's C.apk, reused; equals the brief)
**Unit:** D-HU (head unit), D-POCO (phone), tester PC as RTP receiver; D-MOTO radios off   **Date:** 2026-10-30   **Operator interventions:** 1 recorded by the session (removing the UDP firewall rule); before the session the operator also turned D-POCO's Bluetooth on, added the UDP rule, and answered the opening yes (C1)

## Setup notes

- No rebuild: C.apk copied from round 3, md5 checked against the brief. rig-lib 2c7a270e0. Session by `rig_session.py`, 08:04:54 to 08:19:13.
- Quirk files read: tooling.md, units/D-HU.md, units/D-POCO.md. D-SAM.md was not read (the unit is not used here).
- Pre-flight before the session: first run FAIL (adb devices empty, then D-POCO Bluetooth off, then a root `apt-compat` cron `sleep 1251` flagged as a stray, then the operator's own `rig_watch.sh` tail flagged as a monitor). None were killed. D-HU 192.168.1.9/24, D-POCO battery 82%. Host 59 to 60 C before the session.
- No `CLOCKS` line or `clock-offsets.txt` was written by this session; cross-unit timestamps were not compared.
- Device files: 3 `LEFT` entries, each "listing failed, not checked": D-HU /sdcard/Pictures/Screenshots/, D-HU /sdcard/DCIM/Screenshots/, D-POCO /sdcard/DCIM/Screenshots/. Nothing was deleted.
- S5 grade defect: `s5.py` sets `rendered_ok` only when every run has exactly 30 windows (`x[0] == 30`). S3 and S4 had 29 windows each. The verdict below follows the brief's stated condition; the measurements are in R S5.

## R0
**PASS**

13/13 conditions: two PATCH_OK lines, live APK md5 equals apk/C.apk, `ACTION_QUERY_STATE` carries 8d4af793b5e8, DEX holds RtpRefreshPolicy and RtpPacingPolicy, UDP probe from D-HU reached the PC (5 datagrams), stream.sdp has 8 lines. Graded 08:05:39.

## S12 (the point of the round)
**PASS**

A1 to A4 met (graded 08:11:06). Refresh 10000 ms on wlan0: announce 1, open 1, output open 1, unavailable 0. A2: 25833 packets over 179.16 s. A3: 15 keyframe asks, cycled 15 (needs 14 or 15), early keyframes 0, failure lines 0. A4: answered 100%, longest IDR gap 11.57 s (limit 15.00 s). A5: 11 offsets, spread 0.02 s.

## S2
**FAIL**

B2: first frame after the join 25.60 s, limit 20.00 s (`first_frame_after_join_s x100: 2560 le 2000`). Everything else passed: traffic before the join, decode span 49.10 s, 1007 distinct frames, 5 distinct snapshots, 154 and 530 new frames after the two route changes, the map moved. Graded 08:11:08.

## S4
**PASS**

REPRODUCED (the rig shows the reported fault with the refresh off, `aux-rtp-refresh-seconds` 0): refresh 0 ms, open 1, unavailable 0, wlan0. 3 burst lines, 15 dropped, 15 lost. Graded 08:14:54.

## S3
**PASS**

C1 to C4 met (graded 08:18:59). Refresh 10000 ms, 3 burst lines, 15 dropped, 15 lost, 24928 packets over 149.92 s, unrecovered 0, longest break 10.99 s (limit 12.50 s), 13 asks, failure lines 0.

## S5
**FAIL**

`S5 F10=29.0 F0=29 fed10=29.0 fed0=29 fps_ok=yes rendered_ok=no` (graded 08:19:05). E1 fps passed (29.0 against 29, margin 3). E1 rendered_ok failed because of the exact-30 window rule above: S12 30 windows with 30 rendered, S3 29 windows with 29 rendered, S4 29 windows with 29 rendered, median 29 fps and fed 29 fps in all three. Every other E2 to E4 condition passed (no SSL failure, destroy, errors, shed, aux decoder or failure lines; no main stop, VSS or retake; no phone Critical error or No input for display).

## cleanup
**PASS**

1/1: D-HU settings.xml equals the R0 backup. Graded 08:19:11. The operator removed the UDP rule by hand after the cleanup cue.

## Anything the brief did not ask about

- S5's `rendered_ok` rule (`x[0] == 30`) fails on a capture that has 29 windows all rendered. The brief's author may want `windows >= 29` or a ratio.
- S2 missed its limit by 5.60 s while S12, S4 and S3 all got pictures; one attempt only, no re-run.
- Evidence zip and sha256: see the upload line the publishing step adds.
- Evidence: release asset `rig-evidence-second-screen-rtp-push-round4` / `second-screen-rtp-push-round4-captures.zip`, sha256 `c6d327f0ebd7318ce2f4ddb75e85adf622d1fd480a06ae0a6d260265babfd242`. Closing check: `rig_cleanup.sh list` clean; baseline left D-HP and D-SAM Bluetooth unreadable (API 19).
