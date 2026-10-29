# automatic-dpi-panel, round 1 brief: Automatic DPI on dense panels

Publish name on `transfer/rig-rounds`: `automatic-dpi-panel-round1-brief.md`. Results go in `automatic-dpi-panel-round1-results.md`. Evidence goes in a new release `rig-evidence-automatic-dpi-panel` as the asset `automatic-dpi-panel-round1-captures.zip`.

Four units are used as the head unit, one after the other: D-HU and D-SAM over Native AA with D-POCO as the phone, then D-POCO and D-MOTO each in Self Mode. Two builds: A (`main`) and C (the candidate). Each unit runs one session on each build, and three units also open the DPI page on each build. That is 8 sessions and 6 page reads, each with a screenshot. About 1 h 40 min, builds included. Every action is a verb, an `am start` or a keyevent. Three hand steps are possible, all named in section 5: the Android Auto "Start head unit server" toggle on a phone where nothing listens on 5277, forgetting D-HU's radio on D-POCO before D-SAM, and unlocking a PIN keyguard.

| Run | Head unit | Phone | Build | What it decides |
|---|---|---|---|---|
| HU-A, HU-C | D-HU | D-POCO | A, C | a 6 inch 720p head unit moves from 240 to 256, at its height floor |
| HUP-A, HUP-C | D-HU | none | A, C | the DPI page shows the value that goes on the wire |
| SAM-A, SAM-C | D-SAM | D-POCO | A, C | control: a 7 inch 1280x800 tablet does not change (240 on both) |
| PO-A, **PO-C** | D-POCO | itself (Self Mode) | A, C | **the point of the round**: a 1080p phone panel moves from 240 to 384 |
| POP-A, POP-C | D-POCO | none | A, C | the DPI page shows 384 on C, where the engineer saw 240 |
| MO-A, **MO-C** | D-MOTO | itself (Self Mode) | A, C | **the point of the round**, on a second 1080p phone panel |
| MOP-A, MOP-C | D-MOTO | none | A, C | the DPI page on D-MOTO |

## 1. Build and baseline

| Arm | Ref | SHA | `commit` in `ACTION_QUERY_STATE` starts with |
|---|---|---|---|
| **C** (candidate) | `fork/fix/automatic-dpi-panel-size` | `245e8d501a81fe295851dbe09946823d0e371c34` | `245e8d50` |
| **A** (control) | `main` | `947b3ae9fae9834aa8e4145ee91751ed743c2ae0` | `947b3ae9` |

C is one commit on A: `245e8d50 Display: never let Automatic DPI fall below the panel's own density`. This is the thread's first round, so no history was rewritten. The dev host ran the full JVM suite at C: **4250 tests, 0 failures, 0 errors**. The rig does not run the unit tests.

Build both arms in one worktree, A first, then C (section 5 of the template). Use the thermal limits in `rig-quirks/topics/tooling.md`.

**dpa_build.sh**
```bash
git fetch fork fix/automatic-dpi-panel-size
git rev-parse fork/fix/automatic-dpi-panel-size      # must start with 245e8d50; record it
$RIG_TOOLKIT/wt-new.sh dpa                           # one worktree for both arms; record its path as WT
OUT=rig-data/rounds/automatic-dpi-panel-round1; EV=$OUT; mkdir -p $OUT/apk $OUT/scripts
git -C $WT checkout --detach 947b3ae9fae9834aa8e4145ee91751ed743c2ae0 && git -C $WT rev-parse HEAD
$RIG_TOOLKIT/rig_thermal.sh wait 70
HUR_DIR=$WT MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh && cp $WT/app/build/outputs/apk/github/debug/*.apk $OUT/apk/A.apk
git -C $WT checkout --detach fork/fix/automatic-dpi-panel-size && git -C $WT rev-parse HEAD
$RIG_TOOLKIT/rig_thermal.sh wait 70
HUR_DIR=$WT MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh && cp $WT/app/build/outputs/apk/github/debug/*.apk $OUT/apk/C.apk
md5sum $OUT/apk/A.apk $OUT/apk/C.apk
for a in A C; do printf '%s panel-based=%s codec-based=%s\n' $a \
  "$(unzip -p $OUT/apk/$a.apk 'classes*.dex' | strings | grep -cF 'panel-based')" \
  "$(unzip -p $OUT/apk/$a.apk 'classes*.dex' | strings | grep -cF 'codec-based')"; done
```

### R0. Build and identity gate

**PASS when all hold:**

1. Both builds succeed, and the two md5s differ.
2. C: `panel-based` counts 1 or more. A: `panel-based` counts 0. Both: `codec-based` counts 1 or more. `panel-based` is the string C adds, so A cannot carry it.

**A failure in R0 stops the round.** After every install, `ACTION_QUERY_STATE`'s `commit` must start with the arm's prefix above, or the session that follows is void.

## 2. What this is and why

On a dense panel, Automatic DPI gave Android Auto text that was too small. The landscape Automatic value comes only from the video width (`1920 * 150 / 800`), with a hard cap of 240, and the screen-size preset can only lower it. Every 1080p session therefore got 240, on a 395 ppi phone panel and on a 216 ppi tablet alike. On D-POCO as head unit, the engineer saw "too small" at 240, "correct" at 320, and "too large" at 440. At 440 the canvas is also under Android Auto's 450 dp height floor, so the reduced layout comes in.

The decompiled Android Auto 18.0 rules out a phone-side remedy. The phone copies `density` (field 5 of `VideoConfiguration`) into the car display's `Configuration.densityDpi` unchanged. It only logs `real_density` (field 9). `viewing_distance` (field 7) is not in its schema at all.

C keeps the old value as a lower bound. On Automatic in landscape it takes the larger of the old value and a panel-based value. The panel-based value makes one dp 1/160 inch: the panel's pixels per inch at the preset's diagonal, per canvas row. It is still held to the 748x450 dp floor (`huigMaxDpi`). A stored DPI and portrait do not change. JVM predictions for this rig, with FILL and the pixel aspect ratio derived:

| Unit | A sends | C sends |
|---|---|---|
| D-POCO, D-MOTO, 1080p | 240, 1600x720 dp | **384, 1000x450 dp** |
| D-HU, 720p | 240, 960x480 dp | 256, 900x450 dp |
| D-SAM, 720p | 240, 853x533 dp | 240, 853x533 dp |

The width on the phones follows the projection window. A window 2340 px wide gives 975x450 on C and 1560x720 on A. The height and the DPI do not move.

**What a PASS would look like if the change did nothing:** C would send 240 `codec-based` on the phones, the same as A. The A arm on the same unit shows that number, so a C session that matches A is a FAIL, not a pass.

## 3. What is different about this round

- **Arm A is the control, and it shows the defect.** PO-A and MO-A should send 240 and draw the widescreen layout. PO-C and MO-C should send 384 and not draw it.
- **The phones should lose the widescreen layout on C, and that is expected.** In Android Auto 18.0 the widescreen branch needs `screenWidthDp >= 1240` (`SystemUi__widescreen_breakpoint_dp`, 1240). C's 1000 dp is under it. The engineer's own 320 (1200 dp) was too. Grade only `WIDE` against not `WIDE`, and record the rail.
- **The legibility verdict is the engineer's, not the host's.** The host grades the numbers and the `WIDE` class. The screenshots go in the results, A beside C per unit.
- **Self Mode on Android Auto 17.4 or later needs the phone's head unit server on `127.0.0.1:5277`.** The toggle is UI only (Android Auto developer settings, "Start head unit server"). Never force-stop Gearhead: that drops the server. The precheck tests the port.
- **D-POCO stays bonded to every head unit it has projected to.** A D-HU bond can stop D-SAM waking it (D-POCO quirk). The precheck reads the bonded list.
- **`SettingsActivity` is not exported.** The page runs open it with `run-as $PKG am start --user 0`, from the app's own UID. On D-HU, whose adb shell is root, a plain `am start` works.
- **D-SAM:** API 19, no `sed`, no `exec-out`, no `-p` on `am`, the first frame comes late, and the mount is portrait with `screen-orientation` 2. It is on 2.4 GHz, so `narrow-band-profile-cap` is written false (otherwise AAC would come on) and the resolution is pinned at 720p.
- **The fallback.** Connect also offers a 720p configuration whose density keeps the same dp canvas (256 on the phones' C arm). A session where the phone takes it (`HeadUnitScreenConfig: Video: the phone chose the `) is INCONCLUSIVE and is re-run once.
- **The rig's audio settings are a deliberate worst case.** Never write `use-aac-audio`, `audio-latency-multiplier`, `audio-queue-capacity` or `enable-audio-sink`.
- **Phone `Critical error` lines come within about 2 s of an Exit, on `main` too.** Count them only between the `arm` and `x-go` markers.
- **Never run adb calls in parallel against one unit.** The background logcat captures are the only exception.

## 4. Settings keys

Write with the app stopped, from a fresh backup per unit, with `put_keys` (section 5). The rig's `set_hu_settings_host.py` (D-HU) and `set_prefs_runas_host.py` (the others) do the same job. Use them if they take these keys, and say which in Setup notes. State each unit's delta against its backup in Setup notes, then restore the backup at the end of the unit and diff it.

`$MAC` is D-POCO's Bluetooth address. Read it from each head unit's bonded list (`DC:B7:2E:5E:4E:59` in earlier rounds).

| Element | D-HU | D-SAM | D-POCO, D-MOTO (Self Mode) | Why |
|---|---|---|---|---|
| `int dpi-pixel-density` | 0 | 0 | 0 | Automatic, the value under test |
| `boolean dpi-floor-override` | false | false | false | no "Use anyway" |
| `string display-size-preset` | `PHONE_4_6` | `SMALL_7_8` | `PHONE_4_6` | the size the wizard estimates for each panel |
| `int resolutionId` | 2 (720p) | 2 (720p) | 3 (1080p) | pinned, so Auto cannot move it |
| `int video-fit-mode` | 0 | 0 | 0 | FILL |
| `int pixel-aspect-ratio-e4` | 10000 | 10000 | 10000 | unset: the ratio is derived |
| `int screen-orientation` | 2 | 2 | 2 | landscape |
| `boolean narrow-band-profile-cap` | false | false | false | no 720p or AAC cap from a 2.4 GHz link |
| delete `video-profile-starvation-cap` | yes | yes | yes | no cap left over from a fumbled session |
| `int log-level` | 2 | 2 | 2 | every graded line is INFO or WARN |
| `int wifi-connection-mode` | 3 | 3 | as backup | Native AA for the two head units |
| `int native-ap-transport` | 0 | 0 | | WiFi Direct |
| `int native-driver-selection-mode` | 0 | 0 | | |
| `boolean native-poke-all-paired` | false | false | | |
| `set native-poke-bt-macs` | `$MAC` | `$MAC` | | wake only D-POCO |
| `string last-connected-native-mac`, `string native-preferred-device-mac` | `$MAC` | `$MAC` | | |
| delete `native-aa-wake-damage-verdict` | yes | yes | | |
| `boolean auto-start-self-mode` | false | false | false | |
| `boolean auto-connect-last-session` | false | false | false | |
| `boolean kill-on-disconnect` | false | false | false | |
| `int onboarding-version` | 2 | 2 | 2 | no wizard |
| `int wifi-direct-band` | 1 | 0 | | ask D-HU for 5 GHz; D-SAM is 2.4 GHz only |

As `put_keys` tokens. Source this after `dpa_lib.sh`, with `MAC` set from the bonded list:

**dpa_env.sh**
```bash
MAC=${MAC:?set MAC to the Bluetooth address of D-POCO first}
COMMON="int:dpi-pixel-density=0 bool:dpi-floor-override=false int:video-fit-mode=0 int:pixel-aspect-ratio-e4=10000 int:screen-orientation=2 bool:narrow-band-profile-cap=false del:video-profile-starvation-cap int:log-level=2 bool:auto-start-self-mode=false bool:auto-connect-last-session=false bool:kill-on-disconnect=false int:onboarding-version=2"
NATIVE="int:wifi-connection-mode=3 int:native-ap-transport=0 int:native-driver-selection-mode=0 bool:native-poke-all-paired=false set:native-poke-bt-macs=$MAC str:last-connected-native-mac=$MAC str:native-preferred-device-mac=$MAC del:native-aa-wake-damage-verdict"
KEYS_HU="$COMMON $NATIVE str:display-size-preset=PHONE_4_6 int:resolutionId=2 int:wifi-direct-band=1"
KEYS_SAM="$COMMON $NATIVE str:display-size-preset=SMALL_7_8 int:resolutionId=2 int:wifi-direct-band=0"
KEYS_PHONE="$COMMON str:display-size-preset=PHONE_4_6 int:resolutionId=3"
```

`ACTION_LOG_MARKER` is not in `AutomationCommandPolicy.CONFIGURING` at either SHA, so `allow-external-configuration` is not needed.

## 5. Helpers, verbs and hand steps

Save both files under `$OUT/scripts/` and `source $OUT/scripts/dpa_lib.sh`. Set `DHU`, `SAM`, `POCO` and `MOTO` to the serials in the template's unit table, and `EV=$OUT`.

**dpa_keys.py**
```python
# dpa_keys.py IN OUT TOKEN...  : apply int:/long:/bool:/str:/set:/del: tokens to a SharedPreferences file
import sys, xml.etree.ElementTree as ET
src, dst, toks = sys.argv[1], sys.argv[2], sys.argv[3:]
root = ET.parse(src).getroot()
def drop(name):
    for e in list(root):
        if e.get('name') == name: root.remove(e)
for t in toks:
    kind, kv = t.split(':', 1)
    if kind == 'del':
        drop(kv); continue
    k, v = kv.split('=', 1)
    drop(k)
    tag = {'int': 'int', 'long': 'long', 'bool': 'boolean', 'str': 'string', 'set': 'set'}[kind]
    e = ET.SubElement(root, tag, {'name': k})
    if tag == 'string': e.text = v
    elif tag == 'set':
        for item in v.split(','): ET.SubElement(e, 'string').text = item
    else: e.set('value', v)
with open(dst, 'w') as f:
    f.write("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n" + ET.tostring(root, encoding='unicode'))
```

**dpa_lib.sh**
```bash
# dpa_lib.sh : needs EV, OUT, DHU, SAM, POCO, MOTO. One adb call at a time per unit.
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
MAIN=$PKG/com.andrerinas.openheadunit.main.MainActivity
SETTINGS=$PKG/com.andrerinas.openheadunit.main.SettingsActivity
PREFS=/data/data/$PKG/shared_prefs/settings.xml
sendto() { local s=$1 a=$2; shift 2; adb -s $s shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; sleep 0.3; }
prefs_cat() { if [ "$1" = "$DHU" ]; then adb -s $1 shell cat $PREFS; else adb -s $1 shell run-as $PKG cat shared_prefs/settings.xml; fi | tr -d '\r'; }
put_keys() { local s=$1 base=$2; shift 2
  python3 -I $OUT/scripts/dpa_keys.py "$base" $EV/put.xml "$@" || return 1
  adb -s $s push $EV/put.xml /data/local/tmp/dpa.xml >/dev/null; sleep 0.3
  adb -s $s shell chmod 644 /data/local/tmp/dpa.xml; sleep 0.3
  if [ "$s" = "$DHU" ]; then local o=$(adb -s $s shell stat -c %u:%g $PREFS | tr -d '\r')
    adb -s $s shell cp /data/local/tmp/dpa.xml $PREFS; adb -s $s shell chown $o $PREFS; adb -s $s shell chmod 660 $PREFS
  else adb -s $s shell run-as $PKG cp /data/local/tmp/dpa.xml shared_prefs/settings.xml; sleep 0.3
    prefs_cat $s | cmp -s - $EV/put.xml || adb -s $s shell "run-as $PKG sh -c 'cat > shared_prefs/settings.xml'" < $EV/put.xml; fi
  sleep 0.3; prefs_cat $s | cmp -s - $EV/put.xml || { echo "PUT FAILED on $s"; return 1; }; }
# restore_prefs UNIT BACKUP : the backup back on the unit unchanged, then an exact diff
restore_prefs() { cp "$2" $EV/put.xml; put_keys_raw $1; prefs_cat $1 | diff - "$2" && echo restored; }
put_keys_raw() { local s=$1; adb -s $s push $EV/put.xml /data/local/tmp/dpa.xml >/dev/null; sleep 0.3; adb -s $s shell chmod 644 /data/local/tmp/dpa.xml; sleep 0.3
  if [ "$s" = "$DHU" ]; then local o=$(adb -s $s shell stat -c %u:%g $PREFS | tr -d '\r')
    adb -s $s shell cp /data/local/tmp/dpa.xml $PREFS; adb -s $s shell chown $o $PREFS; adb -s $s shell chmod 660 $PREFS
  else adb -s $s shell run-as $PKG cp /data/local/tmp/dpa.xml shared_prefs/settings.xml; sleep 0.3
    prefs_cat $s | cmp -s - $EV/put.xml || adb -s $s shell "run-as $PKG sh -c 'cat > shared_prefs/settings.xml'" < $EV/put.xml; fi; sleep 0.3; }
cap_start() { adb -s $1 logcat -c; sleep 0.3; stdbuf -oL adb -s $1 logcat -v threadtime > $EV/$2.log 2>&1 & echo $! > $EV/$2.pid; sleep 1; }
cap_stop() { for p in "$@"; do kill $(cat $EV/$p.pid) 2>/dev/null; done; }
# mark UNIT LABEL [PHONE] : AutomationMarker on the head unit, OHURIG on the phone
mark() { sendto $1 ACTION_LOG_MARKER --es text "$2" >/dev/null; [ -n "$3" ] && adb -s $3 shell log -t OHURIG "$2"; sleep 0.3; }
# win FILE RUN FROM TO : the lines between two of a run's markers
win() { sed -n "/$2:$3/,/$2:$4/p" $EV/$1.log; }
# waitfor FILE ERE CAP : poll once a second, print the seconds taken, return 1 on the cap
waitfor() { local i=0; while [ $i -lt $3 ]; do grep -aqE "$2" $EV/$1.log && { echo "waited ${i}s for $2"; return 0; }; sleep 1; i=$((i+1)); done; echo "CAP ${3}s: $2"; return 1; }
shot() { adb -s $1 shell screencap -p /sdcard/dpa.png; sleep 0.3; adb -s $1 pull /sdcard/dpa.png $EV/$2.png >/dev/null; sleep 0.3; }
install_arm() { adb -s $1 install -r -d $OUT/apk/$2.apk; sleep 0.3
  adb -s $1 shell md5sum $(adb -s $1 shell pm path $PKG | cut -d: -f2 | tr -d '\r'); sendto $1 ACTION_QUERY_STATE; }
phone_off() { adb -s $1 shell svc wifi disable; sleep 2; adb -s $1 shell svc bluetooth disable; sleep 3
  adb -s $1 shell dumpsys wifi | grep -a -m1 'Wi-Fi is'; adb -s $1 shell dumpsys bluetooth_manager | grep -a -m2 -E 'enabled:|state:'; }
phone_on() { adb -s $1 shell svc wifi enable; sleep 2; adb -s $1 shell svc bluetooth enable; sleep 3
  adb -s $1 shell dumpsys wifi | grep -a -m1 'Wi-Fi is'; adb -s $1 shell dumpsys bluetooth_manager | grep -a -m2 -E 'enabled:|state:'; }
# sess_native HU PHONE RUN BASEXML TOKENS... : one Native AA session, template section 4
sess_native() { local hu=$1 ph=$2 run=$3 base=$4; shift 4
  phone_off $ph > $EV/$run.radio-off
  adb -s $hu shell am force-stop $PKG; sleep 0.3
  put_keys $hu "$base" "$@"; prefs_cat $hu > $EV/$run.keys-before
  $RIG_TOOLKIT/rig_thermal.sh wait 75 || $RIG_TOOLKIT/rig_thermal.sh wait 75
  $RIG_TOOLKIT/rig_thermal.sh watch $EV/thermal-$run.log & echo $! > $EV/$run.th.pid
  cap_start $hu $run.hu; cap_start $ph $run.ph
  mark $hu "$run:arm" $ph
  adb -s $hu shell am start -n $MAIN; sleep 0.3
  waitfor $run.hu 'Initializing WiFi Mode: ' 10 || sendto $hu ACTION_START_WIRELESS_SCAN
  phone_on $ph > $EV/$run.radio-on
  waitfor $run.hu 'Throughput over .*rendered=[1-9]' 90 > $EV/$run.wait
  waitfor $run.ph 'contentArea: ' 20 >> $EV/$run.wait
  sleep 10   # the map and rail finish drawing; this time is the screenshot's, not a wait for a line
  shot $hu $run
  mark $hu "$run:x-go" $ph; sendto $hu ACTION_DISCONNECT
  waitfor $run.hu 'AapService: Disconnected\.' 15 >> $EV/$run.wait; mark $hu "$run:x-done" $ph
  cap_stop $run.hu $run.ph; kill $(cat $EV/$run.th.pid) 2>/dev/null; }
# sess_self PHONE RUN BASEXML TOKENS... : one Self Mode session; the one capture is both sides
sess_self() { local s=$1 run=$2 base=$3; shift 3
  adb -s $s shell am force-stop $PKG; sleep 0.3          # never force-stop Gearhead
  put_keys $s "$base" "$@"; prefs_cat $s > $EV/$run.keys-before
  $RIG_TOOLKIT/rig_thermal.sh wait 75 || $RIG_TOOLKIT/rig_thermal.sh wait 75
  $RIG_TOOLKIT/rig_thermal.sh watch $EV/thermal-$run.log & echo $! > $EV/$run.th.pid
  cap_start $s $run.hu; ln -sf $run.hu.log $EV/$run.ph.log
  mark $s "$run:arm"
  adb -s $s shell am start -n $MAIN; sleep 0.3
  waitfor $run.hu 'MainActivity' 10; sendto $s ACTION_START_SELF_MODE
  waitfor $run.hu 'Throughput over .*rendered=[1-9]' 90 > $EV/$run.wait
  waitfor $run.hu 'contentArea: ' 20 >> $EV/$run.wait
  sleep 10   # as in sess_native
  shot $s $run
  mark $s "$run:x-go"; sendto $s ACTION_EXIT
  waitfor $run.hu 'AapService: Disconnected\.' 15 >> $EV/$run.wait; mark $s "$run:x-done"
  cap_stop $run.hu; kill $(cat $EV/$run.th.pid) 2>/dev/null; }
# dest_id APK : the decimal id of the DPI page's nav destination in that APK
dest_id() { local a=$(ls ${ANDROID_HOME:-$ANDROID_SDK_ROOT}/build-tools/*/aapt2 2>/dev/null | sort -V | tail -1)
  [ -n "$a" ] || { echo 0; return; }
  local h=$($a dump resources "$1" | grep -a 'id/dpiSettingsFragment' | grep -ao '0x7f[0-9a-f]*' | head -1)
  [ -n "$h" ] && printf '%d\n' "$h" || echo 0; }
# page UNIT RUN DEST BASEXML TOKENS... : open the DPI page with the app stopped, dump it, screenshot it
page() { local s=$1 run=$2 d=$3 base=$4 an; shift 4
  adb -s $s shell am force-stop $PKG; sleep 0.3
  put_keys $s "$base" "$@"; prefs_cat $s > $EV/$run.keys-before
  an=$(adb -s $s shell settings get global animator_duration_scale | tr -d '\r'); echo "anim_before=$an" > $EV/$run.summary
  adb -s $s shell settings put global animator_duration_scale 0; sleep 0.3
  cap_start $s $run.hu; mark $s "$run:start"
  if [ "$s" = "$DHU" ]; then adb -s $s shell am start -n $SETTINGS --ei extra_destination $d
  else adb -s $s shell run-as $PKG am start --user 0 -n $SETTINGS --ei extra_destination $d; fi >> $EV/$run.summary
  waitfor $run.hu 'HeadUnitScreenConfig|DpiSettings|SettingsActivity' 15 >> $EV/$run.summary; sleep 6
  adb -s $s shell uiautomator dump /sdcard/dpa.xml >> $EV/$run.summary; sleep 0.3
  adb -s $s pull /sdcard/dpa.xml $EV/$run.xml >/dev/null; sleep 0.3
  shot $s $run; mark $s "$run:end"
  adb -s $s shell input keyevent 4; sleep 2; adb -s $s shell am force-stop $PKG; cap_stop $run.hu
  if [ "$an" = "null" ]; then adb -s $s shell settings delete global animator_duration_scale
  else adb -s $s shell settings put global animator_duration_scale "$an"; fi; }
```

Only D-HU uses `stat`. On D-SAM, `run-as ... cat` returns `\r\r\n`, which `prefs_cat` strips. `put_keys` checks its own write and prints `PUT FAILED` if the file on the unit differs from the one it built: that session is a setup failure. `restore_prefs` pushes the backup byte for byte, so the final diff is exact. D-SAM has no `md5sum`: there, `install_arm`'s md5 line fails and the `commit` reply is the identity check.

**Verbs and commands, and nothing else acts on the app:**

| Action | Command |
|---|---|
| arm Native AA when the launch did not | `sendto $HU ACTION_START_WIRELESS_SCAN` (inside `sess_native`, only after 10 s with no `Initializing WiFi Mode:`) |
| start Self Mode | `sendto $S ACTION_START_SELF_MODE` (inside `sess_self`) |
| user exit, Native AA | `sendto $HU ACTION_DISCONNECT` |
| exit, Self Mode | `sendto $S ACTION_EXIT` |
| marker | `mark` (`ACTION_LOG_MARKER`, plus `log -t OHURIG` on the phone) |
| build identity | `sendto $S ACTION_QUERY_STATE` (inside `install_arm`) |
| launch | `adb -s $S shell am start -n $MAIN` |
| open the DPI page | `page` (`am start`, or `run-as $PKG am start --user 0` off D-HU) |
| close the DPI page | `adb -s $S shell input keyevent 4` |

Every verb must print `AutomationReceiver: ` in that unit's capture. A step with no such line never landed, and that session is void, not a FAIL.

**Hand steps, and only these three:**

1. **"Start head unit server" on D-POCO or D-MOTO**, when the precheck finds nothing listening on 5277 and that phone's Gearhead is 17.4 or later. Android Auto offers no verb, deep link or settings key for it. Ask the operator once per phone, then re-run the port check.
2. **Forget D-HU's radio on D-POCO**, when the precheck lists it among D-POCO's bonded devices, before SAM-A. Android has no adb verb to unpair. Ask once.
3. **Unlock a PIN keyguard on D-MOTO or D-POCO**, when the precheck shows one. adb has no way past a PIN (D-MOTO quirk). Ask once per phone.

### Precheck, once

Apps stopped. Record every output in Setup notes.

**dpa_precheck.sh**
```bash
for s in $POCO $MOTO; do
  adb -s $s shell dumpsys package com.google.android.projection.gearhead | grep -a -m1 versionName; sleep 0.3
  adb -s $s shell cat /proc/net/tcp | grep -ci ':149D'; sleep 0.3        # 0 with Gearhead 17.4+ is hand step 1
  adb -s $s shell dumpsys window | grep -a -m1 -E 'mCurrentFocus|mShowingLockscreen|isKeyguardShowing'; sleep 0.3
  adb -s $s shell dumpsys display | grep -a -m2 -E 'xDpi|PhysicalDisplayInfo'; sleep 0.3
  adb -s $s shell wm density; sleep 0.3
done
adb -s $POCO shell dumpsys bluetooth_manager | grep -a -A10 'Bonded devices'; sleep 0.3   # D-HU's radio here is hand step 2
for s in $DHU $SAM; do adb -s $s shell dumpsys bluetooth_manager | grep -a -A8 'Bonded devices'; sleep 0.3; done
adb -s $SAM shell date; date
for s in $DHU $SAM $POCO $MOTO; do adb -s $s shell getprop persist.sys.locale; sleep 0.3; done
prefs_cat $DHU | grep -aoE 'name="(use-aac-audio|audio-latency-multiplier|audio-queue-capacity|enable-audio-sink)"[^>]*>'
type sess_native sess_self page put_keys win waitfor dest_id | grep -c 'is a function'   # must print 7
```

If a phone shows a PIN keyguard, that is hand step 3. If `mCurrentFocus` on D-POCO is not the launcher, send `adb -s $POCO shell input keyevent KEYCODE_HOME`.

## 6. The deciding lines

Every app string below matched `grep -F -r` in `app/src/main/java` at `245e8d50`. All are INFO or WARN.

| Line (grep -F) | Level | File, window | Used for |
|---|---|---|---|
| `AutomationReceiver: ` | INFO | head unit, whole run | a verb landed |
| `AutomationMarker: ` | WARN | head unit, whole run | markers |
| `[ServiceDiscovery] Density is: ` | INFO | head unit, `arm` to `x-go` | the density, its dp canvas and its reason |
| `[ServiceDiscovery] NegotiatedResolution is: ` | INFO | same | the run's codec |
| `[ServiceDiscovery] PixelAspectRatioE4 is: ` | INFO | same | record |
| `[ServiceDiscovery] Margins are: ` | INFO | same | record |
| `[ServiceDiscovery] Offering a ` | INFO | same | record (the fallback and its density) |
| `HeadUnitScreenConfig: Video: the phone chose the ` | INFO | same | must be 0, else INCONCLUSIVE |
| `[RES_CAP] resolutionId=` | INFO | same | `linkCapped=none` |
| `Raw size: ` | INFO | same | the window the canvas came from |
| `SSL handshake complete` | INFO | same | a session formed |
| `Throughput over ` | INFO | same | `rendered=[1-9]` is a picture |
| `Initializing WiFi Mode: ` | INFO | same, Native AA runs | `NATIVE` |
| `SelfMode: AA 17.4+ detected. Connecting directly to Headunit Server on 127.0.0.1:5277` | INFO | same, Self Mode runs | the route taken (or the legacy `AA < 17.4 detected`) |
| `Headunit Server (127.0.0.1:5277) is NOT running` | WARN | same | must be 0; else hand step 1 and re-run |
| `MATCH! Starting AapService` | INFO | same | discard if 1 or more |
| `AapService: Disconnected.` | INFO | `arm` to `x-go` | must be 0 |

`Density is:` is built by `AnnouncedDensityPolicy.describe`: `<dpi> (dp canvas <w>x<h>, requested automatic, <reason>)`, where the reason is `codec-based` or `panel-based`.

Android Auto lines, graded on the phone's capture (`$RUN.ph.log`, the same file in Self Mode) in `arm` to `x-go`. They are not in our source. Both were checked in the decompiled 18.0 release (`ohb.java` in `gearhead-18.0.664204-release`), and earlier rounds saw both on 18.0:

| Line (grep -F) | Format in 18.0 | Used for |
|---|---|---|
| `displayDimensions ` | `displayDimensions %s, dpi %s, contentInsets: %s, displayInsets: %s` | the dpi the phone uses: `dpi [0-9]+` |
| `contentArea: ` | `contentArea: %s, DisplayDimensions: %s, VD Config: %s` | the phone's canvas: `w[0-9]+dp h[0-9]+dp` in the VD Config |
| `Real density` | `Real density %d` | must be 0: we do not send field 9 |
| `Critical error` | | must be 0 in `arm` to `x-go` |
| `Will use layout: ` | | record only; it has not printed on 18.0 in earlier rounds |

**dpa_readout.sh**
```bash
# readout RUN : the numbers a session is graded on
readout() { local r=$1 h="win $1.hu $1 arm x-go" p="win $1.ph $1 arm x-go"
  echo "density=$($h | grep -a -m1 -o '\[ServiceDiscovery\] Density is: .*' | sed 's/^\[ServiceDiscovery\] Density is: //')"
  echo "negotiated=$($h | grep -a -m1 -o 'NegotiatedResolution is: .*')"
  echo "par=$($h | grep -a -m1 -o 'PixelAspectRatioE4 is: .*')"
  echo "margins=$($h | grep -a -m1 -o 'Margins are: .*')"
  echo "fallback_offer=$($h | grep -a -m1 -o 'Offering a .*')"
  echo "chose_fallback=$($h | grep -acF 'HeadUnitScreenConfig: Video: the phone chose the ')"
  echo "rescap=$($h | grep -a -m1 -o '\[RES_CAP\] resolutionId=.*')"
  echo "raw=$($h | grep -a -m1 -o 'Raw size: [0-9]*x[0-9]*')"
  echo "ssl=$($h | grep -acF 'SSL handshake complete')"
  echo "rendered_windows=$($h | grep -aF 'Throughput over ' | grep -acE 'rendered=[1-9]')"
  echo "mode=$($h | grep -a -m1 -oE 'Initializing WiFi Mode: [A-Z_]+|SelfMode: AA [^d]*detected')"
  echo "server_down=$($h | grep -acF 'Headunit Server (127.0.0.1:5277) is NOT running')"
  echo "self_wake=$($h | grep -acF 'MATCH! Starting AapService')"
  echo "disconnected_in_session=$($h | grep -acF 'AapService: Disconnected.')"
  echo "verbs=$(grep -acF 'AutomationReceiver: ' $EV/$r.hu.log)"
  echo "ph_dpi=$($p | grep -aF 'displayDimensions ' | grep -a -m1 -oE 'dpi [0-9]+')"
  echo "ph_dims=$($p | grep -aF 'displayDimensions ' | grep -a -m1 -oE 'Point\([0-9]+, [0-9]+\)')"
  echo "ph_canvas=$($p | grep -aF 'contentArea: ' | grep -a -m1 -oE 'w[0-9]+dp h[0-9]+dp')"
  echo "ph_real_density=$($p | grep -acF 'Real density')"
  echo "ph_critical=$($p | grep -acF 'Critical error')"
  echo "ph_layout=$($p | grep -a -m1 -oF 'Will use layout: ' )"
  cat $EV/$r.wait 2>/dev/null | sed 's/^/wait: /'
}
# pageout RUN : the DPI page's Automatic line, as the dump holds it
pageout() { local x=$EV/$1.xml
  echo "auto_desc=$(grep -ao '<node[^>]*:id/dpi_auto_desc"[^>]*>' $x | grep -ao ' text="[^"]*"')"
  echo "auto_checked=$(grep -ao '<node[^>]*:id/dpi_auto_switch"[^>]*>' $x | grep -ao ' checked="[^"]*"')"
  echo "fatal=$(win $1.hu $1 start end | grep -acF 'FATAL EXCEPTION')"
  echo "resumed=$(grep -acE 'Starting: Intent|SettingsActivity' $EV/$1.summary)"
}
```

The DPI page's line is `Uses %1$d DPI for %2$s, a %3$dx%4$d dp canvas.` in English. On another locale, grade the three numbers in order, not the words.

### Expected values

| Run | `density=` (exact, except the phones' width) | `ph_dpi=` | `ph_canvas=` height | `auto_desc` numbers (DPI, W, H) |
|---|---|---|---|---|
| HU-A | `240 (dp canvas 960x480, requested automatic, codec-based)` | `dpi 240` | `h480dp` | HUP-A: 240, 960, 480 |
| HU-C | `256 (dp canvas 900x450, requested automatic, panel-based)` | `dpi 256` | `h450dp` | HUP-C: 256, 900, 450 |
| SAM-A, SAM-C | `240 (dp canvas 853x533, requested automatic, codec-based)` | `dpi 240` | `h533dp` | not run |
| PO-A, MO-A | `240 (dp canvas Wx720, requested automatic, codec-based)`, W from 1536 to 1600 | `dpi 240` | `h720dp` | POP-A, MOP-A: 240, 1600, 720 |
| PO-C, MO-C | `384 (dp canvas Wx450, requested automatic, panel-based)`, W from 960 to 1000 | `dpi 384` | `h450dp` | POP-C, MOP-C: 384, 1000, 450 |

On the phones, W follows the projection window: `W = round(rawWidth * 160 / dpi)`, within 1, where `rawWidth` is from `raw=`. The page reads the full panel, so its width is the 2400 px one. A `ph_canvas=` height within 1 dp of ours is a match: the phone rounds `px / density` itself.

### Layout class (the host reads the PNGs)

| Class | What the projection shows |
|---|---|
| `WIDE` | a rail, the map, and a media card beside the map, outside the rail |
| `RAIL` | a rail on the left or right and the map, no media card beside it |
| `BAR` | a rail along the bottom, the media control inside it, the map above |
| `OTHER` | a dialog, a black or frozen picture, the launcher, or anything else |

A session whose PNG is `OTHER` is re-run once. A second `OTHER` makes it INCONCLUSIVE.

## 7. Runs

Time: builds about 30 min with the thermal gates, 8 sessions at about 4 min, 6 page reads at about 2 min, installs and restores about 15 min, and up to 15 min of thermal waits before runs: about 1 h 40 min.

Order: precheck, R0, then one unit at a time: D-HU (HUP-A, HU-A, HUP-C, HU-C), D-SAM (SAM-A, SAM-C), D-POCO (POP-A, PO-A, POP-C, PO-C), D-MOTO (MOP-A, MO-A, MOP-C, MO-C). Start each unit with a fresh backup and `install_arm $S A`. After the A runs, `install_arm $S C`. End the unit by restoring its backup, diffing it, and leaving C installed. An install can wipe `settings.xml` (tooling quirk). Every run rebuilds it from the backup.

**dpa_unit.sh**
```bash
# D-HU: replace S, the key set and the run ids for the other units as the order above says
S=$DHU; prefs_cat $S > $OUT/settings-backup-DHU.xml; B=$OUT/settings-backup-DHU.xml
install_arm $S A; DA=$(dest_id $OUT/apk/A.apk); DC=$(dest_id $OUT/apk/C.apk); echo "dest A=$DA C=$DC"
page $S HUP-A $DA "$B" $KEYS_HU; pageout HUP-A | tee $EV/HUP-A.pageout
sess_native $S $POCO HU-A "$B" $KEYS_HU; readout HU-A | tee $EV/HU-A.readout
install_arm $S C
page $S HUP-C $DC "$B" $KEYS_HU; pageout HUP-C | tee $EV/HUP-C.pageout
sess_native $S $POCO HU-C "$B" $KEYS_HU; readout HU-C | tee $EV/HU-C.readout
adb -s $S shell am force-stop $PKG; restore_prefs $S "$B"
# D-SAM: S=$SAM, KEYS_SAM, sess_native $S $POCO SAM-A / SAM-C, no page runs
# D-POCO: S=$POCO, KEYS_PHONE, page POP-A / POP-C, sess_self $S PO-A / PO-C
# D-MOTO: S=$MOTO, KEYS_PHONE, page MOP-A / MOP-C, sess_self $S MO-A / MO-C
```

A `dest_id` of 0 means `aapt2` was not found. Then the page runs are UNTESTABLE, and the sessions still run.

**Every session is valid only when all hold**, read from its readout:

1. `verbs` is 1 or more, and the `commit` after the last install matches the arm.
2. `ssl` is 1 and `rendered_windows` is 1 or more. A 0 is a setup failure: re-run once. A 2 or more is a discard.
3. `self_wake` is 0 and `disconnected_in_session` is 0. Otherwise discard and re-run once.
4. `mode` reads `Initializing WiFi Mode: NATIVE` (HU, SAM) or a `SelfMode: AA` route (PO, MO), and `server_down` is 0.
5. `negotiated` is the run's codec (720p for HU and SAM, 1080p for PO and MO), and `rescap` shows `linkCapped=none`.
6. `keys-before` shows `dpi-pixel-density` 0 and the run's `resolutionId` and `display-size-preset`.
7. `chose_fallback` is 0. Otherwise INCONCLUSIVE: re-run once.

A failed condition 4, 5 or 6 is a setup failure. Fix the keys and re-run once. **Stop rule:** a run that is void, discarded or INCONCLUSIVE twice is recorded as such, and the round goes on to the next run.

### PO-C and MO-C. The point of the round

**PASS when all hold, on both phones:**

1. `density=` reads `384`, `requested automatic`, `panel-based`, with a dp height of 450 and W as section 6 gives.
2. `ph_dpi=` is `dpi 384`, and `ph_canvas=` height is 449 to 451.
3. `ph_real_density` is 0, and `ph_critical` is 0.
4. The layout is not `WIDE`.

### PO-A and MO-A. The control

**PASS when all hold:** `density=` reads `240`, `codec-based`, height 720; `ph_dpi=` is `dpi 240`; `ph_canvas=` height is 719 to 721; the layout is `WIDE`. A PASS here is what makes the C result a change of the build rather than of the rig.

### HU-A, HU-C

**PASS when all hold:** `density=` equals section 6's row exactly, `ph_dpi=` matches it, and `ph_canvas=` height is within 1 of it. The layout class is recorded; both canvases are under 1240 dp, so neither should be `WIDE`.

### SAM-A, SAM-C. The no-change control

**PASS when both sessions read the same `density=`, exactly section 6's SAM row,** and `ph_dpi=` is `dpi 240` in both. A C value above 240 here is a FAIL: the fix must not move a tablet whose old value already fits it.

### HUP, POP, MOP. The DPI page

**PASS when all hold:** `auto_desc`'s three numbers equal section 6's row; `auto_checked` is `checked="true"`; `fatal` is 0; and the C page's DPI equals the C session's `density=` DPI on the same unit. A page that did not open (`resumed` 0, or no dump) is UNTESTABLE, not a FAIL.

## 8. Do not re-run

- The 800x480 and 1280x720 clamp texts and the widescreen line from `wizard-dpi-layout` rounds 1 and 2. A stored DPI goes through the same code on both arms.
- Portrait. C does not change the portrait path.
- Whether the settings screen's first, portrait reading changes the value. The JVM test covers it: the decision is the same both ways.

## 9. Report back

1. PO-C and MO-C: `density=`, `ph_dpi=` and `ph_canvas=`, against PO-A and MO-A.
2. SAM-A and SAM-C: whether they read the same.
3. The eight session screenshots, set out A beside C per unit, for the engineer's legibility call, with the host's layout class under each.
