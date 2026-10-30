# pr-train-20261029, round 2 results (stage W only)

**Candidate:** T = fork/test/pr-train-20261029 @ e2b1264c5265ac32ad2667f889838a24664c467c   **Baseline:** B = main @ a4f80ff1b6aa6726c17e7385192cf23abe0d0e9f
**APK md5:** T 2dda80522bfb9b5453ba0a6e7bdf8bcd / B 9a077f29fb3ba545285fc028f69cba0a (round 1's APKs, reused, md5 equal to the brief)
**Unit:** D-SAM (head unit, API 19), D-POCO (phone, no D-HU bond), D-HU Bluetooth off, D-MOTO radios off
**Date:** 2026-10-29   **Operator interventions:** 1 (the opening yes; it covered five briefs)

## Setup notes

- Builds: no rebuild; round 1's T.apk and B.apk were copied to the round folder and their md5s match the brief. Setup run confirmed T by DEX (NightOverlayState 30) and B (0).
- Session run by rig_session.py (lib 871881d26); every run graded by its grade script. Quirk files read: tooling.md (head only).
- Pre-flight before launch: PREFLIGHT OK (D-SAM, D-POCO, D-HU, D-MOTO present, radios as the brief needs).
- Capture files are not cleared between runs: the W1 grep above was windowed from the W1-start marker (22:30:53.467) and, for the post-relaunch counts, from the W1-relaunch marker (22:32:30.431) to W1-end (22:35:30.707).
- Operator annotation: D-SAM's projection looked cropped in one corner. The first screenshot (user-dsam-cropped-corner-223110.png, 22:31) shows the launcher only and did not capture it. A second screenshot during W1 (user-dsam-projection-223236.png, 22:32) shows the map filling only the top ~740 px of the 800x1280 screen, the rest black, and the Search pill at the right edge clipped. Not graded by any condition; cause unexplained.

## Rsetup
**PASS**

7/7 conditions (T DEX has NightOverlayState, B has none, B installed by md5, commit a4f80ff1b, mode 3 and stable identity read back, D-POCO holds no relay bond).

## RWB Positive control: a pending rotation is skipped on main
**PASS**

10/10. L-ADOPT 1, same SSID before and after the kill (DIRECT-A4-Navegadortz3), second SSL session 2 handshake lines (22:29:13.589), kill to relaunch 3 s.

## RW1 Pending rotation purges and re-creates (the point)
**FAIL**

L-SSL after W1-relaunch: 0, needed at least 1. The other 10 conditions passed: T md5 and commit e2b1264c5, rotation-pending true read back, L-ADOPT 0, L-PURGE 1, a new SSID after the kill (DIRECT-cT-Navegadortz3 then DIRECT-om-Navegadortz3), 87 RFCOMM dials on the phone, 0 Critical error, 0 FATAL EXCEPTION.

Windowed counts from W1-relaunch (22:32:30.431) to W1-end (22:35:30.707): createGroup SUCCESS at 22:32:38.569, WifiStartRequest (Type 1) sent 3, Received Type 2 3, Successfully delivered TYPE 3 0, WirelessServer Incoming connection 0, SSL handshake complete 0. The first session before the kill did complete (22:31:58.230). Cause unexplained; the new group came up and the phone answered the handshake, but credentials were never delivered and no TCP connection arrived.

## RW2 Ordinary relaunch still reads the group
**PASS**

10/10. rotation-pending false, L-ADOPT 1, L-PURGE 0, second SSL session 2 handshake lines.

## Rcleanup
**PASS**

Exit 0.

## Anything the brief did not ask about

- The cropped D-SAM projection described in Setup notes appeared in the T build's first W1 session; it was not compared against B (WB ran B and was not looked at). Worth a control on B before attributing it to T.
