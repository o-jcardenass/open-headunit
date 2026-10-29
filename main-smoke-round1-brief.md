# main-smoke, round 1 brief: current main on every unit, the USB dongle included

Published name: `main-smoke-round1-brief.md`. Results go in `main-smoke-round1-results.md`.
Evidence: create the release `rig-evidence-main-smoke` with the asset `main-smoke-round1-captures.zip`.

Units: D-HU, D-SAM, D-HP and D-POCO as head units; D-POCO and D-MOTO as phones; the USB dongle (`D-DONGLE`, below).

Queue line: `main-smoke-round1-brief.md` (one arm, `main` `947b3ae9`; one build; units D-HU, D-SAM, D-HP, D-POCO, D-MOTO
and the dongle; M3, M6 and M7 are the point).

Pairing: none, because the round uses every unit (§3b). Hand steps: one Bluetooth re-pair, eight cable moves, one
dongle reseat per dongle cycle at most, USB system dialogs, a D-MOTO unlock (§4). Injected taps: none.

**Time budget.** Build 25 min, Prepare 15 min, stage S 12 min, stage U 30 min, stage H 30 min, stage P 25 min, cable
moves and installs 15 min, thermal waits about 10 min. **About 2 h 40 min.** Write the results file after every run.

| Stage | Run | Head unit | Phone or device | Link | What it proves | Min |
|---|---|---|---|---|---|---|
| S | M4 | D-SAM (API 19) | D-POCO | Native wireless, PCM | a session, audio damage for the record, the settings screen draws | 12 |
| U | M1 | D-HU (API 34) | D-POCO | Native wireless | a session, a fast Exit and Start, the log export name, the settings screen, no module lines | 15 |
| U | **M3** | D-HU, 32-bit install | D-POCO | Native, software H.264 | **the bundled FFmpeg H.264 decoder on `armeabi-v7a`** | 15 |
| H | **M6** | D-HP (API 17) | the dongle | USB dongle | **3 dongle cycles to a picture on an API 17 host** | 15 |
| H | M5 | D-HP | D-MOTO | USB | a wired session with PCM music | 12 |
| P | M2 | D-POCO (API 35) | D-MOTO | USB | a wired session with a phone as the host, and a clean unplug | 8 |
| P | **M7** | D-POCO | the dongle | USB dongle | **5 dongle cycles to a picture on an API 35 host** | 15 |

## 1. Build and baseline

One arm, no control. `main` is `v.4.0.0-alpha` plus nine merges: the Material 3 settings screen, the bundled H.264
decoder (now on 32-bit ARM too), the audio renderer and its time-correction engines, cleanup tied to its arm after an
Exit, the ZBT link state before a reconnect, WPP decisions in one snapshot, and the `OHU_Log_` file name. Every one of
those ran on the rig inside `test/pr-merge-train` (rounds 1 to 4). This exact tree has not.

| Ref | Commit | Tree |
|---|---|---|
| `origin/main` | `947b3ae9fae9834aa8e4145ee91751ed743c2ae0` | `e7112fbfdd820cf4834a8a9a5a47f5b62e3ea165` |

The dev host ran the JVM suite at this commit: 4245 tests, 0 failures, 0 skipped. Do not run the unit tests on the rig.

**get_apk.sh**

```bash
OUT=rig-data/rounds/main-smoke-round1; mkdir -p $OUT
WT=$($RIG_TOOLKIT/wt-new.sh main-smoke)     # usage in TOOLS.md; record the path in Setup notes
git -C $WT fetch origin main
git -C $WT checkout --detach 947b3ae9fae9834aa8e4145ee91751ed743c2ae0
git -C $WT rev-parse 'HEAD^{tree}'          # MUST print e7112fbfdd820cf4834a8a9a5a47f5b62e3ea165
$RIG_TOOLKIT/rig_thermal.sh wait 70
HUR_DIR=$WT MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh
cp rig-data/apks/*.apk $OUT/apk-M.apk; md5sum $OUT/apk-M.apk   # record the md5
unzip -l $OUT/apk-M.apk | grep -ac 'lib/armeabi-v7a/libavcodec.so'   # MUST print 1
```

**Identity after every install, on every head unit:** `send ACTION_QUERY_STATE` and its `data=` line carries
`947b3ae9`; the md5 of the pulled live APK matches; `unzip -p live.apk 'classes*.dex' | strings | grep -acE
'FfmpegVideoDecoder|NativeTimeStretchEngine'` is 2 or more.

## 2. What this is and why

Seven PRs landed on `main` in one day, each tested inside a merge train but never as this tree. This round runs `main`
once on every unit and every route the rig has, with absolute gates and no A/B. It also closes three gaps:

- **The bundled H.264 decoder on 32-bit ARM** never ran: rounds 1 to 4 used `arm64-v8a`, and the decoder refuses to
  load below Android 5 (`FfmpegVideoDecoder`: `SDK_INT < LOLLIPOP` returns false), so D-SAM and D-HP cannot run it.
  M3 installs the APK on D-HU as `armeabi-v7a` with `adb install --abi`, which the APK carries (`lib/armeabi-v7a/`).
- **The dongle has not run since 3.5.0.** Wireless dongles regressed in 3.5.0 for one user (a different model). The
  `usb-dongle-regression` round 1 found the rig's dongle projecting 8 of 8 cycles on v.3.5.0 (H-B 3/3, P-B 5/5). Since
  then the video decoder, the audio renderer and the session lifecycle all changed. M6 and M7 repeat that round's
  cycle on `main`, with its settings, so the two can be compared.
- **The USB route on D-HP with PCM music** was asked for in `pr-merge-train` round 4 and ran over Headunit Server
  instead. M5 runs it.

## 3. What is different about this round

- **D-HU cannot host USB** (`rig-quirks/topics/tooling.md`). USB runs use D-HP and D-POCO as hosts, on wireless adb,
  as the `usb-dongle-regression` and `emotionbug-batch` rounds did (template §7b).
- **D-DONGLE** is the rig's wireless Android Auto dongle. It enumerates as `Google Pixel 4` (idle `18d1:4ee1`,
  accessory `18d1:2d00`) and is bonded to D-MOTO as `carplay_box_F96B`. A warm re-attach carries 2 to 2.4 KB of stale
  data, and the first version request then fails with a detach; `main` retries and reaches SSL on the next attach.
  D-MOTO stays on its PC cable during M6 and M7: the dongle reaches it over its own WiFi.
- **D-HP's battery is degraded and both the dongle and D-MOTO draw from it over OTG.** M6 runs before M5, on a full
  charge. Gates: 60% to start stage H, read before every cycle and every run, stop stage H at 30%.
- **D-POCO is not paired with D-HU** since `pr-merge-train` round 4. So stage S (D-SAM) runs first, then the operator
  re-pairs D-POCO with D-HU before stage U.
- **D-POCO changes role once**, from phone to USB host in stage P. Its own app gets `headunit://exit` first:
  a force-stop leaves its `p2p0` a group owner, and a phone in that state never joins a head unit's group.
- **Expected INCONCLUSIVE:** M2's video (a phone host holds the projection for milliseconds only, so `rendered` may
  be 0; M2 grades on the log); M3 if `ro.product.cpu.abilist` on D-HU has no `armeabi-v7a`, or the install refuses the
  ABI (then UNTESTABLE: §5 forbids an uninstall).
- **No comparison is a verdict.** M4 and M5 print the same damage numbers as round 4 for the record only.
- Read `rig-quirks/topics/tooling.md`, `topics/audio.md`, `topics/wifi.md`, every `units/` file, and template §7b.

## 4. Cabling and hand steps

The PC has four USB ports for six devices. Each stage names what is plugged in.

| Stage | On the PC | On an OTG port | Wireless adb |
|---|---|---|---|
| Prepare 1 | D-HU, D-POCO, D-SAM, D-HP | | |
| Prepare 2 to stage U | D-HU, D-POCO, D-SAM, D-MOTO | | D-HP |
| H, M6 | D-HU, D-POCO, D-SAM, D-MOTO | the dongle on D-HP | D-HP |
| H, M5 | D-HU, D-POCO, D-SAM | D-MOTO on D-HP | D-HP, D-MOTO |
| P, M2 | D-HU, D-SAM | D-MOTO on D-POCO | D-POCO, D-MOTO, D-HP |
| P, M7 | D-HU, D-SAM, D-MOTO | the dongle on D-POCO | D-POCO, D-HP |

| Id | Hand step | When |
|---|---|---|
| H0 | Unlock D-MOTO if it shows a PIN | Prepare, and when a cue asks |
| H1 | Unplug D-HP from the PC (it is on wireless adb). Plug D-MOTO into that port | end of Prepare |
| H2 | On D-POCO, pair D-HU's Bluetooth (`Navegadortz2`) | after stage S |
| H3 | Plug the dongle into D-HP's OTG port | stage H start |
| H4 | Move the dongle off D-HP. Unplug D-MOTO from the PC and plug it into D-HP's OTG port | after M6 |
| H5 | Unplug D-MOTO from D-HP. Unplug D-POCO from the PC and plug D-MOTO into D-POCO's OTG port | after M5 |
| H6 | Unplug D-MOTO from D-POCO and put it on the free PC port. Plug the dongle into D-POCO's OTG port | after M2 |
| H7 | Unplug the dongle; D-POCO back on its PC cable | Closing |
| H8 | Answer a system USB dialog with OK. "Always" is allowed for the dongle, not for D-MOTO | when one appears |
| H9 | Unplug D-MOTO from D-POCO | the M2 unplug cue |
| H10 | Reseat the dongle | a host's `/sys/bus/usb/devices` shows root hubs only; once per run |

Open the cue terminal first: `tail -n0 -F $OUT/cues.log`. `cue <text>` appends to it.

## 5. Settings keys

Write with the app stopped. D-HU: `set_hu_settings_host.py` (rooted). D-SAM, D-HP, D-POCO:
`set_prefs_runas_host.py`. Back up every head unit's `settings.xml` first and diff after each install, with carriage
returns stripped on D-SAM and D-HP. Read every key back after every write and every install (`adb install -r` can
wipe the file, `rig-quirks/topics/tooling.md`).

| Key | Type | Native (M1, M3, M4) | USB phone (M2, M5) | Dongle (M6, M7) |
|---|---|---|---|---|
| `log-level` | int | 2 | 2 | 2 |
| `onboarding-version` | int | 2 | 2 | 2 |
| `allow-external-configuration` | boolean | true | true | true |
| `kill-on-disconnect` | boolean | false | false | false |
| `enable-floating-button` | boolean | false | false | false |
| `video-profile-starvation-cap` | | delete | delete | delete |
| `wifi-connection-mode` | int | 3 | 0 | 0 |
| `connection-modes` | set | `wifi` | `usb` | `usb` |
| `enable-audio-sink` | boolean | true | true | false |
| `use-aac-audio` | boolean | false | false | (leave) |
| `audio-latency-multiplier` | int | 2 | 2 | (leave) |
| `audio-queue-capacity` | int | 20 | 20 | (leave) |
| `use-aaudio-output` | boolean | false | false | (leave) |
| `narrow-band-profile-cap` | boolean | false | false | false |
| `force-software-decoding` | boolean | false (M3: true) | false | false |
| `software-video-decoder` | int | (leave) (M3: 1) | (leave) | (leave) |
| `video-codec` | string | (leave) (M3: `H.264`) | (leave) | `H.264` |
| `resolutionId` | int | (leave) | (leave) | 2 |
| `native-ap-transport` | int | 0 | | |
| `external-bt-zbt-transport` | boolean | false | | |
| `native-driver-selection-mode` | int | 0 | | 0 |
| `native-poke-all-paired` | boolean | false | | false |
| `native-poke-bt-macs` | set | `DC:B7:2E:5E:4E:59` only | | |
| `native-preferred-device-mac`, `last-connected-native-mac` | string | `DC:B7:2E:5E:4E:59` | | |
| `native-mac` | | delete (not read by `main`) | | |
| `screen-orientation` | int | D-SAM only: 2 | | |
| `auto-start-on-usb` | boolean | | false | false |
| `auto-connect-last-session` | boolean | | true | true |
| `reopen-on-reconnection` | boolean | | true | true |
| `use-libusb` | boolean | | false | false |

`DC:B7:2E:5E:4E:59` is D-POCO's Bluetooth address: confirm it with `dumpsys bluetooth_manager` on D-POCO and use the
value read. `narrow-band-profile-cap` false stops a 2.4 GHz link from announcing AAC over `use-aac-audio`.
`auto-start-on-usb` false avoids the API 35 `BootCompleteReceiver` crash dialog on D-POCO.

## 6. Helpers

**libm.sh**

```bash
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
MAIN=$PKG/com.andrerinas.openheadunit.main.MainActivity
SETTINGS=$PKG/com.andrerinas.openheadunit.main.SettingsActivity
OUT=rig-data/rounds/main-smoke-round1; mkdir -p $OUT; export LC_ALL=C
DHU=27870808938846; SAM=30041c35642d2200; POCO=4f4027e9; MOTO=ZY22GC3BM4
# Each stage sets HU (the head unit's serial or ip:5555) and PH (the phone's).
A() { flock "/tmp/rig-adb-${HU//[:.]/_}.lock" adb -s "$HU" "$@"; }
send() { a=$1; shift; A shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; }
mark() { send ACTION_LOG_MARKER --es text "$1" >/dev/null; sleep 0.3; adb -s "$PH" shell log -t RIGMARK "$1"
  echo "$1 $(date '+%m-%d %H:%M:%S')" >> $OUT/marks.txt; }
cue() { echo "$(date +%T) $*" >> $OUT/cues.log; }
cap() { A logcat -G 16M 2>/dev/null; A logcat -c; stdbuf -oL adb -s "$HU" logcat -v time >> $OUT/$1-hu.txt & echo $! > $OUT/$1-hu.pid
  adb -s "$PH" logcat -c; stdbuf -oL adb -s "$PH" logcat -v time >> $OUT/$1-phone.txt & echo $! > $OUT/$1-phone.pid; }
alive() { for s in hu phone; do kill -0 "$(cat $OUT/$1-$s.pid)" 2>/dev/null && continue
  d=$HU; [ $s = phone ] && d=$PH; echo "$(date +%T) $1-$s capture restarted" >> $OUT/marks.txt
  stdbuf -oL adb -s "$d" logcat -v time >> $OUT/$1-$s.txt & echo $! > $OUT/$1-$s.pid; done; }
uncap() { kill $(cat $OUT/$1-hu.pid) $(cat $OUT/$1-phone.pid) 2>/dev/null; }
after() { sed -n "/AutomationMarker: $1/,\$p" $OUT/$2-hu.txt | grep -acF -- "$3"; }
waitn() { local m=$1 r=$2 s=$3 cap=$4 t=0       # waitn <marker> <run> <string> <seconds>
  while [ $t -lt $cap ]; do [ "$(after $m $r "$s")" -ge 1 ] && return 0; sleep 1; t=$((t+1)); done; return 1; }
th_gate() { $RIG_TOOLKIT/rig_thermal.sh wait 75 || $RIG_TOOLKIT/rig_thermal.sh wait 75 || { echo HOST_HOT; return 1; }
  $RIG_TOOLKIT/rig_thermal.sh watch $OUT/thermal-$1.log & THPID=$!; }
th_end() { kill $THPID 2>/dev/null; wait $THPID 2>/dev/null; }
launch() { A shell am start -n $MAIN >/dev/null; }
appexit() { send ACTION_DISCONNECT; sleep 5; A shell am start -a android.intent.action.VIEW -d "headunit://exit" >/dev/null
  sleep 5; A shell am force-stop $PKG; sleep 1; }
hold() { local end=$((SECONDS+$2)); while [ $SECONDS -lt $end ]; do sleep 30; alive $1; done; }
bat() { A shell dumpsys battery | grep -a -m1 level | tr -dc 0-9; }
usbdev() { A shell ls /sys/bus/usb/devices | tr -d '\r' | grep -acE '^[0-9]+-[0-9.]+$'; }   # 0: root hubs only
ph_link() { adb -s $PH shell svc wifi $1; sleep 0.3; adb -s $PH shell svc bluetooth $1; sleep 4
  adb -s $PH shell dumpsys wifi | grep -a -m1 'Wi-Fi is'; adb -s $PH shell dumpsys bluetooth_manager | grep -a -m1 -E '^ *state:'; }
focus() { A shell dumpsys activity activities | grep -a -m1 -E 'topResumedActivity|mResumedActivity' | tee -a $OUT/$1-focus.txt; }
shot() { A shell screencap -p /sdcard/s.png; sleep 0.3; A pull /sdcard/s.png $OUT/$1.png >/dev/null; }
```

**pcrit.py** (phone `Critical error` lines in a run, minus those within 2 s after an Exit marker)

```python
import re, sys
from datetime import datetime, timedelta
cap, run = sys.argv[1], sys.argv[2]
ts = lambda l: datetime.strptime("2026-" + l[:18], "%Y-%m-%d %H:%M:%S.%f")
L = [l for l in open(cap, errors="replace") if re.match(r"\d\d-\d\d \d\d:\d\d:\d\d\.\d", l)]
on, exits, hits = False, [], []
for l in L:
    if f"RIGMARK" in l and f"{run}-start" in l: on = True
    if not on: continue
    if "RIGMARK" in l and ("-exit" in l.split("RIGMARK")[-1]): exits.append(ts(l))
    if "Critical error" in l: hits.append((ts(l), l.strip()))
    if "RIGMARK" in l and f"{run}-end" in l: break
left = [h for h in hits if not any(timedelta(0) <= h[0] - e <= timedelta(seconds=2) for e in exits)]
print(f"{run}\tcrit_total={len(hits)}\tcrit_graded={len(left)}")
for h in left[:5]: print(h[1])
```

**dongle_cycle.py** (one TSV row per cycle, from `usb-dongle-regression` round 1, with the stale-data columns)

```python
import re, sys
from datetime import datetime
cap, go, end = sys.argv[1], sys.argv[2], sys.argv[3]
L = open(cap, errors="replace").read().splitlines()
def at(lbl):
    for i, l in enumerate(L):
        if l.rstrip().endswith("AutomationMarker: " + lbl): return i
    return None
i0, i1 = at(go), at(end)
if i0 is None or i1 is None: print(f"{go}\tVOID markers_missing"); sys.exit(0)
W = L[i0:i1]
def ts(l):
    m = re.match(r"(\d\d-\d\d) (\d\d:\d\d:\d\d\.\d+)", l)
    return datetime.strptime("2026-" + m.group(1) + " " + m.group(2), "%Y-%m-%d %H:%M:%S.%f") if m else None
st = [i for i, l in enumerate(W) if "Start Aap transport handshake" in l]
ssl = sdr = reply = silent = projected = 0; thr_max = 0; sel = set()
for k, i in enumerate(st):
    seg = W[i:(st[k + 1] if k + 1 < len(st) else len(W))]
    has = lambda s: any(s in l for l in seg)
    first = lambda s: next((l for l in seg if s in l), None)
    for l in seg:
        m = re.search(r"the phone selected (\d+\.\d+)", l)
        if m: sel.add(m.group(1))
    if has("SSL handshake complete"): ssl += 1
    if not has("Service Discovery Request"): continue
    sdr += 1
    rep = has("Audio Focus Request") or has("Sensor Start Request") or has("Media Sink Setup Request")
    n = sum("Throughput over" in l for l in seg); thr_max = max(thr_max, n)
    if rep: reply += 1
    if rep and n >= 3: projected = 1
    if not rep:
        s, d = first("Service Discovery Request"), first("USB_DEVICE_DETACHED")
        if d and ts(s) and ts(d) and (ts(d) - ts(s)).total_seconds() <= 5: silent += 1
stale = sum("stale USB bytes before version request" in l for l in W)
late = sum("discarded a late VERSION_RESPONSE" in l for l in W)
print(f"{go}\thandshakes={len(st)}\tssl={ssl}\tsdr={sdr}\treply={reply}\tsilent_detach={silent}"
      f"\tthr_max={thr_max}\tprojected={projected}\tstale={stale}\tlate_discard={late}\tselected={','.join(sorted(sel)) or '-'}")
```

**session.sh** (Native runs: `nsession <run> <first-cap>`; M1 adds `fastcycle`)

```bash
. ./libm.sh
nsession() { local R=$1 CAP=$2
  th_gate $R || return 1; ph_link disable; A shell am force-stop $PKG; cap $R
  launch; sleep 2; mark $R-start; ph_link enable
  waitn $R-start $R "SSL handshake complete" $CAP || { echo "$R NO_SESSION" >> $OUT/marks.txt; nend $R; return 1; }
  waitn $R-start $R "Throughput over" 30
  adb -s $PH shell input keyevent 126; waitn $R-start $R "Media Start Request AUDIO" 20 || echo "$R NO_MUSIC" >> $OUT/marks.txt; }
nend() { local R=$1; mark $R-exit; mark $R-end; th_end; appexit; uncap $R; }
fastcycle() { local C=$1 R=$2
  mark $C-exit; send ACTION_DISCONNECT; sleep 0.3; mark $C-go; send ACTION_START_WIRELESS_SCAN
  waitn $C-go $R "SSL handshake complete" 120 || echo "$C NO_SESSION" >> $OUT/marks.txt
  waitn $C-go $R "Throughput over" 30; sleep 20; mark $C-end; }
settingsshot() { local R=$1 RUNAS=$2
  mark $R-set; A shell $RUNAS am start --user 0 -n $SETTINGS --ei extra_destination 0 >/dev/null; sleep 10
  focus $R-set; shot $R-settings; A shell input keyevent 4; sleep 2; mark $R-set-end; }
```

`settingsshot M1 ""` on D-HU; `settingsshot M4 "run-as $PKG"` on D-SAM (its `SettingsActivity` is not exported).

## 7. The deciding lines

All verified with `grep -F -r` in `app/src/main` at `947b3ae9`. All print at INFO. Grep with `-a`, in `<run>-hu.txt`
between `<run>-start` and `<run>-end` unless noted; remove duplicate lines first in a capture `alive` restarted
(`awk '!seen[$0]++'`).

| Id | Line (fixed substring) | Read |
|---|---|---|
| L-RX | `AutomationReceiver: ` | a verb landed; a step with none is void, not a FAIL |
| L-SSL | `SSL handshake complete` | a session formed |
| L-TP | `Throughput over` | `rendered=N`, one line per 5 s |
| L-CODEC | `Media Sink Setup Request: ` with `on channel VIDEO` | 3 = H.264 |
| L-MUS | `Media Start Request AUDIO` | music started |
| L-DEC | `AudioDecoder.start: channel=` | the media channel's `codec=` must not be AAC |
| L-MIX | `AudioMixer: id=` with `loopGapMax=` | the 10 s device line: `effective=`, `loopGapMax=` |
| L-CH6 | regex `AudioMixer: id=[0-9]+ channel=6 target=` | `concealedFrames=`, `staleFrames=`, `compressedFrames=` |
| L-ARM | `WifiLauncher: Initializing WiFi Mode:` | M1: the new Start armed |
| L-EXIT | `AapService: Native AA user exit. Stopping active launcher.` | M1: the old Exit's cleanup |
| L-GRP | `AapService: CommManager teardown complete. Stopping WiFi Direct group.` | M1: the old Exit's group removal |
| L-ZBT | `[ZBT]` | M1: must be 0 (the module route is off) |
| L-XY | `XYAuto projection media policy` | M1: must be 0 (that change is not on `main`; 1 or more means the wrong APK) |
| L-FF1 | `Configuring bundled FFmpeg H.264/AVC decoder for` | M3 |
| L-FF2 | `Bundled FFmpeg H.264/AVC decoder initialized` | M3 |
| L-FF3 | `First bundled software H.264/AVC frame rendered` | M3 |
| L-WARN | `never opened the Android Auto channel` | M1, M4: the phone is bound to another head unit |
| L-ACC | `Sending acc start` | M2, M5: the AOA switch |
| L-SEL | `the phone selected ` | M2, M6, M7: the version answer (report it) |
| L-SDR | `Service Discovery Request` | the phone asked for our response |
| L-ANS | `Audio Focus Request`, `Sensor Start Request`, `Media Sink Setup Request` | the phone answered our response |
| L-DET | `USB Intent: ` with `USB_DEVICE_DETACHED` | a USB device left |
| L-STALE | `stale USB bytes before version request` | dongle: a warm re-attach (report) |
| L-LATE | `discarded a late VERSION_RESPONSE` | dongle: the late-answer guard fired (report) |
| L-PERM | `Requesting USB permission` | USB: report each identity (template §7b) |
| D-MATCH | `MATCH! Starting AapService` | discard rule (template §4) |

**FAIL lines, every run:** `FATAL EXCEPTION` in a block naming `com.andrerinas`; `UnsatisfiedLinkError`; `ANR in
com.andrerinas`. M3 also: `Failed to load hur_soft_video`, `decoder failed to initialize`, `Stopping initialization
retries for this codec`. **Phone, every run:** `python3 -I pcrit.py <run>-phone.txt <run>` prints `crit_graded=0`
(Gearhead prints `Critical error` lines within about 1 s of every Exit we cause, on every build; those are excluded).

**Damage** (M4, M5, for the record): sum the increases of `concealedFrames`, `staleFrames` and `compressedFrames`
between consecutive L-CH6 lines from `<run>-music` to `<run>-end`; when a value falls, add the new value. **Slow
windows:** L-MIX lines whose `loopGapMax` in ms is above `effective=`/48.

## 8. Runs

**Session gate, every Native and USB phone run:** L-SSL within the cap, the last L-TP before `<run>-end` has
`rendered=` above 0 (except M2), 0 FAIL lines, `crit_graded=0`, D-MATCH 0. A run that misses it on a first session
that never forms is retried once, then UNTESTABLE. Stop rule: two runs UNTESTABLE in one stage end that stage.

### Prepare

1. `get_apk.sh` (section 1). D-SAM on a separate charger, 50% or more. D-HP charged to 100% if possible.
2. On D-HU, D-SAM, D-HP and D-POCO, with the app stopped: back up `settings.xml` to `$OUT/backup-<unit>.xml`, pull the
   installed APK to `$OUT/prior-<unit>.apk` and record its md5, record `dumpsys usb | grep -i -A4 headunitrevived` on
   D-HP and D-POCO. Install `apk-M.apk` with `adb install -r -d` on all four, run the identity check, diff each
   `settings.xml` against its backup and state the delta.
3. D-HU: `stat -c '%U:%G %a' /data/data/$PKG/shared_prefs` (`chown` to the app's uid if wrong; report it either way);
   `getprop ro.product.cpu.abilist` into `$OUT/abilist.txt`.
4. D-POCO, as a phone: `am start -a android.intent.action.VIEW -d headunit://exit`, then force-stop its app;
   `dumpsys bluetooth_manager | grep -a -A10 'Bonded'` into `$OUT/poco-bonds.txt`. D-SAM must be in it.
5. D-HP: `adb -s CNU350BGBJ tcpip 5555`, read `wlan0`, `adb connect <ip>:5555`, record `HP_IP`. Then cue H1. Then D-MOTO:
   `svc power stayon true`, record Gearhead's `versionName`, confirm `carplay_box_F96B` in its bonded list, then
   `adb -s ZY22GC3BM4 tcpip 5555`, `adb connect <ip>:5555`, record `MOTO_IP`. D-MOTO `svc wifi disable` and
   `svc bluetooth disable` until stage H, read back.
6. VLC and `/sdcard/Music/ohu-stutter-long.mp3` (12 min or more) on D-MOTO; a Spotify playlist of 6 or more tracks on
   D-POCO.

### Stage S: D-SAM with D-POCO

`HU=$SAM; PH=$POCO`. Write the Native keys plus `screen-orientation` 2.

**M4.** `nsession M4 150 && { sleep 20; mark M4-music; hold M4 300; nend M4; }`, then with the app stopped
`settingsshot M4 "run-as $PKG"` inside a fresh `cap M4s` (then `uncap M4s`).

- PASS: the session gate; L-MUS 1 or more; L-DEC on the media channel reads `codec=PCM`; 28 or more L-MIX lines in the
  music window; L-WARN 0; the `M4-set` focus line names `SettingsActivity` and `M4-settings.png` shows the settings
  screen (a header and a list, not blank or black).
- If L-SSL is 0 and L-WARN printed: cue "unpair Navegadortz2 on D-POCO" only if it is still bonded, retry once.
- Record: damage and its three parts, slow windows, the largest `loopGapMax`, against round 4 SA (29665, 0, 56 ms).

Then cue H2 and wait for the operator.

### Stage U: D-HU with D-POCO

`HU=$DHU; PH=$POCO`. Write the Native keys with `set_hu_settings_host.py`.

**M1.** `nsession M1 120 && { sleep 30; fastcycle M1-c1 M1; }`, then (skip the rest if the session did not form) `send ACTION_EXPORT_LOG; sleep 8`, then
`A shell find /storage/emulated/0/Android/data/$PKG/files -name '*_Log_*' -mmin -5 > $OUT/M1-exports.txt`, then
`mark M1-exit; send ACTION_DISCONNECT; sleep 5; settingsshot M1 ""; mark M1-end; th_end; appexit; uncap M1`.

- PASS: the session gate on both sessions; between `M1-c1-go` and `M1-c1-end`, the first L-ARM within 5 s of
  `M1-c1-go`, no L-EXIT or L-GRP after that L-ARM, L-SSL within 120 s of `M1-c1-go`; `M1-exports.txt` lists a file
  whose name starts `OHU_Log_` and none that starts `HUR_Log_`; the `M1-set` focus line names `SettingsActivity` and the
  screenshot shows the settings screen; L-ZBT 0 and L-XY 0 over the whole capture.
- Record: t(`M1-c1-go`) to L-ARM and to L-SSL; the times of L-EXIT and L-GRP relative to L-ARM.

**M3.** Skip as INCONCLUSIVE if `abilist.txt` has no `armeabi-v7a`. Otherwise, app stopped:

**m3-install.sh**

```bash
. ./libm.sh; HU=$DHU
A install -r -d --abi armeabi-v7a $OUT/apk-M.apk | tee $OUT/M3-install.txt
A shell dumpsys package $PKG | grep -a -m1 primaryCpuAbi | tee $OUT/M3-abi.txt    # MUST read armeabi-v7a
```

If the install fails or the ABI reads otherwise: M3 is UNTESTABLE (do not uninstall); go on. Else re-read every key,
write the M3 keys (`force-software-decoding` true, `software-video-decoder` 1, `video-codec` `H.264`), and run
`nsession M3 120 && { sleep 180; nend M3; }`. Then `A install -r -d --abi arm64-v8a $OUT/apk-M.apk`, check `primaryCpuAbi`
reads `arm64-v8a`, and restore D-HU's backup.

- PASS: the session gate; L-FF1, L-FF2 and L-FF3 each 1 or more, in that order; L-CODEC reads 3; 0 M3 FAIL lines.
- If L-FF1 is 0: the keys did not take. Check them, fix, retry once.
- Record: t(L-SSL) to L-FF3, the median and last `rendered=` of L-TP.

### Stage H: D-HP as host (M6 first, then M5)

`HU=$HP_IP:5555`. Battery: `bat` 60 or more to start; before every cycle and run; 30 or below stops stage H (rest
UNTESTABLE, battery). D-MOTO `svc wifi enable`, `svc bluetooth enable`, read back, then `adb connect $MOTO_IP:5555`.

**M6.** `PH=$MOTO` (PC cable). Cue H3; wait for `usbdev` above 0 (cap 3 min, then cue H10 once). Then:

**dongle.sh** (`drun <run> <cycles>`)

```bash
. ./libm.sh
dcycle() { local R=$1 k=$2
  A shell am force-stop $PKG; sleep 1     # write the dongle keys again here and read them back
  mark $R-$k-go; launch
  waitn $R-$k-go $R "SSL handshake complete" 60 && sleep 45
  mark $R-$k-end; send ACTION_DISCONNECT; sleep 8; alive $R
  echo -e "$(python3 -I $OUT/dongle_cycle.py $OUT/$R-hu.txt $R-$k-go $R-$k-end)\tbat=$(bat)" | tee -a $OUT/$R.tsv; }
drun() { local R=$1 n=$2; th_gate $R || return 1; cap $R; mark $R-start
  for k in $(seq 1 $n); do b=$(bat); [ "$b" -le 30 ] && { echo "$R STOP battery $b" >> $OUT/marks.txt; break; }
    dcycle $R $k; sleep 3; done
  mark $R-end; th_end; appexit; uncap $R; python3 -I $OUT/pcrit.py $OUT/$R-phone.txt $R; }
```

Run `drun M6 3`. A row with `VOID` is re-run once as the next `k`.

- PASS: 2 or more of 3 rows read `projected=1`, and 0 FAIL lines. FAIL: 1 or fewer. Phone: report `crit_graded` and
  quote the first `Critical error` line of any cycle that did not project, with the 5 phone lines before it.
- Record per row: `ssl`, `reply`, `silent_detach`, `stale`, `late_discard`, `selected`, `bat`. Compare with
  `usb-dongle-regression` round 1 H-B (3 of 3 on v.3.5.0).

**M5.** Cue H4. `PH=$MOTO_IP:5555`. Write the USB phone keys. Then:

**m5.sh**

```bash
. ./libm.sh; VLC=org.videolan.vlc; MEDIA=/sdcard/Music/ohu-stutter-long.mp3
th_gate M5 || exit 1; A shell am force-stop $PKG; cap M5
launch; sleep 5; mark M5-start; send ACTION_CHECK_USB
waitn M5-start M5 "SSL handshake complete" 60 || { send ACTION_CHECK_USB; waitn M5-start M5 "SSL handshake complete" 60; } ||
  { cue "replug D-MOTO into D-HP once"; waitn M5-start M5 "SSL handshake complete" 90; } || echo "M5 NO_SESSION" >> $OUT/marks.txt
adb -s $PH shell am start -a android.intent.action.VIEW -d "file://$MEDIA" -t audio/mpeg -n $VLC/.StartActivity
waitn M5-start M5 "Media Start Request AUDIO" 20 || echo "M5 NO_MUSIC" >> $OUT/marks.txt
sleep 20; mark M5-music; hold M5 300; mark M5-exit; mark M5-end
adb -s $PH shell am force-stop $VLC; th_end; appexit; uncap M5
```

- PASS: the session gate; L-ACC 1 or more; L-MUS 1 or more; L-DEC media `codec=PCM`; 28 or more L-MIX lines.
- Record: damage and its parts, slow windows, the largest `loopGapMax`. Round 4's HA ran Headunit Server, not USB, so
  its numbers (8441, 19) are context only.

### Stage P: D-POCO as host (M2, then M7)

Before cue H5: `adb -s $POCO tcpip 5555`, read `wlan0`, `adb connect <ip>:5555`, record `POCO_IP`. `HU=$POCO_IP:5555`.
Write the USB phone keys on D-POCO. Battery 60% or more on D-POCO.

**M2.** Cue H5. `PH=$MOTO_IP:5555`. Then:

**m2.sh**

```bash
. ./libm.sh
th_gate M2 || exit 1; A shell am force-stop $PKG; cap M2
launch; sleep 5; mark M2-start; send ACTION_CHECK_USB
waitn M2-start M2 "SSL handshake complete" 60 || { send ACTION_CHECK_USB; waitn M2-start M2 "SSL handshake complete" 60; } ||
  echo "M2 NO_SESSION" >> $OUT/marks.txt
sleep 60; mark M2-exit; send ACTION_DISCONNECT; sleep 5
mark M2-unplug; cue "H9: unplug D-MOTO from D-POCO now"; waitn M2-unplug M2 "USB_DEVICE_DETACHED" 120
sleep 10; mark M2-end; th_end; appexit; uncap M2
```

- PASS: the session gate (without the `rendered` part); L-ACC 1 or more; L-SEL reads `STATUS_SUCCESS`; L-SDR 1 or more
  and L-ANS 1 or more; L-DET 1 or more after `M2-unplug`; 0 FAIL lines from `M2-unplug` to `M2-end`.
- Video: report the last `rendered=`. 0 is INCONCLUSIVE for video, not a FAIL.
- Record: each L-PERM identity (VID:PID), and any system USB dialog (template §7b names the two kinds).

**M7.** Cue H6. `PH=$MOTO` (PC cable). Write the dongle keys on D-POCO. Wait for `usbdev` above 0 (cap 3 min, then
cue H10 once). Run `drun M7 5`.

- PASS: 4 or more of 5 rows read `projected=1`, and 0 FAIL lines. FAIL: 2 or fewer. 3 is INCONCLUSIVE. Phone and
  record as M6. Compare with `usb-dongle-regression` round 1 P-B (5 of 5 on v.3.5.0).

### Closing

Run all of it, whatever happened before.

1. On every head unit: `appexit`, reinstall `prior-<unit>.apk` with `adb install -r -d`, check its md5, restore its
   `settings.xml` backup and diff (empty, carriage returns stripped).
2. `dumpsys usb | grep -i -A4 headunitrevived` on D-HP and D-POCO; report any new grant against Prepare.
3. Cue H7. `adb -s ZY22GC3BM4 shell svc power stayon false`; D-MOTO radios back as found in Prepare.
4. `adb -s $POCO_IP:5555 usb` and `adb -s $MOTO_IP:5555 usb` after their cables are back; D-HP stays on wireless adb
   until a port is free, then `usb`. Say in Setup notes what is cabled at the end.
5. Leave D-POCO paired with D-HU.

## 9. Do not re-run

`pr-merge-train` rounds 1 to 4 (each PR on its own tree), and `usb-dongle-regression` round 1's arms C, A and E.
Two-finger gestures, the night overlay and the ILL+ calibration are not on `main`. The four optional audio engines
are not this round.

## 10. Report back

1. M6 and M7: projected rows of 3 and of 5, with `stale`, `late_discard` and `selected` per row.
2. M3: L-FF3 present on `armeabi-v7a`, t(L-SSL) to L-FF3, and the last `rendered=`. Or why it could not run.
3. Every other run's verdict, plus M4's and M5's damage and slow windows, and any FAIL line with its first five lines.

Put every capture, `.tsv`, `marks.txt`, `cues.log`, thermal log, screenshot and `M1-exports.txt` in
`main-smoke-round1-captures.zip` on the new release `rig-evidence-main-smoke`, with its sha256 in the results file.
