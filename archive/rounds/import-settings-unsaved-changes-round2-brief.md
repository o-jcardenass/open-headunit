# import-settings-unsaved-changes, round 2 brief

> **Revised 2026-10-17: the candidate was rebased onto `main` `28af450d`.** C moved from `0b6601d1` to
> `2ab6f398`. The two commits are the same patches; only the base moved. Re-read section 1 and the
> R0 checklist. No run, string or verdict rule changed. A copy that still names `0b6601d1` is stale.

Published as `import-settings-unsaved-changes-round2-brief.md`. One unit (D-HU), one build, no baseline APK. The point of the round is **R1**.

This is the first round that runs on the rig. The round 1 brief failed its own check (it let one run hold more than 5 taps) and was never published, so no round 1 results or grade exist. Nothing is settled.

## 1. Build and baseline

| Arm | Ref | SHA | Tree |
|---|---|---|---|
| **C** (candidate) | `fork/fix/import-settings-unsaved-changes` | `2ab6f3981f107be636aa179350170341dafb9ced` | `178930ffd4fde635b233746ecf8c57a0178b92a8` |

C is two commits on `main` `28af450dc1e50f5264cc5415a907684daa807ab7`. The branch was rebased there on 2026-10-17 from `0b6601d1`; both commits are the same patches.

```bash
git fetch fork fix/import-settings-unsaved-changes
git checkout --detach 2ab6f3981f107be636aa179350170341dafb9ced
git rev-parse HEAD^{tree}      # must print 178930ffd4fde635b233746ecf8c57a0178b92a8
```

If the fetch finds no such branch, or the tree differs, stop and escalate. That is a failed build gate.

Build C with `rig-toolkit/build_hur.sh`. Copy the APK out of `apks/` at once (`rig-quirks/topics/tooling.md`). Do not run the unit tests: C's 3314 JVM tests passed at this SHA. Install with `adb -s $HU install -r`, never an uninstall.

**APK identity (R0):** `send ACTION_QUERY_STATE`. The `commit` on the `data=` line must start `2ab6f398`. Then pull the APK and look for a class that only C has:

```bash
unzip -p "$OUT/live.apk" 'classes*.dex' | strings | grep -cF 'SettingsPendingCopy'    # must print 1 or more
```

## 2. What this is and why

A user imports a settings backup from Settings, Advanced, Import settings, Import from app backup folder. The values apply at once, and some (orientation) recreate the screen. Then BACK raises "Unsaved Changes". The user thinks that Discard throws the import away, so they press Save.

The import is a direct write: `SettingsBackupManager.importFromJson` commits every key before the screen hears about it. So the prompt is false. The screen keeps a second copy of the settings (the pending copy), and three paths left that copy different from the stored values:

- **H1, the reporter's trigger.** The Basic/Advanced tab was a pending setting. A tab tap made the screen "changed". After the import's `recreate()`, view state restore checked the Advanced button again, and its listener marked the screen changed.
- **H2, data loss.** The reload after Import, Reset and Discard missed `mic-input-source`, `static-bssid` and `static-p2p-bssid`, and forced the native driver mode to AUTO. So the next tap on any row armed Save, and Save wrote the old values back over the imported ones.
- **H3.** The DPI sub-screen left a result handle that every new view received again.

C makes one class (`SettingsPendingCopy`) the only loader and comparer of the copy. A tab tap now writes `advanced-settings-active` at once and is not a pending change. The DPI handle is gone. JVM tests cover the field drift (H2), and H3 has no path once the handle is gone. This round covers what the JVM cannot reach: view state restore after a recreate, and the Save button after a real import.

## 3. What is different about this round

- **The tap budget is at most 5 per run.** No verb reaches the settings screen's Import flow. `AutomationEffectRunner.importSettings` writes the prefs but never runs `SettingsFragment`, so it cannot show the dialog under test. So the rows and dialogs take injected taps (`rig-quirks/topics/tooling.md`, "Injected taps, when the operator says so"). Each tap goes on a node found in a `uiautomator dump`, by resource id or exact text. No scroll, no swipe, no typed text.
- **One run holds one cycle.** A run is: force-stop, write the baseline, start the capture, do the steps, stop the capture, force-stop. `run_open` is the only function that sets the tap counter to 0. `tap_xy` refuses a sixth tap in a run, logs the refusal, and the run is void.
- **The screen opens with `am start`, and BACK is `input keyevent 4`.** Neither is a tap (template section 3). `SettingsActivity` takes `--es extra_search_query <text>`, and the fragment filters the list to that text. The Import row is in the Backup category near the end of the list. So every import step opens the screen with a search query, and nothing scrolls.
- **R1 uses two visits, not one.** Plan r2 asked for the tab tap and the Import in one visit. That is not possible on this screen without a scroll or typed text:
  - `renderSettings` sets `settingsTabGroup.isEnabled = searchQuery.isBlank()`, so a search disables the tab buttons.
  - Without a search, the Import row is off screen, because it is not a Basic row and it sits near the end of the Advanced list.

  So R1 taps the tab in visit 1 and imports in visit 2, inside one run, with 4 taps. The check that the plan put at Import ("no discard prompt") reads `hasChanges` in `startImportSettings`. Visit 1 reads the same flag twice right after the tab tap: the Save button's `enabled` (`updateSaveButtonState` sets `isEnabled = hasChanges`), and the `Unsaved Changes` dialog on BACK. Those two checks prove that the tab tap is not a pending edit. Visit 2 keeps the discard-prompt check as a regression guard. On C, the tab tap writes `advanced-settings-active` at once, so visit 2 starts in the same stored state that one long visit would hold, and the recreate restores the same tab.
- **The defect prints nothing to the log.** The deciding evidence is the dump (the dialogs and the Save button's `enabled`), the stored `settings.xml`, and one framework line that counts activity creations. Log level 2 (INFO) carries every app line graded here.
- **No run touches the phone's Android Auto**, so no phone capture is taken. Both phones stay in airplane mode for the whole round, so no session can start and take the screen.
- **The reporter ran Android 10. The rig has no Android 10 unit.** D-HU (Android 14) runs every run. The Material library is the same on every API level, so the view restore path is the same.
- **R3 and PC need the floating button toggle to stay on our screen.** Turning it on calls `FloatingButtonManager.requestOverlayPermission`, which opens the system overlay screen only when the permission is missing. Stage S grants it with `appops` first.
- **Pre-registered results that are not FAILs:** a run where a target does not resolve is void. An R1 run whose import did not recreate the screen is void. A scenario with fewer than 3 graded runs after 5 runs is INCONCLUSIVE, with the count.
- **This is the thread's first round on the rig.** Create the release `rig-evidence-import-settings-unsaved-changes` and add `import-settings-unsaved-changes-round2-captures.zip` to it (template section 7).

## 4. Settings keys

Write with the app stopped, and read back at the start of every run. D-HU: `hu_put` (root), from one backup taken at the start of the round. `KEYS_R1` is the baseline for R1, and `KEYS_ADV` is the baseline for PC, R3 and R6:

| Key | Type | Value | Why |
|---|---|---|---|
| `log-level` | int | `2` | INFO carries every app line graded |
| `onboarding-version` | int | `2` | keeps the wizard off the screen |
| `advanced-settings-active` | boolean | `false` in R1, `true` in PC, R3 and R6 | R1 starts on the Basic tab, as the reporter did |
| `screen-orientation` | int | `0` | SYSTEM. Backup A changes it to `2` (LANDSCAPE), and `shouldRecreateAfterImport` then recreates the screen |
| `mic-input-source` | int | `0` | backups A and B change it |
| `static-bssid` | string | delete | backup B sets it |
| `enable-floating-button` | boolean | `false` | R3 and PC toggle it, and never save it |
| `native-driver-selection-mode` | int | `0` | DISABLED, not the AUTO that the old reload forced. R3 shows a drift |

```bash
KEYS_R1="int:log-level=2 int:onboarding-version=2 bool:advanced-settings-active=false int:screen-orientation=0 int:mic-input-source=0 del:static-bssid bool:enable-floating-button=false int:native-driver-selection-mode=0"
KEYS_ADV="int:log-level=2 int:onboarding-version=2 bool:advanced-settings-active=true int:screen-orientation=0 int:mic-input-source=0 del:static-bssid bool:enable-floating-button=false int:native-driver-selection-mode=0"
```

Read and record, but do not write: `app-language`, `use-aac-audio`, `audio-latency-multiplier`, `audio-queue-capacity`. The three audio keys are a deliberate worst case on this rig, and this round does not touch them. `ACTION_LOG_MARKER` is not in `AutomationCommandPolicy.CONFIGURING` on C, so `allow-external-configuration` is not needed.

### The three backup files

Write them on the host. The file names must match `(?:open-headunit|headunit-revived)-settings-\d{8}-\d{6}\.json`. The year 2099 puts them at the top of the picker, which sorts names in descending order.

```bash
cat > "$OUT/open-headunit-settings-20991231-000001.json" <<'EOF'
{"format":"open-headunit-settings","version":1,"settings":{"screen-orientation":2,"mic-input-source":1}}
EOF
cat > "$OUT/open-headunit-settings-20991231-000002.json" <<'EOF'
{"format":"open-headunit-settings","version":1,"settings":{"mic-input-source":6,"static-bssid":"02:11:22:33:44:55"}}
EOF
cat > "$OUT/open-headunit-settings-20991231-000003.json" <<'EOF'
{"format":"open-headunit-settings","version":1,"settings":{"screen-orientation":0,"mic-input-source":0}}
EOF
BKA=open-headunit-settings-20991231-000001.json   # A: recreates (orientation) and changes the mic source
BKB=open-headunit-settings-20991231-000002.json   # B: no recreate; changes the mic source and the static BSSID
BKC=open-headunit-settings-20991231-000003.json   # C: equal to the baseline
```

## 5. Every action

Make the folder `~/Coding/StudioProjects/rig-data/rounds/import-settings-unsaved-changes-round2/`. Copy `ohu_setkeys.py` into it from `rig-data/rounds/projection-teardown-and-relays-round3/` (if it is not there, `find ~/Coding/StudioProjects/rig-data -name ohu_setkeys.py`). This round writes no `set:` key, so the empty-set fix in `tooling.md` does not change anything here. Save `libimp.sh` below beside it and source it. Run the round as one script in the background under `flock /tmp/ohu-rig.lock`. List every file in Setup notes.

```bash
HU=27870808938846; POCO=4f4027e9; MOTO=ZY22GC3BM4
OUT=~/Coding/StudioProjects/rig-data/rounds/import-settings-unsaved-changes-round2; mkdir -p $OUT; cd $OUT
BASEXML=$OUT/settings-backup-DHU.xml; WANT_MD5=<C md5>
source ./libimp.sh
```

**`libimp.sh`**:

```bash
# libimp.sh : source after setting HU, OUT, BASEXML, WANT_MD5.
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
SA=$PKG/com.andrerinas.openheadunit.main.SettingsActivity
EXT=/sdcard/Android/data/$PKG/files
UNSAVED='Unsaved Changes'
DISCARD_PENDING='Importing a backup will discard your unsaved settings changes. Continue?'
send() { a=$1; shift; adb -s "$HU" shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; sleep 0.3; }
mark() { send ACTION_LOG_MARKER --es text "$1" >/dev/null; echo "$(date +%T.%3N) $1" >> "$OUT/marks.log"; }

# Host thermal, verbatim from lib1008.sh (rig-quirks/topics/tooling.md)
th_pkg() { if [ -x ../rig_thermal.sh ]; then ../rig_thermal.sh status | grep -o ' pkg=[0-9]*' | head -1 | cut -d= -f2; return; fi
  for z in /sys/class/hwmon/hwmon*; do [ "$(cat $z/name 2>/dev/null)" = coretemp ] && { echo $(( $(cat $z/temp1_input) / 1000 )); return; }; done
  echo $(( $(cat /sys/class/thermal/thermal_zone0/temp) / 1000 )); }
th_thr() { cat /sys/devices/system/cpu/cpu0/thermal_throttle/package_throttle_count 2>/dev/null || echo 0; }
th_wait() { local lim=${1:-75} n=0; while [ "$(th_pkg)" -ge "$lim" ]; do n=$((n+1)); [ $n -ge 90 ] && return 1; sleep 10; done; }
th_watch() { while :; do echo "$(date +%T) pkg=$(th_pkg)C throttle_pkg=$(th_thr)"; sleep 20; done >> "$1"; }
th_gate() { th_wait 75 || { echo "$(date +%T) HOST_TOO_HOT ${RUN:-run}" | tee -a "$OUT/thermal.log"; return 3; }
  [ -n "$THPID" ] && kill $THPID 2>/dev/null; th_watch "$OUT/${RUN:-run}.thermal" & THPID=$!
  echo "$(date +%T) ${RUN:-run} start pkg=$(th_pkg)C throttle_pkg=$(th_thr)" >> "$OUT/thermal.log"; }

# apk_check : pull the installed APK and compare its md5 with $WANT_MD5
apk_check() { local m; adb -s "$HU" pull $(adb -s "$HU" shell pm path $PKG | cut -d: -f2 | tr -d '\r') "$OUT/live.apk" >/dev/null 2>&1
  m=$(md5sum "$OUT/live.apk" | cut -d' ' -f1); echo "$(date +%T) live=$m want=$WANT_MD5" | tee -a "$OUT/apk-check.log"; [ "$m" = "$WANT_MD5" ]; }

# Capture: main, system and events buffers in one file (events carries the activity-create line).
cap_start() { CAP=$OUT/$1.hu.logcat; : > "$CAP"; adb -s "$HU" logcat -G 16M; adb -s "$HU" logcat -b main,system,events -c
  ( stdbuf -oL adb -s "$HU" logcat -b main,system,events -v time >> "$CAP" ) & CAPPID=$!; sleep 1; }
cap_stop() { pkill -P $CAPPID 2>/dev/null; kill $CAPPID 2>/dev/null; sleep 0.5
  tr -d '\r' < "$CAP" > "$CAP.tmp" && mv "$CAP.tmp" "$CAP"; ps aux | grep -c "[l]ogcat -b main,system,events"; }   # must print 0
# win <label> : the capture lines from "AutomationMarker: <label>-start" to "AutomationMarker: <label>-end"
win() { awk -v s="AutomationMarker: $1-start" -v e="AutomationMarker: $1-end" 'index($0,s){f=1} f{print} f&&index($0,e){exit}' "$CAP"; }
creates() { win "$1" | grep -aE '(wm|am)_on_create_called' | grep -acF 'main.SettingsActivity'; }
landed() { win "$RUN" | grep -acF 'AutomationReceiver: '; }

# Settings: root write with the app stopped (verbatim from lib1008.sh, without its read-back grep), and a root read.
hu_put() { python3 ohu_setkeys.py "$1" new.xml "${@:2}" || return 1
  OWN=$(adb -s "$HU" shell "su -c 'stat -c %u:%g /data/data/$PKG'" | tr -d '\r')
  printf 'cp /data/local/tmp/new.xml /data/data/%s/shared_prefs/settings.xml\nchown %s /data/data/%s/shared_prefs/settings.xml\nchmod 660 /data/data/%s/shared_prefs/settings.xml\n' "$PKG" "$OWN" "$PKG" "$PKG" > hu_put.sh
  adb -s "$HU" push new.xml /data/local/tmp/new.xml >/dev/null; adb -s "$HU" push hu_put.sh /data/local/tmp/hu_put.sh >/dev/null
  adb -s "$HU" shell "su -c 'sh /data/local/tmp/hu_put.sh'"; }
prefs() { sleep 1; adb -s "$HU" shell "su -c 'cat /data/data/$PKG/shared_prefs/settings.xml'" | tr -d '\r' > "$OUT/$1.settings.xml"; }
# kv <prefs-label> <key> : the stored value, or ABSENT
kv() { python3 -I -c '
import sys,xml.etree.ElementTree as ET
for e in ET.parse(sys.argv[1]).getroot():
    if e.get("name")==sys.argv[2]: print(e.get("value") if e.get("value") is not None else (e.text or "")); sys.exit()
print("ABSENT")' "$OUT/$1.settings.xml" "$2"; }

# Screen
top() { adb -s "$HU" shell dumpsys activity activities | tr -d '\r' | grep -a -m1 -E 'topResumedActivity|mResumedActivity'; }
onset() { top | grep -qF 'main.SettingsActivity'; }
# wait_onset : poll onset every 0.5 s, at most 3 s. 0 as soon as the settings screen is in front.
wait_onset() { local i; for i in 1 2 3 4 5 6; do onset && return 0; sleep 0.5; done; return 1; }
# open_settings [query] : open the settings screen, filtered to <query> when given. 0 when it is in front.
open_settings() { local q=""; [ -n "$1" ] && q="--es extra_search_query '$1'"
  adb -s "$HU" shell "am start -n $SA --ei extra_destination 0 $q" >/dev/null 2>&1; wait_onset && { OPEN_ROUTE=am; return 0; }
  adb -s "$HU" shell "run-as $PKG am start --user 0 -n $SA --ei extra_destination 0 $q" >/dev/null 2>&1; wait_onset && { OPEN_ROUTE=run-as; return 0; }
  OPEN_ROUTE=none; return 1; }
back() { adb -s "$HU" shell input keyevent 4; sleep 2; }
# ui <step> : dump the screen to $OUT/ui.xml and keep a copy per step
ui() { adb -s "$HU" shell uiautomator dump /sdcard/ohu-ui.xml >/dev/null 2>&1; sleep 0.3
  adb -s "$HU" exec-out cat /sdcard/ohu-ui.xml > "$OUT/ui.xml"; cp "$OUT/ui.xml" "$OUT/$RUN.$1.ui.xml"; [ -s "$OUT/ui.xml" ]; }
# has <fixed-string> : how many times it occurs in the last dump (the dump is one line, so grep -c cannot count)
has() { grep -aoF -- "$1" "$OUT/ui.xml" | wc -l; }
# target <id|*> <text|-> [prefix] : centre "x y" of the one enabled node whose resource-id ends ":id/<id>" ("*" = any id)
#   and whose text equals <text> (or starts with it, with "prefix"); "-" skips the text test. Fails on 0 or 2+ matches.
target() { python3 -I -c '
import sys,re,xml.etree.ElementTree as ET
f,want,txt=sys.argv[1],sys.argv[2],sys.argv[3]; pre=len(sys.argv)>4
n=[e for e in ET.parse(f).iter("node") if (want=="*" or e.get("resource-id","").endswith(":id/"+want)) and e.get("enabled")=="true"]
if txt!="-": n=[e for e in n if (e.get("text","").startswith(txt) if pre else e.get("text","")==txt)]
if len(n)!=1: sys.exit("matches=%d"%len(n))
x1,y1,x2,y2=map(int,re.findall(r"\d+",n[0].get("bounds"))); print((x1+x2)//2,(y1+y2)//2)' "$OUT/ui.xml" "$@"; }
# attr <id> <attribute> : that attribute of the one node whose resource-id ends ":id/<id>"
attr() { python3 -I -c '
import sys,xml.etree.ElementTree as ET
n=[e for e in ET.parse(sys.argv[1]).iter("node") if e.get("resource-id","").endswith(":id/"+sys.argv[2])]
print(n[0].get(sys.argv[3]) if len(n)==1 else "matches=%d"%len(n))' "$OUT/ui.xml" "$1" "$2"; }
# tap_xy <name> <x> <y> : one injected tap, logged. At most 5 per run: refuses a sixth and voids the run.
tap_xy() { if [ "${TAPS:-0}" -ge 5 ]; then echo "$(date +%T) $RUN REFUSED tap6 $1" >> "$OUT/taps.log"; echo -e "$RUN\t$1\tTAP_BUDGET" >> "$OUT/void.tsv"; return 1; fi
  TAPS=$((TAPS+1)); echo "$(date +%T) $RUN tap$TAPS $1 $2 $3" >> "$OUT/taps.log"; adb -s "$HU" shell input tap "$2" "$3"; sleep 2; }
# tap_on <name> <id|*> <text|-> [prefix] : dump, resolve, tap. Returns 2 (and taps nothing) when the target does not resolve.
tap_on() { local n=$1 xy; shift; ui "pre-$n" || return 2; xy=$(target "$@") || { echo -e "$RUN\t$n\tNO_TARGET $xy" | tee -a "$OUT/void.tsv"; return 2; }
  tap_xy "$n" $xy || return 2; }
# run_open <run-id> <baseline-keys> : stop, baseline settings, read back, capture, marker.
#   The ONLY place that sets TAPS to 0. One run = one cycle.
run_open() { RUN=$1; TAPS=0; th_gate || return 3; adb -s "$HU" shell am force-stop $PKG; sleep 1; hu_put "$BASEXML" $2 >/dev/null; prefs "$RUN-base"
  for k in advanced-settings-active screen-orientation mic-input-source static-bssid enable-floating-button native-driver-selection-mode; do echo -e "$RUN\tbase\t$k=$(kv $RUN-base $k)"; done | tee -a "$OUT/prefs.tsv"
  top | grep -qF 'AapProjectionActivity' && { echo -e "$RUN\tprojection-in-front" >> "$OUT/void.tsv"; return 4; }
  cap_start "$RUN"; mark "$RUN-start"; }
run_close() { mark "$RUN-end"; sleep 1; cap_stop; adb -s "$HU" shell am force-stop $PKG; echo -e "$RUN\ttaps=$TAPS\tlanded=$(landed)" | tee -a "$OUT/runs.tsv"; }
row() { echo -e "$RUN\t$*" | tee -a "$OUT/results.tsv"; }
```

A run is void when `run_open` returns non-zero, when `open_settings` returns 1, when `tap_on` returns 2, or when `landed` is 0 (the marker broadcast never reached the app). Write `void.tsv` and go on to the next run of the same scenario. A void run is not a FAIL.

Every action in this round:

| Action | Command | Notes |
|---|---|---|
| Read the build | `send ACTION_QUERY_STATE` | `commit` on the `data=` line |
| Mark a step | `mark <label>` | `AutomationMarker: <label>` at WARN |
| Open the settings screen | `open_settings` or `open_settings '<query>'` | `am start`, template section 3. Not a verb, not a tap |
| Close the screen, or cancel a dialog | `back` | `input keyevent 4`. Not a tap |
| Tap the Advanced tab | `tap_on tab tabAdvanced -` | injected tap on a dumped node |
| Tap the Import row | `tap_on import settingName 'Import settings'` | injected tap on a dumped node |
| Choose the app folder | `tap_on folder '*' 'Import from app backup folder'` | injected tap on a dumped node |
| Choose a backup | `tap_on backup '*' "$BKA" prefix` (or `$BKB`, `$BKC`) | the label is the file name, a line break, then its folder |
| Toggle the floating button row | `tap_on float settingName 'Floating Launcher Button'` | a row tap toggles its switch (`SettingsAdapter`) |

Taps per run, all inside the budget of at most 5 per run:

| Run | Taps | Graded runs needed | Runs allowed |
|---|---|---|---|
| S0 (screen check) | 0 | 1 | 2 |
| PC-1 | 1 | 1 | 2 |
| R1-1 to R1-5 | 4 | 3 | 5 |
| R3-1 to R3-5 | 5 | 3 | 5 |
| R6-1 to R6-5 | 3 | 3 | 5 |

R3 uses the whole budget. A target that does not resolve in R3 voids that run.

## 6. The deciding lines and strings

Each app string was checked with `grep -F -r` against `app/src/main` at `2ab6f398`. The English text comes from `app/src/main/res/values/strings.xml`, so D-HU must show the app in English. Stage S checks that.

| String | Where it is read | Meaning |
|---|---|---|
| `Unsaved Changes` | dump, `has` | the false prompt on BACK (`R.string.unsaved_changes`) |
| `Importing a backup will discard your unsaved settings changes. Continue?` | dump, `has` | the discard-pending prompt at Import (`R.string.import_settings_discard_pending`) |
| `Import settings` | dump, `:id/settingName` text | the Import row |
| `Import from app backup folder` | dump, any id | the option in the Import dialog |
| `Floating Launcher Button` | dump, `:id/settingName` text | the row that R3 and PC toggle |
| `:id/tabAdvanced` | dump, `checked` | the Advanced tab |
| `:id/save_button_widget` | dump, `enabled` | the Save button. `true` only while `hasChanges` is true |
| `advanced-settings-active`, `screen-orientation`, `mic-input-source`, `static-bssid`, `enable-floating-button`, `native-driver-selection-mode` | `settings.xml`, `kv` | the stored values |
| `AutomationReceiver: ` | unit capture, `landed` | a verb landed |
| `AutomationMarker: ` | unit capture | the window bounds, `<RUN>-start` to `<RUN>-end` |
| `wm_on_create_called` (or `am_on_create_called`) with `main.SettingsActivity` | unit capture, events buffer, `creates <label>` | one creation of the settings screen. A framework line, not app source |
| `topResumedActivity` or `mResumedActivity` with `main.SettingsActivity` | `dumpsys activity activities`, `onset` | the settings screen is in front. A framework line |

## 7. Runs

**Order:** R0, S, S0, PC-1, R1-1 to R1-5, R3-1 to R3-5, R6-1 to R6-5, Closing.

**Stop rule, per scenario:** stop at the 3rd graded run. A scenario with fewer than 3 graded runs after its 5th run is INCONCLUSIVE, with the count. Write `results.tsv` after every run and the results file after every scenario.

**Count dialogs, not runs.** Every scenario reports the number of graded runs that showed `Unsaved Changes` on BACK.

### R0 Gate (once)

PASS needs all of these:

- C's tree as in section 1, and C's APK md5 recorded.
- `adb install -r` succeeded, and `apk_check` is true.
- `commit` starts `2ab6f398`, and `SettingsPendingCopy` is in the DEX.

Back up `settings.xml` before and after the install, diff the two, and state the delta (`tooling.md`: an install can wipe it). Read `stat -c %U:%G` of `/data/data/$PKG/shared_prefs` as root. If it is not the app's own uid, `chown` it to the uid and gid of `/data/data/$PKG`, and record both readings. R1 grades a value that the app writes (`rig-quirks/units/D-HU.md`). Time: 3 min plus the build.

### S Preparation (once, no taps, not a run)

```bash
for P in $POCO $MOTO; do adb -s $P shell cmd connectivity airplane-mode enable; done; sleep 5
for P in $POCO $MOTO; do echo "$P"; adb -s $P shell dumpsys bluetooth_manager | grep -a -m1 -E '^ *enabled:'; adb -s $P shell dumpsys wifi | grep -a -m1 'Wi-Fi is'; done
adb -s $HU shell appops get $PKG SYSTEM_ALERT_WINDOW                               # record before
adb -s $HU shell appops set $PKG SYSTEM_ALERT_WINDOW allow; adb -s $HU shell appops get $PKG SYSTEM_ALERT_WINDOW   # must print allow
adb -s $HU shell settings get system user_rotation; adb -s $HU shell settings get system accelerometer_rotation   # record only
adb -s $HU shell mkdir -p $EXT; adb -s $HU shell ls -l $EXT                     # record existing backups; do not delete any
for f in $BKA $BKB $BKC; do adb -s $HU push "$OUT/$f" $EXT/; done; adb -s $HU shell ls -l $EXT
```

If a phone is not on adb, record it in Setup notes, and turn its radios off by hand. That is a hand step: a phone is hardware, and its radios have no verb. Each phone must read Bluetooth off and WiFi off.

If the push to `$EXT` is refused, use the app's cache folder, which the picker also searches (`appBackupDirectories`). For each file: `adb push` it to `/data/local/tmp/`, then `su -c 'cp /data/local/tmp/<f> /data/data/$PKG/cache/'`, then `chown` it to the uid and gid of `/data/data/$PKG`. Record which folder you used.

### S0 Screen check (no taps; at most 2 runs)

```bash
run_open S0 "$KEYS_R1"
open_settings 'Open Headunit'; ui s0a
target settingName 'Import settings'; target settingName 'Floating Launcher Button'; attr save_button_widget enabled; back
open_settings 'Import settings'; ui s0b; target settingName 'Import settings'; back
open_settings; ui s0c; target tabAdvanced -; attr tabAdvanced checked; back
run_close
```

PASS needs:

- `OPEN_ROUTE` is `am` or `run-as` each time.
- Every `target` prints one coordinate pair.
- `attr save_button_widget enabled` prints `false`.
- `attr tabAdvanced checked` prints `false`.

Take one screenshot at `s0a` (template section 3) and attach it.

If a target with English text does not resolve, read `app-language` from the backup. Add `str:app-language=en` to both `KEYS_R1` and `KEYS_ADV`, record that in Setup notes, and do S0 once more. If it still fails:

- a failed `s0a` makes R3 and PC-1 UNTESTABLE;
- a failed `s0b` makes R1 and R6 UNTESTABLE;
- a failed `s0c` makes R1 UNTESTABLE.

Time: 5 min.

### PC-1 Positive control: a real change still raises the prompt (1 tap)

This proves that the dump finds the `Unsaved Changes` dialog on this unit, so a zero in R1, R3 and R6 means something. Run PC-1 before R1. It is one run of one tap. If it is void, run it once more.

```bash
run_open PC-1 "$KEYS_ADV"
open_settings 'Open Headunit'
tap_on float settingName 'Floating Launcher Button'       # tap 1
onset; ui float-on; attr save_button_widget enabled       # expect: in front, true
back; ui back1; has "$UNSAVED"; onset                     # expect: 1, still in front
back; sleep 1                                             # cancels the dialog
run_close; prefs PC-1-after; kv PC-1-after enable-floating-button   # expect: false
```

PASS needs:

- after the tap, the screen is still in front (no overlay screen opened) and Save is `true`;
- after the first BACK, `has "$UNSAVED"` is 1 or more and `onset` is true;
- `enable-floating-button` reads `false` after the stop.

A FAIL of PC-1 makes R1, R3 and R6 INCONCLUSIVE, because a zero count then proves nothing.

### R1 Import that recreates the screen, after a tab tap (the point of the round; 4 taps per run)

The reporter's path: start on Basic, tap Advanced, import a backup that changes the orientation, then BACK. Two visits inside one run, for the reason in section 3. `$k` is 1 to 5.

```bash
run_open R1-$k "$KEYS_R1"
# visit 1: the tab tap
open_settings
tap_on tab tabAdvanced -                                  # tap 1
ui tab-after; attr tabAdvanced checked                    # expect: true
attr save_button_widget enabled                           # expect: false
prefs R1-$k-tab; kv R1-$k-tab advanced-settings-active    # expect: true (written at once, no Save)
back; ui back-a; has "$UNSAVED"; onset                    # expect: 0, not in front
# visit 2: the import
mark "$RUN-import-start"
open_settings 'Import settings'
tap_on import settingName 'Import settings'               # tap 2
ui import-dialog; has "$DISCARD_PENDING"                  # expect: 0
tap_on folder '*' 'Import from app backup folder'         # tap 3
tap_on backup '*' "$BKA" prefix                           # tap 4
sleep 4; onset; ui after-import
attr tabAdvanced checked; attr save_button_widget enabled # expect: true, false
prefs R1-$k-import; for x in screen-orientation mic-input-source advanced-settings-active; do echo "$x=$(kv R1-$k-import $x)"; done
mark "$RUN-import-end"
back; ui back-b; has "$UNSAVED"; onset                    # expect: 0, not in front
run_close
creates "$RUN-import"                                     # expect: 2 or more
```

Grade each run on these comparisons:

| Check | PASS | FAIL | Void |
|---|---|---|---|
| a. `advanced-settings-active` in `R1-$k-tab` | `true` | `false` or `ABSENT` | |
| b. Save `enabled` after the tab tap | `false` | `true` | |
| c. `has "$UNSAVED"` after BACK in visit 1 | 0, and `onset` false | 1 or more | |
| d. `has "$DISCARD_PENDING"` at Import | 0 | 1 or more | |
| e. `creates "$RUN-import"` | | | less than 2: the import did not recreate the screen. If the capture has no `on_create_called` line at all, the events buffer is not readable. Then use `screen-orientation=2` in `R1-$k-import` as the proof of the recreate, and say so in Setup notes |
| f. stored after the import | `screen-orientation=2`, `mic-input-source=1`, `advanced-settings-active=true` | any other value | |
| g. **`has "$UNSAVED"` after BACK in visit 2** | **0, and `onset` false** | **1 or more** | |

A run is PASS when a, b, c, d, f and g pass and e is not void. Report `attr save_button_widget enabled` after the import (expect `false`) and `attr tabAdvanced checked` after the recreate (expect `true`) beside each run, ungraded. If check g FAILs, keep the run's dumps and capture whole.

What a PASS would look like if the change did nothing: on `main`, checks b and c already show the change, because the tab was a pending edit. So a PASS of b and c is a direct measurement of the tab fix. Check g means something only when e reads 2 or more, which is why e voids the run.

### R3 Import with no recreate, then a row toggled on and off (5 taps per run)

H2's path. After a correct reload, a toggle and its reverse leave nothing to save. On `main`, the stale mic source, static BSSID and driver mode left Save armed, and a Save wrote them back. This run uses all 5 taps. `$k` is 1 to 5.

```bash
run_open R3-$k "$KEYS_ADV"
open_settings 'Open Headunit'
tap_on import settingName 'Import settings'               # tap 1
tap_on folder '*' 'Import from app backup folder'         # tap 2
tap_on backup '*' "$BKB" prefix                           # tap 3
sleep 3; onset; ui after-import; attr save_button_widget enabled      # expect: false
prefs R3-$k-import; for x in mic-input-source static-bssid; do echo "$x=$(kv R3-$k-import $x)"; done
tap_on float settingName 'Floating Launcher Button'       # tap 4
onset; ui toggle-on; attr save_button_widget enabled                  # expect: true (reachability)
tap_on float settingName 'Floating Launcher Button'       # tap 5
ui toggle-off; attr save_button_widget enabled                        # expect: false
back; ui back; has "$UNSAVED"; onset                                  # expect: 0, not in front
run_close
prefs R3-$k-after; for x in mic-input-source static-bssid enable-floating-button native-driver-selection-mode screen-orientation; do echo "$x=$(kv R3-$k-after $x)"; done
creates "$RUN"                                                        # expect: 1 (no recreate)
```

| Check | PASS | FAIL | Void |
|---|---|---|---|
| a. stored after the import | `mic-input-source=6`, `static-bssid=02:11:22:33:44:55` | any other value | |
| b. Save after the import, before any toggle | `false` | `true` | |
| c. Save after the first toggle | | | `false`, or `onset` false (the toggle did not register, or a system screen opened) |
| d. **Save after the second toggle** | **`false`** | **`true`** | |
| e. `has "$UNSAVED"` after BACK | 0, and `onset` false | 1 or more | |
| f. stored after the stop | `mic-input-source=6`, `static-bssid=02:11:22:33:44:55`, `enable-floating-button=false`, `native-driver-selection-mode=0`, `screen-orientation=0` | any other value | |

A run is PASS when a, b, d, e and f pass and c is not void. Report `creates "$RUN"` beside each run. A value above 1 means a recreate that nothing asked for; record it in Setup notes. Check c is the reachability pair for d: it proves that the screen counted the first toggle as a change, so a `false` at d means that the copy matched the stored values again.

### R6 Import of a backup equal to the stored values (3 taps per run)

The simplest path: the import changes nothing, so nothing recreates and nothing is pending. `$k` is 1 to 5.

```bash
run_open R6-$k "$KEYS_ADV"
open_settings 'Import settings'
tap_on import settingName 'Import settings'               # tap 1
ui import-dialog; has "$DISCARD_PENDING"                  # expect: 0
tap_on folder '*' 'Import from app backup folder'         # tap 2
tap_on backup '*' "$BKC" prefix                           # tap 3
sleep 3; onset; ui after-import; has "$BKC"; attr save_button_widget enabled   # expect: in front, 0, false
back; ui back; has "$UNSAVED"; onset                      # expect: 0, not in front
run_close
creates "$RUN"                                            # expect: 1
```

PASS per run needs:

- `has "$DISCARD_PENDING"` is 0;
- after the import, the screen is in front, the picker is gone (`has "$BKC"` is 0) and Save is `false`;
- after BACK, `has "$UNSAVED"` is 0 and `onset` is false.

A `has "$BKC"` above 0 after tap 3 voids the run (the import did not run).

### Closing

1. Run `adb -s $HU shell am force-stop $PKG`.
2. Restore `settings-backup-DHU.xml` with `hu_put "$BASEXML"` (no specs), and read it back.
3. Remove the three backup files from the folder you used.
4. Set `appops` `SYSTEM_ALERT_WINDOW` back to the value that Stage S read first, if it was not `allow`.
5. Turn airplane mode off on both phones. Turn on WiFi and Bluetooth with `svc`, and check both with `dumpsys` (template section 3: the airplane toggle does not always bring them back).

## 8. Do not re-run

Nothing is settled yet. Three of the diagnosis scenarios are left out on purpose:

- **R2 (the tab control on `main`), R4 (the driver fields) and R5 (the DPI result).** The JVM tests (`SettingsPendingCopyTest`, five cases) prove the field drift, and C removes the DPI handle, so H3 has no path. R3 seeds a driver mode that is not AUTO, so it also catches a drift of the driver fields.
- **A `main` control arm.** PC-1 is a positive control on C itself, with no second build.

## 9. Report back

- **R1:** dialogs on BACK after the import (check g), out of the graded runs, each with its `creates` count. Also the counts for checks b and c. Target: 0 of 3 for g and c, and Save `false` in 3 of 3 for b.
- **R3:** Save `enabled` after the second toggle (check d), out of the graded runs, and the stored `mic-input-source` and `static-bssid` after the stop. Target: `false` in 3 of 3, and backup B's values kept.
- **PC-1:** the dialog count. Target: 1 or more. Without that, the zeros above are INCONCLUSIVE.

Also report every line of `taps.log` (each line carries its run id), `runs.tsv`, `void.tsv`, the `stat` reading of `shared_prefs`, the folder that held the backups, and `OPEN_ROUTE` per run.
