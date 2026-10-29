# video-pipeline-stack — round 2 results

**Candidate:** `fork/fix/decoder-wedge-on-corrupt-access-unit` @ `8f0beab1b3e628c85e9ca64ca747f22859d0ccff`
(round 1's build A `f008e3d1` + fragment-audit fix `9c6460eb` + this round's four commits)
**Baseline:** none — one build, no baseline APK, per the brief.
**APK md5:** `439f2a3ee253ebaecd5d94a8e560eb55`
**Unit:** UNISOC MT50 head unit (`MT50_YT610E4GFPSL_U`, Android 14); phone bonded over Bluetooth,
Native AA wireless (`wifi-connection-mode=3`).
**Date:** 2026-08-18

## Setup notes

- `hur-wifi-test-scripts/` inventory: `build_hur.sh`, `run_unit_tests.sh` for R0;
  `install_and_launch.sh` (`SKIP_BUILD=1`) to install the pre-built APK; `set_hu_prefs.sh` for every
  settings change (one relaunch per run, all keys written together). No new script needed this round.
- Every log line and the `debug-keyframe-lever-no-input-focus` settings key the brief quoted was
  verified with `grep -rn` against `8f0beab1` before building. All matched exactly, including the
  two `AapProjectionActivity` lines the brief didn't quote but that turned out to matter for reading
  R2 and R5 (`relaunched surface has no picture after <N>ms - cycling video focus` and
  `... still has no picture - requesting video focus (unsolicited)`).
- `settings.xml` backed up to `round2-settings-backup.xml` before the first run, restored
  byte-for-byte (verified with `diff`) at the end of the round. The inline
  `run-as ... sh -c 'cp ...'` restore failed with `cp: Needs 1 argument` on the first attempt,
  exactly as the known quirk predicts — redone with the pushed-script pattern, which worked first
  try.
- **New quirk found this round:** at least one capture (`r6.txt`) got auto-detected as binary by
  `grep` — `file` reports "ASCII text, with very long lines" — which makes `grep -c` print nothing
  and exit 1 instead of printing `0`, silently looking like "pattern not found" rather than "grep
  refused to count." Every grep this round was redone with `-a` once this was noticed. Worth adding
  to `TESTING-TEMPLATE.md`'s quirks list: **always grep captures with `-a`.**
- **`FAULT INJECTED` counts were far lower than round 1's at identical settings**, and varied a lot
  run to run: R2 (rate=3) got 2 in ~3 min vs round 1's 10 in 99s at the same rate; R3 (rate=20) got
  0 in 5 min; R5 (R2's settings again) got 3 in ~3 min. The injector targets flag=8 (middle-fragment)
  messages, whose frequency depends on how often a frame fragments — a function of encoded frame
  size/motion on whatever the phone was projecting, not of the setting or the code. Not chased
  further, per the brief's own framing that this is content-dependent and not what's being measured
  — but it means R2's numbers are a real, valid result on their own rather than a literal
  round-1-scale re-run.
- Phone and head-unit radios were both already off at round start (equivalent to airplane-mode-on);
  brought up per §4 protocol before each run's timed window. Every capture showed exactly one
  `createGroup SUCCESS`, one distinct `p2p-wlan0-N` interface, one `SSL handshake complete`, and zero
  `MATCH! Starting AapService` — clean by the discard rules in all five runs.
- No `FATAL EXCEPTION` / `AndroidRuntime` in any of the five captures.

## R0 — build and unit-test gate

**PASS**

- `build_hur.sh` on `8f0beab1`: compiled clean, first try.
- `run_unit_tests.sh`: `BUILD SUCCESSFUL`, **454/454** tests passed — exactly the brief's predicted
  count (422 + 11 from the audit fix + 21 from this branch).

## R2 — R4 re-run, verbatim (the point of the round)

**PASS**

- Settings: `log-level=2`, `video-codec=H.264`, `view-mode=0`, `debug-video-fault-injection=2`,
  `debug-video-fault-rate=3`; `debug-keyframe-lever-no-input-focus`, `force-software-decoding`,
  `software-video-decoder` all absent. Verified read-back before launch.
- Discard-rule check: clean (1 `createGroup SUCCESS`, `p2p-wlan0-1`, 1 SSL handshake, 0 `MATCH!`).
- Capture: `04:00:25.868`–`04:03:28.917` (3m03s).
- **`FAULT INJECTED`: 2** (round 1: 10 in 99s — see Setup notes on content-dependent rate).
- **`Codec initialized:` 3** (1 initial + 1 restart per fault) — well below round 1's 7-in-99s rate.
- **`Decoder has had no keyframe since it started`: 0.** The new starvation branch was never
  reached — both faults recovered via the ordinary stall-restart + focus-cycle path before the 15s
  starvation bound could fire.
- **`cycling video focus`: 2, each followed by `keyframe reached the codec` well inside 2s:**
  - `04:01:35.863` → `04:01:36.407` — **0.544s**
  - `04:02:40.720` → `04:02:41.277` — **0.557s**
- **`rendered=` never hit 0 in any Throughput window.** Lowest values were 161 (32fps) and 152
  (30fps) in the two affected 5s windows; every other window held 295-308. **Longest run of
  consecutive `rendered=0`: zero** (round 1: 90+ seconds).
- Both faults exercised the cross-manager mutual exclusion correctly, and differently: fault #1 had
  `AapProjectionActivity.maybeRecoverWarmRelaunch` claim the lever first, and
  `AapTransport.escalateIfStillUnrepaired` then logged **"picture unrepaired for 2000ms - a focus
  cycle is already in flight, waiting for it"** (`04:01:36.003`) instead of double-firing — exactly
  the finding the brief flagged as worth reporting on its own. Fault #2 was the reverse:
  `AapTransport` led the cycle, `AapProjectionActivity` only logged its own non-claiming
  "requesting video focus (unsolicited)" line.
- One decoder restart per fault (1/4 used each time) — the restart budget was never close to
  exhausted.
- All four of the brief's PASS conditions met, and by a wide margin — zero `rendered=0` windows is a
  stronger result than the brief's own "no run longer than ~25s" bar asked for.

## R3 — the realistic rate

**INCONCLUSIVE**

- Settings: as R2 but `debug-video-fault-rate=20`. Capture: `04:06:43.409`–`04:11:42.588` (4m59s).
- Discard-rule check: clean (1 `createGroup SUCCESS`, `p2p-wlan0-2`, 1 SSL handshake, 0 `MATCH!`).
- **`FAULT INJECTED`: 0** in 5 minutes — below the brief's own 2-fault INCONCLUSIVE threshold.
  Single continuous session throughout, `Codec initialized:` exactly 1, `rendered=` never dropped
  below 239/300 across 59 Throughput windows.
- Exactly the outcome §3 predicted in advance: the stream did not fragment often enough at this rate
  on whatever content was on screen. No further runs attempted, per the brief's own instruction.

## R4 — the positive control that must stay clean

**PASS**

- Settings: `debug-video-fault-injection=4`, `debug-video-fault-rate=2`, `video-codec=H.264`.
  Capture: `04:12:24.927`–`04:17:21.204` (4m56s).
- Discard-rule check: clean (1 `createGroup SUCCESS`, `p2p-wlan0-3`, 1 SSL handshake, 0 `MATCH!`).
- **`FAULT INJECTED`: 10.** `AapVideo.handleAnomaly`'s "First fragment has no start code at offset
  10 or 2 ... Discarding the frame instead of assembling it headless": **10** — exact 1:1 with the
  fault count, matching round 1's 60/60 signature. The separate, throttled recovery-request line
  (`AapVideo.requestKeyframe`: "first fragment has no start code, requesting keyframe to recover
  stream") fired only **6** times, as designed — several faults landed inside the same 1s-throttle
  window (e.g. faults #4-#6 within 150ms of each other).
- **Reassembly anomaly totals: headless=10 (exact match to `FAULT INJECTED`), orphan=18,
  truncated=0, overflow=0** — the same pattern round 1 documented (a discarded headless frame turns
  its remaining fragments into orphans).
- **`Codec initialized:` exactly 1** — no restart across all 10 corrupted frames.
- **Zero starvation lines, zero `cycling video focus`, zero `retaking video focus`.** The new
  branch never fired on frames that were merely discarded — the one way this fix could have made
  things worse. Clean.
- The user directly observed heavy visible artifacting throughout this run and asked whether it was
  expected — confirmed live: this fault mode discards frames rather than corrupting the decode
  (unlike round 1's DROP_MIDDLE), so visible breakup/repair is the correct behaviour for this
  positive control, not a defect, and matches round 1's identical R3.

## R5 — the mode 4 probe

**Measurement — no FAIL applies, per the brief.** Closes as: **the probe is as inert as the nudge,
and in this run, worse.**

- Settings: R2's settings + `debug-keyframe-lever-no-input-focus=true`.
  Capture: `04:18:12.102`–`04:21:08.359` (2m56s).
- Discard-rule check: clean (1 `createGroup SUCCESS`, `p2p-wlan0-4`, 1 SSL handshake, 0 `MATCH!`).
- **`FAULT INJECTED`: 3** — #1 at `04:19:13.454`, #2/#3 at `04:20:13.522`/`.529`.
- **Three `keyframe cycle using PROJECTED_NO_INPUT_FOCUS (probe)` lines:**
  - `04:19:16.131` — from `AapProjectionActivity`'s warm-relaunch path, via
    `CommManager.releaseVideoFocusForKeyframe`
  - `04:19:17.489` — from `AapTransport.escalateIfStillUnrepaired`, cycle 1/3
  - `04:21:02.741` — from `AapTransport.escalateIfStillUnrepaired`, cycle 2/3
- **For all three: no `Media Sink Stop Request: VIDEO` followed** (zero in the whole capture),
  **no new `Media Start Request VIDEO: session=`** (stayed at `session=0` the entire capture), and
  **no `keyframe reached the codec` in any reasonable window.** The one keyframe that did arrive
  mid-capture (`04:20:13.576`, 62023 bytes) landed **~57s after** probes #1/#2 — matching the
  phone's own natural cadence, not anything the probe caused — and produced no rendered output
  afterward (`rendered=0` at the very next Throughput window).
- **Answer: the probe is inert**, same as the gain-only nudge measured across three prior rounds —
  no sink teardown, no new session, no attributable keyframe.
- **Because the debug key stays on for the whole session, every cycle this run used the inert probe
  instead of a real release** — so unlike R2, neither escalation ever had its one working lever
  available. The result was the brief's own explicitly-flagged worst case, at full severity:
  `rendered=0` sustained from `04:19:20.554` through the end of the capture — **108+ seconds,
  still unrecovered when the capture ended** — decoder restart budget fully exhausted (4/4) with the
  same 20/30/40/50s suppressed-stall ladder and eventual 59924ms stall round 1's original R4 FAIL
  showed, before one final forced restart at `04:21:00.714`.
- `Decoder has had no keyframe since it started` fired **4** times (10005/10003/10011/10006ms),
  once ahead of each of the 4 restart-budget slots — the starvation branch worked exactly as
  designed, it just had no real keyframe to wait for, because the only lever active this run does
  nothing.
- Matches the brief's own disclaimer — "the picture may recover worse than in R2, since the probe
  replaces the only lever known to work" — at its most severe: not just worse, but a full,
  unrecovered reproduction of the pre-fix wedge signature. Not a code defect: the probe only
  activates behind a debug-only key never reachable outside this brief.

## R6 — the clean session, and the two free measurements

**PASS**

- Settings: no injection keys, `video-codec=H.265`, `view-mode=0`, `log-level=2`.
  Capture: `04:23:54.021`–`04:33:53.023` (9m59s).
- Discard-rule check: clean (1 `createGroup SUCCESS`, `p2p-wlan0-5`, 1 SSL handshake, 0 `MATCH!`).
- **Zero** `Decoder has had no keyframe since it started`, **zero** `cycling video focus` /
  `retaking video focus`, **zero** `AapRead: DELTA_CHANGED on VIDEO` — round 1 saw exactly 10 false
  positives per session here; this build produced none across a full 10-minute H.265 session. The
  audit fix holds.
- **`Codec initialized:` exactly once.** `AapRead: fragment accounting established for VIDEO:`
  exactly once (`channel=2 fragments=2 declaredTotal=16716 observed=16774 delta=-58
  perFragment=-29`); `MUSIC_PLAYBACK` also fragmented and also logged exactly once, as expected.
- **All three `access unit classified` answers appeared, once each, all within the first 20ms of
  the session** (`PARAMETER_SETS_ONLY`, `PARAMETER_SETS_WITH_PICTURE`, `NO_PARAMETER_SETS`) — and
  the same three, once each, appear in R2's and R5's captures too, despite R2 having two later
  decoder restarts. **`PARAMETER_SETS_ONLY` appearing at all is the finding the brief called out:**
  this component now receives mid-stream buffers flagged as codec configuration, confirmed on this
  rig.
  - Why it never re-fires after a mid-session restart: `loggedContentKinds`
    (`VideoDecoder.kt:1444`) is a set on the `VideoDecoder` instance itself, and per this repo's own
    long-lived-singleton convention that instance survives `stop()`/`start()` restarts within one
    `AapService` lifetime. So "first this session" in the log text is really "first this process" —
    a mid-session restart cannot make it fire again. Not a bug, just a scoping note for whoever next
    asks this question.
- **`parameter sets changed mid-session`: 0** across the full 10 minutes, and also 0 in R2's and
  R5's captures (checked, as the brief asked, since both forced real decoder rebuilds). The encoder
  on this phone did not reconfigure at any point observed this round. A real answer, not a gap: the
  change-latch exists and has simply never had anything to catch on this rig.
- Codec-init-to-first-render timing consistent with round 1's clean runs — first Throughput window
  landed ~5s after `Codec initialized:`, ramping to steady 59-60fps by the second window.
- **Screen-motion note:** the brief asks for "moving for the first five minutes, static for the
  last five," but no scriptable lever exists in this brief or `hur-wifi-test-scripts/` for AA's
  projected screen content, and driving it would mean tapping the UI — reserved for genuinely
  non-scriptable cases. Ran the full 10 minutes under whatever the phone's own AA session displayed
  by default. Every PASS condition here is log-line/count based and unaffected by this; flagged
  because the brief's own "moving vs static" framing was not independently exercised.

## Anything the brief did not ask about

- R5's magnitude deserves surfacing even though the brief pre-cleared the run as a non-FAIL
  measurement: with the probe forced on, the exact `rendered=0` / restart-budget-exhaustion
  signature from round 1's original bug reappeared and was **still unrecovered when the capture
  ended**, 108+ seconds in. The probe is debug-only and nothing suggests it will ever ship
  user-reachable — but if it ever did, this is what it would look like.
- `grep -c` silently printing nothing (not even `0`) on a capture `file(1)` calls "ASCII text, with
  very long lines" cost real time to notice on `r6.txt` before the rest of the round's greps were
  redone with `-a`. Worth a line in `TESTING-TEMPLATE.md`'s quirks list for the next round.
