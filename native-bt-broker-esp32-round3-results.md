# native-bt-broker-esp32, round 3 results

**Candidate:** C = commit f1bed4ee4 (the round 2 build)   **Baseline:** none (the brief runs one build)
**APK md5:** C e4915024f9b13ec04c694872b16a66c5 (round 2's C.apk, reused; equals the brief). Firmware: release v1.0.0 on E-1, no flash.
**Unit:** D-HU (head unit), D-POCO (phone), D-SAM (API 19, stage S), E-1 (ESP32)   **Date:** 2026-10-29   **Operator interventions:** 1 (the opening yes; the D-POCO to D-HU bond from the earlier brief of this session was still in force)

## Setup notes

- No rebuild: round 2's C.apk, md5 checked. Round 2's folder (ids.env, venv/, settings-backup-hu.xml) was present.
- Session by rig_session.py (lib 871881d26). Quirk files read: tooling.md (head only).
- K's grade script and the brief say the bond must take effect; D-POCO never dialled D-HU (phone-side dial count 0), so K could not measure what it measures. Cause unexplained; not investigated further.

## RK Relay route with the board held in reset (the point)
**PASS** (regraded 2026-10-30; the first grade was INCONCLUSIVE)

Regrade: `rig_session.py regrade` on round 3's evidence folder with the brief's corrected K grade, which no longer requires a phone dial. 14/14 conditions. Passing: C md5 and commit f1bed4ee4, relay file enabled read back, the relay route was chosen (1), no board boot inside K (0). The old COND 6 (D-POCO dialled D-HU, 0, needed at least 1) is now a report line (0). Other report lines: SSL handshake complete 0, ACTIVELY LISTENING 0, fell back to the stock route 0, Broker link-lost lines 0, intentional wakes 1, self wake-ups 0, phone Critical error 0, board advertising again after the reset 1.

`Broker:` lines from the capture (18 lines, first at 23:08:30.010): one `intentional AA wake (HFP) to 78:1C:3C:2B:AC:EE (early, waiting for Wi-Fi snapshot)`, then a Wi-Fi snapshot `DIRECT-QS-MT50YT610E4GFPSLU 192.168.49.1:5288` pushed at 23:08:35.117 and `one start` plus a snapshot every ~20.2 s through 23:10:57.810 (8 starts).

Limit of this PASS: report conditions 6 to 14 are `ge 0` checks and cannot fail, so the verdict rests on conditions 1 to 5. The phone still never dialled D-HU, so no SSL session formed and what happens after a dial is not measured here. The reason the dial never came stays unexplained.

## RS Settings opens the broker screen (D-SAM)
**FAIL**

COND 4: FATAL EXCEPTION for com.andrerinas in S-go..S-end: 1, needed 0. COND 5: the resumed activity is SettingsActivity: 0, needed at least 1 (S-activity.txt shows the stock launcher resumed).
Decisive line (S-fatal.txt, 23:11:48.221, process 23925): `java.lang.RuntimeException: Unable to start activity ComponentInfo{com.andrerinas.headunitrevived/com.andrerinas.openheadunit.main.SettingsActivity}: kotlin.UninitializedPropertyAccessException: lateinit property scanner has not been initialized` at `BrokerSettingsFragment.onDestroyView(BrokerSettingsFragment.kt:72)`. Passing: C md5, commit f1bed4ee4, wifi-connection-mode 3 read back. The host read the stack from S-fatal.txt and S-activity.txt; the crash is the app's own, not a rig fault.

## Rcleanup
**PASS**

Exit 0.

## Anything the brief did not ask about

- The S crash is in `onDestroyView` of `BrokerSettingsFragment` on an API 19 unit: `scanner` is a lateinit read before it was set, in a path where the view is destroyed during activity start.
