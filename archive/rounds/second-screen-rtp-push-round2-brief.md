# second-screen-rtp-push, round 2 brief

Published name on the transfer branch: `second-screen-rtp-push-round2-brief.md`. Results file:
`second-screen-rtp-push-round2-results.md`. Evidence: the release `rig-evidence-second-screen-rtp-push`
exists from round 1, so upload to it. Asset `second-screen-rtp-push-round2-captures.zip`.

Units: **D-HU** (head unit), **D-POCO** (phone), and the **tester PC** as the RTP receiver. No D-SAM
fallback this round: round 1 proved the route from D-HU to the PC.

Estimated time: 15 min of pre-flight, then about 35 min of runs (O1 4 min, R4 10 min, R5 5 min,
O2 4 min, and N1 4 min only if section 7 asks for it). Total about 50 min.

## 1. Build and baseline

- **Candidate:** `feat/second-screen-outputs` on `fork` at
  **`8fc611a4901c09247b52c54c4c8e87ee862a71aa`**, tree `310fca3c60ee287c1b152bc7db4c44773928aa7c`.
  This is the same commit that round 1 tested. **History was not rewritten** since round 1.
- **The APK from round 1 is the candidate.** Its md5 is `2b24c2b3f2075713824a1511da06ad4c`. If D-HU
  still carries it, do not build (R0).

```bash
git ls-remote fork feat/second-screen-outputs   # MUST print 8fc611a4901c09247b52c54c4c8e87ee862a71aa
```

  If `ls-remote` prints another SHA or nothing, stop and report. The round has no candidate.
- **No baseline build.** O1 and O2 are the feature-off controls on the candidate (template section 8).

## 2. What this is and why

The branch adds a fifth second-screen output, `RTP_PUSH`. It sends the second Android Auto display
as RTP H.264 over UDP to a typed IPv4 address and port. A user's instrument cluster has its own IP
address and listens for RTP on a UDP port. It connects to nothing, so the TCP `NETWORK` output
cannot reach it. Round 1 brief section 2 describes the candidate in full. It is unchanged.

Round 1 passed in substance. A real receiver took 4250 datagrams with no loss, FU-A intact, SPS
and PPS in front of every IDR, and 1992 frames decoded at 1280x720. Three questions stayed open,
and this round answers them:

1. **Does the SDP, as written, open in FFmpeg and give a picture?** In round 1, FFmpeg joined about
   60 s into the stream. The phone sent no IDR while FFmpeg listened, so FFmpeg saw only P slices.
   The grade could not say PASS or FAIL. This round starts FFmpeg **before** the session, so the
   stream's first IDR reaches it. (R4, D1)
2. **How long does a late receiver wait?** Round 1 measured IDRs at about 22:51:26 and 22:52:29,
   then none for 108 s or more. The candidate asks for one keyframe at most, at open, and usually
   none. This round measures the phone's IDR gaps over two sessions. It is a number for the
   follow-up decision, not a PASS condition. (R4, R5, D3)
3. **Does the push cost the main picture?** Round 1's R2 read a main median of 24 fps against 29
   in R1. But R2 ran 39 windows and R1 ran 9, and an idle map lowers its own frame rate over time.
   This round compares the first 30 windows after the picture starts, in four sessions: off, push,
   push, off. (O1, R4, R5, O2, C-FPS)

It also runs the failure path that round 1 could not reach. The socket is not connected, so an
ICMP error from the PC never reaches it, and no send fails. Round 1's C10 and R3A were INCONCLUSIVE
for that reason. This round makes a send fail on purpose: an `iptables` rule on D-HU drops the
push's datagrams for 30 s, then goes away. The candidate must log the failure once, keep the
session, and send again when the rule goes. (R4, D5 and D6)

## 3. What is different about this round

- **Native AA, as in round 1.** D-HU cannot host USB (`rig-quirks/topics/tooling.md`). D-HU's
  station stayed on the house WiFi in every round 1 session (`network=wlan0`). R4 and R5 read
  `wlan0` again inside the session.
- **The scripts folder.** Round 1 found that `hur-wifi-test-scripts/` no longer exists and used
  `rig-toolkit/` and `rig-data/rounds/second-screen-rtp-push-round1/scripts/`. Do the same.
- **The PC firewall rule from round 1 is still in place**, by round 1's notes. This round needs it:
  FFmpeg and the counter listen on UDP 5004. P5 checks it. Leave it in place to the end, and ask
  the operator to remove it at Cleanup.
- **FFmpeg is installed on the PC** (7.1.5, round 1). P4 checks it.
- **D-HU's adb shell is root**, as `hu_keys` already uses. The new `iptables` rule and the read of
  `/proc/net/snmp` need it. The rule matches only UDP to the PC's address on port 5004. Android
  Auto runs on the P2P group, so the rule does not touch the session. Cleanup removes the rule
  whatever happens, and R5 and O2 check that it is gone before they start.
- **A dropped send fails with an error, by the kernel's design.** A packet that the `OUTPUT` chain
  drops makes `sendto` return `EPERM`. Android turns that into a `SocketException`. So one
  `RTP push send failed (SocketException): ...` line is the expected result in R4 phase 3. Grade the
  count of lines and classes, not the class name.
- **The closed port is the plan's "Part B".** With the firewall rule in place and no listener, the
  PC answers each datagram with ICMP port unreachable. R4 phase 2 counts those ICMP messages on D-HU
  from `/proc/net/snmp`. The design says the socket ignores them. So D4 passes with **no** failure
  line, when the ICMP count proves they arrived.
- **The keyframe request stays unexercised.** It runs only when the first unit carries neither an
  IDR nor SPS and PPS. The phone opens with SPS and PPS, then an IDR. No rig lever changes that.
  `RtpStreamOutputTest` covers it. `e_kf_req` is reported and must stay 0 or 1.
- **The main map is idle.** Nobody touches the phone. The fps comparison uses the first 30 windows
  after `<RUN>-up` in every arm, so all arms cover the same part of the session.
- **The phone's own wireless start can log `Critical error` before the session.** Round 1's R2 had
  two such lines 25 s before SSL. This round grades the phone window `<RUN>-up..<RUN>-end`, and
  reports `<RUN>-launch..<RUN>-up` separately, quote only.
- **The rig's audio settings are a deliberate worst case.** Do not change `use-aac-audio`,
  `audio-latency-multiplier`, `audio-queue-capacity` or `enable-audio-sink`. Read them back and record
  them. Round 1 read the multiplier as 8.
- **Not covered, again:** the settings rows for the address and the port, the toast for a refused
  address, and the write-only-when-changed rule on those rows. Entry needs typing, which the rig
  does not do. Say this in Setup notes. It is a known gap, not a finding.
- **Expected INCONCLUSIVE, said now:**
  - D4, if P8 reads `ICMP_PROBE=none` (the PC sends no ICMP to D-HU).
  - D5, if P8 reads `IPT=failed`, or if the rule drops the datagrams with no error line.
  - R4 and R5, if the station has no `wlan0` address inside the session.

## 4. Settings keys

All runs are on D-HU, written as root with the app stopped, by `hu_keys` (round 1's `ssr_lib.sh`).
`hu_keys` reads every key back and prints `KEYS_OK` or `KEYS_MISMATCH <keys>`. `POCO_MAC` comes
from P3 and `HOSTIP` from P5.

**Every run (`BASE`), the same as round 1:**

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

**Per run:**

| Key | Type | O1, O2 | R4, R5 | N1 (only if asked) |
|---|---|---|---|---|
| `aux-display-enabled` | boolean | delete (default `false`) | `true` | `true` |
| `aux-output` | string | `RTP_PUSH` | `RTP_PUSH` | `NETWORK` |
| `aux-rtp-destination` | string | `HOSTIP` | `HOSTIP` | delete |
| `aux-rtp-port` | int | `5004` | `5004` | `5004` |
| `aux-network-size` | int | `1` (1280x720) | `1` | `1` |
| `aux-network-port` | int | delete | delete | delete |
| `aux-display-role` | string | delete (`AUXILIARY`) | delete | delete |
| `aux-display-content` | int | delete | delete | delete |
| `aux-display-id` | int | delete | delete | delete |
| `preferred-display-mode` | int | delete | delete | delete |
| `preferred-display-id` | int | delete | delete | delete |

O1 and O2 store a full RTP setup with the switch off, as round 1's R1 did. N1 announces the same
1280x720 aux display through the `NETWORK` output with no viewer. It separates the cost of a second
display on the phone from the cost of the UDP sends.

`ACTION_LOG_MARKER` is not in `AutomationCommandPolicy.CONFIGURING` on the candidate, so
`allow-external-configuration` is not needed.

```bash
BASE="int:wifi-connection-mode=3 set:native-poke-bt-macs=$POCO_MAC bool:native-poke-all-paired=false int:native-driver-selection-mode=0 int:onboarding-version=2 int:log-level=2 str:video-codec=H.264 int:resolutionId=3 int:view-mode=1 bool:force-software-decoding=false del:software-video-decoder del:debug-video-fault-injection del:video-profile-starvation-cap int:night-mode=1"
AUXREST="int:aux-rtp-port=5004 int:aux-network-size=1 del:aux-network-port del:aux-display-role del:aux-display-content del:aux-display-id del:preferred-display-mode del:preferred-display-id"
OKEYS="del:aux-display-enabled str:aux-output=RTP_PUSH str:aux-rtp-destination=$HOSTIP $AUXREST"
PKEYS="bool:aux-display-enabled=true str:aux-output=RTP_PUSH str:aux-rtp-destination=$HOSTIP $AUXREST"
NKEYS="bool:aux-display-enabled=true str:aux-output=NETWORK del:aux-rtp-destination $AUXREST"
```

Define these after P3 and P5 have set `POCO_MAC` and `HOSTIP`. Echo all five and check that no value
is empty before O1.

## 5. Every action as a verb, and the helpers

| Step | Command |
|---|---|
| marker, both units | `mark <label>` (in `ssr_lib.sh`: `send ACTION_LOG_MARKER --es text <label>`, then `log -t AutomationMarker <label>` on D-POCO) |
| identity | `send ACTION_QUERY_STATE` |
| raise the projection, fallback only | `send ACTION_RAISE_PROJECTION` (inside `bringup`; it prints `RAISE_VERB_USED`) |
| exit at run end | `send ACTION_EXIT`, then `am force-stop` (inside `run_end`) |

**No verb exists for these, and none is needed on the app:**

- Launch: `adb shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity` (inside `bringup`).
- The `iptables` rule and the `/proc/net/snmp` read act on D-HU's kernel, not on our app.
- The receiver, FFmpeg and the UDP probe run on the PC or in D-HU's shell.

**There is no tap and no hand step during the runs.** The only human steps are the P6 ask and the
firewall removal at Cleanup.

### Scripts

Make the folder `rig-toolkit/rig-data/rounds/second-screen-rtp-push-round2/scripts/` and `cd` into
it for the whole round. List its files and their md5 in Setup notes. `python3 -I` runs every
Python file.

**Nine files come from round 1's folder.** Copy them, and compare the md5s with round 1's Setup
notes:

```bash
S1=../../second-screen-rtp-push-round1/scripts      # adjust if that folder is elsewhere
cp $S1/ssr_lib.sh $S1/ssr_keys.py $S1/tput.py $S1/nal_count.py $S1/pngdims.py $S1/rtp_recv.py $S1/rtp_lib.sh .
cp $S1/ssr_build.sh . 2>/dev/null
md5sum ssr_lib.sh ssr_keys.py tput.py nal_count.py pngdims.py rtp_recv.py rtp_lib.sh
# round 1 read: ssr_lib.sh 37fa464e, ssr_keys.py 3e9ffa2e, tput.py 4df665f4, nal_count.py e199ca79,
#               pngdims.py d11bf834, rtp_recv.py 7b6df231, rtp_lib.sh 9c1530fc
```

If a prefix differs, record it and use the round 1 file anyway. If the folder is missing, take
`ssr_lib.sh` from section 5 of `second-screen-outputs-round2-brief.md`, the four other `ssr` files
from section 5 of `second-screen-outputs-round1-brief.md`, and `rtp_recv.py` and `rtp_lib.sh` from
section 5 of `second-screen-rtp-push-round1-brief.md`. Record which source you used.

**Do not pipe `recv_stop` into `tee`.** Round 1 found that the pipe runs it in a subshell, which
cannot `wait` for the receiver. Write `recv_stop <RUN> > file; cat file`, as below.

**Four files are new.** Save them as below.

**`udp_probe.sh`** (pushed to D-HU, run there). It replaces round 1's, which hung on toybox `nc`:

```sh
# udp_probe.sh <ip> <port> : five UDP datagrams from D-HU by toybox nc, each bounded at 3 s
i=0; while [ $i -lt 5 ]; do echo "ohu-udp-probe-$i" | timeout 3 toybox nc -u -w 1 "$1" "$2" 2>&1 | head -2; i=$((i+1)); done
echo PROBE_SENT
```

**`fps30.py`** (the first 30 main windows after a marker). `RTP_PUSH` and `NETWORK` build no aux
decoder, so every `Throughput over` line in these runs is the main decoder:

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

**`idr_gaps.py`** (the IDR arrival times in one receiver CSV):

```python
# idr_gaps.py <csv> : arrival times of the IDR access units in one rtp_recv.py CSV, and the gaps between them
import csv, sys
rows = list(csv.DictReader(open(sys.argv[1])))
if not rows: print("IDR_TIMES n=0 rows=0"); sys.exit(0)
t0 = float(rows[0]["t"]); seen = set(); t = []
for r in rows:
    nal = int(r["nal"])
    if nal == 5 or (nal == 28 and r["fu_s"] == "1" and r["fu_type"] == "5"):
        if r["ts"] not in seen:
            seen.add(r["ts"]); t.append(float(r["t"]) - t0)
span = float(rows[-1]["t"]) - t0
gaps = [b - a for a, b in zip(t, t[1:])]
print("IDR_TIMES n=%d span_s=%.1f times=%s gaps=%s max_gap_s=%s tail_s=%s" % (
    len(t), span, ",".join("%.1f" % x for x in t) or "none", ",".join("%.1f" % g for g in gaps) or "none",
    "%.1f" % max(gaps) if gaps else "none", "%.1f" % (span - t[-1]) if t else "none"))
```

`times` are seconds from the first datagram. `tail_s` is the time from the last IDR to the last
datagram, so the next gap is at least that long.

**`rtp2_lib.sh`** (source it after `ssr_lib.sh` and `rtp_lib.sh`: `source ./rtp2_lib.sh`):

```bash
# rtp2_lib.sh : round 2 of second-screen-rtp-push. Source it after ssr_lib.sh and rtp_lib.sh.
# ff_start <RUN> <timeout_s> : FFmpeg on stream.sdp as written, in the background; <RUN>.ffexit holds its rc and end time
ff_start() { rm -f "$OUT/$1.ffexit" "$OUT/$1-first.png"; FF_T0=$(date +%s.%N)
  ( timeout "$2" ffmpeg -nostdin -hide_banner -loglevel info -protocol_whitelist file,udp,rtp -i "$D/stream.sdp" -frames:v 1 -y "$OUT/$1-first.png" > "$OUT/$1-ffmpeg.log" 2>&1
    echo "rc=$? t=$(date +%s.%N)" > "$OUT/$1.ffexit" ) & FFPID=$!; sleep 1; }
ff_kill() { pkill -f 'ffmpeg -nostdin'; [ -n "$FFPID" ] && wait $FFPID 2>/dev/null; FFPID=; true; }
# ff_wait <RUN> : waits for FFmpeg to end, then prints FFMPEG_EARLY, the PNG line and FFmpeg's error lines
ff_wait() { [ -n "$FFPID" ] && wait $FFPID 2>/dev/null; FFPID=
  local rc t; rc=$(sed -n 's/^rc=\([0-9]*\) .*/\1/p' "$OUT/$1.ffexit"); t=$(sed -n 's/.* t=//p' "$OUT/$1.ffexit")
  echo "FFMPEG_EARLY rc=$rc run_s=$(python3 -I -c "print(round($t-$FF_T0,1))") exit_after_up_s=$(python3 -I -c "print(round($t-$T_UP,1))")"
  [ -s "$OUT/$1-first.png" ] && python3 -I pngdims.py "$FF_T0" "$OUT/$1-first.png"
  grep -a -i -E 'error|non-existing|Stream #0' "$OUT/$1-ffmpeg.log" | head -8 | cut -c1-200; true; }
# icmp_du : D-HU's count of ICMP destination-unreachable messages received
icmp_du() { adb -s $HU shell cat /proc/net/snmp | tr -d '\r' | awk '$1=="Icmp:"{ if (!h) {for(i=1;i<=NF;i++) k[i]=$i; h=1} else {for(i=1;i<=NF;i++) if (k[i]=="InDestUnreachs") print $i} }'; sleep 0.3; }
# ipt_on / ipt_off : a D-HU rule that drops UDP to the PC's port 5004, and its removal
ipt_spec() { echo "-d $HOSTIP/32 -p udp --dport $RPORT -j DROP"; }
ipt_on() { adb -s $HU shell iptables -w -I OUTPUT 1 $(ipt_spec); sleep 0.3
  if adb -s $HU shell iptables -w -C OUTPUT $(ipt_spec) >/dev/null 2>&1; then echo IPT_ON; else echo IPT_ON_FAILED; fi; sleep 0.3; }
ipt_off() { local n=0
  while adb -s $HU shell iptables -w -C OUTPUT $(ipt_spec) >/dev/null 2>&1 && [ $n -lt 5 ]; do
    sleep 0.3; adb -s $HU shell iptables -w -D OUTPUT $(ipt_spec); n=$((n+1)); sleep 0.3; done
  if adb -s $HU shell iptables -w -C OUTPUT $(ipt_spec) >/dev/null 2>&1; then echo IPT_STILL_ON; else echo IPT_OFF; fi; sleep 0.3; }
# kf_times <RUN> <from> <to> : the times of the 10 s reports that carried one keyframe or more
kf_times() { echo "KF_REPORTS $(seg "$OUT/$1-hu.txt" "$2" "$3" | grep -aE 'RTP push sent [0-9]+ packets, [0-9]+ bytes, [1-9][0-9]* keyframes in' | awk '{print $2}' | tr '\n' ' ')"; }
```

`fps30.py` and `idr_gaps.py` were run before publication on a synthetic capture and CSV. The marker
match is exact, so `R4-up` does not match `R4-up2`. They have not seen a real capture. If a field
comes back empty rather than a number, the pattern is wrong: fix it, say so in Setup notes, and keep
going. The same applies to `rtp_counts` from round 1, which worked on round 1's captures.

## 6. The deciding lines

Every head unit string below was checked with `grep -F -r` in the candidate's `app/src/main` at
`8fc611a4`. All are INFO, WARN or ERROR, so `log-level` 2 carries them. `...` marks a value the app
fills in. Files: `$OUT/<RUN>-hu.txt` (head unit) and `$OUT/<RUN>-phone.txt` (phone). Windows are
marker pairs, `seg <file> <from> <to>`.

| Line (head unit) | Means |
|---|---|
| `AutomationReceiver: ` | a verb landed. A step with no such line is void, not a FAIL |
| `WifiLauncher: Initializing WiFi Mode: NATIVE` | preflight: Native path |
| `Projection backend: viewMode=TEXTURE ` | preflight: view backend |
| `Media Sink Setup Request: 3 on channel VIDEO` at line end | preflight: the main sink, H.264 |
| `HotspotManager:`, `SoftApCredentials:`, less `Setting hotspot enabled=false` | preflight: must be 0 |
| `SSL handshake complete` | a session formed. One per session at INFO |
| `AapService destroying` | the service went down. Must be 0 inside a run |
| `Throughput over ...ms: rendered=N (Xfps), fed=N (Yfps), dropped=N` | the main decoder's frames (`fps30.py`, `tput.py`) |
| `[ServiceDiscovery] Announcing an auxiliary display on VIDEO_AUX: RTP_PUSH 1280x720 as _1280x720, ..., input on INPUT_AUX` | the aux display was announced for the push (`NETWORK` in N1) |
| `SecondScreen: the RTP_PUSH output is not available, so one display is announced` | no valid address. Must be 0 in R4 and R5 |
| `AapTransport: the auxiliary display's video lane is open` | the phone opened channel 14 |
| `SecondScreen: RTP push to <ip>:5004 open (network=<name>)` | the socket opened; `<name>` is the network it bound to |
| `SecondScreen: the RTP_PUSH output is open` | the hub took the output |
| `SecondScreen: could not start the RTP_PUSH output: ...` | the socket open threw |
| `SecondScreen: RTP push sent <n> packets, <bytes> bytes, <k> keyframes in <ms>ms, <j> keyframes carried their own SPS and PPS` | the 10 s report. `<n>` counts each datagram before it is sent, so a refused send still counts |
| `SecondScreen: RTP push send failed (<Class>): ...; later failures of this kind are not logged` | a send threw, logged once per class |
| `SecondScreen: RTP push stopped` | the socket closed at session end |
| `AapTransport: cycling the auxiliary display's video focus for a keyframe (the second screen asked for one)` | the push asked for its one keyframe |
| `AapTransport: no auxiliary keyframe cycle before its setup ()` | an early request was refused (quote only) |
| `Error processing video message` | a lane threw |
| `AapTransport: the VIDEO_AUX thread is ... messages behind, shedding` | the aux lane shed its backlog |
| `Decoder stopped: CommManager: doDisconnect (second screen)` | an aux decoder stopped. Must be 0 |

**Phone strings** (`$OUT/<RUN>-phone.txt`, by `phone_grades`). They are Android Auto's, not ours,
and they drift between builds. Report the Gearhead version (round 1: 18.0.164204-release.daily).

| Phone line | Graded | Means |
|---|---|---|
| `GH.` | capture gate, 1 or more | the phone capture works |
| `AutomationMarker: ` | window gate, 1 or more | the markers reached the phone log |
| `Critical error` | 0 in `<RUN>-up..<RUN>-end` | Android Auto ended the session over something we sent |
| `No input for display` | 0 | the aux display has no input |
| `Video focus gained before configurations received` | 0 | a focus gain before the aux Config |

The exit teardown makes the phone log `Critical error` lines after `<RUN>-end`. The window stops at
`<RUN>-end`, so they do not count. Report `critical_error_file` beside the windowed count.

**D-HU kernel and PC strings** (not in our tree): `IPT_ON`, `IPT_ON_FAILED`, `IPT_OFF`,
`IPT_STILL_ON`, `ICMP_DU `, `RTP_RECV `, `RTP_GAPS `, `PROBE datagrams=`, `PROBE_SENT`,
`FFMPEG_EARLY `, `PNG count=`, `FPS30 `, `IDR_TIMES `, `KF_REPORTS `.

## 7. Runs

### Pre-flight (one batch, before R0)

**P1. Units.** `adb devices -l` must list `27870808938846` (D-HU) and `4f4027e9` (D-POCO).

```bash
for s in ZY22GC3BM4; do adb devices | grep -q "^$s" && { adb -s $s shell svc bluetooth disable; sleep 0.3; adb -s $s shell svc wifi disable; }; done
for s in 30041c35642d2200 0123456789ABCDEF CNU350BGBJ; do adb devices | grep -q "^$s" && adb -s $s shell am force-stop com.andrerinas.headunitrevived; done
```

Write in Setup notes which units were on adb.

**P2. D-HU state.**

```bash
source ./ssr_lib.sh; source ./rtp_lib.sh; source ./rtp2_lib.sh
adb -s $HU shell stat -c '%U:%G' /data/data/$PKG/shared_prefs                    # record it
adb -s $HU shell cat /data/data/$PKG/shared_prefs/settings.xml > settings-backup-HU.xml; md5sum settings-backup-HU.xml
grep -o -E '(use-aac-audio|audio-latency-multiplier|audio-queue-capacity|enable-audio-sink)[^/]*' settings-backup-HU.xml
grep -o -E '(video-profile-starvation-cap|keep-dummy-vpn-during-session)[^/]*' settings-backup-HU.xml
grep -o -E 'aux-[a-z-]*|preferred-display-[a-z]*' settings-backup-HU.xml
HU_MAC=$(adb -s $HU shell settings get secure bluetooth_address | tr -d '\r'); echo "HU_MAC=$HU_MAC"
HUIP_P=$(hu_ip); echo "HUIP_P=$HUIP_P"; HUIP=${HUIP_P%/*}; HU_PREFIX=${HUIP_P#*/}   # MUST be an address, for example 192.168.1.9/24
adb -s $HU shell iptables -w -S OUTPUT | head -5                                       # record; no rule may name port 5004
```

If `HUIP_P` is empty, D-HU's station is not on the house WiFi. Put "join D-HU to the house WiFi" in P6.
If the `iptables` list already holds a rule with `--dport 5004`, run `ipt_off` after P5 sets
`HOSTIP`, and record it.

**P3. D-POCO state.**

```bash
adb -s $PH shell dumpsys package com.google.android.projection.gearhead | grep -a -m2 -E "versionName|versionCode"
POCO_MAC=$(adb -s $PH shell settings get secure bluetooth_address | tr -d '\r' | tr a-f A-F); echo "POCO_MAC=$POCO_MAC"
adb -s $PH shell dumpsys window | grep -a mCurrentFocus                        # must be the launcher
adb -s $PH shell input keyevent KEYCODE_HOME
```

`POCO_MAC` must be a real address, not empty and not `02:00:00:00:00:00`. Round 1 used the same
source.

**P4. PC tools.**

```bash
python3 --version
command -v ffmpeg ffprobe; ffmpeg -version 2>/dev/null | head -1     # round 1: 7.1.5
```

If `ffmpeg` is absent, put its install in P6.

**P5. The route from D-HU to the PC.**

```bash
HOSTIP=$(ip -4 route get "$HUIP" | grep -o 'src [0-9.]*' | cut -d' ' -f2); echo "HOSTIP=$HOSTIP"
SAME_SUBNET=$( [ "$HU_PREFIX" = 24 ] && [ "${HOSTIP%.*}" = "${HUIP%.*}" ] && echo yes || echo no ); echo "SAME_SUBNET=$SAME_SUBNET"
adb -s $HU shell ping -c 3 -W 2 $HOSTIP | tail -2; sleep 0.3
adb -s $HU push udp_probe.sh /data/local/tmp/udp_probe.sh >/dev/null; sleep 0.3
python3 -I rtp_recv.py --probe $RPORT 25 > "$OUT/P5-probe.txt" & PRB=$!; sleep 2
adb -s $HU shell sh /data/local/tmp/udp_probe.sh $HOSTIP $RPORT | tee "$OUT/P5-hu.txt"
wait $PRB; cat "$OUT/P5-probe.txt"
```

- `PROBE datagrams=` 1 or more and `from=` holds `$HUIP`: `UDP_PROBE=ok`.
- `datagrams=0` and `PROBE_SENT` printed: `UDP_PROBE=blocked`. Put the firewall rule in P6, then
  run this block again.

Write the SDP exactly as the candidate builds it, with no trailing newline:

```bash
printf 'v=0\no=- 0 0 IN IP4 %s\ns=Open Headunit second screen\nc=IN IP4 %s\nt=0 0\nm=video 5004 RTP/AVP 96\na=rtpmap:96 H264/90000\na=fmtp:96 packetization-mode=1' "$HOSTIP" "$HOSTIP" > stream.sdp
cat stream.sdp; echo; wc -l stream.sdp          # 7 newlines, 8 lines
```

Now define `BASE`, `AUXREST`, `OKEYS`, `PKEYS` and `NKEYS` from section 4.

**P8. The two levers.** Run after P5 has `UDP_PROBE=ok`. Nothing listens on the PC's port 5004 here.

```bash
adb -s $HU shell false; echo "ADB_EXIT=$?"                 # MUST print 1: the ipt helpers need adb's exit status
I0=$(icmp_du); echo "ICMP_DU before=$I0"                   # MUST be a number
adb -s $HU shell sh /data/local/tmp/udp_probe.sh $HOSTIP $RPORT > /dev/null; sleep 2
I1=$(icmp_du); echo "ICMP_DU after=$I1 delta=$((I1-I0))"
ipt_on; ipt_off                                            # MUST print IPT_ON, then IPT_OFF
```

- `ADB_EXIT` is not 1, or `IPT_ON_FAILED`, or `IPT_STILL_ON`: set `IPT=failed`. Run `ipt_off` until
  it prints `IPT_OFF`. If it never does, stop and report: a rule is left on D-HU. With `IPT=failed`,
  R4 skips `ipt_on` and D5 is INCONCLUSIVE.
- Otherwise `IPT=ok`.
- `ICMP_DU` not a number: set `ICMP_PROBE=unread`. D4 is then INCONCLUSIVE.
- `delta` 1 or more: `ICMP_PROBE=ok`. `delta` 0: `ICMP_PROBE=none`. Record both values.

**P6. The one ask, only if needed.** Ask the operator in one message, and only for these:

- any rig unit that is powered on but not on adb (P1): switch its Bluetooth off or power it off;
- if P2 found no `wlan0` address on D-HU: join D-HU to the house WiFi;
- if P4 found no `ffmpeg`: may the executor run `sudo apt-get install -y ffmpeg` on the PC?
- if P5 read `UDP_PROBE=blocked`: may the executor open UDP 5004 and 5005 on the PC's firewall for
  D-HU's subnet? On `ufw` that is
  `sudo ufw allow proto udp from ${HUIP%.*}.0/24 to any port 5004:5005`. Record the exact rule.

If none of these applies, skip P6. After the answers, run P4, P5 and P8 again.

### R0. Identity

```bash
adb -s $HU pull $(adb -s $HU shell pm path $PKG | cut -d: -f2 | tr -d '\r') "$OUT/live.apk"; md5sum "$OUT/live.apk"
send ACTION_QUERY_STATE
unzip -p "$OUT/live.apk" 'classes*.dex' | strings | grep -c 'RtpStreamOutput'      # 1 or more
```

**PASS without a build:** the md5 is `2b24c2b3f2075713824a1511da06ad4c`, the `data=` reply carries
commit `8fc611a4901c` (a `-dirty` suffix from `.WORKTREE-INFO` alone is fine), and the DEX count is 1
or more.

**Otherwise build.** Use round 1's worktree if it is still at `8fc611a4` (`git -C <worktree> rev-parse
HEAD`), or make a new one at that SHA with `wt-new.sh`, and check the tree is
`310fca3c60ee287c1b152bc7db4c44773928aa7c`. Build with `--max-workers=2` after `th_wait 70`, with
`ssr_build.sh`. Copy the APK out of `apks/` at once. Then:

```bash
adb -s $HU install -r <apk>
adb -s $HU pull $(adb -s $HU shell pm path $PKG | cut -d: -f2 | tr -d '\r') "$OUT/live.apk"; md5sum "$OUT/live.apk" <apk>
send ACTION_QUERY_STATE
```

**PASS after a build:** the two md5s agree, and the `data=` reply carries commit `8fc611a4901c`.
**A build failure stops the round.** `hu_keys` writes and checks every key on every run, so an
install that wipes `settings.xml` does no harm.

### O1. Feature off, the first fps control (4 min)

```bash
RUN=O1; recv_start O1
bringup O1 $OKEYS || { run_end; keep_attempt O1; recv_stop O1 >/dev/null; recv_keep O1; recv_start O1; bringup O1 $OKEYS; }
preflight                                                   # a FAIL: fix the keys, re-run O1 once
mark O1-hold; sleep 160; mark O1-check
run_end
recv_stop O1 > "$OUT/O1.rtp"; cat "$OUT/O1.rtp"
python3 -I fps30.py "$CAP" O1-up | tee "$OUT/O1.fps30"
rtp_counts O1 O1-launch O1-end | tee "$OUT/O1.counts"
phone_grades O1 O1-up O1-end | tee "$OUT/O1.phone"
phone_grades O1 O1-launch O1-up | tee "$OUT/O1.phone-bringup"
```

**PASS:** all of these.

- `e_secondscreen` 0, `e_vidaux` 0, `e_announce` 0 (`O1.counts`).
- `RTP_RECV packets=0` (`O1.rtp`).
- `e_ssl` 1, `e_destroy` 0.
- `FPS30` `windows` 30, `rendered_ge1` 27 or more.
- Phone (`O1.phone`): `win|Critical error=0`, `phone_markers_in_window` 1 or more.

**FAIL:** any of those is not met. Two failed bring-ups stop the round (rig broken). If both fail at
`SESSION_FAIL_SSL`, compare the MAC in the phone's last `Creating rfcomm socket for device` line with
`HU_MAC` (`rig-quirks/units/D-POCO.md`).

### R4. Early receiver, closed port, refused sends: THE POINT OF THE ROUND (10 min)

Four phases inside one session, after FFmpeg's early start:

- **Phase 0 (from before the launch):** FFmpeg owns UDP 5004 with `stream.sdp` as written. It
  decodes the first frame it can and exits.
- **Phase 1 (180 s):** `rtp_recv.py` owns UDP 5004 and records the stream and its IDRs.
- **Phase 2 (40 s):** nothing listens on 5004. The PC answers with ICMP port unreachable.
- **Phase 3 (30 s):** the `iptables` rule drops every push datagram on D-HU. A counter listens.
- **Phase 4 (30 s):** the rule is gone. A new counter listens.

```bash
RUN=R4; th_wait 75; ff_start R4 360
bringup R4 $PKEYS; rc=$?
if [ $rc = 1 ]; then run_end; keep_attempt R4; ff_kill; mv "$OUT/R4-ffmpeg.log" "$OUT/R4-a1-ffmpeg.log"; th_wait 75; ff_start R4 360; bringup R4 $PKEYS; rc=$?; fi
T_UP=$(date +%s.%N); echo "R4_BRINGUP rc=$rc"     # 2 = the phone logged Critical error with no frame: do not retry, grade at once
preflight
HUIP_S=$(hu_ip); echo "HUIP_SESSION=$HUIP_S" | tee "$OUT/R4.huip"
ff_wait R4 | tee "$OUT/R4.ffmpeg"                 # returns at once if FFmpeg already ended
# phase 1: the counter
recv_start R4; I0=$(icmp_du); mark R4-p1; sleep 180; mark R4-p1-end; I1=$(icmp_du)
recv_stop R4 > "$OUT/R4.rtp"; cat "$OUT/R4.rtp"
# phase 2: nothing listens
mark R4-p2; sleep 40; mark R4-p2-end; I2=$(icmp_du)
echo "ICMP_DU p1_delta=$((I1-I0)) p2_delta=$((I2-I1))" | tee "$OUT/R4.icmp"
# phase 3: the rule drops the sends (skip ipt_on when IPT=failed)
[ "$IPT" = ok ] && ipt_on | tee "$OUT/R4.ipt"
recv_start R4b; mark R4-p3; sleep 30; mark R4-p3-end
recv_stop R4b > "$OUT/R4b.rtp"; cat "$OUT/R4b.rtp"
# phase 4: the rule is gone
ipt_off | tee -a "$OUT/R4.ipt"
recv_start R4c; mark R4-p4; sleep 30; mark R4-p4-end
recv_stop R4c > "$OUT/R4c.rtp"; cat "$OUT/R4c.rtp"
python3 -I tput.py "$CAP" R4-up R4-p4-end > "$OUT/R4.tput"; cat "$OUT/R4.tput"
run_end
python3 -I fps30.py "$CAP" R4-up | tee "$OUT/R4.fps30"
python3 -I idr_gaps.py "$OUT/R4-rtp.csv" | tee "$OUT/R4.idr"
for w in "R4-launch R4-p1" "R4-p1 R4-p1-end" "R4-p2 R4-p2-end" "R4-p3 R4-p3-end" "R4-p4 R4-p4-end" "R4-launch R4-end" "R4-end R4-closed"; do rtp_counts R4 $w; echo; done > "$OUT/R4.counts"; cat "$OUT/R4.counts"
kf_times R4 R4-launch R4-end | tee "$OUT/R4.kf"
phone_grades R4 R4-up R4-end | tee "$OUT/R4.phone"
phone_grades R4 R4-launch R4-up | tee "$OUT/R4.phone-bringup"
```

FFmpeg and the counters talk to D-HU over the WiFi, not over adb, so house rule 8 does not apply to
them. The `icmp_du` and `ipt_*` calls are adb calls, and the script runs them in sequence with the
markers.

**Conditions.** Windows: `P0` = `R4-launch..R4-p1`, `P1` = `R4-p1..R4-p1-end`, `P2` =
`R4-p2..R4-p2-end`, `P3` = `R4-p3..R4-p3-end`, `P4` = `R4-p4..R4-p4-end`, `ALL` =
`R4-launch..R4-end`, `CLOSE` = `R4-end..R4-closed`.

- **D1, the SDP opens as written.** `R4.ffmpeg`: `FFMPEG_EARLY rc=0`, and `PNG count=1` with
  `width=1280 height=720`. Report `run_s` and `exit_after_up_s` (negative means the picture came
  before `R4-up`). **FAIL** when `rc` is not 0 and `P0` `e_keyframes` is 1 or more: a keyframe went
  out while FFmpeg listened. **INCONCLUSIVE** when `rc` is not 0 and `P0` `e_keyframes` is 0. Quote
  FFmpeg's error lines in both cases.
- **D2, the socket and the stream.** `ALL`: `e_announce` 1, and the quoted line names
  `RTP_PUSH 1280x720` and ends `input on INPUT_AUX`. `e_open` 1, `e_outopen` 1, `e_nostart` 0,
  `e_unavail` 0. With `SAME_SUBNET=yes`, `e_network` is `wlan0`. `R4.rtp`: `packets` 500 or more,
  `loss_pct` below 1.000, `srcs` holds only `$HUIP`, `marker_bad` 0, `fu_bad` 0,
  `idr_intact_without_ps` 0.
- **D3, IDR cadence (report only, no verdict).** `R4.idr`: `n`, `times`, `gaps`, `max_gap_s` and
  `tail_s`. `R4.kf`: the `KF_REPORTS` times. These are the numbers a late receiver waits for.
- **D4, the closed port (the plan's Part B).** `P2`: `e_fail_lines` 0, `e_sent_lines` 3 or more,
  `e_packets` 300 or more. `R4.icmp`: report `p1_delta` and `p2_delta`. **PASS** when the counts are
  met and `p2_delta` is 10 or more: ICMP reached D-HU and the socket raised nothing, as designed.
  **INCONCLUSIVE** when the counts are met and `p2_delta` is below 10, or `ICMP_PROBE` is not `ok`.
  **FAIL** when a count is not met.
- **D5, a refused send (AC 9).** `R4.ipt` holds `IPT_ON`. `P3`: `e_fail_lines` exactly 1,
  `e_sent_lines` 2 or more, `e_errors` 0, `e_shed` 0. `R4b.rtp`: `packets` 5 or fewer. Report the
  class and the first line. **INCONCLUSIVE** when `IPT=failed`, or `R4.ipt` holds `IPT_ON_FAILED`,
  or `R4b.rtp` `packets` is above 5 (the rule did not hold), or `P3` `e_fail_lines` is 0 with
  `R4b.rtp` `packets` 5 or fewer (the kernel dropped them with no error). **FAIL** when `P3`
  `e_fail_lines` is 2 or more, or `ALL` shows one class more than once.
- **D6, sends resume.** `R4.ipt` holds `IPT_OFF` after the phase 3 line. `R4c.rtp`: `packets` 300
  or more, `srcs` holds only `$HUIP`, `marker_bad` 0, `fu_bad` 0. `P4`: `e_sent_lines` 2 or more,
  `e_fail_lines` 0. Report `idr_groups` in `R4c.rtp`: 0 is normal, because a receiver that joins
  mid-GOP has no IDR until the phone sends one.
- **D7, no cost to the session.** `ALL`: `e_ssl` 1, `e_destroy` 0, `e_errors` 0, `e_auxdec` 0,
  `e_kf_req` 0 or 1. `R4.tput`: main `windows_rendered_ge1` 90% of `windows` or more. Phone
  (`R4.phone`, window `R4-up..R4-end`): `win|Critical error=0`, `win|No input for display=0`,
  `win|Video focus gained before configurations received=0`. Report `R4.phone-bringup` beside it,
  quote only.
- **D8, the close.** `CLOSE`: `e_stopped` 1.

**Outcomes.** Grade in this order and stop at the first that matches.

- **Setup failure:** `R4.preflight` FAIL, or `ALL` `e_unavail` 1 or more. Fix the keys and re-run R4
  once. Run `ipt_off` first.
- **INCONCLUSIVE (rig):** `HUIP_SESSION` is empty, or two bring-ups failed with rc 1. Report the
  numbers.
- **FAIL-B, the push breaks the session:** D7 is not met, or rc was 2. Quote the phone's
  `Critical error` line and the first `AapService destroying` line.
- **PASS:** D1, D2, D6, D7 and D8 are met, and D4 and D5 are each PASS or INCONCLUSIVE.
- **FAIL:** anything else. Name each condition that is not met, with its numbers.

A PASS that came from a stream with no keyframe would show `FFMPEG_EARLY rc=124`. D1 refuses it.
A D5 PASS with a rule that did nothing would show `R4b.rtp` `packets` above 5. D5 refuses it.

### R5. A second push session (5 min)

The second push sample for the fps comparison and the IDR cadence.

```bash
RUN=R5; ipt_off                                   # MUST print IPT_OFF
recv_start R5
bringup R5 $PKEYS || { run_end; keep_attempt R5; recv_stop R5 >/dev/null; recv_keep R5; recv_start R5; bringup R5 $PKEYS; }
preflight
HUIP_S=$(hu_ip); echo "HUIP_SESSION=$HUIP_S" | tee "$OUT/R5.huip"
mark R5-hold; sleep 160; mark R5-check
recv_stop R5 > "$OUT/R5.rtp"; cat "$OUT/R5.rtp"
run_end
python3 -I fps30.py "$CAP" R5-up | tee "$OUT/R5.fps30"
python3 -I idr_gaps.py "$OUT/R5-rtp.csv" | tee "$OUT/R5.idr"
rtp_counts R5 R5-launch R5-end | tee "$OUT/R5.counts"; rtp_counts R5 R5-end R5-closed | tee -a "$OUT/R5.counts"
kf_times R5 R5-launch R5-end | tee "$OUT/R5.kf"
phone_grades R5 R5-up R5-end | tee "$OUT/R5.phone"
phone_grades R5 R5-launch R5-up | tee "$OUT/R5.phone-bringup"
```

If `ipt_off` prints `IPT_STILL_ON`, stop and report: the rule is left on D-HU.

**PASS:** all of these.

- `e_open` 1, `e_network` as D2 asks for, `e_fail_lines` 0 in `R5-launch..R5-end`.
- `R5.rtp`: `packets` 500 or more, `loss_pct` below 1.000, `marker_bad` 0, `fu_bad` 0,
  `idr_intact_without_ps` 0.
- `e_ssl` 1, `e_destroy` 0, `e_errors` 0, `e_kf_req` 0 or 1.
- `FPS30` `windows` 30, `rendered_ge1` 27 or more.
- Phone (`R5.phone`): `win|Critical error=0`, `win|No input for display=0`.
- `e_stopped` 1 in `R5-end..R5-closed`.

Report `R5.idr` and `R5.kf` as D3 does. **FAIL:** a PASS condition is not met.

### O2. Feature off, the second fps control (4 min)

Run O1's block with `O2` in place of every `O1`, and with `ipt_off` (MUST print `IPT_OFF`) as its
first command. The same PASS conditions apply.

### C-FPS. The cost to the main picture (graded after O2)

From the four `FPS30` lines: `F_off` = the mean of O1's and O2's `median_fps`. `F_push` = the mean
of R4's and R5's `median_fps`. Report the same pair for `median_fed_fps`.

- **PASS:** `F_push` is not lower than `F_off - 3`, and R4 and R5 each have `rendered_ge1` 27 or more.
- **FAIL-COST:** otherwise. Then run N1.

The limit is 3, not round 1's 5, because every arm now covers the same 30 windows. Five earlier
arms of 8 windows or more on this rig all read a median of 29. If `median_fed_fps` falls by the same
amount as `median_fps`, the phone sent fewer frames, and the main decoder is not the cause.

### N1. The NETWORK output, no viewer (4 min). Only after FAIL-COST.

```bash
RUN=N1; recv_start N1
bringup N1 $NKEYS || { run_end; keep_attempt N1; recv_stop N1 >/dev/null; recv_keep N1; recv_start N1; bringup N1 $NKEYS; }
preflight
mark N1-hold; sleep 160; mark N1-check
run_end
recv_stop N1 > "$OUT/N1.rtp"; cat "$OUT/N1.rtp"
python3 -I fps30.py "$CAP" N1-up | tee "$OUT/N1.fps30"
rtp_counts N1 N1-launch N1-end | tee "$OUT/N1.counts"
phone_grades N1 N1-up N1-end | tee "$OUT/N1.phone"
```

**Setup check:** `e_announce` 1 with `NETWORK` in the quoted line, `e_open` 0, `RTP_RECV packets=0`,
`e_ssl` 1, `e_destroy` 0, phone `win|Critical error=0`. If a check fails, N1 is UNTESTABLE.

**Reading, no verdict of its own:** if N1's `median_fps` is not lower than `F_off - 3`, the cost
comes from the UDP sends, and C-FPS stays FAIL-COST against the candidate. If N1's `median_fps` is
also lower than `F_off - 3`, the cost comes from the phone's second display, which every output
shares. Write which one in the results.

### Cleanup (always, last)

```bash
[ -n "$RECVPID" ] && { touch "$RSTOP"; wait $RECVPID; }
pkill -f 'rtp_recv.py'; pkill -f 'ffmpeg -nostdin'; true
ipt_off                                            # MUST print IPT_OFF
adb -s $HU shell iptables -w -S OUTPUT | grep -c -- '--dport 5004'    # MUST print 0
adb -s $HU shell rm /data/local/tmp/udp_probe.sh
adb -s $HU shell am force-stop $PKG
adb -s $HU push settings-backup-HU.xml /data/local/tmp/ssr-settings.xml
adb -s $HU shell cp /data/local/tmp/ssr-settings.xml /data/data/$PKG/shared_prefs/settings.xml
adb -s $HU shell chown $(adb -s $HU shell stat -c %u:%g /data/data/$PKG | tr -d '\r') /data/data/$PKG/shared_prefs/settings.xml
adb -s $HU shell chmod 660 /data/data/$PKG/shared_prefs/settings.xml
adb -s $HU shell cat /data/data/$PKG/shared_prefs/settings.xml | diff - settings-backup-HU.xml && echo RESTORED
```

Ask the operator to remove the PC firewall rule for UDP 5004 and 5005 (from round 1, or from P6),
and record the command they used. Restore D-POCO's radios with `ph_on` if a run ended with them off.

### Stop rule

- Each bring-up gets at most 2 attempts. A bring-up that returns 2 is not retried.
- O1 failing twice stops the round (rig broken).
- A preflight FAIL is re-run once. A second FAIL makes that run UNTESTABLE.
- An `ipt_off` that prints `IPT_STILL_ON` after 5 tries stops the round.
- N1 runs only after FAIL-COST.
- The host gate: a run that cannot start below 75C in 30 min is UNTESTABLE (host thermal), and so is
  every run after it.

## 8. Do not re-run

- Round 1's R1 (feature off), R3A (an address nobody answers) and R3C (no address): PASS. O1 and O2
  repeat R1's settings only as the fps base.
- Round 1's R2 conditions C1 to C7, C9 and C11: PASS. D2 repeats a short form of them only to prove
  R4's own stream was whole.
- The D-SAM fallback: round 1 proved the PC route.
- The aux display with its input on `INPUT_AUX`, the phone opening channel 14, and the `NETWORK` and
  `ANDROID_DISPLAY` outputs: settled in `second-screen-outputs` round 2.
- The packet bytes (RTP header, sequence wrap, single-NAL and FU-A layout, SPS and PPS in front of an
  IDR, the 90 kHz clock, the address and port rules, the SDP text, the keyframe decision): the JVM
  tests, among them `RtpH264PacketizerTest`, `RtpStreamOutputTest`, `RtpDestinationPolicyTest` and
  `RtpNetworkPolicyTest`.

## 9. Report back

1. **D1: did the SDP, as written, give FFmpeg a picture?** `FFMPEG_EARLY rc`, `PNG count` with width
   and height, `run_s` and `exit_after_up_s`.
2. **D3: how long does a late receiver wait?** `max_gap_s`, `gaps` and `tail_s` from `R4.idr` and
   `R5.idr`, and the `KF_REPORTS` times from both runs.
3. **The cost and the failure path:** the four `FPS30` lines (`median_fps`, `median_fed_fps`,
   `rendered_ge1`), `F_off` and `F_push`, and N1's line if it ran; the phone's windowed
   `Critical error` in each run; D4's `p2_delta` and `P2` `e_fail_lines`; D5's class, `P3`
   `e_fail_lines` and `R4b.rtp` `packets`; D6's `R4c.rtp` `packets`.
