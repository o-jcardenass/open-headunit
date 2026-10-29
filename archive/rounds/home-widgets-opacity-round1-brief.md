# home-widgets-opacity, round 1 brief

Published name: `home-widgets-opacity-round1-brief.md`. Results go in `home-widgets-opacity-round1-results.md`.
Evidence: create the release `rig-evidence-home-widgets-opacity` (first round of this thread) and upload `home-widgets-opacity-round1-captures.zip` to it.

Units: **D-HU only** (the home screen as launcher, idle, no phone). No phone, no Android Auto session, no Bluetooth or WiFi change. So no phone capture is needed: no run touches the phone's Android Auto.

Queue line: `home-widgets-opacity-round1-brief.md` (C `2d41ab4e` on `fork/feat/home-widgets-and-opacity`, one commit on `main` `947b3ae9`; one build, settings arms only; unit D-HU only, idle; about 45 min; W-C, W-D and O-40 are the point).

Pairing: D-HU only, and every run is idle with no radio change. Any round that does not use D-HU can run beside this one.

Cabling: D-HU on adb.

**Hand steps.** None.

**Injected gestures.** None. **Injected keys.** One BACK, in C-REC only.

**Time budget.** Build 20 min, with pre-flight (3 min) beside it. Install 2 min. Runs W-A to W-D 6 min, O-100 and O-40 4 min, C-REC 2 min. Thermal waits about 3 min. Restore 3 min. **About 45 min in all, 12 min of it runs.**

## 1. Build and baseline

One APK. No baseline build: the four widget arms and the two opacity arms are settings changes on the candidate, and each arm's control is another arm (section 2). History was not rewritten.

| Arm | Ref | Commit | Tree |
|---|---|---|---|
| C (candidate) | `fork/feat/home-widgets-and-opacity` | `2d41ab4e47ed1f4309050291ea9d2e4aa84cd3e2` | `1633c0255d14282d773e7a6d945666b4c07eebb5` |

The dev host ran the JVM suite at `2d41ab4e`: 4249 tests, 0 failures. Do not run the unit tests on the rig. No other queued round shares this base.

Work from `~/Coding/StudioProjects`. Save every script block under `$OUT/scripts/` with `python3 -I $RIG_TOOLKIT/extract_brief_blocks.py <this brief> $OUT/scripts`. Each block name is bold and in backticks, on the line right above its fence. Run every script with `bash $OUT/scripts/<name>.sh`. Only the scripts source `lib.sh`.

**`build.sh`**
```bash
. $(dirname $0)/lib.sh
C=2d41ab4e47ed1f4309050291ea9d2e4aa84cd3e2; T=1633c0255d14282d773e7a6d945666b4c07eebb5
# reuse this session's worktree if WT is set; else make one (never pick an arbitrary folder under worktrees/)
WT=${WT:-$($RIG_TOOLKIT/wt-new.sh home-widgets-opacity candidate main | tail -1)}
git -C $WT fetch https://github.com/o-jcardenass/open-headunit.git feat/home-widgets-and-opacity
git -C $WT checkout --detach $C
[ "$(git -C $WT rev-parse 'HEAD^{tree}')" = $T ] || { echo TREE_MISMATCH; exit 1; }
$RIG_TOOLKIT/rig_thermal.sh wait 70
HUR_DIR=$WT MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh || { echo BUILD_FAILED; exit 1; }
cp $(ls -t $RIG_DATA/apks/com.andrerinas.headunitrevived_*.apk | head -1) $OUT/C.apk
{ echo "WT=$WT"; echo "C md5 $(md5sum < $OUT/C.apk | cut -c1-32)"
  echo "HomeSurfaceOpacityPolicy=$(unzip -p $OUT/C.apk 'classes*.dex' | strings | grep -c HomeSurfaceOpacityPolicy)"
  echo "show-home-widgets=$(unzip -p $OUT/C.apk 'classes*.dex' | strings | grep -c show-home-widgets)"; } | tee $OUT/build.txt
```

Build gate: `HomeSurfaceOpacityPolicy` MUST be 1 or more, and `show-home-widgets` MUST be 1 or more. Both are new in `2d41ab4e`, so no earlier APK carries either. A failed gate stops the round.

Identity after the install (`inst` in `lib.sh` does both):

- `$RIG_TOOLKIT/apk_identity.sh $OUT/C.apk $D_HU` MUST exit 0.
- The `ACTION_QUERY_STATE` reply MUST carry commit `2d41ab4e47ed`. A `-dirty` suffix is fine; say so.
- If `adb install -r -d` answers `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, stop and ask. Never uninstall.

## 2. What this is and why

Two requests against the new home screen.

- **Widgets switch.** One switch, `show-home-favorite-apps`, used to hide the favorite apps and the widgets together. A driver who wanted the favorites but no widgets still got the "Add widget" tile, which takes a full row. The candidate adds `show-home-widgets` for the widget row, the widget pages and the add tile. The favorites switch now hides only the favorites. Until `show-home-widgets` is first saved, its getter returns the favorites value, so an upgraded unit looks the same as before.
- **Panel opacity.** The home's panels and tiles were fully opaque, so a background image showed only in the gaps. The candidate adds `home-surface-opacity-percent` (20 to 100, default 100). It scales the alpha of the panel and tile fills in `HomeUiHelper.palette()`, and of the main card's fill in day mode, where the card wears the connection's color. Text, icons, outlines and the buttons on the card stay opaque.

| Id | What | Run | Kind |
|---|---|---|---|
| WA | Upgrade, favorites on, new key absent: both shown (control for the dumps) | W-A | GATE |
| WB | Upgrade, favorites off, new key absent: both hidden (the getter follows the favorites) | W-B | GATE |
| WC | Favorites on, widgets off: favorites shown, no add tile | W-C | GATE, the point |
| WD | Favorites off, widgets on: no favorites, add tile shown | W-D | GATE, the point |
| O1 | Opacity 100 over a magenta image: the panels hide the image (control) | O-100 | GATE |
| O2 | Opacity 40 over the same image: the image shows through the panels | O-40 | GATE, the point |
| CR | The two new Customization rows are in the screen | C-REC | RECORD |

**What a PASS looks like if the change did nothing.** On `main`, W-C would show the add tile (the favorites switch also turned the widgets on), and W-D would show no add tile (the favorites switch off also hid the widgets). On `main`, O-40 would read the same as O-100, because no code reads the key. So W-C, W-D and O-40 each tell the candidate from `main`. W-A and W-B read the same on both, so they guard the upgrade and prove the dumps can see the nodes.

Not run, with the reason:

- **The cached palette on return from Customization.** `HomeFragment` rebuilds its palette when the stored opacity changes. A rig run can only change the value with the app stopped, which starts a fresh fragment and never reaches the cache. No scripted route moves the slider (it needs a scroll, which is banned). Covered by code reading only.
- **The clamp** (a stored 5 reads as 20). `HomeSurfaceOpacityPolicyTest` covers it on the dev host.
- **Widget pages hidden by the switch.** `HomePagesPolicy.pages(widgetsShown = false)` already returns no widget page, and `HomePagesPolicyTest` covers it.
- **A real widget in the row.** Round 3 of the home redesign found that D-HU's only widget provider (VLC) draws "Couldn't add widget.", and that seeding `home-widgets` leaves host bindings behind. The add tile alone carries the signal, because the switch gates the whole row.

## 3. What is different about this round

- **The home must not be armed.** Round 3 of the home redesign found that `uiautomator dump` writes no file while the home sits on an armed or waiting step. Every run here seeds `wifi-connection-mode` 0 and no auto-connect, so the home is idle.
- **The widget row must sit under the main button on D-HU.** If the screen is too small (the "compact" home), the favorites area moves to page 2 and a page 1 dump cannot see it. Round 3 walked from `favorite_slot_2` to `home_widget_frame` on page 1, so D-HU is not compact. W-A is the check: if it shows no favorites or no add tile, W-B to W-D are INCONCLUSIVE (layout), not FAIL.
- **Launcher mode by setting, not by the home role.** `isCarLauncherActive` is true when `enable-car-launcher` is true. No run takes the HOME role, so a force-stop does not restart the app.
- **The opacity runs read pixels.** The background is a solid magenta PNG (`#FF00FF`), pushed as root into the app's files. `homeq.py` decodes the screenshot itself (no Pillow needed) and reports the share of pixels whose magenta excess, `min(R,B) - G`, is above 60, inside a node's bounds with 10% cut from every side to skip the rounded corners. No home color reaches 60: the nearest is the day WiFi color `#5B3AB0` at 33. At 40% a night panel blends to about (163, 12, 163), excess 150, and a tile over a panel to about 93.
- **Night mode is neutralised.** `home-background-night-mode` 2 (NONE) keeps the image undimmed whatever the unit's night state.
- **Animations are off on D-HU** for the round. Pre-flight saves the value and `restore.sh` puts it back.
- **The audio settings are a deliberate worst case.** Do not touch `use-aac-audio`, `audio-latency-multiplier` or `audio-queue-capacity`.

## 4. Settings keys

`seed` in `lib.sh` writes these with the app stopped, through `set_hu_settings_host.py` (D-HU is rooted), and reads them back. `restore.sh` puts the backup back at the end.

| Key | Type | `BASE` (every run) | W-A | W-B | W-C | W-D | O-100 | O-40 | Why |
|---|---|---|---|---|---|---|---|---|---|
| `onboarding-version` | int | 2 | | | | | | | no wizard |
| `rename_notice_shown_v2` | boolean | true | | | | | | | no notice |
| `aa174_notice_shown` | boolean | true | | | | | | | no notice |
| `native-driver-selection-mode` | int | 0 | | | | | | | no driver selector |
| `log-level` | int | 2 | | | | | | | INFO; every graded line is INFO or a system line |
| `connection-modes` | set | `usb,wifi,self` | | | | | | | every tile |
| `video-profile-starvation-cap` | | deleted | | | | | | | |
| `has-accepted-disclaimer` | boolean | true | | | | | | | |
| `auto-connect-last-session` | boolean | false | | | | | | | idle home |
| `auto-start-self-mode` | boolean | false | | | | | | | idle home |
| `auto-connect-single-usb` | boolean | false | | | | | | | no countdown |
| `auto-connect-delay-seconds` | int | 0 | | | | | | | no countdown |
| `wifi-connection-mode` | int | 0 | | | | | | | idle, so the home can be dumped |
| `enable-car-launcher` | boolean | true | | | | | | | launcher mode: favorites and widgets exist |
| `home-pages-hint-shown` | boolean | true | | | | | | | no hint in the way |
| `home-widget-pages` | string | deleted | | | | | | | page 1 only |
| `home-widgets` | string | deleted | | | | | | | an empty row: only the add tile |
| `show-home-favorite-apps` | boolean | | true | false | true | false | true | true | |
| `show-home-widgets` | boolean | | deleted | deleted | false | true | true | true | deleted = the upgrade case |
| `home-surface-opacity-percent` | int | | deleted | deleted | deleted | deleted | 100 | 40 | |
| `home-background-image-path` | string | | deleted | deleted | deleted | deleted | `/data/user/0/com.andrerinas.headunitrevived/files/custom_home_bg.png` | same | |
| `home-background-night-mode` | int | | | | | | 2 | 2 | NONE: no night dim |

`ACTION_LOG_MARKER` is not in `AutomationCommandPolicy.CONFIGURING` at `2d41ab4e`, so `allow-external-configuration` is not needed.

## 5. Helpers

Run every adb call in sequence, never two at once against one unit. The `logcat` capture is the only standing stream. Write the results file after every run.

**`lib.sh`**
```bash
source ~/Coding/StudioProjects/rig-toolkit/rig_devices.sh
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
MAIN=$PKG/com.andrerinas.openheadunit.main.MainActivity
SETACT=$PKG/com.andrerinas.openheadunit.main.SettingsActivity
D_HU=${D_HU:-27870808938846}; H=$D_HU
OUT=$RIG_DATA/rounds/home-widgets-opacity-round1; S=$OUT/scripts; mkdir -p $S $OUT/ui
export LC_ALL=C
[ -f $OUT/env.sh ] && . $OUT/env.sh       # HU_OWNER HU_ANIM CUST
BG=/data/user/0/$PKG/files/custom_home_bg.png
BASE="scalar:int:onboarding-version:2 scalar:boolean:rename_notice_shown_v2:true scalar:boolean:aa174_notice_shown:true
  scalar:int:native-driver-selection-mode:0 scalar:int:log-level:2 set:connection-modes:usb,wifi,self del:video-profile-starvation-cap
  scalar:boolean:has-accepted-disclaimer:true scalar:boolean:auto-connect-last-session:false scalar:boolean:auto-start-self-mode:false
  scalar:boolean:auto-connect-single-usb:false scalar:int:auto-connect-delay-seconds:0 scalar:int:wifi-connection-mode:0
  scalar:boolean:enable-car-launcher:true scalar:boolean:home-pages-hint-shown:true del:home-widget-pages del:home-widgets"
NOBG="del:home-surface-opacity-percent del:home-background-image-path"
K_WA="$BASE $NOBG scalar:boolean:show-home-favorite-apps:true del:show-home-widgets"
K_WB="$BASE $NOBG scalar:boolean:show-home-favorite-apps:false del:show-home-widgets"
K_WC="$BASE $NOBG scalar:boolean:show-home-favorite-apps:true scalar:boolean:show-home-widgets:false"
K_WD="$BASE $NOBG scalar:boolean:show-home-favorite-apps:false scalar:boolean:show-home-widgets:true"
OBG="scalar:boolean:show-home-favorite-apps:true scalar:boolean:show-home-widgets:true scalar:string:home-background-image-path:$BG scalar:int:home-background-night-mode:2"
K_O100="$BASE $OBG scalar:int:home-surface-opacity-percent:100"
K_O40="$BASE $OBG scalar:int:home-surface-opacity-percent:40"
KEYS="show-home-favorite-apps show-home-widgets home-surface-opacity-percent home-background-image-path home-background-night-mode
  enable-car-launcher wifi-connection-mode auto-connect-single-usb home-widgets home-widget-pages"

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

pull_settings() { adb -s $H shell cat /data/data/$PKG/shared_prefs/settings.xml | tr -d '\r'; }
showkeys() { local f=$1 k; for k in $KEYS; do grep -aoE "<[a-z]+ name=\"$k\"[^>]*(/>|>[^<]*</[a-z]+>)" $f || echo "$k ABSENT"; done; }
# seed <spec>...: app stopped, write, read back
seed() { adb -s $H shell am force-stop $PKG; sleep 1
  python3 -I $RIG_TOOLKIT/set_hu_settings_host.py $H "$@" >> $OUT/seed.log 2>&1
  pull_settings > $OUT/seeded.xml; showkeys $OUT/seeded.xml; }
inst() { adb -s $H install -r -d $OUT/C.apk | tail -1
  $RIG_TOOLKIT/apk_identity.sh $OUT/C.apk $H; echo "apk_identity exit $?"
  send ACTION_QUERY_STATE | grep -ao 'commit[^,}]*'; adb -s $H shell am force-stop $PKG; }
uidump() { adb -s $H shell rm -f /sdcard/ohu-ui.xml; sleep 0.3
  adb -s $H shell uiautomator dump /sdcard/ohu-ui.xml >/dev/null 2>&1; sleep 0.3
  adb -s $H pull /sdcard/ohu-ui.xml $OUT/ui/$1.xml >/dev/null 2>&1; sleep 0.3
  adb -s $H shell screencap -p /sdcard/ohu-ui.png; sleep 0.3; adb -s $H pull /sdcard/ohu-ui.png $OUT/ui/$1.png >/dev/null 2>&1
  [ -s $OUT/ui/$1.xml ] || { adb -s $H shell uiautomator dump /sdcard/ohu-ui.xml >/dev/null 2>&1; sleep 0.3
    adb -s $H pull /sdcard/ohu-ui.xml $OUT/ui/$1.xml >/dev/null 2>&1; }
  [ -s $OUT/ui/$1.xml ] || echo "$1 DUMP_FAILED" | tee -a $OUT/marks.txt >&2; }
hq() { python3 -I $S/homeq.py "$@"; }
# one <run> <spec>: stop, seed, mark, launch, wait for the home, dump; prints the counts
one() { local R=$1; shift; stopapp; seed "$@" | tee $OUT/$R-seed.txt
  mark "$R-start"; launch
  waitfor $CAPF "$R-start" 30 'HomeFragment: onResume. isConnected=' && echo "$R resumed" || echo "$R NO_RESUME"
  sleep 5; uidump $R
  { echo "card=$(hq count $OUT/ui/$R.xml home_primary_card)"
    echo "fav=$(hq count $OUT/ui/$R.xml favorite_slot_1 favorite_slot_2 favorite_slot_3 favorite_slot_4)"
    echo "add=$(hq count $OUT/ui/$R.xml widget_add_tile)"
    echo "bar=$(hq count $OUT/ui/$R.xml home_driver_bar)"; } | tee $OUT/$R-counts.txt
  mark "$R-end"; echo "fatal=$(fatal $CAPF $R-start $R-end)" | tee -a $OUT/$R-counts.txt
  stopapp; pull_settings > $OUT/$R-after.xml; showkeys $OUT/$R-after.xml | tee $OUT/$R-after-keys.txt; }
```

**`homeq.py`**
```python
# count <xml> <id>...: nodes whose resource-id ends ":id/<id>" with on-screen, non-empty bounds
# magenta <png> <xml> <id>: share of pixels with min(R,B) - G above 60 inside the first such node, 10% cut from each side
# magenta <png> - screen: the same over the whole screen
import re, struct, sys, zlib

def nodes(xml):
    s = open(xml, encoding="utf-8", errors="replace").read()
    out = []
    for m in re.finditer(r'<node [^>]*>', s):
        t = m.group(0)
        rid = re.search(r'resource-id="([^"]*)"', t); b = re.search(r'bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"', t)
        if b: out.append((rid.group(1) if rid else "", tuple(int(x) for x in b.groups())))
    return out

def onscreen(ns):
    W = max(r for _, (l, t, r, b) in ns) if ns else 0
    return [(i, bx) for i, bx in ns if bx[2] > bx[0] and bx[3] > bx[1] and bx[0] < W and bx[2] > 0]

def png(path):
    d = open(path, "rb").read(); assert d[:8] == b"\x89PNG\r\n\x1a\n"
    p, idat, w = 8, b"", 0
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

def share(pngf, box):
    w, h, bpp, rows = png(pngf); l, t, r, b = box
    dx, dy = (r - l) // 10, (b - t) // 10; l, r, t, b = max(0, l + dx), min(w, r - dx), max(0, t + dy), min(h, b - dy)
    n = hit = 0
    for y in range(t, b, 2):
        row = rows[y]
        for x in range(l, r, 2):
            R, G, B = row[x * bpp], row[x * bpp + 1], row[x * bpp + 2]; n += 1
            if min(R, B) - G > 60: hit += 1
    return hit, n

cmd = sys.argv[1]
if cmd == "count":
    ns = onscreen(nodes(sys.argv[2])); ids = sys.argv[3:]
    print(sum(1 for i, _ in ns if any(i.endswith(":id/" + x) for x in ids)))
elif cmd == "magenta":
    if sys.argv[3] == "-":
        w, h, _, _ = png(sys.argv[2]); box = (0, 0, w, h)
    else:
        m = [bx for i, bx in onscreen(nodes(sys.argv[3])) if i.endswith(":id/" + sys.argv[4])]
        if not m: print("NO_NODE"); sys.exit(2)
        box = m[0]
    hit, n = share(sys.argv[2], box); print("share=%.3f hit=%d n=%d box=%s" % (hit / max(n, 1), hit, n, list(box)))
```

**`mkpng.py`**
```python
# mkpng.py <out>: a 64x64 solid #FF00FF PNG
import struct, sys, zlib
def chunk(k, d): return struct.pack(">I", len(d)) + k + d + struct.pack(">I", zlib.crc32(k + d) & 0xffffffff)
raw = b"".join(b"\x00" + b"\xff\x00\xff" * 64 for _ in range(64))
open(sys.argv[1], "wb").write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 64, 64, 8, 2, 0, 0, 0))
    + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b""))
```

## 6. Pre-flight and install

Run `build.sh` in the background and `preflight.sh` in the foreground beside it: pre-flight is adb only. Run `install.sh` once the build gate passes.

**`preflight.sh`**
```bash
. $(dirname $0)/lib.sh
exec > >(tee -a $OUT/preflight.txt) 2>&1
echo "==== preflight $(date)"
$RIG_TOOLKIT/rig_preflight.sh D_HU:wifi; $RIG_TOOLKIT/rig_cleanup.sh list
adb devices | grep -q "^$D_HU" || { echo "D-HU NOT ON ADB"; exit 1; }      # MUST be on adb
adb -s $H shell id                                                          # MUST show uid=0(root)
echo "sdk $(adb -s $H shell getprop ro.build.version.sdk | tr -d '\r') locale $(adb -s $H shell getprop persist.sys.locale | tr -d '\r')"
adb -s $H shell wm size; adb -s $H shell wm density
echo "home role $(adb -s $H shell cmd role get-role-holders android.app.role.HOME | tr -d '\r')"   # MUST NOT be $PKG; else stop and ask
stopapp
[ -f $OUT/backup.xml ] || pull_settings > $OUT/backup.xml
wc -c < $OUT/backup.xml                                                     # MUST be over 0
showkeys $OUT/backup.xml
HU_OWNER=$(adb -s $H shell ls -ld /data/data/$PKG/shared_prefs | awk '{print $3":"$4}' | tr -d '\r'); echo "HU_OWNER=$HU_OWNER"
echo "bg file before: $(adb -s $H shell ls -l /data/data/$PKG/files/custom_home_bg.png 2>&1 | tr -d '\r')"   # record: restore puts it back
[ -f $OUT/bg-before.png ] || adb -s $H pull /data/data/$PKG/files/custom_home_bg.png $OUT/bg-before.png >/dev/null 2>&1
HU_ANIM=$(grep -ao 'HU_ANIM=[^ ;]*' $OUT/env.sh 2>/dev/null | cut -d= -f2)
[ -n "$HU_ANIM" ] || HU_ANIM=$(adb -s $H shell settings get global animator_duration_scale | tr -d '\r')
adb -s $H shell settings put global animator_duration_scale 0
python3 -I $S/mkpng.py $OUT/magenta.png; ls -l $OUT/magenta.png
echo "HU_OWNER=$HU_OWNER; HU_ANIM=$HU_ANIM" > $OUT/env.sh; cat $OUT/env.sh
```

Pre-flight gates: each line marked MUST. Put the whole `preflight.txt` in Setup notes, with `wm size`, `wm density` and the locale.

**`install.sh`**
```bash
. $(dirname $0)/lib.sh
exec > >(tee $OUT/install.txt) 2>&1
inst
AAPT=$(ls ${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}/build-tools/*/aapt2 2>/dev/null | sort -V | tail -1)
HEX=$($AAPT dump resources $OUT/C.apk 2>/dev/null | grep -a 'id/customizationFragment' | grep -aoE '0x7f[0-9a-f]{6}' | head -1)
[ -n "$HEX" ] && echo "CUST=$((HEX))" >> $OUT/env.sh; echo "aapt2=$AAPT customizationFragment=$HEX"; cat $OUT/env.sh
```

D-HU MUST show `apk_identity exit 0` and commit `2d41ab4e47ed`. `env.sh` SHOULD hold `CUST=<decimal>`; without it C-REC is UNTESTABLE and the round goes on.

## 7. The deciding lines

Each was checked with `grep -F -r` against `app/src` at `2d41ab4e`. All are in the head unit capture `$OUT/hu.txt`, in the window between the run's `<run>-start` and `<run>-end` markers.

| String | Source | Level | Used for |
|---|---|---|---|
| `AutomationMarker: ` | `AutomationReceiver` | WARN | the run window |
| `HomeFragment: onResume. isConnected=` | `HomeFragment.onResume` | INFO | the home was shown in this run |
| `Failed to load custom background: ` | `BaseActivity.applyWindowBackground` | ERROR | O runs: the image did not load |
| `FATAL EXCEPTION` with `Process: com.andrerinas.headunitrevived` | system | | no crash |

The other evidence is the dump and the screenshot of each run, read by `homeq.py`:

| Node id (`<pkg>:id/<id>`) | Layout | Means |
|---|---|---|
| `home_primary_card` | `view_home_primary_card.xml` | the home is on screen and the dump worked |
| `favorite_slot_1` to `favorite_slot_4` | `view_home_favorites.xml` | the favorite slots (shown even when empty) |
| `widget_add_tile` | `item_home_widget_add.xml` | the "Add widget" tile |
| `home_driver_bar` | `fragment_home.xml` | the bar whose pixels the O runs read |

## 8. Runs

All runs share one capture and run in this order. Before the first run, gate on the host temperature. Each run is about 1 min.

**`runs.sh`**
```bash
. $(dirname $0)/lib.sh
exec > >(tee -a $OUT/runs.txt) 2>&1
th_gate runs || exit 1
CAPF=$OUT/hu.txt; stopapp; cap $CAPF
# W-A to W-D: no background image (NOBG); the home as it ships
one W-A $K_WA
one W-B $K_WB
one W-C $K_WC
one W-D $K_WD
# O runs: push the magenta background as root, then two opacity arms
adb -s $H push $OUT/magenta.png /data/local/tmp/ohu-bg.png >/dev/null
adb -s $H shell mkdir -p /data/data/$PKG/files
adb -s $H shell cp /data/local/tmp/ohu-bg.png /data/data/$PKG/files/custom_home_bg.png
adb -s $H shell chown $HU_OWNER /data/data/$PKG/files/custom_home_bg.png; adb -s $H shell chmod 600 /data/data/$PKG/files/custom_home_bg.png
adb -s $H shell ls -l /data/data/$PKG/files/custom_home_bg.png
for R in O-100 O-40; do
  K=K_O${R#O-}; one $R ${!K}
  { echo "screen $(hq magenta $OUT/ui/$R.png - screen)"
    echo "barpx $(hq magenta $OUT/ui/$R.png $OUT/ui/$R.xml home_driver_bar)"
    echo "cardpx $(hq magenta $OUT/ui/$R.png $OUT/ui/$R.xml home_primary_card)"
    echo "bgfail=$(cnt $CAPF $R-start $R-end 'Failed to load custom background: ')"; } | tee -a $OUT/$R-counts.txt
done
# C-REC: the Customization screen, opened as root (SettingsActivity is not exported)
stopapp; mark C-REC-start
if [ -n "$CUST" ]; then
  adb -s $H shell am start -n $SETACT --ei extra_destination $CUST | tee $OUT/C-REC-am.txt; sleep 5
  adb -s $H shell dumpsys window | grep -a -m1 mCurrentFocus | tee -a $OUT/C-REC-am.txt
  uidump C-REC
  { for i in switch_show_favorite_apps switch_show_widgets slider_surface_opacity; do
      echo "$i=$(grep -ac ":id/$i\"" $OUT/ui/C-REC.xml)"; done
    echo "text_widgets=$(grep -acF 'text="Widgets on the home screen"' $OUT/ui/C-REC.xml)"
    echo "text_opacity=$(grep -acF 'text="Panel opacity"' $OUT/ui/C-REC.xml)"; } | tee $OUT/C-REC-counts.txt
  adb -s $H shell input keyevent 4; sleep 1
else echo "C-REC NO_CUST" | tee $OUT/C-REC-counts.txt; fi
mark C-REC-end; echo "fatal=$(fatal $CAPF C-REC-start C-REC-end)" | tee -a $OUT/C-REC-counts.txt
stopapp; uncap $CAPF; th_end
```

Grade every run from `$OUT/<run>-counts.txt` and `$OUT/<run>-after-keys.txt`. In every run, `fatal` MUST be 0 and the run's line MUST read `<run> resumed`. A `DUMP_FAILED` or `card=0` means the dump did not see the home: re-run that run once with `one <run> $K_<run>` (a new capture is fine, say so), and a second failure makes it UNTESTABLE.

### W-A: upgrade, favorites on (control)

- PASS: `card` is 1 or more, `fav` is 1 or more, `add` is 1, and `show-home-widgets ABSENT` in `W-A-after-keys.txt` (the getter writes nothing).
- If `fav` is 0 or `add` is 0 here: the layout on D-HU puts the favorites or the row out of page 1. Mark W-A to W-D INCONCLUSIVE (layout), attach `W-A.png`, and go on to the O runs.

### W-B: upgrade, favorites off

- PASS: `card` 1 or more, `fav` 0, `add` 0, and `show-home-widgets ABSENT` in `W-B-after-keys.txt`.

### W-C: favorites on, widgets off (the point)

- PASS: `card` 1 or more, `fav` 1 or more, `add` 0, and `show-home-widgets ... value="false"` in `W-C-after-keys.txt`.
- On `main` this would read `add=1`.

### W-D: favorites off, widgets on (the point)

- PASS: `card` 1 or more, `fav` 0, `add` 1, and `show-home-widgets ... value="true"` in `W-D-after-keys.txt`.
- On `main` this would read `add=0`.

### O-100: opacity 100 over the magenta image (control)

- Reach: `bgfail=0` and `screen share` is 0.10 or more (the image is on screen around the panels). If either fails, O-100 and O-40 are INCONCLUSIVE (no image), with the reason.
- PASS: `barpx share` below 0.05 and `cardpx share` below 0.05.

### O-40: opacity 40 over the same image (the point)

- Reach: as O-100.
- PASS: `barpx share` 0.30 or more and at least 0.25 above O-100's, and `cardpx share` 0.20 or more and at least 0.15 above O-100's. The card's bar is lower because its action button stays opaque.
- On `main` both readings would equal O-100's.
- Record both screenshots (`O-100.png`, `O-40.png`) in the zip, and say in one line whether the bar's text and icons can still be read in `O-40.png`. That line is RECORD, not part of the verdict.

### C-REC: the Customization rows (RECORD)

- Record `C-REC-am.txt`, the focus line and every count. `switch_show_widgets` and `slider_surface_opacity` each 1 means the row is in the screen's tree.
- A count of 0 is not a FAIL: the rows sit below the first screen, and a dump may leave off-screen rows out. No scroll is allowed. Attach `C-REC.png`.

Stop rule: none beyond the per-run re-run above. Every run is run once.

## 9. Restore

**`restore.sh`**
```bash
. $(dirname $0)/lib.sh
exec > >(tee $OUT/restore.txt) 2>&1
stopapp
adb -s $H push $OUT/backup.xml /data/local/tmp/ohu-hwo.xml >/dev/null
adb -s $H shell cp /data/local/tmp/ohu-hwo.xml /data/data/$PKG/shared_prefs/settings.xml
adb -s $H shell chown $HU_OWNER /data/data/$PKG/shared_prefs/settings.xml; adb -s $H shell chmod 660 /data/data/$PKG/shared_prefs/settings.xml
pull_settings | diff -B - $OUT/backup.xml >/dev/null && echo SETTINGS_BACK || echo SETTINGS_DIFFER
if [ -s $OUT/bg-before.png ]; then adb -s $H push $OUT/bg-before.png /data/local/tmp/ohu-bg.png >/dev/null
  adb -s $H shell cp /data/local/tmp/ohu-bg.png /data/data/$PKG/files/custom_home_bg.png
  adb -s $H shell chown $HU_OWNER /data/data/$PKG/files/custom_home_bg.png; echo BG_BACK
else adb -s $H shell rm -f /data/data/$PKG/files/custom_home_bg.png; echo BG_REMOVED; fi
adb -s $H shell rm -f /data/local/tmp/ohu-hwo.xml /data/local/tmp/ohu-bg.png /sdcard/ohu-ui.xml /sdcard/ohu-ui.png
adb -s $H shell settings put global animator_duration_scale ${HU_ANIM:-1.0}
echo "anim $(adb -s $H shell settings get global animator_duration_scale | tr -d '\r')"
$RIG_TOOLKIT/rig_preflight.sh D_HU:wifi
```

The candidate APK stays installed: the next round on D-HU installs its own build. `SETTINGS_BACK` MUST print. Record `BG_BACK` or `BG_REMOVED` and the animator scale.

## 10. Do not re-run

Nothing: this is the thread's first round.

## 11. Report back

1. W-C and W-D: the `fav` and `add` counts. Together they decide whether the switches are separate on hardware.
2. O-100 and O-40: the `barpx` and `cardpx` shares. They decide whether the opacity reaches the screen.
3. W-A and W-B: whether `show-home-widgets` stayed `ABSENT` after the run, which decides the upgrade case.
