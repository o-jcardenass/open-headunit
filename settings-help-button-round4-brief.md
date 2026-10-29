# settings-help-button, round 4 brief

Published name: `settings-help-button-round4-brief.md`. Results go in `settings-help-button-round4-results.md`.
Evidence: upload `settings-help-button-round4-captures.zip` to the existing release `rig-evidence-settings-help-button`.

Units: D-HU (Part A), then D-SAM (Part B). No phone takes part. D-POCO, D-MOTO and D-HP take no part.

Queue line: `settings-help-button-round4-brief.md` (`1c25716f` on `fork/docs/call-audio-needs-hfp`, on `main` `54256dcb`;
one build, no baseline; units D-HU and D-SAM, no phone; R1 is the point).

Pairing: this round uses no phone. It turns D-HU's Bluetooth off. A round that uses only D-POCO, D-MOTO or D-HP can
run beside it (`TESTING-TEMPLATE.md` §3b). If this round runs second in a pair, its lock is `/tmp/ohu-rig-help.lock`.

Hand steps: none. Injected input: yes, on targets from a `uiautomator dump` only, at most 5 per run. The search box is
filled by an intent extra, never typed. R2 scrolls the section rail as a bounded search (`rig-quirks/topics/tooling.md`,
"bounded search"): at most 4 swipes per arm, each followed by a dump, and the arm stops when it finds `Info`.

**Time budget.** Pre-flight 5 min, build 25 min, Part A 35 min, Part B 12 min, thermal waits about 6 min. About
1 h 25 min in all.

| Run | Unit, panel | What | Input | Minutes |
|---|---|---|---|---|
| R1 | D-HU WIDE | **The point.** The chevron folds the rail to icons, a folded icon jumps to its section, the choice survives a restart | 4 taps | 5 |
| R2 | D-HU WIDE | The rail lists the sections in the new order, on Advanced (13) and on Basic (11) | up to 4 swipes per arm | 6 |
| R3 | D-HU WIDE | With connection type USB only, "Bluetooth address" shows, under General | 0 | 2 |
| R4 | D-HU WIDE | The GPS row has its new name, summary and help, under Navigation | 1 tap | 3 |
| R5 | D-HU WIDE | Two rows with help from this branch: Show Toast Messages (General) and Hide the Clock (Theming) | 2 taps | 4 |
| R6 | D-HU NARROW | Below 560dp wide the rail shows, icons only, with no chevron; an icon jumps | 1 tap | 4 |
| R7 | D-HU portrait | Portrait shows the chip row and no rail | 0 | 3 |
| S1 | D-SAM landscape | API 19: the rail and its fold work and the screen does not crash | up to 2 taps | 5 |
| S2 | D-SAM portrait | API 19: portrait shows the chip row and does not crash | 0 | 3 |

## 1. Build and baseline

One arm. **History was rewritten since round 3.** The branch was rebased onto `main` `54256dcb`, and its commits were
regrouped. Round 3's tip `e3b2597c` is no longer on the branch. The Material 3 settings redesign that round 3 stacked
on is now in `main`. The branch has five commits:

| Commit | Subject |
|---|---|
| `affa10b0` | Updates: offer only a release whose APK is uploaded |
| `a52013c0` | Settings: a help button on every setting, with one-line summaries |
| `0e9da549` | Translations: the Wi-Fi scan screen and 17 other untranslated strings |
| `03441fa6` | Settings: order the sections by how often they are used |
| `1c25716f` | Settings: fold the landscape section rail to its icons |

| Arm | Ref | Commit | Tree |
|---|---|---|---|
| C (candidate) | `fork/docs/call-audio-needs-hfp` | `1c25716f93b4ec8c20b6dd11ebd9c79b0491d973` | `a93c5eb8917db424f0e1251172370270bb1b1cc7` |

The dev host ran the JVM suite at this tree: 4285 tests, 0 failures. Do not run the unit tests on the rig. No other
queued round shares this base.

**get_apk.sh**

```bash
OUT=$(pwd)/rig-data/rounds/settings-help-button-round4; mkdir -p $OUT
WT=$(ls -d worktrees/* 2>/dev/null | head -1)        # any existing worktree; else make one with wt-new.sh
git -C $WT fetch fork docs/call-audio-needs-hfp
git -C $WT checkout --detach 1c25716f93b4ec8c20b6dd11ebd9c79b0491d973
git -C $WT rev-parse 'HEAD^{tree}'                    # MUST print a93c5eb8917db424f0e1251172370270bb1b1cc7
$RIG_TOOLKIT/rig_thermal.sh wait 70
HUR_DIR=$WT MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh   # copy the APK out of apks/ to $OUT/apk-C.apk at once
md5sum $OUT/apk-C.apk                                 # record in Setup notes
```

Install with `adb -s <serial> install -r -d $OUT/apk-C.apk` on D-HU and on D-SAM. Never uninstall. Identity on each
unit after the install:

- `send ACTION_QUERY_STATE`: the `data=` line MUST carry a commit that starts with `1c25716f93b4`.
- Pull the live APK (`adb shell pm path $PKG`, then `adb pull`). Then
  `unzip -p live.apk 'classes*.dex' | strings | grep -c settings-rail-collapsed` MUST be 1 or more. Only this build
  has that preference key.

## 2. What this is and why

Round 3 passed the (?) help button. Since then the operator asked for four more changes on this branch:

1. **The section order.** Sections now follow how often people use them: General, Wireless Connection, Graphics,
   Video, Audio, Automation, Input Settings, Navigation, Theming, More Features, Backup and reset, Debug, Info. The old
   order had More Features and Navigation above Graphics, and Audio near the bottom. The three UI rows (Hide the Clock,
   phone signal, battery) joined Theming, and Reset joined Backup.
2. **Rows that moved.** "Bluetooth address" moved from Wireless Connection to General. It applies to USB sessions
   too, but Wireless Connection is hidden when the connection type is USB only, so a USB-only user never saw it.
   "Allow external configuration" and "Android Auto Exit Action" moved to Automation.
3. **The GPS row.** Its name was "GPS for navigation", which reads as a GPS on or off switch. The row only chooses
   whose position Android Auto gets, this unit's or the phone's. The name, summary and help now say that.
4. **The rail.** In landscape, 560dp wide or more, a chevron at the top of the section rail folds it to its icons and
   gives the settings list the room. A folded icon still jumps to its section, and a long press shows its name. The
   choice is kept in `settings-rail-collapsed`. Below 560dp wide the rail was hidden before. Now it shows, icons only,
   with no chevron. Portrait keeps its chip row.

The JVM suite covers the strings in every locale and the help lengths. It cannot see the views. The section order is
also proven on the dev host: a script moved the code blocks, and a sorted line diff showed only the dropped headers
changed. This round checks what the screen shows, by dump and screenshot.

## 3. What is different about this round

- **No phone, no session.** Opening `SettingsActivity` directly does not start `AapService`. Turn D-HU's Bluetooth
  off for the round and on at the end. D-SAM's Bluetooth cannot be turned off from the shell. Every run window MUST
  show 0 of L-ARM and 0 of L-WAKE (section 6). A window with either is void; re-run it once.
- **No crash.** Every run window MUST show 0 of `FATAL EXCEPTION` (L-CRASH). This matters most on D-SAM, API 19.
- **Panels on D-HU, by `wm density`.** Round 3 found D-HU at density 240 with no override, 480dp tall, landscape.
  WIDE is the found density, and it MUST read 560dp wide or more. NARROW picks a density for about 520dp wide, and it
  MUST read under 560dp. `panel.sh` sets each one, reads the width back, and `dens_restore` puts the found density back.
- **Portrait on D-HU is a settings key.** R7 writes `screen-orientation=4` (portrait) with the app stopped. If the
  unit does not rotate (`am get-config` still reads `land`), R7 is INCONCLUSIVE (no rotation), not a FAIL.
- **D-SAM mounts portrait and runs landscape at `screen-orientation=2`** (`rig-quirks/units/D-SAM.md`). S1 writes 2,
  and S2 writes 4. Round 3 found D-SAM at 4. The backup restore puts the found value back.
- **D-SAM sleeps.** Round 3 lost three attempts to a blank dump with the screen off. On API 19 `KEYCODE_WAKEUP` does
  nothing and `KEYCODE_POWER` toggles, so `wake` reads the screen state first and presses POWER only when it is off.
- **D-SAM:** `SettingsActivity` is not exported there, so `open` falls back to `run-as $PKG am start --user 0`.
  `run-as cat` adds `\r`; strip CRs and blank lines before any diff.
- **Two views share one id.** A rail item and a section header in the list both use `categoryTitle`. `ui.py` tells
  them apart by their list: a rail item sits under `settingsCategories`, a header under `settingsRecyclerView`.
- **A folded item has no title node.** Its title is gone from the layout, so the dump holds no `categoryTitle` for
  it. The item names its section in its `content-desc` instead.
- **The tooltip may not be in the dump.** A long press opens a popup window, which `uiautomator dump` may not hold.
  R1 records a screenshot of it and grades nothing from it.
- **English, for the names.** Write `app-language=en` on both units.
- **Expected INCONCLUSIVE:** R7 when D-HU does not rotate.

## 4. Settings keys

Back up each unit's `settings.xml` at the start (`$OUT/settings-backup-<serial>.xml`), restore it at the end, and diff
the restored file against the backup. State the delta in Setup notes. On D-HU write with `set_hu_settings_host.py`, on
D-SAM with `set_prefs_runas_host.py`, always with the app stopped, and read every key back. Write the set key with the
`set:` kind of `ohu_setkeys.py`, with the empty-set fix in `rig-quirks/topics/tooling.md`, and read back the exact
element.

**Both units, at the start:**

```xml
<int name="log-level" value="1" />
<int name="onboarding-version" value="2" />
<string name="app-language">en</string>
<int name="app-theme" value="2" />
<boolean name="use-extreme-dark-mode" value="false" />
<int name="ui-scale-settings-percent" value="100" />
<boolean name="advanced-settings-active" value="true" />
<boolean name="settings-rail-collapsed" value="false" />
<set name="connection-modes"><string>usb</string><string>wifi</string></set>
```

`log-level=1` is DEBUG, which L-HELP needs (`AppLog.d`). `advanced-settings-active=true` opens the Advanced tab.
`connection-modes` with both `usb` and `wifi` shows the Wireless Connection section and the GPS row.

**Per run:**

| Run | Key | Value |
|---|---|---|
| R2, Basic arm | `advanced-settings-active` | `false`, then back to `true` at the end of R2 |
| R3 | `connection-modes` | `usb` only: `<set name="connection-modes"><string>usb</string></set>`, then back to both at the end of R3 |
| R7 | `screen-orientation` | `4` |
| S1 | `screen-orientation` | `2` |
| S2 | `screen-orientation` | `4` |

Leave the rig's audio keys as found. This round plays no audio. No run presses Save.

## 5. Helpers

Run every adb call in sequence, never two at once against one unit. Write the results file after every run. Source
`lib.sh` from the rig root, so `OUT` resolves to `rig-data/rounds/settings-help-button-round4`.

**ui.py** (save it as `$OUT/ui.py`; `lib.sh` calls it there)

```python
# ui.py : read a uiautomator dump.  python3 -I ui.py <cmd> <dump.xml> [args]
import sys, re, xml.etree.ElementTree as ET
cmd, path, args = sys.argv[1], sys.argv[2], sys.argv[3:]
root = ET.parse(path).getroot(); nodes = list(root.iter("node"))
par = {c: p for p in root.iter() for c in p}
def B(n): return list(map(int, re.findall(r"-?\d+", n.get("bounds", "[0,0][0,0]"))))
def rid(n, s): return n.get("resource-id", "").endswith(":id/" + s)
def sub(n, *s): return [d for d in n.iter("node") if any(rid(d, x) for x in s)]
def one(s):
    m = [n for n in nodes if rid(n, s)]
    return m[0] if m else None
def anc(n):
    while n in par:
        n = par[n]; yield n
def row_of(n):     # the row card: the highest ancestor below the list that holds one setting name
    best = None
    for a in anc(n):
        if a.tag != "node" or a.get("class", "").endswith("RecyclerView") or len(sub(a, "settingName")) != 1: break
        best = a
    return best
def row_named(name):
    rows = [row_of(x) for x in nodes if rid(x, "settingName") and x.get("text") == name]
    if len(rows) != 1 or rows[0] is None: sys.exit("rows=%d" % len(rows))
    return rows[0]
def mid(b): return "%d %d" % ((b[0] + b[2]) // 2, (b[1] + b[3]) // 2)
def inside(b, f): return b[0] >= f[0] and b[1] >= f[1] and b[2] <= f[2] and b[3] <= f[3]
def items():       # rail or chip items: the direct children of settingsCategories, top to bottom
    c = one("settingsCategories")
    if c is None: return []
    out = []
    for it in [k for k in c if k.tag == "node"]:
        t = [d for d in sub(it, "categoryTitle") if d.get("text", "")]
        out.append({"title": t[0].get("text") if t else "", "desc": it.get("content-desc", ""),
                    "sel": it.get("selected") == "true", "b": B(it)})
    return out
def headers():     # section headers in the settings list, top to bottom
    l = one("settingsRecyclerView")
    if l is None: return []
    return sorted([(B(h)[1], h.get("text", ""), B(h)) for h in sub(l, "categoryTitle")])
F = B(nodes[0])
if cmd == "frame": print(",".join(map(str, F)))
elif cmd == "text":        # count of nodes whose text contains args[0]
    print(sum(1 for n in nodes if args[0] in n.get("text", "")))
elif cmd == "dlg":         # count of dialog titles on screen
    print(sum(1 for n in nodes if rid(n, "alertTitle")))
elif cmd == "desc":        # centre of the one node whose content-desc is args[0]
    m = [n for n in nodes if n.get("content-desc") == args[0]]
    if len(m) != 1: sys.exit("matches=%d" % len(m))
    print(mid(B(m[0])))
elif cmd == "rail":        # rail <density>: what the section navigation shows
    den = int(args[0]); r = one("settingsRail"); t = one("settingsRailToggle"); c = one("settingsCategories")
    l = one("settingsRecyclerView"); it = items()
    ys = set(i["b"][1] for i in it)
    names = [i["title"] or ("~" + i["desc"]) for i in it]
    print("rail=%d toggle=%s cats=%d n=%d titles=%d descs=%d horiz=%d railW=%ddp listX1=%ddp listW=%ddp sel=%s names=%s" % (
        r is not None, (t.get("content-desc") or "NODESC") if t is not None else "NONE", c is not None, len(it),
        sum(1 for i in it if i["title"]), sum(1 for i in it if i["desc"]), len(it) > 1 and len(ys) == 1,
        (B(r)[2] - B(r)[0]) * 160 // den if r is not None else -1,
        B(l)[0] * 160 // den if l is not None else -1, (B(l)[2] - B(l)[0]) * 160 // den if l is not None else -1,
        ",".join(i["title"] or i["desc"] for i in it if i["sel"]) or "NONE", "|".join(names)))
elif cmd == "names":       # the section names in rail order, one per line
    for i in items(): print(i["title"] or i["desc"])
elif cmd == "railbox":     # bounds of the rail list, for the bounded search
    c = one("settingsCategories"); print(" ".join(map(str, B(c))) if c is not None else "NONE")
elif cmd == "item":        # centre of the rail item named args[0], by title or by content-desc
    m = [i for i in items() if args[0] in (i["title"], i["desc"])]
    if len(m) != 1: sys.exit("items=%d" % len(m))
    print(mid(m[0]["b"]))
elif cmd == "hdr":         # the first section header in the list, and every header shown
    h = headers(); print("first=%s hdrs=%s" % (h[0][1] if h else "NONE", "|".join(x[1] for x in h)))
elif cmd == "row":         # row <name>: what the row card holds, and the section header above it
    r = row_named(args[0]); rb = B(r)
    def first(*ids):
        m = [n for n in sub(r, *ids) if n.get("text", "")]
        return m[0] if m else None
    s = first("settingDescription"); h = sub(r, "settingHelp"); sw = sub(r, "settingSwitch")
    above = [x for x in headers() if x[2][3] <= rb[1]]
    print("summary=%s\thelp=%d\tswitch=%s\tunder=%s\tonscreen=%d" % (
        s.get("text") if s is not None else "NONE", len(h), sw[0].get("checked") if sw else "NA",
        above[-1][1] if above else "NONE", inside(rb, F)))
elif cmd == "dialog":      # dialog <title> <screen x1,y1,x2,y2> <substring>...
    f = list(map(int, args[1].split(",")))
    t = [n for n in nodes if rid(n, "alertTitle")]; m = [n for n in nodes if rid(n, "message")]
    b = [n for n in nodes if rid(n, "button1")]
    if len(t) != 1 or len(m) != 1 or len(b) != 1:
        sys.exit("title=%d message=%d ok=%d" % (len(t), len(m), len(b)))
    msg = m[0].get("text", "")
    print("title_ok=%d text_ok=%d ok_label=%s fits=%d" % (t[0].get("text") == args[0],
          all(x in msg for x in args[2:]), b[0].get("text"), all(inside(B(x), f) for x in t + m + b)))
```

**lib.sh**

```bash
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
SA=$PKG/com.andrerinas.openheadunit.main.SettingsActivity
HU=27870808938846; SAM=30041c35642d2200
OUT=$(pwd)/rig-data/rounds/settings-help-button-round4; mkdir -p $OUT
export LC_ALL=C
D=$HU                                   # the unit under test: D=$SAM for Part B
send() { a=$1; shift; adb -s $D shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; }
mark() { send ACTION_LOG_MARKER --es text "$1" >/dev/null; echo "$1 $(date '+%m-%d %H:%M:%S')" >> $OUT/marks.txt; }
cap()  { echo $OUT/cap-$D.txt; }
win()  { sed -n "/AutomationMarker: $1-start/,/AutomationMarker: $1-end/p" $(cap); }
cnt()  { win $1 | grep -acF -- "$2"; }
# waitline <run> <fixed> <cap s>: capped wait for a line after <run>-start
waitline() { local t=0; while [ $t -lt $3 ]; do
  [ "$(sed -n "/AutomationMarker: $1-start/,\$p" $(cap) | grep -acF -- "$2")" -ge 1 ] && return 0; sleep 1; t=$((t+1)); done; return 1; }
# wake: D-SAM (API 19) only; POWER toggles, so press it only when the screen is off
wake() { [ $D = $SAM ] || return 0
  adb -s $D shell dumpsys power | grep -a -q 'mScreenOn=false' && { adb -s $D shell input keyevent 26; sleep 2; }
  adb -s $D shell dumpsys power | grep -a -o 'mScreenOn=[a-z]*' | head -1 | tee -a $OUT/wake.txt; }
# open <query|->: a fresh settings screen, filtered or whole
open() { adb -s $D shell am force-stop $PKG; sleep 1; wake; local ex="" r
  [ "$1" != "-" ] && ex="--es extra_search_query '$1'"
  r=$(adb -s $D shell "am start -n $SA $ex" 2>&1)
  echo "$r" | grep -qE 'Error|Exception' && r=$(adb -s $D shell "run-as $PKG am start --user 0 -n $SA $ex" 2>&1)
  echo "$D $1: $r" >> $OUT/am-start.log; sleep 2; }
ui()   { python3 -I $OUT/ui.py "$@"; }
# dump <label> [text]: our window in $OUT/<label>-<serial>.xml; with [text], wait until a node holds it. 3 tries.
dump() { local f=$OUT/$1-$D.xml i; for i in 1 2 3; do
  rm -f $f; adb -s $D shell rm -f /sdcard/ohu-ui.xml; sleep 0.3
  adb -s $D shell uiautomator dump /sdcard/ohu-ui.xml >/dev/null 2>&1; sleep 0.3
  adb -s $D pull /sdcard/ohu-ui.xml $f >/dev/null 2>&1
  if grep -q "package=\"$PKG\"" $f 2>/dev/null; then [ -z "$2" ] && return 0; [ "$(ui text $f "$2")" -ge 1 ] && return 0; fi
  sleep 2; done; echo "$1 NO_DUMP" >> $OUT/marks.txt; return 1; }
shot() { adb -s $D shell screencap -p /sdcard/ohu-s.png; sleep 0.3; adb -s $D pull /sdcard/ohu-s.png $OUT/$1-$D.png >/dev/null; }
back() { adb -s $D shell input keyevent 4; sleep 1; }
# tap/press/swipe <run> <what> ...: at most 5 inputs per run, each logged
budget() { local n; n=$(grep -c "^$1 " $OUT/taps.log 2>/dev/null); [ "${n:-0}" -lt 5 ] || { echo "$1 REFUSED $2" >> $OUT/taps.log; return 1; }; }
tap()   { budget $1 $2 || return 1; echo "$1 $2 tap $3 $4 $(date +%T)" >> $OUT/taps.log; adb -s $D shell input tap $3 $4; sleep 1.5; }
press() { budget $1 $2 || return 1; echo "$1 $2 press $3 $4 $(date +%T)" >> $OUT/taps.log; adb -s $D shell input swipe $3 $4 $3 $4 1200; sleep 0.5; }
swipe() { budget $1 $2 || return 1; echo "$1 $2 swipe $3 $4 $5 $6 $(date +%T)" >> $OUT/taps.log; adb -s $D shell input swipe $3 $4 $5 $6 400; sleep 1.5; }
den()  { adb -s $D shell wm density | grep -a -o '[0-9]*' | tail -1; }
# the live configuration; API 19 may have no `am get-config`, so fall back to dumpsys (round 3's hdp did the same)
conf() { local c; c=$(adb -s $D shell am get-config 2>/dev/null | grep -a -m1 -- '-w[0-9]*dp')
  [ -z "$c" ] && c=$(adb -s $D shell dumpsys activity activities | grep -a -m1 ' w[0-9]*dp'); echo "$c"; }
cfg()  { conf | grep -a -o -- '[- ]w[0-9]*dp\|[- ]h[0-9]*dp\|[- ]land\|[- ]port' | tr -d ' -' | head -3 | tr '\n' ' '; }
wdp()  { conf | grep -a -o -- '[- ]w[0-9]*dp' | head -1 | tr -dc 0-9; }
pref() { adb -s $D shell run-as $PKG cat shared_prefs/settings.xml | tr -d '\r' | grep -a -o "name=\"$1\"[^/]*" ; }
guard() { echo "$1 init=$(cnt $1 'WifiLauncher: Initializing WiFi Mode: ') match=$(cnt $1 'MATCH! Starting AapService') crash=$(cnt $1 'FATAL EXCEPTION')" | tee -a $OUT/guard.txt; }
void() { echo "$1 VOID $2" | tee -a $OUT/marks.txt; mark $1-end; guard $1; }
# host thermal (rig-quirks/topics/tooling.md)
th_gate() { $RIG_TOOLKIT/rig_thermal.sh wait 75 || $RIG_TOOLKIT/rig_thermal.sh wait 75 || { echo HOST_HOT; return 1; }
  $RIG_TOOLKIT/rig_thermal.sh watch $OUT/thermal-$1.log & THPID=$!; }
th_end() { kill $THPID 2>/dev/null; wait $THPID 2>/dev/null; }
```

**panel.sh** (D-HU only: `panel WIDE` or `panel NARROW`; prints the density it set and the width it read)

```bash
dens_found() { adb -s $HU shell wm density | tee $OUT/density-found.txt
  OVR=$(grep -a -o 'Override density: [0-9]*' $OUT/density-found.txt | tr -dc 0-9)
  D0=$OVR; [ -z "$D0" ] && D0=$(grep -a -o 'Physical density: [0-9]*' $OUT/density-found.txt | tr -dc 0-9)
  PW=$(adb -s $HU shell wm size | grep -a -o '[0-9]*x[0-9]*' | tail -1 | awk -Fx '{print ($1>$2)?$1:$2}')
  echo "D0=$D0 OVR=$OVR PW=$PW" | tee -a $OUT/density-found.txt; }
dens_restore() { if [ -n "$OVR" ]; then adb -s $HU shell wm density $OVR; else adb -s $HU shell wm density reset; fi
  sleep 3; adb -s $HU shell wm density | tee $OUT/density-restored.txt; }
# panel <WIDE|NARROW>: WIDE is the found density and MUST read >= 560dp; NARROW aims at 520dp and MUST read < 560dp
panel() { local d w
  if [ $1 = WIDE ]; then d=$D0; else d=$(( PW*160/520 )); fi
  adb -s $HU shell wm density $d; sleep 3; open -; w=$(wdp)
  [ $1 = NARROW ] && [ $w -ge 560 ] && { d=$((d+16)); adb -s $HU shell wm density $d; sleep 3; open -; w=$(wdp); }
  echo "$1 density=$d w=${w}dp cfg=$(cfg)" | tee -a $OUT/panels.txt
  if [ $1 = WIDE ] && [ $w -lt 560 ]; then echo "$1 UNTESTABLE" | tee -a $OUT/panels.txt; return 1; fi
  if [ $1 = NARROW ] && [ $w -ge 560 ]; then echo "$1 UNTESTABLE" | tee -a $OUT/panels.txt; return 1; fi; }
```

Every action in this round:

| Action | Command | Notes |
|---|---|---|
| Open settings, whole | `open -` | a fresh `SettingsActivity` |
| Open settings on one row | `open '<setting name>'` | `extra_search_query`; filters the list, no keyboard |
| Fold or unfold the rail | `tap <run> toggle $(ui desc <dump> 'Hide section names')` (or `'Show section names'`) | one dumped target, by its label |
| Jump to a section | `tap <run> jump $(ui item <dump> '<section>')` | a rail item, by title or by content-desc |
| Long press a folded icon | `press <run> tooltip $(ui item <dump> '<section>')` | `input swipe` on one point, 1.2 s |
| Scroll the rail | `swipe <run> rail <x> <y1> <x> <y2>` | R2 only, a bounded search |
| Open a row's help | `tap <run> help $(ui desc <dump> 'Help for <name>')` | |
| Close a dialog | `back` | `input keyevent 4` |
| Mark a step | `mark <label>` | `AutomationMarker: <label>` at WARN |
| Read the build | `send ACTION_QUERY_STATE` | `commit` on the `data=` line |

Every `send` must print `AutomationReceiver: ` in the capture. A step with no such line never landed; the run is void,
not a FAIL. If a target does not resolve (`ui` exits with `matches=`, `items=` or `rows=`), input nothing, record the
reason, and the run is void, except where a run says that the miss is a FAIL. Do not guess a coordinate.

## 6. The deciding lines

Log lines, verified with `grep -F -r` in `app/src/main` at `1c25716f`. Grep every capture with `-a`, inside the run's
marker window (`win <run>`), in `$OUT/cap-<serial>.txt`.

| Id | Line | Level | Read |
|---|---|---|---|
| L-HELP | `SettingHelpDialog: help opened for ` | DEBUG (`log-level=1`) | one per (?) tap; the setting id follows |
| L-RX | `AutomationReceiver: ` | INFO | every `send` landed |
| L-MARK | `AutomationMarker: ` | WARN | the run windows |
| L-ARM | `WifiLauncher: Initializing WiFi Mode: ` | INFO | MUST be 0 in every window |
| L-WAKE | `MATCH! Starting AapService` | INFO | MUST be 0 in every window |
| L-CRASH | `FATAL EXCEPTION` | system (`AndroidRuntime`) | MUST be 0 in every window |

L-HELP is written from a template, `AppLog.d("SettingHelpDialog: help opened for $id")`. The runs grep the prefix
followed by the id: `gpsNavigation` (R4), `showToastMessages` and `hideClock` (R5). Each id is a verbatim `grep -F`
match in `app/src/main` at `1c25716f`.

Dump strings, verified with `grep -F` in `app/src/main/res/values/strings.xml` at `1c25716f`. The XML escapes the
apostrophe as `\'`; the screen and the dump show a plain `'`.

| Id | String | From |
|---|---|---|
| U-FOLD | `Hide section names` | `settings_rail_collapse`, the chevron's label while the names show |
| U-UNFOLD | `Show section names` | `settings_rail_expand`, its label while the rail is folded |
| U-SEC | `General`, `Wireless Connection`, `Graphics`, `Video`, `Audio`, `Automation`, `Input Settings`, `Navigation`, `Theming`, `More Features`, `Backup and reset`, `Debug`, `Info` | the 13 `category_*` titles, in the new order |
| U-BT | `Bluetooth address` | `bluetooth_address_s` |
| U-GPS | `Use this unit's GPS in Android Auto` | `gps_for_navigation`, the new name |
| U-GPSS | `Android Auto takes the car's position from this unit, not from the phone.` | `gps_for_navigation_description` |
| U-GPSH1 | `Chooses where Android Auto gets the car's position` | start of `gps_for_navigation_help` |
| U-GPSH2 | `Turning this off does not turn off this unit's GPS.` | in `gps_for_navigation_help` |
| U-GPSOLD | `GPS for navigation` | the old name; MUST be absent |
| U-TOAST | `Show Toast Messages` | `show_toast_messages` |
| U-TOASTS | `Display status toast popups on connection, disconnect, and mode switching.` | `show_toast_messages_description` |
| U-TOASTH | `Shows short pop-up messages when a phone connects` | start of `show_toast_messages_help` |
| U-CLOCK | `Hide the Clock` | `hide_clock_label` |
| U-CLOCKS | `Ask Android Auto not to show its own clock.` | `hide_clock_summary` |
| U-CLOCKH | `Asks Android Auto not to show its own clock` | start of `hide_clock_help` |
| U-LBL | `Help for ` + the name | `setting_help_for` = `Help for %1$s` |

## 7. Pre-flight (5 min)

**preflight.sh** (run once per unit: `D=$HU`, then before Part B `D=$SAM`)

```bash
. ./lib.sh
adb -s $D shell am force-stop $PKG
adb -s $D shell run-as $PKG cat shared_prefs/settings.xml > $OUT/settings-backup-$D.xml
grep -a -o 'name="\(log-level\|app-language\|app-theme\|use-extreme-dark-mode\|ui-scale-settings-percent\|screen-orientation\|onboarding-version\|advanced-settings-active\|settings-rail-collapsed\|connection-modes\|gps-navigation\|show-toast-messages\|hide-clock\)"[^/]*' \
  $OUT/settings-backup-$D.xml | tee $OUT/pre-keys-$D.txt
[ $D = $HU ] && { adb -s $D shell svc bluetooth disable; sleep 4; }
adb -s $D shell dumpsys bluetooth_manager | grep -a -m2 -E 'enabled:|state:' | tee $OUT/pre-bt-$D.txt
adb -s $D shell date; date                                   # record both
# write section 4's keys with the app stopped; read every one back
adb -s $D shell run-as $PKG cat shared_prefs/settings.xml > $OUT/settings-seeded-$D.xml
adb -s $D logcat -G 16M; adb -s $D logcat -c
stdbuf -oL adb -s $D logcat -v time > $(cap) & echo $! > $OUT/cap-$D.pid
```

Then the identity checks of section 1 on that unit. Some key names in the grep may not exist on this unit; a missing
one is information.

## 8. Runs

Before each part: `th_gate <part>`; after it: `th_end`. One capture per unit. Each run sends `mark <run>-start` first
and `mark <run>-end` last, then `guard <run>`. Every run's PASS also needs `init=0 match=0 crash=0` on its `guard` line.

**The point of the round is R1.**

### Part A: D-HU (`D=$HU`)

1. `dens_found`, then `panel WIDE`. Run R1, R2, R3, R4, R5 in that order.
2. `panel NARROW`. Run R6.
3. `adb -s $HU shell wm density $D0` (the found density). Write `screen-orientation=4` with the app stopped, read it
   back. Run R7.
4. `dens_restore`, `kill $(cat $OUT/cap-$HU.pid)`, restore the backup (as root, `chown u0_a176:u0_a176`, `chmod 660`,
   `rig-quirks/units/D-HU.md`), diff it against the backup, `svc bluetooth enable`, and read Bluetooth back.

**r1.sh** (`r1 R1`, WIDE)

```bash
r1() { local R=$1 xy DN; DN=$(den); mark $R-start
  open -; dump $R-a 'General' || { void $R no-dump; return 1; }; shot $R-a
  echo "$R-a $(ui rail $OUT/$R-a-$D.xml $DN)" | tee -a $OUT/$R.txt
  xy=$(ui desc $OUT/$R-a-$D.xml 'Hide section names') || { echo "$R NO_TOGGLE" | tee -a $OUT/$R.txt; mark $R-end; guard $R; return 2; }
  tap $R fold $xy; dump $R-b; shot $R-b
  echo "$R-b $(ui rail $OUT/$R-b-$D.xml $DN)" | tee -a $OUT/$R.txt
  xy=$(ui item $OUT/$R-b-$D.xml 'Graphics') || { void $R no-graphics-icon; return 1; }
  tap $R jump $xy; dump $R-c; shot $R-c
  echo "$R-c $(ui rail $OUT/$R-c-$D.xml $DN) $(ui hdr $OUT/$R-c-$D.xml)" | tee -a $OUT/$R.txt
  xy=$(ui item $OUT/$R-c-$D.xml 'Video') && { press $R tooltip $xy; shot $R-tip; }
  adb -s $D shell am force-stop $PKG; sleep 1
  echo "$R-pref-after-fold $(pref settings-rail-collapsed)" | tee -a $OUT/$R.txt
  open -; dump $R-d; shot $R-d
  echo "$R-d $(ui rail $OUT/$R-d-$D.xml $DN)" | tee -a $OUT/$R.txt
  xy=$(ui desc $OUT/$R-d-$D.xml 'Show section names') || { void $R no-unfold-target; return 1; }
  tap $R unfold $xy; dump $R-e; shot $R-e
  echo "$R-e $(ui rail $OUT/$R-e-$D.xml $DN)" | tee -a $OUT/$R.txt
  adb -s $D shell am force-stop $PKG; sleep 1
  echo "$R-pref-after-unfold $(pref settings-rail-collapsed)" | tee -a $OUT/$R.txt
  mark $R-end; guard $R; }
```

R1 PASS needs all of these, read from `$OUT/R1.txt`:

- `R1-a`: `rail=1 toggle=Hide section names`, `titles=` 3 or more, `descs=0`. Record `railW` and `listX1`.
- `R1-b`: `rail=1 toggle=Show section names`, `titles=0`, `descs=` equal to `n=`, and `n=` 3 or more. `railW` from 60dp
  to 80dp. `listX1` of `R1-b` is at least 100dp less than `listX1` of `R1-a` (the list took the room).
- `R1-c`: `sel=Graphics`, and `first=Graphics` (the list scrolled to the Graphics header). `titles=0` still.
- `R1-pref-after-fold` reads `name="settings-rail-collapsed" value="true"`.
- `R1-d` (after a restart): `toggle=Show section names` and `titles=0`.
- `R1-e`: `toggle=Hide section names` and `titles=` 3 or more.
- `R1-pref-after-unfold` reads `value="false"`.
- `init=0 match=0 crash=0`.

If the change did nothing, `R1-a` reads `toggle=NONE` and the run prints `NO_TOGGLE`: that is a FAIL, not a void,
when `R1-a` reads `rail=1`. `R1-tip-<serial>.png` is information only: say whether a popup shows the name `Video`.

**r2.sh** (`r2 R2-ADV`, then write `advanced-settings-active=false` with the app stopped and read it back, then
`r2 R2-BAS`, then write it back to `true`)

```bash
r2() { local R=$1 i=0 bx x y1 y2; mark $R-start
  open -; dump $R-0 'General' || { void $R no-dump; return 1; }; shot $R-0
  ui names $OUT/$R-0-$D.xml > $OUT/$R-names.txt
  while ! grep -qx 'Info' $OUT/$R-names.txt && [ $i -lt 4 ]; do i=$((i+1))
    bx=$(ui railbox $OUT/$R-$((i-1))-$D.xml); [ "$bx" = NONE ] && break
    set -- $bx; x=$(( ($1+$3)/2 )); y1=$(( $4-30 )); y2=$(( $2+30 ))
    swipe $R rail $x $y1 $x $y2 || break
    dump $R-$i; ui names $OUT/$R-$i-$D.xml >> $OUT/$R-names.txt; done
  awk '!seen[$0]++' $OUT/$R-names.txt > $OUT/$R-order.txt
  echo "$R swipes=$i order=$(paste -sd'|' $OUT/$R-order.txt)" | tee -a $OUT/marks.txt
  mark $R-end; guard $R; }
```

R2-ADV PASS: `order=` is exactly
`General|Wireless Connection|Graphics|Video|Audio|Automation|Input Settings|Navigation|Theming|More Features|Backup and reset|Debug|Info`.

R2-BAS PASS: `order=` is exactly
`General|Wireless Connection|Graphics|Video|Audio|Automation|Input Settings|Navigation|Theming|More Features|Info`.
Basic has no row in Backup and reset or in Debug, so those two sections drop out.

If the change did nothing, the order would put More Features and Navigation above Graphics, and the Advanced list
would hold `UI`, `Backup` and `Reset` in place of `Backup and reset`. If `Info` is not found after 4 swipes, the arm is
INCONCLUSIVE (rail not read in full); report the partial `order=`. A partial order that already breaks the expected
sequence is a FAIL.

**r3.sh** (`r3 R3`; before it write `connection-modes` to `usb` only with the app stopped and read it back; after it
write both back)

```bash
r3() { local R=$1; mark $R-start
  open 'Bluetooth address'; dump $R-a 'Bluetooth address'; shot $R-a
  echo "$R $(ui row $OUT/$R-a-$D.xml 'Bluetooth address' 2>&1) $(ui hdr $OUT/$R-a-$D.xml)" | tee -a $OUT/$R.txt
  mark $R-end; guard $R; }
```

R3 PASS: the line has `under=General` and `onscreen=1`, and `hdrs=` does not hold `Wireless Connection`. If the change
did nothing, the row is hidden with Wireless Connection, so `ui row` prints `rows=0`: that is a FAIL, not a void, when
the dump holds the app's window.

**r4.sh** (`r4 R4`)

```bash
r4() { local R=$1 xy f; mark $R-start
  open 'Use this unit'; dump $R-a "Use this unit's GPS in Android Auto" || { void $R no-row; return 1; }
  shot $R-a; f=$(ui frame $OUT/$R-a-$D.xml)
  echo "$R-a $(ui row $OUT/$R-a-$D.xml "Use this unit's GPS in Android Auto" 2>&1) old=$(ui text $OUT/$R-a-$D.xml 'GPS for navigation')" | tee -a $OUT/$R.txt
  xy=$(ui desc $OUT/$R-a-$D.xml "Help for Use this unit's GPS in Android Auto") || { void $R no-target; return 1; }
  tap $R help $xy; waitline $R 'help opened for gpsNavigation' 5
  dump $R-b 'Chooses where Android Auto'; shot $R-b
  ui dialog $OUT/$R-b-$D.xml "Use this unit's GPS in Android Auto" $f "Chooses where Android Auto gets the car's position" \
    "Turning this off does not turn off this unit's GPS." > $OUT/$R-dialog.txt 2>&1
  back; dump $R-c "Use this unit's GPS in Android Auto"
  echo "$R-c $(ui row $OUT/$R-c-$D.xml "Use this unit's GPS in Android Auto" 2>&1) dlg=$(ui dlg $OUT/$R-c-$D.xml)" | tee -a $OUT/$R.txt
  mark $R-end; guard $R; }
```

R4 PASS needs all of these:

- `R4-a`: `summary=Android Auto takes the car's position from this unit, not from the phone.` exactly, `help=1`,
  `under=Navigation`, `old=0`.
- `cnt R4 'SettingHelpDialog: help opened for gpsNavigation'` = 1, and `cnt R4 'SettingHelpDialog: help opened for '` = 1.
- `R4-dialog.txt` reads `title_ok=1 text_ok=1 ok_label=OK fits=1`.
- The `switch=` value is the same on `R4-a` and `R4-c`, and `R4-c` has `dlg=0`.

If the change did nothing, the dump has no row by the new name, so the run voids at its first dump with `old=1` in a
dump of the old row. Record that case as a FAIL.

**r5.sh** (`r5 R5`)

```bash
r5() { local R=$1 xy f n s h i=0; mark $R-start
  for n in 'Show Toast Messages' 'Hide the Clock'; do i=$((i+1))
    case $n in 'Show Toast Messages') h='Shows short pop-up messages when a phone connects';;
               *) h='Asks Android Auto not to show its own clock';; esac
    open "$n"; dump $R-$i-a "$n" || { echo "$R-$i NO_ROW" | tee -a $OUT/$R.txt; continue; }
    shot $R-$i-a; f=$(ui frame $OUT/$R-$i-a-$D.xml)
    echo "$R-$i-a $(ui row $OUT/$R-$i-a-$D.xml "$n" 2>&1)" | tee -a $OUT/$R.txt
    xy=$(ui desc $OUT/$R-$i-a-$D.xml "Help for $n") || { echo "$R-$i NO_TARGET" | tee -a $OUT/$R.txt; continue; }
    tap $R help-$i $xy; dump $R-$i-b "$h"; shot $R-$i-b
    echo "$R-$i-dialog $(ui dialog $OUT/$R-$i-b-$D.xml "$n" $f "$h" 2>&1)" | tee -a $OUT/$R.txt
    back; dump $R-$i-c "$n"
    echo "$R-$i-c $(ui row $OUT/$R-$i-c-$D.xml "$n" 2>&1) dlg=$(ui dlg $OUT/$R-$i-c-$D.xml)" | tee -a $OUT/$R.txt; done
  mark $R-end; guard $R; }
```

R5 PASS needs all of these:

- `R5-1-a`: `summary=Display status toast popups on connection, disconnect, and mode switching.`, `help=1`,
  `under=General`.
- `R5-2-a`: `summary=Ask Android Auto not to show its own clock.`, `help=1`, `under=Theming`.
- Both dialog lines read `title_ok=1 text_ok=1 ok_label=OK fits=1`.
- `cnt R5 'SettingHelpDialog: help opened for showToastMessages'` = 1, `cnt R5 'SettingHelpDialog: help opened for hideClock'` = 1.
- On each pair, the `switch=` value is the same on `-a` and `-c`, and `-c` has `dlg=0`.

If the change did nothing, Hide the Clock would sit under `UI`, not `Theming`, and it would have no summary.

**r6.sh** (`r6 R6`, NARROW)

```bash
r6() { local R=$1 xy DN; DN=$(den); mark $R-start
  open -; dump $R-a 'General' || { void $R no-dump; return 1; }; shot $R-a
  echo "$R-a w=$(wdp)dp $(ui rail $OUT/$R-a-$D.xml $DN)" | tee -a $OUT/$R.txt
  xy=$(ui item $OUT/$R-a-$D.xml 'Graphics') || { echo "$R NO_ICON" | tee -a $OUT/$R.txt; mark $R-end; guard $R; return 2; }
  tap $R jump $xy; dump $R-b; shot $R-b
  echo "$R-b $(ui rail $OUT/$R-b-$D.xml $DN) $(ui hdr $OUT/$R-b-$D.xml)" | tee -a $OUT/$R.txt
  mark $R-end; guard $R; }
```

R6 PASS needs all of these:

- `R6-a`: `w=` under 560, `rail=1`, `toggle=NONE`, `titles=0`, `descs=` equal to `n=`, `n=` 3 or more, `railW` from
  60dp to 80dp.
- `R6-b`: `sel=Graphics` and `first=Graphics`.

`R1` left `settings-rail-collapsed=false`, so R6 also shows that the stored choice does not unfold the narrow rail. If
the change did nothing, `R6-a` reads `rail=0 cats=0` (no section navigation at all below 560dp): that is a FAIL.

**r7.sh** (`r7 R7`, portrait)

```bash
r7() { local R=$1 DN; DN=$(den); mark $R-start
  open -; dump $R-a 'General'; shot $R-a
  echo "$R-a cfg=$(cfg) $(ui rail $OUT/$R-a-$D.xml $DN)" | tee -a $OUT/$R.txt
  mark $R-end; guard $R; }
```

R7: if `cfg=` holds `land`, the verdict is INCONCLUSIVE (no rotation). With `port`, PASS needs `rail=0 toggle=NONE`,
`cats=1`, `titles=` 2 or more, and `horiz=1` (the chip row, one line of items).

### Part B: D-SAM (`D=$SAM`)

Pre-flight for D-SAM, then:

1. Write `screen-orientation=2` with the app stopped and read it back. Run S1.
2. Write `screen-orientation=4` with the app stopped and read it back. Run S2.
3. `kill $(cat $OUT/cap-$SAM.pid)`, restore the backup, and diff it against the backup with CRs and blank lines
   stripped.

**s1.sh** (`s1 S1`)

```bash
s1() { local R=$1 xy DN w; DN=$(den); mark $R-start
  open -; dump $R-a 'General' || { void $R no-dump; return 1; }; shot $R-a; w=$(wdp)
  echo "$R-a w=${w}dp cfg=$(cfg) $(ui rail $OUT/$R-a-$D.xml $DN)" | tee -a $OUT/$R.txt
  if [ $w -ge 560 ]; then
    xy=$(ui desc $OUT/$R-a-$D.xml 'Hide section names') || { echo "$R NO_TOGGLE" | tee -a $OUT/$R.txt; mark $R-end; guard $R; return 2; }
    tap $R fold $xy; dump $R-b; shot $R-b
    echo "$R-b $(ui rail $OUT/$R-b-$D.xml $DN)" | tee -a $OUT/$R.txt
  else cp $OUT/$R-a-$D.xml $OUT/$R-b-$D.xml; fi
  xy=$(ui item $OUT/$R-b-$D.xml 'Graphics') || { void $R no-graphics-icon; return 1; }
  tap $R jump $xy; dump $R-c; shot $R-c
  echo "$R-c $(ui rail $OUT/$R-c-$D.xml $DN) $(ui hdr $OUT/$R-c-$D.xml)" | tee -a $OUT/$R.txt
  mark $R-end; guard $R; }
```

S1 PASS: `crash=0`, and:

- With `w=` 560 or more: `S1-a` as `R1-a`, `S1-b` as `R1-b`, and `S1-c` as `R1-c`. The `listX1` comparison is
  between `S1-a` and `S1-b`.
- With `w=` under 560: `S1-a` as `R6-a`, and `S1-c` as `R6-b`.

**s2.sh** (`s2 S2`)

```bash
s2() { local R=$1 DN; DN=$(den); mark $R-start
  open -; dump $R-a 'General'; shot $R-a
  echo "$R-a cfg=$(cfg) $(ui rail $OUT/$R-a-$D.xml $DN)" | tee -a $OUT/$R.txt
  mark $R-end; guard $R; }
```

S2 PASS: `crash=0`, `cfg=` holds `port`, and `rail=0 toggle=NONE cats=1`, `titles=` 2 or more, `horiz=1`. If `cfg=`
holds `land`, S2 is INCONCLUSIVE (no rotation).

### Stop rule

- Retry a void run once. A run void twice is UNTESTABLE; say why.
- If 2 `open` calls in a row on one unit give `NO_DUMP`, stop that unit's part and mark its remaining runs UNTESTABLE.
- If `panel WIDE` prints `UNTESTABLE`, run R1 to R5 at the found density anyway and grade R1 as information only.
- If `panel NARROW` prints `UNTESTABLE`, R6 is UNTESTABLE (density).
- A host thermal stop follows `rig-quirks/topics/tooling.md`.

## 9. Do not re-run

Settled in rounds 1 and 3, and the holders, the dialog and the toolbar code did not change since:

- The (?) opens the help and leaves a switch, a slider or a picker alone (round 1 R1, round 3 R1, R3, R7).
- The (?) geometry on toggle rows at T2 and T1 (round 1 R3), and the long help fits at 150% scale (round 1 R4b).
- The help text is searchable (round 1 R5), and the toolbar (?) on five sub-screens (round 1 R6).
- The "(experimental)" label is gone, and plain rows show a summary (round 3 R2, R4).

The JVM suite covers every locale's strings, the 80 and 500 character limits, and the main and quick screen pairs
(4285 tests at `1c25716f`). Do not try to prove those on the rig, for example by changing the app language.

## 10. Report back

The numbers that decide shipping:

1. R1: the seven lines of `R1.txt`, with `toggle=`, `titles=`, `railW` and `listX1` on `R1-a` and `R1-b`, `sel=` and
   `first=` on `R1-c`, and both `settings-rail-collapsed` reads.
2. R2: the two `order=` strings and the swipe counts.
3. R6 and S1: the `rail` lines, with `w=`, `toggle=` and `railW`.

Also give the D-HU density found, set and restored with the width read at each panel, the `under=` of R3, R4 and R5,
R7 and S2 `cfg=` and `horiz=`, every `guard` line, the backup diffs, whether `R1-tip` shows the name, and every input
from `taps.log` (run, target, kind, coordinates).

The block below lists every app log string the runs grep. `FATAL EXCEPTION` (L-CRASH) is a system line, not an app
string, so it is not in the block. Each line of the block is a verbatim `grep -F` match in `app/src` at `1c25716f`.
Note the trailing space on four of them.

```log-strings
SettingHelpDialog: help opened for 
AutomationReceiver: 
AutomationMarker: 
WifiLauncher: Initializing WiFi Mode: 
MATCH! Starting AapService
```
