# audio-stutter, round 1 results

- Candidate: `fork/fix/audio-stutter` `d96f17c19cf0f169c72b3f38091f92f93ac1bcb1`, APK md5 `616f5108b25898e78ece4d0d75d72d46`, tree `1a688ef3`. Baseline: `d5528b51fee45c13c5112c0a004c823224fbff7b`, APK md5 `cf40b9bb017a067b0c575eb55d5be734`, tree `df6679f8`. The md5s differ. DEX `FIXED_FRAMES`: baseline 0, candidate 1.
- Units: D-HP (API 17, over USB, driven on wireless adb) with D-MOTO (Gearhead 17.9 per the brief) as phone. Paired with the pr-1001-devserver-p2p round on D-HU and D-POCO.
- Date: 2026-10-15.
- Evidence: release `rig-evidence-audio-stutter`, asset `audio-stutter-round1-captures.zip`, sha256 `62e93049ca4fe62b0dcb616b218d56aa0f03ca987140c693ac74d766d6926600`.

## Round verdict: not requested by the brief

R0 PASS. R1 (baseline) PASS as a recorded baseline, with a fault present for R2 to remove. R2 (candidate) was not run, by the operator's decision.

## Setup notes

- Opening state: D-HP 100%, D-MOTO 54% battery. D-HP settings before: `audio-queue-capacity` 50, `audio-latency-multiplier` 16, `use-aac-audio` true, `static-audio-focus` false. The brief's worst-case keys were written (queue 20, multiplier 2) with the USB keys, all read back. D-HP's USB filter entries were present.
- The long MP3 `ohu-stutter-long.mp3` (20.8 MB, 4 x `ohu1008.mp3`) was built on D-MOTO; VLC was started with `file://` and the media channel opened (`Media Start Request AUDIO:` 2).
- R1 setup: the script's SSL wait (2 x 60 s) ended with `R1-nossl` at 23:22:54 because the USB session had not formed yet; the operator replugged and started the session by hand a moment later and `SSL handshake complete` was then seen once. The script resumed at step 5 (media). That is the one replug the brief allows.
- Brief erratum, swipe: `input swipe x1 y1 x2 y2 300` is invalid on API 17 (`Error: Invalid arguments for command: swipe`). The first hold (23:26:41) therefore carried no swipes and was stopped after 90 s. `scripts/env.sh` drops the duration argument; the hold was restarted at 23:28:32 with swipes (896,368 to 384,368 on a 1280x736 dump, one every 30 s, 18 in the run). The graded window is the last `R1-music` marker (23:28:29.974) to `R1-end` (23:38:35.154). R2 would have used the same swipe.
- Captures: counted on the capture with duplicate lines removed (D-HP's logcat repeats lines; 161221 lines became 89616).
- The operator pressed Enter for `heard` for audible faults and began before the music marker; 4 stamps fall before the window and are not counted.
- The operator asked to use Spotify instead of VLC. Not done for the graded holds: both arms need the same source, and a stream over the house WiFi adds variation the buffer change would be confounded with. Offered as an unscored extra pair.
- D-HP's wireless adb shared the house WiFi with the pr-1001 sessions. Locks: the hold ran under `/tmp/ohu-rig-dhp.lock`.
- Overnight the host adb server restarted and the USB topology changed; D-HP and D-MOTO were found back on the PC by USB. Restore at the end: D-HP settings.xml diff against its backup is empty (carriage returns stripped; adb on API 17 adds them), D-MOTO bluetooth enabled and stayon false, VLC stopped. D-HP still has the baseline build (its previous build was not recorded).

## R0

**PASS**

Two md5s differ; the installed baseline pulled back with md5 `cf40b9bb017a067b0c575eb55d5be734` (equal to the source); `ACTION_QUERY_STATE` `commit` `d5528b51fee4`. Candidate DEX `FIXED_FRAMES` 1, baseline 0.

## R1

**PASS**

Baseline, no PASS bar; preflight checks met. First `L-OUT`: `output AudioTrack requested=2400 effective=2229 staging=0 burst=480 minimum=2229 stableFloor=2400 maximum=3360` (2229 frames = 46.4 ms; the line prints before the window, so 0 `L-OUT` lines fall inside it). `L-DEC` channel 6: `codec=AAC_LC`, `latencyMultiplier=2`, `queueCapacity=20`. `L-ACC` 1, `L-SSL` 1, `L-WIFI` 0, `L-MIX` windows 59 (limit 55).

Recorded values (window 23:28:29.974 to 23:38:35.154):
- `loopGapMax` maximum 1263 ms (23:34:24.604, `writeMax` 582 ms); `writeMax` maximum 710 ms (23:37:05.974). Windows with `loopGapMax` above the 46.4 ms buffer: 47 of 59. Windows at 190 ms or more: 6.
- `AudioStutter:` lines 830: by cause `UNKNOWN` 766, `LINK_LATE` 42, `MIXER_STALL` 21, `PHONE_SOURCE_GAP` 1; by kind `COMPRESS` 737, `CONCEAL` 85, `STALE` 8. Last roll-up: `stutters=81 mixer=0 ... unknown=81 suppressed=63`.
- Channel 6 deltas: `concealedFrames` 170849, `staleFrames` 39502, `compressedFrames` 29769; `arrivalGapMax` maximum 833 ms.
- `heard`: 63 stamps in the window, 42 matched to an `AudioStutter:` line within [-2500 ms, +500 ms], 21 unmatched. The matched stamps' nearest causes were mostly `UNKNOWN` with kind `COMPRESS` or `CONCEAL`, with some `MIXER_STALL` and `LINK_LATE`.
- `PCM overflow channel=` 18. `clock drift=+1 ppm over 850s`. `disabled due to previous underrun` 0, `underrunframes=` 0. Video median `rendered=` 150 per window (116 windows).
- Phone: `Creating MediaSourceFlowController for` 0, so the phone's flow lines (`out of permits` 5, `queue` 0, `ACK` 0, `drop` 0) are not measured per the brief and read n/a; `Car connection state changed` 0 (a renamed string, "string not seen"); `FATAL EXCEPTION` 0.

Reading for R2: the baseline had 21 `MIXER_STALL` lines, 47 windows over its buffer and 63 heard stamps, so there is a fault for a larger buffer to remove. Most events are not tagged `MIXER_STALL` (766 `UNKNOWN`, mostly catch-up compression), which is a point for the review.

## R2

**UNTESTABLE**

Not run, by the operator's decision: the change under test is to be tested through a follow-up change instead. No candidate install, no hold. The reachability condition is met by R1 (see above), so a later run can be graded.

## Anything the brief did not ask about

- The brief's swipe command does not run on API 17 (Setup notes). The brief's `L-OUT` check cannot be read from inside the window because the line prints when the track opens, before `R1-music`.
- `L-ST` shows 92% `UNKNOWN`, which the brief's section 9 does not cover.
- The first connection needed an operator replug after the script's two waits; D-HP's USB link took longer than 120 s to form this time.
- Thermal: peak 73C and throttle delta 0.
