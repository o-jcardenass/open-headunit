# native-join-scan - round 2 results

**Candidate:** feat/native-join-scan @ ebc9c0f5       **Baseline:** origin/main @ 22392a15
**APK md5:** c697331f12b1835db08d389921bfc5a9 (candidate, built this round) / fb608e0ee92fbfb7d1d1d7542bc89b2b (baseline, saved from D-HU, MATCH with the brief)
**Unit:** D-HU (UNISOC MT50, Android 14) head unit; D-POCO (POCO X3 NFC, Android 15) phone, Android Auto 18.0.664204-release; D-MOTO Bluetooth off
**Date:** 2026-10-17
**Evidence:** release `rig-evidence-native-join-scan` on o-jcardenass/open-headunit, asset `native-join-scan-round2-captures.zip`, sha256 `8440d971f266c71dbdbc94238c268cb79411d035c326f9f787a4584323975ca3`

## Setup notes

- Branch: transfer/rig-rounds is held by worktree rig-sync__scratch__8228cb07__20261015; fast-forwarded it to a858bc30 (clean before). Main checkout stayed on main. rig_sync.py: in sync, no RESTART.
- Quirk files read: topics/tooling.md, units/D-HU.md, units/D-POCO.md, units/D-MOTO.md.
- Opening pre-flight 2026-10-17: D_HU ok wifi 1 bt 1 Awake HFP:none; D_POCO ok wifi 1 bt 1 Dozing HFP:XX:33:59 A2DP:up; D_MOTO MISSING from adb. PREFLIGHT FAILED. Round stopped before any device action.
- Pre-flight re-run after D-MOTO replugged: D_HU wifi 1 bt 1 Awake; D_POCO wifi 1 bt 1 Awake HFP:XX:33:59 A2DP:up; D_MOTO wifi 1 bt 0 Awake. PREFLIGHT OK.
- Scripts: njs_env/hfp_state/njs_keys/njs_bonded copied from round 1 (EV renamed to round 2; phone_screen off accepts Dozing), njs2_cycle.sh extracted verbatim from the brief. All in $EV/scripts.
- Build worktree native-join-scan__scratch__ebc9c0f5__20261017 at ebc9c0f54 (parent chain contains 22392a15). Build started in background via build_hur_cool.sh, log build-cand.log.
- P0: base-22392a15.apk md5 fb608e0ee92fbfb7d1d1d7542bc89b2b (MATCH with the brief). P1: settings.bak 9636 B md5 c1f1dbe9264386493587ca946b217a6a, identical to round 1's backup, so delta = 0. P2: D-MOTO BT state OFF. P3: D-HU list = Magnetic Speaker, motorola edge 30 neo, POCO X3 NFC; D-POCO list holds only Navegadortz2 (XX:33:59 = HU_MAC) among head units, no stale radio. P4: Gearhead 18.0.664204-release. P5: D-POCO focus was com.android.settings WifiSettingsActivity; sent KEYCODE_HOME. P6: stay_on_while_plugged_in = 0. P8: D-HU station joined Pegue Cdesta 5745 MHz COMPLETED.
- Markers: ACTION_LOG_MARKER not in CONFIGURING at ebc9c0f5, so no allow-external-configuration write.
- P7: phone_unjoined 0 twice 45 s apart (DISCONNECTED). P9: candidate ebc9c0f5 md5 c697331f12b1835db08d389921bfc5a9, copied to apk/cand-ebc9c0f5.apk. Not installed.
- R1 done 20:38:05: seed c0 + c1-c5 all exit 0, arm KS met in 5 of 5, 5 skips. Every KS cycle had HL=1 (phone session landed before Type 3, so T3 absent, TD/JH n/a). Provisional PASS pending R2 control. Extractor: scripts/njs2_extract.py. Runner: scripts/njs2_run.sh (holds /tmp/ohu-rig.lock).
- R2 done 20:44:53: c0-c5 exit 0. Seed c0 adopted the kept group (HA=1, HL=1, skip) then removed it; c1-c5 arm R met 5 of 5, 0 skips. c2 and c5 had J-req=2 (second request 7.0 s after the first; grep-verified), JS 9.21 and 9.69 s, JH 9.6 and 10.2 s, still one H3 and one H4. By the brief's failed-join definition (J-req > 1) the Delay verdict is INCONCLUSIVE (an R2 cycle has a failed join); R3 is run anyway for the numbers.
- Candidate installed 20:45:22, md5 c697331f12b1835db08d389921bfc5a9, apk_identity MATCH.
- R3 done 20:51:50: c0-c5 exit 0, arm R 5 of 5, HD=0 in all (correct for the candidate), HC=1. TD 0.005-0.012 s. R3 c2 had J-req=2 (JS 9.59 s, JH 10.1 s), same phone-side double request seen in R2 c2/c5.
- Runner: scripts/njs2_run.sh (new, one-round) loops njs2_cycle.sh over c0..c5, holds /tmp/ohu-rig.lock and stops at the first non-zero exit. Extractor: scripts/njs2_extract.py (new, one-round) greps the brief's section 6 strings; the host cross-checked one count per run by hand (R1 c3 HL = 1, R2 c2 and c5 J-req = 2).
- Quirk files read: topics/tooling.md, units/D-HU.md, units/D-POCO.md, units/D-MOTO.md. Toolkit tools used: rig_devices.sh, rig_preflight.sh, rig_thermal.sh, wt-new.sh, build_hur_cool.sh, apk_identity.sh, set_hu_settings_host.py (inside njs_keys.sh).
- Reruns: none needed. Every cycle of every run exited 0 on its first try, no discard rule fired (H12 0, H4 1, H10 0, no Magic Garbage in any cycle).
- The brief's "failed join" definition counts J-req > 1. Three graded cycles meet it purely on that clause (R2 c2, R2 c5, R3 c2). In each the phone's second request came about 7 s after the first, H3 and H4 stayed at 1 and INC printed. This is what decides the Delay verdict below.
- F3: baseline reinstalled 20:52:11, apk_identity MATCH fb608e0ee92fbfb7d1d1d7542bc89b2b. F2: stay_on_while_plugged_in 0 (P6 value); D-HU settings.xml restored as root, md5 c1f1dbe9264386493587ca946b217a6a before and after. F1: operator turned Auto-connect back on; phone_unjoined 1 (Pegue Cdesta, f4:52:46:60:8d:4e, 192.168.1.4). The first check right after the approve read 0 because the hand step was not yet done.
- Closing: rig_cleanup killed one idle Gradle daemon (the only stray), then pre-flight: D_HU wifi 1 bt 1 Awake; D_POCO wifi 1 bt 1 Awake HFP:XX:33:59 A2DP:up; D_MOTO wifi 1 bt 0. PREFLIGHT OK, same radio states as the opening table. Build worktree removed. The evidence zip also holds the two APKs and settings.bak.

## R1 - kept group, phone stays on it, baseline (the point of the round)

**PASS**

- Settings written: the brief's 13 keys by njs_keys.sh at the start of every cycle, read back, exit 0.
- Radio state: phone Bluetooth off at each cycle start and on after, screen Awake in every cycle (c0-c5). D-MOTO BT off.
- Discard-rule check: clean, no reruns. Arm KS met in c1-c5 (HA 1, H2 0, phone BSSID 6a:85:8d:e4:a3:b7 = the last HI BSSID). K-misses 0, exit 10 never.
- Counted cycles 5 of 5. Skips 5 of 5 (S-start 0, J-req 0 in each).
- Quoted, c1: `20:34:11.607 NativeAA: [RX] Received Type 2`, `20:34:12.091 WirelessServer: Incoming connection detected from /192.168.49.136`, `20:34:12.520 SSL handshake complete`, `20:34:12.612 NativeAA: the phone's session landed while Type 3 was pending, so no credentials are sent.`

| cycle | TD | JH | AS s | BT s | JS | S-start | J-req | HL | BSSID |
|---|---|---|---|---|---|---|---|---|---|
| c0 seed | 1.015 | 3.239 | 5.370 | 8.136 | 2.677 | 1 | 1 | 0 | 6a:85:8d:e4:a3:b7 (new group) |
| c1 | n/a | n/a | 1.486 | 5.375 | n/a | 0 | 0 | 1 | 6a:85:8d:e4:a3:b7 |
| c2 | n/a | n/a | 1.468 | 4.467 | n/a | 0 | 0 | 1 | same |
| c3 | n/a | n/a | 1.548 | 3.734 | n/a | 0 | 0 | 1 | same |
| c4 | n/a | n/a | 1.600 | 3.254 | n/a | 0 | 0 | 1 | same |
| c5 | n/a | n/a | 1.576 | 3.703 | n/a | 0 | 0 | 1 | same |

Median AS 1.548 s, median BT 3.734 s. JH is n/a in all five because no Type 3 was written (HL 1 in each): the phone was already on the group and its TCP connection landed 0.48 s after its Type 2, before the head unit wrote credentials. HC printed once per cycle, T3 (`Wrote TYPE 3`) never did.

## R2 - removed group, baseline (control)

**PASS** (control valid: arm R 5 of 5, skips 0 of 5)

- Arm R in c1-c5: HA 0, H2 1, HB `reason=BUSY`, HI BSSID differs from the previous cycle. Screens Awake. P-dial 6, MAC 11:46:03:10:33:59 only. H9 3, H6 0, H12 0, H4 1, H10 0. HD = HC = 1 (baseline APK).
- The seed c0 started on R1's kept group: it adopted it (HA 1, HL 1) and removed it at the end, as the brief says. Not graded.

| cycle | TD | JH | AS s | BT s | JS | S-start | J-req | HL | BSSID |
|---|---|---|---|---|---|---|---|---|---|
| c1 | 1.007 | 3.110 | 5.018 | 7.745 | 2.658 | 1 | 1 | 0 | 0e:8e:be:46:4f:0b |
| c2 | 1.008 | 9.616 | 11.406 | 14.653 | 9.214 | 2 | 2 | 0 | 1e:4c:22:e7:3f:33 |
| c3 | 1.014 | 3.080 | 5.062 | 7.626 | 2.658 | 1 | 1 | 0 | da:35:3b:e6:e3:2a |
| c4 | 1.009 | 3.201 | 5.176 | 7.828 | 2.747 | 1 | 1 | 0 | ea:e4:c4:87:2c:ec |
| c5 | 1.011 | 10.198 | 12.151 | 14.362 | 9.690 | 2 | 2 | 0 | 5a:4a:e3:48:c8:1f |

Medians: TD 1.009 s, JH 3.201 s, AS 5.176 s, BT 7.828 s. Failed joins by the brief definition: 2 (c2, c5, J-req 2). Quoted, c2 phone: `20:41:15.088 WifiNetworkFactory got request` (first J-req), `20:41:22.105 ... got request` (second), `20:41:24.302 User initiated connect to network: "DIRECT-`.

## R3 - removed group, candidate (the 1 s delay)

**INCONCLUSIVE**

- Candidate ebc9c0f5 installed 20:45:22, apk_identity MATCH c697331f12b1835db08d389921bfc5a9. HD 0 in every cycle, HC 1 (correct for the candidate).
- Arm R in c1-c5, same checks as R2, clean, no reruns.
- Quoted, c1: `20:47:19.371 NativeAA: [RX] Received Type 2`, `20:47:19.377 NativeAA: [TX] Wrote TYPE 3 (size 66)`, `20:47:22.342 WirelessServer: Incoming connection detected`.

| cycle | TD | JH | AS s | BT s | JS | S-start | J-req | HL | BSSID |
|---|---|---|---|---|---|---|---|---|---|
| c1 | 0.006 | 2.965 | 3.899 | 6.640 | 2.558 | 1 | 1 | 0 | ba:c0:d5:fc:56:0e |
| c2 | 0.005 | 10.106 | 10.996 | 13.722 | 9.592 | 2 | 2 | 0 | e6:17:9c:b1:03:47 |
| c3 | 0.008 | 2.952 | 3.905 | 6.661 | 2.555 | 1 | 1 | 0 | 2e:94:d8:41:9b:e0 |
| c4 | 0.007 | 3.024 | 3.909 | 6.129 | 2.644 | 1 | 1 | 0 | e6:ae:fa:b6:04:a5 |
| c5 | 0.012 | 3.009 | 3.933 | 6.515 | 2.628 | 1 | 1 | 0 | 4a:52:29:3b:7a:7d |

Medians: TD 0.007 s, JH 3.009 s, AS 3.909 s, BT 6.640 s. Failed joins: 1 (c2, J-req 2).

By the brief's rules: R3 median TD 0.007 s (<= 0.1) and median AS 3.909 s (<= R2's 5.176 - 0.7 = 4.476) meet the PASS numbers, but PASS needs no failed join in R3 and R3 c2 has one; the FAIL rules do not apply (R2 also has failed joins; TD is far below 0.5 s); and R2 has failed joins, which the INCONCLUSIVE rule names. So the verdict is INCONCLUSIVE. The medians themselves show the 1.0 s delay gone and AS shorter by 1.27 s.

## Verdicts

**KS (R1 against R2): PASS.** R1 skips the join in 5 of 5 counted cycles, R2 in 0 of 5. Median JH: R1 n/a (no Type 3 written), R2 3.201 s. Median BT: R1 3.734 s, R2 7.828 s.

**Delay (R3 against R2): INCONCLUSIVE** (see R3). Median TD 0.007 s against 1.009 s; median AS 3.909 s against 5.176 s; failed joins R3 1, R2 2.

## Report back

1. KS: 5 of 5 skips against 0 of 5; JH medians R1 n/a, R2 3.201 s; BT medians 3.734 s and 7.828 s.
2. Delay: TD 0.007 s against 1.009 s, AS 3.909 s against 5.176 s, failed joins 1 against 2.
3. HL: R1 c1-c5 each HL = 1 (and R2 seed c0). S-start and J-req in every KS cycle: 0 and 0, as expected on a real skip.

## Anything the brief did not ask about

- **The phone sometimes asks for the group twice, 7.0 s apart, and the join then takes 9 to 10 s instead of 2.6 s.** It happened in 3 of 10 removed-group cycles (R2 c2, R2 c5, R3 c2), on both builds. Round 1 had 16 of 17 cycles at 2.6 s with one J-req, so this is a new tail, not a build effect. Cause not examined; the second J-req line is a plain `WifiNetworkFactory got request` with no second `User initiated connect` before the first.
- **In every KS cycle the phone's TCP connection arrives 0.48 s after its Type 2, so the head unit never sends credentials** (HL 1). The 1 s Type 3 delay is therefore irrelevant to a kept-group reconnect on the baseline: the phone had already connected before the delay would have elapsed.
- **Bring-up time on a kept group falls over the five cycles** (BT 5.375, 4.467, 3.734, 3.254, 3.703 s) while AS stays flat at 1.47 to 1.60 s. The slow part is the first cycles' time from listener to accept; not investigated.
- D-POCO's focus was left on the WiFi settings page when the round started (P5), and the phone read as joined to nothing before the hand step.
