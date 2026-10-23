# settings-help-button, round 3 brief

Published name: `settings-help-button-round3-brief.md`. Results go in `settings-help-button-round3-results.md`.
Evidence: upload `settings-help-button-round3-captures.zip` to the existing release `rig-evidence-settings-help-button`.

Units: D-HU (Part A), then D-SAM (Part B). No phone takes part. D-POCO, D-MOTO and D-HP take no part.

Queue line: `settings-help-button-round3-brief.md` (C `e3b2597c` on `fork/docs/call-audio-needs-hfp`, stacked on the
settings redesign `c43896b1`, not on `main`; one build, no baseline; units D-HU and D-SAM, no phone; R1 is the point).

Pairing: this round uses no phone. It turns D-HU's Bluetooth off. A round that uses only D-POCO, D-MOTO or D-HP can
run beside it (`TESTING-TEMPLATE.md` §3b). If this round runs second in a pair, its lock is `/tmp/ohu-rig-help.lock`.

Hand steps: none. Injected taps: yes, on targets from a `uiautomator dump` only, at most 5 per run, never a typed
search, a scroll or a swipe. This round uses at most 1 tap per run. The search box is filled by an intent extra.

**Time budget.** Pre-flight 5 min, build 25 min, Part A 30 min, Part B 8 min, thermal waits about 6 min. About
1 h 15 min in all.

| Run | Unit, panel tier | What | Taps | Minutes |
|---|---|---|---|---|
| R1 | D-HU TALL (point), T2, T1 | **The point.** The first slider row with a (?): Desaturation opens its help; the slider value does not move | 1 | 3 each |
| R2 | D-HU T2, T1 | A plain row (no switch) shows a summary line and its value; no (?) | 0 | 2 each |
| R3 | D-HU T2 | New help text on Audio Queue Capacity; the (?) opens help, not the picker | 1 | 3 |
| R4 | D-HU T2 | The "(experimental)" label is gone from AAC audio and AAudio output | 1 | 3 |
| R6 | D-HU T2 | The media button hint row is gone; its text is in the row's help, with all three options | 1 | 3 |
| R7 | D-HU T2 | A toggle that got help at the plan revise: Force Software Decoding; the switch does not move | 1 | 2 |
| R5 | D-SAM | Information only: does `extra_search_query` fill the search box with the orientation as found, and with it set to 0 | 0 | 6 |

There is no R8. The glyph per theme passed in round 1 and the holders did not change.

**There is no round 2 on this branch.** A round 2 brief was written for `c0e39bb9` and stopped at its own check
before it was published, so no rig ran it and there are no round 2 results. This round runs the same set of runs on
the compacted tip. The last results for this thread are `settings-help-button-round1-results.md`.

## 1. Build and baseline

One arm. The branch stacks on the Material 3 settings redesign (`c43896b1`), not on `main`. **History was rewritten
since round 1.** Fixups folded into the second commit, and one commit was added. Round 1's tip `ba1a5c6d` is no
longer on the branch. Commit 1 (`631599d1`) is unchanged. The branch has four commits: `631599d1`, `eee3b501`,
`dac779d4`, `e3b2597c`. The unpublished round 2 brief named `c0e39bb9`. That tip is also gone. Its tree differs from
this one in one string only, `enable_audio_sink_description` in `values-de`, `values-hu` and `values-ro` (a translation
fix). The English strings this round reads are the same.

| Arm | Ref | Commit | Tree |
|---|---|---|---|
| C (candidate) | `fork/docs/call-audio-needs-hfp` | `e3b2597ca30701807ac80b4334d33d4abe23dfd8` | `51ada2bb24ac83acb433e2c6c32dbdc75b7c3ae0` |

The dev host ran the JVM suite at this tree: 3550 tests, 0 failures, 0 skipped. Do not run the unit tests on the rig.
No other queued round shares this base.

**get_apk.sh**

```bash
OUT=$(pwd)/rig-data/rounds/settings-help-button-round3; mkdir -p $OUT
WT=$(ls -d worktrees/* 2>/dev/null | head -1)        # any existing worktree; else make one with wt-new.sh
git -C $WT fetch fork docs/call-audio-needs-hfp
git -C $WT checkout --detach e3b2597ca30701807ac80b4334d33d4abe23dfd8
git -C $WT rev-parse 'HEAD^{tree}'                    # MUST print 51ada2bb24ac83acb433e2c6c32dbdc75b7c3ae0
$RIG_TOOLKIT/rig_thermal.sh wait 70
HUR_DIR=$WT MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh   # copy the APK out of apks/ to $OUT/apk-C.apk at once
md5sum $OUT/apk-C.apk                                 # record in Setup notes
RT=$(find $WT/app/build/intermediates/runtime_symbol_list -name R.txt | head -1); echo "$RT"
for n in darkModeFragment; do
  awk -v n=$n '$1=="int"&&$2=="id"&&$3==n{print n, $4}' "$RT"; done | tee $OUT/dest-ids.txt
```

`dest-ids.txt` holds the `extra_destination` id for R1. The dev host's build of the same tree gave
`darkModeFragment 0x7f0900e2`. Use the rig's own value, and record any difference.

Install with `adb -s <serial> install -r -d $OUT/apk-C.apk` on D-HU and on D-SAM. Never uninstall. Identity on each
unit after the install:

- `send ACTION_QUERY_STATE`: the `data=` line MUST carry a commit that starts with `e3b2597ca307`.
- Pull the live APK (`adb shell pm path $PKG`, then `adb pull`). Then
  `unzip -p live.apk 'classes*.dex' | strings | grep -c SettingHelpDialog` MUST be 1 or more, and
  `unzip -p live.apk 'classes*.dex' | strings | grep -c mediaKeyRoutingHint` MUST be 0. Round 1's build had that row
  id. This build deletes it.

## 2. What this is and why

Round 1 passed on D-HU: the (?) opens the help, the switch does not move, the dialog fits, the toolbar (?) works on
five sub-screens. The operator did not approve the branch for two reasons. Many settings had no (?), and the AAC
audio text still said "(Experimental)". The engineer then asked for three changes (ticket `plan-r3.md`):

1. **A row audit.** Every settings row without a (?) was checked. 32 row constructors changed:
   - Three rows get help from strings that exist already. One of them is `aaDesaturation`, the first slider row with
     a (?).
   - Ten rows get new help text. `audioQueueCapacity` and `forceSoftwareDecoding` are two of them.
   - Five plain rows (`SettingEntry`) get a summary line, for example `aaExitAction` and `audioLatencyMultiplier`.
     No plain row had a summary before. The holder draws it, but no device has shown it.
   - Eight hint rows under a setting are deleted. Their text moves into the help of that setting. Two of them had one
     hint per option, so their help joins all three: "Always: ...", "Automatic: ...", "Never: ...".
2. **The label.** "(Experimental)" is removed from `use_aac_audio_description`, `use_aac_audio_summary` and
   `aaudio_output`, in all 26 string files.
3. **The search seed.** `extra_search_query` did nothing on D-SAM in round 1. This branch does not change the seed
   path, so the fault comes from the base branch. The plan has a theory: the activity is created again after a forced
   rotation, and the second one finds no extra. R5 gathers information for the base branch's author. It grades
   nothing.

The JVM suite covers the strings (the label is gone in every locale, the joined help holds each locale's three option
names), the 500-character limit, and that a setting on the main and quick screens carries the same help. It cannot
cover the holders. This round covers them by dump, log and screenshot.

## 3. What is different about this round

- **No phone, no session.** Opening `SettingsActivity` directly does not start `AapService`. Turn D-HU's Bluetooth
  off for the round, so a phone cannot wake the unit, and turn it on at the end. D-SAM's Bluetooth cannot be turned
  off from the shell (API 19, round 1); its guard is the count alone. Every run window MUST show 0 of
  `WifiLauncher: Initializing WiFi Mode: ` and 0 of `MATCH! Starting AapService`. A window with either is void;
  re-run it once.
- **R1 needs a tall panel, because the Desaturation row is low on its screen.** The Dark Mode screen shows the app
  theme, Extreme dark, the night mode, the monochrome switch, then the slider. At T2 (400dp) and T1 (300dp) the slider
  is very likely under the fold, and this round does not scroll. So R1 runs first at a third density, TALL (600dp to
  720dp), and that is the graded arm. R1-T2 and R1-T1 run only up to the first dump. If the row is not fully on the
  screen there, they are **INCONCLUSIVE (below the fold)**, which is expected, and nothing is tapped.
- **The slider shows only with the monochrome switch on and a night mode that is not Day.** Section 4 writes
  `aa-monochrome-enabled=true` and `night-mode=2` (Night) with the app stopped. This avoids a tap on the switch, which
  opens a "GLES required" dialog. No run presses Save, so nothing is stored by the app. Section 8 checks that.
- **Settings are reached by intent extras, not by scrolling.** `extra_search_query` fills the search box and filters
  the list on D-HU. `extra_destination` opens the Dark Mode screen by its nav id. Each search in this round names one
  setting by its full name, so the list holds one or two rows and the row is on the first screen. Round 1's `calls`
  search listed too many rows (its R5 setup note).
- **Panel tiers on D-HU, by `wm density`.** Round 1 found D-HU at density 240 with no override, 480dp tall, which is
  neither tier. T2 (density about 288) gives 400dp; T1 (about 384) gives 300dp; TALL (about 180) gives 640dp.
  `tier.sh` sets each one, reads the height back, and `dens_restore` puts the found density back.
- **English, for the names.** Write `app-language=en` on both units. The backup restores the found value.
- **The helpers carry round 1's fixes.** `row_of` climbs to the row card and stops at the list, so the summary, the
  value and the switch are in the row (round 1 setup note on `ui.py`). `dump` deletes the old file on the unit and on
  the host first, so a failed dump cannot return a stale screen (round 1 setup note on `r6.sh`). `OUT` is absolute.
- **The dialog dump.** With a dialog open, `uiautomator dump` holds the dialog window. Its title is
  `:id/alertTitle`, its text `android:id/message`, its OK `android:id/button1`. The text node holds the whole string
  even when part of it is out of view. A single-choice picker has no `message` node and has `CheckedTextView` rows,
  which is how R3 tells the help from the picker. `scroll=` was not stable in round 1, so this round does not read it.
- **Close a dialog with BACK** (`input keyevent 4`), a scripted keyevent, not a tap.
- **D-SAM:** `SettingsActivity` is not exported there, so `open` falls back to `run-as $PKG am start --user 0`.
  `run-as cat` adds `\r`; strip CRs and blank lines before any diff (`rig-quirks/units/D-SAM.md`).
- **Expected INCONCLUSIVE:** R1-T2 and R1-T1 (below the fold, see above).

## 4. Settings keys

Back up each unit's `settings.xml` at the start (`$OUT/settings-backup-<serial>.xml`), restore it at the end, and diff
the restored file against the backup. State the delta in Setup notes. On D-HU write with `set_hu_settings_host.py`,
on D-SAM with `set_prefs_runas_host.py`, always with the app stopped, and read every key back.

**Both units:**

```xml
<int name="log-level" value="1" />
<int name="onboarding-version" value="2" />
<string name="app-language">en</string>
<int name="app-theme" value="2" />
<boolean name="use-extreme-dark-mode" value="false" />
<int name="ui-scale-settings-percent" value="100" />
```

**D-HU only (R1):**

```xml
<int name="night-mode" value="2" />
<boolean name="aa-monochrome-enabled" value="true" />
```

`log-level=1` is DEBUG, which `help opened for` needs (`AppLog.d`, no VERBOSE guard). `app-theme=2` is Dark. Leave
`screen-orientation` as found on both units, except in R5's second arm. Leave the rig's audio keys as found
(`use-aac-audio`, `audio-latency-multiplier`, `audio-queue-capacity` are a deliberate worst case). This round plays no
audio, and R2 and R3 only read their displayed values.

**Per run:** R5's second arm writes `screen-orientation=0` on D-SAM. The backup restore puts the found value back.

## 5. Helpers

Run every adb call in sequence, never two at once against one unit. Write the results file after every run. Source
`lib.sh` from the rig root, so `OUT` resolves to `rig-data/rounds/settings-help-button-round3`.

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
F = B(nodes[0])
if cmd == "frame": print(",".join(map(str, F)))
elif cmd == "text":        # count of nodes whose text contains args[0]
    print(sum(1 for n in nodes if args[0] in n.get("text", "")))
elif cmd == "dlg":         # count of dialog titles on screen
    print(sum(1 for n in nodes if rid(n, "alertTitle")))
elif cmd == "box":         # the text in the settings search box
    m = [n for n in nodes if rid(n, "settingsSearch")]
    print(m[0].get("text", "") if m else "NO_BOX")
elif cmd == "desc":        # centre of the one node whose content-desc is args[0]
    m = [n for n in nodes if n.get("content-desc") == args[0]]
    if len(m) != 1: sys.exit("matches=%d" % len(m))
    print(mid(B(m[0])))
elif cmd == "row":         # row <name>: what the row card holds, tab-separated
    r = row_named(args[0]); rb = B(r)
    def first(*ids):
        m = [n for n in sub(r, *ids) if n.get("text", "")]
        return m[0] if m else None
    s = first("settingDescription"); v = first("settingValue", "settingValueInline")
    h = sub(r, "settingHelp"); sw = sub(r, "settingSwitch"); sl = sub(r, "settingSlider")
    seg = [n.get("text") for n in sub(r, "btnOption1", "btnOption2", "btnOption3") if n.get("checked") == "true"]
    parts = [x for x in (s, v) if x is not None] + h + sw + sl
    print("summary=%s\tvalue=%s\thelp=%d\tswitch=%s\tseg=%s\tslider=%d\tinrow=%d\tonscreen=%d\trowH=%d" % (
        s.get("text") if s is not None else "NONE", v.get("text") if v is not None else "NONE", len(h),
        sw[0].get("checked") if sw else "NA", ",".join(seg) if seg else "NA", len(sl),
        all(inside(B(x), rb) for x in parts), all(inside(B(x), F) for x in parts + [r]), rb[3] - rb[1]))
elif cmd == "rows":        # geometry of every row that shows a (?)
    bad = 0; out = []
    for h in [n for n in nodes if rid(n, "settingHelp")]:
        r = row_of(h)
        if r is None: bad += 1; out.append("NOROW\thelp=%s" % h.get("bounds")); continue
        nm = sub(r, "settingName")[0]; hb, nb, rb = B(h), B(nm), B(r)
        a = abs(hb[0] - nb[2]) <= 4                         # A: right after the name
        v = hb[1] < nb[3] and hb[3] > nb[1]                 # B: on the name line
        c = rb[2] <= F[2]                                   # C: the row and its control stay on the row
        for ctl in sub(r, "settingSwitch", "settingSlider", "buttonContainer"):
            cb = B(ctl); c = c and inside(cb, rb)
            if rid(ctl, "settingSwitch"): c = c and cb[0] >= hb[2]
        d = h.get("content-desc") == "Help for " + nm.get("text", "")   # D: the label
        ok = a and v and c and d; bad += not ok
        out.append("%s\tA=%d B=%d C=%d D=%d\thelp=%s\trowH=%d\tnameH=%d" % (nm.get("text"), a, v, c, d,
                   h.get("bounds"), rb[3] - rb[1], nb[3] - nb[1]))
    print("\n".join(out)); print("rows=%d fail=%d" % (len(out), bad))
elif cmd == "dialog":      # dialog <title> <screen x1,y1,x2,y2> <substring>...
    f = list(map(int, args[1].split(",")))
    t = [n for n in nodes if rid(n, "alertTitle")]; m = [n for n in nodes if rid(n, "message")]
    b = [n for n in nodes if rid(n, "button1")]
    items = sum(1 for n in nodes if n.get("class", "").endswith("CheckedTextView"))
    if len(t) != 1 or len(m) != 1 or len(b) != 1:
        sys.exit("title=%d message=%d ok=%d items=%d" % (len(t), len(m), len(b), items))
    msg = m[0].get("text", "")
    print("title_ok=%d text_ok=%d ok_label=%s fits=%d items=%d xp=%d len=%d" % (t[0].get("text") == args[0],
          all(x in msg for x in args[2:]), b[0].get("text"), all(inside(B(x), f) for x in t + m + b), items,
          "xperiment" in msg, len(msg)))
```

**lib.sh**

```bash
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
SA=$PKG/com.andrerinas.openheadunit.main.SettingsActivity
HU=27870808938846; SAM=30041c35642d2200
OUT=$(pwd)/rig-data/rounds/settings-help-button-round3; mkdir -p $OUT
export LC_ALL=C
D=$HU                                   # the unit under test: D=$SAM for Part B
send() { a=$1; shift; adb -s $D shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; }
mark() { send ACTION_LOG_MARKER --es text "$1" >/dev/null; echo "$1 $(date '+%m-%d %H:%M:%S')" >> $OUT/marks.txt; }
cap()  { echo $OUT/cap-$D.txt; }
win()  { sed -n "/AutomationMarker: $1-start/,/AutomationMarker: $1-end/p" $(cap); }
cnt()  { win $1 | grep -acF -- "$2"; }
did()  { awk -v n=$1 '$1==n{print $2}' $OUT/dest-ids.txt; }
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
  rm -f $f; adb -s $D shell rm -f /sdcard/ohu-ui.xml; sleep 0.3
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
void() { echo "$1 VOID $2" | tee -a $OUT/marks.txt; mark $1-end; guard $1; }
# host thermal (rig-quirks/topics/tooling.md)
th_gate() { $RIG_TOOLKIT/rig_thermal.sh wait 75 || $RIG_TOOLKIT/rig_thermal.sh wait 75 || { echo HOST_HOT; return 1; }
  $RIG_TOOLKIT/rig_thermal.sh watch $OUT/thermal-$1.log & THPID=$!; }
th_end() { kill $THPID 2>/dev/null; wait $THPID 2>/dev/null; }
```

**tier.sh** (D-HU only: `tier TALL`, `tier T2` or `tier T1`; prints the density it set and the height it read)

```bash
# found density: the override if there is one, else the physical one; restore it with dens_restore
dens_found() { adb -s $HU shell wm density | tee $OUT/density-found.txt
  D0=$(grep -a -o 'Override density: [0-9]*' $OUT/density-found.txt | tr -dc 0-9)
  OVR=$D0; [ -z "$D0" ] && D0=$(grep -a -o 'Physical density: [0-9]*' $OUT/density-found.txt | tr -dc 0-9)
  P=$(adb -s $HU shell wm size | grep -a -o '[0-9]*x[0-9]*' | tail -1 | awk -Fx '{print ($1<$2)?$1:$2}')
  open -; H0=$(hdp); BARS=$(( P*160/D0 - H0 )); echo "D0=$D0 OVR=$OVR P=$P H0=$H0 BARS=$BARS" | tee -a $OUT/density-found.txt; }
dens_restore() { if [ -n "$OVR" ]; then adb -s $HU shell wm density $OVR; else adb -s $HU shell wm density reset; fi
  sleep 3; adb -s $HU shell wm density | tee $OUT/density-restored.txt; }
# tier <TALL|T2|T1>: pick a density for 640dp, 400dp or 300dp; one correction of 10 dpi; else UNTESTABLE
tier() { local lo hi tg d h
  case $1 in TALL) lo=600; hi=720; tg=640;; T2) lo=330; hi=459; tg=400;; *) lo=1; hi=329; tg=300;; esac
  d=$(( (P*160 + (tg+BARS)/2) / (tg+BARS) ))
  adb -s $HU shell wm density $d; sleep 3; open -; h=$(hdp)
  if [ $h -gt $hi ]; then d=$((d+10)); elif [ $h -lt $lo ]; then d=$((d-10)); fi
  if [ $h -gt $hi ] || [ $h -lt $lo ]; then adb -s $HU shell wm density $d; sleep 3; open -; h=$(hdp); fi
  echo "$1 density=$d h=${h}dp" | tee -a $OUT/tiers.txt
  [ $h -ge $lo ] && [ $h -le $hi ] || { echo "$1 UNTESTABLE" | tee -a $OUT/tiers.txt; return 1; }; }
```

Every action in this round:

| Action | Command | Notes |
|---|---|---|
| Open settings on one row | `open '<full setting name>'` | `extra_search_query`; filters the list, no keyboard |
| Open Dark Mode | `open - $(did darkModeFragment)` | `extra_destination` |
| Open a row's help | `tap <run> help $(ui desc <dump> 'Help for <name>')` | one dumped target, by its label |
| Close a dialog | `back` | `input keyevent 4` |
| Mark a step | `mark <label>` | `AutomationMarker: <label>` at WARN |
| Read the build | `send ACTION_QUERY_STATE` | `commit` on the `data=` line |
| Stop the app | `adb -s $D shell am force-stop $PKG` | also drops a pending change with no Save |

Every `send` must print `AutomationReceiver: ` in the capture. A step with no such line never landed; the run is void,
not a FAIL. If a tap target does not resolve (`ui` exits with `matches=` or `rows=`), tap nothing, record the reason,
and the run is void, except where a run says that `rows=0` is a FAIL. Do not guess a coordinate.

## 6. The deciding lines

Log lines, verified with `grep -F -r` in `app/src/main` at `e3b2597c`. Grep every capture with `-a`, inside the run's
marker window (`win <run>`), in `$OUT/cap-<serial>.txt`.

| Id | Line | Level | Read |
|---|---|---|---|
| L-HELP | `SettingHelpDialog: help opened for ` | DEBUG (`log-level=1`) | one per (?) tap; the `stableId` follows |

The source writes L-HELP from a template, `AppLog.d("SettingHelpDialog: help opened for $id")` in
`SettingHelpDialog.kt`. The app fills in the setting id at run time. So a line in the capture is the prefix followed
by the id, for example `SettingHelpDialog: help opened for aaDesaturation`. The runs grep that full line. `grep -F`
finds only the prefix in `app/src`, and each of the five ids as its own string:

| Run | Id that follows the prefix |
|---|---|
| R1 | `aaDesaturation` |
| R3 | `audioQueueCapacity` |
| R4 | `useAacAudio` |
| R6 | `mediaKeyRouting` |
| R7 | `forceSoftwareDecoding` |
| L-RX | `AutomationReceiver: ` | INFO | every `send` landed |
| L-MARK | `AutomationMarker: ` | WARN | the run windows |
| L-ARM | `WifiLauncher: Initializing WiFi Mode: ` | INFO | MUST be 0 in every window (section 3) |
| L-WAKE | `MATCH! Starting AapService` | INFO | MUST be 0 in every window |
| L-DISP | `Displayed ` on a line that also holds `SettingsActivity` | system (`ActivityManager`) | R5 only, information |

Dump strings, verified with `grep -F` in `app/src/main/res/values/strings.xml` at `e3b2597c`. `ui.py` reads them.

| Id | String | From |
|---|---|---|
| U-LBL | `Help for ` + the name | `setting_help_for` = `Help for %1$s` |
| U-MONO | `Make Android Auto Monochrome` | `aa_monochrome` (the row above the slider) |
| U-DES | `Desaturation` | `aa_desaturation` |
| U-DESH | `Controls how much color is removed` | start of `aa_desaturation_description`, the help |
| U-EXIT | `Android Auto Exit Action` | `pref_aa_exit_action_title` |
| U-EXITS | `Action when tapping Exit inside the Android Auto app drawer.` | `pref_aa_exit_action_summary`, the new summary |
| U-LAT | `Audio Latency Multiplier` | `audio_latency_multiplier` |
| U-LATS | `Adjust audio buffer size (Lower = Less Lag, Higher = Less Stutter).` | `audio_latency_multiplier_description` |
| U-AQ | `Audio Queue Capacity (Backpressure)` | `audio_queue_capacity` |
| U-AQH | `How much received audio can wait before it plays.` | start of `audio_queue_capacity_help` (new) |
| U-AAC | `Use AAC Audio` | `use_aac_audio` |
| U-AACS | `Use AAC audio to save bandwidth.` | `use_aac_audio_summary`, label removed |
| U-AACH | `Use AAC codec for audio to save bandwidth.` | start of `use_aac_audio_description`, the help |
| U-AAU | `AAudio output` | `aaudio_output`, label removed (whole name) |
| U-XP | `xperiment` | the label, any case of its first letter; MUST be absent |
| U-MK | `Send Media Buttons To Android Auto` | `media_key_routing` |
| U-MKH | `Always: `, `Automatic: `, `Never: ` | the joined `media_key_routing_help` |
| U-HINT | `Next, previous and play/pause`, `Stops sending them while`, `Never sends them` | the three old hint texts, now only in the help |
| U-FSD | `Force Software Decoding` | `force_software_decoding` |
| U-FSDH | `Decodes the video on the main processor` | start of `force_software_decoding_help` (new) |
| U-NONE | `No settings match` | `settings_search_no_results` |

The search `Force Software Decoding` also lists the Software Video Decoder row, because its new help text names that
setting. That is correct; the run reads only the row named U-FSD.

## 7. Pre-flight (5 min)

**preflight.sh** (run once per unit: `D=$HU`, then before Part B `D=$SAM`)

```bash
. ./lib.sh
adb -s $D shell stat -c '%U:%G %a' /data/data/$PKG/shared_prefs 2>&1 | tee $OUT/pre-stat-$D.txt
adb -s $D shell am force-stop $PKG
adb -s $D shell run-as $PKG cat shared_prefs/settings.xml > $OUT/settings-backup-$D.xml
grep -a -o 'name="\(log-level\|app-language\|app-theme\|use-extreme-dark-mode\|ui-scale-settings-percent\|screen-orientation\|onboarding-version\|night-mode\|aa-monochrome-enabled\|aa-desaturation-level\|media-key-routing\|force-software-decoding\|use-aac-audio\|use-aaudio-output\|audio-queue-capacity\|audio-latency-multiplier\|aa-exit-action\)"[^/]*' \
  $OUT/settings-backup-$D.xml | tee $OUT/pre-keys-$D.txt
[ $D = $HU ] && { adb -s $D shell svc bluetooth disable; sleep 4; }
adb -s $D shell dumpsys bluetooth_manager | grep -a -m2 -E 'enabled:|state:' | tee $OUT/pre-bt-$D.txt
adb -s $D shell date; date                                   # record both
# write section 4's keys with the app stopped (set_hu_settings_host.py on D-HU, set_prefs_runas_host.py on D-SAM); read back
adb -s $D shell run-as $PKG cat shared_prefs/settings.xml > $OUT/settings-seeded-$D.xml
adb -s $D logcat -G 16M; adb -s $D logcat -c
stdbuf -oL adb -s $D logcat -v time > $(cap) & echo $! > $OUT/cap-$D.pid
```

Then the identity checks of section 1 on that unit.

## 8. Runs

Before each part: `th_gate <part>`; after it: `th_end`. One capture per unit. Each run sends `mark <run>-start` first
and `mark <run>-end` last, then `guard <run>`. Run ids carry the tier: `R1-TALL`, `R1-T2`, `R1-T1`, `R2-T2`, `R2-T1`.

**The point of the round is R1-TALL.**

### Part A: D-HU (`D=$HU`)

1. `dens_found`, then `tier TALL`. Run R1-TALL.
2. `tier T2`. Run R1-T2, R2-T2, R3, R4, R6, R7.
3. `tier T1`. Run R1-T1, R2-T1.
4. `adb -s $HU shell am force-stop $PKG`, then the no-Save check below.
5. `dens_restore`, `kill $(cat $OUT/cap-$HU.pid)`, restore the backup (as root, `chown u0_a176:u0_a176`, `chmod 660`,
   `rig-quirks/units/D-HU.md`), diff it against the backup, `svc bluetooth enable`, and read Bluetooth back.

**r1.sh** (`r1 R1-TALL`, then `r1 R1-T2` and `r1 R1-T1`)

```bash
r1() { local R=$1 xy f v0 v1; mark $R-start
  open - $(did darkModeFragment); dump $R-a 'Make Android Auto Monochrome' || { void $R no-monochrome-row; return 1; }
  shot $R-a; f=$(ui frame $OUT/$R-a-$D.xml)
  ui row $OUT/$R-a-$D.xml 'Desaturation' > $OUT/$R-row.txt 2>&1
  grep -q 'onscreen=1' $OUT/$R-row.txt || { echo "$R BELOW_FOLD $(cat $OUT/$R-row.txt)" | tee -a $OUT/marks.txt
    mark $R-end; guard $R; return 2; }
  ui rows $OUT/$R-a-$D.xml > $OUT/$R-rows.txt
  v0=$(cut -f2 $OUT/$R-row.txt)
  xy=$(ui desc $OUT/$R-a-$D.xml 'Help for Desaturation') || { void $R no-target; return 1; }
  tap $R help $xy; waitline $R 'help opened for aaDesaturation' 5
  dump $R-b 'Controls how much color is removed'; shot $R-b
  ui dialog $OUT/$R-b-$D.xml 'Desaturation' $f 'Controls how much color is removed' > $OUT/$R-dialog.txt 2>&1
  back; dump $R-c 'Make Android Auto Monochrome'
  v1=$(ui row $OUT/$R-c-$D.xml 'Desaturation' 2>&1 | cut -f2)
  echo "$R before=$v0 after=$v1 dlg_after_back=$(ui dlg $OUT/$R-c-$D.xml)" | tee -a $OUT/marks.txt
  mark $R-end; guard $R; }
```

R1-TALL PASS needs all of these:

- `cnt R1-TALL 'SettingHelpDialog: help opened for aaDesaturation'` = 1, and
  `cnt R1-TALL 'SettingHelpDialog: help opened for '` = 1.
- `R1-TALL-row.txt` reads `help=1`, `slider=1`, `inrow=1`, `onscreen=1`.
- The `Desaturation` line of `R1-TALL-rows.txt` reads `A=1 B=1 C=1 D=1`, and its last line reads `fail=0`. C=1 here
  means the slider sits inside the row card. D=1 means the label is exactly `Help for Desaturation`.
- `R1-TALL-dialog.txt` reads `title_ok=1 text_ok=1 ok_label=OK fits=1 items=0`.
- `before` = `after`, and both are `value=` followed by a percentage (for example `value=100%`).
- `dlg_after_back=0`.
- L-ARM = 0 and L-WAKE = 0 (`guard`).

If the change did nothing, the row would have no `Help for Desaturation` node and the run would void at `ui desc`
with `help=0` in the row file. That is a FAIL, not a void: record it as FAIL when `R1-TALL-row.txt` shows
`onscreen=1` and `help=0`.

R1-T2 and R1-T1: when the run prints `BELOW_FOLD`, the verdict is INCONCLUSIVE (below the fold); record `rowH` and the
row line. When the row is on the screen, grade the run as R1-TALL.

**r2.sh** (`r2 R2-T2`, then `r2 R2-T1`)

```bash
r2() { local R=$1 i=0 n; mark $R-start
  for n in 'Android Auto Exit Action' 'Audio Latency Multiplier'; do i=$((i+1))
    open "$n"; dump $R-$i "$n" || continue; shot $R-$i
    echo "$R-$i $n	$(ui row $OUT/$R-$i-$D.xml "$n" 2>&1)" | tee -a $OUT/$R-row.txt; done
  mark $R-end; guard $R; }
```

R2 PASS, per tier: `<R>-row.txt` has two lines, and on both:

- `summary=` is the exact string: U-EXITS on the U-EXIT line, U-LATS on the U-LAT line.
- `value=` is not `NONE` (record it; the latency value is the rig's own setting).
- `help=0`, `inrow=1`, `onscreen=1`.
- `cnt <R> 'SettingHelpDialog: help opened for '` = 0. L-ARM = 0 and L-WAKE = 0.

If the change did nothing, `summary=NONE` on both lines (these rows had no summary in round 1). A `rows=0` line means
the search did not list the row: the run is void, re-run it once. Record `rowH` at each tier as information.

**r3.sh** (`r3 R3`, T2)

```bash
r3() { local R=$1 xy f; mark $R-start
  open 'Audio Queue Capacity'; dump $R-a 'Audio Queue Capacity (Backpressure)' || { void $R no-row; return 1; }
  shot $R-a; f=$(ui frame $OUT/$R-a-$D.xml)
  ui row $OUT/$R-a-$D.xml 'Audio Queue Capacity (Backpressure)' > $OUT/$R-row-a.txt 2>&1
  ui rows $OUT/$R-a-$D.xml > $OUT/$R-rows.txt
  xy=$(ui desc $OUT/$R-a-$D.xml 'Help for Audio Queue Capacity (Backpressure)') || { void $R no-target; return 1; }
  tap $R help $xy; waitline $R 'help opened for audioQueueCapacity' 5
  dump $R-b 'How much received audio'; shot $R-b
  ui dialog $OUT/$R-b-$D.xml 'Audio Queue Capacity (Backpressure)' $f 'How much received audio can wait before it plays.' \
    > $OUT/$R-dialog.txt 2>&1
  back; dump $R-c 'Audio Queue Capacity (Backpressure)'
  ui row $OUT/$R-c-$D.xml 'Audio Queue Capacity (Backpressure)' > $OUT/$R-row-c.txt 2>&1
  echo "$R dlg_after_back=$(ui dlg $OUT/$R-c-$D.xml)" | tee -a $OUT/marks.txt
  mark $R-end; guard $R; }
```

R3 PASS needs all of these:

- `cnt R3 'SettingHelpDialog: help opened for audioQueueCapacity'` = 1, and `cnt R3 'SettingHelpDialog: help opened for '` = 1.
- `R3-dialog.txt` reads `title_ok=1 text_ok=1 ok_label=OK fits=1 items=0`. `items=0` and one `message` node mean the
  tap opened the help, not the capacity picker. If `ui dialog` exits with `message=0` and `items` above 0, the (?) opened
  the picker: that is a FAIL.
- `R3-row-a.txt` reads `help=1`; the `value=` field is the same in `R3-row-a.txt` and `R3-row-c.txt`.
- `R3-rows.txt` ends `fail=0`. `dlg_after_back=0`. L-ARM = 0 and L-WAKE = 0.

**r4.sh** (`r4 R4`, T2)

```bash
r4() { local R=$1 xy f; mark $R-start
  open 'Use AAC Audio'; dump $R-a 'Use AAC Audio' || { void $R no-aac-row; return 1; }
  shot $R-a; f=$(ui frame $OUT/$R-a-$D.xml)
  echo "$R-a	$(ui row $OUT/$R-a-$D.xml 'Use AAC Audio' 2>&1)	xp=$(ui text $OUT/$R-a-$D.xml xperiment)" | tee -a $OUT/$R-row.txt
  xy=$(ui desc $OUT/$R-a-$D.xml 'Help for Use AAC Audio') || { void $R no-target; return 1; }
  tap $R help $xy; waitline $R 'help opened for useAacAudio' 5
  dump $R-b 'Use AAC codec for audio'; shot $R-b
  ui dialog $OUT/$R-b-$D.xml 'Use AAC Audio' $f 'Use AAC codec for audio to save bandwidth.' > $OUT/$R-dialog.txt 2>&1
  back
  open 'AAudio output'; dump $R-c 'AAudio output' || { void $R no-aaudio-row; return 1; }
  shot $R-c
  echo "$R-c	$(ui row $OUT/$R-c-$D.xml 'AAudio output' 2>&1)	xp=$(ui text $OUT/$R-c-$D.xml xperiment)" | tee -a $OUT/$R-row.txt
  mark $R-end; guard $R; }
```

R4 PASS needs all of these:

- The `R4-a` line: `summary=Use AAC audio to save bandwidth.` exactly, and `xp=0`.
- `cnt R4 'SettingHelpDialog: help opened for useAacAudio'` = 1, and `R4-dialog.txt` reads
  `title_ok=1 text_ok=1 ok_label=OK fits=1 items=0 xp=0`.
- The `R4-c` line is a row line (not `rows=0`), and `xp=0`. `ui row` matches the name exactly, so a row line proves
  the name is `AAudio output` with nothing after it.
- L-ARM = 0 and L-WAKE = 0.

If the change did nothing, the `R4-a` summary would end `(experimental)` with `xp=1`, and the `R4-c` line would read
`rows=0`, because the name would be `AAudio output (experimental)`. So in this run a `rows=0` on `R4-c` with a dump
that holds `AAudio output` is a FAIL, not a void. Record the name text of every node that holds `AAudio` in that case.

**r6.sh** (`r6 R6`, T2)

```bash
r6() { local R=$1 xy f d; mark $R-start
  open 'Send Media Buttons'; dump $R-a 'Send Media Buttons To Android Auto' || { void $R no-row; return 1; }
  shot $R-a; f=$(ui frame $OUT/$R-a-$D.xml); d=$OUT/$R-a-$D.xml
  echo "$R-a	$(ui row $d 'Send Media Buttons To Android Auto' 2>&1)	hint1=$(ui text $d 'Next, previous and play/pause') hint2=$(ui text $d 'Stops sending them while') hint3=$(ui text $d 'Never sends them')" | tee -a $OUT/$R-row.txt
  ui rows $d > $OUT/$R-rows.txt
  xy=$(ui desc $d 'Help for Send Media Buttons To Android Auto') || { void $R no-target; return 1; }
  tap $R help $xy; waitline $R 'help opened for mediaKeyRouting' 5
  dump $R-b 'Always: '; shot $R-b
  ui dialog $OUT/$R-b-$D.xml 'Send Media Buttons To Android Auto' $f 'Always: ' 'Automatic: ' 'Never: ' > $OUT/$R-dialog.txt 2>&1
  back; dump $R-c 'Send Media Buttons To Android Auto'
  echo "$R-c	$(ui row $OUT/$R-c-$D.xml 'Send Media Buttons To Android Auto' 2>&1)	dlg=$(ui dlg $OUT/$R-c-$D.xml)" | tee -a $OUT/$R-row.txt
  mark $R-end; guard $R; }
```

R6 PASS needs all of these:

- The `R6-a` line: `hint1=0 hint2=0 hint3=0`, `help=1`, and `seg=` names one option (not `NA`).
- `cnt R6 'SettingHelpDialog: help opened for mediaKeyRouting'` = 1, and `cnt R6 'SettingHelpDialog: help opened for '` = 1.
- `R6-dialog.txt` reads `title_ok=1 text_ok=1 ok_label=OK fits=1 items=0`. Record `len=`.
- The `seg=` value is the same on the `R6-a` and `R6-c` lines, and the `R6-c` line has `dlg=0`.
- `R6-rows.txt` ends `fail=0`. C=1 here means the button group sits inside the row card.
- L-ARM = 0 and L-WAKE = 0.

If the change did nothing, one hint row would show under the setting, so one of `hint1` to `hint3` would be 1 (which
one depends on the rig's stored option), and the dialog would hold none of `Always: `, `Automatic: `, `Never: `.

**r7.sh** (`r7 R7`, T2)

```bash
r7() { local R=$1 xy f; mark $R-start
  open 'Force Software Decoding'; dump $R-a 'Force Software Decoding' || { void $R no-row; return 1; }
  shot $R-a; f=$(ui frame $OUT/$R-a-$D.xml)
  echo "$R-a	$(ui row $OUT/$R-a-$D.xml 'Force Software Decoding' 2>&1)" | tee -a $OUT/$R-row.txt
  xy=$(ui desc $OUT/$R-a-$D.xml 'Help for Force Software Decoding') || { void $R no-target; return 1; }
  tap $R help $xy; waitline $R 'help opened for forceSoftwareDecoding' 5
  dump $R-b 'Decodes the video on the main processor'; shot $R-b
  ui dialog $OUT/$R-b-$D.xml 'Force Software Decoding' $f 'Decodes the video on the main processor' > $OUT/$R-dialog.txt 2>&1
  back; dump $R-c 'Force Software Decoding'
  echo "$R-c	$(ui row $OUT/$R-c-$D.xml 'Force Software Decoding' 2>&1)	dlg=$(ui dlg $OUT/$R-c-$D.xml)" | tee -a $OUT/$R-row.txt
  mark $R-end; guard $R; }
```

R7 PASS: `cnt R7 'SettingHelpDialog: help opened for forceSoftwareDecoding'` = 1 and the all-ids count = 1;
`R7-dialog.txt` reads `title_ok=1 text_ok=1 ok_label=OK fits=1 items=0`; the `switch=` value is the same on the `R7-a`
and `R7-c` lines and is `true` or `false`; `help=1` on `R7-a`; `dlg=0` on `R7-c`; L-ARM = 0 and L-WAKE = 0.

**The no-Save check** (Part A step 4, before the restore)

**nosave.sh**

```bash
adb -s $HU shell run-as $PKG cat shared_prefs/settings.xml > $OUT/settings-end-$HU.xml
for k in aa-desaturation-level aa-monochrome-enabled media-key-routing force-software-decoding audio-queue-capacity use-aac-audio use-aaudio-output aa-exit-action audio-latency-multiplier; do
  a=$(grep -a -o "name=\"$k\"[^/]*" $OUT/settings-seeded-$HU.xml); b=$(grep -a -o "name=\"$k\"[^/]*" $OUT/settings-end-$HU.xml)
  echo "$k seeded=[$a] end=[$b] same=$([ "$a" = "$b" ] && echo 1 || echo 0)"; done | tee $OUT/nosave.txt
diff <(tr -d '\r' < $OUT/settings-seeded-$HU.xml | grep -v '^$') <(tr -d '\r' < $OUT/settings-end-$HU.xml | grep -v '^$') > $OUT/nosave-diff.txt
```

PASS: every line of `nosave.txt` reads `same=1`. `nosave-diff.txt` is information: list any key that changed. A
changed key from this list means a run stored a setting, which is a FAIL of the run that touched it.

### Part B: D-SAM (`D=$SAM`)

Pre-flight for D-SAM, then `hdp` once with `open -` (record it), then R5.

**r5s.sh** (`r5s R5-A`, then write `screen-orientation=0` with the app stopped and read it back, then `r5s R5-B`)

```bash
r5s() { local R=$1; mark $R-start
  adb -s $D shell dumpsys input | grep -a -m1 SurfaceOrientation | tee $OUT/$R-orient.txt
  open zzqx; dump $R-a; shot $R-a
  adb -s $D shell dumpsys input | grep -a -m1 SurfaceOrientation | tee -a $OUT/$R-orient.txt
  mark $R-end; guard $R
  echo "$R box=[$(ui box $OUT/$R-a-$D.xml)] none=$(ui text $OUT/$R-a-$D.xml 'No settings match') displayed=$(win $R | grep -a 'Displayed ' | grep -ac SettingsActivity) orient=$(tr '\n' ' ' < $OUT/$R-orient.txt)" | tee -a $OUT/marks.txt; }
```

R5 has no PASS or FAIL. Report per arm: the `screen-orientation` value in force, `box=`, `none=`, `displayed=` and
both `SurfaceOrientation` reads. `box=[zzqx]` with `none=1` means the seed reached the field. `displayed=2` or more
means Android showed the activity twice, which supports the plan's relaunch theory. The `zzqx` query lists no row, so
the result does not depend on how much of the list fits the screen.

Then `kill $(cat $OUT/cap-$SAM.pid)`, restore the backup, and diff it against the backup with CRs and blank lines
stripped.

### Stop rule

- Retry a void run once. A run void twice is UNTESTABLE; say why.
- If 2 `open` calls in a row on one unit give `NO_DUMP`, stop that unit's part and mark its remaining runs
  UNTESTABLE.
- If `tier TALL` prints `UNTESTABLE`, run R1 at the found density (`wm density reset`, or the found override) as
  `R1-N` and grade it as R1-TALL. If R1-N prints `BELOW_FOLD`, R1 is UNTESTABLE (density).
- If `tier T2` or `tier T1` prints `UNTESTABLE`, skip that tier's runs and mark them UNTESTABLE (density).
- A host thermal stop follows `rig-quirks/topics/tooling.md`.

## 9. Do not re-run

Settled in round 1 at `ba1a5c6d` on D-HU, and the holders, the dialog and the toolbar code did not change since:

- Audio Sink (?) opens its help and leaves the switch alone (round 1 R1).
- A tap on the row body still toggles the switch (R2).
- (?) geometry on toggle rows at T2 and T1 (R3).
- The long Wireless Mode help fits, also at 150% scale (R4, R4b).
- The help text is searchable (R5 on D-HU).
- The toolbar (?) on five sub-screens and none on About and Permissions (R6).
- The filled and outline glyphs per theme (R8).

The JVM suite covers the label in every locale, the joined help per locale, the 500-character limit and the main and
quick screen pairs (3550 tests at `e3b2597c`). Do not try to prove those on the rig, for example by changing the app
language.

## 10. Report back

The numbers that decide shipping:

1. R1-TALL: the L-HELP count, the dialog line, the `Desaturation` rows line (A to D), and `before` against `after`.
2. R4: the `xp=` counts on `R4-a`, `R4-c` and the dialog, and whether `R4-c` is a row line.
3. R2 and R6: the `summary=` and `help=` fields of R2 at both tiers, and R6's `hint1` to `hint3` with its dialog line.

Also give the D-HU density found and restored, the `h` read at each tier, `rowH` of the Desaturation row at every tier
where it showed, the `nosave.txt` lines, R5's two arms, and every injected tap from `taps.log` (run, target,
coordinates).

The five setting ids that the runs append to the L-HELP prefix. Each one is a verbatim `grep -F` match in `app/src`
at `e3b2597c` (3, 9, 9, 4 and 8 files).

```setting-ids
aaDesaturation
audioQueueCapacity
useAacAudio
mediaKeyRouting
forceSoftwareDecoding
```

The block below lists every app log string the runs grep. L-DISP (`Displayed ` with `SettingsActivity`, R5 only) is a
system `ActivityManager` line, not an app string, so it is not in the block. `SettingsActivity` there is only a
class-name filter. The block holds the L-HELP prefix only, because the app adds the setting id at run time (section 6).
Each line of the block is a verbatim `grep -F` match in `app/src` at `e3b2597c`. Note the trailing space on four of them.
The block is the last thing in this file.
