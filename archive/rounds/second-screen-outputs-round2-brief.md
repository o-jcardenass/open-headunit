# second-screen-outputs, round 2 brief

Published name on the transfer branch: `second-screen-outputs-round2-brief.md`. Results file:
`second-screen-outputs-round2-results.md`. Evidence release: `rig-evidence-second-screen-outputs`
(it exists from round 1), new asset `second-screen-outputs-round2-captures.zip`.

Units: **D-HU** (head unit), **D-POCO** (phone) and **D-SAM** (a second screen viewer, R2 only).
Estimated time: 20 min of pre-flight and build, then about 30 min of runs (R1 5 min, R2 8 min,
R2C 6 min, R3 with R4 10 min). If R2 is FAIL-B, the runs take about 15 min.

## 1. Build and baseline

- **Candidate:** `feat/second-screen-outputs` on `fork` at **`4897c1218417d6ea9ded71ea0857eff59b7599f4`**,
  tree `570307efb5c47f70448cb8532944fed0f58f5dda`. Five commits on `main` `ec9d9c33`.
- **History was rewritten again.** Round 1 tested `331b0a8c` on `main` `71375a68`. The branch now
  sits on `main` `ec9d9c33`, and commit 1 carries the fix below. Do not `git pull` an old checkout.
  Use a new worktree at the SHA, or hard-reset:

```bash
git ls-remote fork feat/second-screen-outputs   # MUST print 4897c1218417d6ea9ded71ea0857eff59b7599f4
git fetch fork feat/second-screen-outputs
git worktree add ../ohu-wt-ssr-4897c121 4897c1218417d6ea9ded71ea0857eff59b7599f4
git -C ../ohu-wt-ssr-4897c121 rev-parse 'HEAD^{tree}'   # MUST print 570307efb5c47f70448cb8532944fed0f58f5dda
# an existing checkout of the branch instead: git checkout feat/second-screen-outputs && git reset --hard 4897c1218417d6ea9ded71ea0857eff59b7599f4
```

  If `ls-remote` prints another SHA, stop and report. The round has no candidate.
- **No baseline build.** R1 is the feature-off control on the candidate (template section 8). Round 1's
  R2 is the negative control for the fix, and it is settled (section 8).

## 2. What this is and why

The branch adds a second video stream from Android Auto. With `aux-display-enabled` on, the head unit
announces a second video sink on channel 14 (`ID_VID2`, logged as `VIDEO_AUX`) for display id 1. The
sink feeds one of four outputs (`ANDROID_DISPLAY`, `NETWORK`, `MS912X`, `USB_DISPLAY`). The feature is
off by default, and then the service discovery bytes are the same as on `main`.

**Round 1 found the fault that this candidate fixes.** With the aux sink announced, Android Auto 17.9
on D-POCO ended every session at service discovery, 18 times in 18:
`CAR.SERVICE: Critical error 2 detail: 26 msg: No input for display 1`. No `Media Sink Setup Request`
came on any channel. The feature-off control (R1) passed.

The cause, read from the decompiled Android Auto 17.5 and 17.8 (`jqb`, `ikk`, `jcb`): for each announced
display, the phone counts the input services whose `display_id` equals that display's id. The phone
ends the session on 0 (`No input for display N`) or on more than 1 (`Multiple inputs found for display N`).
The input for display 0 does not count for display 1. No exception exists for AUXILIARY or CLUSTER.

The candidate changes three things, all in commit 1:

- **A.** Discovery announces one input service for the aux display on a new channel 15 (`ID_INP2`,
  logged as `INPUT_AUX`), with `display_id = 1`. It carries no touchscreen, no touchpad and no keys.
  The `Announcing an auxiliary display` line now ends `, input on INPUT_AUX`.
- **B.** The head unit answers a key binding request on channel 15 and logs
  `AapControlTouch: Input binding answered on INPUT_AUX`. It sends nothing else on 15.
- **C.** No aux focus cycle goes out before the aux sink has its Config. A focus gain before the Config
  ends the session on the phone (`Video focus gained before configurations received`). A refused cycle
  logs `AapTransport: no auxiliary keyframe cycle before its setup ()`. The parentheses are empty in
  this build, which is a known cosmetic fault and not a finding.

3217 JVM tests pass, 15 of them new. A test oracle copies the phone's display check and reproduces
round 1's failure without the fix. The phone in this rig runs 17.9, which nobody decompiled, so only
this round shows whether the session now forms.

This round answers four questions:

1. Does a normal session with the feature off still behave as on `main`? (R1)
2. With the aux display and its input announced, does the session form, does the phone open channel 14,
   and does an aux picture arrive with the main picture healthy? (R2, the point of the round)
3. Does the CLUSTER role do the same? (R2C, on every R2 outcome)
4. The two decision lines from revision 3, on an output that decodes on the head unit (R3, R4):
   - After a session end, `Decoder stopped: CommManager: doDisconnect (second screen)` appears, and the
     next session shows an aux picture with no app restart.
   - After a screen-off and wake, no `Decoder stopped:` line names the second screen, and the aux
     picture continues.

## 3. What is different about this round

- **Round 1's script faults are fixed in `ssr_lib.sh` (section 5).**
  - `th_pkg` matched `throttle_pkg=` too. It now reads only the `pkg=` field.
  - `preflight` counted `HotspotManager: Setting hotspot enabled=false`, which the app logs on every
    start. It now ignores lines that contain `hotspot enabled=false`.
  - `critical_error` counted the exit teardown. Android Auto logs `Critical error` lines after our own
    `ACTION_EXIT`, so the count is now windowed. `mark` writes every marker into the phone's log too
    (`log -t AutomationMarker`), so the same `seg` works on both captures.
  - `phone_grades` cuts every quoted line to 260 characters.
  - The toolkit is `rig-toolkit`, not `hur-wifi-test-scripts`.
- **A failed bring-up with a phone `Critical error` is not retried.** Round 1 spent 4 min on a second
  attempt that failed the same way. `bringup` now returns 2 when the phone logged `Critical error` and
  no frame arrived. The run is then graded at once. A retry for any other failure keeps the first
  attempt's captures as `<RUN>-a1-*`.
- **R2C now runs after R2 on every outcome.** The input rule covers both roles. R2C has no viewers.
- **New skip rule for R3 and R4.** Round 1's rule needed R2C, which a FAIL-B excluded. Section 7 gives
  the rule for every case.
- **The starvation cap.** Round 1 had 18 sessions with no picture, and three in a row can write
  `video-profile-starvation-cap`. Round 1's cleanup restored the backup, so the key is probably absent.
  P2 reads it and records it. `BASE` deletes it on every run anyway.
- **D-SAM had no WiFi network and no VLC in round 1.** P6 now asks the operator to join D-SAM to the
  house WiFi and to allow the VLC install. Without both, VIEWER-SAM is UNTESTABLE again, and that does
  not change R2.
- **Kept from round 1:**
  - The Android display arm uses a simulated display: `settings put global overlay_display_devices 800x480/160`.
  - The aux and main decoders print the same lines. `tput.py` splits them by thread id. The capture uses
    `logcat -v threadtime`, and the overlay is 800x480 apart from the main picture's 1920.
  - The aux lane opens on the phone's channel open on 14. The network output listens only after that.
  - Projection is proved at INFO by `Media Sink Setup Request: 3 on channel VIDEO` (line-end anchor,
    because `VIDEO` is a prefix of `VIDEO_AUX`) and `Throughput over ...ms: rendered=`.
  - The main map is idle. Compare R2's main numbers with R1's, not with a fixed rate.
  - One expected cache miss of `cachedSurfaceSettingsHash` on the first launch after the install.
- **The rig's audio settings are a deliberate worst case.** Do not change `use-aac-audio`,
  `audio-latency-multiplier`, `audio-queue-capacity` or `enable-audio-sink`. Read them back and record
  them. Round 1 read the multiplier as 8.
- **Expected INCONCLUSIVE, said now:**
  - R3 and R4 when section 7's skip rule applies.
  - R2C when the phone refuses the cluster's focus by its own policy (`Video focus rejected for display type`).
  - R4's decision, if the overlay display destroys the aux surface at screen-off.
- **What an empty aux input looks like is not known.** The phone accepts it, but nobody has seen the aux
  picture with no input source. The PNGs and screenshots in R2 show it. A map or a turn card needs no
  input.

## 4. Settings keys

All runs are on D-HU, written as root with the app stopped, by `hu_keys` (section 5). `hu_keys` reads
every key back and prints `KEYS_OK` or `KEYS_MISMATCH <keys>`. `POCO_MAC` comes from P3. `OVL` and
`ROLESPEC` come from R3's setup.

**Every run (`BASE`), unchanged from round 1:**

| Key | Type | Value | Why |
|---|---|---|---|
| `wifi-connection-mode` | int | `3` | Native AA wireless, the rig's only transport |
| `native-poke-bt-macs` | set | `POCO_MAC` | wake D-POCO only |
| `native-poke-all-paired` | boolean | `false` | no other bonded phone is poked |
| `native-driver-selection-mode` | int | `0` | no selector countdown |
| `onboarding-version` | int | `2` | no wizard on launch |
| `log-level` | int | `2` | INFO. Every deciding line in section 6 is INFO, WARN or ERROR |
| `video-codec` | string | `H.264` | main sink `3`, the same codec as the aux sink |
| `resolutionId` | int | `3` | 1080p main picture, apart from the aux sizes |
| `view-mode` | int | `1` | TEXTURE |
| `force-software-decoding` | boolean | `false` | both decoders on hardware |
| `software-video-decoder` | int | delete | |
| `debug-video-fault-injection` | int | delete | |
| `video-profile-starvation-cap` | boolean | delete | a latch that forces 720p30 and AAC |
| `night-mode` | int | `1` | DAY, no sensor message mid-window |

**Per run:**

| Key | Type | R1 | R2 | R2C | R3, R4 |
|---|---|---|---|---|---|
| `aux-display-enabled` | boolean | delete (default `false`) | `true` | `true` | `true` |
| `aux-output` | string | delete | `NETWORK` | `NETWORK` | `ANDROID_DISPLAY` |
| `aux-network-size` | int | delete | `1` (1280x720) | `1` | delete |
| `aux-network-port` | int | delete | delete (default 5000) | delete | delete |
| `aux-display-role` | string | delete | delete (`AUXILIARY`) | `CLUSTER` | `ROLESPEC` (section 7) |
| `aux-display-content` | int | delete | delete (65538, the map) | delete | delete |
| `aux-display-id` | int | delete | delete | delete | `OVL` |
| `preferred-display-mode` | int | delete | delete | delete | delete |
| `preferred-display-id` | int | delete | delete | delete | delete |

`ACTION_LOG_MARKER` is not in `AutomationCommandPolicy.CONFIGURING` on the candidate, so
`allow-external-configuration` is not needed.

```bash
BASE="int:wifi-connection-mode=3 set:native-poke-bt-macs=$POCO_MAC bool:native-poke-all-paired=false int:native-driver-selection-mode=0 int:onboarding-version=2 int:log-level=2 str:video-codec=H.264 int:resolutionId=3 int:view-mode=1 bool:force-software-decoding=false del:software-video-decoder del:debug-video-fault-injection del:video-profile-starvation-cap int:night-mode=1"
R1KEYS="del:aux-display-enabled del:aux-output del:aux-network-size del:aux-network-port del:aux-display-role del:aux-display-content del:aux-display-id del:preferred-display-mode del:preferred-display-id"
R2KEYS="bool:aux-display-enabled=true str:aux-output=NETWORK int:aux-network-size=1 del:aux-network-port del:aux-display-role del:aux-display-content del:aux-display-id del:preferred-display-mode del:preferred-display-id"
R2CKEYS="bool:aux-display-enabled=true str:aux-output=NETWORK int:aux-network-size=1 del:aux-network-port str:aux-display-role=CLUSTER del:aux-display-content del:aux-display-id del:preferred-display-mode del:preferred-display-id"
# R3KEYS is set in R3's setup, after OVL and ROLESPEC are known
```

## 5. Every action as a verb, and the helpers

| Step | Command |
|---|---|
| marker, both units | `mark <label>` = `send ACTION_LOG_MARKER --es text <label>`, then `log -t AutomationMarker <label>` on D-POCO |
| identity | `send ACTION_QUERY_STATE` |
| raise the projection, fallback only | `send ACTION_RAISE_PROJECTION` (inside `bringup`; it prints `RAISE_VERB_USED`) |
| end a session, keep the network | `send ACTION_END_SESSION_STAY_ARMED` |
| re-arm wireless, fallback only | `send ACTION_START_WIRELESS_SCAN` |
| exit between runs | `send ACTION_EXIT`, then `am force-stop` (inside `run_end`) |

**No verb exists for these, and none is needed on the app:**

- Launch: `adb shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity` (template section 3).
- The simulated display: `adb shell settings put global overlay_display_devices 800x480/160`.
- Sleep and wake: `adb shell input keyevent 223` (SLEEP), `224` (WAKEUP), `wm dismiss-keyguard`.
- The stream: `adb forward tcp:5000 tcp:5000` and `aux_recv.py` on the host.
- The viewers: VLC on the host (`pc_vlc`), `relay.py` on the host, and VLC for Android on D-SAM
  (`sam_view`). These act on VLC, not on our app.

**There is no tap and no hand step during the runs.** The only human step is the P6 ask.

### Scripts

Make the folder `rig-toolkit/rig-data/rounds/second-screen-outputs-round2/scripts/` (or the same place
round 1 used on this host) and `cd` into it for the whole round. List its files in Setup notes.
`python3 -I` runs every Python file.

**Seven files are unchanged from round 1.** Copy them from round 1's scripts folder:

```bash
R1S=../../second-screen-outputs-round1/scripts      # adjust if round 1's folder is elsewhere
cp $R1S/ssr_keys.py $R1S/tput.py $R1S/aux_recv.py $R1S/nal_count.py $R1S/relay.py $R1S/pngdims.py $R1S/viewers.sh .
cp $R1S/ssr_build.sh . 2>/dev/null                    # round 1's build wrapper, if present
md5sum ssr_keys.py tput.py aux_recv.py nal_count.py relay.py pngdims.py viewers.sh   # record them
```

If round 1's folder is missing, take each file's text from section 5 of
`second-screen-outputs-round1-brief.md` on this branch. Record which source you used.

Fix one line in the copied `ssr_keys.py` if it is still there: the `set:` write must read
`for item in (val.split(",") if val else []):` (`rig-quirks/topics/tooling.md`). Round 1's copy
already has this form.

**`ssr_lib.sh` is new for this round.** Write it as below and source it (`source ./ssr_lib.sh`):

```bash
# ssr_lib.sh : round 2. Source it inside the round 2 scripts folder.
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
MAIN=$PKG/com.andrerinas.openheadunit.main.MainActivity
HU=27870808938846; PH=4f4027e9
D=$(pwd); OUT=$D/out; mkdir -p "$OUT"
send() { local a=$1; shift; adb -s $HU shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; sleep 0.3; }
# mark writes the same label into both captures, so seg works on the phone file too
mark() { send ACTION_LOG_MARKER --es text "$1" >/dev/null; adb -s $PH shell log -t AutomationMarker "$1"; sleep 0.3; }
nl() { wc -l < "$CAP"; }
waitfor() { local end=$((SECONDS+$1)); while [ $SECONDS -lt $end ]; do tail -n +"$3" "$CAP" | grep -aqE -- "$2" && return 0; sleep 1; done; return 1; }
seg() { sed -n "/AutomationMarker: $2\$/,/AutomationMarker: $3\$/p" "$1"; }
cnt()  { seg "$1" "$2" "$3" | grep -acF -- "$4"; }
cntE() { seg "$1" "$2" "$3" | grep -acE -- "$4"; }
# Host thermal. Fixed: read only the pkg= field, never throttle_pkg=.
th_pkg() { if [ -x ../rig_thermal.sh ]; then ../rig_thermal.sh status | grep -oE '(^|[^_a-z])pkg=[0-9]+' | head -1 | grep -oE '[0-9]+$'; return; fi
  for z in /sys/class/hwmon/hwmon*; do [ "$(cat $z/name 2>/dev/null)" = coretemp ] && { echo $(( $(cat $z/temp1_input) / 1000 )); return; }; done
  echo $(( $(cat /sys/class/thermal/thermal_zone0/temp) / 1000 )); }
th_thr() { cat /sys/devices/system/cpu/cpu0/thermal_throttle/package_throttle_count 2>/dev/null || echo 0; }
th_wait() { local lim=${1:-75} n=0; while [ "$(th_pkg)" -ge "$lim" ]; do n=$((n+1)); [ $n -ge 90 ] && return 1; sleep 10; done; }
th_watch() { while :; do echo "$(date +%T) pkg=$(th_pkg)C throttle_pkg=$(th_thr)"; sleep 20; done >> "$1"; }
th_gate() { th_wait 75 || { echo "$(date +%T) HOST_TOO_HOT ${RUN:-run}" | tee -a "$OUT/thermal.log"; return 3; }
  [ -n "$THPID" ] && kill $THPID 2>/dev/null; th_watch "$OUT/${RUN:-run}.thermal" & THPID=$!
  echo "$(date +%T) ${RUN:-run} start pkg=$(th_pkg)C throttle_pkg=$(th_thr)" >> "$OUT/thermal.log"; }
th_report() { awk -F'[ =C]+' '{p=$3; t=$5; if(p>m)m=p; if(NR==1)t0=t} END{print "thermal_max=" m "C throttle_delta=" t-t0}' "$OUT/$1.thermal"; }
ph_off() { adb -s $PH shell svc wifi disable; sleep 0.3; adb -s $PH shell svc bluetooth disable; sleep 5; }
ph_on() { adb -s $PH shell svc bluetooth enable; sleep 0.3; adb -s $PH shell svc wifi enable; sleep 8
  { adb -s $PH shell dumpsys wifi | grep -a -m1 "Wi-Fi is"; sleep 0.3
    adb -s $PH shell dumpsys bluetooth_manager | grep -a -m1 -iE "^ *state:"; } | tee -a "$OUT/$RUN.radios"; }
cap_start() { CAP=$OUT/$RUN-hu.txt; PCAP=$OUT/$RUN-phone.txt
  adb -s $HU logcat -G 16M; sleep 0.3; adb -s $HU logcat -c
  stdbuf -oL adb -s $HU logcat -v threadtime > "$CAP" & CAPPID=$!
  stdbuf -oL adb -s $PH logcat -v threadtime -T 1 -b main,system,crash > "$PCAP" & PCAPPID=$!; sleep 2; }
cap_stop() { kill $CAPPID $PCAPPID 2>/dev/null; wait $CAPPID $PCAPPID 2>/dev/null; ps aux | grep -c "[l]ogcat"; }
hu_keys() { adb -s $HU shell cat /data/data/$PKG/shared_prefs/settings.xml > "$OUT/cur.xml"
  python3 -I "$D/ssr_keys.py" "$OUT/cur.xml" "$OUT/new.xml" $BASE "$@" || return 1
  adb -s $HU push "$OUT/new.xml" /data/local/tmp/ssr-settings.xml >/dev/null
  local own; own=$(adb -s $HU shell stat -c %u:%g /data/data/$PKG | tr -d '\r')
  adb -s $HU shell cp /data/local/tmp/ssr-settings.xml /data/data/$PKG/shared_prefs/settings.xml
  adb -s $HU shell chown $own /data/data/$PKG/shared_prefs/settings.xml
  adb -s $HU shell chmod 660 /data/data/$PKG/shared_prefs/settings.xml
  adb -s $HU shell cat /data/data/$PKG/shared_prefs/settings.xml > "$OUT/$RUN.prefs.xml"
  python3 -I "$D/ssr_keys.py" --check "$OUT/$RUN.prefs.xml" $BASE "$@"; }
# bringup <RUN> <spec...> : 0 = a session with a picture; 1 = no session (retry once);
#   2 = the phone logged Critical error and no frame came (do not retry); 3 = host too hot
bringup() { RUN=$1; shift
  th_gate || return 3
  adb -s $HU shell am force-stop $PKG; sleep 1
  ph_off; adb -s $PH shell input keyevent KEYCODE_HOME
  hu_keys "$@" || { echo "KEYS_FAIL $RUN"; return 1; }
  cap_start
  adb -s $HU shell am start -n $MAIN >/dev/null; sleep 3; mark $RUN-launch; sleep 15
  local L; L=$(nl); ph_on; mark $RUN-phone-on
  waitfor 150 'SSL handshake complete' $L || { echo "SESSION_FAIL_SSL $RUN"; return 1; }
  waitfor 40 'Throughput over [0-9]+ms: rendered=[1-9]' $L && { mark $RUN-up; return 0; }
  grep -aqF 'Critical error' "$PCAP" && { echo "SESSION_FAIL_PHONE_CRITICAL $RUN"; return 2; }
  send ACTION_RAISE_PROJECTION >/dev/null; echo "RAISE_VERB_USED $RUN"
  waitfor 30 'Throughput over [0-9]+ms: rendered=[1-9]' $L && { mark $RUN-up; return 0; }
  grep -aqF 'Critical error' "$PCAP" && { echo "SESSION_FAIL_PHONE_CRITICAL $RUN"; return 2; }
  echo "SESSION_FAIL_NO_FRAMES $RUN"; return 1; }
# keep_attempt <RUN> : keeps a failed first attempt's files before the retry
keep_attempt() { local f; for f in hu.txt phone.txt; do [ -f "$OUT/$1-$f" ] && mv "$OUT/$1-$f" "$OUT/$1-a1-$f"; done
  [ -f "$OUT/$1.thermal" ] && mv "$OUT/$1.thermal" "$OUT/$1-a1.thermal"; }
# preflight : proves this capture ran the path under test. Fixed: the app's own hotspot-off line is ignored.
preflight() { local f=$OUT/$RUN.preflight ok=1 c; : > "$f"
  c=$(grep -acF "WifiLauncher: Initializing WiFi Mode: NATIVE" "$CAP"); echo "mode_native=$c" >> "$f"; [ "$c" -ge 1 ] || ok=0
  c=$(grep -acF "Projection backend: viewMode=TEXTURE " "$CAP"); echo "view_texture=$c" >> "$f"; [ "$c" -ge 1 ] || ok=0
  c=$(grep -acE "Media Sink Setup Request: 3 on channel VIDEO[[:space:]]*$" "$CAP"); echo "main_sink_h264=$c" >> "$f"; [ "$c" -ge 1 ] || ok=0
  c=$(grep -aE "HotspotManager:|SoftApCredentials:" "$CAP" | grep -avc "hotspot enabled=false"); echo "hotspot_lines=$c" >> "$f"; [ "$c" -eq 0 ] || ok=0
  c=$(grep -ac "GH\." "$PCAP"); echo "phone_gh_lines=$c" >> "$f"; [ "$c" -ge 1 ] || ok=0
  cat "$f"; if [ $ok = 1 ]; then mark $RUN-preflight-ok; return 0; fi; mark $RUN-preflight-FAIL; echo "PREFLIGHT_FAIL $RUN"; return 2; }
run_end() { mark $RUN-end; send ACTION_EXIT >/dev/null; sleep 4; mark $RUN-closed; sleep 1
  adb -s $HU shell am force-stop $PKG; cap_stop; th_report $RUN | tee -a "$OUT/$RUN.preflight"; }
# phone_grades <RUN> <from> <to> [<from2> <to2>] : phone conditions inside the marker windows only
phone_grades() { local P=$OUT/$1-phone.txt W=$OUT/$1-phone.win s; shift; : > "$W"
  while [ $# -ge 2 ]; do seg "$P" "$1" "$2" >> "$W"; shift 2; done
  echo "phone_markers_in_window=$(grep -acF 'AutomationMarker: ' "$W") gh_lines=$(grep -ac 'GH\.' "$P") critical_error_file=$(grep -ac 'Critical error' "$P")"
  for s in "Critical error" "No input for display" "Multiple inputs found for display" "Video focus gained before configurations received" "Car not sending ACK, channel:"; do
    echo "win|$s=$(grep -acF -- "$s" "$W")"; done
  grep -aF 'Critical error' "$W" | head -3 | cut -c1-260
  for s in "Discovered input for display" "Setting theme for display" "blocked by power saving" "Video focus rejected for display type" "Checking video config at index"; do
    echo "quote|$s=$(grep -acF -- "$s" "$W")"; grep -aF -- "$s" "$W" | head -3 | cut -c1-260; done; }
# aux_counts <RUN> : head unit counts inside <RUN>-launch .. <RUN>-end
aux_counts() { local W=$OUT/$1-hu.win A='[ServiceDiscovery] Announcing an auxiliary display on VIDEO_AUX: '
  seg "$OUT/$1-hu.txt" "$1-launch" "$1-end" > "$W"
  echo "window_lines=$(wc -l < "$W")"
  echo "b1=$(grep -acF "$A" "$W") b1_input=$(grep -aF "$A" "$W" | grep -acE 'input on INPUT_AUX[[:space:]]*$')"
  grep -aF "$A" "$W" | head -1 | cut -c1-300
  echo "b2=$(grep -acF 'output is not available, so one display is announced' "$W")"
  echo "b3=$(grep -acF "AapTransport: the auxiliary display's video lane is open" "$W")"
  echo "b4=$(grep -acE 'Media Sink Setup Request: [0-9]+ on channel VIDEO_AUX' "$W")"; grep -aoE 'Media Sink Setup Request: [0-9]+ on channel VIDEO_AUX' "$W" | sort | uniq -c
  echo "b4m=$(grep -acE 'Media Sink Setup Request: [0-9]+ on channel VIDEO[[:space:]]*$' "$W")"
  echo "b5=$(grep -acF 'Granting video focus on VIDEO_AUX after its setup' "$W")"
  echo "b6=$(grep -acF 'Media Start Request VIDEO_AUX' "$W")"
  echo "b7=$(grep -acF 'SecondScreen: network stream listening on port 5000' "$W")"
  echo "b8=$(grep -acF 'SecondScreen: network client connected from ' "$W") b8_max=$(grep -aoE '\([0-9]+ connected\)' "$W" | grep -oE '[0-9]+' | sort -n | tail -1)"
  echo "b9 fell_behind=$(grep -acF 'fell behind; resuming at the next keyframe' "$W") cycles=$(grep -acF "cycling the auxiliary display's video focus" "$W") shed=$(grep -acF 'AapTransport: the VIDEO_AUX thread is ' "$W") errors=$(grep -acF 'Error processing video message' "$W")"
  echo "b10=$(grep -acF 'SSL handshake complete' "$W")"
  echo "b11=$(grep -acF 'Decoder stopped: CommManager: doDisconnect (second screen)' "$W")"
  echo "b12 binding=$(grep -acF 'AapControlTouch: Input binding answered on INPUT_AUX' "$W") early_cycle=$(grep -acF 'AapTransport: no auxiliary keyframe cycle before its setup' "$W") unsupported_input=$(grep -acF 'Unsupported Input message type' "$W")"
  grep -aE 'RX: Video Focus Request.*channel: VIDEO_AUX|The phone released the second display|Auxiliary Video Sink Stopped|Unsupported Input message type' "$W" | head -5 | cut -c1-260; }
```

## 6. The deciding lines

Every head unit string below was checked with `grep -F -r` in the candidate's `app/src/main` at
`4897c121`. All are INFO, WARN or ERROR, so `log-level` 2 carries them. `...` marks a value the app
fills in. `VIDEO_AUX` is `Channel.name(14)` and `INPUT_AUX` is `Channel.name(15)`. Files:
`$OUT/<RUN>-hu.txt` (head unit) and `$OUT/<RUN>-phone.txt` (phone). Windows are marker pairs.

| Line (head unit) | Means |
|---|---|
| `AutomationReceiver: ` | a verb landed. A step with no such line is void, not a FAIL |
| `WifiLauncher: Initializing WiFi Mode: NATIVE` | preflight: Native path |
| `Projection backend: viewMode=TEXTURE ` | preflight: view backend |
| `Media Sink Setup Request: 3 on channel VIDEO` at line end | the main sink, H.264 |
| `HotspotManager:`, `SoftApCredentials:`, less `hotspot enabled=false` | preflight: must be 0 |
| `SSL handshake complete` | session formed |
| `Throughput over ...ms: rendered=N` | frames; `tput.py` splits main from aux |
| `Output Format Changed: ` with `width=800` | names the aux decoder's output thread (R3, R4) |
| `[ServiceDiscovery] Announcing an auxiliary display on VIDEO_AUX: <output> WxH as _WxH, margins ..., density ..., role=..., content ..., input on INPUT_AUX` | **new ending**: the aux sink and its input were announced |
| `SecondScreen: the <OUTPUT> output is not available, so one display is announced` | setup failure |
| `AapTransport: the auxiliary display's video lane is open` | the phone opened channel 14 |
| `Media Sink Setup Request: N on channel VIDEO_AUX` | the phone set up the aux sink; N is its codec |
| `Granting video focus on VIDEO_AUX after its setup` | we gave the aux sink focus |
| `Media Start Request VIDEO_AUX: session=...` | the phone started the aux stream |
| `AapControlTouch: Input binding answered on INPUT_AUX` | **new**: the phone asked for key bindings on 15. Quote only |
| `AapTransport: no auxiliary keyframe cycle before its setup ()` | **new**: part C refused an early cycle. Quote only |
| `Unsupported Input message type: ` | a message on an input channel that we do not handle. Quote only |
| `RX: Video Focus Request - mode: ..., reason: ..., channel: VIDEO_AUX` | the phone asked for aux focus |
| `The phone released the second display's video. The main session continues.` | the phone released aux focus |
| `Auxiliary Video Sink Stopped -> ` | the phone stopped the aux sink |
| `SecondScreen: the NETWORK output is open` | R2: the output started |
| `SecondScreen: network stream listening on port 5000` | R2: the server is up |
| `SecondScreen: network client connected from ` | R2: a receiver connected; ends `(N connected)` |
| `fell behind; resuming at the next keyframe` | a receiver's queue overflowed |
| `AapTransport: cycling the auxiliary display's video focus for a keyframe (` | an aux keyframe request |
| `AapTransport: the VIDEO_AUX thread is ...messages behind, shedding` | the aux lane shed its backlog |
| `Error processing video message` | a lane threw |
| `AapProjectionActivity: the auxiliary display is up on ` | R3: the Presentation is showing |
| `AuxDisplayPresentation: surface ready on display ` | R3, R4: the aux surface was created (again) |
| `cannot carry a second screen`, `could not open the auxiliary display: ` | R3: setup failure |
| `DisplayTargets: projecting on display ` | the display list, with `N:Overlay #1 800x480@160` in R3 |
| `Decoder stopped: CommManager: doDisconnect (second screen)` | the aux decoder stopped at session end |
| `Decoder stopped: the auxiliary display went away`, `Decoder stopped: the auxiliary display was dismissed` | the aux decoder stopped for its surface |
| `Decoder stopped: screen_off_sleep` | the main decoder stopped at sleep (report only) |
| `AapService: ACTION_END_SESSION_STAY_ARMED received` | R3: the session end landed |
| `WakeDetect: SCREEN_OFF`, `WakeDetect: SCREEN_ON (screen was off for ` | R4: the sleep and wake landed |

**Phone strings** (`$OUT/<RUN>-phone.txt`, inside the run's marker window). They are Android Auto's,
checked present in the decompiled 17.8 (`jqb`, `jez`, `jdx`, `ikk`, `ivn`, `ivq`). The phone runs 17.9,
and strings drift between builds, so report the Gearhead version.

| Phone line | Graded | Means |
|---|---|---|
| `GH.` | capture gate, 1 or more | the phone capture works |
| `Critical error` | 0 in the window | Android Auto ended the session over something we sent |
| `No input for display` | 0 | round 1's failure |
| `Multiple inputs found for display` | 0 | the aux input landed on the wrong display |
| `Video focus gained before configurations received` | 0 | a focus gain before the Config (part C) |
| `Car not sending ACK, channel:` | 0 | an ACK timeout |
| `Discovered input for display` | quote only | the phone found an input for a display |
| `Setting theme for display` | quote only | |
| `blocked by power saving` | quote only | the phone dropped the display by its own setting |
| `Video focus rejected for display type` | quote only | the phone refused a cluster's focus by its own policy |
| `Checking video config at index` | quote only | |
| `AutomationMarker: ` | window gate | the markers reached the phone log |

The exit teardown makes the phone log `Critical error` lines after `<RUN>-end`. The window stops at
`<RUN>-end`, so they do not count. Report `critical_error_file` beside the windowed count.

Viewer evidence is not in our tree: `RELAY open <ip>`, `RELAY close <ip> down_bytes=N seconds=S`,
`PNG count=N ... width=W height=H first_after_s=S`, the D-SAM screenshots and VLC's logs.

## 7. Runs

### Pre-flight (one batch, before R0)

**P1. Units.** As round 1. `adb devices -l` must list `27870808938846` (D-HU) and `4f4027e9` (D-POCO).

```bash
for s in ZY22GC3BM4; do adb devices | grep -q "^$s" && { adb -s $s shell svc bluetooth disable; sleep 0.3; adb -s $s shell svc wifi disable; }; done
for s in 30041c35642d2200 0123456789ABCDEF CNU350BGBJ; do adb devices | grep -q "^$s" && adb -s $s shell am force-stop com.andrerinas.headunitrevived; done
```

Write in Setup notes which units were on adb.

**P2. D-HU state.**

```bash
source ./ssr_lib.sh
adb -s $HU shell stat -c '%U:%G %a' /data/data/$PKG/shared_prefs        # record it
adb -s $HU shell cat /data/data/$PKG/shared_prefs/settings.xml > settings-backup-HU.xml
grep -o -E '(use-aac-audio|audio-latency-multiplier|audio-queue-capacity|enable-audio-sink)[^/]*' settings-backup-HU.xml
grep -o -E 'video-profile-starvation-cap[^/]*' settings-backup-HU.xml   # record it; BASE deletes it
grep -o -E 'aux-[a-z-]*|preferred-display-[a-z]*' settings-backup-HU.xml   # expect nothing; record what is there
adb -s $HU shell settings get global overlay_display_devices               # expect null; record it
HU_MAC=$(adb -s $HU shell settings get secure bluetooth_address | tr -d '\r'); echo "HU_MAC=$HU_MAC"
```

**P3. D-POCO state.** Round 1's `dumpsys` grep for the address came back empty, so this round reads the
setting first.

```bash
adb -s $PH shell dumpsys package com.google.android.projection.gearhead | grep -a -m2 -E "versionName|versionCode"
POCO_MAC=$(adb -s $PH shell settings get secure bluetooth_address | tr -d '\r' | tr a-f A-F); echo "POCO_MAC=$POCO_MAC"
adb -s $PH shell dumpsys bluetooth_manager | grep -a -A12 "Bonded devices"     # record names and count
adb -s $PH shell dumpsys window | grep -a mCurrentFocus                        # must be the launcher
adb -s $PH shell input keyevent KEYCODE_HOME
adb -s $PH shell log -t AutomationMarker P3-probe; sleep 1
adb -s $PH logcat -d -s AutomationMarker | grep -acF 'AutomationMarker: P3-probe'   # must be 1 or more
```

`POCO_MAC` must be a real address, not empty and not `02:00:00:00:00:00`. Round 1 read
`DC:B7:2E:5E:4E:59`. If it is wrong, read it from D-HU's bonded list and record the source. If the
`P3-probe` count is 0, the phone markers do not work: every phone window is void, so stop and report.

Then define `BASE`, `R1KEYS`, `R2KEYS` and `R2CKEYS` from section 4 in this shell.

**P4. Host tools.** `python3 --version` must work. `command -v ffprobe ffmpeg` is optional.

```bash
VLCBIN=$(command -v vlc || command -v cvlc); echo "VLCBIN=$VLCBIN"; [ -n "$VLCBIN" ] && $VLCBIN --version 2>/dev/null | head -1
echo "DISPLAY=$DISPLAY"
```

Round 1 found VLC 3.0.24 at `/usr/bin/vlc`.

**P5. Thermal.** `th_pkg` must print one number. Then `th_wait 70` before the build.

**P7. The D-SAM viewer.** Do this before P6, because P6 may need to ask about it.

```bash
source ./viewers.sh
adb devices | grep -q "^$SAM" || echo "VIEWER_SAM_UNTESTABLE no adb"
adb -s $SAM shell getprop ro.build.version.sdk                                     # expect 19
adb -s $SAM shell pm path org.videolan.vlc                                         # empty: VLC is not installed
adb -s $SAM shell pm dump org.videolan.vlc | grep -a -c '\.StartActivity'          # must be 1 or more
adb -s $SAM shell dumpsys battery | grep -a -m1 level
SAM_IP=$(adb -s $SAM shell ip -4 addr show wlan0 | grep -a -o 'inet [0-9.]*' | cut -d' ' -f2); echo "SAM_IP=$SAM_IP"
adb -s $SAM shell date; date                                                        # record both
```

`SAM_IP` of `0.0.0.0` or empty means D-SAM has no WiFi network. Round 1 found exactly that.

If `SAM_IP` is a real address, run round 1's route test (round 1 brief, section 7, P7, "Route test"), and
set `ROUTE` and `VIEW_URL` from its count as round 1 did.

**P6. The one ask, only if needed.** Ask the operator in one message, and only for these:

- any rig unit that is powered on but not on adb (P1): switch its Bluetooth off or power it off;
- if P4 found no VLC: may the executor run `sudo apt-get install -y vlc` on the tester PC?
- if P7 found no WiFi address on D-SAM: please join D-SAM to the house WiFi that the tester PC uses;
- if P7 found no VLC on D-SAM: may the executor install VLC for Android on D-SAM with
  `adb -s 30041c35642d2200 install <apk>`, using an APK from VideoLAN's own download site whose minimum
  API is 19 or lower?

A no, or `INSTALL_FAILED_OLDER_SDK`, makes that viewer UNTESTABLE. After the answers, re-run the P4
`VLCBIN` line and every P7 line, then the route test if `SAM_IP` is now real. If none applies, skip P6.

### R0. Build gate

Build with `--max-workers=2` after `th_wait 70`, with round 1's `ssr_build.sh` (or `build_hur.sh`)
pointed at `../ohu-wt-ssr-4897c121`. Copy the APK out of `apks/` at once.

```bash
adb -s $HU install -r <apk>
adb -s $HU pull $(adb -s $HU shell pm path $PKG | cut -d: -f2 | tr -d '\r') "$OUT/live.apk"; md5sum "$OUT/live.apk" <apk>
send ACTION_QUERY_STATE
```

**PASS:** the build succeeds, the two md5s agree, and the `data=` reply carries commit `4897c1218417`.
A `-dirty` suffix is allowed only when `git status --porcelain` in the worktree lists nothing but the
rig's own `.WORKTREE-INFO`. Record it.
**A build failure stops the round.** Unit tests are not re-run here: the SDLC run passed 3217.

### R1. Feature off, a normal session (5 min)

Guards the rebase onto `ec9d9c33` with the feature at its default.

```bash
bringup R1 $R1KEYS; rc=$?; echo "R1 rc=$rc"
[ $rc = 1 ] && { run_end; keep_attempt R1; bringup R1 $R1KEYS; rc=$?; echo "R1 retry rc=$rc"; }
preflight                                                   # a FAIL with rc=0: fix the keys, re-run R1 once
mark R1-hold; sleep 60; mark R1-check
python3 -I tput.py "$CAP" R1-up R1-check > "$OUT/R1.tput"; cat "$OUT/R1.tput"
run_end
aux_counts R1 | tee "$OUT/R1.counts"
phone_grades R1 R1-launch R1-end | tee "$OUT/R1.phone"
```

From `R1.counts` (window `R1-launch` to `R1-end`):

- `a1` = `b1` (must be 0). `a2` = `grep -acF "VIDEO_AUX" "$OUT/R1-hu.win"`. `a8` = `grep -acF "INPUT_AUX" "$OUT/R1-hu.win"`.
- `a4` = `b3`. `a6` = `errors` from `b9`. `a7` = `b10` (report; R2 compares with it).
- `R1.tput`: main `windows`, `windows_rendered_ge1`, `median_fps`, `dropped_sum`.
- `R1.phone`: `win|Critical error`, `phone_markers_in_window`.

**PASS:** `a1`, `a2`, `a8`, `a4` and `a6` are 0, `b4m` is 1 or more, main `windows` is 10 or more,
main `windows_rendered_ge1` is at least 90% of main `windows`, `phone_markers_in_window` is 2 or more,
and `win|Critical error` is 0.
**FAIL:** any of those is not met. Quote the first offending line.
If both attempts fail at `SESSION_FAIL_SSL`, compare the MAC in the phone's last
`Creating rfcomm socket for device` line with `HU_MAC`. A different MAC is Gearhead's stale target
(`rig-quirks/units/D-POCO.md`), not the candidate. Report it and stop the round. Any other R1 FAIL also
stops the round, because R2 has no control.

### R2. Feature on, network output, AUXILIARY, with the two viewers: THE POINT OF THE ROUND (8 min)

**If the fix did nothing,** the phone would end every session as in round 1: `b4` 0, `b4m` 0,
`win|Critical error` 1 or more with `No input for display 1`. A PASS needs `b4` and `b4m`, which round 1
never had, so a PASS cannot come from an unchanged path.

```bash
bringup R2 $R2KEYS; rc=$?; echo "R2 rc=$rc"
[ $rc = 1 ] && { run_end; keep_attempt R2; bringup R2 $R2KEYS; rc=$?; echo "R2 retry rc=$rc"; }
if [ $rc = 0 ]; then
  preflight
  adb -s $HU forward tcp:5000 tcp:5000; mark R2-recv
  python3 -I aux_recv.py 5000 60 "$OUT/R2-aux.h264" 90 > "$OUT/R2-recv.txt" & RECV=$!
  if [ "$ROUTE" = hu ]; then VIEW_URL=; HUIP=$(adb -s $HU shell ip -4 addr show wlan0 | grep -a -o 'inet [0-9.]*' | cut -d' ' -f2)
    [ -n "$HUIP" ] && [ "${HUIP%.*}" = "${SAM_IP%.*}" ] && VIEW_URL=http://$HUIP:5000/aux.h264; echo "HUIP=$HUIP VIEW_URL=$VIEW_URL"; fi
  [ "$ROUTE" = relay ] && { python3 -I relay.py 5001 5000 > "$OUT/R2-relay.log" 2>&1 & RELAY=$!; }
  sleep 3; pc_vlc R2; mark R2-viewers
  sam_view R2
  python3 -I aux_recv.py 5000 10 "$OUT/R2-http.h264" 30 http > "$OUT/R2-http.txt"
  wait $RECV; [ -n "$VPC" ] && wait $VPC; [ -n "$RELAY" ] && { kill $RELAY; RELAY=; }
  cat "$OUT/R2-recv.txt" "$OUT/R2-http.txt"; mark R2-check
  python3 -I pngdims.py $(cat "$OUT/R2-vlc-pc.t0" 2>/dev/null || echo 0) "$OUT"/R2-vlc-pc/*.png | tee "$OUT/R2.vlc-pc"
  grep -a "RELAY" "$OUT/R2-relay.log" 2>/dev/null | tee "$OUT/R2.relay"
  grep -a -i -E "avcodec|h264|vout|error" "$OUT/R2-vlc-pc.log" 2>/dev/null | head -20 > "$OUT/R2.vlc-pc-log"
  grep -a -i "vlc" "$OUT/R2-sam.txt" 2>/dev/null | head -40 > "$OUT/R2.sam-vlc-log"
  RS=$(grep -o 'received_s=[0-9.]*' "$OUT/R2-recv.txt" | cut -d= -f2)
  python3 -I nal_count.py "$OUT/R2-aux.h264" "${RS:-0}" | tee "$OUT/R2.nal"
  command -v ffprobe >/dev/null && ffprobe -v error -count_frames -select_streams v:0 -show_entries stream=codec_name,profile,width,height,nb_read_frames -of default=nw=1 "$OUT/R2-aux.h264" | tee "$OUT/R2.ffprobe"
  command -v ffmpeg >/dev/null && ffmpeg -v error -i "$OUT/R2-aux.h264" -frames:v 1 "$OUT/R2-aux.png"
  python3 -I tput.py "$CAP" R2-up R2-check > "$OUT/R2.tput"; cat "$OUT/R2.tput"
else
  preflight || true                                         # for the record only
fi
[ $rc != 3 ] && { run_end; adb -s $HU forward --remove tcp:5000 2>/dev/null; }
aux_counts R2 | tee "$OUT/R2.counts"
phone_grades R2 R2-launch R2-end | tee "$OUT/R2.phone"
```

`rc=3` means the host was too hot: R2 is UNTESTABLE (host thermal), and the stop rule applies.

Record from `R2.counts`: every `b` value, the `b1` line, the `b4` codec numbers, the `b12` counts and
the quoted lines. From `R2.phone`: every `win|` and `quote|` value and the quoted lines. From the host
files: `R2-recv.txt` (`result`, `first_byte_after_s`, `bytes`, `early_eof`), `R2-http.txt`
(`http_status`), `R2.nal` (`idr`, `slices`, `slices_per_s`, `profile_idc`, `width`, `height`),
`R2.ffprobe` if present, and the main numbers of `R2.tput` beside R1's.

**Outcomes.** Grade in this order and stop at the first that matches.

- **Setup failure:** `b1` is 0, or `b1_input` is 0, or `b2` is 1 or more. Fix the keys and re-run R2
  once. A `b1` line that does not end `input on INPUT_AUX` means the APK is not the candidate: re-check R0.
- **Void:** `phone_markers_in_window` is below 2. Re-run R2 once.
- **FAIL-B, the feature breaks the main session:** any `win|` row in `R2.phone` is 1 or more, or `b10` is
  above R1's `a7`, or `rc` was 2, or (`rc` was 0 and main `windows_rendered_ge1` is below 90% of main
  `windows`). Quote the first `Critical error` line in full. Name which `win|` row fired.
- **FAIL-A, the phone never opens channel 14:** `b3` is 0, `b4m` is 1 or more, and the FAIL-B rows are
  clear. If `quote|blocked by power saving` is 1 or more, write "the phone dropped the display by its
  power saving setting".
- **FAIL-C, channel open, no picture:** `b3` is 1 or more, and `R2.nal` has `idr` 0 or `slices` below 10.
  Record which of `b4`, `b5`, `b6` appeared, to show where it stopped.
- **PASS:** `b3`, `b4`, `b4m`, `b5`, `b6` and `b7` are each 1 or more, `R2.nal` has `idr` 1 or more and
  `slices` 10 or more, `b8` is 2 or more with `b8_max` 2 or more, `http_status` is `'HTTP/1.1 200 OK'`,
  `b11` is 0, and the FAIL-B rows are clear.

Report the main `median_fps` and `dropped_sum` beside R1's whatever the outcome.

**VIEWER verdicts.** One per viewer, apart from R2's verdict. They never change it.

- **VIEWER-PC (VLC on the tester PC):**
  - **INCONCLUSIVE:** `b7` is 0. No stream existed for VLC to play.
  - **UNTESTABLE:** `VLCBIN` is empty after P6.
  - **PASS:** `R2.vlc-pc` shows `count` 1 or more and `width`x`height` 1280x720. Report `first_after_s`.
  - **FAIL:** `b7` is 1 or more, VLC ran, and `count` is 0. Quote the `R2.vlc-pc-log` lines.
- **VIEWER-SAM (VLC for Android on D-SAM as a display):**
  - **INCONCLUSIVE:** `b7` is 0.
  - **UNTESTABLE:** D-SAM not on adb, no VLC after P6, no `.StartActivity`, no WiFi address, or
    `VIEW_URL` empty after the R2 route step. Name which.
  - **PASS:** a stream reached the tablet, and a screenshot shows the picture. The stream reached it when
    `R2.relay` has `RELAY close <SAM_IP> down_bytes=N` with N of 100000 or more (route `relay`), or when a
    `network client connected from /<SAM_IP>` line is in the `R2-hu.win` window (route `hu`).
    **The host reads the four screenshots** (`R2-sam-5s.png` to `R2-sam-35s.png`). An extract cannot grade
    them. A picture is the Android Auto map or card, not VLC's own screen or a black frame. Report the
    first screenshot with a picture as the seconds to first picture.
  - **FAIL:** a stream reached the tablet and no screenshot shows a picture. Quote `R2.sam-vlc-log` and
    `R2-sam.top`.
- Record `R2-sam.ohu-procs`. It must be 0.

### R2C. The same as R2 with the CLUSTER role, no viewers (6 min). Always run it after R2.

```bash
bringup R2C $R2CKEYS; rc=$?; echo "R2C rc=$rc"
[ $rc = 1 ] && { run_end; keep_attempt R2C; bringup R2C $R2CKEYS; rc=$?; echo "R2C retry rc=$rc"; }
if [ $rc = 0 ]; then
  preflight
  adb -s $HU forward tcp:5000 tcp:5000; mark R2C-recv
  python3 -I aux_recv.py 5000 45 "$OUT/R2C-aux.h264" 90 > "$OUT/R2C-recv.txt" & RECV=$!
  sleep 5; python3 -I aux_recv.py 5000 10 "$OUT/R2C-http.h264" 30 http > "$OUT/R2C-http.txt"
  wait $RECV; cat "$OUT/R2C-recv.txt" "$OUT/R2C-http.txt"; mark R2C-check
  RS=$(grep -o 'received_s=[0-9.]*' "$OUT/R2C-recv.txt" | cut -d= -f2)
  python3 -I nal_count.py "$OUT/R2C-aux.h264" "${RS:-0}" | tee "$OUT/R2C.nal"
  command -v ffmpeg >/dev/null && ffmpeg -v error -i "$OUT/R2C-aux.h264" -frames:v 1 "$OUT/R2C-aux.png"
  python3 -I tput.py "$CAP" R2C-up R2C-check > "$OUT/R2C.tput"; cat "$OUT/R2C.tput"
else
  preflight || true
fi
[ $rc != 3 ] && { run_end; adb -s $HU forward --remove tcp:5000 2>/dev/null; }
aux_counts R2C | tee "$OUT/R2C.counts"
phone_grades R2C R2C-launch R2C-end | tee "$OUT/R2C.phone"
```

Grade R2C with R2's outcome list, on the R2C files, with these differences:

- The `b1` line must say `role=CLUSTER` and `content the phone's choice`. If not, it is a setup failure.
- PASS needs `b8` 2 or more and `b8_max` 1 or more (two receivers, one after the other). No viewer conditions.
- **INCONCLUSIVE, the phone refused the cluster:** `quote|Video focus rejected for display type` is 1 or
  more, the FAIL-B rows are clear, and there is no picture. That is the phone's own policy for this car
  make, not the candidate. Record `b3`, `b4`, `b5`, `b6`.

### R3. Android display output, session end and the next session (decision line 1), with R4 inside (10 min)

**Skip rule.** Decide the role, or skip, from R2 and R2C:

- R2 is FAIL-B: skip R3 and R4. Both are INCONCLUSIVE, "skipped: R2 FAIL-B".
- R2 is not FAIL-B and its `b3` is 1 or more: `ROLESPEC="del:aux-display-role"` (AUXILIARY).
- Else, R2C is not FAIL-B and its `b3` is 1 or more: `ROLESPEC="str:aux-display-role=CLUSTER"`.
- Else: skip R3 and R4. Both are INCONCLUSIVE, "the phone never opened VIDEO_AUX".

**Setup.**

```bash
adb -s $HU shell settings put global overlay_display_devices 800x480/160; sleep 3
OVL=$(adb -s $HU shell dumpsys display | grep -a -o 'DisplayInfo{"Overlay #1", displayId [0-9]*' | head -1 | grep -o '[0-9]*$')
[ -z "$OVL" ] && OVL=$(adb -s $HU shell dumpsys display | grep -a -B40 'mPrimaryDisplayDevice=Overlay #1' | grep -a -o 'mDisplayId=[0-9]*' | tail -1 | cut -d= -f2)
echo "OVL=$OVL ROLESPEC=$ROLESPEC"
R3KEYS="bool:aux-display-enabled=true str:aux-output=ANDROID_DISPLAY int:aux-display-id=$OVL del:aux-network-size del:aux-network-port $ROLESPEC del:aux-display-content del:preferred-display-mode del:preferred-display-id"
```

If `OVL` is empty, R3 and R4 are UNTESTABLE ("this build makes no overlay display"). Go to Cleanup.

**Steps.** R4 runs inside the first session, before the session end.

```bash
bringup R3 $R3KEYS; rc=$?; echo "R3 rc=$rc"
[ $rc = 1 ] && { run_end; keep_attempt R3; bringup R3 $R3KEYS; rc=$?; echo "R3 retry rc=$rc"; }
if [ $rc != 0 ]; then                                      # then go to Cleanup
  [ $rc != 3 ] && run_end; phone_grades R3 R3-launch R3-end | tee "$OUT/R3.phone"; echo "R3_STOPPED rc=$rc"
fi
# only with rc=0, from here to phone_grades:
preflight
mark R3-s1-hold; sleep 40; mark R3-s1-check
adb -s $HU exec-out screencap -p > "$OUT/R3-s1.png"; sleep 0.3
python3 -I tput.py "$CAP" R3-up R3-s1-check > "$OUT/R3-s1.tput"; cat "$OUT/R3-s1.tput"
mark R4-sleep; adb -s $HU shell input keyevent 223; sleep 15
mark R4-wake-go; adb -s $HU shell input keyevent 224; sleep 1; adb -s $HU shell wm dismiss-keyguard; mark R4-wake
sleep 45; mark R4-check
adb -s $HU exec-out screencap -p > "$OUT/R4-wake.png"; sleep 0.3
python3 -I tput.py "$CAP" R4-wake R4-check R4-wake > "$OUT/R4.tput"; cat "$OUT/R4.tput"
mark R3-end1; L=$(nl); send ACTION_END_SESSION_STAY_ARMED
waitfor 90 'SSL handshake complete' $L || { mark R3-scan; send ACTION_START_WIRELESS_SCAN; waitfor 90 'SSL handshake complete' $L; } || echo "SESSION2_FAIL"
mark R3-s2-ssl; L=$(nl)
waitfor 40 'Throughput over [0-9]+ms: rendered=[1-9]' $L || { send ACTION_RAISE_PROJECTION >/dev/null; echo "RAISE_VERB_USED R3-s2"; }
sleep 45; mark R3-s2-check
adb -s $HU exec-out screencap -p > "$OUT/R3-s2.png"; sleep 0.3
python3 -I tput.py "$CAP" R3-s2-ssl R3-s2-check R3-s2-ssl > "$OUT/R3-s2.tput"; cat "$OUT/R3-s2.tput"
run_end
phone_grades R3 R3-launch R4-sleep R3-s2-ssl R3-end | tee "$OUT/R3.phone"
phone_grades R3 R4-sleep R4-check | tee "$OUT/R4.phone"
```

`R3_STOPPED rc=1` after two attempts: R3 and R4 INCONCLUSIVE (rig). `rc=2`: R3 FAIL-B (quote the first
`Critical error` line), R4 INCONCLUSIVE. `rc=3`: both UNTESTABLE (host thermal).

`R3.phone` leaves out the R4 window and the session end at `R3-end1`. The session end makes the phone log
`Critical error` lines by design. `R4.phone` is report only.

**R3 counts** on `$OUT/R3-hu.txt`:

- `c1` = `cnt $OUT/R3-hu.txt R3-launch R3-s1-check "AapProjectionActivity: the auxiliary display is up on "`
- `c2` = the `DisplayTargets: projecting on display 0 ` line, quoted. It must list `$OVL:Overlay #1 800x480@160`.
- `c3` = `R3-s1.tput`: aux `windows_rendered_ge1`, and main `windows_rendered_ge1` of `windows`
- `c4` = `cnt $OUT/R3-hu.txt R3-end1 R3-s2-ssl "Decoder stopped: CommManager: doDisconnect (second screen)"`
- `c5` = `cnt $OUT/R3-hu.txt R3-end1 R3-s2-ssl "AapService: ACTION_END_SESSION_STAY_ARMED received"`
- `c6` = `SESSION2_FAIL` printed or not, and `R3-scan` used or not
- `c7` = `seg $OUT/R3-hu.txt R3-up R3-s2-check | grep -a ' OPENHU' | awk '{print $3}' | sort -u | wc -l` (1 means no app restart)
- `c8` = `R3-s2.tput`: aux `windows_rendered_ge1` and `first_aux_picture_after_R3-s2-ssl_s`
- `c9` = `grep -acF "could not open the auxiliary display: " $OUT/R3-hu.txt` and `grep -acF "cannot carry a second screen" $OUT/R3-hu.txt`
- `c10` = `grep -acF "AapTransport: no auxiliary keyframe cycle before its setup" $OUT/R3-hu.txt` (quote only)
- phone: every `win|` row and `phone_markers_in_window` in `R3.phone`

**R3 verdict:**

- **Setup failure:** `c1` is 0, or `c9` is 1 or more. Quote the line. Re-run R3 once.
- **Void:** `phone_markers_in_window` in `R3.phone` is below 4. Re-run R3 once.
- **FAIL-B:** any `win|` row in `R3.phone` is 1 or more. Quote the first `Critical error` line.
- **INCONCLUSIVE:** `c3` shows aux `windows_rendered_ge1` 0 in session 1, or `c6` shows `SESSION2_FAIL`.
- **PASS:** `c4` is 1, `c5` is 1, `c7` is 1, and `c8` shows aux `windows_rendered_ge1` 1 or more with
  `first_aux_picture_after_R3-s2-ssl_s` 60 or less.
- **FAIL:** session 2 formed with a main picture, and `c4` is 0, or `c7` is above 1, or `c8` shows no aux
  picture in 60 s.

Report the main `windows_rendered_ge1` of `windows` in both sessions beside R1's. Two hardware H.264
decoders run at once here.

**R4 counts** on `$OUT/R3-hu.txt`:

- `d1` = `cnt $OUT/R3-hu.txt R4-sleep R4-wake-go "WakeDetect: SCREEN_OFF"`, and `cnt $OUT/R3-hu.txt R4-wake-go R4-check "WakeDetect: SCREEN_ON (screen was off for "`
- `d2` = `cnt` over `R4-sleep` to `R4-check` for each of `Decoder stopped: CommManager: doDisconnect (second screen)`,
  `Decoder stopped: the auxiliary display went away`, `Decoder stopped: the auxiliary display was dismissed`. Sum them.
- `d3` = `cnt $OUT/R3-hu.txt R4-sleep R4-check "AuxDisplayPresentation: surface ready on display "`
- `d4` = `R4.tput`: aux `windows_rendered_ge1` and `first_aux_picture_after_R4-wake_s`
- `d5` = the timestamps of every `cycling the auxiliary display's video focus` line between `R4-wake` and `R4-check`
- `d6` = `cnt $OUT/R3-hu.txt R4-sleep R4-check "Decoder stopped: screen_off_sleep"` (the main decoder; report only)
- `d7` = `R4.phone`: every `win|` row (report only)

**R4 verdict** (grade in this order):

- **UNTESTABLE:** either `d1` count is 0. The sleep or the wake never landed.
- **INCONCLUSIVE, surface lost:** `d3` is 1 or more. Report `d2`, `d4` and `d5`.
- **PASS:** `d2` is 0, `d4` shows aux `windows_rendered_ge1` 1 or more with
  `first_aux_picture_after_R4-wake_s` 30 or less, and no `d5` timestamp is earlier than that first aux picture.
- **FAIL:** `d3` is 0 and any PASS condition is not met.

### Cleanup (always, last)

```bash
adb -s $HU shell settings delete global overlay_display_devices
adb -s $HU shell am force-stop $PKG
adb -s $HU push settings-backup-HU.xml /data/local/tmp/ssr-settings.xml
adb -s $HU shell cp /data/local/tmp/ssr-settings.xml /data/data/$PKG/shared_prefs/settings.xml
adb -s $HU shell chown $(adb -s $HU shell stat -c %u:%g /data/data/$PKG | tr -d '\r') /data/data/$PKG/shared_prefs/settings.xml
adb -s $HU shell chmod 660 /data/data/$PKG/shared_prefs/settings.xml
adb -s $HU shell cat /data/data/$PKG/shared_prefs/settings.xml | diff - settings-backup-HU.xml && echo RESTORED
adb -s $HU shell settings get global overlay_display_devices      # must print null
```

Restore D-POCO's radios with `ph_on` if a run ended with them off.

### Stop rule

- Each bring-up gets at most 2 attempts. `rc=2` gets 1 attempt.
- R1 FAIL stops the round.
- A preflight FAIL with `rc=0` is re-run once. A second FAIL makes that run UNTESTABLE.
- R2C always runs after R2. R3 and R4 follow the skip rule in R3.
- The host gate: a run that cannot start below 75C in 15 min is UNTESTABLE (host thermal). After a
  second such run, stop the round and mark every run after it UNTESTABLE (host thermal).

## 8. Do not re-run

- **Round 1's R2 at `331b0a8c`.** It is the negative control for this fix: the same keys, the same phone,
  no aux input, 18 sessions in 18 ended with `No input for display 1`. Do not build `331b0a8c` again.
- Native AA session forming between D-HU and D-POCO: settled in many rounds. R1 is a control here.
- The aux lane's shed, ACK session, quit join and hole callback, and the oracle for the phone's display
  rules: covered by the 3217 JVM tests (`VideoLaneTest`, `AuditRecoveryPolicyTest`,
  `AuxDisplayAnnouncementTest`, `SecondaryVideoFocusPolicyTest`).

## 9. Report back

1. **R2 and R2C:** did the session form with the aux display announced (`win|` rows, `b4m`), did the phone
   open `VIDEO_AUX` (`b3`, `b4` with its codec), and did a picture arrive (`idr`, `slices_per_s`,
   `profile_idc`, `width`x`height`). Quote every `Discovered input for display` and `Input binding answered`
   line.
2. **The cost to the main picture:** main `median_fps`, `windows_rendered_ge1` of `windows` and
   `dropped_sum` in R2, R2C and R3 beside R1, and every windowed phone `Critical error` count.
3. **R3 and R4:** the two decision verdicts, with `c4`, `c8`, `d2`, `d3` and the seconds to the aux
   picture after each event.
4. **The viewers:** VIEWER-PC and VIEWER-SAM for R2, each with its seconds to first picture, and the
   D-SAM route used.

