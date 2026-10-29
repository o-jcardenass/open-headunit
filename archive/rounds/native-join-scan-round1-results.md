# native-join-scan - round 1 results

**Candidate:** none (no code change)       **Baseline:** origin/main @ 22392a15
**APK md5:** fb608e0ee92fbfb7d1d1d7542bc89b2b (4.0.0-alpha debug, reused from the device, MATCH)
**Unit:** D-HU (UNISOC MT50, Android 14) head unit; D-POCO (POCO X3 NFC, Android 15) phone, Android Auto 18.0.664204-release; D-MOTO Bluetooth off
**Date:** 2026-10-17
**Evidence:** release `rig-evidence-native-join-scan` on o-jcardenass/open-headunit, asset `native-join-scan-round1-captures.zip`, sha256 `31c5b14d0cd9c715eb2379ecc80984b68bac7c8edc85f14706beab3a933e6ee3`

## Setup notes

- Branch: transfer/rig-rounds is checked out in worktree rig-sync__scratch__8228cb07__20261015 (clean, at fork tip c053b52c); the main checkout stayed on `main`. Read the brief and wrote results from that worktree instead of switching the main checkout. rig_sync.py: in sync, no RESTART, CLAUDE.md unchanged.
- Quirk files read: topics/tooling.md, units/D-HU.md, units/D-POCO.md, units/D-MOTO.md, topics/lifecycle.md (head).
- Opening pre-flight (2026-10-17): ROLE ADB WIFI BT SCREEN BT-PROFILES / D_HU ok 1 1 Awake HFP:none A2DP:none / D_POCO ok 1 1 Awake HFP:XX:33:59 A2DP:up / D_MOTO ok 1 0 Awake HFP:none A2DP:none. PREFLIGHT OK, no strays, lock free.
- APK: installed base.apk md5 fb608e0ee92fbfb7d1d1d7542bc89b2b, versionName 4.0.0-alpha, lastUpdateTime 2026-10-17 17:31:15 = MATCH, no build.
- Scripts: brief's njs_*.sh and hfp_state.sh extracted verbatim with a one-off extractor (toolkit extract_brief_blocks.py needs backticked names the brief lacks) into $EV/scripts. They rely on $RIG_TOOLKIT, so rig_devices.sh is sourced before njs_env.sh.
- P1: settings.bak 9636 bytes md5 c1f1dbe9264386493587ca946b217a6a. P1a: shared_prefs owner u0_a176:u0_a176 (app uid, ok). P2: D-MOTO BT state OFF. P4: Gearhead versionName 18.0.664204-release. P5: focus was NotificationShade, sent KEYCODE_HOME. P6: stay_on_while_plugged_in = 0.
- P3: D-HU bonded list holds POCO DC:B7:2E:5E:4E:59; D-POCO list holds Navegadortz2 (XX:33:59 = HU_MAC 11:46:03:10:33:59) AND Navegadortz3 (XX:5E:1D, D-SAM radio). Brief says stop and ask the operator to forget it on D-POCO (same as late-listener-poke round 1).
- P8: D-HU station joined Pegue Cdesta 5745 MHz, COMPLETED.
- P3 resolved: operator forgot Navegadortz3 on D-POCO; list re-read, only Navegadortz2 of the head units remains. P7: after the hand step, phone_unjoined returned 1 twice (90 s and 135 s): D-POCO still joined to Pegue Cdesta (f4:52:46:60:8d:4e, 192.168.1.4, COMPLETED). Round stopped per brief P7. Files p7-wifi.txt, p7-wifi2.txt.
- P7 redone after operator disconnected D-POCO by hand: phone_unjoined 0 twice 45 s apart (DISCONNECTED, unknown ssid). Auto-connect stays off.
- R1 c1 try 1: exit 7 (phone still on our DIRECT- group with BT off, 52 s after c0 end). Per brief not a fault; rerun once.
- R1 c1 try 2: exit 7 again (PHONE_ON_GROUP). Brief stop rule: two exit 7 in a row in a K run => R1 INCONCLUSIVE, go on to R2. Captures R1-c0, R1-c1 (try 2) and R1-c1-try1-* kept.
- R2 c0 try 1: exit 7 (PHONE_ON_GROUP): R1 left the kept group up with the phone joined, and the seed cycle's gate fires before any exit. Deviation: before the R2 seed, ran njs_keys.sh, launched MainActivity, sent headunit://exit to the running app, force-stopped; wifip2p mGroup null and phone_unjoined=0 afterwards (pre-r2-*.txt). Then R2 c0..c5 from scratch. Not a cycle change, a state reset between runs.
- Extraction: scripts/njs_extract.py (host-written, greps the brief's fixed strings, times from logcat -v time on one device clock each). R2 c0-c5 counted: JS 2.69,2.70,2.61,2.72,2.66,2.57 s; BT 7.50-7.89 s; AGE c1-c5 30.2-30.5 s.
- R3 c0 try 1: exit 4 NO_SCREEN. D-POCO reports mWakefulness=Dozing (not Asleep) with the panel off, so phone_screen off never agreed. Fix in scripts/njs_env.sh: off accepts Asleep or Dozing. Grading of screen files for R3/R4 treats Dozing or Asleep as off. No state change from the failed try (stay_on_while_plugged_in still 0).
- R3 c1 try 1: exit 7 (PHONE_ON_GROUP), rerun once.
- R3 c1 try 2: exit 7 again. R3 INCONCLUSIVE per stop rule. Same pre-R4 state reset as pre-R2 (keys, launch, headunit://exit, force-stop) before the R4 seed.
- F2: stay_on_while_plugged_in set to 0 (P6 value). D-HU settings.xml restored as root (adb shell is uid 0; cp from /data/local/tmp, chown u0_a176, chmod 660): md5 c1f1dbe9264386493587ca946b217a6a before and after, byte-identical.
- F1: operator turned Auto-connect back on; the phone rejoined `Pegue Cdesta` (phone_unjoined 1, f4:52:46:60:8d:4e, 192.168.1.4).
- Closing pre-flight: D_HU wifi 1 bt 1, D_POCO wifi 1 bt 1, D_MOTO wifi 1 bt 0, PREFLIGHT OK, identical to the opening table. No build worktree was created.

## R1 - kept group, phone screen on (the point of the round)

**INCONCLUSIVE**

- Settings written: the brief's 13 keys by `njs_keys.sh` every cycle (read back, exit 0).
- c0 (seed) ran: HA 0, H2 1, J-req 19:19:19.469, J-conn 19:19:25.397 (JS 5.9 s). It ended with `am force-stop`, group left up.
- c1 exited 7 twice (`PHONE_ON_GROUP`, 52 s and 61 s after c0 ended): `mWifiInfo SSID: "DIRECT-QS-MT50YT610E4GFPSLU", BSSID: 62:ae:7d:9f:b0:e6 ... IP: /192.168.49.136 ... Supplicant state: COMPLETED` with the phone's Bluetooth off. Brief stop rule: two exit 7 in a row in a K run = INCONCLUSIVE.
- Counted cycles: 0. Skips: not measured.

With the phone's Bluetooth off the phone does not leave a kept group, so it never has to find it again. The K arm as scripted cannot reach the launch gate.

## R2 - removed group, phone screen on (R1's control)

**PASS** (control valid: arm R in 5 of 5, 0 skips)

- Arm R in c1-c5: HA 0, H2 1, HB `reason=BUSY`, J-req BSSID differs every cycle. Screens Awake before and after. P-dial 6 each, MAC 11:46:03:10:33:59 only. H9 3, H6 0, H12 0, one H4, H10 0, no Magic Garbage. Re-runs: c0 once (exit 7, see Setup notes).

| cycle | JS s | AGE s | BT s | S-start | P-cache |
|---|---|---|---|---|---|
| c1 | 2.70 | 30.2 | 7.59 | 1 | 1 |
| c2 | 2.61 | 30.5 | 7.89 | 1 | 1 |
| c3 | 2.72 | 30.3 | 7.58 | 1 | 1 |
| c4 | 2.66 | 30.4 | 7.61 | 1 | 1 |
| c5 | 2.57 | 30.4 | 7.60 | 1 | 1 |

- Median JS 2.66 s, median BT 7.60 s. Skips (JS <= 1.5 s): 0 of 5. c0 (seed): JS 2.69 s, AGE 4.3 s.
- Quoted: c1 phone `19:26:01.121 WifiNetworkFactory ... got request` (BSSID 66:c5:5c:9c:c6:e0, HU `stable=no`), `19:26:03.817 User initiated connect to network: "DIRECT-` (JS 2.70 s).

Pair R1 with R2: INCONCLUSIVE, because R1 is.

## R3 - kept group, phone screen off

**INCONCLUSIVE**

- First try exit 4 `NO_SCREEN`: D-POCO reports `mWakefulness=Dozing`, not `Asleep` (script fixed, Setup notes). c0 then ran. c1 exited 7 twice (`PHONE_ON_GROUP`, BSSID 2a:fa:3f:ad:2f:37 joined).
- Counted cycles: 0.

## R4 - removed group, phone screen off

**INCONCLUSIVE**

All six cycles ran as arm R (HA 0, H2 1, BSSID new each cycle, one H4, H9 3, H6 0, H12 0, P-dial 6 with HU_MAC only). The screen was `Dozing` before the launch in every cycle and `Awake` after SSL in every cycle, so under the brief's counted-cycle rule none counts. The numbers are given as observation only.

| cycle | JS s | AGE s | BT s | S-start | P-cache |
|---|---|---|---|---|---|
| c1 | 2.66 | none | 7.19 | 1 | 1 |
| c2 | 2.59 | none | 7.68 | 1 | 1 |
| c3 | 2.67 | none | 7.95 | 1 | 1 |
| c4 | 2.66 | none | 7.46 | 1 | 1 |
| c5 | 2.63 | none | 8.24 | 1 | 1 |

Median JS 2.66 s, median BT 7.68 s. With the screen off the phone logged no completed scan before the request in any cycle (AGE none), and the join took the same 2.6 s as with the screen on.

Pair R3 with R4: INCONCLUSIVE, because both runs are.

## Report back

1. R1 against R2: not answerable. The kept-group arm was never reached, because the phone stays joined to the kept group with its Bluetooth off (exit 7).
2. R3 against R4: same.
3. JS and BT medians: R2 2.66 s and 7.60 s; R4 (observation only) 2.66 s and 7.68 s. No cycle skipped the scan. AGE of skipping cycles: none.

## Anything the brief did not ask about

- Join scan on this build in the removed arm was 2.57 to 2.72 s, not the 5.2 to 6.0 s `late-listener-poke` measured, in both screen states. R1's seed c0 (first cycle of the round, same arm) was 5.9 s. A single scan started by the request (S-start 1) each time.
- R2 AGE sits at 30.2 to 30.5 s, just over the 30 s cache limit, which fits a phone background scan every ~30 s with the screen on. R4 shows no background scan at all.
- A design that keeps the group across a stop must also handle the phone staying associated to it. A measurement of the kept-group arm needs the phone to drop the group first (for example its WiFi toggled off and on, which this brief forbids), or a design where the group is kept without the phone.
- Between runs the kept group had to be cleared by hand (launch, `headunit://exit`, force-stop) so the next seed cycle could start.
