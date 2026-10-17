# late-listener-poke - round 1 results

**Candidate:** origin/main @ 22392a157 (one build, no candidate branch)       **Baseline:** none
**APK md5:** fb608e0ee92fbfb7d1d1d7542bc89b2b (4.0.0-alpha debug, built from a detached worktree at 22392a157)
**Unit:** D-HU (UNISOC MT50, Android 14, Bluetooth name Navegadortz2, MAC 11:46:03:10:33:59) as head unit; D-POCO (POCO X3 NFC) as phone, Android Auto 18.0.664204-release; D-MOTO plugged in, Bluetooth off (dumpsys state OFF)
**Date:** 2026-10-17

## Setup notes

- Transfer branch was already checked out in a clean scratch worktree, so it was fast-forwarded there and the main checkout stayed on main. `rig_sync.py` reported in sync, no restart.
- Quirk files read: `topics/tooling.md`, `topics/bt.md` and `topics/lifecycle.md` (entry headlines plus the entries on head-unit-first order, Bluetooth self-revert, poke targets), `units/D-HU.md`, `units/D-POCO.md`, `units/D-MOTO.md` (headlines and the bonded-list entries).
- Opening pre-flight, first attempt: FAILED, D-MOTO absent from `adb devices`. The operator replugged it. Second pre-flight: D_HU wifi 1, bt 1, HFP none; D_POCO wifi 1, bt 1, HFP ..:33:59, A2DP up; D_MOTO wifi 1, bt 0. `rig_preflight.sh` takes underscore role names (`D_HU`), not `D-HU`.
- P3: D-POCO's bonded list also held `Navegadortz3` (another rig head unit's radio). The operator forgot it before the build was installed; the list was re-read and no longer held it. Remaining entries: motorola edge 30 neo, Magnetic Speaker, FX Plus, DR-82645, BC8-Android, Navegadortz2.
- `POCO` = DC:B7:2E:5E:4E:59 (from D-HU's own bonded list), `HU_MAC` = 11:46:03:10:33:59 (`settings get secure bluetooth_address` on D-HU).
- **Brief errata 1, `hfp_state.sh`.** The brief's awk started at the `HeadsetClientService` entry in the "Enabled Profile Services" list and stopped at the next line, so it could never read a state. The real section is `Profile: HeadsetClientService`, with the line `mCurrentDevice: XX:XX:XX:XX:4E:59(POCO X3 NFC) name=HeadsetClientStateMachine state=Connected`; dumpsys masks the MAC. The host rewrote the script to read from `^Profile: HeadsetClientService` and match POCO's last two octets plus `state=Connected`.
- **Brief errata 2, arm drift.** After a session the app writes `native-poke-bt-macs` = POCO by itself. The brief writes the arm keys once per arm, so R1 cycles 2 and 3 of the first attempt ran with the poke on (H5 = 1, H7 = 1, H9 = 0) and were void (captures kept in `void/`). The host added a rewrite of the arm keys (`llp_arm.sh`) before every cycle and re-ran R1 cycles 2 and 3. R1 cycle 1 (H9 = 3) is from the first attempt. Every later cycle (R2, R3, R4) had the rewrite.
- Settings delta against the backup (arm A): `native-driver-selection-mode` 1 to 0, `native-preferred-device-mac` empty to POCO, `native-poke-all-paired` true to false, `native-poke-bt-macs` emptied. Already matching the brief: wifi-connection-mode 3, native-ap-transport 0, log-level 2, last-connected-native-mac POCO, wake-damage verdict 0, armings 0, onboarding 2, auto-start-bt-macs empty. `wifi-direct-stable-identity` left as found: true. `connection-modes` = {wifi, self}. `allow-external-configuration` was already true.
- D-HU has no `su`; `set_hu_settings_host.py` worked and the baseline was read with `run-as`.
- The build was done by the host (`build_hur_cool.sh`, thermal gated). Install and identity: `apk_identity.sh` MATCH. The `ACTION_QUERY_STATE` commit check was not sent (it would start the app); identity rests on the md5 and versionName.
- The host ran the install, settings writes and cycle scripts itself rather than through `rig-executor` (5 min scripted cycles under a background runner and a monitor). Scripts used from the toolkit: `rig_devices.sh`, `rig_preflight.sh`, `rig_thermal.sh`, `build_hur_cool.sh`, `wt-new.sh`, `apk_identity.sh`, `set_hu_settings_host.py`. Round scripts in `rig-data/rounds/late-listener-poke-round1/scripts/`: `llp_env.sh`, `hfp_state.sh` (corrected), `llp_cycle.sh` (as briefed), `llp_arm.sh`, `llp_run.sh`, `llp_extract.py`, `llp_bonded.sh`.
- All counts are windowed per cycle (logcat cleared on both devices before each capture; every count was taken from that cycle's own file). Thermal: pre-run gate passed each cycle; thermal samples max 71C, throttle_pkg 0.
- Discard rule: no cycle had an H12, a second H2, a second H4 or a foreign MAC in a P-dial (badmac 0 in every cycle). No cycle took the TCP route (H10 = 0).
- Gearhead (P4) `versionName=18.0.664204-release`. P-fail printed, so grading rested on P-dial and P-fail both.

## R1 - late listener, poke off (the point of the round)

**PASS**

- Settings written: arm A (see Setup notes), read back before every counted cycle.
- Discard-rule check: clean. Two void cycles (arm drift) re-run once each.
- Decisive lines, cycle 1: `17:32:51.288 NativeAA: ACTIVELY LISTENING on Android Auto UUID`, `17:32:53.844 NativeAA: Connection accepted from POCO X3 NFC (DC:B7:2E:5E:4E:59)`, `17:33:02.290 SSL handshake complete`, `17:32:51.386 NativeAA: No wake poke phone selected, so nothing is poked.`
- Measurements:

| Cycle | P-dial before launch | G (s) | H9 | H5 / H6 | D1 (s) | D2 (s) |
|---|---|---|---|---|---|---|
| 1 | 6 | 5.23 to 5.54 | 3 | 0 / 0 | 2.556 | 11.002 |
| 2 | 6 | 5.27 to 5.48 | 3 | 0 / 0 | 2.479 | 10.800 |
| 3 | 6 | 5.29 to 5.39 | 3 | 0 / 0 | 2.229 | 10.753 |

The phone redialled every 5.2 to 5.7 s while HFP was up and nothing listened, and the first dial after the launch was answered. P-fail printed 7 times per cycle (`Rfcomm socket connection failed, restarting WPP using HU presence tracker.`), matching the brief's description of Android Auto 18.0.

## R2 - late listener, poke on

**PASS**

- Settings written: arm B (`native-poke-bt-macs` = POCO, `native-poke-all-paired` false).
- Discard-rule check: clean.
- Decisive line (cycle 1): `17:39:08.481 NativeAA: Not poking POCO X3 NFC (DC:B7:2E:5E:4E:59) - this head unit already holds a Bluetooth hands-free link to it ... Waiting for the phone to start Android Auto itself.`, then `17:39:10.398 Connection accepted from POCO X3 NFC`.
- Measurements:

| Cycle | P-dial before launch | H7 | H6 before first H3 | D1 (s) | D2 (s) |
|---|---|---|---|---|---|
| 1 | 6 | 1 | 0 | 3.286 | 11.762 |
| 2 | 6 | 1 | 0 | 1.462 | 9.639 |
| 3 | 6 | 1 | 0 | 2.062 | 10.142 |

H8 (the 90 s escalation) did not print in any cycle. H5 (`Attempting active poke`) printed once per cycle, followed at once by the stand-down H7; no `socket.connect()` was made.

## R3 - long late gap, poke off

**PASS**

- Settings written: arm A, rewritten before each cycle.
- Discard-rule check: clean.
- Decisive lines, cycle 1: `17:44:38.450 ACTIVELY LISTENING`, `17:44:38.903 Connection accepted from POCO X3 NFC`, `17:44:47.685 SSL handshake complete`.
- Measurements:

| Cycle | Hold (s) | P-dial hfp-up to launch | P-dial in last 60 s | Last P-dial before launch | D1 (s) | D2 (s) | H5 / H6 |
|---|---|---|---|---|---|---|---|
| 1 | 150 | 28 | 11 | 2.3 s (17:44:32.854 vs launch mark 17:44:35.152, phone clock) | 0.453 | 9.235 | 0 / 0 |
| 2 | 150 | 28 | 11 | 1.1 s (17:48:19.663 vs 17:48:20.739) | 2.438 | 10.599 | 0 / 0 |

Cycle 2's HFP link took 46 s to reach Connected after the phone's Bluetooth came on (gate is 60 s); it counted.

## R4 - control, listener first, poke off

**PASS**

- Settings written: arm A, rewritten before each cycle.
- Discard-rule check: clean.
- Measurements:

| Cycle | D3 (s) | H4 minus phone-on (s) | D1 (s) | launch to H4 (s) |
|---|---|---|---|---|
| 1 | 3.983 | 19.528 | 20.600 | 38.7 |
| 2 | 4.251 | 7.173 | 20.776 | 26.4 |
| 3 | 4.138 | 7.209 | 20.800 | 26.4 |

D1 here is not a delay: the script waits about 15 s after the group is up before it switches the phone on, so H3 minus H1 is that wait plus D3.

## R5 - escalation

**not run** (R3 PASS)

## Closing pre-flight

After restoring `settings.xml` (byte-identical to the backup) and killing an idle Gradle daemon: D_HU wifi 1 bt 1, HFP none; D_POCO wifi 1 bt 1, HFP ..:33:59, A2DP up; D_MOTO wifi 1 bt 0. PREFLIGHT OK, same as the opening table.

## Anything the brief did not ask about

- Answers to the brief's three questions:
  1. R1 D1 is 2.556, 2.479 and 2.229 s: with the poke off, the phone finds a late listener in about 2.5 s.
  2. R3's last P-dial before the launch is 2.3 s and 1.1 s before it, after a 150 s hold: the phone is still redialling a held link at 150 s, 28 dials per cycle.
  3. R4 D3 is 3.983, 4.251 and 4.138 s from the phone's Bluetooth switch-on to the accepted connection, with the listener already up. In the late order (R1, R2) the connection is accepted 1.5 to 3.3 s after the listener opens and the session is up 12.2 to 14.3 s after the launch, so being late costs about 2 to 3 s of dial wait on top of the normal setup, not the 10 to 20 s seen in the field.

- R4 cycle 1 took 15.5 s from H3 to H4 (17:49:24.466 to 17:49:40.011) against 2.9 and 3.1 s in cycles 2 and 3. Cycle 1 was the first early-order cycle after a long late hold; not investigated.
- Android Auto 18.0's dial interval measured here is 5.2 to 5.8 s with no sign of backing off over 150 s, so the escalation after 90 s in `HandsFreeWakeEscalationPolicy` was not reached in any cycle. Whether its premise ("a link that never changes raises no further event") holds for a late listener: this round says the phone redials regardless.
- The app learning `native-poke-bt-macs` after a session means any multi-cycle run that depends on an empty poke list must rewrite it each cycle.

**Evidence:** release `rig-evidence-late-listener-poke` on `o-jcardenass/open-headunit`, asset `late-listener-poke-round1-captures.zip`, sha256 `bedb3610d4870b59069c6f4d87875e9aeead0a2cb93aeaae96b50b638b26a7dd`.
