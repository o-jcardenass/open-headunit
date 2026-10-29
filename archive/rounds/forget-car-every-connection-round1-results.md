# forget-car-every-connection, round 1 results

**Candidate:** `fix/forget-car-every-connection` @ `93a59f7a2f722391e8b3579eecfc7b53b7214699` (tree `ce1cd02f5d7804cf5cbc7fc4de5f78cc8c438677`)   **Baseline:** none built (Stage 2 compares with the `samsung-driver-native` round 1 addendum on `main` `71375a68`)
**APK md5:** `9a103bdc05d45d05cab1610c93f23f42` (candidate, identical on D-HU and D-POCO); no baseline APK
**Unit:** Stage 1 head unit D-HU (UNISOC MT50, Android 14, rooted adbd) with D-POCO as phone (Android 15); Stage 2 head unit D-POCO with D-MOTO as phone (Android 14). Gearhead `17.9.664004-release` on both phones.
**Date:** 2026-10-09
**Evidence:** release `rig-evidence-forget-car-every-connection`, asset `forget-car-every-connection-round1-captures.zip`, sha256 `aed06b640415d94d0b5c072a1506917871f6fde910a18e418f537ae14665bc04`

## Round summary

| Run | Verdict | One line |
|---|---|---|
| H1 | **FAIL** | c1, c2 and r1 meet every condition. r2 (a read) left `wifi-direct-ip-repeated` = `true`; expected `false` or absent. |
| H2 | **FAIL** | Advertises and reconnects by WPP over TCP. c1 grade line `stable=unproven`, not `stable=yes`; phone dial errors present in c2 and c3 (stamped before the app relaunched). |
| H3 | **PASS** | The point of the round. Moved IP withheld, one WARN against 6 deliveries, banner stamp held, type 10 sent. |
| H4 | **FAIL** | A single read proved the IP and cleared the banner: stamp 1791601716145 to 0, `ip-repeated` false to true. |
| P1 | **FAIL** | Every head-unit condition met in all 4 cycles. c3 phone strings: `Trying to start WPP on TCP` = 0, `NETWORK_NOT_FOUND` = 1 (a scan event, see R P1). |

One mechanism accounts for the H1, H2-c1/c3 and H4 misses: the IP grade of a surviving group runs before the "already up" decision flags it as a read, so the read is graded as a create. Details under "Anything the brief did not ask about".

## Setup notes

**Scripts and paths.** The scripts folder is now `rig-toolkit/` (the old `hur-wifi-test-scripts` name does not exist here; the skill and the `rig-executor` agent file still point at it). I ran every step in the host session with `adb`, no `rig-executor` agent. Used: `rig_preflight.sh`, `wt-new.sh`, `build_hur_cool.sh`, `run_unit_tests.sh`, `apk_identity.sh`, `rig_thermal.sh`, `set_prefs_runas_host.py` (D-POCO), `restore_settings.sh` (D-POCO). Nothing added to `rig-toolkit/`. One-round scripts are in `rig-data/rounds/forget-car-every-connection-round1/scripts/`: `fc_lib.sh` (the brief's section 5 helpers), `h1.sh` to `h4.sh`, `p1.sh` (adapted from the addendum's `a1.sh`, using the brief's helpers and D-MOTO as phone). Quirk files read: `topics/tooling.md`, `topics/wifi.md`, `units/D-HU.md`, `units/D-POCO.md`, `units/D-MOTO.md`.

**Pre-flight** (`rig_preflight.sh D_HU:wifi,bt D_POCO:wifi,bt D_MOTO:wifi,bt`): PREFLIGHT OK.

| Role | ADB | WiFi | BT | Screen | Profiles |
|---|---|---|---|---|---|
| D_HU | ok | 1 | 1 | Awake | HFP:none A2DP:none |
| D_POCO | ok | 1 | 1 | Awake | HFP:none A2DP:none |
| D_MOTO | ok | 1 | 1 | Awake | HFP:none A2DP:none |

D-POCO also appeared as a second adb transport over WiFi; every command used the USB serial.

**Build gate.** Worktree from `wt-new.sh`. The first build failed with `SDK location not found` because a new worktree has no `local.properties`; I copied the main checkout's file in and rebuilt. Build exit 0. `thermal_guarded` stopped the Gradle tree at 88 C and resumed at 68 C (3 min 35 s pause); the thermal log's maximum during the build was 95 C. No run overlapped a build. Unit tests: **3035 tests, 0 failures, 0 errors, 0 skipped**. APK copied out of `apks/` at once. Identity on both units: md5 match; `ACTION_QUERY_STATE` reports `commit 93a59f7a2f72-dirty` (the `-dirty` is the untracked `.WORKTREE-INFO` that `wt-new.sh` writes; `git diff` of tracked files is empty); `noteAdvertisedGroupAddressMoved` found 2 times in the dex. Settings were unchanged by the install (diff empty on both units).

**P1 to P4.**
- P1: all three present. D-POCO `isGroupOwner: false`. D-POCO and D-MOTO on their launchers.
- P2: D-HU `shared_prefs` is `u0_a176:u0_a176 771`, `settings.xml` `u0_a176:u0_a176 660`, app uid 10176. Correct, no `chown`.
- P3: D-POCO is bonded to D-HU's radio (`Navegadortz2`): yes (also `Navegadortz3`). D-MOTO is bonded to `POCO X3 NFC`: yes. D-POCO's list redacts the Motorola address (`XX:XX:XX:XX:E4:95`), so `MOTO_MAC` is "redacted".
- P4: nothing missing, no message sent.

**Deviations from the brief.**
1. **No `su` on D-HU.** `su -c` fails (`su: inaccessible or not found`) because adbd already runs as root (`uid=0`). `hu_kv` runs `sh /data/local/tmp/fc_kv.sh` directly. Ownership and mode were set by the script and read back as `u0_a176:u0_a176`.
2. **Output folder** is `rig-data/rounds/forget-car-every-connection-round1/`, not `~/rig-private/...` (the tidy hook blocks that).
3. **Window rule.** The app process starts about 3 s after `am start`, so on a read the lines `is already up from`, `group address ip=` and the first credential deliveries print 120 to 300 ms before the start marker (example, H1-r1: grade line 22:03:14.176, marker 22:03:14.297). Windowed counts therefore read 0 for them. Every capture holds exactly one launch (it starts before the launch), so for those lines I graded the whole capture and say so in each run. Windowed and whole-file counts are both in the run sections where they differ. Create-path lines (c1, c2, P1) fall inside the window.
4. **Stage 2 wake list.** `MOTO_MAC` redacted, so `native-poke-bt-macs` is an empty set and `native-poke-all-paired` is `true`, as the brief says. (D-HU's own `last-connected-native-mac` was `A0:46:5A:97:E4:95`, whose tail matches the redacted address; not used.)
5. **D-HU Bluetooth in Stage 2 reverted on before every one of the 4 cycles** (`settings get global bluetooth_on` = 1 at each cycle start); `p1.sh` disabled it again each time. D-HU's app was stopped throughout, so it did not poke or answer, but its radio was on for most of each cycle.
6. **D-HU settings at round start** (kept, as the brief says): `native-poke-bt-macs` = `DC:B7:2E:5E:4E:59` (D-POCO), `native-poke-all-paired` = `true`, `native-driver-selection-mode` = 1 (AUTO), `connection-modes` = `wifi`, `self`, `log-level` already 2. `connection-issue-stale-endpoint` was present as 0. Stage 1 baseline written: mode 3, `native-ap-transport` 0, `wifi-direct-stable-identity` true, `static-p2p-bssid` 0, `log-level` 2, `onboarding-version` 2; the seven new keys and the stamp deleted, read back.
7. **Restores.** D-HU `settings.xml` restored as root, `cmp` against the round-start backup: identical (`u0_a176:u0_a176 660`). D-POCO restored, `cmp`: identical. The delta each stage applied is in the evidence zip (`settings-s1-final-before-restore.xml`, `settings-POCO-s2-final-before-restore.xml`). D-HU Bluetooth on again, D-MOTO Bluetooth on again, D-POCO `isGroupOwner: false`.
8. **Thermal.** Every run segment 54 to 60 C, throttle delta 0. No run voided.
9. **Per-cycle logs.** `say` wrote to the per-segment log, so `H2.log` and `P1.log` lack the SSL wait lines; times below are computed from the capture timestamps (start marker to `SSL handshake complete`).

## R H1: the IP record, two creates prove it, two reads do not

**FAIL**

- Settings written: Stage 1 baseline (above); IP record deleted before r1.
- Radio state: D-POCO airplane mode on (`airplane_mode_on` = 1), no phone in the run. D-HU station WiFi and Bluetooth on.
- Discard-rule check: clean. `MATCH! Starting AapService` 0 in all four. `p2p-wlan0` index 1 in c1, 2 in c2, r1, r2 (the bump from 1 to 2 is the second create, as designed).

| Segment | createGroup | already up | `group address ip=` lines | credential deliveries | `ip=unread` | settings after |
|---|---|---|---|---|---|---|
| c1 (create) | 1 | 0 | 1 | 4 | 0 | last-ip 192.168.49.1, repeated `false` |
| c2 (create) | 1 | 0 | 1 | 4 | 0 | last-ip 192.168.49.1, repeated `true` |
| r1 (read, whole capture) | 0 | 1 | 1 | 4 | 0 | last-ip 192.168.49.1, repeated `false` |
| r2 (read, whole capture) | 0 | 1 | 1 | 4 | 0 | last-ip 192.168.49.1, repeated **`true`** |

Decisive lines:
- c1, 22:01:40.070: `group address ip=192.168.49.1 stable=no (first reading of the WiFi Direct group's address at 192.168.49.1; it has to come back after a restart)`.
- c2, 22:02:29.865: `group address ip=192.168.49.1 stable=no (name, BSSID and address repeat)` (the label carries D-HU's BSSID half, as the brief says).
- r1, 22:03:14.176: `group address ip=192.168.49.1 stable=no (first reading ...)`, after `is already up from` at 22:03:14.035.
- r2, 22:03:59.108: `group address ip=192.168.49.1 stable=unproven (name, BSSID and address repeat)`, **before** `is already up from` at 22:03:59.116.

Conditions not met: after r2 `wifi-direct-ip-repeated` is `true` (expected `false` or absent), and the r2 line does not carry the "not yet seen across a restart" wording. Everything else in the PASS list holds, including `group address ip=` = 1 against 4 deliveries in all four captures and 192.168.49.1 on every line. Both reads kept the group (c2 left it up through the force-stop: `groupFormed: true isGroupOwner: true`, `groupOwnerAddress: /192.168.49.1`).

## R H2: a proven fixed IP still advertises and the phone reconnects by WPP over TCP

**FAIL**

- Settings written: seed `wifi-direct-ip-repeated` = `true`, `wifi-direct-last-identity-verdict` = `STABLE`; `H2-seed` read back: last-ip 192.168.49.1, digest present, repeated `true`, verdict `STABLE`; group up (`groupFormed: true isGroupOwner: true`).
- Radio state: `ph_up` (airplane off, WiFi and Bluetooth enabled, both read back ON via dumpsys). D-POCO on its launcher.
- Discard-rule check: clean (`MATCH!` 0, createGroup 0, one `SSL handshake complete` each).

| Cycle | Route | Start marker to SSL | already up (file) | `group address` label | credential lines (file) | advertising 192.168.49.1:5299 | `connection from` |
|---|---|---|---|---|---|---|---|
| c1 | Bluetooth (`Connection accepted from POCO X3 NFC`) | 22:05:52.690 to 22:06:01.506 = 8.8 s | 1 | **`unproven`** | 5 yes, 2 unproven | 1 | 0 |
| c2 | WPP over TCP | 22:06:32.435 to 22:06:34.068 = 1.6 s | 1 | `yes` | 6 yes | 0 | 3 |
| c3 | WPP over TCP | 22:07:07.182 to 22:07:08.274 = 1.1 s | 1 | `unproven` | 4 yes, 2 unproven | 0 | 3 |

- c1 advertised (`NativeAA: advertising WPP over TCP at 192.168.49.1:5299`, 22:06:01 window) and the four `wifi-direct-advertised-endpoint-*` keys are present with `-ip` = 192.168.49.1.
- `needs this head unit forgotten` 0 in all three. `connection-issue-stale-endpoint` absent after c3.
- Phone: c2 `Trying to start WPP on TCP with configuration` 6, c3 7, both with `ipAddress=192.168.49.1`; `NETWORK_NOT_FOUND` 0, `BSSID_MISMATCH` 0, **`TCP_SOCKET_CONNECTION_FAILED` 2 in c2 and 2 in c3**. All four are stamped before the app relaunched (c2 22:06:29.050 and 22:06:31.139, app up 22:06:32.1; c3 22:07:03.145 and 22:07:05.231, app up 22:07:06.9); no failure after the HU was listening.

Conditions not met: c1 line reads `stable=unproven`, not `stable=yes`, and 2 of 7 c1 credential lines carry `identity stable=unproven` (grade line 22:05:52.560 precedes `is already up from` 22:05:52.603); the phone error count is not 0. The first is the mechanism in the findings section; the second is the phone dialling a stopped head unit between cycles. Neither reached the outcome the brief names as FAIL: c1 advertised, and c2 and c3 reconnected by TCP without a forget.

## R H3: a moved group IP withholds, warns once and holds the banner

**PASS**

- Settings written: `wifi-direct-last-ip` and `wifi-direct-advertised-endpoint-ip` = 192.168.77.1. `H3-seed` read back: last-ip 192.168.77.1, repeated `true`, verdict `STABLE`, all four endpoint keys present (`-ip` 192.168.77.1); group up.
- Radio state: D-POCO radios on (left from H2), D-POCO on its launcher.
- Discard-rule check: clean (`MATCH!` 0, createGroup 0, one `SSL handshake complete`).
- Counts (whole capture, read-path lines precede the marker): `is already up from` 1; moved line 1 on `group address ip=192.168.49.1 stable=no`; credential deliveries **6**, all `identity stable=no`; WARN `the WPP endpoint advertised on the WiFi Direct group at 192.168.77.1` **exactly 1** (against 6 deliveries); `NativeAA: advertising WPP over TCP at` 0; `not advertising WPP over TCP:` 1; `WppTcpServer: connection from` 1 then `rejecting this dial` 1; `SSL handshake complete` 1 within the window.
- Decisive lines: 22:08:36.112 `group address ip=192.168.49.1 stable=no (the WiFi Direct group came back but its address moved from 192.168.77.1 to 192.168.49.1, and the phone would keep the old one)`; 22:08:36.145 WARN `NativeAA: the WPP endpoint advertised on the WiFi Direct group at 192.168.77.1 no longer matches (address 192.168.77.1 -> 192.168.49.1); a phone holding it needs this head unit forgotten in Android Auto`; 22:08:36.338 `WppTcpServer: connection from 192.168.49.136`; 22:08:36.444 `rejecting this dial so the phone drops our endpoint and goes back to Bluetooth`; 22:08:36.468 `[TX] wrote type 10`; 22:08:37.458 `not advertising WPP over TCP`; 22:08:38.512 `SSL handshake complete` (Bluetooth route).
- `settings-H3.xml`: `connection-issue-stale-endpoint` = **1791601716145** (> 0), `wifi-direct-last-ip` = 192.168.49.1, `wifi-direct-ip-repeated` = `false`, no `wifi-direct-advertised-endpoint-*` key.
- The dial was not served (a type 10 followed within 130 ms, no session on the TCP route). Phone: `Trying to start WPP on TCP` 2, `No WPP on TCP configuration found in storage` 5, `TCP_SOCKET_CONNECTION_FAILED` 2, `THROTTLE_LIMIT_EXCEEDED` 15 (recorded, not graded).

The H3 grade line also preceded `is already up from` here (22:08:36.112 against 22:08:36.153), but a moved address is graded `no` whichever way it is flagged, so the outcome was unaffected.

## R H4: the phone is clean after the type 10, and the banner still holds

**FAIL**

- Settings written: none (state from H3). `H4-start` read: stamp 1791601716145, last-ip 192.168.49.1, repeated `false`, verdict `STABLE`.
- Discard-rule check: clean.
- Met: `advertising WPP over TCP at` 0; `WppTcpServer: connection from` 0; `SSL handshake complete` 1 (22:09:42.823, Bluetooth route); phone `No WPP on TCP configuration found in storage` 6, `Trying to start WPP on TCP` 0 (H3 did log `rejecting this dial`, so the phone conditions are graded, not INCONCLUSIVE).
- Not met: the grade line is `group address ip=192.168.49.1 stable=unproven (name, BSSID and address repeat)` at 22:09:36.608, not `but not yet seen across a restart` (count 0); it precedes `is already up from` at 22:09:36.659. After the run `connection-issue-stale-endpoint` is **0** (was 1791601716145) and `wifi-direct-ip-repeated` is `true` (was `false`). The banner was shown at 22:09:35.267 (`showing the connection issue banner for PHONE_HOLDS_STALE_ENDPOINT`) and then cleared by this one read and Bluetooth landing.

## R P1: a real fixed-IP unit, withhold once after the update, then advertise and reconnect by TCP

**FAIL**

- Settings written: Stage 2 keys (mode 3, `native-ap-transport` 0, stable identity true, empty wake set, `native-poke-all-paired` true, driver selection 0, onboarding 2, log level 2), the seven new keys and the stamp deleted, read back.
- Radio state: D-HU Bluetooth disabled (read OFF), reverted on before each cycle and disabled again (Setup note 5). D-MOTO awake, keyguard not showing, launcher, WiFi and Bluetooth on.
- Discard-rule check: clean (`MATCH!` 0, one `createGroup SUCCESS` and one `SSL handshake complete` per cycle, `never opened the Android Auto channel on radio [` 0, so the stop rule never applied).

| Cycle | Route | Start marker to SSL | Group address line | Advertising | Phone |
|---|---|---|---|---|---|
| c1 | Bluetooth | 22:11:45.358 to 22:11:58.812 = 13.5 s | `ip=192.168.49.1 stable=unproven (first reading ...)` | withheld (`not advertising WPP over TCP:` 1) | No WPP config 2, Trying 0, errors 0 |
| c2 | Bluetooth | 22:12:44.299 to 22:12:54.723 = 10.4 s | `stable=yes (name, BSSID and address repeat)` | `advertising WPP over TCP at 192.168.49.1:5299` 1 | Trying 1, errors 0 |
| c3 | WPP over TCP (`connection from 192.168.49.180` 22:13:47.669) | 22:13:43.261 to 22:13:48.346 = 5.1 s | `stable=yes` | none needed | **Trying 0, `NETWORK_NOT_FOUND` 1**, other errors 0 |
| c4 | WPP over TCP | 22:14:37.199 to 22:14:45.111 = 7.9 s | `stable=yes` | none needed | Trying 1, errors 0 |

- All four: `needs this head unit forgotten` 0; `group address ip=unread` 0.
- Comparison with `main` (addendum: 10.3 s, 8.7 s, 8.0 s, endpoint advertised in cycle 1): c1 and c2 land by Bluetooth here at 13.5 s and 10.4 s; c3 and c4 are 5.1 s and 7.9 s. The behavioural difference the candidate was written for shows: cycle 1 withholds and cycle 2 advertises.
- Why FAIL: c3's two phone conditions. `NETWORK_NOT_FOUND` matched `WIRELESS_WIFI_SCAN_RESULTS_NETWORK_NOT_FOUND` (a `GH.ConnLoggerV2` scan event at 22:13:41.934, 0.9 s before `createGroup SUCCESS` at 22:13:42.801), not a stored-endpoint failure. The phone did dial: `GH.WPP.TCP: WPP on TCP connected to the WiFi network` 22:13:48.302, `Created raw socket on attempt 0`, and the head unit logged the connection and SSL. The brief's `Trying to start WPP on TCP with configuration` line did not print in that cycle. I graded the letter of the conditions; the head-unit side of c3 is clean.

## Anything the brief did not ask about

**1. A read of a surviving group can be graded as a create (the cause of the H1, H2-c1/c3 and H4 misses).** `WifiDirectManager.kt:1419` samples `nativeGroupWasRead` for each credential delivery; the flag is set at `:2107`, after the adopt-or-create decision. The identity assessment at `:1305` is guarded by `nativeAdoptDecisionPending`; the IP grade path at `:1419` to `:1444` is not. In the logs, the grade line comes before the `already up` line in 5 of the 7 read launches, and the unproven label and the false proof follow the same split:

| Launch | grade line | `already up` line | label |
|---|---|---|---|
| H1-r1 | 22:03:14.176 | 22:03:14.035 | first reading (record empty, correct) |
| H1-r2 | 22:03:59.108 | 22:03:59.116 | unproven, then `ip-repeated` true |
| H2-c1 | 22:05:52.560 | 22:05:52.603 | unproven |
| H2-c2 | 22:06:32.309 | 22:06:32.153 | yes |
| H2-c3 | 22:07:06.923 | 22:07:06.966 | unproven |
| H3 | 22:08:36.112 | 22:08:36.153 | no (moved address, unaffected) |
| H4 | 22:09:36.608 | 22:09:36.659 | unproven, `ip-repeated` true, stamp cleared |

This is read from the code and the log order; I did not instrument it. It matters in the field because a single reconnect to a surviving group can prove the IP and clear the banner (H4). It has no hardware test in the JVM suite, as the brief says.

**2. Phone dials between cycles.** In H2 the phone dials the stored endpoint while the head unit's app is force-stopped between cycles, so `TCP_SOCKET_CONNECTION_FAILED` is expected there. A brief that wants 0 should either not stop the app between cycles or window the phone capture from the head unit's launch.

**3. Brief errata.** (a) `su -c` does not exist on D-HU. (b) The `launch; sleep 3; mark start` placement puts read-path lines just before the start marker, so the windowed counts for `is already up from` and `group address ip=` are 0 on every read; place the marker before the launch, or grade those two on the whole capture. (c) The Gearhead string `NETWORK_NOT_FOUND` also appears inside `WIRELESS_WIFI_SCAN_RESULTS_NETWORK_NOT_FOUND`; grep with the `GH.WPP` prefix. (d) The P1 phone line `Trying to start WPP on TCP with configuration` did not print in one TCP-route cycle (P1-3).

**4. Reconnect speed.** WPP over TCP landed in 1.1 s and 1.6 s (H2) and 5.1 s and 7.9 s (P1) from the start marker, against 8.8 s to 13.5 s by Bluetooth.

**5. Not run.** No second pass of any run, no baseline arm. H1 was run once.
