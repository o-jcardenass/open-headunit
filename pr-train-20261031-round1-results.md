# pr-train-20261031, round 1 results

- Candidate: T `8e0452ddc` (APK md5 `e925f1aa31d4f9f761211aaac81d95c4`)
- Baseline: B `c2c0e95aa` (APK md5 `ab3515f73f93f133de0a565177960494`)
- Units: D-HU, D-POCO, D-MOTO, D-SAM, board E-1. Date: 2026-10-31.
- Lib `8191b7c4c`. Session 09:36:28 to 10:00:15. Operator interventions: 4 (see Setup notes).
- Totals: 8 PASS, 2 FAIL, 2 INCONCLUSIVE, 1 UNTESTABLE (13 runs incl. setup, flash, cleanup).

## Setup notes

- Pre-flight before the session: FAIL on D-POCO (wifi_on=0, bluetooth_on=0). Left to `check --fix`, which switched them; the session then reported REQUIREMENTS OK. Other roles ok; D-SAM screen asleep.
- Clocks: D-HU -114 ms, D-MOTO -529 ms, D-POCO -363 ms, D-SAM -26291 ms (no root shell, left; subtract from D-SAM times).
- The host did not read the trimmed template, the quirk files or the TOOLS.md entries in full this round (the session is script-driven); only the TOOLS.md 'Pick a tool' table was read.
- Operator interventions (4, per the session): the two E1 OPERATOR lines (pair OpenHU-BT-Relay on D-POCO, done in 22 s), plus the E1 helper note and the C1 server question the host logged. The opening A to D message with B and C answers came before the session.
- **Brief defect, E1:** pairing the relay was a hand step, although the rig has a BT helper (`rig_pair.py` / `rig_bt.py`). The ESP32 shows as a normal Bluetooth device in D-POCO's scan, so the helper could pair it. `e1.sh` does not call it. E1 should pair through the helper.
- **Brief defect, C1:** "Start head unit server is on" was a question to the operator. It is automatable in 3 taps: launch the Android Auto shortcut over adb, the three-dot menu at the upper right, Start head unit server. Replace C1 with a scripted step that reads `:5277` and runs the taps if absent.
- **Brief defect, E5:** `e5.sh` passes the query as an intent extra, then dumps the UI after a fixed sleep. The dump caught the SystemUI recents screen, not Settings, so the grade could not be read.
- Diagnosis of X1, PB, E4, E3 and E5 used one grep-only `rig-executor` pass over the captures; the host re-read its numbers and settings (E3-settings.xml: wifi-connection-mode 1 as the brief asks).

## Rsetup
**PASS**

8/8: Esp32RelaySession in the T DEX 35, in B 0; T and B installed (md5) on D-HU and D-POCO; commits read back; settings backups taken.

## RX1
**INCONCLUSIVE**

Point of the round: a second service start inside the 5 s Stop wait must not crash. The race was not produced. Measured (D-HU capture): X1-exit 09:37:50.049, X1-exit2 09:37:50.219, Stop 09:37:50.176, `AapService destroying` 09:37:50.518, second Stop 09:37:51.003, second destroy 09:37:51.043. The first destroy fell before the second Stop, so the second start landed after the destroy, outside the wait (grade: both Stops before first L-DES 1 of 2). Everything else held: S-CRASH 0, S-FGS 0, S-FGSX 0, no AapService record 20 s later, phone P-CRIT 0. No crash was seen, but this run cannot show the fix works.

## RPB
**FAIL**

Baseline arm B, not a point. Stated condition: P-CRIT 0 in the window; counted 1. Line (D-POCO phone capture, 09:39:25.854): `E GH.CAR  : Critical error 3 (5): Couldn't connect to receiver socket`, with `WIRELESS_WIFI_SOCKET_CONNECTION_FAILED` at 09:39:25.850 and `socket retry count = 3`. The session later reached L-SSL. The other conditions held (L-MOVE 0, final anchor 2400x1080 equal to mAppBounds, S-CRASH 0, L-ZBT 0). Cause unexplained; the same check was 0 on T in P1.

## RRB
**PASS**

8/8. resolutionId 1 then 2 read back with the process alive, MainActivity not resumed, phone reconnected, announced 1280x720.

## RP1
**PASS**

11/11 on T. L-MOVE T 0 vs B 0, L-HON T 2 vs B 1 (limit +2), final anchor 2400x1080 = mAppBounds, L-ZBT 0, S-CRASH 0, P-CRIT 0.

## RR1
**PASS**

8/8 on T. resolutionId 2 read back with the process alive, phone reconnected after the arm, announced 1280x720 is the new 720p (B also 1280x720; 480p is the stale announce), P-CRIT 0.

## Rflash
**PASS**

5/5. Four bins match the v1.0.1 sha256 values, esptool verified four regions, E-1 advertises, D-POCO holds no relay bond.

## RE1
**PASS**

12/12. Relay row present, L-PAIRW, F-PMODE, F-PAIRED, L-PAIRC, `Esp32Relay.xml` holds an address and is enabled, D-POCO lists OpenHU-BT-Relay, injected taps 3 (limit 5), S-CRASH 0.

## RE2
**PASS**

34/34. 5 of 5 sessions over the relay: each cycle L-SSL 1, L-RELAY 1, L-LIST 0, the phone dialled the board (78:1C:3C:2B:AC:EE) and never D-HU. S-CRASH 0, P-CRIT 0.

## RE4
**FAIL**

Point of the round. Stated condition failed: `Creating rfcomm socket for device: 78:1C:3C:2B:AC:EE` after E4-relaunch counted 0 (1 before the kill, at 09:53:23.110). Other conditions held: first session over the relay, L-SSL after the relaunch 1 (09:54:04.103), F-BOOT 0, F-CLEAR 1, S-CRASH 0. Observed after the relaunch (D-POCO): the old RFCOMM link reported 7 unanswered pings until 09:53:59.449; at 09:54:00.216 `WIRELESS_SETUP_SHARED_HFP_CONNECTING` and HFP device state 1 (09:54:00.196), state 0 at 09:54:04.013; D-HU log `intentional AA wake (HFP)` at 09:53:59.895 and Wi-Fi snapshot sent 09:54:00.134. So the session came up by a path that logs no RFCOMM dial. Why that path was taken is unexplained. The decisive string may be too narrow for this path; the host did not change the grade for that.

## RE3
**UNTESTABLE**

Gate `session=none`: no `SSL handshake complete` in D-HU's capture in the 150 s window (09:55:06 to 09:57:36). The :5277 prerequisite check on D-POCO passed at the start (the script exits 3 otherwise). Settings: wifi-connection-mode 1. D-HU swept 192.168.1.* (253 probed, 0 responded on 5289). Window has no E3-ssl, E3-check or E3-exit markers. Cause unexplained; the relay-leaves-non-relay-sessions-alone question is still open.

## RE5
**INCONCLUSIVE**

`text="esp32"` count 0 in `ui-E5-list.xml`: the dump is the SystemUI recents screen (package com.android.systemui), not Settings. Conditions that held: T and commit on D-SAM, mode 3 read back, no relay row on API 19 (0), S-CRASH 0, the relay screen guard line 1. The search field state cannot be read. D-SAM times are 26.3 s behind the host.

## Rcleanup
**PASS**

5/5. B on D-HU and D-POCO, settings equal the backups, D-POCO holds no relay bond. Device files: 0 left.

## Anything the brief did not ask about

- E4's decisive string misses the HFP-wake reconnect path seen after a force-stop. Worth deciding whether the brief means "the phone dials the board" or "a new session comes up".
- PB's P-CRIT hit and E3's no-session are both unexplained; each needs a repeat before it is blamed on a build.
- Evidence: release `rig-evidence-pr-train-20261031`, asset `pr-train-20261031-round1-captures.zip`, sha256 `7b06309b14a044f5b52a1dee7d0934ae1eb506042e3fa5a958e1cdede73aad7d`.
- Closing pre-flight: OK (D-HU, D-POCO, D-MOTO wifi and bt on; D-SAM wifi and bt on, screen asleep); baseline radios OK; no strays; device files 0 left.
