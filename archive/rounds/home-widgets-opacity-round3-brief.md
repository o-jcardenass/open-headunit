# home-widgets-opacity, round 3 brief

Published name: `home-widgets-opacity-round3-brief.md`. Results go in `home-widgets-opacity-round3-results.md`.
Evidence: upload `home-widgets-opacity-round3-captures.zip` to the existing release `rig-evidence-home-widgets-opacity`.

Units: **D-HU, then D-SAM**, both idle with no phone. No Android Auto session and no radio change, so no phone capture is needed: no run touches the phone's Android Auto.

Queue line: `home-widgets-opacity-round3-brief.md` (C `a2a3b0d7` on `fork/feat/home-widgets-and-opacity`, three commits on `main` `947b3ae9`; one build, no baseline; units D-HU and D-SAM, idle; about 45 min; L1 and L2 are the point).

Pairing: D-HU and D-SAM, idle, no radio change. Any round that uses neither unit can run beside this one.

**Hand steps.** None.

**Injected taps.** At most 3 per run, each on a node resolved from a `uiautomator dump` by resource id or exact text, with exactly one match. The targets are the settings row's switch (`:id/settingSwitch`), the Save button (`:id/save_button_widget`) and, in the system's home-role dialog, the row `Open Headunit`, the button `Set as default` and the button `Cancel`. The role dialog is a system dialog with no verb. A target that does not resolve voids the run: never guess a coordinate.

**Time budget.** Build 20 min, pre-flight beside it. Install 3 min. D-HU runs 10 min, D-SAM 4 min. Thermal waits about 3 min. Restore 4 min. **About 45 min.**

## 1. Build and baseline

One APK. No baseline: each run's control is the role holder read before it. **History was not rewritten**: `a2a3b0d7` sits on round 2's `84079239` with two new commits.

| Arm | Ref | Commit | Tree |
|---|---|---|---|
| C (candidate) | `fork/feat/home-widgets-and-opacity` | `a2a3b0d71d8b9be2e058408bc4fc9a35bbc7c514` | `a9c7d363b88deb6559ae922332cdbbc3def014b6` |

The dev host ran the JVM suite at `a2a3b0d7`: 4255 tests, 0 failures. Do not run the unit tests on the rig.

Work from `~/Coding/StudioProjects`. Save every script block under `$OUT/scripts/` with `python3 -I $RIG_TOOLKIT/extract_brief_blocks.py <this brief> $OUT/scripts`. Run each with `bash $OUT/scripts/<name>.sh`.

**`build.sh`**
```bash
. $(dirname $0)/lib.sh
C=a2a3b0d71d8b9be2e058408bc4fc9a35bbc7c514; T=a9c7d363b88deb6559ae922332cdbbc3def014b6
WT=${WT:-$($RIG_TOOLKIT/wt-new.sh home-widgets-opacity-r3 candidate main | tail -1)}
git -C $WT fetch https://github.com/o-jcardenass/open-headunit.git feat/home-widgets-and-opacity
git -C $WT checkout --detach $C
[ "$(git -C $WT rev-parse 'HEAD^{tree}')" = $T ] || { echo TREE_MISMATCH; exit 1; }
$RIG_TOOLKIT/rig_thermal.sh wait 70
HUR_DIR=$WT MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh || { echo BUILD_FAILED; exit 1; }
cp $(ls -t $RIG_DATA/apks/com.andrerinas.headunitrevived_*.apk | head -1) $OUT/C.apk
{ echo "WT=$WT"; echo "C md5 $(md5sum < $OUT/C.apk | cut -c1-32)"
  echo "HomeRoleRequestPolicy=$(unzip -p $OUT/C.apk 'classes*.dex' | strings | grep -c HomeRoleRequestPolicy)"
  echo "openPath=$(unzip -p $OUT/C.apk 'classes*.dex' | strings | grep -cF 'SettingsFragment: the app holds the home role')"; } | tee $OUT/build.txt
```

Build gate: `HomeRoleRequestPolicy` MUST be 1 or more (new in `4a6e144f`), and `openPath` MUST be 1 or more (new in `a2a3b0d7`). A failed gate stops the round. If `git fetch` does not find `a2a3b0d71d8b`, stop and ask.

## 2. What this is and why

**The fault.** In launcher mode, the settings row and the wizard ask Android for the home role. From Android 10 the app started that request with `startActivity` and `FLAG_ACTIVITY_NEW_TASK`. The system's role dialog reads the asking app from the caller of a result, so it saw none, logged `Package name cannot be null or empty`, and closed in about 40 ms. The user saw nothing happen. A reporter's realme on API 34 showed three such 40 ms flickers in a row.

**Fix 1 (`4a6e144f`).** The request now starts for a result, with no new task. When the result comes back with no role in under 1 s (`HomeRoleRequestPolicy.IGNORED_WITHIN_MS`), the app opens the system Home settings, because no person could answer in that time. A slower "no" opens nothing. Below Android 10 the row opens the Home settings, as before.

**Fix 2 (`a2a3b0d7`).** The reporter confirmed fix 1 shows the dialog, but launcher mode was not stored. Picking the new home sends a Home intent, which clears the settings screen before its Save. The next opening of the screen then turned the launcher alias off, against a role the app holds. Now a granted role stores `enable-car-launcher` at once, and the settings screen stores it on open when the role is held.

| Id | What | Run | Kind |
|---|---|---|---|
| L1 | The row's switch shows the role dialog; a grant stores launcher mode | L1 | GATE, the point |
| L2 | The screen opened with the role held and the key `false` stores `true` and keeps the alias | L2 | GATE, the point |
| L3 | Off and Save, then reopen, stays off | L3 | GATE (known risk, below) |
| L4 | Cancel in the dialog opens nothing | L4 | GATE |
| L5 | API 19: the switch opens the Home settings or the Home chooser | L5 | GATE |

**Known risk in L3.** After the alias is turned off, Android may not take the home role away at once. If the role still names the app when the screen opens again, fix 2 turns launcher mode back on. L3 measures that: record the role holder at each step.

**Not covered.** The wizard's launcher step: no verb reaches that step and a seeded wizard needs scrolling. It calls the same `CarLauncherManager` code as the settings row, and `OnboardingLauncherPolicy` is under JVM tests. The under-1-s fallback (an app the user denied for good): setting that state takes two denials and a "Don't ask again" tick, and `HomeRoleRequestPolicyTest` covers the rule.

## 3. What is different about this round

- While the app holds the home role, a force-stop makes Android start it again as the home. `stopapp` puts the system Settings in front first and logs `running-after-stop`, which MUST be 0, or the seed that follows can race a live process.
- The dialog taps match English text (`Open Headunit`, `Set as default`, `Cancel`). If pre-flight reads a locale other than English, the dialog runs void: say so.
- D-HU's home role read `com.android.launcher3` in round 2. Every D-HU run starts from that holder and restore puts it back with `cmd role add-role-holder`, which needs D-HU's root adb.
- `SettingsActivity` opens with `--es extra_search_query`, which filters the list to the matching rows. That is how a run reaches the row without a scroll.
- On D-SAM, `SettingsActivity` is not exported: open it with `run-as $PKG am start` (`rig-quirks/units/D-SAM.md`). API 19 logs no activity `START` line, so grade L5 from `mResumedActivity`.

## 4. Settings keys

Each run seeds these with the app stopped, through `seed` in `lib.sh`.

| Key | Type | Value | Why |
|---|---|---|---|
| `enable-car-launcher` | boolean | `false` | the run's start state |
| `onboarding-version` | int | `2` | no wizard on launch |
| `has-accepted-disclaimer` | boolean | `true` | no disclaimer |
| `rename_notice_shown_v2`, `aa174_notice_shown` | boolean | `true` | no notices over the screen |
| `log-level` | int | `2` | DEBUG; every deciding line is INFO |
| `wifi-connection-mode` | int | `0` | nothing armed |
| `auto-connect-last-session`, `auto-start-self-mode`, `auto-connect-single-usb` | boolean | `false` | no connect on launch |

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
OUT=$RIG_DATA/rounds/home-widgets-opacity-round3; S=$OUT/scripts; mkdir -p $S $OUT/ui
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
$RIG_TOOLKIT/rig_preflight.sh D_HU:wifi; $RIG_TOOLKIT/rig_preflight.sh D_SAM:wifi; $RIG_TOOLKIT/rig_cleanup.sh list
for s in $D_HU $D_SAM; do adb devices | grep -q "^$s" || { echo "$s NOT ON ADB"; exit 1; }; done   # MUST be on adb
unit hu; adb -s $H shell id                                                  # MUST show uid=0(root)
echo "hu locale $(adb -s $H shell getprop persist.sys.locale | tr -d '\r')"   # the dialog taps match English text; record it
for u in hu sam; do unit $u; echo "$u sdk $(adb -s $H shell getprop ro.build.version.sdk | tr -d '\r')"
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
for u in hu sam; do unit $u; echo "== $u"; inst; done
```

Each unit MUST show `apk_identity exit 0` and commit `a2a3b0d71d8b`. If `adb install -r -d` answers `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, stop and ask. Never uninstall.

## 7. The deciding lines

Each was checked with `grep -F -r` against `app/src` at `a2a3b0d7`. All are INFO or WARN, in the unit's capture (`$OUT/hu.txt` or `$OUT/sam.txt`), between `<run>-start` and `<run>-end`.

| String | Source | Means |
|---|---|---|
| `AutomationMarker: ` | `AutomationReceiver` | the run window |
| `CarLauncherManager: asked the system for the home role` | `promptSetDefaultLauncher` | the request went out for a result |
| `CarLauncherManager: home role granted after` | `onRoleRequestResult` | the role came back held |
| `car launcher mode stored` | same line | the grant stored the key |
| `CarLauncherManager: home role not granted after` | `onRoleRequestResult` | a person said no |
| `with no answer, opening the Home settings` | `onRoleRequestResult` | the under-1-s fallback |
| `SettingsFragment: the app holds the home role, storing car launcher mode` | `SettingsFragment.onViewCreated` | fix 2's open path |
| `CarLauncherManager: set launcher alias enabled=false` | `setLauncherEnabled` | the alias went off |
| `Package name cannot be null or empty` | system `RequestRoleActivity` | the old fault |
| `FATAL EXCEPTION` with `Process: com.andrerinas.headunitrevived` | system | a crash |

## 8. Runs

D-HU runs L1 to L4 in one capture, chained: each run's end state is the next one's start, except L4, which resets the role first. Then D-SAM runs L5 in its own capture.

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
waitfor $CAPF L1-start 10 'home role granted after'; sleep 3; uidump L1-e
echo "L1 key-live $(launcherkey)" | tee -a $OUT/L1-counts.txt; fin
# L2, the point: the role held, the key back to false, open the screen
seed scalar:boolean:enable-car-launcher:false; begin L2; opensettings
echo "L2 switch $(checked L2-a)" | tee -a $OUT/L2-counts.txt; sleep 2; fin
# L3: switch off, Save, leave, reopen
begin L3; opensettings
tap L3-a id settingSwitch; sleep 1
tap L3-b id save_button_widget; sleep 2
echo "L3 holder-after-save $(holder)" | tee -a $OUT/L3-counts.txt
adb -s $H shell input keyevent 4; sleep 5
echo "L3 holder-before-reopen $(holder)" | tee -a $OUT/L3-counts.txt
mark L3-reopen; opensettings; echo "L3 switch-reopen $(checked L3-c)" | tee -a $OUT/L3-counts.txt; fin
# L4: role back to the stock home, switch on, Cancel in the dialog
adb -s $H shell cmd role add-role-holder $ROLE $HU_HOME; sleep 2
seed scalar:boolean:enable-car-launcher:false; begin L4; opensettings
tap L4-a id settingSwitch
waitfor $CAPF L4-start 10 'asked the system for the home role'; sleep 2     # the time is what L4 measures: over 1 s
tap L4-b text "Cancel"
waitfor $CAPF L4-start 10 'home role not granted after'; sleep 3
echo "L4 resumed $(resumed)" | tee -a $OUT/L4-counts.txt; fin
uncap $CAPF; th_end
for r in L1 L2 L3 L4; do R=$r; { echo "== $r"
  for s in 'asked the system for the home role' 'home role granted after' 'car launcher mode stored' 'home role not granted after' \
    'with no answer, opening the Home settings' 'SettingsFragment: the app holds the home role' \
    'set launcher alias enabled=false' 'Package name cannot be null or empty'; do echo "$(n "$s") $s"; done
  seg $CAPF $r-start $r-end | grep -aoE 'home role (not )?granted after [0-9]+ms'; } | tee -a $OUT/$r-counts.txt; done
echo "L3 reopen-window storing=$(cnt $CAPF L3-reopen L3-end 'SettingsFragment: the app holds the home role')" | tee -a $OUT/L3-counts.txt
```

**`sam.sh`**
```bash
. $(dirname $0)/lib.sh
unit sam; CAPF=$OUT/sam.txt; th_gate sam || exit 1; cap $CAPF
seed scalar:boolean:enable-car-launcher:false; begin L5; opensettings
tap L5-a id settingSwitch; sleep 4
echo "L5 resumed $(resumed)" | tee -a $OUT/L5-counts.txt; uidump L5-b
adb -s $H shell input keyevent 4; sleep 2
echo "L5 asked=$(n 'asked the system for the home role')" | tee -a $OUT/L5-counts.txt; fin
uncap $CAPF; th_end
```

If a `tap` prints `NO_UNIQUE_NODE`, the run is void: keep its dump, note it in Setup notes and go on to the next run. If L1 voids, L2 and L3 cannot run (they need the role held): mark them UNTESTABLE and run L4.

### L1: the switch shows the dialog, and a grant stores launcher mode (the point)

PASS, all of:
- `holder-before` is `$HU_HOME`, `switch-before` is `false`, and `running-after-stop` reads 0 in `marks.txt` before every run.
- `asked the system for the home role` 1 or more, and the `Open Headunit` and `Set as default` taps both resolved. Together they show the dialog appeared.
- `Package name cannot be null or empty` 0.
- `home role granted after` 1, and its line carries `car launcher mode stored`.
- `holder-after` is `com.andrerinas.headunitrevived`; `key-live` is `value="true"`.
- `with no answer, opening the Home settings` 0, `fatal` 0.

FAIL on any other value. A PASS with `Package name cannot be null or empty` above 0 is not possible: if it is above 0, the old code ran, so check the APK identity first.

### L2: the screen opened with the role held stores the key (the point)

The seed writes `false` with the role held: the state the reporter's device was left in.

PASS: `SettingsFragment: the app holds the home role` 1, `switch` is `true`, `set launcher alias enabled=false` 0, `holder-after` is `com.andrerinas.headunitrevived`, `key-after` is `value="true"`, `fatal` 0.

If the change did nothing, `switch` would read `false`, `key-after` `value="false"`, and the role holder would fall back to `$HU_HOME` after the alias went off.

### L3: off and Save, then reopen, stays off

PASS: `set launcher alias enabled=false` 1 or more, `switch-reopen` is `false`, `reopen-window storing` 0, `key-after` is `value="false"`, `fatal` 0. Record `holder-after-save` and `holder-before-reopen` either way: they decide the known risk.

FAIL when `reopen-window storing` is 1. Then the role still named the app at the reopen; say what `holder-before-reopen` read.

### L4: Cancel opens nothing

PASS: `asked the system for the home role` 1 or more, `home role not granted after` 1 with its value 1000 ms or more, `with no answer, opening the Home settings` 0, `resumed` names `com.andrerinas.headunitrevived`, `holder-after` is `$HU_HOME`, `fatal` 0.

If `not granted after` is under 1000 ms, the run did not measure the decline: mark it INCONCLUSIVE and give the value.

### L5: API 19 opens the Home settings or the Home chooser

PASS: `asked` 0, `resumed` names a package other than `com.andrerinas.headunitrevived` (the system Settings, or `android` for the chooser), `fatal` 0. Say which one it was and attach `L5-b.png`. The BACK closes it without a choice.

## 9. Restore

**`restore.sh`**
```bash
. $(dirname $0)/lib.sh
exec > >(tee $OUT/restore.txt) 2>&1
unit hu; adb -s $H shell cmd role add-role-holder $ROLE $HU_HOME; sleep 2; echo "hu holder $(holder)"   # MUST be $HU_HOME
for u in hu sam; do unit $u; echo "== $u"; stopapp
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
$RIG_TOOLKIT/rig_preflight.sh D_HU:wifi; $RIG_TOOLKIT/rig_preflight.sh D_SAM:wifi
```

`SETTINGS_BACK` MUST print for both units, and `hu holder final` MUST be `$HU_HOME`. If the launch rewrote a key, `SETTINGS_DIFFER` prints: give the diff in Setup notes rather than retrying. The candidate APK stays installed.

## 10. Do not re-run

Round 2's opacity runs (H-100, H-0, M-0, S-100, S-0) passed on `84079239`, and round 1's widgets switch runs passed on `2d41ab4e`. Neither changed since.

## 11. Report back

1. L1: whether the dialog appeared (the two dialog taps resolved), and `holder-after` and `key-live`.
2. L2: `switch` and `key-after`.
3. L3: `holder-after-save`, `holder-before-reopen` and `reopen-window storing`. They decide whether the open-path store can undo an "off".
