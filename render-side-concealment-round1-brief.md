# render-side-concealment — round 1 brief: a lost access unit should freeze the picture, not melt it

## 1. Build and baseline

**Candidate:** `feat/render-side-concealment` @ `03df5890` (8 commits over `main`) on the fork.
**Baseline:** `fix/wire-corruption-escalation` @ `cd603ac0` on the fork. This is the same branch the
wire-corruption round 1 validated, **extended by 4 commits since that round's `96eefddb`** (an
escalation-gate reorder, a cycle-budget refund, an input-buffer return on feed errors, and an SSL
zero-unwrap WARN). No history rewrite; `96eefddb` is still in its ancestry.

```bash
git fetch fork
git checkout -B render-side-concealment fork/feat/render-side-concealment
git log --oneline -8   # expect 03df5890 .. f0e4c804, then 96eefddb
```

| commit | what it does |
|---|---|
| `dad12f25` | Display-freeze constants get names on `ProjectionWatchdogPolicy`. Behaviour preserving. |
| `1e0e9cac` | `lastOutputMs` (stall watchdog) split from `lastFrameRenderedMs` (display watchdogs). |
| `5649fb85` | **The subject of this round.** Output thread holds the last good frame after a corruption report, until a repaired keyframe or a 3.5s cap. |
| `03df5890` | An access unit the framing audit found short of a whole fragment is discarded, not decoded. |

**Neither build has ever compiled.** CI runs only on pull requests and pushes to `main`, and both
branch tips are new. C0 is a real gate, for both APKs.

## 2. What this is and why it exists

Wire-corruption round 1 proved a truncated frame now repairs in ~2.68s instead of ~69s. But for
those 2.68s the melt is on the screen, and the #219 reporter counts a single visible artifact per
multi-hour drive as a failure. The lost access unit itself was never fed; the melt is every
P-frame after it predicting from a picture the decoder does not have.

The candidate makes the decoder release those buffers unrendered, so the surface keeps the last
good frame. The window closes on the frame `KeyframeRepairTracker` confirms as the repaired
picture, which is itself shown, or on a hard 3.5s cap. Round 1 measured escalated repairs at 2682,
2673 and 2687 ms on this unit, so a successful repair lands ~800ms inside the cap. A window that
expires disarms concealment until the next confirmed keyframe, so sustained loss buys one freeze
and then the honest smear.

The discard commit closes the last feed of damaged data: a missing *middle* fragment (invisible to
the reassembler, caught only by the framing audit's byte accounting) used to be assembled around
the hole and decoded. On this rig that fault mode has previously wedged the decoder into rebuilds.

## 3. What is different about this round

- **Rate 87 is pre-calibrated.** Round 1 measured 0.971 flag-10 candidates/s on the default
  post-connect screen; reuse `debug-video-fault-rate=87` directly unless the screen content
  changes, in which case recalibrate per that round's R1a recipe.
- **The idle-screen candidate trickle is real** (60-70s stalls in candidate flow at healthy fps).
  Single-fault runs are unaffected; do not extend a run past its cap to force extra faults.
- **C5 (storm) will very likely not produce a cap expiry.** Round 1's R4 showed a 30-fault storm
  completes in ~1.5s on this screen, faster than the 2s escalation timer, so the repair beats the
  3.5s cap and `CLOSE_EXPIRED` / the anti-strobe disarm are expected INCONCLUSIVE on this rig.
  Their coverage is the JVM tests (`CorruptionConcealmentPolicyTest`: the cap, the disarm, the
  no-extension rule, and a property test that every state renders within the cap). Say which you
  got; do not force it.
- **The freeze is invisible in a log except through its own lines.** There is no fps dip to look
  for: during a window the throughput line's `rendered=` keeps counting only shown frames, and the
  new `concealed=` field carries the held ones. Pair every window claim with that field.

## 4. Settings keys this round needs

Same keys as wire-corruption round 1, plus nothing new. All runs: `log-level=2`.

| key | values used |
|---|---|
| `debug-video-fault-injection` | 1, 2, 3, 5 per run; delete for C6 |
| `debug-video-fault-rate` | 87 (C1-C4), 3 (C5) |
| `debug-video-fault-budget` | 1 (C1-C4), 30 (C5) |
| `log-level` | 2 |

## 5. The lines that decide every run

Verified with `grep -F` against `03df5890`. The first is split across a string concatenation in
source; grep captures for it are the prefix only.

```
VideoDecoder: holding the picture after <reason> - the last good frame stays up until a keyframe
VideoDecoder: picture restored <N>ms after <reason> (keyframe decoded)
VideoDecoder: no keyframe within 3500ms of <reason> - resuming on the damaged stream
AapVideo: discarding a <N>-byte access unit the framing audit found short
AapRead: DELTA_CHANGED on VIDEO - ...
AapVideo: Previous frame was truncated! Resetting assembly state.
VideoDecoder: keyframe decoded - the picture is repaired
Throughput over ...: rendered=... concealed=<N>, inputWait=...
AapTransport: quiet stream earned back        (must be ABSENT in every injection run)
Decoder stall detected                        (kill line - must not appear in C1-C5)
Display stall                                 (kill line - must not appear in C1-C5)
Rebuilding projection view                    (kill line - must not appear in C1-C5)
```

`<reason>` is the corruption source: `frame truncated` (mode 3), `orphaned fragment` (mode 1),
`fragment run lost bytes` (mode 5). All the new lines print at WARN except the throughput and
`keyframe decoded` lines (INFO); `log-level=2` shows all of them.

## 6. Runs

### C0 — build and unit-test gate

Build **both** APKs (candidate and baseline), record md5s. `run_unit_tests.sh` on the candidate:
expect all green including `CorruptionConcealmentPolicyTest` (16 tests, new),
`AuditRecoveryPolicyTest` (10, extended), `KeyframeCycleEscalationPolicyTest` (40, extended).
**FAIL:** either build fails or any test is red. A candidate build failure stops the round; a
baseline failure stops only the runs that need it (none below do — it exists for a later A/B if
this round finds something).

### C1 — the point of the round: one truncated frame, held then restored

Candidate. `debug-video-fault-injection=3`, rate=87, budget=1. Same shape as wire-corruption R1.

**PASS, all of:**
- One `FAULT INJECTED` line, then `Previous frame was truncated!`.
- `holding the picture after frame truncated` within ~100ms of the truncation.
- `cycling video focus (1/3)` at ~2s, then `picture restored <N>ms after frame truncated
  (keyframe decoded)` with **N < 3500** and N within a few hundred ms of round 1's 2682ms.
- `concealed=` > 0 in the throughput window covering the fault, and `= 0` in every other window;
  `rendered=` never 0 in any full window (the freeze must not read like a dead stream).
- Zero kill lines, zero `resuming on the damaged stream`, zero `quiet stream earned back`.

**FAIL:** any kill line; `resuming on the damaged stream` (means the repair lost to the cap);
restored-N at or over 3500; a second `holding` line for one fault.

If the change did nothing, this run would still repair in ~2.7s - the pass is carried by the
`holding`/`restored` pair and `concealed=`, not by the repair interval.

### C2 — the previously-wedging fault: holed run detected, discarded, concealed

Candidate. `debug-video-fault-injection=5` (drop a middle fragment in the reader), rate=87,
budget=1.

**PASS, all of:**
- One `FAULT INJECTED`, then `AapRead: DELTA_CHANGED on VIDEO`.
- `AapVideo: discarding a <N>-byte access unit the framing audit found short`.
- `holding the picture after fragment run lost bytes`, then `picture restored <N>ms ... (keyframe
  decoded)` with N < 3500.
- `Configuring decoder` count stays at **1** for the whole session - this fault mode used to hand
  the codec a holed unit and has produced rebuild chains on this rig; the discard means the codec
  never sees it.
- Zero kill lines.

**FAIL:** no discard line (the verdict plumbing is broken); `Configuring decoder` > 1 attributable
to the fault; any kill line.

### C3 — negative control: the undetectable hole must not freeze

Candidate. `debug-video-fault-injection=2` (drop a middle fragment at the assembler, downstream of
the audit), rate=87, budget=1.

Nothing in the app can detect this mode: the audit already counted the bytes, and the reassembler
sees a first, middles and a last in order. **PASS:** one `FAULT INJECTED`, and then **zero**
`holding the picture`, zero `discarding`, zero `DELTA_CHANGED`. The holed unit is fed (the known,
pre-existing blind spot for a fault no real transport produces at this stage); brief smearing or a
decoder reaction after it is expected and is not a FAIL. **FAIL:** any `holding the picture` or
`discarding` line - it would mean the freeze is driven by something broader than detection, and
would fire on healthy streams.

### C4 — a second anomaly source: the orphan

Candidate. `debug-video-fault-injection=1` (drop a first fragment), rate=87, budget=1.

**PASS:** `Orphaned fragment` anomaly, `holding the picture after orphaned fragment`, `picture
restored` with N < 3500, zero kill lines. **FAIL:** the anomaly fires with no `holding` line (a
corruption source missed the report call), or any kill line.

### C5 — the storm: one freeze, not a strobe

Candidate. `debug-video-fault-injection=3`, rate=3, budget=30.

Round 1's R4 shape: expect all 30 faults inside ~2s. **PASS:** exactly **one** `holding the
picture` and exactly **one** `picture restored` for the whole storm (the window opened by the
first truncation absorbs the rest), restored-N < 3500, zero kill lines, `rendered=` recovers to
the pre-storm rate in the next full throughput window. **INCONCLUSIVE** (say so, do not force):
`resuming on the damaged stream` and the disarm behavior, if the storm never outlives the repair -
expected on this screen state, covered by JVM tests. **FAIL:** two or more `holding` lines
(strobing), or any kill line.

### C6 — clean control

Candidate. All `debug-video-fault-*` keys deleted, 10-minute session, default post-connect screen.

**PASS, all of:**
- `holding the picture` = 0, `discarding` = 0, `concealed=0` in **every** throughput window.
- `quiet stream earned back` = 0 (no cycle was ever spent, so nothing may come back).
- `unwrap produced no application data` <= 10 lines total (the burst budget; a handful at session
  start is legal TLS), and none in the back half of the session.
- Throughput steady at the unit's usual 45-50fps; `Configuring decoder` = 1; zero kill lines.

**FAIL:** any concealment line on a clean stream, or a `concealed=` > 0 window.

## 7. Do not re-run

Wire-corruption round 1's ladder: the escalation timing (R1), the baseline GOP comparison (R2),
the budget carry (R3), and the banner (R5) are settled. C1 re-measures the repair interval only
because the concealment sits on the same path and must not have slowed it.

## 8. Report back

1. C1's `picture restored <N>ms` and its distance from round 1's 2682ms repair.
2. C2's `Configuring decoder` count (the wedge-class fault must leave it at 1) and its restored-N.
3. C5's `holding` / `restored` counts (must be 1 / 1) and whether the cap expiry was reachable.
4. `quiet stream earned back` count across every injection run (must be 0 everywhere).
5. The `concealed=` values of every non-zero throughput window, with their timestamps.
