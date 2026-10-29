# forget-car-still-needed, round 2 brief

> **Revised 2026-10-26: both arms moved again.** F was rebased from `048fd16a` to `6c23c264` on `main`
> `947b3ae9`, with the same patches (clean rebase), and C moved from `e9377ff9` to `947b3ae9`. F and C
> still differ only by the fix. `main` now also carries PRs #1138 and #1139. The queue line named
> `048fd16a` while the branch sat at `842f72e4` since 2026-10-25; this note supersedes both. Rebuild both
> arms and re-read section 1. The dev host ran 4268 tests at F, 0 failures. No run, string or verdict
> rule changed. A copy that still names `048fd16a` or `842f72e4` is stale.

> **Revised 2026-10-22: both arms moved again.** F was rebased from `96304bce` to `048fd16a` on `main`
> `e9377ff9`, with the same patches, and C moved from `28af450d` to `e9377ff9`. F and C still differ
> only by the fix. Re-read section 1 and rebuild both arms. No run, string or verdict rule changed. A copy
> that still names `96304bce` is stale.

> **Revised 2026-10-17: both arms moved.** F was rebased from `25a56033` to `96304bce` on `main`
> `28af450d`, with the same patches, and C moved from `d2d0a6bf` to `28af450d` so that F and C still
> differ only by the fix. Round 1's APKs are neither arm: section 1 now builds both. Re-read section 1
> and the install blocks C and F. No run, string or verdict rule changed. A copy that still names
> `25a56033` is stale.

Published name: `forget-car-still-needed-round2-brief.md`. Results go in
`forget-car-still-needed-round2-results.md`. Evidence: upload `forget-car-still-needed-round2-captures.zip`
to the existing release `rig-evidence-forget-car-still-needed`.

Units: D-HU (head unit) and D-POCO (phone). D-MOTO, D-SAM and D-HP take no part. No hand steps and no
injected taps.

Queue line: `forget-car-still-needed-round2-brief.md` (F `6c23c264` on `fork/fix/forget-car-still-needed`,
C `947b3ae9` = `main`; units D-HU and D-POCO; R1 is the point).

Estimated time: 10 min pre-flight, 25 min to build both arms, 30 min R1, 20 min R4, 10 min thermal waits.
About 1 h 35 min.

**R1 is the point of the round.** R4 is a regression guard that round 1 left at FAIL on one rep.

## 1. Build and baseline

Round 1's three commits, rebased onto `main` `947b3ae9` on 2026-10-26. They are the same patches, and C
moved to the new base with them.
History was rewritten three times (2026-10-17, 2026-10-22, 2026-10-26). Round 1's APKs are neither arm, so build both.

| Arm | Ref | Commit | Tree |
|---|---|---|---|
| F (fix) | `fork/fix/forget-car-still-needed` | `6c23c264931712d51bff48c4d38bd99c090162aa` | `f90896cc2b7961a41f1601e854d863948bf97259` |
| C (control) | `main` | `947b3ae9fae9834aa8e4145ee91751ed743c2ae0` | `e7112fbfdd820cf4834a8a9a5a47f5b62e3ea165` |

The dev host ran the JVM suite at `6c23c264`: 4268 tests, 0 failures. Do not run the unit tests on the rig.

Build both arms in one worktree, F first.

**get_apks.sh**

```bash
OUT=rig-data/rounds/forget-car-still-needed-round2; mkdir -p $OUT
git fetch fork fix/forget-car-still-needed && git fetch origin main
WT=$(ls -d worktrees/*fcsn* 2>/dev/null | head -1)   # else create one with wt-new.sh
git -C $WT checkout --detach 6c23c264931712d51bff48c4d38bd99c090162aa
git -C $WT rev-parse 'HEAD^{tree}'                   # MUST print f90896cc2b7961a41f1601e854d863948bf97259
$RIG_TOOLKIT/rig_thermal.sh wait 70
HUR_DIR=$WT MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh   # copy the APK out of apks/ to $OUT/apk-F.apk at once
git -C $WT checkout --detach 947b3ae9fae9834aa8e4145ee91751ed743c2ae0
git -C $WT rev-parse 'HEAD^{tree}'                   # MUST print e7112fbfdd820cf4834a8a9a5a47f5b62e3ea165
HUR_DIR=$WT MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh   # copy to $OUT/apk-C.apk at once
```

Record both md5s in Setup notes, after `md5sum $OUT/apk-F.apk $OUT/apk-C.apk`. They MUST differ.

Identity, after every install:

- `send ACTION_QUERY_STATE`: the `data=` line MUST carry commit `6c23c264` (F) or `947b3ae9` (C).
- `unzip -p $OUT/live.apk 'classes*.dex' | strings | grep -c HotspotStartSupersededPolicy` MUST be at
  least 1 on F and 0 on C.

Install with `adb -s $HU install -r -d <apk>` on D-HU only. D-HU still carries F from round 1.

## 2. What this is and why

A field unit runs Native AA. Its user switches the Native transport from the head unit's own hotspot to
WiFi Direct. After the switch, no session forms until the user closes the app. In the field log, every
WiFi Direct group create answers `BUSY` within 2 ms. The handshake waits 60 s for credentials and fails.
The next app start logs a framework `stopSoftAp` and then `createGroup SUCCESS` on the first try. So an
access point (AP) of ours was still up after the switch, and it held the radio against the P2P group.

F changes two things that the rig can see:

- **Seam 1.** A hotspot start checks for a stop before each request and on each poll. A start that a stop
  has passed sends no request, or takes its own AP down again. `disableAndAwaitDown` waits up to 1.5 s
  for a start in flight to finish.
- **Seam 2.** When a Native create gets `BUSY` while this unit's AP is up, `WifiDirectManager` takes the AP
  down once and asks for the same group again.

## 3. What round 1 found, and what is different now

Round 1: R1 INCONCLUSIVE, R2 PASS, R3 PASS, R4 FAIL on one rep.

- **R1.** The control (C) did not leak in 5 of 5 reps. Each C rep ended on `The hotspot was switched off
  while this start was still running` with `ap_end=0`. So the stop arrived on C before the AP came up, and
  D-HU dropped the enable by itself. F's guard fired in 5 of 5 reps. Round 1 timed the stop from
  `Setting hotspot enabled=true` (+1.5 s) and from `HotspotManager: Band preference is ` (+1.0 s). Both
  land before or just after the request.
- **The leak needs a later stop.** The stop must reach the framework while D-HU is already starting the AP.
  Then the framework can drop the disable and finish the enable. This round times the stop from the line
  that prints right before the request goes out, and sweeps the delay.
- **The trigger is `SoftApConfigCompat: enableHotspot called (API=`.** It prints at INFO, once per band,
  a few ms before `HotspotManager` posts the start request. Do not use
  `HotspotManager: SoftAp configuration applied; now starting the hotspot.`: D-HU refuses
  `setSoftApConfiguration()` (`rig-quirks/units/D-HU.md`), so that line never prints on D-HU. On D-HU the
  next line is `SoftApConfigCompat: could not configure the access point (` at WARN.
- **The host delay is not the device delay.** The host polls its capture file, and the switch script adds a
  few hundred ms. So each rep reports `off_ms`: the device time from the trigger to the stop line
  `HotspotManager: Setting hotspot enabled=false`. Grade the sweep on `off_ms`, not on the host delay.
- **D-HU may never leak.** The app's own hotspot start rarely brings D-HU's AP up inside its window
  (`rig-quirks/units/D-HU.md`). If no C rep leaks after the sweep, R1 is INCONCLUSIVE by design. That is
  not a failure.
- **The switch sends `ACTION_START_WIRELESS_SCAN` a few seconds after the launch.** §4 step 4 of the
  template forbids that for arming. Here it is the field user's Save (`setActiveFromSettings(force =
  true)`), and it is what the run measures. Keep it.
- **R4 gets a rig-fault rule.** R4-F-2 in round 1 formed no session because D-POCO's own stack logged
  `RFCOMM_CreateConnection failed` on all 4 of its attempts, and D-HU logged no `NativeAA: Connection
  accepted from`. C-2 showed the same fault (69.2 s). A rep with that pair is now void, not FAIL.
- **R4's phone window starts at the switch marker.** Round 1 started it at `<ID>-phone`, so R4-F-1 and
  R4-F-3 read `rfcomm=0` and `tcp=0` on sessions that formed. The phone dials within the 4 s of `ph_up`
  before that marker.
- **Read `rig-quirks/topics/tooling.md`, `rig-quirks/units/D-HU.md`, `rig-quirks/units/D-POCO.md` and
  `rig-quirks/topics/wifi.md` first.** Thermal limits are the table in `tooling.md`.

## 4. Settings keys

Back up D-HU's `settings.xml` at the start (`$OUT/settings-backup.xml`) and diff it against the end. State
the delta in Setup notes. `stat` D-HU's `shared_prefs/` and record owner and mode. Leave the audio keys as
they are: they are a deliberate worst case. Leave `video-profile-starvation-cap` as it is and record it.
No run grades audio or video.

Record, do not change: `native-poke-bt-macs`, `native-poke-all-paired`, `native-driver-selection-mode`,
`native-preferred-device-mac`, `wifi-direct-stable-identity`, `hotspot-band`.

**Base** (every run). `allow-external-configuration` is needed for `ACTION_SET_SETTINGS`.

```xml
<int name="wifi-connection-mode" value="3" />
<int name="log-level" value="2" />
<boolean name="allow-external-configuration" value="true" />
<int name="onboarding-version" value="2" />
<string name="static-bssid">0</string>
<string name="static-p2p-bssid">0</string>
```

**HOT** (hotspot transport at launch; R1 and R4). Also delete `hotspot-teardown-proven-unsafe`.
`hotspot-ssid` MUST be set: with it, D-HU reaches `setSoftApConfiguration()`, refuses it, and posts the
start request.

```xml
<int name="native-ap-transport" value="1" />
<string name="hotspot-interface">wlan2</string>
<string name="hotspot-ssid">OHU-TEST</string>
<string name="hotspot-password">testtest1234</string>
<boolean name="auto-enable-hotspot" value="true" />
```

Log level 2 (INFO) carries every head unit line in section 6.

## 5. Helpers

Run every adb call in sequence, never two at once against one unit. Write the results file after every
run.

**lib.sh**

```bash
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
HU=27870808938846; PH=4f4027e9; MOTO=ZY22GC3BM4
OUT=rig-data/rounds/forget-car-still-needed-round2; mkdir -p $OUT
export LC_ALL=C
send() { a=$1; shift; adb -s $HU shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; }
pnow() { adb -s $PH shell "date '+%m-%d %H:%M:%S.000'" | tr -d '\r'; }
step() { send ACTION_LOG_MARKER --es text "$1" >/dev/null; sleep 0.3; echo "$1 $(pnow)" >> $OUT/phone-marks.txt; }
launch() { adb -s $HU shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity >/dev/null; }
# huexit: only while the app runs (an exit on a stopped app starts the service)
huexit() { adb -s $HU shell am start -a android.intent.action.VIEW -d "headunit://exit" >/dev/null; sleep 5
  adb -s $HU shell am force-stop $PKG; sleep 1; }
hustop() { adb -s $HU shell am force-stop $PKG; sleep 1; }

# host thermal (rig-quirks/topics/tooling.md)
th_gate() { $RIG_TOOLKIT/rig_thermal.sh wait 75 || $RIG_TOOLKIT/rig_thermal.sh wait 75 || { echo HOST_HOT; return 1; }
  $RIG_TOOLKIT/rig_thermal.sh watch $OUT/thermal-$RUN.log & THPID=$!; }
th_end() { kill $THPID 2>/dev/null; wait $THPID 2>/dev/null; }

# the AP on D-HU: apup prints 1 when wlan2 has an IPv4 address, else 0
apup()   { adb -s $HU shell ip -4 addr show wlan2 2>&1 | grep -ac 'inet '; }
ap_on()  { adb -s $HU shell cmd wifi start-softap OHU-TEST wpa2 testtest1234 -b 5; sleep 6; apup; }
ap_off() { adb -s $HU shell cmd wifi stop-softap; sleep 4; apup; }
ph_bt()  { adb -s $PH shell svc bluetooth $1; sleep 4; adb -s $PH shell dumpsys bluetooth_manager | grep -m1 -E "^ *state:"; }
ph_up()  { adb -s $PH shell svc wifi enable; sleep 0.3; ph_bt enable; adb -s $PH shell dumpsys wifi | grep -m1 "Wi-Fi is"; }

# the switch: a marker, the transport write, the force re-arm. Pushed once, run per rep.
cat > $OUT/fc_switch.sh <<'EOF'
R=com.andrerinas.headunitrevived/com.andrerinas.openheadunit.automation.AutomationReceiver
A=com.andrerinas.openheadunit
am broadcast -f 0x00000020 -n $R -a $A.ACTION_LOG_MARKER --es text "$1-switch"
am broadcast -f 0x00000020 -n $R -a $A.ACTION_SET_SETTINGS --es json '{"format":"open-headunit-settings","version":1,"settings":{"native-ap-transport":0}}'
am broadcast -f 0x00000020 -n $R -a $A.ACTION_START_WIRELESS_SCAN
EOF
adb -s $HU push $OUT/fc_switch.sh /data/local/tmp/fc_switch.sh
doswitch() { adb -s $HU shell sh /data/local/tmp/fc_switch.sh "$1" > $OUT/$1-switch-reply.txt; sleep 0.3
  echo "$1-switch $(pnow)" >> $OUT/phone-marks.txt; }

# race <ID> <delay-s>: the switch <delay> s after the trigger first prints, then AP samples at +10 s and +20 s
TRIG="SoftApConfigCompat: enableHotspot called (API="
race() { local n=0; until grep -aqF -- "$TRIG" $OUT/$1-hu.txt; do sleep 0.1; n=$((n+1))
  [ $n -ge 900 ] && { echo "$1 NO_TRIGGER" | tee -a $OUT/races.log; return 1; }; done
  sleep "$2"; doswitch "$1"; echo "$1 switched delay=$2" >> $OUT/races.log
  sleep 9; echo "ap10=$(apup)" > $OUT/$1-ap.txt; sleep 9; echo "ap20=$(apup)" >> $OUT/$1-ap.txt; }

# captures: started before the launch
cap_on() { adb -s $HU logcat -c; adb -s $HU logcat -G 16M; stdbuf -oL adb -s $HU logcat -v threadtime > $OUT/$1-hu.txt & HUPID=$!
  PHPID=; if [ "$2" = phone ]; then stdbuf -oL adb -s $PH logcat -v threadtime -T 1 -b main,system,crash,events > $OUT/$1-phone.txt & PHPID=$!; fi; sleep 2; }
cap_off() { sleep 2; kill $HUPID $PHPID 2>/dev/null; wait $HUPID $PHPID 2>/dev/null; }
waitfor() { local n=0; while [ $n -lt $3 ]; do grep -aqF -- "$2" "$1" && { echo $n; return 0; }; sleep 5; n=$((n+5)); done; echo TIMEOUT; return 1; }

# head unit greps over one rep: capture start to "<ID>-end"
upto() { awk -v e="AutomationMarker: $1-end" '{print} index($0,e){exit}' $OUT/$1-hu.txt; }
ln1()  { upto "$1" | grep -anF -- "$2" | head -1 | cut -d: -f1; }                        # ln1 ID STRING
lnA()  { upto "$1" | awk -v f="$2" -v s="$3" 'NR>f && index($0,s){print NR; exit}'; }     # lnA ID FROMLINE STRING
cntA() { upto "$1" | awk -v f="${2:-0}" -v s="$3" 'NR>f && index($0,s){n++} END{print n+0}'; }
tms()  { upto "$1" | sed -n "${2}p" | awk '{split($2,t,":"); printf "%.3f\n", t[1]*3600+t[2]*60+t[3]}'; }
gap()  { [ -n "$2" ] && [ -n "$3" ] && printf "%.1f\n" "$(echo "$(tms $1 $3) - $(tms $1 $2)" | bc)" || echo none; }
gapms(){ [ -n "$2" ] && [ -n "$3" ] && printf "%.0f\n" "$(echo "($(tms $1 $3) - $(tms $1 $2)) * 1000" | bc)" || echo none; }
END_S=("HotspotManager: Hotspot is up" "An access point came up after its band's window had expired"
  "HotspotManager: Every start path was tried on" "HotspotManager: All hotspot attempts failed."
  "The hotspot was switched off while this start was still running"
  "HotspotManager: a stop arrived before this start asked for" "HotspotManager: a stop arrived while this start was running")
lnEnd() { local m= x s; for s in "${END_S[@]}"; do x=$(lnA "$1" "$2" "$s"); [ -n "$x" ] && { [ -z "$m" ] || [ $x -lt $m ]; } && m=$x; done; echo $m; }
busyA() { echo $(( $(cntA $1 $2 "createGroup failed (BUSY") + $(cntA $1 $2 "the group create got BUSY while") )); }

# phone greps over one window, on the phone's clock from phone-marks.txt
pt()   { grep -aF "$1 " $OUT/phone-marks.txt | tail -1 | cut -d' ' -f2-; }
pwin() { awk -v a="$(pt "$2")" -v b="$(pt "$3")" 'substr($0,1,18)>=a && substr($0,1,18)<=b' $OUT/$1-phone.txt; }
pc()   { pwin "$1" "$2" "$3" | grep -acF -- "$4"; }                                       # pc ID FROM TO STRING

# settings on D-HU, app stopped. Args: "set TYPE KEY VALUE" or "del x KEY". adbd is root.
hu_kv() {
  local IFS=' '
  { echo 'F=/data/data/com.andrerinas.headunitrevived/shared_prefs/settings.xml'
    for kv in "$@"; do set -- $kv
      echo "sed -i -E 's#<[a-z]+ name=\"$3\"[^>]*/>##g' \$F"
      echo "sed -i -E 's#<string name=\"$3\">[^<]*</string>##g' \$F"
      if [ "$1" = set ]; then
        if [ "$2" = string ]; then echo "sed -i 's|</map>|<string name=\"$3\">$4</string></map>|' \$F"
        else echo "sed -i 's|</map>|<$2 name=\"$3\" value=\"$4\" /></map>|' \$F"; fi
      fi
    done
    echo 'chown $(stat -c %u:%g /data/data/com.andrerinas.headunitrevived) $F; chmod 660 $F'
  } > $OUT/fc_kv.sh
  adb -s $HU push $OUT/fc_kv.sh /data/local/tmp/fc_kv.sh >/dev/null
  adb -s $HU shell "sh /data/local/tmp/fc_kv.sh"
}
base() { hu_kv "set int wifi-connection-mode 3" "set int log-level 2" "set boolean allow-external-configuration true" \
  "set int onboarding-version 2" "set string static-bssid 0" "set string static-p2p-bssid 0"; }
hot()  { base; hu_kv "set int native-ap-transport 1" "set string hotspot-interface wlan2" "set string hotspot-ssid OHU-TEST" \
  "set string hotspot-password testtest1234" "set boolean auto-enable-hotspot true" "del x hotspot-teardown-proven-unsafe"; }
hu_read() { adb -s $HU shell run-as $PKG cat shared_prefs/settings.xml > $OUT/settings-$1.xml
  grep -aoE '(native-ap-transport|auto-enable-hotspot|allow-external-configuration|hotspot-teardown-proven-unsafe|hotspot-ssid|log-level)"[^/]*' $OUT/settings-$1.xml; }

# APK identity: pull and hash on the host (a piped md5 over adb is not reliable)
apk_check() { adb -s $HU pull $(adb -s $HU shell pm path $PKG | cut -d: -f2 | tr -d '\r') $OUT/live.apk >/dev/null 2>&1
  local m=$(md5sum $OUT/live.apk | cut -d' ' -f1); echo "$(date +%T) live=$m want=$WANT_MD5" | tee -a $OUT/apk-check.log
  [ "$m" = "$WANT_MD5" ] || { echo APK_MISMATCH | tee -a $OUT/apk-check.log; return 1; }; }
```

## 6. The deciding lines

Each head unit string was checked with `grep -F -r` on `app/src/main` at `6c23c264`, and at `947b3ae9`
where the table says C. All print at INFO or WARN. Lines with a run-time value are listed by their
run-time text here and by their source fragment in the `decisive-strings` block.

**Head unit capture** (`$OUT/<ID>-hu.txt`, capture start to `<ID>-end`):

| Grep (run-time text) | Arms | Meaning |
|---|---|---|
| `SoftApConfigCompat: enableHotspot called (API=` | F, C | R1 trigger: the start is about to post its request |
| `SoftApConfigCompat: could not configure the access point (` | F, C | D-HU refused the configuration; the request follows (record) |
| `Setting hotspot enabled=true` / `Setting hotspot enabled=false` | F, C | a start, and a stop (source `HotspotManager: Setting hotspot enabled=`) |
| `trying to switch this device's hotspot on` | F, C | the hotspot route asks for its own AP |
| `ensuring hotspot is disabled first...` | F, C | the WiFi Direct bring-up stops the hotspot |
| `HotspotManager: Hotspot is up` / `An access point came up after its band's window had expired` | F, C | the start reports an AP up |
| `HotspotManager: Every start path was tried on` / `HotspotManager: All hotspot attempts failed.` | F, C | the start ends with no AP |
| `The hotspot was switched off while this start was still running` | C only | the old stop check |
| `HotspotManager: a stop arrived before this start asked for` | F only | the start sends no request |
| `HotspotManager: a stop arrived while this start was running` | F only | the start takes its own AP down again |
| `HotspotManager: a start was still running` | F only | the 1.5 s drain expired (WARN) |
| `HotspotManager: the access point was still up` | F, C | the stop did not bring the AP down in 8 s (WARN) |
| `WifiDirectManager: Attempting createGroup for Native AA` | F, C | a Native create starts |
| `createGroup SUCCESS` | F, C | the group formed |
| `createGroup failed (BUSY` | F, C | a create got `BUSY` (source `createGroup failed (` plus `BUSY (System is busy, retry needed)`) |
| `the group create got BUSY while` | F only | the backstop takes the AP down |
| `NativeAA: ACTIVELY LISTENING on Android Auto UUID` | F, C | the Bluetooth listeners are open (R4 settle line) |
| `NativeAA: advertising WPP over TCP at` | F, C | an endpoint went out (MUST be 0) |
| `NativeAA: Connection accepted from` | F, C | the phone came back over Bluetooth |
| `SSL handshake complete` | F, C | a session formed (INFO form) |
| `Throughput over` | F, C | video decoded |
| `Handshake failed - No WiFi credentials available after` | F, C | the field symptom's end line |
| `MATCH! Starting AapService` | F, C | a self wake (record) |
| `AutomationReceiver: ` / `AutomationMarker: ` | F, C | a verb or a marker landed; a missing line voids the step |

**Phone capture** (`$OUT/<ID>-phone.txt`, phone clock window from `phone-marks.txt`). Record the Gearhead
version (`adb -s $PH shell dumpsys package com.google.android.projection.gearhead | grep versionName`).
Round 1 read `18.0.164204-release.daily`. A 0 can mean a renamed string. Then mark the condition "string
not seen", not FAIL.

| Grep | Meaning |
|---|---|
| `Critical error` | Android Auto ended a session on a protocol fault (MUST be 0 in R4) |
| `Creating rfcomm socket for device:` | the phone ran the Bluetooth handshake |
| `WPP on TCP connected to the WiFi network` | the phone reached a stored TCP endpoint |
| `RFCOMM_CreateConnection failed` | the phone's own stack could not open RFCOMM (rig fault, see R4) |

**AP state on D-HU** is `apup` (1 or 0), read at fixed points. It is not a log line.

## 7. Runs

### Pre-flight (read only)

1. `sed -n '/^## Pick a tool/,/^## Rig and devices/p' $RIG_TOOLKIT/TOOLS.md`. Record which tools you used.
2. `$RIG_TOOLKIT/rig_preflight.sh D_HU:wifi,bt D_POCO:wifi,bt`.
3. D-MOTO: `adb -s $MOTO shell svc bluetooth disable` for the whole round, so no poke goes to it.
4. D-HU station: `adb -s $HU shell dumpsys wifi | grep -iE "mWifiInfo|SSID|Frequency" | head`. Record SSID
   and frequency. Round 1 read `Pegue Cdesta` at 5745 MHz.
5. D-HU: `adb -s $HU shell appops get $PKG WRITE_SETTINGS`. Record it. `adb -s $HU shell svc power stayon true`.
6. D-POCO: `adb -s $PH shell dumpsys wifip2p | grep isGroupOwner` MUST be false (else `svc wifi disable`,
   `svc wifi enable`). `adb -s $PH shell dumpsys window | grep mCurrentFocus`: at home (else
   `input keyevent KEYCODE_HOME`). `adb -s $PH shell dumpsys bluetooth_manager | grep -a -A8 'Bonded devices'`:
   record it.
7. D-HU: `ap_off` MUST print 0. `ap_on` MUST print 1. `ap_off` MUST print 0.
8. `hustop`. Back up `settings.xml` to `$OUT/settings-backup.xml`. `stat` `shared_prefs/`.

### Block C: install C

`hustop`; `adb -s $HU install -r -d $OUT/apk-C.apk`; `WANT_MD5=<md5 of apk-C>`; `apk_check`; `launch`;
`sleep 5`; `send ACTION_QUERY_STATE` (commit `947b3ae9`); `huexit`. Read back the base keys
(`hu_read C0`): an install can wipe `settings.xml`. Then run R1 (C), then R4 (C).

### Block F: install F

The same, with `apk-F.apk` and commit `6c23c264`. Then run R1 (F) if R1's rule below calls for it, then
R4 (F).

### R1. The switch lands while D-HU is starting the AP (THE POINT)

Phone capture: no. D-POCO's Bluetooth stays off, so the run grades the group only.

IDs `R1-<arm>-d<ms>-<n>`, for example `R1-C-d300-2`. Host delays `d`: 0, 300, 600 and 900 ms.

**Stop rule.**

1. On C, run 2 reps at each delay in order: d0, d300, d600, d900. After each delay, grade both reps.
2. At the first delay with a C rep that leaked (rule below), stop the sweep. That delay is `D`.
3. Run C at `D` until it has 5 reached reps in all. Then run F at `D` for 5 reached reps.
4. If no C rep leaks after all 8 sweep reps, stop R1. Do not run F. R1 is INCONCLUSIVE.
5. A void rep does not count. Re-run it, at most 2 extra reps per arm and delay.

**r1_rep.sh** (set `ID` and `DLY`, in seconds, for example `ID=R1-C-d300-1; DLY=0.3`)

```bash
RUN=$ID th_gate || exit 1; apk_check || exit 1
hustop; ap_off                       # MUST print 0
hot; hu_read $ID-pre                 # native-ap-transport 1, auto-enable-hotspot true, hotspot-ssid OHU-TEST
ph_bt disable                        # MUST read state: OFF
cap_on $ID
launch; sleep 1; step $ID-start
race $ID $DLY                        # takes about 20 s after the switch
sleep 25                             # the window this run measures: 45 s from the switch
echo "ap_end=$(apup)" >> $OUT/$ID-ap.txt
step $ID-end; cap_off; huexit; ap_off; th_end
```

**r1_grade.sh** (set `ID`). Print one line per rep.

```bash
Lt=$(ln1 $ID "SoftApConfigCompat: enableHotspot called (API=")
Lm=$(ln1 $ID "AutomationMarker: $ID-switch")
Lw=$([ -n "$Lt" ] && lnA $ID "$Lt" "HotspotManager: Setting hotspot enabled=false")
Le=$([ -n "$Lt" ] && lnEnd $ID $Lt)
reached=no; [ -n "$Lt" ] && [ -n "$Lw" ] && { [ -z "$Le" ] || [ $Lw -lt $Le ]; } && reached=yes
off_ms=$(gapms $ID "$Lt" "$Lw")
endline=$([ -n "$Le" ] && upto $ID | sed -n "${Le}p" | cut -c1-160)
guard=$(( $(cntA $ID "$Lm" "HotspotManager: a stop arrived before this start asked for") + $(cntA $ID "$Lm" "HotspotManager: a stop arrived while this start was running") ))
late_up=$(( $(cntA $ID "$Lm" "HotspotManager: Hotspot is up") + $(cntA $ID "$Lm" "An access point came up after its band's window had expired") ))
busy=$(busyA $ID "$Lm"); creates=$(cntA $ID "$Lm" "createGroup SUCCESS")
succ_s=$(gap $ID "$Lm" "$(lnA $ID "$Lm" "createGroup SUCCESS")")
cred60=$(cntA $ID 0 "Handshake failed - No WiFi credentials available after")
stillup=$(cntA $ID 0 "HotspotManager: the access point was still up")
drain=$(cntA $ID 0 "HotspotManager: a start was still running")
verbs=$(( $(cntA $ID 0 "AutomationReceiver: com.andrerinas.openheadunit.ACTION_SET_SETTINGS") + $(cntA $ID 0 "AutomationReceiver: com.andrerinas.openheadunit.ACTION_START_WIRELESS_SCAN") ))
imported=$(grep -ac 'imported":1' $OUT/$ID-switch-reply.txt)
echo "$ID reached=$reached off_ms=$off_ms guard=$guard late_up=$late_up busy=$busy creates=$creates succ_s=$succ_s $(tr '\n' ' ' < $OUT/$ID-ap.txt)cred60=$cred60 stillup=$stillup drain=$drain verbs=$verbs imported=$imported end=[$endline]"
```

- **Void:** `NO_TRIGGER` in `races.log`, or `verbs` below 2, or `imported` = 0.
- **Not reached:** `reached=no`. The start ended (for example `Hotspot is up`) before the stop line. Record it.
  It does not count toward the 5.
- Do not discard a rep for a second `createGroup SUCCESS`. A late AP that breaks the group is the defect.

**C rep, leaked** (reached rep): `late_up` >= 1, or `ap10=1`, or `ap20=1`, or `ap_end=1`, or `busy` >= 1, or
`cred60` >= 1. The group forms about 1.6 s after the switch, so an AP up 10 s after the switch came up after
the stop.

**F rep** (reached rep):

- PASS: `guard` >= 1, `late_up` = 0, `ap10=0`, `ap20=0`, `ap_end=0`, `cred60` = 0, and `succ_s` <= 30.
- FAIL: `guard` = 0, or `late_up` >= 1, or any `ap*=1`, or `cred60` >= 1, or `succ_s` is `none` or above 30.

**R1 verdict.**

- PASS: C leaked in at least 1 reached rep at `D`, F has 5 reached reps at `D`, and every reached F rep passes.
- FAIL: any reached F rep fails.
- INCONCLUSIVE: no C rep leaked in the sweep. Write "the leak does not reproduce on D-HU at `off_ms` from N to
  M ms; F not run". Give N and M, the smallest and largest `off_ms` of the reached C reps. Also INCONCLUSIVE:
  F has fewer than 3 reached reps after its extra reps.

What a PASS would look like if F did nothing: `ap_end=0` and a fast `succ_s` can both come from D-HU's
platform stopping the AP for P2P. So `guard` >= 1 is required, and C's leak at the same delay is what proves
the window was the field one.

### R4. Regression: a switch with the AP settled, then a session over WiFi Direct

Phone capture: yes. IDs `R4-<arm>-<n>`. C: 2 graded reps. F: 3 graded reps. A void rep does not count.
Re-run it, at most 2 extra reps per arm.

**r4_rep.sh** (set `ID`)

```bash
RUN=$ID th_gate || exit 1; apk_check || exit 1
hustop; ap_on                        # MUST print 1
hot; hu_read $ID-pre
ph_bt disable                        # no hotspot session, so no endpoint
cap_on $ID phone
launch; sleep 1; step $ID-start
waitfor $OUT/$ID-hu.txt "NativeAA: ACTIVELY LISTENING on Android Auto UUID" 60; sleep 5
doswitch $ID
sleep 15; echo "ap_after_switch=$(apup)" > $OUT/$ID-ap.txt
ph_up; step $ID-phone
waitfor $OUT/$ID-hu.txt "SSL handshake complete" 120
sleep 15; step $ID-end; cap_off; huexit; ap_off; th_end
```

If the listener line times out, record it and go on. The switch still runs.

**r4_grade.sh** (set `ID`)

```bash
Lm=$(ln1 $ID "AutomationMarker: $ID-switch"); Lp=$(ln1 $ID "AutomationMarker: $ID-phone")
Lssl=$([ -n "$Lp" ] && lnA $ID "$Lp" "SSL handshake complete")
echo "$ID ens=$(cntA $ID "$Lm" 'ensuring hotspot is disabled first...') auto_on=$(cntA $ID 0 "trying to switch this device's hotspot on") drain=$(cntA $ID 0 'HotspotManager: a start was still running') busy=$(busyA $ID "$Lm") backstop=$(cntA $ID "$Lm" 'the group create got BUSY while') succ_s=$(gap $ID "$Lm" "$(lnA $ID "$Lm" 'createGroup SUCCESS')") ssl_s=$(gap $ID "$Lp" "$Lssl") thr=$([ -n "$Lssl" ] && cntA $ID "$Lssl" 'Throughput over' || echo 0) accepted=$(cntA $ID "$Lm" 'NativeAA: Connection accepted from') adv=$(cntA $ID 0 'NativeAA: advertising WPP over TCP at') imported=$(grep -ac 'imported":1' $OUT/$ID-switch-reply.txt) $(cat $OUT/$ID-ap.txt) crit=$(pc $ID $ID-switch $ID-end 'Critical error') rfcomm=$(pc $ID $ID-switch $ID-end 'Creating rfcomm socket for device:') tcp=$(pc $ID $ID-switch $ID-end 'WPP on TCP connected to the WiFi network') rfail=$(pc $ID $ID-switch $ID-end 'RFCOMM_CreateConnection failed')"
```

- **Void:** `imported` = 0, or `auto_on` >= 1 (the shell AP was not up, so the AP was not settled).
- **Void, rig fault:** `ssl_s` is `none`, `rfail` >= 1 and `accepted` = 0. The phone's stack could not open
  RFCOMM and the head unit never saw a dial. Record it with its `rfail` count.
- **PASS** (F, every graded rep): `ens` >= 1, `drain` = 0, `busy` = 0, `succ_s` <= 30, `ssl_s` <= 120,
  `thr` >= 1, `adv` = 0, `crit` = 0, and `rfcomm` + `tcp` >= 1.
- **FAIL** (F): any PASS condition missed in a graded rep. If C misses the same condition, say so beside the
  FAIL.
- `busy` >= 1 with `backstop` = 0 and `ap_after_switch=1` on F is a FAIL of seam 2.
- If `rfcomm` + `tcp` = 0 on a rep with a session, the string may have moved. Then mark it "string not seen"
  and grade the rest.

### End of round

`huexit` if the app runs, `hustop`, `ap_off` (MUST print 0). Restore `$OUT/settings-backup.xml` with
`$RIG_TOOLKIT/restore_settings.sh` and diff it: MUST be empty. D-POCO: `ph_up`, `isGroupOwner` false.
D-MOTO: `svc bluetooth enable`. D-HU: `svc power stayon false`.

## 8. Do not re-run

- R2, the BUSY backstop: PASS in round 1 (variant B, 3 of 3, `createGroup SUCCESS` 0.2 to 0.3 s after the
  backstop line).
- R3, a user exit from a hotspot session: PASS in round 1. `hotspot-teardown-proven-unsafe` is written on
  both arms because D-HU's restart never comes back.
- R1 with the stop before the request: F's guard fired 5 of 5 in round 1, on both early windows.
- The status pill on the external module route. No rig unit has the module. Unit tests decide it.
- Whether D-POCO dials a stored WPP-over-TCP endpoint after a transport switch. It is parked.

## 9. Report back

1. R1: one row per rep, with `off_ms`, `reached`, `guard`, `late_up`, `ap10`, `ap20`, `ap_end`, `busy`,
   `succ_s` and the `end` line. Then `D`, the count of C reps that leaked, and the count of F reps that
   passed. If no C rep leaked, the `off_ms` range of the reached C reps.
2. R4: `ssl_s` per graded rep on both arms, and every void rep with its `rfail` and `accepted` counts.
3. Any rep on either arm with `cred60` >= 1 or `busy` >= 1: its ID and the line numbers of the first `BUSY`
   and of `createGroup SUCCESS`.
