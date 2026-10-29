# native-bt-broker-esp32, round 1 brief

Published name on the transfer branch: `native-bt-broker-esp32-round1-brief.md`. Results file: `native-bt-broker-esp32-round1-results.md`. Evidence: create the release `rig-evidence-native-bt-broker-esp32` and add the asset `native-bt-broker-esp32-round1-captures.zip` (template §7).

**The point of the round is Stage B2, read against Stage B.** A contributor PR moves the Bluetooth half of Native AA onto an ESP32 board. The board talks to the phone over Classic Bluetooth and relays the bytes to the head unit over BLE. Stage B runs 5 Native bring-ups on the released firmware `v1.0.0`, which does not know the PR's HFP commands, and records what that mismatch costs. Stage B2 runs the same 5 bring-ups on the firmware branch `apply_hfp`, which does. Both arms use one APK build.

**Queue:** units D-HU (head unit), D-POCO (phone), board E-1 on the host's serial port, D-MOTO (optional call stage; its radios are off before that), D-SAM (short stage at the end). D-HP is free. Board E-2 is not touched. This round cannot pair with another round that uses D-HU, D-POCO or D-MOTO (template §3b).

**Order and time:** Prepare, build and flash 35 min; Stage C1 10 min; Stage P (pairing) 10 min; Stage B (`v1.0.0`) 30 min; Prepare-2 (build and flash `apply_hfp`) 25 min; Stage B2 (`apply_hfp`) 30 min; Stage C2 10 min; Stage M on `apply_hfp` 25 min (optional); Stage S 15 min; Closing 15 min. **About 3 h 25 min**, plus thermal waits. Each voided cycle adds 5 min.

## 1. Build and baseline

| | |
|---|---|
| Candidate | PR head `792989746b8f71b03562e44e067ee4dd47594c4f` (contributor branch `main-aa-native-bt-broker`), 3 commits on `main` `f9a72d480d51105870f62df6f0a40a1276e98a34` |
| Tree | `6918b60f1739e57c57762dc9eed6eac877560ea5` |
| Baseline | none. Stages C1 and C2 are the control, on the same APK with no broker saved |
| History | round 1, nothing rewritten |
| JVM suite | dev host at `79298974`: 4031 tests in 437 classes, 0 failures, 0 errors, 0 skipped (2026-10-25). The PR adds no tests. Do not run tests on the rig |
| Firmware arm 1 | `kendrickkim/aa-native-bt-broker-for-openheadunit` release `v1.0.0`, commit `2a1cb58c21cfb9d73ec2fee2e4ebb917aa62360a`, release bins (Stages C1, P, B) |
| Firmware arm 2 | same repository, branch `apply_hfp` at `21e0eb48f1ba8afb3f2f37795b2f8fa9c80488ec`, no release, built on the rig with PlatformIO (Prepare-2; Stages B2, C2, M) |

The candidate is not on `fork`. It is the upstream pull ref.

**prep.sh**

```bash
RIG=$(dirname "$(find ~ -maxdepth 3 -type d -name rig-toolkit -print -quit)"); echo "$RIG"   # MUST print a non-empty path
RIG_TOOLKIT=$RIG/rig-toolkit
EV=$RIG/rig-data/rounds/native-bt-broker-esp32-round1; APKS=$RIG/rig-data/apks/native-bt-broker-esp32-round1
mkdir -p $EV/scripts $EV/fw $APKS
git fetch origin refs/pull/1187/head
git cat-file -t 792989746b8f71b03562e44e067ee4dd47594c4f      # MUST print commit
gh release download v1.0.0 --repo kendrickkim/aa-native-bt-broker-for-openheadunit --pattern '*.bin' --dir $EV/fw
cat > $EV/fw/SHA256SUMS <<'EOF'
22d12685b5d131492a54cc1ee491768ac4c3be93b7a10ba05fdabb3636533534  bootloader.bin
d90b0f480968f1cde746d7d8746261b38ec098b63c7f45b1aa23698cca4a2236  partitions.bin
f94c5d786a7a8fab06ac5d10e33bf37711a6697636dc037559ea19cc410a17f0  boot_app0.bin
9e62a6b87ab535e25e2da83389d7378d2cd56a3ee4adcde4b72b8b425314496b  firmware.bin
EOF
( cd $EV/fw && sha256sum -c SHA256SUMS )                        # MUST print 4 lines ending in OK
python3 -m venv $EV/venv && $EV/venv/bin/pip install -q esptool  # esptool brings pyserial
$EV/venv/bin/esptool version                                     # record the version
```

Make the build worktree with `$RIG_TOOLKIT/wt-new.sh` (usage in `$RIG_TOOLKIT/TOOLS.md`), name `bt-broker-r1`, at `792989746b8f71b03562e44e067ee4dd47594c4f`. Set `WT` to its path. Copy `local.properties` into `$WT` if it has none. No other queued round shares this base.

**build.sh**

```bash
git -C $WT rev-parse 'HEAD^{tree}'      # MUST print 6918b60f1739e57c57762dc9eed6eac877560ea5
$RIG_TOOLKIT/rig_thermal.sh wait 70
HUR_DIR=$WT MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh
# build_hur_cool.sh writes to rig-data/apks/, not $WT/apks (usb-dongle-regression round 1). Copy the one APK it names:
cp <the APK build_hur_cool.sh names> $APKS/C.apk && md5sum $APKS/C.apk
unzip -p $APKS/C.apk 'classes*.dex' | strings | grep -cF NativeBTBrokerCarrier    # MUST print 1 or more
```

**Stop and escalate** if the tree differs, the build fails, a firmware hash fails or the DEX count is 0.

**The navigation id of the broker screen.** `SettingsActivity --ei extra_destination <id>` opens any nav-graph fragment, and the broker screen is one (`brokerSettingsFragment`). Find `aapt2` and read the id from the APK:

**dest.sh**

```bash
A2=$(find ~/Android ~/.android /opt -name aapt2 -type f -perm -u+x 2>/dev/null | sort | tail -1)
if [ -z "$A2" ]; then J=$(find ~/.gradle/caches -name 'aapt2-*-linux.jar' 2>/dev/null | sort | tail -1)
  [ -n "$J" ] && mkdir -p $EV/aapt2 && unzip -o -q "$J" aapt2 -d $EV/aapt2 && chmod +x $EV/aapt2/aapt2 && A2=$EV/aapt2/aapt2; fi
echo "aapt2=$A2"
H=$($A2 dump resources $APKS/C.apk | grep -a -m1 -E 'resource 0x[0-9a-f]+ id/brokerSettingsFragment' | grep -o '0x[0-9a-f]*')
echo "DEST=$((H))" >> $EV/ids.env; tail -1 $EV/ids.env     # MUST print DEST=<a positive decimal>
```

If no `aapt2` is found, write `DEST=` (empty) to `ids.env`. Hand step H3 then opens the screen.

## 2. What this is and why it exists

**The PR.** It adds a "Native BT Broker" settings screen and the package `connection/broker/`. A saved broker changes the Native AA route with no other switch: `NativeAaHandshakeManager.start` calls `startOverBrokerModule()` whenever `NativeBTBrokerSession.hasSavedBroker()` is true. Then no RFCOMM listener opens on the head unit and no wake poke goes out. The board brings up hands-free (HFP) to the phone, the phone opens Android Auto's RFCOMM channel on the board, and the board relays the WPP bytes over GATT. Only "Forget broker" undoes the route. The saved broker lives in its own prefs file, `shared_prefs/NativeBTBroker.xml`, keys `address` and `name`.

**What the PR claims.** "When OHU attempts to start AA, the ESP32 broker enables HFP ... Once the connection information is received and AA connects, the broker disables HFP." The PR also says the brief HFP link does not disturb a call. It was tested on one phone.

**What the code reading found.** These are predictions. The round measures them.

1. **The v1.0.0 firmware does not know the PR's HFP commands.** The app sends `CMD_HFP_ON` (0x08) with every wake and `CMD_HFP_OFF` (0x09) at SSL. The v1.0.0 firmware knows commands 0x01 to 0x07 only (`src/protocol/broker_protocol.h`). Its `on_frame` answers any other type with `EVT_ERROR` code 7 (`BROKER_ERR_FRAME`), and nothing in v1.0.0 disconnects HFP. Its `AA_ENABLE` still dials HFP itself (`hfp_hf_connect_bonded`).
2. **On v1.0.0 the handshake may fail at bring-up, not only at SSL.** `NativeBTBrokerCarrier.onFrame` passes `EVT_ERROR` to `onTransportError`, which closes the handshake stream and clears the pending RFCOMM open. The app sends one `HFP_ON` per wake, and a wake comes from `requestAaWake` at start and from every credential re-delivery (3 or 4 per group). So an error can land while the phone's channel is opening or while WPP runs, and that attempt then ends. At SSL the `HFP_OFF` error also closes our held WPP channel, while the phone's RFCOMM stays open on the board. So we predict on v1.0.0: one serial `ERROR` frame and one app transport error for every `HFP_ON` and `HFP_OFF` sent; some bring-ups that do not reach SSL; and HFP still up after SSL.
6. **`apply_hfp` implements both commands.** `CMD_HFP_ON` dials HFP (`apply_hfp_on`, serial `HFP on (`). `CMD_HFP_OFF` disconnects it (`apply_hfp_off`, serial `HFP off (` then `HFP disconnect `). While HFP is not armed, the board refuses a phone's own HFP connect (`HFP refused`). HFP stays off at boot, and the boot no longer dials the last phone. So on `apply_hfp` we predict no `ERROR` frames, every bring-up to SSL, and HFP state 0 within a few seconds of SSL.
3. **No API guards below 21.** `NativeBTBrokerClient.scan` calls `adapter.bluetoothLeScanner` and builds a `ScanCallback` (API 21). `connectGatt(..., TRANSPORT_LE)` is API 23. The github flavor has minSdk 16. We predict a crash on D-SAM (API 19) at Scan.
4. **The self-wake loop should not apply.** HFP runs on the board, so no `ACL_CONNECTED` to the phone comes from the head unit. Count `MATCH! Starting AapService` anyway.
5. **Unknown: does the phone re-dial during a session?** Gearhead 17.8 and later re-dial the WPP RFCOMM every ~5 s if the channel closes while a profile is up (late-listener-poke round 1 measured 5.2 to 5.8 s on 18.0). v1.0.0 closes the phone's RFCOMM only after 10 s of BLE loss. So we expect no re-dial, but the phone capture decides it.

## 3. What is different about this round

- **Three captures on two clocks.** The head unit and the phone logcats carry their device clocks. The ESP32 serial log carries the host clock, because `serial_cap.py` stamps each line. `mark` writes the same `RIGMARK <label>` line into all three, so every count uses marker windows. Record the clock offsets with `clk` at the start and at the end.
- **Opening the board's serial port can reset it.** On v1.0.0 a reset of a paired board opens a 180 s discoverable window and makes it dial HFP to its last phone for 60 s (`MSG_SPP_READY` in `src/broker/broker_runtime.cpp`). On `apply_hfp` the reset opens the same window but dials no phone. So start the serial capture at each flash and keep it running until the next flash or Closing. A boot line inside a cycle window voids that cycle.
- **The phone keeps its radios on in Stages B and C.** Template §4 takes the phone off the link before each launch. Here the board's HFP dial and the head unit's own HFP link to D-POCO are graded variables, and a phone radio cycle moves both. So each cycle starts with the phone on. The head unit app is still force-stopped and relaunched every cycle. Note this in Setup notes.
- **The broker's prefs file is written by the app, not by us.** If D-HU's `shared_prefs/` is root-owned, the app's write never reaches disk (`rig-quirks/units/D-HU.md`), and the broker vanishes at the next force-stop. Prepare checks it.
- **Forget broker is done in the file, not in the UI.** With the app stopped, deleting `NativeBTBroker.xml` gives a new process the same state that "Forget broker" gives. Check that no legacy file (`AABroker.xml`, `esp32-broker.xml`) exists, because the store migrates one on first read.
- **Injected taps.** Pairing and the D-SAM Scan have no verb. Per `rig-quirks/topics/tooling.md` ("Injected taps, when the operator says so"), this brief allows up to 3 injected taps per run, each on a node found by exact text in a `uiautomator dump`. All taps are listed in section 4. **The operator has not confirmed this yet.** Ask once in Prepare step 2, and write "injected taps: operator to confirm" in Setup notes, then the answer. If the operator says no, the operator makes those taps by hand on cue, and Setup notes say so.
- **D-POCO is bonded to D-HU and will also be bonded to the board.** Gearhead may dial either. Every phone `Creating rfcomm socket for device:` line names the MAC it dialled. Compare it with `BROKER_BT` and `HU_MAC`.
- **Expected FAIL, said up front:** on v1.0.0, B-d (HFP off after SSL) in every B cycle, and B-a in some cycles (predictions 1 and 2). Run S fails by design if prediction 3 is right. B2 has no expected FAIL: its conditions are the shipping question.
- **The pairing survives the second flash.** Prepare-2 writes the same four regions as the first flash (0x1000, 0x8000, 0xe000, 0x10000). The `nvs` partition at 0x9000 holds the Bluetooth bonds and the board's last phone (`Preferences` namespace `broker` in `src/classic/hfp_hf.cpp`). No image covers it, and both branches use the same partition table (`huge_app_32mbit.csv`, identical at `v1.0.0` and `21e0eb48`). So Stage P is not repeated, unless B2's first cycle shows the bond is gone (Prepare-2, step 7).
- **Expected INCONCLUSIVE:** a B-g reading in a cycle where D-HU's own HFP link to D-POCO is not Connected before the launch, even after one adapter cycle.

## 4. Hand steps and injected taps

| Id | Step | When | Why no verb |
|---|---|---|---|
| H1 | Open a terminal with `tail -n0 -F $EV/hand-steps.log` | Prepare | a cue must reach the operator in time |
| H2 | On D-HU, accept a system "Bluetooth pairing request" dialog if one appears | Stage P | a system dialog, not our app |
| H3 | On D-HU, open Settings, then Native BT Broker | Stage P, only if `DEST` is empty | no id to deep-link with |
| H4 | On D-POCO (Bluetooth settings already open): tap "Pair new device", tap `OpenHU-NativeBTBroker`, confirm, or type `0000` if asked | Stage P, on cue | phone pairing is a system UI |
| H5 | Unlock D-MOTO if it has a PIN | Stage M start | adb has no way past a PIN (`rig-quirks/units/D-MOTO.md`) |
| H10 | None for the second flash: esptool resets the board by RTS/DTR. If it cannot connect, hold BOOT (IO0) while esptool retries | Prepare-2, only on a failed connect | a button is hardware |
| H6 | Give a callee number (`NUM`); answer each call on cue and stay on the line until it ends | Stage M | a call needs a person at the other end |
| H7 | As H4, on D-MOTO | Stage M, on cue | as H4 |
| H8 | Plug D-SAM into a free port; if none is free, unplug D-MOTO first | Stage S start | a cable is hardware |
| H9 | Forget `OpenHU-NativeBTBroker` in the Bluetooth settings of D-POCO and D-MOTO, and `OpenHU-NativeBTBroker-Setter` on D-HU | Closing | no adb verb unpairs |

| Run | Injected tap | Exact text of the node |
|---|---|---|
| P | Scan | `Look for OpenHU-NativeBTBroker-Setter` |
| P | the found broker | `OpenHU-NativeBTBroker-Setter` |
| P | Pair phone, only if no `pairing mode for ` serial line within 10 s of the second tap | `Pair phone` |
| M | Pair phone | `Pair phone` |
| S | Scan | `Look for OpenHU-NativeBTBroker-Setter` |

A node that does not resolve to exactly one match voids the step. Never guess a coordinate. All injected taps are "operator to confirm" (section 3).

## 5. Settings keys

D-HU is rooted: write with `$RIG_TOOLKIT/set_hu_settings_host.py $HU $KEYS` with the app stopped, then read back. D-SAM is not: use `$RIG_TOOLKIT/set_prefs_runas_host.py $SAM <keys>`. `log-level` 2 (INFO) carries every app line in section 7: all are `AppLog.i` or `AppLog.w`. `ACTION_LOG_MARKER` is not in `AutomationCommandPolicy.CONFIGURING` on the candidate, and this round marks with `log -t` anyway, so `allow-external-configuration` is not needed. Leave the audio keys as found: they are the rig's deliberate worst case. Leave `wifi-direct-stable-identity` as found and record its value.

Stages C1, B, C2 (`TGT` = `POCO`); Stage M (`TGT` = `MOTO_MAC`):

| Element | Why |
|---|---|
| `<int name="wifi-connection-mode" value="3" />` | Native AA |
| `<int name="native-ap-transport" value="0" />` | WiFi Direct |
| `<int name="log-level" value="2" />` | INFO |
| `<int name="onboarding-version" value="2" />` | no wizard |
| `<int name="native-driver-selection-mode" value="0" />` | no driver selector |
| `<string name="native-preferred-device-mac">TGT</string>` | the target phone |
| `<string name="last-connected-native-mac">TGT</string>` | |
| `<set name="native-poke-bt-macs"><string>TGT</string></set>` | the stock route pokes only the target |
| `<boolean name="native-poke-all-paired" value="false" />` | |
| `<set name="auto-start-bt-macs"><string>TGT</string></set>` | so an `ACL_CONNECTED` from the target can print `MATCH!` (graded in B) |
| `<int name="native-aa-wake-damage-verdict" value="0" />` | no latched verdict |
| `<int name="native-aa-wake-armings-without-session" value="0" />` | |
| `video-profile-starvation-cap` | delete: no 720p cap from earlier fumbles |

The broker itself is the file `shared_prefs/NativeBTBroker.xml`, present in B and M, absent in C1 and C2.

Stage S on D-SAM: `wifi-connection-mode` 3, `onboarding-version` 2, `log-level` 2, `native-driver-selection-mode` 0, `native-poke-all-paired` false, `native-poke-bt-macs` empty.

## 6. Shell setup

Save every block below in `$EV/scripts/`. Fill `ids.env` in Prepare. One adb call at a time per unit; the captures are the only streams beside them (house rule 8). Run each stage under `flock /tmp/ohu-rig.lock`.

**bb_env.sh**

```bash
export PKG=com.andrerinas.headunitrevived
export HU=27870808938846 PH=4f4027e9 MOTO=ZY22GC3BM4 SAM=30041c35642d2200
export RIG=$(dirname "$(find ~ -maxdepth 3 -type d -name rig-toolkit -print -quit)")
export RIG_TOOLKIT=$RIG/rig-toolkit
export EV=$RIG/rig-data/rounds/native-bt-broker-esp32-round1 SCR=$RIG/rig-data/rounds/native-bt-broker-esp32-round1/scripts
export APK=$RIG/rig-data/apks/native-bt-broker-esp32-round1/C.apk VENV=$EV/venv
export E1=/dev/serial/by-path/pci-0000:00:14.0-usb-0:1.4:1.0 SER=$EV/serial-E1.txt
export MAIN=$PKG/com.andrerinas.openheadunit.main.MainActivity SETT=$PKG/com.andrerinas.openheadunit.main.SettingsActivity
export SP=/data/data/$PKG/shared_prefs
[ -f $EV/ids.env ] && . $EV/ids.env     # POCO MOTO_MAC HU_MAC BROKER_LE BROKER_BT DEST NUM APPUID
: ${MARK_PHONES:=$PH} ${CAP_PHONE:=$PH} ${TGT:=$POCO}
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
send() { a=$1; shift; adb -s ${SEND_TO:-$HU} shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; }
tsh() { date +'%m-%d %H:%M:%S.%3N'; }
mark() { adb -s $HU shell log -t RIGMARK RIGMARK "$1"; for p in $MARK_PHONES; do adb -s $p shell log -t RIGMARK RIGMARK "$1"; done
         echo "$(tsh) RIGMARK $1" >> $SER; echo "$(tsh) $1" >> $EV/marks.txt; }
cue() { echo "$(tsh) $*" >> $EV/hand-steps.log; }
# waitline <file> <fixed string> <cap s> [first line to search from]
waitline() { for i in $(seq 1 "$3"); do tail -n +"${4:-1}" "$1" 2>/dev/null | grep -aqF -- "$2" && return 0; sleep 1; done; return 1; }
nlines() { wc -l < "$1" 2>/dev/null || echo 0; }
th_gate() { $RIG_TOOLKIT/rig_thermal.sh wait 75 || $RIG_TOOLKIT/rig_thermal.sh wait 75; }
th_watch() { $RIG_TOOLKIT/rig_thermal.sh watch $EV/thermal-$1.log & echo $! > $EV/thermal-$1.pid; }
th_stop() { kill "$(cat $EV/thermal-$1.pid)" 2>/dev/null; }
cap_start() { adb -s $HU logcat -c && ( stdbuf -oL adb -s $HU logcat -v time > $EV/$1-hu.txt & echo $! > $EV/$1-hu.pid )
              adb -s $CAP_PHONE logcat -c && ( stdbuf -oL adb -s $CAP_PHONE logcat -v time > $EV/$1-ph.txt & echo $! > $EV/$1-ph.pid ); }
cap_stop() { sleep 1; kill "$(cat $EV/$1-hu.pid)" "$(cat $EV/$1-ph.pid)" 2>/dev/null; }
readers() { pgrep -fc "^adb .*logcat"; }
hu_su() { adb -s $HU shell su -c "$1"; }
broker_saved() { hu_su "cat $SP/NativeBTBroker.xml" 2>/dev/null | grep -ao 'name="address">[^<]*' | cut -d'>' -f2; }
broker_forget() { hu_su "rm -f $SP/NativeBTBroker.xml"; }
broker_restore() { adb -s $HU push $EV/NativeBTBroker-saved.xml /data/local/tmp/NativeBTBroker.xml >/dev/null
  hu_su "cp /data/local/tmp/NativeBTBroker.xml $SP/NativeBTBroker.xml && chown $APPUID:$APPUID $SP/NativeBTBroker.xml && chmod 660 $SP/NativeBTBroker.xml"; }
KEYS() { echo "scalar:int:wifi-connection-mode:3 scalar:int:native-ap-transport:0 scalar:int:log-level:2 scalar:int:onboarding-version:2 scalar:int:native-driver-selection-mode:0 scalar:string:native-preferred-device-mac:$TGT scalar:string:last-connected-native-mac:$TGT set:native-poke-bt-macs:$TGT scalar:boolean:native-poke-all-paired:false set:auto-start-bt-macs:$TGT scalar:int:native-aa-wake-damage-verdict:0 scalar:int:native-aa-wake-armings-without-session:0 del:video-profile-starvation-cap"; }
keys_ok() { hu_su "cat $SP/settings.xml" > $EV/$1-settings.xml
  grep -aq 'name="wifi-connection-mode" value="3"' $EV/$1-settings.xml && grep -aq 'name="native-poke-all-paired" value="false"' $EV/$1-settings.xml \
  && ! grep -aq 'name="video-profile-starvation-cap"' $EV/$1-settings.xml && grep -aiqF "$TGT" $EV/$1-settings.xml && echo keys_ok ok; }
# D-HU's own hands-free client link to $TGT: prints CONNECTED or NOT, saves the section it read
hu_hfp() { adb -s $HU shell dumpsys bluetooth_manager > $EV/.bt.txt
  awk '/HeadsetClientService/{f=1} f&&/^ *Profile:/&&!/HeadsetClient/{f=0} f' $EV/.bt.txt > "$1"
  if grep -aiqF "$TGT" "$1" && grep -aiqE 'curState.*Connected|state: *CONNECTED' "$1"; then echo CONNECTED; else echo NOT; fi; }
# the phone's Audio Gateway view: saved for the host to read, no verdict here
ph_hfp() { adb -s $CAP_PHONE shell dumpsys bluetooth_manager | awk '/HeadsetService/{f=1} f&&/^ *Profile:/&&!/HeadsetService/{f=0} f' > "$1"; }
hu_st() { adb -s $HU shell dumpsys bluetooth_manager | grep -a -m1 -E '^ *state:'; }
hu_bt_cycle() { adb -s $HU shell svc bluetooth disable   # D-HU's adapter turns itself back on in ~14 s (rig-quirks/topics/bt.md)
  for i in $(seq 1 10); do sleep 1; hu_st | grep -q ' ON' || break; done
  for i in $(seq 1 30); do sleep 1; hu_st | grep -q ' ON' && return 0; done
  adb -s $HU shell svc bluetooth enable; sleep 5; }
clk() { for d in $HU $PH $MOTO $SAM; do adb -s $d get-state >/dev/null 2>&1 || continue
  echo "$d host=$(date +%s.%3N) dev=$(adb -s $d shell date +%s.%N 2>/dev/null | tr -d '\r')"; done | tee -a $EV/clocks.txt; }
tap() { # tap <unit> <label> <exact text>; up to 5 dumps 2 s apart until exactly one node matches
  for i in 1 2 3 4 5; do adb -s $1 shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb -s $1 pull /sdcard/ui.xml $EV/ui-$2.xml >/dev/null 2>&1
    xy=$(python3 -I $SCR/tap_text.py $EV/ui-$2.xml "$3") && break; sleep 2; done
  [ -n "$xy" ] || { echo "$(tsh) TAP_UNRESOLVED $1 $2" | tee -a $EV/taps.log; return 2; }
  echo "$(tsh) tap $1 $2 $xy" >> $EV/taps.log; adb -s $1 shell input tap $xy; }
```

**tap_text.py**

```python
# Print the centre of the one node whose text is exactly argv[2] in a uiautomator dump; exit 2 unless exactly one.
import re, sys, xml.etree.ElementTree as E
try:
    root = E.parse(sys.argv[1]).getroot()
except Exception as e:
    print(f"tap_text: unreadable dump: {e}"); sys.exit(2)
hits = [n for n in root.iter("node") if n.get("text") == sys.argv[2]]
if len(hits) != 1:
    print(f"tap_text: {len(hits)} matches for {sys.argv[2]!r}", file=sys.stderr); sys.exit(2)
x1, y1, x2, y2 = map(int, re.findall(r"\d+", hits[0].get("bounds")))
print((x1 + x2) // 2, (y1 + y2) // 2)
```

**serial_cap.py**

```python
# Read the ESP32 serial port and stamp each line with the host clock. Opens with DTR and RTS low, so it should not reset the board.
import sys, time, datetime, serial
port, out = sys.argv[1], sys.argv[2]
s = serial.Serial(); s.port = port; s.baudrate = 115200; s.timeout = 1; s.dtr = False; s.rts = False
s.open()
if "--reset" in sys.argv:          # EN low for 100 ms, IO0 high: a normal boot, as esptool's hard reset
    s.rts = True; time.sleep(0.1); s.rts = False
buf = b""
with open(out, "a", buffering=1, errors="replace") as f:
    f.write(datetime.datetime.now().strftime("%m-%d %H:%M:%S.%f")[:-3] + " SERIAL_CAP open " + port + "\n")
    while True:
        b = s.read(256)
        if not b:
            continue
        buf += b
        while b"\n" in buf:
            line, buf = buf.split(b"\n", 1)
            ts = datetime.datetime.now().strftime("%m-%d %H:%M:%S.%f")[:-3]
            f.write(ts + " " + line.decode("ascii", "replace").rstrip("\r") + "\n")
```

**grade_cycle.py**

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
    for i, l in enumerate(L):
        if l.rstrip().endswith("RIGMARK " + lbl): return i
    return None
def win(L, a, b):
    i, j = at(L, a), at(L, b)
    return [] if i is None else L[i:(j if j is not None else len(L))]
def n(W, s): return sum(s in l for l in W)
H, P, S = load(hu), load(ph), load(ser)
go, ssl, end, post = T + "-go", T + "-ssl", T + "-end", T + "-post"
if at(H, go) is None or at(H, post) is None: print(f"{T}\tVOID markers_missing"); sys.exit(0)
Ha, Pa, Sa = win(H, go, post), win(P, go, post), win(S, go, post)
has_ssl = at(H, ssl) is not None
Hh = win(H, ssl, end) if has_ssl else []
Ph = win(P, ssl, end) if has_ssl else []
Se = win(S, go, end)
Hp = win(H, go, ssl) if has_ssl else win(H, go, end)      # bring-up only
Sp = win(S, go, ssl) if has_ssl else Se
t_ssl = "-"
f = next((l for l in Ha if "SSL handshake complete" in l), None)
if f and ts(f) and ts(Ha[0]): t_ssl = f"{(ts(f) - ts(Ha[0])).total_seconds():.1f}"
pu = next((l for l in Se if "rx frame type=07 PROJECTION_UP" in l), None)
rel = "-"
if pu and ts(pu):
    rel = "no"
    for l in Se:
        if "HFP HF state 0" in l and ts(l) and 0 <= (ts(l) - ts(pu)).total_seconds() <= 10: rel = "yes"; break
st = [re.search(r"HFP HF state (\d)", l).group(1) for l in Se if re.search(r"HFP HF state \d", l)]
st_post = [re.search(r"HFP HF state (\d)", l).group(1) for l in Sa if re.search(r"HFP HF state \d", l)]
macs = sorted({m.group(1).upper() for l in Pa for m in [re.search(r"Creating rfcomm socket for device: ([0-9A-Fa-f:]{17})", l)] if m})
void = []
if n(Sa, "Classic name OpenHU-NativeBTBroker, BLE name"): void.append("board_reset")
if n(Ha, "AutomationReceiver: ") == 0: void.append("no_verb_line")
if n(Ha, "WifiLauncher: Initializing WiFi Mode:") == 0: void.append("not_armed")
row = dict(
    ssl=n(Ha, "SSL handshake complete"), t_ssl=t_ssl, thr=n(Hh, "Throughput over"),
    broker_on=n(Ha, "NativeAA: Native BT Broker transport is on"), listening=n(Ha, "ACTIVELY LISTENING on Android Auto UUID"),
    poke=n(Ha, "NativeAA: Attempting active poke to device"), wake=n(Ha, "Broker: intentional AA wake (HFP) to "),
    rf_open=n(Ha, "NativeAA: [Broker] the phone opened Android Auto on the broker."),
    rf_close=n(Ha, "NativeAA: [Broker] the phone closed the Android Auto channel."),
    reopen=n(Ha, "the phone reopened Android Auto while a session is up"),
    terr=n(Ha, "NativeAA: [Broker] transport error ("), terr7=n(Ha, "broker EVT_ERROR code=7"),
    terr_pre=n(Hp, "NativeAA: [Broker] transport error ("), hs_ended=n(Ha, "NativeAA: [Broker] handshake ended: "),
    held=n(Ha, "WiFi session landed. Holding the Bluetooth channel"), held_rel=n(Ha, "releasing the held Bluetooth channel after"),
    proj_fail=n(Ha, "PROJECTION_UP not recorded for 10s") + n(Ha, "Broker: PROJECTION_UP failed for"),
    match=n(Ha, "MATCH! Starting AapService"), btconn=n(Ha, "BT Device connected: "),
    groups=n(Ha, "createGroup SUCCESS"), incoming=n(Ha, "WirelessServer: Incoming connection detected from"),
    pdial=n(Pa, "Creating rfcomm socket for device"), pdial_hold=n(Ph, "Creating rfcomm socket for device"),
    wpp_restart_hold=n(Ph, "Triggering WPP restart"), rf_fail_hold=n(Ph, "Rfcomm socket connection failed"),
    ping_timeout=n(Pa, "has timed out on pings"), crit=n(Pa, "Critical error"), pdial_macs=",".join(macs) or "-",
    s_ble_conn=n(Sa, "head unit BLE connected"), s_aa_enable=n(Sa, "rx frame type=03 AA_ENABLE"),
    s_hfp_on_unknown=n(Sa, "rx frame type=08 UNKNOWN"), s_hfp_off_unknown=n(Sa, "rx frame type=09 UNKNOWN"),
    s_err=n(Sa, "tx frame type=26 ERROR"), s_err_pre=n(Sp, "tx frame type=26 ERROR"),
    s_hfp_on_named=n(Sa, "rx frame type=08 HFP_ON"), s_hfp_off_named=n(Sa, "rx frame type=09 HFP_OFF"),
    s_hfp_on_line=n(Sa, "HFP on ("), s_hfp_off_line=n(Sa, "HFP off ("), s_hfp_disc=n(Sa, "HFP disconnect "),
    s_hfp_refused=n(Sa, "HFP refused"), s_stream_fail=n(Sa, "RFCOMM stream failed, code="), s_rf_open=n(Sa, "phone RFCOMM connected, handle"), s_rf_close=n(Sa, "phone RFCOMM closed"),
    hfp_rel=rel, hfp_states_to_end=">".join(st) or "-", hfp_last_post=(st_post[-1] if st_post else "-"),
    s_audio=n(Sa, "HFP audio state "), hu_hfp_before=hb, hu_hfp_after=ha)
print(T + "\t" + ("VOID " + ",".join(void) + "\t" if void else "") + "\t".join(f"{k}={v}" for k, v in row.items()))
```

**cycle.sh**

```bash
# usage: cycle.sh <RUN> <k>. One Native bring-up from a stopped app, a 120 s hold after SSL, a user exit.
. $SCR/bb_env.sh; RUN=$1; K=$2; T=$RUN-c$K
adb -s $HU shell am force-stop $PKG; sleep 1
$RIG_TOOLKIT/set_hu_settings_host.py $HU $(KEYS) >> $EV/keys.log 2>&1
[ "$(keys_ok $T)" = "keys_ok ok" ] || { echo "$T VOID keys" | tee -a $EV/$RUN.tsv; exit 9; }
B=$(broker_saved); case $RUN in B*|M*) [ -n "$B" ] || { echo "$T VOID no_broker" | tee -a $EV/$RUN.tsv; exit 9; };; C*) [ -z "$B" ] || { echo "$T VOID broker_present" | tee -a $EV/$RUN.tsv; exit 9; };; esac
s=$(hu_hfp $EV/$T-hfp-pre.txt); relink=0
[ "$s" = CONNECTED ] || { hu_bt_cycle; for i in $(seq 1 30); do s=$(hu_hfp $EV/$T-hfp-pre.txt); [ "$s" = CONNECTED ] && break; sleep 1; done; relink=1; }
echo "$T relink=$relink" >> $EV/relink.log
ph_hfp $EV/$T-phfp-before.txt
adb -s $CAP_PHONE shell input keyevent KEYCODE_HOME
cap_start $T
mark $T-go
adb -s $HU shell am start -n $MAIN >/dev/null
if waitline $EV/$T-hu.txt 'SSL handshake complete' 120; then mark $T-ssl; sleep 120; fi   # the hold is the measured window
mark $T-end
send ACTION_DISCONNECT > $EV/$T-disc.txt
sleep 15                                    # settle before the after-reads
a=$(hu_hfp $EV/$T-hfp-after.txt); ph_hfp $EV/$T-phfp-after.txt
mark $T-post
cap_stop $T
L=$(nlines $SER); adb -s $HU shell am force-stop $PKG
case $RUN in C*) ;; *) waitline $SER 'head unit BLE disconnected' 15 $L; sleep 12;; esac   # the board drops the phone link 10 s after BLE loss
python3 -I $SCR/grade_cycle.py $EV/$T-hu.txt $EV/$T-ph.txt $SER $T $s $a | tee -a $EV/$RUN.tsv
```

## 7. The lines that decide the runs

**App lines** were checked with `git grep -F` at `792989746b8f71b03562e44e067ee4dd47594c4f` under `app/src`, and all matched. Each is INFO or WARN. They are in `$EV/<T>-hu.txt`.

| Line (fixed substring) | Source | Meaning |
|---|---|---|
| `AutomationReceiver: ` | `AutomationReceiver.kt` | a verb landed |
| `WifiLauncher: Initializing WiFi Mode:` | `WifiLauncherManager.kt` | the launch armed mode 3 |
| `NativeAA: Native BT Broker transport is on` | `NativeAaHandshakeManager.startOverBrokerModule` | **the broker route was chosen** |
| `ACTIVELY LISTENING on Android Auto UUID` | `NativeAaHandshakeManager.kt` | the stock route opened its listeners |
| `NativeAA: Attempting active poke to device` | `NativeAaHandshakeManager.kt` | a stock wake poke |
| `Broker: intentional AA wake (HFP) to ` | `NativeBTBrokerSession.requestAaWake` | one HFP_ON plus AA_ENABLE sent |
| `NativeAA: [Broker] hands-free link up to ` | `NativeBTBrokerCarrier.onFrame` | the board reports HFP up to the phone |
| `NativeAA: [Broker] the phone opened Android Auto on the broker.` | `NativeBTBrokerCarrier.onFrame` | the phone opened RFCOMM on the board |
| `NativeAA: [Broker] the phone closed the Android Auto channel.` | same | the phone closed it |
| `the phone reopened Android Auto while a session is up` | `NativeBTBrokerCarrier.run` | a re-dial during a session, seen from our side |
| `NativeAA: [Broker] transport error (` | `NativeBTBrokerCarrier.onTransportError` | the stream was ended |
| `broker EVT_ERROR code=7` | same (`code=$code`) | the board refused an unknown command |
| `Broker: projection socket ` | `NativeBTBrokerSession.notifyProjection` | `up` or `down` follows |
| `Broker: releasing HFP (` | `NativeBTBrokerSession.releaseHfp` | HFP_OFF on a stop or a cancel |
| `PROJECTION_UP not recorded for 10s` | `AapService.kt` | the PR ended a session on its heartbeat |
| `Broker: PROJECTION_UP failed for` | `NativeBTBrokerSession.kt` | same, from the session object |
| `Broker: link lost, retrying for ` | `NativeBTBrokerSession.noteLinkLost` | BLE dropped |
| `Broker: opening new-phone pairing window on` | `NativeBTBrokerSession.openPairing` | Stage P |
| `SSL handshake complete` | `AapSslContext.kt` | the AAP session is up (never prefix `Handshake:`) |
| `Throughput over` | `VideoDecoder.kt` | video renders, one line per 5 s |
| `WiFi session landed. Holding the Bluetooth channel` | `NativeAaHandshakeManager.kt` | the held WPP channel started |
| `releasing the held Bluetooth channel after` | same | it ended (report the pings it gives) |
| `WirelessServer: Incoming connection detected from` | `WirelessServer.kt` | the phone reached port 5288 |
| `createGroup SUCCESS` | `WifiDirectManager.kt` | a P2P group |
| `MATCH! Starting AapService` | `AutoStartReceiver.kt` | a Bluetooth auto-start match |
| `BT Device connected: ` | `AutoStartReceiver.kt` | every ACL the receiver saw, with name and MAC |

`FATAL EXCEPTION` (Stage S) is the system's `AndroidRuntime` line, not app source.

**Firmware lines**, from the v1.0.0 source (`src/` at tag `v1.0.0`), each checked with `grep -F`. They are in `$SER`, stamped with the host clock.

| Line | Source | Meaning |
|---|---|---|
| `Classic name OpenHU-NativeBTBroker, BLE name` | `src/main.cpp` | a boot (inside a cycle window it voids the cycle) |
| `broker self-check failed` | `src/broker/broker_runtime.cpp` | the codec self-check failed (flash FAIL) |
| `advertising OpenHU-NativeBTBroker-Setter` | `src/ble/hu_ble.cpp` | BLE is up |
| `SPP server on RFCOMM channel` | `src/classic/rfcomm_bridge.cpp` | the Android Auto RFCOMM server is up |
| `classic discoverable=` | `src/classic/rfcomm_bridge.cpp` | `1` opens, `0` closes the inquiry window |
| `head unit BLE connected`, `head unit BLE disconnected` | `src/ble/hu_ble.cpp` | the head unit's GATT link |
| `BLE bond ` | `src/ble/hu_ble.cpp` | `ok` or `failed` |
| `rx frame type=` | `src/broker/broker_runtime.cpp` (`on_frame`) | a command from the head unit; printed as `rx frame type=%02X <NAME>` |
| `tx frame type=` | `src/broker/broker_runtime.cpp` (`emit`) | an event to the head unit; printed as `tx frame type=%02X <NAME>` |
| `HFP HF state ` | `src/classic/hfp_hf.cpp` | the board's HFP link: 0 off, 1 connecting, 2 connected, 3 service level, 4 disconnecting |
| `HFP audio state ` | `src/classic/hfp_hf.cpp` | SCO audio on the board: 2 or 3 means a call's audio went to the board |
| `phone RFCOMM connected, handle`, `phone RFCOMM closed` | `src/classic/rfcomm_bridge.cpp` | the phone's Android Auto channel on the board |
| `cleared phone bonds for new pairing`, `pairing mode for `, `pairing mode ended`, `phone paired: ` | `src/broker/broker_runtime.cpp`, `src/classic/rfcomm_bridge.cpp` | Stage P |
| `head unit BLE down for 10s; closing the phone link` | `src/broker/broker_runtime.cpp` | the board closed the phone's channel |
| `reconnecting last phone ` | `src/classic/hfp_hf.cpp` | the boot-time HFP dial |

The frame names come from `k_labels` in `broker_runtime.cpp`. The greps use these printed forms: `rx frame type=03 AA_ENABLE`, `rx frame type=07 PROJECTION_UP`, `rx frame type=08 UNKNOWN` (the app's HFP_ON), `rx frame type=09 UNKNOWN` (the app's HFP_OFF), `tx frame type=26 ERROR`. Types 0x08 and 0x09 are not in `k_labels`, so they print `UNKNOWN`.

**`apply_hfp` firmware lines**, from `src/` at `21e0eb48f1ba8afb3f2f37795b2f8fa9c80488ec` (fetched with `?ref=`), each checked with `grep -F`. They are in `$SER` after the `FIRMWARE apply_hfp` line. The boot, BLE, frame, `HFP HF state`, `HFP audio state` and RFCOMM lines keep the `v1.0.0` text. `phone RFCOMM connected, handle` now ends in ` gen <n>`, so grep only the prefix.

| Line | Source | Meaning |
|---|---|---|
| `HFP on (` | `src/broker/broker_runtime.cpp` (`apply_hfp_on`) | the board dials HFP; the reason follows, for example `CMD_HFP_ON` |
| `HFP off (` | `src/broker/broker_runtime.cpp` (`apply_hfp_off`) | **the board drops HFP**; printed only when HFP was up |
| `HFP disconnect ` | `src/classic/hfp_hf.cpp` | the disconnect went to the phone's address |
| `HFP refused` | `src/broker/broker_runtime.cpp` (`MSG_HFP`) | the phone connected HFP while it was not armed; the board dropped it |
| `RFCOMM stream failed, code=` | `src/broker/broker_runtime.cpp` (`fail_rfcomm_stream`) | the board closed the phone's channel after a relay loss |
| `pairing failed: ` | `src/classic/rfcomm_bridge.cpp` | a bond attempt failed (Prepare-2 step 7) |
| `reconnecting last phone ` | `src/classic/hfp_hf.cpp` | must not appear at boot on this branch |

`k_labels` on this branch names 0x08 `HFP_ON` and 0x09 `HFP_OFF`, so the printed forms are `rx frame type=08 HFP_ON` and `rx frame type=09 HFP_OFF`.

**Phone lines** (Gearhead), in `$EV/<T>-ph.txt`. Checked in the Gearhead 18.0.664204 decompile; record the phone's version, because strings drift (`rig-quirks/topics/gearhead.md`).

| Line | Source | Meaning |
|---|---|---|
| `Creating rfcomm socket for device` | `oyc.java`, tag `GH.WIRELESS.BT` | **one RFCOMM dial**; the MAC follows |
| `Triggering WPP restart` | `pva.java` | WPP restarted |
| `Rfcomm socket connection failed` | `pva.java` | a dial that found no listener |
| `has timed out on pings` | `ptw.java` | ten unanswered pings |
| `Critical error` | `SetupFsm.java` | Android Auto ended a session and why |
| `RIGMARK` | | the markers |

## 8. Runs

**Preflight in every run.** `readers` prints 0 before the first `cap_start`. `th_gate` passes, then `th_watch <run>`; `th_stop <run>` at the end. The serial capture is alive (`kill -0 $(cat $EV/ser.pid)`). Every `send` prints `AutomationReceiver: <action>` in the unit capture, or the cycle is void.

### Prepare

1. `prep.sh`, then `build.sh`, then `dest.sh` (section 1). Save the section 6 blocks. Write `ids.env` as you learn each value below, one `NAME=value` per line.
2. One batched message to the operator: "1) Open the cue terminal: `tail -n0 -F <EV>/hand-steps.log`. 2) Keep D-POCO unlocked and near D-HU. 3) Stage P needs you for about 3 minutes, and Stage M needs a callee number and you to answer two calls. Say now if Stage M cannot run today."
3. **D-POCO:** `adb -s $PH reboot`, then `adb -s $PH wait-for-device` and poll `getprop sys.boot_completed` for 1 (cap 180 s). A wedged Gearhead RFCOMM throttle survives a force-stop, and only a reboot cleared it on D-POCO. If the reboot is refused, ask the operator. Then `svc wifi enable`, `svc bluetooth enable`, read both back with `dumpsys` (template §3). Record `dumpsys package com.google.android.projection.gearhead | grep -a -m1 versionName`. Record `getprop persist.bluetooth.maxconnectedaudiodevices` and `getprop bluetooth.max_connected_audio_devices`.
4. **D-MOTO off the link:** `adb -s $MOTO shell svc bluetooth disable` and `svc wifi disable`; read back `state: OFF` and `Wi-Fi is disabled`.
5. **D-HU:** `adb -s $HU shell am force-stop $PKG`. Back up `settings.xml` (`hu_su "cat $SP/settings.xml" > $EV/settings-backup-hu.xml`) and the installed APK (`adb pull` of `pm path`, to `$APKS/prior-hu.apk`, with md5). Diff `settings.xml` against the last round's backup and state the delta. `hu_su 'stat -c "%U:%G %a" '$SP` and `hu_su "stat -c %U /data/data/$PKG"`: record both. If the first owner is not the app's user, run `hu_su "chown -R \$(stat -c %u /data/data/$PKG):\$(stat -c %g /data/data/$PKG) $SP"` and re-stat. Write `APPUID=<the app's user name>` to `ids.env`. `hu_su "ls $SP"` must show no `NativeBTBroker.xml`, `AABroker.xml` or `esp32-broker.xml`; delete any it shows and say so.
6. **Install:** `adb -s $HU install -r -d $APK`. Pull it back and check the md5 equals `C.apk`. `send ACTION_QUERY_STATE`: `commit` must start with `79298974`. Diff `settings.xml` against the backup again. Grant the scan permissions, so no runtime dialog appears in Stage P: `adb -s $HU shell pm grant $PKG android.permission.BLUETOOTH_SCAN`, then the same for `BLUETOOTH_CONNECT` and `ACCESS_FINE_LOCATION`. `dumpsys package $PKG | grep -aE 'BLUETOOTH_SCAN|BLUETOOTH_CONNECT|ACCESS_FINE_LOCATION'` must show `granted=true` for each.
7. **MACs:** `adb -s $HU shell dumpsys bluetooth_manager | grep -a -A12 'Bonded devices'` gives `POCO` (D-POCO) and `MOTO_MAC` (D-MOTO). `adb -s $PH shell dumpsys bluetooth_manager | grep -a -A12 'Bonded devices'` gives `HU_MAC` (D-HU's radio). Write all three in upper case. Record `wifi-direct-stable-identity` and the current `auto-start-bt-macs` from the backup.
8. **Flash E-1.** Never open E-2's port (`...usb-0:1.3:1.0`).

**flash.sh**

```bash
. $SCR/bb_env.sh
readlink -f $E1                                  # record the tty
fuser -v $(readlink -f $E1) 2>&1 | tee $EV/fuser-before.txt   # MUST name no process; else stop that reader first
$VENV/bin/esptool --port $E1 read-mac 2>&1 | tee $EV/e1-mac.txt
grep -aiq '78:1c:3c:2b:ac:ec' $EV/e1-mac.txt || { echo "STOP: not board E-1"; exit 3; }
( cd $EV/fw && $VENV/bin/esptool --chip esp32 --port $E1 --baud 460800 write-flash -z \
  0x1000 bootloader.bin 0x8000 partitions.bin 0xe000 boot_app0.bin 0x10000 firmware.bin ) 2>&1 | tee $EV/flash.txt
grep -ac 'Hash of data verified' $EV/flash.txt   # MUST print 4
stdbuf -oL $VENV/bin/python -I -u $SCR/serial_cap.py $E1 $SER --reset > $EV/serial_cap.err 2>&1 & echo $! > $EV/ser.pid
waitline $SER 'advertising OpenHU-NativeBTBroker-Setter' 20
grep -ac 'Classic name OpenHU-NativeBTBroker, BLE name' $SER   # MUST print 1 or 2 (the port open may reset it too); record
grep -ac 'broker self-check failed' $SER                       # MUST print 0
grep -ac 'SPP server on RFCOMM channel' $SER                   # MUST print 1 or more
grep -a 'classic discoverable=' $SER | tail -1                 # record (1 = the 180 s boot window)
```

If `Hash of data verified` prints fewer than 4 times, flash once more. If it fails again, stop and escalate. `--reset` gives the capture the whole boot log. The reset also opens the 180 s boot inquiry window, which is harmless: no phone is bonded to the board yet.

9. `clk`. Record the three offsets (unit, phone, and the host, which stamps `$SER`).
10. **D-HU's own HFP link to D-POCO:** `TGT=$POCO hu_hfp $EV/prep-hfp.txt`. If it prints `NOT`, run `hu_bt_cycle` once and read it again for up to 30 s. Record the result. Each cycle repeats this check (`cycle.sh`).

### Stage C1. Control: the stock route, no broker saved (2 cycles)

**stageC1.sh**

```bash
. $SCR/bb_env.sh; th_gate || exit 3; th_watch C1
for k in 1 2; do bash $SCR/cycle.sh C1 $k; done
th_stop C1; readers     # MUST print 0
```

- **PASS per cycle:** `ssl` 1 or more, `t_ssl` 120 or less, `thr` 18 or more, `listening` 1 or more, `broker_on` 0, `s_ble_conn` 0, and `pdial_macs` contains `HU_MAC`.
- **Run:** PASS when both cycles pass. Report `match`, `poke` and `groups`; they are not graded here (the stock poke can raise its own `ACL_CONNECTED`).
- **Stop rule:** if 0 of 2 cycles reach `ssl`, reboot D-POCO once (Prepare step 3) and run C1 once more. If it reaches 0 of 2 again, the rig is not healthy: mark B, C2 and M UNTESTABLE (rig) and go to Stage S.

### Stage P. Pair the board (hand steps H2 to H4, up to 3 injected taps)

**pair.sh**

```bash
. $SCR/bb_env.sh; adb -s $HU shell am force-stop $PKG
cap_start PAIR; mark PAIR-go
if [ -n "$DEST" ]; then hu_su "am start -n $SETT --ei extra_destination $DEST"; else cue "H3: on D-HU open Settings, then Native BT Broker"; fi
tap $HU p-scan 'Look for OpenHU-NativeBTBroker-Setter' || { mark PAIR-end; cap_stop PAIR; exit 4; }
L=$(nlines $SER); tap $HU p-pick 'OpenHU-NativeBTBroker-Setter' || { mark PAIR-end; cap_stop PAIR; exit 5; }
waitline $SER 'pairing mode for ' 10 $L || tap $HU p-pair 'Pair phone'
cue "H2: accept a Bluetooth pairing dialog on D-HU if one is shown"
adb -s $PH shell am start -a android.settings.BLUETOOTH_SETTINGS >/dev/null
cue "H4 NOW: on D-POCO tap Pair new device, then OpenHU-NativeBTBroker, confirm (PIN 0000 if asked)"
waitline $SER 'phone paired: ' 150 $L; echo "paired=$?" | tee -a $EV/pair.log
waitline $SER 'BLE bond ok' 5 $L; echo "ble_bond=$?" | tee -a $EV/pair.log
adb -s $HU shell input keyevent 4; sleep 2; mark PAIR-end; cap_stop PAIR
adb -s $HU shell am force-stop $PKG
broker_saved | tee -a $EV/pair.log          # MUST print a MAC: that is BROKER_LE
hu_su "cat $SP/NativeBTBroker.xml" > $EV/NativeBTBroker-saved.xml
adb -s $PH shell dumpsys bluetooth_manager | grep -a -A14 'Bonded devices' | tee $EV/poco-bonded-after-pair.txt
```

- Write `BROKER_LE` (from `broker_saved`) and `BROKER_BT` (the `OpenHU-NativeBTBroker` line of the phone's bonded list) to `ids.env`.
- **If `p-pick` does not resolve** after the scan (exit 5), the PR's scan did not find the board. Record it as a finding with `$EV/ui-p-pick.xml`. Run `pair.sh` once more. If it fails again, seed the file by hand: write `NativeBTBroker-saved.xml` with `<string name="address">78:1C:3C:2B:AC:EE</string>` (E-1's Bluetooth MAC is its base MAC plus 2, the ESP-IDF rule) and `<string name="name">OpenHU-NativeBTBroker-Setter</string>`, run `broker_restore`, reopen the screen, and tap `Pair phone`. Say so in Setup notes.
- **PASS:** `paired=0`, `broker_saved` prints a MAC, the phone's bonded list names `OpenHU-NativeBTBroker`, and the PAIR capture has `Broker: opening new-phone pairing window on` 1 or more. Report `BLE bond ok` and the serial lines from `rx frame type=06 SET_BOND_MODE` to `pairing mode ended`.
- **Stop rule:** two failed pairings make B, C2 and M UNTESTABLE (pairing). Go to Stage S.

### Stage B. The broker route on v1.0.0, 5 bring-ups. It records the PR and firmware mismatch

**stageB.sh**

```bash
# usage: stageB.sh B|B2. Five broker bring-ups; the same script for both firmware arms.
. $SCR/bb_env.sh; RUN=$1; th_gate || exit 3; th_watch $RUN
grep -a 'SERIAL_CAP open' $SER | tail -1; kill -0 "$(cat $EV/ser.pid)" || { echo "STOP: serial capture dead"; exit 3; }
miss=0
for k in 1 2 3 4 5; do
  bash $SCR/cycle.sh $RUN $k
  tail -1 $EV/$RUN.tsv | grep -q 'ssl=0' && miss=$((miss+1)) || miss=0
  [ $miss -ge 3 ] && { echo "STOP $RUN: 3 cycles in a row without SSL"; break; }
done
th_stop $RUN; readers      # MUST print 0
```

Run it as `bash $SCR/stageB.sh B` on `v1.0.0`.

A cycle with `VOID` in its row is run again once as the next `k` (at most 2 voids in the run). Grade each cycle from its row:

| Id | Condition | Expected |
|---|---|---|
| B-a | `ssl` 1 or more and `t_ssl` 120 or less | **may FAIL** (section 2, finding 2) |
| B-b | `thr` 18 or more (the hold is 120 s, so 24 at full rate) | |
| B-c | `broker_on` 1 or more, `listening` 0, `poke` 0, `rf_open` 1 or more, and `pdial_macs` contains `BROKER_BT` | the route is the board's |
| B-d | **`hfp_rel` = `yes`**: `HFP HF state 0` on serial within 10 s of the first `rx frame type=07 PROJECTION_UP` | **FAIL predicted** (section 2, finding 1) |
| B-e | `match` 0 | |
| B-f | **phone, graded:** `pdial_hold` 2 or less and `wpp_restart_hold` 2 or less. FAIL when `pdial_hold` is 10 or more | |
| B-g | `hu_hfp_after` equals `hu_hfp_before`, read only when `hu_hfp_before` = `CONNECTED` | report `n/a` otherwise |
| B-h | `proj_fail` 0 | |

| B-i | **the error count, report per cycle:** `s_hfp_on_unknown`, `s_hfp_off_unknown`, `s_err` and `s_err_pre` on serial; `terr7`, `terr`, `terr_pre` and `hs_ended` on the unit | prediction: `s_err` equals `s_hfp_on_unknown` + `s_hfp_off_unknown`, and `terr7` equals `s_err` |

- **Run verdict.** PASS when B-a to B-c and B-e to B-h hold in 4 or more of the cycles run, and B-d holds in 4 or more. FAIL when B-a fails in 3 or more cycles, or B-d, B-f or B-g fails in 2 or more. INCONCLUSIVE otherwise. A FAIL on B-d, with or without B-a, is the predicted result: say so in the verdict line.
- **Did the error end a bring-up?** For each cycle with `ssl=0`, quote every `NativeAA: [Broker] transport error (` line and the unit lines from 3 s before it to 3 s after it. Say whether a `the phone opened Android Auto on the broker.` line came within those 3 s before it. That pairing is the evidence for finding 2.
- **What a PASS would look like if the board did nothing:** B-c separates it. A session that formed over D-HU's own radio would show `listening` 1 or more and a `pdial_macs` of `HU_MAC` only.
- **Report per cycle:** the whole TSV row, and `relink.log`. For each cycle, quote the first `tx frame type=26 ERROR` after `rx frame type=07 PROJECTION_UP` on serial, the matching `broker EVT_ERROR code=7` unit line, and the last `HFP HF state` before `-end`. Quote the `releasing the held Bluetooth channel after` line with its ping count. Read the two `phfp` files of cycle 1 and cycle 5 (the phone's Audio Gateway view) and say which devices it lists as connected before and after. This is a short read by the host, not an extract.

### Prepare-2. Build `apply_hfp` and flash it to E-1

1. **Fetch the source at the exact commit.** Keep it under the round's folder, not in `rig-toolkit/` or `worktrees/`.

**fw2.sh**

```bash
. $SCR/bb_env.sh; FW2=$EV/fw-apply_hfp; mkdir -p $FW2
git clone -q https://github.com/kendrickkim/aa-native-bt-broker-for-openheadunit.git $FW2/src
git -C $FW2/src checkout -q --detach 21e0eb48f1ba8afb3f2f37795b2f8fa9c80488ec
git -C $FW2/src rev-parse HEAD                     # MUST print 21e0eb48f1ba8afb3f2f37795b2f8fa9c80488ec
grep -E '^(nvs|otadata|app0|spiffs)' $FW2/src/huge_app_32mbit.csv   # MUST read nvs 0x9000, otadata 0xe000, app0 0x10000 size 0x300000, spiffs 0x310000
diff <(sed -n '/^\[env/,$p' $FW2/src/platformio.ini) - <<'EOF' && echo INI_SAME
[env:esp32dev]
platform = espressif32
board = esp32dev
framework = arduino
monitor_port = COM17
monitor_speed = 115200
upload_port = COM17
board_build.mcu = esp32
board_build.partitions = huge_app_32mbit.csv
EOF
python3 -m venv $FW2/pio-venv && $FW2/pio-venv/bin/pip install -q platformio
export PLATFORMIO_CORE_DIR=$FW2/pio-core
$RIG_TOOLKIT/rig_thermal.sh wait 70
( cd $FW2/src && $FW2/pio-venv/bin/pio run -e esp32dev -j 2 ) 2>&1 | tee $FW2/build.txt | tail -5   # MUST end in SUCCESS
$FW2/pio-venv/bin/pio pkg list -d $FW2/src 2>&1 | tee $FW2/pkgs.txt | head -20   # record: platform espressif32 is not pinned
mkdir -p $FW2/bins; B=$FW2/src/.pio/build/esp32dev
cp $B/bootloader.bin $B/partitions.bin $B/firmware.bin $FW2/bins/
cp "$(find $PLATFORMIO_CORE_DIR/packages -path '*framework-arduinoespressif32*' -name boot_app0.bin | head -1)" $FW2/bins/
( cd $FW2/bins && sha256sum bootloader.bin partitions.bin boot_app0.bin firmware.bin | tee SHA256SUMS )   # record all four
cmp $FW2/bins/boot_app0.bin $EV/fw/boot_app0.bin && echo BOOT_APP0_SAME
cmp $FW2/bins/partitions.bin $EV/fw/partitions.bin && echo PARTITIONS_SAME
strings $FW2/bins/firmware.bin | grep -c -F 'HFP off ('       # MUST print 1 or more: this is the apply_hfp build
```

`-j 2` is the worker limit for this build (`rig-quirks/topics/tooling.md`, thermal table). The offsets are the same as `v1.0.0`, because the partition CSV is the same file at both commits. Report `BOOT_APP0_SAME` and `PARTITIONS_SAME`, or the difference. A different `bootloader.bin` is expected: `platform = espressif32` is not pinned, so PlatformIO takes its current release. **Stop and escalate** if the commit, the CSV, the build or the `strings` check fails.

2. Stop the serial capture: `kill $(cat $EV/ser.pid)`; then `fuser -v $(readlink -f $E1)` must name no process.
3. `$VENV/bin/esptool --port $E1 read-mac` must still name `78:1c:3c:2b:ac:ec`.
4. Flash the four files at the same offsets:

**flash2.sh**

```bash
. $SCR/bb_env.sh; FW2=$EV/fw-apply_hfp
( cd $FW2/bins && $VENV/bin/esptool --chip esp32 --port $E1 --baud 460800 write-flash -z \
  0x1000 bootloader.bin 0x8000 partitions.bin 0xe000 boot_app0.bin 0x10000 firmware.bin ) 2>&1 | tee $EV/flash2.txt
grep -ac 'Hash of data verified' $EV/flash2.txt      # MUST print 4
echo "$(tsh) FIRMWARE apply_hfp 21e0eb48" >> $SER
stdbuf -oL $VENV/bin/python -I -u $SCR/serial_cap.py $E1 $SER --reset > $EV/serial_cap2.err 2>&1 & echo $! > $EV/ser.pid
L=$(grep -an 'FIRMWARE apply_hfp' $SER | tail -1 | cut -d: -f1)
waitline $SER 'advertising OpenHU-NativeBTBroker-Setter' 20 $L
tail -n +$L $SER | grep -ac 'broker self-check failed'    # MUST print 0
tail -n +$L $SER | grep -ac 'reconnecting last phone '   # MUST print 0: apply_hfp does not dial at boot
```

5. If `Hash of data verified` prints fewer than 4 times, flash once more. If it fails again, stop and escalate.
6. Do not repeat Stage P. The bonds live in `nvs` at 0x9000, which the flash does not write (section 3).
7. **Bond check, in B2's first cycle.** If B2-c1 has `s_hfp_on_line` 1 or more but no `HFP HF state 3`, and the serial window shows `pairing failed: ` or the phone's `Creating rfcomm socket for device` never names `BROKER_BT`, the bond did not survive. Then mark B2-c1 VOID, run `pair.sh` once (Stage P, the same taps), say so in Setup notes, and start B2 again at `k`=1.

### Stage B2. The broker route on `apply_hfp`, 5 bring-ups. B2 IS THE POINT OF THE ROUND

Run `bash $SCR/stageB.sh B2`. The stop rule, the void rule and the cycle are the same as B. Grade each cycle from its row:

| Id | Condition | Expected |
|---|---|---|
| B2-a | `ssl` 1 or more and `t_ssl` 120 or less | |
| B2-b | `thr` 18 or more | |
| B2-c | `broker_on` 1 or more, `listening` 0, `poke` 0, `rf_open` 1 or more, and `pdial_macs` contains `BROKER_BT` | |
| B2-d | **HFP drops after SSL:** `hfp_rel` = `yes`, and `s_hfp_off_named` 1 or more, `s_hfp_off_line` 1 or more and `s_hfp_disc` 1 or more | a real PASS condition |
| B2-e | `match` 0 | |
| B2-f | **phone, graded:** `pdial_hold` 2 or less and `wpp_restart_hold` 2 or less. FAIL when `pdial_hold` is 10 or more | |
| B2-g | `hu_hfp_after` equals `hu_hfp_before`, read only when `hu_hfp_before` = `CONNECTED` | report `n/a` otherwise |
| B2-h | `proj_fail` 0 | |
| B2-i | `s_err` 0 and `terr7` 0 | the board knows both commands |

- **Run verdict.** PASS when B2-a to B2-i hold in 4 or more of the cycles run. FAIL when B2-a or B2-d fails in 2 or more cycles, or B2-f or B2-g fails in 2 or more. INCONCLUSIVE otherwise.
- **What a PASS would look like if `HFP_OFF` did nothing:** B2-d needs both the board's own `HFP off (` line and state 0 within 10 s of `PROJECTION_UP`. An HFP drop for any other reason would show state 0 without the `HFP off (` line.
- **Report per cycle:** the whole TSV row, `relink.log`, and the seconds from the first `rx frame type=07 PROJECTION_UP` to `HFP HF state 0` on serial. Report `s_hfp_refused` per cycle: a phone that dials HFP back after the drop shows here. Quote the `releasing the held Bluetooth channel after` line. Read the `phfp` files of cycle 1 and cycle 5, as in B.

### Stage C2. Forget the broker, then the stock route again (2 cycles)

**stageC2.sh**

```bash
. $SCR/bb_env.sh; adb -s $HU shell am force-stop $PKG; broker_forget
[ -z "$(broker_saved)" ] || { echo "STOP: broker file still present"; exit 3; }
th_gate || exit 3; th_watch C2
for k in 1 2; do bash $SCR/cycle.sh C2 $k; done
th_stop C2; readers     # MUST print 0
```

- **PASS per cycle:** as C1. In addition, `s_ble_conn` 0: the forgotten board gets no GATT link.
- **Run:** PASS when both cycles pass. If C1 passed and C2 fails, the forget does not return the unit to the stock route. Then quote `pdial_macs`, the last `HFP HF state` on serial and any `HFP refused` lines: on `apply_hfp` the forgotten board should hold no HFP, so a phone that still dials the board is the finding.

### Stage M. Calls on D-MOTO, on `apply_hfp` only (optional)

Run only if the operator gave a callee number in Prepare step 2. Otherwise mark M-1 and M-2 UNTESTABLE (operator). Run it only with `apply_hfp` on E-1 (check the last `FIRMWARE apply_hfp` line in `$SER`): `v1.0.0` cannot drop HFP, so a call test on it would only repeat B-d. If Prepare-2 failed, mark M UNTESTABLE (firmware).

1. **Setup.** `adb -s $PH shell svc bluetooth disable` and read back `state: OFF`. `adb -s $MOTO shell svc bluetooth enable` and `svc wifi enable`; read both back. Cue H5. Record D-MOTO's Gearhead `versionName`. Write `NUM=<number>` to `ids.env`.
2. **Pair D-MOTO.** This clears D-POCO's bond on the board (`SET_BOND_MODE` 2).

**pairM.sh**

```bash
. $SCR/bb_env.sh; export CAP_PHONE=$MOTO MARK_PHONES=$MOTO
adb -s $HU shell am force-stop $PKG; broker_restore; [ -n "$(broker_saved)" ] || exit 3
cap_start PAIRM; mark PAIRM-go; L=$(nlines $SER)
if [ -n "$DEST" ]; then hu_su "am start -n $SETT --ei extra_destination $DEST"; else cue "H3: on D-HU open Settings, then Native BT Broker"; fi
tap $HU m-pair 'Pair phone' || { mark PAIRM-end; cap_stop PAIRM; exit 4; }
adb -s $MOTO shell am start -a android.settings.BLUETOOTH_SETTINGS >/dev/null
cue "H7 NOW: on D-MOTO tap Pair new device, then OpenHU-NativeBTBroker, confirm (PIN 0000 if asked)"
waitline $SER 'phone paired: ' 150 $L; echo "paired_moto=$?" | tee -a $EV/pair.log
adb -s $HU shell input keyevent 4; sleep 2; mark PAIRM-end; cap_stop PAIRM; adb -s $HU shell am force-stop $PKG
```

3. **The two calls.** `callrun.sh hfp` rings while the board holds HFP and before SSL. `callrun.sh proj` rings 30 s into a projection.

**callrun.sh**

```bash
# usage: callrun.sh hfp|proj
. $SCR/bb_env.sh; export CAP_PHONE=$MOTO MARK_PHONES=$MOTO TGT=$MOTO_MAC; W=$1; T=M-$W
adb -s $HU shell am force-stop $PKG; $RIG_TOOLKIT/set_hu_settings_host.py $HU $(KEYS) >> $EV/keys.log 2>&1
[ "$(keys_ok $T)" = "keys_ok ok" ] || exit 9
cap_start $T; mark $T-go; adb -s $HU shell am start -n $MAIN >/dev/null
if [ $W = hfp ]; then waitline $EV/$T-hu.txt 'NativeAA: [Broker] hands-free link up to ' 90 || mark $T-nohfp
else waitline $EV/$T-hu.txt 'SSL handshake complete' 120 && mark $T-ssl; sleep 30; fi
mark $T-call; adb -s $MOTO shell am start -a android.intent.action.CALL -d tel:$NUM >/dev/null
cue "H6 NOW: answer the call on $NUM and stay on the line"
sleep 25                                                # the call is the measured window
adb -s $MOTO shell dumpsys telecom > $EV/$T-telecom.txt
mark $T-callend; adb -s $MOTO shell input keyevent KEYCODE_ENDCALL
if [ $W = hfp ]; then waitline $EV/$T-hu.txt 'SSL handshake complete' 120 && mark $T-ssl; fi
sleep 30; mark $T-end; send ACTION_DISCONNECT > $EV/$T-disc.txt; sleep 5; mark $T-post
cap_stop $T; adb -s $HU shell am force-stop $PKG
grep -a -oE 'route: [A-Z_]+' $EV/$T-telecom.txt | sort | uniq -c        # report all of them
awk '/RIGMARK '$T'-call$/{f=1} /RIGMARK '$T'-callend$/{f=0} f' $SER | grep -ac -E 'HFP audio state [23]'
awk '/RIGMARK '$T'-callend$/{f=1} /RIGMARK '$T'-end$/{f=0} f' $EV/$T-hu.txt | grep -ac 'Throughput over'
grep -ac 'SSL handshake complete' $EV/$T-hu.txt; grep -ac 'Critical error' $EV/$T-ph.txt
```

- **M-1 (`hfp`). PASS** when the call's audio stays on the phone: 0 `HFP audio state 2` or `3` lines on serial between `-call` and `-callend`, and no `route: BLUETOOTH` in the telecom dump. **And** the session still forms: `SSL handshake complete` 1 or more after `-callend`, within 120 s. If `-nohfp` was marked, M-1 is INCONCLUSIVE (the window never opened).
- **M-2 (`proj`). PASS** when the call's audio stays on the phone (the same two checks) **and** the session survives: exactly 1 `SSL handshake complete` in the capture, `Throughput over` 4 or more between `-callend` and `-end`, and `Critical error` 0 on the phone.
- If the dump carries no `route:` text, save it and say so. The host reads the call section by hand.

### Stage S. D-SAM (API 19): Scan without the API 21 guard

No ESP32 and no phone. Cue H8 first. D-SAM rejects `-p` on `am` (`rig-quirks/units/D-SAM.md`); the commands here use `-n` only.

**dsam.sh**

```bash
. $SCR/bb_env.sh; export SEND_TO=$SAM
adb -s $SAM shell pm list features | grep -a bluetooth | tee $EV/S-features.txt     # record whether bluetooth_le is listed
adb -s $SAM shell date; date                                                      # record both
adb -s $SAM shell dumpsys battery | grep -a -m1 level                             # record
adb -s $SAM shell am force-stop $PKG
adb -s $SAM shell run-as $PKG cat shared_prefs/settings.xml > $EV/settings-backup-sam.xml
adb -s $SAM pull "$(adb -s $SAM shell pm path $PKG | cut -d: -f2 | tr -d '\r')" $APKS/prior-sam.apk && md5sum $APKS/prior-sam.apk
adb -s $SAM install -r -d $APK
$RIG_TOOLKIT/set_prefs_runas_host.py $SAM scalar:int:wifi-connection-mode:3 scalar:int:onboarding-version:2 scalar:int:log-level:2 scalar:int:native-driver-selection-mode:0 scalar:boolean:native-poke-all-paired:false setclear:native-poke-bt-macs
adb -s $SAM shell run-as $PKG cat shared_prefs/settings.xml | grep -ao 'wifi-connection-mode" value="[0-9]"'   # MUST print value="3"
send ACTION_QUERY_STATE                                   # commit MUST start with 79298974; this starts the app process
adb -s $SAM shell dumpsys bluetooth_manager | grep -a -m2 -iE 'enabled|state'                # record: the adapter state
adb -s $SAM logcat -c && ( stdbuf -oL adb -s $SAM logcat -v time > $EV/S-sam.txt & echo $! > $EV/S-sam.pid )
adb -s $SAM shell log -t RIGMARK RIGMARK S-go
if [ -n "$DEST" ]; then adb -s $SAM shell run-as $PKG am start --user 0 -n $SETT --ei extra_destination $DEST
else cue "H3 (D-SAM): open Settings, then Native BT Broker"; fi
tap $SAM s-scan 'Look for OpenHU-NativeBTBroker-Setter'; echo "tap=$?" | tee -a $EV/S.log
waitline $EV/S-sam.txt 'FATAL EXCEPTION' 15; echo "fatal=$?" | tee -a $EV/S.log
sleep 2; adb -s $SAM shell log -t RIGMARK RIGMARK S-end; sleep 1; kill "$(cat $EV/S-sam.pid)"
grep -a -A25 'FATAL EXCEPTION' $EV/S-sam.txt | grep -aE 'FATAL EXCEPTION|Process: |Error|Exception|NativeBTBroker|BrokerSettingsFragment' | head -20
```

The PR's broker row appears only in Native mode, which is why `wifi-connection-mode` is 3. If `run-as ... am start` fails, use H3 and say so.

- **S verdict.** **FAIL (predicted)** when a `FATAL EXCEPTION` for `com.andrerinas.headunitrevived` prints within 15 s of the tap, and its trace names `NativeBTBrokerClient` with `NoSuchMethodError`, `NoClassDefFoundError` or `VerifyError`. **PASS** (the prediction is wrong) when no `FATAL EXCEPTION` prints and the screen does not close. **INCONCLUSIVE** when the tap did not resolve. If the crash comes when the screen opens, before the tap, it is still FAIL: say which.
- **Report:** whether `bluetooth_le` is listed, the `FATAL EXCEPTION` block (first 20 lines), and the adapter state.
- **Restore D-SAM:** force-stop, `adb -s $SAM install -r -d $APKS/prior-sam.apk`, push the backup and `run-as $PKG cp` it into `shared_prefs/settings.xml` (the D-SAM recipe), and compare with CRs stripped. D-SAM's platform removes an empty P2P group. That does not matter here, because the run stops at Scan.

### Closing

Run all of it, whatever happened before.

1. **D-HU:** `adb -s $HU shell am start -a android.intent.action.VIEW -d headunit://exit` while the app runs, `sleep 3`, force-stop. `broker_forget`. `adb -s $HU install -r -d $APKS/prior-hu.apk`; pull and check the md5. Restore `settings.xml` as root: push the backup, `cp` it into `$SP`, `chown $APPUID:$APPUID`, `chmod 660`, and diff. The diff must be empty.
2. **Board E-1:** `kill $(cat $EV/ser.pid)`. Leave the `apply_hfp` firmware on it (or `v1.0.0` if Prepare-2 did not run); it ran only a blink sketch before. Do not touch E-2.
3. **Phones:** `adb -s $PH shell svc bluetooth enable` and read it back. D-MOTO: `svc bluetooth disable` and `svc wifi disable` (its opening state). Then cue H9 and open each Bluetooth settings screen with `am start -a android.settings.BLUETOOTH_SETTINGS`, so the stale bonds cannot mislead a later round.
4. `clk`. `readers` prints 0. Kill every thermal watcher by pid.
5. Zip `$EV` without `venv/`, `fw/`, `fw-apply_hfp/pio-venv/`, `fw-apply_hfp/pio-core/` and `fw-apply_hfp/src/.pio/`, but with `fw-apply_hfp/bins/SHA256SUMS`, `build.txt`, `pkgs.txt` and `flash2.txt`: every capture, `serial-E1.txt`, every `.tsv`, `ids.env`, `marks.txt`, `taps.log`, `hand-steps.log`, `keys.log`, `relink.log`, `pair.log`, `clocks.txt`, `flash.txt`, the `ui-*.xml` dumps and the `*-telecom.txt` files. Create the release and upload the asset (template §7). Put the size and sha256 in the results file.

### Stop rules, as counts

- C1: 0 of 2 sessions twice stops the main arm (B, B2, C2, M UNTESTABLE, rig).
- P: 2 failed pairings stop B, B2, C2 and M (UNTESTABLE, pairing).
- B and B2: 5 cycles each, or 3 cycles in a row with `ssl=0`. At most 2 voids per run, each re-run once. A B stop does not skip Prepare-2 or B2.
- Prepare-2: a failed source check, build or second failed flash stops the `apply_hfp` arm. Mark B2 and M UNTESTABLE (firmware), run C2 on `v1.0.0`, and go on.
- M runs only with a callee number.
- Thermal: `th_gate` before each stage. A second failed wait stops the round; mark the rest UNTESTABLE (host thermal).

## 9. Do not re-run

- **The stock route's held RFCOMM and its re-dial cadence:** `hold-aa-rfcomm` round 1 and `late-listener-poke` round 1 measured them on this rig. C1 and C2 are controls only, and they grade no re-dial count.
- **The stand-in HFP and the wake damage verdict:** settled, and not on this route.

## 10. Report back

Give the three numbers for both arms, side by side:

| Number | B (`v1.0.0`) | B2 (`apply_hfp`) |
|---|---|---|
| 1. Sessions: cycles with a-condition and b-condition true, of the cycles run; median `t_ssl` | | |
| 2. HFP after SSL: cycles with `hfp_rel=yes`, of the cycles run; last `HFP HF state` at `-end` and at `-post`; error count per cycle (`s_err` / `terr7`) | | |
| 3. Phone re-dials in the 120 s hold: `pdial_hold` and `wpp_restart_hold` per cycle; the g-condition per cycle | | |

Also: C1 and C2 verdicts, Prepare-2's four SHA256s with `BOOT_APP0_SAME` and `PARTITIONS_SAME`, whether Stage P was repeated after the second flash, M-1 and M-2 with their `route:` counts, and S with its exception class. Report every hand step and injected tap with its time, `BROKER_LE`, `BROKER_BT`, the three clock offsets, both Gearhead versions, the firmware flash output and the APK md5s.

The block below lists every string the runs grep, one per line. The prefix names the source to re-verify it against: `app:` is `git grep -F` on the candidate under `app/src`; `fw:` is `grep -F` on the firmware `src/` at `v1.0.0`; `fw-printed:` is a form that the firmware's `printf` builds from a format string and a label (verify the format and the label); `fw-hfp:` is `grep -F` on `src/` at `apply_hfp` `21e0eb48`; `fw-hfp-printed:` is a printed form on that branch, verified as `fw-printed:`; `phone:` is the Gearhead 18.0.664204 decompile; `system:` is Android's own log.

```decisive-strings
app: AutomationReceiver: 
app: WifiLauncher: Initializing WiFi Mode:
app: NativeAA: Native BT Broker transport is on
app: ACTIVELY LISTENING on Android Auto UUID
app: NativeAA: Attempting active poke to device
app: Broker: intentional AA wake (HFP) to 
app: NativeAA: [Broker] hands-free link up to 
app: NativeAA: [Broker] the phone opened Android Auto on the broker.
app: NativeAA: [Broker] the phone closed the Android Auto channel.
app: the phone reopened Android Auto while a session is up
app: NativeAA: [Broker] transport error (
app: broker EVT_ERROR code=
app: NativeAA: [Broker] handshake ended: 
app: Broker: projection socket 
app: Broker: releasing HFP (
app: PROJECTION_UP not recorded for 10s
app: Broker: PROJECTION_UP failed for
app: Broker: link lost, retrying for 
app: Broker: opening new-phone pairing window on
app: SSL handshake complete
app: Throughput over
app: WiFi session landed. Holding the Bluetooth channel
app: releasing the held Bluetooth channel after
app: WirelessServer: Incoming connection detected from
app: createGroup SUCCESS
app: MATCH! Starting AapService
app: BT Device connected: 
fw: Classic name OpenHU-NativeBTBroker, BLE name
fw: broker self-check failed
fw: advertising OpenHU-NativeBTBroker-Setter
fw: SPP server on RFCOMM channel
fw: classic discoverable=
fw: head unit BLE connected
fw: head unit BLE disconnected
fw: BLE bond 
fw: rx frame type=
fw: tx frame type=
fw: HFP HF state 
fw: HFP audio state 
fw: phone RFCOMM connected, handle
fw: phone RFCOMM closed
fw: cleared phone bonds for new pairing
fw: pairing mode for 
fw: pairing mode ended
fw: phone paired: 
fw: head unit BLE down for 10s; closing the phone link
fw: reconnecting last phone 
fw-printed: rx frame type=03 AA_ENABLE
fw-printed: rx frame type=06 SET_BOND_MODE
fw-printed: rx frame type=07 PROJECTION_UP
fw-printed: rx frame type=08 UNKNOWN
fw-printed: rx frame type=09 UNKNOWN
fw-printed: tx frame type=26 ERROR
fw-printed: HFP HF state 0
fw-printed: BLE bond ok
fw-hfp: Classic name OpenHU-NativeBTBroker, BLE name
fw-hfp: broker self-check failed
fw-hfp: advertising OpenHU-NativeBTBroker-Setter
fw-hfp: head unit BLE connected
fw-hfp: head unit BLE disconnected
fw-hfp: rx frame type=
fw-hfp: tx frame type=
fw-hfp: HFP HF state 
fw-hfp: HFP audio state 
fw-hfp: HFP on (
fw-hfp: HFP off (
fw-hfp: HFP disconnect 
fw-hfp: HFP refused
fw-hfp: RFCOMM stream failed, code=
fw-hfp: phone RFCOMM connected, handle
fw-hfp: phone RFCOMM closed
fw-hfp: pairing failed: 
fw-hfp: reconnecting last phone 
fw-hfp-printed: rx frame type=07 PROJECTION_UP
fw-hfp-printed: rx frame type=08 HFP_ON
fw-hfp-printed: rx frame type=09 HFP_OFF
fw-hfp-printed: tx frame type=26 ERROR
fw-hfp-printed: HFP HF state 0
phone: Creating rfcomm socket for device
phone: Triggering WPP restart
phone: Rfcomm socket connection failed
phone: has timed out on pings
phone: Critical error
system: FATAL EXCEPTION
```
