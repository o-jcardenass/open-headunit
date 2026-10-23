# pr-1110-wifi-direct-wedge, round 2 brief (with PR #1109)

Published name: `pr-1109-1110-wifi-direct-round2-brief.md`. Results go in
`pr-1109-1110-wifi-direct-round2-results.md`. Evidence: upload `pr-1110-wifi-direct-wedge-round2-captures.zip`
to the existing release `rig-evidence-pr-1110-wifi-direct-wedge`.

Round 1 of this thread is `pr-1110-wifi-direct-wedge-round1-brief.md` and its results file. This round
tests a new #1110 head and adds #1109 on the same build.

Units: D-HU (head unit) and D-POCO (phone). D-MOTO keeps its Bluetooth off for the whole round and runs
nothing. D-SAM and D-HP take no part. If D-SAM is plugged in, keep its app stopped.

Hand steps: none planned. Injected taps: R5 (at most 4) and R4 (at most 4), each on a dumped target. The
"Allow USB debugging?" tap in R5 is one of them.

Queue line: `pr-1109-1110-wifi-direct-round2-brief.md` (C `fc346982` on `fork/review/pr-1109-1110`, one arm,
settings are the controls; units D-HU and D-POCO, D-MOTO Bluetooth off; R5 and R4 are the point).

Pairing: none. `fps-cap-round1` and `usb-native-loop-round1` need D-POCO in another role, and every other
queued round uses D-HU. Run this round alone.

Estimated time: 6 min pre-flight, 9 min R1 and R2 (one chain), 10 min R5, 6 min R3, 8 min R4, 4 min end of
round, about 4 min thermal waits. About 47 min after the build. The build is about 25 min.

**R5 and R4 are the point of the round.** R1 is the positive control. R2 and R3 are false-positive guards
with fewer cycles than round 1. Six runs is the ceiling; there is no R6.

## 1. Build and baseline

One arm. It is #1110's head `fcf2c032` plus a clean merge of #1109's head `30310b93`, both on base
`d2d0a6bf`. No history was rewritten. The branch goes to `fork` after this brief is approved, so fetch it
from `fork`. There is no baseline build: settings are the controls.

| Arm | Ref | Commit | Tree |
|---|---|---|---|
| C (candidate) | `fork` `review/pr-1109-1110` | `fc3469823c693a708ec03ead6022dc26d57fbf98` | `ecdeaaa3d300ebcecc3a8efa03abde76fd94fe49` |

The dev host ran the JVM suite at `fc346982`: 3262 tests, 0 failures, 0 skipped. Do not run the unit tests
on the rig.

**get_apk.sh**

```bash
OUT=rig-data/rounds/pr-1109-1110-wifi-direct-round2; mkdir -p $OUT
WT=$(ls -d worktrees/* 2>/dev/null | head -1)        # any existing worktree; else create one with wt-new.sh
git -C $WT fetch fork review/pr-1109-1110
git -C $WT checkout --detach fc3469823c693a708ec03ead6022dc26d57fbf98
git -C $WT rev-parse 'HEAD^{tree}'                    # MUST print ecdeaaa3d300ebcecc3a8efa03abde76fd94fe49
$RIG_TOOLKIT/rig_thermal.sh wait 70
HUR_DIR=$WT MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh   # copy the APK out of apks/ to $OUT/apk-C.apk at once
md5sum $OUT/apk-C.apk                                 # record in Setup notes
```

Identity, after the install:

- `send ACTION_QUERY_STATE`: the `data=` line MUST carry commit `fc346982`.
- `unzip -p $OUT/live.apk 'classes*.dex' | strings | grep -c 'banded refusal remembered'` MUST be at least 1.
  That string is #1109's. `grep -c WifiStackRecovery` on the same output MUST be at least 1 (#1110).

Install with `adb -s $HU install -r -d $OUT/apk-C.apk` on D-HU only. D-POCO keeps the APK it has.

## 2. What this is and why

**#1110** detects a wedged WiFi Direct stack and offers a WiFi restart. It calls the stack wedged when,
inside 60 s, WiFi is on, at least 2 `WIFI_P2P_STATE_DISABLED` broadcasts arrived and at least 3 Native
group creates failed. Then it logs a WARN line, raises `WIFI_DIRECT_STACK_STUCK` (pref
`connection-issue-p2p-stuck`) and, with the opt-in on, restarts WiFi through root or local ADB. The banner
tap does the restart, or opens the WiFi panel when no route exists.

Round 1 (`58828f81`) passed both false-positive runs on D-HU: a force-stop produced 0 disabled broadcasts
and 0 failed creates, and a WiFi toggle produced 2 or 3 broadcasts and 0 failed creates. No restart ran,
so the restart path and the banner were never exercised. The new head `fcf2c032` changes the inputs: it
clears both counts when WiFi comes back on, uses the churn verdict, counts a CommManager session, and
changes the banner ranking. So R2 and R3 run again, with fewer cycles.

The review (`.claude/sdlc/pr-1109-1110/review.md`) left one #1110 finding this rig can measure. **Finding 1:
the ADB route can raise "Allow USB debugging?" on the road.** The settings switch checks only that
`127.0.0.1:5555` accepts a TCP connection. The first real `AdbManager.exec` happens at the first restart,
and an unpaired app key then raises the system dialog. R5 taps the banner on the ADB route and records
whether the dialog appears. Finding 2 (a stale root grant) needs `su`, and D-HU has none.

**#1109** remembers a unit that refuses every banded WiFi Direct group. After the banded ladder fails four
times under band AUTO and the no-band create succeeds, it stores the build fingerprint in
`wifi-direct-banded-refused-on`. While that matches `Build.FINGERPRINT`, the next bring-up skips the banded
request and makes the no-band group at once. Changing the band setting removes the key.

D-HU's banded 5 GHz create succeeds (round 1, R2: `5GHz createGroup SUCCESS!`), so D-HU can never write the
record by itself. R4 seeds it. **Review finding 2 for #1109:** on AOSP the no-band group often lands on a
2.4 GHz channel, and a 2.4 GHz link turns on the narrow-band video cap. So R4 records which frequency the
no-band group lands on, beside the station's frequency, and whether `[RES_CAP]` reports `linkCapped=`
other than `none`. R1 and R2 check that #1109 writes nothing on a unit whose banded create works.

## 3. What is different about this round

- **After a force-stop the P2P group survives, and the relaunch reads it instead of creating one.** Round
  1 measured this in every rep: `a group named ... is already up from before this bring-up; reading it
  instead of tearing it down.` That path makes no create and does not clear connection-issue records. R5
  uses this on purpose: a stuck record seeded with the app stopped survives the relaunch, so the banner
  shows. R4 needs the opposite (a real create), so R4 starts with no group up.
- **D-HU has no `su`.** `adbd` runs as uid 0 and `su -c` fails. The app asks root through libsu, which
  finds no `su` binary, so no line on D-HU reads `via ROOT` and no superuser prompt can appear. This round
  writes `wifi-restart-root-granted` = `false` everywhere.
- **Port 5555 was closed on D-HU at round 1 pre-flight.** R5 taps the banner first with the port as found
  (R5a), then opens it with `adb tcpip 5555` and taps again (R5b). `adb tcpip` restarts `adbd`, which ends
  any running logcat stream, so R5 stops its capture before the command and starts a new one after. The
  port stays open for R3 and R4. The end of the round closes it with `adb usb`.
- **The "Allow USB debugging?" dialog is the measurement in R5b.** `ro.adb.secure` was 1 in round 1. If the
  dialog appears, take the screenshot first, then tap Allow by its dumped target. **Do not tick "Always
  allow".** That keeps the app's key out of D-HU's `adb_keys`, so a later round sees the same first-use state.
- **Settings-screen rows may be below the fold, and this round does not scroll.** R5c (the opt-in switch)
  and R4b (the band setting) each take one `uiautomator dump`. If the row is not in that dump, inside the
  list's visible bounds, the step is UNTESTABLE (`row_offscreen`). Do not swipe, search or type.
- **The banner is drawn on `onResume()` only** (`rig-quirks/topics/projection.md`). A seeded stamp needs
  `connection-issue-dismissed-at` deleted too.
- **`shared_prefs/` must be owned by the app's uid** (`rig-quirks/units/D-HU.md`). R2, R3 and R4 read keys
  the app writes. Pre-flight records the `stat` and fixes the owner if it is wrong.
- **A marker cannot go to a stopped app.** A broadcast starts its process. So no marker goes between a
  force-stop and the next launch.
- **On `wifi-connection-mode` 3 the launch arms the stack.** No run sends `ACTION_START_WIRELESS_SCAN` after a
  launch, except the one capped fallback in R5b.
- **The phone leaves the link by `svc`.** D-POCO's WiFi can come back by itself (`rig-quirks/units/D-POCO.md`).
  R5 holds it down with `ph_hold` while the banner is on screen.
- **Grade projection on `Throughput over` and `Media Sink Setup Request: `**, never on `TransportStarted`,
  which is not a log string. `Service Discovery Response` prints only in VERBOSE message dumps, so this
  round uses the INFO line `Service Discovery Request: ` instead.
- **Read `rig-quirks/topics/tooling.md`, `topics/wifi.md`, `topics/lifecycle.md`, `topics/projection.md`,
  `units/D-HU.md` and `units/D-POCO.md` first.** The thermal limits are the table in `tooling.md`.

## 4. Settings keys

Back up D-HU's `settings.xml` at the start (`$OUT/settings-backup.xml`) and diff it at the end. State the
delta in Setup notes. Leave the audio keys as they are: they are a deliberate worst case. No run grades
audio.

Record, do not change: `native-poke-bt-macs`, `native-poke-all-paired`, `native-driver-selection-mode`,
`wifi-direct-stable-identity`, `connection-modes`, `narrow-band-profile-cap` (absent means true),
`video-profile-starvation-cap`. **If `video-profile-starvation-cap` is true, a `linkCapped=` value in R4
does not come from the band.** Say so beside the R4 grade, and do not clear it.

**Base** (every run). `log-level` 2 (INFO) carries every app line in section 6.

```xml
<int name="wifi-connection-mode" value="3" />
<int name="native-ap-transport" value="0" />
<int name="log-level" value="2" />
<int name="onboarding-version" value="2" />
<int name="wifi-direct-band" value="0" />
<int name="wifi-5ghz-channel" value="0" />
<string name="static-bssid">0</string>
<string name="static-p2p-bssid">0</string>
<boolean name="wifi-restart-root-granted" value="false" />
```

Also delete, before every run: `connection-issue-p2p-stuck`, `connection-issue-dismissed-at`,
`wifi-direct-banded-refused-on`.

| Key | R1, R2, R3 | R5 | R4 |
|---|---|---|---|
| `auto-restart-wifi-p2p-stuck` (boolean) | `true` | `false` | `true` |
| `advanced-settings-active` (boolean) | as found | `true` | `false` |
| `connection-issue-p2p-stuck` (long) | deleted | D-HU epoch ms, seeded | deleted |
| `wifi-direct-banded-refused-on` (string) | deleted | deleted | D-HU's `ro.build.fingerprint`, seeded |

## 5. Helpers

Run every adb call in sequence, never two at once against one unit. Write the results file after every
run.

**lib.sh**

```bash
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
HU=27870808938846; PH=4f4027e9; MOTO=ZY22GC3BM4
OUT=rig-data/rounds/pr-1109-1110-wifi-direct-round2; mkdir -p $OUT
export LC_ALL=C
send() { a=$1; shift; adb -s $HU shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; }
pnow() { adb -s $PH shell "date '+%m-%d %H:%M:%S.000'" | tr -d '\r'; }
step() { send ACTION_LOG_MARKER --es text "$1" >/dev/null; sleep 0.3; echo "$1 $(pnow)" >> $OUT/phone-marks.txt; }
launch() { adb -s $HU shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity >/dev/null; }
settings_open() { adb -s $HU shell am start -n $PKG/com.andrerinas.openheadunit.main.SettingsActivity --ei extra_destination 0 >/dev/null; }
# huexit: only while the app runs (an exit on a stopped app starts the service)
huexit() { adb -s $HU shell am start -a android.intent.action.VIEW -d "headunit://exit" >/dev/null; sleep 5
  adb -s $HU shell am force-stop $PKG; sleep 1; }
hustop() { adb -s $HU shell am force-stop $PKG; sleep 1; }
focus() { adb -s $HU shell dumpsys window | grep -m1 mCurrentFocus | tr -d '\r'; }
shot() { adb -s $HU exec-out screencap -p > $OUT/$1.png; echo "shot $1" >> $OUT/shots.log; }

# host thermal (rig-quirks/topics/tooling.md). th_end also kills the watcher's child sleep (round 1 fix).
th_gate() { $RIG_TOOLKIT/rig_thermal.sh wait 75 || $RIG_TOOLKIT/rig_thermal.sh wait 75 || { echo HOST_HOT; return 1; }
  $RIG_TOOLKIT/rig_thermal.sh watch $OUT/thermal-$RUN.log & THPID=$!; }
th_end() { pkill -P $THPID 2>/dev/null; kill $THPID 2>/dev/null; wait $THPID 2>/dev/null; true; }

# radios
hu_wifi()    { adb -s $HU shell dumpsys wifi | grep -m1 "Wi-Fi is" | tr -d '\r'; }
hu_wifi_on() { local t; for t in 1 2 3; do adb -s $HU shell svc wifi enable; sleep 5
  hu_wifi | grep -q enabled && { echo "enable_tries=$t"; return 0; }; done; echo "enable_tries=FAIL"; return 1; }
hu_wifi_cycle() { adb -s $HU shell svc wifi disable; sleep 3; hu_wifi_on; }
hu_sta() { adb -s $HU shell dumpsys wifi | grep -m1 -oE 'Frequency: ?-?[0-9]+' | tr -d '\r'; }
hu_group() { adb -s $HU shell dumpsys wifip2p | grep -a -m1 -oE 'groupFormed: (true|false)' | tr -d '\r'; }
ph_bt()  { adb -s $PH shell svc bluetooth $1; sleep 4; adb -s $PH shell dumpsys bluetooth_manager | grep -m1 -E "^ *state:"; }
ph_off() { adb -s $PH shell svc wifi disable; sleep 0.3; ph_bt disable; adb -s $PH shell dumpsys wifi | grep -m1 "Wi-Fi is"; }
ph_on()  { ph_hold_stop; adb -s $PH shell svc wifi enable; sleep 0.3; ph_bt enable; adb -s $PH shell dumpsys wifi | grep -m1 "Wi-Fi is"; }
ph_hold() { ( while :; do adb -s $PH shell svc wifi disable >/dev/null 2>&1; sleep 3; done ) & HOLDPID=$!; }
ph_hold_stop() { [ -n "$HOLDPID" ] && kill $HOLDPID 2>/dev/null; HOLDPID=; }

# captures: started before the launch
cap_on() { adb -s $HU logcat -c; adb -s $HU logcat -G 16M; stdbuf -oL adb -s $HU logcat -v threadtime > $OUT/$1-hu.txt & HUPID=$!
  stdbuf -oL adb -s $PH logcat -v threadtime -T 1 -b main,system,crash,events > $OUT/$1-phone.txt & PHPID=$!; sleep 2; }
cap_off() { sleep 2; kill $HUPID $PHPID 2>/dev/null; wait $HUPID $PHPID 2>/dev/null; }
waitfor() { local n=0; while [ $n -lt $3 ]; do grep -aqF -- "$2" "$1" && { echo $n; return 0; }; sleep 5; n=$((n+5)); done; echo TIMEOUT; return 1; }
# waitafter FILE MARKER STRING CAP: STRING on a line after the first MARKER line
waitafter() { local n=0; while [ $n -lt $4 ]; do
  awk -v m="$2" -v s="$3" 'f&&index($0,s){x=1;exit} index($0,m){f=1} END{exit !x}' "$1" && { echo $n; return 0; }
  sleep 5; n=$((n+5)); done; echo TIMEOUT; return 1; }

# head unit greps. win CAP FROM TO: the lines from the first FROM line to the first TO line after it
win()  { awk -v a="$2" -v b="$3" 'index($0,a){f=1} f{print} f&&index($0,b){exit}' $OUT/$1-hu.txt; }
wc_()  { win "$1" "$2" "$3" | grep -acF -- "$4"; }                       # wc_ CAP FROM TO STRING
ln1()  { grep -anF -- "$2" $OUT/$1-hu.txt | head -1 | cut -d: -f1; }     # ln1 CAP STRING
lnA()  { awk -v f="$2" -v s="$3" 'NR>f && index($0,s){print NR; exit}' $OUT/$1-hu.txt; }   # lnA CAP FROMLINE STRING
tms()  { sed -n "${2}p" $OUT/$1-hu.txt | awk '{split($2,t,":"); printf "%.3f\n", t[1]*3600+t[2]*60+t[3]}'; }
gap()  { [ -n "$2" ] && [ -n "$3" ] && printf "%.1f\n" "$(echo "$(tms $1 $3) - $(tms $1 $2)" | bc)" || echo none; }
gfreq() { win "$1" "$2" "$3" | grep -a 'onGroupInfoAvailable: SSID:' | tail -1 | grep -aoE 'Freq: -?[0-9]+ MHz' | grep -oE -- '-?[0-9]+' || echo none; }
gmode() { win "$1" "$2" "$3" | grep -a 'WifiDirectManager: Native AA WiFi Direct: ' | tail -1 | sed 's/.*Native AA WiFi Direct: //; s/, SSID=.*//'; }
capd()  { win "$1" "$2" "$3" | grep -a '\[RES_CAP\]' | tail -1 | grep -aoE 'linkCapped=[^ ]+' || echo linkCapped=absent; }

# phone greps over one window, on the phone's clock from phone-marks.txt
pt()   { grep -aF "$1 " $OUT/phone-marks.txt | tail -1 | cut -d' ' -f2-; }
pwin() { awk -v a="$(pt "$2")" -v b="$(pt "$3")" 'substr($0,1,18)>=a && substr($0,1,18)<=b' $OUT/$1-phone.txt; }
pc()   { pwin "$1" "$2" "$3" | grep -acF -- "$4"; }                       # pc CAP FROMMARK TOMARK STRING

# settings on D-HU, app stopped. Args: "set TYPE KEY VALUE" or "del x KEY". adbd is root; no su, no run-as.
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
  } > $OUT/w_kv.sh
  adb -s $HU push $OUT/w_kv.sh /data/local/tmp/w_kv.sh >/dev/null
  adb -s $HU shell "sh /data/local/tmp/w_kv.sh"
}
base() { hu_kv "set int wifi-connection-mode 3" "set int native-ap-transport 0" "set int log-level 2" \
  "set int onboarding-version 2" "set int wifi-direct-band 0" "set int wifi-5ghz-channel 0" \
  "set string static-bssid 0" "set string static-p2p-bssid 0" "set boolean wifi-restart-root-granted false" \
  "del x connection-issue-p2p-stuck" "del x connection-issue-dismissed-at" "del x wifi-direct-banded-refused-on"; }
# pv TAG KEY: one key's value from a saved copy; "absent" if the key is not there
pv() { local f=$OUT/settings-$1.xml; local v
  v=$(grep -aoE "name=\"$2\" value=\"[^\"]*\"" $f | sed 's/.*value="//; s/"$//')
  [ -z "$v" ] && v=$(grep -aoE "<string name=\"$2\">[^<]*</string>" $f | sed 's/<[^>]*>//g')
  echo "${v:-absent}"; }
prefread() { adb -s $HU shell cat /data/data/$PKG/shared_prefs/settings.xml > $OUT/settings-$1.xml
  echo "stuck=$(pv $1 connection-issue-p2p-stuck) banded=$(pv $1 wifi-direct-banded-refused-on) band=$(pv $1 wifi-direct-band) optin=$(pv $1 auto-restart-wifi-p2p-stuck)"; }

# APK identity: pull and hash on the host
apk_check() { adb -s $HU pull $(adb -s $HU shell pm path $PKG | cut -d: -f2 | tr -d '\r') $OUT/live.apk >/dev/null 2>&1
  local m=$(md5sum $OUT/live.apk | cut -d' ' -f1); echo "$(date +%T) live=$m want=$WANT_MD5" | tee -a $OUT/apk-check.log
  [ "$m" = "$WANT_MD5" ] || { echo APK_MISMATCH | tee -a $OUT/apk-check.log; return 1; }; }

# candidate counts over a window, one line
W1="WifiDirectManager: WiFi Direct looks wedged - WiFi is on, the stack reported disabled"
cand() { echo "wedged=$(wc_ $1 "$2" "$3" "$W1") restart=$(wc_ $1 "$2" "$3" 'WifiStackRecovery: restarting WiFi via') restarted=$(wc_ $1 "$2" "$3" 'WifiStackRecovery: WiFi restarted via') cannot=$(wc_ $1 "$2" "$3" 'WifiStackRecovery: cannot restart WiFi (') held=$(wc_ $1 "$2" "$3" 'WifiDirectManager: automatic WiFi restart held back') recorded=$(wc_ $1 "$2" "$3" 'recorded that this unit refuses banded groups on this firmware') skip=$(wc_ $1 "$2" "$3" 'so the band request is skipped') forget=$(wc_ $1 "$2" "$3" 'the remembered no-band route failed too') banner=$(wc_ $1 "$2" "$3" 'MainActivity: showing the connection issue banner for WIFI_DIRECT_STACK_STUCK') edges=$(wc_ $1 "$2" "$3" 'WifiDirectManager: WIFI_P2P_STATE_CHANGED_ACTION state=1') fails=$(wc_ $1 "$2" "$3" 'createGroup failed (') creates=$(wc_ $1 "$2" "$3" 'Attempting createGroup for Native AA (Attempt ') adopt=$(wc_ $1 "$2" "$3" 'before this bring-up; reading it instead of tearing it down.') groups=$(wc_ $1 "$2" "$3" 'createGroup SUCCESS')"; }
sess() { echo "msr=$(wc_ $1 "$2" "$3" 'Media Sink Setup Request: ') sdr=$(wc_ $1 "$2" "$3" 'Service Discovery Request: ') thr=$(wc_ $1 "$2" "$3" 'Throughput over') ssl=$(wc_ $1 "$2" "$3" 'SSL handshake complete')"; }
```

**tapnode.py** (resolves one target in a `uiautomator dump`; prints `OK x y` or `VOID <reason>`; never guesses)

```python
# usage: python3 -I tapnode.py UI.xml RESOURCE_ID ROW_TEXT CHECK
#   RESOURCE_ID: full id, or a bare name for the app's own ids
#   ROW_TEXT: "-" or the exact text of the settingName in the same settings row
#   CHECK: "-" or attr=value (for example checked=false, enabled=true) or text~substring
import sys, re, xml.etree.ElementTree as ET
f, rid, row, check = sys.argv[1:5]
P = 'com.andrerinas.headunitrevived:id/'
full = rid if ':' in rid else P + rid
root = ET.parse(f).getroot()
parent = {c: p for p in root.iter() for c in p}
def box(n):
    a = list(map(int, re.findall(r'\d+', n.get('bounds', '')))); return a if len(a) == 4 else None
def row_of(n):
    p = parent.get(n)
    while p is not None:
        names = [x for x in p.iter('node') if x.get('resource-id') == P + 'settingName']
        if names: return names[0].get('text') if len(names) == 1 else None
        p = parent.get(p)
    return None
def scroller(n):
    p = parent.get(n)
    while p is not None:
        if p.get('scrollable') == 'true': return box(p)
        p = parent.get(p)
    return None
hits = [n for n in root.iter('node') if n.get('resource-id') == full and (row == '-' or row_of(n) == row)]
if len(hits) != 1: print('VOID matches=%d' % len(hits)); sys.exit(1)
n = hits[0]; b = box(n)
if not b: print('VOID no_bounds'); sys.exit(1)
x, y = (b[0] + b[2]) // 2, (b[1] + b[3]) // 2
s = scroller(n)
if s and not (s[0] <= x <= s[2] and s[1] <= y <= s[3]): print('VOID row_offscreen'); sys.exit(1)
if check != '-':
    if '~' in check:
        k, v = check.split('~', 1)
        if v not in (n.get(k) or ''): print('VOID %s=%r' % (k, n.get(k))); sys.exit(1)
    else:
        k, v = check.split('=', 1)
        if n.get(k) != v: print('VOID %s=%r' % (k, n.get(k))); sys.exit(1)
print('OK %d %d' % (x, y))
```

**tap.sh** (one dumped-target tap; the per-run budget is enforced in `$OUT/taps-$RUN.n`)

```bash
# tap LABEL RESOURCE_ID ROW_TEXT CHECK   (ROW_TEXT and CHECK may be "-")
tap() {
  local n=$(cat $OUT/taps-$RUN.n 2>/dev/null || echo 0)
  [ $n -ge 4 ] && { echo "$RUN $1 TAP_BUDGET" | tee -a $OUT/taps.log; return 1; }
  adb -s $HU shell uiautomator dump /sdcard/ui.xml >/dev/null; adb -s $HU pull /sdcard/ui.xml $OUT/$RUN-$1-ui.xml >/dev/null
  local r=$(python3 -I $OUT/tapnode.py $OUT/$RUN-$1-ui.xml "$2" "$3" "$4")
  echo "$RUN $1 $r" | tee -a $OUT/taps.log
  case "$r" in OK*) set -- $r; adb -s $HU shell input tap $2 $3; echo $((n+1)) > $OUT/taps-$RUN.n; return 0;; esac
  return 1
}
```

## 6. The deciding lines

Each app string was checked with `git grep -F` on `app/src/main` at `fc346982`. All print at INFO or
above. Lines with a run-time value are listed by their fixed part.

**Head unit capture** (`$OUT/<CAP>-hu.txt`, window named per run):

| Grep | Level | Meaning |
|---|---|---|
| `WifiDirectManager: WiFi Direct looks wedged - WiFi is on, the stack reported disabled` | WARN | #1110 called the stack wedged |
| `WifiStackRecovery: restarting WiFi via` | WARN | a restart starts; `... via ADB (banner tap).` or `... via ADB (WiFi Direct wedged).` |
| `WifiStackRecovery: WiFi restarted via` | INFO | the restart command returned 0 |
| `WifiStackRecovery: WiFi restart via` | WARN | the restart command failed (`... failed (code=`) |
| `WifiStackRecovery: cannot restart WiFi (` | WARN | no route: no root and no local ADB |
| `WifiDirectManager: automatic WiFi restart held back (cooldown, hourly limit or live session).` | INFO | opt-in on, a limit or a session stopped a restart |
| `MainActivity: showing the connection issue banner for` | INFO | the banner is drawn; R5 wants `... for WIFI_DIRECT_STACK_STUCK` |
| `MainActivity: could not open the WiFi panel:` | WARN | the panel fallback failed |
| `AdbManager: Command '` with `executed successfully via Self-ADB` | INFO | the local ADB route ran the command |
| `AdbManager: Self-ADB execution failed for '` | ERROR | the local ADB route failed |
| `AdbManager: read timed out after` | WARN | the ADB read was cut off |
| `recorded that this unit refuses banded groups on this firmware` | INFO | #1109 wrote the record (MUST be 0 on D-HU) |
| `so the band request is skipped` | INFO | #1109 skipped the banded request |
| `standard (banded refusal remembered)` | INFO | the group mode on the skip route, in the `Native AA WiFi Direct:` line |
| `the remembered no-band route failed too; forgetting it` | WARN | the skip route failed and the key was removed |
| `WifiDirectManager: Native AA WiFi Direct: ` | INFO | band, frequency and group mode, once per group |
| `WifiDirectManager: onGroupInfoAvailable: SSID:` | INFO | carries `Freq: <n> MHz (<band>)` |
| `WifiDirectManager: Requesting Native AA P2P group on 5GHz band.` | INFO | a banded request went out |
| `5GHz createGroup SUCCESS!` | INFO | the banded create succeeded |
| `WifiDirectManager: Standard createGroup SUCCESS!` | INFO | a no-band create succeeded |
| `createGroup SUCCESS` | INFO | any create succeeded |
| `createGroup failed (` | WARN | a failed Native create (#1110's failure input) |
| `Attempting createGroup for Native AA (Attempt ` | INFO | a create attempt (the reach of #1109's record path) |
| `before this bring-up; reading it instead of tearing it down.` | INFO | the relaunch read a surviving group, no create |
| `WifiDirectManager: WIFI_P2P_STATE_CHANGED_ACTION state=1` | INFO | a P2P disabled broadcast (lower bound: the line has a log quota) |
| `WifiDirectManager: P2P enabled, auto-starting Native AA quiet host` | INFO | the stack came back and the app re-armed |
| `[RES_CAP]` with `linkCapped=` | INFO | the video cap; `linkCapped=none` means no cap |
| `Service Discovery Request: ` | INFO | the phone started the AAP session |
| `Media Sink Setup Request: ` | INFO | the phone set up a media sink |
| `Throughput over` | INFO | video decoded; every 5 s in a live session |
| `SSL handshake complete` | INFO | a session formed (INFO form) |
| `AutomationReceiver: ` / `AutomationMarker: ` | INFO / WARN | a verb or a marker landed; a missing line voids the step |

`ConnectionIssues.raise` logs nothing at INFO. So "no `WIFI_DIRECT_STACK_STUCK` raise" is graded on the
wedge line, the banner line and the pref value together.

**On screen** (grade from the screenshot or the dump, not from logcat):

| Text | Where |
|---|---|
| `WiFi Direct on this unit is stuck and only a WiFi restart frees it. Tap to restart WiFi.` | banner text, `:id/connection_issue_text` |
| `Restarting WiFi…` | toast after the banner tap on a route |
| `Could not restart WiFi. Switch it off and on here.` | toast when the restart failed |
| `Root granted. WiFi will restart by itself if WiFi Direct gets stuck.` | opt-in toast, root route |
| `Local ADB found. WiFi will restart by itself if WiFi Direct gets stuck.` | opt-in toast, ADB route |
| `No root or local ADB on this unit, so the banner will offer a one-tap WiFi restart instead.` | opt-in toast, no route |
| `Restart WiFi when WiFi Direct gets stuck` | opt-in row, `:id/settingName` |
| `WiFi Direct band` with options `Auto`, `5 GHz only` | band row, `:id/btnOption1`, `:id/btnOption2` |

**Phone capture** (`$OUT/<CAP>-phone.txt`, phone clock window from `phone-marks.txt`). Record the Gearhead
version (`adb -s $PH shell dumpsys package com.google.android.projection.gearhead | grep versionName`). A 0
can mean a renamed string: then mark the condition "string not seen", not FAIL.

| Grep | Meaning |
|---|---|
| `Creating rfcomm socket for device:` | the phone ran the Bluetooth handshake (record the MAC) |
| `WPP on TCP connected to the WiFi network` | the phone reached a stored TCP endpoint |
| `Critical error` | Android Auto ended a session on a protocol fault |

## 7. Runs

### P. Pre-flight (read only, except where named)

1. `sed -n '/^## Pick a tool/,/^## Rig and devices/p' $RIG_TOOLKIT/TOOLS.md`. Record which tools you used.
2. `$RIG_TOOLKIT/rig_preflight.sh D_HU:wifi,bt D_POCO:wifi,bt`.
3. D-MOTO: `adb -s $MOTO shell svc bluetooth disable`, and read back `state: OFF`.
4. Save `tapnode.py` to `$OUT/tapnode.py`. Source `lib.sh` and `tap.sh`.
5. D-HU facts. Record each output:

   **p_facts.sh**

   ```bash
   adb -s $HU shell id -u                                   # expect 0
   adb -s $HU shell 'which su; echo su_rc=$?'               # expect su_rc=1
   adb -s $HU shell getprop ro.adb.secure
   adb -s $HU shell "netstat -ltn 2>/dev/null | grep -c ':5555 '"   # PORT5555_AT_P
   adb -s $HU shell getprop ro.build.fingerprint | tr -d '\r' > $OUT/fingerprint.txt; cat $OUT/fingerprint.txt
   adb -s $HU shell wm size
   adb -s $HU shell dumpsys wifi | grep -iE "mWifiInfo" | head -2   # station SSID and frequency
   hu_group                                                  # groupFormed at rest
   adb -s $HU shell stat -c '%U:%G %a' /data/data/$PKG/shared_prefs
   ```

   If `shared_prefs` is not owned by the app's uid, `chown` it to the app's uid:gid, then read it back.
   If the fingerprint contains `|` or `&`, stop and say so in Setup notes: `hu_kv` cannot seed it.
6. D-POCO: `adb -s $PH shell dumpsys wifip2p | grep isGroupOwner` MUST be false (else `svc wifi disable`,
   `svc wifi enable`). `adb -s $PH shell dumpsys window | grep mCurrentFocus`: at home (else
   `wm dismiss-keyguard`, then `input keyevent KEYCODE_HOME`).
7. D-HU: `adb -s $HU shell svc power stayon true`.
8. `hustop`. Back up `settings.xml` to `$OUT/settings-backup.xml`. Install C (section 1),
   `WANT_MD5=<md5 of apk-C>`, `apk_check`, `launch`, `sleep 5`, `send ACTION_QUERY_STATE` (commit `fc346982`),
   `huexit`. Then `hu_group`: if it prints `groupFormed: true`, run `hu_wifi_cycle` and read it again. R1
   needs no group up. Read the keys back with `prefread P0`: an install can wipe `settings.xml`.

### R1 + R2. Positive control, then 3 force-stops in a session (one chain)

Phone capture: yes. One capture `R12`. Opt-in on. R1 is the first bring-up. R2 is three reps `R2-1` to
`R2-3`. Each rep's relaunched session is the next rep's "in a session". R2-3 leaves the session up for R5.

**r12.sh**

```bash
RUN=R12 th_gate || exit 1; apk_check || exit 1
hustop; base; hu_kv "set boolean auto-restart-wifi-p2p-stuck true"; prefread R1-pre
echo "R1 sta=$(hu_sta) group_at_start=$(hu_group)" >> $OUT/r12.log
ph_off
cap_on R12
launch; sleep 1; step R1-start
waitafter $OUT/R12-hu.txt "AutomationMarker: R1-start" "createGroup SUCCESS" 60 >> $OUT/r12.log
sleep 5; ph_on; step R1-phone
t=$(waitafter $OUT/R12-hu.txt "AutomationMarker: R1-phone" "Throughput over" 180); echo "R1 session_wait=$t" >> $OUT/r12.log
sleep 15; echo "R1 $(prefread R1-end)" >> $OUT/r12.log; step R1-end
[ "$t" = TIMEOUT ] && { cap_off; th_end; echo "R1 NO_SESSION: stop, R2 and R5 do not run"; exit 1; }
for n in 1 2 3; do
  ID=R2-$n
  sleep 15
  step $ID-kill
  adb -s $HU shell am force-stop $PKG
  sleep 3; echo "$ID afterkill $(hu_group)" >> $OUT/r12.log
  sleep 5
  launch; sleep 1; step $ID-relaunch
  t=$(waitafter $OUT/R12-hu.txt "AutomationMarker: $ID-relaunch" "Throughput over" 120); echo "$ID session_wait=$t" >> $OUT/r12.log
  sleep 20
  echo "$ID $(prefread $ID)" >> $OUT/r12.log
  step $ID-end
  [ "$t" = TIMEOUT ] && break
done
cap_off; th_end
```

If the chain broke on a `TIMEOUT`, run `hu_wifi_cycle`, then `huexit`, and record it. R5 then starts from
a fresh `launch` with the phone on, and its first step waits for `Throughput over` (180 s cap).

**r12_grade.sh**

```bash
echo "R1 $(cand R12 'AutomationMarker: R1-start' 'AutomationMarker: R1-end') $(sess R12 'AutomationMarker: R1-start' 'AutomationMarker: R1-end') banded_req=$(wc_ R12 'AutomationMarker: R1-start' 'AutomationMarker: R1-end' 'WifiDirectManager: Requesting Native AA P2P group on 5GHz band.') banded_ok=$(wc_ R12 'AutomationMarker: R1-start' 'AutomationMarker: R1-end' '5GHz createGroup SUCCESS!') freq=$(gfreq R12 'AutomationMarker: R1-start' 'AutomationMarker: R1-end') mode=[$(gmode R12 'AutomationMarker: R1-start' 'AutomationMarker: R1-end')] $(capd R12 'AutomationMarker: R1-start' 'AutomationMarker: R1-end') rf=$(pc R12 R1-start R1-end 'Creating rfcomm socket for device:') crit=$(pc R12 R1-start R1-end 'Critical error')"
grep -aE '^R1 (sta|stuck)' $OUT/r12.log
for n in 1 2 3; do ID=R2-$n; grep -aqF "AutomationMarker: $ID-kill" $OUT/R12-hu.txt || continue
  Lr=$(ln1 R12 "AutomationMarker: $ID-relaunch"); Lt=$([ -n "$Lr" ] && lnA R12 $Lr "Throughput over")
  echo "$ID $(cand R12 "AutomationMarker: $ID-kill" "AutomationMarker: $ID-end") sess_s=$(gap R12 "$Lr" "$Lt") $(sess R12 "AutomationMarker: $ID-relaunch" "AutomationMarker: $ID-end") $(grep -aE "^$ID stuck=" $OUT/r12.log | cut -d' ' -f2-) $(grep -aE "^$ID afterkill" $OUT/r12.log | cut -d' ' -f3-) rf=$(pc R12 $ID-relaunch $ID-end 'Creating rfcomm socket for device:') tcp=$(pc R12 $ID-relaunch $ID-end 'WPP on TCP connected to the WiFi network') crit=$(pc R12 $ID-relaunch $ID-end 'Critical error')"
done
```

**R1 verdict** (the positive control).

- PASS: `banded_req` >= 1, `banded_ok` >= 1, `recorded=0`, `skip=0`, `wedged=0`, `restart=0`, `cannot=0`,
  `banded=absent` in the `R1 stuck=` line, `mode` starts with `5GHz (` and ends with `5GHz requested`,
  `msr` >= 1, `thr` >= 1, `rf` >= 1, `crit=0`.
- FAIL: `recorded` >= 1, `skip` >= 1, `wedged` >= 1, `restart` >= 1, or `banded` is not `absent`.
- INCONCLUSIVE: `banded_ok=0` with no candidate line (the banded create did not succeed this time; then
  #1109's record path was reachable: quote `fails`, `creates` and `mode`).
- Record: `freq`, `sta`, and `linkCapped`.

**R2 verdict.** Grade each rep. Crit is counted from the relaunch marker (round 1 fix): the phone's
session dies at the kill.

- Void rep: its `-kill` or `-relaunch` marker is missing, or the rep before it had no session.
- Rep PASS: `wedged=0`, `restart=0`, `cannot=0`, `held=0`, `recorded=0`, `skip=0`, `stuck=absent`,
  `banded=absent`, `adopt` + `groups` >= 1, `sess_s` not `none` and <= 120, `thr` >= 1, `crit=0`, and `rf` +
  `tcp` >= 1.
- Rep FAIL: `wedged` >= 1, `restart` >= 1, `cannot` >= 1, `recorded` >= 1, or `stuck` or `banded` not `absent`.
- R2 PASS: 3 graded reps, all pass. FAIL: any graded rep fails. INCONCLUSIVE: fewer than 2 graded reps.

What a PASS would look like if neither change did anything: the same. So report the reach per rep:
`edges`, `fails` and `creates`. If `adopt=1` and `creates=0` in a rep, #1109's record path was not
reachable in that rep, and only #1110's false-wedge half was tested.

### R5. The stuck banner on both routes, then the opt-in toast (THE POINT, part 1)

Phone capture: yes. Opt-in off. Starts from R2-3's live session. Taps: at most 4 (banner R5a, banner R5b,
Allow if the dialog shows, opt-in switch).

**R5a. Port as found.** Skip R5a if `PORT5555_AT_P` was 1, and record "R5a skipped, port already open".

**r5a.sh**

```bash
RUN=R5 th_gate || exit 1; apk_check || exit 1
ph_off; ph_hold                               # the phone stays off while the banner is up
sleep 10
hustop                                        # the group survives the force-stop
echo "R5 afterkill $(hu_group)" >> $OUT/r5.log
NOWMS=$(adb -s $HU shell date +%s | tr -d '\r')000
base; hu_kv "set boolean auto-restart-wifi-p2p-stuck false" "set boolean advanced-settings-active true" \
  "set long connection-issue-p2p-stuck $NOWMS"
prefread R5-pre >> $OUT/r5.log
cap_on R5a
launch; sleep 1; step R5a-launch
waitafter $OUT/R5a-hu.txt "AutomationMarker: R5a-launch" "showing the connection issue banner for WIFI_DIRECT_STACK_STUCK" 20 >> $OUT/r5.log
sleep 2; shot R5a-banner; step R5a-tap
tap banner_a connection_issue_banner - - || echo "R5a VOID" >> $OUT/r5.log
sleep 4; echo "R5a focus=$(focus)" >> $OUT/r5.log; shot R5a-after
adb -s $HU shell input keyevent 4; sleep 2
step R5a-end; cap_off
```

- Void: `afterkill` read `groupFormed: false` (no group survived, so the launch makes one and clears the
  record), the banner line never came, or the tap printed `VOID`. Re-run R5a once from a new session
  (`ph_on`, `launch`, wait for `Throughput over`, then `r5a.sh`). A second void makes R5a INCONCLUSIVE.
- PASS (`PORT5555_AT_P=0`): `banner` >= 1; `R5a focus=` names a WiFi panel or WiFi settings activity
  (`Panel` or `WifiSettings` or `com.android.settings`); `restart=0`; `cannot=0`; no
  `could not open the WiFi panel` line. The banner text in `R5a-banner.png` reads as in section 6.
- FAIL: `restart` >= 1, or the focus stays on `MainActivity` with no panel line.

**R5b. Open the port, tap again.**

**r5b.sh**

```bash
adb -s $HU tcpip 5555
for i in $(seq 1 20); do sleep 3; [ "$(adb -s $HU shell id -u 2>/dev/null | tr -d '\r')" = 0 ] && break; done
echo "R5b port=$(adb -s $HU shell "netstat -ltn 2>/dev/null | grep -c ':5555 '" | tr -d '\r') tries=$i" | tee -a $OUT/r5.log
cap_on R5b
adb -s $HU shell input keyevent 3; sleep 2; launch; sleep 1; step R5b-resume   # onResume redraws the banner
waitafter $OUT/R5b-hu.txt "AutomationMarker: R5b-resume" "showing the connection issue banner for WIFI_DIRECT_STACK_STUCK" 20 >> $OUT/r5.log
step R5b-tap
tap banner_b connection_issue_banner - - || echo "R5b VOID" >> $OUT/r5.log
sleep 1; shot R5b-1s; sleep 3; shot R5b-4s
F=$(focus); echo "R5b focus=$F" >> $OUT/r5.log
DIALOG=0; echo "$F" | grep -qi UsbDebugging && DIALOG=1; echo "R5b dialog=$DIALOG" >> $OUT/r5.log
if [ $DIALOG = 1 ]; then
  step R5b-dialog; shot R5b-dialog
  tap allow android:id/button1 - - || echo "R5b ALLOW_VOID" >> $OUT/r5.log    # never tick "Always allow"
fi
waitafter $OUT/R5b-hu.txt "AutomationMarker: R5b-tap" "WifiStackRecovery: WiFi restarted via" 60 >> $OUT/r5.log
sleep 30; echo "R5b wifi_back=$(hu_wifi | grep -c enabled)" >> $OUT/r5.log
t=$(waitafter $OUT/R5b-hu.txt "WifiStackRecovery: WiFi restarted via" "createGroup SUCCESS" 60)
if [ "$t" = TIMEOUT ]; then
  hu_wifi | grep -q enabled || hu_wifi_on >> $OUT/r5.log
  step R5b-scan; send ACTION_START_WIRELESS_SCAN; echo "R5b scan_needed=1" >> $OUT/r5.log
  waitafter $OUT/R5b-hu.txt "AutomationMarker: R5b-scan" "createGroup SUCCESS" 60 >> $OUT/r5.log
else echo "R5b scan_needed=0" >> $OUT/r5.log; fi
ph_on; step R5b-phone
waitafter $OUT/R5b-hu.txt "AutomationMarker: R5b-phone" "Throughput over" 180 >> $OUT/r5.log
sleep 10; step R5b-end
```

If `R5b port=0`: write `ROUTE=NONE`. The tap then opens the panel again; grade R5b like R5a and say so.
Else write `ROUTE=ADB`.

**R5c. The opt-in toast** (same capture, session live).

**r5c.sh**

```bash
step R5c-start
settings_open; sleep 4
tap optin settingSwitch 'Restart WiFi when WiFi Direct gets stuck' checked=false \
  || echo "R5c row_offscreen_or_void" >> $OUT/r5.log
sleep 1; shot R5c-1s; sleep 1.5; shot R5c-2s
step R5c-end; cap_off
huexit                                        # leaves the switch unsaved
prefread R5-end >> $OUT/r5.log                # optin MUST still read false
ph_off; th_end
```

**r5_grade.sh**

```bash
cat $OUT/r5.log; grep -a '^R5' $OUT/taps.log
[ -f $OUT/R5a-hu.txt ] && echo "R5a $(cand R5a 'AutomationMarker: R5a-launch' 'AutomationMarker: R5a-end') adopt_at_launch=$(wc_ R5a 'AutomationMarker: R5a-launch' 'AutomationMarker: R5a-end' 'before this bring-up; reading it instead of tearing it down.') panel_fail=$(grep -acF 'MainActivity: could not open the WiFi panel:' $OUT/R5a-hu.txt)"
L=$(ln1 R5b 'AutomationMarker: R5b-tap'); Lx=$([ -n "$L" ] && lnA R5b $L 'WifiStackRecovery: restarting WiFi via'); Lok=$([ -n "$L" ] && lnA R5b $L 'WifiStackRecovery: WiFi restarted via'); Lg=$([ -n "$Lok" ] && lnA R5b $Lok 'createGroup SUCCESS'); Lp=$(ln1 R5b 'AutomationMarker: R5b-phone')
echo "R5b restart_line=[$([ -n "$Lx" ] && sed -n "${Lx}p" $OUT/R5b-hu.txt | cut -c19-150)] to_ok_s=$(gap R5b "$Lx" "$Lok") group_s=$(gap R5b "$Lok" "$Lg") rearm=$(wc_ R5b 'AutomationMarker: R5b-tap' 'AutomationMarker: R5b-phone' 'WifiDirectManager: P2P enabled, auto-starting Native AA quiet host') adb_ok=$(grep -acF 'executed successfully via Self-ADB' $OUT/R5b-hu.txt) adb_fail=$(grep -acF "AdbManager: Self-ADB execution failed for '" $OUT/R5b-hu.txt) adb_timeout=$(grep -acF 'AdbManager: read timed out after' $OUT/R5b-hu.txt) rfail=$(grep -acF 'WifiStackRecovery: WiFi restart via' $OUT/R5b-hu.txt) cannot=$(grep -acF 'WifiStackRecovery: cannot restart WiFi (' $OUT/R5b-hu.txt) sess_s=$(gap R5b "$Lp" "$([ -n "$Lp" ] && lnA R5b $Lp 'Throughput over')") $(sess R5b 'AutomationMarker: R5b-phone' 'AutomationMarker: R5b-end') rf=$(pc R5b R5b-phone R5b-end 'Creating rfcomm socket for device:') crit=$(pc R5b R5b-tap R5b-end 'Critical error')"
```

**R5b verdict, `ROUTE=ADB`.**

- **Finding 1 (the dialog).** Report `dialog` as the result, with `R5b-dialog.png`. `dialog=1` means review
  finding 1 is CONFIRMED on D-HU. `dialog=0` with `adb_ok` >= 1 means NOT REPRODUCED (the app's key was
  already trusted, or the unit does not ask). `dialog=0` with `adb_ok=0` and `adb_fail` or `adb_timeout`
  >= 1 means the route failed silently: quote the line.
- **Restart.** PASS: `restart_line` reads `... via ADB (banner tap).`, `adb_ok` >= 1, `rfail=0`,
  `wifi_back=1`, and `to_ok_s` is not `none`. If `dialog=1`, `to_ok_s` includes the time to answer it: say so.
  FAIL: no restart line, or `rfail` >= 1, or `adb_fail` >= 1 with `adb_ok=0`.
- **Group and session back.** PASS: `group_s` not `none` and <= 60 with `scan_needed=0`, then `sess_s` <=
  180, `thr` >= 1, `rf` >= 1, `crit=0`. With `scan_needed=1` and a session, grade "back only after the WiFi
  button", which is a finding on review point 3 (the re-arm after a restart), not a PASS. Quote `rearm`.
- `wifi_back=0` is its own finding (D-HU's `svc wifi enable` quirk, `rig-quirks/topics/wifi.md`). Record
  it; the host decides.

**R5c verdict.**

- UNTESTABLE: `row_offscreen_or_void` (the row was not on the first screen; no scroll by rule).
- Else record the toast text from `R5c-1s.png` or `R5c-2s.png`, word for word, and match it to one of the
  three opt-in toasts in section 6. Expected on `ROUTE=ADB`: `Local ADB found. ...`. If `dialog=1` in R5b and
  the toast reads `Local ADB found. ...`, write: "the switch promises a route that needs a dialog".
- `optin` in `R5-end` MUST read `false` (the switch was never saved). If it reads `true`, say so.

### R3. WiFi toggles with the port open (false-wedge guard)

Phone capture: yes. Opt-in on, and the ADB route is open, so a false wedge would now restart WiFi for real.
The phone stays off until the toggles end. One rep, three cycles in one capture.

| Cycle | `OFF` (s) | `ON` (s) |
|---|---|---|
| c1 (slow) | 8 | 12 |
| c2 (fast) | 3 | 5 |
| c3 (fast) | 3 | 5 |

**r3.sh**

```bash
RUN=R3 th_gate || exit 1; apk_check || exit 1
hustop; base; hu_kv "set boolean auto-restart-wifi-p2p-stuck true"; prefread R3-pre
ph_off
cap_on R3
launch; sleep 1; step R3-start
waitfor $OUT/R3-hu.txt "createGroup SUCCESS" 60 >/dev/null; sleep 5
step R3-toggle
for c in "8 12" "3 5" "3 5"; do set -- $c; adb -s $HU shell svc wifi disable; sleep $1; hu_wifi_on >> $OUT/r3.log; sleep $2; done
step R3-back
sleep 75                                      # the 60 s window plus a margin, no session
echo "R3 $(prefread R3-quiet)" >> $OUT/r3.log
ph_on; step R3-phone
waitafter $OUT/R3-hu.txt "AutomationMarker: R3-phone" "Throughput over" 180 >> $OUT/r3.log
sleep 10; step R3-end; cap_off; huexit; th_end
```

**r3_grade.sh**

```bash
Lp=$(ln1 R3 'AutomationMarker: R3-phone')
echo "R3 all:[$(cand R3 'AutomationMarker: R3-start' 'AutomationMarker: R3-end')] toggle:[$(cand R3 'AutomationMarker: R3-toggle' 'AutomationMarker: R3-phone')] rearm=$(wc_ R3 'AutomationMarker: R3-toggle' 'AutomationMarker: R3-phone' 'WifiDirectManager: P2P enabled, auto-starting Native AA quiet host') nosess_ssl=$(wc_ R3 'AutomationMarker: R3-start' 'AutomationMarker: R3-back' 'SSL handshake complete') adb_ok=$(grep -acF 'executed successfully via Self-ADB' $OUT/R3-hu.txt) sess_s=$(gap R3 "$Lp" "$([ -n "$Lp" ] && lnA R3 $Lp 'Throughput over')") $(sess R3 'AutomationMarker: R3-phone' 'AutomationMarker: R3-end') rf=$(pc R3 R3-toggle R3-end 'Creating rfcomm socket for device:') crit=$(pc R3 R3-start R3-end 'Critical error')"
cat $OUT/r3.log
```

- Void: `nosess_ssl` >= 1 (a session formed while the phone was meant to be off; re-run once with `ph_hold`
  after `ph_off`), or any `enable_tries=FAIL` (record it, nudge with `hu_wifi_on`, re-run once).
- PASS: in `all`, `wedged=0`, `restart=0`, `cannot=0`, `held=0`, `banner=0`; `stuck=absent` in the `R3 stuck=`
  line; `adb_ok=0`; `rearm` >= 3; `sess_s` <= 180; `thr` >= 1; `crit=0`; `rf` >= 1.
- FAIL: `wedged` >= 1, `restart` >= 1, `adb_ok` >= 1, or `stuck` not `absent`. Quote the first wedge line.

What a PASS would look like if the policy did nothing: the same. So report `edges` and `fails` from the
`toggle` window. The concern is reachable only where `edges` >= 2 and `fails` >= 1.

### R4. #1109's skip route, synthetic, then the clear (THE POINT, part 2)

Phone capture: yes. Opt-in on. The seeded key makes the next create skip the banded request. Taps: at most
4 (two options and two Saves).

**r4.sh**

```bash
RUN=R4 th_gate || exit 1; apk_check || exit 1
hustop
G=$(hu_group); echo "R4 group_at_start=$G" >> $OUT/r4.log
echo "$G" | grep -q true && { hu_wifi_cycle >> $OUT/r4.log; echo "R4 group_after_cycle=$(hu_group)" >> $OUT/r4.log; }
echo "R4 sta=$(hu_sta)" >> $OUT/r4.log
FP=$(cat $OUT/fingerprint.txt)
base; hu_kv "set boolean auto-restart-wifi-p2p-stuck true" "set boolean advanced-settings-active false" \
  "set string wifi-direct-banded-refused-on $FP"
echo "R4 $(prefread R4-pre)" >> $OUT/r4.log        # banded MUST equal the fingerprint
ph_off
cap_on R4
launch; sleep 1; step R4-start
waitafter $OUT/R4-hu.txt "AutomationMarker: R4-start" "createGroup SUCCESS" 60 >> $OUT/r4.log
sleep 5; ph_on; step R4-phone
waitafter $OUT/R4-hu.txt "AutomationMarker: R4-phone" "Throughput over" 180 >> $OUT/r4.log
sleep 15; echo "R4 sta_live=$(hu_sta)" >> $OUT/r4.log
echo "R4 $(prefread R4-live)" >> $OUT/r4.log       # banded MUST still equal the fingerprint
step R4-session-end
```

**r4b.sh** (the clear, same capture; session live)

```bash
step R4b-start
settings_open; sleep 4
tap band5 btnOption2 'WiFi Direct band' text~5 || echo "R4b row_offscreen_or_void" >> $OUT/r4.log
if ! grep -aq 'R4b row_offscreen_or_void' $OUT/r4.log; then
  sleep 1; tap save1 save_button_widget - enabled=true || echo "R4b save1_void" >> $OUT/r4.log
  sleep 3; echo "R4b $(prefread R4b-after5)" >> $OUT/r4.log     # banded MUST read absent, band 1
  tap bandauto btnOption1 'WiFi Direct band' text~Auto || echo "R4b auto_void" >> $OUT/r4.log
  sleep 1; tap save2 save_button_widget - enabled=true || echo "R4b save2_void" >> $OUT/r4.log
  sleep 3; echo "R4b $(prefread R4b-afterAuto)" >> $OUT/r4.log  # banded absent, band 0
fi
step R4-end; cap_off; huexit; ph_off; th_end
```

**r4_grade.sh**

```bash
cat $OUT/r4.log; grep -a '^R4' $OUT/taps.log
echo "R4 $(cand R4 'AutomationMarker: R4-start' 'AutomationMarker: R4-session-end') banded_req=$(wc_ R4 'AutomationMarker: R4-start' 'AutomationMarker: R4-session-end' 'WifiDirectManager: Requesting Native AA P2P group on 5GHz band.') std_ok=$(wc_ R4 'AutomationMarker: R4-start' 'AutomationMarker: R4-session-end' 'WifiDirectManager: Standard createGroup SUCCESS!') remembered=$(wc_ R4 'AutomationMarker: R4-start' 'AutomationMarker: R4-session-end' 'standard (banded refusal remembered)') freq=$(gfreq R4 'AutomationMarker: R4-start' 'AutomationMarker: R4-session-end') mode=[$(gmode R4 'AutomationMarker: R4-start' 'AutomationMarker: R4-session-end')] $(capd R4 'AutomationMarker: R4-start' 'AutomationMarker: R4-session-end') $(sess R4 'AutomationMarker: R4-phone' 'AutomationMarker: R4-session-end') rf=$(pc R4 R4-phone R4-session-end 'Creating rfcomm socket for device:') crit=$(pc R4 R4-start R4-session-end 'Critical error')"
grep -a 'so the band request is skipped' $OUT/R4-hu.txt | head -1 | cut -c1-200
```

**R4 verdict (skip route).**

- Void: `banded` in `R4-pre` does not equal the fingerprint (the seed failed), or `adopt` >= 1 (a surviving
  group was read, so no create ran). Re-run once.
- PASS: `skip` >= 1, `remembered` >= 1, `banded_req=0`, `std_ok` >= 1, `mode` ends with
  `standard (banded refusal remembered)`, `forget=0`, `recorded=0`, `banded` in `R4-live` still equals the
  fingerprint, `msr` >= 1, `thr` >= 1, `rf` >= 1, `crit=0`.
- FAIL: `skip=0` with `banded_req` >= 1 (the record was ignored), or `forget` >= 1, or no session.

**R4 key measurement (review finding 2).** Report these four values on one line, whatever the verdict:
`freq` (the no-band group's frequency), `sta` and `sta_live` (the station's frequency), `linkCapped`, and
`video-profile-starvation-cap` from Setup notes. Compare `freq` with R1's `freq`.

- `freq` <= 4000 means the skip route put D-HU on 2.4 GHz where the banded route gave 5 GHz. Then finding 2
  is CONFIRMED on this unit. Say whether `linkCapped` is other than `none`.
- `freq` > 4000 means the no-band group also landed on 5 GHz here. Finding 2 is NOT REPRODUCED on D-HU.

**R4b verdict (the clear).**

- UNTESTABLE: `row_offscreen_or_void` (the band row was not on the first screen; no scroll by rule).
- PASS: `banded=absent` and `band=1` in `R4b-after5`, then `banded=absent` and `band=0` in `R4b-afterAuto`.
- FAIL: `banded` still equals the fingerprint after `save1` with `band=1` (the setter did not remove it).
- INCONCLUSIVE: `band` did not change in `R4b-after5` (the Save did not reach the file). Quote the
  `shared_prefs` `stat` line.

### End of round

`huexit` if the app runs, then `hustop`. If `PORT5555_AT_P` was 0, run `adb -s $HU usb`, wait until
`adb -s $HU shell id -u` prints 0 (20 tries, 3 s apart), then check the port count reads 0. D-HU:
`hu_wifi_on`, then `hu_group`. Restore `$OUT/settings-backup.xml` as root (`rig-quirks/units/D-HU.md`, last
entry), `chown` and `chmod 660` it, and diff it: MUST be empty. D-POCO: `ph_on`, `isGroupOwner` false.
D-MOTO: put its Bluetooth back to its state at pre-flight, and read it back. D-HU: `svc power stayon false`.

## 8. Do not re-run

- Round 1's R3 (killing `wpa_supplicant` or the WiFi HAL as root). Six attempts gave no wedge signature on
  D-HU, and no other lever is known. The policy arithmetic is in `P2pStackWedgePolicyTest`.
- The root route and review finding 2 for #1110 (a stale root grant). D-HU has no `su`, and no rig unit
  has a superuser app.
- #1109's record path from a real exhausted ladder. D-HU's banded create succeeds, so it cannot happen
  here. `NativeGroupBandPolicyTest` covers `recordsBandedRefusal` and `skipsBandedRequest`.
- Five force-stop reps and four toggle reps. Round 1 measured them on `58828f81`; this round keeps three of
  each against the new head.

## 9. Report back

1. R5b: `dialog` (0 or 1) with the screenshot, `restart_line`, `adb_ok`, `to_ok_s`, `wifi_back`, `group_s`
   and `scan_needed`. R5c: the toast text word for word. R5a: the focus line.
2. R4: `skip`, `remembered`, `banded_req`, then `freq`, `sta`, `linkCapped` and R1's `freq` on one line.
   R4b: `banded` and `band` after each Save.
3. R1, R2 and R3: the verdicts, and per rep `wedged`, `restart`, `recorded`, `edges`, `fails`, `creates`
   and `adopt`.

```decisive-strings
WifiDirectManager: WiFi Direct looks wedged - WiFi is on, the stack reported disabled
WifiStackRecovery: restarting WiFi via
WifiStackRecovery: WiFi restarted via
WifiStackRecovery: WiFi restart via
WifiStackRecovery: cannot restart WiFi (
WifiDirectManager: automatic WiFi restart held back (cooldown, hourly limit or live session).
MainActivity: showing the connection issue banner for
WIFI_DIRECT_STACK_STUCK
MainActivity: could not open the WiFi panel:
AdbManager: Command '
executed successfully via Self-ADB
AdbManager: Self-ADB execution failed for '
AdbManager: read timed out after
recorded that this unit refuses banded groups on this firmware
so the band request is skipped
standard (banded refusal remembered)
the remembered no-band route failed too; forgetting it
Native AA WiFi Direct: 
WifiDirectManager: onGroupInfoAvailable: SSID:
Freq: 
WifiDirectManager: Requesting Native AA P2P group on 
 createGroup SUCCESS!
WifiDirectManager: Standard createGroup SUCCESS!
createGroup failed (
Attempting createGroup for Native AA (Attempt 
before this bring-up; reading it instead of tearing it down.
WifiDirectManager: WIFI_P2P_STATE_CHANGED_ACTION state=
WifiDirectManager: P2P enabled, auto-starting Native AA quiet host
[RES_CAP] resolutionId=
linkCapped=
Service Discovery Request: 
Media Sink Setup Request: 
Throughput over 
SSL handshake complete
AutomationReceiver: 
AutomationMarker: 
WiFi Direct on this unit is stuck and only a WiFi restart frees it. Tap to restart WiFi.
Restarting WiFi…
Could not restart WiFi. Switch it off and on here.
Root granted. WiFi will restart by itself if WiFi Direct gets stuck.
Local ADB found. WiFi will restart by itself if WiFi Direct gets stuck.
No root or local ADB on this unit, so the banner will offer a one-tap WiFi restart instead.
Restart WiFi when WiFi Direct gets stuck
WiFi Direct band
5 GHz only
connection-issue-p2p-stuck
connection-issue-dismissed-at
auto-restart-wifi-p2p-stuck
wifi-restart-root-granted
wifi-direct-banded-refused-on
wifi-direct-band
wifi-5ghz-channel
advanced-settings-active
narrow-band-profile-cap
connection_issue_banner
connection_issue_text
settingName
settingSwitch
btnOption1
btnOption2
save_button_widget
Creating rfcomm socket for device:
WPP on TCP connected to the WiFi network
Critical error
```

The last three lines are Gearhead's, not app strings: no grep of `app/src` can confirm them. Three log lines
are built from two source literals, so the list gives the fixed fragment that is one literal: the wedge
line (its run-time counts follow), `before this bring-up; reading it instead of tearing it down.` (the log
reads `... is already up from before this bring-up; ...`), and the `5GHz createGroup SUCCESS!` line (the
band label is a run-time value). Two more are built at run time: `WifiDirectManager: Native AA WiFi Direct: `
(the class prefix and `Native AA WiFi Direct: ` are separate literals) and
`WifiDirectManager: Requesting Native AA P2P group on 5GHz band.` (the band label is a value). Both match
the log as written in section 6. `5GHz requested` in the R1 mode check is the constant
`NATIVE_GROUP_MODE_5GHZ_REQUESTED`.
