# self-mode-media-session — round 2 results

**Candidate:** `fix/929-self-mode-media-session` @ `d5d463d5`       **Baseline:** same branch @ `665332d8` (round 1's candidate)
**APK md5:** candidate `d07801b77d10c54b8d5c42b8a8194485` / baseline `28455e33b31ede223e76b84fc0a016d4`
**Unit:** D-HU (UNISOC MT50, Android 14) for A3/B2/C3/D1's ordinary session; D-POCO (POCO X3 NFC, Android 15, Gearhead 17.8.163804-release.daily) for every Self Mode run
**Date:** 2026-09-18

## Setup notes

- Scripts used: `run_unit_tests.sh`, `build_hur.sh` (candidate only; round 1's baseline APK was
  already saved as `candidate-665332d8.apk` and reused as this round's baseline), `set_prefs_runas.sh`
  for every settings write on both units, `restore_settings.sh` at the end to put both units back to
  their pre-round `settings.xml`.
- `D-HU had `enable-audio-sink=false` left over from an earlier, unrelated round.** Without it,
  `AudioSinkAnnouncementPolicy.announcesMediaAndSpeech` never announces the AUD/AU1 channels at all
  (`ServiceDiscoveryResponse.kt:170-206`), so the phone never opens a real media or speech channel and
  no audio-focus event of any kind can be observed regardless of what the candidate does. Set to
  `true` for A3/B2/D1's ordinary session and restored to `false` at the end; not in the brief's §4
  table, worth adding there for the next audio-focus round on D-HU.
- **Playing a phone-side app on its own screen does not route audio into an active Android Auto
  session, even while connected.** First attempt at A3 played Spotify directly on D-POCO's own
  screen; `AapTransport: inbound rate` stayed `audio=0kB/s` for the whole session despite Spotify
  visibly playing. Only playback started *from inside the AA-projected UI* (the sidebar media app
  icons on the head unit's own screen) opens the AUD channel and streams real bytes (confirmed
  `audio=157-187kB/s` once done this way). Any future "a track playing through Android Auto" run
  must start playback from the projection, not the phone's home screen.
- **No local music files exist on D-POCO any more** (round 1's 15-track Downloads queue is gone;
  `Music/` and `Download/` both came back empty). Spotify's offline "Liked Songs" content was used
  instead on both units for every run that needed real audio, launched via the AA sidebar icon on
  D-HU and via the phone's own mini-player (tap-resumed, since a media key routes to whichever app
  currently holds the OS media-button session, not necessarily Spotify) on D-POCO.
- **`auto-start-self-mode=true` confounds any run that needs to observe post-session behaviour**,
  because relaunching `MainActivity` immediately re-triggers Self Mode before an ordinary wireless
  mode can arm (discovered while first attempting C2). Left `false` throughout and used the explicit
  `com.andrerinas.openheadunit.ACTION_START_SELF_MODE` intent (`am start -a
  com.andrerinas.openheadunit.ACTION_START_SELF_MODE -p <pkg>`) instead for every Self Mode run.
- **`headunit://exit` is a full app/service teardown, not "end the current session."**
  `AapService.onDestroy`, `AapProjectionActivity.onDestroy` and `MainActivity.onDestroy` all fire
  within about 1s of the exit intent, which meant C2's post-rearm `createGroup SUCCESS` could never
  land in the same capture with that lever — the process is gone before the group finishes forming.
  `headunit://disconnect` ends the session but keeps the app and its log file alive, and is the
  correct lever for any run that needs to watch what happens right after a session ends. Worth adding
  to `TESTING-TEMPLATE.md`.
- The `/proc/net/tcp6` precondition check from round 1's correction was used and matched cleanly
  throughout (`:149D` present whenever the HU dev server was up).
- Log rotation is tied to `LogExporter`'s own capture lifetime, not process restart: D1's two
  `headunit://exit`-separated Self Mode sessions landed in the *same* `HUR_Log_*.txt` (confirmed by
  filename), so both decline lines were counted from one file rather than two as round 1's R6a did.

## R0 — build gate

**PASS**

- `run_unit_tests.sh`: **2178** tests, green (round 1 read 2168).
- `LocationSourceAnnouncementPolicyTest` = **3**, `SelfModeWirelessPausePolicyTest` = **4**,
  `PlaybackFocusPolicyTest` = **23**, `MediaSessionOwnershipPolicyTest` = **2**,
  `MediaKeyRoutingPolicyTest` = **8** — all match the brief exactly.
- `build_hur.sh`: candidate md5 `d07801b77d10c54b8d5c42b8a8194485`, copied out of `apks/` before
  anything else ran. Confirmed live on D-HU and D-POCO via `md5sum` before every run that needed it.

## A3 — an ordinary session still takes focus, candidate

**PASS**, with a discrepancy in the brief's own decisive-line table (see below)

- Settings (D-HU): `log-level=0`, `log-source=1`, `log-capture-enabled=true`, `gps-navigation=true`,
  `wifi-connection-mode=3`, `static-audio-focus=false`, plus `enable-audio-sink=true` (Setup notes).
- Radio state: D-POCO wifi+bluetooth disabled before D-HU launch, D-HU launched first
  (`p2p-wlan0-1` came up), D-POCO radios restored ~25s later, screen woken and confirmed at Maps' AA
  ghost activity.
- Discard-rule check: clean. One P2P interface used, one connect, one SSL handshake.
- Music started from the AA sidebar's Spotify icon on D-HU itself (Liked Songs, Shuffle Play).
  `AapAudio: AA audio started (AUDIO) - acquiring transient system audio focus (mode=AUTO)` then
  `AapAudio: Playback transient focus request result: GRANTED`. **Zero**
  `leaving system audio focus alone`. `AapTransport: inbound rate` showed `audio=157-187kB/s` once
  playback was live.
- **The brief's §5 line for "we asked Android for focus" (`Audio focus request result:`) never
  appears in this run**, and a bare `grep -c 'Audio focus request result'` reads zero. The actual
  grant came through `AapAudio`'s *other* entry point, `requestPlaybackFocus()` (fired from
  `onAudioPlaybackStarted` when real media bytes start flowing on the AUD channel), which logs the
  different string `AapAudio: Playback transient focus request result:`. Checked
  `git show 4dfa66a0 -- .../AapAudio.kt`: this commit touches only the gating (`isLoopbackSession`),
  not either log string, so both entry points and both strings predate this candidate — this is a
  brief-table gap, not a candidate regression. §2's own text already says the fix covers "the
  protocol path" and "the playback path" as two distinct mechanisms; §5 only names the protocol
  path's grant string. Worth adding the playback-path string to §5 for the next round that greps for
  it.

## B2 — an ordinary session still offers its GPS, candidate

**PASS**

- Same session as A3.
- `LOCATION sensor requested. Sending current fix immediately. sentOnWire=true` — one occurrence.

## A1 — Self Mode does not take focus from the local player

**INCONCLUSIVE** (paired with A1c, per the brief's own contingency)

- D-POCO, candidate, Spotify (offline "Liked Songs" content, phone's own screen) confirmed `PLAYING`
  before Self Mode launch (`ACTION_START_SELF_MODE`). Session ran ~90s.
- **Zero** `leaving system audio focus alone`, **zero** `Audio focus request result:`. Self Mode
  never announces a media/speech channel by design, so `onAudioPlaybackStarted`'s decline path can
  never fire; and Gearhead 17.8.163804 never sent an explicit `AudioFocusRequestNotification` GAIN
  over the wire in this configuration either — the only wire event was a boilerplate `RELEASE`
  (type=4) at connect. `Sending immediate AudioFocusNotification: STATE_LOSS (always-grant)` present
  (the reply mechanism itself is intact and unaffected).
- Per the brief: "both the decline count and the result count are zero... say so rather than reading
  it as a PASS."
- Observation, no verdict: Spotify's local playback continued the whole session uninterrupted (it
  advanced onto a new track partway through), never evicted.

## A1c — the control

**INCONCLUSIVE**, same reason, confirms A1

- Baseline `665332d8` installed and confirmed live (md5 `28455e33...`). Identical setup: Spotify
  playing before Self Mode launch.
- Identical pattern to A1: zero decline, zero grant, only the connect-time `RELEASE`. This is on the
  code *without* this round's gate, so the zero is proven environmental (this Gearhead build never
  sends a wire GAIN in Self Mode at all here), not something the candidate's `isLoopbackSession` gate
  suppressed. Per the brief: "A1 proves less than it looks and both should be reported together."

## A2 — static audio focus, candidate

**PASS**

- D-POCO, candidate, `static-audio-focus=true`.
- `AapService: Static Audio Focus - leaving system audio focus alone (mode=AUTO, bluetoothMedia=true,
  selfMode=true)`, `CommManager: Static Audio Focus - leaving system audio focus alone (...
  selfMode=true)`, and `Static Audio Focus active - skipping dynamic system focus request to prevent
  routing loss`. **Zero** `Audio focus request result:`.
- `static-audio-focus` restored to `false` immediately after.

## B1 — Self Mode withholds the GPS sensor

**PASS**

- D-POCO, candidate, `gps-navigation=true`.
- `Self Mode is projecting this device to itself, so Android Auto reads this device's location
  directly and the head unit GPS sensor is not announced - this is not a fault` present. **Zero**
  `LOCATION sensor requested`.
- Observation, no verdict: opened the dashboard's nav card — the car marker matched the unit's actual
  street position correctly.

## B1c — the control

**As expected.** Baseline `665332d8`, same setup: `LOCATION sensor requested. Sending current fix
immediately. sentOnWire=true` present, no withheld line.

## C1 — Self Mode runs no wireless stack

**PASS**

- D-POCO, candidate, `wifi-connection-mode=3`, `connection-modes` an empty `<set>` (confirmed
  `Settings.kt:892`'s `showsWifi()` returns true when the set is empty, satisfying "absent or
  contains wifi"). Session ran ~2m10s.
- `standing the wireless stack down` exactly once. **Zero** `createGroup SUCCESS`, **zero**
  `Attempting active poke to device`, **zero** `MATCH! Starting AapService`.

## C1c — the control

**As expected.** Baseline `665332d8`, identical settings: one `WifiDirectManager: 5GHz createGroup
SUCCESS!` during the Self Mode session — the defect this branch fixes, present on baseline and absent
on the candidate.

## C2 — the stack comes back when Self Mode ends

**PASS**

- Candidate reinstalled after C1c, confirmed live. Self Mode launched via `ACTION_START_SELF_MODE`,
  then ended with `headunit://disconnect` (see Setup notes on why not `headunit://exit`).
- `SelfMode: the Self Mode session ended; letting the wireless stack arm again` →
  `WifiLauncher: Initializing WiFi Mode: NATIVE` → `WifiDirectManager: 5GHz createGroup SUCCESS!`
  (0.6s later), in exactly that order.

## C3 — the stack comes back when Self Mode fails

**PASS** — the highest-value run in the round, exactly as the brief said.

- D-HU, candidate, `wifi-connection-mode=3`, Self Mode launched via `ACTION_START_SELF_MODE`. D-HU's
  AA is 17.3.662864 (legacy, pre-17.4), so it took the `WirelessServer`/broadcast-fallback path and
  had nothing to connect to; `SelfMode: nothing connected within 30000ms of the launch` →
  `SelfMode: the Self Mode launch timed out; letting the wireless stack arm again` →
  `SelfMode: Failed, timed out!`.
- Wireless fully re-armed afterward: `WifiLauncher: Initializing WiFi Mode: NATIVE`, a fresh 5GHz P2P
  group (`createGroup SUCCESS`), and D-POCO (sitting nearby, radios on) completed a full Native AA
  Bluetooth handshake and joined the group — not just the log line firing, an actual working
  reconnection to a real phone.

## C4 — the WiFi button still works

**INCONCLUSIVE**

- D-POCO, candidate, during a live Self Mode session. Pressing `KEYCODE_BACK` from the projection
  surface landed on Gearhead's own phone-side Settings screen, not OHU's home fragment; relaunching
  OHU's `MainActivity` only brought the (dimmed) `AapProjectionActivity` task back to front. OHU's
  home fragment and its WiFi button were never reachable while the Self Mode projection is up.
- Matches the brief's own anticipated condition exactly: "the button cannot be reached because the
  projection is in front... it is a rig fact, not a defect."

## D1 — round 1's R6, repeated

**PASS**

- D-POCO, candidate, two Self Mode sessions back to back (`ACTION_START_SELF_MODE`, then
  `headunit://exit`, twice). Both sessions landed in the same `HUR_Log_*.txt` (Setup notes).
  `media session left to the player on this device (Self Mode)` appears exactly twice total across
  the file (line 190 for session 1, line 4421 for session 2), one per session, not accumulating.
- D-HU then brought up as head unit with D-POCO as phone for an ordinary Native AA session on the
  same D-POCO install (radios cycled as in A3): `MediaSession: State updated to PLAYING,
  positionMs=0` returned on D-HU's log.

## Anything the brief did not ask about

- **The audio-focus dual-entry-point log-string gap (A3).** `AapAudio` has always had two separate
  paths to a real system-focus grant — the explicit wire protocol path (`requestFocusChange`, logs
  `Audio focus request result:`) and the playback-triggered path (`requestPlaybackFocus`, logs
  `AapAudio: Playback transient focus request result:`). Only the first is in the brief's §5 table,
  but ordinary Android Auto music playback on this rig only ever exercises the second. Any future
  round that greps for "we asked Android for focus" needs both strings or it will silently read a
  working session as zero.
- **D-HU's stale `enable-audio-sink=false`** is worth a standing rig-quirk entry: it silently makes
  an entire audio channel (and therefore an entire class of audio-focus test) unreachable until
  checked, with no error anywhere pointing at it.
- **`headunit://exit` vs `headunit://disconnect`** is a real, reusable distinction for
  `TESTING-TEMPLATE.md`: `exit` tears down the whole app/service (confirmed via `onDestroy` chain),
  `disconnect` ends only the session and keeps the process alive. Any run that needs to observe
  "what happens right after a session ends" needs `disconnect`, not `exit`.
