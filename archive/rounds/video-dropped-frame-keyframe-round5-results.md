# Dropped-frame keyframe recovery: round 5 results

**Candidate:** `fix/830-request-keyframe-on-dropped-frame` @ `62889f29` (shipping candidate)
**Baseline:** none. Rounds 1-3 ran the identical provocation and produced zero focus cycles between
them; that is the comparison
**APK md5:** `4d54b75538877378fcd25d27a2a718d8`
**Unit:** UNISOC MT50_YT610E4GFPSL_U, Android 14, 3.8GB RAM
**Date:** 2026-08-14

## Headline

**The fix works as designed. T0 to repair measured 2.672-2.678 s for both fully-captured cycles,
against ~35 s median / up to ~69 s the same drop cost before this build.** Budget and cooldown both
held exactly as coded: 3 cycles fired this session (capped correctly, a would-be 4th never got past
budget check, 8 explicit refusals recorded), spacing between the two in-window cycles measured 65.3 s
(over the 60 s minimum). No #755 signal: fps held 42-51 throughout, and the codec component was never
re-initialized across any cycle.

## Setup notes

- Navigation still could not be started; substituted phone media playback (Amazon Music via `input
  keyevent 85` relayed through the head unit) for both R1 and R2, the same fallback used in rounds 1
  and 4. Two additional wrinkles this round: the media session reverted to `PAUSED` after the
  force-stop that `set_hu_prefs.sh` performs (expected, force-stop kills any client Bluetooth
  session state), so the play key had to be resent after each of the two relaunches (R1 start, R2
  start); the second attempt for R2 also needed a resend after the first `keyevent 85` was sent too
  early (before the AA audio channel had reopened) and was silently dropped. Confirmed `PLAYING` via
  `dumpsys media_session` before starting each timed capture.
- **A genuine capture-boundary gap: the session's first escalation cycle (1/3) fired before official
  R2 logcat capture began**, in the ~35-40 s gap between the provocation settings taking effect
  (relaunch) and the start of the `stdbuf`-piped capture (spent on `dumpsys` checks, resending the
  play key, and a 15 s discovery capture used only to confirm the codec had actually switched to
  software before starting the timed run). The only trace of cycle 1 in any capture is a refusal line
  at 15:36:50.612 already reading `no cycle available now (1/3 spent)`, meaning cycle 1's own T0 and
  firing predate every capture taken. This does not change the round's verdict: R2's official 10-minute
  window still contains **two complete, fully-instrumented chains** (cycles 2/3 and 3/3, session
  numbers 2 and 3), which is what the brief's PASS bar requires. Cycle 1 having already fired is
  corroborated three independent ways: the `(1/3 spent)` refusal itself, the fact that R2's captured
  cycles are numbered 2/3 and 3/3 not 1/3 and 2/3, and the `Media Start Request VIDEO` session numbers
  landing on 2 and 3 (implying a session=1 start already happened). Flagged here rather than silently
  smoothed over, and worth remembering for any future round: leave more margin between a
  provocation-settings relaunch and the start of the official capture, or start the capture before
  applying the provocation settings.
- Scripts used: `build_hur.sh`, `run_unit_tests.sh`, `set_hu_prefs.sh`, all as documented. No changes,
  no new scripts.

## R0: build and unit-test gate

**PASS.** `build_hur.sh` clean, `run_unit_tests.sh` clean. **286/286** unit tests (round 4's 284 minus
the old 8 `KeyframeCycleEscalationPolicyTest` cases plus 10 rewritten ones), exactly as predicted. APK
md5 `4d54b75538877378fcd25d27a2a718d8` confirmed identical between the built artifact and the live
install.

## R1: silence on a healthy stream (regression guard, gate for R2)

**PASS.** 5-minute undisturbed capture, hardware decoding, screen moving. `dropped=0` for the entire
window. **Zero** occurrences of `dropped a reference frame`, `Requesting recovery keyframe`, `picture
unrepaired`, or `cycling video focus` anywhere in the capture: the new trigger stays completely silent
when nothing is broken, as required.

4 natural keyframes, gaps 67.824-68.598 s (median ~68.3 s), reproducing round 4's fixed-GOP finding
(69.448 s median there) within the same tight band. Codec: `c2.unisoc.avc.decoder` throughout.

## R2: does the chain actually repair a wash? (the point of the round)

**PASS.** Provocation settings applied (`force-software-decoding=true`, `software-video-decoder=0`,
`video-codec=H.264`). 10-minute capture, `c2.android.avc.decoder` confirmed active. Reconstructing
every drop-to-repair episode from the ordered log (a "run" = the drops between two consecutive
`keyframe reached the codec` lines) gives **11 runs** in the official window: 1 boundary artifact (the
tail of the pre-capture cycle 1's wait, see Setup notes, excluded from the statistics below), **2
escalated (cycled)**, and **8 explicitly refused** (budget exhausted, correctly waiting for the
phone's own keyframe instead). **Zero silent self-clears** were observed inside the official window
(every genuine run reached the 2 s check), a finding in its own right, see "Anything the brief did not
ask about."

`dropped=367` total drop events across the window, but only 11 distinct unrepaired-clock runs: once a
run is unrepaired, further drops during the same episode log their own nudge but do not restart the
2 s clock, exactly as the design in the brief describes.

### The two complete chains

| Step | Cycle 2/3 | Cycle 3/3 |
|---|---|---|
| T0: `dropped a reference frame` | 15:37:43.650 | 15:38:48.955 |
| T0: `Requesting recovery keyframe` | 15:37:43.650 (same ms) | 15:38:48.956 (+1 ms) |
| `picture unrepaired ... cycling video focus (n/3)` | 15:37:45.654 (+2.004 s) | 15:38:50.957 (+2.002 s) |
| `retaking video focus` | 15:37:46.057 (+403 ms) | 15:38:51.362 (+405 ms) |
| `Media Sink Stop Request: VIDEO` | 15:37:46.198 (+141 ms) | 15:38:51.509 (+147 ms) |
| `Media Start Request VIDEO` | 15:37:46.205, `session=2` (+7 ms) | 15:38:51.511, `session=3` (+2 ms) |
| **`keyframe reached the codec`** | **15:37:46.328, 8200 B (+123 ms)** | **15:38:51.627, 8200 B (+116 ms)** |
| **T0 -> repair, total** | **2.678 s** | **2.672 s** |

Both repair keyframes are exactly 8,200 bytes, the same fresh-session startup-frame signature measured
in round 4's L3/L4 fires, confirming this is the identical, already-characterized mechanism.

### Budget and cooldown

3 cycles fired this session (1 unseen before capture, 2 captured), matching the coded cap exactly. All
8 refusals after cycle 3 correctly read `(3/3 spent)`; the one refusal between cycles 2 and 3 correctly
read `(2/3 spent)`. **No fourth cycle occurred anywhere.** Spacing between the two in-window cycles
(escalation-line to escalation-line): 15:38:50.957 - 15:37:45.654 = **65.303 s**, over the coded 60 s
minimum.

### The 8 refused runs, and what repaired them instead

Every refusal correctly deferred to the phone's own natural keyframe. Total time from T0 to repair for
each:

| # | T0 | Repair | Total |
|---|---|---|---|
| 1 | 15:37:47.444 | 15:38:48.954 | 61.510 s |
| 2 | 15:38:54.361 | 15:40:03.209 | 68.848 s |
| 3 | 15:40:19.559 | 15:41:11.248 | 51.689 s |
| 4 | 15:41:11.780 | 15:42:20.860 | 69.080 s |
| 5 | 15:42:22.445 | 15:43:29.851 | 67.406 s |
| 6 | 15:43:30.531 | 15:44:39.320 | 68.789 s |
| 7 | 15:44:51.180 | 15:45:49.374 | 58.194 s |
| 8 | 15:45:50.066 | 15:46:57.362 | 67.296 s |

n=8, median 67.35 s, min 51.69 s, max 69.08 s: consistent with R1's measured natural cadence (~68.3 s
median) and round 4's (~69.45 s), confirming the budget cap is correctly falling back to "wait it out"
once spent rather than doing nothing or erroring.

### #755 sentinel

fps ranged **42-51** across the whole 10-minute capture (lower than rounds 1/2/4's hardware-path
figures, expected under sustained forced-software-decoding overload, not a regression signature).
Immediately around both cycles fps stayed in the low-to-mid 40s and climbed back to 48-51 within
10-15 s, never dropping and staying down. **`Codec initialized:` count: 0** across the entire capture
and both cycles, matching round 4's finding that the underlying component survives repeated
sink-stop/start cycles without re-initializing.

## Anything the brief did not ask about

- **Zero silent self-clears in the official window** is itself informative, not just a data point:
  under this round's *sustained* heavy overload (367 drops in 10 minutes), no drop episode ever
  resolved inside the 2 s window without either an escalation or an explicit refusal. That is a
  property of the sustained-overload provocation specifically (drops keep arriving faster than the
  natural ~68 s cadence can plausibly land inside any given 2 s window), not necessarily of the
  single-lost-frame case #830 actually reports. It says nothing about whether the 2 s window is well
  tuned for a light, isolated real-world drop; it only says this stress test cannot produce that
  signal, and a future round chasing that question would need a lighter, intermittent provocation
  instead of the sustained one this round (correctly) reused from rounds 1-3.
- The one excluded "boundary artifact" run (T0 before capture start, repaired at 15:37:43.116 after
  28.344 s with no unrepaired line visible in-window) is not a silent self-clear either; it is the tail
  of the pre-capture cycle 1's post-refusal wait for the phone's own keyframe, continuing a clock that
  had already been running before logcat started. Included here for completeness, excluded from every
  statistic above.

## Net position for #830

**One number decided the PR, and it came back at 2.672-2.678 s** against the ~35 s median / up to
~69 s the same shed frame cost before this build, on both fully-instrumented cycles this round
captured. Budget (3, correctly capped, no fourth) and cooldown (65.3 s spacing, over the 60 s minimum)
both held exactly as coded. No #755 signal anywhere. **The branch is ready.**
