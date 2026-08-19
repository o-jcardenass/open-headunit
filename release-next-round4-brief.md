# release/next — round 4 brief

## 1. Build

**Candidate:** `fork/fix/video-stack` @ `bbf328e8b0d616d5782ed0fd9664d87a50ddf13b` (short `bbf328e8`).

```bash
git fetch fork fix/video-stack
git checkout bbf328e8
```

**One new commit on round 3's candidate:**

```
* bbf328e8  Video: a fragment run that lost bytes asks for a keyframe, and can be   <- new
            injected where one is lost
* e1c00ec7  Video: hold the focus cycle until the stream stops losing frames        <- round 3's tip
* 6911d3c5  Video: bound the fault injector to a fault budget
* 7a3242ab  Video: a keyframe repairs the picture only when it decodes
* ecc247d2  Projection: an idle Android Auto screen is not a lost connection
* 90b9958a  Transport: record when the phone last spoke, not just when it last sent a picture
* 14bad4f2  Transport: a read that loses bytes ends the session instead of skipping the message
* d43112e2  Video: wait for a keyframe instead of rebuilding the decoder that needs one
* 0e6d5747  Video: configure the decoder from the stream, and name it when it is the bottleneck
* 40e6c4eb  Video: size the pipeline to the device instead of to a guess
* 53dfd66a  Video: never assemble a frame without its first fragment, and instrument the rest
```

**No baseline APK.** `versionCode` unchanged at **98**. The new commit has never been compiled; R0 is
its first compile.

## 2. What this is and why it exists

**Round 3's central finding was right, and this commit is the answer to it.**

Round 3 found zero `holding the cycle until it settles` lines in all four runs and traced it to
source. That trace is confirmed and it is exact: `VideoFragmentAssembler.onMessage()` has four
anomaly cases and **none fires for a dropped middle fragment** — the run stays open, the `10` still
arrives, `AppendAndDecode` still runs. `AapVideo.requestKeyframe()` is gated entirely on those four,
and it is the only setter of the corruption clock the hold reads. So the hold's trigger was
structurally unreachable for the one fault the thread exists for. Zero
`requesting keyframe to recover stream` lines against 37 and 59 injected faults is the proof, and
R1's 66.9 s recovery against round 3's 63.1 s is what that costs.

**The app could already see the fault and threw the finding away.** `FragmentedMessageAudit` was
written for exactly this case — its first paragraph says a missing middle fragment leaves the run
looking intact to the reassembler. It computed `DELTA_CHANGED`, `AapRead` printed it, and nothing
acted on it. The one instrument in the app that can see a holed access unit was wired to a log line.

Now `AuditRecoveryPolicy` routes that finding into the ordinary keyframe request — same throttle,
same log line, same escalation behind it. Only `DELTA_CHANGED`, only on `VIDEO`: the other two audit
faults are already reported by the reassembler, and asking twice for one fault would also stamp the
corruption clock twice, making a single lost fragment read as a wire still losing them.

**And none of it was testable, by construction.** `VideoFaultInjector` runs in `AapVideo.process()`,
downstream of the audit, so every message it pretends never arrived has already been counted as
arriving — its own documentation said reproducing the audit's detection needs a fault injected in the
reader. So modes now carry a stage, and **`DROP_MIDDLE_FRAGMENT_IN_READER` drops the fragment before
the audit counts it**, which is what a fragment lost on the wire actually looks like.

## 3. What is different about this round

- **There is a new fault mode and this round is mostly about it.**
  `debug-video-fault-injection=5` is `DROP_MIDDLE_FRAGMENT_IN_READER`. It attacks the same flag as
  mode `2` and at the same rate, so round 3's fault-density experience carries over directly — the
  only difference is *where* the drop happens, and that difference is the whole round.
- **The old mode is kept and is still worth running.** Mode `2` answers "what does the decoder do
  with a hole"; mode `5` answers "does anything notice the hole". R3 below runs mode `2` as a control
  precisely to show the two are not the same experiment.
- **The injector's lines move prefix in reader mode.** Mode `5` prints
  `AapRead: FAULT INJECTED …`, not `AapVideo: FAULT INJECTED …`. **Grep the bare token
  `FAULT INJECTED`, never the prefixed form** — a count keyed to `AapVideo:` will read zero on every
  run of this round's headline mode. The same applies to `fault injection - ` and
  `fault injection budget spent after`; the tokens are unchanged, the prefixes are not.
- **Two new lines are what R1 turns on.** `DELTA_CHANGED on VIDEO` was already possible and has been
  measured at zero on every clean session; `fragment run lost bytes, requesting keyframe to recover
  stream` is new and is the detection reaching the recovery path.
- **Expect `fragment run lost bytes` to be at or below the `DELTA_CHANGED` count, not equal to it.**
  The ask is throttled to one a second by `VideoRecoveryPolicy`, the same throttle every keyframe
  request in the app is held to. At round 3's densities the two should track closely, but a shortfall
  is the throttle working, not a miss.
- **A run that looks connected but static may be sitting behind the renderer dialog.** Round 3 lost a
  whole capture to it. There is now a line for it —
  `the renderer confirmation banner is up` — so grep for it before diagnosing low fault density. If
  it appears, tap through the banner and re-run; the setting persists and it will not recur.
- **Fault density floor is unchanged.** Below **30 `FAULT INJECTED`** an injection run is
  INCONCLUSIVE and must be re-run, not reported.
- **`SURFACE` for every run**, clean control included.
- `log-level=2` (INFO) carries every line this round needs. **Grep every capture with `-a`** — long
  captures read as binary, and `grep -c` then prints nothing and exits 1 rather than printing `0`,
  which looks exactly like an absent pattern.
- **Discard rule:** as round 3 applied it — count SSL handshakes and `p2p-wlan0-N` indices reaching
  the measurement window, not `MATCH! Starting AapService` itself. That judgment was right.

## 4. Settings

Types: `log-level`, `view-mode`, `debug-video-fault-injection`, `debug-video-fault-rate` and
`debug-video-fault-budget` are **int**; `video-codec` is **string**. "delete" means the removal half
of the template's §1 only. Use `set_hu_prefs.sh`.

| Key | R1 | R2 | R3 | R4 |
|---|---|---|---|---|
| `log-level` | `2` | `2` | `2` | `2` |
| `view-mode` | `0` (SURFACE) | `0` | `0` | `0` |
| `video-codec` | `H.264` | `H.264` | `H.264` | `H.265` |
| `debug-video-fault-injection` | **`5`** | **`5`** | `2` | delete |
| `debug-video-fault-rate` | `3` | `3` | `3` | delete |
| `debug-video-fault-budget` | `30` | delete | `30` | delete |
| `force-software-decoding` | delete | delete | delete | delete |
| `software-video-decoder` | delete | delete | delete | delete |

`5` is the new mode and is the only new value in this table. R1 and R3 are the same run at the same
rate and budget, differing only in the mode — that pairing is the round's control.

## 5. The lines that decide the round

**New or newly reachable on this commit:**

| Line | Level | Means |
|---|---|---|
| `AapRead: DELTA_CHANGED on VIDEO` (composed: `… - declaredTotal=N observed=N delta=N …`) | W | **the detection.** A fragment run's byte total cannot be explained by its fragment count, so a fragment is missing. Possible before this commit; produced on purpose for the first time here |
| `fragment run lost bytes, requesting keyframe to recover stream` | W | **the detection reaching recovery.** This is the line that did not exist in round 3, and the one that makes the hold reachable |
| `AapProjectionActivity: the renderer confirmation banner is up` | W | projection is waiting on the user. A session showing this is not a valid measurement |
| `AapRead: FAULT INJECTED` (composed: `… (#N of M candidates): DROP on flag 8, len=N`) | W | one reader-stage fault. **Note the prefix** |
| `AapRead: fault injection budget spent after` | W | **R1's stopwatch starts here** |

**From round 3, and what each run counts:**

| Line | Level | Means |
|---|---|---|
| `- holding the cycle until it settles` (composed: `AapTransport: picture unrepaired for Nms but the stream is still losing frames (last Nms ago) - holding the cycle until it settles (N/3 spent)`) | W | the cycle earned and deliberately not spent. **R1's key condition** |
| `cycling video focus` | W | a cycle spent, with `(N/3)` and the unrepaired time |
| `retaking video focus` | W | its second half |
| `VideoDecoder: keyframe decoded - the picture is repaired` | I | **the repair, and the only line that means it** |
| `VideoDecoder: keyframe reached the codec` | I | a keyframe was **fed**. Says nothing about the picture |
| `no cycle available now` | W | the budget or the cooldown refused. Not the same as the hold line |
| `Throughput over ` (composed: `…rendered=N (Nfps), fed=N, dropped=N`) | I | the stopwatch stops at the first non-zero `rendered=` |
| `Decoder has had no keyframe since it started` | W | the starvation branch deferring a rebuild |
| `but restart suppressed` | W | the sync-stall watchdog out of budget. **Expected under sustained loss; out of scope — see round 3 §3** |
| `Codec initialized:` | I | one per codec build |
| `AapRead: fragment accounting established for` | I | the audit's baseline, once per fragmenting channel. Flat **-29 bytes/fragment** on this rig |
| `parameter sets changed mid-session` | W | the encoder reconfigured. Settled at 2 per clean session with no interaction |

**Read-desync, for R5 — every one ends the session:**
`AapRead: fragment total read returned`, `AapRead: body read returned`, `AapRead: declared message size`,
`Disconnecting to resync`.

## 6. Runs

### R0 — build and unit-test gate

`build_hur.sh`, then `run_unit_tests.sh`.

- **PASS:** compiles, suite reports **505** tests — round 3's 495 plus 10 (5 for
  `AuditRecoveryPolicy`, 5 for the injector's new stage).
- **FAIL:** stops the round. The unbuilt files are `AuditRecoveryPolicy.kt`, `VideoFaultReporter.kt`,
  `VideoFaultInjector.kt`, `AapRead.kt`, `AapReadSingleMessage.kt`, `AapReadMultipleMessages.kt`,
  `AapVideo.kt` and `AapProjectionActivity.kt`.

### R1 — the detection fires, and the picture comes back

Mode **`5`**, rate `3`, **budget `30`**, H.264, `SURFACE`. **Four minutes minimum, and at least two
of them after the budget-spent line.** This is round 3's R1 with a fault the app can see.

- **PASS, all four, in this order — each one is a link in the chain round 3 broke at the first:**
  1. **`AapRead: DELTA_CHANGED on VIDEO` appears.** The audit sees the hole. Report the count.
  2. **`fragment run lost bytes, requesting keyframe to recover stream` appears**, at or below the
     `DELTA_CHANGED` count. The detection reaches recovery.
  3. **At least one `holding the cycle until it settles` before the budget-spent line.** This is the
     line round 3 could not produce at all. Quote the first and last in full.
  4. **The gap from `fault injection budget spent after` to the first non-zero `rendered=` is
     ≤ 10 s.** Round 3 measured 66.9 s and round 2 63.1 s on the equivalent run.
- **Report the budget-spent → `cycling video focus` gap separately.** It should be roughly **2-4 s**
  and is the direct read on the two-second quiet window.
- **FAIL:** condition 1 or 2 missing (the detection or its wiring does not work), or still
  `rendered=0` **90 s** after the budget-spent line.
- **A finding, not a failure:** conditions 1-3 met but the gap between 10 s and 90 s. Say whether a
  cycle had already been spent before the budget-spent line and how many hold lines preceded it.
- **Also record:** every `cycling video focus` line with its `(N/3)`, total hold lines, total
  `Codec initialized:`, and the longest run of consecutive `rendered=0` windows.

### R2 — the hold has a floor

Mode **`5`**, rate `3`, **no budget**, H.264, `SURFACE`. **Three minutes.**

Nothing can hold a picture while a fragment in three is being lost, and this run is not asked to. It
proves the hold cannot become a permanent refusal.

- **PASS, both:**
  1. **`cycling video focus` fires at least once.** The 60 s deferral ceiling exists to guarantee
     this. Zero cycles across three minutes with the picture dead is the FAIL this run is for.
  2. **No hold line reports a first value above `60000ms`** — that first number is how long the
     picture has been unrepaired, and above the ceiling would mean the ceiling is not firing.
- **Expected and NOT scored:** the `but restart suppressed` ladder and a `Codec initialized:` count
  in the high single digits or teens. That is the decoder's 15 s keyframe patience, untouched by this
  commit and out of scope. Record the counts, do not score them.

### R3 — the old mode is still blind, which is the point

Mode **`2`** (`DROP_MIDDLE_FRAGMENT`, the assembler stage), rate `3`, budget `30`, H.264, `SURFACE`.
**Four minutes**, same shape as R1.

The control. This mode drops the fragment *after* the audit has counted it, so nothing can see the
hole — by construction, not by defect.

- **PASS:** **zero** `DELTA_CHANGED on VIDEO`, **zero** `fragment run lost bytes`, **zero**
  `holding the cycle until it settles`, and a recovery gap in the same range round 3 measured
  (**66.9 s**) rather than R1's.
- **This run is what makes R1 mean something.** If R1 and R3 produce the same numbers, the new mode
  is not testing anything the old one could not, and R1's result needs re-reading.
- **A finding either way:** any `DELTA_CHANGED` here would mean the two stages are not as separate as
  the code claims.

### R4 — clean session regression

No injection. **Ten minutes**, H.265, `SURFACE`, Android Auto's default screen, left alone.

This is the run that answers the change's one real risk: a false `DELTA_CHANGED` used to cost a log
line and now costs a keyframe request. Rounds 2 and 3 both measured zero on a clean session; a third
zero settles it.

- **PASS, all:**
  - **Zero** `DELTA_CHANGED`, **zero** `fragment run lost bytes`, **zero**
    `holding the cycle until it settles`. **Any of these is the round's most important finding.**
  - **`Codec initialized:` exactly 1.**
  - **Zero** `cycling video focus`, **zero** `retaking video focus`, **zero**
    `Decoder has had no keyframe since it started`.
  - **`keyframe decoded - the picture is repaired` non-zero** — 5 and 6 in the last two rounds. Zero
    on a rendering session is a FAIL.
  - **One** `fragment accounting established for` per fragmenting channel, both flat **-29
    bytes/fragment**.
  - No `FATAL EXCEPTION` / `AndroidRuntime`; `rendered=` never 0.
  - `parameter sets changed mid-session`: 2 in each of the last two rounds. Note the count; it is
    settled and is not a condition.

### R5 — the read-desync fix is still silent (no new run)

Counted across R1-R4. **PASS: zero** of the four lines in §5. This matters slightly more this round:
two of the readers' hot paths were edited, and a dropped fragment that took the framing with it would
show up here first.

## 7. Do not re-run

- **#852**, the airplane-mode disconnect, the natural ~68-69 s keyframe cadence, and focus mode 4.
  Settled; quote, do not remeasure.
- **`HIDE_START_CODE` (mode 4).** Round 3's R4 passed it and nothing here touches the discard path.
- **Whether the holed-keyframe fix or the idle-screen fix works.** Answered in rounds 2 and 3.
- **The `restart suppressed` ladder under sustained loss.** Diagnosed as the decoder's 15 s keyframe
  patience, out of scope, expected in R2.
- **Whether the audit produces false positives on a clean stream.** R4 is the third measurement of
  this; if it is zero again, stop asking.

## 8. Report back

1. **Does the audit's detection reach recovery?** (R1.) The `DELTA_CHANGED on VIDEO` count and the
   `fragment run lost bytes` count. These two lines existing at all is the round's first question.
2. **Did the hold finally engage?** (R1.) The count of `holding the cycle until it settles`, first
   and last quoted in full. Round 3's answer was zero.
3. **How long does the picture take to come back once the loss stops?** (R1.) One number in seconds
   against round 3's 66.9 and round 2's 63.1.
4. **Is the new mode testing something the old one could not?** (R1 vs R3.) The same four counts from
   both runs, side by side. If they match, say so plainly — it would mean the stage separation does
   not do what the code claims.
5. **Does the hold have a floor?** (R2.) Did a cycle still fire, and did any hold line exceed
   60000 ms?
6. **Does a clean stream ever see any of it?** (R4.) Three zeroes, or the finding.
7. **Did the renderer-banner line appear in any run**, and did it save a capture? Round 3 lost one to
   its absence.
8. **Anything in a capture none of the above asked about.**
