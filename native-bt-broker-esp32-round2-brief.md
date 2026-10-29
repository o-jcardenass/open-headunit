# native-bt-broker-esp32, round 2 brief: the contributor's fixes, on the matching firmware

Published name: `native-bt-broker-esp32-round2-brief.md`. Results go in `native-bt-broker-esp32-round2-results.md`.
Evidence: create the release `rig-evidence-native-bt-broker-esp32` again (it was swept) and add
`native-bt-broker-esp32-round2-captures.zip`.

**The point of the round is Stage B.** Round 1 ran PR #1187 on firmware that did not know the PR's commands, so no
session formed. The contributor has since published firmware that matches, added a switch, an API 23 guard, new
names and pairing steps. This round asks whether a Native AA session now forms through the board, and whether the
board drops the phone's hands-free link at SSL as the PR claims. We send the change requests after this round.

Units: D-HU (head unit), D-POCO (phone), board E-1 on the host's serial port, D-SAM (short Stage S at the end).
D-MOTO radios off. D-HP free. Board E-2 is not touched.

Queue line: `native-bt-broker-esp32-round2-brief.md` (C `f1bed4ee` on `fork/test/pr-1187-relay`, PR #1187 head
`adb0ae6b` merged onto `main` `a4f80ff1`; one build, no baseline, C1 and C2 are the controls; units D-HU, D-POCO,
E-1, D-SAM, D-MOTO radios off; B is the point).

Pairing: a round on D-HP and D-MOTO only. Hand steps: none planned; one cue (H1) only if an injected tap target does
not resolve. **Injected taps: up to 10 per run** (the operator raised the cap for this round), each on a node found by
exact text or resource id in a `uiautomator dump`, never a guessed coordinate.

**Order and time.** Prepare, build and flash 30 min; C1 10 min; P0 and P (forget and pair) 10 min; B 25 min; C2 10 min;
K 5 min; S 10 min; closing 10 min. **About 1 h 50 min.**

| Stage | What it proves | Min |
|---|---|---|
| C1 | control: stock route, no relay file | 10 |
| P0, P | the phone forgets the old bond, then pairs with the board with no hand step (contributor fix 7) | 10 |
| **B** | **the point: 5 Native bring-ups over the board; HFP drops at SSL; no error frames (fixes 1 and 2)** | 25 |
| C2 | board saved, switch off: the stock route comes back (fix 5) | 10 |
| K | switch on, board held in reset: what the user sees with the board gone | 5 |
| S | D-SAM (API 19): the screen opens and leaves with no crash (fix 3) | 10 |

## 1. Build and baseline

| | |
|---|---|
| Candidate | `fork/test/pr-1187-relay` `f1bed4ee42528a21a2ce03caebac4f214afdaf87`, tree `c90b77d66271c3cb0f41e6d615a8db609f415f75` |
| What it is | `main` `a4f80ff1b6aa6726c17e7385192cf23abe0d0e9f` plus PR #1187 head `adb0ae6b` as one merge commit |
| Conflict | one, in `NativeAaHandshakeManager.start`: #1163's HCT route against the relay's early return. Kept both, relay check first, as the PR intends |
| JVM suite | dev host at `f1bed4ee`: count in the README row. The PR adds no tests. Do not run tests on the rig |
| Firmware | `kendrickkim/aa-native-bt-broker-for-openheadunit` release `v1.0.0`, **assets replaced on 2026-10-26 21:01 UTC** under the same tag; source matches commit `efbe1809`. Release bins only, no build |

**The release kept its tag and changed `firmware.bin`.** Fetch it fresh, never from round 1's `fw/`. Round 1's
`firmware.bin` hash was `9e62a6b8...`; the new one is below. The other three bins are unchanged.

**prep2.sh**
```bash
RIG=$(dirname "$(find ~ -maxdepth 3 -type d -name rig-toolkit -print -quit)"); echo "$RIG"   # MUST print a non-empty path
RIG_TOOLKIT=$RIG/rig-toolkit
EV=$RIG/rig-data/rounds/native-bt-broker-esp32-round2; APKS=$RIG/rig-data/apks/native-bt-broker-esp32-round2
mkdir -p $EV/scripts $EV/fw $APKS
git fetch fork test/pr-1187-relay
git cat-file -t f1bed4ee42528a21a2ce03caebac4f214afdaf87       # MUST print commit
gh release download v1.0.0 --repo kendrickkim/aa-native-bt-broker-for-openheadunit --pattern '*.bin' --dir $EV/fw --clobber
cat > $EV/fw/SHA256SUMS <<'EOF'
22d12685b5d131492a54cc1ee491768ac4c3be93b7a10ba05fdabb3636533534  bootloader.bin
d90b0f480968f1cde746d7d8746261b38ec098b63c7f45b1aa23698cca4a2236  partitions.bin
f94c5d786a7a8fab06ac5d10e33bf37711a6697636dc037559ea19cc410a17f0  boot_app0.bin
2a6059dc22c7e54f5c931a64cef4d1458d4bb7f437b119c11fbdc553bfaad7ba  firmware.bin
EOF
( cd $EV/fw && sha256sum -c SHA256SUMS )                        # MUST print 4 lines ending in OK
strings -a $EV/fw/firmware.bin | grep -cF 'OpenHU-BT-Relay-Setup' # MUST print 1 or more
strings -a $EV/fw/firmware.bin | grep -cF 'HFP off ('             # MUST print 1
cp -r $RIG/rig-data/rounds/native-bt-broker-esp32-round1/venv $EV/venv 2>/dev/null || { python3 -m venv $EV/venv && $EV/venv/bin/pip install -q esptool; }
$EV/venv/bin/esptool version                                      # record the version
```

If `firmware.bin` does not match, the release changed again: record the new hash, check the two `strings` lines,
and go on only if both pass. Say so in Setup notes.

Make the build worktree with `$RIG_TOOLKIT/wt-new.sh`, name `bt-broker-r2`, at `f1bed4ee`. Copy `local.properties`
into it if it has none. Set `WT` to its path.

**build2.sh**
```bash
git -C $WT rev-parse 'HEAD^{tree}'      # MUST print c90b77d66271c3cb0f41e6d615a8db609f415f75
$RIG_TOOLKIT/rig_thermal.sh wait 70
HUR_DIR=$WT MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh
cp <the APK build_hur_cool.sh names> $APKS/C.apk && md5sum $APKS/C.apk
unzip -p $APKS/C.apk 'classes*.dex' | strings | grep -cF NativeBTBrokerCarrier    # MUST print 1 or more
```

Then run round 1's `dest.sh` unchanged (it reads `id/brokerSettingsFragment` from the APK), with `APKS` and `EV`
set to the round 2 paths. **Stop and escalate** if the tree differs, the build fails or the DEX count is 0.

## 2. What this is and why it exists

**The PR.** An original ESP32 carries the phone's Classic Bluetooth for Native AA and relays the Android Auto RFCOMM
bytes to the head unit over BLE. The head unit still runs the WPP handshake. The board brings the phone's
hands-free link up only to wake Android Auto (`CMD_HFP_ON`) and drops it at SSL (`CMD_HFP_OFF`).

**Round 1 (results and addendum on this branch).** The v1.0.0 release then answered `HFP_ON` with an error frame, so
every bring-up ended with `transport error (broker EVT_ERROR code=7)`. A build of `apply_hfp` did not pair and
crashed the board. D-SAM crashed at Scan (`NoSuchMethodError: getBluetoothLeScanner`). The app logged one line about
645 times per bring-up.

**What the contributor says is fixed (comment of 2026-10-26).**

1. The firmware release now matches the PR. The new `firmware.bin` holds `HFP_ON`, `HFP_OFF` and `HFP off (`.
2. `platformio.ini` now pins `espressif32@53.3.12`. This round uses release bins, so it does not test the build.
3. Below API 23 the relay row is disabled, and the relay screen leaves at once (`BrokerSettingsFragment`).
4. The `previous start is still on the way` line is now `AppLog.d`.
5. A **Use Bluetooth relay** switch (`enabled` in `NativeBTBroker.xml`, default false) gates the route. With it
   off, a saved board is kept and the stock route runs.
6. New names: Classic `OpenHU-BT-Relay`, BLE `OpenHU-BT-Relay-Setup`.
7. The screen lists the pairing order, and the firmware waits a few seconds after it clears an old bond.

**What the dev host found on the merged build, for the report, not graded:** the relay's early return skips
`closeRetainedHct()` (harmless on D-HU, which has no HCT); and `hasSavedBroker` does not check the connection mode,
so the relay's 10 s `PROJECTION_UP` rule could end a USB or Headunit Server session while the switch is on.

## 3. What is different about this round

- **No hand pairing.** P0 forgets the old bond on D-POCO with injected taps, and P pairs with injected taps on both
  units. A target that does not resolve cues H1 for that one step and the run goes on.
- **The relay switch is a file key.** `NativeBTBroker.xml` is written as root with the app stopped, like round 1's
  `broker_restore`. The UI switch is never tapped.
- **D-HU and D-POCO keep stale bonds from round 1.** D-POCO lists the board as `OpenHU-NativeBTBroker` (same MAC,
  `78:1C:3C:2B:AC:EE`); D-HU lists `OpenHU-NativeBTBroker-Setter`. P0 removes the phone's. D-HU's LE bond is left:
  the PR's pick re-bonds it. If P fails with `BLE bond failed`, that bond is the first suspect; say so.
- **Opening the serial port can reset the board.** A reset inside a cycle window voids the cycle, as in round 1.
- **Round 1's script errors are fixed below**: `hu_su` runs directly (D-HU's shell is root), `hu_hfp` matches the
  last two octets and `state=Connected`, `APKS` is defined, E-1 is found by MAC, and the block names are in
  backticks so `extract_brief_blocks.py` finds them.
- **K holds the board in reset** by opening its serial port with RTS high. No cable moves.
- **Expected INCONCLUSIVE:** B-g in a cycle where D-HU's own HFP link to D-POCO is not Connected before the launch.
- Read `rig-quirks/topics/tooling.md`, `topics/bt.md`, `topics/gearhead.md`, `units/D-HU.md`, `units/D-POCO.md`,
  `units/D-SAM.md`, `units/D-SAM-and-D-HP.md`, and `native-bt-broker-esp32-hardware-findings.md`.

## 4. Hand steps and injected taps

| Id | Step | When |
|---|---|---|
| H1 | Do the one step named on the cue line in `hand-steps.log` | only when an injected tap does not resolve |
| H2 | Forget `OpenHU-BT-Relay` on D-POCO and `OpenHU-BT-Relay-Setup` on D-HU | Closing, only if `forget_phone.sh` fails there |

| Run | Unit | Injected tap | Target in the dump |
|---|---|---|---|
| P0 | D-POCO | the gear on the board's row | resource id ending `settings_button`, in the row whose text is `OpenHU-NativeBTBroker` or `OpenHU-BT-Relay` |
| P0 | D-POCO | Forget | text `Forget` |
| P0 | D-POCO | confirm | text `Forget device` |
| P | D-HU | Scan | text `Look for OpenHU-BT-Relay-Setup` |
| P | D-HU | the found board | text `OpenHU-BT-Relay-Setup` |
| P | D-HU | a system pairing dialog, only if shown within 8 s | text `Pair` |
| P | D-HU | Pair phone | text `Pair phone` |
| P | D-POCO | Pair new device | text `Pair new device` |
| P | D-POCO | the board | text `OpenHU-BT-Relay` |
| P | D-POCO | confirm | text `Pair`, or `OK` after `input text 0000` when a PIN field shows |
| S | D-SAM | none | |

P uses at most 7 taps and P0 at most 3. Typing `0000` into a system PIN field is a scripted key entry into a system
dialog, not a search box; it is allowed this round.

## 5. Settings keys

D-HU is rooted: `$RIG_TOOLKIT/set_hu_settings_host.py` with the app stopped (call it through `python3`; it is not
executable), then read back. The keys are round 1's `KEYS` with `TGT` = D-POCO's MAC, unchanged:
`wifi-connection-mode` 3, `native-ap-transport` 0, `log-level` 2, `onboarding-version` 2,
`native-driver-selection-mode` 0, `native-preferred-device-mac` / `last-connected-native-mac` = TGT,
`native-poke-bt-macs` = {TGT}, `native-poke-all-paired` false, `auto-start-bt-macs` = {TGT},
`native-aa-wake-damage-verdict` 0, `native-aa-wake-armings-without-session` 0, delete `video-profile-starvation-cap`.

The relay is the file `shared_prefs/NativeBTBroker.xml`:

| Stage | File |
|---|---|
| C1 | absent |
| P | `enabled` true, no `address` (the pick writes it) |
| B, K | as P saved it, `enabled` true |
| C2 | as P saved it, `enabled` **false** |

D-SAM (Stage S): as round 1, `wifi-connection-mode` 3, `onboarding-version` 2, `log-level` 2,
`native-driver-selection-mode` 0, `native-poke-all-paired` false, `native-poke-bt-macs` cleared.

## 6. Shell setup

Save every block in `$EV/scripts/` (`SCR`). Copy `tap_text.py`, `serial_cap.py`, `dest.sh`, `flash.sh`,
`stageB.sh` and `dsam.sh` from round 1's patched copies in `rig-data/rounds/native-bt-broker-esp32-round1/scripts/`
(round 1's brief names them without backticks, so the extractor misses them there). The blocks below replace round 1's
`bb_env.sh`, `cycle.sh`, `grade_cycle.py` and `pair.sh`. Run each stage under `flock /tmp/ohu-rig.lock`, and start the serial capture with
`3>&-` so it holds no lock (round 1, error 4).

**`bb_env.sh`**
```bash
export PKG=com.andrerinas.headunitrevived
export HU=27870808938846 PH=4f4027e9 MOTO=ZY22GC3BM4 SAM=30041c35642d2200
export RIG=$(dirname "$(find ~ -maxdepth 3 -type d -name rig-toolkit -print -quit)")
export RIG_TOOLKIT=$RIG/rig-toolkit
export EV=$RIG/rig-data/rounds/native-bt-broker-esp32-round2 SCR=$RIG/rig-data/rounds/native-bt-broker-esp32-round2/scripts
export APKS=$RIG/rig-data/apks/native-bt-broker-esp32-round2; export APK=$APKS/C.apk VENV=$EV/venv SER=$EV/serial-E1.txt
export MAIN=$PKG/com.andrerinas.openheadunit.main.MainActivity SETT=$PKG/com.andrerinas.openheadunit.main.SettingsActivity
export SP=/data/data/$PKG/shared_prefs
[ -f $EV/ids.env ] && . $EV/ids.env     # E1 POCO HU_MAC BROKER_LE BROKER_BT DEST APPUID
: ${TGT:=$POCO}
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
send() { a=$1; shift; adb -s ${SEND_TO:-$HU} shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; }
tsh() { date +'%m-%d %H:%M:%S.%3N'; }
mark() { adb -s $HU shell log -t RIGMARK RIGMARK "$1"; adb -s $PH shell log -t RIGMARK RIGMARK "$1"
         echo "$(tsh) RIGMARK $1" >> $SER; echo "$(tsh) $1" >> $EV/marks.txt; }
cue() { echo "$(tsh) $*" >> $EV/hand-steps.log; }
waitline() { for i in $(seq 1 "$3"); do tail -n +"${4:-1}" "$1" 2>/dev/null | grep -aqF -- "$2" && return 0; sleep 1; done; return 1; }
nlines() { wc -l < "$1" 2>/dev/null || echo 0; }
th_gate() { $RIG_TOOLKIT/rig_thermal.sh wait 75 || $RIG_TOOLKIT/rig_thermal.sh wait 75; }
th_watch() { $RIG_TOOLKIT/rig_thermal.sh watch $EV/thermal-$1.log & echo $! > $EV/thermal-$1.pid; }
th_stop() { kill "$(cat $EV/thermal-$1.pid)" 2>/dev/null; }
cap_start() { adb -s $HU logcat -c && ( stdbuf -oL adb -s $HU logcat -v time > $EV/$1-hu.txt & echo $! > $EV/$1-hu.pid )
              adb -s $PH logcat -c && ( stdbuf -oL adb -s $PH logcat -v time > $EV/$1-ph.txt & echo $! > $EV/$1-ph.pid ); }
cap_stop() { sleep 1; kill "$(cat $EV/$1-hu.pid)" "$(cat $EV/$1-ph.pid)" 2>/dev/null; }
readers() { pgrep -fc "^adb .*logcat"; }
hu_su() { adb -s $HU shell "$1"; }                       # D-HU's adb shell is already uid 0
broker_saved() { hu_su "cat $SP/NativeBTBroker.xml" 2>/dev/null | grep -ao 'name="address">[^<]*' | cut -d'>' -f2; }
broker_enabled() { hu_su "cat $SP/NativeBTBroker.xml" 2>/dev/null | grep -ao 'name="enabled" value="[a-z]*"' | grep -o 'true\|false'; }
broker_forget() { hu_su "rm -f $SP/NativeBTBroker.xml"; }
# broker_write <true|false> [address] [name]: the whole file, app stopped
broker_write() { { echo "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>"; echo "<map>"
  echo "    <boolean name=\"enabled\" value=\"$1\" />"
  [ -n "$2" ] && echo "    <string name=\"address\">$2</string>"; [ -n "$3" ] && echo "    <string name=\"name\">$3</string>"
  echo "</map>"; } > $EV/NativeBTBroker-w.xml
  adb -s $HU push $EV/NativeBTBroker-w.xml /data/local/tmp/NativeBTBroker.xml >/dev/null
  hu_su "cp /data/local/tmp/NativeBTBroker.xml $SP/NativeBTBroker.xml && chown $APPUID:$APPUID $SP/NativeBTBroker.xml && chmod 660 $SP/NativeBTBroker.xml"
  [ "$(broker_enabled)" = "$1" ] || echo "STOP: broker file did not take"; }
KEYS() { echo "scalar:int:wifi-connection-mode:3 scalar:int:native-ap-transport:0 scalar:int:log-level:2 scalar:int:onboarding-version:2 scalar:int:native-driver-selection-mode:0 scalar:string:native-preferred-device-mac:$TGT scalar:string:last-connected-native-mac:$TGT set:native-poke-bt-macs:$TGT scalar:boolean:native-poke-all-paired:false set:auto-start-bt-macs:$TGT scalar:int:native-aa-wake-damage-verdict:0 scalar:int:native-aa-wake-armings-without-session:0 del:video-profile-starvation-cap"; }
keys_ok() { hu_su "cat $SP/settings.xml" > $EV/$1-settings.xml
  grep -aq 'name="wifi-connection-mode" value="3"' $EV/$1-settings.xml && grep -aq 'name="native-poke-all-paired" value="false"' $EV/$1-settings.xml \
  && ! grep -aq 'name="video-profile-starvation-cap"' $EV/$1-settings.xml && grep -aiqF "$TGT" $EV/$1-settings.xml && echo keys_ok ok; }
# D-HU masks MACs here and prints state=Connected under HeadsetClientService (round 1, error 3)
hu_hfp() { adb -s $HU shell dumpsys bluetooth_manager > $EV/.bt.txt
  awk '/HeadsetClientService/{f=1} f&&/^ *Profile:/&&!/HeadsetClient/{f=0} f' $EV/.bt.txt > "$1"
  if grep -aiqF "${TGT: -5}" "$1" && grep -aiqE 'state[=:] *Connected' "$1"; then echo CONNECTED; else echo NOT; fi; }
ph_hfp() { adb -s $PH shell dumpsys bluetooth_manager | awk '/HeadsetService/{f=1} f&&/^ *Profile:/&&!/HeadsetService/{f=0} f' > "$1"; }
hu_st() { adb -s $HU shell dumpsys bluetooth_manager | grep -a -m1 -E '^ *state:'; }
hu_bt_cycle() { adb -s $HU shell svc bluetooth disable
  for i in $(seq 1 10); do sleep 1; hu_st | grep -q ' ON' || break; done
  for i in $(seq 1 30); do sleep 1; hu_st | grep -q ' ON' && return 0; done
  adb -s $HU shell svc bluetooth enable; sleep 5; }
clk() { for d in $HU $PH $SAM; do adb -s $d get-state >/dev/null 2>&1 || continue
  echo "$d host=$(date +%s.%3N) dev=$(adb -s $d shell date +%s.%N 2>/dev/null | tr -d '\r')"; done | tee -a $EV/clocks.txt; }
# tap <unit> <label> <exact text>: up to 5 dumps 2 s apart; on no match, cue H1 and return 2
TAPS=0
tap() { for i in 1 2 3 4 5; do adb -s $1 shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb -s $1 pull /sdcard/ui.xml $EV/ui-$2.xml >/dev/null 2>&1
    xy=$(python3 -I $SCR/tap_text.py $EV/ui-$2.xml "$3") && break; sleep 2; done
  [ -n "$xy" ] || { echo "$(tsh) TAP_UNRESOLVED $1 $2" | tee -a $EV/taps.log; cue "H1: on $1, $4"; return 2; }
  TAPS=$((TAPS+1)); [ $TAPS -le 10 ] || { echo "$(tsh) TAP_BUDGET $2" | tee -a $EV/taps.log; return 3; }
  echo "$(tsh) tap $1 $2 $xy" >> $EV/taps.log; adb -s $1 shell input tap $xy; }
```

**`tap_gear.py`**
```python
# Print the centre of the gear (resource id ending settings_button) in the row that holds a node whose text is one of argv[2:].
import re, sys, xml.etree.ElementTree as E
root = E.parse(sys.argv[1]).getroot()
parent = {c: p for p in root.iter() for c in p}
hits = [n for n in root.iter("node") if n.get("text") in sys.argv[2:]]
if len(hits) != 1:
    print(f"tap_gear: {len(hits)} rows match", file=sys.stderr); sys.exit(2)
row = hits[0]
for _ in range(4):
    row = parent.get(row)
    if row is None: break
    gears = [n for n in row.iter("node") if (n.get("resource-id") or "").endswith("settings_button")]
    if len(gears) == 1:
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", gears[0].get("bounds"))); print((x1 + x2) // 2, (y1 + y2) // 2); sys.exit(0)
print("tap_gear: no single gear in the row", file=sys.stderr); sys.exit(2)
```

**`cycle.sh`**
```bash
# usage: cycle.sh <RUN> <k>. One Native bring-up from a stopped app, a 120 s hold after SSL, a user exit.
. $SCR/bb_env.sh; RUN=$1; K=$2; T=$RUN-c$K
adb -s $HU shell am force-stop $PKG; sleep 1
python3 $RIG_TOOLKIT/set_hu_settings_host.py $HU $(KEYS) >> $EV/keys.log 2>&1
[ "$(keys_ok $T)" = "keys_ok ok" ] || { echo "$T VOID keys" | tee -a $EV/$RUN.tsv; exit 9; }
B=$(broker_saved); E=$(broker_enabled)
case $RUN in
  B*|K*) [ -n "$B" ] && [ "$E" = true ] || { echo "$T VOID relay_not_on" | tee -a $EV/$RUN.tsv; exit 9; };;
  C1*)   [ -z "$B$E" ] || { echo "$T VOID relay_file_present" | tee -a $EV/$RUN.tsv; exit 9; };;
  C2*)   [ -n "$B" ] && [ "$E" = false ] || { echo "$T VOID relay_not_saved_off" | tee -a $EV/$RUN.tsv; exit 9; };;
esac
s=$(hu_hfp $EV/$T-hfp-pre.txt); relink=0
[ "$s" = CONNECTED ] || { hu_bt_cycle; for i in $(seq 1 30); do s=$(hu_hfp $EV/$T-hfp-pre.txt); [ "$s" = CONNECTED ] && break; sleep 1; done; relink=1; }
echo "$T relink=$relink" >> $EV/relink.log
ph_hfp $EV/$T-phfp-before.txt
adb -s $PH shell input keyevent KEYCODE_HOME
cap_start $T; mark $T-go
adb -s $HU shell am start -n $MAIN >/dev/null
if waitline $EV/$T-hu.txt 'SSL handshake complete' ${CYCLE_CAP:-120}; then mark $T-ssl; sleep 120; fi
mark $T-end
send ACTION_DISCONNECT > $EV/$T-disc.txt
sleep 15
a=$(hu_hfp $EV/$T-hfp-after.txt); ph_hfp $EV/$T-phfp-after.txt
mark $T-post; cap_stop $T
L=$(nlines $SER); adb -s $HU shell am force-stop $PKG
case $RUN in B*) waitline $SER 'head unit BLE disconnected' 15 $L; sleep 12;; esac
python3 -I $SCR/grade_cycle.py $EV/$T-hu.txt $EV/$T-ph.txt $SER $T $s $a | tee -a $EV/$RUN.tsv
```

**`grade_cycle.py`**
```python
# One TSV row per cycle: counts between RIGMARK labels in the unit, phone and serial captures.
# usage: grade_cycle.py <hu> <ph> <ser> <T> <hfp_before> <hfp_after>
import re, sys
from datetime import datetime
hu, ph, ser, T, hb, ha = sys.argv[1:7]
def load(p):
    try: return open(p, errors="replace").read().splitlines()
    except OSError: return []
def ts(l):
    m = re.match(r"(\d\d-\d\d) (\d\d:\d\d:\d\d\.\d{3})", l)
    return datetime.strptime("2026-" + m.group(1) + " " + m.group(2), "%Y-%m-%d %H:%M:%S.%f") if m else None
def at(L, lbl):
    idx = [i for i, l in enumerate(L) if l.rstrip().endswith("RIGMARK " + lbl)]
    return idx[-1] if idx else None          # the last one: the serial file is append-only (round 1, error 6)
def win(L, a, b):
    i, j = at(L, a), at(L, b)
    return [] if i is None else L[i:(j if j is not None and j > i else len(L))]
def n(W, s): return sum(s in l for l in W)
H, P, S = load(hu), load(ph), load(ser)
go, ssl, end, post = T + "-go", T + "-ssl", T + "-end", T + "-post"
if at(H, go) is None or at(H, post) is None: print(f"{T}\tVOID markers_missing"); sys.exit(0)
Ha, Pa, Sa = win(H, go, post), win(P, go, post), win(S, go, post)
has_ssl = at(H, ssl) is not None
Hh = win(H, ssl, end) if has_ssl else []
Ph = win(P, ssl, end) if has_ssl else []
Se = win(S, go, end)
Hp = win(H, go, ssl) if has_ssl else win(H, go, end)
Sp = win(S, go, ssl) if has_ssl else Se
t_ssl = "-"
f = next((l for l in Ha if "SSL handshake complete" in l), None)
if f and ts(f) and ts(Ha[0]): t_ssl = f"{(ts(f) - ts(Ha[0])).total_seconds():.1f}"
pu = next((l for l in Se if "rx frame type=07 PROJECTION_UP" in l), None)
rel = "-"
if pu and ts(pu):
    rel = "no"
    for l in Se:
        if ("HFP off (" in l or "HFP HF state 0" in l) and ts(l) and 0 <= (ts(l) - ts(pu)).total_seconds() <= 10: rel = "yes"; break
st = [re.search(r"HFP HF state (\d)", l).group(1) for l in Se if re.search(r"HFP HF state \d", l)]
st_post = [re.search(r"HFP HF state (\d)", l).group(1) for l in Sa if re.search(r"HFP HF state \d", l)]
macs = sorted({m.group(1).upper() for l in Pa for m in [re.search(r"Creating rfcomm socket for device: ([0-9A-Fa-f:]{17})", l)] if m})
void = []
if n(Sa, "Classic name OpenHU-BT-Relay, BLE name"): void.append("board_reset")
if n(Ha, "AutomationReceiver: ") == 0: void.append("no_verb_line")
if n(Ha, "WifiLauncher: Initializing WiFi Mode:") == 0: void.append("not_armed")
row = dict(
    ssl=n(Ha, "SSL handshake complete"), t_ssl=t_ssl, thr=n(Hh, "Throughput over"),
    relay_on=n(Ha, "NativeAA: Bluetooth relay transport is on"), listening=n(Ha, "ACTIVELY LISTENING on Android Auto UUID"),
    poke=n(Ha, "NativeAA: Attempting active poke to device"), wake=n(Ha, "Broker: intentional AA wake (HFP) to "),
    hf_up=n(Ha, "NativeAA: [Broker] hands-free link up to "),
    rf_open=n(Ha, "NativeAA: [Broker] the phone opened Android Auto on the broker."),
    rf_close=n(Ha, "NativeAA: [Broker] the phone closed the Android Auto channel."),
    reopen=n(Ha, "the phone reopened Android Auto while a session is up"),
    terr=n(Ha, "NativeAA: [Broker] transport error ("), terr_code=n(Ha, "broker EVT_ERROR code="),
    terr_pre=n(Hp, "NativeAA: [Broker] transport error ("), hs_ended=n(Ha, "NativeAA: [Broker] handshake ended: "),
    hfp_release=n(Ha, "Broker: releasing HFP ("), proj_up=n(Ha, "Broker: projection socket up"),
    held=n(Ha, "WiFi session landed. Holding the Bluetooth channel"), held_rel=n(Ha, "releasing the held Bluetooth channel after"),
    proj_fail=n(Ha, "PROJECTION_UP not recorded for 10s") + n(Ha, "Broker: PROJECTION_UP failed for"),
    link_lost=n(Ha, "Broker: link lost, retrying for "), still_down=n(Ha, "Broker: still down after "),
    flood=n(Ha, "previous start is still on the way"),
    match=n(Ha, "MATCH! Starting AapService"), btconn=n(Ha, "BT Device connected: "),
    groups=n(Ha, "createGroup SUCCESS"), incoming=n(Ha, "WirelessServer: Incoming connection detected from"),
    pdial=n(Pa, "Creating rfcomm socket for device"), pdial_hold=n(Ph, "Creating rfcomm socket for device"),
    wpp_restart_hold=n(Ph, "Triggering WPP restart"), rf_fail_hold=n(Ph, "Rfcomm socket connection failed"),
    ping_timeout=n(Pa, "has timed out on pings"), crit=n(Pa, "Critical error"), pdial_macs=",".join(macs) or "-",
    s_ble_conn=n(Sa, "head unit BLE connected"), s_aa_enable=n(Sa, "rx frame type=03 AA_ENABLE"),
    s_hfp_on=n(Sa, "rx frame type=08 HFP_ON"), s_hfp_off=n(Sa, "rx frame type=09 HFP_OFF"),
    s_unknown=n(Sa, "UNKNOWN"), s_err=n(Sa, "tx frame type=26 ERROR"), s_err_pre=n(Sp, "tx frame type=26 ERROR"),
    s_hfp_on_line=n(Sa, "HFP on ("), s_hfp_off_line=n(Sa, "HFP off ("), s_hfp_disc=n(Sa, "HFP disconnect "),
    s_hfp_refused=n(Sa, "HFP refused"), s_rf_open=n(Sa, "phone RFCOMM connected, handle"), s_rf_close=n(Sa, "phone RFCOMM closed"),
    s_panic=n(Sa, "Guru Meditation Error"),
    hfp_rel=rel, hfp_states_to_end=">".join(st) or "-", hfp_last_post=(st_post[-1] if st_post else "-"),
    s_audio=n(Sa, "HFP audio state "), hu_hfp_before=hb, hu_hfp_after=ha)
print(T + "\t" + ("VOID " + ",".join(void) + "\t" if void else "") + "\t".join(f"{k}={v}" for k, v in row.items()))
```

## 7. The lines that decide the runs

**App lines**, checked with `git grep -F` under `app/src/main` at `f1bed4ee`. All INFO or WARN except where noted.
They are the strings in `grade_cycle.py`. New or renamed since round 1:

| Line | Meaning |
|---|---|
| `NativeAA: Bluetooth relay transport is on` | the relay route was chosen (round 1 read `Native BT Broker transport is on`) |
| `Broker: releasing HFP (` | the app sent HFP_OFF |
| `Broker: projection socket ` | `up` or `down` follows |
| `Broker: still down after ` | the board stayed unreachable for the retry window (K) |
| `Broker: opening new-phone pairing window on` | Stage P |
| `Broker: phone pairing confirmed` | Stage P, the app saw the pair complete |
| `previous start is still on the way` | now `AppLog.d`: at `log-level` 2 it MUST count 0 |

**Firmware lines**, from `src/` at `efbe1809`, each also in the release `firmware.bin` (`strings`):

| Line | Meaning |
|---|---|
| `Classic name OpenHU-BT-Relay, BLE name OpenHU-BT-Relay-Setup` | a boot (inside a cycle voids it) |
| `advertising OpenHU-BT-Relay-Setup` | BLE is up |
| `rx frame type=08 HFP_ON`, `rx frame type=09 HFP_OFF` | the PR's commands, now named |
| `HFP on (`, `HFP off (`, `HFP disconnect ` | the board raises and drops the phone's hands-free link |
| `HFP refused` | the phone tried hands-free while not armed |
| `tx frame type=26 ERROR` | an error frame to the head unit: MUST be 0 in B |
| `cleared phone bonds for new pairing`, `pairing mode for `, `pairing mode ended`, `phone paired: ` | Stage P |
| `no classic bonds to clear` | Stage P, nothing old on the board |
| `head unit BLE connected`, `head unit BLE disconnected`, `head unit BLE down for 10s; closing the phone link` | the GATT link |
| `Guru Meditation Error` | a board panic (round 1 saw them on `apply_hfp`) |

**Phone lines** (Gearhead 18.1, not decompiled; the 18.0 strings held in round 1): `Creating rfcomm socket for
device` (the MAC follows), `Triggering WPP restart`, `Rfcomm socket connection failed`, `has timed out on pings`,
`Critical error`.

## 8. Runs

**Preflight in every stage.** `readers` prints 0 before the first `cap_start`. `th_gate` passes, then `th_watch`.
The serial capture is alive. Every `send` prints `AutomationReceiver: <action>`, or the cycle is void.

### Prepare

1. `prep2.sh`, `build2.sh`, round 1's `dest.sh`. Save the §6 blocks. Write `ids.env` as values arrive.
2. **D-POCO:** reboot, wait for `sys.boot_completed` 1 (cap 180 s), `svc wifi enable`, `svc bluetooth enable`, read
   both back with `dumpsys`. Record Gearhead's `versionName`.
3. **D-MOTO off the link:** `svc bluetooth disable`, `svc wifi disable`, read back.
4. **D-HU:** force-stop. Back up `settings.xml` and the installed APK (`$APKS/prior-hu.apk`, md5). Record
   `stat -c "%U:%G %a" $SP`; chown it to the app's user if it is not. Write `APPUID`. `broker_forget`.
   Install `C.apk`, pull it back, match md5, `send ACTION_QUERY_STATE`: `commit` MUST start with `f1bed4ee`.
   Check `granted=true` for `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `ACCESS_FINE_LOCATION` in `dumpsys package`
   (round 1: `pm grant` is refused, and they were granted).
5. **MACs:** `POCO` from D-HU's bonded list, `HU_MAC` from D-POCO's. Write both in upper case.
6. **E-1:** list `/dev/serial/by-path/`, and for each `usb-*port0` path run `$VENV/bin/esptool --port <path> read-mac`.
   The one that prints `78:1c:3c:2b:ac:ec` is E-1: write `E1=<path>`. Never open E-2's port again after you know it.
   Flash with round 1's `flash.sh` (its `$E1` from `ids.env`), with its two greps changed to
   `advertising OpenHU-BT-Relay-Setup` and `Classic name OpenHU-BT-Relay, BLE name`, and the serial capture started
   as `setsid nohup ... 3>&- &`. `Hash of data verified` MUST print 4.
7. `clk`. Record the three offsets.

### Stage C1. Control: stock route, no relay file (2 cycles)

`for k in 1 2; do bash $SCR/cycle.sh C1 $k; done`

- **PASS per cycle:** `ssl` 1 or more, `t_ssl` 120 or less, `thr` 18 or more, `listening` 1 or more, `relay_on` 0,
  `s_ble_conn` 0, `pdial_macs` contains `HU_MAC`. **Run PASS** when both pass.
- **Stop rule:** 0 of 2 sessions: reboot D-POCO once and run C1 again. 0 of 2 again: B, C2 and K are UNTESTABLE (rig).

### Stage P0. D-POCO forgets the old board bond (at most 3 taps)

**`forget_phone.sh`**
```bash
. $SCR/bb_env.sh; TAPS=0
adb -s $PH shell am start -a android.settings.BLUETOOTH_SETTINGS >/dev/null; sleep 3
adb -s $PH shell uiautomator dump /sdcard/ui.xml >/dev/null; adb -s $PH pull /sdcard/ui.xml $EV/ui-p0-list.xml >/dev/null
xy=$(python3 -I $SCR/tap_gear.py $EV/ui-p0-list.xml OpenHU-NativeBTBroker OpenHU-BT-Relay) || { echo "p0: no old bond row" | tee -a $EV/pair.log; exit 0; }
TAPS=1; echo "$(tsh) tap $PH p0-gear $xy" >> $EV/taps.log; adb -s $PH shell input tap $xy; sleep 2
tap $PH p0-forget 'Forget' 'tap Forget on the board details page' || exit 4; sleep 2
tap $PH p0-confirm 'Forget device' 'confirm Forget device' || exit 4; sleep 2
adb -s $PH shell dumpsys bluetooth_manager | grep -a -A14 'Bonded devices' | tee $EV/poco-bonded-after-forget.txt
grep -aiqF '78:1C:3C:2B:AC:EE' $EV/poco-bonded-after-forget.txt && echo "p0: bond still listed" | tee -a $EV/pair.log
adb -s $PH shell input keyevent KEYCODE_HOME
```

- **PASS:** the board's MAC is not in D-POCO's bonded list afterwards, or there was no old row. Report the taps.

### Stage P. Pair the board, no hand step (at most 7 taps)

**`pair2.sh`**
```bash
. $SCR/bb_env.sh; TAPS=0; adb -s $HU shell am force-stop $PKG
broker_write true
cap_start PAIR; mark PAIR-go; L=$(nlines $SER)
hu_su "am start -n $SETT --ei extra_destination $DEST"; sleep 4
adb -s $HU shell screencap -p /sdcard/relay.png; adb -s $HU pull /sdcard/relay.png $EV/PAIR-screen.png >/dev/null
tap $HU p-scan 'Look for OpenHU-BT-Relay-Setup' 'tap Scan on the relay screen' || { mark PAIR-end; cap_stop PAIR; exit 4; }
sleep 6; tap $HU p-pick 'OpenHU-BT-Relay-Setup' 'tap the found OpenHU-BT-Relay-Setup' || { mark PAIR-end; cap_stop PAIR; exit 5; }
for i in 1 2 3 4; do sleep 2; adb -s $HU shell uiautomator dump /sdcard/ui.xml >/dev/null; adb -s $HU pull /sdcard/ui.xml $EV/ui-p-dlg.xml >/dev/null
  python3 -I $SCR/tap_text.py $EV/ui-p-dlg.xml 'Pair' >/dev/null 2>&1 && { tap $HU p-hudlg 'Pair' 'accept the pairing dialog'; break; }; done
waitline $SER 'BLE bond ' 15 $L; grep -a 'BLE bond ' $SER | tail -1 | tee -a $EV/pair.log
adb -s $PH shell am start -a android.settings.BLUETOOTH_SETTINGS >/dev/null; sleep 2
tap $HU p-pair 'Pair phone' 'tap Pair phone' || { mark PAIR-end; cap_stop PAIR; exit 6; }
waitline $SER 'pairing mode for ' 10 $L || echo "p: no pairing window" | tee -a $EV/pair.log
tap $PH p-new 'Pair new device' 'tap Pair new device on D-POCO'
for i in $(seq 1 15); do sleep 2; adb -s $PH shell uiautomator dump /sdcard/ui.xml >/dev/null; adb -s $PH pull /sdcard/ui.xml $EV/ui-p-dev.xml >/dev/null
  python3 -I $SCR/tap_text.py $EV/ui-p-dev.xml 'OpenHU-BT-Relay' >/dev/null 2>&1 && break; done
tap $PH p-dev 'OpenHU-BT-Relay' 'tap OpenHU-BT-Relay in the list'; sleep 3
adb -s $PH shell uiautomator dump /sdcard/ui.xml >/dev/null; adb -s $PH pull /sdcard/ui.xml $EV/ui-p-pin.xml >/dev/null
if grep -aq 'class="android.widget.EditText"' $EV/ui-p-pin.xml; then
  adb -s $PH shell input text 0000; echo "$(tsh) text $PH 0000" >> $EV/taps.log; tap $PH p-ok 'OK' 'tap OK on the PIN dialog'
else tap $PH p-confirm 'Pair' 'tap Pair on the pairing dialog'; fi
waitline $SER 'phone paired: ' 120 $L; echo "paired=$?" | tee -a $EV/pair.log
sleep 3; adb -s $HU shell input keyevent 4; sleep 2; mark PAIR-end; cap_stop PAIR
adb -s $HU shell am force-stop $PKG
broker_saved | tee -a $EV/pair.log; broker_enabled | tee -a $EV/pair.log
hu_su "cat $SP/NativeBTBroker.xml" > $EV/NativeBTBroker-saved.xml
adb -s $PH shell dumpsys bluetooth_manager | grep -a -A14 'Bonded devices' | tee $EV/poco-bonded-after-pair.txt
adb -s $PH shell input keyevent KEYCODE_HOME
```

- Write `BROKER_LE` (`broker_saved`) and `BROKER_BT` (D-POCO's bonded line for `OpenHU-BT-Relay`) to `ids.env`.
- **PASS:** `paired=0`; `broker_saved` prints a MAC and `broker_enabled` prints `true`; D-POCO's bonded list names
  `OpenHU-BT-Relay`; the PAIR capture has `Broker: opening new-phone pairing window on` 1 or more; and serial shows no
  `pairing mode ended` within 5 s of `cleared phone bonds for new pairing` (fix 7). Report `BLE bond`, every serial
  line from `rx frame type=06 SET_BOND_MODE` to `phone paired: `, the taps used and any H1 cue.
- **If p-pick does not resolve twice**, write the file by hand: `broker_write true 78:1C:3C:2B:AC:EE OpenHU-BT-Relay-Setup`,
  reopen the screen and go on from `Pair phone`. Say so: the PR's scan did not find the board.
- **Stop rule:** two failed pairings make B, C2 and K UNTESTABLE (pairing). Go to Stage S.

### Stage B. Five bring-ups over the board. B IS THE POINT

Round 1's `stageB.sh`, unchanged, run as `bash $SCR/stageB.sh B` (it calls the new `cycle.sh`). A void cycle is run
once more as the next `k`, at most 2 per run. Grade each row:

| Id | Condition |
|---|---|
| B-a | `ssl` 1 or more and `t_ssl` 120 or less |
| B-b | `thr` 18 or more |
| B-c | `relay_on` 1 or more, `listening` 0, `poke` 0, `rf_open` 1 or more, `pdial_macs` contains `BROKER_BT` |
| B-d | `hfp_rel` = `yes` (HFP off within 10 s of the first `PROJECTION_UP`) and `hfp_release` 1 or more |
| B-e | `s_err` 0, `s_unknown` 0, `terr` 0, `s_panic` 0 (fixes 1 and 2) |
| B-f | phone: `pdial_hold` 2 or less, `wpp_restart_hold` 2 or less, `crit` 0 outside 2 s after the Exit. FAIL at `pdial_hold` 10 or more |
| B-g | `hu_hfp_after` equals `hu_hfp_before`, read only when `hu_hfp_before` = `CONNECTED` |
| B-h | `proj_fail` 0, `flood` 0 (fix 4) |

- **Run verdict.** PASS when B-a to B-h hold in 4 or more cycles. FAIL when B-a fails in 3 or more, or B-d, B-e or B-f
  fails in 2 or more. INCONCLUSIVE otherwise.
- **What a PASS would look like if the board did nothing:** B-c separates it. A session over D-HU's own radio shows
  `listening` 1 or more and `pdial_macs` = `HU_MAC` only.
- **For each `ssl=0` cycle**, quote every `transport error (` and `handshake ended: ` line with 3 s of unit lines
  around it, and the serial lines from `rx frame type=03 AA_ENABLE` to `-end`.
- **Report per cycle:** the TSV row and `relink.log`; the `releasing the held Bluetooth channel after` line with its
  ping count; and, for cycles 1 and 5, which devices the two `phfp` files list as connected.

### Stage C2. Board saved, switch off: the stock route (2 cycles)

```bash
. $SCR/bb_env.sh; adb -s $HU shell am force-stop $PKG; broker_write false $BROKER_LE OpenHU-BT-Relay-Setup
for k in 1 2; do bash $SCR/cycle.sh C2 $k; done
```

- **PASS per cycle (fix 5):** the C1 conditions, plus `relay_on` 0 and `s_ble_conn` 0. **Run PASS** when both pass.
  A `relay_on` above 0 is a FAIL: the switch does not gate the route.

### Stage K. Switch on, board held in reset (1 cycle)

```bash
. $SCR/bb_env.sh; kill "$(cat $EV/ser.pid)"; sleep 1
setsid nohup $VENV/bin/python -I -c "import serial,time; s=serial.Serial(); s.port='$E1'; s.baudrate=115200; s.rts=True; s.dtr=False; s.open(); time.sleep(600)" >/dev/null 2>&1 3>&- & echo $! > $EV/hold.pid
adb -s $HU shell am force-stop $PKG; broker_write true $BROKER_LE OpenHU-BT-Relay-Setup
CYCLE_CAP=150 bash $SCR/cycle.sh K 1
kill "$(cat $EV/hold.pid)"; sleep 1
setsid nohup stdbuf -oL $VENV/bin/python -I -u $SCR/serial_cap.py $E1 $SER --reset > $EV/serial_cap.err 2>&1 3>&- & echo $! > $EV/ser.pid
waitline $SER 'advertising OpenHU-BT-Relay-Setup' 20 $(nlines $SER) || echo "K: board did not come back" | tee -a $EV/K.log
```

- **Not graded PASS or FAIL. Report:** `relay_on`, `ssl`, `listening`, `link_lost`, `still_down`, and every unit line
  matching `Broker:` from `K-c1-go` to `K-c1-end`, with timestamps. The question for the change request is whether
  Native mode falls back to the stock route or says why it waits. `ssl` 1 with `listening` 1 means it fell back.
- `s.rts=True` holds EN low, so the board stays in reset. If serial shows any boot line during K, the hold did not
  work: say so and mark K INCONCLUSIVE.

### Stage S. D-SAM (API 19): the relay screen with the API 23 guard

Round 1's `dsam.sh`, with three changes: `EV` and `APKS` are round 2's; `commit` MUST start with `f1bed4ee`; and
replace the `tap $SAM s-scan ...` line with `sleep 8; adb -s $SAM shell dumpsys activity activities | grep -a -m2 -E 'mResumedActivity|topResumedActivity' | tee -a $EV/S.log`.
D-SAM already carries round 1's candidate APK and no prior build (round 1, error 8): back up the APK that is there
before the install, and restore that one.

- **PASS (fix 3):** no `FATAL EXCEPTION` naming `com.andrerinas` within 15 s of the screen start, and the resumed
  activity line names `SettingsActivity` (the relay screen left at once to the settings list, which is the guard).
- **FAIL:** a `FATAL EXCEPTION`; quote its first 20 lines.

### Closing

1. **D-HU:** `headunit://exit`, force-stop, `broker_forget`, reinstall `prior-hu.apk` and check md5, restore
   `settings.xml` as root (cp, `chown $APPUID:$APPUID`, `chmod 660`), and diff: it MUST be empty.
2. **D-POCO:** run `forget_phone.sh` again (it finds the `OpenHU-BT-Relay` row now), so a later round does not meet a
   stale bond. D-HU keeps its LE bond to `OpenHU-BT-Relay-Setup`; list it in the results.
3. **E-1:** kill the serial capture and any hold by pid. Leave the round 2 release firmware on it.
4. D-MOTO radios as at the start. `clk`. `readers` prints 0. Kill thermal watchers.
5. Zip `$EV` without `venv/` and `fw/` (keep `fw/SHA256SUMS`), and without any `settings*.xml` (they hold
   passphrases). Upload to the release. Put size and sha256 in the results file.

**Stop rules, as counts.** C1: 0 of 2 twice stops B, C2 and K. P: 2 failed pairings stop B, C2 and K. B: 5 cycles, or
3 in a row with `ssl=0`. Thermal: a second failed wait stops the round.

## 9. Do not re-run

- The `apply_hfp` branch build and its panics: the contributor's release replaces it.
- Stage M (calls): not this round. It needs a callee; the change request asks the contributor for a call test.
- The stock route's held RFCOMM and re-dial cadence: settled in `hold-aa-rfcomm` and `late-listener-poke`.

## 10. Report back

1. Stage B: sessions out of 5, median `t_ssl`, and B-d, B-e and B-f per cycle.
2. C2 and S: did the switch gate the route, and did the API guard hold.
3. P: taps used, any H1 cue, and the serial lines around the bond clear. K: what the unit logged with the board gone.
