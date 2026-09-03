# release/next — round 3 brief

## 1. Build

**Candidate:** `fork/fix/video-stack` @ `e1c00ec7471b33dc6dfbfe214a114a5255b29d1b` (short `e1c00ec7`).

```bash
git fetch fork fix/video-stack
git checkout e1c00ec7
```

**One new commit on round 2's candidate**, which is otherwise unchanged:

```
* e1c00ec7  Video: hold the focus cycle until the stream stops losing frames   <- new
* 6911d3c5  Video: bound the fault injector to a fault budget                  <- round 2's tip
* 7a3242ab  Video: a keyframe repairs the picture only when it decodes
* ecc247d2  Projection: an idle Android Auto screen is not a lost connection
* 90b9958a  Transport: record when the phone last spoke, not just when it last sent a picture
* 14bad4f2  Transport: a read that loses bytes ends the session instead of skipping the message
* d43112e2  Video: wait for a keyframe instead of rebuilding the decoder that needs one
* 0e6d5747  Video: configure the decoder from the stream, and name it when it is the bottleneck
* 40e6c4eb  Video: size the pipeline to the device instead of to a guess
* 53dfd66a  Video: never assemble a frame without its first fragment, and instrument the rest
```

Only three files changed, all in the escalation path: `KeyframeCycleEscalationPolicy.kt`,
`AapTransport.kt` and that policy's test. **Nothing in the decoder, the reassembler, the reader or the
injector was touched**, so round 2's R0, R3, R4 and R5 results still stand on their own terms — this
round re-runs two of them only as regression controls, not to re-answer them.

**No baseline APK.** `versionCode` unchanged at **98**. The new commit has never been compiled; R0 is
its first compile.

## 2. What this is and why it exists

Round 2 answered its question and found a better one underneath it.

**What round 2 established.** The holed-keyframe fix works: cycle 1 fired at `2001ms` (R1) and
`2000ms` (R2) where round 1's R6 took `62003ms`. R3 produced zero false repairs across 53 discarded
frames, R4 six genuine repairs on a clean session, R0 486/486.

**What round 2 measured without naming.** Its R2 stopped the injected loss at a known instant and the
picture took **63.1 s** to come back — but the lever itself took **0.96 s** of that. The other 62 are
one decision:

```
09:57:30.452  cycling video focus (1/3)              <- 8.1s BEFORE the loss stopped
09:57:38.554  fault injection budget spent           <- the wire goes clean here
09:57:38.469  no cycle available now (1/3 spent)     <- the cooldown blocks the retry
09:58:38.470  cycling video focus (2/3)              <- 60.001s later, to the millisecond
09:58:39.434  keyframe decoded - the picture is repaired
09:58:41.672  rendered=101
```

Cycle 1 was spent on a wire that was still losing frames. The keyframe it bought was broken like
everything else on that wire, and firing it stamped `CYCLE_COOLDOWN_MS = 60_000`, which then held off
cycle 2 — the one that worked — for a full minute. Two of the three cycles were unspent the whole
time. **The 63.1 s was not the escalation failing. It was the escalation succeeding at the wrong
moment.**

**The fix.** `AapVideo`'s corruption path already reaches `AapTransport` as the only caller that means
*an access unit arrived broken on the wire* — the three decoder callers mean *the decoder has no
picture*, which is the consequence and outlives the fault. So the transport now stamps when the wire
last broke, and `KeyframeCycleEscalationPolicy` refuses to spend a cycle until the wire has been quiet
for `CORRUPTION_QUIET_MS = 2000` (twice the report throttle, so one suppressed report cannot read as
quiet). The hold is bounded: after `DEFER_FOR_QUIET_LIMIT_MS = 60_000` of unrepaired picture the cycle
goes out regardless, because past that the wait has already cost as much as the wasted cycle it was
avoiding.

For the fault this escalation was actually written for — **one** lost frame on an otherwise clean wire
— nothing changes at all. There the corruption stamp and the unrepaired stamp are the same instant, so
both windows elapse together and the cycle fires exactly when it always has. A unit test pins that.

## 3. What is different about this round, and one correction to round 2

- **The `but restart suppressed` ladder is expected, is not this round's business, and must not be
  reported as a FAIL.** Round 2 failed its R1 on it and attributed it to `CYCLE_COOLDOWN_MS`. That
  attribution is wrong. The ladder is `DecoderStallCausePolicy.KEYFRAME_STARVATION_PATIENCE_MS =
  15_000`: the starvation branch holds a rebuild only while the stall is under fifteen seconds, then
  deliberately hands back to the ordinary rebuild path so a genuinely dead codec still gets rebuilt.
  Round 2's own numbers show it — the starvation line reads `10004ms`, every ladder line reads
  `20012 / 30019 / 40022ms`. **Nothing in this round changes that threshold**, so R2 below will
  produce ladders again and that is the correct outcome, not a regression.
- **One new line, and it is the mechanism made visible:** `holding the cycle until it settles`. Its
  *presence* during injection is the fix working. Its presence on a clean session would be a bug.
- **The headline number is a single stopwatch: budget-spent line → first non-zero `rendered=`.**
  Round 2 measured 63.1 s on the same run. Under 10 s is the bar, and the route should be visible in
  between: one or more `holding the cycle` lines, then `cycling video focus`, then
  `keyframe decoded - the picture is repaired`.
- **`keyframe reached the codec` still does not mean the picture is repaired.** It means a keyframe was
  *fed*, and a holed one prints it too. The repair line is `keyframe decoded - the picture is
  repaired`. This tripped round 1 and the warning stands.
- **Fault density floor is unchanged.** A run below **30 `FAULT INJECTED`** is INCONCLUSIVE and must be
  re-run, not reported. Round 1 drew 90 where an earlier round drew 2 at the same setting — dosage
  follows what the phone is showing, not the rate key. The 15 s
  `fault injection - … candidates seen … injected` summary tells you during the run. Round 2 hit 37,
  30/30 and 53 without priming the content, so this is unlikely to bite, but check it before reporting.
- **`SURFACE` for every run**, clean control included. Every video number on record was measured there.
- `log-level=2` (INFO) carries every line this round needs; none are behind a `LOG_VERBOSE` guard,
  checked against the guard rather than the call.
- **Grep every capture with `-a`.** These captures read as binary otherwise, and `grep -c` then prints
  nothing and exits 1 instead of printing `0` — a refused count looks exactly like an absent pattern,
  which is how most conditions below are phrased.
- **Self-wake noise.** Round 2 saw `MATCH! Starting AapService` in all four runs and once it caused a
  real group recreation, and flagged it rather than discarding. That judgment was right and stands:
  apply the discard rule to whether a **second session reached the measurement window** (count SSL
  handshakes and `p2p-wlan0-N` interface indices), not to the `MATCH!` line itself.

## 4. Settings

Types: `log-level`, `view-mode`, `debug-video-fault-injection`, `debug-video-fault-rate` and
`debug-video-fault-budget` are **int**; `video-codec` is **string**; `force-software-decoding` is
**boolean**. "delete" means the removal half of the template's §1 only. Use `set_hu_prefs.sh`.

| Key | R1 | R2 | R3 | R4 |
|---|---|---|---|---|
| `log-level` | `2` | `2` | `2` | `2` |
| `view-mode` | `0` (SURFACE) | `0` | `0` | `0` |
| `video-codec` | `H.264` | `H.264` | `H.265` | `H.264` |
| `debug-video-fault-injection` | `2` | `2` | delete | `4` |
| `debug-video-fault-rate` | `3` | `3` | delete | `2` |
| `debug-video-fault-budget` | `30` | delete | delete | delete |
| `force-software-decoding` | delete | delete | delete | delete |
| `software-video-decoder` | delete | delete | delete | delete |

These are round 2's four settings columns re-ordered, nothing new. **R1 here is round 2's R2**, **R2
here is round 2's R1**, **R3 here is round 2's R4**, **R4 here is round 2's R3** — the order changed
because the bounded run is now the headline and runs first.

## 5. The lines that decide the round

Every fixed substring below was verified against the candidate. Lines the code builds by
concatenation are marked; grep the fixed part given, never the whole sentence.

**New on this commit:**

| Line | Level | Means |
|---|---|---|
| `- holding the cycle until it settles` (composed: full line is `AapTransport: picture unrepaired for Nms but the stream is still losing frames (last Nms ago) - holding the cycle until it settles (N/3 spent)`) | W | **the fix doing its work.** A cycle was earned and deliberately not spent, because the wire was still breaking. Both `Nms` values matter: the first is how long the picture has been broken, the second how recently the wire last lost a frame |
| `but the stream is still losing frames` | W | the same line — use whichever half greps more cleanly |

**Unchanged, and what decides each run:**

| Line | Level | Means |
|---|---|---|
| `AapVideo: fault injection budget spent after` (composed: `… N faults - the stream is clean from here`) | W | **R1's stopwatch starts here.** The injector is done |
| `Throughput over ` (composed: `…Nms: rendered=N (Nfps), fed=N, dropped=N`) | I | the stopwatch stops at the first non-zero `rendered=` after that |
| `cycling video focus` | W | a cycle spent, with `(N/3)` and the unrepaired time |
| `retaking video focus` | W | its second half |
| `VideoDecoder: keyframe decoded - the picture is repaired` | I | **the repair, and the only line that means it** |
| `VideoDecoder: keyframe reached the codec` | I | a keyframe was **fed**. Says nothing about the picture |
| `no cycle available now` | W | the budget or the cooldown refused. Distinct from the new hold line — do not conflate them |
| `Decoder has had no keyframe since it started` | W | the starvation branch deferring a rebuild |
| `but restart suppressed` (composed: `Decoder stall detected (no output for Nms) but restart suppressed (N/4 used, 8000ms cooldown)`) | W | the sync-stall watchdog out of budget. **Expected in R2 — see §3** |
| `Codec initialized:` | I | one per codec build |
| `AapVideo: FAULT INJECTED` | W | one injected fault |
| `AapVideo: fault injection - ` (composed, ends `…, no budget` or `…, budget N/M`) | W | the 15 s summary |
| `Discarding the frame instead of assembling it headless` | W | R4's counterpart to a fault |
| `never carries a keyframe's timestamp through to its ` (composed, codec name prepended) | W | the timestamp fallback fired. **Its appearance is a finding** — report it and treat every timing that run as approximate |
| `a focus cycle is already in flight` | W | the other escalation held the lever. Rare; report if seen |
| `DELTA_CHANGED` (composed) | W | the framing audit firing. Must be zero on a clean session |
| `parameter sets changed mid-session` | W | the encoder reconfigured — see R3 |

**Read-desync, for R5 — every one ends the session:**
`AapRead: fragment total read returned`, `AapRead: body read returned`, `AapRead: declared message size`,
`Disconnecting to resync`.

## 6. Runs

### R0 — build and unit-test gate

`build_hur.sh`, then `run_unit_tests.sh`.

- **PASS:** compiles, suite reports **495** tests — round 2's 486 plus 9 for
  `KeyframeCycleEscalationPolicy`. All green.
- **FAIL:** stops the round. Quote the compiler output. The only unbuilt code is
  `KeyframeCycleEscalationPolicy.kt`, `AapTransport.kt` and `KeyframeCycleEscalationPolicyTest.kt`.

### R1 — the picture comes back as soon as the loss stops

`debug-video-fault-injection=2` (`DROP_MIDDLE_FRAGMENT`), rate `3`, **budget `30`**, H.264, `SURFACE`.
**Four minutes minimum, and at least two of them after the budget-spent line.** If the budget is not
spent by three minutes, keep capturing until it is, then two more minutes.

This is round 2's R2 verbatim, on the fixed code, and it is the whole point of the round.

- **Report the gap in seconds** from `fault injection budget spent after` to the first `Throughput`
  window with a non-zero `rendered=`. **Round 2's answer on this exact run was 63.1 s.**
- **PASS, all three:**
  1. The gap is **≤ 10 s** — the bar round 2 could not reach.
  2. **At least one `holding the cycle until it settles` line appears before the budget-spent line.**
     Zero of them means the hold never engaged and the result, pass or fail, measured something else.
     Quote the first and last one in full.
  3. The route is visible: a `cycling video focus` **after** the budget-spent line, followed by
     `keyframe decoded - the picture is repaired`. Report the gap from the budget-spent line to that
     `cycling video focus` separately — it should be roughly **2-4 s**, and it is the direct
     measurement of `CORRUPTION_QUIET_MS`.
- **FAIL:** still `rendered=0` **90 s** after the budget-spent line, or the recovery came from a
  `cycling video focus` that fired *before* the budget was spent (that would mean the hold did not
  engage and round 2's timing repeated by luck).
- **A finding either way:** if the gap is between 10 s and 90 s, say whether a cycle had already been
  spent before the budget-spent line, and how many `holding the cycle` lines preceded it. Those two
  numbers separate "the hold engaged and was not enough" from "the hold never engaged".
- **Also record:** total `cycling video focus` lines and the `(N/3)` on each, total
  `holding the cycle until it settles`, total `Codec initialized:`, longest run of consecutive
  `rendered=0` windows, and whether `never carries a keyframe's timestamp` appeared.

### R2 — the hold must not starve the budget

Same as R1 but **no budget** — inject for the whole session. **Three minutes.** This is round 2's R1.

Nothing can hold a picture while one middle fragment in three is being dropped, and this run is not
asked to. Its only job is to prove the hold has a floor: a wire that never settles must still reach
the lever, and a stream that stays broken must not sit on an unspent budget forever.

- **PASS, both:**
  1. **`cycling video focus` still appears at least once.** The `DEFER_FOR_QUIET_LIMIT_MS = 60_000`
     ceiling exists to guarantee this. **Zero cycles across a three-minute run with the picture dead
     is the FAIL this run is for** — it would mean the hold became a permanent refusal.
  2. **No `holding the cycle until it settles` line reports a first value above `60000ms`.** That
     first `Nms` is how long the picture has been unrepaired; above 60000 would mean the ceiling
     itself is not firing.
- **Expected and NOT a FAIL:** the `but restart suppressed` ladder, repeated exhaustion at `4/4`, and
  a `Codec initialized:` count in the mid-teens. Round 2 saw 13 ladder lines and 17 codec inits over
  6m15s. That is `KEYFRAME_STARVATION_PATIENCE_MS`, it is out of this round's scope, and re-reporting
  it as a regression would repeat round 2's misattribution. Record the counts; do not score them.
- **Record:** every `cycling video focus` line in full, the count of
  `holding the cycle until it settles`, and the largest first-`Nms` value any of them reported.

### R3 — clean session regression

No injection. **Ten minutes**, H.265, `SURFACE`, Android Auto's default screen, left alone. Round 2's
R4. Its job here is to prove the hold costs a healthy stream nothing.

- **PASS, all:**
  - **Zero** `holding the cycle until it settles`. On a clean wire nothing stamps corruption, so
    nothing can ever be held. **Any occurrence here is the round's most important finding.**
  - **`Codec initialized:` exactly 1.**
  - **Zero** `cycling video focus`, **zero** `retaking video focus`, **zero**
    `Decoder has had no keyframe since it started`, **zero** `never carries a keyframe's timestamp`.
  - **`keyframe decoded - the picture is repaired` non-zero** — round 2 saw 6 over ten minutes against
    a ~69 s natural cadence. Zero on a rendering session is a FAIL: it would mean the signal the whole
    escalation reads has gone inert.
  - **Zero** `DELTA_CHANGED`. No `FATAL EXCEPTION` / `AndroidRuntime`. `rendered=` never 0.
- **One question carried over.** Round 2 saw `parameter sets changed mid-session` **twice with zero
  phone interaction**, which overturned round 1's attribution of it to map panning. Report the count
  again with the session left alone. Two rounds agreeing that this rig's encoder reconfigures unaided
  settles it as a property of the stream rather than of anything we do, and that matters to work not
  yet written. Not a FAIL unless it repeats on every keyframe.

### R4 — corruption alone must not produce a hold

`debug-video-fault-injection=4` (`HIDE_START_CODE`), rate `2`, H.264, `SURFACE`. **Three minutes.**
Round 2's R3, shortened.

This mode makes `AapVideo` discard whole frames *before* the decoder sees them, so it stamps the new
corruption clock on every fault while never damaging the codec. That makes it the one run that
isolates the stamp from its consequences: the wire is reported broken constantly, and the picture is
fine.

- **PASS, all:**
  - **Zero** `holding the cycle until it settles`. There is nothing to hold, because the escalation
    clock is never armed by this path — a corruption report on its own does not start it.
  - **Zero** `cycling video focus`, **zero** `Decoder has had no keyframe since it started`, **zero**
    `but restart suppressed`.
  - **`Codec initialized:` exactly 1.**
  - `FAULT INJECTED` non-zero, matched 1:1 by `Discarding the frame instead of assembling it
    headless`. Round 2 measured 53 and 53.
- **FAIL:** any hold line or any focus cycle. The decoder is fed clean frames throughout, so anything
  here means the new stamp is arming something it should not.

### R5 — the read-desync fix is still silent (no new run)

Counted across R1, R2, R3 and R4's captures.

- **PASS: zero** of the four lines in §5. Round 2 was zero across all four of its captures.
- **FAIL: any of them on a session that was otherwise healthy.** Quote with twenty lines either side.

## 7. Do not re-run

- **#852**, the airplane-mode disconnect, and the natural ~68-69 s keyframe cadence. Settled; quote,
  do not remeasure.
- **Focus mode 4 / `PROJECTED_NO_INPUT_FOCUS`.** Measured inert, removed from the code.
- **Whether the holed-keyframe fix works.** Round 2 answered it: cycle 1 at 2001 ms against round 1's
  62003 ms, zero false repairs across 53 discarded frames, 6 genuine repairs on a clean session. This
  round assumes it.
- **The `restart suppressed` ladder under sustained loss.** Diagnosed, out of scope, expected in R2.
  See §3.
- **The framing audit's `-29 bytes/fragment` baseline and the `access unit classified` answers.**
  Confirmed in round 2; note them if they change, do not make them conditions.

## 8. Report back

1. **How long does the picture take to come back once the loss stops?** (R1.) One number in seconds
   against round 2's 63.1. Plus the budget-spent → `cycling video focus` gap, which is the direct read
   on the two-second quiet window.
2. **Did the hold actually engage?** (R1.) The count of `holding the cycle until it settles`, and the
   first and last one quoted in full. A pass with zero hold lines is not a pass — it measured a
   different run.
3. **Does the hold have a floor?** (R2.) Did `cycling video focus` still fire on a wire that never
   settles, and did any hold line report more than 60000 ms of unrepaired picture?
4. **Does a clean stream ever see the new line?** (R3, R4.) Both should be zero, for different
   reasons: R3 has no corruption to stamp, R4 has constant corruption but nothing to hold.
5. **`parameter sets changed mid-session` on R3**, with the session left alone. Round 2 saw 2.
6. **Anything in a capture none of the above asked about.**
