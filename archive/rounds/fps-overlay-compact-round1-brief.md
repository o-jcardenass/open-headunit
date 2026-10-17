# fps-overlay-compact, round 1 brief: the performance overlay prints only the chosen lines, and its settings move to their own screen, on D-HU with D-POCO

One build, no baseline. Five Native AA sessions on D-HU and nine settings-screen dumps with no session. About 1 h 15 min plus the build.

## 1. Build and baseline

| | Where | SHA | Gate |
|---|---|---|---|
| Candidate | fork `o-jcardenass/open-headunit`, branch `feat/fps-overlay-compact` | `398b54b172ff78f761bb0cc938580adb5be0c7a3` | 2755 JVM tests, 0 failures |
| Base | `main` | `145a0c762f0a87386ca63b54518ea5e861b290be` | not built |

```bash
git fetch fork feat/fps-overlay-compact
git worktree add ../fps-overlay-compact-r1 fork/feat/fps-overlay-compact   # or checkout --detach in a worktree you already have
cd ../fps-overlay-compact-r1
git rev-parse HEAD                                                  # must be 398b54b172ff78f761bb0cc938580adb5be0c7a3
git rev-parse 'HEAD^{tree}'                                         # must be 98bd0d721fe0cce8b0fc30fddb6f9858e90ef0ef
git merge-base HEAD 145a0c762f0a87386ca63b54518ea5e861b290be        # must be 145a0c762f0a87386ca63b54518ea5e861b290be
git log --oneline 145a0c762f0a87386ca63b54518ea5e861b290be..HEAD | wc -l   # must be 3
```

**If the SHA or the tree hash differs, stop and ask.** This is the first round of the thread, so no history was rewritten. The third commit is a `fixup!` that carries the translations. It folds into the second commit after the rig, and the tree does not change.

A new worktree has no `local.properties`: copy it from the main checkout. Build under the thermal gate (`rig-quirks/topics/tooling.md`) with `export GRADLE_OPTS=-Dorg.gradle.workers.max=2`, as `projection-teardown-and-relays` round 3 did. Copy the APK out of `apks/` to `$OUT/cand.apk` at once. No baseline APK is needed: every condition below is new on the candidate or is graded against the candidate's own settings.

### R0. Gate

1. Run the four `git` checks above. Record each output.
2. Build the candidate. Record its md5.
3. Run `run_unit_tests.sh` with `HUR_DIR` on the candidate worktree. Count from the JUnit XML: 2755 tests, 0 failures, and `PerformanceOverlayPolicyTest` present with 25 tests.
4. Identity in the DEX. Each must count 1 or more:

```bash
for s in PerformanceOverlaySettingsFragment PerformanceOverlayField PerformanceOverlaySource entrySummary; do
  printf '%s=%s ' "$s" $(unzip -p $OUT/cand.apk 'classes*.dex' | strings | grep -cF "$s"); done; echo
```

5. The sub-screen's destination id, for the deep link in U2. Take it from the APK, or from the build's `R.txt` if `aapt2` is not on the rig:

```bash
AAPT2=$(ls -d $ANDROID_HOME/build-tools/*/aapt2 2>/dev/null | tail -1)
[ -n "$AAPT2" ] && $AAPT2 dump resources $OUT/cand.apk | grep -F 'id/performanceOverlaySettingsFragment'
find app/build/intermediates -name R.txt -path '*ithubDebug*' | xargs grep -h 'id performanceOverlaySettingsFragment' | head -1
# both print a hex id such as 0x7f0a0237; then:
DEST=$((0x7f0a0237)); echo DEST=$DEST      # use the real hex value; record DEST in Setup notes
```

6. Install with `adb -s $HU install -r $OUT/cand.apk`. Set `WANT_MD5` to the candidate's md5. Run `apk_check`. Then `send ACTION_QUERY_STATE` and record `commit`: it must start with `398b54b1`.
7. The `shared_prefs` owner on D-HU (`rig-quirks/units/D-HU.md`). U2 grades that the app wrote nothing, which a root-owned directory would hide:

```bash
adb -s $HU shell "su -c 'stat -c %U:%G /data/data/$PKG; stat -c %U:%G /data/data/$PKG/shared_prefs'"
# if the two differ: su -c 'chown <owner of the first>:<group of the first> /data/data/$PKG/shared_prefs', then stat again
```

**PASS:** the four `git` checks match; 2755 tests and 0 failures; each DEX string counts 1 or more; `DEST` found; `commit` starts with `398b54b1`; the `shared_prefs` owner is the app's (after a `chown` if needed). A build or test failure stops the round. Record the `stat` output in Setup notes even when it is correct.

## 2. What this is and why it exists

The projection's performance overlay always printed four lines: FPS, CPU (app and system), temperature and frame age. A user who wants only the FPS number could not get it, and four lines cover more of the picture than one. The settings for the overlay were also two rows in the Debug section: the "Show Performance Overlay" toggle and "Performance overlay position".

The branch does two things:

1. **The overlay prints and samples only the chosen lines** (`0e7e1a34`). A new int key `overlay-fields` holds one bit per line: FPS 1, CPU 2, TEMP 4, FRAME 8. Its default is 15, all four, so a user who changes nothing sees the same overlay. `PerformanceOverlay.attachTo` reads the set once per projection start and logs one INFO line, for example `PerformanceOverlay: fields=FPS sources=none`. A line that is off is not sampled: with CPU off the sampler reads no `/proc/stat` and no `/proc/loadavg`, with TEMP off no thermal zone. With no line chosen the overlay draws nothing at all, not an empty dark box.
2. **The settings move to their own screen** (`d126589a`). Debug keeps one row, "Performance overlay", whose value names the state: `Off`, or the chosen lines and the side, for example `FPS (left)`. The row opens a new screen with the "Show Performance Overlay" toggle, four line switches (FPS, CPU, Temperature, Frame age) and "Performance overlay position". The line switches and the position row are greyed while the toggle is off. The screen has its own Save button, and Back with a change asks before it discards.

The JVM tests prove the text rules, the sampling rule, the summary text and the stored-int round trip. Only a projection screen proves what the view draws, that an empty set draws no box, and that the settings screens show what the stored keys say. That is this round.

## 3. What is different about this round

- **The point of the round is R2**: only FPS chosen, the overlay must print exactly one line. If the change did nothing, R2 shows four lines and no `PerformanceOverlay: fields=` line at all.
- **Settings are written to `settings.xml`, never through the UI** (template section 1). The overlay and both settings screens read the stored keys, so the round sets the keys with the app stopped and reads what the app shows.
- **What the round cannot grade, and why.** Changing a value on the new screen and pressing Save, or changing one and pressing Back then Discard, needs a tap on a switch or a dialog of our app. No `AutomationReceiver` verb sets a pending value on that screen, and the template forbids a tap. The same holds for the Quick Settings overlay toggle inside the projection. These steps are not hand steps: do not run them. The engineer checks them at review. U2 grades the half that needs no tap: the screen shows the stored state, Save stays disabled, and Back with no change leaves without a dialog and writes nothing.
- **The overlay's text is read with `uiautomator dump`.** The overlay is a plain `TextView` over the projection, so the dump carries its exact text and bounds. A dump can fail with `could not get idle state` while the text changes every second. `ui_dump` tries 3 times. If all 3 fail in both dumps of a run, the host grades that run's text conditions from the screenshot `$OUT/<RUN>-d1.png` and says so in the run's section. The log conditions never depend on the dump.
- **Native AA on D-HU with D-POCO** is the only transport that reaches a picture on D-HU (`rig-quirks/topics/tooling.md`). Each session run uses the standard bring-up, `run_open` from `ohu_lib.sh`. Check D-POCO's screen is idle before each one (`rig-quirks/units/D-POCO.md`).
- **The settings runs (U1, U2) need no session.** They run in manual mode (`wifi-connection-mode=0`) with D-POCO in airplane mode, so nothing connects while the settings screen is up.
- **The rig's audio settings are a deliberate worst case.** Do not write `use-aac-audio`, `audio-latency-multiplier`, `audio-queue-capacity` or `enable-audio-sink`. Record them from the backup.
- **`hud_mirroring` is written `false`** in every run, so a screenshot reads left to right. The round's closing restore puts the found value back.
- **Report only, never graded:** whether the FPS line shows a number or `--`, the CPU and temperature values on D-HU, and SELinux denials for `stat`, `loadavg` or thermal reads. The denials are the one place a skipped read might show: with CPU on, Android 14 may log an `avc: denied` for `/proc/stat` each second; with CPU off it should not. A `dontaudit` rule can hide them, so a count of 0 in both runs proves nothing.
- **Never run adb calls in parallel against one unit.**

### Units

| Unit | Role |
|---|---|
| D-HU | head unit, rooted, the unit under test |
| D-POCO | phone for every session run, and its capture |

D-MOTO and the tablets are not used. Keep D-MOTO's radios off or unplug it, so it cannot take D-HU's hands-free slot.

## 4. Settings keys

Write with the app stopped, through `hu_put` (root on D-HU), and read back before every launch. Back up D-HU's `settings.xml` at the start of the round as `settings-backup-HU.xml` and diff it against the last round that used D-HU; state the delta in Setup notes.

| Key | Type | Session runs (R1 to R5) | Settings runs (U1, U2) | Note |
|---|---|---|---|---|
| `wifi-connection-mode` | int | `3` | `0` | Native AA; manual for the settings runs |
| `log-level` | int | `2` | `2` | INFO. The attach line is `AppLog.i` |
| `onboarding-version` | int | `2` | `2` | |
| `kill-on-disconnect` | boolean | `false` | `false` | |
| `hud_mirroring` | boolean | `false` | `false` | |
| `app-language` | string | leave | `en` | the settings runs grade English labels |
| `video-profile-starvation-cap`, `native-aa-wireless`, `wifi-launcher-mode` | | delete | delete | |
| `show-fps-counter` | boolean | per run | per run | the "Show Performance Overlay" toggle |
| `overlay-fields` | int | per run | per run | bits FPS 1, CPU 2, TEMP 4, FRAME 8; absent means 15 |
| `overlay-position` | int | per run | per run | 0 left, 1 right; absent means 0 |
| audio keys | | leave | leave | record them |

```bash
SKEYS="int:wifi-connection-mode=3 int:log-level=2 int:onboarding-version=2 bool:kill-on-disconnect=false bool:hud_mirroring=false del:video-profile-starvation-cap del:native-aa-wireless del:wifi-launcher-mode"
UKEYS="int:wifi-connection-mode=0 int:log-level=2 int:onboarding-version=2 bool:kill-on-disconnect=false bool:hud_mirroring=false str:app-language=en del:video-profile-starvation-cap del:native-aa-wireless del:wifi-launcher-mode"
```

`ACTION_LOG_MARKER` is not gated on this build (`AutomationCommandPolicy.CONFIGURING` does not list it), so `allow-external-configuration` is not needed.

## 5. Every action as a verb, and the helpers

| Step | Command | Note |
|---|---|---|
| marker | `mark <label>` (`send ACTION_LOG_MARKER --es text <label>`) | `AutomationMarker: <label>` at WARN |
| identity | `send ACTION_QUERY_STATE` | record `commit` |
| exit after a run | `send ACTION_EXIT`, `sleep 3`, `am force-stop` | |

Steps that are not app verbs, and are allowed (template section 3): the launch inside `run_open`; `am start -n $SETTINGS --es extra_search_query overlay` (the settings screen, seeded with a search, so the Debug row shows with no scroll); `am start -n $SETTINGS --ei extra_destination $DEST` (the new sub-screen, a nav-graph destination); `input keyevent 4` (BACK); `uiautomator dump` and `screencap` (reads, no input); D-POCO's airplane mode inside `run_open`, `phone_air_on` and `phone_air_off`. **No tap on the app anywhere, and no scroll.** There is no hand step in this round.

Make the folder `hur-wifi-test-scripts/fps-overlay-compact-round1/`. Copy `ohu_lib.sh`, `ohu_setkeys.py` and `ptr_lib.sh` into it from `hur-wifi-test-scripts/projection-teardown-and-relays-round3/`. Save the three files below beside them. List every file in Setup notes. Source order: `ohu_lib.sh`, `ptr_lib.sh`, `fo_lib.sh`. From the first two this brief uses `PKG`, `send`, `mark`, `nl`, `waitfor`, `hu_put`, `run_open`, `phone_air_on`, `phone_air_off` (`ohu_lib.sh`) and `apk_check`, `th_report`, `cap_start`, `cap_stop`, `pcap_start`, `pcap_stop`, `prefs_cat` (`ptr_lib.sh`). If a script does not match the real line format, fix it, say so in Setup notes, and keep going. Run the round under `flock /tmp/ohu-rig.lock`.

```bash
HU=27870808938846; PH=4f4027e9; OTHER=; PUT=hu_put; OUT=$PWD
source ./ohu_lib.sh; source ./ptr_lib.sh; source ./fo_lib.sh
adb -s $HU shell "su -c 'cat /data/data/$PKG/shared_prefs/settings.xml'" > $OUT/settings-backup-HU.xml; BASEXML=$OUT/settings-backup-HU.xml
grep -aoE '(use-aac-audio|audio-latency-multiplier|audio-queue-capacity|enable-audio-sink|hud_mirroring|app-language|show-fps-counter|overlay-position|overlay-fields)"[^/]*' $BASEXML   # record
adb -s $PH shell dumpsys package com.google.android.projection.gearhead | grep -m1 versionName   # record
adb -s $HU shell appops get $PKG SYSTEM_ALERT_WINDOW                    # record: the raise route depends on it
adb -s $HU shell wm size                                                # record
WANT_MD5=<the candidate's md5 from R0>
```

**`fo_lib.sh`**:

```bash
# fo_lib.sh : source after ohu_lib.sh and ptr_lib.sh. Needs HU, PH, OUT, BASEXML, SKEYS, UKEYS, DEST.
SETTINGS=$PKG/com.andrerinas.openheadunit.main.SettingsActivity
pmark() { adb -s "$PH" shell log -t OHURIG "$1"; }
keys_now() { prefs_cat | grep -aoE '(show-fps-counter|overlay-fields|overlay-position|hud_mirroring|wifi-connection-mode)"[^/]*' | sort; }
fshot() { adb -s "$HU" shell screencap -p /sdcard/fo.png; sleep 0.3; adb -s "$HU" pull /sdcard/fo.png "$OUT/$1.png" >/dev/null 2>&1; sleep 0.3; }
# ui_dump <label> : a uiautomator dump (3 tries) plus a screenshot. Writes <label>.xml, .json, .png. Prints DUMP_OK or DUMP_FAIL.
ui_dump() { local i ok=0; rm -f "$OUT/$1.xml" "$OUT/$1.json"
  for i in 1 2 3; do adb -s "$HU" shell rm -f /sdcard/fo.xml; sleep 0.3
    adb -s "$HU" shell uiautomator dump /sdcard/fo.xml >> "$OUT/$1.dump.txt" 2>&1; sleep 0.3
    adb -s "$HU" pull /sdcard/fo.xml "$OUT/$1.xml" >/dev/null 2>&1
    grep -q '<hierarchy' "$OUT/$1.xml" 2>/dev/null && { ok=1; break; }; rm -f "$OUT/$1.xml"; sleep 2; done
  fshot "$1"
  if [ $ok = 1 ]; then python3 -I fo_ui.py "$OUT/$1.xml" > "$OUT/$1.json"; echo "DUMP_OK $1"; else echo "DUMP_FAIL $1"; fi; }
# srun <RUN> <overlay spec...> : one Native AA session on D-HU, two overlay dumps 10 s apart, a clean exit.
srun() { RUN=$1; shift; apk_check || return 1
  adb -s "$PH" shell dumpsys window | grep -a mCurrentFocus          # if not idle or home: adb -s $PH shell input keyevent KEYCODE_HOME
  pcap_start "$RUN"; CAPLOOP=
  run_open "$RUN" $SKEYS "$@" || { echo "SESSION_FAIL $RUN"; pcap_stop; cap_stop; adb -s "$HU" shell am force-stop $PKG; return 2; }
  pmark "$RUN-start"; keys_now > "$OUT/$RUN.keys"
  ui_dump "$RUN-d1"; sleep 10; ui_dump "$RUN-d2"; sleep 5
  mark "$RUN-end"; pmark "$RUN-end"; sleep 1; pcap_stop; cap_stop; th_report "$RUN" > "$OUT/$RUN.thermal.txt"
  send ACTION_EXIT >/dev/null; sleep 3; adb -s "$HU" shell am force-stop $PKG; }
# sx <RUN> : the session run's counts, from its own capture files
sx() { local C=$OUT/$1.logcat P=$OUT/$1.phone.logcat
  echo "$1 attach=$(grep -acF 'PerformanceOverlay: fields=' $C) ssl=$(grep -acF 'SSL handshake complete' $C) resume=$(grep -acF 'AapProjectionActivity: onResume' $C) rendered=$(grep -acE 'Throughput over [0-9]+ms: rendered=[1-9]' $C) upd_fail=$(grep -acF 'PerformanceOverlay: update failed: ' $C) fatal=$(grep -acF 'FATAL EXCEPTION' $C) match=$(grep -acF 'MATCH! Starting AapService' $C) garbage=$(grep -acF 'Magic Garbage detected in header' $C) avc_cpu=$(grep -a 'avc: denied' $C | grep -acE 'name="(stat|loadavg)"') avc_thermal=$(grep -a 'avc: denied' $C | grep -ac thermal) ph_crit=$(grep -acF 'Critical error' $P) ph_lines=$(grep -ac . $P)"
  grep -aF 'PerformanceOverlay: fields=' $C | sed 's/.*PerformanceOverlay: //' | sort | uniq -c; }
# ucap <RUN> <spec...> : a settings run's start: app stopped, keys written and read back, a capture
ucap() { RUN=$1; shift; CAP=$OUT/$RUN.logcat; CAPLOOP=; adb -s "$HU" shell am force-stop $PKG; sleep 1
  hu_put "$BASEXML" $UKEYS "$@" >/dev/null; keys_now > "$OUT/$RUN.keys-before"; cat "$OUT/$RUN.keys-before"; cap_start; sleep 1; mark "$RUN-start"; }
# uend <RUN> : a settings run's end. The 2 s let any write the app made reach the disk before the stop.
uend() { mark "$1-end"; sleep 2; cap_stop; adb -s "$HU" shell am force-stop $PKG; sleep 1; keys_now > "$OUT/$1.keys-after"
  echo "$1 fatal=$(grep -acF 'FATAL EXCEPTION' $OUT/$1.logcat) keys_changed=$(diff "$OUT/$1.keys-before" "$OUT/$1.keys-after" | grep -c '^[<>]')"; }
```

**`fo_ui.py`** (checked against a synthetic dump before this brief was written):

```python
#!/usr/bin/env python3
"""fo_ui.py <dump.xml> : the overlay text views and the settings rows of one uiautomator dump, as JSON."""
import json, re, sys, xml.etree.ElementTree as ET

LINE = re.compile(r'^(FPS|CPU|Temp|Frame): ')
BND = re.compile(r'\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]')
root = ET.parse(sys.argv[1]).getroot()
nodes = list(root.iter('node'))
parent = {c: p for p in root.iter() for c in p}

def bounds(n):
    m = BND.match(n.get('bounds', ''))
    return [int(x) for x in m.groups()] if m else None

def rid(n, name):
    return n.get('resource-id', '').endswith(':id/' + name)

width = max((bounds(n)[2] for n in nodes if bounds(n)), default=0)
overlay = []
for n in nodes:
    t = n.get('text', '')
    if n.get('class') == 'android.widget.TextView' and LINE.match(t):
        b = bounds(n)
        cx = (b[0] + b[2]) / 2 if b else -1
        overlay.append({'text': t, 'lines': t.split('\n'), 'bounds': b,
                        'side': 'left' if cx < width / 2 else 'right'})

rows = []
for n in nodes:
    if not rid(n, 'settingName'):
        continue
    top = n
    row = parent.get(n)
    while row is not None and row.tag == 'node':
        if sum(1 for d in row.iter('node') if rid(d, 'settingName')) > 1:
            break
        top = row
        row = parent.get(row)
    sw = next((d for d in top.iter('node') if rid(d, 'settingSwitch')), None)
    val = next((d for d in top.iter('node') if rid(d, 'settingValue')), None)
    rows.append({'name': n.get('text', ''), 'value': val.get('text') if val is not None else None,
                 'checked': sw.get('checked') if sw is not None else None,
                 'enabled': sw.get('enabled') if sw is not None else None})

save = next((n for n in nodes if rid(n, 'save_button_widget')), None)
print(json.dumps({'width': width, 'overlay': overlay, 'rows': rows,
                  'save': {'present': save is not None, 'enabled': save.get('enabled') if save is not None else None},
                  'texts': [n.get('text') for n in nodes if n.get('text')]}))
```

**`fo_check.py`** (one verdict line per dump; checked against the same synthetic dump):

```python
#!/usr/bin/env python3
"""fo_check.py, one verdict line per dump.
  overlay <json> <FPS,CPU,TEMP,FRAME|none> <left|right>   the overlay on the projection
  entry   <json> <expected row value>                     the Debug entry row, from the search "overlay"
  screen  <json> <show 0|1> <bits 0-15> <Left|Right>      the sub-screen's rows
Prints key=value pairs and ok=1 or ok=0."""
import json, re, sys

mode, path = sys.argv[1], sys.argv[2]
j = json.load(open(path))
RX = {'FPS': r'FPS: (\d+|--)', 'CPU': r'CPU: app (\d+%|--) / sys (\d+%|\d+\.\d\d load|--)',
      'TEMP': r'Temp: (-?\d+C|--)', 'FRAME': r'Frame: (\d+ms|--)'}
out, ok = [], True

if mode == 'overlay':
    want = [] if sys.argv[3] == 'none' else sys.argv[3].split(',')
    ov = j['overlay']
    out.append('overlay_nodes=%d' % len(ov))
    if not want:
        ok = len(ov) == 0
    else:
        ok = len(ov) == 1
        if ov:
            lines = ov[0]['lines']
            out.append('lines=%s side=%s' % (json.dumps(lines), ov[0]['side']))
            ok = ok and len(lines) == len(want) and all(re.fullmatch(RX[f], l) for f, l in zip(want, lines))
            ok = ok and ov[0]['side'] == sys.argv[4]
            if 'FPS' in want and lines:
                out.append('fps_numeric=%d' % (1 if re.fullmatch(r'FPS: \d+', lines[0]) else 0))
elif mode == 'entry':
    rows = [r for r in j['rows'] if r['name'] == 'Performance overlay']
    value = rows[0]['value'] if rows else None
    old_toggle = j['texts'].count('Show Performance Overlay')
    old_pos = j['texts'].count('Performance overlay position')
    debug = j['texts'].count('Debug')
    out.append('entry_rows=%d value=%s old_toggle=%d old_position=%d debug_header=%d'
               % (len(rows), json.dumps(value), old_toggle, old_pos, debug))
    ok = len(rows) == 1 and value == sys.argv[3] and old_toggle == 0 and old_pos == 0
elif mode == 'screen':
    show, bits, side = sys.argv[3] == '1', int(sys.argv[4]), sys.argv[5]
    by = {r['name']: r for r in j['rows']}
    def chk(name, checked, enabled):
        r = by.get(name)
        good = r is not None and r['checked'] == checked and r['enabled'] == enabled
        out.append('%s=%s/%s' % (name.replace(' ', '_'), r and r['checked'], r and r['enabled']))
        return good
    ok = chk('Show Performance Overlay', 'true' if show else 'false', 'true')
    for name, bit in (('FPS', 1), ('CPU', 2), ('Temperature', 4), ('Frame age', 8)):
        ok = chk(name, 'true' if bits & bit else 'false', 'true' if show else 'false') and ok
    pos = by.get('Performance overlay position')
    out.append('position=%s save=%s/%s' % (json.dumps(pos and pos['value']), j['save']['present'], j['save']['enabled']))
    ok = ok and pos is not None and pos['value'] == side and j['save']['present'] and j['save']['enabled'] == 'false'
print(' '.join(out), 'ok=%d' % (1 if ok else 0))
```

### The deciding lines

Every head unit line below was checked with `grep -F` against `app/src/main` at `398b54b1`. All are INFO, WARN or ERROR, so `log-level=2` carries them.

| Line | Source | Where it is counted |
|---|---|---|
| `PerformanceOverlay: fields=<names> sources=<names>` (composed: `PerformanceOverlay: ` plus `PerformanceOverlayPolicy.describe`, which builds `fields=` and `sources=`) | `view/PerformanceOverlay.kt`, `view/PerformanceOverlayPolicy.kt` | the run's head unit capture |
| `PerformanceOverlay: update failed: ` | `view/PerformanceOverlay.kt` | the same |
| `AapProjectionActivity: onResume` | `aap/AapProjectionActivity.kt` | the same |
| `SSL handshake complete` | `AapSslContext.kt` | the same |
| `Throughput over ` (regex `Throughput over [0-9]+ms: rendered=[1-9]`) | `decoder/video/VideoDecoder.kt` | the same |
| `AutomationReceiver: `, `AutomationMarker: ` | `automation/` | the same |
| `MATCH! Starting AapService`, `Magic Garbage detected in header` | receivers, `AapReadSingleMessage.kt` | the same, discard rules |
| `FPS: `, `CPU: app `, `Temp: `, `Frame: ` | `view/PerformanceOverlayPolicy.kt`, the overlay text | the dump, through `fo_check.py overlay` |
| `Performance overlay`, `Show Performance Overlay`, `Performance overlay position`, `Frame age`, `Temperature`, `No lines`, `Debug`, `Left`, `Right`, `Off` | `res/values/strings.xml` | the dump, through `fo_check.py entry` and `screen` |
| `Unsaved Changes` | `res/values/strings.xml` | the U2b back dump |

Lines that are not ours, so not in the source: `FATAL EXCEPTION` (Android), `avc: denied` (the kernel audit), and on the phone `Critical error` (Android Auto, `CAR.SERVICE`).

## 6. Runs

Order: R0, R1, **R2**, R3, R4, R5, then U1 and U2. **The point of the round is R2.** Write the results file after every run.

**Discard and stop rule, for every session run.** A session run is void when `ssl` is not exactly 1 (no session, or a reconnect, which starts a second projection and a second attach), when `match` or `garbage` is above 0, or when `srun` printed `SESSION_FAIL`. Re-run a void run once. A second void makes it INCONCLUSIVE; say which count voided it. Each run is run once otherwise: the stop rule is one non-void pass of each.

**The dump rule, for every session run.** Grade the overlay from `d1` when it printed `DUMP_OK`, else from `d2`. When both printed `DUMP_FAIL`, the host grades the overlay conditions from `$OUT/<RUN>-d1.png` and writes "graded from the screenshot" in the run's section. When both dumps are `DUMP_OK`, both must pass.

**Every session run also needs**, besides its own conditions: `rendered` 1 or more and `resume` 1 or more (the projection was up, so the overlay had a screen to attach to); `upd_fail` 0; `fatal` 0; and on the phone, `ph_crit` 0 with `ph_lines` above 0 (the phone capture ran). Report `avc_cpu`, `avc_thermal`, `fps_numeric` and the overlay's lines as numbers and quotes; they are not graded.

### R1. Upgrade default: the overlay on, no `overlay-fields` key

```bash
srun R1 bool:show-fps-counter=true del:overlay-fields del:overlay-position
sx R1
for d in d1 d2; do [ -f $OUT/R1-$d.json ] && python3 -I fo_check.py overlay $OUT/R1-$d.json FPS,CPU,TEMP,FRAME left; done
```

**PASS:** `attach` 1 or more, and every attach line reads exactly `fields=FPS,CPU,TEMP,FRAME sources=cpu,temp`; the overlay check prints `ok=1` (one overlay view, four lines in the order FPS, CPU, Temp, Frame, each in its format, on the left). **FAIL:** any other value. This run is the same picture as `main`, so a PASS here is a regression guard: it says the absent key reads as all four lines.

### R2. FPS only (**the point of the round**)

```bash
srun R2 bool:show-fps-counter=true int:overlay-fields=1 int:overlay-position=0
sx R2
for d in d1 d2; do [ -f $OUT/R2-$d.json ] && python3 -I fo_check.py overlay $OUT/R2-$d.json FPS left; done
```

**PASS:** `attach` 1 or more, every attach line reads exactly `fields=FPS sources=none`; the overlay check prints `ok=1`: one overlay view whose whole text is one line, `FPS: <number>` or `FPS: --`, with no newline, on the left. **FAIL:** any other value. A view with four lines, or no attach line, means the build does not carry the change: recheck identity before you write FAIL.

### R3. No line chosen, the toggle on

```bash
srun R3 bool:show-fps-counter=true int:overlay-fields=0 int:overlay-position=0
sx R3
for d in d1 d2; do [ -f $OUT/R3-$d.json ] && python3 -I fo_check.py overlay $OUT/R3-$d.json none left; done
```

**PASS:** `attach` 1 or more, every attach line reads exactly `fields=none sources=none`; the overlay check prints `overlay_nodes=0 ok=1` in each dump. **FAIL:** any overlay view, or another attach line. The attach line is what makes a 0 count mean something: it proves the overlay attached and chose to draw nothing. Report what the screenshot shows in both top corners (a dark box there is a FAIL the dump cannot see; say so if you see one).

### R4. CPU and frame age, on the right

```bash
srun R4 bool:show-fps-counter=true int:overlay-fields=10 int:overlay-position=1
sx R4
for d in d1 d2; do [ -f $OUT/R4-$d.json ] && python3 -I fo_check.py overlay $OUT/R4-$d.json CPU,FRAME right; done
```

**PASS:** `attach` 1 or more, every attach line reads exactly `fields=CPU,FRAME sources=cpu`; the overlay check prints `ok=1`: two lines, `CPU: ...` then `Frame: ...`, with the view's centre right of the screen's centre. **FAIL:** any other value. This run proves three things at once: the line order does not follow the bit order of the stored int, CPU is sampled without TEMP, and the position key still moves the overlay.

### R5. The toggle off

```bash
srun R5 bool:show-fps-counter=false int:overlay-fields=15 int:overlay-position=0
sx R5
for d in d1 d2; do [ -f $OUT/R5-$d.json ] && python3 -I fo_check.py overlay $OUT/R5-$d.json none left; done
```

**PASS:** `attach` 0 and the overlay check prints `overlay_nodes=0 ok=1` in each dump, with `rendered` 1 or more. **FAIL:** an attach line or an overlay view. `main` behaves the same, so this is a regression guard.

### U1. The Debug row, five stored states, no session

Run `phone_air_on` once before U1 and `phone_air_off` once after U2. Each state opens the settings screen with the search seeded to `overlay`. The search spans both tiers and keeps the Debug header above any Debug row it matches, so no scroll is needed.

```bash
u1() { ucap U1$1 "${@:2}"; adb -s $HU shell am start -n $SETTINGS --es extra_search_query overlay >/dev/null; sleep 5
  ui_dump U1$1; uend U1$1; }
u1 a bool:show-fps-counter=true  int:overlay-fields=1  int:overlay-position=0
u1 b bool:show-fps-counter=false int:overlay-fields=15 int:overlay-position=0
u1 c bool:show-fps-counter=true  int:overlay-fields=10 int:overlay-position=1
u1 d bool:show-fps-counter=true  int:overlay-fields=0  int:overlay-position=0
u1 e bool:show-fps-counter=true  del:overlay-fields    del:overlay-position
python3 -I fo_check.py entry $OUT/U1a.json 'FPS (left)'
python3 -I fo_check.py entry $OUT/U1b.json 'Off'
python3 -I fo_check.py entry $OUT/U1c.json 'CPU, Frame age (right)'
python3 -I fo_check.py entry $OUT/U1d.json 'No lines (left)'
python3 -I fo_check.py entry $OUT/U1e.json 'FPS, CPU, Temperature, Frame age (left)'
```

**PASS:** each of the five checks prints `entry_rows=1`, the expected value, `old_toggle=0`, `old_position=0` and `ok=1`; each `uend` line reads `fatal=0`. Report `debug_header` per state; it should read 1. **FAIL:** any other value. A dump with no `Performance overlay` row and English labels elsewhere is a FAIL; a dump in another language means `app-language` did not take: re-run U1 once after a check of the key, then INCONCLUSIVE. A `DUMP_FAIL` here is not expected (nothing on this screen changes each second): re-run that state once, then INCONCLUSIVE.

### U2. The new screen shows the stored state, and Back with no change writes nothing

```bash
ucap U2a bool:show-fps-counter=false int:overlay-fields=15 int:overlay-position=0
adb -s $HU shell am start -n $SETTINGS --ei extra_destination $DEST >/dev/null; sleep 5; ui_dump U2a; uend U2a
ucap U2b bool:show-fps-counter=true int:overlay-fields=1 int:overlay-position=1
adb -s $HU shell am start -n $SETTINGS --ei extra_destination $DEST >/dev/null; sleep 5; ui_dump U2b
adb -s $HU shell input keyevent 4; sleep 3; ui_dump U2b-back; uend U2b
python3 -I fo_check.py screen $OUT/U2a.json 0 15 Left
python3 -I fo_check.py screen $OUT/U2b.json 1 1 Right
python3 -I -c 'import json,sys; t=json.load(open(sys.argv[1]))["texts"]; print("back: toggle=%d unsaved=%d" % (t.count("Show Performance Overlay"), t.count("Unsaved Changes")))' $OUT/U2b-back.json
```

**PASS:**
- U2a's check prints `ok=1`: the toggle off and enabled; all four line switches on and disabled (greyed); the position row reads `Left`; the Save button is present and disabled.
- U2b's check prints `ok=1`: the toggle on; FPS on, CPU, Temperature and Frame age off, all four enabled; the position row reads `Right`; Save present and disabled.
- The back dump prints `toggle=0 unsaved=0`: BACK with no change left the screen with no dialog.
- Both `uend` lines read `fatal=0 keys_changed=0`: opening the screen and leaving it wrote nothing.

**FAIL:** any other value. If U2a or U2b shows no `Show Performance Overlay` row, the deep link did not land: check `DEST` against R0 step 5, re-run once, then UNTESTABLE with the id used. Report from the U2a screenshot whether the position row looks greyed. `fo_check.py` cannot grade it, because `setOnClickListener` sets the row clickable again after the adapter clears the flag, so the dump's `clickable` is always true.

### Close

Restore `$BASEXML` with `hu_put "$BASEXML"` (no spec), read it back and diff it against the backup: the diff must be empty. `ps aux | grep -c "[l]ogcat"` must print 0. Run `phone_air_off` if U2 did not.

## 7. Do not re-run

Nothing on this thread was run before. The overlay's temperature and CPU findings in `fps-overlay-temp-and-cpu-findings.md` are a source read, not a round, and this round does not grade them.

## 8. Report back

1. R2: the attach line, the overlay's exact text from the dump, and its side.
2. R3: `overlay_nodes` in each dump, the attach line, and what the screenshot shows in the top corners.
3. U1's five row values and U2's two `ok` lines with `keys_changed`.

Also give `fps_numeric` and the CPU and temperature lines from R1, and `avc_cpu` for R1 against R2.

Results go in `fps-overlay-compact-round1-results.md` in the template's skeleton (section 7). Put each run's `th_report` output in its section. Captures (logcats, dumps, screenshots) go to the fork as release `rig-evidence-fps-overlay-compact`, asset `fps-overlay-compact-round1-captures.zip`, cited with its sha256.
