# emotionbug-batch, round 1 brief

Published name: `emotionbug-batch-round1-brief.md`. Results go in `emotionbug-batch-round1-results.md`.
Evidence: create the release `rig-evidence-emotionbug-batch` and upload `emotionbug-batch-round1-captures.zip`.

Units: D-HU (head unit), D-POCO (phone in Stage A, USB head unit in Stage B), D-MOTO (USB phone in
Stage B only; its Bluetooth stays off for the whole round). D-SAM and D-HP take no part.

Queue line: `emotionbug-batch-round1-brief.md` (C `14ad25b8` on `fork/test/emotionbug-batch-4pr`, four contributor PRs
merged on `main` `22392a15`, one arm, no baseline; units D-HU, D-POCO, D-MOTO; W1 and U1 are the point).

Pairing: none. This round uses D-HU and both phones.

Hand steps: HS1 to HS4 in Stage B (plug, unplug, replug, USB permission dialog). Injected taps: none.

**Time budget.** Pre-flight 8 min, build 25 min, runs 44 min, thermal waits about 10 min. About 1 h 30 min in
all; the runs alone take 44 min.

| Stage | Runs | Units | Minutes |
|---|---|---|---|
| A | W1, W2 (one capture) | D-HU + D-POCO, wireless mode 3 | 20 |
| B | U1, U2 (one capture) | D-POCO hosts D-MOTO over USB | 18 |
| | stage change | | 6 |

**W1 and U1 are the point of the round.** W1 covers three PRs in one session. U1 is the only libusb run.

## 1. Build and baseline

One arm. It is `main` `22392a15` with four upstream PRs merged in this order: LIBUSB, FRAMING, PRESENT, BRIDGE.
All four merged with no conflict; FRAMING was merged at its first head and again at
its updated head `39d10947`. No history was rewritten. There is no baseline build: `main` is the release
the rig already knows, and each PR's own setting or code path is the control.

| Arm | Ref | Commit | Tree |
|---|---|---|---|
| C (candidate) | `fork/test/emotionbug-batch-4pr` | `14ad25b8eda4c2f6d4dad7a90ea6041166c0fa76` | `63d2a55f6d69968e1d512be51e385ee4c94bcef7` |

| Label | PR head | What it changes |
|---|---|---|
| LIBUSB | `fb840234` | libusb read keeps the bytes of a partial transfer on timeout |
| FRAMING | `39d10947` | the cleartext handshake reads whole frames; a nonzero version status ends the handshake |
| PRESENT | `06668caf` | media and navigation display work moves off the AAP reader thread onto Main |
| BRIDGE | `82499a57` | HCT and QF vendor Bluetooth bridges are detected and probed before the module route is used |

Two more of the contributor's PRs are not in this round: the audio draft, which conflicts with `main` in
`AapAudio.kt`, and the Shizuku WiFi scan pause, held back by the engineer.

The dev host ran the JVM suite at `14ad25b8`: 3384 tests, 0 failures, 0 skipped. FRAMING's head is its second commit, which changes
tests only. Do not run the unit tests on the rig.

**get_apk.sh**

```bash
OUT=rig-data/rounds/emotionbug-batch-round1; mkdir -p $OUT
WT=$(ls -d worktrees/* 2>/dev/null | head -1)        # any existing worktree; else create one with wt-new.sh
git -C $WT fetch fork test/emotionbug-batch-4pr
git -C $WT checkout --detach 14ad25b8eda4c2f6d4dad7a90ea6041166c0fa76
git -C $WT rev-parse 'HEAD^{tree}'                    # MUST print 63d2a55f6d69968e1d512be51e385ee4c94bcef7
$RIG_TOOLKIT/rig_thermal.sh wait 70
HUR_DIR=$WT MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh   # copy the APK out of apks/ to $OUT/apk-C.apk at once
md5sum $OUT/apk-C.apk                                 # record in Setup notes
```

Install with `adb install -r -d $OUT/apk-C.apk` on D-HU and on D-POCO. D-MOTO keeps the APK it has; it is a
phone only. Identity, on each of D-HU and D-POCO after the install:

- `send ACTION_QUERY_STATE`: the `data=` line MUST carry commit `14ad25b8`.
- Pull the live APK and run
  `for s in HandshakeFrameReader PresentationQueue ZbtIdentityProbe; do unzip -p live.apk 'classes*.dex' | strings | grep -c $s; done`.
  Each of the three counts MUST be at least 1. One symbol per label except LIBUSB, which is native code.

## 2. What this is and why

The contributor opened several PRs at once. These four each touch a different seam, so one APK and one round
can cover all four. What each run must show:

- **FRAMING, handshake framing.** Every USB and wireless handshake now goes through `HandshakeFrameReader`.
  Two behaviour changes need a real phone. A version response with any nonzero status now ends the handshake
  (`Handshake: rejected VERSION_RESPONSE`); `main` went on to TLS. A frame whose flags byte is not 3 now
  throws `Invalid cleartext handshake header`; `main` logged it and waited. So each run records the status
  each phone sends, and the PASS is that both phones send `STATUS_SUCCESS` and reach SSL.
- **PRESENT, presentation off the reader.** Media metadata, playback state and nav turn notifications now run
  on Main through a queue of four slots, newest wins. Each disconnect now clears the media and nav
  notifications. The risks are a missed or stale title, a stale title after a reconnect, and a nav
  notification left behind after an exit. The rig's Binder is fast, so W1 proves no regression, not the
  speed-up.
- **BRIDGE, HCT and QF bridges.** No rig unit is HCT, QF or ZBT. On an ordinary unit, `ZbtDiscoveryPolicy.detect()`
  makes two `File.exists` reads and returns NONE, and nothing dials 3152. W1 proves an ordinary unit still
  takes Android Bluetooth and completes a Native handshake. Detection, the identity probe and the fallback
  can only be proven on a reporter's unit.
- **LIBUSB, libusb partial read.** On `LIBUSB_ERROR_TIMEOUT`, `nativeRead` now returns the bytes it got instead
  of 0. Only `use-libusb=true` reaches it. The rig cannot force a mid-transfer timeout, so U1 is a
  no-regression run on libusb, and U2 is the standard USB path with the same handshake code.

## 3. What is different about this round

- **D-HU has no USB accessory path** (`rig-quirks/topics/tooling.md`). Stage B uses the layout from
  `bluetooth-audio-disabled-usb-connect` round 5: D-POCO is the USB head unit on wireless adb, and D-MOTO
  plugs into D-POCO's OTG port. Charge D-POCO to 80 % or more before Stage B. Plug and unplug are hand steps.
- **Role swap.** D-POCO is a phone in Stage A and a head unit in Stage B. Before Stage A, stop the app
  on D-POCO with `headunit://exit` and a force-stop. Before Stage B, stop the app on D-HU the same way.
- **Nav lines are DEBUG.** Stage A runs at `log-level=1`. D-HU floods logcat, so the capture runs from before
  the launch to the end of W2 and nothing relies on `logcat -d`.
- **On `wifi-connection-mode` 3 the launch arms the stack.** No run sends `ACTION_START_WIRELESS_SCAN`
  straight after a launch. W2 sends it once, after an exit, to re-arm.
- **The phone leaves the link by `svc`, not airplane mode.** D-POCO's WiFi can self-revert; use `ph_off`.
- **Gearhead can dial a cached head unit.** Pre-flight records D-POCO's bonded list
  (`rig-quirks/units/D-POCO.md`). D-SAM must have its Bluetooth off.
- **A media player on D-POCO.** W1 needs a playlist of at least 6 tracks in the player the rig uses for audio
  rounds (`rig-quirks/topics/audio.md`), playing before the session starts.
- **Navigation is started on the phone**, not on the app: `ph_nav` sends a `google.navigation:` intent to
  Maps on D-POCO. Android Auto then sends the turns to D-HU.

## 4. Settings keys

Back up `settings.xml` on D-HU and D-POCO at the start (`$OUT/settings-backup-hu.xml`, `-poco.xml`), and diff
each against the end. State the delta in Setup notes. Leave the audio keys as they are (a deliberate worst
case). Record `video-profile-starvation-cap` on both units; if it is set, delete it and say so.

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

`allow-external-configuration` is needed for `ACTION_EXPORT_LOG`. `ACTION_LOG_MARKER` is not gated on this build.

**D-POCO, Stage B** (with the fixed `set:` writer, `rig-quirks/topics/tooling.md`):

```
int:wifi-connection-mode=0 int:log-level=2 int:onboarding-version=2 bool:allow-external-configuration=true
bool:auto-start-on-usb=false bool:auto-connect-single-usb=false bool:auto-connect-last-session=false
bool:reopen-on-reconnection=false set:connection-modes=usb
```

Plus `bool:use-libusb=true` for U1 and `bool:use-libusb=false` for U2.

## 5. Helpers

Run every adb call in sequence, never two at once against one unit. Write the results file after every run.

**lib.sh**

```bash
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
HU=27870808938846; PH=4f4027e9; MOTO=ZY22GC3BM4
OUT=rig-data/rounds/emotionbug-batch-round1; mkdir -p $OUT
export LC_ALL=C
# D = the unit the app under test runs on (D-HU in Stage A, $POCO_W in Stage B)
send() { a=$1; shift; adb -s $D shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; }
mark() { send ACTION_LOG_MARKER --es text "$1" >/dev/null; sleep 0.3; echo "$1 $(date '+%m-%d %H:%M:%S')" >> $OUT/marks.txt; }
launch() { adb -s $D shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity >/dev/null; }
appexit() { adb -s $1 shell am start -a android.intent.action.VIEW -d "headunit://exit" >/dev/null; sleep 5
  adb -s $1 shell am force-stop $PKG; sleep 1; }
# wait for a fixed string in a capture, capped
waitline() { local f=$1 s=$2 cap=$3 t=0; while [ $t -lt $cap ]; do grep -aqF -- "$s" $f && return 0; sleep 1; t=$((t+1)); done; return 1; }

# host thermal (rig-quirks/topics/tooling.md)
th_gate() { $RIG_TOOLKIT/rig_thermal.sh wait 75 || $RIG_TOOLKIT/rig_thermal.sh wait 75 || { echo HOST_HOT; return 1; }
  $RIG_TOOLKIT/rig_thermal.sh watch $OUT/thermal-$RUN.log & THPID=$!; }
th_end() { kill $THPID 2>/dev/null; wait $THPID 2>/dev/null; }

# phone radios
ph_bt()  { adb -s $1 shell svc bluetooth $2; sleep 4; adb -s $1 shell dumpsys bluetooth_manager | grep -m1 -E "^ *state:"; }
ph_off() { adb -s $PH shell svc wifi disable; sleep 0.3; ph_bt $PH disable; adb -s $PH shell dumpsys wifi | grep -m1 "Wi-Fi is"; }
ph_on()  { adb -s $PH shell svc wifi enable; sleep 0.3; ph_bt $PH enable; adb -s $PH shell dumpsys wifi | grep -m1 "Wi-Fi is"; }
ph_nav() { adb -s $PH shell am start -a android.intent.action.VIEW -p com.google.android.apps.maps \
  -d "google.navigation:q=Plaza+de+Bolivar+Bogota\&mode=d" >/dev/null; }

# media title on the app's MediaSession (head unit) and on the player (phone)
hu_title() { adb -s $D shell dumpsys media_session | grep -a -A25 "package=$PKG" | grep -a -m1 -o 'description=[^,]*' | tr -d '\r'; }
ph_title() { adb -s $PH shell dumpsys media_session | grep -a -m1 -o 'description=[^,]*' | tr -d '\r'; }
# nav and media notifications posted by the app
hu_notes() { adb -s $D shell dumpsys notification --noredact | grep -a -c "$1"; }
```

`ph_title` reads the first active session on D-POCO. If D-POCO shows more than one active session, record
which package `ph_title` read in Setup notes, and grep for that package the way `hu_title` does.

## 6. The deciding lines

All verified with `grep -F -r` in `app/src/main` at `14ad25b8`. Levels in brackets. Grep every capture with `-a`.

| Line | Level | PR | Where |
|---|---|---|---|
| `NativeAA: ACTIVELY LISTENING on Android Auto UUID` | INFO | BRIDGE | D-HU |
| `NativeAA: [TX] Sending WifiStartRequest (Type 1)` | INFO | BRIDGE | D-HU |
| `WirelessServer: Incoming connection detected` | INFO | BRIDGE | D-HU |
| `Handshake: Version response ` (then `X.Y, requested 1.2, status STATUS_...`) | INFO | FRAMING | both head units |
| `SSL handshake complete` | INFO | FRAMING | both head units |
| `Media Sink Setup Request` | INFO | session | both head units |
| `Throughput over` (`rendered=N`) | INFO | session | both head units |
| `AapTransport: transport dispatch over` (`longest=Nms on <CHANNEL>`) | INFO | PRESENT | D-HU |
| `Nav: NavigationState steps=` | DEBUG | PRESENT | D-HU |
| `LibusbAccessoryConnection: Successfully connected via JNI Libusb` | INFO | LIBUSB | D-POCO |
| `Established connection: ` | INFO | standard USB | D-POCO |

FAIL lines. Any one of these in a run's window is a FAIL for the label named:

| Line | Level | PR |
|---|---|---|
| `Handshake: rejected VERSION_RESPONSE` | ERROR | FRAMING |
| `Invalid cleartext handshake header` | in `Handshake failed with exception` (ERROR) | FRAMING |
| `SSL Handshake: expected channel 0 SSL_HANDSHAKE message` | ERROR | FRAMING |
| `AA presentation update failed` | ERROR | PRESENT |
| `vendor Bluetooth bridge candidate detected` | INFO | BRIDGE |
| `[ZBT candidate]` | INFO | BRIDGE |
| `NativeAA: external Bluetooth module detected (` | WARN/ERROR | BRIDGE |
| `libusb_bulk_transfer read failed` (native `LOGE`, not `AppLog`) | ERROR | LIBUSB |

The log export header carries `bt=<route>`. On an ordinary unit it MUST read `bt=internal`. `bt=zbt-candidate:`
or `bt=module:` is a FAIL for BRIDGE.

Note for readers of older captures: FRAMING replaces the INFO line `the phone selected X.Y (we asked for 1.2)` on
the handshake path with `Handshake: Version response X.Y, requested 1.2, status ...`. Grade on the new line.

## 7. Pre-flight (8 min)

**preflight.sh**

```bash
. ./lib.sh
# D-HU: no vendor bridge markers (BRIDGE). All four MUST be "No such file".
adb -s $HU shell ls /system/bin/fscsdkbw123 /system/bin/gocsdk_zj /dev/rf_serial /dev/zj_bt_serial 2>&1 | tee $OUT/pre-markers.txt
adb -s $HU shell getprop rw.zlink.bt.type; adb -s $HU shell getprop rw.zj.bt.type     # both MUST be empty
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

If any marker file exists on D-HU, or a prop is set, BRIDGE is INCONCLUSIVE on this unit: say so and go on.

## 8. Runs

Each run follows `TESTING-TEMPLATE.md` §4 (clean-run protocol) for its first session. Stop rule for every
run: a run that reaches no `SSL handshake complete` within 120 s of its trigger ends there, with its verdict
graded from what it has. Do not retry a run more than once.

### Stage A: W1 and W2, one capture (20 min). D = D-HU.

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
waitline $OUT/A-hu.txt "SSL handshake complete" 120 || echo "W1 NO_SESSION" >> $OUT/marks.txt
waitline $OUT/A-hu.txt "Throughput over" 30
mark W1-session
```

**W1, the point of the round (14 min).** Run these in order, after `W1-session`:

1. **Five track changes, 20 s apart (PRESENT).** Repeat 5 times:
   `adb -s $HU shell input keyevent KEYCODE_MEDIA_NEXT; mark W1-skip$i; sleep 3; echo "skip$i hu=[$(hu_title)] ph=[$(ph_title)]" >> $OUT/W1-titles.txt; sleep 17`.
2. **A navigation route (PRESENT).** `ph_nav; mark W1-nav; sleep 60; echo "nav=$(hu_notes headunit_navigation)" >> $OUT/W1-notes.txt`.
   Then `sleep 120; mark W1-nav-end`.
3. **Log export (BRIDGE route token).** `send ACTION_EXPORT_LOG`; pull the newest `HUR_Log_*.txt` it names into
   `$OUT/W1-export.txt` and run `grep -a -m1 -o 'bt=[^ |]*' $OUT/W1-export.txt`.
4. `mark W1-end`.

W1 PASS needs every one of these, graded in the `W1-start` to `W1-end` window of `A-hu.txt` unless named:

- `ACTIVELY LISTENING on Android Auto UUID` ≥ 1, `Sending WifiStartRequest (Type 1)` ≥ 1,
  `Incoming connection detected` ≥ 1, `SSL handshake complete` ≥ 1 (BRIDGE, FRAMING).
- Exactly one `Handshake: Version response ` line in the session, and it ends `status STATUS_SUCCESS` (FRAMING).
  Record its `X.Y`.
- `Media Sink Setup Request` ≥ 1 and the last `Throughput over` before `W1-end` has `rendered=` > 0.
- In `W1-titles.txt`, `hu=` equals `ph=` on at least 4 of the 5 lines, and the 5 `hu=` values contain at
  least 4 different titles (PRESENT). A line where `ph=` is empty does not count either way; record it.
- `nav=` in `W1-notes.txt` is at least 1, and `Nav: NavigationState steps=` ≥ 1 after `W1-nav` (PRESENT).
- No `transport dispatch over` line in the window has `longest=` ≥ 50 ms `on MUSIC_PLAYBACK` or
  `on NAVIGATION_DIRECTIONS` (PRESENT). Record the largest `longest=` on each of those two channels.
- 0 lines from the FAIL table for FRAMING, PRESENT, BRIDGE.
- The export reads `bt=internal` (BRIDGE).
- Phone capture `A-phone.txt`: between `W1-start` and `W1-end` (by `marks.txt` wall clock), the phone's
  Gearhead logs no `Critical error` and no `ByeBye`; record the count of each (`grep -ac`).

Grade each bullet against its label in the results, so one bullet's FAIL does not hide the others.

**W2, exit and reconnect (5 min).** Same capture. Continue after `W1-end`:

**W2.sh**

```bash
. ./lib.sh; D=$HU
mark W2-start
send ACTION_DISCONNECT
sleep 8; echo "after-exit nav=$(hu_notes headunit_navigation) media=$(hu_title)" >> $OUT/W2-notes.txt
mark W2-rearm
send ACTION_START_WIRELESS_SCAN
waitline <(sed -n '/W2-rearm/,$p' $OUT/A-hu.txt) "SSL handshake complete" 120   # or re-grep the file after the marker
sleep 15; adb -s $HU shell input keyevent KEYCODE_MEDIA_NEXT; mark W2-skip; sleep 4
echo "skip hu=[$(hu_title)] ph=[$(ph_title)]" >> $OUT/W2-notes.txt
mark W2-end
kill $HUPID $PHPID; th_end
appexit $HU
```

If `waitline` with process substitution is not supported by the shell, poll
`sed -n '/W2-rearm/,$p' $OUT/A-hu.txt | grep -acF 'SSL handshake complete'` once a second for 120 s.

W2 PASS needs all of:

- `after-exit nav=0` in `W2-notes.txt` (the nav notification is gone 8 s after the exit) (PRESENT).
- After `W2-rearm`: exactly one `Handshake: Version response ` ending `status STATUS_SUCCESS`, and
  `SSL handshake complete` ≥ 1 (FRAMING).
- The `skip` line has `hu=` equal to `ph=`, and `hu=` differs from the last W1 title (PRESENT: no stale title
  from the old session).
- 0 FAIL-table lines between `W2-start` and `W2-end`.

Record `after-exit media=` as found. The PR clears the media notification on disconnect, but the
MediaSession may keep its last metadata; that reading is information, not a condition.

### Stage B: U1 and U2, one capture (18 min). D = $POCO_W.

Set-up: D-HU app stopped (`appexit $HU`, done at the end of W2). Move D-POCO to wireless adb
(`adb connect $POCO_W`) and free its USB port. `adb connect $MOTO_W`. D-MOTO is unplugged. Write D-POCO's
Stage B keys with `use-libusb=true` (section 4) and read them back. Read `adb -s $POCO_W shell dumpsys usb`
for grants, and quote it in Setup notes.

**stageB.sh**

```bash
. ./lib.sh; D=$POCO_W; RUN=B; th_gate || exit 1
adb -s $D shell am force-stop $PKG
adb -s $D logcat -G 16M; adb -s $D logcat -c
stdbuf -oL adb -s $D logcat -v time > $OUT/B-hu.txt & HUPID=$!
adb -s $MOTO_W logcat -c
stdbuf -oL adb -s $MOTO_W logcat -v time > $OUT/B-phone.txt & PHPID=$!
launch; sleep 5; mark U1-start
```

**U1, libusb, the second point of the round (10 min):**

1. **HS1:** plug D-MOTO into D-POCO's OTG port. `mark U1-plug`.
2. **HS4, if shown:** a system "Allow Open Headunit to access the USB device?" dialog on D-POCO. Press OK. Do not
   tick "always". Count each one in `hand-steps.log` with the VID:PID it names.
3. `send ACTION_CHECK_USB`.
4. `waitline $OUT/B-hu.txt "SSL handshake complete" 120`, then `sleep 120; mark U1-first-end`.
5. **HS2:** unplug D-MOTO. `sleep 10`. **HS3:** plug it back in. `mark U1-replug`. Repeat steps 2 and 3.
6. Wait for a second `SSL handshake complete` (cap 120 s), then `sleep 60; mark U1-end`.

U1 PASS needs all of, in `B-hu.txt` between `U1-start` and `U1-end`:

- `Successfully connected via JNI Libusb` = 2 and `SSL handshake complete` = 2 (one per plug) (LIBUSB, FRAMING).
- 2 `Handshake: Version response ` lines, each ending `status STATUS_SUCCESS`. Record `X.Y` for D-MOTO (FRAMING).
- `Media Sink Setup Request` ≥ 2, and a `Throughput over` with `rendered=` > 0 after each handshake.
- 0 FAIL-table lines for FRAMING and LIBUSB.
- Phone capture `B-phone.txt`: 0 `Critical error` lines in the window.

**U2, standard USB (6 min).** Keep the same capture running. Unplug D-MOTO (**HS2**), `mark U2-switch`, then:

```bash
adb -s $D shell am force-stop $PKG
# write bool:use-libusb=false on D-POCO, read it back
launch; sleep 5; mark U2-start
```

Plug D-MOTO in (**HS1**), handle **HS4** if shown, `send ACTION_CHECK_USB`, wait for `SSL handshake complete`
(cap 120 s), `sleep 90; mark U2-end`. Then unplug, `kill $HUPID $PHPID; th_end`, `appexit $D`.

U2 PASS needs all of, between `U2-start` and `U2-end`:

- `Established connection: ` ≥ 1 and `Successfully connected via JNI Libusb` = 0 (the setting took).
- `SSL handshake complete` = 1, and one `Handshake: Version response ` ending `status STATUS_SUCCESS` (FRAMING).
- `Media Sink Setup Request` ≥ 1 and a `Throughput over` with `rendered=` > 0.
- 0 FAIL-table lines for FRAMING.

U1 FAIL with U2 PASS points at LIBUSB or at libusb itself; U1 and U2 both FAIL on a handshake line points at FRAMING.

After Stage B: restore D-POCO's `settings.xml` from its backup, `adb -s $PH usb` when D-POCO is back on its PC
cable, and the same for D-MOTO.

## 9. Do not re-run

Nothing from earlier threads. Do not add a Headunit Server (mode 1) run: W1 already covers FRAMING's socket path.

## 10. Report back

1. The version status each phone sent, D-POCO wireless and D-MOTO USB, and whether any handshake was refused
   (FRAMING). This decides whether FRAMING's "any nonzero status is fatal" rule is safe on our phones.
2. W1's title matches out of 5, the nav notification count, and the largest `longest=` on `MUSIC_PLAYBACK` and
   `NAVIGATION_DIRECTIONS` (PRESENT).
3. U1's two libusb sessions: both formed or not (LIBUSB).
4. The `bt=` token and the marker check (BRIDGE).

One verdict per label in a table at the top of the results file, beside the per-run verdicts.
