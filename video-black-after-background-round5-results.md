# video-black-after-background — round 5 results

**Candidate:** `fix/822-stale-surface-callback` @ `1192daa5`       **Baseline:** none (no A/B, per brief)
**APK md5:** `15cd7f63ea20a21e3e3321e22f9bfa41`
**Unit:** UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14, `head-unit-make: Royal Enfield`
**Date:** 2026-08-13

## Setup notes

- `hur-wifi-test-scripts/` inventory taken first: `run_unit_tests.sh` and `build_hur.sh` used
  unchanged for R0; `set_hu_prefs.sh` used for every settings write (no `set_hu_pref.sh`, since
  every run needed the three-key group written together). No existing script covered the
  cover/return cycle, so two new scripts were added and left in
  `hur-wifi-test-scripts/round5-video-black/`: `run_r5_cover_return.sh <log> <markers> <hold1>
  [hold2 ...]` (R1-R3) and `run_r5_rapid.sh <log> <markers> [n_cycles] [hold_s]` (R4).
- **Tree confirmed byte-identical to round 4 before the round started**: `git log --oneline -4`
  on `fix/822-stale-surface-callback` @ `1192daa5` returned exactly `1192daa5, d2cafa27, 5a87d90b,
  dc0ddc1c` as the brief specified.
- **R0 PASS**: full suite 252/252, `DecoderRestartPolicyTest` 4/4, `ProjectionWatchdogPolicyTest`
  4/4, `DecoderStopPolicyTest` 6/6 — identical counts to round 4, as expected for an unchanged
  tree. APK installed with `adb install -r`, live md5 matched the built md5.
- **This rig is rooted** (`adb shell id` → `uid=0(root)`), so settings were read/written directly
  at `/data/data/$PKG/shared_prefs/settings.xml` via `set_hu_prefs.sh`, not through `run-as`. The
  app was force-stopped before every write and the file read back to verify, per protocol.
- **New rig fact, precise and load-bearing for how "return → picture" is measured on this unit:**
  `adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1` takes **exactly ~26.1 s**
  (26.10-26.11 s, measured 17 times across R1-R4 with zero meaningful variance) between the
  process actually starting (`Calling main entry com.android.commands.monkey.Monkey`) and the
  event actually landing (`Events injected: 1`, `ActivityTaskManager: START u0 {... LAUNCHER ...}
  result code=START_TASK_TO_FRONT`). This is independent of view-mode, hold length, and how deep
  into the round it fires. The likely mechanism is `monkey`'s own wait-for-system-idle heuristic
  never resolving early on this rig, since `TESTING-TEMPLATE.md` §7a already documents the driver
  stack flooding logcat continuously — the system may never look "idle" to `monkey`'s own check,
  so it always rides out to whatever ceiling that heuristic caps at. **All "return → picture"
  numbers below are computed from the device-log `Events injected: 1` timestamp, not from the
  host-side command-issue timestamp** — the two differ by this same ~26.1 s on every cycle, and
  using the host timestamp would silently inflate every latency in this round by that amount.
  Worth folding into `TESTING-TEMPLATE.md` §7a: a future brief timing anything off a `monkey`
  invocation on this rig should budget for it or use the device-log line instead.
- Cross-checked against the round-4-documented ~1.5-2 s device-behind-host clock skew: host-side
  script timestamps consistently landed 1.6-2.1 s *after* the corresponding device-log line for
  the same event, which is that skew, not a script-ordering bug. Not re-litigated per run below.
- All four backends/runs used a single already-formed Native AA session per run (fresh relaunch
  between R1/R2/R3/R4, one `createGroup SUCCESS` and one interface index each: `p2p-wlan0-1`
  (R1), `-2` (R2), `-3` (R3), `-4` (R4) — sequential, no bump *during* any run). No
  `MATCH! Starting AapService`, no `Magic Garbage detected in header`, no second SSL handshake in
  any capture. All four discard-rule checks clean.
- `log-level` left at `2` (INFO), unchanged from round 4, per the brief.
- Settings backed up before the first write (`round5-video-black/settings-backup.xml`) and
  restored at the end of the round; final `wifi-connection-mode=3` / `view-mode=2` /
  `enable-audio-sink=false` confirmed matching the pre-round file.
- Captures are large on this rig's flooded logcat (93 MB-161 MB per run); kept locally in
  `hur-wifi-test-scripts/round5-video-black/`, not committed to this branch.

## The headline rig fact (brief's report item 2)

**Whether a fullscreen cover destroys the surface depends entirely on the backend, and this round
measured all three for the first time:**

- **GLES and TEXTURE: no.** Across R1, R2 and R4 (9 cover events total), `SurfaceCallback:
  onSurfaceDestroyed`, `Decoder stopped: surfaceDestroyed` and `Media Sink Stop Request: VIDEO`
  never fired once. This extends round 1's Home-press finding to a real fullscreen app cover for
  these two backends — on this unit, neither Home nor Settings tears anything down for GLES/
  TEXTURE. The disruption these two backends *do* show (restart/warm-up churn, detailed below)
  comes entirely from `MainActivity.onResume()`'s "Active session detected" relaunch cascade, not
  from any surface teardown.
- **SURFACE: yes, cleanly, every time.** All 4 R3 cycles show `SurfaceCallback:
  onSurfaceDestroyed` firing ~1 s after the cover's own `ActivityTaskManager: START` line, followed
  within under 0.25 s by **two** `Decoder stopped: surfaceDestroyed` lines (the brief's own
  "two gated safety-net stops" — confirmed idempotent no-ops, no double-teardown symptom of any
  kind), then `Media Sink Stop Request: VIDEO` about 0.5-0.6 s later. **This is the
  legitimate-background path the fix claims to leave untouched, exercised for the first time on
  this rig, and it worked exactly as specified**: focus released, phone told to stop the sink,
  nothing forbidden anywhere near it. `Media Sink Stop Request: VIDEO` never once appeared after a
  *return* trigger in any of the 17 cycles this round ran — the rounds 3/4 FAIL meaning of that
  line was never at risk.

## R1 — GLES, holds 5 s / 45 s / 180 s / 5 s

**PASS.** Zero forbidden lines anywhere in a 1,280,590-line capture. Cover never tore the surface
down (see above). All four cycles ended with a stable, rendering picture; the session was alive
and rendering at run end.

- Settings written: `wifi-connection-mode=3`, `enable-audio-sink=true`, `view-mode=2`
- Radio state: unchanged from default (Native AA session already using Bluetooth/WiFi Direct as
  normal for this mode)
- Discard-rule check: clean, no re-run needed
- Self-foreground events during any hold: **0**
- `Forcing restart (` counts per cycle: **2, 4, 0, 6**
- Return (device `Events injected: 1`) → `First frame rendered`, per cycle: **21.7 s, 45.9 s,
  6.8 s, 116.4 s**

Cycle 4 (hold 5 s) is the one worth flagging on its own: it exceeded the 90 s per-cycle budget
(116.4 s) — the only cycle in the whole round to do so. Read from the capture, this is exactly
round 4's confirmed non-escalating pattern, now hit via the real user route: the decoder reached
`Forcing restart (4/4)` once (line-timestamped 09:28:21.553), then a further 59.6 s stall reset the
counter to `(1/4)` and it recovered two restarts later at `(2/4)`, rendering cleanly at 09:29:36.650
— well before this run's own soak window ended. No forbidden line fired at any point in that
recovery, and the session was rendering normally (`Throughput over` at steady fps) for the
remainder of the run. Kept as a full capture (`r1-gles.txt`) rather than an excerpt per the FAIL
capture rule, even though the run's overall verdict is PASS, since it is the one cycle that missed
its own per-cycle number.

## R2 — TEXTURE, holds 5 s / 45 s / 180 s / 5 s

**PASS, clean.** Zero forbidden lines in a 1,410,845-line capture. Cover never tore the surface
down. All four cycles recovered comfortably inside budget — this is the backend round 4 measured
as the slowest to relaunch, and it was the fastest of all three here.

- Settings written: `wifi-connection-mode=3`, `enable-audio-sink=true`, `view-mode=1`
- Discard-rule check: clean
- Self-foreground events during any hold: **0**
- `Forcing restart (` counts per cycle: **2, 4, 1, 0**
- Return → `First frame rendered`, per cycle: **28.1 s, 52.5 s, 13.4 s, 5.4 s**

## R3 — SURFACE, holds 5 s / 45 s / 180 s / 5 s

**PASS, this is the headline run of the round.** First dedicated SURFACE coverage of the whole
investigation. Zero forbidden lines in a 899,758-line capture. Every cycle showed a genuine
teardown-and-rebuild and recovered in **under one second**.

- Settings written: `wifi-connection-mode=3`, `enable-audio-sink=true`, `view-mode=0`
- Discard-rule check: clean
- Self-foreground events during any hold: **0**
- `Forcing restart (` counts per cycle: **0, 0, 0, 0** — never needed; the surface was cleanly
  torn down and rebuilt each time rather than left idling for a stall watchdog to catch
- Cover → teardown confirmed each cycle: `onSurfaceDestroyed` fired within ~1 s of the cover's own
  `ActivityTaskManager: START`, immediately followed by the two idempotent `Decoder stopped:
  surfaceDestroyed` lines and then `Media Sink Stop Request: VIDEO`, every cycle, in that order
- `Decoder stopped: onDetachedFromWindow` fired on **every** cycle's return, within 20-60 ms of the
  device's actual `Events injected: 1` — confirmed present as the brief asked, and never appeared
  a second time per cycle (the "does any stop appear twice for one teardown" question: no —
  `onDetachedFromWindow` is single per return, `surfaceDestroyed` is the one that is
  (deliberately, idempotently) double per cover)
- Return → `First frame rendered`, per cycle: **0.68 s, 0.68 s, 0.71 s, 0.80 s**

No stale-surface-gate line (`onSurfaceDestroyed for a stale surface - ignoring`) fired in any of
R1-R3 — consistent with there being no case this round where a *second*, superseding surface
arrived while an old one's teardown callback was still in flight (that is R4's job, on a different
backend).

## R4 — rapid switching, GLES, 5× (cover → 3 s hold → return) back-to-back

**PASS.** Zero forbidden lines in a 379,952-line capture. This is round 4's own documented
deviation case — a fresh surface superseding a still-recovering codec — reproduced through the
real user route instead of the scripted trigger, and it did not escalate.

- Settings written: `wifi-connection-mode=3`, `enable-audio-sink=true`, `view-mode=2`
- Discard-rule check: clean
- Self-foreground events during any hold: **0** (every `AapProjectionActivity` START in the
  capture is the deliberate return cascade, tied 1:1 to a preceding LAUNCHER return)
- `Forcing restart (` total across the run: **9** (`1/4, 2/4` on cycle 2's surface; `1/4, 2/4` on
  cycle 3's; `1/4` on cycle 4's; `1/4, 2/4, 3/4, 4/4` on cycle 5's)
- Cycle-by-cycle outcome, since cycles overlap too tightly for independent per-cycle budgets:
  cycle 1 (initial launch) rendered normally; cycles 2 and 3's fresh surfaces were each superseded
  by the next cover before ever rendering a frame — no forbidden line either time, just silently
  replaced; cycle 4's surface (set 10:06:49.438) rendered one frame at 10:07:01.141 (11.7 s later)
  before cycle 5 covered again; cycle 5's surface (set 10:07:19.038) hit all four restarts up to
  `(4/4)` at 10:07:59.431, then recovered — counter reset, two more restarts, `First frame
  rendered` at 10:08:13.079, **54.0 s after its own New-surface line** — and stayed up through the
  run's closing soak.
- No `Media Sink Stop Request: VIDEO` anywhere (GLES, matches R1's no-teardown finding)

The session was alive and rendering at run end, and at no point did any restart counter's ceiling
produce a forbidden line or a permanent-failure symptom — the exact property round 4 verified under
the scripted trigger now holds under genuine rapid app-flipping too.

## Anything the brief did not ask about

- The `monkey`-injection-delay finding above (Setup notes) is the round's real surprise: it is
  precise enough (±0.01 s across 17 measurements) that it reads more like a fixed internal
  timeout than device jitter, and it would have silently inflated every latency number in this
  report by ~26 s had the device-log timestamp not been cross-checked against the host-side one.
  Worth a `TESTING-TEMPLATE.md` §7a entry for any future brief that times something off a
  `monkey`-driven trigger on this rig.
- SURFACE's near-instant (<1 s) recovery versus GLES/TEXTURE's 5-116 s is a real, measured
  difference in relaunch cost between backends on this SoC, in the *opposite* direction from what
  might be assumed (the backend that actually tears its surface down recovers fastest, because it
  gets a clean rebuild instead of waiting out an idle-stall watchdog on a decoder that was never
  actually stopped). Not a defect in either direction — flagged here only because it is
  counter-intuitive and could misdirect a future round that assumes "real teardown" means "slower
  recovery."
- R1's cycle 4 and R4's cycle 5 both independently exercised the exact `(4/4)`-then-reset-then-
  recover path round 4 first confirmed; seeing it twice more, unprompted, via the real user route
  (not the scripted `am start -n MainActivity` trigger) is more evidence for the fix than either
  run's own PASS/FAIL line captures on its own.
