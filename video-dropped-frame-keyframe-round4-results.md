# Dropped-frame keyframe recovery: round 4 results

**Candidate:** `test/830-keyframe-lever-probe` @ `1dc7e6ec` (TEST ONLY, never to be merged)
**Baseline:** none. Every measurement is a property of the phone's own response to each lever
**APK md5:** `559456b2f8bbec84c58fba322d68f9aa`
**Unit:** UNISOC MT50_YT610E4GFPSL_U, Android 14, 3.8GB RAM
**Date:** 2026-08-14

## Setup notes

- **Navigation still could not be started**, same as round 1. Substituted phone media playback
  (Amazon Music, started via `input keyevent 85` relayed through the head unit, no phone screen
  touched) as the moving-screen content for both R1 and R2, per the same fallback used and approved
  in round 1. The track auto-advanced through the phone's own queue for the full ~16 minutes with no
  further intervention needed.
- The session was already live (auto-started via `auto-start-bt-macs`) by the time settings were
  applied and the app relaunched, so R1's capture starts mid-session rather than at a cold `Codec
  initialized:` line. `Throughput` lines confirm `c2.unisoc.avc.decoder` throughout R1 and R2; the
  codec component was never re-initialized even across R2's seven `Media Start Request VIDEO` cycles
  (zero `Codec initialized:` lines anywhere in R2), which is itself worth recording as a stability
  data point for the #755 question.
- **R1's cross-check needed a judgment call the brief anticipated but didn't fully resolve.** The
  size method's raw output, sorted by size, does not cleanly separate into "3 keyframes, rest small."
  Right after each true keyframe there are several single-fragment (`flags: 11`) frames in the
  5,000-18,000 byte range, well above the 1,496-byte median but far below the ~70,000-byte true
  keyframes, and the scanner never flags these. Distinguishing the two by fragmentation pattern rather
  than raw byte threshold makes the two instruments agree exactly: the three scanner-flagged
  keyframes each correspond to a `flags: 9 -> 8 -> ... -> 10` fragment run whose summed payload
  matches the scanner's own byte count to within ~10 bytes (77542 vs 77532, 72578 vs 72568, 67335 vs
  67325) and within 14-21 ms of the scanner's timestamp; every one of the ambiguous secondary "large"
  frames is a single unfragmented `flags: 11` message, a signature no true keyframe in this capture
  ever showed. Judged **PASS** on that basis, see R1 below for the full number. This reproduces, and
  explains, exactly the ambiguity round 2 flagged in the old method, and is why
  `VideoKeyframeScanner` is the more trustworthy instrument going forward.
- Scripts used: `build_hur.sh`, `run_unit_tests.sh`, `set_hu_prefs.sh` (all as documented, no changes
  needed). No new scripts added.
- One host-side artifact of running each probe phase as a separate backgrounded shell call: there is
  real (harmless) wall-clock drift between the brief's nominal phase boundaries and actual fire times
  (e.g. L3-1 fired ~14s after L2's last `sleep 25` nominally ended) from polling latency between
  phases, not from the schedule itself. Every table below uses the real fire timestamps from the
  capture, not the nominal schedule.

## R0: build and unit-test gate

**PASS.** `build_hur.sh` clean, `run_unit_tests.sh` clean. **284/284** unit tests (round 2/3's 272
plus the new `VideoKeyframeScannerTest` **12/12**), exactly as predicted. APK md5
`559456b2f8bbec84c58fba322d68f9aa` confirmed identical between the built artifact and the live install
(`pm path` + `md5sum` on-device).

## R1: is the new keyframe line trustworthy? (gate for R2)

**PASS.** 3-minute undisturbed capture, no probes fired, screen moving (phone media). `dropped=0`
throughout (matching rounds 1-2's hardware-decoding census). Codec: `c2.unisoc.avc.decoder` (no
`Codec initialized:` line in-window; the session predates this capture, see Setup notes).

Both instruments found exactly **3** keyframes, at matching timestamps and near-identical byte counts:

| # | Scanner (`keyframe reached the codec`) | Size method (fragment sum) | Time gap | Size gap |
|---|---|---|---|---|
| 1 | 14:18:03.133, 77532 B | 14:18:03.119, 77542 B | 14 ms | 10 B |
| 2 | 14:19:12.453, 72568 B | 14:19:12.439, 72578 B | 14 ms | 10 B |
| 3 | 14:20:22.079, 67325 B | 14:20:22.058, 67335 B | 21 ms | 10 B |

Median frame size (whole window): 1,496 bytes. Every true keyframe fragmented as `flags 9 -> 8 -> 8
-> 8 -> 10`; every large single-message (`flags 11`) frame the raw size sort also turns up (5,248 to
18,627 bytes) is a P-frame the scanner correctly does not flag, see Setup notes. `VideoKeyframeScanner`
is confirmed trustworthy; R2 proceeds on it alone.

## R2: which lever produces a keyframe? (the point of the round)

**The headline finding, stated up front:** **none of L1, L2 or L3 makes the picture recover any
faster than doing nothing, and L3 is not actually cheaper than L4.** `NATIVE_TRANSIENT` was hoped to
hold the session; on this hardware it did not. Every one of the 5 L3 fires produced a real `Media
Sink Stop Request: VIDEO` -> `Media Start Request VIDEO` cycle, session-incrementing exactly like L4.
The "keyframe" that follows every L3 and L4 fire is not evidence of a nudge working. It is the fresh
session's own tiny startup frame (see below).

One continuous ~16-minute capture (2 min quiet, then 8xL1, 8xL2, 5xL3, 2xL4, then 2 min quiet). All
23 probes fired cleanly (`Broadcast completed: result=0` on every call) and are accounted for in the
log via their own `KeyframeLeverProbe:` lines: 8 L1 + 8 L2 + 5 L3 + 2 L4 = 23/23. `dropped=0` for the
entire capture, no confounder anywhere, and the forbidden `AapTransport: Requesting recovery keyframe`
line never appears (0 occurrences).

### L1: `UpdateUiConfigRequest`, margins unchanged

The phone acknowledges every single fire (`RX: Update UI Config Reply received... AA acknowledged new
margins`, 8/8) but never sends a keyframe in response. The keyframes that land during the L1 window
are the same natural cadence continuing uninterrupted (see control, below).

| # | T0 | Delta to next keyframe |
|---|---|---|
| 1 | 14:24:40.811 | 19.640 s |
| 2 | 14:25:05.895 | 64.060 s |
| 3 | 14:25:30.996 | 38.959 s |
| 4 | 14:25:56.099 | 13.856 s |
| 5 | 14:26:21.187 | 58.065 s |
| 6 | 14:26:46.294 | 32.958 s |
| 7 | 14:27:11.383 | 7.869 s |
| 8 | 14:27:36.473 | 52.032 s |

n=8, median **35.96 s**, min 7.87 s, max 64.06 s, 0/8 under 1 s.

### L2: `UpdateUiConfigRequest`, margins toggled by 1 px

Same picture: 8/8 acknowledged, 0/8 produce anything faster than the natural cadence.

| # | T0 | Delta to next keyframe |
|---|---|---|
| 1 | 14:28:07.327 | 21.178 s |
| 2 | 14:28:32.412 | 63.944 s |
| 3 | 14:28:57.494 | 38.862 s |
| 4 | 14:29:22.589 | 13.767 s |
| 5 | 14:29:47.678 | 58.192 s |
| 6 | 14:30:12.760 | 33.110 s |
| 7 | 14:30:37.854 | 8.016 s |
| 8 | 14:31:02.942 | *excluded, see note* |

n=7 (L2-8 excluded: the next keyframe-line event after it is L3-1's own product, firing only 0.78 s
after its own T0. Attributing that keyframe to L2-8, 40 s earlier, would misrepresent an L3 result as
an L2 one). Median **33.11 s**, min 8.02 s, max 63.94 s, 0/7 under 1 s.

### L3: focus release as `NATIVE_TRANSIENT`

**This was the whole question, and the answer is no.** All 5 fires produced `Media Sink Stop Request:
VIDEO` (with the log's own `Video Sink Stopped -> Ignored (Forced Keyframe Request)` annotation) 53-66
ms after the retake, followed immediately by `Media Start Request VIDEO` with an incrementing
`session=` (1 through 5). Release-to-retake held at the coded 400-403 ms gap, matching round 3's L4
figure exactly.

| # | Release T0 | Retake | Release-to-kf | Retake-to-kf | `session=` |
|---|---|---|---|---|---|
| 1 | 14:31:42.236 | 14:31:42.639 | 0.783 s | 0.380 s | 1 |
| 2 | 14:32:22.321 | 14:32:22.723 | 0.671 s | 0.269 s | 2 |
| 3 | 14:33:02.405 | 14:33:02.807 | 0.532 s | 0.130 s | 3 |
| 4 | 14:33:42.494 | 14:33:42.895 | 0.650 s | 0.249 s | 4 |
| 5 | 14:34:22.582 | 14:34:22.983 | 0.521 s | 0.120 s | 5 |

n=5, all under 1 s (release-to-kf median 0.650 s; retake-to-kf median 0.249 s), but this is not L3
"working" in the sense the round was testing for. Every one of these "keyframes" is **8,200 bytes**
(wire size 8,210 bytes, confirmed against the raw `RECV: VIDEO` line, a single unfragmented `flags:
11` message), against a natural keyframe size of 51,000 to 80,000 bytes measured in the same capture
(see below). This is the fresh session's own tiny startup frame, sent because the phone tore the sink
down and rebuilt it, the identical mechanism L4 uses, not a lighter one. **`NATIVE_TRANSIENT` does
not hold the session on this hardware; it behaves exactly like a full `NATIVE` release for the
purposes of this recovery mechanism.**

### L4: focus release as `NATIVE` (the control)

Reproduces round 3 exactly. Both fires: `Media Sink Stop Request: VIDEO` -> `Media Start Request
VIDEO` (`session=6`, `session=7`), 400-403 ms retake gap, tiny 8,200-byte startup keyframe within
0.53-0.68 s of release.

| # | Release T0 | Retake | Release-to-kf | Retake-to-kf | `session=` |
|---|---|---|---|---|---|
| 1 | 14:35:19.799 | 14:35:20.202 | 0.534 s | 0.131 s | 6 |
| 2 | 14:36:19.884 | 14:36:20.286 | 0.676 s | 0.274 s | 7 |

### Natural cadence: the control

Two dedicated quiet phases plus the seven gaps spanning the entire unbroken L1/L2 window (16 probe
fires between them, zero effect on the cadence):

| Gap | Duration |
|---|---|
| K1 to K2 (pre-L1 quiet) | 69.392 s |
| K2 to K3 (spans L1) | 69.602 s |
| K3 to K4 (spans L1) | 69.504 s |
| K4 to K5 (spans L1) | 69.297 s |
| K5 to K6 (spans L1/L2) | 69.253 s |
| K6 to K7 (spans L2) | 67.851 s |
| K7 to K8 (spans L2) | 69.514 s |
| K16 to K17 (post-L4 quiet) | 69.728 s |

n=8, median **69.448 s**, min 67.851 s, max 69.728 s: a strikingly tight ~69-70 s period (much
tighter than rounds 1-2's 7.5-70.1 s / 9.0-69.9 s range measured under forced software decoding and
active drops). On the clean hardware path with zero drops, the phone appears to simply be emitting
keyframes on a fixed encoder GOP schedule rather than in response to any network feedback: the whole
7-gap span from K2 to K8, straddling all 16 L1/L2 fires, never deviates from this ~69-70 s period by
more than 1.9 s. **L1's and L2's Δ medians (35.96 s and 33.11 s) sit almost exactly at half this
period**, the signature of a random observer waiting out a periodic process, identical to what
rounds 1 and 2 found for the gain-only nudge. Both levers are confirmed inert.

### #755 check

`dropped=0` for the entire ~16-minute capture. fps ranged 37-57 across all Throughput windows (the
one 37fps reading, at 14:30:40.946 during L2, is an isolated single-window dip unrelated to any
probe, `dropped=0` in that same window, and fps was back to 51-52 in the next). Through **seven**
real sink-stop/start cycles (5xL3 + 2xL4) in under 5 minutes, more disruption in less time than
round 3's single cycle, fps never failed to recover, and the underlying codec component was never
re-initialized (`Codec initialized:` count: 0, meaning `c2.unisoc.avc.decoder` persisted across every
cycle). No #755 signal anywhere in this round.

### Picture blink

Not directly observed: the round ran unattended per house rule 4, with no user watching the physical
screen. Inferred from the log evidence only: L1/L2 caused no session disruption of any kind and almost
certainly did not blink; L3/L4's confirmed sink-stop/start cycles almost certainly did, consistent
with round 3's own visually-confirmed observation of the same mechanism.

## Anything the brief did not ask about

- **`AapTransport: Requesting recovery keyframe (unsolicited focus gain).` never appeared**: 0
  occurrences across the whole ~16-minute R2 capture. No confounder; `dropped=0` the entire time means
  nothing else in the app ever had a reason to ask for a keyframe.
- **The phone answered every single `UpdateUiConfigRequest`**, L1 and L2 alike: `RX: Update UI Config
  Reply received. Acknowledging UI Config change.` followed by `[UI_DEBUG_FIX] UpdateUiConfig reply
  received. AA acknowledged new margins.`, 16/16, one per fire, zero missed. The message is not being
  silently dropped by the phone; it is being correctly processed and acknowledged, just without any
  video-side side effect. This rules out "the phone never saw it" as an explanation for L1/L2's
  inertness.
- L3's own log line already predicted this round's finding before the capture confirmed it: `Video
  Sink Stopped -> Ignored (Forced Keyframe Request)` fires as part of the existing app's own sink-
  stop handling on every L3 fire, meaning the app's own code already treats a video sink stop as a
  forced-keyframe situation, consistent with L3 being functionally indistinguishable from L4 here.

## Net position for #830

Two things the brief asked to be settled:

1. **Does any of L1/L2/L3 collapse Δ the way L4 does?** No. L1 and L2 are measured inert a third time,
   now with the tightest and cleanest control distribution of any round (69.45 s median, under 2 s
   spread). L3 does not hold the session as `NATIVE_TRANSIENT` should in principle: it costs exactly
   what L4 costs, a full sink-stop/start cycle, on this hardware. **The fix ships on the L4 cycle**
   (or L3, which is no cheaper); there is no free or pixel-cheap lever available.
2. **Is `VideoKeyframeScanner` trustworthy?** Yes, confirmed in R1: exact agreement with the
   fragmentation-aware size method on count, timing (within 21 ms) and byte size (within 10 bytes) for
   all 3 true keyframes in an undisturbed session. It is the right evidence source for a
   request-until-answered latch and can be cherry-picked onto the fix branch as planned.
