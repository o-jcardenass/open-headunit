# hus-version-retry, round 1 brief

Published name: `hus-version-retry-round1-brief.md`. Results go in `hus-version-retry-round1-results.md`.
Evidence: create the release `rig-evidence-hus-version-retry` and upload `hus-version-retry-round1-captures.zip`.

Units: D-HU (head unit, rooted adbd) and D-POCO (phone, Android Auto's head unit server on the house WiFi). D-SAM only if the delay lever is not available on D-HU (section 3, fallback).

Queue line: `hus-version-retry-round1-brief.md` (two APKs: baseline `27c3e48b` (the parent of F) and fix `68c2bbb5`, both on `fork/fix/hus-single-version-request`; units D-HU, D-POCO, and D-SAM only in the fallback; DB1 and DF1 are the point).

Pairing: none. The round needs D-POCO's head unit server to itself, so no other round may connect to it.

Hand steps, all outside this app:

1. Pre-flight, only if the check fails: turn "Start head unit server" on in Android Auto's developer settings on D-POCO.
2. Pre-flight, only if the check fails: join D-HU's station to the house WiFi.
3. Pre-flight: close Open Headunit on every head unit that is not cabled but is on the house WiFi (D-SAM, D-HP), or switch that unit off. A head unit in mode 1 connects to D-POCO's server by itself and spoils the counts.
4. Mid-round, after each run that leaves the server deaf (expected after DB1 and DB2): on D-POCO, stop and then start the head unit server in Android Auto's developer settings. No adb route exists: the service and the developer settings screen are not exported, and D-POCO has no root. `restart_srv.sh` waits for the restart by itself and then the round goes on.

No gestures. Every app action is an `AutomationReceiver` verb.

**Time budget.** Pre-flight 15 min, two builds 20 min, runs about 55 min, two server restarts about 8 min, restore 5 min. **About 1 h 45 in all, 1 h of it runs.** The fallback, if used, replaces the delay runs and adds about 20 min of hand steps.

## 1. Build and baseline

Two APKs. No history was rewritten. Both refs are on `fork` once this brief is published.

| Arm | Ref | Commit | Tree |
|---|---|---|---|
| B (baseline) | `fork/fix/hus-single-version-request~1` (upstream main) | `27c3e48b727a72d8caf1302e6fc4b388510ac8e1` | `9549de4efb8297a50bbd4ef5cbefa33945d07a81` |
| F (fix) | `fork/fix/hus-single-version-request` | `68c2bbb500aa545d4354717e4a3e31425d926487` | `759737111747d913c1c0cd8ab820246a39cf97c6` |

F is one commit on B. The dev host ran the JVM suite at `68c2bbb5`: 3481 tests, 0 failures. Do not run the unit tests on the rig. No other queued round shares this base.

Work from `~/Coding/StudioProjects`. Save every script block in section 5 under `$OUT/scripts/` with `$RIG_TOOLKIT/extract_brief_blocks.py <brief> $OUT/scripts`. Each block name is bold and in backticks, on the line right above its fence.

**`build.sh`**
```bash
. $(dirname $0)/lib.sh
WT=$(ls -d worktrees/* 2>/dev/null | head -1)
[ -n "$WT" ] || WT=$($RIG_TOOLKIT/wt-new.sh hus-version-retry candidate main | tail -1)
git -C $WT fetch fork fix/hus-single-version-request
for A in F B; do
  [ $A = F ] && C=68c2bbb500aa545d4354717e4a3e31425d926487 T=759737111747d913c1c0cd8ab820246a39cf97c6
  [ $A = B ] && C=27c3e48b727a72d8caf1302e6fc4b388510ac8e1 T=9549de4efb8297a50bbd4ef5cbefa33945d07a81
  git -C $WT checkout --detach $C
  [ "$(git -C $WT rev-parse 'HEAD^{tree}')" = $T ] || { echo "TREE_MISMATCH $A"; exit 1; }
  $RIG_TOOLKIT/rig_thermal.sh wait 70
  HUR_DIR=$WT MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh || { echo "BUILD_FAILED $A"; exit 1; }
  cp $(ls -t $RIG_DATA/apks/com.andrerinas.headunitrevived_*.apk | head -1) $OUT/$A.apk
  echo "$A md5 $(md5sum < $OUT/$A.apk | cut -c1-32) versionRequests=$(unzip -p $OUT/$A.apk 'classes*.dex' | strings | grep -c versionRequests)"
done | tee $OUT/build.txt
```

Build gate. The two md5s MUST differ. `versionRequests` MUST be 1 or more in F and MUST be 0 in B: `HandshakeMessagePolicy.versionRequests` is new in F. A failed gate stops the round.

Identity after each install (`inst` in `lib.sh` does both):

- `$RIG_TOOLKIT/apk_identity.sh $OUT/<arm>.apk <serial>` MUST exit 0.
- The `ACTION_QUERY_STATE` reply MUST carry commit `68c2bbb500aa` for F and `27c3e48b727a` for B. A `-dirty` suffix is fine; say so.

A second identity check comes from the log. On a silent socket F prints `No VERSION_RESPONSE within the handshake budget (attempt 1)` and B prints `No VERSION_RESPONSE within 2s (attempt 1)`.

## 2. What this is and why

Android Auto's head unit server on the phone (TCP 5277) hands each accepted socket to its car service and waits there with no timeout. Its accept loop is serial. So one connection that the car service never finishes leaves the server deaf to every later connection. Only a stop and start of the server on the phone clears it.

The head unit sends an AAP `VERSION_REQUEST` and waits 2 s for the answer. On B, with no answer in 2 s, it sends the request again, up to three times. Over TCP a request is never lost, so a second request reaches the phone only as a duplicate.

Evidence (pr-1119 round 1, D-SAM in mode 1 against D-POCO, Gearhead 18.0.664204, captures asset `pr-1119-home-redesign-round1-captures.zip`, folder `for-coding-agents-d-sam-loop/`):

| D-SAM time | Line |
|---|---|
| 21:07:20.093 | `Found Headunit Server on 192.168.1.20:5277`, just after the server started |
| 21:07:23.517 | `No VERSION_RESPONSE within 2s (attempt 1)`, so a second request went out |
| 21:07:24.928 | the version answer, `the phone selected 1.7 ... STATUS_SUCCESS` |
| 21:07:26.940 | `SSL Handshake: Failed to read AAP header`, then `handshake_failed` |
| 21:07:29 to 21:10:25 | every later connection silent, then `peer_silent` |
| 21:11:17.345 | after a server restart: the answer in 1.36 s, no resend, SSL 0.45 s later |

So the phone got VR, VR, then the TLS ClientHello, and never answered the ClientHello.

Hypotheses, from the ticket's diagnosis:

1. **The duplicate request wedges the server (leading).** Delay the first answer past 2 s: B wedges, F does not.
2. A cold server's first session fails whatever the head unit sends. F would then fail the first connection after a server start too.
3. Gearhead 18.0 is stricter about bytes before TLS. This round cannot test it: there is one Gearhead build on the rig.
4. The slow link, not the request. F under the same delay would then fail too.

The fix (`HandshakeMessagePolicy.versionRequests(isUsb)`): over TCP, one request and the full 10 s budget. USB keeps its three tries. The question: does the second request wedge the server, does F avoid it, and does F still make a normal Headunit Server connection?

## 3. What is different about this round

**The delay lever.** D-HU's adbd runs as root. `tc` adds an egress delay of 2500 ms on D-HU's WiFi interface for each delay run. The sequence on B is then:

- the TCP connect takes about 2.5 s (the connect timeout is 5 s);
- the first request reaches the phone 2.5 s after the head unit sends it;
- B times out at 2 s and queues the second request at about 2.2 s;
- the answer comes back at about 2.6 s, and B queues the ClientHello.

The phone so gets VR, VR, ClientHello, the same order as the D-SAM capture. F sends one request and waits. The script removes the delay 3 s after `Handshake: the phone selected`, so every queued packet has left before the delete and nothing is dropped. The session then runs at normal speed.

**Delay runs use `wifi-connection-mode` 0, not 1.** Mode 1 runs a discovery sweep that opens its own sockets to :5277. A socket that the app opens and then gives up, or that the connection arbiter refuses beside an explicit connect, wedges the server by itself (memory `aa-headunit-server-binds-one-connection`). Mode 0 runs no discovery, so every connection in a delay run is one `ACTION_CONNECT --es ip`. The handshake code is the same in both modes: `AapTransport` over a TCP socket. The controls use mode 1, the real Headunit Server path.

**Fallback if `tc` or netem is not available on D-HU** (pre-flight writes `NETEM=no`). Then skip DF1, DF2, DB1 and DB2 and run the cold-server runs (section 7, FB) on D-SAM. D-SAM is where the bug showed: its first answer after a server start took 3.4 s. Each try needs a server restart by hand. The head unit connects as soon as the phone logs that the server runs.

**Strings differ from the pr-1119 capture.** That build printed `Version response received` and `SSL Handshake: Failed to read AAP header`. Neither exists on B or F. The runs grade on the strings in section 6.

Other rig facts that change the runs:

- **The server can be down** (`rig-dpoco-headunit-server-down`). Every run checks the :5277 listener (`:149D` in `/proc/net/tcp6`) first. A run that finds it down is void. Never force-stop Gearhead on D-POCO: that drops the server. Check Gearhead with `ps -A | grep gearhead`.
- **Gearhead's own permission dialog wedges the server like a force-stop** (`rig-quirks/topics/gearhead.md`). Pre-flight reads D-POCO's focus and sends HOME if Gearhead or Settings is in front.
- **D-POCO's own Open Headunit must not run.** Its Self Mode would use the same server. Pre-flight stops it if `pidof` finds it.
- **D-POCO's Bluetooth goes off for the round** and comes back at the end, as pre-flight found it. Mode 0 and mode 1 use no Bluetooth. With it off, Gearhead's wireless RFCOMM attempts toward D-HU's radio stay out of the phone capture.
- **D-POCO's screen stays on** for the round (`svc power stayon usb`), so Doze cannot hold the server's network. Pre-flight saves the old value and restore puts it back.
- **Device-to-device ARP can break on this LAN** (pr-1119 HP-C2). Pre-flight pings D-POCO from D-HU. Zero replies twice, 60 s apart, stops the round.
- **D-HU's clock may be months behind.** Every window is by marker text, and every time difference is within one capture. Do not compare D-HU times with D-POCO times.
- **D-HU's driver stack floods logcat.** The capture runs to a file from before the launch, so the ring buffer does not matter. The round needs `log-level` 1 (DEBUG) for `Handshake: Version request sent`, which is `AppLog.d` with no verbose guard.
- **`SSL handshake complete` prints twice per session at DEBUG**: the INFO line from `AapSslContext` and `Handshake: SSL handshake complete. TS:`. The helper `ssl` counts only the INFO form, so one session counts 1.
- **The audio settings are a deliberate worst case.** Do not touch `use-aac-audio`, `audio-latency-multiplier` or `audio-queue-capacity`.
- **`shared_prefs` may be root-owned** (`rig-quirks/units/D-HU.md`). Pre-flight `stat`s the directory and the file. Report both, also when they are correct.
- **The pre-round build comes back at the end.** Pre-flight pulls the installed APK and the settings, and restore puts both back.

**Expected INCONCLUSIVE:** a delay run whose lever did not fire (section 7 gives the gate). After one re-run at 3500 ms, a second miss is INCONCLUSIVE, not FAIL.

## 4. Settings keys

`seed` in `lib.sh` writes these with the app stopped, by `$RIG_TOOLKIT/set_hu_settings_host.py` on D-HU (rooted) and `$RIG_TOOLKIT/set_prefs_runas_host.py` on D-SAM. It reads `wifi-connection-mode` and `log-level` back before every launch.

| Key | Value | Why |
|---|---|---|
| `onboarding-version` (int) | 2 | no wizard on launch |
| `rename_notice_shown_v2` (boolean) | true | no notice dialog |
| `aa174_notice_shown` (boolean) | true | no notice dialog |
| `native-driver-selection-mode` (int) | 0 | no driver selector |
| `log-level` (int) | 1 | DEBUG, for `Version request sent` |
| `wifi-connection-mode` (int) | 1 in C runs, 0 in D and FB runs | section 3 |
| `connection-modes` (set) | `usb,wifi,self` | wireless must be a chosen mode, or the launcher refuses |
| `auto-connect-last-session` (boolean) | false | no connection the run did not send |
| `auto-connect-single-usb` (boolean) | false | same |
| `video-profile-starvation-cap` | deleted | value recorded in pre-flight first |
| `connection-issue-headunit-server-deaf` | deleted | so a new stamp is visible; value recorded first |

`ACTION_LOG_MARKER` is not in `AutomationCommandPolicy.CONFIGURING` on B or F, so `allow-external-configuration` is not needed.

## 5. Helpers

Run every adb call in sequence, never two at once against one unit. Each run is a script: run it with `bash $OUT/scripts/<name>.sh <args>`. Only `lib.sh` is sourced, and only by those scripts. Write the results file after every run.

**`lib.sh`**
```bash
source ~/Coding/StudioProjects/rig-toolkit/rig_devices.sh
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
MAIN=$PKG/com.andrerinas.openheadunit.main.MainActivity
OUT=$RIG_DATA/rounds/hus-version-retry-round1; mkdir -p $OUT/scripts
H=${H:-$D_HU}; P=$D_POCO
export LC_ALL=C
[ -f $OUT/env.sh ] && . $OUT/env.sh      # POCO_IP HU_IP IF NETEM ... written by preflight.sh
send() { local a=$1; shift; adb -s $H shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; sleep 0.3; }
# marker on the head unit (AutomationMarker) and on the phone (RIGMARK); labels are <RUN>_<step>
mark() { send ACTION_LOG_MARKER --es text "$1" >/dev/null; adb -s $P shell log -t RIGMARK "$1"; sleep 0.3; echo "$1 $(date +%T)" >> $OUT/marks.txt; }
pmark() { adb -s $P shell log -t RIGMARK "$1"; sleep 0.3; echo "$1 $(date +%T)" >> $OUT/marks.txt; }
launch() { adb -s $H shell am start -n $MAIN >/dev/null; }
appexit() { adb -s $H shell am start -a android.intent.action.VIEW -d "headunit://exit" >/dev/null; sleep 5; adb -s $H shell am force-stop $PKG; }
cap() { adb -s $2 logcat -G 16M >/dev/null 2>&1; adb -s $2 logcat -c; stdbuf -oL adb -s $2 logcat -v time > $1 & echo $! > $1.pid; sleep 1; }
uncap() { kill $(cat $1.pid) 2>/dev/null; }
# seg <file> <from> [<to>]: lines after the last <from> marker, up to the next <to> marker (or the end)
seg() { awk -v a=": $2" -v b=": ${3:-@@none@@}" 'index($0,a){buf="";on=1;next} on&&index($0,b){on=0} on{buf=buf $0 "\n"} END{printf "%s",buf}' "$1"; }
cnt() { seg "$1" "$2" "$3" | grep -acF -- "$4"; }
ssl() { seg "$1" "$2" "$3" | grep -aF 'SSL handshake complete' | grep -acvF 'Handshake: SSL handshake complete'; }
# waitfor <file> <from> <cap-s> <string>...: returns 0 as soon as one string appears after <from>
waitfor() { local f=$1 m=$2 cap=$3 t=0 s; shift 3
  while [ $t -lt $cap ]; do for s in "$@"; do [ "$(seg $f $m | grep -acF -- "$s")" -ge 1 ] && return 0; done
    sleep 1; t=$((t+1)); done; return 1; }
gap() { python3 -I $OUT/scripts/gap.py "$@"; }

# host thermal (rig-quirks/topics/tooling.md)
th_gate() { $RIG_TOOLKIT/rig_thermal.sh wait 75 || $RIG_TOOLKIT/rig_thermal.sh wait 75 || { echo HOST_HOT; return 1; }
  $RIG_TOOLKIT/rig_thermal.sh watch $OUT/thermal-$1.log & THPID=$!; }
th_end() { kill $THPID 2>/dev/null; wait $THPID 2>/dev/null; }

# the server on D-POCO: listener count (MUST be 1 or more), and who holds a socket on it
srv_up() { adb -s $P shell cat /proc/net/tcp /proc/net/tcp6 2>/dev/null | grep -acE ':149D [0-9A-F]+:0000 0A'; }
peers() { adb -s $P shell cat /proc/net/tcp /proc/net/tcp6 2>/dev/null | python3 -I $OUT/scripts/peers.py; }

# netem on D-HU (adbd is root, no su). Each prints one verdict line: NM_ON_OK / NM_ON_FAILED, NM_OFF_OK / NM_OFF_FAILED
nm_on() { adb -s $D_HU shell tc qdisc add dev $IF root netem delay ${DELAY:-2500ms} 2>&1 \
    || adb -s $D_HU shell tc qdisc replace dev $IF root netem delay ${DELAY:-2500ms} 2>&1
  adb -s $D_HU shell tc qdisc show dev $IF | tee -a $OUT/qdisc.log | grep -q netem && echo NM_ON_OK || echo NM_ON_FAILED; }
nm_off() { adb -s $D_HU shell tc qdisc del dev $IF root 2>/dev/null
  adb -s $D_HU shell tc qdisc show dev $IF | tee -a $OUT/qdisc.log | grep -q netem && echo NM_OFF_FAILED || echo NM_OFF_OK; }

# install one arm on $H and prove it (section 1)
inst() { adb -s $H install -r -d $OUT/$1.apk
  $RIG_TOOLKIT/apk_identity.sh $OUT/$1.apk $H; echo "apk_identity exit $?"
  send ACTION_QUERY_STATE | grep -ao 'commit[^,}]*'; adb -s $H shell am force-stop $PKG
  echo $1 > $OUT/installed-$H; }
# seed <wifi-connection-mode>: section 4 keys, app stopped, then read back
seed() { local K="scalar:int:onboarding-version:2 scalar:boolean:rename_notice_shown_v2:true scalar:boolean:aa174_notice_shown:true
  scalar:int:native-driver-selection-mode:0 scalar:int:log-level:1 scalar:int:wifi-connection-mode:$1 set:connection-modes:usb,wifi,self
  scalar:boolean:auto-connect-last-session:false scalar:boolean:auto-connect-single-usb:false
  del:video-profile-starvation-cap del:connection-issue-headunit-server-deaf"
  adb -s $H shell am force-stop $PKG; sleep 1
  if [ $H = $D_HU ]; then $RIG_TOOLKIT/set_hu_settings_host.py $H $K
    adb -s $H shell cat /data/data/$PKG/shared_prefs/settings.xml
  else $RIG_TOOLKIT/set_prefs_runas_host.py $H $K; adb -s $H shell run-as $PKG cat shared_prefs/settings.xml
  fi | grep -aoE '"(wifi-connection-mode|log-level)" value="[0-9]+"'; }

# end a live session cleanly (ByeBye, then the socket closes: this does not wedge the server)
bye() { mark ${1}_bye$2; send ACTION_DISCONNECT >/dev/null; waitfor $HC ${1}_bye$2 15 'Disconnect action received.'; sleep 5; }
# connect <run> <step>: one explicit connection, waits for its outcome, ends it if it formed
conn() { mark ${1}_$2; send ACTION_CONNECT --es ip $POCO_IP >/dev/null
  waitfor $HC ${1}_$2 40 'SSL handshake complete' 'session state failed'
  if [ "$(ssl $HC ${1}_$2)" -ge 1 ]; then waitfor $HC ${1}_$2 30 'Throughput over'; bye $1 $2; fi; }

# counts for one window, as key=value lines the grader reads
hcounts() { local f=$1 a=$2 b=$3
  echo "HU $a: connect_verb=$(cnt $f $a "$b" 'AutomationReceiver: com.andrerinas.openheadunit.ACTION_CONNECT')" \
    "init_auto=$(cnt $f $a "$b" 'WifiLauncher: Initializing WiFi Mode: AUTO')" "init_manual=$(cnt $f $a "$b" 'WifiLauncher: Initializing WiFi Mode: MANUAL')" \
    "scan=$(cnt $f $a "$b" 'NetworkDiscovery: Starting scan...')" "found=$(cnt $f $a "$b" "NetworkDiscovery: Found Headunit Server on $POCO_IP:5277")" \
    "vreq=$(cnt $f $a "$b" 'Handshake: Version request sent')" "within2s=$(cnt $f $a "$b" 'No VERSION_RESPONSE within 2s (attempt 1)')" \
    "budget=$(cnt $f $a "$b" 'within the handshake budget')" "selected=$(cnt $f $a "$b" 'Handshake: the phone selected')" \
    "late=$(cnt $f $a "$b" 'SSL Handshake: discarded a late VERSION_RESPONSE')" "sslfail=$(cnt $f $a "$b" 'Handshake: SSL performHandshake failed.')" \
    "ssl=$(ssl $f $a "$b")" "tput=$(cnt $f $a "$b" 'Throughput over')" "failed=$(cnt $f $a "$b" 'session state failed')" \
    "silent=$(cnt $f $a "$b" 'session state failed (peer_silent)')" "silent_line=$(cnt $f $a "$b" 'the peer accepted the connection and then sent nothing at all')"; }
pcounts() { local f=$1 a=$2 b=$3
  echo "PH $a: hu_connected=$(cnt $f $a "$b" 'Head unit connected')" "hu_disconnected=$(cnt $f $a "$b" 'Head unit disconnected')" \
    "car_requests=$(cnt $f $a "$b" 'Car requests protocol version')" "critical=$(cnt $f $a "$b" 'Critical error')" \
    "terminating=$(cnt $f $a "$b" 'Terminating connection')" "handoff=$(cnt $f $a "$b" 'CONNECTION_HANDOFF_COMPLETE')" \
    "duplex=$(cnt $f $a "$b" 'START_DUPLEX')" "hu_ip_lines=$(cnt $f $a "$b" "$HU_IP")"; }
```

**`gap.py`**
```python
# gap.py <file> <from-label> <to-label|-> <A> <B>: ms from the first line with A to the next line with B, inside the window
import sys, re
f, a, b, pa, pb = sys.argv[1:6]
on, ta = False, None
for l in open(f, errors='replace'):
    if ': ' + a in l:
        on, ta = True, None
        continue
    if on and b != '-' and ': ' + b in l:
        on = False
    if not on:
        continue
    m = re.match(r'\d\d-\d\d (\d\d):(\d\d):(\d\d)\.(\d{3})', l)
    if not m:
        continue
    t = ((int(m[1]) * 60 + int(m[2])) * 60 + int(m[3])) * 1000 + int(m[4])
    if ta is None and pa in l:
        ta = t
    elif ta is not None and pb in l:
        print(t - ta)
        sys.exit(0)
print('NA')
```

**`peers.py`**
```python
# peers.py: reads /proc/net/tcp and tcp6 on stdin; prints "<state> <remote ip> <remote port>" for every socket on :5277
# state 0A = listen, 01 = established (a wedged server leaves new connections at 01, never served)
import sys
for l in sys.stdin:
    p = l.split()
    if len(p) < 4 or not p[1].endswith(':149D'):
        continue
    ip, port = p[2].split(':')
    h = ip[-8:]
    print(p[3], '.'.join(str(int(h[i:i + 2], 16)) for i in (6, 4, 2, 0)), int(port, 16))
```

**`preflight.sh`**
```bash
. $(dirname $0)/lib.sh
exec > >(tee $OUT/preflight.txt) 2>&1
$RIG_TOOLKIT/rig_preflight.sh D_HU:wifi D_POCO:wifi; $RIG_TOOLKIT/rig_cleanup.sh list
echo "== D-HU"; adb -s $D_HU shell id                           # MUST show uid=0(root)
echo "hu date $(adb -s $D_HU shell date) host $(date)"
adb -s $D_HU shell stat -c '%U:%G %a %n' /data/data/$PKG/shared_prefs /data/data/$PKG/shared_prefs/settings.xml
HU_OWNER=$(adb -s $D_HU shell stat -c '%U:%G' /data/data/$PKG/shared_prefs/settings.xml | tr -d '\r')
adb -s $D_HU shell cat /data/data/$PKG/shared_prefs/settings.xml > $OUT/hu-backup.xml; wc -c $OUT/hu-backup.xml   # MUST be over 0
grep -aoE '"(video-profile-starvation-cap|connection-issue-[a-z-]*|wifi-connection-mode|log-level)"[^/]*' $OUT/hu-backup.xml
adb -s $D_HU pull $(adb -s $D_HU shell pm path $PKG | cut -d: -f2 | tr -d '\r') $OUT/hu-before.apk; md5sum $OUT/hu-before.apk
adb -s $D_HU shell cmd role get-role-holders android.app.role.HOME   # MUST NOT be $PKG; if it is, stop and ask
adb -s $D_HU shell dumpsys wifi | grep -a -m1 'mWifiInfo SSID'      # MUST name the house WiFi; else hand step 2
echo "== D-POCO"
POCO_IP=$(adb -s $D_POCO shell ip -4 addr show wlan0 | grep -ao 'inet [0-9.]*' | cut -d' ' -f2)
HU_IP=$(adb -s $D_HU shell ip -4 addr show wlan0 | grep -ao 'inet [0-9.]*' | cut -d' ' -f2)
IF=$(adb -s $D_HU shell ip route get $POCO_IP | grep -ao 'dev [^ ]*' | cut -d' ' -f2); IF=${IF:-wlan0}
echo "POCO_IP=$POCO_IP HU_IP=$HU_IP IF=$IF"
adb -s $D_POCO shell dumpsys package com.google.android.projection.gearhead | grep -a -m1 versionName
echo "gearhead procs $(adb -s $D_POCO shell ps -A | grep -c gearhead)"
echo "server listeners $(srv_up)"                                  # MUST be 1 or more; else hand step 1
peers
adb -s $D_POCO shell pidof $PKG && { adb -s $D_POCO shell am start -a android.intent.action.VIEW -d headunit://exit; sleep 5; adb -s $D_POCO shell am force-stop $PKG; }
adb -s $D_POCO shell dumpsys window | grep -a -m1 mCurrentFocus    # Gearhead or Settings in front: HOME
adb -s $D_POCO shell input keyevent KEYCODE_HOME
POCO_BT=$(adb -s $D_POCO shell dumpsys bluetooth_manager | grep -a -m1 -E '^ *enabled:' | grep -aoE 'true|false')
POCO_STAYON=$(adb -s $D_POCO shell settings get global stay_on_while_plugged_in | tr -d '\r')
echo "POCO_BT=$POCO_BT POCO_STAYON=$POCO_STAYON"
adb -s $D_POCO shell svc bluetooth disable; sleep 3; adb -s $D_POCO shell svc power stayon usb
adb -s $D_POCO shell dumpsys bluetooth_manager | grep -a -m1 -E '^ *enabled:'
echo "== link"; adb -s $D_HU shell ping -c 4 -W 3 $POCO_IP | grep -a -E 'received|time='
echo "== netem"; NETEM=no
adb -s $D_HU shell which tc && {
  adb -s $D_HU shell tc qdisc show dev $IF > $OUT/qdisc-baseline.txt; cat $OUT/qdisc-baseline.txt
  r=$(nm_on); echo "$r"
  adb -s $D_HU shell ping -c 2 -W 6 $POCO_IP | grep -ao 'time=[0-9.]*'   # MUST be 2400 or more with the delay on
  echo "$(nm_off)"; adb -s $D_HU shell tc qdisc show dev $IF | diff - $OUT/qdisc-baseline.txt && echo QDISC_BASELINE_BACK
  [ "$r" = NM_ON_OK ] && NETEM=yes; }
echo "NETEM=$NETEM"
cat > $OUT/env.sh <<EOF
POCO_IP=$POCO_IP; HU_IP=$HU_IP; IF=$IF; NETEM=$NETEM; HU_OWNER=$HU_OWNER; POCO_BT=$POCO_BT; POCO_STAYON=$POCO_STAYON
EOF
```

Pre-flight gates. Each line marked MUST is a gate. A failed gate with a hand step: ask for the hand step, then run `preflight.sh` again. A link with 0 replies: wait 60 s and run it again; a second 0 stops the round. `NETEM=no` with every other gate met: run the fallback (section 7, FB) in place of the D runs. Put the whole `preflight.txt` in Setup notes.

Usage: `bash run_ctl.sh <F|B> <k>`: a control, mode 1, no delay.

**`run_ctl.sh`**
```bash
. $(dirname $0)/lib.sh
A=$1; R=C$1$2; HC=$OUT/$R-hu.txt; PC=$OUT/$R-ph.txt
th_gate $R || exit 1
[ "$(srv_up)" -ge 1 ] || { echo "$R VOID: server down"; th_end; exit 2; }
[ "$(cat $OUT/installed-$H 2>/dev/null)" = $A ] || inst $A
seed 1
cap $HC $H; cap $PC $P
mark ${R}_start; launch
waitfor $HC ${R}_start 20 'WifiLauncher: Initializing WiFi Mode: AUTO' || echo "$R no AUTO init line"
waitfor $HC ${R}_start 90 'SSL handshake complete' 'session state failed'
[ "$(ssl $HC ${R}_start)" -ge 1 ] && { waitfor $HC ${R}_start 30 'Throughput over'; bye $R x; }
peers | tee $OUT/$R-peers.txt
mark ${R}_end; appexit; uncap $HC; uncap $PC; th_end
hcounts $HC ${R}_start ${R}_end; pcounts $PC ${R}_start ${R}_end
echo "gap_ms=$(gap $HC ${R}_start ${R}_end 'Handshake: Version request sent' 'Handshake: the phone selected')"
```

Usage: `bash run_delay.sh <F|B> <k>`: the delay run, mode 0, D-HU only.

**`run_delay.sh`**
```bash
. $(dirname $0)/lib.sh
A=$1; R=D$1$2${DELAY:+_$DELAY}; HC=$OUT/$R-hu.txt; PC=$OUT/$R-ph.txt
trap 'nm_off >> $OUT/$R-nm.txt' EXIT
th_gate $R || exit 1
[ "$(srv_up)" -ge 1 ] || { echo "$R VOID: server down"; th_end; exit 2; }
[ "$(cat $OUT/installed-$H 2>/dev/null)" = $A ] || inst $A
seed 0
cap $HC $H; cap $PC $P
mark ${R}_start; launch
waitfor $HC ${R}_start 20 'WifiLauncher: Initializing WiFi Mode: MANUAL' || echo "$R no MANUAL init line"
sleep 5
nm_on | tee $OUT/$R-nm.txt                                      # MUST print NM_ON_OK, else the run is void
mark ${R}_n0; send ACTION_CONNECT --es ip $POCO_IP >/dev/null
waitfor $HC ${R}_n0 15 'Handshake: the phone selected' && sleep 3   # 3 s: every queued packet leaves first
nm_off | tee -a $OUT/$R-nm.txt; mark ${R}_clear
waitfor $HC ${R}_n0 40 'SSL handshake complete' 'session state failed'
[ "$(ssl $HC ${R}_n0)" -ge 1 ] && { waitfor $HC ${R}_n0 30 'Throughput over'; bye $R 0; }
conn $R n1
[ "$(ssl $HC ${R}_n1)" -ge 1 ] || conn $R n2
peers | tee $OUT/$R-peers.txt
mark ${R}_end; appexit; uncap $HC; uncap $PC; th_end
hcounts $HC ${R}_n0 ${R}_n1; hcounts $HC ${R}_n1 ${R}_n2; hcounts $HC ${R}_n2 ${R}_end
pcounts $PC ${R}_n0 ${R}_n1; pcounts $PC ${R}_n1 ${R}_n2; pcounts $PC ${R}_n2 ${R}_end
echo "gap_ms=$(gap $HC ${R}_n0 ${R}_n1 'Handshake: Version request sent' 'Handshake: the phone selected')"
cat $OUT/$R-nm.txt
```

When `n2` does not exist, `hcounts ... n1 n2` reads to the end of the capture, and the `n2` window prints zeros.

Usage: `bash restart_srv.sh <k>`: hand step 4.

**`restart_srv.sh`**
```bash
. $(dirname $0)/lib.sh
R=RS$1; PC=$OUT/$R-ph.txt; cap $PC $P; pmark ${R}_hand
echo "HAND STEP: on D-POCO, Android Auto developer settings: Stop head unit server, then Start head unit server."
down=0; t=0; ok=0
while [ $t -lt 900 ]; do
  [ "$(cnt $PC ${R}_hand '' 'Network server running on port')" -ge 1 ] && { ok=1; break; }
  n=$(srv_up); [ "$n" -eq 0 ] && down=1; [ $down = 1 ] && [ "$n" -ge 1 ] && { ok=1; break; }
  sleep 1; t=$((t+1)); done
sleep 3; echo "$R restarted=$ok after ${t}s listeners=$(srv_up)"; peers; uncap $PC
```

`restarted=0` after 15 min stops the round: the remaining runs are UNTESTABLE (server not restarted).

## 6. The deciding lines

Every string below was checked with `grep -F` against `app/src/main/java` at both `27c3e48b` and `68c2bbb5`. Four are built at run time from a template, and the source holds only the template: `No VERSION_RESPONSE within 2s (attempt 1)` and `within the handshake budget` (from `Handshake: No VERSION_RESPONSE within ${...} (attempt $attempt)` in `AapTransport`), `session state failed (peer_silent)` (from `AapService: session state $state (reason)`), and the two `Initializing WiFi Mode:` forms. The phone strings were checked in the Gearhead 18.0.664204 decompile.

Head unit capture (`$OUT/<run>-hu.txt`), in the window the run names:

| Key | String | Level | Meaning |
|---|---|---|---|
| `vreq` | `Handshake: Version request sent` | DEBUG | one per request sent |
| `within2s` | `No VERSION_RESPONSE within 2s (attempt 1)` | WARN | B resends. F never prints it over TCP |
| `budget` | `within the handshake budget` | WARN | the last window ran out |
| `selected` | `Handshake: the phone selected` | INFO | the version answer arrived |
| `late` | `SSL Handshake: discarded a late VERSION_RESPONSE` | INFO | the phone answered a second request too |
| `sslfail` | `Handshake: SSL performHandshake failed.` | ERROR | TLS did not finish |
| `ssl` | `SSL handshake complete`, minus `Handshake: SSL handshake complete` | INFO | one per session |
| `tput` | `Throughput over` | INFO | video renders: the session is live |
| `failed` | `session state failed` | INFO | any failed attempt |
| `silent` | `session state failed (peer_silent)` | INFO | the server accepted and said nothing |
| `silent_line` | `the peer accepted the connection and then sent nothing at all` | ERROR | same, from `AapTransport` |
| `found` | `NetworkDiscovery: Found Headunit Server on <POCO_IP>:5277` | INFO | mode 1 found the server |
| `scan` | `NetworkDiscovery: Starting scan...` | INFO | MUST be 0 in a D run |
| `init_auto`, `init_manual` | `WifiLauncher: Initializing WiFi Mode: AUTO` / `MANUAL` | INFO | the mode under test |
| `connect_verb` | `AutomationReceiver: com.andrerinas.openheadunit.ACTION_CONNECT` | INFO | the verb landed |
| | `Disconnect action received.` | INFO | the clean end landed |

Phone capture (`$OUT/<run>-ph.txt`), same windows (the `RIGMARK` lines carry the labels):

| Key | String | Tag | Meaning |
|---|---|---|---|
| `hu_connected` | `Head unit connected` | `GH.DHUService` | the server's accept loop took a connection. A wedged server takes none |
| `car_requests` | `Car requests protocol version` | `CAR.GAL.GAL` | one per `VERSION_REQUEST` the car service read |
| `critical` | `Critical error` | `CAR.*` | record |
| `terminating` | `Terminating connection` | `CAR.GAL.GAL` | record |
| `handoff`, `duplex` | `CONNECTION_HANDOFF_COMPLETE`, `START_DUPLEX` | `GH.ConnLoggerV2`, `ActivityTaskManager` | record |
| `hu_ip_lines` | D-HU's IP | any | record |
| | `Network server running on port` | `GH.DHUService` | the server started (restart_srv.sh) |

**A phone condition counts only if the phone prints at that level.** If `hu_connected` is 0 in every window of every run, Gearhead logs nothing at that level on this build. Then the phone conditions are record-only, and Setup notes say so.

`peers` lists the sockets on D-POCO's :5277 at the end of each run. A `01` line from an IP that is not `HU_IP` means another device used the server: that run is void. Record how many `01` lines from `HU_IP` remain after a wedge.

## 7. Runs

Order: CF1, DF1, DF2, CB1, DB1, RS1, CB2, DB2, RS2, CF2. A run starts only when the run before it has ended and its counts are in the results file.

Common void rules (re-run the run once; a second void is UNTESTABLE): `VOID: server down`; `NM_ON_FAILED`; `connect_verb` lower than the connections the run sent; `scan` 1 or more in a D run; a `01` line in `peers` from another IP; `HOST_HOT`.

Server-health rule: after any run whose last connection ended `silent` 1 or more, run `restart_srv.sh` before the next run. After every restart, the next run is a C run. If a C run right after a restart also ends `silent` 1 or more, stop the round: the rig cannot serve D-HU.

### CF1, CB1, CB2, CF2: controls (mode 1, no delay)

`bash $OUT/scripts/run_ctl.sh F 1` (CF1), `B 1` (CB1), `B 2` (CB2), `F 2` (CF2). Window: `<run>_start` to `<run>_end`.

PASS when all hold: `init_auto` 1 or more; `found` 1 or more; `ssl` = 1; `tput` 1 or more; `failed` = 0; `vreq` = 1; `within2s` = 0; phone `hu_connected` 1 or more. FAIL otherwise.

What each control shows: CF1 that F connects on the normal path. CB1 that the server still serves after the F delay runs, and that B connects. CB2 and CF2 that a restart cleared the server, and each is the first session on a cold server (hypothesis 2). A PASS with `vreq` = 1 means the answer came inside 2 s, so the resend path was not on trial. Record `gap_ms`.

If a B control fails with `within2s` 1 or more, the slow first answer reproduced the bug with no lever. Record it as FAIL with that note, and apply the server-health rule.

### DF1, DF2: the fix under delay (mode 0, netem 2500 ms) **(point of the round)**

`bash $OUT/scripts/run_delay.sh F 1`, then `F 2`. Windows: n0 = `_n0` to `_n1`, n1 = `_n1` to `_n2` (to the end if `_n2` is absent).

Gate, else INCONCLUSIVE: `NM_ON_OK` and `NM_OFF_OK` in `$OUT/<run>-nm.txt`; n0 `selected` 1 or more; `gap_ms` 2000 or more (the answer arrived later than B's 2 s window, so B would have resent).

PASS when all hold:

- n0: `vreq` = 1, `within2s` = 0, `ssl` = 1, `tput` 1 or more, `failed` = 0.
- n1: `ssl` = 1, `tput` 1 or more, `failed` = 0 (the next connection after the delay also answers).
- phone n0: `car_requests` = 1. Phone n1: `hu_connected` 1 or more.

FAIL otherwise. A FAIL with n0 `ssl` = 0 and n1 `silent` 1 or more means F wedges the server too: hypothesis 1 is not the whole story (look at hypotheses 2 and 4).

### DB1, DB2: the baseline under delay (positive control)

`bash $OUT/scripts/run_delay.sh B 1`, then `B 2`. Same windows. **PASS here means the baseline reproduced the wedge.**

Gate, else INCONCLUSIVE: `NM_ON_OK` and `NM_OFF_OK`; n0 `within2s` 1 or more (the lever fired: B resent). If the gate fails with `NM_ON_OK` present, re-run once with `DELAY=3500ms bash $OUT/scripts/run_delay.sh B <k>`. A second gate miss is INCONCLUSIVE, and the other DB run is skipped.

PASS when all hold:

- n0: `vreq` = 2, `selected` 1 or more, `ssl` = 0, `failed` 1 or more.
- n1: `ssl` = 0 and `silent` 1 or more. If n2 ran: `ssl` = 0 and `silent` 1 or more.
- phone n0: `car_requests` = 2. Phone n1: `hu_connected` = 0 (the kernel took the connection, and the server never served it).

FAIL when the lever fired but n0 `ssl` = 1, or n1 `ssl` = 1: the duplicate request did not wedge the server, and hypothesis 1 is refuted for this order of bytes. Record `late`: 1 or more means the phone answered both requests.

Run DB2 only if DB1 is PASS or FAIL and the time so far is under 1 h 15 of runs. After each DB run, the server-health rule calls RS1 or RS2.

### RS1, RS2: server restart (hand step)

`bash $OUT/scripts/restart_srv.sh 1` after DB1, `... 2` after DB2. Not graded. Record `restarted=`, the seconds it took and the `peers` lines (the parked `01` sockets from `HU_IP` before the restart).

### FB: cold-server fallback (only with `NETEM=no`)

Run on D-SAM in place of DF1, DF2, DB1 and DB2. The C runs stay on D-HU. Cable D-SAM. Set `H=$D_SAM` for these runs only, and give D-SAM its own backup first: `adb -s $D_SAM shell run-as com.andrerinas.headunitrevived cat shared_prefs/settings.xml > $OUT/sam-backup.xml`. D-SAM is API 19: `am` takes no `-p` (the helpers use `-n`), `logcat -G` prints usage and the capture still runs, and `logcat -c` may not clear (the markers bound every window).

Usage: `H=$D_SAM bash run_cold.sh <F|B> <k>`.

**`run_cold.sh`**
```bash
. $(dirname $0)/lib.sh
A=$1; R=FB$1$2; HC=$OUT/$R-hu.txt; PC=$OUT/$R-ph.txt
th_gate $R || exit 1
[ "$(cat $OUT/installed-$H 2>/dev/null)" = $A ] || inst $A
seed 0
cap $HC $H; cap $PC $P
mark ${R}_start; launch
waitfor $HC ${R}_start 20 'WifiLauncher: Initializing WiFi Mode: MANUAL' || echo "$R no MANUAL init line"
mark ${R}_hand
echo "HAND STEP: on D-POCO, Android Auto developer settings: Stop head unit server, then Start head unit server."
waitfor $PC ${R}_hand 900 'Network server running on port' || { echo "$R VOID: no restart seen"; }
conn $R n0
[ "$(ssl $HC ${R}_n0)" -ge 1 ] || conn $R n1
peers | tee $OUT/$R-peers.txt
mark ${R}_end; appexit; uncap $HC; uncap $PC; th_end
hcounts $HC ${R}_n0 ${R}_n1; hcounts $HC ${R}_n1 ${R}_end; pcounts $PC ${R}_n0 ${R}_n1; pcounts $PC ${R}_n1 ${R}_end
echo "gap_ms=$(gap $HC ${R}_n0 ${R}_n1 'Handshake: Version request sent' 'Handshake: the phone selected')"
```

Order: FBF1, FBB1, then FBF2 and FBB2 only if FBB1's gate missed. Each run carries its own restart, so a wedge from FBB cannot leak forward. The gates and PASS rules are those of DF and DB, with n0 and n1 of this run. `phone selected` on n0 after a restart that never printed `Network server running on port` makes the run void. Stop after two FBB runs whose gate missed: the fallback is then INCONCLUSIVE (the cold server answered inside 2 s every time).

## 8. Restore

**`restore.sh`**
```bash
. $(dirname $0)/lib.sh
exec > >(tee $OUT/restore.txt) 2>&1
[ "$NETEM" = yes ] && { nm_off; adb -s $D_HU shell tc qdisc show dev $IF | diff - $OUT/qdisc-baseline.txt && echo QDISC_BASELINE_BACK; }
adb -s $D_HU install -r -d $OUT/hu-before.apk; adb -s $D_HU shell am force-stop $PKG
$RIG_TOOLKIT/apk_identity.sh $OUT/hu-before.apk $D_HU; echo "apk_identity exit $?"
adb -s $D_HU push $OUT/hu-backup.xml /data/local/tmp/hus-settings.xml
adb -s $D_HU shell cp /data/local/tmp/hus-settings.xml /data/data/$PKG/shared_prefs/settings.xml
adb -s $D_HU shell chown $HU_OWNER /data/data/$PKG/shared_prefs/settings.xml
adb -s $D_HU shell chmod 660 /data/data/$PKG/shared_prefs/settings.xml
adb -s $D_HU shell cat /data/data/$PKG/shared_prefs/settings.xml | diff - $OUT/hu-backup.xml && echo HU_SETTINGS_BACK
adb -s $D_POCO shell settings put global stay_on_while_plugged_in $POCO_STAYON
[ "$POCO_BT" = true ] && adb -s $D_POCO shell svc bluetooth enable; sleep 3
adb -s $D_POCO shell dumpsys bluetooth_manager | grep -a -m1 -E '^ *enabled:'
echo "server listeners $(srv_up)"; peers
$RIG_TOOLKIT/rig_cleanup.sh list
```

If D-SAM ran the fallback, also push `sam-backup.xml` back with `$RIG_TOOLKIT/set_prefs_runas_host.py` or the `run-as cp` recipe in `rig-quirks/units/D-SAM.md`, and read it back (strip CRs before the compare). If `hu-before.apk` refuses to install, leave F installed and say so. Leave D-POCO's server running: a later round needs it. If it is deaf at the end, say so in Setup notes.

## 9. Do not re-run

Nothing in this thread is settled yet. Do not repeat the pr-1119 home runs, and do not re-measure the USB dongle retry (`usb-dongle-version-retry-poisons-tls`): USB keeps its three tries on F, and that path has its own rounds.

## 10. Report back

1. **DB:** the wedge reproduced in how many of the DB runs that passed the gate (n0 `vreq` = 2 and `ssl` = 0, n1 `silent` 1 or more, phone n1 `hu_connected` = 0).
2. **DF:** in how many DF runs that passed the gate F connected under the delay and on the next connection (n0 `vreq` = 1 and `ssl` = 1, n1 `ssl` = 1), with each `gap_ms`.
3. **Controls:** how many of CF1, CB1, CB2, CF2 passed, with `vreq` and `gap_ms` for each.

Also give, for each D run, the phone's n0 `car_requests` (predicted 2 on B, 1 on F) and `late`, and the `peers` lines after each wedge.

```decisive-strings
AutomationReceiver: com.andrerinas.openheadunit.ACTION_CONNECT
AutomationMarker: 
Disconnect action received.
WifiLauncher: Initializing WiFi Mode: AUTO
WifiLauncher: Initializing WiFi Mode: MANUAL
NetworkDiscovery: Starting scan...
NetworkDiscovery: Found Headunit Server on
Handshake: Version request sent
No VERSION_RESPONSE within 2s (attempt 1)
within the handshake budget
Handshake: the phone selected
SSL Handshake: discarded a late VERSION_RESPONSE
Handshake: SSL performHandshake failed.
SSL handshake complete
Handshake: SSL handshake complete
Throughput over
session state failed
session state failed (peer_silent)
the peer accepted the connection and then sent nothing at all
Head unit connected
Head unit disconnected
Car requests protocol version
Critical error
Terminating connection
CONNECTION_HANDOFF_COMPLETE
START_DUPLEX
Network server running on port
RIGMARK
```
