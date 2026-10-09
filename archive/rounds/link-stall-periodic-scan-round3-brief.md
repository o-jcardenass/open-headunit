# link-stall-periodic-scan — round 3 brief: the codec A/B

**Candidate:** `fork/fix/video-stack` @ `9a1257ca` — **the same APK for every run.** No baseline
build, no A/B install. The only thing that changes between runs is one string in `settings.xml`.

**Supersedes** the "switch modes later" next-step recorded in this thread's README row. Round 6 R4/R5
answered the discovery-loop question: the loop never arms on a plain native session, and through a
deliberate mode-2→mode-3 switch it stops at the phone's WiFi join and never overlapped a live one. It
is a bounded defect and it is not what #839 and #824 are. Do not spend this round on it.

---

## 1. Why this round exists

Reading all five of #839's reporter captures against one instrument moved the whole thread. The
controlling variable is **which video codec the phone was told to encode**, not which version of the
app was installed.

`Media Sink Setup Request: N on channel VIDEO` is the phone's own request, prints at **INFO**, and its
enum is stable across every release (`app/src/main/proto/media.proto:28,32` — `3` = H.264 BP,
`7` = H.265). On his unit (`ac8227l`, Android 8.1, 1024x600, negotiated 1280x720):

| Capture | Ver | Link | Sink codec | Audio underruns | Throughput `fed` |
|---|---|---|---|---|---|
| 08-16 08:46 | 3.2.4 | WiFi Direct | **7 = H.265** | **0** (0.00/min) | 18–53 fps, 12/12 windows |
| 08-17 03:00 | 3.2.4 | **USB** | **7 = H.265** | **0** (0.00/min) | 50–59 fps |
| 08-16 22:56 | 3.2.4 | WiFi Direct | **3 = H.264** | 35 (**8.42/min**) | 0–20 fps, 23 windows |
| 08-19 | 3.2.5exp | WiFi Direct | **3 = H.264** | 15 (**10.45/min**) | 0–34 fps |
| 08-20 | **3.1.1** | WiFi Direct | **3 = H.264** | 13 (2.00/min) | n/a |

The setting flipped under him because `SystemOptimizer.kt:106`
(`hasH265 && panelCeil.width > 1920`) writes `"H.264"` on every sub-1080p panel at first-time setup,
where v.3.1.1 `:92` wrote `"H.265"` whenever HEVC was present at all. At 720p that setting is the
*whole* decision: `ServiceDiscoveryResponse.kt:63-81` announces exactly one codec and its `"Auto"`
branch needs `_3840x2160`.

**The mechanism is the part that is not established.** The obvious story is "H.264 is roughly twice
the bits, on a link that is already marginal" — but measured off his VERBOSE 3.1.1 capture, H.264 at
720p30 was only **1.08 Mbit/s** of video (audio was larger, at 1.33 Mbit/s). That is not obviously
enough to saturate anything. So the round's job is to **measure the two codecs' actual wire cost on
identical content**, which no capture we hold can do because none of them A/B the codec.

---

## 2. Read this before planning the runs

**This rig has already run H.264 clean, twice.** Round 6 R4: 0 stalls > 1.2 s, 0.0 % dead time, 24965
RECV lines over 322.8 s. R5: 0 stalls, 0.0 % dead, 31966 over 415.9 s. Both on `video-codec=H.264`.

So **do not expect the reporter's fault to reproduce here, in either arm.** A 5 GHz P2P group on
Android 14 is not a 2.4 GHz group on Android 8.1, and this round is not designed to reproduce the
outage. Two consequences, both binding:

- **The primary metric is bitrate, not stalls.** It is decidable on this rig and it either supports
  or kills the bandwidth explanation.
- **Zero stalls in both arms is the expected result and is a PASS of R1/R2, not a FAIL.** Report it
  as a number and move on. Only a *difference* between the arms is interesting.

**The rig's own baseline forces the software HEVC decoder** (`force-software-decoding=true`,
`software-video-decoder=1`). That must not be left on: it would make the H.265 arm slow for a reason
that has nothing to do with the link. Both runs set `force-software-decoding=false`. R0 checks
whether this unit can actually decode HEVC in hardware; if it cannot, say so and the round becomes
INCONCLUSIVE rather than being run on a crutch.

**Every run needs a busy screen.** Round 6 R3 found candidate accumulation on a static AA screen is
very low, and a static screen also makes AA drop its own frame rate — which would swamp the bitrate
comparison. This is stated up front rather than as a remedy: swipe the AA surface every 20–30 s
throughout every measured run, and record how many swipes you issued per run.

Standing rules that bit previous rounds and apply here: `grep -a` without exception (§7a);
`set_hu_prefs.sh` for multi-key writes; head unit up before the phone (§4).

---

## 3. The measurement

`recv_gaps.py` is already in `hur-wifi-test-scripts/` (re-saved in round 6). Run it on every capture,
unchanged, and report its numbers.

**`LinkGapMonitor` is now hardware-validated** — round 6 R4 and R5 both had it agree with
`recv_gaps.py` at 0 stalls. This is the first round it can be quoted as a primary instrument. Report
its `inbound link quiet` lines alongside the script's output; a disagreement between the two is
itself a finding worth its own paragraph.

Add one new script. Save it as `hur-wifi-test-scripts/wire_bitrate.py` and list it in Setup notes.

```python
#!/usr/bin/env python3
"""wire_bitrate.py <capture.txt>

Per-channel inbound wire cost of an AAP session, from "RECV: <CH> ... size: N" lines.
Needs log-level=0 (VERBOSE) — RECV: is guarded by LOG_VERBOSE.
Prints total bytes, span, mean Mbit/s, and for VIDEO the frame-start count and bytes per frame.
"""
import sys, re
from collections import defaultdict

pat = re.compile(r'^(?:\d\d-\d\d )?(\d\d):(\d\d):(\d\d)\.(\d\d\d)')
def ts(line):
    m = pat.match(line)
    if not m:
        return None
    h, mi, s, ms = m.groups()
    return int(h) * 3600 + int(mi) * 60 + int(s) + int(ms) / 1000.0

size_re = re.compile(r'size:\s*(\d+)')
flag_re = re.compile(r'flags:\s*(\d+)')

tot = defaultdict(int)
first = {}
last = {}
frames = 0

for line in open(sys.argv[1], errors='replace'):
    if 'RECV:' not in line:
        continue
    t = ts(line)
    if t is None:
        continue
    tail = line.split('RECV:', 1)[1]
    ch = tail.split()[0]
    m = size_re.search(tail)
    if not m:
        continue
    tot[ch] += int(m.group(1))
    first.setdefault(ch, t)
    last[ch] = t
    if ch == 'VIDEO':
        f = flag_re.search(tail)
        if f and f.group(1) in ('11', '9'):
            frames += 1

if not tot:
    print('no RECV lines with a size: field — was log-level=0 set?')
    sys.exit(1)

span = max(last.values()) - min(first.values())
print(f'span {span:.1f}s')
for ch in sorted(tot, key=lambda c: -tot[c]):
    mb = tot[ch] / 1e6
    print(f'  {ch:16s} {mb:8.2f} MB  {tot[ch]*8/span/1e6:6.3f} Mbit/s')
v = tot.get('VIDEO', 0)
if frames:
    print(f'\nVIDEO frame starts (flags 11|9): {frames}  = {frames/span:.1f} fps')
    print(f'VIDEO bytes per frame: {v/frames:.0f}')
```

Run it on both arms' captures. **The number that decides the round is the ratio of VIDEO Mbit/s
between R2 and R1, and the ratio of bytes-per-frame**, both at comparable measured fps. Report them
as numbers.

---

## 4. Runs

Both measured runs: `log-level=0` (VERBOSE — `RECV:` is guarded by `LOG_VERBOSE`, see the template's
§1 warning), `wifi-connection-mode=3`, `view-mode=0`, `fps-limit=60`, `force-software-decoding=false`,
no fault-injection keys. **At least 5 minutes each**, with the swipe cadence from §2, and Spotify
playing throughout so the audio channel is loaded and underruns are countable:

```bash
adb -s <phone> shell am force-stop com.spotify.music
adb -s <phone> shell monkey -p com.spotify.music -c android.intent.category.LAUNCHER 1
```

Run R1 and R2 back to back on one Bluetooth link, head-unit-only resets between them (§7a: a live
link survives head-unit restarts; touching the phone's radios is what loses it).

### R0 — gate and preconditions

**PASS / FAIL — a FAIL here stops the round.**

- `build_hur.sh`, `run_unit_tests.sh`. **Expect 525.** Record the APK md5.
- Back up `settings.xml` before anything (§1). Restore and diff at the end.
- With `force-software-decoding=false`, confirm from a short launch that
  `findBestCodec: hw=... ` names a **hardware** HEVC decoder on this unit, and that
  `Configuring decoder:` uses it. Record the exact string.
- If no hardware HEVC decoder exists here, or `isHevcSupported()` is false: **stop, report
  UNTESTABLE, and say so.** This rig cannot carry the round and no substitute will fix that.

### R1 — H.265 arm

`video-codec=H.265`, everything else per §4.

- Verify the setting took by the **negotiation**, not the file: expect
  `Media Sink Setup Request: 7 on channel VIDEO`. If it says `3`, the run is void — report why.
- `recv_gaps.py`, `wire_bitrate.py`, `grep -ac "disabled due to previous underrun"`, and the count of
  `inbound link quiet` lines.
- Record the `Throughput over ...` windows verbatim and the number of swipes issued.

**PASS** = the run completed with sink codec 7 and all four measurements recorded. Stalls are a
number to report, not a pass condition.

### R2 — H.264 arm

Identical to R1 except `video-codec=H.264`. Expect `Media Sink Setup Request: 3 on channel VIDEO`.

**PASS** = same as R1, with sink codec 3.

### R3 — the desk check, no rig time

**PASS / FAIL.** Three questions, all answerable by reading source plus one `getprop` on the rig.

**(i)** Read `SystemOptimizer.calculateOptimalSettings` on the candidate and report, for **this rig's
own panel**, which codec a fresh first-time setup would persist, and the values of `hasH265` and
`panelCeil.width` it would use. This is the line #839 turns on (`utils/SystemOptimizer.kt:106`) and
nobody has yet checked what it does on a unit we can inspect.

**(ii)** `VideoDecoder.isHevcReliable()` (`VideoDecoder.kt:130-142`) is a **chipset allowlist** —
`qcom` / `msm` / `exynos` / `gs` / `google`, matched against `Build.HARDWARE` and
`Build.SOC_MANUFACTURER`. Report what this rig returns, with the two property values:

```bash
adb shell getprop ro.hardware; adb shell getprop ro.soc.manufacturer
```

This matters because the obvious fix was going to route the codec recommendation through that
predicate, and it would **reject** #839's unit — MediaTek `ac8227l`, not on the list — which is the one
unit where H.265 is *measured* good (58 fps, zero `ACodec` errors, over USB). If this rig is also off
the list while decoding HEVC fine in R1, that is a second data point against the predicate and worth
saying plainly.

**(iii)** Confirm or refute this reading of the source, which was found while checking the fix and is
the reason R0 gates on hardware HEVC:

- `ServiceDiscoveryResponse.kt:63-68` treats `videoCodec == "H.265"` as a **capability-gated
  preference** — with `hevcAvailableForUserChoice == false` it announces `MEDIA_CODEC_VIDEO_H264_BP`.
- `VideoDecoder.kt:755-759` treats it as a **command**: `codecName` there is `settings.videoCodec`
  (from `AapVideo.kt:246`), and on `"H.265"` it returns `CodecType.H265` **discarding
  `detectCodecType()`'s answer**.

If both readings hold, then setting `"H.265"` on a unit whose HEVC is undetected announces H.264,
receives H.264, and builds an HEVC decoder for it. **If the rig can be put into that state cheaply —
`video-codec=H.265` with `isHevcSupported()` false — capture it; that is the round's best possible
bonus finding.** If it cannot, say so and leave it to a JVM test. Do not spend rig time forcing it.

### R4 — optional, only if R1/R2 both landed and there is time

Build `v.3.1.1` and run the H.264 arm again on it, everything else identical. This settles the one
question the reporter logs left open: whether there is a residual version effect *within* H.264
(3.1.1 held 30 fps for 34 consecutive seconds and had zero audio RECV gaps ≥ 1.2 s over its first
188.6 s, against 3.2.4's 0–20 fps — suggestive, but confounded by screen activity).

**This is expected to be awkward.** `v.3.1.1` predates the package rename and may not build with the
current toolchain at all. If it does not build in one attempt, report **UNTESTABLE** and stop — do
not spend the round on it.

---

## 5. Report back

1. **The two bitrate ratios**, R2 over R1: VIDEO Mbit/s, and VIDEO bytes-per-frame, with the measured
   fps of each arm alongside so the reader can see whether the content was comparable.
2. **Did the negotiation follow the setting** in both arms — sink codec 7 in R1, 3 in R2?
3. **Stalls, dead time and underruns per arm**, as numbers. Zero in both is the expected answer.
4. **Did `LinkGapMonitor` agree with `recv_gaps.py`** in both arms?
5. **R3: which codec would first-time setup persist on this rig's panel**, and the two values it
   turns on?
6. **Was the HEVC hardware decoder real** on this unit, and which one?
7. Anything the brief did not ask about.

### What each outcome means, decided in advance

- **H.264 measures materially more wire cost per frame than H.265** (say, ≥ 1.4×): the bandwidth
  explanation holds, and `SystemOptimizer.kt:106` becomes a fix to design rather than a hypothesis.
- **The two are within ~10 % of each other**: the bandwidth explanation is wrong, and the reporter's
  H.265-vs-H.264 split needs a different mechanism — decoder-side cost, or something in how AA itself
  paces an H.264 stream. That is a more interesting result than the first one, not a failed round.
- **Either arm cannot be negotiated at all**: that is a bug in the announcement path and outranks
  everything else in this brief.
