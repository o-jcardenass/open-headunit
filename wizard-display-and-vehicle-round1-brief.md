# wizard-display-and-vehicle, round 1 brief

Published name on the transfer branch: `wizard-display-and-vehicle-round1-brief.md`. Results file: `wizard-display-and-vehicle-round1-results.md`. Evidence: create the release `rig-evidence-wizard-display-and-vehicle` with the asset `wizard-display-and-vehicle-round1-captures.zip` (first round of the thread).

This round tests the setup wizard on D-HU only. No phone takes part, because no run starts an Android Auto session. Estimated time: 25 min of runs, plus one build.

## 1. Build and baseline

- **Candidate C:** branch `fix/wizard-display-and-vehicle` on the fork, SHA **`3e4f7597`** (`3e4f7597e6a30f3bc492493f39d934111a15cbaf`), tree `4a1eb38f4b3b85fa2226b4389ff67d356fe18076`. One commit on `main` `71375a68` (`71375a689f704df1dc844e6a0656a58e471a12aa`). New branch, so no history was rewritten.
- **No baseline APK.** Each run carries its own control: a sentinel DPI that the old code keeps and the new code replaces (section 2).
- **JVM tests:** 3033, 0 failures, our run on tree `4a1eb38f`. `OnboardingDisplayPolicyTest` (9 tests) is new. Do not run them on the rig.

```bash
git ls-remote fork fix/wizard-display-and-vehicle   # MUST print 3e4f7597e6a30f3bc492493f39d934111a15cbaf
git fetch fork fix/wizard-display-and-vehicle
git worktree add ../ohu-wt-wizard-3e4f7597 3e4f7597e6a30f3bc492493f39d934111a15cbaf
git -C ../ohu-wt-wizard-3e4f7597 rev-parse 'HEAD^{tree}'   # MUST print 4a1eb38f4b3b85fa2226b4389ff67d356fe18076
```

Build with `build_hur.sh` and `HUR_DIR=../ohu-wt-wizard-3e4f7597`. Cool the host to 70C first and build with `--max-workers=2` (`rig-quirks/topics/tooling.md`). Copy the APK out of `apks/` at once, to `apks/candidate-3e4f7597.apk`. Copy `local.properties` into the worktree first if it has none. Kill the build's Gradle daemon after the build.

**Stop and escalate** if `git ls-remote` prints another SHA, or the tree differs.

### 1a. Identity gate

Install with `adb -s $HU install -r apks/candidate-3e4f7597.apk`, never an uninstall. Then pull the installed APK with `adb pull` and check:

```bash
for s in OnboardingDisplayPolicy display-size-preset; do
  printf '%s\t' "$s"; unzip -p installed.apk 'classes*.dex' | strings | grep -c -F "$s"
done                                    # each MUST print 1 or more
send ACTION_QUERY_STATE                 # the data= line MUST carry commit 3e4f7597
```

`main` has neither string, so a count of 0 means the wrong APK is installed.

## 2. What this is and why

The wizard's Display step asks for the screen size (4-6", 7-8", 9-10", 11"+). The size has one job: it sets the suggested DPI. A user reported that the size "does not change and does not save", and that it is "stuck on the smallest size". The code agrees, for three reasons:

1. Since the wizard got the DPI slider (July 2026), the slider takes its value once, when the wizard opens. A size tap did not move it. Next saves the slider's value, so the size never reached the saved DPI.
2. The size itself was never saved. Each launch guessed it again from the panel's reported `xdpi`.
3. A unit that reports a high density gets a small guess (4-6" or 7-8").

The candidate fixes all three. An untouched slider now follows a size or orientation tap. The size is saved as the new key `display-size-preset` and comes back on the next launch. A slider the user moved by hand keeps its value; the JVM tests cover that case, and this round does not (section 3).

The same commit adds a Car / Truck / Motorcycle choice to the wizard's car step. It writes `vehicle-type`, and it shows a note when `use-head-unit-microphone` is false, because the app then always sends Motorcycle.

**What a PASS would look like if the change did nothing:** the slider stays at the sentinel 300 after the size tap, `dpi-pixel-density` stays `300`, `display-size-preset` is absent, and the car step has no `onb_vehicle_type_group` node.

## 3. What is different this round

- **Injected taps drive the wizard.** No verb opens the wizard at a step or presses its buttons. Every tap is on a target found by resource id in a `uiautomator dump`, with exactly one match required (`target` in section 6). A target that does not resolve voids the run. Never guess a coordinate. List every tap from `taps.log` in Setup notes.
- **Tap budget: 5 per run, except R4, which the operator has allowed 10.** The car step is step 9 of the wizard, and the wizard always opens at step 0, so R4 needs 8 Next taps plus Truck plus Next.
- **No settings screen and no scrolling.** The wizard opens by itself from `MainActivity` while `onboarding-version` is below 2.
- **`onboarding-version` is 1 in every run**, so the wizard treats the unit as an upgrader. It then writes only the DPI and the size, and leaves resolution, codec, view mode and orientation alone. That is the reporter's path (a re-run of the wizard).
- **`connection-modes` is `self` only**, which hides the GPS step and keeps R4 at 10 taps. `wifi-connection-mode` is 1 (Headunit Server, NSD only), so the stack forms no group and pokes nobody.
- **Not tested on the rig, covered by JVM tests:** a slider the user moved keeps its value after a size tap (`a picker the user moved keeps its value`). Proving it on the rig needs 6 taps.
- **The DPI numbers depend on D-HU's panel.** Record `wm size`, `wm density` and the `xDpi`/`yDpi` from `dumpsys display` in Setup notes. Grade against the app's own "recommended" number on the DPI step, not against a table. For reference: on a panel that renders 1280x720, the four sizes give 240, 215, 161 and 129 dpi; on 1920x1080 they give 240, 240, 240 and 193.

## 4. Settings keys

Write them with the app stopped, from the round's backup, with `hu_put` (section 6). Every run starts from the same keys. `log-level` 2 (INFO) carries every app line this round reads: `SystemOptimizer: SoC=` is `AppLog.i`, `AutomationReceiver:` is `AppLog.i`, `AutomationMarker:` is `AppLog.w`. `ACTION_LOG_MARKER` is not in `AutomationCommandPolicy.CONFIGURING` on C, so `allow-external-configuration` is not needed.

| Key | Type | R1, R2 | R3 | R4 | Why |
|---|---|---|---|---|---|
| `onboarding-version` | int | `1` | (left from R2) | `1` | wizard opens, upgrader path |
| `has-accepted-disclaimer` | boolean | `true` | | `true` | Next is enabled on the Safety step |
| `connection-modes` | string set | `self` | | `self` | hides the GPS step |
| `wifi-connection-mode` | int | `1` | | `1` | NSD only: no group, no poke |
| `native-poke-all-paired` | boolean | `false` | | `false` | a quiet unit |
| `native-driver-selection-mode` | int | `0` | | `0` | no driver selector on launch |
| `log-level` | int | `2` | | `2` | INFO |
| `dpi-pixel-density` | int | **`300`** | | `300` | the sentinel: no size gives 300 (the cap is 240) |
| `display-size-preset` | | delete | | delete | no stored size |
| `use-head-unit-microphone` | boolean | | | **`false`** | R4: the forced-Motorcycle note must show |
| `vehicle-type` | | | | delete | R4: starts as Car |

```bash
KEYS="int:onboarding-version=1 bool:has-accepted-disclaimer=true set:connection-modes=self int:wifi-connection-mode=1 bool:native-poke-all-paired=false int:native-driver-selection-mode=0 int:log-level=2 int:dpi-pixel-density=300 del:display-size-preset"
R4KEYS="$KEYS bool:use-head-unit-microphone=false del:vehicle-type"
```

Check `ohu_setkeys.py` first: `grep -c 'if v else \[\]' ohu_setkeys.py` must print 1.

## 5. Every action

| Action | Command | Notes |
|---|---|---|
| Launch the app (the wizard opens on top) | `adb -s $HU shell am start -n $MAIN` | not a verb; `MainActivity.checkSetupFlow` starts the wizard |
| Stop the app | `adb -s $HU shell am force-stop $PKG` | the wizard writes with `apply()` and `commit()` before this, so the file is current |
| Mark a step | `mark <label>` | `AutomationMarker: <label>` at WARN |
| Read the build | `send ACTION_QUERY_STATE` | `commit` on the `data=` line |
| Next, a size, Truck | `tapid <id>` | one injected tap on a dumped target (section 3) |

Every `send` must print `AutomationReceiver: <action>` in the capture. A step with no such line never landed, and its run is void, not a FAIL.

## 6. The lines and nodes that decide every run

All verified with `grep -F` against `3e4f7597`. The UI checks read the dump file `$OUT/ui.xml` taken at the step named.

| What | Where | Exact string or node |
|---|---|---|
| The wizard bound its steps | capture, from the launch to the run's end marker | `SystemOptimizer: SoC=` |
| The wizard is in front | `dumpsys activity activities` | `OnboardingActivity` on the `ResumedActivity` line |
| The Display step is showing | `ui.xml` | node with resource-id ending `:id/onb_size_group` |
| Which size is chosen | `ui.xml` | the one `checked="true"` node among `:id/onb_size_phone`, `onb_size_small`, `onb_size_standard`, `onb_size_large` |
| The slider's value | `ui.xml` on the DPI step | `text` of `:id/dpi_input` |
| The recommended value | `ui.xml` on the DPI step | the only number in the `text` of `:id/onb_dpi_recommended` (locale independent) |
| The car step is showing | `ui.xml` | node `:id/onb_vehicle_type_group` |
| The microphone note | `ui.xml` on the car step | node `:id/onb_vehicle_type_forced` present (a hidden view is absent from a dump) |
| Saved values | `settings.xml` after `force-stop` | `dpi-pixel-density`, `display-size-preset`, `vehicle-type` |

Save this as `hur-wifi-test-scripts/wizard-display-and-vehicle-round1/libwizard1.sh`, next to a copy of `ohu_setkeys.py` from `../pr-1047-session-reconnect-round3/`, and copy `ptr_lib.sh` from the same folder for `th_gate`. Run under `flock /tmp/ohu-rig.lock`. One adb call at a time (house rule 8).

```bash
HU=27870808938846; PKG=com.andrerinas.headunitrevived
MAIN=$PKG/com.andrerinas.openheadunit.main.MainActivity
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
OUT=${OUT:-$PWD/out}; mkdir -p "$OUT"
send() { a=$1; shift; adb -s $HU shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; }
mark() { send ACTION_LOG_MARKER --es text "$1" >/dev/null; }
hu_put() { python3 ohu_setkeys.py "$1" new.xml "${@:2}" || return 1
  OWN=$(adb -s "$HU" shell "su -c 'stat -c %u:%g /data/data/$PKG'" | tr -d '\r')
  printf 'cp /data/local/tmp/new.xml /data/data/%s/shared_prefs/settings.xml\nchown %s /data/data/%s/shared_prefs/settings.xml\nchmod 660 /data/data/%s/shared_prefs/settings.xml\n' "$PKG" "$OWN" "$PKG" "$PKG" > hu_put.sh
  adb -s "$HU" push new.xml /data/local/tmp/new.xml >/dev/null; adb -s "$HU" push hu_put.sh /data/local/tmp/hu_put.sh >/dev/null
  adb -s "$HU" shell "su -c 'sh /data/local/tmp/hu_put.sh'"; }
prefs() { adb -s $HU shell "su -c 'cat /data/data/$PKG/shared_prefs/settings.xml'" | grep -aoE '(dpi-pixel-density|display-size-preset|vehicle-type|onboarding-version|use-head-unit-microphone|connection-modes)[^/]*'; }
cap_start() { adb -s $HU logcat -c; stdbuf -oL adb -s $HU logcat -v time > "$OUT/$1.txt" & CAP=$!; }
cap_stop() { kill $CAP 2>/dev/null; wait $CAP 2>/dev/null; }
ui_dump() { sleep 0.3; adb -s "$HU" shell uiautomator dump /sdcard/ohu-ui.xml >/dev/null 2>&1; adb -s "$HU" exec-out cat /sdcard/ohu-ui.xml > "$OUT/ui.xml"; cp "$OUT/ui.xml" "$OUT/$RUN-ui-$1.xml"; [ -s "$OUT/ui.xml" ]; }
# uinode <id> [attr] : prints the attr (default "bounds") of the one node whose resource-id ends ":id/<id>"; fails on 0 or 2+
uinode() { python3 -I -c '
import sys,xml.etree.ElementTree as ET
n=[e for e in ET.parse(sys.argv[1]).iter("node") if e.get("resource-id","").endswith(":id/"+sys.argv[2])]
if len(n)!=1: sys.exit("matches=%d"%len(n))
print(n[0].get(sys.argv[3] if len(sys.argv)>3 else "bounds"))' "$OUT/ui.xml" "$@"; }
has() { uinode "$1" >/dev/null 2>&1; }
checked_size() { for s in phone small standard large; do [ "$(uinode onb_size_$s checked 2>/dev/null)" = true ] && echo $s; done; }
num() { uinode "$1" text | grep -oE '[0-9]+' | head -1; }
# tapid <id> : one injected tap on a fresh dump's target, budget $BUDGET per run (5 unless the run says)
tapid() { local b xy; [ "${TAPS:-0}" -ge "${BUDGET:-5}" ] && { echo "$(date +%T) $RUN REFUSED tap $((TAPS+1)) ($1)" >> "$OUT/taps.log"; return 1; }
  ui_dump "pre-$1-$TAPS" && b=$(uinode "$1") || { echo "$(date +%T) $RUN NO_TARGET $1" >> "$OUT/taps.log"; return 1; }
  [ "$(uinode "$1" enabled)" = true ] || { echo "$(date +%T) $RUN DISABLED $1" >> "$OUT/taps.log"; return 1; }
  xy=$(echo "$b" | grep -oE '[0-9]+' | paste -sd' ' | awk '{print int(($1+$3)/2), int(($2+$4)/2)}')
  TAPS=$((TAPS+1)); echo "$(date +%T) $RUN tap$TAPS $1 $xy" >> "$OUT/taps.log"; adb -s $HU shell input tap $xy; sleep 1.2; }
# r_open <run> <keys...> : stop, write keys, start capture, launch, wait for the wizard
r_open() { RUN=$1; TAPS=0; th_gate || return 3; adb -s $HU shell am force-stop $PKG; sleep 1
  [ -n "$2" ] && { hu_put settings-backup-HU.xml "${@:2}" || return 2; }
  prefs > "$OUT/$RUN-prefs-before.txt"; cap_start $RUN; mark $RUN-start
  adb -s $HU shell am start -n $MAIN >/dev/null; sleep 6
  adb -s $HU shell dumpsys activity activities | grep -a "ResumedActivity" | tee "$OUT/$RUN-resumed.txt" | grep -q OnboardingActivity; }
r_close() { mark $RUN-end; sleep 1; adb -s $HU shell am force-stop $PKG; sleep 1; cap_stop; prefs | tee "$OUT/$RUN-prefs-after.txt"
  echo "$RUN optimizer_lines=$(grep -ac 'SystemOptimizer: SoC=' "$OUT/$RUN.txt") receiver=$(grep -ac 'AutomationReceiver: ' "$OUT/$RUN.txt") fatal=$(grep -ac 'FATAL EXCEPTION' "$OUT/$RUN.txt")" | tee -a "$OUT/summary.txt"; }
# to_display : Next from Welcome, Safety, Connection (3 taps), then assert the Display step
to_display() { tapid onb_next && tapid onb_next && tapid onb_next && ui_dump display && has onb_size_group; }
# The Display step must show exactly one chosen size, or the dump cannot read the toggle state
one_size() { [ "$(checked_size | wc -l)" -eq 1 ] || { adb -s $HU shell screencap -p /sdcard/s.png; adb -s $HU pull /sdcard/s.png "$OUT/$RUN-display.png" >/dev/null; echo "$RUN NO_CHECKED_STATE" | tee -a "$OUT/summary.txt"; return 1; }; }
```

## 7. Runs

**The point of the round is R1.** If `one_size` fails in R1 (the dump carries no `checked="true"` on any size button), R1 to R3 cannot be graded from the dump: mark them INCONCLUSIVE, attach the screenshot, and go on to R4. Every run is preflighted: `r_open` must return 0 (the wizard is the resumed activity), the capture must hold 1 or more `SystemOptimizer: SoC=` lines, and `$RUN-prefs-before.txt` must show the run's keys. If a preflight fails, the run is a setup failure: fix it and re-run once, and never grade it. Write the results file after every run. Discard rule for every run: a `FATAL EXCEPTION` in the capture makes the run a FAIL, quote it.

**Prepare (once).** Back up `settings.xml` to `settings-backup-HU.xml` (template section 1) and diff it against the previous round's backup; state the delta in Setup notes. Record `wm size`, `wm density` and `adb -s $HU shell dumpsys display | grep -aoE '[xy]Dpi=[0-9.]+' | sort -u`.

### R1 The size tap moves the saved DPI (the point)

```bash
r_open R1 $KEYS || echo SETUP_FAIL
to_display && one_size || echo VOID_NO_DISPLAY
EST=$(checked_size); echo "R1 estimate=$EST" | tee -a $OUT/summary.txt
T1=small; [ "$EST" = small ] && T1=standard
tapid onb_size_$T1; ui_dump after-size; echo "R1 checked_after=$(checked_size)" | tee -a $OUT/summary.txt
tapid onb_next; ui_dump dpi
echo "R1 picker=$(num dpi_input) recommended=$(num onb_dpi_recommended)" | tee -a $OUT/summary.txt
r_close
```

- **PASS:** `checked_after` equals `$T1`; `picker` equals `recommended`; `picker` is not 300; after the stop, `dpi-pixel-density` equals `picker` and `display-size-preset` is `SMALL_7_8` (or `STANDARD_9_10` when `$T1` is `standard`); 5 taps; `fatal` 0.
- **FAIL:** `picker` is 300, or `dpi-pixel-density` is 300, or `display-size-preset` is absent.
- **Report:** `EST`, `T1`, `picker`, `recommended`, the saved values. Time: 3 min.

### R2 A second size gives its own DPI

Same setup. The target is `large`, or `phone` when the estimate is `large`.

```bash
r_open R2 $KEYS || echo SETUP_FAIL
to_display && one_size || echo VOID_NO_DISPLAY
EST=$(checked_size); T2=large; [ "$EST" = large ] && T2=phone
tapid onb_size_$T2; tapid onb_next; ui_dump dpi
echo "R2 estimate=$EST target=$T2 picker=$(num dpi_input) recommended=$(num onb_dpi_recommended)" | tee -a $OUT/summary.txt
r_close
```

- **PASS:** `picker` equals `recommended`, and is not 300; `dpi-pixel-density` equals `picker`; `display-size-preset` is `LARGE_11_PLUS` (or `PHONE_4_6`); and when R2's `recommended` differs from R1's, the saved DPIs differ too.
- **FAIL:** any of those not met.
- **Report:** R1 and R2 `recommended` side by side. If they are equal, say so: on a 1080p panel the first three sizes all reach the 240 cap. Time: 3 min.

### R3 The size comes back on the next launch

Run straight after R2. **Do not write keys and do not restore the backup**: R3 reads what R2 saved. R2 did not finish the wizard, so `onboarding-version` is still 1.

```bash
r_open R3 || echo SETUP_FAIL          # no keys: R2's file as it is
to_display || echo VOID_NO_DISPLAY
echo "R3 checked=$(checked_size)" | tee -a $OUT/summary.txt
tapid onb_next; ui_dump dpi
echo "R3 picker=$(num dpi_input)" | tee -a $OUT/summary.txt
r_close
```

- **PASS:** `checked` equals R2's target (`large`, or `phone`), not R2's estimate; `picker` equals R2's saved `dpi-pixel-density` (no size was tapped, so the slider keeps the saved value); after the stop, `display-size-preset` is unchanged; 4 taps.
- **FAIL:** `checked` equals R2's estimate, or `picker` differs from R2's saved DPI.
- Time: 2 min.

### R4 The car step offers the vehicle type (10 taps, operator-approved)

```bash
r_open R4 $R4KEYS || echo SETUP_FAIL
BUDGET=10
for i in 1 2 3 4 5 6 7 8; do ui_dump step$i; has onb_vehicle_type_group && break; tapid onb_next || break; done
ui_dump car; has onb_vehicle_type_group || echo VOID_NO_CAR_STEP
echo "R4 car_checked=$(uinode onb_vehicle_type_car checked) note=$(has onb_vehicle_type_forced && echo shown || echo absent)" | tee -a $OUT/summary.txt
tapid onb_vehicle_type_truck; ui_dump after-truck
echo "R4 truck_checked=$(uinode onb_vehicle_type_truck checked)" | tee -a $OUT/summary.txt
tapid onb_next
r_close
```

- **PASS:** the car step is reached with 8 Next taps; `car_checked` is `true` before the tap (no stored type reads as Car); `note` is `shown` (`use-head-unit-microphone` is false); `truck_checked` is `true`; after the stop, `vehicle-type` is `2`; 10 taps or fewer.
- **FAIL:** no `onb_vehicle_type_group` on the car step, or `note` absent, or `vehicle-type` not `2`.
- **INCONCLUSIVE:** the car step needs more than 8 Next taps (a hidden step showed). Report which step appeared, from the `step<i>` dumps.
- Time: 4 min.

### Closing

1. `adb -s $HU shell am force-stop $PKG`.
2. Restore `settings-backup-HU.xml` with `hu_put settings-backup-HU.xml` and no keys (or push it as root, as `hu_put` does). Read it back. The diff against the backup must be empty.
3. `pgrep -fc "^adb .*logcat"` must print 0.

### Stop rules

- A run voided by `tapid` (`NO_TARGET`, `DISABLED` or `REFUSED` in `taps.log`) is re-run once. A second void makes it INCONCLUSIVE.
- If R1 FAILs, still run R2 to R4: they answer separate questions.
- The host thermal rules of `rig-quirks/topics/tooling.md` apply through `th_gate`.

## 8. Do not re-run

- **The vehicle type on the wire.** `headunit-info-round3` measured that `vehicle-type` reaches the phone's `CarInfoInternal` and how the phone files each type. This commit only adds a second place that writes the key, so R4 grades `settings.xml` and starts no session.

## 9. Report back

1. R1: `picker` against 300, and the saved `dpi-pixel-density` and `display-size-preset`.
2. R3: the size shown on relaunch against R2's target and estimate.
3. R4: `vehicle-type` after the Truck tap, and whether the note showed.
