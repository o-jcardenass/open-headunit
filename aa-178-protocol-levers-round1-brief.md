# aa-178-protocol-levers, round 1 brief

Published name: `aa-178-protocol-levers-round1-brief.md`. Results go in
`aa-178-protocol-levers-round1-results.md`. Evidence: create the release
`rig-evidence-aa-178-protocol-levers` with the asset `aa-178-protocol-levers-round1-captures.zip`.

Estimated time: 20 min build and gate, with the pre-flight run during the build, 7 min R2, 6 min R1
with R4, 10 min R3, 5 min closing. Total about 48 min. Units: D-HU (head unit), D-POCO (phone), D-MOTO (Bluetooth off only).

## 1. Build and baseline

- **Candidate:** `feat/aa-178-protocol-levers` at **`dd5b2a82`**
  (`dd5b2a823df9b25c7ec1fae3cab4c13a0987ae4e`), tree `ca8a5828f34f22f2e9b7e9c85b369abad1252d82`.
  Four commits on `main` `ec9d9c33`: `0e2e5767` Proto, `c0adfbc6` AAP, `6ca79463` Native AA, `dd5b2a82` Video.
- **History:** this is round 1. The fixups were folded before the round, and the tree is byte-identical
  to the unfolded tip that the 3120 tests ran on. Build this SHA exactly.
- **Baseline:** none built. `main` `ec9d9c33` is the build that `main-beta5-regression` round 1 measured.

```bash
git ls-remote fork feat/aa-178-protocol-levers   # MUST print dd5b2a823df9b25c7ec1fae3cab4c13a0987ae4e
git fetch fork feat/aa-178-protocol-levers
git worktree add ../ohu-wt-aa178-dd5b2a82 dd5b2a823df9b25c7ec1fae3cab4c13a0987ae4e
git -C ../ohu-wt-aa178-dd5b2a82 rev-parse 'HEAD^{tree}'   # MUST print ca8a5828f34f22f2e9b7e9c85b369abad1252d82
cp <main checkout>/local.properties ../ohu-wt-aa178-dd5b2a82/   # a new worktree has none
```

If `ls-remote` prints nothing or another SHA, stop the round and say so.

Start the build and the unit tests in the background, then run the pre-flight (section 7) while they run.
The pre-flight needs no APK.

```bash
( HUR_DIR=../ohu-wt-aa178-dd5b2a82 rig-toolkit/build_hur_cool.sh && \
  HUR_DIR=../ohu-wt-aa178-dd5b2a82 rig-toolkit/run_unit_tests.sh ) > $OUT/build.log 2>&1 &
BUILD_PID=$!
# ... pre-flight P1 to P7 ...
wait $BUILD_PID; echo "build exit=$?"
```

Pass the worktree to both scripts the way they take it today; the line above shows the order, not new
flags. Copy the APK out of `apks/` at once. Expect **3120** tests, 0 failures. A build or test failure
stops the round.

Install with `adb -s 27870808938846 install -r <apk>` on **D-HU only**. Identity gate, all MUST hold:

```bash
send ACTION_QUERY_STATE     # data= line MUST carry commit dd5b2a82 (a "-dirty" suffix is fine)
unzip -p <pulled apk> 'classes*.dex' | strings | grep -cF WppChannelListPolicy   # MUST be >= 1
unzip -p <pulled apk> 'classes*.dex' | strings | grep -cF VideoFallbackPolicy    # MUST be >= 1
```

`main` carries neither class.

## 2. What this is and why

Android Auto 17.8 was read in full from its decompiled code. Three findings changed this branch.
Decompiles: 17.8 at `~/projects/ohu-project/ohu-fixes-handoff/gearhead-17.8.163744-release-daily/`
and 17.5 at `~/projects/ohu-project/ohu-fixes-handoff/gearhead-17.5.663204-release/` (`jadx-out/` for
Java, `smali/` to confirm a negative).

1. **The phone refuses 1080p and above on a 2.4 GHz wireless link.** `ivq.C` (17.8) refuses
   `1920x1080`, `2560x1440`, `3840x2160` and the portrait forms when the link frequency is 1 to 2500 MHz.
   It logs `VideoCodecResolutionType %s (%d) is not allowed due wireless frequency.`, then walks our
   `Config.configuration_indices` in order. We announced one configuration and one index, so a 1080p
   head unit on a 2.4 GHz link got `No working configuration` and no picture.
   **The fix (commit `dd5b2a82`):** above 720p, the service discovery adds a second video
   configuration, `_1280x720` (or `_720x1280`), with margins scaled from the first. The video config
   response lists indices 0 and 1. When the phone's `Media.Start` names index 1, the app adopts the
   fallback size and margins for that session. `TouchConfig` stays at the first size, because the
   phone reads only its type. Whether the phone scales touch by it is open, and R4 decides it.
2. **The WPP status -8 came from an empty channel list, and status 0 starts the phone's ping.**
   The phone checks `WifiVersionRequest` field 4 against its 5 GHz scan channels. We sent no list, so
   every phone answered `NO_SUPPORTED_WIFI_CHANNELS(-8)`. Only status 0 starts the phone's 1 Hz WPP
   ping on the held RFCOMM channel, which is why every earlier hold logged `0 pings answered`.
   **The fix (commit `6ca79463`):** field 4 now carries the group's own frequency. A 5 GHz group can
   pass. A 2.4 GHz group still gets -8 by design, because no honest value passes. A phone-sent
   `WifiStartRequest` (type 1) is logged and ignored.
3. **The phone never parses `ConnectionConfiguration`** (service discovery field 16). **The fix
   (commit `c0adfbc6`):** the "Ask for a longer link timeout" setting and its wiring are gone. The
   stored key `announce-connection-configuration` becomes an orphan and does nothing.

Commit `0e2e5767` renames proto fields only and puts nothing new on the wire.

## 3. What is different about this round

- **R1 is the point of the round.** It needs a 2.4 GHz group, 1080p and the app's own 2.4 GHz cap off,
  or our own cap announces 720p and no fallback is offered.
- **R1 grades nothing if the phone never logs the refusal.** Then the phone did not read its link as
  2.4 GHz, and R1 is INCONCLUSIVE, not PASS. R4 inherits that verdict.
- **R2 is the positive control and runs first,** on a 5 GHz group with the same build: index 0, WPP
  status 0, pings answered.
- **R4's taps go into the projected video**, the standing exception in template §3. The projected UI
  is video, so `uiautomator dump` cannot resolve a target in it. The taps use fixed coordinates in the
  bottom bar's rows, and the grade compares coordinates, not which app opened.
- **R5 needs no settings screen.** The row cannot render without its string, so R5 reads the APK's
  string table and seeds the orphan key in R2.
- **R1 takes D-HU off its station network.** A group forced to 2.4 GHz beside the 5500 MHz station makes
  the default `StationStandDownMode.AUTO` stand the station down (pre-flight question Q2). The exit
  restores it. Record `StationStandDown: ` lines and read the station back after R1.
- **R3 changes two station links.** D-HU hosts a 2.4 GHz SoftAP and D-POCO joins it by adb (pre-flight
  question Q3). R3 runs last.
- **Log level is VERBOSE (`0`) in every run,** because `[UI_DEBUG] Touch map:` is `AppLog.v` behind
  `LOG_VERBOSE`. At VERBOSE, `SSL handshake complete` prints twice per session (the DEBUG
  `Handshake:` form joins the INFO form). The helper `sslc` counts the INFO form only.
- **`Service Discovery Response` is not an INFO line** (`main-beta5-regression` round 1). Use
  `Service Discovery Request: `.
- **Stat D-HU's `shared_prefs/` first** (`rig-quirks/units/D-HU.md`). Record the owner.
- **Do not force-stop Gearhead and do not probe `:5277`** with `nc`. Read `/proc/net/tcp6` instead.

Read `rig-quirks/topics/tooling.md`, `units/D-HU.md`, `units/D-POCO.md`, `units/D-MOTO.md`,
`topics/wifi.md`, `topics/bt.md`, `topics/gearhead.md` and `topics/projection.md` before the round.

## 4. Settings keys

Back up D-HU's `settings.xml` at the round start and diff it against the last round's backup. State
the delta in Setup notes. **Leave the rig's audio keys as they are** (a deliberate worst case). Leave
`video-codec`, `fps-limit`, `video-fit-mode`, `fullscreen-mode`, `native-poke-bt-macs` and
`native-driver-selection-mode` as found, and record each. `ACTION_LOG_MARKER` is not gated on this
branch (`AutomationCommandPolicy.CONFIGURING` does not list it).

**D-HU baseline** (write once, app stopped, then each run writes only what it names):

| Key | Type | Value | Why |
|---|---|---|---|
| `wifi-connection-mode` | int | `3` | Native AA (R3 writes `0`) |
| `native-ap-transport` | int | `0` | WiFi Direct, never the hotspot transport |
| `resolutionId` | int | `3` | 1080p, so a fallback exists. D-HU's 1440x720 panel has a 1080p hard ceiling |
| `narrow-band-profile-cap` | boolean | `false` | "Lower video on a 2.4 GHz link" off |
| `video-profile-starvation-cap` | boolean | delete | clears the starvation cap from earlier fumbles |
| `native-aa-wake-damage-verdict` | int | `0` | a latched verdict stands the poke down |
| `native-poke-all-paired` | boolean | `false` | poke only the wake list (D-POCO) |
| `announce-connection-configuration` | boolean | `true` | R5: the orphan key, seeded on |
| `log-level` | int | `0` | VERBOSE, for `Touch map` |
| `onboarding-version` | int | `2` | no wizard over a launch |

Per run: `wifi-direct-band` = `1` (R2, 5 GHz only) or `2` (R1, 2.4 GHz only). Read the file back
before every launch. `native-poke-bt-macs` MUST hold D-POCO's address: compare it with the `POCO X3 NFC`
entry in D-HU's bonded list. If it does not, stop at pre-flight and say so.

## 5. Helpers

```bash
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
HU=27870808938846; PH=4f4027e9; MOTO=ZY22GC3BM4
OUT=rig-data/rounds/aa-178-protocol-levers-round1; mkdir -p $OUT
send() { a=$1; shift; adb -s $HU shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; }
mark() { send ACTION_LOG_MARKER --es text "$1"; }
huexit() { adb -s $HU shell am start -a android.intent.action.VIEW -d "headunit://exit"; sleep 5; }
phnow() { adb -s $PH shell "date '+%m-%d %H:%M:%S.000'" | tr -d '\r'; }
launch() { PHT=$(phnow); echo "PHT=$PHT"; sleep 0.3
  adb -s $HU shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity; }

# captures start before the launch; the phone stream keeps the phone's buffer
cap_on() { adb -s $HU logcat -c; adb -s $PH logcat -G 16M
  stdbuf -oL adb -s $HU logcat -v threadtime > $OUT/$1-hu.txt & HUPID=$!
  stdbuf -oL adb -s $PH logcat -v threadtime -T 1 -b main,system,crash,events > $OUT/$1-phone.txt & PHPID=$!; sleep 2; }
cap_off() { sleep 2; kill $HUPID $PHPID 2>/dev/null; }
waitfor() { local n=0; while [ $n -lt $3 ]; do grep -aqF "$2" "$1" && { echo $n; return 0; }; sleep 5; n=$((n+5)); done; echo TIMEOUT; return 1; }

# head unit greps: capture start to "<id>-end"; each capture holds one launch
upto() { awk -v e="AutomationMarker: $2-end" '{print} index($0,e){exit}' "$1"; }
cnt()  { upto "$1" "$2" | grep -acF -- "$3"; }                     # cnt FILE ID STRING
line() { upto "$1" "$2" | grep -aF -- "$3" | head -1; }            # first matching line
last() { upto "$1" "$2" | grep -aF -- "$3" | tail -1; }            # last matching line
sslc() { upto "$1" "$2" | grep -aF 'SSL handshake complete' | grep -avcF 'Handshake: SSL handshake complete'; }
# lines between two markers (head unit clock)
hubtw() { awk -v a="AutomationMarker: $2" -v b="AutomationMarker: $3" 'index($0,a){f=1;next} index($0,b){exit} f' "$1"; }

# phone greps: phone clock, threadtime lines start "MM-DD HH:MM:SS.mmm"
ph_after() { awk -v t="$2" 'substr($0,1,18) >= t' "$1"; }
ph_btw()   { awk -v t="$2" -v u="$3" 'substr($0,1,18) >= t && substr($0,1,18) < u' "$1"; }
pcnt() { ph_after "$1" "$2" | grep -acF -- "$3"; }                  # pcnt FILE PHT STRING

ph_up() { adb -s $PH shell cmd connectivity airplane-mode disable; sleep 2
  adb -s $PH shell svc wifi enable; sleep 0.3; adb -s $PH shell svc bluetooth enable; sleep 5
  adb -s $PH shell dumpsys bluetooth_manager | grep -m1 -E "^ *state:"; adb -s $PH shell dumpsys wifi | grep -m1 "Wi-Fi is"; }
ph_down() { adb -s $PH shell cmd connectivity airplane-mode enable; sleep 5; }
ph_link() { adb -s $PH shell dumpsys wifi | grep -m1 -a "mWifiInfo"; sleep 0.3
  adb -s $PH shell dumpsys wifip2p | grep -a -iE "frequency|isGroupOwner|groupFormed" | head -4; }

# one projected-video tap, stamped on both clocks: tap ID X Y; tap R1-t1 sets $PT_R1_t1
tap() { mark "$1"; sleep 0.3; eval "PT_${1//-/_}='$(phnow)'"; sleep 0.3
  adb -s $HU shell input tap $2 $3; sleep 4; }
```

Write settings with the `hu_kv` helper from `forget-car-every-connection-round2-brief.md` §5 (pushed
script, run with `sh` as root, then `chown` and `chmod 660`). Copy `th_gate`, `th_watch` and
`th_report` from `archive/rounds/projection-teardown-and-relays-round1-brief.md` (`ptr_lib.sh`) and call
`RUN=<id> th_gate` before every run. Never run two adb commands against one unit at the same time.

## 6. The deciding lines

Each head unit string was checked with `grep -F -r` on `app/src` at `dd5b2a82`, and each phone string
in `jadx-out/` of both 17.8 and 17.5. Several lines are built at run time. The runs grep the run-time
text shown here. The `decisive-strings` block at the end lists the verbatim source fragments of the
head unit lines, and the `phone-strings` block lists the Gearhead fragments.

**Head unit capture** (`$OUT/<id>-hu.txt`, `upto` the end marker unless a run says otherwise):

| Line (as it prints) | Level | What it means |
|---|---|---|
| `WifiLauncher: Initializing WiFi Mode: NATIVE` (`MANUAL` in R3) | I | path under test |
| `WifiDirectManager: onGroupInfoAvailable: SSID: ... Freq: <N> MHz (5GHz` or `(2.4GHz` | I | the group's band |
| `[RES_CAP] resolutionId=3 ... capped=_1920x1080 ... linkCapped=none` | I | 1080p announced, no link cap |
| `[ServiceDiscovery] NegotiatedResolution is: 1920x1080` | I | the primary configuration |
| `[ServiceDiscovery] Offering a _1280x720 fallback, margins <W>x<H>` | I | the fallback went out |
| `NativeAA: [TX] version request channel type=<type> channels=[<MHz>]` | I | field 4 on the wire; `channels=[]` means frequency unreadable |
| `NativeAA: [RX] WifiVersionResponse v<M>.<m> status=SUCCESS(0)` or `status=NO_SUPPORTED_WIFI_CHANNELS(-8)` | I | the phone's channel verdict |
| `NativeAA: [RX] WifiStartRequest from the phone, ignored on purpose` | I | recorded only |
| `Media Sink Setup Request: <n> on channel VIDEO` | I | the codec the phone sends |
| `configuration_indices: 1` | I | inside the multi-line `Config response:`; the fallback index was offered |
| `Media Start Request VIDEO: session=<n>, config_index=<0 or 1>` | I | **the phone's pick** |
| `HeadUnitScreenConfig: Video: the phone chose the _1280x720 fallback (index 1), margins <W>x<H>` | I | the app adopted it |
| `Throughput over <ms>ms: rendered=<N>` | I | frames on screen |
| `NativeAA: [HOLD] Bluetooth channel held <s>s, <N> pings answered.` | I | every 60 s while held |
| `NativeAA: the session ended; releasing the held Bluetooth channel after ` | I | hold ended with the session |
| `NativeAA: the phone closed the held Bluetooth channel after ` | W | the phone dropped the hold |
| `[UI_DEBUG] Touch map: raw=<x>,<y> -> video=<X>,<Y> view=<w>x<h> video=<vw>x<vh> margin=<mw>x<mh>` | V | R4: where a tap went |
| `Service Discovery Request: ` | I | the session reached discovery |
| `Handshake: Version response received: the phone selected ` | I | AAP version, recorded only |
| `SSL handshake complete` (helper `sslc`, INFO form only) | I | session formed |
| `StationStandDown: ` | I/W | recorded only |
| `Standard createGroup SUCCESS!` (grep `createGroup SUCCESS`) | I | discard if 2 in one run |
| `MATCH! Starting AapService`, `Magic Garbage detected in header` | I/W | discard rules (template §4) |
| `AutomationReceiver: ` / `AutomationMarker: ` | I/W | a verb or marker landed; a missing line voids the step |

**Phone capture** (`$OUT/<id>-phone.txt`, only lines at or after `$PHT`). Record the Gearhead
version first: `adb -s $PH shell dumpsys package com.google.android.projection.gearhead | grep versionName`.
Read 17.8 strings against the 17.8 decompile and 17.5 strings against 17.5. A 0 on another version can
mean a renamed string: say so.

| Line (as it prints) | What it means |
|---|---|
| `VideoCodecResolutionType ... is not allowed due wireless frequency.` | **the refusal** (the R1 and R3 gate) |
| `Checking video config at index <i> codec:<c> fps:<f>. isAllowed: <bool>` | the phone's walk over our list |
| `No working configuration` | no index was allowed; no picture |
| `WiFi channels not supported: [...]` | the phone's side of -8 |
| `State changed to FOUND_COMPATIBLE_WIFI_NETWORK` | the phone's side of status 0 |
| `has not received the ping response for last: ` | the phone is pinging |
| `has timed out on pings` | 10 pings unanswered; the phone stops |
| `Retrying connection attempt on all channels` | a WPP restart |
| `GhFacetBar injectMotionEvent(event:` ... `action=ACTION_DOWN` ... `x[0]=<x>, y[0]=<y>` | R4: where the phone put a tap in its bottom bar |
| `injectMotionEvent(event:` | R4: any window's touch (window name precedes it) |

## 7. Runs

### R0. Build gate and R5 part 1 (20 min, no phone)

Section 1, then the string table of the pulled APK (`AAPT2=$(ls $ANDROID_HOME/build-tools/*/aapt2 | tail -1)`):

```bash
$AAPT2 dump strings <pulled apk> | grep -cF 'Ask for a longer link timeout'   # R5a: MUST be 0
$AAPT2 dump strings <pulled apk> | grep -cF 'Lower video on a 2.4 GHz link'   # control: MUST be >= 1
```

The control proves the dump reads the table. On `main` the first count is 1.

### Pre-flight (one batch, during the R0 build, before the install)

- **P1, read only.** `rig-toolkit/rig_preflight.sh D_HU:wifi,bt D_POCO:wifi,bt D_MOTO:bt`. D-POCO on USB,
  unlocked (`wm dismiss-keyguard`) and at home (`dumpsys window | grep mCurrentFocus`; fix with
  `input keyevent KEYCODE_HOME`). D-POCO `dumpsys wifip2p | grep isGroupOwner` MUST be false (else
  `svc wifi disable`, `svc wifi enable`).
- **P2.** D-HU: `adb -s $HU shell 'stat -c "%U:%G %a" /data/data/com.andrerinas.headunitrevived/shared_prefs'`.
  Record it. If the owner is not the app's uid, `chown` it and record both reads.
- **P3, read only.** `adb -s $PH shell dumpsys bluetooth_manager | grep -a -A12 "Bonded devices"` and the
  same on D-HU. D-POCO MUST be bonded to `Navegadortz2` and not to `Navegadortz3`. D-HU MUST list
  `POCO X3 NFC`. Record D-HU's `native-poke-bt-macs`.
- **P4, read only.** D-HU station: `adb -s $HU shell dumpsys wifi | grep -m1 -a mWifiInfo`. Record the
  SSID and frequency (expected `Pegue Cdesta`, 5500 MHz). D-POCO: `ph_link`; record.
- **P5, read only.** D-POCO head unit server for R3:
  `adb -s $PH shell cat /proc/net/tcp6 /proc/net/tcp | grep -ci ':149D'`. Record the count. Never `nc`.
- **P6.** D-MOTO: `adb -s $MOTO shell svc bluetooth disable`, then confirm with `dumpsys bluetooth_manager`.
  It stays off until the closing step.
- **P7, one message to the operator**, only for what P1 to P6 found wrong: a missing or extra bond, a
  `:149D` count of 0 (toggle "Start head unit server" in Android Auto developer settings), or D-MOTO
  not reachable by adb (switch its Bluetooth off by hand). If nothing is wrong, send nothing.

Then wait for the build, install, and run the identity gate and R5a. Only then back up D-HU's
`settings.xml`, write the baseline (section 4), and read it back.

### R2. Positive control on a 5 GHz group, with R5 part 2 and the R4 control taps (7 min)

Setup, app stopped: `hu_kv "set int wifi-direct-band 1"`. Read back. D-HU `dumpsys wifip2p | grep groupFormed`
MUST read false (else `huexit`, then `adb -s $HU shell cmd wifip2p remove-group`, then read again).

1. `ph_down`. `adb -s $HU shell am force-stop $PKG`. `RUN=R2 th_gate`.
2. `cap_on R2`, `launch`, `sleep 3`, `mark R2-start`, `sleep 15`, `ph_up`.
3. `waitfor $OUT/R2-hu.txt "SSL handshake complete" 150`. On TIMEOUT: `mark R2-end`, `cap_off`, `huexit`,
   and repeat steps 1 to 3 once as `R2b`. A second TIMEOUT is a FAIL; go to R1.
4. `ph_link > $OUT/R2-link.txt`. `sleep 120` (the hold under test; the phone gives up after 10 s of unanswered pings).
5. `tap R2-t1 59 665`, `tap R2-t2 400 665`, `tap R2-t3 652 665`. Then `PT_R2_end=$(phnow)`.
6. `mark R2-end`, `huexit`, `cap_off`. Read D-HU's station back (P4 command) and record it.

Pre-check (a failure is a setup failure: fix it and re-run once, never grade):
`cnt F R2 "Initializing WiFi Mode: NATIVE"` >= 1; the `onGroupInfoAvailable:` line reads `(5GHz`;
`line F R2 "[RES_CAP]"` carries `capped=_1920x1080` and `linkCapped=none`; `cnt F R2 "createGroup SUCCESS"` = 1.

PASS, every condition (`F=$OUT/R2-hu.txt`, `P=$OUT/R2-phone.txt`):

1. `line F R2 "NativeAA: [TX] version request channel type="` ends `channels=[<f>]`, where `<f>` is the
   `Freq:` of the `onGroupInfoAvailable:` line.
2. `line F R2 "NativeAA: [RX] WifiVersionResponse"` carries `status=SUCCESS(0)`.
3. `cnt F R2 "[ServiceDiscovery] Offering a _1280x720 fallback"` = 1 and
   `cnt F R2 "configuration_indices: 1"` >= 1.
4. `line F R2 "Media Start Request VIDEO: session="` carries `config_index=0`, and
   `cnt F R2 "the phone chose the"` = 0.
5. `last F R2 "NativeAA: [HOLD] Bluetooth channel held"` reads at least `100s` and its pings count is
   **> 0**. Record held seconds and pings.
6. Session alive for the hold: `sslc F R2` = 1, `cnt F R2 "[HOLD] Bluetooth channel held"` >= 2,
   `cnt F R2 "the phone closed the held Bluetooth channel"` = 0, and `hubtw F R2-start R2-t1 | grep -acF "the session ended; releasing the held Bluetooth channel"` = 0.
7. R5b: `cnt F R2 "Asking for ping timeout"` = 0 (the orphan key is `true`; `main` prints this line).
8. Phone: `pcnt P "$PHT" "FOUND_COMPATIBLE_WIFI_NETWORK"` >= 1; `pcnt P "$PHT" "WiFi channels not supported"` = 0;
   `pcnt P "$PHT" "has timed out on pings"` = 0; `pcnt P "$PHT" "Retrying connection attempt on all channels"` = 0.
9. Phone: `ph_after P "$PHT" | grep -aF "Checking video config at index 0" | grep -acF "isAllowed: true"` >= 1.

Record, not graded: `cnt F R2 "WifiStartRequest from the phone, ignored on purpose"`; the
`the phone selected` line; `pcnt P "$PHT" "has not received the ping response for last: "`; the
`Throughput over` count and the median `rendered=` after SSL.

**R4 control** (graded in R4, recorded here): for each tap `k`, `hubtw F R2-t<k> <next>` (next is `R2-t2`,
`R2-t3`, `R2-end`) first `Touch map:` line, and on the phone
`ph_btw P "$PT_R2_t<k>" "<next phone time>" | grep -aF "injectMotionEvent(event:" | grep -aF ACTION_DOWN | head -1`.
Record the window name before `injectMotionEvent`, `x[0]` and `y[0]`.

FAIL: any condition 1 to 9 not met. If condition 2 reads -8 with a 5 GHz `channels=[...]`, also record
the phone's `pcnt P "$PHT" "No compatible frequency was found for"` and its country code
(`adb -s $PH shell cmd wifi get-country-code`).

What a PASS looks like if the change did nothing: on `main` field 4 is empty, the status is -8, and the
ping never starts, so conditions 2, 5 and 8 fail. On `main` with the orphan key on, condition 7 fails.

### R1. The point of the round: 2.4 GHz group, 1080p, the phone picks the fallback (4 min)

Setup, app stopped: `hu_kv "set int wifi-direct-band 2"`. Read back. D-HU `groupFormed` MUST read false.

1. `ph_down`. `adb -s $HU shell am force-stop $PKG`. `RUN=R1 th_gate`.
2. `cap_on R1`, `launch`, `sleep 3`, `mark R1-start`, `sleep 15`, `ph_up`.
3. `waitfor $OUT/R1-hu.txt "SSL handshake complete" 150`. On TIMEOUT: `mark R1-end`, `cap_off`, `huexit`,
   and repeat once as `R1b`. A second TIMEOUT is a FAIL; R4 is then INCONCLUSIVE; go to R3.
4. `ph_link > $OUT/R1-link.txt`. `sleep 20`. `adb -s $HU exec-out screencap -p > $OUT/R1-pre.png`.
5. R4 taps: `tap R1-t1 59 665`, `tap R1-t2 400 665`, `tap R1-t3 652 665`. Then `PT_R1_end=$(phnow)`.
6. `sleep 50` (a `[HOLD]` line needs 60 s of hold). `mark R1-end`, `huexit`, `cap_off`.
7. Read D-HU's station back (P4 command). If it is not `Pegue Cdesta` within 60 s, run
   `adb -s $HU shell svc wifi disable; sleep 3; adb -s $HU shell svc wifi enable`, wait 60 s, and record both reads.

Pre-check (setup failure if not met): `Initializing WiFi Mode: NATIVE` >= 1; the `onGroupInfoAvailable:`
line reads `(2.4GHz` with `Freq:` below 2500; `[RES_CAP]` carries `capped=_1920x1080` and
`linkCapped=none`; `[ServiceDiscovery] NegotiatedResolution is: 1920x1080` = 1; `createGroup SUCCESS` = 1.

**Gate** (`F=$OUT/R1-hu.txt`, `P=$OUT/R1-phone.txt`): `pcnt P "$PHT" "is not allowed due wireless frequency"`.
If it is **0**, R1 is **INCONCLUSIVE**: the phone did not read its link as 2.4 GHz. Do not grade R1 or R4.
Record the `config_index`, `R1-link.txt`, and every `Checking video config at index` line.

PASS, every condition (gate passed):

1. `line F R1 "NativeAA: [TX] version request channel type="` ends `channels=[<f>]`, `<f>` the group's
   `Freq:`; and `line F R1 "NativeAA: [RX] WifiVersionResponse"` carries `status=NO_SUPPORTED_WIFI_CHANNELS(-8)`.
2. `cnt F R1 "[ServiceDiscovery] Offering a _1280x720 fallback"` = 1 and `cnt F R1 "configuration_indices: 1"` >= 1.
3. `line F R1 "Media Start Request VIDEO: session="` carries `config_index=1`.
4. `cnt F R1 "HeadUnitScreenConfig: Video: the phone chose the _1280x720 fallback (index 1)"` = 1, and its
   `margins <W>x<H>` equals the `Offering a` line's `margins <W>x<H>`.
5. At least one `Throughput over` line after the `chose the` line has `rendered=` > 0. Record the median
   `rendered=` over the windows from SSL to `R1-end`.
6. Phone: `pcnt P "$PHT" "No working configuration"` = 0; and
   `ph_after P "$PHT" | grep -aF "Checking video config at index 1" | grep -acF "isAllowed: true"` >= 1.
7. Phone: `pcnt P "$PHT" "WiFi channels not supported"` >= 1 and `pcnt P "$PHT" "has not received the ping response for last: "` = 0.
   Head unit: the last `[HOLD]` line, or the `releasing the held Bluetooth channel after` line, reads `0 pings`.
8. `sslc F R1` = 1.

Record, not graded: `cnt F R1 "StationStandDown: "` and those lines; `WifiStartRequest from the phone,
ignored on purpose`; `R1-pre.png` (the picture should fill the panel, unconfirmed by eye only).

FAIL: the gate passed and any condition 1 to 8 is not met.

What a PASS looks like if the change did nothing: `main` offers one configuration and lists index 0
only, so the phone logs `No working configuration` and condition 3 cannot hold. R1 cannot pass by
accident once the gate holds.

### R4. Touch during the fallback session (graded from R1's capture, no extra time)

R4 takes R1's verdict if R1 is INCONCLUSIVE or condition 4 failed.

The open question: the phone may scale touch by our `TouchConfig` (1920x1080) rather than by the
video it picked (1280x720). If it does, a tap lands 1.5x off.

For each tap `k` (1 to 3), with `<next>` = `R1-t2`, `R1-t3`, `R1-end` and the phone time after it:

- head unit: `hubtw $OUT/R1-hu.txt R1-t<k> <next> | grep -aF "Touch map:" | head -1`. Take `X,Y` from
  `-> video=X,Y`.
- phone: `ph_btw $OUT/R1-phone.txt "$PT_R1_t<k>" "<next phone time>" | grep -aF "injectMotionEvent(event:" | grep -aF ACTION_DOWN | head -1`.
  Take the window name before `injectMotionEvent`, `x[0]` and `y[0]`.

PASS, every condition:

1. Every `Touch map:` line in R1 after the `chose the` line carries `video=1280x720`, and its
   `margin=<mw>x<mh>` equals the `chose the` line's margins.
2. For all 3 taps, the phone line exists, names `GhFacetBar`, and `|x[0] - X|` <= 2. (The bottom bar
   starts at row 600 of 720, so `y[0]` reads about `Y - 600`. Record it.)

FAIL: for any tap, the phone line names another window with `x[0]` near `X * 2/3` or `X * 3/2` (more than
10 px from `X`), or names `GhFacetBar` with `|x[0] - X|` > 2. Record the ratio `x[0] / X` per tap.

INCONCLUSIVE: no `injectMotionEvent(event:` line for any R1 tap **and** none for R2's control taps either
(this Gearhead build does not log it). If R2's control taps logged lines and R1's did not, that is a
FAIL: the taps reached the phone in a window that does not log, which is the scaled-touch signature.

A `meb injectMotionEvent` line can sit about 88 px left of the mapped X, because Android Auto's own rail
is inside the video. If a tap names `meb`, grade it as `|x[0] - (X - 88)|` <= 2 and say so.

### R3. Head unit server on a 2.4 GHz access point (10 min, runs last)

The phone's own head unit server (`:5277`), reached over a 2.4 GHz SoftAP that D-HU hosts. This is the
link that `NarrowBandProfilePolicy` cannot read (frequency 0), so only the fallback can help here. Mode
`0` (MANUAL) runs no discovery; `ACTION_CONNECT --es ip` dials the phone directly.

Skip R3 and mark it UNTESTABLE if P5 read 0 and the operator did not turn the server on.

Setup:

1. D-POCO: `adb -s $PH shell cat /proc/net/tcp6 /proc/net/tcp | grep -ci ':149D'` MUST be >= 1.
2. D-HU: `adb -s $HU shell cmd wifi start-softap OHU-TEST wpa2 testtest1234 -b 2`, `sleep 10`,
   `adb -s $HU shell dumpsys wifi | grep -i SoftApInfo`. The frequency MUST be below 2500. If the command
   printed `Soft AP failed to start`, run `cmd wifi stop-softap`, `sleep 3` and start it once more.
3. D-POCO: `adb -s $PH shell cmd wifi connect-network OHU-TEST wpa2 testtest1234`, `sleep 15`, `ph_link`.
   `mWifiInfo` MUST name `OHU-TEST` with a frequency below 2500. If it does not after one more try,
   R3 is UNTESTABLE: go to closing.
4. `PIP=$(adb -s $PH shell ip -4 addr show wlan0 | grep -oE 'inet [0-9.]+' | cut -d' ' -f2)`. Record it.
5. App stopped: `hu_kv "set int wifi-connection-mode 0"`. Read back.

Run:

1. `adb -s $HU shell am force-stop $PKG`. `RUN=R3 th_gate`.
2. `cap_on R3`, `launch`, `sleep 5`, `mark R3-start`, `send ACTION_CONNECT --es ip $PIP`.
3. `waitfor $OUT/R3-hu.txt "SSL handshake complete" 90`. On TIMEOUT: `mark R3-end`, `cap_off`, record, and go
   to closing (no retry: a failed attempt can wedge the phone's server).
4. `sleep 40`. `send ACTION_DISCONNECT`. `sleep 5`. `mark R3-end`, `cap_off`.

Pre-check (setup failure if not met): `Initializing WiFi Mode: MANUAL` >= 1 or no `Initializing WiFi Mode`
line at all; `[RES_CAP]` carries `capped=_1920x1080`; `AutomationReceiver: ` names the connect action.

**Gate:** `pcnt $OUT/R3-phone.txt "$PHT" "is not allowed due wireless frequency"`. If 0, R3 is
INCONCLUSIVE (the phone did not treat this link as 2.4 GHz wireless). Record the `config_index`.

PASS, every condition (gate passed): R1's conditions 2, 3, 4, 5, 6 and 8 on `$OUT/R3-hu.txt` and
`$OUT/R3-phone.txt`. No WPP lines exist in this mode.

### Closing

`huexit` on D-HU if the app runs, then force-stop. `adb -s $HU shell cmd wifi stop-softap`. D-POCO: find
`OHU-TEST` in `adb -s $PH shell cmd wifi list-networks` and `cmd wifi forget-network <id>`; then `ph_link`
MUST show its P4 network again. D-MOTO: `svc bluetooth enable`. Restore D-HU's backup as root
(`rig-quirks/units/D-HU.md`), `chown` and `chmod 660`, read it back and diff against the round-start
backup: MUST be empty. D-HU station MUST read `Pegue Cdesta`. Leave the candidate APK installed.

**Stop rules.** Each run has at most one repeat, and only for a setup failure or a TIMEOUT as written.
Discard and re-run once on a template §4 discard hit (a second `createGroup SUCCESS`,
`Magic Garbage detected in header`, or `MATCH! Starting AapService` with group churn). After that, grade
what you have.

## 8. Do not re-run

- That -8 on `main` is inert for the session (`rig-quirks/topics/wifi.md`). R1 records it only.
- The cross-band split with the station on 5 GHz and the stand-down that fixes it.
- That `ConnectionConfiguration` is absent from the phone's parser. That was read in both decompiles.
- That the phone negotiates AAP 1.7 whatever we ask. Record the line; do not grade it.
- The JVM cases for `VideoFallbackPolicy`, `WppChannelListPolicy` and the type 1 rule (3120 tests in R0).

## 9. Report back

1. R1: the refusal count, the `config_index`, the `chose the` count, and the median `rendered=` per window.
2. R2: the WPP status, the last `[HOLD]` line (seconds and pings answered), and the phone's
   `has timed out on pings` count.
3. R4: per tap, `X` from `Touch map`, the phone window, `x[0]`, `y[0]` and the ratio `x[0] / X`, for R1 and
   for the R2 control.
