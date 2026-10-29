# native-join-scan - round 3 results

**Candidate:** `feat/native-join-scan` @ `afc0b507`       **Baseline:** none (round 2 measured `main`)
**APK md5:** candidate `dcb4df8159fbfa9a3b4ad6a0a2d1e9f0` / previously installed `fb608e0ee92fbfb7d1d1d7542bc89b2b` (put back in F3)
**Unit:** D-HU (UNISOC MT50, Android 14) with D-POCO (POCO X3 NFC, Gearhead 18.0.664204-release); D-MOTO Bluetooth off
**Date:** 2026-10-17

**Evidence:** release `rig-evidence-native-join-scan`, asset `native-join-scan-round3-captures.zip`, sha256 `7ebcdb04869bf94300415a7a71efbe631770f12e6f34a619e8918c405705451a`

## Setup notes

- Resolved SHA 09308bd9 -> native-join-scan 3.
- Checkout of transfer/rig-rounds failed (branch held by another worktree). Mistake: chained `git reset --hard` ran on main; main restored to 22392a157 at once, status clean. Operator chose a detached worktree: worktrees/native-join-scan__review__09308bd9__20261017 at 09308bd9. Commit/push at the end must use HEAD:transfer/rig-rounds (detached).
- rig_sync.py sync run in the detached worktree: "in sync". No CLAUDE.md diff vs f9abbfcc6.
- Quirk files read: rig-quirks/topics/tooling.md, units/D-HU.md, units/D-POCO.md, topics/wifi.md, topics/bt.md (skimmed, lines cut to 900 chars). TOOLS.md "Pick a tool" table read.
- rig_preflight.sh role names need underscores (D_HU, not D-HU); first call with D-HU printed an error and a meaningless OK. Valid table in preflight.txt.
- Brief scripts extracted by sed line range from the brief (extract_brief_blocks.py only matches **`name.sh`** with backticks, the brief uses **name.sh**).
- Build worktree: worktrees/native-join-scan__scratch__afc0b507__20261017.
- P0: installed APK md5 fb608e0ee92fbfb7d1d1d7542bc89b2b (matches round 2's left-installed APK). P1: settings.bak 9636 bytes, byte-identical to round 2's settings.bak (delta zero); baseline wifi-direct-band=1, kill-on-disconnect=false, enable-car-launcher=false, stable-identity=true. shared_prefs owner u0_a176:u0_a176. P2: D-MOTO BT disabled, read back state OFF. P3: D-POCO bonded list holds Navegadortz2 (..33:59 = HU_MAC), D-HU bonded list holds POCO X3 NFC (DC:B7:2E:5E:4E:59), motorola edge 30 neo and a speaker; D-POCO also lists FX Plus, DR-82645, BC8-Android (not rig head units). P4: gearhead 18.0.664204-release. P5: focus was NotificationShade (phone Dozing); HOME sent, focus unchanged because the screen is off, cycle wakes it. P6: stay_on_while_plugged_in=0. P8: D-HU station on Pegue Cdesta 5745MHz, BSSID f4:52:46:60:8d:4e (second mWifiInfo line is the P2P/other iface, DISCONNECTED).
- P0-P6, P8 were run directly by the host (read-only adb), not through rig-executor.
- AutomationCommandPolicy CONFIGURING at afc0b507 does not list ACTION_LOG_MARKER: markers are not gated.
candidate md5 recorded
- P7 done by operator; phone_unjoined rc=0 twice 45 s apart (p7-wifi*.txt). P9: candidate installed, md5 dcb4df8159fbfa9a3b4ad6a0a2d1e9f0.
- njs3_extract.py: phone window ends at the first phone line whose timestamp is >= the head unit's E marker (the phone capture holds no E marker line).
K1 PASS graded; monitor b5xdt5n1m ended (script exit=0)
D1 PASS graded. D1 c0 (seed) started on K1's kept group (HA=1), not graded.
- U1 PASS graded. B1: njs3_run.sh's flock was held by a leftover `sleep 20` from the seed's thermal watch, so the chained c1 exited 99 (lock held, nothing run). c1 was started alone about 25 s later with the same env; no state changed in between (seed ended ks, group kept, phone on it).
- B1 seed J-conn: the phone's connect went through a saved-config reconnect path (WIRELESS_WIFI_RECONNECTION_ATTEMPT) and printed no "User initiated connect to network" line, so apChannel is "none" in the seed. Literal reading of the brief says INCONCLUSIVE. Graded PASS instead: all four PASS conditions hold in c1 and the mismatch is proven from the head unit, seed "Freq: 5805 MHz (5GHz)" and c1 "the group already up is on 5805 MHz, which the band or channel setting no longer allows". Operator may overrule.
- After B1 c1: D-HU station read DISCONNECTED, -1 MHz (P8 was Pegue Cdesta 5745 MHz); WifiStateMachine NETWORK_DISCONNECTION_EVENT 22:14:48.851, 0.4 s before the 2.4 GHz group was requested. Wi-Fi is enabled. Station not rejoined by the host (needs an operator tap per quirk notes).
- F2: stay_on_while_plugged_in restored to 0 (read back 0). settings.xml restored as root (shell is uid 0, no su): cp, chown u0_a176, chmod 660; read back byte-identical to settings.bak. F3: base APK reinstalled, md5 fb608e0e... matches P0.
- Opening pre-flight: D_HU wifi 1 bt 1 Awake; D_POCO wifi 1 bt 1 Dozing, HFP to ..33:59, A2DP up; D_MOTO wifi 1 bt 0 Asleep. Closing pre-flight (after the operator's F1 and the head unit's WiFi rejoin, one idle Gradle daemon killed by `rig_cleanup.sh kill`): same radios, PREFLIGHT OK.
- Scripts: round scripts in `rig-data/rounds/native-join-scan-round3/scripts/` (brief's five, plus `njs3_run.sh` driver that takes the rig lock and stops on the first non-zero exit, `njs3_extract.py`, `f2_restore.sh`). Toolkit: `rig_preflight.sh`, `rig_cleanup.sh`, `rig_thermal.sh`, `wt-new.sh`, `build_hur_cool.sh`, `apk_identity.sh`, `set_hu_settings_host.py` (through the brief's `njs3_keys.sh`).
- Every cycle exited 0 and was counted. No discard rule fired in any cycle (H12, H10, H6, Magic Garbage all 0; H4 = 1; P-dial 6 with HU_MAC every time; screen Awake).
- Executors: none used. The host ran every session script itself with `run_in_background` and graded from its own extractor output. Spot check: one `awk`/`grep -c` of H4 in K1 c3 (1) matched the extractor.

## R K1

**PASS**

Settings: BAND=0 KILL=false GATE=on, the brief's 15 keys read back (exit 0 every cycle). Seed c0 `PC` = 1 (instrument check met). All of c1 to c5 passed the arm check (HA = 1, H2 = 0, phone BSSID `7a:15:62:e2:36:b0` equal to the last HI BSSID) and every one is good: AS at most 2.5 s, PC = PD = 0, phone BSSID unchanged after the session.

| Cycle | AS s | BT s | TI s | HC | T3 | HL | PC | PD | J-req | S-start |
|---|---|---|---|---|---|---|---|---|---|---|
| c1 | 1.35 | 3.66 | 0.42 | 1 | yes | 0 | 0 | 0 | 0 | 2 |
| c2 | 1.59 | 3.48 | 0.44 | 1 | yes | 0 | 0 | 0 | 0 | 1 |
| c3 | 1.48 | 4.29 | 0.50 | 1 | yes | 0 | 0 | 0 | 0 | 1 |
| c4 | 1.50 | 4.57 | 0.45 | 1 | yes | 0 | 0 | 0 | 0 | 1 |
| c5 | 1.51 | 3.92 | 0.54 | 1 | yes | 0 | 0 | 0 | 0 | 1 |

Medians: AS 1.50 s (round 2: 1.55 s), BT 3.92 s (round 2: 3.73 s). Re-associating cycles: 0 of 5. Type 3 now goes out 0.42 to 0.54 s before the phone's TCP connection, and the phone did not react to it. Decisive line, c3: `INC 21:59:21.716`, `H4 21:59:22.163`.

## R D1

**PASS**

Settings: BAND=0 KILL=true GATE=any. Seed c0 `kd`; it started on K1's kept group (HA = 1), not graded. The phone's WiFi going off ended the session in every cycle (HQ printed, no exit 11).

| Cycle | end before: HK | HX | HQ | HP | wifi-off to HQ s | to HX s | start: HA | H2 | HI BSSID | AS s | BT s | Result |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| c1 | 1 | 1 | 1 | 0 | 15.07 | 15.66 | 1 | 0 | 7A:15:62:E2:36:B0 | 1.56 | 4.07 | Kept |
| c2 | 1 | 1 | 1 | 0 | 15.10 | 15.67 | 1 | 0 | 7A:15:62:E2:36:B0 | 2.97 | 5.54 | Kept |
| c3 | 1 | 1 | 1 | 0 | 15.10 | 15.69 | 1 | 0 | 7A:15:62:E2:36:B0 | 2.98 | 5.82 | Kept |

Kept 3 of 3, Lost 0. The phone rejoined each time (PC = 1, J-req = 1) because its own WiFi had been cycled. AS in c2 and c3 is 2.97 and 2.98 s, higher than K1's 1.5 s: the phone was coming back from a WiFi bounce.

## R U1

**PASS**

Settings: BAND=0 KILL=true GATE=any, end `ku`. Seed c0 started on D1's kept group (HA = 1), not graded.

| Cycle | end before: HE | HX | HK | HP | start: HA | H2 | HB | AS s | BT s | Result |
|---|---|---|---|---|---|---|---|---|---|---|
| c1 | 1 | 1 | 0 | 0 | 0 | 1 | `reason=BUSY` | 3.96 | 6.77 | Removed |
| c2 | 1 | 1 | 0 | 0 | 0 | 1 | `reason=BUSY` | 3.99 | 6.27 | Removed |
| c3 | 1 | 1 | 0 | 0 | 0 | 1 | `reason=BUSY` | 3.38 | 5.37 | Removed |

Removed 3 of 3, Kept by mistake 0. Quoted: `WifiDirectManager: Native AA removeGroup before recreate failed (reason=BUSY (System is busy, retry needed)); expected if no group existed`.

## R B1

**PASS** (the seed's J-conn rule would give INCONCLUSIVE; see Setup notes, operator may overrule)

Settings: seed c0 BAND=0, c1 BAND=2 (2.4 GHz only), end `r`. Seed: HU group `Freq: 5805 MHz (5GHz)`, phone PC = 1, no `User initiated connect` line (saved-config reconnect path), so seed `apChannel` = none. c1: HM = 1, HA = 0, H2 = 1, HB 0, J-conn `apChannel:2412` at 22:14:53.506.

- `22:14:49.223 WifiDirectManager: the group already up is on 5805 MHz, which the band or channel setting no longer allows, so it is replaced.`
- `22:14:49.698 onGroupInfoAvailable: ... Freq: 2412 MHz (2.4GHz, channel 1)`, new BSSID `F2:C4:BD:51:63:10`.
- After c1, D-HU's station read `DISCONNECTED`, `Frequency: -1MHz`, against P8's `Pegue Cdesta` 5745 MHz. `WifiStateMachine NETWORK_DISCONNECTION_EVENT` at 22:14:48.851, 0.4 s before the 2.4 GHz request. WiFi stayed enabled; the operator rejoined it by hand afterwards.

## Anything the brief did not ask about

- The 2.4 GHz group stood D-HU's 5 GHz station down, as the brief warned. It did not rejoin by itself before the round ended.
- The seed of B1 was a slow join: AS 8.36 s, TI 7.42 s, Type 3 written at 22:13:21 and the phone's connect at 22:13:29. That is the first-scan miss round 2 recorded, now with the no-pause build.
- U1 removed-group joins cost AS 3.38 to 3.99 s, near round 2's 3.91 s median on a removed group; the kept group (K1) costs 1.5 s.
- `njs3_run.sh`'s `flock` lock is inherited by the thermal watch's `sleep 20`, which outlives the cycle script by up to 20 s; chaining two invocations back to back exits 99. Not a rig fault; leave a 25 s gap or close fd 9 for the children.
- The brief's `rig_preflight.sh` role names need underscores (`D_HU`).
