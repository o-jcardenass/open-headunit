# link-stall-periodic-scan — round 4 brief: put the rig on the band the reports come from

**Candidate:** `fork/fix/video-stack` @ `c2983fc7` — **six commits**, **541 JVM tests**. One APK for
the whole round; everything that changes between runs is `settings.xml`.

**Note the branch has been rewritten.** `fix/video-stack` was compacted from eighteen commits to
five on 2026-08-19 and the sixth is this round's new lever, so **fetch and reset, do not pull**.
`fix/video-pipeline` and `fix/relaunch-onto-idle-screen` are **deleted** — their content is inside
these commits. Round 3's pinned SHA `9a1257ca` still resolves, from the tag
`video-stack-pre-compaction`.

---

## 1. Why this round exists, and how its goal differs from round 3's

Round 3 did what it was asked and the answer was not the one the thread expected. Both codecs ran
**clean** on this rig — 0 stalls, 0 dead time, 0 underruns in every arm — and H.264's wire cost came
out at **1.16× Mbit/s / 1.18× per frame**, not the ~2× the thread had been assuming. That is real but
modest, it landed between the brief's two pre-registered bars, and it does not on its own explain an
8-10/min underrun rate on the reporter's unit. R3's desk check found a genuine behavioural bug
instead, and that is already fixed on this candidate.

So the bandwidth story is not carrying #839, and the thread has now run **five clean rounds on this
rig** without once reproducing the fault. That is the finding worth acting on: every round so far has
varied things the reporter's unit and this rig have in common, and never the two things they do not.

| | #839 / #824 units | this rig |
|---|---|---|
| Link | WiFi Direct, **band unrecorded** | 5 GHz |
| RAM | ~1 GB, heap 12-20 MB | ample |
| Android | **8.1 (API 27)** | 14 |
| SoC | MediaTek `ac8227l` / Spreadtrum `sp7731e_1h10` | UNISOC `uis7861_6h10` |

**Correction, and it matters for how you read this round.** An earlier version of this brief said both
reporter units are on 2.4 GHz. That was an inference, not a measurement, and it has been withdrawn.
What their captures actually show is `Standard createGroup SUCCESS!` and `Freq: 0 MHz (unknown)`, in
every capture on both units: they are pre-Android-10, so the band request (API 29) never runs, and
`getGroupFrequency` returns 0 below Q while the pre-Q reflection finds no field on either vendor
build. **Their band is simply not recorded anywhere.** They may be on 2.4 GHz; nothing proves it.

The Android version and the SoC cannot be changed. The band and the memory profile now can, and that
is this round — with the band arm now testing a *hypothesis* about the reporters rather than
reproducing a known configuration.

- **`debug-force-p2p-band-24`** is new on this candidate. The rig could never run 2.4 GHz before, for
  two independent reasons: `createQuietGroup` asks for `GROUP_OWNER_BAND_5GHZ`, and
  `onGroupInfoAvailable` tears down and remakes any Native AA group that comes up on 2.4 GHz anyway.
  The setting turns off both together. Note it is **inert on the reporters' own units** — on API 27
  the requested band is only ever read inside the API-29 branch — so this arm tests what 2.4 GHz does
  to *a* head unit, not what it does to theirs.
- **`debug-force-memory-profile=CONSTRAINED`** already existed and has never been used on hardware.
  It makes the video pipeline size itself as though this were a 1 GB unit — smaller
  `KEY_MAX_INPUT_SIZE`, a 2 MB frame-pool budget, a 16 KB pooled-buffer floor.

**This round tries to reproduce, where round 3 tried to measure.** That changes what a null result
means and it is stated in advance in §5: a clean run here is not another "as expected", it is
evidence that this rig cannot carry #839 at all, which retires a line of investigation rather than
extending it.

**Do not read this as a recommendation to move anyone onto 2.4 GHz.** 5 GHz is what a working session
runs on. The setting is a rig lever and it stays off by default.

---

## 2. Read this before planning the runs

**A 2.4 GHz group may simply not work here, and that is a result, not a failure.** Three ways it can
go wrong, all of which you should report rather than fight:

- The group comes up on **a channel above 11** (2467 MHz or higher). A client in the FCC domain
  associates on channels 1-11 only and will not even list the SSID, so the phone reports "can't find
  the network". `WifiDirectManager` already reports this. It is a regulatory/driver property of the
  unit, recreating the group does not move it, and if it happens the arm is **UNTESTABLE** — say so
  and move on.
- The phone joins and the session **dies within seconds**, repeatedly. That is a known 2.4 GHz
  signature on the hotspot transport on other hardware. If it happens here, capture one instance
  properly and report it; do not spend the round retrying.
- `createGroup` fails outright on the forced band. The retry ladder handles it and falls back to
  `standardCreateGroup` — **and a standard fallback voids the arm**, because the platform then picks
  the band. §4's R0 check is what catches this.

**Both levers must be verified from the log, never from the settings file.** A written setting that
did not take is how a round measures nothing and reports a PASS.

**The constrained profile has never run on hardware.** It is the first commit of this branch and its
sizing arithmetic has only ever been exercised in JVM tests. Two consequences: its own log lines are
worth recording whatever else happens (§3), and if the picture is *worse* under it in a way that has
nothing to do with the link — a decoder that cannot get an input buffer, say — that is a finding
about the branch and it outranks the reproduction question. Report it prominently.

Standing rules that bit previous rounds and apply here: `grep -a` without exception (§7a);
`set_hu_prefs.sh` for multi-key writes; **head unit up before the phone** (§4); confirm the A2DP link
immediately before every run that needs it and never infer it from the last one (§7a).

**Round 3's discard-rule finding stands and is not to be re-litigated.** One `MATCH! Starting
AapService` per capture, and two `p2p-wlan0-N` values in a capture that spans a previous group's
teardown, are **benign** under this launch protocol, as long as `AapService.onCreate` and
`createGroup SUCCESS!` each verify to exactly one occurrence. Check those two counts, record them,
and carry on.

---

## 3. The measurement

`recv_gaps.py` and `wire_bitrate.py` are both in `hur-wifi-test-scripts/` from round 3. Run both on
every capture, unchanged, and report their numbers.

**Three additions to how they are reported, all from re-checking round 3.**

1. **Report the audio channel's coverage of the window, per run.** `wire_bitrate.py`'s AUDIO figure
   divides by the *global* RECV span; `recv_gaps.py`'s kB/s divides by the *audio-only* span. Their
   ratio is therefore how much of the measured window actually carried audio, and
   `recv_gaps.py` alone structurally cannot see a hole at the head or tail — it reports 100.0 % of
   real time either way. Round 3's R1 was **~78 %** by this arithmetic (≈68 s of 307.8 s), R4 ~89 %,
   R2 ~100 %, and nobody noticed at the time. Report it as a line per run:
   `AUDIO coverage = (wire_bitrate AUDIO kB/s) / (recv_gaps kB/s)`.
2. **Confirm Spotify is `state=PLAYING` *before* starting each capture**, not only mid-run. Round 3's
   first R2 was voided because an `exit`+relaunch cycle had silently dropped media focus and nothing
   in the app resumes it. Re-check every 4th swipe as round 3 did.
3. **`LinkGapMonitor` may finally have something to say.** It has been hardware-validated only in the
   negative — it has agreed with `recv_gaps.py` at *zero* stalls on every run it has ever had. If this
   round produces a real outage, whether the in-app instrument reports it is worth its own paragraph
   either way. A disagreement between it and the script is a finding.

Record these from every run, and they are the whole report:

```bash
grep -a "Requesting Native AA P2P group on"     log.txt   # which band was asked for
grep -a "createGroup SUCCESS"                    log.txt   # which band arrived, and how many groups
grep -a "onGroupInfoAvailable"                   log.txt   # the frequency and channel, verbatim
grep -a "memory profile\|FORCED"                 log.txt   # the constrained reading
grep -a "Allocating .* buffers of size"          log.txt   # what the component actually took
grep -a "Configuring decoder"                    log.txt   # codec, size, and how many times
grep -a "Media Sink Setup Request: . on channel VIDEO" log.txt   # 3 = H.264, 7 = H.265
grep -ac "disabled due to previous underrun"     log.txt
grep -ac "inbound link quiet"                    log.txt
grep -ac "MATCH! Starting AapService"            log.txt
grep -a "AapService.onCreate"                    log.txt
```

---

## 4. Runs

Common to every measured run: `log-level=0` (VERBOSE — `RECV:` is guarded by `LOG_VERBOSE`),
`wifi-connection-mode=3`, `view-mode=0`, `fps-limit=60`, `force-software-decoding=false`, no
fault-injection keys, Spotify playing throughout, and the AA surface swiped every 20-30 s with the
count recorded. **Ten minutes each**, not five: the fault this round hunts has a ~11.6 s period in
one capture and ~10 s in the other, so a five-minute window that happens to miss it proves nothing,
and the extra five minutes are the cheapest part of the round.

The two new keys, for the settings writer:

```xml
<boolean name="debug-force-p2p-band-24" value="true" />
<string name="debug-force-memory-profile">CONSTRAINED</string>
```

`CONSTRAINED` is matched by name and is case-sensitive. Removing the key entirely is what "measure
it" means; `false` is what "5 GHz" means.

### R0 — gate and preconditions

**PASS / FAIL — a FAIL stops the round.**

- `build_hur.sh`, `run_unit_tests.sh`. **Expect 541.** Record the APK md5.
- Back up `settings.xml` before anything. Restore and diff at the end.
- **Both levers verified from a short launch, on the log, before any measured run:**
  - `Requesting Native AA P2P group on 2.4GHz band. Forced by debug setting.` — the request took.
  - `2.4GHz createGroup SUCCESS!` followed by an `onGroupInfoAvailable` **under 2500 MHz**, and *no*
    `Recreating 5GHz group` line anywhere. A `Standard createGroup SUCCESS!` here means the forced
    band failed and fell back: **the arm is void**, report why.
  - The channel is **2412-2462 MHz**. Above that, see §2 — UNTESTABLE, and say which frequency.
  - The memory reading prints `CONSTRAINED` and marks itself `(FORCED)`.
- If the group cannot be made to come up on 2.4 GHz at all, **stop and report UNTESTABLE for R1-R3**.
  R4 below can still run and is worth having.

### R1 — the reproduction attempt: 2.4 GHz + constrained + H.264

`debug-force-p2p-band-24=true`, `debug-force-memory-profile=CONSTRAINED`, `video-codec=H.264`.
Expect `Media Sink Setup Request: 3 on channel VIDEO`. Ten minutes.

This is the sick profile from #839's own captures with both new levers on. Report the full
instrument set from §3.

**PASS** = the run completed on 2.4 GHz with both levers confirmed and every measurement recorded.
Stalls are the number the round is *for*, not a pass condition — a clean run is a PASS and a
meaningful result.

### R2 — band isolation, **only if R1 reproduced**: 5 GHz + constrained + H.264

Identical to R1 with `debug-force-p2p-band-24` removed. If R1 was clean, skip this and say so — it
answers a question nobody has.

### R3 — memory isolation, **only if R1 reproduced**: 2.4 GHz + measured profile + H.264

Identical to R1 with `debug-force-memory-profile` removed.

### R4 — the replicate round 3 never had

**Run this whatever R1 does.** A second run of whichever arm is most interesting — R1 if it
reproduced, otherwise R1 again unchanged. Round 3's headline ratio has no error bar and its only
same-codec comparison differed by 1.12×, so this thread currently cannot tell a 16 % effect from its
own run-to-run spread. One replicate fixes that permanently and costs ten minutes.

Report R4's numbers beside R1's without averaging them.

---

## 5. Pre-registered outcomes — decided now, not after the numbers

**What counts as reproducing #839/#824.** All four, not any one:

- `recv_gaps.py` reports **stalls > 1.2 s**, and their **period** (start-to-start) is regular — a
  median around 10-12 s with a small spread. An irregular one-off is not this fault.
- **Dead time is a double-digit percentage** of the run. The two reporter captures profiled at 14.1 %
  and 78 %.
- **Underruns are non-zero** and, divided by the run's span, land in the 2-10/min band.
- The gaps are **on every channel at once**, not video only. That is what separates a link outage
  from anything the decoder or the audio path could cause, and `wire_bitrate.py`'s per-channel
  breakdown is where it shows.

**If R1 reproduces:** R2 and R3 isolate which lever did it, and this thread has a rig-side
reproduction for the first time. That is the round's best outcome by a distance and everything else
in it is secondary.

**If R1 is clean:** say so plainly. It means neither the band nor the memory profile is sufficient to
produce the fault on Android 14 hardware, and combined with round 3 it means **this rig cannot carry
#839** — the remaining differences are the Android version and the SoC, neither of which is settable.
That retires the rig as an instrument for this thread and moves the question back to the reporter's
own unit, which is a real and useful conclusion. Do not soften it into "as expected".

**If the constrained profile breaks something on its own** — a decoder that cannot get input buffers,
frames dropped that were not dropped before, a size the component rejects — that is a defect in this
branch's first commit and it **outranks everything above**. Report it first, with the `Allocating`
and `Configuring decoder` lines, whatever the link did.

---

## 6. Report back

1. R0's two lever verifications, quoted verbatim from the log, plus the group's frequency.
2. Per run: `recv_gaps.py` in full, `wire_bitrate.py` in full, **AUDIO coverage**, underrun count and
   rate/min, `inbound link quiet` count, swipes issued, and the throughput windows.
3. The four reproduction criteria in §5, answered one by one, for R1 and R4.
4. Whether `LinkGapMonitor` agreed with `recv_gaps.py`, in either direction.
5. Anything the constrained profile changed that the link cannot explain.
6. The discard-rule counts (§2) for every capture.
7. Anything this brief did not ask about.
