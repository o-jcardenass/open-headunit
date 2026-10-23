# station-scan, round 1 results

**Candidate:** `probe/local-only-hotspot` @ `b887adeb` (reports `commit` `b887adebe185-dirty`; the dirty flag is the untracked `.WORKTREE-INFO` only)   **Baseline:** none (measurement round)
**APK md5:** `fc0297dfec89d66a800abfa55a16f31a` on D-POCO, D-HU and D-SAM (host file and on-device `base.apk` md5 match for D-HU and D-POCO; `apk_identity.sh` MATCH on all three)
**Unit:** D-POCO (POCO X3 NFC, Qualcomm, Android 15) and D-HU (UNISOC MT50, Android 14) as head units for R1, R1b, R3, R4; D-SAM (SM-T230, Android 4.4.2) for R5; D-MOTO (Android 14) the phone for every run
**Date:** 2026-10-15
**Evidence:** `station-scan-round1-captures.zip` on release `rig-evidence-station-scan`, asset `station-scan-round1-captures.zip` (8899993 bytes), sha256 `8cb589407c559fb7d5c3c7f81f1992655c764ed6e5a082def046bd0015e7aa6a`

## Summary table

| Run | Unit | Station as read | Window | Scans | Stalls within +-5 s of a scan | Underruns | Verdict |
|---|---|---|---|---|---|---|---|
| R0 | all three | n/a | n/a | n/a | n/a | n/a | PASS |
| R1b | D-POCO | joined to the house network (5745 MHz) | 600 s | 0 | 0 | 0 | PASS (measurement) |
| R1 | D-POCO | not connected | 600 s | 5 | 0 link-quiet, 0 audio; 5 of 5 coincide with a one-window fps dip (19 to 25 fps vs 27 to 30) | 0 | PASS (measurement) |
| R3 | D-POCO | not connected | 6 + 6 min | 2 in step 1, 3 more with no hotspot | n/a | n/a | INCONCLUSIVE |
| R3 | D-HU | not connected | 6 + 6 min | 3 in step 1, 3 more with no hotspot | n/a | n/a | INCONCLUSIVE |
| R4 | D-POCO | joined to a stray WiFi Direct group (see R4) | none | n/a | n/a | n/a | INCONCLUSIVE |
| R4 | D-HU | not connected | none | n/a | n/a | n/a | INCONCLUSIVE |
| R5 | D-SAM | not connected (`Supplicant state: DISCONNECTED`) | 180 s idle, 600 s session | idle 0, session 3 | 1 of 3 (fps 5 and one audio underrun) | 1 | PASS (measurement) |

## Setup notes

**Order and deviations from the brief.**
- **Run order changed.** R1b ran before R1 (so the station was unjoined once), then R3 D-POCO, R3 D-HU, R5, and R4 last. The rig quirks say a hotspot-transport run leaves a permanent endpoint on the phone, so every R4 goes after every WiFi Direct run on D-MOTO. All five devices were cabled, so no staging was needed.
- **Skill path.** The skill points at `hur-wifi-test-scripts/`, which does not exist; the scripts are in `rig-toolkit/` and were used from there.
- **Pre-flight.** First attempt FAILED (D-MOTO Bluetooth off); the operator switched it on and the re-run printed `PREFLIGHT OK` (D-POCO, D-HU, D-SAM and D-MOTO with WiFi 1 and BT 1; host 58C, throttle 0). Quirk files read: `rig-quirks/topics/tooling.md`, `topics/wifi.md`, `units/D-POCO.md`, `D-HU.md`, `D-SAM.md`, `D-MOTO.md`.
- **Clocks.** The device clock offsets to the host were D-POCO about 0 s, D-SAM 19 s behind, D-HU about 7 min behind. Device-log timestamps are used in the per-run sections below, host wall-clock only where a script anchor is named.
- **Scripts.** Existing: `build_hur.sh`, `run_unit_tests.sh`, `apk_identity.sh`, `rig_preflight.sh`, `rig_thermal.sh`, `wt-new.sh`, `set_prefs_runas_host.py` (D-POCO, D-SAM), `set_hu_settings_host.py` (D-HU), `restore_settings.sh`, `rig_cleanup.sh`. One-round scripts (in `rig-data/rounds/station-scan-round1/scripts/`): `session.sh` (R1b, R1, R5), `r3.sh`, `r4.sh`. No script added to `rig-toolkit/`.
- **Build gate.** `build_hur.sh` exit 0 on the `b887adeb` worktree; `run_unit_tests.sh` (HUR_DIR honoured) 2374 tests, 0 failures, 0 errors; probe class `LocalOnlyHotspotProbeReceiver` present in the dex. Host peaked 92C during the build with `throttle_pkg=0`; run-time maxima were 59C to 75C, `throttle_pkg=0` throughout.
- **Settings.** Written with the app stopped and read back. All head units: `log-level=2`, `wifi-connection-mode=3`. R1b, R1, R3, R5: `native-ap-transport=0`. R4: `native-ap-transport=1`, `auto-enable-hotspot=false`, `hotspot-ssid` and `hotspot-password` deleted. D-POCO's `connection-modes` was an empty set as found; I set it to `{wifi}` so the "connection-modes contains wifi" check could pass. `stand-down-station-mode` as found: D-HU 0, D-SAM 0, D-POCO absent; never changed. D-HU `shared_prefs` stat: `u0_a176:u0_a176 771` (correct owner), `settings.xml` `u0_a176:u0_a176 660`.
- **D-SAM poke target.** D-SAM's `native-poke-bt-macs` and `last-connected-native-mac` pointed at D-POCO (`DC:B7:2E:5E:4E:59`), and D-MOTO was not bonded to D-SAM. The operator paired them; I then set both keys to D-MOTO's address (`A0:46:5A:97:E4:95`) for R5 and restored the original values at the end.
- **LocalOnlyHotspot prerequisites.** D-POCO: `ACCESS_FINE_LOCATION` and `NEARBY_WIFI_DEVICES` already granted, `location_mode=3` as found. D-HU: `pm grant ACCESS_FINE_LOCATION` printed `SecurityException ... not a changeable permission type`, but `dumpsys package` shows both permissions `granted=true`; `location_mode=3` as found. Nothing was changed on either unit.
- **Network unjoin.** Operator supplied the password for the house network (not recorded here). On D-POCO I forgot network id 3 and on D-HU id 2, both lists captured beforehand (`R1-poco-networks-before.txt`, `R3-hu-networks-before.txt`). D-SAM was already unjoined (`Supplicant state: DISCONNECTED`, `Net ID: -1`) and was not touched. Both forgotten networks were restored at the end with `cmd wifi connect-network` (the SSID needs `\"...\"` inside a quoted `adb shell` string; an unquoted call fails with `Unknown network type Cdesta`). D-HU read `Wifi is connected to "Pegue Cdesta"` at 5745 MHz after 15 s; D-POCO needed about 25 s.
- **Phone isolation for "no session" phases.** Launching the app on mode 3 arms Native AA, and D-MOTO connected within seconds in every run that did not isolate it (R1b and R1 had the phone connected by design). For R3 and for the idle phase of R5 the brief's "no session" needs the phone unable to answer. `cmd connectivity airplane-mode enable` on D-MOTO left Bluetooth enabled (Android keeps it), so the first R3 D-POCO attempt formed a session and was voided (`R3-poco-VOID1.*`). Final method: `svc wifi disable` and `svc bluetooth disable` on D-MOTO, verified by `dumpsys` (`Wi-Fi is disabled`, `state: OFF`) before launch, restored and verified (`wifi_on=1 bt_on=1 state: ON`) at the end of each such run.
- **Arm verb after launch.** The brief says launch then `send ACTION_START_WIRELESS_SCAN`. Launching `MainActivity` on mode 3 already arms and creates the group; the verb then tears the group down and creates a second (`createGroup SUCCESS` twice, 8 s apart, in both R1b and R1 and in R3 D-POCO's launch). On D-POCO the second group still produced a session (before each measurement window, so counted as pre-window churn, not a window contamination). On D-SAM it broke the handshake: the phone reported `WIFI_NETWORK_UNAVAILABLE(-11)` four times in a row (R5 attempt 3, void). R5's final run therefore skipped the verb and let the launch arm.
- **R5 attempts.** Attempt 1: D-SAM poked D-POCO (target not D-MOTO), no SSL. Attempt 2: the "idle" phase was a live session (the phone connected at launch), the arm verb dropped the link (`WiFi read timeout (15000ms) - connection lost`) and the script's SSL wait matched the earlier handshake line; void. Attempt 3: the phone was isolated for the idle but the arm verb broke the handshake as above; void. Attempt 4 is the graded run. All void captures are kept (`R5-attempt1.*`, `R5-attempt2-VOID.*`, `R5-attempt3-VOID.*`).
- **Counting rules.** D-SAM refuses `logcat -c`, so R5 counts are windowed from the `R5-launched` marker. `StationScanMonitor` prints a line at the scan that closes a window, so the line's timestamp is a scan time, and its count includes earlier scans in the window. The window that opens at app start can hold a scan from before the measurement.
- **Brief threshold.** "A `Throughput` dip below 45 fps" cannot apply: the phone sent 15 to 30 fps in every run (R1b median 14 to 15 fps through the navigation screen, R1 and R5 27 to 30 fps). Dips are reported against their immediate neighbours instead.
- **Clean-run protocol.** The Phone-airplane-mode steps of §4 were not used (see above). Logcat ring buffer raised with `logcat -G 16M` where the unit allowed it. Capture started before launch with `stdbuf -oL`; verified growing within 30 s of every launch.
- **Discard-rule check, all graded runs:** `MATCH! Starting AapService` 0; `Magic Garbage` 0; a second SSL handshake 0; p2p interface index bump 0. `createGroup SUCCESS` count was 2 in R1b and R1 (launch plus the arm verb, see above) and 1 where no verb was sent.

## R0 Identity

**PASS**

`ACTION_QUERY_STATE` on D-POCO, D-HU and D-SAM: `"commit":"b887adebe185-dirty","versionName":"3.5.0-alpha","versionCode":113,"flavor":"github","ok":true`.

## R1b Joined station on WiFi Direct, D-POCO (control)

**PASS**

- Settings: `native-ap-transport=0`, `log-level=2`, `wifi-connection-mode=3`, `connection-modes={wifi}`.
- Station as read before launch: `Wifi is connected to "Pegue Cdesta"`, 5745 MHz, IP 192.168.1.4.
- Session: `WirelessServer: Incoming connection detected from /192.168.49.180` 17:03:25, `SSL handshake complete` 17:03:26. Window 17:03:41 to 17:13:42 (601 s), music started with `KEYCODE_MEDIA_PLAY`.
- Measurements: `StationScanMonitor` lines 0; `inbound link quiet` 0; audio sink windows 20, `underruns=0` in all 20 (one window, ending 17:03:56, reads min depth 0 ms at session start); `Throughput over` windows 123, `dropped` never above start-up; fps 9 to 30 (the phone's own content changed from 28/29 fps to 14/15 fps from about 17:08, no scan involved).
- Discard check: clean apart from the two group creates at 17:03:07 and 17:03:15 before the window.

A joined station produced no station-scan line in 600 s.

## R1 Unjoined station on WiFi Direct, D-POCO

**PASS**

- Settings as R1b. Station as read before launch: `Wifi is not connected` (after `cmd wifi forget-network 3`; no other saved network in range joined).
- Session: `SSL handshake complete` 17:15:04. Window 17:15:20 to 17:25:20 (600 s), music started.
- Scans (`StationScanMonitor` lines, which carry the scan time): 17:15:33 (that window also held an earlier scan 39.2 s before it, at about 17:14:54, before the measurement), 17:16:53 (80.0 s), 17:19:33 (160.2 s), 17:22:13 (160.2 s), 17:24:53 (159.8 s). That is 5 scans inside the window, with the platform backoff at 80 s, then 160 s.
- Stalls: `inbound link quiet` 0; audio windows 20 (`underruns=0` in all; one window ending 17:15:33 reads min depth 0 ms at session start, the same start-up reading as R1b); `Throughput` windows `dropped=0` throughout.
- Coincidence: every scan sits on a single 5 s `Throughput` window with a reduced frame rate, each surrounded by windows of 27 to 30 fps: 17:15:34 20 fps, 17:16:54 25 fps, 17:19:34 23 fps, 17:22:14 19 fps, 17:24:55 23 fps. Those five are the only isolated dips (at or below 25 fps with both neighbours at or above 27) in the whole window. 5 scans, 5 coincide with a frame-rate dip, 0 with a link stall or an audio underrun.
- Station at the end: `Wifi is not connected`.

On D-POCO an unjoined station costs a visible but harmless 3 to 11 frames per second for one 5 s window per scan and nothing else, which matches "D-POCO has never been reported to stutter".

## R3 LocalOnlyHotspot with no session, D-POCO

**INCONCLUSIVE**

- Settings as R1. Phone isolated (D-MOTO `Wi-Fi is disabled`, `state: OFF`) so no session formed (`Incoming connection detected` 0). Station before and throughout: `Wifi is not connected`.
- Step 1 (control, 17:28:58 to 17:34:58): `StationScanMonitor` lines at 17:30:13 and 17:32:53, so 2 scans in 6 minutes; the control fired.
- Step 2, `PROBE_LOHS_START` 17:34:59. There is no `LohsProbe: started` line. Verbatim:
  - `LohsProbe: before start, station wifiEnabled=true supplicant=DISCONNECTED, staApConcurrency=true`
  - `LohsProbe: start failed, reason=2 (generic)` at 17:34:59.252 (112 ms after the request)
  - framework: `WifiService: start lohs uid=10277`, then `HalDevMgr: bestIfaceCreationProposal is null, requestIface=AP, existingIface=[name=wlan0 type=STA, name=p2p0 type=P2P]`
- `dumpsys wifi` SoftApInfo, verbatim, at after-start, end-of-hold and after-stop: `mCurrentSoftApInfoMap {}`. `cmd wifi status`: `Wifi is enabled`, `Wifi scanning is always available`, `Wifi is not connected`.
- Step 3 (hold 17:35:19 to 17:41:19): `LohsProbe: station scan` lines 0 and `heartbeat` lines 0, because there was no hotspot to report on. The station kept scanning on its own: `StationScanMonitor` 17:35:33, 17:38:13, 17:40:53 (3 scans).
- Second `PROBE_LOHS_START` (brief item 3, 17:41:32): `start failed, reason=2 (generic)` again after 38 ms. Whether credentials survive a restart cannot be answered.
- `staApConcurrency=true`.

The lever could not be tested because the hotspot never came up; the chip refused to add an access point beside the station and the app's WiFi Direct interface.

## R3 LocalOnlyHotspot with no session, D-HU

**INCONCLUSIVE**

- Settings as R1. Phone isolated as above; `Incoming connection detected` 0. Station: `Wifi is not connected` (after forgetting network id 2). Device clock about 7 min behind the host.
- Step 1 (device 17:36:03 to 17:42:03): `StationScanMonitor` 17:36:37, 17:37:55, 17:40:35, so 3 scans in 6 minutes; the control fired.
- `PROBE_LOHS_START` (device 17:42:04): `LohsProbe: before start, station wifiEnabled=true supplicant=DISCONNECTED, staApConcurrency=true`, then `LohsProbe: start failed, reason=2 (generic)` at 17:42:04.180 (156 ms). Framework: `HalDevMgr: bestIfaceCreationProposal is null, requestIface=AP, existingIface=[name=wlan0 type=STA, name=p2p-dev-wlan0 type=P2P]`. Second start (17:48:37): the same, 43 ms.
- SoftApInfo, verbatim at every snapshot: `mCurrentSoftApInfoMap {}`. `cmd wifi status`: `Wifi is enabled`, `Wifi scanning is only available when wifi is enabled`, `Wifi is not connected`.
- Hold period: `LohsProbe` scan lines 0; `StationScanMonitor` 17:43:15, 17:45:55, 17:48:35 (3 scans, no hotspot).

Same refusal as D-POCO, which matches the D-HU quirk that its HAL cannot hold an AP and a WiFi Direct interface together.

## R4 A Native session over the LocalOnlyHotspot, D-POCO

**INCONCLUSIVE**

- Settings: `native-ap-transport=1`, `auto-enable-hotspot=false`, `hotspot-ssid` and `hotspot-password` absent, `wifi-connection-mode=3`, `log-level=2`.
- Station as read before the probe: `Wifi is connected to "DIRECT-QS-MT50YT610E4GFPSLU"`, 192.168.49.136, `Supplicant state: COMPLETED`. This is not the intended state: after I forgot the house network, D-POCO's station joined a WiFi Direct group that D-HU's app had left up after its own R3 (`p2p-wlan0-0` was still present on D-HU).
- `PROBE_LOHS_START` 18:45:56: `start failed, reason=2 (generic)` after 34 ms; `HalDevMgr: bestIfaceCreationProposal is null, requestIface=AP, existingIface=[name=wlan0 type=STA, name=p2p0 type=P2P]` three times. No `LohsProbe: handed '` line, so no arm and no session; the script stopped on the failure. SoftApInfo: `mCurrentSoftApInfoMap {}`.
- App-side, 18:45:41: `SoftApCredentials: The access point on p2p0 is up, but this device will not let apps read its name, so there is nothing to hand the phone.`

No session, no band, no fps. staApConcurrency=true.

## R4 A Native session over the LocalOnlyHotspot, D-HU

**INCONCLUSIVE**

- Settings: `native-ap-transport=1`, `auto-enable-hotspot=false`, `hotspot-ssid` and `hotspot-password` deleted (found as `Navegadortz2` and a password), others as above. Station: `Wifi is not connected`.
- `PROBE_LOHS_START` (device 18:41:18): `start failed, reason=2 (generic)` after 51 ms; `HalDevMgr: bestIfaceCreationProposal is null, requestIface=AP, existingIface=[name=wlan0 type=STA, name=p2p-dev-wlan0 type=P2P]`. No `handed` line; the script stopped. SoftApInfo: `mCurrentSoftApInfoMap {}`.

No session, no band, no fps. staApConcurrency=true.

## R5 KitKat control, D-SAM

**PASS**

Graded as a measurement; the run produced a count in both phases. Attempts 1 to 3 are void (see Setup notes).

- Settings: `native-ap-transport=0`, `log-level=2`, `wifi-connection-mode=3`, `native-poke-bt-macs` and `last-connected-native-mac` temporarily set to D-MOTO. Station: `mWifiInfo: [, Supplicant state: DISCONNECTED, RSSI: -200, ... Net ID: -1`. Phone isolated for the idle and restored at the end of it; the app armed itself at launch (arm verb skipped).
- Idle (device 18:30:05 to 18:33:07, 182 s, group up, phone unable to answer): `StationScanMonitor` lines 0. The first line of the capture is 18:34:35 and reads `station scans: 1 in 277820ms`, i.e. the window opened at app start and held only that scan.
- Session: phone radios back 18:33:16, `Incoming connection detected from /192.168.49.191` 18:33:33, `SSL handshake complete` 18:33:34. Window device 18:33:52 to 18:43:52 (600 s), music started.
- Session scans: 18:34:35 (277.8 s after app start), 18:37:33 (178.0 s), 18:42:18 (285.0 s). That is 3 scans.
- Stalls: `inbound link quiet` 0; audio windows 20, `underruns=1` in one: `18:42:38 audio sink AUDIO over 30003ms: underruns=1, silentCycles=0, rebanks=1 ... depth=915ms (min 0ms)`. `Throughput`: scan 1 29 to 30 fps throughout, scan 2 29, 30, then 26 fps, scan 3 28, 20, 5, 27 fps (18:42:15 20 fps, 18:42:20 5 fps).
- Coincidence: 1 of 3 scans (the third) coincides with a frame-rate collapse and an audio underrun; scans 1 and 2 show no stall. One more isolated dip of 25 fps at 18:36:50 sits on no scan.
- Brief's reading: idle 0 against session 3. KitKat's suppression (zero during a connected group beside a non-zero idle count) is not seen here; the pattern is the other way round.

## Anything the brief did not ask about

- **The hotspot lever was never reached.** All six `PROBE_LOHS_START` calls with the app running ended in `reason=2` within 160 ms, each with the HAL line `bestIfaceCreationProposal is null, requestIface=AP` and a P2P interface present beside the station. Even with `native-ap-transport=1` (R4) the app brings the P2P interface up. An unprompted diagnostic (`D1-diag.txt`, not a graded run) force-stopped the app first and got `reason=3 (incompatible mode (tethering on?))` on both units. It is not a clean control: D-POCO's station was joined to the stray D-HU group and D-HU still had `p2p-wlan0-0` up. A clean attempt needs both units free of P2P groups (for example after a reboot, which this round did not do).
- **Stray group.** D-HU's app leaves its WiFi Direct group up after a force-stop, and D-POCO's station then auto-joined it once the house network was forgotten. Any later round that forgets D-POCO's home network while D-HU has run should expect this.
- **D-MOTO state after this round.** R4 used `native-ap-transport=1` against D-MOTO only up to the failed hotspot start, so most likely no endpoint reached it (not checked), but the quirk says to forget the vehicle on D-MOTO before it is used again; the operator should decide.
- **Station-scan fps cost.** R1 shows the five D-POCO scans each cost one 5 s window 3 to 11 frames per second below its neighbours; R5 shows one of three D-SAM scans produced a 5 fps window and one audio underrun. The reporters' stutter (3.3 s and 6.8 s with every stream stopped) was not reproduced.
- **D-POCO stale bonds.** D-POCO is bonded to both `Navegadortz2` (D-HU) and `Navegadortz3` (D-SAM) and held an HFP link to D-HU at the start; it did not matter here because D-POCO was the head unit.
- **Disk.** The D-HU R3 capture is 69 MB because the unit floods logcat; the other captures are 0.2 to 7.6 MB.
