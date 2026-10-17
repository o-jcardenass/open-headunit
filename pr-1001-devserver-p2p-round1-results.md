# pr-1001-devserver-p2p, round 1 results

- Arms: B `0cbff00432d13c164799fe2d20e79444be2c0983` (APK md5 `b30226ed91f914b2fc588066b6226433`, tree `4a411bba`), P `7f5bc6e926fbbdf167951281ca54de59f44c0559` (md5 `fdddb5b955954a4129db69ee86da9254`, tree `02a53e33`), F `ce5e61cb55d7a19b26312e460ce86b760f9c61f5` (md5 `ec466b0d1249a743efceaee298a8fe1e`, tree `adefaa2f`). `commit` in `ACTION_QUERY_STATE` read `0cbff00432d1`, `7f5bc6e926fb`, `ce5e61cb55d7`.
- Units: D-HU and D-POCO (Gearhead 18.0.164204-release.daily). Paired with the audio-stutter round; its units (D-HP, D-MOTO) took no part in these runs.
- Date: 2026-10-15 and 2026-10-16.
- Evidence: release `rig-evidence-pr-1001-devserver-p2p`, asset `pr-1001-devserver-p2p-round1-captures.zip`, sha256 `0dbb0ee61f224d07fd42390deb60022d46d45318c9cf54ac82e5a23e1d7adfb7`.

## Round verdict: not requested by the brief

R0 PASS. R3-B PASS, R3-P FAIL (the pre-registered result: H1 reproduced), R3-F PASS. R1-B FAIL (one condition), R1-P INCONCLUSIVE by rule. R5A PASS, R5B INCONCLUSIVE. R4 PASS (joined as `Navegadortz2`). R6 FAIL (one condition). R2-B PASS, R2-P PASS.

## Setup notes

- Transfer branch worktree: the branch was already checked out in `worktrees/rig-sync__scratch__8228cb07__20261015`, used as is. Quirk files read: `topics/tooling.md`, `units/D-HU.md`, `units/D-POCO.md`, `units/D-HP.md` (head), `topics/wifi.md` (grep). `rig_sync.py sync` read "in sync".
- Opening pre-flight: D_HU wifi 1 bt 1 Awake HFP none A2DP none; D_POCO wifi 1 bt 1 Awake HFP none A2DP none. Closing pre-flight: see the end of this file.
- Pairing: five APKs built in one worktree (`scripts/build_all.sh`, thermal-gated). audio-stutter R2 was dropped by the operator's decision, so no hold ran beside R4, R6 or any hand step; its R1 hold ran beside R5.
- D-MOTO bluetooth was left on by the previous round's restore. It was disabled for this round (restore: enabled). D-SAM has no `svc bluetooth`; its Bluetooth was switched off by the operator before R2 (`bluetooth_on` read 0).
- Settings were written with `hu_put` and `tab_put` from the copied `ohu_lib.sh`, keys read back each time. D-HU audio keys left as found: `use-aac-audio` false, `audio-latency-multiplier` 8, `audio-queue-capacity` 20, `enable-audio-sink` false. D-HU `shared_prefs` was `drwxrwx--x u0_a176:u0_a176`. D-HU `wifi_p2p_device_name` read `null` before and after (the app's rename was refused, see R4).
- Arming on a stopped app: `ACTION_START_WIRELESS_SCAN` returned `startForegroundService() not allowed due to mAllowStartForeground false` (result=1). Lever used in every later script: `cmd deviceidle tempwhitelist -d <ms> <pkg>` before the first marker and before the verb. A cold start by the marker alone also crashed `BootCompleteReceiver` once (LOCKED_BOOT_COMPLETED redelivered, same exception); the whitelist before the marker removed it. Not in the brief.
- R3-B took four attempts: (1) verb refused, no launcher; (2) VOID: D-MOTO's Bluetooth was on, so the armed Native launcher accepted a real Native AA session from D-MOTO (`NativeAA: Connection accepted from motorola edge 30 neo`, 22:51:07), not a Self Mode session; (3) cold-start crash above; (4) PASS. Attempts 1 to 3 kept as `attempt*-R3-B.*`. Attempt 2 was the host's own rig state.
- Phone window helper erratum: the brief's `pwin` matches `OHURIG: <label>`; the phone prints `I/OHURIG  (pid): <label>`. Every phone-window count from the brief's helper reads 0. `scripts/env.sh` overrides `pwin`. R3 uses the head unit capture only and is unaffected.
- R1 `first` check: my script omitted the brief's optional fallback line; R1-B attempt 1 would not have been rescued by it (the reconnect also failed).
- Overnight (between R5 and R4): the host adb server restarted, all USB devices re-enumerated, D-HU rebooted (uptime 14 min at 08:44). D-HU kept F and the `KEYS_ON` settings. D-POCO's Bluetooth read off and its Gearhead server was down, so HS1 ran again. D-POCO Bluetooth was re-enabled before R2 (opening state: on).
- R4 `HS2` text names "OpenHU"; the group is not named that on this unit (R4). Attempt 2 used the entry `Navegadortz2`.
- D-HU's clock stepped back about 18 min while its WiFi was off in R6 (08:53:48 to 08:36:01) and returned when the network did. Windows follow marker order and gaps are taken on one side of the step, so counts are unaffected. R6's brief window `R6-on..R6-end` starts after `svc wifi enable` plus 10 s, which is after the group re-create, so the "P2P Group created" count must be taken from `R6-off-done` to `R6-end`.
- R2-B's script deleted its own log (a `rm -f R2-B-*` in the script matched its log name); the run was unaffected and graded from the capture.
- Two logcat pids from R2-P (D-HU and D-POCO) outlived `cap_stop`; killed by pid afterwards. No other pids were killed.
- Hand steps (from `hand-steps.log`): HS1 22:35 (port 5277 read 0), HS1 23:13 (R1-B wedge), HS1 08:45 (after the overnight reset), HS2 08:46 (no OpenHU entry existed), HS2 08:52 (tapped `Navegadortz2`). HS3 was not reported. HS4 and HS5 were not needed. In R6 the operator tapped `Navegadortz2` again without a cue.
- Rig lock `/tmp/ohu-rig.lock` was not taken by the scripts; the audio hold used `/tmp/ohu-rig-dhp.lock`.

## R0

**PASS**

Three builds, three md5s (above) all differ. `; proceeding with group creation` in the DEX: B 0, P 1, F 1.

## R3-B

**PASS**

Four attempts (Setup notes); the graded one is attempt 4. Counts, `R3-B-off` to `R3-B-hold-end` unless marked: `refused` 1 (start window), `native` 1, `last_init` 1, `stops_after_last_init` 0, `route` 1, `ssl` 1, `rend_pre` 3, `leave` 2, `close` 0, `fin` 0, `disc` 0, `rend_hold` 6, `bye` 0, `setWifiEnabled` 1. The two `leave` lines are one event delivered twice, as on F.

`22:59:53.459 AapService: WIFI_STATION_DISABLING, but this session does not ride that link; leaving it alone`

`p2p-armed`: `groupFormed: true isGroupOwner: true 192.168.49.1`. After hold: `Wi-Fi is disabled`; after `svc wifi enable`: `Wi-Fi is enabled`.

## R3-P

**FAIL**

The pre-registered outcome: the P column holds, so H1 is reproduced on hardware. Counts: `leave` 0, `close` 2, `fin` 2, `disc` 1, `rend_hold` 0, `bye` 1 (phone), `hud` 1 (`Head unit disconnected`). Setup conditions all met (`native` 1, `last_init` 1, `stops_after_last_init` 0, `route` 1, `ssl` 1, `rend_pre` 3). `close` and `fin` read 2 where the brief's table says 1 because the trigger arrives twice, as `leave` does on B and F.

`23:01:47.840 AapService: WIFI_STATION_DISABLING with a live session` (the line continues, closing it now while the link still works)
`23:01:47.846 AapService: link-loss teardown finished in 1ms` and, on the phone, `23:01:47.846 CAR.GAL.GAL.LITE: received ByeByeRequest`.

## R3-F

**PASS**

Counts as B: `leave` 2, `close` 0, `fin` 0, `disc` 0, `rend_hold` 6, `bye` 0, setup conditions met (`native` 1, `last_init` 1, `stops_after_last_init` 0, `route` 1, `ssl` 1, `rend_pre` 3).

`23:03:36.651 AapService: WIFI_STATION_DISABLING, but this session does not ride that link; leaving it alone`

D-POCO at the end of stage 1: `isGroupOwner: false`, `Pegue Cdesta`, settings restored from the backup (diff empty).

## R1-B

**FAIL**

Failed brief condition 2 only. Sessions formed 4 of 4 (`first=0`, `r1=0`, `r2=0`, `r3=0`). Window `R1-B-start` to `R1-B-x1-go` held `SSL handshake complete` 2 where the brief asks for 1: the phone's server reset the first connect 1.4 s after it opened (`23:14:17.733 SocketProjectionConnection: Socket broken (server disconnected ungracefully?)`), discovery redialled and the second connect held (`SSL handshake complete` 23:14:16.309 and 23:14:20.980). Other conditions met: `NetworkDiscovery: Found Headunit Server on 192.168.1.4:5277` 2 and `Auto-connecting ... 192.168.1.4:5277` 2; exits 3 of 3 (`Disconnect action received.` 1, `AapService: Disconnected.` 1, phone `received ByeByeRequest` 1 each); reconnects 3 of 3 (`SSL handshake complete` 1, `rend` 1, phone `Head unit connected` 1); `Auto/P2P` 0, `scanning P2P` 0, `P2P Group created` 0.

Attempt 1 was a Gearhead server wedge: discovery found `192.168.1.4:5277`, TCP was accepted at 23:09:18 and `Handshake: the peer accepted the connection and then sent nothing at all` followed at 23:09:28 (`listening` 1, phone `Head unit connected` 0). HS1 restarted the server. The stop rule allowed one re-run and it was used, so no third run was taken.

## R1-P

**INCONCLUSIVE**

By the brief's rule (R1-B failed). P's own numbers met every condition: `first=0`, window `R1-P-start`..`x1-go` `SSL handshake complete` 1, `rend` 3, found 1, auto 1 (`192.168.1.4:5277`), exits 3 of 3 with `received ByeByeRequest` 1 each, reconnects 3 of 3 with `Head unit connected` 1 each, `Auto/P2P` 0, `scanning P2P` 0, `P2P Group created` 0.

## R5A

**PASS**

`R5A-go` to `R5A-done`: `WifiLauncher: Initializing WiFi Mode: ` 1 and it ends `AUTO`; `; proceeding with group creation` 1 (`rename refused (0)`); `P2P Group created (fresh this session).` 1; `createGroup failed: ` 0; `Auto/P2P badge: group=true` 1; `scanning P2P ` 0. Interface at 30 s and at 300 s: `p2p-wlan0-0` both times. H3: no create failed, so the retry cadence did not show; H3 stays at code reading.

## R5B

**INCONCLUSIVE**

10 attempts, each `presses=2`, `hit=0`, `removed=0`, `leftalone=0`, `creates=2`, `other_abandon=0`, `outage_ms=NA`. Press B never landed inside the create of press A, so H2 is not reproduced on hardware and stays at code reading.

## R4

**PASS**

Attempt 2 (attempt 1 had no join: the rename to OpenHU was refused, so D-POCO's Wi-Fi Direct list had no such entry). All 11 conditions met, joined by tapping `Navegadortz2`. Counts over `R4-start`..`R4-end`: init 1 `AUTO`; `; proceeding with group creation` 1 (`Auto/P2P: rename refused (0)`); `P2P Group created (fresh this session).` 1 at 08:52:04.780 and `Auto/P2P badge: group=true` at 08:52:04.915; `NetworkDiscovery: scanning P2P p2p-wlan0-1 192.168.49.1/24` first at 08:52:51.228; `Found Headunit Server on 192.168.49.111:5277` 08:52:53.351; `Auto-connecting to Headunit Server at 192.168.49.111:5277 (reusing socket)` 08:52:53.357; `SSL handshake complete` 1 at 08:52:53.848; `rend` after it 3. Scan to SSL 2620 ms (limit 30000). Order group, scan, found, auto, SSL holds. Phone `Head unit connected` 1. `R4.poco-p2p`: `groupFormed: true isGroupOwner: false`.

Caveat for the review: the group did not carry the name "OpenHU" on this unit (Android 14). `Auto/P2P: rename refused (0); proceeding with group creation` at 08:46:35.041, and the badge reads `deviceName=Navegadortz2`. The hand step used the head unit's own name. HS3 was not reported.

## R6

**FAIL**

Failed brief condition 2: the phone received no `ByeByeRequest` in `R6-go`..`R6-off-done`. Its only `ByeByeRequest` is the end-of-run exit at 08:56:24.843. In the same window `with a live session` 1, `leaving it alone` 0, `link-loss teardown finished in 182ms` 1, `AapService: Disconnected.` 2, `setWifiEnabled` 1. Sequence: `08:53:47.953 setWifiEnabled enable=false`, `08:53:47.966 WIFI_STATION_DISABLING with a live session`, `08:53:47.971 WIFI_P2P_STATE_CHANGED_ACTION state=1`, `08:53:48.148 link-loss teardown finished in 182ms`. The P2P interface went down 5 ms after the close was sent, which fits a ByeBye that never left the device.

All other conditions met: after `svc wifi enable` the group was re-created (`P2P Group created (fresh this session).` 1 and `P2P enabled, auto-starting WiFi Direct visibility` 1 between `R6-off-done` and `R6-end`; the brief's `R6-on`..`R6-end` window reads 0 by construction, Setup notes), `scanning P2P` 1, `SSL handshake complete` 1 at 08:56:06.597, `rend` 3, scan to SSL 2507 ms (limit 60000), phone `Head unit connected` 1, `listening_after` 1, no HS1 in the window.

Whether the phone rejoined by itself is not established: no cue was printed because the script's own wait saw `scanning P2P` about 108 s after `R6-on`, and the operator reports tapping `Navegadortz2` again in that interval. D-HU `devicename` stayed the platform name; nothing to restore.

## R2-B

**PASS**

Sessions 5 of 5; each `s1..s5` window `SSL handshake complete` 1 and `rend` 1; each exit `Disconnect action received.` 1, `Native AA user exit. Stopping active launcher.` 1, phone `received ByeByeRequest` 1. Whole capture: `createGroup SUCCESS` 5, `MATCH! Starting AapService` 0.

## R2-P

**PASS**

Sessions 5 of 5 equal to B; exits as B; `Auto/P2P` 0, `scanning P2P` 0, `createGroup SUCCESS` 5 (B 5, limit B+1), `MATCH` 0.

## Anything the brief did not ask about

- The OpenHU rename is refused on D-HU (Android 14) by the reflective `setDeviceName` call: `rename refused (0)` on every bring-up in R4, R5 and R6. The setting-on route still works because the join was by the unit's own name, but a user following "tap OpenHU" would find nothing.
- R6 on a real WiFi-off: the phone gets no ByeBye on the P2P route, because the P2P interface goes down 5 ms after the close is sent. R3-P in Self Mode (loopback) did deliver it. This is a difference between the two routes, not a defect in F.
- R3 events double: every `WIFI_STATION_DISABLING` trigger prints two lines on all arms.
- D-HU lost its time while its WiFi was off (an 18 min step), which can mislead any gap measured across a WiFi-off.
- The Gearhead developer server wedged once after a Self Mode stage, and went down overnight. HS1 was needed three times; a Self Mode stage before a head unit server stage is worth a server restart in the brief.
- Thermal: peak 73C and throttle delta 0 on every graded run.

## Closing

Restore: D-HU, D-POCO and D-HP `settings.xml` diffs against their backups are empty (D-HP with carriage returns stripped). D-MOTO bluetooth enabled and `stayon` false. D-HU still carries the F build and D-HP the baseline build; neither previous build was recorded. D-SAM's Bluetooth was on at the opening and was switched off by the operator for R2; it has to be switched on by hand.

Closing pre-flight (`rig_cleanup.sh list`: no strays, lock free): D_HU wifi 1 bt 1 Awake HFP none A2DP none; D_POCO wifi 1 bt 1 Awake HFP XX:XX:XX:XX:33:59 A2DP up; PREFLIGHT OK. D-HU WiFi Direct device name: platform name `Navegadortz2` before and after; `wifi_p2p_device_name` `null` before and after.
