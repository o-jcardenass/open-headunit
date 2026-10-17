# main-smoke, round 1 brief: one short session on each main path of the 3.5.0-beta4 tree

On the transfer branch this file is `main-smoke-round1-brief.md`. Results go in
`main-smoke-round1-results.md`. Evidence goes in a new release `rig-evidence-main-smoke` as the asset
`main-smoke-round1-captures.zip` (template section 7).

This is a shallow round on purpose. Each run is one session of about 2 minutes and one user exit.
There is no positive control and no repeat cycle. Do not add either.

## 0. Pre-flight and time budget

The round runs unattended. One hand step may be needed: the Android Auto "Start head unit server"
toggle on D-POCO (S4 and S3), only if the precheck reads it off. D-HU, D-POCO and D-HP take part.
D-SAM and D-MOTO are not used.

```bash
HU=27870808938846; PH=4f4027e9; HP=CNU350BGBJ; MOTO=ZY22GC3BM4; PKG=com.andrerinas.headunitrevived
rig_preflight.sh D_HU:wifi,bt D_POCO:wifi,bt                                               # must print PREFLIGHT OK
adb devices | grep -c -E "$HU|$PH|$HP"                                                     # expect 3
adb -s $PH shell settings get secure bluetooth_address                                     # expect DC:B7:2E:5E:4E:59
adb -s $HU shell dumpsys bluetooth_manager | grep -a -iA20 "Bonded devices" | grep -a -c "DC:B7:2E:5E:4E:59"   # expect 1 or more
adb -s $HU shell stat -c %U:%G /data/data/$PKG/shared_prefs /data/user_de/0/$PKG/shared_prefs   # expect u0_a176:u0_a176 twice
adb -s $HU shell dumpsys usb | grep -a -c "host_connected=false"                           # S1 premise, expect 1 or more
adb -s $PH shell dumpsys package com.google.android.projection.gearhead | grep -a -m1 versionName
adb -s $HU shell dumpsys package com.google.android.projection.gearhead | grep -a -m1 versionName
adb -s $PH shell "cat /proc/net/tcp /proc/net/tcp6" | grep -a -ci ':149D'                   # D-POCO head unit server, expect 1 or more
adb -s $PH shell pidof $PKG; adb -s $HP shell ps | grep -a -c $PKG                          # our app on D-POCO and D-HP: record
```

D-MOTO stays silent for the whole round. If it is plugged in, run this and read it back:

```bash
adb devices | grep -c $MOTO                                                               # 1 means plugged in
adb -s $MOTO shell cmd connectivity airplane-mode enable; sleep 8
adb -s $MOTO shell dumpsys bluetooth_manager | grep -a -m1 -E '^ *state:'                  # expect state: OFF
adb -s $MOTO shell svc bluetooth disable                                                  # only if it read state: ON; read it again
```

- If the phone's address is not `DC:B7:2E:5E:4E:59`, use the address it printed everywhere this brief writes that one.
- If D-HU has no bond to D-POCO, S2 and S5 are UNTESTABLE (pairing is a hand step). Run S3 and S4.
- If a `stat` line reads `root:root`, run `adb -s $HU shell chown u0_a176:u0_a176 <that directory>` and read it again.
- If the `:149D` count is 0, see S4's precheck. Do not force-stop Gearhead on D-POCO at any point.
- If our app runs on D-POCO, send it `ACTION_EXIT` (section 5, `U=$PH`) before S2.
- D-POCO is a phone in S2, S5 and S3, and a head unit in S4. A device that swaps role gets an exit, never a bare force-stop (template section 7a).

| Step | Wall clock |
|---|---|
| S0: build, unit tests, three installs, identity | 45 min |
| Backups, settings, prechecks | 15 min |
| S2, S5 (D-HU and D-POCO) | 15 min |
| S4 (D-POCO alone) | 6 min |
| S3 (D-HP and D-POCO) | 8 min |
| Restore, zip, upload | 10 min |
| **Total** | **about 1 h 40 min** |

## 1. Build and baseline

| | Where | SHA |
|---|---|---|
| Candidate | upstream `origin/main` (`andreknieriem/open-headunit`) | `7102b4283666ffcc7802e49402735e3958cd51a0` |

```bash
git fetch origin main
git checkout 7102b4283666ffcc7802e49402735e3958cd51a0
git log -1 --format=%s          # Release: changelog and version for 3.5.0-beta4
git status --short | wc -l      # 0
```

No baseline build. The candidate is the 3.5.0-beta4 release commit, `versionCode` 117. It is 69
commits above the 3.5.0-beta3 release `c901819a`. No history was rewritten.

## 2. What this is and why it exists

Thirteen branches merged into `main` since beta3. Each one had its own rig rounds, but nothing has
run the merged tree. The merges touch these areas:

- the launcher's auto-connect while the unit is asleep;
- the Self Mode VPN release;
- the navigation street name and the FPS overlay;
- the external Bluetooth module cold start;
- projection teardown;
- the home-WiFi rejoin stand-down;
- a USB service guard;
- the WPP and mic lifecycles;
- audio playback, with opt-in AAudio.

This round asks four plain questions on each main connection path:

1. Does a session form?
2. Does a picture render?
3. Does the user exit end it cleanly?
4. Does any process of ours crash or stop answering?

## 3. What is different about this round

- **The point of the round is S2**, Native AA on D-HU. It is the most used wireless path. It also
  carries most of the merged changes.
- **S1 (USB) is pre-registered UNTESTABLE.** D-HU has no USB host path (`rig-quirks/topics/tooling.md`,
  `host_connected=false`), so a phone cannot plug into it. The section 0 precheck records the premise.
  Do not build a substitute with a phone as USB host: it needs wireless adb, cable moves and a system
  permission dialog. The JVM tests in S0 cover the USB service guard.
- **S5 starts from a stopped service, not from an armed stack.**
  - A bring-up is held only when it starts while the screen is off (`WifiLauncherManager.setActive`).
  - An armed, idle stack holds nothing, so its wake prints no replay.
  - So in S5 the phone's Bluetooth arrival starts the service in the dark, as in
    `launcher-auto-connect-crash` round 3's R6. The wake then replays the held bring-up once.
- **S3 runs on D-HP only.** D-HP has no WiFi Direct and no hotspot. So `wifi-connection-mode=1` is
  the only mode it runs (template section 7a).
- **Self Mode on D-POCO needs Android Auto's head unit server.** D-POCO runs Gearhead 17.4 or later.
  On that version Self Mode connects to `127.0.0.1:5277`. The toggle has no adb route.
- **Phone capture in every run that touches Android Auto** (S2, S3, S4, S5). In S4 the head unit and
  the phone are one device, so the one full capture is both.
- **Audio is reported, not graded.** The rig's audio settings are a deliberate worst case. Do not
  change `use-aac-audio`, `audio-latency-multiplier`, `audio-queue-capacity` or `enable-audio-sink`
  on any unit. Read them back and record them. If `enable-audio-sink` reads `false` on a unit, its
  underrun count is not reachable; record that next to the count.
- **Markers are shell markers** (`log -p w -t RIGMARK <label>`) on every unit. A broadcast marker
  starts a stopped package, which would break S5's dark start.
- **Template section 4 discard rules change as follows.**
  - S2 and S5 set `auto-start-bt-macs`, so `MATCH! Starting AapService` is expected. Record its count in every run.
  - A second `SSL handshake complete` inside a run's hold window voids the run. Re-run it once.
- **Log level is INFO (`log-level=2`).** Every app line in section 6 prints at INFO or WARN.
- **Host thermal gate:** `th_gate` before every run (section 5).

## 4. Settings keys

Write settings with the app stopped, with `ms_pref.sh` (section 5). Take a fresh backup of each unit
first. Diff it against the last round's backup where one exists, and put the delta in Setup notes,
even if it is zero. Read every key back before each launch. Write the keys again after each
`adb install -r`, because an install can wipe `settings.xml` (`rig-quirks/topics/tooling.md`).

**D-HU (S2 and S5).** The same keys as `launcher-auto-connect-crash` round 3:

| Key | Type | Value | Why |
|---|---|---|---|
| `wifi-connection-mode` | int | `3` | Native AA |
| `log-level` | int | `2` | INFO |
| `native-driver-selection-mode` | int | `0` | no selector countdown |
| `onboarding-version` | int | `2` | no wizard over the launch |
| `native-poke-all-paired` | boolean | `false` | poke D-POCO only |
| `native-poke-bt-macs` | set | `DC:B7:2E:5E:4E:59` | the wake list |
| `auto-start-bt-macs` | set | `DC:B7:2E:5E:4E:59` | S5's dark start |
| `last-connected-native-mac` | string | `DC:B7:2E:5E:4E:59` | repoint a stale target |
| `native-preferred-device-mac` | string | `DC:B7:2E:5E:4E:59` | the same |
| `native-aa-wake-damage-verdict` | delete | | a latched verdict stands every poke down |
| `video-profile-starvation-cap` | delete | | a latch that caps the session |
| `enable-car-launcher` | boolean | `false` | D-HU is not HOME in this round |

```bash
adb -s $HU shell am force-stop $PKG
adb -s $HU shell cat /data/data/$PKG/shared_prefs/settings.xml > settings-backup-HU.xml
hu_pref set wifi-connection-mode int 3
hu_pref set log-level int 2
hu_pref set native-driver-selection-mode int 0
hu_pref set onboarding-version int 2
hu_pref set native-poke-all-paired boolean false
hu_pref set native-poke-bt-macs set $MAC
hu_pref set auto-start-bt-macs set $MAC
hu_pref set last-connected-native-mac string $MAC
hu_pref set native-preferred-device-mac string $MAC
hu_pref del native-aa-wake-damage-verdict
hu_pref del video-profile-starvation-cap
hu_pref set enable-car-launcher boolean false
hu_fix
```

Read back on D-HU. Each `hu_has` line must print 1, each `hu_not` line 0, and `</map>` must count 1:

```bash
hu_has '<int name="wifi-connection-mode" value="3" />'; hu_has '<int name="log-level" value="2" />'
hu_has '<int name="native-driver-selection-mode" value="0" />'; hu_has '<int name="onboarding-version" value="2" />'
hu_has '<boolean name="native-poke-all-paired" value="false" />'; hu_has '<boolean name="enable-car-launcher" value="false" />'
hu_has "<set name=\"native-poke-bt-macs\"><string>$MAC</string></set>"; hu_has "<set name=\"auto-start-bt-macs\"><string>$MAC</string></set>"
hu_has "<string name=\"last-connected-native-mac\">$MAC</string>"; hu_has "<string name=\"native-preferred-device-mac\">$MAC</string>"
hu_not native-aa-wake-damage-verdict; hu_not video-profile-starvation-cap; hu_has '</map>'
```

D-HU also needs `connection-modes` absent, or a set that holds `<string>wifi</string>`. An absent set
means every mode. Read it with `adb -s $HU shell cat /data/data/$PKG/shared_prefs/settings.xml | grep -a -A4 'name="connection-modes"'`.
If it holds no `wifi`, run `hu_pref set connection-modes set wifi; hu_fix` and say so in Setup notes.

**D-POCO as head unit (S4 only).** The same keys as `self-mode-vpn-release` round 2:

| Key | Type | Value | Why |
|---|---|---|---|
| `auto-start-self-mode` | boolean | `true` | the launch reaches `HomeFragment.startSelfMode` |
| `auto-connect-delay-seconds` | int | `0` | no wait |
| `log-level` | int | `2` | INFO |
| `onboarding-version` | int | `2` | no wizard |
| `connection-modes` | set | `self` | Self Mode only; replaces the whole set |
| `auto-connect-last-session` | delete | | Self Mode is the only auto-connect |
| `auto-connect-single-usb` | delete | | the same |
| `keep-dummy-vpn-during-session` | delete | | default false |
| `video-profile-starvation-cap` | delete | | clear a cap from earlier rounds |

```bash
U=$PH; adb -s $PH shell am force-stop $PKG
adb -s $PH shell run-as $PKG cat shared_prefs/settings.xml > settings-backup-POCO.xml
app_pref set auto-start-self-mode boolean true
app_pref set auto-connect-delay-seconds int 0
app_pref set log-level int 2
app_pref set onboarding-version int 2
app_pref set connection-modes set self
app_pref del auto-connect-last-session
app_pref del auto-connect-single-usb
app_pref del keep-dummy-vpn-during-session
app_pref del video-profile-starvation-cap
```

Read back on D-POCO. Each `app_has` line must print 1, each `app_not` line 0:

```bash
app_has '<boolean name="auto-start-self-mode" value="true" />'; app_has '<int name="auto-connect-delay-seconds" value="0" />'
app_has '<int name="log-level" value="2" />'; app_has '<int name="onboarding-version" value="2" />'
app_has '<set name="connection-modes"><string>self</string></set>'; app_has '</map>'
app_not auto-connect-last-session; app_not auto-connect-single-usb; app_not keep-dummy-vpn-during-session; app_not video-profile-starvation-cap
```

Restore D-POCO's file from `settings-backup-POCO.xml` at the end of S4 (section 7), and compare it
byte for byte.

**D-HP (S3 only).** Read these and record them. D-HP has run mode 1 in every round, so expect no write.

| Key | Expect |
|---|---|
| `wifi-connection-mode` | `1` |
| `log-level` | `2` |
| `onboarding-version` | `2` |
| `connection-modes` | absent, or a set that holds `wifi` |
| `video-profile-starvation-cap` | absent |

```bash
adb -s $HP shell run-as $PKG cat shared_prefs/settings.xml | grep -a -oE 'name="(wifi-connection-mode|log-level|onboarding-version|video-profile-starvation-cap)"[^/]*'
adb -s $HP shell run-as $PKG cat shared_prefs/settings.xml | grep -a -A3 'name="connection-modes"'
```

Android 4.2 has no `sed`, so `ms_pref.sh` cannot run on D-HP. If `wifi-connection-mode`, `log-level`
or `connection-modes` differs from the table, S3 is UNTESTABLE; record the values. A
`video-profile-starvation-cap` that is present does not stop S3. Record it, because it caps the
picture at 1280x720 and turns AAC on.

**Audio keys, every head unit:** record them with `maudio` (section 5). Never change them.

`ACTION_LOG_MARKER` is not used. `allow-external-configuration` is not needed: no run sends a verb
from `AutomationCommandPolicy.CONFIGURING`.

## 5. Helpers and verbs

Make the folder `hur-wifi-test-scripts/main-smoke-round1/` and save the two files below in it. They
need nothing from earlier rounds except `rig_thermal.sh` one folder up, and they fall back to sysfs
without it. List both files in Setup notes.

```bash
OUT=~/hur-wifi-test-scripts/main-smoke-round1; mkdir -p $OUT; cd $OUT     # adjust to where hur-wifi-test-scripts/ lives
HU=27870808938846; PH=4f4027e9; HP=CNU350BGBJ; MAC=DC:B7:2E:5E:4E:59
PKG=com.andrerinas.headunitrevived
MAIN=$PKG/com.andrerinas.openheadunit.main.MainActivity
source ./ms_lib.sh
adb -s $HU push ms_pref.sh /data/local/tmp/ms_pref.sh; adb -s $PH push ms_pref.sh /data/local/tmp/ms_pref.sh
```

**`ms_pref.sh`** (runs on the unit; template section 1's element-scoped write). It also removes a
`<set>` that Android wrote over several lines, but only where the opening tag is alone on its line.

```sh
#!/system/bin/sh
# ms_pref.sh <file> set <key> <int|boolean|string|set> <value>    or    ms_pref.sh <file> del <key>
f=$1; op=$2; k=$3; t=$4; v=$5
sed -i -E "s#<[a-z]+ name=\"$k\"[^>]*/>##g" "$f"
sed -i -E "s#<(string|int|boolean|long|float) name=\"$k\">[^<]*</(string|int|boolean|long|float)>##g" "$f"
sed -i -E "s#<set name=\"$k\">(<string>[^<]*</string>)*</set>##g" "$f"
sed -i -E "/^ *<set name=\"$k\">\$/,/^ *<\/set>\$/d" "$f"
[ "$op" = set ] || exit 0
case $t in
  string) e="<string name=\"$k\">$v</string>" ;;
  set)    e="<set name=\"$k\"><string>$v</string></set>" ;;
  *)      e="<$t name=\"$k\" value=\"$v\" />" ;;
esac
sed -i "s|</map>|$e</map>|" "$f"
```

**`ms_lib.sh`** (the host). Set `U` (the head unit serial), `P` (the phone serial; the same as `U` in
S4) and `RUN` before each run.

```bash
# ms_lib.sh : thermal gate, settings, verbs, shell markers, two-unit capture and windowed counts.
# Thermal helpers copied from projection-teardown-and-relays-round1-brief.md (ptr_lib.sh).
th_pkg() { if [ -x ../rig_thermal.sh ]; then ../rig_thermal.sh status | grep -o 'pkg=[0-9]*' | cut -d= -f2; return; fi
  for z in /sys/class/hwmon/hwmon*; do [ "$(cat $z/name 2>/dev/null)" = coretemp ] && { echo $(( $(cat $z/temp1_input) / 1000 )); return; }; done
  echo $(( $(cat /sys/class/thermal/thermal_zone0/temp) / 1000 )); }
th_thr() { cat /sys/devices/system/cpu/cpu0/thermal_throttle/package_throttle_count 2>/dev/null || echo 0; }
th_wait() { local lim=${1:-75} n=0; while [ "$(th_pkg)" -ge "$lim" ]; do n=$((n+1)); [ $n -ge 90 ] && return 1; sleep 10; done; }
th_watch() { while :; do echo "$(date +%T) pkg=$(th_pkg)C throttle_pkg=$(th_thr)"; sleep 20; done >> "$1"; }
th_gate() { th_wait 75 || { echo "$(date +%T) HOST_TOO_HOT ${RUN:-run}" | tee -a "$OUT/thermal.log"; return 3; }
  [ -n "$THPID" ] && kill $THPID 2>/dev/null; th_watch "$OUT/${RUN:-run}.thermal" & THPID=$!
  echo "$(date +%T) ${RUN:-run} start pkg=$(th_pkg)C throttle_pkg=$(th_thr)" >> "$OUT/thermal.log"; }
th_report() { awk -F'[ =C]+' '{p=$3; t=$5; if(p>m)m=p; if(NR==1)t0=t} END{print "thermal_max=" m "C throttle_delta=" t-t0}' "$OUT/$1.thermal"; }
# Settings. D-HU: root shell, then restore owner and mode. D-POCO: run-as, pushed script.
HUX=/data/data/$PKG/shared_prefs/settings.xml
hu_pref() { adb -s $HU shell sh /data/local/tmp/ms_pref.sh $HUX "$@"; }
hu_fix() { adb -s $HU shell chown u0_a176:u0_a176 $HUX; adb -s $HU shell chmod 660 $HUX; }
hu_has() { adb -s $HU shell cat $HUX | grep -acF -- "$1"; }
hu_not() { adb -s $HU shell cat $HUX | grep -acF -- "name=\"$1\""; }
app_pref() { adb -s "$U" shell run-as $PKG sh /data/local/tmp/ms_pref.sh shared_prefs/settings.xml "$@"; }
app_has() { adb -s "$U" shell run-as $PKG cat shared_prefs/settings.xml | grep -acF -- "$1"; }
app_not() { adb -s "$U" shell run-as $PKG cat shared_prefs/settings.xml | grep -acF -- "name=\"$1\""; }
# Verbs and markers.
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
msend() { a=$1; shift; adb -s "$U" shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; sleep 0.3; }
mmark() { adb -s "$U" shell log -p w -t RIGMARK "$1"; sleep 0.3
  [ "$P" != "$U" ] && { adb -s "$P" shell log -p w -t RIGMARK "$1"; sleep 0.3; }; }
# Capture: $HF is the head unit's file, $PF the phone's (the same file when U = P).
mcap_start() { HF="$OUT/$RUN.hu.txt"; PF="$OUT/$RUN.phone.txt"
  adb -s "$U" logcat -G 16M 2>/dev/null; adb -s "$U" logcat -c
  stdbuf -oL adb -s "$U" logcat -v time > "$HF" & HUPID=$!
  if [ "$P" != "$U" ]; then adb -s "$P" logcat -G 16M 2>/dev/null; adb -s "$P" logcat -c
    stdbuf -oL adb -s "$P" logcat -v time > "$PF" & PHPID=$!; else PF="$HF"; PHPID=; fi; sleep 2; }
mcap_stop() { sleep 2; kill $HUPID $PHPID 2>/dev/null; }
# Counts inside a marker window: <file> <from-label> <to-label> <string>.
mwin() { sed -n "/RIGMARK.*: $2\$/,/RIGMARK.*: $3\$/p" "$1"; }
mcnt() { mwin "$1" "$2" "$3" | grep -acF -- "$4"; }
mcntE() { mwin "$1" "$2" "$3" | grep -acE -- "$4"; }
mrend() { mwin "$1" "$2" "$3" | grep -aF 'Throughput over ' | grep -ac 'rendered=[1-9]'; }   # windows with a picture
mwait() { for i in $(seq "$1"); do grep -aqF -- "$2" "$3" && return 0; sleep 1; done; return 1; }   # <secs> <string> <file>
mmarks() { grep -ac 'RIGMARK' "$1"; }
# Unit state.
mtun() { adb -s "$U" shell ls /sys/class/net | grep -ac '^tun[0-9]'; }
m5277() { adb -s $PH shell "cat /proc/net/tcp /proc/net/tcp6" | grep -a -ci ':149D'; }
mp2p() { adb -s $HU shell dumpsys wifip2p | grep -ac "groupFormed: $1"; }    # true | false
maudio() { adb -s "$U" shell run-as $PKG cat shared_prefs/settings.xml | grep -a -oE 'name="(use-aac-audio|audio-latency-multiplier|audio-queue-capacity|enable-audio-sink)"[^/]*'; }
# S5 only.
hu_sleep() { adb -s $HU shell input keyevent 223; sleep 3; adb -s $HU shell dumpsys power | grep -a -m1 -oE 'mWakefulness=[A-Za-z]+'; }
hu_wake() { adb -s $HU shell input keyevent 224; sleep 1; adb -s $HU shell wm dismiss-keyguard; adb -s $HU shell dumpsys power | grep -a -m1 -oE 'mWakefulness=[A-Za-z]+'; }
hu_svc() { adb -s $HU shell dumpsys activity services $PKG | grep -acF 'aap.AapService}'; }
hu_mirror() { adb -s $HU shell cat /data/user_de/0/$PKG/shared_prefs/settings_device_protected.xml | grep -acF "$MAC"; }
ph_bt() { adb -s $PH shell svc bluetooth $1; sleep 5; adb -s $PH shell dumpsys bluetooth_manager | grep -a -m1 -E '^ *state:'; }   # enable | disable
```

If `app_pref` prints `No such file or directory` or `Permission denied`, run-as cannot read
`/data/local/tmp` on that unit. Then feed the script on stdin instead, for each line:
`adb -s $PH shell run-as $PKG sh -s shared_prefs/settings.xml <op> <key> [type value] < ms_pref.sh`.
Say in Setup notes which form worked.

**The crash gate, every run, on every capture of the run:**

```bash
grep -acF "Process: $PKG" <file>          # our process crashed (Android 5+); must be 0
grep -acF "ANR in $PKG" <file>            # our process stopped answering; must be 0
grep -acF 'FATAL EXCEPTION' <file>        # any crash; record. D-HP (API 17) prints no Process: line, so this must be 0 there
grep -acF 'disabled due to previous underrun' <file>   # audio underruns; record, not graded
```

**Verbs used** (all in `AutomationCommandPolicy` at `7102b428`):

| Step | Command |
|---|---|
| identity | `msend ACTION_QUERY_STATE` (the reply's `commit` must read `7102b4283666`) |
| user exit from the projection | `msend ACTION_DISCONNECT` |
| stop the service | `msend ACTION_EXIT` |
| launch | `adb -s $U shell am start -n $MAIN` (template section 3, launch only) |

OS-level steps, allowed: `input keyevent 223`, `224` and `3`, `wm dismiss-keyguard`, `svc bluetooth`,
`svc wifi`, `dumpsys`, `log`.

## 6. The lines that decide every run

Every app string below matched `git grep -F` in `app/src` at `7102b428` on 2026-10-07. Each prints at
INFO or WARN, so `log-level=2` carries it. Every grep runs inside the marker window the run names.

| Line (fixed string) | Source | Used for |
|---|---|---|
| `AutomationReceiver: ` | `AutomationReceiver` | a verb landed; a verb with no such line voids the run |
| `SSL handshake complete` | `AapSslContext` (INFO) | session up. At INFO this prints once per session; `Handshake: SSL handshake complete` is DEBUG and absent |
| `Throughput over ` | `VideoDecoder` | a 5 s window; `rendered=N` with N above 0 is a picture |
| `Media Sink Setup Request: ` | `AapControl` | the phone's codec request; record the `on channel VIDEO` line |
| `WirelessServer: Incoming connection detected from` | `WirelessServer` | the phone reached our 5288 server (Native AA) |
| `NativeAA: ACTIVELY LISTENING on Android Auto UUID` | `NativeAaHandshakeManager` | Native listeners open |
| `NativeAA: Connection accepted from ` | `NativeAaHandshakeManager` | the phone dialled us over Bluetooth |
| `createGroup SUCCESS!` | `WifiDirectManager` | a P2P group is up |
| `AapService: Native AA user exit. Stopping active launcher.` | `AapService` | the Native user exit took the launcher down |
| `the wireless teardown did not finish in` | `AapService` (WARN) | the 5 s exit bound was hit; must be 0 |
| `AapService: Disconnected.` | `AapService` | a session ended |
| `NetworkDiscovery: Found Headunit Server on ` | `NetworkDiscovery` | the sweep found the phone's 5277; record |
| `SelfMode: AA 17.4+ detected. Connecting directly to Headunit Server on 127.0.0.1:5277...` | `SelfLauncherManager` | the Self Mode route on D-POCO |
| `SelfMode: Headunit Server (127.0.0.1:5277) is NOT running.` | `SelfLauncherManager` | the server is off; must be 0 |
| `SelfMode: nothing connected within` | `SelfLauncherManager` | Self Mode missed its deadline; must be 0 |
| `DummyVpnService: tun established` | `DummyVpnService` (github flavor) | a tun came up; must be 0 in S4 |
| `VpnControl: Starting DummyVpnService (GitHub Build` | `VpnControl` (github flavor) | a VPN start; must be 0 in S4 |
| `MATCH! Starting AapService via Bluetooth Auto-start...` | `AutoStartReceiver` | the Bluetooth auto-start trigger; record everywhere, S5 needs it |
| `AapService: Bluetooth auto-start: ` | `AapService` | what the service did with an auto-start; record |
| `WifiLauncher: Initializing WiFi Mode: ` | `WifiLauncherManager` | a bring-up |
| `WifiLauncher: wireless bring-up held while the screen is off. ` | `WifiLauncherManager` | S5: a bring-up held in the dark |
| `WakeDetect: SCREEN_OFF` | `AapService` | S5: the service saw the screen go off |
| `WakeDetect: SCREEN_ON (screen was off for ` | `AapService` | S5: the service saw the screen come on |
| `WakeDetect: re-arming the wireless bring-up held while the unit was asleep (force=` | `AapService.replaySleepHold` | S5: the replay, once |

Lines that are not in `app/src`, graded by count:

| Line | Where | Used for |
|---|---|---|
| `RIGMARK` | every capture | the markers |
| `Process: com.andrerinas.headunitrevived`, `ANR in com.andrerinas.headunitrevived`, `FATAL EXCEPTION` | every capture | the crash gate |
| `disabled due to previous underrun` | head unit capture | audio underruns; recorded |
| `groupFormed: true`, `groupFormed: false` | `dumpsys wifip2p` on D-HU | the group before and after the S2 exit |
| `[VDIWEF]/CAR\.(SERVICE|SETUP|PROJECTION)` (regex) | phone capture | the phone's car service ran a projection (S2, S5) |
| `Head unit connected` | phone capture | Gearhead's 5277 server took our connection (S3, S4); seen on 17.9 in `self-mode-vpn-release` round 2 |
| `received ByeByeRequest` | phone capture | Gearhead got our exit (S3, S4); seen on 17.9 in the same round |
| `Critical error` | phone capture | Gearhead ended the session itself; must be 0 inside the hold |

## 7. Runs

Order: S0, S1 (record only), S2, S5, S4, S3. Start each run with `RUN=<id>; th_gate`, and put
`th_report <id>` in its results section. Write the results file after each run.

A run void for setup is re-run once; a second void makes it UNTESTABLE. A run is void for setup when
one of these holds:

- a verb has no `AutomationReceiver:` line;
- the reply's `commit` is wrong;
- a marker is missing. After `mcap_stop`, `mmarks "$HF"` and `mmarks "$PF"` must each equal the
  number of `mmark` calls in the run. A lower count means a capture died.

### S0: build, unit tests, identity

```bash
th_wait 70; build_hur_cool.sh                    # or build_hur.sh under --max-workers=2
cp apks/com.andrerinas.headunitrevived_*.apk $OUT/ms-main.apk; md5sum $OUT/ms-main.apk
run_unit_tests.sh
```

Install on each head unit, D-HU, D-POCO and D-HP, with `adb -s <unit> install -r $OUT/ms-main.apk`.
Use `install -r -d` only on `INSTALL_FAILED_VERSION_DOWNGRADE`. After each install:

```bash
adb -s <unit> pull $(adb -s <unit> shell pm path $PKG | cut -d: -f2 | tr -d '\r') live.apk; md5sum $OUT/ms-main.apk live.apk
U=<unit>; msend ACTION_QUERY_STATE                 # record data=
```

**PASS when all hold:**

- The build succeeds.
- The JUnit XML total has 0 failures and 0 errors. Record the total. The tree carries 2820 `@Test`
  annotations, so a total far below that means a suite did not run.
- On each of the three units, the live md5 equals `ms-main.apk`'s md5.
- On each of the three units, the reply's `commit` reads `7102b4283666` with no `-dirty`.

**FAIL** on any of these. A build or test failure stops the round.

### S1: USB, D-POCO into D-HU. Pre-registered UNTESTABLE

Do not run it. Record the section 0 count of `host_connected=false`. Mark S1 UNTESTABLE (no USB host
path on D-HU).

### S2: Native AA, D-HU and D-POCO. The point of the round

Setup, in order:

```bash
RUN=S2; th_gate; U=$HU; P=$PH
adb -s $HU shell am force-stop $PKG       # then write and read back section 4's D-HU keys
adb -s $PH shell svc wifi enable; adb -s $PH shell svc bluetooth enable; sleep 5
adb -s $PH shell dumpsys bluetooth_manager | grep -a -m1 -E '^ *state:'      # expect state: ON
adb -s $PH shell dumpsys wifi | grep -a -m1 'Wi-Fi is'                       # expect Wi-Fi is enabled
adb -s $PH shell dumpsys window | grep -a mCurrentFocus                      # if not the launcher: adb -s $PH shell input keyevent 3
maudio
```

Run:

```bash
mcap_start; mmark S2-start
adb -s $HU shell am start -n $MAIN
mwait 120 'SSL handshake complete' "$HF"; echo "ssl_wait=$?"; sleep 5
mmark S2-ssl
sleep 110
echo "p2p_true_live=$(mp2p true)"
mmark S2-exit; msend ACTION_DISCONNECT
mwait 15 'AapService: Native AA user exit. Stopping active launcher.' "$HF"; echo "exit_wait=$?"; sleep 5
echo "p2p_true_after=$(mp2p true) p2p_false_after=$(mp2p false)"
mmark S2-end; mcap_stop
msend ACTION_EXIT; sleep 3
```

If `ssl_wait` is 1, the session did not form. Grade S2 FAIL and record the last 30 app lines before
`S2-ssl`. Still run the exit and the crash gate.

**PASS when all hold:**

- `S2-start` to `S2-ssl` in `$HF`: `NativeAA: ACTIVELY LISTENING on Android Auto UUID` 1 or more,
  `WirelessServer: Incoming connection detected from` 1 or more, `SSL handshake complete` exactly 1.
- `S2-ssl` to `S2-exit` in `$HF`: `mrend` 3 or more, `SSL handshake complete` 0, `AapService: Disconnected.` 0.
- `p2p_true_live` 1 or more (the group was up during the session).
- `S2-exit` to `S2-end` in `$HF`: `AutomationReceiver: ` 1 or more, and
  `AapService: Native AA user exit. Stopping active launcher.` exactly 1.
- `S2-exit` to `S2-end` in `$HF`: `the wireless teardown did not finish in` 0.
- `p2p_true_after` 0 and `p2p_false_after` 1 or more (the exit took the group down).
- Phone: `mcntE "$PF" S2-start S2-exit '[VDIWEF]/CAR\.(SERVICE|SETUP|PROJECTION)'` 1 or more.
- Phone, `S2-ssl` to `S2-exit` in `$PF`: `Critical error` 0.
- Crash gate, `S2-start` to `S2-end`, both captures: `Process: com.andrerinas.headunitrevived` 0 and
  `ANR in com.andrerinas.headunitrevived` 0.

**FAIL** on any condition not met. Record these:

- the counts of `createGroup SUCCESS!`, `MATCH! Starting AapService via Bluetooth Auto-start...` and `NativeAA: Connection accepted from `;
- the `Media Sink Setup Request: ` line for VIDEO;
- the first and last `rendered=` values in the hold;
- the underrun count;
- the time from `S2-start` to `SSL handshake complete`.

What a PASS would look like if the exit did nothing: `p2p_true_after` would stay at 1. The
`p2p_true_live` read is the pair that shows the group was there to remove.

### S5: a wake replays the bring-up held in the dark, D-HU and D-POCO

One cycle in the shape of `launcher-auto-connect-crash` round 3's R6, with a 30 s dark soak and a user
exit. D-HU keeps section 4's keys from S2.

```bash
RUN=S5; th_gate; U=$HU; P=$PH
adb -s $HU shell am start -n $MAIN >/dev/null; sleep 10; echo "mirror=$(hu_mirror)"     # 1 or more
ph_bt disable                                                                           # expect state: OFF
msend ACTION_EXIT; sleep 3; adb -s $HU shell input keyevent 3; sleep 2; echo "svc=$(hu_svc)"   # 0
mcap_start; mmark S5-start
mmark S5-off; hu_sleep                                                                  # expect mWakefulness=Asleep or Dozing
sleep 5; mmark S5-bt-on; ph_bt enable                                                   # expect state: ON
mwait 60 'MATCH! Starting AapService via Bluetooth Auto-start...' "$HF"; echo "arrival_wait=$?"
sleep 30                                                                                # the dark soak
mmark S5-wake; hu_wake                                                                  # expect mWakefulness=Awake
mwait 120 'SSL handshake complete' "$HF"; echo "ssl_wait=$?"
sleep 60
mmark S5-exit; msend ACTION_DISCONNECT; sleep 10
mmark S5-end; mcap_stop
msend ACTION_EXIT; sleep 5
```

- If `mirror` reads 0, S5 is UNTESTABLE: the Bluetooth auto-start cannot fire.
- If `svc` reads more than 0, send `ACTION_EXIT` again and wait 5 s. If it still reads more than 0, S5 is UNTESTABLE.
- If `hu_sleep` prints `mWakefulness=Awake`, or a `ph_bt` read-back is wrong, S5 is UNTESTABLE.
- If `arrival_wait` is 1, or the dark `MATCH!` count below is 0, S5 is INCONCLUSIVE. Run it once more.

**PASS when all hold:**

- `S5-off` to `S5-wake` in `$HF`: `MATCH! Starting AapService via Bluetooth Auto-start...` 1 or more,
  `WakeDetect: SCREEN_OFF` 1 or more, and `WifiLauncher: wireless bring-up held while the screen is off. ` exactly 1.
- `S5-off` to `S5-wake` in `$HF`: `WifiLauncher: Initializing WiFi Mode: ` 0, `createGroup SUCCESS!` 0,
  and `SSL handshake complete` 0.
- `S5-wake` to `S5-exit` in `$HF`: `WakeDetect: SCREEN_ON (screen was off for ` 1 or more, and
  `WakeDetect: re-arming the wireless bring-up held while the unit was asleep (force=` exactly 1.
- That re-arm line carries `force=true`. This prints 1:
  `mwin "$HF" S5-wake S5-exit | grep -aF 'WakeDetect: re-arming the wireless bring-up held while the unit was asleep (force=' | grep -acF 'force=true'`.
- `S5-wake` to `S5-exit` in `$HF`: `WifiLauncher: Initializing WiFi Mode: ` 1 or more,
  `SSL handshake complete` exactly 1, and `mrend` 3 or more.
- `S5-exit` to `S5-end` in `$HF`: `AapService: Native AA user exit. Stopping active launcher.` exactly 1.
- Phone: `mcntE "$PF" S5-wake S5-exit '[VDIWEF]/CAR\.(SERVICE|SETUP|PROJECTION)'` 1 or more.
- Crash gate, `S5-start` to `S5-end`, both captures: `Process: com.andrerinas.headunitrevived` 0 and
  `ANR in com.andrerinas.headunitrevived` 0.

**FAIL** on any condition not met. Record every `AapService: Bluetooth auto-start: ` line in the run,
the time from `S5-wake` to `SSL handshake complete`, and the underrun count.

What a PASS would look like if the hold did nothing: the dark window would carry
`WifiLauncher: Initializing WiFi Mode: ` 1 or more and no held line. Report both dark counts.

**End of S2 and S5:**

```bash
msend ACTION_EXIT; sleep 3; adb -s $HU shell am force-stop $PKG
adb -s $HU push settings-backup-HU.xml /data/local/tmp/ms-backup-HU.xml
adb -s $HU shell cp /data/local/tmp/ms-backup-HU.xml $HUX; hu_fix
adb -s $HU shell cat $HUX | cmp - settings-backup-HU.xml && echo RESTORED_HU
adb -s $HU shell svc bluetooth disable; sleep 5
adb -s $HU shell dumpsys bluetooth_manager | grep -a -m1 -E '^ *state:'      # expect state: OFF, so D-HU cannot reach D-POCO in S4 and S3
```

### S4: Self Mode on D-POCO, the 5277 route

**Precheck.** `m5277` must read 1 or more. If it reads 0, ask a person to toggle "Start head unit
server" in Android Auto's developer settings on D-POCO. This is a hand step because Gearhead 17.4 and
later have no adb route to it. Then read it again. A second 0 makes S4 and S3 UNTESTABLE.

```bash
RUN=S4; th_gate; U=$PH; P=$PH
msend ACTION_EXIT; sleep 3; adb -s $PH shell am force-stop $PKG     # our app only, never Gearhead
# write and read back section 4's D-POCO keys
adb -s $PH shell dumpsys window | grep -a mCurrentFocus             # if not the launcher: adb -s $PH shell input keyevent 3
echo "tun_before=$(mtun)"; maudio
```

The run starts only when `tun_before` reads 0.

```bash
mcap_start; mmark S4-start
adb -s $PH shell am start -n $MAIN
mwait 60 'SSL handshake complete' "$HF"; echo "ssl_wait=$?"; sleep 5
mmark S4-ssl; echo "tun_at_ssl=$(mtun)"
sleep 110
echo "tun_at_end=$(mtun)"
mmark S4-exit; msend ACTION_DISCONNECT; sleep 10
mmark S4-end; mcap_stop
msend ACTION_EXIT; sleep 3
echo "port_after=$(m5277)"
```

**PASS when all hold** (one capture, `$HF`, which is also the phone capture):

- `S4-start` to `S4-ssl`: `SelfMode: AA 17.4+ detected. Connecting directly to Headunit Server on 127.0.0.1:5277...` exactly 1.
- `S4-start` to `S4-ssl`: `SelfMode: Headunit Server (127.0.0.1:5277) is NOT running.` 0, and `SSL handshake complete` exactly 1.
- `S4-start` to `S4-end`: `DummyVpnService: tun established` 0, and `VpnControl: Starting DummyVpnService (GitHub Build` 0.
- `tun_at_ssl` 0 and `tun_at_end` 0.
- `S4-ssl` to `S4-exit`: `mrend` 3 or more, `AapService: Disconnected.` 0, `Critical error` 0.
- Phone side, `S4-start` to `S4-exit`: `Head unit connected` 1 or more.
- `S4-exit` to `S4-end`: `AapService: Disconnected.` 1 or more, and `received ByeByeRequest` 1 or more.
- `S4-start` to `S4-end`: `SelfMode: nothing connected within` 0.
- `port_after` 1 or more (the exit left Gearhead's server able to serve S3).
- Crash gate, `S4-start` to `S4-end`: `Process: com.andrerinas.headunitrevived` 0 and `ANR in com.andrerinas.headunitrevived` 0.

**FAIL** on any condition not met. A tun at any read is a FAIL. Record the underrun count and the
`Media Sink Setup Request: ` VIDEO line.

**End of S4:**

```bash
adb -s $PH shell am force-stop $PKG
adb -s $PH push settings-backup-POCO.xml /data/local/tmp/ms-backup-POCO.xml
adb -s $PH shell run-as $PKG cp /data/local/tmp/ms-backup-POCO.xml shared_prefs/settings.xml
adb -s $PH shell run-as $PKG cat shared_prefs/settings.xml | cmp - settings-backup-POCO.xml && echo RESTORED_POCO
```

If the `cp` cannot read `/data/local/tmp`, feed the file on stdin instead:
`adb -s $PH shell run-as $PKG tee shared_prefs/settings.xml < settings-backup-POCO.xml > /dev/null`.
If `port_after` read 0, S3 is UNTESTABLE; say so and skip it.

### S3: Headunit Server mode, D-HP and D-POCO on the house LAN

**Precheck.** Both units must sit on the same LAN, and D-POCO's server must listen:

```bash
HPNET=$(adb -s $HP shell netcfg | grep -a '^wlan0' | grep -aoE '([0-9]+\.){3}' | head -1)
PHNET=$(adb -s $PH shell ip -4 -o addr show wlan0 | grep -aoE 'inet ([0-9]+\.){3}' | cut -d' ' -f2)
[ -n "$HPNET" ] && [ "$HPNET" = "$PHNET" ] && echo SAME_LAN || echo NOT_SAME_LAN
m5277                                                               # 1 or more
```

If it prints `NOT_SAME_LAN` and D-HP's `dumpsys wifi` reads `Wi-Fi is disabled`, do these steps:

1. Run `adb -s $HP shell svc wifi enable`.
2. Poll `adb -s $HP shell dumpsys wifi | grep -a -m1 'Wi-Fi is'` every 5 s for up to 40 s. D-HP takes 25 to 30 s (`rig-quirks/units/D-HP.md`).
3. Run the check again. A second `NOT_SAME_LAN` makes S3 UNTESTABLE.

```bash
RUN=S3; th_gate; U=$HP; P=$PH
msend ACTION_EXIT; sleep 3; adb -s $HP shell am force-stop $PKG
# read section 4's D-HP keys
maudio
mcap_start; mmark S3-start
adb -s $HP shell am start -n $MAIN
mwait 90 'SSL handshake complete' "$HF"; echo "ssl_wait=$?"; sleep 5
mmark S3-ssl
sleep 110
mmark S3-exit; msend ACTION_DISCONNECT; sleep 10
mmark S3-end; mcap_stop
msend ACTION_EXIT; sleep 3
```

**PASS when all hold:**

- `S3-start` to `S3-ssl` in `$HF`: `SSL handshake complete` exactly 1.
- `S3-ssl` to `S3-exit` in `$HF`: `mrend` 3 or more, `AapService: Disconnected.` 0.
- `S3-exit` to `S3-end` in `$HF`: `AutomationReceiver: ` 1 or more, and `AapService: Disconnected.` 1 or more.
- Phone, `S3-start` to `S3-ssl` in `$PF`: `Head unit connected` 1 or more.
- Phone, `S3-ssl` to `S3-exit` in `$PF`: `Critical error` 0.
- Phone, `S3-exit` to `S3-end` in `$PF`: `received ByeByeRequest` 1 or more.
- Crash gate, `S3-start` to `S3-end`: `FATAL EXCEPTION` 0 and `ANR in com.andrerinas.headunitrevived` 0 in `$HF`.
- Crash gate, `S3-start` to `S3-end`: `Process: com.andrerinas.headunitrevived` 0 in `$PF`.

**FAIL** on any condition not met. Record the `NetworkDiscovery: Found Headunit Server on ` count, the
time from `S3-start` to `SSL handshake complete`, the `Media Sink Setup Request: ` VIDEO line and the
underrun count.

### End of the round

```bash
U=$HP; msend ACTION_EXIT; sleep 3; adb -s $HP shell am force-stop $PKG
adb -s $HU shell svc bluetooth enable; sleep 5
adb -s $HU shell dumpsys bluetooth_manager | grep -a -m1 -E '^ *state:'      # expect state: ON
adb -s $PH shell svc wifi enable; adb -s $PH shell svc bluetooth enable
adb -s $HU shell rm /data/local/tmp/ms_pref.sh /data/local/tmp/ms-backup-HU.xml
adb -s $PH shell rm /data/local/tmp/ms_pref.sh /data/local/tmp/ms-backup-POCO.xml
```

If section 0 put D-MOTO in airplane mode, undo it:

```bash
adb -s $MOTO shell cmd connectivity airplane-mode disable; adb -s $MOTO shell svc bluetooth enable
adb -s $MOTO shell dumpsys bluetooth_manager | grep -a -m1 -E '^ *state:'   # expect state: ON
```

Say in Setup notes whether each unit's `settings.xml` matches its backup. Leave the candidate
installed on all three units and say so.

### Stop rule

- S0 failing stops the round.
- Each of S2 to S5 runs once. It runs a second time only after a setup void, a discard (section 3),
  or S5's `INCONCLUSIVE` arrival rule.
- No run is repeated to turn an INCONCLUSIVE or a FAIL into a PASS.

## 8. Do not re-run

- `self-mode-vpn-release` round 2, R5P2 and R5L2 (no tun on an online start, on both Self Mode
  routes): PASS. S4 is a smoke check of the merged tree, not a re-test of the fix. It does not run
  the legacy route on D-HU.
- `launcher-auto-connect-crash` round 3, R8, R8-PC, R6B and R6 (the sleep hold, its replay and the
  forced start inside the settle): PASS. S5 runs one R6-shaped cycle only, to show the merge kept it.
- Any USB run. S1 is UNTESTABLE on this rig.
- Long sessions, repeated exit cycles and audio grading. They are out of scope for a smoke round.

## 9. Report back

1. **A table, one row per run**, with these columns:
   - the verdict;
   - the `SSL handshake complete` count;
   - the count of `mrend` windows in the hold;
   - our crash count: `Process: com.andrerinas.headunitrevived` plus `ANR in com.andrerinas.headunitrevived`, plus `FATAL EXCEPTION` on D-HP;
   - the underrun count, with that unit's `enable-audio-sink` value.
2. **S2:** the time from `S2-start` to SSL, and `p2p_true_live`, `p2p_true_after` and `p2p_false_after`.
3. **S0:** the JUnit total and failure count.

Also give, in Setup notes: each unit's Gearhead `versionName`, API level and audio keys, the APK md5,
the `:149D` count before S4 and after it, which `ms_pref.sh` form worked on D-POCO, and every script
used or added.

Zip the captures, `ms_lib.sh` and `ms_pref.sh` to `main-smoke-round1-captures.zip`. Create the release
with `gh release create rig-evidence-main-smoke --repo o-jcardenass/open-headunit --notes "main-smoke rig captures, one asset per round" main-smoke-round1-captures.zip`,
and quote the sha256 in `main-smoke-round1-results.md`.
