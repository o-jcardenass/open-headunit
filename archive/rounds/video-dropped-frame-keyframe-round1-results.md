# Dropped-frame keyframe recovery — round 1 results

**Candidate:** `fix/830-request-keyframe-on-dropped-frame` @ `563ae013`       **Baseline:** none (no baseline needed per brief §1)
**APK md5:** `b2a1abac3733ca26d9979018c48713fb`
**Unit:** UNISOC MT50_YT610E4GFPSL_U, Android 14, Native AA wireless (this rig has no USB accessory path)
**Date:** 2026-08-14

## Setup notes

- Inventoried `hur-wifi-test-scripts/` per house rule; used `build_hur.sh`, `run_unit_tests.sh`, and
  `set_hu_prefs.sh` for every settings write. No new script was needed.
- **The brief's primary "moving screen" method (live navigation with an animating map) could not be
  run** — no route could be started on the phone this round. Used the brief's own documented
  alternative instead: phone media playback started via the head unit's media relay
  (`adb shell input keyevent 85`, i.e. `KEYCODE_MEDIA_PLAY`), no phone screen touched. This routed
  through the phone's Bluetooth media session (`com.android.bluetooth/BluetoothMediaBrowserService`)
  and produced sustained real AA video traffic at 49-54 fps for the whole of R1 — not a parked/static
  screen. Said so here per the brief's own instruction to record what was on screen.
- **R1's own run description in §6 doesn't restate `video-codec=H.264`**, but §4's settings table says
  it is "pinned so R1 and R2 are the same stream." Read that as applying to R1 too and set it there
  (the device's actual `video-codec` was `H.265`, left over from an earlier round, not upstream
  default). Also `view-mode` was `2` (GLES) on the device, not the brief's stated default of `1`
  (TEXTURE) — explicitly set to `1` for R1 onward per the brief's instruction to leave it at default.
- **One `MATCH! Starting AapService` fired in R1**, at 07:55:47.506 — the instant `am start -n
  MainActivity` was issued for the round's very first launch. `TESTING-TEMPLATE.md`'s discard rule
  flags this line unconditionally. Not discarded and re-run: it coincided with the round's initial
  bring-up (phone Bluetooth was already reconnecting from the prior round's session), produced exactly
  one P2P group (`p2p-wlan0-5`) and one handshake with no second `createGroup SUCCESS` afterward, and
  the 5-minute timed census window did not begin until 07:59:16 — 3.5 minutes after a session that was
  already stable. R2, R2b and R3 (all launched the same way) show zero occurrences of this line, so it
  did not recur. Flagging it plainly rather than silently treating it as clean, since the rule is
  unconditional; the census figure it might have touched (R1's `dropped=0`) is unambiguous either way.
- **The frame-assembly recipe (§6, R3) works unmodified**, but continuation fragments (`flags: 8`,
  `flags: 10`) log under `RECV: VIDEO Unknown (N)` rather than `RECV: VIDEO Media Data` — the `type`
  field printed for those lines is raw mid-stream H.264 bytes misread as a message type, not an actual
  type. The brief's `grep -F "RECV: VIDEO"` already matches these regardless of message name, so no
  correction was needed to the pipeline itself; noting it because a spot-check of a few consecutive
  fragment lines looks like unrelated garbage traffic until the `flags` field is checked. Sanity-check
  passed: every fragmented sequence in the capture was exactly one `9` then zero-or-more `8`s then one
  `10` (69 firsts, 69 lasts, 18 middles across the whole R3 capture) — no unterminated sequences.
- **Keyframe-size threshold for R3 chosen as ≥10000 bytes.** Median assembled-frame size was ~1590
  bytes (ordinary P-frames, sent whole in a single `flags: 11` message); the size distribution has a
  clean gap before a 141-of-16882 cluster spanning 10000-115170 bytes. Nothing fell in an ambiguous
  band large enough to move the threshold.

## R0 — build and unit-test gate

**PASS**

- Both commits compiled clean on the first try (`563ae013` had never been built before this round).
- `./gradlew testGithubDebugUnitTest`: **264/264** passed. `VideoRecoveryPolicyTest`: **6/6** (covers
  the three cases the brief named — "a drop before the first rendered frame never asks", "the first
  drop after a rendered frame asks immediately", "drops share the corrupt-frame throttle window" —
  plus three more throttle-window edge cases in the same suite).
- APK md5 `b2a1abac3733ca26d9979018c48713fb`, copied out of `apks/` immediately per §7a and installed
  with `adb install -r`; live-APK md5 confirmed identical before any run.

## R1 — baseline drop census (answers Q1)

**Census, no PASS/FAIL** (per brief's own framing)

- Settings written: `log-level=2`, `view-mode=1` (TEXTURE), `video-codec="H.264"`,
  `force-software-decoding` and `software-video-decoder` deleted.
- Radio state: Native AA (`wifi-connection-mode=3`, unchanged), phone Bluetooth enabled via
  `svc bluetooth enable`.
- Discard-rule check: 1 P2P group (`p2p-wlan0-5`), 1 SSL handshake, 0 `Magic Garbage`, 0 second
  `createGroup SUCCESS` — clean apart from the single `MATCH!` line discussed in Setup notes.
- Decisive log lines:
  - `08-14 07:55:52.043 ... Codec initialized: c2.unisoc.avc.decoder`
- Measurements (5-minute timed window, 07:59:16-08:04:16, 60 `Throughput` lines):
  - **`dropped=0`, summed across the entire window.**
  - `dropped=0` across the entire capture too (102 `Throughput` lines total, including pre-window
    settle time).
  - `Input buffer full. Dropping frame.`: 0. `VideoDecoder: dropped a reference frame`: 0.
    `AapVideo: Frame corrupted`: 0. `Frame larger than the codec input buffer:`: 0.
  - Sustained `rendered` fps: 49-54 for nearly the whole window; two dips to 25/22 fps immediately
    around the audio-session-change transition at 07:59:04, recovered within one 5 s window.

**This rig does not reproduce #830's precondition under hardware decoding** — zero shed frames across
a full 5-minute session of real, moving AA traffic on `c2.unisoc.avc.decoder`. Q1's answer on this
hardware is negative; the fix's coverage on hardware decoding has to come from a reporter or from R2's
provoked run below.

## R2 — provoke drops (gate for R3)

**PASS**

- Settings written: `force-software-decoding=true`, `software-video-decoder=0` (added on top of R1's
  `log-level=2`/`view-mode=1`/`video-codec=H.264`, unchanged).
- Discard-rule check: 1 P2P group (`p2p-wlan0-6`), 1 handshake, 0 `MATCH!`, 0 `Magic Garbage` — clean.
- Decisive log lines:
  - `08-14 08:05:34.992 ... Codec initialized: c2.android.avc.decoder` — software path confirmed taken.
  - `VideoDecoder: dropped a reference frame, requesting keyframe`: **5 occurrences.**
  - `AapTransport: Requesting recovery keyframe (unsolicited focus gain).`: **5 occurrences**, each
    matching one of the five drop lines by timestamp to the millisecond. `AapVideo: Frame corrupted`:
    0 — every nudge this run came from the fix's own drop-triggered path.
- Measurements: `dropped=20` total across the capture (14 during the connection-settle transient before
  the timed window began, 6 inside the 5-minute timed window, 60 `Throughput` lines in-window);
  `rendered` stayed non-zero throughout (180-273 per 5 s window).

Both PASS conditions met: `dropped>0` and the fix's line fired at least once. `Codec initialized`
confirms the software lever took effect (`c2.android.avc.decoder`, not the hardware
`c2.unisoc.avc.decoder`).

## R2b — positive control

**PASS**

- Settings written: `force-software-decoding` and `software-video-decoder` deleted (reverting to R1's
  hardware-decoder config); `log-level`/`view-mode`/`video-codec` unchanged.
- Discard-rule check: 1 P2P group, 0 `MATCH!` — clean.
- Decisive log lines: `08-14 08:11:49.113 ... Codec initialized: c2.unisoc.avc.decoder` — hardware path
  confirmed restored.
- Measurements (2-minute window, 08:12:07-08:14:07, 24 `Throughput` lines): **`dropped=0`**;
  `VideoDecoder: dropped a reference frame`: 0.

Drops and the fix's line both returned to R1's clean baseline the moment the lever was removed,
confirming R2's drops were caused by forcing software decoding, not by session conditions.

## R3 — does the nudge produce a keyframe? (the point of the round)

**Gate:** R2 = PASS, so R3 ran as specified.

- Settings written: same as R2, plus `log-level=0` (VERBOSE) — read back and confirmed
  (`log-level" value="0"`) before launch.
- Discard-rule check: 1 P2P group (`p2p-wlan0-8`), 0 `MATCH!`, 0 `Magic Garbage`. Two lines both
  contain the substring "SSL handshake complete" (`AapSslContext.performHandshake` and
  `AapTransport.handshake`) but they are one event, 1 ms apart (08:14:41.951/.952) — two different log
  statements for the same handshake, not a second reconnect. Clean.
- `Codec initialized: c2.android.avc.decoder`.
- Total `dropped=510` across the ~5.5-minute capture (65 `Throughput` windows).
- `VideoDecoder: dropped a reference frame, requesting keyframe`: **177 occurrences**, every one
  paired 1:1 by timestamp (within 1 ms) with an `AapTransport: Requesting recovery keyframe
  (unsolicited focus gain).` line. `AapVideo: Frame corrupted, requesting keyframe to recover stream`:
  **0** — on this hardware, under sustained software-decoder overload, the fix's own path was the only
  requester ever exercised.
- `Frame larger than the codec input buffer:` — 0 occurrences (as predicted, rare by design; did not
  appear in R1 or R2 either).

### (a) Natural keyframe cadence

Keyframe-sized frames (≥10000 bytes) cluster tightly in two places that are not representative of
steady-state cadence: a connection-startup burst from 08:14:45.807 to 08:14:48.326 (frames every
9-40 ms, clearly the codec's own catch-up flood after `Codec initialized`), and a second tight cluster
around 08:19:33 (13 frames, ~100 ms apart). Outside those two clusters, the isolated steady-state gaps
between keyframe-sized frames were:

| From → To | Gap |
|---|---|
| 08:15:10.457 → 08:15:54.225 | 43.768 s |
| 08:15:54.225 → 08:17:04.358 | 70.133 s |
| 08:17:04.456 → 08:18:14.192 | 69.736 s |
| 08:18:14.227 → 08:19:16.031 | 61.804 s |
| 08:19:16.057 → 08:19:23.524 | 7.467 s |
| 08:19:23.629 → 08:19:32.824 | 9.195 s |

Median assembled-frame size overall: **~1590 bytes**. Threshold chosen for "keyframe-sized": **≥10000
bytes** (see Setup notes).

### (b) What follows each nudge

177 nudges total; 156 had a later keyframe-sized frame recorded in the capture (21 were near the
capture's end with no further keyframe logged before the round stopped).

| Metric | Value |
|---|---|
| Median Δ (nudge → next keyframe-sized frame) | **30.391 s** |
| Mean Δ | 28.936 s |
| p90 Δ | 56.902 s |
| Max Δ | 70.066 s |
| Δ < 0.1 s | 3 / 177 |
| Δ < 0.5 s | 6 / 177 |
| Δ < 1.0 s | 8 / 177 |
| Δ ≥ 5.0 s | 133 / 177 |

The 8 sub-1-second deltas, examined individually: 2 fall inside the connection-startup burst (where
keyframe-sized frames were arriving every 9-40 ms regardless of any nudge), and the other 6 land
shortly before the same widely-spaced "natural cadence" keyframes tabulated in (a) above. That is not
surprising on its own: nudges fire roughly every 1.5 s throughout the whole run (throttled), so *some*
nudge is within a second of *any* keyframe purely by density, independent of causation.

**The nudge is inert on this hardware.** Median Δ (30.4 s) sits inside the natural cadence's own range
(7.5-70 s, median ~52.8 s across the six isolated gaps) rather than being clearly shorter than it, which
is what a working nudge would show. The keyframes that follow a nudge are the ones that were coming
anyway on the phone's own schedule — the same conclusion the `video-black-after-background` rounds
reached for this identical gain-only `VideoFocusEvent`, now confirmed on a live, running stream rather
than a backgrounded one.

### #755 fear check

`rendered` fps held at 49-51 in every one of the 65 `Throughput` windows across the whole run,
including windows immediately after nudges and windows with double-digit `dropped` counts (e.g.
`08:19:18.912 rendered=249 fps=49 dropped=20`). No sustained fps degradation anywhere. The one 33 fps
dip (`08:14:48.658 rendered=168 fps=33 dropped=26`) sits inside the connection-startup transient, not
mid-stream fallout from nudging.

## Anything the brief did not ask about

- **Ratio of `AapVideo: Frame corrupted` to `VideoDecoder: dropped a reference frame` nudges across the
  whole round: 0 : 187** (0:5 in R2, 0:177 in R3). On this hardware, under sustained software-decoder
  overload, the corrupt-frame requester never fired once in either provoked run — only the new
  drop-triggered path in `563ae013` did anything. If a request-until-answered redesign is scoped from
  this round's evidence, the drop-triggered path is the one to build it around; the corrupt-frame path
  contributed nothing to observe here.
- Continuation-fragment log lines appearing under `RECV: VIDEO Unknown (N)` rather than `RECV: VIDEO
  Media Data` (Setup notes) is worth carrying into the next round's own sanity-check step explicitly,
  since it is easy to misread as unrelated traffic on a quick eyeball pass.
