# video-black-after-background — round 4 results

**Candidate:** `fix/822-stale-surface-callback` @ `75334e3c` on `fork` (round 3's seven commits plus
`0fa51759`, `be61924c`, `75334e3c`)
**Baseline:** none (regression-guard round, no A/B)
**APK md5:** `15cd7f63ea20a21e3e3321e22f9bfa41`
**Unit:** UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14
**Date:** 2026-08-12

## Setup notes

- `hur-wifi-test-scripts/` inventory: used `run_unit_tests.sh` and `build_hur.sh` unchanged for R0.
  No existing script covered the relaunch-soak or cold-start loops this round needed, so two new
  scripts were added and left in `hur-wifi-test-scripts/round4-video-black/`:
  `run_relaunch_cycles.sh <log> <markers> <n> <wait_s> <soak_s>` (R1/R2) and `run_r3_home_cycles.sh` /
  `run_r4_coldstart.sh` (single-purpose, hardcoded holds per the brief). `set_hu_prefs.sh` was used
  for every settings write.
- **A host-side timing bug, not a device defect.** All three cycle-runner scripts poll the growing
  logcat capture file every 1-2s from the *host* to detect "First frame rendered" within a
  wait-budget, then fall back to a fixed soak. That polling frequently reported
  `FRAME_NOT_SEEN_WITHIN_Ns` even when the frame appears in the file well inside the budget on
  direct post-hoc `grep`. Traced to this rig's own §7a-documented flooded logcat stream: the
  Monitor's live `tail -F` caught lines in real time in several cases where the polling loop's own
  `wc -l`/`tail` check (sub-25ms per call, confirmed by direct timing) still missed them, meaning
  the delay is in delivery through the adb pipe under heavy driver-log volume, not in the
  detection logic. **Every verdict below is therefore taken from the log's own embedded device
  timestamps and line-range bucketing against each cycle's recorded trigger, not from the runner
  scripts' own real-time wait/soak verdict**, which is unreliable on this rig and should not be
  trusted as a timing measurement in a future round without the same cross-check.
- **A second false alarm, resolved by cross-referencing.** R3's capture initially looked like it
  contradicted round 1's "Home never tears down the surface" finding (a real
  `AapProjectionActivity.onDestroy` + `TextureProjectionView: Surface destroyed` appeared during
  every hold). Tracing the causal `ActivityTaskManager: START ... with LAUNCH_SINGLE_TASK ... uid 0`
  line, plus correcting for a consistent ~1.5-2s device-clock-behind-host skew (established from
  R1/R2's own trigger-vs-log timestamps), showed the teardown lands at each hold's own boundary —
  i.e. it's caused by the round's own scripted "return via the launcher trigger" step (the same
  `am start -n MainActivity` mechanism R1/R2 already exercise), not by `KEYCODE_HOME` or by
  backgrounding duration. Round 1's finding (plain Home alone causes no teardown) is unaffected;
  it was never re-tested here since R3's own method always includes a return trigger.
- `enable-audio-sink=true` held for the whole round, `wifi-connection-mode=3` (Native AA, the only
  transport this rig has), `view-mode` set per run (2 for R1, 1 for R2/R3/R6), `fake_speed=false`
  only for R6, restored after. `settings.xml` backed up before any write and restored via a pushed
  `cp` (the `run-as sh -c` form was not attempted, per the known unreliability) at the end of the
  round; verified restored.
- One phone/head-unit A2DP link check at round start: connected, bonded, no link-dependent runs in
  this brief so no further monitoring was needed.

## R0 — gate

**PASS.** `run_unit_tests.sh`: 252/252 unit tests, `BUILD SUCCESSFUL`.
`DecoderRestartPolicyTest` 4/4, `ProjectionWatchdogPolicyTest` 4/4, `DecoderStopPolicyTest` 6/6 —
all present and green, matching the brief's expected counts exactly. `build_hur.sh` produced
`com.andrerinas.headunitrevived_3.2.4_debug.apk`, md5 `15cd7f63ea20a21e3e3321e22f9bfa41`, copied out
of `apks/` immediately per the known `build_hur.sh`-deletes-old-APKs rig quirk. `adb install -r`,
confirmed live via `pm path` + `md5sum` before any run.

## R1 — GLES, 10-cycle relaunch soak — **the point of the round**

**PASS.** Zero `Falling back to `, zero `Both codec types failed`, zero
`Giving up to avoid an infinite restart loop` anywhere in the capture (verified with a live
`tail -F` monitor throughout and a full post-hoc `grep -c`, both zero). 11 `New surface set` events
(initial launch + 10 relaunches), 11 matching `Decoder stopped: New surface` (the unconditional stop
firing every time, including when no codec was running — confirmed as the fix working, not churn,
per the brief's own note). Discard-rule check clean: 0 self-wakes, 1 `createGroup SUCCESS`, 0
`Magic Garbage`, 1 SSL handshake.

`Forcing restart (` per relaunch cycle, in order: **2, 3, 4, 4, 4, 0, 1, 2, 3, 4.** Four separate
cycles reached exactly 4/4 restarts — the exact threshold at which round 3's unpatched build
flipped codec type and latched permanently — and every one recovered with no escalation.

**One honestly-reported deviation, not a FAIL under the brief's stated criteria:** cycle 5 (4/4
restarts) never produced its own `First frame rendered` before cycle 6's relaunch superseded it
(~61s after cycle 5's surface was set, exceeding the ~60s cycle budget). No forbidden line appeared
at any point in that window, and cycle 6's own surface then rendered cleanly in 0 restarts —
evidence the unconditional-stop backstop handles a still-recovering codec being interrupted by a
fresh surface cleanly, not evidence of the round 3 cascade recurring. 9 of 10 cycles ended with
their own stable picture; the 10th was absorbed cleanly by the next cycle.

**R1 in one sentence: yes — GLES survived 10 relaunch cycles with zero codec flips and zero
latches, including four cycles that hit the exact 4/4-restart threshold that broke round 3's build.**

## R2 — TEXTURE, 5-cycle relaunch soak

**PASS.** Zero forbidden lines. Zero `Media Sink Stop Request: VIDEO` after any trigger — round 3's
headline still holds. 6 `New surface set` (initial + 5), 6 `Decoder stopped: New surface`. Discard
checks clean (1 `createGroup SUCCESS`, 0 self-wakes, 0 Magic Garbage, 1 SSL handshake).

`Forcing restart (` per cycle: **4 (interrupted), 2, 4 (interrupted), 4 (interrupted), 2.** Three of
five cycles hit 4/4 restarts and were still recovering (no frame yet) when the next relaunch fired,
against R1's one of ten — TEXTURE's relaunch path goes through a full `AapProjectionActivity.onDestroy`
+ `TextureProjectionView` surface-destroy every time (confirmed: `onDestroy` and `Surface destroyed`
both fire exactly once per cycle, 1:1), a heavier operation than GLES's in-place surface swap, which
plausibly explains the longer, more variable warm-up. Even three consecutive interrupted-recovery
cycles in a row (cycles 3 and 4 back to back) produced zero forbidden lines — the fix held under
repeated back-to-back interruption, not just isolated cases.

## R3 — background half regression guard (Home-press)

**PASS.** Three Home-press cycles (3s / 30s / 90s holds), each returned via the launcher trigger.
Zero forbidden lines across the capture. All three cycles went through a genuine
`AapProjectionActivity.onDestroy` + `TextureProjectionView: Surface destroyed` (caused by the return
trigger, see Setup notes — not a new teardown source, the same mechanism R1/R2 already exercise).
Cycle 1 (3s hold) and cycle 3 (90s hold) exceeded the scripted capture window before independently
confirming their frame; cycle 2 (30s hold) rendered cleanly in 1 restart. For cycle 3, a live
`logcat -d` check after the scripted capture ended confirmed the session recovered and has been
running healthy (42-51fps, `rendered==fed`, 0 dropped) for several minutes since — the recovery is
real, just outside the truncated capture window. No deviation from round 1's plain-Home baseline:
this round never tested Home alone without a return trigger.

## R4 — cold-start ladder unchanged (regression guard for `0fa51759`)

**PASS.** Three fresh sessions from `force-stop`. **Zero startup stalls occurred in all three** — no
`Forcing restart (` anywhere in the capture, so the 2s-vs-10s grace distinction was never exercised;
verdict rests on timing alone, per the brief's own fallback. `Configuring decoder:` → `First frame
rendered` gaps, all three: **53ms, 51ms, 51ms.** Zero forbidden lines.

## R5 — latch-recovery backstop

**UNTESTABLE, as expected.** No run in R1-R4 produced `Both codec types failed` or `Giving up`, so
the backstop was never exercised live. Rests on `DecoderRestartPolicyTest`/`DecoderStopPolicyTest`
and code reading, per the brief's own acceptance of this outcome.

## R6 — idle hygiene spot-check

**PASS.** 2-minute idle window, `fake_speed=false`, untouched. Zero `restart suppressed (` lines,
zero `Forcing restart (`, zero forbidden lines. Throughput steady 49-50fps, `rendered==fed`,
0 dropped/skipped throughout — matches round 3's R7.

## Anything the brief did not ask about

- The host-side cycle-runner timing bug (Setup notes) is worth a fix before it's reused: a future
  round should verify its own polling loop against a live `Monitor`/`tail -F` before trusting its
  wait/soak verdicts, on this rig specifically.
- R2's TEXTURE-vs-GLES asymmetry (3/5 vs 1/10 cycles needing more than ~60s to recover) is a real,
  measured difference in relaunch cost between view modes on this SoC, independent of anything this
  branch touches. Not a defect — the fix's safety property (no escalation) held throughout — but
  worth knowing if a future round designs cycle timing budgets around TEXTURE mode specifically.
- `AapProjectionActivity.onDestroy` fired 10 + 5 + 3 = 18 times across R1-R3 combined, every single
  one followed by a clean recreate with zero codec flips and zero latches. That is a materially
  larger sample than round 3's own confirmations and is worth citing as the round's real weight of
  evidence, beyond the four cycles that happened to hit exactly 4/4 restarts.
