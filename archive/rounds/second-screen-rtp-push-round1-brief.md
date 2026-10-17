# second-screen-rtp-push, round 1 brief

Published name on the transfer branch: `second-screen-rtp-push-round1-brief.md`. Results file:
`second-screen-rtp-push-round1-results.md`. Evidence release: `rig-evidence-second-screen-rtp-push`
(the first round of the thread, so create it), asset `second-screen-rtp-push-round1-captures.zip`.

Units: **D-HU** (head unit), **D-POCO** (phone), and the **tester PC** as the RTP receiver.
**D-SAM** is a fallback receiver, used only if pre-flight P5 finds no route from D-HU to the PC.

Estimated time: 20 min of pre-flight and build, then about 22 min of runs (R1 4 min, R2 9 min,
R3A 4 min, R3C 4 min). Total about 45 min.

## 1. Build and baseline

- **Candidate:** `feat/second-screen-rtp-push` on `fork` at
  **`9341d71fa068f113bc7aebb69e1879322df9da07`**, tree `f325fbfeefe0df92405fe2f86801e4b1cfe8ca74`.
  Two commits on `feat/second-screen-outputs` at `82b4214881d462711b853e8205cf4290ebedc53f`, which
  is the tip that the `second-screen-outputs` thread tested. It does **not** sit on `main`.
- **A new branch.** No round has tested it. The base was rebased in its own thread, so do not reuse
  an old checkout of `feat/second-screen-outputs` without the check below.

```bash
git ls-remote fork feat/second-screen-rtp-push   # MUST print 9341d71fa068f113bc7aebb69e1879322df9da07
git fetch fork feat/second-screen-rtp-push
git worktree add ../ohu-wt-rtp-9341d71f 9341d71fa068f113bc7aebb69e1879322df9da07
git -C ../ohu-wt-rtp-9341d71f rev-parse 'HEAD^{tree}'               # MUST print f325fbfeefe0df92405fe2f86801e4b1cfe8ca74
git -C ../ohu-wt-rtp-9341d71f merge-base --is-ancestor 82b4214881d462711b853e8205cf4290ebedc53f HEAD && echo BASE_OK
```

  If `ls-remote` prints another SHA or nothing, stop and report. The round has no candidate.
- **No baseline build.** R1 is the feature-off control on the candidate (template section 8).
- 3277 JVM tests pass on the candidate. They prove the packet bytes (header, single-NAL, FU-A,
  SPS and PPS in front of an IDR, the 90 kHz clock, the address rules). This round does not re-prove
  those. It proves what a JVM test cannot see (section 2).

## 2. What this is and why

The branch adds a fifth second-screen output, `RTP_PUSH`. The `second-screen-outputs` thread made
the second Android Auto display work (its round 2 passed every run). That thread's `NETWORK` output
is a TCP server: a receiver connects to the head unit and gets raw Annex-B H.264.

A user has an instrument cluster with its own IP address. It listens for RTP H.264 on a UDP port
and connects to nothing. Its SDP reads `m=video 25000 RTP/AVP 96` and `a=rtpmap:96 H264/90000`.
The `NETWORK` output cannot reach it, so the user runs a relay service. Their log also shows two
hardware decoders that fail on their unit, one for each picture. An output that forwards builds no
decoder, so `RTP_PUSH` removes the relay and the second decoder.

What the candidate does with `aux-display-enabled` on and `aux-output` = `RTP_PUSH`:

- It announces the aux display only when `aux-rtp-destination` holds a valid IPv4 address.
  Otherwise it logs `SecondScreen: the RTP_PUSH output is not available, so one display is announced`.
- When the phone opens channel 14, it opens one UDP socket. From API 22 it binds the socket to the
  network whose subnet holds the destination, never a VPN. It does not connect the socket: each
  datagram carries the destination and port. It logs `SecondScreen: RTP push to <ip>:<port> open
  (network=<name>)`. With no route, each send fails, and the first send after the route returns
  goes out.
- Each aux access unit goes out as RTP (payload type 96, 90 kHz clock, random start), 1400 bytes
  or less per datagram. A large NAL goes out as FU-A. The cached SPS and PPS go in front of an IDR
  that does not carry its own.
- It asks for one keyframe at most per open, and runs no timer. A unit with an IDR settles it with
  no request. A unit with only SPS and PPS leaves the decision to the next unit. Any other unit asks
  once.
- Every 10 s it logs `SecondScreen: RTP push sent <n> packets, <bytes> bytes, <k> keyframes in
  <ms>ms, <j> keyframes carried their own SPS and PPS`.
- A send that throws never reaches the aux lane. It logs `SecondScreen: RTP push send failed
  (<Class>): <msg>; later failures of this kind are not logged` once per exception class.
- At session end it logs `SecondScreen: RTP push stopped`.

The settings screen shows a complete SDP for the receiver. The text is built by
`RtpDestinationPolicy.sdp` and a JVM test pins it, line for line:

```
v=0
o=- 0 0 IN IP4 <destination>
s=Open Headunit second screen
c=IN IP4 <destination>
t=0 0
m=video <port> RTP/AVP 96
a=rtpmap:96 H264/90000
a=fmtp:96 packetization-mode=1
```

This round answers four questions that no JVM test can answer:

1. Does a real receiver take the stream: a whole stream with no loss, FU-A put back together, and a
   decoder that decodes it? Does the SDP above, with no line changed, open it in FFmpeg? (R2)
2. Does the socket bind to D-HU's WiFi interface, not to a VPN or to nothing? (R2, R3A)
3. Does the main picture and the session stay healthy while the push runs, and when the receiver
   is gone? (R2, R3A)
4. With the feature off, or with no address, does nothing start? (R1, R3C)

## 3. What is different about this round

- **No USB, so Native AA.** The plan for this round said to connect the phone over USB. D-HU cannot
  host USB (`host_connected=false`, `rig-quirks/topics/tooling.md`). Every run uses Native AA
  wireless, as the `second-screen-outputs` rounds did.
- **D-HU's station stays on the house WiFi during a Native session.** That thread's round 2 read
  `wlan0` = `192.168.1.4` on D-HU in the middle of a session. The RTP push uses that route. R2
  reads `wlan0` again inside the session. If it is empty, the station stood down, and R2 is
  INCONCLUSIVE (rig), not a FAIL.
- **The receiver is new.** `rtp_recv.py` (section 5) listens on UDP 5004 on the PC. It writes one
  CSV row per datagram, puts the stream back together as Annex-B, and prints one `RTP_RECV` line.
  `nal_count.py` (from the `second-screen-outputs` rounds) then reads that file.
- **The PC's firewall can block it.** `rig-quirks/units/D-HU.md`: a listener on the PC needed a rule
  change once before D-HU could reach it. In `second-screen-outputs` round 2, D-SAM could not reach
  a listener on the PC either. Pre-flight P5 sends five UDP datagrams from D-HU to the PC before any
  run. P6 asks for the firewall rule only if they do not arrive.
- **FFmpeg was absent on the PC** in `second-screen-outputs` rounds 1 and 2. It is the decoder of
  this round. P4 checks for it and P6 asks for the install. Without it, the decode conditions (C7,
  C8) are INCONCLUSIVE and R2 grades on the rest.
- **The SDP file is typed from this brief, not read from the screen.** The SDP row is far down the
  settings list, and the template bans scrolling it. P5 writes `stream.sdp` with `printf`, byte for
  byte the same as `RtpDestinationPolicy.sdp` (no trailing newline). The JVM test
  `RtpDestinationPolicyTest` pins the code's text.
- **Not covered by this round:** the settings rows for the address and the port, the toast for a
  refused address, and the write-only-when-changed rule on those rows. Settings go in
  `settings.xml` (template house rule 3), so no run touches the rows. No JVM test covers them either.
  Say this in Setup notes. It is a known gap, not a finding.
- **Two phases share one UDP port in R2.** The counter owns port 5004 first (60 s), then FFmpeg owns
  it with the SDP (up to 100 s). R2's phase 3 then has nothing on the port at all, on purpose. The
  socket is not connected, so an ICMP port unreachable from the PC never reaches it, and no
  `PortUnreachableException` line can appear.
- **A late receiver waits for the phone's next IDR.** The candidate asks for one keyframe at most
  per open, usually none, and runs no timer. In R2 phase 2 FFmpeg joins about 60 s late. The phone's GOP has been measured
  up to about 69 s. So phase 2 can end with no keyframe, and then C8 is INCONCLUSIVE, not a FAIL.
  The seconds to the first picture is a number the thread needs, whatever it is.
- **The main map is idle.** Nobody touches the phone. Compare R2's main numbers with R1's, from the
  same day, not with a fixed rate.
- **Only one decoder runs.** `RTP_PUSH` builds no aux decoder, so every `Throughput over` line in
  R2 and R3A is the main decoder. `tput.py` reports `aux_tids=none` there. That is correct.
- **The rig's audio settings are a deliberate worst case.** Do not change `use-aac-audio`,
  `audio-latency-multiplier`, `audio-queue-capacity` or `enable-audio-sink`. Read them back and record
  them. The last two rounds read the multiplier as 8.
- **Expected INCONCLUSIVE, said now:**
  - C8, if no keyframe arrives in phase 2 (above).
  - C10 on every run, and R3A's failure path. The socket is not connected, so the PC's ICMP error
    never reaches it, and no send fails. The conditions still hold and still grade the session's
    health.
  - R2, if the station has no `wlan0` address inside the session.

## 4. Settings keys

All runs are on D-HU, written as root with the app stopped, by `hu_keys` (section 5). `hu_keys`
reads every key back and prints `KEYS_OK` or `KEYS_MISMATCH <keys>`. `POCO_MAC` comes from P3.
`HOSTIP` and `UNUSED` come from P5.

**Every run (`BASE`), the same as `second-screen-outputs` round 2:**

| Key | Type | Value | Why |
|---|---|---|---|
| `wifi-connection-mode` | int | `3` | Native AA wireless, the rig's only transport |
| `native-poke-bt-macs` | set | `POCO_MAC` | wake D-POCO only |
| `native-poke-all-paired` | boolean | `false` | no other bonded phone is poked |
| `native-driver-selection-mode` | int | `0` | no selector countdown |
| `onboarding-version` | int | `2` | no wizard on launch |
| `log-level` | int | `2` | INFO. Every deciding line in section 6 is INFO, WARN or ERROR |
| `video-codec` | string | `H.264` | main sink `3`, the same codec as the aux sink |
| `resolutionId` | int | `3` | 1080p main picture |
| `view-mode` | int | `1` | TEXTURE |
| `force-software-decoding` | boolean | `false` | the main decoder on hardware |
| `software-video-decoder` | int | delete | |
| `debug-video-fault-injection` | int | delete | |
| `video-profile-starvation-cap` | boolean | delete | a latch that forces 720p30 and AAC |
| `night-mode` | int | `1` | DAY, no sensor message mid-window |

**Per run (the aux keys, from `Settings.kt` on the candidate):**

| Key | Type | R1 | R2 | R3A | R3C |
|---|---|---|---|---|---|
| `aux-display-enabled` | boolean | delete (default `false`) | `true` | `true` | `true` |
| `aux-output` | string | `RTP_PUSH` | `RTP_PUSH` | `RTP_PUSH` | `RTP_PUSH` |
| `aux-rtp-destination` | string | `HOSTIP` | `HOSTIP` | `UNUSED` | delete (default empty) |
| `aux-rtp-port` | int | `5004` | `5004` | `5004` | `5004` |
| `aux-network-size` | int | `1` (1280x720) | `1` | `1` | `1` |
| `aux-network-port` | int | delete | delete | delete | delete |
| `aux-display-role` | string | delete (`AUXILIARY`) | delete | delete | delete |
| `aux-display-content` | int | delete | delete | delete | delete |
| `aux-display-id` | int | delete | delete | delete | delete |
| `preferred-display-mode` | int | delete | delete | delete | delete |
| `preferred-display-id` | int | delete | delete | delete | delete |

R1 stores a full RTP setup with the switch off. That makes R1 a stronger control: a stored address
alone must start nothing. `aux-network-size` 1 is 1280x720, the same size the RTP push announces.

`ACTION_LOG_MARKER` is not in `AutomationCommandPolicy.CONFIGURING` on the candidate, so
`allow-external-configuration` is not needed.

```bash
BASE="int:wifi-connection-mode=3 set:native-poke-bt-macs=$POCO_MAC bool:native-poke-all-paired=false int:native-driver-selection-mode=0 int:onboarding-version=2 int:log-level=2 str:video-codec=H.264 int:resolutionId=3 int:view-mode=1 bool:force-software-decoding=false del:software-video-decoder del:debug-video-fault-injection del:video-profile-starvation-cap int:night-mode=1"
AUXREST="int:aux-rtp-port=5004 int:aux-network-size=1 del:aux-network-port del:aux-display-role del:aux-display-content del:aux-display-id del:preferred-display-mode del:preferred-display-id"
R1KEYS="del:aux-display-enabled str:aux-output=RTP_PUSH str:aux-rtp-destination=$HOSTIP $AUXREST"
R2KEYS="bool:aux-display-enabled=true str:aux-output=RTP_PUSH str:aux-rtp-destination=$HOSTIP $AUXREST"
R3AKEYS="bool:aux-display-enabled=true str:aux-output=RTP_PUSH str:aux-rtp-destination=$UNUSED $AUXREST"
R3CKEYS="bool:aux-display-enabled=true str:aux-output=RTP_PUSH del:aux-rtp-destination $AUXREST"
```

Define these after P3 and P5 have set `POCO_MAC`, `HOSTIP` and `UNUSED`. Echo all six and check that
no value is empty before R0.

## 5. Every action as a verb, and the helpers

| Step | Command |
|---|---|
| marker, both units | `mark <label>` (in `ssr_lib.sh`: `send ACTION_LOG_MARKER --es text <label>`, then `log -t AutomationMarker <label>` on D-POCO) |
| identity | `send ACTION_QUERY_STATE` |
| raise the projection, fallback only | `send ACTION_RAISE_PROJECTION` (inside `bringup`; it prints `RAISE_VERB_USED`) |
| exit at run end | `send ACTION_EXIT`, then `am force-stop` (inside `run_end`) |

**No verb exists for these, and none is needed on the app:**

- Launch: `adb shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity` (inside `bringup`).
- The receiver, FFmpeg and the UDP probe run on the PC or in D-HU's shell. They do not act on our app.
- The D-SAM fallback (R2S only) opens VLC for Android with an intent. It acts on VLC.

**There is no tap and no hand step during the runs.** The only human step is the P6 ask.

### Scripts

Make the folder `rig-toolkit/rig-data/rounds/second-screen-rtp-push-round1/scripts/` (or the same place
the `second-screen-outputs` rounds used on this host) and `cd` into it for the whole round. List its
files and their md5 in Setup notes. `python3 -I` runs every Python file.

**Five files are unchanged from `second-screen-outputs` round 2.** Copy them from that round's
scripts folder:

```bash
S2=../../second-screen-outputs-round2/scripts      # adjust if that folder is elsewhere
cp $S2/ssr_lib.sh $S2/ssr_keys.py $S2/tput.py $S2/nal_count.py $S2/pngdims.py .
cp $S2/ssr_build.sh . 2>/dev/null                   # the build wrapper, if present
md5sum ssr_lib.sh ssr_keys.py tput.py nal_count.py pngdims.py   # record them
grep -c 'phone_grades()' ssr_lib.sh                  # MUST print 1: this is round 2's lib, not round 1's
```

If that folder is missing, take `ssr_lib.sh` from section 5 of `second-screen-outputs-round2-brief.md`
and the four other files from section 5 of `second-screen-outputs-round1-brief.md`, both on this
branch. Record which source you used. `ssr_lib.sh` gives `send`, `mark`, `nl`, `waitfor`, `seg`,
`cnt`, the thermal gate, `ph_off`, `ph_on`, `cap_start`, `cap_stop`, `hu_keys`, `bringup`,
`keep_attempt`, `preflight`, `run_end` and `phone_grades`. Do not use its `aux_counts`: it counts the
`NETWORK` output's lines. `pngdims.py` raises on an empty file list, so call it only when the PNG exists.

**Three files are new.** Save them as below.

**`rtp_recv.py`** (the receiver on the PC):

```python
# rtp_recv.py <port> <max_s> <out_prefix> <stop_file> : records RTP datagrams until <stop_file> exists or <max_s>
#   writes <out_prefix>.csv (one row per datagram) and <out_prefix>.h264 (the stream put back together as Annex-B)
#   prints RTP_RECV ... and RTP_GAPS ... at the end
# rtp_recv.py --probe <port> <secs> : counts any datagrams, prints PROBE datagrams=N from=<ips>
import os, socket, struct, sys, time

if sys.argv[1] == "--probe":
    port, secs = int(sys.argv[2]), float(sys.argv[3])
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM); s.bind(("0.0.0.0", port)); s.settimeout(0.5)
    n = 0; src = set(); end = time.time() + secs
    while time.time() < end:
        try:
            d, a = s.recvfrom(65535); n += 1; src.add(a[0])
        except socket.timeout:
            pass
    print("PROBE datagrams=%d from=%s" % (n, ",".join(sorted(src)) or "none")); sys.exit(0)

port, max_s, pre, stop = int(sys.argv[1]), float(sys.argv[2]), sys.argv[3], sys.argv[4]
s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
s.setsockopt(socket.SOL_SOCKET, socket.SO_RCVBUF, 8 << 20)
s.bind(("0.0.0.0", port)); s.settimeout(0.5)
rcvbuf = s.getsockopt(socket.SOL_SOCKET, socket.SO_RCVBUF)
t0 = time.time(); pk = []; src = {}
es = open(pre + ".h264", "wb"); csv = open(pre + ".csv", "w")
csv.write("t,src,len,v,pt,m,seq,ts,ssrc,nal,fu_s,fu_e,fu_type\n")
while time.time() < t0 + max_s and not os.path.exists(stop):
    try:
        d, a = s.recvfrom(65535)
    except socket.timeout:
        continue
    t = time.time() - t0; src[a[0]] = src.get(a[0], 0) + 1
    if len(d) < 13:
        pk.append(dict(t=t, n=len(d), short=1)); continue
    b0, b1 = d[0], d[1]; cc = b0 & 15
    seq, ts, ssrc = struct.unpack(">HII", d[2:12]); pl = d[12 + 4 * cc:]
    r = dict(t=t, n=len(d), short=0, v=b0 >> 6, pxcc=(b0 >> 4) & 3 | cc, m=b1 >> 7, pt=b1 & 127,
             seq=seq, ts=ts, ssrc=ssrc, nal=pl[0] & 31 if pl else -1, s=0, e=0, ft=-1)
    if r["nal"] in range(1, 24):
        es.write(b"\x00\x00\x00\x01" + pl)
    elif r["nal"] == 28 and len(pl) >= 2:
        fu = pl[1]; r["s"], r["e"], r["ft"] = fu >> 7, (fu >> 6) & 1, fu & 31
        if r["s"]:
            es.write(b"\x00\x00\x00\x01" + bytes([(pl[0] & 0xE0) | r["ft"]]))
        es.write(pl[2:])
    pk.append(r)
    csv.write("%.4f,%s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d\n" % (t, a[0], r["n"], r["v"], r["pt"], r["m"], seq, ts, ssrc, r["nal"], r["s"], r["e"], r["ft"]))
es.close(); csv.close()

ok = [p for p in pk if not p["short"]]
out = dict(packets=len(pk), bytes=sum(p["n"] for p in pk), short=len(pk) - len(ok), rcvbuf=rcvbuf,
           srcs=",".join("%s:%d" % kv for kv in sorted(src.items())) or "none",
           first_after_s="%.1f" % pk[0]["t"] if pk else "none")
if ok:
    out.update(version_bad=sum(p["v"] != 2 for p in ok), pt_bad=sum(p["pt"] != 96 for p in ok),
               pxcc_bad=sum(p["pxcc"] != 0 for p in ok), ssrcs=len(set(p["ssrc"] for p in ok)),
               max_len=max(p["n"] for p in ok), stap=sum(p["nal"] == 24 for p in ok))
    # extend the 16-bit sequence in arrival order
    ext = []; prev = None; reorder = 0; wraps = 0
    for p in ok:
        if prev is None:
            e = p["seq"]
        else:
            diff = (p["seq"] - prev[0]) & 0xFFFF
            e = prev[1] + diff if diff < 0x8000 else prev[1] - (0x10000 - diff)
            if diff >= 0x8000: reorder += 1
            if diff < 0x8000 and p["seq"] < prev[0]: wraps += 1
        p["x"] = e; ext.append(e); prev = (p["seq"], e)
    uniq = sorted(set(ext)); expected = uniq[-1] - uniq[0] + 1; lost = expected - len(uniq)
    gaps = {}
    for a, b in zip(uniq, uniq[1:]):
        if b - a > 1: gaps[b - a - 1] = gaps.get(b - a - 1, 0) + 1
    out.update(seq_expected=expected, seq_lost=lost, loss_pct="%.3f" % (100.0 * lost / expected),
               dup=len(ext) - len(uniq), reorder=reorder, seq_wraps=wraps)
    # groups: consecutive datagrams (in sequence order) that share one timestamp
    by = {}
    for p in ok: by.setdefault(p["x"], p)
    seqd = [by[x] for x in uniq]; groups = []; cur = []
    for p in seqd:
        if cur and p["ts"] != cur[-1]["ts"]:
            groups.append(cur); cur = []
        cur.append(p)
    if cur: groups.append(cur)
    def intact(i, g):
        if any(b["x"] - a["x"] != 1 for a, b in zip(g, g[1:])): return False
        if i > 0 and g[0]["x"] - groups[i - 1][-1]["x"] != 1: return False
        if i < len(groups) - 1 and groups[i + 1][0]["x"] - g[-1]["x"] != 1: return False
        return 0 < i < len(groups) - 1
    marker_bad = fu_bad = idr = idr_ps = idr_no_ps = idr_partial = 0; first_idr = None; fu_starts = 0
    for i, g in enumerate(groups):
        types = []
        for p in g:
            if p["nal"] == 28:
                if p["s"]: types.append(p["ft"]); fu_starts += 1
            elif p["nal"] > 0:
                types.append(p["nal"])
        good = intact(i, g)
        if good:
            if [p["m"] for p in g] != [0] * (len(g) - 1) + [1]: marker_bad += 1
            run = None
            for p in g:
                if p["nal"] == 28:
                    if p["s"]:
                        if run is not None: fu_bad += 1
                        run = p["ft"]
                    elif run is None or p["ft"] != run: fu_bad += 1
                    if p["e"]: run = None
                elif run is not None:
                    fu_bad += 1; run = None
            if run is not None: fu_bad += 1
        if 5 in types:
            idr += 1
            if first_idr is None: first_idr = g[0]["t"]
            if not good:
                idr_partial += 1
            else:
                k = types.index(5)
                if 7 in types[:k] and 8 in types[:k]: idr_ps += 1
                else: idr_no_ps += 1
    span_t = seqd[-1]["t"] - seqd[0]["t"]; span_ts = (groups[-1][0]["ts"] - groups[0][0]["ts"]) & 0xFFFFFFFF
    steps = sorted(((b[0]["ts"] - a[0]["ts"]) & 0xFFFFFFFF) for a, b in zip(groups, groups[1:]))
    out.update(groups=len(groups), marker_bad=marker_bad, fu_starts=fu_starts, fu_bad=fu_bad,
               idr_groups=idr, idr_intact_with_ps=idr_ps, idr_intact_without_ps=idr_no_ps, idr_partial=idr_partial,
               first_idr_after_s="%.1f" % first_idr if first_idr is not None else "none",
               ts_rate="%.3f" % (span_ts / 90000.0 / span_t) if span_t > 0 else "na",
               ts_step_median=steps[len(steps) // 2] if steps else "na", span_s="%.1f" % span_t)
    print("RTP_GAPS " + (",".join("%d:%d" % kv for kv in sorted(gaps.items())) or "none"))
print("RTP_RECV " + " ".join("%s=%s" % kv for kv in out.items()))
```

What its fields mean, for the grade:

- `seq_lost`, `loss_pct`: sequence numbers that never arrived, against the span from the first to
  the last. `RTP_GAPS size:count` lists the gaps by size.
- A **group** is the datagrams of one timestamp, that is one access unit. A group is **intact** when
  no datagram inside it or at its two edges is lost. The first and the last group are never intact.
  `marker_bad` and `fu_bad` count only intact groups, so a lost datagram cannot cause them.
- `marker_bad`: intact groups where the marker bit is not on the last datagram only.
- `fu_bad`: intact groups where an FU-A run does not start with S, end with E, and keep one type.
- `idr_intact_without_ps`: intact groups with an IDR and no SPS and PPS in front of it.
- `ts_rate`: the timestamp span in seconds (90 kHz) over the arrival span. 1.000 is exact.

This script was run on the brief's host against a synthetic sender before publication: a sequence
wrap, a timestamp wrap and two dropped datagrams read back as `seq_wraps=1`, `seq_lost=2`,
`RTP_GAPS 2:1`, `marker_bad=0`, `fu_bad=0` and `ts_step_median=3000`.

**`udp_probe.sh`** (pushed to D-HU, run there):

```sh
# udp_probe.sh <ip> <port> : five UDP datagrams from D-HU by toybox nc
i=0; while [ $i -lt 5 ]; do echo "ohu-udp-probe-$i" | toybox nc -u -w 1 "$1" "$2" 2>&1 | head -2; i=$((i+1)); done
echo PROBE_SENT
```

**`rtp_lib.sh`** (source it after `ssr_lib.sh`: `source ./rtp_lib.sh`):

```bash
# rtp_lib.sh : round 1 of second-screen-rtp-push. Source it after ssr_lib.sh.
RPORT=5004; SAM=30041c35642d2200
# recv_start <RUN> : the RTP counter on the PC, started before the launch
recv_start() { RSTOP=$OUT/$1.stop; rm -f "$RSTOP"
  python3 -I "$D/rtp_recv.py" $RPORT 900 "$OUT/$1-rtp" "$RSTOP" > "$OUT/$1-rtp.txt" 2>&1 & RECVPID=$!; sleep 1; }
# recv_stop <RUN> : stops it and prints its two lines
recv_stop() { touch "$RSTOP"; [ -n "$RECVPID" ] && wait $RECVPID 2>/dev/null; RECVPID=; cat "$OUT/$1-rtp.txt"; }
# recv_keep <RUN> : keeps a failed first attempt's receiver files before the retry
recv_keep() { local f; for f in rtp.txt rtp.h264 rtp.csv; do [ -f "$OUT/$1-$f" ] && mv "$OUT/$1-$f" "$OUT/$1-a1-$f"; done; }
# hu_ip : D-HU's station address with its prefix, empty when the station has none
hu_ip() { adb -s $HU shell ip -4 addr show wlan0 | grep -a -o 'inet [0-9.]*/[0-9]*' | cut -d' ' -f2; sleep 0.3; }
# rtp_counts <RUN> <from> <to> : head unit counts inside one marker window
rtp_counts() { local W=$OUT/$1-$2.win A='[ServiceDiscovery] Announcing an auxiliary display on '
  seg "$OUT/$1-hu.txt" "$2" "$3" > "$W"
  echo "window=$2..$3 lines=$(wc -l < "$W")"
  echo "e_announce=$(grep -acF "$A" "$W")"; grep -aF "$A" "$W" | head -1 | cut -c1-300
  echo "e_unavail=$(grep -acF 'SecondScreen: the RTP_PUSH output is not available, so one display is announced' "$W")"
  echo "e_lane=$(grep -acF "AapTransport: the auxiliary display's video lane is open" "$W")"
  echo "e_vidaux=$(grep -acF 'VIDEO_AUX' "$W") e_secondscreen=$(grep -acF 'SecondScreen: ' "$W")"
  echo "e_open=$(grep -acF 'SecondScreen: RTP push to ' "$W") e_outopen=$(grep -acF 'SecondScreen: the RTP_PUSH output is open' "$W") e_nostart=$(grep -acF 'SecondScreen: could not start the ' "$W")"
  grep -aF 'SecondScreen: RTP push to ' "$W" | head -2 | cut -c1-260
  echo "e_network=$(grep -aF 'SecondScreen: RTP push to ' "$W" | head -1 | sed -n 's/.* open (network=\(.*\))[[:space:]]*$/\1/p')"
  grep -aoE 'RTP push sent [0-9]+ packets, [0-9]+ bytes, [0-9]+ keyframes in [0-9]+ms, [0-9]+ keyframes carried' "$W" \
    | awk '{n++; p+=$4; b+=$6; k+=$8; j+=$12; if ($4 == 0) z++} END {printf "e_sent_lines=%d e_sent_zero=%d e_packets=%d e_bytes=%d e_keyframes=%d e_kf_own=%d\n", n, z, p, b, k, j}'
  echo "e_fail_lines=$(grep -acF 'SecondScreen: RTP push send failed (' "$W")"; grep -aoE 'RTP push send failed \([A-Za-z]+\)' "$W" | sort | uniq -c
  grep -aF 'SecondScreen: RTP push send failed (' "$W" | head -3 | cut -c1-260
  echo "e_kf_req=$(grep -acF 'for a keyframe (the second screen asked for one)' "$W") e_kf_all=$(grep -acF "cycling the auxiliary display's video focus for a keyframe (" "$W") e_kf_early=$(grep -acF 'AapTransport: no auxiliary keyframe cycle before its setup ()' "$W")"
  echo "e_ssl=$(grep -acF 'SSL handshake complete' "$W") e_destroy=$(grep -acF 'AapService destroying' "$W") e_errors=$(grep -acF 'Error processing video message' "$W") e_shed=$(grep -acF ' messages behind, shedding' "$W")"
  echo "e_stopped=$(grep -acF 'SecondScreen: RTP push stopped' "$W") e_auxdec=$(grep -acF 'Decoder stopped: CommManager: doDisconnect (second screen)' "$W")"; }
```

`rtp_counts` was run before publication on a synthetic capture with the candidate's line formats.
It has not seen a real capture. If a count comes back empty rather than a number, the pattern is
wrong: fix it, say so in Setup notes, and keep going.

## 6. The deciding lines

Every head unit string below was checked with `grep -F -r` in the candidate's `app/src/main` at
`9341d71f`. All are INFO, WARN or ERROR, so `log-level` 2 carries them. `...` marks a value the app
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
| `AapService destroying` | the service went down. Must be 0 inside a run's hold |
| `Throughput over ...ms: rendered=N` | the main decoder's frames (`tput.py`) |
| `[ServiceDiscovery] Announcing an auxiliary display on VIDEO_AUX: RTP_PUSH 1280x720 as _1280x720, ..., input on INPUT_AUX` | the aux display was announced for the RTP push |
| `SecondScreen: the RTP_PUSH output is not available, so one display is announced` | no valid address, so no aux display |
| `AapTransport: the auxiliary display's video lane is open` | the phone opened channel 14 |
| `Media Sink Setup Request: 3 on channel VIDEO_AUX` | the phone set up the aux sink (quote only) |
| `Granting video focus on VIDEO_AUX after its setup` | we gave the aux sink focus (quote only) |
| `SecondScreen: RTP push to <ip>:5004 open (network=<name>)` | **new**: the socket opened; `<name>` is the network it bound to |
| `SecondScreen: the RTP_PUSH output is open` | the hub took the output |
| `SecondScreen: could not start the RTP_PUSH output: ...` | the socket open threw |
| `SecondScreen: RTP push sent <n> packets, <bytes> bytes, <k> keyframes in <ms>ms, <j> keyframes carried their own SPS and PPS` | **new**: the 10 s report |
| `SecondScreen: RTP push send failed (<Class>): ...; later failures of this kind are not logged` | **new**: a send threw, logged once per class |
| `SecondScreen: RTP push stopped` | **new**: the socket closed at session end |
| `AapTransport: cycling the auxiliary display's video focus for a keyframe (the second screen asked for one)` | the RTP push asked for its one keyframe |
| `AapTransport: no auxiliary keyframe cycle before its setup ()` | an early request was refused (quote only) |
| `Error processing video message` | a lane threw |
| `AapTransport: the VIDEO_AUX thread is ... messages behind, shedding` | the aux lane shed its backlog (quote only) |
| `Decoder stopped: CommManager: doDisconnect (second screen)` | an aux decoder stopped. Must be 0: `RTP_PUSH` builds none |

**Phone strings** (`$OUT/<RUN>-phone.txt`, inside the run's marker window, by `phone_grades`).
They are Android Auto's, not ours, and they drift between builds. Report the Gearhead version.

| Phone line | Graded | Means |
|---|---|---|
| `GH.` | capture gate, 1 or more | the phone capture works |
| `AutomationMarker: ` | window gate, 1 or more | the markers reached the phone log |
| `Critical error` | 0 in the window | Android Auto ended the session over something we sent |
| `No input for display` | 0 | the aux display has no input (the old `second-screen-outputs` round 1 fault) |
| `Multiple inputs found for display` | 0 | |
| `Video focus gained before configurations received` | 0 | a focus gain before the aux Config |
| `Discovered input for display` | quote only | the phone found an input for display 0 and 1 |

The exit teardown makes the phone log `Critical error` lines after `<RUN>-end`. The window stops at
`<RUN>-end`, so they do not count. Report `critical_error_file` beside the windowed count.

**PC strings** (not in our tree): `RTP_RECV `, `RTP_GAPS `, `PROBE datagrams=`, `PROBE_SENT`,
`FFMPEG_LIVE `, `PNG count=`, and `nal_count.py`'s `bytes=... idr=... width=... height=...`.

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
source ./ssr_lib.sh; source ./rtp_lib.sh
adb -s $HU shell stat -c '%U:%G' /data/data/$PKG/shared_prefs                    # record it
adb -s $HU shell cat /data/data/$PKG/shared_prefs/settings.xml > settings-backup-HU.xml; md5sum settings-backup-HU.xml
grep -o -E '(use-aac-audio|audio-latency-multiplier|audio-queue-capacity|enable-audio-sink)[^/]*' settings-backup-HU.xml
grep -o -E '(video-profile-starvation-cap|keep-dummy-vpn-during-session)[^/]*' settings-backup-HU.xml   # record; BASE deletes the cap
grep -o -E 'aux-[a-z-]*|preferred-display-[a-z]*' settings-backup-HU.xml       # record what is there
HU_MAC=$(adb -s $HU shell settings get secure bluetooth_address | tr -d '\r'); echo "HU_MAC=$HU_MAC"
HUIP_P=$(hu_ip); echo "HUIP_P=$HUIP_P"; HUIP=${HUIP_P%/*}; HU_PREFIX=${HUIP_P#*/}   # MUST be an address, for example 192.168.1.4/24
adb -s $HU shell dumpsys wifi | grep -a -m1 -E 'mWifiInfo|SSID:' | cut -c1-200            # record the network and its frequency
```

If `HUIP_P` is empty, D-HU's station is not on the house WiFi. Put "join D-HU to the house WiFi" in P6.

**P3. D-POCO state.**

```bash
adb -s $PH shell dumpsys package com.google.android.projection.gearhead | grep -a -m2 -E "versionName|versionCode"
POCO_MAC=$(adb -s $PH shell settings get secure bluetooth_address | tr -d '\r' | tr a-f A-F); echo "POCO_MAC=$POCO_MAC"
adb -s $PH shell dumpsys window | grep -a mCurrentFocus                        # must be the launcher
adb -s $PH shell input keyevent KEYCODE_HOME
```

`POCO_MAC` must be a real address, not empty and not `02:00:00:00:00:00`. The last two rounds read
`DC:B7:2E:5E:4E:59`. If it differs, take the D-POCO entry from D-HU's bonded list
(`adb -s $HU shell dumpsys bluetooth_manager | grep -a -A12 "Bonded devices"`) and record the source.

**P4. PC tools.**

```bash
python3 --version
command -v ffmpeg ffprobe; ffmpeg -version 2>/dev/null | head -1     # the last two rounds found neither
```

If `ffmpeg` is absent, put its install in P6.

**P5. The route from D-HU to the PC.** This decides the receiver for R2.

```bash
HOSTIP=$(ip -4 route get "$HUIP" | grep -o 'src [0-9.]*' | cut -d' ' -f2); echo "HOSTIP=$HOSTIP"
SAME_SUBNET=$( [ "$HU_PREFIX" = 24 ] && [ "${HOSTIP%.*}" = "${HUIP%.*}" ] && echo yes || echo no ); echo "SAME_SUBNET=$SAME_SUBNET"
adb -s $HU shell ping -c 3 -W 2 $HOSTIP | tail -2; sleep 0.3                          # record the loss line
adb -s $HU push udp_probe.sh /data/local/tmp/udp_probe.sh >/dev/null; sleep 0.3
python3 -I rtp_recv.py --probe $RPORT 20 > "$OUT/P5-probe.txt" & PRB=$!; sleep 2
adb -s $HU shell sh /data/local/tmp/udp_probe.sh $HOSTIP $RPORT | tee "$OUT/P5-hu.txt"
wait $PRB; cat "$OUT/P5-probe.txt"
```

Set `UDP_PROBE` from the two outputs:

- `PROBE datagrams=` 1 or more, `from=` holds `$HUIP`: `UDP_PROBE=ok`. The PC is the receiver.
- `P5-hu.txt` shows a `toybox`/`nc` usage error or `not found`: `UDP_PROBE=unknown`. Keep the PC as
  the receiver. R2 decides the route itself (section 7, R2 outcomes).
- `datagrams=0` and `PROBE_SENT` printed with no error: `UDP_PROBE=blocked`. Put the firewall rule
  in P6, then run this block again.

If `SAME_SUBNET=no`, the candidate cannot bind to a subnet that holds the PC, so `network=default`
is then correct. C1 changes for that case (R2). Record both values in Setup notes.

Then pick an unused address on D-HU's subnet for R3A (a ping with no reply, and no ARP entry):

```bash
UNUSED=; for x in 253 252 251 250 249; do c=${HUIP%.*}.$x
  adb -s $HU shell ping -c 1 -W 1 $c >/dev/null 2>&1; r=$?; sleep 0.3
  [ $r -ne 0 ] && ! adb -s $HU shell ip neigh show $c | grep -aq lladdr && { UNUSED=$c; break; }; sleep 0.3; done; echo "UNUSED=$UNUSED"
```

Write the SDP exactly as the candidate builds it, with no trailing newline:

```bash
printf 'v=0\no=- 0 0 IN IP4 %s\ns=Open Headunit second screen\nc=IN IP4 %s\nt=0 0\nm=video 5004 RTP/AVP 96\na=rtpmap:96 H264/90000\na=fmtp:96 packetization-mode=1' "$HOSTIP" "$HOSTIP" > stream.sdp
cat stream.sdp; echo; wc -l stream.sdp          # 7 newlines, 8 lines
```

Now define `BASE`, `AUXREST`, `R1KEYS`, `R2KEYS`, `R3AKEYS` and `R3CKEYS` from section 4.

**P7. The D-SAM fallback. Only if `UDP_PROBE=blocked` after P6, or the PC has no route.**

```bash
adb devices | grep -q "^$SAM" || echo "R2S_UNTESTABLE no adb"
adb -s $SAM shell pm path org.videolan.vlc                                   # installed in second-screen-outputs round 2
SAM_IP=$(adb -s $SAM shell ip -4 addr show wlan0 | grep -a -o 'inet [0-9.]*' | cut -d' ' -f2); echo "SAM_IP=$SAM_IP"
```

`SAM_IP` must share D-HU's subnet (`${SAM_IP%.*}` = `${HUIP%.*}`). Otherwise R2S is UNTESTABLE.

**P6. The one ask, only if needed.** Ask the operator in one message, and only for these:

- any rig unit that is powered on but not on adb (P1): switch its Bluetooth off or power it off;
- if P2 found no `wlan0` address on D-HU: join D-HU to the house WiFi;
- if P4 found no `ffmpeg`: may the executor run `sudo apt-get install -y ffmpeg` on the PC (or will the
  operator run it)? A no makes C7's decode and C8 INCONCLUSIVE;
- if P5 read `UDP_PROBE=blocked`: may the executor open UDP 5004 and 5005 on the PC's firewall for
  D-HU's subnet? On `ufw` that is
  `sudo ufw allow proto udp from ${HUIP%.*}.0/24 to any port 5004:5005`. Record the exact rule, and
  remove it in Cleanup. A no sends R2 to the D-SAM fallback (P7, R2S).

If none of these applies, skip P6. After the answers, run P4 and P5 again.

### R0. Build gate

Build with `--max-workers=2` after `th_wait 70`, with `ssr_build.sh` (or `build_hur.sh`) and
`HUR_DIR=../ohu-wt-rtp-9341d71f`. Copy the APK out of `apks/` at once.

```bash
adb -s $HU install -r <apk>
adb -s $HU pull $(adb -s $HU shell pm path $PKG | cut -d: -f2 | tr -d '\r') "$OUT/live.apk"; md5sum "$OUT/live.apk" <apk>
send ACTION_QUERY_STATE
unzip -p "$OUT/live.apk" 'classes*.dex' | strings | grep -c 'RtpStreamOutput'      # 1 or more
```

**PASS:** the build succeeds, the two md5s agree, the `data=` reply carries commit `9341d71fa068`
(a `-dirty` suffix from `.WORKTREE-INFO` alone is fine), and the DEX count is 1 or more.
**A build failure stops the round.** Unit tests are not re-run here: the SDLC run passed 3277.
`adb install -r` once wiped D-HU's settings, which is one reason `hu_keys` writes and checks every
key on every run.

### R1. Feature off, a full RTP setup stored (4 min)

The control. A stored address and output must start nothing while `aux-display-enabled` is off.

```bash
RUN=R1; recv_start R1
bringup R1 $R1KEYS || { run_end; keep_attempt R1; recv_stop R1 >/dev/null; recv_keep R1; recv_start R1; bringup R1 $R1KEYS; }
preflight                                                   # a FAIL: fix the keys, re-run R1 once
mark R1-hold; sleep 45; mark R1-check
python3 -I tput.py "$CAP" R1-up R1-check > "$OUT/R1.tput"; cat "$OUT/R1.tput"
run_end
recv_stop R1 | tee "$OUT/R1.rtp"
rtp_counts R1 R1-launch R1-end | tee "$OUT/R1.counts"
phone_grades R1 R1-launch R1-end | tee "$OUT/R1.phone"
```

**PASS:** all of these.

- `e_secondscreen` 0, `e_vidaux` 0, `e_announce` 0 (`R1.counts`).
- `RTP_RECV packets=0` (`R1.rtp`).
- `e_ssl` 1, `e_destroy` 0.
- Main `windows` 8 or more, and `windows_rendered_ge1` 90% of `windows` or more (`R1.tput`).
- Phone: `win|Critical error=0`, `phone_markers_in_window` 1 or more (`R1.phone`).

**FAIL:** any of those is not met. Quote the first offending line. Two failed bring-ups stop the
round (rig broken). If both fail at `SESSION_FAIL_SSL`, compare the MAC in the phone's last
`Creating rfcomm socket for device` line with `HU_MAC` (`rig-quirks/units/D-POCO.md`).

A PASS here would look the same if the feature code did nothing. That is the point: it is the control
for R2's main numbers and for R2's "something did start" counts.

### R2. RTP push to the PC: THE POINT OF THE ROUND (9 min)

Run this when `UDP_PROBE` is `ok` or `unknown`. With `UDP_PROBE=blocked` after P6, run R2S instead.

Three phases inside one session:

- **Phase 1 (60 s):** `rtp_recv.py` owns UDP 5004. It ran from before the launch, so it holds the
  whole stream from its first datagram.
- **Phase 2 (100 s at most):** FFmpeg opens `stream.sdp` as written, decodes the first frame it can,
  and exits. This is the late receiver, about 60 s into the stream.
- **Phase 3 (30 s):** nothing listens on 5004. This is the "receiver gone" case. D-HU must keep the
  session and log at most one failure line per exception class.

```bash
RUN=R2; recv_start R2
bringup R2 $R2KEYS; rc=$?
if [ $rc = 1 ]; then run_end; keep_attempt R2; recv_stop R2 >/dev/null; recv_keep R2; recv_start R2; bringup R2 $R2KEYS; rc=$?; fi
echo "R2_BRINGUP rc=$rc"          # 2 = the phone logged Critical error with no frame: do not retry, grade at once
preflight
HUIP_S=$(hu_ip); echo "HUIP_SESSION=$HUIP_S" | tee "$OUT/R2.huip"
# phase 1
mark R2-p1; sleep 60; mark R2-p1-end
recv_stop R2 | tee "$OUT/R2.rtp"
# phase 2: the SDP as written, with no line added
mark R2-p2; T0=$(date +%s.%N)
if command -v ffmpeg >/dev/null; then
  timeout 100 ffmpeg -nostdin -hide_banner -loglevel info -protocol_whitelist file,udp,rtp -i "$D/stream.sdp" -frames:v 1 -y "$OUT/R2-first.png" > "$OUT/R2-ffmpeg.log" 2>&1; FRC=$?
else FRC=none; sleep 60; fi
T1=$(date +%s.%N); mark R2-p2-end
echo "FFMPEG_LIVE rc=$FRC seconds=$(python3 -I -c "print(round($T1-$T0,1))")" | tee "$OUT/R2.ffmpeg"
[ -s "$OUT/R2-first.png" ] && python3 -I pngdims.py "$T0" "$OUT/R2-first.png" | tee -a "$OUT/R2.ffmpeg"
grep -a -i -E 'error|non-existing|Stream #0' "$OUT/R2-ffmpeg.log" | head -8 | cut -c1-200 | tee -a "$OUT/R2.ffmpeg"
# phase 3: no receiver
mark R2-p3; sleep 30; mark R2-p3-end
python3 -I tput.py "$CAP" R2-up R2-p3-end > "$OUT/R2.tput"; cat "$OUT/R2.tput"
run_end
# offline reads of the stream that phase 1 put back together
RS=$(grep -ao 'span_s=[0-9.]*' "$OUT/R2.rtp" | cut -d= -f2)
python3 -I nal_count.py "$OUT/R2-rtp.h264" "${RS:-0}" | tee "$OUT/R2.nal"
command -v ffprobe >/dev/null && ffprobe -v error -count_frames -select_streams v:0 -show_entries stream=codec_name,profile,width,height,nb_read_frames -of default=nw=1 "$OUT/R2-rtp.h264" | tee "$OUT/R2.ffprobe"
command -v ffmpeg >/dev/null && echo "decode_error_lines=$(ffmpeg -v error -i "$OUT/R2-rtp.h264" -f null - 2>&1 | wc -l)" | tee -a "$OUT/R2.ffprobe"
for w in "R2-launch R2-p1-end" "R2-p2 R2-p2-end" "R2-p3 R2-p3-end" "R2-launch R2-end" "R2-end R2-closed"; do rtp_counts R2 $w; echo; done | tee "$OUT/R2.counts"
phone_grades R2 R2-launch R2-end | tee "$OUT/R2.phone"
```

The receivers and FFmpeg talk to D-HU over the WiFi, not over adb, so house rule 8 does not apply.

**Conditions.** Every count is in the window named. `P1` = `R2-launch..R2-p1-end`, `P2` =
`R2-p2..R2-p2-end`, `P3` = `R2-p3..R2-p3-end`, `ALL` = `R2-launch..R2-end`, `CLOSE` =
`R2-end..R2-closed`.

- **C1, the socket and its network.** `ALL`: `e_announce` 1 and the quoted line names
  `RTP_PUSH 1280x720` and ends `input on INPUT_AUX`. `e_open` 1, `e_outopen` 1, `e_nostart` 0.
  With `SAME_SUBNET=yes`, `e_network` is the name of D-HU's station interface (`wlan0` on this
  unit). With `SAME_SUBNET=no`, `e_network` is `default`. Any other value, `default, bind failed: ...`
  included, fails C1. Quote the open line.
- **C2, the sender runs.** `P1`: `e_sent_lines` 4 or more, `e_sent_zero` 0, `e_packets` 500 or more.
- **C3, the datagrams arrive whole.** `R2.rtp`: `packets` 500 or more, `loss_pct` below 1.000,
  `srcs` holds only `$HUIP`, `short` 0, `version_bad` 0, `pt_bad` 0, `pxcc_bad` 0, `ssrcs` 1,
  `max_len` 1400 or less, `stap` 0.
- **C4, units and FU-A.** `R2.rtp`: `marker_bad` 0, `fu_bad` 0, and `fu_starts` 1 or more (proof that
  the FU-A path ran; a 1280x720 IDR does not fit in one datagram).
- **C5, parameter sets.** `R2.rtp`: `idr_groups` 1 or more and `idr_intact_without_ps` 0. Report
  `idr_intact_with_ps` and `idr_partial`. Report `e_kf_own` and `e_keyframes` from `P1` beside them.
- **C6, the clock.** `R2.rtp`: `ts_rate` from 0.950 to 1.050. Report `ts_step_median` (about 3000 at
  30 fps).
- **C7, the stream decodes.** `R2.nal`: `sps` 1 or more, `idr` 1 or more, and `width=1280 height=720`.
  With FFmpeg: `R2.ffprobe` `nb_read_frames` 100 or more and `codec_name=h264`. Report
  `decode_error_lines`: errors before the first IDR are normal, because the recording starts on
  whatever unit came first. Without FFmpeg, the decode half is INCONCLUSIVE and the `R2.nal` half
  still grades.
- **C8, the SDP opens as written.** `R2.ffmpeg`: `FFMPEG_LIVE rc=0`, and `PNG count=1` with
  `width=1280 height=720`. Report `seconds`: the late receiver's wait for a picture.
  **INCONCLUSIVE** when `rc` is not 0 and `P2` `e_keyframes` is 0 (no keyframe came in the window).
  **FAIL** when `rc` is not 0 and `P2` `e_keyframes` is 1 or more. Quote the FFmpeg error lines.
  Without FFmpeg, INCONCLUSIVE.
- **C9, one keyframe request.** `ALL`: `e_kf_req` 0 or 1. Expect 0 on most sessions, because the
  first unit usually carries SPS and PPS and the next one an IDR. Report `e_kf_all` and `e_kf_early`.
- **C10, the receiver gone.** `ALL`: each class in the `RTP push send failed (<Class>)` list appears
  once at most. `P3`: `e_sent_lines` 2 or more. Report every class and its first line. If `P3`
  `e_fail_lines` is 0 and no class appears in `ALL`, the PC sent no ICMP error and the failure path
  did not run: C10 is **INCONCLUSIVE** and does not change R2's verdict.
- **C11, the close.** `CLOSE`: `e_stopped` 1.
- **C12, no cost to the session.** `ALL`: `e_ssl` 1, `e_destroy` 0, `e_errors` 0, `e_auxdec` 0.
  `R2.tput`: main `windows_rendered_ge1` 90% of `windows` or more, and main `median_fps` no more than
  5 below R1's. Phone: `win|Critical error=0`, `win|No input for display=0`,
  `win|Video focus gained before configurations received=0`.

**Outcomes.** Grade in this order and stop at the first that matches.

- **Setup failure:** `R2.preflight` FAIL, or `ALL` `e_unavail` 1 or more (the address was not
  stored). Fix the keys and re-run R2 once.
- **INCONCLUSIVE (rig):** `HUIP_SESSION` is empty (the station stood down in the session), or two
  bring-ups failed with rc 1. Report the numbers.
- **FAIL-B, the push breaks the session:** C12 is not met, or rc was 2. Quote the phone's
  `Critical error` line and the first `AapService destroying` line. This is the worst outcome.
- **FAIL-OPEN:** `e_nostart` 1 or more, or `e_open` 0 while `e_lane` is 1. Quote the line.
- **INCONCLUSIVE (route):** C2 is met, `R2.rtp` `packets` is 0, and `UDP_PROBE=unknown`.
  The route was never proved. Report `e_network`.
- **FAIL-ROUTE:** C2 is met, `R2.rtp` `packets` is 0, and `UDP_PROBE=ok`. The PC took D-HU's
  datagrams in P5 and not the candidate's. Quote the open line.
- **PASS:** C1 to C7, C9, C11 and C12 are met, and C8 and C10 are each PASS or INCONCLUSIVE.
- **FAIL:** anything else. Name each condition that is not met, with its numbers.

A PASS that came from a broken stream would show `packets` with `fu_starts` 0 or `idr_groups` 0.
C4 and C5 refuse that case. A PASS with a socket that bound to nothing would show `e_network` =
`default`. C1 refuses that case when `SAME_SUBNET=yes`.

### R2S. The D-SAM fallback (8 min). Only when R2 cannot use the PC (P6, P7).

D-SAM runs VLC for Android on the SDP. This grades the picture only. C3 to C8 are UNTESTABLE here,
and R2S says so.

```bash
RUN=R2S
R2SKEYS="bool:aux-display-enabled=true str:aux-output=RTP_PUSH str:aux-rtp-destination=$SAM_IP $AUXREST"
printf 'v=0\no=- 0 0 IN IP4 %s\ns=Open Headunit second screen\nc=IN IP4 %s\nt=0 0\nm=video 5004 RTP/AVP 96\na=rtpmap:96 H264/90000\na=fmtp:96 packetization-mode=1' "$SAM_IP" "$SAM_IP" > stream-sam.sdp
adb -s $SAM push stream-sam.sdp /sdcard/stream.sdp; sleep 0.3
adb -s $SAM shell am force-stop org.videolan.vlc; sleep 0.3
adb -s $SAM shell am start -a android.intent.action.VIEW -d file:///sdcard/stream.sdp -t application/sdp -n org.videolan.vlc/.StartActivity
bringup R2S $R2SKEYS || { run_end; keep_attempt R2S; bringup R2S $R2SKEYS; }
preflight
mark R2S-hold; prev=0
for t in 10 30 60 90; do sleep $((t - prev)); prev=$t
  adb -s $SAM shell screencap -p /sdcard/rtp-view.png; sleep 0.3
  adb -s $SAM pull /sdcard/rtp-view.png "$OUT/R2S-sam-${t}s.png" >/dev/null 2>&1; sleep 0.3; done
mark R2S-check
python3 -I tput.py "$CAP" R2S-up R2S-check > "$OUT/R2S.tput"; cat "$OUT/R2S.tput"
run_end
adb -s $SAM shell am force-stop org.videolan.vlc; sleep 0.3; adb -s $SAM shell rm /sdcard/rtp-view.png /sdcard/stream.sdp
rtp_counts R2S R2S-launch R2S-end | tee "$OUT/R2S.counts"; rtp_counts R2S R2S-end R2S-closed | tee -a "$OUT/R2S.counts"
phone_grades R2S R2S-launch R2S-end | tee "$OUT/R2S.phone"
```

**PASS:** C1 (with `SAM_IP` in place of `HOSTIP`), C2, C9, C11 and C12 as in R2, and **the host
reads the four screenshots** and finds the Android Auto map in one or more. An extract cannot grade
them. VLC's own screen or a black frame is not a picture. Report the first screenshot with a picture.
**FAIL:** a condition is not met, or no screenshot shows a picture while C2 is met.

### R3A. An address that nobody answers (4 min)

The destination is on D-HU's subnet, and no device holds it. The kernel can find no neighbour for it.

```bash
RUN=R3A
bringup R3A $R3AKEYS || { run_end; keep_attempt R3A; bringup R3A $R3AKEYS; }
preflight
mark R3A-hold; sleep 60; mark R3A-check
python3 -I tput.py "$CAP" R3A-up R3A-check > "$OUT/R3A.tput"; cat "$OUT/R3A.tput"
run_end
rtp_counts R3A R3A-launch R3A-end | tee "$OUT/R3A.counts"; rtp_counts R3A R3A-end R3A-closed | tee -a "$OUT/R3A.counts"
phone_grades R3A R3A-launch R3A-end | tee "$OUT/R3A.phone"
```

**PASS:** all of these.

- `e_open` 1, and `e_network` as C1 asks for (`UNUSED` shares D-HU's subnet, so with a /24 it is the
  station interface).
- `e_sent_lines` 4 or more in `R3A-launch..R3A-end`. The report runs even when the sends fail, so
  `e_packets` can be 0. Report it.
- Each `RTP push send failed (<Class>)` class appears once at most. Report every class.
- C12's session conditions: `e_ssl` 1, `e_destroy` 0, `e_errors` 0, main `windows_rendered_ge1` 90% of
  `windows` or more, phone `win|Critical error=0`.
- `e_stopped` 1 in `R3A-end..R3A-closed`.

If no class appears at all, the kernel dropped the datagrams without an error. Write "the failure
path did not run" beside the PASS. **FAIL:** a PASS condition is not met.

### R3C. No address stored (4 min)

```bash
RUN=R3C
bringup R3C $R3CKEYS || { run_end; keep_attempt R3C; bringup R3C $R3CKEYS; }
preflight
mark R3C-hold; sleep 40; mark R3C-check
python3 -I tput.py "$CAP" R3C-up R3C-check > "$OUT/R3C.tput"; cat "$OUT/R3C.tput"
run_end
rtp_counts R3C R3C-launch R3C-end | tee "$OUT/R3C.counts"
phone_grades R3C R3C-launch R3C-end | tee "$OUT/R3C.phone"
```

**PASS:** `e_unavail` 1 or more, `e_announce` 0, `e_vidaux` 0, `e_open` 0, `e_sent_lines` 0, `e_ssl` 1,
`e_destroy` 0, main `windows_rendered_ge1` 90% of `windows` or more, and phone
`win|Critical error=0` and `win|No input for display=0`.
**FAIL:** any of those is not met.

### Cleanup (always, last)

```bash
[ -n "$RECVPID" ] && { touch "$RSTOP"; wait $RECVPID; }
pkill -f 'rtp_recv.py' ; pkill -f 'ffmpeg -nostdin' ; true
adb -s $HU shell rm /data/local/tmp/udp_probe.sh
adb -s $HU shell am force-stop $PKG
adb -s $HU push settings-backup-HU.xml /data/local/tmp/ssr-settings.xml          # restore as root, as hu_keys does
adb -s $HU shell cp /data/local/tmp/ssr-settings.xml /data/data/$PKG/shared_prefs/settings.xml
adb -s $HU shell chown $(adb -s $HU shell stat -c %u:%g /data/data/$PKG | tr -d '\r') /data/data/$PKG/shared_prefs/settings.xml
adb -s $HU shell chmod 660 /data/data/$PKG/shared_prefs/settings.xml
adb -s $HU shell cat /data/data/$PKG/shared_prefs/settings.xml | diff - settings-backup-HU.xml && echo RESTORED
```

Remove any firewall rule that P6 added, and record the command. Restore D-POCO's radios with `ph_on`
if a run ended with them off. Leave VLC installed on D-SAM.

### Stop rule

- Each bring-up gets at most 2 attempts. A bring-up that returns 2 is not retried.
- R1 failing twice stops the round (rig broken).
- A preflight FAIL is re-run once. A second FAIL makes that run UNTESTABLE.
- R2S runs only when R2 cannot use the PC. R3A and R3C run on every R2 outcome.
- The host gate: a run that cannot start below 75C in 30 min is UNTESTABLE (host thermal), and so is
  every run after it.

## 8. Do not re-run

- The aux display with its input on `INPUT_AUX`, the phone opening channel 14, and the `NETWORK` and
  `ANDROID_DISPLAY` outputs: settled in `second-screen-outputs` round 2 (every run PASS).
- The packet bytes (RTP header, sequence wrap, single-NAL and FU-A layout, SPS and PPS in front of an
  IDR, the 90 kHz clock, the address and port rules, the SDP text): 3277 JVM tests, among them
  `RtpH264PacketizerTest`, `RtpStreamOutputTest`, `RtpDestinationPolicyTest` and
  `RtpNetworkPolicyTest`. This round checks them only as a real receiver sees them.
- A Native AA session between D-HU and D-POCO: settled in many rounds. R1 is a control here.

## 9. Report back

1. **R2: did a real receiver take the stream?** `packets`, `loss_pct` and `RTP_GAPS`; `fu_starts`,
   `fu_bad`, `marker_bad`; `idr_groups` and `idr_intact_without_ps`; `R2.nal` width and height and
   `R2.ffprobe` `nb_read_frames`. Then C8: did the SDP open as written, and in how many seconds.
2. **The network the socket bound to:** `e_network` in R2 and R3A, beside `SAME_SUBNET`.
3. **The cost:** main `median_fps` and `windows_rendered_ge1` of `windows` in R1, R2, R3A and R3C,
   the phone's windowed `Critical error` in each run, and every `RTP push send failed` class seen in
   R2 phase 3 and R3A.
