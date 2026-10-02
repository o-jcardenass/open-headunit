# external-bt-module-coldstart: round 1 results

**Candidate:** fix/external-bt-module-coldstart @ 4335f0f8160dda32fdcdeafdf2a95bca3b66e6ae       **Baseline:** none (graded on the candidate alone, per the brief)
**APK md5:** 94a1d3c0d8d7d36fd8bf628bb418db5f / n.a.
**Unit:** D-HU (UNISOC MT50, Android 14, rooted shell, `shared_prefs/` owner `u0_a176:u0_a176`, read before and after); D-MOTO as the phone for G2 and G1; D-POCO Bluetooth off for G2 and G1.
**Date:** 2026-10-02
**Evidence:** release `rig-evidence-external-bt-module-coldstart`, asset `external-bt-module-coldstart-round1-captures.zip`, sha256 `eaa9eb62f4ae5eaf591d00a03963fd27a5d45b5b8197a105afd682aae18d9873`

## Round summary

| Run | Verdict |
|---|---|
| R0 build gate | PASS |
| G2 control | PASS |
| G1 hotspot user exit | INCONCLUSIVE (2 cycles, stop rule) |
| G4 refused, then listening | PASS |
| G3a / G3b / G3c / G3d / G3e | PASS / PASS / PASS / PASS / PASS |

## Setup notes

**Files read.** `TESTING-TEMPLATE.md` without sections 7b and 8; `rig-quirks/topics/tooling.md`, `rig-quirks/units/D-HU.md`, `rig-quirks/units/D-MOTO.md`, the external-module and poke entries of `rig-quirks/topics/bt.md`, the hotspot entries of `rig-quirks/topics/wifi.md`, the head of `rig-quirks/units/D-POCO.md`.

**Pre-flight.** It failed twice before the round could start. First D-MOTO was not in `adb devices`; then it was present with WiFi and Bluetooth both off (`wifi_on=0`, `bluetooth_on=0`, `dumpsys` agreed, airplane mode off). The operator brought it back and switched the radios on; the host switched nothing. Passing table:

```
ROLE     ADB      WIFI   BT     SCREEN    BT-PROFILES
D_HU     ok       1      1      Awake     HFP:none A2DP:none
D_MOTO   ok       1      1      Awake     HFP:none A2DP:none
D_POCO   ok       1      1      Awake     HFP:XX:XX:XX:XX:33:59 A2DP:up
PREFLIGHT OK
```

Brief section 5 preflight on D-HU: `rw.zlink.bt.type` and `rw.zj.bt.type` empty; `/dev/auto_serial`, `/dev/rf_serial`, `/dev/zj_bt_serial` all absent; nothing on 3152; `toybox nc` lists `-l` and `-p`. D-POCO `svc bluetooth disable` done after the round started and re-enabled in Cleanup; D-POCO's Bluetooth read 0 before every G2 and G1 cycle.

**R0.** Built in a scratch worktree of the candidate SHA (`rev-parse` gave `4335f0f81`). The brief's 2470/0 reproduced from the test-result XML (2470 tests, 0 failures, 0 errors). `ACTION_QUERY_STATE` reply carries `"commit":"4335f0f8160d"`, `versionCode` 115; `apk_identity.sh` MATCH; the DEX contains `ZbtReopenPolicy` and `HotspotRestartPolicy`. `settings.xml` was identical before and after the install.

**Settings.** Backup `settings-backup-HU.xml` taken before the install. Its delta against what the round needed: `log-level`, `wifi-connection-mode`, `native-aa-wake-damage-verdict`, `external-bt-zbt-transport`, `native-aa-ignore-external-bt`, `native-ap-transport` already read the round's values; `external-bt-blink-transport` and the four hotspot keys were written. No earlier round's backup was diffed beyond this (the previous thread's evidence folder holds none for this file).

**Deviations and errors, in order.**

1. **G2 attempt 1 void (host error).** I wrote `native-poke-all-paired=false` for G2; the brief asks for it only on G4 and G3. The head unit then poked only the one MAC on its wake list. Capture kept as `G2-attempt1-void.txt`.
2. **Brief gap: the wake list.** The brief never mentions `native-poke-bt-macs`, and D-HU's baseline carried D-POCO's MAC only (`DC:B7:2E:5E:4E:59`). With `native-poke-all-paired=true` the head unit still poked D-POCO alone (attempt 2, `G2-attempt2-void.txt`: poke via HFP-AG and HSP-AG to D-POCO failed every 15 s, D-MOTO never poked). Fix: that one `<set>` key was rewritten on the host and pushed as root (D-MOTO `A0:46:5A:97:E4:95` replacing D-POCO's MAC; `diff` shows that single line changed), `chown u0_a176:u0_a176`, `chmod 660`. `native-poke-all-paired` was `true` for G2 and G1, and `false` for G4 and G3. The full backup was restored in Cleanup and read back identical. G2 attempt 3 is the graded run; G1 ran on the same wake list.
3. **Brief bug: `g4_listen.sh`.** `adb shell sh g4_listen.sh` never returns because the backgrounded `sleep | nc` keeps the adb channel, even with redirections added inside the script. The listener is instead started from a host-side background `adb shell "sleep 600 | toybox nc -l -p 3152"` job (`start_listener` in `ebm_common.sh`).
4. **Brief bug: cleanup `pkill -f 'nc -l -p 3152'`.** This toybox answers `pkill: bad -L '-p'`. Listeners were killed by pid through `ps -A -o PID,ARGS` (`stop_listener`). Separately, `adb shell setprop rw.zlink.bt.type ""` drops the empty argument and prints usage; the value was cleared with `adb shell "setprop rw.zlink.bt.type ''"` and read back empty.
5. **Brief bug: the `watch1` helper.** `adb logcat -T 1 | grep -m N` does not return when grep exits; it returns at the timeout with rc 124. G4 attempts 1 and 2 (`G4-attempt1-void.txt`, `G4-attempt2-void.txt`) therefore never started the mid-window listener and saw all seven dials refused. `watch1` was replaced with a poll of the capture file (count of new matches, 0.1 s steps). G2 and G1 used the old helper only for the handshake wait; their grades read the capture, not the helper's return code, so they are unaffected.
6. **No `no_ui` verdict note.** Every run used `--ez no_ui true` as the brief says; see G1.
7. **Phone screen.** D-MOTO's focus read `NotificationShade` rather than the launcher before every G2 and G1 run (HOME keyevent sent first, it did not change). The handshake still came up, so the state did not matter here.
8. **Windowing.** Every count is windowed by the run's own markers (or from the `ACTION_DISCONNECT` receiver line to the end marker for G1). D-HU honours `logcat -c`.
9. **Scripts.** Used `rig_devices.sh`, `rig_preflight.sh`, `build_hur.sh`, `run_unit_tests.sh`, `apk_identity.sh`, `set_hu_prefs.sh`. Added to `hur-wifi-test-scripts/`: `ebm_common.sh`, `ebm_g2.sh`, `ebm_g1.sh`, `ebm_g4.sh`, `ebm_g3.sh`, `g4_listen.sh` (kept for reference, unusable as is, see 3). Every session script refuses to start on a failed pre-check and ends with `script exit=`.
10. **Markers.** `ACTION_LOG_MARKER` is not in `CONFIGURING` on this SHA, so `allow-external-configuration` was not needed or written.
11. **Host slip.** One `pgrep -f` in the host shell matched its own command line and ended that shell (exit 144); the stalled G4 attempt-1 processes were then killed by pid. No device state depended on it.

## R0: Build gate

**PASS**

- 2470 tests, 0 failures, 0 errors; md5 `94a1d3c0d8d7d36fd8bf628bb418db5f`; reply `"commit":"4335f0f8160d"`; DEX carries both new classes.

## G2: Control, ordinary hardware is untouched

**PASS**

- Settings written: all-runs table, `native-ap-transport=0`, hotspot keys cleared, `native-poke-all-paired=true`, wake list as in Setup note 2.
- Radio state: D-MOTO airplane mode on 5 s before `ACTION_START_WIRELESS`, off after 20 s with Bluetooth and WiFi re-enabled; D-POCO Bluetooth off.
- Discard-rule check: clean (`MATCH! Starting AapService` 0, `Magic Garbage` 0, one `SSL handshake complete`, `p2p-wlan0-0` only). Two earlier attempts void, see Setup notes 1 and 2.
- Decisive lines: `14:24:11.148 ACTION_START_WIRELESS` receiver line; `14:24:45.039 NativeAA: Connection accepted from motorola edge 30 neo`; `14:24:54.649 WirelessServer: Incoming connection detected from /192.168.49.28`; `14:24:55.160 AapSslContext.performHandshake | SSL handshake complete`.
- Measurements: receiver line to `SSL handshake complete` = 44.0 s (limit 90 s). `asking the vendor daemon whether it will carry Android Auto` = 0; `NativeAA: [ZBT]` = 0; `the vendor daemon is still being asked for a route` = 0.

`createGroup SUCCESS` read 0 in this run (the group already existed), so the second-group discard rule could not trip.

## G1: Hotspot user exit

**INCONCLUSIVE**

- Settings written: all-runs table plus the G1 table; `hotspot-teardown-proven-unsafe=false` re-written and read back before each cycle; reverted afterwards with the four-key rule, `auto-enable-hotspot=false` as in the backup, `cmd wifi stop-softap` ("Soft AP stopped successfully"), `wlan2` gone.
- Radio state: AP started by hand, `wlan2 inet before=1` both cycles; D-MOTO as in G2.
- Discard-rule check: clean (`MATCH!` 0, `Magic Garbage` 0).

| Cycle | Handshake | `ACTION_DISCONNECT` to end | restart lines | outcome line | final `wlan2` inet | Cycle verdict |
|---|---|---|---|---|---|---|
| 1 | 14:27:34.510 (33.5 s after start) | 14:29:11.686 to 14:29:56.809 | 0 | none | 1 | INCONCLUSIVE |
| 2 | 14:32:01.560 | 14:33:36.986 to 14:34:22.107 | 0 | none | 1 | INCONCLUSIVE |

Stop rule: 2 consecutive INCONCLUSIVE.

Why the new code never ran (host read of the capture and of the candidate source, not an executor claim): after `AapService.onStartCommand | Disconnect action received.` the transport, audio and decoder stopped (`Decoder stopped: CommManager: doDisconnect` at 14:29:11.881 in cycle 1), then the app logged nothing at all for the remaining 45 s. `AapService.onDisconnected` never ran: its first INFO line (`AapService: Stopping GpsLocationService and NightModeManager since connection is disconnected`) and `AapService: Disconnected. wasPlayingBeforeDisconnect=` are both absent, so the user-exit branch that restarts the hotspot was never entered. In `AapService.observeConnectionState` the `Disconnected` branch calls `onDisconnected` only `if (hasEverConnected)`, and `hasEverConnected` is set only on `TransportStarted`. Both captures contain `session state connecting` and `session state connected` but no `session state projecting` and no `TransportStarted`: a `--ez no_ui true` session never starts projecting, so it never sets the flag. The same verb on the previous thread's captures, with a projecting session, logs `AapService: Disconnected.` (count 1 in `A-R1a` and `A-R4`). This is behaviour of the run's setup (headless arm), not evidence about the restart code, and the brief's own prediction that D-HU cannot show this path also holds. A projecting session (start through `ACTION_START_WIRELESS_SCAN` with the UI) is what a later round would need; it was not substituted here.

## G4: Refused, then listening

**PASS**

- Settings written: all-runs table, WiFi Direct keys, `native-poke-all-paired=false`; `rw.zlink.bt.type=extra` written with the app stopped and read back `extra`; D-MOTO in airplane mode.
- Discard-rule check: clean (`MATCH!` 0). `createGroup SUCCESS` = 1 (the launcher's own start). Two earlier attempts void, see Setup notes 3 and 5.
- T0 = `14:42:06.484` (`asking the vendor daemon whether it will carry Android Auto`).
- Decisive lines: `14:42:13.656 AutomationMarker: G4-listener-up`; `14:42:24.561 [ZBT] the daemon is on 127.0.0.1:3152 but did not answer within 3s`; `14:42:54.601 [ZBT] nothing is listening on 127.0.0.1:3152. This unit carries the external-Bluetooth markers but has no vendor daemon to carry Android Auto`; `14:43:24.612 [ZBT] channel open to the Bluetooth module daemon on 127.0.0.1:3152`.

| Condition | Measured |
|---|---|
| 1. four refusals, listener up before T0 + 15 s | refusals at T0 +0.02, +1.03, +3.04, +7.05 s; `G4-listener-up` at T0 +7.17 s |
| 2. one silent line at T0 +16 to +20 s, no refusal between | T0 +18.08 s; none between |
| 3. zero `nothing has listened ... for`, zero `external Bluetooth module detected (` | 0 and 0 |
| 4. first carrier refusal within 3 s of the silent line; waits 2, 4, 8, 16, 30; gaps match | first refusal 0.015 s after; waits `2`, `4`, `8`, `16`, `30`; gaps 2.007, 4.005, 8.006, 16.007 s |
| 5. one loud warning, at least 29 s after the first carrier refusal, at the 5th refusal | 1 line; 30.025 s after the first; 1 ms before the 5th refusal line (same event, the warning prints first) |
| 6. one channel open within 31 s of `G4-relisten` | 1; 29.8 s (`14:42:54.813` to `14:43:24.612`) |

Condition 5 is read as met: the brief says "no earlier than the 5th carrier refusal line (within 100 ms of it)", and the loud line precedes the 5th line by 1 ms because the same refusal logs it first.

Not graded: after the channel opened the carrier logged `a wake could not go out within 60s, so it is dropped.` (no phone) and `channel ended: closed locally` after `ACTION_STOP_WIRELESS`.

## G3: A daemon that never comes back, the press, and the stop

Start `14:44:27.740`, property still set, 3152 not listening (checked for `LISTEN`), D-MOTO in airplane mode. `native-poke-all-paired=false`. Zero `daemon refused, retrying in` lines in the whole run.

### G3a

**PASS**

- T0 = `14:44:28.048`. Seven refused dials at T0 +0.02, +1.03, +3.04, +7.06, +15.07, +23.09, +30.02 s (expected 0, 1, 3, 7, 15, 23, 30).
- Six `dialling again in` lines, gaps `1`, `2`, `4`, `8`, `8`, `6`. One `nothing has listened ... for 30s` at T0 +30.02 s (`14:44:58.069`); `external Bluetooth module detected (` at `14:44:58.076`, 7 ms later.

### G3b

**PASS**

- `14:45:03.238 AapService: WiFi button: the vendor daemon refused 5s ago; asking it again.` (the `nothing has listened` line was 5.17 s earlier); `14:45:03.239 WiFi button on the Bluetooth module route: START_HANDSHAKE`; `asking the vendor daemon` at `14:45:03.243`, 22 ms after the press receiver line (`14:45:03.221`); three refused dials at `14:45:03.252`, `04.262`, `06.272`.
- Verb reply `"ok":true`. Screenshot 1 s after the press (`G3b-screen.png`): the launcher home screen, no toast on it. Not graded; the verb does not go through `HomeFragment`.

### G3c

**PASS**

- `ACTION_STOP_WIRELESS` receiver line `14:45:06.404`. From 0.5 s after it to `G3c-rearm` (`14:45:49.248`): 0 refused dials, 0 `dialling again in`, 0 `nothing has listened`, 0 `asking the vendor daemon`, 0 `external Bluetooth module detected (`. Lines after the stop: 0.
- After `G3c-rearm`: 1 `asking the vendor daemon` at `14:45:49.359`, 43 ms after the `ACTION_START_WIRELESS_SCAN` receiver line (`14:45:49.316`); the window ended with 1 `nothing has listened` at `14:46:19.378`.

### G3d

**PASS**

- `G3d-cached` to `G3d-press`: 1 `external Bluetooth module detected (` at `14:46:24.622`, 31 ms after the scan receiver line (`14:46:24.591`); 0 `asking the vendor daemon`.
- After the press (`14:46:34.747`, reply `"ok":true`): `WiFi button: the vendor daemon refused 15s ago; asking it again.` (`14:46:34.762`; the cached line was 15.38 s earlier), `WiFi button on the Bluetooth module route: REBUILD_LAUNCHER` (`14:46:34.763`), `asking the vendor daemon` at `14:46:34.796`, 49 ms after the press line.

### G3e

**PASS**

- `G3e-press` `14:46:44.825`, receiver `14:46:44.902`: `WiFi button on the Bluetooth module route: WAKE_PHONE` at `14:46:44.918`; `the vendor daemon is still being asked for a route, so the wake waits for it.` at `14:46:44.919`, 1 ms after it. Zero `asking the vendor daemon` and zero `asking it again.` in the window.
- Refused dials from `G3d-press` to `G3-end`: `14:46:34.806`, `35.816`, `37.828`, `41.842`, `49.857`, `57.873`, `14:47:04.813`; shortest gap 1.010 s (limit 0.5 s). Exactly 1 `nothing has listened` in the window (`14:47:04.814`).

Report items: G3a offsets above; G3e route `WAKE_PHONE`, shortest refused-dial gap 1.010 s; G3b printed age 5s; G3c lines after the stop: 0.

Observation on the discard rule: G3 shows 4 `createGroup SUCCESS` lines (`14:44:28.641`, `14:45:49.910`, `14:46:25.661`, `14:46:35.385`), one per launcher start the run causes on purpose (initial arm, scan re-arm, scan on a cached "no", rebuilt launcher). The rule is for a run meant to form one group; this run is not.

## Anything the brief did not ask about

- **`native-poke-bt-macs` decides which phone a Native AA run wakes.** A brief that names D-MOTO as the phone must either set that list or say it is already D-MOTO. The rig's baseline carries D-POCO, and turning D-POCO's Bluetooth off does not hand the poke to D-MOTO; the head unit keeps poking the one MAC and failing every 15 s.
- **`--ez no_ui true` sessions skip `onDisconnected` entirely** because `hasEverConnected` is set only on `TransportStarted`. For this branch that means any run of the user-exit hotspot path needs a projecting session. It is outside what this round measures, and it holds on `main` as well (the guard is not in the candidate's two commits).
- **The brief's listener and helper recipes are not reliable on this rig** (adb shell hang on a device-side background process, `watch1` pipe not closing, `pkill -f` and empty `setprop` argument). The replacements are in `ebm_common.sh`.
- **A leftover `TIME_WAIT` on 3152 matches `netstat -ltn | grep 3152`** after a listener run. A "nothing on 3152" check should grep for `LISTEN`.
- **D-MOTO's launcher was not in focus** (`NotificationShade`) in every run; it did not stop the handshake.
