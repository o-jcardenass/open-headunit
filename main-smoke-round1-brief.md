# main-smoke, round 1 brief: current main on every unit, the USB dongle included

Published name: `main-smoke-round1-brief.md`. Results go in `main-smoke-round1-results.md`.
Evidence: create the release `rig-evidence-main-smoke` with the asset `main-smoke-round1-captures.zip`.

Units: D-HU, D-SAM, D-HP and D-POCO as head units; D-POCO and D-MOTO as phones; the USB dongle (`D-DONGLE`, below).

Queue line: `main-smoke-round1-brief.md` (one arm, `main` `947b3ae9`; one build; units D-HU, D-SAM, D-HP, D-POCO, D-MOTO
and the dongle; M3, M6 and M7 are the point).

Pairing: none, because the round uses every unit (§3b). Hand steps: one Bluetooth re-pair, eight cable moves, one
dongle reseat per run at most, USB system dialogs, a D-MOTO unlock (§4). Injected taps: none.

**Time budget.** Build 25 min, prep 15 min, stage S 12 min, stage U 30 min, stage H 30 min, stage P 25 min, cable
moves 15 min, closing 20 min, thermal waits about 10 min. **About 3 h.**

> **Revised 2026-10-29: moved onto `rig-lib`.** The round runs as one `rig_session.py` process from the block below.
> Same runs, build and gates. Prepare is now two runs (`prep`, `moto`), D-POCO's move to wireless adb is `pnet`, and
> Closing is `close` and `close-hp`. Several strings and one bond rule were wrong; section 7 and section 3 say which.

```rig-requires
lib 2c7a270e0
candidate M 947b3ae9fae9834aa8e4145ee91751ed743c2ae0
unit D-HU   role=headunit wifi=on bt=on
unit D-SAM  role=headunit wifi=on bt=on
unit D-HP@prep role=headunit battery=60
unit D-POCO@prep,M4,M1,M3 role=phone wifi=on bt=on
unit D-POCO@M4 bond=D-SAM nobond=D-HU
unit D-POCO@M1,M3 bond=D-HU nobond=D-SAM
unit D-SAM@M4 battery=50
unit D-MOTO@moto wifi=on bt=on
unit D-MOTO@M4,M1,M3 wifi=off bt=off
unit D-MOTO@M6,M7 wifi=on bt=on
unit D-POCO@pnet role=headunit battery=60
unit D-HP@close-hp
hardware the wireless Android Auto dongle (D-DONGLE, bonded to D-MOTO as carplay_box_F96B), and one OTG adapter each for D-HP and D-POCO
confirm D-POCO has projected to D-HU and to D-SAM in Android Auto at least once
confirm D-POCO's Spotify has a playlist of 6 or more tracks
confirm D-SAM is on its own charger, and D-HP is charged as full as it goes
confirm D-MOTO unlocks without a PIN, or you unlock it when a cue asks (H0)
run prep cap=2400 script=prep.sh grade=grade-prep.sh
run moto cap=420 script=moto.sh grade=grade-moto.sh
run M4 cap=1200 script=m4.sh grade=grade-m4.sh
run M1 cap=1200 script=m1.sh grade=grade-m1.sh
run M3 cap=1200 script=m3.sh grade=grade-m3.sh point=yes
run M6 cap=1500 script=m6.sh grade=grade-m6.sh point=yes hand="H3: plug the dongle into D-HP's OTG port. Answer a USB dialog with OK (Always is allowed for the dongle)"
run M5 cap=1500 script=m5.sh grade=grade-m5.sh hand="H4: move the dongle off D-HP. Unplug D-MOTO from the PC and plug it into D-HP's OTG port. Answer a USB dialog with OK, never Always. Unlock D-MOTO if it shows a PIN"
run pnet cap=420 script=pnet.sh grade=grade-pnet.sh
run M2 cap=1080 script=m2.sh grade=grade-m2.sh hand="H5: unplug D-MOTO from D-HP. Unplug D-POCO from the PC and plug D-MOTO into D-POCO's OTG port. Answer a USB dialog with OK, never Always"
run M7 cap=1800 script=m7.sh grade=grade-m7.sh point=yes hand="H6: D-MOTO stays on its PC cable. Plug the dongle into D-POCO's OTG port. Answer a USB dialog with OK (Always is allowed for the dongle)"
run close cap=1800 script=close.sh always=yes
run close-hp cap=900 script=close-hp.sh always=yes
stop prep FAIL,UNTESTABLE skip=all
stop moto FAIL,UNTESTABLE skip=M5,M2
stop pnet FAIL,UNTESTABLE skip=M2,M7
```

D-HP, D-MOTO and D-POCO have scoped lines only, because each spends part of the round on wireless adb with its USB
port given to an OTG adapter, and `check` reads a unit by its USB serial. A scoped line that names only a unit
(`D-HP@close-hp`) makes the session wait until that unit is back on its PC cable.

## 1. Build and baseline

One arm, no control. `main` is `v.4.0.0-alpha` plus nine merges: the Material 3 settings screen, the bundled H.264
decoder (now on 32-bit ARM too), the audio renderer and its time-correction engines, cleanup tied to its arm after an
Exit, the ZBT link state before a reconnect, WPP decisions in one snapshot, and the `OHU_Log_` file name. Every one of
those ran on the rig inside `test/pr-merge-train` (rounds 1 to 4). This exact tree has not.

| Ref | Commit | Tree |
|---|---|---|
| `origin/main` | `947b3ae9fae9834aa8e4145ee91751ed743c2ae0` | `e7112fbfdd820cf4834a8a9a5a47f5b62e3ea165` |

The dev host ran the JVM suite at this commit: 4245 tests, 0 failures, 0 skipped. Do not run the unit tests on the rig.

The host builds the arm (rig-round skill, Prepare) and puts the APK at `$EV/apk/M.apk`. `prep` checks that it carries
`lib/armeabi-v7a/libavcodec.so`.

```text
WT=$($RIG_TOOLKIT/wt-new.sh main-smoke candidate 947b3ae9fae9834aa8e4145ee91751ed743c2ae0 | tail -1)
git -C $WT rev-parse 'HEAD^{tree}'          # MUST print e7112fbfdd820cf4834a8a9a5a47f5b62e3ea165
$RIG_TOOLKIT/rig_thermal.sh wait 70
HUR_DIR=$WT MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh    # copy the APK from rig-data/apks/ to $EV/apk/M.apk at once
```

**Identity after every install, on every head unit** (`identity` in section 6): `ACTION_QUERY_STATE`'s `data=` line
carries `947b3ae9`; the md5 of the pulled live APK matches; `unzip -p live.apk 'classes*.dex' | strings | grep -acE
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
  charge. Gates: 60% to start M6, read before every cycle and every run, 30% or below ends stage H (UNTESTABLE,
  battery). An unreadable battery is recorded and does not stop a run.
- **D-POCO's bonds change once, and the session checks both states.** M4 needs D-POCO bonded to D-SAM and not to D-HU:
  a D-POCO bonded to D-HU dials D-HU only and never wakes D-SAM (`units/D-POCO.md`). M1 and M3 need the D-HU bond. So
  stage S runs first, and the session asks the operator to pair D-POCO with D-HU (`Navegadortz2`) before M1 (H2).
  The old M4 rule "unpair `Navegadortz2` if L-WARN prints" is now the `nobond=D-HU` line.
- **D-POCO changes role once**, from phone to USB host in stage P. `check --fix` sends its app `headunit://exit` first
  (before `pnet`): a force-stop leaves its `p2p0` a group owner, and a phone in that state never joins a head unit's
  group.
- **Installs and APK pulls on D-HP go over USB only** (`units/D-HP.md`). So `prep` installs on D-HP before it moves to
  wireless adb, and `close-hp` puts the prior APK back after the operator returns its cable.
- **`ACTION_EXPORT_LOG` is a configuring verb at `947b3ae9`** (`AutomationCommandPolicy.CONFIGURING`), so
  `allow-external-configuration` is `true` in every key set. `ACTION_LOG_MARKER` is not gated.
- **Expected INCONCLUSIVE:** M2's video (a phone host holds the projection for milliseconds only, so `rendered` may
  be 0; M2 grades on the log); M3 if `ro.product.cpu.abilist` on D-HU has no `armeabi-v7a`. M3 is UNTESTABLE if the
  install refuses the ABI (§5 forbids an uninstall).
- **No comparison is a verdict.** M4 and M5 print the same damage numbers as round 4 for the record only.
- Read `rig-quirks/topics/tooling.md`, `topics/audio.md`, `topics/wifi.md`, every `units/` file, and template §7b.

## 4. Cabling and hand steps

The PC has four USB ports for six devices. Each stage names what is plugged in.

| Stage | On the PC | On an OTG port | Wireless adb |
|---|---|---|---|
| prep | D-HU, D-POCO, D-SAM, D-HP | | |
| moto to M3 | D-HU, D-POCO, D-SAM, D-MOTO | | D-HP |
| M6 | D-HU, D-POCO, D-SAM, D-MOTO | the dongle on D-HP | D-HP |
| M5 | D-HU, D-POCO, D-SAM | D-MOTO on D-HP | D-HP, D-MOTO |
| pnet | D-HU, D-POCO, D-SAM | D-MOTO on D-HP | D-HP, D-MOTO |
| M2 | D-HU, D-SAM | D-MOTO on D-POCO | D-POCO, D-MOTO, D-HP |
| M7, close | D-HU, D-SAM, D-MOTO | the dongle on D-POCO | D-POCO, D-HP |
| close-hp | D-HU, D-SAM, D-POCO, D-HP | | |

| Id | Hand step | When (how the session asks) |
|---|---|---|
| H0 | Unlock D-MOTO if it shows a PIN | a cue that names it |
| H1 | Unplug D-HP from the PC (it is on wireless adb). Plug D-MOTO into that port | end of `prep` (OPERATOR line); `moto` waits for D-MOTO |
| H2 | On D-POCO, pair D-HU's Bluetooth (`Navegadortz2`) | before M1 (the session's bond check) |
| H3 | Plug the dongle into D-HP's OTG port | M6's `hand=` |
| H4 | Move the dongle off D-HP. Unplug D-MOTO from the PC and plug it into D-HP's OTG port | M5's `hand=` |
| H5 | Unplug D-MOTO from D-HP. Unplug D-POCO from the PC and plug D-MOTO into D-POCO's OTG port | M2's `hand=` |
| H6 | Unplug D-MOTO from D-POCO and put it on the free PC port. Plug the dongle into D-POCO's OTG port | before M7 (D-MOTO's adb check, then M7's `hand=`) |
| H7 | Unplug the dongle; D-POCO back on its PC cable. Then unplug D-MOTO from the PC and plug D-HP into that port | end of `close` (OPERATOR line); `close-hp` waits for D-HP |
| H8 | Answer a system USB dialog with OK. "Always" is allowed for the dongle, not for D-MOTO | named in each USB run's cue |
| H9 | Unplug D-MOTO from D-POCO | M2's OPERATOR line |
| H10 | Reseat the dongle, or replug D-MOTO | an OPERATOR line, when a host shows root hubs only for 3 min; once per run |

## 5. Settings keys

Every write goes in with the app stopped: `set_hu_settings_host.py` on D-HU (rooted), `set_prefs_runas_host.py` on
D-SAM, D-HP and D-POCO (`setkeys` in section 6). `prep` backs up every head unit's `settings.xml` with carriage returns
stripped, and states the delta each install made. Every run writes its keys again and reads them back
(`adb install -r` can wipe the file, `rig-quirks/topics/tooling.md`).

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
| `native-poke-bt-macs` | set | D-POCO's address only | | |
| `native-preferred-device-mac`, `last-connected-native-mac` | string | D-POCO's address | | |
| `native-mac` | | delete (not read by `main`) | | |
| `screen-orientation` | int | D-SAM only: 2 | | |
| `auto-start-on-usb` | boolean | | false | false |
| `auto-connect-last-session` | boolean | | true | true |
| `reopen-on-reconnection` | boolean | | true | true |
| `use-libusb` | boolean | | false | false |

D-POCO's address is read in `prep` (`settings get secure bluetooth_address`) into `$EV/poco.btaddr`. If it reads
nothing, `prep` uses `DC:B7:2E:5E:4E:59` and says so in `notes.txt`. `narrow-band-profile-cap` false stops a 2.4 GHz
link from announcing AAC over `use-aac-audio`. `auto-start-on-usb` false avoids the API 35 `BootCompleteReceiver`
crash dialog on D-POCO.

## 6. Helpers

Every script runs through `rig_run.sh` with `rig_lib.sh` and this `lib.sh` loaded, and writes to `$EV`. Each run sets
`HU` (the head unit's serial or `ip:5555`) and `PH` (the phone's). `prep`, `moto` and `pnet` write the wireless adb
addresses to `$EV/hp.addr`, `$EV/moto.addr` and `$EV/poco.addr`. A script that cannot run its test calls `untestable
<reason>`, and the grade turns that into UNTESTABLE.

**lib.sh**

```bash
. "$RIG_TOOLKIT/rig_devices.sh"
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
MAIN=$PKG/com.andrerinas.openheadunit.main.MainActivity
SETTINGS=$PKG/com.andrerinas.openheadunit.main.SettingsActivity
APK=$EV/apk/M.apk
DHU=$D_HU; SAM=$D_SAM; POCO=$D_POCO; MOTO=$D_MOTO; HPU=$D_HP
HP_W=$(cat "$EV/hp.addr" 2>/dev/null); HP_W=${HP_W:-$HPU}
MOTO_W=$(cat "$EV/moto.addr" 2>/dev/null); MOTO_W=${MOTO_W:-$MOTO}
POCO_W=$(cat "$EV/poco.addr" 2>/dev/null); POCO_W=${POCO_W:-$POCO}
POCO_BT=$(cat "$EV/poco.btaddr" 2>/dev/null); POCO_BT=${POCO_BT:-DC:B7:2E:5E:4E:59}
unit_of() { case $1 in "$DHU") echo hu ;; "$SAM") echo sam ;; "$HPU"|"$HP_W") echo hp ;; "$POCO"|"$POCO_W") echo poco ;; *) echo other ;; esac; }

send() { local a=$1; shift; adb -s "$HU" shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; }
mark() { send ACTION_LOG_MARKER --es text "$1" >/dev/null; sleep 0.3; adb -s "$PH" shell log -t AutomationMarker "$1"; }
launch() { adb -s "$HU" shell am start -n $MAIN >/dev/null; }
running() { local p; p=$(adb -s "$1" shell pidof $PKG 2>/dev/null | tr -d '\r')
  case "$p" in *[0-9]*) return 0 ;; esac; adb -s "$1" shell ps 2>/dev/null | grep -aqF "$PKG"; }
# appexit: Disconnect and headunit://exit only while the app runs (an exit on a stopped app starts the service)
appexit() { if running "$HU"; then send ACTION_DISCONNECT >/dev/null; sleep 5
  adb -s "$HU" shell am start -a android.intent.action.VIEW -d "headunit://exit" >/dev/null; sleep 5; fi
  adb -s "$HU" shell am force-stop $PKG; sleep 1; }
untestable() { echo "$*" > "$EV/$RUN-untestable.txt"; echo "UNTESTABLE $*"; exit 20; }
skipped() { echo "$*" > "$EV/$RUN-inconclusive.txt"; echo "INCONCLUSIVE $*"; exit 0; }
# slow_step CAP TEXT: a long step rig_run.sh must not call a stall (installs, the thermal gate)
slow_step() { rl_step "WAIT cap=$1s $2"; }
thermal() { slow_step 1260 "host thermal gate"; rl_thermal_gate; }
bat() { adb -s "$HU" shell dumpsys battery | grep -a -m1 level | tr -dc 0-9; }
waddr() {   # waddr SERIAL: the unit's wlan0 IPv4 address
  { adb -s "$1" shell ip -f inet addr show wlan0; adb -s "$1" shell ifconfig wlan0; } 2>/dev/null | tr -d '\r' |
    grep -aoE '([0-9]{1,3}\.){3}[0-9]{1,3}' | grep -v '^255\.' | head -n 1; }
to_wireless() {   # to_wireless SERIAL FILE: tcpip 5555, connect, and write ip:5555 to FILE when the model answers
  local ip; ip=$(waddr "$1"); echo "$1 wlan0=$ip" >> "$EV/wireless.log"
  [ -n "$ip" ] || return 1
  adb -s "$1" tcpip 5555 >> "$EV/wireless.log" 2>&1; rl_sleep 6
  adb connect "$ip:5555" >> "$EV/wireless.log" 2>&1; rl_sleep 2
  [ -n "$(adb -s "$ip:5555" shell getprop ro.product.model | tr -d '\r')" ] || return 1
  echo "$ip:5555" > "$2"
  local role; case "$1" in "$D_HP") role=D-HP ;; "$D_MOTO") role=D-MOTO ;; "$D_POCO") role=D-POCO ;; esac
  [ -n "$role" ] && echo "$role $ip:5555" >> "$(dirname "$EV")/unit-serials.txt"; }   # rig_requires.py reads it

# captures: $EV/<T>-hu.txt and $EV/<T>-ph.txt; alive restarts a capture that died as <tag>-pN
logclear() { adb -s "$1" logcat -G 16M >/dev/null 2>&1; adb -s "$1" logcat -c; }
caps() { logclear "$HU"; logclear "$PH"; rl_cap_start "$1-hu" "$HU"; rl_cap_start "$1-ph" "$PH"; }
alive() { local tag pid n s
  for tag in "$1-hu" "$1-ph"; do
    pid=$(awk -v t="cap:$tag" 'index($2,t)==1{p=$1} END{print p}' "$RL_PIDS" 2>/dev/null)
    if [ -n "$pid" ] && ! kill -0 "$pid" 2>/dev/null; then
      n=$(ls "$EV/$tag"-p*.txt 2>/dev/null | wc -l); s=$HU; [ "$tag" = "$1-ph" ] && s=$PH
      echo "$(date +%T) $tag capture restarted" >> "$EV/notes.txt"; rl_cap_start "$tag-p$((n + 1))" "$s"
    fi
  done; }
curfile() { local f; f=$(ls -t "$EV/$1"-p*.txt 2>/dev/null | head -n 1); echo "${f:-$EV/$1.txt}"; }
merged() { cat "$EV/$1.txt" "$EV/$1"-p*.txt 2>/dev/null | awk '!seen[$0]++' > "$EV/$1-all.txt"; echo "$EV/$1-all.txt"; }
hold() { local i; for i in $(seq 1 $(( $2 / 30 ))); do rl_sleep 30; alive "$1"; done; }
# usbwatch TAG: a tracked poller that writes USB_DEVICE_PRESENT while the host sees a non-hub USB device
usbwatch() { rl_bg "usb:$1" bash -c "while :; do n=\$(adb -s '$HU' shell ls /sys/bus/usb/devices 2>/dev/null | tr -d '\r' | grep -acE '^[0-9]+-[0-9.]+\$'); [ \"\${n:-0}\" -gt 0 ] && echo \"\$(date +%T) USB_DEVICE_PRESENT n=\$n\"; sleep 2; done" >> "$EV/$1-usbwatch.txt"; }
focus() { adb -s "$HU" shell dumpsys activity activities | grep -a -m1 -E 'topResumedActivity|mResumedActivity' >> "$EV/$1-focus.txt"; }
shot() { adb -s "$HU" shell screencap -p /sdcard/s.png; sleep 0.3; adb -s "$HU" pull /sdcard/s.png "$EV/$1.png" >/dev/null 2>&1; }

# settings
COMMON="scalar:int:log-level:2 scalar:int:onboarding-version:2 scalar:boolean:allow-external-configuration:true scalar:boolean:kill-on-disconnect:false scalar:boolean:enable-floating-button:false del:video-profile-starvation-cap scalar:boolean:narrow-band-profile-cap:false scalar:boolean:force-software-decoding:false"
NATIVE="$COMMON scalar:int:wifi-connection-mode:3 set:connection-modes:wifi scalar:boolean:enable-audio-sink:true scalar:boolean:use-aac-audio:false scalar:int:audio-latency-multiplier:2 scalar:int:audio-queue-capacity:20 scalar:boolean:use-aaudio-output:false scalar:int:native-ap-transport:0 scalar:boolean:external-bt-zbt-transport:false scalar:int:native-driver-selection-mode:0 scalar:boolean:native-poke-all-paired:false set:native-poke-bt-macs:$POCO_BT scalar:string:native-preferred-device-mac:$POCO_BT scalar:string:last-connected-native-mac:$POCO_BT del:native-mac"
USBK="scalar:int:wifi-connection-mode:0 set:connection-modes:usb scalar:boolean:auto-start-on-usb:false scalar:boolean:auto-connect-last-session:true scalar:boolean:reopen-on-reconnection:true scalar:boolean:use-libusb:false"
USBPH="$COMMON $USBK scalar:boolean:enable-audio-sink:true scalar:boolean:use-aac-audio:false scalar:int:audio-latency-multiplier:2 scalar:int:audio-queue-capacity:20 scalar:boolean:use-aaudio-output:false"
DONGLE="$COMMON $USBK scalar:boolean:enable-audio-sink:false scalar:string:video-codec:H.264 scalar:int:resolutionId:2 scalar:int:native-driver-selection-mode:0 scalar:boolean:native-poke-all-paired:false"
setkeys() { local s=$1; shift; adb -s "$s" shell am force-stop $PKG
  if [ "$s" = "$DHU" ]; then python3 -I "$RIG_TOOLKIT/set_hu_settings_host.py" "$s" "$@"
  else python3 -I "$RIG_TOOLKIT/set_prefs_runas_host.py" "$s" "$@"; fi >> "$EV/keys.log" 2>&1; }
keys_read() { adb -s "$1" shell run-as $PKG cat shared_prefs/settings.xml | tr -d '\r' > "$EV/settings-$2.xml"; }

# identity SERIAL NAME: md5 of the live APK, DEX symbols, ACTION_QUERY_STATE; IDENT_OK or IDENT_BAD in ident.log
identity() { local s=$1 u=$2 f="$EV/live-$2.apk" m w d q
  adb -s "$s" pull "$(adb -s "$s" shell pm path $PKG | head -n 1 | cut -d: -f2 | tr -d '\r')" "$f" >/dev/null 2>&1
  m=$(md5sum "$f" 2>/dev/null | cut -d' ' -f1); w=$(md5sum "$APK" 2>/dev/null | cut -d' ' -f1)
  d=$(unzip -p "$f" 'classes*.dex' 2>/dev/null | strings | grep -acE 'FfmpegVideoDecoder|NativeTimeStretchEngine')
  HU=$s; launch; sleep 3; send ACTION_QUERY_STATE > "$EV/query-$u.txt"; appexit
  q=$(grep -ac '947b3ae9' "$EV/query-$u.txt")   # not-literal: the am reply
  echo "$u live=$m want=$w dex=$d query=$q" >> "$EV/ident.log"
  if [ -n "$m" ] && [ "$m" = "$w" ] && [ "${d:-0}" -ge 2 ] && [ "${q:-0}" -ge 1 ]; then echo "IDENT_OK $u" >> "$EV/ident.log"
  else echo "IDENT_BAD $u" >> "$EV/ident.log"; fi; }

# settings restore: D-HU as root (run-as cannot read /data/local/tmp there), the others through restore_settings.sh
restore_prefs() { local s=$1 u=$2
  [ -s "$EV/backup-$u.xml" ] || return 0
  adb -s "$s" push "$EV/backup-$u.xml" /data/local/tmp/settings-restore.xml >/dev/null
  if [ "$s" = "$DHU" ]; then
    printf '%s\n' "F=/data/data/$PKG/shared_prefs/settings.xml" 'cp /data/local/tmp/settings-restore.xml $F' \
      "chown \$(stat -c %u:%g /data/data/$PKG) \$F; chmod 660 \$F" > "$EV/restore-hu.sh"
    adb -s "$s" push "$EV/restore-hu.sh" /data/local/tmp/restore-hu.sh >/dev/null; adb -s "$s" shell sh /data/local/tmp/restore-hu.sh
  else
    adb -s "$s" push "$RIG_TOOLKIT/restore_settings.sh" /data/local/tmp/restore_settings.sh >/dev/null
    adb -s "$s" shell run-as $PKG sh /data/local/tmp/restore_settings.sh
  fi
  keys_read "$s" "restored-$u"; diff "$EV/backup-$u.xml" "$EV/settings-restored-$u.xml" > "$EV/restore-diff-$u.txt"; }
# restore_unit SERIAL NAME: the prior APK back, its md5 checked, settings.xml restored; returns 1 on a mismatch
restore_unit() { local s=$1 u=$2 m w r=0
  slow_step 900 "restore $u"
  HU=$s; appexit
  if [ -s "$EV/prior-$u.apk" ]; then
    adb -s "$s" install -r -d "$EV/prior-$u.apk" > "$EV/restore-install-$u.txt" 2>&1
    adb -s "$s" pull "$(adb -s "$s" shell pm path $PKG | head -n 1 | cut -d: -f2 | tr -d '\r')" "$EV/restored-$u.apk" >/dev/null 2>&1
    m=$(md5sum "$EV/restored-$u.apk" 2>/dev/null | cut -d' ' -f1); w=$(md5sum "$EV/prior-$u.apk" | cut -d' ' -f1)
    echo "$u prior=$w live=$m" >> "$EV/restore.log"; [ -n "$m" ] && [ "$m" = "$w" ] || r=1
  fi
  restore_prefs "$s" "$u" || r=1
  return "$r"; }

# Native session start and end (M4, M1, M3). n_start returns 9 when no session forms within CAP seconds.
n_start() { local T=$1 CAP=$2 F="$EV/$1-hu.txt" O
  rl_radio "$PH" wifi off; rl_radio "$PH" bt off
  adb -s "$HU" shell am force-stop $PKG; caps "$T"
  launch; sleep 2; O=$(rl_offset "$F"); mark "$T-start"
  rl_radio "$PH" wifi on; rl_radio "$PH" bt on
  rl_wait_line "$F" 'SSL handshake complete' "$CAP" "$O" || return 9
  rl_wait_line "$F" 'Throughput over' 30 "$O"
  adb -s "$PH" shell input keyevent 126
  rl_wait_line "$F" 'Media Start Request AUDIO:' 20 "$O" || echo "$T NO_MUSIC" >> "$EV/notes.txt"   # not-literal: "Media Start Request %s: session"
  return 0; }
n_end() { mark "$1-exit"; mark "$1-end"; appexit; rl_cap_stop; }
# settings_shot T RUNAS: open the settings screen, record the focused activity and a screenshot
settings_shot() { local T=$1; shift
  mark "$T-set"; adb -s "$HU" shell "$@" am start --user 0 -n $SETTINGS --ei extra_destination 0 >/dev/null
  rl_sleep 10; focus "$T-set"; shot "$T-settings"; adb -s "$HU" shell input keyevent 4; sleep 2; mark "$T-setend"; }

# ---- grading ----
NFAIL=0; INC=""; DISCARD=""
c() { local o; o=$(rl_cond "$@"); echo "$o"; case "$o" in "COND $1 FAIL"*) NFAIL=$((NFAIL + 1)) ;; esac; }
inc() { INC="$INC${INC:+; }$*"; }
fin() { if [ -n "$INC" ] && [ "$NFAIL" -eq 0 ]; then rl_verdict INCONCLUSIVE "$INC"; fi; }
# early: called last in every grade, so its VERDICT wins over the COND roll-up
early() { if [ -f "$EV/$RUN-untestable.txt" ]; then rl_verdict UNTESTABLE "$(cat "$EV/$RUN-untestable.txt")"
  elif [ -f "$EV/$RUN-inconclusive.txt" ]; then rl_verdict INCONCLUSIVE "$(cat "$EV/$RUN-inconclusive.txt")"
  elif [ -n "$DISCARD" ]; then rl_verdict INCONCLUSIVE "discard (template 4): $DISCARD"; fi; }
attempt() { local t r; read -r t r < <(tail -n 1 "$EV/$1-attempts.txt" 2>/dev/null); echo "${t:-$1} ${r:-9}"; }
cntE() { rl_win "$1" "$2" "$3" | grep -acE -- "$4"; }
# after FILE FROM TO ANCHOR STRING: count STRING in FROM..TO after the first line that holds ANCHOR
after() { rl_win "$1" "$2" "$3" | awk -v a="$4" -v s="$5" 'f && index($0,s){n++} !f && index($0,a){f=1} END{print n+0}'; }
# gapms FILE FROM TO ANCHOR STRING: ms from the first ANCHOR to the first STRING after it (empty if none)
gapms() { rl_win "$1" "$2" "$3" | awk -v a="$4" -v s="$5" 'function t(l){split(l,x," ");split(x[2],h,":");return (h[1]*3600+h[2]*60+h[3])*1000}
  !f && index($0,a){f=1;t0=t($0);next} f && index($0,s){printf "%d\n",t($0)-t0;exit}'; }
# before FILE FROM TO A B: 1 if the first A comes before the first B and both print, else 0
before() { rl_win "$1" "$2" "$3" | awk -v a="$4" -v b="$5" '!ia && index($0,a){ia=NR} !ib && index($0,b){ib=NR} END{print (ia && ib && ia<ib)?1:0}'; }
rends() { rl_win "$1" "$2" "$3" | grep -aF 'Throughput over' | grep -aoE 'rendered=[0-9]+' | cut -d= -f2; }   # not-literal: "rendered=$n"
lastrend() { rends "$@" | tail -n 1; }
faillines() { local w="$EV/.win-$RUN.txt" a b x; rl_win "$1" "$2" "$3" > "$w"
  a=$(grep -a -A3 'FATAL EXCEPTION' "$w" | grep -acF 'com.andrerinas')   # system
  b=$(grep -acF 'UnsatisfiedLinkError' "$w")   # system
  x=$(grep -acF 'ANR in com.andrerinas' "$w")   # system
  echo $(( a + b + x )); }
pcrit() { python3 -I "$EV/scripts/pcrit.py" "$1" "$2" 2>/dev/null | head -n 1 | sed -n 's/.*crit_graded=\([0-9]*\).*/\1/p'; }
# gate T F P [rend]: the session gate of section 8
gate() { local r m
  c "$1.G1" ge "$(rl_cnt "$2" "$1-start" "$1-end" 'SSL handshake complete')" 1 "$1 a session formed"
  if [ "${4:-}" = rend ]; then r=$(lastrend "$2" "$1-start" "$1-end"); c "$1.G2" gt "${r:-0}" 0 "$1 the last Throughput line rendered frames"; fi
  c "$1.G3" eq "$(faillines "$2" "$1-start" "$1-end")" 0 "$1 no FATAL EXCEPTION, UnsatisfiedLinkError or ANR"
  c "$1.G4" eq "$(pcrit "$3" "$1")" 0 "$1 phone Critical error lines, the 2 s after an Exit excluded"
  m=$(rl_cnt "$2" "$1-start" "$1-end" 'MATCH! Starting AapService')
  if [ "${m:-0}" -gt 0 ]; then DISCARD="$1 printed MATCH! Starting AapService $m times; rerun once"; fi; }
mixlines() { rl_win "$1" "$2" "$3" | grep -aF 'AudioMixer: id=' | grep -acF 'loopGapMax='; }
pcm_media() { rl_win "$1" "$2" "$3" | grep -aF 'AudioDecoder.start: channel=' | grep -aF 'channel=6,' | grep -acF "codec=$4"; }   # not-literal: "channel=$channel, ... codec=$codec"
damage() { rl_win "$1" "$2" "$3" | grep -aE 'AudioMixer: id=[0-9]+ channel=6 target=' | awk '{for(i=1;i<=NF;i++){split($i,kv,"="); k=kv[1]
  if(k=="concealedFrames"||k=="staleFrames"||k=="compressedFrames"){v=kv[2]+0; if(seen[k]){tot[k]+=(v>=last[k])?v-last[k]:v} seen[k]=1; last[k]=v}}}
  END{printf "damage=%d concealed=%d stale=%d compressed=%d\n", tot["concealedFrames"]+tot["staleFrames"]+tot["compressedFrames"], tot["concealedFrames"], tot["staleFrames"], tot["compressedFrames"]}'; }
slow() { rl_win "$1" "$2" "$3" | grep -aF 'AudioMixer: id=' | grep -aF 'loopGapMax=' | awk '{e=-1;g=-1; for(i=1;i<=NF;i++){if($i~/^effective=/)e=substr($i,11)+0; if($i~/^loopGapMax=/)g=substr($i,12)+0}
  if(g>mx)mx=g; if(e>0 && g>e/48)n++} END{printf "slow_windows=%d loopGapMax_max=%dms\n", n, mx}'; }
```

`pcrit.py` counts the phone's `Critical error` lines between `<run>-start` and `<run>-end`, minus those within 2 s after
an `-exit` marker. Gearhead prints them within about 1 s of every Exit we cause, on every build.

**pcrit.py**

```python
import re, sys
from datetime import datetime, timedelta
cap, run = sys.argv[1], sys.argv[2]
ts = lambda l: datetime.strptime("2026-" + l[:18], "%Y-%m-%d %H:%M:%S.%f")
L = [l for l in open(cap, errors="replace") if re.match(r"\d\d-\d\d \d\d:\d\d:\d\d\.\d", l)]
on, exits, hits = False, [], []
for l in L:
    mk = l.split("AutomationMarker: ", 1)[1].strip() if "AutomationMarker: " in l else None
    if mk == f"{run}-start": on = True
    if not on: continue
    if mk and mk.endswith("-exit"): exits.append(ts(l))
    if "Critical error" in l: hits.append((ts(l), l.strip()))
    if mk == f"{run}-end": break
left = [h for h in hits if not any(timedelta(0) <= h[0] - e <= timedelta(seconds=2) for e in exits)]
print(f"{run}\tcrit_total={len(hits)}\tcrit_graded={len(left)}")
for h in left[:5]: print(h[1])
```

`dongle_cycle.py` prints one TSV row per cycle. It comes from `usb-dongle-regression` round 1, with the stale-data
columns.

**dongle_cycle.py**

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

`dongle_lib.sh` holds the dongle cycle of M6 and M7.

**dongle_lib.sh**

```bash
dcycle() { local R=$1 k=$2 F O row
  adb -s "$HU" shell am force-stop $PKG; sleep 1
  setkeys "$HU" $DONGLE; keys_read "$HU" "$R-$k"
  F=$(curfile "$R-hu"); O=$(rl_offset "$F"); mark "$R-$k-go"; launch
  if rl_wait_line "$F" 'SSL handshake complete' 60 "$O"; then rl_sleep 45; fi
  mark "$R-$k-end"; send ACTION_DISCONNECT >/dev/null; rl_sleep 8; alive "$R"
  row=$(python3 -I "$EV/scripts/dongle_cycle.py" "$(merged "$R-hu")" "$R-$k-go" "$R-$k-end" 2>&1 | head -n 1)
  printf '%s\tbat=%s\n' "$row" "$(bat)" >> "$EV/$R.tsv"; }
# drun R N: N cycles; a VOID row is run once more as the next k; 30% battery or less ends the cycles
drun() { local R=$1 N=$2 k=0 good=0 rerun=0 b
  : > "$EV/$R.tsv"; caps "$R"; mark "$R-start"
  while [ "$good" -lt "$N" ] && [ "$k" -lt $((2 * N)) ]; do
    k=$((k + 1)); b=$(bat)
    if [ -n "$b" ] && [ "$b" -le 30 ]; then echo "$R STOP battery $b" >> "$EV/$R-notes.txt"; break; fi
    dcycle "$R" "$k"
    if tail -n 1 "$EV/$R.tsv" | grep -q VOID && [ "$rerun" -eq 0 ]; then rerun=1; else good=$((good + 1)); rerun=0; fi
  done
  mark "$R-end"; appexit; rl_cap_stop; }
# dongle_grade R N NEED: rows, projected rows, FAIL lines; prints the COND lines
dongle_grade() { local R=$1 N=$2 rows proj F
  rows=$(grep -av VOID "$EV/$R.tsv" 2>/dev/null | grep -ac 'projected=')   # not-literal: dongle_cycle.py's column
  proj=$(grep -av VOID "$EV/$R.tsv" 2>/dev/null | grep -ac 'projected=1')   # not-literal
  F=$(merged "$R-hu")
  { cat "$EV/$R.tsv" 2>/dev/null; python3 -I "$EV/scripts/pcrit.py" "$(merged "$R-ph")" "$R" 2>/dev/null; } > "$EV/$R-record.txt"
  echo "$rows $proj $(faillines "$F" "$R-start" "$R-end")"; }
```

## 7. The deciding lines

All verified with `git grep -F` in `app/src/main` at `947b3ae9`, unless a line names another source. All print at
INFO. Grep with `-a`, in `<run>-hu.txt` between `<run>-start` and `<run>-end` unless noted. The grades read a capture
`alive` restarted through `merged`, which drops duplicate lines. **Corrected on 2026-10-29:** five lines here are
formatted at run time, so the old table's form of them is in no source file; the scripts grep a literal fragment of
each, or mark the line `# not-literal`.

| Id | Line (fixed substring) | Read |
|---|---|---|
| L-RX | `AutomationReceiver: ` | a verb landed; a step with none is void, not a FAIL |
| L-SSL | `SSL handshake complete` | a session formed |
| L-TP | `Throughput over` | `rendered=N`, one line per 5 s |
| L-CODEC | `Media Sink Setup Request: 3 on channel VIDEO` (source `Media Sink Setup Request: %d on channel %s`) | 3 = H.264 |
| L-MUS | `Media Start Request AUDIO:` (source `Media Start Request %s: session`) | music started |
| L-DEC | `AudioDecoder.start: channel=6,` with `codec=PCM` | the media channel's codec must not be AAC |
| L-MIX | `AudioMixer: id=` with `loopGapMax=` | the 10 s device line: `effective=`, `loopGapMax=` |
| L-CH6 | regex `AudioMixer: id=[0-9]+ channel=6 target=` | `concealedFrames=`, `staleFrames=`, `compressedFrames=` |
| L-ARM | `WifiLauncher: Initializing WiFi Mode:` | M1: the new Start armed |
| L-EXIT | `AapService: Native AA user exit. Stopping active launcher.` | M1: the old Exit's cleanup |
| L-GRP | `AapService: CommManager teardown complete. Stopping WiFi Direct group.` | M1: the old Exit's group removal |
| L-ZBT | `[ZBT]` | M1: must be 0 (the module route is off) |
| L-XY | `XYAuto projection media policy` (not on `main`) | M1: must be 0 (1 or more means the wrong APK) |
| L-FF1 | `Configuring bundled FFmpeg H.264/AVC decoder for` (source `Configuring bundled FFmpeg ${type.displayName}`) | M3 |
| L-FF2 | `Bundled FFmpeg H.264/AVC decoder initialized` | M3 |
| L-FF3 | `First bundled software H.264/AVC frame rendered` | M3 |
| L-WARN | `never opened the Android Auto channel` | M1, M4: the phone is bound to another head unit |
| L-ACC | `Sending acc start` | M2, M5: the AOA switch |
| L-SEL | `the phone selected ` | M2, M6, M7: the version answer (report it); its line ends `status STATUS_SUCCESS` |
| L-SDR | `Service Discovery Request` | the phone asked for our response |
| L-ANS | `Audio Focus Request`, `Sensor Start Request`, `Media Sink Setup Request` | the phone answered our response |
| L-DET | `USB_DEVICE_DETACHED` (in a `USB Intent: ` line) | a USB device left |
| L-STALE | `stale USB bytes before version request` | dongle: a warm re-attach (report) |
| L-LATE | `discarded a late VERSION_RESPONSE` | dongle: the late-answer guard fired (report) |
| L-PERM | `Requesting USB permission for ` | USB: report each identity (template §7b) |
| D-MATCH | `MATCH! Starting AapService` | discard rule (template §4) |

**FAIL lines, every run** (`faillines`): `FATAL EXCEPTION` in a block naming `com.andrerinas`; `UnsatisfiedLinkError`;
`ANR in com.andrerinas`. M3 also: `Failed to load hur_soft_video`, `decoder failed to initialize`, `Stopping
initialization retries for this codec`. **Phone, every run** (`pcrit`): `crit_graded=0`.

**Damage** (M4, M5, for the record): `damage` sums the increases of `concealedFrames`, `staleFrames` and
`compressedFrames` between consecutive L-CH6 lines from `<run>-music` to `<run>-end`; when a value falls, it adds the
new value. **Slow windows** (`slow`): L-MIX lines whose `loopGapMax` in ms is above `effective=`/48.

## 8. Runs

**Session gate, every Native and USB phone run** (`gate`): L-SSL within the cap, the last L-TP before `<run>-end` has
`rendered=` above 0 (except M2), 0 FAIL lines, `crit_graded=0`. D-MATCH above 0 makes the run INCONCLUSIVE (discard,
rerun once). A Native run whose first session never forms tries once more in the same run (`<run>r`); two misses make
it UNTESTABLE. The grade reads the last attempt in `$EV/<run>-attempts.txt`.

### prep. Backups, installs, identity, D-HP to wireless adb

D-HU, D-POCO, D-SAM and D-HP are on their PC cables. For each: stop the app, back up `settings.xml` (carriage returns
stripped), pull the installed APK to `prior-<unit>.apk`, install `M.apk` with `adb install -r -d`, run `identity`, and
diff `settings.xml` against the backup. Then D-HU's `shared_prefs` owner and ABI list, D-POCO's bonds, D-HP's and
D-POCO's USB grants, and D-HP to wireless adb. It ends with the H1 cue.

**prep.sh**

```bash
rl_step "APK and D-POCO address"
echo "armv7_libavcodec=$(unzip -l "$APK" | grep -acF 'lib/armeabi-v7a/libavcodec.so')" > "$EV/prep-apk.txt"   # not-literal: an APK entry
md5sum "$APK" >> "$EV/prep-apk.txt"
a=$(adb -s "$POCO" shell settings get secure bluetooth_address | tr -d '\r')
case "$a" in ??:??:??:??:??:??) echo "$a" > "$EV/poco.btaddr" ;;
  *) echo "DC:B7:2E:5E:4E:59" > "$EV/poco.btaddr"; echo "D-POCO address unread ($a); the brief's value used" >> "$EV/notes.txt" ;; esac
for s in "$DHU" "$SAM" "$HPU" "$POCO"; do
  u=$(unit_of "$s"); HU=$s
  slow_step 900 "prep $u: backup, prior APK, install, identity"
  adb -s "$s" shell am force-stop $PKG
  adb -s "$s" shell run-as $PKG cat shared_prefs/settings.xml | tr -d '\r' > "$EV/backup-$u.xml"
  adb -s "$s" pull "$(adb -s "$s" shell pm path $PKG | head -n 1 | cut -d: -f2 | tr -d '\r')" "$EV/prior-$u.apk" >/dev/null 2>&1
  md5sum "$EV/prior-$u.apk" >> "$EV/prior-md5.txt" 2>&1
  adb -s "$s" install -r -d "$APK" > "$EV/install-$u.txt" 2>&1
  identity "$s" "$u"
  keys_read "$s" "after-install-$u"; diff "$EV/backup-$u.xml" "$EV/settings-after-install-$u.xml" > "$EV/install-delta-$u.txt"
done
adb -s "$DHU" shell stat -c '%U:%G %a' /data/data/$PKG/shared_prefs > "$EV/hu-prefs-stat.txt"
adb -s "$DHU" shell getprop ro.product.cpu.abilist | tr -d '\r' > "$EV/abilist.txt"
rl_bonds "$POCO" > "$EV/poco-bonds.txt"
for s in "$HPU" "$POCO"; do adb -s "$s" shell dumpsys usb | grep -i -A4 headunitrevived > "$EV/usb-before-$(unit_of "$s").txt"; done
rl_step "D-HP to wireless adb"
to_wireless "$HPU" "$EV/hp.addr" || echo "D-HP wireless adb failed" >> "$EV/notes.txt"
echo "OPERATOR NOW: H1 - unplug D-HP from the PC (it is on wireless adb now) and plug D-MOTO into that port"
```

**grade-prep.sh**

```bash
for u in hu sam hp poco; do c "I.$u" eq "$(grep -acF "IDENT_OK $u" "$EV/ident.log")" 1 "$u runs main 947b3ae9 (md5, DEX, query)"; done   # not-literal
c A1 eq "$(sed -n 's/^armv7_libavcodec=//p' "$EV/prep-apk.txt")" 1 "the APK carries lib/armeabi-v7a/libavcodec.so"
c W1 eq "$(grep -ac ':5555' "$EV/hp.addr" 2>/dev/null)" 1 "D-HP answers on wireless adb"   # not-literal
early
```

A FAIL stops the round (`stop prep`): every run needs the identity, and stage H needs D-HP on wireless adb.

### moto. D-MOTO to wireless adb

D-MOTO is on the port D-HP left (H1; the session waits until adb sees it). It keeps the screen on, records Gearhead's
version and its bond to the dongle, checks VLC and the test file for M5, and moves to wireless adb. The session then
switches D-MOTO's radios off until stage H.

**moto.sh**

```bash
adb -s "$MOTO" shell svc power stayon true
adb -s "$MOTO" shell dumpsys package com.google.android.projection.gearhead | grep -a -m1 versionName > "$EV/moto-gearhead.txt"
rl_bonds "$MOTO" > "$EV/moto-bonds.txt"
vlc=$(adb -s "$MOTO" shell pm list packages | grep -acF 'org.videolan.vlc')   # not-literal: a package name
mp3=$(adb -s "$MOTO" shell ls -l /sdcard/Music/ohu-stutter-long.mp3 | grep -acF 'ohu-stutter-long')   # not-literal
echo "vlc=$vlc mp3=$mp3" > "$EV/moto-media.txt"
to_wireless "$MOTO" "$EV/moto.addr" || echo "D-MOTO wireless adb failed" >> "$EV/notes.txt"
```

**grade-moto.sh**

```bash
c 1 ge "$(grep -acF 'carplay_box_F96B' "$EV/moto-bonds.txt")" 1 "D-MOTO is bonded to the dongle"   # not-literal: a device name
c 2 eq "$(grep -ac ':5555' "$EV/moto.addr" 2>/dev/null)" 1 "D-MOTO answers on wireless adb"   # not-literal
early
```

A FAIL skips M5 and M2, which reach D-MOTO over wireless adb only. `vlc=0` or `mp3=0` makes M5 UNTESTABLE.

### Stage S: D-SAM with D-POCO

### M4. A Native session on D-SAM (API 19) with PCM music, then the settings screen

`HU=$SAM; PH=$POCO`. The session already checked D-POCO bonded to D-SAM and not to D-HU, and D-SAM at 50% or more.
Write the Native keys plus `screen-orientation` 2, start a session (cap 150 s), play Spotify, hold 5 minutes, end it,
then open the settings screen with the app stopped (its `SettingsActivity` is not exported, so through `run-as`).

**m4.sh**

```bash
HU=$SAM; PH=$POCO
thermal || untestable "host thermal"
setkeys "$HU" $NATIVE scalar:int:screen-orientation:2; keys_read "$HU" M4-pre
: > "$EV/M4-attempts.txt"
for T in M4 M4r; do n_start "$T" 150; rc=$?; echo "$T $rc" >> "$EV/M4-attempts.txt"; [ "$rc" -eq 0 ] && break; n_end "$T"; done
if [ "$rc" -eq 0 ]; then rl_sleep 20; mark "$T-music"; hold "$T" 300; n_end "$T"; fi
caps "$T-s"; settings_shot "$T" run-as $PKG; rl_cap_stop; adb -s "$HU" shell am force-stop $PKG
```

**grade-m4.sh**

```bash
read -r T RC < <(attempt M4); F=$(merged "$T-hu"); P=$(merged "$T-ph")
{ echo "M4 attempt=$T exit=$RC"; damage "$F" "$T-music" "$T-end"; slow "$F" "$T-music" "$T-end"; } > "$EV/M4-record.txt"
if [ "$RC" -ne 0 ]; then rl_verdict UNTESTABLE "no session in two attempts (L-WARN=$(rl_cnt "$F" "$T-start" "$T-end" 'never opened the Android Auto channel'))"; else
  gate "$T" "$F" "$P" rend
  c M4.1 ge "$(rl_cnt "$F" "$T-start" "$T-end" 'Media Start Request AUDIO:')" 1 "music started"   # not-literal
  c M4.2 ge "$(pcm_media "$F" "$T-start" "$T-end" PCM)" 1 "the media channel decodes PCM"
  c M4.3 eq "$(pcm_media "$F" "$T-start" "$T-end" AAC)" 0 "the media channel is not AAC"
  c M4.4 ge "$(mixlines "$F" "$T-music" "$T-end")" 28 "28 or more AudioMixer device lines in the music window"
  c M4.5 eq "$(rl_cnt "$F" "$T-start" "$T-end" 'never opened the Android Auto channel')" 0 "no L-WARN"
  c M4.6 ge "$(grep -acF 'SettingsActivity' "$EV/$T-set-focus.txt" 2>/dev/null)" 1 "the settings screen has the focus"   # not-literal: a dumpsys line
  c M4.7 ge "$(stat -c %s "$EV/$T-settings.png" 2>/dev/null)" 30000 "the settings screenshot is not blank (bytes)"
  c M4.8 eq "$(faillines "$(merged "$T-s-hu")" "$T-set" "$T-setend")" 0 "no FAIL line while the settings screen opened"
  fin
fi
early
```

- PASS: the session gate; L-MUS 1 or more; L-DEC on the media channel reads `codec=PCM` and never AAC; 28 or more L-MIX
  lines in the music window; L-WARN 0; the `M4-set` focus line names `SettingsActivity`; `M4-settings.png` is 30000
  bytes or more (a blank or black frame is far smaller; the host looks at it for a header and a list).
- Record (`M4-record.txt`): damage and its three parts, slow windows, the largest `loopGapMax`, against round 4 SA
  (29665, 0, 56 ms).

### Stage U: D-HU with D-POCO

Before M1 the session checks that D-POCO is bonded to D-HU, and asks the operator to pair them (H2) if it is not.

### M1. A Native session on D-HU (API 34), a fast Exit and Start, the log export name, the settings screen

`HU=$DHU; PH=$POCO`. A session (cap 120 s), then 30 s, then Exit and Start 0.3 s apart (`$T-c1`), the log export, the
settings screen, and the end.

**m1.sh**

```bash
HU=$DHU; PH=$POCO
thermal || untestable "host thermal"
setkeys "$HU" $NATIVE; keys_read "$HU" M1-pre
: > "$EV/M1-attempts.txt"
for T in M1 M1r; do n_start "$T" 120; rc=$?; echo "$T $rc" >> "$EV/M1-attempts.txt"; [ "$rc" -eq 0 ] && break; n_end "$T"; done
if [ "$rc" -eq 0 ]; then
  F="$EV/$T-hu.txt"; rl_sleep 30
  mark "$T-c1-exit"; send ACTION_DISCONNECT >/dev/null; sleep 0.3
  O=$(rl_offset "$F"); mark "$T-c1-go"; send ACTION_START_WIRELESS_SCAN >/dev/null
  rl_wait_line "$F" 'SSL handshake complete' 120 "$O" || echo "$T-c1 NO_SESSION" >> "$EV/notes.txt"
  rl_wait_line "$F" 'Throughput over' 30 "$(rl_offset "$F")"
  rl_sleep 20; mark "$T-c1-end"
  send ACTION_EXPORT_LOG >/dev/null; rl_sleep 8
  adb -s "$HU" shell find /storage/emulated/0/Android/data/$PKG/files -name '*_Log_*' -mmin -5 | tr -d '\r' > "$EV/$T-exports.txt"
  mark "$T-exit"; send ACTION_DISCONNECT >/dev/null; rl_sleep 5
  settings_shot "$T"; mark "$T-end"; appexit; rl_cap_stop
fi
```

**grade-m1.sh**

```bash
read -r T RC < <(attempt M1); F=$(merged "$T-hu"); P=$(merged "$T-ph")
ARM='WifiLauncher: Initializing WiFi Mode:'; GO="AutomationMarker: $T-c1-go"
arm_ms=$(gapms "$F" "$T-c1-go" "$T-c1-end" "$GO" "$ARM"); ssl_ms=$(gapms "$F" "$T-c1-go" "$T-c1-end" "$GO" 'SSL handshake complete')
{ echo "M1 attempt=$T exit=$RC go_to_arm_ms=$arm_ms go_to_ssl_ms=$ssl_ms"
  rl_win "$F" "$T-c1-exit" "$T-c1-end" | grep -aF -e "$ARM" -e 'Native AA user exit. Stopping active launcher.' -e 'CommManager teardown complete. Stopping WiFi Direct group.' | cut -c1-160
  cat "$EV/$T-exports.txt" 2>/dev/null; } > "$EV/M1-record.txt"
if [ "$RC" -ne 0 ]; then rl_verdict UNTESTABLE "no session in two attempts (L-WARN=$(rl_cnt "$F" "$T-start" "$T-end" 'never opened the Android Auto channel'))"; else
  c M1.G1 ge "$(rl_cnt "$F" "$T-start" "$T-c1-exit" 'SSL handshake complete')" 1 "first session formed"
  c M1.G2 gt "$(lastrend "$F" "$T-start" "$T-c1-exit")" 0 "first session rendered frames"
  c M1.G3 ge "$(rl_cnt "$F" "$T-c1-go" "$T-c1-end" 'SSL handshake complete')" 1 "second session formed"
  c M1.G4 gt "$(lastrend "$F" "$T-c1-go" "$T-c1-end")" 0 "second session rendered frames"
  c M1.G5 eq "$(faillines "$F" "$T-start" "$T-end")" 0 "no FATAL EXCEPTION, UnsatisfiedLinkError or ANR"
  c M1.G6 eq "$(pcrit "$P" "$T")" 0 "phone Critical error lines, the 2 s after an Exit excluded"
  c M1.1 le "${arm_ms:-999999}" 5000 "the new Start armed within 5 s of the go marker (ms)"
  c M1.2 eq "$(after "$F" "$T-c1-go" "$T-c1-end" "$ARM" 'Native AA user exit. Stopping active launcher.')" 0 "no old-Exit cleanup after the new arm"
  c M1.3 eq "$(after "$F" "$T-c1-go" "$T-c1-end" "$ARM" 'CommManager teardown complete. Stopping WiFi Direct group.')" 0 "no old-Exit group removal after the new arm"
  c M1.4 le "${ssl_ms:-999999}" 120000 "the second session within 120 s of the go marker (ms)"
  c M1.5 ge "$(grep -acF 'OHU_Log_' "$EV/$T-exports.txt" 2>/dev/null)" 1 "the export is named OHU_Log_"
  c M1.6 eq "$(grep -acF 'HUR_Log_' "$EV/$T-exports.txt" 2>/dev/null)" 0 "no export named HUR_Log_"
  c M1.7 ge "$(grep -acF 'SettingsActivity' "$EV/$T-set-focus.txt" 2>/dev/null)" 1 "the settings screen has the focus"   # not-literal: a dumpsys line
  c M1.8 ge "$(stat -c %s "$EV/$T-settings.png" 2>/dev/null)" 30000 "the settings screenshot is not blank (bytes)"
  c M1.9 eq "$(grep -acF '[ZBT]' "$F")" 0 "no module route line"
  c M1.10 eq "$(grep -acF 'XYAuto projection media policy' "$F")" 0 "no line from the XYAuto branch"   # not-literal: not on main by design
  m=$(rl_cnt "$F" "$T-start" "$T-end" 'MATCH! Starting AapService'); [ "${m:-0}" -eq 0 ] || DISCARD="M1 printed MATCH! Starting AapService $m times; rerun once"
fi
early
```

- PASS: the session gate on both sessions; between `M1-c1-go` and `M1-c1-end`, the first L-ARM within 5 s of
  `M1-c1-go`, no L-EXIT or L-GRP after that L-ARM, L-SSL within 120 s of `M1-c1-go`; `M1-exports.txt` lists a file
  whose name starts `OHU_Log_` and none that starts `HUR_Log_`; the `M1-set` focus line names `SettingsActivity` and the
  screenshot is not blank; L-ZBT 0 and L-XY 0 over the whole capture.
- Record (`M1-record.txt`): t(`M1-c1-go`) to L-ARM and to L-SSL; the L-ARM, L-EXIT and L-GRP lines of the fast cycle
  with their times.

### M3. The bundled FFmpeg H.264 decoder on `armeabi-v7a` (THE POINT)

`HU=$DHU; PH=$POCO`. INCONCLUSIVE if `abilist.txt` has no `armeabi-v7a`. Otherwise install the APK as `armeabi-v7a`. If
the install fails or `primaryCpuAbi` reads otherwise, M3 is UNTESTABLE (do not uninstall). Else write the Native keys
plus `force-software-decoding` true, `software-video-decoder` 1 and `video-codec` `H.264`, start a session (cap 120 s)
and hold 3 minutes. Then reinstall as `arm64-v8a` and restore D-HU's backup.

**m3.sh**

```bash
HU=$DHU; PH=$POCO
thermal || untestable "host thermal"
grep -q 'armeabi-v7a' "$EV/abilist.txt" || skipped "D-HU's abilist has no armeabi-v7a"
adb -s "$HU" shell am force-stop $PKG
slow_step 600 "install as armeabi-v7a"
adb -s "$HU" install -r -d --abi armeabi-v7a "$APK" > "$EV/M3-install.txt" 2>&1
adb -s "$HU" shell dumpsys package $PKG | grep -a -m1 primaryCpuAbi | tr -d '\r' > "$EV/M3-abi.txt"
if grep -q 'armeabi-v7a' "$EV/M3-abi.txt"; then
  setkeys "$HU" ${NATIVE/force-software-decoding:false/force-software-decoding:true} scalar:int:software-video-decoder:1 scalar:string:video-codec:H.264
  keys_read "$HU" M3-pre
  : > "$EV/M3-attempts.txt"
  for T in M3 M3r; do n_start "$T" 120; rc=$?; echo "$T $rc" >> "$EV/M3-attempts.txt"; [ "$rc" -eq 0 ] && break; n_end "$T"; done
  if [ "$rc" -eq 0 ]; then hold "$T" 180; n_end "$T"; fi
else
  echo "the install refused armeabi-v7a: $(cat "$EV/M3-abi.txt")" > "$EV/M3-untestable.txt"
fi
adb -s "$HU" shell am force-stop $PKG
slow_step 600 "reinstall as arm64-v8a"
adb -s "$HU" install -r -d --abi arm64-v8a "$APK" > "$EV/M3-reinstall.txt" 2>&1
adb -s "$HU" shell dumpsys package $PKG | grep -a -m1 primaryCpuAbi | tr -d '\r' > "$EV/M3-abi-after.txt"
restore_prefs "$HU" hu
```

**grade-m3.sh**

```bash
read -r T RC < <(attempt M3); F=$(merged "$T-hu"); P=$(merged "$T-ph")
FF1='Configuring bundled FFmpeg H.264/AVC decoder for'; FF2='Bundled FFmpeg H.264/AVC decoder initialized'; FF3='First bundled software H.264/AVC frame rendered'
{ echo "M3 attempt=$T exit=$RC abi=$(cat "$EV/M3-abi.txt" 2>/dev/null) after=$(cat "$EV/M3-abi-after.txt" 2>/dev/null)"
  echo "ssl_to_ff3_ms=$(gapms "$F" "$T-start" "$T-end" 'SSL handshake complete' "$FF3")"
  echo "rendered_median=$(rends "$F" "$T-start" "$T-end" | sort -n | awk '{a[NR]=$1} END{print a[int((NR+1)/2)]}') rendered_last=$(lastrend "$F" "$T-start" "$T-end")"; } > "$EV/M3-record.txt"
if [ -f "$EV/M3-untestable.txt" ] || [ -f "$EV/M3-inconclusive.txt" ]; then :
elif [ "$RC" -ne 0 ]; then rl_verdict UNTESTABLE "no session in two attempts"
elif [ "$(rl_win "$F" "$T-start" "$T-end" | grep -acF 'Configuring bundled FFmpeg ')" -eq 0 ] && ! grep -q 'force-software-decoding" value="true' "$EV/settings-M3-pre.xml"; then   # not-literal: settings.xml
  rl_verdict INCONCLUSIVE "L-FF1 0 and force-software-decoding did not read true: the keys did not take"   # not-literal
else
  gate "$T" "$F" "$P" rend
  c M3.1 ge "$(rl_cnt "$F" "$T-start" "$T-end" "$FF1")" 1 "L-FF1 the bundled decoder was configured"
  c M3.2 ge "$(rl_cnt "$F" "$T-start" "$T-end" "$FF2")" 1 "L-FF2 it initialized"
  c M3.3 ge "$(rl_cnt "$F" "$T-start" "$T-end" "$FF3")" 1 "L-FF3 it rendered a frame"
  c M3.4 eq "$(( $(before "$F" "$T-start" "$T-end" "$FF1" "$FF2") + $(before "$F" "$T-start" "$T-end" "$FF2" "$FF3") ))" 2 "L-FF1, L-FF2, L-FF3 in that order"
  c M3.5 ge "$(rl_win "$F" "$T-start" "$T-end" | grep -acF 'Media Sink Setup Request: 3 on channel VIDEO')" 1 "the phone sent H.264 (L-CODEC 3)"   # not-literal: "Request: %d on channel %s"
  c M3.6 eq "$(( $(rl_cnt "$F" "$T-start" "$T-end" 'Failed to load hur_soft_video') + $(rl_cnt "$F" "$T-start" "$T-end" 'decoder failed to initialize') + $(rl_cnt "$F" "$T-start" "$T-end" 'Stopping initialization retries for this codec') ))" 0 "no M3 FAIL line"
  fin
fi
early
```

The three FFmpeg lines are formatted from `CodecType.displayName` (`H.264/AVC`), so they are in no source file as
written; `FF1`, `FF2` and `FF3` are their run-time form.

- PASS: the session gate; L-FF1, L-FF2 and L-FF3 each 1 or more, in that order; L-CODEC reads 3; 0 M3 FAIL lines.
- INCONCLUSIVE when L-FF1 is 0 and `force-software-decoding` did not read back `true` (the keys did not take).
- Record (`M3-record.txt`): t(L-SSL) to L-FF3, the median and last `rendered=` of L-TP, and `primaryCpuAbi` after the
  reinstall (MUST read `arm64-v8a`).

### Stage H: D-HP as host (M6 first, then M5)

D-HP is on wireless adb (`$EV/hp.addr`). Battery: 60% or more to start M6; read before every cycle and every run; 30%
or below ends the cycles and makes the rest of stage H UNTESTABLE (battery). The session turns D-MOTO's radios on
before M6.

### M6. 3 dongle cycles to a picture on an API 17 host (THE POINT)

`HU=$HP_W; PH=$MOTO` (PC cable). The dongle goes on D-HP's OTG port (H3). The run waits up to 3 minutes for the host
to see it, asks for one reseat (H10), and waits 3 minutes more.

**m6.sh**

```bash
HU=$HP_W; PH=$MOTO
thermal || untestable "host thermal"
adb connect "$HU" >/dev/null 2>&1
b=$(bat); echo "M6 start bat=$b" >> "$EV/M6-notes.txt"
if [ -n "$b" ] && [ "$b" -lt 60 ]; then untestable "D-HP battery $b% is below 60%"; fi
usbwatch M6
rl_wait_line "$EV/M6-usbwatch.txt" 'USB_DEVICE_PRESENT' 180 ||   # not-literal: usbwatch's own line
  { echo "OPERATOR NOW: H10 - reseat the dongle in D-HP's OTG port"; rl_wait_line "$EV/M6-usbwatch.txt" 'USB_DEVICE_PRESENT' 180; } ||   # not-literal
  untestable "D-HP shows root hubs only after a reseat"
rl_stop "usb:"
drun M6 3
```

**grade-m6.sh**

```bash
read -r rows proj fl < <(dongle_grade M6 3)
if [ "${rows:-0}" -lt 3 ] && [ "${proj:-0}" -lt 2 ] && grep -q 'STOP battery' "$EV/M6-notes.txt" 2>/dev/null; then   # not-literal: drun's own note
  rl_verdict UNTESTABLE "battery: D-HP reached 30% after $rows cycles"
else
  c M6.1 ge "${proj:-0}" 2 "cycles that projected, of 3"
  c M6.2 eq "${fl:-0}" 0 "no FATAL EXCEPTION, UnsatisfiedLinkError or ANR"
fi
early
```

- PASS: 2 or more of 3 rows read `projected=1`, and 0 FAIL lines. FAIL: 1 or fewer.
- Record (`M6-record.txt`): per row `ssl`, `reply`, `silent_detach`, `stale`, `late_discard`, `selected`, `bat`, and
  `crit_graded` with up to 5 phone `Critical error` lines. For a cycle that did not project, quote its first `Critical
  error` line with the 5 phone lines before it. Compare with `usb-dongle-regression` round 1 H-B (3 of 3 on v.3.5.0).

### M5. A wired session on D-HP with D-MOTO and PCM music

`HU=$HP_W; PH=$MOTO_W`. The dongle comes off and D-MOTO goes on D-HP's OTG port (H4). Write the USB phone keys,
connect with `ACTION_CHECK_USB` (twice, then one replug cue), play the test file in VLC, hold 5 minutes.

**m5.sh**

```bash
HU=$HP_W; PH=$MOTO_W; VLC=org.videolan.vlc; MEDIA=/sdcard/Music/ohu-stutter-long.mp3; F="$EV/M5-hu.txt"
grep -qE 'vlc=0|mp3=0' "$EV/moto-media.txt" 2>/dev/null && untestable "VLC or $MEDIA is missing on D-MOTO"
thermal || untestable "host thermal"
adb connect "$HU" >/dev/null 2>&1; adb connect "$PH" >/dev/null 2>&1
b=$(bat); echo "M5 start bat=$b" >> "$EV/M5-notes.txt"
if [ -n "$b" ] && [ "$b" -le 30 ]; then untestable "D-HP battery $b%"; fi
usbwatch M5
rl_wait_line "$EV/M5-usbwatch.txt" 'USB_DEVICE_PRESENT' 180 ||   # not-literal
  { echo "OPERATOR NOW: H10 - replug D-MOTO into D-HP's OTG port"; rl_wait_line "$EV/M5-usbwatch.txt" 'USB_DEVICE_PRESENT' 180; } ||   # not-literal
  untestable "D-HP does not see D-MOTO"
rl_stop "usb:"
setkeys "$HU" $USBPH; keys_read "$HU" M5-pre
caps M5; launch; rl_sleep 5; O=$(rl_offset "$F"); mark M5-start; send ACTION_CHECK_USB >/dev/null
rl_wait_line "$F" 'SSL handshake complete' 60 "$O" ||
  { send ACTION_CHECK_USB >/dev/null; rl_wait_line "$F" 'SSL handshake complete' 60 "$O"; } ||
  { echo "OPERATOR NOW: replug D-MOTO into D-HP once (OK on a USB dialog, never Always)"; rl_wait_line "$F" 'SSL handshake complete' 90 "$O"; } ||
  echo "M5 NO_SESSION" >> "$EV/notes.txt"
adb -s "$PH" shell am start -a android.intent.action.VIEW -d "file://$MEDIA" -t audio/mpeg -n $VLC/.StartActivity >/dev/null
rl_wait_line "$F" 'Media Start Request AUDIO:' 20 "$O" || echo "M5 NO_MUSIC" >> "$EV/notes.txt"   # not-literal
rl_sleep 20; mark M5-music; hold M5 300; mark M5-exit; mark M5-end
adb -s "$PH" shell am force-stop $VLC; appexit; rl_cap_stop
```

**grade-m5.sh**

```bash
F=$(merged M5-hu); P=$(merged M5-ph)
{ damage "$F" M5-music M5-end; slow "$F" M5-music M5-end; rl_win "$F" M5-start M5-end | grep -aF 'Requesting USB permission for ' | cut -c1-200; } > "$EV/M5-record.txt"
if [ ! -f "$EV/M5-untestable.txt" ]; then
  gate M5 "$F" "$P" rend
  c M5.1 ge "$(rl_cnt "$F" M5-start M5-end 'Sending acc start')" 1 "the AOA switch ran"
  c M5.2 ge "$(rl_cnt "$F" M5-start M5-end 'Media Start Request AUDIO:')" 1 "music started"   # not-literal
  c M5.3 ge "$(pcm_media "$F" M5-start M5-end PCM)" 1 "the media channel decodes PCM"
  c M5.4 ge "$(mixlines "$F" M5-music M5-end)" 28 "28 or more AudioMixer device lines in the music window"
fi
early
```

- PASS: the session gate; L-ACC 1 or more; L-MUS 1 or more; L-DEC media `codec=PCM`; 28 or more L-MIX lines.
- Record (`M5-record.txt`): damage and its parts, slow windows, the largest `loopGapMax`, each L-PERM identity. Round
  4's HA ran Headunit Server, not USB, so its numbers (8441, 19) are context only.

### Stage P: D-POCO as host (M2, then M7)

### pnet. D-POCO to wireless adb

D-POCO is still on its PC cable. The session swaps its role (`headunit://exit`, then a force-stop) and checks 60%
battery. This run writes the USB phone keys on D-POCO over USB and moves it to wireless adb.

**pnet.sh**

```bash
setkeys "$POCO" $USBPH; keys_read "$POCO" pnet
to_wireless "$POCO" "$EV/poco.addr" || echo "D-POCO wireless adb failed" >> "$EV/notes.txt"
```

**grade-pnet.sh**

```bash
c 1 eq "$(grep -ac ':5555' "$EV/poco.addr" 2>/dev/null)" 1 "D-POCO answers on wireless adb"   # not-literal
c 2 ge "$(grep -acF 'name="connection-modes"' "$EV/settings-pnet.xml" 2>/dev/null)" 1 "the USB keys are on D-POCO"   # not-literal: an XML attribute
early
```

A FAIL skips M2 and M7.

### M2. A wired session with a phone as the host, and a clean unplug

`HU=$POCO_W; PH=$MOTO_W`. D-MOTO goes on D-POCO's OTG port (H5). After 60 s of session, Exit, then the operator unplugs
D-MOTO (H9).

**m2.sh**

```bash
HU=$POCO_W; PH=$MOTO_W; F="$EV/M2-hu.txt"
thermal || untestable "host thermal"
adb connect "$HU" >/dev/null 2>&1; adb connect "$PH" >/dev/null 2>&1
usbwatch M2
rl_wait_line "$EV/M2-usbwatch.txt" 'USB_DEVICE_PRESENT' 180 ||   # not-literal
  { echo "OPERATOR NOW: H10 - replug D-MOTO into D-POCO's OTG port"; rl_wait_line "$EV/M2-usbwatch.txt" 'USB_DEVICE_PRESENT' 180; } ||   # not-literal
  untestable "D-POCO does not see D-MOTO"
rl_stop "usb:"
adb -s "$HU" shell am force-stop $PKG
caps M2; launch; rl_sleep 5; O=$(rl_offset "$F"); mark M2-start; send ACTION_CHECK_USB >/dev/null
rl_wait_line "$F" 'SSL handshake complete' 60 "$O" ||
  { send ACTION_CHECK_USB >/dev/null; rl_wait_line "$F" 'SSL handshake complete' 60 "$O"; } ||
  echo "M2 NO_SESSION" >> "$EV/notes.txt"
rl_sleep 60; mark M2-exit; send ACTION_DISCONNECT >/dev/null; rl_sleep 5
O=$(rl_offset "$F"); mark M2-unplug
echo "OPERATOR NOW: H9 - unplug D-MOTO from D-POCO now"
rl_wait_line "$F" 'USB_DEVICE_DETACHED' 120 "$O"
rl_sleep 10; mark M2-end; appexit; rl_cap_stop
```

**grade-m2.sh**

```bash
F=$(merged M2-hu); P=$(merged M2-ph)
{ echo "rendered_last=$(lastrend "$F" M2-start M2-end)"; rl_win "$F" M2-start M2-end | grep -aF -e 'the phone selected ' -e 'Requesting USB permission for ' | cut -c1-220; } > "$EV/M2-record.txt"
if [ ! -f "$EV/M2-untestable.txt" ]; then
  gate M2 "$F" "$P"
  c M2.1 ge "$(rl_cnt "$F" M2-start M2-end 'Sending acc start')" 1 "the AOA switch ran"
  c M2.2 ge "$(rl_win "$F" M2-start M2-end | grep -aF 'the phone selected ' | grep -acF 'STATUS_SUCCESS')" 1 "the version answer reads STATUS_SUCCESS"
  c M2.3 ge "$(rl_cnt "$F" M2-start M2-end 'Service Discovery Request')" 1 "the phone asked for our response"
  c M2.4 ge "$(( $(rl_cnt "$F" M2-start M2-end 'Audio Focus Request') + $(rl_cnt "$F" M2-start M2-end 'Sensor Start Request') + $(rl_cnt "$F" M2-start M2-end 'Media Sink Setup Request') ))" 1 "the phone answered our response"
  c M2.5 ge "$(rl_cnt "$F" M2-unplug M2-end 'USB_DEVICE_DETACHED')" 1 "the unplug was seen"
  c M2.6 eq "$(faillines "$F" M2-unplug M2-end)" 0 "no FAIL line after the unplug"
fi
early
```

- PASS: the session gate (without the `rendered` part); L-ACC 1 or more; L-SEL reads `STATUS_SUCCESS`; L-SDR 1 or more
  and L-ANS 1 or more; L-DET 1 or more after `M2-unplug`; 0 FAIL lines from `M2-unplug` to `M2-end`.
- Video: `M2-record.txt` has the last `rendered=`. 0 is INCONCLUSIVE for video, not a FAIL, so no condition reads it.
- Record: each L-PERM identity (VID:PID), and any system USB dialog (template §7b names the two kinds).

### M7. 5 dongle cycles to a picture on an API 35 host (THE POINT)

`HU=$POCO_W; PH=$MOTO` (PC cable). The session waits until D-MOTO is back on its PC cable, then cues H6 for the dongle.

**m7.sh**

```bash
HU=$POCO_W; PH=$MOTO
thermal || untestable "host thermal"
adb connect "$HU" >/dev/null 2>&1
usbwatch M7
rl_wait_line "$EV/M7-usbwatch.txt" 'USB_DEVICE_PRESENT' 180 ||   # not-literal
  { echo "OPERATOR NOW: H10 - reseat the dongle in D-POCO's OTG port"; rl_wait_line "$EV/M7-usbwatch.txt" 'USB_DEVICE_PRESENT' 180; } ||   # not-literal
  untestable "D-POCO shows root hubs only after a reseat"
rl_stop "usb:"
drun M7 5
```

**grade-m7.sh**

```bash
read -r rows proj fl < <(dongle_grade M7 5)
c M7.2 eq "${fl:-0}" 0 "no FATAL EXCEPTION, UnsatisfiedLinkError or ANR"
if [ "${proj:-0}" -eq 3 ]; then inc "3 of 5 cycles projected"; else c M7.1 ge "${proj:-0}" 4 "cycles that projected, of 5"; fi
fin
early
```

- PASS: 4 or more of 5 rows read `projected=1`, and 0 FAIL lines. FAIL: 2 or fewer. 3 is INCONCLUSIVE. Record as M6
  (`M7-record.txt`). Compare with `usb-dongle-regression` round 1 P-B (5 of 5 on v.3.5.0).

### close. Closing, part 1

Runs whatever happened before (`always=yes`). On D-HU, D-SAM and D-POCO: `appexit`, reinstall `prior-<unit>.apk`,
check its md5, restore `settings.xml` and diff it (`restore-diff-<unit>.txt` MUST be empty). D-POCO's and D-HP's USB
grants are recorded against `prep`. D-MOTO's screen timeout goes back, and D-POCO, D-HP and D-MOTO leave wireless adb.
The run exits 1 on any mismatch. It ends with the H7 cue.

**close.sh**

```bash
rc=0
restore_unit "$DHU" hu || rc=1
restore_unit "$SAM" sam || rc=1
restore_unit "$POCO_W" poco || rc=1
adb -s "$POCO_W" shell dumpsys usb | grep -i -A4 headunitrevived > "$EV/usb-after-poco.txt"
HU=$HP_W; appexit
adb -s "$HP_W" shell dumpsys usb | grep -i -A4 headunitrevived > "$EV/usb-after-hp.txt"
adb -s "$MOTO" shell svc power stayon false
for w in "$POCO_W" "$HP_W" "$MOTO_W"; do
  case "$w" in *:5555) adb -s "$w" usb >> "$EV/wireless.log" 2>&1; adb disconnect "$w" >> "$EV/wireless.log" 2>&1 ;; esac
done
echo "OPERATOR NOW: H7 - unplug the dongle and put D-POCO back on its PC cable. Then unplug D-MOTO from the PC and plug D-HP into that port"
exit "$rc"
```

### close-hp. Closing, part 2

The session waits until D-HP is back on its PC cable (H7). D-HP's prior APK goes back over USB (`units/D-HP.md`),
then its `settings.xml`. At the end of the round D-HU, D-SAM, D-POCO and D-HP are on the PC and D-MOTO is off it,
as in `prep`. D-POCO stays paired with D-HU.

**close-hp.sh**

```bash
restore_unit "$HPU" hp || exit 1
```

## 9. Do not re-run

`pr-merge-train` rounds 1 to 4 (each PR on its own tree), and `usb-dongle-regression` round 1's arms C, A and E.
Two-finger gestures, the night overlay and the ILL+ calibration are not on `main`. The four optional audio engines
are not this round.

## 10. Report back

1. M6 and M7: projected rows of 3 and of 5, with `stale`, `late_discard` and `selected` per row.
2. M3: L-FF3 present on `armeabi-v7a`, t(L-SSL) to L-FF3, and the last `rendered=`. Or why it could not run.
3. Every other run's verdict, plus M4's and M5's damage and slow windows, and any FAIL line with its first five lines.

Put every capture, `.tsv`, record file, `notes.txt`, thermal log, screenshot and `M1-exports.txt` in
`main-smoke-round1-captures.zip` on the new release `rig-evidence-main-smoke`, with its sha256 in the results file.
