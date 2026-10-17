# forget-car-every-connection, round 2 results

**Candidate:** `fix/forget-car-every-connection` @ `c06d35b576d869c27987415f6d2f2d9a555cdd66` (tree `5d2ba66510099f43eebce69d401d61899247240b`, matches the brief)
**Baseline:** none built. Round 1's captures on `93a59f7a` are the comparison.
**APK md5:** candidate `4d2e85930a8a99b6f0e8544b22a4942b` (pulled back from both units, identical)
**Unit:** Stage 1 D-HU (UNISOC MT50, Android 14) as head unit with D-POCO (POCO X3 NFC, Android 15) as phone; Stage 2 D-POCO as head unit with D-MOTO (edge 30 neo, Android 14) as phone. Gearhead on D-MOTO `17.9.664004-release`.
**Date:** 2026-10-10

## Round verdict: PASS

All seven runs PASS. No run needed a re-run. No discard-rule hit (`MATCH! Starting AapService` = 0 in every capture).

## Setup notes

- **Pre-flight (`rig_preflight.sh D_HU:wifi,bt D_POCO:wifi,bt D_MOTO:wifi,bt`) FAILED on two items; the operator chose "proceed, Stage 1 as briefed".**
  - D-MOTO WiFi 0 and Bluetooth 0 at the start. The brief wants D-MOTO Bluetooth off for all of Stage 1 anyway. The operator switched D-MOTO WiFi and Bluetooth on and unlocked it before Stage 2 (a hand step; I never switch a phone radio on myself). D-MOTO WiFi turned out to be on the home network (192.168.1.5), not a P2P group.
  - A stray-process FAIL named a Gradle daemon (java, pid 131471). It is the build daemon, not a logcat; no logcat was running (`ps aux | grep logcat` = 0 before and after).
  - Table: D_HU wifi 1 bt 1 Awake; D_POCO wifi 1 bt 1 Awake, HFP up; D_MOTO wifi 0 bt 0 Awake.
- **P2:** D-HU `shared_prefs/` read `u0_a176:u0_a176 771` (the app's uid), so no chown was needed. After the end-of-Stage-1 restore the file reads `u0_a176:u0_a176 660`.
- **P3:** D-POCO bonded to `Navegadortz2`: yes. D-MOTO bonded to `POCO X3 NFC`: yes. P4 sent nothing for pairing. D-POCO was on the clock app at pre-flight and was sent HOME; D-MOTO was on the notification shade and stayed on the minimalist launcher after the operator unlocked it.
- **Build:** worktree via `wt-new.sh` (`worktrees/forget-car-every-connection__candidate__c06d35b5__20261010`, not `../ohu-wt-...`; the brief's path predates the layout rule). `git ls-remote fork` printed `c06d35b5...`. Built with `build_hur_cool.sh` (thermal guard stopped Gradle at 86C and resumed at 68C several times; build host peak below the 95C void limit). APK copied out of `rig-data/apks/` at once. `run_unit_tests.sh`: **3041 tests, 0 failures, 0 errors** (counted from the JUnit XML).
- **Identity gate on both units:** `ACTION_QUERY_STATE` `commit":"c06d35b576d8-dirty"` and `raisesStaleEndpointRecord` count 1 in the pulled dex, on D-HU and on D-POCO. `install -r` left `settings.xml` byte-identical on both (diffed).
- **Scripts:** `rig-toolkit/` was used for `rig_preflight.sh`, `rig_thermal.sh`, `wt-new.sh`, `build_hur_cool.sh`, `run_unit_tests.sh`, `set_prefs_runas_host.py` and `restore_settings.sh`. The `hur-wifi-test-scripts` name the skill cites is not present as a symlink under `StudioProjects/`; I used `rig-toolkit/` directly. One-round scripts are in `rig-data/rounds/forget-car-every-connection-round2/scripts/` (`fc2_lib.sh` adapted from round 1's `fc_lib.sh` with the brief's section 5 helpers, `h1.sh`, `h2.sh`, `seg.sh` for H5/H3/H4/H6, `p1.sh`). No new script in `rig-toolkit/`.
- **Script defects found and fixed during the round:**
  - `th_pkg` returned two lines (`grep -o 'pkg=[0-9]*'` also matches `throttle_pkg=`), which made `th_wait` print `integer expression expected` and skip the cool-down gate for the first segment of H1 (host read 65C, below 75C, so no effect). Fixed with ` pkg=` and `head -1` before H2; the gate worked from H2 on.
  - A first `hu_kv` call in setup passed the whole baseline as one argument and made `sed` print `bad pattern`; the file was verified unchanged apart from the intended `wifi-connection-mode` re-write (same value). The baseline was then written correctly and read back; XML parses.
- **Settings written, D-HU Stage 1 baseline:** `wifi-connection-mode` 3, `native-ap-transport` 0, `wifi-direct-stable-identity` true, `static-p2p-bssid` 0, `log-level` 2, `onboarding-version` 2, plus `clear_new`. Delta against the round-start backup: **no value changed**; only `connection-issue-stale-endpoint` went from `0` to absent and the key order moved. `native-poke-bt-macs` = `DC:B7:2E:5E:4E:59` (D-POCO) and `native-poke-all-paired` = `true` were kept. End of Stage 1: restored as root, `chown` to the app uid, `chmod 660`, `diff` against the round-start backup **empty**.
- **Settings written, D-POCO Stage 2:** the brief's eight keys via `set_prefs_runas_host.py`, with `native-poke-bt-macs` an empty set, and the seven new keys plus the stamp deleted. Delta against D-POCO's round-start file: `native-poke-bt-macs` went from `A0:46:5A:97:E4:95` to empty; `wifi-direct-last-ip`, its digest, `wifi-direct-ip-repeated=false` and a stale `connection-issue-stale-endpoint=1791648602123` (left from an earlier round) were removed; the other keys were added or re-written as listed. End of Stage 2: restored with `restore_settings.sh`, `diff` **empty**.
- **D-HU Bluetooth** was ON before every Stage 2 cycle (self-revert), `OFF` after each `svc bluetooth disable`: reads at 14:04:18 / 14:05:11 / 14:06:05 (ON), each followed by OFF. Re-enabled at the end of Stage 2 (reads `ON`). **D-MOTO Bluetooth** was ON at 13:54:39 (before H2-c1; a self-revert since I disabled it earlier) and OFF at every later Stage 1 cycle start.
- **Phone clock:** `$PHT` was taken from the phone's `date` just before each launch (files `<id>.pht`); all phone counts are lines at or after it. `pnf` excludes `SCAN_RESULTS_NETWORK_NOT_FOUND`.
- **Quirk files read:** `rig-quirks/topics/tooling.md`, `rig-quirks/units/D-HU.md`, `D-POCO.md`, `D-MOTO.md`; the template without sections 7b and 8.
- **Thermal:** host package peak 65C over all runs; `throttle_delta=0` for every segment (14 thermal logs).
- **Evidence:** `forget-car-every-connection-round2-captures.zip` (45268351 bytes) in release `rig-evidence-forget-car-every-connection`; sha256 `ba9c5f1f62d663d590f002f3e53fbacb26684f959e5a17055a0b805cfabc1c5c`.

## R-H1 The IP record: two creates prove it, two reads do not

**PASS**

- Settings: Stage 1 baseline, D-POCO in airplane mode (`airplane_mode_on=1`) for the whole run. Radios: D-MOTO Bluetooth off.
- Clean start done at 13:50:52. Group check after c1: `groupFormed: false`; after c2: `groupFormed: true`, `groupOwnerAddress: /192.168.49.1`.
- Counts (window start of capture to the end marker):

| Capture | `createGroup SUCCESS` | `is already up from` | `group address ip=` (of which `192.168.49.1`) | deliveries | `StationStandDown: ` (not graded) | `lno` group / up |
|---|---|---|---|---|---|---|
| H1-c1 | 1 | 0 | 1 (1) | 5 | 1 | 3506 / none |
| H1-c2 | 1 | 0 | 1 (1) | 5 | 1 | 3459 / none |
| H1-r1 | 0 | 1 | 1 (1) | 6 | 1 | 2116 / 1850 |
| H1-r2 | 0 | 1 | 1 (1) | 4 | 1 | 1594 / 1404 |

- c1: `10-10 13:51:00.589 ... group address ip=192.168.49.1 stable=no (first reading of the WiFi Direct group's address at 192.168.49.1; it has to come back after a restart)`.
- c2: `10-10 13:51:51.054 ... group address ip=192.168.49.1 stable=no (name, BSSID and address repeat)`; `settings-H1-c2.xml`: `wifi-direct-last-ip` `192.168.49.1`, `wifi-direct-ip-repeated` `true`.
- r1: `10-10 13:52:35.482 ... stable=no (first reading ...)`, `wifi-direct-ip-repeated` `false`. The `group address` line (2116) follows the `is already up from` line (1850).
- r2: `10-10 13:53:20.740 ... stable=no (name, BSSID and address repeat)`; the line does not carry `stable=yes`; `wifi-direct-ip-repeated` `false` (round 1 read `true` here). Line 1594 follows 1404.
- P2P interface index: `p2p-wlan0-1` in c1, `p2p-wlan0-2` in c2, r1, r2 (two creates, no extra churn).

The r2 line still carries the label text `name, BSSID and address repeat` on a read even though the record stays unproven (`ip-repeated=false`); the brief grades `stable=` and the stored key, both correct, so this is only recorded.

## R-H2 A proven fixed IP still advertises, and the phone reconnects by WPP over TCP

**PASS**

- Seed: `wifi-direct-ip-repeated` true, verdict `STABLE`; `H2-seed` read: last IP `192.168.49.1`, 64-hex digest, repeated `true`, verdict `STABLE`; `groupFormed: true`. Phone up via `ph_up` (HOME key sent first because the phone showed the notification shade).

| Cycle | `is already up from` | `group address ip=192.168.49.1 stable=yes` | `advertising WPP over TCP at 192.168.49.1:5299` | `WppTcpServer: connection from` | route | start marker to SSL | forgotten | stamp | phone trying / connected / nostore / TCP fail / BSSID / pnf |
|---|---|---|---|---|---|---|---|---|---|
| c1 | 1 | 1 | 1 | 0 | `NativeAA: Connection accepted from` | 5.7 s | 0 | 0 | 0 / 0 / 2 / 0 / 0 / 0 |
| c2 | 1 | 1 | 0 | 3 | TCP | 1.4 s | 0 | 0 | 7 / 7 connected / 0 / 2 / 0 / 0 |
| c3 | 1 | 1 | 0 | 3 | TCP | 1.5 s | 0 | 0 | 5 / 5 connected / 0 / 1 / 0 / 0 |

- c1 identity labels: all 7 credential deliveries carry `identity stable=yes`; `settings-H2-c1.xml` holds all four `wifi-direct-advertised-endpoint-*` keys, `-ip` `192.168.49.1`. Round 1 read `unproven` here.
- c2 and c3 label counts: 6 deliveries each, all `stable=yes`. The phone's `SSL` follows within 1.5 s of the start marker.

c2 and c3 did not log `advertising WPP over TCP`: the phone dialled the stored endpoint before the app advertised again. The brief does not grade advertising in those cycles.

## R-H5 No false banner after the update

**PASS**

- Seed (`H5-seed`): no `wifi-direct-last-ip`, no stamp, verdict `STABLE`, the four endpoint keys present with `-ip` `192.168.49.1`. Phone still up from H2.
- Gate: `WppTcpServer: connection from` = 1 (not INCONCLUSIVE); `not withdrawing the endpoint because the` = 0.
- `10-10 13:57:15.784 ... group address ip=192.168.49.1 stable=unproven (first reading of the WiFi Direct group's address at 192.168.49.1; it has to come back after a restart)`; `is already up from` 1.
- `10-10 13:57:15.764 ... WppTcpServer.refuse | WppTcpServer: this dial reached the group's live address, so the rejection leaves nothing to forget` (count 1); `rejecting this dial so the phone drops our endpoint and goes back to Bluetooth:` 1.
- `needs this head unit forgotten` 0; `showing the connection issue banner for PHONE_HOLDS_STALE_ENDPOINT` 0; `stamp H5` = 0.
- `advertising WPP over TCP at` 0; `not advertising WPP over TCP:` 1. `SSL handshake complete` at 13:57:18.236, 2.3 s after the start marker (13:57:15.921), over Bluetooth (`Connection accepted from` 13:57:16.701).
- Phone: `Trying to start WPP on TCP with configuration` 1, `WPP on TCP connected to the WiFi network` 1, `No WPP on TCP configuration found in storage` 5 (after the rejection).

On `93a59f7a` this dial raised the banner, so this run does separate the builds.

## R-H3 A moved group IP withholds, warns once and holds the banner

**PASS**

- Seed: `wifi-direct-last-ip` and `-advertised-endpoint-ip` = `192.168.77.1`; the other three endpoint keys were present; verdict `STABLE`.
- `is already up from` 1; `lno` group 2152 > up 1932.
- `10-10 13:58:10.634 ... group address ip=192.168.49.1 stable=no (the WiFi Direct group came back but its address moved from 192.168.77.1 to 192.168.49.1, and the phone would keep the old one)` (count of the moved text: 1).
- Deliveries 6; `identity stable=yes` 0. `the WPP endpoint advertised on the WiFi Direct group at 192.168.77.1` **1**; `needs this head unit forgotten` 1.
- `advertising WPP over TCP at` 0; `not advertising WPP over TCP:` 1; `SSL handshake complete` 1 at 5.9 s.
- `settings-H3.xml`: `connection-issue-stale-endpoint` = `1791658690660` (`stamp H3` > 0); `wifi-direct-last-ip` `192.168.49.1`; `wifi-direct-ip-repeated` `false`; no `wifi-direct-advertised-endpoint-*` key.
- Not graded: `WppTcpServer: connection from` = 0 (as expected); banner line count 0 in this window.

## R-H4 A read does not clear the banner

**PASS**

- `is already up from` 1, `createGroup SUCCESS` 0; `lno` group 2152 > up 1923.
- `10-10 13:59:01.535 ... group address ip=192.168.49.1 stable=unproven (same address 192.168.49.1, but not yet seen across a restart, which is when tethering picks a new one)`.
- `advertising WPP over TCP at` 0; `WppTcpServer: connection from` 0; `SSL handshake complete` 1 at 1.6 s over Bluetooth.
- **Stamp: H3 `1791658690660`, H4 `1791658690660` (unchanged; round 1 read 0).** `wifi-direct-ip-repeated` `false` (round 1 read `true`).
- Phone: `No WPP on TCP configuration found in storage` 6; `WPP on TCP connected to the WiFi network` 0.
- Also seen: `showing the connection issue banner for PHONE_HOLDS_STALE_ENDPOINT` = 1 in this run (the banner appears once the home screen shows it; H3's window had 0).

## R-H6 A create proves the IP and releases the banner

**PASS**

- Group was removed by H4's `huexit` (`groupFormed: false` before the run). `createGroup SUCCESS` 1, `is already up from` 0.
- `10-10 14:00:00.320 ... group address ip=192.168.49.1 stable=no (name, BSSID and address repeat)`.
- `SSL handshake complete` 1, 8.5 s after the start marker; `WppTcpServer: connection from` 0; `needs this head unit forgotten` 0.
- `settings-H6.xml`: `wifi-direct-ip-repeated` `true`; `connection-issue-stale-endpoint` `0` (`stamp H6` = 0).

Summary of H4 and H6 together: the banner stamp held at `1791658690660` through the read in H4 and went to 0 on the create in H6, so the change separates reads from creates.

## R-P1 A real fixed-IP unit: withhold once after the update, then advertise and reconnect by TCP

**PASS**

- Settings: Stage 2 baseline on D-POCO. D-HU Bluetooth disabled before each cycle (reads above). D-MOTO on its launcher (`com.qqlabs.minimalistlauncher`). `ACTION_DISCONNECT` sent 20 s after SSL in each cycle.

| Cycle | `createGroup SUCCESS` | `is already up from` | `group address` label | `advertising WPP over TCP at` | `not advertising` | `WppTcpServer: connection from` | route | start marker to SSL | phone trying / connected / nostore / TCP fail / BSSID / pnf |
|---|---|---|---|---|---|---|---|---|---|
| c1 | 1 | 0 | `stable=unproven (first reading ... 192.168.49.1 ...)` | 0 | 1 | 0 | `Connection accepted from` | 9.7 s | 0 / 0 / 2 / 0 / 0 / 0 |
| c2 | 1 | 0 | `stable=yes (name, BSSID and address repeat)` (count of `stable=yes` line 1) | 1, at `192.168.49.1:5299` | 0 | 0 | `Connection accepted from` | 8.5 s | 1 / 0 / 1 / 0 / 0 / 0 |
| c3 | 1 | 0 | `stable=yes` | 0 | 0 | 1 | TCP | 19.9 s | 1 / 1 / 0 / 0 / 0 / 2 |

- c1: no dial, so the "this dial reached" condition is recorded as **no dial** (`WppTcpServer: connection from` 0); `stamp P1-1` 0.
- All three: `needs this head unit forgotten` 0; `never opened the Android Auto channel on radio [` 0; `stamp` 0.
- c3 detail: `14:06:33.158 WppTcpServer: connection from 192.168.49.180`; `14:06:33.533 SSL handshake complete`. The 19.9 s came from the phone's first attempt: `14:06:28.205 GH.WPP.TCP: Restarting WPP over TCP, connection failure reason: NETWORK_UNAVAILABLE_NETWORK_NOT_FOUND`, then `14:06:33.237 Trying to start WPP on TCP with configuration ... ssid=DIRECT-74-POCOX3NFC`, `14:06:33.815 WPP on TCP connected to the WiFi network`. After the dial the session formed 0.4 s later. Round 1 reference times were c1 13.5 s, c2 10.4 s, c3 5.1 s.
- D-MOTO did not dial in c1.

## Anything the brief did not ask about

- **D-MOTO was never on D-HU's group in Stage 1** (the operator asked): it sat on the home network (192.168.1.5) with no P2P group and Bluetooth off, and every session in H2 to H4 came from `POCO X3 NFC` at 192.168.49.136.
- The group IP grade, deliveries per group (4 to 6 per capture) and the `StationStandDown: ` count (1 in each H1 capture) behaved as the brief expects.
- **c3's 19.9 s** is phone-side: the phone's own scan reported the network not found 5 s before it dialled, then it dialled and the session formed in 0.4 s. Not an app delay.
- **Gearhead on D-MOTO is 17.9.664004-release**; the strings `WPP on TCP connected to the WiFi network` and `Trying to start WPP on TCP with configuration` both printed.
- **Banner display vs stamp:** H3 set the stamp but its window shows 0 `showing the connection issue banner` lines; H4 shows 1. The stamp is the graded state; display timing follows the home screen.
- **D-MOTO Bluetooth self-reverted** between the operator's Stage 1 pre-flight state and H2-c1 (ON at 13:54:39 after I disabled it earlier).
- The rig-toolkit symlink `hur-wifi-test-scripts` named by the round skill is not present under `StudioProjects/`.
