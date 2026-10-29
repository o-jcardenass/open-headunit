# Dropped-frame keyframe recovery: round 6 results

**Candidate:** `fix/830-request-keyframe-on-dropped-frame` @ `d4f42814`
**Baseline:** `62889f29` (round 5's candidate, reused APK)
**APK md5:** candidate `c5d4c0feeb60d81d38aca693bcf7940c` / baseline `4d54b75538877378fcd25d27a2a718d8`
**Unit:** UNISOC MT50_YT610E4GFPSL_U, Android 14, 3.8GB RAM
**Date:** 2026-08-14

## Headline

**R2 (transient stall, the point of the round): PASS, cleanly.** Under the identical CPU-burst lever,
the candidate shed **zero** reference frames (`dropped=0`, `skipped=110`) while the baseline shed 4
(`dropped=4`, `skipped=95`, both escalating into full recovery cycles). The deeper queue does exactly
what it was built to do: the input-drop-becomes-output-skip trade, measured directly.

**R3 (sustained overload, candidate only) surfaced something worth flagging plainly, as the brief
asked: the drop rate rose, not fell.** 67.0 drops/min against round 5's 36.7/min on the same candidate
lineage, same settings, same duration, roughly 1.8x. The recovery chain itself is unaffected (still
~2.7 s repair, budget and cooldown both held exactly as coded) and no #755 signal appeared, but this is
the "a rise would be a finding" case the brief explicitly warned about, not the "modest fall" it called
the honest expectation.

## Setup notes

- **A genuine methodology error, corrected mid-round rather than hidden:** R3 was launched with a
  `sleep 300` instead of the required 10-minute duration, so the first capture (`r3.txt`) only covers
  308.9 s. Caught immediately after stopping it (the file's own last timestamp made the shortfall
  obvious). Rather than discard and restart, a second capture (`r3b.txt`) was started immediately
  against the same still-running, still-provoked session (same pid, same settings, `PLAYING` media
  confirmed) and run for a further 300 s, giving **625.9 s (10.43 min) of combined logged coverage**
  against the intended ~600 s. There is a **30.2 s gap** between the two files (`16:56:06.626` to
  `16:56:36.871`) from the `logcat -c` + pipe-restart overhead, during which nothing was logged. This
  gap directly straddles one drop episode, merging what were almost certainly two separate
  unrepaired-clock runs into one confusing reconstructed record; that one run (and only that one) is
  excluded from every table below and detailed in R3's own section. The headline `dropped=`/`skipped=`
  totals are unaffected (they come from `Throughput` line sums across both files, not from run
  reconstruction) but are very likely a slight undercount, since drops almost certainly continued
  during the unlogged 30.2 s. Lesson for the next round needing a multi-segment capture: start the
  second segment's `logcat -c` before killing the first, not after.
- Media playback (Amazon Music via `input keyevent 85` relayed through the head unit) needed a resend
  after **every** relaunch this round (R1, R2 baseline, R2 candidate, R3), not just the first,
  consistent with round 5's finding that force-stop reverts the session to `PAUSED`, but confirms this
  now holds across every relaunch in a round, not only the first.
- The mid-round question about visible artifacts during R3 was answered live: the rig was correctly
  running the deliberate `force-software-decoding=true` provocation at that moment, not a regression.
- Scripts used: `build_hur.sh`, `run_unit_tests.sh`, `set_hu_prefs.sh`, all as documented. No changes,
  no new scripts. The CPU-burst lever script was used verbatim from the brief; the thermal-throttle
  fallback was never needed since the burst lever produced drops on the baseline immediately.

## R0: build and unit-test gate

**PASS.** `build_hur.sh` clean, `run_unit_tests.sh` clean. **292/292** unit tests (round 5's 286 plus
the new `VideoFeedQueuePolicyTest` **6/6**), exactly as predicted. APK md5
`c5d4c0feeb60d81d38aca693bcf7940c` confirmed identical between the built artifact and the live install.

## R1: silence and depth confirmation (gate for R2)

**PASS.** 5-minute undisturbed capture, hardware decoding, screen moving. `dropped=0` for the entire
window. **Zero** occurrences of `dropped a reference frame`, `picture unrepaired`, or `cycling video
focus`.

The feed-thread depth line was not present in this particular capture (the session predated it, same
as prior rounds), so it was confirmed with a short, separate relaunch-only capture immediately after:
`Feed thread started (queue holds 30 frames, 500ms at 60fps)`, exactly as the brief predicted for the
candidate. This did not require repeating the 5-minute silence measurement, which stands on its own.

3 natural keyframes, gaps **68.489 s, 69.162 s, 68.819 s** (median 68.8 s), reproducing round 4/5's
fixed-GOP finding within the same tight band.

## R2: the transient stall, both builds (the point of the round)

**PASS.** Same rig, same CPU-burst lever (400 ms full-core contention every 10 s, 20 times, roughly
208 s), same ~8-minute capture window, same content, run back-to-back on each build with the live APK
md5 confirmed before each.

| | Baseline `62889f29` | Candidate `d4f42814` |
|---|---|---|
| `Feed thread started` | plain, no detail (confirms baseline) | `(queue holds 30 frames, 500ms at 60fps)` |
| Total `dropped=` | **4** | **0** |
| Total `skipped=` | 95 | **110** |
| `dropped a reference frame` count | 2 | 0 |
| `Input buffer full` count | 0 | 0 |
| Cycles fired | 2 (both escalated) | 0 |
| fps range | 46-58 | 47-62 |
| `Codec initialized:` mid-session | 0 | 0 |

**The candidate's `dropped=` is materially below the baseline's (0 vs 4), with `skipped=` at or above
it (110 vs 95)**, exactly the input-drop-becomes-output-skip trade the brief predicted, measured
directly rather than argued from first principles.

Both of the baseline's drops escalated into a full recovery cycle (the candidate had none to
escalate). Both chains reproduce round 5's timing closely:

| # | T0 | Repair | T0 -> repair | `session=` |
|---|---|---|---|---|
| 1 | 16:41:31.271 | 16:41:33.839 | 2.568 s | 1 |
| 2 | 16:43:51.575 | 16:43:54.264 | 2.689 s | 2 |

## R3: sustained overload, candidate only (the count comparison)

**A rise, not a fall.** 10.43 minutes of combined logged coverage (see Setup notes for the two-segment
capture and its gap), identical provocation settings to rounds 1-3 and 5.

| Round | Build | `dropped=` | Duration | Rate |
|---|---|---|---|---|
| 1 | `563ae013` | 510 | 5.5 min | 92.7/min |
| 2 | `ec0a2d28` | 543 | 10.6 min | 51.2/min |
| 3 | `a2e0268e` (TEST ONLY) | 533 | 5 min | 106.6/min |
| 5 | `62889f29` | 367 | 10 min | 36.7/min |
| **6** | **`d4f42814`** | **699** | **10.43 min** | **67.0/min** |

Against round 5's own build lineage under the identical settings and near-identical duration, the rate
**rose from 36.7/min to 67.0/min, roughly 1.8x**. `skipped=` stayed at **0** for the entire capture
(the sustained software-decode bottleneck never produces a decoded backlog to skip from; skipping only
happens when the codec is keeping up but the surface can't consume fast enough, the R2 signature, not
this one).

`dropped a reference frame` (the throttled W-level log line) fired 175 times against 699 actual
`framesDropped` increments, a 4:1 ratio, fully explained by the source: `notifyFrameDropped()`
increments the counter behind every `Throughput` `dropped=` value unconditionally, but only prints its
own log line when `VideoRecoveryPolicy.shouldRequestOnDroppedFrame` allows it (throttled to keep the
nudge rate bounded, unrelated to the actual count). Round 5 measured a 1:1 ratio because its baseline's
drops never arrived densely enough to hit that throttle; this round's denser bursts do. Not a defect,
but the throttle ratio itself (4:1 here vs 1:1 in round 5) is a second, independent signal that this
build's drops arrive in denser bursts under sustained overload than the shallower-queue baseline's did.

### Budget and cooldown

**3 cycles fired, capped correctly, no fourth.** All chains complete and clean:

| # | T0 | Repair | T0 -> repair | `session=` |
|---|---|---|---|---|
| 1/3 | 16:51:19.650 | 16:51:22.345 | 2.695 s | 1 |
| 2/3 | 16:52:42.328 | 16:52:45.007 | 2.679 s | 2 |
| 3/3 | 16:53:55.000 | 16:53:57.669 | 2.669 s | 3 |

Reproduces round 5's 2.672-2.678 s almost exactly. Spacing: cycle 1 -> 2 = **82.678 s**, cycle 2 -> 3 =
**72.674 s**, both over the 60 s minimum. 8 explicit `no cycle available now` refusal lines were
logged; 2 of them (`16:55:09.406` and `16:57:03.209`) fall on either side of the capture-boundary gap
and are excluded from the table below as one contaminated, un-reconstructible run (see Setup notes).
The remaining **6 clean refused runs**, all correctly deferring to the phone's own keyframe:

| # | T0 | Repair | Total |
|---|---|---|---|
| 1 | 16:51:35.361 | 16:52:32.814 | 57.453 s |
| 2 | 16:52:57.450 | 16:53:54.425 | 56.975 s |
| 3 | 16:54:00.006 | 16:55:05.446 | 65.440 s |
| 4 | 16:57:40.869 | 16:58:31.950 | 51.081 s |
| 5 | 16:58:32.291 | 16:59:45.958 | 73.667 s |
| 6 | 17:01:34.835 | 17:01:52.028 | 17.193 s |

n=6, median 57.2 s, min 17.2 s (a natural keyframe that happened to already be due soon after this
episode's T0, not an anomaly), max 73.7 s: consistent with R1's own measured cadence.

**Zero silent self-clears** among all 9 clean runs (3 cycled + 6 refused): every genuine run reached
the 2 s check, matching round 5's finding under sustained overload exactly.

### #755 sentinel

`Codec initialized:` count: **0** across the whole capture and all three cycles, matching round 4/5's
finding that the component survives repeated cycling intact. fps ranged **19-52** across the capture
(excluding one terminal 16 fps window that is a capture-truncation artifact, the last, incomplete
5-second window before the capture process was killed, not a real reading). The one genuine low point,
19 fps at `17:01:32.458`, had `dropped=0` in that window and the surrounding ones, recovering to
40-42 fps within 10 s. No sustained, non-recovering fps degradation anywhere.

## Anything the brief did not ask about

**Whether any drop episode in R2 cleared inside the 2 s window without an escalation: unanswered by
this round's data, and worth saying precisely why.** The candidate had zero drops of any kind under
the transient lever, so there was nothing to observe clearing. The baseline had exactly 2 drops, and
both escalated into full cycles rather than self-clearing (0/2). R2's sample size for this specific
question is too small either way to say anything about the 2 s window's tuning against a real isolated
drop; R3's zero-self-clear result (this round and round 5's) both come from sustained overload, which
the brief already flagged as unable to answer this. **The 2 s window's tuning against a genuinely light,
isolated drop remains an open question no round in this thread has yet been able to observe**, since
every provocation tried so far either produces zero drops (hardware path, R1) or produces them densely
enough that every run reaches the 2 s mark.

## Net position for #830

**R2 answers what it was built to answer: the deeper queue converts input drops into output skips,
measured 0-vs-4 dropped and 110-vs-95 skipped under an identical transient lever.** That is the queue
fix working as designed.

**R3's rise (36.7 -> 67.0/min) is the round's other finding, reported as instructed rather than
downplayed.** It does not touch the recovery chain's timing or the budget/cooldown mechanism, both of
which reproduce rounds 3-5 closely, and no #755 signal accompanies it. But a near-doubling of the
sustained-overload drop rate on the exact settings rounds 1-5 used as their historical control is a
real number against a real prior baseline, not noise, and the brief's own framing (a rise is a finding)
should decide how much weight it carries before this branch ships. Worth deciding explicitly rather
than folding into "R2 passed" and moving on: is R3's rise expected and acceptable collateral of a
queue that (correctly) holds more frames before shedding under a bottleneck that never clears, or does
it warrant its own follow-up round with a controlled same-session A/B (which this round's R3, scoped
candidate-only per the brief, did not run)?
