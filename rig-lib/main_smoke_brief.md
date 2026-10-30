# main-smoke-auto: the wireless half of the main smoke, run at will on the newest `main`

Not a queued round. `rig-lib/main_smoke.py` runs it on demand: it fetches `origin/main`, writes that commit into
this brief, builds it, runs the session with no model and no hand step, and prints one line per run. Evidence goes
to `rig-data/rounds/main-smoke-auto-<date>-<sha8>/`. Results are not committed; the runner's summary is the report.

M4, M8, M1 and M3 are the wireless runs of `main-smoke-round1-brief.md`, unchanged except that the D-HU runs wake
D-MOTO, so D-POCO keeps its D-SAM bond and no run needs a pair or a forget. H1 (Headunit Server), S1 (Self Mode) and
AU (audio output) are new. Audio is graded only on D-POCO and D-SAM: D-HU has no speakers. USB needs a cable on a
host's OTG port, so it waits for the rig's USB switch; the full round still carries it.

```rig-requires
lib ca8a86a90
candidate M c2c0e95aa514ae00c8e3ce5047701b83ca5f1264
unit D-HU role=headunit wifi=on bt=on
unit D-HU@AU bt=off
unit D-SAM role=headunit wifi=on bt=on battery=50
unit D-POCO@prep,M4,M8,H1 role=phone wifi=on bt=on
unit D-POCO@M4,M8 bond=D-SAM nobond=D-HU
unit D-POCO@S1,AU role=headunit wifi=on bt=on
unit D-POCO@M1,M3 wifi=off bt=off
unit D-MOTO@M4,M8,H1,S1 wifi=off bt=off
unit D-MOTO@AU role=phone wifi=on bt=on bond=D-POCO
unit D-MOTO@M1,M3 role=phone wifi=on bt=on bond=D-HU
confirm on D-POCO, Android Auto's developer setting "Start head unit server" is on (S1 and H1 need :5277)
run prep cap=1800 script=prep.sh grade=grade-prep.sh
run M4 cap=1200 script=m4.sh grade=grade-m4.sh
run M8 cap=1800 script=m8.sh grade=grade-m8.sh
run H1 cap=600 script=h1.sh grade=grade-h1.sh
run S1 cap=600 script=s1.sh grade=grade-s1.sh
run AU cap=1800 script=au.sh grade=grade-au.sh point=yes
run M1 cap=1200 script=m1.sh grade=grade-m1.sh point=yes
run M3 cap=1200 script=m3.sh grade=grade-m3.sh point=yes
run close cap=1200 script=close.sh always=yes
stop prep FAIL,UNTESTABLE skip=all
```

| Run | Unit | What it checks |
|---|---|---|
| prep | D-HU, D-SAM, D-POCO | backups, the prior APK, install, identity (md5, DEX, `ACTION_QUERY_STATE` commit) |
| M4 | D-SAM with D-POCO | a Native session on API 19 with PCM music, then the settings screen |
| M8 | D-SAM with D-POCO | the station after three force-stops (expected FAIL on `main` until the stand-down race is fixed) |
| H1 | D-HU with D-POCO | Headunit Server mode: discovery finds D-POCO's server on :5277 over the home WiFi, and a session renders |
| S1 | D-POCO | Self Mode: the 17.4+ path to its own head unit server, a session that renders, the audio sinks skipped, a clean end |
| AU | D-POCO with D-MOTO | audio output on a unit with speakers: PCM on AudioTrack, PCM on AAudio, and AAC, 2 minutes of VLC music each |
| M1 | D-HU with D-MOTO | a Native session, a fast Exit and Start, the log export name, the settings screen |
| M3 | D-HU with D-MOTO | the bundled FFmpeg H.264 decoder on `armeabi-v7a` |
| close | D-HU, D-SAM, D-POCO | the prior APK and `settings.xml` back, D-MOTO `stayon false` |

The deciding lines, the settings keys and each run's PASS rule are those of `main-smoke-round1-brief.md`
sections 5, 7 and 8. `main_smoke.py` lints this brief against the commit under test before it builds, so a line
that `main` stopped printing reports as a brief to update, never as a regression.

## Scripts

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
MOTO_BT=$(cat "$EV/moto.btaddr" 2>/dev/null); MOTO_BT=${MOTO_BT:-A0:46:5A:97:E4:95}
MAIN_SHA=c2c0e95aa   # main_smoke.py writes the commit under test here
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
NATIVE_HU=${NATIVE//$POCO_BT/$MOTO_BT}   # a head unit that wakes D-MOTO, so D-POCO keeps its D-SAM bond
SELFK="$COMMON scalar:int:wifi-connection-mode:3 set:connection-modes:wifi scalar:boolean:native-poke-all-paired:false del:native-poke-bt-macs scalar:boolean:auto-start-self-mode:false"
HUSRV="$COMMON scalar:int:wifi-connection-mode:1 set:connection-modes:wifi scalar:boolean:enable-audio-sink:true"
server5277() { adb -s "$1" shell cat /proc/net/tcp6 /proc/net/tcp | grep -aci ':149D'; }   # Android Auto's head unit server listens
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
  q=$(grep -ac "${MAIN_SHA:0:8}" "$EV/query-$u.txt")
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

### prep. Backups, installs and identity on D-HU, D-SAM and D-POCO

**prep.sh**

```bash
rl_step "APK and phone addresses"
echo "armv7_libavcodec=$(unzip -l "$APK" | grep -acF 'lib/armeabi-v7a/libavcodec.so')" > "$EV/prep-apk.txt"   # not-literal: an APK entry
md5sum "$APK" >> "$EV/prep-apk.txt"
for u in poco moto; do
  if [ "$u" = poco ]; then s=$POCO; d=DC:B7:2E:5E:4E:59; else s=$MOTO; d=A0:46:5A:97:E4:95; fi
  a=$(adb -s "$s" shell settings get secure bluetooth_address | tr -d '\r')
  case "$a" in ??:??:??:??:??:??) echo "$a" > "$EV/$u.btaddr" ;;
    *) echo "$d" > "$EV/$u.btaddr"; echo "$u address unread ($a); the brief's value used" >> "$EV/notes.txt" ;; esac
done
for s in "$DHU" "$SAM" "$POCO"; do
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
adb -s "$DHU" shell getprop ro.product.cpu.abilist | tr -d '\r' > "$EV/abilist.txt"
adb -s "$MOTO" shell svc power stayon true
vlc=$(adb -s "$MOTO" shell pm list packages | grep -acF 'org.videolan.vlc')   # not-literal: a package name
mp3=$(adb -s "$MOTO" shell ls -l /sdcard/Music/ohu-stutter-long.mp3 | grep -acF 'ohu-stutter-long')   # not-literal
echo "vlc=$vlc mp3=$mp3" > "$EV/moto-media.txt"
```

**grade-prep.sh**

```bash
for u in hu sam poco; do c "I.$u" eq "$(grep -acF "IDENT_OK $u" "$EV/ident.log")" 1 "$u runs main ${MAIN_SHA:0:8} (md5, DEX, query)"; done   # not-literal
c A1 eq "$(sed -n 's/^armv7_libavcodec=//p' "$EV/prep-apk.txt")" 1 "the APK carries lib/armeabi-v7a/libavcodec.so"
early
```

### M4. A Native session on D-SAM with PCM music, then the settings screen

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

### M8. D-SAM's station after a force-stop

**m8.sh**

```bash
HU=$SAM; PH=$POCO
thermal || untestable "host thermal"
setkeys "$HU" $NATIVE scalar:int:screen-orientation:2 del:stand-down-station-mode; keys_read "$HU" M8-pre
adb -s "$HU" shell dumpsys wifi | tr -d '\r' | grep -a -m1 'mNetworkInfo' > "$EV/M8-station.txt"
grep -aqF 'CONNECTED/CONNECTED' "$EV/M8-station.txt" || untestable "D-SAM is not joined to its home network, so nothing can be given back"   # not-literal: a dumpsys line
smark() { adb -s "$HU" shell log -t AutomationMarker "$1"; adb -s "$PH" shell log -t AutomationMarker "$1"; }
: > "$EV/M8-attempts.txt"
n_start M8 150; rc=$?; echo "M8 $rc" >> "$EV/M8-attempts.txt"
if [ "$rc" -ne 0 ]; then n_end M8; untestable "no first session in 150 s"; fi
F="$EV/M8-hu.txt"
for k in 1 2 3; do
  rl_sleep 30
  smark "M8-k$k"; adb -s "$HU" shell am force-stop $PKG
  O=$(rl_offset "$F"); smark "M8-r$k"; launch
  rl_wait_line "$F" 'SSL handshake complete' 150 "$O" > "$EV/M8-r$k-ssl.txt"
  rl_sleep 20
  smark "M8-e$k"
done
n_end M8
```

**grade-m8.sh**

```bash
F=$(merged M8-hu); P=$(merged M8-ph); gave=0; raced=0
if [ ! -f "$EV/M8-untestable.txt" ]; then
  for k in 1 2 3; do
    g=$(rl_cnt "$F" "M8-r$k" "M8-e$k" 'WiFi network is enabled again and should rejoin')
    sk=$(rl_cnt "$F" "M8-r$k" "M8-e$k" 'this unit is not joined to another WiFi network, so ')
    dn=$(rl_cnt "$F" "M8-r$k" "M8-e$k" 'asked this unit to leave its WiFi network')
    co=$(rl_cnt "$F" "M8-r$k" "M8-e$k" 'This unit is connected to another WiFi network while hosting')
    ssl=$(rl_cnt "$F" "M8-r$k" "M8-e$k" 'SSL handshake complete')
    nf=$(rl_cnt "$P" "M8-r$k" "M8-e$k" 'NETWORK_NOT_FOUND')   # phone
    gap=$(gapms "$F" "M8-r$k" "M8-e$k" 'WiFi network is enabled again and should rejoin' 'this unit is not joined to another WiFi network, so ')
    r=0; [ "${g:-0}" -ge 1 ] && [ "${sk:-0}" -ge 1 ] && [ -n "$gap" ] && [ "$gap" -le 3000 ] && [ "${co:-0}" -ge 1 ] && r=1
    [ "${g:-0}" -ge 1 ] && gave=$((gave + 1)); raced=$((raced + r))
    echo "cycle=$k give=$g skip=$sk standdown=$dn coex=$co give_to_skip_ms=${gap:-none} ssl=$ssl phone_not_found=$nf raced=$r" >> "$EV/M8-record.txt"
  done
  c M8.0 ge "$(rl_cnt "$F" M8-start M8-k1 'SSL handshake complete')" 1 "the first session formed"
  c M8.F eq "$(faillines "$F" M8-start M8-end)" 0 "no FATAL EXCEPTION, UnsatisfiedLinkError or ANR"
  c M8.1 ge "$gave" 1 "report: cycles where the stand-down gave the network back"
  c M8.2 eq "$raced" 0 "cycles where the station joined beside the group after a give-back (the race)"
  c M8.3 ge "$(rl_cnt "$F" "M8-r3" "M8-e3" 'SSL handshake complete')" 0 "report: SSL after the third relaunch"
  [ "$gave" -ge 1 ] || inc "no cycle gave the network back, so the trigger never fired"
  fin
fi
early
```

### H1. Headunit Server mode on D-HU with D-POCO's server

**h1.sh**

```bash
HU=$DHU; PH=$POCO; F="$EV/H1-hu.txt"
thermal || untestable "host thermal"
[ "$(server5277 "$PH")" -ge 1 ] || untestable "no :5277 listener on D-POCO (Android Auto's Start head unit server is off)"
hip=$(waddr "$HU"); pip=$(waddr "$PH"); echo "hu=$hip poco=$pip" > "$EV/H1-net.txt"
[ -n "$hip" ] && [ -n "$pip" ] || untestable "D-HU or D-POCO is not on a WiFi network (hu=$hip poco=$pip)"
adb -s "$PH" shell am force-stop $PKG
setkeys "$HU" $HUSRV; keys_read "$HU" H1-pre
caps H1; O=$(rl_offset "$F"); mark H1-start; launch
if rl_wait_line "$F" 'SSL handshake complete' 150 "$O"; then
  rl_wait_line "$F" 'Throughput over' 30 "$O"; rl_sleep 60
  mark H1-exit; send ACTION_DISCONNECT >/dev/null; rl_sleep 8
else echo "H1 NO_SESSION" >> "$EV/notes.txt"; mark H1-exit; fi
mark H1-end; appexit; rl_cap_stop
```

**grade-h1.sh**

```bash
F=$(merged H1-hu); P=$(merged H1-ph)
if [ ! -f "$EV/H1-untestable.txt" ]; then
  pip=$(sed -n 's/.*poco=//p' "$EV/H1-net.txt")
  found=$(rl_win "$F" H1-start H1-end | grep -aF 'NetworkDiscovery: Found Headunit Server on ' | grep -acF "$pip:5277")   # not-literal: the address
  c H1.1 ge "$found" 1 "discovery found D-POCO's server at $pip:5277"
  c H1.2 ge "$(rl_cnt "$F" H1-start H1-end 'SSL handshake complete')" 1 "a Headunit Server session formed"
  c H1.3 gt "$(lastrend "$F" H1-start H1-exit)" 0 "the last Throughput line before the exit rendered frames"
  c H1.4 eq "$(faillines "$F" H1-start H1-end)" 0 "no FATAL EXCEPTION, UnsatisfiedLinkError or ANR"
  c H1.5 eq "$(pcrit "$P" H1)" 0 "phone Critical error lines, the 2 s after an Exit excluded"
  [ "$(rl_cnt "$F" H1-start H1-end 'connections in a row')" -eq 0 ] || inc "Android Auto's head unit server on D-POCO stopped answering; stop and start it in its developer settings"
  fin
fi
early
```

### S1. Self Mode on D-POCO

**s1.sh**

```bash
HU=$POCO; PH=$POCO; F="$EV/S1-hu.txt"
thermal || untestable "host thermal"
[ "$(server5277 "$HU")" -ge 1 ] || untestable "no :5277 listener on D-POCO (Android Auto's Start head unit server is off)"
setkeys "$HU" $SELFK; keys_read "$HU" S1-pre
logclear "$HU"; rl_cap_start S1-hu "$HU"
launch; rl_sleep 10; O=$(rl_offset "$F"); mark S1-start
send ACTION_START_SELF_MODE >/dev/null
if rl_wait_line "$F" 'SSL handshake complete' 90 "$O"; then
  rl_wait_line "$F" 'Throughput over' 30 "$O"; rl_sleep 60
  mark S1-exit; send ACTION_DISCONNECT >/dev/null; rl_sleep 8
else echo "S1 NO_SESSION" >> "$EV/notes.txt"; mark S1-exit; fi
mark S1-end; appexit; rl_cap_stop
```

**grade-s1.sh**

```bash
F=$(merged S1-hu)
if [ ! -f "$EV/S1-untestable.txt" ]; then
  c S1.1 ge "$(rl_cnt "$F" S1-start S1-end 'SelfMode: AA 17.4+ detected. Connecting directly to Headunit Server on 127.0.0.1:5277')" 1 "the 17.4+ Self Mode path was chosen"
  c S1.2 ge "$(rl_cnt "$F" S1-start S1-end 'SSL handshake complete')" 1 "a Self Mode session formed"
  c S1.3 gt "$(lastrend "$F" S1-start S1-exit)" 0 "the last Throughput line before the exit rendered frames"
  c S1.4 ge "$(rl_cnt "$F" S1-start S1-end 'Self Mode is projecting this device to itself, so the media and speech ')" 1 "the audio sinks were skipped, as designed"
  c S1.5 ge "$(rl_cnt "$F" S1-exit S1-end 'AapService: Self Mode disconnected. Not restarting')" 1 "the exit ended Self Mode"
  c S1.6 eq "$(rl_cnt "$F" S1-start S1-end 'SelfMode: All launchers failed')" 0 "no launcher failure"
  c S1.7 eq "$(faillines "$F" S1-start S1-end)" 0 "no FATAL EXCEPTION, UnsatisfiedLinkError or ANR"
  [ "$(rl_cnt "$F" S1-start S1-end 'connections in a row')" -eq 0 ] || inc "Android Auto's head unit server on D-POCO stopped answering; stop and start it in its developer settings"
  fin
fi
early
```

### AU. Audio output on D-POCO: AudioTrack, AAudio and AAC

**au.sh**

```bash
HU=$POCO; PH=$MOTO; VLC=org.videolan.vlc; MEDIA=/sdcard/Music/ohu-stutter-long.mp3
thermal || untestable "host thermal"
grep -qE 'vlc=0|mp3=0' "$EV/moto-media.txt" 2>/dev/null && untestable "VLC or $MEDIA is missing on D-MOTO"
: > "$EV/AU-attempts.txt"
for A in AT AA AC; do
  case $A in AT) AA_=false; AAC_=false ;; AA) AA_=true; AAC_=false ;; AC) AA_=false; AAC_=true ;; esac
  K=${NATIVE_HU/use-aac-audio:false/use-aac-audio:$AAC_}; K=${K/use-aaudio-output:false/use-aaudio-output:$AA_}
  setkeys "$HU" $K; keys_read "$HU" "$A-pre"
  adb -s "$PH" shell am start -a android.intent.action.VIEW -d "file://$MEDIA" -t audio/mpeg -n $VLC/.StartActivity >/dev/null
  n_start "$A" 150; rc=$?; echo "$A $rc" >> "$EV/AU-attempts.txt"
  if [ "$rc" -eq 0 ]; then rl_sleep 20; mark "$A-music"; hold "$A" 120; fi
  n_end "$A"; adb -s "$PH" shell am force-stop $VLC
done
```

**grade-au.sh**

```bash
: > "$EV/AU-record.txt"
for A in AT AA AC; do
  rc=$(awk -v a="$A" '$1==a{print $2}' "$EV/AU-attempts.txt" 2>/dev/null | tail -n 1)
  F=$(merged "$A-hu")
  { echo "$A exit=${rc:-none}"; damage "$F" "$A-music" "$A-end"; slow "$F" "$A-music" "$A-end"; } >> "$EV/AU-record.txt"
  if [ "${rc:-9}" != 0 ]; then inc "$A: no session in two attempts"; continue; fi
  case $A in AT) dev=' AudioTrack effective='; codec=PCM ;; AA) dev=' AAudio callback effective='; codec=PCM ;; AC) dev=''; codec=AAC ;; esac
  c "$A.1" ge "$(rl_cnt "$F" "$A-start" "$A-end" 'Media Start Request AUDIO:')" 1 "$A music started"   # not-literal
  c "$A.2" ge "$(pcm_media "$F" "$A-start" "$A-end" "$codec")" 1 "$A the media channel decodes $codec"
  if [ -n "$dev" ]; then
    c "$A.3" ge "$(rl_win "$F" "$A-music" "$A-end" | grep -aF 'AudioMixer: id=' | grep -acF "$dev")" 10 "$A 10 or more device lines from the expected output"   # not-literal
  else
    c "$A.3" ge "$(rl_cnt "$F" "$A-start" "$A-end" 'AAC Decoder started for ')" 1 "$A the AAC decoder started"
  fi
  c "$A.4" eq "$(rl_cnt "$F" "$A-start" "$A-end" 'AAudio could not open; using AudioTrack')" 0 "$A no AAudio fallback"
  c "$A.5" eq "$(faillines "$F" "$A-start" "$A-end")" 0 "$A no FATAL EXCEPTION, UnsatisfiedLinkError or ANR"
done
fin
early
```

### M1. A Native session on D-HU with D-MOTO, a fast Exit and Start, the export name, the settings screen

**m1.sh**

```bash
HU=$DHU; PH=$MOTO
thermal || untestable "host thermal"
setkeys "$HU" $NATIVE_HU; keys_read "$HU" M1-pre
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
  c M1.10 ge "$(grep -acF 'XYAuto projection media policy' "$F")" 0 "report: XYAuto media policy lines (on main since #1155; D-HU is no XYAuto unit)"
  m=$(rl_cnt "$F" "$T-start" "$T-end" 'MATCH! Starting AapService'); [ "${m:-0}" -eq 0 ] || DISCARD="M1 printed MATCH! Starting AapService $m times; rerun once"
fi
early
```

### M3. The bundled FFmpeg H.264 decoder on armeabi-v7a

**m3.sh**

```bash
HU=$DHU; PH=$MOTO
thermal || untestable "host thermal"
grep -q 'armeabi-v7a' "$EV/abilist.txt" || skipped "D-HU's abilist has no armeabi-v7a"
adb -s "$HU" shell am force-stop $PKG
slow_step 600 "install as armeabi-v7a"
adb -s "$HU" install -r -d --abi armeabi-v7a "$APK" > "$EV/M3-install.txt" 2>&1
adb -s "$HU" shell dumpsys package $PKG | grep -a -m1 primaryCpuAbi | tr -d '\r' > "$EV/M3-abi.txt"
if grep -q 'armeabi-v7a' "$EV/M3-abi.txt"; then
  setkeys "$HU" ${NATIVE_HU/force-software-decoding:false/force-software-decoding:true} scalar:int:software-video-decoder:1 scalar:string:video-codec:H.264
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

### close. The prior APK and settings back

**close.sh**

```bash
rc=0
restore_unit "$DHU" hu || rc=1
restore_unit "$SAM" sam || rc=1
restore_unit "$POCO" poco || rc=1
adb -s "$MOTO" shell svc power stayon false
exit "$rc"
```
