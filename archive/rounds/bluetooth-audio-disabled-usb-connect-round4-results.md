# bluetooth-audio-disabled-usb-connect: round 4 results

- Candidate: `fix/bluetooth-audio-disabled-usb-connect` `fff96699` (`commit` reply `fff96699480d`, versionCode 116), APK md5 `234caaadff2d4731e006591a0c69bb2a`, reused from round 3 (no build)
- Baseline: none (measurement round)
- Unit: D-MOTO (Android 14, Android Auto `17.9.664004`) as the phone, **D-HP (Android 4.2.2) as the USB host running the app**, D-HU (Bluetooth name Navegadortz2) as the plain Bluetooth car unit with its app stopped, D-POCO as the second phone (receives the call only)
- Date: 2026-10-08
- Outcome: **the round stopped after R1 on the brief's abort rule** (`ph.abort` 2). R1 was repeated once with D-POCO as host (the round 3 layout) at the operator's request and read the same (`ph.abort` 1, no disabler). Stage S and R2a to R3b were not run.

## Setup notes

1. **Layout change (the main deviation).** D-POCO could not be the host: its battery was low and it cannot place a call. D-SAM has no USB host support. D-HP (API 17) hosted instead, and D-POCO became the callee. D-HP's only port is the OTG port, so D-HP ran on wireless adb (`tcpip 5555`) and was off the PC for every session. The candidate APK was installed on D-HP from the PC over USB adb (a wireless install of the 21 MB file took 97 s). `settings_backup_hp.xml` is D-HP's pre-round `settings.xml`. The brief's keys were written to D-HP with the app stopped (`run-as` on 4.2: the file is pushed to `/data/local/tmp` and copied in, because `exec-in` is not available there). `bt-address` was D-HU's `11:46:03:10:33:59`. `ACTION_QUERY_STATE` on D-HP replied `fff96699480d`. D-HP's own Bluetooth could not be turned off (a `service call bluetooth_manager 8` is denied for the shell). It is not bonded to D-HU or D-MOTO (checked in both bond lists before the round), so it was left on.
2. **Call direction reversed.** The brief rings D-MOTO from another phone. D-POCO has a SIM with no minutes and cannot dial out, so D-MOTO dialled D-POCO (`am start -a android.intent.action.CALL`), D-POCO answered with `KEYCODE_CALL` within 3 s of ringing, and both ends hung up 20 s later. The call is therefore an **outgoing** call on D-MOTO; incoming-call routing is untested. D-MOTO is dual SIM and shows a "Choose SIM for this call" dialog on every dial. That dialog cost one injected tap per call (SIM 1, the TIGO line; its row is clickable at 540,1384).
3. **No scripted route opens D-HU's screen on D-MOTO.** `android.settings.BLUETOOTH_DEVICE_DETAIL_SETTINGS` does not resolve (`unable to resolve Intent`; `cmd package query-activities` finds none). My first Stage 0 reading of `NOROW com.android.settings true` was a false positive: the screen the operator had left open. A direct `SubSettings` start is `not exported`. Resuming Settings lands on its root. The route used: `am start -a android.settings.BLUETOOTH_SETTINGS` (the device list), then one injected tap on the gear next to Navegadortz2 (965,805 area, found from a `uiautomator dump` by `navxy.py`). `ROUTE=resume`, `LABEL` "Media audio" (English UI). The Media audio row also reads with Phone calls, Internet access, Contacts and call history sharing, Text Messages (rows column).
4. **Injected taps** (the operator authorized them and asked that the hold taps be injected). Stage 0: 4 taps while finding the route by hand-guided navigation (Connected devices twice because a stale background job pressed HOME, the gear twice) and 3 SIM chooser taps (the first did not register, the retry did). R1: 2 taps, `540,1384` (SIM chooser, 20:21:48) and `965,1378` (the H9 Media audio switch, 20:22:33). Close: 1 tap, `965,1324` (the switch back on, 20:32:15). Every tap is in `taps.log` of the asset. Stage 0's total is over 5; it is not a run.
5. **Screen kept in front.** To keep taps few, `home` was made a no-op and `ui_open` re-navigates only when D-HU's details screen is not in front. Readings after a session starts therefore come from a screen that was open before the session. **The `live` reading may be stale** (see R1).
6. **A mistaken bond.** On an operator reply of "paired" to a request to connect, D-MOTO was bonded with D-HP (`88:33:14:53:1A:75`) at 19:47. It had not been asked for. It was removed by hand at the close (`BOND_STATE_NONE` 20:34:38). Bonds after equal the list from before (10 entries, D-HU, D-POCO and the rest unchanged).
7. **Harness fixes.** (a) `nl` (the start line for finding the session) was taken after the plug cue, so an operator who plugged early made the run miss `SSL handshake complete`: R1 attempt 1 was voided for that, and `orun` now takes the line before the cue (also R3b's second plug). (b) `call()` has a function-level `wc_ "$PCAP"` that read stdin when `PCAP` was unset in a fresh shell and hung one Stage 0 call until the awk was killed by pid. (c) A stray HOME from a finished background job landed between a launch and its tap once (tap logged as spent). (d) `av` now reads 14 lines after `- STREAM_MUSIC:` for `Devices:` (the brief's 4 lines never reach it). Files: `lib1008r3.sh` and `lib1008r4.sh` as in the brief, plus `lib1008r5.sh`, `navxy.py`, `s0.env`, `r4.env`.
8. **Stage 0 values.** `linkst` was `none` until the operator connected D-MOTO to Navegadortz2 by hand (D-HU cycle and D-MOTO Bluetooth cycle did not reconnect); then `A2dpService=Connected HeadsetService=Connected`. `BTCYC=cmd`. `A2DP_PLAY_RE='mIsPlaying: true'` (1 playing, 0 stopped on D-MOTO). `SINK_PLAY_RE='playbackState=PlaybackState \{state=3,'` (1 playing, 0 stopped on D-HU; `Standby: no` in `dumpsys media.audio_flinger` also separates them). `MEDIA=/sdcard/Music/ohu1008.mp3` (a copy of an existing 5.2 MB file, 216738 ms). `ROUTE_RE='Entering state Active[A-Za-z]*Route|Current state: Active[A-Za-z]*Route'`. Flags count (`a2dp_fix_car_list` and others): 0. `dh.wake` 0. Step 4 (D-POCO's own screen on D-MOTO) was **not done** to save taps. Not done either: the settings diff against round 3's backups and the `stat` of both `shared_prefs/` directories.
9. **Stage 0 call.** The first attempt was answered late (about a minute after ringing) and connected to voicemail at 20:02:32 (the route went from `ActiveBluetoothRoute` to `ActiveEarpieceRoute` at that instant). The operator then asked that calls never trigger voicemail, so the script answers within seconds. The answered call at 20:03:50 to 20:04:15: the route entered `ActiveBluetoothRoute` at dial (20:03:24.983) and left it for `ActiveEarpieceRoute` at 20:03:50.220, when the call connected.
10. **Close (after R1p).** The same steps were repeated after R1p, with D-POCO's `settings.xml` restored (same serialization difference as D-HP's and D-HU's), the switch `ON`, A2DP and hands-free connected, D-POCO's Bluetooth on, bonds equal to the earlier close, and both phones back to USB.
11. **Close (after R1).** D-HP's and D-HU's `settings.xml` equal their backups element for element; the only difference is how an empty string is written (`<string name="x"></string>` against `<string name="x" />`) and the XML declaration, from the host editor. D-POCO's Bluetooth was turned back on (`enabled: true`). D-MOTO `svc power stayon false`. Both phones went back to USB adb. No `adb logcat` was left running. The capture loops started by a shell that had since ended were killed by pid.

## S0

**PASS** (gate met: a dump names D-HU and its Media audio row reads `ON`)

Route and regexes are in setup notes 3 and 8. The Stage 0 call: dial 20:03:24, ringing 20:03:50.9, answered 20:03:55.2, route `ActiveEarpieceRoute` 5 s after the answer.

## R1: clean end, call, hold test during the session

**INCONCLUSIVE** (not a valid control: the disabler did not run)

Attempt 1 is void (setup note 7a). Attempt 2 (`R1`, markers 20:20:36 to 20:24:54): `hu.ssl` 1 (SSL handshake complete 20:20:57.140 on D-HP's clock), announce line `carAddress=11:46:03:10:33:59 (bt-announce=real)` once, `hu.announce_empty` 0, `hu.media_audio` 2, `hu.live_at_stop` 1, disconnected `user_exit` at 20:23:44.8, `hu.fatal` 0, `dh.wake` 0, `ph.bond_none` 0, `ph.noroute` 0. The keys were read back as `bt-announce` real, `bt-address` D-HU's, `Google`, `Desktop Head Unit`.

**Control not valid:** `ph.disabler` 0 and `p.disabled_by_gh_before_stop` 0. The phone logged `disabling A2dp route while in projection` and `Routing media audio via device type 0` at 20:20:56.619 and no `disabling A2dp via profile disabler`. The stored policy for D-HU stayed at its starting value: `p.P0=100` at 20:20:59.150, no `gearhead:car` setter in the session. The switch read `ON` at `pre` and `live` with `policy=none` (no stored line yet).

**Abort lines, `ph.abort` 2:** `D/CAR.BT.SVC.LITE: Sending a pairing request` at 20:20:57.805 (0.67 s after the SSL line) and 20:22:57.806. Context of the first:

```
20:20:57.804 D/CAR.BT.LITE: BluetoothFsm Transition from STATE_WAITING_FOR_BLUETOOTH_PROFILE_UTIL to STATE_REQUESTING_CAR_PAIRING_PREPARATION
20:20:57.804 D/CAR.BT.LITE: isPairing
20:20:57.805 D/CAR.BT.SVC.LITE: requestCarPairingPreparation
20:20:57.805 D/CAR.BT.LITE: invalidateAuthenticationData
20:20:57.805 D/CAR.BT.SVC.LITE: Sending a pairing request
20:20:57.810 I/CAR.BT.SVC.LITE: Sending pairing request using method: 4
```

`Bluetooth pairing method chosen: 2` and `4` were logged at 20:20:56.608 for car address `11:46:03:10:33:59`. Under the brief's rule 9 the stage stopped here. No bond changed (`ph.bond_none` 0; Navegadortz2 still bonded). Whether rounds 1 to 3 had these lines was not checked here. Whether the different host (D-HP, API 17) or the different Android Auto path (a pairing flow, not the profile disabler) explains the missing disabler was not separated.

**Hold test (H9) `REFUSED`, and it is my tap's doing.** `hold.tsv`:

```
R1	H1	REFUSED	dumps=false x18	h.set 20:22:32.732 pid=14485 owner=com.android.settings value=0	h.on=none	h.back=none	h.last_saved=0
```

The switch read `ON` before the tap (checked true, policy never set to 0). The injected tap at 20:22:33 therefore **turned it off**: `A2dpService(11187): Saved connectionPolicy 11:46:03:10:33:59 = 0` at 20:22:32.735, owner `com.android.settings`. The 18 dumps after it all read unchecked. This is not a grey state and not Android Auto refusing a change: the brief's hold test assumes the switch is off when it is run (it runs only "where the switch reads off"), and R1's `hold-live` ran it with the switch on. With a stale `live` reading I could not have known; the policy line shows it was on.

All readings (`tsum`):

```
R1 t.pre phone=20:20:43.189 state=ON checked=true sw_enabled=true row_enabled=true policy=none
R1 t.live phone=20:21:38.030 state=ON checked=true sw_enabled=true row_enabled=true policy=none
R1 t.hold1 (18 readings) 20:22:36.625 to 20:23:35.284 state=OFF checked=false sw_enabled=true row_enabled=true policy=0
R1 t.end+5 phone=20:23:51.377 state=OFF ... policy=0
R1 t.poll (10 readings) 20:23:56.464 to 20:24:41.561 state=OFF ... policy=0
R1 t.end+60 phone=20:24:49.731 state=OFF checked=false sw_enabled=true row_enabled=true policy=0
R1 t.grey_polls=0 first=none last=none
```

`p.reading=NOT_RESTORED` (the 0 came from `com.android.settings`, not Android Auto), `p.restore=none`, `p.non_gh_setters_before_read=1` (the hold tap). The earlier `pre` line at 20:14:50 belongs to the voided attempt.

**Second copy.** `av.tsv`: `R1 live moto_a2dp_play=0 dhu_sink_play=1 music_dev=Devices: bt_a2dp(80)`; `R1 after-hold1 moto_a2dp_play=0 dhu_sink_play=0 music_dev=Devices: speaker(2)`. D-MOTO's STREAM_MUSIC device was `bt_a2dp(80)` at `live` while D-MOTO's A2DP reported not playing. The two sides disagree and I did not resolve it; the D-HU field may carry the state of the earlier Bluetooth media session.

**Call.** `calls.tsv`: `R1 route=Entering state ActiveBluetoothRoute Entering state ActiveEarpieceRoute Entering state ActiveBluetoothRoute Entering state ActiveEarpieceRoute noroute=0`. Dial 20:21:41.9, ringing 20:22:01.5, answered 20:22:05.8, call end 20:22:25.8. Call audio started on the Bluetooth route at dial and moved to the earpiece when the call connected, as in Stage 0. No `No high priority audio routes available` line.

## R1p: R1 repeated with D-POCO as the host (round 3 layout)

**INCONCLUSIVE** (not a valid control: the disabler did not run, the same as R1 on D-HP)

Done after R1, at the operator's request, to separate the host from the Android Auto behaviour. D-POCO hosted (wireless adb, Bluetooth off, the candidate APK, the brief's keys), D-MOTO the phone, D-HU quiet, D-MOTO plugged into D-POCO's OTG port at the cue with "USB controlled by" set to "This device" on D-MOTO by the operator. The call: D-MOTO dialled D-POCO, which is also the host here. The hold test ran only where the switch reads off (a guard added after R1). Two earlier starts were stopped before any session: one because `ui_open` took the device list (`NOROW`) as the details screen and started a repair session, and one because D-MOTO was plugged in before the cue. Their files are in `out2/aborted1` and `out2/aborted2` of the asset. Markers 20:45:30 to 20:48:25.

Valid by the brief's items 1 to 8: `hu.ssl` 1 (SSL handshake complete 20:45:49.573), announce `carAddress=11:46:03:10:33:59 (bt-announce=real)` once, `hu.announce_empty` 0, `hu.media_audio` 2, `hu.live_at_stop` 1, `user_exit` disconnect 20:47:16.6, `hu.fatal` 0, `dh.wake` 0, `ph.bond_none` 0, `ph.noroute` 0.

**Control not valid:** `ph.disabler` 0, `p.disabled_by_gh_before_stop` 0, `p.reading=OTHER`, no setter of any kind in the run (`p.non_gh_setters_before_read` 0). `p.P0=100` at 20:45:57.701. The phone logged `disabling A2dp route while in projection` at 20:45:50.494 and, at 20:45:51.668 (2.1 s after the SSL line), the same pairing state as R1 (`STATE_REQUESTING_CAR_PAIRING_PREPARATION`, `Sending a pairing request`). `ph.abort` 1. No bond changed.

Readings (`tsum`):

```
R1p t.pre phone=20:45:35.582 state=ON checked=true sw_enabled=true row_enabled=true policy=none
R1p t.live phone=20:46:17.487 state=ON checked=true sw_enabled=true row_enabled=true policy=none
R1p t.prehold1 phone=20:47:11.165 state=ON checked=true sw_enabled=true row_enabled=true policy=none
R1p t.end+5 phone=20:47:24.497 state=ON checked=true sw_enabled=true row_enabled=true policy=none
R1p t.end+60 phone=20:48:22.511 state=ON checked=true sw_enabled=true row_enabled=true policy=none
R1p t.grey_polls=0 first=none last=none
```

Hold test: `R1p H1 SKIPPED_SWITCH_ON (no tap: the switch is not off)`. Second copy: `av live` `moto_a2dp_play=0 dhu_sink_play=1 music_dev=Devices: bt_a2dp(80)`, `after-hold1` `moto_a2dp_play=0 dhu_sink_play=0 music_dev=Devices: bt_a2dp(80)`. Call: dial 20:46:20.8, ringing 20:46:39.3, answered 20:46:43.2, call end 20:47:02.7, route `ActiveBluetoothRoute` then `ActiveEarpieceRoute`, twice (the same as R1), `noroute=0`. Taps: 2 (the gear at 943,805 in the pre-check and the SIM chooser at 540,1384).

**Reading across R1 and R1p:** the disabler was absent and the pairing flow was present with both hosts (D-HP, API 17, and D-POCO, the round 3 host), so the host does not explain the difference from rounds 1 to 3. What changed on D-MOTO's side between round 3 and now was not isolated (Android Auto's stored car list, D-MOTO's bonds, or its Bluetooth state). The switch stayed `ON` and enabled in both runs for as long as nobody touched it.

## R1x, R2a, R2b, R3a, R3b, S1, S2, S3, S4

**UNTESTABLE** (not run: the round stopped on the abort rule)

The brief says to stop the stage on an abort line and go to the close. R1 and R1p are not valid controls, so Stage S would have no control. None of these runs were started.

## Anything the brief did not ask about

- **The disabler path differed from rounds 1 to 3, with either host.** D-HP as host (R1) and D-POCO as host (R1p) both gave a pairing flow (`isPairing`, method 4, 0.67 s and 2.1 s after the SSL line) and no profile disabler. The next question is what changed on D-MOTO's Android Auto side since round 3.
- **A switch can read `ON` while the policy is unset.** At `pre` and `live` the stored policy had no line in the window (`policy=none`) and `p.P0` was 100.
- **D-MOTO shows a SIM chooser on every dial.** A dial script needs one tap or a stored default.
- **Android 4.2 host limits:** no `exec-in`, no `service call` for Bluetooth, no Bluetooth adapter shell. D-HP's Bluetooth stayed on for the round.
- **Evidence:** release `rig-evidence-bluetooth-audio-disabled-usb-connect-round4`, asset `bluetooth-audio-disabled-usb-connect-round4-captures.zip` (14723607 bytes), sha256 `fc5a9c1354cf6fc8a1572b74a21bac4a4cf2de8a689dfbaaf32fa0c21de56bc3`. The voided attempt's files are in `R1-void1/`. APKs are not in the zip.
