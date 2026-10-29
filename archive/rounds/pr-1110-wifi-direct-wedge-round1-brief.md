# pr-1110-wifi-direct-wedge, round 1 brief

Published name: `pr-1110-wifi-direct-wedge-round1-brief.md`. Results go in
`pr-1110-wifi-direct-wedge-round1-results.md`. Evidence: create the release
`rig-evidence-pr-1110-wifi-direct-wedge` and upload `pr-1110-wifi-direct-wedge-round1-captures.zip` to it.

Units: D-HU (head unit) and D-POCO (phone). D-MOTO keeps its Bluetooth off for the whole round and runs
nothing. D-SAM and D-HP take no part. If D-SAM is plugged in, keep its app stopped.

Hand steps: at most one, in Stage B. If D-HU shows the system "Allow USB debugging?" dialog for the
app's own local ADB connection, accept it (HS1). Injected taps: R4 only, at most 3 per run (budget 5).

Queue line: `pr-1110-wifi-direct-wedge-round1-brief.md` (C `58828f81`, contributor PR head, one arm, the
setting is the control; units D-HU and D-POCO, D-MOTO Bluetooth off; R1 is the point).

Pairing: none. `automation-screen-disconnect-round2` uses D-POCO, D-MOTO and D-SAM, and it holds D-HU's
Bluetooth off before every session. This round needs D-HU's Bluetooth on and one of those phones. Every
other queued round uses D-HU. So run this round alone.

Estimated time: 10 min pre-flight, 25 min build, 35 min R1, 25 min R2, 10 min Stage B route, up to 40 min
R3, 10 min R4, 10 min thermal waits. About 2 h 45 min. R3 and R4 stop early if the wedge does not reproduce.

**R1 is the point of the round.** R2 is the second false-positive check. R3 is a positive control and may
be INCONCLUSIVE. R4 runs only if R3 reproduced the wedge. R5 is a count over all captures.

## 1. Build and baseline

One arm, the contributor's PR head as is. It is one commit on base `d2d0a6bf`, 17 commits behind `main`
`28af450d`, and it merges clean. No history was rewritten. There is no baseline build: the opt-in setting
is the control (off in R4, on everywhere else).

| Arm | Ref | Commit | Tree |
|---|---|---|---|
| C (candidate) | `origin` `pull/1110/head` | `58828f812c87e66713548f7ae11c6b4f65d83050` | `fd9f2168f12b91e9662ed52bc8926baa732c8696` |

The fork remote does not carry this commit. Fetch it from `origin` (upstream).

The dev host ran the JVM suite at `58828f81`: 3252 (0 failures, 0 skipped). Do not run the unit tests on the rig.

**get_apk.sh**

```bash
OUT=rig-data/rounds/pr-1110-wifi-direct-wedge-round1; mkdir -p $OUT
WT=$(ls -d worktrees/* 2>/dev/null | head -1)        # any existing worktree; else create one with wt-new.sh
git -C $WT fetch origin pull/1110/head
git -C $WT checkout --detach 58828f812c87e66713548f7ae11c6b4f65d83050
git -C $WT rev-parse 'HEAD^{tree}'                    # MUST print fd9f2168f12b91e9662ed52bc8926baa732c8696
$RIG_TOOLKIT/rig_thermal.sh wait 70
HUR_DIR=$WT MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh   # copy the APK out of apks/ to $OUT/apk-C.apk at once
md5sum $OUT/apk-C.apk                                 # record in Setup notes
```

Identity, after the install:

- `send ACTION_QUERY_STATE`: the `data=` line MUST carry commit `58828f81`.
- `unzip -p $OUT/live.apk 'classes*.dex' | strings | grep -c WifiStackRecovery` MUST be at least 1.

Install with `adb -s $HU install -r -d $OUT/apk-C.apk` on D-HU only. D-POCO keeps the APK it has; it is the
phone in this round.

## 2. What this is and why

A contributor reproduced a wedged WiFi Direct stack on a MediaTek Android 10 GSI phone. They
force-stopped the app in the middle of a session. After that, the framework logged `WifiP2pNative: Failed
to setup P2p iface in supplicant` and `HalDevMgr: removeIfaceInternal: iface(name)=p2p0` in a loop.
`dumpsys wifip2p` stayed in `P2pDisabledState` with WiFi on. Every group create failed until WiFi was
switched off and on.

The PR adds three things:

- **Detection.** `P2pStackWedgePolicy` calls the stack wedged when all three are true inside 60 s: WiFi
  is on, at least 2 `WIFI_P2P_STATE_DISABLED` broadcasts arrived, and at least 3 Native group creates
  failed. The failures are counted in `onQuietGroupFailed` and in both `standardCreateGroup` failure
  paths. The check runs only when a create fails. It reports at most once a minute.
- **Record and banner.** A wedge raises `ConnectionIssue.WIFI_DIRECT_STACK_STUCK` (pref
  `connection-issue-p2p-stuck`). It outranks every other banner record. `noteGroupFormed()` clears it.
  The banner tap restarts WiFi through root or local ADB, or opens the WiFi panel.
- **Automatic restart.** With `auto-restart-wifi-p2p-stuck` on (default off), and no Native session
  connected, the app runs `svc wifi disable && sleep 3 && svc wifi enable` through root or local ADB.
  It waits 2 min between restarts and does at most 3 an hour.

The risk on the rig is a false positive. D-HU has a known force-stop shape: an orphaned P2P group, and
a create that can answer `BUSY` for up to 120 s. A false positive on a real unit switches WiFi off for
every app on it. So R1 and R2 ask whether D-HU's normal faults reach the policy's threshold.

## 3. What is different about this round

- **D-HU has no `su`, so the root route cannot work here.** `adbd` on D-HU runs as uid 0, and `su -c`
  fails with `su: inaccessible or not found` (recorded in four earlier results files). The app asks
  root through libsu, which needs a `su` binary. So `Shell.isAppGrantedRoot()` can never be true on D-HU,
  and no line on D-HU will read `via ROOT`. Write `wifi-restart-root-granted` = `true` anyway. Then a
  restart with no other route tries root once. No prompt can appear, because no superuser app exists.
- **The only restart route on D-HU is local ADB.** The app uses it when `127.0.0.1:5555` accepts a TCP
  connection (`AdbManager.isAdbPortOpen`). Pre-flight records whether D-HU listens on 5555.
  - Stage A (R1, R2) runs with the port as found. If it is closed, a false positive logs
    `WifiStackRecovery: cannot restart WiFi (` and nothing restarts. R1 and R2 grade the detection line,
    which comes first, so the route does not change their verdict.
  - Stage B opens the port with `adb tcpip 5555` if it was closed, so R3 and R4 can see a real restart.
    The end of the round puts it back with `adb usb`.
  - The first local ADB connection from the app can raise the system "Allow USB debugging?" dialog on
    D-HU (when `ro.adb.secure` is 1). The app's command then waits until somebody answers. That is HS1.
- **R3 may be INCONCLUSIVE, and that is not a failure.** Nobody has made D-HU's framework print the
  contributor's signature. R3 tries three ways to break the supplicant as root. If none gives the
  signature, R3 is INCONCLUSIVE and R4 does not run.
- **The "held back" check has a narrow timing window.** The wedge line prints at most once a minute, and
  the `held back` line prints only after a new wedge line. So a second wedge counts only if the app
  judges it between 60 s and 120 s after the first wedge line. R3 sends the second induction 65 s after
  the first wedge line.
- **A marker cannot go to a stopped app.** A broadcast to a stopped app starts its process. So R1 stamps
  `-kill` before the force-stop and `-relaunch` after the launch, and nothing between them.
- **On `wifi-connection-mode` 3 the launch arms the stack.** No run sends `ACTION_START_WIRELESS_SCAN`.
- **The phone leaves the link by `svc`.** D-POCO's WiFi can come back by itself a few seconds after
  `svc wifi disable` (`rig-quirks/units/D-POCO.md`). A no-session window that shows a session is void.
  Re-run it with the hold-down loop `ph_hold` (section 5).
- **`svc wifi enable` on D-HU does not always bring WiFi back** (`rig-quirks/topics/wifi.md`). The
  helper `hu_wifi_on` reads the state back and tries up to 3 times. In R3 and R4 the app's own restart
  uses the same command. If D-HU's WiFi is still off 30 s after `WifiStackRecovery: WiFi restarted via`,
  record it as a finding (`wifi_back=0`). Do not nudge it until the run has recorded that.
- **Gearhead can dial a cached head unit.** D-POCO is bonded to D-HU and to D-SAM. Pre-flight records the
  bonded list and the MAC in the phone's `Creating rfcomm socket for device:` lines.
- **Read `rig-quirks/topics/tooling.md`, `rig-quirks/topics/wifi.md`, `rig-quirks/topics/lifecycle.md`,
  `rig-quirks/units/D-HU.md` and `rig-quirks/units/D-POCO.md` first.** The thermal limits are the table
  in `tooling.md`.

## 4. Settings keys

Back up D-HU's `settings.xml` at the start (`$OUT/settings-backup.xml`) and diff it against the end. State
the delta in Setup notes. `stat` D-HU's `shared_prefs/` and record owner and mode. If the owner is not the
app's uid, `chown` it to the app's uid:gid: R1 reads `connection-issue-p2p-stuck`, which the app writes.

Leave the audio keys as they are: they are a deliberate worst case. Leave `video-profile-starvation-cap`
as it is and record its value. No run grades audio or the picture.

Record, do not change: `native-poke-bt-macs`, `native-poke-all-paired`, `native-driver-selection-mode`,
`native-preferred-device-mac`, `wifi-direct-stable-identity`, `video-profile-starvation-cap`.

**Base** (every run). `allow-external-configuration` is not needed: the marker verb is not gated on this
build.

```xml
<int name="wifi-connection-mode" value="3" />
<int name="native-ap-transport" value="0" />
<int name="log-level" value="2" />
<int name="onboarding-version" value="2" />
<string name="static-bssid">0</string>
<string name="static-p2p-bssid">0</string>
```

Also delete `connection-issue-p2p-stuck` and `connection-issue-dismissed-at` before every run.

**ON** (R1, R2, R3):

```xml
<boolean name="auto-restart-wifi-p2p-stuck" value="true" />
<boolean name="wifi-restart-root-granted" value="true" />
```

**OFF** (R4):

```xml
<boolean name="auto-restart-wifi-p2p-stuck" value="false" />
<boolean name="wifi-restart-root-granted" value="false" />
```

Log level 2 (INFO) carries every app line in section 6. The wedge line is WARN.

## 5. Helpers

Run every adb call in sequence, never two at once against one unit. Write the results file after every
run.

**lib.sh**

```bash
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
HU=27870808938846; PH=4f4027e9; MOTO=ZY22GC3BM4
OUT=rig-data/rounds/pr-1110-wifi-direct-wedge-round1; mkdir -p $OUT
export LC_ALL=C
send() { a=$1; shift; adb -s $HU shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; }
pnow() { adb -s $PH shell "date '+%m-%d %H:%M:%S.000'" | tr -d '\r'; }
step() { send ACTION_LOG_MARKER --es text "$1" >/dev/null; sleep 0.3; echo "$1 $(pnow)" >> $OUT/phone-marks.txt; }
launch() { adb -s $HU shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity >/dev/null; }
# huexit: only while the app runs (an exit on a stopped app starts the service)
huexit() { adb -s $HU shell am start -a android.intent.action.VIEW -d "headunit://exit" >/dev/null; sleep 5
  adb -s $HU shell am force-stop $PKG; sleep 1; }
hustop() { adb -s $HU shell am force-stop $PKG; sleep 1; }

# host thermal (rig-quirks/topics/tooling.md)
th_gate() { $RIG_TOOLKIT/rig_thermal.sh wait 75 || $RIG_TOOLKIT/rig_thermal.sh wait 75 || { echo HOST_HOT; return 1; }
  $RIG_TOOLKIT/rig_thermal.sh watch $OUT/thermal-$RUN.log & THPID=$!; }
th_end() { kill $THPID 2>/dev/null; wait $THPID 2>/dev/null; }

# radios
hu_wifi()    { adb -s $HU shell dumpsys wifi | grep -m1 "Wi-Fi is" | tr -d '\r'; }
hu_wifi_on() { local t; for t in 1 2 3; do adb -s $HU shell svc wifi enable; sleep 5
  hu_wifi | grep -q enabled && { echo "enable_tries=$t"; return 0; }; done; echo "enable_tries=FAIL"; return 1; }
hu_wifi_cycle() { adb -s $HU shell svc wifi disable; sleep 3; hu_wifi_on; }
ph_bt()  { adb -s $PH shell svc bluetooth $1; sleep 4; adb -s $PH shell dumpsys bluetooth_manager | grep -m1 -E "^ *state:"; }
ph_off() { adb -s $PH shell svc wifi disable; sleep 0.3; ph_bt disable; adb -s $PH shell dumpsys wifi | grep -m1 "Wi-Fi is"; }
ph_on()  { ph_hold_stop; adb -s $PH shell svc wifi enable; sleep 0.3; ph_bt enable; adb -s $PH shell dumpsys wifi | grep -m1 "Wi-Fi is"; }
# only after a void no-session window: keep D-POCO's WiFi down every 3 s
ph_hold() { ( while :; do adb -s $PH shell svc wifi disable >/dev/null 2>&1; sleep 3; done ) & HOLDPID=$!; }
ph_hold_stop() { [ -n "$HOLDPID" ] && kill $HOLDPID 2>/dev/null; HOLDPID=; }

# D-HU P2P and supplicant state, one line per call
p2pdump() { echo "$1 $(adb -s $HU shell dumpsys wifip2p | grep -aE 'curState|groupFormed|isGroupOwner' | head -4 | tr -s ' \r\n' ' ') disabledState=$(adb -s $HU shell dumpsys wifip2p | grep -ac P2pDisabledState) wifi=[$(hu_wifi)]" >> $OUT/p2p-states.txt; }
prefread() { adb -s $HU shell cat /data/data/$PKG/shared_prefs/settings.xml > $OUT/settings-$1.xml
  v=$(grep -aoE 'connection-issue-p2p-stuck" value="[0-9]+' $OUT/settings-$1.xml | grep -oE '[0-9]+$'); echo "${v:-0}"; }

# captures: started before the launch
cap_on() { adb -s $HU logcat -c; adb -s $HU logcat -G 16M; stdbuf -oL adb -s $HU logcat -v threadtime > $OUT/$1-hu.txt & HUPID=$!
  PHPID=; if [ "$2" = phone ]; then stdbuf -oL adb -s $PH logcat -v threadtime -T 1 -b main,system,crash,events > $OUT/$1-phone.txt & PHPID=$!; fi; sleep 2; }
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

# phone greps over one window, on the phone's clock from phone-marks.txt
pt()   { grep -aF "$1 " $OUT/phone-marks.txt | tail -1 | cut -d' ' -f2-; }
pwin() { awk -v a="$(pt "$2")" -v b="$(pt "$3")" 'substr($0,1,18)>=a && substr($0,1,18)<=b' $OUT/$1-phone.txt; }
pc()   { pwin "$1" "$2" "$3" | grep -acF -- "$4"; }                       # pc CAP FROMMARK TOMARK STRING

# settings on D-HU, app stopped. Args: "set TYPE KEY VALUE" or "del x KEY". adbd is root; no su.
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
  "set int onboarding-version 2" "set string static-bssid 0" "set string static-p2p-bssid 0" \
  "del x connection-issue-p2p-stuck" "del x connection-issue-dismissed-at"; }
opt_on()  { base; hu_kv "set boolean auto-restart-wifi-p2p-stuck true" "set boolean wifi-restart-root-granted true"; }
opt_off() { base; hu_kv "set boolean auto-restart-wifi-p2p-stuck false" "set boolean wifi-restart-root-granted false"; }
hu_read() { adb -s $HU shell cat /data/data/$PKG/shared_prefs/settings.xml > $OUT/settings-$1.xml
  grep -aoE '(wifi-connection-mode|native-ap-transport|log-level|auto-restart-wifi-p2p-stuck|wifi-restart-root-granted|connection-issue-p2p-stuck|connection-issue-dismissed-at)"[^/]*' $OUT/settings-$1.xml; }

# APK identity: pull and hash on the host (a piped md5 over adb is not reliable)
apk_check() { adb -s $HU pull $(adb -s $HU shell pm path $PKG | cut -d: -f2 | tr -d '\r') $OUT/live.apk >/dev/null 2>&1
  local m=$(md5sum $OUT/live.apk | cut -d' ' -f1); echo "$(date +%T) live=$m want=$WANT_MD5" | tee -a $OUT/apk-check.log
  [ "$m" = "$WANT_MD5" ] || { echo APK_MISMATCH | tee -a $OUT/apk-check.log; return 1; }; }

# candidate lines over a window, one line of counts
W1="WifiDirectManager: WiFi Direct looks wedged - WiFi is on, the stack reported disabled"
cand() { echo "wedged=$(wc_ $1 "$2" "$3" "$W1") restart=$(wc_ $1 "$2" "$3" 'WifiStackRecovery: restarting WiFi via') restarted=$(wc_ $1 "$2" "$3" 'WifiStackRecovery: WiFi restarted via') rfail=$(wc_ $1 "$2" "$3" 'WifiStackRecovery: WiFi restart via') cannot=$(wc_ $1 "$2" "$3" 'WifiStackRecovery: cannot restart WiFi (') held=$(wc_ $1 "$2" "$3" 'WifiDirectManager: automatic WiFi restart held back') edges=$(wc_ $1 "$2" "$3" 'WifiDirectManager: WIFI_P2P_STATE_CHANGED_ACTION state=1') fails=$(wc_ $1 "$2" "$3" 'createGroup failed (') busyrm=$(wc_ $1 "$2" "$3" 'Native AA removeGroup before recreate failed (reason=BUSY') groups=$(wc_ $1 "$2" "$3" 'createGroup SUCCESS') sup=$(wc_ $1 "$2" "$3" 'Failed to setup P2p iface in supplicant') halrm=$(wc_ $1 "$2" "$3" 'removeIfaceInternal: iface(name)=')"; }
```

## 6. The deciding lines

Each app string was checked with `grep -F -r` on `app/src/main` at `58828f81`. All print at INFO or above.
Lines with a run-time value are listed by their fixed part.

**Head unit capture** (`$OUT/<CAP>-hu.txt`, window named per run):

| Grep | Level | Meaning |
|---|---|---|
| `WifiDirectManager: WiFi Direct looks wedged - WiFi is on, the stack reported disabled` | WARN | the policy called the stack wedged (MUST be 0 in R1, R2) |
| `WifiStackRecovery: restarting WiFi via` | WARN | a restart starts; run-time text `... via ADB (WiFi Direct wedged).` or `... via ADB (banner tap).` |
| `WifiStackRecovery: WiFi restarted via` | INFO | the command returned 0 |
| `WifiStackRecovery: WiFi restart via` | WARN | the command failed (`... failed (code=`) |
| `WifiStackRecovery: cannot restart WiFi (` | WARN | no route: no root and no local ADB |
| `WifiDirectManager: automatic WiFi restart held back (cooldown, hourly limit or live session).` | INFO | opt-in on, the rate limit or a session stopped a restart |
| `WifiDirectManager: WIFI_P2P_STATE_CHANGED_ACTION state=1` | INFO | a P2P disabled broadcast (a lower bound: the line has a log quota) |
| `createGroup failed (` | WARN | a failed Native create (the policy's failure input) |
| `Native AA removeGroup before recreate failed (reason=BUSY` | INFO | a startup remove got `BUSY` (record) |
| `WifiDirectManager: startNativeAaQuietHost() requested. Removing old group if any...` | INFO | a Native bring-up starts |
| `WifiDirectManager: P2P enabled, auto-starting Native AA quiet host` | INFO | the stack came back and the app re-armed itself |
| `createGroup SUCCESS` | INFO | a group formed (two source forms, both end in `createGroup SUCCESS!`) |
| `MainActivity: showing the connection issue banner for` | INFO | the banner is on screen; R4 wants `... for WIFI_DIRECT_STACK_STUCK` |
| `AdbManager: Command '` with `executed successfully via Self-ADB` | INFO | the local ADB route ran the command |
| `AdbManager: Self-ADB execution failed for '` | ERROR | the local ADB route failed |
| `NativeAA: Connection accepted from` | INFO | the phone came back over Bluetooth |
| `SSL handshake complete` | INFO | a session formed (INFO form) |
| `Throughput over` | INFO | video decoded; printed every 5 s in a live session |
| `MATCH! Starting AapService` | INFO | a self wake or the phone's own reconnect (record) |
| `AutomationReceiver: ` / `AutomationMarker: ` | INFO / WARN | a verb or a marker landed; a missing line voids the step |

**Framework lines on D-HU** (same capture; not in `app/src`, quoted from the contributor's report):

| Grep | Meaning |
|---|---|
| `Failed to setup P2p iface in supplicant` | the contributor's wedge signature |
| `removeIfaceInternal: iface(name)=` | the P2P interface torn down (record; it may be a debug line on D-HU) |

**Phone capture** (`$OUT/<CAP>-phone.txt`, phone clock window from `phone-marks.txt`). Record the Gearhead
version (`adb -s $PH shell dumpsys package com.google.android.projection.gearhead | grep versionName`). A 0
can mean a renamed string: then mark the condition "string not seen", not FAIL.

| Grep | Meaning |
|---|---|
| `Creating rfcomm socket for device:` | the phone ran the Bluetooth handshake (record the MAC) |
| `WPP on TCP connected to the WiFi network` | the phone reached a stored TCP endpoint |
| `Critical error` | Android Auto ended a session on a protocol fault (MUST be 0) |

## 7. Runs

### P. Pre-flight (read only, except the two radio settings named)

1. `sed -n '/^## Pick a tool/,/^## Rig and devices/p' $RIG_TOOLKIT/TOOLS.md`. Record which tools you used.
2. `$RIG_TOOLKIT/rig_preflight.sh D_HU:wifi,bt D_POCO:wifi,bt`.
3. D-MOTO: `adb -s $MOTO shell svc bluetooth disable` for the whole round, and read back `state: OFF`. D-MOTO
   is bonded to D-HU and once took a session meant for another test.
4. D-HU station: `adb -s $HU shell dumpsys wifi | grep -iE "mWifiInfo|SSID|Frequency" | head`. Record SSID
   and frequency.
5. D-HU route facts. Record each output:

   **p_route.sh**

   ```bash
   adb -s $HU shell id -u                                   # expect 0
   adb -s $HU shell 'which su; echo su_rc=$?'               # expect su_rc=1 (no su)
   adb -s $HU shell getprop ro.adb.secure
   adb -s $HU shell getprop service.adb.tcp.port
   adb -s $HU shell "netstat -ltn 2>/dev/null | grep -c ':5555 '"   # PORT5555_AT_P
   adb -s $HU shell 'pidof wpa_supplicant; which wpa_cli'
   adb -s $HU shell "ps -A | grep -iE 'android.hardware.wifi|wifi_hal|wificond' | head"
   adb -s $HU shell 'ls /sys/class/net | grep -i p2p'
   ```

   If `su_rc` is 0, D-HU has a `su` binary after all. Then say so in Setup notes and continue as written.
6. D-POCO: `adb -s $PH shell dumpsys wifip2p | grep isGroupOwner` MUST be false (else `svc wifi disable`,
   `svc wifi enable`). `adb -s $PH shell dumpsys window | grep mCurrentFocus`: at home (else
   `input keyevent KEYCODE_HOME`). `adb -s $PH shell dumpsys bluetooth_manager | grep -a -A8 'Bonded devices'`:
   record it.
7. D-HU: `adb -s $HU shell svc power stayon true`. `p2pdump P-start`.
8. `hustop`. Back up `settings.xml` to `$OUT/settings-backup.xml`. `stat` `shared_prefs/`. Install C (section
   1), `WANT_MD5=<md5 of apk-C>`, `apk_check`, `launch`, `sleep 5`, `send ACTION_QUERY_STATE` (commit
   `58828f81`), `huexit`. Read the keys back with `hu_read P0`: an install can wipe `settings.xml`.

### Stage A: the port as found

### R1. Force-stop D-HU in a session, relaunch, five times (THE POINT)

Phone capture: yes. One capture `R1`, one chain of reps `R1-1` to `R1-5`. Each rep's relaunched session is
the next rep's "in a session". Opt-in is on.

**Stop rule.** Grade 5 reps. Stop the chain early at the first rep with `sess=none` (rule below). A void
rep does not count: re-run it in the same chain, at most 2 extra reps.

**r1.sh**

```bash
RUN=R1 th_gate || exit 1; apk_check || exit 1
hustop; opt_on; hu_read R1-pre                # auto-restart-wifi-p2p-stuck true, wifi-restart-root-granted true
ph_off                                        # MUST read Wi-Fi is disabled and state: OFF
cap_on R1 phone
launch; sleep 1; step R1-start
sleep 20; ph_on; step R1-phone
waitafter $OUT/R1-hu.txt "AutomationMarker: R1-phone" "Throughput over" 180 || echo "R1 NO_FIRST_SESSION" | tee -a $OUT/r1.log
for n in 1 2 3 4 5; do
  ID=R1-$n
  sleep 20                                    # the session runs
  step $ID-kill
  adb -s $HU shell am force-stop $PKG
  sleep 3; p2pdump $ID-afterkill
  sleep 7
  launch; sleep 1; step $ID-relaunch
  t=$(waitafter $OUT/R1-hu.txt "AutomationMarker: $ID-relaunch" "Throughput over" 240); echo "$ID session_wait=$t" >> $OUT/r1.log
  sleep 30                                    # the session holds; any late create failure lands here
  p2pdump $ID-end; echo "$ID stuck_pref=$(prefread $ID)" >> $OUT/r1.log
  step $ID-end
  [ "$t" = TIMEOUT ] && break
done
cap_off; th_end
```

If the chain broke on a `TIMEOUT`, run **r1_diag.sh** before `huexit`. It tells a stuck stack from a
phone that did not come back.

**r1_diag.sh**

```bash
cap_on R1-diag phone; step R1-diag-start
p2pdump R1-diag-before
hu_wifi_cycle                                 # the remedy the PR automates, done by hand
step R1-diag-wifi
t=$(waitafter $OUT/R1-diag-hu.txt "AutomationMarker: R1-diag-wifi" "Throughput over" 180); echo "R1-diag session_wait=$t" >> $OUT/r1.log
p2pdump R1-diag-after; step R1-diag-end; cap_off
```

Then `huexit` (the app runs), and `ph_off`.

**r1_grade.sh** (prints one line per rep)

```bash
for n in 1 2 3 4 5; do ID=R1-$n; grep -aqF "AutomationMarker: $ID-kill" $OUT/R1-hu.txt || continue
  Lr=$(ln1 R1 "AutomationMarker: $ID-relaunch"); Lt=$([ -n "$Lr" ] && lnA R1 $Lr "Throughput over")
  echo "$ID $(cand R1 "AutomationMarker: $ID-kill" "AutomationMarker: $ID-end") sess=$(gap R1 "$Lr" "$Lt") ssl=$(wc_ R1 "AutomationMarker: $ID-relaunch" "AutomationMarker: $ID-end" 'SSL handshake complete') $(grep -aF "$ID stuck_pref" $OUT/r1.log | cut -d' ' -f2) rf=$(pc R1 $ID-relaunch $ID-end 'Creating rfcomm socket for device:') tcp=$(pc R1 $ID-relaunch $ID-end 'WPP on TCP connected to the WiFi network') crit=$(pc R1 $ID-kill $ID-end 'Critical error')"
done
grep -aF R1- $OUT/p2p-states.txt
```

- **Void:** the `$ID-kill` or `$ID-relaunch` marker is missing, or the rep before it had no session (then
  this kill was not "in a session").
- **Rep PASS:** `wedged=0`, `restart=0`, `cannot=0`, `held=0`, `stuck_pref=0`, `groups` >= 1, `sess` not
  `none` and <= 240, `ssl` >= 1, `crit=0`, and `rf` + `tcp` >= 1.
- **Rep FAIL:** `wedged` >= 1, or `restart` >= 1, or `cannot` >= 1, or `stuck_pref` > 0. Quote the first
  wedge line with its timestamp, and the `edges` and `fails` counts.
- **Rep `no session`:** `sess=none` with every candidate count at 0. This is not a candidate fault: the
  candidate prints nothing on that path. Record it with `busyrm`, `groups` and the R1-diag line.

**R1 verdict.**

- PASS: at least 4 graded reps, every graded rep passes.
- FAIL: any graded rep fails.
- INCONCLUSIVE: no rep fails, and fewer than 4 reps reached a session.

What a PASS would look like if the policy did nothing: `wedged=0` in every rep. So report the reach: the
largest `edges` and the largest `fails` in any one rep. If both are 0 in every rep, write "D-HU's
force-stop shape reaches neither policy input". Also report the framework side: `sup` and `halrm` per rep,
and the `curState` lines from `p2p-states.txt`. They answer whether D-HU shows the contributor's signature.

### R2. The user switches D-HU's WiFi off and on twice inside 60 s

Phone capture: yes. Opt-in is on. The phone stays off until the toggles end, so no session is live.

Two variants, 2 reps each. IDs `R2-a-<n>` and `R2-b-<n>`.

| Variant | `OFF` (s) | `ON` (s) | `CYC` |
|---|---|---|---|
| a | 8 | 12 | 2 |
| b | 3 | 5 | 3 |

`hu_wifi_on` adds its own 5 s per try, so variant a spans about 50 s and variant b about 35 s.

**r2_rep.sh** (set `ID`, `OFF`, `ON`, `CYC`)

```bash
RUN=$ID th_gate || exit 1; apk_check || exit 1
hustop; opt_on; hu_read $ID-pre
ph_off
cap_on $ID phone
launch; sleep 1; step $ID-start
waitfor $OUT/$ID-hu.txt "createGroup SUCCESS" 60; sleep 10
step $ID-toggle
for c in $(seq 1 $CYC); do adb -s $HU shell svc wifi disable; sleep $OFF; hu_wifi_on >> $OUT/$ID-wifi.txt; sleep $ON; done
step $ID-back
sleep 90                                      # the 60 s window plus a margin, with no session
echo "stuck_pref=$(prefread $ID)" >> $OUT/$ID-wifi.txt
ph_on; step $ID-phone
waitafter $OUT/$ID-hu.txt "AutomationMarker: $ID-phone" "Throughput over" 180
sleep 10; step $ID-end; cap_off; huexit; th_end
```

**r2_grade.sh** (set `ID`)

```bash
Lb=$(ln1 $ID "AutomationMarker: $ID-back"); Lp=$(ln1 $ID "AutomationMarker: $ID-phone")
echo "$ID all:[$(cand $ID "AutomationMarker: $ID-start" "AutomationMarker: $ID-end")] toggle:[$(cand $ID "AutomationMarker: $ID-toggle" "AutomationMarker: $ID-phone")] rearm=$(wc_ $ID "AutomationMarker: $ID-toggle" "AutomationMarker: $ID-phone" 'WifiDirectManager: P2P enabled, auto-starting Native AA quiet host') group_s=$(gap $ID "$Lb" "$([ -n "$Lb" ] && lnA $ID $Lb 'createGroup SUCCESS')") sess=$(gap $ID "$Lp" "$([ -n "$Lp" ] && lnA $ID $Lp 'Throughput over')") nosess_ssl=$(wc_ $ID "AutomationMarker: $ID-start" "AutomationMarker: $ID-phone" 'SSL handshake complete') $(tr '\n' ' ' < $OUT/$ID-wifi.txt) rf=$(pc $ID $ID-phone $ID-end 'Creating rfcomm socket for device:') tcp=$(pc $ID $ID-phone $ID-end 'WPP on TCP connected to the WiFi network') crit=$(pc $ID $ID-start $ID-end 'Critical error')"
```

- **Void:** `nosess_ssl` >= 1 (a session formed while the phone was meant to be off; re-run with `ph_hold`
  after `ph_off`), or any `enable_tries=FAIL` (D-HU's WiFi did not come back; record it, nudge it, re-run).
  At most 2 extra reps per variant.
- **Rep PASS:** in `all`, `wedged=0`, `restart=0`, `cannot=0`, `held=0`; `stuck_pref=0`; `group_s` not
  `none` and <= 60; `sess` not `none`; `crit=0`; and `rf` + `tcp` >= 1.
- **Rep FAIL:** `wedged` >= 1, or `restart` >= 1, or `cannot` >= 1, or `stuck_pref` > 0.
- `group_s` or `sess` missing with every candidate count at 0 is not a candidate fault. Record it.

**R2 verdict.** PASS: 4 graded reps, all pass. FAIL: any graded rep fails. INCONCLUSIVE: fewer than 3 graded
reps.

What a PASS would look like if the policy did nothing: the same. So report `edges` and `fails` from the
`toggle` window of each rep. The concern is real only where `edges` >= 2 and `fails` >= 1.

### Stage B: open the local ADB route

Skip this stage if `PORT5555_AT_P` was 1. Then the route is already open.

**b_route.sh**

```bash
adb -s $HU tcpip 5555
timeout 60 adb -s $HU wait-for-device || echo "HU_LOST" | tee -a $OUT/route.log
adb -s $HU shell id -u                                   # MUST print 0; else adb -s $HU root, then wait-for-device
adb -s $HU shell "netstat -ltn 2>/dev/null | grep -c ':5555 '" | tee -a $OUT/route.log    # MUST print 1
```

- If `HU_LOST`: stop the round. That is a broken rig (template §3a). R3 and R4 are UNTESTABLE.
- If the port count is 0: record it. R3 and R4 still run, and the restart halves are graded against
  `cannot` instead of `restart` (rules below). Write `ROUTE=NONE`. Else write `ROUTE=ADB`.

### R3. Positive control: break the supplicant as root (exploratory)

Phone capture: yes. Opt-in is on. The phone stays off until the stack is back, so no session is live during
the inductions.

**Methods**, in order, at most 2 attempts each. IDs `R3-M<k>-<n>`.

| Method | Induction (D-HU, as root) |
|---|---|
| M1 | app running, group up: `adb -s $HU shell 'kill -9 $(pidof wpa_supplicant)'` |
| M2 | `hustop`, then the M1 kill, then `launch` (the process dies owning the group, as in the PR's report) |
| M3 | app running, group up: kill the first `android.hardware.wifi` process that is not the supplicant |

**Stop rule.** Stop R3 at the first attempt with `sig=1` (rule below), and run the follow-up on that
attempt. After 6 attempts with no `sig=1`, R3 is INCONCLUSIVE and R4 does not run.

**r3_try.sh** (set `ID` and `M`)

```bash
RUN=$ID th_gate || exit 1; apk_check || exit 1
hustop; opt_on; hu_read $ID-pre
ph_off
cap_on $ID phone
launch; sleep 1; step $ID-start
waitfor $OUT/$ID-hu.txt "createGroup SUCCESS" 60; sleep 10
step $ID-induce
case $M in
  M1) adb -s $HU shell 'kill -9 $(pidof wpa_supplicant)';;
  M2) hustop; adb -s $HU shell 'kill -9 $(pidof wpa_supplicant)'; sleep 5; launch; sleep 1; step $ID-relaunch;;
  M3) HALPID=$(adb -s $HU shell "ps -A | grep -i 'android.hardware.wifi' | grep -v supplicant | head -1" | awk '{print $2}')
      echo "$ID HALPID=$HALPID" >> $OUT/r3.log; adb -s $HU shell "kill -9 $HALPID";;
esac
sleep 30; p2pdump $ID-30s
waitafter $OUT/$ID-hu.txt "AutomationMarker: $ID-induce" "$W1" 90
p2pdump $ID-120s
```

Then grade the attempt at once with **r3_sig.sh**.

**r3_sig.sh** (set `ID`)

```bash
S=$(wc_ $ID "AutomationMarker: $ID-induce" "ZZZ_NO_END" 'Failed to setup P2p iface in supplicant')
WON=$(grep -aF "$ID-30s" $OUT/p2p-states.txt | grep -c 'Wi-Fi is enabled')
sig=0; [ "$S" -ge 3 ] && [ "$WON" -ge 1 ] && sig=1
echo "$ID sig=$sig sup=$S wifi_on_30s=$WON $(cand $ID "AutomationMarker: $ID-induce" ZZZ_NO_END)" | tee -a $OUT/r3.log
```

**If `sig=0`:** end the attempt. Run `step $ID-end; cap_off; th_end`. Then check the stack:

- If `hu_wifi` reads disabled, or the capture has no `createGroup SUCCESS` after `$ID-induce`, run
  `hu_wifi_cycle` and wait 60 s for `createGroup SUCCESS` in a fresh `launch`.
- If two cycles do not bring a group back, stop R3 and stop the round (broken rig). Ask the operator to
  reboot D-HU. R4 is UNTESTABLE.
- Else `huexit` and go to the next attempt.
- A `wedged` >= 1 line in a `sig=0` attempt is not a FAIL. Record it with `edges`, `fails` and `sup`: the
  induction broke the stack, and the policy saw that.

**If `sig=1`:** keep the capture running and run the follow-up.

**r3_follow.sh** (set `ID`)

```bash
waitafter $OUT/$ID-hu.txt "AutomationMarker: $ID-induce" "$W1" 60      # the wedge line, if not yet seen
sleep 10
adb -s $HU shell dumpsys window | grep mCurrentFocus | tee -a $OUT/$ID-focus.txt
# HS1: if that line names UsbDebugging, D-HU shows "Allow USB debugging?". Accept it now:
# tick "Always allow" and press Allow (dumped-target taps by resource id, or the operator). Setup notes.
waitafter $OUT/$ID-hu.txt "AutomationMarker: $ID-induce" "WifiStackRecovery: WiFi restarted via" 90
sleep 30; echo "wifi_back=$(hu_wifi | grep -c enabled)" >> $OUT/$ID-follow.txt
waitafter $OUT/$ID-hu.txt "WifiStackRecovery: WiFi restarted via" "createGroup SUCCESS" 60
# second induction: 65 s after the first wedge line, by device time
L1=$(ln1 $ID "$W1"); T1=$(tms $ID $L1)
while :; do NOW=$(adb -s $HU shell "date '+%H:%M:%S'" | tr -d '\r' | awk -F: '{print $1*3600+$2*60+$3}')
  [ "$(echo "$NOW - $T1 >= 65" | bc)" = 1 ] && break; sleep 1; done
step $ID-induce2
case $M in                                    # never a force-stop here: it would wipe the restart history
  M3) adb -s $HU shell "kill -9 $(adb -s $HU shell "ps -A | grep -i 'android.hardware.wifi' | grep -v supplicant | head -1" | awk '{print $2}')";;
  *)  adb -s $HU shell 'kill -9 $(pidof wpa_supplicant)';;
esac
waitafter $OUT/$ID-hu.txt "AutomationMarker: $ID-induce2" "$W1" 55
sleep 5; step $ID-phase2-end
# let the stack recover; the next allowed restart comes about 130 s after the first wedge line
waitafter $OUT/$ID-hu.txt "AutomationMarker: $ID-phase2-end" "createGroup SUCCESS" 120
ph_on; step $ID-phone
waitafter $OUT/$ID-hu.txt "AutomationMarker: $ID-phone" "Throughput over" 180
sleep 10; step $ID-end; cap_off; huexit; th_end
```

The second induction never force-stops the app. The cooldown and the hourly count live in the app's
memory, so a force-stop would erase them and the check would test nothing. So M2 repeats as the M1 kill
alone. If that kill gives no second wedge line, the held-back part is INCONCLUSIVE.

**r3_grade.sh** (set `ID`)

```bash
L1=$(ln1 $ID "$W1"); Lr=$(lnA $ID ${L1:-0} 'WifiStackRecovery: restarting WiFi via'); Lok=$(lnA $ID ${L1:-0} 'WifiStackRecovery: WiFi restarted via')
Lg=$([ -n "$Lok" ] && lnA $ID $Lok 'createGroup SUCCESS'); Li2=$(ln1 $ID "AutomationMarker: $ID-induce2")
L2=$([ -n "$Li2" ] && lnA $ID $Li2 "$W1"); Lh=$([ -n "$L2" ] && lnA $ID $L2 'WifiDirectManager: automatic WiFi restart held back')
Lp=$(ln1 $ID "AutomationMarker: $ID-phone")
echo "$ID detect_s=$(gap $ID "$(ln1 $ID "AutomationMarker: $ID-induce")" "$L1") restart_line=[$([ -n "$Lr" ] && sed -n "${Lr}p" $OUT/$ID-hu.txt | cut -c19-140)] to_restart_s=$(gap $ID "$L1" "$Lr") to_ok_s=$(gap $ID "$Lr" "$Lok") group_s=$(gap $ID "$Lok" "$Lg") $(cat $OUT/$ID-follow.txt) second_wedge_s=$(gap $ID "$L1" "$L2") held_after_second=$([ -n "$Lh" ] && echo 1 || echo 0) restarts_phase2=$(wc_ $ID "AutomationMarker: $ID-induce2" "AutomationMarker: $ID-phase2-end" 'WifiStackRecovery: restarting WiFi via') adb_ok=$(grep -acF 'executed successfully via Self-ADB' $OUT/$ID-hu.txt) adb_fail=$(grep -acF "AdbManager: Self-ADB execution failed for '" $OUT/$ID-hu.txt) cannot=$(grep -acF 'WifiStackRecovery: cannot restart WiFi (' $OUT/$ID-hu.txt) sess=$(gap $ID "$Lp" "$([ -n "$Lp" ] && lnA $ID $Lp 'Throughput over')") rf=$(pc $ID $ID-phone $ID-end 'Creating rfcomm socket for device:') tcp=$(pc $ID $ID-phone $ID-end 'WPP on TCP connected to the WiFi network') crit=$(pc $ID $ID-start $ID-end 'Critical error')"
```

**R3 verdict** (on the `sig=1` attempt):

- **Detection.** PASS: `detect_s` is not `none` and <= 120. FAIL: `detect_s` is `none`. Then quote `edges`,
  `fails` and `sup` from `r3.log`.
- **Restart, `ROUTE=ADB`.** PASS: `restart_line` reads `... via ADB (WiFi Direct wedged).`,
  `to_restart_s` <= 5, `to_ok_s` <= 60, `adb_ok` >= 1, `wifi_back=1`, and `group_s` <= 60. FAIL: no
  restart line, or `adb_fail` >= 1 with no `adb_ok`. If HS1 was needed, `to_ok_s` includes the time to
  answer the dialog: say so.
- **Restart, `ROUTE=NONE`.** PASS: `restart_line` is empty and `cannot` >= 1. A restart line is a FAIL.
- **`wifi_back=0`** is its own finding: the app's restart left D-HU's WiFi off. Record it beside the D-HU
  quirk in `rig-quirks/topics/wifi.md`. Do not count it as a candidate FAIL; the host decides.
- **Held back.** PASS: `second_wedge_s` is from 60 to 120, `held_after_second=1`, and `restarts_phase2=0`.
  FAIL: `second_wedge_s` is from 60 to 120 and `restarts_phase2` >= 1. INCONCLUSIVE: `second_wedge_s` is
  `none` or outside 60 to 120 (the timing missed the window). Give the number.
- **Session back.** PASS: `sess` <= 180, `crit=0`, and `rf` + `tcp` >= 1.
- R3 overall: PASS if every part passes, FAIL if any part fails, else INCONCLUSIVE with the parts named.

### R4. The banner with opt-in off (only if R3 had `sig=1`)

Phone capture: yes. Opt-in is off, so no automatic restart may run. One rep, `R4-1`. At most 2 reps if the
first is void.

**r4_rep.sh** (set `ID=R4-1` and `M` to the method R3 used)

```bash
RUN=$ID th_gate || exit 1; apk_check || exit 1
hustop; opt_off; hu_read $ID-pre              # auto-restart-wifi-p2p-stuck false, wifi-restart-root-granted false
ph_off
cap_on $ID phone
launch; sleep 1; step $ID-start
waitfor $OUT/$ID-hu.txt "createGroup SUCCESS" 60; sleep 10
step $ID-induce
case $M in
  M1) adb -s $HU shell 'kill -9 $(pidof wpa_supplicant)';;
  M2) hustop; adb -s $HU shell 'kill -9 $(pidof wpa_supplicant)'; sleep 5; launch; sleep 1;;
  M3) adb -s $HU shell "kill -9 $(adb -s $HU shell "ps -A | grep -i 'android.hardware.wifi' | grep -v supplicant | head -1" | awk '{print $2}')";;
esac
waitafter $OUT/$ID-hu.txt "AutomationMarker: $ID-induce" "$W1" 120 || { step $ID-end; cap_off; huexit; th_end; echo "$ID NO_WEDGE"; exit 0; }
sleep 10
adb -s $HU shell input keyevent 3; sleep 2; launch; sleep 3     # resume MainActivity, which redraws the banner
waitafter $OUT/$ID-hu.txt "AutomationMarker: $ID-induce" "MainActivity: showing the connection issue banner for WIFI_DIRECT_STACK_STUCK" 15
step $ID-tap
tap_banner                                    # below; at most 3 taps in this run
waitafter $OUT/$ID-hu.txt "AutomationMarker: $ID-tap" "WifiStackRecovery: WiFi restarted via" 90
adb -s $HU shell dumpsys window | grep mCurrentFocus | tee -a $OUT/$ID-focus.txt
waitafter $OUT/$ID-hu.txt "AutomationMarker: $ID-tap" "createGroup SUCCESS" 90
ph_on; step $ID-phone
waitafter $OUT/$ID-hu.txt "AutomationMarker: $ID-phone" "Throughput over" 180
sleep 10; step $ID-end; cap_off; huexit; th_end
```

**tap_banner.sh** (one dumped-target tap; logs coordinates; the budget is enforced in `TAPS`)

```bash
tap_banner() {
  TAPS=${TAPS:-0}; [ $TAPS -ge 3 ] && { echo "TAP_BUDGET" | tee -a $OUT/taps.log; return 1; }
  adb -s $HU shell uiautomator dump /sdcard/ui.xml >/dev/null; adb -s $HU pull /sdcard/ui.xml $OUT/$ID-ui.xml >/dev/null
  RID='resource-id="com.andrerinas.headunitrevived:id/connection_issue_banner"'
  N=$(grep -o "$RID" $OUT/$ID-ui.xml | wc -l)
  [ "$N" = 1 ] || { echo "$ID banner_matches=$N VOID" | tee -a $OUT/taps.log; return 1; }
  B=$(grep -o "$RID[^>]*bounds=\"[^\"]*\"" $OUT/$ID-ui.xml | grep -o 'bounds="[^"]*"' | grep -oE '[0-9]+' | tr '\n' ' ')
  set -- $B; X=$(( ($1+$3)/2 )); Y=$(( ($2+$4)/2 ))
  grep -o 'resource-id="com.andrerinas.headunitrevived:id/connection_issue_text"[^>]*' $OUT/$ID-ui.xml | grep -o 'text="[^"]*"' >> $OUT/taps.log
  adb -s $HU shell input tap $X $Y; TAPS=$((TAPS+1)); echo "$ID tap $X $Y n=$TAPS" | tee -a $OUT/taps.log
}
```

If the banner does not resolve to exactly one node, the rep is void. Never guess a coordinate.

**r4_grade.sh** (set `ID`)

```bash
Lt=$(ln1 $ID "AutomationMarker: $ID-tap"); Lp=$(ln1 $ID "AutomationMarker: $ID-phone")
Lx=$([ -n "$Lt" ] && lnA $ID $Lt 'WifiStackRecovery: restarting WiFi via')
echo "$ID pre_tap:[$(cand $ID "AutomationMarker: $ID-induce" "AutomationMarker: $ID-tap")] banner=$(wc_ $ID "AutomationMarker: $ID-induce" "AutomationMarker: $ID-tap" 'MainActivity: showing the connection issue banner for WIFI_DIRECT_STACK_STUCK') tap_restart=[$([ -n "$Lx" ] && sed -n "${Lx}p" $OUT/$ID-hu.txt | cut -c19-140)] restarted=$(wc_ $ID "AutomationMarker: $ID-tap" "AutomationMarker: $ID-phone" 'WifiStackRecovery: WiFi restarted via') group_after=$(wc_ $ID "AutomationMarker: $ID-tap" "AutomationMarker: $ID-phone" 'createGroup SUCCESS') focus=[$(tr '\n' ' ' < $OUT/$ID-focus.txt)] sess=$(gap $ID "$Lp" "$([ -n "$Lp" ] && lnA $ID $Lp 'Throughput over')") crit=$(pc $ID $ID-start $ID-end 'Critical error') $(grep -c tap $OUT/taps.log)taps"
```

- **Void:** `NO_WEDGE`, or the banner did not resolve to one node.
- **PASS, `ROUTE=ADB`:** in `pre_tap`, `wedged` >= 1, `restart=0` and `held=0` (opt-in off logs neither);
  `banner` >= 1; `tap_restart` reads `... via ADB (banner tap).`; `restarted` >= 1; `group_after` >= 1;
  `sess` <= 180; `crit=0`.
- **PASS, `ROUTE=NONE`:** the same up to `banner`; then `tap_restart` is empty and `focus` names the WiFi
  panel (`SettingsPanelActivity` or a WiFi settings activity). No restart line.
- **FAIL:** `restart` >= 1 in `pre_tap` (a restart ran with opt-in off), or `banner=0` after a `wedged` line,
  or the tap gave neither the restart line nor the panel.

### R5. No restart inside a live session (all captures)

No new capture. Run after the last run.

**r5.sh**

```bash
for f in $OUT/*-hu.txt; do
  awk -v F="$f" '{split($2,t,":"); s=t[1]*3600+t[2]*60+t[3]}
    index($0,"Throughput over"){last=s}
    index($0,"WifiStackRecovery: restarting WiFi via"){n++; live=(last>0 && s-last<=6); if(live) bad++; print F, $1, $2, "live=" live}
    END{print F, "restarts=" n+0, "inside_session=" bad+0}' "$f"
done | tee $OUT/r5.txt
```

- PASS: `inside_session=0` for every capture.
- FAIL: any `inside_session` >= 1. Quote the restart line and the `Throughput over` line before it.
- If no capture has a restart line, R5 is INCONCLUSIVE ("no restart ran this round").

What a PASS would look like if the guard did nothing: no restart at all. So give the total `restarts` count
next to the verdict.

### End of round

`huexit` if the app runs, then `hustop`. If `PORT5555_AT_P` was 0, run `adb -s $HU usb`, then
`timeout 60 adb -s $HU wait-for-device`, then check the port count reads 0. D-HU: `hu_wifi_on`, then
`p2pdump X-end`. Restore `$OUT/settings-backup.xml` with `$RIG_TOOLKIT/restore_settings.sh` and diff it:
MUST be empty. D-POCO: `ph_on`, `isGroupOwner` false. D-MOTO: `svc bluetooth enable`, read back. D-HU:
`svc power stayon false`.

## 8. Do not re-run

- The `BUSY` create wedge on D-HU itself. The rig has never provoked it (D-HU forms a group in about
  1.2 s). R1 records `busyrm` but does not try to cause the 120 s window.
- The policy's arithmetic (thresholds, window, cooldown, hourly cap, banner order). The JVM tests
  (`P2pStackWedgePolicyTest`, `ConnectionIssueBannerPolicyTest`) cover it.
- The root route. D-HU has no `su`, and no other rig unit is rooted with a superuser app.

## 9. Report back

1. R1: one row per rep with `wedged`, `restart`, `cannot`, `stuck_pref`, `edges`, `fails`, `busyrm`, `sup`,
   `halrm` and `sess`. Then the largest `edges` and `fails` in any one rep, and the `curState` lines.
2. R2: per rep, `edges` and `fails` in the `toggle` window, `wedged`, `group_s`, and the `enable_tries`
   lines.
3. R3 and R4: which method gave `sig=1`, if any. For that attempt: `detect_s`, `restart_line`,
   `to_restart_s`, `to_ok_s`, `wifi_back`, `second_wedge_s`, `held_after_second`, and whether HS1 was
   needed. Also R5's `restarts` and `inside_session` totals.

```decisive-strings
WifiDirectManager: WiFi Direct looks wedged - WiFi is on, the stack reported disabled
WifiStackRecovery: restarting WiFi via
WifiStackRecovery: WiFi restarted via
WifiStackRecovery: WiFi restart via
WifiStackRecovery: cannot restart WiFi (
WifiDirectManager: automatic WiFi restart held back (cooldown, hourly limit or live session).
WifiDirectManager: WIFI_P2P_STATE_CHANGED_ACTION state=
createGroup failed (
Native AA removeGroup before recreate failed (reason=
WifiDirectManager: startNativeAaQuietHost() requested. Removing old group if any...
WifiDirectManager: P2P enabled, auto-starting Native AA quiet host
createGroup SUCCESS
MainActivity: showing the connection issue banner for
AdbManager: Command '
executed successfully via Self-ADB
AdbManager: Self-ADB execution failed for '
NativeAA: Connection accepted from
SSL handshake complete
Throughput over
MATCH! Starting AapService
AutomationReceiver: 
AutomationMarker: 
connection-issue-p2p-stuck
auto-restart-wifi-p2p-stuck
wifi-restart-root-granted
connection-issue-dismissed-at
connection_issue_banner
connection_issue_text
WifiStackRecovery
Failed to setup P2p iface in supplicant
removeIfaceInternal: iface(name)=
Creating rfcomm socket for device:
WPP on TCP connected to the WiFi network
Critical error
```

The last five lines are not app strings. The two framework lines come from the contributor's report, and
the three phone lines are Gearhead's. No grep of `app/src` can confirm them.
