# surfaceview-crop, round 2 brief: does SurfaceView in Normal screen mode sit where its own margins put it

Published name: `surfaceview-crop-round2-brief.md`. Results go in `surfaceview-crop-round2-results.md`.
Evidence: release `rig-evidence-surfaceview-crop`, new asset `surfaceview-crop-round2-captures.zip`.

**One point.** Round 1 passed S1, S4, S5 and S6 and failed one condition of S2: in Normal screen mode the SurfaceView
picture reached 18 px further toward the bottom of the screen than the TextureView reference (TN). The two sessions
ended with different margins on the wire. Android Auto drew TN's picture inside 231 margin rows and the SurfaceView's
inside 209. This round asks one question on the same build and the same units: once each session is graded against its
own last margins, does the SurfaceView picture sit where TextureView's sits? Six sessions, alternating, so the round
sees each arm more than once and likely sees both margin values.

Units: D-SAM (head unit, API 19) with D-POCO (phone). D-HU Bluetooth held off, D-MOTO radios off. Nothing else.
Hand steps: none. Injected input: none.
Time: build 5 to 20 min, setup 6, S2 18, cleanup 4, plus thermal waits. **About 35 min.**
No other queued round shares this base (`main` `c2c0e95aa`). The Queue line names D-SAM and D-POCO, so a round on D-HU
and D-MOTO can pair with it (§3b).

```rig-requires
lib 0f6b5a7a1
unit D-SAM role=headunit wifi=on bt=any battery=50
unit D-POCO role=phone wifi=on bt=on nobond=D-HU aa=D-SAM battery=30
unit D-HU bt=off
unit D-MOTO wifi=off bt=off
candidate T ff21d2716
run setup cap=420 script=setup.sh grade=grade-setup.sh
run S2 cap=3000 script=s2.sh grade=grade-s2.sh point=yes
run cleanup cap=600 script=cleanup.sh grade=grade-cleanup.sh always=yes
stop setup FAIL,INCONCLUSIVE,UNTESTABLE skip=all
```

`bt=any` on D-SAM: API 19 prints no `state:` line, so a read-back reads `unknown`. The pairs, bonds and Android Auto
setup are the ones round 1 used, so the operator has nothing to change. Round 1's `hardware` line for a separate D-SAM
supply is gone: D-SAM now charges from the PC port (`rig-quirks/units/D-SAM.md`, "Power").

| Run | Arms (`view-mode`) | What it decides | Min |
|---|---|---|---|
| setup | | T on D-SAM, keys written, settings saved | 6 |
| **S2** | tn1 sv1 tn2 sv2 tn3 sv3; TextureView (1) and SurfaceView (0); screen mode Normal | **the point: the SurfaceView picture matches TN once each session's own margins are taken out** | 18 |
| cleanup | | network and settings given back | 4 |

## 1. Build and baseline

| Arm | Ref | Commit | Tree |
|---|---|---|---|
| T | `fix/surfaceview-crop` | `ff21d27165c125e4d3d93e51744e335eed4a9588` | `6715bb42ff7bc375c71bc24e00c765f120671c49` |

The same build as round 1. One commit on `main` `c2c0e95aa`. No history was rewritten and no fixup was added: the coding
side read the S2 FAIL as a fault of the comparison, not of the code (section 2).

```text
WT=$("$RIG_TOOLKIT/wt-new.sh" surfaceview-crop candidate ff21d27165c125e4d3d93e51744e335eed4a9588 | tail -1)
git -C "$WT" rev-parse 'HEAD^{tree}' > "$EV/build-tree.txt"     # MUST read 6715bb42ff7bc375c71bc24e00c765f120671c49
"$RIG_TOOLKIT/rig_thermal.sh" wait 70
HUR_DIR="$WT" MAX_WORKERS=2 "$RIG_TOOLKIT/build_hur_cool.sh"    # then copy the APK from rig-data/apks/ to $EV/apk/T.apk
```

The dev host ran the JVM suite at `ff21d2716`: 4390 tests, 0 failures. No rig unit tests.

## 2. What this is and why

**The fix under test.** Below API 24, `ProjectionViewScaler.updateScale` now sizes a SurfaceView by its layout
(`SurfaceViewFitPolicy`) instead of the view scale, because a pre-N SurfaceView layer takes the view's moved origin but
not its scale. Round 1 proved the main case: S1 lit 949 to 991 permille of the TextureView box, against 396 on `main`.

**Round 1's S2 FAIL.** In Normal mode (`fullscreen-mode` 0) the lit box was TN cols 28-798 and SurfaceView cols 10-798,
rows 0-1279 in both. The buffer is portrait, so screen (x, y) is buffer row x, column 799-y. Col 798 is screen y 1, the
status bar, and is the same in both. The edge that moved is the bottom of the picture: screen y 771 for TN, 789 for
SurfaceView.

**The cause, from round 1's `S2-hu.txt`.** In Normal mode `HeadUnitScreenConfig.recalculate` alternates between
`CarScreen: usable area 1280x734` and `CarScreen: usable area 1280x767` (734 is 800 less the 33 px bar twice). Each
change logs `CarScreen: margins drifted from the announced` and sends an `UpdateUiConfigRequest`. The last one sent
decides where Android Auto draws:

- TN: last `[UI_DEBUG_FIX] TX UpdateUiConfigRequest: L=213 T=115 R=214 B=116` (231 margin rows).
- SurfaceView: last `[UI_DEBUG_FIX] TX UpdateUiConfigRequest: L=213 T=104 R=214 B=105` (209 margin rows).

Both arms ran the same scale (`Normal Scale. scaleX: 1.5005862, scaleY: 1.409002` and `SurfaceView sized by layout:
1921x1081 at (-320,-157), buffer 1280x767, scale 1.5005862x1.409002`), so both put 720 video rows over 1081 screen rows,
K = 1081 / 720 = 1.5014 px per row. TN's picture therefore ends (116 - 105) x 1.5014 = 16.5 px higher, against 18 px
measured (the measure steps by 2 px). The race is in `HeadUnitScreenConfig`, which this branch does not touch, and
TextureView runs it on `main` too. It is a separate defect and needs its own ticket.

**The model, checked on round 1's own frames.** If the margins explain the box, E = (bottom edge in screen y) + B x K is
the same in every session, where B is the last bottom margin sent before the screenshot. `margin_fit.py` (section 7) on
round 1's `S2-tn-lit.txt` and `S2-sv-lit.txt` gives E = 945.2 for TN and 946.6 for SurfaceView, 1.4 px apart. One pair
fits the model, but one pair cannot prove it. This round measures six sessions and grades every one against it.

**What a PASS would look like if the fix did nothing.** On `main` the SurfaceView lights 396 permille, with its bottom
edge hundreds of pixels above TN's. E would then differ by far more than 8 px, so S2 cannot pass on an unfixed build.

## 3. What is different about this round

- **Only S2 runs, with three sessions per arm in the order tn1 sv1 tn2 sv2 tn3 sv3.** Alternating spreads any slow drift
  of the phone's screen over both arms.
- **Every session is graded against its own last margins.** A `-shot` marker goes down just before each screenshot. The
  window from `-go` to `-shot` gives the last `TX UpdateUiConfigRequest:` line, the video size (`Received video
  dimensions:`) and the scale. A session with no `TX UpdateUiConfigRequest:` line, no video size, or a scale other than
  the SurfaceView arm's common one is left out and counted (`EXCL`).
- **The model has its own control.** TN's three E values must agree within 8 px (`TN_SPREAD`). If they do not, the margins
  do not explain TextureView's own boxes, and S2 grades INCONCLUSIVE, not FAIL. One cause would be a different Android
  Auto screen at the bottom edge in one session: the lit edge sits about 12 px inside the content end in both round 1
  arms, so it depends on what Android Auto draws there.
- **A matched pair is a second check.** Where a SurfaceView session and a TN session ended on the same bottom margin, their
  raw bottom edges must agree within 8 px with no model at all (`PAIR_DEV`). Graded only when such a pair exists.
- **The settle wait is a line, then 10 s.** Each session waits for `Received video dimensions:` (capped at 60 s), then
  10 s for the margin re-announces to stop. The race has no last line to wait on, so the 10 s is the measured settle:
  round 1's last re-announce came 0.1 to 0.5 s after the video size.
- **The phone capture is graded.** `Critical error` on D-POCO stays 0 outside the 5 s after each exit, and the phone
  capture must hold the same `AutomationMarker: S2-` lines as D-SAM's, which proves the phone window is real.
- **D-SAM's logcat buffer is not cleared by `logcat -c`.** Every count is inside a marker window.
- **D-SAM's station is held off before each launch** (`hold_station_off`, as in round 1). `cleanup` gives it back.
- **D-SAM's clock is about 27 s behind the host.** Every grade is inside one unit's capture, so no correction is needed.
- **The rig's audio settings stay as they are** (AAC on, a short queue). They are a deliberate worst case and not this
  round's subject, so `KEYS` does not write them.
- Read `rig-quirks/topics/tooling.md`, `topics/projection.md`, `units/D-SAM.md`, `units/D-SAM-and-D-HP.md`, `units/D-POCO.md`.

## 4. Settings keys

**D-SAM**, written by `write_keys` with the app stopped and read back into `$EV/<run>-<tag>-settings.xml`:

| Key | Type | Value |
|---|---|---|
| `onboarding-version` | int | 2 |
| `allow-external-configuration` | boolean | true |
| `enable-audio-sink` | boolean | true |
| `narrow-band-profile-cap`, `kill-on-disconnect`, `enable-floating-button`, `external-bt-zbt-transport`, `forced_scale`, `hud_mirroring`, `wifi-direct-rotation-pending` | boolean | false |
| `native-poke-all-paired`, `wifi-direct-stable-identity` | boolean | true |
| `connection-modes` | set | `wifi` |
| `wifi-connection-mode` | int | 3 |
| `native-ap-transport` | int | 0 |
| `resolutionId` | int | 3 |
| `video-fit-mode` | int | 0 (FILL) |
| `dpi-pixel-density` | int | 175 |
| `inset-left`, `inset-top`, `inset-right`, `inset-bottom` | int | 0 |
| `view-mode` | int | `VIEW`: 1 TEXTURE (tn), 0 SURFACE (sv) |
| `fullscreen-mode` | int | `FS`: 1 IMMERSIVE in setup; 0 NONE in S2 |
| `screen-orientation` | int | 2 LANDSCAPE |
| `log-level` | int | 1 DEBUG |
| `video-profile-starvation-cap`, `native-poke-bt-macs`, `native-mac`, `last-connected-native-mac`, `native-preferred-device-mac` | | delete |
| `station-stand-down-network-id` | | delete before each launch; written back in cleanup |

## 5. The deciding lines

App strings checked with `git grep -F` in `app/src/main` at `ff21d2716`: all exist. `hu_parse.py` reads them in
`$EV/S2-hu.txt` from `S2-<tag>-go` to `S2-<tag>-shot`. Phone lines are in `$EV/S2-ph.txt`.

| Id | Line (literal part) | Level | Read |
|---|---|---|---|
| L-SSL | `SSL handshake complete` | INFO | a session formed |
| L-THR | `Throughput over` | INFO | the decoder renders |
| L-USE-S / L-USE-T | `Using SurfaceView` / `Using TextureView` | INFO | the view the activity built |
| L-FALL | `Falling back to SurfaceView for this session` | WARN | TextureView gave up by itself |
| L-FIT | `SurfaceView sized by layout: ` + `WxH at (L,T), buffer BWxBH, scale SXxSY` | INFO | the SurfaceView fit, its height and its scale |
| L-NORM | `Normal Scale. scaleX: ` | INFO | the TextureView scale (the last one in the window) |
| L-TXM | `TX UpdateUiConfigRequest: L=` + `L T R B` | INFO | the margins sent; the last one before `-shot` is the session's B |
| L-VID | `Received video dimensions: ` + `WxH` | INFO | the video height for K; also the settle trigger |
| L-USABLE | `CarScreen: usable area ` + `1280x734` or `1280x767` | INFO | report: how often each side of the race fired |
| L-DRIFT | `CarScreen: margins drifted from the announced` | INFO | report: re-announces |
| L-GIVE | `WiFi network is enabled again and should rejoin` | INFO | cleanup |
| S-CRASH | `Process: com.andrerinas.headunitrevived` | system | a `FATAL EXCEPTION` block |
| S-MARK | `AutomationMarker: S2-` | system / phone | the marker lines on both units |
| P-CRIT | `Critical error` | phone | Android Auto ended the session; 5 s after exits left out |

## 6. Runs

### setup. Known state

**setup.sh**
```bash
rl_step "D-SAM: back up, install T, write the keys"
adb -s "$SAM" shell am force-stop "$PKG"
[ -s "$EV/settings-backup-sam.xml" ] || adb -s "$SAM" shell run-as "$PKG" cat shared_prefs/settings.xml > "$EV/settings-backup-sam.xml"
install_apk "$SAM" "$APK_T" setup
send ACTION_QUERY_STATE > "$EV/setup-query.txt" 2>&1
VIEW=1; FS=1
write_keys base
adb -s "$SAM" shell getprop ro.build.version.sdk > "$EV/setup-sdk.txt"
adb -s "$SAM" shell dumpsys battery > "$EV/setup-battery.txt"
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
```

- **PASS** when all five hold. Any other verdict stops the round; cleanup still runs.

**Decision:** PASS lets S2 run on T. FAIL stops the round: fix the install or the keys and start again.

### S2. Normal screen mode, graded against each session's own margins (the point)

Six sessions, `fullscreen-mode` 0: tn1, sv1, tn2, sv2, tn3, sv3. A TextureView session (tn) is the reference; a
SurfaceView session (sv) is the arm under test. Each session launches, waits for a rendering decoder and the video size,
settles 10 s, marks `-shot`, takes the screenshot and the dumps, then exits.

**s2.sh**
```bash
apk_check "$SAM" "$APK_T" S2
run_open || exit 0
FS=0
for TAG in tn1 sv1 tn2 sv2 tn3 sv3; do
  VIEW=1; case "$TAG" in sv*) VIEW=0 ;; esac
  write_keys "$TAG"
  if launch "$TAG"; then
    rl_wait_line "$EV/$RUN-hu.txt" 'Received video dimensions: ' 60 "$LAUNCH_FROM" > "$EV/$RUN-$TAG-vid.txt"
    rl_sleep 10
    mark "$RUN-$TAG-shot"
    shot "$RUN-$TAG" keep
  fi
  mark "$RUN-$TAG-x"; app_exit
done
mark "$RUN-end"
run_close
```

**grade-s2.sh**
```bash
[ -s "$EV/S2-thermal.txt" ] && { rl_verdict UNTESTABLE "host thermal: the wait failed"; exit 0; }
v_counts S2 S2-tn1-go S2-end
: > "$EV/S2-table.txt"; : > "$EV/S2-sessions.txt"
SVN=0; SV_USES=0; TN_FALL=0; OVER=0; BARN=0; BARMAX=-1; PREV=""; OUT=0; TN_MIDS=""; SV_MIDS=""
for TAG in tn1 sv1 tn2 sv2 tn3 sv3; do
  why=""; WANT=1; case "$TAG" in sv*) WANT=0 ;; esac
  if ! grep -q "^$TAG ssl=1" "$EV/S2-gate.txt" 2>/dev/null; then why="no SSL"
  elif grep -q "^$TAG thr=0" "$EV/S2-gate.txt"; then why="no Throughput over after SSL"
  elif [ ! -s "$EV/S2-$TAG-lit.txt" ]; then why="no lit file"
  fi
  if [ -z "$why" ]; then
    eval "$(python3 -I "$SCR/hu_parse.py" "$EV/S2-hu.txt" "S2-$TAG-go" "S2-$TAG-shot")"
    USE=${USE_T:-0}; [ "$WANT" = 0 ] && USE=${USE_S:-0}
    if [ "$USE" -lt 1 ]; then why="no Using line for its view mode"
    elif [ "${THR:-0}" -lt 1 ]; then why="no Throughput over between -go and -shot"
    elif ! grep -aq "name=\"view-mode\" value=\"$WANT\"" "$EV/S2-$TAG-settings.xml" 2>/dev/null; then why="settings read-back is not view-mode $WANT"   # not-literal: a settings.xml line
    fi
  fi
  if [ -n "$why" ]; then echo "$TAG left out: $why" >> "$EV/S2-sessions.txt"; OUT=$((OUT + 1)); continue; fi
  unset SV_X SV_Y SV_EW SV_EH SV_DEF BARS BAR_N SV_OVER_BAR
  eval "$(python3 -I "$SCR/sf_parse.py" "$EV/S2-$TAG-sf.txt")"
  python3 -I "$SCR/crop_tools.py" vmid "$EV/S2-$TAG.raw" "${BARS:-}" > "$EV/S2-$TAG-mid.txt"
  case "$TAG" in
    tn*)
      echo "$TAG tn $EV/S2-$TAG-lit.txt $TXM_LAST $VID_LAST $NORMAL_LAST_SCALE -" >> "$EV/S2-table.txt"
      [ "${FALL:-0}" -ge 1 ] && TN_FALL=$((TN_FALL + 1))
      TN_MIDS="$TN_MIDS $EV/S2-$TAG-mid.txt"; PREV=$TAG ;;
    sv*)
      echo "$TAG sv $EV/S2-$TAG-lit.txt $TXM_LAST $VID_LAST $FIT_LAST_SCALE $FIT_LAST_H" >> "$EV/S2-table.txt"
      SVN=$((SVN + 1)); [ "${USE_S:-0}" -ge 1 ] && SV_USES=$((SV_USES + 1))
      SV_MIDS="$SV_MIDS $EV/S2-$TAG-mid.txt"
      [ "${SV_OVER_BAR:-0}" = 1 ] && OVER=$((OVER + 1))
      [ "${BAR_N:-0}" -ge 1 ] && BARN=$((BARN + 1))
      if [ -n "$PREV" ]; then
        eval "$(python3 -I "$SCR/crop_tools.py" bars "$EV/S2-$PREV.raw" "$EV/S2-$TAG.raw" "${BARS:-}")"
        [ "${BAR_DIFF:--1}" -gt "$BARMAX" ] && BARMAX=$BAR_DIFF
      fi ;;
  esac
  echo "$TAG txm=$TXM_LAST vid=$VID_LAST fit=${FIT_LAST:-none} fitscale=$FIT_LAST_SCALE normscale=$NORMAL_LAST_SCALE usable734=$USABLE_734 usable767=$USABLE_767 drift=$DRIFT layer=${SV_X:-?},${SV_Y:-?} ${SV_EW:-?}x${SV_EH:-?} default=${SV_DEF:-?} $(cat "$EV/S2-$TAG-mid.txt")" >> "$EV/S2-sessions.txt"
done
eval "$(python3 -I "$SCR/margin_fit.py" "$EV/S2-table.txt" 2>> "$EV/S2-sessions.txt")"
eval "$(python3 -I "$SCR/crop_tools.py" mids --ref $TN_MIDS --cand $SV_MIDS)"
HU_MARKS=$(rl_cnt "$EV/S2-hu.txt" S2-tn1-go S2-end 'AutomationMarker: S2-')   # system
PH_MARKS=$(rl_cnt "$EV/S2-ph.txt" S2-tn1-go S2-end 'AutomationMarker: S2-')   # phone
rl_cond 1 eq "$MD5" 1 'T on D-SAM (md5)'
rl_cond 2 ge "$(grep -ac 'name="fullscreen-mode" value="0"' "$EV/S2-sv1-settings.xml")" 1 'fullscreen-mode 0 read back'  # not-literal
rl_cond 3 eq "$SV_USES" "$SVN" "L-USE-S in every SurfaceView session that rendered ($SV_USES of $SVN)"
rl_cond 4 eq "$TN_FALL" 0 'L-FALL 0: no TextureView session fell back to SurfaceView'
rl_cond 5 eq "$OVER" "$SVN" "control: the SurfaceView layer overlaps a bar layer in every SurfaceView session ($OVER of $SVN)"
rl_cond 6 le "$BARMAX" 20 "bar pixels that differ from the TN session before, worst permille (round 1: 4)"
rl_cond 7 le "${MID_DEV:-999}" 8 "every SurfaceView picture midpoint within 8 px of TN's median midpoint $MID_REF (round 1: 416.0 against 415.5)"
rl_cond 8 le "${ROW_DEV:-999}" 8 'every SurfaceView left and right edge within 8 px of TN median'
if [ "${PAIR_N:-0}" -ge 1 ]; then
  rl_cond 9 le "$PAIR_DEV" 8 "matched pairs ($PAIR_N with equal bottom margins): raw bottom edges within 8 px"
else
  rl_cond 9 ge 0 0 'report: no SurfaceView and TN session ended on the same bottom margin'
fi
rl_cond 10 eq "$CRIT" 0 'phone: Critical error, 5 s after exits left out'
rl_cond 11 eq "$PH_MARKS" "$HU_MARKS" "phone capture holds the same S2 markers as D-SAM's ($PH_MARKS against $HU_MARKS)"
rl_cond 12 eq "$CRASH" 0 'no FATAL EXCEPTION for com.andrerinas on D-SAM'
rl_cond 13 ge "${TN_SPREAD:-999}" 0 "report: margin model, TN E spread $TN_SPREAD px, worst SurfaceView E offset $SV_DEV px (K $K, bottom margins $BS, scale $SCALE_Y)"
rl_cond 14 ge "$OUT" 0 "report: sessions left out at admission $OUT, clipped at a bar $MID_CLIP, picture height TN $HGT_TN and SurfaceView $HGT_SV (see S2-sessions.txt)"
if [ "${MID_TN:-0}" -lt 2 ] || [ "${MID_SV:-0}" -lt 2 ]; then rl_verdict UNTESTABLE "session gate: TN $MID_TN and SurfaceView $MID_SV admitted unclipped sessions, 2 each needed ($OUT left out, see S2-sessions.txt)"
elif [ "$BARN" -lt "$SVN" ]; then rl_verdict INCONCLUSIVE "a SurfaceView dump names no StatusBar or NavigationBar layer, so Normal mode was not in force"
elif [ "$OVER" -lt "$SVN" ]; then rl_verdict INCONCLUSIVE "control failed: a SurfaceView layer does not reach the bar, so that session tests nothing"
elif [ "${MID_SPREAD:-999}" -gt 8 ]; then rl_verdict INCONCLUSIVE "TN's own midpoints spread $MID_SPREAD px, so there is no reference"
fi
```

- **Admission.** A session counts only with SSL, no `thr=0` gate line, its arm's `Using` line and at least one
  `Throughput over` line between `-go` and `-shot`, and its arm's `view-mode` in its settings read-back. A picture
  whose top or bottom lit row touches a bar or the screen edge is clipped and left out of the midpoint. Each session left
  out is named with its reason in `S2-sessions.txt`.
- **PASS** when all graded conditions hold: at least two admitted unclipped sessions per arm, TN's midpoints agree within
  8 px, every SurfaceView midpoint is within 8 px of TN's median, and the bar pixels are unchanged. The margin model (E)
  is a report line only.
- **FAIL** when TN agrees with itself and a SurfaceView midpoint, its left or right edge, a matched pair, or the bar
  pixels differ. Report the session, its PNG and its `S2-sessions.txt` line.
- **INCONCLUSIVE** when TN's own midpoints spread above 8 px, a dump shows no bar, or the layer does not reach the bar.
  **UNTESTABLE** when fewer than two admitted unclipped sessions per arm remain, or the host stays hot.

**Decision:** PASS means the SurfaceView picture sits where TextureView's sits in Normal mode, so the round 1 S2
difference was the margin race in `HeadUnitScreenConfig` (it changes the picture's height, not its place), not the layout fit: the
`contentFillsScreen` gate does not go in, and the branch goes to review as it is, with the race filed as its own ticket.
FAIL means the SurfaceView picture sits off its own margins in Normal mode: the fit's offset under the status bar is
wrong, so re-plan from the failing session's L-FIT line and SurfaceFlinger layer against TN, and add the
`contentFillsScreen` gate if the fit cannot be made to match. INCONCLUSIVE on TN's spread means TextureView itself does
not hold one place in Normal mode: re-open the S2 cause from the TN sessions before the SurfaceView arm can be judged.

### cleanup. D-SAM back to a known state (always runs)

**cleanup.sh**
```bash
fail=0
rl_step "D-SAM: network and settings back"
adb -s "$SAM" shell am force-stop "$PKG"
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
adb -s "$SAM" shell rm -f /sdcard/ohu-crop.raw /sdcard/ohu-crop.png
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
```

- **PASS** when both hold. A FAIL leaves a note for the next D-SAM round and changes no other verdict. T stays
  installed on D-SAM; the next round installs its own build.

**Decision:** PASS leaves D-SAM at its baseline for the next round. FAIL means the next D-SAM round restores the network
and settings by hand first.

## 7. Scripts

`crop_tools.py`, `sf_parse.py` and `crit_count.py` are round 1's, unchanged. `hu_parse.py` is cut down to the fields S2
reads and adds L-TXM, L-VID and the L-FIT scale. `margin_fit.py` is new.

**lib.sh**
```bash
# Serials, paths, verbs and session helpers for surfaceview-crop round 2. Sourced before every script and grade.
source /home/oscar/Coding/StudioProjects/rig-toolkit/rig_devices.sh
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
MAIN=$PKG/com.andrerinas.openheadunit.main.MainActivity
SAM=$D_SAM; PH=$D_POCO; DHU=$D_HU; MOTO=$D_MOTO
SCR=$EV/scripts; APK_T=$EV/apk/T.apk
T_SHA=ff21d2716
KEYS="scalar:int:onboarding-version:2 scalar:boolean:allow-external-configuration:true scalar:boolean:enable-audio-sink:true
 scalar:boolean:narrow-band-profile-cap:false scalar:boolean:kill-on-disconnect:false scalar:boolean:enable-floating-button:false
 set:connection-modes:wifi scalar:int:wifi-connection-mode:3 scalar:int:native-ap-transport:0
 scalar:boolean:native-poke-all-paired:true scalar:boolean:external-bt-zbt-transport:false
 scalar:boolean:wifi-direct-stable-identity:true scalar:boolean:wifi-direct-rotation-pending:false
 scalar:int:resolutionId:3 scalar:int:video-fit-mode:0 scalar:int:dpi-pixel-density:175
 scalar:boolean:forced_scale:false scalar:boolean:hud_mirroring:false
 scalar:int:inset-left:0 scalar:int:inset-top:0 scalar:int:inset-right:0 scalar:int:inset-bottom:0
 scalar:int:screen-orientation:2 scalar:int:log-level:1
 del:video-profile-starvation-cap del:native-poke-bt-macs del:native-mac del:last-connected-native-mac
 del:native-preferred-device-mac"
VIEW=1; FS=1; LAUNCH_FROM=0   # the arm keys; each script sets them before write_keys

send() { local a=$1; shift; adb -s "$SAM" shell am broadcast -f 0x00000020 -n "$RX" -a "com.andrerinas.openheadunit.$a" "$@"; }
mark() {   # mark LABEL: the same marker line on D-SAM and D-POCO
  adb -s "$SAM" shell log -t AutomationMarker "$1"; adb -s "$PH" shell log -t AutomationMarker "$1"
  echo "$1 $(date '+%m-%d %H:%M:%S')" >> "$EV/marks.txt"
}
write_keys() {   # write_keys TAG: section 4's keys plus the arm keys, app stopped, then the file read back
  adb -s "$SAM" shell am force-stop "$PKG"
  "$RIG_TOOLKIT/set_prefs_runas_host.py" "$SAM" $KEYS "scalar:int:view-mode:$VIEW" "scalar:int:fullscreen-mode:$FS" \
    > "$EV/$RUN-$1-keys.txt" 2>&1
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
  local att
  for att in 1 2; do
    rl_step "$1, attempt $att"
    phone_link off
    adb -s "$SAM" shell am force-stop "$PKG"
    hold_station_off
    mark "$RUN-$1-go"; LAUNCH_FROM=$(rl_offset "$EV/$RUN-hu.txt")
    adb -s "$SAM" shell am start -n "$MAIN" >/dev/null
    rl_sleep 2; phone_link on
    see_picture "$1" "$LAUNCH_FROM" && return 0
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
v_counts() {   # v_counts RUN FROM TO: identity, crashes and phone Critical error inside FROM..TO
  CRASH=$(rl_cnt "$EV/$1-hu.txt" "$2" "$3" 'Process: com.andrerinas.headunitrevived')   # system
  CRIT=$(python3 -I "$SCR/crit_count.py" "$EV/$1-ph.txt" "$2" "$3" 'Critical error')   # phone
  MD5=$(grep -c '^md5match=1' "$EV/$1-identity.txt" 2>/dev/null)   # not-literal
}
```

**crop_tools.py**
```python
# Screen measures from a raw screencap (header, then RGBA_8888) and the comparisons the grades use.
#   measure RAW                      one line: w= h= rows=a-b cols=c-d box_permille=N (round 1's lit box; exit 2: not raw)
#   box --ref LIT... --cand LIT...   MIN MED (candidate boxes) REF (reference median) RATIO DEV (worst edge offset) CROPPED N
#   strips RAW PREFIX                PREFIX_STRIP_LIT (lit lines in x<100 and y<40) and PREFIX_STRIP_PM (lit pixels, permille)
#   bars RAW_A RAW_B RECTS           BAR_PX and BAR_DIFF (permille of bar pixels whose max channel differs by more than 16)
#   vmid RAW RECTS                   TOP BOT MID HGT CLIP: the picture's lit rows between the bars (CLIP 1: it touches a bar or the edge)
#   mids --ref MID... --cand MID...  MID_TN MID_SV MID_CLIP MID_REF MID_SPREAD MID_DEV HGT_TN HGT_SV, from vmid files
# A portrait buffer (w < h) is D-SAM's landscape screen turned once: screen (x, y) is buffer row x, column w-1-y.
import math, re, statistics, struct, sys


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


def vmid(path, rects):
    # A margin change moves the top and bottom edges apart; a placement error moves both the same way.
    img = load(path)
    if not img:
        print("TOP=-1; BOT=-1; MID=-1; HGT=0; CLIP=1"); return
    sw, sh = screen_size(img)
    top, bot = 0, sh
    for r in (rects or "").split(";"):
        try:
            x1, y1, x2, y2 = (int(v) for v in r.split(","))
        except ValueError:
            continue
        if x2 - x1 < sw // 2:
            continue
        if y1 <= 0:
            top = max(top, y2)
        elif y2 >= sh:
            bot = min(bot, y1)
    lit = [y for y in range(top, bot) if 2 * sum(1 for x in range(0, sw, 2) if is_lit(pixel(img, x, y))) >= sw * 0.02]
    if not lit:
        print("TOP=-1; BOT=-1; MID=-1; HGT=0; CLIP=1"); return
    t, b = lit[0], lit[-1]
    print(f"TOP={t}; BOT={b}; MID={(t + b) / 2:.1f}; HGT={b - t + 1}; CLIP={int(t <= top or b >= bot - 1)}")


def mids(args):
    ref, cand, cur = [], [], None
    for a in args:
        if a in ("--ref", "--cand"):
            cur = ref if a == "--ref" else cand
        elif cur is not None:
            cur.append(a)

    def rd(p):
        try:
            t = open(p, errors="replace").read()
        except OSError:
            return None
        m = re.search(r"MID=(-?[\d.]+); HGT=(\d+); CLIP=(\d)", t)
        if not m or m.group(3) == "1" or float(m.group(1)) < 0:
            return None
        return float(m.group(1)), int(m.group(2))
    r = [x for x in map(rd, ref) if x]
    c = [x for x in map(rd, cand) if x]
    clip = len(ref) + len(cand) - len(r) - len(c)
    if not r or not c:
        print(f"MID_TN={len(r)}; MID_SV={len(c)}; MID_CLIP={clip}; MID_REF=0; MID_SPREAD=999; MID_DEV=999; HGT_TN=0; HGT_SV=0")
        return
    med = statistics.median(x[0] for x in r)
    spread = math.ceil(max(x[0] for x in r) - min(x[0] for x in r))
    dev = math.ceil(max(abs(x[0] - med) for x in c))
    print(f"MID_TN={len(r)}; MID_SV={len(c)}; MID_CLIP={clip}; MID_REF={med:.1f}; MID_SPREAD={spread}; MID_DEV={dev}; "
          f"HGT_TN={int(statistics.median(x[1] for x in r))}; HGT_SV={int(statistics.median(x[1] for x in c))}")


cmd = sys.argv[1] if len(sys.argv) > 1 else ""
if cmd == "measure" and len(sys.argv) > 2:
    measure(sys.argv[2])
elif cmd == "box":
    box(sys.argv[2:])
elif cmd == "strips" and len(sys.argv) > 3:
    strips(sys.argv[2], sys.argv[3])
elif cmd == "bars" and len(sys.argv) > 3:
    bars(sys.argv[2], sys.argv[3], sys.argv[4] if len(sys.argv) > 4 else "")
elif cmd == "vmid" and len(sys.argv) > 2:
    vmid(sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else "")
elif cmd == "mids":
    mids(sys.argv[2:])
else:
    print("usage: crop_tools.py measure|box|strips|bars|vmid|mids ..."); sys.exit(2)
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
# usage: hu_parse.py CAPTURE FROM TO [--prefix P]
import re, sys

args = sys.argv[1:]
prefix = ""
if "--prefix" in args:
    i = args.index("--prefix")
    prefix = args[i + 1] if i + 1 < len(args) else ""
    del args[i:i + 2]
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


def last(rx):
    found = [m for m in (rx.search(line) for line in win) if m]
    return found[-1] if found else None


fit = last(re.compile(r"SurfaceView sized by layout: (\d+)x(\d+) at \((-?\d+),(-?\d+)\), buffer (\d+)x(\d+), scale (-?[\d.Ee-]+)x(-?[\d.Ee-]+)"))
norm = last(re.compile(r"Normal Scale\. scaleX: (-?[\d.Ee-]+), scaleY: (-?[\d.Ee-]+)"))
txm = last(re.compile(r"TX UpdateUiConfigRequest: L=(-?\d+) T=(-?\d+) R=(-?\d+) B=(-?\d+)"))
vid = last(re.compile(r"Received video dimensions: (\d+)x(\d+)"))
out = {
    "WIN_N": len(win),
    "USE_S": cnt("Using SurfaceView"),
    "USE_T": cnt("Using TextureView"),
    "FALL": cnt("Falling back to SurfaceView for this session"),
    "THR": cnt("Throughput over"),
    "FIT_N": cnt("SurfaceView sized by layout: "),
    "FIT_LAST": f"{fit.group(1)}x{fit.group(2)}@{fit.group(3)},{fit.group(4)}/{fit.group(5)}x{fit.group(6)}" if fit else "",
    "FIT_LAST_SCALE": f"{fit.group(7)},{fit.group(8)}" if fit else "-",
    "FIT_LAST_H": fit.group(2) if fit else "-",
    "FIT_LAST_L": fit.group(3) if fit else 999,
    "FIT_LAST_T": fit.group(4) if fit else 999,
    "FIT_LAST_BH": fit.group(6) if fit else 0,
    "NORMAL_N": cnt("Normal Scale. scaleX: "),
    "NORMAL_LAST_SCALE": f"{norm.group(1)},{norm.group(2)}" if norm else "-",
    "TXM_N": cnt("TX UpdateUiConfigRequest: L="),
    "TXM_LAST": ",".join(txm.groups()) if txm else "-",
    "VID_LAST": f"{vid.group(1)}x{vid.group(2)}" if vid else "-",
    "DRIFT": cnt("CarScreen: margins drifted from the announced"),
    "USABLE_734": sum(1 for line in win if re.search(r"CarScreen: usable area \d+x734\b", line)),
    "USABLE_767": sum(1 for line in win if re.search(r"CarScreen: usable area \d+x767\b", line)),
}
for k, v in out.items():
    print(f"{k}='{v}'")
```

**margin_fit.py**
```python
# Normal-mode lit boxes graded against each session's own last margins.
# usage: margin_fit.py TABLE   (one line per session: TAG ARM LIT TXM VID SCALE FITH; '-' for none)
#   ARM tn or sv; LIT a crop_tools measure file; TXM the last "L,T,R,B" sent before the shot;
#   VID the "WxH" the phone sent; SCALE the session's last "sx,sy"; FITH the last L-FIT height (sv only).
# The bottom edge of the picture sits B video rows above the content end, so E = bottom + B * K is the
# same for every session when the margins explain the box. K is screen px per video row, FIT height / VID height.
import re, statistics, sys

rows = []
try:
    lines = open(sys.argv[1], errors="replace").read().splitlines()
except (OSError, IndexError):
    lines = []
for line in lines:
    f = line.split()
    if len(f) < 7:
        continue
    tag, arm, lit, txm, vid, scale, fith = f[:7]
    try:
        t = open(lit, errors="replace").read()
    except OSError:
        t = ""
    m = re.search(r"w=(\d+) h=(\d+) rows=(\d+)-(\d+) cols=(\d+)-(\d+)", t)
    mm = re.fullmatch(r"(-?\d+),(-?\d+),(-?\d+),(-?\d+)", txm)
    mv = re.fullmatch(r"(\d+)x(\d+)", vid)
    try:
        sy = round(abs(float(scale.split(",")[1])), 4)
    except (IndexError, ValueError):
        sy = None
    rec = {"tag": tag, "arm": arm, "ok": bool(m and mm and mv and sy), "sy": sy}
    if m:
        w, h, r0, r1, c0, c1 = (int(g) for g in m.groups())
        if w < h:   # portrait buffer: screen y is column w-1-c, screen x is row
            rec.update(bottom=w - 1 - c0, x0=r0, x1=r1)
        else:
            rec.update(bottom=r1, x0=c0, x1=c1)
    if mm:
        rec["B"] = int(mm.group(4))
    if mv:
        rec["vh"] = int(mv.group(2))
    rec["fith"] = int(fith) if fith.isdigit() else 0
    rows.append(rec)

good = [r for r in rows if r["ok"]]
sys_ = sorted({r["sy"] for r in good if r["arm"] == "sv"})
common = statistics.mode([r["sy"] for r in good if r["arm"] == "sv"]) if sys_ else None
use = [r for r in good if r["sy"] == common]
ks = [r["fith"] / r["vh"] for r in use if r["arm"] == "sv" and r["fith"] and r["vh"]]
K = statistics.median(ks) if ks else 0.0
for r in use:
    r["E"] = r["bottom"] + r["B"] * K
tn = [r for r in use if r["arm"] == "tn"]
sv = [r for r in use if r["arm"] == "sv"]
out = {"TN_N": len(tn), "SV_N": len(sv), "EXCL": len(rows) - len(use), "K": f"{K:.4f}", "SCALE_Y": common or ""}
if tn and sv and K:
    med = statistics.median(r["E"] for r in tn)
    mx0 = statistics.median(r["x0"] for r in tn); mx1 = statistics.median(r["x1"] for r in tn)
    out["TN_SPREAD"] = round(max(r["E"] for r in tn) - min(r["E"] for r in tn))
    out["SV_DEV"] = round(max(abs(r["E"] - med) for r in sv))
    out["ROW_DEV"] = round(max(max(abs(r["x0"] - mx0), abs(r["x1"] - mx1)) for r in sv))
    pairs = [(s, t) for s in sv for t in tn if s["B"] == t["B"]]
    out["PAIR_N"] = len(pairs)
    out["PAIR_DEV"] = max(abs(s["bottom"] - t["bottom"]) for s, t in pairs) if pairs else 0
    out["BS"] = ",".join(str(b) for b in sorted({r["B"] for r in use}))
else:
    out.update(TN_SPREAD=999, SV_DEV=999, ROW_DEV=999, PAIR_N=0, PAIR_DEV=0, BS="")
for r in rows:
    print(f"# {r['tag']} {r['arm']} ok={int(r['ok'])} sy={r['sy']} bottom={r.get('bottom', '-')} B={r.get('B', '-')} "
          f"E={r['E']:.1f}" if "E" in r else f"# {r['tag']} {r['arm']} ok={int(r['ok'])} sy={r['sy']} excluded", file=sys.stderr)
for k, v in out.items():
    print(f"{k}='{v}'")
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

- **S1, TX, S4, S5, S6** (round 1, same build, same units): all PASS. S1 lit 949 to 991 permille of TextureView with the
  layer at the fit and the surface at 1280x800; S4 and S5 matched their TextureView references to 2 px with dark strips;
  S6 re-fit after a rotation. Nothing in this round changes the code they ran.
- **`main` on D-SAM** (`dsam-surfaceview-crop` round 1): SurfaceView 396 permille in 3 of 3 sessions. Settled.
- **API 24 and up** (the plan's TA, on D-HU): left out, as in round 1. It needs D-POCO bonded to D-HU, which this pair
  must not hold. `SurfaceViewFitPolicyTest` covers both sides of the `sdkInt >= 24` gate.
- **GLES** (`view-mode` 2) and **touch parity**: settled by the code, as section 8 of round 1 says.
- **The 734/767 margin race itself**: not this ticket. S2 reports how often each side fired (`usable734`, `usable767` in
  `S2-sessions.txt`) so the new ticket starts with numbers.

## Objections answered

**O1 (warn), the margin model is tested only if the bottom margin varies. Accepted and changed.** Cond 7 now grades the
picture's vertical midpoint, which needs no margin model: a margin change moves the top and bottom edges apart, and a
placement error moves both the same way. `crop_tools.py vmid` reads the lit rows between the bar rectangles that
`sf_parse.py` gives, and `mids` compares each SurfaceView midpoint with TN's median. On round 1's raw frames the
midpoints are 415.5 (TN) and 416.0 (SurfaceView), and the 33 px height difference equals (231 - 209) x 1.5. A picture
that touches a bar or the screen edge is clipped and left out. The margin model (E, cond 13) and the matched pairs
(cond 9) stay as a second check, E as a report line only.

**O2 (warn), a session is graded without proof of its view mode and a live picture. Accepted and changed.**
`grade-s2.sh` admits a session only with SSL and no `thr=0` gate line, its arm's `Using TextureView` or `Using
SurfaceView` line and a `Throughput over` line between `-go` and `-shot` (`hu_parse.py` `USE_T`, `USE_S`, `THR`), and its
arm's `view-mode` in `S2-<tag>-settings.xml`. Each session left out is named with its reason in `S2-sessions.txt`, and
cond 14 reports the count.

## 9. Report back

1. S2: `TN_SPREAD`, `SV_DEV`, `ROW_DEV`, `K`, `BS` and `PAIR_N`/`PAIR_DEV`, plus the per-session lines of
   `S2-sessions.txt` (bottom edge, last bottom margin, E, fit and layer).
2. The bar check: the worst bar permille and the bar rectangles the dump named.
3. The phone: `Critical error` count and the two marker counts.

