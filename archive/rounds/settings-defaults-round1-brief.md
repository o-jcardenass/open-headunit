# settings-defaults: round 1 brief

## 1. Build and baseline

| Arm | Ref | SHA | Get it |
|---|---|---|---|
| A (baseline) | `main` | `f749b84c` | `git fetch fork && git checkout f749b84c` |
| B (candidate) | `fix/settings-defaults` | `9e32cf47` | `git fetch fork && git checkout 9e32cf47` |

The candidate is two commits on top of A: `9ccadb7f` (floating button) and `9e32cf47` (settings: the Save rule, the Exit default and the old Bluetooth address). History was rewritten before the round to fold three settings commits into one; nothing ran on the old SHAs. The branch was `fix/floating-button-touch` until this round and this brief was first pushed as `floating-button-touch-round1-brief.md` naming `9ccadb7f` only; it now covers the whole branch. No run of the old brief took place.

**Build gate (R0).** Build both with `build_hur.sh` and copy each APK out of `apks/` as soon as it is built (`build_hur.sh` deletes the previous APK). Then run `run_unit_tests.sh` on B.

- PASS: B has 2836 tests and 0 failures, counted from the JUnit XML, including `FloatingButtonOpacityPolicyTest` (9), `FloatingButtonPolicyTest` (5), `AaExitActionPolicyTest` (4) and `BluetoothAddressSeedPolicyTest` (4).
- PASS: the two APK md5s differ.
- PASS: `unzip -p <B.apk> 'classes*.dex' | strings | grep -cF windowAlpha` is 1 or more, and the same grep on A's APK is 0. `windowAlpha` is a method that B adds and A does not have. Also run `unzip -p <apk> 'classes*.dex' | strings | grep -cF 40:EF:4C:A3:CB:A5`: B is 1 or more and A is 0, because only B carries the old address as a constant.
- A build or test failure stops the round.

Install with `adb install -r <named apk>` only, and check the md5 of the live APK before every run (`apk_check` below).

## 2. What this is and why

A merged change added a "Connection Status Indicator" mode to the floating launcher button. With it on, the button uses a "Disconnected Opacity" (slider 0 to 100%, step 5) when no phone is connected. At 0% the change set `FLAG_NOT_TOUCHABLE` on the window and set the opacity on the **view** (`View.alpha`). The window itself stayed fully opaque (`LayoutParams.alpha` = 1.0).

From Android 12 (API 31), the input system blocks a touch that passes through another app's overlay window unless one of these is true: the window alpha is 0, the window is hidden, or the window alpha is at most 0.8. A view alpha does not count. So we expect that on API 31+, a tap on the invisible button reaches neither the button nor the app under it. Android drops it and logs an untrusted-touch line. This comes from reading the platform rules and is **not measured**. R1 measures it.

The candidate does two things:

- Below a 10% floor the button is not touchable, so 5% also lets taps through. Before, only exactly 0% did.
- Below the floor the opacity goes on the window (`LayoutParams.alpha`) and the view stays at 1.0. At or above the floor nothing changes: window alpha 1.0, view alpha animated.

### The Exit default (`9e32cf47`)

When the user taps Exit in the Android Auto app drawer, the phone sends a video focus request with mode `VIDEO_FOCUS_NATIVE`. Up to 3.3.1 the head unit ended the session on it. PR #954 (in A) added the setting "Android Auto Exit Action" with **Return to OEM Launcher (Minimize)** as the default, so on A the projection goes to the home screen and the session stays up. B makes **Disconnect Session** the default again. A stored value is kept: a user with `aa-exit-action` = `0` on disk still minimizes. There is no migration.

### The Save rule (`9e32cf47`)

Each settings screen loads every value into a pending field. On A, Save writes every one of them back, so one changed row puts every default on disk (a "frozen default"). B writes only the values that differ from what is stored. Prediction from the code, **not measured**: on A one Save adds many keys; on B it adds only the changed one.

### The old Bluetooth address (`9e32cf47`)

Up to v.1.14.2 the `bt-address` default was `40:EF:4C:A3:CB:A5`, one developer's own address, and a Save kept it on disk. B reads that value as empty, so `AapService.onCreate` fills in the detected address.

## 3. What is different this round

- **The tap is a hand step, by the operator's choice.** No verb can tap another app, and `input tap` is excluded for this round. The executor arms a touch recorder (`getevent`) and asks the operator to tap once at a given point. The executor waits until the recorder sees the touch. The verdict is graded from the logs, never from what the operator saw.
- **The app under the button is Android's Settings app.** A tap that reaches a Settings row starts a sub-screen, and the framework logs that as `START u0 ... cmp=com.android.settings/...`. That is the scripted evidence that the tap passed through.
- **The floating button is off by default.** Every run turns it on in `settings.xml`.
- **The button only shows while Open Headunit is in the background.** Each run launches `MainActivity`, then opens Settings over it. That backgrounds the app and starts `FloatingButtonService`.
- **R1 expects the defect.** Its PASS means "the defect reproduced". If D-HU's `block_untrusted_touches` is not `2` (or null, which means 2), R1 is INCONCLUSIVE, not a FAIL.
- **The Exit tap is a hand step.** It is a tap into the projected video, which §3 allows, but the Exit tile moves between Android Auto versions, so the operator finds and taps it. The executor waits until the head unit logs the focus request.
- **S1 and S2 change a setting in the app's UI on purpose.** The Save button is the control under test and no verb reaches it, so the operator flips one switch and taps Save. Every other setting in the round still goes through `settings.xml`.
- **S1 expects the defect** (A writes the untouched keys). If S1 writes none of them, S2's PASS proves nothing; mark S2 INCONCLUSIVE.
- **Back up `settings.xml` before C0** with `pdump round-start` and restore it after S2. The S runs delete keys.
- **A is never run on D-SAM.** API 19 has no untrusted-touch blocking, so A and B are expected to behave the same there.

## 4. Settings keys

Write them with the app stopped. On D-HU use the template's §1 write method (not `set_hu_prefs.sh`, which relaunches the app). On D-SAM use `set_prefs_runas_host.py`, because D-SAM has no `sed`. Read every key back before launch.

| Key | Type | Value |
|---|---|---|
| `enable-floating-button` | boolean | `true` |
| `floating-button-connection-status-mode` | boolean | per run |
| `floating-button-disconnected-opacity-percent` | int | per run |
| `floating-button-opacity-percent` | int | `80` |
| `floating-button-size-dp` | int | `96` |
| `floating-button-x-percent` | int | `50` (C0 may change it) |
| `floating-button-y-percent` | int | `50` (C0 may change it) |
| `onboarding-version` | int | `2` |
| `native-driver-selection-mode` | int | `0` |
| `native-poke-all-paired` | boolean | `false` (all runs except R6) |

The overlay permission is not in `settings.xml`. On D-HU, before R0 and again before every run:

```bash
adb -s $HU shell appops set $PKG SYSTEM_ALERT_WINDOW allow
adb -s $HU shell appops get $PKG SYSTEM_ALERT_WINDOW     # must print allow
adb -s $HU shell settings get global block_untrusted_touches   # record it; null or 2 = block
```

`ACTION_LOG_MARKER` is not gated on either build, so `allow-external-configuration` is not needed.

The E and S runs add these. "delete" means the template's §1 delete half only, so the key reads as its default.

| Run | Key | Type | Value |
|---|---|---|---|
| E1, E2 | `aa-exit-action` | int | delete |
| E3 | `aa-exit-action` | int | `0` |
| E3 | `bt-address` | string | `40:EF:4C:A3:CB:A5` |
| S0, S1, S2 | `hud_mirroring`, `hide-clock`, `hide-battery-level`, `fps-limit`, `aa-exit-action`, `show-toast-messages` | | delete all six |
| E1 to E3 | `enable-floating-button` | boolean | `false` |

The E runs otherwise use the rig's Native AA baseline, as R6 does (do **not** write `native-poke-all-paired=false`).

## 5. The helper, `fbt_lib.sh`

Put this in `hur-wifi-test-scripts/` as `sd_lib.sh` and source it. It needs `HU`, `OUT` and `RUN` set. The thermal helpers are copied verbatim from `projection-teardown-and-relays-round1-brief.md`.

```bash
# sd_lib.sh : settings-defaults round 1
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
MAIN=$PKG/com.andrerinas.openheadunit.main.MainActivity
send() { a=$1; shift; adb -s "$HU" shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; }
mark() { send ACTION_LOG_MARKER --es text "$1" >/dev/null; sleep 0.3; }
CAP() { echo "$OUT/$RUN.hu.txt"; }
# Host thermal (verbatim from ptr_lib.sh)
th_pkg() { if [ -x ../rig_thermal.sh ]; then ../rig_thermal.sh status | grep -o 'pkg=[0-9]*' | cut -d= -f2; return; fi
  for z in /sys/class/hwmon/hwmon*; do [ "$(cat $z/name 2>/dev/null)" = coretemp ] && { echo $(( $(cat $z/temp1_input) / 1000 )); return; }; done
  echo $(( $(cat /sys/class/thermal/thermal_zone0/temp) / 1000 )); }
th_thr() { cat /sys/devices/system/cpu/cpu0/thermal_throttle/package_throttle_count 2>/dev/null || echo 0; }
th_wait() { local lim=${1:-75} n=0; while [ "$(th_pkg)" -ge "$lim" ]; do n=$((n+1)); [ $n -ge 90 ] && return 1; sleep 10; done; }
th_watch() { while :; do echo "$(date +%T) pkg=$(th_pkg)C throttle_pkg=$(th_thr)"; sleep 20; done >> "$1"; }
th_gate() { th_wait 75 || { echo "$(date +%T) HOST_TOO_HOT ${RUN:-run}" | tee -a "$OUT/thermal.log"; return 3; }
  [ -n "$THPID" ] && kill $THPID 2>/dev/null; th_watch "$OUT/${RUN:-run}.thermal" & THPID=$!
  echo "$(date +%T) ${RUN:-run} start pkg=$(th_pkg)C throttle_pkg=$(th_thr)" >> "$OUT/thermal.log"; }
# apk_check : pulls the live APK and compares its md5 with $WANT_MD5
apk_check() { th_gate || return 3; local m; adb -s "$HU" pull $(adb -s "$HU" shell pm path $PKG | cut -d: -f2 | tr -d '\r') "$OUT/live.apk" >/dev/null 2>&1
  m=$(md5sum "$OUT/live.apk" | cut -d' ' -f1); echo "$(date +%T) $HU live=$m want=$WANT_MD5" | tee -a "$OUT/apk-check.log"
  [ "$m" = "$WANT_MD5" ] || { echo "APK_MISMATCH $HU" | tee -a "$OUT/apk-check.log"; return 1; }; }
# Capture: full on API 21+, tag-filtered with restart below it (D-SAM)
TAGS="OPENHU:V ActivityManager:I InputDispatcher:V libc:V DEBUG:V AndroidRuntime:V *:S"
cap_start() { local api; api=$(adb -s "$HU" shell getprop ro.build.version.sdk | tr -d '\r'); adb -s "$HU" logcat -c; : > "$(CAP)"
  if [ "$api" -ge 21 ]; then adb -s "$HU" logcat -G 16M; stdbuf -oL adb -s "$HU" logcat -v time > "$(CAP)" & CAPPID=$!; CAPLOOP=
  else ( while :; do stdbuf -oL adb -s "$HU" logcat -v time $TAGS >> "$(CAP)"; sleep 1; done ) & CAPLOOP=$!; CAPPID=$CAPLOOP; fi; }
cap_stop() { if [ -n "$CAPLOOP" ]; then kill $CAPLOOP 2>/dev/null; pkill -P $CAPLOOP 2>/dev/null; wait $CAPLOOP 2>/dev/null
    awk '!seen[$0]++' "$(CAP)" > "$(CAP).tmp" && mv "$(CAP).tmp" "$(CAP)"
  else kill $CAPPID 2>/dev/null; wait $CAPPID 2>/dev/null; fi; }
# Background the app: launch MainActivity, wait, then open Settings over it
bring_up() { adb -s "$HU" shell am start -n $MAIN >/dev/null; sleep 15; adb -s "$HU" shell am start -a android.settings.SETTINGS >/dev/null; sleep 5; }
front() { adb -s "$HU" shell dumpsys activity activities | grep -aE 'topResumedActivity|mResumedActivity' | head -1; }
# win <label> : saves our overlay window's dumpsys block, prints its attrs and flags
win() { adb -s "$HU" shell dumpsys window windows > "$OUT/$RUN.$1.window.txt"
  awk -v pkg="package=$PKG" '/Window #[0-9]+ Window\{/{ if (b ~ pkg && b ~ /SYSTEM_ALERT_WINDOW/) print b; b="" } { b = b "\n" $0 }
       END { if (b ~ pkg && b ~ /SYSTEM_ALERT_WINDOW/) print b }' "$OUT/$RUN.$1.window.txt" > "$OUT/$RUN.$1.overlay.txt"
  grep -aoE 'mAttrs=\{\([0-9-]+,[0-9-]+\)\([0-9]+x[0-9]+\)|alpha=[0-9.]+|fl=[A-Z_ #0-9a-f]+' "$OUT/$RUN.$1.overlay.txt" | head -6; }
shot() { adb -s "$HU" shell screencap -p /sdcard/fbt.png; sleep 0.3; adb -s "$HU" pull /sdcard/fbt.png "$OUT/$RUN.$1.png" >/dev/null; }
# hand_tap <label> : arm the touch recorder, ask the operator, wait for one touch (max 180 s)
hand_tap() { local ge="$OUT/$RUN.$1.getevent"; : > "$ge"
  adb -s "$HU" shell getevent -lt > "$ge" & local gp=$!; sleep 1; mark "$RUN-$1-ready"
  echo ">>> OPERATOR: tap ONCE at x=$TAPX y=$TAPY on $HU now."
  local n=0; until grep -aqE 'BTN_TOUCH +DOWN|ABS_MT_TRACKING_ID +0' "$ge"; do n=$((n+1)); [ $n -ge 900 ] && break; sleep 0.2; done
  sleep 3; mark "$RUN-$1-done"; kill $gp 2>/dev/null; adb -s "$HU" shell pkill getevent 2>/dev/null
  echo "touch_downs=$(grep -acE 'BTN_TOUCH +DOWN|ABS_MT_TRACKING_ID +0' "$ge")"; }
# seg <from> <to> : capture lines between two markers
seg() { sed -n "/AutomationMarker: $1\$/,/AutomationMarker: $2\$/p" "$(CAP)"; }
# grade_tap <label> : the four counts every tap is graded on
grade_tap() { local s; s=$(seg "$RUN-$1-ready" "$RUN-$1-done")
  echo "settings_start=$(echo "$s" | grep -acE 'START u0 .*cmp=com\.android\.settings/')"
  echo "ohu_start=$(echo "$s" | grep -acE 'START u0 .*cmp=com\.andrerinas\.headunitrevived/com\.andrerinas\.openheadunit\.main\.MainActivity')"
  echo "untrusted=$(echo "$s" | grep -aic 'untrusted touch')"; }
# Settings file: pdump <label> saves it, pget <key> prints one key or "<key> ABSENT"
pdump() { adb -s "$HU" shell run-as $PKG cat shared_prefs/settings.xml > "$OUT/$RUN.$1.settings.xml"; }
pget() { adb -s "$HU" shell run-as $PKG cat shared_prefs/settings.xml | grep -oE "name=\"$1\"( value=\"[^\"]*\"|>[^<]*)" || echo "$1 ABSENT"; }
# Phone (D-POCO): capture, and a marker on both units
PH=4f4027e9
pcap_start() { adb -s $PH logcat -c; stdbuf -oL adb -s $PH logcat -v time > "$OUT/$RUN.phone.txt" & PCAP=$!; }
pcap_stop() { kill $PCAP 2>/dev/null; wait $PCAP 2>/dev/null; }
pmark() { mark "$1"; adb -s $PH shell log -t AutomationMarker "$1"; }
pseg() { sed -n "/AutomationMarker.*: $1\$/,/AutomationMarker.*: $2\$/p" "$OUT/$RUN.phone.txt"; }
# hand_exit : ask the operator to tap Exit in Android Auto, wait for the focus request (max 180 s)
hand_exit() { pmark "$RUN-exit-ready"
  echo ">>> OPERATOR: on $HU, open the Android Auto app drawer and tap Exit, once."
  local n=0; until sed -n "/AutomationMarker: $RUN-exit-ready\$/,\$p" "$(CAP)" | grep -aqF 'Video Focus NATIVE received'; do
    n=$((n+1)); [ $n -ge 900 ] && break; sleep 0.2; done
  sleep 20; front > "$OUT/$RUN.front-after-exit.txt"; pmark "$RUN-exit-done"; }
# grade_exit : the counts every E run is graded on
grade_exit() { local s; s=$(seg "$RUN-exit-ready" "$RUN-exit-done")
  echo "throughput_before=$(seg "$RUN-start" "$RUN-exit-ready" | grep -acF 'Throughput over')"
  echo "native_focus=$(echo "$s" | grep -acF 'Video Focus NATIVE received. User clicked Exit in Android Auto.')"
  echo "minimize=$(echo "$s" | grep -acF 'ExitAction: Minimizing projection to OEM Launcher')"
  echo "disconnect=$(echo "$s" | grep -acF 'ExitAction: Disconnecting projection session')"
  echo "user_exit=$(echo "$s" | grep -acF 'AapService: Native AA user exit. Stopping active launcher.')"
  echo "throughput_after=$(echo "$s" | grep -acF 'Throughput over')"
  echo "phone_aa_before=$(pseg "$RUN-start" "$RUN-exit-ready" | grep -acE ' (GH|CAR)\.')"
  echo "phone_byebye=$(pseg "$RUN-exit-ready" "$RUN-exit-done" | grep -aic byebye)"
  echo "front_after=$(cat "$OUT/$RUN.front-after-exit.txt")"; }
# hand_save : open Settings on the toast row, ask the operator to flip it and Save (max 180 s)
SETTINGS=$PKG/com.andrerinas.openheadunit.main.SettingsActivity
hand_save() { adb -s "$HU" shell am start -n $SETTINGS --ei extra_destination 0 --es extra_search_query "Show Toast" >/dev/null; sleep 5; shot settings
  mark "$RUN-save-ready"; echo ">>> OPERATOR: on $HU, flip the 'Show Toast Messages' switch once, then tap Save."
  local n=0; until pget show-toast-messages | grep -q 'value="false"'; do n=$((n+1)); [ $n -ge 90 ] && break; sleep 2; done
  sleep 3; mark "$RUN-save-done"; }
# frozen : how many of the five untouched keys are on disk now
frozen() { local c=0 k; for k in hud_mirroring hide-clock hide-battery-level fps-limit aa-exit-action; do pget $k | grep -q ABSENT || c=$((c+1)); done; echo "frozen=$c"; }
```

**Where to tap (`TAPX`, `TAPY`).** Take them from `win`'s `mAttrs={(x,y)(WxH)` line: `TAPX = x + W/2`, `TAPY = y + H/2`. These are screen coordinates. Say them to the operator together with the C0 screenshot, which shows the visible button at that spot. On D-SAM, check the coordinates against a `uiautomator dump` (see `D-SAM-and-D-HP.md`).

**Decoding `fl=` on D-SAM.** API 19 prints the flags as hex (`fl=#...`). `FLAG_NOT_TOUCHABLE` is bit `0x10`: `python3 -c "print(bool(int('<hex>',16) & 0x10))"`. API 31+ prints the names, so look for `NOT_TOUCHABLE`. A missing `alpha=` token means the window alpha is 1.0.

## 6. The deciding lines

Every line from the app was checked with `grep -F` against `9ccadb7f`. Every framework line is a pattern, because it comes from Android, not from this tree.

| What | Line or pattern | Where | Level |
|---|---|---|---|
| Button shown | `FloatingButtonService: Added floating button overlay` | HU capture | INFO |
| Add failed | `FloatingButtonService: Failed to add overlay view` | HU capture | WARN |
| Update failed | `FloatingButtonService: Failed to update overlay layout` | HU capture | WARN |
| Button removed | `FloatingButtonService: Removed floating button overlay` | HU capture | INFO |
| Tap reached Settings | `START u0 .*cmp=com.android.settings/` | HU capture, tap window | framework, INFO |
| Tap reached the button | `START u0 .*cmp=com.andrerinas.headunitrevived/com.andrerinas.openheadunit.main.MainActivity` | HU capture, tap window | framework, INFO |
| Tap dropped | `untrusted touch`, case-insensitive (`InputDispatcher: Dropping untrusted touch event due to ...`) | HU capture, tap window | framework, WARN |
| Tap happened | `BTN_TOUCH +DOWN` or `ABS_MT_TRACKING_ID +0...` | `$RUN.<label>.getevent` | n/a |
| Projection | `Throughput over` | HU capture | INFO |
| Marker | `AutomationMarker: <label>` | HU capture | WARN |
| Exit tapped | `Video Focus NATIVE received. User clicked Exit in Android Auto.` | HU capture | INFO |
| Exit minimized | `ExitAction: Minimizing projection to OEM Launcher` | HU capture | INFO |
| Exit disconnected | `ExitAction: Disconnecting projection session` | HU capture | INFO |
| Session torn down | `AapService: Native AA user exit. Stopping active launcher.` | HU capture | INFO |
| Address filled | `AapService: filled in this device's Bluetooth address` | HU capture | INFO |
| Phone side active | a ` GH.` or ` CAR.` tag | phone capture | framework |

`log-level` = 2 (INFO) carries every app line above. Leave the rig's log level unless it is above 2.

The exact text of the untrusted-touch line was not checked against AOSP. The case-insensitive `untrusted touch` covers the InputDispatcher wording. If a run drops a tap with no such line, quote the InputDispatcher and InputManager lines in the tap window in Setup notes.

## 7. Runs

Units: **D-HU** (API 34) for R1 to R4, R6, E1 to E3 and S0 to S2, **D-SAM** (API 19) for R5, **D-POCO** as the phone in R6 and E1 to E3 only. Every phone stays in airplane mode except during R6 and the E runs. For each run: `RUN=<id>`, `apk_check` with that arm's md5, force-stop, write the keys, `cap_start`, then the steps. `cap_stop` at the end. Reset between runs with the template's §3a commands.

**A tap that misses.** If `touch_downs` is 0, ask the operator again, at most twice per tap. After three misses the run is UNTESTABLE (operator unavailable). If `touch_downs` is 2 or more, grade it anyway and give the count.

### C0: calibrate the tap point (B, D-HU, then D-SAM)

Keys: status mode `false`, opacity 80. Run `bring_up`. `front` must name `com.android.settings`. Then run `win C0`, `shot C0`, and compute `TAPX`/`TAPY`.

1. Read `C0.png`. The button must sit over a Settings row that opens a sub-screen (not a switch, and not a row that is already selected in a two-pane layout). If it does not, change `floating-button-y-percent` (and x if needed) and repeat. Stop after 3 tries; if no row fits, R1 to R5 are UNTESTABLE on that unit.
2. **The instrument control (C0b).** Force-stop. Set `enable-floating-button` = `false`. Restart the capture, open Settings alone with `adb -s $HU shell am start -a android.settings.SETTINGS`, then `hand_tap C0b` at the same `TAPX`/`TAPY`. **PASS:** `settings_start` ≥ 1 and `touch_downs` ≥ 1. This proves a tap at that point starts a Settings sub-screen and gets logged. If it fails, every other run on that unit is UNTESTABLE.

Record the final x, y, `TAPX`, `TAPY` and `C0.png` in Setup notes. Use them unchanged for every run on that unit.

### R1: the defect on the merged build (A, D-HU). Point of the round, first half.

Keys: status mode `true`, disconnected opacity `0`. Steps: `bring_up`; `front` (must be Settings); `win R1`; `shot R1`; `hand_tap tap`; `grade_tap tap`.

- Setup is valid if `Added floating button overlay` ≥ 1 after launch, and `win` prints `fl=` containing `NOT_TOUCHABLE` with **no** `alpha=` token. If not, the run is INCONCLUSIVE; quote the window block.
- **PASS (defect reproduced):** `touch_downs` ≥ 1, `untrusted` ≥ 1, `settings_start` = 0, `ohu_start` = 0.
- **FAIL (prediction refuted):** `touch_downs` ≥ 1 and `settings_start` ≥ 1. Say so plainly: the tap passed through on A. B's change is then harmless but was not needed on this unit.
- INCONCLUSIVE if `block_untrusted_touches` is not null or `2`.

### R2: the fix at 0% (B, D-HU). Point of the round, second half.

Same keys and steps as R1, on B.

- `win` must print `alpha=0.0` and `NOT_TOUCHABLE`.
- **PASS:** `touch_downs` ≥ 1, `settings_start` ≥ 1, `untrusted` = 0, `ohu_start` = 0.
- **FAIL:** any of those not met. Quote the tap window.

### R3: the fix at 5% (B, D-HU)

Same as R2 with disconnected opacity `5`.

- `win` must print `alpha=0.05` and `NOT_TOUCHABLE`. `R3.png` should show a faint button; that part is seen, not scripted, so report it as unconfirmed.
- **PASS:** the R2 conditions.

### R4: positive control, the button still works (B, D-HU)

Keys: status mode `false`, opacity 80 (the C0 state).

- `win` must print `NOT_TOUCHABLE` **absent** and no `alpha=` token.
- **PASS:** `touch_downs` ≥ 1, `ohu_start` ≥ 1, `settings_start` = 0, `untrusted` = 0, and `Removed floating button overlay` ≥ 1 after `R4-tap-ready` (the app came to the front and hid the button).
- If R4 fails, R2 and R3 cannot tell "passes through" from "the button is broken", so mark them unproven in Setup notes.

### R5: the fix below API 31 (B, D-SAM)

Three taps, each a fresh launch, with labels `t0`, `t5` and `t80`:

| Label | Status mode | Disconnected % | Expected `fl=` bit 0x10 | Expected `alpha=` | PASS |
|---|---|---|---|---|---|
| `t0` | true | 0 | set | `0.0` | `settings_start` ≥ 1, `ohu_start` = 0 |
| `t5` | true | 5 | set | `0.05` | `settings_start` ≥ 1, `ohu_start` = 0 |
| `t80` | false | (any) | clear | absent | `ohu_start` ≥ 1, `settings_start` = 0 |

Every label also needs `touch_downs` ≥ 1. There is no `untrusted` condition on API 19. Use `am` without `-p` (the `send` helper uses `-n`, which works on 4.4).

### R6: the fade with a real session (B, D-HU + D-POCO)

Keys: status mode `true`, disconnected opacity `0`, opacity 80, and the rig's Native AA baseline otherwise (do **not** write `native-poke-all-paired=false` here). Before the run, check that D-POCO's screen is idle (`D-POCO.md`) and that D-POCO is not bonded to another head unit's radio. Capture the phone too: `stdbuf -oL adb -s 4f4027e9 logcat -v time > $OUT/R6.phone.txt &`, started before the first marker. Stamp each HU marker on the phone as well with `adb -s 4f4027e9 shell log -t AutomationMarker "<label>"`.

1. Phone airplane mode on. HU: force-stop, write keys, `cap_start`, launch `MainActivity`, `sleep 15`, `mark R6-start`.
2. Phone: airplane mode off, then `svc wifi enable` and `svc bluetooth enable`, and verify with `dumpsys`. Wait up to 90 s for `Throughput over` in the HU capture. If none, the run is INCONCLUSIVE (no session).
3. `mark R6-bg`. `adb -s $HU shell am start -a android.settings.SETTINGS`, `sleep 5`. `front` must name Settings. If the projection returned to the front (`AapProjectionActivity`), run the same `am start` once more, then go on. Then `win R6-connected` and `shot R6-connected`.
4. `mark R6-drop`. Phone: airplane mode on. Every 3 s for up to 60 s, run `win R6-drop` until it prints `alpha=0.0`. Record the seconds it took. Then `front` (must still be Settings) and `shot R6-drop`.
5. `hand_tap tap`, `grade_tap tap`, `mark R6-end`.

Grade:

| Condition | From |
|---|---|
| `Throughput over` ≥ 1 between `R6-start` and `R6-bg` | HU capture |
| Lines with a `GH.` or `CAR.` tag ≥ 1 between `R6-start` and `R6-bg` (Android Auto was active on the phone) | phone capture |
| `R6-connected` window: no `NOT_TOUCHABLE`, no `alpha=` token | `R6.R6-connected.overlay.txt` |
| `R6-drop` window: `alpha=0.0` and `NOT_TOUCHABLE`, within 60 s of `R6-drop` | `R6.R6-drop.overlay.txt` |
| `Failed to add overlay view` = 0 and `Failed to update overlay layout` = 0, from `R6-start` to `R6-end` | HU capture |
| Tap: `touch_downs` ≥ 1, `settings_start` ≥ 1, `untrusted` = 0 | HU capture, getevent |

- **PASS:** every row met.
- If the window never reaches `alpha=0.0` in 60 s, the run FAILs. Quote the last `Throughput over` timestamp so the host can see whether the session was still alive.
- If `front` names an Open Headunit activity after step 4, the app came forward on its own and removed the button. Grade rows 1 to 3 and 5, and mark the tap INCONCLUSIVE.

The fade itself (300 ms, seen on screen) is not scripted. Report what `R6-connected.png` and `R6-drop.png` show as unconfirmed.

### E1 to E3: the Exit default (D-HU + D-POCO)

| Run | Arm | `aa-exit-action` before | Point |
|---|---|---|---|
| E1 | A | absent | The defect: an untouched setting minimizes on A |
| E2 | B | absent | **Point of the round for Exit**: an untouched setting disconnects on B |
| E3 | B | `0` | A stored choice is kept; the old Bluetooth address is replaced |

Steps for each, after `apk_check` with that arm's md5:

1. Phone airplane mode on. HU: force-stop, write the keys (§4), read each back with `pget`, then `cap_start` and `pcap_start`.
2. Launch `MainActivity`, `sleep 15`, `pmark $RUN-start`.
3. Phone: airplane mode off, then `svc wifi enable` and `svc bluetooth enable`, and verify with `dumpsys`, as R6 does. Wait up to 90 s for `Throughput over` in the HU capture. If none, the run is INCONCLUSIVE (no session).
4. `sleep 10`, then `hand_exit`, then `grade_exit`.
5. `send ACTION_EXIT`, `sleep 5`, `pmark $RUN-end`. Phone airplane mode on. `cap_stop`, `pcap_stop`.
6. HU force-stop, then `pget aa-exit-action` and `pget bt-address`, and `pdump after`.

Every E run needs `throughput_before` ≥ 1, `phone_aa_before` ≥ 1 and `native_focus` ≥ 1, or it is INCONCLUSIVE. Report `phone_byebye` and `front_after` in every run; they are not graded.

- **E1 PASS (defect reproduced):** `minimize` ≥ 1, `disconnect` = 0, `user_exit` = 0. `aa-exit-action` is still ABSENT afterwards.
- **E1 FAIL (prediction refuted):** `disconnect` ≥ 1. Say so plainly.
- **E2 PASS:** `disconnect` ≥ 1, `user_exit` ≥ 1, `minimize` = 0, `throughput_after` = 0. `aa-exit-action` is still ABSENT afterwards (an exit writes nothing).
- **E3 PASS:** `minimize` ≥ 1, `disconnect` = 0, `user_exit` = 0, and `aa-exit-action` still reads `value="0"` afterwards. For the address: if the HU capture has `filled in this device's Bluetooth address` ≥ 1, `bt-address` afterwards must equal the address that line names and must not be `40:EF:4C:A3:CB:A5`. If the line is absent, report that row as INCONCLUSIVE (this unit's address could not be read), not as a FAIL.

### S0 to S2: the Save rule (D-HU only, no phone)

For each: `apk_check` with that arm's md5, force-stop, delete the six keys (§4), check all six read ABSENT with `pget`, `cap_start`. Then:

- **S0 (B, control, no Save):** `adb -s $HU shell am start -n $SETTINGS --ei extra_destination 0 --es extra_search_query "Show Toast"`, `sleep 5`, `adb -s $HU shell input keyevent 4`, `sleep 3`, force-stop, `frozen`, `pdump after`. This shows what opening the screen writes by itself. **PASS:** `frozen=0`.
- **S1 (A) and S2 (B):** `hand_save`, then force-stop, `frozen`, `pget show-toast-messages`, `pdump after`. `cap_stop`.

| Run | PASS |
|---|---|
| S1 (A) | `show-toast-messages` reads `value="false"` and `frozen` ≥ 4 (defect reproduced) |
| S2 (B) | `show-toast-messages` reads `value="false"` and `frozen` = 0 |

- If `show-toast-messages` never reads false within 180 s, the Save did not happen: ask the operator once more, then mark the run UNTESTABLE.
- If S1 gives `frozen` ≤ 3, S2 is INCONCLUSIVE whatever it shows, because A did not freeze the keys on this unit.
- Also report the number of keys in `round-start` and in each `after` file (`grep -c 'name="' <file>`), so the host sees the full size of the write.

After S2, restore `round-start.settings.xml` with the app stopped (the template's §1 notes on pushing a whole file back apply) and read back `aa-exit-action` and `bt-address`.

**Stop rule:** stop the round after S2, or after 3 runs in a row are UNTESTABLE for the same reason.

## 8. Do not re-run

Nothing. This is the thread's first round.

## 9. Report back

1. R1: `untrusted` and `settings_start`, which together say whether the merged build blocks taps on Android 12+.
2. R2 and R3: `settings_start` and `untrusted`, which say whether the fix lets the tap through on D-HU.
3. R4 and R5 `t80`: `ohu_start`, which says whether the button still opens the app where it is meant to.
4. E1 and E2: `minimize`, `disconnect` and `user_exit`, which say whether an untouched setting now ends the session.
5. S1 and S2: `frozen`, which says whether one Save still writes every untouched default to disk.
