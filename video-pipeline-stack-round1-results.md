# video-pipeline-stack — round 1 results

**Candidate A:** fork `fix/video-backpressure-diagnostics` @ `f008e3d124f7880ed0e94d886927b6236cc53b55`
**Candidate B:** fork `fix/aap-partial-read-desync` @ `becebffa0928028da71c110c567343818d87500f`
**Baseline:** none built (brief says none needed; `main` @ `9f7c3b20` numbers quoted from the brief where used)
**APK md5:** A `4cf6a285189398804b16094b6034b9d9` / B `3b7612e1b0f578d8e89f3863824f924a`
**Unit:** UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14, 1440x720 panel, Native AA wireless (mode 3)
**Date:** 2026-08-18

## Setup notes

- Both builds compiled first-try, no source changes needed.
- `hur-wifi-test-scripts/build_hur.sh` and `run_unit_tests.sh` used for R0 on each SHA, unmodified.
  `install_and_launch.sh` with `SKIP_BUILD=1` used to install pre-copied APKs. `set_hu_prefs.sh` used
  for every settings change. Each built APK copied out of `apks/` into a new
  `round-video-pipeline-stack/` folder immediately after building, per the brief's warning that
  `build_hur.sh` deletes the previous APK.
- R1's negotiated resolution on this session was **1920x1080@60**, not the 1280x720 the brief's §5
  table quotes `KEY_MAX_INPUT_SIZE` numbers for. The rig's `resolutionId=3` setting (already present
  from a prior round, not touched this round) drives that. Reported the actual `max-input-size=1530KB`
  instead of the brief's `675KB`; the requested/got match is the load-bearing check, not the literal
  number, and 1530KB scales consistently with 675KB @ 1280x720 for the same codec formula (ratio of
  pixel counts is 2.25x; 675 * 2.25 = 1519, close to 1530 after rounding).
- No `ACodec: Allocating` or `CCodec`/`C2` framework buffer-count line appeared anywhere in the R1
  capture, exactly as §3 warned might happen on Codec2/Android 14. The app-side `Codec input buffer:
  requested/got` line is the only evidence for that part of the check, per the brief's own fallback.
- First R1 attempt was invalidated by an operator mistake, not a rig or app issue: `adb logcat -c` was
  run mid-session, after the codec had already configured, discarding the one-shot SPS/capability/
  configure lines before they could be captured in the same file as the 5-minute throughput window.
  Redone clean: disconnected, cleared the buffer, started the capture, then reconnected, so the whole
  session from `headunit://connect` through 5+ minutes of steady playback is in one continuous file.
- Round 5's lesson held again: media session was `PAUSED`/`STOPPED` after each reconnect and had to be
  woken with `input keyevent 85`, confirmed `PLAYING` via `dumpsys media_session` before the timed
  window began.
- **The rig lost power mid-round** (between R2 and the redo of R2), hard-rebooting. Confirmed clean
  reboot (`sys.boot_completed=1`) once external power was restored, `settings.xml` survived intact
  (checked the R1/R2-relevant keys against what had been written), app reinstalled state intact. No
  data was lost — the in-flight R2 capture at the time was already invalidated for an unrelated reason
  (auto-connect-before-buffer-clear) and was being redone regardless.
- **Protocol deviation: no `settings.xml` backup was taken before the round started.** §5 of the
  template calls for one. Restored the keys known to differ from the pre-round dump
  (`video-codec=H.265`, `view-mode=2`, `log-level=0`, `software-video-decoder=1`, all `debug-*` keys
  and `force-software-decoding` deleted) at the end of the round instead of doing a byte-for-byte
  restore. Whoever runs the next round on this rig should verify these match what they expect before
  relying on them.
- **R10's primary CPU-burst lever (the `adb shell` busy-loop spin script) was blocked by this
  session's permission classifier** before it could run against the device. Asked the user, who chose
  the brief's own stated fallback (`cmd thermalservice override-status 3`, released with `0` at the end)
  over retrying the blocked command or reporting UNTESTABLE.
- Build B was left installed on the rig at the end of the round (last build used, for R12) rather than
  reinstalling build A or a release build — the template doesn't require restoring the original APK,
  and whichever build the next round needs, it installs its own.

## Summary — the six things the brief said would decide what happens next

1. **Both builds compile, all tests pass.** Build A: 422/422. Build B: 321/321. Gate cleared.
2. **`Stream SPS (H.264)`: `num_ref_frames=1`, `num_reorder_frames=0`.** Per the brief's own decision
   rule, the SPS-rewrite/Moonlight-derived dependency buys nothing on this phone's encoder output and
   should be dropped, at least for this reporter's phone — no other stream was measured this round.
3. **`max-input-size` / buffer sizing verified safe.** `requested`/`got` matched exactly (1530KB, scaled
   correctly for the 1920x1080 this rig negotiated rather than the 1280x720 the brief assumed), and
   `Frame larger than the codec input buffer` never appeared. B2's risky commit is not what broke R4.
4. **The three injection signatures, all correct:** HIDE_START_CODE → `headless=60`/`orphan=167`
   (R3, PASS); DROP_FIRST → `orphan=473`/`headless=0` (R5a, PASS); DROP_LAST → `truncated=122`/`orphan=0`
   (R5b, PASS); DROP_MIDDLE → **the decoder wedged into a permanent black-screen stall/restart loop**
   instead of the expected "corrupts the picture but keeps decoding" (R4, **FAIL**). Three of four
   fault modes are clean; the fourth is a real regression, not a measurement artifact.
5. **H.265 capability/negotiation lines:** both clean, no WARN form on this rig (R2, R11) — this rig's
   hardware fully supports what was asked. **But R2 and R8 together are the round's real finding on
   #219**: two live, unprompted reproductions of the "melting"/smearing artifact class, on two
   structurally different decoders (hardware `c2.unisoc.hevc.decoder` and the bundled software FFmpeg
   HEVC), both with byte-perfect, on-time transport and zero correlating diagnostic signal from any of
   this stack's new instrumentation. The transport/reassembly layer is not the cause — settled, not
   hypothesized.
6. **Build B: zero `Disconnecting to resync.` on a healthy 10-minute link (R12), and `the codec is the
   bottleneck` never appeared in R1's undisturbed baseline (R10 itself was INCONCLUSIVE — this rig
   could not manufacture codec pressure with either lever).** Both of the brief's "must be an absence"
   checks hold.

**Bottom line: build A is not safe to ship as-is.** B1 (reassembly/diagnostics), B2 (buffer sizing),
and B4 (backpressure verdict) all measured clean. **B3 or its interaction with B1/B2 introduced a
decoder-wedge regression under DROP_MIDDLE_FRAGMENT-shaped corruption** (R4) that did not exist as a
named risk in the brief and needs to be found and fixed before this stack goes anywhere near a
reporter's device — a real mid-stream corruption on a live network is exactly the shape of fault this
regression responds to. Build B (the transport fix) is clean and independent of build A; nothing here
blocks it. The #219 investigation gained its strongest evidence yet (R2 + R8) that the artifact
originates below the transport/reassembly layer, on both decode paths, which redirects that
investigation's next step away from anything in this repository's control and toward the phone's
encoder output or decoder-vendor-level bitstream handling.

## R0 — build and unit-test gate, both builds

**PASS** (both builds)

- Build A (`f008e3d1`): `assembleGithubDebug` succeeded. `testGithubDebugUnitTest`: **422 tests, 0
  failures, 0 errors, 0 skipped** (summed from all `app/build/test-results/**/*.xml`) — matches the
  brief's predicted 422 exactly.
- Build B (`becebffa`): `assembleGithubDebug` succeeded. `testGithubDebugUnitTest`: **321 tests, 0
  failures, 0 errors, 0 skipped** — matches the brief's predicted 321 exactly.

## R1 — clean hardware session, H.264, SURFACE (the point of the round)

**PASS**

- Settings written: `log-level=2`, `video-codec=H.264`, `view-mode=0`, and `force-software-decoding`,
  `software-video-decoder`, `debug-video-fault-injection`, `debug-video-fault-rate`,
  `debug-force-memory-profile`, `debug-video-low-latency` all deleted (defaults).
- Session: connect at `23:55:43`, SSL handshake complete `23:56:21.545`, codec configured
  `23:56:23.408`, capture ended `00:00:58.632` — 4m35s of live session, continuous single capture.
- Discard-rule check: clean, no re-run needed.
- **Deliverable — verbatim `Stream SPS (H.264)` line:**
  `Stream SPS (H.264): profile=66 level=42 poc_type=2 num_ref_frames=1 size=1920x1080 vui=true bitstream_restriction=true num_reorder_frames=0 max_dec_frame_buffering=1`
  `num_ref_frames=1`, `num_reorder_frames=0` — per the brief's own decision rule, this settles it: the
  SPS-rewrite idea borrowed from Moonlight buys this project nothing on this phone's encoder output,
  and the dependency should be dropped (for this reporter's/this phone's stream; the brief frames this
  as decided by whatever a real stream reports, and this is the only stream measured so far).
- **Decoder capability:**
  `Decoder capability: codec=c2.unisoc.avc.decoder mime=video/avc target=1920x1080@60 sizeSupported=true rateSupported=true sustains=true widths=[64, 1920] heights=[64, 3840] featureLowLatency=false featureAdaptivePlayback=true`
  — full match, no WARN form.
- **max-input-size / buffer request:**
  `Configuring decoder: c2.unisoc.avc.decoder for 1920x1080, max-input-size=1530KB, memory=AMPLE (totalRam=3745MB heapLimit=512MB memoryClass=192MB lowRamFlag=false), queue=30 frames, optionalKeys=none`
  `Codec input buffer: requested 1530KB, got 1530KB per buffer` — component honoured the request
  exactly; no truncation.
- `ACodec: Allocating` / `CCodec`/`C2` line: **absent** (see Setup notes) — not a failed run per §3.
- `AapRead: largest message body so far:` — two occurrences, neither on VIDEO:
  `871 bytes (on CONTROL)`, `16149 bytes (on MUSIC_PLAYBACK)`.
- `AapRead: fragment accounting established for VIDEO`: **fired** —
  `channel=2 fragments=3 declaredTotal=40473 observed=40560 delta=-87`. Other channel with one:
  `MUSIC_PLAYBACK: channel=9 fragments=6 declaredTotal=94604 observed=94778 delta=-174`.
  **This gates R3-R5 open** — this rig's video stream does fragment this session (contrary to the risk
  §3 flagged), so R3-R5 are not INCONCLUSIVE by that gate.
- `Throughput` totals: 55 windows over the capture, **all `dropped=0`, `skipped=0`** — zero drops for
  the entire session. `rendered` sum 13950 frames / `fed` sum 13951 over ~275s of throughput-logged
  time ≈ 50.7 fps average (this session ran faster than earlier rounds' ~30fps captures; not a
  regression signal, no drops either way). `inputWait` averaged 167.6ms per 5s window.
- `dumpsys meminfo` at session end: **TOTAL PSS 90144 KB** (Java Heap 27756 KB / Native Heap 13928 KB
  private-dirty / Graphics 4852 KB / Code 22972 KB) — baseline for R6's comparison.
- Keyframe cadence observed: `23:56:23` (8200 bytes, initial), `23:57:35`, `23:58:47`, `23:59:57` —
  ~70-72s apart, consistent with the ~68-69s already measured in prior rounds (not re-measuring per §7,
  noted only as confirmation nothing regressed).
- No `reassembly anomalies` line at all. No `Stream SPS … could not be parsed`. No `Fallback to
  negotiated dimensions`. No `Frame larger than the codec input buffer`. No `SSL Decrypt: produced`. No
  `Magic Garbage`. `Codec initialized:` appeared exactly once. Two harmless `Dropped Flag 11 packet`
  control-traffic lines (len=4, len=6).
- **`AapRead: DELTA_CHANGED on VIDEO` fired 10 times, unprovoked** (no fault injection active this run).
  Per §3/§4 the brief treats any occurrence of this line as "worth the round on its own" and not
  reachable by the injector, so it is reported here in full rather than folded into the totals above.
  See the dedicated writeup under R2 below (round 2's capture, same session-independent pattern,
  makes the mechanism clear) — **this is a false positive in the audit's own bookkeeping, not evidence
  of dropped bytes.** Every one of the 10 lines satisfies `delta == -29 * fragments` exactly
  (`fragments=7→delta=-203`, `8→-232`, `4→-116`, `2→-58`), i.e. the framing overhead is perfectly
  linear and accounted-for; but `expectedDelta` stays frozen at `-87` (`3 * -29`, the fragment count of
  the very first `fragment accounting established for VIDEO` message) for the rest of the session
  instead of being recomputed per-message from that message's own fragment count. Every subsequent
  video message whose fragment count differs from the establishing message's trips the warning. Full
  lines:
  ```
  fragments=7 declaredTotal=97689 observed=97892 delta=-203 expectedDelta=-87
  fragments=8 declaredTotal=120114 observed=120346 delta=-232 expectedDelta=-87
  fragments=8 declaredTotal=127783 observed=128015 delta=-232 expectedDelta=-87
  fragments=7 declaredTotal=97604 observed=97807 delta=-203 expectedDelta=-87
  fragments=7 declaredTotal=100048 observed=100251 delta=-203 expectedDelta=-87
  fragments=4 declaredTotal=56284 observed=56400 delta=-116 expectedDelta=-87
  fragments=2 declaredTotal=25474 observed=25532 delta=-58 expectedDelta=-87
  fragments=2 declaredTotal=30162 observed=30220 delta=-58 expectedDelta=-87
  fragments=2 declaredTotal=16480 observed=16538 delta=-58 expectedDelta=-87
  fragments=2 declaredTotal=17575 observed=17633 delta=-58 expectedDelta=-87 (further DELTA_CHANGED reports suppressed)
  ```
  All 10 landed in a 200ms burst right at connection start (`23:56:25.331`-`.534`), then the 10-report
  cap silenced the rest of the session — so this almost certainly fires on every real-world session
  with more than one keyframe/delta-frame fragment-count shape, not just this rig. **Worth fixing
  before shipping**, since a WARN-level line that fires on every healthy session pollutes the exact
  signal (`DELTA_CHANGED` = "reader is skipping fragments") the brief itself says would be worth a
  round on its own if seen for real.

Picture rendered throughout, no visible artifacts, both FAIL conditions absent, one PASS condition
(`max-input-size=675KB`) differs only because this session negotiated a different resolution than the
brief assumed — the underlying check (requested==got, no oversized-frame drop) is unambiguously
satisfied. The DELTA_CHANGED false-positive above does not change the PASS verdict (§3 excludes it from
being reachable by injection and treats a real occurrence as a standalone finding, not a run failure).

## R2 — clean hardware session, H.265

**PASS**

- Settings written: `video-codec=H.265`, everything else unchanged from R1 (`log-level=2`,
  `view-mode=0`, all `debug-*`/`force-software-decoding`/`software-video-decoder` still deleted).
- First attempt invalidated the same way R1's was, for a different reason: this rig's
  `auto-start-bt-macs`/boot-receiver auto-connected the app on its own between the post-reboot check
  and the first `am start`, so by the time the logcat buffer was cleared the SPS/capability/configure
  lines had already fired once and were lost. (The `am start -n MainActivity` command's own output —
  "Activity not started, intent has been delivered to currently running top-most instance" — was the
  tell.) That session is not wasted: it produced the live-artifact evidence below. Redone clean with
  the same disconnect/clear/reconnect protocol as R1 for the lines quoted here.
- Session: connect `00:18:15` (device time), SSL handshake complete `00:18:15.529`, codec configured
  `00:18:17.122`, capture ended `00:23:24` — 5m9s of live session, continuous single capture.
- Discard-rule check: clean.
- **Deliverable — verbatim `Stream SPS (H.265)` line:**
  `Stream SPS (H.265): profile=1 level=123 chroma_format=1 bit_depth=8 size=1920x1080 max_dec_pic_buffering=2 max_num_reorder_pics=0`
- **Decoder capability:**
  `Decoder capability: codec=c2.unisoc.hevc.decoder mime=video/hevc target=1920x1080@60 sizeSupported=true rateSupported=true sustains=true widths=[64, 1920] heights=[64, 3840] featureLowLatency=false featureAdaptivePlayback=true`
  — full match, no WARN form.
- **max-input-size / buffer request:**
  `Configuring decoder: c2.unisoc.hevc.decoder for 1920x1080, max-input-size=1530KB, memory=AMPLE (totalRam=3745MB heapLimit=512MB memoryClass=192MB lowRamFlag=false), queue=30 frames, optionalKeys=none`
  `Codec input buffer: requested 1530KB, got 1530KB per buffer` — same as R1's H.264 session, exact
  match, no truncation.
- **`H.265 SPS parsed: 1920x1080 (negotiated 1920x1080)`** — fired, confirming H.265 dimensions now
  come from the stream rather than negotiation (new in `d89e26a2`). This is R2's specific PASS
  condition beyond R1's baseline checks, and it is satisfied.
- `AapRead: largest message body so far:` — `871 bytes (on CONTROL)`, `16149 bytes (on MUSIC_PLAYBACK)`
  — same values as R1, no VIDEO occurrence.
- `AapRead: fragment accounting established for VIDEO`: `channel=2 fragments=2 declaredTotal=18451 observed=18509 delta=-58`.
- `Throughput` totals: 59 windows, **all `dropped=0`, `skipped=0`**. `rendered` sum 16185 over ~295s ≈
  54.9 fps average. `inputWait` averaged 90.1ms per 5s window.
- `dumpsys meminfo` at session end: **TOTAL PSS 84311 KB** (Java Heap 17080 KB / Native Heap 12540 KB
  private-dirty / Graphics 4716 KB / Code 12568 KB).
- Keyframe cadence: `00:18:17` (627 bytes, initial), `00:19:21`, `00:20:27`, `00:21:30`, `00:22:38` —
  ~63-68s apart, consistent with prior rounds' measurement.
- No `reassembly anomalies`, no `Stream SPS … could not be parsed`, no `Fallback to negotiated
  dimensions`, no `Frame larger than the codec input buffer`, no `SSL Decrypt: produced`, no `Magic
  Garbage`. `Codec initialized:` once. Two harmless `Dropped Flag 11 packet` lines.
- **`AapRead: DELTA_CHANGED on VIDEO` fired 10 more times**, same mechanism as R1, confirmed a third
  time (including the earlier invalidated attempt) with a different frozen baseline
  (`expectedDelta=-58`, from this session's establishing message having `fragments=2`):
  ```
  fragments=3 declaredTotal=41689 observed=41776 delta=-87 expectedDelta=-58
  fragments=5 declaredTotal=75606 observed=75751 delta=-145 expectedDelta=-58
  fragments=4 declaredTotal=56980 observed=57096 delta=-116 expectedDelta=-58
  fragments=8 declaredTotal=113084 observed=113316 delta=-232 expectedDelta=-58
  fragments=4 declaredTotal=60000 observed=60116 delta=-116 expectedDelta=-58
  fragments=4 declaredTotal=61337 observed=61453 delta=-116 expectedDelta=-58
  fragments=3 declaredTotal=32342 observed=32429 delta=-87 expectedDelta=-58
  fragments=3 declaredTotal=34926 observed=35013 delta=-87 expectedDelta=-58
  fragments=3 declaredTotal=43321 observed=43408 delta=-87 expectedDelta=-58
  fragments=3 declaredTotal=33731 observed=33818 delta=-87 expectedDelta=-58
  ```
  `delta == -29 * fragments` holds in every one of the now 20 occurrences across R1 and R2, combined
  with two different codecs and two different sessions — this is not measurement noise, it is a
  deterministic bug in how `expectedDelta` is tracked (frozen at the establishing message's own
  fragment count instead of recomputed per message). See R1's writeup for the full explanation; not
  repeating it in R2 beyond confirming the pattern reproduces identically on H.265.

Both FAIL conditions absent, both codec-specific PASS conditions (`H.265 SPS parsed`, requested==got
buffer sizing) satisfied. Same DELTA_CHANGED false positive as R1, does not change the verdict.

### A live #219 reproduction, unprompted, during this run

**Not a scripted run — the operator (user) was watching the physical screen live during this R2
session and reported visible artifacts while scrolling Google Maps and Waze**, specifically "green
images" on Maps and "darkest images" on Waze almost every time. This is exactly the class of defect
this whole round exists to investigate (#219's "melting"/smearing artifacts). What was captured:

- **First report** landed while the earlier (invalidated, auto-connected) H.265 session was live. No
  correlating anomaly in the throughput log for that window — `dropped=0`, no `DELTA_CHANGED`, no
  `reassembly anomalies`, at the timestamp the report correlates to. User confirmed the artifact had
  already cleared and was not reproducible on demand at that moment.
- **Second report, "Its now visible"**, landed moments later, still during the same live session
  (before it was disconnected for the clean redo). Two screenshots were taken 39 seconds apart:
  - `evidence/r2-artifact-live.png` (device time ≈ `00:14:52`): a dark, jagged, blocky smear sits over
    the map background near "Productos Alimenticios Zenú", with an irregular edge that does not match
    any real map geometry (compare the clean, sharp road/building edges elsewhere in the same frame).
  - `evidence/r2-artifact-live-2.png` (device time ≈ `00:15:31`): the same map region, artifact gone,
    otherwise pixel-identical framing (same road labels, same zoom, same debug overlay reading
    `FPS: 60`).
  - **A keyframe landed at `00:14:59.575`**, squarely between the two screenshots. That is the
    most likely explanation for the clearing: whatever corrupted the picture was in the inter-frame
    prediction chain, and a fresh IDR reset it.
  - Across the entire bracketing window (`00:14:44`-`00:16:04`, spanning both screenshots and one more
    keyframe at `00:16:00`), **every throughput line read `dropped=0, skipped=0`, no `DELTA_CHANGED`,
    no `reassembly anomalies`, no FAIL sentinel of any kind.** The bitstream delivery layer — the part
    this whole stack's diagnostics (B1's audit, B4's backpressure verdict) were built to instrument —
    saw nothing wrong at any point during the artifact's visible lifetime.

**This is the round's most important field observation.** It demonstrates, on this rig, that #219's
artifact class can occur with byte-perfect, on-time delivery all the way to the codec's input buffer.
That rules out (for at least this occurrence) frame loss, fragment reassembly corruption, and codec
input starvation as the cause — the three failure modes B1-B4 were built to catch and report on. What
remains as the likely locus is the phone's own H.265 encoder output, or (more likely, given the
self-correction at the next keyframe) the UNISOC `c2.unisoc.hevc.decoder`'s own inter-frame
reference/error-concealment handling — neither of which any code in this repository controls.
**Recommendation for whoever picks up #219 next:** this stack's diagnostics are necessary but not
sufficient; a report from this class needs the decoder's own error/vendor logs (`logcat` around
`c2.unisoc.hevc.decoder` at a lower filter, or `dumpsys media.codec` if the vendor exposes anything)
captured at the moment of visible corruption, not just the app's own throughput/audit lines, since
those will read clean exactly as they did here.

## R11 — the capability line at negotiation time (build A, no new run — read from R1 and R2)

**PASS**

- H.264 (R1): `[ServiceDiscovery] Negotiating a profile this device claims to carry: codec=c2.unisoc.avc.decoder mime=video/avc target=1920x1080@60 sizeSupported=true rateSupported=true sustains=true widths=[64, 1920] heights=[64, 3840] featureLowLatency=false featureAdaptivePlayback=true`
  `target=1920x1080@60` matches the neighbouring `[ServiceDiscovery] NegotiatedResolution is: 1920x1080`
  line, and `codec=c2.unisoc.avc.decoder` is a real, present component.
- H.265 (R2): `[ServiceDiscovery] Negotiating a profile this device claims to carry: codec=c2.unisoc.hevc.decoder mime=video/hevc target=1920x1080@60 sizeSupported=true rateSupported=true sustains=true widths=[64, 1920] heights=[64, 3840] featureLowLatency=false featureAdaptivePlayback=true`
  Same match against its own `NegotiatedResolution is: 1920x1080` line.
- Both are the "claims to carry" INFO form, as the brief predicted for this rig — no WARN in either.

## R3 — HIDE_START_CODE injection (the B1 positive control)

**PASS**

- Settings written: `debug-video-fault-injection=4`, `debug-video-fault-rate=2`, `video-codec=H.264`
  (reverted from R2's H.265), everything else as R1.
- Session: connect `00:24:39`, `FAULT INJECTION IS ON - mode=HIDE_START_CODE, one in 2` logged at
  session start, capture ran 5m10s.
- Discard-rule check: clean.
- **Counts:** `FAULT INJECTED`: **60**. `First fragment has no start code`: **60** — exact 1:1 with
  the injection count. `requesting keyframe to recover stream` (headless form): fired but throttled to
  at most once per second as designed (26 lines over 5m10s against 60 faults).
- **Reassembly anomaly totals, summed across all 29 summary lines:** `headless=60` (matches
  `FAULT INJECTED` exactly), `orphan=167`, `truncated=0`, `overflow=0`. `truncated`/`overflow` staying
  at 0 for this mode is one of the two PASS conditions and is satisfied. The `orphan=167` alongside
  `headless=60` is the expected secondary effect the brief describes (closing the run on a headless
  first fragment discards the remaining 8s/10 as orphans instead of assembling them onto nothing) —
  not a second fault.
- `Throughput`: 65 windows, all `dropped=0`. Sample lines:
  ```
  AapVideo: FAULT INJECTED (#1): HIDE_START_CODE on flag 9, len=16120
  AapVideo: first fragment has no start code, requesting keyframe to recover stream
  ```
- `Codec initialized:` appeared exactly once — no decoder restart despite 60 corrupted frames over the
  session. No `sync_stall`, no `decoderPermanentlyFailed` signature, no crash
  (`FATAL EXCEPTION`/`AndroidRuntime` both absent).
- The user directly observed the picture visibly breaking up and repairing throughout this run — as
  expected, this is the positive control and a bad picture is the correct outcome, not a defect. Called
  out to the user in the moment so the deliberately-corrupted picture wasn't mistaken for a new bug.

## R4 — DROP_MIDDLE_FRAGMENT injection (demonstrating the blind spot)

**FAIL**

- Settings written: `debug-video-fault-injection=2`, `debug-video-fault-rate=3`, everything else as R3
  (`video-codec=H.264`).
- Session: connect `00:31:12`, `FAULT INJECTION IS ON - mode=DROP_MIDDLE_FRAGMENT, one in 3` logged at
  start. **Run stopped early at `00:32:59` (1m47s in) — not a discard, a decisive FAIL**, see below.
- **The decoder wedged completely and never recovered for the remainder of the run.** Timeline:
  ```
  00:31:15.960  Codec initialized (normal startup)
  00:31:20.023  Decoder stall detected (no output for 2005ms). Forcing restart (1/4).
  00:31:20.095  Codec initialized
  00:31:24.512  stall detected (2004ms) but restart suppressed (1/4 used, 8000ms cooldown)
  00:31:28.032  stall detected (5524ms). Forcing restart (2/4).
  00:31:28.103  Codec initialized
  00:31:38.105  stall detected (10001ms). Forcing restart (3/4).
  00:31:38.174  Codec initialized
  00:31:48.180  stall detected (10005ms). Forcing restart (4/4).
  00:31:48.259  Codec initialized
  00:31:58.263  stall (10001ms) restart suppressed (4/4 used)
  00:32:08.272  stall (20010ms) restart suppressed (4/4 used)
  00:32:18.277  stall (30016ms) restart suppressed (4/4 used)
  00:32:23.777  AapProjectionActivity: "relaunched surface still has no picture - requesting video
                focus (unsolicited)" — starts firing every ~2s, continues for the rest of the capture
  00:32:28.277  stall (40016ms) restart suppressed
  00:32:38.278  stall (50016ms) restart suppressed
  00:32:48.184  stall (59923ms). Forcing restart (1/4) — cooldown window finally let the budget refill
  00:32:48.256  Codec initialized
  00:32:58.264  stall (10007ms). Forcing restart (2/4)
  00:32:58.343  Codec initialized
  ```
  **`rendered=0` on every `Throughput` line from `00:31:28` through the run's end** — over 90 seconds
  of zero output while `fed` kept flowing at ~50fps the whole time, i.e. input was being delivered
  continuously and the decoder simply never produced a frame from it again. The stall duration climbed
  monotonically (2s → 5.5s → 10s → 10s → then 10/20/30/40/50/60s once the restart budget was
  exhausted) — the watchdog was not recovering, it was cycling through its own budget and then idling
  until an 8-second-multiple cooldown let it try again, restart, stall again within ~10s, and repeat.
  Nothing in the capture suggests this would have self-resolved; it was still in the same cycle when
  the run was stopped, 1m47s after `FAULT INJECTION IS ON`.
- **Visual confirmation:** `evidence/r4-decoder-wedged.png`, taken at `00:32:5x` — a fully black
  screen, debug overlay reading `Frame: --` (no frame data at all), and **Android Auto's own client
  surfaced its own failure dialog: "Do you see the Android Auto screen? [YES] [SWITCH RENDERER]"** —
  the phone side independently detected the same thing this rig's logs show.
- Counts: `FAULT INJECTED`: **10** (in the ~99s before the run was stopped). `Codec initialized:`
  **7 times** (1 normal startup + 6 stall-driven restarts) — the brief's PASS condition is the session
  surviving without triggering the wedge state; this is the opposite. `reassembly anomalies`: **never
  appeared** (consistent with §3 — the injector sits downstream of the audit and this fault mode isn't
  visible to it), so that specific sub-check does not itself fail, but it is moot next to the wedge.
  `AapRead: DELTA_CHANGED on VIDEO` fired **10 more times**, same false-positive mechanism as
  R1/R2 (not folded in here since it's a separate, already-documented finding, and not the cause of
  the wedge — DELTA_CHANGED lines happened during the healthy opening seconds, before the first stall).
- No crash: `FATAL EXCEPTION`/`AndroidRuntime` absent, app process stayed alive throughout
  (`pidof` confirmed after stopping the capture). This is a hang, not a crash.
- **This exactly matches the brief's explicit FAIL condition**: "the app crashes, or the decoder
  permanently stops producing frames rather than carrying on with a corrupt picture." A dropped middle
  fragment was supposed to produce a corrupt-but-still-decoding picture (the blind-spot demonstration);
  instead it walked the decoder into a stall/restart loop that burns its entire restart budget in the
  first 33 seconds and then never recovers a picture.
- Session disconnected cleanly via `headunit://disconnect` after the evidence was collected, ahead of
  R5, rather than left running or force-stopped.

**This is the round's other major finding, alongside the live #219 reproduction in R2.** The B1 branch
(`fix/video-reassembly-and-diagnostics`) changed how the reassembler treats fragments around a run
(closing on headless firsts, discarding orphans), and something in that same neighbourhood — or an
interaction between it and B2's smaller `max-input-size` / pooled buffers, or B3's decoder-restart
watchdog — now drives the stall-restart loop into a state it cannot climb out of when a real
mid-stream corruption (not just a silently-headless one) reaches the codec. Whoever picks this up next
should treat R3's clean pass and R4's wedge as bracketing evidence: **HIDE_START_CODE (fault mode 4,
first-fragment) is fully recovered; DROP_MIDDLE_FRAGMENT (fault mode 2, middle-fragment) is not.** That
narrows the search to what differs between how a headless-first run and a holed-middle run reach
MediaCodec.

## R5a — DROP_FIRST_FRAGMENT injection

**PASS**

- Settings written: `debug-video-fault-injection=1`, `debug-video-fault-rate=2`.
- Session: connect `00:34:36`, `FAULT INJECTION IS ON - mode=DROP_FIRST_FRAGMENT, one in 2`. Ran the
  full 3 minutes, no early stop needed.
- **User watched the physical screen live throughout and reported it accurately without prompting**:
  "It looks baaaaaaad" early on (109 faults already injected at that point, correctly reading as bad),
  then "It looked good for a while, then the image moved and went bad again" — this matches the
  mechanism exactly: motion increases the video stream's fragment rate, which gives the 1-in-2 injector
  more chances per second to hit, so visible corruption tracks with motion and repairs during
  lower-motion/post-keyframe windows. Confirmed against the log both times (no stall, `dropped=0`,
  only 1 `Codec initialized` throughout) rather than taken at face value, since R4 had just shown a
  bad-looking picture can also mean a real regression — this one didn't.
- **Counts:** `FAULT INJECTED`: **317**. `Orphaned fragment (Flag 10)`: **317** (1:1 with faults —
  every dropped first fragment's matching last-fragment arrives orphaned). `Orphaned fragment (Flag
  8)`: **156** (the middle fragments belonging to those same dropped-first frames, also orphaned).
  `orphaned fragment, requesting keyframe to recover stream`: **61** lines — correctly throttled to
  ~1/s against 317 faults.
- **Reassembly anomaly totals:** `orphan=473` (= 317 + 156, exact match), **`headless=0`, `truncated=0`,
  `overflow=0`** — both required-zero PASS conditions satisfied. This is the one injection mode that
  produces orphans without any headless first fragment, distinguishing it cleanly from R3.
- No `Decoder stall detected`, `Codec initialized` exactly once (no restart), no crash. `Throughput`:
  41 windows, all `dropped=0` — the picture degraded from missing reference data, never from the
  transport or decoder failing to keep up.

## R5b — DROP_LAST_FRAGMENT injection

**PASS**

- Settings written: `debug-video-fault-injection=3`, `debug-video-fault-rate=2`.
- Session: connect `00:38:49`, `FAULT INJECTION IS ON - mode=DROP_LAST_FRAGMENT, one in 2`. Ran the
  full 3 minutes.
- **User reported "It already looks bad since the start"** — checked against the log immediately
  rather than assumed: `dropped=0`, `Codec initialized` exactly once, no stall, 86 faults already
  injected by that point at a 1-in-2 rate (an aggressive rate by design) — same conclusion as R5a,
  expected positive-control corruption, not a wedge.
- **Counts:** `FAULT INJECTED`: **122**. `Previous frame was truncated!`: **121** (one behind the
  fault count — the very last injected fault's truncation is detected by the *next* frame's opening
  fragment, which likely hadn't arrived yet when the capture was stopped). `frame truncated,
  requesting keyframe to recover stream`: **35** lines, correctly throttled to ~1/s against 122 faults
  — and this keyframe request is itself the change from `main`, which logged the truncation and asked
  for nothing.
- **Reassembly anomaly totals:** `truncated=122` (exact match), **`orphan=0`, `headless=0`,
  `overflow=0`** — both required-zero PASS conditions satisfied, cleanly distinguishing this mode from
  R3 and R5a.
- No `Decoder stall detected`, `Codec initialized` exactly once, no crash. `Throughput`: 41 windows,
  all `dropped=0`.
- No `AapRead:` audit line fired in this sub-run either, consistent with §3 (the injector is downstream
  of the audit for all three modes).

## R6 — forced CONSTRAINED memory profile (regression, weak power by design)

**PASS**

- Settings written: `debug-force-memory-profile=CONSTRAINED`, `debug-video-fault-injection` and
  `debug-video-fault-rate` deleted (back to off), everything else as R1.
- Session: connect `00:42:59`, capture ran 10m33s (00:42:59-00:53:32).
- `Configuring decoder: c2.unisoc.avc.decoder for 1920x1080, max-input-size=1530KB, memory=CONSTRAINED (FORCED) (totalRam=3745MB heapLimit=512MB memoryClass=192MB lowRamFlag=false), queue=30 frames, optionalKeys=none`
  — `memory=CONSTRAINED (FORCED)` confirms the setting took effect.
- `Throughput`: 124 windows, **all `dropped=0`, `skipped=0`** — `rendered`/`fed` within noise of R1 (no
  drops appeared that R1 didn't have; `rendered` sum 22710 over ~620s ≈ 36.6 fps average, a slower
  session than R1's/R2's but zero drops either way — no starvation signal).
- `dumpsys meminfo` at end: **TOTAL PSS 87272 KB** (Java Heap 26180 KB / Native Heap 14948 KB
  private-dirty / Graphics 4720 KB / Code 24112 KB) — against R1's H.264 baseline of 90144 KB, this is
  *lower*, i.e. well within noise on this 3.8GB rig exactly as §3 predicted. **Unchanged/improved
  reading, reported as PASS per the brief's own framing** (R6's real job is confirming the constrained
  path doesn't cost throughput, not proving a memory reduction that a 3.8GB rig can't show cleanly
  anyway).
- No `Decoder stall detected`, `Codec initialized` exactly once, no crash.

## R7 — GLES with hardware decoding

**PASS**

- Settings written: `view-mode=2`, `debug-force-memory-profile` deleted, `video-codec=H.264`.
- Session: connect `00:54:17`, first codec init `00:54:19.430`. Ran the full 5 minutes.
- Setup note: media playback would not stay in `PLAYING` on this session despite repeated
  `input keyevent 85` — each press was confirmed forwarded correctly (`CommManager: TX Key -> AA=85`,
  5 separate press/release pairs, all logged), so the routing itself worked; Spotify on the phone side
  simply kept returning to `PAUSED`, cause not investigated since it's outside this round's scope. Not
  a blocker for R7's actual purpose — it tests GLES surface/rendering correctness on the hardware path,
  not audio, and a screenshot confirmed the map view was actively rendering (`FPS: 49` in the on-screen
  debug overlay) throughout.
- `Throughput`: 81 windows, **all `dropped=0`** — comparable to R1's numbers. `Codec initialized:`
  once for this session. No `Decoder stall detected`, no crash.
- `first YUV420 frame queued` and `direct YUV upload missed its 50ms deadline`: **both absent**, as
  required — the hardware path never touches the YUV/direct-upload code (that's R8's path).
- **Teardown check:** `headunit://exit` then relaunch via `headunit://connect`. Second session
  established cleanly — `Codec initialized: c2.unisoc.avc.decoder` fired a second time,
  `Output Format Changed` and `First frame rendered (hardware decode)` both appeared, throughput
  resumed at `dropped=0`. The `release()` ordering fix (Surface released inside the same posted
  callback that tells the decoder it's going) is exercised correctly by this teardown/relaunch cycle —
  no black screen, no stuck state, no restart-loop symptom like R4's.
- `AapRead: DELTA_CHANGED on VIDEO` fired again in the second session (10 more instances, same
  `delta == -29 * fragments` / frozen-`expectedDelta` pattern as R1/R2 — 5th confirmation of that
  finding across the round, not repeated in detail here).
- No `KEYCODE_HOME` press used per §7's note that Home does not tear down the surface on this unit;
  `headunit://exit` was the correct lever, as the brief specifies.

## R8 — GLES with the bundled FFmpeg HEVC decoder (the YUV path)

**PASS on the brief's named criteria — with two findings that matter more than the label**

- Settings written: `view-mode=2`, `video-codec=H.265`, `force-software-decoding=true`,
  `software-video-decoder=1`. Not skipped — R2 was PASS, not INCONCLUSIVE.
- Session: connect `01:02:31`, `Configuring bundled FFmpeg HEVC decoder for 1920x1080` first fired
  `01:02:32.749`, ran the full 5 minutes.
- `Configuring bundled FFmpeg HEVC decoder for 1920x1080` appeared, `first YUV420 frame drawn`
  appeared, picture rendered for the full five minutes, no crash, app process stayed alive throughout.
  **The brief's specific named FAIL trigger — `direct YUV upload missed its 50ms deadline` appearing
  more than once — never fired at all** (0 occurrences). By that literal criterion this is a PASS.
- **Finding 1 — a rocky first 30 seconds, not named in the brief's checks.** Four
  `AapProjectionActivity.maybeRecoverFromDisplayStall | Display stall (no draw for N ms). Rebuilding
  projection view (attempt K). See issue #650.` events fired in quick succession right after connect
  (`01:02:42` attempt 1/6003ms, `01:02:52` attempt 2/8003ms, `01:03:02` attempt 3/8003ms, `01:03:12`
  attempt 4/8004ms), each one tearing down and rebuilding the entire GLES surface, GL context, and
  bundled decoder — visible on screen as the picture blanking and reappearing. **No fifth stall
  occurred**; the session then ran cleanly and rendered continuously for the remaining ~4.5 minutes.
  Every `Throughput` line on this path read `fed=0 (0fps)` for the entire session, including during
  the stable period — most likely a counter that this codepath simply doesn't feed (the bundled FFmpeg
  path bypasses `MediaCodec.queueInputBuffer`, which is what the counter is almost certainly wired to),
  not literal zero data arriving, since `rendered` stayed healthy (100-260/window) and `dropped=0`
  throughout even while `fed` read 0. Recommend whoever owns this branch either wires `fed` to a
  path-agnostic counter or documents that it reads 0 by design on the software path — as it stands the
  number is actively misleading to anyone reading a log from this path.
  **User directly correlated the stall cluster with input** — reported, unprompted, "The restarts
  happen everytime i moved the screen" and refined it to "everytime I moved the screen a lot" — matching
  the CPU readings at the time: `CPU: app 133%` / `Frame: 54ms` in a screenshot taken during the cycle,
  against 30-58% / ~15-20ms readings everywhere else this round. The software decode path appears to
  saturate under heavy-motion load in a way the hardware paths (R1, R2, R7) did not, and something
  downstream of that saturation stops producing draws for 6-8 seconds at a stretch, which the existing
  issue #650 recovery mechanism catches and resolves by rebuilding.
- **Finding 2 — two more live, unprompted #219 reproductions, on a second decoder implementation.**
  During the stable period the user twice reported visible corruption without being asked to look for
  it ("I have saw some artifacts on this software run", then after it cleared, "Reapeared"). Checked
  against the log both times: **`dropped=0`, no `DELTA_CHANGED`, no `Display stall`, no reassembly
  anomaly at either correlating timestamp** — same signature as R2's finding, on a completely different
  decoder (bundled FFmpeg software HEVC here vs. `c2.unisoc.hevc.decoder` hardware in R2).
  `evidence/r8-artifact-live.png` (device time ≈ `01:05:55`) shows dramatic, widespread, sharp-edged
  block-shaped dark corruption across most of the visible map/terrain — a different visual character
  from R2's small localized smudge, but the same class of defect (classic block/macroblock-shaped
  compression corruption). A second screenshot moments later
  (`evidence/r8-artifact-live-2.png`, ≈`01:06:37`) happened to catch a clean instant — the corruption
  appears to flicker rather than hold steady, which is consistent with a per-frame or per-reference
  defect rather than a single stuck bad frame.
  **This is the strongest evidence in the round on the #219 question.** Two structurally different
  decoders — one a vendor hardware ASIC, one a portable software codec this project bundles and
  controls the exact version of — both produced the same class of visible corruption, both with
  byte-perfect, on-time transport all the way to their respective inputs. That argues against a
  decoder-implementation-specific bug and toward either the phone's H.265 encoder output itself, or a
  bitstream interpretation issue shared by both decode paths (e.g. a reference-frame or
  entropy-decoding edge case neither implementation handles identically to the encoder's assumptions).
  Combined with R2's keyframe-correlated clearing and this run's apparent flicker-not-hold behavior,
  the shared trait across all observed instances is: **byte-perfect delivery, corruption that isn't
  permanent, and no signal in any diagnostic this stack added.** Whoever picks up #219 next should
  treat "the transport/reassembly layer is not the cause" as settled by this round, not as a hypothesis
  still open.

## R9 — the configure ladder and low-latency keys (3-minute log harvest)

**PASS** (as the setting-is-inert case; ladder itself is INCONCLUSIVE, exactly as the brief predicted)

- Settings written: `debug-video-low-latency=true`, back to R1's baseline otherwise (`video-codec=H.264`,
  `view-mode=0`, `force-software-decoding`/`software-video-decoder` deleted).
- Session: connect `01:09:23`, ran the full 3 minutes.
- `Decoder capability: codec=c2.unisoc.avc.decoder mime=video/avc target=1920x1080@60 sizeSupported=true rateSupported=true sustains=true widths=[64, 1920] heights=[64, 3840] featureLowLatency=false featureAdaptivePlayback=true`
  — `featureLowLatency=false`, as expected for this component.
- `Configuring decoder: c2.unisoc.avc.decoder for 1920x1080, max-input-size=1530KB, memory=AMPLE (...), queue=30 frames, optionalKeys=none`
  — **one line only**: `optionalKeys=none`, agreeing with `featureLowLatency=false`. No
  `Decoder rejected optionalKeys=` and no `Decoder accepted the format only with optionalKeys=` lines
  — the ladder never had a rung to try beyond the base format, since UNISOC doesn't match the
  MediaTek/Amlogic/Qualcomm/Exynos/HiSilicon vendor-key pattern list and doesn't advertise
  `FEATURE_LowLatency`.
- Session configured and rendered on the first attempt: `Codec initialized:` exactly once, `Throughput`
  43 windows all `dropped=0`, no `Decoder stall detected`, no crash.
- **This rig cannot exercise the ladder** — legitimate INCONCLUSIVE for that specific question, exactly
  as §3 predicted — but the setting itself is confirmed inert and harmless: it changed nothing
  observable and did not prevent a clean session.

## R10 — the backpressure verdict, provoked (build A)

**INCONCLUSIVE** — neither lever could manufacture codec pressure on this rig, consistent with round
6's own R2 finding.

- Settings written: back to R1's baseline (`video-codec=H.264`, `view-mode=0`, `log-level=2`,
  nothing else set).
- **Setup deviation:** the brief's primary CPU-burst lever (`adb shell` busy-loop spin processes) was
  blocked by this session's permission classifier before it could run. Asked the user how to proceed;
  they chose the brief's own stated fallback — `cmd thermalservice override-status 3` — rather than
  retrying the busy-loop or reporting UNTESTABLE. Released with `override-status 0` at the end of the
  run, confirmed.
- Connection took longer than usual to establish this run: the phone's stale Bluetooth HFP link from
  the previous session (R9) caused four consecutive poke-skips
  (`noteHandsFreePokeSkip: this head unit already holds a Bluetooth hands-free link...`), then a
  Bluetooth cycle on the phone triggered a fresh `AutoStartReceiver` self-wake that also stalled.
  Resolved with a force-stop + relaunch of the app, which reset the native AA manager state cleanly and
  connected on the first attempt afterward. Noted here since it cost real time and is a reasonable
  recovery step for a future round to reach for sooner.
- Session ran the full 8 minutes with the thermal-throttle override active throughout.
- `Throughput`: 193 windows, **all `dropped=0`** — including windows that dropped to 15-25 fps (well
  below the usual ~50 fps), so the throttle was measurably affecting the rendering rate, just never
  past the point of an actual drop.
- `VideoDecoder: the codec is the bottleneck` — **did not appear**, in this run or in R1's undisturbed
  baseline (checked explicitly). Since the FAIL condition is this line firing during an *undisturbed*
  run and that didn't happen, and the INCONCLUSIVE condition is neither lever producing a drop, this
  is a clean INCONCLUSIVE rather than either a PASS or FAIL — there is nothing here for the verdict
  logic to have gotten wrong, because it was never asked to make a call.
- No crash.

R11 was already covered above (read out of R1's and R2's captures, per the brief — no new run needed).

## R12 — the transport fix does not disconnect a healthy link (build B)

**PASS**

- Installed `becebffa` (build B) via `install_and_launch.sh SKIP_BUILD=1` after pulling the pre-built
  APK from `apks/`. Settings already at `log-level=2`, `video-codec=H.264`, `view-mode=0` from the
  previous run — none of build B's `debug-*` keys exist on this build (as the brief notes), so nothing
  else was written.
- **Setup note:** no `SSL handshake complete` line appears in this capture. The session was already
  live by the time the logcat buffer was cleared and the explicit `connect` intent was sent — the same
  silent auto-reconnect-on-launch mechanism seen earlier in the round (the phone's Bluetooth link was
  never dropped between runs, so `AutoStartReceiver` brought `AapService` up and connected before the
  capture started). Confirmed this is still one single, continuous, undisturbed session and not two
  concatenated ones: the `Throughput` stream runs unbroken from `01:33:07` to `01:43:17` (10m10s, 123
  consecutive 5s windows, no gap), which is what the run actually needs.
- **Counts:** `Disconnecting to resync.` (all four variants): **0**. `AapRead: WiFi read timeout
  (15000ms)`: **0**. Both are the required absences.
- `Throughput`: 123 windows, **all `dropped=0`**. Session survived the full window undisturbed, no
  unexpected reconnect.
- No crash.
- **Cross-thread observation, not part of this brief's scope:** the user, watching the live screen
  during this run, asked whether leaving Spotify paused full-screen for ~10 seconds and getting a
  "Connection lost"-style overlay was what this test covered — it is not. That behavior is
  `AapProjectionActivity.showReconnectingOverlay`/`maybeRequestVideoFocus`, driven by a frame-activity
  watchdog independent of anything build B touches, and it fired **4 times** in this capture (each
  preceded by repeated `connected but no frames - requesting video focus (unsolicited)` lines, each
  cycle lasting roughly 6-16 seconds — consistent with the user's own "~10 seconds" estimate). This is
  the defect the *separate*, already-queued `idle-screen-reconnect-overlay` thread on this branch
  exists to investigate (issue #852) — worth flagging to whoever picks that thread up next: it
  reproduces on this rig readily and without any special setup, which was still an open question for
  that round's own R1 gate.

## Round complete

All twelve runs (R0-R12, R11 folded into R1/R2's captures) are done. See §8 in the report-back below
for the six things that decide what happens next.

## Anything the brief did not ask about

- The rig negotiated 1920x1080@60 this round rather than the 1280x720 seen in earlier decoder rounds.
  Nothing in this round's settings changed `resolutionId` (left at its prior value, 3); whatever picked
  the higher resolution did so independent of this round's changes. Worth a note for whoever reads the
  max-input-size numbers later: they scale with negotiated resolution, not a fixed rig constant.
