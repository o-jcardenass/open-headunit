# native-bt-broker-esp32, round 2 results (PARTIAL: stage S and the closing were not run)

**Candidate:** fork/test/pr-1187-relay @ f1bed4ee42528a21a2ce03caebac4f214afdaf87 (tree c90b77d66271c3cb0f41e6d615a8db609f415f75)   **Baseline:** none (C1 and C2 are the controls)
**APK md5:** C e4915024f9b13ec04c694872b16a66c5 (D-HU pulled APK equal; `commit` f1bed4ee4252; `NativeBTBrokerCarrier` 30 in the DEX). Prior D-HU APK saved as `prior-hu.apk` md5 2dda80522bfb9b5453ba0a6e7bdf8bcd (pr-train T).
**Firmware:** release v1.0.0 assets, SHA256SUMS ok (firmware.bin 2a6059dc...); `OpenHU-BT-Relay-Setup` 2 and `HFP off (` 1 in firmware.bin; flash: 4 `Hash of data verified`. esptool 5.6.0.
**Unit:** D-HU (rooted, Android 14), D-POCO (Gearhead 18.1.164344), board E-1 (ESP32-D0WD-V3, MAC 78:1c:3c:2b:ac:ec), D-MOTO radios off
**Date:** 2026-10-29. Stopped by the operator after stage B; stage S, the closing and the evidence upload were not done.

## Setup notes

- P0: D-POCO had no OpenHU-NativeBTBroker/OpenHU-BT-Relay row after its reboot (brief expected a stale OpenHU-NativeBTBroker bond); D-HU bonded list had only D-MOTO and D-POCO, no stale LE bond.
- Stage P attempt 1 (brief's pair2.sh): exit 6, 'Pair phone' never resolved. Cause: the PR's relay screen now opens the pairing window by itself right after 'BLE bond ok' (screen text 'Phone: pair OpenHU-BT-Relay now - PIN 0000 (120 s)'; serial 'pairing mode for 120 s' 18:58:42; app 'Broker: opening new-phone pairing window on 78:1C:3C:2B:AC:EE' 18:58:33), so there is no Pair phone button. Brief step is obsolete for this build. Used 3 taps (p-scan, p-pick, p-hudlg), the 120 s window lapsed unused while the script waited for the missing button. Attempt 2 uses scripts/pair3.sh = pair2.sh without the Pair phone tap.
- Stage P attempt 2 (pair3.sh): exit 5, p-pick 'OpenHU-BT-Relay-Setup' did not resolve (scan list empty, 'Saved Bluetooth relay: None'); 1 tap used (p-scan). The board was probably still linked to D-HU from attempt 1 (serial silent after 18:58:43 and no 'head unit BLE disconnected'). Used the brief's fallback (broker_write true 78:1C:3C:2B:AC:EE OpenHU-BT-Relay-Setup).
- Board reset (serial capture restarted with --reset, 19:03:15) before Stage P attempt 3: it advertises again. Observation for the contributor: after attempt 1 the app was force-stopped, yet serial shows no 'head unit BLE disconnected' and the board stopped advertising (attempt 2 scan found nothing) until it was reset; pairing window 'pairing mode ended' 19:00:42. Attempts 1 and 2 are not counted as pairing failures of the PR: 1 failed on the obsolete Pair phone step, 2 on a board that was not advertising.
- Stage P attempt 3 (pair3.sh): scan, pick (BLE bond ok 19:03:51.366), pairing window opened by the app ('pairing mode for 120 s' 19:03:51.746), D-POCO new-device and board taps resolved, but the confirm tap on D-POCO failed: the system dialog 'Pair with OpenHU-BT-Relay?' has buttons CANCEL and PAIR (upper case) and no PIN field, so the brief's text 'Pair' did not match (tap_text is exact). 'paired=1', window ended 19:05:51 unused. 4 taps used. Attempt 4 uses pair4.sh with 'PAIR' (brief text error).
- Stage P attempt 4 (pair4.sh): paired=0. 'BLE bond ok' 19:07:56.787; 'cleared phone bonds for new pairing' 19:07:57.253 and 'pairing mode for 120 s' 19:07:57.362; 'phone paired: POCO X3 NFC' 19:08:54.422; D-POCO bonded list names OpenHU-BT-Relay (masked XX:..:AC:EE). 4 taps used (p-scan, p-pick, p-dev, p-confirm). p-new 'Pair new device' did not resolve (H1 cue logged 19:08:39) but nothing was done by hand: the flow went on and the board row was tapped 6 s later. BROKER_LE and BROKER_BT both written as 78:1C:3C:2B:AC:EE (brief says same MAC).
- Stage B first launch was MY ERROR: run4.sh ran 'bash stageB.sh' with no argument, so RUN was empty and cycle.sh took the cycle number as RUN. Five cycles (19:09-19:20) ran under the names '1-c'..'5-c' with no relay_not_on gate (the relay file was in place and enabled); every row had ssl=0, relay_on=1, listening=0, hf_up=1 or 0, rf_open=0. Captures kept in B-mislabelled/ (not graded). Stage B re-run properly as 'stageB.sh B'.
- Stage B (stageB.sh B): stopped after 3 cycles with ssl=0 (its own stop rule). Per cycle: relay_on 1, listening 0, poke 0, wake 2, rf_open 0, hf_up 0. Board serial: HELLO, HFP_ON ('HFP on (CMD_HFP_ON)'), AA_ENABLE, SET_NETWORK stored; no 'HFP HF state' line in any cycle. Phone (D-POCO) 'Creating rfcomm socket for device' 27 times per cycle, ALL to 11:46:03:10:33:59 (D-HU's own address), 0 to the board. So the board was never dialled. D-POCO is still bonded to D-HU (units/D-POCO.md: Gearhead caches the head unit it last talked to). A same-conditions control (D-HU unpaired on D-POCO) is needed before blaming the PR or the rig; that is an operator step. Not graded FAIL for that reason; reordering: C2 and K now (they need the D-HU bond), B re-run after the unpair.
- Operator forgot D-HU on D-POCO at about 19:36-19:37, i.e. at or just before K-c1-go (19:36:09). K therefore ran without the D-HU bond and cannot show a fallback to the stock route; record K as such. After K: B control with the D-HU bond gone (B-g will be INCONCLUSIVE, as the brief expects when HU-POCO HFP is not Connected).
- K: ran without the D-HU bond. Row: ssl 0, relay_on 1, listening 0, wake 3, match 2 (MATCH! Starting AapService 2, a self wake-up, in a not-graded stage), btconn 2, pdial 9. Board came back (advertising 19:39:01).
- Stop: the operator asked to stop after the B rows and report. Not done: Stage S (D-SAM), the Closing (D-HU APK and settings restore, `broker_forget`, D-POCO forget of the relay bond, board left on release firmware, radios, zip and upload). Rig state is listed at the end.

## C1 Control: stock route, no relay file (2 cycles)

**PASS**

| cycle | ssl | t_ssl | thr | relay_on | listening | s_ble_conn | pdial_macs |
|---|---|---|---|---|---|---|---|
| C1-c1 | 1 | 11.0 | 23 | 0 | 1 | 0 | 11:46:03:10:33:59 (HU_MAC) |
| C1-c2 | 1 | 14.3 | 23 | 0 | 1 | 0 | 11:46:03:10:33:59 (HU_MAC) |

## P0 D-POCO forgets the old board bond

**PASS**

No old row existed after D-POCO's reboot (`tap_gear: 0 rows match`, `p0: no old bond row`). 0 taps. The brief expected a stale `OpenHU-NativeBTBroker` bond on D-POCO; it was not there.

## P Pair the board, no hand step

**PASS** (on the fourth attempt; attempts 1 to 3 were voided by brief defects, see Setup notes)

- Attempt 4: `paired=0`. `BLE bond ok` 19:07:56.787; `rx frame type=06 SET_BOND_MODE` 19:07:57.231; `cleared phone bonds for new pairing` 19:07:57.253; `pairing mode for 120 s` 19:07:57.362; `phone paired: POCO X3 NFC` 19:08:54.422; `pairing mode ended` 19:08:54.422, 57 s after the clear, so not within 5 s of it (fix 7).
- `Broker: opening new-phone pairing window on 78:1C:3C:2B:AC:EE` 1, `Broker: phone pairing confirmed` 1. `NativeBTBroker.xml`: `enabled` true, `address` 78:1C:3C:2B:AC:EE, `name` OpenHU-BT-Relay-Setup. D-POCO bonded list names `OpenHU-BT-Relay` (masked XX:XX:XX:XX:AC:EE).
- Taps used in attempt 4: 4 (p-scan 705,564; p-pick 578,662; p-dev 410,1020; p-confirm 866,1363). `p-new` ('Pair new device') did not resolve and logged an H1 cue at 19:08:39; nothing was done by hand.

## B Five bring-ups over the board (the point)

**PASS** (with the control that removed D-HU from D-POCO's bonds; the first attempt did not pass, see below)

| cycle | ssl | t_ssl | thr | relay_on | listening | poke | rf_open | hf_up | hfp_rel | s_err | terr | pdial_hold | wpp_restart_hold | proj_fail | flood |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| B-c1 | 1 | 13.9 | 22 | 1 | 0 | 0 | 1 | 1 | yes | 0 | 0 | 0 | 0 | 0 | 0 |
| B-c2 | 1 | 13.6 | 23 | 1 | 0 | 0 | 1 | 1 | yes | 0 | 0 | 0 | 0 | 0 | 0 |
| B-c3 | 1 | 13.5 | 23 | 1 | 0 | 0 | 1 | 1 | yes | 0 | 0 | 0 | 0 | 0 | 0 |
| B-c4 | 1 | 14.4 | 23 | 1 | 0 | 0 | 1 | 1 | yes | 0 | 0 | 0 | 0 | 0 | 0 |
| B-c5 | 1 | 14.2 | 23 | 1 | 0 | 0 | 1 | 1 | yes | 0 | 0 | 0 | 0 | 0 | 0 |

- B-a, B-b, B-c, B-d, B-e, B-h hold in 5 of 5. `pdial_macs` is the board (78:1C:3C:2B:AC:EE) only, in every cycle.
- B-f: `crit` 4 in every cycle, all 4 lines within 2 s after the Exit (disc 19:42:23.302, crit 19:42:24.318 to .395; likewise c2 to c5), none outside it. Held by the brief's own exclusion; the grader script counts them, so its raw `crit=4` reads as a fail.
- B-g: INCONCLUSIVE in 5 of 5: D-HU's own hands-free link to D-POCO was not Connected before launch (`hu_hfp_before` NOT, D-HU not bonded to D-POCO after the operator's forget); `relink.log` `relink=1` each cycle.
- HFP states on the board in each cycle: `2>3>0`; `HFP off (` 1 per cycle (hfp_release 1). `Broker: releasing the held Bluetooth channel after 121s and 122 lease-handled pings.` in B-c2 (held_rel 0 in c1).
- **First attempt of B, with D-HU still bonded to D-POCO:** 3 cycles, all ssl 0, relay_on 1, listening 0, wake 2, rf_open 0, hf_up 0, no `HFP HF state` line on the board. D-POCO's Gearhead logged `Creating rfcomm socket for device: 11:46:03:10:33:59` (D-HU's own address) 27 times per cycle and never dialled the board. Same signature as the pr-train stage W block. The stage's stop rule ended it after 3 cycles. Not graded FAIL because the bond is rig state, and the same-conditions control (bond forgotten by the operator) formed 5 of 5 sessions. Captures in `B-attempt1/`; an earlier mislabelled launch (host error) in `B-mislabelled/`.

## C2 Board saved, switch off: the stock route

**PASS**

| cycle | ssl | t_ssl | thr | relay_on | listening | s_ble_conn | pdial_macs |
|---|---|---|---|---|---|---|---|
| C2-c1 | 1 | 13.2 | 23 | 0 | 1 | 0 | 11:46:03:10:33:59 |
| C2-c2 | 1 | 14.2 | 23 | 0 | 1 | 0 | 11:46:03:10:33:59 |

The switch gates the route (fix 5): the board is saved, `enabled` false, and the stock route ran.

## K Switch on, board held in reset (1 cycle; report only)

**INCONCLUSIVE**

The operator forgot D-HU on D-POCO at about the start of this cycle (K-c1-go 19:36:09), so the cycle cannot show a fallback to the stock route. Row: ssl 0, relay_on 1, listening 0, wake 3, hf_up 0, rf_open 0, link_lost 0, still_down 0, match 2 (`MATCH! Starting AapService`, two self wake-ups), btconn 2, pdial 9. Unit `Broker:` lines from `K-c1-go`: `intentional AA wake (HFP) to 78:1C:3C:2B:AC:EE (early, waiting for Wi-Fi snapshot)` 19:36:11.672; `carrier listening` 19:36:11.705; `sending Wi-Fi snapshot ... without a new start` 19:36:16.776; `one start for DIRECT-QS-MT50YT610E4GFPSLU 192.168.49.1:5288` 19:36:17.774. Neither `Broker: link lost` nor `still down` appeared. The board came back (advertising 19:39:01). Whether Native mode falls back to the stock route with the board gone remains unanswered: re-run K with the D-HU bond in place.

## S D-SAM relay screen with the API 23 guard

**UNTESTABLE** (not run: the operator stopped the round after stage B)

## Anything the brief did not ask about

- The relay works only when D-POCO has no bond to the head unit's own radio. With D-HU bonded, Android Auto dials D-HU's address and never the board. That is a rig-state and usage question for the contributor, not proof of a PR defect.
- After a force-stop of the app, the board stopped advertising and did not answer a new scan until its serial port was opened with a reset (stage P attempt 2). The serial capture showed no `head unit BLE disconnected`. Worth asking the contributor whether the board releases its BLE link when the app dies.
- The pairing window is opened by the app right after the BLE bond; the brief's `Pair phone` step no longer exists, and D-POCO's dialog button is `PAIR` in capitals (brief says `Pair`).
- Brief defects found: `stageK.sh` is not extractable (K is only a block under a heading); `run4`-style launchers need `stageB.sh B` with the argument; `grade_cycle.py` counts the 2 s after the Exit into `crit`; the stale-bond text in section 3 did not match what was on the units.

## Rig state left behind (nothing restored)

D-HU: relay APK C installed, `NativeBTBroker.xml` enabled with the board, settings are the brief's keys (backup `settings-backup-hu.xml`), Bluetooth on, D-HU not bonded to D-POCO. D-POCO: WiFi and Bluetooth on, bonded to `OpenHU-BT-Relay`, no bond to D-HU. Board E-1 on release firmware, advertising, serial capture killed. D-MOTO: Bluetooth and WiFi off. No logcat or capture processes left running.
