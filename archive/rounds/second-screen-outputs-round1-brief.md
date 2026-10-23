# second-screen-outputs, round 1 brief

Published name on the transfer branch: `second-screen-outputs-round1-brief.md`. Results file:
`second-screen-outputs-round1-results.md`. Evidence release: `rig-evidence-second-screen-outputs`
(first round of the thread, so create it), asset `second-screen-outputs-round1-captures.zip`.

Units: **D-HU** (head unit), **D-POCO** (phone) and **D-SAM** (a second screen viewer, R2 only).
Estimated time: 20 min of pre-flight and build, then 32 min of runs (R1 5 min, R2 8 min, R2C 8 min
only if needed, R3 with R4 10 min). Total about 50 min.

## 1. Build and baseline

- **Candidate:** `feat/second-screen-outputs` on `fork` at **`331b0a8ce852a00b878e7978739dff0036e0dec1`**,
  tree `ca2dba4ce2ed0ae5e748bec0d193be435834c1e4`. Five commits on `main` `71375a68`.
- **History was rewritten.** The branch was force-pushed on 2026-10-09 over the old tip `6cfcca3a`.
  Do not `git pull` an old checkout of it. Use a new worktree at the SHA, or hard-reset:

```bash
git ls-remote fork feat/second-screen-outputs   # MUST print 331b0a8ce852a00b878e7978739dff0036e0dec1
git fetch fork feat/second-screen-outputs
git worktree add ../ohu-wt-ssr-331b0a8c 331b0a8ce852a00b878e7978739dff0036e0dec1
git -C ../ohu-wt-ssr-331b0a8c rev-parse 'HEAD^{tree}'   # MUST print ca2dba4ce2ed0ae5e748bec0d193be435834c1e4
# an existing checkout of the branch instead: git checkout feat/second-screen-outputs && git reset --hard 331b0a8ce852a00b878e7978739dff0036e0dec1
```

- **No baseline build.** R1 is the feature-off control on the candidate itself (template section 8,
  a settings change on the candidate).

## 2. What this is and why

The branch adds a second video stream from Android Auto. The head unit announces a second video sink
on channel 14 (`ID_VID2`, logged as `VIDEO_AUX`) in its service discovery. That sink has its own
video lane, its own decoder, and one of four outputs:

- `ANDROID_DISPLAY`: a `Presentation` window on a second Android display. The head unit decodes.
- `NETWORK`: raw Annex-B H.264 on TCP port 5000, with HTTP for VLC. The head unit only forwards.
- `MS912X`: a MacroSilicon USB HDMI adapter. The head unit decodes.
- `USB_DISPLAY`: the open Open Headunit USB display protocol (a Pi Zero 2 W gadget).

The feature is off by default (`aux-display-enabled` false). While it is off, the service discovery
bytes are the same as on `main`. The branch is a port onto `main`: commit 1 moves the video thread out
of `AapTransport` into `VideoLane`, with `main`'s ACK rule, backlog shedding and quit. 3115 JVM tests
pass. **No hardware round has ever run this feature in our tree.**

The owner asked for "a brief to test this, at least what we can test". This round answers three
questions on the hardware we have:

1. Does a normal session with the feature off still behave as on `main`? (R1)
2. Does the phone open channel 14 at all, and does an aux picture arrive? What codec and size, and
   does the main picture stay healthy beside it? (R2, the point of the round)
3. The two decision lines from the plan, on an output that decodes on the head unit (R3, R4):
   - After a session end, `Decoder stopped: CommManager: doDisconnect (second screen)` appears, and
     the next session shows an aux picture with no app restart.
   - After a screen-off and wake, no `Decoder stopped:` line names the second screen, and the aux
     picture continues with no `cycling the auxiliary display's video focus` line in between.

The owner also wants to see the aux picture on two real viewers: VLC on the tester PC, and a rig
tablet used as a display. Both run inside R2's session and have their own VIEWER verdict. They do not
change R2's verdict.

The answers decide whether an MS912x adapter or a Pi Zero 2 W is worth buying for round 2.

## 3. What is different about this round

- **Hardware this rig does not have is out.** The rig has no MS912x adapter and no Pi Zero 2 W, and
  D-HU cannot host USB (`host_connected=false`, `rig-quirks/topics/tooling.md`). So `MS912X` and
  `USB_DISPLAY` are not tested. The Pi receiver in `tools/second-screen/` is not used.
- **The Android display arm uses a simulated display.** No rig unit has a second Android display.
  Android's own developer setting makes one with no hardware:
  `settings put global overlay_display_devices 800x480/160` adds a display named `Overlay #1`.
  It draws as a small window on D-HU's own panel and carries `FLAG_PRESENTATION`. R3 and R4 run the
  `ANDROID_DISPLAY` output on it, because the `NETWORK` output builds no decoder. If D-HU's build
  does not create the display, R3 and R4 are UNTESTABLE (section 7, R3 setup).
- **The aux decoder and the main decoder print the same lines.** Both print `Throughput over` and
  `Configuring decoder` with nothing to tell them apart. `tput.py` (section 5) separates them by
  thread id: the aux decoder's output thread is the one that logs `Output Format Changed:` with
  `width=800`. So the capture uses `logcat -v threadtime`, and the overlay display is 800x480 on
  purpose, apart from the main picture's 1920.
- **The aux lane opens on the first message on channel 14,** which is the phone's channel open, not
  the first frame. `AapTransport: the auxiliary display's video lane is open` therefore proves the
  phone opened the channel. A picture needs the frame counts on top of it.
- **The network output opens only after the lane opens.** Nothing listens on port 5000 before the
  phone opens channel 14. `aux_recv.py` retries the connection every second for that reason.
- **Two viewers ride along in R2.** `NetworkStreamOutput` keeps a list of clients with no limit,
  and each client has its own bounded queue, so a slow viewer cannot hold up the others. The graded
  instrument stays `aux_recv.py`. The viewers are:
  - **VLC on the tester PC**, on the HTTP route through the same `adb forward`. VLC's scene filter
    writes one PNG for every 30 decoded frames, so the proof needs no window grab.
  - **VLC for Android on D-SAM.** D-POCO is the Android Auto phone and D-HU is the head unit, so
    the tablet is the only choice left. D-HP has the same API problem (below) and a flaky USB link.
    D-SAM is API 19, and `adb reverse` needs API 21 or later. So the tablet needs a network path:
    a host relay (`relay.py`, port 5001 to the forward's port 5000) over the house LAN, tested in
    pre-flight P7. If the relay is not reachable, the fallback is D-HU's own `wlan0` address on port
    5000, if the tablet shares its subnet during the session. The stock player cannot play a bare
    H.264 stream over HTTP, so VLC is the only player used. If neither route works, or VLC cannot be
    installed on API 19, the D-SAM viewer is UNTESTABLE.
  - A viewer that falls behind makes the app ask the phone for a keyframe (`fell behind; resuming at
    the next keyframe`, then an aux focus cycle). That adds keyframes for every receiver. Count it in
    `b9`. It does not change R2's verdict.
- **Projection is proved at INFO by** `Media Sink Setup Request: 3 on channel VIDEO` and
  `Throughput over ...ms: rendered=`. `Service Discovery Response` and `Channel Open Response` are
  VERBOSE-only on this build and are not used. `TransportStarted` is never logged.
  Note `on channel VIDEO` is also a prefix of `on channel VIDEO_AUX`, so the main sink is matched
  with a line-end anchor.
- **The main map is idle.** Nobody touches the phone, so Android Auto may send few frames on a
  static map. R1 and R2 run the same idle condition, so compare R2's main numbers with R1's, not with
  a fixed rate.
- **One expected cache miss.** The branch folds the display id into the screen settings hash, so the
  first launch after the install misses `cachedSurfaceSettingsHash` once. That is a known follow-up,
  not a fault of this round.
- **The rig's audio settings are a deliberate worst case.** Do not change `use-aac-audio`,
  `audio-latency-multiplier`, `audio-queue-capacity` or `enable-audio-sink`. Read them back and record
  them in Setup notes.
- **Expected INCONCLUSIVE, said now:**
  - R3 and R4, if the phone opens channel 14 in neither R2 nor R2C. Section 7 then skips them.
  - R4's decision, if the overlay display's surface is destroyed at screen-off. The decision line
    assumes the surface outlives the sleep. On an overlay display it may not.
- **Not visible on the rig:** thread names are cut to 15 characters
  (`AapTransport:Ha`), so the main and aux lane threads cannot be told apart in `ps -T`. The JVM tests
  cover that the aux thread is joined at quit.

## 4. Settings keys

All runs are on D-HU, written as root with the app stopped, by `hu_keys` (section 5). `hu_keys`
reads every key back with `ssr_keys.py --check` and prints `KEYS_OK` or `KEYS_MISMATCH <keys>`.
`POCO_MAC` comes from pre-flight P3. `OVL` comes from R3's setup.

**Every run (`BASE`):**

| Key | Type | Value | Why |
|---|---|---|---|
| `wifi-connection-mode` | int | `3` | Native AA wireless, the rig's only transport |
| `native-poke-bt-macs` | set | `POCO_MAC` | wake D-POCO only |
| `native-poke-all-paired` | boolean | `false` | no other bonded phone is poked |
| `native-driver-selection-mode` | int | `0` | no selector countdown |
| `onboarding-version` | int | `2` | no wizard on launch |
| `log-level` | int | `2` | INFO. Every deciding line in section 6 is INFO or WARN |
| `video-codec` | string | `H.264` | main sink `3`, the same codec as the aux sink |
| `resolutionId` | int | `3` | 1080p main picture, apart from the aux sizes |
| `view-mode` | int | `1` | TEXTURE |
| `force-software-decoding` | boolean | `false` | both decoders on hardware |
| `software-video-decoder` | int | delete | |
| `debug-video-fault-injection` | int | delete | |
| `video-profile-starvation-cap` | boolean | delete | a latch that forces 720p30 and AAC |
| `night-mode` | int | `1` | DAY, no sensor message mid-window |

**Per run (the aux keys, from `Settings.kt` on the candidate):**

| Key | Type | R1 | R2 | R2C | R3, R4 |
|---|---|---|---|---|---|
| `aux-display-enabled` | boolean | delete (default `false`) | `true` | `true` | `true` |
| `aux-output` | string | delete | `NETWORK` | `NETWORK` | `ANDROID_DISPLAY` |
| `aux-network-size` | int | delete | `1` (1280x720) | `1` | delete |
| `aux-network-port` | int | delete | delete (default 5000) | delete | delete |
| `aux-display-role` | string | delete | delete (`AUXILIARY`) | `CLUSTER` | delete, or `CLUSTER` (section 7) |
| `aux-display-content` | int | delete | delete (65538, the map) | delete | delete |
| `aux-display-id` | int | delete | delete | delete | `OVL` |
| `preferred-display-mode` | int | delete | delete | delete | delete (0, the built-in panel) |
| `preferred-display-id` | int | delete | delete | delete | delete |

`aux-network-size` indexes 800x480, 1280x720 and 1920x1080. `ACTION_LOG_MARKER` is not gated on the
candidate (`AutomationCommandPolicy.CONFIGURING` lacks it), so `allow-external-configuration` is not
needed.

```bash
BASE="int:wifi-connection-mode=3 set:native-poke-bt-macs=$POCO_MAC bool:native-poke-all-paired=false int:native-driver-selection-mode=0 int:onboarding-version=2 int:log-level=2 str:video-codec=H.264 int:resolutionId=3 int:view-mode=1 bool:force-software-decoding=false del:software-video-decoder del:debug-video-fault-injection del:video-profile-starvation-cap int:night-mode=1"
R1KEYS="del:aux-display-enabled del:aux-output del:aux-network-size del:aux-network-port del:aux-display-role del:aux-display-content del:aux-display-id del:preferred-display-mode del:preferred-display-id"
R2KEYS="bool:aux-display-enabled=true str:aux-output=NETWORK int:aux-network-size=1 del:aux-network-port del:aux-display-role del:aux-display-content del:aux-display-id del:preferred-display-mode del:preferred-display-id"
R2CKEYS="bool:aux-display-enabled=true str:aux-output=NETWORK int:aux-network-size=1 del:aux-network-port str:aux-display-role=CLUSTER del:aux-display-content del:aux-display-id del:preferred-display-mode del:preferred-display-id"
# R3KEYS is set in R3's setup, after OVL is known
```

## 5. Every action as a verb, and the helpers

| Step | Command |
|---|---|
| marker | `mark <label>` = `send ACTION_LOG_MARKER --es text <label>` |
| identity | `send ACTION_QUERY_STATE` |
| raise the projection, fallback only | `send ACTION_RAISE_PROJECTION` (inside `bringup`; it prints `RAISE_VERB_USED`) |
| end a session, keep the network | `send ACTION_END_SESSION_STAY_ARMED` |
| re-arm wireless, fallback only | `send ACTION_START_WIRELESS_SCAN` |
| exit between runs | `send ACTION_EXIT`, then `am force-stop` (inside `run_end`) |

**No verb exists for these, and none is needed on the app:**

- Launch: `adb shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity` (template section 3).
- The simulated display: `adb shell settings put global overlay_display_devices 800x480/160`. A
  system setting, not an app control.
- Sleep and wake: `adb shell input keyevent 223` (SLEEP), `224` (WAKEUP), `wm dismiss-keyguard`.
  OS keyevents, allowed by template section 0.
- The stream: `adb forward tcp:5000 tcp:5000` and `aux_recv.py` on the host.
- The viewers: VLC on the host (`pc_vlc`), `relay.py` on the host, and on D-SAM an
  `am start -a android.intent.action.VIEW -d <url> -t video/h264 -n org.videolan.vlc/.StartActivity`
  (`sam_view`). These act on VLC, not on our app. `am` on API 19 rejects `-p`, so the intent names
  the component with `-n` (`rig-quirks/units/D-SAM.md`, `rig-quirks/topics/audio.md`).

**There is no hand step during the round and no tap.** The only possible human step is pre-flight P6.

### Scripts

Make the folder `hur-wifi-test-scripts/second-screen-outputs-round1/`, save the eight files below in
it, and `cd` into it for the whole round. List them in Setup notes. If a script does not match the
real line format, fix it, say so in Setup notes, and keep going. `python3 -I` runs every Python file.

**`ssr_lib.sh`** (source it: `source ./ssr_lib.sh`):

```bash
# ssr_lib.sh : source it inside hur-wifi-test-scripts/second-screen-outputs-round1/
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
MAIN=$PKG/com.andrerinas.openheadunit.main.MainActivity
HU=27870808938846; PH=4f4027e9
D=$(pwd); OUT=$D/out; mkdir -p "$OUT"
send() { local a=$1; shift; adb -s $HU shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; sleep 0.3; }
mark() { send ACTION_LOG_MARKER --es text "$1" >/dev/null; }
nl() { wc -l < "$CAP"; }
# waitfor <secs> <ERE> <from-line> : 0 when the pattern appears in $CAP at or after <from-line>
waitfor() { local end=$((SECONDS+$1)); while [ $SECONDS -lt $end ]; do tail -n +"$3" "$CAP" | grep -aqE -- "$2" && return 0; sleep 1; done; return 1; }
# seg <file> <from-marker> <to-marker> : the lines between two markers, both included
seg() { sed -n "/AutomationMarker: $2\$/,/AutomationMarker: $3\$/p" "$1"; }
cnt()  { seg "$1" "$2" "$3" | grep -acF -- "$4"; }     # fixed string
cntE() { seg "$1" "$2" "$3" | grep -acE -- "$4"; }     # extended regex
# Host thermal, copied from projection-teardown-and-relays round 1 (ptr_lib.sh)
th_pkg() { if [ -x ../rig_thermal.sh ]; then ../rig_thermal.sh status | grep -o 'pkg=[0-9]*' | cut -d= -f2; return; fi
  for z in /sys/class/hwmon/hwmon*; do [ "$(cat $z/name 2>/dev/null)" = coretemp ] && { echo $(( $(cat $z/temp1_input) / 1000 )); return; }; done
  echo $(( $(cat /sys/class/thermal/thermal_zone0/temp) / 1000 )); }
th_thr() { cat /sys/devices/system/cpu/cpu0/thermal_throttle/package_throttle_count 2>/dev/null || echo 0; }
th_wait() { local lim=${1:-75} n=0; while [ "$(th_pkg)" -ge "$lim" ]; do n=$((n+1)); [ $n -ge 90 ] && return 1; sleep 10; done; }
th_watch() { while :; do echo "$(date +%T) pkg=$(th_pkg)C throttle_pkg=$(th_thr)"; sleep 20; done >> "$1"; }
th_gate() { th_wait 75 || { echo "$(date +%T) HOST_TOO_HOT ${RUN:-run}" | tee -a "$OUT/thermal.log"; return 3; }
  [ -n "$THPID" ] && kill $THPID 2>/dev/null; th_watch "$OUT/${RUN:-run}.thermal" & THPID=$!
  echo "$(date +%T) ${RUN:-run} start pkg=$(th_pkg)C throttle_pkg=$(th_thr)" >> "$OUT/thermal.log"; }
th_report() { awk -F'[ =C]+' '{p=$3; t=$5; if(p>m)m=p; if(NR==1)t0=t} END{print "thermal_max=" m "C throttle_delta=" t-t0}' "$OUT/$1.thermal"; }
# Phone radios. D-POCO ignores airplane mode for this; svc is the proven method.
ph_off() { adb -s $PH shell svc wifi disable; sleep 0.3; adb -s $PH shell svc bluetooth disable; sleep 5; }
ph_on() { adb -s $PH shell svc bluetooth enable; sleep 0.3; adb -s $PH shell svc wifi enable; sleep 8
  { adb -s $PH shell dumpsys wifi | grep -a -m1 "Wi-Fi is"; sleep 0.3
    adb -s $PH shell dumpsys bluetooth_manager | grep -a -m1 -iE "^ *state:"; } | tee -a "$OUT/$RUN.radios"; }
# One capture per unit per run. The phone capture streams from now and does not clear the phone.
cap_start() { CAP=$OUT/$RUN-hu.txt; PCAP=$OUT/$RUN-phone.txt
  adb -s $HU logcat -G 16M; sleep 0.3; adb -s $HU logcat -c
  stdbuf -oL adb -s $HU logcat -v threadtime > "$CAP" & CAPPID=$!
  stdbuf -oL adb -s $PH logcat -v threadtime -T 1 -b main,system,crash > "$PCAP" & PCAPPID=$!; sleep 2; }
cap_stop() { kill $CAPPID $PCAPPID 2>/dev/null; wait $CAPPID $PCAPPID 2>/dev/null; ps aux | grep -c "[l]ogcat"; }
# hu_keys <spec...> : BASE plus the specs into D-HU's settings.xml, as root, app stopped. Reads it back.
hu_keys() { adb -s $HU shell cat /data/data/$PKG/shared_prefs/settings.xml > "$OUT/cur.xml"
  python3 -I "$D/ssr_keys.py" "$OUT/cur.xml" "$OUT/new.xml" $BASE "$@" || return 1
  adb -s $HU push "$OUT/new.xml" /data/local/tmp/ssr-settings.xml >/dev/null
  local own; own=$(adb -s $HU shell stat -c %u:%g /data/data/$PKG | tr -d '\r')
  adb -s $HU shell cp /data/local/tmp/ssr-settings.xml /data/data/$PKG/shared_prefs/settings.xml
  adb -s $HU shell chown $own /data/data/$PKG/shared_prefs/settings.xml
  adb -s $HU shell chmod 660 /data/data/$PKG/shared_prefs/settings.xml
  adb -s $HU shell cat /data/data/$PKG/shared_prefs/settings.xml > "$OUT/$RUN.prefs.xml"
  python3 -I "$D/ssr_keys.py" --check "$OUT/$RUN.prefs.xml" $BASE "$@"; }
# bringup <RUN> <spec...> : 0 = a session with a picture, 1 = no session, 3 = host too hot
bringup() { RUN=$1; shift
  th_gate || return 3
  adb -s $HU shell am force-stop $PKG; sleep 1
  ph_off; adb -s $PH shell input keyevent KEYCODE_HOME
  hu_keys "$@" || { echo "KEYS_FAIL $RUN"; return 1; }
  cap_start
  adb -s $HU shell am start -n $MAIN >/dev/null; sleep 3; mark $RUN-launch; sleep 15
  local L; L=$(nl); ph_on; mark $RUN-phone-on
  waitfor 150 'SSL handshake complete' $L || { echo "SESSION_FAIL_SSL $RUN"; return 1; }
  waitfor 40 'Throughput over [0-9]+ms: rendered=[1-9]' $L || { send ACTION_RAISE_PROJECTION >/dev/null; echo "RAISE_VERB_USED $RUN"
    waitfor 30 'Throughput over [0-9]+ms: rendered=[1-9]' $L || { echo "SESSION_FAIL_NO_FRAMES $RUN"; return 1; }; }
  mark $RUN-up; return 0; }
# preflight : proves this capture ran the path under test. Marks <RUN>-preflight-ok or -FAIL.
preflight() { local f=$OUT/$RUN.preflight ok=1 c; : > "$f"
  c=$(grep -acF "WifiLauncher: Initializing WiFi Mode: NATIVE" "$CAP"); echo "mode_native=$c" >> "$f"; [ "$c" -ge 1 ] || ok=0
  c=$(grep -acF "Projection backend: viewMode=TEXTURE " "$CAP"); echo "view_texture=$c" >> "$f"; [ "$c" -ge 1 ] || ok=0
  c=$(grep -acE "Media Sink Setup Request: 3 on channel VIDEO[[:space:]]*$" "$CAP"); echo "main_sink_h264=$c" >> "$f"; [ "$c" -ge 1 ] || ok=0
  c=$(grep -acE "HotspotManager:|SoftApCredentials:" "$CAP"); echo "hotspot_lines=$c" >> "$f"; [ "$c" -eq 0 ] || ok=0
  c=$(grep -ac "GH\." "$PCAP"); echo "phone_gh_lines=$c" >> "$f"; [ "$c" -ge 1 ] || ok=0
  cat "$f"; if [ $ok = 1 ]; then mark $RUN-preflight-ok; return 0; fi; mark $RUN-preflight-FAIL; echo "PREFLIGHT_FAIL $RUN"; return 2; }
# run_end : exit the app, close the window and both captures
run_end() { mark $RUN-end; send ACTION_EXIT >/dev/null; sleep 4; mark $RUN-closed; sleep 1
  adb -s $HU shell am force-stop $PKG; cap_stop; th_report $RUN | tee -a "$OUT/$RUN.preflight"; }
# phone_grades <RUN> : the phone conditions, whole phone file
phone_grades() { local P=$OUT/$1-phone.txt
  echo "gh_lines=$(grep -ac 'GH\.' "$P") critical_error=$(grep -ac 'Critical error' "$P")"
  grep -a 'Critical error' "$P" | head -3
  grep -a -i -E 'auxiliary|cluster|DISPLAY_TYPE|secondary display|display_?id' "$P" | head -20; }
```

**`ssr_keys.py`**:

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

**`tput.py`**:

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

**`aux_recv.py`**:

```python
# aux_recv.py <port> <receive_s> <out_file> <wait_s> [http]
# Connects to 127.0.0.1:<port> (the adb forward), retrying each second until the first byte or <wait_s>.
# Then records <receive_s> seconds. With "http" it sends a GET first and prints the status line.
import socket, sys, time
port, secs, out, wait = int(sys.argv[1]), float(sys.argv[2]), sys.argv[3], float(sys.argv[4])
http = len(sys.argv) > 5 and sys.argv[5] == "http"
t0 = time.time(); deadline = t0 + wait; attempts = 0; s = None; data = bytearray()
while time.time() < deadline:
    attempts += 1
    try:
        s = socket.create_connection(("127.0.0.1", port), timeout=3)
        if http: s.sendall(b"GET / HTTP/1.0\r\n\r\n")
        s.settimeout(5)
        first = s.recv(65536)
        if first: data += first; break
        s.close(); s = None
    except OSError:
        s = None
    time.sleep(1)
if not data:
    open(out, "wb").close()
    print("AUX_RECV result=no_data attempts=%d waited_s=%.1f" % (attempts, time.time() - t0)); sys.exit(1)
first_at = time.time(); eof = False
while time.time() < first_at + secs:
    try:
        c = s.recv(65536)
        if not c: eof = True; break
        data += c
    except OSError:
        pass
s.close()
status = ""
if http:
    status = data.split(b"\r\n", 1)[0][:40].decode("ascii", "replace")
    cut = data.find(b"\r\n\r\n"); data = data[cut + 4:] if cut >= 0 else data
open(out, "wb").write(bytes(data))
print("AUX_RECV result=data attempts=%d first_byte_after_s=%.1f received_s=%.1f bytes=%d early_eof=%s http_status=%r"
      % (attempts, first_at - t0, time.time() - first_at, len(data), eof, status))
```

**`nal_count.py`**:

```python
# nal_count.py <dump.h264> <receive_seconds> : counts H.264 NAL units and reads the first SPS
import sys
data = open(sys.argv[1], 'rb').read(); secs = float(sys.argv[2])
starts = []; i = 0
while True:
    j = data.find(b'\x00\x00\x01', i)
    if j < 0: break
    starts.append(j + 3); i = j + 3
units = []
for k, s in enumerate(starts):
    e = starts[k + 1] - 3 if k + 1 < len(starts) else len(data)
    units.append(data[s:e].rstrip(b'\x00'))
count = {}
for u in units:
    if u: count[u[0] & 0x1f] = count.get(u[0] & 0x1f, 0) + 1
def rbsp(n):
    out = bytearray(); z = 0
    for b in n:
        if z >= 2 and b == 3: z = 0; continue
        out.append(b); z = z + 1 if b == 0 else 0
    return bytes(out)
class Bits:
    def __init__(s, b): s.b = b; s.p = 0
    def u(s, n):
        v = 0
        for _ in range(n):
            v = (v << 1) | ((s.b[s.p >> 3] >> (7 - (s.p & 7))) & 1); s.p += 1
        return v
    def ue(s):
        z = 0
        while s.u(1) == 0: z += 1
        return (1 << z) - 1 + s.u(z)
    def se(s):
        k = s.ue(); return (k + 1) // 2 if k & 1 else -(k // 2)
sps = "none"
for u in units:
    if u and u[0] & 0x1f == 7:
        try:
            r = Bits(rbsp(u[1:])); prof = r.u(8); r.u(8); lvl = r.u(8); r.ue()
            if prof in (100, 110, 122, 244, 44, 83, 86, 118, 128, 138, 139, 134, 135):
                if r.ue() == 3: r.u(1)
                r.ue(); r.ue(); r.u(1)
                if r.u(1): raise ValueError("scaling lists")
            r.ue(); poc = r.ue()
            if poc == 0: r.ue()
            elif poc == 1:
                r.u(1); r.se(); r.se()
                for _ in range(r.ue()): r.se()
            r.ue(); r.u(1); wm = r.ue(); hm = r.ue(); fmo = r.u(1)
            if not fmo: r.u(1)
            r.u(1); cl = cr = ct = cb = 0
            if r.u(1): cl, cr, ct, cb = r.ue(), r.ue(), r.ue(), r.ue()
            w = (wm + 1) * 16 - 2 * (cl + cr); h = (2 - fmo) * (hm + 1) * 16 - 2 * (2 - fmo) * (ct + cb)
            sps = "profile_idc=%d level_idc=%d width=%d height=%d" % (prof, lvl, w, h)
        except Exception as ex:
            sps = "unparsed (%s)" % ex
        break
slices = count.get(1, 0) + count.get(5, 0)
print("bytes=%d nal_total=%d sps=%d pps=%d idr=%d non_idr=%d slices=%d slices_per_s=%.1f %s"
      % (len(data), len(units), count.get(7, 0), count.get(8, 0), count.get(5, 0), count.get(1, 0),
         slices, slices / secs if secs > 0 else 0, sps))
```

**`viewers.sh`** (source it after `ssr_lib.sh`: `source ./viewers.sh`):

```bash
# viewers.sh : source it after ssr_lib.sh. Pre-flight P7 sets VLCBIN, VIEW_URL and ROUTE.
SAM=30041c35642d2200
# pc_vlc <RUN> : VLC on the host plays the HTTP route for 45 s, in the background. Its scene filter
# writes one PNG per 30 decoded frames, so no window grab is needed. Sets VPC.
pc_vlc() { VPC=; [ -z "$VLCBIN" ] && { echo "VIEWER_PC skipped: no VLC"; return 0; }
  local V=$VLCBIN; [ -z "$DISPLAY" ] && ! xset -display :0 q >/dev/null 2>&1 && V="$VLCBIN --vout=dummy"
  mkdir -p "$OUT/$1-vlc-pc"; date +%s.%N > "$OUT/$1-vlc-pc.t0"
  ( DISPLAY=${DISPLAY:-:0} timeout 45 $V --no-one-instance --play-and-exit --demux=h264 --network-caching=300 \
      --video-filter=scene --scene-format=png --scene-ratio=30 --scene-path="$OUT/$1-vlc-pc" --scene-prefix=pc \
      --file-logging --logfile="$OUT/$1-vlc-pc.log" --log-verbose=2 http://127.0.0.1:5000/aux.h264 >/dev/null 2>&1 ) & VPC=$!; }
# sam_view <RUN> : VLC for Android on D-SAM plays VIEW_URL; screenshots at 5, 10, 20 and 35 s; then stop it.
sam_view() { [ -z "$VIEW_URL" ] && { echo "VIEWER_SAM skipped: no route"; return 0; }
  adb -s $SAM shell am force-stop $PKG; sleep 0.3
  stdbuf -oL adb -s $SAM logcat -v time > "$OUT/$1-sam.txt" & SAMCAP=$!; sleep 1
  date +%s.%N > "$OUT/$1-sam.t0"
  adb -s $SAM shell am start -a android.intent.action.VIEW -d "$VIEW_URL" -t video/h264 -n org.videolan.vlc/.StartActivity
  local t prev=0
  for t in 5 10 20 35; do sleep $((t - prev)); prev=$t
    adb -s $SAM shell screencap -p /sdcard/ssr-view.png; sleep 0.3
    adb -s $SAM pull /sdcard/ssr-view.png "$OUT/$1-sam-${t}s.png" >/dev/null 2>&1; sleep 0.3; done
  adb -s $SAM shell dumpsys activity activities | grep -a -m2 -E "mFocusedActivity|mResumedActivity" > "$OUT/$1-sam.top"; sleep 0.3
  adb -s $SAM shell am force-stop org.videolan.vlc; sleep 0.3
  adb -s $SAM shell rm /sdcard/ssr-view.png; kill $SAMCAP 2>/dev/null; wait $SAMCAP 2>/dev/null
  adb -s $SAM shell ps | grep -ac headunitrevived > "$OUT/$1-sam.ohu-procs"; }
```

**`relay.py`**:

```python
# relay.py <listen_port> <target_port> : relays 0.0.0.0:<listen_port> to 127.0.0.1:<target_port> (the adb forward).
# One line per connection: RELAY open <peer>, and RELAY close <peer> down_bytes=N seconds=S when it ends.
import socket, sys, threading, time
lp, tp = int(sys.argv[1]), int(sys.argv[2])
def pipe(src, dst, count):
    try:
        while True:
            b = src.recv(65536)
            if not b: break
            dst.sendall(b); count[0] += len(b)
    except OSError: pass
    for s in (src, dst):
        try: s.shutdown(socket.SHUT_RDWR)
        except OSError: pass
def serve(c, peer):
    t0 = time.time(); down = [0]; up = [0]
    try: u = socket.create_connection(("127.0.0.1", tp), timeout=5); u.settimeout(None)
    except OSError as e: print("RELAY upstream_failed %s %s" % (peer, e), flush=True); c.close(); return
    print("RELAY open %s" % peer, flush=True)
    t = threading.Thread(target=pipe, args=(c, u, up), daemon=True); t.start()
    pipe(u, c, down); t.join(2)
    print("RELAY close %s down_bytes=%d seconds=%.1f" % (peer, down[0], time.time() - t0), flush=True)
srv = socket.socket(); srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
srv.bind(("0.0.0.0", lp)); srv.listen(8); print("RELAY listening %d -> 127.0.0.1:%d" % (lp, tp), flush=True)
while True:
    c, a = srv.accept()
    threading.Thread(target=serve, args=(c, a[0]), daemon=True).start()
```

**`pngdims.py`**:

```python
# pngdims.py <start_epoch_s> <png>... : how many PNGs, the first one's size, and its seconds after <start>
import os, struct, sys
t0 = float(sys.argv[1]); files = sorted(sys.argv[2:], key=os.path.getmtime)
files = [f for f in files if open(f, "rb").read(8) == b"\x89PNG\r\n\x1a\n"]
if not files: print("PNG count=0"); sys.exit(1)
w, h = struct.unpack(">II", open(files[0], "rb").read(24)[16:24])
print("PNG count=%d first=%s width=%d height=%d first_after_s=%.1f" % (len(files), os.path.basename(files[0]), w, h, os.path.getmtime(files[0]) - t0))
```

## 6. The deciding lines

Every string below was checked with `grep -F -r` in the candidate's `app/src` at `331b0a8c`. All are
INFO or WARN, so `log-level` 2 carries them. A `...` marks a value the app fills in. `VIDEO_AUX` is
`Channel.name(14)`. Files: `$OUT/<RUN>-hu.txt` (head unit) and `$OUT/<RUN>-phone.txt` (phone).
Windows are marker pairs, `seg <file> <from> <to>`.

| Line (head unit) | Means |
|---|---|
| `AutomationReceiver: ` | a verb landed. A step with no such line is void, not a FAIL |
| `WifiLauncher: Initializing WiFi Mode: NATIVE` | preflight: Native path |
| `Projection backend: viewMode=TEXTURE ` | preflight: view backend |
| `Media Sink Setup Request: 3 on channel VIDEO` at line end | preflight: main sink, H.264 |
| `HotspotManager:`, `SoftApCredentials:` | preflight: must be 0 |
| `SSL handshake complete` | session formed |
| `Throughput over ...ms: rendered=N` | frames; `tput.py` splits main from aux |
| `Output Format Changed: ` with `width=800` | names the aux decoder's output thread (R3, R4) |
| `[ServiceDiscovery] Announcing an auxiliary display on VIDEO_AUX: <output> WxH as _WxH, margins ...` | the aux sink was announced |
| `SecondScreen: the <OUTPUT> output is not available, so one display is announced` | the output was missing, so nothing was announced |
| `AapTransport: the auxiliary display's video lane is open` | the phone opened channel 14 |
| `Media Sink Setup Request: N on channel VIDEO_AUX` | the phone set up the aux sink; N is its codec (3 = H.264 BP) |
| `Granting video focus on VIDEO_AUX after its setup` | we gave the aux sink focus |
| `Media Start Request VIDEO_AUX: session=...` | the phone started the aux stream |
| `RX: Video Focus Request - mode: ..., reason: ..., channel: VIDEO_AUX` | the phone asked for aux focus |
| `The phone released the second display's video. The main session continues.` | the phone released aux focus |
| `Auxiliary Video Sink Stopped -> ` | the phone stopped the aux sink |
| `SecondScreen: the NETWORK output is open` | R2: the output started |
| `SecondScreen: network stream listening on port 5000` | R2: the server is up |
| `SecondScreen: network client connected from ` | R2: a receiver connected; the line ends `(N connected)` |
| `fell behind; resuming at the next keyframe` | a receiver's queue overflowed |
| `AapTransport: cycling the auxiliary display's video focus for a keyframe (` | an aux keyframe request |
| `AapTransport: the VIDEO_AUX thread is ` | the aux lane shed its backlog |
| `Error processing video message` | a lane threw |
| `AapProjectionActivity: the auxiliary display is up on ` | R3: the Presentation is showing |
| `AuxDisplayPresentation: surface ready on display ` | R3, R4: the aux surface was created (again) |
| `AapProjectionActivity: the auxiliary display ` + `is not attached` | R3: setup failure |
| `cannot carry a second screen` | R3: setup failure |
| `could not open the auxiliary display: ` | R3: the Presentation threw |
| `DisplayTargets: projecting on display ` | the display list the session saw, with `N:Overlay #1 800x480@160` in R3 |
| `Decoder stopped: CommManager: doDisconnect (second screen)` | the aux decoder stopped at session end |
| `Decoder stopped: the auxiliary display went away`, `Decoder stopped: the auxiliary display was dismissed` | the aux decoder stopped for its surface |
| `AapService: ACTION_END_SESSION_STAY_ARMED received` | R3: the session end landed |
| `WakeDetect: SCREEN_OFF`, `WakeDetect: SCREEN_ON (screen was off for ` | R4: the sleep and wake landed |

Viewer evidence is not in our tree: `RELAY open <ip>` and `RELAY close <ip> down_bytes=N seconds=S`
(`relay.py`), `PNG count=N ... width=W height=H first_after_s=S` (`pngdims.py`), the D-SAM
screenshots, and VLC's own logs. VLC's log strings drift between builds, so no verdict rests on one.

Phone strings (`$OUT/<RUN>-phone.txt`, whole file) are Gearhead's, not ours, and they drift between
builds. Report the Gearhead version in Setup notes. `GH.` (capture gate, must be 1 or more) and
`Critical error` (Android Auto ending the session over something we sent; see CLAUDE.md, the levers
Android Auto refuses by name). `phone_grades` also prints lines with `auxiliary`, `cluster`,
`DISPLAY_TYPE`, `secondary display` or `display_id` for the record. They are not graded.

## 7. Runs

### Pre-flight (one batch, before R0)

**P1. Units.** `adb devices -l` must list `27870808938846` (D-HU) and `4f4027e9` (D-POCO). For each
other rig unit on adb, stop its Bluetooth so it neither pokes nor answers:

```bash
for s in ZY22GC3BM4; do adb devices | grep -q "^$s" && { adb -s $s shell svc bluetooth disable; sleep 0.3; adb -s $s shell svc wifi disable; }; done
for s in 30041c35642d2200 0123456789ABCDEF CNU350BGBJ; do adb devices | grep -q "^$s" && adb -s $s shell am force-stop com.andrerinas.headunitrevived; done
```

Write in Setup notes which units were on adb, and which were not.

**P2. D-HU state.**

```bash
source ./ssr_lib.sh
adb -s $HU shell stat -c '%U:%G %a' /data/data/$PKG/shared_prefs        # record it (D-HU quirk)
adb -s $HU shell cat /data/data/$PKG/shared_prefs/settings.xml > settings-backup-HU.xml
grep -o -E '(use-aac-audio|audio-latency-multiplier|audio-queue-capacity|enable-audio-sink)[^/]*' settings-backup-HU.xml
grep -o -E 'aux-[a-z-]*|preferred-display-[a-z]*' settings-backup-HU.xml   # expect nothing; record what is there
adb -s $HU shell settings get global overlay_display_devices               # expect null; record it
HU_MAC=$(adb -s $HU shell settings get secure bluetooth_address | tr -d '\r'); echo "HU_MAC=$HU_MAC"
```

**P3. D-POCO state.**

```bash
adb -s $PH shell dumpsys package com.google.android.projection.gearhead | grep -a -m2 -E "versionName|versionCode"
POCO_MAC=$(adb -s $PH shell dumpsys bluetooth_manager | grep -a -m1 -o -iE 'address: ([0-9A-F]{2}:){5}[0-9A-F]{2}' | awk '{print toupper($2)}'); echo "POCO_MAC=$POCO_MAC"
adb -s $PH shell dumpsys bluetooth_manager | grep -a -A12 "Bonded devices"     # record names and count
adb -s $PH shell dumpsys window | grep -a mCurrentFocus                        # must be the launcher
adb -s $PH shell input keyevent KEYCODE_HOME
```

`POCO_MAC` must be a real address, not empty and not `02:00:00:00:00:00`. If it is, read it from
D-HU's bonded list instead (`adb -s $HU shell dumpsys bluetooth_manager | grep -a -A12 "Bonded devices"`,
the D-POCO entry) and record which source gave it.

Then define `BASE`, `R1KEYS`, `R2KEYS` and `R2CKEYS` from section 4 in this shell.

**P4. Host tools.** `python3 --version` must work. `command -v ffprobe ffmpeg` is optional: with
them, R2 adds a decoded-frame count and a still image. Then find VLC:

```bash
VLCBIN=$(command -v vlc || command -v cvlc); echo "VLCBIN=$VLCBIN"; [ -n "$VLCBIN" ] && $VLCBIN --version 2>/dev/null | head -1
echo "DISPLAY=$DISPLAY"
```

No rig record says whether VLC is on the tester PC. If `VLCBIN` is empty, put its install in P6.

**P5. Thermal.** `th_wait 70` before the build.

**P7. The D-SAM viewer.** Do this before P6, because P6 may need to ask about it.

```bash
source ./viewers.sh
adb devices | grep -q "^$SAM" || echo "VIEWER_SAM_UNTESTABLE no adb"            # D-SAM must be on adb
adb -s $SAM shell getprop ro.build.version.sdk                                     # expect 19
adb -s $SAM shell pm path org.videolan.vlc                                         # empty: VLC is not installed
adb -s $SAM shell pm dump org.videolan.vlc | grep -a -c '\.StartActivity'          # must be 1 or more
adb -s $SAM shell dumpsys battery | grep -a -m1 level                              # record it; the port does not charge it
SAM_IP=$(adb -s $SAM shell ip -4 addr show wlan0 | grep -a -o 'inet [0-9.]*' | cut -d' ' -f2); echo "SAM_IP=$SAM_IP"
```

If `SAM_IP` is empty, read it from `adb -s $SAM shell netcfg | grep -a wlan0`. An empty address means
D-SAM is not on a WiFi network, and the viewer is UNTESTABLE.

**Route test (host relay).** Serve one small file from the host and open it in D-SAM's browser.
This reaches the host only if the LAN and the host firewall both let it through:

```bash
HOSTIP=$(ip -4 route get "$SAM_IP" | grep -o 'src [0-9.]*' | cut -d' ' -f2); echo "HOSTIP=$HOSTIP"
mkdir -p "$OUT/probe"; echo ok > "$OUT/probe/probe.txt"
python3 -I -m http.server 5001 --bind 0.0.0.0 --directory "$OUT/probe" > "$OUT/probe.log" 2>&1 & HS=$!; sleep 2
adb -s $SAM shell am start -a android.intent.action.VIEW -d "http://$HOSTIP:5001/probe.txt"; sleep 10
kill $HS; adb -s $SAM shell input keyevent 3
grep -a "GET /probe.txt" "$OUT/probe.log" | grep -c "$SAM_IP"                       # 1 or more: the relay route works
```

Set the route from that count:

- 1 or more: `ROUTE=relay; VIEW_URL=http://$HOSTIP:5001/aux.h264`.
- 0: `ROUTE=hu; VIEW_URL=` for now. R2 tries D-HU's `wlan0` address after its bring-up (section 7, R2).

If `pm path` was empty, put the VLC install in P6. If `.StartActivity` reads 0 after an install,
the D-SAM viewer is UNTESTABLE ("this VLC build has no StartActivity"). Do not try other components.

**P6. The one ask, only if needed.** Ask the operator in one message, and only for these:
- any rig unit that is powered on but not on adb (P1): switch its Bluetooth off or power it off;
- if P4 found no VLC: may the executor run `sudo apt-get install -y vlc` on the tester PC (or will the
  operator run it)? A no makes the PC viewer UNTESTABLE;
- if P7 found no VLC on D-SAM: may the executor install VLC for Android on D-SAM with
  `adb -s 30041c35642d2200 install <apk>`, using an APK from VideoLAN's own download site whose
  minimum API is 19 or lower? `INSTALL_FAILED_OLDER_SDK`, or a no, makes the D-SAM viewer
  UNTESTABLE.

If none of these applies, skip P6. After the answers, re-run the P4 `VLCBIN` line and the P7
`pm path` and `.StartActivity` lines.

### R0. Build gate

Build with `--max-workers=2` after `th_wait 70`, using `build_hur.sh` with
`HUR_DIR=../ohu-wt-ssr-331b0a8c` (or its equivalent on this host). Copy the APK out of `apks/` at once.

```bash
adb -s $HU install -r <apk>
adb -s $HU pull $(adb -s $HU shell pm path $PKG | cut -d: -f2 | tr -d '\r') "$OUT/live.apk"; md5sum "$OUT/live.apk" <apk>
send ACTION_QUERY_STATE
```

**PASS:** the build succeeds, the two md5s agree, and the `data=` reply carries commit `331b0a8c`.
**A build failure stops the round.** Unit tests are not re-run here: the SDLC run passed 3115.
`adb install -r` once wiped D-HU's settings (`rig-quirks/topics/tooling.md`), which is one reason
`hu_keys` writes and checks every key on every run.

### R1. Feature off, a normal session (5 min)

Guards the port of `VideoLane`, the ACK rule and quit with the feature at its default.

```bash
bringup R1 $R1KEYS || { run_end; bringup R1 $R1KEYS; }      # one retry; a second failure stops the round
preflight                                                   # a FAIL: fix the keys, re-run R1 once
mark R1-hold; sleep 60; mark R1-check
python3 -I tput.py "$CAP" R1-up R1-check > "$OUT/R1.tput"; cat "$OUT/R1.tput"
run_end
phone_grades R1 | tee "$OUT/R1.phone"
```

Counts, all on `R1-hu.txt` over the whole file unless a window is named:

- `a1` = `grep -acF "Announcing an auxiliary display on " R1-hu.txt`
- `a2` = `grep -acF "VIDEO_AUX" R1-hu.txt`
- `a3` = `grep -acF "SecondScreen: " R1-hu.txt`
- `a4` = `grep -acF "AapTransport: the auxiliary display's video lane is open" R1-hu.txt`
- `a5` = `cnt R1-hu.txt R1-end R1-closed "Decoder stopped: CommManager: doDisconnect (second screen)"`
- `a6` = `grep -acF "Error processing video message" R1-hu.txt`
- `a7` = `grep -acF "SSL handshake complete" R1-hu.txt` (report; one session prints one or two)
- `R1.tput`: main `windows`, `windows_rendered_ge1`, `median_fps`, `dropped_sum`
- phone: `critical_error`

**PASS:** `a1` to `a6` are all 0, main `windows` is 10 or more, main `windows_rendered_ge1` is at
least 90% of main `windows`, and phone `critical_error` is 0.
**FAIL:** any of those is not met. Quote the first offending line.
If both R1 attempts fail at `SESSION_FAIL_SSL`, compare the MAC in the phone's last
`Creating rfcomm socket for device` line with `HU_MAC`. A different MAC is Gearhead's stale target
(`rig-quirks/units/D-POCO.md`), not the candidate. Report it and stop the round.

A PASS with the feature off would look the same if the feature code did nothing at all. That is the
point of this run: it is the control for R2.

### R2. Feature on, network output, with the two viewers: THE POINT OF THE ROUND (8 min)

```bash
bringup R2 $R2KEYS || { run_end; bringup R2 $R2KEYS; }      # one retry; a second failure: R2 INCONCLUSIVE (rig)
preflight                                                   # a FAIL: fix the keys, re-run R2 once
adb -s $HU forward tcp:5000 tcp:5000; mark R2-recv
python3 -I aux_recv.py 5000 60 "$OUT/R2-aux.h264" 90 > "$OUT/R2-recv.txt" & RECV=$!
# viewers: not graded for R2, see VIEWER below
if [ "$ROUTE" = hu ]; then VIEW_URL=; HUIP=$(adb -s $HU shell ip -4 addr show wlan0 | grep -a -o 'inet [0-9.]*' | cut -d' ' -f2)
  [ -n "$HUIP" ] && [ "${HUIP%.*}" = "${SAM_IP%.*}" ] && VIEW_URL=http://$HUIP:5000/aux.h264; echo "HUIP=$HUIP VIEW_URL=$VIEW_URL"; fi
[ "$ROUTE" = relay ] && { python3 -I relay.py 5001 5000 > "$OUT/R2-relay.log" 2>&1 & RELAY=$!; }
sleep 3; pc_vlc R2; mark R2-viewers
sam_view R2                                                  # about 37 s, with the screenshots
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
run_end; adb -s $HU forward --remove tcp:5000
phone_grades R2 | tee "$OUT/R2.phone"
```

The receivers share one adb link with the captures. That is socket traffic through a forward, not
parallel `adb shell` calls, so house rule 8 does not apply. `sam_view` sends `adb` calls to D-SAM
only, one at a time, so it does not break the rule either.

Counts on `R2-hu.txt` (whole file):

- `b1` = the `[ServiceDiscovery] Announcing an auxiliary display on VIDEO_AUX:` line. Quote it. It must name `NETWORK 1280x720`.
- `b2` = `grep -acF "output is not available, so one display is announced"` (setup failure if 1 or more)
- `b3` = `grep -acF "AapTransport: the auxiliary display's video lane is open"`
- `b4` = `grep -a "Media Sink Setup Request: [0-9]* on channel VIDEO_AUX"`: quote it and record N
- `b5` = `grep -acF "after its setup"`
- `b6` = `grep -acF "Media Start Request VIDEO_AUX"`
- `b7` = `grep -acF "SecondScreen: network stream listening on port 5000"`
- `b8` = `grep -acF "SecondScreen: network client connected from "`, and the highest `(N connected)`
- `b9` = `grep -acF "fell behind; resuming at the next keyframe"`, `grep -acF "cycling the auxiliary display's video focus"`, `grep -acF "AapTransport: the VIDEO_AUX thread is "`, `grep -acF "Error processing video message"`
- `b10` = `grep -acF "SSL handshake complete"` and the same count for R1. A higher count than R1 means the session re-formed.
- `b11` = `grep -acF "Decoder stopped: CommManager: doDisconnect (second screen)"`. Must be 0: a forwarding output builds no decoder.
- host: `R2-recv.txt` (`result`, `first_byte_after_s`, `bytes`, `early_eof`), `R2-http.txt` (`http_status`), `R2.nal` (`idr`, `slices`, `slices_per_s`, `profile_idc`, `width`, `height`), `R2.ffprobe` if present
- `R2.tput`: main numbers, beside R1's
- phone: `critical_error`; any `RX: Video Focus Request` and `The phone released` lines for `VIDEO_AUX`, quoted

**Outcomes.** Grade in this order and stop at the first that matches.

- **Setup failure:** `b1` is 0, or `b2` is 1 or more. Fix the keys and re-run R2 once.
- **FAIL-B, the feature breaks the main session:** phone `critical_error` 1 or more, or `b10` above
  R1's, or main `windows_rendered_ge1` below 90% of main `windows`. Quote the phone's `Critical error`
  line. This is the worst outcome.
- **FAIL-A, the phone never opens channel 14:** `b3` is 0 and the main session is healthy.
- **FAIL-C, channel open, no picture:** `b3` is 1 or more, and `R2.nal` has `idr` 0 or `slices` below 10.
  Record which of `b4`, `b5`, `b6` appeared, to show where it stopped.
- **PASS:** `b3`, `b5`, `b6`, `b7` are each 1 or more, `R2.nal` has `idr` 1 or more and `slices` 10 or
  more, `b8` is 2 or more with a highest `(N connected)` of 2 or more, `http_status` is `'HTTP/1.1 200 OK'`, `b11` is
  0, and the FAIL-B conditions are clear.

A PASS that came from a broken stream would show `slices` but `idr` 0 or no `sps`. Report the main
`median_fps` and `dropped_sum` beside R1's whatever the outcome: that pair says what the aux stream
costs the main picture.

**VIEWER verdicts.** One per viewer, separate from R2's verdict. They never change it.

- **VIEWER-PC (VLC on the tester PC):**
  - **UNTESTABLE:** `VLCBIN` is empty after P6.
  - **PASS:** `R2.vlc-pc` shows `count` 1 or more and `width`x`height` equal to the size in `b1`
    (1280x720). Report `first_after_s`, the seconds from VLC's start to its first decoded frame.
  - **FAIL:** VLC ran and `count` is 0. Quote the `R2.vlc-pc-log` lines.
- **VIEWER-SAM (VLC for Android on D-SAM as a display):**
  - **UNTESTABLE:** D-SAM not on adb, no VLC after P6, no `.StartActivity`, no WiFi address, or
    `VIEW_URL` empty after the R2 route step. Name which.
  - **PASS:** a stream reached the tablet, and a screenshot shows the map. The stream reached it
    when `R2.relay` has a `RELAY close <SAM_IP> down_bytes=N` with N of 100000 or more (route
    `relay`), or when `b8`'s lines include `network client connected from /<SAM_IP>` (route `hu`).
    **The host reads the four screenshots** (`R2-sam-5s.png` to `R2-sam-35s.png`). An extract cannot
    grade them. A picture is the Android Auto map or card, not VLC's own screen or a black frame.
    Report the first screenshot with a picture as the seconds to first picture (5, 10, 20 or 35).
  - **FAIL:** a stream reached the tablet and no screenshot shows a picture. Quote `R2.sam-vlc-log`
    and `R2-sam.top`.
- Record `R2-sam.ohu-procs`. It must be 0: our app must not run on D-SAM during the session.

### R2C. The same as R2 with the CLUSTER role (8 min). Only if R2 is FAIL-A or FAIL-C.

Some Android Auto builds may serve a cluster and not an auxiliary display. Run R2's block with
`R2CKEYS` and every `R2` in names and markers replaced by `R2C`, the viewers included. Grade it the
same way, and quote the `Announcing` line, which must say `role=CLUSTER`. Skip R2C when R2 passed or hit FAIL-B.

### R3. Android display output, session end and the next session (decision line 1), with R4 inside (10 min)

**Skip R3 and R4** if `b3` was 0 in R2 and in R2C: they are INCONCLUSIVE, "the phone never opened
VIDEO_AUX". Otherwise use the role that opened the channel: delete `aux-display-role` if R2 opened
it, write `str:aux-display-role=CLUSTER` if only R2C did.

**Setup.**

```bash
adb -s $HU shell settings put global overlay_display_devices 800x480/160; sleep 3
OVL=$(adb -s $HU shell dumpsys display | grep -a -o 'DisplayInfo{"Overlay #1", displayId [0-9]*' | head -1 | grep -o '[0-9]*$')
[ -z "$OVL" ] && OVL=$(adb -s $HU shell dumpsys display | grep -a -B40 'mPrimaryDisplayDevice=Overlay #1' | grep -a -o 'mDisplayId=[0-9]*' | tail -1 | cut -d= -f2)
echo "OVL=$OVL"
R3KEYS="bool:aux-display-enabled=true str:aux-output=ANDROID_DISPLAY int:aux-display-id=$OVL del:aux-network-size del:aux-network-port del:aux-display-role del:aux-display-content del:preferred-display-mode del:preferred-display-id"
```

If `OVL` is empty, R3 and R4 are UNTESTABLE ("this build makes no overlay display"). Go to Cleanup.

**Steps.** R4 runs inside the first session, before the session end, so it does not depend on the
second session forming.

```bash
bringup R3 $R3KEYS || { run_end; bringup R3 $R3KEYS; }      # one retry; a second failure: R3 and R4 INCONCLUSIVE (rig)
preflight
mark R3-s1-hold; sleep 40; mark R3-s1-check
adb -s $HU exec-out screencap -p > "$OUT/R3-s1.png"; sleep 0.3
python3 -I tput.py "$CAP" R3-up R3-s1-check > "$OUT/R3-s1.tput"; cat "$OUT/R3-s1.tput"
# R4: sleep and wake inside session 1
mark R4-sleep; adb -s $HU shell input keyevent 223; sleep 15
mark R4-wake-go; adb -s $HU shell input keyevent 224; sleep 1; adb -s $HU shell wm dismiss-keyguard; mark R4-wake
sleep 45; mark R4-check
adb -s $HU exec-out screencap -p > "$OUT/R4-wake.png"; sleep 0.3
python3 -I tput.py "$CAP" R4-wake R4-check R4-wake > "$OUT/R4.tput"; cat "$OUT/R4.tput"
# R3: end the session, keep the network, and let the phone come back on its own
mark R3-end1; L=$(nl); send ACTION_END_SESSION_STAY_ARMED
waitfor 90 'SSL handshake complete' $L || { mark R3-scan; send ACTION_START_WIRELESS_SCAN; waitfor 90 'SSL handshake complete' $L; } || echo "SESSION2_FAIL"
mark R3-s2-ssl; L=$(nl)
waitfor 40 'Throughput over [0-9]+ms: rendered=[1-9]' $L || { send ACTION_RAISE_PROJECTION >/dev/null; echo "RAISE_VERB_USED R3-s2"; }
sleep 45; mark R3-s2-check
adb -s $HU exec-out screencap -p > "$OUT/R3-s2.png"; sleep 0.3
python3 -I tput.py "$CAP" R3-s2-ssl R3-s2-check R3-s2-ssl > "$OUT/R3-s2.tput"; cat "$OUT/R3-s2.tput"
run_end
phone_grades R3 | tee "$OUT/R3.phone"
```

`R3.phone` covers R3 and R4: one phone capture spans both.

**R3 counts** on `R3-hu.txt`:

- `c1` = `cnt R3-hu.txt R3-launch R3-s1-check "AapProjectionActivity: the auxiliary display is up on "`
- `c2` = the `DisplayTargets: projecting on display 0 ` line, quoted. It must list `$OVL:Overlay #1 800x480@160`.
- `c3` = `R3-s1.tput`: aux `windows_rendered_ge1` (session 1 aux picture) and main `windows_rendered_ge1` of `windows`
- `c4` = `cnt R3-hu.txt R3-end1 R3-s2-ssl "Decoder stopped: CommManager: doDisconnect (second screen)"`
- `c5` = `cnt R3-hu.txt R3-end1 R3-s2-ssl "AapService: ACTION_END_SESSION_STAY_ARMED received"`
- `c6` = `SESSION2_FAIL` printed or not, and `R3-scan` used or not
- `c7` = `seg R3-hu.txt R3-up R3-s2-check | grep -a ' OPENHU' | awk '{print $3}' | sort -u | wc -l` (process ids; 1 means no app restart)
- `c8` = `R3-s2.tput`: aux `windows_rendered_ge1` and `first_aux_picture_after_R3-s2-ssl_s`
- `c9` = `grep -acF "could not open the auxiliary display: "` and `grep -acF "cannot carry a second screen"` (setup failure if either is 1 or more)
- phone: `critical_error`

**R3 verdict:**

- **Setup failure:** `c1` is 0, or `c9` is 1 or more. Quote the line. Re-run R3 once.
- **INCONCLUSIVE:** `c3` shows aux `windows_rendered_ge1` 0 in session 1 (no aux picture to keep), or
  `c6` shows `SESSION2_FAIL`. Report the numbers.
- **PASS:** `c4` is 1, `c5` is 1, `c7` is 1, and `c8` shows aux `windows_rendered_ge1` 1 or more with
  `first_aux_picture_after_R3-s2-ssl_s` 60 or less, and phone `critical_error` is 0.
- **FAIL:** session 2 formed with a main picture, and `c4` is 0, or `c7` is above 1, or `c8` shows no
  aux picture in 60 s.

Report the main `windows_rendered_ge1` of `windows` in both sessions beside R1's. Two hardware H.264
decoders run at once here, which is the codec-contention question an MS912x round would also ask.

**R4 counts** on `R3-hu.txt`:

- `d1` = `cnt R3-hu.txt R4-sleep R4-wake-go "WakeDetect: SCREEN_OFF"`, and `cnt R3-hu.txt R4-wake-go R4-check "WakeDetect: SCREEN_ON (screen was off for "`
- `d2` = `cnt` over `R4-sleep` to `R4-check` for each of `Decoder stopped: CommManager: doDisconnect (second screen)`,
  `Decoder stopped: the auxiliary display went away`, `Decoder stopped: the auxiliary display was dismissed`. Sum them.
- `d3` = `cnt R3-hu.txt R4-sleep R4-check "AuxDisplayPresentation: surface ready on display "`
- `d4` = `R4.tput`: aux `windows_rendered_ge1` and `first_aux_picture_after_R4-wake_s`
- `d5` = the timestamps of every `cycling the auxiliary display's video focus` line between `R4-wake` and `R4-check`
- `d6` = `cnt R3-hu.txt R4-sleep R4-check "Decoder stopped: screen_off_sleep"` (the main decoder; report only)

**R4 verdict** (grade in this order):

- **UNTESTABLE:** either `d1` count is 0. The sleep or the wake never landed.
- **INCONCLUSIVE, surface lost:** `d3` is 1 or more. The overlay display destroyed the aux surface at
  screen-off, so the decision's premise does not hold on this display. Report `d2`, `d4` and `d5`.
- **PASS:** `d2` is 0, `d4` shows aux `windows_rendered_ge1` 1 or more with
  `first_aux_picture_after_R4-wake_s` 30 or less, and no `d5` timestamp is earlier than that first
  aux picture.
- **FAIL:** `d3` is 0 and any PASS condition is not met.

### Cleanup (always, last)

```bash
adb -s $HU shell settings delete global overlay_display_devices
adb -s $HU shell am force-stop $PKG
adb -s $HU push settings-backup-HU.xml /data/local/tmp/ssr-settings.xml   # restore as root, as hu_keys does
adb -s $HU shell cp /data/local/tmp/ssr-settings.xml /data/data/$PKG/shared_prefs/settings.xml
adb -s $HU shell chown $(adb -s $HU shell stat -c %u:%g /data/data/$PKG | tr -d '\r') /data/data/$PKG/shared_prefs/settings.xml
adb -s $HU shell chmod 660 /data/data/$PKG/shared_prefs/settings.xml
adb -s $HU shell cat /data/data/$PKG/shared_prefs/settings.xml | diff - settings-backup-HU.xml && echo RESTORED
adb -s $HU shell settings get global overlay_display_devices      # must print null
```

Restore D-POCO's radios with `ph_on` if a run ended with them off.

### Stop rule

- Each bring-up gets at most 2 attempts. R1 failing twice stops the round (rig broken).
- A preflight FAIL is re-run once; a second FAIL makes that run UNTESTABLE.
- R2C runs only on R2 FAIL-A or FAIL-C. R3 and R4 are skipped when R2 and R2C both read `b3` 0.
- The host gate: a run that cannot start below 75C in 30 min is UNTESTABLE (host thermal), and so is
  every run after it.

## 8. Do not re-run

- Native AA session forming between D-HU and D-POCO: settled in many rounds. R1 is a control here, not
  a test of that path.
- The aux lane's shed, ACK session, quit join and channel-carrying hole callback: covered by the 3115
  JVM tests (`VideoLaneTest`, `PeerCloseReasonTest`, `AuditRecoveryPolicyTest`). The rig cannot see
  them better.

## 9. Report back

1. **R2:** did the phone open `VIDEO_AUX` (`b3`), and did a picture reach the host: `idr`, `slices_per_s`,
   `profile_idc`, `width`x`height`. Add R2C if it ran.
2. **The cost to the main picture:** main `median_fps`, `windows_rendered_ge1` of `windows` and
   `dropped_sum` in R2 and R3 beside R1, and the phone's `critical_error` in each run.
3. **The viewers:** VIEWER-PC and VIEWER-SAM verdicts for R2 (and R2C if it ran), each with its
   seconds to first picture, and for D-SAM the route used (`relay` or `hu`).
4. **R3 and R4:** the two decision verdicts, with `c4`, `c8`, `d2`, `d3` and the seconds to the aux
   picture after each event.

```decisive-strings
AutomationReceiver: 
AutomationMarker: 
WifiLauncher: Initializing WiFi Mode: 
Projection backend: viewMode=
Media Sink Setup Request: 
VIDEO_AUX
HotspotManager:
SoftApCredentials:
SSL handshake complete
Throughput over 
Output Format Changed: 
[ServiceDiscovery] Announcing an auxiliary display on 
output is not available, so one display is announced
AapTransport: the auxiliary display's video lane is open
Granting video focus on 
after its setup
Media Start Request 
RX: Video Focus Request - mode: 
The phone released the second display's video. The main session continues.
Auxiliary Video Sink Stopped -> 
SecondScreen: the 
output is open
SecondScreen: network stream listening on port 
SecondScreen: network client connected from 
fell behind; resuming at the next keyframe
AapTransport: cycling the auxiliary display's video focus for a keyframe (
AapTransport: the 
thread is 
Error processing video message
AapProjectionActivity: the auxiliary display is up on 
AuxDisplayPresentation: surface ready on display 
AapProjectionActivity: the auxiliary display 
 is not attached
cannot carry a second screen
could not open the auxiliary display: 
DisplayTargets: projecting on display 
Decoder stopped: 
CommManager: doDisconnect (second screen)
the auxiliary display went away
the auxiliary display was dismissed
screen_off_sleep
AapService: ACTION_END_SESSION_STAY_ARMED received
WakeDetect: SCREEN_OFF
WakeDetect: SCREEN_ON (screen was off for 
```

Phone and platform strings, not in our tree, so not checkable against it:

```decisive-strings-external
GH.
Critical error
DisplayInfo{"Overlay #1", displayId 
mPrimaryDisplayDevice=Overlay #1
Wi-Fi is
Creating rfcomm socket for device
RELAY open 
RELAY close 
PNG count=
GET /probe.txt
org.videolan.vlc/.StartActivity
```
