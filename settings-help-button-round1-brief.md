# settings-help-button, round 1 brief

Published name: `settings-help-button-round1-brief.md`. Results go in `settings-help-button-round1-results.md`.
Evidence: create the release `rig-evidence-settings-help-button` and upload `settings-help-button-round1-captures.zip`.

Units: D-HU (Part A) and D-SAM (Part B), one after the other. No phone takes part. D-POCO, D-MOTO and D-HP take no part.
D-HP is left out on purpose: no script reaches its settings screen (`rig-quirks/units/D-HP.md`).

Queue line: `settings-help-button-round1-brief.md` (C `ba1a5c6d` on `fork/feat/settings-help-button`, stacked on the
settings redesign `c43896b1`, not on `main`; one build, no baseline; units D-HU and D-SAM, no phone; R1 is the point).

Pairing: this round uses no phone and turns D-HU's and D-SAM's Bluetooth off. A round that uses only D-POCO, D-MOTO
or D-HP can run beside it (`TESTING-TEMPLATE.md` §3b). If this round runs second in a pair, its lock is
`/tmp/ohu-rig-help.lock`.

Hand steps: none. Injected taps: yes, on targets from a `uiautomator dump` only, at most 5 per run, never a search
typed, a scroll or a swipe (section 5). The search box is filled by an intent extra, not by typing.

**Time budget.** Pre-flight 5 min, build 25 min, Part A 35 min, Part B 15 min, thermal waits about 6 min. About
1 h 30 min in all.

| Run | Unit, panel tier | What | Taps | Minutes |
|---|---|---|---|---|
| R1 | D-HU T2, D-HU T1, D-SAM | **The point.** Audio Sink (?) opens its help; the switch does not move | 1 | 3 each |
| R2 | D-HU T2 | A tap on the row body still toggles the switch, and opens no help | 2 | 2 |
| R3 | D-HU T2, D-HU T1 | Row geometry: the (?) sits right after the name, the switch stays on the row | 0 | 3 each |
| R4 | D-HU T2, D-HU T1, D-SAM | A long help text (Wireless Mode) fits the screen | 1 | 2 each |
| R4b | D-HU T1 | The same at `ui-scale-settings-percent=150`, so the text must scroll | 1 | 3 |
| R5 | D-HU T2, D-SAM | A search for `calls` lists Audio Sink, which matches only on its help | 0 | 2 each |
| R6 | D-HU T2 (full), D-SAM (Keymap only) | Sub-screens: row (?) on Auto Start, toolbar (?) on five screens, none on About and Permissions | 2 | 8 / 2 |
| R8 | D-HU T2 | Extreme dark draws the outline glyph | 0 | 3 |

There is no R7. Plan R7 (TalkBack reads "Help for Audio Sink") is graded inside R1 from the button's
`content-desc`, which is the text TalkBack speaks (section 3).

## 1. Build and baseline

One arm. The branch stacks on the Material 3 settings redesign (`c43896b1`), not on `main`. History was rewritten
once before this round (fixups folded, three commits). Nothing on the transfer branch cites an older SHA of it.

| Arm | Ref | Commit | Tree |
|---|---|---|---|
| C (candidate) | `fork/feat/settings-help-button` | `ba1a5c6d59bce102be40d234ba0fe3d61fdf7f6d` | `e585a6bd86fde901ec7a3929d6186a121e335f5f` |

The dev host ran the JVM suite at `ba1a5c6d`: 3547 tests, 0 failures, 0 skipped. Do not run the unit tests on the
rig. No other queued round shares this base.

**get_apk.sh**

```bash
OUT=rig-data/rounds/settings-help-button-round1; mkdir -p $OUT
WT=$(ls -d worktrees/* 2>/dev/null | head -1)        # any existing worktree; else make one with wt-new.sh
git -C $WT fetch fork feat/settings-help-button
git -C $WT checkout --detach ba1a5c6d59bce102be40d234ba0fe3d61fdf7f6d
git -C $WT rev-parse 'HEAD^{tree}'                    # MUST print e585a6bd86fde901ec7a3929d6186a121e335f5f
$RIG_TOOLKIT/rig_thermal.sh wait 70
HUR_DIR=$WT MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh   # copy the APK out of apks/ to $OUT/apk-C.apk at once
md5sum $OUT/apk-C.apk                                 # record in Setup notes
RT=$(find $WT/app/build/intermediates/runtime_symbol_list -name R.txt | head -1); echo "$RT"
for n in keymapFragment autoConnectFragment dpiSettingsFragment customizationFragment loadingScreenFragment \
         autoStartFragment aboutFragment permissionsFragment; do
  awk -v n=$n '$1=="int"&&$2=="id"&&$3==n{print n, $4}' "$RT"; done | tee $OUT/dest-ids.txt
```

`dest-ids.txt` holds the `extra_destination` ids for R6. The dev host's build of the same tree gave
`keymapFragment 0x7f090177`, `autoConnectFragment 0x7f090072`, `dpiSettingsFragment 0x7f090102`,
`customizationFragment 0x7f0900de`, `loadingScreenFragment 0x7f090189`, `autoStartFragment 0x7f090073`,
`aboutFragment 0x7f09000e`, `permissionsFragment 0x7f090255`. Use the rig's own values, and record any difference.

Install with `adb -s <serial> install -r -d $OUT/apk-C.apk` on D-HU and on D-SAM. Never uninstall. Identity on each
unit after the install:

- `send ACTION_QUERY_STATE`: the `data=` line MUST carry commit `ba1a5c6d`.
- Pull the live APK (`adb shell pm path $PKG`, then `adb pull`) and run
  `unzip -p live.apk 'classes*.dex' | strings | grep -c SettingHelpDialog`. It MUST be 1 or more. The class is new on
  this branch.

## 2. What this is and why

On a short panel the settings list cuts a row's text. The redesign caps it at 1 line below 330dp of screen height and
at 2 lines from 330dp to 459dp. The Audio Sink text needed about 4 lines, so the sentence a driver needs (calls never
use this audio) was the one that was cut. The project owner asked for a small (?) button that opens the full text.

The branch does this (ticket `spec.md` and `plan-r1.md`):

- Each row shows a summary of at most 80 characters. The long text moves to help text.
- A (?) sits right after the setting name, on the name line, only on rows that have help text. A tap opens a dialog
  titled with the setting name, with the help text, a scroll when it is long, and an OK button. The tap never toggles
  the switch. Its accessibility label is "Help for <name>".
- Search matches help text too. A search for `calls` finds Audio Sink, whose summary does not say "calls".
- Row-based sub-screens (Auto Start, Mic, Quick Settings and others) get the row (?). Five sub-screens with their own
  layout get one (?) in the toolbar for the whole screen, before Save or Reset: Auto Connect, DPI, Keymap,
  Customization and Loading Screen. About, Support, Permissions and the network and USB lists get none.
- Extreme dark draws an outline glyph; the other themes draw a filled one.
- One `AppLog.d` line when a dialog opens: `SettingHelpDialog: help opened for <id>`. A row's id is its `stableId`
  (`enableAudioSink`, `wifiConnectionMode`); a toolbar's id is `screen:<name>` (`screen:keymap`).

The unit tests cover the filter, the policy that decides which rows show a (?), and the string length limits. They
cannot cover the holders, the dialog or the toolbar items. This round covers those, by dump, log and screenshot.

## 3. What is different about this round

- **No phone, no session.** Opening `SettingsActivity` directly does not start `AapService`. To keep a phone from
  waking the unit through the Bluetooth auto-start, turn D-HU's and D-SAM's Bluetooth off for the round and restore
  it at the end. Every run window MUST show 0 of `WifiLauncher: Initializing WiFi Mode: ` and 0 of
  `MATCH! Starting AapService`. A window with either is void; re-run it once.
- **Settings are reached by intent extras, not by scrolling.** `extra_search_query` fills the search box and filters
  the list to the matching rows (it also clears focus, so no keyboard opens). `extra_destination` opens a sub-screen
  by its nav id. Neither is typing or scrolling.
- **Two panel tiers on D-HU, by `wm density`.** The summary line cap depends on the screen height in dp. T2 is 330dp
  to 459dp (2 lines); T1 is 329dp or less (1 line). D-HU is 1440x720 at density 240; its natural height is very
  likely T2. T1 needs a higher density. `tier.sh` computes it, checks the result and restores the found density at
  the end. D-SAM runs at its own density, whatever tier that gives; record it.
- **English, for the search.** The `calls` search is an English word. Write `app-language=en` for the round on both
  units; the backup restores the found value at the end. Every dump of the settings list MUST hold a node with text
  `Audio Sink` or the run is a setup failure.
- **TalkBack is not switched on.** The label TalkBack speaks is the button's `content-desc`. R1 grades it from the
  dump. Whether a TalkBack user on Android 4.x can still reach the row's own click is not tested; say so in the
  results.
- **The dialog dump.** With a dialog open, `uiautomator dump` holds the dialog window. Its title is
  `:id/alertTitle`, its text `android:id/message`, its OK `android:id/button1`. The text node's `text` attribute
  holds the whole string even when part of it is scrolled out of view, so "reachable by scroll" needs no swipe.
- **Close a dialog with BACK** (`input keyevent 4`), a scripted keyevent, not a tap.
- **D-SAM:** `SettingsActivity` is not exported there, so `open` falls back to `run-as $PKG am start --user 0`.
  `adb exec-out` is not used; files are pulled. Tap the dump's bounds, never a screenshot pixel
  (`rig-quirks/units/D-SAM-and-D-HP.md`).
- **Expected INCONCLUSIVE:** R4 scroll on a tier where the text fits (the scroll line is information there; R4b is
  the scroll control); the name wrap (no row name is long enough to wrap at these widths; R3 records it if one does);
  R4b if the scale does not make the text overflow.

## 4. Settings keys

Back up each unit's `settings.xml` at the start (`$OUT/settings-backup-<serial>.xml`), restore it at the end, and diff
the restored file against the backup. State the delta in Setup notes. On D-HU write with `set_hu_settings_host.py`,
on D-SAM with `set_prefs_runas_host.py`, always with the app stopped, and read every key back.

**Both units, every run:**

```xml
<int name="log-level" value="1" />
<int name="onboarding-version" value="2" />
<string name="app-language">en</string>
<int name="app-theme" value="2" />
<boolean name="use-extreme-dark-mode" value="false" />
<int name="ui-scale-settings-percent" value="100" />
```

`log-level=1` is DEBUG, which `help opened for` needs (`AppLog.d`, no VERBOSE guard). `app-theme=2` is Dark, which
draws the filled glyph. Leave `screen-orientation` as found (D-SAM needs its `2`). Leave the rig's audio keys as found:
this round does not play audio.

**Per run:** R4b writes `ui-scale-settings-percent=150` and puts back `100` after. R8 writes `app-theme=3`
(Extreme dark) and puts back `2` after.

## 5. Helpers

Run every adb call in sequence, never two at once against one unit. Write the results file after every run.

**ui.py** (save it as `$OUT/ui.py`; `lib.sh` calls it there)

```python
# ui.py : read a uiautomator dump.  python3 -I ui.py <cmd> <dump.xml> [args]
import sys, re, xml.etree.ElementTree as ET
cmd, path, args = sys.argv[1], sys.argv[2], sys.argv[3:]
root = ET.parse(path).getroot(); nodes = list(root.iter("node"))
par = {c: p for p in root.iter() for c in p}
def B(n): return list(map(int, re.findall(r"-?\d+", n.get("bounds", "[0,0][0,0]"))))
def rid(n, s): return n.get("resource-id", "").endswith(":id/" + s)
def sub(n, s): return [d for d in n.iter("node") if rid(d, s)]
def anc(n):
    while n in par:
        n = par[n]; yield n
def row_of(n):
    for a in anc(n):
        if sub(a, "settingName"): return a
def row_named(name):
    rows = [row_of(x) for x in nodes if rid(x, "settingName") and x.get("text") == name]
    if len(rows) != 1: sys.exit("rows=%d" % len(rows))
    return rows[0]
def mid(b): return "%d %d" % ((b[0] + b[2]) // 2, (b[1] + b[3]) // 2)
def inside(b, f): return b[0] >= f[0] and b[1] >= f[1] and b[2] <= f[2] and b[3] <= f[3]
if cmd == "frame": print(",".join(map(str, B(nodes[0]))))
elif cmd == "text":        # count of nodes whose text contains args[0]
    print(sum(1 for n in nodes if args[0] in n.get("text", "")))
elif cmd == "helpcount":   # nodes labelled "Help for ..."
    print(sum(1 for n in nodes if (n.get("content-desc", "") + n.get("text", "")).startswith("Help for ")))
elif cmd == "desc":        # centre of the one node whose content-desc is args[0]
    m = [n for n in nodes if n.get("content-desc") == args[0]]
    if len(m) != 1: sys.exit("matches=%d" % len(m))
    print(mid(B(m[0])))
elif cmd == "firsthelp":   # centre of the first row (?) in the dump
    m = [n for n in nodes if rid(n, "settingHelp")]
    if not m: sys.exit("matches=0")
    print(mid(B(m[0])))
elif cmd == "summary":     # centre and text of the summary of the row named args[0]
    s = sub(row_named(args[0]), "settingDescription")
    if len(s) != 1: sys.exit("summaries=%d" % len(s))
    print(mid(B(s[0])) + "\t" + s[0].get("text", ""))
elif cmd == "switch":      # checked state of the switch in the row named args[0]
    s = sub(row_named(args[0]), "settingSwitch")
    if len(s) != 1: sys.exit("switches=%d" % len(s))
    print(s[0].get("checked"))
elif cmd == "rows":        # geometry of every row that shows a (?)
    f = B(nodes[0]); bad = 0; out = []
    for h in [n for n in nodes if rid(n, "settingHelp")]:
        r = row_of(h); nm = sub(r, "settingName")[0]; hb, nb, rb = B(h), B(nm), B(r)
        a = abs(hb[0] - nb[2]) <= 4                         # A: right after the name
        v = hb[1] < nb[3] and hb[3] > nb[1]                 # B: on the name line
        sw = sub(r, "settingSwitch"); c = True
        if sw:
            sb = B(sw[0]); c = sb[0] >= hb[2] and inside(sb, rb) and rb[2] <= f[2]   # C: switch on the row
        d = h.get("content-desc") == "Help for " + nm.get("text", "")              # D: the label
        ok = a and v and c and d; bad += not ok
        out.append("%s\tA=%d B=%d C=%d D=%d\thelp=%s\trowH=%d\tnameH=%d" % (nm.get("text"), a, v, c, d,
                   h.get("bounds"), rb[3] - rb[1], nb[3] - nb[1]))
    print("\n".join(out)); print("rows=%d fail=%d" % (len(out), bad))
elif cmd == "dialog":      # dialog <title> <substring> <screen x1,y1,x2,y2>
    f = list(map(int, args[2].split(",")))
    t = [n for n in nodes if rid(n, "alertTitle")]; m = [n for n in nodes if rid(n, "message")]
    b = [n for n in nodes if rid(n, "button1")]
    if len(t) != 1 or len(m) != 1 or len(b) != 1: sys.exit("title=%d message=%d ok=%d" % (len(t), len(m), len(b)))
    sc = any(a.get("scrollable") == "true" for a in anc(m[0]))
    print("title_ok=%d text_ok=%d ok_label=%s fits=%d scroll=%d" % (t[0].get("text") == args[0],
          args[1] in m[0].get("text", ""), b[0].get("text"), all(inside(B(x), f) for x in t + m + b), sc))
elif cmd == "toolbar":     # toolbar <title>: the screen (?) in the toolbar, and its place before Save or Reset
    tb = [n for n in nodes if rid(n, "toolbar")]
    if len(tb) != 1: sys.exit("toolbars=%d" % len(tb))
    want = "Help for " + args[0]
    h = [n for n in tb[0].iter("node") if want in (n.get("content-desc", ""), n.get("text", ""))]
    any_h = [n for n in tb[0].iter("node") if (n.get("content-desc", "") + n.get("text", "")).startswith("Help for ")]
    o = [n for n in nodes if rid(n, "reset_button_widget") or rid(n, "save_button_widget")]
    order = "NA" if not (len(h) == 1 and o) else ("OK" if B(h[0])[2] <= B(o[0])[0] else "BAD")
    print("help=%d toolbarhelp=%d order=%s%s" % (len(h), len(any_h), order, ("\t" + mid(B(h[0]))) if len(h) == 1 else ""))
```

**lib.sh**

```bash
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
SA=$PKG/com.andrerinas.openheadunit.main.SettingsActivity
HU=27870808938846; SAM=30041c35642d2200
OUT=rig-data/rounds/settings-help-button-round1; mkdir -p $OUT
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
# open <query|-> [dest-id hex]: a fresh settings screen, filtered or on a sub-screen
open() { adb -s $D shell am force-stop $PKG; sleep 1; local ex="" r
  [ "$1" != "-" ] && ex="--es extra_search_query '$1'"
  [ -n "$2" ] && ex="$ex --ei extra_destination $(printf '%d' $2)"
  r=$(adb -s $D shell "am start -n $SA $ex" 2>&1)
  echo "$r" | grep -qE 'Error|Exception' && r=$(adb -s $D shell "run-as $PKG am start --user 0 -n $SA $ex" 2>&1)
  echo "$D $1 $2: $r" >> $OUT/am-start.log; sleep 2; }
ui()   { python3 -I $OUT/ui.py "$@"; }
# dump <label> [text]: our window in $OUT/<label>-<serial>.xml; with [text], wait until a node holds it. 3 tries.
dump() { local f=$OUT/$1-$D.xml i; for i in 1 2 3; do
  adb -s $D shell uiautomator dump /sdcard/ohu-ui.xml >/dev/null 2>&1; sleep 0.3
  adb -s $D pull /sdcard/ohu-ui.xml $f >/dev/null 2>&1
  if grep -q "package=\"$PKG\"" $f 2>/dev/null; then [ -z "$2" ] && return 0; [ "$(ui text $f "$2")" -ge 1 ] && return 0; fi
  sleep 2; done; echo "$1 NO_DUMP" >> $OUT/marks.txt; return 1; }
shot() { adb -s $D shell screencap -p /sdcard/ohu-s.png; sleep 0.3; adb -s $D pull /sdcard/ohu-s.png $OUT/$1-$D.png >/dev/null; }
back() { adb -s $D shell input keyevent 4; sleep 1; }
# tap <run> <what> <x> <y>: at most 5 per run, each logged
tap() { local n; n=$(grep -c "^$1 " $OUT/taps.log 2>/dev/null); [ "${n:-0}" -ge 5 ] && { echo "$1 REFUSED $2" >> $OUT/taps.log; return 1; }
  echo "$1 $2 $3 $4 $(date +%T)" >> $OUT/taps.log; adb -s $D shell input tap $3 $4; sleep 1.5; }
hdp()  { local h; h=$(adb -s $D shell am get-config 2>/dev/null | grep -a -o -- '-h[0-9]*dp' | head -1 | tr -dc 0-9)
  [ -z "$h" ] && h=$(adb -s $D shell dumpsys activity activities | grep -a -o ' h[0-9]*dp' | head -1 | tr -dc 0-9); echo $h; }
guard() { echo "$1 init=$(cnt $1 'WifiLauncher: Initializing WiFi Mode: ') match=$(cnt $1 'MATCH! Starting AapService')" | tee -a $OUT/guard.txt; }
# host thermal (rig-quirks/topics/tooling.md)
th_gate() { $RIG_TOOLKIT/rig_thermal.sh wait 75 || $RIG_TOOLKIT/rig_thermal.sh wait 75 || { echo HOST_HOT; return 1; }
  $RIG_TOOLKIT/rig_thermal.sh watch $OUT/thermal-$1.log & THPID=$!; }
th_end() { kill $THPID 2>/dev/null; wait $THPID 2>/dev/null; }
```

**tier.sh** (D-HU only: `tier T2` or `tier T1`; prints the density it set and the height it read)

```bash
# found density: the override if there is one, else the physical one; restore it with dens_restore
dens_found() { adb -s $HU shell wm density | tee $OUT/density-found.txt
  D0=$(grep -a -o 'Override density: [0-9]*' $OUT/density-found.txt | tr -dc 0-9)
  OVR=$D0; [ -z "$D0" ] && D0=$(grep -a -o 'Physical density: [0-9]*' $OUT/density-found.txt | tr -dc 0-9)
  P=$(adb -s $HU shell wm size | grep -a -o '[0-9]*x[0-9]*' | tail -1 | awk -Fx '{print ($1<$2)?$1:$2}')
  open -; H0=$(hdp); BARS=$(( P*160/D0 - H0 )); echo "D0=$D0 OVR=$OVR P=$P H0=$H0 BARS=$BARS" | tee -a $OUT/density-found.txt; }
dens_restore() { if [ -n "$OVR" ]; then adb -s $HU shell wm density $OVR; else adb -s $HU shell wm density reset; fi
  sleep 3; adb -s $HU shell wm density | tee $OUT/density-restored.txt; }
# tier <T2|T1>: pick a density for 400dp (T2) or 300dp (T1); one correction of 10 dpi; else UNTESTABLE
tier() { local lo hi tg d h; if [ $1 = T2 ]; then lo=330; hi=459; tg=400; else lo=1; hi=329; tg=300; fi
  if [ $1 = T2 ] && [ $H0 -ge 330 ] && [ $H0 -le 459 ]; then d=$D0
  else d=$(( (P*160 + (tg+BARS)/2) / (tg+BARS) )); fi
  adb -s $HU shell wm density $d; sleep 3; open -; h=$(hdp)
  if [ $h -gt $hi ]; then d=$((d+10)); elif [ $h -lt $lo ]; then d=$((d-10)); fi
  if [ $h -gt $hi ] || [ $h -lt $lo ]; then adb -s $HU shell wm density $d; sleep 3; open -; h=$(hdp); fi
  echo "$1 density=$d h=${h}dp" | tee -a $OUT/tiers.txt
  [ $h -ge $lo ] && [ $h -le $hi ] || { echo "$1 UNTESTABLE" | tee -a $OUT/tiers.txt; return 1; }; }
```

Every action in this round:

| Action | Command | Notes |
|---|---|---|
| Open settings on one row | `open '<query>'` | `extra_search_query`; filters the list, no keyboard |
| Open a sub-screen | `open - <id from dest-ids.txt>` | `extra_destination` |
| Open a row's help | `tap <run> help $(ui desc <dump> 'Help for <name>')` | one dumped target, by its label |
| Open a screen's help | `tap <run> toolbar-help <x> <y>` from `ui toolbar` | one dumped target |
| Tap a row body | `tap <run> row $(ui summary <dump> 'Audio Sink' \| cut -f1)` | the summary text, away from the (?) |
| Close a dialog | `back` | `input keyevent 4` |
| Mark a step | `mark <label>` | `AutomationMarker: <label>` at WARN |
| Read the build | `send ACTION_QUERY_STATE` | `commit` on the `data=` line |
| Stop the app | `adb -s $D shell am force-stop $PKG` | also ends a pending change with no Save |

Every `send` must print `AutomationReceiver: ` in the capture. A step with no such line never landed; the run is void,
not a FAIL. If a tap target does not resolve (`ui` exits with `matches=` or `rows=`), tap nothing more, record the
reason, and the run is void. Do not guess a coordinate.

## 6. The deciding lines

Log lines, verified with `grep -F -r` in `app/src/main` at `ba1a5c6d`. Grep every capture with `-a`, inside the run's
marker window (`win <run>`), in `$OUT/cap-<serial>.txt`.

| Id | Line | Level | Read |
|---|---|---|---|
| L-HELP | `SettingHelpDialog: help opened for ` | DEBUG (`log-level=1`) | one per (?) tap; the id follows |
| L-RX | `AutomationReceiver: ` | INFO | every `send` landed |
| L-MARK | `AutomationMarker: ` | WARN | the run windows |
| L-ARM | `WifiLauncher: Initializing WiFi Mode: ` | INFO | MUST be 0 in every window (section 3) |
| L-WAKE | `MATCH! Starting AapService` | INFO | MUST be 0 in every window |

Dump strings, verified with `grep -F` in `app/src/main/res/values/strings.xml` at `ba1a5c6d`. `ui.py` reads them.

| Id | String | From |
|---|---|---|
| U-LBL | `Help for ` + the name | `setting_help_for` = `Help for %1$s` |
| U-SINK | `Audio Sink` | `enable_audio_sink` |
| U-SUM | `Play Android Auto sound on this unit. Off: the phone plays it.` | `enable_audio_sink_summary`; it has no "call" |
| U-CALL | `Calls never use this audio` | `enable_audio_sink_description`, the help text |
| U-WM | `Wireless Mode` | `wireless_mode` |
| U-WMT | `A change restarts the wireless setup.` | the end of `wireless_mode_description` (393 characters) |
| U-NONE | `No settings match` | `settings_search_no_results` |
| U-T | `Keymap`, `Auto-Connect Settings`, `Pixel density (DPI)`, `Customization`, `Loading screen` | the five toolbar titles |
| U-AS | `Start on screen on` | `auto_start_screen_on_label` (Auto Start row) |

## 7. Pre-flight (5 min)

**preflight.sh** (run once per unit: `D=$HU`, then before Part B `D=$SAM`)

```bash
. ./lib.sh
adb -s $D shell stat -c '%U:%G %a' /data/data/$PKG/shared_prefs 2>&1 | tee $OUT/pre-stat-$D.txt
adb -s $D shell am force-stop $PKG
adb -s $D shell run-as $PKG cat shared_prefs/settings.xml > $OUT/settings-backup-$D.xml
grep -a -o 'name="\(log-level\|app-language\|app-theme\|use-extreme-dark-mode\|ui-scale-settings-percent\|screen-orientation\|onboarding-version\)"[^/]*' \
  $OUT/settings-backup-$D.xml | tee $OUT/pre-keys-$D.txt
adb -s $D shell svc bluetooth disable; sleep 4
adb -s $D shell dumpsys bluetooth_manager | grep -a -m2 -E 'enabled:|state:' | tee $OUT/pre-bt-$D.txt
adb -s $D shell date; date                                   # record both (D-SAM clock check)
# write section 4's keys with the app stopped (set_hu_settings_host.py on D-HU, set_prefs_runas_host.py on D-SAM); read back
adb -s $D logcat -G 16M; adb -s $D logcat -c
stdbuf -oL adb -s $D logcat -v time > $(cap) & echo $! > $OUT/cap-$D.pid
```

Then the identity checks of section 1 on that unit.

## 8. Runs

Before each part: `th_gate <part>`; after it: `th_end`. One capture per unit. Each run sends `mark <run>-start`
first and `mark <run>-end` last, then `guard <run>`. Run ids carry the tier or unit: `R1-T2`, `R1-T1`, `R1-SAM`.

**The point of the round is R1.**

### Part A: D-HU (`D=$HU`)

`dens_found`, then `tier T2`. Run R1-T2, R2, R3-T2, R4-T2, R5-T2, R6, R8. Then `tier T1` and run R1-T1, R3-T1,
R4-T1, R4b. Then `dens_restore`, `kill $(cat $OUT/cap-$HU.pid)`, restore the backup, `svc bluetooth enable`.

**r1.sh** (`r1 R1-T2`)

```bash
r1() { local R=$1 xy f s0 s1; mark $R-start
  open 'Audio Sink'; dump $R-a 'Audio Sink' || { mark $R-end; return 1; }
  f=$(ui frame $OUT/$R-a-$D.xml); echo "$R frame=$f" >> $OUT/marks.txt
  ui rows $OUT/$R-a-$D.xml > $OUT/$R-rows.txt
  ui summary $OUT/$R-a-$D.xml 'Audio Sink' | cut -f2 > $OUT/$R-summary.txt
  s0=$(ui switch $OUT/$R-a-$D.xml 'Audio Sink'); shot $R-a
  xy=$(ui desc $OUT/$R-a-$D.xml 'Help for Audio Sink') || { echo "$R VOID no-target" >> $OUT/marks.txt; mark $R-end; return 1; }
  tap $R help $xy
  waitline $R 'help opened for enableAudioSink' 5
  dump $R-b 'Calls never use this audio'; shot $R-b
  ui dialog $OUT/$R-b-$D.xml 'Audio Sink' 'Calls never use this audio' $f > $OUT/$R-dialog.txt
  back; dump $R-c 'Audio Sink'; s1=$(ui switch $OUT/$R-c-$D.xml 'Audio Sink')
  echo "$R switch_before=$s0 switch_after=$s1 dialog_after_back=$(ui text $OUT/$R-c-$D.xml 'Calls never use this audio')" | tee -a $OUT/marks.txt
  mark $R-end; guard $R; }
```

R1 PASS needs all of these:

- `cnt <R> 'SettingHelpDialog: help opened for enableAudioSink'` = 1, and `cnt <R> 'SettingHelpDialog: help opened for '` = 1.
- `<R>-dialog.txt` reads `title_ok=1 text_ok=1 ok_label=OK fits=1`. Record `scroll=`.
- `switch_before` = `switch_after`, and both are `true` or `false` (not empty).
- `dialog_after_back=0` (BACK closed the dialog).
- The Audio Sink line of `<R>-rows.txt` has `D=1`, so the label is exactly `Help for Audio Sink` (the TalkBack text).
- `<R>-summary.txt` equals U-SUM.
- L-ARM = 0 and L-WAKE = 0 (`guard`).

If the change did nothing, there would be no `Help for Audio Sink` node and the run would void at `ui desc`. A tap
that missed would leave L-HELP at 0. The switch pair shows the tap did not reach the row.

**r2.sh** (`r2 R2`, D-HU T2 only, right after R1-T2)

```bash
r2() { local R=$1 xy s0 s1 s2; mark $R-start
  open 'Audio Sink'; dump $R-a 'Audio Sink' || { mark $R-end; return 1; }
  s0=$(ui switch $OUT/$R-a-$D.xml 'Audio Sink'); xy=$(ui summary $OUT/$R-a-$D.xml 'Audio Sink' | cut -f1)
  tap $R row $xy; dump $R-b 'Audio Sink'; s1=$(ui switch $OUT/$R-b-$D.xml 'Audio Sink')
  xy=$(ui summary $OUT/$R-b-$D.xml 'Audio Sink' | cut -f1)
  tap $R row-back $xy; dump $R-c 'Audio Sink'; s2=$(ui switch $OUT/$R-c-$D.xml 'Audio Sink')
  echo "$R s0=$s0 s1=$s1 s2=$s2" | tee -a $OUT/marks.txt
  adb -s $D shell am force-stop $PKG                      # no Save: the pending change is dropped
  adb -s $D shell run-as $PKG cat shared_prefs/settings.xml | grep -a -o 'name="enable-audio-sink"[^/]*' | tee -a $OUT/marks.txt
  mark $R-end; guard $R; }
```

R2 PASS: `s0` is `true` or `false`, `s1` is the opposite of `s0`, `s2` = `s0`, `cnt R2 'SettingHelpDialog: help opened for '` = 0, and the
`enable-audio-sink` read-back equals the backup's (absent in both counts as equal). L-ARM = 0 and L-WAKE = 0.

**r3.sh** (`r3 R3-T2`)

```bash
r3() { local R=$1 q i=0; mark $R-start
  for q in - 'Audio Sink' 'Wireless Mode'; do i=$((i+1))
    open "$q"; dump $R-$i || continue; shot $R-$i
    ui rows $OUT/$R-$i-$D.xml > $OUT/$R-rows-$i.txt; tail -1 $OUT/$R-rows-$i.txt | sed "s/^/$R-$i /" >> $OUT/marks.txt; done
  mark $R-end; guard $R; }
```

R3 PASS: in each of the three dumps, the last line of `<R>-rows-<i>.txt` reads `rows=N fail=0` with N ≥ 1. Dump 2
MUST list `Audio Sink` and dump 3 `Wireless Mode`. A row that fails names the check: A (not right after the name),
B (not on the name line), C (switch off the row or past the screen edge), D (wrong label). Record `rowH` and `nameH`
of the Audio Sink row at each tier (information: whether the 48dp button grew a 1-line row). If any `nameH` is more
than 1.6 times the Audio Sink `nameH` on the same tier, that name wrapped: say which, and A and B still apply.

**r4.sh** (`r4 R4-T2`; R4b is `r4 R4b` after writing `ui-scale-settings-percent=150` with the app stopped)

```bash
r4() { local R=$1 xy f; mark $R-start
  open 'Wireless Mode'; dump $R-a 'Wireless Mode' || { mark $R-end; return 1; }
  f=$(ui frame $OUT/$R-a-$D.xml)
  xy=$(ui desc $OUT/$R-a-$D.xml 'Help for Wireless Mode') || { echo "$R VOID no-target" >> $OUT/marks.txt; mark $R-end; return 1; }
  tap $R help $xy; waitline $R 'help opened for wifiConnectionMode' 5
  dump $R-b 'A change restarts the wireless setup.'; shot $R-b
  ui dialog $OUT/$R-b-$D.xml 'Wireless Mode' 'A change restarts the wireless setup.' $f | tee $OUT/$R-dialog.txt
  back; mark $R-end; guard $R; }
```

R4 PASS: `cnt <R> 'SettingHelpDialog: help opened for wifiConnectionMode'` = 1, and `<R>-dialog.txt` reads
`title_ok=1 text_ok=1 ok_label=OK fits=1`. Record `scroll=` (information).
R4b PASS: the same, plus `scroll=1`. With `scroll=0`, R4b is INCONCLUSIVE (the scale did not make the text overflow).
Put `ui-scale-settings-percent=100` back after R4b and read it back.

**r5.sh** (`r5 R5-T2`)

```bash
r5() { local R=$1; mark $R-start
  open calls; dump $R-a; shot $R-a
  echo "$R sink=$(ui text $OUT/$R-a-$D.xml 'Audio Sink') none=$(ui text $OUT/$R-a-$D.xml 'No settings match')" | tee -a $OUT/marks.txt
  ui summary $OUT/$R-a-$D.xml 'Audio Sink' | cut -f2 | tee $OUT/$R-summary.txt
  open zzqx; dump $R-b; echo "$R control_none=$(ui text $OUT/$R-b-$D.xml 'No settings match')" | tee -a $OUT/marks.txt
  mark $R-end; guard $R; }
```

R5 PASS: `sink` ≥ 1, `none` = 0, `control_none` = 1, and `<R>-summary.txt` does not contain `call` (check with
`grep -ci call` = 0). The name and the summary carry no "call", so the row is listed on its help text alone. The
`zzqx` control proves the seeded query filters the list.

**r6.sh** (`r6 R6`, D-HU T2; ids from `dest-ids.txt`)

```bash
did() { awk -v n=$1 '$1==n{print $2}' $OUT/dest-ids.txt; }
r6() { local R=$1 xy t; mark $R-start
  open - $(did autoStartFragment); dump $R-as 'Start on screen on'; shot $R-as
  echo "$R autostart toolbar: $(ui toolbar $OUT/$R-as-$D.xml 'Auto-start settings' | cut -f1) rows: $(ui rows $OUT/$R-as-$D.xml | tail -1)" | tee -a $OUT/marks.txt
  xy=$(ui firsthelp $OUT/$R-as-$D.xml) && { tap $R row-help $xy; waitline $R 'help opened for ' 5; back; }
  for t in keymapFragment:Keymap autoConnectFragment:'Auto-Connect Settings' dpiSettingsFragment:'Pixel density (DPI)' \
           customizationFragment:Customization loadingScreenFragment:'Loading screen'; do
    open - $(did ${t%%:*}); dump $R-${t%%:*} || continue; shot $R-${t%%:*}
    echo "$R ${t%%:*} $(ui toolbar $OUT/$R-${t%%:*}-$D.xml "${t#*:}")" | tee -a $OUT/marks.txt; done
  xy=$(grep -a "^$R keymapFragment " $OUT/marks.txt | tail -1 | cut -f2)
  [ -n "$xy" ] && { tap $R toolbar-help $xy; waitline $R 'help opened for screen:keymap' 5; back; }
  for t in aboutFragment permissionsFragment; do open - $(did $t); dump $R-$t
    echo "$R $t helpcount=$(ui helpcount $OUT/$R-$t-$D.xml)" | tee -a $OUT/marks.txt; done
  mark $R-end; guard $R; }
```

R6 PASS needs all of these:

- Auto Start: `toolbarhelp=0` and the rows line reads `fail=0` with N ≥ 1. Exactly one new L-HELP line after the
  row tap, and its id does not start with `screen:` (record the id).
- Each of the five screens: `help=1 toolbarhelp=1`. For Keymap, DPI and Auto Connect also `order=OK` (the (?) sits
  before Reset or Save). Customization and Loading screen have neither button, so `order=NA` is correct there.
- `cnt R6 'SettingHelpDialog: help opened for screen:keymap'` = 1.
- About and Permissions: `helpcount=0`.
- L-ARM = 0 and L-WAKE = 0.

The Auto Start toolbar title may differ from `Auto-start settings`; `toolbarhelp` counts any "Help for" in the
toolbar, so the check holds whatever the title is.

**r8.sh** (`r8 R8`, D-HU T2; write `app-theme=3` with the app stopped first, and `2` after)

```bash
r8() { local R=$1; mark $R-start
  open 'Audio Sink'; dump $R-a 'Audio Sink'; shot $R-a
  ui rows $OUT/$R-a-$D.xml > $OUT/$R-rows.txt; grep -a '^Audio Sink' $OUT/$R-rows.txt | tee -a $OUT/marks.txt
  mark $R-end; guard $R; }
```

R8 is graded by the host from two screenshots: `R1-T2-a-<serial>.png` (Dark) and `R8-a-<serial>.png` (Extreme dark).
Look at the (?) at the `help=` bounds of the Audio Sink line in each rows file. PASS: R1-T2 shows a filled disk with
a question mark, and R8 shows an outline ring with a question mark. Also `fail=0` in `R8-rows.txt`. This condition
needs the images, not an extract: say so in the results.

### Part B: D-SAM (`D=$SAM`)

Pre-flight for D-SAM, `hdp` once with `open -` (record the tier), then R1-SAM (`r1 R1-SAM`), R4-SAM (`r4 R4-SAM`),
R5-SAM (`r5 R5-SAM`) and R6-SAM. R6-SAM is the Keymap part only:

**r6s.sh**

```bash
r6s() { local R=$1 xy; mark $R-start
  open - $(did keymapFragment); dump $R-k; shot $R-k
  echo "$R keymapFragment $(ui toolbar $OUT/$R-k-$D.xml Keymap)" | tee -a $OUT/marks.txt
  xy=$(grep -a "^$R keymapFragment " $OUT/marks.txt | tail -1 | cut -f2)
  [ -n "$xy" ] && { tap $R toolbar-help $xy; waitline $R 'help opened for screen:keymap' 5; back; }
  mark $R-end; guard $R; }
```

Part B PASS conditions are R1's, R4's and R5's, and for R6-SAM `help=1 toolbarhelp=1 order=OK` and one
`help opened for screen:keymap`. Also run `ui rows` on `R1-SAM-a` (written by `r1`) and grade it as R3: `fail=0`.
Then `kill $(cat $OUT/cap-$SAM.pid)`, restore the backup, `svc bluetooth enable`. D-SAM's `run-as cat` adds `\r`, so strip
CRs and blank lines before the diff (`rig-quirks/units/D-SAM.md`).

### Stop rule

- Retry a void run once. A run void twice is UNTESTABLE; say why.
- If 2 `open` calls in a row on one unit give `NO_DUMP`, stop that unit's part and mark its remaining runs
  UNTESTABLE.
- If `tier T1` prints `UNTESTABLE`, skip the T1 runs and mark them UNTESTABLE (density).
- A host thermal stop follows `rig-quirks/topics/tooling.md`.

## 9. Do not re-run

Nothing is settled on hardware yet. The JVM suite covers the filter, the help policy and the string limits (3547
tests at `ba1a5c6d`): do not try to prove those again on the rig, for example by searching for more words.

## 10. Report back

The numbers that decide shipping:

1. R1 on all three: the L-HELP count, the dialog line, and `switch_before` against `switch_after`.
2. R3 and the R1-SAM rows check: `fail=` on every dump, per tier, with `rowH` of the Audio Sink row at T2 and T1.
3. R6: `help=` and `order=` for the five screens, and `helpcount` for About and Permissions.

Also give the D-HU density found and restored, the `h` read at each tier, D-SAM's `h`, and every injected tap from
`taps.log` (run, target, coordinates).
