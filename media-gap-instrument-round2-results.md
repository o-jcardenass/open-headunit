# media-gap-instrument — round 2 results

**Candidate:** fix/media-gap-instrument-and-attribution @ `93354419`       **Baseline:** none built (no A/B this round; not requested by the brief)
**APK md5:** `0f8b79da22f4a212b1bcf5c7813b4df4` (candidate) / N/A
**Unit:** UNISOC MT50_YT610E4GFPSL_U, Android 14, TextureView view-mode, H.265
**Date:** 2026-08-20

## Setup notes

- Inventoried `hur-wifi-test-scripts/` per house rules. Used `build_hur.sh`, `run_unit_tests.sh` and
  `install_and_launch.sh` (`SKIP_BUILD=1` for the install step, since the build already ran).
  No script added or changed this round.
- **Settings never needed to change.** Round 1 left `settings.xml` at exactly the values round 2
  needs (`log-level=2`, `wifi-connection-mode=3`, `view-mode=1`, `enable-audio-sink=true`,
  `debug-video-fault-injection=0`) — confirmed with `diff` against a fresh backup taken at the start
  of this round, zero delta. `set_hu_prefs.sh` was not invoked.
- `cmd connectivity airplane-mode disable` on the phone reliably brought WiFi back but left Bluetooth
  off; an explicit `svc bluetooth enable` was needed afterward. Same shape as the WiFi-side quirk
  `TESTING-TEMPLATE.md` §7a already documents for this phone, just on the other radio — worth adding
  there if this recurs.
- **R2's screen setup needed a correction mid-round.** The phone was staged on a paused Spotify
  screen before the window opened; Spotify's screen legitimately drives Android Auto's video channel
  closer to silent (a different, and separately interesting, fault shape from Maps' trickle), so it
  would not have been a valid re-run of round 1's R2. Corrected to a stationary Google Maps screen,
  full screen, no navigation, before starting the 3-minute clock. No capture time was spent on the
  Spotify staging; nothing about it appears in the numbers below.
- `p2p-wlan0-9` → `p2p-wlan0-10` appears in the capture, but entirely inside the first 1s of launch
  (`WifiDirectManager: startNativeAaQuietHost() requested. Removing old group if any...` tearing down
  a stale group from a previous round, then creating the round's actual group), before `createGroup
  SUCCESS` ever fires. One `createGroup SUCCESS`, one SSL handshake, for the whole ~11.5-minute
  capture. Read against the `native-aa-5288` round's own note that the discard rule is better applied
  as "a second `createGroup SUCCESS`" specifically, this was not treated as contamination.
- One `MATCH! Starting AapService` line appears, from the phone's own Bluetooth reconnect at launch,
  with zero group churn attached to it — the same benign pattern the `native-aa-5288` round
  characterized. Not treated as contamination either.
- R1 and R2 were run back-to-back on one live session, per the brief's instruction to reuse it
  (§7a: reuse a live link). Session formed 53s after launch (well inside the 90s allowance).

## R0 — build and unit tests

**PASS**

- `assembleGithubDebug`: clean build.
- `testGithubDebugUnitTest`: all green, **594** total (matches expectation).
- `LinkGapMonitorTest`: **17** (expected 17). `StationCoexistencePolicyTest`: **8** (expected 8).
- Named tests confirmed present:
  - `LinkGapMonitorTest > a trickling idle screen says nothing either`
  - `LinkGapMonitorTest > the reporter's own waveform still prints against the new ceiling`
  - `LinkGapMonitorTest > the audio waveform a rig measured still prints`
  - `LinkGapMonitorTest > the link series keeps no ceiling`
  - `StationCoexistencePolicyTest > no branch prescribes anything`

## R1 — clean session, 10 minutes. Regression.

**PASS**

- Settings written: none (already correct, see Setup notes).
- Radio state: phone airplane mode on → head unit launched (12:20:44) → settled ~18s → phone
  airplane mode off, then explicit `svc bluetooth enable` → session formed 12:21:37 (53s after
  launch, well inside the 90s allowance).
- Discard-rule check: clean. Single `createGroup SUCCESS` (5GHz, `freq=5805`), single SSL handshake,
  single benign `MATCH!` line, no `Magic Garbage`, `p2p-wlan0` settled at `-10` for the whole run
  (see Setup notes on the pre-`createGroup` bump).
- Decisive log lines, over the 10-minute window (12:20:44–12:30:44):

  ```
  grep -ac "inbound link quiet"   -> 0
  grep -ac "inbound video quiet"  -> 0
  grep -ac "inbound audio quiet"  -> 0
  grep -ac "uplink blocked on"    -> 0
  ```

- Video throughput held steady throughout, ~44-46fps per 5s window (H.265, `c2.unisoc.hevc.decoder`).

## R2 — idle screen, 3 minutes. The point of the round.

**PASS**

- Window: 12:28:36–12:31:36, same live session as R1, Google Maps full screen, stationary, no
  navigation, untouched.
- `grep -ac "inbound video quiet"` over the whole capture (R1+R2 combined): **0**. Restricted to the
  R2 window specifically: also **0**.
- `grep -ac "Throughput over"` in the R2 window: **36** lines. FPS distribution across those 36
  samples: 39fps×1, 42fps×1, 44fps×1, 45fps×6, 46fps×27 — i.e. the picture kept moving at roughly
  39-46fps the whole time the instrument stayed silent. (Slightly under the brief's reference
  45-55fps range, but flat and steady — no stalls, no drops, `dropped=0`/`skipped=0` throughout.)

## R4 — the coexistence line, one grep.

**PASS**

```
grep -ac "Disconnecting the other network"                r1.txt   -> 0
grep -ac "This unit is connected to another WiFi network" r1.txt   -> 1 (W level)
```

Full line:

```
08-20 12:20:45.491 W/OPENHU  (27876): [2] WifiDirectManager.logStationCoexistence | WifiDirectManager:
This unit is connected to another WiFi network on 5500 MHz while hosting the WiFi Direct group on 5805
MHz. One radio has to retune between the two, which can cost the projected video and audio a few
hundred milliseconds at a time. Measured units have run clean in this state, so read this as context
for a report rather than as something to change.
```

Ends exactly on "read this as context for a report rather than as something to change," as expected.

## Anything the brief did not ask about

Nothing beyond what's already folded into Setup notes (the Spotify-vs-Maps correction and the
phone-side Bluetooth quirk). No other anomalies surfaced.
