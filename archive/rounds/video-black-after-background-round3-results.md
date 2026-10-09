# video-black-after-background — round 3 results

**Candidate:** `fix/822-stale-surface-callback` @ `fc04147e` on `fork`, seven commits.
**APK md5:** `da057272cc8ee7117f07ff68993ee213`
**Unit:** UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14, Native AA wireless (mode 3) only
**Date:** 2026-08-12

**Headline: the branch's own fix works. Every time the stale-surface gate was actually exercised, it
did its job — no black screen, no stuck picture from the mechanism this branch targets. But the round
surfaced a separate, pre-existing, and more serious bug: the decoder's stall-recovery path can
permanently give up on video for the rest of a session, with no way back — not even a legitimate
relaunch. Root-caused to two lines in `VideoDecoder.kt`, confirmed not to be view-backend-specific via
a same-conditions control test across all three renderers, and confirmed not to require audio.**

## Setup notes

- **R0 was a full gate, not a pure check.** `run_unit_tests.sh` (full suite green) then `build_hur.sh`.
  `ProjectionWatchdogPolicyTest` 4/4, `DecoderStopPolicyTest` 6/6, `VideoRecoveryPolicyTest` 3/3, all
  green, confirming the right commit was built.
- **`enable-audio-sink` was found to be `false` on the device**, left over from an earlier round. With
  it off, `ServiceDiscoveryResponse.kt` never advertises the AUD (media) or AU1 (speech) channels to
  the phone at all — only AU2 (system sounds) is offered "to keep connection alive" — so the phone
  never even attempts to open a media-audio channel. This is why round 2's three attempts saw
  `Media Sink Setup Request: 1 on channel AUDIO2` and nothing else: it was never going to get further
  with this setting off. Flipped to `true` for part of this round (see below), then back to `false` to
  finish the structural runs once it became clear audio was not the determining factor in the decoder
  failure (see Inbound).
- **This rig has no speaker** (per `TESTING-TEMPLATE.md` §7a), so audio activity was confirmed only via
  log lines (`AapAudio: AA audio started (AUDIO)` firing after `Media Start Request AUDIO`), never by
  ear.
- **`am start -n $MAIN` on a live session routinely returns `Warning: Activity not started, ...`**
  rather than a clean start — expected, matches round 2. The effect is read from the app's own log, not
  from `adb`'s return message.
- **A Bluetooth speaker was paired to the head unit's own radio mid-round** (user-driven, for audio
  verification). While connected, it occupied the rig's single Bluetooth radio and blocked every poke
  to the test phone, stalling the Native AA handshake for ~15 minutes until the speaker was
  disconnected. Not a code issue; noted because it explains a long gap in the timeline.
- **A head-unit reboot was tried** to rule out a session-length resource issue and did not change the
  underlying condition — the same background camera/DMS service (`cameraserver`, `com.zqc.camera`,
  present since the very first logcat capture of this whole investigation) restarts immediately as part
  of the rig's normal boot sequence. This turned out to be a red herring for explaining the decoder
  failures (see below) and is not the cause.

## The new finding: video can die for the rest of a session, with no way back

**Verdict: CONFIRMED, code-level, not part of this branch.**

### What was observed

Starting partway through the round, the `MainActivity` relaunch trigger began intermittently producing
this sequence instead of a clean recovery:

```
Decoder stall detected (no output for ~2000ms while receiving input). Forcing restart (1/4).
... [decoder reconfigures, stalls again] ...
Forcing restart (2/4), (3/4), (4/4)...
H.264/AVC failed 3 times in a row without rendering a frame. Falling back to H.265/HEVC.
... [HEVC stalls too] ...
Both codec types failed to render a frame this session. Giving up to avoid an infinite restart loop.
```

After this line, the picture is permanently black for the rest of the session. Confirmed visually
twice (screenshots below): `FPS: --`, `Frame: --`, and the app's own stall-detection banner
("Do you see the Android Auto screen?" / SWITCH RENDERER) showing, meaning HUR itself recognizes it
cannot render. Screenshots: `r1redo_fail.png` (20:52:27), `r1redo2_fail.png` (21:48:44).

**Once this state is reached, nothing in the app can recover it within the session.** A second
relaunch trigger sets a new `Surface` (`VideoDecoder.setSurface` logs "New surface set") but produces
*zero* subsequent decoder activity — no `findBestCodec`, no `start()`. Only the AAP-level reconnecting
watchdog keeps asking the phone for keyframes, uselessly, since the local decoder is inert. Recovery
only happens via a full app force-stop and fresh session.

### Root cause (read from source, not inferred)

Two mechanisms compound:

1. **`SYNC_STALL_THRESHOLD_MS = 2000L`** (`VideoDecoder.kt:42`) is measured fresh from each output
   thread's own start (confirmed not a stale-timer bug — `lastOutputMs` is a local `var` reset at
   `outputThreadLoop()`'s entry every restart). This SoC's hardware decoder does not reliably produce
   its first frame within 2 seconds of a **mid-session reconfigure** (as opposed to a fresh cold
   session start, which the earliest clean runs in this round mostly cleared on the first try). That
   marginal warm-up latency, colliding with a threshold with no slack for it, is what starts the
   restart cascade. This is inherent to the existing stall-detection logic; nothing in this branch
   changes it.
2. **`decoderPermanentlyFailed` never clears once latched.** It resets to `false` only inside `stop()`'s
   non-restart-reason branch (`VideoDecoder.kt:441-449`). But the failure path releases the codec via a
   *restart-reason* `stop()` (`"restart: sync_stall"`), which skips that reset. `VideoDecoder.setSurface()`
   (`VideoDecoder.kt:352`) only calls `stop()` at all when `codec != null` — so once the codec has
   already been released, a legitimate relaunch's `setSurface()` call finds `codec == null`, never calls
   `stop()`, and the flag that gates every future `decode()` call (`VideoDecoder.kt:542`) stays latched
   for the rest of the session. Confirmed empirically as described above.

Neither line is touched by any of this branch's seven commits — `codecTypePinned`, `restartsSinceLastFrame`,
`decoderPermanentlyFailed`, and `SYNC_STALL_THRESHOLD_MS` all predate it. The earliest clean runs in this
same round (R1, R2, R3-SURFACE) show the *same* stall-then-restart mechanism firing — they just recovered
within one cycle rather than exhausting the budget. This is a latent, pre-existing weakness in the
decoder's own recovery ladder that this round happened to expose repeatedly, not a regression from the
branch under test.

### What it is *not* (ruled out directly, not assumed)

- **Not audio-specific.** Two of the first three occurrences happened with `enable-audio-sink=true` and
  audio actively decoding (`AudioTrackWrapper.write | Audio queue is full, dropping audio frame` —
  expected on this speakerless rig, not itself a bug). A third occurrence reproduced with audio fully
  disabled (`R3-GLES` retry). Audio is at most correlated, not causal.
- **Not GLES-specific.** All four `R3-GLES` attempts failed, which raised the question directly. A
  same-session, same-conditions control test was run on `view-mode=0` (SurfaceView — the code's own
  "robust fallback path," per its comments) immediately after: it failed identically
  (`Both codec types failed...`) under the exact conditions where GLES had just failed, while SURFACE,
  TEXTURE, and GLES had *all* succeeded cleanly earlier in the very same session. The determining factor
  tracks with the codec's own post-relaunch warm-up timing, not which `IProjectionView` backend consumes
  the output.
- **Not simply "high system load."** A background camera/DMS service (`cameraserver`, `com.zqc.camera`)
  runs continuously on this rig and was initially suspected (load average ~13–20 throughout). A head-unit
  reboot did not change this baseline, and video was observed running perfectly cleanly at 51fps for
  extended stretches under presumably the same ambient conditions (see `current_state.png`, 22:35:07 —
  Google Maps + Spotify both live and smooth). This rules out a simple "the rig is generally overloaded"
  story; the CPU/camera-load angle is not the explanation and is not carried forward as one.

**What actually determines success vs. failure was not fully isolated in this round.** The two
confirmed mechanisms above (tight stall threshold, non-clearing failure latch) fully explain why a
stall *cascades into permanence* once it starts, and are unambiguously code, not hardware. What decides
whether the *very first* post-relaunch stall happens at all — inherent SoC/driver jitter in
first-frame latency after a mid-stream MediaCodec reconfigure, most likely — is not something this
round's tooling could isolate further, and is flagged as open rather than guessed at.

## R1 — round 2 reproduction, on the fix

**One sentence: yes, the round 2 reproduction survives the fix — with audio off, the picture always
returned and stayed; the only deviation from a clean pass is the pre-existing stall/restart cycle
described above, which resolved on its own every clean-audio attempt.**

First run (no audio, TEXTURE, 20:34:43): `Decoder stopped: New surface` fired (new instance claimed the
decoder correctly); no `stale surface - ignoring` line was needed because the old instance's `onDestroy`
(deregistering its own callback) ran before any `onSurfaceDestroyed` could fire on the old surface — the
documented "ordering made it unnecessary" exception. One stall-restart cycle (`no output for 2007ms`,
`Forcing restart (1/4)`), first frame at 20:34:51.337 (~7.8 s after the true relaunch time), then
sustained 45–56 fps for the full 90 s window. **No `Media Sink Stop Request: VIDEO` at any point.**

A second run with `enable-audio-sink=true` and audio actively streaming hit the new permanent-failure
finding above (twice, reproducibly) instead of completing cleanly — recorded under the finding, not as
this branch's own result, since a third occurrence reproduced with audio off (ruling out audio as
causal) and the mechanism is pre-existing.

## R2 — rapid repeat (8×, ~5 s apart)

**PASS.** All 8 triggers handled correctly: each showed `Active session detected` →
`New surface set` → `Decoder stopped: New surface`, the same one-cycle stall/restart pattern as R1, and
recovered every time. **Zero `Error feeding input buffer` after any `Decoder stopped:` line** — the
race-hardening check this run exists for. No `Media Sink Stop Request: VIDEO`. Throughput settled back
to 42–51 fps within the 60 s window after the 8th trigger.

## R3 — SURFACE and GLES backends

| Backend | Trigger # | Stale-ignoring line fired? | Sink stop seen? | Picture stable at end? |
|---|---|---|---|---|
| SURFACE (early, clean session) | 1 | **Yes** — `SurfaceCallback: onSurfaceDestroyed for a stale surface - ignoring.` + `Decoder stop (surfaceDestroyed) skipped: surface is no longer current` (twice) | No | Yes, but at reduced ~20fps baseline (see below) |
| GLES | 4 attempts (2 with audio, 2 without, one post-reboot) | N/A — never reached; stall cascade every time | No | **No — hit the new finding all 4 times** |
| SURFACE (control, same conditions as failing GLES) | 1 | N/A — never reached | No | **No — also hit the new finding**, confirming R3-GLES's failures are not backend-specific |

The one clean SURFACE run is a genuine, direct confirmation the fix's ownership gate works when
actually exercised — this is the run that produced the `stale surface - ignoring` line, not just an
absence of `Media Sink Stop Request: VIDEO`. Its throughput dropped from the session's earlier ~50 fps
to ~20 fps with no trigger involved partway through, matching the general timing pattern noted for the
decoder-failure finding; still zero dropped/skipped frames, so this reads as reduced headroom rather
than an error.

**R3-GLES could not get clean signal in this round** — every attempt collided with the
permanent-failure finding above before the stale-surface mechanism itself could be assessed pass/fail.
Given the control test proves this isn't GLES-specific, R3-GLES's actual target behavior remains
unverified rather than failed; retry once the intermittent stall-cascade condition can be
avoided or worked around.

## R4 — ordering variant (background first, then relaunch by icon)

**Both attempts hit the new permanent-failure finding**, not the stale-surface mechanism being tested.
First attempt: an initial `Too many consecutive exceptions in output thread` restart, then the usual
stall cascade, self-recovered after ~2 minutes (first frame at 22:39:40, after starting at 22:37:46) —
then a second relaunch fired moments later (as part of the same test's later Home-cycle) and this time
ran to `Both codec types failed...`. Second attempt: same signature end-to-end,
`Both codec types failed` at 22:27:52 confirmed via a fresh-session retry.

`gain=true`-vs-watchdog routing (the actual thing R4 exists to check) never got to matter — the picture
never returned by either route on either attempt. Not evidence against the branch specifically (see
finding above), but genuinely unverified.

## R5 — watchdog proof

**PASS**, via a different mechanism than the brief's original plan. Phone screen-off did **not**
produce a video gap — Android Auto kept streaming through 18 s of phone screen-off at a steady 46–55
fps, so that technique doesn't work on this pairing. A brief phone-side WiFi disable did produce the
needed signal, though more disruptively than intended (it tore down the whole P2P session rather than
just pausing the stream):

- `Showing reconnecting overlay` fired at 22:36:55.524, ~9 s after WiFi went down.
- `AapProjectionActivity: connected but no frames - requesting video focus (unsolicited)` fired three
  times at 22:36:55.530 / 57.534 / 59.540 — **~2 s spacing, confirming the throttle**, not a tight
  per-tick loop.
- The full session then dropped and re-established automatically; `hideReconnectingOverlay — frames
  resumed` fired cleanly once the new handshake completed and the first frame rendered.

This confirms the reconnecting watchdog (`b7aa150d`, "keep the recovery watchdog alive for the whole
session") is doing its job — it would have been impossible to observe pre-branch, per round 2.

## R6 — regression guard

**Background/foreground half: could not get a clean baseline this round** — both attempted Home-press
cycle sequences (3 s/30 s/90 s holds, returning via the same relaunch trigger used throughout this
investigation) collided with the permanent-failure finding rather than producing a comparison against
round 1's clean baseline. Not evidence of a regression from this branch (the mechanism is shared with
every other run in this round, see the finding above), but the comparison itself is unverified.

**Link-stall catch-up half: INCONCLUSIVE.** No throughput line in this entire round ever showed
`skipped` > 0 — the tripwire metric this half exists to check never had a chance to fire. Both a 17 s
and a 4 s phone-side WiFi disable produced a full P2P disconnect/reconnect cycle rather than a brief
in-session stall the feed thread could visibly catch up from; no scriptable technique available this
round could produce a shorter, non-destructive link interruption. This needs either a different
provocation method (physical RF obstruction, per the original brief's suggestion) or a rig where a
sub-disconnect-threshold WiFi blip is achievable.

## R7 — idle-stream log hygiene

**PASS.** Fresh session, `fake_speed=false`, left completely untouched from 22:46:51 to 22:49:51 (3
full minutes). **Zero `restart suppressed (` lines** in the window; throughput held at a steady 49–50
fps the entire time. Confirms `1046a484` ("stop reporting an idle stream as a suppressed decoder
stall") is doing what it says.

## Do not re-run

- R1's clean-audio, no-trigger baseline — already the reference point above.
- Phone-screen-off as a technique for provoking a video gap — confirmed inert on this phone/AA pairing,
  don't retry it.
- A 4–17 s phone-side WiFi disable as a technique for a *brief* link stall — confirmed to always cause a
  full P2P teardown on this rig, not a stall; don't retry without a different method.

## Report back

1. **R1: yes, survives the fix** — clean with audio off (single stall-restart cycle, ~7.8s to first
   frame, then stable); the audio-active attempts hit the separate finding below instead, and a
   no-audio repeat of that same trigger later also hit it, ruling out audio as the determining factor.
2. **R2/R3 table:** see above. GLES could not get clean signal (see finding); SURFACE confirmed the
   fix directly (stale-ignoring line fired) in its one clean run, then also hit the finding in a later
   control test under different session conditions.
3. **R5:** watchdog and throttle both confirmed, via a phone-side WiFi-disable-driven full
   reconnect rather than the originally planned brief gap — see mechanism note above.
4. **R6:** background/foreground comparison unverified (collided with the finding both attempts);
   link-stall catch-up INCONCLUSIVE, `skipped` metric never exercised all round.
5. **R7: 0** `restart suppressed (` lines over a clean 3-minute window.
6. **Audio:** confirmed genuinely active (`AapAudio: AA audio started (AUDIO)` after
   `Media Start Request AUDIO`, continuous frame delivery) once `enable-audio-sink` was corrected from
   its leftover `false` value — but not confirmed audible, since this rig has no speaker. Ruled out as
   the cause of the permanent-decoder-failure finding via a no-audio reproduction.
7. **Anything noticed in passing:** `enable-audio-sink=false` silently blocks the AUD/AU1 service
   advertisement entirely (`ServiceDiscoveryResponse.kt:158`) — worth a settings-screen warning or at
   minimum a log line, since the symptom (`Media Sink Setup Request` on AUDIO2 only, nothing else ever)
   looks identical to the rig-flakiness round 2 attributed it to, and cost real time to distinguish.
