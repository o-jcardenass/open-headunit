# release/next — round 5 brief

## 1. Build

**Candidate:** `fork/fix/video-stack` @ `64033c4a511a9a337adeec5871298e55ada46a42` (short `64033c4a`).
`fork/fix/relaunch-onto-idle-screen` points at the same commit; either name works.

```bash
git fetch fork fix/video-stack
git checkout 64033c4a
```

**Three new commits on round 4's candidate:**

```
* 64033c4a  Projection: a relaunch onto an idle screen can still ask for the picture back   <- new
* bf38924d  Projection: say which of the four situations put the renderer banner up         <- new
* 4cf2451d  Video: a dropped fragment is still decrypted, so the session survives it        <- new
* bbf328e8  Video: a fragment run that lost bytes asks for a keyframe, …                    <- round 4's tip
* e1c00ec7  Video: hold the focus cycle until the stream stops losing frames
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

**No baseline APK.** `versionCode` unchanged at **98**. None of the three new commits has been
compiled; R0 is their first compile.

**This round carries two threads on one candidate**, so the rig runs one session instead of two. R1-R3
finish the question round 4 could not answer; R4-R5 test a separate fix, for a user-facing bug on the
#852 reporter's unit.

## 2. What this is and why it exists

### The half round 4 could not test

Round 4's mode `5` killed the connection within seconds of its first fault — four times in one
capture, dying after 1, 13, 1 and 4 injected faults — so R1 and R2 were UNTESTABLE.

**Your diagnosis was right and it is fixed.** The drop discarded the message's bytes *before*
`AapMessageIncoming.decrypt()`. `AapSslContext.decrypt()` calls `SSLEngine.unwrap()`, whose TLS
record sequence advances per record, and the phone advances its own for every record it encrypts
whether or not we look at it. A record we never unwrap left our engine permanently one behind and
every later unwrap failed authentication. The framing genuinely stayed intact the whole time, exactly
as your R5 showed — the desync was one layer up, where nothing was watching.

Both readers now resolve the injector's answer into a local, **skip only `auditFragment` and the
handler, and decrypt unconditionally**. Skipping the audit is what leaves the run short of the bytes
it declared, which is the whole point of the mode; skipping the decrypt is what killed the session.
There is no longer an early return between the two.

One correction that came with it: the mode's documentation claimed it reproduces *a fragment lost on
the wire*. Nothing is silently lost on this wire — AAP runs TLS over TCP, and a record that goes
missing takes the session with it rather than leaving a hole, which is what round 4 discovered the
hard way. What it reproduces is a fragment that **never reaches the reassembler**. Identical effect
downstream, different cause.

### The other half

The #852 reporter confirms the overlay fix works and has reported a new problem: with a full-screen
player paused and left idle 2-3 minutes, pressing Home and reopening OHU gives a **completely black
screen** with no "AA is starting" and nothing recovers it. A second reopen shows the overlay and
recovers after several seconds. Their capture has it **alternating, activity instance by instance** —
two instances silent for 11.8 s and 23 s, two that recover at 8940 ms and 8902 ms.

Two gates closed together, each disabling one of the two things that could ask for video back:

- The nudge loop was gated on the loading overlay being visible. `onCreate` hides that overlay when
  the *previous* instance's rendered-frame stamp is non-zero — a stamp the surface handoff zeroes
  moments later — and the runnable does not re-post when it declines. One silent tick ended it. That
  is the alternation.
- The escalation was gated on the age of the last **video** bytes against a 1500 ms window. Android
  Auto sends no video at all while nothing on screen animates, so on a paused player that gate shuts
  within seconds and stays shut for the whole idle period.

The nudge loop now keys on the picture rather than the overlay, and the escalation gates on link
activity against the same `LINK_QUIET_MS` the reconnecting overlay uses.

## 3. What is different about this round

- **The first thing R1 must show is that the session survives.** If `Decrypted payload too short` or
  an early `Connection closed (EOF)` appears after a fault, stop and report — everything else in R1
  is unmeasurable and re-running will not help, exactly as in round 4.
- **The renderer banner needs polling, not a one-shot check.** Round 4 lost R3's first attempt to it
  arriving 7.7 s after the handshake. Keep the poll-and-tap loop running for the whole of every run;
  it earned its place last round.
- **The banner line now names which of four situations armed it**, in parentheses. Only
  `the setup wizard changed the renderer` is governed by the persisted `pending-renderer-confirm`
  flag; the other three are session health checks and will fire regardless of it. If it appears,
  **report the reason string** — that is the answer to round 4's open question about why it re-armed.
- **R4 and R5 run on `view-mode=1` (TextureView).** Every rig round to date has been `SURFACE`, where
  the surface teardown releases video focus by accident and the wait is 42-96 ms rather than seconds.
  A `SURFACE` run would pass R4 without exercising anything.
- **R4 needs four reopens, not one.** The bug alternates, so a single reopen has a 50% chance of
  passing on the broken build. Four consecutive reopens in one session is the measurement.
- **Round 4's R4 (clean session) is dropped.** Its three zeroes are settled across rounds 2, 3 and 4.
  The reader edit touches every message on the connection, and what covers it is that R1, R2 and R3
  are each multi-minute live sessions through that same path — a decrypt-ordering mistake cannot
  survive any of them.
- **Grep the bare token `FAULT INJECTED`.** Reader-stage faults print under `AapRead:`, not
  `AapVideo:`. Same for `fault injection - ` and `fault injection budget spent after`.
- **Grep every capture with `-a`**, and remember `grep -c` prints nothing and exits 1 on a refused
  binary file, which reads exactly like a zero count.
- **Fault density floor unchanged:** below **30 `FAULT INJECTED`** an injection run is INCONCLUSIVE.
- **Discard rule:** as round 3 and 4 applied it — count SSL handshakes and `p2p-wlan0-N` indices
  reaching the measurement window, not `MATCH! Starting AapService` itself.

## 4. Settings

Types: `log-level`, `view-mode`, `debug-video-fault-injection`, `debug-video-fault-rate` and
`debug-video-fault-budget` are **int**; `video-codec` is **string**. "delete" means the removal half
of the template's §1 only.

| Key | R1 | R2 | R3 | R4 | R5 |
|---|---|---|---|---|---|
| `log-level` | `2` | `2` | `2` | `2` | `2` |
| `view-mode` | `0` (SURFACE) | `0` | `0` | **`1` (TEXTURE)** | **`1`** |
| `video-codec` | `H.264` | `H.264` | `H.264` | `H.264` | `H.264` |
| `debug-video-fault-injection` | `5` | `5` | `2` | delete | delete |
| `debug-video-fault-rate` | `3` | `3` | `3` | delete | delete |
| `debug-video-fault-budget` | `30` | delete | `30` | delete | delete |
| `force-software-decoding` | delete | delete | delete | delete | delete |
| `software-video-decoder` | delete | delete | delete | delete | delete |

R1-R3 are round 4's settings unchanged. R4 and R5 are new and differ from everything before them only
in `view-mode`.

## 5. The lines that decide the round

**New on this candidate:**

| Line | Level | Means |
|---|---|---|
| `the renderer confirmation banner is up (` (composed: the reason follows in the parentheses) | W | the banner, and **which of four situations armed it**. Report the reason string |
| `relaunched surface has no picture after ` (composed: `…Nms - cycling video focus`) | W | **R4's key line.** The warm-relaunch escalation fired. The `Nms` should be near 850, not near 9000 |
| `relaunched surface still has no picture - requesting video focus (unsolicited)` | W | the escalation's throttled nudge, after its one cycle is spent. **R5 wants zero** |

**Round 4's, and what each run counts:**

| Line | Level | Means |
|---|---|---|
| `Decrypted payload too short` | E | **R1's first FAIL condition.** The TLS desync round 4 hit |
| `Connection closed (EOF). Disconnecting.` | I | ditto, when it lands seconds after a fault |
| `AapRead: DELTA_CHANGED on VIDEO` (composed) | W | the framing audit sees the hole |
| `fragment run lost bytes, requesting keyframe to recover stream` | W | the detection reaching recovery. At or below the `DELTA_CHANGED` count — the ask is throttled to 1/s |
| `- holding the cycle until it settles` (composed) | W | **R1's key line.** The cycle earned and deliberately not spent |
| `AapVideo: fault injection budget spent after` / `AapRead: …` | W | **R1's and R3's stopwatch starts here** |
| `Throughput over ` (composed: `…rendered=N (Nfps), fed=N …`) | I | the stopwatch stops at the first non-zero `rendered=` |
| `cycling video focus` | W | a cycle spent, with `(N/3)` and the unrepaired time |
| `retaking video focus` | W | its second half |
| `VideoDecoder: keyframe decoded - the picture is repaired` | I | the repair, and the only line that means it |
| `treating this as a lost connection` | W | the reconnecting overlay. **R5 wants zero** — this is the #852 line |
| `Codec initialized:` | I | one per codec build |
| `AapProjectionActivity: onPause` / `onCreate` / `New surface set:` | I | **R4's instance boundaries** |
| `but restart suppressed` (composed) | W | the sync-stall watchdog out of budget. Expected under sustained loss, out of scope |

**Read-desync, for R6 — every one ends the session:**
`AapRead: fragment total read returned`, `AapRead: body read returned`, `AapRead: declared message size`,
`Disconnecting to resync`.

## 6. Runs

### R0 — build and unit-test gate

`build_hur.sh`, then `run_unit_tests.sh`.

- **PASS:** compiles, suite reports **513** tests — round 4's 505 plus 8 for the relaunch fix
  (`ProjectionWatchdogPolicy` and `WarmRelaunchKeyframePolicy`). The TLS fix adds none, deliberately:
  reaching it needs a fake `SSLEngine` and a fake connection, so R1 is its test.
- **FAIL:** stops the round. The unbuilt files are `AapReadSingleMessage.kt`,
  `AapReadMultipleMessages.kt`, `AapRead.kt`, `VideoFaultInjector.kt`, `AapProjectionActivity.kt`,
  `ProjectionWatchdogPolicy.kt` and `WarmRelaunchKeyframePolicy.kt`.

### R1 — the question rounds 3 and 4 could not answer

Mode **`5`**, rate `3`, budget `30`, H.264, `SURFACE`. **Four minutes minimum, at least two of them
after the budget-spent line.** Round 4's R1, on a mode 5 that no longer kills the session.

- **PASS condition 0, checked first:** **the session survives.** One SSL handshake, no
  `Decrypted payload too short`, no `Connection closed (EOF)` inside the run. **If this fails, stop
  and report it** — nothing below is measurable and a re-run will reproduce it.
- **Then the chain, each link a thing an earlier round could not produce:**
  1. `AapRead: DELTA_CHANGED on VIDEO` appears. Report the count.
  2. `fragment run lost bytes` appears, at or below that count.
  3. **At least one `holding the cycle until it settles` before the budget-spent line.** Round 3's
     zero was a genuine finding; round 4's zero measured nothing. Quote the first and last in full.
  4. **Budget-spent line → first non-zero `rendered=` is ≤ 10 s**, against R3's 61.6 s.
- Report the **budget-spent → `cycling video focus` gap** separately: it should be ~2-4 s and is the
  direct read on the two-second quiet window.
- **FAIL:** condition 0 fails; or links 1-2 missing; or still `rendered=0` 90 s after the budget-spent
  line.
- **Also record:** every `cycling video focus` line with its `(N/3)`, total hold lines, total
  `Codec initialized:`, longest run of consecutive `rendered=0` windows, and total `FAULT INJECTED`.

### R2 — the hold has a floor

Mode **`5`**, rate `3`, **no budget**, H.264, `SURFACE`. **Three minutes.**

- **PASS, both:** `cycling video focus` fires at least once — zero cycles across three minutes with
  the picture dead is the FAIL this run is for — and no hold line reports a first value above
  `60000ms`.
- **Expected, not scored:** the `but restart suppressed` ladder and a `Codec initialized:` count in
  the high single digits. That is the decoder's 15 s keyframe patience, untouched and out of scope.

### R3 — the control, unchanged

Mode **`2`**, rate `3`, budget `30`, H.264, `SURFACE`. **Four minutes**, same shape as R1.

- **PASS:** **zero** `DELTA_CHANGED on VIDEO`, **zero** `fragment run lost bytes`, **zero** hold
  lines, and a recovery gap near round 4's **61.6 s**.
- **R1 means nothing without this run.** If R1 and R3 produce the same four counts, the reader stage
  is not testing anything the assembler stage could not, and R1's result needs re-reading.

### R4 — a relaunch onto an idle screen recovers the first time

**`view-mode=1` (TextureView)**, no injection, H.264. This is the reporter's exact reproduction.

Per reopen: connect, leave the AA screen **completely untouched for 3 minutes** — confirm from the
capture that `Throughput` windows are reading `rendered=0` before continuing, that is the idle state
the bug needs — then `adb shell input keyevent HOME`, wait ~2 s, and relaunch OHU. **Do this four
times in one session.**

- **PASS, all three:**
  1. **Every one of the four reopens logs `relaunched surface has no picture after Nms - cycling
     video focus`**, with `N` near **850**, not near 9000.
  2. **Every one renders within ~2 s of its `New surface set:`.**
  3. **No reopen is silent.** For each of the four, list `onCreate`, `New surface set:`, the
     escalation line and the first `Throughput` with non-zero `rendered=`.
- **FAIL:** any reopen with nothing logged between its `New surface set:` and the next `onPause`.
  That is the current signature, and on the broken build it happens to **every other** reopen — which
  is why four are needed and one proves nothing.
- **Report the four instances as a table** even on a pass. The alternation is the finding, and its
  absence is the fix.

### R5 — the widened liveness gate costs an idle screen nothing

**`view-mode=1`**, no injection, no relaunch. **Ten minutes** on a static AA screen, left alone.

The escalation now treats a phone that is quiet on video but present on the link as alive, where it
used to treat it as gone. This is the only check on that.

- **PASS, all:** **zero** `cycling video focus`, **zero** `relaunched surface still has no picture`,
  **zero** `relaunched surface has no picture after`, **zero** `treating this as a lost connection`
  (the #852 line), **one** `Codec initialized:`.
- **`keyframe decoded - the picture is repaired` should still be non-zero** — single digits over ten
  minutes on the phone's own cadence.
- **FAIL:** any focus cycle on a session nobody touched. That would mean the gate was widened too far
  and an idle screen now gets disturbed.

### R6 — the read-desync fix is still silent (no new run)

Counted across R1-R5. **PASS: zero** of the four lines in §5.

Worth reading carefully this round: round 4's four session deaths produced zero of these, which is
what proved the failure was above the framing layer rather than in it. A non-zero count here now
would mean something genuinely new.

## 7. Do not re-run

- **The clean-session regression** (round 4's R4). Three zeroes across rounds 2, 3 and 4. Settled.
- **`parameter sets changed mid-session` = 2 with no interaction.** Measured identically three rounds
  running. Settled as a property of this rig's encoder.
- **The `-29 bytes/fragment` audit baseline** and the `access unit classified` answers. Note them if
  they change; they are not conditions.
- **#852's overlay behaviour**, the airplane-mode disconnect, the ~68-69 s keyframe cadence, focus
  mode 4, and `HIDE_START_CODE`. All settled in earlier rounds.
- **The `restart suppressed` ladder under sustained loss.** Diagnosed as the decoder's 15 s keyframe
  patience, out of scope, expected in R2.

## 8. Report back

1. **Does mode 5 sustain a session now?** (R1 condition 0.) One sentence, and the fault count it
   reached. Round 4's best was 13 before death.
2. **Did the hold finally engage?** (R1.) The count of `holding the cycle until it settles`, first and
   last quoted in full. Rounds 3 and 4 both reported zero, for different reasons.
3. **How long does the picture take to come back once the loss stops?** (R1.) One number against
   R3's 61.6 s, and the budget-spent → `cycling video focus` gap alongside it.
4. **Is the reader stage testing something the assembler stage cannot?** (R1 vs R3.) The four counts
   from both, side by side.
5. **Does the first reopen recover?** (R4.) The four-instance table. This is the one a reporter is
   waiting on.
6. **Does an idle screen still get left alone?** (R5.) Five zeroes, or the finding.
7. **Did the renderer banner appear, and with which reason string?** Round 4 could not tell which of
   four paths armed it; the line now says.
8. **Anything in a capture none of the above asked about.**
