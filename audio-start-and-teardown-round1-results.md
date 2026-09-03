# audio-start-and-teardown — round 1 results

**Candidate:** `fork/fix/audio-start-and-teardown` @ `3d3fe465`       **Baseline:** `origin/main` @ `ea7aa7e0`
**APK md5:** `51af1553d422f03f7c45ea65ca33cb30` (candidate) / `5a5a16bc00ab5539dbb9cb145f07cd40` (baseline)
**Unit:** UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14, Native AA (`wifi-connection-mode=3`)
**Date:** 2026-08-25

## Setup notes

**This rig has no speaker or 3.5mm audio output.** Every "audio was audible" / "confirm audio audible"
corroboration line in the brief (R1, R3, R7, R8) could not be checked by ear on any run. Per
`TESTING-TEMPLATE.md`'s own rule that a verdict must never depend on a human being present, every
verdict below is scored on scriptable log/count evidence only. The phone's own Spotify playback state
(`dumpsys media_session`) and the app's own `Media Start Request AUDIO` / `AapMediaPlayback` status
lines were used as the scriptable proxy for "music is actually playing" wherever the brief asked for
audible corroboration.

**`hur-wifi-test-scripts/` inventory**: `build_hur.sh`, `run_unit_tests.sh` used for R0. `set_hu_prefs.sh`
used for all multi-key settings writes (rooted `adb shell`, not the `run-as` template — matches this
rig's documented setup). No new script needed this round.

**`settings.xml` delta at round start** (diffed against a fresh backup, `settings-backup-original.xml`):
`log-level` 2→1, `audio-latency-multiplier` 2→8, `audio-queue-capacity` 20→50, `log-source` (absent)→0.
`static-audio-focus` and `wifi-connection-mode` already matched the round's required values. Original
settings restored at the end of the round (confirmed: `log-level=2`, `audio-latency-multiplier=2`).

**Buffer arithmetic, confirmed on-device** (both arms, `audio-latency-multiplier=8`):
`AudioDecoder.start: channel=6, ... latencyMultiplier=8, queueCapacity=50` /
`Audio stream: 3 buffer size: 123136 (min: 15392) sampleRateInHz: 48000 channelCount: 2`.
At `audio-latency-multiplier=1`: buffer size 15392 (= min).

**Getting music to play required real methodology work, documented here for the next round.**
`KEYCODE_MEDIA_PLAY_PAUSE` sent via `adb -s <phone> shell input keyevent` and via the head unit's
`CommManager` relay (`adb -s <hu> shell input keyevent 85`) **did not** reliably reopen a closed AAP
audio channel or resume Spotify's own system media session on this phone — matches
`TESTING-TEMPLATE.md` §7a's existing note but is stronger here: even a genuinely fresh Spotify relaunch
did not reopen the channel once closed. What **did** work reliably: the Android Auto media widget is
rendered as part of the projected video itself (not a native view), so `adb -s <hu> shell input tap 272
657` (screen coordinates for this rig's 1440×720 layout, the widget's play/pause toggle) reliably
toggles playback and reopens the AAP audio channel every time, forwarded over the app's own touch
channel. This is the mechanism R1's and R2's ten/one media starts were produced with, in place of the
brief's literal `KEYCODE_MEDIA_PLAY_PAUSE` recipe. **One real, reproducible finding surfaced by this
work**: tapping Play once from a cold session produces a rapid `PLAYING`→`PAUSED` toggle loop for
several seconds (`AapMediaPlayback` status flapping every ~200-800ms, `MediaSession: Processing
transport control action = KEYCODE_MEDIA_PAUSE` firing repeatedly) before settling — reproduced live
with the operator watching, matches "it auto pauses immediately" exactly. Not scored as a round
finding since it wasn't in the brief's scope, but worth a look; it delayed R1's arm-A capture by two
discarded attempts (archived: `r1-a-attempt1-keys-only-no-relaunch.txt`,
`r1-a-attempt2-messy-discovery.txt`).

**R5 needed the manual-poke intent** (`ACTION_NATIVE_AA_POKE`), phone MAC `DC:B7:2E:5E:4E:59`, exactly
as the brief specified.

**The scripted Assistant trigger (`CarKeyReceiver` broadcast, `com.microntek.irkeyDown`/`irkeyUp`,
keyCode 84) did not work on this build** — attempted for R7's "one Assistant session" sub-step.
`AapProjectionActivity` was confirmed resumed and `key-codes` was confirmed absent from
`settings.xml`, both of the documented preconditions. The broadcast was enqueued by
`ActivityManager` (confirmed in logcat) but never reached the app: no
`CarKeyReceiver: Handling intent action:` line ever appeared, on three attempts. `CarKeyReceiver` has
no static `<receiver>` entry in `AndroidManifest.xml` — it is registered dynamically at runtime
(`CarKeysManager.registerReceivers`), which may not be reliably targetable by an explicit
component-name broadcast the way the (currently unvalidated) recipe assumes. **This blocks
`mic-uplink-round1-brief.md`'s M1**, which is described as depending on this exact trigger and is
that round's own point-of-the-round run — worth resolving before that round is attempted.

## R0 — Gate

**PASS**

- Baseline: first-ever run at this exact SHA in this session, clean compile, **765 tests, 0 failures**,
  `LinkGapMonitorTest` = 17 (matches expected).
- Candidate: clean compile, **782 tests, 0 failures** (765 + 17, matching the three new suites exactly),
  `AudioPrerollPolicyTest` = 12, `NativeGroupBringUpPolicyTest` = 4, `LinkGapMonitorTest` = 18. All
  match the brief's expected counts exactly.

## R1 — The point of the round: does a media start still underrun?

**INCONCLUSIVE (pre-registered fallback, not a FAIL)**

- Baseline: **10/10** `Media Start Request AUDIO` events produced (via the tap-toggle method above).
  **Zero** `disabled due to previous underrun` lines anywhere in either arm's capture — broader
  `underrun` (case-insensitive) also zero on both arms, despite `AudioFlinger` itself being present and
  logging 80 other lines in the baseline capture. This rig's AudioFlinger does not emit the underrun
  signal this A/B depends on; the efficacy question is unanswerable here, exactly as the brief's own
  fallback anticipated.
- Candidate: **6/10** taps produced a `Media Start Request AUDIO` (the toggle-button method naturally
  alternates play/pause from a running start, unlike baseline's session which happened to close fully
  between every tap — see Setup notes). Zero underruns here either, consistent with the rig limitation
  above, not a candidate result.
- **Strong secondary evidence, both directions:**
  - Baseline crashed with the exact predicted signature, twice independently (once in the archived
    discovery capture, once in the official capture): `AudioTrackWrapper.cleanup | Error during audio
    track cleanup` / `java.lang.InterruptedException` at `AudioTrackWrapper.kt:519`, called from
    `AudioTrackWrapper.run` at `.kt:267` — `cleanup()`'s sleep interrupted by a channel restart, exactly
    as the brief's mechanism describes.
  - Candidate's pre-roll fired cleanly on **every single one** of its 6 starts:
    `AudioTrackWrapper: playback started with 8192 frames banked (target 9600) after Xms`, X ranging
    4-7ms. Zero `Error during audio track cleanup` anywhere in the candidate capture (that string does
    not exist in candidate source, confirming the correct APK was live throughout).

## R2 — The pre-roll scales with the buffer, and never exceeds it

**PASS**

Candidate only, both checks against the actual `AudioPrerollPolicy` source (frames-banked in the log
line is the pre-write count per `AudioTrackWrapper.kt:289-303`; `shouldStart` gates on
`framesBanked + framesIncoming >= target`, so the logged banked figure is always at or slightly under
target by design when the triggering write is what pushes it over — confirmed against source, not a
defect).

| Check | R2a (multiplier=8) | R2b (multiplier=1) | Result |
|---|---|---|---|
| target below capacity | target 9600 ≤ ¾×30784=23088 | target 2886 ≤ ¾×3848=2886 (exactly the cap) | PASS both |
| target scales | — | 2886 < 9600, capped by ¾ as predicted | PASS |
| pre-roll happened | banked 8192 (pre-write; total incl. incoming write reached target) | banked 2048 (same) | PASS |
| cost bounded | 4-7ms | 1ms | PASS both, ≪300ms |

`Audio stream:` buffer size at multiplier=1: 15392 bytes (= the device minimum). Zero `Audio queue is
full` in either run. No 16kHz (AUDIO1/AUDIO2) channel activity occurred naturally in any run this
round; not scored, per the brief's own routing note.

## R3 — Teardown ends when the audio does

**PASS.** Free from R1's own captures (10 baseline teardowns, 5 candidate teardowns, all clean sessions).

| Arm | n | Mean | Max | Notes |
|---|---|---|---|---|
| Baseline | 9 clean + 1 crashed | **2562ms** | 2654ms | Individual: 2535, 2581, 2654, 2584, 2538, 2529, 2576, 2533, 2532ms. Matches reporter's ~2500ms. The 10th teardown was the R1 crash (291ms) — short exactly because `release()` ran immediately after the interrupted sleep, truncating the tail, as the brief's mechanism predicts. |
| Candidate | 5 | **379ms** | 629ms | Individual: 375, 292, 300, 629, 297ms. All ≪1100ms threshold. |

Candidate: **zero** `Error during audio track cleanup` (confirmed absent from source). Three
`drain cut short by a restart` INFO lines, all `338ms` — the interrupted-by-restart path firing cleanly
at INFO with no stack trace, as designed.

## R5 — One group per bring-up request

**A real, consistent finding — not the brief's predicted clean PASS.** 5/5 attempts per arm, gap
between the two racing commands 164-190ms (matching the reporter's ~175ms) on every attempt.

| Arm | Attempt | `createGroup SUCCESS` | `BUSY` | credential deliveries | session formed in 90s |
|---|---|---|---|---|---|
| Baseline | 1 | 2 | 4 | 6 | No |
| Baseline | 2 | 3 | — | 9 | No |
| Baseline | 3 | 3 | — | 9 | No |
| Baseline | 4 | 2 | — | 6 | No |
| Baseline | 5 | 2 | — | 6 | No |
| Candidate | 1 | 2 | 3 | 5 | No |
| Candidate | 2 | 2 | 3 | 5 | No |
| Candidate | 3 | 2 | 3 | 5 | No |
| Candidate | 4 | 2 | — | 5 | No |
| Candidate | 5 | 2 | — | 5 | No |

**Neither arm ever forms a session within 90s when the poke races the service's own bring-up at
launch.** The candidate is measurably more bounded and deterministic than baseline (always exactly 2
groups / 5 deliveries vs. baseline's variable 2-3 groups / 6-9 deliveries), and the *mechanism* of the
race is genuinely different: baseline's two `createGroup` calls collide within **11ms** of each other
(a real simultaneous race, immediate `BUSY`, the standard retry ladder engages 1-2 times before a
second group succeeds ~2-4s later); the candidate's `finishNativeBringUp` correctly folds the second,
overlapping call (`WifiDirectManager: a group refresh was folded into the last bring-up (5GHz
createGroup succeeded) - running it now.`) so there is **no simultaneous collision** — but per
`WifiDirectManager.kt:1211-1212`, the folded request still calls `startNativeAaQuietHost()` again once
the first finishes, which tears down and rebuilds the group (`Removing old group if any...`) roughly
2.5s later, hitting one incidental `BUSY` on its own `removeGroup`-before-recreate step in every
attempt that shows the extra `BUSY`s. **This is exactly the "fold with no session forming afterwards"
failure mode the brief itself flags as mattering more than the raw counts** — verified directly:
`Handshake: SSL handshake complete` and `keyframe reached the codec` never appear in any of the 10
captures (5 per arm) within the 90s window, on either arm.

Per the brief's literal PASS bar ("exactly one `createGroup SUCCESS` per attempt") this is a **FAIL**
on the candidate. Per the brief's own softer, explicitly-weighted criterion, it is a real, partial
improvement (bounded churn, no collision) that does not fix the underlying symptom (no session forms).
Report both readings; this needs a design decision, not just a retest.

## R6 — The guard does not latch

**FAIL.**

- **Cycle 1: PASS.** Full session formed cleanly (single `createGroup SUCCESS`, clean handshake,
  first frame ~28.6s after launch, video steady ~47-52fps).
- **Cycle 2: wedged for 9+ minutes, never formed a session in the original process.** Sequence:
  scripted `headunit://disconnect` at 01:11:37 (clean teardown confirmed: `WifiDirectManager: Final
  group removal success`, port 5288 released, decoder stopped). The documented reconnect lever (cycle
  the phone's Bluetooth) did not bring the link back after two full cycles and ~70s of waiting — a
  known rig quirk on its own — so the manual-poke intent (`ACTION_NATIVE_AA_POKE`) was used to force
  progress, per house rules' scripted-lever-of-last-resort. This is itself informative: it produced the
  **same fold-then-rebuild pattern found in R5** (2× `createGroup SUCCESS`, 2.5s apart). From there,
  the Bluetooth-side handshake **repeated in full every ~43 seconds** (`Handshake completed
  successfully on Bluetooth side.` fired at least twice, full version-exchange sequence each time) while
  the WiFi side never associated — the documented "stuck on Obtaining IP address" symptom. Underneath
  that, `WifiDirectManager.nativeJoinWatchdog` fired on its own 60s cycle **four consecutive times**
  (01:16:08, 01:17:08, 01:18:08, 01:19:08) and logged `Native AA join watchdog fired but a Bluetooth
  handshake or handoff is in flight — deferring recovery.` every time — the watchdog's own liveness
  check treats a *repeating* handshake as equivalent to a *progressing* one and never escalates.
  A force-stop + relaunch at 01:19:59 recovered a full session within ~14s (group at 01:20:09,
  handshake complete 01:20:23) — confirming recovery is possible, but **only** via a full process
  restart, which is precisely the brief's own stated FAIL condition.
- Cycles 3-5 not attempted once the FAIL condition (cycle 2 needing a force-stop) was already met;
  continuing in a freshly force-stopped process would no longer be "one process, no force-stop between
  them," the exact thing R6 exists to test.

Full capture: `r6.txt` (this file, not committed — see note at end).

## R7 — A paused sink is not an outage

**PASS**, with one sub-step unconfirmed (see Setup notes: scripted Assistant trigger did not work).

- 12-minute session, fresh clean single-group start. Music paused 3 min (tap-toggle method), resumed,
  Assistant trigger attempted and failed to fire (see above, not scored), resumed, 4 tracks skipped via
  `KEYCODE_MEDIA_NEXT` at ~1s intervals through the head unit.
- **Zero** `inbound audio quiet` across the whole session.
- Paired proof of reachability: **zero** `inbound video quiet`, **zero** `inbound link quiet` — both
  series stayed live and reporting throughout, so the zero-audio-quiet result is not an artifact of a
  generally-silent session. Video throughput: mostly 48-55fps, dipping to a 20-33fps cluster during the
  pause/resume/skip actions themselves but never zero. Single `createGroup SUCCESS` for the whole
  12-minute window (clean, no discard-rule hit).
- The "does a *later* real outage still register" half is INCONCLUSIVE by construction, exactly as the
  brief pre-registered — no way to force one on this rig; covered by `LinkGapMonitorTest`.

## R8 — Clean control

**PASS.** 10-minute uninterrupted session, nothing touched (music was already auto-playing at session
start this time, no tap needed).

```
disabled due to previous underrun:      0
Audio queue is full:                    0
inbound audio quiet:                    0
drain cut short by a restart:           0
ignoring a superseded bring-up outcome: 0
createGroup SUCCESS:                    1
```

Audio continuous for the full window: **660** consecutive `AapMediaPlayback` `state=PLAYING` status
packets (~1/second for the full 660s), **zero** `Media Sink Stop Request: AUDIO`, one
`Media Start Request AUDIO` for the entire session (no rebuilds). Video throughput steady, mostly
47-55fps with a brief 27-32fps cluster early on, never zero.

## Anything the brief did not ask about

- **The Native AA join watchdog's liveness check is fooled by a repeating-but-never-completing
  handshake** (R6). This looks like a real, separate defect from the group-race issue in R5, though
  the two interact: R6's wedge only happened after the manual-poke fallback introduced the same
  fold-then-rebuild churn R5 measures. Worth its own look independent of whether R5's fold behavior
  changes.
- **Getting real audio playback on this rig at all needs the AA media widget's on-screen tap
  (`input tap 272 657` on this rig's 1440×720 layout), not media keys.** Media keys (both
  phone-targeted and head-unit-relayed) reliably failed to open or resume a channel in every attempt
  made this round. This should be folded into `TESTING-TEMPLATE.md` §7a for future audio rounds — it
  cost real time twice this round.
- **The scripted Assistant/`CarKeyReceiver` trigger is unvalidated and did not work** — see Setup
  notes. Flagging clearly since `mic-uplink-round1-brief.md`'s M1 (its own point-of-the-round run)
  depends on it.
- This rig has no audio output hardware at all (no speaker, no 3.5mm) — every future audio-focused
  brief for this channel should route "confirm audible" checks to a scriptable proxy up front rather
  than assuming a listener is available.

## Capture files

All captures are local to this session's `hur-wifi-test-scripts/round-audio-start-and-teardown/`
directory (largest is `r7.txt` at ~164MB) and are **not** committed to this branch — consistent with
this channel's existing pattern of keeping raw captures out of git. Available on request: `r1-a.txt`,
`r1-a-attempt1-keys-only-no-relaunch.txt`, `r1-a-attempt2-messy-discovery.txt`, `r1-b.txt`,
`r1-b-discard-double-group.txt`, `r2b.txt`, `r5-a-1..5.txt`, `r5-b-1..5.txt`, `r6.txt`, `r7.txt`,
`r8.txt`. Two candidate/baseline APKs also retained: `arm-a-ea7aa7e0.apk` (md5
`5a5a16bc00ab5539dbb9cb145f07cd40`), `arm-b-3d3fe465.apk` (md5 `51af1553d422f03f7c45ea65ca33cb30`).
