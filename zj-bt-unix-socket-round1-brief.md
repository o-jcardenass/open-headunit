# zj-bt-unix-socket, round 1 brief

> **Revised 2026-10-29: C moved, nothing to re-read but section 1.** C was rebased from `2b381091` (on `main`
> `947b3ae9`) to `73249d8b` (on `2018d8f0`) on 2026-10-27, and now to `390b6931` on `main` `a4f80ff1`, after #1163
> (the HCT-owned WPP route) merged. The one conflict was route context in `NativeAaHandshakeManager.start`: the ZJ arm
> now sits under main's `selectedRoute`. The ZJ code and every string in section 6 are unchanged. Rebuild.
>
> **Revised 2026-10-26: C moved again.** C was rebased from `d594102c` to `2b381091d1ab` on `main`
> `947b3ae9`. The patch gained a fourth commit, `Native AA: Ask the ZJ socket at the first ZBT refusal`,
> and one log line was hand-merged with main's rewording. Re-read section 1 and rebuild. The W string in
> section 6 now matches the code, and W2 is new. The fourth commit asks the socket at the first ZBT
> refusal. A socket that connects ends the ZBT window there (W2, no W). A socket that refuses lets the
> window run out (W), and is asked again after it. So R2 conditions 2 and 3, R4b part 1 conditions 1
> and 2, and R4b part 2 conditions 3 and 4 changed, with the reports and section 9. R4a, R3 and R5 did
> not change.

Published name: `zj-bt-unix-socket-round1-brief.md`. Results go in `zj-bt-unix-socket-round1-results.md`.
Evidence: create the release `rig-evidence-zj-bt-unix-socket` (first round of this thread) and upload
`zj-bt-unix-socket-round1-captures.zip` to it.

Units: D-HU (head unit, root adbd) and D-POCO (phone, R1 only). D-MOTO takes no part: its Bluetooth is
off for the whole round. D-SAM and D-HP take no part. No hand steps and no injected taps.

Queue line: `zj-bt-unix-socket-round1-brief.md` (C `390b6931` on `fork/feat/zj-bt-unix-socket`, on
`main` `a4f80ff1`; one build, no baseline; units D-HU (root) and D-POCO, D-MOTO Bluetooth
off; R2 is the point).

Estimated time: 10 min pre-flight, 20 min build, 6 min R1, 3 min R4a, 7 min R2 with R3, 5 min R4b, 5 min R5,
10 min thermal waits. About 1 h 10 min.

**R2 is the point of the round.** R4b is the control that shows the route needs a connect, not a file.

## 1. Build and baseline

| Arm | Ref | Commit | Tree |
|---|---|---|---|
| C (candidate) | `fork/feat/zj-bt-unix-socket` | `390b6931e1a6f44959f411f81e09a25c2af34fd7` | `00003772e4dc902bb260f3014957a1262dd9d7f3` |

The branch was rewritten three times after its first push: `1c425934` was squashed to `bbda5eff` (same
tree), rebased to `d594102c` when the HCT route (#1106) merged, then rebased to `2b381091` on 2026-10-26, to `73249d8b` on 2026-10-27 and to `390b6931` on 2026-10-29.
It has four commits on `main` `a4f80ff1b6aa6726c17e7385192cf23abe0d0e9f`: a contributor's commit
(`21087973`, Justus Mungard), then three of ours.
No other queued round shares this base.

The dev host ran the JVM suite at this tree: 4380 tests, 0 failures. Do not run the unit tests on the rig.

No baseline APK. There is no `main` behaviour to compare with: the route does not exist on `main`.

**`get_apk.sh`**
```bash
OUT=rig-data/rounds/zj-bt-unix-socket-round1; mkdir -p $OUT
git fetch fork feat/zj-bt-unix-socket
WT=$(ls -d worktrees/*zj-bt* 2>/dev/null | head -1)   # else create one with wt-new.sh
git -C $WT checkout --detach 390b6931e1a6f44959f411f81e09a25c2af34fd7
git -C $WT rev-parse 'HEAD^{tree}'                    # MUST print 00003772e4dc902bb260f3014957a1262dd9d7f3
$RIG_TOOLKIT/rig_thermal.sh wait 70
HUR_DIR=$WT MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh   # copy the APK out of apks/ to $OUT/apk-C.apk at once
md5sum $OUT/apk-C.apk                                 # record as WANT_MD5
```

Install with `adb -s $HU install -r -d $OUT/apk-C.apk`. Identity, after the install:

- `send ACTION_QUERY_STATE`: the `data=` line MUST carry commit `390b6931e1a6`.
- Pull the live APK (`apk_check`, section 5). In it, `unzip -p $OUT/live.apk 'classes*.dex' | strings | grep -c ZjSocketProbe`
  MUST be at least 1, and the same count for `RawUnixSocketCarrier` MUST be 0.

## 2. What this is and why

Some head units have their Bluetooth on an external module that `android.bluetooth` cannot reach. The
app reaches such a module through its vendor daemon. Until now the only daemon the app knew was ZBT on
TCP port 3152. A user's unit, a UIS7862 with a ZJ Bluetooth module, has no listener on 3152. Its daemon
instead serves the phone's raw RFCOMM byte stream on the Unix socket `/dev/socket/zj_bt_socket`. A
contributor's commit added a route over that socket, and one session worked on that unit.

That commit left five faults, and this branch fixes them:

1. **Only the first session worked.** The carrier stopped when a session closed the Android listeners,
   and nothing started it again. The next drive needed an app restart.
2. **The screens treated the unit as an ordinary phone radio.** The route was not in the external
   module checks, so the WiFi button and the settings read the refused 3152 dial.
3. **The wake and the WiFi button did not see the carrier.** It sat outside `moduleCarrier`, so a WiFi
   button press found no module channel.
4. **The route choice was unsafe.** The app took the socket route when the file existed. That check sat
   above a ZBT daemon that answers and above the user's ZBT switch.
5. **There was no switch and no path list.**

What the candidate does, in the terms this round can see:

- The app asks the ZBT daemon first. Only when ZBT refuses does it **connect** to the socket, once per
  arming, with a 2 s limit, and then close it. One INFO line gives the result. A file that exists but
  refuses gives `REFUSED`, and the route stays refused.
- A positive answer is kept for the life of the process. A negative answer is asked again at a new
  arming and at a WiFi button press.
- The carrier lives in `moduleCarrier`. It dials, serves one handshake, closes, waits about 3 s, and
  dials again. It stops only when the wireless stack stops. It sends no wake. A WiFi button press makes
  the next dial happen at once.
- A new setting `external-bt-zj-socket-transport` (default false) forces the route on a unit with no
  markers.

## 3. What is different about this round

- **No rig unit has this socket or this daemon.** D-HU stands in. Root puts it on the external-module
  detection path with `setprop rw.zlink.bt.type extra` (`rig-quirks/topics/bt.md`). That gives the same
  detection class as the user's unit (`REACHABILITY`), and nothing listens on 3152, as on the user's
  unit. A `toybox nc` listener stands in for the daemon. It accepts every connect, reads and discards,
  and never writes. So it acts like a daemon with **no phone behind it**.
- **Nothing on the rig can answer a handshake over the socket.** So the rig cannot prove a session, a
  second session, the hold for the session, a call, or what the probe's close does to a real phone.
  Those are section 10, which the coding side sends to the user. The rig skips section 10.
- **No phone takes part in R2 to R5.** D-POCO's Bluetooth is off and its WiFi is held off by a loop, so
  a persistent group cannot pull it in (`rig-quirks/units/D-HU.md`). No run in R2 to R5 touches Android
  Auto, so those runs need no phone capture. R1 forms a real session and has a phone capture.
- **The property is read once per process** (`BluetoothHelper.externalBtEvidence` holds a positive), so
  set or clear it only with the app force-stopped. Native AA is refused while the property is set and
  no socket answers. Cleanup clears it.
- **Lessons from the external-bt-module-coldstart round, built into the helpers:** a device-side
  background job hangs `adb shell`, so the listener runs as a host-side background job; this toybox
  rejects `pkill -f`, so the kill goes by pid from `ps`; `setprop` drops an empty argument, so the clear
  quotes it; a `logcat | grep -m` watcher does not return, so every wait polls the capture file.
- **The `toybox nc` form is not yet proven on D-HU.** Pre-flight step 6 tests it. If the listener does
  not come up, R2, R3, R4b and R5 are UNTESTABLE (stub) and R1 and R4a still run.
- **If D-HU reads `Enforcing`** in pre-flight step 5, do not change it. Then each connect gives
  `DENIED (`, the route is refused, and R2, R3, R4b part 2 and R5 are INCONCLUSIVE. Grade R4b part 1 as
  written but accept `DENIED (` in place of `REFUSED (`, and record it.
- **The status pill is not graded.** No log line names its stage. The JVM tests cover that this route
  reports no wake.
- **Log level 2 (INFO).** Every head unit line in section 6 prints at INFO or WARN. D-HU floods logcat
  at VERBOSE.
- **Read `rig-quirks/topics/tooling.md`, `rig-quirks/units/D-HU.md`, `rig-quirks/units/D-POCO.md` and
  `rig-quirks/topics/bt.md` first.** Thermal limits are the table in `tooling.md`.

## 4. Settings keys

Back up D-HU's `settings.xml` at the start (`$OUT/settings-backup.xml`) and diff it against the end.
State the delta in Setup notes. `stat` D-HU's `shared_prefs/` and record owner and mode. Leave the audio
keys as they are: they are a deliberate worst case. Record `video-profile-starvation-cap`. No run grades
audio or video quality.

Record, do not change: `native-poke-bt-macs`, `native-poke-all-paired`, `native-driver-selection-mode`,
`native-preferred-device-mac`, `wifi-direct-stable-identity`, `bt-address`, `native-ap-transport`.

**Base** (every run). `ACTION_LOG_MARKER` is not in `AutomationCommandPolicy.CONFIGURING` at this SHA,
so `allow-external-configuration` is not needed.

```xml
<int name="wifi-connection-mode" value="3" />
<int name="log-level" value="2" />
<int name="onboarding-version" value="2" />
<int name="native-aa-wake-damage-verdict" value="0" />
<boolean name="external-bt-zbt-transport" value="false" />
<boolean name="external-bt-blink-transport" value="false" />
<boolean name="native-aa-ignore-external-bt" value="false" />
```

**R1, R2, R3, R4a, R4b:** delete `external-bt-zj-socket-transport`. Its absence tests the default.

**R5:** `<boolean name="external-bt-zj-socket-transport" value="true" />`.

## 5. Helpers

Run every adb call in sequence, never two at once against one unit. The D-POCO WiFi hold loop talks to
D-POCO only. Write the results file after every run.

**`lib.sh`**
```bash
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
HU=27870808938846; PH=4f4027e9; MOTO=ZY22GC3BM4
OUT=rig-data/rounds/zj-bt-unix-socket-round1; mkdir -p $OUT
SOCK=/dev/socket/zj_bt_socket
export LC_ALL=C
send() { a=$1; shift; adb -s $HU shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; }
mark() { send ACTION_LOG_MARKER --es text "$1" >/dev/null; sleep 0.3; }
pnow() { adb -s $PH shell "date '+%m-%d %H:%M:%S.000'" | tr -d '\r'; }
step() { mark "$1"; echo "$1 $(pnow)" >> $OUT/phone-marks.txt; }
launch() { adb -s $HU shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity >/dev/null; }
# huexit: only while the app runs (an exit on a stopped app starts the service)
huexit() { adb -s $HU shell am start -a android.intent.action.VIEW -d "headunit://exit" >/dev/null; sleep 5
  adb -s $HU shell am force-stop $PKG; sleep 1; }
hustop() { adb -s $HU shell am force-stop $PKG; sleep 1; }

# host thermal (rig-quirks/topics/tooling.md)
th_gate() { $RIG_TOOLKIT/rig_thermal.sh wait 75 || $RIG_TOOLKIT/rig_thermal.sh wait 75 || { echo HOST_HOT; return 1; }
  $RIG_TOOLKIT/rig_thermal.sh watch $OUT/thermal-$RUN.log & THPID=$!; }
th_end() { kill $THPID 2>/dev/null; wait $THPID 2>/dev/null; }

# D-POCO off the link. The WiFi loop holds it off, because its WiFi turns itself back on.
ph_off() { adb -s $PH shell svc bluetooth disable; adb -s $PH shell svc wifi disable
  ( while :; do adb -s $PH shell svc wifi disable; sleep 3; done ) & PHLOOP=$!; sleep 4
  adb -s $PH shell dumpsys bluetooth_manager | grep -m1 -E "^ *state:"; adb -s $PH shell dumpsys wifi | grep -m1 "Wi-Fi is"; }
ph_on()  { kill $PHLOOP 2>/dev/null; wait $PHLOOP 2>/dev/null; PHLOOP=
  adb -s $PH shell svc wifi enable; sleep 0.3; adb -s $PH shell svc bluetooth enable; sleep 4
  adb -s $PH shell dumpsys bluetooth_manager | grep -m1 -E "^ *state:"; adb -s $PH shell dumpsys wifi | grep -m1 "Wi-Fi is"; }

# the external-module marker on D-HU (app stopped)
prop_on()  { adb -s $HU shell setprop rw.zlink.bt.type extra; adb -s $HU shell getprop rw.zlink.bt.type; }
prop_off() { adb -s $HU shell "setprop rw.zlink.bt.type ''"; adb -s $HU shell getprop rw.zlink.bt.type; }

# the daemon stand-in. stub_up prints STUB_UP or STUB_FAIL; stub_down leaves the socket file in place.
stub_up() { adb -s $HU shell sh /data/local/tmp/zj_stub.sh > $OUT/stub-$1.log 2>&1 & STUBPID=$!
  local n=0; until [ "$(adb -s $HU shell grep -c zj_bt_socket /proc/net/unix | tr -d '\r')" -ge 1 ]; do
    sleep 0.5; n=$((n+1)); [ $n -ge 20 ] && { echo STUB_FAIL; return 1; }; done
  adb -s $HU shell chmod 666 $SOCK; adb -s $HU shell ls -l $SOCK; echo STUB_UP; }
stub_down() { adb -s $HU shell sh /data/local/tmp/zj_kill.sh; sleep 1; kill $STUBPID 2>/dev/null; wait $STUBPID 2>/dev/null
  echo "unix_entries=$(adb -s $HU shell grep -c zj_bt_socket /proc/net/unix | tr -d '\r')"; adb -s $HU shell ls -l $SOCK; }

# captures: started before the launch. -v threadtime on both units.
cap_on() { adb -s $HU logcat -c; adb -s $HU logcat -G 16M; stdbuf -oL adb -s $HU logcat -v threadtime > $OUT/$1-hu.txt & HUPID=$!
  PHPID=; if [ "$2" = phone ]; then stdbuf -oL adb -s $PH logcat -v threadtime -T 1 -b main,system,crash,events > $OUT/$1-phone.txt & PHPID=$!; fi; sleep 2; }
cap_off() { sleep 2; kill $HUPID $PHPID 2>/dev/null; wait $HUPID $PHPID 2>/dev/null; }

# waitn FILE N STRING CAP_S: poll until STRING has printed N times. Prints "ok <s>" or TIMEOUT.
waitn() { local t=0; while [ $t -lt $(($4*2)) ]; do
    [ "$(grep -acF -- "$3" "$1")" -ge "$2" ] && { echo "ok $((t/2))s"; return 0; }; sleep 0.5; t=$((t+1)); done
  echo TIMEOUT; return 1; }

# greps over one window: from marker A to marker B in one capture
win()  { awk -v a="AutomationMarker: $2" -v b="AutomationMarker: $3" 'index($0,a){on=1} on{print} on&&index($0,b){exit}' $OUT/$1-hu.txt; }
cnt()  { win "$1" "$2" "$3" | grep -acF -- "$4"; }              # cnt CAP FROM TO STRING
hits() { win "$1" "$2" "$3" | grep -aF -- "$4"; }               # the lines, with their times
sec()  { awk '{split($2,t,":"); printf "%.3f\n", t[1]*3600+t[2]*60+t[3]}'; }   # time of a threadtime line

# phone greps over one window, on the phone's clock from phone-marks.txt
pt()   { grep -aF "$1 " $OUT/phone-marks.txt | tail -1 | cut -d' ' -f2-; }
pwin() { awk -v a="$(pt "$2")" -v b="$(pt "$3")" 'substr($0,1,18)>=a && substr($0,1,18)<=b' $OUT/$1-phone.txt; }
pc()   { pwin "$1" "$2" "$3" | grep -acF -- "$4"; }

# settings on D-HU, app stopped. Args: "set TYPE KEY VALUE" or "del x KEY". adbd is root.
hu_kv() {
  local IFS=' '
  { echo 'F=/data/data/com.andrerinas.headunitrevived/shared_prefs/settings.xml'
    for kv in "$@"; do set -- $kv
      echo "sed -i -E 's#<[a-z]+ name=\"$3\"[^>]*/>##g' \$F"
      echo "sed -i -E 's#<string name=\"$3\">[^<]*</string>##g' \$F"
      if [ "$1" = set ]; then
        if [ "$2" = string ]; then echo "sed -i 's|</map>|<string name=\"$3\">$4</string></map>|' \$F"
        else echo "sed -i 's|</map>|<$2 name=\"$3\" value=\"$4\" /></map>|' \$F"; fi
      fi
    done
    echo 'chown $(stat -c %u:%g /data/data/com.andrerinas.headunitrevived) $F; chmod 660 $F'
  } > $OUT/zj_kv.sh
  adb -s $HU push $OUT/zj_kv.sh /data/local/tmp/zj_kv.sh >/dev/null
  adb -s $HU shell "sh /data/local/tmp/zj_kv.sh"
}
base() { hu_kv "set int wifi-connection-mode 3" "set int log-level 2" "set int onboarding-version 2" \
  "set int native-aa-wake-damage-verdict 0" "set boolean external-bt-zbt-transport false" \
  "set boolean external-bt-blink-transport false" "set boolean native-aa-ignore-external-bt false"; }
zj_default() { hu_kv "del x external-bt-zj-socket-transport"; }
zj_forced()  { hu_kv "set boolean external-bt-zj-socket-transport true"; }
hu_read() { adb -s $HU shell cat /data/data/$PKG/shared_prefs/settings.xml > $OUT/settings-$1.xml
  grep -aoE '(wifi-connection-mode|log-level|external-bt-[a-z-]+|native-aa-ignore-external-bt|native-aa-wake-damage-verdict)"[^/]*' $OUT/settings-$1.xml; }

# APK identity: pull and hash on the host (a piped md5 over adb is not reliable)
apk_check() { adb -s $HU pull $(adb -s $HU shell pm path $PKG | cut -d: -f2 | tr -d '\r') $OUT/live.apk >/dev/null 2>&1
  local m=$(md5sum $OUT/live.apk | cut -d' ' -f1); echo "$(date +%T) live=$m want=$WANT_MD5" | tee -a $OUT/apk-check.log
  [ "$m" = "$WANT_MD5" ] || { echo APK_MISMATCH | tee -a $OUT/apk-check.log; return 1; }; }
```

`zj_stub.sh` (push once to `/data/local/tmp/`). A silent daemon stand-in: it accepts every connect,
reads and discards, and never writes. `umask 000` makes the socket file `srwxrwxrwx`.

**`zj_stub.sh`**
```sh
#!/system/bin/sh
P=/dev/socket/zj_bt_socket
rm -f $P
umask 000
exec toybox nc -L -U -s $P sh -c 'cat >/dev/null'
```

`zj_kill.sh` (push once to `/data/local/tmp/`). It kills the listener and every open connection by
pid, and leaves the socket file.

**`zj_kill.sh`**
```sh
#!/system/bin/sh
ps -A -o PID,ARGS | grep -e 'nc -L -U' -e 'cat >/dev/null' | grep -v grep | while read pid rest; do kill $pid; done
```

## 6. The deciding lines

Each head unit string was checked with `grep -F -r` on `app/src/main` at `390b6931`. All print at INFO
or WARN. A line with a run-time value is listed here by its run-time text, and by its source fragment
in the `decisive-strings` block at the end.

**Head unit capture** (`$OUT/<CAP>-hu.txt`, between the run's markers):

| Grep (run-time text) | Meaning |
|---|---|
| `AutomationReceiver: ` / `AutomationMarker: ` | a verb or a marker landed; a missing line voids the step |
| `NativeAA: vendor Bluetooth bridge candidate detected; checking the local daemon before choosing a route.` | T0: an arming starts to ask the daemons |
| `NativeAA: [ZBT] connection attempt to 127.0.0.1:3152 classified as refused` | one refused ZBT dial |
| `NativeAA: [ZBT] the local daemon check did not establish reachability during this` | W: the 30 s ZBT window ran out with "no" |
| `NativeAA: [ZBT] 127.0.0.1:3152 refused and another module route answered` | W2: the ZBT window ended at its first refusal because the socket connected |
| `NativeAA: [ZJ] probe /dev/socket/zj_bt_socket: CONNECTS` | the socket accepted the probe's connect |
| `NativeAA: [ZJ] probe /dev/socket/zj_bt_socket: REFUSED (` | the socket file exists and nothing accepts |
| `NativeAA: [ZJ] socket transport is on (` | the ZJ route started |
| `NativeAA: [ZJ] persist.zj.BTmac is not set, so the module's` | the address seed ran and found no property (D-HU has none) |
| `NativeAA: [ZJ] Carrier started, watching /dev/socket/zj_bt_socket` | a carrier started |
| `NativeAA: [ZJ] Connected to /dev/socket/zj_bt_socket (attempt #` | the carrier connected; the number follows |
| `NativeAA: [TX] Sending WifiVersionRequest (Type 4)` | our opener went into the socket |
| `NativeAA: BT Handshake link closed.` | one handshake ended |
| `NativeAA: [ZJ] connect to /dev/socket/zj_bt_socket failed (` | a carrier connect failed; the outcome and `next try in` follow |
| `NativeAA: [ZJ] Socket /dev/socket/zj_bt_socket does not exist yet. Waiting...` | the carrier found no socket file (WARN, R5 only if the file goes with the listener) |
| `NativeAA: [ZJ] Carrier stopped after ` | the carrier ended; the attempt count follows |
| `NativeAA: [ZJ] this route sends no wake; the module brings the phone's link up by itself.` | a wake request reached the carrier (at most 1 per carrier) |
| `NativeAA: [ZJ] carrier stopped unexpectedly: ` | MUST be 0 |
| `NativeAA: [ZJ]` | any ZJ line (counts in the control runs) |
| `NativeAA: external Bluetooth module detected (` | the route is refused (`BLOCKED`) |
| `AapService: WiFi button on the Bluetooth module route: ` | a module WiFi press; the action follows |
| `AapService: WiFi button: the vendor daemon refused ` | the press forgets a cached "no" and asks again |
| `NativeAA: Manual poke requested` | the press reached the module carrier |
| `AapService: no module channel is open yet` | the press found no carrier (MUST be 0) |
| `AapService: Native-AA poke without a device ignored` | the press was not on the module route (MUST be 0) |
| `the phone connected over Bluetooth and answered nothing we sent` | the silent-radio warning (MUST be 0 on this route) |
| `NativeAA: Attempting active poke to device` | an Android radio poke (MUST be 0 on this route) |
| `NativeAA: Calling socket.connect()` | an Android radio connect (MUST be 0 on this route) |
| `NativeAA: ACTIVELY LISTENING on Android Auto UUID` | the Android RFCOMM listeners are open |
| `NativeAA: Connection accepted from ` | a phone came back over the Android radio (R1) |
| `NativeAA: [ZBT]` | any ZBT line (counts in R1 and R5) |
| `SSL handshake complete` | a session formed (INFO form) |
| `Throughput over ` | video decoded (R1) |
| `createGroup SUCCESS` | group count (record) |
| `MATCH! Starting AapService` | a self wake: discard rule, MUST be 0 |

**Thread of a line.** AppLog writes the caller's thread id as the first `[digits]` token after
`OPENHU`. The main thread is the one on the `AutomationReceiver: ` line.

**Phone capture** (`$OUT/R1-phone.txt`, R1 only, phone clock window from `phone-marks.txt`). Record the
Gearhead version (`adb -s $PH shell dumpsys package com.google.android.projection.gearhead | grep versionName`).
A 0 can mean a renamed string. Then mark the condition "string not seen", not FAIL.

| Grep | Meaning |
|---|---|
| `Creating rfcomm socket for device: ` | the phone ran the Bluetooth handshake (`GH.WIRELESS.BT`) |

## 7. Runs

### Pre-flight (read only, except step 6)

1. `sed -n '/^## Pick a tool/,/^## Rig and devices/p' $RIG_TOOLKIT/TOOLS.md`. Record which tools you used.
2. `$RIG_TOOLKIT/rig_preflight.sh D_HU:wifi,bt D_POCO:wifi,bt`.
3. `adb -s $MOTO shell svc bluetooth disable` for the whole round. Read it back with `dumpsys bluetooth_manager`.
4. `adb -s $HU root`. Then record each, all MUST hold:
   `adb -s $HU shell getprop rw.zlink.bt.type` and `getprop rw.zj.bt.type` empty;
   `adb -s $HU shell getprop persist.zj.BTmac` empty;
   `adb -s $HU shell ls /dev/rf_serial /dev/zj_bt_serial /dev/auto_serial $SOCK` all absent;
   `adb -s $HU shell netstat -ltn | grep 3152 | grep -c LISTEN` prints 0;
   `adb -s $HU shell pm list packages android.microntek.mtcser` prints nothing.
   If `/dev/auto_serial` exists, stop R2 to R5 (the FYT route would win) and say so.
5. `adb -s $HU shell getenforce`. Record it. Section 3 says what `Enforcing` changes.
6. Push `zj_stub.sh` and `zj_kill.sh` to `/data/local/tmp/`. Run `stub_up pre`. It MUST print `STUB_UP`,
   and `ls -l` MUST show a line that starts with `s` and ends with `zj_bt_socket`. Then run `stub_down`.
   It MUST print `unix_entries=0`. Record whether `ls -l` still shows the file: R4b part 1 needs it.
   Then `adb -s $HU shell rm -f $SOCK`. If `stub_up` prints `STUB_FAIL`, record `toybox nc --help | head -20`
   and `$OUT/stub-pre.log`, mark R2, R3, R4b and R5 UNTESTABLE (stub), and run R1 and R4a only.
7. D-POCO: `adb -s $PH shell dumpsys wifip2p | grep isGroupOwner` MUST be false (else `svc wifi disable`,
   `svc wifi enable`). `adb -s $PH shell dumpsys window | grep mCurrentFocus`: at home (else
   `input keyevent KEYCODE_HOME`).
8. `hustop`. Back up `settings.xml` to `$OUT/settings-backup.xml`. `stat` `shared_prefs/`. Record the
   keys section 4 says to record.
9. Install and check identity (section 1). `export WANT_MD5=<md5>`; `apk_check` MUST pass.

Order: R1, R4a, R2 with R3 (one capture), R4b, R5, Cleanup. Every run: `RUN=<id>; th_gate` first, and
`th_end` last. Re-run a run once if its thermal log shows a throttle rise or 95C.

### R1. Control: ordinary hardware takes no ZJ path (D-POCO session)

The property is not set and no socket file exists. This is a regression guard. It passes the same way
whether the change works or does nothing, because no ZJ code runs here. It proves only that ordinary
hardware is not touched.

**`r1.sh`**
```bash
RUN=R1; th_gate; hustop; base; zj_default; hu_read R1
adb -s $PH shell svc bluetooth disable; adb -s $PH shell svc wifi disable
cap_on R1 phone
step R1-start
launch
waitn $OUT/R1-hu.txt 1 'NativeAA: ACTIVELY LISTENING on Android Auto UUID' 60
step R1-phone
ph_on
waitn $OUT/R1-hu.txt 1 'SSL handshake complete' 120
waitn $OUT/R1-hu.txt 1 'Throughput over ' 60
sleep 10; step R1-end
huexit; cap_off
adb -s $PH shell svc bluetooth disable; adb -s $PH shell svc wifi disable; th_end
```

PASS, all of, in `win R1 R1-start R1-end`:

1. `SSL handshake complete` at least 1, within 120 s of `R1-phone`.
2. A `Throughput over ` line whose `rendered=` value is above 0.
3. `NativeAA: [ZJ]` 0, `NativeAA: vendor Bluetooth bridge candidate detected` 0, `NativeAA: [ZBT]` 0.
4. `NativeAA: ACTIVELY LISTENING on Android Auto UUID` at least 1.
5. `MATCH! Starting AapService` 0.
6. Phone: `pc R1 R1-phone R1-end 'Creating rfcomm socket for device: '` at least 1.

If condition 1 fails and the phone shows 0 for condition 6, D-POCO never ran the handshake: mark R1
INCONCLUSIVE (rig), not FAIL, and record `native-poke-bt-macs` and `native-poke-all-paired`.

Report: seconds from `R1-phone` to `SSL handshake complete`; the condition 6 count.

### R4a. A marked unit with no socket file keeps today's refusal

**`r4a.sh`**
```bash
RUN=R4a; th_gate; hustop; ph_off; adb -s $HU shell rm -f $SOCK; prop_on; base; zj_default; hu_read R4a
cap_on R4a
mark R4a-start
send ACTION_START_WIRELESS --ez no_ui true
waitn $OUT/R4a-hu.txt 1 'NativeAA: external Bluetooth module detected (' 60
sleep 5; mark R4a-end
send ACTION_STOP_WIRELESS; sleep 2; cap_off; hustop; th_end
```

PASS, all of, in `win R4a R4a-start R4a-end`:

1. T0 exactly 1.
2. W exactly 1, 28 s to 34 s after T0, with at least 1 `classified as refused` line between them.
3. `NativeAA: [ZJ]` 0. No socket file exists, so no probe runs.
4. `NativeAA: external Bluetooth module detected (` exactly 1, within 3 s after W.

Report: T0 to W in seconds; the count of `classified as refused` lines.

### R2. The route by probe, the carrier and the WiFi button (the point of the round)

The property is set and the stand-in listens. D-POCO stays off the link from R4a. R3 continues in the
same capture.

**`r2r3.sh`**
```bash
RUN=R2; th_gate; hustop; prop_on; base; zj_default; hu_read R2
stub_up R2                                        # MUST print STUB_UP
cap_on R2
mark R2-start
send ACTION_START_WIRELESS --ez no_ui true
waitn $OUT/R2-hu.txt 1 'NativeAA: [ZJ] Carrier started, watching ' 60
waitn $OUT/R2-hu.txt 2 'NativeAA: [ZJ] Connected to ' 60
mark R2-press
send ACTION_NATIVE_AA_POKE > $OUT/R2-press-reply.txt
waitn $OUT/R2-hu.txt 4 'NativeAA: [ZJ] Connected to ' 90
mark R2-end
# R3 starts here, same capture
mark R3-start
send ACTION_STOP_WIRELESS
waitn $OUT/R2-hu.txt 1 'NativeAA: [ZJ] Carrier stopped after ' 15
sleep 6                                           # the quiet window after the stop is what R3 measures
mark R3-rearm
send ACTION_START_WIRELESS --ez no_ui true
waitn $OUT/R2-hu.txt 2 'NativeAA: [ZJ] Carrier started, watching ' 20
waitn $OUT/R2-hu.txt 6 'NativeAA: [ZJ] Connected to ' 40
mark R3-end
send ACTION_STOP_WIRELESS; sleep 2; cap_off; hustop; stub_down; th_end
```

`ACTION_NATIVE_AA_POKE` with no `extra_mac` is the module route's WiFi button verb: it sends what the
home screen's WiFi button sends on this route. Its reply MUST read `"ok":true`, or the press conditions
(7 to 9) are UNTESTABLE.

PASS, all of, in `win R2 R2-start R2-end`. Name the first `Connected to` line C1, the second C2, and so
on.

1. T0 exactly 1, and `NativeAA: external Bluetooth module detected (` 0.
2. The ZBT daemon is asked first: at least 1 `classified as refused` line after T0. W2 exactly 1, within
   5 s of T0. W 0. The socket answer ended the 30 s window at its first refusal.
3. `NativeAA: [ZJ] probe /dev/socket/zj_bt_socket: CONNECTS` exactly 1, after the first
   `classified as refused` line and before W2. Its thread token MUST differ from the thread token on the
   `AutomationReceiver: ` line (it ran off the main thread).
4. `NativeAA: [ZJ] socket transport is on (`, then `NativeAA: [ZJ] Carrier started, watching /dev/socket/zj_bt_socket`,
   each exactly 1 and within 2 s after the probe line. `NativeAA: [ZJ] persist.zj.BTmac is not set, so the module's`
   exactly 1.
5. C1 reads `(attempt #1)` and comes within 5 s of the carrier start. `NativeAA: [TX] Sending WifiVersionRequest (Type 4)`
   follows C1 within 2 s.
6. Every C(n+1) has one `NativeAA: BT Handshake link closed.` between C(n) and it. For every C(n+1)
   that is not the first one after `R2-press`, the time from that closed line to C(n+1) is 2.9 s to
   4.5 s.
7. After `R2-press`: `AapService: WiFi button on the Bluetooth module route: WAKE_PHONE` exactly 1, then
   `NativeAA: Manual poke requested` exactly 1. `AapService: no module channel is open yet` 0,
   `AapService: Native-AA poke without a device ignored` 0, `AapService: WiFi button: the vendor daemon refused ` 0.
8. The first C after `R2-press` comes within 1.0 s of the later of two times: the press's
   `AutomationReceiver: ` line, and the last `NativeAA: BT Handshake link closed.` before that C. A
   press cuts the 3 s wait.
9. `NativeAA: [ZJ] this route sends no wake; the module brings the phone's link up by itself.` at most 1.
10. 0 each of: `NativeAA: Attempting active poke to device`, `NativeAA: Calling socket.connect()`,
    `NativeAA: ACTIVELY LISTENING on Android Auto UUID`, `the phone connected over Bluetooth and answered nothing we sent`,
    `NativeAA: [ZJ] Carrier stopped after `, `NativeAA: [ZJ] carrier stopped unexpectedly: `,
    `NativeAA: [ZJ] connect to /dev/socket/zj_bt_socket failed (`, `MATCH! Starting AapService`.

If the change did nothing (the contributor's commit): no ZBT dial, no probe line and no W2, because
that code took the route on the file at T0; the press would print `AapService: no module channel is
open yet` (condition 7) or `Native-AA poke without a device ignored`. Condition 2 and 3 separate the
two. If only the fourth commit did nothing: W 28 s to 34 s after T0, and the probe after W.

Report: T0 to the probe line, probe line to W2, and probe to C1, in seconds; every gap from a closed line to the
next C, in seconds, with the post-press gap marked; the press action read in condition 7; the count of
`NativeAA: Handshake failed` lines (record only).

### R3. Stop and re-arm keep the positive answer (same capture as R2)

PASS, all of, in `win R2 R3-start R3-end`:

1. `NativeAA: [ZJ] Carrier stopped after N attempts.` exactly 1, within 3 s of the
   `AutomationReceiver: com.andrerinas.openheadunit.ACTION_STOP_WIRELESS` line. N MUST equal the count
   of `NativeAA: [ZJ] Connected to ` in `win R2 R2-start R3-start` plus any between `R3-start` and the
   stopped line.
2. `NativeAA: [ZJ] Connected to ` 0 between the stopped line and `R3-rearm`.
3. After `R3-rearm`: `NativeAA: [ZJ] Carrier started, watching ` exactly 1, within 10 s of the second
   `AutomationReceiver: com.andrerinas.openheadunit.ACTION_START_WIRELESS` line.
4. After `R3-rearm`: T0 0, `NativeAA: [ZJ] probe ` 0, `NativeAA: [ZBT]` 0. The kept answer chose the
   route with no new dial of 3152 and no new probe.
5. After `R3-rearm`: the first `Connected to` reads `(attempt #1)` and comes within 5 s of the new carrier
   start. At least 1 more follows.
6. After `R3-rearm`: `this route sends no wake` at most 1. `MATCH! Starting AapService` 0.

If the change kept no answer: a T0, a 30 s ZBT window and a second probe line after `R3-rearm`
(condition 4).

Report: N; stop line to `R3-rearm` with 0 dials (yes/no); `R3-rearm` to the new C1, in seconds.

### R4b. A socket file that refuses does not take the route; the WiFi button asks again

Part 1 needs the stale socket file from pre-flight step 6. If that file did not stay after `stub_down`,
part 1 is INCONCLUSIVE (no stale file), and part 2 still runs.

**`r4b.sh`**
```bash
RUN=R4b; th_gate; hustop; prop_on; base; zj_default; hu_read R4b
stub_up R4b-make; stub_down                      # leaves a socket file that nothing accepts on
adb -s $HU shell ls -l $SOCK                     # record: present or absent
cap_on R4b
mark R4b-start
send ACTION_START_WIRELESS --ez no_ui true
waitn $OUT/R4b-hu.txt 1 'NativeAA: external Bluetooth module detected (' 60
sleep 3; mark R4b-blocked
stub_up R4b                                       # MUST print STUB_UP
mark R4b-press
send ACTION_NATIVE_AA_POKE > $OUT/R4b-press-reply.txt
waitn $OUT/R4b-hu.txt 1 'NativeAA: [ZJ] Carrier started, watching ' 60
waitn $OUT/R4b-hu.txt 1 'NativeAA: [ZJ] Connected to ' 15
mark R4b-end
send ACTION_STOP_WIRELESS; sleep 2; cap_off; hustop; stub_down; th_end
```

Part 1 PASS, all of, in `win R4b R4b-start R4b-blocked`:

1. T0 exactly 1, W exactly 1, W2 0. A refusing socket does not end the 30 s window.
2. `NativeAA: [ZJ] probe /dev/socket/zj_bt_socket: REFUSED (` exactly 2: the first after the first
   `classified as refused` line and before W, the second after W and within 3 s of it. The window's
   refusal is forgotten, so the socket is asked again at its end.
3. `NativeAA: external Bluetooth module detected (` exactly 1, after the probe line.
4. `NativeAA: [ZJ] Carrier started, watching ` 0 and `NativeAA: [ZJ] socket transport is on (` 0.

If the change did nothing: no probe line, and the route started on the file alone (`Carrier started`
with no W and no `external Bluetooth module detected (`). If only the fourth commit did nothing: one
`REFUSED (` line, after W.

Part 2 PASS, all of, in `win R4b R4b-press R4b-end`:

1. `AapService: WiFi button: the vendor daemon refused ` exactly 1 (the line ends `s ago; asking it again.`).
2. `AapService: WiFi button on the Bluetooth module route: ` exactly 1, reading `START_HANDSHAKE` or
   `REBUILD_LAUNCHER`. Record which.
3. T0 exactly 1, within 5 s of the press's `AutomationReceiver: ` line; then W2 exactly 1, within 5 s
   of T0. W 0.
4. `NativeAA: [ZJ] probe /dev/socket/zj_bt_socket: CONNECTS` exactly 1, before W2; then
   `NativeAA: [ZJ] Carrier started, watching ` exactly 1; then a `Connected to` that reads `(attempt #1)`.
5. `NativeAA: external Bluetooth module detected (` 0. `MATCH! Starting AapService` 0.

Report: whether the stale file was present; the part 1 probe text after `REFUSED (`; the part 2 action;
press to T0 and T0 to W2, in seconds.

### R5. The switch forces the route on an unmarked unit; the carrier survives a daemon restart

**`r5.sh`**
```bash
RUN=R5; th_gate; hustop; prop_off; base; zj_forced; hu_read R5
stub_up R5                                        # MUST print STUB_UP
cap_on R5
mark R5-start
send ACTION_START_WIRELESS --ez no_ui true
waitn $OUT/R5-hu.txt 1 'NativeAA: [ZJ] Connected to ' 30
sleep 2; mark R5-kill
stub_down
# pre-flight step 6: file stayed -> R5WAIT='NativeAA: [ZJ] connect to /dev/socket/zj_bt_socket failed ('
#                    file went   -> R5WAIT='NativeAA: [ZJ] Socket /dev/socket/zj_bt_socket does not exist yet.'
waitn $OUT/R5-hu.txt 1 "$R5WAIT" 40
sleep 5; mark R5-restart
stub_up R5b                                       # MUST print STUB_UP
waitn $OUT/R5-hu.txt 2 'NativeAA: [ZJ] Connected to ' 45
mark R5-end
send ACTION_STOP_WIRELESS; sleep 2; cap_off; hustop; stub_down; adb -s $HU shell rm -f $SOCK; th_end
```

PASS, all of, in `win R5 R5-start R5-end`:

1. T0 0, `NativeAA: [ZJ] probe ` 0, `NativeAA: [ZBT]` 0, `NativeAA: external Bluetooth module detected (` 0.
   The switch chose the route with no measurement.
2. `NativeAA: [ZJ] socket transport is on (` exactly 1, within 10 s of the `ACTION_START_WIRELESS`
   receiver line; then `Carrier started`; then the first `Connected to`, reading `(attempt #1)`, within
   5 s of it.
3. After `R5-kill`: `NativeAA: BT Handshake link closed.` at least 1, then
   `NativeAA: [ZJ] connect to /dev/socket/zj_bt_socket failed (REFUSED` exactly 1, whose line ends
   `next try in 3000ms`. If pre-flight step 6 showed that the file goes with the listener, this
   condition reads `NativeAA: [ZJ] Socket /dev/socket/zj_bt_socket does not exist yet. Waiting...`
   exactly 1 instead.
4. `NativeAA: [ZJ] Carrier stopped after ` 0 before `R5-end`. The carrier keeps going while the daemon
   is down.
5. After `R5-restart`: a `Connected to` within 31 s of `R5-restart`, reading `(attempt #2)`.

If the switch did nothing: R5 would show `NativeAA: [ZJ]` 0 and an ordinary Android radio bring-up
(`ACTIVELY LISTENING on Android Auto UUID`).

Report: start to C1, in seconds; `R5-restart` to the second C, in seconds; the text of the failed line.

### Cleanup (mandatory)

**`cleanup.sh`**
```bash
hustop
adb -s $HU shell sh /data/local/tmp/zj_kill.sh; adb -s $HU shell rm -f $SOCK; adb -s $HU shell ls -l $SOCK   # expect absent
prop_off                                          # expect empty
kill $PHLOOP 2>/dev/null; adb -s $PH shell svc wifi enable; adb -s $PH shell svc bluetooth enable
adb -s $MOTO shell svc bluetooth enable
```

Restore `$OUT/settings-backup.xml` on D-HU as root, `chown` it to the app's uid and gid, `chmod 660`,
and read it back (`rig-quirks/units/D-HU.md`). Diff it against the backup and state the delta.

**Stop rule:** if pre-flight step 6 prints `STUB_FAIL`, run R1, R4a and Cleanup only. If R2 condition 3
fails (no `CONNECTS` line), still run R3 to R5, because each reads its own route.

## 8. Do not re-run

First round of this thread, so nothing is settled on the rig. These stay with the JVM tests and are
not rig runs: the route order over every switch and answer (`ExternalBtTransportPolicyTest`), the ZBT
switch beating the ZJ switch, the cache rules and the probe order (`ZjSocketPolicyTest`), the retry and
backoff numbers, the address seed rule (`BluetoothAddressSeedPolicyTest`), and the setting's re-arm
(`WirelessRearmPolicyTest`).

## 9. Report back

1. R2: T0 to the probe line, the probe line to W2, and the post-press gap against the normal 2.9 s to
   4.5 s gaps.
2. R4b: the part 1 probe outcomes (expect `REFUSED` twice, no carrier), and part 2's probe outcome
   (expect `CONNECTS` before W2, carrier started).
3. R3 and R5: R3's N against the `Connected to` count, and R5's `R5-restart` to reconnect time.

## 10. The user's runs (not for the rig)

The rig does not run this section. The coding side sends it to the user whose unit has the socket. Ask
for log level VERBOSE (Settings, Advanced, log level), the app's exported log, and the phone's
`adb logcat` beside it in every run. The phone side is the only view of what the probe's close does.

| Run | Do | PASS when | FAIL when |
|---|---|---|---|
| U-A cold start | Force-stop, open the app, phone in the car | one `NativeAA: [ZJ] probe /dev/socket/zj_bt_socket: CONNECTS` before the first `NativeAA: [ZJ] Connected to`; then `SSL handshake complete`; `Holding the Bluetooth channel for the session` and `NativeAA: [HOLD] Bluetooth channel held ` while projecting; no `NativeAA: BT Handshake link closed.` before the session ends | a second probe line in one arming, or `BT Handshake link closed.` during projection |
| U-B second drive | End the session with Exit, wait 30 s, let the phone come back | `session state re-armed; waiting for the next manufacturer phone channel.`, a second `NativeAA: [ZJ] Connected to`, a second `SSL handshake complete`, no app restart, no `NativeAA: [ZJ] Carrier stopped after ` | `Carrier stopped after`, or no second session within 3 min |
| U-C WiFi button | Press the WiFi button with no session | `AapService: WiFi button on the Bluetooth module route: `, `NativeAA: Manual poke requested`; no `NativeAA: Attempting active poke to device`; no `the handshake servers are not running` | any of those |
| U-D no phone | Phone Bluetooth off, open the app, wait 3 min | record what each `Connected to` does with no phone; gaps from `BT Handshake link closed.` to the next `Connected to` of 2.9 s or more; no `the phone connected over Bluetooth and answered nothing we sent` | that warning, or a gap below 2.9 s |
| U-E calls | During projection, place a test call | `NativeAA: the external module named this unit's Bluetooth address (`, or a Bluetooth address already set in the app (record which); audio on the car speakers (by ear) | no address and calls on the phone |
| U-F probe close | Phone linked to the module, then arm (WiFi button after Exit) | phone logcat: no `Rfcomm socket connection failed, restarting WPP` within 5 s after the probe line's time; the session forms | the phone restarts WPP at the probe's close; then the next round builds the hand-over of the probed socket |

U-F decides whether the probe's connect and close is safe on a real daemon. A FAIL there is a finding
for the next round, not a defect in this branch.
