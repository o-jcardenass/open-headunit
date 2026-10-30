# second-screen-rtp-push, round 3 brief

Published name on the transfer branch: `second-screen-rtp-push-round3-brief.md`. Results file:
`second-screen-rtp-push-round3-results.md`. The ticket's round 3 brief of revision 1 was never
published, so this is the thread's third published round. Evidence: upload to the existing release
`rig-evidence-second-screen-rtp-push`, asset `second-screen-rtp-push-round3-captures.zip`.

> **Revised 2026-10-29: moved onto `rig-lib`.** The round runs as one `rig_session.py` process from the
> `rig-requires` block below. Every script is a named block built on `rig-lib/rig_lib.sh`, and no file is copied
> from round 2's folder. S4 now runs before S3, because S3's grade reads S4's. No condition or build changed.

Units: **D-HU** (head unit), **D-POCO** (phone), and the **tester PC** as the RTP receiver, with D-MOTO's
radios off. Queue line: D-HU, D-POCO, tester PC. No other unit is used, so the README can pair this round with
a round on D-SAM or D-HP (template section 3b).

Estimated time: 15 min for the build, then about 26 min of runs (R0 3 min, S12 8 min, S4 5 min, S3 5 min,
S2, S5 and cleanup 2 min). Add up to 10 min for each thermal wait. Total about 45 min.

**S1 is the point of the round.** It asks one question that no earlier round could answer: does
Android Auto send a new keyframe on the second display when we cycle that display's video focus?

## 0. What a session needs

```rig-requires
lib 19f862828
unit D-HU   role=headunit wifi=on bt=on
unit D-POCO role=phone wifi=on bt=on bond=D-HU aa=D-HU battery=30
unit D-MOTO wifi=off bt=off
candidate C 8d4af793b5e87a7b44f1e0ac01976a8db21766f6
confirm The tester PC has ffmpeg installed, and its firewall lets UDP 5004 and 5005 in from D-HU's subnet (on ufw: sudo ufw allow proto udp from <D-HU subnet>.0/24 to any port 5004:5005). Say the exact rule you added.
run R0 cap=300 script=r0.sh grade=grade-r0.sh
run S12 cap=900 script=s12.sh grade=grade-s12.sh point=yes
run S2 cap=60 script=s2.sh grade=grade-s2.sh
run S4 cap=600 script=s4.sh grade=grade-s4.sh
run S3 cap=600 script=s3.sh grade=grade-s3.sh
run S5 cap=60 script=s5.sh grade=grade-s5.sh
run cleanup cap=180 script=cleanup.sh grade=grade-cleanup.sh always=yes hand="remove the PC firewall rule for UDP 5004 and 5005 if one was added for this round, and tell the host the command you used"
stop R0 FAIL,INCONCLUSIVE,UNTESTABLE skip=all
stop S12 UNTESTABLE skip=all
stop S4 UNTESTABLE skip=all
stop S3 UNTESTABLE skip=all
```

D-POCO must be bonded to D-HU: with that bond, D-POCO's Android Auto dials D-HU only. D-MOTO's radios stay off
so that it cannot connect to D-HU. The S12 grade is S1's verdict; S2 and S5 run no device step and grade from
the files that S12, S3 and S4 left. Each cap covers one in-script bring-up retry. The rig host reads
`rig_session.py view`, not this whole file.

## 1. Build and baseline

- **Candidate:** `fix/rtp-push-recovery` on `fork` at
  **`8d4af793b5e87a7b44f1e0ac01976a8db21766f6`**, tree `153794946844e3a07135c9ad9b505bdcee096d4f`.
  The fix is commit `d73c45df1`. The tip `8d4af793b` adds only the PC harness. Both sit on
  `feat/second-screen-outputs` at `9350946f7`.
- **This is a new branch.** No earlier round tested it, so there is no history to rewrite and no APK
  to reuse. Rounds 1 and 2 tested `feat/second-screen-outputs` at `8fc611a4`.
- **JVM tests:** 4439 on the dev host at `8d4af793b`, 0 failures, 1 skipped. The skipped one is
  `RtpPushHarnessTest`, which runs only from `tools/second-screen/rtp-harness/run.sh`. The rig runs no
  unit tests.
- **No baseline build.** S4 is the control. It is a settings change on the candidate
  (`aux-rtp-refresh-seconds` = 0), so one APK serves every run.

The host builds (the rig-round skill, step 3), and the APK goes to `$EV/apk/C.apk`. If `ls-remote` prints
another SHA or nothing, stop and report: the round has no candidate.

```text
git ls-remote fork fix/rtp-push-recovery   # MUST print 8d4af793b5e87a7b44f1e0ac01976a8db21766f6
git -C ~/Coding/StudioProjects/headunit-revived fetch fork fix/rtp-push-recovery
WT=$($RIG_TOOLKIT/wt-new.sh second-screen-rtp-push candidate 8d4af793b5e87a7b44f1e0ac01976a8db21766f6 | tail -1)
git -C $WT rev-parse 'HEAD^{tree}'         # MUST print 153794946844e3a07135c9ad9b505bdcee096d4f
$RIG_TOOLKIT/rig_thermal.sh wait 70
HUR_DIR=$WT MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh   # then copy the APK to $EV/apk/C.apk at once
```

R0 takes the PC harness from `~/Coding/StudioProjects/headunit-revived` at that SHA with `git archive`, so the
fetch above must have run.

## 2. What this is and why

Rounds 1 and 2 added `RTP_PUSH`, a second-screen output that sends the second Android Auto display
as RTP H.264 over UDP to a typed IPv4 address. The reporter's instrument cluster receives it that
way. On 2026-10-27 the reporter wrote: "I get the first frame of the map on the dashboard and then
the image does not change."

The plan (`plan-r2.md`) names the cause. The push is one way, so the receiver cannot ask for
anything. The old code asked for a keyframe once, at open, and in practice never (`e_kf_req` 0 in
every earlier run). So one lost packet froze the picture until the phone's next IDR. Round 2
measured the phone's IDR gap on this rig: 60.0 s, every time. A PC harness also found a second
cause: a keyframe sent as one burst overflows a receiver with a small socket buffer, so the repair
itself is lost.

The fix has two parts:

1. **Refresh.** When no keyframe went out for `aux-rtp-refresh-seconds` (choices 0, 5, 10, 30;
   default 10) and no request was made in that time, `RtpStreamOutput` logs
   `SecondScreen: RTP push asking for a keyframe, none sent for <ms>ms`. It then calls
   `AapTransport.requestAuxKeyframe`, which cycles the video focus of the second display only. The
   phone should answer with a new IDR on that display.
2. **Pacing.** A unit of more than 8 datagrams goes out in runs with a 1 ms pause between them. The
   10 s report counts the pauses.

The PC harness ran the real `RtpStreamOutput` against an x264 stand-in that answers a request with
an IDR 1 s later. With a 10 s refresh, a picture broken by loss recovered in 7.32 s, against
24.97 s unrecovered with the refresh off. **The stand-in answers on purpose. Whether Gearhead
answers an auxiliary focus cycle with an auxiliary IDR was never measured**: the earlier rounds fired
0 such cycles. S1 measures it.

## 3. What is different about this round

**Two method faults from round 2 must not repeat. This brief fixes both.**

- **FFmpeg started before any traffic and quit after 10 s.** Round 2's FFmpeg ended with
  `Could not find codec parameters ... unspecified size` 24 s before the push opened. In S2, FFmpeg
  starts only after the stream has run for about 185 s and a 10 s report line shows packets. It
  also gets `-analyzeduration 25000000`, so it waits 25 s for a keyframe instead of 5 s.
- **"Decodes" was graded on a frame count of an idle map.** An idle map gives many decoded frames
  that all look the same. That is also what the reported fault looks like. S2 grades on frames that
  change: the count of distinct frame hashes from FFmpeg's `framemd5`, new hashes after each route
  change, and PNG snapshots 10 s apart. Screenshots of D-HU's main screen prove that the phone's map
  moved in the same window.

**The map moves by a route, not by a mock GPS.** `ph_nav` starts Google Maps navigation on D-POCO to
`Parque Principal Marinilla`, which reached ACTIVE in 11 s in `emotionbug-batch` round 1. S2 changes
the route twice (Rionegro, then Marinilla again), and each change redraws the map. `mock_drive.sh`
is not used. In `bring-up-status-pill-and-poke-readiness` round 7 its fixes did not move Android
Auto's projected map, and it runs adb calls to D-POCO in parallel with the run's own.

**The strict receiver is the branch's `rtp_grade.py`, and the lossy forwarder is its
`lossy_proxy.py`.** Do not edit the files on the branch. `patch_tools.py` (section 5) makes rig copies
in `$EV/tools/` with four small additions, and refuses to write them if a source file's md5 differs:

- `lossy_proxy_rig.py` gets `--bind` (the branch binds `127.0.0.1`, which D-HU cannot reach across
  the LAN), `--first-wait` (the branch waits only 30 s for the first datagram, less than a
  bring-up), and one `PROXY burst t=... wall=...` line for each burst.
- `rtp_grade_rig.py` gets `--idr-log` (the PC time of each whole IDR, for S1) and `--duration` (so it
  ends on its own and still prints its `GRADE` line).

**S1 pairs two clocks.** The request is logged on D-HU, and the IDR arrives on the PC. D-HU's clock
is wrong (it read 2026-06-17 in earlier rounds), so `markt` writes the PC time of each marker's
send, and `s1_pair.py` takes the median of (head unit marker time minus PC send time). The spread of
those offsets is reported. adb adds about 0.2 to 0.5 s of delay, which makes an IDR look later, so
the 3 s window is slightly strict, never loose.

**Other facts that change the runs:**

- **Native AA, as in rounds 1 and 2.** D-HU cannot host USB (`rig-quirks/topics/tooling.md`). D-HU's
  station must stay on the house WiFi (`network=wlan0` in every earlier session). R0 checks it.
- **The PC firewall rule for UDP 5004 and 5005 may be gone again.** Round 2 found round 1's rule
  gone. The session's one `confirm` asks for it, and R0 probes it. The proxy forwards to the grader on
  `127.0.0.1:5006`, which needs no rule.
- **The rig's audio settings are a deliberate worst case.** Do not change `use-aac-audio`,
  `audio-latency-multiplier`, `audio-queue-capacity` or `enable-audio-sink`. R0 records them. Round 2
  read the multiplier as 8.
- **The phones may run Gearhead 18.1.164344**, which is not decompiled. R0 records the version. Phone
  strings in this brief are Gearhead's, not ours, and cannot be checked against our tree.
- **`ACTION_LOG_MARKER` is not in `AutomationCommandPolicy.CONFIGURING`** at the candidate, so
  `allow-external-configuration` is not needed.
- **Not covered:** the new "Refresh the picture every" row, and the address and port rows. Entry
  needs a dialog or typing. Say so in Setup notes. It is a known gap, not a finding.
- **Thermal:** every device run starts with `rl_thermal_gate 75` (the table in `rig-quirks/topics/tooling.md`).
  A gate that fails twice grades the run UNTESTABLE (host thermal), and the stop lines skip every run after it.
- **Expected INCONCLUSIVE, said now:**
  - S1, if the phone sends IDRs at least every 15 s by itself (then fewer than 5 requests go out).
  - S2, if neither the second display nor D-HU's main screen changes after a route change (the map
    did not move).
  - S3, if S4 does not reproduce the reported fault.

## 4. Settings keys

All runs are on D-HU, written as root with the app stopped, by `hu_keys` in `ss_lib.sh`. It reads every key
back and logs `KEYS_OK` or `KEYS_MISMATCH <keys>`. `POCO_MAC` and `HOSTIP` come from R0 (`$EV/env.sh`).

**Every run (`BASE`), the same as rounds 1 and 2:**

| Key | Type | Value | Why |
|---|---|---|---|
| `wifi-connection-mode` | int | `3` | Native AA wireless, the rig's only transport |
| `native-poke-bt-macs` | set | `POCO_MAC` | wake D-POCO only |
| `native-poke-all-paired` | boolean | `false` | no other bonded phone is poked |
| `native-driver-selection-mode` | int | `0` | no selector countdown |
| `onboarding-version` | int | `2` | no wizard on launch |
| `log-level` | int | `2` | INFO. Every deciding line in section 6 is INFO, WARN or ERROR |
| `video-codec` | string | `H.264` | main sink `3` |
| `resolutionId` | int | `3` | 1080p main picture |
| `view-mode` | int | `1` | TEXTURE |
| `force-software-decoding` | boolean | `false` | the main decoder on hardware |
| `software-video-decoder` | int | delete | |
| `debug-video-fault-injection` | int | delete | |
| `video-profile-starvation-cap` | boolean | delete | a latch that forces 720p30 and AAC |
| `night-mode` | int | `1` | DAY, no sensor message mid-window |

**Per run (`K10` and `K0` in `ss_lib.sh`):**

| Key | Type | S12, S3 | S4 (the control) |
|---|---|---|---|
| `aux-display-enabled` | boolean | `true` | `true` |
| `aux-output` | string | `RTP_PUSH` | `RTP_PUSH` |
| `aux-rtp-destination` | string | `HOSTIP` | `HOSTIP` |
| `aux-rtp-port` | int | `5004` | `5004` |
| `aux-rtp-refresh-seconds` | int | `10` | `0` (Off) |
| `aux-network-size` | int | `1` (1280x720) | `1` |
| `aux-network-port` | int | delete | delete |
| `aux-display-role` | string | delete (`AUXILIARY`) | delete |
| `aux-display-content` | int | delete | delete |
| `aux-display-id` | int | delete | delete |
| `preferred-display-mode` | int | delete | delete |
| `preferred-display-id` | int | delete | delete |

10 is the default of `aux-rtp-refresh-seconds`. It is written anyway, so `KEYS_OK` proves it.
No run changes the WiFi Direct identity, so leave `wifi-direct-last-identity-verdict` as found. R0 records
its value.

## 5. Every action as a verb, and the helpers

| Step | Command |
|---|---|
| marker, both units | `mark <label>` (`send ACTION_LOG_MARKER --es text <label>`, then `log -t AutomationMarker <label>` on D-POCO) |
| marker with its PC time | `markt <label>` (writes the PC time to `$EV/<RUN>.pcmarks`, then `mark`) |
| identity | `send ACTION_QUERY_STATE` |
| raise the projection, fallback only | `send ACTION_RAISE_PROJECTION` (inside `bringup`; it records `RAISE_USED`) |
| exit at run end | `send ACTION_EXIT`, then `am force-stop` (inside `run_end`) |

**No verb exists for these, and none is needed on the app:**

- Launch: `adb shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity` (inside `bringup`).
- The route: `ph_nav` sends a `google.navigation:` intent to Google Maps on D-POCO. It acts on Maps.
- The screenshots of D-HU's main screen: `adb exec-out screencap`, one at a time, never in a loop.
- The grader, the proxy and FFmpeg run on the PC, each as an `rl_bg` job that `rl_stop` ends.

**There is no tap and no hand step during the runs.** The only human steps are the firewall `confirm`
before the session and its removal at cleanup.

### Scripts

`rig_session.py` saves every named block to `$EV/scripts/` and sources `ss_lib.sh` before every script and
grade. Captures, waits, radios, background jobs and the thermal gate are `rl_*` helpers from `rig-lib`. Every
file a run writes is in `$EV`. A run's script writes its decisions to `$EV/<RUN>.vars`, and its grade script
sources that file.

**ss_lib.sh**

```bash
# ss_lib.sh: serials, paths, verbs, settings writes and greps for this round. Sourced before every script.
source ~/Coding/StudioProjects/rig-toolkit/rig_devices.sh
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
MAIN=$PKG/com.andrerinas.openheadunit.main.MainActivity
HU=$D_HU; PH=$D_POCO
SD=$EV/scripts; OUT=$EV; APK_C=$EV/apk/C.apk; SHA_C=8d4af793b5e8; SHA_FULL=8d4af793b5e87a7b44f1e0ac01976a8db21766f6
APP=~/Coding/StudioProjects/headunit-revived; TOOLS=$EV/tools; RPORT=5004; GPORT=5006
CAP=$OUT/${RUN:-run}-hu.txt; PCAP=$OUT/${RUN:-run}-phone.txt; VARS=$OUT/${RUN:-run}.vars
[ -f "$OUT/env.sh" ] && source "$OUT/env.sh"
rm -f "$OUT/win-${RUN:-run}-"*.txt 2>/dev/null   # a window file never outlives a re-run
BASE="int:wifi-connection-mode=3 set:native-poke-bt-macs=${POCO_MAC:-} bool:native-poke-all-paired=false int:native-driver-selection-mode=0 int:onboarding-version=2 int:log-level=2 str:video-codec=H.264 int:resolutionId=3 int:view-mode=1 bool:force-software-decoding=false del:software-video-decoder del:debug-video-fault-injection del:video-profile-starvation-cap int:night-mode=1"
AUXREST="int:aux-rtp-port=5004 int:aux-network-size=1 del:aux-network-port del:aux-display-role del:aux-display-content del:aux-display-id del:preferred-display-mode del:preferred-display-id"
K10="bool:aux-display-enabled=true str:aux-output=RTP_PUSH str:aux-rtp-destination=${HOSTIP:-} int:aux-rtp-refresh-seconds=10 $AUXREST"
K0="bool:aux-display-enabled=true str:aux-output=RTP_PUSH str:aux-rtp-destination=${HOSTIP:-} int:aux-rtp-refresh-seconds=0 $AUXREST"

send() { local a=$1; shift; adb -s "$HU" shell am broadcast -f 0x00000020 -n "$RX" -a "com.andrerinas.openheadunit.$a" "$@"; sleep 0.3; }
# mark writes the same label into both captures, so a marker window works on the phone file too
mark() { send ACTION_LOG_MARKER --es text "$1" >/dev/null; adb -s "$PH" shell log -t AutomationMarker "$1"; sleep 0.3; }
markt() { echo "$1 $(date +%s.%N)" >> "$OUT/$RUN.pcmarks"; mark "$1"; }   # a marker with the PC time of its send
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

keep_attempt() {  # keep_attempt [LABEL]: keep a failed first attempt's files (and LABEL's grader files) before the retry
  local f g; for f in hu.txt phone.txt pcmarks rx rx.json idr proxy; do
    for g in "$OUT/$RUN-$f" "$OUT/$RUN.$f" "$OUT/${1:-$RUN}.$f"; do [ -f "$g" ] && mv "$g" "${g%/*}/a1-${g##*/}"; done; done; }

# --- the PC side: each tool is a tracked job; rl_stop ends it and its children ---
grader_on() {  # grader_on LABEL BIND PORT DURATION_S: the strict receiver; its GRADE line lands in LABEL.rx
  rl_bg "grader:$1" python3 -I "$TOOLS/rtp_grade_rig.py" --bind "$2" --port "$3" --first-wait 400 --idle 15 \
    --duration "$4" --label "$1" --idr-log "$OUT/$1.idr" --json "$OUT/$1.rx.json" > "$OUT/$1.rx" 2>&1
  rl_sleep 1; }
proxy_on() {  # proxy_on: the lossy proxy on UDP 5004 of every interface, bursts at 30, 60 and 90 s, to the grader on 5006
  rl_bg "proxy:$RUN" python3 -I "$TOOLS/lossy_proxy_rig.py" --bind 0.0.0.0 --listen "$RPORT" --forward "127.0.0.1:$GPORT" \
    --first-wait 400 --idle 15 --drop-at 30 --drop-at 60 --drop-at 90 > "$OUT/$RUN.proxy" 2>&1
  rl_sleep 1; }
ff_s2() {  # ff_s2: FFmpeg on stream.sdp, 60 s of decode into framemd5 and a PNG every 10 s; run it with rl_bg
  timeout 100 ffmpeg -nostdin -hide_banner -loglevel info -protocol_whitelist file,udp,rtp \
    -analyzeduration 25000000 -probesize 50000000 -i "$OUT/stream.sdp" \
    -map 0:v:0 -t 60 -f framemd5 "$OUT/S2.framemd5" \
    -map 0:v:0 -t 60 -vf fps=1/10 -y "$OUT/S2-snap%02d.png" > "$OUT/S2-ffmpeg.log" 2>&1
  echo "rc=$? t=$(date +%s.%N)" > "$OUT/S2.ffexit"; }
ph_nav() {  # ph_nav DESTINATION: a driving route on D-POCO, inside its offline map area
  adb -s "$PH" shell am start -a android.intent.action.VIEW -p com.google.android.apps.maps \
    -d "google.navigation:q=$1\&mode=d" >/dev/null; sleep 0.3; }
maps_notes() { adb -s "$PH" shell dumpsys notification --noredact | grep -a NotificationRecord | grep -ac 'pkg=com.google.android.apps.maps'; sleep 0.3; }   # system
hu_shot() { local t; t=$(date +%s.%N); adb -s "$HU" exec-out screencap -p > "$OUT/$1.png"; echo "shot $t $OUT/$1.png" >> "$OUT/S2.events"; sleep 0.3; }

loss_run() {  # loss_run KEYS: the push through the lossy proxy (bursts at 30, 60, 90 s) to the strict grader
  rl_thermal_gate 75 || { echo "HOT=1" >> "$VARS"; return 0; }
  : > "$OUT/$RUN.pcmarks"
  proxy_on; grader_on "$RUN" 127.0.0.1 "$GPORT" 150
  local rc; bringup $1; rc=$?
  if [ "$rc" = 1 ]; then run_end; rl_stop "grader:"; rl_stop "proxy:"; keep_attempt; echo "RETRIED=1" >> "$VARS"
    : > "$OUT/$RUN.pcmarks"; proxy_on; grader_on "$RUN" 127.0.0.1 "$GPORT" 150; bringup $1; rc=$?; fi
  echo "RC=$rc" >> "$VARS"
  [ "$rc" = 0 ] || { rl_stop "grader:"; rl_stop "proxy:"; run_end; return 0; }
  ph_nav "Parque+Principal+Marinilla"; markt "$RUN-nav"
  rl_wait_line "$OUT/$RUN.rx" 'GRADE ' 260 >/dev/null || echo "GRADER_KILLED=1" >> "$VARS"
  rl_stop "grader:$RUN"
  markt "$RUN-gend"
  run_end
  rl_wait_line "$OUT/$RUN.proxy" 'PROXY seen=' 30 >/dev/null   # not-literal: the PC proxy prints it
  rl_stop "proxy:$RUN"; }

# --- grading: counts inside marker windows; each window is cut once into its own file ---
win() { local w="$OUT/win-$(basename "$1" .txt)-$2-$3.txt"; [ -s "$w" ] || rl_win "$1" "$2" "$3" > "$w"; echo "$w"; }
pk() { grep -ao "\b$1=[^ ]*" "$2" 2>/dev/null | head -1 | cut -d= -f2; }   # pk KEY FILE: the first KEY=value
x100() { awk -v v="$1" 'BEGIN { if (v ~ /^-?[0-9]+(\.[0-9]+)?$/) printf "%d", v * 100 + (v < 0 ? -0.5 : 0.5); else print "none" }'; }
num() { [[ "$1" =~ ^-?[0-9]+$ ]] && echo "$1" || echo "$2"; }
pre() {  # pre: the capture ran the path under test (Native, TEXTURE, an H.264 main sink, no hotspot, a phone log)
  pre_native=$(grep -acF 'WifiLauncher: Initializing WiFi Mode: NATIVE' "$CAP")   # not-literal: "...Mode: ${newLauncher.mode}"
  pre_texture=$(grep -acF 'Projection backend: viewMode=TEXTURE ' "$CAP")   # not-literal: "viewMode=$mode "
  pre_h264=$(grep -acE 'Media Sink Setup Request: 3 on channel VIDEO[[:space:]]*$' "$CAP")   # not-literal: "%d on channel %s"
  pre_hotspot=$(grep -aE 'HotspotManager:|SoftApCredentials:' "$CAP" | grep -avc 'hotspot enabled=false')   # not-literal
  pre_gh=$(grep -ac 'GH\.' "$PCAP")   # phone
  [ "$pre_native" -ge 1 ] && [ "$pre_texture" -ge 1 ] && [ "$pre_h264" -ge 1 ] && [ "$pre_hotspot" -eq 0 ] && [ "$pre_gh" -ge 1 ]; }
push_counts() {  # push_counts RUN FROM TO: head unit counts inside one marker window, as e_name=value lines
  local W; W=$(win "$OUT/$1-hu.txt" "$2" "$3")
  echo "window=$2..$3 lines=$(wc -l < "$W")"
  echo "e_announce=$(grep -acF 'Announcing an auxiliary display on ' "$W") e_unavail=$(grep -acF 'output is not available, so one display is announced' "$W") e_lane=$(grep -acF "AapTransport: the auxiliary display's video lane is open" "$W")"
  echo "e_open=$(grep -acF 'SecondScreen: RTP push to ' "$W") e_outopen=$(grep -acF 'SecondScreen: the RTP_PUSH output is open' "$W") e_nostart=$(grep -acF 'SecondScreen: could not start the ' "$W")"   # not-literal: "the ${output.name} output is open"
  local O; O=$(grep -aF 'SecondScreen: RTP push to ' "$W" | head -1)
  echo "e_network=$(echo "$O" | sed -n 's/.* open (network=\(.*\), refresh=\([0-9]*\)ms)[[:space:]]*$/\1/p') e_refresh_ms=$(echo "$O" | sed -n 's/.* open (network=\(.*\), refresh=\([0-9]*\)ms)[[:space:]]*$/\2/p')"
  grep -aoE 'RTP push sent [0-9]+ packets, [0-9]+ bytes, [0-9]+ keyframes in [0-9]+ms, [0-9]+ keyframes carried their own SPS and PPS, [0-9]+ refreshes asked, [0-9]+ pacing pauses' "$W" \
    | awk '{n++; p+=$4; b+=$6; k+=$8; if ($8 > 0) kl++; j+=$12; r+=$20; q+=$23} END {printf "e_sent_lines=%d e_packets=%d e_bytes=%d e_keyframes=%d e_kf_lines=%d e_kf_own=%d e_refreshes=%d e_pauses=%d\n", n, p, b, k, kl, j, r, q}'
  echo "e_asks=$(grep -acF 'SecondScreen: RTP push asking for a keyframe, none sent for ' "$W") e_cyc_push=$(grep -acF "cycling the auxiliary display's video focus for a keyframe (the second screen asked for one)" "$W") e_cyc_all=$(grep -acF "AapTransport: cycling the auxiliary display's video focus for a keyframe (" "$W") e_kf_early=$(grep -acF 'AapTransport: no auxiliary keyframe cycle before its setup ()' "$W")"   # not-literal: "... ($reason)"
  echo "e_aux_stop_cycle=$(grep -acF 'Auxiliary Video Sink Stopped -> Ignored (Forced Keyframe Request)' "$W") e_aux_stop_other=$(grep -acF 'Auxiliary Video Sink Stopped -> the main session continues' "$W") e_aux_start=$(grep -acF 'Media Start Request VIDEO_AUX: ' "$W") e_aux_released=$(grep -acF "The phone released the second display's video. The main session continues." "$W")"   # not-literal: "Media Start Request %s: "
  echo "e_main_stop=$(grep -acE 'Media Sink Stop Request: VIDEO[[:space:]]*$' "$W") e_main_vss=$(grep -aF 'Video Sink Stopped -> ' "$W" | grep -avcF 'Auxiliary') e_main_retake=$(grep -acF 'retaking video focus to complete the keyframe cycle' "$W") e_main_start=$(grep -acF 'Media Start Request VIDEO: ' "$W") e_main_setup=$(grep -acE 'Media Sink Setup Request: [0-9]+ on channel VIDEO[[:space:]]*$' "$W")"   # not-literal: "Media Sink Stop Request: " + channel
  echo "e_fail_lines=$(grep -acF 'SecondScreen: RTP push send failed (' "$W") e_ssl=$(grep -acF 'SSL handshake complete. ' "$W") e_destroy=$(grep -acF 'AapService destroying' "$W") e_errors=$(grep -acF 'Error processing video message' "$W") e_shed=$(grep -acF ' messages behind, shedding' "$W")"
  echo "e_stopped=$(grep -acF 'SecondScreen: RTP push stopped' "$W") e_auxdec=$(grep -acF 'Decoder stopped: CommManager: doDisconnect (second screen)' "$W")"; }   # not-literal: "Decoder stopped: $reason"
phone() {  # phone FROM TO: phone counts inside one window of $PCAP
  local w; w=$(win "$PCAP" "$1" "$2")
  ph_markers=$(grep -acF 'AutomationMarker: ' "$w")
  ph_crit=$(grep -acF 'Critical error' "$w")   # phone
  ph_crit_file=$(grep -acF 'Critical error' "$PCAP")   # phone
  ph_noinput=$(grep -acF 'No input for display' "$w")   # phone
  ph_early=$(grep -acF 'Video focus gained before configurations received' "$w"); }   # phone
bad=""; i=0
c() { i=$((i + 1)); local l; l=$(rl_cond "$i" "$@"); echo "$l"; case "$l" in *" FAIL "*) bad="$bad ${sec:-x}" ;; esac; }
failed() { local s; for s in "$@"; do case " $bad " in *" $s "*) return 0 ;; esac; done; return 1; }
mac_ok() { [[ "$1" =~ ^([0-9A-F]{2}:){5}[0-9A-F]{2}$ ]] && [ "$1" != 02:00:00:00:00:00 ] && echo 1 || echo 0; }
yes1() { [ "$1" = "$2" ] && echo 1 || echo 0; }
```

`c` numbers the conditions and records the section (`sec`) of each one that fails; `failed` asks whether a
section failed. `e_main_vss` counts every main-display `Video Sink Stopped -> ` line, the forced and the normal
one. `RTP_PUSH` builds no aux decoder, so every `Throughput over` line is the main decoder's. If a `push_counts`
value comes back empty rather than a number, the pattern is wrong: say so in Setup notes.

**udp_probe.sh** (pushed to D-HU and run there)

```sh
#!/system/bin/sh
# udp_probe.sh <ip> <port> : five UDP datagrams from D-HU by toybox nc, each bounded at 3 s
i=0; while [ $i -lt 5 ]; do echo "ohu-udp-probe-$i" | timeout 3 toybox nc -u -w 1 "$1" "$2" 2>&1 | head -2; i=$((i+1)); done
echo PROBE_SENT
```

**udp_rx_probe.py** (the probe half of round 1's `rtp_recv.py`)

```python
# udp_rx_probe.py <port> <secs> : counts any datagrams, prints PROBE datagrams=N from=<ips>
import socket, sys, time
port, secs = int(sys.argv[1]), float(sys.argv[2])
s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM); s.bind(("0.0.0.0", port)); s.settimeout(0.5)
n = 0; src = set(); end = time.time() + secs
while time.time() < end:
    try:
        d, a = s.recvfrom(65535); n += 1; src.add(a[0])
    except socket.timeout:
        pass
print("PROBE datagrams=%d from=%s" % (n, ",".join(sorted(src)) or "none"), flush=True)
```

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

**fps30.py**

```python
# fps30.py <capture> <from_marker> [<n>] : the first <n> (default 30) "Throughput over" windows after the marker
import re, sys
cap, a = sys.argv[1], sys.argv[2]; n = int(sys.argv[3]) if len(sys.argv) > 3 else 30
rx = re.compile(r"Throughput over (\d+)ms: rendered=(\d+) \((\d+)fps\), fed=(\d+) \((\d+)fps\), dropped=(\d+)")
on = False; w = []
for l in open(cap, "r", errors="replace"):
    if not on:
        on = l.rstrip().endswith("AutomationMarker: " + a); continue
    m = rx.search(l)
    if m:
        w.append((int(m.group(2)), int(m.group(3)), int(m.group(5)), int(m.group(6))))
        if len(w) == n: break
if not on: print("FPS30 error=marker_missing from=%s" % a); sys.exit(1)
med = lambda xs: sorted(xs)[len(xs) // 2] if xs else "na"
print("FPS30 from=%s windows=%d rendered_ge1=%d median_fps=%s median_fed_fps=%s dropped_sum=%d"
      % (a, len(w), sum(1 for x in w if x[0] >= 1), med([x[1] for x in w]), med([x[2] for x in w]), sum(x[3] for x in w)))
```

**patch_tools.py**

```python
# patch_tools.py <harness_dir> : rig copies of the branch's lossy_proxy.py and rtp_grade.py.
# Adds --bind and --first-wait to the proxy and a burst line; adds --idr-log and --duration to the grader.
# Each replacement must match exactly once, or nothing is written.
import hashlib, os, sys
src = sys.argv[1]
WANT = {"lossy_proxy.py": "c68135181e9f3514ad10dd8ec2e2ac43", "rtp_grade.py": "b6701fd9f44cc1d514874f92671c3d67"}
EDITS = {
    "lossy_proxy.py": [
        ('    ap.add_argument("--seed", type=int, default=1)\n',
         '    ap.add_argument("--seed", type=int, default=1)\n'
         '    ap.add_argument("--bind", default="127.0.0.1")\n'
         '    ap.add_argument("--first-wait", type=float, default=30.0)\n'),
        ('    rx.bind(("127.0.0.1", a.listen))\n    rx.settimeout(30.0)\n',
         '    rx.bind((a.bind, a.listen))\n    rx.settimeout(a.first_wait)\n'),
        ('            burst_left = a.drop_count\n',
         '            burst_left = a.drop_count\n'
         '            print(f"PROXY burst t={t:.2f} wall={time.time():.3f}", flush=True)\n'),
    ],
    "rtp_grade.py": [
        ('    ap.add_argument("--label", default="run")\n',
         '    ap.add_argument("--label", default="run")\n'
         '    ap.add_argument("--idr-log")\n'
         '    ap.add_argument("--duration", type=float, default=0.0)\n'),
        ('    out = open(a.annexb, "wb") if a.annexb else None\n',
         '    out = open(a.annexb, "wb") if a.annexb else None\n'
         '    idr_log = open(a.idr_log, "w", buffering=1) if a.idr_log else None\n'),
        ('                idr_times.append(t)\n',
         '                idr_times.append(t)\n'
         '                if idr_log:\n'
         '                    idr_log.write("%.3f %.3f %d\\n" % (time.time(), t, int(have_sps and have_pps)))\n'),
        ('        t = now - t0\n        last_t = t\n',
         '        t = now - t0\n        if a.duration and t >= a.duration:\n            break\n        last_t = t\n'),
    ],
}
out = {}
for name, edits in EDITS.items():
    text = open(os.path.join(src, name)).read()
    md5 = hashlib.md5(text.encode()).hexdigest()
    if md5 != WANT[name]:
        print("PATCH_FAIL %s md5=%s want=%s" % (name, md5, WANT[name])); sys.exit(1)
    for old, new in edits:
        if text.count(old) != 1:
            print("PATCH_FAIL %s edit_matches=%d: %r" % (name, text.count(old), old[:60])); sys.exit(1)
        text = text.replace(old, new)
    out[name.replace(".py", "_rig.py")] = text
for name, text in out.items():
    open(name, "w").write(text)
    print("PATCH_OK %s md5=%s" % (name, hashlib.md5(text.encode()).hexdigest()))
```

It must print `PATCH_OK lossy_proxy_rig.py md5=db455889b4eab3e50c6cad32adf94dc5` and
`PATCH_OK rtp_grade_rig.py md5=38e9d5b4796ee1b2cf432a5d4b2b34ba`. On the brief's host, the two copies
ran together against a synthetic sender: 2 bursts printed 2 `PROXY burst` lines and
`dropped=10`, and the grader read `lost=10 broken_n=2 unrecovered=0`, wrote 4 IDR lines and ended
at `--duration 7`.

**s1_pair.py**

```python
# s1_pair.py <hu.txt> <phone.txt> <pcmarks> <idr.log> <from> <to> [win_s] :
# pairs each keyframe request on the head unit with the next whole IDR at the PC grader.
# Clocks: each marker's head unit time minus the PC time of its send gives one offset; the median is used.
import datetime, re, statistics, sys
hu_f, ph_f, pcm_f, idr_f, a, b = sys.argv[1:7]
win = float(sys.argv[7]) if len(sys.argv) > 7 else 3.0
TS = re.compile(r"^(\d\d)-(\d\d) (\d\d):(\d\d):(\d\d)\.(\d{3})")
def secs(line):
    m = TS.match(line)
    if not m: return None
    mo, d, h, mi, s, ms = map(int, m.groups())
    return (datetime.datetime(2026, mo, d, h, mi, s) - datetime.datetime(2026, 1, 1)).total_seconds() + ms / 1000.0
def load(path):
    rows = []
    for l in open(path, "r", errors="replace"):
        t = secs(l)
        if t is not None: rows.append((t, l.rstrip("\n")))
    return rows
def marks(rows):
    out = {}
    for t, l in rows:
        m = re.search(r"AutomationMarker: (\S+)\s*$", l)
        if m and m.group(1) not in out: out[m.group(1)] = t
    return out
hu, ph = load(hu_f), load(ph_f)
hm, pm = marks(hu), marks(ph)
if a not in hm or b not in hm: print("S1PAIR error=marker_missing from=%s to=%s" % (a, b)); sys.exit(1)
pc = {}
for l in open(pcm_f):
    p = l.split()
    if len(p) == 2: pc[p[0]] = float(p[1])
offs = [hm[k] - v for k, v in pc.items() if k in hm]
if len(offs) < 3: print("S1PAIR error=too_few_offsets n=%d" % len(offs)); sys.exit(1)
off = statistics.median(offs); spread = max(offs) - min(offs)
poffs = [hm[k] - pm[k] for k in hm if k in pm]
poff = statistics.median(poffs) if poffs else None
t0, t1 = hm[a], hm[b]
W = [(t, l) for t, l in hu if t0 <= t <= t1]
def times(s): return [t for t, l in W if s in l]
idr = []
for l in open(idr_f):
    p = l.split()
    if len(p) == 3 and p[2] == "1": idr.append(float(p[0]) + off)
asks = [t for t in times("SecondScreen: RTP push asking for a keyframe, none sent for ") if t <= t1 - win - 1.0]
cyc = times("cycling the auxiliary display's video focus for a keyframe (the second screen asked for one)")
stops = times("Auxiliary Video Sink Stopped -> Ignored (Forced Keyframe Request)")
starts = times("Media Start Request VIDEO_AUX: ")
reps = [(t, int(m.group(1))) for t, l in W for m in [re.search(r"RTP push sent \d+ packets, \d+ bytes, (\d+) keyframes in ", l)] if m]
PH = re.compile(r"(?i)video ?focus|VIDEO_AUX|keyframe|codec|display 1|display id 1")
def first(xs, lo, hi):
    for x in xs:
        if lo <= x <= hi: return x
    return None
ok = lat = 0; lats = []; hu_ok = 0
print("S1ASK  n  hu_time       cycled stop  start  idr_lat_s  hu_kf_next2  phone_lines")
for i, t in enumerate(asks):
    c = first(cyc, t, t + 1.0); s = first(stops, t, t + win); st = first(starts, t, t + win)
    d = first(idr, t - 0.5, t + win)
    nx = [(rt, k) for rt, k in reps if rt > t][:2]
    if len(nx) == 2 and nx[0][0] >= t + win: nx = nx[:1]
    hk = 1 if any(k >= 1 for _, k in nx) else 0
    pl = 0 if poff is None else sum(1 for pt, l in ph if t - 0.5 <= pt + poff <= t + win and PH.search(l))
    if d is not None: ok += 1; lats.append(d - t)
    hu_ok += hk
    print("S1ASK %2d %12.3f %6s %5s %6s %10s %12d %12d" % (i + 1, t - t0, "yes" if c else "no", "yes" if s else "no",
          "yes" if st else "no", "%.2f" % (d - t) if d is not None else "none", hk, pl))
n = len(asks)
gaps = [y - x for x, y in zip([v for v in idr if t0 <= v <= t1], [v for v in idr if t0 <= v <= t1][1:])]
print("S1PAIR asks=%d cycled=%d answered=%d answered_pct=%s lat_med_s=%s lat_max_s=%s hu_kf_next2=%d "
      "offset_n=%d offset_spread_s=%.2f phone_offset=%s idrs_in_window=%d idr_gap_max_s=%s stops=%d starts=%d"
      % (n, sum(1 for t in asks if first(cyc, t, t + 1.0)), ok, "%.0f" % (100.0 * ok / n) if n else "na",
         "%.2f" % statistics.median(lats) if lats else "na", "%.2f" % max(lats) if lats else "na", hu_ok,
         len(offs), spread, "ok" if poff is not None else "none", len([v for v in idr if t0 <= v <= t1]),
         "%.1f" % max(gaps) if gaps else "na", len(stops), len(starts)))
```

What its fields mean:

- One `S1ASK` row for each request. `cycled`: a `cycling ... (the second screen asked for one)` line
  within 1 s. `stop`: an `Auxiliary Video Sink Stopped -> Ignored (Forced Keyframe Request)` line
  within 3 s. `start`: a `Media Start Request VIDEO_AUX: ` line within 3 s. `idr_lat_s`: seconds from
  the request to the next whole IDR with SPS and PPS at the PC, or `none` if there is no such IDR
  within 3 s.
- `hu_kf_next2`: 1 when the head unit's next 10 s report (or the one after it, if the next one came
  within 3 s) counts a keyframe. It is the fallback when the clocks cannot be paired.
- `phone_lines`: phone lines within the same 3 s that match `video focus`, `VIDEO_AUX`, `keyframe`,
  `codec` or `display 1`. Report only. These are Gearhead strings and not verified.
- `answered_pct`: requests with an `idr_lat_s` over all requests. `offset_spread_s`: the largest
  minus the smallest marker offset.

On the brief's host, a synthetic capture with 17 requests, 13 of them answered 1.2 s later, read back
`asks=17 cycled=17 answered=13 answered_pct=76 lat_med_s=1.20 offset_spread_s=0.00 stops=17 starts=17`.

**s2_grade.py**

```python
# s2_grade.py <framemd5> <snap_glob_prefix> <events> : grades the late receiver's picture.
# <events> lines: "join <pc_epoch>", "reroute <pc_epoch>", "shot <pc_epoch> <png>" (main screen of the head unit).
import glob, hashlib, os, re, sys
fm, snap, ev = sys.argv[1:4]
tb = None; frames = []
for l in open(fm, errors="replace"):
    m = re.match(r"#tb 0: (\d+)/(\d+)", l)
    if m: tb = int(m.group(1)) / int(m.group(2)); continue
    if l.startswith("#") or not l.strip(): continue
    p = [x.strip() for x in l.split(",")]
    if len(p) >= 6 and p[0] == "0": frames.append((int(p[2]), p[5]))
snaps = sorted(glob.glob(snap + "*.png"))
join = None; reroutes = []; shots = []
for l in open(ev):
    p = l.split()
    if p[0] == "join": join = float(p[1])
    elif p[0] == "reroute": reroutes.append(float(p[1]))
    elif p[0] == "shot" and os.path.getsize(p[2]) > 0: shots.append((float(p[1]), hashlib.md5(open(p[2], "rb").read()).hexdigest()))
def md5(f): return hashlib.md5(open(f, "rb").read()).hexdigest()
if not frames or tb is None or not snaps:
    print("S2GRADE frames=%d distinct=0 first_frame_after_join_s=none decode_span_s=0 tb=%s snaps=%d" % (len(frames), tb, len(snaps)))
    sys.exit(0)
w0 = os.stat(snaps[0]).st_mtime          # the first selected frame is the first decoded frame
p0 = frames[0][0]
wall = [(w0 + (pts - p0) * tb, h) for pts, h in frames]
seen = set(); new_at = []
for t, h in wall:
    if h not in seen: seen.add(h); new_at.append(t)
span = wall[-1][0] - wall[0][0]
def new_after(r, win=25.0): return sum(1 for t in new_at if r < t <= r + win)
def shot_changed(r, win=25.0):
    before = [h for t, h in shots if t <= r]; after = [h for t, h in shots if r < t <= r + win]
    return "na" if not before or not after else ("yes" if any(h != before[-1] for h in after) else "no")
sm = [md5(f) for f in snaps]
print("S2GRADE frames=%d distinct=%d first_frame_after_join_s=%s decode_span_s=%.1f tb=%s snaps=%d snap_distinct=%d "
      "main_shots=%d main_distinct=%d %s"
      % (len(frames), len(seen), "%.1f" % (w0 - join) if join else "na", span, tb, len(snaps), len(set(sm)),
         len(shots), len(set(h for _, h in shots)),
         " ".join("r%d_new_frames=%d r%d_main_changed=%s" % (i + 1, new_after(r), i + 1, shot_changed(r)) for i, r in enumerate(reroutes))))
b = [0, 0, 0, 0, 0, 0]
for t in new_at:
    k = int((t - wall[0][0]) // 10)
    if 0 <= k < len(b): b[k] += 1
print("S2BUCKETS new_hashes_per_10s=%s" % ",".join(map(str, b)))
```

What its fields mean:

- `frames`: decoded frames in `framemd5`. `distinct`: different frame hashes among them.
- `first_frame_after_join_s`: the time of the first PNG (the first decoded frame) minus the time
  FFmpeg started.
- `rN_new_frames`: frames with a hash not seen before, in the 25 s after route change N.
- `rN_main_changed`: `yes` when a D-HU main-screen screenshot in those 25 s differs from the last one
  before the change. This proves the phone's map moved. `na` means a screenshot is missing.
- `snap_distinct`: different PNG snapshots of the second display, 10 s apart.

On the brief's host, a synthetic `framemd5` with 41 hashes over 50 s read back `distinct=41
decode_span_s=50.0 r1_new_frames=41 r1_main_changed=yes r2_new_frames=0 r2_main_changed=no`.

**s5.py**

```python
# s5.py <out_dir> : the main picture with the refresh on (S12, S3) against off (S4), from the FPS30 lines
import re, sys
v = {}
for r in ("S12", "S3", "S4"):
    try: l = open("%s/%s.fps30" % (sys.argv[1], r)).read()
    except OSError: l = ""
    m = re.search(r"windows=(\d+) rendered_ge1=(\d+) median_fps=(\d+) median_fed_fps=(\d+)", l)
    v[r] = tuple(int(x) for x in m.groups()) if m else None
    print("S5RUN %s %s" % (r, "windows=%d rendered_ge1=%d median_fps=%d median_fed_fps=%d" % v[r] if v[r] else "missing"))
if None in v.values(): print("S5 verdict_input=missing"); sys.exit(0)
f10 = (v["S12"][2] + v["S3"][2]) / 2.0; f0 = v["S4"][2]
g10 = (v["S12"][3] + v["S3"][3]) / 2.0; g0 = v["S4"][3]
print("S5 F10=%.1f F0=%d fed10=%.1f fed0=%d fps_ok=%s rendered_ok=%s" % (f10, f0, g10, g0,
      "yes" if f10 >= f0 - 3 else "no", "yes" if all(x[0] == 30 and x[1] >= 27 for x in v.values()) else "no"))
```

## 6. The deciding lines

`brief_check.py` checked every string a script greps against `app/src/main` at `8d4af793b`. Lines marked
`# not-literal` are built at run time from the fragments in the comment. All are INFO, WARN or ERROR, so
`log-level` 2 carries them. `...` marks a value the app fills in. Files: `$EV/<RUN>-hu.txt` (head unit) and
`$EV/<RUN>-phone.txt` (phone). Windows are marker pairs.

| Line (head unit) | File in the tree | Means |
|---|---|---|
| `AutomationReceiver: ` | `automation/AutomationReceiver.kt` | a verb landed. A step with no such line is void, not a FAIL |
| `WifiLauncher: Initializing WiFi Mode: NATIVE` | `connection/wifi/WifiLauncherManager.kt` | `pre`: Native path |
| `Projection backend: viewMode=TEXTURE ` | `aap/AapProjectionActivity.kt` | `pre`: view backend |
| `Media Sink Setup Request: 3 on channel VIDEO` at line end | `aap/AapControl.kt` | `pre`: the main sink, H.264 |
| `SSL handshake complete. ` | `aap/AapSslContext.kt` | a session formed. One per session at INFO |
| `AapService destroying` | `aap/AapService.kt` | the service went down. Must be 0 inside a run |
| `Throughput over ...ms: rendered=N (Xfps), fed=N (Yfps), dropped=N` | `decoder/video/VideoDecoder.kt` | the main decoder's frames (`fps30.py`) |
| `[ServiceDiscovery] Announcing an auxiliary display on VIDEO_AUX: RTP_PUSH 1280x720 as ...` | `ServiceDiscoveryResponse.kt` | the aux display was announced for the push |
| `SecondScreen: the RTP_PUSH output is not available, so one display is announced` | `SecondScreenHub.kt` | no valid address. Must be 0 |
| `AapTransport: the auxiliary display's video lane is open` | `aap/AapTransport.kt` | the phone opened channel 14 |
| `SecondScreen: RTP push to <ip>:5004 open (network=<name>, refresh=<ms>ms)` | `RtpStreamOutput.kt` | the socket opened. `<ms>` is 10000 in S12 and S3, 0 in S4 |
| `SecondScreen: the RTP_PUSH output is open` | `SecondScreenHub.kt` | the hub took the output |
| `SecondScreen: RTP push asking for a keyframe, none sent for <ms>ms` | `RtpStreamOutput.kt` | **the refresh fired** |
| `AapTransport: cycling the auxiliary display's video focus for a keyframe (the second screen asked for one)` | `aap/AapTransport.kt` | the aux focus cycle went out: a release on channel 14, then a gain 400 ms later |
| `AapTransport: no auxiliary keyframe cycle before its setup ()` | `aap/AapTransport.kt` | a request before the aux sink setup was refused |
| `Media Sink Stop Request: VIDEO_AUX` | `aap/AapControl.kt` | the phone stopped the aux sink |
| `Auxiliary Video Sink Stopped -> Ignored (Forced Keyframe Request)` | `aap/AapControl.kt` | that stop was the one our cycle asked for |
| `Auxiliary Video Sink Stopped -> the main session continues` | `aap/AapControl.kt` | an aux stop we did not ask for (report) |
| `Media Start Request VIDEO_AUX: session=..., config_index=...` | `aap/AapControl.kt` | the phone restarted the aux sink |
| `The phone released the second display's video. The main session continues.` | `aap/AapControl.kt` | the phone asked to release the aux display (report) |
| `SecondScreen: RTP push sent <n> packets, <bytes> bytes, <k> keyframes in <ms>ms, <j> keyframes carried their own SPS and PPS, <r> refreshes asked, <p> pacing pauses` | `RtpStreamOutput.kt` | the 10 s report |
| `SecondScreen: RTP push send failed (<Class>): ...` | `RtpStreamOutput.kt` | a send threw, logged once per class. Must be 0 |
| `SecondScreen: RTP push stopped` | `RtpStreamOutput.kt` | the socket closed at session end |
| `Media Sink Stop Request: VIDEO` at line end | `aap/AapControl.kt` | the phone stopped the **main** sink. Must be 0 after `<RUN>-up` |
| `Video Sink Stopped -> ` without `Auxiliary` | `aap/AapControl.kt` | a main sink stop. Must be 0 after `<RUN>-up` |
| `retaking video focus to complete the keyframe cycle` | `aap/AapTransport.kt`, `aap/AapProjectionActivity.kt` | a **main** focus cycle. Must be 0 after `<RUN>-up` |
| `Media Start Request VIDEO: ` | `aap/AapControl.kt` | the phone restarted the **main** sink. Report the count after `<RUN>-up` |
| `Error processing video message` | `aap/VideoLane.kt` | a lane threw. Must be 0 |
| ` messages behind, shedding` | `aap/VideoLane.kt` | the aux lane shed its backlog. Must be 0 |
| `Decoder stopped: CommManager: doDisconnect (second screen)` | `decoder/video/VideoDecoder.kt`, `connection/CommManager.kt` | an aux decoder stopped. Must be 0 |

**Phone strings** (`$EV/<RUN>-phone.txt`, by `phone`). They are Gearhead's, not ours, and they drift between
builds.

| Phone line | Graded | Means |
|---|---|---|
| `GH.` | capture gate, 1 or more | the phone capture works |
| `AutomationMarker: ` | window gate, 1 or more | the markers reached the phone log |
| `Critical error` | 0 in `<RUN>-up..<RUN>-end` | Android Auto ended the session over something we sent |
| `No input for display` | 0 | the aux display has no input |
| `Video focus gained before configurations received` | 0 | a focus gain before the aux Config |

The exit teardown makes the phone log `Critical error` lines after `<RUN>-end` (memory: about 1.1 s after each
Exit). Every phone window stops at `<RUN>-end`, which `run_end` marks before it sends the Exit, so those lines
never count. `ph_crit_file` is reported beside the windowed count.

**PC strings** (not in our tree): `GRADE `, `GRADER listening=`, `PROXY listening=`, `PROXY burst `,
`PROXY seen=`, `PATCH_OK `, `PATCH_FAIL `, `S1ASK `, `S1PAIR `, `S2GRADE `, `S2BUCKETS `, `S5RUN `,
`S5 `, `FPS30 `, `PROBE datagrams=`, `PROBE_SENT`.

## 7. Runs

`rig_session.py` runs them in the block's order, one at a time: R0, S12, S2, S4, S3, S5, cleanup. S4 runs before
S3 because S3's grade reads S4's outcome. A failed `pre` grades a run INCONCLUSIVE (setup): fix the keys and
re-run that run once.

### R0. Identity, rig state and the PC route (3 min)

**r0.sh**

```bash
adb -s "$HU" shell am force-stop "$PKG"; sleep 1
for s in "$D_SAM" "$D_HP"; do adb -s "$s" shell am force-stop "$PKG" >/dev/null 2>&1; sleep 0.3; done
rl_step "R0 D-HU state"
echo "PREFS_OWNER=$(adb -s "$HU" shell stat -c '%U:%G' /data/data/$PKG/shared_prefs | tr -d '\r')" >> "$VARS"; sleep 0.3
B=$OUT/settings-backup-HU.xml
[ -s "$B" ] || adb -s "$HU" shell cat /data/data/$PKG/shared_prefs/settings.xml > "$B"; sleep 0.3
md5sum "$B" > "$OUT/R0-backup.md5"
grep -o -E '(use-aac-audio|audio-latency-multiplier|audio-queue-capacity|enable-audio-sink|video-profile-starvation-cap|wifi-direct-last-identity-verdict)[^/]*' "$B" > "$OUT/R0-keys-found.txt"
grep -o -E 'aux-[a-z-]*|preferred-display-[a-z]*' "$B" | sort -u > "$OUT/R0-aux-keys.txt"
HUIP_P=$(hu_ip); HUIP=${HUIP_P%/*}
rl_step "R0 D-POCO state"
adb -s "$PH" shell dumpsys package com.google.android.projection.gearhead | grep -a -m2 -E 'versionName|versionCode' > "$OUT/R0-gearhead.txt"; sleep 0.3
echo "MAPS=$(adb -s "$PH" shell pm path com.google.android.apps.maps | grep -c 'package:')" >> "$VARS"; sleep 0.3   # system
POCO_MAC=$(adb -s "$PH" shell settings get secure bluetooth_address | tr -d '\r' | tr a-f A-F); sleep 0.3
adb -s "$PH" shell input keyevent KEYCODE_HOME; sleep 0.3
rl_step "R0 PC tools and route"
if command -v ffmpeg >/dev/null; then echo "FFMPEG=1" >> "$VARS"; ffmpeg -version 2>/dev/null | head -1 > "$OUT/R0-ffmpeg.txt"; else echo "FFMPEG=0" >> "$VARS"; fi
HOSTIP=""
[ -n "$HUIP" ] && HOSTIP=$(ip -4 route get "$HUIP" | grep -o 'src [0-9.]*' | cut -d' ' -f2)
[ -n "$HOSTIP" ] && { adb -s "$HU" shell ping -c 3 -W 2 "$HOSTIP" | tail -2 > "$OUT/R0-ping.txt"; sleep 0.3; }
printf 'POCO_MAC=%q\nHUIP=%q\nHOSTIP=%q\n' "$POCO_MAC" "$HUIP" "$HOSTIP" > "$OUT/env.sh"
printf 'v=0\no=- 0 0 IN IP4 %s\ns=Open Headunit second screen\nc=IN IP4 %s\nt=0 0\nm=video 5004 RTP/AVP 96\na=rtpmap:96 H264/90000\na=fmtp:96 packetization-mode=1' "$HOSTIP" "$HOSTIP" > "$OUT/stream.sdp"
if [ -n "$HOSTIP" ]; then
  rl_step "R0 UDP probe"
  adb -s "$HU" push "$SD/udp_probe.sh" /data/local/tmp/udp_probe.sh >/dev/null; sleep 0.3
  rl_bg probe python3 -I "$SD/udp_rx_probe.py" 5004 25 > "$OUT/R0-probe.txt" 2>&1; rl_sleep 2
  adb -s "$HU" shell sh /data/local/tmp/udp_probe.sh "$HOSTIP" 5004 > "$OUT/R0-probe-hu.txt"; sleep 0.3
  rl_wait_line "$OUT/R0-probe.txt" 'PROBE datagrams=' 30 >/dev/null   # not-literal: the PC probe prints it
  rl_stop probe
fi
rl_step "R0 harness"
rm -rf "$EV/harness"; mkdir -p "$EV/harness" "$TOOLS"
git -C "$APP" archive "$SHA_FULL" tools/second-screen/rtp-harness | tar -x -C "$EV/harness"
( cd "$TOOLS" && python3 -I "$SD/patch_tools.py" "$EV/harness/tools/second-screen/rtp-harness" ) > "$OUT/R0-patch.txt" 2>&1
rl_step "R0 install and identity"
adb -s "$HU" install -r -d "$APK_C" > "$OUT/R0-install.txt" 2>&1; sleep 0.3
"$RIG_TOOLKIT/apk_identity.sh" "$APK_C" "$HU" > "$OUT/R0-identity.txt" 2>&1; echo "IDENTITY_RC=$?" >> "$VARS"
F=$OUT/live.apk
adb -s "$HU" pull "$(adb -s "$HU" shell pm path "$PKG" | cut -d: -f2 | tr -d '\r')" "$F" >/dev/null 2>&1; sleep 0.3
echo "MD5_LIVE=$(md5sum < "$F" | cut -d' ' -f1)" >> "$VARS"
echo "MD5_WANT=$(md5sum < "$APK_C" | cut -d' ' -f1)" >> "$VARS"
echo "DEX_REFRESH=$(unzip -p "$F" 'classes*.dex' 2>/dev/null | strings | grep -c 'RtpRefreshPolicy')" >> "$VARS"
echo "DEX_PACING=$(unzip -p "$F" 'classes*.dex' 2>/dev/null | strings | grep -c 'RtpPacingPolicy')" >> "$VARS"
adb -s "$HU" shell am start -n "$MAIN" >/dev/null; rl_sleep 4
send ACTION_QUERY_STATE > "$OUT/R0-query.txt"
adb -s "$HU" shell am force-stop "$PKG"; sleep 0.3
```

**grade-r0.sh**

```bash
source "$VARS" 2>/dev/null
sec=r0
c eq "$(grep -acE '^PATCH_OK (lossy_proxy_rig.py md5=db455889b4eab3e50c6cad32adf94dc5|rtp_grade_rig.py md5=38e9d5b4796ee1b2cf432a5d4b2b34ba)$' "$OUT/R0-patch.txt")" 2 'two PATCH_OK lines with the md5s in section 5'   # not-literal
c eq "${IDENTITY_RC:-}" 0 'apk_identity.sh exit'
c eq "$( [ -n "${MD5_LIVE:-}" ] && [ "$MD5_LIVE" = "${MD5_WANT:-}" ] && echo 1 || echo 0 )" 1 'the live APK md5 equals apk/C.apk'
c ge "$(grep -acF "$SHA_C" "$OUT/R0-query.txt")" 1 'ACTION_QUERY_STATE data= carries 8d4af793b5e8'
c ge "${DEX_REFRESH:-}" 1 'the DEX holds RtpRefreshPolicy'
c ge "${DEX_PACING:-}" 1 'the DEX holds RtpPacingPolicy'
c eq "$(mac_ok "${POCO_MAC:-}")" 1 "POCO_MAC is a real address (${POCO_MAC:-empty})"
c eq "$( [ -n "${HUIP:-}" ] && echo 1 || echo 0 )" 1 "D-HU has a wlan0 address (${HUIP:-none})"
c ge "${MAPS:-}" 1 'Google Maps is installed on D-POCO'
c eq "${FFMPEG:-0}" 1 'ffmpeg is on the PC'
n=$(grep -ao 'PROBE datagrams=[0-9]*' "$OUT/R0-probe.txt" 2>/dev/null | cut -d= -f2)   # not-literal
c ge "$n" 1 'UDP probe: datagrams from D-HU reached the PC'
c eq "$( [ -n "${HUIP:-}" ] && grep -aq "from=.*$HUIP" "$OUT/R0-probe.txt" 2>/dev/null && echo 1 || echo 0 )" 1 'UDP probe: from= holds HUIP'
c eq "$(wc -l < "$OUT/stream.sdp")" 7 'stream.sdp has 8 lines (7 newlines)'
```

**PASS:** every condition above. **FAIL stops the round**, as before: a wrong patch, identity, md5, query
commit or DEX count, no station, no Maps, no ffmpeg, or a blocked probe. Both DEX classes are new in
`d73c45df1`, so no older build carries them. `hu_keys` writes and checks every key on every run, so an install
that wipes `settings.xml` does no harm. Record `PREFS_OWNER`, the backup md5, `R0-keys-found.txt`, the aux keys,
the Gearhead version and the ffmpeg version in Setup notes.

### S12. S1 and S2 in one session (8 min)

S1 and S2 share one session. S1 ends when the grader ends. S2 starts at once on the same live
stream, so the FFmpeg join is a real late join, about 185 s after the push opened. The screenshots are
sequential adb calls with `sleep 0.3` after each one. FFmpeg and the grader talk to D-HU over the WiFi,
not over adb.

**s12.sh**

```bash
rl_thermal_gate 75 || { echo "HOT=1" >> "$VARS"; exit 0; }
: > "$OUT/S12.pcmarks"; : > "$OUT/S2.events"
grader_on S1 0.0.0.0 "$RPORT" 180
bringup $K10; rc=$?
if [ "$rc" = 1 ]; then run_end; rl_stop "grader:"; keep_attempt S1; echo "RETRIED=1" >> "$VARS"
  : > "$OUT/S12.pcmarks"; grader_on S1 0.0.0.0 "$RPORT" 180; bringup $K10; rc=$?; fi
echo "RC=$rc" >> "$VARS"
[ "$rc" = 0 ] || { rl_stop "grader:"; run_end; exit 0; }
echo "HUIP_SESSION=$(hu_ip)" >> "$VARS"
# S1: the grader holds UDP 5004 for 180 s from the first datagram
ph_nav "Parque+Principal+Marinilla"; markt S1-nav
for n in 1 2 3 4 5; do rl_sleep 30; markt "S1-m$n"; done
rl_wait_line "$OUT/S1.rx" 'GRADE ' 120 >/dev/null || echo "GRADER_KILLED=1" >> "$VARS"
rl_stop "grader:S1"
markt S1-gend
echo "MAPS_NOTES_1=$(maps_notes)" >> "$VARS"
# S2: FFmpeg joins the live stream once a report shows packets
L=$(rl_offset "$CAP")
if rl_wait_line "$CAP" 'RTP push sent ' 12 "$L" >/dev/null && tail -c +$((L + 1)) "$CAP" | grep -aqE 'RTP push sent [1-9][0-9]* packets'; then
  echo "S2_TRAFFIC=yes" >> "$VARS"; else echo "S2_TRAFFIC=no" >> "$VARS"; fi
rm -f "$OUT/S2.framemd5" "$OUT"/S2-snap*.png "$OUT/S2.ffexit"; echo "join $(date +%s.%N)" >> "$OUT/S2.events"
rl_bg ff:S2 ff_s2; markt S2-join
hu_shot S2-main1
rl_sleep 3; ph_nav "Parque+Principal+Rionegro"; echo "reroute $(date +%s.%N)" >> "$OUT/S2.events"; markt S2-r1
rl_sleep 8; hu_shot S2-main2
rl_sleep 8; hu_shot S2-main3
rl_sleep 4; ph_nav "Parque+Principal+Marinilla"; echo "reroute $(date +%s.%N)" >> "$OUT/S2.events"; markt S2-r2
rl_sleep 10; hu_shot S2-main4
rl_sleep 10; hu_shot S2-main5
rl_wait_line "$OUT/S2.ffexit" 'rc=' 50 >/dev/null || echo "FF_KILLED=1" >> "$VARS"
rl_stop "ff:S2"
markt S2-end
echo "MAPS_NOTES_2=$(maps_notes)" >> "$VARS"
run_end
```

**grade-s12.sh**

```bash
source "$VARS" 2>/dev/null
out() { echo "S1_OUTCOME=$1" > "$OUT/S1.outcome"; rl_verdict "$2" "$1: $3"; exit 0; }
[ "${HOT:-0}" = 1 ] && out HOT UNTESTABLE "host thermal: two waits above 75C"
[ "${RC:-1}" = 1 ] && out RIG INCONCLUSIVE "no session with a picture after the retry"
[ "${RC:-1}" = 2 ] && out BRINGUP FAIL "the phone logged Critical error at bring-up and no frame came"
pre || out SETUP INCONCLUSIVE "pre native=$pre_native texture=$pre_texture h264=$pre_h264 hotspot=$pre_hotspot gh=$pre_gh; fix the keys and re-run S12 once"
python3 -I "$SD/s1_pair.py" "$CAP" "$PCAP" "$OUT/S12.pcmarks" "$OUT/S1.idr" S12-launch S1-gend 3.0 > "$OUT/S1.pair" 2>&1
push_counts S12 S12-launch S1-gend > "$OUT/S1.counts"
G=$(grep -a '^GRADE ' "$OUT/S1.rx" 2>/dev/null | tail -1); echo "$G" > "$OUT/S1.gradeline"
g() { echo "$G" | grep -ao "\b$1=[^ ]*" | head -1 | cut -d= -f2; }
P=$OUT/S1.pair; E=$OUT/S1.counts
sec=A1
c eq "$(pk e_announce "$E")" 1 'A1 e_announce'; c eq "$(pk e_unavail "$E")" 0 'A1 e_unavail'
c eq "$(pk e_open "$E")" 1 'A1 e_open'; c eq "$(pk e_outopen "$E")" 1 'A1 e_outopen'
c eq "$(pk e_refresh_ms "$E")" 10000 'A1 e_refresh_ms'; c eq "$(yes1 "$(pk e_network "$E")" wlan0)" 1 'A1 e_network is wlan0'
sec=A2
c ge "$(g packets)" 500 'A2 GRADE packets'; c ge "$(x100 "$(g span_s)")" 15000 'A2 GRADE span_s x100'
sec=A3
asks=$(num "$(pk asks "$P")" 0); cyc=$(num "$(pk cycled "$P")" 0)
c ge "$asks" 5 'A3 asks'; c ge "$cyc" $((asks - 1)) 'A3 cycled is asks or asks - 1'
c eq "$(pk e_kf_early "$E")" 0 'A3 e_kf_early'; c eq "$(pk e_fail_lines "$E")" 0 'A3 e_fail_lines'
sec=A4
c ge "$(pk answered_pct "$P")" 80 'A4 answered_pct'; c le "$(x100 "$(g idr_gap_max_s)")" 1500 'A4 GRADE idr_gap_max_s x100'
sec=A5
c ge "$(pk offset_n "$P")" 3 'A5 offset_n'; c le "$(x100 "$(pk offset_spread_s "$P")")" 150 'A5 offset_spread_s x100'
grep -a '^S1ASK ' "$P" | head -20 | sed 's/^/NOTE /'
echo "NOTE stops=$(pk stops "$P") starts=$(pk starts "$P") lat_med_s=$(pk lat_med_s "$P") GRADE: $G"
gapn=$(num "$(x100 "$(g idr_gap_max_s)")" 999999)
failed A1 && out SETUP INCONCLUSIVE "fix the keys and re-run S12 once"
[ -n "$G" ] || out RIG INCONCLUSIVE "GRADE missing"
failed A2 && out RIG INCONCLUSIVE "the stream did not reach the grader"
[ "$asks" -lt 5 ] && [ "$gapn" -le 1500 ] && out NOT_REACHED INCONCLUSIVE "the phone sent IDRs often enough by itself"
failed A3 && out WIRING FAIL "the refresh or the cycle did not run as designed"
if failed A5; then
  hk=$(num "$(pk hu_kf_next2 "$P")" 0)
  [ $((hk * 100)) -ge $((asks * 80)) ] && [ "$gapn" -le 1500 ] && out PASS PASS "graded on the head unit reports"
else
  failed A4 || out PASS PASS "A1 to A4 met"
fi
[ "$(pk stops "$P")" = 0 ] && out PHONE FAIL "the phone ignored the focus release"
out PHONE FAIL "Gearhead does not answer an aux focus cycle with an aux IDR"
```

#### S1. The phone answers the refresh: THE POINT OF THE ROUND

Inputs: `S1.pair` (`S1PAIR` and the `S1ASK` rows), `S1.rx` (`GRADE`), `S1.counts` (window
`S12-launch..S1-gend`), `pre`, and the phone capture.

Conditions:

- **A1, setup.** `pre` passes (`mode_native`, `view_texture` and `main_sink_h264` 1 or more, `hotspot_lines` 0,
  `phone_gh_lines` 1 or more). `S1.counts`: `e_announce` 1, `e_unavail` 0, `e_open` 1, `e_outopen` 1,
  `e_refresh_ms` 10000, `e_network` `wlan0`.
- **A2, the stream reached the grader.** `GRADE` `packets` 500 or more and `span_s` 150 or more.
- **A3, the refresh fired and the cycle went out.** `S1PAIR` `asks` 5 or more, and `cycled` is
  `asks` or `asks - 1`. `S1.counts`: `e_kf_early` 0, `e_fail_lines` 0.
- **A4, the phone answered.** `answered_pct` 80 or more, and `GRADE` `idr_gap_max_s` 15.0 or less.
- **A5, the clocks paired.** `offset_n` 3 or more and `offset_spread_s` 1.5 or less.

Report, no verdict of their own: every `S1ASK` row; `lat_med_s` and `lat_max_s`; `stops`, `starts`;
`S1.counts` `e_aux_stop_cycle`, `e_aux_stop_other`, `e_aux_start`, `e_aux_released`, `e_refreshes`,
`e_pauses`, `e_keyframes`, `e_kf_lines`; `GRADE` `idrs`, `lost`, `first_picture_s`; and the phone
lines that `S1ASK` counted for the first three requests, quoted from `S12-phone.txt`.

**Outcomes.** `grade-s12.sh` grades in this order and stops at the first that matches.

1. **Setup failure:** A1 is not met. INCONCLUSIVE: fix the keys and re-run S12 once.
2. **INCONCLUSIVE (rig):** no session with a picture after the retry, or A2 is not met, or `GRADE` is missing.
3. **INCONCLUSIVE (not reached):** `asks` is below 5 and `idr_gap_max_s` is 15.0 or less. The phone
   sent IDRs often enough by itself, so the refresh had nothing to do.
4. **FAIL-WIRING:** `asks` is below 5 with `idr_gap_max_s` above 15.0, or `cycled` is below
   `asks - 1`, or `e_kf_early` or `e_fail_lines` is 1 or more. The refresh or the cycle did not run as
   designed. Quote the `S1ASK` rows and the first 3 lines around a missing cycle.
5. **Clock fallback:** A5 is not met. Then grade A4 with `hu_kf_next2` in place of `answered`:
   `hu_kf_next2` 80% of `asks` or more, and `idr_gap_max_s` 15.0 or less. The verdict says "graded on the head
   unit reports".
6. **PASS:** A1 to A4 are met (A4 by the fallback when 5 applied).
7. **FAIL-PHONE:** A3 is met and A4 is not. Gearhead does not answer an aux focus cycle with an aux
   IDR. This is a real finding, not a rig fault. Quote `stops`, `starts`, the `S1ASK` rows and the
   `GRADE` line. If `stops` is 0, the verdict says "the phone ignored the focus release".

A bring-up where the phone logs `Critical error` and no frame comes is a FAIL of its own (`BRINGUP`).

**The outcome for INCONCLUSIVE:** the host may re-run S12 once (delete `S12.grade.json`, `S2.grade.json` and
rerun the session). If S1 is INCONCLUSIVE again, record both sets of numbers, grade S2 to S5 as written, and
carry on. Do not invent another lever. The coding side decides the next step from the numbers.

A PASS cannot come from a refresh that did nothing. Without an answer, the phone's IDRs come every
60 s (round 2, and S4 here), so `idr_gap_max_s` would be near 60 and `answered_pct` near 0.

#### S2. A late receiver gets a moving picture

S2 runs no device step: `s2.sh` only marks the step, and `grade-s2.sh` grades S12's files. Inputs:
`S2.grade` (`S2GRADE`, `S2BUCKETS`), `S2-ffmpeg.log` and `S2.ffexit`, `S12.vars` (`S2_TRAFFIC`, the
`MAPS_NOTES`), `S2.counts` (window `S2-join..S2-end`), and S1's outcome.

**s2.sh**

```bash
rl_step "S2 is graded from the files S12 left"
```

**grade-s2.sh**

```bash
source "$OUT/S12.vars" 2>/dev/null; source "$OUT/S1.outcome" 2>/dev/null
out() { rl_verdict "$1" "$2"; exit 0; }
[ "${HOT:-0}" = 1 ] && out UNTESTABLE "host thermal in S12"
python3 -I "$SD/s2_grade.py" "$OUT/S2.framemd5" "$OUT/S2-snap" "$OUT/S2.events" > "$OUT/S2.grade" 2>&1
push_counts S12 S2-join S2-end > "$OUT/S2.counts"
q() { grep -a '^S2GRADE ' "$OUT/S2.grade" | grep -ao "\b$1=[^ ]*" | head -1 | cut -d= -f2; }
sec=B1; c eq "$(yes1 "${S2_TRAFFIC:-}" yes)" 1 'B1 the join came after traffic'
sec=B2; c le "$(x100 "$(q first_frame_after_join_s)")" 2000 'B2 first_frame_after_join_s x100'
sec=B3
c ge "$(x100 "$(q decode_span_s)")" 3000 'B3 decode_span_s x100'; c ge "$(q distinct)" 30 'B3 distinct'
c ge "$(q snap_distinct)" 2 'B3 snap_distinct'; c ge "$(q r1_new_frames)" 1 'B3 r1_new_frames'; c ge "$(q r2_new_frames)" 1 'B3 r2_new_frames'
m1=$(q r1_main_changed); m2=$(q r2_main_changed)
sec=B4; c eq "$( [ "$m1" = yes ] || [ "$m2" = yes ] && echo 1 || echo 0 )" 1 'B4 the map moved (a main screenshot changed)'
echo "NOTE $(grep -a '^S2BUCKETS ' "$OUT/S2.grade") $(cat "$OUT/S2.ffexit" 2>/dev/null) maps_notes=${MAPS_NOTES_1:-}/${MAPS_NOTES_2:-}"
echo "NOTE S2.counts e_asks=$(pk e_asks "$OUT/S2.counts") e_keyframes=$(pk e_keyframes "$OUT/S2.counts") e_fail_lines=$(pk e_fail_lines "$OUT/S2.counts")"
case "${S1_OUTCOME:-RIG}" in RIG|SETUP|HOT) out INCONCLUSIVE "no stream: S1 was ${S1_OUTCOME:-not graded}" ;; esac
failed B1 && out INCONCLUSIVE "no stream: the join came before any traffic"
[ "${S1_OUTCOME:-}" = PHONE ] && out INCONCLUSIVE "report only: S1 is FAIL-PHONE, so S2 measures the phone's 60 s GOP"
{ [ "$(num "$(q frames)" 0)" = 0 ] || failed B2; } && out FAIL "FAIL-LATE: no picture within 20 s of the join"
{ { [ "$m1" = yes ] && [ "$(q r1_new_frames)" = 0 ]; } || { [ "$m2" = yes ] && [ "$(q r2_new_frames)" = 0 ]; }; } && \
  out FAIL "FAIL-FROZEN: the phone's map moved and the second display did not"
failed B1 B2 B3 B4 || out PASS "B1 to B4 met"
failed B3 && failed B4 && out INCONCLUSIVE "map idle: nothing proves the map moved"
```

Conditions:

- **B1, the join came after traffic.** `S2_TRAFFIC=yes`.
- **B2, a picture in 20 s.** `first_frame_after_join_s` 20.0 or less.
- **B3, the picture changes.** `decode_span_s` 30.0 or more, `distinct` 30 or more, `snap_distinct` 2
  or more, `r1_new_frames` 1 or more and `r2_new_frames` 1 or more.
- **B4, the map moved.** `r1_main_changed=yes` or `r2_main_changed=yes`.

Report: `frames`, `S2BUCKETS`, `snaps`, `main_distinct`, the `rc=` in `S2.ffexit` and the error lines of
`S2-ffmpeg.log`, the two `MAPS_NOTES` counts, and `S2.counts` `e_asks`, `e_keyframes` and `e_fail_lines`. Put
the first, middle and last PNG snapshot in the evidence zip.

**Outcomes.** In this order, stopping at the first that matches:

1. **INCONCLUSIVE (no stream):** B1 is not met, or S1 was INCONCLUSIVE (rig) or a setup failure.
2. **Report only (INCONCLUSIVE):** S1 is FAIL-PHONE. Without an answer, a late receiver waits for the phone's
   60 s GOP, so S2 measures only that.
3. **FAIL-LATE:** `frames` is 0, or B2 is not met. Quote FFmpeg's error lines.
4. **FAIL-FROZEN:** the route change whose `rN_main_changed` is `yes` has `rN_new_frames` 0. The phone's map
   moved and the second display did not. This is the reported fault.
5. **PASS:** B1 to B4 are met.
6. **INCONCLUSIVE (map idle):** B3 is not met and B4 is not met. Nothing proves the map moved.
   `rN_main_changed=na` counts as not met.
7. **FAIL:** anything else. The COND lines name each condition that is not met.

**The outcome for INCONCLUSIVE:** S2 is not re-run alone. If S12 is re-run for S1, S2 is graded again after it.

### S4. The control: S3 with the refresh off (5 min)

S4 and S3 share `loss_run` in `ss_lib.sh`: the push goes through the lossy proxy (bursts at 30, 60 and 90 s)
to the strict grader. S4 runs first, with the refresh off.

**s4.sh**

```bash
loss_run "$K0"
```

**grade-s4.sh**

```bash
source "$VARS" 2>/dev/null
out() { echo "S4_OUTCOME=$1" > "$OUT/S4.outcome"; rl_verdict "$2" "$1: $3"; exit 0; }
[ "${HOT:-0}" = 1 ] && out HOT UNTESTABLE "host thermal: two waits above 75C"
[ "${RC:-1}" = 1 ] && out RIG INCONCLUSIVE "no session with a picture after the retry"
[ "${RC:-1}" = 2 ] && out BRINGUP FAIL "the phone logged Critical error at bring-up and no frame came"
pre || out SETUP INCONCLUSIVE "pre native=$pre_native texture=$pre_texture h264=$pre_h264 hotspot=$pre_hotspot gh=$pre_gh; fix the keys and re-run once"
push_counts S4 S4-launch S4-end > "$OUT/S4.counts"; E=$OUT/S4.counts
G=$(grep -a '^GRADE ' "$OUT/S4.rx" 2>/dev/null | tail -1); echo "$G" > "$OUT/S4.gradeline"
g() { echo "$G" | grep -ao "\b$1=[^ ]*" | head -1 | cut -d= -f2; }
X=$(grep -a '^PROXY seen=' "$OUT/S4.proxy" 2>/dev/null | tail -1)   # not-literal
sec=D1
c eq "$(pk e_open "$E")" 1 'D1 e_open'; c eq "$(pk e_refresh_ms "$E")" 0 'D1 e_refresh_ms'
c eq "$(yes1 "$(pk e_network "$E")" wlan0)" 1 'D1 e_network is wlan0'; c eq "$(pk e_unavail "$E")" 0 'D1 e_unavail'
sec=D2
c eq "$(grep -ac '^PROXY burst ' "$OUT/S4.proxy" 2>/dev/null)" 3 'D2 PROXY burst lines'   # not-literal
c ge "$(echo "$X" | grep -ao 'dropped=[0-9]*' | cut -d= -f2)" 15 'D2 PROXY dropped'
c ge "$(g lost)" 15 'D2 GRADE lost'; c ge "$(g packets)" 500 'D2 GRADE packets'; c ge "$(x100 "$(g span_s)")" 14000 'D2 GRADE span_s x100'
sec=D3
c eq "$(pk e_asks "$E")" 0 'D3 e_asks'; c eq "$(pk e_cyc_push "$E")" 0 'D3 e_cyc_push'
sec=D4
c eq "$( [ "$(g unrecovered)" = 1 ] || [ "$(num "$(x100 "$(g broken_max_s)")" 0)" -ge 2500 ] && echo 1 || echo 0 )" 1 'D4 unrecovered=1 or broken_max_s >= 25.0'
echo "NOTE GRADE: $G | $X"
failed D1 && out SETUP INCONCLUSIVE "fix the keys and re-run once"
{ [ -z "$G" ] || failed D2; } && out RIG INCONCLUSIVE "the bursts or the stream did not happen as planned"
failed D3 && out FAIL FAIL "refresh 0 still asked for a keyframe"
failed D4 || out REPRODUCED PASS "the rig shows the reported fault with the refresh off"
out NOT_REPRODUCED INCONCLUSIVE "idr_gap_max_s=$(g idr_gap_max_s) broken_max_s=$(g broken_max_s); S3 becomes INCONCLUSIVE (control)"
```

Inputs: `S4.rx` (`GRADE`), `S4.proxy` (`PROXY` lines), `S4.counts` (window `S4-launch..S4-end`), `pre`.

- **D1, setup.** As C1, but `e_refresh_ms` 0.
- **D2, the bursts happened.** As C2.
- **D3, the setting turns the refresh off.** `S4.counts` `e_asks` 0 and `e_cyc_push` 0.
- **D4, the fault shows.** `GRADE` `unrecovered=1`, or `broken_max_s` 25.0 or more.

**Outcomes.** In this order:

1. **Setup failure (INCONCLUSIVE):** D1 is not met. Fix the keys and re-run once.
2. **INCONCLUSIVE (rig):** a bring-up failed twice, or D2 is not met. Re-run once.
3. **FAIL:** D3 is not met. Refresh 0 must mean no request.
4. **REPRODUCED (PASS):** D3 and D4 are met. The rig shows the reported fault with the refresh off.
5. **NOT REPRODUCED (INCONCLUSIVE):** D3 is met and D4 is not. Report `idr_gap_max_s` and `broken_max_s`. S3
   becomes INCONCLUSIVE (control).

Why D4 must happen when the code behaves as round 2 measured: the phone's IDRs come every 60 s, and
the bursts come 30 s apart. So at least one burst lands in the first half of a GOP, and the picture
stays broken for at least about 30 s.

### S3. Recovery from loss on the real network (5 min)

**s3.sh**

```bash
loss_run "$K10"
```

**grade-s3.sh**

```bash
source "$VARS" 2>/dev/null; source "$OUT/S4.outcome" 2>/dev/null; source "$OUT/S1.outcome" 2>/dev/null
out() { rl_verdict "$1" "$2"; exit 0; }
[ "${HOT:-0}" = 1 ] && out UNTESTABLE "host thermal: two waits above 75C"
[ "${RC:-1}" = 1 ] && out INCONCLUSIVE "rig: no session with a picture after the retry"
[ "${RC:-1}" = 2 ] && out FAIL "the phone logged Critical error at bring-up and no frame came"
pre || out INCONCLUSIVE "setup: pre native=$pre_native texture=$pre_texture h264=$pre_h264 hotspot=$pre_hotspot gh=$pre_gh"
push_counts S3 S3-launch S3-end > "$OUT/S3.counts"; E=$OUT/S3.counts
G=$(grep -a '^GRADE ' "$OUT/S3.rx" 2>/dev/null | tail -1); echo "$G" > "$OUT/S3.gradeline"
g() { echo "$G" | grep -ao "\b$1=[^ ]*" | head -1 | cut -d= -f2; }
X=$(grep -a '^PROXY seen=' "$OUT/S3.proxy" 2>/dev/null | tail -1)   # not-literal
sec=C1
c eq "$(pk e_open "$E")" 1 'C1 e_open'; c eq "$(pk e_refresh_ms "$E")" 10000 'C1 e_refresh_ms'
c eq "$(yes1 "$(pk e_network "$E")" wlan0)" 1 'C1 e_network is wlan0'; c eq "$(pk e_unavail "$E")" 0 'C1 e_unavail'
sec=C2
c eq "$(grep -ac '^PROXY burst ' "$OUT/S3.proxy" 2>/dev/null)" 3 'C2 PROXY burst lines'   # not-literal
c ge "$(echo "$X" | grep -ao 'dropped=[0-9]*' | cut -d= -f2)" 15 'C2 PROXY dropped'
c ge "$(g lost)" 15 'C2 GRADE lost'; c ge "$(g packets)" 500 'C2 GRADE packets'; c ge "$(x100 "$(g span_s)")" 14000 'C2 GRADE span_s x100'
sec=C3; c eq "$(g unrecovered)" 0 'C3 GRADE unrecovered'; c le "$(x100 "$(g broken_max_s)")" 1250 'C3 GRADE broken_max_s x100'
sec=C4; c ge "$(pk e_asks "$E")" 3 'C4 e_asks'; c eq "$(pk e_fail_lines "$E")" 0 'C4 e_fail_lines'
echo "NOTE GRADE: $G | $X | e_refreshes=$(pk e_refreshes "$E") e_pauses=$(pk e_pauses "$E")"
failed C1 && out INCONCLUSIVE "setup failure: fix the keys and re-run once"
{ [ -z "$G" ] || failed C2; } && out INCONCLUSIVE "rig: the bursts or the stream did not happen as planned; re-run once"
[ "${S4_OUTCOME:-NONE}" = REPRODUCED ] || out INCONCLUSIVE "control: S4 is ${S4_OUTCOME:-not graded}, so a recovery here proves nothing"
failed C1 C2 C3 C4 || out PASS "C1 to C4 met"
[ "${S1_OUTCOME:-}" = PHONE ] && out FAIL "follows S1"
```

Inputs: `S3.rx` (`GRADE`), `S3.proxy` (`PROXY` lines), `S3.counts` (window `S3-launch..S3-end`), `pre`.

- **C1, setup.** As A1, on `S3.counts` and `pre`: `e_open` 1, `e_refresh_ms` 10000, `e_network` `wlan0`,
  `e_unavail` 0.
- **C2, the bursts happened.** 3 `PROXY burst` lines, `PROXY` `dropped` 15 or more, `GRADE` `lost`
  15 or more, `packets` 500 or more, `span_s` 140 or more.
- **C3, recovery.** `GRADE` `unrecovered=0` and `broken_max_s` 12.5 or less.
- **C4, the refresh did it.** `S3.counts` `e_asks` 3 or more and `e_fail_lines` 0.

Report: `broken_n`, `broken_total_s`, `idr_gap_max_s`, `first_picture_s`, the burst times, and
`e_refreshes`, `e_pauses`.

**Outcomes.** In this order:

1. **Setup failure (INCONCLUSIVE):** C1 is not met. Fix the keys and re-run once.
2. **INCONCLUSIVE (rig):** the bring-up failed twice, or C2 is not met. Re-run once.
3. **INCONCLUSIVE (control):** S4 is not REPRODUCED. The rig did not show the fault, so a recovery here proves
   nothing.
4. **PASS:** C1 to C4 are met.
5. **FAIL:** anything else. If S1 is FAIL-PHONE, the verdict says "follows S1".

### S5. No cost to the session (graded after S3)

S5 runs no device step. `grade-s5.sh` reads the captures that S12, S3 and S4 left. "up" is
`<RUN>-up..<RUN>-end`, and "all" is `<RUN>-launch..<RUN>-end`.

**s5.sh**

```bash
rl_step "S5 is graded from the files S12, S3 and S4 left"
```

**grade-s5.sh**

```bash
for r in S12 S3 S4; do python3 -I "$SD/fps30.py" "$OUT/$r-hu.txt" "$r-up" > "$OUT/$r.fps30" 2>&1; done
python3 -I "$SD/s5.py" "$OUT" > "$OUT/S5.grade" 2>&1
S=$(grep -a '^S5 ' "$OUT/S5.grade")
sec=E1; c eq "$(yes1 "$(pk fps_ok "$OUT/S5.grade")" yes)" 1 'E1 fps_ok (F10 >= F0 - 3)'; c eq "$(yes1 "$(pk rendered_ok "$OUT/S5.grade")" yes)" 1 'E1 rendered_ok'
for r in S12 S3 S4; do
  push_counts "$r" "$r-launch" "$r-end" > "$OUT/$r-all.counts"; push_counts "$r" "$r-up" "$r-end" > "$OUT/$r-up.counts"
  A=$OUT/$r-all.counts; U=$OUT/$r-up.counts
  sec=E2; c eq "$(pk e_ssl "$A")" 1 "E2 $r e_ssl"; c eq "$(pk e_destroy "$A")" 0 "E2 $r e_destroy"
  c eq "$(pk e_errors "$U")" 0 "E2 $r e_errors"; c eq "$(pk e_shed "$U")" 0 "E2 $r e_shed"
  c eq "$(pk e_auxdec "$U")" 0 "E2 $r e_auxdec"; c eq "$(pk e_fail_lines "$U")" 0 "E2 $r e_fail_lines"
  if [ "$r" != S4 ]; then sec=E3
    c eq "$(pk e_main_stop "$U")" 0 "E3 $r e_main_stop"; c eq "$(pk e_main_vss "$U")" 0 "E3 $r e_main_vss"; c eq "$(pk e_main_retake "$U")" 0 "E3 $r e_main_retake"; fi
  PCAP=$OUT/$r-phone.txt; phone "$r-up" "$r-end"
  sec=E4; c eq "$ph_crit" 0 "E4 $r phone Critical error (file: $ph_crit_file)"; c eq "$ph_noinput" 0 "E4 $r phone No input for display"
  c ge "$ph_markers" 1 "E4 $r phone markers in the window"
  echo "NOTE $r e_main_start=$(pk e_main_start "$U") e_main_setup=$(pk e_main_setup "$U") focus_before_config=$ph_early $(grep -a '^FPS30 ' "$OUT/$r.fps30")"
done
echo "NOTE $S"
grep -aq 'verdict_input=missing' "$OUT/S5.grade" && { rl_verdict INCONCLUSIVE "a run gave no FPS30 line: $(grep -a 'missing' "$OUT/S5.grade" | tr '\n' ' ')"; exit 0; }   # not-literal
if failed E2 E3 E4; then rl_verdict FAIL "FAIL-B: the refresh breaks the session"
elif failed E1; then rl_verdict FAIL "FAIL-COST: $S"
fi
```

The `e_fail_lines` line of `push_counts` also carries `e_ssl`, `e_destroy`, `e_errors` and `e_shed`,
and the `e_stopped` line carries `e_auxdec`.

Conditions:

- **E1, the main picture.** `S5` `fps_ok=yes` (`F10`, the mean of the S12 and S3 medians, is not
  lower than `F0 - 3`, with `F0` the S4 median) and `rendered_ok=yes`.
- **E2, the session held.** In each run's `all` window: `e_ssl` 1 and `e_destroy` 0. In each run's
  `up` window: `e_errors` 0, `e_shed` 0, `e_auxdec` 0, `e_fail_lines` 0.
- **E3, no main focus cycle.** In the `up` windows of S12 and S3: `e_main_stop` 0, `e_main_vss` 0,
  `e_main_retake` 0.
- **E4, the phone.** In each run: `Critical error` 0 and `No input for display` 0 in
  `<RUN>-up..<RUN>-end`, and phone markers in the window 1 or more.

Report: `e_main_start` and `e_main_setup` in the `up` windows. 0 means the phone did not lay out its
main screen again on an aux cycle. Report each run's `fed` median beside its `rendered` median.

**Outcomes.** In this order:

1. **INCONCLUSIVE:** `S5 verdict_input=missing` (a run gave no `FPS30` line). Name the run.
2. **FAIL-B, the refresh breaks the session:** E2, E3 or E4 is not met. Quote the first offending
   line, and the phone's `Critical error` line if there is one.
3. **FAIL-COST:** E1 is not met. If `fed10` fell by as much as `F10`, the phone sent fewer frames.
4. **PASS:** E1 to E4 are met.

### End of round (cleanup, always last)

**cleanup.sh**

```bash
adb -s "$HU" shell rm -f /data/local/tmp/udp_probe.sh; sleep 0.3
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
link_up; adb -s "$PH" shell input keyevent KEYCODE_HOME; sleep 0.3   # D-POCO may still be navigating
"$RIG_TOOLKIT/rig_cleanup.sh" list > "$OUT/cleanup-list.txt" 2>&1
```

**grade-cleanup.sh**

```bash
source "$VARS" 2>/dev/null
c eq "${RESTORED:-0}" 1 "D-HU settings.xml equals the R0 backup"
```

`rl_stop` ended every grader, proxy and FFmpeg job inside its own run, so cleanup kills nothing by name. The
operator removes the PC firewall rule when the session prints the cleanup `OPERATOR` line; record the command
they used. `rig_requires.py baseline` puts every radio back afterwards, D-MOTO's included.

### Stop rule

- Each bring-up gets at most 2 attempts inside its run. A bring-up that returns 2 is not retried.
- R0 FAIL stops the round (stop line).
- A run graded UNTESTABLE (host thermal: the gate failed twice) stops the round; every run after it is skipped
  (stop lines on S12, S4 and S3).
- The host may re-run S12 once (S1 INCONCLUSIVE or setup failure), and S3 and S4 once each. So the round has at
  most 6 sessions.
- A setup INCONCLUSIVE is re-run once. A second one makes that run UNTESTABLE.
- No Gradle build runs after the build step.

## 8. Do not re-run

- Round 1's R1 (feature off), R3A (an address nobody answers) and R3C (no address): PASS.
- Round 2's D4 (closed port), D5 (a refused send logs once), D6 (sends resume) and C-FPS (the push
  costs the main picture nothing): PASS or waived. S5 repeats the fps comparison only for the
  refresh.
- The packet bytes (RTP header, FU-A layout, SPS and PPS in front of an IDR, the 90 kHz clock, the
  SDP text): rounds 1 and 2 and the JVM tests. The SDP text is unchanged at the candidate.
- The PC harness arms A0 to A4 (`plan-r2.md`): settled on the PC. The rig adds only what the stand-in
  phone could not show.
- The settings rows: the rig cannot type or pick in a dialog. A known gap.

## 9. Report back

1. **S1: does the phone answer?** `S1PAIR` `asks`, `cycled`, `answered_pct`, `lat_med_s`,
   `lat_max_s`, `offset_spread_s`, `stops`, `starts`, and `GRADE` `idr_gap_max_s`. Name the outcome.
2. **S2 and S3: what a receiver sees.** S2's `first_frame_after_join_s`, `distinct`,
   `r1_new_frames`, `r2_new_frames`, `r1_main_changed`, `r2_main_changed`. S3's and S4's `GRADE`
   `broken_max_s`, `unrecovered` and `idr_gap_max_s`.
3. **S5: the cost.** `F10`, `F0`, and `e_main_start` after `up` in S12 and S3.
