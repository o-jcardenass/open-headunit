# pr-1132-exit-dialog, round 1 brief

Published name: `pr-1132-exit-dialog-round1-brief.md`. Results go in `pr-1132-exit-dialog-round1-results.md`.
Evidence: create the release `rig-evidence-pr-1132-exit-dialog` on this first round and upload `pr-1132-exit-dialog-round1-captures.zip` to it.

**This round is short on purpose.** It checks one dialog on three units. It needs no phone, no session, no Bluetooth and no WiFi.

Units: D-HU (Android 14), D-SAM (API 19), D-HP (API 17). D-POCO and D-MOTO are not used.

Queue line: `pr-1132-exit-dialog-round1-brief.md` (C `f17c14ef` on `fork/fix/pr-1132-exit-dialog`, B `443faca8` = the contributor's PR head, both in one worktree; units D-HU, D-SAM, D-HP, no phone; SAM-4 and HP-4 are the point).

Pairing: the SAM and HP stages use no unit that another queued round needs, except `aap-protocol-revision-round1` (D-SAM). The HU stage takes D-HU for about 8 minutes.

Cabling: D-HU, D-SAM and D-HP on adb, one port each.

**Hand steps.** None.

**Injected gestures and keys.** Each A run (HU-1, SAM-1, HP-1) taps once: the first option row of the exit dialog, found in a fresh `uiautomator` dump, to choose Stop. Every other run taps nothing. Every run sends at most 3 BACK keys (`input keyevent 4`). BACK is the app's own way to open the exit dialog, so it is the instrument and not a work-around.

**Time budget.** Build 2 APKs 8 min, pre-flight beside it. Installs 5 min. HU 8 min, SAM 12 min, HP 10 min, plus up to 4 min for each tablet reboot. Restore 5 min. **About 55 min in all, 30 min of it runs.**

## 1. Build and baseline

| Arm | Ref | Commit | Tree |
|---|---|---|---|
| C (candidate) | `fork/fix/pr-1132-exit-dialog` | `f17c14ef6e71e38027fcc332bb3e4719275c7fa8` | `680879d5628d3f9f39bfeddb47a03ec2510a8168` |
| B (control) | the same branch, `C^1^1` | `443faca81bfa26293f7dcf96fcdae6dfa8524218` | `7e73f9893b120fc375569d5c86aa00bffd86490a` |

B is the contributor's PR as submitted. C is B merged with `main` `199657e3`, plus one commit (`f17c14ef`) that keeps the system font scale in the scaled dialog and saves the scale only when it changes. B is an ancestor of C, so one fetch of the fork branch brings both. History was not rewritten.

B is used only on D-SAM, as the positive control for SAM-4. The dev host ran the JVM suite at C: 3686 tests, 0 failures. Do not run the unit tests on the rig. No other queued round shares this base.

Work from `~/Coding/StudioProjects`. Save every script block under `$OUT/scripts/` with `python3 -I $RIG_TOOLKIT/extract_brief_blocks.py <this brief> $OUT/scripts`. Each block name is bold and in backticks, on the line right above its fence. Run every script with `bash $OUT/scripts/<name>.sh`. Each script sets `H` **after** it sources `lib.sh`.

**`build.sh`**
```bash
. $(dirname $0)/lib.sh
C=f17c14ef6e71e38027fcc332bb3e4719275c7fa8; CT=680879d5628d3f9f39bfeddb47a03ec2510a8168
B=443faca81bfa26293f7dcf96fcdae6dfa8524218; BT=7e73f9893b120fc375569d5c86aa00bffd86490a
# reuse this session's worktree if WT is set; else make one (never pick an arbitrary folder under worktrees/)
WT=${WT:-$($RIG_TOOLKIT/wt-new.sh pr-1132-exit-dialog candidate main | tail -1)}
git -C $WT fetch https://github.com/o-jcardenass/open-headunit.git fix/pr-1132-exit-dialog
for arm in C B; do
  eval sha=\$$arm; eval tree=\$${arm}T
  git -C $WT checkout --detach $sha
  [ "$(git -C $WT rev-parse 'HEAD^{tree}')" = $tree ] || { echo "$arm TREE_MISMATCH"; exit 1; }
  $RIG_TOOLKIT/rig_thermal.sh wait 70
  HUR_DIR=$WT MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh || { echo "$arm BUILD_FAILED"; exit 1; }
  cp $(ls -t $RIG_DATA/apks/com.andrerinas.headunitrevived_*.apk | head -1) $OUT/$arm.apk
done
{ echo "WT=$WT"
  for arm in C B; do echo "$arm md5 $(md5sum < $OUT/$arm.apk | cut -c1-32) applySystemBars=$(unzip -p $OUT/$arm.apk 'classes*.dex' | strings | grep -c applySystemBars)"; done
} | tee $OUT/build.txt
```

Build gate: the two md5s MUST differ, and `applySystemBars` MUST be 1 or more in both. The PR adds that method, so the installed `main` build has 0. A failed gate stops the round.

Identity after each install (`inst` in `lib.sh` does both):

- `$RIG_TOOLKIT/apk_identity.sh $OUT/<arm>.apk <serial>` MUST exit 0.
- The `ACTION_QUERY_STATE` reply MUST carry commit `f17c14ef6e71` for C and `443faca81bfa` for B. A `-dirty` suffix is fine; say so.
- If `adb install -r -d` answers `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, stop and ask. Never uninstall.

## 2. What this is and why

The contributor's PR changes the exit dialog that BACK opens over the projection, in two ways:

1. **A scale setting for the dialog**, `ui-scale-exit-dialog-percent` (100 to 150 in steps of 10), like the home and settings scales. It wraps the dialog's context in a configuration with a higher `densityDpi`.
2. **The system bars stay hidden while the dialog is open.** The dialog window gets the projection's own bar flags. It is created with `FLAG_NOT_FOCUSABLE`, shown, and the flag is then cleared, so the window takes the immersive flags before it takes focus.

Our review found one defect in part 1. The PR built the override from a blank `Configuration()`. On API 17 to 23 a blank `Configuration` has `fontScale = 1`, and the override then replaces the user's system font scale. So on an old unit with large system text, the exit dialog lost it, **at every scale including the default 100%**, because the PR wraps the context whatever the setting. C copies the live configuration, as `MainActivity` does, and skips the wrapper at 100%. API 24 and later build a blank `Configuration` with every field unset, so D-HU cannot show the defect.

| Id | Question | Code | Runs | Kind |
|---|---|---|---|---|
| FOCUS | BACK closes the dialog, so the window took focus after the flag was cleared | `showExitDialog` in `AapProjectionActivity` | HU-1, SAM-1, HP-1 | GATE |
| STOP | The first row still chooses Stop through the scaled inflater | the adapter's `dialogInflater` | HU-1, SAM-1, HP-1 | GATE |
| SCALE | 130% makes the dialog about 1.3 times larger | the `densityDpi` override | HU-2, SAM-2, HP-2 | GATE |
| FONT | At 130% the dialog keeps the system font scale on API 17 to 23 | `f17c14ef` | SAM-4, HP-4 | GATE |
| CTRL | The PR as submitted loses the font scale, so the FONT measurement can see the defect | B | SAM-C | CONTROL |
| FIT | On Android 14 the edge-to-edge dialog stays inside the screen and centred | `SystemUI.applySystemBars` | HU-2 | GATE |
| BARS | The bars stay hidden while the dialog is open | `SystemUI.applySystemBars` | HU-1, HU-3, SAM-1, HP-1 | RECORD |

A RECORD run reports what it saw. It does not pass or fail the PR.

## 3. What is different about this round

- **No session.** `AapProjectionActivity` is exported, and BACK opens the exit dialog whenever no session has started its transport (`ProjectionKeyPolicy.shouldRouteBackKeyToProjection` only sends BACK to the phone once `TransportStarted`). So each run launches the projection screen with `am start -n`, with `wifi-connection-mode=0` (manual: no discovery and no group). If the screen does not stay resumed for 3 s after its `onResume`, that unit's runs are UNTESTABLE (no-session launch), and the round goes on with the other units.
- **The measurements come from `uiautomator` dumps, not from the log.** `exitdlg.py` finds the option rows by shape (a parent with one `ImageView` and one `TextView`), because API 17 dumps carry no resource ids. The icon is 24 dp and does not follow the font scale. The text is 16 sp and follows both the scale and the font scale. So `icon_w` measures the dialog scale, and `text_h / icon_w` measures the font scale alone.
- **What a PASS would look like if the change did nothing.** Without part 1, `icon_w` at 130% equals `icon_w` at 100%, and SCALE fails. Without `f17c14ef`, `text_h / icon_w` at 130% with system font 1.3 drops to about 0.77 of its value at 100%, and FONT fails. SAM-C runs B to prove that drop is visible on this unit. FOCUS and STOP also pass on `main`; they are a regression guard for the new focus handling.
- **`uiautomator` dumps the focused window.** If the dialog never takes focus, the dump shows the projection screen and `rows=0` while the dialog is on screen. That is still a FOCUS FAIL, and the screenshot shows which.
- **The system font scale on Android 4.x may need a reboot.** SAM-3 and HP-3 check that `settings put system font_scale 1.3` reached the app. If it did not, the script reboots that tablet once and repeats the run. If it still did not, SAM-3, SAM-4, SAM-C, HP-3 and HP-4 are INCONCLUSIVE (font scale not applied). After the reboot `reboot_wait` sends MENU to clear a swipe keyguard and prints the focus. A run that then reads `NO_RESUME` is INCONCLUSIVE (keyguard), not FAIL.
- **D-SAM has no `sed`.** `set_prefs_runas_host.py` writes its settings unchanged (`rig-quirks/units/D-SAM.md`). `am` rejects `-p` there; every command here uses `-n`.
- **D-HP's capture can die silently.** `capwatch` restarts it (`rig-quirks/units/D-HP.md`).
- **D-SAM is mounted portrait with `screen-orientation=2`.** The seeds leave `screen-orientation` alone.
- **On either tablet, tap the dump's bounds**, not a screenshot pixel (`rig-quirks/units/D-SAM-and-D-HP.md`). `gestxy` does.
- **Animations are off on all three units** for the round, so a dump does not wait on a spinner. Pre-flight saves each value.
- **The audio settings are a deliberate worst case.** The seeds do not touch `use-aac-audio`, `audio-latency-multiplier` or `audio-queue-capacity`.

## 4. Settings keys

`seed` in `lib.sh` writes these with the app stopped: `set_hu_settings_host.py` on D-HU (rooted), `set_prefs_runas_host.py` on D-SAM and D-HP. It reads the keys back with `showkeys.py`. `restore.sh` puts every backup back at the end.

| Key | Type | Every seed | `K100` | `K130` | `KBARS` (HU-3) | Why |
|---|---|---|---|---|---|---|
| `log-level` | int | 2 | | | | INFO; every graded line is INFO or WARN |
| `wifi-connection-mode` | int | 0 | | | | manual: nothing arms |
| `auto-connect-last-session` | boolean | false | | | | |
| `auto-connect-single-usb` | boolean | false | | | | |
| `auto-start-self-mode` | boolean | false | | | | |
| `hud_mirroring` | boolean | false | | | | a mirrored dialog would invert the tap |
| `fullscreen-mode` | int | | 1 | 1 | 0 | 1 immersive; 0 bars shown |
| `ui-scale-exit-dialog-percent` | int | | 100 | 130 | 100 | the setting under test |

System font scale, set with `fs` in `lib.sh` (`settings put system font_scale`): 1.0 for HU-*, SAM-1, SAM-2, HP-1 and HP-2; 1.3 for SAM-3, SAM-4, SAM-C, HP-3 and HP-4.

## 5. Helpers

Run every adb call in sequence, never two at once against one unit. The `logcat` captures are the only standing streams. Write the results file after every run.

**`lib.sh`**
```bash
source ~/Coding/StudioProjects/rig-toolkit/rig_devices.sh
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
PROJ=$PKG/com.andrerinas.openheadunit.aap.AapProjectionActivity
D_HU=${D_HU:-27870808938846}; D_SAM=${D_SAM:-30041c35642d2200}; D_HP=${D_HP:-CNU350BGBJ}
OUT=$RIG_DATA/rounds/pr-1132-exit-dialog-round1; S=$OUT/scripts; mkdir -p $S $OUT/ui
export LC_ALL=C
[ -f $OUT/env.sh ] && . $OUT/env.sh       # HU_OWNER: preflight.sh writes it
[ -f $OUT/orig.sh ] && . $OUT/orig.sh     # opening font and animation scales: the first preflight.sh only
BASE="scalar:int:log-level:2 scalar:int:wifi-connection-mode:0 scalar:boolean:auto-connect-last-session:false
  scalar:boolean:auto-connect-single-usb:false scalar:boolean:auto-start-self-mode:false scalar:boolean:hud_mirroring:false"
K100="$BASE scalar:int:fullscreen-mode:1 scalar:int:ui-scale-exit-dialog-percent:100"
K130="$BASE scalar:int:fullscreen-mode:1 scalar:int:ui-scale-exit-dialog-percent:130"
KBARS="$BASE scalar:int:fullscreen-mode:0 scalar:int:ui-scale-exit-dialog-percent:100"

send() { local a=$1; shift; adb -s $H shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; sleep 0.3; }
mark() { send ACTION_LOG_MARKER --es text "$1" >/dev/null; echo "$1 $(date +%T)" >> $OUT/marks.txt; }
running() { { adb -s $1 shell ps; adb -s $1 shell ps -A; } 2>/dev/null | grep -ac "$PKG"; }
# headunit://exit only to a running app; sent to a stopped one it starts and arms it (rig-quirks/topics/lifecycle.md)
stopapp() { if [ "$(running $1)" -ge 1 ]; then adb -s $1 shell am start -a android.intent.action.VIEW -d headunit://exit >/dev/null; sleep 5; fi
  adb -s $1 shell am force-stop $PKG; sleep 1; }
resumed() { adb -s $H shell dumpsys activity activities | grep -a -m1 -E 'mResumedActivity|ResumedActivity:' | tr -d '\r'; }
# focus: the window with input focus, retried while it reads null
focus() { local f i; for i in 1 2 3; do f=$(adb -s $H shell dumpsys window | grep -a -m1 mCurrentFocus | tr -d '\r')
  echo "$f" | grep -q 'mCurrentFocus=null' || break; sleep 1; done; echo "$f"; }
# who <focus line>: DIALOG (focus on a window of ours that is not the projection), PROJ, or OTHER
who() { case "$1" in *AapProjectionActivity*) echo PROJ;; *StatusBar*|*NavigationBar*|*systemui*|*Keyguard*|*=null*) echo OTHER;;
  *) echo DIALOG;; esac; }

# captures: started before the launch, always with stdbuf -oL
cap() { adb -s $2 logcat -G 16M >/dev/null 2>&1; adb -s $2 logcat -c; stdbuf -oL adb -s $2 logcat -v time > $1 & echo $! > $1.pid; sleep 1; }
# D-HP's capture can die silently: check every 60 s, restart with >> (rig-quirks/units/D-HP.md)
capwatch() { ( while :; do sleep 60; kill -0 $(cat $1.pid) 2>/dev/null || { stdbuf -oL adb -s $2 logcat -v time >> $1 & echo $! > $1.pid; echo "capture restarted $1 $(date +%T)" >> $OUT/marks.txt; }; done ) & echo $! > $1.watch; }
uncap() { [ -f $1.watch ] && kill $(cat $1.watch) 2>/dev/null; kill $(cat $1.pid) 2>/dev/null; }

# seg <file> <from> [<to>]: lines after the last <from> marker up to the next <to> marker (or the end).
# A label matches only at the end of a line, so SAM-3_x never matches SAM-3r_x.
seg() { awk -v a=": $2" -v b=": ${3:-@@none@@}" '
  function ends(s,t){ return length(s)>=length(t) && substr(s,length(s)-length(t)+1)==t }
  { l=$0; sub(/\r+$/,"",l) }
  ends(l,a){ buf=""; on=1; next }
  on && ends(l,b){ on=0 }
  on{ buf=buf $0 "\n" }
  END{ printf "%s", buf }' "$1"; }
cnt() { seg "$1" "$2" "$3" | grep -acF -- "$4"; }
fatal() { seg "$1" "$2" "$3" | grep -aF -A3 'FATAL EXCEPTION' | grep -acF "Process: $PKG"; }
# waitfor <file> <from> <cap-s> <string>: 0 as soon as the string appears after <from>
waitfor() { local f=$1 m=$2 cap=$3 s=$4 t=0
  while [ $t -lt $cap ]; do [ "$(cnt $f $m '' "$s")" -ge 1 ] && return 0; sleep 1; t=$((t+1)); done; return 1; }

# host thermal (rig-quirks/topics/tooling.md)
th_gate() { $RIG_TOOLKIT/rig_thermal.sh wait 75 || $RIG_TOOLKIT/rig_thermal.sh wait 75 || { echo HOST_HOT; return 1; }
  $RIG_TOOLKIT/rig_thermal.sh watch $OUT/thermal-$1.log & THPID=$!; }
th_end() { kill $THPID 2>/dev/null; wait $THPID 2>/dev/null; }

# settings
pull_settings() { if [ $1 = $D_HU ]; then adb -s $1 shell cat /data/data/$PKG/shared_prefs/settings.xml
  else adb -s $1 shell run-as $PKG cat shared_prefs/settings.xml; fi | tr -d '\r'; }
push_settings() { local s=$1 f=$2; adb -s $s shell am force-stop $PKG; sleep 1
  adb -s $s push $f /data/local/tmp/ohu-r1.xml >/dev/null; adb -s $s shell chmod 644 /data/local/tmp/ohu-r1.xml
  if [ $s = $D_HU ]; then adb -s $s shell cp /data/local/tmp/ohu-r1.xml /data/data/$PKG/shared_prefs/settings.xml
    adb -s $s shell chown $HU_OWNER /data/data/$PKG/shared_prefs/settings.xml; adb -s $s shell chmod 660 /data/data/$PKG/shared_prefs/settings.xml
  else adb -s $s shell run-as $PKG cp /data/local/tmp/ohu-r1.xml shared_prefs/settings.xml; fi
  pull_settings $s | diff -B - $f >/dev/null && echo SETTINGS_BACK || echo SETTINGS_DIFFER; }
# seed <serial> <key-spec>...: app stopped, write, read back
seed() { local s=$1; shift; adb -s $s shell am force-stop $PKG; sleep 1
  if [ $s = $D_HU ]; then python3 -I $RIG_TOOLKIT/set_hu_settings_host.py $s "$@"
  else python3 -I $RIG_TOOLKIT/set_prefs_runas_host.py $s "$@"; fi >> $OUT/seed.log 2>&1
  pull_settings $s > $OUT/seeded-$s.xml
  python3 -I $S/showkeys.py $OUT/seeded-$s.xml log-level wifi-connection-mode fullscreen-mode ui-scale-exit-dialog-percent \
    hud_mirroring auto-connect-last-session auto-connect-single-usb auto-start-self-mode screen-orientation | tr '\n' ' '; echo; }
# fs <serial> <scale>: the system font scale, read back
fs() { adb -s $1 shell am force-stop $PKG; adb -s $1 shell settings put system font_scale $2; sleep 0.3
  echo "font_scale $(adb -s $1 shell settings get system font_scale | tr -d '\r')"; }
# reboot_wait <serial>: reboot and wait for boot_completed, 240 s at most
reboot_wait() { local t=0; adb -s $1 reboot; sleep 5; adb -s $1 wait-for-device
  while [ "$(adb -s $1 shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" != 1 ] && [ $t -lt 240 ]; do sleep 3; t=$((t+3)); done
  sleep 10; adb -s $1 shell input keyevent 82; sleep 2      # MENU dismisses a swipe keyguard on Android 4.x
  echo "rebooted $1 after ${t}s focus=[$(adb -s $1 shell dumpsys window | grep -a -m1 mCurrentFocus | tr -d '\r')]"; }
# inst <serial> <arm>: install the arm and prove it (section 1)
inst() { local s=$1 H=$1; adb -s $s install -r -d $OUT/$2.apk | tail -1
  $RIG_TOOLKIT/apk_identity.sh $OUT/$2.apk $s; echo "apk_identity $s $2 exit $?"
  send ACTION_QUERY_STATE | grep -ao 'commit[^,}]*'; adb -s $s shell am force-stop $PKG; }

# screen
uidump() { adb -s $H shell rm -f /sdcard/ohu-ui.xml; sleep 0.3
  adb -s $H shell uiautomator dump /sdcard/ohu-ui.xml >/dev/null 2>&1; sleep 0.3
  adb -s $H pull /sdcard/ohu-ui.xml $OUT/ui/$1.xml >/dev/null 2>&1; sleep 0.3
  adb -s $H shell screencap -p /sdcard/ohu-ui.png; sleep 0.3; adb -s $H pull /sdcard/ohu-ui.png $OUT/ui/$1.png >/dev/null 2>&1
  [ -s $OUT/ui/$1.xml ] || { adb -s $H shell uiautomator dump /sdcard/ohu-ui.xml >/dev/null 2>&1; sleep 0.3
    adb -s $H pull /sdcard/ohu-ui.xml $OUT/ui/$1.xml >/dev/null 2>&1; }
  [ -s $OUT/ui/$1.xml ] || echo "$1 DUMP_FAILED" | tee -a $OUT/marks.txt >&2; }
dlg() { python3 -I $S/exitdlg.py "$@"; }
# key <run> <keyevent>: one key event; 3 per run
key() { local R=$1 n; n=$(cat $OUT/keys-$R 2>/dev/null || echo 0)
  [ "$n" -ge 3 ] && { echo "$R KEY_BUDGET_SPENT" | tee -a $OUT/marks.txt >&2; return 3; }
  adb -s $H shell input keyevent $2 >/dev/null 2>&1
  echo $((n+1)) > $OUT/keys-$R; echo "$R key $2 $(date +%T)" | tee -a $OUT/keys.log >&2; sleep 2; }
# gestxy <run> <x> <y> <why>: one tap on a point exitdlg.py computed from a dump; 1 per run
gestxy() { local R=$1 n; n=$(cat $OUT/taps-$R 2>/dev/null || echo 0)
  [ "$n" -ge 1 ] && { echo "$R BUDGET_SPENT" | tee -a $OUT/marks.txt >&2; return 3; }
  adb -s $H shell input tap $2 $3; echo $((n+1)) > $OUT/taps-$R; echo "$R tap xy:$4 $2 $3 $(date +%T)" | tee -a $OUT/gestures.log >&2; sleep 3; }

# open <run> <capture>: launch the projection screen with no session, open the exit dialog with BACK, dump it.
# Returns 1 when the screen did not resume. Writes $OUT/<run>.txt.
open() { local R=$1 C=$2; : > $OUT/$R.txt
  mark ${R}_launch; adb -s $H shell am start -n $PROJ >/dev/null; sleep 0.3
  waitfor $C ${R}_launch 20 'AapProjectionActivity: onResume' || { echo "$R NO_RESUME" | tee -a $OUT/marks.txt $OUT/$R.txt; return 1; }
  sleep 3; echo "resumed=[$(resumed)]" >> $OUT/$R.txt; uidump $R-act
  echo "act $(dlg $OUT/ui/$R-act.xml)" >> $OUT/$R.txt
  mark ${R}_open; key $R 4; uidump $R-open; f=$(focus)
  echo "open $(dlg $OUT/ui/$R-open.xml) focus=$(who "$f") [$f]" >> $OUT/$R.txt; }
# close <run>: BACK again; the dialog must be gone and the projection focused
close() { local R=$1; mark ${R}_back; key $R 4; uidump $R-closed; f=$(focus)
  echo "closed $(dlg $OUT/ui/$R-closed.xml) focus=$(who "$f") [$f]" >> $OUT/$R.txt; }
# ratio <run-a> <run-b> <field>: field_a / field_b from the "open" lines, 3 decimals; NA when either is missing
val() { grep -a '^open ' $OUT/$1.txt 2>/dev/null | grep -aoE "$2=[0-9.]+" | head -1 | cut -d= -f2; }
ratio() { local a b; a=$(val $1 $3); b=$(val $2 $3)
  [ -n "$a" ] && [ -n "$b" ] && awk -v a=$a -v b=$b 'BEGIN{ if (b>0) printf "%.3f", a/b; else print "NA" }' || echo NA; }
```

**`exitdlg.py`**
```python
# exitdlg.py <dump.xml>: one line describing the exit dialog in a uiautomator dump.
#   rows=N icon_w=W icon_h=H text_h=T fr=T/W tap=X,Y win=L,T,R,B inwin=0|1 text=<first row's label>
# A row is a node whose children are exactly one ImageView and one TextView with text; API 17 dumps carry no ids.
# win is the dump's root node, which is the dialog window when the dialog is focused, else the whole screen.
import re, sys, xml.etree.ElementTree as ET
def box(n):
    m = re.findall(r'\d+', n.get('bounds', ''))
    return tuple(map(int, m)) if len(m) == 4 else None
try:
    root = ET.parse(sys.argv[1]).getroot()
except Exception:
    print('rows=0 PARSE_FAILED'); sys.exit(2)
top = root.find('node')
win = box(top) if top is not None else None
rows = []
for p in root.iter('node'):
    kids = list(p.findall('node'))
    imgs = [k for k in kids if k.get('class') == 'android.widget.ImageView']
    txts = [k for k in kids if k.get('class') == 'android.widget.TextView' and k.get('text')]
    if len(kids) == 2 and len(imgs) == 1 and len(txts) == 1 and box(imgs[0]) and box(txts[0]):
        rows.append((box(imgs[0]), box(txts[0]), txts[0].get('text')))
rows.sort(key=lambda r: r[0][1])
w = ','.join(map(str, win)) if win else 'NA'
if not rows:
    print('rows=0 win=%s' % w); sys.exit(1)
(il, it, ir, ib), (tl, tt, tr, tb), label = rows[0]
inwin = int(bool(win) and all(r[1][0] >= win[0] and r[1][2] <= win[2] and r[1][3] <= win[3] for r in rows))
print('rows=%d icon_w=%d icon_h=%d text_h=%d fr=%.3f tap=%d,%d win=%s inwin=%d text=%s' % (
    len(rows), ir - il, ib - it, tb - tt, (tb - tt) / float(max(ir - il, 1)), (tl + tr) // 2, (tt + tb) // 2, w, inwin,
    label.replace(' ', '_')))
```

**`showkeys.py`**
```python
# showkeys.py <settings.xml> <key>...: key=value per key; a set prints its items joined by ","; absent prints key=ABSENT
import sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
by = {e.get('name'): e for e in root}
for k in sys.argv[2:]:
    e = by.get(k)
    if e is None: print(k + '=ABSENT')
    elif e.tag == 'set': print(k + '=' + ','.join(sorted((s.text or '') for s in e)))
    elif e.tag == 'string': print(k + '=' + (e.text or ''))
    else: print(k + '=' + str(e.get('value')))
```

## 6. Pre-flight and install

Run `build.sh` in the background and `preflight.sh` in the foreground beside it: pre-flight is adb only. Run `install.sh` once the build gate passes.

**`preflight.sh`**
```bash
. $(dirname $0)/lib.sh
exec > >(tee -a $OUT/preflight.txt) 2>&1
echo "==== preflight $(date)"
$RIG_TOOLKIT/rig_preflight.sh D_HU:wifi; $RIG_TOOLKIT/rig_cleanup.sh list
adb devices                                                       # MUST list D-HU, D-SAM and D-HP
FIRST=no; [ -f $OUT/orig.sh ] || FIRST=yes
for s in $D_HU $D_SAM $D_HP; do
  adb devices | grep -q "^$s" || { echo "== $s not on adb"; continue; }
  echo "== $s sdk $(adb -s $s shell getprop ro.build.version.sdk | tr -d '\r') locale $(adb -s $s shell getprop persist.sys.locale | tr -d '\r')$(adb -s $s shell getprop persist.sys.language | tr -d '\r')"
  echo "wm $(adb -s $s shell wm size | tr -d '\r' | tr '\n' ' ') $(adb -s $s shell wm density | tr -d '\r' | tr '\n' ' ')"
  echo "app running $(running $s)"; stopapp $s
  if [ $FIRST = yes ]; then pull_settings $s > $OUT/backup-$s.xml
    adb -s $s pull $(adb -s $s shell pm path $PKG | cut -d: -f2 | tr -d '\r') $OUT/before-$s.apk >/dev/null; fi
  wc -c < $OUT/backup-$s.xml                                        # MUST be over 0
  python3 -I $S/showkeys.py $OUT/backup-$s.xml wifi-connection-mode fullscreen-mode ui-scale-exit-dialog-percent hud_mirroring \
    screen-orientation use-aac-audio audio-latency-multiplier audio-queue-capacity | tr '\n' ' '; echo
done
HU_OWNER=$(adb -s $D_HU shell ls -ld /data/data/$PKG/shared_prefs | awk '{print $3":"$4}' | tr -d '\r'); echo "HU_OWNER=$HU_OWNER"
adb -s $D_HU shell id                                               # MUST show uid=0(root)
if [ $FIRST = yes ]; then
  { for s in HU:$D_HU SAM:$D_SAM HP:$D_HP; do n=${s%%:*}; d=${s#*:}
      echo "${n}_FONT=$(adb -s $d shell settings get system font_scale | tr -d '\r')"
      echo "${n}_ANIM=$(adb -s $d shell settings get global animator_duration_scale | tr -d '\r')"; done; } > $OUT/orig.sh
fi
cat $OUT/orig.sh
for s in $D_HU $D_SAM $D_HP; do adb -s $s shell settings put global animator_duration_scale 0; sleep 0.3; done
echo "HU_OWNER=$HU_OWNER" | tee $OUT/env.sh
```

Pre-flight gates: each line marked MUST. Put the whole `preflight.txt` in Setup notes, with each unit's locale, `wm size` and `wm density`. A `font_scale` of `null` means 1.0.

**`install.sh`**
```bash
. $(dirname $0)/lib.sh
exec > >(tee $OUT/install.txt) 2>&1
for s in $D_HU $D_SAM $D_HP; do echo "== $s"; inst $s C; done
```

Every unit MUST show `apk_identity ... exit 0` and commit `f17c14ef6e71`. **After every install, `seed` reads the keys back before the next launch** (`rig-quirks/topics/tooling.md`: an install can wipe `settings.xml`).

## 7. The deciding lines

Every app string below was checked with `grep -F` in `app/src/main` at both `f17c14ef` and `443faca8`. Grep every capture with `-a`.

Head unit captures (`$OUT/<run>-hu.txt`):

| Key | String | Meaning |
|---|---|---|
| L-RX | `AutomationReceiver: ` | a verb landed; a missing line voids the step |
| L-MARK | `AutomationMarker: ` | a marker landed |
| L-RES | `AapProjectionActivity: onResume` | the projection screen is up |
| L-PAUSE | `AapProjectionActivity: onPause` | it left the front; a dialog does not cause this |
| L-STOPPED | `AapProjectionActivity: onStop` | it is no longer visible |
| L-FATAL | `FATAL EXCEPTION` with `Process: com.andrerinas.headunitrevived` within 3 lines | a crash |

Dump lines (`$OUT/<run>.txt`, written by `open`, `close` and the runs):

| Line | Fields graded |
|---|---|
| `resumed=[...]` | MUST contain `AapProjectionActivity` (the reach gate) |
| `open rows=...` | `rows`, `icon_w`, `fr`, `win`, `inwin`, `focus` |
| `closed ...` | `rows` and `focus` |
| `reopen ...` | `rows` and `focus` |
| `after_stop resumed=[...]` | MUST NOT contain `AapProjectionActivity` |

`focus` is `who`'s answer from `mCurrentFocus`: `DIALOG`, `PROJ` or `OTHER`.

## 8. Runs

Each unit's block is one script. Every run has its own capture, its markers and its `$OUT/<run>.txt`. The script prints each run's counts to `$OUT/<run>-counts.txt`.

### HU: D-HU, Android 14 (HU-1, HU-2, HU-3)

**`hu.sh`**
```bash
. $(dirname $0)/lib.sh
H=$D_HU
th_gate HU || exit 1
fs $D_HU 1.0
# HU-1: BACK opens, BACK closes, BACK reopens, the first row chooses Stop
R=HU-1; C=$OUT/$R-hu.txt; stopapp $H; seed $H $K100 | tee $OUT/$R-seed.txt      # MUST show fullscreen-mode=1, ui-scale-exit-dialog-percent=100
cap $C $H
if open $R $C; then
  close $R
  mark ${R}_reopen; key $R 4; uidump $R-reopen; f=$(focus); echo "reopen $(dlg $OUT/ui/$R-reopen.xml) focus=$(who "$f") [$f]" >> $OUT/$R.txt
  xy=$(grep -a '^reopen ' $OUT/$R.txt | grep -aoE 'tap=[0-9]+,[0-9]+' | cut -d= -f2)
  mark ${R}_stop
  if [ -n "$xy" ]; then gestxy $R ${xy%,*} ${xy#*,} first-row; else echo "$R NO_TARGET" | tee -a $OUT/marks.txt; fi
  sleep 2; echo "after_stop resumed=[$(resumed)]" >> $OUT/$R.txt
fi
mark ${R}_end; uncap $C
echo "pause_dialog=$(cnt $C ${R}_open ${R}_stop 'AapProjectionActivity: onPause') pause_stop=$(cnt $C ${R}_stop ${R}_end 'AapProjectionActivity: onPause') stop_stop=$(cnt $C ${R}_stop ${R}_end 'AapProjectionActivity: onStop') fatal=$(fatal $C ${R}_launch ${R}_end)" | tee $OUT/$R-counts.txt
grep -aq '^resumed=.*AapProjectionActivity' $OUT/$R.txt || { echo "HU NO_SESSION_LAUNCH: HU-1, HU-2 and HU-3 are UNTESTABLE"; stopapp $H; th_end; exit 0; }
# HU-2: 130%, measured against HU-1; the dialog inside the screen and centred
R=HU-2; C=$OUT/$R-hu.txt; stopapp $H; seed $H $K130 | tee $OUT/$R-seed.txt      # MUST show ui-scale-exit-dialog-percent=130
cap $C $H; open $R $C && close $R; mark ${R}_end; uncap $C
scr=$(grep -a '^act ' $OUT/$R.txt | grep -aoE 'win=[0-9,]+' | cut -d= -f2); dwin=$(grep -a '^open ' $OUT/$R.txt | grep -aoE 'win=[0-9,]+' | cut -d= -f2)
centre=$(awk -v s="$scr" -v d="$dwin" 'BEGIN{ n=split(s,a,","); m=split(d,b,","); if (n<4||m<4) { print "NA"; exit }
  dx=(b[1]-a[1])-(a[3]-b[3]); dy=(b[2]-a[2])-(a[4]-b[4]); if (dx<0) dx=-dx; if (dy<0) dy=-dy
  inside=(b[1]>=a[1] && b[2]>=a[2] && b[3]<=a[3] && b[4]<=a[4]); smaller=((b[3]-b[1])<(a[3]-a[1]) && (b[4]-b[2])<(a[4]-a[2]))
  printf "dx=%d dy=%d w=%d h=%d inside=%d smaller=%d", dx, dy, a[3]-a[1], a[4]-a[2], inside, smaller }')
echo "S=$(ratio HU-2 HU-1 icon_w) F=$(ratio HU-2 HU-1 fr) screen=$scr dialog=$dwin $centre fatal=$(fatal $C ${R}_launch ${R}_end)" | tee $OUT/$R-counts.txt
# HU-3 (RECORD): bars shown, for the eye to compare with HU-1's screenshot
R=HU-3; C=$OUT/$R-hu.txt; stopapp $H; seed $H $KBARS | tee $OUT/$R-seed.txt     # MUST show fullscreen-mode=0
cap $C $H; open $R $C && close $R; mark ${R}_end; uncap $C
stopapp $H; th_end
```

**HU-1 PASS** when all of these hold:
1. Reach: `resumed` contains `AapProjectionActivity`, and the `act` line reads `rows=0` (no dialog yet).
2. FOCUS: `open` reads `rows` 2 or more and `focus=DIALOG`; `closed` reads `rows=0` and `focus=PROJ`; `reopen` reads the same `rows` as `open` and `focus=DIALOG`.
3. STOP: `pause_dialog` = 0, `pause_stop` 1 or more, `stop_stop` 1 or more, and `after_stop` does not contain `AapProjectionActivity`.
4. `fatal` = 0.

FAIL on any of 2 to 4. `open` with `rows=0` and `focus=PROJ` while `HU-1-open.png` shows the dialog is the focus defect: FAIL, and say so. Reach not met: UNTESTABLE (no-session launch); skip HU-2 and HU-3.

**HU-2 PASS** when `S` is 1.22 to 1.38, `F` is 0.92 to 1.08 (no font change on API 34), `inside=1`, `inwin=1` on the `open` line, `dx` and `dy` are each at most 5% of `w` and `h`, and `fatal=0`. FAIL otherwise. `S` near 1.00 means the scale had no effect. If `smaller=0`, the dump gave the whole screen as the dialog's root, so FIT is INCONCLUSIVE and only `S`, `F` and `inwin` grade.

**HU-3 RECORD.** For the eye only: say whether the status bar and the navigation bar are visible in `HU-3-open.png`, and whether they are absent in `HU-1-open.png` and `HU-2-open.png`.

### SAM: D-SAM, API 19 (SAM-1, SAM-2, SAM-3, SAM-4, SAM-C) **(SAM-4 is the point)**

`old.sh` runs the same four runs on either tablet. `sam_ctrl.sh` runs the control after it, on D-SAM only.

**`old.sh`**
```bash
. $(dirname $0)/lib.sh
H=$1; N=$2                                   # bash old.sh $D_SAM SAM   or   bash old.sh $D_HP HP
th_gate $N || exit 1
run() { local R=$1 K=$2 C=$OUT/$1-hu.txt; stopapp $H; seed $H $K | tee $OUT/$R-seed.txt   # $K unquoted on purpose: one spec per word
  cap $C $H; [ $H = $D_HP ] && capwatch $C $H; open $R $C && close $R; mark ${R}_end; uncap $C; }
# N-1: BACK and Stop at font 1.0, 100%
fs $H 1.0 | tee $OUT/$N-font1.txt
R=$N-1; C=$OUT/$R-hu.txt; stopapp $H; seed $H $K100 | tee $OUT/$R-seed.txt
cap $C $H; [ $H = $D_HP ] && capwatch $C $H
if open $R $C; then
  close $R
  mark ${R}_reopen; key $R 4; uidump $R-reopen; f=$(focus); echo "reopen $(dlg $OUT/ui/$R-reopen.xml) focus=$(who "$f") [$f]" >> $OUT/$R.txt
  xy=$(grep -a '^reopen ' $OUT/$R.txt | grep -aoE 'tap=[0-9]+,[0-9]+' | cut -d= -f2)
  mark ${R}_stop
  if [ -n "$xy" ]; then gestxy $R ${xy%,*} ${xy#*,} first-row; else echo "$R NO_TARGET" | tee -a $OUT/marks.txt; fi
  sleep 2; echo "after_stop resumed=[$(resumed)]" >> $OUT/$R.txt
fi
mark ${R}_end; uncap $C
echo "pause_dialog=$(cnt $C ${R}_open ${R}_stop 'AapProjectionActivity: onPause') pause_stop=$(cnt $C ${R}_stop ${R}_end 'AapProjectionActivity: onPause') stop_stop=$(cnt $C ${R}_stop ${R}_end 'AapProjectionActivity: onStop') fatal=$(fatal $C ${R}_launch ${R}_end)" | tee $OUT/$R-counts.txt
grep -aq '^resumed=.*AapProjectionActivity' $OUT/$R.txt || { echo "$N NO_SESSION_LAUNCH: the unit's runs are UNTESTABLE"; stopapp $H; th_end; exit 0; }
# N-2: 130% at font 1.0
run $N-2 "$K130"; echo "S=$(ratio $N-2 $N-1 icon_w) F=$(ratio $N-2 $N-1 fr)" | tee $OUT/$N-2-counts.txt
# N-3: font 1.3 at 100%; the reach gate for the font scale, with one reboot if it did not apply
fs $H 1.3 | tee $OUT/$N-font13.txt
run $N-3 "$K100"; REACH=$(ratio $N-3 $N-1 fr); echo "reach=$REACH" | tee $OUT/$N-3-counts.txt
if awk -v r=$REACH 'BEGIN{ exit !(r=="NA" || r<1.15) }'; then
  reboot_wait $H | tee -a $OUT/$N-3-counts.txt; fs $H 1.3 >> $OUT/$N-3-counts.txt
  run $N-3r "$K100"; REACH=$(ratio $N-3r $N-1 fr); echo "reach_after_reboot=$REACH" | tee -a $OUT/$N-3-counts.txt
  cp $OUT/$N-3r.txt $OUT/$N-3.txt
fi
echo "FONT_REACHED=$(awk -v r=$REACH 'BEGIN{ print (r!="NA" && r>=1.15) ? "yes" : "no" }')" | tee -a $OUT/$N-3-counts.txt
# N-4: font 1.3 at 130%, the point of the round
run $N-4 "$K130"; echo "S=$(ratio $N-4 $N-3 icon_w) F=$(ratio $N-4 $N-3 fr)" | tee $OUT/$N-4-counts.txt
stopapp $H; th_end
```

**`sam_ctrl.sh`**
```bash
. $(dirname $0)/lib.sh
H=$D_SAM
grep -aq 'FONT_REACHED=yes' $OUT/SAM-3-counts.txt || { echo "SAM-C SKIPPED: the font scale never reached the app"; exit 0; }
th_gate SAM-C || exit 1
inst $D_SAM B | tee $OUT/SAM-C-install.txt                       # MUST show exit 0 and commit 443faca81bfa
fs $H 1.3
for p in 100 130; do R=SAM-C$p; C=$OUT/$R-hu.txt; stopapp $H; eval K=\$K$p; seed $H $K | tee $OUT/$R-seed.txt
  cap $C $H; open $R $C && close $R; mark ${R}_end; uncap $C; done
echo "Fb100=$(ratio SAM-C100 SAM-3 fr) Fb130=$(ratio SAM-C130 SAM-3 fr) Sb=$(ratio SAM-C130 SAM-C100 icon_w)" | tee $OUT/SAM-C-counts.txt
inst $D_SAM C | tee -a $OUT/SAM-C-install.txt                    # C back on the unit
stopapp $H; th_end
```

Run `bash $OUT/scripts/old.sh $D_SAM SAM`, then `bash $OUT/scripts/sam_ctrl.sh`.

**SAM-1 PASS:** the same four conditions as HU-1. **SAM-2 PASS:** `S` 1.22 to 1.38 and `F` 0.92 to 1.08. **SAM-3** is a gate, not a verdict on the PR: `FONT_REACHED=yes` lets SAM-4 and SAM-C count; `no` makes them INCONCLUSIVE (font scale not applied).

**SAM-4 PASS** (the point of the round): `FONT_REACHED=yes`, `S` 1.22 to 1.38, and `F` 0.92 or more. **FAIL:** `F` 0.85 or less, which is the font scale lost at 130%. Between 0.85 and 0.92: INCONCLUSIVE, give the four `open` lines.

**SAM-C CONTROL.** Reached when `Fb100` is 0.85 or less: the PR as submitted drops the system font scale at its default 100%, and this measurement can see it. `Sb` 1.22 to 1.38 shows B's scale itself works. If `Fb100` is above 0.92, the control did not show the defect on this unit: say so, and SAM-4's PASS is then a regression guard only.

### HP: D-HP, API 17 (HP-1, HP-2, HP-3, HP-4)

Run `bash $OUT/scripts/old.sh $D_HP HP`. The conditions are SAM's, with HP in place of SAM. There is no control on D-HP. The bars RECORD for both tablets: say whether `SAM-1-open.png` and `HP-1-open.png` show the status bar or the navigation bar.

## 9. Restore

**`restore.sh`**
```bash
. $(dirname $0)/lib.sh
exec > >(tee $OUT/restore.txt) 2>&1
for s in HU:$D_HU SAM:$D_SAM HP:$D_HP; do n=${s%%:*}; d=${s#*:}
  echo "== $n"; stopapp $d
  adb -s $d install -r -d $OUT/before-$d.apk | tail -1
  $RIG_TOOLKIT/apk_identity.sh $OUT/before-$d.apk $d; echo "apk_identity $d exit $?"
  echo "$n $(push_settings $d $OUT/backup-$d.xml)"
  eval f=\$${n}_FONT; eval a=\$${n}_ANIM
  case $f in ''|null) adb -s $d shell settings delete system font_scale;; *) adb -s $d shell settings put system font_scale $f;; esac
  case $a in ''|null) adb -s $d shell settings delete global animator_duration_scale;; *) adb -s $d shell settings put global animator_duration_scale $a;; esac
  echo "$n font_scale $(adb -s $d shell settings get system font_scale | tr -d '\r') (opening $f) anim $(adb -s $d shell settings get global animator_duration_scale | tr -d '\r') (opening $a)"
done
for n in SAM HP; do grep -aqF 'rebooted' $OUT/$n-3-counts.txt 2>/dev/null && { eval d=\$D_$n; echo "== $n rebooted for the font scale, reboot again"; reboot_wait $d; }; done
$RIG_TOOLKIT/rig_cleanup.sh list
$RIG_TOOLKIT/rig_preflight.sh D_HU:wifi
```

Each unit SHOULD print `apk_identity ... exit 0`, `SETTINGS_BACK`, and its opening font and animation scales. On `SETTINGS_DIFFER`, put the diff in Setup notes and leave the file; do not loop. If a `before-*.apk` refuses to install, leave C installed and say so. A tablet that needed a reboot for the font scale gets a second reboot, so the opening scale applies too.

## 10. Do not re-run

- Nothing. This is the thread's first round. The JVM suite at C (3686 tests) is the coverage for `Settings.uiScaleExitDialogPercent`'s clamp, and the Customization dialog's save path has no instrument here: it is read in the code.

## 11. Report back

One verdict per run in a table at the top of the results file: HU-1, HU-2, SAM-1, SAM-2, SAM-4, HP-1, HP-2, HP-4 (GATE), SAM-3 and HP-3 (`FONT_REACHED`), SAM-C (CONTROL), HU-3 and the tablet bars (RECORD). Then:

1. **The font scale on the old units:** SAM-4's and HP-4's `S` and `F`, SAM-C's `Fb100`, `Fb130` and `Sb`, and whether either tablet needed the reboot.
2. **Focus and Stop:** for HU-1, SAM-1 and HP-1, the `open`, `closed`, `reopen` and `after_stop` lines and the counts line.
3. **Scale and fit:** each `-2` run's `S` and `F`, and HU-2's `dx`, `dy`, `inside` and `inwin`. Then what the eye saw of the bars.

```decisive-strings
AutomationReceiver: 
AutomationMarker: 
AapProjectionActivity: onResume
AapProjectionActivity: onPause
AapProjectionActivity: onStop
FATAL EXCEPTION
```
