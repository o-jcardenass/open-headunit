# Dropped-frame keyframe recovery — round 2 results

**Candidate:** `fix/830-request-keyframe-on-dropped-frame` @ `ec0a2d28`       **Baseline:** none (round 1's `563ae013` is the comparison point, not a rebuild)
**APK md5:** `e7b7b8ad4281d09ec3341bd842cf096a`
**Unit:** UNISOC MT50_YT610E4GFPSL_U, Android 14, Native AA wireless (this rig has no USB accessory path)
**Date:** 2026-08-14

## Setup notes

- **This round repeats round 1's brief verbatim as its baseline shape**, against a new candidate
  built same-day on top of `563ae013`: `ec0a2d28` adds `KeyframeCycleEscalationPolicy`, which
  escalates `AapTransport.triggerFocusCycleRecovery()` from the gain-only nudge to a real
  release/regain focus cycle once a drop episode has persisted >=150s continuously, capped at one
  cycle per session. Round 1 found the gain-only nudge inert; this round exists to check whether the
  new escalation actually fires on this hardware and, if it does, whether it survives the release
  half without reproducing issue #755's permanent-freeze regression.
- Inventoried `hur-wifi-test-scripts/` per house rule; used `build_hur.sh`, `run_unit_tests.sh`, and
  `set_hu_prefs.sh` for every settings write, same as round 1. No new script was needed.
- Same moving-screen substitution as round 1: phone media playback started via the head unit's media
  relay (`adb shell input keyevent 85`), routed through the phone's Bluetooth media session. Produced
  sustained real AA video traffic at 47-52 fps for the whole round.
- **R3 was deliberately extended from the brief's 5 minutes to ~10.6 minutes.** Mid-round analysis of
  R2's own drop-trigger timestamps showed the longest unbroken run of triggers (gaps <=3s apart, the
  new policy's own episode-continuity window) was only 13.7s — far short of the 150s escalation
  threshold `ec0a2d28` needs to ever fire `CYCLE_FOCUS`. A plain 5-minute R3 was very unlikely to
  exercise the new path at all, so R3 ran roughly twice as long specifically to give it a fair chance.
  It still did not fire — see R3 below. This is a deviation from the brief's literal duration, done in
  service of the brief's own spirit (testing what the candidate does), not a substitute for it: R3's
  first 5 minutes are directly comparable to round 1's R3 on every measure the original brief asked
  for, and are reported both as the full ~10.6-minute run and implicitly bounded by the fact that
  nothing about the fix's behavior changes across that extension (no escalation fired at any point).
- **Human-observed, not scriptable per TESTING-TEMPLATE.md §0: during R2, the tester watched the
  screen and reported visible smearing/washed-out corruption on the software-decoder path** — the
  same #830 symptom the whole investigation exists to fix, now directly confirmed by eye on this rig
  rather than only inferred from `dropped=` counts. Recorded here as real evidence per the template's
  own instruction, not a scripted measurement.
- Radio state: Native AA (`wifi-connection-mode=3`, unchanged), phone Bluetooth enabled via
  `svc bluetooth enable` (was off at round start, matching round 1's own note that BT state carries
  over between sessions rather than resetting).
- Settings backup/restore used a pushed restore script rather than an inline `run-as ... sh -c 'cat
  ... > file'` redirect — the inline form failed with `can't create shared_prefs/settings.xml: No
  such file or directory` despite the target directory existing and being writable by the app; a
  pushed one-line script (`cp /data/local/tmp/settings-backup.xml shared_prefs/settings.xml`) run via
  `run-as $PKG sh /data/local/tmp/restore.sh` worked on the first try. Matches this repo's own
  documented `run-as` quoting caveats; worth the next round reusing the pushed-script pattern for
  restores as well as writes.
- **R3's frame-size distribution did not show as clean a gap as round 1's.** Round 1 found a clear
  cliff before a 10000-115170 byte cluster; this capture's sizes climb more continuously from ~4000 to
  ~200000 bytes with no single dominant gap (finest gap found was 626 bytes, between 17168 and 17794).
  Kept the same >=10000 byte threshold as round 1 for direct comparability rather than re-deriving one
  from this capture's own shape, since the two captures are the same content type (phone media
  playback) and round 1's reasoning (many times the ~1600-1650 byte median P-frame) still applies
  cleanly at that threshold; noting the shape difference so a future round does not assume the cliff
  will always be there.

## R0 — build and unit-test gate

**PASS**

- `ec0a2d28` (four commits on `563ae013`: `KeyframeCycleEscalationPolicy` new file,
  `AapTransport.triggerFocusCycleRecovery()` rewrite, `VideoRecoveryPolicy` doc fix, new
  `KeyframeCycleEscalationPolicyTest`) compiled clean on the first try.
- `./gradlew testGithubDebugUnitTest` (via `run_unit_tests.sh`): **272/272** passed — round 1's 264
  plus the new `KeyframeCycleEscalationPolicyTest` **8/8**. `VideoRecoveryPolicyTest` unchanged at
  6/6 (its own scope, whether to ask at all, was not touched by this candidate).
- APK md5 `e7b7b8ad4281d09ec3341bd842cf096a`, copied out of `apks/` immediately and installed with
  `adb install -r`; live-APK md5 confirmed identical before any run.

## R1 — baseline drop census (answers Q1)

**Census, no PASS/FAIL** (per brief's own framing)

- Settings written: `log-level=2`, `view-mode=1` (TEXTURE), `video-codec="H.264"`,
  `force-software-decoding` and `software-video-decoder` deleted.
- Discard-rule check: 1 P2P group (`p2p-wlan0-5`), 1 SSL handshake, 0 `MATCH!`, 0 `Magic Garbage` —
  clean, no repeat of round 1's borderline `MATCH!` case.
- Decisive log lines: `Codec initialized: c2.unisoc.avc.decoder`.
- Measurements (5-minute timed window, 09:46:42-09:51:42, 60 `Throughput` lines):
  - **`dropped=0`, summed across the entire window and the entire capture** (79 `Throughput` lines
    total, including pre-window settle time) — matches round 1's `dropped=0` exactly.
  - `Input buffer full. Dropping frame.`: 0. `VideoDecoder: dropped a reference frame`: 0.
    `AapVideo: Frame corrupted`: 0.
  - Sustained `rendered` fps: 49-53 for the entire window, no dips.

**Reconfirms round 1's finding**: this rig does not shed frames under hardware decoding at all.
Nothing about this candidate changes that — expected, since `KeyframeCycleEscalationPolicy` only
executes once a request is already due, and R1 never generates one.

## R2 — provoke drops (gate for R3)

**PASS**

- Settings written: `force-software-decoding=true`, `software-video-decoder=0` (added on top of R1's
  settings, unchanged otherwise).
- Discard-rule check: 1 P2P group, 0 `MATCH!`, 0 `Magic Garbage` — clean.
- Decisive log lines: `Codec initialized: c2.android.avc.decoder` — software path confirmed taken.
- Measurements (5-minute window, 09:52:57-09:57:57): `dropped=433` total across the capture (65
  `Throughput` lines), `VideoDecoder: dropped a reference frame, requesting keyframe`: **46
  occurrences**, all attributable to the drop path (`AapVideo: Frame corrupted`: 0).
  `retaking video focus`/`cycling video focus`: **0** — no episode this run stayed unbroken long
  enough to reach `KeyframeCycleEscalationPolicy`'s 150s threshold (longest unbroken run of triggers:
  13.7s; `rendered` stayed non-zero throughout, 244-261 per 5s window (49-52fps)).
- **Directly confirmed by eye**: visible washed-out/smearing corruption on screen during this run —
  see Setup notes.

Both PASS conditions met (`dropped>0`, the fix's line fired). Consistent with round 1's R2 in every
respect except the raw counts, which differ only because this run's software-decoder overload
happened to cluster its drops slightly differently — not a behavioral change in the candidate.

## R2b — positive control

**PASS**

- Settings written: `force-software-decoding`/`software-video-decoder` deleted, reverting to R1's
  hardware-decoder config.
- Discard-rule check: 1 P2P group, 0 `MATCH!` — clean.
- Decisive log lines: `Codec initialized: c2.unisoc.avc.decoder` — hardware path restored.
- Measurements (2-minute window, 09:59:17-10:01:17, 30 `Throughput` lines): **`dropped=0`**;
  `VideoDecoder: dropped a reference frame`: 0.

Matches round 1's R2b exactly: drops return to the clean baseline the instant the lever is removed.

## R3 — does the escalation ever fire, and is the nudge still inert? (the point of the round)

**Gate:** R2 = PASS, so R3 ran as specified (with the duration extension noted in Setup notes).

- Settings written: same as R2, plus `log-level=0` (VERBOSE), read back and confirmed before launch.
- Discard-rule check: 1 P2P group, 0 `MATCH!`, 0 `Magic Garbage`. Two `SSL handshake complete` lines
  1ms apart (`AapSslContext.performHandshake` / `AapTransport.handshake`) — the same single-event
  double-log-statement pattern round 1 documented, not a second reconnect. Clean.
- `Codec initialized: c2.android.avc.decoder`. Capture ran 10:02:35-10:13:42 (~11.1 min wall time),
  playback active 10:03:02-10:13:41 (~10.65 min).
- Total `dropped=543` across the capture (131 `Throughput` windows).
- `VideoDecoder: dropped a reference frame, requesting keyframe`: **242 occurrences**, every one
  paired 1:1 by timestamp with an `AapTransport: Requesting recovery keyframe` line.
  `AapVideo: Frame corrupted`: **0** — the drop-triggered path remains the only requester exercised on
  real hardware, same as round 1.
- `Frame larger than the codec input buffer:` — 0 occurrences.

### (a) Does the new escalation fire?

**No. `cycling video focus` / `retaking video focus`: 0 occurrences across the whole 10.65-minute
run.** Episode-continuity analysis of all 242 trigger timestamps (gap <=3s = same episode, per
`KeyframeCycleEscalationPolicy.EPISODE_RESET_GAP_MS`):

| Metric | Value |
|---|---|
| Total triggers | 242 |
| Gaps > 3s (episode breaks) | 30 |
| Longest unbroken episode | **52.1 s** |
| Escalation threshold | 150.0 s |

The longest unbroken episode this rig produced under sustained software-decoder overload — the most
aggressive drop-provoking condition this round has — reached barely a third of the 150s escalation
threshold. **This is a real, honest finding about this rig's drop rhythm, not an inconclusive run**:
software-decoder-forced drops here come in bursts of a few seconds to ~50s, separated by quiet
stretches long enough to reset the episode clock, rather than one continuous multi-minute episode.
`KeyframeCycleEscalationPolicy`'s safety margin (2x the slowest natural keyframe gap round 1 ever
measured) means this candidate cannot be shown to fire its `CYCLE_FOCUS` path on this hardware without
either a synthetic drop condition worse than anything measured on real AA traffic here, or a
test-only lowered threshold — neither of which this round's brief authorizes. **The #755 safety
question (does a real release/regain survive on this hardware) remains open**, exactly as flagged in
the design writeup before this round started.

### (b) Is the gain-only nudge still inert? (reconfirms round 1's Q2)

**Natural keyframe cadence** (isolated gaps >5s between keyframe-sized (>=10000 byte) frames, same
threshold and method as round 1):

| Metric | Value |
|---|---|
| Isolated gaps found | 14 |
| Range | 9.0 s – 69.9 s |
| Median | 58.7 s |

Median assembled-frame size: **~1644 bytes** (round 1: ~1590 bytes — consistent).

**Nudge → next keyframe delta**, 242 nudges, 224 with a later keyframe in the capture:

| Metric | Value |
|---|---|
| Median Δ | **27.901 s** |
| Mean Δ | 30.510 s |
| p90 Δ | 61.624 s |
| Max Δ | 69.398 s |
| Δ < 0.1 s | 2 / 224 |
| Δ < 0.5 s | 5 / 224 |
| Δ < 1.0 s | 9 / 224 |
| Δ ≥ 5.0 s | 192 / 224 |

Of the 9 sub-1s deltas, 2 fall inside the connection-startup keyframe burst (10:02:45-46) and the
other 7 land shortly before one of the isolated natural-cadence keyframes tabulated above — the same
pattern round 1 found, for the same reason (nudges fire roughly once a second while drops persist, so
some nudge is always near some keyframe by sheer density).

**The nudge remains measurably inert on this build.** Median Δ (27.9s) and mean Δ (30.5s) both sit
inside the natural cadence's own range (9.0-69.9s, median 58.7s) rather than beating it — reproducing
round 1's finding on the new candidate to the point of near-identical numbers (round 1: median 30.4s,
mean 28.9s, natural range 7.5-70.1s median ~52.8s). `ec0a2d28` does not change the nudge's own
behavior at all (by design — `KeyframeCycleEscalationPolicy` only changes what happens once a request
is already due), so this is confirmation the two builds are measuring the same underlying mechanism,
not a new result.

### #755 fear check

**Clean, and over a longer window than round 1's.** `rendered` fps held 47-52 in 130 of 131
`Throughput` windows across the whole ~11-minute run, including windows with `dropped` as high as 16
in a single 5s window (`10:10:18.803 rendered=252 fps=50 dropped=16`). The one low reading,
**33 fps at 10:02:48.376** (6 seconds after launch), sits inside the connection-startup transient —
the same pattern as round 1's one 33fps dip — not mid-stream fallout from sustained nudging. Since
`CYCLE_FOCUS` never fired this round, this check only re-confirms the nudge path's safety (already
established) and says nothing new about the release/regain path's safety, which remains untested on
hardware.

## Anything the brief did not ask about

- **Ratio of `AapVideo: Frame corrupted` to `VideoDecoder: dropped a reference frame` nudges across
  the whole round: 0 : 288** (0:46 in R2, 0:242 in R3) — identical pattern to round 1 (0:187), now
  confirmed on a second, independent build. The corrupt-frame path continues to contribute nothing
  observable on this hardware under any condition tested across two rounds.
- **The 150s escalation threshold could not be exercised by this rig's own natural drop rhythm even
  under the most aggressive available lever** (forced software decoding, ~11 continuous minutes).
  If verifying `CYCLE_FOCUS`'s hardware safety is a priority before this branch ships, the options are:
  (1) accept it as untestable on this rig without a dedicated lower-threshold test build, since a
  test-only constant override would need to be built and is out of scope for a round working from the
  shipping candidate; or (2) find a condition that produces a longer unbroken drop episode than
  software-decoder overload does here (this round did not find one). Recording this explicitly rather
  than letting the R3 PASS read as if it covered the escalation path.
