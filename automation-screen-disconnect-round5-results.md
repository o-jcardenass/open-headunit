# automation-screen-disconnect, round 5 results

**Candidate:** fork/feat/automation-screen-disconnect @ 512343cad8601e3970eb652e9f6a3e95b73450d8 (tree 9cd49b3fbe4b7caa2569db2c6fb0b4445f960951)   **Baseline:** none (one arm)
**APK md5:** C 2f1b67a001c3861ef5d8cf076583012c
**Unit:** D-POCO (POCO X3 NFC, API 35) as head unit, D-MOTO (Motorola edge 30 neo) as phone, Gearhead 18.1.164344-release.daily; D-HU Bluetooth held off
**Date:** 2026-10-29

## Setup notes

- Session started 2026-10-29. Operator answered "all of them in the proposed orders" for commit b2ff181d: running the 4 briefs that commit touches, in README Queue order: automation-screen-disconnect r5 (#2), zj-bt-unix-socket r1 (#12), pr-train-20261029 r1 (#14), native-bt-broker-esp32 r2 (#15).
- Quirk files read: topics/tooling.md, units/D-POCO.md, units/D-MOTO.md, units/D-HU.md (head).
- Opening pre-flight (rig_preflight.sh D_POCO D_MOTO D_HU D_SAM D_HP; role names use underscores, brief's D-POCO form is invalid for the script):
  D_POCO wifi1 bt1 Awake | D_MOTO wifi1 bt0 Awake | D_HU wifi1 bt1 Awake HFP:XX:..:97:64 | D_SAM wifi1 bt1 Asleep | D_HP wifi1 bt1 Awake. PREFLIGHT OK, no strays, lock free.
- Builds: one batch (build-batch.sh, worktree round-builds__scratch__512343ca__20261029) for all 4 rounds. C md5 2f1b67a001c3861ef5d8cf076583012c (asd r5). Other md5s: zj C a0fe0e2eef941658228d1e8ade87a6a7; pr-train T 2dda80522bfb9b5453ba0a6e7bdf8bcd, B 9a077f29fb3ba545285fc028f69cba0a; esp32 C e4915024f9b13ec04c694872b16a66c5.
- Gate: fork SHA and tree equal brief for all four branches. Tree/dirty of worktree not re-checked after build beyond HEAD==SHA.
- Helper deviation: round4 runlocked.sh sources env4*.sh; wrote scripts/run5.sh (sources ptr_lib.sh, r5_lib.sh, r5keys.sh, p.env under /tmp/ohu-rig.lock, plus a 20 s thermal sampler into $RUN.thermal so th_report works).
- R0: install OK (settings delta pre/post install empty). R0.state commit=512343cad860 -> R0 PASS.
- P: moto-bonds count was 0 on first read because D-MOTO Bluetooth was OFF (MOTO_BT0 disabled): bonded list empty while off. Enabled D-MOTO BT (p_radios does it anyway), re-read: POCO X3 NFC count 1, D-POCO sees 'motorola edge 30 neo' count 1. Pair bonded.
- P: D-POCO isGroupOwner was true; headunit://exit + force-stop, re-read false. D-MOTO groupFormed false.
- P: Gearhead versionName 18.1.164344-release.daily on D-MOTO; KG_POCO=false, TIMEOUT_POCO=1800000, MOTO_WIFI0 enabled, MOTO_BT0 disabled (restore to off in X).
- D-POCO bonded list also holds Navegadortz3 (D-SAM) and DR-82645; no Navegadortz2.
- Q: Q.summary says listening=1, but the ACTIVELY LISTENING line is in the capture at 17:25:33.792, 46 s before Q-quiet (17:26:19). Brief bug: arm() waitfor only sees lines newer than its own start, and init's waitfor consumed the window in which the line printed. Graded condition 1 from the capture (n hwin Q-start Q-quiet of NATIVE init = 1, listening line before Q-quiet). Same race will recur in N1/N2/B; will grade the same way.
- Q: D-HU Bluetooth read off after dhu_bt_off, but Q.dhu-after-quiet reads enabled: true (self-revert inside the 60 s window, as topics/bt.md records). Quiet counts still 0.
- grade scripts: scripts/grade_q.sh (sources libs; run via bash -c, run5.sh swallowed output because flock subshell quoting).
- run5.sh first version leaked ptr_lib th_watch (holds lock fd) after Q; N1 attempt 1 lock-timed out (exit 1, no capture, no device action). Killed stray by pid, run5.sh now kills THPID. N1 re-run.
- N1 grader: my c4b greped `name motorola...` without the quotes; log holds `by name "motorola edge 30 neo" resolved to A0:46:5A:97:E4:95` as the brief's `name "$MOTO_NAME"`. Not a defect in the run.
- N1: phone Android Auto `Creating rfcomm socket for device: ` was 0; condition 7 met by `State changed to CONNECTING_RFCOMM` (phone) plus `Starting manager of type: TCP`. Phone clock reads 1.3 s behind HU (N1-cmd marker HU 17:35:12.428, phone 17:35:11.126), so the phone line at 12.933 is ~14.2 s HU-time, after connect() at 13.219.
- N1 dhu-after-quiet: enabled: true (D-HU BT self-reverted during quiet); quiet counts 0.
- B not run. x_restore's trailing logcat count printed 1 (grep matched its own ps); separate check shows 0.

## R0 Build, install and APK identity

**PASS**

`R0.state` reports `"commit":"512343cad860"`, `wifiMode":"NATIVE"`. Install over the existing build left `settings.xml` unchanged (pre/post diff empty). Build ran in one batch worktree shared with the other three queued rounds.

## P Precheck

**PASS**

Bonds present both ways (`moto-bonds.txt` count of `POCO X3 NFC` 1 after D-MOTO Bluetooth was switched on; 0 while it was off). D-POCO `isGroupOwner` true on first read, cleared by `headunit://exit` plus force-stop, then false. `KG_POCO=false`, `TIMEOUT_POCO=1800000`. `MOTO_BT=A0:46:5A:97:E4:95`, `MOTO_NAME=motorola edge 30 neo`.

## Q Control: the start state with no command

**PASS**

- `quiet=held`, `wrapped=no`, `MATCH! Starting AapService` 0, `Q.dhu-bt` off, `Q.dhu-after-quiet` `enabled: true`.
- Head unit, `Q-quiet` (17:26:19.804) to `Q-session` (17:29:44.816), each 0: `SSL handshake complete`, `NativeAA: Connection accepted from `, `WirelessServer: Incoming connection detected`, `NativeAA: Calling socket.connect() for `, `NativeAA: Attempting manual poke to `, `NativeAA: Attempting active poke to device: `.
- Phone, same window, each 0: `Creating rfcomm socket for device: `, `CONNECTING_RFCOMM`, `Starting manager of type: `.
- `createGroup SUCCESS` 1, `gets the stand-in HFP record, because` 1, phone `State changed to ` 0.
- Condition 1: `listening=1` in `Q.summary` is a harness race (see Setup notes); `17:25:33.792 NativeAA: ACTIVELY LISTENING on Android Auto UUID` is 46 s before `Q-quiet`, and `WifiLauncher: Initializing WiFi Mode: NATIVE` is 1.

## N1 Connect by name

**PASS**

| Quantity | Value |
|---|---|
| quiet window (HU 6 counts, phone 3 counts) | all 0 |
| `AapService: connect by "motorola edge 30 neo" resolved to A0:46:5A:97:E4:95` | 17:35:13.197 (`refused: ` 0) |
| `NativeAA: Driver selected: A0:46:5A:97:E4:95 (explicit pick)` | 17:35:13.198 |
| `NativeAA: Calling socket.connect() for motorola edge 30 neo` | 17:35:13.219, 791 ms after `N1-cmd` (17:35:12.428) |
| guard line / hands-free-up / Not poking | 0 / 0 / 0 |
| phone `Starting manager of type: TCP`, `State changed to CONNECTING_RFCOMM` | phone clock 17:35:12.933 (phone is 1.3 s behind the HU), after the poke |
| `SSL handshake complete` | 17:35:22.785, 10357 ms after the command |
| first `Throughput over ` / `rend` | 17:35:29.598 / 3 |
| `Successfully poked motorola edge 30 neo via HFP-AG` | 17:35:13.945 |
| wake lock / WiFi button route / active poke | 0 / 0 / 0 |
| `raising the projection by DIRECT` | 17:35:22.808 |

Condition 7 met by `CONNECTING_RFCOMM` plus the `TCP` manager; `Creating rfcomm socket for device: ` was 0. Order check holds in all five timestamps. `Connection accepted from ` did not appear (gap NA); the phone came in over the group.

## N2 Connect by MAC

**PASS**

Quiet window counts all 0. `connect by MAC A0:46:5A:97:E4:95 resolved to A0:46:5A:97:E4:95` 17:38:37.591; `Driver selected` 17:38:37.592 (explicit); `Calling socket.connect()` 17:38:37.611 (789 ms after the command); phone `Starting manager of type: TCP` 17:38:37.421 phone clock; `SSL handshake complete` 17:38:47.981 (11159 ms); first `Throughput over ` 17:38:54.449; `rend` 3. Guard, hands-free-up, Not poking, wake lock, WiFi button, active poke all 0. The quiet window opened after N1's session ended and stayed silent.

## B Old poke

**UNTESTABLE** (trigger did not fire: N1 and N2 both gave `ssl=0`; not run, per the brief).

## X Restore

**PASS**

`settings.xml` restored (diff empty), keyguard setting `false`, screen timeout 1800000, D-HU Bluetooth on, D-MOTO Bluetooth back to off (`MOTO_BT0`) with WiFi enabled as at P, no logcat left. Candidate left installed on D-POCO.

## Anything the brief did not ask about

- D-MOTO never dialled D-POCO by itself in Q, N1 or N2 quiet windows (phone counts 0 in all three); after a poke it took the `Starting manager of type: TCP` route with `CONNECTING_RFCOMM`, not a logged `Creating rfcomm socket`.
- D-HU's Bluetooth self-reverted on inside every 60 s quiet window; it did not break any quiet window.
- Phone clock is 1.3 s behind the HU clock, so cross-device ordering needs that offset.
