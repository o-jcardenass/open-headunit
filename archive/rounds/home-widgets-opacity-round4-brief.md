# home-widgets-opacity, round 4 brief

Published name: `home-widgets-opacity-round4-brief.md`. Results go in `home-widgets-opacity-round4-results.md`.
Evidence: upload `home-widgets-opacity-round4-captures.zip` to the existing release `rig-evidence-home-widgets-opacity`.

Units: **D-HU only**, idle with no phone. No Android Auto session and no radio change, so no phone capture is needed.

Queue line: `home-widgets-opacity-round4-brief.md` (C `4f04c168` on `fork/feat/home-widgets-and-opacity`, four commits on `main` `947b3ae9`; one build, no baseline; unit D-HU, idle; about 30 min; L1 is the point).

Pairing: D-HU only, idle, no radio change. Any round that does not use D-HU can run beside this one.

**Hand steps.** None.

**Injected taps.** At most 3 per run, each on a node resolved from a `uiautomator dump` with exactly one match: the row's switch (`:id/settingSwitch`), the settings screen's Save (`:id/settingsSave`), and in the system's home-role dialog the row `Open Headunit` and the button `Set as default`. A target that does not resolve voids the run: never guess a coordinate.

**Time budget.** Build 20 min, pre-flight beside it. Install 2 min. Runs 4 min. Thermal wait about 1 min. Restore 2 min. **About 30 min.**

## 1. Build and baseline

One APK. No baseline. **History was not rewritten**: `4f04c168` is round 3's `a2a3b0d7` plus one commit.

| Arm | Ref | Commit | Tree |
|---|---|---|---|
| C (candidate) | `fork/feat/home-widgets-and-opacity` | `4f04c16873ced36676b1ba534d25760605459bf2` | `da76401ab5ceacbe6fc46a66c552cb7224e83297` |

The dev host ran the JVM suite at `4f04c168`: 4255 tests, 0 failures. Do not run the unit tests on the rig.

Work from `~/Coding/StudioProjects`. Save every script block under `$OUT/scripts/` with `python3 -I $RIG_TOOLKIT/extract_brief_blocks.py <this brief> $OUT/scripts`. Run each with `bash $OUT/scripts/<name>.sh`.

**`build.sh`**
```bash
. $(dirname $0)/lib.sh
C=4f04c16873ced36676b1ba534d25760605459bf2; T=da76401ab5ceacbe6fc46a66c552cb7224e83297
WT=${WT:-$($RIG_TOOLKIT/wt-new.sh home-widgets-opacity-r4 candidate main | tail -1)}
git -C $WT fetch https://github.com/o-jcardenass/open-headunit.git feat/home-widgets-and-opacity
git -C $WT checkout --detach $C
[ "$(git -C $WT rev-parse 'HEAD^{tree}')" = $T ] || { echo TREE_MISMATCH; exit 1; }
$RIG_TOOLKIT/rig_thermal.sh wait 70
HUR_DIR=$WT MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh || { echo BUILD_FAILED; exit 1; }
cp $(ls -t $RIG_DATA/apks/com.andrerinas.headunitrevived_*.apk | head -1) $OUT/C.apk
{ echo "WT=$WT"; echo "C md5 $(md5sum < $OUT/C.apk | cut -c1-32)"
  echo "mainStore=$(unzip -p $OUT/C.apk 'classes*.dex' | strings | grep -cF 'launcher mode not stored yet, storing car launcher mode')"
  echo "oldKeep=$(unzip -p $OUT/C.apk 'classes*.dex' | strings | grep -cF 'launcher mode not stored yet, keeping the launcher alias')"; } | tee $OUT/build.txt
```

Build gate: `mainStore` MUST be 1 or more and `oldKeep` MUST be 0. Round 3's APK reads the other way round. A failed gate stops the round. If `git fetch` does not find `4f04c16873ce`, stop and ask.

## 2. What this is and why

Round 3 (`home-widgets-opacity-round3-results.md`) showed the home-role dialog works and a grant makes the app the role holder, but the grant stored nothing (L1 FAIL). When the role is granted, Android starts the Home activity at once, which is this app's alias to `MainActivity`. That clears the settings screen that asked, so its result callback never runs. `MainActivity` saw the state and only logged `launcher mode not stored yet, keeping the launcher alias`.

`4f04c168` makes `MainActivity` store `enable-car-launcher=true` in that same branch. The grant always lands on that screen, so the settings row and the wizard are both covered.

| Id | What | Run | Kind |
|---|---|---|---|
| L1 | The grant ends with `enable-car-launcher=true` | L1 | GATE, the point |
| L3 | Off and Save, then a fresh settings screen, stays off | L3 | GATE |

L3 was void in round 3 (the brief named the wrong Save id). Round 3 did show that turning the switch off drops the role at once, so L3 is expected to PASS. It grades the save-then-reopen order, which round 3 did not.

## 3. What is different about this round

- Same as round 3: `stopapp` puts the system Settings in front before a force-stop, because the app as home is started again otherwise; `running-after-stop` MUST read 0.
- L3 reopens the settings screen after a `stopapp`, so the screen is a new one. Round 3's reopen found the old screen and its unfiltered list, so the dump showed no switch.
- In L1, `home role granted after` may read 0 or 1. The settings screen is cleared by the grant, so its result often never arrives. L1 grades `MainActivity`'s line and the stored key instead.

## 4. Settings keys

As round 3: `seed` writes `enable-car-launcher=false` plus the run's fixed keys (`onboarding-version` 2, `has-accepted-disclaimer` true, `rename_notice_shown_v2` true, `aa174_notice_shown` true, `log-level` 2, `wifi-connection-mode` 0, `auto-connect-last-session` false, `auto-start-self-mode` false, `auto-connect-single-usb` false).

## 5. Helpers

**`lib.sh`**
```bash
source ~/Coding/StudioProjects/rig-toolkit/rig_devices.sh
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
SET=$PKG/com.andrerinas.openheadunit.main.SettingsActivity
MAIN=$PKG/com.andrerinas.openheadunit.main.MainActivity
ROLE=android.app.role.HOME
D_HU=${D_HU:-27870808938846}; D_SAM=${D_SAM:-30041c35642d2200}
OUT=$RIG_DATA/rounds/home-widgets-opacity-round4; S=$OUT/scripts; mkdir -p $S $OUT/ui
export LC_ALL=C
[ -f $OUT/env.sh ] && . $OUT/env.sh       # HU_OWNER, HU_HOME
unit() { case $1 in hu) H=$D_HU; U=hu;; sam) H=$D_SAM; U=sam;; esac; }
SEED="scalar:int:onboarding-version:2 scalar:boolean:has-accepted-disclaimer:true scalar:boolean:rename_notice_shown_v2:true
  scalar:boolean:aa174_notice_shown:true scalar:int:log-level:2 scalar:int:wifi-connection-mode:0
  scalar:boolean:auto-connect-last-session:false scalar:boolean:auto-start-self-mode:false scalar:boolean:auto-connect-single-usb:false"

send() { local a=$1; shift; adb -s $H shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; sleep 0.3; }
mark() { send ACTION_LOG_MARKER --es text "$1" >/dev/null; echo "$1 $(date +%T)" >> $OUT/marks.txt; }
running() { { adb -s $H shell ps; adb -s $H shell ps -A; } 2>/dev/null | grep -ac "$PKG"; }
# with the app as home, a force-stop makes Android relaunch it as the home; the system Settings in front prevents that
stopapp() { if [ "$(running)" -ge 1 ]; then adb -s $H shell am start -a android.intent.action.VIEW -d headunit://exit >/dev/null; sleep 5; fi
  adb -s $H shell am start -a android.settings.SETTINGS >/dev/null; sleep 2
  adb -s $H shell am force-stop $PKG; sleep 1; echo "running-after-stop $(running)" >> $OUT/marks.txt; }
# opensettings: the settings screen filtered to the launcher rows
opensettings() { if [ $U = hu ]; then adb -s $H shell am start -n $SET --es extra_search_query "Car Launcher" >/dev/null
  else adb -s $H shell run-as $PKG am start --user 0 -n $SET --es extra_search_query "Car Launcher" >/dev/null; fi; sleep 3; }
holder() { if [ $U = hu ]; then adb -s $H shell cmd role get-role-holders $ROLE | tr -d '\r'; else echo n/a; fi; }
resumed() { adb -s $H shell dumpsys activity activities | grep -aE 'mResumedActivity|ResumedActivity:' | head -1 | tr -d '\r'; }

cap() { adb -s $H logcat -G 16M >/dev/null 2>&1; adb -s $H logcat -c; stdbuf -oL adb -s $H logcat -v time > $1 & echo $! > $1.pid; sleep 1; }
uncap() { kill $(cat $1.pid) 2>/dev/null; }
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
launcherkey() { pull_settings | grep -aoE '<boolean name="enable-car-launcher"[^>]*/>' || echo "enable-car-launcher ABSENT"; }
seed() { adb -s $H shell am force-stop $PKG; sleep 1
  if [ $U = hu ]; then python3 -I $RIG_TOOLKIT/set_hu_settings_host.py $H $SEED "$@" >> $OUT/seed.log 2>&1
  else python3 -I $RIG_TOOLKIT/set_prefs_runas_host.py $H $SEED "$@" >> $OUT/seed.log 2>&1; fi
  launcherkey; }
inst() { adb -s $H install -r -d $OUT/C.apk | tail -1
  $RIG_TOOLKIT/apk_identity.sh $OUT/C.apk $H; echo "apk_identity exit $?"
  send ACTION_QUERY_STATE | grep -ao 'commit[^,}]*'; adb -s $H shell am force-stop $PKG; }
uidump() { adb -s $H shell rm -f /sdcard/ohu-ui.xml; sleep 0.3
  adb -s $H shell uiautomator dump /sdcard/ohu-ui.xml >/dev/null 2>&1; sleep 0.3
  adb -s $H pull /sdcard/ohu-ui.xml $OUT/ui/$1.xml >/dev/null 2>&1; sleep 0.3
  adb -s $H shell screencap -p /sdcard/ohu-ui.png; sleep 0.3; adb -s $H pull /sdcard/ohu-ui.png $OUT/ui/$1.png >/dev/null 2>&1
  [ -s $OUT/ui/$1.xml ] || echo "$1 DUMP_FAILED" | tee -a $OUT/marks.txt >&2; }
# tap <dumpname> id|text <value>: one injected tap on the only matching node; TAPS counts them per run
tap() { local xy; uidump $1; xy=$(python3 -I $S/uiq.py center $OUT/ui/$1.xml $2 "$3") || {
    echo "$R tap $2=$3 NO_UNIQUE_NODE ($xy)" | tee -a $OUT/$R-counts.txt; return 1; }
  TAPS=$((TAPS+1)); [ $TAPS -le 3 ] || { echo "$R TAP_BUDGET"; return 1; }
  adb -s $H shell input tap $xy; sleep 0.3; echo "$R tap $TAPS $2=$3 at $xy $(date +%T)" | tee -a $OUT/$R-counts.txt; }
checked() { uidump $1; python3 -I $S/uiq.py checked $OUT/ui/$1.xml settingSwitch; }
begin() { R=$1; TAPS=0; echo "$R holder-before $(holder)" | tee $OUT/$R-counts.txt; mark "$R-start"; }
fin() { mark "$R-end"; echo "fatal=$(fatal $CAPF $R-start $R-end)" | tee -a $OUT/$R-counts.txt
  echo "$R holder-after $(holder)" | tee -a $OUT/$R-counts.txt; stopapp
  echo "$R key-after $(launcherkey)" | tee -a $OUT/$R-counts.txt; }
# n <string>: count in the current run's window
n() { cnt $CAPF $R-start $R-end "$1"; }
```

**`uiq.py`**
```python
# center <xml> id <rid> | text <text>   the centre of the only on-screen node that matches; exit 2 when not exactly one
# checked <xml> <rid>                   the checked= value of each on-screen node with that id, space-separated
import re, sys

def nodes(xml):
    s = open(xml, encoding="utf-8", errors="replace").read()
    for t in re.findall(r'<node [^>]*>', s):
        a = dict(re.findall(r'([a-z-]+)="([^"]*)"', t))
        b = re.match(r'\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]', a.get("bounds", ""))
        if b:
            l, t_, r, bo = map(int, b.groups())
            if r > l and bo > t_: yield a, (l, t_, r, bo)

def match(a, kind, v):
    if kind == "id": return a.get("resource-id", "").endswith(":id/" + v)
    return a.get("text", "") == v

cmd = sys.argv[1]
if cmd == "center":
    m = [bx for a, bx in nodes(sys.argv[2]) if match(a, sys.argv[3], sys.argv[4])]
    if len(m) != 1: print("matches=%d" % len(m)); sys.exit(2)
    l, t, r, b = m[0]; print((l + r) // 2, (t + b) // 2)
elif cmd == "checked":
    print(" ".join(a.get("checked", "?") for a, _ in nodes(sys.argv[2]) if match(a, "id", sys.argv[3])) or "NONE")
```

## 6. Pre-flight and install

Run `build.sh` in the background and `preflight.sh` beside it. Run `install.sh` once the build gate passes.

**`preflight.sh`**
```bash
. $(dirname $0)/lib.sh
exec > >(tee -a $OUT/preflight.txt) 2>&1
$RIG_TOOLKIT/rig_preflight.sh D_HU:wifi; $RIG_TOOLKIT/rig_cleanup.sh list
for s in $D_HU; do adb devices | grep -q "^$s" || { echo "$s NOT ON ADB"; exit 1; }; done   # MUST be on adb
unit hu; adb -s $H shell id                                                  # MUST show uid=0(root)
echo "hu locale $(adb -s $H shell getprop persist.sys.locale | tr -d '\r')"   # the dialog taps match English text; record it
for u in hu; do unit $u; echo "$u sdk $(adb -s $H shell getprop ro.build.version.sdk | tr -d '\r')"
  stopapp; [ -f $OUT/backup-$u.xml ] || pull_settings > $OUT/backup-$u.xml
  echo "$u backup bytes $(wc -c < $OUT/backup-$u.xml)"; done                # MUST be over 0
unit hu; HU_HOME=$(holder); echo "HU_HOME=$HU_HOME"                          # MUST NOT be $PKG, MUST NOT be empty
HU_OWNER=$(adb -s $H shell ls -ld /data/data/$PKG/shared_prefs | awk '{print $3":"$4}' | tr -d '\r')
printf 'HU_OWNER=%s\nHU_HOME=%s\n' "$HU_OWNER" "$HU_HOME" > $OUT/env.sh; cat $OUT/env.sh
```

**`install.sh`**
```bash
. $(dirname $0)/lib.sh
exec > >(tee $OUT/install.txt) 2>&1
unit hu; inst
```

D-HU MUST show `apk_identity exit 0` and commit `4f04c16873ce`. If `adb install -r -d` answers `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, stop and ask. Never uninstall.

## 7. The deciding lines

Each was checked with `grep -F -r` against `app/src` at `4f04c168`. All are INFO, in `$OUT/hu.txt`, between `<run>-start` and `<run>-end`.

| String | Source | Means |
|---|---|---|
| `AutomationMarker: ` | `AutomationReceiver` | the run window |
| `CarLauncherManager: asked the system for the home role` | `promptSetDefaultLauncher` | the request went out |
| `MainActivity: the app is the home app with launcher mode not stored yet, storing car launcher mode` | `MainActivity.onCreate` | this round's fix |
| `CarLauncherManager: home role granted after` | `onRoleRequestResult` | the result arrived (optional) |
| `SettingsFragment: the app holds the home role, storing car launcher mode` | `SettingsFragment.onViewCreated` | the open-path store |
| `CarLauncherManager: set launcher alias enabled=false` | `setLauncherEnabled` | the alias went off |
| `Package name cannot be null or empty` | system `RequestRoleActivity` | the old fault |
| `FATAL EXCEPTION` with `Process: com.andrerinas.headunitrevived` | system | a crash |

## 8. Runs

L1 then L3, chained in one capture: L1 ends with the role held and the key `true`, which is L3's start.

**`hu.sh`**
```bash
. $(dirname $0)/lib.sh
unit hu; CAPF=$OUT/hu.txt; th_gate hu || exit 1; cap $CAPF
# L1, the point: switch on, grant in the dialog
seed scalar:boolean:enable-car-launcher:false; begin L1; opensettings
echo "L1 switch-before $(checked L1-a)" | tee -a $OUT/L1-counts.txt
tap L1-b id settingSwitch
waitfor $CAPF L1-start 10 'asked the system for the home role'; sleep 2
tap L1-c text "Open Headunit"; sleep 1
tap L1-d text "Set as default"
waitfor $CAPF L1-start 15 'launcher mode not stored yet, storing car launcher mode'; sleep 3
echo "L1 key-live $(launcherkey)" | tee -a $OUT/L1-counts.txt; fin
# L3: switch off, Save, BACK, then a fresh settings screen
begin L3; opensettings
echo "L3 switch-before $(checked L3-a)" | tee -a $OUT/L3-counts.txt
tap L3-b id settingSwitch; sleep 2
echo "L3 holder-after-switch $(holder)" | tee -a $OUT/L3-counts.txt
tap L3-c id settingsSave; sleep 2
echo "L3 key-after-save $(launcherkey)" | tee -a $OUT/L3-counts.txt
adb -s $H shell input keyevent 4; sleep 3
echo "L3 resumed-after-back $(resumed)" | tee -a $OUT/L3-counts.txt
stopapp; echo "L3 holder-before-reopen $(holder)" | tee -a $OUT/L3-counts.txt
mark L3-reopen; opensettings; sleep 2
echo "L3 switch-reopen $(checked L3-d)" | tee -a $OUT/L3-counts.txt; fin
uncap $CAPF; th_end
for r in L1 L3; do R=$r; { echo "== $r"
  for s in 'asked the system for the home role' 'launcher mode not stored yet, storing car launcher mode' 'home role granted after' \
    'SettingsFragment: the app holds the home role' 'set launcher alias enabled=false' 'Package name cannot be null or empty'; do echo "$(n "$s") $s"; done
  } | tee -a $OUT/$r-counts.txt; done
echo "L3 reopen-window storing=$(cnt $CAPF L3-reopen L3-end 'SettingsFragment: the app holds the home role')" | tee -a $OUT/L3-counts.txt
```

If a `tap` prints `NO_UNIQUE_NODE`, the run is void: keep its dump and note it in Setup notes. If L1 voids or FAILs with `holder-after` other than `com.andrerinas.headunitrevived`, L3 cannot start: mark it UNTESTABLE.

### L1: the grant ends with launcher mode stored (the point)

PASS, all of:
- `holder-before` is `$HU_HOME`, `switch-before` is `false`, and `running-after-stop` reads 0 in `marks.txt` before every run.
- `asked the system for the home role` 1 or more, and the `Open Headunit` and `Set as default` taps both resolved.
- `Package name cannot be null or empty` 0.
- `launcher mode not stored yet, storing car launcher mode` 1 or more.
- `holder-after` is `com.andrerinas.headunitrevived`; `key-live` and `key-after` are both `value="true"`.
- `fatal` 0.

Record `home role granted after` either way; it is not graded. If the fix did nothing, round 3's `keeping the launcher alias` line would print instead and the key would read `value="false"`; the build gate rules that build out.

### L3: off and Save, then a fresh screen, stays off

PASS, all of:
- `switch-before` is `true`.
- `set launcher alias enabled=false` 1 or more; `key-after-save` is `value="false"`.
- `resumed-after-back` does not name `SettingsActivity`. If it does, a dialog held the screen: void the run and attach `L3-c.png`.
- `holder-before-reopen` is not `com.andrerinas.headunitrevived`.
- `reopen-window storing` 0, `switch-reopen` is `false`, `key-after` is `value="false"`, `fatal` 0.

FAIL when `reopen-window storing` is 1 or `key-after` is `value="true"`: say what `holder-after-switch` and `holder-before-reopen` read.

## 9. Restore

**`restore.sh`**
```bash
. $(dirname $0)/lib.sh
exec > >(tee $OUT/restore.txt) 2>&1
unit hu; adb -s $H shell cmd role add-role-holder $ROLE $HU_HOME; sleep 2; echo "hu holder $(holder)"   # MUST be $HU_HOME
for u in hu; do unit $u; echo "== $u"; stopapp
  adb -s $H push $OUT/backup-$u.xml /data/local/tmp/ohu-hwo.xml >/dev/null; adb -s $H shell chmod 644 /data/local/tmp/ohu-hwo.xml
  if [ $u = hu ]; then adb -s $H shell cp /data/local/tmp/ohu-hwo.xml /data/data/$PKG/shared_prefs/settings.xml
    adb -s $H shell chown $HU_OWNER /data/data/$PKG/shared_prefs/settings.xml; adb -s $H shell chmod 660 /data/data/$PKG/shared_prefs/settings.xml
  else adb -s $H shell run-as $PKG cp /data/local/tmp/ohu-hwo.xml shared_prefs/settings.xml; fi
  # one launch syncs the alias to the restored key; then stop
  adb -s $H shell am start -n $MAIN >/dev/null; sleep 8; stopapp
  pull_settings | diff -B - $OUT/backup-$u.xml >/dev/null && echo "$u SETTINGS_BACK" || echo "$u SETTINGS_DIFFER"
  adb -s $H shell rm -f /data/local/tmp/ohu-hwo.xml /sdcard/ohu-ui.xml /sdcard/ohu-ui.png
done
unit hu; echo "hu holder final $(holder)"
$RIG_TOOLKIT/rig_preflight.sh D_HU:wifi
```

`SETTINGS_BACK` or a diff of only WiFi Direct bookkeeping (as in round 3) is fine; give the diff in Setup notes. `hu holder final` MUST be `$HU_HOME`. The candidate APK stays installed.

## 10. Do not re-run

Round 3's L2 (open path), L4 (Cancel) and L5 (API 19 chooser) passed on `a2a3b0d7`, and none of that code changed since. Round 2's opacity runs and round 1's widgets runs also stay settled.

## 11. Report back

1. L1: `launcher mode not stored yet, storing car launcher mode`, `key-live` and `holder-after`.
2. L3: `holder-before-reopen`, `reopen-window storing` and `key-after`.
