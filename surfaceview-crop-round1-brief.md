# surfaceview-crop, round 1 brief: does a layout-sized SurfaceView fill D-SAM's screen

Published name: `surfaceview-crop-round1-brief.md`. Results go in `surfaceview-crop-round1-results.md`.
Evidence: create the release `rig-evidence-surfaceview-crop` with the asset `surfaceview-crop-round1-captures.zip`.

**One point.** `dsam-surfaceview-crop` round 1 measured the crop on `main`: SurfaceView lit 396 permille of D-SAM's
screen in 3 of 3 sessions, TextureView 957. The candidate sizes a SurfaceView by its layout below API 24 instead of by
the view scale. S1 asks whether the candidate's SurfaceView now lights what TextureView lights. The other runs ask
whether the oversized layer shows picture where it must not (S2, S4, S5), and whether a resize re-fits it (S5, S6).
Touch is not a run: the candidate changes no line of the touch path (section 8).

Units: D-SAM (head unit, API 19) with D-POCO (phone). D-HU Bluetooth held off, D-MOTO radios off. Nothing else.
Hand steps: at most two, in S5 only, and only when `service call statusbar 1` does not open D-SAM's shade: the script
then prints `OPERATOR NOW` and the operator pulls the shade down and back up. Injected input: none.
Time: build 5 to 20 min, setup 6, TX 6, S1 6, S2 6, S4 6, S5 8, S6 4, cleanup 4, plus thermal waits. **About 70 min.**
No other queued round shares this base (`main` `c2c0e95aa`). The Queue line names D-SAM and D-POCO, so a round on D-HU
and D-MOTO can pair with it (§3b).

```rig-requires
lib 0f6b5a7a1
unit D-SAM role=headunit wifi=on bt=any battery=50
unit D-POCO role=phone wifi=on bt=on nobond=D-HU aa=D-SAM battery=30
unit D-HU bt=off
unit D-MOTO wifi=off bt=off
candidate T ff21d2716
hardware D-SAM charges from its own supply (its USB port does not charge it)
run setup cap=420 script=setup.sh grade=grade-setup.sh
run TX cap=1200 script=tx.sh grade=grade-tx.sh
run S1 cap=1200 script=s1.sh grade=grade-s1.sh point=yes
run S2 cap=1200 script=s2.sh grade=grade-s2.sh
run S4 cap=1200 script=s4.sh grade=grade-s4.sh
run S5 cap=1500 script=s5.sh grade=grade-s5.sh hand="pull D-SAM's notification shade down and back up, only when OPERATOR NOW asks"
run S6 cap=900 script=s6.sh grade=grade-s6.sh
run cleanup cap=600 script=cleanup.sh grade=grade-cleanup.sh always=yes
stop setup FAIL,INCONCLUSIVE,UNTESTABLE skip=all
stop TX UNTESTABLE skip=all
stop S1 FAIL,UNTESTABLE skip=all
```

`bt=any` on D-SAM: API 19 prints no `state:` line, so a read-back reads `unknown`. The pairs, bonds and Android Auto
setup are the ones `dsam-surfaceview-crop` round 1 left, so the operator has nothing to change.

| Run | Arms (`view-mode`) | What it decides | Min |
|---|---|---|---|
| setup | | T on D-SAM, keys written, settings and rotation saved | 6 |
| TX | TextureView (1), 3 sessions | the reference: TextureView still fills the screen on T | 6 |
| **S1** | SurfaceView (0), 3 sessions | **the point: the layout fit removes the crop, and the surface still reads 1280x800** | 6 |
| S2 | TN TextureView, then SurfaceView; screen mode Normal | an oversized layer under the status bar shows nothing there | 6 |
| S4 | TI TextureView, then SurfaceView; insets 100 and 40 | the layer under the inset strips shows only the dark margin band | 6 |
| S5 | TM TextureView, then SurfaceView; insets change in a live session | a moved canvas re-fits, and the strips stay dark | 8 |
| S6 | SurfaceView; rotation in a live session | the parent listener re-fits after a rotation | 4 |
| cleanup | | rotation, network and settings given back | 4 |

## 1. Build and baseline

| Arm | Ref | Commit | Tree |
|---|---|---|---|
| T | `fix/surfaceview-crop` | `ff21d27165c125e4d3d93e51744e335eed4a9588` | `6715bb42ff7bc375c71bc24e00c765f120671c49` |

One commit on `main` `c2c0e95aa`. The branch is new, so no history was rewritten. No baseline arm: `dsam-surfaceview-crop`
round 1 measured `main` on the same unit with the same settings (section 8).

```text
WT=$("$RIG_TOOLKIT/wt-new.sh" surfaceview-crop candidate ff21d27165c125e4d3d93e51744e335eed4a9588 | tail -1)
git -C "$WT" rev-parse 'HEAD^{tree}' > "$EV/build-tree.txt"     # MUST read 6715bb42ff7bc375c71bc24e00c765f120671c49
"$RIG_TOOLKIT/rig_thermal.sh" wait 70
HUR_DIR="$WT" MAX_WORKERS=2 "$RIG_TOOLKIT/build_hur_cool.sh"    # then copy the APK from rig-data/apks/ to $EV/apk/T.apk
```

The dev host ran the JVM suite at `ff21d2716`: 4390 tests, 0 failures (10 new in `SurfaceViewFitPolicyTest`). No rig
unit tests.

## 2. What this is and why

**The defect.** Below API 24 a SurfaceView's layer takes the view's moved top-left but not its scale.
`ProjectionViewScaler.updateScale` fits the picture with `scaleX` 1.5005862 and `scaleY` 1.3508443 on D-SAM (the phone
picks the 1280x720 fallback). The round 1 SurfaceFlinger dump put the `SurfaceView` layer at `pos=(-319,-139)` with
`size=(1280, 800)`: the top-left of the scaled view at the unscaled size. So only the top-left 961x661 of the layer was on
screen, and the screen lit 396 permille.

**The fix** (`SurfaceViewFitPolicy`, `ProjectionView`, `ProjectionViewScaler`). Below API 24, for a SurfaceView on the
non-forced path only:

1. The view gets layout params of `round(content * |scale|)` with `Gravity.CENTER`. On D-SAM that is 1921x1081 at
   (-320,-140). The log line is `[UI_DEBUG] SurfaceView sized by layout: 1921x1081 at (-320,-140), buffer 1280x800, scale ...`.
2. The view scale goes back to 1 (the mirror sign stays).
3. `holder.setFixedSize` pins the buffer to the content size, 1280x800. So `surfaceChanged` still reports 1280x800, and
   the canvas the margins were announced for does not move (the "F1" hazard).
4. A layout listener on the parent re-fits when the parent's content size changes, because steps 1 and 3 pin both
   triggers that did it before. The log line is `[UI_DEBUG] SurfaceView parent resized: WxH -> WxH, refitting`.
5. The 2 s surface watchdog in `AapProjectionActivity.checkAndForceSurface` reports the buffer size, not the view size.

API 24 and up, TextureView, GLES and the forced branch keep their old path. No clip is added: nothing below API 24 clips a
SurfaceView layer. The layer outside the content area is Android Auto's margin band, which round 1 read as dark ("F4").
S2, S4 and S5 test that where no app pixel covers the layer.

**The instruments.** After each session renders, `shot` takes a raw `screencap`, the round 1 lit-box measure
(`crop_tools.py measure`, luminance above 24 in 2% of a line), a PNG, `dumpsys SurfaceFlinger` and `dumpsys window
windows`. `sf_parse.py` reads the projection's `SurfaceView` layer (position, size times transform, buffer
`default-size`), the projection window's `transparentRegion` and the `StatusBar` and `NavigationBar` layers.
`hu_parse.py` reads the log lines of section 5 inside a marker window.

## 3. What is different about this round

- **The physical buffer is portrait (800x1280); the app draws landscape** (`rig-quirks/units/D-SAM-and-D-HP.md`). The
  lit box is in buffer rows and columns. The strip and bar measures turn the buffer back to the landscape screen: screen
  (x, y) is buffer row x, column 799-y. Round 1's S1 box (rows 0-739, cols 252-798) matches its layer dump under that rule.
- **SurfaceFlinger positions are landscape screen pixels.** Round 1's layer line reads `pos=(-319,-139)` on a 1280x800
  screen.
- **D-SAM's logcat buffer is not cleared by `logcat -c`.** Every count is inside a marker window. Markers are
  `adb shell log -t AutomationMarker <label>` on D-SAM and D-POCO.
- **The phone `Critical error` window after an exit is 5 s, not 2 s.** Round 1's TX script failed on an exit cascade
  2.07 to 2.13 s after its marker. `crit_count.py` leaves out 5 s after every marker that ends in `-x`, `-r`, `-k2` or
  `-x3`.
- **D-SAM's station is held off before each launch** (`hold_station_off`, as in round 1). `cleanup` gives it back.
- **D-SAM has hardware keys, so the Normal screen mode may show only a `StatusBar` layer.** S2 compares whatever bar
  layers the dump names. With none, S2 is INCONCLUSIVE.
- **S5 moves the canvas with two steps that are not verbs.** `ACTION_SET_SETTINGS` writes `inset-left` 100 and
  `inset-top` 40 into the live preferences; the grade wants `"imported":2` in the reply. Nothing re-applies insets until
  the window regains focus (`onWindowFocusChanged` calls `setFullscreen`). So `move_canvas` opens the shade with
  `service call statusbar 1` (`expandNotificationsPanel` in AOSP 4.4) and reads `mCurrentFocus`. If the focus is on
  `StatusBar`, the code worked, and `service call statusbar 2` (`collapsePanels`) closes it. If not, the code does not
  match this Samsung build: the script prints `OPERATOR NOW` and waits 90 s for the operator to pull the shade down and
  back up. `S5-lever.txt` records which lever each arm used. Nobody can check the codes before the round from the dev
  host, so the run checks them and falls back by itself.
- **S5's precondition is the inset values, not the line alone.** `SystemUI.applyInsets` below API 21 adds the visible
  frame's insets to the manual ones and then removes its listener. A status bar still shown when focus returns reads
  `T33` and skews the canvas. So each arm needs its last `Detected Insets` line after the move to read `L0 T0 R0 B0`.
  Any other value makes S5 INCONCLUSIVE: the lever moved the canvas, not the inset settings.
- **S6 rotates D-SAM with `settings put system user_rotation`** (`rig-quirks/topics/tooling.md`), with `screen-orientation`
  0 (SYSTEM, `SCREEN_ORIENTATION_USER`). `AapProjectionActivity` handles `orientation|screenSize` itself, so the activity
  is not rebuilt. Setup saves `accelerometer_rotation` and `user_rotation`; S6 and cleanup put them back. S6 has no
  TextureView arm: `TextureProjectionView.onSurfaceTextureSizeChanged` calls no `onSurfaceChanged`, so a TextureView
  logs no `Actual surface dimensions:` line on a resize and cannot be compared line for line.
- **The rig's audio settings stay as they are** (AAC on, a short queue). They are a deliberate worst case and not this
  round's subject, so `KEYS` does not write them.
- Read `rig-quirks/topics/tooling.md`, `topics/projection.md`, `units/D-SAM.md`, `units/D-SAM-and-D-HP.md`, `units/D-POCO.md`.

## 4. Settings keys

**D-SAM**, written by `write_keys` with the app stopped and read back into `$EV/<run>-<tag>-settings.xml`:

| Key | Type | Value |
|---|---|---|
| `onboarding-version` | int | 2 |
| `allow-external-configuration` | boolean | true (S5's `ACTION_SET_SETTINGS` needs it) |
| `enable-audio-sink` | boolean | true |
| `narrow-band-profile-cap`, `kill-on-disconnect`, `enable-floating-button`, `external-bt-zbt-transport`, `forced_scale`, `hud_mirroring`, `wifi-direct-rotation-pending` | boolean | false |
| `native-poke-all-paired`, `wifi-direct-stable-identity` | boolean | true |
| `connection-modes` | set | `wifi` |
| `wifi-connection-mode` | int | 3 |
| `native-ap-transport` | int | 0 |
| `resolutionId` | int | 3 |
| `video-fit-mode` | int | 0 (FILL) |
| `dpi-pixel-density` | int | 175 |
| `inset-right`, `inset-bottom` | int | 0 |
| `view-mode` | int | `VIEW`: 1 TEXTURE, 0 SURFACE |
| `fullscreen-mode` | int | `FS`: 1 IMMERSIVE; S2 0 NONE |
| `inset-left`, `inset-top` | int | `IL`, `IT`: 0 and 0; S4 100 and 40 |
| `screen-orientation` | int | `ORI`: 2 LANDSCAPE; S6 0 SYSTEM |
| `log-level` | int | 1 DEBUG (`LOG`) |
| `video-profile-starvation-cap`, `native-poke-bt-macs`, `native-mac`, `last-connected-native-mac`, `native-preferred-device-mac` | | delete |
| `station-stand-down-network-id` | | delete before each launch; written back in cleanup |

`cached-surface-width` and `cached-surface-height` are left alone. They read 1280 and 800 after round 1. S1 grades that
they still do, because the F1 leak would write 1921 and 1081 into them.

## 5. The deciding lines

App strings checked with `git grep -F` in `app/src/main` at `ff21d2716`: all exist. `hu_parse.py` greps them in
`$EV/<run>-hu.txt` inside the marker window each grade names. Phone lines are in `$EV/<run>-ph.txt`.

| Id | Line (literal part) | Level | Read |
|---|---|---|---|
| L-SSL | `SSL handshake complete` | INFO | a session formed |
| L-THR | `Throughput over` | INFO | the decoder renders |
| L-USE-S / L-USE-T | `Using SurfaceView` / `Using TextureView` | INFO | the view the activity built |
| L-FALL | `Falling back to SurfaceView for this session` | WARN | TextureView gave up by itself |
| L-FIT | `SurfaceView sized by layout: ` + `WxH at (L,T), buffer BWxBH` | INFO | new: the layout fit and the buffer |
| L-RESIZE | `SurfaceView parent resized: ` + `WxH -> WxH, refitting` | INFO | new: the parent listener fired |
| L-NORM | `Normal Scale. scaleX: ` | INFO | the view-scale path (TextureView here); S1 derives its expected fit from TX's |
| L-SURF | `onSurfaceChanged. Actual surface dimensions: width=` | INFO | the surface size the activity got |
| L-MISM | `Surface mismatch! Expected: ` | INFO | the canvas moved under the surface |
| L-INIT | `Honest Init \| Mode: ` with `Anchor: 1280x800 (from ` | INFO | the anchor the session used |
| L-WD | `Watchdog: SurfaceView IS valid. Forcing onSurfaceChanged.` | WARN | the 2 s watchdog fired (report) |
| L-DRIFT | `CarScreen: margins drifted from the announced` | INFO | a margin re-announce (D-SAM runs MARGIN) |
| L-MOVED | `CarScreen: canvas moved from the announced` | INFO | the PAR form of the same |
| L-UICFG | `AA is already running, send corrected via sendUpdateUiConfigRequest` | INFO | margins sent on the live session |
| L-ROT | `[AapProjectionActivity] onConfigurationChanged: orientation=` then `1` | INFO | S6 precondition: portrait |
| L-INSETS | `Legacy SystemUI: Detected Insets` + `L0 T0 R0 B0` | DEBUG | S5 precondition: insets re-applied, no bar counted |
| L-NEWSURF | `New surface set: ` | INFO | report: a recreated surface |
| L-GIVE | `WiFi network is enabled again and should rejoin` | INFO | cleanup |
| S-CRASH | `Process: com.andrerinas.headunitrevived` | system | a `FATAL EXCEPTION` block |
| P-CRIT | `Critical error` | phone | Android Auto ended the session; 5 s after exits left out |

## 6. Runs

### setup. Known state

**setup.sh**
```bash
rl_step "D-SAM: back up, install T, write the keys, save the rotation"
adb -s "$SAM" shell am force-stop "$PKG"
[ -s "$EV/settings-backup-sam.xml" ] || adb -s "$SAM" shell run-as "$PKG" cat shared_prefs/settings.xml > "$EV/settings-backup-sam.xml"
install_apk "$SAM" "$APK_T" setup
send ACTION_QUERY_STATE > "$EV/setup-query.txt" 2>&1
VIEW=1; FS=1; IL=0; IT=0; ORI=2; LOG=1
write_keys base
rot_save
adb -s "$SAM" shell getprop ro.build.version.sdk > "$EV/setup-sdk.txt"
adb -s "$SAM" shell date > "$EV/setup-clock.txt"; date >> "$EV/setup-clock.txt"
rl_step "D-POCO: stop the app, go home"
adb -s "$PH" shell am force-stop "$PKG"
adb -s "$PH" shell input keyevent KEYCODE_HOME
```

**grade-setup.sh**
```bash
rl_cond 1 eq "$(grep -c '^md5match=1' "$EV/setup-identity.txt")" 1 'T installed on D-SAM (md5 of the pulled APK)'  # not-literal
rl_cond 2 ge "$(grep -c "$T_SHA" "$EV/setup-query.txt")" 1 "D-SAM reports commit $T_SHA"
rl_cond 3 ge "$(grep -ac 'name="wifi-connection-mode" value="3"' "$EV/setup-base-settings.xml")" 1 'wifi-connection-mode 3 read back'  # not-literal
rl_cond 4 ge "$(grep -ac 'shared_prefs\|<map' "$EV/settings-backup-sam.xml")" 1 'D-SAM settings backup taken'  # not-literal
rl_cond 5 eq "$(tr -d '\r ' < "$EV/setup-sdk.txt")" 19 'D-SAM runs API 19, the side of the gate under test'
rl_cond 6 eq "$(grep -c . "$EV/rot-orig.txt" 2>/dev/null)" 2 'accelerometer_rotation and user_rotation saved'  # not-literal
```

- **PASS** when all six hold. Any other verdict stops the round; cleanup still runs.

**Decision:** PASS lets the round run on T. FAIL stops the round: fix the install or the keys and start again.

### TX. Reference: TextureView, three sessions

s1 is a fresh launch, s2 a relaunch after a force-stop with the session up, s3 a relaunch after a clean exit, as in
round 1.

**tx.sh**
```bash
apk_check "$SAM" "$APK_T" TX
run_open || exit 0
VIEW=1; FS=1; IL=0; IT=0; ORI=2; LOG=1
v_cycle
run_close
```

**grade-tx.sh**
```bash
[ -s "$EV/TX-thermal.txt" ] && { rl_verdict UNTESTABLE "host thermal: the wait failed"; exit 0; }
v_counts TX TX-s1-go TX-end
[ "${SESS:-0}" -ge 2 ] || { rl_verdict UNTESTABLE "session gate: $SESS of 3 sessions rendered and were measured"; exit 0; }
eval "$(python3 -I "$SCR/crop_tools.py" box --ref "$EV/TX-s1-lit.txt" "$EV/TX-s2-lit.txt" "$EV/TX-s3-lit.txt" --cand "$EV/TX-s1-lit.txt" "$EV/TX-s2-lit.txt" "$EV/TX-s3-lit.txt")"
eval "$(python3 -I "$SCR/hu_parse.py" "$EV/TX-hu.txt" TX-s1-go TX-end)"
MEDOFF=$(absdiff "${MED:-0}" 957)
rl_cond 1 eq "$MD5" 1 'T on D-SAM (md5)'
rl_cond 2 ge "$(grep -ac 'name="view-mode" value="1"' "$EV/TX-all-settings.xml")" 1 'view-mode 1 read back'  # not-literal
rl_cond 3 ge "$USE_T" 1 'L-USE-T: the activity built a TextureView'
rl_cond 4 eq "$FALL" 0 'L-FALL 0: TextureView did not fall back'
rl_cond 5 ge "${MIN:-0}" 940 "every lit box is 940 permille or more (min $MIN, median $MED)"
rl_cond 6 le "$MEDOFF" 10 "the median is within 10 of round 1's 957 (median $MED)"
rl_cond 7 eq "$FIT_N" 0 'no L-FIT line: the TextureView path is unchanged'
rl_cond 8 ge "$NORMAL" 1 "L-NORM printed (scale $NORMAL_SCALE; round 1 read 1.5005862,1.3508443)"
rl_cond 9 eq "$CRIT" 0 'phone: Critical error, 5 s after exits left out'
rl_cond 10 eq "$CRASH" 0 'no FATAL EXCEPTION for com.andrerinas on D-SAM'
[ "${FALL:-0}" -ge 1 ] && rl_verdict INCONCLUSIVE "TextureView fell back to SurfaceView, so TX is not a reference"
```

- **PASS** when all ten hold: TextureView still fills the screen on T, as on `main`.
- **FAIL** on any condition: S1 then grades INCONCLUSIVE.
- **INCONCLUSIVE** when TextureView fell back. **UNTESTABLE** when fewer than 2 sessions render, or the host stays hot;
  the stop rule then ends the round.

**Decision:** PASS makes TX the reference for S1 and confirms the gate left TextureView alone. FAIL means T changed the
TextureView path or the rig changed since round 1: re-plan from TX's log before any S result counts.

### S1. SurfaceView, three sessions (the point)

**s1.sh**
```bash
apk_check "$SAM" "$APK_T" S1
run_open || exit 0
VIEW=0; FS=1; IL=0; IT=0; ORI=2; LOG=1
v_cycle
run_close
```

**grade-s1.sh**
```bash
[ -s "$EV/S1-thermal.txt" ] && { rl_verdict UNTESTABLE "host thermal: the wait failed"; exit 0; }
TXV=$(python3 -I -c 'import json, sys; print(json.load(open(sys.argv[1])).get("verdict", ""))' "$EV/TX.grade.json" 2>/dev/null)   # rig_session.py writes TX.grade.json
v_counts S1 S1-s1-go S1-end
[ "${SESS:-0}" -ge 2 ] || { rl_verdict UNTESTABLE "session gate: $SESS of 3 sessions rendered and were measured"; exit 0; }
eval "$(python3 -I "$SCR/crop_tools.py" box --ref "$EV/TX-s1-lit.txt" "$EV/TX-s2-lit.txt" "$EV/TX-s3-lit.txt" --cand "$EV/S1-s1-lit.txt" "$EV/S1-s2-lit.txt" "$EV/S1-s3-lit.txt")"
eval "$(python3 -I "$SCR/hu_parse.py" "$EV/TX-hu.txt" TX-s1-go TX-end --prefix TX_)"
eval "$(python3 -I "$SCR/hu_parse.py" "$EV/S1-hu.txt" S1-s1-go S1-end --prefix ALL_)"
NS=0; FITOK=0; SURFOK=0; SURFBIG=0; MISMS=0; HON=0; WDS=0; SFOK=0; SFDEF=0; NEWS=0
for n in s1 s2 s3; do
  case "$n" in s1) e=S1-k2 ;; s2) e=S1-x3 ;; *) e=S1-end ;; esac
  grep -q "^$n ssl=1" "$EV/S1-gate.txt" 2>/dev/null && [ -s "$EV/S1-$n-lit.txt" ] || continue
  NS=$((NS + 1))
  eval "$(python3 -I "$SCR/hu_parse.py" "$EV/S1-hu.txt" "S1-$n-go" "$e" --scale "$TX_NORMAL_SCALE" --surf 1280x800)"
  [ "${FIT_LAST_OK:-0}" = 1 ] && FITOK=$((FITOK + 1))
  SURFOK=$((SURFOK + ${SURF_OK:-0})); SURFBIG=$((SURFBIG + ${SURF_BIG:-0})); MISMS=$((MISMS + ${MISM:-0}))
  HON=$((HON + ${HONEST:-0})); WDS=$((WDS + ${WD:-0})); NEWS=$((NEWS + ${NEWSURF:-0}))
  eval "$(python3 -I "$SCR/sf_parse.py" "$EV/S1-$n-sf.txt")"
  echo "$n fit=${FIT_LAST:-none} expect=${FIT_EXPECT:-none} layer=${SV_X:-?},${SV_Y:-?} ${SV_EW:-?}x${SV_EH:-?} tr=${SV_TR:-?} default=${SV_DEF:-?}" >> "$EV/S1-layers.txt"
  if [ "${SF_OK:-0}" = 1 ] && [ "$(absdiff "$SV_X" "$FIT_EXP_L")" -le 2 ] && [ "$(absdiff "$SV_Y" "$FIT_EXP_T")" -le 2 ] &&
     [ "$(absdiff "$SV_EW" "$FIT_EXP_W")" -le 4 ] && [ "$(absdiff "$SV_EH" "$FIT_EXP_H")" -le 4 ]; then SFOK=$((SFOK + 1)); fi
  [ "${SV_DEF:-}" = 1280x800 ] && SFDEF=$((SFDEF + 1))
done
rl_cond 1 eq "$MD5" 1 'T on D-SAM (md5)'
rl_cond 2 ge "$(grep -ac 'name="view-mode" value="0"' "$EV/S1-all-settings.xml")" 1 'view-mode 0 read back'  # not-literal
rl_cond 3 ge "$ALL_USE_S" 1 'L-USE-S: the activity built a SurfaceView'
rl_cond 4 ge "${RATIO:-0}" 750 "every S1 lit box is 750 permille or more of TX's median (min $RATIO; S1 min $MIN, TX median $REF)"
rl_cond 5 le "${DEV:-999}" 8 "every S1 box edge is within 8 px of TX's (worst $DEV)"
rl_cond 6 eq "$FITOK" "$NS" "each session's last L-FIT line is the fit TX's scale gives (sessions $NS; see S1-layers.txt)"
rl_cond 7 eq "$SFOK" "$NS" 'SurfaceView layer at the fit position within 2 px and the fit size within 4 px, each session'
rl_cond 8 eq "$SFDEF" "$NS" 'the layer buffer default-size is 1280x800 (setFixedSize took), each session'
rl_cond 9 ge "$SURFOK" "$NS" 'L-SURF reads 1280x800 at least once a session'
rl_cond 10 eq "$SURFBIG" 0 'no L-SURF line wider than the panel (the F1 leak would read 1921x1081)'
rl_cond 11 eq "$MISMS" 0 'L-MISM 0'
rl_cond 12 ge "$HON" "$NS" 'L-INIT anchor 1280x800 in each session'
rl_cond 13 ge "$(grep -ac 'name="cached-surface-width" value="1280"' "$EV/S1-after.xml")" 1 'cached-surface-width still 1280'  # not-literal
rl_cond 14 ge "$(grep -ac 'name="cached-surface-height" value="800"' "$EV/S1-after.xml")" 1 'cached-surface-height still 800'  # not-literal
rl_cond 15 eq "$CRIT" 0 'phone: Critical error, 5 s after exits left out'
rl_cond 16 eq "$CRASH" 0 'no FATAL EXCEPTION for com.andrerinas on D-SAM'
rl_cond 17 ge "$WDS" 0 "report: L-WD watchdog lines ($WDS); every L-SURF after one is covered by 9 and 10"
rl_cond 18 ge "$NEWS" 0 "report: L-NEWSURF lines ($NEWS)"
[ "${TXV:-}" != PASS ] && rl_verdict INCONCLUSIVE "TX did not pass ($TXV), so there is no full-screen reference"
```

- **PASS** when all graded conditions hold: the SurfaceView fills the screen as TextureView does, at the layout the
  policy derives from TX's own scale, and every surface reading stays 1280x800.
- **FAIL** when any condition fails with TX PASS. The stop rule then ends the round. Report which session, its PNG, its
  `S1-layers.txt` line and its L-FIT and L-SURF lines.
- **INCONCLUSIVE** when TX did not pass.

**Decision:** PASS means the layout fit removes the crop with the F1 guard holding; S2, S4 and S5 then decide whether the
`contentFillsScreen` gate goes in. FAIL means the layer rule or the fixed-size scaling did not hold on API 19: re-plan
from the S1 layer dump (cond 7 and 8 name which half).

### S2. Screen mode Normal: the layer under the status bar

One TextureView session (TN, the reference) and one SurfaceView session, `fullscreen-mode` 0. Round 1 showed the
relaunch order does not change the geometry on this unit (three sessions identical to the pixel), so one session per arm.

**s2.sh**
```bash
apk_check "$SAM" "$APK_T" S2
run_open || exit 0
FS=0; IL=0; IT=0; ORI=2; LOG=1
for TAG in tn sv; do
  VIEW=1; [ "$TAG" = sv ] && VIEW=0
  write_keys "$TAG"
  if launch "$TAG"; then rl_sleep 20; shot "$RUN-$TAG" keep; fi
  mark "$RUN-$TAG-x"; app_exit
done
mark "$RUN-end"
run_close
```

**grade-s2.sh**
```bash
[ -s "$EV/S2-thermal.txt" ] && { rl_verdict UNTESTABLE "host thermal: the wait failed"; exit 0; }
v_counts S2 S2-tn-go S2-end
grep -q '^tn ssl=1' "$EV/S2-gate.txt" 2>/dev/null || { rl_verdict UNTESTABLE "TN did not render, so S2 has no reference"; exit 0; }  # not-literal
grep -q '^sv ssl=1' "$EV/S2-gate.txt" 2>/dev/null || { rl_verdict UNTESTABLE "the SurfaceView session did not render"; exit 0; }  # not-literal
eval "$(python3 -I "$SCR/hu_parse.py" "$EV/S2-hu.txt" S2-sv-go S2-sv-x)"
eval "$(python3 -I "$SCR/sf_parse.py" "$EV/S2-sv-sf.txt")"
eval "$(python3 -I "$SCR/crop_tools.py" box --ref "$EV/S2-tn-lit.txt" --cand "$EV/S2-sv-lit.txt")"
eval "$(python3 -I "$SCR/crop_tools.py" bars "$EV/S2-tn.raw" "$EV/S2-sv.raw" "${BARS:-}")"
rl_cond 1 eq "$MD5" 1 'T on D-SAM (md5)'
rl_cond 2 ge "$(grep -ac 'name="fullscreen-mode" value="0"' "$EV/S2-sv-settings.xml")" 1 'fullscreen-mode 0 read back'  # not-literal
rl_cond 3 ge "$USE_S" 1 'L-USE-S in the SurfaceView session'
rl_cond 4 ge "$FIT_N" 1 "L-FIT printed (last $FIT_LAST)"
rl_cond 5 eq "${SV_OVER_BAR:-0}" 1 "control: the SurfaceView layer overlaps a bar layer (bars $BARS; layer $SV_X,$SV_Y ${SV_EW}x$SV_EH)"
rl_cond 6 le "${BAR_DIFF:--1}" 20 "bar pixels whose max channel differs from TN by more than 16, permille of $BAR_PX"
rl_cond 7 le "${DEV:-999}" 8 "lit box edges within 8 px of TN's (box $MIN, TN $REF)"
rl_cond 8 eq "$CRIT" 0 'phone: Critical error, 5 s after exits left out'
rl_cond 9 eq "$CRASH" 0 'no FATAL EXCEPTION for com.andrerinas on D-SAM'
if [ "${BAR_N:-0}" -eq 0 ]; then rl_verdict INCONCLUSIVE "the dump names no StatusBar or NavigationBar layer with a visible region"
elif [ "${SV_OVER_BAR:-0}" != 1 ]; then rl_verdict INCONCLUSIVE "control failed: the layer does not reach a bar, so S2 tests nothing"
elif [ "${BAR_DIFF:--1}" -lt 0 ]; then rl_verdict INCONCLUSIVE "the bar pixels could not be compared (a raw frame is missing)"
fi
```

- **PASS** when all nine hold: the bar shows what it shows over TextureView, and the picture sits where TN's does.
- **FAIL** when the control holds and the bar pixels or the box differ.
- **INCONCLUSIVE** when no bar layer shows, or the layer does not reach one.

**Decision:** PASS means an oversized layer costs no picture under a system bar (F2), so Normal mode needs no gate. FAIL
means picture shows through or beside a bar: add the `contentFillsScreen` gate in round 2.

### S4. Inset strips 100 and 40: the layer under them

One TextureView session (TI, the reference for S4 and S5) and one SurfaceView session, `inset-left` 100 and `inset-top`
40, IMMERSIVE.

**s4.sh**
```bash
apk_check "$SAM" "$APK_T" S4
run_open || exit 0
FS=1; IL=100; IT=40; ORI=2; LOG=1
for TAG in ti sv; do
  VIEW=1; [ "$TAG" = sv ] && VIEW=0
  write_keys "$TAG"
  if launch "$TAG"; then rl_sleep 20; shot "$RUN-$TAG" keep; fi
  mark "$RUN-$TAG-x"; app_exit
done
mark "$RUN-end"
run_close
```

**grade-s4.sh**
```bash
[ -s "$EV/S4-thermal.txt" ] && { rl_verdict UNTESTABLE "host thermal: the wait failed"; exit 0; }
v_counts S4 S4-ti-go S4-end
grep -q '^ti ssl=1' "$EV/S4-gate.txt" 2>/dev/null || { rl_verdict UNTESTABLE "TI did not render, so S4 has no reference"; exit 0; }  # not-literal
grep -q '^sv ssl=1' "$EV/S4-gate.txt" 2>/dev/null || { rl_verdict UNTESTABLE "the SurfaceView session did not render"; exit 0; }  # not-literal
eval "$(python3 -I "$SCR/hu_parse.py" "$EV/S4-hu.txt" S4-sv-go S4-sv-x)"
eval "$(python3 -I "$SCR/sf_parse.py" "$EV/S4-sv-sf.txt")"
eval "$(python3 -I "$SCR/crop_tools.py" box --ref "$EV/S4-ti-lit.txt" --cand "$EV/S4-sv-lit.txt")"
eval "$(python3 -I "$SCR/crop_tools.py" strips "$EV/S4-ti.raw" TI)"
eval "$(python3 -I "$SCR/crop_tools.py" strips "$EV/S4-sv.raw" SV)"
LIMIT=$(( ${TI_STRIP_LIT:-0} + 4 ))
rl_cond 1 eq "$MD5" 1 'T on D-SAM (md5)'
rl_cond 2 ge "$(grep -ac 'name="inset-left" value="100"' "$EV/S4-sv-settings.xml")" 1 'inset-left 100 read back'  # not-literal
rl_cond 3 ge "$USE_S" 1 'L-USE-S in the SurfaceView session'
rl_cond 4 lt "${FIT_LAST_L:-999}" 100 "L-FIT left edge is left of the strip (last fit $FIT_LAST)"
rl_cond 5 lt "${FIT_LAST_T:-999}" 40 'L-FIT top edge is above the strip'
rl_cond 6 lt "${SV_X:-999}" 100 "control: the layer starts left of x=100 (layer $SV_X,$SV_Y ${SV_EW}x$SV_EH)"
rl_cond 7 lt "${SV_Y:-999}" 40 'control: the layer starts above y=40'
rl_cond 8 le "${DEV:-999}" 8 "lit box edges within 8 px of TI's (box $MIN, TI $REF)"
rl_cond 9 eq "$CRIT" 0 'phone: Critical error, 5 s after exits left out'
rl_cond 10 eq "$CRASH" 0 'no FATAL EXCEPTION for com.andrerinas on D-SAM'
rl_cond 11 le "${SV_STRIP_LIT:-999}" "$LIMIT" "lit strip lines over SurfaceView: $SV_STRIP_LIT (TI $TI_STRIP_LIT, pm $SV_STRIP_PM against $TI_STRIP_PM)"
if [ "${TI_STRIP_LIT:--1}" -lt 0 ] || [ "${SV_STRIP_LIT:--1}" -lt 0 ]; then rl_verdict INCONCLUSIVE "a raw frame for the strip measure is missing"
elif [ "${HINT_STRIPS:-0}" != 1 ]; then rl_verdict INCONCLUSIVE "the window's transparentRegion does not hold the strips (P1=$HINT_P1 P2=$HINT_P2): the app window paints them, so the pixels say nothing about F4; S5 decides alone"
fi
```

- **PASS** when all eleven hold and the window's `transparentRegion` holds both strip points (50,400) and (640,20):
  the layer shows in the strips, and it shows only the dark margin band (F4 holds).
- **FAIL** when the hint holds the strips and they carry picture (cond 11), or the layer or fit misses the strips.
- **INCONCLUSIVE** when the hint does not hold the strips: the app window paints them, so the pixel grade is void.

**Decision:** PASS means F4 holds in the inset strips and no gate goes in. FAIL on cond 11 means F4 is false: the
`contentFillsScreen` gate goes in. INCONCLUSIVE leaves F4 to S5.

### S5. A canvas moved under a live session

Two sessions with no insets at launch: TM (TextureView, the reference) and then SurfaceView. After each renders,
`move_canvas` imports `inset-left` 100 and `inset-top` 40 and makes the window lose and regain focus (section 3), so
the window re-applies the insets. TM goes through the same import and the same lever, so it is the reference for the
lit box (cond 9): it separates a live margin re-announce from the SurfaceView fit. A TextureView clips to its view, so
TM says nothing about the strips; S4's TI stays the strip reference (cond 10).

**s5.sh**
```bash
apk_check "$SAM" "$APK_T" S5
run_open || exit 0
FS=1; IL=0; IT=0; ORI=2; LOG=1
for TAG in tm sv; do
  VIEW=1; [ "$TAG" = sv ] && VIEW=0
  write_keys "$TAG"
  if launch "$TAG"; then
    rl_sleep 20
    move_canvas "$TAG"
    rl_sleep 10
    shot "$RUN-$TAG" keep
  fi
  mark "$RUN-$TAG-x"; app_exit
done
mark "$RUN-end"
run_close
```

**grade-s5.sh**
```bash
[ -s "$EV/S5-thermal.txt" ] && { rl_verdict UNTESTABLE "host thermal: the wait failed"; exit 0; }
v_counts S5 S5-tm-go S5-end
grep -q '^sv ssl=1' "$EV/S5-gate.txt" 2>/dev/null || { rl_verdict UNTESTABLE "the SurfaceView session did not render"; exit 0; }  # not-literal
eval "$(python3 -I "$SCR/hu_parse.py" "$EV/S5-hu.txt" S5-tm-mv S5-tm-x --prefix TM_)"
eval "$(python3 -I "$SCR/hu_parse.py" "$EV/S5-hu.txt" S5-sv-mv S5-sv-x --buf 1180x760 --surf 1180x760)"
eval "$(python3 -I "$SCR/sf_parse.py" "$EV/S5-sv-sf.txt")"
eval "$(python3 -I "$SCR/crop_tools.py" box --ref "$EV/S4-ti-lit.txt" --cand "$EV/S5-tm-lit.txt")"
TMTI=$DEV
REFL=TM; REFBOX="$EV/S5-tm-lit.txt"
if ! grep -q '^tm ssl=1' "$EV/S5-gate.txt" 2>/dev/null || [ "${TM_INSETS_LAST:-}" != 0,0,0,0 ] || [ ! -s "$REFBOX" ]; then  # not-literal
  REFL=TI; REFBOX="$EV/S4-ti-lit.txt"
fi
eval "$(python3 -I "$SCR/crop_tools.py" box --ref "$REFBOX" --cand "$EV/S5-sv-lit.txt")"
eval "$(python3 -I "$SCR/crop_tools.py" strips "$EV/S4-ti.raw" TI)"
eval "$(python3 -I "$SCR/crop_tools.py" strips "$EV/S5-sv.raw" SV)"
LIMIT=$(( ${TI_STRIP_LIT:-0} + 4 ))
rl_cond 1 eq "$MD5" 1 'T on D-SAM (md5)'
rl_cond 2 ge "$(grep -ac '"imported":2' "$EV/S5-sv-import.txt")" 1 'ACTION_SET_SETTINGS imported both inset keys'  # not-literal
rl_cond 3 ge "$DRIFT" 1 'L-DRIFT after the move'
rl_cond 4 ge "$RESIZE_N" 1 'L-RESIZE after the move: the parent listener fired'
rl_cond 5 ge "$FIT_BUF_MATCH" 1 "an L-FIT line with buffer 1180x760 after the move (last $FIT_LAST)"
rl_cond 6 ge "$SURF_OK" 1 "L-SURF reads 1180x760 after the move (last $SURF_LAST)"
rl_cond 7 lt "${SV_X:-999}" 100 "control: the layer starts left of x=100 (layer $SV_X,$SV_Y ${SV_EW}x$SV_EH)"
rl_cond 8 lt "${SV_Y:-999}" 40 'control: the layer starts above y=40'
rl_cond 9 le "${DEV:-999}" 8 "lit box edges within 8 px of $REFL's after the same move (box $MIN, $REFL $REF)"
rl_cond 10 le "${SV_STRIP_LIT:-999}" "$LIMIT" "lit strip lines: $SV_STRIP_LIT (S4 TI $TI_STRIP_LIT)"
rl_cond 11 eq "$CRIT" 0 'phone: Critical error, 5 s after exits left out'
rl_cond 12 eq "$CRASH" 0 'no FATAL EXCEPTION for com.andrerinas on D-SAM'
rl_cond 13 ge "${TMTI:-999}" 0 "report: TM's box against S4's TI, worst edge $TMTI px (levers: $(tr '\n' ' ' < "$EV/S5-lever.txt" 2>/dev/null))"
if [ "${INSETS_LAST:-}" != 0,0,0,0 ]; then rl_verdict INCONCLUSIVE "the last L-INSETS after the move reads '${INSETS_LAST:-none}', not L0 T0 R0 B0: the lever did not re-apply the insets cleanly (see S5-sv-shade.txt)"
elif [ "${USE_S:-0}" -ge 1 ] || [ "${USE_T:-0}" -ge 1 ]; then rl_verdict INCONCLUSIVE "the import rebuilt the projection activity, so the canvas did not move under a live view"
elif [ ! -s "$EV/S4-ti-lit.txt" ] || [ "${TI_STRIP_LIT:--1}" -lt 0 ] || [ "${SV_STRIP_LIT:--1}" -lt 0 ]; then rl_verdict INCONCLUSIVE "S4's TI reference or a raw frame is missing"
elif [ "${HINT_STRIPS:-0}" != 1 ]; then rl_verdict INCONCLUSIVE "the window's transparentRegion does not hold the strips (P1=$HINT_P1 P2=$HINT_P2), so cond 10 says nothing about F4"
elif [ "$REFL" = TI ] && [ "${DEV:-999}" -gt 8 ]; then rl_verdict INCONCLUSIVE "TM gave no clean reference, and against S4's TI the box is off by $DEV px: a live re-announce and the fit cannot be told apart"
fi
```

- **PASS** when all thirteen hold: the move re-fit the layer to the 1180x760 content, the picture sits where TM's sits
  after the same move, and the strips stay dark.
- **FAIL** on cond 2 to 6: the move did not reach the fit. FAIL on cond 9 or 10: picture shows past the content area.
- **INCONCLUSIVE** when the SurfaceView arm's insets did not read `L0 T0 R0 B0` after the move, the activity was
  rebuilt, S4's reference is missing, the hint does not hold the strips, or cond 9 fails with no clean TM.

**Decision:** PASS means a canvas moved under a live session re-fits and shows no picture in the strips, so layout sizing
stays for that case too. FAIL on cond 3 to 6 points at the parent listener: re-plan from its log line. FAIL on cond 9
with TM as the reference, or on cond 10, means the SurfaceView shows picture past the content area: the
`contentFillsScreen` gate goes in.

### S6. Rotation in a live session

`screen-orientation` 0 (SYSTEM), `accelerometer_rotation` 0, `user_rotation` 1 at launch. After the session renders,
`user_rotation` 0 turns D-SAM to portrait. One SurfaceView session, graded on its own expected values: the parent
listener logs a resize from 1280x800, the fit takes the new content size as its buffer, and the surface and the layer
read that buffer.

**s6.sh**
```bash
apk_check "$SAM" "$APK_T" S6
run_open || exit 0
VIEW=0; FS=1; IL=0; IT=0; ORI=0; LOG=1
adb -s "$SAM" shell settings put system accelerometer_rotation 0
adb -s "$SAM" shell settings put system user_rotation 1
write_keys sv
if launch sv; then
  rl_sleep 20; shot "$RUN-sv-l"
  mark "$RUN-sv-rot"; from=$(rl_offset "$EV/$RUN-hu.txt")
  adb -s "$SAM" shell settings put system user_rotation 0
  rl_wait_line "$EV/$RUN-hu.txt" '[AapProjectionActivity] onConfigurationChanged: orientation=1' 30 "$from" > "$EV/$RUN-sv-rotwait.txt"
  rl_wait_line "$EV/$RUN-hu.txt" 'SurfaceView parent resized' 20 "$from" >> "$EV/$RUN-sv-rotwait.txt"
  rl_sleep 10
  shot "$RUN-sv"
fi
mark "$RUN-sv-x"; app_exit
adb -s "$SAM" shell settings put system user_rotation 1
mark "$RUN-end"
rot_restore
run_close
```

**grade-s6.sh**
```bash
[ -s "$EV/S6-thermal.txt" ] && { rl_verdict UNTESTABLE "host thermal: the wait failed"; exit 0; }
v_counts S6 S6-sv-go S6-end
grep -q '^sv ssl=1' "$EV/S6-gate.txt" 2>/dev/null || { rl_verdict UNTESTABLE "the SurfaceView session did not render"; exit 0; }  # not-literal
eval "$(python3 -I "$SCR/hu_parse.py" "$EV/S6-hu.txt" S6-sv-rot S6-sv-x)"
eval "$(python3 -I "$SCR/sf_parse.py" "$EV/S6-sv-sf.txt")"
TOOK=0; [ -n "${RESIZE_TO:-}" ] && [ "${FIT_LAST_BUF:-}" = "$RESIZE_TO" ] && TOOK=1
BUFEQ=0; [ -n "${FIT_LAST_BUF:-}" ] && [ "$FIT_LAST_BUF" = "${SURF_LAST:-}" ] && BUFEQ=1
DEFEQ=0; [ -n "${FIT_LAST_BUF:-}" ] && [ "${SV_DEF:-}" = "$FIT_LAST_BUF" ] && DEFEQ=1
rl_cond 1 eq "$MD5" 1 'T on D-SAM (md5)'
rl_cond 2 ge "$(grep -ac 'name="screen-orientation" value="0"' "$EV/S6-sv-settings.xml")" 1 'screen-orientation 0 read back'  # not-literal
rl_cond 3 ge "$RESIZE_1280" 1 "L-RESIZE from 1280x800 after the rotation (to $RESIZE_TO)"
rl_cond 4 ge "$FIT_N" 1 "an L-FIT line after the rotation (last $FIT_LAST)"
rl_cond 5 eq "$TOOK" 1 "the last L-FIT buffer is the new content size ($FIT_LAST_BUF against $RESIZE_TO)"
rl_cond 6 eq "$BUFEQ" 1 "the last L-SURF equals the last L-FIT buffer ($SURF_LAST against $FIT_LAST_BUF)"
rl_cond 7 eq "$DEFEQ" 1 "the layer buffer default-size is the new buffer ($SV_DEF against $FIT_LAST_BUF)"
rl_cond 8 ge "$THR" 1 'the session renders after the rotation (L-THR)'
rl_cond 9 eq "$CRIT" 0 'phone: Critical error, 5 s after exits left out'
rl_cond 10 eq "$CRASH" 0 'no FATAL EXCEPTION for com.andrerinas on D-SAM'
rl_cond 11 ge "$(( DRIFT + MOVED + UICFG + MISM ))" 0 "report: re-announce lines after the rotation (drift $DRIFT, moved $MOVED, uicfg $UICFG, mismatch $MISM)"
if [ "${CONF_P:-0}" -lt 1 ]; then rl_verdict INCONCLUSIVE "L-ROT orientation=1 missing after the rotation marker: user_rotation did not turn the activity"; fi
```

- **PASS** when all eleven hold: the parent listener re-fit the view to the rotated content, and the buffer, the
  surface and the layer all read the new content size.
- **FAIL** on any condition with the precondition met.
- **INCONCLUSIVE** when the activity did not turn to portrait.

**Decision:** PASS means the parent listener puts back the resize triggers that layout sizing pinned. FAIL means it did
not re-fit after a rotation: re-plan from its log line (cond 3 to 7 name which step stopped: listener, fit, buffer,
surface or layer).

### cleanup. D-SAM back to a known state (always runs)

**cleanup.sh**
```bash
fail=0
rl_step "D-SAM: rotation, network and settings back"
adb -s "$SAM" shell am force-stop "$PKG"
rot_restore
NETID=$(head -1 "$EV/netid.txt" 2>/dev/null)
if [ -n "$NETID" ]; then
  "$RIG_TOOLKIT/set_prefs_runas_host.py" "$SAM" "scalar:int:station-stand-down-network-id:$NETID" > "$EV/cleanup-netid.txt" 2>&1
  rl_cap_start cleanup-hu "$SAM"; from=$(rl_offset "$EV/cleanup-hu.txt")
  adb -s "$SAM" shell am start -n "$MAIN" >/dev/null
  rl_wait_line "$EV/cleanup-hu.txt" 'WiFi network is enabled again and should rejoin' 60 "$from" > "$EV/cleanup-give.txt" || fail=1
  app_exit
  rl_cap_stop cleanup-hu
fi
if [ -s "$EV/settings-backup-sam.xml" ]; then
  adb -s "$SAM" push "$EV/settings-backup-sam.xml" /data/local/tmp/settings-restore.xml >/dev/null
  adb -s "$SAM" shell run-as "$PKG" cp /data/local/tmp/settings-restore.xml shared_prefs/settings.xml
  adb -s "$SAM" shell rm -f /data/local/tmp/settings-restore.xml
  adb -s "$SAM" shell run-as "$PKG" cat shared_prefs/settings.xml > "$EV/cleanup-sam-settings.xml"
  diff <(tr -d '\r' < "$EV/settings-backup-sam.xml" | grep -v '^$') <(tr -d '\r' < "$EV/cleanup-sam-settings.xml" | grep -v '^$') > "$EV/cleanup-sam-diff.txt" || fail=1
fi
adb -s "$SAM" shell rm -f /sdcard/ohu-crop.raw /sdcard/ohu-crop.png /data/local/tmp/ohu-insets.json
{ adb -s "$SAM" shell settings get system accelerometer_rotation
  adb -s "$SAM" shell settings get system user_rotation; } | tr -d '\r' > "$EV/cleanup-rot.txt"
rl_step "D-HU and D-MOTO: Bluetooth on"
rl_radio "$DHU" bt on || fail=1
rl_radio "$MOTO" bt on || fail=1
echo "netid=${NETID:-none} fail=$fail" > "$EV/cleanup-result.txt"
exit "$fail"
```

**grade-cleanup.sh**
```bash
NETID=$(head -1 "$EV/netid.txt" 2>/dev/null)
if [ -n "$NETID" ]; then
  rl_cond 1 ge "$(grep -acF 'WiFi network is enabled again and should rejoin' "$EV/cleanup-hu.txt")" 1 'L-GIVE: D-SAM got its network back'
else
  rl_cond 1 eq 0 0 'no network id was saved, so nothing to give back'
fi
rl_cond 2 eq "$(grep -c . "$EV/cleanup-sam-diff.txt" 2>/dev/null)" 0 'D-SAM settings equal the backup'  # not-literal
RD=0
for i in 1 2; do   # a value setup read as null is not compared: settings on API 19 cannot delete a key
  o=$(sed -n "${i}p" "$EV/rot-orig.txt" 2>/dev/null); c=$(sed -n "${i}p" "$EV/cleanup-rot.txt" 2>/dev/null)
  case "$o" in [0-9]) [ "$o" = "$c" ] || RD=$((RD + 1)) ;; esac
done
rl_cond 3 eq "$RD" 0 'D-SAM rotation settings equal what setup saved (numeric values only)'
```

- **PASS** when all three hold. A FAIL leaves a note for the next D-SAM round and changes no other verdict. T stays
  installed on D-SAM; the next round installs its own build.

**Decision:** PASS leaves D-SAM at its baseline for the next round. FAIL means the next D-SAM round restores rotation,
network and settings by hand first.

## 7. Scripts

**lib.sh**
```bash
# Serials, paths, verbs and session helpers for surfaceview-crop round 1. Sourced before every script and grade.
source /home/oscar/Coding/StudioProjects/rig-toolkit/rig_devices.sh
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
MAIN=$PKG/com.andrerinas.openheadunit.main.MainActivity
SAM=$D_SAM; PH=$D_POCO; DHU=$D_HU; MOTO=$D_MOTO
SCR=$EV/scripts; APK_T=$EV/apk/T.apk
T_SHA=ff21d2716
INSETS_JSON='{"format":"open-headunit-settings","version":1,"settings":{"inset-left":100,"inset-top":40}}'
KEYS="scalar:int:onboarding-version:2 scalar:boolean:allow-external-configuration:true scalar:boolean:enable-audio-sink:true
 scalar:boolean:narrow-band-profile-cap:false scalar:boolean:kill-on-disconnect:false scalar:boolean:enable-floating-button:false
 set:connection-modes:wifi scalar:int:wifi-connection-mode:3 scalar:int:native-ap-transport:0
 scalar:boolean:native-poke-all-paired:true scalar:boolean:external-bt-zbt-transport:false
 scalar:boolean:wifi-direct-stable-identity:true scalar:boolean:wifi-direct-rotation-pending:false
 scalar:int:resolutionId:3 scalar:int:video-fit-mode:0 scalar:int:dpi-pixel-density:175
 scalar:boolean:forced_scale:false scalar:boolean:hud_mirroring:false scalar:int:inset-right:0 scalar:int:inset-bottom:0
 del:video-profile-starvation-cap del:native-poke-bt-macs del:native-mac del:last-connected-native-mac
 del:native-preferred-device-mac"
VIEW=1; FS=1; IL=0; IT=0; ORI=2; LOG=1   # the arm keys; each script sets them before write_keys

send() { local a=$1; shift; adb -s "$SAM" shell am broadcast -f 0x00000020 -n "$RX" -a "com.andrerinas.openheadunit.$a" "$@"; }
mark() {   # mark LABEL: the same marker line on D-SAM and D-POCO
  adb -s "$SAM" shell log -t AutomationMarker "$1"; adb -s "$PH" shell log -t AutomationMarker "$1"
  echo "$1 $(date '+%m-%d %H:%M:%S')" >> "$EV/marks.txt"
}
absdiff() { local v=$(( ${1:-0} - ${2:-0} )); echo "${v#-}"; }   # absdiff A B: |A - B|
write_keys() {   # write_keys TAG: section 4's keys plus the arm keys, app stopped, then the file read back
  adb -s "$SAM" shell am force-stop "$PKG"
  "$RIG_TOOLKIT/set_prefs_runas_host.py" "$SAM" $KEYS "scalar:int:view-mode:$VIEW" "scalar:int:fullscreen-mode:$FS" \
    "scalar:int:inset-left:$IL" "scalar:int:inset-top:$IT" "scalar:int:screen-orientation:$ORI" \
    "scalar:int:log-level:$LOG" > "$EV/$RUN-$1-keys.txt" 2>&1
  adb -s "$SAM" shell run-as "$PKG" cat shared_prefs/settings.xml > "$EV/$RUN-$1-settings.xml"
}
apk_check() {   # apk_check SERIAL APK TAG: md5 of the installed APK against the built one, to $EV/TAG-identity.txt
  local s=$1 apk=$2 tag=$3 dev a b
  dev=$(adb -s "$s" shell pm path "$PKG" | tr -d '\r' | sed -n 's/^package://p' | head -1)
  rm -f "$EV/$tag-pulled.apk"
  [ -n "$dev" ] && adb -s "$s" pull "$dev" "$EV/$tag-pulled.apk" >/dev/null 2>&1
  a=$(md5sum < "$apk" 2>/dev/null | cut -c1-32); b=$(md5sum < "$EV/$tag-pulled.apk" 2>/dev/null | cut -c1-32)
  if [ -n "$a" ] && [ "$a" = "$b" ]; then echo "md5match=1 $a"; else echo "md5match=0 built=$a installed=$b"; fi > "$EV/$tag-identity.txt"
  rm -f "$EV/$tag-pulled.apk"
}
install_apk() { adb -s "$1" install -r -d "$2" > "$EV/$3-install.txt" 2>&1; apk_check "$1" "$2" "$3"; }
app_exit() {   # disconnect, exit, force-stop D-SAM
  adb -s "$SAM" shell am broadcast -f 0x00000020 -n "$RX" -a com.andrerinas.openheadunit.ACTION_DISCONNECT >/dev/null 2>&1; rl_sleep 5
  adb -s "$SAM" shell am start -a android.intent.action.VIEW -d headunit://exit >/dev/null; rl_sleep 5
  adb -s "$SAM" shell am force-stop "$PKG"
}
phone_link() { rl_radio "$PH" wifi "$1"; rl_radio "$PH" bt "$1"; }   # phone_link on|off
hold_station_off() {   # before a launch: save the stand-down network id once, then delete it so the launch gives nothing back
  local id
  id=$(adb -s "$SAM" shell run-as "$PKG" cat shared_prefs/settings.xml | tr -d '\r' | grep -ao 'name="station-stand-down-network-id" value="[0-9]*"' | grep -ao '[0-9]*"$' | tr -d '"')   # not-literal: a settings.xml line
  [ -n "$id" ] && [ ! -s "$EV/netid.txt" ] && echo "$id" > "$EV/netid.txt"
  echo "netid_seen=${id:-none}" >> "$EV/$RUN-station.txt"
  "$RIG_TOOLKIT/set_prefs_runas_host.py" "$SAM" "del:station-stand-down-network-id" >> "$EV/$RUN-station.txt" 2>&1
}
rot_save() {   # D-SAM's accelerometer_rotation and user_rotation, once per round, to $EV/rot-orig.txt
  [ -s "$EV/rot-orig.txt" ] && return 0
  { adb -s "$SAM" shell settings get system accelerometer_rotation
    adb -s "$SAM" shell settings get system user_rotation; } | tr -d '\r' > "$EV/rot-orig.txt"
}
rot_restore() {   # put both back as rot_save found them
  local acc rot
  acc=$(sed -n 1p "$EV/rot-orig.txt" 2>/dev/null); rot=$(sed -n 2p "$EV/rot-orig.txt" 2>/dev/null)
  case "$acc" in 0|1) adb -s "$SAM" shell settings put system accelerometer_rotation "$acc" ;; esac
  case "$rot" in 0|1|2|3) adb -s "$SAM" shell settings put system user_rotation "$rot" ;; esac
}

shot() {   # shot TAG [keep]: raw and PNG screencap, the lit box, the layer and window dumps; keep holds the raw frame
  adb -s "$SAM" shell screencap /sdcard/ohu-crop.raw
  adb -s "$SAM" pull /sdcard/ohu-crop.raw "$EV/$1.raw" >/dev/null 2>&1
  adb -s "$SAM" shell screencap -p /sdcard/ohu-crop.png
  adb -s "$SAM" pull /sdcard/ohu-crop.png "$EV/$1.png" >/dev/null 2>&1
  adb -s "$SAM" shell rm -f /sdcard/ohu-crop.raw /sdcard/ohu-crop.png
  python3 -I "$SCR/crop_tools.py" measure "$EV/$1.raw" > "$EV/$1-lit.txt" 2>&1
  [ "${2:-}" = keep ] || rm -f "$EV/$1.raw"
  adb -s "$SAM" shell dumpsys SurfaceFlinger > "$EV/$1-sf.txt" 2>&1
  adb -s "$SAM" shell dumpsys window windows > "$EV/$1-win.txt" 2>&1
}
see_picture() {   # see_picture TAG FROM: SSL and a rendering decoder after byte FROM; writes TAG ssl=1|0 to the gate file
  if rl_wait_line "$EV/$RUN-hu.txt" 'SSL handshake complete' 150 "$2"; then
    rl_wait_line "$EV/$RUN-hu.txt" 'Throughput over' 60 "$2" || echo "$1 thr=0" >> "$EV/$RUN-gate.txt"
    echo "$1 ssl=1" >> "$EV/$RUN-gate.txt"; return 0
  fi
  echo "$1 ssl=0" >> "$EV/$RUN-gate.txt"; return 1
}
launch() {   # launch TAG: phone off, start the app, phone on, wait for a rendering session; two attempts, 0 when one renders
  local att from
  for att in 1 2; do
    rl_step "$1, attempt $att"
    phone_link off
    adb -s "$SAM" shell am force-stop "$PKG"
    hold_station_off
    mark "$RUN-$1-go"; from=$(rl_offset "$EV/$RUN-hu.txt")
    adb -s "$SAM" shell am start -n "$MAIN" >/dev/null
    rl_sleep 2; phone_link on
    see_picture "$1" "$from" && return 0
    mark "$RUN-$1-r"; app_exit
  done
  return 1
}
run_open() {   # thermal gate, D-HU Bluetooth held off, the phone app stopped, both captures on; 2 when the host stays hot
  rm -f "$EV/$RUN-thermal.txt" "$EV/$RUN-gate.txt"
  rl_thermal_gate || { echo HOST_HOT > "$EV/$RUN-thermal.txt"; return 2; }
  rl_hold_off "$DHU" bt || echo "dhu_bt_not_off" >> "$EV/$RUN-gate.txt"
  adb -s "$PH" shell am force-stop "$PKG"
  adb -s "$PH" shell input keyevent KEYCODE_HOME
  rl_cap_start "$RUN-hu" "$SAM"; rl_cap_start "$RUN-ph" "$PH"
}
run_close() { rl_cap_stop "$RUN-hu"; rl_cap_stop "$RUN-ph"; rl_release "$DHU" bt; }
move_canvas() {   # move_canvas TAG: import insets 100 and 40 live, then a focus loss and regain through the shade
  local from
  rl_step "$1: move the canvas (import, then the shade)"
  mark "$RUN-$1-mv"; from=$(rl_offset "$EV/$RUN-hu.txt")
  send ACTION_SET_SETTINGS --es json "'$INSETS_JSON'" > "$EV/$RUN-$1-import.txt" 2>&1
  if ! grep -aq '"imported":2' "$EV/$RUN-$1-import.txt"; then   # not-literal: the verb's reply
    printf '%s' "$INSETS_JSON" > "$EV/$RUN-insets.json"
    adb -s "$SAM" push "$EV/$RUN-insets.json" /data/local/tmp/ohu-insets.json >/dev/null
    adb -s "$SAM" shell chmod 644 /data/local/tmp/ohu-insets.json
    send ACTION_SET_SETTINGS --es path /data/local/tmp/ohu-insets.json >> "$EV/$RUN-$1-import.txt" 2>&1
    adb -s "$SAM" shell rm -f /data/local/tmp/ohu-insets.json
  fi
  adb -s "$SAM" shell service call statusbar 1 > "$EV/$RUN-$1-shade.txt" 2>&1
  rl_sleep 3
  adb -s "$SAM" shell dumpsys window windows | grep -a 'mCurrentFocus' >> "$EV/$RUN-$1-shade.txt"   # system
  if grep -a 'mCurrentFocus' "$EV/$RUN-$1-shade.txt" | grep -aq 'StatusBar'; then   # system
    echo "$1 lever=service_call" >> "$EV/$RUN-lever.txt"
    adb -s "$SAM" shell service call statusbar 2 >> "$EV/$RUN-$1-shade.txt" 2>&1
  else
    echo "$1 lever=operator" >> "$EV/$RUN-lever.txt"
    echo "OPERATOR NOW: on D-SAM, pull the notification shade down from the top edge, then push it back up. Touch nothing else."
  fi
  rl_wait_line "$EV/$RUN-hu.txt" 'Legacy SystemUI: Detected Insets' 90 "$from" > "$EV/$RUN-$1-mv-wait.txt"
  [ "$1" = sv ] && rl_wait_line "$EV/$RUN-hu.txt" 'SurfaceView parent resized' 20 "$from" >> "$EV/$RUN-$1-mv-wait.txt"
  return 0
}

v_cycle() {   # TX and S1: s1 fresh launch, s2 relaunch after a force-stop with the session up, s3 relaunch after a clean exit
  local from ok=0
  write_keys all
  launch s1 && ok=1
  [ "$ok" = 1 ] && { rl_sleep 20; shot "$RUN-s1"; }
  rl_step "s2: force-stop with the session up, relaunch"
  mark "$RUN-k2"
  adb -s "$SAM" shell am force-stop "$PKG"
  hold_station_off
  mark "$RUN-s2-go"; from=$(rl_offset "$EV/$RUN-hu.txt")
  adb -s "$SAM" shell am start -n "$MAIN" >/dev/null
  if [ "$ok" = 1 ] && see_picture s2 "$from"; then rl_sleep 20; shot "$RUN-s2"; fi
  rl_step "s3: clean exit, relaunch"
  mark "$RUN-x3"
  app_exit
  if [ "$ok" = 1 ] && launch s3; then rl_sleep 20; shot "$RUN-s3"; fi
  mark "$RUN-end"
  app_exit
  adb -s "$SAM" shell run-as "$PKG" cat shared_prefs/settings.xml > "$EV/$RUN-after.xml"
}

v_counts() {   # v_counts RUN FROM TO: identity, measured sessions, crashes and phone Critical error inside FROM..TO
  local hu="$EV/$1-hu.txt" n
  SESS=0
  for n in s1 s2 s3 tn ti tm sv; do grep -q "^$n ssl=1" "$EV/$1-gate.txt" 2>/dev/null && [ -s "$EV/$1-$n-lit.txt" ] && SESS=$((SESS + 1)); done
  CRASH=$(rl_cnt "$hu" "$2" "$3" 'Process: com.andrerinas.headunitrevived')   # system
  CRIT=$(python3 -I "$SCR/crit_count.py" "$EV/$1-ph.txt" "$2" "$3" 'Critical error')   # phone
  MD5=$(grep -c '^md5match=1' "$EV/$1-identity.txt" 2>/dev/null)   # not-literal
  eval "$(python3 -I "$SCR/hu_parse.py" "$hu" "$2" "$3")"
}
```

**crop_tools.py**
```python
# Screen measures from a raw screencap (header, then RGBA_8888) and the comparisons the grades use.
#   measure RAW                      one line: w= h= rows=a-b cols=c-d box_permille=N (round 1's lit box; exit 2: not raw)
#   box --ref LIT... --cand LIT...   MIN MED (candidate boxes) REF (reference median) RATIO DEV (worst edge offset) CROPPED N
#   strips RAW PREFIX                PREFIX_STRIP_LIT (lit lines in x<100 and y<40) and PREFIX_STRIP_PM (lit pixels, permille)
#   bars RAW_A RAW_B RECTS           BAR_PX and BAR_DIFF (permille of bar pixels whose max channel differs by more than 16)
# A portrait buffer (w < h) is D-SAM's landscape screen turned once: screen (x, y) is buffer row x, column w-1-y.
import re, statistics, struct, sys


def load(path):
    try:
        data = open(path, "rb").read()
    except OSError:
        return None
    if len(data) < 16:
        return None
    w, h, fmt = struct.unpack("<III", data[:12])
    head = len(data) - w * h * 4
    if fmt != 1 or head < 12 or head > 16:
        return None
    return w, h, data[head:]


def screen_size(img):
    w, h, _ = img
    return (h, w) if w < h else (w, h)


def pixel(img, x, y):
    w, h, px = img
    i = (x * w + (w - 1 - y)) * 4 if w < h else (y * w + x) * 4
    return px[i], px[i + 1], px[i + 2]


def is_lit(p):
    return p[0] * 299 + p[1] * 587 + p[2] * 114 > 24000


def measure(path):
    img = load(path)
    if not img:
        print(f"crop_measure: not an RGBA_8888 raw frame: {path}"); sys.exit(2)
    w, h, px = img
    row_lit = [0] * h; col_lit = [0] * w
    for y in range(h):
        base = y * w * 4
        for x in range(0, w, 2):
            i = base + x * 4
            if px[i] * 299 + px[i + 1] * 587 + px[i + 2] * 114 > 24000:
                row_lit[y] += 2; col_lit[x] += 2
    rows = [y for y in range(h) if row_lit[y] >= w * 0.02]
    cols = [x for x in range(0, w, 2) if col_lit[x] >= h * 0.02]
    if not rows or not cols:
        print(f"w={w} h={h} rows=none cols=none box_permille=0"); return
    area = (rows[-1] - rows[0] + 1) * (cols[-1] - cols[0] + 2)
    print(f"w={w} h={h} rows={rows[0]}-{rows[-1]} cols={cols[0]}-{cols[-1]} box_permille={min(1000, area * 1000 // (w * h))}")


def edges(path):
    try:
        t = open(path, errors="replace").read()
    except OSError:
        return None
    m = re.search(r"rows=(\d+)-(\d+) cols=(\d+)-(\d+) box_permille=(\d+)", t)
    return tuple(int(g) for g in m.groups()) if m else None


def box(args):
    ref, cand, cur = [], [], None
    for a in args:
        if a in ("--ref", "--cand"):
            cur = ref if a == "--ref" else cand
        elif cur is not None:
            cur.append(a)
    refs = [e for e in map(edges, ref) if e]
    cands = [e for e in map(edges, cand) if e]
    if not refs or not cands:
        print("MIN=0; MED=0; REF=0; RATIO=0; DEV=999; CROPPED=0; N=0"); return
    med = [int(statistics.median(e[i] for e in refs)) for i in range(5)]
    vals = sorted(c[4] for c in cands)
    dev = max(max(abs(c[i] - med[i]) for i in range(4)) for c in cands)
    ratio = vals[0] * 1000 // med[4] if med[4] else 0
    low = sum(1 for c in cands if med[4] and c[4] * 1000 < 900 * med[4])
    print(f"MIN={vals[0]}; MED={int(statistics.median(vals))}; REF={med[4]}; RATIO={ratio}; DEV={dev}; CROPPED={low}; N={len(cands)}")


def strips(path, prefix):
    img = load(path)
    if not img:
        print(f"{prefix}_STRIP_LIT=-1; {prefix}_STRIP_PM=-1"); return
    sw, sh = screen_size(img)
    lines = 0; lit = 0; total = 0
    for x in range(100):
        c = sum(1 for y in range(sh) if is_lit(pixel(img, x, y)))
        lines += c >= sh * 0.02; lit += c; total += sh
    for y in range(40):
        c = sum(1 for x in range(sw) if is_lit(pixel(img, x, y)))
        lines += c >= sw * 0.02
        c2 = sum(1 for x in range(100, sw) if is_lit(pixel(img, x, y)))
        lit += c2; total += sw - 100
    print(f"{prefix}_STRIP_LIT={lines}; {prefix}_STRIP_PM={lit * 1000 // total if total else -1}")


def bars(a, b, rects):
    A, B = load(a), load(b)
    if not A or not B or screen_size(A) != screen_size(B) or not rects:
        print("BAR_PX=0; BAR_DIFF=-1"); return
    sw, sh = screen_size(A)
    n = d = 0
    for r in rects.split(";"):
        try:
            x1, y1, x2, y2 = (int(v) for v in r.split(","))
        except ValueError:
            continue
        for y in range(max(0, y1), min(sh, y2)):
            for x in range(max(0, x1), min(sw, x2)):
                p, q = pixel(A, x, y), pixel(B, x, y)
                n += 1
                d += max(abs(p[0] - q[0]), abs(p[1] - q[1]), abs(p[2] - q[2])) > 16
    print(f"BAR_PX={n}; BAR_DIFF={d * 1000 // n if n else -1}")


cmd = sys.argv[1] if len(sys.argv) > 1 else ""
if cmd == "measure" and len(sys.argv) > 2:
    measure(sys.argv[2])
elif cmd == "box":
    box(sys.argv[2:])
elif cmd == "strips" and len(sys.argv) > 3:
    strips(sys.argv[2], sys.argv[3])
elif cmd == "bars" and len(sys.argv) > 3:
    bars(sys.argv[2], sys.argv[3], sys.argv[4] if len(sys.argv) > 4 else "")
else:
    print("usage: crop_tools.py measure|box|strips|bars ..."); sys.exit(2)
```

**sf_parse.py**
```python
# Shell assignments from `dumpsys SurfaceFlinger` on API 19.
#   SF_OK SV_X SV_Y SV_EW SV_EH SV_TR SV_DEF: the projection's SurfaceView layer (same client as the projection window);
#     EW and EH are size times the transform, so a scaled layer and a sized layer read alike
#   HINT_P1 HINT_P2 HINT_STRIPS: whether the projection window's transparentRegion holds (50,400) and (640,20)
#   BARS BAR_N SV_OVER_BAR: StatusBar and NavigationBar visible rectangles, and whether the SurfaceView layer overlaps one
# usage: sf_parse.py SF_DUMP
import re, sys

try:
    text = open(sys.argv[1], errors="replace").read()
except (OSError, IndexError):
    text = ""
RECT = re.compile(r"^\[\s*(-?\d+),\s*(-?\d+),\s*(-?\d+),\s*(-?\d+)\]")
layers, cur, region = [], None, None
for line in text.splitlines():
    m = re.match(r"\+ Layer 0x[0-9a-fA-F]+ \((.*)\) id=", line)
    if m:
        cur = {"name": m.group(1), "transparent": [], "visible": [], "x": 0, "y": 0, "w": 0, "h": 0,
               "a": 1.0, "d": 1.0, "client": "", "default": ""}
        layers.append(cur); region = None
        continue
    if cur is None:
        continue
    s = line.strip()
    if s.startswith("Region transparentRegion"):
        region = "transparent"; continue
    if s.startswith("Region visibleRegion"):
        region = "visible"; continue
    r = RECT.match(s)
    if region and r:
        cur[region].append(tuple(int(v) for v in r.groups())); continue
    if "pos=(" in s:
        region = None
        p = re.search(r"pos=\((-?[\d.]+),\s*(-?[\d.]+)\)", s)
        z = re.search(r"size=\(\s*(\d+),\s*(\d+)\)", s)
        t = re.search(r"tr=\[\s*(-?[\d.]+),\s*(-?[\d.]+)\]\[\s*(-?[\d.]+),\s*(-?[\d.]+)\]", s)
        if p:
            cur["x"], cur["y"] = round(float(p.group(1))), round(float(p.group(2)))
        if z:
            cur["w"], cur["h"] = int(z.group(1)), int(z.group(2))
        if t:
            cur["a"], cur["d"] = float(t.group(1)), float(t.group(4))
        continue
    c = re.search(r"client=(0x[0-9a-fA-F]+)", s)
    if c and not cur["client"]:
        cur["client"] = c.group(1)
    dz = re.search(r"default-size=\[(\d+)x(\d+)\]", s)
    if dz and not cur["default"]:
        cur["default"] = f"{dz.group(1)}x{dz.group(2)}"


def area(l):
    return sum(max(0, r[2] - r[0]) * max(0, r[3] - r[1]) for r in l["visible"])


def inside(rects, x, y):
    return int(any(r[0] <= x < r[2] and r[1] <= y < r[3] for r in rects))


apps = [l for l in layers if "AapProjectionActivity" in l["name"]]
app = max(apps, key=area) if apps else None
svs = [l for l in layers if l["name"] == "SurfaceView"]
mine = [l for l in svs if app and l["client"] == app["client"]] or svs
sv = max(mine, key=lambda l: l["w"] * l["h"]) if mine else None
out = {"SF_OK": int(bool(app and sv))}
if sv:
    ew, eh = round(sv["w"] * abs(sv["a"])), round(sv["h"] * abs(sv["d"]))
    out.update(SV_X=sv["x"], SV_Y=sv["y"], SV_EW=ew, SV_EH=eh, SV_TR=f"{sv['a']:.2f},{sv['d']:.2f}", SV_DEF=sv["default"])
p1 = inside(app["transparent"], 50, 400) if app else 0
p2 = inside(app["transparent"], 640, 20) if app else 0
out.update(HINT_P1=p1, HINT_P2=p2, HINT_STRIPS=int(p1 and p2))
bars = [r for l in layers if l["name"] in ("StatusBar", "NavigationBar") for r in l["visible"] if r[2] > r[0] and r[3] > r[1]]
out.update(BARS=";".join(",".join(str(v) for v in r) for r in bars), BAR_N=len(bars))
over = 0
if sv:
    sx1, sy1, sx2, sy2 = sv["x"], sv["y"], sv["x"] + out["SV_EW"], sv["y"] + out["SV_EH"]
    over = int(any(min(sx2, r[2]) > max(sx1, r[0]) and min(sy2, r[3]) > max(sy1, r[1]) for r in bars))
out["SV_OVER_BAR"] = over
for k, v in out.items():
    print(f"{k}='{v}'")
```

**hu_parse.py**
```python
# Shell assignments from one head-unit capture window: the lines from marker FROM to marker TO.
# usage: hu_parse.py CAPTURE FROM TO [--prefix P] [--scale SX,SY] [--surf WxH] [--buf WxH]
#   --scale derives the fit the policy must log for a 1280x800 content area from TX's Normal Scale values.
import math, re, sys

args = sys.argv[1:]


def opt(name):
    if name in args:
        i = args.index(name)
        v = args[i + 1] if i + 1 < len(args) else ""
        del args[i:i + 2]
        return v
    return ""


prefix, scale, surf, buf = opt("--prefix"), opt("--scale"), opt("--surf"), opt("--buf")
cap, a, b = (args + ["", "", ""])[:3]
try:
    lines = open(cap, errors="replace").read().splitlines()
except OSError:
    lines = []
win, on = [], False
for line in lines:
    if not on and a and ("AutomationMarker: " + a) in line:
        on = True
    if on:
        win.append(line)
        if b and ("AutomationMarker: " + b) in line:
            break


def cnt(s):
    return sum(1 for line in win if s in line)


FIT = re.compile(r"SurfaceView sized by layout: (\d+)x(\d+) at \((-?\d+),(-?\d+)\), buffer (\d+)x(\d+)")
fits = [m for m in (FIT.search(line) for line in win) if m]
fit_strs = [f"{m.group(1)}x{m.group(2)}@{m.group(3)},{m.group(4)}/{m.group(5)}x{m.group(6)}" for m in fits]
SURF = re.compile(r"Actual surface dimensions: width=(\d+), height=(\d+)")
surfs = [(int(m.group(1)), int(m.group(2))) for m in (SURF.search(line) for line in win) if m]
NORM = re.compile(r"Normal Scale\. scaleX: (-?[\d.Ee-]+), scaleY: (-?[\d.Ee-]+)")
norm = next((m for m in (NORM.search(line) for line in win) if m), None)
INS = re.compile(r"Detected Insets L(-?\d+) T(-?\d+) R(-?\d+) B(-?\d+)")
ins = [m for m in (INS.search(line) for line in win) if m]
RSZ = re.compile(r"SurfaceView parent resized: \d+x\d+ -> (\d+x\d+)")
rsz = [m for m in (RSZ.search(line) for line in win) if m]
expect, ew, eh, el, et = "", 0, 0, 0, 0
if scale and "," in scale:
    try:
        sx, sy = (abs(float(v)) for v in scale.split(",")[:2])
        ew, eh = math.floor(1280 * sx + 0.5), math.floor(800 * sy + 0.5)
        el, et = int((1280 - ew) / 2), int((800 - eh) / 2)   # Kotlin integer division truncates toward zero
        expect = f"{ew}x{eh}@{el},{et}/1280x800"
    except ValueError:
        pass
last = fits[-1] if fits else None
out = {
    "WIN_N": len(win),
    "FIT_N": len(fits),
    "FIT_LAST": fit_strs[-1] if fit_strs else "",
    "FIT_EXPECT": expect,
    "FIT_EXP_W": ew, "FIT_EXP_H": eh, "FIT_EXP_L": el, "FIT_EXP_T": et,
    "FIT_LAST_OK": int(bool(expect) and bool(fit_strs) and fit_strs[-1] == expect),
    "FIT_LAST_L": last.group(3) if last else 999,
    "FIT_LAST_T": last.group(4) if last else 999,
    "FIT_LAST_BUF": f"{last.group(5)}x{last.group(6)}" if last else "",
    "FIT_BUF_MATCH": sum(1 for m in fits if f"{m.group(5)}x{m.group(6)}" == buf),
    "SURF_N": len(surfs),
    "SURF_OK": sum(1 for w, h in surfs if f"{w}x{h}" == surf),
    "SURF_BIG": sum(1 for w, h in surfs if w > 1280 or h > 1280),
    "SURF_LAST": f"{surfs[-1][0]}x{surfs[-1][1]}" if surfs else "",
    "RESIZE_N": cnt("SurfaceView parent resized: "),
    "RESIZE_1280": cnt("SurfaceView parent resized: 1280x800 -> "),
    "MISM": cnt("Surface mismatch! Expected: "),
    "HONEST": sum(1 for line in win if "Honest Init | Mode:" in line and "Anchor: 1280x800 (from " in line),
    "NORMAL": cnt("Normal Scale. scaleX: "),
    "NORMAL_SCALE": f"{norm.group(1)},{norm.group(2)}" if norm else "",
    "WD": cnt("Watchdog: SurfaceView IS valid. Forcing onSurfaceChanged."),
    "DRIFT": cnt("CarScreen: margins drifted from the announced"),
    "MOVED": cnt("CarScreen: canvas moved from the announced"),
    "UICFG": cnt("AA is already running, send corrected via sendUpdateUiConfigRequest"),
    "CONF_P": cnt("[AapProjectionActivity] onConfigurationChanged: orientation=1"),
    "INSETS": cnt("Legacy SystemUI: Detected Insets"),
    "USE_S": cnt("Using SurfaceView"),
    "USE_T": cnt("Using TextureView"),
    "FALL": cnt("Falling back to SurfaceView for this session"),
    "THR": cnt("Throughput over"),
    "NEWSURF": cnt("New surface set: "),
    "INSETS_LAST": ",".join(ins[-1].groups()) if ins else "",
    "RESIZE_TO": rsz[-1].group(1) if rsz else "",
}
for k, v in out.items():
    print(f"{prefix}{k}='{v}'")
```

**crit_count.py**
```python
# Count lines holding NEEDLE between markers FROM and TO, leaving out the 5 s after every marker whose label
# ends in -x, -r, -k2 or -x3 (an exit, a retry, a force-stop): Android Auto's own teardown lands up to 2.2 s later.
# usage: crit_count.py CAPTURE FROM TO NEEDLE. Prints nothing (exit 1) when the window is missing.
import re, sys
from datetime import datetime, timedelta

if len(sys.argv) < 5:
    sys.exit(1)
cap, a, b, needle = sys.argv[1:5]
END = re.compile(r"AutomationMarker: \S*-(?:x|r|k2|x3)\s*$")


def ts(line):
    m = re.match(r"(\d\d-\d\d) (\d\d:\d\d:\d\d\.\d{3})", line)
    return datetime.strptime("2026-" + m.group(1) + " " + m.group(2), "%Y-%m-%d %H:%M:%S.%f") if m else None


try:
    lines = open(cap, errors="replace").read().splitlines()
except OSError:
    sys.exit(1)
win, on = [], False
for line in lines:
    if not on and ("AutomationMarker: " + a) in line:
        on = True
    if on:
        win.append(line)
        if ("AutomationMarker: " + b) in line:
            break
if not win:
    sys.exit(1)
cuts = [t for t in (ts(line) for line in win if END.search(line)) if t]
n = 0
for line in win:
    if needle in line:
        t = ts(line)
        if t and any(c <= t <= c + timedelta(seconds=5) for c in cuts):
            continue
        n += 1
print(n)
```

## 8. Do not re-run

- **`main` on D-SAM** (`dsam-surfaceview-crop` round 1): SurfaceView 396 permille in 3 of 3 sessions, TextureView 957,
  and the layer at `pos=(-319,-139)` unscaled. Same unit, same settings, same day. TX here is the reference against the
  same numbers, so a `main` arm adds no decision.
- **API 24 and up** (the plan's TA, on D-HU): left out. It needs a second head unit and a re-pair of D-POCO with D-HU,
  which round 1 removed. The gate is one comparison, `sdkInt >= 24`, and `SurfaceViewFitPolicyTest` covers both sides of
  it (API 23 gives `Layout`, API 24 gives `ViewScale`). A `main` smoke on D-HU with SurfaceView covers it in passing.
- **GLES** (`view-mode` 2): not on the gated path, and not seen to crop.
- **Touch parity** (S3 in this brief's first draft): settled by the code. `AapProjectionActivity.sendTouchEvent` divides by
  `touchOverlayView`'s width and height, and falls back to the projection view only when the overlay reads 0. The overlay
  is added to `container` with `MATCH_PARENT` after the projection view, so it is 1280x800 in both view modes and takes
  every touch. The candidate diff changes no line of that path. What a touch maps through beside the overlay size is the
  negotiated size and the margins, and S1 grades that both stay at 1280x800 (cond 9 to 14).

## 9. Report back

1. S1: the box in permille for s1, s2 and s3, RATIO and DEV against TX, and `S1-layers.txt` (fit, layer position, size,
   transform and buffer per session).
2. S2, S4, S5: the verdict, the control values (bar overlap, layer start, `HINT_STRIPS`) and the strip or bar numbers
   against the reference.
3. S5 and S6: `S5-lever.txt`, the last L-INSETS values per S5 arm, and S6's L-RESIZE, last L-FIT and last L-SURF lines.

## Objections answered

**O1 (block), S1 read a grade file that does not exist. Accepted and changed.** `rig_session.py` writes only
`ev / f"{run['id']}.grade.json"` (the run loop, beside `grade_run`), as JSON with a `"verdict"` key. Round 1's evidence
holds `TX.grade.json` and no `.grade.txt`. `grade-s1.sh` now reads `TXV` from `$EV/TX.grade.json` with
`python3 -I -c 'import json, sys; ...'`. The run loop grades TX before S1 starts, so the file exists when S1 grades, and
a regrade reads the same file. S1 can now PASS or FAIL, and its stop rule can fire.

**O2 (block), S6 compared against a line TextureView never logs on a resize. Accepted and changed.** Checked:
`TextureProjectionView.onSurfaceTextureSizeChanged` logs `Surface size changed:` and calls `updateScale` only; only
`onSurfaceTextureAvailable` and `addCallback` call `onSurfaceChanged`. `AapProjectionActivity.onConfigurationChanged`
skips `HeadUnitScreenConfig.init` once the resolution is locked. So TR could never log L-SURF after the rotation. The
TR arm is gone. S6 is one SurfaceView session graded on its own expected values: L-RESIZE from 1280x800 (cond 3), the
last L-FIT buffer equal to the resize target, read by the new `RESIZE_TO` field of `hu_parse.py` (cond 5), the last
L-SURF equal to that buffer (cond 6), and the layer's `default-size` equal to it (cond 7). The re-announce lines are a
report only (cond 11): `updateSurfaceDimensions` normalises the surface by orientation first, so a portrait buffer may
or may not move the anchor, and the fix does not decide that. S6 drops from 7 to 4 min.

**O3 (warn), S5's lever, precondition and reference. Accepted in all three parts and changed.**
1. The codes cannot be checked from the dev host: no unit is attached here (`adb devices` lists none). So the run
   checks them itself. `move_canvas` reads `mCurrentFocus` after `service call statusbar 1`. If the focus is on
   `StatusBar`, it closes the shade with code 2. If not, it prints `OPERATOR NOW` and waits 90 s for the operator to
   pull the shade, which is the plan's fallback. The S5 run line carries the `hand=` cue, and `S5-lever.txt` names the
   lever per arm. A wrong code now costs one hand step, not the run.
2. Checked: `SystemUI.applyInsets` below API 21 logs `Detected Insets L$insetL T$insetT R$insetR B$insetB`, passes
   `manual + inset` to `HeadUnitScreenConfig.updateInsets`, and then removes its listener, so one transient bar skews
   the canvas for the session. `hu_parse.py` now reads the last values as `INSETS_LAST`, and S5 is INCONCLUSIVE unless
   they read `0,0,0,0`. A TM arm (TextureView, same import, same lever) now runs before the SurfaceView arm and is the
   cond 9 reference. S4's TI stays the strip reference for cond 10, because a TextureView clips the strips. If TM gives
   no clean reference, cond 9 falls back to TI, and a cond 9 miss against TI grades INCONCLUSIVE, not FAIL. S5 grows
   from 5 to 8 min.
3. Checked: `AutomationEffectRunner.importSettings` puts `result.importedKeys`, an `Int`, in every reply. Cond 2 and
   the fallback test in `move_canvas` now match `"imported":2`.

**O4 (warn), S3 could not fail for the reason its Decision named. Accepted; S3 is removed.** Checked:
`sendTouchEvent` takes `overlay?.width` and `overlay?.height` and uses the projection view only when they read 0. The
overlay is added `MATCH_PARENT` to `container`, and the candidate diff touches neither it nor the touch path. The
other inputs to `TouchCoordinateMapper.map` are the negotiated size and the margins, which S1 already grades (L-SURF,
L-INIT, L-MISM, the cached surface size). So S3's PASS was fixed before the run. Section 8 now carries the code read.
The round loses 6 min and both injected touches, and `touch_overlay`, `ui_target.py`, the VERBOSE arm and the
`Touch map: raw=` string are gone with it.

