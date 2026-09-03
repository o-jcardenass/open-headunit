# video-black-after-background — round 1 results

**Candidate A:** `v.3.2.4` @ `c9556803bbf90f495f5be1a360c7698b59ac8637`
**Candidate B (baseline):** `v.3.2.3` @ `e900de7832178e91558749bc6c781fa689130661`
**APK md5:** A `5a19bdb1696d95ba2bc224de853e29da` / B `6489a53c822676c217b6a6adf0a1da70`
**Unit:** UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14, Native AA wireless (mode 3) only
**Date:** 2026-08-12

## Setup notes

- **Both builds compiled clean from `git checkout v.3.2.4` / `v.3.2.3` in the main repo directory**,
  built with `build_hur.sh`, copied out of `apks/` into `apks/round1-video-black/` before the next
  build overwrote them (the script's own `rm -f com.andrerinas.headunitrevived_*.apk` otherwise
  destroys the previous build). Installing B over A hit
  `INSTALL_FAILED_VERSION_DOWNGRADE` (versionCode 97→96, the tags aren't monotonic) — worked around
  with `adb install -r -d`, which is safe here because both builds are debuggable. Not in the brief;
  worth adding to `TESTING-TEMPLATE.md` if a future round A/Bs a downgrade.
- **`log-source`/`log-capture-enabled` didn't exist yet in `settings.xml`** on this unit's prior
  state; `set_hu_prefs.sh` created them correctly (its own "set" path handles a missing key).
- **The framework logcat stream was only captured for R1's window** (~09:28–09:38), not the whole
  round. The capture process was live via `adb logcat -v time > file &` but the pipe silently stopped
  advancing once the unplanned reboot (see below) cycled the USB transport; it was not restarted for
  R2–R5. The three `ACodec`/`OMX`/`MediaCodec` lines that did get captured are routine
  (`flushMediametrics`, one `setting surface generation`) — no crash, no wedge signature. Given every
  run below passed by continuity with zero decoder restarts, this gap does not change any verdict,
  but a future round chasing an actual FAIL should re-arm the logcat capture after any USB drop, not
  assume it survived.
- **Unplanned interruption, mid-round:** the head unit lost power and rebooted during R2's third cycle
  (HOLD=90). `ro.boot.bootreason` read `shutdown,,charging` on the resulting boot — a power event, not
  an app or OS crash. The user reconnected an external battery; the device came back cleanly (bonded
  Bluetooth intact, WiFi on, app settings intact via `settings.xml` on persistent storage). That
  cycle's data was discarded and the cycle was re-run cleanly once the device held a stable uptime.
  No other run was affected.
- Scripts used: `build_hur.sh`, `run_unit_tests.sh`, `set_hu_prefs.sh` (all pre-existing, no changes).
  `adb install -r` for same-version reinstalls, `adb install -r -d` for the one downgrade. No new
  script was needed — every cycle's drive/observe logic fit in a single inline loop, so nothing
  reusable was added to `hur-wifi-test-scripts/`.
- Settings restored to the pre-round backup (`video-codec=H.265`, `view-mode=2`/GLES, `log-level=2`,
  no `log-source`/`log-capture-enabled` keys) and confirmed byte-identical via `diff` before ending
  the round. The debug build (A, v.3.2.4) is left installed rather than the production APK that was
  live beforehand; flag if that matters for a following round.

## R0 — build gate

**PASS**

- `run_unit_tests.sh` on `v.3.2.4`: 244/244 unit tests, `DecoderStopPolicyTest` 6/6.
- Both APKs built from source, md5s recorded and different (A `5a19bdb1...`, B `6489a53c...`).

## R1 — short cycles, 3.2.4, TEXTURE

**PASS**

- Settings written: `log-level=2`, `log-source=1`, `log-capture-enabled=true`, `view-mode=1`,
  `video-codec=Auto` (`wifi-connection-mode` was already `3`).
- Radio state: headunit BT on, WiFi on; phone BT was off at the very start of the round and was
  enabled via `svc bluetooth enable` before any run (Native AA requires it for the handshake); both
  already bonded to each other.
- Discard-rule check: clean, no re-runs.
- Three cycles at `HOLD=3`. **None of the three tore down the surface at all** — no
  `Decoder stopped: surfaceDestroyed` line in any of them. `Throughput over 5000ms` continued
  uninterrupted through every Home/relaunch pair (e.g. cycle 1: `rendered=150 (29fps)` immediately
  before and after, zero gap). This is a stronger pass than the brief's own criterion assumed: it
  predicted a fresh `First frame rendered` line after each relaunch, but on this rig at HOLD=3 the
  surface is never destroyed in the first place, so there is nothing to recover from.
- Measurement: recovery time N/A for all 3 cycles (no teardown occurred to recover from).

## R2 — graduated holds, 3.2.4, TEXTURE

**PASS**

- Settings: unchanged from R1 (build A, TEXTURE, `video-codec=Auto`).
- Three cycles, `HOLD=10`, `HOLD=30`, `HOLD=90`, in order.
- **All three passed by continuity — no `Decoder stopped:` line at any hold length up to 90s.**
  `New surface set:` never appeared either; the surface plainly survives backgrounding on this unit
  regardless of hold duration in this range.
- One cycle (`HOLD=10`) showed a genuine but unrelated throughput dip: `rendered`/`fed` dropped from
  ~29fps to 7–15fps for roughly 30s after the relaunch, recovering to ~29fps on its own, with
  `fed==rendered` throughout (no drops, no skips) — a source-side rate change, not a decoder stall.
  Noted under "anything the brief did not ask about," not counted against the verdict.
- Measurement: recovery time N/A for all 3 cycles.
- The third cycle's first attempt was interrupted by the unplanned power event (see Setup notes) and
  discarded; the number above is from the clean re-run.

## R3 — graduated holds, 3.2.3 — the point of the round

**PASS**

- Settings: build B installed (`adb install -r -d`, downgrade), same `view-mode=1`,
  `video-codec=Auto`; settings.xml carried over unchanged (confirmed via `run-as cat` before and
  after install).
- Same three holds (`10`/`30`/`90`), same observation window.
- **All three passed by continuity, identical in shape to R2** — no `Decoder stopped:`, no
  `New surface set:`, throughput uninterrupted through every relaunch.
- Measurement: recovery time N/A for all 3 cycles.

**R2 did not fail where R3 passed — neither build failed at all.** `9f98afd1`'s feed-thread change
(M2) cannot be isolated by this comparison because the code path it touches (`stop()` called from a
backgrounded surface teardown) never ran on this rig at any of the three holds. This round does not
confirm or rule out M2; it establishes that this rig cannot exercise it via a plain Home-press cycle,
which is a finding about the reproduction method on this hardware, not about the commit.

## R4 — GLES, 3.2.4

**PASS**

- Settings: build A reinstalled, `view-mode=2` (GLES).
- Cycle 1, `HOLD=45`: no `Decoder stopped:` line; throughput held steady at ~50fps (`rendered==fed`,
  `dropped=0`, `skipped=0`) through the entire hold and after relaunch.
- Cycle 2, `HOLD=120`: same result — **no `Decoder stopped:` line even after a 2-minute hold**,
  throughput steady at ~50fps for the full window (samples at T+2s through T+177s all read
  `rendered≈254-256 (50fps)`).
- This is the opposite of the brief's own prediction for GLES (§3, §7): the brief expected surface
  destruction to never be *reported* on Home while the decoder kept feeding a drained-nowhere
  `SurfaceTexture`, burning the stall budget silently. On this rig neither half of that happened —
  the decoder was never stopped **and** kept rendering at full rate, meaning whatever is compositing
  the GLES surface here keeps draining it through the whole background hold. `Decoder stopped:` did
  not appear at all during either hold, which the brief itself calls out as the finding to note if it
  happens (§7, R4).
- Measurement: recovery time N/A for both cycles.

## R5 — codec-flip probe, 3.2.4, TEXTURE

**PASS**

- Settings: `view-mode=1`, `video-codec=H.264` (string). `findBestCodec` confirmed at session start:
  `hw=c2.unisoc.avc.decoder, sw=c2.android.avc.decoder, preferHardware=true, selected=c2.unisoc.avc.decoder`
  — `avc` is H.264, so the explicit choice was honored from the first frame.
- One cycle at `HOLD=30`, observed 90s.
- **No `Falling back to ` anywhere in the capture.** No teardown occurred (consistent with every
  other run this round), so the codec-flip logic in `VideoDecoder.setSurface()`/restart path was
  never entered — this run cannot distinguish "the flip doesn't happen" from "the flip's trigger
  condition never arose," and should be read as the latter.

## R6 — latch probe

**UNTESTABLE**, per the brief's own gate (§7): R6 only runs if R2, R4, or R5 produced a black
screen, and none did — not one cycle in this entire round, across two builds, two view modes, and
holds from 3s to 120s, produced a black screen, a `Decoder stopped:` line, or any interruption to
`rendered`/`fed` throughput. There is nothing to probe the latch against. Force-stop/relaunch
recovery (§9 item 5) is correspondingly untested — there was no black screen to recover from.

## Anything the brief did not ask about

**The headline finding of this round is procedural, not mechanistic: this rig's projection surface
is never torn down by a plain `KEYCODE_HOME` press, at any hold length tested (3s to 120s), in either
TEXTURE or GLES mode, on either 3.2.3 or 3.2.4.** `Decoder stopped: surfaceDestroyed` — the one line
that gates every failure mode in the brief's own analysis (M1, M2, and M3 all require it to fire —
M1 needs `setSurface()` to call `stop()`, M2's race is inside `stop()`, M3 is about what happens
after a stall `stop()` restarts the decoder) — did not appear in any of the 12 scripted cycles across
R1, R2, R4, and R5. Whatever destroys the surface on the reporter's device (a different Android
version, a different launcher/task-recents behavior, actual process death under memory pressure, or
a swipe-away rather than a Home press) is not reproduced by this method on this hardware. This is a
genuine gap in coverage, not a clean bill of health for `VideoDecoder`'s surface-teardown path — the
three defects the brief describes in the source (M1's un-clearable latch, M2's timed-join race, M3's
dead watchdogs) are all still real, still present in the code, and still unverified either way by
this round.

**A secondary, real observation:** R2's `HOLD=10` cycle showed a throughput dip to 7–15fps for ~30s
post-relaunch with no drops or skips, recovering on its own. Not chased further since it's orthogonal
to the black-screen mechanism and never repeated in R1's three faster cycles or R2's other two holds,
but worth a note if a future round is measuring resume latency specifically.

**Recommendation for the next round, if the black screen needs to be reproduced here:** since a Home
press alone does not destroy the surface on this unit, try (a) swiping the task away from Recents
instead of Home, (b) forcing the activity to be killed via `am kill` or a memory-pressure trim while
backgrounded, or (c) checking whether the reporter's device is on an older Android version where
`TextureView`/`GLSurfaceView` surface lifecycle on backgrounding behaves differently. Any of these
would need to be confirmed scriptable within the house rules before being written into a brief.
