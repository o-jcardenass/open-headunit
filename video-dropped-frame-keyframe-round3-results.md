# Dropped-frame keyframe recovery — round 3 results

**Candidate:** `test/830-lowered-escalation-threshold` @ `a2e0268e` (TEST ONLY, never to be merged or
shipped, four commits on `fix/830-request-keyframe-on-dropped-frame` @ `ec0a2d28`)
**Baseline:** none (this round exists purely to force and observe `CYCLE_FOCUS`, not to compare
against anything)
**APK md5:** `8f58bb39d0714319cfed6774a3396353`
**Unit:** UNISOC MT50_YT610E4GFPSL_U, Android 14, Native AA wireless (this rig has no USB accessory path)
**Date:** 2026-08-14

## Setup notes

- **This round exists solely because round 2 could not answer the #755 safety question.** Round 2
  found this rig's own drop episodes never sustain past ~52s even under sustained forced software
  decoding, so the shipping `ESCALATE_AFTER_EPISODE_MS = 150_000L` could never fire here. This round's
  candidate lowers that one constant to `5_000L` (commit `a2e0268e`, `KeyframeCycleEscalationPolicy.kt`,
  marked TEST ONLY inline and in the commit message) so the escalation is forced within the first
  sustained drop burst. Nothing else changed: `MAX_CYCLES_PER_SESSION` stays at 1, matching shipping
  behavior exactly, so this measures the real release/regain mechanism under real timing, not a
  synthetic stand-in for it.
- One unit test (`the escalation window stays clear of the slowest natural keyframe gap ever
  measured`) asserts the production 150s margin and is `@Ignore`d on this branch with an explicit
  reason pointing back at the constant change; all other tests (272 total, 8/8 in the new suite) pass
  unmodified.
- Same settings and moving-screen method as round 2's R2/R3: forced software decoding
  (`force-software-decoding=true`, `software-video-decoder=0`), `video-codec=H.264`, `view-mode=1`,
  `log-level=0`, phone media playback via the head unit's media relay (`input keyevent 85`).
- **The tester watched the screen live during this run** and reported visible video corruption
  persisting for roughly 15 seconds around the escalation. Recorded as direct human observation per
  TESTING-TEMPLATE.md §0 (a claim a scripted step "should have" worked is not evidence; what was
  actually seen is), and cross-checked against the log below, where it lines up with the measured
  recovery window.
- Settings backup/restore used the pushed-script pattern from round 2's own Setup notes
  (`run-as $PKG sh /data/local/tmp/restore_settings.sh`), not an inline redirect. Confirmed working
  again.

## R0 — build and unit-test gate

**PASS**

- `a2e0268e` compiled clean on the first try.
- `./gradlew testGithubDebugUnitTest` (via `run_unit_tests.sh`): **272/272 run, 271 passed, 1
  intentionally skipped** (the production-margin pinning test, `@Ignore`d with a reason on this
  branch only).
- APK md5 `8f58bb39d0714319cfed6774a3396353`, copied out of `apks/` immediately, installed with
  `adb install -r`, live-APK md5 confirmed identical before the run.

## R1 — force the escalation and observe

**PASS — the escalation fired, the phone rebuilt the stream, and the session recovered with no
permanent freeze.**

- Settings written: `log-level=0`, `view-mode=1`, `video-codec=H.264`, `force-software-decoding=true`,
  `software-video-decoder=0`.
- Discard-rule check: 1 P2P group, 0 `MATCH!`, 0 `Magic Garbage`. Two `SSL handshake complete` lines
  1ms apart — the same single-event double-log-statement pattern rounds 1 and 2 both documented, not a
  second reconnect. Clean.
- `Codec initialized: c2.android.avc.decoder` at 10:42:46.121 — software path confirmed.

**The cycle, in full, with every decisive line:**

| Time | Event |
|---|---|
| 10:43:06.644 | `AapTransport: corruption sustained 5035ms - cycling video focus (1/1)` — release sent |
| 10:43:06.697 | `Media Sink Stop Request: VIDEO` — phone acknowledges the loss, stops its video sink |
| 10:43:07.047 | `AapTransport: retaking video focus to complete the keyframe cycle` — regain sent, **403ms** after the release (`FOCUS_CYCLE_GAP_MS` is 400ms) |
| 10:43:07.059 | `RECV: VIDEO Media Start Request` |
| 10:43:07.061 | `Media Start Request VIDEO: session=1, config_index=0` — **a new session, incremented from `session=0`: the phone genuinely rebuilt the stream**, not just acknowledged a no-op |

This is the direct, positive answer to what round 2 could not test: on this hardware, the release
half of the cycle produces a real phone-side response (sink stop, then a fresh session on the regain),
not silence.

**fps and drop trajectory around the cycle** (5s `Throughput` windows):

| Window end | rendered fps | dropped |
|---|---|---|
| 10:43:01.150 | 50 | 0 |
| 10:43:06.154 | 45 | 56 |
| 10:43:11.169 | **41 (lowest point)** | 62 |
| 10:43:16.166 | 43 | 84 |
| 10:43:21.169 | 47 | 46 |
| 10:43:26.180 | 50 | 21 |
| 10:43:31.183 | 50 | 11 |
| 10:43:36.190 | **52 (fully recovered, dropped=0)** | 0 |

Full recovery to `dropped=0` at normal fps took **~29.5s** from the release (10:43:06.644 to
10:43:36.190). The tester's live observation of ~15s of visible corruption sits inside this window —
consistent with the picture clearing well before every last elevated-drop window resolves, since the
worst `dropped` counts (62, 84) land in the first ~10-15s and taper off from there. **This transition
cost is a real, measured effect of the cycle itself** (the phone tearing down and rebuilding its video
sink takes time, and that rebuild happens while the same software-decoder overload that triggered the
cycle in the first place is still ongoing) — not evidence of anything going wrong. It is also almost
certainly a worst-case number: this run keeps forcing software decoding for its entire duration, which
is far more sustained overload than the ordinary, transient reference-frame loss issue #830 describes.

**No #755 regression across the following four minutes.** fps held 45-52 in every one of 39
subsequent `Throughput` windows (10:43:36 through 10:46:56), including windows with `dropped` as high
as 58 (`10:44:26.237`, ordinary continued drop activity, not related to the cycle). No sustained
degradation, no low-fps floor, no freeze.

**The cap held.** `cycling video focus` / `retaking video focus`: **1 / 1** across the whole ~5-minute
run, despite 50 total `dropped a reference frame` triggers and continued drop activity for the rest of
the capture. Every trigger after the cycle correctly logged as `AapTransport: Requesting recovery
keyframe (unsolicited focus gain)` (the plain nudge) — confirmed by inspecting the lines immediately
following the one `cycling` line, and by the total nudge count (50) all being the gain-only form.

**No cross-channel disruption.** `Media Start Request AUDIO` fired exactly once, at session start
(10:43:03.817) — the video-only focus cycle never touched the audio channel.

- Total `dropped=533` across the whole ~5-minute capture (58 `Throughput` windows).
- `VideoDecoder: dropped a reference frame, requesting keyframe`: 50 occurrences, `AapVideo: Frame
  corrupted`: 0 (same pattern as rounds 1 and 2 — the drop-triggered path is the only one exercised).
- fps range across the whole capture: 35 (connection-startup transient, matching rounds 1/2's own
  startup dips) to 52.

## Net position

**The #755 safety question is answered, on this hardware, for a single cycle: the release/regain
escalation works as designed and does not reproduce the permanent-freeze regression.** The phone
answers the release with a real sink stop and rebuilds the stream on the regain, the picture recovers
fully within about 30 seconds under sustained worst-case overload, the session stays healthy for the
following four minutes, and the once-per-session cap holds exactly as coded. This is the first time
`CYCLE_FOCUS` has fired on real hardware since it was written.

**What this round does not answer:**

- Whether **repeated** cycles (a second, third drop episode each earning its own release) are safe —
  `MAX_CYCLES_PER_SESSION = 1` makes that structurally untestable without a further-modified test
  build, and was left untouched deliberately (see the design writeup: even a single worst-case failure
  must be the ceiling on the shipping build).
- Whether the ~30s transition cost measured here (under continuous forced software-decoder overload)
  generalizes to the much lighter, transient drop episodes issue #830 actually describes — this test
  build's provoking condition is a pessimistic upper bound by construction, not a realistic #830
  scenario.
- Whether a different phone or a different head-unit SoC reproduces #755 where this one didn't — this
  channel serves one rig; a single clean result here is evidence, not proof, that the mechanism is
  universally safe.

This test-only branch and its lowered constant must never be merged; the shipping `ec0a2d28` constant
(150s, 2x the slowest natural keyframe gap ever measured) is unaffected by anything in this round.
