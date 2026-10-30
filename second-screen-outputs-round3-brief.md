# second-screen-outputs, round 3 brief

> **Revised 2026-10-26: C moved again.** C moved from `e85e5c10` (on `main` `e9377ff9`; it also stood at `590beceb`
> on `f9a72d48` on 2026-10-25) to `2812a70e` (tree `c0d0a04b`) on `main` `947b3ae9`. The patch was hand-resolved:
> `main` now drops video that arrives before ServiceDiscovery announces a codec, and decodes with the announced
> codec instead of the codec setting. The auxiliary lane records H.264 as its announced codec, and the branch's own
> fixed-codec parameter on `AapVideo` is gone. The ack window of both video channels now follows
> `VideoDecoderBackendPolicy`. Rebuild C and re-check the identity. No run, string or verdict rule changed. No run
> reads the aux codec or the ack window. A copy that names `e85e5c10` is stale.
>
> **Revised 2026-10-29: moved onto `rig-lib`.** The round runs as one `rig_session.py` process from the
> `rig-requires` block below. Every script is a named block built on `rig-lib/rig_lib.sh`. No file is copied
> from an earlier round's folder any more. No run, condition or build changed.

Published name: `second-screen-outputs-round3-brief.md`. Results go in `second-screen-outputs-round3-results.md`.
Evidence: create the release `rig-evidence-second-screen-outputs` again (the thread's old release went into
`rig-evidence-legacy`) and upload `second-screen-outputs-round3-captures.zip` to it.

Units: D-HU (head unit) and D-POCO (phone), with D-MOTO's radios off. D-SAM and D-HP take no part. No PC
receiver, no firewall rule, no hand steps and no injected taps.

Queue line: `second-screen-outputs-round3-brief.md` (C `2812a70e` on `fork/feat/second-screen-outputs`, on
`main` `947b3ae9`, one build, no baseline; units D-HU and D-POCO, D-MOTO radios off; a smoke after three
rebases; P1 is the point).

Estimated time: 12 min build (warm worktree), R0 3 min, O1 4 min, P1 11 min, A1 8 min, cleanup 1 min, 6 min
thermal waits. About 45 min, of which 27 min are runs.

**This is a smoke after three rebases.** The feature is off by default. The rounds before this one settled the
outputs themselves. This round checks only that a session still renders and ends cleanly, and that a session
with a second screen ends by user exit and by link loss with no hang and no crash, then reconnects and renders.

## 0. What a session needs

```rig-requires
lib 19f862828
unit D-HU   role=headunit wifi=on bt=on
unit D-POCO role=phone wifi=on bt=on bond=D-HU aa=D-HU battery=30
unit D-MOTO wifi=off bt=off
candidate C 2812a70e22159bcc4282f045eb3843ec29be6542
run R0 cap=300 script=r0.sh grade=grade-r0.sh
run O1 cap=480 script=o1.sh grade=grade-o1.sh
run P1 cap=1080 script=p1.sh grade=grade-p1.sh point=yes
run A1 cap=780 script=a1.sh grade=grade-a1.sh
run cleanup cap=180 script=cleanup.sh grade=grade-cleanup.sh always=yes
stop R0 FAIL,INCONCLUSIVE,UNTESTABLE skip=all
stop O1 INCONCLUSIVE,UNTESTABLE skip=all
```

D-POCO must be bonded to D-HU: with that bond, D-POCO's Android Auto dials D-HU only. D-MOTO's radios stay off
so that it cannot connect to D-HU. Each cap covers one in-script bring-up retry. The rig host reads
`rig_session.py view`, not this whole file.

## 1. Build and baseline

| Arm | Ref | Commit | Tree |
|---|---|---|---|
| C (candidate) | `fork/feat/second-screen-outputs` | `2812a70e22159bcc4282f045eb3843ec29be6542` | `c0d0a04b5a1da4b8c573a45c517bc26866a8c8d7` |

Seven commits on `main` `947b3ae9`. History was rewritten three times since the last round (`8fc611a4`): a rebase onto
`main` `28af450d` on 2026-10-17, one onto `main` `e9377ff9` on 2026-10-22 and a hand-resolved one onto `main`
`947b3ae9` on 2026-10-26. There is no baseline arm. O1 is the feature-off control on the candidate.

The dev host ran the JVM suite at `2812a70e`: **4390 tests, 0 failures**. The host-connection harness passed.
Do not run the unit tests on the rig.

`self-mode-media-session-round3-brief.md` also builds on `main` `947b3ae9`. If the same session runs it, build
both in one worktree, that one first, then this one.

The host builds (the rig-round skill, step 3), and the APK goes to `$EV/apk/C.apk`:

```text
git fetch fork feat/second-screen-outputs
WT=$($RIG_TOOLKIT/wt-new.sh second-screen-outputs candidate 2812a70e22159bcc4282f045eb3843ec29be6542 | tail -1)
git -C $WT rev-parse 'HEAD^{tree}'               # MUST print c0d0a04b5a1da4b8c573a45c517bc26866a8c8d7
$RIG_TOOLKIT/rig_thermal.sh wait 70
HUR_DIR=$WT MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh    # then copy the APK to $EV/apk/C.apk at once
```

R0 installs it on D-HU only and proves its identity:

- the pulled APK's md5 equals `$EV/apk/C.apk`'s;
- `ACTION_QUERY_STATE`: the `data=` line carries commit `2812a70e2215` (a `-dirty` suffix is fine);
- the DEX holds `RtpStreamOutput` (1 or more; `main` has none) and `AaPresentationSession` (1 or more; the old
  candidate `8fc611a4` has none).

## 2. What this is and why

The branch offers Android Auto a second screen on a chosen output: an Android display, a network stream, an
RTP push to a typed address, or a USB adapter. With `aux-display-enabled` off, nothing about a session changes.

**What the rebases changed.** The 2026-10-22 rebase merged `AapTransport.quit`. It now calls `main`'s new
`presentationSession.retire()` first. It then waits for the poll and send threads and for both video lane
threads, `videoLane.thread` and `auxVideoLane?.thread`. Only after those joins does it close the second-screen
hub and release TLS. `main` also added a presentation session that retires before the disconnect is published.
None of this ran on hardware with a second screen.

**The risk is a hang.** If a lane thread does not end, `quit` waits for it. `CommManager` then waits on
`awaitTermination()`, the decoder stop after it never runs, and no next session can form. The lines after the
joins are the instrument: `SecondScreen: RTP push stopped` and `SecondScreen: the RTP_PUSH output is closed` come
from the hub close, and `Decoder stopped: CommManager: doDisconnect` comes after `awaitTermination()` returns.

## 3. What is different about this round

- **No PC receiver.** The RTP push sends to the PC's address with nothing listening. Round 2 of
  `second-screen-rtp-push` measured that path (D4): ICMP port unreachable comes back, no send fails, the session
  carries on. The head unit's own 10 s report (`RTP push sent ...`) shows the stream ran. So no firewall rule is
  needed.
- **Link loss is new to this thread.** P1 takes D-POCO off the link by `svc` (never airplane mode) and holds it
  off. D-POCO's WiFi turns itself back on after a single disable (`rig-quirks/units/D-POCO.md`), so `link_loss`
  holds both radios off with `rl_hold_off` (a disable every 3 s) until the head unit ends the session. The app
  ends a silent WiFi session on its 10 s read timeout.
- **A reconnect after a user exit needs the WiFi button.** A user exit drops the P2P group and holds the stack
  down, so P1 and A1 send `ACTION_START_WIRELESS_SCAN` after the exit. That is not a send straight after a
  launch (template §4 step 4). After the link loss, the stack re-arms by itself. If no session forms in 120 s,
  P1 sends the scan once and records `SCAN2_USED`.
- **The Android display output uses an overlay display**, `overlay_display_devices=800x480/160`, as round 2's R3
  did. The aux and main decoders print the same `Throughput over` lines. `tput.py` splits them by thread id.
- **D-HU's station must be on the house WiFi.** The push binds to `wlan0`. R0 reads the address. With no
  address, P1 is UNTESTABLE ("no station for the push"), and O1 and A1 still run.
- **D-HU's `enable-audio-sink` read `false` in both earlier rounds.** No run grades audio, so leave it.
- Read `rig-quirks/topics/tooling.md`, `units/D-HU.md`, `units/D-POCO.md`, `topics/projection.md` and
  `topics/wifi.md` first. The thermal limits are the table in `tooling.md`; each run starts with
  `rl_thermal_gate 75`.

## 4. Settings keys

All runs are on D-HU, written as root with the app stopped, by `hu_keys` in `ss_lib.sh`. It reads every key
back and logs `KEYS_OK` or `KEYS_MISMATCH <keys>`. Leave the audio keys as they are: they are a deliberate
worst case. `POCO_MAC` and `HOSTIP` come from R0 (`$EV/env.sh`).

**Every run (`BASE`), the same as `second-screen-rtp-push` round 2:**

| Key | Type | Value | Why |
|---|---|---|---|
| `wifi-connection-mode` | int | `3` | Native AA |
| `native-poke-bt-macs` | set | `POCO_MAC` | wake D-POCO only |
| `native-poke-all-paired` | boolean | `false` | no other bonded device is poked |
| `native-driver-selection-mode` | int | `0` | no selector countdown |
| `onboarding-version` | int | `2` | no wizard |
| `log-level` | int | `2` | INFO carries every line in section 6 |
| `video-codec` | string | `H.264` | main sink `3` |
| `resolutionId` | int | `3` | the main picture |
| `view-mode` | int | `1` | TEXTURE |
| `force-software-decoding` | boolean | `false` | hardware main decoder |
| `software-video-decoder`, `debug-video-fault-injection` | int | delete | |
| `video-profile-starvation-cap` | boolean | delete | a latch that forces 720p30 and AAC |
| `night-mode` | int | `1` | DAY |

**Per run:**

| Key | O1 | P1 | A1 |
|---|---|---|---|
| `aux-display-enabled` | delete (default `false`) | `true` | `true` |
| `aux-output` | `RTP_PUSH` | `RTP_PUSH` | `ANDROID_DISPLAY` |
| `aux-rtp-destination` | `HOSTIP` | `HOSTIP` | delete |
| `aux-rtp-port` | `5004` | `5004` | delete |
| `aux-network-size` | `1` | `1` | delete |
| `aux-display-id` | delete | delete | `OVL` (from A1's setup) |
| `aux-network-port`, `aux-display-role`, `aux-display-content`, `preferred-display-mode`, `preferred-display-id` | delete | delete | delete |

O1 stores a full push setup with the switch off, so the only difference to P1 is the switch. `ACTION_LOG_MARKER`
is not in `AutomationCommandPolicy.CONFIGURING` at `2812a70e`, so `allow-external-configuration` is not needed.

## 5. Helpers

`rig_session.py` saves every named block to `$EV/scripts/` and sources `ss_lib.sh` before every script and
grade. Captures, waits, radios and the thermal gate are `rl_*` helpers from `rig-lib`. Every file a run writes
is in `$EV`. A run's script writes its decisions to `$EV/<RUN>.vars`, and its grade script sources that file.

**ss_lib.sh**

```bash
# ss_lib.sh: serials, paths, verbs, settings writes and greps for this round. Sourced before every script.
source ~/Coding/StudioProjects/rig-toolkit/rig_devices.sh
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
MAIN=$PKG/com.andrerinas.openheadunit.main.MainActivity
HU=$D_HU; PH=$D_POCO
SD=$EV/scripts; OUT=$EV; APK_C=$EV/apk/C.apk; SHA_C=2812a70e2215
CAP=$OUT/${RUN:-run}-hu.txt; PCAP=$OUT/${RUN:-run}-phone.txt; VARS=$OUT/${RUN:-run}.vars
[ -f "$OUT/env.sh" ] && source "$OUT/env.sh"
rm -f "$OUT/win-${RUN:-run}-"*.txt 2>/dev/null   # a window file never outlives a re-run
BASE="int:wifi-connection-mode=3 set:native-poke-bt-macs=${POCO_MAC:-} bool:native-poke-all-paired=false int:native-driver-selection-mode=0 int:onboarding-version=2 int:log-level=2 str:video-codec=H.264 int:resolutionId=3 int:view-mode=1 bool:force-software-decoding=false del:software-video-decoder del:debug-video-fault-injection del:video-profile-starvation-cap int:night-mode=1"
AUXREST="del:aux-network-port del:aux-display-role del:aux-display-content del:preferred-display-mode del:preferred-display-id"
OKEYS="del:aux-display-enabled str:aux-output=RTP_PUSH str:aux-rtp-destination=${HOSTIP:-} int:aux-rtp-port=5004 int:aux-network-size=1 del:aux-display-id $AUXREST"
PKEYS="bool:aux-display-enabled=true str:aux-output=RTP_PUSH str:aux-rtp-destination=${HOSTIP:-} int:aux-rtp-port=5004 int:aux-network-size=1 del:aux-display-id $AUXREST"

send() { local a=$1; shift; adb -s "$HU" shell am broadcast -f 0x00000020 -n "$RX" -a "com.andrerinas.openheadunit.$a" "$@"; sleep 0.3; }
# mark writes the same label into both captures, so a marker window works on the phone file too
mark() { send ACTION_LOG_MARKER --es text "$1" >/dev/null; adb -s "$PH" shell log -t AutomationMarker "$1"; sleep 0.3; }
hu_ip() { adb -s "$HU" shell ip -4 addr show wlan0 | grep -a -o 'inet [0-9.]*/[0-9]*' | cut -d' ' -f2; sleep 0.3; }
link_down() { rl_radio "$PH" wifi off; rl_radio "$PH" bt off; }
link_up() { rl_radio "$PH" bt on; rl_radio "$PH" wifi on; }

hu_keys() {  # hu_keys SPEC...: BASE plus SPEC into settings.xml as root, read back; 1 on a mismatch
  local cur=$OUT/$RUN.cur.xml new=$OUT/$RUN.new.xml own
  adb -s "$HU" shell cat /data/data/$PKG/shared_prefs/settings.xml > "$cur"; sleep 0.3
  python3 -I "$SD/ssr_keys.py" "$cur" "$new" $BASE "$@" || return 1
  adb -s "$HU" push "$new" /data/local/tmp/ss-settings.xml >/dev/null; sleep 0.3
  own=$(adb -s "$HU" shell stat -c %u:%g /data/data/$PKG | tr -d '\r'); sleep 0.3
  adb -s "$HU" shell cp /data/local/tmp/ss-settings.xml /data/data/$PKG/shared_prefs/settings.xml; sleep 0.3
  adb -s "$HU" shell chown "$own" /data/data/$PKG/shared_prefs/settings.xml; sleep 0.3
  adb -s "$HU" shell chmod 660 /data/data/$PKG/shared_prefs/settings.xml; sleep 0.3
  adb -s "$HU" shell cat /data/data/$PKG/shared_prefs/settings.xml > "$OUT/$RUN.prefs.xml"; sleep 0.3
  python3 -I "$SD/ssr_keys.py" --check "$OUT/$RUN.prefs.xml" $BASE "$@" >> "$OUT/$RUN.keys"; }

picture() {  # picture FROM_BYTE N: wait up to N throughput windows after FROM_BYTE for one that rendered a frame
  local k=1
  while [ "$k" -le "$2" ]; do
    rl_wait_count "$CAP" "$k" 'Throughput over ' 12 "$1" >/dev/null || return 1
    tail -c +$(( $1 + 1 )) "$CAP" | grep -aqE 'Throughput over [0-9]+ms: rendered=[1-9]' && return 0
    k=$((k + 1))
  done
  return 1; }

bringup() {  # bringup SPEC...: 0 a session with a picture; 1 no session; 2 a phone Critical error and no frame
  adb -s "$HU" shell am force-stop "$PKG"; sleep 1
  link_down; adb -s "$PH" shell input keyevent KEYCODE_HOME; sleep 0.3
  hu_keys "$@" || { echo "KEYS_FAIL $RUN"; return 1; }
  adb -s "$HU" logcat -G 16M; sleep 0.3; adb -s "$HU" logcat -c; sleep 0.3
  rl_cap_start "$RUN-hu" "$HU"
  rl_cap_start "$RUN-phone" "$PH" -T 1 -b main,system,crash
  adb -s "$HU" shell am start -n "$MAIN" >/dev/null; rl_sleep 3; mark "$RUN-launch"; rl_sleep 15
  local L; L=$(rl_offset "$CAP"); link_up; mark "$RUN-phone-on"
  rl_wait_line "$CAP" 'SSL handshake complete. ' 150 "$L" || { echo "SESSION_FAIL_SSL $RUN"; return 1; }
  picture "$L" 8 && { mark "$RUN-up"; return 0; }
  grep -aqF 'Critical error' "$PCAP" && { echo "PHONE_CRITICAL $RUN"; return 2; }   # phone
  send ACTION_RAISE_PROJECTION >/dev/null; echo "RAISE_USED=1" >> "$VARS"
  picture "$L" 6 && { mark "$RUN-up"; return 0; }
  grep -aqF 'Critical error' "$PCAP" && return 2   # phone
  echo "SESSION_FAIL_NO_FRAMES $RUN"; return 1; }

run_end() {  # run_end: the Exit verb and the closing markers, then stop the app and both captures
  mark "$RUN-end"; send ACTION_EXIT >/dev/null; rl_sleep 4; mark "$RUN-closed"; rl_sleep 1
  adb -s "$HU" shell am force-stop "$PKG"; sleep 0.3
  rl_cap_stop "$RUN-hu"; rl_cap_stop "$RUN-phone"; }

keep_attempt() { local f; for f in hu.txt phone.txt; do [ -f "$OUT/$RUN-$f" ] && mv "$OUT/$RUN-$f" "$OUT/$RUN-a1-$f"; done; }

session() {  # session SPEC...: bringup, one retry on 1; writes RC to the vars file
  local rc; bringup "$@"; rc=$?
  if [ "$rc" = 1 ]; then run_end; keep_attempt; echo "RETRIED=1" >> "$VARS"; bringup "$@"; rc=$?; fi
  echo "RC=$rc" >> "$VARS"
  [ "$rc" = 0 ] || run_end
  return "$rc"; }

user_exit() {  # user_exit LABEL: the Exit verb, then the quit and the decoder stop after it
  mark "$1"; local L; L=$(rl_offset "$CAP"); send ACTION_DISCONNECT >/dev/null
  rl_wait_line "$CAP" 'AapTransport quitting (clean=' 20 "$L"
  rl_wait_line "$CAP" 'Decoder stopped: CommManager: doDisconnect' 15 "$L"   # not-literal: "Decoder stopped: $reason"
  rl_sleep 3; mark "$1-end"; }

link_loss() {  # link_loss LABEL: D-POCO off the link until the session ends (cap 45 s), then 6 s more
  mark "$1"; local L t0; L=$(rl_offset "$CAP"); t0=$(date +%s)
  rl_hold_off "$PH" wifi 3; rl_hold_off "$PH" bt 3
  if rl_wait_line "$CAP" 'AapTransport quitting (clean=' 45 "$L"; then echo "LL_QUIT_AFTER_S=$(( $(date +%s) - t0 ))" >> "$VARS"
  else echo "LL_QUIT_AFTER_S=none" >> "$VARS"; fi
  rl_sleep 6
  rl_wait_line "$CAP" 'Decoder stopped: CommManager: doDisconnect' 15 "$L"   # not-literal: "Decoder stopped: $reason"
  rl_release "$PH" wifi; rl_release "$PH" bt; mark "$1-end"; }

reconnect() {  # reconnect LABEL scan|auto: wait for the next session with a picture, then mark LABEL
  local L v=${1//-/_}; L=$(rl_offset "$CAP")
  if [ "$2" = scan ]; then send ACTION_START_WIRELESS_SCAN >/dev/null; else link_up; fi
  if ! rl_wait_line "$CAP" 'SSL handshake complete. ' 120 "$L"; then
    [ "$2" = auto ] || { echo "SESSION_FAIL_$v=1" >> "$VARS"; return 1; }
    echo "SCAN2_USED=1" >> "$VARS"; send ACTION_START_WIRELESS_SCAN >/dev/null
    rl_wait_line "$CAP" 'SSL handshake complete. ' 90 "$L" || { echo "SESSION_FAIL_$v=1" >> "$VARS"; return 1; }
  fi
  picture "$L" 8 || { send ACTION_RAISE_PROJECTION >/dev/null; echo "RAISE_USED=1" >> "$VARS"; picture "$L" 6; }
  mark "$1"; }

# --- grading: counts inside marker windows; each window is cut once into its own file ---
win() { local w="$OUT/win-$(basename "$1" .txt)-$2-$3.txt"; [ -s "$w" ] || rl_win "$1" "$2" "$3" > "$w"; echo "$w"; }
# gap FILE ANCHOR STRING: tenths of a second from the first ANCHOR to the first STRING after it, or none
gap() { awk -v a="$2" -v s="$3" 'function t(l){split(l,x," ");split(x[2],h,":");return h[1]*3600+h[2]*60+h[3]}
  !f && index($0,a){f=1;t0=t($0);next} f && index($0,s){printf "%d\n",(t($0)-t0)*10+0.5;d=1;exit} END{if(!d)print "none"}' "$1"; }
pre() {  # pre: the capture ran the path under test (Native, TEXTURE, an H.264 main sink, no hotspot, a phone log)
  pre_native=$(grep -acF 'WifiLauncher: Initializing WiFi Mode: NATIVE' "$CAP")   # not-literal: "...Mode: ${newLauncher.mode}"
  pre_texture=$(grep -acF 'Projection backend: viewMode=TEXTURE ' "$CAP")   # not-literal: "viewMode=$mode "
  pre_h264=$(grep -acE 'Media Sink Setup Request: 3 on channel VIDEO[[:space:]]*$' "$CAP")   # not-literal: "%d on channel %s"
  pre_hotspot=$(grep -aE 'HotspotManager:|SoftApCredentials:' "$CAP" | grep -avc 'hotspot enabled=false')   # not-literal
  pre_gh=$(grep -ac 'GH\.' "$PCAP")   # phone
  [ "$pre_native" -ge 1 ] && [ "$pre_texture" -ge 1 ] && [ "$pre_h264" -ge 1 ] && [ "$pre_hotspot" -eq 0 ] && [ "$pre_gh" -ge 1 ]; }
sess() {  # sess FROM HOLD TO: one session; FROM..TO for the bring-up, HOLD..TO for the hold
  local w h; w=$(win "$CAP" "$1" "$3"); h=$(win "$CAP" "$2" "$3")
  ssl=$(( $(grep -acF 'SSL handshake complete. Session id' "$w") + $(grep -acF 'SSL handshake complete. No session id' "$w") ))
  ann=$(grep -acF 'Announcing an auxiliary display on ' "$w")
  ann_off=$(grep -acF 'output is not available, so one display is announced' "$w")
  lane=$(grep -acF "AapTransport: the auxiliary display's video lane is open" "$w")
  rtp_open=$(grep -acF 'SecondScreen: RTP push to ' "$w")
  rtp_sent=$(grep -acF 'RTP push sent ' "$h")   # the line is "SecondScreen: " plus "RTP push sent ..."
  start_fail=$(grep -acF 'SecondScreen: could not start the ' "$w")
  rend=$(grep -acE 'Throughput over [0-9]+ms: rendered=[1-9]' "$h")
  vid_err=$(grep -acF 'Error processing video message' "$w"); }
endck() {  # endck FROM TO: one session end
  local w; w=$(win "$CAP" "$1" "$2")
  quit=$(grep -acF 'AapTransport quitting (clean=' "$w")
  stop_aux=$(grep -acF 'Decoder stopped: CommManager: doDisconnect (second screen)' "$w")   # not-literal: "Decoder stopped: $reason"
  stop_main=$(( $(grep -acF 'Decoder stopped: CommManager: doDisconnect' "$w") - stop_aux ))   # not-literal
  q2s=$(gap "$w" 'AapTransport quitting (clean=' 'Decoder stopped: CommManager: doDisconnect')
  rtp_stopped=$(grep -acF 'SecondScreen: RTP push stopped' "$w")
  closed=$(grep -acE 'SecondScreen: the [A-Z_]+ output is closed' "$w")
  cleanup_fail=$(grep -acF 'AapTransport cleanup ' "$w")
  rd_timeout=$(grep -acF 'AapRead: WiFi read timeout (' "$w")
  rd_eof=$(grep -acF 'AapRead: Connection closed (EOF). Disconnecting.' "$w"); }
runck() {  # runck FROM TO: crash and restart signs over the whole run
  local w; w=$(win "$CAP" "$1" "$2")
  pids=$(grep -a ' OPENHU' "$w" | awk '{print $3}' | sort -u | wc -l)
  destroy=$(grep -acF 'AapService destroying' "$w")
  fatal=$(grep -acF 'FATAL EXCEPTION' "$w")   # system
  anr=$(grep -acF 'ANR in com.andrerinas.headunitrevived' "$w"); }   # system
phone() {  # phone FROM TO [FROM TO]...: phone counts inside the hold windows only
  local w=$OUT/$RUN-phone.win; : > "$w"
  while [ $# -ge 2 ]; do rl_win "$PCAP" "$1" "$2" >> "$w"; shift 2; done
  ph_markers=$(grep -acF 'AutomationMarker: ' "$w")
  ph_crit=$(grep -acF 'Critical error' "$w")   # phone
  ph_crit_file=$(grep -acF 'Critical error' "$PCAP")   # phone
  ph_noinput=$(grep -acF 'No input for display' "$w"); }   # phone
bad=""; i=0
c() { i=$((i + 1)); local l; l=$(rl_cond "$i" "$@"); echo "$l"; case "$l" in *" FAIL "*) bad="$bad ${sec:-x}" ;; esac; }
failed() { local s; for s in "$@"; do case " $bad " in *" $s "*) return 0 ;; esac; done; return 1; }
mac_ok() { [[ "$1" =~ ^([0-9A-F]{2}:){5}[0-9A-F]{2}$ ]] && [ "$1" != 02:00:00:00:00:00 ] && echo 1 || echo 0; }
```

`c` numbers the conditions and records the section (`sec`) of each one that fails; `failed` asks whether a
section failed. The phone's `Critical error` lines after an Exit are excluded by construction: every phone
window ends at the marker sent before the Exit or the link loss. `ph_crit_file` is reported beside the
windowed count.

**ssr_keys.py**

```python
# ssr_keys.py <in.xml> <out.xml> <spec>...   writes the specs into a copy of settings.xml
# ssr_keys.py --check <file.xml> <spec>...   prints KEYS_OK, or KEYS_MISMATCH and each bad key (exit 1)
# spec = int:K=V bool:K=V str:K=V set:K=a,b del:K
import sys, xml.etree.ElementTree as E
check = sys.argv[1] == "--check"
tree = E.parse(sys.argv[2] if check else sys.argv[1]); root = tree.getroot()
specs = sys.argv[3:] if not check else sys.argv[3:]
bad = []
for spec in specs:
    kind, kv = spec.split(":", 1); key, _, val = kv.partition("=")
    found = [e for e in root if e.get("name") == key]
    if check:
        if kind == "del": ok = not found
        elif len(found) != 1: ok = False
        elif kind in ("int", "bool"): ok = found[0].get("value") == val
        elif kind == "str": ok = (found[0].text or "") == val
        else: ok = sorted(x.text or "" for x in found[0]) == sorted(val.split(",") if val else [])
        if not ok: bad.append(key)
        continue
    for e in found: root.remove(e)
    if kind == "del": continue
    tag = {"int": "int", "bool": "boolean", "str": "string", "set": "set"}[kind]
    e = E.SubElement(root, tag, {"name": key})
    if kind in ("int", "bool"): e.set("value", val)
    elif kind == "str": e.text = val
    else:
        for item in (val.split(",") if val else []): E.SubElement(e, "string").text = item
if check:
    print("KEYS_OK" if not bad else "KEYS_MISMATCH " + " ".join(bad)); sys.exit(1 if bad else 0)
tree.write(sys.argv[2], encoding="utf-8", xml_declaration=True)
```

**tput.py**

```python
# tput.py <capture> <from_marker> <to_marker> [<after_marker>]
# Splits "Throughput over" lines between two markers into the auxiliary decoder (output threads that
# logged "Output Format Changed:" with width=800) and the main decoder (every other thread).
# With <after_marker>, also prints the seconds from that marker to the first aux window with rendered>=1.
import re, sys
cap, a, b = sys.argv[1], sys.argv[2], sys.argv[3]; after = sys.argv[4] if len(sys.argv) > 4 else None
lines = open(cap, "r", errors="replace").read().splitlines()
def ts(l):
    try:
        h, m, s = l.split()[1].split(":"); return int(h) * 3600 + int(m) * 60 + float(s)
    except Exception: return None
def idx(name):
    for i, l in enumerate(lines):
        if l.rstrip().endswith("AutomationMarker: " + name): return i
    return None
ia, ib = idx(a), idx(b); ix = idx(after) if after else None
if ia is None or ib is None: print("TPUT error=marker_missing from=%s to=%s" % (ia, ib)); sys.exit(1)
aux = set()
for l in lines[:ib + 1]:
    if "Output Format Changed: " in l and re.search(r"width=800[,}]", l): aux.add(l.split()[3])
rx = re.compile(r"Throughput over (\d+)ms: rendered=(\d+) \((\d+)fps\), fed=(\d+) \((\d+)fps\), dropped=(\d+)")
st = {"aux": [], "main": []}; first_aux = None
for i in range(ia, ib + 1):
    m = rx.search(lines[i])
    if not m: continue
    k = "aux" if lines[i].split()[3] in aux else "main"
    st[k].append((int(m.group(2)), int(m.group(3)), int(m.group(6))))
    if k == "aux" and ix is not None and i > ix and int(m.group(2)) >= 1 and first_aux is None: first_aux = i
for k in ("main", "aux"):
    w = st[k]; f = sorted(x[1] for x in w)
    print("TPUT %s windows=%d windows_rendered_ge1=%d median_fps=%s dropped_sum=%d"
          % (k, len(w), sum(1 for x in w if x[0] >= 1), f[len(f) // 2] if f else "na", sum(x[2] for x in w)))
print("TPUT aux_tids=%s" % ",".join(sorted(aux)) if aux else "TPUT aux_tids=none")
if ix is not None:
    t1, t2 = ts(lines[ix]), ts(lines[first_aux]) if first_aux is not None else None
    print("TPUT first_aux_picture_after_%s_s=%s" % (after, "%.1f" % (t2 - t1) if t2 is not None and t1 is not None else "none"))
```

## 6. The deciding lines

`brief_check.py` checked every string a script greps against `app/src/main` at `2812a70e`. Lines marked
`# not-literal` are built at run time from the fragments in the comment. All print at INFO, WARN or ERROR,
so `log-level` 2 carries them. Files: `$EV/<RUN>-hu.txt` (head unit) and `$EV/<RUN>-phone.txt` (phone).
Windows are marker pairs.

| Line (head unit) | Meaning |
|---|---|
| `AutomationReceiver: ` / `AutomationMarker: ` | a verb or a marker landed; a missing line voids the step |
| `WifiLauncher: Initializing WiFi Mode: NATIVE`, `Projection backend: viewMode=TEXTURE `, `Media Sink Setup Request: 3 on channel VIDEO` | `pre`: the path under test |
| `SSL handshake complete. Session id: ...` or `SSL handshake complete. No session id (full handshake).` | one session |
| `Throughput over <n>ms: rendered=<n> ...` | a picture; `tput.py` splits main from aux |
| `[ServiceDiscovery] Announcing an auxiliary display on VIDEO_AUX: RTP_PUSH 1280x720 ...` | the second screen was offered |
| `SecondScreen: the RTP_PUSH output is not available, so one display is announced` | no valid address (MUST be 0 in P1) |
| `AapTransport: the auxiliary display's video lane is open` | the phone opened the aux channel |
| `SecondScreen: RTP push to <ip>:5004 open (network=wlan0)` | the push socket opened |
| `SecondScreen: RTP push sent <n> packets, ...` | the 10 s report: the stream ran |
| `SecondScreen: could not start the <OUTPUT> output: ...` | the output did not open (MUST be 0) |
| `AapTransport quitting (clean=<b>)` | a session end started |
| `SecondScreen: RTP push stopped` / `SecondScreen: the RTP_PUSH output is closed` | the hub closed, after the lane joins |
| `Decoder stopped: CommManager: doDisconnect` | `awaitTermination()` returned (main decoder: every such line less the `(second screen)` ones) |
| `Decoder stopped: CommManager: doDisconnect (second screen)` | the aux decoder stopped (A1) |
| `AapTransport cleanup <step> failed` | a cleanup step threw (MUST be 0) |
| `AapRead: WiFi read timeout (10000ms) - connection lost.` / `AapRead: Connection closed (EOF). Disconnecting.` | how the link loss was seen (record) |
| `AapProjectionActivity: the auxiliary display is up on <id>` | the Android display output is up |
| `could not open the auxiliary display` / `cannot carry a second screen` | the Android display output failed (MUST be 0) |
| `Output Format Changed: ...width=800...` | `tput.py` marks the aux decoder's thread |
| `Error processing video message` | a lane threw (MUST be 0) |
| `AapService destroying` | the service went down inside a run (MUST be 0) |

**System lines** (head unit capture, not ours): `FATAL EXCEPTION` and `ANR in com.andrerinas.headunitrevived`,
both MUST be 0.

**Phone lines** (`$EV/<RUN>-phone.txt`, by `phone`). They are Android Auto's and drift between builds. R0
records the Gearhead version (round 2 read `18.0.164204-release.daily`).

| Line | Graded | Meaning |
|---|---|---|
| `GH.` | 1 or more | the phone capture works |
| `AutomationMarker: ` | 1 or more in the window | the markers reached the phone log |
| `Critical error` | 0 inside the hold windows | Android Auto ended a session over something we sent |
| `No input for display` | 0 inside the hold windows | the aux display has no input |

## 7. Runs

Each run starts with `rl_thermal_gate 75`. A gate that fails twice grades the run UNTESTABLE (host thermal).
A failed `pre` grades the run INCONCLUSIVE (setup): fix the keys and re-run that run once.

### R0. Identity and rig state (3 min)

**r0.sh**

```bash
adb -s "$HU" shell am force-stop "$PKG"; sleep 1
for s in "$D_SAM" "$D_HP"; do adb -s "$s" shell am force-stop "$PKG" >/dev/null 2>&1; sleep 0.3; done
rl_step "R0 D-HU state"
echo "PREFS_OWNER=$(adb -s "$HU" shell stat -c '%U:%G' /data/data/$PKG/shared_prefs | tr -d '\r')" >> "$VARS"; sleep 0.3
B=$OUT/settings-backup-HU.xml
[ -s "$B" ] || adb -s "$HU" shell cat /data/data/$PKG/shared_prefs/settings.xml > "$B"; sleep 0.3
md5sum "$B" > "$OUT/R0-backup.md5"
grep -o -E '(use-aac-audio|audio-latency-multiplier|audio-queue-capacity|enable-audio-sink|video-profile-starvation-cap)[^/]*' "$B" > "$OUT/R0-audio-keys.txt"
grep -o -E 'aux-[a-z-]*|preferred-display-[a-z]*' "$B" | sort -u > "$OUT/R0-aux-keys.txt"
OVERLAY=$(adb -s "$HU" shell settings get global overlay_display_devices | tr -d '\r'); sleep 0.3
echo "OVERLAY_WAS=$OVERLAY" >> "$VARS"
[ "$OVERLAY" = null ] || { adb -s "$HU" shell settings delete global overlay_display_devices; sleep 0.3; }
HUIP_P=$(hu_ip); HUIP=${HUIP_P%/*}
rl_step "R0 D-POCO state"
GEARHEAD=$(adb -s "$PH" shell dumpsys package com.google.android.projection.gearhead | grep -a -m1 versionName | tr -d '\r '); sleep 0.3
POCO_MAC=$(adb -s "$PH" shell settings get secure bluetooth_address | tr -d '\r' | tr a-f A-F); sleep 0.3
HOSTIP=""
[ -n "$HUIP" ] && HOSTIP=$(ip -4 route get "$HUIP" | grep -o 'src [0-9.]*' | cut -d' ' -f2)
[ -n "$HOSTIP" ] && { adb -s "$HU" shell ping -c 2 -W 2 "$HOSTIP" | tail -1 > "$OUT/R0-ping.txt"; sleep 0.3; }
printf 'POCO_MAC=%q\nHUIP=%q\nHOSTIP=%q\nGEARHEAD=%q\n' "$POCO_MAC" "$HUIP" "$HOSTIP" "$GEARHEAD" > "$OUT/env.sh"
rl_step "R0 install and identity"
adb -s "$HU" install -r -d "$APK_C" > "$OUT/R0-install.txt" 2>&1; sleep 0.3
F=$OUT/live.apk
adb -s "$HU" pull "$(adb -s "$HU" shell pm path "$PKG" | cut -d: -f2 | tr -d '\r')" "$F" >/dev/null 2>&1; sleep 0.3
echo "MD5_LIVE=$(md5sum < "$F" | cut -d' ' -f1)" >> "$VARS"
echo "MD5_WANT=$(md5sum < "$APK_C" | cut -d' ' -f1)" >> "$VARS"
echo "DEX_RTP=$(unzip -p "$F" 'classes*.dex' 2>/dev/null | strings | grep -c 'RtpStreamOutput')" >> "$VARS"
echo "DEX_PRES=$(unzip -p "$F" 'classes*.dex' 2>/dev/null | strings | grep -c 'AaPresentationSession')" >> "$VARS"
adb -s "$HU" shell am start -n "$MAIN" >/dev/null; rl_sleep 4
send ACTION_QUERY_STATE > "$OUT/R0-query.txt"
adb -s "$HU" shell am force-stop "$PKG"; sleep 0.3
```

**grade-r0.sh**

```bash
source "$VARS" 2>/dev/null
sec=r0
c eq "$( [ -n "${MD5_LIVE:-}" ] && [ "$MD5_LIVE" = "${MD5_WANT:-}" ] && echo 1 || echo 0 )" 1 'the live APK md5 equals apk/C.apk'
c ge "${DEX_RTP:-}" 1 'the DEX holds RtpStreamOutput'
c ge "${DEX_PRES:-}" 1 'the DEX holds AaPresentationSession'
c ge "$(grep -acF "$SHA_C" "$OUT/R0-query.txt")" 1 'ACTION_QUERY_STATE data= carries 2812a70e2215'
c eq "$(mac_ok "${POCO_MAC:-}")" 1 "POCO_MAC is a real address (${POCO_MAC:-empty})"
echo "NOTE HUIP=${HUIP:-none} PREFS_OWNER=${PREFS_OWNER:-} OVERLAY_WAS=${OVERLAY_WAS:-} GEARHEAD=${GEARHEAD:-}"
```

**PASS:** the live md5 equals `C.apk`'s, both DEX counts are 1 or more, the query reply carries
`2812a70e2215`, and `POCO_MAC` is a real address. A FAIL stops the round. Record `PREFS_OWNER`, the backup md5,
the audio keys, the aux keys, `OVERLAY_WAS`, `HUIP` and the Gearhead version in Setup notes.

### O1. Feature off: the session renders and ends cleanly (4 min)

Phone capture: yes.

**o1.sh**

```bash
rl_thermal_gate 75 || { echo "HOT=1" >> "$VARS"; exit 0; }
session $OKEYS || exit 0
mark O1-s1; rl_sleep 40
user_exit O1-x1
run_end
```

**grade-o1.sh**

```bash
source "$VARS" 2>/dev/null
[ "${HOT:-0}" = 1 ] && { rl_verdict UNTESTABLE "host thermal: two waits above 75C"; exit 0; }
[ "${RC:-1}" = 1 ] && { rl_verdict INCONCLUSIVE "rig: no session with a picture after the retry; stop the round"; exit 0; }
[ "${RC:-1}" = 2 ] && { rl_verdict FAIL "the phone logged Critical error at bring-up and no frame came"; exit 0; }
pre || { rl_verdict INCONCLUSIVE "setup: pre native=$pre_native texture=$pre_texture h264=$pre_h264 hotspot=$pre_hotspot gh=$pre_gh"; exit 0; }
sec=s1; sess O1-launch O1-s1 O1-x1
c eq "$ssl" 1 'ssl'; c eq "$ann" 0 'ann'; c eq "$lane" 0 'lane'; c eq "$rtp_open" 0 'rtp_open'; c ge "$rend" 3 'rend'
sec=x1; endck O1-x1 O1-x1-end
c eq "$quit" 1 'quit'; c eq "$stop_main" 1 'stop_main'; c le "$q2s" 100 'q2s in tenths of a second'; c eq "$cleanup_fail" 0 'cleanup_fail'
sec=run; runck O1-launch O1-x1-end
c eq "$pids" 1 'pids'; c eq "$destroy" 0 'destroy'; c eq "$fatal" 0 'fatal'; c eq "$anr" 0 'anr'
sec=phone; phone O1-s1 O1-x1
c eq "$ph_crit" 0 "phone Critical error in the hold window (file: $ph_crit_file)"; c ge "$ph_markers" 1 'phone markers in the window'
```

**O1 PASS:** all of these.

- `ssl` = 1, `ann` = 0, `lane` = 0, `rtp_open` = 0, `rend` >= 3.
- End: `quit` = 1, `stop_main` = 1, `q2s` <= 10 s, `cleanup_fail` = 0.
- `pids` = 1, `destroy` = 0, `fatal` = 0, `anr` = 0.
- Phone: `Critical error` = 0 in the hold window, phone markers in the window >= 1.

**FAIL:** any of those is not met. **INCONCLUSIVE (rig):** no session with a picture twice; the rig is
broken, so the round stops.

### P1. RTP push: a user exit, a reconnect, a link loss, a reconnect (11 min, THE POINT)

Phone capture: yes. One process carries three sessions. If a reconnect fails, the script carries on, and the
session after it grades as absent.

**p1.sh**

```bash
rl_thermal_gate 75 || { echo "HOT=1" >> "$VARS"; exit 0; }
[ -n "${HUIP:-}" ] || { echo "NOSTATION=1" >> "$VARS"; exit 0; }
session $PKEYS || exit 0
mark P1-s1; rl_sleep 30
user_exit P1-x1                       # session 1 ends by user exit
reconnect P1-s2 scan; rl_sleep 30     # session 2, after the WiFi button
link_loss P1-ll                       # session 2 ends by link loss
reconnect P1-s3 auto; rl_sleep 30     # session 3, after the radios come back
mark P1-s3-end
run_end
```

**grade-p1.sh**

```bash
source "$VARS" 2>/dev/null
[ "${HOT:-0}" = 1 ] && { rl_verdict UNTESTABLE "host thermal: two waits above 75C"; exit 0; }
[ "${NOSTATION:-0}" = 1 ] && { rl_verdict UNTESTABLE "no station for the push: D-HU has no wlan0 address"; exit 0; }
[ "${RC:-1}" = 1 ] && { rl_verdict INCONCLUSIVE "rig: no session with a picture after the retry"; exit 0; }
[ "${RC:-1}" = 2 ] && { rl_verdict FAIL "the phone logged Critical error at bring-up and no frame came"; exit 0; }
pre || { rl_verdict INCONCLUSIVE "setup: pre native=$pre_native texture=$pre_texture h264=$pre_h264 hotspot=$pre_hotspot gh=$pre_gh"; exit 0; }
s() { sess "$1" "$2" "$3"
  c ge "$ssl" 1 "$sec ssl"; c ge "$ann" 1 "$sec ann"; c eq "$ann_off" 0 "$sec ann_off"; c ge "$lane" 1 "$sec lane"
  c ge "$rtp_open" 1 "$sec rtp_open"; c ge "$rtp_sent" 2 "$sec rtp_sent"; c eq "$start_fail" 0 "$sec start_fail"
  c ge "$rend" 3 "$sec rend"; c eq "$vid_err" 0 "$sec vid_err"; }
e() { endck "$1" "$2"
  c eq "$quit" 1 "$sec quit"; c eq "$stop_main" 1 "$sec stop_main"; c le "$q2s" 100 "$sec q2s in tenths"
  c ge "$rtp_stopped" 1 "$sec rtp_stopped"; c ge "$closed" 1 "$sec closed"; c eq "$cleanup_fail" 0 "$sec cleanup_fail"
  echo "NOTE $sec read_timeout=$rd_timeout eof=$rd_eof"; }
sec=s1; s P1-launch P1-s1 P1-x1
sec=x1; e P1-x1 P1-x1-end
sec=s2; s P1-x1-end P1-s2 P1-ll; sf2=$start_fail
sec=ll; e P1-ll P1-ll-end; c ge "${LL_QUIT_AFTER_S:-none}" 0 'LL_QUIT_AFTER_S is a number'
sec=s3; s P1-ll-end P1-s3 P1-s3-end; sf3=$start_fail
sec=run; runck P1-launch P1-s3-end
c eq "$pids" 1 'pids'; c eq "$destroy" 0 'destroy'; c eq "$fatal" 0 'fatal'; c eq "$anr" 0 'anr'
sec=phone; phone P1-s1 P1-x1 P1-s2 P1-ll P1-s3 P1-s3-end
c eq "$ph_crit" 0 "phone Critical error in the hold windows (file: $ph_crit_file)"; c eq "$ph_noinput" 0 'phone No input for display'
c ge "$ph_markers" 3 'phone markers in the windows'
echo "NOTE SCAN2_USED=${SCAN2_USED:-0} RAISE_USED=${RAISE_USED:-0}"
if [ "${SESSION_FAIL_P1_s2:-0}" = 1 ] && [ "$sf2" = 0 ] && ! failed s1 x1; then
  rl_verdict INCONCLUSIVE "session 2 did not form after a clean end; the phone did not come back"
elif [ "${SESSION_FAIL_P1_s3:-0}" = 1 ] && [ "$sf3" = 0 ] && ! failed s1 x1 s2 ll; then
  rl_verdict INCONCLUSIVE "session 3 did not form after a clean end; the phone did not come back"
fi
```

**P1 PASS:** all of these.

- Each of the three sessions: `ssl` >= 1, `ann` >= 1, `ann_off` = 0, `lane` >= 1, `rtp_open` >= 1, `rtp_sent` >= 2,
  `start_fail` = 0, `rend` >= 3, `vid_err` = 0.
- Each of the two ends (`P1-x1`, `P1-ll`): `quit` = 1, `stop_main` = 1, `q2s` <= 10 s, `rtp_stopped` >= 1,
  `closed` >= 1, `cleanup_fail` = 0. `LL_QUIT_AFTER_S` is a number.
- `pids` = 1, `destroy` = 0, `fatal` = 0, `anr` = 0.
- Phone: `Critical error` = 0 and `No input for display` = 0 in the hold windows, phone markers >= 3.

**FAIL:** any end with `quit` = 1 and `stop_main` = 0 (the hang this round looks for), or `q2s` above 10 s, or
`closed` = 0, or `cleanup_fail` >= 1; `start_fail` >= 1 in session 2 or 3 (the hub did not let go of the output);
`LL_QUIT_AFTER_S=none` (the session outlived 45 s with the phone off the link); `pids` >= 2, `fatal` >= 1 or
`anr` >= 1; or any other PASS condition missed.

**INCONCLUSIVE:** session 2 or 3 did not form while everything before it passed and its `start_fail` = 0. The
phone did not come back, and our side ended cleanly. Record whether the phone capture shows a dial in that
window (`Creating rfcomm socket for device:`). Record `SCAN2_USED`, `read_timeout` and `eof` either way.

What a PASS would look like if the change did nothing: the same. This is a regression smoke, and `rtp_sent`
>= 2 with `lane` >= 1 is what proves the aux lane thread was alive when each `quit` had to join it.

### A1. Android display: a user exit and a reconnect (8 min)

Phone capture: yes. The aux decoder sits behind `auxVideoLane.thread` here, so its stop line is the second
instrument. With no overlay display id, A1 is UNTESTABLE ("no overlay display").

**a1.sh**

```bash
rl_thermal_gate 75 || { echo "HOT=1" >> "$VARS"; exit 0; }
adb -s "$HU" shell settings put global overlay_display_devices 800x480/160; rl_sleep 3
OVL=$(adb -s "$HU" shell dumpsys display | grep -a -o 'DisplayInfo{"Overlay #1", displayId [0-9]*' | head -1 | grep -o '[0-9]*$')   # system
[ -z "$OVL" ] && OVL=$(adb -s "$HU" shell dumpsys display | grep -a -B40 'mPrimaryDisplayDevice=Overlay #1' | grep -a -o 'mDisplayId=[0-9]*' | tail -1 | cut -d= -f2)   # system
echo "OVL=$OVL" >> "$VARS"
if [ -z "$OVL" ]; then adb -s "$HU" shell settings delete global overlay_display_devices; exit 0; fi
AKEYS="bool:aux-display-enabled=true str:aux-output=ANDROID_DISPLAY int:aux-display-id=$OVL del:aux-rtp-destination del:aux-rtp-port del:aux-network-size $AUXREST"
session $AKEYS || { adb -s "$HU" shell settings delete global overlay_display_devices; exit 0; }
mark A1-s1; rl_sleep 30
user_exit A1-x1
reconnect A1-s2 scan; rl_sleep 30
mark A1-s2-end
run_end
adb -s "$HU" shell settings delete global overlay_display_devices
```

**grade-a1.sh**

```bash
source "$VARS" 2>/dev/null
[ "${HOT:-0}" = 1 ] && { rl_verdict UNTESTABLE "host thermal: two waits above 75C"; exit 0; }
[ -n "${OVL:-}" ] || { rl_verdict UNTESTABLE "no overlay display"; exit 0; }
[ "${RC:-1}" = 1 ] && { rl_verdict INCONCLUSIVE "rig: no session with a picture after the retry"; exit 0; }
[ "${RC:-1}" = 2 ] && { rl_verdict FAIL "the phone logged Critical error at bring-up and no frame came"; exit 0; }
pre || { rl_verdict INCONCLUSIVE "setup: pre native=$pre_native texture=$pre_texture h264=$pre_h264 hotspot=$pre_hotspot gh=$pre_gh"; exit 0; }
python3 -I "$SD/tput.py" "$CAP" A1-s1 A1-x1 > "$OUT/A1-s1.tput"
python3 -I "$SD/tput.py" "$CAP" A1-x1-end A1-s2-end A1-x1-end > "$OUT/A1-s2.tput"
tv() { grep -a "^TPUT $2 " "$1" | grep -ao "$3=[^ ]*" | head -1 | cut -d= -f2; }
first=$(grep -ao 'first_aux_picture_after_A1-x1-end_s=[^ ]*' "$OUT/A1-s2.tput" | cut -d= -f2 | awk '$1 ~ /^[0-9.]+$/ {printf "%d", $1 * 10 + 0.5; next} {print "none"}')   # not-literal
sec=up; w=$(win "$CAP" A1-launch A1-x1)
c ge "$(grep -acF 'AapProjectionActivity: the auxiliary display is up on ' "$w")" 1 'up1'
c eq "$(grep -acF 'could not open the auxiliary display' "$CAP")" 0 'open_fail'
c eq "$(grep -acF 'cannot carry a second screen' "$CAP")" 0 'carry'
sec=s1; aux1=$(tv "$OUT/A1-s1.tput" aux windows_rendered_ge1)
c ge "$aux1" 1 'session 1 aux windows_rendered_ge1'; c ge "$(tv "$OUT/A1-s1.tput" main windows_rendered_ge1)" 3 'session 1 main windows_rendered_ge1'
sec=x1; endck A1-x1 A1-x1-end
c eq "$quit" 1 'quit'; c eq "$stop_main" 1 'stop_main'; c eq "$stop_aux" 1 'stop_aux'; c le "$q2s" 100 'q2s in tenths'; c eq "$cleanup_fail" 0 'cleanup_fail'
sec=s2; aux2=$(tv "$OUT/A1-s2.tput" aux windows_rendered_ge1); main2=$(tv "$OUT/A1-s2.tput" main windows_rendered_ge1)
c ge "$aux2" 1 'session 2 aux windows_rendered_ge1'; c le "$first" 900 'first aux picture after A1-x1-end, tenths'
c ge "$main2" 3 'session 2 main windows_rendered_ge1'
sec=run; runck A1-launch A1-s2-end
c eq "$pids" 1 'pids'; c eq "$destroy" 0 'destroy'; c eq "$fatal" 0 'fatal'; c eq "$anr" 0 'anr'
sec=phone; phone A1-s1 A1-x1 A1-s2 A1-s2-end
c eq "$ph_crit" 0 "phone Critical error in the hold windows (file: $ph_crit_file)"; c eq "$ph_noinput" 0 'phone No input for display'
if [ "${aux1:-0}" = 0 ]; then rl_verdict INCONCLUSIVE "session 1 has no aux picture, so the end tested nothing"
elif [ "${SESSION_FAIL_A1_s2:-0}" = 1 ] && ! failed x1; then rl_verdict INCONCLUSIVE "session 2 did not form after a clean end"
fi
```

**A1 PASS:** all of these.

- `up1` >= 1, `open_fail` = 0, `carry` = 0.
- Session 1 (`tput.py` A1-s1..A1-x1): aux `windows_rendered_ge1` >= 1, main `windows_rendered_ge1` >= 3.
- End (`A1-x1`): `quit` = 1, `stop_main` = 1, `stop_aux` = 1, `q2s` <= 10 s, `cleanup_fail` = 0.
- Session 2 (`tput.py` A1-x1-end..A1-s2-end): aux `windows_rendered_ge1` >= 1 with
  `first_aux_picture_after_A1-x1-end_s` <= 90, main `windows_rendered_ge1` >= 3.
- `pids` = 1, `destroy` = 0, `fatal` = 0, `anr` = 0. Phone: `Critical error` = 0, `No input for display` = 0.

**FAIL:** `quit` = 1 with `stop_aux` = 0 or `stop_main` = 0, or `q2s` above 10 s, or `cleanup_fail` >= 1, or
session 2 formed with a main picture and no aux picture, or any other PASS condition missed.
**INCONCLUSIVE:** session 1 has no aux picture (the output never came up, so the end tested nothing), or
session 2 did not form while the end passed.

### End of round (cleanup)

**cleanup.sh**

```bash
adb -s "$HU" shell settings delete global overlay_display_devices; sleep 0.3
echo "OVERLAY_END=$(adb -s "$HU" shell settings get global overlay_display_devices | tr -d '\r')" >> "$VARS"; sleep 0.3
adb -s "$HU" shell am force-stop "$PKG"; sleep 0.3
B=$OUT/settings-backup-HU.xml
if [ -s "$B" ]; then
  adb -s "$HU" push "$B" /data/local/tmp/ss-settings.xml >/dev/null; sleep 0.3
  own=$(adb -s "$HU" shell stat -c %u:%g /data/data/$PKG | tr -d '\r'); sleep 0.3
  adb -s "$HU" shell cp /data/local/tmp/ss-settings.xml /data/data/$PKG/shared_prefs/settings.xml; sleep 0.3
  adb -s "$HU" shell chown "$own" /data/data/$PKG/shared_prefs/settings.xml; sleep 0.3
  adb -s "$HU" shell chmod 660 /data/data/$PKG/shared_prefs/settings.xml; sleep 0.3
  adb -s "$HU" shell cat /data/data/$PKG/shared_prefs/settings.xml > "$OUT/settings-end-HU.xml"; sleep 0.3
  if diff -q "$OUT/settings-end-HU.xml" "$B" >/dev/null; then echo "RESTORED=1" >> "$VARS"; else echo "RESTORED=0" >> "$VARS"; fi
else echo "RESTORED=0" >> "$VARS"; fi
adb -s "$HU" shell rm -f /data/local/tmp/ss-settings.xml; sleep 0.3
link_up; adb -s "$PH" shell input keyevent KEYCODE_HOME; sleep 0.3
```

**grade-cleanup.sh**

```bash
source "$VARS" 2>/dev/null
c eq "${RESTORED:-0}" 1 "D-HU settings.xml equals the R0 backup"
c eq "$( [ "${OVERLAY_END:-}" = null ] && echo 1 || echo 0 )" 1 'overlay_display_devices is null'
```

`rig_requires.py baseline` puts every radio back afterwards, D-MOTO's included.

## 8. Do not re-run

- **`second-screen-rtp-push` round 2's R4** (early receiver, closed port, refused sends, resumed sends), **R5's
  stream checks** (sequence loss, IDR groups and cadence) and **C-FPS** (the push costs the main picture
  nothing: 29 fps both ways). The push path has not changed since `8fc611a4`; only its teardown has.
- **`second-screen-outputs` round 2's R2 and R2C** (the network output with two viewers, the CLUSTER role),
  **R4** (screen off and wake) and **VIEWER-SAM**. A1 repeats only R3's session end and next session.
- **N1**, the network output with no viewer. It runs only after a cost FAIL, and C-FPS passed.
- **The MacroSilicon and USB display outputs.** No rig unit has the hardware; the dev host's 4390 tests cover
  their policies.
- **The settings rows** for the address and the port. They need typing, which the rig does not do.

## 9. Report back

1. **P1:** for each end, `quit`, `stop_main`, `q2s`, `rtp_stopped`, `closed` and `cleanup_fail`, plus
   `LL_QUIT_AFTER_S` and whether `read_timeout` or `eof` named the link loss.
2. **P1 and A1 reconnects:** `ssl`, `rtp_sent` (P1) or the aux `windows_rendered_ge1` (A1) for each later
   session, and `SCAN2_USED` if it printed.
3. **Crash signs across the round:** `pids`, `fatal` and `anr` per run, and O1's `stop_main` and `q2s`.
