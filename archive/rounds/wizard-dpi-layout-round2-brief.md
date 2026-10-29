# wizard-dpi-layout, round 2 brief: the widescreen line and the picker floor

Publish name on `transfer/rig-rounds`: `wizard-dpi-layout-round2-brief.md`. Results go in
`wizard-dpi-layout-round2-results.md`. Evidence goes in the existing release
`rig-evidence-wizard-dpi-layout` as the asset `wizard-dpi-layout-round2-captures.zip`.

One head unit (D-HU) and one phone (D-POCO), the same pair as round 1. One build: the candidate C.
Round 1's baseline APK A is used again for one control run, with no rebuild. 10 short Native AA
sessions with one screenshot each, plus two runs that open the DPI page with the phone off. About
1 h 15 min, build included. The runs use verbs, deep links and keyevents only. No injected tap and
no operator hand step.

| Run | Build | Codec | Stored DPI | What it decides |
|---|---|---|---|---|
| W1 (x3) | C | 800x480 | 103 | **the point of the round**: 103 gives the widescreen layout |
| W2 (x3) | C | 1280x720 | 165 | **the point of the round**: 165 gives the widescreen layout |
| W1p, W2p (x1 each) | C | 800x480, 1280x720 | 104, 166 | record only: one DPI above the line, where the layout should change |
| C1 (x1) | C | 800x480 | 171 | with no migration, a stored 171 stays stored and the clamp sends 170 |
| A1 (x1) | C | 800x480 | 0 (Automatic) | the value a fresh install keeps sends 150 |
| P1 | C | 800x480 | 103 | the DPI page opens below the old floor and shows the line |
| P1-A | A (round 1 APK) | 800x480 | 103 | control: `main` shows 110 and no line |

## 1. Build and baseline

| Arm | Ref | SHA | `commit` in `ACTION_QUERY_STATE` starts with |
|---|---|---|---|
| **C** (candidate) | `fork/fix/wizard-dpi-layout` | `70271831dd4569c9124051f75e1b88ddcf2179d2` | `70271831` |
| **A** (control, P1-A only) | round 1's `A.apk`, built from `origin/main` | `73b52b64cbf88beb411f064b014cd4d7ae5b19db` | `73b52b64` |

**History was rewritten since round 1.** Round 1 tested `71e3854d`, with four commits on `main` at
`73b52b64`. The branch was rebased and force-pushed. It now has three commits on the same base:

- `205be230` Display: keep the announced density inside Android Auto's minimum canvas
- `e8fd5848` Service discovery: log the announced density and keep the fallback's dp canvas
- `70271831` Settings: show the DPI Android Auto receives and its dp canvas

The one-time migration commit (`2195466d` in round 1) is gone, with `LegacyWizardDpiPolicy` and the
`dpi-legacy-wizard-reset` key.

```bash
git fetch fork fix/wizard-dpi-layout
git rev-parse fork/fix/wizard-dpi-layout          # print the full SHA; record it
git worktree add ../wdl-C2 fork/fix/wizard-dpi-layout
cp local.properties ../wdl-C2/
```

If the SHA does not start with `70271831`, stop the round.

### R0. Build and identity gate

Make `rig-data/rounds/wizard-dpi-layout-round2/` and set `OUT` to it. Copy the helpers first
(section 5), so `th_wait` exists. Use the rig's own scripts (`wt-new.sh`, `build_hur_cool.sh`,
`run_unit_tests.sh` in `rig-toolkit/`) where they do the same thing, and say which in Setup notes.
The contract:

```bash
th_wait 70
cd ../wdl-C2 && ./gradlew :app:assembleGithubDebug --max-workers=2 && mkdir -p $OUT/apk && cp app/build/outputs/apk/github/debug/*.apk $OUT/apk/C.apk
th_wait 70
cd ../wdl-C2 && ./gradlew :app:testGithubDebugUnitTest --max-workers=2
cp rig-data/rounds/wizard-dpi-layout-round1/apk/A.apk $OUT/apk/A.apk
md5sum $OUT/apk/C.apk $OUT/apk/A.apk
for s in 'Density is: ' widescreenHintDpi dpiToSave LegacyWizardDpiPolicy dpi-legacy-wizard-reset; do
  printf '%s C=%s\n' "$s" "$(unzip -p $OUT/apk/C.apk 'classes*.dex' | strings | grep -cF "$s")"; done
```

**PASS when all hold:**

1. The build succeeds. The unit tests report 0 failures and 0 errors. Record the count; the review
   host ran 3241.
2. In C, `Density is: `, `widescreenHintDpi` and `dpiToSave` each count 1 or more.
3. In C, `LegacyWizardDpiPolicy` and `dpi-legacy-wizard-reset` each count 0. This proves the
   migration is gone.
4. C's md5 differs from round 1's C (`10e32fe334a62a588a039a9796732207`).

**A failure in R0 stops the round.**

A's md5 must read `1f047a5eea4a08849ac1d9c0c19ebe6f`, as in round 1. If the file is missing or its
md5 differs, do not rebuild A: mark P1-A UNTESTABLE and run the rest.

## 2. What this is and why

A reporter projects at 800x480 and got a reduced Android Auto layout. The wizard had stored a DPI of
171, which gives a dp canvas below Google's 748x450 dp floor. Round 1 measured the cause on this
rig: on D-POCO the layout changes between 170 (h451 dp, full layout) and 171 (h449 dp, left rail and
no media control). C clamps the announced density so the canvas stays at or above the floor. Round 1
passed all seven conditions.

The engineer then asked for these changes, which C now carries:

- **The wizard no longer writes a DPI** unless the user moved the DPI picker. A fresh install keeps
  0 (Automatic), and an upgrader keeps the stored value. Automatic computes the DPI for the codec at
  connect: 150 at 800x480 with the 6 inch size.
- **Every DPI preview uses the visible area.** The wizard's recommendation, the DPI row, the DPI
  page and its new line take the margins that connect will announce, with the projection's
  orientation from the stored setting. On D-HU with this round's keys the margins are 0x0, so every
  expected text below is unchanged.
- **The migration is gone.** A stored 171 now stays stored. The clamp sends 170, which round 1's R2b
  measured as the full layout (as 175 sent as 170).
- **The DPI picker goes down to 80**, not 110. On `main`, the picker forces a stored 103 up to 110.
- **The DPI page shows a new line**: "N DPI or lower: Android Auto shows the map with a media card
  beside it; text gets smaller." N is `codecWidth * 160 / 1240`: 103 at 800x480, 165 at 1280x720.

The 1240 dp is `SystemUi__widescreen_breakpoint_dp` in the decompiled Gearhead. No round has
measured it. Round 1 saw the widescreen form twice, both at a 1280 dp wide canvas: 1920x1080 at 240
(R3) and the 720p fallback at 160 (R4). It saw the bottom-bar form at 1024 dp (1280x720 at 200). So
the breakpoint is between 1024 and 1280 dp on this phone. **W1 and W2 put the canvas at 1242 and
1241 dp**, just above 1240, to test the line the page now shows every user.

The decompile also has `SystemUi__widescreen_aspect_ratio_breakpoint` = 1.67. The smali shows the
widescreen rule does not need it for a canvas under 900 dp high, so W1 (1242x745 dp, ratio 1.667) and
W2 (1241x698 dp) should both draw the widescreen form. A `BAR` in W1 with `WIDE` in W2 would prove
that reading wrong. That result is a real finding about the line, not a rig fault.

## 3. What is different about this round

- **Round 1's layout class `DASH` is now two classes.** Round 1 graded two different forms as `DASH`.
  This round names them apart (section 6). The widescreen form is round 1's R3-1080 and R4 picture.
- **The DPI page is opened by a deep link to its nav destination.** `DpiSettingsFragment` is a
  nav-graph fragment, so `SettingsActivity --ei extra_destination <id>` opens it with no tap. The id
  is a resource integer that differs per build. Section 7 reads it from each APK.
- **The DPI page runs an endless preview animation.** `uiautomator dump` can fail to reach an idle
  state while it runs. P1 and P1-A set `animator_duration_scale` to 0 before they open the page and
  restore it after.
- **No `dpi-legacy-wizard-reset` key is written in this round.** C does not read it. Record whether
  the fresh backup holds one, and do not delete it.
- **W2 asks for a 5 GHz group** (`wifi-direct-band` 1). If the phone takes a fallback in a W2 or W2p
  session (a `the phone chose the ` line), that session is INCONCLUSIVE, because the canvas is not
  the one under test.
- **Round 1's brief errors are fixed here.** The `pwin` marker pattern (round 1 Setup notes), the
  missing class for the bottom bar, and the 170 prediction.
- **The rig's audio settings are a deliberate worst case.** Never write `use-aac-audio`,
  `audio-latency-multiplier`, `audio-queue-capacity` or `enable-audio-sink`. Read them back once and
  record them.
- **D-HU is not rotated** (projection quirk). Nothing here needs it.
- **Phone `Critical error` lines at a verb-driven exit are normal.** Count them only between the arm
  and exit markers.
- **Never run adb calls in parallel against one unit.** The background logcat captures are the only
  exception.

## 4. Settings keys

Write with the app stopped, with `hu_put` (root, then `chown` and `chmod 660`) from `ohu_lib.sh`, in
the key syntax below. Take a fresh backup first and state the delta against it in Setup notes.
Restore D-HU from the backup at the end and diff it against the backup.

```bash
adb -s $DHU shell am force-stop $PKG
prefs_cat > $OUT/settings-backup-DHU.xml; BASEXML=$OUT/settings-backup-DHU.xml
grep -ac 'dpi-legacy-wizard-reset' $BASEXML     # record only
```

`$MAC` is D-POCO's Bluetooth address, `DC:B7:2E:5E:4E:59` in round 1. Read it again from D-HU's
`dumpsys bluetooth_manager` "Bonded devices" list and record it.

```bash
BASE="int:wifi-connection-mode=3 int:native-ap-transport=0 int:native-driver-selection-mode=0 bool:native-poke-all-paired=false set:native-poke-bt-macs=$MAC str:last-connected-native-mac=$MAC str:native-preferred-device-mac=$MAC bool:auto-start-self-mode=false bool:auto-connect-last-session=false bool:kill-on-disconnect=false int:onboarding-version=2 del:native-aa-wake-damage-verdict del:video-profile-starvation-cap int:log-level=2 int:video-fit-mode=0 int:pixel-aspect-ratio-e4=10001 str:display-size-preset=PHONE_4_6 bool:narrow-band-profile-cap=false"
K480="$BASE int:resolutionId=1 int:wifi-direct-band=0"
K720="$BASE int:resolutionId=2 int:wifi-direct-band=1"
KEYS_W1="$K480 int:dpi-pixel-density=103"
KEYS_W1P="$K480 int:dpi-pixel-density=104"
KEYS_C1="$K480 int:dpi-pixel-density=171"
KEYS_A1="$K480 int:dpi-pixel-density=0"
KEYS_W2="$K720 int:dpi-pixel-density=165"
KEYS_W2P="$K720 int:dpi-pixel-density=166"
KEYS_P1="$K480 int:dpi-pixel-density=103"
```

What the keys mean:

- `resolutionId`: 1 is 800x480, 2 is 1280x720.
- `dpi-pixel-density`: the stored DPI. 0 is Automatic.
- `display-size-preset` `PHONE_4_6` is the 6 inch size. Only Automatic reads it.
- `video-fit-mode` 0 is FILL. With it, D-HU announces margins of 0x0, so the codec width is the
  canvas width.
- `pixel-aspect-ratio-e4` 10001 forces square pixels, so the dp canvas is `px * 160 / dpi`.
- `wifi-direct-band`: 0 automatic, 1 ask for 5 GHz.

`ACTION_LOG_MARKER` is not in `AutomationCommandPolicy.CONFIGURING` at C or A, so no
`allow-external-configuration` is needed. Every app line this round greps is INFO or WARN, so
`log-level` 2 carries all of them.

## 5. Helpers and verbs

Copy every helper from round 1's folder into `$OUT`: `ohu_lib.sh`, `ohu_setkeys.py`, `ptr_lib.sh`,
`p1042_lib.sh`, `flags.py`, `env.sh` and `wdl_lib.sh`, all from
`rig-data/rounds/wizard-dpi-layout-round1/`. Round 1's `wdl_lib.sh` carries the corrected `pwin` (the
phone prints `OHURIG  ( <pid>): <label>`) and the `sess` function this round uses unchanged. Source
them in round 1's order, then save and source `$OUT/wdl2_lib.sh` last. It replaces `readout` and adds
`page`:

```bash
# wdl2_lib.sh : source after round 1's wdl_lib.sh. Needs OUT, BASEXML, DHU, PKG.
# readout RUN : the numbers each session is graded on, one per line
readout() { RUN=$1; local w="hwin $RUN-arm $RUN-x-go"
  echo "rendered_windows=$($w | rend)"
  echo "ssl=$($w | c 'SSL handshake complete')"
  echo "self_wake=$($w | c 'MATCH! Starting AapService')"
  echo "disconnected_in_session=$($w | c 'AapService: Disconnected.')"
  echo "mode=$($w | grep -a -m1 -oF 'Initializing WiFi Mode: NATIVE')"
  echo "video_sink=$($w | grep -acE 'Media Sink Setup Request: [0-9]+ on channel VIDEO')"
  echo "rescap=$($w | grep -a -m1 -o '\[RES_CAP\] resolutionId=.*')"
  echo "freq=$($w | grep -a -m1 -o 'onGroupInfoAvailable: SSID: .*' | grep -ao 'Freq: [0-9]* MHz')"
  echo "density=$($w | grep -a -m1 -o '\[ServiceDiscovery\] Density is: .*' | sed 's/^\[ServiceDiscovery\] Density is: //')"
  echo "chose_fallback=$($w | c 'HeadUnitScreenConfig: Video: the phone chose the ')"
  $w | grep -aF -e '[ServiceDiscovery] NegotiatedResolution is: ' -e '[ServiceDiscovery] Margins are: ' \
    -e '[ServiceDiscovery] PixelAspectRatioE4 is: ' -e '[ServiceDiscovery] Offering a ' \
    -e 'HeadUnitScreenConfig: Video: the phone chose the ' | sed 's/^/hu: /'
  local p="pwin $RUN-arm $RUN-x-go"
  echo "ph_lines=$($p | wc -l)"
  echo "ph_codec=$($p | c 'The videoCodecType to be used is:')"
  echo "ph_critical=$($p | c 'Critical error')"
  echo "ph_dims=$($p | grep -a -m1 -o 'displayDimensions Point([0-9]*, [0-9]*), dpi [0-9]*')"
  echo "ph_bye=$(pwin $RUN-x-go $RUN-x-done | c 'received ByeByeRequest')"
  $p | grep -aF -e 'contentArea: ' -e 'Will use layout: ' -e 'displayDimensions ' | head -6 | sed 's/^/ph: /'
}
# dest_id APK : the decimal resource id of the DPI page's nav destination in that APK
dest_id() { local a=$(ls ${ANDROID_HOME:-$ANDROID_SDK_ROOT}/build-tools/*/aapt2 2>/dev/null | sort -V | tail -1)
  [ -n "$a" ] || { echo 0; return; }
  local h=$($a dump resources "$1" | grep -a 'id/dpiSettingsFragment' | grep -ao '0x7f[0-9a-f]*' | head -1)
  [ -n "$h" ] && printf '%d\n' "$h" || echo 0; }
# page RUN DEST : open the DPI page with the app stopped, dump it, screenshot it, close it
page() { RUN=$1; local D=$2 S
  adb -s $POCO shell cmd connectivity airplane-mode enable; sleep 5
  adb -s $DHU shell am force-stop $PKG; sleep 0.3
  hu_put "$BASEXML" $KEYS_P1
  prefs_cat | grep -aoE "$WANT[^>]*>" > $OUT/$RUN.keys-before
  S=$(adb -s $DHU shell settings get global animator_duration_scale | tr -d '\r'); echo "anim_before=$S" > $OUT/$RUN.summary
  adb -s $DHU shell settings put global animator_duration_scale 0; sleep 0.3
  th_gate; PHCAP=; cap_start; mark $RUN-start
  adb -s $DHU shell am start -n $PKG/com.andrerinas.openheadunit.main.SettingsActivity --ei extra_destination $D; sleep 6
  adb -s $DHU shell dumpsys activity activities | grep -a -m1 mResumedActivity >> $OUT/$RUN.summary; sleep 0.3
  adb -s $DHU shell uiautomator dump /sdcard/$RUN.xml >> $OUT/$RUN.summary; sleep 0.3
  adb -s $DHU pull /sdcard/$RUN.xml $OUT/$RUN.xml; sleep 0.3
  adb -s $DHU shell screencap -p /sdcard/$RUN.png; sleep 0.3; adb -s $DHU pull /sdcard/$RUN.png $OUT/$RUN.png
  mark $RUN-end; adb -s $DHU shell input keyevent 4; sleep 2; adb -s $DHU shell am force-stop $PKG; cap_stop
  if [ "$S" = "null" ]; then adb -s $DHU shell settings delete global animator_duration_scale
  else adb -s $DHU shell settings put global animator_duration_scale "$S"; fi
  echo "anim_after=$(adb -s $DHU shell settings get global animator_duration_scale | tr -d '\r')" >> $OUT/$RUN.summary
}
# pageout RUN : the numbers P1 and P1-A are graded on
pageout() { RUN=$1; local x=$OUT/$RUN.xml
  node() { grep -ao "<node[^>]*:id/$1\"[^>]*>" $x; }
  echo "fatal=$(hwin $RUN-start $RUN-end | c 'FATAL EXCEPTION')"
  echo "ise=$(hwin $RUN-start $RUN-end | c 'IllegalStateException')"
  echo "resumed=$(grep -ac 'SettingsActivity' $OUT/$RUN.summary)"
  echo "input_text=$(node dpi_input | grep -ao ' text="[^"]*"')"
  echo "auto_checked=$(node dpi_auto_switch | grep -ao ' checked="[^"]*"')"
  echo "hint_nodes=$(node dpi_widescreen_hint | wc -l)"
  echo "hint_text=$(node dpi_widescreen_hint | grep -ao ' text="[^"]*"')"
  echo "auto_desc=$(node dpi_auto_desc | grep -ao ' text="[^"]*"')"
  echo "clamped_nodes=$(node dpi_clamped_note | wc -l)"
}
```

`sess` (round 1, unchanged) follows the clean-run protocol: the phone is in airplane mode while D-HU
starts, and it comes back after the arm. It takes two screenshots (`$OUT/$RUN.layout1.png` and
`$OUT/$RUN.layout2.png`) and ends with a user exit. Save `readout`'s output as `$OUT/$RUN.readout`
and `pageout`'s as `$OUT/$RUN.pageout`, and quote them in the results.

**Verbs and commands used, and nothing else acts on the app:**

| Action | Command |
|---|---|
| arm Native AA, as the WiFi button | `send ACTION_START_WIRELESS_SCAN` (inside `sess`) |
| user exit from the projection | `send ACTION_DISCONNECT` (inside `sess`) |
| marker | `mark <label>` (also writes `OHURIG` on the phone while `PHCAP=1`) |
| build identity | `state` |
| launch | `adb -s $DHU shell am start -n $MAIN` (inside `sess`) |
| open the DPI page | `adb -s $DHU shell am start -n $PKG/com.andrerinas.openheadunit.main.SettingsActivity --ei extra_destination <id>` (inside `page`) |
| close the DPI page | `adb -s $DHU shell input keyevent 4` (inside `page`) |
| stop the app | `clean_hu` (only on a running app), or `am force-stop` with the app on the settings screen |

Every verb must print `AutomationReceiver: ` in the head unit capture. A step with no such line never
landed: that session is void, not a FAIL.

### Precheck, once

Apps stopped. Record every output in Setup notes.

```bash
adb -s $POCO shell dumpsys package com.google.android.projection.gearhead | grep -a -m1 versionName   # round 1: 18.0.164204-release.daily
adb -s $POCO shell settings get global airplane_mode_on                          # round 1 found 1; sess handles it
adb -s $DHU shell dumpsys bluetooth_manager | grep -a -A8 'Bonded devices'       # D-POCO listed; take $MAC
adb -s $POCO shell dumpsys wifip2p | grep -a -m2 -E 'isGroupOwner|groupFormed'   # isGroupOwner false
adb -s $POCO shell dumpsys window | grep -a -m1 mCurrentFocus                    # launcher, not Settings
adb -s $DHU shell ls -ld /data/data/com.andrerinas.headunitrevived/shared_prefs
adb -s $DHU shell getprop persist.sys.locale                                     # the page's language
prefs_cat | grep -aoE 'name="(use-aac-audio|audio-latency-multiplier|audio-queue-capacity|enable-audio-sink)"[^>]*>'
type sess pwin hwin readout page pageout dest_id | grep -c 'is a function'      # must print 7
```

If `mCurrentFocus` is not the launcher, send `adb -s $POCO shell input keyevent KEYCODE_HOME`. If
D-POCO's Bluetooth is on and lists another head unit's radio as bonded, ask the operator once, before
W1, to forget it on the phone (D-POCO quirk). That is the round's only possible operator ask.

## 6. The deciding lines and the layout classes

Every app string below matched `git grep -F` in `app/src/main` at `70271831`.

| Line (grep -F) | Source | Level | File and window | Used for |
|---|---|---|---|---|
| `AutomationReceiver: ` | `AutomationReceiver` | INFO | head unit, whole run | a verb landed |
| `AutomationMarker: ` | `AutomationEffectRunner` | WARN | head unit, whole run | markers |
| `Initializing WiFi Mode: ` | `WifiLauncherManager` | INFO | head unit, arm to x-go | must read `NATIVE` |
| `WifiDirectManager: onGroupInfoAvailable: SSID: ` | `WifiDirectManager` | INFO | head unit, arm to x-go | the group's `Freq: N MHz` |
| `SSL handshake complete` | `AapSslContext` | INFO | head unit, arm to x-go | a session formed |
| `[RES_CAP] resolutionId=` | `HeadUnitScreenConfig` | INFO | head unit, arm to x-go | `capped=` is the run's codec, `linkCapped=none` |
| `[ServiceDiscovery] NegotiatedResolution is: ` | `ServiceDiscoveryResponse` | INFO | head unit, arm to x-go | the run's codec |
| `[ServiceDiscovery] Margins are: ` | `ServiceDiscoveryResponse` | INFO | head unit, arm to x-go | `0x0` |
| `[ServiceDiscovery] PixelAspectRatioE4 is: ` | `ServiceDiscoveryResponse` | INFO | head unit, arm to x-go | `10001` |
| `[ServiceDiscovery] Density is: ` | `ServiceDiscoveryResponse` | INFO | head unit, arm to x-go | the announced density and why |
| `[ServiceDiscovery] Offering a ` | `ServiceDiscoveryResponse` | INFO | head unit, arm to x-go | record only |
| `HeadUnitScreenConfig: Video: the phone chose the ` | `HeadUnitScreenConfig` | INFO | head unit, arm to x-go | must be 0 (else INCONCLUSIVE) |
| `Media Sink Setup Request: ` | `AapControl` | INFO | head unit, arm to x-go | regex `Media Sink Setup Request: [0-9]+ on channel VIDEO` |
| `Throughput over ` | `VideoDecoder` | INFO | head unit, arm to x-go | picture; `rend` counts `rendered=[1-9]` |
| `MATCH! Starting AapService` | `AutoStartReceiver` | INFO | head unit, arm to x-go | discard check |
| `AapService: Disconnected.` | `AapService` | INFO | head unit, arm to x-go | must be 0 |

System lines on D-HU, for P1 and P1-A, in the window `<RUN>-start` to `<RUN>-end`:

| Line (grep -F) | Used for |
|---|---|
| `FATAL EXCEPTION` | must be 0: the page did not crash the app |
| `IllegalStateException` | must be 0: the `Slider` did not refuse the value |

Gearhead lines, graded on D-POCO's capture in the window `<RUN>-arm` to `<RUN>-x-go` (the bye in
`<RUN>-x-go` to `<RUN>-x-done`). They are not in our source.

| Line (grep -F) | Used for |
|---|---|
| `The videoCodecType to be used is:` | the phone set up video; 1 or more |
| `Critical error` | 0 |
| `received ByeByeRequest` | the session was live until our exit |
| `displayDimensions ` | the size and dpi the phone read; graded through `ph_dims` |
| `contentArea: ` | record only: the line carries the phone's `VD Config` with `w<N>dp h<N>dp` |
| `Will use layout: ` | record only; it did not print on Gearhead 18.0 in round 1 |

The `Density is:` text follows from `AnnouncedDensityPolicy.describe`. `readout` prints it after
`density=`. Compare it exactly:

| Run | Expected `density=` | Expected `ph_dims=` |
|---|---|---|
| W1 | `103 (dp canvas 1242x745, requested 103, within the HUIG floor)` | `displayDimensions Point(800, 480), dpi 103` |
| W1p | `104 (dp canvas 1230x738, requested 104, within the HUIG floor)` | `displayDimensions Point(800, 480), dpi 104` |
| C1 | `170 (dp canvas 752x451, requested 171, clamped to the 450 dp height floor)` | `displayDimensions Point(800, 480), dpi 170` |
| A1 | `150 (dp canvas 853x512, requested automatic, codec-based)` | `displayDimensions Point(800, 480), dpi 150` |
| W2 | `165 (dp canvas 1241x698, requested 165, within the HUIG floor)` | `displayDimensions Point(1280, 720), dpi 165` |
| W2p | `166 (dp canvas 1233x693, requested 166, within the HUIG floor)` | `displayDimensions Point(1280, 720), dpi 166` |

### Layout classes (the grading host reads the PNGs)

The host opens `$OUT/$RUN.layout2.png` and gives it one class and one rail position. Record
`layout1.png`'s class too. The executor does not classify.

| Class | What the projection shows | Seen in round 1 |
|---|---|---|
| `WIDE` | a rail on the left or right, the map, and a media card (album art and controls) beside the map, outside the rail | R3-1080 and R4 (1280 dp wide), graded `DASH`/`LEFT` there |
| `BAR` | a rail along the bottom with the media control inside it; the map fills the area above | 150, 160, 170 at 800x480 and 200 at 1280x720, graded `DASH`/`BOTTOM` there |
| `MAP` | a rail on the left or right, the map fills the rest, no media control anywhere | 171 at 800x480 |
| `OTHER` | anything else: a dialog, a black or frozen picture, the launcher, an app other than the map | none |

Rail position: `LEFT`, `RIGHT` or `BOTTOM`. A session whose `layout2` is `OTHER` is re-run once; a
second `OTHER` makes that session INCONCLUSIVE. Round 1's renderer prompt ("Do you see the Android
Auto screen?") over an otherwise readable picture is not `OTHER`: classify the picture under it and
note the prompt.

## 7. Runs

Order: precheck, R0, install C, W1-1, W1-2, W1-3, W1p, C1, A1, W2-1, W2-2, W2-3, W2p, P1, install A,
P1-A, install C. That keeps P1-A last, so only one extra install pair is needed.

Install C: `adb -s $DHU install -r $OUT/apk/C.apk`, set `WANT_MD5` to C's md5, `apk_check`, `state`.
The `commit` must start with `70271831`. Install A the same way with `install -r -d`; its `commit`
must start with `73b52b64`. An install can wipe `settings.xml` (tooling quirk); `sess` and `page`
rebuild it from `BASEXML` every time.

Each session is `sess <RUN> "$KEYS_..."`, then `readout <RUN> | tee $OUT/<RUN>.readout`. About 3 min
each. After W1-1, check `ph_lines` in its readout. If it is 0 while
`grep -ac 'The videoCodecType to be used is:'` on the phone capture is 1 or more, `pwin` does not
match the phone's marker form: that is a setup failure. Fix `pwin` as round 1 did and regenerate the
readout before W1-2.

**Every session is valid only when all hold**, read from the readout. Report all of them:

1. `rendered_windows` is 1 or more, and `video_sink` is 1 or more.
2. `ssl` is 1. A 0 is a setup failure. A 2 or more is a discard.
3. `self_wake` is 0 and `disconnected_in_session` is 0. Otherwise discard.
4. `mode` reads `Initializing WiFi Mode: NATIVE`.
5. `rescap` shows `capped=` with the run's codec (`_800x480` or `_1280x720`) and `linkCapped=none`.
   The `NegotiatedResolution is:` line shows the same size. `Margins are: 0x0`.
   `PixelAspectRatioE4 is: 10001`.
6. `keys-before` shows the run's `dpi-pixel-density` and `resolutionId`.
7. Phone: `ph_codec` is 1 or more, and `ph_critical` is 0.

A failed condition 4, 5 or 6 is a setup failure: fix the keys and re-run once. A `ph_critical` of 1
or more is not a setup failure: keep the session, quote the line, and the run it belongs to FAILs.

### W1 and W2. The point of the round: does N give the widescreen layout?

**W1-1, W1-2, W1-3.** `sess W1-1 "$KEYS_W1"`, then `-2` and `-3` the same.
**W2-1, W2-2, W2-3.** `sess W2-1 "$KEYS_W2"`, then `-2` and `-3` the same.

**W1 PASS when all hold, in all three sessions:**

1. `density=` equals the W1 row of section 6 exactly.
2. `ph_dims=` equals the W1 row of section 6 exactly.
3. `chose_fallback` is 0.
4. The layout is `WIDE`.

**W2 PASS** has the same four conditions with the W2 rows. If `chose_fallback` is 1 or more in a W2
session, that session is INCONCLUSIVE (the phone drew a fallback canvas). Re-run it once.

**W1 or W2 FAIL** when any valid session is `BAR` or `MAP`, or when a `density=` or `ph_dims=` text
differs. A `BAR` in W1 with `WIDE` in W2 means the phone checks the 1.67 aspect ratio (section 2). Report it
as a FAIL of W1, and say so in the paragraph under the verdict.

**What a PASS would look like if the line were wrong:** the phone would draw `BAR` at 1242 dp, as it
drew `BAR` at 1024 dp in round 1. `WIDE` at 103 and 165 is the only result that makes the page's line
true. The density on the wire is the same on `main` for these stored values, so W1 and W2 test the
phone's breakpoint, not a code path. The code path is the page (P1).

Record the rail side and quote every `ph: ` line, including the phone's `contentArea: ` line with its
`w<N>dp h<N>dp`.

### W1p and W2p. One DPI above the line (record only)

**W1p.** `sess W1p "$KEYS_W1P"`. **W2p.** `sess W2p "$KEYS_W2P"`.

Record the class, the rail, `density=` and `ph_dims=`. These do not change the round verdict. `BAR`
here confirms that N is the boundary. `WIDE` here says the real boundary is higher than N; the line
("N DPI or lower") is then still true but not exact. Report the highest DPI that gave `WIDE` and the
lowest that gave `BAR`, per codec.

### C1. A stored 171 stays stored

**C1.** `sess C1 "$KEYS_C1"`.

PASS when all hold:

1. `density=` equals the C1 row of section 6 exactly. The text
   `150 (dp canvas 853x512, requested automatic, codec-based)` instead means something reset the
   value: FAIL.
2. `ph_dims=` equals the C1 row exactly.
3. `$OUT/C1.keys-after` shows `dpi-pixel-density` with `value="171"`.
4. The layout is `BAR`.

### A1. Automatic, the value a fresh install keeps

**A1.** `sess A1 "$KEYS_A1"`.

PASS when all hold:

1. `density=` equals the A1 row of section 6 exactly.
2. `ph_dims=` equals the A1 row exactly.
3. The layout is `BAR`.

The wizard's save itself is not driven on the rig. The `dpiToSave` unit tests in R0 cover it, and A1
measures what a stored 0 sends.

### P1. The DPI page below the old floor

No phone capture: the phone stays in airplane mode, and no Android Auto session runs.

```bash
DEST_C=$(dest_id $OUT/apk/C.apk); echo "DEST_C=$DEST_C" | tee $OUT/P1.dest
```

If `DEST_C` is 0, read it from the build instead:
`printf '%d\n' $(grep -a ' id dpiSettingsFragment ' $(find ../wdl-C2/app/build/intermediates -name R.txt -path '*runtime_symbol_list*' | head -1) | awk '{print $4}')`.
If it is still 0 or empty, P1 is UNTESTABLE. Never guess the id, and never tap through the settings
list.

```bash
page P1 $DEST_C
pageout P1 | tee $OUT/P1.pageout
```

If the `uiautomator dump` line in `$OUT/P1.summary` does not say `dumped to`, run `page P1 $DEST_C`
once more. A second failure leaves only `P1.png`: the host then grades conditions 3 to 6 from the
screenshot and says so.

**P1 PASS when all hold:**

1. `fatal` is 0 and `ise` is 0.
2. `resumed` is 1 or more.
3. `input_text` is ` text="103"`.
4. `auto_checked` is ` checked="false"`.
5. `hint_nodes` is 1, and `hint_text` starts with ` text="103`. On an English locale the full text is
   ` text="103 DPI or lower: Android Auto shows the map with a media card beside it; text gets smaller."`.
   Quote it exactly whatever the locale.
6. `clamped_nodes` is 0 (103 is within the floor at 800x480, so no clamp note).

Record `auto_desc` (English: ` text="Uses 150 DPI for 480p, a 853x512 dp canvas"`), `anim_before`
and `anim_after`. `anim_after` must equal `anim_before`; if not, restore it by hand with the command
in `page` and say so in Setup notes.

### P1-A. Control: the same page on `main`

Run only if R0 confirmed A's md5. Install A (`install -r -d`), `apk_check`, `state`.

```bash
DEST_A=$(dest_id $OUT/apk/A.apk); echo "DEST_A=$DEST_A" | tee $OUT/P1-A.dest
page P1-A $DEST_A
pageout P1-A | tee $OUT/P1-A.pageout
```

If `DEST_A` is 0, P1-A is UNTESTABLE (round 1's `wdl-A` build folder may be gone; do not rebuild).

**P1-A PASS when all hold:** `fatal` is 0, `input_text` is ` text="110"`, and `hint_nodes` is 0.
That is `main` forcing 103 up to its old floor of 110, with no line. If `input_text` reads `103` on A,
the control does not separate the builds: report it, and P1's condition 3 then proves nothing new.

Then install C again (`install -r`), `apk_check`, `state`, and leave it installed.

### Stop rule

- A setup failure (wrong commit, a missing `AutomationReceiver: ` line, a key that did not read back,
  a failed validity condition 4, 5 or 6) is re-run once. A second setup failure makes that session
  UNTESTABLE.
- A discarded or INCONCLUSIVE session is re-run once. A second one makes it UNTESTABLE or
  INCONCLUSIVE as stated.
- Every session and page run runs at most 2 times. No session is repeated to turn a FAIL into a PASS.
- Stop the round after 18 Native AA sessions in total, re-runs included. Mark every session not yet
  run UNTESTABLE (budget).
- Host thermal: the template's rule. If the host stays hot for 30 min, stop and mark the rest
  UNTESTABLE (host thermal).

End of round: restore D-HU's `settings.xml` from the backup as root, diff it against the backup,
confirm `animator_duration_scale` reads what the precheck of P1 read, leave C installed, and put
D-POCO back in airplane mode.

## 8. Do not re-run

- The clamp at 171, 175 and 160, Automatic after the old migration, and the 720p and 1080p values
  (round 1 R1 to R3, all PASS). C1 and A1 are single sessions for the two changed paths only.
- The 720p fallback density (round 1 R4, PASS). This revision does not change `decide`,
  `fallbackDensity` or `ServiceDiscoveryResponse`.
- The wizard's save path. It needs more taps than a brief allows. The `dpiToSave` tests in R0 cover it.
- D-SAM and D-HP. D-HP cannot run Native AA. A tablet round needs its own brief.
- Portrait canvases. The line is hidden for them, and D-HU is not rotated.

## 9. Report back

1. **W1 and W2:** the class and rail of each session, with each `density=` and `ph_dims=`. This
   decides whether the page's line ships as written.
2. **The boundary:** the class of W1p and W2p, and the phone's `w<N>dp` from `contentArea: ` for
   every W session.
3. **P1 and P1-A:** `input_text`, `hint_text` and `fatal` for each.

Also give D-POCO's Gearhead `versionName`, C's md5, the unit test count, the R0 symbol counts, the
`shared_prefs` `ls -ld` line, D-HU's locale, each session's `Freq:`, C1's `keys-after` DPI, and the
seven validity readings for every session.

## 10. Decisive strings

The runs grep two kinds of log string. The `decisive-strings` block holds the 16 that this app
prints, one per line. Each matched `grep -rF` in `app/src/main` at `70271831`. The
`external-strings` block holds the 8 that other code prints. The `app/src` check does not cover them.
Lines 1 and 2 of that block are Android's. Lines 3 to 8 are Gearhead's. The resource ids
`dpi_input`, `dpi_auto_switch`, `dpi_widescreen_hint`, `dpi_auto_desc`, `dpi_clamped_note` and the
nav id `dpiSettingsFragment` are not log strings. Each matched in `app/src/main/res` at `70271831`.

```external-strings
FATAL EXCEPTION
IllegalStateException
The videoCodecType to be used is:
Critical error
received ByeByeRequest
displayDimensions 
contentArea: 
Will use layout: 
```
