# Round 4 brief — black screen after backgrounding: verify the cascade fix

Round 3's new finding is fixed on the same branch. This round verifies the three new commits and
closes the two runs round 3 could not get clean signal on (R3-GLES, R6's background half). Read
round 3's results first; its root-cause section is the specification for everything below.

**Round 3's finding, restated in one paragraph:** the restart ladder judged "broken codec" by "no
frame since the last start" — but every surface swap zeroes that timestamp, so a session that had
rendered for an hour looked never-worked; this SoC takes up to ~7.8 s to produce a first frame
after a mid-session reconfigure against a 2 s stall window, so every relaunch burned restarts, the
ladder flipped the codec type to one guaranteed wrong for the stream, latched
`decoderPermanentlyFailed`, and `setSurface()` skipped the only `stop()` that clears the latch
because the codec was already gone. Permanent black, force-stop the only recovery.

The three commits, and what each must show on hardware:

1. `0fa51759` — a session that has already rendered **never** escalates a restart: no codec flip,
   no latch, however many warm-up restarts a relaunch costs.
2. `be61924c` — `setSurface()` stops unconditionally, so even a latched failure is cleared by the
   next surface. (With commit 1 the latch should be unreachable mid-session; this is the backstop.)
3. `75334e3c` — a codec rebuilt mid-session gets a **10 s** first-frame window instead of 2 s, so
   the watchdog stops manufacturing the stall it then detects. Cold start keeps the 2 s window.

## 1. Build

**Candidate:** `fix/822-stale-surface-callback` @ `75334e3c` on `fork` (round 3's seven commits
plus the three above).

```bash
git fetch fork --prune
git checkout -B fix/822-stale-surface-callback fork/fix/822-stale-surface-callback
git log --oneline -4
# expect 75334e3c, be61924c, 0fa51759, then fc04147e (round 3's build)
```

### R0 — gate

`run_unit_tests.sh` then `build_hur.sh`; md5 recorded and confirmed live.
**`DecoderRestartPolicyTest` must be present and green (4 tests)** — wrong commit if missing.
`ProjectionWatchdogPolicyTest` 4/4, `DecoderStopPolicyTest` 6/6, full suite green.

## 2. What is different about this round

- **One expected log change:** `Decoder stopped: New surface` now also appears when a surface is
  set with no codec running (session start, and every relaunch's first `setSurface`). That extra
  line is the unconditional-stop fix working, not a fault — do not count it as churn or
  contamination.
- **The failure this round hunts is intermittent.** Round 3 saw clean runs and cascade runs in the
  same session under the same settings. Verdicts below therefore come from repetition counts, not
  single passes. When a run's budget is exhausted without the condition appearing, that is a PASS
  for the runs defined here — the mechanisms are deterministic once a stall starts, and the grace
  commit exists precisely to stop the stall starting.
- `enable-audio-sink`: leave **`true`** for the whole round (round 3 corrected it; audio ruled out
  as causal, and `true` matches real users). Same log plumbing as rounds 2–3.
- Round 3's inert techniques stay retired: phone-screen-off for a video gap, 4–17 s WiFi-off for a
  brief stall.

## 3. The lines that decide every run

New or newly-relevant, verified with `grep -F` against `75334e3c` (composed lines marked):

| Meaning | Line |
|---|---|
| **must NOT appear anywhere this round** | `Falling back to ` |
| **must NOT appear anywhere this round** | `Both codec types failed` |
| **must NOT appear anywhere this round** | `Giving up to avoid an infinite restart loop` |
| a warm-up restart still fired (counted, not fatal) | `Forcing restart (` |
| the ladder declining to count a proven stream *(absence of escalation after these)* | `Decoder restart requested: ` |
| the unconditional stop *(composed)* | `Decoder stopped: New surface` |
| picture back | `First frame rendered (hardware decode)` |
| the fix from round 3, still working | `SurfaceCallback: onSurfaceDestroyed for a stale surface - ignoring.` |

## 4. Runs

```bash
PKG=com.andrerinas.headunitrevived
MAIN=$PKG/com.andrerinas.openheadunit.main.MainActivity
TRIGGER='adb shell am start -n '"$MAIN"
```

### R1 — the round 3 killer, repeated to exhaustion — **the point of the round**

`view-mode=2` (GLES — round 3's 4-for-4 failure mode), session up and rendering. Fire the trigger,
wait for the picture (up to 30 s), soak 30 s, repeat — **10 cycles**, one session if it survives.

**PASS:** all 10 cycles end with a stable picture; zero `Falling back to `, zero
`Both codec types failed` in the whole capture.
**FAIL:** any cycle ends black, or either forbidden line appears. Keep the capture and stop.

Count and report: `Forcing restart (` occurrences per cycle. With the 10 s grace, cycles whose
first frame arrives inside 10 s should show **zero** restarts — round 3's clean runs showed 1–3.
That count dropping is commit `75334e3c` visible in the log.

### R2 — the same, TEXTURE

`view-mode=1`, **5 cycles**, same pass conditions. Also confirm the round 3 headline still holds:
no `Media Sink Stop Request: VIDEO` after any trigger.

### R3 — round 3's unfinished business: background half of the regression guard

`view-mode=1`. Three Home-press cycles (3 s / 30 s / 90 s holds), returning via the launcher
trigger. Round 1's baseline: no teardown on Home, throughput uninterrupted during the hold; round
3 could not complete this because the cascade ate both attempts.

**PASS:** all three cycles return to a stable picture and the capture is free of the forbidden
lines. Report any deviation from round 1's during-hold behaviour.

### R4 — cold-start ladder unchanged (regression guard for commit `0fa51759`)

Fresh session from force-stop, three times. **PASS:** first frame within the usual startup time,
and — if any startup stall does occur — `Forcing restart (` fires at the same ~2 s pace as before
(the 10 s grace must NOT apply on a cold start; nothing has rendered yet).

If no startup stall occurs in three starts, record the verdict on timing alone: `Configuring
decoder:` → `First frame rendered` gaps, all three, in seconds.

### R5 — latch-recovery backstop — only if a permanent failure somehow still occurs

If any run produces `Both codec types failed` or `Giving up`, do not force-stop: fire the trigger
once more. **The picture must now return** (`be61924c` — the new surface clears the latch, then
decode restarts the codec). If it does, that is a PASS for the backstop and a FAIL for R1/R2 —
report both. If it does not, full capture, highest-priority finding of the round.

If no run latches (expected), R5 is **UNTESTABLE** — say so; the backstop then rests on the unit
test and code reading, which is acceptable.

### R6 — idle hygiene spot-check

One 2-minute idle window (`fake_speed=false`, untouched). **PASS:** zero `restart suppressed (`
lines, unchanged from round 3's R7.

## 5. Do not re-run

- Round 3's clean confirmations: R2 rapid-repeat, R5 watchdog/throttle, R7 idle hygiene (beyond
  the spot-check above). They stand.
- The link-stall `skipped` guard — INCONCLUSIVE on this rig until a sub-disconnect RF blip method
  exists; nothing in the three new commits touches the catch-up path (`git diff fc04147e..75334e3c`
  is the proof: `DecoderRestartPolicy.kt`, the ladder gate, `setSurface`, the stall threshold).
- Retired techniques per round 3.

## 6. Report back

1. **R1 in one sentence: does GLES survive 10 relaunch cycles with zero flips and zero latches?**
2. `Forcing restart (` counts per cycle, R1 and R2 — the number that shows the grace working.
3. R3: the background baseline, finally clean or not.
4. R4: the three cold-start timings, and whether any startup stall kept the 2 s pace.
5. R5's verdict (expected UNTESTABLE), R6's count (expected 0).
6. Anything in passing — rounds 1 and 3 both found their headline there.
