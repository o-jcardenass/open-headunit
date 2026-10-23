# pr-1119-home-redesign, round 1 brief

Published name: `pr-1119-home-redesign-round1-brief.md`. Results go in `pr-1119-home-redesign-round1-results.md`.
Evidence: create the release `rig-evidence-pr-1119-home-redesign` and upload `pr-1119-home-redesign-round1-captures.zip`.

Units: every rig unit. Head units D-HU, D-SAM and D-HP, and D-POCO as a head unit in Self Mode. Phones D-MOTO and
D-POCO. Each head unit runs the home checks. Each phone drives at least one connection.

Queue line: `pr-1119-home-redesign-round1-brief.md` (one APK, `11a2c37f` on `fork/review/pr-1119`, a contributor UI
PR that replaces the home screen; units D-HU, D-SAM, D-HP, D-POCO, D-MOTO, in four stages; HU-C1 and HU-C2b are the
point).

Pairing: none. The round uses every unit.

Hand steps: two, both outside this app. (1) Pre-flight: if nothing listens on D-POCO's `:5277`, the operator turns
"Start head unit server" on in Android Auto's developer settings on D-POCO. (2) Pre-flight: if D-MOTO is behind a PIN,
the operator unlocks it once. Contingency only: if D-POCO never opens the Android Auto channel to D-SAM, the operator
forgets D-HU's radio on D-POCO (`rig-quirks/units/D-POCO.md`).

**Injected gestures: the cap is 5 per run, and it counts long presses and swipes as taps.** This PR is a UI PR. Pages,
widgets, the main button, the bar tiles and Move to Background have no `AutomationReceiver` verb. So the runs use
dumped-target gestures, and the runs are split so that no run needs more than 5. Every gesture resolves its target by
resource id (or by exact text on D-HP, which has no resource ids at API 17) in a fresh `uiautomator dump`, asserts
exactly one match, logs the coordinates and counts against the budget (`gest` in section 5). A target that does not
resolve voids that bullet. Never guess a coordinate. No run searches, scrolls or types.

**Time budget.** Pre-flight 15 min, build 25 min, stage A (D-HU) 60 min, stage B (D-SAM) 35 min, stage C (D-HP)
30 min, stage D (D-POCO) 45 min, thermal waits and stage changes about 30 min. **About 4 h in all.** The stages are
independent: two sessions of about 2 h each work (A and B, then C and D).

| Stage | Head unit | Phone | Cabled | Runs (gestures) |
|---|---|---|---|---|
| A | D-HU | D-MOTO, then D-POCO | D-HU, D-MOTO, D-POCO | HU-U, HU-CUST, HU-LAY-L, HU-LAY-R, HU-INFO, HU-C2a (1), HU-L1 (3), HU-L2 (2 or 3), HU-L3 (4 or 5), HU-C1 (2), HU-C2b (2), HU-C3 (2) |
| B | D-SAM | D-POCO | D-SAM, D-POCO, D-HU | SAM-U, SAM-CUST, SAM-LAY-L, SAM-LAY-R, SAM-LAY-P, SAM-INFO, SAM-LN, SAM-C1 (1), SAM-C2 (1) |
| C | D-HP | D-POCO (head unit server) | D-HP, D-POCO | HP-U, HP-LAY-L, HP-LAY-R, HP-LAY-P, HP-INFO, HP-LN, HP-C2 (1 or 2) |
| D | D-POCO | itself (Self Mode) | D-POCO | POCO-U, POCO-CUST, POCO-LAY-P, POCO-LAY-PR, POCO-LAY-W, POCO-INFO, POCO-L1 (3), POCO-L2 (2 or 3), POCO-L3 (4 or 5), POCO-S1 (3), POCO-S2 (0) |

Four USB ports: D-HU, D-POCO, D-MOTO and D-SAM fit at once for stages A and B. In stage C, unplug D-SAM and plug
D-HP in. D-HU stays cabled in stage B only to hold its Bluetooth off.

## 1. Build and baseline

One APK, from the PR head as the contributor pushed it. No baseline build: the upgrade runs start from the build each
unit has now, which is the round's "current release" (section 3 says why). No history was rewritten.

| Arm | Ref | Commit | Tree |
|---|---|---|---|
| C (candidate) | `fork/review/pr-1119` | `11a2c37fd244024444c06e78c447ca3a2190a041` | `fbb9eb36b917968e49705165c03e824a8426ba8b` |

`review/pr-1119` goes to `fork` after this brief is approved. If `git fetch` does not find it, stop and ask. Do not
build from the contributor's repository. The PR head is 50 commits on merge base `fbeb46fa` and carries
`versionName` 4.0.0-alpha, `versionCode` 120.

The dev host ran the JVM suite at `11a2c37f`: 3531 tests, 0 failures. Do not run the unit tests on the rig.

**get_apk.sh**

```bash
OUT=rig-data/rounds/pr-1119-home-redesign-round1; mkdir -p $OUT/ui
WT=$(ls -d worktrees/* 2>/dev/null | head -1)        # any existing worktree; else create one with $RIG_TOOLKIT/wt-new.sh
git -C $WT fetch fork review/pr-1119
git -C $WT checkout --detach 11a2c37fd244024444c06e78c447ca3a2190a041
git -C $WT rev-parse 'HEAD^{tree}'                    # MUST print fbb9eb36b917968e49705165c03e824a8426ba8b
$RIG_TOOLKIT/rig_thermal.sh wait 70
HUR_DIR=$WT MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh   # copy the APK out of apks/ to $OUT/apk.apk at once
md5sum $OUT/apk.apk                                   # record in Setup notes
unzip -p $OUT/apk.apk 'classes*.dex' | strings | grep -c HomePagesHintPolicy   # MUST be 1 or more
```

The APK is installed on D-HU, D-SAM, D-HP and D-POCO, each inside its own `HU-U`/`SAM-U`/`HP-U`/`POCO-U` run, never
before. D-MOTO gets no APK.

Identity on each unit after its install:

- `send ACTION_QUERY_STATE`: the `data=` line MUST carry commit `11a2c37fd244` (a `-dirty` suffix from a copied
  `local.properties` is fine; say so).
- Pull the live APK and run `unzip -p live.apk 'classes*.dex' | strings | grep -c HomePagesHintPolicy`. It MUST be
  1 or more. The class is new in this PR, so no earlier build has it.
- If `adb install -r -d` answers `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, the unit carries a build with another
  signature (for example the contributor's release APK). Stop and ask. Never uninstall.

## 2. What this is and why

PR #1119 replaces the home screen. The classic grid of equal buttons is removed for every user. The new home has:

- a **driver bar** on the driver's side with the chosen connections, Settings and Exit (a row along the bottom in
  portrait, mirrored with right-hand drive);
- one **main button** that names the connection, its state and one action ("Connect over WiFi", "Back to Android
  Auto", ...). While a connection comes up it shows the step, a progress bar and Cancel. Cancel calls the same
  `MainActivity.cancelBringUp` as the status pill's X, so the service holds the stack down
  (`WirelessCancelPolicy`). While the home is on screen the status pill is hidden (`homeShowsBringUp`);
- **Connecting** and **Connected** on a bar tile. The bar carries the main button's own connection only when the
  home has more than one page (`HomePagesPolicy.barCarriesEveryConnection`). So a tile says Connecting or Connected
  only on a multi-page home, which needs launcher mode on API 21 or later. The PR says the same for Android 4.4;
- in **launcher mode** (the app is the unit's home app, or `enable-car-launcher` is on): pages (swipe, add with a
  long press, up to four), a favorites row with All apps, widgets (API 21 and later), a one-time pages hint, Home
  returning to page 1, and a short exit menu in the projection (no Picture-in-Picture, no Quick Settings);
- **Move to Background** names this app's own home when this app is the home app, so the factory launcher does not
  come forward. `MainActivity.onResume` no longer raises the projection when this app is the home app;
- **Picture-in-Picture** offered only where the unit declares `android.software.picture_in_picture`;
- date, time and GPS speed switches in Customization, off by default.

What can go wrong on a real head unit, and what the round checks:

- **Clipping.** The Exit tile can drop off a short screen, and the main button's action can be cut. Graded from dump
  bounds on every head unit at its native size, with right-hand drive and in portrait where the unit can turn.
- **Cancel that does not stop the attempt, or no Cancel at all.** Our review (Block 1) found that the pill is hidden
  whenever the home is in front, but the home's Cancel shows only for some states. Predicted gaps: a page without the
  main button, and Headunit Server discovery (`SEARCHING`). HU-F1 and HP-F1 measure both.
- **A session lost to Move to Background**, the factory launcher in front, or something over the favorites row.
- **Settings lost on upgrade.** The classic home is removed, so a user of the classic home gets the new one on the
  next start. `custom-settings-button-color` is no longer read; the three connection colours must stay.
- **PiP offered where the unit refuses it.**

Two review warnings the round records but does not grade: Move to Background starts `MainActivity` with only
`FLAG_ACTIVITY_NEW_TASK`, which may finish the projection activity (Warn 8), and widget sizes may be doubled above
160 dpi (Block 2 and 3).

## 3. What is different about this round

- **"Current release" is the build each unit has now.** A release APK from GitHub carries the release signature, and
  installing a debug build over it needs an uninstall, which wipes the settings the upgrade check is about. So each
  `*-U` run records the installed build's commit (`ACTION_QUERY_STATE`) and proves from a dump that it shows the
  classic home (`main_buttons_layout` present). If a unit does not show the classic home, its `-U` run is
  INCONCLUSIVE, not FAIL.
- **Animations off for the round.** The progress light and the tile light are endless animations, and `uiautomator
  dump` cannot get an idle state while they run. Pre-flight saves each head unit's `animator_duration_scale` and sets
  it to 0. The PR keeps the light still when animations are off, so the light itself is not checked (a screen detail
  only an eye can grade).
- **The HOME role is changed on D-HU and D-POCO, and given back.** Launcher runs make this app the home app with
  `cmd role add-role-holder`. While this app holds the role, a force-stop makes Android start it again at once. So the
  helpers give the role back to the factory launcher (`role_off`) before every force-stop and settings write, and take
  it again after the launch (`role_on`). Pre-flight saves the original holder.
- **D-HU is not rotated** (operator instruction, `rig-quirks/topics/projection.md`). Portrait is N/A on D-HU.
- **D-HU's GLES surface survives only screen-off** (`rig-dhu-gles-surface-survives-only-screen-off`). Move to
  Background takes the projection's surface down, so `Decoder stopped: ` and a new surface on the return are expected.
  Do not grade them. The session is graded on the AAP link and the phone instead.
- **`shared_prefs` may be root-owned** (`rig-quirks/units/D-HU.md`). The launcher runs read keys the app writes
  (`home-widget-pages`, `home-pages-hint-shown`). Pre-flight `stat`s the directory on every head unit and `chown`s it
  to the app's uid:gid when it is wrong. Report what `stat` read, also when it is correct.
- **D-POCO's head unit server may be down** (`rig-dpoco-headunit-server-down`). HP-C2, POCO-S1 and POCO-S2 need it.
  Never force-stop Gearhead on any phone: that drops the server. Check Gearhead with `ps -A | grep gearhead`, not
  `pidof` (`rig-gearhead-liveness-and-throttle`).
- **D-POCO is a phone in stages A to C and a head unit in stage D.** Send `headunit://exit` to its app only if the app
  runs (`pidof`), then force-stop it, before stage A and at the end of stage D.
- **D-POCO's WiFi turns itself back on** after one `svc wifi disable`. In stage A runs that do not use D-POCO, a 3 s
  hold loop keeps it off (`hold_on`), because D-HU's persistent group lets a known phone rejoin on WiFi alone.
- **The audio settings are a deliberate worst case.** Do not touch `use-aac-audio`, `audio-latency-multiplier` or
  `audio-queue-capacity`. Record `video-profile-starvation-cap`; nothing here depends on it.
- **The rig unit's locale decides the labels.** Pre-flight reads it. Section 6 gives the English and Spanish label
  for every text the runs match. If a unit runs another locale, take the label from
  `app/src/main/res/values-<lang>/strings.xml` at `11a2c37f` and say so.
- **On mode 3 the launch arms the stack.** No `ACTION_START_WIRELESS_SCAN` straight after a launch. The runs send it
  only to lift a hold, long after the launch.
- **A phone leaves the link by `svc`**, not airplane mode.

### Steps graded N/A, by device, decided before the round

| Step | D-HU (API 34) | D-SAM (API 19) | D-HP (API 17) | D-POCO as head unit (API 35) |
|---|---|---|---|---|
| Portrait layout | N/A: operator instruction | run (`screen-orientation` 4) | run | run (native portrait); landscape run too |
| Pages: add, remove, swipe, pages hint | run | N/A: no page can be added below API 21, and the hint needs API 21 | N/A, same | run |
| Widgets: add, remove | run | N/A: widgets need API 21 | N/A, same | run |
| Home key returns to page 1 | run | N/A: no role manager; the default home needs a system chooser, and there is no added page | N/A, same | run |
| Move to Background into this app's home | run | N/A: this app cannot be made the home app by script below API 29 | N/A, same | run |
| Connecting tile | run (two pages) | N/A: one page only (the PR says the same for 4.4) | N/A, same | N/A: Self Mode reports no steps |
| Connected tile, Back to Android Auto on the home | run | N/A: not the home app, so `MainActivity` raises the projection by design | N/A, same | run |
| Cancel during a wake | run | run | N/A: mode 1 sends no wake; F1 runs instead | N/A: Self Mode has no bring-up steps |
| PiP row in the exit menu | by feature | never offered: API 19 is below 24 | never offered: API 17 | by feature |
| Speed with the permission revoked | run | N/A: install-time permission | N/A, same | run |
| Speed switch asks for the permission | N/A on every unit: the switch is below the first screen of Customization, and the round does not scroll. `CustomizationFragment` launches the request on enable; the code is the evidence |
| Card colour pixel check | run | N/A: the screenshot is portrait while the app is landscape | N/A, same | run |
| Customization colour preview (record only) | run | run (`run-as am start`) | N/A: Settings cannot be reached by script (`rig-quirks/units/D-HP.md`) | run |
| TalkBack reading the hint | N/A on every unit: a screen reader cannot be graded from a script |
| Connection | Native AA, D-MOTO and D-POCO | Native AA, D-POCO | Headunit Server, D-POCO, only with `:5277` up | Self Mode, only with `:5277` up |

**Expected INCONCLUSIVE:** a `-U` run on a unit that does not show the classic home; the "Connecting" dump bullet of
HU-C2b and HU-C3 when the bring-up is shorter than one dump (HU-C1 covers that tile); HP-C2, POCO-S1 and POCO-S2 if
`:5277` stays down after the hand step.

**Pre-registered expectations from the review:** HU-F1 and HP-F1 are predicted to find no Cancel and no pill on
screen while a stage is up. That is a FAIL of the PR's promise, and the round exists partly to measure it.

## 4. Settings keys

Write keys with `$RIG_TOOLKIT/set_hu_settings_host.py` on D-HU (rooted) and `$RIG_TOOLKIT/set_prefs_runas_host.py` on
D-SAM, D-HP and D-POCO, always with the app stopped (and with the HOME role given back first, section 3). `TOOLS.md`
gives the arguments, the `set:` form for `connection-modes` and the delete form. Read every key back before the
launch. Each run starts from the unit's pre-flight backup (`$OUT/<U>-backup.xml`, restored by `restore`), then writes
its keys.

**Base, every run on every head unit:**

**base.xml**

```xml
<int name="onboarding-version" value="2" />
<boolean name="rename_notice_shown_v2" value="true" />
<boolean name="aa174_notice_shown" value="true" />
<int name="native-driver-selection-mode" value="0" />
<int name="log-level" value="2" />
<int name="app-theme" value="1" />
```

Also delete every key whose name starts with `connection-issue-`, so no banner holds the pages hint back. `app-theme`
1 is the day look, where the whole main button wears its connection's colour.

**Unit base:** D-HU adds `<int name="native-ap-transport" value="0" />` and
`<boolean name="external-bt-zbt-transport" value="false" />`. D-SAM keeps `screen-orientation` 2 (its mount) except in
SAM-LAY-P.

**Per run** (the run sections name the sets they use; when two sets name one key, the later set wins):

| Set | Keys |
|---|---|
| `K_LAY` | `wifi-connection-mode` 0; `connection-modes` = `usb,wifi,self`; `enable-car-launcher` false |
| `K_RHD` | `right-hand-drive` true (absent or false otherwise) |
| `K_PORT` | `screen-orientation` 4 |
| `K_LAND` | `screen-orientation` 2 |
| `K_INFO` | `show-home-clock` true; `show-home-speed` true |
| `K_LN` | `enable-car-launcher` true; `home-favorite-apps` = `com.android.settings,,,` (string) |
| `K_L` | `K_LN`, plus delete `home-pages-hint-shown`, `home-widget-pages`, `home-widgets` |
| `K_CONN` | `wifi-connection-mode` 3 (D-HU, D-SAM) or 1 (D-HP); `connection-modes` = `usb,wifi,self`; `native-poke-all-paired` true |
| `K_CL` | `K_CONN`, plus `enable-car-launcher` true, `home-widget-pages` = `p` (string: one empty added page) and `home-pages-hint-shown` true (the hint would cover the home once the stack is idle) |
| `K_COL` | `custom-usb-button-color`, `custom-wifi-button-color`, `custom-self-mode-button-color`, all int -1754827 (`#E53935`) |
| `K_SELF` | `wifi-connection-mode` 0; `connection-modes` = `self` |

## 5. Helpers

Save every script block below under `$OUT/scripts/` (`extract_brief_blocks.py` does it by the bold name). Run every
adb call in sequence, never two at once against one unit (the hold loop targets a phone that no other call uses at
that time). Write the results file after every run. Set `D`, `U`, `PH`, `PHCAP` and `LANGU` as each stage
says.

**lib.sh**

```bash
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
MAIN=$PKG/com.andrerinas.openheadunit.main.MainActivity
S_HU=27870808938846; S_POCO=4f4027e9; S_MOTO=ZY22GC3BM4; S_SAM=30041c35642d2200; S_HP=CNU350BGBJ
OUT=rig-data/rounds/pr-1119-home-redesign-round1; mkdir -p $OUT/ui
UIQ=$OUT/scripts/uiq.py; KEYS=$OUT/scripts/keys.py; APK=$OUT/apk.apk
export LC_ALL=C
send() { a=$1; shift; adb -s $D shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; }
# marker on the head unit (AutomationMarker) and on the run's phone (RIGMARK)
mark() { send ACTION_LOG_MARKER --es text "$1" >/dev/null; sleep 0.3
  if [ -n "$PH" ] && [ "$PH" != "$D" ]; then adb -s $PH shell log -t RIGMARK "$1"; fi
  echo "$1 $(date '+%m-%d %H:%M:%S')" >> $OUT/marks.txt; }
launch() { adb -s $D shell am start -n $MAIN >/dev/null; }
appexit() { adb -s $D shell am start -a android.intent.action.VIEW -d "headunit://exit" >/dev/null; sleep 5; }
# windows between <run>-start and <run>-end; the label is unique, so ": <label>" finds both marker forms
win()  { sed -n "/: $1-start/,/: $1-end/p" $HUCAP; }
pwin() { sed -n "/: $1-start/,/: $1-end/p" $PHCAP; }
# lines between two markers of one run: span <from> <to> [file]
span() { sed -n "/: $1\$/,/: $2\$/p" ${3:-$HUCAP} | tr -d '\r'; }
cnt()  { win $1 | grep -acF -- "$2"; }
# lines after the last copy of a marker, and capped waits on them
after() { local n; n=$(grep -anF ": $1" $HUCAP | tail -1 | cut -d: -f1); [ -n "$n" ] && tail -n +$n $HUCAP; }
acnt() { after $1 | grep -acF -- "$2"; }
acnt2() { after $1 | grep -aF -- "$2" | grep -acF -- "$3"; }
waitn() { local m=$1 s=$2 n=$3 cap=$4 t=0
  while [ $t -lt $cap ]; do [ "$(acnt $m "$s")" -ge $n ] && return 0; sleep 1; t=$((t+1)); done; return 1; }
waitn2() { local m=$1 s=$2 s2=$3 n=$4 cap=$5 t=0
  while [ $t -lt $cap ]; do [ "$(acnt2 $m "$s" "$s2")" -ge $n ] && return 0; sleep 1; t=$((t+1)); done; return 1; }
HOMELINE="(not shown, the home's main button tells it)"

# host thermal (rig-quirks/topics/tooling.md)
th_gate() { $RIG_TOOLKIT/rig_thermal.sh wait 75 || $RIG_TOOLKIT/rig_thermal.sh wait 75 || { echo HOST_HOT; return 1; }
  $RIG_TOOLKIT/rig_thermal.sh watch $OUT/thermal-$1.log & THPID=$!; }
th_end() { kill $THPID 2>/dev/null; wait $THPID 2>/dev/null; }

# one dump, one screenshot (and a raw frame on API 21+); a failed dump leaves no stale file behind
uidump() { local l=$1; adb -s $D shell rm -f /sdcard/ohu-ui.xml; sleep 0.3
  adb -s $D shell uiautomator dump /sdcard/ohu-ui.xml >/dev/null 2>&1; sleep 0.3
  adb -s $D pull /sdcard/ohu-ui.xml $OUT/ui/$l.xml >/dev/null 2>&1; sleep 0.3
  adb -s $D shell screencap -p /sdcard/ohu-ui.png; sleep 0.3
  adb -s $D pull /sdcard/ohu-ui.png $OUT/ui/$l.png >/dev/null 2>&1
  [ -s $OUT/ui/$l.xml ] || echo "$l DUMP_FAILED" | tee -a $OUT/marks.txt; }
raw() { adb -s $D exec-out screencap > $OUT/ui/$1.raw; sleep 0.3; }
focus() { adb -s $D shell dumpsys window | grep -a -m1 mCurrentFocus | tee -a $OUT/focus.txt; }

# gest <run> <tap|long|next> <selector> <dump-label>: one gesture on the one dumped match, budget 5 per run
gest() { local run=$1 kind=$2 sel=$3 xml=$OUT/ui/$4.xml n xy x y l t r b
  n=$(cat $OUT/taps-$run 2>/dev/null || echo 0)
  [ "$n" -ge 5 ] && { echo "$run BUDGET_SPENT" | tee -a $OUT/marks.txt; return 3; }
  case $kind in
    next) xy=$(python3 -I $UIQ box $xml "$sel") || { echo "$run $sel NO_TARGET $xy" | tee -a $OUT/marks.txt; return 2; }
          read l t r b <<< "$xy"; x=$((l+(r-l)*8/10)); y=$(((t+b)/2))
          adb -s $D shell input swipe $x $y $((l+(r-l)*2/10)) $y 300 ;;
    *)    xy=$(python3 -I $UIQ center $xml "$sel") || { echo "$run $sel NO_TARGET $xy" | tee -a $OUT/marks.txt; return 2; }
          read x y <<< "$xy"
          if [ $kind = long ]; then adb -s $D shell input swipe $x $y $x $y 1200; else adb -s $D shell input tap $x $y; fi ;;
  esac
  echo $((n+1)) > $OUT/taps-$run
  echo "$run $kind $sel $x $y $(date +%T)" | tee -a $OUT/gestures.log; sleep 2; }
# long press on a point the reader computed (the widget cell): gestxy <run> <x> <y>
gestxy() { local run=$1 n; n=$(cat $OUT/taps-$run 2>/dev/null || echo 0)
  [ "$n" -ge 5 ] && { echo "$run BUDGET_SPENT" | tee -a $OUT/marks.txt; return 3; }
  adb -s $D shell input swipe $2 $3 $2 $3 1200; echo $((n+1)) > $OUT/taps-$run
  echo "$run long xy $2 $3 $(date +%T)" | tee -a $OUT/gestures.log; sleep 2; }

# phone radios (svc, never airplane mode); read both back
ph_off() { adb -s $1 shell svc wifi disable; sleep 0.3; adb -s $1 shell svc bluetooth disable; sleep 4
  adb -s $1 shell dumpsys wifi | grep -a -m1 "Wi-Fi is"; adb -s $1 shell dumpsys bluetooth_manager | grep -a -m1 -E "^ *state:|enabled:"; }
ph_on()  { adb -s $1 shell svc wifi enable; sleep 0.3; adb -s $1 shell svc bluetooth enable; sleep 4
  adb -s $1 shell dumpsys wifi | grep -a -m1 "Wi-Fi is"; adb -s $1 shell dumpsys bluetooth_manager | grep -a -m1 -E "^ *state:|enabled:"; }
# D-POCO's WiFi turns itself back on: hold it off every 3 s while another phone is under test
hold_on()  { ( while :; do adb -s $S_POCO shell svc wifi disable >/dev/null 2>&1; sleep 3; done ) & echo $! > $OUT/hold.pid; }
hold_off() { kill $(cat $OUT/hold.pid 2>/dev/null) 2>/dev/null; rm -f $OUT/hold.pid; }

# settings: back up once per unit in pre-flight; restore before every run (app stopped, role given back)
pull_settings() { if [ $D = $S_HU ]; then adb -s $D shell su -c "cat /data/data/$PKG/shared_prefs/settings.xml"
  else adb -s $D shell run-as $PKG cat shared_prefs/settings.xml; fi > $1; }
restore() { adb -s $D shell am force-stop $PKG; sleep 1
  restore_file $OUT/$U-backup.xml          # the unit's own method, below
  pull_settings $OUT/$U-readback.xml; python3 -I $KEYS $OUT/$U-backup.xml $OUT/$U-readback.xml; }

# the HOME role (D-HU and D-POCO only); pre-flight wrote the factory holder to $OUT/home-role-$U.txt
role_on()  { adb -s $D shell cmd role add-role-holder android.app.role.HOME $PKG; sleep 1
  adb -s $D shell cmd role get-role-holders android.app.role.HOME | tee -a $OUT/role.log; }
role_off() { adb -s $D shell cmd role add-role-holder android.app.role.HOME "$(cat $OUT/home-role-$U.txt)"; sleep 1
  adb -s $D shell cmd role get-role-holders android.app.role.HOME | tee -a $OUT/role.log; }

# captures, one per stage: head unit, and each phone
cap() { local f=$1 s=$2; adb -s $s logcat -G 16M 2>/dev/null; adb -s $s logcat -c
  stdbuf -oL adb -s $s logcat -v time > $f & echo $! > $f.pid; }
uncap() { kill $(cat $1.pid) 2>/dev/null; }
# D-HP's capture can die silently: check it every 60 s and restart it with >> (rig-quirks/units/D-HP.md)
capwatch() { ( while :; do sleep 60; kill -0 $(cat $1.pid) 2>/dev/null || { stdbuf -oL adb -s $2 logcat -v time >> $1 & echo $! > $1.pid; echo "capture restarted $(date +%T)" >> $OUT/marks.txt; }; done ) & echo $! > $1.watch; }
```

Define `restore_file <xml>` once per unit with the method that unit takes, because it differs and the rig owns it.
D-HU: copy the file into `shared_prefs/` as root, `chown` it to the uid:gid pre-flight read and `chmod 660`
(`rig-quirks/units/D-HU.md`). D-SAM, D-HP and D-POCO: write it back with `set_prefs_runas_host.py`, or push it and
`run-as $PKG cp` it (`rig-quirks/units/D-SAM.md`). The `keys.py` line that `restore` prints MUST show `removed`,
`changed` and `added` all empty before the run writes its own keys (`keys.py` strips CRs itself).

**uiq.py** (reads a dump; `layout` grades E1 to E5, `color` grades the card colour)

```python
#!/usr/bin/env python3
"""Read a uiautomator dump for the pr-1119 round.

  uiq.py find   <xml> <sel>                    one JSON line per match
  uiq.py center <xml> <sel>                    "x y" of the one match; exit 2 if not exactly one
  uiq.py layout <xml> <lhd|rhd|port> <home|launcher> [en|es]
  uiq.py box    <xml> <sel>                    "l t r b" of the one match; exit 2 if not exactly one
  uiq.py prevcell <xml> <land|port>            "x y" of the cell before the one widget_add_tile
  uiq.py clear  <xml> <sel> <sel>...           overlap in px2 of the first sel with each other sel
  uiq.py color  <raw> <xml> <rrggbb>           card colour against the expected one

sel is id:<name>, idfirst:<name> (the first match only), text:<exact> or textin:<part>. A node's label is its own text, or the text of
its descendants joined by " | " when it has none (a bar tile keeps its label in a child).
"""
import json
import re
import struct
import sys
import xml.etree.ElementTree as ET

LABELS = {
    "en": {"exit": "Exit", "settings": "Settings",
           "actions": ["Back to Android Auto", "Connect now", "Search again", "Connect over USB",
                       "Connect over WiFi", "Start Self Mode"]},
    "es": {"exit": "Salir", "settings": "Configuración",
           "actions": ["Volver a Android Auto", "Conectar ahora", "Buscar de nuevo", "Conectar por USB",
                       "Conectar por Wi-Fi", "Iniciar Modo Autónomo"]},
}
TILES = ["usb_button", "wifi_button", "self_mode_button", "settings_button", "exit_button"]
TOL = 2


def bounds(node):
    m = re.match(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]", node.get("bounds", ""))
    return [int(v) for v in m.groups()] if m else None


def label(node):
    own = node.get("text", "")
    if own:
        return own
    return " | ".join(n.get("text") for n in node.iter("node") if n is not node and n.get("text"))


def load(path):
    root = ET.parse(path).getroot()
    nodes = list(root.iter("node"))
    return nodes[0] if nodes else None, nodes


def match(nodes, sel):
    kind, _, val = sel.partition(":")
    out = []
    for n in nodes:
        rid = n.get("resource-id", "")
        if kind in ("id", "idfirst") and rid.endswith(":id/" + val):
            out.append(n)
        elif kind == "text" and n.get("text", "") == val:
            out.append(n)
        elif kind == "textin" and val in n.get("text", ""):
            out.append(n)
    out = [n for n in out if bounds(n) and bounds(n)[2] > bounds(n)[0] and bounds(n)[3] > bounds(n)[1]]
    return out[:1] if kind == "idfirst" else out


def info(n):
    return {"id": n.get("resource-id", "").split(":id/")[-1], "text": label(n), "bounds": bounds(n),
            "enabled": n.get("enabled"), "clickable": n.get("clickable")}


def inside(a, b):
    return a[0] >= b[0] - TOL and a[1] >= b[1] - TOL and a[2] <= b[2] + TOL and a[3] <= b[3] + TOL


def overlap(a, b):
    return max(0, min(a[2], b[2]) - max(a[0], b[0])) * max(0, min(a[3], b[3]) - max(a[1], b[1]))


def one(nodes, sel):
    m = match(nodes, sel)
    return bounds(m[0]) if len(m) == 1 else None, len(m)


def layout(path, case, mode, lang):
    root, nodes = load(path)
    screen = bounds(root)
    by_id = any(n.get("resource-id") for n in nodes)
    r = {"screen": screen, "by_id": by_id, "case": case, "mode": mode}
    lab = LABELS[lang]
    if by_id:
        card, r["card_count"] = one(nodes, "id:home_primary_card")
        action_m = match(nodes, "id:home_primary_action")
        bar, r["bar_count"] = one(nodes, "id:home_driver_bar")
        exit_b, r["exit_count"] = one(nodes, "id:exit_button")
        sett, r["settings_count"] = one(nodes, "id:settings_button")
        title_m = match(nodes, "id:home_primary_title")
    else:
        action_m = [n for n in nodes if n.get("text") in lab["actions"]]
        card = bounds(action_m[0]) if len(action_m) == 1 else None
        r["card_count"] = len(action_m)
        exits = [n for n in nodes if n.get("text", "").startswith(lab["exit"][:4])]
        setts = [n for n in nodes if n.get("text", "").startswith(lab["settings"][:5])]
        exit_b = bounds(exits[0]) if len(exits) == 1 else None
        sett = bounds(setts[0]) if len(setts) == 1 else None
        r["exit_count"], r["settings_count"] = len(exits), len(setts)
        boxes = [b for b in (exit_b, sett) if b]
        bar = [min(b[0] for b in boxes), min(b[1] for b in boxes), max(b[2] for b in boxes),
               max(b[3] for b in boxes)] if boxes else None
        r["bar_count"] = 1 if bar else 0
        title_m = []
    action = bounds(action_m[0]) if len(action_m) == 1 else None
    r["action_count"] = len(action_m)
    r["action_text"] = label(action_m[0]) if len(action_m) == 1 else None
    # E1: the Exit tile is whole and on screen (home mode), or absent (launcher mode, by design).
    if mode == "home":
        r["E1"] = bool(exit_b and sett and inside(exit_b, screen)
                       and (exit_b[3] - exit_b[1]) >= 0.9 * (sett[3] - sett[1])
                       and (exit_b[2] - exit_b[0]) >= 0.9 * (sett[2] - sett[0]))
    else:
        r["E1"] = r["exit_count"] == 0
    # E2: the main button's action is on screen, inside the card, with text.
    r["E2"] = bool(action and card and inside(action, screen) and inside(action, card) and r["action_text"])
    # E3: the bar is on the driver's side, or under the card in portrait.
    if bar and card:
        r["E3"] = {"lhd": bar[2] <= card[0] + TOL, "rhd": bar[0] >= card[2] - TOL,
                   "port": bar[1] >= card[3] - TOL}[case]
    else:
        r["E3"] = False
    # E4: no two bar tiles overlap (needs ids).
    if by_id:
        tiles = [(t, bounds(m[0])) for t in TILES for m in [match(nodes, "id:" + t)] if len(m) == 1]
        r["tiles"] = {t: b for t, b in tiles}
        r["E4"] = all(overlap(a[1], b[1]) == 0 for i, a in enumerate(tiles) for b in tiles[i + 1:])
        r["E5"] = bool(len(title_m) == 1 and label(title_m[0]) and card and inside(bounds(title_m[0]), card))
        fav, r["favorites_count"] = one(nodes, "id:home_favorites")
        r["favorites_inside_and_clear"] = bool(fav and inside(fav, screen) and card and overlap(fav, card) == 0) if fav else None
        r["info_count"] = len(match(nodes, "id:home_info"))
        r["pill_count"] = len(match(nodes, "id:auto_connect_pill"))
    else:
        r["E4"] = None
        r["E5"] = None
    graded = [r["E1"], r["E2"], r["E3"]] + ([r["E4"], r["E5"]] if by_id else [])
    r["PASS"] = all(graded)
    return r


def color(raw_path, xml_path, expect):
    root, nodes = load(xml_path)
    screen = bounds(root)
    card, count = one(nodes, "id:home_primary_card")
    data = open(raw_path, "rb").read()
    w, h, _fmt = struct.unpack("<III", data[:12])
    header = len(data) - w * h * 4
    out = {"raw": [w, h], "header": header, "screen": screen, "card": card}
    if card is None or header not in (12, 16) or (w > h) != (screen[2] - screen[0] > screen[3] - screen[1]):
        out["result"] = "NA"
        return out
    x = card[2] - 6
    seen = {}
    for k in range(3, 8):
        y = card[1] + (card[3] - card[1]) * k // 10
        o = header + (y * w + x) * 4
        px = "%02x%02x%02x" % (data[o], data[o + 1], data[o + 2])
        seen[px] = seen.get(px, 0) + 1
    dom = max(seen, key=seen.get)
    want = [int(expect[i:i + 2], 16) for i in (0, 2, 4)]
    got = [int(dom[i:i + 2], 16) for i in (0, 2, 4)]
    out.update({"samples": seen, "dominant": dom, "expect": expect,
                "result": "MATCH" if all(abs(a - b) <= 12 for a, b in zip(want, got)) else "DIFFERENT"})
    return out


def main(argv):
    cmd = argv[1]
    if cmd == "find":
        _, nodes = load(argv[2])
        for n in match(nodes, argv[3]):
            print(json.dumps(info(n), ensure_ascii=False))
        return 0
    if cmd == "center":
        _, nodes = load(argv[2])
        m = match(nodes, argv[3])
        if len(m) != 1:
            print("MATCHES=%d" % len(m))
            return 2
        b = bounds(m[0])
        print((b[0] + b[2]) // 2, (b[1] + b[3]) // 2)
        return 0
    if cmd == "box":
        _, nodes = load(argv[2])
        m = match(nodes, argv[3])
        if len(m) != 1:
            print("MATCHES=%d" % len(m))
            return 2
        print(*bounds(m[0]))
        return 0
    if cmd == "prevcell":
        _, nodes = load(argv[2])
        m = match(nodes, "id:widget_add_tile")
        if len(m) != 1:
            print("MATCHES=%d" % len(m))
            return 2
        l, t, r, b = bounds(m[0])
        if argv[3] == "land":
            print(l - (r - l) // 2, (t + b) // 2)
        else:
            print((l + r) // 2, t - (b - t) // 2)
        return 0
    if cmd == "clear":
        _, nodes = load(argv[2])
        base = match(nodes, argv[3])
        if len(base) != 1:
            print(json.dumps({"base": argv[3], "matches": len(base)}))
            return 0
        res = {}
        for sel in argv[4:]:
            res[sel] = sum(overlap(bounds(base[0]), bounds(n)) for n in match(nodes, sel))
        print(json.dumps({"base": argv[3], "overlap": res}))
        return 0
    if cmd == "layout":
        print(json.dumps(layout(argv[2], argv[3], argv[4], argv[5] if len(argv) > 5 else "en"), ensure_ascii=False))
        return 0
    if cmd == "color":
        print(json.dumps(color(argv[2], argv[3], argv[4].lower())))
        return 0
    print(__doc__)
    return 1


if __name__ == "__main__":
    sys.exit(main(sys.argv))
```

**keys.py**

```python
#!/usr/bin/env python3
"""keys.py <before.xml> <after.xml>: settings keys removed, changed and added, as JSON."""
import json
import sys
import xml.etree.ElementTree as ET


def load(path):
    root = ET.fromstring(open(path, encoding="utf-8").read().replace("\r", ""))
    out = {}
    for e in root:
        name = e.get("name")
        if e.tag == "set":
            out[name] = "set:" + ",".join(sorted((s.text or "") for s in e))
        elif e.tag == "string":
            out[name] = "string:" + (e.text or "")
        else:
            out[name] = e.tag + ":" + str(e.get("value"))
    return out


a, b = load(sys.argv[1]), load(sys.argv[2])
print(json.dumps({"removed": sorted(set(a) - set(b)),
                  "changed": {k: [a[k], b[k]] for k in sorted(set(a) & set(b)) if a[k] != b[k]},
                  "added": sorted(set(b) - set(a))}, ensure_ascii=False))
```

What `uiq.py layout <xml> <lhd|rhd|port> <home|launcher> [en|es]` grades:

- **E1:** home mode: exactly one Exit tile, inside the window, at least 90% of the Settings tile's height and width.
  Launcher mode: no Exit tile (the PR hides it there by design).
- **E2:** exactly one main button action, with text, inside the window and inside the main button's card.
- **E3:** the bar is on the driver's side: left of the card (`lhd`), right of it (`rhd`), under it (`port`).
- **E4:** no two bar tiles overlap. **E5:** the title has text and sits inside the card. E4 and E5 need resource ids,
  so on D-HP they print `null` and do not count.
- `PASS` is true only when every graded check is true. It also prints `info_count` (the date and time block),
  `pill_count` (the status pill) and `favorites_inside_and_clear` (the favorites row is on screen and clear of the
  card; `null` when the row is not on this page).

## 6. The deciding lines and view ids

All lines verified with `grep -F -r` in `app/src/main` at `11a2c37f`. Every one is INFO or WARN, so `log-level=2`
carries them. Grep every capture with `-a`.

| Id | Line | Read |
|---|---|---|
| L-RX | `AutomationReceiver: ` | every verb lands; a missing line voids the step |
| L-RES | `HomeFragment: onResume. isConnected=` | the home is on screen |
| L-SSL | `SSL handshake complete` | the session formed; exactly 1 per connection run |
| L-MSSR | `Media Sink Setup Request: ` | projection |
| L-TP | `Throughput over` | projection; the line MUST carry `rendered=` above 0 |
| L-DISC | `AapService: Disconnected.` | the session ended |
| L-STEP | `MainActivity: status pill step: ` | with `HOMELINE` on the same line: the home, not the pill, told that step |
| HOMELINE | `(not shown, the home's main button tells it)` | |
| L-CANCEL | `pressed, stopping the bring-up` | with `home Cancel` on the same line: the home's Cancel fired |
| L-HOLD | `AapService: the status pill's X stopped the wireless bring-up.` | the service held the stack down |
| L-POKE | `NativeAA: Attempting active poke to device` | a wake attempt |
| L-SCAN | `AapService: Force-starting WIFI-Scan from UI` | `ACTION_START_WIRELESS_SCAN` reached the service |
| L-ARM | `WifiLauncher: Initializing WiFi Mode:` | the stack armed |
| L-BG | `AapProjectionActivity: moving the projection to the background, home app is this one=` | read `true` or `false` after `=` |
| L-RAISE | `MainActivity: Active session detected, bringing projection to front` | the home raised the projection itself |
| L-PDEST | `AapProjectionActivity.onDestroy called. isFinishing=` | record only (review Warn 8) |
| L-DEC | `Decoder stopped: ` | record only (expected on Move to Background, section 3) |
| L-HINT | `HomeFragment: showing the pages hint` | both forms of the hint |
| L-HINTX | `HomeFragment: pages hint closed (` | read the reason (`BACK`) |
| L-PADD | `HomeFragment: added a home page` | |
| L-PREM | `HomeFragment: removed added home page ` | |
| L-PAGES | `HomeFragment: the home has ` | read `N page(s)` |
| L-PAGE | `HomePager: showing page ` | read `N of M` |
| L-WNO | `HomeFragment: the widget was not allowed, releasing its id` | MUST be 0 |
| L-SPD0 | `HomeFragment: the speed readout has no location permission, showing the placeholder` | |
| L-SPDX | `HomeFragment: the speed readout could not subscribe to GPS: ` | record |
| L-FIT | `HomeFragment: the main button is ` | record: lines the card dropped to keep its action |
| L-ROLE | `CarLauncherManager: RoleManager failed: ` | MUST be 0 |
| L-WAKE | `MATCH! Starting AapService` | §4 discard rule |
| L-POKED | `NativeAA: Successfully poked ` | SAM-C2 contingency only |
| L-ACC | `NativeAA: Connection accepted from ` | SAM-C2 contingency only |

The stage names the runs read on L-STEP lines are the enum names in `ConnectionStage.kt`: `WAKING_PHONE`,
`PHONE_ANSWERED`, `SENDING_CREDENTIALS`, `PHONE_JOINING`, `CONNECTING`, `SECURING`, `STARTING_PROJECTION`. A
"connecting step" below means any of these seven. `ARMED`, `WAITING_FOR_PHONE`, `SEARCHING` and `USB_ATTACHED` are
listening steps.

**Phone lines** (Gearhead, verified in the decompiled 18.0.664204 sources, not in our tree): `Critical error` (the
phone ended the session on a protocol error) and `ByeByeRequest` (the session ended). Record each phone's Gearhead
version in Setup notes, because phone strings drift between builds.

**View ids** (all in `app/src/main/res/layout` at `11a2c37f`): `home_primary_card`, `home_primary_title`,
`home_primary_subtitle`, `home_primary_action`, `home_primary_cancel`, `home_primary_progress`, `home_driver_bar`,
`usb_button`, `wifi_button`, `self_mode_button`, `settings_button`, `exit_button`, `home_pager`, `home_page_dots`,
`home_favorites`, `apps_button`, `favorite_slot_1` to `favorite_slot_4`, `favorite_label`, `home_widgets_scroll`,
`widget_add_tile`, `widget_option_label`, `home_actions_title`, `home_action_title`, `home_info`, `home_info_clock`,
`home_info_date`, `home_info_speed`, `home_pages_hint_got_it`, `auto_connect_pill`, `wifi_direct_info`,
`connection_issue_banner`, `home_color_preview`. The old build's classic home is `main_buttons_layout` (in
`fragment_home.xml` at `v.3.5.0` and at `fbeb46fa`; gone at `11a2c37f`). The system's widget bind dialog button is
`android:id/button1`.

**Labels** the runs match by text (`values` and `values-es` at `11a2c37f`):

| Variable | Where | English | Spanish |
|---|---|---|---|
| `L_BG` | exit menu | `Move to Background` | `Mover a segundo plano` |
| `L_PIP` | exit menu | `Picture-in-Picture` | `Imagen en imagen (PiP)` |
| `L_QS` | exit menu | `Quick Settings` | `Ajustes rápidos` |
| `L_STOP` | exit menu | `Stop Connection` | `Detener conexión` |
| `L_CONNECTING` | bar tile | `Connecting` | `Conectando` |
| `L_CONNECTED` | bar tile | `Connected` | `Conectado` |
| `L_BACK` | main action, session up | `Back to Android Auto` | `Volver a Android Auto` |
| `L_SELF` | main action, Self Mode | `Start Self Mode` | `Iniciar Modo Autónomo` |
| `L_WAKING` | card step | `Waking your phone` | `Despertando tu teléfono` |
| `L_CANCEL` | card | `Cancel` | `Cancelar` |
| `L_PADD` | page menu | `Add a page` | `Agregar una página` |
| `L_PREM` | page menu | `Remove this page` | `Eliminar esta página` |
| `L_WREM` | widget menu | `Remove widget` | `Eliminar widget` |
| `L_ADDAPP` | empty favorite slot | `Add app` | `Agregar app` |

Set the variables per stage from the column of the unit's locale, for example `L_BG="Move to Background"`. A unit
on `es-ES` reads `values-es-rES` first, which overrides a few labels (Settings is `Ajustes` there).

## 7. Pre-flight (15 min)

**preflight.sh** (stage A units first; repeat the per-unit block for D-SAM, D-HP and D-POCO at the start of their
stages)

```bash
. ./lib.sh
sed -n '/^## Pick a tool/,/^## Rig and devices/p' $RIG_TOOLKIT/TOOLS.md > $OUT/tools.txt   # Setup notes: which tools served which step
# per head unit: set D and U first (D=$S_HU U=HU, ...)
unit_pre() {
  adb -s $D shell getprop ro.build.version.sdk | tee $OUT/$U-sdk.txt
  adb -s $D shell wm size | tee $OUT/$U-wm.txt; adb -s $D shell wm density | tee -a $OUT/$U-wm.txt
  adb -s $D shell getprop persist.sys.locale | tee $OUT/$U-locale.txt
  adb -s $D shell getprop persist.sys.language | tee -a $OUT/$U-locale.txt
  adb -s $D shell pm list features | grep -c 'android.software.picture_in_picture' | tee $OUT/$U-pip.txt
  adb -s $D shell ls -ld /data/data/$PKG/shared_prefs | tee $OUT/$U-stat.txt   # chown to the app uid:gid if root-owned
  adb -s $D shell settings get global animator_duration_scale | tee $OUT/$U-anim.txt
  adb -s $D shell settings put global animator_duration_scale 0
  adb -s $D shell dumpsys package $PKG | grep -a -E 'ACCESS_(FINE|COARSE)_LOCATION' | tee $OUT/$U-loc.txt
  adb -s $D shell am force-stop $PKG; sleep 1
  pull_settings $OUT/$U-backup.xml
  grep -o 'name="\(video-profile-starvation-cap\|use-aac-audio\|audio-latency-multiplier\|audio-queue-capacity\|wifi-connection-mode\|screen-orientation\|connection-modes\)"[^/]*' $OUT/$U-backup.xml | tee $OUT/$U-keys0.txt
}
# D-HU and D-POCO only: the HOME role holder, and the widget bind grant (API 21+)
role_pre() {
  adb -s $D shell cmd role get-role-holders android.app.role.HOME | tr -d '\r' | tee $OUT/home-role-$U.txt
  # empty file: read the holder from `dumpsys role` (the android.app.role.HOME entry) and write the package name in
  adb -s $D shell appwidget grantbind --package $PKG --user 0; echo "grantbind rc=$?" | tee -a $OUT/marks.txt
}
# phones
phone_pre() { local s=$1 n=$2
  adb -s $s shell ps -A | grep -c gearhead | tee $OUT/$n-gearhead.txt
  adb -s $s shell dumpsys package com.google.android.projection.gearhead | grep -a -m1 versionName | tee -a $OUT/$n-gearhead.txt
  adb -s $s logcat -d | grep -ac THROTTLE_LIMIT_EXCEEDED | tee $OUT/$n-throttle.txt   # above 0: reboot that phone first
  adb -s $s shell dumpsys window | grep -a -m1 mCurrentFocus | tee $OUT/$n-focus.txt   # a Keyguard or NotificationShade focus: hand step (2)
  adb -s $s shell dumpsys bluetooth_manager | grep -a -A8 'Bonded devices' | tee $OUT/$n-bonds.txt
}
phone_pre $S_MOTO MOTO; phone_pre $S_POCO POCO
# D-POCO's head unit server: a local address ending :149D in either table
adb -s $S_POCO shell "cat /proc/net/tcp /proc/net/tcp6" | grep -ac ':149D ' | tee $OUT/POCO-5277.txt   # 0: hand step (1), then read it again
# D-POCO's own app, a head unit in earlier rounds: exit only if it runs
[ -n "$(adb -s $S_POCO shell pidof $PKG)" ] && { adb -s $S_POCO shell am start -a android.intent.action.VIEW -d "headunit://exit" >/dev/null; sleep 5; }
adb -s $S_POCO shell am force-stop $PKG
```

## 8. Runs

Each run ends with a verdict and the numbers its bullets ask for. A bullet that cannot be read (a dump failed twice,
a target did not resolve) is UNTESTABLE for that bullet, and the rest still grade. Apply the §4 discard rules to every
connection run (HU-C2a, HU-C2b, HU-C3, SAM-C2, HP-C2, POCO-S1, POCO-S2). The Move to Background return MUST NOT add a
second L-SSL; that is a bullet, not a discard.

**Stop rules.** A connection run with no L-SSL within 120 s of its phone going on is retried once. If the phone shows
`THROTTLE_LIMIT_EXCEEDED`, reboot the phone, then retry once. Stop a stage after 2 connection runs with no session and
mark its other connection runs UNTESTABLE (rig). The host-thermal rule in `rig-quirks/topics/tooling.md` applies to
every run.

### Shared run shapes

The per-device sections call these. `R` is the run id.

**upg.sh** (`upg R`: the upgrade, one per head unit)

```bash
upg() { local R=$1; th_gate $R || return 1
  adb -s $D shell am force-stop $PKG; sleep 1
  send ACTION_QUERY_STATE | tee $OUT/$R-old-state.txt          # the old build's commit
  adb -s $D shell am force-stop $PKG; sleep 1
  # write base + K_COL on top of the backup (section 4); leave the unit's own mode and connection keys as they are
  launch; sleep 2; mark $R-start
  waitn $R-start "HomeFragment: onResume. isConnected=" 1 40; sleep 3
  uidump $R-old
  adb -s $D shell am force-stop $PKG; sleep 1; pull_settings $OUT/$R-pre-install.xml
  adb -s $D install -r -d $APK                                  # identity checks of section 1 now
  pull_settings $OUT/$R-post-install.xml
  launch; sleep 2; mark $R-new
  waitn $R-new "HomeFragment: onResume. isConnected=" 1 40; sleep 3
  uidump $R-new; [ "$(cat $OUT/$U-sdk.txt | tr -d '\r')" -ge 21 ] && raw $R-new; focus > $OUT/$R-focus.txt
  send ACTION_QUERY_STATE | tee $OUT/$R-new-state.txt
  mark $R-end
  adb -s $D shell am force-stop $PKG; sleep 1; pull_settings $OUT/$R-post-launch.xml
  th_end; }
```

PASS when all of these hold:

- **U1** (gate): `python3 -I $UIQ find $OUT/ui/$R-old.xml id:main_buttons_layout` prints 1 line. Else the run is
  INCONCLUSIVE (the unit did not show the classic home) and the rest is recorded only.
- **U2:** identity as section 1 (md5, `HomePagesHintPolicy` in the DEX, `commit` `11a2c37fd244`).
- **U3:** `python3 -I $KEYS $OUT/$R-pre-install.xml $OUT/$R-post-install.xml`: all three lists empty.
- **U4:** `$R-new.xml` has `id:home_primary_card` 1 line and `id:main_buttons_layout` 0 lines; `$R-focus.txt` names
  `MainActivity` and not `OnboardingActivity`.
- **U5:** `python3 -I $KEYS $OUT/$R-pre-install.xml $OUT/$R-post-launch.xml`: `removed` empty; `changed` holds no key
  (list any key there in the results, with both values); `added` is recorded only. The three `custom-*-button-color`
  keys read -1754827.
- **U6** (D-HU and D-POCO only): `python3 -I $UIQ color $OUT/ui/$R-new.raw $OUT/ui/$R-new.xml E53935` prints
  `"result": "MATCH"`. `NA` makes this bullet N/A (orientation mismatch), not a FAIL.
- Record: the old build's `commit`, `uiq layout` of `$R-new` (information here; the LAY runs grade layout).

**cust.sh** (`cust R`: Customization colour preview, record only, right after the `-U` run while `K_COL` is in place)

```bash
cust() { local R=$1
  # the nav id: aapt2 dump resources $APK | grep -m1 'id/customizationFragment' gives 0x7f...; CUST=$(printf '%d' 0x7f...)
  launch; sleep 4; mark $R-start
  adb -s $D shell am start -n $PKG/com.andrerinas.openheadunit.main.SettingsActivity --ei extra_destination $CUST
  # D-SAM: adb -s $D shell run-as $PKG am start --user 0 -n $PKG/com.andrerinas.openheadunit.main.SettingsActivity --ei extra_destination $CUST
  sleep 4; uidump $R; mark $R-end
  adb -s $D shell input keyevent 4; sleep 1; adb -s $D shell am force-stop $PKG; }
```

Record whether `id:home_color_preview` is in `$R.xml` with bounds inside the window. If it is not on the first
screen, say so and move on. Attach the screenshot. Not graded.

**lay.sh** (`lay R <lhd|rhd|port> [K_RHD] [K_PORT|K_LAND]`: layout, home mode, phones stay off)

```bash
lay() { local R=$1 CASE=$2; th_gate $R || return 1
  restore
  # write base + unit base + K_LAY, plus the extra sets the run names; read back
  launch; sleep 2; mark $R-start
  waitn $R-start "HomeFragment: onResume. isConnected=" 1 40; sleep 5
  uidump $R; focus
  python3 -I $UIQ layout $OUT/ui/$R.xml $CASE home $LANGU | tee $OUT/$R.layout.json
  mark $R-end; adb -s $D shell am force-stop $PKG; th_end; }
```

PASS: `$R.layout.json` has `"PASS": true`, `info_count` 0 (the date block is off by default) and `pill_count` 0.
Record the Exit tile and action bounds, and every L-FIT line in the window.

**homeinfo.sh** (`homeinfo R`: date, time and speed)

```bash
homeinfo() { local R=$1; th_gate $R || return 1
  restore
  # write base + unit base + K_LAY + K_INFO; read back
  if [ "$(cat $OUT/$U-sdk.txt | tr -d '\r')" -ge 23 ]; then
    adb -s $D shell pm revoke $PKG android.permission.ACCESS_FINE_LOCATION
    adb -s $D shell pm revoke $PKG android.permission.ACCESS_COARSE_LOCATION
    launch; sleep 2; mark $R-a-start
    waitn $R-a-start "HomeFragment: onResume. isConnected=" 1 40; sleep 4
    uidump $R-a; mark $R-a-end; adb -s $D shell am force-stop $PKG; sleep 1
    adb -s $D shell pm grant $PKG android.permission.ACCESS_FINE_LOCATION
    adb -s $D shell pm grant $PKG android.permission.ACCESS_COARSE_LOCATION
  fi
  launch; sleep 2; mark $R-b-start
  waitn $R-b-start "HomeFragment: onResume. isConnected=" 1 40; sleep 4
  uidump $R-b; python3 -I $UIQ layout $OUT/ui/$R-b.xml lhd home $LANGU | tee $OUT/$R-b.layout.json
  mark $R-b-end; adb -s $D shell am force-stop $PKG; th_end; }
```

PASS:

- **I1** (API 23+ only, else N/A): `span $R-a-start $R-a-end | grep -acF "HomeFragment: the speed readout has no location permission, showing the placeholder"`
  is 1 or more. In `$R-a.xml`: `home_info_speed` text is `--`.
- **I2:** `span $R-b-start $R-b-end | grep -acF "HomeFragment: the speed readout has no location permission, showing the placeholder"`
  is 0. In `$R-b.xml`: `home_info_clock` text contains `:`, `home_info_date` text is not empty, `home_info_speed` text
  is `--` or digits only.
- **I3:** `$R-b.layout.json` has `"PASS": true` (the date block does not push the action off the card).
- Record: the L-SPDX count, and the device's `date +%H:%M` beside the clock text.
- Restore the location grant state pre-flight read (`$OUT/$U-loc.txt`) at the end of the stage.

**lnr.sh** (`lnr R <lhd|port>`: launcher home without the HOME role; D-SAM and D-HP)

```bash
lnr() { local R=$1 CASE=$2; th_gate $R || return 1
  restore
  # write base + unit base + K_LAY + K_LN (K_LN sets enable-car-launcher true); read back
  launch; sleep 2; mark $R-start
  waitn $R-start "HomeFragment: onResume. isConnected=" 1 40; sleep 5
  uidump $R; python3 -I $UIQ layout $OUT/ui/$R.xml $CASE launcher $LANGU | tee $OUT/$R.layout.json
  mark $R-end; adb -s $D shell am force-stop $PKG; th_end; }
```

PASS: `"PASS": true` (so no Exit tile, action whole); `favorites_inside_and_clear` true; `id:apps_button` 1 line;
four `favorite_slot_N` lines; the first `id:favorite_label` text is not `$L_ADDAPP`; `id:home_widgets_scroll` 0 lines;
L-HINT 0 in the window (below API 21 the hint never shows).

**conn_home.sh** (`conn_home R <phone-serial> <phone-capture>`: a session in home mode, the PiP row, Move to
Background to the factory home, the return)

```bash
conn_home() { local R=$1; PH=$2; PHCAP=$3; th_gate $R || return 1
  ph_off $PH; restore
  # write base + unit base + K_CONN; read back
  launch; sleep 20; mark $R-start
  ph_on $PH
  waitn $R-start "SSL handshake complete" 1 120 || { echo "$R NO_SESSION" | tee -a $OUT/marks.txt; mark $R-end; th_end; return 1; }
  waitn $R-start "Media Sink Setup Request: " 1 30; waitn $R-start "Throughput over" 1 30; sleep 10
  adb -s $D shell input keyevent 4; sleep 2; uidump $R-exit
  gest $R tap "text:$L_BG" $R-exit; mark $R-bg; sleep 3; focus > $OUT/$R-focus-bg.txt
  sleep 20; mark $R-ret
  launch; sleep 5; focus > $OUT/$R-focus-ret.txt
  waitn $R-ret "Throughput over" 1 30
  mark $R-disc; send ACTION_DISCONNECT; sleep 10; mark $R-end
  ph_off $PH; th_end; }
```

PASS:

- **P1:** in `win $R`, L-SSL is exactly 1; L-MSSR 1 or more; a L-TP line before `$R-bg` with `rendered=` above 0.
- **P2:** before the first L-SSL, at least one L-STEP line with `HOMELINE` and a connecting step on it.
- **P3:** `$R-exit.xml` holds `text:$L_BG` and `text:$L_STOP`, and `text:$L_QS` (the full menu). `text:$L_PIP`
  appears exactly when `$OUT/$U-pip.txt` reads 1 or more and the unit's API is 26 or more; else 0 lines.
- **P4:** `span $R-bg $R-ret` has one L-BG line ending `=false`, and `$R-focus-bg.txt` names the package in
  `$OUT/home-role-$U.txt` (the factory launcher, expected when this app is not the home app). D-SAM and D-HP have no
  role file: there `$R-focus-bg.txt` MUST NOT name `$PKG`.
- **P5:** the session survived: `span $R-start $R-disc` has L-DISC 0; `span $R-start $R-disc $PHCAP` has
  `ByeByeRequest` 0 and `Critical error` 0.
- **P6:** the return: `span $R-ret $R-disc` has L-RAISE 1 or more (by design here); `$R-focus-ret.txt` names
  `AapProjectionActivity`; a L-TP line after `$R-ret` with `rendered=` above 0.
- **P7:** `span $R-disc $R-end $PHCAP | grep -acF ByeByeRequest` is 1 or more.
- Record: L-PDEST and L-DEC lines between `$R-bg` and `$R-ret`.

**conn_launcher.sh** (`conn_launcher R <phone-serial> <phone-capture> <tile|main>`: a session on the launcher home
with two pages, Move to Background to this app's home, the return by the Connected tile or the main button; D-HU)

```bash
conn_launcher() { local R=$1 HOW=$4 i; PH=$2; PHCAP=$3; th_gate $R || return 1
  mark $R-start
  send ACTION_START_WIRELESS_SCAN; waitn $R-start "WifiLauncher: Initializing WiFi Mode:" 1 20
  ph_on $PH
  for i in $(seq 1 40); do [ "$(acnt $R-start "SSL handshake complete")" -ge 1 ] && break; uidump $R-up$i; done
  waitn $R-start "SSL handshake complete" 1 120 || { echo "$R NO_SESSION" | tee -a $OUT/marks.txt; mark $R-end; th_end; return 1; }
  waitn $R-start "Media Sink Setup Request: " 1 30; waitn $R-start "Throughput over" 1 30; sleep 10
  adb -s $D shell input keyevent 4; sleep 2; uidump $R-exit
  gest $R tap "text:$L_BG" $R-exit; mark $R-bg; sleep 3; focus > $OUT/$R-focus-bg.txt
  uidump $R-home; python3 -I $UIQ clear $OUT/ui/$R-home.xml id:home_favorites id:auto_connect_pill id:wifi_direct_info id:connection_issue_banner | tee $OUT/$R-clear.json
  sleep 20; mark $R-ret
  if [ $HOW = tile ]; then gest $R tap id:wifi_button $R-home; else gest $R tap id:home_primary_action $R-home; fi
  sleep 5; focus > $OUT/$R-focus-ret.txt
  waitn $R-ret "Throughput over" 1 30
  mark $R-disc; send ACTION_DISCONNECT; sleep 10; mark $R-end
  ph_off $PH; th_end; }
```

PASS:

- **P1** and **P2** as in `conn_home`.
- **P2b** (information, not a FAIL): the number of `$R-up*` dumps whose `id:wifi_button` text is `$L_CONNECTING`.
  At least 1 grades the bullet PASS; 0 grades it INCONCLUSIVE (the bring-up was shorter than one dump).
- **P3:** `$R-exit.xml` holds `text:$L_BG` and `text:$L_STOP`, and 0 lines of `text:$L_PIP` and `text:$L_QS` (the
  short launcher menu).
- **P4:** `span $R-bg $R-ret` has one L-BG line ending `=true` and L-RAISE 0. `$R-focus-bg.txt` names `$PKG` and not
  the package in `$OUT/home-role-$U.txt`.
- **P5:** `$R-home.xml`: `id:wifi_button` text is `$L_CONNECTED`; `id:auto_connect_pill` 0 lines; every overlap in
  `$R-clear.json` is 0 (when `home_favorites` is not on the shown page, the file says `matches: 0`; record it). If `id:home_primary_action` is in the dump, its text is `$L_BACK`.
- **P6:** the session survived: `span $R-start $R-disc` has L-DISC 0 and L-SSL exactly 1; on the phone
  `ByeByeRequest` 0 and `Critical error` 0 in the same span.
- **P7:** the return: `$R-focus-ret.txt` names `AapProjectionActivity`; a L-TP line after `$R-ret` with `rendered=`
  above 0.
- **P8:** `span $R-disc $R-end $PHCAP | grep -acF ByeByeRequest` is 1 or more.
- Record: L-PDEST and L-DEC lines between `$R-bg` and `$R-ret`.

**pages.sh** (`pages1 R <land|port>`, `pages2 R`, `pages3 R <land|port>`: the launcher pages, one capture, the HOME
role held, phones off; D-HU and D-POCO)

```bash
lblock() {   # start of the launcher block: role back, settings, launch, role taken
  role_off; restore
  # write base + unit base + K_LAY + K_L (D-POCO: K_SELF in place of K_LAY's mode and connection keys); read back
  launch; sleep 2; mark L-$U-launch
  waitn L-$U-launch "HomeFragment: onResume. isConnected=" 1 40; role_on; }
pages1() { local R=$1; th_gate $R || return 1; mark $R-start
  if waitn $R-start "HomeFragment: showing the pages hint" 1 20; then
    sleep 2; uidump $R-hint; adb -s $D shell input keyevent 4; sleep 2      # BACK closes the hint
  else echo "$R NO_HINT" | tee -a $OUT/marks.txt; fi                         # no BACK: it would leave the home
  uidump $R-home; python3 -I $UIQ layout $OUT/ui/$R-home.xml $([ $2 = land ] && echo lhd || echo port) launcher $LANGU | tee $OUT/$R.layout.json
  gest $R long id:home_primary_title $R-home; uidump $R-menu
  gest $R tap "text:$L_PADD" $R-menu; sleep 2; mark $R-added
  adb -s $D shell input keyevent 3; sleep 3; mark $R-home-key
  uidump $R-p1; gest $R next id:home_pager $R-p1; sleep 2; mark $R-swiped
  launch; sleep 3                                   # a second showing of the home: no second hint
  mark $R-end; th_end; }
pages2() { local R=$1 M i; th_gate $R || return 1; mark $R-start
  M=$(grep -a "HomeFragment: the home has " $HUCAP | tail -1 | grep -o 'has [0-9]*' | grep -o '[0-9]*')
  for i in 1 2; do                                  # go to the added page, the last one
    grep -a "HomePager: showing page " $HUCAP | tail -1 | grep -q "page $M of $M" && break
    uidump $R-nav$i; gest $R next id:home_pager $R-nav$i; sleep 2
  done
  uidump $R-p2; gest $R tap id:widget_add_tile $R-p2; sleep 2; uidump $R-pick
  gest $R tap idfirst:widget_option_label $R-pick; sleep 3
  focus > $OUT/$R-focus.txt                         # a bind dialog in focus: one more tap on android:id/button1
  grep -qi 'MainActivity' $OUT/$R-focus.txt || { uidump $R-bind; gest $R tap id:button1 $R-bind; sleep 3; }
  uidump $R-after; pull_settings $OUT/$R-settings.xml
  mark $R-end; th_end; }
pages3() { local R=$1 xy; th_gate $R || return 1; mark $R-start
  uidump $R-p2; xy=$(python3 -I $UIQ prevcell $OUT/ui/$R-p2.xml $2) && gestxy $R $xy
  uidump $R-wmenu; gest $R tap "text:$L_WREM" $R-wmenu; sleep 2
  uidump $R-p2b; gest $R long id:widget_add_tile $R-p2b; uidump $R-pmenu
  gest $R tap "text:$L_PREM" $R-pmenu; sleep 2
  uidump $R-after; pull_settings $OUT/$R-settings.xml
  mark $R-end; th_end; }
```

A compact home (a small or narrow screen) already has two pages before anything is added: the main button, then
the favorites. So the page bullets count from `N0`, the page count before the run: the value on the last L-PAGES line
before `$R-start`, or 1 when no L-PAGES line printed (the home logs the count only when it is not 1). `M` = `N0` + 1.

`pages1` PASS:

- **G1:** L-HINT exactly 1 in `win $R` (the second showing at `launch` adds none), and L-HINTX exactly 1 reading
  `(BACK)`. `$R-hint.xml` holds `id:home_pages_hint_got_it` (record if not).
- **G2:** `$R.layout.json` has `"PASS": true` (launcher mode: no Exit tile, action whole).
- **G3:** `$R-menu.xml` holds `text:$L_PADD`. After the tap: L-PADD 1, and L-PAGES reads `M page(s)`, and L-PAGE
  reads `M of M`, all between `$R-start` and `$R-added`.
- **G4:** the Home key: `span $R-added $R-home-key` has L-PAGE reading `1 of M`.
- **G5:** the swipe: `span $R-home-key $R-swiped` has L-PAGE reading `2 of M`.
- **G6:** L-ROLE 0. Record `home-pages-hint-shown` from settings (the app's own write; it MUST read true if
  `shared_prefs` is owned right).

`pages2` PASS: before the `+` tap the last L-PAGE line reads `M of M` (the added page); `$R-settings.xml` has `home-widget-pages` matching `p[0-9]+` (one widget on the added page); L-WNO 0
in `win $R`. Record: the widget's bounds in `$R-after.xml`, how many `widget_add_tile` lines remain, the screenshot.
If `$R-pick.xml` shows no `widget_option_label` (the unit has no widgets), the run is INCONCLUSIVE and `pages3` skips
its widget half.

`pages3` PASS: `$R-wmenu.xml` holds `text:$L_WREM`; after it `$R-p2b.xml` still holds `id:widget_add_tile`;
`$R-pmenu.xml` holds `text:$L_PREM`; L-PREM 1 reading `page 1` and L-PAGES reading `N0 page(s)` in `win $R`;
`$R-settings.xml` has no `home-widget-pages` value with a `p` in it.

**cancel_wake.sh** (`cancel_wake R <pages|single>`: Cancel while the stack wakes a phone that is off; every phone
off)

```bash
cancel_wake() { local R=$1 MODE=$2 k; th_gate $R || return 1; mark $R-start
  for k in 1 2 3; do
    waitn2 $R-start "$HOMELINE" "WAKING_PHONE" $k 120 || break
    uidump $R-wake$k; python3 -I $UIQ find $OUT/ui/$R-wake$k.xml id:home_primary_progress | grep -q bounds && break
  done
  if [ $MODE = pages ]; then
    gest $R next id:home_pager $R-wake$k; sleep 2; uidump $R-page2; mark $R-f1
    adb -s $D shell input keyevent 3; sleep 3
    waitn2 $R-f1 "$HOMELINE" "WAKING_PHONE" 1 90; uidump $R-wakeB; k=B
  fi
  gest $R tap id:home_primary_cancel $R-wake$k; mark $R-cancel
  sleep 65; uidump $R-after; mark $R-end; th_end; }
```

PASS:

- **W1:** the last `$R-wake*` dump before the tap: `id:home_primary_progress` 1 line; `id:home_primary_cancel` 1
  line inside the window; `id:home_primary_subtitle` text `$L_WAKING`; `id:auto_connect_pill` 0 lines. With `pages`:
  `id:wifi_button` text `$L_CONNECTING`.
- **W2:** within 3 s of `$R-cancel`: one L-CANCEL line that also holds `home Cancel`, and one L-HOLD line.
- **W3:** from 5 s after `$R-cancel` to `$R-end`: L-POKE 0, and 0 L-STEP lines with `WAKING_PHONE`.
  `$R-after.xml` has `id:home_primary_progress` 0 lines and `id:home_primary_cancel` 0 lines.
- **F1** (with `pages`; graded on its own row as `<unit>-F1`): `$R-page2.xml` has a Cancel on screen: either
  `id:home_primary_cancel` with bounds inside the window, or `id:auto_connect_pill`. Predicted FAIL (review Block 1).
  Record which stage the last L-STEP line before `$R-f1` names.

### Stage A: D-HU (phones D-MOTO, D-POCO)

Set `D=$S_HU U=HU`, the labels for D-HU's locale, `LANGU` to `en` or `es`. Start three captures: `cap $OUT/A-hu.txt
$S_HU; cap $OUT/A-moto.txt $S_MOTO; cap $OUT/A-poco.txt $S_POCO; HUCAP=$OUT/A-hu.txt`. Run `unit_pre` and
`role_pre`. Both phones off (`ph_off $S_MOTO; ph_off $S_POCO; hold_on`). Read `$OUT/HU-pip.txt` and say in Setup notes
whether D-HU declares PiP.

| Run | Command | One-line PASS |
|---|---|---|
| HU-U | `upg HU-U` | classic home before, new home after, no key lost or changed, colours kept, card red |
| HU-CUST | `cust HU-CUST` | record only |
| HU-LAY-L | `lay HU-LAY-L lhd` | E1 to E5 true at 1440x720 native, bar on the left |
| HU-LAY-R | `lay HU-LAY-R rhd` with `K_RHD` | E1 to E5 true, bar on the right |
| HU-LAY-P | none | N/A: D-HU is not rotated |
| HU-INFO | `homeinfo HU-INFO` | placeholder with the permission revoked, readout with it granted, action still whole |
| HU-C2a | `conn_home HU-C2a $S_MOTO $OUT/A-moto.txt` | session; PiP row exactly when D-HU declares the feature; Move to Background keeps the session; the home raises it again |
| HU-L1 | `lblock; pages1 HU-L1 land` | hint once, page added, Home to page 1, swipe to page 2 |
| HU-L2 | `pages2 HU-L2` | widget on the added page |
| HU-L3 | `pages3 HU-L3 land` | widget removed, page removed |
| HU-C1 | see below; `cancel_wake HU-C1 pages` | **the point**: card shows the wake with progress, Cancel and the Connecting tile; Cancel stops it for 60 s |
| HU-F1 | inside HU-C1 | a Cancel on page 2 (predicted FAIL) |
| HU-C2b | `conn_launcher HU-C2b $S_MOTO $OUT/A-moto.txt tile` | **the point**: Connected tile, Move to Background lands on this app's home, session survives, tile returns to it |
| HU-C3 | `hold_off; ph_off $S_MOTO; conn_launcher HU-C3 $S_POCO $OUT/A-poco.txt main` | same with D-POCO, return by "Back to Android Auto" |

Before HU-C1, start the connection block on the launcher home (both phones off, `hold_on` running):

**hu_cblock.sh**

```bash
role_off; restore
# write base + unit base + K_CL; read back
launch; sleep 2; mark C-HU-launch
waitn C-HU-launch "HomeFragment: onResume. isConnected=" 1 40; role_on
waitn C-HU-launch "HomeFragment: the home has " 1 20      # MUST read 2 or more page(s)
```

Run HU-C1, HU-C2b and HU-C3 in that order, in this one block. HU-C2b sends `ACTION_START_WIRELESS_SCAN` first: HU-C1
left the stack held down, and the launch was minutes before. If HU-C1 produced no `WAKING_PHONE` home line within
120 s, mark HU-C1 and HU-F1 INCONCLUSIVE and record the last L-STEP line.

End of stage A: `role_off`, `adb -s $S_HU shell am force-stop $PKG`, restore D-HU's backup, put
`animator_duration_scale` back, restore the location grant. Then `adb -s $S_HU shell svc bluetooth disable` (stage B
needs D-HU's radio off). `hold_off`. Stop the three captures.

### Stage B: D-SAM (phone D-POCO)

Set `D=$S_SAM U=SAM`. D-SAM's `logcat -c` does not clear its buffer: every count is windowed by markers, as the
helpers do. `am` on 4.4 rejects `-p`; the helpers use `-n`. Charge D-SAM from a separate supply if it reads below 30%
(`rig-quirks/units/D-SAM.md`). Start `cap $OUT/B-sam.txt $S_SAM; cap $OUT/B-poco.txt $S_POCO; HUCAP=$OUT/B-sam.txt`.
Run `unit_pre`. `ph_off $S_POCO` now; D-MOTO stays off. Before each connection run, read D-HU's Bluetooth
(`adb -s $S_HU shell dumpsys bluetooth_manager | grep -a -m1 enabled:`) and disable it again if it came back.

| Run | Command | One-line PASS |
|---|---|---|
| SAM-U | `upg SAM-U` | classic home before, new home after, no key lost or changed (U6 N/A) |
| SAM-CUST | `cust SAM-CUST` (the `run-as` start) | record only |
| SAM-LAY-L | `lay SAM-LAY-L lhd` | E1 to E5 true at 1280x800 native |
| SAM-LAY-R | `lay SAM-LAY-R rhd` with `K_RHD` | E1 to E5 true, bar on the right |
| SAM-LAY-P | `lay SAM-LAY-P port` with `K_PORT` | E1 to E5 true, bar along the bottom |
| SAM-INFO | `homeinfo SAM-INFO` | I1 N/A; readout shown, action whole |
| SAM-LN | `lnr SAM-LN lhd` | launcher home: favorites row, no widget row, no hint, action whole |
| SAM-C1 | see below; `cancel_wake SAM-C1 single` | card shows the wake with progress and Cancel; Cancel stops it (tile and F1 N/A) |
| SAM-C2 | `conn_home SAM-C2 $S_POCO $OUT/B-poco.txt` | session from D-POCO; no PiP row; Move to Background keeps the session |

Before SAM-C1: `ph_off $S_POCO; restore`, write base + unit base + `K_CONN`, `launch`. Then `cancel_wake SAM-C1
single`. After SAM-C1, `adb -s $S_SAM shell am force-stop $PKG` before SAM-C2 (`conn_home` restores and relaunches).

If SAM-C2 reaches no L-SSL in 150 s while its window shows `NativeAA: Successfully poked ` lines and no
`NativeAA: Connection accepted from ` line, D-POCO may be bound to another head unit's radio. Ask the operator to forget
D-HU's radio on D-POCO, then retry once (`rig-quirks/units/D-POCO.md`). (The warning text that quirk quotes is not in
this build.)

End of stage B: restore D-SAM's backup (`screen-orientation` 2 comes back with it), put `animator_duration_scale`
back, `adb -s $S_HU shell svc bluetooth enable`. Stop the captures.

### Stage C: D-HP (phone D-POCO, Headunit Server)

Set `D=$S_HP U=HP`. D-HP's dump has no resource ids (API 17): `uiq.py layout` switches to text by itself, and every
`gest` uses a `text:` selector. Start `cap $OUT/C-hp.txt $S_HP; capwatch $OUT/C-hp.txt $S_HP; cap $OUT/C-poco.txt
$S_POCO; HUCAP=$OUT/C-hp.txt`. Run `unit_pre`. Hold D-POCO off the LAN until HP-C2 (`ph_off $S_POCO; hold_on`), so no
session forms early: its head unit server stays up with WiFi off. D-HP stays on the house WiFi (its radio has no
working lever). If
`$OUT/POCO-5277.txt` reads 0 after the hand step, run HP-U to HP-LN and mark HP-C2 UNTESTABLE (head unit server down).

| Run | Command | One-line PASS |
|---|---|---|
| HP-U | `upg HP-U` | classic home before, new home after, no key lost or changed (U1 and U4 by text, below; U6 N/A) |
| HP-LAY-L | `lay HP-LAY-L lhd` | E1 to E3 true by text |
| HP-LAY-R | `lay HP-LAY-R rhd` with `K_RHD` | E1 to E3 true by text |
| HP-LAY-P | `lay HP-LAY-P port` with `K_PORT` | E1 to E3 true by text |
| HP-INFO | `homeinfo HP-INFO` | I1 N/A; readout shown (by text: a `:` clock text and `--` or digits) |
| HP-LN | `lnr HP-LN lhd` | launcher home by text: no Exit text, action whole, no hint |
| HP-F1 | below | a Cancel on screen while discovery runs (predicted FAIL) |
| HP-C2 | below | session from D-POCO; no PiP row; Move to Background keeps the session |

HP-U at API 17 cannot read ids. Grade U1 from the old dump by text instead: the old classic home carries the labels
`Settings`/`Exit` and no main button action text from section 6's action list. Grade U4 by the new dump holding one
action text from that list. U3 and U5 grade as written.

HP-F1 and HP-C2, in one capture window:

**hp_f1.sh**

```bash
PH=$S_POCO; PHCAP=$OUT/C-poco.txt
restore                                                        # D-POCO is still held off the LAN
# write base + K_CONN (wifi-connection-mode 1); read back
launch; sleep 30; mark HP-F1-start
uidump HP-F1; grep -aF "MainActivity: status pill step: " $HUCAP | tail -1 | tee $OUT/HP-F1-step.txt
python3 -I $UIQ find $OUT/ui/HP-F1.xml "text:$L_CANCEL" | tee $OUT/HP-F1-cancel.txt
# only if a Cancel is on screen: gest HP-F1 tap "text:$L_CANCEL" HP-F1; sleep 3; send ACTION_START_WIRELESS_SCAN
mark HP-F1-end; adb -s $D shell am force-stop $PKG
hold_off; ph_on $S_POCO
conn_home HP-C2 $S_POCO $OUT/C-poco.txt
```

HP-F1 PASS: `$OUT/HP-F1-cancel.txt` holds 1 line, or `$OUT/HP-F1-step.txt` names a stage with no suffix after it
(the pill is shown, with its X). A step line that reads `hidden` means no stage was up: then HP-F1 is INCONCLUSIVE. Predicted FAIL: `SEARCHING` wins on the card, so neither shows (review Block 1). If a Cancel was there and
tapped: one L-CANCEL line with `home Cancel` and one L-HOLD line within 3 s. Record the stage name on the step line.

HP-C2 grades as `conn_home`, with P3's PiP row 0 lines (API 17) and P4's focus not naming `$PKG`.

End of stage C: restore D-HP's backup, put `animator_duration_scale` back, kill the `capwatch` loop. Stop the captures.
Unplug D-HP.

### Stage D: D-POCO as the head unit (Self Mode)

Set `D=$S_POCO U=POCO PH=$S_POCO`. One capture serves both sides: `cap $OUT/D-poco.txt $S_POCO; HUCAP=$OUT/D-poco.txt;
PHCAP=$HUCAP`. Run `unit_pre` and `role_pre`. `ph_on $S_POCO` (its normal phone state). D-MOTO and D-HU stay off the air
(`adb -s $S_HU shell svc bluetooth disable` if D-HU is still cabled). Check D-POCO's screen is idle (`mCurrentFocus`; `input keyevent KEYCODE_HOME` if a
Settings screen is open). If `$OUT/POCO-5277.txt` reads 0, mark POCO-S1 and POCO-S2 UNTESTABLE and run the rest.

| Run | Command | One-line PASS |
|---|---|---|
| POCO-U | `upg POCO-U` | classic home before, new home after, no key lost or changed, card red |
| POCO-CUST | `cust POCO-CUST` | record only |
| POCO-LAY-P | `lay POCO-LAY-P port` | E1 to E5 true in native portrait, bar along the bottom |
| POCO-LAY-PR | `lay POCO-LAY-PR port` with `K_RHD` | E1 to E5 true, portrait, right-hand drive |
| POCO-LAY-W | `lay POCO-LAY-W lhd` with `K_LAND` | E1 to E5 true on a short wide screen (the compact sizes) |
| POCO-INFO | `homeinfo POCO-INFO` | placeholder with the permission revoked, readout with it granted |
| POCO-L1 | `lblock; pages1 POCO-L1 port` | hint once, page added, Home to page 1, swipe to page 2, in portrait |
| POCO-L2 | `pages2 POCO-L2` | widget on the added page |
| POCO-L3 | `pages3 POCO-L3 port` | widget removed, page removed |
| POCO-S1 | below | Self Mode from the main button; Connected tile; Move to Background lands on this app's home; the tile returns |
| POCO-S2 | below | PiP row exactly when D-POCO declares the feature (home mode, full menu) |

In POCO-LAY-W, wait 8 s after the launch before the dump (`rig-quirks/units/D-POCO.md`: a rotation can lag). If the
dump's window is still taller than wide, mark POCO-LAY-W UNTESTABLE.

**poco_s.sh**

```bash
S1() { local R=POCO-S1; th_gate $R || return 1
  role_off; restore
  # write base + K_SELF + enable-car-launcher true + home-widget-pages p + home-pages-hint-shown true; read back
  launch; sleep 2; mark $R-start
  waitn $R-start "HomeFragment: onResume. isConnected=" 1 40; role_on; sleep 2
  uidump $R-ready                                                   # the main action MUST read $L_SELF
  gest $R tap id:home_primary_action $R-ready
  waitn $R-start "SSL handshake complete" 1 60 || { echo "$R NO_SESSION" | tee -a $OUT/marks.txt; mark $R-end; th_end; return 1; }
  waitn $R-start "Media Sink Setup Request: " 1 30; waitn $R-start "Throughput over" 1 30; sleep 10
  adb -s $D shell input keyevent 4; sleep 2; uidump $R-exit
  gest $R tap "text:$L_BG" $R-exit; mark $R-bg; sleep 3; focus > $OUT/$R-focus-bg.txt
  uidump $R-home; python3 -I $UIQ clear $OUT/ui/$R-home.xml id:home_favorites id:auto_connect_pill id:wifi_direct_info id:connection_issue_banner | tee $OUT/$R-clear.json
  sleep 20; mark $R-ret
  gest $R tap id:self_mode_button $R-home; sleep 5; focus > $OUT/$R-focus-ret.txt
  waitn $R-ret "Throughput over" 1 30
  mark $R-disc; send ACTION_DISCONNECT; sleep 10; mark $R-end; th_end; }
S2() { local R=POCO-S2; th_gate $R || return 1
  role_off; restore
  # write base + K_SELF (enable-car-launcher false); read back
  launch; sleep 4; mark $R-start
  send ACTION_START_SELF_MODE
  waitn $R-start "SSL handshake complete" 1 60 || { echo "$R NO_SESSION" | tee -a $OUT/marks.txt; mark $R-end; th_end; return 1; }
  waitn $R-start "Throughput over" 1 30; sleep 5
  adb -s $D shell input keyevent 4; sleep 2; uidump $R-exit
  adb -s $D shell input keyevent 4; sleep 1
  mark $R-disc; send ACTION_DISCONNECT; sleep 10; mark $R-end; th_end; }
```

POCO-S1 PASS: `$R-ready.xml` action text `$L_SELF`; then `conn_launcher`'s P1, P3, P4, P6, P7 and P8 on this one
capture, with P5 reading `id:self_mode_button` text `$L_CONNECTED` (not the WiFi tile). P2 is N/A (Self Mode reports
no steps).

POCO-S2 PASS: L-SSL 1; `$R-exit.xml` holds `text:$L_QS`; `text:$L_PIP` appears exactly when `$OUT/POCO-pip.txt` reads
1 or more; on the capture `ByeByeRequest` 0 and `Critical error` 0 before `$R-disc` and `ByeByeRequest` 1 or more
after it.

End of stage D: `role_off`, restore D-POCO's backup, put `animator_duration_scale` back, restore the location grant.
Then `headunit://exit` (the app runs) and `adb -s $S_POCO shell am force-stop $PKG`. Read the HOME role holder on
D-HU and D-POCO once more and compare with the pre-flight files. Stop the capture.

## 9. Do not re-run

Nothing from earlier threads. `pr-1042-disabled-home-buttons` graded the classic home's buttons; this PR removes that
home, so its runs do not apply here.

## 10. Report back

One verdict per run in a table at the top of the results file, with one line per unit on what ran and what was N/A.
Then:

1. **Cancel.** W1 to W3 on D-HU and D-SAM: did the home's Cancel stop the wake, and did the stack stay down for 60 s?
   F1 on D-HU page 2 and on D-HP: was any Cancel or pill on screen, and which stage was up?
2. **Move to Background.** For HU-C2a, HU-C2b, HU-C3, SAM-C2, HP-C2 and POCO-S1: the L-BG value, the focus after it,
   L-DISC and L-SSL counts, the phone's `ByeByeRequest` and `Critical error` counts before the disconnect, and the
   focus after the return. Record L-PDEST lines (review Warn 8).
3. **Layout.** E1 to E5 per unit and case, with the Exit tile's and the action's bounds against the window.
4. **Upgrade.** Per unit: the old build's commit, classic home before, `removed`/`changed` keys, colour MATCH.
5. **Launcher.** G1 to G6 and the widget and page results on D-HU and D-POCO, and the widget bounds.
6. **PiP.** Per unit: the feature count from `pm list features` against the PiP row in the exit menu.

````decisive-strings
AutomationReceiver: 
AutomationMarker: 
HomeFragment: onResume. isConnected=
SSL handshake complete
Media Sink Setup Request: 
Throughput over
rendered=
AapService: Disconnected.
MainActivity: status pill step: 
(not shown, the home's main button tells it)
WAKING_PHONE
PHONE_ANSWERED
SENDING_CREDENTIALS
PHONE_JOINING
CONNECTING
SECURING
STARTING_PROJECTION
pressed, stopping the bring-up
home Cancel
AapService: the status pill's X stopped the wireless bring-up.
NativeAA: Attempting active poke to device
AapService: Force-starting WIFI-Scan from UI
WifiLauncher: Initializing WiFi Mode:
AapProjectionActivity: moving the projection to the background, home app is this one=
MainActivity: Active session detected, bringing projection to front
AapProjectionActivity.onDestroy called. isFinishing=
Decoder stopped: 
HomeFragment: showing the pages hint
HomeFragment: pages hint closed (
HomeFragment: added a home page
HomeFragment: removed added home page 
HomeFragment: the home has 
HomePager: showing page 
HomeFragment: the widget was not allowed, releasing its id
HomeFragment: the speed readout has no location permission, showing the placeholder
HomeFragment: the speed readout could not subscribe to GPS: 
HomeFragment: the main button is 
CarLauncherManager: RoleManager failed: 
MATCH! Starting AapService
NativeAA: Successfully poked 
NativeAA: Connection accepted from 
````

Phone-side strings, verified in the decompiled Gearhead 18.0.664204 sources rather than in `app/src`:

````decisive-strings-phone
Critical error
ByeByeRequest
THROTTLE_LIMIT_EXCEEDED
````
