# Video pipeline stack — round 2 brief

Round 1's R4 was the round's headline FAIL: `DROP_MIDDLE_FRAGMENT` walked the decoder into a
permanent black-screen stall/restart loop. This round tests the fix for it, and collects three
measurements that ride along for free.

**One build. No baseline APK.** Everything here is either a new log line with no counterpart
anywhere, or a comparison against round 1's own recorded numbers.

---

## 1. Build and baseline

```bash
git fetch fork fix/decoder-wedge-on-corrupt-access-unit
git checkout 8f0beab1b3e628c85e9ca64ca747f22859d0ccff        # short 8f0beab1
```

This is round 1's build A with two things added on top:

| Tip | What it adds | Runs |
|---|---|---|
| `f008e3d1` | round 1's build A, exactly as tested | the whole round |
| `9c6460eb` | the fragment audit fixed: per-fragment scaling, and a print budget that refills | R1, and a check on every run |
| `8f0beab1` | this round's subject: the decoder-wedge fix, the focus-cycle lever, the parameter-set tracker, the mode 4 probe | R2, R3, R4, R5, R6 |

**History was not rewritten.** `f008e3d1` is still where round 1 left it; `9c6460eb` was pushed on
2026-08-18 on top of it, and the four commits of `8f0beab1` on top of that. Nothing below has moved,
so round 1's results remain valid as the comparison this round is scored against.

**None of it has been compiled.** No JVM on the machine that wrote it, no PR, so CI has never seen
it either. R0 is again the most valuable run in the round.

`build_hur.sh` deletes the previous APK before it builds. Only one build this round, so nothing to
copy out — but record its md5 and confirm it is the one live on the unit before trusting a run.

---

## 2. What this is and why it exists

### The defect round 1 found

R4 injected a dropped middle fragment at one in three. A dropped middle leaves the fragment run
looking intact — a first, some middles, a last, in order — so the frame is assembled with a hole and
handed to MediaCodec as though it were whole. The measured result was not a corrupt picture; it was
a hang:

```
00:31:20.023  Decoder stall detected (no output for 2005ms). Forcing restart (1/4).
00:31:28.032  stall detected (5524ms). Forcing restart (2/4).
00:31:38.105  stall detected (10001ms). Forcing restart (3/4).
00:31:48.180  stall detected (10005ms). Forcing restart (4/4).
00:31:58.263  stall (10001ms) restart suppressed (4/4 used)
   … 20s, 30s, 40s, 50s, 60s …
```

`rendered=0` on every throughput line from `00:31:28` to the end of the capture — over 90 seconds of
nothing on screen while `fed` kept flowing at ~50 fps. Android Auto's own client raised
*"Do you see the Android Auto screen? [YES] [SWITCH RENDERER]"*.

### What it actually is, and what round 1 got wrong about it

**It is not a regression in the stack.** `AapTransport.kt` and `AapProjectionActivity.kt` — which
between them own every decision about asking the phone for a keyframe — are byte-identical between
`main` and the stack tip, and the stall watchdog's cap, cooldown and warm-reconfigure grace all
predate the stack. What the stack contributed was `VideoFaultInjector`, the first tool this app has
ever had for feeding itself a holed access unit. R4 is the first time anyone pointed it at that case.
The defect has been in `main` for as long as the watchdog has.

The loop, and why it cannot climb out of itself:

1. A holed access unit reaches the codec and it stops producing output.
2. The watchdog sees "input arriving, nothing coming out" and rebuilds the codec.
3. **A rebuilt codec resumes on a P-frame.** It can render nothing until an IDR arrives — and
   Android Auto's keyframes are on a fixed ~69 s cadence, with **no keyframe-request message
   anywhere in AAP**. The video-focus release/regain cycle is the only lever that exists.
4. So the watchdog fires again ten seconds later, and again, until its budget is gone.
5. Meanwhile every rebuild ran the transport's decoder-error path, which **cancelled** the clock that
   would have spent a focus cycle and armed nothing in its place. The gain-only nudge it sent
   instead is measured inert across three previous rounds.

The decoder could not render without a keyframe, the rebuilds stopped anything asking for one, and
the failure to render caused the rebuilds. **One** corrupt access unit was enough to enter it — the
1-in-3 rate only made it fast.

### What the fix does

- A stall with **no keyframe fed since this codec started**, on a session that has already rendered,
  is now classified as starvation rather than a fault: report it, ask for a keyframe, do not rebuild
  and do not spend a restart. Bounded at 15 seconds, so a decoder that is genuinely dead still
  reaches the rebuild path and the codec-type fallback behind it.
- A decoder rebuild, and the new starvation signal, both **arm** the escalation clock instead of
  cancelling it. The budget it spends is unchanged: three focus cycles a session, sixty seconds
  apart.
- The two focus-cycle implementations — the transport's and the projection activity's — are now
  mutually exclusive. They always were two, with two budgets and two handlers, and their comments
  asserted an overlap was impossible while nothing enforced it.

### The three things riding along

- **The audit fix.** Round 1 saw ten `AapRead: DELTA_CHANGED on VIDEO` lines in the first ~200 ms of
  every session, all false: the class was comparing every message against the *first* message's
  whole-run byte difference, when the difference is per fragment (a flat 29 bytes, exact in all 20+
  of round 1's observations). Those ten also exhausted a report budget that never refilled, so the
  one instrument that can see a missing fragment was silent for the rest of every capture —
  including the windows where R2's and R8's artifacts were on screen. Both are fixed and neither has
  been near hardware.
- **Mid-session parameter sets.** The decoder reads VPS/SPS/PPS once and then stops looking. VLC and
  Moonlight both keep reading. Nothing this project has ever captured says whether they change.
- **Focus mode 4.** `VIDEO_FOCUS_PROJECTED_NO_INPUT_FOCUS` is declared in our proto and in the
  protobufs extracted from Google's own binary, and has never been sent by anything. It is the only
  focus transition that is a genuine state change without leaving projection — which is exactly the
  gap in the ladder between the inert nudge and the expensive cycle.

---

## 3. What is different about this round

Read these before planning, or several runs will be misread.

- **A bad picture is the expected outcome of R2, R3 and R5.** One frame in three arriving broken is
  near-total corruption; no amount of recovery makes that look good. What is being tested is whether
  the session *survives and keeps recovering*, not whether it looks right. Round 1's R5a lost time to
  this in the opposite direction — a picture that looked terrible turned out to be a clean PASS.
- **The injector still sits downstream of the framing audit.** It drops inside `AapVideo.process`,
  after `auditFragment` has already counted the fragment, so **no fault mode can make the audit
  fire**. That is unchanged from round 1 and is not what the audit fix is being tested on — the test
  is the *absence* of the false positives, not the presence of true ones.
- **This rig negotiates 1920x1080@60, not 1280x720.** Round 1 discovered that mid-round from the
  `resolutionId=3` setting already on the unit. Leave it alone; every number below assumes it.
- **Round 1 did not restore `settings.xml` byte-for-byte** — it restored the keys it knew it had
  changed. Dump the file and check the round-2 keys against §4 before the first run rather than
  assuming a clean slate, and take a real backup this time.
- **R5 can legitimately come back INCONCLUSIVE**, and that is a result. It needs an escalation to
  actually fire before it can observe anything, and the only lever proven to cause one on this rig is
  R2's own condition. If R2 produced no `cycling video focus` line, R5 has nothing to measure.
- **Several PASS conditions here are absences.** R4 and R6 pass by *not* producing lines. Quote the
  grep and its zero count, the same as any other measurement.
- **`Codec initialized:` counts are the primary number this round.** Round 1's R4 hit **7** in 99
  seconds. That is the figure every injection run is scored against.

---

## 4. Settings

Types: `log-level`, `view-mode`, `debug-video-fault-injection`, `debug-video-fault-rate` are
**int**; `video-codec` is **string**; `debug-keyframe-lever-no-input-focus` is **boolean**. "delete"
means run only the removal half of the template's §1, so the key reads as its default.

| Key | R1 | R2 | R3 | R4 | R5 | R6 |
|---|---|---|---|---|---|---|
| `log-level` | `2` | `2` | `2` | `2` | `2` | `2` |
| `video-codec` | `H.264` | `H.264` | `H.264` | `H.264` | `H.264` | `H.265` |
| `view-mode` | `0` | `0` | `0` | `0` | `0` | `0` |
| `debug-video-fault-injection` | delete | `2` | `2` | `4` | `2` | delete |
| `debug-video-fault-rate` | delete | `3` | `20` | `2` | `3` | delete |
| `debug-keyframe-lever-no-input-focus` | delete | delete | delete | delete | `true` | delete |
| `force-software-decoding` | delete | delete | delete | delete | delete | delete |
| `software-video-decoder` | delete | delete | delete | delete | delete | delete |

`view-mode`: 0 = SURFACE.
`debug-video-fault-injection`: 0 = OFF, 1 = DROP_FIRST_FRAGMENT, 2 = DROP_MIDDLE_FRAGMENT,
3 = DROP_LAST_FRAGMENT, 4 = HIDE_START_CODE.
`debug-video-fault-rate`: one in this many of the targeted messages is faulted.

`debug-keyframe-lever-no-input-focus` is new this round and exists nowhere else. If writing it fails,
say so — the key name is the likeliest thing in this brief to be wrong.

Use `set_hu_prefs.sh` for every run: each writes at least four keys.

---

## 5. The lines that decide every run

All verified with `grep -F` against `8f0beab1` before this brief was written. Level is the `AppLog`
call's own priority; every one survives `log-level=2`.

**The new ones, from the fix:**

```
W  Decoder has had no keyframe since it started <N>ms ago - waiting for one instead of rebuilding.
W  AapTransport: picture unrepaired for <N>ms - cycling video focus (<n>/3)
W  AapTransport: retaking video focus to complete the keyframe cycle
I  CommManager: releasing video focus to force a keyframe
W  AapTransport: picture unrepaired for <N>ms - a focus cycle is already in flight, waiting for it
I  CommManager: a video-focus cycle is already in flight - not starting a second
W  AapProjectionActivity: relaunched surface has no picture, but a focus cycle is already in flight - waiting for it
W  VideoDecoder: parameter sets changed mid-session (<kinds>, <size note>) - change #<n>
I  VideoDecoder: access unit classified <CONTENT> - <consequence> (first this session)
W  AapTransport: keyframe cycle using PROJECTED_NO_INPUT_FOCUS (probe) - a sink stop after this is a result, not a fault
```

`<CONTENT>` is one of `PARAMETER_SETS_ONLY`, `PARAMETER_SETS_WITH_PICTURE`, `NO_PARAMETER_SETS`.
`<kinds>` is a space-separated subset of `VPS SPS PPS`.

**The existing ones this round is scored against:**

```
I  Codec initialized: <component name>
W  Decoder restart requested: <reason>
W  Decoder stall detected (no output for <N>ms while receiving input). Forcing restart (<n>/4).
W  Decoder stall detected (no output for <N>ms) but restart suppressed (<n>/4 used, 8000ms cooldown). Still spinning on output.
I  VideoDecoder: keyframe reached the codec (<N> bytes)
I  Throughput over <N>ms: rendered=<n> (<n>fps), fed=… dropped=… skipped=… inputWait=…
I  AapRead: fragment accounting established for VIDEO: <summary>
W  AapRead: DELTA_CHANGED on VIDEO - <summary>
W  AapVideo: FAULT INJECTED (#<n>): <MODE> on flag <n>, len=<n>
W  AapVideo: reassembly anomalies over <N>ms: truncated=<n>, orphan=<n>, headless=<n>, overflow=<n>
I  Media Sink Stop Request: VIDEO
I  Video Sink Stopped -> Ignored (Forced Keyframe Request)
I  Video Sink Stopped -> Normal background/transition behavior
I  Media Start Request VIDEO: session=<n>, config_index=<n>
I  Stream SPS (H.264): profile=… num_ref_frames=… bitstream_restriction=…
```

Two of those pairs carry most of the round's meaning:

- **`Ignored (Forced Keyframe Request)` vs `Normal background/transition behavior`** distinguishes a
  sink stop we asked for from one we did not. The probe in R5 deliberately does **not** arm the
  "ignore" flag, so a sink stop there prints the *second* form. That is the whole answer to R5.
- **`cycling video focus` followed by `keyframe reached the codec`** is the recovery working. The gap
  between them is the number worth recording; previous rounds measured it at 0.52-0.78 s.

---

## 6. Runs

### R0 — build and unit-test gate

`build_hur.sh` then `run_unit_tests.sh` on `8f0beab1`.

- **PASS:** it compiles and the suite reports **454** tests, all green. That is round 1's 422 plus 11
  from `9c6460eb` (`FragmentedMessageAuditTest` +6, new `AuditReportPolicyTest` 5) plus 21 from this
  branch (`DecoderStallCausePolicyTest` 6, `FocusCycleLeverTest` 5, `ParameterSetTrackerTest` 9,
  `KeyframeCycleEscalationPolicyTest` +1).
- **FAIL:** stops the round. Quote the compiler output in full — this is the first compile of any of
  it, and a failure here is the round's most useful possible result.

### R2 — R4 re-run, verbatim (the point of the round)

`debug-video-fault-injection=2`, `debug-video-fault-rate=3`, everything else as round 1's R4. Run it
for **three minutes** — round 1 stopped its own R4 at 1m47s because the outcome was already decisive;
three minutes gives the escalation budget room to be spent and refuse to be spent again.

This is the same corruption, at the same rate, on the same rig, so it is directly comparable with
round 1's recorded numbers.

- **Record, as numbers:**
  - count of `AapVideo: FAULT INJECTED` (round 1: 10 in 99 s);
  - count of `Codec initialized:` (round 1: **7** in 99 s, and that is the number to beat);
  - count of `Decoder has had no keyframe since it started` — the new branch being reached at all;
  - every `cycling video focus` line, with its timestamp, and the gap to the next
    `keyframe reached the codec`;
  - every `Throughput` line's `rendered=` (round 1: **0** from 00:31:28 to the end);
  - the longest run of consecutive `rendered=0` windows.
- **PASS** needs all four:
  1. at least one `cycling video focus`, each followed by `keyframe reached the codec` within ~2 s;
  2. `rendered=` non-zero on some throughput windows after the first stall — the picture comes back,
     repeatedly, even though it does not stay;
  3. no run of `rendered=0` longer than about 25 s (the escalation window plus the 15 s starvation
     bound, with slack), where round 1 had 90+ s and rising;
  4. `Codec initialized:` well below round 1's 7-in-99 s rate.
- **FAIL:** `rendered=0` sustained to the end of the capture, or the restart budget exhausted into
  the 10/20/30/40/50/60 s suppressed-stall ladder again. Either means the loop is still closed.
  Attach the full capture.
- **A finding worth reporting on its own:** `cycling video focus` firing more than three times, or
  `a focus cycle is already in flight` appearing. The first would mean the budget is not holding; the
  second is the new mutual exclusion doing its job and is worth a line either way.
- The picture will look bad throughout. That is not the measurement — see §3.

### R3 — the realistic rate

Same fault mode, `debug-video-fault-rate=20`. Five minutes. One holed access unit every minute or so
rather than every few seconds, which is the rate a real link fault would plausibly produce.

- **Record:** `FAULT INJECTED` count (expect roughly 3-8 in five minutes); `Codec initialized:` count;
  `cycling video focus` count; the `rendered=` profile across the whole capture.
- **PASS:** the session renders normally between faults, each fault costs a visible interruption of a
  few seconds at most, and the focus-cycle budget is **not** exhausted — at most one or two cycles in
  five minutes.
- **INCONCLUSIVE:** fewer than 2 `FAULT INJECTED` lines in five minutes. The stream did not fragment
  often enough at this rate; say so and move on rather than extending the run.
- **FAIL:** any single fault costs more than ~25 s of `rendered=0`, or the session ends up in R4's
  ladder at this rate.

### R4 — the positive control that must stay clean

`debug-video-fault-injection=4`, `debug-video-fault-rate=2` — round 1's R3, re-run unchanged. Five
minutes. One first fragment in two is presented as having no start code, and the whole frame is
discarded rather than assembled headless.

This is the regression guard for the new starvation branch. A discarded frame is not a *corrupt* frame
— the codec never sees it, keeps its keyframe, and keeps rendering the frames that do arrive. Nothing
here should reach the new code at all.

- **Record:** `FAULT INJECTED` count (round 1: 60); `First fragment has no start code` count (round 1:
  60, exactly 1:1); the `reassembly anomalies` totals (round 1: headless=60, orphan=167, truncated=0,
  overflow=0); `Codec initialized:` count (round 1: **exactly 1**).
- **PASS:** `Codec initialized:` still exactly 1, **zero** `Decoder has had no keyframe since it
  started`, **zero** `cycling video focus`, and the anomaly counts still track the fault count.
- **FAIL:** any starvation or focus-cycle line at all. That would mean the new branch fires on frames
  that were merely dropped, which is the one way this fix could make things worse — a focus cycle
  releases video focus across a live stream, which is the precondition of a defect that once turned
  one dropped frame into a permanent few-fps freeze.

### R5 — the mode 4 probe

`debug-keyframe-lever-no-input-focus=true`, plus R2's settings exactly
(`debug-video-fault-injection=2`, rate `3`). Three minutes.

R2's condition is the only lever on this rig proven to make an escalation fire, so the probe rides on
it. With the key set, the *first half* of every keyframe cycle is sent as
`VIDEO_FOCUS_PROJECTED_NO_INPUT_FOCUS` instead of releasing video focus; the second half is an
ordinary gain either way.

The question is whether mode 4 gets a keyframe without the cost. Three outcomes, and all three close
it:

- **A cheap rung exists:** `keyframe reached the codec` within ~2 s of the probe line, **and no**
  `Media Sink Stop Request: VIDEO`, **and no** new `Media Start Request VIDEO: session=` with a
  higher session id. This would be the best result the round can produce.
- **It is an ordinary release wearing a different number:** a `Media Sink Stop Request: VIDEO`
  follows, printing `Video Sink Stopped -> Normal background/transition behavior` (not `Ignored`,
  because the probe deliberately does not arm that flag).
- **It is as inert as the nudge:** no keyframe follows, and recovery waits for the phone's own ~69 s
  cadence.

- **Record:** every `keyframe cycle using PROJECTED_NO_INPUT_FOCUS` line with its timestamp, and for
  each one: the next `Media Sink Stop Request` (or its absence), the next `Media Start Request VIDEO:
  session=` (or its absence), and the next `keyframe reached the codec` (or its absence), all with
  timestamps.
- **INCONCLUSIVE:** no probe line appeared, because no escalation fired. Expected if R2 was itself
  weak; say so and do not try to force it.
- There is no FAIL here. The run is a measurement, not a test — but note that the picture may recover
  *worse* than in R2, since the probe replaces the only lever known to work.

### R6 — the clean session, and the two free measurements

No injection at all. `video-codec=H.265`. **Ten minutes**, screen moving for the first five and left
static for the last five.

This is the run that matters most, because everything the fix adds must be unreachable on a healthy
stream.

- **PASS, and all of these are required:**
  - **zero** `Decoder has had no keyframe since it started`;
  - **zero** `cycling video focus` and zero `retaking video focus`;
  - `Codec initialized:` exactly once;
  - **zero** `AapRead: DELTA_CHANGED on VIDEO` — round 1 saw exactly 10 per session here, all false,
    and their absence is the whole test of the audit fix;
  - `AapRead: fragment accounting established for VIDEO:` still appears exactly once, and once for
    any other channel that fragments;
  - time from `Codec initialized:` to the first `Throughput` line with `rendered=` non-zero is
    consistent with round 1's clean runs.
- **Record as well:**
  - every `VideoDecoder: access unit classified` line — there should be two or three, once per
    distinct answer. **`PARAMETER_SETS_ONLY` appearing at all is a finding**: it means this component
    now receives mid-stream buffers flagged as codec configuration, which it never did before the
    stack, and nobody has confirmed one way or the other;
  - every `VideoDecoder: parameter sets changed mid-session` line, **or the fact that there are
    none**. Both answers are results: some means the encoder really does reconfigure mid-session and
    there is work to do; none across ten minutes means the sets repeat rather than change, and a
    planned change is dead code. **Firing on every keyframe would be a FAIL** — that means the
    change-latch is wrong.
  - the same two, from R2's and R5's captures, where a focus cycle *has* forced the phone to rebuild
    its video sink. That is the condition most likely to produce a genuine parameter-set change, and
    it costs nothing to grep for.
- **FAIL:** any starvation line, any focus cycle, or any `DELTA_CHANGED` on a clean stream.

---

## 7. Do not re-run

- **The SPS question.** Round 1 settled it on both codecs: H.264 came back
  `bitstream_restriction=true num_reorder_frames=0 num_ref_frames=1` and H.265
  `max_num_reorder_pics=0`. Quote the line if a capture has it; do not spend a run on it.
- **`max-input-size` and the buffer sizing.** Verified in round 1's R1, `requested`/`got` matched
  exactly and `Frame larger than the codec input buffer` never appeared.
- **The other two fault modes.** DROP_FIRST_FRAGMENT and DROP_LAST_FRAGMENT both PASSED in round 1
  and neither touches the code this round changes.
- **The backpressure verdict.** INCONCLUSIVE twice now — this rig cannot manufacture codec pressure.
  Do not attempt it again.
- **The capability lines.** Clean on both codecs in round 1, no WARN form. `c2.unisoc.avc.decoder` and
  `c2.unisoc.hevc.decoder`, both `sustains=true`.
- **Natural keyframe cadence**, ~69 s. Measured across three previous rounds.
- **Whether a Home press tears down the projection surface.** It does not, on this unit.

---

## 8. Report back

Five things decide what happens next, in this order:

1. **Does it compile, and do all 454 tests pass?** (R0.) Nothing else matters if not.
2. **R2's four numbers**: `Codec initialized:` count against round 1's 7-in-99 s, the longest run of
   `rendered=0` against round 1's 90+ s, the count of `cycling video focus`, and the gap from each
   cycle to the keyframe that followed it. Those four say whether the wedge is fixed.
3. **R4's zeroes.** No starvation line and no focus cycle on the HIDE_START_CODE control. This is the
   run that says the fix is not trigger-happy, and a failure here is worse than R2 failing — it would
   mean the fix releases video focus on healthy streams.
4. **R6's zeroes**, and its `DELTA_CHANGED` count in particular. Ten false positives per session
   became zero, or they did not. Until that reads zero, the audit cannot be trusted as evidence about
   anything, which is the state the last round left it in.
5. **R5's three-way answer, and R6's parameter-set answer.** Both are measurements with no wrong
   result, and both close a question that has been open in the code for a long time. Report whichever
   of the three outcomes each produced, with the timestamps, and resist rounding either into a verdict.

If a run is INCONCLUSIVE for a reason §3 already predicted, say so in one line and move on — those
are answered in advance, not open questions.
