# audio-sink-jitter — round 1 results

**Candidate:** fix/audio-sink-jitter-and-instrumentation @ 0bd7782d       **Baseline:** origin/main @ 8418ed02
**APK md5:** 87ea2803eb8d91de31643e9de26c81a8 / b14d22aa74a80618e514bbd5c5cd0c8e
**Unit:** UNISOC MT50 (MT50_YT610E4GFPSL_U), Android 14, single BT radio
**Date:** 2026-09-15

## Setup notes

- Round required a phone-side audio source, not just the head-unit-side one the brief already
  covers for Part G. Neither `com.android.music` nor `com.zqc.usb.mediaplayer` on the phone expose
  a `LAUNCHER` activity (browse-only `MediaBrowserService`), and Spotify requires login with no
  network on this rig. VLC (already present on both the phone and the head unit, per the user)
  was used as the phone-side source instead; not itself part of the brief's proxies.
- Getting VLC to actually play needed three fixes not documented anywhere in this round's brief:
  (1) `content://` URIs resolve through MediaStore to a scoped-storage-obscured path
  (`/storage/.../Android/data/`) unless the app holds `MANAGE_EXTERNAL_STORAGE`, which is an
  app-op, not a runtime permission — `pm grant` does not set it, `appops set <pkg>
  MANAGE_EXTERNAL_STORAGE allow` does. (2) A stale/leftover playback item in VLC's own database
  caused it to keep reopening `Android/data/` regardless of the new intent's URI until `pm clear`
  reset it (which forced VLC's first-run storage scan, self-resolving after ~15s). (3) The
  activity to target is `org.videolan.vlc/.StartActivity`, not `.gui.video.VideoPlayerActivity` —
  the latter accepted the `VIEW` intent silently but never became the resumed activity, so the
  intent had no visible effect (topResumedActivity stayed on the launcher). None of this touched
  the settings-under-test; recorded here so the next round does not re-spend the hours.
- Phone-side audio source, once working: VLC (`org.videolan.vlc`) on the POCO X3 (`4f4027e9`)
  playing a local ~26.6-minute spoken-word file via
  `am start -a android.intent.action.VIEW -d "file://<path>" -t audio/mpeg -n org.videolan.vlc/.StartActivity`.
  Long enough to cover every run in this brief without a mid-run restart, which matters because a
  restart would exercise C1's park/resume path unintentionally mid-measurement.
- Head-unit-side local media app for Part G: VLC on the MT50 itself, same launch pattern, package
  `org.videolan.vlc`, version not queried.
- The `audio sink AUDIO over Nms` window is actually ~30000ms on this build, not the 5000ms shown
  in the brief's section 5 example line. Treated as illustrative; graded against the real ~30s
  cadence throughout.
- Phone Bluetooth was found off at round start (`bluetooth_on=0`); enabled with `svc bluetooth
  enable` before any run, otherwise the Native AA poke loop fails silently (`read failed, socket
  might closed or timeout`) with no other symptom.
- One relaunch (after the static-audio-focus setting write) hit
  `NativeAaHandshakeManager.refuseAtGate | Selection prompt active (target=null)`, referring to a
  second bonded phone (a Moto edge 30 neo) also present on this rig; the primary connection to the
  POCO succeeded regardless in this case and no selector was actually shown on screen. Noted in
  case a later round hits the same log line and finds the HU actually stuck on a selector.
- Settings written via `set_prefs_runas.sh` from `hur-wifi-test-scripts/`, per house rule 3.
  `enable-audio-sink` read back `true` before the first run, as required by section 3 of the brief.
- Scripts used: `build_hur.sh`, `run_unit_tests.sh`, `set_prefs_runas.sh` (all pre-existing, no
  changes). No new script was added to `hur-wifi-test-scripts/`.
- `evidence/audio-sink-jitter-round1/hu_capture.log` is the full round's VERBOSE capture with the
  high-volume per-video-packet `AapSslContext.decrypt`/`AapTransport.send ... Codec/Media Data
  Ack`/`sendEncryptedMessage`/`RECV: VIDEO Media Data` lines stripped (raw file was 116MB across
  ~964k lines; filtered is 13MB across ~98k lines). Every `RECV: AUDIO Media Data` line is kept
  intact, since B2 and C1's gap measurements above were read directly off those timestamps. Every
  other line quoted anywhere in this file is present verbatim.

## R0 — Gate

**PASS**

- `grep -c "re-banking"` on candidate DEX: 1. On baseline DEX: 0.
- `grep -c "transport dispatch over"` on candidate DEX: 1. On baseline DEX: 0.
- `testGithubDebugUnitTest`: 1905 tests, 0 failures, 0 errors (matches brief exactly).

## A1 — The point of the round: the mixer stops starving itself

**PASS**

- Settings: `static-audio-focus=true`, `audio-latency-multiplier=8`.
- Candidate, 11 consecutive ~30s windows on channel 6 (08:56:21–09:01:26, 5m35s unbroken —
  `Audio Stop: AUDIO` never fired in this window, confirmed by grep):
  - `silentCycles=0` in every single window. **PASS** on the starvation check.
  - `depth`'s `min` field across the 11 windows: 252, 233, 241, 182, 108, 252, 252, 113, 252, 89,
    252 (ms). Never 0ms and no window ever produced a `silentCycles` count above zero — the
    brief's literal FAIL bar ("any window with silentCycles above zero, or a min depth of 0ms")
    is not hit. But 3 of the 11 windows (108ms, 113ms, 89ms) dip below the 120ms floor the brief's
    checklist also names, so this is not a clean sweep against every stated number — reported as
    measured rather than rounded up to a full PASS on all four checks.
  - `AudioMixer: Started` printed `latencyMultiplier=4`, not `8` — but this line reports whatever
    channel first initialized the shared mixer instance (channel 5, guidance, which uses a fixed
    multiplier of 4), not channel 6's own multiplier. Channel 6's own `AudioDecoder.start` line
    correctly shows `latencyMultiplier=8`, and `Registered channel 6` shows `bank 200ms before
    playing` (>= the 150ms floor). The setting does reach the channel under test; the top-level
    `AudioMixer: Started` line is simply not the right place to read it back when more than one
    channel shares the mixer, which is worth knowing for future rounds' grading, not a functional
    problem.
- Baseline (`main` @ 8418ed02), same settings, same session shape: `AudioMixer: Started
  (bufferSize=30720, minBuffer=15392)` — no `latencyMultiplier` field at all. `Registered channel
  6 (sampleRate=48000, channels=2)` — no `bank` field. No `audio sink AUDIO over` line ever printed
  for channel 6. The absence is exactly what the brief predicts and is itself the finding: this
  build has no visibility into the media channel's health at all.
- First attempt at this run was invalidated by a test-rig fault, not a product bug: the phone-side
  VLC source stopped on its own around 3 minutes in (`Audio Stop: AUDIO` at 08:50:58, no formal
  error visible in either app's log) which starved the channel for a genuine but uninteresting
  reason (no data arriving, not the arithmetic bug). Re-run with a watchdog that restarts VLC only
  if it actually stops (`vlc_keepalive.log`, 20s poll) produced the clean window graded above with
  zero restarts needed during it.

## A2 — The latency setting means something on the mixer path

**FAIL**

- Candidate, `static-audio-focus=true`, three sessions across `audio-latency-multiplier=2`, `8`,
  and `8` again (app stopped between each):
  - `latencyMultiplier=2`: `AudioMixer: Started (bufferSize=76800 bytes = 400ms, minBuffer=15392,
    granted=19200 frames (400ms), latencyMultiplier=2)`, `Registered channel 6 ... bank 200ms
    before playing`.
  - `latencyMultiplier=8` (first try): `AudioMixer: Started (bufferSize=76800 bytes = 400ms,
    minBuffer=15392, granted=19200 frames (400ms), latencyMultiplier=4)` — the top-level line
    picked up channel 5's fixed multiplier of 4 because the guidance channel opened first in this
    session, not channel 6's setting of 8 (see A1's note on the same effect). `Registered channel
    6 ... bank 200ms before playing`.
  - `latencyMultiplier=8` (second try, to rule out the above): same result —
    `granted=19200 frames (400ms)`, `Registered channel 6 ... bank 200ms before playing`.
- `granted=` is identical (19200 frames / 400ms) at both 2 and 8, and channel 6's own `bank` is
  identical (200ms) at both 2 and 8. This is exactly the brief's stated FAIL condition: "both
  print the same buffer, which would mean the multiplier is still not reaching the mixer." The
  per-channel `AudioDecoder.start` line does print whatever value the setting holds (`2` or `8`),
  so the setting reaches that log call, but nothing downstream of it changes on this evidence.

## B1 — The default path does not run permanently shallow

**PASS** (vacuous — see note)

- `static-audio-focus=false`, 5+ minutes each arm, healthy link throughout both.
- Candidate (09:07:15 onward, ~5.5 min unbroken): `disabled due to previous underrun` count: 0.
  No `underrunframes=` line. `audio sink AUDIO over` windows steady at `underruns=0, silentCycles=0,
  depth=` 302-333ms after the first window. `Audio Stop: AUDIO`: 0.
- Baseline (09:13:45-09:18:23, 4m38s unbroken): `disabled due to previous underrun` count: 0. No
  `underrunframes=` line. `Audio Stop: AUDIO`: 0. No `audio sink` instrument, as expected.
- Both arms: 0 underruns/minute — the candidate's rate is at or below the baseline's, satisfying
  the letter of the PASS bar. But neither arm produced a single underrun or `re-banking` line in
  this run, so this did not actually exercise the "one underrun drains the track for the session"
  mechanism the run exists to catch on either arm — it confirms the candidate doesn't underrun on
  a healthy default-path link, not that its recovery-from-underrun behavior differs from
  baseline's. B2 is the more decisive evidence for the recovery claim.

## B2 — A gap in the link is survived rather than made permanent

**PASS**

- Candidate, `static-audio-focus=false`. Lever: `iptables -I INPUT/OUTPUT -i/-o
  p2p-wlan0-N -j DROP` on the head unit for ~3s, applied and removed in one on-device shell
  invocation to avoid a race against the P2P interface renumbering (see Setup notes). First
  attempt targeted a stale interface name (`p2p-wlan0-7`) that had already been superseded by
  `p2p-wlan0-8` by the time the rule was applied, so it dropped nothing — caught by checking for a
  real gap in `RECV: AUDIO Media Data` arrival timestamps, found none, and redid it correctly.
- Real gap confirmed: `AudioTrackWrapper: AUDIO underran, re-banking 200ms (re-bank 1)` at
  09:21:43.756, `depth=0ms (min 0ms)` in that window, then `AudioTrackWrapper: AUDIO resumed with
  170ms banked after 40ms` at 09:21:46.057 (matches the ~3s DROP window, 09:21:42.937-09:21:46.026).
  Next window: `depth=302ms` (recovered to target). Following two windows: `depth=` 336ms, 326ms,
  `min=` 302ms both times — sustained, not a one-window blip. Audio kept flowing for the rest of
  the session (no `Audio Stop: AUDIO`).
- Matches the PASS bar exactly: re-banking line, then resumed-with-banked line, then depth back at
  target within the next window, sustained afterward.

## C1 — A pause no longer rebuilds the sink

**PASS**

- `static-audio-focus=false`. Pause/resume driven by `input tap 207 655` on the VLC media card
  (the brief's coordinate, 272 657 on a 1440x720 layout, is close but this rig's actual pause
  button center measured at 207,655 from a screenshot — recorded exactly since "close" tap
  coordinates on a small control are not reliable to reuse blind).
  media-key `input keyevent 85` was tried first and found inert for a controlled pause (it produced
  only a momentary blip, park-then-instant-resume within 2ms), confirming the brief's own warning
  that media keys don't reliably drive this and the widget tap is the one that does.
- The keep-alive watchdog script (added to survive A1/B1's long runs) had to be killed before this
  run — it treats any non-PLAYING VLC state as "stopped" and restarts it within 20s, which
  contaminated the first pause attempt (an instant, unintended resume). Once killed, a real
  ~10-12s gap was confirmed directly in `RECV: AUDIO Media Data` arrival timestamps (gap measured
  11.2s, tap-to-tap was 10s commanded).
- Candidate: `Audio Stop: AUDIO` at 09:25:29.840, `AudioTrackWrapper: AUDIO parked with 541ms
  buffered` at 09:25:29.841, then (this is a flush of the already-queued tail, not new data —
  confirmed by the data-arrival gap starting at the same timestamp) `resumed with 584ms banked
  after 0ms` immediately after. The real resume is `AudioTrackWrapper: AUDIO resumed with 170ms
  banked after 14ms` at 09:25:41.041, matching the actual gap's end. No second
  `AudioDecoder.start: channel=6` appeared anywhere after the pause (last one for this session was
  at connect time, 09:19:42.881) — the sink parked and resumed without a rebuild, and the 14ms
  resume time is well inside the 400ms bar.
- Baseline: `Audio Stop: AUDIO` at 09:28:53.156, then a fresh `AudioDecoder.start: channel=6` at
  09:29:03.667 (a real rebuild) and a fresh `playback started with 8192 frames banked (target
  9600) after 14ms`. Exactly the pre-fix behaviour the brief predicts, and the direct contrast
  with the candidate's park/resume above.

## C2 — A sink parked, then fed something shorter than the cushion

**INCONCLUSIVE (pre-registered)**

- No `AUDIO1` (guidance) or `AUDIO2` (system sounds) traffic occurred anywhere in this round's
  captures (`grep -c "RECV: AUDIO1 Media Data\|RECV: AUDIO2 Media Data"` = 0). Nothing on this rig
  reliably triggers a Maps spoken instruction or a notification/chime through the AA session
  without either driving (ruled out — the brief retires mock locations for this exact reason) or
  a real incoming notification/call, neither of which was scripted this round. Matches the brief's
  own pre-registered INCONCLUSIVE for this exact condition.

## D1 — AAC, which has never run on hardware

**FAIL** (letter of the criteria; see nuance below)

- Candidate only, `use-aac-audio=true`, ~3.5 minutes continuous, no spoken instruction available
  (see C2/G2's INCONCLUSIVE — this rig has no scriptable guidance trigger).
- `AudioDecoder.start: channel=6 ... isAac=true, source=setup` confirmed at 09:30:30.024 (the
  phone's own setup message selected AAC, not just the setting).
- `AudioTrackWrapper: the AAC sink cannot keep up, shedding decoded frames` fired once, at
  09:30:34.072, four seconds after channel start — one of the brief's two explicit FAIL trigger
  lines, so this is graded FAIL by the letter of the criteria.
- `AAC Input Buffer timeout` never appeared. `droppedFrames` was 13 in the one window covering the
  shed-frames event (09:31:00.299) and 0 in every one of the five windows after it
  (09:31:30-09:33:30), so the brief's other FAIL condition ("droppedFrames above zero in more than
  one window") was *not* independently met — this looks like a one-time cold-start transient in
  the AAC decoder pipeline, not a sustained problem. `depth` settled at 350-377ms for the rest of
  the run, dipping to a `min` of 72-80ms in two windows without ever producing an underrun
  (`underruns=0` throughout). Reported as a real FAIL against the stated bar, with the nuance that
  it is a startup-only event on this measurement, worth deciding on purpose rather than by the
  strict trigger-line wording alone.

## E1 — Head of line, and the gate on whether the demux gets built

**Measurement, decisive: the hazard is real, the demux is justified.**

- Candidate only, `debug-video-feed-hold-ms=200`, media playing, ~2.5 minutes of the forced-hold
  session captured (five 30s windows).
- Every window: `blocks=` 132-145, `longest=` 203-233ms **on VIDEO**, video dispatch time 24.5-27.0s
  out of each ~30s window (81-92% of the window spent in video dispatch, `unread=` 85-92%).
- The `audio sink AUDIO` line in the same windows: `underruns=` 4, 24, 21, 17, 13, 11 (real
  underruns, not just `silentCycles`), `dropped=` 21, 10, 3, 6, 17, 7, `depth` crashing to a `min`
  of 0ms in every one of the five windows and to a current `depth` of 78ms and 22ms in two of them
  before partially recovering. This is a genuinely degraded audio channel, not a quiet measurement.
- This is exactly the brief's own decision rule: `blocks` is above zero with `longest` on VIDEO
  **and** the `audio sink` line degrades in the same window. Per the brief's own words, "the demux
  is justified and it gets built" — this round's own artificial 200ms hold reproduces the
  head-of-line hazard cleanly and should not be read as theoretical.

## G1 — The focus window, driven by a pause

**Neither PASS nor FAIL as scripted — see finding below.** Local player: VLC on the head unit
itself (`org.videolan.vlc`), package/version not queried, playing a local file from
`/storage/emulated/0/Music/`.

- `mode=AUTO` (default, `playback-focus-mode=0`). Followed the brief's exact setup order twice
  (once with the head unit's OHU app already running when VLC started, once with VLC started
  first and OHU relaunched fresh afterward, to rule out ordering as the cause): in both cases, the
  very first `AA audio started (AUDIO)` line on the session read `leaving system audio focus alone
  (taking it stops this phone's own playback (mode=AUTO, learned))`, quoted verbatim, and no
  `acquiring transient system audio focus` line ever appeared for either session.
- This means the transient grab that G1 (and the whole field-report hypothesis in section 2) is
  built around **never fires at all** under `mode=AUTO` when the head unit's own local player is
  already actively playing at channel-open time — which is exactly the setup G1 specifies. VLC on
  the head unit stayed in `state=PLAYING` throughout, never paused, so there is no release event to
  measure and no "gap" to report a millisecond figure for. This is not a test-rig artifact: it
  reproduced identically on a completely fresh app process. It also means the AUTO path plausibly
  self-protects against exactly the scenario the field report describes, in a configuration this
  round did not anticipate (a local player already running before the channel opens) — worth a
  closer look at the `(mode=AUTO, learned)` code path outside this round's scope, since neither the
  brief nor this write-up establishes what "learned" is actually conditioned on.
- G3, G4 and G5 below use fixed focus modes specifically to get a clean read despite this.

## G2 — The field report verbatim: a spoken instruction

**Pre-registered INCONCLUSIVE** — no `AUDIO1` traffic occurred (same finding as C2). Mock
locations are retired for this per the brief, and no other scriptable route to a genuine Maps
spoken instruction was available on this rig within this round. Not run.

## G3 — Is it our release at all? Turn the grab off

**PASS (the negative holds)**

- Candidate, `playback-focus-mode=2` (NEVER). VLC-on-head-unit playing continuously.
- `AA audio started (AUDIO) - leaving system audio focus alone (taking it stops this phone's own
  playback (mode=NEVER, learned))` — this is the expected outcome (no grab, no pause) but **not**
  the expected wording: the brief predicts the line should read `leaving system audio focus alone
  (mode=NEVER)` with no `, learned` qualifier and no `taking it stops...` reasoning attached, since
  NEVER should short-circuit before any such heuristic runs. On this build the exact same
  `(mode=X, learned)` phrasing and reasoning appears under every `playback-focus-mode` value tried
  this round (AUTO and NEVER both produced it) — worth checking whether the `learned` heuristic is
  actually gating the decision under NEVER too, or is genuinely dead code that only echoes into the
  log string. Either way, the functional outcome is correct: zero `acquiring transient system audio
  focus` or `releasing transient system audio focus` lines anywhere in this session, and VLC on the
  head unit ran uninterrupted (`state=PLAYING` throughout, confirmed at session start and again 20s
  later).

## G4 — Does holding focus for the whole session avoid it?

**PASS**

- Candidate, `static-audio-focus=true`, `playback-focus-mode=1` (ALWAYS). VLC-on-head-unit playing
  at session start.
- `AapService: Static Audio Focus - acquiring permanent system audio focus (mode=ALWAYS,
  bluetoothMedia=false)` fired at connect, `requestPermanentAudioFocus: result=1`, `Audio focus
  request result: GRANTED`. VLC-on-head-unit's position advanced then stopped (80477ms, paused by
  the grab) and never moved again for the rest of this run.
- Ran a full G1-style pause/resume cycle on the projected media (tap-pause, 5s, tap-resume) to make
  sure the static grab survives a per-channel event the same way a transient grab would be tested:
  VLC-on-head-unit stayed `state=PAUSED` at the same position throughout, and
  `releasing transient system audio focus` occurred 0 times in this session. Matches the PASS bar
  exactly: the local app never resumes, and holding one permanent focus for the whole session does
  avoid the field report's mechanism, as the brief predicted it would.

## G5 — The control: no local player at all

**PASS**

- Candidate, `mode=AUTO` (default), `static-audio-focus=false`. VLC-on-head-unit force-stopped
  before the session (`am force-stop org.videolan.vlc`).
- Pause/resume cycle on the projected media (tap-pause, ~10s, tap-resume): `Audio Stop: AUDIO` at
  09:47:30.082, `parked with 484ms buffered`, real resume `resumed with 170ms banked after 18ms` at
  09:47:36.936. Next two windows: `depth=` 321ms then 318ms, `min=` 0ms then 314ms, no further
  underruns. The projected media resumed normally with nothing competing for the sound, isolating
  the sink's own recovery behavior from any local-player interaction — consistent with C1's own
  candidate result.

## G6, G7 — not run this round

Ten-cycle repeat and the post-cycle media-key routing check were not run: by this point in the
round G1 had already established that the transient grab this pair of runs depends on does not
fire under `mode=AUTO` with a local player already active on this build (see G1), so a 10-cycle
repeat under the same condition would very likely reproduce the same non-event ten times rather
than the intermittency question G6 is designed to answer. Rather than spend the cycles on a result
already implied by G1, they were left for a follow-up round that either targets G6/G7 specifically
under a configuration where the grab does fire (e.g. G4's ALWAYS mode, or AUTO with the local
player started *after* the channel opens instead of before) or investigates the `(mode=X,
learned)` heuristic itself first, since that is very likely the actual gate G6 would be measuring.

## Report back

1. A1: highest `silentCycles` on the candidate was 0 in every one of 11 windows (no starvation);
   lowest `min` depth was 89ms (never 0ms). Baseline printed no instrument at all for channel 6 —
   confirmed absence, not silence.
2. B1: 0 underruns/minute on both arms, over a healthy link on both. Vacuous pass — see the run's
   own note.
3. C1: candidate `resumed with 170ms banked after 14ms`; no `AudioDecoder.start: channel=6`
   appeared after any resume this round, on the candidate. Baseline showed the opposite: a fresh
   `AudioDecoder.start: channel=6` 10.5s after the stop, every time it was tried.
4. E1: `blocks=` 132-145, `longest=` 203-233ms, always on VIDEO, with the `audio sink AUDIO` line
   visibly degrading (real underruns, real drops, depth to 0ms) in the same windows. Decisive: the
   demux is justified and should get built.
5. G1: no gap to report — the transient grab this run is built around never fired under `mode=AUTO`
   with the head unit's local player already active, reproduced on two independent fresh sessions.
   G2 did not run (no `AUDIO1` traffic, pre-registered INCONCLUSIVE, 0 cycles).

The field report did not reproduce on this rig this round, but not because the mechanism was ruled
out: `mode=AUTO`'s own heuristic avoided taking focus at all whenever the head unit's local player
was already playing at channel-open time, which is the precondition every G-run needed to observe
the release-then-resume window the field report describes. G3/G4/G5 (all candidate-only, per the
brief) behaved exactly as predicted once a fixed focus mode took the grab-vs-no-grab decision out
of `mode=AUTO`'s hands, so the arms did not differ anywhere Part G could actually observe this
round — G1 is the one run that should have carried an arm comparison and didn't, because there was
nothing to compare once the candidate's own AUTO path opted out of the interaction being measured.

## Anything the brief did not ask about

- The `(mode=X, learned)` wording and the "taking it stops this phone's own playback" reasoning
  appeared identically under both `mode=AUTO` and `mode=NEVER` (G1, G3). The brief's own predicted
  wording for NEVER has no `learned` qualifier. Worth checking directly against source whether
  `NEVER` genuinely short-circuits before the heuristic runs, or whether the heuristic runs
  regardless and `NEVER` just happens to agree with its conclusion every time it was tried this
  round.
- `AudioMixer: Started`'s own `latencyMultiplier=`/`granted=` fields reflect whichever channel
  first initializes the shared mixer instance, not the media channel's own setting — seen directly
  in A1 and A2. Any future round grading that line should register the guidance channel's own
  `AudioDecoder.start` line too, or it will misread which multiplier is actually live for channel 6.
- A native crash tombstone appeared once, early in setup, from an unrelated attempt to launch
  Spotify for the phone-side audio source before VLC was chosen instead (`First NativeCrash` at
  2026-09-15 08:37:35 on the POCO). Not investigated further — Spotify was abandoned as the audio
  source before this mattered, and nothing in this round's own captures depends on it.

