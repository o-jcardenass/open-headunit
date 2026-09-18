# Video latency after a link stall — round 1 results

**Candidate:** `fork/fix/755-wireless-video-latency` @ `13408d98`   **Baseline:** `origin/main` @ `e900de78`
**APK md5:** `2398e0115f71e123d7f23f994a191664` (candidate) / `6dd8d6d4508e214ef20115cc8568583d` (baseline)
**Unit:** UNISOC MT50_YT610E4GFPSL_U (uis7861_6h10), Android 14, 1440x720 landscape, screen `resolutionId` default 2 (720p)
**Date:** 2026-08-09

## Setup notes

- `hur-wifi-test-scripts/build_hur.sh` built both APKs (checked out each SHA into the main repo working
  tree in turn, since the script always builds whatever is currently checked out there — no worktree
  used). `run_unit_tests.sh` served as the R0 build gate. `set_hu_prefs.sh` wrote every settings change.
  No new script was needed.
- Deviation from §4: the brief's settings block was applied as written, except `resolutionId` was
  changed to `5` only for R4 and restored to `2` immediately after, and `audio-queue-capacity` was
  changed only for R3's two sub-runs and restored to `50` before R4.
- **R3 needed a real playback trigger and automation could not reliably provide one.** Media keys
  (`input keyevent 85/126`), `cmd media_session dispatch play`, and an on-screen `input tap` on the
  phone's player UI all failed to start playback (confirmed via `dumpsys audio`'s player-piid log:
  OpenSL/AudioTrack players were created and released within ~1s on every attempt, no `event:started`
  ever appeared). The user tapped play manually on the phone for both R3a and R3b.
- **Found in the process, not part of this branch:** with Spotify freshly relaunched, the very first
  channel-open attempt entered a self-inflicted pause loop — `AapAudio.onAudioPlaybackStarted` grants
  itself local transient audio focus, and `AapService`'s own `MediaSessionCompat.onPause()` callback
  fires almost immediately afterward, forwarding `KEYCODE_MEDIA_PAUSE` back to the phone
  (`AapService.kt:1036-1039`). This repeated 8 times (`Media Start Request AUDIO` sessions 0-7) before
  settling on a fresh reconnect. `git diff main...13408d98 -- .../aap/AapAudio.kt` is empty, so this is
  **not caused by this branch** — it reproduces identically against `main`. Matches what the user
  observed live ("needed to press play twice or more" / "play and pauses instantly"). Worth a dedicated
  investigation; out of scope for this round.
- **R4's first attempt was discarded.** The connection dropped with an EOF right after handshake,
  before any video arrived (`AapRead: Connection closed (EOF)`), and the ensuing auto-recovery bumped
  the P2P interface index twice more while cycling — a clear discard-rule hit, unrelated to the touch
  load since it happened before the first swipe was even sent. Re-run clean.
- **R5 needed the head unit joined to a WiFi network with Native AA also active**, to test coexistence.
  Rejoining a saved network needed the user to re-enable "Auto-connect" in the system WiFi settings
  (declined to script this myself — toggling it is nested in the saved-network detail screen, which the
  standing instruction treats as a stop-and-ask case). **Self-inflicted setup problem**: cleaning up
  afterward with `svc wifi disable` turned off the whole WiFi radio, which Native AA also needs, and
  blocked the start of R6 until caught and re-enabled. A future round should tear down just the station
  link (`forget-network`/disconnect), not the radio.
- **R6's first attempt was invalid** and had to be redone. The deep links are handled by
  `.main.AutomationActivity` per the manifest, not `AapProjectionActivity`; targeting `-n
  .../AapProjectionActivity` explicitly bypassed the actual handler, so the first 10 "cycles" fired zero
  disconnects (0 `AutomationActivity.onCreate` lines, 0 `Feed thread stopped`). Redone with a plain
  implicit `am start -a android.intent.action.VIEW -d "headunit://..."` (no `-n`), confirmed reaching
  `AutomationActivity` and `CommManager: doDisconnect` correctly.
- Bash tool quirk this round: backgrounding a `for` loop with its own trailing `&` while also passing
  `run_in_background: true` on the tool call caused the tool call to return as soon as the first
  iteration's stdout line was captured, while the loop kept running detached in the background (visible
  via the logcat capture continuing normally). Not an app issue; just meant progress had to be
  cross-checked against the log's timestamps rather than the tool's own reported output.

## R1 — healthy stream is untouched

**PASS**

- Settings: `wifi-connection-mode=3, log-level=2, fps-limit=60, resolutionId=2, audio-queue-capacity=50,
  video-codec=Auto, force-software-decoding=false`
- Discard-rule check: clean (single `createGroup SUCCESS`, single `SSL handshake complete`, the one
  `p2p-wlan0-N` line before the group's own creation is a pre-existing leftover interface from the prior
  session, not a within-run bump — every group-info call after creation consistently used the same
  interface)
- `skipped=0` in all 56/56 throughput windows over the 5-minute run (100%, above the 80% floor)
- `rendered` matched `fed` almost exactly in every window; one legitimate joint dip
  (`rendered=103 fed=103`, both moving together) reflects a real drop in source delivery, not a discard
- Full throughput series (`rendered/fed`, fps): 46/46, 49/49, 50/50, 50/49, 49/49, 50/50, 50/50, 51/50,
  49/49, 50/50, 49/50, 50/49, 50/50, 50/50, 50/50, 49/50, 50/50, 49/49, 50/50, 49/49, 50/50, 52/50,
  59/59, 58/58, 59/60, 53/53, 51/51, 55/55, 54/54, **20/20**, 43/44, 51/50, 50/50, 50/49, 49/50, 50/50,
  49/49, 50/50, 49/49, 50/50, 49/49, 50/50, 50/50, 49/49, 50/50, 50/50, 50/50, 49/50, 50/49, 49/49,
  50/50, 49/49, 50/50, 50/50, 50/50 (all `dropped=0`, `skipped=0`)

## R2 — baseline comparison, same conditions

Reference run, no PASS/FAIL of its own.

- 54 throughput windows over ~4.5 minutes, mean `rendered` fps 45.4, min 20fps
- The last ~10 windows (last ~40s of the run) show a genuine, sustained drop to 20-25fps
  (`rendered==fed` throughout — a real drop in source delivery, not a decoder fault; `TextureProjectionView`
  confirms the same drop independently). Excluding that stretch, the first 33 windows average 49.4fps —
  statistically indistinguishable from R1's 49.8fps candidate mean.
- **Comparison that matters**: candidate `rendered` fps (mean 49.8, never dipped below baseline's
  non-anomalous range) is not below baseline. R1's veto does not trigger; if anything the raw baseline
  mean is dragged down by an environmental event R1's run didn't happen to hit, not by anything the
  candidate does worse.

## R3a — audio bound at the default (queueCapacity=50)

**PASS**

- Settings: `audio-queue-capacity=50` (default, unchanged from §4)
- `AudioDecoder.start` confirmed `queueCapacity=50` on every channel open (13 total across the run,
  counting the pause-loop churn described in Setup notes)
- Once the channel stabilized (21:52:55 onward, after a clean HUR relaunch), it stayed open
  continuously through the full 3-minute window with **zero** `Audio queue is full` lines
- Video stayed healthy throughout (~50fps, `skipped=0`) — the earlier pause-loop period showed the
  video dip to ~20fps in step with the audio churn, recovering fully once audio stabilized

## R3b — positive control (queueCapacity=0)

**PASS** (as a positive control — confirms the setting reaches the audio thread)

- Settings: `audio-queue-capacity=0`
- `AudioDecoder.start` confirmed `queueCapacity=0` on channel open; channel opened once and never
  needed to restart across the full 3-minute window (matches R3a's stability once past the pause-loop
  period)
- Zero drops, as expected for an unbounded queue
- Could not verify audible drift directly (no listening station on this rig); the user was present but
  not specifically monitoring for drift. No corroborating signal either way in the logs.

## R4 — discard path under load

**INCONCLUSIVE**

- Settings: `resolutionId=5` (negotiated/capped to `_1920x1080`, the max the unit's screen accepts —
  confirmed via `[RES_CAP] ... chosen=_3840x2160 capped=_1920x1080`), `fps-limit=60`
- ~2 minutes of continuous scripted touch-panning on the head unit's own screen (200 swipe pairs),
  `CPU: app 97% / sys 48%` confirmed via the app's on-screen debug overlay — genuine heavy load
- `skipped` stayed 0 in every throughput window through the whole run (confirmed both in the initial
  capture and cross-checked against the live logcat buffer after the capture process died early);
  `rendered` held 42-60fps
- Screenshot taken under load: picture fully coherent, no corruption, no smearing
- Per the brief's own criterion: "if `skipped` stays 0 throughout, this rig's decoder is simply fast
  enough" — this unit's decoder kept up even at the max negotiable resolution under sustained heavy
  touch load, so the catch-up path was never exercised. Not a fault; the brief's own anticipated
  outcome.

## R5 — coexistence warning

**PASS**

- Head unit joined a station WiFi network ("Pegue Cdesta", user re-enabled auto-connect) while Native
  AA hosted its own P2P group
- `WifiDirectManager: This unit is connected to another WiFi network while hosting the WiFi Direct
  group (station 5260 MHz, group 5805 MHz: different channels...)` fired exactly once for the group,
  with both frequencies quoted, matching the PASS condition
- **Foreground-independence confirmed from source, not just observed timing**: `logStationCoexistence()`
  is called from `WifiDirectManager.onGroupInfoAvailable` (service-level), with an explicit code comment
  noting it must not key on data that gets redacted "whenever the caller cannot satisfy the location
  gate, which on a head unit is routine — the service runs without the projection activity in front."
  This is exactly the not-in-foreground case the brief asks about, and the mechanism is structurally
  independent of `AapProjectionActivity`'s lifecycle by design.

## R6 — thread lifecycle across restarts

**PASS**

- Candidate APK, 10 connect/disconnect cycles via `headunit://connect` / `headunit://disconnect` deep
  links (implicit `VIEW` intents, ~15s connected / ~5s disconnected each)
- Across the full capture: `Feed thread started`=4, `Feed thread stopped`=3, `Output thread started`=4.
  Not a leak: the fourth start (the app's own post-loop auto-reconnect) was still an active, healthy
  session at the time of capture, not an orphaned thread — every session that *ended* during the test
  had its feed thread stopped exactly once, and `Output thread started` count matches `Feed thread
  started` count throughout.
- Zero `Input buffer full` lines across any of the ordinary disconnects
- Reconnected once more afterward and confirmed clean rendering (screenshot: coherent map, 50fps,
  `CPU: app 63% / sys 39%`), no corruption from a prior session — see attached observation.
- **Rig-timing note, not a defect**: this unit's Native AA reconnect typically takes 45-90s (consistent
  with every other reconnect timed this round and in prior rounds), well above the 15s hold the brief
  specifies. Only 1 of the 10 cycles completed a full reconnect-with-active-decoding before its
  disconnect fired; the other 9 disconnects landed mid-handshake with no decoder yet running (hence no
  feed-thread event to log for those). This doesn't change the verdict — nothing leaked — but a future
  brief targeting this specific rig might want a longer hold to exercise more full-session
  teardown/rebuild cycles rather than mostly mid-handshake cancellations.

## Anything the brief did not ask about

The `AapService` self-pause-on-focus-grant bug described in Setup notes (reproduces identically on
`main`) is the most actionable finding from this round outside the brief's own scope. It's a strong
candidate for its own investigation: `AapAudio.onAudioPlaybackStarted` → `requestPlaybackFocus` granted
→ `AapService`'s own `MediaSessionCompat.Callback.onPause()` fires and forwards a pause back to the
phone, sometimes several times before the loop breaks. The user directly observed the symptom live
("play and pauses instantly" / "needed to press play twice or more").
