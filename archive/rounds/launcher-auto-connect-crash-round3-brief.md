# launcher-auto-connect-crash, round 3 brief: the wake replay drops its force after a newer start

On the transfer branch this file is `launcher-auto-connect-crash-round3-brief.md`.

## 0. Pre-flight and time budget

The round runs unattended. It has no hand step, except one operator grant if the session refuses D-MOTO's airplane mode (as in round 2). Only D-HU and D-POCO take part. D-SAM, D-HP and D-MOTO are not used. D-HU is **not** the HOME app in this round.

Run the pre-flight and read these values. Put each one in Setup notes.

```bash
HU=27870808938846; PH=4f4027e9; PKG=com.andrerinas.headunitrevived
rig_preflight.sh D_HU:wifi,bt D_POCO:wifi,bt                                               # must print PREFLIGHT OK
adb -s $PH shell settings get secure bluetooth_address                                    # expect DC:B7:2E:5E:4E:59
adb -s $HU shell dumpsys bluetooth_manager | grep -a -iA20 "Bonded devices" | grep -a -c "DC:B7:2E:5E:4E:59"   # expect 1 or more
adb -s $HU shell stat -c %U:%G /data/data/$PKG/shared_prefs /data/user_de/0/$PKG/shared_prefs   # expect u0_a176:u0_a176 twice
adb -s $PH shell dumpsys package com.google.android.projection.gearhead | grep -a -m1 versionName
adb devices | grep -c ZY22GC3BM4                                                          # D-MOTO plugged in: 1
ls -l ~/hur-wifi-test-scripts/launcher-auto-connect-crash-round2/lacc2-control.apk        # the round 2 control APK; adjust the path
md5sum ~/hur-wifi-test-scripts/launcher-auto-connect-crash-round2/lacc2-control.apk       # expect 1bab669446155a93bcf97116ef14f633
```

- If the phone's address is not `DC:B7:2E:5E:4E:59`, use the address it printed everywhere this brief writes that one.
- If D-HU has no bond to D-POCO, stop the round. Every run needs it, and pairing is a hand step.
- If a `stat` line is `root:root`, run `adb -s $HU shell chown u0_a176:u0_a176 <that directory>` and read it again.
- If the control APK is missing or its md5 differs, follow "Control, fallback" in section 1.
- If D-MOTO is plugged in, put it in airplane mode for the whole round and read it back:

```bash
adb -s ZY22GC3BM4 shell cmd connectivity airplane-mode enable; sleep 8
adb -s ZY22GC3BM4 shell dumpsys bluetooth_manager | grep -a -m1 -E '^ *state:'             # expect state: OFF
```

| Step | Wall clock |
|---|---|
| R0: one build, unit tests, identity | 35 min |
| Backups, settings, scripts, the inject check | 15 min |
| R8-PC on the control APK (3 to 5 cycles) | 20 min |
| Install the candidate, settings again | 5 min |
| R6B (1 cycle) | 6 min |
| R6 (3 cycles) | 15 min |
| R8 (5 to 8 cycles) | 35 min |
| Restore, zip, upload | 10 min |
| **Total** | **about 2 h 20 min** |

## 1. Build and baseline

| | Where | SHA | Gate |
|---|---|---|---|
| Candidate | fork `o-jcardenass/open-headunit`, branch `fix/launcher-auto-connect-crash` | `bbec49fc6573d54b6cf8b2b0688904e487dfb515` | 2783 JVM tests, 0 failures |
| Control | the round 2 control APK, not rebuilt: round 2's candidate `e5f818b8` with `control.patch` | reports `e5f818b88499-dirty` | md5 `1bab669446155a93bcf97116ef14f633` |
| Base | `main` | `145a0c762f0a87386ca63b54518ea5e861b290be` | not built |

**History was rewritten since round 2.** Round 2 ran `e5f818b8`, two commits on the base. The two commits and one new change are now one commit, and the branch was force-pushed. `e5f818b8` is no longer on the fork branch.

```bash
git fetch fork fix/launcher-auto-connect-crash
git checkout bbec49fc6573d54b6cf8b2b0688904e487dfb515
git log --oneline 145a0c762f0a87386ca63b54518ea5e861b290be..HEAD | wc -l      # 1
git log -1 --format=%s                                                         # Wireless: hold the bring-up while the unit is asleep
git status --short | wc -l                                                     # 0
```

If the scratch worktree has no `local.properties`, copy it from the main checkout, as rounds 1 and 2 did.

**Candidate build.** Run `th_wait 70` (section 5) first. Build with `build_hur_cool.sh` (or `build_hur.sh` under `--max-workers=2`). Copy the APK out of `apks/` at once, to `lacc3-candidate.apk` in the round folder. Run `run_unit_tests.sh`.

**Control.** Copy the round 2 control into the round folder: `cp ../launcher-auto-connect-crash-round2/lacc2-control.apk .`. It has neither the replay veto nor this round's new change, because it is `e5f818b8` with the veto removed, and `e5f818b8` came before the new change.

**Control, fallback** (only if the round 2 APK is missing or its md5 differs). `e5f818b8` may still be in the rig's clone from round 2:

```bash
git cat-file -e e5f818b8849904d5f26cfa76b498eca85594cbda^{commit} && echo HAVE_E5F8   # if it prints nothing: mark R8-PC UNTESTABLE, run the rest
git checkout e5f818b8849904d5f26cfa76b498eca85594cbda
git apply --check ../launcher-auto-connect-crash-round2/control.patch && git apply ../launcher-auto-connect-crash-round2/control.patch
th_wait 70; build_hur_cool.sh            # copy the APK out of apks/ at once, to lacc2-control.apk
git checkout -- app/src/main/java/com/andrerinas/openheadunit/aap/AapService.kt; git status --short | wc -l   # 0
```

A rebuilt control has a new md5. Then the DEX check below and the `ACTION_QUERY_STATE` commit are its identity. Say in Setup notes which control you used.

**R0 PASS when all hold:**

- The candidate gives 2783 tests and 0 failures, counted from the JUnit XML, with `WirelessSleepHoldTest` among them. A build or test failure stops the round.
- `md5sum lacc3-candidate.apk lacc2-control.apk` prints two different values.
- The DEX check below prints `1 1` or more for the candidate, and `0 0` for the control:

```bash
for a in lacc3-candidate.apk lacc2-control.apk; do printf '%s ' $a
  for s in 'the held wireless bring-up is dropped, because' 'started after the hold was taken'; do
    printf '%s ' $(unzip -p $a 'classes*.dex' | strings | grep -cF "$s"); done; echo; done
```

Install and identity, each time an APK goes on D-HU (template section 5: `adb install -r`, never uninstall):

```bash
adb -s $HU install -r <apk>
adb -s $HU pull $(adb -s $HU shell pm path $PKG | cut -d: -f2 | tr -d '\r') live.apk; md5sum <apk> live.apk   # the two md5s match
send ACTION_QUERY_STATE        # the reply's commit is bbec49fc6573 (candidate) or e5f818b88499-dirty (control)
```

If `install -r` of the control fails with `INSTALL_FAILED_VERSION_DOWNGRADE`, use `adb -s $HU install -r -d` (`rig-quirks/topics/tooling.md`).

## 2. What this is and why it exists

A reporter runs Open Headunit as the HOME app on a car head unit, in Native AA mode (#1035). When the ignition goes off, the unit sleeps. When the ignition comes back after 10 to 20 s, the unit does a full reboot instead of a fast resume.

The reporter's logs show this order. The unit starts to sleep, our process dies, and Android starts HOME again. The new process starts `AapService`, which arms the Native stack within about 2 s with no screen check. The arm creates a P2P group, opens the RFCOMM listeners, pokes the phone and can switch WiFi back on. The best-ranked cause is that this bring-up during the sleep stops the fast resume. The rig cannot prove that cause, because no rig unit has a fast-resume sleep. A separate reporter round tests the reboot.

The candidate holds every automatic bring-up, WiFi enable and group recreate while the screen is off. The first `SCREEN_ON` replays the held bring-up once, after a 1.5 s settle. Rounds 1 and 2 measured the hold and the replay (R5, R6 PASS; the dark recreate held in R6B).

This round tests one new change and re-grades one run:

1. **The replay drops its force after a newer start.** The hold now counts the bring-ups that start. The replay remembers the count when it takes the hold at `SCREEN_ON`. If a bring-up started during the 1.5 s settle, the replay logs one line and goes on unforced. An unforced replay against a stack that is already started with the same configuration stops at the restart refusal and rebuilds nothing. This closes the order the veto can miss: a Bluetooth auto-start that comes 0 to 1.5 s after `SCREEN_ON` into a service with no stack armed (the reporter's dark relaunch). Its own bring-up can read neither "coming up" nor "in flight" at the 1.5 s mark, so the veto lets the replay force a second rebuild under the phone's join.
2. **A stop ends the user's arming.** `WifiLauncherManager.stop()` now resets the "started by the user" flag. No run grades it directly. R6B and R6 guard that nothing else moved.
3. **R6B is re-graded under an amended clause.** Round 2's R6B failed one clause only: the re-armed join watchdog fired 30.4 s after the wake, but it logged a deferral, because the phone (WiFi off, Bluetooth on) kept a handshake in flight. A deferral is the designed answer. The clause now accepts the watchdog's own line.

## 3. What is different about this round

- **R8 is new and is the point of the round.** It puts a forced Bluetooth auto-start inside the 1.5 s settle after `SCREEN_ON`, into a service whose stack is held in the dark. Rounds 1 and 2 could not reach this order: the phone's own ACL always landed while the pre-sleep stack's attempt was in flight, so every Bluetooth auto-start was vetoed.
- **R8 sends the Bluetooth auto-start intent straight to the service.** No `AutomationReceiver` verb raises it, and the phone's own ACL cannot be timed inside 1.5 s. `rig-quirks/topics/lifecycle.md` allows this ("fire the action at the service component directly and say in the results that the receiver-side arrival was not the trigger"). `AutoStartReceiver` sends the same action to the same service, and the service runs the same branch for either source. `AapService` is not exported, so the intent needs a root shell. D-HU's adbd is uid 0, so a plain `adb shell` is root. The inject check in section 7 proves the intent lands before any run depends on it.
- **The injector runs on D-HU, not on the host.** A pushed script waits for the service's `WakeDetect: SCREEN_ON` line in the device's own logcat, then sends the intent at once. That removes the host's capture and adb delay. The `am` start still adds its own delay, so the script adds no wait of its own. The grader measures the real offset (`inject_s`).
- **R8-PC grades a different line from the plan, because of the 1.5 s re-entry window on `main`.** The plan asked the control for 2 `WifiLauncher: Initializing WiFi Mode: NATIVE` lines. In this order that cannot happen. `WifiLauncherManager.setActive` refuses a second same-configuration Native bring-up less than 1.5 s after the first (`NativeBringUpReentryPolicy`, on `main`). The injected start lands inside the settle, so the replay's forced bring-up always comes less than 1.5 s later. On the control it meets that window and logs `WifiLauncher: a Native AA bring-up was re-armed <N>ms ago, so this one is not started on top of it.` So the control's signature is that line, or a second `Initializing` line if the window is ever missed. The candidate's signature is `start_drop` then `already_init`, with no re-entry line. Section 7 grades both.
- **R7 and R7-PC do not run.** They stay INCONCLUSIVE from round 2.
- **R8-PC can come back INCONCLUSIVE.** If the injected line comes after the replay in every cycle, or reads `nothing to do`, no cycle is valid. That is a result, not a failure.
- **Keep the round 2 fixes.** No `su` on D-HU. The `th_pkg` parse. The phone's radios set and read back with `dumpsys`. D-MOTO in airplane mode. Shell markers (`smark`), because a broadcast marker would start a stopped package.
- **The discard rules of template section 4 do not apply as written.** `MATCH! Starting AapService` is the trigger in R6 and R8, and our own poke raises it in R6B. A second group is part of what R8 grades. Report every `MATCH!` count instead.
- **Log level is INFO (`log-level=2`).** Every line in section 6 is `AppLog.i`, `AppLog.w` or `AppLog.e`. The capture streams to a file, so the ring buffer does not matter.
- **Host thermal gate:** `th_gate` before each run. Re-run a run once if a timing condition failed and its throttle delta was above 0 or `thermal_max` was 90C or more.

## 4. Settings keys

The keys are the same as round 2. Write them with the app stopped, through `hu_put` (section 5). Read every key back before the launch. Write them again after each `adb install -r`, because an install can wipe `settings.xml` (`rig-quirks/topics/tooling.md`). At the start, save `settings-backup-HU.xml` and diff it against `launcher-auto-connect-crash-round2/settings-backup-HU.xml`. Put the delta in Setup notes. Do not touch the audio keys: the rig's audio settings are a deliberate worst case.

| Key | Type | Value | Why |
|---|---|---|---|
| `wifi-connection-mode` | int | `3` | Native AA |
| `log-level` | int | `2` | INFO, section 3 |
| `native-driver-selection-mode` | int | `0` | no selector countdown |
| `onboarding-version` | int | `2` | no wizard over the launch |
| `native-poke-all-paired` | boolean | `false` | poke D-POCO only |
| `native-poke-bt-macs` | string set | `DC:B7:2E:5E:4E:59` | the wake list |
| `auto-start-bt-macs` | string set | `DC:B7:2E:5E:4E:59` | the Bluetooth auto-start trigger in R6 and R8 |
| `last-connected-native-mac` | string | `DC:B7:2E:5E:4E:59` | repoint a stale target |
| `native-preferred-device-mac` | string | `DC:B7:2E:5E:4E:59` | the same |
| `native-aa-wake-damage-verdict` | int | delete | a latched verdict stands every poke down |
| `video-profile-starvation-cap` | boolean | delete | a latch that caps the session |
| `enable-car-launcher` | boolean | `false` | D-HU is not HOME in this round |

`ACTION_LOG_MARKER` is not used. `allow-external-configuration` is not needed.

```bash
MAC=DC:B7:2E:5E:4E:59
KEYS="int:wifi-connection-mode=3 int:log-level=2 int:native-driver-selection-mode=0 int:onboarding-version=2 bool:native-poke-all-paired=false set:native-poke-bt-macs=$MAC set:auto-start-bt-macs=$MAC str:last-connected-native-mac=$MAC str:native-preferred-device-mac=$MAC del:native-aa-wake-damage-verdict del:video-profile-starvation-cap bool:enable-car-launcher=false"
```

**The Bluetooth auto-start reads a second file.** `AutoStartReceiver` reads `auto-start-bt-macs` from `/data/user_de/0/com.andrerinas.headunitrevived/shared_prefs/settings_device_protected.xml`. `App.onCreate` copies it there at each launch. After the first launch on each APK, run `mirror_check`. If it does not print the MAC, mark R6, R8 and R8-PC UNTESTABLE.

## 5. Helpers and verbs

Every action on the app is a verb or a named OS-level step.

| Step | Command | Note |
|---|---|---|
| identity | `send ACTION_QUERY_STATE` | record the reply's `commit` |
| stop the service | `send ACTION_EXIT` | finishes the activities, stops `AapService` |
| launch | `adb -s $HU shell am start -n $MAIN` | template section 3, launch only. It arms the stack as an automatic bring-up, not a user one |
| marker | `smark <label>` | shell marker on both units |
| Bluetooth auto-start (R8 only) | `lacc3_inject.sh` on D-HU, below | the same action `AutoStartReceiver` sends, to the same service |

OS-level steps, allowed: `input keyevent 223` (SLEEP), `224` (WAKEUP), `3` (HOME), `wm dismiss-keyguard`, `dumpsys power`, `svc bluetooth`, `svc wifi`, `cmd connectivity airplane-mode`, `log`.

**Do not arm with `ACTION_START_WIRELESS_SCAN`.** It is a user request, and a stack the user started is exempt from the recreate hold. Every run arms with the plain launch above.

Make the folder `hur-wifi-test-scripts/launcher-auto-connect-crash-round3/`. Copy `ohu_setkeys.py` and `lacc2_lib.sh` into it, unchanged, from `launcher-auto-connect-crash-round2/`. `lacc2_lib.sh` gives `send`, `smark`, `nl`, `waitfor`, `awake`, `screen_off`, `screen_on`, `svc_up`, `mirror_check`, `hu_put`, `ph_bt_state`, `ph_wifi_state`, `phone_bt_set`, `phone_wifi_off`, `phone_restore`, `cap_start`, `cap_stop` and the `th_*` helpers. Save the four files below beside them. List all of them in Setup notes. If a script does not match the real line format, fix it, say so in Setup notes, and carry on.

**`lacc3_inject.sh`** (pushed to D-HU; it runs there as root):

```sh
#!/system/bin/sh
# lacc3_inject.sh <label> : wait for the service's SCREEN_ON line, then send the Bluetooth auto-start intent at once.
logcat -T 1 -m 1 -e 'WakeDetect: SCREEN_ON' > /dev/null
am start-foreground-service -n com.andrerinas.headunitrevived/com.andrerinas.openheadunit.aap.AapService -a com.andrerinas.openheadunit.ACTION_BT_AUTO_START
log -p w -t RIGMARK "$1-inject"
```

**`lacc3_inject_kill.sh`** (pushed to D-HU; it removes a watcher that never matched):

```sh
#!/system/bin/sh
pkill -f lacc3_inject.sh
pkill -f 'logcat -T 1 -m 1'
```

**`lacc3_extra.sh`** (source it after `lacc2_lib.sh`):

```bash
# lacc3_extra.sh : the R8 injector. Push the two device scripts once per round.
inject_push() { adb -s "$HU" push lacc3_inject.sh /data/local/tmp/lacc3_inject.sh >/dev/null
  adb -s "$HU" push lacc3_inject_kill.sh /data/local/tmp/lacc3_inject_kill.sh >/dev/null
  adb -s "$HU" shell chmod 755 /data/local/tmp/lacc3_inject.sh /data/local/tmp/lacc3_inject_kill.sh; }
# inject_arm <label> : start the device-side watcher in the background. Call it before screen_on.
inject_arm() { timeout 120 adb -s "$HU" shell sh /data/local/tmp/lacc3_inject.sh "$1" > "$OUT/$1.inject.txt" 2>&1 & INJPID=$!; sleep 1; }
# inject_done : wait for the watcher, then remove any leftover. Prints the am output.
inject_done() { wait $INJPID 2>/dev/null; adb -s "$HU" shell sh /data/local/tmp/lacc3_inject_kill.sh >/dev/null 2>&1; cat "$OUT/$1.inject.txt"; }
# wait_until <secs> <start-SECONDS> : sleep until <secs> after <start-SECONDS>.
wait_until() { local r=$(( $1 - (SECONDS - $2) )); [ $r -gt 0 ] && sleep $r; }
```

**`lacc3_grade.py`** prints one JSON object per cycle. The host grades from it. Run it as `python3 lacc3_grade.py <hu.logcat> <cycle-prefix> <phone.logcat> > <cycle-prefix>.json`. It is `lacc2_grade.py` with these changes: `recovery_any` also matches the join watchdog's line and `recovery_line` names the line; new keys `start_drop`, `already_init`, `reentry`, `qh_reentry`, `bt_veto`, `waits`, `arbiter_refuse`; `decision` also accepts `start_drop`; and the R8 fields `inject_s`, `rearm_to_inject_s`, `inject_line`, `inject_forced`, `seq_short`, `r8_order_ok`, `start_drop_to_already_init_s` and `second_forced`. The R7 fields `double_reached` and `drop_follows_force` are removed.

```python
#!/usr/bin/env python3
# lacc3_grade.py <hu-capture> <prefix> [phone-capture] : counts, times, order and checks per marker window, as JSON.
import sys, re, json
from collections import Counter
from datetime import datetime

def has(*parts): return lambda l: all(p in l for p in parts)
S = {
    'create': has('AapService creating...'),
    'hold': has('WifiLauncher: wireless bring-up held while the screen is off. '),
    'held_rec': has('WifiDirectManager: Native AA recovery (', ') held while the screen is off. '),
    'recreate': has('WifiDirectManager: Native AA recovery (', '): recreate attempt '),
    'recovery_any': lambda l: 'WifiDirectManager: Native AA recovery (' in l or 'Native AA join watchdog fired but' in l,
    'defer': has('join watchdog fired but a Bluetooth handshake or handoff is in flight'),
    'never_dialled': has('has not opened the Android Auto Bluetooth channel since this attempt started'),
    'radio_hold': has('WifiDirectManager: WiFi is off and the screen is off, so it is not switched on.'),
    'rearm': has('WakeDetect: re-arming the wireless bring-up held while the unit was asleep (force='),
    'rearm_true': has('WakeDetect: re-arming the wireless bring-up held while the unit was asleep (force=', 'force=true'),
    'dropped': has('WakeDetect: the held wireless bring-up is dropped, because '),
    'start_drop': has('WakeDetect: a wireless bring-up started after the hold was taken, so the held one replays unforced'),
    'waits': has('WakeDetect: a connection attempt is in flight, so the held forced wireless bring-up waits for it to end'),
    'session_drop': lambda l: ('WakeDetect: a session is up, so the wireless bring-up held while the unit was asleep is dropped' in l
                               or 'WakeDetect: a session came up during the settle, so the held wireless bring-up is dropped' in l),
    'screen_off': has('WakeDetect: SCREEN_OFF'),
    'screen_on': has('WakeDetect: SCREEN_ON (screen was off for '),
    'init': has('WifiLauncher: Initializing WiFi Mode: '),
    'init_native': lambda l: 'WifiLauncher: Initializing WiFi Mode: ' in l and l.rstrip().endswith('NATIVE'),
    'already_init': has('with same start-configuration is already initialized'),
    'reentry': has('WifiLauncher: a Native AA bring-up was re-armed ', 'so this one is not started on top of it.'),
    'qh_reentry': has('WifiDirectManager: a Native AA bring-up started ', 'so this one is not started on top of it.'),
    'arbiter_refuse': has('ConnectionArbiter: wireless bring-up refused while '),
    'quiet_host': has('WifiDirectManager: startNativeAaQuietHost() requested. Removing old group if any...'),
    'create_attempt': has('WifiDirectManager: Attempting createGroup for Native AA (Attempt '),
    'group_ok': has('createGroup SUCCESS!'),
    'group_formed': has('WifiDirectManager: Group formed. Owner: '),
    'busy': lambda l: ('createGroup failed (' in l and 'BUSY (System is busy' in l) or 'WifiDirectManager: Chip is BUSY, retrying in 2s...' in l,
    'listen': has('NativeAA: ACTIVELY LISTENING on Android Auto UUID'),
    'poke': has('NativeAA: Attempting active poke to device: '),
    'accept': has('NativeAA: Connection accepted from '),
    'wifi_enable': has('Attempting to enable (attempt '),
    'match': has('MATCH! Starting AapService via Bluetooth Auto-start...'),
    'bt_auto': has('AapService: Bluetooth auto-start: '),
    'bt_force': has('AapService: Bluetooth auto-start: ', 'forceRearmWireless=true'),
    'bt_veto': has('AapService: Bluetooth auto-start: nothing to do, '),
    'fgs_fail': has('Failed to start AapService from background'),
    'ssl': has('SSL handshake complete'),
    'throughput': has('Throughput over '),
    'fatal': has('FATAL EXCEPTION'),
    'our_proc': has('Process: com.andrerinas.headunitrevived'),
}
EV = ('bt_veto', 'bt_force', 'init_native', 'start_drop', 'dropped', 'already_init', 'reentry', 'qh_reentry')
TS = re.compile(r'^(\d\d-\d\d \d\d:\d\d:\d\d\.\d{3})')
CAR = re.compile(r'[VDIWEF]/CAR\.(SERVICE|SETUP|PROJECTION)\b')
WPP = re.compile(r'[VDIWEF]/GH\.WPP')
SETUP = re.compile(r'WIRELESS_SETUP_[A-Z_]+')
KEYS = ('start', 'off', 'held', 'bt-on', 'wake', 'short', 'end')

def load(p): return open(p, errors='replace').read().splitlines()
def t(line):
    m = TS.match(line)
    return datetime.strptime('2026-' + m.group(1), '%Y-%m-%d %H:%M:%S.%f') if m else None
def msg(line): return line.split('): ', 1)[-1].rstrip()
def mark(lines, label):
    for i, l in enumerate(lines):
        if 'RIGMARK' in l and l.rstrip().endswith(': ' + label): return i
    return None
def first(lines, pred, a, b):
    if a is None: return None
    for i in range(a, b):
        if pred(lines[i]): return i
    return None
def secs(lines, i, j):
    if i is None or j is None: return None
    x, y = t(lines[i]), t(lines[j])
    return round((y - x).total_seconds(), 3) if x and y else None
def windows(lines, p):
    m = {k: mark(lines, p + '-' + k) for k in KEYS}
    end = m['end'] if m['end'] is not None else len(lines)
    W = {}
    if m['start'] is not None: W['all'] = (m['start'], m['short'] if m['short'] is not None else end)
    d0 = m['off'] if m['off'] is not None else m['bt-on']
    if d0 is not None and m['wake'] is not None: W['dark'] = (d0, m['wake'])
    if m['wake'] is not None:
        W['short'] = (m['wake'], m['short'] if m['short'] is not None else end)
        W['after'] = (m['wake'], end)
    return m, W

hu, p = load(sys.argv[1]), sys.argv[2]
m, W = windows(hu, p)
out = {'prefix': p, 'markers_found': [k for k in KEYS if m[k] is not None]}
for w, (a, b) in W.items():
    out[w] = {k: sum(1 for i in range(a, b) if f(hu[i])) for k, f in S.items()}
    out[w]['bt_auto_lines'] = [msg(hu[i]) for i in range(a, b) if S['bt_auto'](hu[i])][:8]
if 'dark' in W:
    a, b = W['dark']
    h = first(hu, S['hold'], a, b)
    out['hold_before_any_arm'] = h is not None and all(first(hu, S[k], a, h) is None for k in ('init', 'create_attempt', 'group_ok', 'poke', 'listen'))
if 'after' in W:
    a, b = W['after']; sa, sb = W['short']
    son = first(hu, S['screen_on'], a, b)
    out['first_screen_on'] = msg(hu[son]) if son is not None else None
    rr = first(hu, S['rearm'], a, b)
    out['rearm_line'] = msg(hu[rr]) if rr is not None else None
    out['screen_on_to_rearm_s'] = secs(hu, son, rr)
    dec = first(hu, lambda l: S['init_native'](l) or S['dropped'](l) or S['start_drop'](l), rr, b)
    out['decision'] = None if dec is None else ('dropped' if S['dropped'](hu[dec]) else 'start_drop' if S['start_drop'](hu[dec]) else 'init_native')
    out['decision_line'] = msg(hu[dec]) if dec is not None else None
    out['screen_on_to_decision_s'] = secs(hu, son, dec)
    ssl = first(hu, S['ssl'], a, b)
    out['wake_to_ssl_s'] = secs(hu, a, ssl)
    out['throughput_after_ssl'] = ssl is not None and first(hu, S['throughput'], ssl, b) is not None
    forces = [i for i in range(a, b) if S['bt_force'](hu[i])]
    out['bt_force_offsets_s'] = [secs(hu, son, i) for i in forces] if son is not None else []
    qh = [i for i in range(sa, sb) if S['quiet_host'](hu[i])]
    gaps = [g for g in (secs(hu, qh[k], qh[k + 1]) for k in range(len(qh) - 1)) if g is not None]
    out['quiet_host_min_gap_s'] = min(gaps) if gaps else None
    rec = first(hu, S['recovery_any'], son, b)
    out['screen_on_to_recovery_s'] = secs(hu, son, rec)
    out['recovery_line'] = msg(hu[rec]) if rec is not None else None
    # R8: the injected Bluetooth auto-start, and the order of the lines after the replay line.
    inj = first(hu, S['bt_auto'], son, sb)
    out['inject_s'] = secs(hu, son, inj)
    out['rearm_to_inject_s'] = secs(hu, rr, inj)
    out['inject_line'] = msg(hu[inj]) if inj is not None else None
    out['inject_forced'] = inj is not None and S['bt_force'](hu[inj])
    seq = []
    if rr is not None:
        for i in range(rr, sb):
            k = next((k for k in EV if S[k](hu[i])), None)
            if k: seq.append([k, secs(hu, son, i)])
    out['seq_short'] = seq
    names = [k for k, _ in seq]
    it = iter(names)
    out['r8_order_ok'] = all(w in it for w in ('bt_force', 'init_native', 'start_drop', 'already_init'))
    sd = first(hu, S['start_drop'], sa, sb)
    out['start_drop_to_already_init_s'] = secs(hu, sd, first(hu, S['already_init'], sd, sb))
    tail = names[names.index('bt_force') + 1:] if 'bt_force' in names else []
    out['second_forced'] = 'reentry' in tail or tail.count('init_native') >= 2
if len(sys.argv) > 3:
    ph = load(sys.argv[3])
    pm, PW = windows(ph, p)
    out['phone'] = {w: {
        'car': sum(1 for i in range(a, b) if CAR.search(ph[i])),
        'gh_wpp': sum(1 for i in range(a, b) if WPP.search(ph[i])),
        'setup': dict(Counter(x for i in range(a, b) for x in SETUP.findall(ph[i])))} for w, (a, b) in PW.items()}
print(json.dumps(out, indent=1))
```

## 6. The lines that decide every run

Every app string below was checked with `grep -F -r` against `app/src/main` at `bbec49fc`. Each one is `AppLog.i`, `AppLog.w` or `AppLog.e`, so `log-level=2` carries all of them. `lacc3_grade.py` matches each one in the D-HU capture (`<c>.hu.logcat`), inside the marker windows below. Where the script needs two parts of one line, both parts are listed; the part between them is filled in at runtime.

| Line (fixed string, or parts of one line) | Key | What it means |
|---|---|---|
| `WakeDetect: a wireless bring-up started after the hold was taken, so the held one replays unforced` | `start_drop` | **the new change**: a start during the settle took the force off the replay |
| `with same start-configuration is already initialized` | `already_init` | the unforced replay met a started stack and rebuilt nothing. The full line is `WifiLauncher: WiFi Mode NATIVE.mode with same start-configuration is already initialized.` |
| `WifiLauncher: a Native AA bring-up was re-armed ` + `so this one is not started on top of it.` | `reentry` | a forced bring-up refused by the 1.5 s re-entry window on `main`. The control's signature in R8-PC |
| `WifiDirectManager: a Native AA bring-up started ` + `so this one is not started on top of it.` | `qh_reentry` | the same window, one layer down in the group bring-up |
| `Native AA join watchdog fired but` | in `recovery_any` | the re-armed join watchdog fired. R6B's amended clause accepts it |
| `join watchdog fired but a Bluetooth handshake or handoff is in flight` | `defer` | the watchdog put itself off for 60 s |
| `WifiDirectManager: Native AA recovery (` + `) held while the screen is off. ` | `held_rec` | the 60 s recreate held in the dark |
| `WifiDirectManager: Native AA recovery (` + `): recreate attempt ` | `recreate` | a recreate that went ahead |
| `has not opened the Android Auto Bluetooth channel since this attempt started` | `never_dialled` | the watchdog stood down because the phone never dialled |
| `WifiLauncher: wireless bring-up held while the screen is off. ` | `hold` | a bring-up held in the dark |
| `WakeDetect: re-arming the wireless bring-up held while the unit was asleep (force=` | `rearm` | the replay starts. The line ends `force=true` or `force=false`, then the trigger |
| `WakeDetect: the held wireless bring-up is dropped, because ` | `dropped` | the veto dropped the replay. The reason follows |
| `WakeDetect: a connection attempt is in flight, so the held forced wireless bring-up waits for it to end` | `waits` | the replay waits past its 1.5 s settle |
| `WakeDetect: a session is up, so the wireless bring-up held while the unit was asleep is dropped` and `WakeDetect: a session came up during the settle, so the held wireless bring-up is dropped` | `session_drop` | a session dropped the replay |
| `WakeDetect: SCREEN_OFF`, `WakeDetect: SCREEN_ON (screen was off for ` | `screen_off`, `screen_on` | the screen state, as the service saw it |
| `WifiLauncher: Initializing WiFi Mode: ` | `init`, `init_native` | a bring-up. `init_native` counts the lines that end in `NATIVE` |
| `WifiDirectManager: startNativeAaQuietHost() requested. Removing old group if any...` | `quiet_host` | a group bring-up starts |
| `WifiDirectManager: Attempting createGroup for Native AA (Attempt `, `createGroup SUCCESS!`, `WifiDirectManager: Group formed. Owner: ` | `create_attempt`, `group_ok`, `group_formed` | a group is asked for, a group is up |
| `createGroup failed (` + `BUSY (System is busy`, and `WifiDirectManager: Chip is BUSY, retrying in 2s...` | `busy` | two creates fought over the chip |
| `NativeAA: ACTIVELY LISTENING on Android Auto UUID`, `NativeAA: Attempting active poke to device: `, `NativeAA: Connection accepted from ` | `listen`, `poke`, `accept` | listeners open, a wake poke, the phone dialled |
| `Attempting to enable (attempt ` | `wifi_enable` | the app switched the head unit's WiFi on |
| `MATCH! Starting AapService via Bluetooth Auto-start...` | `match` | the receiver's Bluetooth auto-start trigger. The R8 injection does not print it |
| `AapService: Bluetooth auto-start: ` (+ `forceRearmWireless=true`, or `nothing to do, `) | `bt_auto`, `bt_force`, `bt_veto` | what the service did with an auto-start. A forced rebuild reads `BtAutoStartActions(clearUserExit=true, forceRearmWireless=true, armWirelessIfIdle=false)` |
| `ConnectionArbiter: wireless bring-up refused while ` | `arbiter_refuse` | another owner's attempt refused a bring-up. Reported only |
| `Failed to start AapService from background` | `fgs_fail` | Android refused the service start |
| `SSL handshake complete`, `Throughput over ` | `ssl`, `throughput` | a session and a picture. At INFO only the `AapSslContext` line prints, once per session |

System lines (not in `app/src`): `FATAL EXCEPTION` with `Process: com.andrerinas.headunitrevived` is a crash of ours. `RIGMARK` lines are the markers.

Phone lines, D-POCO capture (`<c>.phone.logcat`), by tag, because Gearhead's strings drift: `CAR.SERVICE`, `CAR.SETUP` or `CAR.PROJECTION` (the phone's car service ran a projection), and `GH.WPP` (its wireless handshake). The script also counts every `WIRELESS_SETUP_<NAME>` token. Those are reported, not graded.

**Windows**, all by `RIGMARK` shell markers, with `<c>` the cycle prefix:

| Window | From | To |
|---|---|---|
| `all` | `<c>-start` | `<c>-short` (or `<c>-end` if there is no `short`) |
| `dark` | `<c>-off` | `<c>-wake` |
| `short` | `<c>-wake` | `<c>-short` (30 s after `wake` in R8 and R8-PC) |
| `after` | `<c>-wake` | `<c>-end` |

## 7. Runs

Start each run with `RUN=<id>; th_gate`. Write the results file after each run. Order: the inject check and R8-PC on the control, then the candidate install, then R6B, R6, R8.

```bash
HU=27870808938846; PH=4f4027e9; MAC=DC:B7:2E:5E:4E:59
OUT=~/hur-wifi-test-scripts/launcher-auto-connect-crash-round3; mkdir -p $OUT; cd $OUT     # adjust to where hur-wifi-test-scripts/ lives
source ./lacc2_lib.sh; source ./lacc3_extra.sh; inject_push
adb -s $HU shell am force-stop $PKG
adb -s $HU shell cat /data/data/$PKG/shared_prefs/settings.xml > settings-backup-HU.xml
```

**Install the control** (section 1), then:

```bash
adb -s $HU shell am force-stop $PKG; hu_put settings-backup-HU.xml $KEYS
adb -s $HU shell am start -n $MAIN >/dev/null; sleep 15; mirror_check      # must print the MAC
send ACTION_QUERY_STATE                                                     # commit e5f818b88499-dirty
```

### Inject check (on the control, before R8-PC)

This proves that a root `am` reaches the service with the Bluetooth auto-start action. The stack is armed and the screen is on, so the service will veto or refresh. Any `AapService: Bluetooth auto-start: ` line passes the check.

```bash
cap_start INJCHK; smark INJCHK-start; L=$(nl)
adb -s $HU shell am start-foreground-service -n $PKG/com.andrerinas.openheadunit.aap.AapService -a com.andrerinas.openheadunit.ACTION_BT_AUTO_START   # record the output
waitfor 10 'AapService: Bluetooth auto-start: ' $L && echo INJECT_OK || echo INJECT_FAIL
smark INJCHK-end; cap_stop
send ACTION_EXIT; sleep 5
```

If it prints `INJECT_FAIL`, or `am` prints `Error` or `SecurityException`, mark R8-PC and R8 UNTESTABLE, run R6B and R6, and put the `am` output in Setup notes.

### The R8 cycle (used by R8-PC and R8)

The point: the service starts in the dark with no stack armed, and holds a forced bring-up. At `SCREEN_ON` the replay takes the hold and waits 1.5 s. Inside that settle, a forced Bluetooth auto-start starts the stack. At 1.5 s the replay must not force a second rebuild.

For run `<R>` (`R8-PC` or `R8`) and cycle `n`:

```bash
RUN=<R>; th_gate
adb -s $HU shell am start -n $MAIN >/dev/null; sleep 10
phone_wifi_off || echo UNTESTABLE_PHONE_WIFI
phone_bt_set off || echo UNTESTABLE_PHONE_BT
send ACTION_EXIT; sleep 3; adb -s $HU shell input keyevent 3; sleep 2; svc_up    # svc_up must print 0
cap_start <R>-c$n; smark <R>-c$n-start
smark <R>-c$n-off; screen_off                                                   # must print ASLEEP
sleep 5; smark <R>-c$n-bt-on; L=$(nl); phone_bt_set on || echo UNTESTABLE_PHONE_BT
waitfor 60 'MATCH! Starting AapService' $L || echo NO_ARRIVAL
sleep 30                                                                        # the dark soak; the phone's Bluetooth stays on
inject_arm <R>-c$n                                                              # the device-side watcher
smark <R>-c$n-wake; W=$(nl); T0=$SECONDS; screen_on
waitfor 10 'WifiLauncher: Initializing WiFi Mode: ' $W || echo NO_REARM
phone_restore                                                                   # the phone's WiFi back on
wait_until 30 $T0; smark <R>-c$n-short
waitfor 120 'SSL handshake complete' $W || echo NO_SSL
waitfor 60 'Throughput over ' $W || echo NO_PICTURE
sleep 10; smark <R>-c$n-end; cap_stop
inject_done <R>-c$n                                                             # record the am output
python3 lacc3_grade.py $CAP <R>-c$n $PCAP > <R>-c$n.json
send ACTION_EXIT; sleep 5
```

If `svc_up` prints more than 0, send `ACTION_EXIT` again and wait 5 s. If it still prints more than 0, the cycle is UNTESTABLE.

**Void cycles.** A cycle is UNTESTABLE when `screen_off` printed `NOT_ASLEEP` or a phone radio read-back failed. A cycle is INCONCLUSIVE (void) when any of these holds:

- `NO_ARRIVAL` printed, or `dark.match` is 0;
- `dark.hold` is 0, or `dark.ssl` is 1 or more;
- `inject_s` is null (the injected line never came);
- `inject_forced` is false (the injected line read `nothing to do`, or did not force);
- `rearm_to_inject_s` is 1.5 or more (the intent landed after the replay's settle).

**Per cycle, report:** `inject_s`, `rearm_to_inject_s`, `inject_line`, `seq_short`, `r8_order_ok`, `second_forced`, `start_drop_to_already_init_s`, `short.init_native`, `short.start_drop`, `short.already_init`, `short.reentry`, `short.qh_reentry`, `short.dropped`, `short.busy`, `short.match`, `quiet_host_min_gap_s`, `after.ssl`, `wake_to_ssl_s`, `throughput_after_ssl`, `dark.hold`, `dark.match`, every `bt_auto_lines` entry of `short`, `phone.dark.car`, `phone.after.car` and `phone.after.setup`, and the `am` output from `inject_done`.

### R8-PC: positive control on the control APK. 3 to 5 cycles

Run the R8 cycle with `<R>` = `R8-PC`. **Stop rule:** stop after 3 non-void cycles, or after 5 cycles in all.

**A non-void control cycle shows the second forced bring-up when all hold:**

- `second_forced` is true. That is: after the injected forced line, `seq_short` holds a `reentry` entry, or 2 or more `init_native` entries.
- `short.start_drop` is 0.

**R8-PC PASS:** at least 1 non-void cycle shows the second forced bring-up. **INCONCLUSIVE:** no non-void cycle, or none shows it. Then give `seq_short` for every cycle.

The control has neither the veto nor the new change, so `short.dropped` and `short.start_drop` are 0 by construction. A value above 0 means the wrong APK is installed: stop and check the identity.

**Install the candidate** (section 1), then:

```bash
adb -s $HU shell am force-stop $PKG; hu_put settings-backup-HU.xml $KEYS
adb -s $HU shell am start -n $MAIN >/dev/null; sleep 15; mirror_check      # must print the MAC
send ACTION_QUERY_STATE                                                     # commit bbec49fc6573
send ACTION_EXIT; sleep 5
```

### R6B: guard. The armed stack's 60 s recreate in the dark. One cycle

The same run as round 2. Only the last condition is amended. The phone's Bluetooth is on and its WiFi is off, so the phone keeps dialling and no phone can join. Do not stop the phone's Bluetooth dialling: a phone that still dials is the reporter's real case.

```bash
RUN=R6B; th_gate
phone_wifi_off || echo UNTESTABLE_PHONE_WIFI
phone_bt_set on || echo UNTESTABLE_PHONE_BT
cap_start R6B-c1; smark R6B-c1-start; L=$(nl)
adb -s $HU shell am start -n $MAIN >/dev/null
waitfor 40 'WifiDirectManager: Group formed. Owner: ' $L || echo NO_ARM
sleep 15; smark R6B-c1-off; screen_off                                         # must print ASLEEP
sleep 130                                                                      # the dark soak: room for one deferred watchdog
smark R6B-c1-wake; screen_on
sleep 55; smark R6B-c1-short
sleep 25; smark R6B-c1-end; cap_stop
python3 lacc3_grade.py $CAP R6B-c1 $PCAP > R6B-c1.json
send ACTION_EXIT; sleep 5
phone_restore
```

`NO_ARM` or `NOT_ASLEEP` makes the run UNTESTABLE.

**PASS when all hold:**

- Dark window: `screen_off` is 1 or more.
- Dark window: `group_ok` is 0, `create_attempt` is 0, `recreate` is 0 and `init` is 0.
- Dark window: `held_rec` is exactly 1.
- After window: `rearm` is exactly 1, and `rearm_line` contains `force=true`.
- After window: `decision` is `init_native` or `dropped`.
- Short window: `init_native` is 1 or less.
- All window: `group_ok` is 2 or less.
- **Amended:** if `decision` is `dropped`, `screen_on_to_recovery_s` is 75 or less. `recovery_line` starts with `WifiDirectManager: Native AA recovery (` or contains `Native AA join watchdog fired but`. Either line shows the re-armed watchdog.
- Every window: `our_proc` is 0.
- Phone: `phone.all.car` is 0.

**INCONCLUSIVE, then repeat once:** dark `held_rec` is 0 and dark `recreate` is 0, with dark `defer` or dark `never_dialled` 1 or more. The watchdog never reached the recreate. If the repeat is the same, the run is INCONCLUSIVE.

Report as numbers: dark `match`, `poke`, `accept`, `defer`, `radio_hold`, `screen_on_to_decision_s`, `decision_line`, `screen_on_to_recovery_s`, `recovery_line`, `after.start_drop`, and every `bt_auto_lines` entry of `dark` and `short`.

What a PASS would look like if the change did nothing: without the recreate hold, `recreate` and `group_ok` are 1 in the dark (round 1). Without the watchdog re-arm, no watchdog line can come after a dropped replay, so `screen_on_to_recovery_s` is null.

### R6: a Bluetooth auto-start into a stopped service while the screen is off. Three cycles

The same as round 2's R6, with one new condition. Nothing starts between the take and the replay here, so the new change must not drop the force: `start_drop` must be 0, and the replay must still read `force=true` and rebuild.

Per cycle `n` = 1, 2, 3:

```bash
RUN=R6; th_gate
adb -s $HU shell am start -n $MAIN >/dev/null; sleep 10
phone_wifi_off || echo UNTESTABLE_PHONE_WIFI
phone_bt_set off || echo UNTESTABLE_PHONE_BT
send ACTION_EXIT; sleep 3; adb -s $HU shell input keyevent 3; sleep 2; svc_up    # svc_up must print 0
cap_start R6-c$n; smark R6-c$n-start
smark R6-c$n-off; screen_off                                                   # must print ASLEEP
sleep 5; smark R6-c$n-bt-on; L=$(nl); phone_bt_set on || echo UNTESTABLE_PHONE_BT
waitfor 60 'MATCH! Starting AapService' $L || echo NO_ARRIVAL
sleep 30                                                                       # the dark soak
smark R6-c$n-wake; W=$(nl); screen_on
waitfor 10 'WifiLauncher: Initializing WiFi Mode: ' $W || echo NO_REARM
phone_restore
waitfor 120 'SSL handshake complete' $W || echo NO_SSL
waitfor 60 'Throughput over ' $W || echo NO_PICTURE
sleep 10; smark R6-c$n-end; cap_stop
python3 lacc3_grade.py $CAP R6-c$n $PCAP > R6-c$n.json
send ACTION_EXIT; sleep 5
```

If `svc_up` prints more than 0, send `ACTION_EXIT` again and wait 5 s. If it still prints more than 0, the cycle is UNTESTABLE. If `screen_off` prints `NOT_ASLEEP`, or a phone read-back failed, the cycle is UNTESTABLE. If dark `match` is 0, the cycle is INCONCLUSIVE (`NO_ARRIVAL`). Run one extra cycle for each void cycle, at most 2 extra. **Stop rule:** stop after 3 cycles with an arrival, or after 5 cycles in all.

**PASS for a cycle when all hold:**

- Dark window: `match` 1 or more, `fgs_fail` 0, `create` 1 or more.
- Dark window: `hold` is exactly 1, and `hold_before_any_arm` is true.
- Dark window: `init`, `create_attempt`, `group_ok`, `listen`, `poke`, `accept` and `wifi_enable` are each 0.
- After window: `first_screen_on` reads `WakeDetect: SCREEN_ON (screen was off for -1s)`.
- After window: `rearm` is exactly 1, `rearm_line` contains `force=true`, and `screen_on_to_rearm_s` is 2.0 or less.
- After window: `decision` is `init_native` and `screen_on_to_decision_s` is 5.0 or less.
- **New:** after window: `start_drop` is 0.
- After window: `wake_to_ssl_s` is 120 or less, and `throughput_after_ssl` is true.
- Every window: `our_proc` is 0.
- Phone: `phone.dark.car` is 0 and `phone.after.car` is 1 or more.

**R6 PASS:** every cycle with an arrival passes, and there are at least 2 of them.

Report every `bt_auto_lines` entry of `after`, `after.init_native` and `after.already_init`.

What a PASS would look like if the change did nothing: the same as a real PASS, because the new change acts only when a bring-up starts during the settle. R6 is a guard that the change stays out of the way. R8 is the run that exercises it.

### R8: a forced start inside the settle, on the candidate. 5 to 8 cycles. **This is the point of the round**

Run the R8 cycle with `<R>` = `R8`. Same void rules. **Stop rule:** stop after 5 non-void cycles, or after 8 cycles in all.

**PASS for a non-void cycle when all hold:**

- Dark window: `hold` is exactly 1, and `init`, `group_ok` and `create_attempt` are each 0.
- After window: `rearm` is exactly 1, and `rearm_line` contains `force=true`.
- `r8_order_ok` is true. That is: in `seq_short` the injected `bt_force`, then an `init_native`, then `start_drop`, then `already_init`, in that order.
- `start_drop_to_already_init_s` is 1.0 or less.
- Short window: `init_native` is exactly 1, `reentry` is 0, `dropped` is 0 and `busy` is 0.
- `quiet_host_min_gap_s` is null or 1.0 or more.
- After window: `ssl` is 1 or more, `wake_to_ssl_s` is 120 or less, and `throughput_after_ssl` is true.
- Every window: `our_proc` is 0.
- Phone: `phone.dark.car` is 0 and `phone.after.car` is 1 or more.

**R8 verdict:**

- **FAIL** when any non-void cycle fails a condition.
- **PASS** when every non-void cycle passes, there are at least 3 of them, and R8-PC is PASS.
- **INCONCLUSIVE** when every non-void cycle passes but R8-PC is not PASS, or fewer than 3 cycles are non-void. Say which.

What a PASS would look like if the change did nothing: `init_native` would still be 1, because the 1.5 s re-entry window refuses the second forced bring-up. So the count alone does not decide. The order decides: without the change the replay logs `reentry` and no `start_drop` or `already_init`, which is what R8-PC must show first.

### End of the round

```bash
send ACTION_EXIT; sleep 3; adb -s $HU shell am force-stop $PKG
adb -s $HU shell sh /data/local/tmp/lacc3_inject_kill.sh; adb -s $HU shell rm /data/local/tmp/lacc3_inject.sh /data/local/tmp/lacc3_inject_kill.sh
hu_put settings-backup-HU.xml                     # the round's starting settings, no keys changed
# if the file differs, push settings-backup-HU.xml as root the same way hu_put does (round 2 had to)
adb -s $HU shell cat /data/data/$PKG/shared_prefs/settings.xml | cmp - settings-backup-HU.xml && echo RESTORED
phone_restore                                      # expect phone wifi=enabled bt=ON
adb -s ZY22GC3BM4 shell cmd connectivity airplane-mode disable     # only if section 0 turned it on
```

Leave the candidate APK installed and say so in Setup notes. Leave D-HU's screen on.

## 8. Do not re-run

- **R7 and R7-PC** (the phone's own ACL with the screen). INCONCLUSIVE in rounds 1 and 2: on this rig the phone's ACL lands while the pre-sleep stack's attempt is in flight, so every Bluetooth auto-start is vetoed. R8 replaces them with a lever that reaches the window.
- **R5 and R5-PC** (a process death in the dark, then a wake). PASS in round 1. Their replay is `force=false`, and neither the veto nor the new change touches an unforced replay.
- **R6-PC** (a Bluetooth auto-start with the screen on). PASS in round 1. Nothing on that path changed.
- **R0 of rounds 1 and 2.** They identified `0b8e7d59` and `e5f818b8`, which are gone from the fork branch.
- Out of scope, by decision: the reboot itself (no rig unit has a fast-resume sleep; the reporter round grades it), and the auto-connect delay (in Native mode it gates no radio action).

## 9. Report back

1. **R8:** the number of non-void cycles that passed, out of the non-void cycles, and how many have `r8_order_ok` true. The range of `inject_s` and of `rearm_to_inject_s`. The largest `short.init_native` and the total `short.reentry`.
2. **R8-PC:** the number of non-void cycles with `second_forced` true, and which entry made it true (`reentry` or a second `init_native`).
3. **R6B:** `decision`, `screen_on_to_recovery_s` and `recovery_line`, and dark `held_rec`, `recreate` and `group_ok`.

Also give R6's cycles that reached `SSL handshake complete`, out of the arrival cycles, and their `after.start_drop`.

Zip the captures, the JSON files, the `.inject.txt` files and the four new scripts to `launcher-auto-connect-crash-round3-captures.zip`. Upload it to the existing release with `gh release upload rig-evidence-launcher-auto-connect-crash --repo o-jcardenass/open-headunit launcher-auto-connect-crash-round3-captures.zip`, and quote the sha256 in `launcher-auto-connect-crash-round3-results.md`.
