# launcher-auto-connect-crash, round 1 brief: the wireless bring-up waits for the screen, on D-HU as HOME

On the transfer branch this file is `launcher-auto-connect-crash-round1-brief.md`.

## 0. Pre-flight and time budget

The round runs unattended. It has no hand step. Only D-HU and D-POCO take part. D-SAM, D-HP and D-MOTO are not used.

Before R0, read these four values. Put each one in Setup notes.

```bash
HU=27870808938846; PH=4f4027e9; PKG=com.andrerinas.headunitrevived
adb -s $PH shell settings get secure bluetooth_address                                  # expect DC:B7:2E:5E:4E:59
adb -s $HU shell dumpsys bluetooth_manager | grep -a -iA20 "Bonded devices" | grep -a -c "DC:B7:2E:5E:4E:59"   # expect 1 or more
adb -s $HU shell cmd role get-role-holders android.app.role.HOME                          # the stock HOME, restored at the end
adb -s $HU shell "su -c 'stat -c %U:%G /data/data/$PKG/shared_prefs /data/user_de/0/$PKG/shared_prefs'"
```

- If the phone's address is not `DC:B7:2E:5E:4E:59`, use the address it printed everywhere this brief writes that one.
- If D-HU has no bond to D-POCO, mark R6, R6-PC and R6B UNTESTABLE (pairing is a hand step) and run R5 and R5-PC only.
- If D-MOTO is plugged in, put it in airplane mode for the whole round: `adb -s ZY22GC3BM4 shell cmd connectivity airplane-mode enable`. If it is not plugged in, say so in Setup notes.

| Step | Wall clock |
|---|---|
| R0: build, unit tests, install, identity | 30 min |
| Backups, settings, scripts | 10 min |
| R6-PC, R6 (3 cycles), R6B | 25 min |
| HOME role set and checked | 5 min |
| R5-PC (2 cycles), R5 (5 cycles) | 15 min |
| Restore, zip, upload | 10 min |
| **Total** | **about 1 h 35 min** |

## 1. Build and baseline

| | Where | SHA | Gate |
|---|---|---|---|
| Candidate | fork `o-jcardenass/open-headunit`, branch `fix/launcher-auto-connect-crash` | `0b8e7d59188c6d9358e5ab5a930723d1fb105520` | 2762 JVM tests, 0 failures |
| Base | `main` | `145a0c762f0a87386ca63b54518ea5e861b290be` | not built |

The candidate is two commits on the base. This is the first round of the thread, so no history was rewritten since a previous round. **The round is not an A/B.** Each run carries its own control.

```bash
git fetch fork fix/launcher-auto-connect-crash
git checkout 0b8e7d59188c6d9358e5ab5a930723d1fb105520
git log --oneline 145a0c762f0a87386ca63b54518ea5e861b290be..HEAD | wc -l      # 2
```

Run `th_wait 70` (section 5) first. Then start `th_watch $OUT/R0.thermal &` and build with `build_hur.sh` under `--max-workers=2`. Copy the APK out of `apks/` at once. Run `run_unit_tests.sh`.

**R0 PASS:** 2762 tests and 0 failures, counted from the JUnit XML, with `WirelessSleepHoldTest` among them. A build or test failure stops the round.

Install with `adb -s $HU install -r <named apk>`. Then check the identity three ways:

```bash
md5sum <named apk>; adb -s $HU pull $(adb -s $HU shell pm path $PKG | cut -d: -f2 | tr -d '\r') live.apk; md5sum live.apk   # the two md5s match
for s in WirelessSleepHold ScreenPower; do printf '%s ' $s; unzip -p live.apk 'classes*.dex' | strings | grep -cF $s; done  # each 1 or more
send ACTION_QUERY_STATE        # the reply's commit begins 0b8e7d59
```

## 2. What this is and why it exists

A reporter runs Open Headunit as the HOME app on a car head unit, in Native AA mode. The ignition goes off and the unit goes to sleep. When the ignition comes back after 10 to 20 s, the unit does a full reboot instead of a fast resume. The reporter thinks that an auto-connect delay of 2 s stops it.

The reporter's three logs show this order:

1. The unit starts to sleep. Bluetooth goes off, then WiFi goes off.
2. Our process dies, and Android starts HOME again at once, twice in 5 s.
3. Each new process starts `AapService`. Its `onCreate` arms the Native stack within about 2 s, with no screen check. The arm stands the station down, creates a P2P group, opens the RFCOMM listeners and pokes the phone. The second process also switches WiFi back on.
4. The next process is a cold boot (`LOCKED_BOOT_COMPLETED`).

In Native mode the auto-connect delay gates nothing, so the delay is not the fix. The best-ranked cause is that the bring-up during the sleep stops the unit's fast resume. The rig cannot prove that, because no rig unit has a fast-resume sleep. The reporter round tests the reboot itself.

**What this round proves is the mechanism of the fix on D-HU.** The candidate does three things:

- `WifiLauncherManager.setActive` holds every automatic bring-up while the screen is off. A bring-up that the user asked for still passes.
- `WifiDirectManager` does not switch WiFi on while the screen is off, unless the user started the stack.
- The screen coming on replays the held bring-up. A process that started with the screen dark never saw a `SCREEN_OFF`, so its first `SCREEN_ON` line reads `screen was off for -1s`. That line is how this round finds a **dark-start process**.

## 3. What is different about this round

- **D-HU becomes the HOME app for R5 and R5-PC only.** Section 7 sets the role and the end of the round gives it back to the stock launcher. R6, R6-PC and R6B run first, with the stock launcher as HOME.
- **Markers are shell markers, not the marker verb.** A broadcast to a killed (not force-stopped) package starts its process. In R5 that would be a relaunch of our own, in the very window the run grades. So every marker is `log -p w -t RIGMARK <label>` from the adb shell, on both units (helper `smark`). It does not touch the app.
- **The plan's R6 premise does not hold, so R6 changed.** With the stack armed and its group up, a Bluetooth auto-start does nothing: `BtAutoStartRearmPolicy` vetoes it with `a handshake is running on a group that is still up`. So it never reaches the hold. R6 now starts from a stopped service, where the auto-start forces a rebuild. R6B keeps the armed case as a guard.
- **The WiFi switch-on guard has no rig trigger.** It runs only inside a stack that was already armed when the screen went off, and only when WiFi is off at a group create. Its coverage is the JVM tests and the reporter round. Report any `WifiDirectManager: WiFi is off and the screen is off, so it is not switched on.` line that appears.
- **The reboot is not graded here.** D-HU has no fast-resume sleep. The reporter's runs (R1, R2, R4 in the plan) are a separate round with a test APK.
- **The discard rules of template section 4 apply per window, with two exceptions.** `MATCH! Starting AapService` is the trigger in R6, R6-PC and R6B. Each cycle of R5 and R6 creates a group on purpose after the wake.
- **Host thermal gate:** `th_gate` runs before each run (section 5). Report `th_report` for each run. Counts stand whatever the temperature. Re-run a run once if a timing condition failed and its throttle delta was above 0 or `thermal_max` was 90C or more.

## 4. Settings keys

Write the keys with the app stopped, through `hu_put` (section 5). Read every key back before the launch. At the start of the round, save `settings-backup-HU.xml` and diff it against the most recent D-HU backup under `hur-wifi-test-scripts/projection-teardown-and-relays-round1/`. Put the delta in Setup notes. Do not touch the audio keys: the rig's audio settings are a deliberate worst case.

| Key | Type | Value | Why |
|---|---|---|---|
| `wifi-connection-mode` | int | `3` | Native AA |
| `log-level` | int | `2` | INFO. Every decisive line in section 6 is INFO or WARN |
| `native-driver-selection-mode` | int | `0` | no selector countdown |
| `onboarding-version` | int | `2` | no wizard over the HOME relaunch |
| `native-poke-all-paired` | boolean | `false` | poke D-POCO only |
| `native-poke-bt-macs` | string set | `DC:B7:2E:5E:4E:59` | the wake list |
| `auto-start-bt-macs` | string set | `DC:B7:2E:5E:4E:59` | the R6 trigger |
| `last-connected-native-mac` | string | `DC:B7:2E:5E:4E:59` | repoint a stale target |
| `native-preferred-device-mac` | string | `DC:B7:2E:5E:4E:59` | the same |
| `native-aa-wake-damage-verdict` | int | delete | a latched verdict stands every poke down |
| `video-profile-starvation-cap` | boolean | delete | a latch that caps the session |
| `enable-car-launcher` | boolean | `false` in R6, R6-PC, R6B; `true` in R5, R5-PC | `MainActivity` turns the HOME alias on or off to match this key at each launch |

`ACTION_LOG_MARKER` is not gated, but this round does not use it. `allow-external-configuration` is not needed.

```bash
MAC=DC:B7:2E:5E:4E:59
BASEKEYS="int:wifi-connection-mode=3 int:log-level=2 int:native-driver-selection-mode=0 int:onboarding-version=2 bool:native-poke-all-paired=false set:native-poke-bt-macs=$MAC set:auto-start-bt-macs=$MAC str:last-connected-native-mac=$MAC str:native-preferred-device-mac=$MAC del:native-aa-wake-damage-verdict del:video-profile-starvation-cap"
R6KEYS="$BASEKEYS bool:enable-car-launcher=false"
R5KEYS="$BASEKEYS bool:enable-car-launcher=true"
```

**The R6 trigger reads a second file.** `AutoStartReceiver` reads `auto-start-bt-macs` from `/data/user_de/0/com.andrerinas.headunitrevived/shared_prefs/settings_device_protected.xml`. `App.onCreate` copies it there at each launch. After the first launch in section 7, run `mirror_check`. If it does not print the MAC, read the `stat` from section 0. If that directory is `root:root`, `chown` it to the app's uid and gid, launch once more and check again. If it still fails, mark R6 and R6-PC UNTESTABLE.

## 5. Helpers and verbs

Every action on the app is a verb or a named OS-level step.

| Step | Command | Note |
|---|---|---|
| identity | `send ACTION_QUERY_STATE` | record the reply's `commit` |
| stop the service | `send ACTION_EXIT` | finishes the activities, stops `AapService` |
| launch | `adb -s $HU shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity` | template section 3, launch only |
| marker | `smark <label>` | shell marker on both units, section 3 says why |

OS-level steps, allowed: `input keyevent 3` (HOME), `223` (SLEEP), `224` (WAKEUP), `wm dismiss-keyguard`, `dumpsys power`, `cmd role`, `pm enable`, `svc bluetooth`, `cmd connectivity airplane-mode`, `log`.

**Two named non-verb steps, with the reason:**

- **`kill -9` of the app process, as root.** R5 tests what a process death during the sleep leads to. No verb kills the process, and `am force-stop` is wrong here: it blocks the HOME relaunch and the service restart that R5 needs.
- **`am start -a android.intent.action.MAIN -c android.intent.category.HOME`.** It is the intent Android sends to start HOME again. R5 uses it only when Android has not started the process by itself 8 s after the kill. The results say which route started each process.

Make the folder `hur-wifi-test-scripts/launcher-auto-connect-crash-round1/`. Copy `ohu_setkeys.py` into it from `hur-wifi-test-scripts/projection-teardown-and-relays-round1/` (or `pr-1046-round1/`). Save the two files below beside it. List all three in Setup notes. If a script does not match the real line format, fix it, say so in Setup notes, and carry on.

**`lacc_lib.sh`**:

```bash
# lacc_lib.sh : source it after setting HU, PH, OUT and cd $OUT.
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
MAIN=$PKG/com.andrerinas.openheadunit.main.MainActivity
ALIAS=$PKG/com.andrerinas.openheadunit.CarLauncherAlias
send() { a=$1; shift; adb -s "$HU" shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; }
# smark <label> : a shell marker on D-HU, and on D-POCO while a phone capture runs. It never touches the app.
smark() { adb -s "$HU" shell log -p w -t RIGMARK "$1"; [ -n "$PCAPPID" ] && adb -s "$PH" shell log -p w -t RIGMARK "$1"; sleep 0.3; }
nl() { echo $(( $(wc -l < "$CAP") + 1 )); }
# waitfor <secs> <fixed-string> <from-line> : 0 when the string appears at or after <from-line>
waitfor() { local end=$((SECONDS+$1)); while [ $SECONDS -lt $end ]; do
  tail -n +"$3" "$CAP" | grep -aqF -- "$2" && return 0; sleep 0.5; done; return 1; }
apid() { adb -s "$HU" shell pidof $PKG | tr -d '\r'; }
awake() { adb -s "$HU" shell dumpsys power | grep -a -m1 -o 'mWakefulness=[A-Za-z]*'; }
# screen_off : SLEEP, then prove it. Prints ASLEEP, or NOT_ASLEEP after one retry.
screen_off() { adb -s "$HU" shell input keyevent 223; sleep 3
  awake | grep -q Awake && { adb -s "$HU" shell input keyevent 223; sleep 3; }
  awake | grep -q Awake && echo NOT_ASLEEP || echo ASLEEP; }
screen_on() { adb -s "$HU" shell input keyevent 224; sleep 1; adb -s "$HU" shell wm dismiss-keyguard; }
printf 'kill -9 $(pidof %s)\n' $PKG > lacc_kill.sh; adb -s "$HU" push lacc_kill.sh /data/local/tmp/lacc_kill.sh >/dev/null
hukill() { adb -s "$HU" shell "su -c 'sh /data/local/tmp/lacc_kill.sh'"; }
home_intent() { adb -s "$HU" shell am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null; }
svc_up() { adb -s "$HU" shell dumpsys activity services $PKG | grep -ac 'ServiceRecord.*AapService'; }
mirror_check() { adb -s "$HU" shell "su -c 'cat /data/user_de/0/$PKG/shared_prefs/settings_device_protected.xml'" | grep -aoF "$MAC"; }
# hu_put <base.xml> <spec...> : host-side edit, root copy, read back. The app must be stopped.
hu_put() { python3 ohu_setkeys.py "$1" new.xml "${@:2}" || return 1
  OWN=$(adb -s "$HU" shell "su -c 'stat -c %u:%g /data/data/$PKG'" | tr -d '\r')
  printf 'cp /data/local/tmp/new.xml /data/data/%s/shared_prefs/settings.xml\nchown %s /data/data/%s/shared_prefs/settings.xml\nchmod 660 /data/data/%s/shared_prefs/settings.xml\n' "$PKG" "$OWN" "$PKG" "$PKG" > hu_put.sh
  adb -s "$HU" push new.xml /data/local/tmp/new.xml >/dev/null; adb -s "$HU" push hu_put.sh /data/local/tmp/hu_put.sh >/dev/null
  adb -s "$HU" shell "su -c 'sh /data/local/tmp/hu_put.sh'"
  adb -s "$HU" shell "su -c 'cat /data/data/$PKG/shared_prefs/settings.xml'" | grep -aoE '(wifi-connection-mode|log-level|native-driver-selection-mode|onboarding-version|native-poke-all-paired|native-poke-bt-macs|auto-start-bt-macs|last-connected-native-mac|native-preferred-device-mac|native-aa-wake-damage-verdict|video-profile-starvation-cap|enable-car-launcher)[^/]*'; }
# Phone radios. Verify with dumpsys, never with settings get global.
phone_air_on() { adb -s "$PH" shell cmd connectivity airplane-mode enable; sleep 8
  adb -s "$PH" shell dumpsys bluetooth_manager | grep -a -m2 -iE "^ *(enabled|state):"; }
phone_bt_on() { adb -s "$PH" shell svc bluetooth enable; }
phone_air_off() { adb -s "$PH" shell cmd connectivity airplane-mode disable; sleep 2
  adb -s "$PH" shell svc wifi enable; adb -s "$PH" shell svc bluetooth enable; sleep 4
  adb -s "$PH" shell dumpsys wifi | grep -a -m1 "Wi-Fi is"; adb -s "$PH" shell dumpsys bluetooth_manager | grep -a -m2 -iE "^ *(enabled|state):"; }
# cap_start <RUN> [phone] : D-HU capture, and the D-POCO capture when the second argument is "phone".
cap_start() { CAP=$OUT/$1.hu.logcat; PCAP=; PCAPPID=
  adb -s "$HU" logcat -G 16M; adb -s "$HU" logcat -c; stdbuf -oL adb -s "$HU" logcat -v time > "$CAP" & CAPPID=$!
  if [ "$2" = phone ]; then PCAP=$OUT/$1.phone.logcat
    adb -s "$PH" logcat -G 16M; adb -s "$PH" logcat -c; stdbuf -oL adb -s "$PH" logcat -v time > "$PCAP" & PCAPPID=$!; fi
  sleep 1; }
cap_stop() { kill $CAPPID 2>/dev/null; wait $CAPPID 2>/dev/null
  [ -n "$PCAPPID" ] && { kill $PCAPPID 2>/dev/null; wait $PCAPPID 2>/dev/null; }; PCAPPID=
  ps aux | grep -c "[l]ogcat"; }                 # must print 0, else kill the leftover by its pid
# Host thermal: th_pkg prints the CPU package temperature in C, th_thr the package throttle count.
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
```

**`lacc_grade.py`** prints one JSON object per cycle. The host grades from it. Run it as `python3 lacc_grade.py <hu.logcat> <cycle-prefix> [phone.logcat] > <cycle-prefix>.json`.

```python
#!/usr/bin/env python3
# lacc_grade.py <hu-capture> <prefix> [phone-capture] : counts and intervals per marker window, as JSON.
import sys, re, json
from datetime import datetime
S = {
    'create': 'AapService creating...',
    'hold': 'WifiLauncher: wireless bring-up held while the screen is off.',
    'radio_hold': 'WifiDirectManager: WiFi is off and the screen is off, so it is not switched on.',
    'rearm': 'WakeDetect: re-arming the wireless bring-up held while the unit was asleep (force=',
    'drop': 'WakeDetect: a session is up, so the wireless bring-up held while the unit was asleep is dropped',
    'screen_off': 'WakeDetect: SCREEN_OFF',
    'screen_on': 'WakeDetect: SCREEN_ON (screen was off for ',
    'init': 'WifiLauncher: Initializing WiFi Mode',
    'init_native': 'WifiLauncher: Initializing WiFi Mode: NATIVE',
    'create_attempt': 'WifiDirectManager: Attempting createGroup',
    'group_ok': 'createGroup SUCCESS',
    'listen': 'NativeAA: ACTIVELY LISTENING',
    'poke': 'NativeAA: Attempting active poke to device',
    'accept': 'NativeAA: Connection accepted from',
    'wifi_enable': 'Attempting to enable (attempt',
    'p2p_auto': 'WifiDirectManager: P2P enabled, auto-starting Native AA quiet host',
    'match': 'MATCH! Starting AapService via Bluetooth Auto-start...',
    'bt_auto': 'AapService: Bluetooth auto-start: ',
    'fgs_fail': 'Failed to start AapService from background',
    'ssl': 'SSL handshake complete',
    'throughput': 'Throughput over',
    'fatal': 'FATAL EXCEPTION',
    'our_proc': 'Process: com.andrerinas.headunitrevived',
}
TS = re.compile(r'^(\d\d-\d\d \d\d:\d\d:\d\d\.\d{3})')
CAR = re.compile(r'[VDIWEF]/CAR\.(SERVICE|SETUP|PROJECTION)\b')
WPP = re.compile(r'[VDIWEF]/GH\.WPP')
KEYS = ('start', 'kill', 'bt-on', 'wake', 'end')

def load(p): return open(p, errors='replace').read().splitlines()
def t(line):
    m = TS.match(line)
    return datetime.strptime('2026-' + m.group(1), '%Y-%m-%d %H:%M:%S.%f') if m else None
def msg(line): return line.split('): ', 1)[-1]
def mark(lines, label):
    for i, l in enumerate(lines):
        if 'RIGMARK' in l and l.rstrip().endswith(': ' + label): return i
    return None
def first(lines, a, b, s):
    if a is None: return None
    for i in range(a, b):
        if s in lines[i]: return i
    return None
def secs(lines, i, j):
    if i is None or j is None: return None
    x, y = t(lines[i]), t(lines[j])
    return round((y - x).total_seconds(), 3) if x and y else None
def windows(lines, p):
    m = {k: mark(lines, p + '-' + k) for k in KEYS}
    a = m['kill'] if m['kill'] is not None else m['bt-on']
    end = m['end'] if m['end'] is not None else len(lines)
    if m['wake'] is not None: return m, {'dark': (a, m['wake']), 'after': (m['wake'], end)}
    return m, {'all': (a, end)}

hu, p = load(sys.argv[1]), sys.argv[2]
m, W = windows(hu, p)
out = {'prefix': p, 'markers_found': [k for k in KEYS if m[k] is not None]}
for w, (a, b) in W.items():
    if a is None: out[w] = 'NO_MARKER'; continue
    out[w] = {k: sum(1 for i in range(a, b) if s in hu[i]) for k, s in S.items()}
    out[w]['bt_auto_lines'] = [msg(hu[i]) for i in range(a, b) if S['bt_auto'] in hu[i]][:5]
    out[w]['start_proc'] = [msg(hu[i]) for i in range(a, b) if 'Start proc' in hu[i] and 'com.andrerinas.headunitrevived' in hu[i]][:5]
if 'after' in W and W['after'][0] is not None:
    a, b = W['after']
    son, rr, ini, ssl = (first(hu, a, b, S[k]) for k in ('screen_on', 'rearm', 'init_native', 'ssl'))
    out['first_screen_on'] = msg(hu[son]) if son is not None else None
    out['rearm_line'] = msg(hu[rr]) if rr is not None else None
    out['screen_on_to_rearm_s'] = secs(hu, son, rr)
    out['screen_on_to_init_native_s'] = secs(hu, son, ini)
    out['wake_to_ssl_s'] = secs(hu, a, ssl)
    out['throughput_after_ssl'] = first(hu, ssl, b, S['throughput']) is not None if ssl is not None else False
if 'all' in W and W['all'][0] is not None:
    a, b = W['all']
    ini = first(hu, a, b, S['init_native'])
    out['create_to_init_native_s'] = secs(hu, first(hu, a, b, S['create']), ini)
    out['match_to_init_native_s'] = secs(hu, first(hu, a, b, S['match']), ini)
if 'dark' in W and W['dark'][0] is not None:
    a, b = W['dark']
    h = first(hu, a, b, S['hold'])
    out['hold_before_any_arm'] = h is not None and all(first(hu, a, h, S[k]) is None for k in ('init', 'create_attempt', 'group_ok', 'poke', 'listen'))
if len(sys.argv) > 3:
    ph = load(sys.argv[3])
    pm, PW = windows(ph, p)
    out['phone'] = {w: ('NO_MARKER' if a is None else {
        'car': sum(1 for i in range(a, b) if CAR.search(ph[i])),
        'gh_wpp': sum(1 for i in range(a, b) if WPP.search(ph[i]))}) for w, (a, b) in PW.items()}
print(json.dumps(out, indent=1))
```

## 6. The lines that decide every run

Every app line below was checked with `grep -F` against `app/src/main` at `0b8e7d59`. Each one is `AppLog.i` (INFO) or `AppLog.w`, so `log-level=2` carries all of them. `lacc_grade.py` greps each one in the D-HU capture, inside the marker windows below.

| Line (fixed string) | What it means |
|---|---|
| `AapService creating...` | a new service instance, so a new process in R5 |
| `WifiLauncher: wireless bring-up held while the screen is off.` | **the fix**: the bring-up is held. Logged once per hold |
| `WakeDetect: SCREEN_ON (screen was off for -1s)` | the process never saw a `SCREEN_OFF`: a dark-start process |
| `WakeDetect: re-arming the wireless bring-up held while the unit was asleep (force=` | **the fix**: the replay. The line ends `force=true` or `force=false`, then the trigger |
| `WifiLauncher: Initializing WiFi Mode: NATIVE` | the stack is armed. The source prints `WifiLauncher: Initializing WiFi Mode: ` and appends the mode name at runtime |
| `WifiDirectManager: Attempting createGroup`, `createGroup SUCCESS` | a group is asked for, a group is up |
| `NativeAA: ACTIVELY LISTENING` | the RFCOMM listeners are open |
| `NativeAA: Attempting active poke to device` | a wake poke |
| `NativeAA: Connection accepted from` | the phone dialled the listener |
| `Attempting to enable (attempt` | the app switched the head unit's WiFi on |
| `MATCH! Starting AapService via Bluetooth Auto-start...` | the R6 trigger |
| `AapService: Bluetooth auto-start: ` | what the service did with it, for example `BtAutoStartActions(clearUserExit=true, forceRearmWireless=true, armWirelessIfIdle=false)` or `nothing to do, ...` |
| `Failed to start AapService from background` | Android refused the service start |
| `SSL handshake complete`, `Throughput over` | a session and a picture |

**Windows**, all by `RIGMARK` shell markers:

- R5 dark and R6 dark: the **dark window** is `<cycle>-kill` (R5) or `<cycle>-bt-on` (R6) up to `<cycle>-wake`. The **after window** is `<cycle>-wake` up to `<cycle>-end`.
- R5-PC, R6-PC and R6B: one window, `<cycle>-kill` or `<cycle>-bt-on` up to `<cycle>-end`.

System lines that the script also counts (they are not in `app/src`): `FATAL EXCEPTION` with `Process: com.andrerinas.headunitrevived` (a crash of ours), and `Start proc <pid>:com.andrerinas.headunitrevived/...` (who started the process: `for service`, `for activity` or `for top-activity`).

Phone lines, D-POCO capture, by tag only, because Gearhead's strings drift between builds: `CAR.SERVICE`, `CAR.SETUP` or `CAR.PROJECTION` (the phone's car service ran a projection), and `GH.WPP` (Gearhead's wireless handshake). Report D-POCO's Gearhead version in Setup notes: `adb -s $PH shell dumpsys package com.google.android.projection.gearhead | grep -a -m1 versionName`.

## 7. Runs

Start each run with `RUN=<id>; th_gate`. Write the results file after each run. Order: R6-PC, R6, R6B, then the HOME setup, then R5-PC, R5.

```bash
HU=27870808938846; PH=4f4027e9; MAC=DC:B7:2E:5E:4E:59
OUT=~/hur-wifi-test-scripts/launcher-auto-connect-crash-round1; mkdir -p $OUT; cd $OUT; source ./lacc_lib.sh   # adjust to where hur-wifi-test-scripts/ lives
adb -s $HU shell am force-stop $PKG
adb -s $HU shell "su -c 'cat /data/data/$PKG/shared_prefs/settings.xml'" > settings-backup-HU.xml
```

### R6-PC: positive control. A Bluetooth auto-start into a stopped service, screen on

Proves that the trigger reaches the bring-up and arms it at once when the screen is lit. One cycle.

```bash
RUN=R6-PC; th_gate; adb -s $HU shell am force-stop $PKG; hu_put settings-backup-HU.xml $R6KEYS
adb -s $HU shell am start -n $MAIN >/dev/null; sleep 15; mirror_check          # must print the MAC
phone_air_on; send ACTION_EXIT; sleep 3; adb -s $HU shell input keyevent 3; sleep 2; svc_up       # svc_up must print 0
cap_start $RUN phone; smark R6-PC-c1-start; smark R6-PC-c1-bt-on; L=$(nl); phone_bt_on
waitfor 60 'MATCH! Starting AapService' $L || echo NO_ARRIVAL
sleep 45; smark R6-PC-c1-end; cap_stop
python3 lacc_grade.py $CAP R6-PC-c1 $PCAP > R6-PC-c1.json
```

If `svc_up` prints more than 0, send `ACTION_EXIT` again and wait 5 s. If it still prints more than 0, the run is UNTESTABLE.

**PASS when all hold** (window `all`):

- `match` is 1 or more, and `fgs_fail` is 0. Otherwise the run is INCONCLUSIVE (no arrival, or Android refused the service start). Then R6 is INCONCLUSIVE too.
- `hold` is 0.
- `match_to_init_native_s` is 5.0 or less.
- `group_ok` is 1 or more.
- Phone: report `phone.all.gh_wpp` and `accept`. Not graded: the phone dials only if Gearhead wants to.

### R6: a Bluetooth auto-start into a stopped service while the screen is off. Three cycles

Without the fix, the arrival would start the service, arm the stack, create a group and poke the phone in the dark. With the fix, the service starts and holds, and the wake replays it as a forced rebuild.

Per cycle `n` = 1, 2, 3:

```bash
RUN=R6; th_gate
adb -s $HU shell am start -n $MAIN >/dev/null; sleep 10                                     # the app is up, then stopped below
phone_air_on; send ACTION_EXIT; sleep 3; adb -s $HU shell input keyevent 3; sleep 2; svc_up      # must print 0
cap_start R6-c$n phone; smark R6-c$n-start
screen_off                                                                                 # must print ASLEEP
sleep 5; smark R6-c$n-bt-on; L=$(nl); phone_bt_on
waitfor 60 'MATCH! Starting AapService' $L || echo NO_ARRIVAL
sleep 30                                                                                   # the dark soak
smark R6-c$n-wake; W=$(nl); screen_on
waitfor 10 'WifiLauncher: Initializing WiFi Mode: NATIVE' $W || echo NO_REARM
phone_air_off
waitfor 120 'SSL handshake complete' $W || echo NO_SSL
waitfor 60 'Throughput over' $W || echo NO_PICTURE
sleep 10; smark R6-c$n-end; cap_stop
python3 lacc_grade.py $CAP R6-c$n $PCAP > R6-c$n.json
send ACTION_EXIT; sleep 5
```

If `screen_off` prints `NOT_ASLEEP`, the cycle is UNTESTABLE. If `match` is 0 in the dark window, the cycle is INCONCLUSIVE (`NO_ARRIVAL`). Run one extra cycle for each INCONCLUSIVE cycle, at most 2 extra. **Stop rule:** stop after 3 cycles with an arrival, or after 5 cycles in all.

**PASS for a cycle when all hold:**

- Dark window: `match` 1 or more, `fgs_fail` 0, `create` 1 or more.
- Dark window: `hold` is exactly 1, and `hold_before_any_arm` is true.
- Dark window: `init`, `create_attempt`, `group_ok`, `listen`, `poke`, `accept` and `wifi_enable` are each 0.
- After window: `first_screen_on` reads `WakeDetect: SCREEN_ON (screen was off for -1s)`.
- After window: `rearm_line` contains `force=true`, and `screen_on_to_rearm_s` is 2.0 or less.
- After window: `screen_on_to_init_native_s` is 5.0 or less.
- After window: `wake_to_ssl_s` is 120 or less, and `throughput_after_ssl` is true.
- Both windows: `our_proc` is 0 (no crash of ours; `fatal` alone also counts other apps).
- Phone: `phone.dark.car` is 0 and `phone.after.car` is 1 or more. Report `phone.dark.gh_wpp` as a number.

**R6 PASS:** every cycle with an arrival passes, and there are at least 2 of them.

What a PASS would look like if the change did nothing: `hold` would be 0 and `init_native` would appear in the dark window, right after `match`. R6-PC shows that the arrival does reach the arm.

### R6B: guard. An armed stack meets Bluetooth while the screen is off. One cycle

The armed case from the plan. The auto-start policy vetoes it before the hold. This run checks that no group is rebuilt in the dark, by any path. The phone's Bluetooth is on and its WiFi is off, so the poke loop runs and no session can form.

```bash
RUN=R6B; th_gate
phone_air_on; adb -s $HU shell am start -n $MAIN >/dev/null
cap_start R6B phone; L=$(nl)
waitfor 30 'createGroup SUCCESS' $L || echo NO_ARM
sleep 15; smark R6B-c1-start; screen_off                                                    # must print ASLEEP
smark R6B-c1-bt-on; phone_bt_on; sleep 90
smark R6B-c1-end; screen_on; sleep 5; cap_stop
python3 lacc_grade.py $CAP R6B-c1 $PCAP > R6B-c1.json
send ACTION_EXIT; sleep 5
```

If `NO_ARM` printed, the run is UNTESTABLE.

**PASS when all hold** (window `all`): `group_ok` is 0, `create_attempt` is 0, `init` is 0 and `our_proc` is 0, and `phone.all.car` is 0. Report as numbers: `match`, `poke`, `accept`, `hold`, `radio_hold`, `phone.all.gh_wpp`, and every entry of `bt_auto_lines`.

### HOME setup for R5 and R5-PC

```bash
adb -s $HU shell am force-stop $PKG; hu_put settings-backup-HU.xml $R5KEYS
adb -s $HU shell am start -n $MAIN >/dev/null; sleep 10                                    # MainActivity turns the alias on
adb -s $HU shell "su -c 'pm enable $ALIAS'"                                                # the same, in case the launch did not
adb -s $HU shell cmd role add-role-holder android.app.role.HOME $PKG
adb -s $HU shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1
# must print com.andrerinas.headunitrevived/com.andrerinas.openheadunit.CarLauncherAlias
```

If `resolve-activity` prints anything else, try `adb -s $HU shell cmd package set-home-activity $ALIAS` once and read it again. If it still prints anything else, mark R5 and R5-PC UNTESTABLE.

Then make sure the stack runs lit, from a HOME process:

```bash
phone_air_on; adb -s $HU shell input keyevent 3; sleep 15
adb -s $HU shell dumpsys activity activities | grep -a -m1 topResumedActivity               # must name com.andrerinas.headunitrevived
```

D-POCO stays in airplane mode for all of R5 and R5-PC. So no phone Android Auto takes part and no phone capture is needed.

### R5-PC: positive control. A process death with the screen on. Two cycles

Proves that a relaunch arms the stack at once when the screen is lit, which is `main`'s behaviour too.

Per cycle `n` = 1, 2:

```bash
RUN=R5-PC; th_gate; cap_start R5-PC-c$n; smark R5-PC-c$n-start
OLD=$(apid); hukill; smark R5-PC-c$n-kill; K=$(nl)
waitfor 8 'AapService creating...' $K || { home_intent; smark R5-PC-c$n-home-intent; waitfor 10 'AapService creating...' $K || echo NO_RELAUNCH; }
echo "old=$OLD new=$(apid)"
sleep 20; smark R5-PC-c$n-end; cap_stop
python3 lacc_grade.py $CAP R5-PC-c$n > R5-PC-c$n.json
sleep 15
```

**PASS for a cycle when all hold** (window `all`):

- `create` is 1 or more, and the old and new pids differ. Otherwise the cycle is INCONCLUSIVE (`NO_RELAUNCH`).
- `hold` is 0, and `screen_on` is 0.
- `create_to_init_native_s` is 5.0 or less.
- `group_ok` is 1 or more.

Report `poke` as a number, and whether the `home-intent` marker appears (which route started the process).

### R5: a process death with the screen off, then a wake. Five cycles. **This is the point of the round**

It is the reporter's sequence on D-HU: the screen goes off, our process dies, Android starts it again in the dark. Without the fix, the new process arms the stack in the dark, as `main` does in the reporter's logs.

Per cycle `n` = 1 to 5. Each cycle starts lit, with the stack armed by the process that the last cycle left.

```bash
RUN=R5; th_gate; cap_start R5-c$n; smark R5-c$n-start
screen_off                                                                                  # must print ASLEEP
sleep 2; OLD=$(apid); hukill; smark R5-c$n-kill; K=$(nl)
waitfor 8 'AapService creating...' $K || { home_intent; smark R5-c$n-home-intent; waitfor 10 'AapService creating...' $K || echo NO_DARK_START; }
echo "old=$OLD new=$(apid)"
sleep 12                                                                                    # about 20 s dark in all
smark R5-c$n-wake; W=$(nl); screen_on
waitfor 10 'WifiLauncher: Initializing WiFi Mode: NATIVE' $W || echo NO_REARM
sleep 15; smark R5-c$n-end; cap_stop
python3 lacc_grade.py $CAP R5-c$n > R5-c$n.json
sleep 15
```

If `screen_off` prints `NOT_ASLEEP`, the cycle is UNTESTABLE. If `create` is 0 in the dark window, the cycle is INCONCLUSIVE (`NO_DARK_START`): no process started in the dark. Run one extra cycle for each INCONCLUSIVE cycle, at most 3 extra. **Stop rule:** stop after 5 cycles with a dark start, or after 8 cycles in all.

**PASS for a cycle when all hold:**

- Dark window: `create` 1 or more, and the old and new pids differ.
- Dark window: `hold` is exactly 1, and `hold_before_any_arm` is true.
- Dark window: `init`, `create_attempt`, `group_ok`, `listen`, `poke` and `wifi_enable` are each 0.
- After window: `first_screen_on` reads `WakeDetect: SCREEN_ON (screen was off for -1s)`.
- After window: `rearm_line` is present, and `screen_on_to_rearm_s` is 2.0 or less.
- After window: `screen_on_to_init_native_s` is 5.0 or less, and `group_ok` is 1 or more.
- Both windows: `our_proc` is 0 (no crash of ours; `fatal` alone also counts other apps).

**R5 PASS:** all 5 cycles with a dark start pass.

What a PASS would look like if the change did nothing: the dark window would carry `init_native` and `group_ok` about 2 s after `create`, as in R5-PC. If `first_screen_on` shows a real number of seconds instead of `-1s`, the process started lit. That is a setup failure for that cycle, not a PASS. Report each cycle's `force=` value, its `start_proc` lines and whether the `home-intent` marker appears.

### End of the round

```bash
adb -s $HU shell cmd role add-role-holder android.app.role.HOME <the stock holder from section 0>
adb -s $HU shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1   # the stock launcher again
send ACTION_EXIT; sleep 3; adb -s $HU shell am force-stop $PKG
hu_put settings-backup-HU.xml                     # the round's starting settings, no keys changed
# if ohu_setkeys.py refuses an empty key list, push settings-backup-HU.xml as root the same way hu_put does
phone_air_off
```

The `hu_put` writes `enable-car-launcher` back as found. The next launch then turns the alias off again if it was off. Leave the candidate APK installed and say so in Setup notes.

## 8. Do not re-run

Nothing in this thread is settled on the rig. Out of scope for this round, by decision:

- The reboot itself. No rig unit has a fast-resume sleep. The reporter round grades it.
- A variant that starts no service on a HOME relaunch while the screen is off. It is built only if the reporter round still reboots.
- The auto-connect delay. In Native mode it gates no radio action, and the code shows it.

## 9. Report back

1. **R5:** the number of dark-start cycles with 0 `init`, 0 `group_ok`, 0 `poke` and exactly 1 `hold` in the dark window, out of the dark-start cycles. Also the largest `screen_on_to_init_native_s`.
2. **R6:** the number of arrival cycles where the dark window held and the wake rebuilt with `force=true` and reached `SSL handshake complete`, out of the arrival cycles. Also the largest `wake_to_ssl_s`.
3. **R6B:** the `group_ok` count in the dark, and the `bt_auto_lines` text.

Zip the captures and the JSON files to `launcher-auto-connect-crash-round1-captures.zip`. Create the release `rig-evidence-launcher-auto-connect-crash` (first round of the thread) and quote the sha256 in the results file.
