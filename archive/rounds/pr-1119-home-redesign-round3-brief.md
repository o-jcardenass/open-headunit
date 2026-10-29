# pr-1119-home-redesign, round 3 brief

Published name: `pr-1119-home-redesign-round3-brief.md`. Results go in `pr-1119-home-redesign-round3-results.md`.
Evidence: the release `rig-evidence-pr-1119-home-redesign` exists from round 1. Upload `pr-1119-home-redesign-round3-captures.zip` to it.

**This round is short on purpose.** Many PRs wait for the rig. It checks only what changed since round 2 (`0d333b19`) and what the author asked for on hardware. It re-runs nothing that round 2 proved on code that did not change since.

Units: D-HU (head unit: idle home, Native AA with D-MOTO, setup wizard), D-MOTO (the phone for D-HU), D-HP (one countdown screenshot), D-POCO (as the head unit for the speed switch only). D-SAM is not used.

Queue line: `pr-1119-home-redesign-round3-brief.md` (one APK, `135131ce` = upstream PR #1119 head, 18 commits on round 2's `0d333b19`; units D-HU, D-MOTO, D-HP, D-POCO; HU-B5 and HU-W12a/b are the point).

Pairing: D-SAM is free. Any round that uses D-SAM alone can run beside this one.

Cabling: D-HU, D-MOTO, D-HP and D-POCO on adb, one port each.

**Hand steps.** Each is outside this app.

1. Pre-flight, only if D-MOTO shows a lock screen: unlock it once.
2. HU-W10 only, and only if a system home-app screen comes up: pick Open Headunit and confirm. The script waits 120 s for it.

**Injected gestures.** The cap is 5 per run, with one exception (HU-W10, below). Each gesture resolves its target in a fresh `uiautomator dump` by resource id or exact text, asserts exactly one match and logs the coordinates (`gest` in section 5). Two gestures use a point computed from the dump: a list row found by its text (`rowof`) and the free part of the widget row (`emptyright`). A target that does not resolve voids that condition. No run types or swipes through a list.

**Injected keys.** The cap is 6 per run (`key` in section 5). A key is logged with its time, and the run keeps a dump and a screenshot after each D-pad key. Two runs use keys as a bounded search: HU-B5 brings a VLC row into view in the widget list, and POCO-U3 brings the speed row into view in Customization. Each stops at 4 keys.

| Run | Gestures | Keys | What they are |
|---|---|---|---|
| HU-CD | 1 | 1 | the Settings tile; BACK out of Settings |
| HU-B5 | 2 (5) | 4 at most | "+", the VLC row; "+" and a second VLC row only if the first had no setup screen; BACK out of the setup screen |
| HU-W12w | 2 (3) | 0 | "+" and the first picker entry, only if HU-B5 left no widget; the bind button only if the system asks |
| HU-W12a | 0 | 6 at most | up to 4 moves to the card, one long press, BACK |
| HU-W12b | 0 | 6 at most | up to 4 moves to the widget, one long press, BACK |
| HU-ROW | 1 | 1 | a long press in the free part of the widget row; BACK |
| HU-U1 | 1 | 0 | the pill's X |
| HU-B1 | 2 | 0 | swipe to page 2, the pill's X |
| HU-W8 | 1 | 1 | BACK opens the exit menu; "End session, stay ready" |
| HU-W10 | 9 | 0 | "Next" eight times, the launcher switch (exception, see the run) |
| HP-RING | 0 | 0 | |
| POCO-U3a, POCO-U3b | 3 each at most | 4 at most each | the speed switch; at most two taps on the system location dialog |

**Time budget.** Build 20 min, with pre-flight (8 min) beside it. Installs 4 min. HU-L 10 min, HU-C 9 min, HU-W10 4 min, HP 3 min, POCO 5 min. Thermal waits about 3 min. Restore 5 min. **About 1 h in all, 31 min of it runs.** HU-W10 is optional; skip it if the operator does not accept its tap count.

## 1. Build and baseline

One APK. No baseline build: round 2 measured `0d333b19`, and nothing graded here has a control that needs the old code. History was not rewritten: `0d333b19` is an ancestor of `135131ce`.

| Arm | Ref | Commit | Tree |
|---|---|---|---|
| C (candidate) | upstream `refs/pull/1119/head` | `135131ce18430b52c773f2ce456f8c8683de9c0c` | `88890a5390bdc6831f5bc2a4fd258792740a2be6` |

C is the PR head itself: the author's 18 commits on `0d333b19` (which carries main `e9377ff9`). No fork branch carries it, so `build.sh` fetches the pull ref from upstream by URL. The dev host ran the JVM suite at `135131ce`: 3681 tests, 0 failures. Do not run the unit tests on the rig. No other queued round shares this base.

Work from `~/Coding/StudioProjects`. Save every script block under `$OUT/scripts/` with `python3 -I $RIG_TOOLKIT/extract_brief_blocks.py <this brief> $OUT/scripts`. Each block name is bold and in backticks, on the line right above its fence. Run every script with `bash $OUT/scripts/<name>.sh`. Only the scripts source `lib.sh`, and each script sets `H` **after** it sources `lib.sh`.

**`build.sh`**
```bash
. $(dirname $0)/lib.sh
C=135131ce18430b52c773f2ce456f8c8683de9c0c; T=88890a5390bdc6831f5bc2a4fd258792740a2be6
# reuse this session's worktree if WT is set; else make one (never pick an arbitrary folder under worktrees/)
WT=${WT:-$($RIG_TOOLKIT/wt-new.sh pr-1119-home-redesign candidate main | tail -1)}
git -C $WT fetch https://github.com/andreknieriem/open-headunit.git refs/pull/1119/head
git -C $WT checkout --detach $C
[ "$(git -C $WT rev-parse 'HEAD^{tree}')" = $T ] || { echo TREE_MISMATCH; exit 1; }
$RIG_TOOLKIT/rig_thermal.sh wait 70
HUR_DIR=$WT MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh || { echo BUILD_FAILED; exit 1; }
cp $(ls -t $RIG_DATA/apks/com.andrerinas.headunitrevived_*.apk | head -1) $OUT/C.apk
{ echo "WT=$WT"; echo "C md5 $(md5sum < $OUT/C.apk | cut -c1-32)"
  echo "NoticeClearancePolicy=$(unzip -p $OUT/C.apk 'classes*.dex' | strings | grep -c NoticeClearancePolicy)"
  echo "SpeedPermissionPolicy=$(unzip -p $OUT/C.apk 'classes*.dex' | strings | grep -c SpeedPermissionPolicy)"; } | tee $OUT/build.txt
```

Build gate: `NoticeClearancePolicy` MUST be 1 or more, and `SpeedPermissionPolicy` MUST be 1 or more. Both classes are new since `0d333b19` (`5c591f8e` and `052f8967`), so the round 2 APK has 0 of each. A failed gate stops the round.

Identity after each install (`inst` in `lib.sh` does both):

- `$RIG_TOOLKIT/apk_identity.sh $OUT/C.apk <serial>` MUST exit 0.
- The `ACTION_QUERY_STATE` reply MUST carry commit `135131ce1843`. A `-dirty` suffix is fine; say so.
- If `adb install -r -d` answers `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, stop and ask. Never uninstall.

## 2. What this is and why

PR #1119 replaces the home screen. Round 2 (`0d333b19`) passed all 11 graded runs, with B5 UNTESTABLE. The author then pushed 18 commits and asked, on 2026-10-23, for these checks on hardware: Cancel during a wireless bring-up (also on page 2), widget sizes and a widget with a setup screen, shortcut favorites after the home role is lost, Back on the pages hint, D-pad focus, "End and stay armed" in the short exit menu, and choosing the app as home in the setup wizard. Our review of the update (`.claude/sdlc/pr-1119/review.md`, "third" and "fourth update") found no blocking issue and three warnings, U1 to U3.

| Id | Finding | Code | Run | Kind |
|---|---|---|---|---|
| B1 | Cancel on page 2 while the phone is woken | `dea62739` (round 2 PASS); the pill and card logic changed since in `70a1c1fa`, `a2c43328`, `2679a532` | HU-B1 | GATE |
| B5 | A widget that declares a setup screen gets it; a cancel releases the id | `26fda6ac`; the release path changed in `985687f3` | HU-B5 | GATE |
| W12 | A D-pad reaches the long press on the main button and on a widget | `b760a49e`; the card stays focusable after `59831d05` | HU-W12a, HU-W12b | GATE |
| W12 | A long press in the empty space beside the widget row opens the page menu | `59831d05` | HU-ROW | GATE |
| CD | Settings opened during the auto-connect countdown crashed the app | `1834b283` | HU-CD | GATE |
| W8 | The short exit menu keeps "End session, stay ready", and the option keeps the network up | `6a47ecf9` (round 2 PASS on the menu only) | HU-W8 | GATE |
| U1 | The pill's X also stops a countdown for another connection | `a2c43328` | HU-U1 | RECORD |
| U3 | The speed switch asks for precise location alone | `052f8967` | POCO-U3a, POCO-U3b | RECORD |
| N2 | On API 29 and later the wizard's launcher switch flips off with no dialog | upstream #1160 | HU-W10 | RECORD |
| ring | The countdown ring sat on a black disc below API 21 | `135131ce` | HP-RING | RECORD |

A RECORD run reports what it saw. It does not pass or fail the PR.

Not run, with the reason:

- **B4** (shortcut favorites after the home role is lost): round 2 HU-B4 PASSed on D-HU, and no commit since touches `loadFavorites`, `shortcutFavorite` or `FavoriteAppsPolicy`.
- **B6** (Back closes the pages hint): round 2 PASSed it on D-HU and D-POCO. The fix (`b760a49e`, `2ae94459`) is unchanged since.
- **B2** (widget width): round 2 PASSed it on D-HU and D-POCO. `HomeWidgets.minWidthPx` and the row sizing are unchanged since.
- **B3** (widget size told in dp): no instrument. `dumpsys appwidget` prints no widget options, and `HomeWidgets.tellSize` logs only a failure.
- **W12 long press below API 21**: no scripted way. A View fires its long click from a key only when the key stays down for the long-press timeout. D-HP is not rooted and has no physical D-pad. Round 2 HP-W12 already proved that the card takes focus there.
- **U2** (the wizard's own task can be trimmed on API 30 and later): it needs the system to trim a task, which no run here can force.

## 3. What is different about this round

- **Round 2 errata, fixed here.** `H` is set after `lib.sh` is sourced in every script. No run in this round needs a LAN path between two units. If a re-run ever needs one, gate it on `tcpprobe` (section 5, a TCP connect from the host to the port), not on ping: round 2 found D-HP projecting against D-POCO's server while ICMP failed both ways. B5 now picks VLC's widget on D-HU, whose setup activity round 2's pre-flight listed (`org.videolan.vlc/.widget.MiniPlayerConfigureActivity`). Pre-flight checks it again; without it, HU-B5 is UNTESTABLE and the round goes on.
- **HU-B5 finds the VLC row by its app name.** The picker lists widgets sorted by app name, so VLC is near the end. If no VLC row is on screen, the run sends DPAD_DOWN, then MOVE_END, then PAGE_UP twice, with a dump after each, and stops at the first dump that shows a VLC row. This is a bounded search by keys, never a swipe.
- **What a B5 PASS looks like if the code did nothing.** Before `26fda6ac` the app placed every widget at once and never opened a setup screen. So the setup screen coming up (`focus` names `org.videolan.vlc`) is the reach condition. If VLC's widget declares the setup as optional (Android 12 and later), the app places it directly; that is correct behaviour, and B5 is then UNTESTABLE again, with the reason.
- **The D-pad long press.** The card (`home_primary_card`) and each widget frame (`home_widget_frame`) are focusable and long-clickable. A View starts its long-press timer on a DPAD_CENTER down with no repeat, and drops it on the up. So the key must stay down for the long-press timeout. Pre-flight reads D-HU's `input` usage: if it lists `--duration`, the run holds the key for 1000 ms; else it uses `--longpress`. The `dispatchKeyEvent` lines give the real hold: the time from `keyCode=23, action=0` to `keyCode=23, action=1`. A hold shorter than the timeout makes that run INCONCLUSIVE, not FAIL.
- **The runs press DPAD_CENTER only on the target itself.** The card holds two buttons (its action and Cancel), and a widget can hold its own buttons. A press on the action button would start a connection. So the run long-presses only when the dump says the focused node is the card or the frame. Focus on a node inside the target is reported as `INSIDE`, and the run stops there.
- **U1 waits for a listening step.** In Native mode the card shows the countdown only while the stage is a listening one (`ARMED`, `WAITING_FOR_PHONE`). During `WAKING_PHONE` the card names the step and hides the countdown, because that step counts as "connecting" in `HomePrimaryPolicy`. So HU-U1 taps the pill's X right after a bare listening step line (the pill is shown) with the countdown on the card. The countdown aims at USB (`auto-connect-single-usb`); D-HU has no phone on its USB port, so a USB attempt that does fire changes nothing.
- **The short exit menu needs the home role, not the switch.** `ProjectionExitMenuPolicy.options` makes the menu short when `CarLauncherManager.isDefaultLauncher` is true, which on API 29 and later is the HOME role. A declined role gives the long menu. So HU-W8 takes the role after the launch, as round 2 did.
- **HU-CD and HU-U1 need the disclaimer accepted.** `attemptSingleUsbAutoConnect` returns at once without `has-accepted-disclaimer`. The seeds write it `true`.
- **U3 runs on D-POCO.** D-HU refuses `pm revoke` of location (round 1: `not a changeable permission type`), so the switch there never asks. `SettingsActivity` is not exported. D-POCO is not rooted, so POCO-U3 opens Customization with `run-as $PKG am start`, which runs as the app's own uid. If that start does not land, POCO-U3 is UNTESTABLE (no scripted route); record the `am` output.
- **On mode 3 the launch arms the stack.** HU-C sends `ACTION_START_WIRELESS_SCAN` only to lift the pill's X, long after the launch.
- **While this app holds the home role, a force-stop starts it again.** The helpers give the role back (`role_off`) before every force-stop and settings write.
- **Animations are off on D-HU, D-POCO and D-HP** for the round. Endless animations stop `uiautomator dump`. Pre-flight saves each value.
- **The audio settings are a deliberate worst case.** Do not touch `use-aac-audio`, `audio-latency-multiplier` or `audio-queue-capacity`.
- **Clocks drift.** Every window is by marker text. Never compare times across two units.
- **A phone leaves the link by `svc`**, not airplane mode. D-POCO's WiFi turns itself back on, so a 3 s loop holds it off during HU-C.

## 4. Settings keys

`seed` in `lib.sh` writes these with the app stopped: `set_hu_settings_host.py` on D-HU (rooted), `set_prefs_runas_host.py` on D-POCO and D-HP. It reads the keys back with `showkeys.py`. `idels` adds a `del:` for every key whose name starts with `connection-issue-`. `restore.sh` puts every backup back at the end.

| Key | Type | `BASE0` (every seed) | `K_CD` (HU-CD) | `K_HUL` (HU-B5 to HU-ROW) | `K_HUC` (HU-C) | `K_W10` (HU-W10) | `K_HP` (HP-RING) | `K_POCO` (POCO-U3) | Why |
|---|---|---|---|---|---|---|---|---|---|
| `onboarding-version` | int | | 2 | 2 | 2 | 1 | 2 | 2 | 1 opens the wizard (`CURRENT_ONBOARDING_VERSION` is 2) |
| `rename_notice_shown_v2` | boolean | true | | | | | | | no notice |
| `aa174_notice_shown` | boolean | true | | | | | | | no notice |
| `native-driver-selection-mode` | int | 0 | | | | | | | no driver selector |
| `log-level` | int | 2 | | | | | | | INFO; every graded line is INFO or WARN |
| `connection-modes` | set | `usb,wifi,self` | | | | | | | every tile; the wizard's Next is enabled |
| `video-profile-starvation-cap` | | deleted | | | | | | | |
| `has-accepted-disclaimer` | boolean | true | | | | | | | the USB auto-connect and the wizard's safety step |
| `auto-connect-last-session` | boolean | false | | | | | | | |
| `auto-start-self-mode` | boolean | false | | | | | | | |
| `auto-connect-single-usb` | boolean | | true | false | true | false | true | false | the countdown's target is USB |
| `auto-connect-delay-seconds` | int | | 25 | 0 | 60 | 0 | 20 | 0 | the countdown |
| `wifi-connection-mode` | int | | 0 | 0 | 3 | 0 | 0 | 0 | idle, or Native AA |
| `enable-car-launcher` | boolean | | false | true | true | false | false | false | launcher mode where the page menu or the short exit menu needs it |
| `show-home-favorite-apps` | boolean | | | true | | | | | the widget row |
| `home-pages-hint-shown` | boolean | | true | true | true | | true | | no hint in the way |
| `home-widget-pages` | string | | | deleted | `p` | | | | none; one empty page, so page 2 exists |
| `home-widgets` | string | | | deleted | deleted | | | | an empty widget row |
| `home-favorite-apps` | string | | | | deleted | | | | |
| `native-poke-all-paired` | boolean | | | | true | | | | |
| `native-poke-bt-macs` | set | | | | `A0:46:5A:97:E4:95` (D-MOTO) | | | | the wake list |
| `native-ap-transport` | int | | | | 0 | | | | WiFi Direct: the menu offers "End session, stay ready" |
| `external-bt-zbt-transport` | boolean | | | | false | | | | |
| `show-home-speed` | boolean | | | | | | | false | the switch starts off |

`ACTION_LOG_MARKER` is not in `AutomationCommandPolicy.CONFIGURING` at `135131ce`, so `allow-external-configuration` is not needed.

## 5. Helpers

Run every adb call in sequence, never two at once against one unit. The `hold_on` loop and the `logcat` captures are the only standing streams. Write the results file after every run.

**`lib.sh`**
```bash
source ~/Coding/StudioProjects/rig-toolkit/rig_devices.sh
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
MAIN=$PKG/com.andrerinas.openheadunit.main.MainActivity
SETACT=$PKG/com.andrerinas.openheadunit.main.SettingsActivity
ALIAS=$PKG/com.andrerinas.openheadunit.CarLauncherAlias
D_HU=${D_HU:-27870808938846}; D_POCO=${D_POCO:-4f4027e9}; D_MOTO=${D_MOTO:-ZY22GC3BM4}; D_HP=${D_HP:-CNU350BGBJ}; P=$D_POCO
OUT=$RIG_DATA/rounds/pr-1119-home-redesign-round3; S=$OUT/scripts; mkdir -p $S $OUT/ui
export LC_ALL=C
[ -f $OUT/env.sh ] && . $OUT/env.sh       # HU_OWNER HU_LPT LPARGS: every preflight.sh rewrites it
[ -f $OUT/orig.sh ] && . $OUT/orig.sh     # opening radios, animations, grants, alias: the first preflight.sh only
[ -f $OUT/cust.sh ] && . $OUT/cust.sh     # CUST: install.sh writes it
STEP="MainActivity: status pill step: "
BASE0="scalar:boolean:rename_notice_shown_v2:true scalar:boolean:aa174_notice_shown:true scalar:int:native-driver-selection-mode:0
  scalar:int:log-level:2 set:connection-modes:usb,wifi,self del:video-profile-starvation-cap scalar:boolean:has-accepted-disclaimer:true
  scalar:boolean:auto-connect-last-session:false scalar:boolean:auto-start-self-mode:false"
ONB="scalar:int:onboarding-version:2"
K_CD="$BASE0 $ONB scalar:boolean:auto-connect-single-usb:true scalar:int:auto-connect-delay-seconds:25 scalar:int:wifi-connection-mode:0
  scalar:boolean:enable-car-launcher:false scalar:boolean:home-pages-hint-shown:true"
K_HUL="$BASE0 $ONB scalar:boolean:auto-connect-single-usb:false scalar:int:auto-connect-delay-seconds:0 scalar:int:wifi-connection-mode:0
  scalar:boolean:enable-car-launcher:true scalar:boolean:show-home-favorite-apps:true scalar:boolean:home-pages-hint-shown:true
  del:home-widget-pages del:home-widgets"
K_HUC="$BASE0 $ONB scalar:boolean:auto-connect-single-usb:true scalar:int:auto-connect-delay-seconds:60 scalar:int:wifi-connection-mode:3
  scalar:boolean:enable-car-launcher:true scalar:boolean:home-pages-hint-shown:true scalar:string:home-widget-pages:p del:home-widgets
  del:home-favorite-apps scalar:boolean:native-poke-all-paired:true set:native-poke-bt-macs:A0:46:5A:97:E4:95
  scalar:int:native-ap-transport:0 scalar:boolean:external-bt-zbt-transport:false"
K_W10="$BASE0 scalar:int:onboarding-version:1 scalar:boolean:auto-connect-single-usb:false scalar:int:auto-connect-delay-seconds:0
  scalar:int:wifi-connection-mode:0 scalar:boolean:enable-car-launcher:false"
K_HP="$BASE0 $ONB scalar:boolean:auto-connect-single-usb:true scalar:int:auto-connect-delay-seconds:20 scalar:int:wifi-connection-mode:0
  scalar:boolean:enable-car-launcher:false scalar:boolean:home-pages-hint-shown:true"
K_POCO="$BASE0 $ONB scalar:boolean:auto-connect-single-usb:false scalar:int:auto-connect-delay-seconds:0 scalar:int:wifi-connection-mode:0
  scalar:boolean:enable-car-launcher:false scalar:boolean:show-home-speed:false"

send() { local a=$1; shift; adb -s $H shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; sleep 0.3; }
mark() { local s; send ACTION_LOG_MARKER --es text "$1" >/dev/null
  for s in $PHONES; do adb -s $s shell log -t RIGMARK "$1"; sleep 0.3; done; echo "$1 $(date +%T)" >> $OUT/marks.txt; }
launch() { adb -s $H shell am start -n $MAIN >/dev/null; sleep 0.3; }
running() { { adb -s $1 shell ps; adb -s $1 shell ps -A; } 2>/dev/null | grep -ac "$PKG"; }
apid() { adb -s $H shell pidof $PKG | tr -d '\r'; }
# headunit://exit only to a running app; sent to a stopped one it starts and arms it (rig-quirks/topics/lifecycle.md)
stopapp() { if [ "$(running $1)" -ge 1 ]; then adb -s $1 shell am start -a android.intent.action.VIEW -d headunit://exit >/dev/null; sleep 5; fi
  adb -s $1 shell am force-stop $PKG; sleep 1; }
# the HOME role, D-HU only; pre-flight wrote the factory holder to home-role-<serial>.txt
role_on()  { adb -s $D_HU shell cmd role add-role-holder android.app.role.HOME $PKG; sleep 1
  echo "on: $(adb -s $D_HU shell cmd role get-role-holders android.app.role.HOME | tr -d '\r')" | tee -a $OUT/role.log >&2; }
role_off() { adb -s $D_HU shell cmd role add-role-holder android.app.role.HOME "$(cat $OUT/home-role-$D_HU.txt)"; sleep 1
  echo "off: $(adb -s $D_HU shell cmd role get-role-holders android.app.role.HOME | tr -d '\r')" | tee -a $OUT/role.log >&2; }
# a LAN gate for any re-run that needs one: a TCP connect from the host, never ping (round 2 erratum)
tcpprobe() { timeout 5 bash -c "</dev/tcp/$1/$2" 2>/dev/null && echo TCP_OK || echo TCP_FAILED; }

# captures: started before the launch, always with stdbuf -oL
cap() { adb -s $2 logcat -G 16M >/dev/null 2>&1; adb -s $2 logcat -c; stdbuf -oL adb -s $2 logcat -v time > $1 & echo $! > $1.pid; sleep 1; }
# D-HP's capture can die silently: check every 60 s, restart with >> (rig-quirks/units/D-HP.md)
capwatch() { ( while :; do sleep 60; kill -0 $(cat $1.pid) 2>/dev/null || { stdbuf -oL adb -s $2 logcat -v time >> $1 & echo $! > $1.pid; echo "capture restarted $1 $(date +%T)" >> $OUT/marks.txt; }; done ) & echo $! > $1.watch; }
uncap() { [ -f $1.watch ] && kill $(cat $1.watch) 2>/dev/null; kill $(cat $1.pid) 2>/dev/null; }

# seg <file> <from> [<to>]: lines after the last <from> marker up to the next <to> marker (or the end).
# A label matches only at the end of a line, so HU-B1_x never matches HU-B1_x2.
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
# bare <file> <from> <to> <stage>: step lines naming the stage with the pill shown (no "(not shown" suffix)
bare()  { seg "$1" "$2" "$3" | grep -aF "$STEP" | grep -aF -- "$4" | grep -avF '(not shown' | grep -acvF hidden; }
rend() { seg "$1" "$2" "$3" | grep -aF 'Throughput over' | grep -aoE 'rendered=[0-9]+' | awk -F= '$2>0' | wc -l; }
ssl()  { seg "$1" "$2" "$3" | grep -aF 'SSL handshake complete' | grep -acvF 'Handshake: SSL handshake complete'; }
fatal() { seg "$1" "$2" "$3" | grep -aF -A3 'FATAL EXCEPTION' | grep -acF "Process: $PKG"; }
# waitfor <file> <from> <cap-s> <string>: 0 as soon as the string appears after <from>
waitfor() { local f=$1 m=$2 cap=$3 s=$4 t=0
  while [ $t -lt $cap ]; do [ "$(cnt $f $m '' "$s")" -ge 1 ] && return 0; sleep 1; t=$((t+1)); done; return 1; }
# waitany <file> <from> <cap-s> <s1> <s2>: 0 as soon as either string appears after <from>
waitany() { local f=$1 m=$2 cap=$3 t=0
  while [ $t -lt $cap ]; do [ $(( $(cnt $f $m '' "$4") + $(cnt $f $m '' "$5") )) -ge 1 ] && return 0; sleep 1; t=$((t+1)); done; return 1; }
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
perms() { adb -s $P shell dumpsys package $PKG | grep -aE 'ACCESS_(FINE|COARSE)_LOCATION: granted' | sort -u | tr -d '\r'; }

# settings
pull_settings() { if [ $1 = $D_HU ]; then adb -s $1 shell cat /data/data/$PKG/shared_prefs/settings.xml
  else adb -s $1 shell run-as $PKG cat shared_prefs/settings.xml; fi | tr -d '\r'; }
push_settings() { local s=$1 f=$2; adb -s $s shell am force-stop $PKG; sleep 1
  adb -s $s push $f /data/local/tmp/ohu-r3.xml >/dev/null; adb -s $s shell chmod 644 /data/local/tmp/ohu-r3.xml
  if [ $s = $D_HU ]; then adb -s $s shell cp /data/local/tmp/ohu-r3.xml /data/data/$PKG/shared_prefs/settings.xml
    adb -s $s shell chown $HU_OWNER /data/data/$PKG/shared_prefs/settings.xml; adb -s $s shell chmod 660 /data/data/$PKG/shared_prefs/settings.xml
  else adb -s $s shell run-as $PKG cp /data/local/tmp/ohu-r3.xml shared_prefs/settings.xml; fi
  pull_settings $s | diff -B - $f >/dev/null && echo SETTINGS_BACK || echo SETTINGS_DIFFER; }
# idels <serial>: a del: spec for every connection-issue-* key the unit holds now
idels() { pull_settings $1 > $OUT/cur-$1.xml; python3 -I $S/showkeys.py --dels connection-issue- $OUT/cur-$1.xml; }
# seed <serial> <key-spec>...: app stopped, write, read back
seed() { local s=$1; shift; adb -s $s shell am force-stop $PKG; sleep 1
  if [ $s = $D_HU ]; then python3 -I $RIG_TOOLKIT/set_hu_settings_host.py $s "$@"
  else python3 -I $RIG_TOOLKIT/set_prefs_runas_host.py $s "$@"; fi >> $OUT/seed.log 2>&1
  pull_settings $s > $OUT/seeded-$s.xml
  python3 -I $S/showkeys.py $OUT/seeded-$s.xml onboarding-version has-accepted-disclaimer wifi-connection-mode log-level connection-modes \
    enable-car-launcher auto-connect-single-usb auto-connect-delay-seconds auto-connect-last-session auto-start-self-mode \
    native-poke-bt-macs native-ap-transport home-widget-pages home-widgets home-pages-hint-shown show-home-favorite-apps \
    show-home-speed video-profile-starvation-cap; }
# inst <serial>: install C and prove it (section 1)
inst() { local s=$1 H=$1; adb -s $s install -r -d $OUT/C.apk | tail -1
  $RIG_TOOLKIT/apk_identity.sh $OUT/C.apk $s; echo "apk_identity $s exit $?"
  send ACTION_QUERY_STATE | grep -ao 'commit[^,}]*'; adb -s $s shell am force-stop $PKG; }

# screen: messages go to stderr, so a function can return a word on stdout
uiq() { python3 -I $S/uiq4.py "$@"; }
uidump() { adb -s $H shell rm -f /sdcard/ohu-ui.xml; sleep 0.3
  adb -s $H shell uiautomator dump /sdcard/ohu-ui.xml >/dev/null 2>&1; sleep 0.3
  adb -s $H pull /sdcard/ohu-ui.xml $OUT/ui/$1.xml >/dev/null 2>&1; sleep 0.3
  adb -s $H shell screencap -p /sdcard/ohu-ui.png; sleep 0.3; adb -s $H pull /sdcard/ohu-ui.png $OUT/ui/$1.png >/dev/null 2>&1
  [ -s $OUT/ui/$1.xml ] || { adb -s $H shell uiautomator dump /sdcard/ohu-ui.xml >/dev/null 2>&1; sleep 0.3
    adb -s $H pull /sdcard/ohu-ui.xml $OUT/ui/$1.xml >/dev/null 2>&1; }
  [ -s $OUT/ui/$1.xml ] || echo "$1 DUMP_FAILED" | tee -a $OUT/marks.txt >&2; }
focus() { local f i; for i in 1 2 3; do f=$(adb -s $H shell dumpsys window | grep -a -m1 mCurrentFocus | tr -d '\r')
  echo "$f" | grep -q 'mCurrentFocus=null' || break; sleep 1; done; echo "$f"; }
# gest <run> <tap|long|next> <selector> <dump-label>: one gesture on the one dumped match; TAPCAP (5) per run
gest() { local R=$1 kind=$2 sel=$3 xml=$OUT/ui/$4.xml n xy x y l t r b
  n=$(cat $OUT/taps-$R 2>/dev/null || echo 0)
  [ "$n" -ge ${TAPCAP:-5} ] && { echo "$R BUDGET_SPENT" | tee -a $OUT/marks.txt >&2; return 3; }
  if [ $kind = next ]; then xy=$(uiq box $xml "$sel") || { echo "$R $sel NO_TARGET $xy" | tee -a $OUT/marks.txt >&2; return 2; }
    read l t r b <<< "$xy"; x=$((l+(r-l)*8/10)); y=$(((t+b)/2))
    adb -s $H shell input swipe $x $y $((l+(r-l)*2/10)) $y 300
  else xy=$(uiq center $xml "$sel") || { echo "$R $sel NO_TARGET $xy" | tee -a $OUT/marks.txt >&2; return 2; }
    read x y <<< "$xy"
    if [ $kind = long ]; then adb -s $H shell input swipe $x $y $x $y 1200; else adb -s $H shell input tap $x $y; fi
  fi
  echo $((n+1)) > $OUT/taps-$R; echo "$R $kind $sel $x $y $(date +%T)" | tee -a $OUT/gestures.log >&2; sleep 2; }
# gestxy <run> <tap|long> <x> <y> <why>: one gesture on a point a uiq command computed from a dump; same budget
gestxy() { local R=$1 kind=$2 x=$3 y=$4 n; n=$(cat $OUT/taps-$R 2>/dev/null || echo 0)
  [ "$n" -ge ${TAPCAP:-5} ] && { echo "$R BUDGET_SPENT" | tee -a $OUT/marks.txt >&2; return 3; }
  if [ $kind = long ]; then adb -s $H shell input swipe $x $y $x $y 1200; else adb -s $H shell input tap $x $y; fi
  echo $((n+1)) > $OUT/taps-$R; echo "$R $kind xy:$5 $x $y $(date +%T)" | tee -a $OUT/gestures.log >&2; sleep 2; }
# key <run> <keyevent args...>: one key event; KEYCAP (6) per run
key() { local R=$1 n; shift; n=$(cat $OUT/keys-$R 2>/dev/null || echo 0)
  [ "$n" -ge ${KEYCAP:-6} ] && { echo "$R KEY_BUDGET_SPENT" | tee -a $OUT/marks.txt >&2; return 3; }
  adb -s $H shell input keyevent "$@" >/dev/null 2>&1
  echo $((n+1)) > $OUT/keys-$R; echo "$R key $* $(date +%T)" | tee -a $OUT/keys.log >&2; sleep 2; }
# lpkey <run>: DPAD_CENTER held for the long press (LPARGS from pre-flight)
lpkey() { key $1 $LPARGS KEYCODE_DPAD_CENTER; }
holdms() { python3 -I $S/hold.py "$@"; }
# walkto <run> <selector> <label>: up to 4 D-pad moves toward the match, a dump after each; prints HERE, INSIDE or the last answer
walkto() { local R=$1 sel=$2 L=$3 i d=NOFOCUS
  for i in 1 2 3 4; do
    if [ $i -gt 1 ]; then d=$(uiq dir $OUT/ui/$L-m$((i-1)).xml "$sel"); case $d in HERE|INSIDE|NOTARGET) echo $d; return; esac; fi
    [ $d = NOFOCUS ] && d=DPAD_DOWN
    key $R KEYCODE_$d || { echo BUDGET; return; }; uidump $L-m$i
    echo "$L m$i $d focus=$(uiq focusid $OUT/ui/$L-m$i.xml)" | tee -a $OUT/$R-walk.txt >&2
  done; uiq dir $OUT/ui/$L-m4.xml "$sel"; }
```

**`uiq4.py`**
```python
# uiq4.py <cmd> <dump.xml> [args]: reads a uiautomator dump (round 2's uiq3 plus five commands).
#   center | box <xml> <sel>    "x y" or "l t r b" of the one match; exit 1 on 0 or 2+ matches
#   count <xml> <sel>           matches with a non-empty box
#   inwin <xml> <sel>           1 if exactly one match and it lies inside the window, else 0
#   text <xml> <sel>            the label of the one match (its text, else its children's, joined by " | ")
#   checked <xml> <sel>         the checked attribute of the one match
#   focusid <xml>               the id (else the class) of the focused node; "none" when nothing has focus
#   dir <xml> <sel>             HERE (the focused node is the match), INSIDE (another node inside the match has focus),
#                               NOFOCUS, NOTARGET, or the D-pad key along the larger offset from the focused node to the match
#   rowof <xml> <id> <part> <k> "x y" of the k-th list row holding a node with that id whose text contains <part>; exit 1 if none
#   emptyright <xml> <row-id> <child-id>...  "x y" in the free part of the row right of its last child; exit 1 under 40 px
# sel: id:NAME, idnth:NAME:K (the K-th match), text:EXACT, textin:PART, textre:REGEX (whole text), desc:EXACT
import sys, re, xml.etree.ElementTree as ET
def box(n):
    m = re.findall(r'-?\d+', n.get('bounds', ''))
    return [int(v) for v in m] if len(m) == 4 else None
def ok(n):
    b = box(n)
    return b is not None and b[2] > b[0] and b[3] > b[1]
def rid(n):
    r = n.get('resource-id') or ''
    return r.split(':id/')[-1] if ':id/' in r else ''
def label(n):
    return n.get('text') or ' | '.join(c.get('text') for c in n.iter('node') if c.get('text'))
def ctr(b):
    return (b[0] + b[2]) // 2, (b[1] + b[3]) // 2
def match(nodes, sel):
    kind, _, val = sel.partition(':')
    nth = None
    if kind == 'idnth':
        val, _, k = val.rpartition(':')
        nth = int(k)
    out = []
    for n in nodes:
        t = n.get('text') or ''
        if kind in ('id', 'idnth') and rid(n) == val: out.append(n)
        elif kind == 'text' and t == val: out.append(n)
        elif kind == 'textin' and val in t: out.append(n)
        elif kind == 'textre' and re.fullmatch(val, t): out.append(n)
        elif kind == 'desc' and n.get('content-desc') == val: out.append(n)
    out = [n for n in out if ok(n)]
    if nth is not None:
        return out[nth - 1:nth] if len(out) >= nth else []
    return out
cmd, path = sys.argv[1], sys.argv[2]
root = ET.parse(path).getroot()
nodes = list(root.iter('node'))
parent = {c: p for p in root.iter() for c in p}
win = box(nodes[0]) if nodes else None
focused = [n for n in nodes if n.get('focused') == 'true' and ok(n)]
if cmd == 'focusid':
    print((rid(focused[-1]) or focused[-1].get('class')) if focused else 'none')
    sys.exit(0)
if cmd == 'dir':
    m = match(nodes, sys.argv[3])
    if len(m) != 1:
        print('NOTARGET'); sys.exit(0)
    if not focused:
        print('NOFOCUS'); sys.exit(0)
    f, t = focused[-1], m[0]
    if f is t:
        print('HERE'); sys.exit(0)
    fx, fy = ctr(box(f))
    tb = box(t)
    if tb[0] <= fx <= tb[2] and tb[1] <= fy <= tb[3]:
        print('INSIDE'); sys.exit(0)
    tx, ty = ctr(tb)
    dx, dy = tx - fx, ty - fy
    if abs(dy) >= abs(dx):
        print('DPAD_DOWN' if dy > 0 else 'DPAD_UP')
    else:
        print('DPAD_RIGHT' if dx > 0 else 'DPAD_LEFT')
    sys.exit(0)
if cmd == 'rowof':
    want, part, k = sys.argv[3], sys.argv[4], int(sys.argv[5])
    rows = []
    for n in nodes:
        if rid(n) == want and part in (n.get('text') or '') and ok(n):
            r = n
            while r in parent and parent[r].get('class') not in ('android.widget.ListView', 'androidx.recyclerview.widget.RecyclerView'):
                r = parent[r]
            if r in parent and ok(r) and r not in rows:
                rows.append(r)
    if len(rows) < k:
        sys.exit(1)
    print(*ctr(box(rows[k - 1])))
    sys.exit(0)
if cmd == 'emptyright':
    rows = match(nodes, 'id:' + sys.argv[3])
    if len(rows) != 1:
        sys.exit(1)
    rb = box(rows[0])
    edge = min(rb[2], win[2]) if win else rb[2]
    kids = set(sys.argv[4:])
    rights = [box(n)[2] for n in nodes if rid(n) in kids and ok(n) and box(n)[0] >= rb[0] and box(n)[2] <= rb[2]]
    right = max(rights) if rights else rb[0]
    if edge - right < 40:
        sys.exit(1)
    print((right + edge) // 2, (rb[1] + rb[3]) // 2)
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
if cmd == 'center': print(*ctr(b))
elif cmd == 'box': print(*b)
elif cmd == 'text': print(label(m[0]))
elif cmd == 'checked': print(m[0].get('checked'))
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

**`hold.py`**
```python
# hold.py <file> <from> <to>: ms from the first DPAD_CENTER down to the first DPAD_CENTER up after it, between the
# two markers (a label matches only at the end of a line); NA when either line is missing.
import sys, re
f, a, b = sys.argv[1:4]
DOWN = 'dispatchKeyEvent: keyCode=23, action=0'
UP = 'dispatchKeyEvent: keyCode=23, action=1'
def ends(l, lab):
    return l.rstrip('\r\n').endswith(': ' + lab)
def ms(l):
    m = re.match(r'\d\d-\d\d (\d\d):(\d\d):(\d\d)\.(\d{3})', l)
    return None if not m else ((int(m[1]) * 60 + int(m[2])) * 60 + int(m[3])) * 1000 + int(m[4])
rows, on = [], False
for l in open(f, errors='replace'):
    if ends(l, a):
        rows, on = [], True
        continue
    if on and ends(l, b):
        on = False
    if on:
        rows.append(l)
t0 = None
for l in rows:
    if t0 is None and DOWN in l:
        t0 = ms(l)
    elif t0 is not None and UP in l:
        t1 = ms(l)
        print(t1 - t0 if t1 is not None else 'NA')
        sys.exit(0)
print('NA')
```

**`b5check.py`**
```python
# b5check.py <appwidget.txt> <settings.xml> [released id ...]: the widget ids bound to this app's host against home-widgets
import sys, re, xml.etree.ElementTree as ET
aw, sx = sys.argv[1], sys.argv[2]
rel = [int(x) for x in sys.argv[3:] if x.isdigit()]
ours, wid, sect = set(), None, ''
for l in open(aw, errors='replace'):
    s = l.strip()
    if s.startswith('Widgets:'): sect = 'w'
    elif s.startswith('Hosts:') or s.startswith('Grants:') or s.startswith('Providers:'): sect = ''
    if sect != 'w': continue
    m = re.match(r'\[\d+\] id=(\d+)', s)
    if m: wid = int(m.group(1))
    elif wid is not None and s.startswith('host=') and 'com.andrerinas.headunitrevived' in s: ours.add(wid)
stored = []
for e in ET.parse(sx).getroot():
    if e.get('name') == 'home-widgets':
        stored = [int(x) for x in (e.text or '').split(',') if x.strip().isdigit()]
print('our_ids=%s stored=%s released=%s released_still_bound=%d released_still_stored=%d leaked=%s' % (
    sorted(ours), stored, rel, len([r for r in rel if r in ours]), len([r for r in rel if r in stored]),
    sorted(ours - set(stored))))
```

## 6. Pre-flight and install

Run `build.sh` in the background and `preflight.sh` in the foreground beside it: pre-flight is adb only. Run `install.sh` once the build gate passes.

**`preflight.sh`**
```bash
. $(dirname $0)/lib.sh
exec > >(tee -a $OUT/preflight.txt) 2>&1
echo "==== preflight $(date)"
$RIG_TOOLKIT/rig_preflight.sh D_HU:wifi D_HP:wifi D_POCO:wifi; $RIG_TOOLKIT/rig_cleanup.sh list
adb devices                                                     # MUST list D-HU, D-HP, D-POCO and D-MOTO
FIRST=no; [ -f $OUT/orig.sh ] || FIRST=yes
for s in $D_HU $D_POCO $D_HP; do
  adb devices | grep -q "^$s" || { echo "== $s not on adb"; continue; }
  echo "== $s sdk $(adb -s $s shell getprop ro.build.version.sdk | tr -d '\r') locale $(adb -s $s shell getprop persist.sys.locale | tr -d '\r')$(adb -s $s shell getprop persist.sys.language | tr -d '\r')"
  echo "app running $(running $s)"; stopapp $s
  if [ $FIRST = yes ]; then pull_settings $s > $OUT/backup-$s.xml
    adb -s $s pull $(adb -s $s shell pm path $PKG | cut -d: -f2 | tr -d '\r') $OUT/before-$s.apk >/dev/null; fi
  wc -c < $OUT/backup-$s.xml                                      # MUST be over 0
  python3 -I $S/showkeys.py $OUT/backup-$s.xml wifi-connection-mode video-profile-starvation-cap use-aac-audio \
    audio-latency-multiplier audio-queue-capacity onboarding-version has-accepted-disclaimer enable-car-launcher \
    home-widgets home-widget-pages show-home-speed auto-connect-delay-seconds
done
[ $FIRST = yes ] && adb -s $D_HU shell cmd role get-role-holders android.app.role.HOME | tr -d '\r' > $OUT/home-role-$D_HU.txt
echo "home role $(cat $OUT/home-role-$D_HU.txt)"                 # MUST NOT be empty and MUST NOT be $PKG; else stop and ask
echo "== D-HU"
adb -s $D_HU shell id                                             # MUST show uid=0(root)
HU_OWNER=$(adb -s $D_HU shell ls -ld /data/data/$PKG/shared_prefs | awk '{print $3":"$4}' | tr -d '\r'); echo "HU_OWNER=$HU_OWNER"
adb -s $D_HU shell wm size; adb -s $D_HU shell wm density
adb -s $D_HU shell appwidget grantbind --package $PKG --user 0; echo "grantbind rc=$?"
adb -s $D_HU shell cmd package query-activities --brief -a android.appwidget.action.APPWIDGET_CONFIGURE | grep -a videolan | tee $OUT/hu-vlc-configure.txt
echo "vlc configure activities $(grep -ac MiniPlayerConfigureActivity $OUT/hu-vlc-configure.txt)"   # 0: HU-B5 is UNTESTABLE, the round goes on
adb -s $D_HU shell dumpsys appwidget | grep -a 'cmp:ComponentInfo{org.videolan.vlc' | tee $OUT/hu-vlc-providers.txt
echo "providers in all $(adb -s $D_HU shell dumpsys appwidget | grep -ac 'provider ProviderId')"
adb -s $D_HU shell input 2>&1 | grep -ai keyevent | tee $OUT/hu-input-usage.txt
LPARGS="--longpress"; grep -q -- '--duration' $OUT/hu-input-usage.txt && LPARGS="--duration 1000"
HU_LPT=$(adb -s $D_HU shell settings get secure long_press_timeout | tr -d '\r'); case $HU_LPT in ''|null) HU_LPT=500;; esac
echo "hu bt $(btstate $D_HU)"
echo "== D-HP"
adb -s $D_HP shell input 2>&1 | grep -ai keyevent | tee $OUT/hp-input-usage.txt    # record only
echo "== D-POCO"
perms | tee $OUT/poco-perms-before.txt
adb -s $P shell dumpsys window | grep -a -m1 mCurrentFocus; adb -s $P shell input keyevent KEYCODE_HOME
echo "== D-MOTO"
adb -s $D_MOTO shell dumpsys window | grep -a -m1 mCurrentFocus   # Keyguard: hand step 1
adb -s $D_MOTO shell dumpsys package com.google.android.projection.gearhead | grep -a -m1 versionName
echo "moto throttle lines $(adb -s $D_MOTO logcat -d | grep -ac THROTTLE_LIMIT_EXCEEDED)"   # above 0: reboot D-MOTO first
if [ $FIRST = yes ]; then
  cat > $OUT/orig.sh <<EOF
HU_ANIM=$(adb -s $D_HU shell settings get global animator_duration_scale | tr -d '\r'); POCO_ANIM=$(adb -s $P shell settings get global animator_duration_scale | tr -d '\r'); HP_ANIM=$(adb -s $D_HP shell settings get global animator_duration_scale | tr -d '\r')
HU_BT=$(btstate $D_HU); POCO_BT=$(btstate $P)
MOTO_WIFI=$(adb -s $D_MOTO shell dumpsys wifi | grep -a -m1 -o 'Wi-Fi is [a-z]*' | awk '{print $3}'); MOTO_BT=$(btstate $D_MOTO)
POCO_FINE=$(grep -a 'ACCESS_FINE_LOCATION' $OUT/poco-perms-before.txt | grep -aoE 'granted=(true|false)' | head -1 | cut -d= -f2)
POCO_COARSE=$(grep -a 'ACCESS_COARSE_LOCATION' $OUT/poco-perms-before.txt | grep -aoE 'granted=(true|false)' | head -1 | cut -d= -f2)
HU_ALIAS=$(adb -s $D_HU shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME | grep -ac CarLauncherAlias)
EOF
fi
cat $OUT/orig.sh
for s in $D_HU $P $D_HP; do adb -s $s shell settings put global animator_duration_scale 0; done
adb -s $P shell svc bluetooth disable; sleep 3; echo "poco bt $(btstate $P)"   # MUST read false
ph_off $D_MOTO
echo "HU_OWNER=$HU_OWNER; HU_LPT=$HU_LPT; LPARGS=\"$LPARGS\"" | tee $OUT/env.sh
```

Pre-flight gates: each line marked MUST. A gate with a hand step: ask for the step, then run `preflight.sh` again. Put the whole `preflight.txt` in Setup notes, with D-MOTO's Gearhead version, `hu-vlc-configure.txt`, `hu-vlc-providers.txt`, both `input` usage files and `LPARGS`.

**`install.sh`**
```bash
. $(dirname $0)/lib.sh
exec > >(tee $OUT/install.txt) 2>&1
for s in $D_HU $D_POCO $D_HP; do echo "== $s"; inst $s; done
AAPT=$(ls ${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}/build-tools/*/aapt2 2>/dev/null | sort -V | tail -1)
HEX=$($AAPT dump resources $OUT/C.apk 2>/dev/null | grep -a 'id/customizationFragment' | grep -aoE '0x7f[0-9a-f]{6}' | head -1)
[ -n "$HEX" ] && echo "CUST=$((HEX))" > $OUT/cust.sh; echo "aapt2=$AAPT customizationFragment=$HEX"; cat $OUT/cust.sh
```

Every unit MUST show `apk_identity ... exit 0` and commit `135131ce1843`. `cust.sh` SHOULD hold a decimal number; without it POCO-U3 is UNTESTABLE.

## 7. The deciding lines

Every app string below was checked with `grep -F` in `app/src/main` at `135131ce`. Grep every capture with `-a`. Some lines are built at run time; the source holds the template:

- `MainActivity: status pill X pressed, stopping the bring-up` comes from `MainActivity: $source pressed, stopping the bring-up` with the default `source` `"status pill X"`. The card's Cancel passes `"home Cancel"`.
- `HomeFragment: Waiting 25s before attempting auto-connect...` comes from `HomeFragment: Waiting ${delaySec}s before attempting auto-connect...`. The helpers grep `HomeFragment: Waiting `.
- `dispatchKeyEvent: keyCode=23, action=0` comes from `AppLog.i("dispatchKeyEvent: keyCode=%d, action=%d", event.keyCode, event.action)`. Key codes: 4 BACK, 19 UP, 20 DOWN, 21 LEFT, 22 RIGHT, 23 DPAD_CENTER. Action 0 is down, 1 is up.
- `HomeFragment: widget <id> was not set up, releasing its id` comes from `HomeFragment: widget $appWidgetId $why, releasing its id`.
- `AapService: Native AA session ended; keeping the WIFI_DIRECT network up for the phone's return.` comes from `AapService: Native AA session ended; keeping the ${launcher?.strategy ?: "wireless"} network up for the phone's return.`. The helpers grep the fixed prefix.
- `WifiLauncher: Initializing WiFi Mode: NATIVE` comes from `WifiLauncher: Initializing WiFi Mode: ${newLauncher.mode}`.

Head unit captures (`$OUT/<block>-hu.txt`):

| Key | String | Meaning |
|---|---|---|
| L-RX | `AutomationReceiver: ` | a verb landed; a missing line voids the step |
| L-MARK | `AutomationMarker: ` | a marker landed |
| L-RES | `HomeFragment: onResume. isConnected=` | the home is up |
| L-WAIT | `HomeFragment: Waiting ` | a countdown started (one per home that ran its start-up) |
| L-USB | `HomeFragment: Requesting single-USB auto-connect via AapService` | a countdown ran out and its USB attempt fired |
| L-CDSTOP | `HomeFragment: auto-connect countdown stopped by the bring-up's cancel` | U1: the X stopped the countdown |
| L-CDEND | `HomeFragment: auto-connect cancelled from the home screen countdown` | the countdown ended cancelled; no attempt follows |
| L-KEY | `dispatchKeyEvent: keyCode=` | a key reached `MainActivity` |
| L-STEP | `MainActivity: status pill step: ` | the step; a suffix says who tells it |
| L-CANCEL | `pressed, stopping the bring-up` | with `status pill X` or `home Cancel` on the same line |
| L-HOLD | `AapService: the status pill's X stopped the wireless bring-up.` | the service held the stack down |
| L-POKE | `NativeAA: Attempting active poke to device` | a wake attempt |
| L-ARM | `WifiLauncher: Initializing WiFi Mode: ` | read the mode |
| L-SCAN | `AapService: Force-starting WIFI-Scan from UI` | the WiFi button verb reached the service |
| L-SSL | `SSL handshake complete` | the session formed (the helper drops the DEBUG twin) |
| L-MSSR | `Media Sink Setup Request: ` | projection |
| L-TP | `Throughput over` | projection; with `rendered=` above 0 |
| L-ESA | `AapProjectionActivity: User ended the session and asked to stay ready` | W8: the menu option ran |
| L-ESA-SVC | `AapService: ACTION_END_SESSION_STAY_ARMED received` | W8: the service got it |
| L-KEEP | `AapService: Native AA session ended; keeping the ` | W8: the network stays up |
| L-STOP | `AapService: Native AA user exit. Stopping active launcher.` | a plain Stop; MUST be 0 in W8 |
| L-GROUP | `createGroup SUCCESS` | a new P2P group |
| L-REL | `, releasing its id` | with `was not set up`: B5 released the cancelled widget |
| L-HOST | `set up through the host: ` | the setup activity is not exported (N1); B5 is not graded |
| L-NOWAIT | `a widget answer came back with no widget waiting` | record |
| L-ONB-OK | `OnboardingActivity: home role granted, car launcher mode stored` | W10: the role came |
| L-ONB-REV | `OnboardingActivity: home role not held after the request, turning the launcher switch back off` | W10: N2 |
| L-ALIAS | `CarLauncherManager: set launcher alias enabled=` | W10: the alias went on or off |
| L-ROLEFAIL | `CarLauncherManager: RoleManager failed: ` | W10: record |
| L-SPEED | `Location permission blocked for the home speed, offering the app settings` | U3: the policy said OPEN_APP_SETTINGS |
| L-MATCH | `MATCH! Starting AapService` | template section 4 discard rule |

Phone captures (`$OUT/HU-C-moto.txt`). Checked in the decompiled Gearhead 18.0.664204 for round 2, not in our tree:

| Key | String | Meaning |
|---|---|---|
| PH-END | `PROJECTION_ENDED_BYEBYE_BY_CAR` | the head unit ended the session cleanly |
| PH-CRIT | `Critical error` | the phone ended it on a protocol error |
| PH-THR | `THROTTLE_LIMIT_EXCEEDED` | Gearhead's RFCOMM throttle; above 0 calls the stop rule |
| | `RIGMARK` | the run's markers on the phone |

System lines: `FATAL EXCEPTION` with `Process: com.andrerinas.headunitrevived` on a line within 3 after it (the app crashed); `mCurrentFocus` (the window in front).

Screen texts the dumps match (`values/strings.xml` at `135131ce`; every rig unit reads English): `Add a page`, `Home screen pages`, `Remove widget`, `End session, stay ready`, `Stop Connection`, `Move to Background`, `Picture-in-Picture`, `Quick Settings`, `Switch Phone`, `Waking your phone`, `Stop connecting` (the pill X's content description). View ids (all in `app/src/main/res/layout` at `135131ce`): `home_primary_card`, `home_primary_cancel`, `home_primary_progress`, `home_countdown`, `home_pager`, `settings_button`, `auto_connect_pill`, `auto_connect_pill_cancel`, `widget_add_tile`, `widget_option_label`, `widget_option_app`, `home_widget_frame`, `home_widgets_row`, `onb_next`, `onb_launcher_switch`, `switch_show_speed`, `row_show_speed`. System ids: `android:id/button1` (the bind dialog), and the Android 12+ permission dialog's `permission_location_accuracy_radio_fine`, `permission_location_accuracy_radio_coarse`, `permission_allow_foreground_only_button`, `permission_allow_one_time_button`, `permission_deny_button` (package `com.android.permissioncontroller`).

## 8. Runs

Order: HU-L, HU-C, HU-W10 (optional), HP, POCO. HU-C runs before HU-W10, because the wizard can turn the launcher alias off and `role_on` needs it. A block starts only when the one before it has ended and its numbers are in the results file. `th_gate` applies the host thermal rule in `rig-quirks/topics/tooling.md` at the start of each block.

Common void rules: a verb with no `AutomationReceiver: ` line; `HOST_HOT`; a `DUMP_FAILED` on a dump a condition reads (that condition only); `NO_TARGET` on a gesture a condition needs. A void run is re-run once; a second void is UNTESTABLE. A seed read-back that does not show the run's keys is a setup failure: fix it and re-run once.

### HU-L: D-HU idle (HU-CD, HU-B5, HU-W12a, HU-W12b, HU-ROW) **(HU-B5 and HU-W12a/b are the point)**

Usage: `bash $OUT/scripts/hu_l.sh`. One capture. No phone: `wifi-connection-mode` 0 arms nothing. No home role in this block: the page menu needs only `enable-car-launcher`.

**`hu_l.sh`**
```bash
. $(dirname $0)/lib.sh
H=$D_HU; PHONES=""
HC=$OUT/HU-L-hu.txt
th_gate HU-L || exit 1
role_off; stopapp $D_HU
seed $D_HU $K_CD $(idels $D_HU) | tee $OUT/HU-CD-seed.txt   # MUST show auto-connect-single-usb=true, auto-connect-delay-seconds=25, has-accepted-disclaimer=true
cap $HC $D_HU
# HU-CD: Settings opened and closed while the countdown runs; one countdown, no crash
R=HU-CD; mark ${R}_start; launch
waitfor $HC ${R}_start 30 'HomeFragment: Waiting ' || echo "$R NO_COUNTDOWN" | tee -a $OUT/marks.txt
sleep 2; uidump $R-a; PID1=$(apid)
mark ${R}_settings; gest $R tap id:settings_button $R-a; sleep 3; focus > $OUT/$R-focus.txt; sleep 3
mark ${R}_back; key $R 4; uidump $R-b; sleep 2; uidump $R-c
waitfor $HC ${R}_back 40 'HomeFragment: Requesting single-USB auto-connect via AapService' || echo "$R NO_ATTEMPT" | tee -a $OUT/marks.txt
sleep 5; PID2=$(apid); mark ${R}_end; stopapp $D_HU
# HU-B5: VLC's widget, which declares a setup screen
seed $D_HU $K_HUL $(idels $D_HU) | tee $OUT/HU-B5-seed.txt  # MUST show enable-car-launcher=true, home-widgets=ABSENT, home-widget-pages=ABSENT
R=HU-B5; RES=none; mark ${R}_start; launch
waitfor $HC ${R}_start 40 'HomeFragment: onResume. isConnected='; sleep 3
# vlcrow <label> <k>: "x y" of the k-th VLC row in the picker; up to 4 keys bring one into view
vlcrow() { local L=$1 k=$2 i=0 kc xy
  uidump $L; xy=$(uiq rowof $OUT/ui/$L.xml widget_option_app VLC $k) && { echo "$xy"; return 0; }
  for kc in KEYCODE_DPAD_DOWN KEYCODE_MOVE_END KEYCODE_PAGE_UP KEYCODE_PAGE_UP; do i=$((i+1))
    key $R $kc || return 1; uidump $L-k$i; xy=$(uiq rowof $OUT/ui/$L-k$i.xml widget_option_app VLC $k) && { echo "$xy"; return 0; }
  done; return 1; }
if [ "$(grep -ac MiniPlayerConfigureActivity $OUT/hu-vlc-configure.txt)" -ge 1 ]; then
  for n in 1 2; do
    uidump $R-row$n; gest $R tap id:widget_add_tile $R-row$n || break; sleep 2
    xy=$(vlcrow $R-pick$n $n) || { echo "$R NO_VLC_ROW$n" | tee -a $OUT/marks.txt; key $R 4; break; }
    mark ${R}_pick$n; gestxy $R tap $xy vlc-row-$n || break; sleep 4; f=$(focus); echo "$f" > $OUT/$R-focus$n.txt
    if echo "$f" | grep -q AllowBindAppWidget; then uidump $R-bind$n; gest $R tap id:button1 $R-bind$n; sleep 4; f=$(focus); echo "$f" >> $OUT/$R-focus$n.txt; fi
    if echo "$f" | grep -q 'org.videolan.vlc'; then RES=CONFIGURE$n; uidump $R-cfg$n; mark ${R}_cfgback; key $R 4; sleep 3; break
    elif echo "$f" | grep -q "$PKG/"; then RES=PLACED$n; fi
  done
else RES=NO_VLC_CONFIGURE; fi
echo "$R result=$RES" | tee -a $OUT/marks.txt > $OUT/$R-result.txt
mark ${R}_measure; uidump $R-after; adb -s $H shell dumpsys appwidget > $OUT/$R-appwidget.txt; pull_settings $H > $OUT/$R-settings.xml
REL=$(seg $HC ${R}_start ${R}_measure | grep -aF 'was not set up, releasing its id' | grep -aoE 'widget [0-9]+' | awk '{print $2}')
python3 -I $S/b5check.py $OUT/$R-appwidget.txt $OUT/$R-settings.xml $REL | tee $OUT/$R-check.txt
mark ${R}_end
# HU-W12w: one widget on the row for the D-pad, only if HU-B5 left none
R=HU-W12w
if [ "$(uiq count $OUT/ui/HU-B5-after.xml id:home_widget_frame)" -eq 0 ]; then
  mark ${R}_start; uidump $R-row; gest $R tap id:widget_add_tile $R-row; sleep 2; uidump $R-pick
  gest $R tap idnth:widget_option_label:1 $R-pick; sleep 4; f=$(focus)
  if echo "$f" | grep -q AllowBindAppWidget; then uidump $R-bind; gest $R tap id:button1 $R-bind; sleep 4; fi
  echo "$(focus)" > $OUT/$R-focus.txt
  echo "$f" | grep -q "$PKG/" || { key $R 4; sleep 2; echo "$R NOT_PLACED" | tee -a $OUT/marks.txt; }
  mark ${R}_end
fi
uidump HU-W12-k0; adb -s $H shell dumpsys appwidget > $OUT/HU-W12-appwidget.txt
# HU-W12a: D-pad to the card, a long key press, the page menu
R=HU-W12a; mark ${R}_start; A=$(walkto $R id:home_primary_card $R); echo "$R walk=$A" | tee -a $OUT/marks.txt
if [ "$A" = HERE ]; then mark ${R}_lp; lpkey $R; uidump $R-lp; mark ${R}_lpend; key $R 4; uidump $R-back; fi
mark ${R}_end
# HU-W12b: D-pad to the widget frame, a long key press, Remove
R=HU-W12b; mark ${R}_start; A=$(walkto $R idnth:home_widget_frame:1 $R); echo "$R walk=$A" | tee -a $OUT/marks.txt
if [ "$A" = HERE ]; then mark ${R}_lp; lpkey $R; uidump $R-lp; mark ${R}_lpend; key $R 4; uidump $R-back; fi
mark ${R}_end
# HU-ROW: a finger long press in the free part of the widget row
R=HU-ROW; mark ${R}_start; uidump $R-a
if xy=$(uiq emptyright $OUT/ui/$R-a.xml home_widgets_row home_widget_frame widget_add_tile); then
  gestxy $R long $xy row-free-space; uidump $R-menu; key $R 4
else echo "$R NO_SPACE" | tee -a $OUT/marks.txt; fi
mark ${R}_end
stopapp $D_HU; uncap $HC; th_end
R=HU-CD; { echo "pid1=$PID1 pid2=$PID2 focus=[$(cat $OUT/$R-focus.txt)]"
  echo "fatal=$(fatal $HC ${R}_start ${R}_end) fatal_lines=$(cnt $HC ${R}_start ${R}_end 'FATAL EXCEPTION')"
  echo "digits a=[$(uiq text $OUT/ui/$R-a.xml id:home_countdown)] b=[$(uiq text $OUT/ui/$R-b.xml id:home_countdown)] c=[$(uiq text $OUT/ui/$R-c.xml id:home_countdown)]"
  echo "waiting=$(cnt $HC ${R}_start ${R}_end 'HomeFragment: Waiting ') usb_req=$(cnt $HC ${R}_start ${R}_end 'HomeFragment: Requesting single-USB auto-connect via AapService')"
  echo "res_after_back=$(cnt $HC ${R}_back ${R}_end 'HomeFragment: onResume. isConnected=')"
} | tee $OUT/$R-counts.txt
R=HU-B5; { cat $OUT/$R-result.txt $OUT/$R-focus*.txt 2>/dev/null; cat $OUT/$R-check.txt
  echo "released=$(cnt2 $HC ${R}_start ${R}_end ', releasing its id' 'was not set up') via_host=$(cnt $HC ${R}_start ${R}_end 'set up through the host: ') no_wait=$(cnt $HC ${R}_start ${R}_end 'a widget answer came back with no widget waiting')"
  echo "frames_after=$(uiq count $OUT/ui/$R-after.xml id:home_widget_frame) keys=$(cat $OUT/keys-$R 2>/dev/null || echo 0) taps=$(cat $OUT/taps-$R 2>/dev/null || echo 0)"
  seg $HC ${R}_start ${R}_end | grep -aF 'HomeFragment: widget '; } | tee $OUT/$R-counts.txt
for R in HU-W12a HU-W12b; do T='text:Add a page'; [ $R = HU-W12b ] && T='text:Remove widget'
  { grep -a "^$R " $OUT/marks.txt | grep -a walk=; cat $OUT/$R-walk.txt 2>/dev/null
    echo "keys=$(cat $OUT/keys-$R 2>/dev/null || echo 0) hold_ms=$(holdms $HC ${R}_lp ${R}_lpend) lpt=$HU_LPT lpargs=[$LPARGS]"
    echo "focus_before_lp=$(f=$(ls -t $OUT/ui/$R-m*.xml 2>/dev/null | head -1); [ -n "$f" ] && uiq focusid $f || echo none)"
    echo "menu=[$( [ -f $OUT/ui/$R-lp.xml ] && uiq count $OUT/ui/$R-lp.xml "$T" || echo NA)] title_pages=[$( [ -f $OUT/ui/$R-lp.xml ] && uiq count $OUT/ui/$R-lp.xml 'text:Home screen pages' || echo NA)]"
    echo "key_lines=$(seg $HC ${R}_start ${R}_end | grep -acF 'dispatchKeyEvent: keyCode=')"
    seg $HC ${R}_lp ${R}_lpend | grep -aF 'dispatchKeyEvent: keyCode='; } | tee $OUT/$R-counts.txt; done
grep -a 'cmp:ComponentInfo' $OUT/HU-W12-appwidget.txt | head -5 > $OUT/HU-W12-providers.txt
R=HU-ROW; { grep -a "^$R " $OUT/marks.txt; grep -a "^$R " $OUT/gestures.log
  echo "menu_add_page=$( [ -f $OUT/ui/$R-menu.xml ] && uiq count $OUT/ui/$R-menu.xml 'text:Add a page' || echo NA)"; } | tee $OUT/$R-counts.txt
```

**HU-CD PASS** when all hold (`HU-CD-counts.txt`):

- Reach: `focus` names `SettingsActivity` (Settings opened during the countdown); `digits a` is a number.
- `fatal` = 0, and `pid1` = `pid2` with both non-empty (the app did not crash or restart).
- `digits` a > b > c, and c is 1 or more (the home's countdown kept running after Settings closed).
- `usb_req` = 1 (exactly one countdown ran out and fired its attempt).

Record `waiting` (2 means the home that Settings builds and drops also started a countdown, which then stopped at `!isAdded`) and `res_after_back`. **What a PASS looks like if the fix did nothing:** at `174a52fd` the dropped home's countdown asked for a gone view owner on its next tick, so `fatal` would be 1 or more within a few seconds of `HU-CD_settings`, and `pid2` would differ or be empty. `NO_COUNTDOWN` makes HU-CD INCONCLUSIVE; record the seed read-back.

**HU-B5 grade** (`HU-B5-counts.txt`). The reach condition is a result `CONFIGURE1` or `CONFIGURE2`, with its focus file naming `org.videolan.vlc`.

- PASS (cancelled) when `released` = 1, `released_still_bound` = 0, `released_still_stored` = 0, `leaked=[]` and `frames_after` = 0.
- PASS (finished) when `released` = 0, `stored` holds one id, that id is in `our_ids`, `leaked=[]` and `frames_after` = 1. This is the case where VLC's setup screen answers RESULT_OK to BACK.
- FAIL when the result is `CONFIGURE*` and neither PASS case holds: a released id still bound or stored, a bound id stored nowhere (`leaked` not empty), or no release line and nothing stored.
- UNTESTABLE when the result is `NO_VLC_CONFIGURE`, `NO_VLC_ROW1`, `PLACED1` or `PLACED2` with no `CONFIGURE*` (the app placed the VLC widget with no setup screen, which is correct when the widget declares its setup optional), or when `via_host` is 1 or more (the N1 path).

Record the provider lines in `hu-vlc-providers.txt`, the keys and taps used, and every `HomeFragment: widget ` line.

**HU-W12a PASS** when all hold (`HU-W12a-counts.txt`):

- Reach: `walk=HERE` (the card took D-pad focus within 4 moves) and `hold_ms` is `lpt` or more.
- `menu` = 1 and `title_pages` = 1 (the long key press opened the page menu).
- `keys` is 6 or fewer.

FAIL when the reach holds and `menu` = 0. INCONCLUSIVE when `walk` is `INSIDE` (a button inside the card took focus; record which), `BUDGET` or a D-pad key name (not reached in 4 moves), or when `hold_ms` is below `lpt` or `NA` (the tool did not hold the key). For the eye only: say whether `HU-W12a-m<n>.png` of the focused card shows a ring that `HU-W12-k0.png` does not.

**HU-W12b PASS**: the same rules with `idnth:home_widget_frame:1` as the target and `menu` counting `Remove widget`. `INSIDE` here means a control of the widget itself took focus; it is INCONCLUSIVE for the run, and the result names the widget from `HU-W12-providers.txt`. Record the ring as above.

**HU-ROW PASS** when `menu_add_page` = 1. `NO_SPACE` makes it INCONCLUSIVE (the row had no free part 40 px wide). FAIL when the gesture landed and `menu_add_page` = 0. Before `59831d05` the row's scroll view took the press and nothing opened.

### HU-C: D-HU Native AA with D-MOTO (HU-U1, HU-B1, HU-W8)

Usage: `bash $OUT/scripts/hu_c.sh`. One capture on D-HU and one on D-MOTO. D-POCO's WiFi is held off and its Bluetooth stays off. D-MOTO's radios stay off until HU-W8, so the wake loop runs and no session forms before then.

**`hu_c.sh`**
```bash
. $(dirname $0)/lib.sh
H=$D_HU; PHONES=$D_MOTO
HC=$OUT/HU-C-hu.txt; MC=$OUT/HU-C-moto.txt
# waitwake <from> <label>: a page 1 dump right after a WAKING_PHONE step, with the progress bar on it; 3 tries
waitwake() { local k; for k in 1 2 3; do waitstep $HC $1 120 WAKING_PHONE $k || return 1
  uidump $2; [ "$(uiq count $OUT/ui/$2.xml id:home_primary_progress)" -ge 1 ] && return 0; done; return 1; }
# waitlisten <from> <label>: a dump right after a bare ARMED or WAITING_FOR_PHONE step, with the X and the countdown on screen; 3 tries
waitlisten() { local k t; for k in 1 2 3; do t=0
    until [ $(( $(bare $HC $1 '' ARMED) + $(bare $HC $1 '' WAITING_FOR_PHONE) )) -ge $k ]; do sleep 1; t=$((t+1)); [ $t -ge 50 ] && return 1; done
    uidump $2; [ "$(uiq inwin $OUT/ui/$2.xml id:auto_connect_pill_cancel)" = 1 ] && [ "$(uiq count $OUT/ui/$2.xml id:home_countdown)" -ge 1 ] && return 0
  done; return 1; }
th_gate HU-C || exit 1
role_off; stopapp $D_HU; hold_on
ph_off $D_MOTO | tee $OUT/HU-C-moto-off.txt
adb -s $D_HU shell svc bluetooth enable; sleep 3
echo "radios: hu_bt=$(btstate $D_HU) poco_bt=$(btstate $P)" | tee $OUT/HU-C-radios.txt   # hu_bt MUST be true, poco_bt false
seed $D_HU $K_HUC $(idels $D_HU) | tee $OUT/HU-C-seed.txt   # MUST show wifi-connection-mode=3, native-poke-bt-macs=A0:46:5A:97:E4:95, auto-connect-delay-seconds=60
cap $HC $D_HU; cap $MC $D_MOTO
# HU-U1 (RECORD): the pill's X on the wireless step while a USB countdown runs
R=HU-U1; mark ${R}_start; launch
waitfor $HC ${R}_start 20 'WifiLauncher: Initializing WiFi Mode: NATIVE' || echo "$R SETUP: no NATIVE init line" | tee -a $OUT/marks.txt
waitfor $HC ${R}_start 40 'HomeFragment: onResume. isConnected='; role_on
if waitlisten ${R}_start $R-x; then
  mark ${R}_x; gest $R tap id:auto_connect_pill_cancel $R-x; sleep 3; uidump $R-after
  waitany $HC ${R}_x 75 'HomeFragment: auto-connect cancelled from the home screen countdown' 'HomeFragment: Requesting single-USB auto-connect via AapService'; sleep 3
else echo "$R NO_LISTEN" | tee -a $OUT/marks.txt; fi
mark ${R}_end
# HU-B1: page 2 while the phone is woken; the pill's X must be there and stop the wake
R=HU-B1; mark ${R}_start; send ACTION_START_WIRELESS_SCAN >/dev/null
waitfor $HC ${R}_start 10 'AapService: Force-starting WIFI-Scan from UI' || echo "$R scan verb not seen" | tee -a $OUT/marks.txt
waitwake ${R}_start $R-wake || echo "$R NO_WAKE" | tee -a $OUT/marks.txt
mark ${R}_sw; gest $R next id:home_pager $R-wake; sleep 3; uidump $R-p2
mark ${R}_x; gest $R tap id:auto_connect_pill_cancel $R-p2; sleep 5
mark ${R}_hold; sleep 40; uidump $R-after; mark ${R}_end
# HU-W8: a real session, the short exit menu, "End session, stay ready"
R=HU-W8; mark ${R}_start; send ACTION_START_WIRELESS_SCAN >/dev/null
waitfor $HC ${R}_start 10 'AapService: Force-starting WIFI-Scan from UI' || echo "$R scan verb not seen" | tee -a $OUT/marks.txt
mark ${R}_phone; ph_on $D_MOTO | tee $OUT/HU-C-moto-on.txt
if waitfor $HC ${R}_phone 150 'Throughput over'; then
  sleep 6; adb -s $D_HU shell cmd role get-role-holders android.app.role.HOME | tr -d '\r' > $OUT/$R-role.txt
  mark ${R}_menu; key $R 4; uidump $R-menu
  mark ${R}_pick; gest $R tap 'text:End session, stay ready' $R-menu
  waitfor $HC ${R}_pick 10 'AapService: ACTION_END_SESSION_STAY_ARMED received'; waitfor $MC ${R}_pick 15 'PROJECTION_ENDED_BYEBYE_BY_CAR'
  mark ${R}_watch; sleep 45; mark ${R}_watchend
  [ "$(ssl $HC ${R}_watch ${R}_watchend)" -ge 1 ] && { mark ${R}_disc; send ACTION_DISCONNECT >/dev/null; sleep 5; }
else echo "$R NO_SESSION" | tee -a $OUT/marks.txt; fi
mark ${R}_end
ph_off $D_MOTO; role_off; stopapp $D_HU; hold_off
uncap $HC; uncap $MC; th_end
R=HU-U1; { echo "REACH listen_bare=$(( $(bare $HC ${R}_start ${R}_x ARMED) + $(bare $HC ${R}_start ${R}_x WAITING_FOR_PHONE) )) countdown_at_x=[$(uiq text $OUT/ui/$R-x.xml id:home_countdown)] x_inwin=$(uiq inwin $OUT/ui/$R-x.xml id:auto_connect_pill_cancel)"
  echo "x_line=$(cnt2 $HC ${R}_x ${R}_end 'pressed, stopping the bring-up' 'status pill X') hold_line=$(cnt $HC ${R}_x ${R}_end "AapService: the status pill's X stopped the wireless bring-up.")"
  echo "cd_stopped=$(cnt $HC ${R}_x ${R}_end "HomeFragment: auto-connect countdown stopped by the bring-up's cancel") cd_end_cancelled=$(cnt $HC ${R}_x ${R}_end 'HomeFragment: auto-connect cancelled from the home screen countdown') usb_req=$(cnt $HC ${R}_start ${R}_end 'HomeFragment: Requesting single-USB auto-connect via AapService')"
  echo "countdown_after_x=$(uiq count $OUT/ui/$R-after.xml id:home_countdown) pokes_after_x=$(cnt $HC ${R}_x ${R}_end 'NativeAA: Attempting active poke to device')"
} | tee $OUT/$R-counts.txt
R=HU-B1; { echo "W wake_subtitle_progress=$(uiq count $OUT/ui/$R-wake.xml id:home_primary_progress)"
  echo "REACH pokes=$(cnt $HC ${R}_start ${R}_x 'NativeAA: Attempting active poke to device') waking=$(nstep $HC ${R}_start ${R}_x WAKING_PHONE)"
  echo "P2 pill=$(uiq count $OUT/ui/$R-p2.xml id:auto_connect_pill) x_inwin=$(uiq inwin $OUT/ui/$R-p2.xml id:auto_connect_pill_cancel) card_cancel=$(uiq count $OUT/ui/$R-p2.xml id:home_primary_cancel)"
  echo "STEP bare_after_sw=$(seg $HC ${R}_sw ${R}_x | grep -aF "$STEP" | grep -avF '(not shown' | grep -acvF hidden)"; seg $HC ${R}_sw ${R}_x | grep -aF "$STEP"
  echo "CANCEL x_line=$(cnt2 $HC ${R}_x ${R}_hold 'pressed, stopping the bring-up' 'status pill X') hold_line=$(cnt $HC ${R}_x ${R}_hold "AapService: the status pill's X stopped the wireless bring-up.")"
  echo "AFTER pokes=$(cnt $HC ${R}_hold ${R}_end 'NativeAA: Attempting active poke to device') waking=$(nstep $HC ${R}_hold ${R}_end WAKING_PHONE) after_x=$(uiq count $OUT/ui/$R-after.xml id:auto_connect_pill_cancel)"
} | tee $OUT/$R-counts.txt
R=HU-W8; { echo "SESSION ssl=$(ssl $HC ${R}_phone ${R}_menu) mssr=$(cnt $HC ${R}_phone ${R}_menu 'Media Sink Setup Request: ') rend=$(rend $HC ${R}_phone ${R}_menu) role=[$(cat $OUT/$R-role.txt 2>/dev/null)]"
  for t in 'End session, stay ready' 'Stop Connection' 'Move to Background' 'Picture-in-Picture' 'Quick Settings' 'Switch Phone'; do echo "MENU [$t]=$(uiq count $OUT/ui/$R-menu.xml "text:$t")"; done
  echo "ESA activity=$(cnt $HC ${R}_pick ${R}_end 'AapProjectionActivity: User ended the session and asked to stay ready') service=$(cnt $HC ${R}_pick ${R}_end 'AapService: ACTION_END_SESSION_STAY_ARMED received')"
  echo "NET keep=$(cnt $HC ${R}_pick ${R}_end 'AapService: Native AA session ended; keeping the ') stop=$(cnt $HC ${R}_pick ${R}_end 'AapService: Native AA user exit. Stopping active launcher.') groups_after=$(cnt $HC ${R}_pick ${R}_watchend 'createGroup SUCCESS')"
  echo "RETURN ssl_in_45s=$(ssl $HC ${R}_watch ${R}_watchend) rend_in_45s=$(rend $HC ${R}_watch ${R}_watchend)"
  echo "PHONE critical=$(cnt $MC ${R}_phone ${R}_end 'Critical error') byecar_after_pick=$(cnt $MC ${R}_pick ${R}_watch 'PROJECTION_ENDED_BYEBYE_BY_CAR') throttle=$(cnt $MC HU-U1_start ${R}_end THROTTLE_LIMIT_EXCEEDED)"
  echo "RECORD match=$(cnt $HC HU-U1_start ${R}_end 'MATCH! Starting AapService') groups=$(cnt $HC HU-U1_start ${R}_end 'createGroup SUCCESS')"
  seg $HC ${R}_pick ${R}_watchend | grep -aF 'keeping the '
} | tee $OUT/$R-counts.txt
```

**HU-U1 RECORD** (`HU-U1-counts.txt`). Reach: `listen_bare` 1 or more, `countdown_at_x` is a number, `x_inwin` = 1. Report `x_line`, `hold_line`, `cd_stopped`, `cd_end_cancelled`, `usb_req`, `countdown_after_x` and `pokes_after_x`. The code at `135131ce` predicts `cd_stopped` = 1, `cd_end_cancelled` = 1 and `usb_req` = 0: the X on a WiFi step also stops the USB countdown. Say plainly which of the two happened. `NO_LISTEN` means no bare listening step with the countdown on screen came within three tries; record the step lines.

**HU-B1 PASS** when all hold (`HU-B1-counts.txt`):

- Reach: `REACH pokes` 1 or more and `waking` 1 or more.
- `P2 x_inwin` = 1 (the pill's X is on screen on page 2).
- `STEP bare_after_sw` 1 or more (the pill told the step once the card left the screen).
- `CANCEL x_line` = 1 and `hold_line` = 1.
- `AFTER pokes` = 0, `waking` = 0 and `after_x` = 0 (the X stopped the wake for 40 s).

`NO_WAKE` makes HU-B1 INCONCLUSIVE; record the last `L-STEP` line.

**HU-W8 PASS** when all hold (`HU-W8-counts.txt`):

- Reach: `ssl` 1 or more, `mssr` 1 or more, `rend` 1 or more; `role` names `com.andrerinas.headunitrevived` (the short menu applies).
- `MENU [End session, stay ready]` = 1, `[Stop Connection]` = 1, `[Move to Background]` = 1, `[Picture-in-Picture]` = 0, `[Quick Settings]` = 0.
- `ESA activity` = 1 and `service` = 1.
- `NET keep` = 1, `stop` = 0 and `groups_after` = 0 (the network stayed up; a plain Stop would stop the launcher and remove the group).
- `PHONE critical` = 0 and `byecar_after_pick` 1 or more.

Record `RETURN` (whether D-MOTO came back on the kept network within 45 s), `[Switch Phone]`, `match` (above 0 is a re-run by the template's discard rule) and `groups`.

**Stop rule.** `NO_SESSION`: run `hu_c.sh` once more. If `throttle` is above 0, reboot D-MOTO first (`adb -s $D_MOTO reboot`, wait for `sys.boot_completed` 1, then hand step 1 if it is locked). A second `NO_SESSION` makes HU-W8 UNTESTABLE (rig): record the last `NativeAA: ` lines. HU-U1 and HU-B1 grade from the first pass unless that pass was void.

### HU-W10 (optional, RECORD): the setup wizard's launcher switch on D-HU

Usage: `bash $OUT/scripts/hu_w10.sh`. One capture.

**Tap cap exception.** The wizard has no verb and no deep link to its launcher step (`STEP_LAUNCHER` = 8), so the run taps the one `Next` button (`onb_next`) eight times and the switch once: 9 taps. Each tap resolves `onb_next` in a fresh dump and asserts one match. The operator decides at the brief gate. If the operator says no, skip HU-W10 and write NOT RUN.

**`hu_w10.sh`**
```bash
. $(dirname $0)/lib.sh
H=$D_HU; PHONES=""; TAPCAP=9
HC=$OUT/HU-W10-hu.txt; R=HU-W10
th_gate $R || exit 1
role_off; stopapp $D_HU
seed $D_HU $K_W10 $(idels $D_HU) | tee $OUT/$R-seed.txt      # MUST show onboarding-version=1, enable-car-launcher=false
cap $HC $D_HU
mark ${R}_start; launch
for i in $(seq 1 15); do f=$(focus); echo "$f" | grep -q OnboardingActivity && break; sleep 1; done; echo "$f" > $OUT/$R-focus0.txt
for i in 1 2 3 4 5 6 7 8; do uidump $R-s$i; gest $R tap id:onb_next $R-s$i || { echo "$R NEXT_FAILED at $i" | tee -a $OUT/marks.txt; break; }; done
uidump $R-launcher
if [ "$(uiq count $OUT/ui/$R-launcher.xml id:onb_launcher_switch)" -eq 1 ]; then
  echo "switch_before=$(uiq checked $OUT/ui/$R-launcher.xml id:onb_launcher_switch)" > $OUT/$R-switch.txt
  mark ${R}_flip; gest $R tap id:onb_launcher_switch $R-launcher; sleep 3
  f=$(focus); echo "$f" > $OUT/$R-focus-flip.txt; uidump $R-flip
  if ! echo "$f" | grep -q OnboardingActivity; then
    echo "HAND STEP 2: a system screen is up on D-HU. Pick Open Headunit as the home app and confirm. Waiting 120 s."
    for i in $(seq 1 120); do f=$(focus); echo "$f" | grep -q OnboardingActivity && break; sleep 1; done; echo "$f" >> $OUT/$R-focus-flip.txt
  fi
  sleep 3; uidump $R-after; echo "switch_after=$(uiq checked $OUT/ui/$R-after.xml id:onb_launcher_switch)" >> $OUT/$R-switch.txt
else echo "$R NO_LAUNCHER_STEP" | tee -a $OUT/marks.txt; fi
echo "role=$(adb -s $H shell cmd role get-role-holders android.app.role.HOME | tr -d '\r')" > $OUT/$R-role.txt
mark ${R}_end
role_off; stopapp $D_HU; uncap $HC; th_end
{ cat $OUT/$R-focus-flip.txt $OUT/$R-switch.txt $OUT/$R-role.txt 2>/dev/null
  echo "granted=$(cnt $HC ${R}_flip ${R}_end 'OnboardingActivity: home role granted, car launcher mode stored') reverted=$(cnt $HC ${R}_flip ${R}_end 'OnboardingActivity: home role not held after the request, turning the launcher switch back off') role_fail=$(cnt $HC ${R}_flip ${R}_end 'CarLauncherManager: RoleManager failed: ')"
  seg $HC ${R}_flip ${R}_end | grep -aF 'CarLauncherManager: set launcher alias enabled='
  echo "taps=$(cat $OUT/taps-$R 2>/dev/null || echo 0) fatal=$(fatal $HC ${R}_start ${R}_end)"; } | tee $OUT/$R-counts.txt
```

**HU-W10 RECORD** (`HU-W10-counts.txt`). Reach: `switch_before=false` and a `flip` mark. Report whether a system screen came up after the flip (the `focus-flip` lines), whether the operator picked the app, `switch_after`, the role holder, `granted`, `reverted`, `role_fail` and the alias lines. Review N2 predicts no system screen, `reverted` = 1 and `switch_after=false` on API 34. That is tracked in upstream #1160 and is not a FAIL of this PR. `NEXT_FAILED` or `NO_LAUNCHER_STEP` make HU-W10 INCONCLUSIVE; record the dump of the step it stopped on.

### HP: D-HP, the countdown ring below API 21 (HP-RING, RECORD)

Usage: `bash $OUT/scripts/hp.sh`. One capture with its watcher. API 17 dumps carry no resource ids, so the run finds the countdown by its number.

**`hp.sh`**
```bash
. $(dirname $0)/lib.sh
H=$D_HP; PHONES=""
HC=$OUT/HP-hu.txt; R=HP-RING
th_gate $R || exit 1
stopapp $D_HP
seed $D_HP $K_HP $(idels $D_HP) | tee $OUT/$R-seed.txt       # MUST show auto-connect-delay-seconds=20, auto-connect-single-usb=true
cap $HC $D_HP; capwatch $HC $D_HP
mark ${R}_start; launch
waitfor $HC ${R}_start 40 'HomeFragment: Waiting ' || echo "$R NO_COUNTDOWN" | tee -a $OUT/marks.txt
sleep 3; uidump $R-a; sleep 3; uidump $R-b
mark ${R}_end; stopapp $D_HP; uncap $HC; th_end
echo "waiting=$(cnt $HC ${R}_start ${R}_end 'HomeFragment: Waiting ') number_nodes_a=$(uiq count $OUT/ui/$R-a.xml 'textre:[0-9]{1,2}') number_nodes_b=$(uiq count $OUT/ui/$R-b.xml 'textre:[0-9]{1,2}') capture_restarts=$(grep -ac 'capture restarted' $OUT/marks.txt)" | tee $OUT/$R-counts.txt
```

**HP-RING RECORD.** Reach: `waiting` = 1 and `number_nodes_a` 1 or more. For the eye only: say whether the ring in `HP-RING-a.png` has a clear centre with the number readable, or sits on a black disc (the look before `135131ce`).

### POCO: D-POCO as the head unit, the speed switch (POCO-U3a, POCO-U3b, RECORD)

Usage: `bash $OUT/scripts/poco.sh`. One capture. No connection. Each arm revokes both location grants first; a revoke stops the app, so each arm opens Customization again.

**`poco.sh`**
```bash
. $(dirname $0)/lib.sh
H=$D_POCO; PHONES=""
HC=$OUT/POCO-hu.txt
th_gate POCO || exit 1
[ -n "$CUST" ] || { echo "POCO-U3 UNTESTABLE: no customizationFragment id"; th_end; exit 3; }
stopapp $P
seed $P $K_POCO $(idels $P) | tee $OUT/POCO-seed.txt         # MUST show show-home-speed=false
cap $HC $P
PC=com.android.permissioncontroller
for A in a b; do R=POCO-U3$A
  adb -s $P shell pm revoke $PKG android.permission.ACCESS_FINE_LOCATION; adb -s $P shell pm revoke $PKG android.permission.ACCESS_COARSE_LOCATION
  for p in FINE COARSE; do adb -s $P shell pm clear-permission-flags $PKG android.permission.ACCESS_${p}_LOCATION user-set user-fixed 2>&1 | tail -1; done
  perms > $OUT/$R-perms-before.txt
  mark ${R}_start; adb -s $P shell run-as $PKG am start -n $SETACT --ei extra_destination $CUST 2>&1 | tr -d '\r' > $OUT/$R-am.txt
  sleep 4; f=$(focus); echo "$f" > $OUT/$R-focus0.txt
  if ! echo "$f" | grep -q SettingsActivity; then echo "$R NO_ROUTE" | tee -a $OUT/marks.txt; mark ${R}_end; continue; fi
  uidump $R-c0; L=$R-c0; i=0
  for kc in KEYCODE_DPAD_DOWN KEYCODE_MOVE_END KEYCODE_PAGE_UP KEYCODE_PAGE_UP; do
    [ "$(uiq inwin $OUT/ui/$L.xml id:switch_show_speed)" = 1 ] && break
    i=$((i+1)); key $R $kc; L=$R-c$i; uidump $L; done
  echo "switch_before=$(uiq checked $OUT/ui/$L.xml id:switch_show_speed)" > $OUT/$R-switch.txt
  mark ${R}_flip; gest $R tap id:switch_show_speed $L; sleep 3
  f=$(focus); echo "$f" > $OUT/$R-focus-flip.txt; uidump $R-dlg
  if echo "$f" | grep -q $PC; then
    for d in permission_location_accuracy_radio_fine permission_location_accuracy_radio_coarse permission_allow_foreground_only_button permission_allow_one_time_button permission_deny_button; do
      echo "$d=$(uiq count $OUT/ui/$R-dlg.xml id:$d)"; done > $OUT/$R-dialog.txt
    RADIO=permission_location_accuracy_radio_fine; [ $A = b ] && RADIO=permission_location_accuracy_radio_coarse
    [ "$(uiq count $OUT/ui/$R-dlg.xml id:$RADIO)" = 1 ] && { gest $R tap id:$RADIO $R-dlg; uidump $R-dlg2; } || cp $OUT/ui/$R-dlg.xml $OUT/ui/$R-dlg2.xml
    gest $R tap id:permission_allow_foreground_only_button $R-dlg2; sleep 3
  else echo "no permission dialog" > $OUT/$R-dialog.txt; fi
  uidump $R-after; echo "switch_after=$(uiq checked $OUT/ui/$R-after.xml id:switch_show_speed)" >> $OUT/$R-switch.txt
  perms > $OUT/$R-perms-after.txt; pull_settings $P > $OUT/$R-settings.xml
  mark ${R}_end; stopapp $P
done
uncap $HC; th_end
for A in a b; do R=POCO-U3$A
  { cat $OUT/$R-am.txt $OUT/$R-focus0.txt $OUT/$R-focus-flip.txt $OUT/$R-dialog.txt $OUT/$R-switch.txt 2>/dev/null
    echo "before: $(tr '\n' ' ' < $OUT/$R-perms-before.txt)"; echo "after: $(tr '\n' ' ' < $OUT/$R-perms-after.txt 2>/dev/null)"
    [ -f $OUT/$R-settings.xml ] && python3 -I $S/showkeys.py $OUT/$R-settings.xml show-home-speed
    echo "blocked=$(cnt $HC ${R}_flip ${R}_end 'Location permission blocked for the home speed, offering the app settings') taps=$(cat $OUT/taps-$R 2>/dev/null || echo 0) keys=$(cat $OUT/keys-$R 2>/dev/null || echo 0)"
  } | tee $OUT/$R-counts.txt; done
```

**POCO-U3a and POCO-U3b RECORD.** Reach: `focus0` names `SettingsActivity` and `switch_before=false`. Report, per arm: whether the system location dialog came up (`dialog` file), which choices it offered, what the run tapped, the grants after, `show-home-speed`, `switch_after` and `blocked`. Read the outcome of `SpeedPermissionPolicy` this way: `show-home-speed=true` is SHOW_SPEED; `blocked` = 1 is OPEN_APP_SETTINGS; neither is EXPLAIN (a toast only, with no log line). Review U3 predicts no dialog at all on Android 12 and later, because the switch asks for `ACCESS_FINE_LOCATION` alone, and then `blocked` = 1 in both arms. `NO_ROUTE` makes that arm UNTESTABLE (no scripted way into Customization); record `am.txt`.

## 9. Restore

**`restore.sh`**
```bash
. $(dirname $0)/lib.sh
exec > >(tee $OUT/restore.txt) 2>&1
hold_off
adb -s $D_HU shell cmd role add-role-holder android.app.role.HOME "$(cat $OUT/home-role-$D_HU.txt)"
echo "hu home role $(adb -s $D_HU shell cmd role get-role-holders android.app.role.HOME | tr -d '\r')"
for s in $D_HU $D_POCO $D_HP; do
  echo "== $s"; stopapp $s
  adb -s $s install -r -d $OUT/before-$s.apk | tail -1
  $RIG_TOOLKIT/apk_identity.sh $OUT/before-$s.apk $s; echo "apk_identity $s exit $?"
  echo "$s $(push_settings $s $OUT/backup-$s.xml)"
done
if [ "$HU_ALIAS" -ge 1 ]; then adb -s $D_HU shell pm enable $ALIAS; else adb -s $D_HU shell pm disable $ALIAS; fi
echo "hu alias $(adb -s $D_HU shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME | grep -ac CarLauncherAlias) (opening $HU_ALIAS)"
[ "$POCO_FINE" = true ] && adb -s $P shell pm grant $PKG android.permission.ACCESS_FINE_LOCATION
[ "$POCO_COARSE" = true ] && adb -s $P shell pm grant $PKG android.permission.ACCESS_COARSE_LOCATION
echo "poco grants $(perms | tr '\n' ' ') (opening fine=$POCO_FINE coarse=$POCO_COARSE)"
adb -s $D_HU shell settings put global animator_duration_scale $HU_ANIM
adb -s $P shell settings put global animator_duration_scale $POCO_ANIM
adb -s $D_HP shell settings put global animator_duration_scale $HP_ANIM
if [ "$HU_BT" = true ]; then adb -s $D_HU shell svc bluetooth enable; else adb -s $D_HU shell svc bluetooth disable; fi
adb -s $P shell svc wifi enable; sleep 5; adb -s $P shell dumpsys wifi | grep -a -m1 'Wi-Fi is'
pbt() { if [ "$POCO_BT" = true ]; then adb -s $P shell svc bluetooth enable; else adb -s $P shell svc bluetooth disable; fi; sleep 3; }
pbt; [ "$(btstate $P)" = "$POCO_BT" ] || pbt; echo "poco bt $(btstate $P) (opening $POCO_BT)"
[ "$MOTO_WIFI" = enabled ] && adb -s $D_MOTO shell svc wifi enable
[ "$MOTO_BT" = true ] && adb -s $D_MOTO shell svc bluetooth enable
$RIG_TOOLKIT/rig_cleanup.sh list
$RIG_TOOLKIT/rig_preflight.sh D_HU:wifi D_HP:wifi D_POCO:wifi
```

Each unit SHOULD print `apk_identity ... exit 0` and `SETTINGS_BACK`. The home role SHOULD read the file pre-flight wrote, the alias count SHOULD match `HU_ALIAS`, and D-POCO's grants SHOULD match the opening ones. On `SETTINGS_DIFFER`, put the diff in Setup notes and leave the file; do not loop. If a `before-*.apk` refuses to install, leave C installed and say so. Leave D-POCO's head unit server as it is.

## 10. Do not re-run

- Everything round 1 and round 2 graded PASS: the layout and info runs, Customization, pages add and remove, Move to Background, Self Mode, SAM-C, B2 on D-HU and D-POCO, B4, B6 on D-HU and D-POCO, W16, HP-F1 and SAM-HS.
- W7 (Move to Background to this app's home finishes the projection). It is unchanged and older than the PR.
- Widget setup through a non-exported activity (review N1); the code is the evidence.
- `hus-version-retry` round 1's delay A/B. Do not add a delay on any link.

## 11. Report back

One verdict per run in a table at the top of the results file: HU-CD, HU-B5, HU-W12a, HU-W12b, HU-ROW, HU-B1, HU-W8 (GATE), then HU-U1, HU-W10, HP-RING, POCO-U3a, POCO-U3b (RECORD). Then:

1. **Widget setup (B5) and D-pad (W12):** HU-B5's result line, `released`, the `b5check` line and `frames_after`; for HU-W12a and HU-W12b the `walk` answer, `hold_ms` against `lpt`, `menu`, and what the eye saw of the ring.
2. **Cancel and the countdown:** HU-B1's `x_inwin`, `x_line`, `hold_line` and `AFTER` counts; HU-CD's `fatal`, the three digits and `usb_req`; HU-U1's `cd_stopped`, `cd_end_cancelled` and `usb_req`.
3. **The rest:** HU-W8's menu counts and `NET` line; HU-ROW's `menu_add_page`; HU-W10's flip result; HP-RING's look; POCO-U3's dialog and `blocked` per arm.

```decisive-strings
AutomationReceiver: 
AutomationMarker: 
HomeFragment: onResume. isConnected=
HomeFragment: Waiting 
s before attempting auto-connect...
HomeFragment: Requesting single-USB auto-connect via AapService
HomeFragment: auto-connect countdown stopped by the bring-up's cancel
HomeFragment: auto-connect cancelled from the home screen countdown
dispatchKeyEvent: keyCode=
MainActivity: status pill step: 
(not shown, the home's main button tells it)
pressed, stopping the bring-up
status pill X
AapService: the status pill's X stopped the wireless bring-up.
NativeAA: Attempting active poke to device
WifiLauncher: Initializing WiFi Mode: 
AapService: Force-starting WIFI-Scan from UI
SSL handshake complete
Handshake: SSL handshake complete
Throughput over
rendered=
Media Sink Setup Request: 
Disconnect action received.
AapProjectionActivity: User ended the session and asked to stay ready
AapService: ACTION_END_SESSION_STAY_ARMED received
AapService: Native AA session ended; keeping the 
AapService: Native AA user exit. Stopping active launcher.
createGroup SUCCESS
MATCH! Starting AapService
, releasing its id
was not set up
set up through the host: 
a widget answer came back with no widget waiting
HomeFragment: widget 
OnboardingActivity: home role granted, car launcher mode stored
OnboardingActivity: home role not held after the request, turning the launcher switch back off
CarLauncherManager: set launcher alias enabled=
CarLauncherManager: RoleManager failed: 
Location permission blocked for the home speed, offering the app settings
WAKING_PHONE
WAITING_FOR_PHONE
ARMED
```

Head unit lines the runs grep in their run-time form. Each is built from a template in the block above or in section 7, so it does not appear in `app/src` as written:

```decisive-strings-runtime
MainActivity: status pill X pressed, stopping the bring-up
HomeFragment: Waiting 25s before attempting auto-connect...
dispatchKeyEvent: keyCode=23, action=0
dispatchKeyEvent: keyCode=23, action=1
HomeFragment: widget <id> was not set up, releasing its id
AapService: Native AA session ended; keeping the WIFI_DIRECT network up for the phone's return.
WifiLauncher: Initializing WiFi Mode: NATIVE
```

Screen texts and view ids the dumps match, in `app/src/main/res` at `135131ce`:

```decisive-strings-ui
Add a page
Home screen pages
Remove widget
End session, stay ready
Stop Connection
Move to Background
Picture-in-Picture
Quick Settings
Switch Phone
Waking your phone
Stop connecting
home_primary_card
home_primary_cancel
home_primary_progress
home_countdown
home_pager
settings_button
auto_connect_pill
auto_connect_pill_cancel
widget_add_tile
widget_option_label
widget_option_app
home_widget_frame
home_widgets_row
onb_next
onb_launcher_switch
switch_show_speed
row_show_speed
```

Phone-side strings, checked in the decompiled Gearhead 18.0.664204 sources for round 2 rather than in `app/src` (`RIGMARK` is our own `log -t` tag):

```decisive-strings-phone
PROJECTION_ENDED_BYEBYE_BY_CAR
Critical error
THROTTLE_LIMIT_EXCEEDED
RIGMARK
```

Android's own strings and ids, not ours:

```decisive-strings-system
FATAL EXCEPTION
Process: com.andrerinas.headunitrevived
mCurrentFocus
AllowBindAppWidget
SettingsActivity
OnboardingActivity
org.videolan.vlc
com.android.permissioncontroller
permission_location_accuracy_radio_fine
permission_location_accuracy_radio_coarse
permission_allow_foreground_only_button
permission_allow_one_time_button
permission_deny_button
MiniPlayerConfigureActivity
```
