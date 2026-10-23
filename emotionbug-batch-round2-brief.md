# emotionbug-batch, round 2 brief

Published name: `emotionbug-batch-round2-brief.md`. Results go in `emotionbug-batch-round2-results.md`.
Evidence: upload `emotionbug-batch-round2-captures.zip` to the existing release `rig-evidence-emotionbug-batch`.

Units: D-HU (head unit), D-POCO (phone in Stage A, USB head unit in Stage B), D-MOTO (USB phone in
Stage B only; its Bluetooth stays off for the whole round). D-SAM and D-HP take no part.

Queue line: `emotionbug-batch-round2-brief.md` (C `4c5960c7` on `fork/test/emotionbug-batch-4pr`, three updated
contributor PRs on `main` `fbeb46fa`, one arm, no baseline; units D-HU, D-POCO, D-MOTO; W1 and U1 are the point).

Pairing: none. This round uses D-HU and both phones.

Hand steps: HS1 to HS4 in Stage B (plug, unplug, replug, USB permission dialog). Injected taps: none.

**Time budget.** Pre-flight 8 min, build 25 min, runs 50 min, thermal waits about 10 min. About 1 h 35 min in all.

| Stage | Runs | Units | Minutes |
|---|---|---|---|
| A | W1, W2 (one capture) | D-HU + D-POCO, wireless mode 3 | 22 |
| B | U1, U2, U3 (one capture) | D-POCO hosts D-MOTO over USB | 22 |
| | stage change | | 6 |

**W1 and U1 are the point of the round.** W1 covers the three PRs in one session. U1 is the replug run that the
new USB drain exists for.

## 1. Build and baseline

One arm. Round 1 (`emotionbug-batch-round1-results.md`, C `14ad25b8`) passed every label. Since then the contributor
added commits to three PRs, and LIBUSB merged into `main`. The test branch took `main` `fbeb46fa` and the three new
PR heads as plain merges on top of `14ad25b8`, then `main` again at `fbeb46fa` after the owner merged the
contributor's Shizuku WiFi scan pause (PR #1092). That last merge conflicted only on additions (one `<queries>`
package and appended strings in `values` and `values-ko`); both sides were kept. No history was rewritten.

| Arm | Ref | Commit | Tree |
|---|---|---|---|
| C (candidate) | `fork/test/emotionbug-batch-4pr` | `4c5960c7856e49c620a17b96c76fe6623d8c1cea` | `22c747c98f5568ee4d9233dfb99c49cf7c2d5859` |

| Label | PR head now | Round 1 head | New since round 1 |
|---|---|---|---|
| LIBUSB | merged in `main` | `fb840234` | `26a0f691`: native logs for a write timeout and for a read timeout that kept bytes |
| FRAMING | `754e257e` | `39d10947` | the pre-request USB drain now goes through the frame reader and skips old encrypted frames by length; a failed USB version write may be resent; the version log line changed back |
| PRESENT | `f6253867` | `06668caf` | a disconnect now sends an empty STOP navigation broadcast if one was sent; display callbacks catch `Throwable` |
| SCAN (not under test) | merged in `main` (`90f4cf68`) | not in round 1 | Shizuku pause of automatic WiFi searches during a Native session; off by default |
| BRIDGE | `62420534` | `82499a57` | a new HCT route (Microntek `android.microntek.mtcser` service and its `goc_rfcom` socket); manufacturer-paired phones from `Settings.System` `MTC.BT.matchList` in the wake list and the picker; a 10 s cap on waiting for a busy ZBT carrier |

The dev host ran the JVM suite at `4c5960c7`: 3563 tests, 0 failures, 0 skipped. Do not run the unit tests on the rig.

**get_apk.sh**

```bash
OUT=rig-data/rounds/emotionbug-batch-round2; mkdir -p $OUT
WT=$(ls -d worktrees/* 2>/dev/null | head -1)        # any existing worktree; else create one with wt-new.sh
git -C $WT fetch fork test/emotionbug-batch-4pr
git -C $WT checkout --detach 4c5960c7856e49c620a17b96c76fe6623d8c1cea
git -C $WT rev-parse 'HEAD^{tree}'                    # MUST print 22c747c98f5568ee4d9233dfb99c49cf7c2d5859
$RIG_TOOLKIT/rig_thermal.sh wait 70
HUR_DIR=$WT MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh   # copy the APK out of apks/ to $OUT/apk-C.apk at once
md5sum $OUT/apk-C.apk                                 # record in Setup notes
```

Install with `adb install -r -d $OUT/apk-C.apk` on D-HU and on D-POCO. D-MOTO keeps the APK it has; it is a
phone only. Identity, on each of D-HU and D-POCO after the install:

- `send ACTION_QUERY_STATE`: the `data=` line MUST carry commit `4c5960c7`.
- Pull the live APK and run
  `for s in HandshakeFrameReader PresentationQueue HctServiceObserver BluetoothPeerDiscovery; do unzip -p live.apk 'classes*.dex' | strings | grep -c $s; done`.
  Each count MUST be at least 1. `HctServiceObserver` and `BluetoothPeerDiscovery` are new since round 1, so they
  tell this build from round 1's.

## 2. What this is and why

Round 1 proved the four PRs on ordinary units. This round proves the new commits did not break that, and
exercises the two new paths the rig can reach.

- **FRAMING, the USB drain.** Before the version request, a USB connection now reads stale frames through the
  same frame reader it uses for the reply. Old encrypted frames (flags 0x08 to 0x0f) are skipped by their
  length. Bytes with no frame boundary are discarded until the pipe goes idle, at most 500 ms. Either case
  prints an INFO line. After the request the reader stays strict. The risk is a drain that eats the phone's
  reply, or a replug that now fails where `main` recovered. U1 replugs, and U3 reconnects without a replug,
  which is where old frames are most likely.
- **FRAMING, the version line.** The INFO line is `Handshake: the phone selected X.Y (we asked for 1.2), status S`
  again. Round 1's `Handshake: Version response X.Y, ...` no longer exists. Grade on the new text only.
- **PRESENT, the STOP broadcast.** On disconnect, the app now sends an empty `NAVIGATION_UPDATE` broadcast if it
  sent one in the session, then cancels the nav notification. W2 disconnects during an active route, so both
  happen. The broadcast has no log line; W2 reads it from the broadcast history as information.
- **BRIDGE, HCT.** The HCT route needs the system service `android.microntek.mtcser/.BlueToothService`, a
  `BluetoothSocket.connectRfcom()` method and `SERIAL_PORT`. D-HU has none of them, so it must route as
  before: Android Bluetooth, the poke, a Native handshake. The new code that does run on D-HU is the
  manufacturer pairing read (`Settings.System` key `MTC.BT.matchList`) on every poke pass and in the driver
  candidate list. On D-HU that key is unset, so it must change nothing. The HCT carrier itself can only be
  proven on a reporter's unit.
- **SCAN, inert by default.** The Shizuku scan pause is now `main` code and ships in this APK, but it is not under
  test. It reads its own prefs file, `shared_prefs/wifi-scan-option.xml`, key `enabled`, default false. Its FYT
  auto-start also needs that key, Shizuku running and a FYT unit (`com.syu.ms`). No rig unit has Shizuku or FYT.
  So the only check is that it stays silent: any `FYT Shizuku: ` or `Wi-Fi scan control: ` line is a FAIL for
  SCAN. It hooks into every wireless session on API 26+, which W1 and W2 cover. Do not enable it.
- **LIBUSB.** Merged, so it is now `main` code. U1 stays as a no-regression run, and records the new native
  `read timeout: preserved=` line if it appears.

## 3. What is different about this round

Round 1's lessons, applied here:

- **Nav probes.** `dumpsys notification | grep headunit_navigation` matches the channel definition. Grade a
  posted nav notification on `NotificationRecord` lines for the app package with `channel=headunit_navigation`.
  D-POCO sends no `INSTRUMENT_CLUSTER_NAVIGATION_STATE`, so `Nav: NavigationState steps=` is not graded. Turn
  data is graded on `Nav: Navigation status=ACTIVE` and `NextTurnDetail`.
- **Destination.** D-POCO has no mobile data. Its offline maps cover Medellin, Marinilla and Rionegro. The route
  is `Parque Principal Marinilla` (11 s to ACTIVE in round 1).
- **SSL line.** Match `SSL handshake complete` with no `Handshake:` prefix. At `log-level=2` only the INFO line
  `AapSslContext.performHandshake | SSL handshake complete` is logged.
- **Plug detection.** `dumpsys usb | grep /dev/bus/usb/` reads non-zero with nothing plugged. Detect plug and
  unplug on D-POCO's capture lines instead (`wait_usb` in section 5).
- **D-MOTO's capture.** Its wireless adb drops when it enters accessory mode. Round 1 lost the whole phone side
  of Stage B. This round sets a 16 MB buffer, reconnects after each session and dumps the buffer with
  `logcat -d` (`moto_dump` in section 5).
- **Expected unplug errors.** `libusb_bulk_transfer read failed` within 1 s after a hand unplug (a kernel
  `USB disconnect` or `UsbHostManager` removal line) is the unplug, not a FAIL. Any other one is a FAIL.

Unchanged from round 1:

- **D-HU has no USB accessory path** (`rig-quirks/topics/tooling.md`). D-POCO is the USB head unit on wireless
  adb and D-MOTO plugs into its OTG port. Charge D-POCO to 80 % or more before Stage B.
- **Role swap.** Before Stage A, stop the app on D-POCO with `headunit://exit` and a force-stop. Before Stage B,
  stop it on D-HU the same way.
- **Stage A runs at `log-level=1`** (nav lines are DEBUG). The capture runs from before the launch to the end of
  W2; nothing relies on `logcat -d` on D-HU.
- **On mode 3 the launch arms the stack.** No `ACTION_START_WIRELESS_SCAN` straight after a launch. W2 sends it
  once, after an exit.
- **The phone leaves the link by `svc`**, not airplane mode (`ph_off`).
- **Gearhead can dial a cached head unit.** Record D-POCO's bonded list. D-SAM's Bluetooth stays off.
- **Music on D-POCO**, a playlist of 6 or more tracks in the rig's audio player (`rig-quirks/topics/audio.md`),
  playing before the session starts.

## 4. Settings keys

Back up `settings.xml` on D-HU and D-POCO at the start (`$OUT/settings-backup-hu.xml`, `-poco.xml`), and diff
each against the end. State the delta in Setup notes. Leave the audio keys as they are. Record
`video-profile-starvation-cap` on both units; if it is set, delete it and say so.

**D-HU, Stage A:**

```xml
<int name="wifi-connection-mode" value="3" />
<int name="native-ap-transport" value="0" />
<int name="log-level" value="1" />
<int name="onboarding-version" value="2" />
<boolean name="allow-external-configuration" value="true" />
<boolean name="sync-media-session-aa-metadata" value="true" />
<boolean name="show-navigation-notifications" value="true" />
<boolean name="external-bt-zbt-transport" value="false" />
<boolean name="external-bt-blink-transport" value="false" />
<boolean name="native-aa-ignore-external-bt" value="false" />
```

**D-POCO, Stage B** (with the fixed `set:` writer, `rig-quirks/topics/tooling.md`):

```
int:wifi-connection-mode=0 int:log-level=2 int:onboarding-version=2 bool:allow-external-configuration=true
bool:auto-start-on-usb=false bool:auto-connect-single-usb=false bool:auto-connect-last-session=false
bool:reopen-on-reconnection=false set:connection-modes=usb
```

Plus `bool:use-libusb=true` for U1 and U3, and `bool:use-libusb=false` for U2.

## 5. Helpers

Run every adb call in sequence, never two at once against one unit. Write the results file after every run.

**lib.sh**

```bash
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
HU=27870808938846; PH=4f4027e9; MOTO=ZY22GC3BM4
OUT=rig-data/rounds/emotionbug-batch-round2; mkdir -p $OUT
export LC_ALL=C
# D = the unit the app under test runs on (D-HU in Stage A, $POCO_W in Stage B)
send() { a=$1; shift; adb -s $D shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; }
mark() { send ACTION_LOG_MARKER --es text "$1" >/dev/null; sleep 0.3; echo "$1 $(date '+%m-%d %H:%M:%S')" >> $OUT/marks.txt; }
launch() { adb -s $D shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity >/dev/null; }
appexit() { adb -s $1 shell am start -a android.intent.action.VIEW -d "headunit://exit" >/dev/null; sleep 5
  adb -s $1 shell am force-stop $PKG; sleep 1; }
# count of a fixed string after the last occurrence of a marker
after() { sed -n "/$2/,\$p" $1 | grep -acF -- "$3"; }
# wait until the count of a string after a marker reaches n, capped in seconds
waitn() { local f=$1 m=$2 s=$3 n=$4 cap=$5 t=0
  while [ $t -lt $cap ]; do [ "$(after $f $m "$s")" -ge $n ] && return 0; sleep 1; t=$((t+1)); done; return 1; }

# host thermal (rig-quirks/topics/tooling.md)
th_gate() { $RIG_TOOLKIT/rig_thermal.sh wait 75 || $RIG_TOOLKIT/rig_thermal.sh wait 75 || { echo HOST_HOT; return 1; }
  $RIG_TOOLKIT/rig_thermal.sh watch $OUT/thermal-$RUN.log & THPID=$!; }
th_end() { kill $THPID 2>/dev/null; wait $THPID 2>/dev/null; }

# phone radios
ph_bt()  { adb -s $1 shell svc bluetooth $2; sleep 4; adb -s $1 shell dumpsys bluetooth_manager | grep -m1 -E "^ *state:"; }
ph_off() { adb -s $PH shell svc wifi disable; sleep 0.3; ph_bt $PH disable; adb -s $PH shell dumpsys wifi | grep -m1 "Wi-Fi is"; }
ph_on()  { adb -s $PH shell svc wifi enable; sleep 0.3; ph_bt $PH enable; adb -s $PH shell dumpsys wifi | grep -m1 "Wi-Fi is"; }
ph_nav() { adb -s $PH shell am start -a android.intent.action.VIEW -p com.google.android.apps.maps \
  -d "google.navigation:q=Parque+Principal+Marinilla\&mode=d" >/dev/null; }

# media title on the app's MediaSession (head unit) and on the player (phone)
hu_title() { adb -s $D shell dumpsys media_session | grep -a -A25 "package=$PKG" | grep -a -m1 -o 'description=[^,]*' | tr -d '\r'; }
ph_title() { adb -s $PH shell dumpsys media_session | grep -a -m1 -o 'description=[^,]*' | tr -d '\r'; }
# posted nav notifications (NotificationRecord lines, never the channel definition)
hu_nav() { adb -s $D shell dumpsys notification --noredact | grep -a "NotificationRecord" | grep -a "pkg=$PKG" | grep -ac "headunit_navigation"; }
hu_notes_save() { adb -s $D shell dumpsys notification --noredact | grep -a "NotificationRecord" | grep -a "pkg=$PKG" > $1; }
# NAVIGATION_UPDATE broadcasts in the system history (information only)
hu_navbc() { adb -s $D shell dumpsys activity broadcasts history | grep -a -B1 -A4 "$PKG.NAVIGATION_UPDATE" > $1; grep -ac "act=$PKG.NAVIGATION_UPDATE" $1; }

# Stage B: plug state from D-POCO's capture (UsbHostManager or kernel lines), capped
wait_usb() { local want=$1 cap=$2 before=$3 t=0   # want = added|removed; before = count at cue time
  local pat='UsbHostManager.*Added device|usb [0-9-]+: new .*USB device'
  [ $want = removed ] && pat='UsbHostManager.*Removed device|usb [0-9-]+: USB disconnect'
  while [ $t -lt $cap ]; do [ "$(grep -acE "$pat" $OUT/B-hu.txt)" -gt $before ] && { echo "$want $(date '+%H:%M:%S')"; return 0; }
    sleep 1; t=$((t+1)); done; echo "$want NOT_SEEN"; return 1; }
usb_count() { local pat='UsbHostManager.*Added device|usb [0-9-]+: new .*USB device'
  [ $1 = removed ] && pat='UsbHostManager.*Removed device|usb [0-9-]+: USB disconnect'; grep -acE "$pat" $OUT/B-hu.txt; }
# Stage B: D-MOTO's buffer, after its wireless adb comes back (cap 60 s)
moto_dump() { local t=0; while [ $t -lt 60 ]; do adb connect $MOTO_W >/dev/null 2>&1
    adb -s $MOTO_W shell true 2>/dev/null && { adb -s $MOTO_W logcat -d -v time > $OUT/B-phone-$1.txt; echo "moto $1 ok"; return 0; }
    sleep 2; t=$((t+2)); done; echo "moto $1 UNREACHABLE"; return 1; }
```

If D-POCO's capture shows neither the `UsbHostManager` nor the kernel line for a plug the operator made,
`wait_usb` prints `NOT_SEEN` after its cap. Then mark at the operator's cue, say so in Setup notes, and go on.

`ph_title` reads the first active session on D-POCO. If D-POCO shows more than one, record which package it read.

## 6. The deciding lines

All verified with `grep -F -r` in `app/src/main` at `4c5960c7`. Grep every capture with `-a`.

| Line | Level | PR | Where |
|---|---|---|---|
| `NativeAA: ACTIVELY LISTENING on Android Auto UUID` | INFO | BRIDGE | D-HU |
| `NativeAA: [TX] Sending WifiStartRequest (Type 1)` | INFO | BRIDGE | D-HU |
| `WirelessServer: Incoming connection detected` | INFO | BRIDGE | D-HU |
| `BluetoothHelper: driver candidates: ` | INFO, only when the mix changes | BRIDGE | D-HU |
| `Handshake: the phone selected ` (then `X.Y (we asked for 1.2), status STATUS_...`) | INFO | FRAMING | both head units |
| `SSL handshake complete` | INFO | FRAMING | both head units |
| ` stale USB bytes before version request` (after `Handshake: Read N`) | INFO | FRAMING | D-POCO |
| `Handshake: Discarding unframed USB data before version request` | INFO | FRAMING | D-POCO |
| `Media Sink Setup Request` | INFO | session | both head units |
| `Throughput over` (`rendered=N`) | INFO | session | both head units |
| `AapTransport: transport dispatch over` (`longest=Nms on <CHANNEL>`) | INFO | PRESENT | D-HU |
| `Navigation status=ACTIVE` and `NextTurnDetail` | DEBUG | PRESENT | D-HU |
| `LibusbAccessoryConnection: Successfully connected via JNI Libusb` | INFO | LIBUSB | D-POCO |
| `Established connection: ` | INFO | standard USB | D-POCO |
| `libusb_bulk_transfer read timeout: preserved=` | native DEBUG | LIBUSB | D-POCO |

FAIL lines. Any one of these in a run's window is a FAIL for the label named:

| Line | Level | PR |
|---|---|---|
| `Handshake: rejected VERSION_RESPONSE` | ERROR | FRAMING |
| `Invalid cleartext handshake header` | in `Handshake failed with exception` (ERROR) | FRAMING |
| `SSL Handshake: expected channel 0 SSL_HANDSHAKE message` | ERROR | FRAMING |
| `Handshake: Version request send failed (ret=` | WARN | FRAMING |
| `AA presentation update failed` | ERROR | PRESENT |
| `vendor Bluetooth bridge candidate detected` | INFO | BRIDGE |
| `[ZBT candidate]` | INFO | BRIDGE |
| `NativeAA: external Bluetooth module detected (` | WARN/ERROR | BRIDGE |
| `NativeAA: [HCT]` | INFO/WARN | BRIDGE |
| `Bluetooth runs over the external module` | INFO | BRIDGE |
| `No eligible Android wake target; manufacturer pairing history` | WARN | BRIDGE |
| `Bluetooth module still busy after 10s` | WARN | BRIDGE |
| `libusb_bulk_transfer read failed` (native), except within 1 s after a hand unplug (section 3) | ERROR | LIBUSB |
| `libusb_bulk_transfer write failed` (native), same exception | ERROR | LIBUSB |
| `FYT Shizuku: ` | INFO/ERROR | SCAN |
| `Wi-Fi scan control: ` | INFO/WARN | SCAN |

The log export header carries `bt=<route>`. On D-HU it MUST read `bt=internal`.

Round 1's line `Handshake: Version response X.Y, requested 1.2, status ...` is gone from this build. A count of 0
for it is expected and is not a missing session.

## 7. Pre-flight (8 min)

**preflight.sh**

```bash
. ./lib.sh
# D-HU: no vendor bridge markers (BRIDGE). All four MUST be "No such file".
adb -s $HU shell ls /system/bin/fscsdkbw123 /system/bin/gocsdk_zj /dev/rf_serial /dev/zj_bt_serial 2>&1 | tee $OUT/pre-markers.txt
adb -s $HU shell getprop rw.zlink.bt.type; adb -s $HU shell getprop rw.zj.bt.type     # both MUST be empty
# D-HU: no HCT service, no manufacturer pairing list (BRIDGE). Both MUST print nothing / "null".
adb -s $HU shell pm list packages android.microntek.mtcser | tee $OUT/pre-hct.txt
adb -s $HU shell settings get system MTC.BT.matchList | tee -a $OUT/pre-hct.txt
# D-HU and D-POCO: no Shizuku, no FYT service, scan pause off (SCAN). Packages MUST print nothing; the prefs
# file MUST be absent or hold enabled=false. D-HU is rooted; D-POCO is read with run-as.
for u in $HU $PH; do adb -s $u shell pm list packages moe.shizuku.privileged.api; adb -s $u shell pm list packages com.syu.ms; done | tee $OUT/pre-scan.txt
adb -s $HU shell su -c "cat /data/data/$PKG/shared_prefs/wifi-scan-option.xml" 2>&1 | tee -a $OUT/pre-scan.txt
adb -s $PH shell run-as $PKG cat shared_prefs/wifi-scan-option.xml 2>&1 | tee -a $OUT/pre-scan.txt
# D-HU: shared_prefs owner (rig-quirks/units/D-HU.md)
adb -s $HU shell stat -c '%U:%G %a' /data/data/$PKG/shared_prefs | tee $OUT/pre-stat.txt
# D-POCO: bonded devices; D-SAM radio off
adb -s $PH shell dumpsys bluetooth_manager | grep -a -A8 'Bonded devices' | tee $OUT/pre-bonds.txt
# D-POCO: stop its own app before it acts as a phone
appexit $PH
# D-MOTO: Bluetooth off for the whole round
ph_bt $MOTO disable
# D-POCO and D-MOTO wireless adb for Stage B, while both are still on PC cables
adb -s $PH tcpip 5555; adb -s $MOTO tcpip 5555; sleep 3
POCO_IP=$(adb -s $PH shell ip -4 addr show wlan0 | grep -o 'inet [0-9.]*' | cut -d' ' -f2)
MOTO_IP=$(adb -s $MOTO shell ip -4 addr show wlan0 | grep -o 'inet [0-9.]*' | cut -d' ' -f2)
echo "POCO_W=$POCO_IP:5555 MOTO_W=$MOTO_IP:5555" | tee $OUT/pre-wadb.txt
adb -s $PH shell dumpsys battery | grep level
```

If a marker file exists, a prop is set, the package is listed or the key holds a value, BRIDGE is INCONCLUSIVE on
this unit: say so and go on.

## 8. Runs

Each run follows `TESTING-TEMPLATE.md` §4 for its first session. Stop rule for every run: a run that reaches no
`SSL handshake complete` within 120 s of its trigger ends there, graded from what it has. Retry a run at most once.

### Stage A: W1 and W2, one capture (22 min). D = D-HU.

**stageA.sh**

```bash
. ./lib.sh; D=$HU; RUN=A; th_gate || exit 1
ph_off
adb -s $HU shell am force-stop $PKG
# write the Stage A keys (section 4) with set_hu_settings_host.py; read them back
adb -s $HU logcat -G 16M; adb -s $HU logcat -c
stdbuf -oL adb -s $HU logcat -v time > $OUT/A-hu.txt & HUPID=$!
adb -s $PH logcat -G 16M; adb -s $PH logcat -c
stdbuf -oL adb -s $PH logcat -v time > $OUT/A-phone.txt & PHPID=$!
launch; sleep 2; mark W1-start
ph_on
# start music on D-POCO now (section 3) if it is not already playing
waitn $OUT/A-hu.txt W1-start "SSL handshake complete" 1 120 || echo "W1 NO_SESSION" >> $OUT/marks.txt
waitn $OUT/A-hu.txt W1-start "Throughput over" 1 30
mark W1-session
```

`ACTIVELY LISTENING` can print before `W1-start` (round 1: 2 s before). Count it over the whole capture.

**W1, the point of the round (14 min).** In order, after `W1-session`:

1. **Five track changes, 20 s apart (PRESENT).** Repeat 5 times:
   `adb -s $HU shell input keyevent KEYCODE_MEDIA_NEXT; mark W1-skip$i; sleep 3; echo "skip$i hu=[$(hu_title)] ph=[$(ph_title)]" >> $OUT/W1-titles.txt; sleep 17`.
2. **A navigation route (PRESENT).** `ph_nav; mark W1-nav`. Then
   `waitn $OUT/A-hu.txt W1-nav "Navigation status=ACTIVE" 1 90; sleep 20; echo "nav=$(hu_nav)" >> $OUT/W1-notes.txt; hu_notes_save $OUT/W1-notif-live.txt`.
   Leave the route running into W2.
3. **Log export (BRIDGE route token).** `send ACTION_EXPORT_LOG`; pull the newest `HUR_Log_*.txt` it names into
   `$OUT/W1-export.txt` and run `grep -a -m1 -o 'bt=[^ |]*' $OUT/W1-export.txt`.
4. `mark W1-end`.

W1 PASS needs every one of these, in the `W1-start` to `W1-end` window of `A-hu.txt` unless named:

- `ACTIVELY LISTENING on Android Auto UUID` ≥ 1 (whole capture), `Sending WifiStartRequest (Type 1)` ≥ 1,
  `Incoming connection detected` ≥ 1, `SSL handshake complete` ≥ 1 (BRIDGE, FRAMING).
- Exactly one `Handshake: the phone selected ` line, and it ends `status STATUS_SUCCESS` (FRAMING). Record `X.Y`.
- `Media Sink Setup Request` ≥ 1 and the last `Throughput over` before `W1-end` has `rendered=` > 0.
- In `W1-titles.txt`, `hu=` equals `ph=` on at least 4 of the 5 lines, and the 5 `hu=` values hold at least 4
  different titles (PRESENT). A line with an empty `ph=` does not count either way; record it.
- `Navigation status=ACTIVE` ≥ 1 and `NextTurnDetail` ≥ 1 after `W1-nav`, and `nav=` in `W1-notes.txt` ≥ 1 (PRESENT).
- No `transport dispatch over` line has `longest=` ≥ 50 ms `on MUSIC_PLAYBACK` or `on NAVIGATION_DIRECTIONS`
  (PRESENT). Record the largest on each of those two channels.
- 0 lines from the FAIL table for FRAMING, PRESENT, BRIDGE and SCAN, over the whole capture up to `W1-end`.
- The export reads `bt=internal` (BRIDGE).
- Record every `BluetoothHelper: driver candidates: ` line in the capture as found (information).
- Phone capture `A-phone.txt`, `W1-start` to `W1-end` by `marks.txt` wall clock (phone runs about 0.4 s ahead):
  Gearhead logs 0 `Critical error` and 0 `ByeBye` (`grep -ac`).

Grade each bullet against its label, so one bullet's FAIL does not hide the others.

**W2, exit during the route, then reconnect (6 min).** Same capture. Continue after `W1-end`:

**W2.sh**

```bash
. ./lib.sh; D=$HU
hu_navbc $OUT/W2-navbc-before.txt > $OUT/W2-navbc-before.count
mark W2-start
send ACTION_DISCONNECT
sleep 8; echo "after-exit nav=$(hu_nav) media=$(hu_title)" >> $OUT/W2-notes.txt; hu_notes_save $OUT/W2-notif-after.txt
hu_navbc $OUT/W2-navbc-after.txt > $OUT/W2-navbc-after.count
mark W2-rearm
send ACTION_START_WIRELESS_SCAN
waitn $OUT/A-hu.txt W2-rearm "SSL handshake complete" 1 120 || echo "W2 NO_SESSION" >> $OUT/marks.txt
waitn $OUT/A-hu.txt W2-rearm "Throughput over" 1 30
adb -s $HU shell input keyevent KEYCODE_MEDIA_NEXT; mark W2-skip; sleep 4
echo "skip hu=[$(hu_title)] ph=[$(ph_title)]" >> $OUT/W2-notes.txt
mark W2-end
kill $HUPID $PHPID; th_end
appexit $HU
```

W2 PASS needs all of:

- `after-exit nav=0` in `W2-notes.txt` (PRESENT).
- After `W2-rearm`: exactly one `Handshake: the phone selected ` ending `status STATUS_SUCCESS`, and
  `SSL handshake complete` ≥ 1 (FRAMING).
- The `skip` line has `hu=` equal to `ph=`, and `hu=` differs from the last W1 title (PRESENT).
- 0 FAIL-table lines between `W2-start` and `W2-end`.
- Phone capture: between `W2-rearm` and `W2-end`, 0 `Critical error` (by wall clock). The ones right after
  `ACTION_DISCONNECT` (`Critical error 4 ... reason:1`, `3 ... io error`, `18 ... Failed to read message`) are
  the normal shape of a head-unit exit and are not counted.

Record as information, not conditions: `after-exit media=`; the two `W2-navbc-*.count` values and whether
`W2-navbc-after.txt` holds a `NAVIGATION_UPDATE` entry newer than the `before` file (the STOP broadcast). If the
history carries no entry at all, write "history not available" and go on.

### Stage B: U1, U2, U3, one capture (22 min). D = $POCO_W.

Set-up: D-HU app stopped (done at the end of W2). Move D-POCO to wireless adb (`adb connect $POCO_W`) and free
its USB port. `adb connect $MOTO_W`. D-MOTO is unplugged. Write D-POCO's Stage B keys with `use-libusb=true`
(section 4) and read them back. Quote `adb -s $POCO_W shell dumpsys usb | grep -i -A4 headunitrevived` in
Setup notes.

**stageB.sh**

```bash
. ./lib.sh; D=$POCO_W; RUN=B; th_gate || exit 1
adb -s $D shell am force-stop $PKG
adb -s $D logcat -G 16M; adb -s $D logcat -c
stdbuf -oL adb -s $D logcat -v time > $OUT/B-hu.txt & HUPID=$!
adb -s $MOTO_W logcat -G 16M; adb -s $MOTO_W logcat -c
launch; sleep 5; mark U1-start
```

**U1, libusb with a replug, the second point of the round (9 min):**

1. `A=$(usb_count added)`. **HS1:** plug D-MOTO into D-POCO's OTG port. `wait_usb added 120 $A; mark U1-plug`.
2. **HS4, if shown:** "Allow Open Headunit to access the USB device?" on D-POCO. Press OK. Do not tick "always".
   Log each one in `hand-steps.log` with the VID:PID it names.
3. `send ACTION_CHECK_USB`.
4. `waitn $OUT/B-hu.txt U1-plug "SSL handshake complete" 1 120; waitn $OUT/B-hu.txt U1-plug "Throughput over" 1 30; sleep 60; mark U1-first-end; moto_dump U1a`.
5. `R=$(usb_count removed)`. **HS2:** unplug D-MOTO. `wait_usb removed 60 $R; mark U1-unplug`.
   `A=$(usb_count added)`. **HS3:** plug it back in at once. `wait_usb added 120 $A; mark U1-replug`. Repeat steps 2 and 3.
6. `waitn $OUT/B-hu.txt U1-replug "SSL handshake complete" 1 120; waitn $OUT/B-hu.txt U1-replug "Throughput over" 1 30; sleep 30; mark U1-end; moto_dump U1b`.

U1 PASS needs all of, in `B-hu.txt` between `U1-start` and `U1-end`:

- `Successfully connected via JNI Libusb` = 2 and `SSL handshake complete` = 2 (LIBUSB, FRAMING).
- 2 `Handshake: the phone selected ` lines, each ending `status STATUS_SUCCESS`. Record `X.Y` (FRAMING).
- `Media Sink Setup Request` ≥ 2, and a `Throughput over` with `rendered=` > 0 after each handshake.
- 0 FAIL-table lines for FRAMING and LIBUSB (the unplug exception in section 3 applies; quote each excepted line
  with the disconnect line it follows and the gap in ms).
- Phone dumps `B-phone-U1a.txt` and `B-phone-U1b.txt`: 0 `Critical error` inside the session windows by wall
  clock. If `moto_dump` printed `UNREACHABLE`, this bullet is INCONCLUSIVE, not FAIL.

Record per session: the `Read N stale USB bytes` value or its absence, and any `Discarding unframed USB data` line
(either is legal), and the count of `read timeout: preserved=` lines.

**U3, reconnect without a replug (5 min).** Same capture, D-MOTO stays plugged in, `use-libusb=true`. This is the
case where the old session's frames can still sit in the pipe.

1. `mark U3-start; send ACTION_DISCONNECT; sleep 5`.
2. `mark U3-reconnect; send ACTION_CHECK_USB`.
3. If no `SSL handshake complete` after `U3-reconnect` within 60 s: record `U3 NO_INPLACE`, then **HS2** and
   **HS3** (unplug, replug) with `wait_usb` as in U1, `mark U3-replug`, handle HS4, `send ACTION_CHECK_USB`, and
   wait 120 s more.
4. `waitn $OUT/B-hu.txt U3-reconnect "Throughput over" 1 30; sleep 30; mark U3-end; moto_dump U3`.

U3 PASS needs, between `U3-reconnect` and `U3-end`: `SSL handshake complete` = 1, one `Handshake: the phone
selected ` ending `STATUS_SUCCESS`, a `Throughput over` with `rendered=` > 0, and 0 FAIL-table lines for FRAMING
and LIBUSB. State whether it reconnected in place or needed the replug; a replug is not a FAIL. Record the
stale-bytes and unframed lines as in U1.

**U2, standard USB (6 min).** Keep the capture running. `R=$(usb_count removed)`; unplug D-MOTO (**HS2**),
`wait_usb removed 60 $R; mark U2-switch`, then:

```bash
adb -s $D shell am force-stop $PKG
# write bool:use-libusb=false on D-POCO, read it back
launch; sleep 5; mark U2-start
```

`A=$(usb_count added)`; plug D-MOTO in (**HS1**), `wait_usb added 120 $A`, handle **HS4**, `send ACTION_CHECK_USB`,
`waitn $OUT/B-hu.txt U2-start "SSL handshake complete" 1 120; waitn $OUT/B-hu.txt U2-start "Throughput over" 1 30; sleep 60; mark U2-end; moto_dump U2`.
Then unplug, `kill $HUPID; th_end`, `appexit $D`.

U2 PASS needs all of, between `U2-start` and `U2-end`:

- `Established connection: ` ≥ 1 and `Successfully connected via JNI Libusb` = 0.
- `SSL handshake complete` = 1, and one `Handshake: the phone selected ` ending `status STATUS_SUCCESS` (FRAMING).
- `Media Sink Setup Request` ≥ 1 and a `Throughput over` with `rendered=` > 0.
- 0 FAIL-table lines for FRAMING.

U1 or U3 FAIL with U2 PASS points at libusb; a FAIL on a handshake line in all three points at FRAMING.

After Stage B: restore D-POCO's `settings.xml` from its backup, `adb -s $PH usb` when D-POCO is back on its PC
cable, and the same for D-MOTO.

## 9. Do not re-run

Round 1's W3 (its navigation addendum): W1 and W2 now cover the route and the exit. No Headunit Server (mode 1) run.

## 10. Report back

1. The `Handshake: the phone selected` status for every session, D-POCO wireless and D-MOTO USB, and whether any
   handshake was refused (FRAMING).
2. U1 and U3: did every USB session form, did U3 reconnect in place, and the stale-bytes and unframed lines per
   session (FRAMING drain).
3. W1's title matches out of 5, `nav=` live and after the exit, the largest `longest=` on `MUSIC_PLAYBACK` and
   `NAVIGATION_DIRECTIONS`, and whether the STOP broadcast was seen (PRESENT).
4. The `bt=` token, the HCT pre-flight and the count of `[HCT]` lines (BRIDGE).
5. The SCAN pre-flight, and the count of `FYT Shizuku: ` and `Wi-Fi scan control: ` lines across Stage A (SCAN).

One verdict per label in a table at the top of the results file, beside the per-run verdicts.
