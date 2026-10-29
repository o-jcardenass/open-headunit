# automation-screen-disconnect: round 4 results

**Candidate:** fork/feat/automation-screen-disconnect @ 3101712afcd587afc6ae628855cba316d5314dda (tree 773cda5d5682e774bc595ca5bce903b654950039)   **Baseline:** none (one build, as briefed)
**APK md5:** edac10621db7e3a3dcd37f4b256207db (candidate only). `ACTION_QUERY_STATE` commit `3101712afcd5`, versionCode 120, on D-POCO (R0) and D-HU (PN).
**Unit:** D-POCO (POCO X3 NFC, Android 15, LineageOS) as head unit for Stages A and U and as phone for Stage N; D-MOTO (Motorola edge 30 neo, Gearhead 18.1.164344-release.daily) as phone; D-HU (UNISOC MT50, Android 14, rooted) as head unit in Stage N.
**Date:** 2026-10-25
**Evidence:** release `rig-evidence-automation-screen-disconnect`, asset `automation-screen-disconnect-round4-captures.zip`, sha256 d2dcaa1262f44afdc231fd24b6faaf50f9cd1eb95e9386c6bc6765f0b16faa46

## Verdicts

| Run | Verdict |
|---|---|
| R0 | PASS |
| V3 | PASS |
| V4n | PASS (third try) |
| R5n | PASS |
| R7n | PASS (conditions 4, 5 INCONCLUSIVE by rule) |
| V4k | PASS |
| R5k | PASS |
| R7k | PASS (condition 4 INCONCLUSIVE by rule) |
| R11 | **FAIL** (condition 8, `Initializing` clause; condition 5 INCONCLUSIVE) |
| C5u | PASS |
| C1 | INCONCLUSIVE (quiet window, both tries) |
| C3 | PASS |
| C5p | PASS |
| C2 | PASS |
| C4, C4c | PASS (condition 6 INCONCLUSIVE in both) |
| C1d | INCONCLUSIVE (quiet window) |

## Setup notes

- Resolved SHA `3802d139` to this round. Came from `main`, transfer branch fast-forwarded, `rig_sync.py` "in sync", no RESTART. Read: this brief in full, TESTING-TEMPLATE.md without 7b and 8, quirks `topics/tooling.md`, `bt.md`, `lifecycle.md`, `projection.md`, `units/D-POCO.md`, `D-MOTO.md`, `D-HU.md`.
- Opening pre-flight failed once on D-POCO Bluetooth off (`bluetooth_on=0`); the operator switched it on and the second pre-flight read `PREFLIGHT OK` (all three: WiFi 1, BT 1, Awake). One idle Gradle daemon was killed with `rig_cleanup.sh kill`.
- Build: `build_hur_cool.sh`, `MAX_WORKERS=2`, host 58C before. Gate passed (sha and tree). DEX symbol counts: WiredRearm 6, `the stand-down decides the re-arm of wireless mode` 1, `screen-on asks the system to dismiss the keyguard` 1, ScreenOnRaisePolicy 3, `external-power disconnect` 13, ConnectByNameGate 22. Install on D-POCO: `-r -d`, `settings.xml` pre/post delta zero. D-HU install: delta zero.
- PA: D-POCO's own backup differs from round 2's backup in many non-feature keys (wifi-direct group keys, view-mode, fps-limit, `auto-start-on-usb` true, `auto-connect-last-session` true); BASE/UKEYS overrode what the runs need. AC powered, level 81. D-MOTO level 35. Lock credential verified, `KG_POCO=false`, `TIMEOUT_POCO=1800000`, admin 0, D-MOTO bond count 1, MOTO_MAC A0:46:5A:97:E4:95, D-MOTO keyguard not showing (HS0 not needed). Grant by `dpm set-active-admin` worked (HS3 not needed).
- Scripts: the brief's blocks were extracted with `extract_brief_blocks.py` after a sed pass (the brief writes `**name.sh**` without backticks); indented blocks (PN, C3, C5u) were written by hand from the brief. Helper libs from round 2 (`ptr_lib.sh`, `ascd2_lib.sh`, `sx.py`) copied; `runlocked.sh` patched to take `NAME ENV BLOCK` and to use this round's directory. Stage A/U scripts source `env4a.sh`, Stage N `env4n.sh`; the two stage libraries never shared a shell. Grading helpers in `scripts/g_*.sh`. One-round scripts are in `rig-data/rounds/automation-screen-disconnect-round4/scripts/`; nothing was added to `rig-toolkit/`.
- **Brief errors found:** (1) `kgset` uses `locksettings set-disable`; D-POCO answers `Unknown command: set-disable`. The command is `set-disabled` (fixed in `ascd3_lib.sh`). (2) `x_cleanup.sh` line 10 holds the literal `<TIMEOUT_POCO>`, a syntax error that aborted the script after the battery reset; the last four checks were run by hand. (3) `rend` in `wdnc_lib.sh` takes its argument, not stdin, unlike `rend` in `ascd2_lib.sh`; Stage N has no `c` helper. (4) `stat -c %a` is not supported on D-HU (owner printed only).
- **Host error:** I ran V4n once after `kg_off` printed `false`; the brief says V4n is then UNTESTABLE. The capture is kept in `attempt1/`.
- **Unscheduled hand step:** V4n attempts 2 and 3. Attempt 2 (valid keys, kg off true) gave `ssl=1` again. D-MOTO's Gearhead logged `Starting manager of type: TCP`, then `GH.WPP.TCP ... ssid=DIRECT-EQ-POCOX3NFC ... port=5299`, then `GH.WirelessNetRequest Failed to find network within PT7S/PT12S/PT17S` five times and `State changed to SHUTDOWN`, with 0 `Creating rfcomm socket`. Round 2's passing captures show the same `Starting manager of type: TCP` line, so that is not the difference. Both phones were associated with the home access point at the time (read after the run). The operator forgot D-POCO from D-MOTO's Android Auto vehicles at about 18:10 and the next try formed a session. Cause of the first two failures is therefore a stale cached endpoint on D-MOTO (matches the earlier thread finding that a forget-car clears it).
- Hand steps and times are in `hand-steps.log`: HS2 18:24:10; CABLES 18:25:23; OTG 18:28:59; H3 and USBOK cues from 18:29:52; OTG-OUT 18:34:42; CABLES-BACK 18:35:44. H1 was not needed (NB: `poco_has_dhu=1 poco_has_dsam=0 dhu_has_poco=1`). RECOVER was not needed. D-POCO's USB permission dialog (`UsbPermissionActivity`) stayed focused from 18:29:52 to 18:31:49 (cued 12 times, about every 10.5 s); `ssl=0` came after the operator pressed OK. No USB dialog appeared at power back.
- Stage N PN: D-HU shell uid 0; `shared_prefs` owner `u0_a176:u0_a176`; audio keys left as found (`audio-latency-multiplier` 8, `enable-audio-sink` false, `use-aac-audio` false, `audio-queue-capacity` 20). D-POCO was rebooted (`boot_poll=30`, the cap, but `boot_completed` and 3 Gearhead processes were present; focus was `NotificationShade`, so I dismissed the keyguard and pressed Home). D-HU had two saved P2P groups (networkId 4 and 5), both deleted. `SYSTEM_ALERT_WINDOW` read `allow`, no change. `wifi_p2p_device_name` read `null`. POCO_NAME `POCO X3 NFC`, MOTO_NAME `motorola edge 30 neo`, POCO_BT DC:B7:2E:5E:4E:59, MOTO_BT A0:46:5A:97:E4:95, DHU_BT 11:46:03:10:33:59.
- Windowed greps: every count is inside a marker window; no unit refusing `logcat -c` was used. Thermal maximum 68C, throttle delta 0 in every run.
- Pre-flight table (opening, after the operator fix): see `preflight-open.txt`: D_HU WiFi 1 BT 1, D_POCO WiFi 1 BT 1, D_MOTO WiFi 1 BT 1, all Awake.

- Closing pre-flight (after a second idle Gradle daemon was killed): D_HU WiFi 1 BT 1 Awake; D_POCO WiFi 1 BT 1 Awake (HFP and A2DP up to D-HU's radio); D_MOTO WiFi 1 BT 1 Awake; `PREFLIGHT OK`. Restores: D-POCO `settings.xml` diff vs backup empty, `locksettings get-disabled` false, `screen_off_timeout` 1800000, admin 0; D-HU `settings.xml` diff vs backup empty; D-MOTO radios on; build worktree removed; no logcat processes.

## R0: build and identity

**PASS**

- APK md5 `edac10621db7e3a3dcd37f4b256207db`, all six symbols count 1 or more, PA `U=$POCO state` and PN `P.state` both read `"commit":"3101712afcd5"`.

## V3: screen-on with the grant, no session

**PASS**

- Settings: `$BASE $A1 $EP_OFF`. D-HU Bluetooth off, D-MOTO radios off.
- `V3-off.reply` `"screenOff":true,"ok":true`; `V3.w-off` `Dozing`; 1 `WakeDetect: SCREEN_OFF`; `power lost beside a screen-off` 0.
- `10-25 17:54:48.270 AapService: turning the screen on (automation) by wake lock and the home screen by NOTIFICATION (keyguard=true)`; gap to `SCREEN_ON` 70 ms; `screen-on launch failed` 0.
- `17:54:48.948 MainActivity: screen-on asks the system to dismiss the keyguard`, `17:54:49.591 MainActivity: the keyguard was dismissed` (dismiss 1 and 1). `V3.kg-off` `isKeyguardShowing=true`, `V3.kg-on` `false`, `V3.focus` `MainActivity`. `device wake-up` line at 17:54:48.259.

## V4n: screen-on during a session, no keyguard

**PASS**

- Third try (two earlier tries `ssl=1`, see Setup notes). Keyguard off by `locksettings set-disabled true`, `kg-off` `isKeyguardShowing=false`.
- Valid: `rend(arm..cut)` 7, `MATCH` 0, `wrapped=no`.
- `18:11:28.235 AapService: turning the screen on (automation) by wake lock and the projection by OVERLAY (keyguard=false)`. `w-off` `Dozing`, `w-on` `Awake`, session survived (0 `since connection is disconnected`), `V4n.focus` `AapProjectionActivity`, `rend(on..after)` 5, phone `received ByeByeRequest` 0.

## R5n: screen-off after the end (no keyguard)

**PASS**

- `$BASE $A0 $EP_MIN_SCR`. A1 `delay` 5000 (the 2 was clamped); A6 5010 ms after A1; stand-down 1; `: screen off ... done.` 1, `refused by the system.` 0; `SCREEN_OFF` 5890 ms after `POWER_DISCONNECTED`; `power lost beside a screen-off` 0; `R5n.wake` `Dozing`, `R5n.kg` `false`; phone ByeBye 1. Valid: `rend` 7, `MATCH` 0, `wrapped=no`.

## R7n: power back, no keyguard

**PASS** (conditions 4 and 5 INCONCLUSIVE by rule)

- `18:14:23.382 AapService: turning the screen on (external power back) by wake lock` (alone, as briefed; `device wake-up` at 18:14:23.381). `POWER_CONNECTED` to the line 10 ms. `power is back; re-arming the wireless bring-up.` 1, `checking USB again.` 0. `ssl2=0`, `rend(V)` 3, `R7n.focus` `AapProjectionActivity`, `screen-on launch failed` 0. `R7n.wake` `Awake`. `re-arming ... held while the unit was asleep` 0, `hibernate wake detected` 0.

## V4k: screen-on during a session with the swipe keyguard

**PASS**

- `V4k.kg-off` `isKeyguardShowing=true` (valid). `18:16:59.106 AapService: turning the screen on (automation) by wake lock and the projection by OVERLAY (keyguard=true)`.
- `18:16:59.261 AapProjectionActivity: screen-on asks the system to dismiss the keyguard`, `18:16:59.961 AapProjectionActivity: the keyguard was dismissed` (854 ms after the screen-on line). The three failure strings 0. `V4k.kg-on` `false`, focus `AapProjectionActivity`, `rend` 5, phone ByeBye 0. Round 2's V4 was `NotificationShade` with `rend` 0.

## R5k: screen-off after the end (swipe keyguard)

**PASS**

- Same as R5n: A1 5000, A6 5006 ms after A1, `SCREEN_OFF` 5961 ms after `POWER_DISCONNECTED`, `R5k.kg` `isKeyguardShowing=true`, phone ByeBye 1.

## R7k: power back with the swipe keyguard

**PASS** (condition 4 INCONCLUSIVE by rule)

- `18:19:46.560 AapService: turning the screen on (external power back) by wake lock and the home screen by OVERLAY (keyguard=true)`, 17 ms after `POWER_CONNECTED`.
- `18:19:46.744 MainActivity: screen-on asks the system to dismiss the keyguard`, `18:19:47.405 MainActivity: the keyguard was dismissed`. Failure strings 0. `power is back; re-arming` 1, `checking USB again.` 0. `ssl2=0`, `rend(V)` 3, `R7k.kg-after` `false`, focus `AapProjectionActivity`. Dismiss to `SSL handshake complete` 8353 ms.

## R11: a USB session ended by power loss

**FAIL**

- Settings `$UKEYS` (mode 1, `connection-modes` usb,wifi). D-POCO and D-MOTO on wireless adb; `ssl=0` after the operator's H3 and USBOK steps. Valid: no SSL before the plug, `rend(plug..cut)` 5, `MATCH` 0, `wrapped=no`, `USB session established while wireless mode AUTO/NEARBY_DEVICES was armed` 1 (18:31:52.931).
- Passing: condition 1 (a to d; quiet window 70898 ms), 2 (A1 delay 20000, 1 ms after `POWER_DISCONNECTED`), 3 (A6 20008 ms after A1), 4 (byebye 17 ms after A6), 6 (hold line 32 ms after A6, stand-down 1, "dropped" 0), 7 (`wr` 0, `Initializing` 0, SSL 0 in the quiet window), 9 (0), 10 (`ssl2=0`, `rend(W3)` 2).
- Condition 5 INCONCLUSIVE: the D-MOTO phone capture ends at 18:31:50 when the USB session formed, so no ByeBye line can be read.
- **Condition 8 failed:** `W3` holds 1 `POWER_CONNECTED`, 1 `checking USB again.` (gap 0), `wr` 1 (gap to re-arm 21 ms), 0 `power is back; re-arming`, but `WifiLauncher: Initializing WiFi Mode: ` is **0** in `W3` (needed 1400 to 5000 ms after the re-arm).
  - `18:33:52.775 UsbLauncherManager.checkAlreadyConnected | Found device already in accessory mode: motorola edge 30 neo (VID: 18D1 PID: 2D01)`
  - `18:33:52.788 AapService.launchWiredSessionRearm | AapService: wired session ended — re-arming wireless mode AUTO`
  - `18:33:54.298 AapService.deferWirelessForUsbHandoff | AapService: a USB projection attempt is in flight — holding the wireless bring-up for up to 8000ms` (and 18:33:55.302)
  - `Version request send failed (ret=-1)` attempts 1 to 3 and a RESWITCH recovery, then `18:34:10.626 SSL handshake complete` (17859 ms after `POWER_CONNECTED`).
  - `Initializing WiFi Mode: AUTO` next appears 18:34:36.582, after the user exit and outside `W3`.
- Reading: the phone stayed attached in accessory mode, so the USB check at power back started a USB attempt at once, and the existing USB hand-off hold kept wireless down until the USB session formed. The hold, the quiet window and the "decide the re-arm after power back" logic all behave as briefed. The `Initializing` clause as written does not hold when a USB attempt is in flight at power back. The brief's re-attach fault strings (`Found device already in accessory mode` 1, `Version request send failed` 4) appeared in `W3` and the session still formed (`ssl2=0`), so condition 10 stands.
- Requested counts: USB dialogs for one OTG plug: 1 `UsbPermissionActivity` (focused 18:29:52 to 18:31:49); for the power return: 0. `Requesting USB permission for` lines in `W3`: 0 (2 in the whole capture).

## C5u: bare ACTION_CONNECT is still the USB check

**PASS**

- Receiver `ACTION_CONNECT` 1, `Boot WakeLock acquired (10min timeout)` 1, `connect by` 0, `Received manual Native-AA poke request` 0. Cold start armed NATIVE (1); active pokes 0.

## C1: connect by name on an armed, quiet stack

**INCONCLUSIVE**

- Cause: condition 2 (quiet) failed in the window used in both tries. In both tries the first 30 s quiet window broke (the phone formed a session on its own, 18:39:59 in try 1), the script sent `ACTION_DISCONNECT` and ran a second 20 s window with 0 SSL, 0 accepted, 0 manual pokes and 0 active pokes on the head unit but **4** `Creating rfcomm socket for device` lines on D-POCO (needed 0).
- Conditions 1 and 3 to 10 met in both tries. Try 2 (kept as the graded capture): `18:44:32.669 AapService.commandPokeTarget | AapService: connect by name "POCO X3 NFC" resolved to DC:B7:2E:5E:4E:59`; `18:44:32.785 NativeAA: Driver selected: DC:B7:2E:5E:4E:59 (explicit pick)`; manual poke 1; command to `SSL handshake complete` 8919 ms (try 1: 12923 ms); command to `Connection accepted` 2173 ms (try 1: 6281 ms); `rend` 3; phone rfcomm in the window 1 (try 1: 2); `Boot WakeLock` 0; `WiFi button on the Bluetooth module route` 0; `raising the projection by OVERLAY (overlay=true, foreground=false)`; `createGroup SUCCESS` 1 before the command and 1 in `W`; `Calling socket.connect() for` 0 and `waking a phone over a hands-free link it holds is` 1 (the wake guard stood the manual poke down; not compared against a run where the guard let it through). `driver candidates: 2 phone, 0 unknown, 1 not a phone - hidden: Magnetic Speaker (hands free unit)`. Reply `"ok":true`. Phone states: CONNECTING_RFCOMM, CONNECTED_RFCOMM, FOUND_COMPATIBLE_WIFI_NETWORK, VERSION_CHECK_COMPLETE, WIFI_PROJECTION_START_REQUESTED, CONNECTING_WIFI, CONNECTED_WIFI, PROJECTION_INITIATED.
- Because the phone dials the head unit by itself with no command (quiet1 broke 2 of 2; 4 dials in each second window), this rig cannot show that the command alone woke the phone. The C5p rule is satisfied (C5p `ssl=0`), so a failure would count; the named run did not fail.

## C3: inside C1's session

**PASS**

- All seven conditions in both tries: "already connected" for the connect and for the poke verb with the name; `refused: another session is live.` for `motorola edge 30 neo`; 0 driver selections, 0 manual pokes, 0 manual-poke requests, 0 byebye; `rend` 3 after the last command; `"connected":true`; 0 phone rfcomm in the window.

## C5p: the old poke from a stopped app

**PASS**

- `Received manual Native-AA poke request for DC:B7:2E:5E:4E:59` 1, `Driver selected: ... automatic` 1, `connect by` 0, `WiFi button on the Bluetooth module route` 0. Baseline: `ssl=0`, `rend` 3, `createGroup SUCCESS` G4 = 1, phone rfcomm 1, command to SSL 10596 ms.

## C2: by MAC, lower case, `no_ui`, from a stopped app

**PASS**

- `18:47:26.540 AapService: connect by MAC dc:b7:2e:5e:4e:59 resolved to DC:B7:2E:5E:4E:59`; explicit pick 1; `ssl=0` (command to SSL 10731 ms); `Not raising the projection, a no_ui command asked for the session only` 1 and `raising the projection by` 0; after `ACTION_RAISE_PROJECTION` `rend` 4 (SSL to raise 3437 ms, inside the phone's patience); `Boot WakeLock` 0; `createGroup SUCCESS` 1 against C5p's 1; phone rfcomm 1.

## C4 and C4c: refusals

**PASS** (C4) and **PASS** (C4c); condition 6 INCONCLUSIVE in both

- `18:49:44.678 AapService: connect by name "NoSuchPhone" refused: no paired phone named.`; `18:49:55.778 AapService: connect by MAC 02:11:22:33:44:55 refused: not paired.`; `18:50:19.610 AapService: connect by name "POCO X3 NFC" refused: not Native AA.` (C4c, mode 0).
- In both captures: `Driver selected` 0, manual poke 0, manual-poke request 0, `WiFi button on the Bluetooth module route` 0, `Boot WakeLock` 0, SSL 0. C4c: `manual Native-AA poke ignored` 0 and `NATIVE` init 0. The three replies read `"ok":true`.
- Condition 6: phone `Creating rfcomm socket` was 5 in C4's window and 3 in C4c. C4's quiet window broke (`quiet1=broken`), and the same self-dialling appeared in C1's and C1d's second windows with no command sent, so these dials are not attributed to the refusals: INCONCLUSIVE.

## C1d: the deep link, lower-case name

**INCONCLUSIVE**

- Condition 1 (quiet) failed in the window used (`quiet1=broken`; second window 4 phone rfcomm dials, 0 head-unit accepts/pokes/SSL). Conditions 2 to 7 met: `AutomationActivity: Received intent. Action:` 1 holding `device_name=poco%20x3%20nfc`; `18:52:09.920 AapService: connect by name "poco x3 nfc" resolved to DC:B7:2E:5E:4E:59`; explicit pick 1; `ssl=0` (command to SSL 9067 ms); `rend` 3; `Boot WakeLock` 0; phone rfcomm 1.

## Anything the brief did not ask about

- The phone dialling the head unit by itself breaks the brief's quiet-window control. `quiet1=broken` in C1 (2 tries), C4 and C1d, and the second window always had 3 to 5 `Creating rfcomm socket` lines on D-POCO with no command. A future brief that wants "the command alone wakes the phone" needs a state where Gearhead is not retrying (for example the phone's Bluetooth off until the command, as `bt.md` describes for a single poke round), or it should drop the control.
- D-MOTO's Gearhead 18.1 kept a stale WPP-over-TCP endpoint (port 5299) for D-POCO; two sessions failed until the vehicle was forgotten on D-MOTO. A fresh D-POCO group SSID per run does not clear it.
- Quirk answers asked for by the brief (to go in `rig-quirks/units/D-POCO.md`): `locksettings set-disabled true` removed the keyguard after `lockNow()`-equivalent state (`kg-off` read `false` in V4n, R5n); `requestDismissKeyguard` lifted the swipe keyguard (V3, V4k, R7k: 3 of 3, 661 to 854 ms); one OTG plug raised one USB permission dialog; one power return raised none.
- Gearhead on D-POCO after a reboot: `NotificationShade` held focus; Home alone did not clear it.
- `rig_preflight.sh` fails when D-POCO Bluetooth is off even though the brief's own PN step 5 would enable it; the skill forbids the host switching a radio, so the operator did.
