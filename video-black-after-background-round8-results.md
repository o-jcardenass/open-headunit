# video-black-after-background — round 8 results

**Candidate:** `fix/warm-relaunch-keyframe` @ `a304bf14` (`2ccfa641`, `eb4bc8e7`, `19d7cc79` beneath it) on `fork`
**Baseline:** none this round — round 7's own published per-cycle figures are the comparison, per the brief
**APK md5:** `93a65e855a9e9348bea5bed682662610`
**Unit:** UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14
**Date:** 2026-08-13

## Setup notes

- `git log --oneline -4` on the checked-out candidate: exactly `a304bf14`, `2ccfa641`, `eb4bc8e7`,
  `19d7cc79` — confirmed before building.
- **R0 gate PASS.** `run_unit_tests.sh` green, full suite **261/261** (up from round 7's 260),
  `WarmRelaunchKeyframePolicyTest` **9/9** (new test present, up from 8), `DecoderRestartPolicyTest`
  **4/4**, `ProjectionWatchdogPolicyTest` **4/4**, `DecoderStopPolicyTest` **6/6**, all unchanged from
  round 7. Counts pulled from each class's JUnit XML report, not just the `BUILD SUCCESSFUL` line.
- One APK built with `build_hur.sh`, copied out of `apks/` immediately, installed with
  `adb install -r`, confirmed live via `pm path` + on-device `md5sum` against the local file before
  R1 — matched exactly. No baseline build this round, per the brief.
- `hur-wifi-test-scripts/` inventory checked: `run_r5_cover_return.sh` (round 5) and `legs.sh`
  (round 6) reused unchanged. No new script needed.
- Settings backed up to `round8-video-black/settings-backup.xml` before the first write and restored
  byte-for-byte at the end (`diff` after restore: no differences). `view-mode` was already `2` (GLES)
  for R1, so no write was needed there; `1` (TEXTURE) and `0` (SURFACE) were written for R2/R3 via
  `set_hu_prefs.sh`. `wifi-connection-mode=3`, `enable-audio-sink=true`, `log-level=2` were already
  correct throughout.
- **Process hygiene fixed from round 7's own recorded lapse (§8 of this round's brief):** each run's
  `stdbuf -oL adb logcat` process was killed by its tracked pid immediately after that run's script
  finished, before the next run's settings write or capture started. Confirmed via `ps aux | grep
  logcat` after each kill (empty every time) and again at the very end of the round. Capture file
  sizes this round reflect only their own round's window: `r1-gles.txt` 106 MB, `r2-texture.txt`
  134 MB, `r3-surface.txt` 92 MB — no cross-round contamination to filter around this time.
- Same session-establishment method as round 7: force-stop for every settings write, fresh app
  launch, existing P2P group (`p2p-wlan0-1`) reconnects in ~9-11 s rather than a cold 45-90 s. Every
  capture's first "cycle" below is this initial launch (session=0), not a scripted cover/return, kept
  for completeness but not one of the brief's measured cycles.
- Discard-rule check (`MATCH! Starting AapService`, `createGroup SUCCESS`) fired once each in every
  capture, always before the first scripted cover (part of the initial launch's own self-wake) —
  confirmed by line number against each cycle's own start line. Zero forbidden lines (`times in a row
  without rendering a frame`, `Both codec types failed`, `Giving up to avoid an infinite restart
  loop`) in any of the three captures.

## The one number that decides it

| Backend | Round 7 (previous candidate) | Round 8 (this candidate) | Target |
|---|---|---|---|
| GLES | 3.2 s, 3.2 s, 3.2 s, 3.0 s | **2.1 s, 2.1 s, 2.0 s, 2.0 s** | under 2.5 s |
| TEXTURE | 3.1 s, 3.2 s, 3.0 s, 3.0 s | **1.9 s, 2.0 s, 1.9 s, 1.9 s** | under 2.5 s |
| SURFACE (control) | 3.2 s\*, 0.7 s, 0.8 s, 0.8 s | **0.7 s, 0.7 s, 0.6 s, 0.8 s** | 0.68-0.80 s, zero escalations |

Every GLES/TEXTURE cycle this round is under 2.1 s, comfortably inside the 2.5 s target and roughly
1.0-1.3 s faster than round 7's own build on the same four holds. SURFACE improved too, and round 7's
one anomalous 3.2 s outlier (R3's flagged cycle) does not recur anywhere — see R3 below.

## R1 — GLES (`view-mode=2`), candidate

**PASS.** All four cycles under 2.5 s, all four fired the escalation and were followed by `Media
Start Request VIDEO`.

- Settings written: none needed (`view-mode=2` already in place).
- Discard-rule check: clean (both hits pre-cycle). Zero forbidden lines.

| Hold | Total | Leg A | Leg B | Leg C | `New surface set:`→escalation (code's own value) | Release→retake | `Forcing restart (` |
|---|---|---|---|---|---|---|---|
| 5 s | 2.08 s | 520 ms | 48 ms | 1,507 ms | 850 ms | 401 ms | 0 |
| 45 s | 2.06 s | 504 ms | 383 ms | 1,174 ms | 854 ms | 401 ms | 0 |
| 180 s | 2.00 s | 507 ms | 142 ms | 1,352 ms | 851 ms | 402 ms | 0 |
| 5 s | 2.03 s | 486 ms | 49 ms | 1,498 ms | 859 ms | 401 ms | 0 |

- The escalation fired **850-859 ms** after each `New surface set:` (the code's own quoted "after
  Xms" value, not a recomputed timestamp diff, since the two differ by 1-2 ms of log-print latency) —
  down from round 7's 2000-2001 ms, exactly the effect `2ccfa641` predicted. Release→retake stayed at
  **401-402 ms**, unchanged from round 7 as expected.
- Every `Media Sink Stop Request: VIDEO` was immediately (0-2 ms) followed by `Video Sink Stopped ->
  Ignored (Forced Keyframe Request)` — confirmed ours every time.
- Retake→`First frame rendered` gaps: 239-301 ms across the four cycles — comfortably positive in
  every case, confirming the escalation did not fire on a surface that was already about to render
  (§5 item 1 of the brief: not observed).
- `Fallback to negotiated dimensions: ` fired once total (initial launch only), never on any
  relaunch — same as rounds 6/7.
- No race lines anywhere in this capture: 0 `Failed to start decoder`, 0 `Decoder restart requested:
  decoder_start_failed`, 0 `Decoder start aborted:` (the race is specific to a genuine
  `onSurfaceDestroyed`, which GLES never fires, consistent with round 5/6/7's own finding that GLES
  never tears its surface down).

## R2 — TEXTURE (`view-mode=1`), candidate

**PASS.** Same shape as R1.

- Settings written: `view-mode=1`.
- Discard-rule check: clean. Zero forbidden lines.

| Hold | Total | Leg A | Leg B | Leg C | `New surface set:`→escalation | Release→retake | `Forcing restart (` |
|---|---|---|---|---|---|---|---|
| 5 s | 1.87 s | 413 ms | 50 ms | 1,403 ms | 851 ms | 402 ms | 0 |
| 45 s | 1.99 s | 451 ms | 38 ms | 1,502 ms | 851 ms | 402 ms | 0 |
| 180 s | 1.88 s | 430 ms | 495 ms | 956 ms | 851 ms | 401 ms | 0 |
| 5 s | 1.87 s | 420 ms | 165 ms | 1,284 ms | 851 ms | 403 ms | 0 |

- The escalation gap is remarkably tight here: **851 ms on all four cycles**, no variance at all.
  Release→retake: 401-403 ms.
- Same pairing confirmed on every cycle: `Media Sink Stop Request: VIDEO` immediately followed by
  `Video Sink Stopped -> Ignored (Forced Keyframe Request)`, `Media Start Request VIDEO` following
  within a few ms.
- Retake→`First frame rendered`: 189-282 ms — same shape as R1, no premature firing.
- `Fallback to negotiated dimensions: ` fired once total (initial launch only).
- No race lines: 0/0/0 for `Failed to start decoder` / `decoder_start_failed` restart /
  `Decoder start aborted:`.

## R3 — SURFACE (`view-mode=0`), candidate, regression guard

**PASS, and it also closes round 7's one open question.** All four cycles at 0.6-0.8 s, **zero**
`cycling video focus` occurrences anywhere in the capture — the escalation never fired on SURFACE,
exactly as the brief required.

- Settings written: `view-mode=0`.
- Discard-rule check: clean. Zero forbidden lines. Zero unpaired `Media Sink Stop Request: VIDEO`.

| Hold | Total | Leg A | Leg B | Leg C | `cycling video focus`? | Race line seen? |
|---|---|---|---|---|---|---|
| 5 s | 0.71 s | 422 ms | 240 ms | 49 ms | 0 | none |
| 45 s | 0.71 s | 410 ms | 239 ms | 65 ms | 0 | `Failed to start decoder` → `Decoder start aborted:`, 4 ms apart |
| 180 s | 0.62 s | 397 ms | 174 ms | 48 ms | 0 | `Failed to start decoder` → `Decoder start aborted:`, 3 ms apart |
| 5 s | 0.76 s | 406 ms | 274 ms | 80 ms | 0 | none |

**The race round 7's own R3 traced (`decoder_start_failed: Surface not valid`) recurred twice this
round — more often than round 7's one occurrence — and `a304bf14` caught both cleanly.** Each time,
during the backgrounded window immediately after a genuine `onSurfaceDestroyed`, the decoder's
speculative restart attempt failed exactly as before (`Failed to start decoder`), but now the very
next line is `Decoder start aborted: the surface went away mid-configure. Waiting for a new one.`
(3-4 ms later) instead of the old code's `Decoder restart requested: decoder_start_failed:` — the
line that must not appear. **`Decoder restart requested: decoder_start_failed` count across all three
captures this round: 0.** With the abort in place, the phone's own `Media Sink Stop Request: VIDEO`
was handled as ordinary `Normal background/transition behavior` and — critically — **no unsolicited
extra `Media Start Request VIDEO` went out this time** (round 7's race produced one, which is what
left the phone's session bookkeeping confused and cost that round's one 3.2 s outlier). On both
affected cycles here, the return proceeded exactly like the two unaffected cycles: `onSurfaceCreated`
→ `Media Start Request VIDEO` (correctly numbered, no gap) → `First frame rendered` in 225-308 ms,
no escalation needed because there was nothing left to be confused about. **This round's two
race-affected cycles (0.71 s, 0.62 s) are in fact among the fastest of the four** — the fix didn't
just avoid a regression, it removed round 7's only slow SURFACE cycle at the source.

## Report back

1. **Return→picture per cycle, R1/R2, against round 7's 3.0-3.2 s** — R1: 2.08/2.06/2.00/2.03 s; R2:
   1.87/1.99/1.88/1.87 s. Both backends improved by roughly 1.0-1.3 s per cycle and landed
   comfortably under the 2.5 s target on every one of the eight real cycles.
2. **The `New surface set:` → escalation gap, confirming the constant took effect** — R1: 850-860 ms;
   R2: 851 ms flat on all four cycles. Down from round 7's 2000-2001 ms, matching the 850 ms constant
   `2ccfa641` sets.
3. **`Decoder restart requested: decoder_start_failed` count across all three runs** — **0**, as
   required. `Failed to start decoder` occurred twice (both in R3), and both times `Decoder start
   aborted: the surface went away mid-configure. Waiting for a new one.` followed within 3-4 ms,
   confirming the fix caught the race rather than looping into a restart.
4. **Any escalation in R3, or confirmation there were none** — **none**. Zero `cycling video focus`
   occurrences anywhere in `r3-surface.txt`.

## Anything the brief did not ask about

- Round 7's R3 anomaly (the one 3.2 s SURFACE cycle, traced to an unsolicited `Media Start Request
  VIDEO` arriving from a confused phone during the race) does not have a round-8 equivalent even
  though the underlying race fired *twice* this round instead of once — `a304bf14` removes the
  unsolicited request at its source by aborting before the phone is ever told anything, rather than
  papering over the confusion afterward. Worth noting for anyone reading round 7's results file cold:
  that finding is now historical, not a standing risk.
