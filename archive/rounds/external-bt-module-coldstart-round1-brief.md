# external-bt-module-coldstart: round 1 brief

Publish as `external-bt-module-coldstart-round1-brief.md`. First round of this thread; no earlier
brief or results file exists for it.

## 1. Build and baseline

- Branch `fix/external-bt-module-coldstart` on the fork, tip **`4335f0f8`**
  (`4335f0f8160dda32fdcdeafdf2a95bca3b66e6ae`), two commits on `main` `93c065dd`. New branch, never
  rewritten.
  ```bash
  git fetch fork fix/external-bt-module-coldstart
  git checkout -B fix/external-bt-module-coldstart fork/fix/external-bt-module-coldstart
  git rev-parse --short HEAD    # 4335f0f8
  ```
  The two commits: `f55245b7` (re-dial a refusing vendor daemon for 30 s, carrier reopen backoff,
  WiFi button re-asks a cached refusal, MAC-less `ACTION_NATIVE_AA_POKE` verb), `4335f0f8` (hotspot
  restart read back after a hold).
- JVM gate: **2470 tests, 0 failures** on this SHA from the author's side. A failure stops the round.
- No baseline APK: every run is graded on the candidate alone.
- APK identity: `send ACTION_QUERY_STATE` reply carries `commit=4335f0f8160d`. If the md5 cannot settle
  it, the DEX carries the new classes `ZbtReopenPolicy` and `HotspotRestartPolicy`, which `main` has
  not.

## 2. What this is and why

Head units whose Bluetooth is an external module reach it through a vendor daemon on
`127.0.0.1:3152`. On a reporter's unit that daemon restarts once in the first ~15 s after ACC on. The
app asked it once at arming: a dial landing inside the restart got `ECONNREFUSED`, which was graded
"no daemon here" and cached for 10 minutes. The route was then `BLOCKED`, nothing re-dialled, the
phone knocked on the module every ~12 s for minutes with nobody listening, and the main screen's WiFi
button only repeated the cached refusal as a toast. Exit and reopen worked because a new process has
no cached answer. When the dial instead landed just after the restart, the module carrier's fixed
30 s reopen delay turned a 2.8 s daemon restart into a 33 s wait from arming to the module channel.
Separately, the same unit's hotspot restart on a user exit came up and fell within 20 ms while the
app logged success.

What the candidate does:
- **A refused dial during a bring-up is re-dialled** at 1, 2, 4, 8, 8 s gaps, clamped to a 30 s
  window from the first refusal (dials at about 0, 1, 3, 7, 15, 23, 30 s). Only the last refusal of a
  full window is cached as "no". A daemon that listens but stays silent still takes the module route,
  as before. A stop during the window abandons it and caches nothing.
- **The carrier's reopen backs off** 2, 4, 8, 16 s, then 30 s, and prints its loud "no vendor
  daemon" warning only once the refusals have lasted 30 s.
- **The WiFi button on a cached refusal** forgets it and re-arms the module route, which measures
  again. A launcher that skipped its network because of a cached refusal is rebuilt.
- **A restarted access point is read back 3 s after it comes up**; if it fell, it is asked for once
  more after 5 s, and either outcome is logged.

## 3. What is different about this round

- **No rig unit runs the vendor daemon.** D-HU is put on the external-module detection path with
  root: `setprop rw.zlink.bt.type extra` (`rig-quirks/topics/bt.md`; `ExternalBtPolicy` reads
  `rw.zlink.bt.type` and `rw.zj.bt.type`). With nothing on port 3152 every dial is a refusal, which
  is exactly log 2's state. A `toybox nc -l` listener stands in for a daemon that has come back: it
  accepts and never answers, so its verdict is "listening but silent". A daemon that **answers** is
  not reachable on this rig and stays with the reporter.
- **The property makes Native AA refuse on D-HU**, so G4 and G3 run last and the property is cleared
  with a force-stop at the end (Cleanup). `BluetoothHelper.externalBtEvidence` is read once per
  process, so the property is written with the app force-stopped.
- **The main-screen WiFi button on the module route is a verb from this build on.**
  `ACTION_NATIVE_AA_POKE` with no `extra_mac` now relays exactly what HomeFragment sends on that
  route (`press_module_wifi`, §5). It skips HomeFragment, whose own new INFO line is on the `REFUSED`
  branch that a ZLink-class unit no longer reaches. A reply with `"ok":false` or no
  `AutomationReceiver: ...ACTION_NATIVE_AA_POKE` line voids G3's press steps.
- **G1 is expected INCONCLUSIVE on D-HU.** Its access point is known not to come back after an app
  stop/start (`UserExitHotspotPolicy`'s own comment; `rig-quirks/units/D-HU.md`: the app's own hotspot
  start rarely brings the AP up in its window). The new code runs only after a first ask that brought
  the AP up. G1 is still worth its cycles as a regression guard: every user exit must end in exactly
  one outcome line.
- Units: **D-HU** head unit (`adb root` first), **D-MOTO** phone for G2 and G1. D-POCO's Bluetooth
  off for G2/G1 (`rig-quirks/topics/bt.md`). G4 and G3 need no phone: D-MOTO in airplane mode.
- Log level **INFO** (`log-level` 2). Every line this round grades is `AppLog.i` or `AppLog.w`, and
  D-HU's driver stack floods logcat at VERBOSE.
- Leave the rig's audio settings alone (AAC on, latency multiplier, queue 20): deliberate worst case,
  and nothing here grades audio.

## 4. Settings keys

Back up `settings.xml` first and diff against the last round's backup; state the delta in Setup notes.
`stat` D-HU's `shared_prefs/` and report its owner (D-HU quirk). Write with the app stopped, read back.

**All runs:**

| Key | Type | Value | Why |
|---|---|---|---|
| `log-level` | int | `2` | INFO |
| `wifi-connection-mode` | int | `3` | Native AA |
| `native-aa-wake-damage-verdict` | int | `0` | a latched verdict stands every poke down |
| `external-bt-zbt-transport` | boolean | `false` | the route must be measured, not forced |
| `external-bt-blink-transport` | boolean | `false` | same |
| `native-aa-ignore-external-bt` | boolean | `false` | same |

**G2, G4, G3 (WiFi Direct):** `native-ap-transport` int `0`, and the four hotspot keys cleared
together: `hotspot-ssid` `""`, `hotspot-password` `""`, `static-bssid` `"0"`, `hotspot-interface` `""`.

**G1 only (hotspot):**

| Key | Type | Value |
|---|---|---|
| `native-ap-transport` | int | `1` |
| `hotspot-interface` | string | `wlan2` |
| `hotspot-ssid` | string | `OHU-HOTSPOT` |
| `hotspot-password` | string | `ohutest12345` |
| `static-bssid` | string | `0` |
| `auto-enable-hotspot` | boolean | `true` (the user-exit restart is gated on it) |
| `hotspot-teardown-proven-unsafe` | boolean | `false`, **re-written before every cycle** (a failed restart latches it `true`, and then the exit only warns) |

**G4 and G3:** `native-poke-all-paired` boolean `false`.

`ACTION_LOG_MARKER` is not in `AutomationCommandPolicy.CONFIGURING` on this SHA, so
`allow-external-configuration` is not needed.

## 5. Helpers and preflight

```bash
PKG=com.andrerinas.headunitrevived
HU=27870808938846; MOTO=ZY22GC3BM4; POCO=4f4027e9      # confirm with rig_check_devices
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
send() { a=$1; shift; adb -s $HU shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; }
# The main screen's WiFi button on the module route: the poke verb with no MAC.
press_module_wifi() { send ACTION_NATIVE_AA_POKE; }
cap() { adb -s $HU logcat -G 16M; adb -s $HU logcat -c; stdbuf -oL adb -s $HU logcat -v time > "$1" & }
watch1() { adb -s $HU logcat -T 1 -v time | grep -a -m"$1" -F "$2"; }   # new lines only; exits on the Nth match
```

`g4_listen.sh`, pushed once to `/data/local/tmp/` (one silent connection on 3152, then it exits):

```sh
#!/system/bin/sh
(sleep 600 | toybox nc -l -p 3152 >/dev/null 2>&1) &
```

Preflight on D-HU, record each answer in Setup notes:

```bash
adb -s $HU root
adb -s $HU shell getprop rw.zlink.bt.type; adb -s $HU shell getprop rw.zj.bt.type   # expect both empty
adb -s $HU shell ls /dev/auto_serial /dev/rf_serial /dev/zj_bt_serial               # expect all absent
adb -s $HU shell netstat -ltn | grep 3152                                          # expect nothing
adb -s $HU shell toybox nc --help | head -5     # must list -l and -p, or G4 is UNTESTABLE
adb -s $POCO shell svc bluetooth disable
```

If `/dev/auto_serial` exists, stop G4 and G3 (the FYT arm would take precedence) and say so.

## 6. The lines that decide every run

All checked with `grep -rF` on `4335f0f8` `app/src/main`. Every grep runs on that run's capture with
`grep -a`, between the run's start and end markers.

| Line (substring) | Meaning |
|---|---|
| `AutomationReceiver: ` + the action name | the verb landed; missing means the run is void |
| `AutomationMarker: ` | the run's markers |
| `asking the vendor daemon whether it will carry Android Auto` | a measurement window opened (T0) |
| `NativeAA: [ZBT] nothing is listening on 127.0.0.1:3152` | one refused dial; **exclude** lines that also contain `but has no vendor daemon to` (the carrier's loud warning shares the prefix) |
| `NativeAA: [ZBT] dialling again in ` | the window's next gap; the number before `s.` is the gap |
| `NativeAA: [ZBT] nothing has listened on 127.0.0.1:3152 for ` | the window ended with a cached "no" (renders `for 30s, so the module cannot carry Android Auto on this unit.`) |
| `NativeAA: [ZBT] the daemon is on 127.0.0.1:3152 but did not answer within ` | listening but silent: route taken |
| `NativeAA: external Bluetooth module detected (` | the route was refused (`BLOCKED`) |
| `NativeAA: [ZBT] daemon refused, retrying in ` | one carrier refusal; the number before `s (` is the wait |
| `but has no vendor daemon to ` | the carrier's loud warning |
| `NativeAA: [ZBT] channel open to the Bluetooth module daemon on ` | the carrier opened a channel |
| `AapService: WiFi button: the vendor daemon refused ` ... `s ago; asking it again.` | the press forgot a cached refusal |
| `AapService: WiFi button on the Bluetooth module route: ` | the press's action: `REBUILD_LAUNCHER`, `START_HANDSHAKE` or `WAKE_PHONE` |
| `NativeAA: the vendor daemon is still being asked for a route, so the wake waits for it.` | a wake held for the window (record only) |
| `AapService: CommManager teardown complete. Restarting the hotspot so the phone leaves the network.` | the user exit chose the restart |
| `HotspotManager: Restarting the hotspot so any joined client is put off it.` | restart entered |
| `HotspotManager: Setting hotspot enabled=` | each ask (`true` or `false` follows) |
| `HotspotManager: the restarted access point held for ` | outcome: held |
| `HotspotManager: the restarted access point fell within ` | it fell; one more ask follows |
| `HotspotManager: second ask after a fall: the access point is ` | outcome after a fall: `up` or `still down` |
| `HotspotManager: The hotspot was taken down to put the phone off the network and would not come back up.` | outcome: the first ask never came up (unchanged path) |
| `Stopping the connection does not switch this device's hotspot ` | the exit only warned: the latch was not reset, cycle void |
| `SSL handshake complete` | session formed (INFO and DEBUG forms both match) |
| `createGroup SUCCESS`, `MATCH! Starting AapService` | discard rules (template §4) |

## 7. Runs

Order: G2, G1 (hotspot, hand-started AP, adjacent), then revert the hotspot keys, then G4, G3, Cleanup.

### R0. Build gate
`build_hur.sh` and `run_unit_tests.sh` with `HUR_DIR` on the candidate worktree; `adb -s $HU install -r`;
`apk_identity.sh`; `send ACTION_QUERY_STATE`.
PASS: 2470/0, md5 matches, reply carries `commit=4335f0f8160d`. A failure stops the round.

### G2. Control: ordinary hardware is untouched (WiFi Direct, D-MOTO)
Settings: all-runs table plus the WiFi Direct keys. Property not set (preflight).
```bash
adb -s $HU shell am force-stop $PKG
cap G2.txt
adb -s $MOTO shell cmd connectivity airplane-mode enable
send ACTION_LOG_MARKER --es text G2-start
send ACTION_START_WIRELESS --ez no_ui true
sleep 20
adb -s $MOTO shell cmd connectivity airplane-mode disable; adb -s $MOTO shell svc bluetooth enable; adb -s $MOTO shell svc wifi enable
watch1 1 'SSL handshake complete'          # give up after 90 s
sleep 10; send ACTION_LOG_MARKER --es text G2-end
```
PASS, all of: `SSL handshake complete` within 90 s of the `ACTION_START_WIRELESS` receiver line;
**zero** `asking the vendor daemon whether it will carry Android Auto`; **zero** `NativeAA: [ZBT]`;
**zero** `the vendor daemon is still being asked for a route`; discard rules clean.
Report: seconds from the receiver line to `SSL handshake complete`.
If the change did nothing to ordinary hardware this passes too; it is a regression guard, not a
proof of the fix.

### G1. Hotspot user exit, up to 3 cycles (D-MOTO)
Settings: all-runs table plus the G1 table. Per cycle:
```bash
adb -s $HU shell am force-stop $PKG
# re-write hotspot-teardown-proven-unsafe=false and read it back (app stopped)
adb -s $HU shell cmd wifi start-softap OHU-HOTSPOT wpa2 ohutest12345 -b 5
adb -s $HU shell ip -4 addr show wlan2 | grep -c inet     # expect 1; after an adb reboot, one stop/start cycle first (D-HU quirk)
cap G1-cN.txt
adb -s $MOTO shell cmd connectivity airplane-mode enable
send ACTION_LOG_MARKER --es text G1-cN-start
send ACTION_START_WIRELESS --ez no_ui true
sleep 20
adb -s $MOTO shell cmd connectivity airplane-mode disable; adb -s $MOTO shell svc bluetooth enable; adb -s $MOTO shell svc wifi enable
watch1 1 'SSL handshake complete'          # give up after 90 s: cycle INCONCLUSIVE, next cycle
sleep 20
send ACTION_DISCONNECT
sleep 45
adb -s $HU shell ip -4 addr show wlan2 | grep -c inet     # AP state at the end: 1 up, 0 down
send ACTION_LOG_MARKER --es text G1-cN-end
```
Graded per cycle, in the window from the `ACTION_DISCONNECT` receiver line to `G1-cN-end`:
- exactly 1 `AapService: CommManager teardown complete. Restarting the hotspot` and exactly 1
  `HotspotManager: Restarting the hotspot so any joined client is put off it.`; zero
  `Stopping the connection does not switch this device's hotspot ` (if present the cycle is void:
  the latch was not reset);
- exactly one terminal outcome: either 1 `held for `, or 1 `second ask after a fall: ` (then also
  exactly 1 `fell within ` before it, 4.5 to 30 s earlier), or 1 `would not come back up.` with no
  `held for ` and no `fell within `;
- `HotspotManager: Setting hotspot enabled=true` at most 2 times after the `Restarting the hotspot`
  line, and 2 only when a `fell within ` line is present;
- the final `inet` count agrees with the outcome: `held for ` or `... is up` reads 1; `still down` or
  `would not come back up.` reads 0 (record a disagreement as FAIL);
- zero `NativeAA: [ZBT]`.

Cycle verdict: FAIL if any condition fails; PASS if it holds and the outcome was `held for ` or
`second ask after a fall: `; INCONCLUSIVE if it holds and the outcome was `would not come back up.`
(the new code never ran). **Stop rule:** stop after 3 cycles, or after 2 consecutive INCONCLUSIVE
cycles. G1 overall: FAIL if any cycle failed, PASS if at least one cycle passed, else INCONCLUSIVE.
If the change did nothing, a cycle that came up would print no `held for ` or `fell within ` line.

**Then revert** to the WiFi Direct keys (four-key rule), `cmd wifi stop-softap`, read back.

### G4. Refused, then listening: the point of the round
This is the reporter's failing state followed by the daemon coming back mid-window.
```bash
adb -s $HU shell am force-stop $PKG
adb -s $HU shell setprop rw.zlink.bt.type extra; adb -s $HU shell getprop rw.zlink.bt.type    # expect extra
adb -s $MOTO shell cmd connectivity airplane-mode enable
cap G4.txt
send ACTION_LOG_MARKER --es text G4-start
( watch1 4 'NativeAA: [ZBT] nothing is listening on 127.0.0.1:3152' >/dev/null && \
  adb -s $HU shell sh /data/local/tmp/g4_listen.sh && send ACTION_LOG_MARKER --es text G4-listener-up ) &
send ACTION_START_WIRELESS --ez no_ui true
watch1 5 'NativeAA: [ZBT] daemon refused, retrying in '    # the 5th carrier refusal, ~30 s after the 1st
adb -s $HU shell sh /data/local/tmp/g4_listen.sh; send ACTION_LOG_MARKER --es text G4-relisten
watch1 1 'NativeAA: [ZBT] channel open to the Bluetooth module daemon on '   # give up after 40 s
sleep 3; send ACTION_STOP_WIRELESS; sleep 3
adb -s $HU shell pkill -f 'nc -l -p 3152'; adb -s $HU shell pkill -f 'sleep 600'
send ACTION_LOG_MARKER --es text G4-end
```
T0 is the `asking the vendor daemon whether it will carry Android Auto` line. PASS, all of:
1. exactly 4 refused-dial lines (excluding `but has no vendor daemon to `) before `G4-listener-up`,
   and `G4-listener-up` before T0 + 15 s;
2. exactly 1 `the daemon is on 127.0.0.1:3152 but did not answer within `, at T0 + 16 s to T0 + 20 s,
   with no refused-dial line between `G4-listener-up` and it;
3. **zero** `nothing has listened on 127.0.0.1:3152 for ` and **zero**
   `NativeAA: external Bluetooth module detected (` in the whole run;
4. the first `daemon refused, retrying in ` within 3 s after the silent line (the one-shot listener
   is gone, so the carrier is refused), and the waits announced by the first five carrier refusals
   are exactly `2`, `4`, `8`, `16`, `30`; each gap between consecutive refusal lines equals the
   previous announced wait within 1 s;
5. exactly 1 line containing `but has no vendor daemon to ` before `G4-relisten`, at least 29 s after
   the first carrier refusal, and no earlier than the 5th carrier refusal line (within 100 ms of it);
6. exactly 1 `channel open to the Bluetooth module daemon on ` after `G4-relisten`, within 31 s of it.

Report: T0 offsets of the 4 refusals and of the silent line; the five announced carrier waits;
the loud warning's offset from the first carrier refusal; `G4-relisten` to channel open in seconds.
If the change did nothing: one refused line, then `external Bluetooth module detected (` about 30 ms
later, no silent line and no carrier lines at all.

### G3. A daemon that never comes back, the press, and the stop
Property still set from G4, nothing listening (`netstat -ltn | grep 3152` empty first).
```bash
adb -s $HU shell am force-stop $PKG
cap G3.txt
send ACTION_LOG_MARKER --es text G3a-start
send ACTION_START_WIRELESS --ez no_ui true
watch1 1 'NativeAA: [ZBT] nothing has listened on 127.0.0.1:3152 for '      # give up after 45 s
sleep 5
send ACTION_LOG_MARKER --es text G3b-press
( watch1 3 'NativeAA: [ZBT] nothing is listening on 127.0.0.1:3152' >/dev/null && \
  send ACTION_STOP_WIRELESS && send ACTION_LOG_MARKER --es text G3c-stopped ) &
press_module_wifi
sleep 45
send ACTION_LOG_MARKER --es text G3c-rearm
send ACTION_START_WIRELESS_SCAN
watch1 1 'NativeAA: [ZBT] nothing has listened on 127.0.0.1:3152 for '      # give up after 45 s
sleep 5
send ACTION_LOG_MARKER --es text G3d-cached
send ACTION_START_WIRELESS_SCAN
sleep 10
send ACTION_LOG_MARKER --es text G3d-press
press_module_wifi
sleep 10
send ACTION_LOG_MARKER --es text G3e-press
press_module_wifi
watch1 1 'NativeAA: [ZBT] nothing has listened on 127.0.0.1:3152 for '      # give up after 45 s
sleep 3
send ACTION_STOP_WIRELESS
send ACTION_LOG_MARKER --es text G3-end
```
**G3a** (T0 = the first `asking the vendor daemon` line). PASS, all of: exactly 7 refused-dial lines
before `G3b-press`, at T0 + 0, 1, 3, 7, 15, 23, 30 s, each within 1 s; exactly 6
`dialling again in ` lines with gaps `1`, `2`, `4`, `8`, `8`, then `6` or `7`; exactly 1
`nothing has listened on 127.0.0.1:3152 for ` at T0 + 29 to 31.5 s; then 1
`NativeAA: external Bluetooth module detected (` within 1 s after it; zero
`daemon refused, retrying in ` in the whole run.
If the change did nothing: 1 refused line and the refusal within 50 ms of T0.

**G3b** (window `G3b-press` to `G3c-stopped`). PASS, all of: exactly 1
`AapService: WiFi button: the vendor daemon refused ` line ending `s ago; asking it again.`, whose
number is within 1 of the seconds between G3a's `nothing has listened` line and it; then exactly 1
`WiFi button on the Bluetooth module route: ` reading `START_HANDSHAKE` or `REBUILD_LAUNCHER`; then 1
`asking the vendor daemon` within 2 s of the press line; then 3 refused-dial lines.
Record, not graded: a screenshot 1 s after `press_module_wifi` (`screencap`), and whether the toast
"Wireless Bluetooth is not running on this head unit" is on it. Before the change the press would
print no `asking it again.` line and toast that message.

**G3c** (the stop guard). PASS, all of: from 0.5 s after the `ACTION_STOP_WIRELESS` receiver line
to `G3c-rearm`, **zero** refused-dial, `dialling again in `, `nothing has listened`,
`asking the vendor daemon` and `external Bluetooth module detected (` lines; after `G3c-rearm`, 1
`asking the vendor daemon` within 3 s of the `ACTION_START_WIRELESS_SCAN` receiver line (an abandoned
window cached nothing; a cached "no" would print `external Bluetooth module detected (` instead), and
that window ends in 1 `nothing has listened` line.

**G3d** (a launcher started on a cached "no"). PASS, all of: between `G3d-cached` and `G3d-press`, 1
`NativeAA: external Bluetooth module detected (` within 3 s of the `ACTION_START_WIRELESS_SCAN`
receiver line and **zero** `asking the vendor daemon`; after `G3d-press`, 1 `... refused Ns ago;
asking it again.` line, then 1 `WiFi button on the Bluetooth module route: ` reading
`REBUILD_LAUNCHER`, then 1 `asking the vendor daemon` within 5 s of the press line.
If the press verb's reply is not `"ok":true`, G3b, G3d and G3e are UNTESTABLE and G3c's first half is still graded on a
fresh `send ACTION_START_WIRELESS --ez no_ui true` arming in place of the press.

**G3e** (a press while the daemon is still being asked; window `G3e-press` to `G3-end`). PASS, all
of: exactly 1 `WiFi button on the Bluetooth module route: ` reading `WAKE_PHONE`; 1
`the vendor daemon is still being asked for a route, so the wake waits for it.` within 1 s of it;
**zero** `asking the vendor daemon` and zero `asking it again.` lines; no two
`NativeAA: [ZBT] nothing is listening on 127.0.0.1:3152` lines within 500 ms of each other across
the whole G3d-press to G3-end span (one window, one dialler); exactly 1 `nothing has listened` line.
Before the fix the press read `START_HANDSHAKE`, printed a second `asking the vendor daemon`, and
each window's refusals came in pairs.

Report: G3a's seven offsets; G3e's route reading and its shortest gap between refused-dial lines; the age G3b printed; G3c's count of lines after the stop (expect 0).

### Cleanup (mandatory)
```bash
adb -s $HU shell am force-stop $PKG
adb -s $HU shell setprop rw.zlink.bt.type ""; adb -s $HU shell getprop rw.zlink.bt.type    # expect empty
adb -s $HU shell pkill -f 'nc -l -p 3152'; adb -s $HU shell netstat -ltn | grep 3152        # expect nothing
adb -s $POCO shell svc bluetooth enable
```
Restore the round's `settings.xml` backup (as root on D-HU, `chown` and `chmod 660`, read back).

## 8. Do not re-run

First round of this thread; nothing is settled. Not for the rig at all: an **answering** daemon,
ACC off/on on the reporter's unit, the held wake reaching the phone through a real module, and the
Bluetooth SoC crash coincidence. Those are the reporter round (cold start hands off x3; app opened by
hand right after ACC on; WiFi button after a cached refusal; warm reconnect control; SoC crash
record; user exit with `getWifiApState`) and the JVM tests (`ZbtReachabilityPolicyTest`,
`ZbtDaemonReachabilityTest`, `ZbtReopenPolicyTest`, `ExternalBtTransportPolicyTest`,
`ModuleRearmPolicyTest`, `HotspotRestartPolicyTest`).

## 9. Report back

1. G4: T0 offset of the silent line, whether any `nothing has listened` or
   `external Bluetooth module detected (` line appeared (expect none), and the five announced
   carrier waits (expect 2, 4, 8, 16, 30).
2. G3: the seven refused-dial offsets from T0, G3e's route reading (expect `WAKE_PHONE`), and the count of measurement lines after the stop in
   G3c (expect 0).
3. G1: per cycle, the terminal outcome line and the final `wlan2` `inet` count.
