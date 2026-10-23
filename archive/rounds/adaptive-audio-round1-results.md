# adaptive-audio — round 1 results

**Candidate:** `emotionbug/open-headunit` `pr/audio-low-latency-stability` @ `a22579ee57da84cc2f1b0aeb6f5bbce98a077dec`       **Baseline:** `origin/main` @ `7db89757`
**APK md5:** candidate `43afe6cdbf308b3724cf6e52af7e02fa` / baseline `7d4113ae68aea356c37fb5e27e2407dd`
**Unit:** four head units — D-HU (UNISOC MT50, Android 14), D-T230 (Samsung SM-T230, Android 4.4.2 / API 19), D-HP (HP Slate 7 Plus, Android 4.2.2 / API 17), D-POCO (POCO X3 NFC, Android 15 / API 35, modern Snapdragon); phones D-POCO and D-MOTO (Motorola edge 30 neo)
**Date:** 2026-09-28

## Setup notes

- **`enable-audio-sink` is not in the brief's §4 settings table, but every run needs it `true`.** D-HU had it explicitly set to `false` at round start (code default is `true`, so something had overridden it). With it `false` the phone never gets an audio sink to send to — video and control both work fine, so this is easy to miss. Cost a full session bring-up + relaunch on D-HU before A1 could start. D-T230 and D-HP already had it `true`; D-POCO also needed no change.
- **The media-widget tap coordinate the brief gives for D-HU, `input tap 272 657`, no longer hits play/pause.** A screenshot showed the actual widget centered around `(770, 665)` on the same `1440x720` layout; used that for every D-HU/D-T230/D-POCO run instead (verified once per device with a screenshot; D-HP used `input keyevent 85` sent to the head unit instead, see H1 below).
- **D-POCO's WiFi was fully consumed by the Native AA P2P group and its mobile data was off**, so Spotify streaming from the network buffered forever ("BUFFERING", never "PLAYING", `audio=0kB/s` indefinitely). Toggling mobile data on briefly fixed it, but the operator asked for it back off and instead played an already-downloaded/offline track directly on the phone, which sidesteps the issue entirely and is what every later D-POCO run used.
- **Playing a track directly on the phone (not through the AA media widget) does not open a fresh AA audio channel** — matches the known `audio-start-and-teardown` round 1 finding. After the D-POCO mobile-data episode, `audio=0kB/s` persisted even with Spotify genuinely `PLAYING` until Spotify was force-stopped and relaunched, then started via the AA widget tap; only then did the channel open (`AudioMixer: channel 6 started playing after banking`).
- **D-HU's actual `audio-latency-multiplier` was 8 at round start, not the "rig value" of 2 the brief's §3a assumed.** Doesn't change any run, since A1's own settings table writes 2 explicitly, but the brief's framing of "a plain run cannot tell the two apart" was built on a stale assumption about the rig's default.
- **The host machine's own background-task memory-pressure reaper killed A7's 5-minute wait mid-run** (a system-level event, unrelated to the rig or the app) after ~2m25s. Restarted A7 clean from scratch once the gap had grown past 40 minutes rather than trust a stretched window.
- **`su` does not exist on D-HU** — its shell is already `uid=0`. The brief's literal `adb shell su -c 'killall audioserver'` for A6 fails; used `adb shell killall audioserver` directly.
- **D-T230 and D-HP have no `sed`/`busybox`**, so `set_pref.sh`'s on-device sed script can't run there (matches the known D-T230 quirk; D-HP shares it). Settings were changed by pulling `shared_prefs/settings.xml`, editing it locally, and pushing it back.
- **On D-T230 and D-HP, `cp` between `/data/local/tmp` and `shared_prefs/` intermittently failed** — once with a bare usage message, once with "No such file or directory" against a relative path that demonstrably existed a moment later. Worked reliably only with fully absolute source and destination paths on both ends.
- **D-HU still held its Native AA/Bluetooth link to D-POCO from the A-series runs when S1 started.** D-T230's poke to D-POCO got no response for the first ~3 minutes; force-stopping the app on D-HU released D-POCO immediately, and D-T230's next poke succeeded within seconds.
- **H1's phone-side head-unit server (`:5277`) has no scriptable bring-up.** Only the "Start head unit server" toggle in Android Auto Developer settings on the phone brings it back; the operator did this once on D-MOTO (D-POCO's is confirmed down, matching the brief).
- **H1's build forces AAC regardless of `use-aac-audio=false`** when the negotiated link is capped to a 2.4 GHz-class narrowband profile on old Android: `Companion.announcesAac | [ServiceDiscovery] AAC audio announced by the 2.4 GHz cap (Use AAC Audio is off)`. A real platform override the brief's §4 table doesn't account for.
- **D-HP negotiated an 800x480 screen on its 1280x800 panel, rendered rotated**, and showed no play/pause control until a track was actually queued (a collapsed "Spotify — Tap to open" card beforehand). Media control was driven with `input keyevent 85` sent to the head unit (D-HP), which AA forwards to the phone through the INPUT channel — the same pattern A1 used for track-skip on D-HU.
- **D-HP's adb serial has changed since it was last documented** (a stale note has it as `CNU350BGBJ`; it currently enumerates as `0123456789ABCDEF`).
- D-MOTO's Bluetooth was off at the start of the round; enabled before P1.
- A stray `adb logcat` capture process was left running across several run transitions (A2 through A7) before being caught and killed. Corrected mid-round; every run from A6 onward has exactly one capture process.
- Scripts used: `build_hur.sh`, `set_pref.sh` (D-HU, D-POCO), `install_and_launch.sh` was not used — installs went via plain `adb install -r` from pre-built APKs in `apks/` to avoid rebuilding per device. No new script added to `hur-wifi-test-scripts/`.

## R0 — Gate

**PASS**

- `./gradlew testGithubDebugUnitTest`: **2508 tests, 0 failures** (summed from the JUnit XML). The brief's expected count is 2501 (author's own count); the two counts differ by 7 but the PASS bar — 0 failures — is met.
- `git log --oneline 7db89757..HEAD | wc -l` = **12**, matches the brief.
- APK md5s differ: candidate `43afe6cdbf308b3724cf6e52af7e02fa` vs baseline `7d4113ae68aea356c37fb5e27e2407dd`.
- `AdaptivePcmBuffer` string count in candidate APK: **4** (≥1 required). `libhur_aaudio.so` count: **4** (≥1 required).

## R:A1 — Media PCM under load, candidate vs baseline

**PASS**

- Settings: `use-aac-audio=false`, `wifi-connection-mode=3`, `log-level=2`, `audio-latency-multiplier=2`, `audio-queue-capacity=20`, `use-aaudio-output=false`, `static-audio-focus=true`, `attach_hw_dsp_equalizer=false`, `enable-audio-sink=true` (see Setup notes).
- Baseline arm (D-HU): `Media Start Request AUDIO`=20, `disabled due to previous underrun`=0, `AudioMixer: output stopped`=0, audio inbound rate non-zero in all 35 windows.
- Candidate arm (D-HU): `Media Start Request AUDIO`=20, `disabled due to previous underrun`=0, `AudioMixer: output stopped`=0, audio inbound rate non-zero in all 35 windows. Final `channel=6 target=` line at A1-end: `concealedFrames=11792 staleFrames=55360 compressedFrames=20800` (well under the 48000/1s ceiling). `first PCM channel=6 requestToPcm=4ms pcmToRender=54ms`.
- Candidate ties baseline at 0 underruns and 0 starts-with-underrun; the difference the brief asks for is that both are zero rather than the candidate being better or worse.

## R:A2 — Unsaved default latency

**FAIL**

- Settings: candidate only, `use-aac-audio=false`, `audio-latency-multiplier` deleted and confirmed absent.
- `AudioDecoder.start:` line for channel=6 shows `latencyMultiplier=2` ✓.
- First 10s after music start (first PCM at 13:55:14.017): `channel=6 target=120ms` at 13:55:18.983 — meets the ≥120ms bar ✓.
- Trajectory: settled 97ms→67ms through 13:57:29 (inside 60-150ms), then jumped to `target=315ms arrivalGapMax=295ms` at 13:57:39 (a real late-batch event, >150ms), another jump to `target=342ms arrivalGapMax=322ms` at 13:58:09, then held 305-342ms through window end. **Final reading, 13:59:09: `target=312ms depth=292ms arrivalGapMax=45ms`** — outside the 60-150ms band, and the only nearby `arrivalGapMax` is 45ms, not >150ms, so the brief's late-batch exception does not cover this reading.
- Underrun count: 0. Final `concealedFrames`=4560.
- **FAIL per the brief's literal criterion**: the target grew in response to genuine late-batch events but never decayed back to the settled band within the ~90 seconds after those events stopped, and the final reading in the window has no accompanying late-batch evidence.
- `audio-latency-multiplier` restored to 2 afterward, confirmed.

## R:A3 — Saved 16 is honoured

**PASS**

- Settings: candidate only, `audio-latency-multiplier=16`.
- `AudioDecoder.start:` for channel=6 shows `latencyMultiplier=16` ✓. (A separate, non-media 16kHz mono stream on channel=4/5 independently shows `latencyMultiplier=4` — unrelated to this setting, not a defect.)
- **Every** `channel=6 target=` line in the window (19 lines, 14:00:35–14:03:25) reads exactly `target=400ms` ✓.
- `audio-latency-multiplier` restored to 2 afterward, confirmed.

## R:A7 — What the new diagnostics cost

No pass/fail (informational).

- Settings: candidate only, `use-aac-audio=true` (rig default), `log-level=2` (INFO).
- First attempt was interrupted at ~2m25s of 5 minutes by the host's own background-task memory-pressure kill (see Setup notes); restarted clean.
- 17 `Audio transport read channel=` lines in the clean 5-minute window.
- **15/17 (88%) are header-dominated**: `header≥50ms` while `body`, `decrypt`, `dispatch` are each `<5ms` — matches the brief's expectation that the line measures the phone's idle time. The 2 that don't qualify: one has `body=79ms` (body-dominated), one has `body=6ms` (1ms over the bound).
- `readerGap≥50ms` count: **0/17**.

## R:A4 — AAC resumes past the 30-permit window

**INCONCLUSIVE** (both arms)

- Settings: candidate first then baseline (positive control), `use-aac-audio=true`, `log-level=0` (VERBOSE).
- Candidate: `RECV: AUDIO .* type: 1` (codec config) count = **0** across the full 40-cycle, ~12-minute window. `Media Start Request AUDIO`=39/40. `AAC output rejected`=0. `Media Stop Request`=40 (one per pause tap, expected). Phone ends `PAUSED` at A4-end, with the final inbound `audio=0kB/s` window — but the baseline control shows the identical pattern (`type:1`=0, `Media Start Request AUDIO`=39, `Media Stop Request`=40, ends `PAUSED`), so this is the blind toggle script's own end state on both arms, not a candidate regression.
- Baseline (positive control): `type:1`=0, `Media Start Request AUDIO`=39, `AAC output rejected`=0 — matches candidate.
- **Per the brief's pre-registered outcome**: "the type: 1 count is 0 or 1 in the whole window. In that case the phone sends a config once per session, so the leak cannot accumulate on this phone." True on both arms with this phone pairing — the permit-leak question A4 exists to answer is untestable here.

## R:A5 — AAudio, opt-in, under stress

**PASS**

- Settings: candidate only, `use-aac-audio=false`, `use-aaudio-output=true`, `attach_hw_dsp_equalizer=false`.
- `opened AAudio callback` count: **5** — one per each of the 5 disconnect/reconnect session cycles, every cycle completing a fresh SSL handshake within 12-16s of the reconnect broadcast.
- `opened AudioTrack` count: **0** (no fallback ever triggered). No `AAudio -> AudioTrack` / `AAudio unavailable` / `AAudio could not open` lines at all.
- Native crash / `Fatal signal` / `SIGSEGV` / `tombstone` mentions: **0**. `adb shell ls -t /data/tombstones` unchanged before and after (`tombstone_14` both times).
- `AudioMixer: output stopped`: 0. Final `xruns=0`.

## R:A6 — Dead audioserver, candidate vs baseline (characterisation)

No pass/fail — but the finding contradicts the brief's own stated premise.

- Settings: D-HU (rooted shell, no `su` binary — used `adb shell killall audioserver` directly).
- **Candidate, `static-audio-focus=true`**: after the kill, `AudioFlinger: Failed to add event callback` (expected transient from clients hitting the dying/respawning server), then `rebanks=3, silentCycles=10` as the mixer recovered. Audio resumed to `audio=187kB/s` by A6-end. `output stopped`=0. No reopen of `AudioTrack` — recovered via the same track object, consistent with the candidate's own commit title ("Keep audio output active during rebanking and recover stalled writes").
- **Candidate, `static-audio-focus=false`**: same clean recovery, `audio=187kB/s` by end, `output stopped`=0.
- **Baseline, `static-audio-focus=true`**: **also** recovered cleanly — `audio=187kB/s` by end, `underruns=0` across all three sink diagnostics (AUDIO/AUDIO1/AUDIO2), `output stopped`=0.
- The brief's premise — "the code says neither build recovers before the next session" — does not hold on this rig/build: baseline and every candidate config resumed audio within about a minute of the kill, no new session needed. Worth checking separately whether this device's audio HAL respawns transparently regardless of app-level recovery logic.

## R:S1 — API 19 (D-T230), candidate vs baseline

**PASS** by every log-based criterion the brief states — but see the audible finding below.

- Settings: `use-aac-audio=false`, `use-aaudio-output=true` (deliberately, to confirm the SDK gate holds AAudio off below API 26).
- Candidate: **0** `VerifyError`/`UnsatisfiedLinkError`/`NoSuchMethodError`/`FATAL EXCEPTION`. **0** `AAudio` lines (gate holds — AAudio never attempted). `opened AudioTrack` present (×3, `effective=3343 frames`). Audio inbound rate never 0 across the 12-minute window. Final `concealedFrames=0 staleFrames=7328`.
- Baseline: same — 0 exceptions, 0 zero-audio windows.
- **The operator heard ~5-6 audible stutters on the candidate arm vs 1 on the baseline arm**, over comparable ~12-minute sessions — despite both arms reading perfectly clean on every counter the app exposes: `AudioMixer` target/depth/concealedFrames, `AudioTrackWrapper` underruns/silentCycles/rebanks/dropped, and zero `AudioFlinger` WARN lines, on both arms. The candidate's `arrivalGapMax` climbed steadily through the session (107ms→128ms→145ms→160ms→185ms) while `depth` oscillated 280-427ms around the fixed 400ms target (D-T230's own rig `audio-latency-multiplier=16`), though this never crossed into logged concealment. This is a real, measurable candidate-vs-baseline difference the brief's log-only PASS criteria cannot see.

## R:H1 — API 17 (D-HP), Headunit Server mode

**PASS** by every log-based criterion the brief states — but see the audible finding below.

- Settings: candidate only, `wifi-connection-mode=1`, `use-aac-audio=false` (the build forces AAC anyway on this link — see Setup notes). Phone: D-MOTO (its `:5277` server needed the manual toggle; D-POCO's confirmed down, matching the brief).
- **0** exceptions, **0** `AAudio` lines, `opened AudioTrack` present (×3, `effective=2229 frames`), audio inbound rate never 0, phone reads `PLAYING` at H1-end (`position=1184`, advancing).
- **The operator heard "a lot" of stutters, self-reported as correlating with system CPU/load** — again with 0 underruns/exceptions/`output stopped` in the log. This is the second of two weak/old-hardware units (D-T230, D-HP) with an audible candidate-side regression that the log-only grading entirely misses.

## R:P1 — Modern Snapdragon head unit (D-POCO + D-MOTO), AAudio on/off

**PASS**

- Settings: candidate only, `use-aac-audio=false`.
- **Arm 1 (`use-aaudio-output=false`)**: `opened AudioTrack` ×3 (`effective=960 frames, burst=480`). `output stopped`=0. No crash. Audio inbound rate never 0; final `audio=187kB/s`.
- **Arm 2 (`use-aaudio-output=true`)**: `opened AAudio callback` ×3 (`effective=288 frames` at open, `burst=96`). `output stopped`=0. No crash. Audio inbound rate never 0; final `audio=186kB/s`. `xruns=5, producerUnderruns=5` — but all 5 occurred in the first ~10 seconds (climbed 4→5, then held flat with zero further growth for the remaining ~12.5 minutes including all 10 pause/resume cycles). A one-time startup stabilization transient, not an ongoing issue; violates none of the brief's stated PASS conditions.
- **The operator reported the AAudio arm "sounds different… not bad, just different"** from the AudioTrack arm. Plausible explanation: AAudio's much smaller low-latency burst (96 vs 480 frames) can route more directly to the HAL, bypassing framework-level audio processing (system EQ, etc.) that a standard `STREAM_MUSIC` `AudioTrack` path goes through — not confirmed from available logging, since no performance-mode/sharing-mode line exists to check it directly.

## Report back (brief §8)

1. **A1**: candidate ties baseline — 0/0 underruns, 0/0 starts-with-underrun on both arms; final candidate `concealedFrames=11792` (well under the 48000 ceiling).
2. **A4**: `type: 1` count is **0 on both arms** — the permit-leak this run tests for cannot accumulate on this phone; no resume N is meaningful since there was never a permit to leak.
3. **A5**: 0 native crashes, 0 fallback lines, final `xruns=0`.
4. **S1 and H1**: crash-free on both — yes.
5. **A7**: 15/17 (88%) of slow-read lines are header-only time.

## Anything the brief did not ask about

- **The single biggest finding of this round is the audible-stutter pattern on S1 and H1** (D-T230/API19 and D-HP/API17, both weak/old hardware) — a real, operator-heard, candidate-vs-baseline difference on S1 and a clear candidate-side complaint on H1, with **every** app-level counter reading perfectly clean on both arms of both runs. None of the brief's stated PASS criteria for S1 or H1 would have caught this; they are log-only and this defect (if it is one) apparently lives below where the app's own instrumentation looks. Worth a hardware-profiling follow-up specifically on weak/old SoCs rather than trusting the log-clean PASS verdicts at face value.
- A6's baseline-also-recovers result undercuts the brief's own stated assumption for that characterisation run and is worth a second look — see R:A6 above.
- `enable-audio-sink` being absent from the brief's own settings table (§4) despite being load-bearing on every single run is worth fixing in the next brief.
- The D-HU media-widget tap coordinate drifted from the brief's documented `(272, 657)` to the actually-working `(770, 665)` since whatever build/round last measured it.

## Evidence

Captures zipped and uploaded to `rig-evidence-adaptive-audio` (release created this round), asset `adaptive-audio-round1-captures.zip`, sha256 `a4bfa9ff1fc96721369159da182305478105e9e916c60a14ddf79a8836a10bb7`.
