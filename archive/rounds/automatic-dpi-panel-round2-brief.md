# automatic-dpi-panel, round 2 brief: Automatic stops at the widescreen line

Publish name on `transfer/rig-rounds`: `automatic-dpi-panel-round2-brief.md`. Results go in `automatic-dpi-panel-round2-results.md`. Evidence goes in the existing release `rig-evidence-automatic-dpi-panel` as the asset `automatic-dpi-panel-round2-captures.zip`.

Two units, each its own head unit in Self Mode: D-POCO and D-MOTO. D-HU, D-SAM and D-HP are not used. Two builds: A (`main`, round 1's APK) and C (the candidate, rebuilt). Each phone runs three sessions: A on Automatic, C on Automatic, and C with a stored 384 as the positive control. Each session takes three screenshots: settled, after a tap on the map, and after a drag on the map. About 55 min, the build included.

| Run | Build | Stored DPI | What it decides |
|---|---|---|---|
| PO-A, MO-A | A | 0 (Automatic) | control: 240, widescreen, the card stays on a map touch |
| **PO-C, MO-C** | C | 0 (Automatic) | **the point of the round**: 309, widescreen kept, the card stays on a map touch |
| PO-K, MO-K | C | 384 | positive control: round 1's 1000x450 canvas, no widescreen, the card retracts on a map touch |

## 1. Build and baseline

| Arm | Ref | SHA | `commit` in `ACTION_QUERY_STATE` starts with |
|---|---|---|---|
| **C** (candidate) | `fork/fix/automatic-dpi-panel-size` | `677f118e10634123f5083c6790b89332d3dd9eaf` | `677f118e` |
| **A** (control) | round 1's `A.apk`, built from `main` | `947b3ae9fae9834aa8e4145ee91751ed743c2ae0` | `947b3ae9` |

C is two commits on A. Round 1 tested the first, `245e8d50`. This round adds `677f118e Display: keep the widescreen layout Automatic DPI already had`. No history was rewritten: `677f118e` is a fast-forward on `245e8d50`. The dev host ran the full JVM suite at C: **4251 tests, 0 failures, 0 errors**. The rig does not run the unit tests.

Do not rebuild A. Copy it from round 1's folder and check its md5 against round 1's results (`0166de4f28b90aad3c91c560f59c733b`). If it is missing or differs, rebuild A from `947b3ae9` in the same worktree before C.

**`r2_build.sh`**
```bash
git fetch fork fix/automatic-dpi-panel-size
git rev-parse fork/fix/automatic-dpi-panel-size      # must start with 677f118e; record it
R1=rig-data/rounds/automatic-dpi-panel-round1; OUT=rig-data/rounds/automatic-dpi-panel-round2; EV=$OUT; mkdir -p $OUT/apk $OUT/scripts
cp $R1/apk/A.apk $OUT/apk/A.apk && md5sum $OUT/apk/A.apk     # must read 0166de4f28b90aad3c91c560f59c733b
$RIG_TOOLKIT/wt-new.sh dpa2                          # record its path as WT
git -C $WT checkout --detach fork/fix/automatic-dpi-panel-size && git -C $WT rev-parse HEAD
$RIG_TOOLKIT/rig_thermal.sh wait 70
HUR_DIR=$WT MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh && cp $WT/app/build/outputs/apk/github/debug/*.apk $OUT/apk/C.apk
md5sum $OUT/apk/C.apk
for a in A C; do printf '%s panel-based=%s widescreen-line=%s\n' $a \
  "$(unzip -p $OUT/apk/$a.apk 'classes*.dex' | strings | grep -cF 'panel-based')" \
  "$(unzip -p $OUT/apk/$a.apk 'classes*.dex' | strings | grep -cF 'dp widescreen line')"; done
```

### R0. Build and identity gate

**PASS when all hold:**

1. C builds. A's md5 is `0166de4f28b90aad3c91c560f59c733b`. C's md5 differs from round 1's C (`73cba570ca939f48962fd4e7c48be27a`).
2. C: `panel-based` and `dp widescreen line` each count 1 or more. A: both count 0. `dp widescreen line` is the text `677f118e` adds, so round 1's C cannot carry it either.

**A failure in R0 stops the round.** After every install, `ACTION_QUERY_STATE`'s `commit` must start with the arm's prefix, or the session that follows is void.

## 2. What this is and why

Round 1 (`automatic-dpi-panel-round1-results.md`) measured every number it asked for. On both phones, C sent 384 and Android Auto used `dpi 384` on a `w1000dp h450dp` canvas. The rig host graded that layout `WIDE` from the screenshot. That grade was wrong, and the round 1 FAIL is real for another reason. The engineer touched the map by hand. In C the media card beside the map retracts. In the real widescreen layout it stays. The phone's own log tells the two apart. A prints `GH.WidescreenIM: Using widescreen interaction manager` in PO-A and MO-A, and neither C session prints it. Android Auto's widescreen line is 1240 dp wide, and 1000 dp is under it.

`677f118e` keeps the larger text and the layout. If the old Automatic value gave the canvas the widescreen layout, the panel-based value now stops at the line. On the phones' stretched canvas (2400 px), that is `2400 * 160 / 1240 = 309`. It gives 1242x559 dp, which Android Auto rounds to 1243. Text is then about as large as at 320, which the engineer called correct. D-HU (256) and D-SAM (240) do not change from round 1, because their canvases were never widescreen. Round 1 covered them, so they are not run.

**What a PASS would look like if the change did nothing:** C would send 384, as in round 1, with no `GH.WidescreenIM` line. The K arm sends exactly that, from a stored value on the same build. So PO-K and MO-K show what a failed C would look like, and they prove the touch probe can see a card retract.

## 3. What is different about this round

- **Widescreen is graded from the phone log, not from a picture.** The line is `GH.WidescreenIM: Using widescreen interaction manager`, seen on Gearhead 18.1.164344 in round 1. A screenshot is still taken. The host grades from it only whether the media card is visible after each touch.
- **Two touches into the projected video, by `input tap` and `input swipe` on the phone.** These go to Android Auto over the touch channel. They are the template's one standing exception to "no taps" (§3). Nothing taps the app's own UI.
- **The DPI page is not read.** Round 1 found no route into the non-exported settings screen on either phone. The engineer reads the page by hand if wanted. Record it in Setup notes, not as a run.
- **Round 1's brief defects are fixed here.** Script block names now carry backticks. `mode=` shows the Self Mode route on its own line. No `aapt2` step is needed.
- **The phones' backups hold `wifi-connection-mode` 3.** So the launch also arms the Native AA host beside Self Mode, as in round 1. Round 1's sessions were all valid this way. Leave it as it is, so the two rounds compare.
- Hand steps, the same as round 1: "Start head unit server" in Android Auto if `:149D` counts 0, and unlocking D-MOTO's PIN. Never force-stop Gearhead.
- Phone `Critical error` lines come within about 2 s of an Exit. Count them only between the `arm` and `x-go` markers.
- **Never run adb calls in parallel against one unit.** The background logcat capture is the only exception.

## 4. Settings keys

The same per-unit keys as round 1's `KEYS_PHONE`, from a fresh backup per phone, with round 1's `put_keys`. The K arm adds one stored value.

**`r2_env.sh`**
```bash
COMMON="int:dpi-pixel-density=0 bool:dpi-floor-override=false int:video-fit-mode=0 int:pixel-aspect-ratio-e4=10000 int:screen-orientation=2 bool:narrow-band-profile-cap=false del:video-profile-starvation-cap int:log-level=2 bool:auto-start-self-mode=false bool:auto-connect-last-session=false bool:kill-on-disconnect=false int:onboarding-version=2"
KEYS_PHONE="$COMMON str:display-size-preset=PHONE_4_6 int:resolutionId=3"
KEYS_K="$KEYS_PHONE int:dpi-pixel-density=384"
```

In `put_keys` a later token replaces an earlier one, so `KEYS_K` stores 384. `ACTION_LOG_MARKER` is not gated at either SHA.

## 5. Helpers

Copy round 1's `dpa_keys.py` and `dpa_lib.sh` from `$R1/scripts/` into `$OUT/scripts/`, then source `dpa_lib.sh` and `r2_lib.sh` with `EV=$OUT`. `r2_lib.sh` adds a Self Mode session with the two touches, and a readout with the widescreen line.

**`r2_lib.sh`**
```bash
# r2_lib.sh : source after round 1's dpa_lib.sh. Needs EV, OUT, POCO, MOTO.
# touch_map S RUN : a tap, then a drag, on the right half of the projection, a screenshot after each
touch_map() { local s=$1 run=$2
  mark $s "$run:tap"; adb -s $s shell input tap 1700 540; sleep 3; shot $s $run.tap
  mark $s "$run:drag"; adb -s $s shell input swipe 1800 540 1500 540 400; sleep 3; shot $s $run.drag; }
# sess_r2 PHONE RUN BASEXML TOKENS... : round 1's sess_self with the touches before the exit
sess_r2() { local s=$1 run=$2 base=$3; shift 3
  adb -s $s shell am force-stop $PKG; sleep 0.3          # never force-stop Gearhead
  put_keys $s "$base" "$@" || return 1; prefs_cat $s > $EV/$run.keys-before
  $RIG_TOOLKIT/rig_thermal.sh wait 75 || $RIG_TOOLKIT/rig_thermal.sh wait 75
  $RIG_TOOLKIT/rig_thermal.sh watch $EV/thermal-$run.log & echo $! > $EV/$run.th.pid
  cap_start $s $run.hu
  mark $s "$run:arm"
  adb -s $s shell am start -n $MAIN; sleep 0.3
  waitfor $run.hu 'MainActivity' 10; sendto $s ACTION_START_SELF_MODE
  waitfor $run.hu 'Throughput over .*rendered=[1-9]' 90 > $EV/$run.wait
  waitfor $run.hu 'contentArea: ' 20 >> $EV/$run.wait
  sleep 10   # the map and the card finish drawing; this time is the screenshot's
  shot $s $run
  touch_map $s $run
  mark $s "$run:x-go"; sendto $s ACTION_EXIT
  waitfor $run.hu 'AapService: Disconnected\.' 15 >> $EV/$run.wait; mark $s "$run:x-done"
  cap_stop $run.hu; kill $(cat $EV/$run.th.pid) 2>/dev/null; }
# readout2 RUN : the numbers a session is graded on; the one capture holds both sides
readout2() { local r=$1 w="win $1.hu $1 arm x-go"
  echo "density=$($w | grep -a -m1 -o '\[ServiceDiscovery\] Density is: .*' | sed 's/^\[ServiceDiscovery\] Density is: //')"
  echo "negotiated=$($w | grep -a -m1 -o 'NegotiatedResolution is: .*')"
  echo "chose_fallback=$($w | grep -acF 'HeadUnitScreenConfig: Video: the phone chose the ')"
  echo "rescap=$($w | grep -a -m1 -o '\[RES_CAP\] resolutionId=.*')"
  echo "ssl=$($w | grep -acF 'SSL handshake complete')"
  echo "rendered_windows=$($w | grep -aF 'Throughput over ' | grep -acE 'rendered=[1-9]')"
  echo "self_route=$($w | grep -a -m1 -oE 'SelfMode: AA [^d]*detected')"
  echo "server_down=$($w | grep -acF 'Headunit Server (127.0.0.1:5277) is NOT running')"
  echo "self_wake=$($w | grep -acF 'MATCH! Starting AapService')"
  echo "disconnected_in_session=$($w | grep -acF 'AapService: Disconnected.')"
  echo "verbs=$(grep -acF 'AutomationReceiver: ' $EV/$r.hu.log)"
  echo "ph_dpi=$($w | grep -aF 'displayDimensions ' | grep -a -m1 -oE 'dpi [0-9]+')"
  echo "ph_canvas=$($w | grep -aF 'contentArea: ' | grep -a -m1 -oE 'w[0-9]+dp h[0-9]+dp')"
  echo "ph_widescreen=$($w | grep -acF 'GH.WidescreenIM: Using widescreen interaction manager')"
  echo "ph_critical=$($w | grep -acF 'Critical error')"
  echo "tap_marker=$(grep -acF "$r:tap" $EV/$r.hu.log) drag_marker=$(grep -acF "$r:drag" $EV/$r.hu.log)"
  cat $EV/$r.wait 2>/dev/null | sed 's/^/wait: /'
}
```

The touch points are on a 2400x1080 landscape display. They land on the map in every layout round 1 drew: the card and the rail sit in the left 800 px. D-MOTO's window was 2300x1017 in round 1, and the same points are on its map too. If the PNG after the tap shows a place sheet or a dialog over the map instead, press nothing: grade the card from that PNG, and say so in Setup notes.

**Verbs and commands, and nothing else acts on the app:** `sendto $S ACTION_START_SELF_MODE`, `sendto $S ACTION_EXIT`, `mark` (`ACTION_LOG_MARKER`), `sendto $S ACTION_QUERY_STATE` (inside `install_arm`), `adb -s $S shell am start -n $MAIN`, and the two touches into the projected video in `touch_map`. Every verb prints `AutomationReceiver: ` in the capture. A step with no such line never landed, and that session is void.

### Precheck, once

**`r2_precheck.sh`**
```bash
for s in $POCO $MOTO; do
  adb -s $s shell dumpsys package com.google.android.projection.gearhead | grep -a -m1 versionName; sleep 0.3   # round 1: 18.1.164344
  adb -s $s shell cat /proc/net/tcp6 /proc/net/tcp | grep -ci ':149D'; sleep 0.3                             # 0 is hand step 1
  adb -s $s shell dumpsys window | grep -a -m1 -E 'mCurrentFocus'; sleep 0.3
  adb -s $s shell dumpsys trust | grep -a -m1 -oE 'deviceLocked=[01]'; sleep 0.3                              # 1 on D-MOTO is hand step 2
done
type put_keys sess_r2 readout2 touch_map win waitfor shot | grep -c 'is a function'                           # must print 7
```

If `mCurrentFocus` is not the launcher, send `adb -s $S shell input keyevent KEYCODE_HOME` once.

## 6. The deciding lines

Every app string below matched `grep -F -r` in `app/src/main/java` at `677f118e`, and every one is INFO or WARN: `[ServiceDiscovery] Density is: `, `dp widescreen line`, `panel-based`, `[ServiceDiscovery] NegotiatedResolution is: `, `HeadUnitScreenConfig: Video: the phone chose the `, `[RES_CAP] resolutionId=`, `SSL handshake complete`, `Throughput over `, `SelfMode: AA 17.4+ detected. Connecting directly to Headunit Server on 127.0.0.1:5277`, `Headunit Server (127.0.0.1:5277) is NOT running`, `MATCH! Starting AapService`, `AapService: Disconnected.`, `AutomationReceiver: `, `AutomationMarker: `.

Android Auto lines, from the same capture in `arm` to `x-go`. They are not in our source. Round 1 saw all three on 18.1.164344:

| Line (grep -F) | Used for |
|---|---|
| `GH.WidescreenIM: Using widescreen interaction manager` | the phone chose the widescreen layout: 1, or 0 |
| `displayDimensions ` | `dpi [0-9]+`, the density the phone uses |
| `contentArea: ` | `w[0-9]+dp h[0-9]+dp`, the phone's canvas |
| `Critical error` | 0 |

### Expected values

| Run | `density=` (exact) | `ph_dpi=` | `ph_canvas=` | `ph_widescreen=` | card after tap and drag |
|---|---|---|---|---|---|
| PO-A, MO-A | `240 (dp canvas 1600x720, requested automatic, codec-based)` | `dpi 240` | w1599 to w1601, h719 to h721 | 1 | `STAYS` |
| PO-C, MO-C | `309 (dp canvas 1242x559, requested automatic, panel-based, held at the 1240 dp widescreen line)` | `dpi 309` | w1241 to w1244, h558 to h560 | 1 | `STAYS` |
| PO-K, MO-K | `384 (dp canvas 1000x450, requested 384, within the HUIG floor)` | `dpi 384` | w999 to w1001, h449 to h451 | 0 | `RETRACTS` |

D-MOTO's window was 2300x1017 in round 1, and its `Density is:` text still read the full 2400 px canvas (1000 dp at 384). If MO-C's dp width differs from 1242, record it and grade on `ph_widescreen=` alone.

### The card, graded by the host from the PNGs

For each session, open `$RUN.png`, `$RUN.tap.png` and `$RUN.drag.png`. For each picture, record whether the media card (album art and play controls, beside the map, outside the rail) is visible: `CARD` or `NO CARD`.

- `STAYS`: `CARD` in all three pictures.
- `RETRACTS`: `CARD` in `$RUN.png`, and `NO CARD` in `$RUN.tap.png` or `$RUN.drag.png`.
- `NONE`: `NO CARD` in `$RUN.png`. Record it.

## 7. Runs

Order: precheck, R0, then D-POCO: `install_arm $POCO A`, PO-A, `install_arm $POCO C`, PO-C, PO-K, restore. Then D-MOTO the same way: MO-A, MO-C, MO-K, restore.

**`r2_unit.sh`**
```bash
# D-POCO; for D-MOTO set S=$MOTO, the backup name and the run ids MO-A, MO-C, MO-K
S=$POCO; prefs_cat $S > $OUT/settings-backup-POCO.xml; B=$OUT/settings-backup-POCO.xml
install_arm $S A
sess_r2 $S PO-A "$B" $KEYS_PHONE; readout2 PO-A | tee $EV/PO-A.readout
install_arm $S C
sess_r2 $S PO-C "$B" $KEYS_PHONE; readout2 PO-C | tee $EV/PO-C.readout
sess_r2 $S PO-K "$B" $KEYS_K;     readout2 PO-K | tee $EV/PO-K.readout
adb -s $S shell am force-stop $PKG; restore_prefs $S "$B"
```

**A session is valid only when all hold:** `verbs` 1 or more and the `commit` matches the arm; `ssl` 1; `rendered_windows` 1 or more; `self_wake` 0 and `disconnected_in_session` 0; `self_route` reads `SelfMode: AA 17.4+ detected` and `server_down` is 0; `negotiated` is 1080p and `rescap` shows `linkCapped=none`; `chose_fallback` is 0; `tap_marker` and `drag_marker` are each 1 or more; `keys-before` shows the run's `dpi-pixel-density`. Otherwise re-run once. **Stop rule:** a run that is void twice is recorded as void, and the round goes on.

### PO-C and MO-C. The point of the round

**PASS when all hold, on both phones:** `density=`, `ph_dpi=` and `ph_canvas=` match section 6's C row; `ph_widescreen=` is 1; `ph_critical=` is 0; the card is `STAYS`.

### PO-A and MO-A. The control

**PASS when all hold:** section 6's A row; `ph_widescreen=` 1; the card `STAYS`. This shows the widescreen layout keeps its card on this touch probe.

### PO-K and MO-K. The positive control

**PASS when all hold:** section 6's K row; `ph_widescreen=` 0; the card `RETRACTS`. This is round 1's C state from a stored value. If the card does not retract here, the touch probe cannot see the fault: mark the card condition INCONCLUSIVE in all three runs on that phone, and grade them on `ph_widescreen=` alone.

## 8. Do not re-run

- D-HU, D-SAM and the 10 inch cases. `677f118e` does not change them: the JVM tests pin 256, 240 and 205, and round 1 measured the first two on hardware.
- The DPI page on the phones (no route, round 1).

## 9. Report back

1. PO-C and MO-C: `density=`, `ph_dpi=`, `ph_widescreen=` and the card grade.
2. PO-K and MO-K: whether the card retracted, which proves the probe.
3. The nine PNGs per phone, A, C and K side by side, for the engineer's legibility call at 309.
