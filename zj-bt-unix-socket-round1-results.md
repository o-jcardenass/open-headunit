# zj-bt-unix-socket, round 1 results

**Candidate:** fork/feat/zj-bt-unix-socket @ 390b6931e1a6f44959f411f81e09a25c2af34fd7 (tree 00003772e4dc902bb260f3014957a1262dd9d7f3)   **Baseline:** none
**APK md5:** C a0fe0e2eef941658228d1e8ade87a6a7 (live APK pulled and hashed equal; `ZjSocketProbe` 18, `RawUnixSocketCarrier` 0; `commit` 390b6931e1a6)
**Unit:** D-HU (MT50, Android 14, root adbd, SELinux Permissive) with a `toybox nc` stand-in on /dev/socket/zj_bt_socket; D-POCO (R1 only)
**Date:** 2026-10-29

## Setup notes

- Pre-flight: first run FAILED on an idle Gradle daemon (java, gradle user) left by the build batch; rig_cleanup.sh kill, re-run PREFLIGHT OK (D_HU wifi1 bt1, D_POCO wifi1 bt1). D-MOTO BT disabled (read back enabled: false).
- Pre-flight 4: root already; rw.zlink.bt.type, rw.zj.bt.type, persist.zj.BTmac empty; /dev/rf_serial /dev/zj_bt_serial /dev/auto_serial socket file absent; 3152 LISTEN 0; mtcser absent. getenforce=Permissive. D-POCO isGroupOwner false, focus launcher3.
- Pre-flight 6: STUB_UP, socket 'srw-rw-rw- root root /dev/socket/zj_bt_socket'; after stub_down unix_entries=0 and the socket FILE STAYS (R4b part 1 can run; R5 waits on 'connect ... failed ('). Then rm -f.
- D-HU shared_prefs owner u0_a176 mode drwxrwx--x (not root-owned). Recorded settings: native-poke-all-paired=true, native-driver-selection-mode=1, native-ap-transport=0, wifi-direct-stable-identity=true, bt-address=11:46:03:10:33:59, video-profile-starvation-cap=false, native-poke-bt-macs and native-preferred-device-mac empty.
- Helpers: extracted the brief's blocks to scripts/; wrote scripts/runzj.sh (lock + lib.sh + kills THPID/PHLOOP/STUBPID/HUPID/PHPID), pre.sh, ident.sh, plus grade scripts. R1 order: R1, R4a, R2+R3, R4b, R5, cleanup.
- R1 (zj): D-POCO not bonded to D-HU (D-POCO bonded: Magnetic Speaker, motorola edge 30 neo, DR-82645, then Navegadortz3 earlier); no session possible. Host killed the run at ~17:53 after the waits (grep polling a 60 MB capture made 'waitn 120' take >10 min). Evidence: SSL 0, ZJ 0, ZBT 0, bridge candidate 0, ACTIVELY LISTENING 1, MATCH 0, phone 'Creating rfcomm socket' 0. Verdict INCONCLUSIVE (rig) per R1 rule; re-run after operator pairs D-POCO with D-HU.
- waitn polls grep -c over the whole capture: with D-HU's ~60 MB/10 min capture each poll takes seconds, stretching waits. Watch run lengths.
- R4a exit=143 from cap_off wait on a killed logcat; capture complete (markers present).
- R2/R3: Handshake failed lines in R2 window: 3 (record only). Press verb reply ok:true. Post-press C3 came 5 ms after the prior closed line (press cut the 3 s wait, while an earlier handshake was in progress).
- R5: file stayed after stub_down so R5WAIT was the 'connect ... failed (' form. 'socket transport is on (null)' (switch, no property), as expected.
- R1 attempt 1 (no D-POCO bond) captures moved to attempt1/. R1 attempt 2 after operator paired D-POCO with D-HU (D-POCO bonded list now holds Navegadortz2).
- R1 attempt 3 after operator connected D-POCO to D-HU in Android Auto (attempt 2 showed D-HU poked D-MOTO (BT off) first; captures in attempt2/).
- BRIEF BUG (found by operator): r1.sh calls ph_on without ph_off, so PHLOOP is empty and 'wait $PHLOOP' becomes a bare 'wait' that blocks on the logcat jobs forever; D-POCO Bluetooth and WiFi were therefore never enabled in R1 attempts 2 and 3 (D-POCO radios stayed off, so attempts 2-3 prove nothing about the pair). Patched lib.sh ph_on to guard an empty PHLOOP; attempt 4 uses it.
- R1 attempt 4 PASS: 18:24:52.660 SSL. R1 run exit=143 = wait on killed logcat, capture complete.
- D-MOTO Bluetooth was off for the whole round (as at the opening pre-flight), so cleanup left it off; the brief's cleanup would have turned it on.

## R1 Control: ordinary hardware takes no ZJ path (D-POCO session)

**PASS**

Fourth attempt. Attempts 1 to 3 formed no session and are explained in Setup notes: D-POCO was not bonded to D-HU (attempt 1), D-POCO's Android Auto had no link to D-HU yet (attempt 2), and `ph_on` in the brief's `lib.sh` hung in a bare `wait` so D-POCO's radios were never switched on (attempts 2 and 3, found by the operator). Attempt 4 used the patched `ph_on`.

- `win R1 R1-start R1-end` (HU `18:24:43.163` to `18:25:09.762`): `NativeAA: [ZJ]` 0, `vendor Bluetooth bridge candidate detected` 0, `NativeAA: [ZBT]` 0, `ACTIVELY LISTENING on Android Auto UUID` 1, `MATCH! Starting AapService` 0, `Attempting active poke` 0.
- `R1-phone` `18:24:45.864`; `NativeAA: Connection accepted from POCO X3 NFC (DC:B7:2E:5E:4E:59)` `18:24:50.507`; `SSL handshake complete` `18:24:52.660`, 6.8 s after `R1-phone`.
- `Throughput over 5003ms: rendered=125 (24fps)` `18:24:59.402`; 3 windows with `rendered=` above 0.
- Phone: `Creating rfcomm socket for device: ` 1 in the window.

## R4a A marked unit with no socket file keeps today's refusal

**PASS**

T0 `17:55:38.685` 1; W `17:56:08.723` 1, 30.038 s after T0, with 7 `classified as refused` lines between; `NativeAA: [ZJ]` 0; `external Bluetooth module detected (` 1 at `17:56:08.748`, 0.025 s after W.

## R2 The route by probe, the carrier and the WiFi button

**PASS**

- T0 `17:56:47.153`; first refused `.174`; `[ZJ] probe ...: CONNECTS` `.183` on thread [48] (main is [2]); W2 `.185` (T0 to probe 0.030 s, probe to W2 0.002 s); W 0; `external Bluetooth module detected (` 0.
- `socket transport is on (rw.zlink.bt.type=extra)` `.210`; `Carrier started` `.215`; `Connected to ... (attempt #1)` `.220` (0.037 s from probe to C1); `[TX] Sending WifiVersionRequest (Type 4)` `.557`; `persist.zj.BTmac is not set` 1.
- Closed-to-next-Connected gaps: `17:57:06.014` to `17:57:09.020` = 3.006 s; `17:57:46.395` to `17:57:49.400` = 3.005 s; post-press C3 `17:57:27.738` 0.005 s after `17:57:27.733`'s closed line, 0.005 s against the 1.0 s bound.
- Press at `17:57:09.512` (verb reply `"ok":true`): `WiFi button on the Bluetooth module route: WAKE_PHONE` 1 (`.539`), `Manual poke requested` 1 (`.541`); `no module channel is open yet`, `poke without a device ignored`, `vendor daemon refused` all 0. `this route sends no wake` 1.
- All zero checks in condition 10 read 0 (active poke, `Calling socket.connect()`, `ACTIVELY LISTENING`, silent-radio warning, `Carrier stopped after `, `carrier stopped unexpectedly`, `connect ... failed (`, `MATCH!`). `NativeAA: Handshake failed` in R2: 3 (record only).

## R3 Stop and re-arm keep the positive answer

**PASS**

`Carrier stopped after 4 attempts.` `17:57:50.285`, 26 ms after `ACTION_STOP_WIRELESS` (`.259`); `Connected to` count before the stop 4, so N = 4. 0 `Connected to` between stop and `R3-rearm`. After the re-arm: `Carrier started` `17:57:57.267` (45 ms after `ACTION_START_WIRELESS`), T0 0, `[ZJ] probe ` 0, `[ZBT]` 0, first `Connected to` `(attempt #1)` 5 ms after, a second `(attempt #2)` `17:58:18.879`; `this route sends no wake` 1; `MATCH!` 0. R3-rearm to new C1 0.42 s.

## R4b A refusing socket file does not take the route; the WiFi button asks again

**PASS**

The stale socket file was present (`srw-rw-rw- ... /dev/socket/zj_bt_socket`). Part 1: T0 1 (`17:59:06.090`), W 1 (`17:59:36.114`), W2 0; `probe: REFUSED (IOException: Connection refused)` at `17:59:06.121` (after the first refused line) and at `17:59:36.122` (8 ms after W); `external Bluetooth module detected (` 1 at `17:59:36.138`; `Carrier started` 0, `socket transport is on` 0. Part 2: press `17:59:41.111`; `the vendor daemon refused 5s ago; asking it again.` 1; action `START_HANDSHAKE`; T0 `.144` (33 ms after the press); probe `CONNECTS` `.152` then W2 `.152`; W 0; `Carrier started` `.164`; `Connected to (attempt #1)` `.166`; blocked line 0 in the window; `MATCH!` 0.

## R5 The switch forces the route on an unmarked unit; the carrier survives a daemon restart

**PASS**

T0, probe, `[ZBT]`, `external Bluetooth module detected (` all 0. `socket transport is on (null)` `18:00:13.934` (326 ms after `ACTION_START_WIRELESS`, `.608`); `Carrier started` `.941`; `Connected to (attempt #1)` `.948` (7 ms). After `R5-kill` (`18:00:16.206`): `BT Handshake link closed.` `18:00:32.779`, then `connect to /dev/socket/zj_bt_socket failed (REFUSED: IOException: Connection refused); next try in 3000ms` `18:00:35.786` (1 line). `Carrier stopped after ` 0 before `R5-end` (it printed at `18:00:45.446`, after `R5-end`). `R5-restart` `18:00:40.955` to `Connected to (attempt #2)` `18:00:44.798` = 3.84 s. `MATCH!` 0.

## Cleanup

**PASS**

Socket file removed, `rw.zlink.bt.type` empty, D-POCO WiFi and Bluetooth on again, D-HU `settings.xml` restored (diff against the backup empty, owner 10176, mode 660), D-MOTO Bluetooth left off, no logcat processes left.

## Anything the brief did not ask about

- The brief's pre-flight does not require a D-POCO to D-HU bond or a first Android Auto connection; R1 needs both. Its `r1.sh` also calls `ph_on` with no `ph_off` first, so `wait $PHLOOP` waits for every background job and hangs (fixed in this round\'s copy of `lib.sh`: guard on an empty `PHLOOP`).
- `waitn` greps the whole capture each poll. D-HU's capture grows to about 60 MB in 10 min, so a 120 s wait stretched past 10 min; R1 was stopped by hand twice.
- R2's post-press connect came 5 ms after the previous closed line: the press landed while a handshake was running, so the 3 s wait did not apply.
- D-HU's `native-poke-all-paired` is true and `native-driver-selection-mode` 1 on the backup; with the property clear D-HU pokes every bonded phone, including D-MOTO (Bluetooth off) and D-POCO.
