# pr-1119-home-redesign, round 2 brief

Published name: `pr-1119-home-redesign-round2-brief.md`. Results go in `pr-1119-home-redesign-round2-results.md`.
Evidence: the release `rig-evidence-pr-1119-home-redesign` exists from round 1. Upload `pr-1119-home-redesign-round2-captures.zip` to it.

**This round is short on purpose.** It retests only the fixes for round 1's FAILs and the review's findings. It reuses round 1's run shapes. It re-runs nothing that passed.

Units: D-HU (Native AA head unit, launcher mode), D-HP (head unit, Headunit Server mode), D-POCO (as a head unit, launcher mode), D-MOTO (the phone for D-HU). D-SAM only for the optional last run.

Queue line: `pr-1119-home-redesign-round2-brief.md` (one APK, `0d333b19` on `fork/review/pr-1119-r2`, the home redesign PR head with its 16 fix commits and main `e9377ff9` merged; units D-HU, D-HP, D-POCO, D-MOTO, and D-SAM for the optional SAM-HS; HU-F1 is the point).

Pairing: none. The round uses D-POCO as a head unit and as the server, so no other round can share it.

Cabling: D-HU, D-HP, D-POCO and D-MOTO on adb, as in round 1. D-SAM only if SAM-HS runs; if the five do not fit, unplug D-HP before SAM-HS.

**Hand steps.** Each is outside this app.

1. Pre-flight, only if D-MOTO shows a lock screen: unlock it once.
2. SAM-HS only (optional): on D-POCO, Android Auto developer settings, "Stop head unit server", then "Start head unit server". No adb route exists: the screen is not exported and D-POCO has no root.
3. SAM-HS only, if its pre-flight finds no `:5277` listener: turn "Start head unit server" on in the same screen.

**Injected gestures.** The cap is 5 per run. Each gesture resolves its target in a fresh `uiautomator dump`: by resource id on D-HU and D-POCO, by text or content description on D-HP (API 17 has no resource ids). It asserts exactly one match and logs the coordinates (`gest` in section 5). A target that does not resolve voids that condition. No run searches, scrolls or types.

| Run | Gestures | What they are |
|---|---|---|
| HU-B6 | 1 (2) | long press on the title; a tap on "Got it" only if the hint is still up |
| HU-W1 | 4 (5) | the row's "+" and a picker entry, twice; a tap on the system bind button only if it asks |
| HU-B4 | 0 | |
| HU-F1 | 3 | swipe to page 2, swipe to page 2 again, the pill's X |
| HU-W8 | 0 | |
| HP-W12 | 0 | six D-pad key events at most |
| HP-F1 | 1 | the pill's X (or the card's Cancel) |
| POCO-B6 | 1 (2) | as HU-B6 |
| POCO-W1 | 3 (5) | swipe to the favorites page, "+", a picker entry; a bind tap or one more swipe if needed |
| SAM-HS | 0 | |

**Time budget.** Build 20 min, with pre-flight (8 min) run beside it. Installs 4 min. HU-L 7 min, HU-C 8 min, HP 7 min, POCO-L 5 min. Thermal waits about 3 min. Restore 5 min. **About 1 h in all, 27 min of it runs.** The optional SAM-HS adds about 12 min with its hand step. Skip it if the round is late.

## 1. Build and baseline

One APK. No baseline build: round 1 measured the head at `11a2c37f`, and this candidate is that head plus the fixes. No history was rewritten.

| Arm | Ref | Commit | Tree |
|---|---|---|---|
| C (candidate) | `fork/review/pr-1119-r2` | `0d333b19bef3349313a3c0d233256a408317d06c` | `3135b1193414ee3ed09f741b126170e0bde7602b` |

C is the PR head itself: the author's 16 fix commits on `11a2c37f`, then a merge of upstream main `e9377ff9`. The dev host ran the JVM suite at `0d333b19`: 3650 tests, 0 failures. Do not run the unit tests on the rig. No other queued round shares this base.

Work from `~/Coding/StudioProjects`. Save every script block under `$OUT/scripts/` with `python3 -I $RIG_TOOLKIT/extract_brief_blocks.py <this brief> $OUT/scripts`. Each block name is bold and in backticks, on the line right above its fence. Run every script with `bash $OUT/scripts/<name>.sh`. Only the scripts source `lib.sh`.

**`build.sh`**
```bash
. $(dirname $0)/lib.sh
C=0d333b19bef3349313a3c0d233256a408317d06c; T=3135b1193414ee3ed09f741b126170e0bde7602b
# reuse this session's worktree if WT is set; else make one (never pick an arbitrary folder under worktrees/)
WT=${WT:-$($RIG_TOOLKIT/wt-new.sh pr-1119-home-redesign candidate main | tail -1)}
git -C $WT fetch fork review/pr-1119-r2
git -C $WT checkout --detach $C
[ "$(git -C $WT rev-parse 'HEAD^{tree}')" = $T ] || { echo TREE_MISMATCH; exit 1; }
$RIG_TOOLKIT/rig_thermal.sh wait 70
HUR_DIR=$WT MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh || { echo BUILD_FAILED; exit 1; }
cp $(ls -t $RIG_DATA/apks/com.andrerinas.headunitrevived_*.apk | head -1) $OUT/C.apk
{ echo "WT=$WT"; echo "C md5 $(md5sum < $OUT/C.apk | cut -c1-32)"
  echo "ProjectionExitMenuPolicy=$(unzip -p $OUT/C.apk 'classes*.dex' | strings | grep -c ProjectionExitMenuPolicy)"
  echo "HomeCardFitPolicy=$(unzip -p $OUT/C.apk 'classes*.dex' | strings | grep -c HomeCardFitPolicy)"; } | tee $OUT/build.txt
```

Build gate: `ProjectionExitMenuPolicy` MUST be 1 or more, and `HomeCardFitPolicy` MUST be 1 or more. Both classes are new in the fix commits (`6a47ecf9`, `dea62739`) and absent at `11a2c37f`, so the round 1 APK has 0 of each. A failed gate stops the round.

Identity after each install (`inst` in `lib.sh` does both):

- `$RIG_TOOLKIT/apk_identity.sh $OUT/C.apk <serial>` MUST exit 0.
- The `ACTION_QUERY_STATE` reply MUST carry commit `0d333b19bef3`. A `-dirty` suffix is fine; say so.
- If `adb install -r -d` answers `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, stop and ask. Never uninstall.

## 2. What this is and why

PR #1119 replaces the home screen. Round 1 (`11a2c37f`) ended at 32 PASS and 5 FAIL after the regrade. The author then pushed 16 fix commits. The review of the update found no blocking issue in code. This round checks each fix on the unit that failed it.

| Id | Round 1 finding | Round 1 evidence | The fix | Run |
|---|---|---|---|---|
| B1 | No Cancel and no pill on page 2 while `WAKING_PHONE` was up | HU-F1: `home_primary_cancel` 0 and `auto_connect_pill` 0 in the page 2 dump | `homeTellsBringUp` is true only while the card is on screen with a step (`dea62739`) | HU-F1 |
| B1 | No Cancel and no pill while `SEARCHING` was up behind a USB card | HP-F1: step `SEARCHING (not shown, the home's main button tells it)`, no Cancel text | same | HP-F1 arm a |
| B1 | The card flipped between "Looking for your phone" with Cancel and "Searching for your phone…" with Search again | D-SAM loop after round 1 | `statusOf` gives WAITING when the stage is on WiFi | HP-F1 arm b |
| W16 | The step line was cut on page 1 after HOME | HU-C1: `the main button is 373px tall, hiding 3 line(s)`, no `home_primary_subtitle` | `HomeCardFitPolicy` drops the kicker and title before the step | HU-F1 |
| B2 | The row widget asked for density times its width | HU-W1: frame 495 px for a 330 px minimum at d=1.5 | `minWidthPx` reads the provider's px as they are (`26fda6ac`) | HU-W1, POCO-W1 |
| B5 | No set-up screen for a widget that declares one | code review | configure after bind; a cancel releases the id (`26fda6ac`) | HU-W1, if a set-up widget comes up |
| B4 | Losing the home role deleted every shortcut favorite | code review | a slot whose shortcuts cannot be read is kept (`8ac3a5e2`) | HU-B4 |
| B6 | BACK did not close the pages hint, and the hint took the next touch | HU-L1 and POCO-L1: `keyCode=4` delivered, hint closed only by `LEFT` or `GOT_IT` | the activity's Back callback registers before `setContentView` (`b760a49e`, `2ae94459`) | HU-B6, POCO-B6 |
| W8 | The launcher-mode exit menu lost "End session, stay ready" | HU-C2b: short menu had Stop and Background only | `ProjectionExitMenuPolicy` keeps `END_STAY_ARMED` (`6a47ecf9`) | HU-W8 |
| W12 | No D-pad focus on the home below API 21 | code review | the card takes focus and shows a pressed look when focused (`b760a49e`) | HP-W12 |

SAM-HS (optional) asks one more question. Round 1's D-SAM looped against D-POCO's server. Main's `68c2bbb5` (one version request over TCP) is now merged in. Does D-SAM connect cold and stay connected across warm cycles?

## 3. What is different about this round

- **The pages hint does not need the home role.** `HomePagesHintPolicy` asks `isCarLauncherActive`, which is `enable-car-launcher` alone. The B6 runs still take the role, as round 1 did, because the defect was in the order of Back callbacks when this app is the home app.
- **HU-B4 uses a shortcut id that does not exist.** That is enough: without the home role, `LauncherShortcuts.forPackage` returns null for every package, so the slot is UNAVAILABLE whatever the id. A second slot names a package that is not installed. The app frees that slot and writes the change, which proves the app's own writes reach the file. If the fix did nothing, the shortcut slot would go too.
- **HU-W1 measures the first widget it can place, whatever the picker lists.** The picker list can scroll between the dump and the tap (round 1 HU-W1). So the run reads the placed widget's provider and its minimum from `dumpsys appwidget`, not from the picker label. `wsize.py` decodes the provider's `min=` value (round 1: a TypedValue dimension, `value>>8` dp, unit 1 = dip).
- **B5 is opportunistic.** Pre-flight lists the exported set-up activities on D-HU (`cmd package query-activities ... APPWIDGET_CONFIGURE`). HU-W1 picks the first two picker entries. If one of them opens a set-up screen, BACK cancels it and the run grades B5. If neither does, B5 is UNTESTABLE. A set-up activity that is not exported goes through the host (`set up through the host`); that is the review's N1, and it is not graded here.
- **What a B2 PASS looks like if the fix did nothing.** The frame width equals `max(minimum px, add tile px)` with the fix, and the minimum times the density without it. If a widget's minimum is narrower than the "+" tile, both give the same width. `wsize.py` prints `discriminating=0` then, and B2 is INCONCLUSIVE for that widget.
- **Widget size in dp (B3) has no instrument.** Round 1 found no options in `dumpsys appwidget`. It is not graded.
- **HP-F1 needs a search that finds nothing.** D-POCO's WiFi is held off for the HP run (`hold_on`), so D-HP's discovery keeps searching. `last-connection-type` picks the main button: `usb` in arm a, `wifi` in arm b.
- **HP-W12 grades focus, not the look of it.** A dump shows `focused="true"`. Whether the focused card looks pressed is for the eye: the run keeps a screenshot after each key, and the result says what the eye saw, as a record.
- **The D-HU wake list holds D-MOTO only** (round 1 erratum: `K_CONN` set no wake list). D-POCO's Bluetooth is off and its WiFi is held off during HU-C, so D-HU's persistent group cannot take it.
- **On mode 3 the launch arms the stack.** HU-C sends `ACTION_START_WIRELESS_SCAN` only to lift the pill's X, minutes after the launch.
- **While this app holds the home role, a force-stop starts it again.** The helpers give the role back (`role_off`) before every force-stop and settings write.
- **Animations are off on D-HU, D-POCO and D-HP** for the round. Endless animations stop `uiautomator dump`. Pre-flight saves each value.
- **The audio settings are a deliberate worst case.** Do not touch `use-aac-audio`, `audio-latency-multiplier` or `audio-queue-capacity`.
- **Clocks drift** (round 1: D-HU read June 2026, D-SAM 21 s behind). Every window is by marker text. Never compare times across two units.
- **A phone leaves the link by `svc`**, not airplane mode. D-POCO's WiFi turns itself back on, so a 3 s loop holds it off.

**Round 1 errata, fixed here:**

| Erratum | Fix in this brief |
|---|---|
| `su -c` on D-HU (root adbd, no `su`) | plain `adb shell cat` and `cp` on D-HU |
| start marker after the launch; taps before their markers | every `mark` comes before the action it labels |
| the extractor did not match bold names without backticks | names are bold and in backticks |
| `hu_cblock.sh` was a script called as a function | every block is a script run with `bash`; only `lib.sh` is sourced |
| the wake list was not set, so D-MOTO was never poked | `K_HUC` writes `native-poke-bt-macs` = D-MOTO's MAC |
| phone string `ByeByeRequest` absent on Gearhead 18.0.664204 | the phone line is `PROJECTION_ENDED_BYEBYE_BY_CAR` |
| the picker scrolled between the dump and the tap | the widget is named from `dumpsys appwidget` after it is placed |
| appwidget `min=` read as px; density assumed 320 | `wsize.py` decodes the dimension and reads the density from `wm density` |
| the swipe at the pager centre on the favorites page hit the widget row | POCO-W1 swipes from page 1, where the card is |
| `uiq.py` text mode compared label boxes | text and content description select nodes; no layout grading this round |
| marker labels that are prefixes of others | `seg` matches a label only at the end of the line |

## 4. Settings keys

`seed` in `lib.sh` writes these with the app stopped: `set_hu_settings_host.py` on D-HU (rooted), `set_prefs_runas_host.py` on D-POCO, D-HP and D-SAM. It reads the keys back with `showkeys.py`. Each run writes onto the unit's current file; `restore.sh` puts every backup back at the end. `idels` adds a `del:` for every key whose name starts with `connection-issue-`, so no banner holds the pages hint back.

| Key | Type | `BASE` (every run) | `K_LAUNCH` (HU-L, POCO-L) | `K_HUC` (HU-C) | `K_HP*` (HP) | Why |
|---|---|---|---|---|---|---|
| `onboarding-version` | int | 2 | | | | no wizard (`CURRENT_ONBOARDING_VERSION` is 2) |
| `rename_notice_shown_v2` | boolean | true | | | | no notice |
| `aa174_notice_shown` | boolean | true | | | | no notice |
| `native-driver-selection-mode` | int | 0 | | | | no driver selector |
| `log-level` | int | 2 | | | | INFO; every graded line is INFO or WARN |
| `auto-connect-last-session` | boolean | false | | | | no connection the run did not ask for |
| `auto-connect-single-usb` | boolean | false | | | | same |
| `connection-modes` | set | `usb,wifi,self` | | | | every tile shown |
| `video-profile-starvation-cap` | | deleted | | | | pre-flight records it first |
| `wifi-connection-mode` | int | | 0 | 3 | 0 (`K_HPIDLE`), 1 (`K_HPUSB`, `K_HPWIFI`) | idle home; Native; Headunit Server |
| `enable-car-launcher` | boolean | | true | true | false | launcher mode; home mode on D-HP |
| `show-home-favorite-apps` | boolean | | true | | | the favorites row and the widget row |
| `home-pages-hint-shown` | boolean | | deleted | true | | the hint shows once; HU-C must not show it |
| `home-widget-pages` | string | | deleted | `p` | | none; one empty added page, so page 2 exists |
| `home-widgets` | string | | deleted | deleted | | an empty widget row |
| `home-favorite-apps` | string | | | deleted | | HU-B4 writes its own value |
| `screen-orientation` | int | | 4 on D-POCO only | | | round 1's portrait case |
| `native-poke-all-paired` | boolean | | | true | | |
| `native-poke-bt-macs` | set | | | `A0:46:5A:97:E4:95` (D-MOTO) | | the wake list |
| `native-ap-transport` | int | | | 0 | | WiFi Direct, so the exit menu offers the stay ready stop |
| `external-bt-zbt-transport` | boolean | | | false | | |
| `last-connection-type` | string | | | | `usb` (`K_HPUSB`), `wifi` (`K_HPWIFI`) | which connection the main button stands for |

HU-B4 writes one more key by itself, with `setxml.py` and a root copy (the value holds a `|`):

| Key | Type | Value |
|---|---|---|
| `home-favorite-apps` | string | `com.android.settings,com.android.settings\|rig_b4_probe,com.rig.b4.absent,` |

The format is `FavoriteAppsPolicy` at `0d333b19`: four slots joined by `,`; a shortcut is `package|shortcutId`. (The `\|` above is table escaping; the value has a plain `|`.)

`ACTION_LOG_MARKER` is not in `AutomationCommandPolicy.CONFIGURING` at `0d333b19`, so `allow-external-configuration` is not needed.

## 5. Helpers

Run every adb call in sequence, never two at once against one unit. The `hold_on` loop and the `logcat` captures are the only standing streams. Write the results file after every run.

**`lib.sh`**
```bash
source ~/Coding/StudioProjects/rig-toolkit/rig_devices.sh
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
MAIN=$PKG/com.andrerinas.openheadunit.main.MainActivity
D_HU=${D_HU:-27870808938846}; D_POCO=${D_POCO:-4f4027e9}; D_MOTO=${D_MOTO:-ZY22GC3BM4}
D_SAM=${D_SAM:-30041c35642d2200}; D_HP=${D_HP:-CNU350BGBJ}; P=$D_POCO
OUT=$RIG_DATA/rounds/pr-1119-home-redesign-round2; S=$OUT/scripts; mkdir -p $S $OUT/ui
export LC_ALL=C
[ -f $OUT/env.sh ] && . $OUT/env.sh       # HU_OWNER HU_DENS POCO_DENS: every preflight.sh rewrites it
[ -f $OUT/orig.sh ] && . $OUT/orig.sh     # opening radios and animation scales: the first preflight.sh only
PHONES=${PHONES-}                         # phones that get the RIGMARK copy of each marker
HOMELINE="(not shown, the home's main button tells it)"
STEP="MainActivity: status pill step: "
BASE="scalar:int:onboarding-version:2 scalar:boolean:rename_notice_shown_v2:true scalar:boolean:aa174_notice_shown:true
  scalar:int:native-driver-selection-mode:0 scalar:int:log-level:2 scalar:boolean:auto-connect-last-session:false
  scalar:boolean:auto-connect-single-usb:false set:connection-modes:usb,wifi,self del:video-profile-starvation-cap"
K_LAUNCH="$BASE scalar:int:wifi-connection-mode:0 scalar:boolean:enable-car-launcher:true scalar:boolean:show-home-favorite-apps:true
  del:home-pages-hint-shown del:home-widget-pages del:home-widgets"
K_HUC="$BASE scalar:int:wifi-connection-mode:3 scalar:boolean:enable-car-launcher:true scalar:boolean:native-poke-all-paired:true
  set:native-poke-bt-macs:A0:46:5A:97:E4:95 scalar:string:home-widget-pages:p scalar:boolean:home-pages-hint-shown:true
  scalar:int:native-ap-transport:0 scalar:boolean:external-bt-zbt-transport:false del:home-widgets del:home-favorite-apps"
K_HPIDLE="$BASE scalar:int:wifi-connection-mode:0 scalar:boolean:enable-car-launcher:false"
K_HPUSB="$BASE scalar:int:wifi-connection-mode:1 scalar:boolean:enable-car-launcher:false scalar:string:last-connection-type:usb"
K_HPWIFI="$BASE scalar:int:wifi-connection-mode:1 scalar:boolean:enable-car-launcher:false scalar:string:last-connection-type:wifi"

send() { local a=$1; shift; adb -s $H shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; sleep 0.3; }
mark() { local s; send ACTION_LOG_MARKER --es text "$1" >/dev/null
  for s in $PHONES; do adb -s $s shell log -t RIGMARK "$1"; sleep 0.3; done; echo "$1 $(date +%T)" >> $OUT/marks.txt; }
launch() { adb -s $H shell am start -n $MAIN >/dev/null; sleep 0.3; }
running() { { adb -s $1 shell ps; adb -s $1 shell ps -A; } 2>/dev/null | grep -ac "$PKG"; }
# headunit://exit only to a running app; sent to a stopped one it starts and arms it (rig-quirks/topics/lifecycle.md)
stopapp() { if [ "$(running $1)" -ge 1 ]; then adb -s $1 shell am start -a android.intent.action.VIEW -d headunit://exit >/dev/null; sleep 5; fi
  adb -s $1 shell am force-stop $PKG; sleep 1; }
# the HOME role (D-HU and D-POCO); pre-flight wrote the factory holder to home-role-<serial>.txt
role_on()  { adb -s $H shell cmd role add-role-holder android.app.role.HOME $PKG; sleep 1
  echo "$H on: $(adb -s $H shell cmd role get-role-holders android.app.role.HOME | tr -d '\r')" | tee -a $OUT/role.log >&2; }
role_off() { adb -s $H shell cmd role add-role-holder android.app.role.HOME "$(cat $OUT/home-role-$H.txt)"; sleep 1
  echo "$H off: $(adb -s $H shell cmd role get-role-holders android.app.role.HOME | tr -d '\r')" | tee -a $OUT/role.log >&2; }

# captures: started before the launch, always with stdbuf -oL
cap() { adb -s $2 logcat -G 16M >/dev/null 2>&1; adb -s $2 logcat -c; stdbuf -oL adb -s $2 logcat -v time > $1 & echo $! > $1.pid; sleep 1; }
# D-HP's capture can die silently: check every 60 s, restart with >> (rig-quirks/units/D-HP.md)
capwatch() { ( while :; do sleep 60; kill -0 $(cat $1.pid) 2>/dev/null || { stdbuf -oL adb -s $2 logcat -v time >> $1 & echo $! > $1.pid; echo "capture restarted $1 $(date +%T)" >> $OUT/marks.txt; }; done ) & echo $! > $1.watch; }
uncap() { [ -f $1.watch ] && kill $(cat $1.watch) 2>/dev/null; kill $(cat $1.pid) 2>/dev/null; }

# seg <file> <from> [<to>]: lines after the last <from> marker up to the next <to> marker (or the end).
# A label matches only at the end of a line, so HU-F1_x never matches HU-F1_x2.
seg() { awk -v a=": $2" -v b=": ${3:-@@none@@}" '
  function ends(s,t){ return length(s)>=length(t) && substr(s,length(s)-length(t)+1)==t }
  { l=$0; sub(/\r+$/,"",l) }
  ends(l,a){ buf=""; on=1; next }
  on && ends(l,b){ on=0 }
  on{ buf=buf $0 "\n" }
  END{ printf "%s", buf }' "$1"; }
cnt()  { seg "$1" "$2" "$3" | grep -acF -- "$4"; }
cnt2() { seg "$1" "$2" "$3" | grep -aF -- "$4" | grep -acF -- "$5"; }        # lines holding both strings
nstep() { cnt2 "$1" "$2" "$3" "$STEP" "$4"; }
bare()  { seg "$1" "$2" "$3" | grep -aF "$STEP" | grep -aF -- "$4" | grep -avF '(not shown' | grep -acvF hidden; }
rend() { seg "$1" "$2" "$3" | grep -aF 'Throughput over' | grep -aoE 'rendered=[0-9]+' | awk -F= '$2>0' | wc -l; }
ssl()  { seg "$1" "$2" "$3" | grep -aF 'SSL handshake complete' | grep -acvF 'Handshake: SSL handshake complete'; }
# waitfor <file> <from> <cap-s> <string>: 0 as soon as the string appears after <from>
waitfor() { local f=$1 m=$2 cap=$3 s=$4 t=0
  while [ $t -lt $cap ]; do [ "$(cnt $f $m '' "$s")" -ge 1 ] && return 0; sleep 1; t=$((t+1)); done; return 1; }
# waitstep <file> <from> <cap-s> <stage> [<n>]: n step lines naming the stage after <from>
waitstep() { local f=$1 m=$2 cap=$3 p=$4 n=${5:-1} t=0
  while [ $t -lt $cap ]; do [ "$(nstep $f $m '' "$p")" -ge $n ] && return 0; sleep 1; t=$((t+1)); done; return 1; }

# host thermal (rig-quirks/topics/tooling.md)
th_gate() { $RIG_TOOLKIT/rig_thermal.sh wait 75 || $RIG_TOOLKIT/rig_thermal.sh wait 75 || { echo HOST_HOT; return 1; }
  $RIG_TOOLKIT/rig_thermal.sh watch $OUT/thermal-$1.log & THPID=$!; }
th_end() { kill $THPID 2>/dev/null; wait $THPID 2>/dev/null; }

# radios
btstate() { adb -s $1 shell dumpsys bluetooth_manager | grep -a -m1 -E '^ *enabled:' | grep -aoE 'true|false'; }
ph_off() { adb -s $1 shell svc wifi disable; sleep 0.3; adb -s $1 shell svc bluetooth disable; sleep 4
  adb -s $1 shell dumpsys wifi | grep -a -m1 "Wi-Fi is"; echo "bt $(btstate $1)"; }
ph_on() { adb -s $1 shell svc wifi enable; sleep 0.3; adb -s $1 shell svc bluetooth enable; sleep 4
  adb -s $1 shell dumpsys wifi | grep -a -m1 "Wi-Fi is"; echo "bt $(btstate $1)"; }
# D-POCO's WiFi turns itself back on: hold it off every 3 s
hold_on()  { ( while :; do adb -s $P shell svc wifi disable >/dev/null 2>&1; sleep 3; done ) & echo $! > $OUT/hold.pid; }
hold_off() { kill $(cat $OUT/hold.pid 2>/dev/null) 2>/dev/null; rm -f $OUT/hold.pid; }

# settings
pull_settings() { if [ $1 = $D_HU ]; then adb -s $1 shell cat /data/data/$PKG/shared_prefs/settings.xml
  else adb -s $1 shell run-as $PKG cat shared_prefs/settings.xml; fi | tr -d '\r'; }
push_settings() { local s=$1 f=$2; adb -s $s shell am force-stop $PKG; sleep 1
  adb -s $s push $f /data/local/tmp/ohu-r2.xml >/dev/null; adb -s $s shell chmod 644 /data/local/tmp/ohu-r2.xml
  if [ $s = $D_HU ]; then adb -s $s shell cp /data/local/tmp/ohu-r2.xml /data/data/$PKG/shared_prefs/settings.xml
    adb -s $s shell chown $HU_OWNER /data/data/$PKG/shared_prefs/settings.xml; adb -s $s shell chmod 660 /data/data/$PKG/shared_prefs/settings.xml
  else adb -s $s shell run-as $PKG cp /data/local/tmp/ohu-r2.xml shared_prefs/settings.xml; fi
  pull_settings $s | diff -B - $f >/dev/null && echo SETTINGS_BACK || echo SETTINGS_DIFFER; }
# idels <serial>: a del: spec for every connection-issue-* key the unit holds now
idels() { pull_settings $1 > $OUT/cur-$1.xml; python3 -I $S/showkeys.py --dels connection-issue- $OUT/cur-$1.xml; }
# seed <serial> <key-spec>...: app stopped, write, read back
seed() { local s=$1; shift; adb -s $s shell am force-stop $PKG; sleep 1
  if [ $s = $D_HU ]; then python3 -I $RIG_TOOLKIT/set_hu_settings_host.py $s "$@"
  else python3 -I $RIG_TOOLKIT/set_prefs_runas_host.py $s "$@"; fi >> $OUT/seed.log 2>&1
  pull_settings $s > $OUT/seeded-$s.xml
  python3 -I $S/showkeys.py $OUT/seeded-$s.xml wifi-connection-mode log-level connection-modes enable-car-launcher \
    screen-orientation native-poke-all-paired native-poke-bt-macs native-ap-transport home-widget-pages home-widgets \
    home-pages-hint-shown home-favorite-apps last-connection-type video-profile-starvation-cap; }
# inst <serial>: install C and prove it (section 1)
inst() { local s=$1 H=$1; adb -s $s install -r -d $OUT/C.apk | tail -1
  $RIG_TOOLKIT/apk_identity.sh $OUT/C.apk $s; echo "apk_identity $s exit $?"
  send ACTION_QUERY_STATE | grep -ao 'commit[^,}]*'; adb -s $s shell am force-stop $PKG; }

# screen: messages go to stderr, so a function can return a word on stdout
uiq() { python3 -I $S/uiq3.py "$@"; }
uidump() { adb -s $H shell rm -f /sdcard/ohu-ui.xml; sleep 0.3
  adb -s $H shell uiautomator dump /sdcard/ohu-ui.xml >/dev/null 2>&1; sleep 0.3
  adb -s $H pull /sdcard/ohu-ui.xml $OUT/ui/$1.xml >/dev/null 2>&1; sleep 0.3
  adb -s $H shell screencap -p /sdcard/ohu-ui.png; sleep 0.3; adb -s $H pull /sdcard/ohu-ui.png $OUT/ui/$1.png >/dev/null 2>&1
  [ -s $OUT/ui/$1.xml ] || { adb -s $H shell uiautomator dump /sdcard/ohu-ui.xml >/dev/null 2>&1; sleep 0.3
    adb -s $H pull /sdcard/ohu-ui.xml $OUT/ui/$1.xml >/dev/null 2>&1; }
  [ -s $OUT/ui/$1.xml ] || echo "$1 DUMP_FAILED" | tee -a $OUT/marks.txt >&2; }
focus() { local f i; for i in 1 2 3; do f=$(adb -s $H shell dumpsys window | grep -a -m1 mCurrentFocus | tr -d '\r')
  echo "$f" | grep -q 'mCurrentFocus=null' || break; sleep 1; done; echo "$f"; }
# gest <run> <tap|long|next> <selector> <dump-label>: one gesture on the one dumped match; 5 per run at most
gest() { local R=$1 kind=$2 sel=$3 xml=$OUT/ui/$4.xml n xy x y l t r b
  n=$(cat $OUT/taps-$R 2>/dev/null || echo 0)
  [ "$n" -ge 5 ] && { echo "$R BUDGET_SPENT" | tee -a $OUT/marks.txt >&2; return 3; }
  if [ $kind = next ]; then xy=$(uiq box $xml "$sel") || { echo "$R $sel NO_TARGET $xy" | tee -a $OUT/marks.txt >&2; return 2; }
    read l t r b <<< "$xy"; x=$((l+(r-l)*8/10)); y=$(((t+b)/2))
    adb -s $H shell input swipe $x $y $((l+(r-l)*2/10)) $y 300
  else xy=$(uiq center $xml "$sel") || { echo "$R $sel NO_TARGET $xy" | tee -a $OUT/marks.txt >&2; return 2; }
    read x y <<< "$xy"
    if [ $kind = long ]; then adb -s $H shell input swipe $x $y $x $y 1200; else adb -s $H shell input tap $x $y; fi
  fi
  echo $((n+1)) > $OUT/taps-$R; echo "$R $kind $sel $x $y $(date +%T)" | tee -a $OUT/gestures.log >&2; sleep 2; }
dens() { if [ $1 = $D_HU ]; then echo $HU_DENS; else echo $POCO_DENS; fi; }

# b6 <run> <capture>: the pages hint, BACK, then one long press that must act (HU-B6, POCO-B6)
b6() { local R=$1 f=$2
  waitfor $f ${R}_start 30 'HomeFragment: showing the pages hint' || { echo "$R NO_HINT" | tee -a $OUT/marks.txt; return 1; }
  sleep 2; uidump $R-hint
  mark ${R}_back; adb -s $H shell input keyevent 4; sleep 2; uidump $R-afterback
  mark ${R}_hold; gest $R long id:home_primary_title $R-afterback; sleep 1; uidump $R-menu
  if [ "$(uiq count $OUT/ui/$R-menu.xml id:home_pages_hint_got_it)" -ge 1 ]; then      # contingency: the hint is still up
    echo "$R HINT_STILL_UP" | tee -a $OUT/marks.txt; gest $R tap id:home_pages_hint_got_it $R-menu; sleep 2; fi
  mark ${R}_menuback; adb -s $H shell input keyevent 4; sleep 2; mark ${R}_end; }

# wpick <run> <n>: the row's "+", then picker entry n; prints PLACED, CONFIGURE or VOID on stdout
wpick() { local R=$1 n=$2 f
  uidump $R-row$n; gest $R tap id:widget_add_tile $R-row$n || { echo VOID; return; }
  sleep 2; uidump $R-pick$n
  gest $R tap idnth:widget_option_label:$n $R-pick$n || { adb -s $H shell input keyevent 4; sleep 1; echo VOID; return; }
  sleep 3; f=$(focus)
  if echo "$f" | grep -q AllowBindAppWidget; then uidump $R-bind$n; gest $R tap id:button1 $R-bind$n; sleep 3; f=$(focus); fi
  echo "$f" > $OUT/$R-focus$n.txt
  if echo "$f" | grep -q "$PKG/"; then uidump $R-placed$n; adb -s $H shell dumpsys appwidget > $OUT/$R-appwidget$n.txt; echo PLACED
  else echo CONFIGURE; fi; }
# wrun <run> <compact 0|1>: place a widget from the row's "+", try for a set-up screen, measure the frame
wrun() { local R=$1 C=$2 r1 r2=none
  if [ $C = 1 ]; then uidump $R-p1; mark ${R}_swipe; gest $R next id:home_pager $R-p1; sleep 2; fi
  mark ${R}_a1; r1=$(wpick $R 1)
  if [ "$r1" = CONFIGURE ]; then mark ${R}_cfgback; adb -s $H shell input keyevent 4; sleep 3; mark ${R}_a2; r2=$(wpick $R 2)
  elif [ "$r1" = PLACED ] && [ $C = 0 ]; then mark ${R}_a2; r2=$(wpick $R 2)
    [ "$r2" = CONFIGURE ] && { mark ${R}_cfgback; adb -s $H shell input keyevent 4; sleep 3; }
  fi
  echo "$R attempt1=$r1 attempt2=$r2" | tee -a $OUT/marks.txt $OUT/$R-attempts.txt
  mark ${R}_measure; uidump $R-after
  if [ $C = 1 ] && [ "$(uiq count $OUT/ui/$R-after.xml id:home_widget_frame)" -eq 0 ]; then
    gest $R next id:home_pager $R-after; sleep 2; uidump $R-after; fi
  adb -s $H shell dumpsys appwidget > $OUT/$R-appwidget.txt; pull_settings $H > $OUT/$R-settings.xml
  local k; for k in 1 2; do [ -f $OUT/ui/$R-placed$k.xml ] && \
    python3 -I $S/wsize.py $OUT/$R-appwidget$k.txt $OUT/ui/$R-placed$k.xml $OUT/$R-settings.xml $(dens $H) $C | sed "s/^/placed$k /"; done | tee $OUT/$R-wsize.txt
  python3 -I $S/wsize.py $OUT/$R-appwidget.txt $OUT/ui/$R-after.xml $OUT/$R-settings.xml $(dens $H) $C | sed 's/^/after /' | tee -a $OUT/$R-wsize.txt
  mark ${R}_end; }
```

**`uiq3.py`**
```python
# uiq3.py <cmd> <dump.xml> [<sel>]: reads a uiautomator dump.
#   center <xml> <sel>   "x y" of the one match; exit 1 on 0 or 2+ matches
#   box    <xml> <sel>   "l t r b" of the one match; exit 1 otherwise
#   count  <xml> <sel>   number of matches with a non-empty box
#   inwin  <xml> <sel>   1 if exactly one match and it lies inside the window, else 0
#   text   <xml> <sel>   the label of the one match (its own text, else its children's, joined by " | ")
#   focused <xml>        one JSON line per focused node, with is_card
# sel: id:NAME, idnth:NAME:K (the K-th match, from 1), text:EXACT, textin:PART, desc:EXACT (content description)
import sys, re, json, xml.etree.ElementTree as ET
ACTIONS = {"Connect over USB", "Connect over WiFi", "Start Self Mode", "Search again", "Connect now", "Back to Android Auto"}
BAR = {"Exit", "Settings"}
def box(n):
    m = re.findall(r'-?\d+', n.get('bounds', ''))
    return [int(v) for v in m] if len(m) == 4 else None
def ok(n):
    b = box(n)
    return b is not None and b[2] > b[0] and b[3] > b[1]
def texts(n):
    return [c.get('text') for c in n.iter('node') if c.get('text')]
def label(n):
    return n.get('text') or ' | '.join(t for t in texts(n) if t)
def match(nodes, sel):
    kind, _, val = sel.partition(':')
    nth = None
    if kind == 'idnth':
        val, _, k = val.rpartition(':')
        nth = int(k)
    out = []
    for n in nodes:
        rid = n.get('resource-id') or ''
        if kind in ('id', 'idnth') and rid.endswith(':id/' + val): out.append(n)
        elif kind == 'text' and n.get('text') == val: out.append(n)
        elif kind == 'textin' and val in (n.get('text') or ''): out.append(n)
        elif kind == 'desc' and n.get('content-desc') == val: out.append(n)
    out = [n for n in out if ok(n)]
    if nth is not None:
        return out[nth - 1:nth] if len(out) >= nth else []
    return out
cmd, path = sys.argv[1], sys.argv[2]
nodes = list(ET.parse(path).getroot().iter('node'))
win = box(nodes[0]) if nodes else None
if cmd == 'focused':
    for n in nodes:
        if n.get('focused') == 'true':
            t = set(texts(n))
            rid = (n.get('resource-id') or '').split(':id/')[-1]
            card = rid == 'home_primary_card' or (not n.get('text') and bool(t & ACTIONS) and not (t & BAR) and n is not nodes[0])
            print(json.dumps({'class': n.get('class'), 'id': rid, 'bounds': box(n), 'label': label(n)[:80], 'is_card': card}))
    sys.exit(0)
m = match(nodes, sys.argv[3])
if cmd == 'count':
    print(len(m)); sys.exit(0)
if cmd == 'inwin':
    b = box(m[0]) if len(m) == 1 else None
    print(1 if b and win and b[0] >= win[0] and b[1] >= win[1] and b[2] <= win[2] and b[3] <= win[3] else 0); sys.exit(0)
if len(m) != 1:
    print('matches', len(m)); sys.exit(1)
b = box(m[0])
if cmd == 'center': print((b[0] + b[2]) // 2, (b[1] + b[3]) // 2)
elif cmd == 'box': print(*b)
elif cmd == 'text': print(label(m[0]))
```

**`showkeys.py`**
```python
# showkeys.py <settings.xml> <key>...: key=value per key; a set prints its items joined by ","; absent prints key=ABSENT
# showkeys.py --dels <prefix> <settings.xml>: "del:<name>" for every key whose name starts with <prefix>
import sys, xml.etree.ElementTree as ET
if sys.argv[1] == '--dels':
    root = ET.parse(sys.argv[3]).getroot()
    print(' '.join('del:' + e.get('name') for e in root if (e.get('name') or '').startswith(sys.argv[2])))
    sys.exit(0)
root = ET.parse(sys.argv[1]).getroot()
by = {e.get('name'): e for e in root}
for k in sys.argv[2:]:
    e = by.get(k)
    if e is None: print(k + '=ABSENT')
    elif e.tag == 'set': print(k + '=' + ','.join(sorted((s.text or '') for s in e)))
    elif e.tag == 'string': print(k + '=' + (e.text or ''))
    else: print(k + '=' + str(e.get('value')))
```

**`setxml.py`**
```python
# setxml.py <settings.xml> <type> <key> <value>: sets one element in place (string, int or boolean)
import sys, xml.etree.ElementTree as ET
f, t, k, v = sys.argv[1:5]
tree = ET.parse(f); root = tree.getroot()
for e in list(root):
    if e.get('name') == k: root.remove(e)
e = ET.SubElement(root, t, {'name': k})
if t == 'string': e.text = v
else: e.set('value', v)
tree.write(f, encoding='utf-8', xml_declaration=True)
```

**`wsize.py`**
```python
# wsize.py <appwidget.txt> <dump.xml> <settings.xml> <density> <compact 0|1>
# One line per home_widget_frame, left to right, matched to the ids in home-widgets in order.
# expected = max(min(minimum px, ceiling), add tile px); ceiling only on the compact home (HomeFragment.widgetTile).
# bug = the same with the minimum multiplied by the density once more (round 1, B2).
import sys, re, xml.etree.ElementTree as ET
aw, dump, sx, dens, compact = sys.argv[1], sys.argv[2], sys.argv[3], float(sys.argv[4]), sys.argv[5] == '1'
GAP = {48: 4, 60: 6, 96: 12, 120: 20}   # home_favorites_height dp -> home_gap dp, per values folder at 0d333b19
def px(v):
    unit, radix = v & 0xF, (v >> 4) & 3
    if v < 256 or unit > 5:
        return v, 'px'                     # already pixels
    val = ((v >> 8) & 0xFFFFFF) * [1.0, 1 / 128, 1 / 32768, 1 / 8388608][radix]
    mult = {0: 1, 1: dens, 2: dens}.get(unit, 1)
    return int(val * mult + 0.5), 'dim%d=%g' % (unit, val)
prov, cur = {}, None
widgets, wid, sect = {}, None, ''
for l in open(aw, errors='replace'):
    s = l.strip()
    if s.startswith('Providers:'): sect = 'p'
    elif s.startswith('Widgets:'): sect = 'w'
    elif s.startswith('Hosts:') or s.startswith('Grants:'): sect = ''
    m = re.search(r'cmp:ComponentInfo\{([^}]+)\}', s)
    if sect == 'p':
        if m and 'provider' in s: cur = m.group(1)
        mm = re.search(r'min=\((\d+)x(\d+)\)', s)
        if mm and cur and cur not in prov: prov[cur] = int(mm.group(1))
    if sect == 'w':
        mi = re.match(r'\[\d+\] id=(\d+)', s)
        if mi: wid = int(mi.group(1)); widgets[wid] = {'host': '', 'cmp': ''}
        elif wid is not None and s.startswith('host=') : widgets[wid]['host'] = s
        elif wid is not None and s.startswith('provider=') and m: widgets[wid]['cmp'] = m.group(1)
ours = [w for w, d in widgets.items() if 'com.andrerinas.headunitrevived' in d['host']]
order = []
for e in ET.parse(sx).getroot():
    if e.get('name') == 'home-widgets':
        order = [int(x) for x in (e.text or '').split(',') if x.strip().isdigit()]
nodes = list(ET.parse(dump).getroot().iter('node'))
def bx(n): return [int(v) for v in re.findall(r'-?\d+', n.get('bounds', ''))]
frames = sorted([bx(n) for n in nodes if (n.get('resource-id') or '').endswith(':id/home_widget_frame')], key=lambda b: b[0])
add = [bx(n) for n in nodes if (n.get('resource-id') or '').endswith(':id/widget_add_tile')]
rootw = bx(nodes[0])[2] - bx(nodes[0])[0] if nodes else 0
print('our_widget_ids=%s home-widgets=%s frames=%d add_tile_px=%s window_w=%d density=%g' %
      (sorted(ours), order, len(frames), (add[0][2] - add[0][0]) if add else 'none', rootw, dens))
addpx = (add[0][2] - add[0][0]) if add else None
for i, f in enumerate(frames):
    w = f[2] - f[0]
    wid = order[i] if i < len(order) else None
    cmp = widgets.get(wid, {}).get('cmp', '?')
    raw = prov.get(cmp)
    if raw is None or addpx is None:
        print('frame%d id=%s provider=%s actual=%d expected=NA (no minimum or no add tile in this dump)' % (i + 1, wid, cmp, w)); continue
    mpx, how = px(raw)
    ceil = 10 ** 9
    if compact:
        gap = int(GAP.get(round(addpx / dens), 12) * dens + 0.5)
        ceil = rootw - gap * 3 - addpx
    exp = max(min(mpx, ceil), addpx)
    bug = max(min(int(mpx * dens + 0.5), ceil), addpx)
    disc = 1 if bug - exp > 4 else 0
    okv = 1 if abs(w - exp) <= 2 else 0
    print('frame%d id=%s provider=%s min_raw=%d (%s) min_px=%d ceiling=%s expected=%d bug=%d actual=%d ok=%d discriminating=%d clipped=%d' %
          (i + 1, wid, cmp, raw, how, mpx, ceil if compact else 'none', exp, bug, w, okv, disc, 1 if f[2] >= rootw - 1 else 0))
```

## 6. Pre-flight and install

Run `build.sh` in the background and `preflight.sh` in the foreground beside it: pre-flight is adb only.

**`preflight.sh`**
```bash
. $(dirname $0)/lib.sh
exec > >(tee -a $OUT/preflight.txt) 2>&1
echo "==== preflight $(date)"
$RIG_TOOLKIT/rig_preflight.sh D_HU:wifi D_HP:wifi D_POCO:wifi; $RIG_TOOLKIT/rig_cleanup.sh list
adb devices                                                     # MUST list D-HU, D-HP, D-POCO and D-MOTO
FIRST=no; [ -f $OUT/orig.sh ] || FIRST=yes
for s in $D_HU $D_POCO $D_HP $D_SAM; do
  adb devices | grep -q "^$s" || { echo "== $s not on adb"; continue; }
  echo "== $s sdk $(adb -s $s shell getprop ro.build.version.sdk | tr -d '\r') locale $(adb -s $s shell getprop persist.sys.locale | tr -d '\r')$(adb -s $s shell getprop persist.sys.language | tr -d '\r')"
  echo "unit date $(adb -s $s shell date | tr -d '\r') host $(date)"
  [ $s = $D_HU ] || [ $s = $D_POCO ] && { [ $FIRST = yes ] && adb -s $s shell cmd role get-role-holders android.app.role.HOME | tr -d '\r' > $OUT/home-role-$s.txt
    echo "home role $(cat $OUT/home-role-$s.txt)"; }           # MUST NOT be empty and MUST NOT be $PKG; else stop and ask
  echo "app running $(running $s)"; stopapp $s
  adb -s $s shell ls -ld /data/data/$PKG/shared_prefs 2>/dev/null || adb -s $s shell run-as $PKG ls -ld shared_prefs
  if [ $FIRST = yes ]; then pull_settings $s > $OUT/backup-$s.xml
    adb -s $s pull $(adb -s $s shell pm path $PKG | cut -d: -f2 | tr -d '\r') $OUT/before-$s.apk >/dev/null; fi
  wc -c < $OUT/backup-$s.xml                                      # MUST be over 0
  python3 -I $S/showkeys.py $OUT/backup-$s.xml wifi-connection-mode screen-orientation video-profile-starvation-cap \
    use-aac-audio audio-latency-multiplier audio-queue-capacity home-favorite-apps home-widgets home-widget-pages
done
echo "== D-HU"
adb -s $D_HU shell id                                             # MUST show uid=0(root)
HU_OWNER=$(adb -s $D_HU shell ls -ld /data/data/$PKG/shared_prefs | awk '{print $3":"$4}' | tr -d '\r'); echo "HU_OWNER=$HU_OWNER"
adb -s $D_HU shell wm density; adb -s $D_HU shell wm size
adb -s $D_HU shell appwidget grantbind --package $PKG --user 0; echo "grantbind rc=$?"
adb -s $D_HU shell cmd package query-activities --brief -a android.appwidget.action.APPWIDGET_CONFIGURE | tee $OUT/hu-configure-activities.txt
echo "hu bt $(btstate $D_HU)"
echo "== D-POCO"
adb -s $P shell wm density; adb -s $P shell appwidget grantbind --package $PKG --user 0; echo "grantbind rc=$?"
adb -s $P shell dumpsys package com.google.android.projection.gearhead | grep -a -m1 versionName
adb -s $P shell dumpsys window | grep -a -m1 mCurrentFocus; adb -s $P shell input keyevent KEYCODE_HOME
echo "== D-MOTO"
adb -s $D_MOTO shell dumpsys window | grep -a -m1 mCurrentFocus   # Keyguard: hand step 1
adb -s $D_MOTO shell dumpsys package com.google.android.projection.gearhead | grep -a -m1 versionName
echo "moto throttle lines $(adb -s $D_MOTO logcat -d | grep -ac THROTTLE_LIMIT_EXCEEDED)"   # above 0: reboot D-MOTO first
if [ $FIRST = yes ]; then
  cat > $OUT/orig.sh <<EOF
HU_ANIM=$(adb -s $D_HU shell settings get global animator_duration_scale | tr -d '\r'); POCO_ANIM=$(adb -s $P shell settings get global animator_duration_scale | tr -d '\r'); HP_ANIM=$(adb -s $D_HP shell settings get global animator_duration_scale | tr -d '\r')
HU_BT=$(btstate $D_HU); POCO_BT=$(btstate $P); POCO_STAYON=$(adb -s $P shell settings get global stay_on_while_plugged_in | tr -d '\r')
MOTO_WIFI=$(adb -s $D_MOTO shell dumpsys wifi | grep -a -m1 -o 'Wi-Fi is [a-z]*' | awk '{print $3}'); MOTO_BT=$(btstate $D_MOTO)
EOF
fi
cat $OUT/orig.sh
for s in $D_HU $P $D_HP; do adb -s $s shell settings put global animator_duration_scale 0; done
adb -s $P shell svc bluetooth disable; sleep 3; echo "poco bt $(btstate $P)"   # MUST read false
ph_off $D_MOTO
dn() { adb -s $1 shell wm density | tr -d '\r' | awk -F': ' '/Override/{o=$2} /Physical/{p=$2} END{print (o?o:p)/160}'; }
echo "HU_OWNER=$HU_OWNER; HU_DENS=$(dn $D_HU); POCO_DENS=$(dn $P)" | tee $OUT/env.sh
```

Pre-flight gates: each line marked MUST. A gate with a hand step: ask for the step, then run `preflight.sh` again. Put the whole `preflight.txt` in Setup notes, with D-MOTO's Gearhead version and the list in `hu-configure-activities.txt`.

**`install.sh`**
```bash
. $(dirname $0)/lib.sh
exec > >(tee $OUT/install.txt) 2>&1
for s in $D_HU $D_POCO $D_HP; do echo "== $s"; inst $s; done
```

Every unit MUST show `apk_identity ... exit 0` and commit `0d333b19bef3`.

## 7. The deciding lines

Every app string below was checked with `git grep -F` in `app/src/main` at `0d333b19`. Grep every capture with `-a`. Some lines are built at run time; the source holds the template:

- `MainActivity: status pill X pressed, stopping the bring-up` comes from `MainActivity: $source pressed, stopping the bring-up` with the default `source` `"status pill X"`. The card's Cancel passes `"home Cancel"`. The helpers grep `pressed, stopping the bring-up` and `status pill X` (or `home Cancel`) on one line.
- `HomeFragment: pages hint closed (BACK)` comes from `HomeFragment: pages hint closed ($reason)`; the reasons are `BACK`, `LEFT`, `GOT_IT` and `HOLD`.
- `HomeFragment: widget <id> was not set up, releasing its id` comes from `HomeFragment: widget $appWidgetId $why, releasing its id`.
- `HomeFragment: favorite shortcut com.android.settings/rig_b4_probe cannot be read now, keeping its slot` and `HomeFragment: favorite com.rig.b4.absent is no longer installed, showing its slot as free` come from the `$name` and `$packageName` templates in `HomeFragment.shortcutFavorite` and `appFavorite`.
- `WifiLauncher: Initializing WiFi Mode: NATIVE` comes from `WifiLauncher: Initializing WiFi Mode: ${newLauncher.mode}`.
- `dispatchKeyEvent: keyCode=4,` comes from `dispatchKeyEvent: keyCode=%d, action=%d`. Key codes: 4 BACK, 19 UP, 20 DOWN, 21 LEFT, 22 RIGHT.

Head unit captures (`$OUT/<block>-hu.txt`):

| Key | String | Meaning |
|---|---|---|
| L-RX | `AutomationReceiver: ` | a verb landed; a missing line voids the step |
| L-MARK | `AutomationMarker: ` | a marker landed |
| L-KEY | `dispatchKeyEvent: keyCode=` | a key event reached `MainActivity` |
| L-HINT | `HomeFragment: showing the pages hint` | both forms of the hint |
| L-HINTX | `HomeFragment: pages hint closed (` | read the reason |
| L-RES | `HomeFragment: onResume. isConnected=` | the home is up |
| L-FIT | `HomeFragment: the main button is ` | record: lines the card hid to keep its action |
| L-STEP | `MainActivity: status pill step: ` | the step; a suffix says who tells it |
| HOMELINE | `(not shown, the home's main button tells it)` | the card tells the step, the pill is down |
| L-CANCEL | `pressed, stopping the bring-up` | with `status pill X` or `home Cancel` on the same line |
| L-HOLD | `AapService: the status pill's X stopped the wireless bring-up.` | the service held the stack down |
| L-POKE | `NativeAA: Attempting active poke to device` | a wake attempt |
| L-ARM | `WifiLauncher: Initializing WiFi Mode: ` | read the mode |
| L-SCAN | `AapService: Force-starting WIFI-Scan from UI` | the WiFi button verb reached the service |
| L-DISC-SCAN | `NetworkDiscovery: Starting scan...` | one discovery scan in mode 1 |
| L-SSL | `SSL handshake complete` | the session formed |
| L-MSSR | `Media Sink Setup Request: ` | projection |
| L-TP | `Throughput over` | projection; with `rendered=` above 0 |
| L-BYE | `Disconnect action received.` | the clean end landed |
| L-KEEP | `cannot be read now, keeping its slot` | B4: the shortcut slot was kept |
| L-GONE | `is gone, showing its slot as free` | B4: the shortcut slot was freed |
| L-ABSENT | `is no longer installed, showing its slot as free` | B4 control: the absent package's slot was freed |
| L-REL | `, releasing its id` | with `was not set up`: B5 released the cancelled widget |
| L-HOST | `set up through the host: ` | the set-up activity is not exported (N1); B5 is not graded |
| L-WAKE | `MATCH! Starting AapService` | template section 4 discard rule |
| L-GROUP | `createGroup SUCCESS` | record |

Phone captures (`$OUT/HU-C-moto.txt`; `SAM-HS-ph.txt` in the optional run). Checked in the decompiled Gearhead 18.0.664204, not in our tree:

| Key | String | Meaning |
|---|---|---|
| PH-END | `PROJECTION_ENDED_BYEBYE_BY_CAR` | the head unit ended the session cleanly |
| PH-CRIT | `Critical error` | the phone ended it on a protocol error |
| PH-THR | `THROTTLE_LIMIT_EXCEEDED` | Gearhead's RFCOMM throttle; above 0 calls the stop rule |
| | `RIGMARK` | the run's markers on the phone |

Screen texts the dumps match (`values/strings.xml` at `0d333b19`; every rig unit reads English): `Waking your phone`, `Looking for your phone`, `Searching for your phone…` (matched by its prefix), `Search again`, `Cancel`, `Stop connecting` (the pill X's content description), `Add a page`, `End session, stay ready`, `Stop Connection`, `Move to Background`, `Picture-in-Picture`, `Quick Settings`, `Switch Phone`. View ids (all in `app/src/main/res/layout` at `0d333b19`): `home_primary_card`, `home_primary_title`, `home_primary_subtitle`, `home_primary_cancel`, `home_primary_progress`, `home_pager`, `home_pages_hint_got_it`, `auto_connect_pill`, `auto_connect_pill_cancel`, `widget_add_tile`, `widget_option_label`, `home_widget_frame`. The system bind dialog's button is `android:id/button1`.

## 8. Runs

Order: HU-L, HU-C, HP, POCO-L, then the optional SAM-HS. A block starts only when the one before it has ended and its numbers are in the results file. `th_gate` applies the host thermal rule in `rig-quirks/topics/tooling.md` at the start of each block.

Common void rules: a verb with no `AutomationReceiver: ` line; `HOST_HOT`; a `DUMP_FAILED` on a dump a condition reads (that condition only). A void run is re-run once; a second void is UNTESTABLE. A seed read-back that does not show the run's keys is a setup failure: fix it and re-run once.

### HU-L: D-HU on the launcher home, idle (HU-B6, HU-W1, HU-B4)

Usage: `bash $OUT/scripts/hu_l.sh`. One capture. No phone: `wifi-connection-mode` 0 arms nothing.

**`hu_l.sh`**
```bash
H=$D_HU; PHONES=""; . $(dirname $0)/lib.sh
HC=$OUT/HU-L-hu.txt
FAV='com.android.settings,com.android.settings|rig_b4_probe,com.rig.b4.absent,'
th_gate HU-L || exit 1
role_off; stopapp $D_HU; stopapp $P
seed $D_HU $K_LAUNCH $(idels $D_HU) | tee $OUT/HU-L-seed.txt     # MUST show home-pages-hint-shown=ABSENT, home-widgets=ABSENT
cap $HC $D_HU
# HU-B6: BACK closes the pages hint, and the next gesture acts
R=HU-B6; mark ${R}_start; launch
waitfor $HC ${R}_start 40 'HomeFragment: onResume. isConnected='; role_on
b6 $R $HC
# HU-W1: widget size (B2), and a set-up screen if one comes up (B5)
R=HU-W1; mark ${R}_start; wrun $R 0
# HU-B4: the home role goes back to the factory launcher, then the app opens with a shortcut favorite stored
R=HU-B4; mark ${R}_role; role_off; stopapp $D_HU
pull_settings $D_HU > $OUT/$R-pre.xml
python3 -I $S/setxml.py $OUT/$R-pre.xml string home-favorite-apps "$FAV"
push_settings $D_HU $OUT/$R-pre.xml | tee $OUT/$R-push.txt           # MUST read SETTINGS_BACK
python3 -I $S/showkeys.py $OUT/$R-pre.xml home-favorite-apps | tee $OUT/$R-before.txt
echo "role holder $(adb -s $D_HU shell cmd role get-role-holders android.app.role.HOME | tr -d '\r')" | tee $OUT/$R-role.txt
mark ${R}_start; launch
waitfor $HC ${R}_start 40 'HomeFragment: onResume. isConnected='; sleep 6; uidump $R-home
pull_settings $D_HU > $OUT/$R-after.xml; python3 -I $S/showkeys.py $OUT/$R-after.xml home-favorite-apps | tee $OUT/$R-afterkey.txt
mark ${R}_end; stopapp $D_HU
uncap $HC; th_end
for R in HU-B6; do
  echo "$R hint=$(cnt $HC ${R}_start ${R}_back 'HomeFragment: showing the pages hint') hint_dump=$(uiq count $OUT/ui/$R-hint.xml id:home_pages_hint_got_it)" \
    "key4=$(cnt $HC ${R}_back ${R}_hold 'dispatchKeyEvent: keyCode=4,') closed_back=$(cnt2 $HC ${R}_back ${R}_hold 'HomeFragment: pages hint closed (' 'BACK)')" \
    "after_back_hint=$(uiq count $OUT/ui/$R-afterback.xml id:home_pages_hint_got_it) menu_add_page=$(uiq count $OUT/ui/$R-menu.xml 'text:Add a page')" \
    "closed_left=$(cnt2 $HC ${R}_start ${R}_end 'pages hint closed (' 'LEFT)') closed_gotit=$(cnt2 $HC ${R}_start ${R}_end 'pages hint closed (' 'GOT_IT)')"
done | tee $OUT/HU-B6-counts.txt
R=HU-W1; { cat $OUT/$R-attempts.txt; cat $OUT/$R-focus*.txt
  echo "released=$(cnt2 $HC ${R}_start ${R}_end ', releasing its id' 'was not set up') via_host=$(cnt $HC ${R}_start ${R}_end 'set up through the host: ')"
  seg $HC ${R}_start ${R}_end | grep -aF ', releasing its id'; } | tee $OUT/$R-counts.txt
R=HU-B4; echo "keep=$(cnt $HC ${R}_start ${R}_end 'favorite shortcut com.android.settings/rig_b4_probe cannot be read now, keeping its slot')" \
  "gone=$(cnt $HC ${R}_start ${R}_end 'favorite shortcut com.android.settings/rig_b4_probe is gone')" \
  "absent_freed=$(cnt $HC ${R}_start ${R}_end 'favorite com.rig.b4.absent is no longer installed, showing its slot as free')" | tee $OUT/$R-counts.txt
```

**HU-B6 PASS** when all hold (`HU-B6-counts.txt`):

- Reach: `hint` 1 and `hint_dump` 1 (the hint was up before BACK).
- `key4` 1 or more, and `closed_back` = 1.
- `after_back_hint` = 0 (the hint is gone in the dump after BACK).
- `menu_add_page` = 1 (the long press after BACK opened the page menu: it was not swallowed).
- `closed_left` = 0 and `closed_gotit` = 0.

`NO_HINT` makes HU-B6 INCONCLUSIVE; record the last `L-RES` line and whether a banner was up. `HINT_STILL_UP` is FAIL.

**HU-W1 PASS (B2)** when the first `frame1` line from the first placed dump in `HU-W1-wsize.txt` (`placed1`, else `placed2`) reads `ok=1`. FAIL when it reads `ok=0`; say whether `actual` is near `bug`. `discriminating=0` makes B2 INCONCLUSIVE (the widget is narrower than the "+" tile). No `PLACED` attempt makes B2 UNTESTABLE. Record every `wsize` line, the provider and the `after` dump's lines.

**HU-W1 grade for B5**, from `HU-W1-attempts.txt`, the focus files and `HU-W1-counts.txt`:

- PASS when an attempt reads `CONFIGURE`, its focus file names a component outside `com.andrerinas.headunitrevived`, `released` = 1, `via_host` = 0, and the released id is not in `our_widget_ids` or `home-widgets` on the `after` line.
- FAIL when an attempt reads `CONFIGURE` and `released` = 0, or the released id is still in `home-widgets`.
- UNTESTABLE when no attempt reads `CONFIGURE` (no set-up widget among the first two entries; record `hu-configure-activities.txt`), or when `via_host` is 1 or more (the N1 path, not graded).

**HU-B4 PASS** when all hold:

- Reach: `HU-B4-push.txt` reads `SETTINGS_BACK`; `HU-B4-before.txt` holds the `FAV` value; `HU-B4-role.txt` does not name `com.andrerinas.headunitrevived`.
- `keep` 1 or more, and `gone` = 0.
- Control: `absent_freed` 1 or more (the app's own write reached the file).
- `HU-B4-afterkey.txt` reads exactly `home-favorite-apps=com.android.settings,com.android.settings|rig_b4_probe,,`.

FAIL when the shortcut slot is missing from the stored value or `gone` is 1 or more. If `absent_freed` is 0 and the value is unchanged from `FAV`, the app wrote nothing: INCONCLUSIVE, and record the `shared_prefs` owner from pre-flight.

### HU-C: D-HU Native AA with D-MOTO, launcher home with two pages (HU-F1, HU-W8) **(the point of the round)**

Usage: `bash $OUT/scripts/hu_c.sh`. One capture on D-HU and one on D-MOTO. D-POCO's WiFi is held off and its Bluetooth stays off. HU-F1 is round 1's HU-C1 and HU-F1, with the X tapped on page 2. HU-W8 is a short session for the exit menu.

**`hu_c.sh`**
```bash
H=$D_HU; PHONES=$D_MOTO; . $(dirname $0)/lib.sh
HC=$OUT/HU-C-hu.txt; MC=$OUT/HU-C-moto.txt
# waitwake <from> <label>: a dump right after a WAKING_PHONE step, with the progress bar on it; 3 tries
waitwake() { local k; for k in 1 2 3; do waitstep $HC $1 120 WAKING_PHONE $k || return 1
  uidump $2; [ "$(uiq count $OUT/ui/$2.xml id:home_primary_progress)" -ge 1 ] && return 0; done; return 1; }
th_gate HU-C || exit 1
role_off; stopapp $D_HU; hold_on
ph_off $D_MOTO | tee $OUT/HU-C-moto-off.txt
adb -s $D_HU shell svc bluetooth enable; sleep 3
echo "radios: hu_bt=$(btstate $D_HU) poco_bt=$(btstate $P)" | tee $OUT/HU-C-radios.txt   # hu_bt MUST be true, poco_bt false
seed $D_HU $K_HUC $(idels $D_HU) | tee $OUT/HU-C-seed.txt          # MUST show native-poke-bt-macs=A0:46:5A:97:E4:95, home-widget-pages=p
cap $HC $D_HU; cap $MC $D_MOTO
R=HU-F1; mark ${R}_start; launch
waitfor $HC ${R}_start 20 'WifiLauncher: Initializing WiFi Mode: NATIVE' || echo "$R SETUP: no NATIVE init line"
waitfor $HC ${R}_start 40 'HomeFragment: onResume. isConnected='; role_on
waitwake ${R}_start $R-wake1 || echo "$R NO_WAKE" | tee -a $OUT/marks.txt
mark ${R}_sw1; gest $R next id:home_pager $R-wake1; sleep 3; uidump $R-p2a
mark ${R}_home; adb -s $H shell input keyevent 3; sleep 3
waitwake ${R}_home $R-wakeB || echo "$R NO_WAKE_B" | tee -a $OUT/marks.txt
mark ${R}_sw2; gest $R next id:home_pager $R-wakeB; sleep 3; uidump $R-p2
mark ${R}_x; gest $R tap id:auto_connect_pill_cancel $R-p2; sleep 5
mark ${R}_hold; sleep 60; uidump $R-after; mark ${R}_end
R=HU-W8; mark ${R}_start; send ACTION_START_WIRELESS_SCAN >/dev/null
waitfor $HC ${R}_start 10 'AapService: Force-starting WIFI-Scan from UI' || echo "$R scan verb not seen"
mark ${R}_phone; ph_on $D_MOTO | tee $OUT/HU-C-moto-on.txt
if waitfor $HC ${R}_phone 150 'Throughput over'; then
  sleep 6; mark ${R}_menu; adb -s $H shell input keyevent 4; sleep 2; uidump $R-menu
  adb -s $D_HU shell cmd role get-role-holders android.app.role.HOME | tr -d '\r' > $OUT/$R-role.txt
  adb -s $H shell input keyevent 4; sleep 2
  mark ${R}_disc; send ACTION_DISCONNECT >/dev/null
  waitfor $HC ${R}_disc 15 'Disconnect action received.'; waitfor $MC ${R}_disc 15 'PROJECTION_ENDED_BYEBYE_BY_CAR'; sleep 3
else echo "$R NO_SESSION" | tee -a $OUT/marks.txt; fi
mark ${R}_end
ph_off $D_MOTO; role_off; stopapp $D_HU; hold_off
uncap $HC; uncap $MC; th_end
R=HU-F1; { for d in wake1 wakeB; do
    echo "W16 $d subtitle=[$(uiq text $OUT/ui/$R-$d.xml id:home_primary_subtitle)] cancel_inwin=$(uiq inwin $OUT/ui/$R-$d.xml id:home_primary_cancel) progress=$(uiq count $OUT/ui/$R-$d.xml id:home_primary_progress)"; done
  echo "FIT lines=$(cnt $HC ${R}_start ${R}_x 'HomeFragment: the main button is ')"; seg $HC ${R}_start ${R}_x | grep -aF 'HomeFragment: the main button is '
  echo "REACH pokes=$(cnt $HC ${R}_start ${R}_x 'NativeAA: Attempting active poke to device') waking=$(nstep $HC ${R}_start ${R}_x WAKING_PHONE)"
  for d in p2a p2; do echo "F1 $d pill=$(uiq count $OUT/ui/$R-$d.xml id:auto_connect_pill) x_inwin=$(uiq inwin $OUT/ui/$R-$d.xml id:auto_connect_pill_cancel) card_cancel=$(uiq count $OUT/ui/$R-$d.xml id:home_primary_cancel)"; done
  echo "STEP bare_after_sw1=$(bare $HC ${R}_sw1 ${R}_home '') bare_after_sw2=$(bare $HC ${R}_sw2 ${R}_x '')"
  seg $HC ${R}_sw2 ${R}_x | grep -aF "$STEP"
  echo "CANCEL x_line=$(cnt2 $HC ${R}_x ${R}_hold 'pressed, stopping the bring-up' 'status pill X') hold_line=$(cnt $HC ${R}_x ${R}_hold "AapService: the status pill's X stopped the wireless bring-up.")"
  echo "AFTER pokes=$(cnt $HC ${R}_hold ${R}_end 'NativeAA: Attempting active poke to device') waking=$(nstep $HC ${R}_hold ${R}_end WAKING_PHONE) after_x=$(uiq count $OUT/ui/$R-after.xml id:auto_connect_pill_cancel)"
} | tee $OUT/$R-counts.txt
R=HU-W8; { echo "SESSION ssl=$(ssl $HC ${R}_phone ${R}_menu) mssr=$(cnt $HC ${R}_phone ${R}_menu 'Media Sink Setup Request: ') rend=$(rend $HC ${R}_phone ${R}_menu) role=[$(cat $OUT/$R-role.txt)]"
  for t in 'End session, stay ready' 'Stop Connection' 'Move to Background' 'Picture-in-Picture' 'Quick Settings' 'Switch Phone'; do echo "MENU [$t]=$(uiq count $OUT/ui/$R-menu.xml "text:$t")"; done
  echo "PHONE critical=$(cnt $MC ${R}_phone ${R}_disc 'Critical error') byecar_after_disc=$(cnt $MC ${R}_disc ${R}_end 'PROJECTION_ENDED_BYEBYE_BY_CAR') throttle=$(cnt $MC HU-F1_start ${R}_end THROTTLE_LIMIT_EXCEEDED)"
  echo "RECORD match=$(cnt $HC ${R}_start ${R}_end 'MATCH! Starting AapService') groups=$(cnt $HC HU-F1_start ${R}_end 'createGroup SUCCESS')"
} | tee $OUT/$R-counts.txt
```

**HU-F1 PASS (B1)** when all hold (`HU-F1-counts.txt`):

- Reach: `REACH pokes` 1 or more and `waking` 1 or more (a wake was running before the X).
- `F1 p2`: `x_inwin` = 1 (the pill's X is on screen on page 2). Record `F1 p2a` the same way.
- `STEP bare_after_sw2` 1 or more (the pill told the step once the card left the screen).
- `CANCEL x_line` = 1 and `hold_line` = 1.
- `AFTER pokes` = 0, `waking` = 0 and `after_x` = 0 (the X stopped the wake for 60 s).

**HU-F1 grade for W16** (graded on its own row as HU-W16): PASS when both `W16` lines read `subtitle=[Waking your phone]`, `cancel_inwin=1` and `progress=1`. The `wakeB` line is the one round 1 failed. Record the `FIT` lines; a `FIT` line is not a FAIL if the step stays.

`NO_WAKE` makes HU-F1 and HU-W16 INCONCLUSIVE; record the last `L-STEP` line. `NO_WAKE_B` makes only the `wakeB` part INCONCLUSIVE.

**HU-W8 PASS** when all hold (`HU-W8-counts.txt`):

- Reach: `ssl` 1 or more, `mssr` 1 or more, `rend` 1 or more; `role` names `com.andrerinas.headunitrevived` (the short menu applies).
- `MENU [End session, stay ready]` = 1, `[Stop Connection]` = 1, `[Move to Background]` = 1.
- `[Picture-in-Picture]` = 0 and `[Quick Settings]` = 0 (the short launcher menu).
- `PHONE critical` = 0 and `byecar_after_disc` 1 or more.

Record `[Switch Phone]`, `match` (above 0 is a re-run by the template's discard rule) and `groups`.

**Stop rule.** `NO_SESSION`: run `hu_c.sh` once more. If `throttle` is above 0, reboot D-MOTO first (`adb -s $D_MOTO reboot`, wait for `sys.boot_completed` 1, then hand step 1 if it is locked). A second `NO_SESSION` makes HU-W8 UNTESTABLE (rig): record the last `NativeAA: ` lines. HU-F1 grades from the first pass unless that pass was void.

### HP: D-HP home mode (HP-W12, HP-F1)

Usage: `bash $OUT/scripts/hp.sh`. One capture with its watcher. D-POCO's WiFi is held off, so D-HP's search finds no server.

**`hp.sh`**
```bash
H=$D_HP; PHONES=""; . $(dirname $0)/lib.sh
HC=$OUT/HP-hu.txt
th_gate HP || exit 1
hold_on; stopapp $D_HP
seed $D_HP $K_HPIDLE $(idels $D_HP) | tee $OUT/HP-seed0.txt
cap $HC $D_HP; capwatch $HC $D_HP
# HP-W12: D-pad focus on the home; stop at the first dump with the card focused
R=HP-W12; mark ${R}_start; launch
waitfor $HC ${R}_start 40 'HomeFragment: onResume. isConnected='; sleep 3; uidump $R-k0
i=0; for k in DPAD_DOWN DPAD_RIGHT DPAD_UP DPAD_UP DPAD_LEFT DPAD_RIGHT; do
  i=$((i+1)); mark ${R}_k$i; adb -s $H shell input keyevent KEYCODE_$k; sleep 2; uidump $R-k$i
  echo "k$i $k $(uiq focused $OUT/ui/$R-k$i.xml | tr '\n' ' ')" | tee -a $OUT/$R-focus.txt
  grep -q '"is_card": true' <(uiq focused $OUT/ui/$R-k$i.xml) && break
done
mark ${R}_end; stopapp $D_HP
# HP-F1 arm a: USB main button, the search runs; the pill's X must be there and work
R=HP-F1a; seed $D_HP $K_HPUSB | tee $OUT/HP-seed1.txt              # MUST show last-connection-type=usb, wifi-connection-mode=1
mark ${R}_start; launch
waitstep $HC ${R}_start 60 SEARCHING || echo "$R NO_SEARCH" | tee -a $OUT/marks.txt
sleep 2; uidump $R-a
SEL="desc:Stop connecting"; [ "$(uiq count $OUT/ui/$R-a.xml "$SEL")" -eq 1 ] || SEL="text:Cancel"
mark ${R}_x; gest HP-F1 tap "$SEL" $R-a; sleep 5
mark ${R}_hold; sleep 30; mark ${R}_end; stopapp $D_HP
# HP-F1 arm b: WiFi main button; the card must not flip during one search
R=HP-F1b; seed $D_HP $K_HPWIFI | tee $OUT/HP-seed2.txt             # MUST show last-connection-type=wifi
mark ${R}_start; launch
waitstep $HC ${R}_start 60 SEARCHING || echo "$R NO_SEARCH" | tee -a $OUT/marks.txt
for d in 1 2 3 4; do mark ${R}_d$d; uidump $R-d$d; sleep 8; done
mark ${R}_end; stopapp $D_HP
uncap $HC; th_end; hold_off
R=HP-W12; { cat $OUT/$R-focus.txt
  echo "keys_reached=$(seg $HC ${R}_start ${R}_end | grep -aE 'dispatchKeyEvent: keyCode=(19|20|21|22),' | grep -ac 'action=0')"; } | tee $OUT/$R-counts.txt
R=HP-F1a; { echo "first_search_step=[$(seg $HC ${R}_start ${R}_x | grep -aF "$STEP" | grep -aF SEARCHING | head -1 | sed 's/.*status pill step: //')]"
  echo "pill_x=$(uiq count $OUT/ui/$R-a.xml 'desc:Stop connecting') card_cancel=$(uiq count $OUT/ui/$R-a.xml text:Cancel) card_title=[$(uiq count $OUT/ui/$R-a.xml 'text:Plug in your phone')]"
  echo "x_line=$(cnt $HC ${R}_x ${R}_hold 'pressed, stopping the bring-up') hold_line=$(cnt $HC ${R}_x ${R}_hold "AapService: the status pill's X stopped the wireless bring-up.")"
  echo "scans_before=$(cnt $HC ${R}_start ${R}_x 'NetworkDiscovery: Starting scan...') scans_after=$(cnt $HC ${R}_hold ${R}_end 'NetworkDiscovery: Starting scan...')"
} | tee $OUT/$R-counts.txt
R=HP-F1b; { for d in 1 2 3 4; do n=${R}_d$((d+1)); [ $d = 4 ] && n=${R}_end
    echo "d$d step=[$(seg $HC ${R}_start ${R}_d$d | grep -aF "$STEP" | tail -1 | sed 's/.*status pill step: //')] looking=$(uiq count $OUT/ui/$R-d$d.xml 'text:Looking for your phone') cancel=$(uiq count $OUT/ui/$R-d$d.xml text:Cancel) searching_title=$(uiq count $OUT/ui/$R-d$d.xml 'textin:Searching for your phone') search_again=$(uiq count $OUT/ui/$R-d$d.xml 'text:Search again')"; done
  echo "scans=$(cnt $HC ${R}_start ${R}_end 'NetworkDiscovery: Starting scan...') found=$(cnt $HC ${R}_start ${R}_end 'NetworkDiscovery: Found Headunit Server on ')"
} | tee $OUT/$R-counts.txt
grep -c 'capture restarted' $OUT/marks.txt
```

**HP-W12 PASS** when one `k<n>` line in `HP-W12-counts.txt` holds `"is_card": true` (the card took D-pad focus). Reach: `keys_reached` 1 or more. FAIL when `keys_reached` is 1 or more and no line holds `"is_card": true`; record which nodes took focus. Record, for the eye only: whether the screenshot `HP-W12-k<n>.png` of the focused card shows a visible change against `HP-W12-k0.png`.

**HP-F1 PASS (B1)** when arm a and arm b both hold:

- Arm a, reach: `scans_before` 1 or more and `first_search_step` names `SEARCHING`.
- Arm a: `first_search_step` reads `[SEARCHING]` with no suffix (the pill is shown), and `pill_x` = 1 or `card_cancel` = 1.
- Arm a: `x_line` = 1, `hold_line` = 1 and `scans_after` = 0 (the X stopped the search for 30 s).
- Arm b, reach: `scans` 2 or more and `found` = 0 (one search across several scans, with nothing to find).
- Arm b: `searching_title` = 0 and `search_again` = 0 in all four `d` lines (no flip).
- Arm b: in every `d` line whose `step` names `SEARCHING`, `looking` = 1 and `cancel` = 1.

Record `card_title`. A `d` line whose `step` is `hidden` or another stage is not graded; if all four are, arm b is INCONCLUSIVE. `NO_SEARCH` in an arm makes that arm INCONCLUSIVE; record the last `L-STEP` line.

### POCO-L: D-POCO as the head unit, launcher home in portrait (POCO-B6, POCO-W1)

Usage: `bash $OUT/scripts/poco_l.sh`. One capture. No connection: `wifi-connection-mode` 0. D-POCO's WiFi state does not matter here.

**`poco_l.sh`**
```bash
H=$D_POCO; PHONES=""; . $(dirname $0)/lib.sh
HC=$OUT/POCO-L-hu.txt
th_gate POCO-L || exit 1
hold_off; role_off; stopapp $P
adb -s $P shell dumpsys window | grep -a -m1 mCurrentFocus; adb -s $P shell input keyevent KEYCODE_HOME
seed $P $K_LAUNCH scalar:int:screen-orientation:4 $(idels $P) | tee $OUT/POCO-L-seed.txt
cap $HC $P
R=POCO-B6; mark ${R}_start; launch
waitfor $HC ${R}_start 40 'HomeFragment: onResume. isConnected='; role_on
b6 $R $HC
R=POCO-W1; mark ${R}_start; wrun $R 1
role_off; stopapp $P; uncap $HC; th_end
R=POCO-B6
echo "$R hint=$(cnt $HC ${R}_start ${R}_back 'HomeFragment: showing the pages hint') hint_dump=$(uiq count $OUT/ui/$R-hint.xml id:home_pages_hint_got_it)" \
  "key4=$(cnt $HC ${R}_back ${R}_hold 'dispatchKeyEvent: keyCode=4,') closed_back=$(cnt2 $HC ${R}_back ${R}_hold 'HomeFragment: pages hint closed (' 'BACK)')" \
  "after_back_hint=$(uiq count $OUT/ui/$R-afterback.xml id:home_pages_hint_got_it) menu_add_page=$(uiq count $OUT/ui/$R-menu.xml 'text:Add a page')" \
  "closed_left=$(cnt2 $HC ${R}_start ${R}_end 'pages hint closed (' 'LEFT)') closed_gotit=$(cnt2 $HC ${R}_start ${R}_end 'pages hint closed (' 'GOT_IT)')" | tee $OUT/$R-counts.txt
cat $OUT/POCO-W1-attempts.txt $OUT/POCO-W1-wsize.txt
```

**POCO-B6 PASS**: the same conditions as HU-B6, from `POCO-B6-counts.txt`.

**POCO-W1 PASS (B2)**: the same rule as HU-W1's B2 on the compact home, from `POCO-W1-wsize.txt`. `wsize.py` applies the compact ceiling (`window width - 3 x gap - "+" width`). A `frame1` line with `clipped=1` is INCONCLUSIVE (the dump cut the frame). B5 is not graded on D-POCO; record any `CONFIGURE` attempt.

### SAM-HS (optional, last): D-SAM in Headunit Server mode against D-POCO's server, cold then three warm cycles

Skip this run if the round is past 1 h. It is round 2 draft's SAM-HS, cut to the cold cycle and three warm cycles. It needs D-SAM on adb, D-POCO's WiFi on, and hand step 2.

Usage: `bash $OUT/scripts/sam_hs.sh`. One capture on D-SAM and one on D-POCO. The script installs C on D-SAM first.

**`libhs.sh`**
```bash
# SAM-HS only: the server, the LAN path and the version counts
srv_up() { adb -s $P shell cat /proc/net/tcp /proc/net/tcp6 2>/dev/null | grep -acE ':149D [0-9A-F]+:0000 0A'; }
peers() { adb -s $P shell cat /proc/net/tcp /proc/net/tcp6 2>/dev/null | python3 -I $S/peers.py; }
waitrestart() { local f=$1 m=$2 cap=$3 t=0 down=0 n
  while [ $t -lt $cap ]; do
    [ "$(cnt $f $m '' 'Network server running on port')" -ge 1 ] && { echo "restarted after ${t}s (log line)"; return 0; }
    n=$(srv_up); [ "$n" -eq 0 ] && down=1
    [ $down = 1 ] && [ "$n" -ge 1 ] && { echo "restarted after ${t}s (listener)"; return 0; }
    sleep 1; t=$((t+1)); done; echo "no restart in ${cap}s"; return 1; }
ipof() { local ip; ip=$(adb -s $1 shell ip -4 addr show wlan0 2>/dev/null | grep -ao 'inet [0-9.]*' | cut -d' ' -f2)
  [ -n "$ip" ] || ip=$(adb -s $1 shell ifconfig wlan0 2>/dev/null | grep -aoE 'ip [0-9.]+' | cut -d' ' -f2); echo $ip | tr -d '\r'; }
pingpair() { local ip pip a b; ip=$(ipof $1); pip=$(ipof $P)
  a=$(adb -s $1 shell ping -c 4 -W 3 $pip 2>/dev/null | grep -aoE '[0-9]+ (packets )?received' | grep -aoE '^[0-9]+')
  b=$(adb -s $P shell ping -c 4 -W 3 $ip 2>/dev/null | grep -aoE '[0-9]+ (packets )?received' | grep -aoE '^[0-9]+')
  echo "unit=$ip poco=$pip to_poco=${a:-0} from_poco=${b:-0}"; [ "${a:-0}" -ge 1 ] && [ "${b:-0}" -ge 1 ] && echo PING_OK || echo PING_FAILED; }
gaps() { python3 -I $S/gaps.py "$@"; }
hcounts() { local f=$1 a=$2 b=$3
  echo "HU $a: vstart=$(cnt $f $a "$b" 'Handshake: Starting version request.') vreq=$(cnt $f $a "$b" 'Handshake: Version request sent')" \
    "within2s=$(cnt2 $f $a "$b" 'Handshake: No VERSION_RESPONSE within' '2s (attempt 1)') budget=$(cnt2 $f $a "$b" 'Handshake: No VERSION_RESPONSE within' 'the handshake budget (attempt 1)')" \
    "selected=$(cnt $f $a "$b" 'Handshake: the phone selected') ssl=$(ssl $f $a "$b") rend=$(rend $f $a "$b")" \
    "silent=$(cnt2 $f $a "$b" 'AapService: session state ' 'peer_silent') silent_line=$(cnt $f $a "$b" 'the peer accepted the connection and then sent nothing at all')" \
    "reset=$(cnt $f $a "$b" 'ECONNRESET') scan_rx=$(cnt $f $a "$b" 'AapService: Force-starting WIFI-Scan from UI') disc_rx=$(cnt $f $a "$b" 'Disconnect action received.')"; }
pcounts() { local f=$1 a=$2 b=$3
  echo "PH $a: hu_connected=$(cnt $f $a "$b" 'Head unit connected') car_requests=$(cnt $f $a "$b" 'Car requests protocol version')" \
    "critical=$(cnt $f $a "$b" 'Critical error') byecar=$(cnt $f $a "$b" 'PROJECTION_ENDED_BYEBYE_BY_CAR')"; }
```

**`gaps.py`**
```python
# gaps.py <file> <from> <to|-> <A> <B>: for each line with A, the ms to the next line with B before the next A ("NA" when none).
# A label matches only at the end of a line. Clock wrap at midnight is not handled.
import sys, re
f, a, b, pa, pb = sys.argv[1:6]
def ends(l, lab):
    return l.rstrip('\r\n').endswith(': ' + lab)
rows, on = [], False
for l in open(f, errors='replace'):
    if ends(l, a):
        rows, on = [], True
        continue
    if on and b != '-' and ends(l, b):
        on = False
    if on:
        rows.append(l)
out, ta = [], None
for l in rows:
    m = re.match(r'\d\d-\d\d (\d\d):(\d\d):(\d\d)\.(\d{3})', l)
    if not m:
        continue
    t = ((int(m[1]) * 60 + int(m[2])) * 60 + int(m[3])) * 1000 + int(m[4])
    if pa in l:
        if ta is not None:
            out.append('NA')
        ta = t
    elif ta is not None and pb in l:
        out.append(str(t - ta))
        ta = None
if ta is not None:
    out.append('NA')
print(' '.join(out) if out else 'NA')
```

**`peers.py`**
```python
# peers.py: /proc/net/tcp and tcp6 on stdin; prints "<state> <remote ip> <remote port>" for every socket on :5277
# 0A = listen. A wedged server leaves sockets at 08 (CLOSE_WAIT) or 01 (established), never served.
import sys
for l in sys.stdin:
    p = l.split()
    if len(p) < 4 or not p[1].endswith(':149D'):
        continue
    ip, port = p[2].split(':')
    h = ip[-8:]
    print(p[3], '.'.join(str(int(h[i:i + 2], 16)) for i in (6, 4, 2, 0)), int(port, 16))
```

**`sam_hs.sh`**
```bash
H=$D_SAM; PHONES=$D_POCO; . $(dirname $0)/lib.sh; . $(dirname $0)/libhs.sh
R=SAM-HS; HC=$OUT/$R-hu.txt; PC=$OUT/$R-ph.txt
K_SAM="$BASE scalar:int:log-level:1 scalar:int:wifi-connection-mode:1 scalar:boolean:enable-car-launcher:false scalar:int:screen-orientation:2"
th_gate $R || exit 1
hold_off; adb -s $P shell svc wifi enable; sleep 8; adb -s $P shell dumpsys wifi | grep -a -m1 'Wi-Fi is'
adb -s $P shell svc power stayon usb; touch $OUT/poco-stayon-changed
stopapp $D_HP; stopapp $D_HU; H=$P role_off; stopapp $P
echo "listeners $(srv_up)"                                         # 0: hand step 3, then run again
inst $D_SAM | tee $OUT/$R-install.txt; touch $OUT/sam-installed    # MUST show apk_identity exit 0 and commit 0d333b19bef3
pingpair $D_SAM | tee $OUT/$R-ping.txt; tail -1 $OUT/$R-ping.txt | grep -q PING_OK || { sleep 60; pingpair $D_SAM | tee -a $OUT/$R-ping.txt; }
tail -1 $OUT/$R-ping.txt | grep -q PING_OK || { echo "$R UNTESTABLE: no LAN path"; th_end; exit 3; }
stopapp $D_SAM; seed $D_SAM $K_SAM $(idels $D_SAM) | tee $OUT/$R-seed.txt
peers | tee $OUT/$R-peers-before.txt
cap $HC $D_SAM; cap $PC $P
adb -s $P shell log -t RIGMARK ${R}_hand
echo "HAND STEP 2: on D-POCO, Android Auto developer settings: Stop head unit server, then Start head unit server."
waitrestart $PC ${R}_hand 900 | tee $OUT/$R-restart.txt; grep -q '^restarted' $OUT/$R-restart.txt || { echo "$R VOID: no restart seen"; uncap $HC; uncap $PC; th_end; exit 2; }
adb -s $P shell input keyevent KEYCODE_HOME
mark ${R}_c0; launch; LAST=c0; STOP=""
waitfor $HC ${R}_c0 90 'Throughput over' && sleep 6 || STOP="c0 NO_VIDEO"
bye() { mark ${R}_$1_bye; send ACTION_DISCONNECT >/dev/null
  waitfor $HC ${R}_$1_bye 15 'Disconnect action received.'; waitfor $PC ${R}_$1_bye 15 'Head unit disconnected'; sleep 3; }
[ -z "$STOP" ] && bye c0
for k in 1 2 3; do
  [ -n "$STOP" ] && break
  [ "$(cnt2 $HC ${R}_$LAST '' 'AapService: session state ' 'peer_silent')" -ge 1 ] && { STOP="$LAST silent"; break; }
  mark ${R}_c$k; LAST=c$k; send ACTION_START_WIRELESS_SCAN >/dev/null
  if waitfor $HC ${R}_c$k 60 'Throughput over'; then sleep 6; bye c$k; else STOP="c$k NO_VIDEO"; fi
done
echo "$R stop: ${STOP:-none} last=$LAST" | tee $OUT/$R-stop.txt
peers | tee $OUT/$R-peers-after.txt
mark ${R}_end; stopapp $D_SAM; uncap $HC; uncap $PC; th_end
for k in 0 1 2 3; do a=${R}_c$k; n=${R}_c$((k+1)); [ $k = 3 ] && n=${R}_end
  [ "$(grep -ac ": $a\$" <(tr -d '\r' < $HC))" -ge 1 ] || continue
  grep -aq ": $n\$" <(tr -d '\r' < $HC) || n=${R}_end
  hcounts $HC $a $n; pcounts $PC $a $n
  echo "GAPS $a gap_ms=[$(gaps $HC $a $n 'Handshake: Version request sent' 'Handshake: the phone selected')]"
done | tee $OUT/$R-counts.txt
```

**SAM-HS PASS, per cycle** (each `HU` and `PH` line): `ssl` 1 or more and `rend` 1 or more; `vreq` = `vstart` and `vstart` 1 or more; `within2s` = 0; `silent` = 0 and `silent_line` = 0; phone `hu_connected` 1 or more; warm cycles also `scan_rx` 1 or more. **Run PASS** when `c0` to `c3` all PASS. FAIL when a cycle that ran misses a condition, or the run stopped before `c3`. Record every `gap_ms`, `budget`, `reset`, `car_requests`, the restart seconds and the `peers` lines. Call out every connection with `gap_ms` 2000 or more, and whether its cycle reached SSL and video. If none reached 2000 ms, say so: the run then shows the merged build connects, not that it survives a slow answer.

**Stop rule.** The script stops at the first cycle with no video within its cap (90 s cold, 60 s warm) or with `silent` 1 or more. It never retries a cycle.

## 9. Restore

**`restore.sh`**
```bash
. $(dirname $0)/lib.sh
exec > >(tee $OUT/restore.txt) 2>&1
hold_off
for s in $D_HU $D_POCO; do adb -s $s shell cmd role add-role-holder android.app.role.HOME "$(cat $OUT/home-role-$s.txt)"
  echo "$s home role $(adb -s $s shell cmd role get-role-holders android.app.role.HOME | tr -d '\r')"; done
UNITS="$D_HU $D_POCO $D_HP"; [ -f $OUT/sam-installed ] && UNITS="$UNITS $D_SAM"
for s in $UNITS; do
  echo "== $s"; stopapp $s
  adb -s $s install -r -d $OUT/before-$s.apk | tail -1
  $RIG_TOOLKIT/apk_identity.sh $OUT/before-$s.apk $s; echo "apk_identity $s exit $?"
  echo "$s $(push_settings $s $OUT/backup-$s.xml)"
done
adb -s $D_HU shell settings put global animator_duration_scale $HU_ANIM
adb -s $P shell settings put global animator_duration_scale $POCO_ANIM
adb -s $D_HP shell settings put global animator_duration_scale $HP_ANIM
if [ "$HU_BT" = true ]; then adb -s $D_HU shell svc bluetooth enable; else adb -s $D_HU shell svc bluetooth disable; fi
adb -s $P shell svc wifi enable; sleep 5; adb -s $P shell dumpsys wifi | grep -a -m1 'Wi-Fi is'
pbt() { if [ "$POCO_BT" = true ]; then adb -s $P shell svc bluetooth enable; else adb -s $P shell svc bluetooth disable; fi; sleep 3; }
pbt; [ "$(btstate $P)" = "$POCO_BT" ] || pbt; echo "poco bt $(btstate $P) (opening $POCO_BT)"
[ -f $OUT/poco-stayon-changed ] && adb -s $P shell settings put global stay_on_while_plugged_in $POCO_STAYON
[ "$MOTO_WIFI" = enabled ] && adb -s $D_MOTO shell svc wifi enable
[ "$MOTO_BT" = true ] && adb -s $D_MOTO shell svc bluetooth enable
$RIG_TOOLKIT/rig_cleanup.sh list
$RIG_TOOLKIT/rig_preflight.sh D_HU:wifi D_HP:wifi D_POCO:wifi
```

Each unit SHOULD print `apk_identity ... exit 0` and `SETTINGS_BACK`, and each home role SHOULD read the file pre-flight wrote. On `SETTINGS_DIFFER`, put the diff in Setup notes and leave the file; do not loop. If a `before-*.apk` refuses to install, leave C installed and say so. Leave D-POCO's server running.

## 10. Do not re-run

- Everything round 1 graded PASS, or PASS after the regrade: the upgrade, layout, info, Customization, pages add and remove, widget add and remove, Move to Background, Self Mode and the SAM-C runs.
- W7 (Move to Background to this app's home finishes the projection). It is unchanged and older than the PR.
- The onboarding role request on API 29 and later (review N2, tracked apart).
- Widget set-up through a non-exported activity (review N1); the code is the evidence.
- HP-C2. Round 1 found no LAN path from D-HP to D-POCO, and nothing in the fixes touches that session.
- `hus-version-retry` round 1's delay A/B. Do not add a delay on any link.

## 11. Report back

One verdict per run in a table at the top of the results file: HU-B6, HU-W1 (B2), HU-W1 (B5), HU-B4, HU-F1, HU-W16, HU-W8, HP-W12, HP-F1, POCO-B6, POCO-W1, and SAM-HS if it ran. Then:

1. **Cancel (B1, the point):** HU-F1's `x_inwin`, `bare_after_sw2`, `x_line`, `hold_line` and the `AFTER` counts; HP-F1 arm a's `first_search_step`, `pill_x`, `scans_after`; arm b's four `d` lines.
2. **Widget size (B2):** each `frame1` line (`min_px`, `expected`, `bug`, `actual`, `discriminating`), with the provider named.
3. **BACK and the hint (B6):** `closed_back`, `after_back_hint` and `menu_add_page` on D-HU and D-POCO.
4. **The rest:** HU-B4's stored value after the launch; HU-W8's six menu counts; the W16 subtitles; HP-W12's focus lines; B5's attempts.

```decisive-strings
AutomationReceiver: 
AutomationMarker: 
dispatchKeyEvent: keyCode=
HomeFragment: showing the pages hint
HomeFragment: pages hint closed (
HomeFragment: onResume. isConnected=
HomeFragment: the main button is 
MainActivity: status pill step: 
(not shown, the home's main button tells it)
pressed, stopping the bring-up
status pill X
AapService: the status pill's X stopped the wireless bring-up.
NativeAA: Attempting active poke to device
WifiLauncher: Initializing WiFi Mode: 
AapService: Force-starting WIFI-Scan from UI
NetworkDiscovery: Starting scan...
NetworkDiscovery: Found Headunit Server on 
SSL handshake complete
Handshake: SSL handshake complete
Throughput over
rendered=
Media Sink Setup Request: 
Disconnect action received.
cannot be read now, keeping its slot
is gone, showing its slot as free
is no longer installed, showing its slot as free
, releasing its id
was not set up
set up through the host: 
MATCH! Starting AapService
createGroup SUCCESS
WAKING_PHONE
SEARCHING
Handshake: Starting version request.
Handshake: Version request sent
Handshake: No VERSION_RESPONSE within
the handshake budget
Handshake: the phone selected
AapService: session state 
peer_silent
the peer accepted the connection and then sent nothing at all
```

Head unit lines the runs grep in their run-time form. Each is built from a template in the block above or in section 7, so it does not appear in `app/src` as written:

```decisive-strings-runtime
MainActivity: status pill X pressed, stopping the bring-up
HomeFragment: pages hint closed (BACK)
HomeFragment: pages hint closed (LEFT)
HomeFragment: pages hint closed (GOT_IT)
HomeFragment: widget <id> was not set up, releasing its id
HomeFragment: favorite shortcut com.android.settings/rig_b4_probe cannot be read now, keeping its slot
HomeFragment: favorite shortcut com.android.settings/rig_b4_probe is gone, showing its slot as free
HomeFragment: favorite com.rig.b4.absent is no longer installed, showing its slot as free
WifiLauncher: Initializing WiFi Mode: NATIVE
dispatchKeyEvent: keyCode=4,
No VERSION_RESPONSE within 2s (attempt 1)
```

Screen texts and view ids the dumps match, in `app/src/main/res` at `0d333b19`:

```decisive-strings-ui
Waking your phone
Looking for your phone
Searching for your phone
Search again
Cancel
Stop connecting
Add a page
End session, stay ready
Stop Connection
Move to Background
Picture-in-Picture
Quick Settings
Switch Phone
Plug in your phone
home_primary_card
home_primary_title
home_primary_subtitle
home_primary_cancel
home_primary_progress
home_pager
home_pages_hint_got_it
auto_connect_pill
auto_connect_pill_cancel
widget_add_tile
widget_option_label
home_widget_frame
```

Phone-side strings, checked in the decompiled Gearhead 18.0.664204 sources rather than in `app/src` (`RIGMARK` is our own `log -t` tag):

```decisive-strings-phone
PROJECTION_ENDED_BYEBYE_BY_CAR
Critical error
THROTTLE_LIMIT_EXCEEDED
Head unit connected
Head unit disconnected
Car requests protocol version
Network server running on port
RIGMARK
```

Android's own strings, not ours (`ECONNRESET` is exception text; `AllowBindAppWidget` names the system bind dialog's activity in `mCurrentFocus`):

```decisive-strings-system
ECONNRESET
AllowBindAppWidget
mCurrentFocus
```
