# pr-1163-hct-owned-wpp, round 1 brief

Published name: `pr-1163-hct-owned-wpp-round1-brief.md`. Results go in `pr-1163-hct-owned-wpp-round1-results.md`.
Evidence: create the release `rig-evidence-pr-1163-hct-owned-wpp` on this first round and upload `pr-1163-hct-owned-wpp-round1-captures.zip` to it.

**This round proves that the PR changes nothing on a unit without the HCT Bluetooth service.** The rig has no HCT unit, so the feature itself is not tested here.

Units: D-HU (head unit), D-POCO (phone). D-MOTO and D-SAM keep Bluetooth and WiFi off. D-HP is not used.

Queue line: `pr-1163-hct-owned-wpp-round1-brief.md` (C `e22017de` = the contributor's PR head on `main` `947b3ae9`, fetched from the upstream pull ref; units D-HU and D-POCO, D-MOTO radios off; about 45 min; X1 is the point: the exit dialog's Stop now goes through the service and still ends the session cleanly)

Pairing: any round that uses neither D-HU nor D-POCO.

Cabling: D-HU and D-POCO on adb. D-MOTO on adb if a port is free, only to switch its radios off.

**Hand steps.** None.

**Injected gestures and keys.** X1 taps once: the `Stop Connection` row of the exit dialog, found in a fresh `uiautomator` dump. X1 sends 1 BACK key to open that dialog. BACK is the app's own way to open it, so it is the instrument. No other run taps or sends a key.

**Time budget.** Build 1 APK 8 min, pre-flight beside it. Install 3 min. S1 and X1 6 min, R2 and X3 6 min, C4 7 min. Restore 5 min. **About 45 min in all, 19 min of it runs.**

| Run | What it proves | Kind |
|---|---|---|
| P | pre-flight: bonds, radios, `shared_prefs` owner, backups | setup |
| S1 | a Native session forms on C | GATE |
| **X1** | **the point:** BACK, then the exit dialog's Stop, ends the session through `ACTION_DISCONNECT`, sends ByeBye, and does not raise the projection again | GATE |
| R2 | the WiFi button verb re-arms after that Exit, and the normal Bluetooth handshake still reads the phone's version | GATE |
| X3 | the `ACTION_DISCONNECT` verb on a live session, the same service path as X1 | GATE |
| C4 | what an `ACTION_DISCONNECT` with no session costs: the automatic wake stops until the WiFi button | RECORD, then GATE on the recovery |
| HCT | no HCT line in any window | GATE, graded in every run |

## 1. Build and baseline

| Arm | Ref | SHA | Tree | `commit` in `ACTION_QUERY_STATE` |
|---|---|---|---|---|
| **C** (candidate) | `refs/pull/1163/head` on `andreknieriem/open-headunit` | `e22017de2c0b45660941f15746047e2a0bb7617d` | `a077424dc109f2ef2667008d3f6f130f4e6d6b51` | `e22017de2c0b` |

C is the contributor's PR as submitted, 4 commits on `main` `947b3ae9fae9834aa8e4145ee91751ed743c2ae0`. It is not on the fork. The contributor rebased it onto that `main` on 2026-10-27, so fetch it fresh. There is no baseline build. On `main`, the exit dialog's Stop called `CommManager.disconnect` directly, so X1 on `main` would print `Disconnect action received.` 0 times. That count is the control.

The dev host ran the JVM suite at C: 4263 tests, 0 failures. Do not run the unit tests on the rig. No other queued round shares this base.

Work from `~/Coding/StudioProjects`. Save every script block under `$OUT/scripts/` with `python3 -I $RIG_TOOLKIT/extract_brief_blocks.py <this brief> $OUT/scripts`. Run every script with `bash $OUT/scripts/<name>.sh`.

**`build.sh`**
```bash
. $(dirname $0)/lib.sh
C=e22017de2c0b45660941f15746047e2a0bb7617d; CT=a077424dc109f2ef2667008d3f6f130f4e6d6b51
WT=${WT:-$($RIG_TOOLKIT/wt-new.sh pr-1163-hct-owned-wpp candidate main | tail -1)}
git -C $WT fetch https://github.com/andreknieriem/open-headunit.git refs/pull/1163/head
git -C $WT checkout --detach $C
[ "$(git -C $WT rev-parse 'HEAD^{tree}')" = $CT ] || { echo "C TREE_MISMATCH"; exit 1; }
[ -z "$(git -C $WT status --porcelain)" ] || { echo "C DIRTY"; exit 1; }
$RIG_TOOLKIT/rig_thermal.sh wait 70
HUR_DIR=$WT MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh || { echo "C BUILD_FAILED"; exit 1; }
cp $(ls -t $RIG_DATA/apks/com.andrerinas.headunitrevived_*.apk | head -1) $OUT/C.apk
echo "WT=$WT C md5 $(md5sum < $OUT/C.apk | cut -c1-32) HctWppOwner=$(unzip -p $OUT/C.apk 'classes*.dex' | strings | grep -c HctWppOwner)" | tee $OUT/build.txt
```

Build gate: `HctWppOwner` MUST be 1 or more. The PR adds that class, so the installed `main` build has 0. A failed gate stops the round.

Identity after the install (`inst` in `lib.sh`): `$RIG_TOOLKIT/apk_identity.sh $OUT/C.apk $D_HU` MUST exit 0, and the `ACTION_QUERY_STATE` reply MUST carry commit `e22017de2c0b`. A `-dirty` suffix is fine; say so. If `adb install -r -d` answers `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, stop and ask. Never uninstall.

## 2. What this is and why

On HCT head units, the manufacturer's Bluetooth service hands the app the phone's WPP stream through a local socket. The PR keeps that stream open after a projection Exit, so the next Start can reuse it without another handoff from the manufacturer's service. The new classes are `HctWppOwner`, `HctRetainedChannel` and `HctProjectionStage`. On every other unit they must stay unused.

Three parts of the PR reach every unit, and they are what this round tests:

1. **The Exit controls now go through the service.** The exit dialog's `Stop Connection`, the `disconnect_button` on the connection-interrupted overlay, and the auto-connect overlay's Cancel used to call `CommManager.disconnect()` directly. They now send `AapService.ACTION_DISCONNECT`, and the activity finishes without waiting. That handler logs `Disconnect action received.`, sets `userExitedAA`, calls `noteSessionEnded(false)` and then `disconnect()`.
2. **`ACTION_DISCONNECT` with no session stops the automatic wake.** A review of the PR found that this handler sets those two flags even when no session exists. The auto-connect overlay's Cancel now reaches the handler while the state is still `Disconnected`. Then every later credential delivery logs `userExitedAA is true. Skipping auto-poke.` until the WiFi button. In Native mode that overlay appears only for USB auto-start, Self Mode and the USB and network lists, and the rig cannot raise any of them cleanly. So C4 sends the same verb by hand and measures the cost.
3. **The normal handshake reads through a new bridge**, `WppInboundMessages`. Off HCT it is unbounded, as before, and must still read the phone's version response.

| Id | Question | Code | Runs | Kind |
|---|---|---|---|---|
| ROUTE | The dialog's Stop reaches `ACTION_DISCONNECT` | `AapProjectionActivity.requestProjectionExit` | X1 | GATE |
| CLEAN | The Exit sends ByeBye and stops the launcher as a user exit | `AapService` `ACTION_DISCONNECT`, `onDisconnected` | X1, X3 | GATE |
| NORAISE | Finishing before the service handles the intent does not raise the projection again | `MainActivity.onResume` guard | X1 | GATE |
| REARM | The WiFi button verb brings the session back after an Exit | `WifiLauncherManager.setActive` | R2, C4 | GATE |
| VERSION | The normal handshake still logs the phone's version | `WppInboundMessages` | S1, R2 | GATE |
| WAKE | How many automatic pokes are lost after an `ACTION_DISCONNECT` with no session | `ACTION_DISCONNECT` handler | C4 | RECORD |
| INERT | No HCT code runs | `NativeAaHandshakeManager.start` | every run | GATE |

## 3. What is different about this round

- **No HCT unit.** D-HU must print no `NativeAA: [HCT]` line at all. Pre-flight checks that D-HU has no HCT service (`android.microntek.mtcser`) and no manufacturer pairing list. If either is present, INERT is INCONCLUSIVE and the round goes on.
- **BACK opens the exit dialog during a session** unless the keymap maps BACK (`ProjectionKeyPolicy.shouldRouteBackKeyToProjection`). If X1's dump reads `rows=0`, check the capture for `TX Key` after `X1_open`. If it is there, the keymap took BACK: X1 is UNTESTABLE (keymap), say so and go on to R2.
- **Do not grade the dialog's focus.** On Android 14, `mCurrentFocus` names the activity for a dialog of that activity (`pr-1132-exit-dialog-round1-results.md`, HU-1). Grade the dump's rows only.
- **`wm size` on D-HU prints the portrait 720x1440.** Tap the dump's bounds, never a size from `wm`.
- **Phone `Critical error` lines follow every Exit, on `main` too.** Gearhead prints `io error` lines within about 1.1 s of each Exit. Count `Critical error` only outside 3 s after each Exit marker.
- **C4 is expected to show the cost.** A count of 0 pokes after `C4_disc` is the finding, not a FAIL. C4 fails only if the WiFi button verb does not bring the session back.
- **On mode 3 the launch arms the stack.** No `ACTION_START_WIRELESS_SCAN` straight after a launch. R2 and C4 send it after an Exit, which is the point.
- **The phone leaves the link by `svc`**, never airplane mode (`ph_off`).
- **Gearhead runs 18.1** on the phones, which is not decompiled. Nothing here depends on its code.
- **The audio settings are a deliberate worst case.** The seeds do not touch them. Record `video-profile-starvation-cap`; the seed deletes it.

## 4. Settings keys

`seed` in `lib.sh` writes these on D-HU with the app stopped (`set_hu_settings_host.py`), then reads them back. `restore.sh` puts the backup back at the end. `POCO_BT` comes from pre-flight.

| Key | Type | Value | Why |
|---|---|---|---|
| `wifi-connection-mode` | int | 3 | Native AA |
| `native-ap-transport` | int | 0 | WiFi Direct |
| `native-driver-selection-mode` | int | 0 | no selector |
| `native-poke-all-paired` | boolean | false | poke only D-POCO |
| `native-poke-bt-macs` | set | `$POCO_BT` | the wake list |
| `last-connected-native-mac` | string | `$POCO_BT` | |
| `native-preferred-device-mac` | string | `$POCO_BT` | |
| `auto-start-bt-macs` | set | empty | no Bluetooth auto-start; C4 needs it off |
| `auto-connect-last-session` | boolean | false | |
| `auto-start-on-boot`, `auto-start-on-screen-on`, `auto-start-on-usb`, `auto-start-self-mode` | boolean | false | |
| `kill-on-disconnect` | boolean | false | an Exit must leave the process alive |
| `external-bt-zbt-transport`, `external-bt-blink-transport` | boolean | false | the android.bluetooth route |
| `native-aa-ignore-external-bt` | boolean | false | |
| `onboarding-version` | int | 2 | no wizard |
| `log-level` | int | 2 | INFO; every graded line is INFO or WARN |
| `native-aa-wake-damage-verdict`, `video-profile-starvation-cap` | | deleted | |

## 5. Helpers

Run every adb call in sequence, never two at once against one unit. The two `logcat` captures are the only standing streams. Write the results file after every run.

**`lib.sh`**
```bash
source ~/Coding/StudioProjects/rig-toolkit/rig_devices.sh
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
MAIN=$PKG/com.andrerinas.openheadunit.main.MainActivity
D_HU=${D_HU:-27870808938846}; D_POCO=${D_POCO:-4f4027e9}; D_MOTO=${D_MOTO:-ZY22GC3BM4}; D_SAM=${D_SAM:-30041c35642d2200}
OUT=$RIG_DATA/rounds/pr-1163-hct-owned-wpp-round1; S=$OUT/scripts; mkdir -p $S $OUT/ui
export LC_ALL=C
[ -f $OUT/env.sh ] && . $OUT/env.sh       # POCO_BT, HU_OWNER: preflight.sh writes them
H=$D_HU
HU=$OUT/hu.txt; PH=$OUT/phone.txt          # one capture each for the whole round
BASE="int:wifi-connection-mode=3 int:native-ap-transport=0 int:native-driver-selection-mode=0 bool:native-poke-all-paired=false set:native-poke-bt-macs=$POCO_BT str:last-connected-native-mac=$POCO_BT str:native-preferred-device-mac=$POCO_BT set:auto-start-bt-macs= bool:auto-connect-last-session=false bool:auto-start-on-boot=false bool:auto-start-on-screen-on=false bool:auto-start-on-usb=false bool:auto-start-self-mode=false bool:kill-on-disconnect=false bool:external-bt-zbt-transport=false bool:external-bt-blink-transport=false bool:native-aa-ignore-external-bt=false int:onboarding-version=2 int:log-level=2 del:native-aa-wake-damage-verdict del:video-profile-starvation-cap"

send() { local a=$1; shift; adb -s $D_HU shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; sleep 0.3; }
# mark stamps both captures: the app's marker on D-HU, a log line on D-POCO
mark() { send ACTION_LOG_MARKER --es text "$1" >/dev/null; adb -s $D_POCO shell log -t OHU_RIG "AutomationMarker: $1"; sleep 0.3
  echo "$1 $(date +%T)" >> $OUT/marks.txt; }
running() { adb -s $1 shell ps -A 2>/dev/null | grep -ac "$PKG"; }
# headunit://exit only to a running app; sent to a stopped one it starts and arms it (rig-quirks/topics/lifecycle.md)
stopapp() { if [ "$(running $1)" -ge 1 ]; then adb -s $1 shell am start -a android.intent.action.VIEW -d headunit://exit >/dev/null; sleep 5; fi
  adb -s $1 shell am force-stop $PKG; sleep 1; }

# phone radios, read back from dumpsys (TESTING-TEMPLATE.md §3)
ph_bt()  { adb -s $1 shell svc bluetooth $2; sleep 4; adb -s $1 shell dumpsys bluetooth_manager | grep -a -m1 -E "^ *state:" | tr -d '\r'; }
ph_off() { adb -s $D_POCO shell svc wifi disable; sleep 0.3; ph_bt $D_POCO disable; adb -s $D_POCO shell dumpsys wifi | grep -a -m1 "Wi-Fi is" | tr -d '\r'; }
ph_on()  { adb -s $D_POCO shell svc wifi enable; sleep 0.3; ph_bt $D_POCO enable; adb -s $D_POCO shell dumpsys wifi | grep -a -m1 "Wi-Fi is" | tr -d '\r'; }

# captures: started before the launch, with stdbuf -oL
cap() { adb -s $D_HU logcat -G 16M >/dev/null 2>&1; adb -s $D_HU logcat -c; stdbuf -oL adb -s $D_HU logcat -v time > $HU & echo $! > $HU.pid
  adb -s $D_POCO logcat -G 16M >/dev/null 2>&1; adb -s $D_POCO logcat -c; stdbuf -oL adb -s $D_POCO logcat -v time > $PH & echo $! > $PH.pid; sleep 1; }
uncap() { kill $(cat $HU.pid) $(cat $PH.pid) 2>/dev/null; }

# seg <file> <from> [<to>]: lines after the last <from> marker up to the next <to> marker (or the end)
seg() { awk -v a="AutomationMarker: $2" -v b="AutomationMarker: ${3:-@@none@@}" '
  function ends(s,t){ return length(s)>=length(t) && substr(s,length(s)-length(t)+1)==t }
  { l=$0; sub(/\r+$/,"",l) }
  ends(l,a){ buf=""; on=1; next }
  on && ends(l,b){ on=0 }
  on{ buf=buf $0 "\n" }
  END{ printf "%s", buf }' "$1"; }
cnt() { seg "$1" "$2" "$3" | grep -acF -- "$4"; }
first() { seg "$1" "$2" "$3" | grep -aF -m1 -- "$4" | cut -c1-18; }
fatal() { seg $HU "$1" "$2" | grep -aF -A3 'FATAL EXCEPTION' | grep -acF "Process: $PKG"; }
# waitfor <file> <from> <cap-s> <string>: 0 as soon as the string appears after <from>
waitfor() { local f=$1 m=$2 cap=$3 s=$4 t=0
  while [ $t -lt $cap ]; do [ "$(cnt $f $m '' "$s")" -ge 1 ] && return 0; sleep 1; t=$((t+1)); done; return 1; }
# session <from>: a projected session after <from>, 120 s cap: SSL, then the first throughput line
session() { waitfor $HU $1 120 'SSL handshake complete' && waitfor $HU $1 30 'Throughput over'; }
# inert <from> <to>: the HCT lines, all MUST be 0
inert() { echo "hct=$(cnt $HU $1 $2 'NativeAA: [HCT]') retain=$(cnt $HU $1 $2 'retaining the owned WPP channel') reader=$(cnt $HU $1 $2 'physical WPP reader closed')"; }
# crit <from> <to>: phone Critical error lines more than 3 s after the <from> marker, on the phone's own clock
crit() { local t0; t0=$(grep -a "AutomationMarker: $1\$" $PH | tail -1 | awk '{ print $2 }' | cut -d. -f1)
  seg $PH $1 $2 | grep -aF 'Critical error' | awk -v t0="$t0" '{ split($2,a,"."); split(a[1],b,":"); split(t0,c,":")
    d=(b[1]*3600+b[2]*60+b[3])-(c[1]*3600+c[2]*60+c[3]); if (d>3) n++ } END{ print n+0 }'; }

# host thermal (rig-quirks/topics/tooling.md)
th_gate() { $RIG_TOOLKIT/rig_thermal.sh wait 75 || $RIG_TOOLKIT/rig_thermal.sh wait 75 || { echo HOST_HOT; return 1; }
  $RIG_TOOLKIT/rig_thermal.sh watch $OUT/thermal-$1.log & THPID=$!; }
th_end() { kill $THPID 2>/dev/null; wait $THPID 2>/dev/null; }

# settings on D-HU (rooted: plain adb shell, never su -c)
pull_settings() { adb -s $D_HU shell cat /data/data/$PKG/shared_prefs/settings.xml | tr -d '\r'; }
seed() { adb -s $D_HU shell am force-stop $PKG; sleep 1
  python3 -I $RIG_TOOLKIT/set_hu_settings_host.py $D_HU $BASE >> $OUT/seed.log 2>&1
  pull_settings > $OUT/seeded.xml
  python3 -I $S/showkeys.py $OUT/seeded.xml wifi-connection-mode native-ap-transport native-poke-all-paired native-poke-bt-macs \
    auto-start-bt-macs kill-on-disconnect log-level video-profile-starvation-cap | tr '\n' ' '; echo; }
inst() { adb -s $D_HU install -r -d $OUT/C.apk | tail -1
  $RIG_TOOLKIT/apk_identity.sh $OUT/C.apk $D_HU; echo "apk_identity exit $?"
  send ACTION_QUERY_STATE | grep -ao 'commit[^,}]*'; adb -s $D_HU shell am force-stop $PKG; }

# screen
uidump() { adb -s $D_HU shell rm -f /sdcard/ohu-ui.xml; sleep 0.3
  adb -s $D_HU shell uiautomator dump /sdcard/ohu-ui.xml >/dev/null 2>&1; sleep 0.3
  adb -s $D_HU pull /sdcard/ohu-ui.xml $OUT/ui/$1.xml >/dev/null 2>&1; sleep 0.3
  adb -s $D_HU shell screencap -p /sdcard/ohu-ui.png; sleep 0.3; adb -s $D_HU pull /sdcard/ohu-ui.png $OUT/ui/$1.png >/dev/null 2>&1
  [ -s $OUT/ui/$1.xml ] || echo "$1 DUMP_FAILED" | tee -a $OUT/marks.txt >&2; }
```

**`exitdlg.py`**
```python
# exitdlg.py <dump.xml> [<label>]: rows=N and the centre of the row whose text starts with <label>.
#   rows=N tap=X,Y text=<label of that row>      (tap=NA when no row matches)
# A row is a node whose children are exactly one ImageView and one TextView with text.
import re, sys, xml.etree.ElementTree as ET
want = sys.argv[2] if len(sys.argv) > 2 else 'Stop'
def box(n):
    m = re.findall(r'\d+', n.get('bounds', ''))
    return tuple(map(int, m)) if len(m) == 4 else None
try:
    root = ET.parse(sys.argv[1]).getroot()
except Exception:
    print('rows=0 PARSE_FAILED'); sys.exit(2)
rows = []
for p in root.iter('node'):
    kids = list(p.findall('node'))
    imgs = [k for k in kids if k.get('class') == 'android.widget.ImageView']
    txts = [k for k in kids if k.get('class') == 'android.widget.TextView' and k.get('text')]
    if len(kids) == 2 and len(imgs) == 1 and len(txts) == 1 and box(p):
        rows.append((box(p), txts[0].get('text')))
hit = [r for r in rows if r[1].startswith(want)]
if not hit:
    print('rows=%d tap=NA' % len(rows)); sys.exit(1)
(l, t, r, b), label = hit[0]
print('rows=%d tap=%d,%d text=%s' % (len(rows), (l + r) // 2, (t + b) // 2, label.replace(' ', '_')))
```

**`showkeys.py`**
```python
# showkeys.py <settings.xml> <key>...: key=value per key; a set prints its items joined by ","; absent prints key=ABSENT
import sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
by = {e.get('name'): e for e in root}
for k in sys.argv[2:]:
    e = by.get(k)
    if e is None: print(k + '=ABSENT')
    elif e.tag == 'set': print(k + '=' + ','.join(sorted((s.text or '') for s in e)))
    elif e.tag == 'string': print(k + '=' + (e.text or ''))
    else: print(k + '=' + str(e.get('value')))
```

## 6. Pre-flight and install

Run `build.sh` in the background and `preflight.sh` in the foreground beside it. Run `install.sh` once the build gate passes.

**`preflight.sh`**
```bash
. $(dirname $0)/lib.sh
exec > >(tee -a $OUT/preflight.txt) 2>&1
echo "==== preflight $(date)"
$RIG_TOOLKIT/rig_preflight.sh D_HU:wifi; $RIG_TOOLKIT/rig_cleanup.sh list
adb devices                                                        # MUST list D-HU and D-POCO
adb -s $D_HU shell id                                              # MUST show uid=0(root)
# INERT premise: no HCT service and no manufacturer pairing list on D-HU. Both MUST print nothing or "null".
adb -s $D_HU shell pm list packages android.microntek.mtcser; sleep 0.3
adb -s $D_HU shell settings get system MTC.BT.matchList; sleep 0.3
adb -s $D_HU shell ls /dev/socket/goc_rfcom 2>&1; sleep 0.3                     # MUST be "No such file"
# shared_prefs owner (rig-quirks/units/D-HU.md); report it even when correct
HU_OWNER=$(adb -s $D_HU shell stat -c '%U:%G' /data/data/$PKG/shared_prefs | tr -d '\r'); echo "HU_OWNER=$HU_OWNER"
# both units: stop their own app first; D-POCO has been a head unit in other rounds
stopapp $D_POCO; stopapp $D_HU
[ -f $OUT/backup-hu.xml ] || { pull_settings > $OUT/backup-hu.xml
  adb -s $D_HU pull $(adb -s $D_HU shell pm path $PKG | cut -d: -f2 | tr -d '\r') $OUT/before-hu.apk >/dev/null; }
wc -c < $OUT/backup-hu.xml                                         # MUST be over 0
python3 -I $S/showkeys.py $OUT/backup-hu.xml wifi-connection-mode native-ap-transport native-poke-bt-macs auto-start-bt-macs \
  kill-on-disconnect key-codes video-profile-starvation-cap use-aac-audio | tr '\n' ' '; echo
# D-POCO identity and bonds; D-HU MUST appear in D-POCO's bonded list
POCO_BT=$(adb -s $D_POCO shell settings get secure bluetooth_address | tr -d '\r'); echo "POCO_BT=$POCO_BT"   # round history: DC:B7:2E:5E:4E:59
adb -s $D_POCO shell dumpsys bluetooth_manager | grep -a -A20 'Bonded devices' > $OUT/poco-bonds.txt; sleep 0.3
adb -s $D_HU shell dumpsys bluetooth_manager | grep -a -A20 'Bonded devices' > $OUT/hu-bonds.txt; sleep 0.3
grep -ac "$POCO_BT" $OUT/hu-bonds.txt                              # MUST be 1 or more
# second phone and tablet off the air (rig-quirks/units/D-HU.md: a paired phone can rejoin a stable group)
for s in $D_MOTO $D_SAM; do adb devices | grep -q "^$s" || { echo "$s not on adb: confirm its Bluetooth and WiFi are off by hand"; continue; }
  adb -s $s shell svc wifi disable; sleep 0.3; ph_bt $s disable; done
ph_off
adb -s $D_POCO shell dumpsys battery | grep -a level
{ echo "POCO_BT=$POCO_BT"; echo "HU_OWNER=$HU_OWNER"; } | tee $OUT/env.sh
```

Pre-flight gates: each line marked MUST. If the HCT package, the pairing list or the socket is present, INERT is INCONCLUSIVE; go on. If `key-codes` in the backup maps BACK (keycode 4), say so: X1 will be UNTESTABLE (keymap). Put the whole `preflight.txt` in Setup notes.

**`install.sh`**
```bash
. $(dirname $0)/lib.sh
exec > >(tee $OUT/install.txt) 2>&1
inst
seed                     # MUST show wifi-connection-mode=3, native-poke-bt-macs=<POCO_BT>, auto-start-bt-macs=, kill-on-disconnect=false
```

**After the install, `seed` reads the keys back before the first launch** (`rig-quirks/topics/tooling.md`: an install can wipe `settings.xml`).

## 7. The deciding lines

Every app string below was checked with `grep -F` in `app/src/main` at `e22017de`. All are INFO unless named. Grep every capture with `-a`.

D-HU capture (`$OUT/hu.txt`):

| Key | String | Meaning |
|---|---|---|
| L-RX | `AutomationReceiver: ` | a verb landed; a missing line voids the step |
| L-SCAN | `WifiLauncher: Initializing WiFi Mode:` | the stack armed |
| L-LISTEN | `NativeAA: ACTIVELY LISTENING on Android Auto UUID` | the RFCOMM listeners opened |
| L-POKE | `NativeAA: Attempting active poke to device` | one automatic or manual poke |
| L-VER | `NativeAA: [RX] WifiVersionResponse v` | the phone's version reply was read (VERSION) |
| L-WS | `WirelessServer: Incoming connection detected` | the phone joined |
| L-SSL | `SSL handshake complete` | the session is up (no `Handshake:` prefix; INFO carries `AapSslContext.performHandshake`) |
| L-TP | `Throughput over` | video is flowing |
| L-DISC | `Disconnect action received.` | `ACTION_DISCONNECT` reached the service (ROUTE) |
| L-UEXIT | `AapService: Native AA user exit. Stopping active launcher.` | a user exit stopped the launcher (CLEAN) |
| L-COOL | `User exit cooldown active for` | the user-exit path ran |
| L-NOWAKE | `NativeAA: deliberate session end; reopening listeners without an automatic wake.` | `noteSessionEnded(false)` ran |
| L-SKIP | `AapService: userExitedAA is true. Skipping auto-poke.` | a credential delivery skipped its poke (WAKE) |
| L-PAUSE | `AapService: automatic wake is paused by the session policy. Keeping listeners available.` | the same, by the session policy (WAKE) |
| L-RES | `AapProjectionActivity: onResume` | the projection screen came to the front |
| L-RAISE | `MainActivity: Active session detected, bringing projection to front` | the home screen re-raised the projection (NORAISE) |
| L-HCT | `NativeAA: [HCT]` | any HCT carrier line, INFO or WARN (INERT) |
| L-RET | `NativeAA: [HCT] retaining the owned WPP channel after projection Exit` | a parked stream (INERT) |
| L-RDR | `NativeAA: [HCT] physical WPP reader closed` | a retained stream's reader (INERT) |
| L-FGS | `ForegroundServiceDidNotStartInTimeException` | system; the Exit intent crashed the service |
| L-FATAL | `FATAL EXCEPTION` with `Process: com.andrerinas.headunitrevived` within 3 lines | a crash |

D-POCO capture (`$OUT/phone.txt`), system and Gearhead lines:

| Key | String | Meaning |
|---|---|---|
| P-BYE | `received ByeByeRequest` | Android Auto got our ByeBye (CLEAN) |
| P-CRIT | `Critical error` | counted only more than 3 s after an Exit marker |

## 8. Runs

One capture pair covers every run. Each run has its own markers. Stop rule for every session wait: a run whose session does not form within 120 s of its trigger ends there and is graded from what it has. Retry a run at most once, in a new capture pair, after `stopapp $D_HU` and a re-seed.

### S1 and X1: session, then the exit dialog's Stop (6 min) **(X1 is the point)**

**`s1x1.sh`**
```bash
. $(dirname $0)/lib.sh
th_gate S1 || exit 1
ph_off; stopapp $D_HU; seed | tee $OUT/S1-seed.txt
cap
mark S1_launch; adb -s $D_HU shell am start -n $MAIN >/dev/null; sleep 15
ph_on | tee $OUT/S1-radios.txt; mark S1_phone_on
session S1_launch; echo "S1 session=$?" | tee $OUT/S1.txt
sleep 20                                                   # let projection settle before the dialog
mark X1_open; adb -s $D_HU shell input keyevent 4; sleep 2; uidump X1-open
echo "open $(python3 -I $S/exitdlg.py $OUT/ui/X1-open.xml 'Stop')" | tee $OUT/X1.txt
xy=$(grep -a '^open ' $OUT/X1.txt | grep -aoE 'tap=[0-9]+,[0-9]+' | cut -d= -f2)
mark X1_stop
if [ -n "$xy" ]; then adb -s $D_HU shell input tap ${xy%,*} ${xy#*,}; echo "X1 tap $xy $(date +%T)" >> $OUT/gestures.log
else echo "X1 NO_TARGET" | tee -a $OUT/X1.txt; fi
sleep 15; mark X1_after; sleep 5; mark X1_end
echo "S1: listen=$(cnt $HU S1_launch X1_open 'NativeAA: ACTIVELY LISTENING on Android Auto UUID') ver=$(cnt $HU S1_launch X1_open 'NativeAA: [RX] WifiVersionResponse v') ssl=$(cnt $HU S1_launch X1_open 'SSL handshake complete') tp=$(cnt $HU S1_launch X1_open 'Throughput over') $(inert S1_launch X1_open)" | tee -a $OUT/S1.txt
echo "X1: tx_key=$(cnt $HU X1_open X1_stop 'TX Key') disc=$(cnt $HU X1_stop X1_end 'Disconnect action received.') disc_at=$(first $HU X1_stop X1_end 'Disconnect action received.') uexit=$(cnt $HU X1_stop X1_end 'AapService: Native AA user exit. Stopping active launcher.') cool=$(cnt $HU X1_stop X1_end 'User exit cooldown active for') res=$(cnt $HU X1_stop X1_end 'AapProjectionActivity: onResume') raise=$(cnt $HU X1_stop X1_end 'Active session detected, bringing projection to front') fgs=$(cnt $HU X1_stop X1_end 'ForegroundServiceDidNotStartInTimeException') fatal=$(fatal S1_launch X1_end) bye=$(cnt $PH X1_stop X1_end 'received ByeByeRequest') crit=$(crit X1_stop X1_end) $(inert X1_open X1_end)" | tee -a $OUT/X1.txt
th_end
```

**S1 PASS** when `session=0`, `listen` 1 or more, `ver` 1 or more, `ssl` 1 or more, `tp` 1 or more, and `hct=0 retain=0 reader=0`. A missing `ver` with a session is a VERSION FAIL. No session: S1 FAIL, and X1 is UNTESTABLE (no session); go on to R2.

**X1 PASS** (the point of the round) when all of these hold:
1. Reach: the `open` line reads `rows` 2 or more and a `tap=` with `text=Stop_Connection`, and `tx_key=0`.
2. ROUTE: `disc=1`. On `main` this count is 0, because the Stop called `CommManager.disconnect()` directly.
3. CLEAN: `uexit=1`, `cool` 1 or more, and `bye` 1 or more.
4. NORAISE: `res=0` and `raise=0`.
5. `fgs=0`, `fatal=0`, `crit=0`, and `hct=0 retain=0 reader=0`.

FAIL on any of 2 to 5. Reach not met with `tx_key` 1 or more: UNTESTABLE (keymap). Reach not met with `tx_key=0`: give `X1-open.png` and the `open` line, and grade X1 FAIL (dialog).

### R2 and X3: re-arm, then the disconnect verb (6 min)

**`r2x3.sh`**
```bash
. $(dirname $0)/lib.sh
th_gate R2 || exit 1
mark R2_scan; send ACTION_START_WIRELESS_SCAN | tee $OUT/R2-send.txt
session R2_scan; echo "R2 session=$?" | tee $OUT/R2.txt
sleep 20
mark X3_disc; send ACTION_DISCONNECT | tee $OUT/X3-send.txt
sleep 15; mark X3_end
echo "R2: scan=$(cnt $HU R2_scan X3_disc 'WifiLauncher: Initializing WiFi Mode:') ver=$(cnt $HU R2_scan X3_disc 'NativeAA: [RX] WifiVersionResponse v') ssl=$(cnt $HU R2_scan X3_disc 'SSL handshake complete') tp=$(cnt $HU R2_scan X3_disc 'Throughput over') $(inert R2_scan X3_disc)" | tee -a $OUT/R2.txt
echo "X3: rx=$(cnt $HU X3_disc X3_end 'AutomationReceiver: ') disc=$(cnt $HU X3_disc X3_end 'Disconnect action received.') uexit=$(cnt $HU X3_disc X3_end 'AapService: Native AA user exit. Stopping active launcher.') cool=$(cnt $HU X3_disc X3_end 'User exit cooldown active for') fatal=$(fatal R2_scan X3_end) bye=$(cnt $PH X3_disc X3_end 'received ByeByeRequest') crit=$(crit X3_disc X3_end) $(inert X3_disc X3_end)" | tee -a $OUT/X3.txt
th_end
```

**R2 PASS** when `session=0`, `scan` 1 or more, `ver` 1 or more, `ssl` 1 or more, `tp` 1 or more, and `hct=0 retain=0 reader=0`. No session: R2 FAIL (REARM), and X3 is UNTESTABLE (no session).

**X3 PASS** when `rx` 1 or more, `disc=1`, `uexit=1`, `cool` 1 or more, `bye` 1 or more, `fatal=0`, `crit=0`, and `hct=0 retain=0 reader=0`. A missing `rx` voids the step; send it once more.

### C4: `ACTION_DISCONNECT` with no session (7 min)

The armed stack pokes D-POCO while D-POCO is off the air. Then the verb arrives with no session, as the overlay's Cancel now does. The run counts the pokes before and after, brings the phone back, and then uses the WiFi button verb.

**`c4.sh`**
```bash
. $(dirname $0)/lib.sh
th_gate C4 || exit 1
ph_off | tee $OUT/C4-radios-off.txt
sleep 25                                                    # let X3's 5 s user-exit cooldown pass
mark C4_arm; send ACTION_START_WIRELESS_SCAN | tee $OUT/C4-arm.txt
sleep 60; mark C4_disc; send ACTION_DISCONNECT | tee $OUT/C4-send.txt
sleep 60; ph_on | tee $OUT/C4-radios-on.txt; mark C4_phone_on
sleep 90; mark C4_scan; send ACTION_START_WIRELESS_SCAN | tee $OUT/C4-scan.txt
session C4_scan; echo "C4 session=$?" | tee $OUT/C4.txt
sleep 10; mark C4_end
echo "C4: poke_before=$(cnt $HU C4_arm C4_disc 'NativeAA: Attempting active poke to device') ssl_before=$(cnt $HU C4_arm C4_disc 'SSL handshake complete') disc=$(cnt $HU C4_disc C4_phone_on 'Disconnect action received.') nowake=$(cnt $HU C4_disc C4_phone_on 'NativeAA: deliberate session end; reopening listeners without an automatic wake.') poke_off=$(cnt $HU C4_disc C4_phone_on 'NativeAA: Attempting active poke to device') poke_on=$(cnt $HU C4_phone_on C4_scan 'NativeAA: Attempting active poke to device') skip=$(cnt $HU C4_disc C4_scan 'AapService: userExitedAA is true. Skipping auto-poke.') paused=$(cnt $HU C4_disc C4_scan 'AapService: automatic wake is paused by the session policy.') ssl_unasked=$(cnt $HU C4_phone_on C4_scan 'SSL handshake complete') poke_scan=$(cnt $HU C4_scan C4_end 'NativeAA: Attempting active poke to device') ssl_scan=$(cnt $HU C4_scan C4_end 'SSL handshake complete') fatal=$(fatal C4_arm C4_end) $(inert C4_arm C4_end)" | tee -a $OUT/C4.txt
th_end
```

C4 has two parts.

**WAKE (RECORD).** Report `poke_before`, `poke_off`, `poke_on`, `skip`, `paused` and `ssl_unasked` as numbers. The reach condition is `poke_before` 1 or more, `ssl_before=0` and `disc=1`. Without it, WAKE is INCONCLUSIVE (the loop was not poking before the verb). The review predicts `poke_off=0`, `poke_on=0`, `ssl_unasked=0` and `skip` or `paused` 1 or more. Any other result is the finding: give the numbers.

**C4 PASS** (REARM after the stand-down) when `session=0`, `poke_scan` 1 or more, `ssl_scan` 1 or more, `fatal=0`, and `hct=0 retain=0 reader=0`. FAIL otherwise.

### End of the runs

**`end.sh`**
```bash
. $(dirname $0)/lib.sh
mark ROUND_end; uncap
echo "whole round: $(inert S1_launch ROUND_end) fatal=$(fatal S1_launch ROUND_end) fgs=$(cnt $HU S1_launch ROUND_end 'ForegroundServiceDidNotStartInTimeException') match=$(cnt $HU S1_launch ROUND_end 'MATCH! Starting AapService')" | tee $OUT/round.txt
stopapp $D_HU
```

Discard check (`TESTING-TEMPLATE.md` §4): `match` MUST be 0. A `MATCH! Starting AapService` is a self-wake and voids the run whose window holds it; re-run that run once.

## 9. Restore

**`restore.sh`**
```bash
. $(dirname $0)/lib.sh
exec > >(tee $OUT/restore.txt) 2>&1
stopapp $D_HU
adb -s $D_HU install -r -d $OUT/before-hu.apk | tail -1
$RIG_TOOLKIT/apk_identity.sh $OUT/before-hu.apk $D_HU; echo "apk_identity exit $?"
adb -s $D_HU shell am force-stop $PKG; sleep 1
adb -s $D_HU push $OUT/backup-hu.xml /data/local/tmp/ohu-r1.xml >/dev/null
adb -s $D_HU shell cp /data/local/tmp/ohu-r1.xml /data/data/$PKG/shared_prefs/settings.xml
adb -s $D_HU shell chown $HU_OWNER /data/data/$PKG/shared_prefs/settings.xml; adb -s $D_HU shell chmod 660 /data/data/$PKG/shared_prefs/settings.xml
pull_settings | diff -B - $OUT/backup-hu.xml >/dev/null && echo SETTINGS_BACK || echo SETTINGS_DIFFER
ph_on
$RIG_TOOLKIT/rig_cleanup.sh list
$RIG_TOOLKIT/rig_preflight.sh D_HU:wifi
```

It SHOULD print `apk_identity exit 0` and `SETTINGS_BACK`. On `SETTINGS_DIFFER`, put the diff in Setup notes and leave the file. Leave D-MOTO's and D-SAM's radios as pre-flight found them only if pre-flight recorded them; otherwise leave them off and say so.

## 10. Do not re-run

- Nothing. This is the thread's first round.
- The HCT retention itself (Exit then Start on a kept stream, an Exit from the car screen, a parked stream against a fresh handoff) needs an HCT unit. It is not a rig question.
- The connection-mode dialog now applies a WiFi on or off change at once. Reaching that dialog needs a scrolled settings list, which §3 bans, so it is covered by reading `SettingsFragment` and by the JVM suite.

## 11. Report back

One verdict per run in a table at the top of the results file: S1, X1, R2, X3, C4 (GATE), WAKE (RECORD), INERT across the round. Then:

1. **X1:** the `open` line and the `X1:` counts line, with the `disc_at` timestamp and the `Disconnect action received.` line quoted.
2. **C4 WAKE:** `poke_before`, `poke_off`, `poke_on`, `skip`, `paused` and `ssl_unasked`, and the time from `C4_scan` to the first `SSL handshake complete`.
3. **INERT:** the `round.txt` line.

```decisive-strings
AutomationReceiver: 
AutomationMarker: 
WifiLauncher: Initializing WiFi Mode:
NativeAA: ACTIVELY LISTENING on Android Auto UUID
NativeAA: Attempting active poke to device
NativeAA: [RX] WifiVersionResponse v
WirelessServer: Incoming connection detected
SSL handshake complete
Throughput over
Disconnect action received.
AapService: Native AA user exit. Stopping active launcher.
User exit cooldown active for
NativeAA: deliberate session end; reopening listeners without an automatic wake.
AapService: userExitedAA is true. Skipping auto-poke.
AapService: automatic wake is paused by the session policy. Keeping listeners available.
AapProjectionActivity: onResume
MainActivity: Active session detected, bringing projection to front
NativeAA: [HCT]
NativeAA: [HCT] retaining the owned WPP channel after projection Exit
NativeAA: [HCT] physical WPP reader closed
FATAL EXCEPTION
```
