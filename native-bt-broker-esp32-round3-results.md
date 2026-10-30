# native-bt-broker-esp32, round 3 results

**Candidate:** C = commit f1bed4ee4 (the round 2 build)   **Baseline:** none (the brief runs one build)
**APK md5:** C e4915024f9b13ec04c694872b16a66c5 (round 2's C.apk, reused; equals the brief). Firmware: release v1.0.0 on E-1, no flash.
**Unit:** D-HU (head unit), D-POCO (phone), D-SAM (API 19, stage S), E-1 (ESP32)   **Date:** 2026-10-29   **Operator interventions:** 1 (the opening yes; the D-POCO to D-HU bond from the earlier brief of this session was still in force)

## Setup notes

- No rebuild: round 2's C.apk, md5 checked. Round 2's folder (ids.env, venv/, settings-backup-hu.xml) was present.
- Session by rig_session.py (lib 871881d26). Quirk files read: tooling.md (head only).
- K's grade script and the brief say the bond must take effect; D-POCO never dialled D-HU (phone-side dial count 0), so K could not measure what it measures. Cause unexplained; not investigated further.

## RK Relay route with the board held in reset (the point)
**INCONCLUSIVE**

COND 6: phone: D-POCO dialled D-HU (the bond is in force): 0, needed at least 1. Passing: C md5 and commit f1bed4ee4, relay file enabled read back, the relay route was chosen (1), no board boot inside K (0), board advertising again after the reset (1). Report lines: SSL handshake complete 0, ACTIVELY LISTENING 0, intentional wakes 1, self wake-ups 0, phone Critical error 0.

## RS Settings opens the broker screen (D-SAM)
**FAIL**

COND 4: FATAL EXCEPTION for com.andrerinas in S-go..S-end: 1, needed 0. COND 5: the resumed activity is SettingsActivity: 0, needed at least 1 (S-activity.txt shows the stock launcher resumed).
Decisive line (S-fatal.txt, 23:11:48.221, process 23925): `java.lang.RuntimeException: Unable to start activity ComponentInfo{com.andrerinas.headunitrevived/com.andrerinas.openheadunit.main.SettingsActivity}: kotlin.UninitializedPropertyAccessException: lateinit property scanner has not been initialized` at `BrokerSettingsFragment.onDestroyView(BrokerSettingsFragment.kt:72)`. Passing: C md5, commit f1bed4ee4, wifi-connection-mode 3 read back. The host read the stack from S-fatal.txt and S-activity.txt; the crash is the app's own, not a rig fault.

## Rcleanup
**PASS**

Exit 0.

## Anything the brief did not ask about

- The S crash is in `onDestroyView` of `BrokerSettingsFragment` on an API 19 unit: `scanner` is a lateinit read before it was set, in a path where the view is destroyed during activity start.
