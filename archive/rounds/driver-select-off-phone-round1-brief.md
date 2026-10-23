# driver-select-off-phone, round 1 brief

Published name: `driver-select-off-phone-round1-brief.md`. Results go in
`driver-select-off-phone-round1-results.md`. Evidence: create the release
`rig-evidence-driver-select-off-phone` with the asset `driver-select-off-phone-round1-captures.zip`.

Estimated time: 20 min build and gate (pre-flight during the build), 6 min per run for S5, S1, S2 and
S3, 10 min for S4, 5 min closing. Total about 70 min. Units: D-HU (head unit), D-POCO (the reachable
phone), D-MOTO (the second phone, Bluetooth off except in S4).

## 1. Build and baseline

- **Candidate:** `fix/driver-select-off-phone` at **`b7793646`**
  (`b77936461f3f1020cf9e52e0ad144c3b8f3323af`), tree `e0acc4a99c714e77c805f14c97905e7d1fd63451`.
  Two commits on `main` `ec9d9c33`: `e1260b9b` (H1, the automatic pick reads the wake list) and
  `b7793646` (H2, only an explicit pick locks the accept gate).
- **History:** round 1 of this thread. Nothing was rewritten.
- **Baseline:** none built. The shipped behaviour is measured already, in `aa-178-protocol-levers`
  round 1, R2 (results file `aa-178-protocol-levers-round1-results.md`). S5 is the in-round control.

```bash
git ls-remote fork fix/driver-select-off-phone   # MUST print b77936461f3f1020cf9e52e0ad144c3b8f3323af
git fetch fork fix/driver-select-off-phone
git worktree add ../ohu-wt-dsop-b7793646 b77936461f3f1020cf9e52e0ad144c3b8f3323af
git -C ../ohu-wt-dsop-b7793646 rev-parse 'HEAD^{tree}'   # MUST print e0acc4a99c714e77c805f14c97905e7d1fd63451
cp <main checkout>/local.properties ../ohu-wt-dsop-b7793646/
```

If `ls-remote` prints nothing or another SHA, stop the round and say so.

Start the build and the unit tests in the background, then run the pre-flight (section 7) while they run:

```bash
( HUR_DIR=../ohu-wt-dsop-b7793646 rig-toolkit/build_hur_cool.sh && \
  HUR_DIR=../ohu-wt-dsop-b7793646 rig-toolkit/run_unit_tests.sh ) > $OUT/build.log 2>&1 &
BUILD_PID=$!
# ... pre-flight P1 to P7 ...
wait $BUILD_PID; echo "build exit=$?"
```

Pass the worktree to both scripts the way they take it today. Copy the APK out of `apks/` at once.
Expect **3130** tests, 0 failures. A build or test failure stops the round.

Install with `adb -s 27870808938846 install -r <apk>` on **D-HU only**. Identity gate, all MUST hold:

```bash
send ACTION_QUERY_STATE     # data= line MUST carry commit b7793646 (a "-dirty" suffix is fine)
unzip -p <pulled apk> 'classes*.dex' | strings | grep -cF extra_explicit_pick   # MUST be >= 1
unzip -p <pulled apk> 'classes*.dex' | strings | grep -cF autoScope             # MUST be >= 1
```

`main` carries neither symbol.

## 2. What this is and why

In `aa-178-protocol-levers` round 1, run R2, D-HU ran Native AA with `native-driver-selection-mode` 1
(AUTO), `native-poke-all-paired` false and a wake list (`native-poke-bt-macs`) that named D-POCO only.
D-MOTO was bonded to D-HU with its Bluetooth off. The capture shows this chain:

- `createGroup SUCCESS` at 12:24:48.005. The selector showed with a 10 s countdown.
- The countdown picked D-MOTO, because `last-connected-native-mac` named D-MOTO from an earlier
  thread: `NativeAA: Driver selected: A0:46:5A:97:E4:95` at 12:24:57.015.
- The gate then refused D-POCO: `NativeAA: the driver chose A0:46:5A:97:E4:95, so DC:B7:2E:5E:4E:59
  waits until that phone has had its turn.` at 12:25:05.429.
- D-HU poked D-MOTO four times, every poke `read failed ... ret: -1`. D-POCO logged 544
  `Retrying connection attempt on all channels`.
- The gate opened at exactly 120 s (`turned away 272 connection attempts before this one.` at
  12:26:57.281). SSL came at 12:27:04.455: 136.5 s after the group, against 59.8 s with mode 0 (R2b).

Two rules were wrong, and each commit fixes one:

1. **H1 (`e1260b9b`).** The automatic pick (the startup check, the countdown, the WiFi button) drew from
   every bonded phone, and history outranked the wake list. Now it draws only from the **wake scope**:
   the offered phones that `native-poke-bt-macs` names, every offered phone when the list is empty and
   `native-poke-all-paired` is true, and none when both are off. A phone with a live Bluetooth link
   still wins outside the scope. In AUTO with one phone in scope and none connected, no selector shows.
2. **H2 (`b7793646`).** Any pick locked the accept gate for as long as its wake ran, up to 120 s. Now
   only an **explicit pick** (a selector row tap, or Switch Phone with a MAC) locks it. An explicit pick
   keeps the 30 s floor. After the floor, its lock ends once one full wake round to that phone ends with
   both records failed (`DIALLED`) and no round since the pick was answered. An automatic pick still
   wakes its phone but locks nothing.

The pick's kind travels as the intent extra `extra_explicit_pick`. The log line
`NativeAA: Driver selected: <MAC>` now ends with `(explicit pick)` or `(automatic pick)`.

## 3. What is different about this round

- **S1 is the point of the round.** It is R2's setup on the candidate.
- **S5 runs first and is the control.** It is S1 with `native-driver-selection-mode` 0. Its
  `createGroup SUCCESS` to SSL time (`T5`) is the bound for S1 and S2. R2b's 59.8 s ran on another build
  (`dd5b2a82`), so this round measures its own baseline.
- **S3 and S4 need injected taps** (2 in S4, 1 in S3), because no `AutomationReceiver` verb makes an
  explicit pick. `ACTION_NATIVE_AA_POKE` sends no `extra_explicit_pick`, so it is an automatic pick by
  design. Each tap targets a node from a `uiautomator dump` by resource id and text, with exactly one
  match (`rig-quirks/topics/tooling.md`, "Injected taps"). A target that does not resolve voids the
  attempt. Never guess a coordinate. List every tap in Setup notes.
- **S3 has no countdown on purpose.** With `last-connected-native-mac` deleted and two phones in the wake
  list, the selector has no automatic target, so it shows no countdown. An animated countdown can make
  `uiautomator dump` fail to reach an idle state.
- **The 45 s figure from the plan is reported, not graded.** In R2 one `DIALLED` round to the off phone
  took about 40 s (two failed connects of about 20 s each). S3 grades the gate against the round's own
  lapse line instead.
- **D-MOTO's Bluetooth is off in S5, S1, S2 and S3, and must stay off.** A phone radio can self-revert
  (`rig-quirks/topics/bt.md`). Read D-MOTO's state before the launch and after the end marker. If it
  reads ON at either point, the run is a setup failure: repeat it once.
- **`last-connected-native-mac` changes after every session** (the app writes the projecting phone).
  Re-seed it before every run.
- **Log level is VERBOSE (`0`), as in R2.** At VERBOSE, `SSL handshake complete` prints twice per
  session (the DEBUG `Handshake:` form joins the INFO form). The helper `sslc` counts the INFO form only.
- **Gearhead 17.9 prints neither `Checking video config` nor `injectMotionEvent`.** Projection is graded
  on `Service Discovery Request: `, `Media Sink Setup Request: ` and `Throughput over`.
  `Service Discovery Response` is not an INFO line.
- **Stat D-HU's `shared_prefs/` first** (`rig-quirks/units/D-HU.md`). The app writes
  `last-connected-native-mac`. Record the owner.

Read `rig-quirks/topics/tooling.md`, `units/D-HU.md`, `units/D-POCO.md`, `units/D-MOTO.md`,
`topics/bt.md` and `topics/gearhead.md` before the round.

## 4. Settings keys

Back up D-HU's `settings.xml` at the round start and diff it against the last round's backup. State the
delta in Setup notes. **Leave the rig's audio keys as they are** (a deliberate worst case). Leave
`video-codec`, `resolutionId`, `fps-limit`, `wifi-direct-stable-identity` and `auto-start-bt-macs` as
found, and record each. `ACTION_LOG_MARKER` is not gated on this branch
(`AutomationCommandPolicy.CONFIGURING` does not list it).

**D-HU baseline** (write once with `hu_prefs`, app stopped; each run then writes only its own keys):

| Key | Type | Value | Why |
|---|---|---|---|
| `wifi-connection-mode` | int | `3` | Native AA |
| `native-ap-transport` | int | `0` | WiFi Direct |
| `wifi-direct-band` | int | `1` | 5 GHz, as R2 and R2b |
| `native-poke-all-paired` | boolean | `false` | the wake list is the only scope |
| `native-preferred-device-mac` | string | delete | no preferred phone, as R2 |
| `native-driver-selection-timeout` | int | `10` | as R2 |
| `native-aa-wake-damage-verdict` | int | `0` | a latched verdict stands the poke down |
| `video-profile-starvation-cap` | boolean | delete | clears the cap from earlier fumbles |
| `log-level` | int | `0` | VERBOSE, as R2 |
| `onboarding-version` | int | `2` | no wizard over a launch |

**Per run** (MACs: D-POCO `DC:B7:2E:5E:4E:59`, D-MOTO `A0:46:5A:97:E4:95`; confirm both in P3):

| Run | `native-driver-selection-mode` (int) | `native-poke-bt-macs` (set) | `last-connected-native-mac` (string) | D-MOTO Bluetooth |
|---|---|---|---|---|
| S5 | `0` | D-POCO | D-MOTO | off |
| S1 | `1` | D-POCO | D-MOTO | off |
| S2 | `1` | D-POCO, D-MOTO | D-MOTO | off |
| S3 | `1` | D-POCO, D-MOTO | delete | off |
| S4 | `1` | D-POCO, D-MOTO | D-POCO | off at launch, on before the switch |

`native-poke-bt-macs` is a `<set>`. Write it only with `hu_prefs` (section 5), which rebuilds the
element on the host. The template's line-scoped `sed` corrupts a `<set>`. Read the file back before
every launch and record the four per-run values.

## 5. Helpers

```bash
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
HU=27870808938846; PH=4f4027e9; MOTO=ZY22GC3BM4
POCO_MAC=DC:B7:2E:5E:4E:59; MOTO_MAC=A0:46:5A:97:E4:95
OUT=rig-data/rounds/driver-select-off-phone-round1; mkdir -p $OUT
send() { a=$1; shift; adb -s $HU shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; }
mark() { send ACTION_LOG_MARKER --es text "$1"; }
huexit() { adb -s $HU shell am start -a android.intent.action.VIEW -d "headunit://exit"; sleep 5; }
phnow() { adb -s $PH shell "date '+%m-%d %H:%M:%S.000'" | tr -d '\r'; }
launch() { PHT=$(phnow); echo "PHT=$PHT"; sleep 0.3
  adb -s $HU shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity; }

# captures start before the launch; S4 also captures D-MOTO
cap_on() { adb -s $HU logcat -c; adb -s $PH logcat -G 16M
  stdbuf -oL adb -s $HU logcat -v threadtime > $OUT/$1-hu.txt & HUPID=$!
  stdbuf -oL adb -s $PH logcat -v threadtime -T 1 -b main,system,crash,events > $OUT/$1-phone.txt & PHPID=$!
  MOPID=; if [ "$2" = moto ]; then
    stdbuf -oL adb -s $MOTO logcat -v threadtime -T 1 -b main,system,crash,events > $OUT/$1-moto.txt & MOPID=$!; fi
  sleep 2; }
cap_off() { sleep 2; kill $HUPID $PHPID $MOPID 2>/dev/null; }
waitfor() { local n=0; while [ $n -lt $3 ]; do grep -aqF "$2" "$1" && { echo $n; return 0; }; sleep 5; n=$((n+5)); done; echo TIMEOUT; return 1; }
# wait for STRING after MARKER: waitafter FILE MARKER STRING SECS [COUNT]
waitafter() { local n=0 c=${5:-1}; while [ $n -lt $4 ]; do
  [ "$(from_mark "$1" "$2" | grep -acF "$3")" -ge $c ] && { echo $n; return 0; }; sleep 2; n=$((n+2)); done; echo TIMEOUT; return 1; }

# head unit greps: one launch per capture
upto() { awk -v e="AutomationMarker: $2-end" '{print} index($0,e){exit}' "$1"; }
from_mark() { awk -v a="AutomationMarker: $2" 'index($0,a){f=1} f' "$1"; }
cnt()  { upto "$1" "$2" | grep -acF -- "$3"; }                      # cnt FILE ID STRING
line() { upto "$1" "$2" | grep -aF -- "$3" | head -1; }             # first matching line
sslc() { upto "$1" "$2" | grep -aF 'SSL handshake complete' | grep -avcF 'Handshake: SSL handshake complete'; }
ssln() { upto "$1" "$2" | grep -aF 'SSL handshake complete' | grep -avF 'Handshake: SSL handshake complete' | sed -n "${3}p"; }
tsec() { awk '{split($2,t,":"); printf "%.3f\n", t[1]*3600+t[2]*60+t[3]}'; }   # one threadtime line on stdin
dt()   { awk -v a="$(echo "$1" | tsec)" -v b="$(echo "$2" | tsec)" 'BEGIN{printf "%.1f\n", b-a}'; }  # dt LINE_A LINE_B

# phone greps: phone clock; threadtime lines start "MM-DD HH:MM:SS.mmm"
ph_btw() { awk -v t="$2" -v u="$3" 'substr($0,1,18) >= t && substr($0,1,18) < u' "$1"; }
pcnt() { ph_btw "$1" "$2" "$3" | grep -acF -- "$4"; }                # pcnt FILE FROM TO STRING

ph_up() { adb -s $PH shell cmd connectivity airplane-mode disable; sleep 2
  adb -s $PH shell svc wifi enable; sleep 0.3; adb -s $PH shell svc bluetooth enable; sleep 5
  adb -s $PH shell dumpsys bluetooth_manager | grep -m1 -E "^ *state:"; adb -s $PH shell dumpsys wifi | grep -m1 "Wi-Fi is"; }
ph_down() { adb -s $PH shell cmd connectivity airplane-mode enable; sleep 5; }
moto_bt() { adb -s $MOTO shell svc bluetooth $1; sleep 5
  adb -s $MOTO shell dumpsys bluetooth_manager | grep -m1 -E "^ *state:"; }        # moto_bt disable|enable
moto_state() { adb -s $MOTO shell dumpsys bluetooth_manager | grep -m1 -E "^ *state:"; }
```

**`hu_prefs`** rebuilds `settings.xml` on the host and puts it back as root, so a `<set>` is never
edited in place. Save this as `$OUT/prefs_edit.py`:

```python
import re, sys
f, specs = sys.argv[1], sys.argv[2:]
s = open(f).read()
for spec in specs:                       # int:KEY=V  boolean:KEY=V  string:KEY=V  set:KEY=A,B  del:KEY
    kind, rest = spec.split(':', 1)
    key, _, val = rest.partition('=')
    k = re.escape(key)
    s = re.sub(r'<(int|boolean|long|float|string|set) name="%s"\s*/>' % k, '', s)
    s = re.sub(r'<(int|boolean|long|float) name="%s"[^>]*/>' % k, '', s)
    s = re.sub(r'<string name="%s">.*?</string>' % k, '', s, flags=re.S)
    s = re.sub(r'<set name="%s">.*?</set>' % k, '', s, flags=re.S)
    if kind == 'del':
        continue
    if kind == 'string':
        el = '<string name="%s">%s</string>' % (key, val)
    elif kind == 'set':
        el = '<set name="%s">%s</set>' % (key, ''.join('<string>%s</string>' % m for m in val.split(',') if m))
    else:
        el = '<%s name="%s" value="%s" />' % (kind, key, val)
    s = s.replace('</map>', el + '</map>', 1)
open(f, 'w').write(s)
```

```bash
SP=/data/data/$PKG/shared_prefs/settings.xml
hu_prefs() {   # app stopped; hu_prefs SPEC...
  adb -s $HU exec-out cat $SP > $OUT/settings-edit.xml
  python3 -I $OUT/prefs_edit.py $OUT/settings-edit.xml "$@" || return 1
  python3 -I -c "import sys,xml.dom.minidom as m; m.parse(sys.argv[1])" $OUT/settings-edit.xml || return 1
  adb -s $HU push $OUT/settings-edit.xml /data/local/tmp/settings-edit.xml >/dev/null
  adb -s $HU shell "cp /data/local/tmp/settings-edit.xml $SP; chown \$(stat -c %u:%g /data/data/$PKG) $SP; chmod 660 $SP"
}
hu_read() { adb -s $HU exec-out cat $SP | grep -aoE "(native-driver-selection-mode|native-driver-selection-timeout|last-connected-native-mac|native-poke-all-paired|native-preferred-device-mac|wifi-connection-mode|wifi-direct-band|log-level)\"[^/]*|<set name=\"native-poke-bt-macs\">.*</set>"; }
```

**Injected taps** (at most 5 per run; S3 uses 1, S4 uses 2). Save as `$OUT/node_xy.py`:

```python
import re, sys, xml.etree.ElementTree as ET
f, rid, text = sys.argv[1:4]
hits = [n for n in ET.parse(f).iter('node') if n.get('resource-id') == rid and n.get('text') == text]
if len(hits) != 1:
    print('%d matches' % len(hits)); sys.exit(1)
n = hits[0]
if n.get('enabled') != 'true':
    print('not enabled'); sys.exit(1)
x1, y1, x2, y2 = map(int, re.findall(r'\d+', n.get('bounds')))
print((x1 + x2) // 2, (y1 + y2) // 2)
```

```bash
TAPS=0   # reset to 0 at the start of every run
tap_node() {   # tap_node RUN LABEL RESOURCE_ID TEXT
  TAPS=$((TAPS+1)); [ $TAPS -le 5 ] || { echo "TAP BUDGET SPENT"; return 1; }
  local i XY; for i in 1 2 3; do
    adb -s $HU shell uiautomator dump /sdcard/ui.xml | grep -q dumped && break; sleep 1; done
  sleep 0.3; adb -s $HU pull /sdcard/ui.xml $OUT/$1-$2.xml >/dev/null; sleep 0.3
  XY=$(python3 -I $OUT/node_xy.py $OUT/$1-$2.xml "$PKG:id/$3" "$4") || { echo "NO TARGET $1-$2: $XY"; return 1; }
  mark "$1-$2"; sleep 0.3
  echo "$1 $2 $3 '$4' at $XY" | tee -a $OUT/taps.txt
  adb -s $HU shell input tap $XY
}
```

Copy `th_gate`, `th_watch` and `th_report` from `rig-data/rounds/aa-178-protocol-levers-round1/scripts/lib.sh`
(they use `rig_thermal.sh wait 75` and `watch`). Call `RUN=<id> th_gate` before every run and report
`th_report <id>` in each run's section. Count verdicts stand whatever the temperature. A timing verdict
(S1, S2, S3) is re-run once if the throttle counter rose or the package reached 90C. Never run two adb
commands against one unit at the same time.

## 6. The deciding lines

Each head unit string was checked with `grep -F -r` on `app/src` at `b7793646`. Two lines are built at
run time; the runs grep the run-time text below, and the `decisive-strings` block at the end lists the
verbatim source fragments. The phone strings are Gearhead's and are in the `phone-strings` block.

**Head unit capture** (`$OUT/<id>-hu.txt`, `upto` the end marker unless a run says otherwise):

| Line (as it prints) | Level | What it means |
|---|---|---|
| `WifiLauncher: Initializing WiFi Mode: NATIVE` | I | path under test |
| `BluetoothHelper: driver candidates: ` | I | which bonded devices are phones (printed when the mix changes) |
| `AapService: ACTION_NATIVE_AA_PROMPT_SHOWN received` | I | the selector is on screen |
| `HomeFragment: Unambiguous driver (<name>) - auto-connecting directly without prompt` | I | the startup check picked without a selector |
| `HomeFragment: Connecting to Native-AA device: <name> (<MAC>), btConnected=<bool>` | I | a UI pick went to the service |
| `NativeAA: Driver selected: <MAC> (automatic pick)` / `(explicit pick)` | I | **the pick and its kind** |
| `NativeAA: Manual poke requested for <name> (<MAC>)` | I | the chosen phone's wake started |
| `NativeAA: Calling socket.connect() for <name> via <profile> (<uuid>)...` | I | one poke attempt to `<name>` |
| `NativeAA: Poke via <profile> to <name> (<MAC>) failed: <msg>` | I | that attempt failed |
| `NativeAA: <name> (<MAC>) did not answer a whole wake round, so other phones are let in once the first 30s pass.` | I | **an explicit pick's lock lapsed** |
| `NativeAA: Selection prompt active (target=` | I | refused while the selector shows (expected) |
| `NativeAA: the driver chose <MAC>, so <MAC> waits until that phone has had its turn.` | I | **refused by a chosen driver's lock** |
| `NativeAA: <MAC> has been turned away <N> times since the gate closed.` | I | the same refusal, restated once a minute |
| `NativeAA: turned away <N> connection attempts before this one.` | I | the gate opened after N refusals |
| `NativeAA: <MAC> is the phone this switch moved away from, so it is not let back in yet.` | I | S4: the switch-away refusal |
| `NativeAA: a driver switch is starting, so <MAC> is not let straight back in.` | I | S4: the switch began |
| `AapProjectionActivity: User requested switch driver` | I | S4: the Switch Phone row was tapped |
| `AapService: ACTION_NATIVE_AA_SWITCH_DEVICE received (targetMac=` | I | S4: the service took the switch |
| `NativeAA: [TX] Sending WifiVersionRequest (Type 4)` | I | a phone passed the gate and a handshake started |
| `WirelessServer: Incoming connection detected from /<ip>` | I | a phone joined the group |
| `SSL handshake complete` (helper `sslc`, INFO form only) | I | session formed |
| `Service Discovery Request: `, `Media Sink Setup Request: `, `Throughput over` | I | projection |
| `Standard createGroup SUCCESS!` (grep `createGroup SUCCESS`) | I | group up; discard if 2 in one run |
| `MATCH! Starting AapService`, `Magic Garbage detected in header` | I | discard rules (template §4) |
| `AutomationReceiver: ` / `AutomationMarker: ` | I/W | a verb or marker landed; a missing line voids the step |

**Phone capture** (`$OUT/<id>-phone.txt` for D-POCO, `$OUT/S4-moto.txt` for D-MOTO; phone clock).
Record each phone's Gearhead version first:
`adb -s <phone> shell dumpsys package com.google.android.projection.gearhead | grep versionName`.

| Line (as it prints) | What it means |
|---|---|
| `Retrying connection attempt on all channels` | a WPP restart; a refused phone logs about 4.5 a second (R2: 544) |
| `GH.WIRELESS.SETUP: State changed to ` | the phone's wireless setup moved on |
| `Creating rfcomm socket for device: ` | the phone dialled a head unit's Android Auto channel |

Both D-POCO strings printed on 17.9 in `aa-178-protocol-levers` round 1. `Creating rfcomm socket for
device: ` is from `rig-quirks/units/D-POCO.md` on an older build. A 0 on another version can mean a
renamed string: say so.

## 7. Runs

Order: **S5, S1, S2, S3, S4.** Each run starts from the baseline plus its own keys (section 4).

### R0. Build gate (20 min, no phone)

Section 1. Then the pre-flight below, during the build.

### Pre-flight (one batch, during the R0 build, before the install)

- **P1, read only.** `rig-toolkit/rig_preflight.sh D_HU:wifi,bt D_POCO:wifi,bt D_MOTO:bt`. D-POCO on USB,
  unlocked (`wm dismiss-keyguard`) and at home (`dumpsys window | grep mCurrentFocus`; fix with
  `input keyevent KEYCODE_HOME`). D-POCO `dumpsys wifip2p | grep isGroupOwner` MUST be false (else
  `svc wifi disable`, `svc wifi enable`). D-MOTO: record `dumpsys window | grep mCurrentFocus` and whether
  a PIN keyguard shows (`dumpsys window | grep -i keyguard`).
- **P2.** D-HU: `adb -s $HU shell 'stat -c "%U:%G %a" /data/data/com.andrerinas.headunitrevived/shared_prefs'`.
  Record it. If the owner is not the app's uid, `chown` it and record both reads.
- **P3, read only.** `adb -s <unit> shell dumpsys bluetooth_manager | grep -a -A12 "Bonded devices"` on
  D-HU, D-POCO and D-MOTO. D-HU MUST list `POCO X3 NFC` at `DC:B7:2E:5E:4E:59` and
  `motorola edge 30 neo` at `A0:46:5A:97:E4:95`. D-POCO and D-MOTO MUST list `Navegadortz2` and MUST
  NOT list `Navegadortz3`. Record any other phone D-HU lists: it adds a selector row, nothing else.
- **P4, read only.** D-HU station: `adb -s $HU shell dumpsys wifi | grep -m1 -a mWifiInfo`. Record SSID and
  frequency.
- **P5.** D-MOTO: `moto_bt disable`. It MUST read `state: OFF`. It stays off until S4.
- **P6, read only.** Gearhead `versionName` on D-POCO and D-MOTO. Record both.
- **P7, one message to the operator**, only for what P1 to P6 found wrong: a missing or extra bond, a
  PIN keyguard on D-MOTO (ask for one unlock before S4), or D-MOTO not reachable by adb. If nothing is
  wrong, send nothing.

Then wait for the build, install, and run the identity gate. Only then back up D-HU's `settings.xml`,
write the baseline (section 4) with one `hu_prefs` call, and read it back with `hu_read`.

### Common run frame

Every run uses this frame. `<id>` is the run id; `<keys>` are the run's `hu_prefs` specs from section 4.

1. `TAPS=0`. `ph_down`. `adb -s $HU shell am force-stop $PKG`. `RUN=<id> th_gate`.
2. `hu_prefs <keys>`, then `hu_read > $OUT/<id>-prefs.txt`. The four per-run values MUST match section 4.
3. `moto_state > $OUT/<id>-moto-before.txt`. It MUST read `OFF` (S4 included).
4. D-HU `dumpsys wifip2p | grep groupFormed` MUST read false (else `huexit`, then
   `adb -s $HU shell cmd wifip2p remove-group`, then read again).
5. `cap_on <id>` (S4: `cap_on S4 moto`), `launch`, `sleep 3`, `mark <id>-start`.

Then the run's own steps. Every run ends with `mark <id>-end`, `huexit`, `cap_off`, and
`moto_state > $OUT/<id>-moto-after.txt` (S5 to S3: it MUST read `OFF`).

**Discard rules** (template §4), checked with `cnt` on the run's capture: `createGroup SUCCESS` MUST be 1,
`Magic Garbage detected in header` 0, `MATCH! Starting AapService` 0 (S4: a `MATCH!` after
`S4-moto-on` is recorded, not a discard, unless a second `createGroup SUCCESS` follows). A setup failure
(a precondition above, a pre-check below, a verb with no `AutomationReceiver:` line, a tap target that
does not resolve) is fixed and re-run once, never graded.

Timing names used below, all from the head unit capture: `G` = the first `createGroup SUCCESS` line,
`S` = `ssln F <id> 1`, `P` = the run's `NativeAA: Driver selected:` line.

### S5. Control: mode 0 (5 min)

Keys: `int:native-driver-selection-mode=0 set:native-poke-bt-macs=$POCO_MAC string:last-connected-native-mac=$MOTO_MAC`.

Steps after the frame: `sleep 15`, `ph_up`, `waitfor $OUT/S5-hu.txt "SSL handshake complete" 150`,
`PHS=$(phnow)`, `sleep 20`, `mark S5-end`, `huexit`, `cap_off`. On TIMEOUT: end the run the same way and
repeat it once as `S5b`. A second TIMEOUT stops the round (the rig cannot form a session).

Pre-check: `cnt F S5 "Initializing WiFi Mode: NATIVE"` >= 1.

PASS, every condition (`F=$OUT/S5-hu.txt`, `P=$OUT/S5-phone.txt`):

1. `cnt F S5 "NativeAA: Driver selected: "` = 0 and `cnt F S5 "waits until that phone has had its turn"` = 0.
2. `sslc F S5` = 1. Record `T5 = dt "$G" "$S"` in seconds.
3. Projection: `cnt F S5 "Service Discovery Request: "` >= 1, `cnt F S5 "Media Sink Setup Request: "` >= 1,
   `cnt F S5 "Throughput over"` >= 1.
4. Phone: record `R5 = pcnt P "$PHT" "$PHS" "Retrying connection attempt on all channels"`.

Record, not graded: the first `BluetoothHelper: driver candidates: ` line; `cnt F S5 "Calling socket.connect() for motorola"`.

### S1. The point of the round: R2's setup on the candidate (5 min)

Keys: `int:native-driver-selection-mode=1 set:native-poke-bt-macs=$POCO_MAC string:last-connected-native-mac=$MOTO_MAC`.

Steps after the frame: as S5 (`S1` in place of `S5`, repeat once as `S1b` on TIMEOUT; a second TIMEOUT is a FAIL).

Pre-check: `cnt F S1 "Initializing WiFi Mode: NATIVE"` >= 1; `hu_read` showed mode 1 and
`last-connected-native-mac` = D-MOTO before the launch.

PASS, every condition (`F=$OUT/S1-hu.txt`, `P=$OUT/S1-phone.txt`):

1. `cnt F S1 "HomeFragment: Unambiguous driver (POCO X3 NFC)"` = 1 and
   `cnt F S1 "AapService: ACTION_NATIVE_AA_PROMPT_SHOWN received"` = 0.
2. `cnt F S1 "NativeAA: Driver selected: $POCO_MAC (automatic pick)"` = 1 and
   `cnt F S1 "NativeAA: Driver selected: $MOTO_MAC"` = 0.
3. `cnt F S1 "Calling socket.connect() for motorola"` = 0.
4. `cnt F S1 "waits until that phone has had its turn"` = 0.
5. `sslc F S1` = 1, and `dt "$G" "$S"` <= `T5 + 10`. Record the number and the plan's bound of 70 s beside it.
6. Projection: as S5 condition 3.
7. Phone: `pcnt P "$PHT" "$PHS" "Retrying connection attempt on all channels"` <= `R5 + 20`.

FAIL: any condition not met. If condition 4 fails, record the first refusal line, the `P` line and every
`Calling socket.connect()` line.

What a PASS looks like if the change did nothing: the shipped build shows the selector, the countdown picks
D-MOTO, and the refusal line follows (R2). Conditions 1 to 5 all fail there, so S1 cannot pass by accident.

### S2. A countdown pick of the off phone locks nothing (5 min)

Keys: `int:native-driver-selection-mode=1 set:native-poke-bt-macs=$POCO_MAC,$MOTO_MAC string:last-connected-native-mac=$MOTO_MAC`.

Steps after the frame: as S5, with `S2` (repeat once as `S2b` on TIMEOUT; a second TIMEOUT is a FAIL). Do
not touch D-HU's screen: the 10 s countdown must expire.

Pre-check (setup failure if not met): `cnt F S2 "AapService: ACTION_NATIVE_AA_PROMPT_SHOWN received"` = 1;
`cnt F S2 "NativeAA: Driver selected: $MOTO_MAC (automatic pick)"` = 1. If the countdown named D-POCO, the
seeded `last-connected-native-mac` did not hold: record `hu_read` and re-run once.

PASS, every condition (`F=$OUT/S2-hu.txt`, `P=$OUT/S2-phone.txt`):

1. `cnt F S2 "(explicit pick)"` = 0.
2. `cnt F S2 "Calling socket.connect() for motorola"` >= 1 (the automatic pick still wakes its phone). Record
   the count and each `Poke via ... failed:` line for D-MOTO.
3. `cnt F S2 "waits until that phone has had its turn"` = 0 and `cnt F S2 "did not answer a whole"` = 0.
4. `sslc F S2` = 1, and `dt "$G" "$S"` <= `T5 + 20` (the countdown holds the gate for 10 s by design).
   Record the number and the plan's bound of 70 s beside it.
5. The first `NativeAA: [TX] Sending WifiVersionRequest (Type 4)` after `P` is less than 60 s after `P`
   (`dt "$P" "<that line>"`). Record the number. The shipped build's figure is 120 s.
6. Projection: as S5 condition 3.
7. Phone: `pcnt P "$PHT" "$PHS" "Retrying connection attempt on all channels"` < 150. Record it (R2: 544).

Record, not graded: `cnt F S2 "NativeAA: Selection prompt active (target="`; any `turned away` line.

What a PASS looks like if H2 did nothing: the countdown pick locks the gate, condition 3 fails on the first
D-POCO dial, and condition 5 reads about 120 s.

### S3. An explicit pick of the off phone lapses after one unanswered round (6 min)

Keys: `int:native-driver-selection-mode=1 set:native-poke-bt-macs=$POCO_MAC,$MOTO_MAC del:last-connected-native-mac`.

With no history and two phones in scope, the selector shows with no countdown and waits.

Steps after the frame:

1. `waitafter $OUT/S3-hu.txt S3-start "AapService: ACTION_NATIVE_AA_PROMPT_SHOWN received" 20`. On TIMEOUT:
   setup failure.
2. `tap_node S3 pick deviceName "motorola edge 30 neo"`. On `NO TARGET`: setup failure, keep the dump.
3. `waitafter $OUT/S3-hu.txt S3-pick "NativeAA: Driver selected: $MOTO_MAC (explicit pick)" 10`. On
   TIMEOUT: the tap did not land; setup failure.
4. `sleep 8`, `ph_up` (D-POCO comes up inside the 30 s floor, so the lock is exercised).
5. `waitfor $OUT/S3-hu.txt "SSL handshake complete" 150`, `PHS=$(phnow)`, `sleep 20`, `mark S3-end`, `huexit`,
   `cap_off`. On TIMEOUT: end the run and grade it (a FAIL unless a precondition failed).

Pre-check: `cnt F S3 "HomeFragment: Connecting to Native-AA device: motorola edge 30 neo"` = 1;
`cnt F S3 "Calling socket.connect() for motorola"` >= 1.

Times (`F=$OUT/S3-hu.txt`, `P=$OUT/S3-phone.txt`): `L` = the line holding `did not answer a whole`;
`O` = the first `NativeAA: [TX] Sending WifiVersionRequest (Type 4)` after `P`.

**Gate:** `cnt F S3 "waits until that phone has had its turn"` >= 1. If it is 0, D-POCO never dialled while
the lock stood, and S3 is **INCONCLUSIVE**: record `P`, `O` and the time `ph_up` ended.

PASS, every condition (gate passed):

1. `cnt F S3 "(explicit pick)"` = 1 and `cnt F S3 "NativeAA: Driver selected: "` = 1.
2. `cnt F S3 "did not answer a whole"` = 1. Record `dt "$P" "$L"`; the plan expected about 35 to 45 s.
3. The floor held: `dt "$P" "$O"` >= 29.5.
4. The lock ended on the lapse: `dt "$P" "$O"` <= max(30, `dt "$P" "$L"`) + 10.
5. `dt "$P" "$O"` < 90 (the shipped ceiling is 120 s).
6. `sslc F S3` = 1, and the projection lines of S5 condition 3.
7. Phone: `pcnt P "$PHT" "$PHS" "Retrying connection attempt on all channels"` < 400. Record it (R2: 544 over
   a 120 s lock).

FAIL: the gate passed and any condition is not met. Record every refusal line, `L`, `O`, and each D-MOTO
`Poke via ... failed:` line with its time.

What a PASS looks like if H2 did nothing: the lock holds to 120 s, condition 2 has no line, and conditions 4
and 5 fail.

### S4. Positive control: Switch Phone to a phone that answers (10 min)

Keys: `int:native-driver-selection-mode=1 set:native-poke-bt-macs=$POCO_MAC,$MOTO_MAC string:last-connected-native-mac=$POCO_MAC`.

D-MOTO stays off until D-POCO projects, so the first session can only be D-POCO's.

Steps after the frame (`cap_on S4 moto`):

1. `sleep 15`, `ph_up`. If a selector shows, its countdown names D-POCO; let it expire.
2. `waitfor $OUT/S4-hu.txt "SSL handshake complete" 150`. On TIMEOUT: setup failure, repeat once.
3. `sleep 20`. `mark S4-moto-on`, `moto_bt enable`; it MUST read `state: ON`. `sleep 30`.
4. `adb -s $HU shell dumpsys window | grep mCurrentFocus` MUST name `AapProjectionActivity` (else
   `send ACTION_RAISE_PROJECTION`, `sleep 3`, read again; a second miss is a setup failure).
5. `mark S4-switch`, `adb -s $HU shell input keyevent 4`, `sleep 2`.
6. `tap_node S4 switch text "Switch Phone"`. On `NO TARGET`: setup failure, keep the dump.
7. `waitafter $OUT/S4-hu.txt S4-switch "AapService: ACTION_NATIVE_AA_PROMPT_SHOWN received" 30`. On TIMEOUT:
   setup failure.
8. `tap_node S4 pick deviceName "motorola edge 30 neo"`.
9. `waitafter $OUT/S4-hu.txt S4-pick "SSL handshake complete" 120`. Then
   `adb -s $MOTO shell ip -4 addr | grep -oE '192\.168\.49\.[0-9]+' > $OUT/S4-moto-ip.txt`.
10. `sleep 20`, `mark S4-end`, `huexit`, `cap_off`. `moto_bt disable` (it MUST read `OFF` again).

Pre-check: the first session is D-POCO's (`cnt F S4 "Driver selected: $MOTO_MAC"` before `S4-switch` is 0).

PASS, every condition (`F=$OUT/S4-hu.txt`, `M=$OUT/S4-moto.txt`; "after X" means `from_mark F X`):

1. After `S4-switch`: `AapProjectionActivity: User requested switch driver` = 1 and
   `NativeAA: a driver switch is starting, so $POCO_MAC is not let straight back in.` = 1.
2. After `S4-pick`: `NativeAA: Driver selected: $MOTO_MAC (explicit pick)` = 1.
3. `cnt F S4 "did not answer a whole"` = 0 (D-MOTO answered, so the lock never lapsed).
4. `sslc F S4` = 2, and `dt "$P" "<second SSL line>"` <= 60. Record the number.
5. The last `WirelessServer: Incoming connection detected from /` before the second SSL carries the address
   in `$OUT/S4-moto-ip.txt`.
6. `cnt F S4 "createGroup SUCCESS"` = 1.
7. Phone (D-MOTO, phone clock from `S4-pick`): `Creating rfcomm socket for device: ` count >= 1. If the
   count is 0, record D-MOTO's Gearhead version and every `GH.WIRELESS` line, and say the string may be
   absent on that build; condition 5 then carries the phone side.

Record, not graded: every D-POCO refusal after `S4-switch` (switch-away or chosen-driver form) with its
time; `Successfully poked motorola edge 30 neo` and its time; the two `Service Discovery Request: ` lines.

FAIL: any condition not met.

### Closing

`huexit` on D-HU if the app runs, then force-stop. D-MOTO: `moto_bt enable` (it is left as found at round
start; P5 switched it off). Restore D-HU's round-start backup as root (`rig-quirks/units/D-HU.md`), `chown`
and `chmod 660`, read it back and diff it against the backup: MUST be empty. A later run in this round
that needs the rig again re-seeds the baseline first (the restore brings back the round-start values).
Leave the candidate APK installed.

**Stop rules.** Each run has at most one repeat, and only for a setup failure, a TIMEOUT as written, or a
thermal re-run. Discard and re-run once on a template §4 discard hit. After that, grade what you have.
Two S5 TIMEOUTs stop the round.

## 8. Do not re-run

- The shipped build's behaviour on R2's setup (`aa-178-protocol-levers` round 1, R2): 120 s lock, 544 phone
  retries, 136.5 s to SSL.
- The JVM cases for `autoScope`, the scoped `resolveAutoConnectTarget` and `shouldShowSelector`,
  `chosenExclusive`, `switchGate` and `explicitPickLapses` (3130 tests in R0).
- The #932 switch race with a hands-free link up (`driver-selection-native` round 6, R14). S4 checks only
  that an answered explicit pick keeps its lock on this build.

## 9. Report back

1. S1: `dt G S` against `T5`, and the counts of `Driver selected: $MOTO_MAC`,
   `Calling socket.connect() for motorola` and `waits until that phone has had its turn`.
2. S2 and S3: `dt P O` for each, and for S3 `dt P L` (the time one unanswered round took).
3. S4: the explicit pick line, the lapse count (0), and the second SSL's client IP against D-MOTO's address.
