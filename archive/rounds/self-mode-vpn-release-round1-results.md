# self-mode-vpn-release, round 1 results

**Candidate:** `fork/fix/self-mode-vpn-release` @ `2a91ee336f64aa37fd4f9babb35f61a842380c26`       **Baseline:** `origin/main` @ `145a0c762f0a87386ca63b54518ea5e861b290be`
**APK md5:** candidate `50f8137b85adf64b917c8e9274c70da0` / baseline `3100817484e47e9a6418753308e92ed1`
**Unit:** D-POCO (POCO X3 NFC, Android 15, API 35, Android Auto 17.9.664004-release) for the 5277 route; D-HU (UNISOC MT50, Android 14, API 34, Android Auto 17.3.662864-release) for the legacy route. D-MOTO not used.
**Date:** 2026-10-07
**Evidence:** release `rig-evidence-self-mode-vpn-release`, asset `self-mode-vpn-release-round1-captures.zip`, sha256 `3563f7cffe9ac1089cd863d3e505b963664b018fd62c40b28032aa105eee4628` (APKs and this file not included; the APKs rebuild from the two SHAs).

## Setup notes

**Round-level reading, in one place**

- R4 (the point of the round, release on the legacy route) is a PASS. The release fired once, 512 ms before SSL, and the session ran 4 min 10 s afterwards with 0 `AapService: Disconnected.` and 49 render lines.
- R2 (no tun on the 5277 route) is a PASS.
- R5P and R5L are graded FAIL on one literal condition, `AapService: Disconnected.` = 0. The baseline shows the same disconnect (R5A), so the cause is the drop lever, not the candidate. Details under R5P. The operator may regrade.
- R4 is graded PASS with one literal miss disclosed (a Gearhead string present on the baseline too). Details under R4. The operator may regrade.
- R2M (optional, D-MOTO) was not run.

**Unit tests and build gate (R0)**

- Worktrees: candidate `../ohu-svr-cand` at `2a91ee33` (new worktree; `local.properties` copied from the main checkout because the build refused without `sdk.dir`); baseline `../ohu-1063-B`, an existing worktree already at `145a0c76`, reused.
- Built with `GRADLE_OPTS=-Dorg.gradle.workers.max=2` through `build_hur.sh` (the brief's `--max-workers=2`). Host waited below 70C before each build (`rig_thermal.sh wait 70`).
- Unit tests on the candidate: 2744 tests, 0 skipped, 0 failures, 0 errors, counted from the JUnit XML. `TEST-...SelfLaunchRoutePolicyTest.xml` and `TEST-...DummyVpnPolicyTest.xml` both present.
- DEX count of `SelfLaunchRoutePolicy`: candidate 4, baseline 0. md5s differ.

**Pre-flight** (`rig_preflight.sh D_POCO:wifi,bt D_HU:wifi,bt`, before the build)

| ROLE | ADB | WIFI | BT | SCREEN | BT-PROFILES |
|---|---|---|---|---|---|
| D_POCO | ok | 1 | 1 | Awake | HFP:none A2DP:none |
| D_HU | ok | 1 | 1 | Awake | HFP:none A2DP:none |

PREFLIGHT OK. No stray logcat, rig lock free.

**Quirk files read:** `rig-quirks/topics/tooling.md`, `rig-quirks/units/D-HU.md`, `rig-quirks/units/D-POCO.md`, `rig-quirks/units/D-MOTO.md`.

**Scripts used** (all in `hur-wifi-test-scripts/`): `build_hur.sh`, `run_unit_tests.sh`, `apk_identity.sh`, `rig_thermal.sh`, `rig_preflight.sh`, `rig_devices.sh`. **Scripts added this round, left in place:** `svr_lib.sh` (the brief's section 5 helpers plus thermal helpers and fixes below), `svr_prepare.sh` (builds, unit tests, DEX check), `svr_r0b.sh` (calibration), `svr_runs.sh` (one session script for R1, R5A, R2, R3P, R5P, R1L, R4, R3L, R5L), `svr_mkprefs.py` and `svr_apply.sh` (build the round's `settings.xml` from a backup and install it, as root on D-HU and through `run-as` on D-POCO, with read-back).

**Errors found in the brief's helpers (all fixed in `svr_lib.sh`)**

1. `port5277()` greps `:1495`. Port 5277 is `0x149D`. With the brief's value D-POCO read `port5277=0` both before and after the operator's toggle, while `/proc/net/tcp6` after the toggle showed `:149D ... 0A` (listening). Whether the server was down before the toggle was never read with the correct port, so the hand step may have been unnecessary. Corrected to `:149D`.
2. `tuns()` greps `': tun'`. On D-HU it matches `tunl0` (a kernel tunnel device), so it reads 1 with no VPN. Changed to `': tun[0-9]'`.
3. On D-POCO `ip -o link show` prints `request send failed: Permission denied` (no netlink for the shell on Android 15), so `tuns()` read 0 whatever the state. This voided the first R1 attempt's tun reads. Replaced by `ls /sys/class/net | grep -cE '^tun[0-9]'`, which works on both units and was checked against a live `tun0`.
4. **The internet probe is only valid while the app's UID is not background-restricted.** With the app stopped, `run-as $PKG ping` fails with 100% loss while online (netpolicy `blocked=APP_BACKGROUND`, `effective=APP_BACKGROUND`). R0b as written (app stopped) therefore cannot calibrate the probe. R0b was run with the app in front (`MainActivity`, `auto-start-self-mode` false for that step). Every run now logs `uidstate` (`procState`, `effective`) beside the probe; all probes in graded runs read `procState=TOP effective=NONE` (R1's probe predates the helper; its state was read directly afterwards, same values, with `tun0` up and the app-UID ping at 100% loss).

**Deviations from the brief**

- **`connection-modes` set to `self` only on both units.** The brief does not mention it. D-HU had `wifi,self` with `wifi-connection-mode=3`, which could arm Native AA and form a P2P group beside the station during a Self Mode run; D-POCO had `usb`. Both were written as `<set name="connection-modes"><string>self</string></set>`. Restored from backup at the end.
- **Settings delta against the fresh backups**, written by `svr_mkprefs.py` (element-scoped, `connection-modes` set replaced whole): added `auto-start-self-mode=true`, `auto-connect-delay-seconds=0`, `log-level=2`, `connection-modes={self}`; on D-HU also `native-poke-all-paired=false`; deleted `auto-connect-last-session`, `auto-connect-single-usb`, `keep-dummy-vpn-during-session`, `video-profile-starvation-cap` (the last was absent). Audio keys untouched and read back unchanged on D-HU: `audio-latency-multiplier=8`, `audio-queue-capacity=20`, `enable-audio-sink=false`, `use-aac-audio=false` (not present on D-POCO). Each file read back identical to the file written (`cmp`). D-HU `shared_prefs` and `settings.xml` are `u0_a176:u0_a176`, correct for the app. At the end both units' `settings.xml` were restored from the backups and diffed identical.
- **D-POCO's `ACTIVATE_VPN` appop read `ignore`** at the start; set to `allow` before the baseline run as the brief says, and set back to `ignore` at the end. D-HU read `allow`.
- **Hand steps:** (1) D-POCO "Start head unit server" toggle (`:149D` was not listening at first). (2) D-HU `Pegue Cdesta` tap: the station had autojoin disabled (`Ignoring auto join disabled SSID`). It needed the tap twice; after the second tap the operator also switched on WiFi auto connect, and from then on an offline and join cycle rejoined by itself (`waitnet=0` every time). The earlier loss was the autojoin flag, not the WiFi toggle.
- **`ACTION_LOG_MARKER` needed no `allow-external-configuration`**: the candidate's `AutomationCommandPolicy.CONFIGURING` does not list it, and every `AutomationMarker:` line landed.
- **R1 first attempt void.** `adbd` restarted on D-POCO at 10:06:06 (`UsbDeviceManager: Setting USB config to midi`) during the capture, so the device was absent from `adb devices` for a few seconds and the script logged `adb: device not found`. D-MOTO appeared in `adb devices` at about that time (cause not established). Together with the netlink problem (item 3) it counted as a setup failure; R1 was re-run once and graded from the second attempt. The void capture is kept as `void1-R1.txt`.
- **R0b first attempts void**, both for setup reasons: D-HU station not joined (`P_on=0`, `waitnet` timeout), and D-POCO with the app stopped (item 4). Both were re-run once and pass.
- **Windows.** Every count is windowed by the run's own capture file (one capture per run, started after `logcat -G 16M; logcat -c`), and teardown and throughput counts by `AutomationMarker:` windows. D-HU and D-POCO both honoured `logcat -c`.
- **Thermal.** `rig_thermal.sh wait 75` gated each run (`th_gate`) and `watch` logged every 20 s. Per-run package maximum: R1 70C, R5A 73C, R2 69C, R3P 64C, R5P 62C, R1L 63C, R4 67C, R3L 63C, R5L 64C; `throttle_pkg` did not increase in any run. R0b on D-HU read 81C at its peak (below the 90C re-run line).
- **Unit order.** Stage A ran with D-HU's app stopped and its Bluetooth off. Stage B ran with D-POCO offline (airplane mode, WiFi, data and Bluetooth off), app stopped. D-HU's `native-poke-all-paired` was `false` in stage B.
- **Stage C / R2M not run.** It is optional and needs an operator toggle on D-MOTO.
- **Capture integrity.** The last line of each capture agrees with the kill time to within about 1 s (script logs record it). No stray logcat remained after the round (`ps` checked).

**Calibration (R0b)**

| Unit | API | Android Auto `versionName` | `port5277` | `P_on` | `P_off` | Result |
|---|---|---|---|---|---|---|
| D-POCO | 35 | 17.9.664004-release | 1 | 12 | 0 | PASS |
| D-HU | 34 | 17.3.662864-release | n/a | 12 | 0 | PASS |

No unit is near API 30. The R3 branch taken on both units was the same-process one (`MainActivity finishing, resetting auto-start flag.` was logged, `finish_lines=1`), so no `force-stop` was needed.

## R0 - Gate

**PASS**

- 2744 tests, 0 failures, 0 errors; `SelfLaunchRoutePolicyTest` and `DummyVpnPolicyTest` present.
- md5 candidate `50f8137b...` vs baseline `31008174...`, different.
- DEX `SelfLaunchRoutePolicy`: candidate 4, baseline 0.

## R0b - Unit calibration

**PASS**

(both units, second attempt each; see Setup notes for the two void first attempts)

- D-POCO: Android Auto 17.9.664004, API 35, `port5277=1`, `P_on=12`, `P_off=0`.
- D-HU: Android Auto 17.3.662864, API 34, `P_on=12`, `P_off=0`.

## R1 - Positive control, baseline, 5277 route (D-POCO)

**PASS**

(control reproduces the fault)

- Settings: as Setup notes. Commit read back `145a0c762f0a`. Radio state: offline (airplane mode, WiFi, data, Bluetooth off), `Active default network: none`, `tun` count 0 before launch. Discard-rule check: one re-run (void first attempt), the second clean.
- Decisive lines:
  - `10-07 10:07:40.522 ... DummyVpnService.startVpn | DummyVpnService: tun established (excludeSelf=false)`, before
  - `10-07 10:07:40.955 ... SelfMode: AA 17.4+ detected. Connecting directly to Headunit Server on 127.0.0.1:5277...`
  - `10-07 10:07:41.537 ... AapSslContext.performHandshake | SSL handshake complete.`
- Measurements: `tun established` 1; `Device is offline. Preparing Dummy VPN for Self Mode.` 1; `tun_at_ssl=1`; `tun_at_end=1`; `P=0`; `SSL handshake complete` 1; `AapService: Disconnected.` 0; `Head unit connected` 1; 27 `Throughput over` lines; `AapService: releasing the dummy VPN` 0. After the window, with the session still live: app UID `procState=TOP effective=NONE`, `tun0` present, app-UID ping 100% loss, so `P=0` is the tun and not background blocking.

## R5A - Baseline, online start, then drop and return, 5277 route (D-POCO)

**PASS**

(recording run; the brief gives it no pass or fail condition)

- Online start: `join; waitnet` gave `Active default network` present before launch.
- Record: `tun_at_ssl=0`, `P1=12`, `tun_at_end=0`, `P2=12`. `Device is offline. Preparing Dummy VPN for Self Mode.` 0; `VpnControl: Starting DummyVpnService (GitHub Build` 0; `DummyVpnService: tun established` 0; `SSL handshake complete` 1; `SelfMode: Installed AA version: ` 1 (Self Mode did not start again); `AapService: Disconnected.` 1.
- No tun appeared, so the 20-line paste the brief asks for is not needed.
- What `main` does on the drop: `10-07 10:11:50.097 AutomationMarker: R5A-drop`, then `10-07 10:11:50.994 AapService.stopDummyVpn | AapService: releasing the dummy VPN (owner=SELF_MODE, reason=SESSION_ENDED)`, `VpnControl: DummyVpnService was not running` and `10-07 10:11:51.019 AapService: Disconnected.` The airplane-mode drop ends the local session 0.9 s after it, and nothing restarts it. After the rejoin, `P2=12` simply because no tun exists, so on this rig `main` shows no fault on an online start.

## R2 - Fix, 5277 route (D-POCO)

**PASS**

- Settings: as Setup notes. Commit `2a91ee336f64`. Radio state: offline, `Active default network: none`, `tun` count 0 before launch. Discard-rule check: clean, one session.
- Decisive lines:
  - `HomeFragment: Device is offline; Android Auto 17.4+ connects over 127.0.0.1:5277, so no dummy VPN.` x1
  - `10-07 10:14:31.214 ... AapService.stopDummyVpn | AapService: releasing the dummy VPN (owner=SELF_MODE, reason=SELF_MODE_SESSION_LIVE)` with no tun behind it (the brief's pre-registered adopt-no-tun case; the tun count is the measure)
- Measurements: `Device is offline. Preparing Dummy VPN for Self Mode.` 0; `DummyVpnService: tun established` 0; `tun_at_ssl=0`; `tun_at_end=0`; `SSL handshake complete` 1; `AapService: Disconnected.` 0; `Throughput over` lines between `R2-join` and `R2-end`: 20, all with `rendered` above 0; `Head unit connected` 1; the 8 Gearhead teardown strings 0 each between `R2-start` and `R2-end`; `P=12` (R1 control `P=0`); `SelfMode: nothing connected within` 0.

## R3P - Fix, reconnect, 5277 route (D-POCO)

**PASS**

- Branch: same process (`MainActivity finishing, resetting auto-start flag.` = 1, no `force-stop`).
- Decisive lines: `10-07 10:16:50.733 SSL handshake complete`, `10-07 10:17:11.303 AapService: Disconnected.` (the exit), `10-07 10:17:19.126 AutomationMarker: R3P-relaunch`, `10-07 10:17:20.482 SSL handshake complete`.
- Measurements: `SSL handshake complete` 2, the second after `R3P-relaunch`; 5 `Throughput over` lines with `rendered` above 0 after the relaunch; `DummyVpnService: tun established` 0; `tun_at_end=0`; `SelfMode: nothing connected within` 0; `Head unit connected` 2. `Device is offline` x2 is the new no-VPN route line, once per launch.

## R5P - Fix, online start, 5277 route (D-POCO)

**FAIL**

(one literal condition, brief defect; see below)

- Measurements: `Device is offline. Preparing Dummy VPN for Self Mode.` 0 and `HomeFragment: Device is offline; Android Auto 17.4+` 0; `DummyVpnService: tun established` 0; `tun_at_mid=0`, `tun_at_end=0`; `SSL handshake complete` 1; `P=12`, `P2=12`; `Head unit connected` 1.
- Condition not met: `AapService: Disconnected.` = 0. Count is 1: `10-07 10:18:54.169 AutomationMarker: R5P-drop`, `10-07 10:18:54.904 AapService: Disconnected. wasPlayingBeforeDisconnect=false` (0.74 s later).
- Why this is the brief and not the candidate: the baseline's R5A ended the session 0.9 s after its drop in the same way (`reason=SESSION_ENDED`). The drop lever, airplane mode plus `svc` disables on a unit that is its own head unit, ends the local session whatever build runs. With this lever `Disconnected.` = 0 cannot be met. The conditions that distinguish the arms (no tun at any read, `P` and `P2` at 12) hold, and the candidate matches the baseline's behaviour. Regrade is the operator's call.
- `SelfMode: Installed AA version: ` appears twice (10:18:17.179 and 10:18:18.045), both before the session and followed by a single `AA 17.4+ detected`; the first is the candidate's new `installedPath` check in `HomeFragment`. Not a restart.

## R1L - Positive control, baseline, legacy route (D-HU)

**PASS**

(control reproduces the fault)

- Commit `145a0c762f0a`. Offline start, `tun` count 0 before launch.
- Decisive lines (all 10-07): `10:23:42.949 DummyVpnService: tun established`, `10:23:43 SelfMode: Launching AA Wireless Startup via Activity...`, `10:23:44.436 WirelessServer: Incoming connection detected from`, `10:23:45.020 SSL handshake complete`.
- Measurements: `tun established` 1, before the launch; `Incoming connection detected` 1; `SSL handshake complete` 1; `tun_at_ssl=1`; `tun_at_end=1`; `P=0` (app UID `procState=TOP effective=NONE`); `AapService: Disconnected.` 0; `Launch projection 127.0.0.1 5288` 1; `AapService: releasing the dummy VPN` 0. The Gearhead string `NO_SOCKET_DISCONNECT` appears once at 10:23:44.497, 61 ms after the incoming connection (relevant to R4).

## R4 - Fix, release at connect, legacy route (D-HU)

**PASS**

(one literal miss disclosed below; regrade is the operator's call)

- Commit `2a91ee336f64`. Offline start, `tun` count 0 before launch. Discard-rule check: clean, one session; R4 was run once.
- Timestamps (10-07): `10:26:51.029 tun established`; `10:26:51.557 SelfMode: Launching AA Wireless Startup via Activity...`; `10:26:51.775 Launch projection 127.0.0.1 5288`; `10:26:52.096 WirelessServer: Incoming connection detected from`; `10:26:52.341 AapService: releasing the dummy VPN (owner=SELF_MODE, reason=SELF_MODE_SESSION_LIVE)`; `10:26:52.381 Dummy VPN stopped`; `10:26:52.853 SSL handshake complete`. `D_rel_ssl` = +512 ms (SSL after the release).
- Measurements: release count (both literals) 1; `Dummy VPN stopped` 1, after the release line; `tun_at_ssl=0`; `tun_at_probe=0`; `SSL handshake complete` 1; `AapService: Disconnected.` 0 from `R4-start` to `R4-end`; `Throughput over` lines between `R4-ssl` and `R4-end`: 49, all with `rendered` above 0; `Launch projection 127.0.0.1 5288` 1; `SelfMode: nothing connected within` 0; `P=12` (R1L control `P=0`). The 30 s after the release line contain 0 `Disconnected.` and 0 Gearhead teardown strings, so the H3 FAIL does not apply.
- Literal miss: the PASS list asks for every Gearhead teardown string to be 0 between `R4-start` and `R4-end`. One is 1: `10-07 10:26:52.185 GH.ConnLoggerV2 ... WIRELESS_WIFI_NETWORK_STATE_CHANGED_DISCONNECTED_NO_SOCKET_DISCONNECT`. It sits 156 ms before the release and 89 ms after the incoming connection, and the baseline R1L (which never releases) logs the same string 61 ms after its incoming connection. It is launch-time noise in both arms, not an effect of the release. The brief itself calls these strings weak evidence and rests the session verdict on `Disconnected.` and `Throughput over`, which are clean, so R4 is graded PASS.
- Late in the hold the render rate fell to 6 fps (`rendered=32` over 5 s at 10:31:02) with 0 dropped; the earlier lines run at about 20 to 30 fps. Not graded.

## R3L - Fix, reconnect, legacy route (D-HU)

**PASS**

- Branch: same process (`finish_lines=1`).
- Decisive lines (10-07): `10:32:13.676 tun established`, `10:32:15.095 releasing the dummy VPN (... SELF_MODE_SESSION_LIVE)`, `10:32:15.596 SSL handshake complete`, `10:32:36.230 AapService: Disconnected.` (the exit), `10:32:41.755 R3L-relaunch`, `10:32:42.923 tun established`, `10:32:43.974 releasing the dummy VPN (... SELF_MODE_SESSION_LIVE)`, `10:32:44.328 SSL handshake complete`.
- Measurements: `tun established` 2, the second after `R3L-relaunch`; release count 2, the second after `R3L-relaunch`; `Dummy VPN stopped` 2; `SSL handshake complete` 2; 4 `Throughput over` lines with `rendered` above 0 after the relaunch; `AapService: Disconnected.` 1 between `R3L-exit` and `R3L-relaunch` and 0 after; `tun_at_end=0`; `Launch projection 127.0.0.1 5288` 2; `SelfMode: nothing connected within` 0.

## R5L - Fix, online start, legacy route (D-HU)

**FAIL**

(one literal condition, same brief defect as R5P)

- Measurements: both `Device is offline` lines 0; `DummyVpnService: tun established` 0; `tun_at_mid=0`, `tun_at_end=0`; `SSL handshake complete` 1; `P=12`, `P2=12`; `Launch projection 127.0.0.1 5288` 1; `SelfMode: Launching AA Wireless Startup via Activity...` 1. A release line is present (`10:33:44.780 ... SELF_MODE_SESSION_LIVE`), the adopt-with-no-tun case the brief says is not a FAIL.
- Condition not met: `AapService: Disconnected.` = 0. Count is 1: `10:34:21.741 R5L-drop`, `10:34:22.533 AapService: Disconnected.` (0.79 s later), the same drop-lever effect as R5A and R5P. Not attributable to the candidate; regrade is the operator's call.

## R2M - Fix, 5277 route, second unit (D-MOTO)

**UNTESTABLE**

Optional run, not attempted: it needs an operator toggle on D-MOTO and a person present for the whole run. D-MOTO was in `adb devices` but was not touched.

## Reported against the brief's section 9

1. **R4:** release count 1; `D_rel_ssl` +512 ms; `AapService: Disconnected.` 0 over the 300 s hold (`R4-start` 10:26:49.552 to `R4-end` 10:31:04.499); `Throughput over` 49 (all `rendered` above 0); `P=12`.
2. **R2 against R1:** `tun_at_ssl` 0 vs 1; `tun_at_end` 0 vs 1; `P` 12 vs 0.
3. **R4 against R1L:** `tun_at_probe` 0 vs 1 (R1L `tun_at_end` 1); `P` 12 vs 0.
4. **R5A against R5P:** `tun_at_ssl` (R5A) 0 and `tun_at_mid` (R5P) 0; `tun_at_end` 0 vs 0; `P1`/`P` 12 vs 12; `P2` 12 vs 12; `VpnControl: Starting DummyVpnService (GitHub Build` 0 vs 0. R5A found no tun, so no 20-line paste.

## Anything the brief did not ask about

- **The brief's drop lever cannot keep a Self Mode session alive.** On a unit that is its own head unit, airplane mode plus `svc wifi/data disable` ends the session in under 1 s (R5A, R5P, R5L all ended 0.74 to 0.9 s after the drop, none restarted). A drop test that wants `Disconnected.` = 0 needs a lever that removes only the upstream network, which this rig has not shown. Worth fixing in the brief before another round grades on it.
- **On an online start `main` already starts no tun** (R5A: 0 `Starting DummyVpnService`, 0 `tun established`), so the reporter's Situation A does not reproduce on this rig through the Self Mode path alone; the fault needs an offline launch followed by a late network (R1, R1L).
- **D-POCO probes and `tun` reads need three fixes** that the next Self Mode round should inherit from `svr_lib.sh`: the `0x149D` port, the `/sys/class/net` tun read, and a foreground app for the probe (a stopped app is `APP_BACKGROUND` blocked and reads 0 replies with no VPN at all).
- **D-HU's WiFi station stays joined across offline and join cycles only while "WiFi auto connect" is on** for the saved network. With it off, `svc wifi enable` never rejoins and there is no adb lever (the `Ignoring auto join disabled SSID` line is the tell).
- **A USB re-enumeration dropped D-POCO off adb for a few seconds during the first R1 attempt** (`UsbDeviceManager: Setting USB config to midi`, adbd restarted). It did not recur in the other eight sessions. Cause not established.
- **The candidate's `installedPath` check logs a second `SelfMode: Installed AA version:` line** (R2, R3P, R5P) before the launch. It is the intended commit-2 behaviour and is harmless, but any grep that counts that line to detect a restart reads 2 on the candidate and 1 on the baseline.
- **Release line printed with no tun** on the 5277 route and on online starts (R2, R3P, R5P, R5L): `SelfLauncherManager.adoptDummyVpn` adopts the owner whenever the flavor has a VPN, as the brief predicted. The next lines are `VpnControl: Stopping DummyVpnService (GitHub Build)` and `VpnControl: DummyVpnService was not running`.
