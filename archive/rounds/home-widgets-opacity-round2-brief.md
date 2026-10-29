# home-widgets-opacity, round 2 brief

Published name: `home-widgets-opacity-round2-brief.md`. Results go in `home-widgets-opacity-round2-results.md`.
Evidence: upload `home-widgets-opacity-round2-captures.zip` to the existing release `rig-evidence-home-widgets-opacity`.

Units: **D-HU and D-SAM**, one after the other, both idle with no phone. No Android Auto session, no Bluetooth or WiFi change, so no phone capture is needed: no run touches the phone's Android Auto.

Queue line: `home-widgets-opacity-round2-brief.md` (C `84079239` on `fork/feat/home-widgets-and-opacity`, one commit on `main` `947b3ae9`, amended since round 1; one build, settings arms only; units D-HU and D-SAM, idle; about 60 min; H-0, M-0 and S-0 are the point).

Pairing: D-HU and D-SAM, both idle with no radio change. Any round that uses neither unit can run beside this one.

Cabling: D-HU and D-SAM on adb. D-SAM's USB port does not charge it (`rig-quirks/units/D-SAM.md`).

**Hand steps.** None.

**Injected gestures.** On D-HU: one hold on the dumped bounds of `settings_button` (H-0) and one long press on the dumped bounds of `home_widgets_row` (M-0). On D-SAM: one swipe probe on the bare screen edge (S-100). **Injected keys.** One BACK in M-0, and up to eight D-pad keys in S-0.

**Time budget.** Build 20 min, with pre-flight (5 min) beside it. Install 4 min. D-HU runs 5 min, D-SAM runs 12 min. Thermal waits about 3 min. Restore 5 min. **About 60 min in all, 17 min of it runs.**

## 1. Build and baseline

One APK, installed on both units. No baseline build: each arm's control is another arm or the same run before the hold (section 2). **History was rewritten**: round 1's `2d41ab4e` was amended into `84079239`, still one commit on `main` `947b3ae9`.

| Arm | Ref | Commit | Tree |
|---|---|---|---|
| C (candidate) | `fork/feat/home-widgets-and-opacity` | `8407923931729edd64601c70e1012adaf37d340a` | `43fc958577bacfd500ae7627919109f05886cdf5` |

The dev host ran the JVM suite at `84079239`: 4250 tests, 0 failures. Do not run the unit tests on the rig.

Work from `~/Coding/StudioProjects`. Save every script block under `$OUT/scripts/` with `python3 -I $RIG_TOOLKIT/extract_brief_blocks.py <this brief> $OUT/scripts`. Each block name is bold and in backticks, on the line right above its fence. Run every script with `bash $OUT/scripts/<name>.sh`. Only the scripts source `lib.sh`.

**`build.sh`**
```bash
. $(dirname $0)/lib.sh
C=8407923931729edd64601c70e1012adaf37d340a; T=43fc958577bacfd500ae7627919109f05886cdf5
# reuse this session's worktree if WT is set; else make one (never pick an arbitrary folder under worktrees/)
WT=${WT:-$($RIG_TOOLKIT/wt-new.sh home-widgets-opacity-r2 candidate main | tail -1)}
git -C $WT fetch https://github.com/o-jcardenass/open-headunit.git feat/home-widgets-and-opacity
git -C $WT checkout --detach $C
[ "$(git -C $WT rev-parse 'HEAD^{tree}')" = $T ] || { echo TREE_MISMATCH; exit 1; }
$RIG_TOOLKIT/rig_thermal.sh wait 70
HUR_DIR=$WT MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh || { echo BUILD_FAILED; exit 1; }
cp $(ls -t $RIG_DATA/apks/com.andrerinas.headunitrevived_*.apk | head -1) $OUT/C.apk
{ echo "WT=$WT"; echo "C md5 $(md5sum < $OUT/C.apk | cut -c1-32)"
  echo "HomeSurfaceOpacityPolicy=$(unzip -p $OUT/C.apk 'classes*.dex' | strings | grep -c HomeSurfaceOpacityPolicy)"
  echo "pressedFill=$(unzip -p $OUT/C.apk 'classes*.dex' | strings | grep -c pressedFill)"; } | tee $OUT/build.txt
```

Build gate: `HomeSurfaceOpacityPolicy` MUST be 1 or more, and `pressedFill` MUST be 1 or more. `pressedFill` is new in `84079239`, so round 1's APK does not carry it. A failed gate stops the round. If `git fetch` does not find `8407923931729edd`, the branch was not pushed yet: stop and ask.

Identity after each install (`inst` in `lib.sh` does both):

- `$RIG_TOOLKIT/apk_identity.sh $OUT/C.apk <serial>` MUST exit 0.
- The `ACTION_QUERY_STATE` reply MUST carry commit `840792393172`. A `-dirty` suffix is fine; say so.
- If `adb install -r -d` answers `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, stop and ask. Never uninstall.

## 2. What this is and why

Round 1 (`home-widgets-opacity-round1-results.md`) passed every graded run on `2d41ab4e`. After it, the opacity change grew in three ways, and this round tests them on hardware.

- **0% floor, buttons and borders.** The slider now goes from 0 to 100 (it was 20 to 100). The fills of the buttons (the active connection tile, the main card's action and cancel buttons) and every outline now fade with the panels. Text, icons and the focus ring stay opaque.
- **The press must still show.** From Android 5, a button's press is a ripple. With no mask, Android clips the ripple to the button's own fill, so a fill at 0% would hide the ripple. Every button now gets an opaque copy of its shape as the ripple mask. Below Android 5 there is no ripple: a press or a D-pad focus shows the pressed fill. That fill now never fades below 40% (`HomeSurfaceOpacityPolicy.PRESSED_MIN`), whatever the slider says.
- **The long-press menu stays opaque.** `HomeActionDialog` took the home's palette, so at a low opacity its panel turned see-through over the home. It now always takes the opaque palette.

D-SAM (API 19) is the only rig unit below Android 5, so it is the only one that can show the floor. On D-SAM, S-0 shows it through a D-pad focus, not a hold: below Android 5 the focused state and the pressed state are the same drawable, and a D-pad driver is the case the floor is for. The S-L and S-B runs also give the data to choose the floor: the developer asked whether 10% or 20% would be enough.

| Id | What | Run | Kind |
|---|---|---|---|
| H1 | Opacity 100: the bar, the settings tile and the action button hide the image (control, and the mapping check) | H-100 | GATE |
| H2 | Opacity 0: the bar, the settings tile and the action button let the image through | H-0 | GATE, the point |
| H3 | Opacity 0, settings tile held: the ripple shows | H-0 hold | GATE, the point |
| M1 | Opacity 0, long press: the menu panel is opaque | M-0 | GATE, the point |
| S1 | D-SAM opacity 100: the settings tile hides the image (control, and the mapping check) | S-100 | GATE |
| S2 | D-SAM opacity 0, a bar tile focused by the D-pad: the pressed fill shows at the 40% floor | S-0 | GATE, the point |
| SP | D-SAM: does `input swipe` with a duration work on API 19 | S-100 probe | RECORD, for `rig-quirks` |
| S3 | D-SAM resting tile at 10, 20 and 40 over a plain image | S-L10, S-L20, S-L40 | RECORD |
| S4 | D-SAM resting tile at 10, 20 and 40 over a busy image | S-B10, S-B20, S-B40 | RECORD |

**What a PASS looks like if the change did nothing.** On round 1's build, a stored 0 reads as 20, so in H-0 the tiles would sit at 20% and read `clear=0.000`, not 0.50 or more. On a build with the 0% floor but without the ripple mask, the H-0 hold would read the same `clear` as the rest. On a build without the dialog fix, M-0's panel would be transparent and read an `excessmed` near 255. On a build without the pressed floor, the S-0 focused tile would read the same `clear` as the rest. So H-0, M-0 and S-0 each tell the candidate from the build before its fix.

Not run, with the reason:

- **The Customization rows and the slider itself.** They sit below the first screen, and a scroll is banned. Round 1's C-REC already recorded that.
- **A hold on D-SAM.** `input swipe` with a duration failed on D-HP (API 17) in two rounds, and the developer recalls it failing on D-SAM (`rig-quirks/units/D-SAM-and-D-HP.md`). S-0 uses a D-pad focus instead, and the S-100 probe records what D-SAM answers.
- **Borders alone.** A 1 dp outline is too thin for the pixel helper after the 10% edge cut. H-0 asks for a RECORD line from the screenshot instead.
- **The clamp, the pressed floor and `fill()`.** `HomeSurfaceOpacityPolicyTest` covers them on the dev host.

## 3. What is different about this round

- **Leave animations as they are on both units.** Round 1 found that `animator_duration_scale` 0 makes `uiautomator dump` fail on D-HU with `ERROR: could not get idle state.` This brief never changes the scale.
- **A split background.** The image is magenta (`#FF00FF`) on its left half and green (`#00FF00`) on its right half. `centerCrop` keeps the split at the middle of the screen on any aspect ratio. `homeq.py` compares each pixel with the color under it.
- **D-SAM's screenshot is the portrait buffer.** `screencap` returns 800x1280 while the dump's bounds are landscape (`rotation="1"`, `rig-quirks/units/D-SAM-and-D-HP.md`). `homeq.py` tries each rotation and keeps the one that reads magenta at the left edge and green at the right edge, both at mid-height. It prints `map=identity`, `map=rotA` or `map=rotB`, and `map=UNKNOWN` if no rotation, or more than one, fits.
- **The pixel words.** Inside a node's box, with 10% cut from each side: `clear` is the share of pixels within 16 of the background on every channel. `excess` is how strongly the background shows: `min(R,B) - G` on magenta and `G - max(R,B)` on green. It is 255 on the bare image and falls as a fill covers it. `fillmed` is the median `excess` of the pixels between 40 and 235, which leaves out white text and icons. A day tile on magenta reads about 228 at 10%, 202 at 20%, 150 at 40% and below 0 when opaque. The helper was tested on synthetic screens in all three mappings.
- **The background file is the round's own.** It goes to `files/ohu_rig_bg.png` on each unit, so the user's `custom_home_bg.png` is never touched. `restore.sh` deletes the round's file.
- **D-SAM has no root and no `sed`.** Settings go through `set_prefs_runas_host.py`, and files through `run-as` (`rig-quirks/units/D-SAM.md`). `SettingsActivity` is never opened by `am`.
- **A hold opens Settings at its end (D-HU only).** `input swipe` with the same start and end point is a hold, and its release is a tap on `settings_button`. The screenshot is taken 2 s into the 4 s hold, before the release. `fin` stops the app right after, so the Settings screen does nothing.
- **D-SAM gets no hold.** S-0 presses D-pad keys until a bar tile has focus (`focused="true"` in the dump), then reads that tile. The keys are LEFT, LEFT, DOWN, DOWN, DOWN, UP, UP, UP, with a dump after each, and the loop stops at the first bar tile. Every bar tile at rest uses the same neutral tile drawable, so any of them will do. The card shows focus as an opaque ring with no fill, so the loop does not stop on it.
- **The audio settings are a deliberate worst case.** Do not touch `use-aac-audio`, `audio-latency-multiplier` or `audio-queue-capacity`.

## 4. Settings keys

`seed` in `lib.sh` writes these with the app stopped and reads them back: `set_hu_settings_host.py` on D-HU (rooted), `set_prefs_runas_host.py` on D-SAM. `restore.sh` puts each unit's backup back at the end.

| Key | Type | `BASE` (every run) | D-HU runs | D-SAM runs | Why |
|---|---|---|---|---|---|
| `onboarding-version` | int | 2 | | | no wizard |
| `rename_notice_shown_v2` | boolean | true | | | no notice |
| `aa174_notice_shown` | boolean | true | | | no notice |
| `native-driver-selection-mode` | int | 0 | | | no driver selector |
| `log-level` | int | 2 | | | INFO; every graded line is INFO or a system line |
| `connection-modes` | set | `usb,wifi,self` | | | every tile |
| `video-profile-starvation-cap` | | deleted | | | |
| `has-accepted-disclaimer` | boolean | true | | | |
| `auto-connect-last-session` | boolean | false | | | idle home |
| `auto-start-self-mode` | boolean | false | | | idle home |
| `auto-connect-single-usb` | boolean | false | | | no countdown |
| `auto-connect-delay-seconds` | int | 0 | | | no countdown |
| `wifi-connection-mode` | int | 0 | | | idle, so the home can be dumped |
| `home-pages-hint-shown` | boolean | true | | | no hint in the way |
| `home-widget-pages` | string | deleted | | | page 1 only |
| `home-widgets` | string | deleted | | | an empty row: only the add tile |
| `show-home-favorite-apps` | boolean | true | | | |
| `show-home-widgets` | boolean | true | | | M-0's menu offers "Add page" only with widgets on |
| `home-background-image-path` | string | `/data/data/com.andrerinas.headunitrevived/files/ohu_rig_bg.png` | | | the round's own file |
| `home-background-night-mode` | int | 2 | | | NONE: no night dim |
| `enable-car-launcher` | boolean | | true | false | D-HU: the widget row exists for M-0. D-SAM: a plain home |
| `screen-orientation` | int | | | 2 | the unit needs 2 (`rig-quirks/units/D-SAM.md`); an earlier round found 4 stored |
| `home-surface-opacity-percent` | int | | per run | per run | section 8 |

Pre-flight records D-SAM's stored `screen-orientation`. The backup puts it back at the end.

`ACTION_LOG_MARKER` is not in `AutomationCommandPolicy.CONFIGURING` at `84079239`, so `allow-external-configuration` is not needed.

## 5. Helpers

Run every adb call in sequence, never two at once against one unit. The `logcat` capture is the only standing stream, one unit at a time. Write the results file after every run.

**`lib.sh`**
```bash
source ~/Coding/StudioProjects/rig-toolkit/rig_devices.sh
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
MAIN=$PKG/com.andrerinas.openheadunit.main.MainActivity
D_HU=${D_HU:-27870808938846}; D_SAM=${D_SAM:-30041c35642d2200}
OUT=$RIG_DATA/rounds/home-widgets-opacity-round2; S=$OUT/scripts; mkdir -p $S $OUT/ui
export LC_ALL=C
[ -f $OUT/env.sh ] && . $OUT/env.sh       # HU_OWNER
BGPATH=/data/data/$PKG/files/ohu_rig_bg.png
# unit hu|sam: every helper below acts on $H
unit() { case $1 in hu) H=$D_HU; U=hu;; sam) H=$D_SAM; U=sam;; esac; }
BASE="scalar:int:onboarding-version:2 scalar:boolean:rename_notice_shown_v2:true scalar:boolean:aa174_notice_shown:true
  scalar:int:native-driver-selection-mode:0 scalar:int:log-level:2 set:connection-modes:usb,wifi,self del:video-profile-starvation-cap
  scalar:boolean:has-accepted-disclaimer:true scalar:boolean:auto-connect-last-session:false scalar:boolean:auto-start-self-mode:false
  scalar:boolean:auto-connect-single-usb:false scalar:int:auto-connect-delay-seconds:0 scalar:int:wifi-connection-mode:0
  scalar:boolean:home-pages-hint-shown:true del:home-widget-pages del:home-widgets
  scalar:boolean:show-home-favorite-apps:true scalar:boolean:show-home-widgets:true
  scalar:string:home-background-image-path:$BGPATH scalar:int:home-background-night-mode:2"
K_HU="$BASE scalar:boolean:enable-car-launcher:true"
K_SAM="$BASE scalar:boolean:enable-car-launcher:false scalar:int:screen-orientation:2"
op() { echo "scalar:int:home-surface-opacity-percent:$1"; }
KEYS="home-surface-opacity-percent home-background-image-path home-background-night-mode enable-car-launcher
  show-home-widgets wifi-connection-mode screen-orientation"

send() { local a=$1; shift; adb -s $H shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; sleep 0.3; }
mark() { send ACTION_LOG_MARKER --es text "$1" >/dev/null; echo "$1 $(date +%T)" >> $OUT/marks.txt; }
launch() { adb -s $H shell am start -n $MAIN >/dev/null; sleep 0.3; }
running() { { adb -s $H shell ps; adb -s $H shell ps -A; } 2>/dev/null | grep -ac "$PKG"; }
# headunit://exit only to a running app; sent to a stopped one it starts and arms it (rig-quirks/topics/lifecycle.md)
stopapp() { if [ "$(running)" -ge 1 ]; then adb -s $H shell am start -a android.intent.action.VIEW -d headunit://exit >/dev/null; sleep 5; fi
  adb -s $H shell am force-stop $PKG; sleep 1; }

cap() { adb -s $H logcat -G 16M >/dev/null 2>&1; adb -s $H logcat -c; stdbuf -oL adb -s $H logcat -v time > $1 & echo $! > $1.pid; sleep 1; }
uncap() { kill $(cat $1.pid) 2>/dev/null; }
# seg <file> <from> [<to>]: lines after the last <from> marker up to the next <to> marker (or the end)
seg() { awk -v a=": $2" -v b=": ${3:-@@none@@}" '
  function ends(s,t){ return length(s)>=length(t) && substr(s,length(s)-length(t)+1)==t }
  { l=$0; sub(/\r+$/,"",l) }
  ends(l,a){ buf=""; on=1; next }
  on && ends(l,b){ on=0 }
  on{ buf=buf $0 "\n" }
  END{ printf "%s", buf }' "$1"; }
cnt()  { seg "$1" "$2" "$3" | grep -acF -- "$4"; }
fatal() { seg "$1" "$2" "$3" | grep -aF -A3 'FATAL EXCEPTION' | grep -acF "Process: $PKG"; }
waitfor() { local f=$1 m=$2 cap=$3 s=$4 t=0
  while [ $t -lt $cap ]; do [ "$(cnt $f $m '' "$s")" -ge 1 ] && return 0; sleep 1; t=$((t+1)); done; return 1; }

th_gate() { $RIG_TOOLKIT/rig_thermal.sh wait 75 || $RIG_TOOLKIT/rig_thermal.sh wait 75 || { echo HOST_HOT; return 1; }
  $RIG_TOOLKIT/rig_thermal.sh watch $OUT/thermal-$1.log & THPID=$!; }
th_end() { kill $THPID 2>/dev/null; wait $THPID 2>/dev/null; }

pull_settings() { if [ $U = hu ]; then adb -s $H shell cat /data/data/$PKG/shared_prefs/settings.xml
  else adb -s $H shell run-as $PKG cat shared_prefs/settings.xml; fi | tr -d '\r' | grep -av '^$'; }
showkeys() { local f=$1 k; for k in $KEYS; do grep -aoE "<[a-z]+ name=\"$k\"[^>]*(/>|>[^<]*</[a-z]+>)" $f || echo "$k ABSENT"; done; }
# seed <spec>...: app stopped, write, read back
seed() { adb -s $H shell am force-stop $PKG; sleep 1
  if [ $U = hu ]; then python3 -I $RIG_TOOLKIT/set_hu_settings_host.py $H "$@" >> $OUT/seed.log 2>&1
  else python3 -I $RIG_TOOLKIT/set_prefs_runas_host.py $H "$@" >> $OUT/seed.log 2>&1; fi
  pull_settings > $OUT/seeded-$U.xml; showkeys $OUT/seeded-$U.xml; }
inst() { adb -s $H install -r -d $OUT/C.apk | tail -1
  $RIG_TOOLKIT/apk_identity.sh $OUT/C.apk $H; echo "apk_identity exit $?"
  send ACTION_QUERY_STATE | grep -ao 'commit[^,}]*'; adb -s $H shell am force-stop $PKG; }
# pushbg <png>: the app stopped; the round's own background file
pushbg() { adb -s $H push $1 /data/local/tmp/ohu-bg.png >/dev/null; adb -s $H shell chmod 644 /data/local/tmp/ohu-bg.png
  if [ $U = hu ]; then adb -s $H shell mkdir -p /data/data/$PKG/files
    adb -s $H shell cp /data/local/tmp/ohu-bg.png $BGPATH; adb -s $H shell chown $HU_OWNER $BGPATH; adb -s $H shell chmod 600 $BGPATH
    adb -s $H shell ls -l $BGPATH
  else adb -s $H shell run-as $PKG mkdir /data/data/$PKG/files >/dev/null 2>&1
    adb -s $H shell run-as $PKG cp /data/local/tmp/ohu-bg.png $BGPATH; adb -s $H shell run-as $PKG ls -l $BGPATH; fi; }
uidump() { adb -s $H shell rm -f /sdcard/ohu-ui.xml; sleep 0.3
  adb -s $H shell uiautomator dump /sdcard/ohu-ui.xml >/dev/null 2>&1; sleep 0.3
  adb -s $H pull /sdcard/ohu-ui.xml $OUT/ui/$1.xml >/dev/null 2>&1; sleep 0.3
  adb -s $H shell screencap -p /sdcard/ohu-ui.png; sleep 0.3; adb -s $H pull /sdcard/ohu-ui.png $OUT/ui/$1.png >/dev/null 2>&1
  [ -s $OUT/ui/$1.xml ] || { adb -s $H shell uiautomator dump /sdcard/ohu-ui.xml >/dev/null 2>&1; sleep 0.3
    adb -s $H pull /sdcard/ohu-ui.xml $OUT/ui/$1.xml >/dev/null 2>&1; }
  [ -s $OUT/ui/$1.xml ] || echo "$1 DUMP_FAILED" | tee -a $OUT/marks.txt >&2; }
hq() { python3 -I $S/homeq.py "$@"; }
# one <run> <spec>...: stop, seed, mark, launch, wait for the home, dump; prints the counts. fin ends the run.
one() { R=$1; shift; stopapp; seed "$@" | tee $OUT/$R-seed.txt
  mark "$R-start"; launch
  waitfor $CAPF "$R-start" 30 'HomeFragment: onResume. isConnected=' && echo "$R resumed" || echo "$R NO_RESUME"
  sleep 5; uidump $R
  { echo "card=$(hq count $OUT/ui/$R.xml home_primary_card)"
    echo "bgfail=$(cnt $CAPF $R-start '' 'Failed to load custom background: ')"
    for id in settings_button home_driver_bar home_primary_action; do echo "$id $(hq stats $OUT/ui/$R.png $OUT/ui/$R.xml $id)"; done
  } | tee $OUT/$R-counts.txt; }
# hold (D-HU only): 4 s on the settings tile, screenshot at 2 s, all in one adb call
hold() { local xy; xy=$(hq center $OUT/ui/$R.xml settings_button) || { echo "hold NO_NODE" | tee -a $OUT/$R-counts.txt; return; }
  adb -s $H shell rm -f /sdcard/ohu-hold.png
  adb -s $H shell "input swipe $xy $xy 4000 & sleep 2; screencap -p /sdcard/ohu-hold.png; wait" > $OUT/$R-hold-shell.txt 2>&1
  sleep 1; adb -s $H pull /sdcard/ohu-hold.png $OUT/ui/$R-hold.png >/dev/null 2>&1
  { echo "holdshell=$(grep -aic 'usage\|error\|exception' $OUT/$R-hold-shell.txt)"
    if [ -s $OUT/ui/$R-hold.png ]; then echo "hold settings_button $(hq stats $OUT/ui/$R-hold.png $OUT/ui/$R.xml settings_button $OUT/ui/$R.png)"
    else echo "hold NO_PNG"; fi; } | tee -a $OUT/$R-counts.txt; }
# focus (D-SAM): D-pad keys until a bar tile has focus, then that tile's stats focused and at rest
focus() { local k i=0 id
  for k in 21 21 20 20 20 19 19 19; do i=$((i+1)); adb -s $H shell input keyevent $k; sleep 1; uidump $R-f$i
    id=$(hq focused $OUT/ui/$R-f$i.xml); echo "focus step $i key $k -> $id" | tee -a $OUT/$R-counts.txt
    case $id in settings_button|usb_button|wifi_button|self_mode_button|exit_button)
      { echo "focus $id $(hq stats $OUT/ui/$R-f$i.png $OUT/ui/$R-f$i.xml $id $OUT/ui/$R.png)"
        echo "rest $id $(hq stats $OUT/ui/$R.png $OUT/ui/$R.xml $id)"; } | tee -a $OUT/$R-counts.txt; return;; esac
  done; echo "focus NO_BAR_TILE" | tee -a $OUT/$R-counts.txt; }
fin() { mark "$R-end"; echo "fatal=$(fatal $CAPF $R-start $R-end)" | tee -a $OUT/$R-counts.txt
  stopapp; pull_settings > $OUT/$R-after.xml; showkeys $OUT/$R-after.xml > $OUT/$R-after-keys.txt; }
```

**`homeq.py`**
```python
# count <xml> <id>...            nodes whose resource-id ends ":id/<id>" with on-screen, non-empty bounds
# center <xml> <id> [fx fy]      a point inside the first such node, as fractions of its box (default 0.5 0.5)
# focused <xml>                  the id (after ":id/") of the node with focused="true", or NONE
# stats <png> <xml> <id> [ref]   pixel stats inside the first such node, 10% cut from each side;
#                                [ref] is a screenshot of the same screen to find the rotation from (a dialog dims the edges)
# The background is magenta on the screen's left half and green on its right half (mkpng.py split).
# A pixel is "clear" when it is within 16 of the background under it on every channel.
# "excess" is how strongly the background color shows: min(R,B)-G on magenta, G-max(R,B) on green.
# "fillmed" is the median excess of the pixels with excess in (40, 235): the fill, with text and icons left out.
# The screenshot may be the unrotated portrait buffer (D-SAM); the mapping is found from the two screen edges.
import re, struct, sys, zlib

def nodes(xml):
    s = open(xml, encoding="utf-8", errors="replace").read()
    out = []
    for m in re.finditer(r'<node [^>]*>', s):
        t = m.group(0)
        rid = re.search(r'resource-id="([^"]*)"', t); b = re.search(r'bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"', t)
        if b: out.append((rid.group(1) if rid else "", tuple(int(x) for x in b.groups())))
    return out

def screen(ns):
    return max(bx[2] for _, bx in ns), max(bx[3] for _, bx in ns)

def onscreen(ns):
    W, _ = screen(ns)
    return [(i, bx) for i, bx in ns if bx[2] > bx[0] and bx[3] > bx[1] and bx[0] < W and bx[2] > 0]

def first(xml, rid):
    m = [bx for i, bx in onscreen(nodes(xml)) if i.endswith(":id/" + rid)]
    return m[0] if m else None

def png(path):
    d = open(path, "rb").read(); assert d[:8] == b"\x89PNG\r\n\x1a\n"
    p, idat = 8, b""
    while p < len(d):
        n, k = struct.unpack(">I4s", d[p:p + 8]); c = d[p + 8:p + 8 + n]; p += 12 + n
        if k == b"IHDR": w, h, bd, ct, _, _, il = struct.unpack(">IIBBBBB", c); assert bd == 8 and il == 0 and ct in (2, 6)
        elif k == b"IDAT": idat += c
    bpp = 4 if ct == 6 else 3; raw = zlib.decompress(idat); st = w * bpp; rows = []; prev = bytearray(st); i = 0
    for _ in range(h):
        f = raw[i]; line = bytearray(raw[i + 1:i + 1 + st]); i += 1 + st
        for x in range(st):
            a = line[x - bpp] if x >= bpp else 0; b = prev[x]; c = prev[x - bpp] if x >= bpp else 0
            if f == 1: line[x] = (line[x] + a) & 255
            elif f == 2: line[x] = (line[x] + b) & 255
            elif f == 3: line[x] = (line[x] + (a + b) // 2) & 255
            elif f == 4:
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                line[x] = (line[x] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        rows.append(line); prev = line
    return w, h, bpp, rows

MAG, GRN = (255, 0, 255), (0, 255, 0)

def near(px, bg): return all(abs(a - b) <= 16 for a, b in zip(px, bg))

def mappings(W, H, w, h):
    # logical (x, y) -> buffer (px, py)
    if (w, h) == (W, H): return {"identity": lambda x, y: (x, y)}
    if (w, h) == (H, W): return {"rotA": lambda x, y: (y, W - 1 - x), "rotB": lambda x, y: (H - 1 - y, x)}
    return {}

def mapping(img, W, H):
    w, h, bpp, rows = img
    def at(f, x, y):
        px, py = f(x, y); o = px * bpp; return tuple(rows[py][o:o + 3])
    good = [k for k, f in mappings(W, H, w, h).items()
            if near(at(f, 3, H // 2), MAG) and near(at(f, W - 4, H // 2), GRN)]
    return (good[0], mappings(W, H, w, h)[good[0]]) if len(good) == 1 else ("UNKNOWN", None)

def stats(pngf, xml, rid, ref=None):
    ns = nodes(xml); W, H = screen(ns); box = first(xml, rid)
    if not box: return "NO_NODE"
    img = png(pngf); w, h, bpp, rows = img
    name, f = mapping(png(ref) if ref else img, W, H)
    if not f: return "map=UNKNOWN png=%dx%d screen=%dx%d" % (w, h, W, H)
    l, t, r, b = box; dx, dy = (r - l) // 10, (b - t) // 10
    n = clear = 0; ex, fill = [], []
    for y in range(t + dy, b - dy, 2):
        for x in range(l + dx, r - dx, 2):
            px, py = f(x, y); o = px * bpp; p = tuple(rows[py][o:o + 3]); R, G, B = p
            bg = MAG if x < W // 2 else GRN
            e = min(R, B) - G if bg is MAG else G - max(R, B)
            n += 1; clear += near(p, bg); ex.append(e)
            if 40 < e < 235: fill.append(e)
    med = lambda v: sorted(v)[len(v) // 2] if v else -999
    return "map=%s clear=%.3f excessmed=%d fillmed=%d fillshare=%.3f n=%d box=%s" % (
        name, clear / n, med(ex), med(fill), len(fill) / n, n, list(box))

cmd = sys.argv[1]
if cmd == "count":
    ns = onscreen(nodes(sys.argv[2])); ids = sys.argv[3:]
    print(sum(1 for i, _ in ns if any(i.endswith(":id/" + x) for x in ids)))
elif cmd == "center":
    box = first(sys.argv[2], sys.argv[3])
    if not box: print("NO_NODE"); sys.exit(2)
    fx, fy = (float(sys.argv[4]), float(sys.argv[5])) if len(sys.argv) > 5 else (0.5, 0.5)
    l, t, r, b = box; print(int(l + (r - l) * fx), int(t + (b - t) * fy))
elif cmd == "focused":
    s = open(sys.argv[2], encoding="utf-8", errors="replace").read()
    m = [re.search(r'resource-id="[^"]*:id/([^"]*)"', t) for t in re.findall(r'<node [^>]*focused="true"[^>]*>', s)]
    m = [x.group(1) for x in m if x]
    print(m[-1] if m else "NONE")
elif cmd == "stats":
    print(stats(sys.argv[2], sys.argv[3], sys.argv[4], sys.argv[5] if len(sys.argv) > 5 else None))
```

**`mkpng.py`**
```python
# mkpng.py split <out>: 128x64, left half #FF00FF, right half #00FF00
# mkpng.py busy <out>:  320x180 of 20 px blocks in fixed pseudo-random colors (a stand-in for a photo)
import random, struct, sys, zlib
def chunk(k, d): return struct.pack(">I", len(d)) + k + d + struct.pack(">I", zlib.crc32(k + d) & 0xffffffff)
def write(path, w, h, pixel):
    raw = b"".join(b"\x00" + b"".join(bytes(pixel(x, y)) for x in range(w)) for y in range(h))
    open(path, "wb").write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b""))
if sys.argv[1] == "split":
    write(sys.argv[2], 128, 64, lambda x, y: (255, 0, 255) if x < 64 else (0, 255, 0))
else:
    rnd = random.Random(7); cols = {}
    def busy(x, y):
        k = (x // 20, y // 20)
        if k not in cols: cols[k] = tuple(rnd.randrange(256) for _ in range(3))
        return cols[k]
    write(sys.argv[2], 320, 180, busy)
```

## 6. Pre-flight and install

Run `build.sh` in the background and `preflight.sh` in the foreground beside it: pre-flight is adb only. Run `install.sh` once the build gate passes.

**`preflight.sh`**
```bash
. $(dirname $0)/lib.sh
exec > >(tee -a $OUT/preflight.txt) 2>&1
echo "==== preflight $(date)"
$RIG_TOOLKIT/rig_preflight.sh D_HU:wifi; $RIG_TOOLKIT/rig_preflight.sh D_SAM:wifi; $RIG_TOOLKIT/rig_cleanup.sh list
for s in $D_HU $D_SAM; do adb devices | grep -q "^$s" || { echo "$s NOT ON ADB"; exit 1; }; done   # MUST be on adb
unit hu; adb -s $H shell id                                                 # MUST show uid=0(root)
for u in hu sam; do unit $u
  echo "$u sdk $(adb -s $H shell getprop ro.build.version.sdk | tr -d '\r') anim $(adb -s $H shell settings get global animator_duration_scale | tr -d '\r')"
  adb -s $H shell wm size 2>/dev/null; adb -s $H shell wm density 2>/dev/null
  stopapp
  [ -f $OUT/backup-$u.xml ] || pull_settings > $OUT/backup-$u.xml
  echo "$u backup bytes $(wc -c < $OUT/backup-$u.xml)"                     # MUST be over 0
  showkeys $OUT/backup-$u.xml
done
unit hu; echo "home role $(adb -s $H shell cmd role get-role-holders android.app.role.HOME | tr -d '\r')"   # MUST NOT be $PKG
HU_OWNER=$(adb -s $H shell ls -ld /data/data/$PKG/shared_prefs | awk '{print $3":"$4}' | tr -d '\r'); echo "HU_OWNER=$HU_OWNER"
unit sam; echo "sam date $(adb -s $H shell date | tr -d '\r') host $(date)"
echo "sam battery $(adb -s $H shell dumpsys battery | grep -a 'level' | tr -d '\r')"      # MUST be 15 or more
python3 -I $S/mkpng.py split $OUT/split.png; python3 -I $S/mkpng.py busy $OUT/busy.png; ls -l $OUT/split.png $OUT/busy.png
echo "HU_OWNER=$HU_OWNER" > $OUT/env.sh; cat $OUT/env.sh
```

Pre-flight gates: each line marked MUST. If D-SAM's battery level is below 15, stop and ask for it to be charged from a separate supply. Put the whole `preflight.txt` in Setup notes, with both units' `sdk`, `anim`, `wm size`, D-SAM's `screen-orientation` and its clock against the host's.

**`install.sh`**
```bash
. $(dirname $0)/lib.sh
exec > >(tee $OUT/install.txt) 2>&1
for u in hu sam; do unit $u; echo "== $u"; inst; done
```

Each unit MUST show `apk_identity exit 0` and commit `840792393172`.

## 7. The deciding lines

Each was checked with `grep -F -r` against `app/src` at `84079239`. All are in the unit's capture (`$OUT/hu.txt` or `$OUT/sam.txt`), between the run's `<run>-start` and `<run>-end` markers.

| String | Source | Level | Used for |
|---|---|---|---|
| `AutomationMarker: ` | `AutomationReceiver` | WARN | the run window |
| `HomeFragment: onResume. isConnected=` | `HomeFragment.onResume` | INFO | the home was shown in this run |
| `Failed to load custom background: ` | `BaseActivity.applyWindowBackground` | ERROR | the image did not load |
| `FATAL EXCEPTION` with `Process: com.andrerinas.headunitrevived` | system | | no crash |

The other evidence is each run's dump and screenshots, read by `homeq.py`:

| Node id (`<pkg>:id/<id>`) | Layout | Means |
|---|---|---|
| `home_primary_card` | `view_home_primary_card.xml` | the home is on screen and the dump worked |
| `settings_button` | `fragment_home.xml` | the neutral bar tile that the holds press |
| `home_driver_bar` | `fragment_home.xml` | the bar panel |
| `home_primary_action` | `view_home_primary_card.xml` | the main card's action button |
| `home_widgets_row` | `fragment_home.xml` | the widget row; a long press on its empty end opens the page menu |
| `home_actions_panel` | `dialog_home_actions.xml` | the long-press menu's panel |

## 8. Runs

D-HU runs first, then D-SAM, each with its own capture. Before each unit's runs, gate on the host temperature. Each run is about 1 min.

**`runs-hu.sh`**
```bash
. $(dirname $0)/lib.sh
exec > >(tee -a $OUT/runs-hu.txt) 2>&1
unit hu; th_gate hu || exit 1
CAPF=$OUT/hu.txt; stopapp; pushbg $OUT/split.png; cap $CAPF
one H-100 $K_HU $(op 100); fin
one H-0 $K_HU $(op 0); hold; fin
# M-0: long press on the empty end of the widget row, then dump the menu
one M-0 $K_HU $(op 0)
XY=$(hq center $OUT/ui/M-0.xml home_widgets_row 0.95 0.5) && {
  adb -s $H shell input swipe $XY $XY 1500; sleep 2; uidump M-0-menu
  { echo "menu=$(hq count $OUT/ui/M-0-menu.xml home_actions_panel)"
    echo "menu home_actions_panel $(hq stats $OUT/ui/M-0-menu.png $OUT/ui/M-0-menu.xml home_actions_panel $OUT/ui/M-0.png)"; } | tee -a $OUT/M-0-counts.txt
  adb -s $H shell input keyevent 4; sleep 1; } || echo "M-0 NO_ROW" | tee -a $OUT/M-0-counts.txt
fin
uncap $CAPF; th_end
```

**`runs-sam.sh`**
```bash
. $(dirname $0)/lib.sh
exec > >(tee -a $OUT/runs-sam.txt) 2>&1
unit sam; th_gate sam || exit 1
CAPF=$OUT/sam.txt; stopapp; pushbg $OUT/split.png; cap $CAPF
one S-100 $K_SAM $(op 100)
# the swipe probe: a 500 ms hold on the bare left edge, where nothing is under the touch
adb -s $H shell input swipe 3 400 3 400 500 > $OUT/swipe-probe.txt 2>&1
echo "swipeprobe=$(grep -aic 'usage\|error\|invalid' $OUT/swipe-probe.txt) bytes=$(wc -c < $OUT/swipe-probe.txt)" | tee -a $OUT/S-100-counts.txt
fin
one S-0 $K_SAM $(op 0); focus; fin
for p in 10 20 40; do one S-L$p $K_SAM $(op $p); fin; done
stopapp; pushbg $OUT/busy.png
for p in 10 20 40; do one S-B$p $K_SAM $(op $p); fin; done
uncap $CAPF; th_end
```

Grade every run from `$OUT/<run>-counts.txt`. In every run, `fatal` MUST be 0, the run's line MUST read `<run> resumed`, and `bgfail` MUST be 0. A `DUMP_FAILED` or `card=0` means the dump did not see the home: re-run that run once by hand with the same `one` line, its `hold` if it has one, and `fin` (a new capture is fine, say so). A second failure makes it UNTESTABLE. Read the `stats` lines as `<id> map=... clear=... excessmed=... fillmed=...`.

### H-100: D-HU opacity 100 (control)

- Reach: `settings_button` and `home_driver_bar` both read `map=identity`. If not, every D-HU pixel verdict is INCONCLUSIVE (mapping), with the line.
- PASS: `settings_button clear` 0.05 or less, and `home_driver_bar clear` 0.05 or less.
- Record `home_primary_action clear`. If it is above 0.10, the unit draws the action as an outline (extreme dark), and the action clause of H-0 is INCONCLUSIVE.

### H-0: D-HU opacity 0, then a hold on the settings tile (the point)

- PASS at rest: `settings_button clear` 0.50 or more, `home_driver_bar clear` 0.50 or more, and `home_primary_action clear` 0.40 or more. On round 1's build all three would read 0.000, because a stored 0 read as 20.
- PASS in the hold: `holdshell=0`, and `hold settings_button clear` 0.20 or less and at least 0.30 below the rest value. Without the ripple mask it would read the same as the rest.
- If `holdshell` is above 0, the hold is UNTESTABLE: quote `H-0-hold-shell.txt`.
- RECORD: in `H-0.png`, say in one line each whether the tile outlines can be seen, and whether the labels and icons can be read.

### M-0: D-HU opacity 0, the long-press menu (the point)

- Reach: `menu=1`. If it is 0, M-0 is INCONCLUSIVE (no menu): attach `M-0-menu.png`.
- PASS: `home_actions_panel clear` 0.05 or less and `excessmed` 30 or less. Without the dialog fix the panel would be transparent at 0 and read an `excessmed` near 255, or near 127 under the dialog's dim.

### S-100: D-SAM opacity 100 (control)

- Reach: `settings_button` reads `map=rotA`, `map=rotB` or `map=identity`. If it reads `map=UNKNOWN`, every D-SAM pixel verdict is INCONCLUSIVE (mapping): attach `S-100.png` and `S-100.xml`.
- PASS: `settings_button clear` 0.05 or less. This also proves that the mapping puts the box on the tile.

### S-0: D-SAM opacity 0, then a D-pad focus on a bar tile (the point)

- Reach: a `focus <id>` line. If the loop prints `focus NO_BAR_TILE`, S-0 is INCONCLUSIVE (no focus): list the eight `focus step` lines and attach `S-0-f8.png`.
- PASS at rest: the `rest <id> clear` 0.50 or more.
- PASS when focused: the `focus <id> clear` 0.20 or less, and its `fillmed` from 110 to 190. That range is the 40% floor: a fill with no floor would read clear, and an opaque one reads a `fillmed` of -999 or below 110.

### S-100 probe: `input swipe` with a duration on API 19 (RECORD)

- Record the `swipeprobe=` line and the whole of `swipe-probe.txt`. `swipeprobe=0 bytes=0` means API 19 took the duration. Anything else means it refused it: quote the first line.
- Put the answer in `rig-quirks/units/D-SAM-and-D-HP.md`, in the entry on `input swipe`, in place of "not yet measured on D-SAM".

### S-L10, S-L20, S-L40: D-SAM resting tile over the plain image (RECORD)

- Record `settings_button fillmed` and `clear` for each. Expect about 228, 202 and 150. A resting tile at a value is the same fill a pressed tile would show with the floor at that value, to within 2 of `fillmed`.
- Look at `S-L10.png`, `S-L20.png` and `S-L40.png` (portrait, so turn them), and say in one line each whether the settings tile reads as a tile at a glance.

### S-B10, S-B20, S-B40: D-SAM resting tile over the busy image (RECORD)

- No pixel verdict: the image is not a known color.
- Look at the three screenshots and say in one line each whether the settings tile reads as a tile at a glance. This is the harder case, and it decides between 10, 20 and 40.

Stop rule: none beyond the per-run re-run above. Every run is run once.

## 9. Restore

**`restore.sh`**
```bash
. $(dirname $0)/lib.sh
exec > >(tee $OUT/restore.txt) 2>&1
for u in hu sam; do unit $u; echo "== $u"; stopapp
  adb -s $H push $OUT/backup-$u.xml /data/local/tmp/ohu-hwo.xml >/dev/null; adb -s $H shell chmod 644 /data/local/tmp/ohu-hwo.xml
  if [ $u = hu ]; then adb -s $H shell cp /data/local/tmp/ohu-hwo.xml /data/data/$PKG/shared_prefs/settings.xml
    adb -s $H shell chown $HU_OWNER /data/data/$PKG/shared_prefs/settings.xml; adb -s $H shell chmod 660 /data/data/$PKG/shared_prefs/settings.xml
    adb -s $H shell rm -f $BGPATH
  else adb -s $H shell run-as $PKG cp /data/local/tmp/ohu-hwo.xml shared_prefs/settings.xml
    adb -s $H shell run-as $PKG rm $BGPATH; fi
  pull_settings | diff -B - $OUT/backup-$u.xml >/dev/null && echo "$u SETTINGS_BACK" || echo "$u SETTINGS_DIFFER"
  echo "$u bg after: $(adb -s $H shell ls -l $BGPATH 2>&1 | tr -d '\r')"
  adb -s $H shell rm -f /data/local/tmp/ohu-hwo.xml /data/local/tmp/ohu-bg.png /sdcard/ohu-ui.xml /sdcard/ohu-ui.png /sdcard/ohu-hold.png
done
$RIG_TOOLKIT/rig_preflight.sh D_HU:wifi; $RIG_TOOLKIT/rig_preflight.sh D_SAM:wifi
```

`SETTINGS_BACK` MUST print for both units. On D-SAM, `ls` of the background reads "No such file" or "Permission denied" after the delete; record which. The candidate APK stays installed on both units: the next round on each installs its own build.

## 10. Do not re-run

Round 1's W-A to W-D (the widgets switch) and O-100 and O-40 (the opacity at 100 and 40) all passed on `2d41ab4e`. The widgets switch did not change since, so do not re-run W-A to W-D.

## 11. Report back

1. H-0: `clear` at rest for the settings tile, the bar and the action button, and the hold's `clear`. They decide whether 0% reaches the screen and whether the ripple survives it.
2. M-0: the menu panel's `clear` and `excessmed`. They decide whether the menu stays opaque.
3. S-0: the focused tile's `clear` and `fillmed`, and the three S-B lines. They decide whether the 40% floor works and whether it can go lower.
4. The S-100 swipe probe: whether API 19 takes a swipe duration.
