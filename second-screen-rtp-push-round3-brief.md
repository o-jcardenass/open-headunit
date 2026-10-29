# second-screen-rtp-push, round 3 brief

Published name on the transfer branch: `second-screen-rtp-push-round3-brief.md`. Results file:
`second-screen-rtp-push-round3-results.md`. The ticket's round 3 brief of revision 1 was never
published, so this is the thread's third published round. Evidence: upload to the existing release
`rig-evidence-second-screen-rtp-push`, asset `second-screen-rtp-push-round3-captures.zip`.

Units: **D-HU** (head unit), **D-POCO** (phone), and the **tester PC** as the RTP receiver.
Queue line: D-HU, D-POCO, tester PC. No other unit is used, so the README can pair this round with a
round on D-MOTO, D-SAM or D-HP (template section 3b).

Estimated time: about 15 min of pre-flight, 15 min for R0 (one build), then about 20 min of runs
(S12 8 min, S3 5 min, S4 5 min, S5 graded from them). Add up to 10 min for each thermal wait. Total
about 50 min.

**S1 is the point of the round.** It asks one question that no earlier round could answer: does
Android Auto send a new keyframe on the second display when we cycle that display's video focus?

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

```bash
git ls-remote fork fix/rtp-push-recovery   # MUST print 8d4af793b5e87a7b44f1e0ac01976a8db21766f6
```

If `ls-remote` prints another SHA or nothing, stop and report. The round has no candidate.

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
with four small additions, and refuses to write them if a source file's md5 differs:

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
  station must stay on the house WiFi (`network=wlan0` in every earlier session).
- **The PC firewall rule for UDP 5004 and 5005 may be gone again.** Round 2 found round 1's rule
  gone. P5 probes it. The proxy forwards to the grader on `127.0.0.1:5006`, which needs no rule.
- **The rig's audio settings are a deliberate worst case.** Do not change `use-aac-audio`,
  `audio-latency-multiplier`, `audio-queue-capacity` or `enable-audio-sink`. Read them back and
  record them. Round 2 read the multiplier as 8.
- **The phones may run Gearhead 18.1.164344**, which is not decompiled. Record the version. Phone
  strings in this brief are Gearhead's, not ours, and cannot be checked against our tree.
- **`ACTION_LOG_MARKER` is not in `AutomationCommandPolicy.CONFIGURING`** at the candidate, so
  `allow-external-configuration` is not needed.
- **Not covered:** the new "Refresh the picture every" row, and the address and port rows. Entry
  needs a dialog or typing. Say so in Setup notes. It is a known gap, not a finding.
- **Expected INCONCLUSIVE, said now:**
  - S1, if the phone sends IDRs at least every 15 s by itself (then fewer than 5 requests go out).
  - S2, if neither the second display nor D-HU's main screen changes after a route change (the map
    did not move).
  - S3, if S4 does not reproduce the reported fault.

## 4. Settings keys

All runs are on D-HU, written as root with the app stopped, by `hu_keys` (round 1's `ssr_lib.sh`).
`hu_keys` reads every key back and prints `KEYS_OK` or `KEYS_MISMATCH <keys>`. `POCO_MAC` comes from
P3 and `HOSTIP` from P5.

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

**Per run:**

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
No run changes the WiFi Direct identity, so leave `wifi-direct-last-identity-verdict` as found and
record its value in P2.

`p_pre.sh` (section 7) writes these as `BASE`, `K10` and `K0` into `env.sh`.

## 5. Every action as a verb, and the helpers

| Step | Command |
|---|---|
| marker, both units | `mark <label>` (in `ssr_lib.sh`: `send ACTION_LOG_MARKER --es text <label>`, then `log -t AutomationMarker <label>` on D-POCO) |
| marker with its PC time | `markt <label>` (in `r3_lib.sh`: writes the PC time, then `mark`) |
| identity | `send ACTION_QUERY_STATE` |
| raise the projection, fallback only | `send ACTION_RAISE_PROJECTION` (inside `bringup`; it prints `RAISE_VERB_USED`) |
| exit at run end | `send ACTION_EXIT`, then `am force-stop` (inside `run_end`) |

**No verb exists for these, and none is needed on the app:**

- Launch: `adb shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity` (inside `bringup`).
- The route: `ph_nav` sends a `google.navigation:` intent to Google Maps on D-POCO. It acts on Maps.
- The screenshots of D-HU's main screen: `adb exec-out screencap`, one at a time, never in a loop.
- The grader, the proxy and FFmpeg run on the PC.

**There is no tap and no hand step during the runs.** The only human steps are the P6 ask and the
firewall removal at Cleanup.

### Scripts

Make the folder `$RIG_DATA/rounds/second-screen-rtp-push-round3/scripts/` and `cd` into it for the
whole round. Save every block below that has a `name` line with
`$RIG_TOOLKIT/extract_brief_blocks.py <brief> .`. List the folder's files and their md5 in Setup
notes. `python3 -I` runs every Python file.

**Six files come from round 2's folder.** Copy them, and compare the md5 prefixes with round 2's
Setup notes:

```bash
S2D=../../second-screen-rtp-push-round2/scripts      # adjust if that folder is elsewhere
cp $S2D/ssr_lib.sh $S2D/ssr_keys.py $S2D/rtp_recv.py $S2D/rtp_lib.sh $S2D/udp_probe.sh $S2D/fps30.py .
md5sum ssr_lib.sh ssr_keys.py rtp_recv.py rtp_lib.sh udp_probe.sh fps30.py
# round 2 read: ssr_lib.sh 37fa464e, ssr_keys.py 3e9ffa2e, rtp_recv.py 7b6df231, rtp_lib.sh 9c1530fc,
#               udp_probe.sh a9ebfbf3, fps30.py c31e93ef
```

If a prefix differs, record it and use the file anyway. If the folder is missing, take the files
from the round 2 evidence zip (release `rig-evidence-second-screen-rtp-push`, asset
`second-screen-rtp-push-round2-captures.zip`, which holds the scripts folder). Record the source.

From `ssr_lib.sh` this round uses `send`, `mark`, `nl`, `waitfor`, `seg`, the thermal gate, `ph_off`,
`ph_on`, `cap_start`, `cap_stop`, `hu_keys`, `bringup`, `keep_attempt`, `preflight`, `run_end` and
`phone_grades`. From `rtp_lib.sh` it uses only `hu_ip`. Do not use `rtp_counts`: it cannot read the
new end of the open line. `push_counts` replaces it.

**Six files are new.**

**`patch_tools.py`**
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

**`s1_pair.py`**
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

**`s2_grade.py`**
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

**`s5.py`**
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

**`r3_lib.sh`**
```bash
# r3_lib.sh : second-screen-rtp-push round 3. Source it after ssr_lib.sh and rtp_lib.sh.
RPORT=5004; GPORT=5006
# markt <label> : a marker, with the PC time of its send in $OUT/$RUN.pcmarks (for the clock pairing)
markt() { echo "$1 $(date +%s.%N)" >> "$OUT/$RUN.pcmarks"; mark "$1"; }
# grade_start <label> <bind> <port> <duration_s> : the strict receiver, in the background
grade_start() { python3 -I "$D/rtp_grade_rig.py" --bind "$2" --port "$3" --first-wait 400 --idle 15 --duration "$4" \
    --label "$1" --idr-log "$OUT/$1.idr" --json "$OUT/$1.grade.json" > "$OUT/$1.grade" 2>&1 & GPID=$!; sleep 1; cat "$OUT/$1.grade"; }
# grade_wait <label> <cap_s> : waits for it to end, kills it at the cap, prints its GRADE line
grade_wait() { local n=0; while kill -0 $GPID 2>/dev/null && [ $n -lt $2 ]; do sleep 1; n=$((n+1)); done
  kill -0 $GPID 2>/dev/null && { kill $GPID; echo "GRADER_KILLED $1 after ${2}s"; }; wait $GPID 2>/dev/null; GPID=
  grep -a '^GRADE ' "$OUT/$1.grade" || echo "GRADE missing $1"; }
# proxy_start <label> : the lossy proxy on UDP 5004 of every interface, bursts at 30, 60 and 90 s, to the grader on 5006
proxy_start() { python3 -I "$D/lossy_proxy_rig.py" --bind 0.0.0.0 --listen $RPORT --forward 127.0.0.1:$GPORT \
    --first-wait 400 --idle 15 --drop-at 30 --drop-at 60 --drop-at 90 > "$OUT/$1.proxy" 2>&1 & XPID=$!; sleep 1; cat "$OUT/$1.proxy"; }
proxy_wait() { local n=0; while kill -0 $XPID 2>/dev/null && [ $n -lt $2 ]; do sleep 1; n=$((n+1)); done
  kill -0 $XPID 2>/dev/null && { kill $XPID; echo "PROXY_KILLED $1 after ${2}s"; }; wait $XPID 2>/dev/null; XPID=; cat "$OUT/$1.proxy"; }
# rx_kill : stops the grader and the proxy of a failed bring-up
rx_kill() { for p in $GPID $XPID; do kill $p 2>/dev/null; wait $p 2>/dev/null; done; GPID=; XPID=; }
# ph_nav <destination> : a driving route on D-POCO, inside its offline map area
ph_nav() { adb -s $PH shell am start -a android.intent.action.VIEW -p com.google.android.apps.maps \
    -d "google.navigation:q=$1\&mode=d" >/dev/null; sleep 0.3; }
# maps_notes : posted Google Maps notifications on D-POCO (report only)
maps_notes() { adb -s $PH shell dumpsys notification --noredact | grep -a NotificationRecord | grep -ac 'pkg=com.google.android.apps.maps'; sleep 0.3; }
# hu_shot <name> : one screenshot of D-HU's main screen, logged in S2.events
hu_shot() { local t; t=$(date +%s.%N); adb -s $HU exec-out screencap -p > "$OUT/$1.png"; echo "shot $t $OUT/$1.png" >> "$OUT/S2.events"; sleep 0.3; }
# ff_s2 : FFmpeg on stream.sdp as written, 60 s of decode into framemd5 and a PNG every 10 s
ff_s2() { rm -f "$OUT/S2.framemd5" "$OUT"/S2-snap*.png "$OUT/S2.ffexit"; echo "join $(date +%s.%N)" >> "$OUT/S2.events"
  ( timeout 100 ffmpeg -nostdin -hide_banner -loglevel info -protocol_whitelist file,udp,rtp \
      -analyzeduration 25000000 -probesize 50000000 -i "$D/stream.sdp" \
      -map 0:v:0 -t 60 -f framemd5 "$OUT/S2.framemd5" \
      -map 0:v:0 -t 60 -vf fps=1/10 -y "$OUT/S2-snap%02d.png" > "$OUT/S2-ffmpeg.log" 2>&1
    echo "rc=$? t=$(date +%s.%N)" > "$OUT/S2.ffexit" ) & FFPID=$!; }
# ff_s2_wait <cap_s> : waits for FFmpeg, stops it at the cap, prints its exit and its error lines
ff_s2_wait() { local n=0; while kill -0 $FFPID 2>/dev/null && [ $n -lt $1 ]; do sleep 1; n=$((n+1)); done
  kill -0 $FFPID 2>/dev/null && pkill -f '^ffmpeg -nostdin -hide_banner'; wait $FFPID 2>/dev/null; FFPID=
  cat "$OUT/S2.ffexit"; grep -a -i -E 'error|non-existing|Stream #0' "$OUT/S2-ffmpeg.log" | head -8 | cut -c1-200; true; }
# push_counts <RUN> <from> <to> : head unit counts inside one marker window
push_counts() { local W=$OUT/$1-$2.win
  seg "$OUT/$1-hu.txt" "$2" "$3" > "$W"
  echo "window=$2..$3 lines=$(wc -l < "$W")"
  echo "e_announce=$(grep -acF '[ServiceDiscovery] Announcing an auxiliary display on VIDEO_AUX: RTP_PUSH ' "$W") e_unavail=$(grep -acF 'SecondScreen: the RTP_PUSH output is not available, so one display is announced' "$W") e_lane=$(grep -acF "AapTransport: the auxiliary display's video lane is open" "$W")"
  echo "e_open=$(grep -acF 'SecondScreen: RTP push to ' "$W") e_outopen=$(grep -acF 'SecondScreen: the RTP_PUSH output is open' "$W") e_nostart=$(grep -acF 'SecondScreen: could not start the ' "$W")"
  local O; O=$(grep -aF 'SecondScreen: RTP push to ' "$W" | head -1); echo "$O" | cut -c1-260
  echo "e_network=$(echo "$O" | sed -n 's/.* open (network=\(.*\), refresh=\([0-9]*\)ms)[[:space:]]*$/\1/p') e_refresh_ms=$(echo "$O" | sed -n 's/.* open (network=\(.*\), refresh=\([0-9]*\)ms)[[:space:]]*$/\2/p')"
  grep -aoE 'RTP push sent [0-9]+ packets, [0-9]+ bytes, [0-9]+ keyframes in [0-9]+ms, [0-9]+ keyframes carried their own SPS and PPS, [0-9]+ refreshes asked, [0-9]+ pacing pauses' "$W" \
    | awk '{n++; p+=$4; b+=$6; k+=$8; if ($8 > 0) kl++; j+=$12; r+=$20; q+=$23} END {printf "e_sent_lines=%d e_packets=%d e_bytes=%d e_keyframes=%d e_kf_lines=%d e_kf_own=%d e_refreshes=%d e_pauses=%d\n", n, p, b, k, kl, j, r, q}'
  echo "e_asks=$(grep -acF 'SecondScreen: RTP push asking for a keyframe, none sent for ' "$W") e_cyc_push=$(grep -acF "cycling the auxiliary display's video focus for a keyframe (the second screen asked for one)" "$W") e_cyc_all=$(grep -acF "AapTransport: cycling the auxiliary display's video focus for a keyframe (" "$W") e_kf_early=$(grep -acF 'AapTransport: no auxiliary keyframe cycle before its setup ()' "$W")"
  echo "e_aux_stop_cycle=$(grep -acF 'Auxiliary Video Sink Stopped -> Ignored (Forced Keyframe Request)' "$W") e_aux_stop_other=$(grep -acF 'Auxiliary Video Sink Stopped -> the main session continues' "$W") e_aux_start=$(grep -acF 'Media Start Request VIDEO_AUX: ' "$W") e_aux_released=$(grep -acF "The phone released the second display's video. The main session continues." "$W")"
  echo "e_main_stop=$(grep -acE 'Media Sink Stop Request: VIDEO[[:space:]]*$' "$W") e_main_vss=$(grep -aF 'Video Sink Stopped -> ' "$W" | grep -avcF 'Auxiliary') e_main_retake=$(grep -acF 'retaking video focus to complete the keyframe cycle' "$W") e_main_start=$(grep -acF 'Media Start Request VIDEO: ' "$W") e_main_setup=$(grep -acE 'Media Sink Setup Request: [0-9]+ on channel VIDEO[[:space:]]*$' "$W")"
  echo "e_fail_lines=$(grep -acF 'SecondScreen: RTP push send failed (' "$W") e_ssl=$(grep -acF 'SSL handshake complete' "$W") e_destroy=$(grep -acF 'AapService destroying' "$W") e_errors=$(grep -acF 'Error processing video message' "$W") e_shed=$(grep -acF ' messages behind, shedding' "$W")"
  echo "e_stopped=$(grep -acF 'SecondScreen: RTP push stopped' "$W") e_auxdec=$(grep -acF 'Decoder stopped: CommManager: doDisconnect (second screen)' "$W")"; }
```

`push_counts` ran on the brief's host on a synthetic capture with the candidate's line formats. Its
open-line parse read `e_network=wlan0 e_refresh_ms=10000`. If a count comes back empty rather than a
number, the pattern is wrong: fix it, say so in Setup notes, and keep going.

`e_main_vss` counts every main-display `Video Sink Stopped -> ` line, the forced and the normal one.
`RTP_PUSH` builds no aux decoder, so every `Throughput over` line is the main decoder's.

## 6. The deciding lines

Every head unit string below was checked with `grep -F -r` in `app/src/main` at `8d4af793b`. All are
INFO, WARN or ERROR, so `log-level` 2 carries them. `...` marks a value the app fills in. Files:
`$OUT/<RUN>-hu.txt` (head unit) and `$OUT/<RUN>-phone.txt` (phone). Windows are marker pairs.

| Line (head unit) | File in the tree | Means |
|---|---|---|
| `AutomationReceiver: ` | `automation/AutomationReceiver.kt` | a verb landed. A step with no such line is void, not a FAIL |
| `WifiLauncher: Initializing WiFi Mode: NATIVE` | `connection/wifi/WifiLauncherManager.kt` | preflight: Native path |
| `Projection backend: viewMode=TEXTURE ` | `aap/AapProjectionActivity.kt` | preflight: view backend |
| `Media Sink Setup Request: 3 on channel VIDEO` at line end | `aap/AapControl.kt` | preflight: the main sink, H.264 |
| `SSL handshake complete` | `aap/AapSslContext.kt` | a session formed. One per session at INFO |
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

**Phone strings** (`$OUT/<RUN>-phone.txt`, by `phone_grades`). They are Gearhead's, not ours, and
they drift between builds.

| Phone line | Graded | Means |
|---|---|---|
| `GH.` | capture gate, 1 or more | the phone capture works |
| `AutomationMarker: ` | window gate, 1 or more | the markers reached the phone log |
| `Critical error` | 0 in `<RUN>-up..<RUN>-end` | Android Auto ended the session over something we sent |
| `No input for display` | 0 | the aux display has no input |
| `Video focus gained before configurations received` | 0 | a focus gain before the aux Config |

The exit teardown makes the phone log `Critical error` lines after `<RUN>-end`. The window stops at
`<RUN>-end`, so they do not count (memory: about 1.1 s after each Exit). Report
`critical_error_file` beside the windowed count.

**PC strings** (not in our tree): `GRADE `, `GRADER listening=`, `PROXY listening=`, `PROXY burst `,
`PROXY seen=`, `PATCH_OK `, `PATCH_FAIL `, `S1ASK `, `S1PAIR `, `S2GRADE `, `S2BUCKETS `, `S5RUN `,
`S5 `, `FPS30 `, `PROBE datagrams=`, `PROBE_SENT`.

## 7. Runs

Start each run script with `nohup bash <script> > <script>.log 2>&1 &` and watch its log for
`script exit=`, as round 2 did. Run them in order, one at a time. Thermal limits are those in
`rig-quirks/topics/tooling.md`: `rig_thermal.sh wait 70` before the build, `wait 75` before a run
(`bringup` gates on 75C), and a run that cannot start below 75C after two waits is UNTESTABLE (host
thermal), and so is every run after it.

### Pre-flight (one batch, before R0)

**`p_pre.sh`**
```bash
# p_pre.sh : P1 to P5 in one pass. Writes env.sh. Run it from the scripts folder.
cd "$(dirname "$0")"; source ~/Coding/StudioProjects/rig-toolkit/rig_devices.sh
source ./ssr_lib.sh; source ./rtp_lib.sh; source ./r3_lib.sh
echo "== P1 units"; adb devices -l
for s in ZY22GC3BM4; do adb devices | grep -q "^$s" && { adb -s $s shell svc bluetooth disable; sleep 0.3; adb -s $s shell svc wifi disable; sleep 0.3; }; done
for s in 30041c35642d2200 0123456789ABCDEF CNU350BGBJ; do adb devices | grep -q "^$s" && { adb -s $s shell am force-stop com.andrerinas.headunitrevived; sleep 0.3; }; done
$RIG_TOOLKIT/rig_preflight.sh D_HU:wifi,bt D_POCO:wifi,bt
echo "== P2 D-HU"
adb -s $HU shell stat -c '%U:%G' /data/data/$PKG/shared_prefs; sleep 0.3
adb -s $HU shell cat /data/data/$PKG/shared_prefs/settings.xml > settings-backup-HU.xml; md5sum settings-backup-HU.xml
grep -o -E '(use-aac-audio|audio-latency-multiplier|audio-queue-capacity|enable-audio-sink)[^/]*' settings-backup-HU.xml
grep -o -E '(video-profile-starvation-cap|wifi-direct-last-identity-verdict)[^/]*' settings-backup-HU.xml
grep -o -E 'aux-[a-z-]*|preferred-display-[a-z]*' settings-backup-HU.xml | sort -u
HUIP_P=$(hu_ip); echo "HUIP_P=$HUIP_P"; HUIP=${HUIP_P%/*}
echo "== P3 D-POCO"
adb -s $PH shell dumpsys package com.google.android.projection.gearhead | grep -a -m2 -E "versionName|versionCode"; sleep 0.3
adb -s $PH shell pm path com.google.android.apps.maps; sleep 0.3
POCO_MAC=$(adb -s $PH shell settings get secure bluetooth_address | tr -d '\r' | tr a-f A-F); echo "POCO_MAC=$POCO_MAC"; sleep 0.3
adb -s $PH shell dumpsys window | grep -a mCurrentFocus; sleep 0.3
adb -s $PH shell input keyevent KEYCODE_HOME; sleep 0.3
echo "== P4 PC tools"; python3 --version; command -v ffmpeg; ffmpeg -version 2>/dev/null | head -1
echo "== P5 route from D-HU to the PC"
HOSTIP=$(ip -4 route get "$HUIP" | grep -o 'src [0-9.]*' | cut -d' ' -f2); echo "HOSTIP=$HOSTIP"
adb -s $HU shell ping -c 3 -W 2 $HOSTIP | tail -2; sleep 0.3
adb -s $HU push udp_probe.sh /data/local/tmp/udp_probe.sh >/dev/null; sleep 0.3
python3 -I rtp_recv.py --probe 5004 25 > "$OUT/P5-probe.txt" & PRB=$!; sleep 2
adb -s $HU shell sh /data/local/tmp/udp_probe.sh $HOSTIP 5004; wait $PRB; cat "$OUT/P5-probe.txt"
printf 'v=0\no=- 0 0 IN IP4 %s\ns=Open Headunit second screen\nc=IN IP4 %s\nt=0 0\nm=video 5004 RTP/AVP 96\na=rtpmap:96 H264/90000\na=fmtp:96 packetization-mode=1' "$HOSTIP" "$HOSTIP" > stream.sdp
cat stream.sdp; echo; wc -l stream.sdp
cat > env.sh <<EOF
POCO_MAC=$POCO_MAC; HOSTIP=$HOSTIP; HUIP=$HUIP
BASE="int:wifi-connection-mode=3 set:native-poke-bt-macs=$POCO_MAC bool:native-poke-all-paired=false int:native-driver-selection-mode=0 int:onboarding-version=2 int:log-level=2 str:video-codec=H.264 int:resolutionId=3 int:view-mode=1 bool:force-software-decoding=false del:software-video-decoder del:debug-video-fault-injection del:video-profile-starvation-cap int:night-mode=1"
AUXREST="int:aux-rtp-port=5004 int:aux-network-size=1 del:aux-network-port del:aux-display-role del:aux-display-content del:aux-display-id del:preferred-display-mode del:preferred-display-id"
K10="bool:aux-display-enabled=true str:aux-output=RTP_PUSH str:aux-rtp-destination=$HOSTIP int:aux-rtp-refresh-seconds=10 \$AUXREST"
K0="bool:aux-display-enabled=true str:aux-output=RTP_PUSH str:aux-rtp-destination=$HOSTIP int:aux-rtp-refresh-seconds=0 \$AUXREST"
EOF
source ./env.sh; for v in POCO_MAC HOSTIP HUIP BASE K10 K0; do echo "$v=${!v}"; done
echo "script exit=0"
```

Check these in the log, and record each one in Setup notes:

- **P1.** `27870808938846` (D-HU) and `4f4027e9` (D-POCO) are listed. `rig_preflight.sh` prints
  `PREFLIGHT OK`. If it prints `PREFLIGHT FAILED`, stop and tell the operator.
- **P2.** Record the `stat` owner, the backup md5, the four audio keys, the two state keys and any
  `aux-*` keys. `HUIP_P` MUST be an address such as `192.168.1.9/24`. If it is empty, put "join D-HU
  to the house WiFi" in P6.
- **P3.** Record the Gearhead version. `pm path` for Maps MUST print a path. `POCO_MAC` MUST be a real
  address, not empty and not `02:00:00:00:00:00`.
- **P4.** If `ffmpeg` is absent, put its install in P6.
- **P5.** `PROBE datagrams=` 1 or more with `from=` holding `$HUIP`: `UDP_PROBE=ok`. `datagrams=0`
  after `PROBE_SENT`: `UDP_PROBE=blocked`, so put the firewall rule in P6. `stream.sdp` has 8 lines
  (7 newlines).
- **env.sh.** No value printed at the end is empty, and `K10` and `K0` each end with the
  `aux-rtp-port=5004` keys.

**P6. The one ask, only if needed.** Ask the operator in one message, and only for these:

- any rig unit that is powered on but not on adb (P1): switch its Bluetooth off or power it off;
- if P2 found no `wlan0` address on D-HU: join D-HU to the house WiFi;
- if P4 found no `ffmpeg`: may the executor run `sudo apt-get install -y ffmpeg` on the PC?
- if P5 read `UDP_PROBE=blocked`: may the executor open UDP 5004 and 5005 on the PC's firewall for
  D-HU's subnet? On `ufw` that is `sudo ufw allow proto udp from ${HUIP%.*}.0/24 to any port 5004:5005`.
  Record the exact rule.

If none of these applies, skip P6. After the answers, run `p_pre.sh` again.

### R0. Build, install and identity

**`r0.sh`**
```bash
# r0.sh : fetch, build, install and prove the candidate. Run it from the scripts folder.
cd "$(dirname "$0")"; source ~/Coding/StudioProjects/rig-toolkit/rig_devices.sh
source ./ssr_lib.sh; source ./rtp_lib.sh; source ./r3_lib.sh
SHA=8d4af793b5e87a7b44f1e0ac01976a8db21766f6; TREE=153794946844e3a07135c9ad9b505bdcee096d4f
APP=~/Coding/StudioProjects/headunit-revived
git -C $APP fetch fork fix/rtp-push-recovery
echo "FETCHED=$(git -C $APP rev-parse FETCH_HEAD)"                     # MUST equal $SHA
WT=$($RIG_TOOLKIT/wt-new.sh second-screen-rtp-push candidate $SHA | tail -1); echo "WT=$WT"
echo "HEAD=$(git -C $WT rev-parse HEAD) TREEREAD=$(git -C $WT rev-parse HEAD^{tree})"   # MUST equal $SHA and $TREE
python3 -I patch_tools.py $WT/tools/second-screen/rtp-harness              # MUST print two PATCH_OK lines
$RIG_TOOLKIT/rig_thermal.sh wait 70 || $RIG_TOOLKIT/rig_thermal.sh wait 70
HUR_DIR=$WT MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh; echo "BUILD_RC=$?"
mkdir -p $RIG_DATA/apks/second-screen-rtp-push-round3
APK=$(ls -t $RIG_DATA/apks/*.apk | head -1); cp "$APK" $RIG_DATA/apks/second-screen-rtp-push-round3/cand.apk
CAND=$RIG_DATA/apks/second-screen-rtp-push-round3/cand.apk; md5sum $CAND
adb -s $HU shell am force-stop $PKG; sleep 0.3
adb -s $HU install -r -d $CAND; sleep 0.3
$RIG_TOOLKIT/apk_identity.sh $CAND $HU; echo "IDENTITY_RC=$?"
adb -s $HU pull $(adb -s $HU shell pm path $PKG | cut -d: -f2 | tr -d '\r') "$OUT/live.apk" >/dev/null; md5sum "$OUT/live.apk"
send ACTION_QUERY_STATE
echo "DEX_REFRESH=$(unzip -p "$OUT/live.apk" 'classes*.dex' | strings | grep -c 'secondscreen/rtp/RtpRefreshPolicy')"
echo "DEX_PACING=$(unzip -p "$OUT/live.apk" 'classes*.dex' | strings | grep -c 'secondscreen/rtp/RtpPacingPolicy')"
echo "script exit=0"
```

**PASS:** all of these.

- `FETCHED`, `HEAD` and `TREEREAD` equal the SHA and the tree in section 1.
- Two `PATCH_OK` lines with the md5s in section 5.
- `BUILD_RC=0`, `IDENTITY_RC=0`, and the `live.apk` md5 equals the `cand.apk` md5.
- The `data=` reply of `ACTION_QUERY_STATE` carries commit `8d4af793b5e8`. A `-dirty` suffix from
  `.WORKTREE-INFO` alone is fine.
- `DEX_REFRESH` 1 or more and `DEX_PACING` 1 or more. Both classes are new in `d73c45df1`, so no older
  build carries them.

**FAIL stops the round:** a SHA or tree mismatch, a `PATCH_FAIL`, a build failure, or a DEX count of
0. Report the lines. `hu_keys` writes and checks every key on every run, so an install that wipes
`settings.xml` does no harm.

### S12. S1 and S2 in one session (8 min)

S1 and S2 share one session. S1 ends when the grader ends. S2 starts at once on the same live
stream, so the FFmpeg join is a real late join, about 185 s after the push opened.

**`run_s12.sh`**
```bash
# run_s12.sh : S1 (the phone answers the refresh) then S2 (a late receiver), one session, refresh 10 s.
cd "$(dirname "$0")"; source ~/Coding/StudioProjects/rig-toolkit/rig_devices.sh
source ./ssr_lib.sh; source ./rtp_lib.sh; source ./r3_lib.sh; source ./env.sh
RUN=S12; : > "$OUT/S12.pcmarks"; : > "$OUT/S2.events"
th_wait 75 || th_wait 75 || { echo "HOST_TOO_HOT S12"; echo "script exit=3"; exit 0; }
grade_start S1 0.0.0.0 $RPORT 180
bringup S12 $K10; rc=$?
if [ $rc = 1 ]; then run_end; keep_attempt S12; rx_kill
  for f in S1.grade S1.idr S1.grade.json S12.pcmarks; do [ -f "$OUT/$f" ] && mv "$OUT/$f" "$OUT/a1-$f"; done
  RUN=S12; : > "$OUT/S12.pcmarks"; grade_start S1 0.0.0.0 $RPORT 180; bringup S12 $K10; rc=$?; fi
echo "S12_BRINGUP rc=$rc"                    # 2 = Critical error with no frame: do not retry; 3 = host too hot
if [ $rc != 0 ]; then rx_kill; run_end; echo "script exit=$rc"; exit 0; fi
preflight
echo "HUIP_SESSION=$(hu_ip)" | tee "$OUT/S12.huip"
# S1: the grader holds UDP 5004 for 180 s from the first datagram
ph_nav "Parque+Principal+Marinilla"; markt S1-nav
for i in 1 2 3 4 5; do sleep 30; markt S1-m$i; done
grade_wait S1 120 | tee "$OUT/S1.gradeline"
markt S1-gend
echo "maps_notes=$(maps_notes)" | tee -a "$OUT/S12.notes"
# S2: FFmpeg joins the live stream once a report shows packets
L=$(nl); if waitfor 12 'RTP push sent [1-9][0-9]* packets' $L; then echo "S2_TRAFFIC=yes"; else echo "S2_TRAFFIC=no"; fi | tee -a "$OUT/S12.notes"
ff_s2; markt S2-join
hu_shot S2-main1
sleep 3; ph_nav "Parque+Principal+Rionegro"; echo "reroute $(date +%s.%N)" >> "$OUT/S2.events"; markt S2-r1
sleep 8; hu_shot S2-main2
sleep 8; hu_shot S2-main3
sleep 4; ph_nav "Parque+Principal+Marinilla"; echo "reroute $(date +%s.%N)" >> "$OUT/S2.events"; markt S2-r2
sleep 10; hu_shot S2-main4
sleep 10; hu_shot S2-main5
ff_s2_wait 50 | tee "$OUT/S2.ffmpeg"
markt S2-end
echo "maps_notes=$(maps_notes)" | tee -a "$OUT/S12.notes"
run_end
python3 -I fps30.py "$CAP" S12-up | tee "$OUT/S12.fps30"
python3 -I s1_pair.py "$OUT/S12-hu.txt" "$OUT/S12-phone.txt" "$OUT/S12.pcmarks" "$OUT/S1.idr" S12-launch S1-gend 3.0 | tee "$OUT/S1.pair"
python3 -I s2_grade.py "$OUT/S2.framemd5" "$OUT/S2-snap" "$OUT/S2.events" | tee "$OUT/S2.grade"
md5sum "$OUT"/S2-snap*.png "$OUT"/S2-main*.png | tee "$OUT/S2.md5"
push_counts S12 S12-launch S1-gend | tee "$OUT/S1.counts"
push_counts S12 S2-join S2-end | tee "$OUT/S2.counts"
push_counts S12 S12-up S12-end | tee "$OUT/S12.counts"
push_counts S12 S12-launch S12-end | tee "$OUT/S12-all.counts"
push_counts S12 S12-end S12-closed | tee "$OUT/S12-close.counts"
phone_grades S12 S12-up S12-end | tee "$OUT/S12.phone"
phone_grades S12 S12-launch S12-up | tee "$OUT/S12.phone-bringup"
echo "script exit=0"
```

The screenshots are sequential adb calls with `sleep 0.3` after each one, so house rule 8 holds.
FFmpeg and the grader talk to D-HU over the WiFi, not over adb.

#### S1. The phone answers the refresh: THE POINT OF THE ROUND

Inputs: `S1.pair` (`S1PAIR` and the `S1ASK` rows), `S1.gradeline` (`GRADE`), `S1.counts`
(window `S12-launch..S1-gend`), `S12.preflight`, `S12.phone`.

Conditions:

- **A1, setup.** `S12.preflight` has `mode_native`, `view_texture` and `main_sink_h264` 1 or more,
  `hotspot_lines` 0, `phone_gh_lines` 1 or more. `S1.counts`: `e_announce` 1, `e_unavail` 0, `e_open`
  1, `e_outopen` 1, `e_refresh_ms` 10000, `e_network` `wlan0`.
- **A2, the stream reached the grader.** `GRADE` `packets` 500 or more and `span_s` 150 or more.
- **A3, the refresh fired and the cycle went out.** `S1PAIR` `asks` 5 or more, and `cycled` is
  `asks` or `asks - 1`. `S1.counts`: `e_kf_early` 0, `e_fail_lines` 0.
- **A4, the phone answered.** `answered_pct` 80 or more, and `GRADE` `idr_gap_max_s` 15.0 or less.
- **A5, the clocks paired.** `offset_n` 3 or more and `offset_spread_s` 1.5 or less.

Report, no verdict of their own: every `S1ASK` row; `lat_med_s` and `lat_max_s`; `stops`, `starts`;
`S1.counts` `e_aux_stop_cycle`, `e_aux_stop_other`, `e_aux_start`, `e_aux_released`, `e_refreshes`,
`e_pauses`, `e_keyframes`, `e_kf_lines`; `GRADE` `idrs`, `lost`, `first_picture_s`; and the phone
lines that `S1ASK` counted for the first three requests, quoted from `S12-phone.txt`.

**Outcomes.** Grade in this order and stop at the first that matches.

1. **Setup failure:** A1 is not met. Fix the keys and re-run S12 once.
2. **INCONCLUSIVE (rig):** `S12_BRINGUP rc` is 1 after the retry, or 3, or A2 is not met, or
   `GRADE missing`. Report the numbers.
3. **INCONCLUSIVE (not reached):** `asks` is below 5 and `idr_gap_max_s` is 15.0 or less. The phone
   sent IDRs often enough by itself, so the refresh had nothing to do.
4. **FAIL-WIRING:** `asks` is below 5 with `idr_gap_max_s` above 15.0, or `cycled` is below
   `asks - 1`, or `e_kf_early` is 1 or more. The refresh or the cycle did not run as designed. Quote
   the `S1ASK` rows and the first 3 lines around a missing cycle.
5. **Clock fallback:** A5 is not met. Then grade A4 with `hu_kf_next2` in place of `answered`:
   `hu_kf_next2` 80% of `asks` or more, and `idr_gap_max_s` 15.0 or less. Write "graded on the head
   unit reports" beside the verdict. Then go on to 6 or 7.
6. **PASS:** A1 to A4 are met (A4 by the fallback when 5 applied).
7. **FAIL-PHONE:** A3 is met and A4 is not. Gearhead does not answer an aux focus cycle with an aux
   IDR. This is a real finding, not a rig fault. Quote `stops`, `starts`, the `S1ASK` rows and the
   `GRADE` line. If `stops` is 0, write "the phone ignored the focus release".

**The outcome for INCONCLUSIVE:** re-run S12 once. If S1 is INCONCLUSIVE again, record both sets of
numbers, grade S2 to S5 as written, and carry on. Do not invent another lever. The coding side
decides the next step from the numbers.

A PASS cannot come from a refresh that did nothing. Without an answer, the phone's IDRs come every
60 s (round 2, and S4 here), so `idr_gap_max_s` would be near 60 and `answered_pct` near 0.

#### S2. A late receiver gets a moving picture

Inputs: `S2.grade` (`S2GRADE`, `S2BUCKETS`), `S2.ffmpeg` (exit and error lines), `S12.notes`
(`S2_TRAFFIC`, `maps_notes`), `S2.counts` (window `S2-join..S2-end`), and S1's outcome.

Conditions:

- **B1, the join came after traffic.** `S2_TRAFFIC=yes`.
- **B2, a picture in 20 s.** `first_frame_after_join_s` 20.0 or less.
- **B3, the picture changes.** `decode_span_s` 30.0 or more, `distinct` 30 or more, `snap_distinct` 2
  or more, `r1_new_frames` 1 or more and `r2_new_frames` 1 or more.
- **B4, the map moved.** `r1_main_changed=yes` or `r2_main_changed=yes`.

Report: `frames`, `S2BUCKETS`, `snaps`, `main_distinct`, the `rc=` in `S2.ffmpeg` and its error lines,
the two `maps_notes` counts, and `S2.counts` `e_asks`, `e_keyframes` and `e_fail_lines`. Put the
first, middle and last PNG snapshot in the evidence zip.

**Outcomes.** Grade in this order and stop at the first that matches.

1. **INCONCLUSIVE (no stream):** B1 is not met, or S1 was INCONCLUSIVE (rig).
2. **Report only:** S1 is FAIL-PHONE. Without an answer, a late receiver waits for the phone's 60 s
   GOP, so S2 measures only that. Report the numbers with no verdict.
3. **FAIL-LATE:** `frames` is 0, or B2 is not met. Quote FFmpeg's error lines.
4. **FAIL-FROZEN:** B4 is met, and the route change whose `rN_main_changed` is `yes` has
   `rN_new_frames` 0. The phone's map moved and the second display did not. This is the reported
   fault.
5. **PASS:** B1 to B4 are met.
6. **INCONCLUSIVE (map idle):** B3 is not met and B4 is not met. Nothing proves the map moved.
   `rN_main_changed=na` counts as not met.
7. **FAIL:** anything else. Name each condition that is not met, with its numbers.

**The outcome for INCONCLUSIVE:** S2 is not re-run alone. If S12 is re-run for S1, S2 runs again
inside it. Otherwise record the numbers and carry on.

### S3. Recovery from loss on the real network (5 min)

S4 runs the same script with the refresh off, so S3 and S4 share one script.

**`run_loss.sh`**
```bash
# run_loss.sh <RUN> <keys var> : the push through the lossy proxy (bursts at 30, 60, 90 s) to the strict grader
cd "$(dirname "$0")"; source ~/Coding/StudioProjects/rig-toolkit/rig_devices.sh
source ./ssr_lib.sh; source ./rtp_lib.sh; source ./r3_lib.sh; source ./env.sh
R=$1; KEYS=${!2}; RUN=$R; : > "$OUT/$R.pcmarks"
th_wait 75 || th_wait 75 || { echo "HOST_TOO_HOT $R"; echo "script exit=3"; exit 0; }
proxy_start $R; grade_start $R 127.0.0.1 $GPORT 150
bringup $R $KEYS; rc=$?
if [ $rc = 1 ]; then run_end; keep_attempt $R; rx_kill
  for f in $R.grade $R.idr $R.grade.json $R.proxy; do [ -f "$OUT/$f" ] && mv "$OUT/$f" "$OUT/a1-$f"; done
  RUN=$R; proxy_start $R; grade_start $R 127.0.0.1 $GPORT 150; bringup $R $KEYS; rc=$?; fi
echo "${R}_BRINGUP rc=$rc"
if [ $rc != 0 ]; then rx_kill; run_end; echo "script exit=$rc"; exit 0; fi
preflight
ph_nav "Parque+Principal+Marinilla"; markt $R-nav
grade_wait $R 260 | tee "$OUT/$R.gradeline"
markt $R-gend
run_end
proxy_wait $R 30 | tee "$OUT/$R.proxyline"
python3 -I fps30.py "$CAP" $R-up | tee "$OUT/$R.fps30"
push_counts $R $R-launch $R-end | tee "$OUT/$R.counts"
push_counts $R $R-up $R-end | tee "$OUT/$R-up.counts"
push_counts $R $R-end $R-closed | tee "$OUT/$R-close.counts"
phone_grades $R $R-up $R-end | tee "$OUT/$R.phone"
phone_grades $R $R-launch $R-up | tee "$OUT/$R.phone-bringup"
echo "script exit=0"
```

Run it as `nohup bash run_loss.sh S3 K10 > run_s3.log 2>&1 &`.

Inputs: `S3.gradeline` (`GRADE`), `S3.proxyline` (`PROXY` lines), `S3.counts` (window
`S3-launch..S3-end`), `S3.preflight`.

- **C1, setup.** As A1, on `S3.counts` and `S3.preflight`: `e_open` 1, `e_refresh_ms` 10000,
  `e_network` `wlan0`, `e_unavail` 0.
- **C2, the bursts happened.** 3 `PROXY burst` lines, `PROXY` `dropped` 15 or more, `GRADE` `lost`
  15 or more, `packets` 500 or more, `span_s` 140 or more.
- **C3, recovery.** `GRADE` `unrecovered=0` and `broken_max_s` 12.5 or less.
- **C4, the refresh did it.** `S3.counts` `e_asks` 3 or more and `e_fail_lines` 0.

Report: `broken_n`, `broken_total_s`, `idr_gap_max_s`, `first_picture_s`, the burst times, and
`e_refreshes`, `e_pauses`.

**Outcomes.** In this order:

1. **Setup failure:** C1 is not met. Fix the keys and re-run once.
2. **INCONCLUSIVE (rig):** `S3_BRINGUP rc` is 1 after the retry, or 3, or C2 is not met. Re-run once.
3. **INCONCLUSIVE (control):** S4 is NOT REPRODUCED (below). The rig did not show the fault, so a
   recovery here proves nothing. Grade S3 after S4.
4. **PASS:** C1 to C4 are met.
5. **FAIL:** anything else. If S1 is FAIL-PHONE, write "follows S1" beside it.

### S4. The control: S3 with the refresh off (5 min)

Run `nohup bash run_loss.sh S4 K0 > run_s4.log 2>&1 &`. Inputs as S3, with `S4` in each name.

- **D1, setup.** As C1, but `e_refresh_ms` 0.
- **D2, the bursts happened.** As C2.
- **D3, the setting turns the refresh off.** `S4.counts` `e_asks` 0 and `e_cyc_push` 0.
- **D4, the fault shows.** `GRADE` `unrecovered=1`, or `broken_max_s` 25.0 or more.

**Outcomes.** In this order:

1. **Setup failure:** D1 is not met. Fix the keys and re-run once.
2. **INCONCLUSIVE (rig):** a bring-up failed twice, or D2 is not met. Re-run once.
3. **FAIL:** D3 is not met. Refresh 0 must mean no request.
4. **REPRODUCED (PASS):** D3 and D4 are met. The rig shows the reported fault with the refresh off.
5. **NOT REPRODUCED:** D3 is met and D4 is not. Report `idr_gap_max_s` and `broken_max_s`. S3 becomes
   INCONCLUSIVE (control).

Why D4 must happen when the code behaves as round 2 measured: the phone's IDRs come every 60 s, and
the bursts come 30 s apart. So at least one burst lands in the first half of a GOP, and the picture
stays broken for at least about 30 s.

### S5. No cost to the session (graded after S4)

**`s5_grade.sh`**
```bash
# s5_grade.sh : S5 from the files S12, S3 and S4 left. "up" = <RUN>-up..<RUN>-end, "all" = <RUN>-launch..<RUN>-end
cd "$(dirname "$0")"; source ./ssr_lib.sh
python3 -I s5.py "$OUT" | tee "$OUT/S5.grade"
for r in S12 S3 S4; do
  up=$OUT/$r-up.counts; all=$OUT/$r.counts; [ $r = S12 ] && { up=$OUT/S12.counts; all=$OUT/S12-all.counts; }
  echo "== $r up: $(grep -a -E '^e_(main|fail|errors|stopped)' "$up" | tr '\n' ' ')"
  echo "== $r all: $(grep -a -E '^e_fail_lines' "$all" | tr '\n' ' ')"
  echo "== $r phone: $(grep -a -E '^(phone_markers|win\|Critical error|win\|No input)' "$OUT/$r.phone" | tr '\n' ' ')"
done | tee -a "$OUT/S5.grade"
echo "script exit=0"
```

The `e_fail_lines` line of `push_counts` also carries `e_ssl`, `e_destroy`, `e_errors` and `e_shed`,
and the `e_stopped` line carries `e_auxdec`.

Conditions:

- **E1, the main picture.** `S5` `fps_ok=yes` (`F10`, the mean of the S12 and S3 medians, is not
  lower than `F0 - 3`, with `F0` the S4 median) and `rendered_ok=yes`.
- **E2, the session held.** In each run's `all` line: `e_ssl` 1 and `e_destroy` 0. In each run's
  `up` line: `e_errors` 0, `e_shed` 0, `e_auxdec` 0, `e_fail_lines` 0.
- **E3, no main focus cycle.** In the `up` windows of S12 and S3: `e_main_stop` 0, `e_main_vss` 0,
  `e_main_retake` 0.
- **E4, the phone.** In each run: `win|Critical error=0` and `win|No input for display=0` in
  `<RUN>-up..<RUN>-end`, and `phone_markers_in_window` 1 or more.

Report: `e_main_start` and `e_main_setup` in the `up` windows. 0 means the phone did not lay out its
main screen again on an aux cycle. Report each run's `fed` median beside its `rendered` median.

**Outcomes.** In this order:

1. **INCONCLUSIVE:** `S5 verdict_input=missing` (a run gave no `FPS30` line). Name the run.
2. **FAIL-B, the refresh breaks the session:** E2, E3 or E4 is not met. Quote the first offending
   line, and the phone's `Critical error` line if there is one.
3. **FAIL-COST:** E1 is not met. If `fed10` fell by as much as `F10`, the phone sent fewer frames.
4. **PASS:** E1 to E4 are met.

### Cleanup (always, last)

**`cleanup.sh`**
```bash
# cleanup.sh : stops what the round started and restores D-HU's settings
cd "$(dirname "$0")"; source ~/Coding/StudioProjects/rig-toolkit/rig_devices.sh; source ./ssr_lib.sh
pgrep -af 'rtp_grade_rig.py|lossy_proxy_rig.py' ; pkill -f 'rtp_grade_rig.py'; pkill -f 'lossy_proxy_rig.py'; pkill -f '^ffmpeg -nostdin -hide_banner'; true
adb -s $HU shell rm /data/local/tmp/udp_probe.sh; sleep 0.3
adb -s $HU shell am force-stop $PKG; sleep 0.3
adb -s $HU push settings-backup-HU.xml /data/local/tmp/ssr-settings.xml >/dev/null; sleep 0.3
adb -s $HU shell cp /data/local/tmp/ssr-settings.xml /data/data/$PKG/shared_prefs/settings.xml; sleep 0.3
adb -s $HU shell chown $(adb -s $HU shell stat -c %u:%g /data/data/$PKG | tr -d '\r') /data/data/$PKG/shared_prefs/settings.xml; sleep 0.3
adb -s $HU shell chmod 660 /data/data/$PKG/shared_prefs/settings.xml; sleep 0.3
adb -s $HU shell cat /data/data/$PKG/shared_prefs/settings.xml | diff - settings-backup-HU.xml && echo RESTORED
$RIG_TOOLKIT/rig_cleanup.sh list
echo "script exit=0"
```

The `pkill -f` patterns name the rig copies and an anchored FFmpeg command line, so they cannot match
the host shell (round 2's cleanup slip). Ask the operator to remove the PC firewall rule for UDP 5004
and 5005, and record the command they used. Restore D-POCO's radios with `ph_on` if a run ended with
them off. D-POCO may still be navigating: send `adb -s 4f4027e9 shell input keyevent KEYCODE_HOME`.

### Stop rule

- Each bring-up gets at most 2 attempts. A bring-up that returns 2 is not retried.
- S12 is re-run at most once (S1 INCONCLUSIVE or setup failure). S3 and S4 are each re-run at most
  once. So the round has at most 6 sessions.
- A preflight FAIL is re-run once. A second FAIL makes that run UNTESTABLE.
- R0 FAIL stops the round.
- The host gate: a run that cannot start below 75C after two waits is UNTESTABLE (host thermal), and
  so is every run after it.
- No Gradle build runs after R0.

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

```decisive-strings
AutomationReceiver: 
AutomationMarker: 
WifiLauncher: Initializing WiFi Mode: 
Projection backend: viewMode=
Media Sink Setup Request: 
SSL handshake complete
AapService destroying
Throughput over 
rendered=
fed=
dropped=
HotspotManager:
SoftApCredentials:
Setting hotspot enabled=
[ServiceDiscovery] Announcing an auxiliary display on 
SecondScreen: the 
 output is not available, so one display is announced
 output is open
SecondScreen: could not start the 
AapTransport: the auxiliary display's video lane is open
SecondScreen: RTP push to 
 open (network=
, refresh=
SecondScreen: RTP push asking for a keyframe, none sent for 
RTP push sent 
 keyframes carried their own SPS and PPS
 refreshes asked, 
 pacing pauses
SecondScreen: RTP push send failed (
SecondScreen: RTP push stopped
AapTransport: cycling the auxiliary display's video focus for a keyframe (
the second screen asked for one
AapTransport: no auxiliary keyframe cycle before its setup ()
Media Sink Stop Request: 
Auxiliary Video Sink Stopped -> Ignored (Forced Keyframe Request)
Auxiliary Video Sink Stopped -> the main session continues
Video Sink Stopped -> 
retaking video focus to complete the keyframe cycle
Media Start Request 
The phone released the second display's video. The main session continues.
Error processing video message
 messages behind, shedding
Decoder stopped: 
CommManager: doDisconnect (second screen)
```

Phone, PC and DEX strings, not in our tree, so not checkable against it:

```decisive-strings-external
GH.
Critical error
No input for display
Video focus gained before configurations received
GRADE 
GRADER listening=
PROXY listening=
PROXY burst 
PROXY seen=
PATCH_OK 
PATCH_FAIL 
S1ASK 
S1PAIR 
S2GRADE 
S2BUCKETS 
S5RUN 
FPS30 
PROBE datagrams=
PROBE_SENT
secondscreen/rtp/RtpRefreshPolicy
secondscreen/rtp/RtpPacingPolicy
```
