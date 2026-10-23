# render-side-concealment — round 1 results

**Candidate:** feat/render-side-concealment @ 03df5890       **Baseline:** fix/wire-corruption-escalation @ cd603ac0
**APK md5:** candidate 7fee59b7c48edee31c6babc72b1a6bfa / baseline f6b9967ff8fb312c864da81a936ee6b7
**Unit:** UNISOC MT50 (MT50_YT610E4GFPSL_U), Android 14, 1920x1080 projected screen
**Date:** 2026-08-20

## Setup notes

- Scripts used: `build_hur.sh` (both APKs), `run_unit_tests.sh` (candidate only, per brief), `set_hu_prefs.sh`
  (all settings writes). No new script needed.
- Copied each APK out of `apks/` into `round-render-side-concealment/` immediately after building,
  before building the next, per the `build_hur.sh` deletes-previous-APK trap.
- C0: both builds succeeded, md5s differ. Unit tests: 605/605 total, `CorruptionConcealmentPolicyTest`
  16/16, `AuditRecoveryPolicyTest` 10/10, `KeyframeCycleEscalationPolicyTest` 40/40 — exact match to the
  brief's expected counts.
- **First C1 attempt was discarded**: the logcat capture was started with a bare shell `&` inside one
  Bash tool call; the background process was reaped when that call's shell exited (~45s in), well
  before the run finished. Re-ran using the harness's own backgrounded-task mechanism for the capture
  process, which survives across tool calls; every run after that used the same pattern and captured
  cleanly end-to-end (verified: last capture line lands within seconds of the kill).
- **C3 needed a longer window than planned.** The idle-screen candidate trickle (flagged in the brief)
  stalled the DROP_MIDDLE_FRAGMENT candidate count at 63 for a full minute-plus inside the first 90s
  window, so zero faults had fired — a run like that proves nothing either way, not a FAIL. Extended
  observation on the same live session (no restart, no setting change) until the count cleared the
  rate=87 threshold; the fault fired at 260s in. C4/C5/C6 were given longer windows from the start
  (4-10 min) to avoid repeating this.
- Phone airplane mode is not usable on this rig (`TESTING-TEMPLATE.md` §7a); used `svc bluetooth
  disable/enable` + `svc wifi disable/enable` on the phone as the clean-run lever, verified with
  `dumpsys` before and after each toggle, per the same section.
- `settings.xml` diff against the pre-round backup: only `debug-video-fault-injection` (0 → various)
  and, from round 1 of the underlying thread, `debug-video-fault-rate`/`debug-video-fault-budget` were
  absent at round start and added/removed per run. All other keys, including `wifi-connection-mode=3`
  and `log-level=2`, were already correct from the previous round and needed no write.

## R/C0 — build and unit-test gate

**PASS**

- Candidate `03df5890`: build succeeded, md5 `7fee59b7c48edee31c6babc72b1a6bfa`.
- Baseline `cd603ac0`: build succeeded, md5 `f6b9967ff8fb312c864da81a936ee6b7`.
- `run_unit_tests.sh` on candidate: BUILD SUCCESSFUL, 605/605 tests, 0 failures, 0 errors.
  `CorruptionConcealmentPolicyTest`=16/16, `AuditRecoveryPolicyTest`=10/10,
  `KeyframeCycleEscalationPolicyTest`=40/40.

## C1 — the point of the round: one truncated frame, held then restored

**PASS**

- Settings written: `debug-video-fault-injection=3`, `debug-video-fault-rate=87`,
  `debug-video-fault-budget=1`, `log-level=2`.
- Radio state: phone BT+WiFi off during launch/settle, on for the run, verified via `dumpsys` each
  time.
- Discard-rule check: clean. `MATCH! Starting AapService`=1 (benign, phone's own reconnect),
  `createGroup SUCCESS`=1, single `p2p-wlan0-1` interface, zero Magic Garbage.
- Decisive log lines (all timestamps 2026-08-20):
  ```
  21:11:41.446 FAULT INJECTED (#1 of 87 candidates): DROP on flag 10, len=3894
  21:11:41.459 Previous frame was truncated! Resetting assembly state.
  21:11:41.479 holding the picture after frame truncated - the last good frame stays up until a keyframe
  21:11:43.465 picture unrepaired for 2000ms - cycling video focus (1/3)
  21:11:44.163 picture restored 2683ms after frame truncated (keyframe decoded)
  ```
- Measurements: restored-N = **2683ms** (< 3500ms cap; 1ms from round 1's 2682ms repair — same
  mechanism, same speed). Throughput window covering the fault: `concealed=63` (5010ms window ending
  21:11:42.550); the following window `concealed=49` (spillover from the still-open freeze);
  every subsequent window `concealed=0`. `rendered=` never 0 in any window (156, then 204, then
  steady 245-257 for the rest of the 90s+). Zero kill lines (`Decoder stall detected`, `Display
  stall`, `Rebuilding projection view` all 0). Zero `resuming on the damaged stream`, zero `quiet
  stream earned back`.

The `holding`/`restored` pair and non-zero `concealed=` carry this PASS, not the repair interval
alone (which would have repaired in ~2.7s even with no concealment change).

## C2 — the previously-wedging fault: holed run detected, discarded, concealed

**PASS**

- Settings written: `debug-video-fault-injection=5`, rate=87, budget=1 (rate/budget unchanged from C1).
- Radio state: same pattern as C1, verified.
- Discard-rule check: clean. `MATCH!`=1, `createGroup SUCCESS`=1, single `p2p-wlan0-2`.
- Decisive log lines:
  ```
  21:15:08.050 FAULT INJECTED (#1 of 87 candidates): DROP on flag 8, len=16153
  21:15:08.055 AapRead: DELTA_CHANGED on VIDEO - channel=2 fragments=2 declaredTotal=37038 observed=20972 delta=16066 expectedDelta=-58 perFragment=-29
  21:15:08.056 AapVideo: discarding a 20904-byte access unit the framing audit found short
  21:15:08.087 holding the picture after fragment run lost bytes - the last good frame stays up until a keyframe
  21:15:10.056 picture unrepaired for 2000ms - cycling video focus (1/3)
  21:15:10.735 picture restored 2651ms after fragment run lost bytes (keyframe decoded)
  ```
- Measurements: restored-N = **2651ms** (< 3500ms). `Configuring decoder` count = **1** for the
  entire session — the wedge-class fault this mode used to hand the codec never reached it. Zero
  kill lines.

## C3 — negative control: the undetectable hole must not freeze

**PASS**

- Settings written: `debug-video-fault-injection=2`, rate=87, budget=1.
- Radio state: same pattern, verified.
- Discard-rule check: clean on the extended capture (`MATCH!`=0, `createGroup SUCCESS`=0 in the
  continuation file — the group and reconnect both landed in the discarded first 90s segment of the
  same unbroken session; nothing new formed). Single `p2p-wlan0-3` for the whole session.
- Candidate trickle stalled at 63 candidates for the whole first 90s window (0 injected) — extended
  observation on the same live session rather than restarting; fault fired at candidate #87 of 260s
  in.
- Decisive log lines:
  ```
  21:20:17.309 FAULT INJECTED (#1 of 87 candidates): DROP on flag 8, len=16124
  21:20:19.324 Decoder stall detected (no output for 2005ms while receiving input). Forcing restart (1/4).
  21:20:19.518 Configuring decoder: c2.unisoc.hevc.decoder ...
  21:20:21.937 VideoDecoder: keyframe decoded - the picture is repaired
  ```
- Measurements: `holding the picture`=0, `discarding a`=0, `DELTA_CHANGED on VIDEO`=0 across the
  whole session — the concealment mechanism correctly never engaged, because this fault mode is
  invisible to the framing audit. The decoder stall/restart is the brief's own predicted "brief
  smearing or a decoder reaction after it," explicitly not a FAIL for this run: the holed unit was
  fed to the codec exactly as expected for the known, pre-existing blind spot at this fault stage.

Note for whoever reads this next: this run's own decoder-stall recovery briefly re-triggered
`Configuring decoder`, which would matter for a run scoring that count (it does not for C3) —
flagging in case a future round in this thread reuses this capture for something else.

## C4 — a second anomaly source: the orphan

**PASS**

- Settings written: `debug-video-fault-injection=1`, rate=87, budget=1.
- Radio state: same pattern, verified.
- Discard-rule check: clean. `MATCH!`=1, `createGroup SUCCESS`=1, single `p2p-wlan0-4`.
- Ran a 4-minute window from the start (informed by C3's trickle-stall) rather than 90s; fault fired
  well inside it.
- Decisive log lines:
  ```
  21:24:39.434 FAULT INJECTED (#1 of 87 candidates): DROP on flag 9, len=16120
  21:24:39.439 AapVideo: Orphaned fragment (Flag 10) detected! Frame data lost.
  21:24:39.462 holding the picture after orphaned fragment - the last good frame stays up until a keyframe
  21:24:42.122 picture restored 2659ms after orphaned fragment (keyframe decoded)
  ```
- Measurements: restored-N = **2659ms** (< 3500ms). Zero kill lines.

## C5 — the storm: one freeze, not a strobe

**PASS**

- Settings written: `debug-video-fault-injection=3`, `debug-video-fault-rate=3`,
  `debug-video-fault-budget=30`.
- Radio state: same pattern, verified.
- Discard-rule check: clean. `MATCH!`=1, `createGroup SUCCESS`=1, single `p2p-wlan0-5`.
- Decisive log lines (storm shape):
  ```
  21:29:48.482 FAULT INJECTED #1 of 3 candidates
  ...
  21:29:50.097 FAULT INJECTED #30 of 90 candidates   (all 30 landed inside 1.615s)
  21:29:51.178 picture restored 2666ms after frame truncated (keyframe decoded)
  ```
- Measurements: `holding the picture` count = **1**, `picture restored` count = **1** for the whole
  storm — the window opened by the first truncation absorbed the other 29. Restored-N = **2666ms**
  (< 3500ms). Throughput: the window spanning the storm shows `concealed=94`, `rendered=83 (16fps)`;
  the very next 5s window is back to `rendered=274 (54fps), concealed=0`. Zero kill lines, zero
  `resuming on the damaged stream`.
- Cap-expiry / anti-strobe-disarm coverage: **INCONCLUSIVE**, as the brief predicted — all 30 faults
  landed in 1.615s, well inside the 2s escalation timer and the 3.5s cap, so the repair beat the cap
  every time. Covered by `CorruptionConcealmentPolicyTest`'s cap/disarm/no-extension tests instead.

## C6 — clean control

**PASS**

- Settings written: `debug-video-fault-injection`, `debug-video-fault-rate`, `debug-video-fault-budget`
  all deleted (absent — reads as default/off). `log-level=2` unchanged.
- Radio state: same pattern, verified.
- 10-minute session, default post-connect screen, no injection.
- **First attempt discarded**: 5x `createGroup SUCCESS` / 5x `MATCH! Starting AapService` over the
  first 4.5 minutes before a session ever stabilized — the self-wake poke-loop churn this repo's own
  `.claude/CLAUDE.md` documents (`triggerPoke()`'s own `socket.connect()` raising `ACL_CONNECTED`,
  read by `AutoStartReceiver` as the phone arriving, re-`initWifiMode(force=true)`). Pre-existing,
  unrelated to this candidate — the poke-loop-cap fix lives in an unmerged branch this thread doesn't
  touch. Only one decode session ever started in that capture (after the churn settled), with zero
  concealment/kill lines in its own 6 clean minutes, but it fails the discard rule as written, so it
  was set aside rather than kept as the answer.
- Re-ran clean: `createGroup SUCCESS`=1 for the whole session, single `p2p-wlan0-11`. Two
  `MATCH! Starting AapService` lines with zero group churn attached (the phone's own Bluetooth
  reconnect during connect, matching `TESTING-TEMPLATE.md`'s clarified reading of the discard rule)
  — not contamination.
- Decisive measurements: **121 throughput windows over the full ~10-minute session, `concealed=0` in
  every one**, `holding the picture`=0, `discarding a`=0, `quiet stream earned back`=0, `unwrap
  produced no application data`=0. `Configuring decoder`=1 for the whole session. Throughput steady
  42-56fps throughout. Zero kill lines (`Decoder stall detected`, `Display stall`, `Rebuilding
  projection view` all 0).

## Anything the brief did not ask about

- C1's restored-N (2683ms) landed within 1ms of round 1's 2682ms baseline repair on the same fault
  shape — about as tight a confirmation as this rig can produce that the concealment change adds no
  measurable delay to the repair path itself.
- The candidate-trickle stall that hit C3 (round 1 of wire-corruption already flagged this as a real
  property of the idle post-connect screen, not new) is worth the next brief in this thread budgeting
  for directly rather than defaulting to a 90s window — every run from C4 onward used 4+ minutes and
  none needed a restart.
- C6's first attempt is the clearest hardware evidence yet for the poke-loop self-wake churn this
  repo's docs already flag from log analysis alone (#760): 5 full group re-inits with real
  `createGroup SUCCESS`/`MATCH!` pairs in under 5 minutes on an otherwise idle, untouched session,
  with `NativeAA: Attempting active poke` firing roughly every 30s throughout. Kept the discarded
  capture (`c6_DISCARDED_group_churn.txt`) rather than deleting it, since it's a clean, reproducible
  specimen of that behavior if anyone wants one.
