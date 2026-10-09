# release/next — round 2 brief

## 1. Build

**Candidate:** `fork/fix/video-stack` @ `6911d3c5eeef6dc369891158733ef51a742d0385` (short `6911d3c5`).

```bash
git fetch fork fix/video-stack
git checkout 6911d3c5
```

**One branch now, nine commits on `main`, linear — no merge commits:**

```
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

This replaces round 1's `release/next` @ `e2e65855`, which was the same work as three merge commits.
The two trees are byte-identical up to the last two commits (verified: `git diff` between the merged
and the flattened tip is empty), so **every round 1 result still applies to the first seven commits**
— nothing was re-resolved and nothing needs re-testing because of the flattening.

**No baseline APK this round.** #852 was answered in round 1 and none of its code has been touched
since; this round has one build and one question. `versionCode` is still **98**, unchanged.

**Round 1's R6 failure is fixed on the last two commits, and they have never been compiled.** R0 is
their first compile.

## 2. What this is and why it exists

Round 1's R6 wedged: two exhausted 4/4 stall ladders, 9 codec inits, ~90 s of `rendered=0`, and an
escalation that only reached 2/3 after 62 seconds — on the same injected fault video-pipeline round 2
measured recovering in 0.544 s and 0.557 s. The results doc read that as the merge undoing the fix,
and named `AapTransport.kt`.

**It is not a merge defect, and no bisection round is needed.** `fix/session-liveness`'s hunks in
that file add one timestamp field and touch nothing the escalation reads — not `unrepairedSinceMs`,
not the cycle budget, not `FocusCycleLever`. The merge's only behavioural change anywhere near the
wedge is that `maybeRecoverFromDisplayStall()` becomes reachable when the overlay stays hidden, and
that function returns at its first guard on the `SURFACE` backend, which is what R6 ran.

**What R6 actually found is a real defect in `fix/video-pipeline`, and round 1 is the first run that
could ever have hit it.** R6 injected **90** faults; video round 2's supposedly identical run injected
**2**. The injector's dosage is a function of how often the phone's content fragments, not of the
setting, so "the same fault at the same rate" was two different experiments. Sustained loss had never
been put through the fix.

The defect: `VideoKeyframeScanner` reads the NAL headers at the **head** of an access unit, so one
that lost a fragment in the *middle* still carries its parameter sets and IDR slice and still scans as
a keyframe. It is fed, it is logged, and it decodes to nothing. Two decisions read that as "the
picture is fine again":

- `DecoderStallCausePolicy` stopped calling the codec starved, so the output watchdog went back to
  rebuilding it — and each rebuild resumes on a P-frame, which is the loop the starvation branch was
  written to break.
- `AapTransport`'s escalation clock was cancelled, so the focus cycle that would have fetched a *real*
  keyframe was pushed behind the next drop. That is where R6's 62 seconds came from.

At one middle fragment in three, roughly a third of keyframes are holed and both resets fire over and
over. **The fix moves the repair signal to the far side of the codec:** a keyframe is pending until a
frame with at least its presentation timestamp comes out. The starvation branch and the escalation
clock both now read that instead.

The second commit exists because R6 could not have answered the question that matters anyway.
Continuous injection only measures that a stream still being broken stays broken; every recovery lever
in the app is bounded per session, so a run with no end to the faults ends wedged whatever the code
does. `debug-video-fault-budget` stops the injector after N faults, so **one capture holds the damage
and the repair**.

## 3. What is different about this round

- **`keyframe reached the codec` has changed meaning, and round 1's brief is now wrong about it.** It
  used to mean the picture was repaired; it now means only that a keyframe was *handed to* the codec,
  which is exactly the event that turned out to prove nothing. The repair line is the new
  `keyframe decoded - the picture is repaired`. **Every PASS condition below that says "the cycle
  worked" keys on the new line.** Grepping the old one will make a wedge look like a recovery.
- **Fault density is the round's main hazard, and R1 has a floor because of it.** Round 1 got 90
  injections where video round 2 got 2, at the same setting. A run below **30 `FAULT INJECTED`** is
  INCONCLUSIVE for R1 and must be re-run, not reported. The 15 s
  `fault injection - … candidates seen … injected` summary tells you during the run: candidates
  climbing with injections flat means lower the rate toward `2`; candidates barely moving means the
  content is not fragmenting, so change what the phone is showing (Maps with the map panned, not a
  static list) and restart.
- **R1 cannot recover the picture and is not asked to.** While one middle fragment in three is being
  dropped, no lever in the protocol can hold a picture. R1 measures the *mechanism*: that the decoder
  waits instead of burning its restart budget, and that the escalation fires on schedule instead of
  62 s late. R2 is where recovery is measured, after the faults stop.
- **A bad picture in R1, R2's damage phase and R3 is the correct outcome.** Injection is on purpose
  and at a rate far past anything real.
- **`SURFACE` for every run**, including the clean control. Every video number on record was measured
  on that backend and they only compare if the backend matches.
- `log-level=2` (INFO) carries every line this round needs — all `AppLog.i` or `AppLog.w`, none behind
  a `LOG_VERBOSE` guard, checked against the guard rather than the call. No run here counts `RECV:`.
- **Grep every capture with `-a`.** These captures are long enough that `grep` calls them binary, and
  `grep -c` then prints nothing and exits 1 rather than printing `0` — a refused count reads exactly
  like an absent pattern, which is how most of the conditions below are phrased.

## 4. Settings

Types: `log-level`, `view-mode`, `debug-video-fault-injection`, `debug-video-fault-rate` and
`debug-video-fault-budget` are **int**; `video-codec` is **string**; `force-software-decoding` is
**boolean**. "delete" means run only the removal half of the template's §1. Use `set_hu_prefs.sh` —
every run writes more than one key.

| Key | R1 | R2 | R3 | R4 |
|---|---|---|---|---|
| `log-level` | `2` | `2` | `2` | `2` |
| `view-mode` | `0` (SURFACE) | `0` | `0` | `0` |
| `video-codec` | `H.264` | `H.264` | `H.264` | `H.265` |
| `debug-video-fault-injection` | `2` | `2` | `4` | delete |
| `debug-video-fault-rate` | `3` | `3` | `2` | delete |
| `debug-video-fault-budget` | delete | `30` | delete | delete |
| `force-software-decoding` | delete | delete | delete | delete |
| `software-video-decoder` | delete | delete | delete | delete |

`debug-video-fault-budget` is new this round. Deleted (or `0`) means the old behaviour: inject for the
whole session. R5 is counted from captures already taken and needs no settings and no rig time.

## 5. The lines that decide the round

Every fixed substring below was verified with `grep -F` against the candidate. Lines that interpolate
values mid-sentence are marked — grep the fixed part given, never the whole sentence.

**New on this branch:**

| Line | Level | Means |
|---|---|---|
| `VideoDecoder: keyframe decoded - the picture is repaired` | I | **the repair, and the only line that now means it.** A keyframe reached the codec *and* produced output |
| `never carries a keyframe's timestamp through to its ` (composed: the codec's name is prepended) | W | the fallback guard fired: this component's output timestamps are unusable, so repairs are being read from frames arriving rather than from the frame that brought them. **Its appearance is a finding** — report it, and treat every timing below as approximate for that run |
| `AapVideo: fault injection budget spent after` (composed: `… N faults - the stream is clean from here`) | W | **R2's stopwatch starts here.** The injector is done; everything after it is the app recovering |
| `AapVideo: fault injection - ` (composed, now ends `…, no budget` or `…, budget N/M`) | W | the 15 s summary, with the budget state added |

**Changed meaning — read §3:**

| Line | Level | Now means |
|---|---|---|
| `VideoDecoder: keyframe reached the codec` | I | a keyframe was **fed**. Says nothing about the picture. A holed one prints this too |

**Pre-existing, and what the round counts:**

| Line | Level | Means |
|---|---|---|
| `Codec initialized:` | I | one per codec build. R1's primary count |
| `Throughput over ` (composed: `…Nms: rendered=N (Nfps), fed=N …`) | I | the global sentinel and the recovery clock |
| `Decoder has had no keyframe since it started` | W | the starvation branch deferring a rebuild. **R1 wants these** |
| `cycling video focus` | W | a focus cycle spent to force a keyframe, with `(N/3)` and the unrepaired time |
| `retaking video focus` | W | its second half |
| `but restart suppressed` (composed: `Decoder stall detected (no output for Nms) but restart suppressed (N/4 used, 8000ms cooldown)`) | W | **the pre-fix wedge signature.** R1's FAIL |
| `AapVideo: FAULT INJECTED` | W | one injected fault, with its denominator |
| `AapRead: fragment accounting established for` | I | the framing audit's baseline, once per fragmenting channel |
| `DELTA_CHANGED` (composed) | W | the framing audit firing. Must be zero on a clean session |
| `access unit classified` | I | the config scanner's answer, once per distinct kind per session |
| `parameter sets changed mid-session` | W | the encoder reconfigured |

**Read-desync, for R5 — every one of these ends the session:**
`AapRead: fragment total read returned`, `AapRead: body read returned`, `AapRead: declared message size`,
`Disconnecting to resync`.

## 6. Runs

### R0 — build and unit-test gate

`build_hur.sh` on the candidate, then `run_unit_tests.sh`.

- **PASS:** compiles, and the suite reports **486** tests — round 1's 472 plus 14 (9 for the new
  `KeyframeRepairTracker`, 1 for `DecoderStallCausePolicy`, 4 for the injector budget). All green.
- **FAIL:** stops the round. Quote the compiler output. The last two commits are the only unbuilt
  code; the files to name are `KeyframeRepairTracker.kt`, `VideoDecoder.kt`, `VideoFaultInjector.kt`,
  `Settings.kt` and `SettingsFragment.kt`.

### R1 — sustained loss is a wait, not a restart storm

`debug-video-fault-injection=2` (`DROP_MIDDLE_FRAGMENT`), rate `3`, **no budget**, H.264, `SURFACE`.
**Three minutes.** This is round 1's R6, verbatim, on the fixed code.

- **INCONCLUSIVE below 30 `FAULT INJECTED`.** See §3; re-run rather than report.
- **The picture will be bad throughout, and that is correct.** Nothing can hold a picture while one
  middle fragment in three is being dropped. This run is about what the app does with that.
- **PASS, all four:**
  1. `Decoder has had no keyframe since it started` **appears** — the starvation branch is now
     reachable, where round 1 never printed it once.
  2. **No `but restart suppressed` ladder.** Round 1 ran 20/30/40 s then 20/30/40/50 s, twice
     exhausting 4/4. Zero is the expectation; a single suppressed line late in the run is worth
     reporting but is not the ladder.
  3. **The first `cycling video focus` lands within ~5 s of the first `FAULT INJECTED`**, and its own
     text says `picture unrepaired for Nms` — that N should be near 2000, not near 62000. **Quote
     every `cycling video focus` line in full**; the unrepaired time in each is the number this whole
     fix is about.
  4. **`Codec initialized:` well under round 1's 9.** The starvation branch holds a rebuild for 15 s
     at a time, so three minutes cannot produce many. Report the exact count.
- **FAIL:** the `restart suppressed` ladder returns, or the first cycle is again tens of seconds late.
- **Record either way:** total `Codec initialized:`, every `cycling video focus` line, count of
  `keyframe decoded - the picture is repaired`, longest run of consecutive `rendered=0` windows, and
  whether `never carries a keyframe's timestamp` ever appeared.

### R2 — the picture comes back when the loss stops

Same as R1 but `debug-video-fault-budget=30`. **Four minutes minimum, and at least two of them after
the budget-spent line** — if the budget is not spent by the three-minute mark, keep capturing until it
is, then two more minutes.

This is the question round 1 never asked. One capture, two halves, and the boundary is the
`fault injection budget spent after 30 faults - the stream is clean from here` line.

- **Report the gap**, in seconds, from that line to the first `Throughput` window with a non-zero
  `rendered=`. That single number is this round's headline deliverable.
- **PASS:** the picture returns within **10 s** of the budget-spent line, and the log says by which
  route — a `cycling video focus` followed by `keyframe decoded - the picture is repaired`, or an
  unaided keyframe. Then `rendered=` stays non-zero for the rest of the capture.
- **FAIL:** still `rendered=0` **90 s** after the budget-spent line. That is longer than the phone's
  own ~69 s keyframe cadence, so it means nothing recovered it and nothing was going to.
- **A finding either way:** if recovery took between 10 s and 90 s, say which route brought it and
  whether the cycle budget was already spent (`cycling video focus (3/3)` earlier in the capture).
  Waiting out a GOP because the budget was gone is a different problem from the escalation not firing,
  and only the log can tell them apart.
- If the injector never reaches 30 faults, the run still counts: report what it reached, and treat the
  last `FAULT INJECTED` as the boundary instead.

### R3 — the repair signal must not fire on frames that were merely discarded

`debug-video-fault-injection=4` (`HIDE_START_CODE`), rate `2`, H.264, `SURFACE`. **Five minutes.**
This mode makes `AapVideo` discard whole frames before the decoder sees them, so the codec is never
handed anything broken — the one way the new render-side check could have made things worse is by
firing here. Video round 2 measured exactly one codec init and zero starvation lines on this mode.

- **PASS:** `Codec initialized:` exactly **1**; **zero** `Decoder has had no keyframe since it
  started`; **zero** `cycling video focus`; **zero** `but restart suppressed`. `FAULT INJECTED` should
  be non-zero and each should be matched 1:1 by `Discarding the frame instead of assembling it
  headless`.
- **FAIL:** any starvation line or focus cycle. The decoder is being fed clean frames throughout, so
  anything here is the new signal misreading a healthy codec.

### R4 — clean session regression

No injection. **Ten minutes**, H.265, `SURFACE`, Android Auto's default screen. This is round 1's R5
re-run, and its job now is to prove the new render-side check costs a healthy stream nothing.

- **`Codec initialized:` exactly 1.**
- **Zero** `Decoder has had no keyframe since it started`, **zero** `cycling video focus`, **zero**
  `retaking video focus`, **zero** `never carries a keyframe's timestamp`.
- **`keyframe decoded - the picture is repaired` should appear**, on the phone's own keyframe cadence
  — roughly one per ~69 s, so single digits over ten minutes. **Zero of them on a session that is
  rendering is a FAIL**: it would mean the repair signal never fires at all, which is the way this fix
  could be silently inert.
- **Zero** `DELTA_CHANGED`.
- **One** `AapRead: fragment accounting established for` per fragmenting channel; quote the whole line
  (the per-fragment delta was a flat **-29 bytes**).
- All three `access unit classified` answers present once each in the first moments.
- `parameter sets changed mid-session`: round 1 saw **4** on this rig and attributed them to the map
  panning it added. If this run is left alone and still produces them, that attribution was wrong —
  say so. They are only a FAIL if they repeat on every keyframe.
- No `FATAL EXCEPTION` / `AndroidRuntime`, `rendered` steady throughout.

### R5 — the read-desync fix is still silent (no new run)

Counted across R1, R2, R3 and R4's captures.

- **PASS: zero** of the four lines in §5. Each one now *ends the session* where the old code skipped a
  message and carried on.
- **FAIL: any of them on a session that was otherwise healthy.** Quote it with twenty lines either
  side; whether the session actually died is the whole question.

## 7. Do not re-run

- **#852.** Answered in round 1 — reproduced 6× on `v.3.2.5`, zero on the candidate, recovery
  confirmed still firing, idle cadence measured at 679-758 ms. None of that code has changed.
- **R4 of round 1, the airplane-mode disconnect.** Passed; untouched since.
- **The natural ~68-69 s keyframe cadence.** Quote it; do not remeasure.
- **Focus mode 4 / `PROJECTED_NO_INPUT_FOCUS`.** Measured inert and removed from the code.
- **Feed queue depth, the dropped-reference-frame chain, the black-screen work.** Settled in their own
  threads.
- **Whether the flattening changed anything.** The flattened tree is byte-identical to round 1's
  merged tree up to the last two commits. Verified here; not a rig question.

## 8. Report back

1. **Does sustained loss still wedge the decoder?** (R1.) The four conditions, and the unrepaired
   time quoted from every `cycling video focus` line. Round 1's was 62003 ms.
2. **How long does the picture take to come back once the loss stops?** (R2.) One number in seconds,
   plus the route that brought it. This has never been measured.
3. **Does the new repair signal fire when it should and stay quiet when it should not?** (R3, R4.)
   R3's zeroes say it does not fire on discarded frames; R4's non-zero count of
   `keyframe decoded - the picture is repaired` says it fires at all on a healthy stream. Both halves
   are needed — a signal that never fires would pass R3 for the wrong reason.
4. **Did `never carries a keyframe's timestamp` appear in any run?** If it did, this rig's decoder does
   not carry presentation timestamps to its output, every timing in this round is approximate, and
   that is worth more than any single verdict here.
5. **Anything in a capture that none of the above asked about.**
