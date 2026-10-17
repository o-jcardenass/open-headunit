# forget-car-still-needed, round 1 brief

Published name: `forget-car-still-needed-round1-brief.md`. Results go in
`forget-car-still-needed-round1-results.md`. Evidence: create the release
`rig-evidence-forget-car-still-needed` and upload `forget-car-still-needed-round1-captures.zip` to it.

Units: D-HU (head unit) and D-POCO (phone). D-MOTO, D-SAM and D-HP take no part.

Estimated time: 15 min build and pre-flight, 40 min block C (control), 50 min block F (fix). About 1 h 45 min.

**R1 is the point of the round.** R2 is best effort. R3 and R4 are regression guards.

## 1. Build and baseline

Two arms. History was not rewritten. This is the thread's first round.

| Arm | Ref | Commit | Tree |
|---|---|---|---|
| F (fix) | `fork/fix/forget-car-still-needed` | `25a56033de5b133e4ce022a52c1815fa71e02a30` | `462d65406c8e2a695da3ed11cbba01e14b1f8f42` |
| C (control) | `main` | `d2d0a6bf71217e8658545314de63ed87c0031451` | `50bd3597ac9702179bffccfed53fa3c295016970` |

F is three commits on C: `60a35f43` (a hotspot stop wins over a start in flight), `7a91352b` (the
WiFi Direct BUSY backstop), `25a56033` (the status pill on the external module route).

Build both arms in ONE worktree, C first, then F.

```bash
git fetch fork fix/forget-car-still-needed
git fetch origin main                      # C is on upstream main; fork carries it on several branches too
WT=$(rig-toolkit/wt-new.sh forget-car-still-needed scratch d2d0a6bf71217e8658545314de63ed87c0031451 | tail -1)   # also copies local.properties
git -C $WT rev-parse 'HEAD^{tree}'   # C: MUST print 50bd3597ac9702179bffccfed53fa3c295016970
# build C (below), copy its APK out, then:
git -C $WT checkout --detach 25a56033de5b133e4ce022a52c1815fa71e02a30
git -C $WT rev-parse 'HEAD^{tree}'   # F: MUST print 462d65406c8e2a695da3ed11cbba01e14b1f8f42
# build F (below), copy its APK out
```

Build each arm with `rig-toolkit/build_hur_cool.sh` (`HUR_DIR=$WT MAX_WORKERS=2`)
after `th_wait 70`. Run each tree check right after its checkout. Copy each APK out
of `apks/` at once, to `$OUT/apk-C.apk` and `$OUT/apk-F.apk`, because `build_hur.sh` deletes the previous
one. Record both md5s. They MUST differ.

F's 3264 JVM tests passed on the dev host at this SHA, so the rig checks APK identity only and runs no
unit tests. A build failure stops the round.

Identity, after every install:

- `send ACTION_QUERY_STATE`: the `data=` line MUST carry commit `25a56033` (F) or `d2d0a6bf` (C).
- DEX symbol: `unzip -p $OUT/live.apk 'classes*.dex' | strings | grep -c HotspotStartSupersededPolicy`
  MUST be at least 1 on F and 0 on C.

Install with `adb -s $HU install -r <apk>` on D-HU only. D-POCO needs no install.

## 2. What this is and why

A field unit runs Native AA. Its user switches the Native transport from the head unit's own hotspot to
WiFi Direct. After the switch, no session forms until the user closes the app. The field log shows
why. Every WiFi Direct group create answers `BUSY` within 2 ms, the handshake waits 60 s for
credentials and fails, and the next app start logs a framework `stopSoftAp` and then
`createGroup SUCCESS` on the first try. So a SoftAP of ours was still up after the switch, and it held
the radio against the P2P group.

The code read found two leaks and a gap:

1. `HotspotManager.setHotspotEnabled(true)` looked for a stop only after a band failed. A stop that
   arrives mid-start does not stop the request, and the access point (AP) comes up after the stop.
2. `HotspotManager.disableAndAwaitDown` reads "down" at once while a start has posted its request but
   the AP is not up yet. So the WiFi Direct bring-up goes ahead into the AP that comes up next.
3. Nothing on the `BUSY` path reads the AP, so the create ladder spends itself against it until the app
   restarts.

What F changes:

- **Seam 1.** A start checks the stop generation before each request and on each poll. A start that a
  stop has passed sends no request, or takes its own AP down again. `disableAndAwaitDown` and
  `restart` wait up to 1.5 s for a start in flight to finish. `restart` re-enables only if no other stop
  came during its settle.
- **Seam 2.** When a Native create gets `BUSY` while this unit's AP is up, `WifiDirectManager` takes the
  AP down once per bring-up and asks for the same group again.
- **Seam 3** (the status pill on the external module route) needs no rig. No rig unit has an external
  Bluetooth module. Unit tests decide it.

## 3. What is different about this round

- **Read `rig-quirks/topics/tooling.md`, `rig-quirks/units/D-HU.md`, `rig-quirks/units/D-POCO.md` and
  `rig-quirks/topics/wifi.md` first.**
- **The switch is two verbs on a running app, not a `settings.xml` edit.** A field user switches in the
  settings screen, which ends in `setActiveFromSettings(force = true)`. `ACTION_START_WIRELESS_SCAN`
  calls the same method with `force = true`. `ACTION_SET_SETTINGS` writes `native-ap-transport` = `0`
  into the live preferences first. Both go out from one pushed script (`fc_switch.sh`), so the gap
  between them is a few hundred ms. That is why this round needs `allow-external-configuration` =
  `true`. Record the `data=` reply of `ACTION_SET_SETTINGS`. It MUST contain `"imported":1`.
- **The race is timed from the capture.** A watcher polls the capture every 0.1 s and runs the switch a
  fixed delay after a trigger line. R1a lands the stop in the start's WiFi-disable wait. R1b lands it
  after the start has asked for an AP. A rep whose trigger line never prints in 90 s is void, not a FAIL.
- **D-HU cannot hold a SoftAP and a P2P group owner at once** (`rig-quirks/units/D-HU.md`). The
  platform itself may stop a leftover AP when the P2P interface is asked for. So the control arm may
  not show the field unit's BUSY loop at all. R1 then grades F as a guard only, and its verdict is
  INCONCLUSIVE for the field symptom. Say so plainly. It is not a failure.
- **The app's own hotspot start rarely brings D-HU's AP up inside its window**
  (`rig-quirks/units/D-HU.md`). R1 grades the order of lines and the AP state at the end, never whether
  the app raised the AP.
- **D-HU's station stays joined to `Pegue Cdesta` (5500 MHz).** That is what makes the WiFi-disable wait
  last up to 8 s, which R1a needs. Verify it in pre-flight.
- **R3 restarts the AP the session used, and D-HU may not bring it back.** When the restart fails,
  `AapService` writes `hotspot-teardown-proven-unsafe` = `true`, on both arms. Delete that key before
  every rep. Then the script stops and starts the shell AP before the reconnect, so the second session
  does not depend on the restart.
- **D-HU's first `start-softap` after a reboot comes up degraded.** If `start-softap` prints
  `Soft AP failed to start`, run `ap_off` then `ap_on` once.
- **Hotspot sessions run last in each block (R3).** D-HU's AP address moves on every start, so no
  endpoint should go out. R3 checks `NativeAA: advertising WPP over TCP at` = 0. If it is not 0, see R3.
- **R1, R2 and the first half of R4 keep D-POCO's Bluetooth off.** Those steps grade the group only, so
  they need no phone capture. R3 and R4 reach the phone's Android Auto and capture the phone.

## 4. Settings keys

Back up D-HU's `settings.xml` at the start (`$OUT/settings-backup.xml`) and diff it against the end. State
the delta in Setup notes. `stat` D-HU's `shared_prefs/` directory and record owner and mode
(`rig-quirks/units/D-HU.md`). Leave the audio keys as they are: they are a deliberate worst case.
Leave `video-profile-starvation-cap` as it is and record it. No run grades audio or video.

Record, do not change: `native-poke-bt-macs`, `native-poke-all-paired`, `native-driver-selection-mode`,
`native-preferred-device-mac`, `wifi-direct-stable-identity`, `hotspot-band`.

**Base** (every run):

```xml
<int name="wifi-connection-mode" value="3" />
<int name="log-level" value="2" />
<boolean name="allow-external-configuration" value="true" />
<int name="onboarding-version" value="2" />
<string name="static-bssid">0</string>
<string name="static-p2p-bssid">0</string>
```

**HOT** (hotspot transport; R1, R3, R4 at launch). Also delete `hotspot-teardown-proven-unsafe`.

```xml
<int name="native-ap-transport" value="1" />
<string name="hotspot-interface">wlan2</string>
<string name="hotspot-ssid">OHU-TEST</string>
<string name="hotspot-password">testtest1234</string>
<boolean name="auto-enable-hotspot" value="true" />
```

**WFD** (WiFi Direct transport; R2 at launch): `native-ap-transport` = `0`, `auto-enable-hotspot` =
`false`, and delete `hotspot-ssid`, `hotspot-password` and `hotspot-interface`.

Log level 2 (INFO) carries every head unit line in section 6. None of them sits behind a
`LOG_VERBOSE` guard.

## 5. Helpers

Run every adb call in sequence, never two at once against one unit. Copy `th_pkg`, `th_thr`, `th_wait`,
`th_watch`, `th_gate` and `th_report` from `archive/rounds/projection-teardown-and-relays-round1-brief.md`
(`ptr_lib.sh`). Call `RUN=<id> th_gate` before each rep and report `th_report <id>`. Write the results
file after every run.

```bash
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
HU=27870808938846; PH=4f4027e9; MOTO=ZY22GC3BM4
OUT=rig-data/rounds/forget-car-still-needed-round1; mkdir -p $OUT
send() { a=$1; shift; adb -s $HU shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; }
pnow() { adb -s $PH shell "date '+%m-%d %H:%M:%S.000'" | tr -d '\r'; }
# step <label>: a head unit marker, and the phone's clock at that moment in phone-marks.txt
step() { send ACTION_LOG_MARKER --es text "$1" >/dev/null; sleep 0.3; echo "$1 $(pnow)" >> $OUT/phone-marks.txt; }
launch() { adb -s $HU shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity >/dev/null; }
# huexit: only while the app runs (an exit on a stopped app starts the service)
huexit() { adb -s $HU shell am start -a android.intent.action.VIEW -d "headunit://exit" >/dev/null; sleep 5
  adb -s $HU shell am force-stop $PKG; sleep 1; }
hustop() { adb -s $HU shell am force-stop $PKG; sleep 1; }

# the access point on D-HU: apup prints 1 when wlan2 has an IPv4 address, else 0
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
# race <ID> <trigger> <delay-s>: the switch, <delay> s after the trigger first prints in this rep's capture
race() { local n=0; until grep -aqF -- "$2" $OUT/$1-hu.txt; do sleep 0.1; n=$((n+1))
  [ $n -ge 900 ] && { echo "$1 NO_TRIGGER" | tee -a $OUT/races.log; return 1; }; done
  sleep "$3"; doswitch "$1"; echo "$1 switched" >> $OUT/races.log; }

# captures: started before the launch. The phone capture runs only where a run says so.
cap_on() { adb -s $HU logcat -c; adb -s $HU logcat -G 16M; stdbuf -oL adb -s $HU logcat -v threadtime > $OUT/$1-hu.txt & HUPID=$!
  PHPID=; if [ "$2" = phone ]; then stdbuf -oL adb -s $PH logcat -v threadtime -T 1 -b main,system,crash,events > $OUT/$1-phone.txt & PHPID=$!; fi; sleep 2; }
cap_off() { sleep 2; kill $HUPID $PHPID 2>/dev/null; wait $HUPID $PHPID 2>/dev/null; }
waitfor() { local n=0; while [ $n -lt $3 ]; do grep -aqF -- "$2" "$1" && { echo $n; return 0; }; sleep 5; n=$((n+5)); done; echo TIMEOUT; return 1; }

# head unit greps over one rep: capture start to "<ID>-end"
upto() { awk -v e="AutomationMarker: $1-end" '{print} index($0,e){exit}' $OUT/$1-hu.txt; }
ln1()  { upto "$1" | grep -anF -- "$2" | head -1 | cut -d: -f1; }                       # ln1 ID STRING
lnA()  { upto "$1" | awk -v f="$2" -v s="$3" 'NR>f && index($0,s){print NR; exit}'; }    # lnA ID FROMLINE STRING
cntA() { upto "$1" | awk -v f="${2:-0}" -v s="$3" 'NR>f && index($0,s){n++} END{print n+0}'; }  # cntA ID FROMLINE STRING
tsec() { upto "$1" | sed -n "${2}p" | awk '{split($2,t,":"); printf "%.1f\n", t[1]*3600+t[2]*60+t[3]}'; }
gap()  { [ -n "$2" ] && [ -n "$3" ] && echo "$(tsec $1 $3) - $(tsec $1 $2)" | bc || echo none; }   # gap ID LINE_A LINE_B
END_S=("HotspotManager: Hotspot is up" "An access point came up after its band's window had expired"
  "HotspotManager: Every start path was tried on" "HotspotManager: All hotspot attempts failed."
  "The hotspot was switched off while this start was still running"
  "HotspotManager: a stop arrived before this start asked for" "HotspotManager: a stop arrived while this start was running")
lnEnd() { local m= x s; for s in "${END_S[@]}"; do x=$(lnA "$1" "$2" "$s"); [ -n "$x" ] && { [ -z "$m" ] || [ $x -lt $m ]; } && m=$x; done; echo $m; }
busyA() { echo $(( $(cntA $1 $2 "createGroup failed (BUSY") + $(cntA $1 $2 "the group create got BUSY while") )); }

# phone greps over one window, on the phone's clock from phone-marks.txt
pt()   { grep -aF "$1 " $OUT/phone-marks.txt | tail -1 | cut -d' ' -f2-; }
pwin() { awk -v a="$(pt "$2")" -v b="$(pt "$3")" 'substr($0,1,18)>=a && substr($0,1,18)<=b' $OUT/$1-phone.txt; }
pc()   { pwin "$1" "$2" "$3" | grep -acF -- "$4"; }                                      # pc ID FROM TO STRING

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
wfd()  { base; hu_kv "set int native-ap-transport 0" "set boolean auto-enable-hotspot false" \
  "del x hotspot-ssid" "del x hotspot-password" "del x hotspot-interface"; }
hu_read() { adb -s $HU shell run-as $PKG cat shared_prefs/settings.xml > $OUT/settings-$1.xml
  grep -aoE '(native-ap-transport|auto-enable-hotspot|allow-external-configuration|hotspot-teardown-proven-unsafe|log-level)"[^/]*' $OUT/settings-$1.xml; }

# APK identity: pull and hash on the host (a piped md5 over adb is not reliable)
apk_check() { adb -s $HU pull $(adb -s $HU shell pm path $PKG | cut -d: -f2 | tr -d '\r') $OUT/live.apk >/dev/null 2>&1
  local m=$(md5sum $OUT/live.apk | cut -d' ' -f1); echo "$(date +%T) live=$m want=$WANT_MD5" | tee -a $OUT/apk-check.log
  [ "$m" = "$WANT_MD5" ] || { echo APK_MISMATCH | tee -a $OUT/apk-check.log; return 1; }; }
```

## 6. The deciding lines

Each head unit string was checked with `grep -F -r` on `app/src/main` at `25a56033`. All print at INFO or
WARN. Two lines carry a run-time value. The runs grep the run-time text, and the `decisive-strings`
block lists the source fragment. One control-arm string does not exist on F and is checked at
`d2d0a6bf` instead. Phone and platform strings are in their own blocks.

**Head unit capture** (`$OUT/<ID>-hu.txt`, capture start to `<ID>-end`):

| Grep (run-time text) | Meaning |
|---|---|
| `Setting hotspot enabled=true` | a hotspot start begins (source `HotspotManager: Setting hotspot enabled=`) |
| `HotspotManager: Band preference is ` | the start is about to ask for an AP (R1b trigger) |
| `trying to switch this device's hotspot on` | the hotspot route asks for its own AP |
| `ensuring hotspot is disabled first...` | the WiFi Direct bring-up stops the hotspot (the switch landed) |
| `HotspotManager: Hotspot is up` / `An access point came up after its band's window had expired` | the start reports an AP up |
| `HotspotManager: Every start path was tried on` / `HotspotManager: All hotspot attempts failed.` | the start ends with no AP |
| `The hotspot was switched off while this start was still running` | C only: the old stop check |
| `HotspotManager: a stop arrived before this start asked for` | F: the start sends no request |
| `HotspotManager: a stop arrived while this start was running` | F: the start takes its own AP down again |
| `HotspotManager: a start was still running` | F: the 1.5 s drain expired (WARN) |
| `HotspotManager: the access point was still up` | the stop did not bring the AP down in 8 s (WARN) |
| `WifiDirectManager: Attempting createGroup for Native AA` | a Native create starts |
| `createGroup SUCCESS` | the group formed |
| `createGroup failed (BUSY` | a create got `BUSY` (source: `createGroup failed (` plus `BUSY (System is busy, retry needed)`) |
| `the group create got BUSY while` | F: the backstop takes the AP down (full text in the block) |
| `access point take-down finished (down=true)` | F: the backstop's take-down ended with the AP down |
| `this unit's access point is up again after the app took it down` | F: something raised the AP again (WARN) |
| `CommManager teardown complete. Restarting the hotspot so the phone leaves the network.` | a user exit restarts the AP |
| `HotspotManager: the restarted access point held for` | restart outcome: held |
| `HotspotManager: the restarted access point fell within` | restart outcome: fell, then one more ask |
| `would not come back up` | restart outcome: failed |
| `so it will not be taken down again` | `AapService` wrote `hotspot-teardown-proven-unsafe` |
| `HotspotManager: another stop arrived while the hotspot was down for a restart` | F: the restart skipped its re-enable |
| `NativeAA: advertising WPP over TCP at` | an endpoint went out (MUST be 0) |
| `NativeAA: Connection accepted from` | the phone came back over Bluetooth |
| `SSL handshake complete` | a session formed (INFO form) |
| `Throughput over` | video decoded |
| `Handshake failed - No WiFi credentials available after` | the field symptom's end line (record only) |
| `MATCH! Starting AapService` | a self wake (record; discard only with group churn) |
| `AutomationReceiver: ` / `AutomationMarker: ` | a verb or a marker landed; a missing line voids the step |

**Phone capture** (`$OUT/<ID>-phone.txt`, phone clock window from `phone-marks.txt`). Report the
Gearhead version (`adb -s $PH shell dumpsys package com.google.android.projection.gearhead | grep versionName`).
A 0 can mean a renamed string. Then mark that condition "string not seen", not FAIL.

| Grep | Meaning |
|---|---|
| `Critical error` | Android Auto ended a session on a protocol fault (MUST be 0) |
| `Creating rfcomm socket for device:` | the phone ran the Bluetooth handshake |
| `WPP on TCP connected to the WiFi network` | the phone reached a stored TCP endpoint |
| `No WPP on TCP configuration found in storage` | the phone holds no endpoint (record only) |

**AP state on D-HU** is `apup` (1 or 0), read at a fixed point in each rep. It is not a log line.

## 7. Runs

### Pre-flight (read only, before block C)

1. Read `rig-toolkit/TOOLS.md`. Record which scripts you used.
2. `rig-toolkit/rig_preflight.sh D_HU:wifi,bt D_POCO:wifi,bt`.
3. D-MOTO: `adb -s $MOTO shell svc bluetooth disable` for the whole round, so no poke goes to it.
4. D-HU station: `adb -s $HU shell dumpsys wifi | grep -iE "mWifiInfo|SSID|Frequency" | head`. MUST show
   `Pegue Cdesta`. If not, R1a is INCONCLUSIVE by design. Run it anyway and say so.
5. D-HU: `adb -s $HU shell appops get $PKG WRITE_SETTINGS`. Record it. `adb -s $HU shell svc power stayon true`.
6. D-HU: `adb -s $HU shell cmd wifip2p list-saved-groups`. Record it.
7. D-POCO: `adb -s $PH shell dumpsys wifip2p | grep isGroupOwner` MUST be false (else `svc wifi disable`,
   `svc wifi enable`). `adb -s $PH shell dumpsys window | grep mCurrentFocus`: at home (else
   `input keyevent KEYCODE_HOME`). `adb -s $PH shell dumpsys bluetooth_manager | grep -a -A8 'Bonded devices'`:
   record it. Record the Gearhead version.
8. D-HU: `ap_off` MUST print 0. Then `ap_on` MUST print 1, then `ap_off` MUST print 0. This clears a
   degraded first start after a reboot.
9. `hustop`, back up `settings.xml` to `$OUT/settings-backup.xml`, `stat` `shared_prefs/`.

### Block C: install C

`hustop`; `adb -s $HU install -r $OUT/apk-C.apk`; `WANT_MD5=<md5 of apk-C>`; `apk_check`; launch once,
`send ACTION_QUERY_STATE` (commit `d2d0a6bf`), `huexit`. Then run R1 (C), R4 (C), R3 (C), in that order.

### Block F: install F

`hustop`; `adb -s $HU install -r $OUT/apk-F.apk`; `WANT_MD5=<md5 of apk-F>`; `apk_check`; launch once,
`send ACTION_QUERY_STATE` (commit `25a56033`), `huexit`. Read back the base keys (`hu_read F0`): an install
can wipe `settings.xml`. Then run R1 (F), R2 (F), R4 (F), R3 (F), in that order.

### R1. The switch lands on a hotspot start in flight (THE POINT)

Per arm: R1a twice, then R1b three times. IDs `R1a-<arm>-<n>` and `R1b-<arm>-<n>`, for example `R1b-F-2`.
A rep with `NO_TRIGGER` in `races.log` is void. Re-run it, at most 2 extra reps per arm.

Setup and commands, per rep:

```bash
ID=R1a-C-1; RUN=$ID th_gate; apk_check || exit 1
hustop; ap_off                       # MUST print 0
hot; hu_read $ID-pre                 # native-ap-transport 1, auto-enable-hotspot true
ph_bt disable                        # MUST read state: OFF
cap_on $ID
launch; sleep 1; step $ID-start
race $ID "Setting hotspot enabled=true" 1.5                 # R1a
# race $ID "HotspotManager: Band preference is " 1.0        # R1b: use this line instead
sleep 60
echo "ap_end=$(apup)" > $OUT/$ID-ap.txt
adb -s $HU shell dumpsys wifi | grep -i SoftApInfo >> $OUT/$ID-ap.txt
step $ID-end; cap_off; huexit; ap_off
```

Grade each rep from these values. Print them in one line per rep.

```bash
Ls=$(ln1 $ID "Setting hotspot enabled=true"); Lw=$(ln1 $ID "ensuring hotspot is disabled first...")
Lm=$(ln1 $ID "AutomationMarker: $ID-switch"); Le=$([ -n "$Ls" ] && lnEnd $ID $Ls)
reached=no; [ -n "$Ls" ] && [ -n "$Lw" ] && [ $Ls -lt $Lw ] && { [ -z "$Le" ] || [ $Lw -lt $Le ]; } && reached=yes
endline=$([ -n "$Le" ] && upto $ID | sed -n "${Le}p" | cut -c1-160)
guard=$(( $(cntA $ID "$Lm" "HotspotManager: a stop arrived before this start asked for") + $(cntA $ID "$Lm" "HotspotManager: a stop arrived while this start was running") ))
late_up=$(( $(cntA $ID "$Lm" "HotspotManager: Hotspot is up") + $(cntA $ID "$Lm" "An access point came up after its band's window had expired") ))
busy=$(busyA $ID "$Lm"); creates=$(cntA $ID "$Lm" "createGroup SUCCESS")
succ_s=$(gap $ID "$Lm" "$(lnA $ID "$Lm" "createGroup SUCCESS")")
drain=$(cntA $ID 0 "HotspotManager: a start was still running")
verbs=$(( $(cntA $ID 0 "AutomationReceiver: com.andrerinas.openheadunit.ACTION_SET_SETTINGS") + $(cntA $ID 0 "AutomationReceiver: com.andrerinas.openheadunit.ACTION_START_WIRELESS_SCAN") ))
imported=$(grep -ac 'imported":1' $OUT/$ID-switch-reply.txt)
echo "$ID reached=$reached guard=$guard late_up=$late_up busy=$busy creates=$creates succ_s=$succ_s $(head -1 $OUT/$ID-ap.txt) drain=$drain verbs=$verbs imported=$imported cred60=$(cntA $ID 0 'No WiFi credentials available after') end=[$endline]"
```

A rep is void if `verbs` is below 2 or `imported` is 0. Do not discard a rep for a second
`createGroup SUCCESS`: a late AP that breaks the group is the defect, not contamination. Record `creates`.

**Control arm (C), per reached rep.** The leak reproduced when `late_up` >= 1, or `ap_end=1`, or
`busy` >= 1.

**Fix arm (F), per reached rep.**

- PASS: `guard` >= 1, `late_up` = 0, `ap_end=0`, and `succ_s` <= 30.
- FAIL: `guard` = 0, or `late_up` >= 1, or `ap_end=1`, or `succ_s` is `none` or above 30.

**R1 verdict, from the two arms together.**

- PASS: F has at least 4 reached reps, every reached F rep passes, and C reproduced the leak in at least
  1 reached rep.
- FAIL: any reached F rep fails.
- INCONCLUSIVE: F has fewer than 3 reached reps, or every reached F rep passes but C reproduced the leak
  in no rep. Then write "the leak does not reproduce on D-HU; F's guard fired in N of N reps".

If the guard line does not match F's start, the brief named a wrong window: if `guard` = 0 and the
`end` field of the rep shows `Hotspot is up` before the switch marker, the rep is not reached, and the
formula already says so. Report the R1a and R1b reps apart.

What a PASS would look like if F did nothing: `ap_end=0` and a fast `succ_s` can both come from D-HU's
platform stopping the AP for P2P. So `guard` >= 1 is a required condition, and the control's leak count
is what proves the window was the field one.

### R2. The backstop, with a SoftAP up before the WiFi Direct bring-up (F only, best effort)

IDs `R2A-<n>` (3 reps), then `R2B-<n>` (3 reps, only under the rule below).

Variant A, per rep:

```bash
ID=R2A-1; RUN=$ID th_gate; apk_check || exit 1
hustop; ap_on                        # MUST print 1
wfd; hu_read $ID-pre                 # native-ap-transport 0
ph_bt disable
cap_on $ID
launch; sleep 1; step $ID-start
sleep 60
echo "ap_end=$(apup)" > $OUT/$ID-ap.txt
step $ID-end; cap_off; huexit; ap_off
```

Variant B differs in two lines. `ap_off` (MUST print 0) replaces `ap_on` before the launch, and a watcher
raises the AP the moment the bring-up has asked it down:

```bash
( until grep -aqF "ensuring hotspot is disabled first..." $OUT/$ID-hu.txt; do sleep 0.1; done
  adb -s $HU shell cmd wifi start-softap OHU-TEST wpa2 testtest1234 -b 5; echo "$ID ap raised" >> $OUT/races.log ) &
```

Start that watcher right after `cap_on`, before `launch`. Kill it after `cap_off` if it is still running.

Grade each rep:

```bash
Lb=$(ln1 $ID "the group create got BUSY while"); Lf=$(ln1 $ID "access point take-down finished (down=")
busy=$(busyA $ID 0); backstop=$(cntA $ID 0 "the group create got BUSY while")
downtrue=$(cntA $ID 0 "access point take-down finished (down=true)")
succ_s=$(gap $ID "$Lb" "$([ -n "$Lb" ] && lnA $ID "$Lb" "createGroup SUCCESS")")
busy_after=$([ -n "$Lf" ] && cntA $ID "$Lf" "createGroup failed (BUSY" || echo none)
echo "$ID busy=$busy backstop=$backstop downtrue=$downtrue succ_s=$succ_s busy_after=$busy_after stillup=$(cntA $ID 0 'HotspotManager: the access point was still up') reraise=$(cntA $ID 0 "this unit's access point is up again after the app took it down") creates=$(cntA $ID 0 'createGroup SUCCESS') $(cat $OUT/$ID-ap.txt)"
```

- Reached: `busy` >= 1.
- PASS (per reached rep): `backstop` = 1, `downtrue` = 1, `succ_s` <= 20, `busy_after` <= 1, `reraise` = 0.
- FAIL (per reached rep): `backstop` = 0 with `ap_end=1`, or `succ_s` is `none`, or `busy_after` >= 3.
- NOT REACHED: `busy` = 0. Also not reached: `backstop` = 0 with `ap_end=0`, because the AP was gone
  when the create got `BUSY`. Do not grade the backstop from a rep that never reached it.

Rule for variant B: run it only if no A rep was reached and `stillup` was 0 in every A rep (the app's
stop took the shell AP down). R2 verdict: PASS if at least 1 rep was reached and every reached rep
passes. FAIL if any reached rep fails. INCONCLUSIVE if no rep was reached. Stop R2 after 6 reps.

### R3. Regression: a user exit from a hotspot session (both arms, 3 reps each)

IDs `R3-<arm>-<n>`. Phone capture: yes.

```bash
ID=R3-C-1; RUN=$ID th_gate; apk_check || exit 1
hustop; ap_on                        # MUST print 1
hot; hu_read $ID-pre                 # hotspot-teardown-proven-unsafe MUST be absent
ph_up
cap_on $ID phone
launch; sleep 1; step $ID-start
waitfor $OUT/$ID-hu.txt "SSL handshake complete" 120     # TIMEOUT: rep not gradable, go to the end lines below
sleep 20; step $ID-exit
send ACTION_DISCONNECT
sleep 30; echo "ap_mid=$(apup)" > $OUT/$ID-ap.txt; step $ID-mid
ap_off; ap_on                        # fresh shell AP for the reconnect, the same on both arms
step $ID-rearm
n0=$(grep -acF -- 'SSL handshake complete' $OUT/$ID-hu.txt)   # SSL lines before the re-arm
send ACTION_START_WIRELESS_SCAN
# wait for the second session's SSL line, capped at 120 s (the failure bound), then 30 s of session
for i in $(seq 1 240); do [ "$(grep -acF -- 'SSL handshake complete' $OUT/$ID-hu.txt)" -gt "$n0" ] && break; sleep 0.5; done
sleep 30; step $ID-end; cap_off; huexit; ap_off
```

Grade each rep:

```bash
Lx=$(ln1 $ID "AutomationMarker: $ID-exit"); Lr=$(ln1 $ID "AutomationMarker: $ID-rearm")
s1=$(upto $ID | head -n "${Lx:-0}" | grep -acF "SSL handshake complete")
ran=$(cntA $ID "$Lx" "CommManager teardown complete. Restarting the hotspot so the phone leaves the network.")
held=$(cntA $ID "$Lx" "HotspotManager: the restarted access point held for"); fell=$(cntA $ID "$Lx" "HotspotManager: the restarted access point fell within")
failed=$(cntA $ID "$Lx" "would not come back up"); unsafe=$(cntA $ID "$Lx" "so it will not be taken down again")
skip=$(cntA $ID 0 "HotspotManager: another stop arrived while the hotspot was down for a restart"); drain=$(cntA $ID 0 "HotspotManager: a start was still running")
s2_s=$(gap $ID "$Lr" "$([ -n "$Lr" ] && lnA $ID "$Lr" "SSL handshake complete")")
adv=$(cntA $ID 0 "NativeAA: advertising WPP over TCP at")
echo "$ID s1=$s1 ran=$ran held=$held fell=$fell failed=$failed unsafe=$unsafe skip=$skip drain=$drain s2_s=$s2_s adv=$adv $(cat $OUT/$ID-ap.txt) crit=$(pc $ID $ID-start $ID-end 'Critical error') rfcomm=$(pc $ID $ID-start $ID-end 'Creating rfcomm socket for device:') tcp=$(pc $ID $ID-start $ID-end 'WPP on TCP connected to the WiFi network') nocfg=$(pc $ID $ID-start $ID-end 'No WPP on TCP configuration found in storage')"
```

- A rep is gradable when `s1` >= 1 and `ran` = 1. If `s1` = 0, record "no first session" and the count of
  `NativeAA: Connection accepted from`. Stop R3 on an arm after 2 reps with no first session.
- FAIL (F): `skip` >= 1 in any rep (this flow has no second stop of ours), or `drain` >= 1, or `crit` >= 1,
  or `unsafe` >= 1 in an F rep while every C rep read `unsafe` = 0, or `s2_s` is `none` or above 120 in an F
  rep while every gradable C rep reconnected.
- PASS (F): at least 2 gradable F reps, `ran` = 1 in each, and none of the FAIL conditions.
- INCONCLUSIVE: fewer than 2 gradable F reps.
- Record the outcome class per rep (`held`, `fell`, `failed`) on both arms. It is D-HU's behaviour, not a
  grade.
- If `adv` >= 1 in any rep, the phone may hold a TCP endpoint. Stop R3 on that arm and record it in Setup
  notes. Before the next block, clear Android Auto on D-POCO with no UI (`rig-quirks/units/D-POCO.md`
  measured that the next bring-up then connects on its first RFCOMM attempt):

  ```bash
  adb -s $PH shell pm clear com.google.android.projection.gearhead   # MUST print Success
  adb -s $PH shell dumpsys window | grep mCurrentFocus                # MUST NOT name gearhead
  ```

  If the focus line names `gearhead`, a first-run dialog is up. Do not dismiss it. Skip every later run
  that needs D-POCO and record the focus line in Setup notes.

### R4. Regression: a switch with the AP settled, then a session over WiFi Direct (both arms, 2 reps each)

IDs `R4-<arm>-<n>`. Phone capture: yes.

```bash
ID=R4-C-1; RUN=$ID th_gate; apk_check || exit 1
hustop; ap_on                        # MUST print 1
hot; hu_read $ID-pre
ph_bt disable                        # no hotspot session, so no endpoint
cap_on $ID phone
launch; sleep 1; step $ID-start
sleep 25
doswitch $ID
sleep 15; echo "ap_after_switch=$(apup)" > $OUT/$ID-ap.txt
ph_up; step $ID-phone
waitfor $OUT/$ID-hu.txt "SSL handshake complete" 120
sleep 15; step $ID-end; cap_off; huexit; ap_off
```

Grade each rep:

```bash
Lm=$(ln1 $ID "AutomationMarker: $ID-switch"); Lp=$(ln1 $ID "AutomationMarker: $ID-phone")
Lssl=$([ -n "$Lp" ] && lnA $ID "$Lp" "SSL handshake complete")
echo "$ID ens=$(cntA $ID "$Lm" 'ensuring hotspot is disabled first...') auto_on=$(cntA $ID 0 "trying to switch this device's hotspot on") drain=$(cntA $ID 0 'HotspotManager: a start was still running') busy=$(busyA $ID "$Lm") backstop=$(cntA $ID "$Lm" 'the group create got BUSY while') succ_s=$(gap $ID "$Lm" "$(lnA $ID "$Lm" 'createGroup SUCCESS')") ssl_s=$(gap $ID "$Lp" "$Lssl") thr=$([ -n "$Lssl" ] && cntA $ID "$Lssl" 'Throughput over' || echo 0) imported=$(grep -ac 'imported":1' $OUT/$ID-switch-reply.txt) $(cat $OUT/$ID-ap.txt) crit=$(pc $ID $ID-phone $ID-end 'Critical error') rfcomm=$(pc $ID $ID-phone $ID-end 'Creating rfcomm socket for device:') tcp=$(pc $ID $ID-phone $ID-end 'WPP on TCP connected to the WiFi network')"
```

- Void: `imported` = 0, or `auto_on` >= 1 (the shell AP was not up, so the AP was not settled).
- PASS (F, every rep): `ens` >= 1, `drain` = 0, `succ_s` <= 30, `ssl_s` <= 120, `thr` >= 1, `crit` = 0, and
  `rfcomm` + `tcp` >= 1 (the phone used one of its two routes).
- FAIL (F): any PASS condition missed in a non-void rep. If C misses the same condition in its reps, say
  so beside the FAIL: that points at the rig, not at F.
- Record `busy` and `backstop` on both arms. On F, `busy` >= 1 with `backstop` = 0 and
  `ap_after_switch=1` is a FAIL of seam 2, and also counts as a reached R2 rep.

### End of round

`huexit` if the app runs, `hustop`, `ap_off` (MUST print 0). Restore `$OUT/settings-backup.xml` with
`rig-toolkit/restore_settings.sh` and diff it: MUST be empty. D-POCO: `ph_up`, `isGroupOwner` false.
D-MOTO: `svc bluetooth enable`. D-HU: `svc power stayon false`.

## 8. Do not re-run

- Whether D-POCO dials a stored WPP-over-TCP endpoint after a transport switch. That hypothesis is
  parked: D-HU grades neither network stable, so no endpoint goes out on this build.
- The type 10 rejection and the endpoint grade (`wpp-endpoint-depoison` rounds,
  `forget-car-every-connection` round 2).
- The status pill on the external module route (seam 3). No rig unit has the module. Unit tests decide it.
- The vendor module's Bluetooth audio pause. It is not in our code.

## 9. Report back

1. R1, one row per rep and arm: `reached`, `guard`, `late_up`, `ap_end`, `busy`, `succ_s`, and the `end`
   line. Then the count of C reps that reproduced the leak, and the count of F reps that passed.
2. R2: whether any rep was reached, and for a reached rep the seconds from the backstop line to
   `createGroup SUCCESS`, and `stillup` per A rep.
3. R3 and R4: the outcome class per rep on both arms, `skip` and `drain` on F, and the seconds to SSL.
