# video-black-after-background — round 7 results

**Candidate:** `fix/warm-relaunch-keyframe` @ `eb4bc8e7` (`19d7cc79`, `1192daa5` beneath it) on `fork`
**Baseline:** `fix/822-stale-surface-callback` @ `1192daa5` (round 5's own candidate, the parent of this branch)
**APK md5:** candidate `706230ac578020229093aacf2007fd2f` / baseline `78fa53ca1ff2d45eb196da82e5831070`
**Unit:** UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14
**Date:** 2026-08-13

## Setup notes

- `git log --oneline -3` on the checked-out candidate: exactly `eb4bc8e7`, `19d7cc79`, `1192daa5` —
  confirmed before building.
- **R0 gate PASS.** `run_unit_tests.sh` green, full suite **260/260** (up from round 5/6's 252),
  `WarmRelaunchKeyframePolicyTest` **8/8** (new), `DecoderRestartPolicyTest` **4/4**,
  `ProjectionWatchdogPolicyTest` **4/4**, `DecoderStopPolicyTest` **6/6**, all unchanged from round
  5/6 as predicted. Counts pulled directly from each class's JUnit XML report, not just the
  `BUILD SUCCESSFUL` line.
- Both APKs built with `build_hur.sh`, copied out of `apks/` immediately after each build (per the
  brief's own §7a warning that the script deletes the previous APK), installed with `adb install -r`
  and confirmed live via `pm path` + on-device `md5sum` against the local file before every run that
  used that build — all four matched exactly.
- `hur-wifi-test-scripts/` inventory checked at the start: `run_r5_cover_return.sh` and
  `run_r5_rapid.sh` (round 5's own) and `legs.sh` (round 6's own) reused unchanged, per the brief.
  No new script needed this round.
- Settings backed up to `round7-video-black/settings-backup.xml` before the first write and restored
  byte-for-byte at the end (`diff` against the backup after restore: no differences). `view-mode` was
  already `2` (GLES) for R1, matching the brief's own value, so no write was needed there; `1`
  (TEXTURE) and `0` (SURFACE) were written for R2/R3 via `set_hu_prefs.sh`, and `2` was restored
  before R4/R5. `wifi-connection-mode=3`, `enable-audio-sink=true`, `log-level=2` were already correct
  throughout and never needed touching.
- **Session-establishment method, different from round 5's.** Round 5 reused an already-live session
  across R1-R4; this round force-stopped for every settings write (unavoidable) and every install, so
  each run's capture starts from a cold app launch. A P2P group (`p2p-wlan0-1`) was already up from
  prior testing, so each fresh launch reconnected in ~8-12 s rather than the documented 45-90 s cold
  path. **Every capture's first "cycle" in the tables below is this initial launch, not a scripted
  cover/return** — it is included for completeness but is not one of the brief's four measured cycles.
  It shows the same one-time channel-setup overhead pattern seen in round 5/6's own initial-launch
  segments (session=0, one `Fallback to negotiated dimensions:`, ~10-12 s total dominated by leg A
  before the surface is even claimed).
- **The discard-rule check fired on `MATCH! Starting AapService` and `createGroup SUCCESS` in every
  capture — always once each, always before the first scripted cover** (part of the initial launch's
  own self-wake, not contamination of any measured cycle). Confirmed by line number against each
  cycle's own start line every time. Zero forbidden lines (`times in a row without rendering a
  frame`, `Both codec types failed`, `Giving up to avoid an infinite restart loop`) in any of the five
  captures.
- `round6-video-black/legs.sh` was reused as-is and worked correctly for every clean run, reproducing
  itself as the reliable per-cycle tool it was built to be. It is not designed to separate overlapping
  relaunches when a cover fires before a still-recovering surface renders (R4's 180 s cycle, R5
  throughout) — in both cases this was caught by cross-checking the reduced file's raw event order
  before trusting the greedy pairing, per the brief's own instruction to show the pairing rather than
  reconcile silently, and reported by hand below rather than left to the script.
- Settings restored, app force-stopped, no residual device state changed beyond what the brief asked.
- **Process hygiene lapse, worth flagging precisely rather than glossing over.** None of the five
  `stdbuf -oL adb logcat` capture processes (r1 through r5) was killed before the next run's capture
  started — only `adb logcat -c` (clearing the device-side ring buffer) was run between rounds, which
  does not disconnect an already-attached reader. As a result every earlier capture file kept
  growing with every later round's traffic for the rest of the session: by the time all five were
  finally killed together at the end (confirmed via `ps aux | grep logcat` showing none left), `r1-
  gles.txt` had grown to 499 MB (not the ~124 MB it was when R1 itself finished), `r2-texture.txt` to
  362 MB, `r3-surface.txt` to 247 MB, and `r4-baseline-gles.txt` to 152 MB; only `r5-rapid.txt` (~39 MB,
  the last one started) is close to just its own window. **This does not affect any figure in this
  report** — every analysis below was either run immediately after its own round finished and before
  the next one started (R1, R2, R3's own `legs.sh` passes and discard-rule/forbidden-line checks, and
  the R4 whole-file `Media Start Request VIDEO` count, all confirmed against files that could not yet
  contain later rounds' data), or, where a check was re-run later for this write-up, explicitly
  filtered by that round's own PID (`25697` for R1, `1491` for R2, `9616` for R3, `17609` for R4,
  `25787` for R5) to exclude the later contamination — every escalation-timing figure quoted above was
  re-verified this way before being written down. A reader opening these files directly should expect
  each one to contain far more than its own round past a certain point, not treat the whole file as
  in-scope.

## The one number that decides it

| Backend | Baseline (round 5's own figures / this round's R4) | Candidate (this round) |
|---|---|---|
| GLES | 6.8-116.4 s (round 5) / **30.8 s, 74.8 s, superseded, 49.3 s** (R4, same day) | **3.2 s, 3.2 s, 3.2 s, 3.0 s** (R1) |
| TEXTURE | 5.4-52.5 s (round 5) | **3.1 s, 3.2 s, 3.0 s, 3.0 s** (R2) |
| SURFACE (control) | 0.68-0.80 s (round 5) | **3.2 s\*, 0.7 s, 0.8 s, 0.8 s** (R3, \*one cycle hit an unrelated pre-existing race, see R3) |

Every GLES/TEXTURE candidate cycle lands under 3.2 s — inside SURFACE's own historical range order of
magnitude, and nowhere near the 5 s PASS bar the brief set.

## R1 — GLES (`view-mode=2`), candidate, the point of the round

**PASS.** All four cycles under 5 s, all four fired and were followed by `Media Start Request VIDEO`.

- Settings written: none needed (`view-mode=2` already in place); `wifi-connection-mode=3`,
  `enable-audio-sink=true`, `log-level=2` unchanged.
- Discard-rule check: clean (both hits pre-cycle, see Setup notes). Zero forbidden lines.

| Hold | Total | Leg A | Leg B | Leg C | Fired? | `Media Start Request VIDEO` followed? | `Forcing restart (` |
|---|---|---|---|---|---|---|---|
| 5 s | 3.20 s | 467 ms | 41 ms | 2,690 ms | yes | yes | 0 |
| 45 s | 3.18 s | 492 ms | 74 ms | 2,615 ms | yes | yes | 0 |
| 180 s | 3.18 s | 494 ms | 174 ms | 2,509 ms | yes | yes | 0 |
| 5 s | 3.04 s | 476 ms | 48 ms | 2,514 ms | yes | yes | 0 |

- Every cycle's `Media Sink Stop Request: VIDEO` was immediately (0-2 ms) followed by `Video Sink
  Stopped -> Ignored (Forced Keyframe Request)` — confirmed ours, not an unrequested background stop,
  every time.
- The escalation fired **exactly 2000-2001 ms** after each `New surface set:` (the code's own "after
  2000ms" text), release→retake gap was **402-404 ms** every cycle (the documented 400 ms), and
  `Media Start Request VIDEO` landed 12-179 ms after the retake, itself 139-146 ms before `First frame
  rendered` — the same tight pairing on all four cycles, no variance worth reporting beyond what's in
  the table.
- `Fallback to negotiated dimensions: ` fired **once** total in the whole capture — on the initial
  launch (session=0), never on any of the four relaunches. Confirms `19d7cc79`'s effect directly.

## R2 — TEXTURE (`view-mode=1`), candidate

**PASS.** Same shape as R1, all four cycles under 5 s, all four fired and were followed by `Media
Start Request VIDEO`.

- Settings written: `view-mode=1`.
- Discard-rule check: clean. Zero forbidden lines.

| Hold | Total | Leg A | Leg B | Leg C | Fired? | Followed? | `Forcing restart (` |
|---|---|---|---|---|---|---|---|
| 5 s | 3.13 s | 448 ms | 46 ms | 2,638 ms | yes | yes | 0 |
| 45 s | 3.15 s | 437 ms | 54 ms | 2,660 ms | yes | yes | 0 |
| 180 s | 3.02 s | 426 ms | 321 ms | 2,273 ms | yes | yes | 0 |
| 5 s | 3.04 s | 460 ms | 109 ms | 2,472 ms | yes | yes | 0 |

- Identical mechanism, identical pairing: escalation at 2000-2001 ms after `New surface set:`,
  retake 402-403 ms after release, `Media Sink Stop Request: VIDEO` always immediately followed by
  `Video Sink Stopped -> Ignored (Forced Keyframe Request)`.
- `Fallback to negotiated dimensions: ` fired once total (initial launch only), same as R1.

## R3 — SURFACE (`view-mode=0`), candidate, regression guard

**PASS, with one flagged finding.** Three of four cycles matched round 5's SURFACE baseline exactly
(0.7-0.8 s); the fourth hit the new escalation once, traced to a pre-existing race unrelated to this
branch.

- Settings written: `view-mode=0`.
- Discard-rule check: clean. Zero forbidden lines. Zero unpaired `Media Sink Stop Request: VIDEO`.

| Hold | Total | `cycling video focus`? | Notes |
|---|---|---|---|
| 5 s | 3.22 s | **1 occurrence** | see below |
| 45 s | 0.74 s | 0 | matches round 5 (0.68-0.80 s) |
| 180 s | 0.80 s | 0 | matches round 5 |
| 5 s | 0.77 s | 0 | matches round 5 |

**The flagged cycle, traced in full.** During the backgrounded window of the first (5 s) cycle, before
the real return ever landed, the decoder's own `onSurfaceDestroyed` handling attempted a speculative
restart against a surface that was already gone: `Failed to start decoder` → `Decoder restart
requested: decoder_start_failed: Surface not valid` at 12:53:32.363-372, followed 378 ms later by the
phone's own `Media Sink Stop Request: VIDEO` (correctly handled as `Normal background/transition
behavior`, not ours) and then an **unsolicited** `Media Start Request VIDEO: session=1` from the phone
at 12:53:32.754 — roughly 32 seconds before the scripted return ever reached the device. When the real
relaunch happened at 12:54:04.9 (a fresh `AapProjectionActivity`, fresh `New surface set:`, fresh
codec configure, all clean), the phone's own session-1 bookkeeping was apparently still left in
whatever state that early unsolicited request put it in, and no picture arrived for 2000 ms — so the
new escalation fired, exactly as designed, and recovered 339 ms after retake (`Media
Sink Stop Request: VIDEO` immediately paired with `Video Sink Stopped -> Ignored (Forced Keyframe
Request)`, `Media Start Request VIDEO: session=2`, first frame 186 ms later).

Confirmed **not caused by this branch**: `git grep` shows `decoder_start_failed` and `Surface not
valid` already present verbatim in the baseline (`fix/822-stale-surface-callback` @ `1192daa5`), and
round 5's own SURFACE capture (same baseline, same rig) shows **zero** occurrences of either string —
so this is a real but intermittent pre-existing race in the baseline's surface-teardown handling, not
something `eb4bc8e7`/`19d7cc79` introduced. What this round adds: when that race does happen to leave
a relaunched surface picture-less, the new escalation catches it and recovers cleanly rather than
leaving SURFACE stuck the way GLES/TEXTURE used to. **Reported per the brief's own instruction ("one
appearing is a finding worth reporting even if the timings are fine") rather than treated as a FAIL**
— nothing here contradicts the regression guard's actual purpose, since the policy fired for a real
reason (no picture after 2 s) and not on a surface that was already fine.

## R4 — GLES (`view-mode=2`), **baseline** `1192daa5`, same-day A/B

Reference run, not scored PASS/FAIL — establishes what R1 is being compared against, same rig, same
day, same settings.

- Discard-rule check: clean. Zero forbidden lines.
- `Media Start Request VIDEO: session=` appears **once in the whole capture** — the initial launch
  only, exactly the round 5/6 finding (0/10 after any GLES/TEXTURE return), confirming the baseline
  build has not silently changed behavior since round 6.

One deviation worth reporting precisely rather than folded into the table: **the 180 s-hold cycle
never rendered its own picture before being superseded by the next scripted cover**, the same
"fresh surface supersedes a still-recovering codec" pattern round 4/5 already documented — its own
budget (90 s + 30 s soak) was exceeded by its own recovery attempt, which was still mid-restart when
the next cover fired. Reported by hand (see Setup notes on why `legs.sh`'s greedy pairing conflated
this with the next cycle) using the raw event timestamps:

| Hold | Outcome | Detail |
|---|---|---|
| 5 s | recovered | **30.8 s** return→picture, 2 `Forcing restart (`, 0 suppressed |
| 45 s | recovered | **74.8 s** return→picture, 4 `Forcing restart (`, 3 suppressed |
| 180 s | **never rendered, superseded** | its surface got 8 `Forcing restart (` / 7 `but restart suppressed (` / 8 keyframe requests over 133 s before the next cover's own `New surface set:` silently replaced it — no forbidden line, just discarded |
| 5 s (final) | recovered | **49.3 s** return→picture (measured from *this* cycle's own `Events injected: 1` at 13:22:07.841 to *its own* `New surface set:` at 13:22:08.398 and `First frame rendered` at 13:22:57.159, not from the superseded 180 s cycle's return), 4 `Forcing restart (`, 0 suppressed |

This is the clean same-day contrast the brief asked for: baseline needed 30.8-74.8 s (and one cycle
needed more than the entire test budget and never finished on its own), candidate needed 3.0-3.2 s for
the same four holds.

## R5 — GLES (`view-mode=2`), candidate, rapid switching (5× cover→3 s hold→return, back-to-back)

**PASS.** Zero forbidden lines across a 30.2 MB capture. Per the brief's own instruction, reported as
per-surface outcomes rather than per-cycle budgets, since consecutive covers arrive faster than a
relaunch can always complete — exactly round 5's own R4 finding, reproduced here on the candidate.

- Settings unchanged from R1 (`view-mode=2`).
- Of 5 scripted covers, only **2 surfaces ever survived long enough to claim a `New surface set:`** —
  script cycles 1, 2 and 4's relaunches were silently discarded before that point (no forbidden line,
  nothing to report, exactly like round 5's R4 cycles 2/3). Script cycles 3 and 5's surfaces stuck:

| Surface | `New surface set:` → `Configuring decoder:` | Escalation fired? | Configure → First frame |
|---|---|---|---|
| script cycle 3 (session=1) | 98 ms | yes, at exactly 2000 ms after `New surface set:` | 2,607 ms |
| script cycle 5 (session=2) | 110 ms | yes, at exactly 2002 ms after `New surface set:` | 2,516 ms |

- Both escalations show the identical pairing seen in R1/R2/R3: release→retake 403-404 ms later,
  `Media Sink Stop Request: VIDEO` immediately followed by `Video Sink Stopped -> Ignored (Forced
  Keyframe Request)`, `Media Start Request VIDEO` landing within a few ms after.
- The session stayed alive and rendering through the run's closing soak, same as round 5's R4.

## The two things the brief said were most likely to be wrong

1. **The 400 ms gap being too short.** Not observed anywhere: `Media Start Request VIDEO` followed
   every single escalation across all 14 real cycles (R1×4, R2×4, R3×4, R5×2) with no case of the
   release/gain going out and nothing following. The phone never coalesced the pair on this rig.
2. **The 2 s window firing on a surface that was about to render anyway.** Also not observed. In every
   escalation, leg C (configure→picture) is dominated almost entirely by the 2 s wait plus the
   ~550-700 ms release/retake/response chain — there is no gap of idle time before the escalation where
   a frame could plausibly have been about to land on its own; on the baseline (R4), the *same* surfaces
   waited 30-75+ seconds with no picture under otherwise identical conditions. R3's one flagged
   occurrence (the only case where the brief said *any* SURFACE firing would be worth scrutinizing) is
   consistent with genuine necessity too — see R3 above — not a premature trigger on a surface that
   would have rendered regardless.

## Report back

1. **Return→picture per cycle, R1/R2/R4** — see the tables above; summarized: candidate 3.0-3.2 s on
   both GLES and TEXTURE across all 8 real cycles, baseline 30.8-74.8 s (plus one cycle that never
   finished inside its own budget) across the same four GLES holds, same day, same rig.
2. **Did the cycle fire, and did `Media Start Request VIDEO` follow** — yes and yes, 14/14 real
   candidate cycles across R1, R2, R3 (including the flagged one) and R5.
3. **Did `Fallback to negotiated dimensions: ` stop appearing on relaunches** — yes, confirmed on both
   R1 and R2: it fired exactly once per capture, on the initial launch only, never on any of the four
   scripted relaunches. `19d7cc79`'s effect is exactly as predicted.

## Anything the brief did not ask about

- The R3 race (`decoder_start_failed: Surface not valid` immediately after a genuine
  `onSurfaceDestroyed`, followed by an unsolicited `Media Start Request VIDEO` from the phone while no
  surface exists) is real, intermittent, and pre-existing in the baseline — worth a closer look in its
  own right if SURFACE ever shows a slow return in the field, since it would otherwise look identical
  to "the fix isn't working" without this round's trace. Not chased further here since it is out of
  this branch's scope and this round's job was the A/B, not root-causing an unrelated race.
- Every session-start (session=0) segment across all five captures shows the same ~8-12 s
  channel-negotiation overhead before the first `New surface set:` — consistent across GLES, TEXTURE
  and SURFACE, and clearly a property of cold app launch/reconnect, not of any backend or this
  branch's changes. Not a finding, just confirming it doesn't vary by view-mode.
