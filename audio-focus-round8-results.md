# Audio focus — round 8 results

**Candidate:** `fix/audio-focus-pauses-bt-source` @ `26032e65`       **Baseline:** none (settings-only positive controls, per brief)
**APK md5:** `4d246ca565d6ecefe8c07512f3c00ec6`, confirmed identical to the installed package.
**Unit:** headunit `27870808938846` (UNISOC MT50_YT610E4GFPSL_U, Android 14) / phone `4f4027e9`
(Redmi M2007J20CG, POCO X3 NFC)
**Date:** 2026-08-08

## Setup notes

- History reset per the brief's instructions: `git fetch fork --prune --prune-tags` then
  `git checkout -B fix/audio-focus-pauses-bt-source fork/fix/audio-focus-pauses-bt-source`.
  Log showed exactly the three commits named in the brief (`26032e65`, `a2381b46`, `d2dff1df`).
- **The A2DP link's behaviour this round was the opposite of round 7's.** Round 7 lost the link and
  it never came back for the rest of the round. This round it would not come up at all through the
  brief's prescribed method (a single airplane-mode cycle) or any of several substitutes tried over
  roughly 15 minutes (phone Bluetooth adapter enable, a full disable/enable cycle, forcing Spotify
  playback to try to trigger an on-demand profile connect) — `A2dpSinkService` stayed at
  `Active Device = null` throughout. R1-R6 were provisionally marked INCONCLUSIVE on that basis.
  Then, during the R7 setup (an unrelated reconnect cycle with the phone's Bluetooth re-enabled after
  a different attempt), the link came up on its own with no different technique applied. R1-R6 were
  re-run successfully once it did. **Net effect: A2DP connectivity on this rig is not just flaky
  run-to-run as round 7 found, but can fail to come up for extended periods for no reason traceable
  from the app or adb-visible Bluetooth state, then recover with no reason traceable either.** Worth
  escalating past "known quirk" if a future round depends on it being reliable.
- **Deviated from the brief's airplane-mode instruction.** `am broadcast -a
  android.intent.action.AIRPLANE_MODE` is blocked by a `SecurityException` (`Permission Denial: not
  allowed to send broadcast ... from pid=... uid=2000`) on this phone's Android version — `settings
  put global airplane_mode_on` alone does not toggle the radios without the broadcast. Used the
  already-documented phone-Bluetooth-adapter lever instead (`svc bluetooth enable/disable`) for every
  link-state change this round, consistent with §7a.
- **Once a link-dependent session was live, it survived four consecutive `force-stop` + settings-write
  + relaunch cycles without dropping A2DP** — R4 through R6/R1 were all run back-to-back on the same
  underlying Bluetooth connection, confirmed via `A2dpSinkService` before and after each cycle. This
  is the first round where that was confirmed explicitly rather than assumed.
- **New phone-side observation:** `svc bluetooth disable` on the phone was seen to silently self-revert
  back to `state: ON` within roughly 45 s on one occasion, with no adb command issued in between. A
  second `disable` a few minutes later held for the full observation window (12+ s checked). Round 6
  established the head unit's own Bluetooth self-reverts in ~14 s (OS/OEM-level); this looks like a
  related but distinct phenomenon on the phone side, not yet characterised. Flagging for §7a
  consideration rather than chasing it this round.
- R7 could not be produced as specified (static focus grab with the phone's Bluetooth off *before*
  connect) because Native AA's handshake itself requires Bluetooth — a fresh connect attempt with the
  phone's radio off never completes (confirmed: no session after 50 s, versus 10-50 s on every
  successful attempt this round with Bluetooth on). An established-session-then-drop-BT-then-deep-link-
  reconnect substitute was tried and got `bluetoothMedia=false` confirmed on the head unit side, but
  did not retrigger a fresh `AapService.requestPermanentAudioFocus` decision (no new SSL handshake
  followed the `disconnect`/`connect` deep links). Marked INCONCLUSIVE on hardware; the equivalent
  logic path is unit-tested (`PlaybackFocusPolicyTest`: "auto takes permanent focus when no bluetooth
  media link is up", one of R0's 20 green tests).
- R8 has no scriptable trigger — export/import live behind an in-app dialog and (on this SDK) a
  system Storage Access Framework document picker, with no deep link or exported action reaching
  either. Departed from house rule 2 for this one run only, driving the UI directly
  (`uiautomator dump` + `input tap`) since nothing else could reach the feature under test. Settings
  were still never hand-edited through the app's own settings list — only through `settings.xml` — so
  house rule 3 held.
- Settings backed up before any change (`/data/local/tmp/round8-settings-backup.xml`) and restored at
  the end via the pushed-script method (`run-as $PKG sh -c 'cp ...'` failed inline again with the same
  "cp: Needs 1 argument" error round 6 first hit; the file-script method worked immediately, as
  expected — this is now confirmed twice as the reliable path and once as inline-unreliable both
  times it was tried this round).
- Deleted the one exported `HUR`-style settings JSON this round produced from
  `/sdcard/Download/` and the `uiautomator`/screenshot scratch files from `/sdcard/` afterward.
- Scripts used: `run_unit_tests.sh`, `build_hur.sh` unchanged from prior rounds. No new script added
  to `hur-wifi-test-scripts/` this round; all settings writes used a small generalised
  `set_pref.sh <key> <type> <value>` pushed to `/data/local/tmp/`, replacing the one-key-per-script
  pattern prior rounds used — same underlying sed technique, just parameterised.

## R0 — build gate

**PASS.** `PlaybackFocusPolicyTest`: 20/20 green (confirmed by name — the 6 new tests the brief
predicted are present: static-mode permanent-grab tests, the always/never-override-on-permanent-path
test, and the "two paths are exact complements" test). Full `testGithubDebugUnitTest` suite green.
APK md5 `4d246ca565d6ecefe8c07512f3c00ec6`, confirmed identical to the package installed at
`/data/app/.../base.apk` on the head unit.

## R1 — path 1 unchanged after rebuild

**PASS.** Settings: `static-audio-focus=false`, `playback-focus-mode=0`, `log-level=1`.

- Decisive line: `AapAudio: AA audio started (AUDIO) - leaving system audio focus alone (mode=AUTO,
  bluetoothMedia=true, latched=false)` at `13:29:57.797`.
- No `sendPassThroughCommandNative` / AVRCP PAUSE (opcode id 70) in the 60 s window.
- `btavrcp_play_position_changed_callback` fired 50 times over the window with no gaps — continuous
  playback confirmed, not just an absence-of-pause inference.

## R2 — #802-style repro attempt, Automatic

**PASS (no repro).** Settings: same as R1, live link, media already playing.

- Lock (`input keyevent 223`) held 10 s, unlock (`input keyevent 224` + `wm dismiss-keyguard`), then
  15 s observation.
- No `AA audio channel stopped` line anywhere in the window — the AA audio channel never closed
  during lock or unlock on this rig/Android-Auto-version combination, so path 1 never had anything to
  re-decide.
- `btavrcp_play_position_changed_callback` count grew from 50 (end of R1, same continuous capture) to
  116 by the end of R2's window, at the same steady rate — playback was continuous through the whole
  lock/unlock cycle.
- On this rig, under Automatic mode, the reported symptom (lock/unlock stops media) does not occur.

## R3 — #802-style positive control, Always

**PASS (bug reproduced, as the brief calls the expected outcome).** Settings: `static-audio-focus=false`,
`playback-focus-mode=1`, live link, same session continued from R1/R2 (settings changed, force-stop,
relaunch, fresh channel via Spotify restart).

- **Which path fired: path 1.** Decisive line: `AapAudio: AA audio started (AUDIO) - acquiring
  transient system audio focus (mode=ALWAYS, bluetoothMedia=true)`, not the path 3 protocol lines.
- The churn was already running continuously before the lock/unlock sequence was even attempted —
  acquire/release cycling at a measured ~3.5-3.9 s interval (matching round 6's A4a within noise),
  driven purely by Always mode's per-channel grab, independent of any lock/unlock action.
- 12 AVRCP PAUSE events (opcode id 70) counted across the capture, continuing at the same cadence
  through and after the lock/unlock window with no visible change in pattern.
- **Conclusion: on this rig, Always mode reproduces a #802-shaped symptom, but the mechanism is the
  pre-existing per-channel churn (round 6's finding), not something specific to locking or unlocking
  the phone.** The lock/unlock action itself appears to be a red herring for this mechanism; if the
  reporter is on Automatic (the default), R2's clean result suggests this isn't the explanation for
  their report.

## R4 — path 2 gate, static + Automatic + live link

**PASS.** Settings: `static-audio-focus=true`, `playback-focus-mode=0`, live link, fresh connect.

- Decisive lines, both twins present: `AapService: Static Audio Focus - leaving system audio focus
  alone (mode=AUTO, bluetoothMedia=true)` and `CommManager: Static Audio Focus - leaving system audio
  focus alone (mode=AUTO, bluetoothMedia=true)`, both at connect (`13:21:07.931` /
  `13:21:08.674`).
- No AVRCP PAUSE (opcode id 70) in the 60+ s window after connect while Spotify played.
  `btavrcp_play_position_changed_callback` ticking steadily throughout.

## R5 — path 2 positive control (the headline run)

**PASS — prediction confirmed: exactly one pause, no cycle.** Settings: `static-audio-focus=true`,
`playback-focus-mode=1`, live link, fresh connect.

- Decisive line: `AapService: Static Audio Focus - acquiring permanent system audio focus
  (mode=ALWAYS, bluetoothMedia=true)` at `13:24:48.923`.
- **Pause count: 1.** A single AVRCP PAUSE passthrough (opcode id 70: `pressed:0` then `pressed:1`,
  i.e. one press-release cycle) fired at `13:24:48.947-948`, 24 ms after the acquire. The capture ran
  to `13:27:56.975` — **3 minutes 9 seconds, well past the 90 s the brief asked for** — with no second
  PAUSE at any point.
- The underlying `AudioManager` dispatch confirms the mechanism: `onAudioFocusChange(-1)` (permanent
  `AUDIOFOCUS_LOSS`, not `-2`/`LOSS_TRANSIENT`) sent to `A2dpSinkStreamHandler` at `13:24:48.928`,
  exactly matching the brief's read of the AOSP handler.
- **Secondary observation, not a failure:** ~16 s after the initial grab, once real A2DP audio started
  flowing (after a manual media-key press resumed Spotify), the head unit's own
  `A2dpSinkStreamHandler` issued a fresh `requestAudioFocus()` (`req=1`, i.e. plain `GAIN`, not
  transient) and — per the subsequent `onAudioFocusChange(-1)` dispatched to *our app's* listener —
  appears to have silently displaced our permanent grab. This did not produce a second AVRCP command
  to the phone (confirmed via the full passthrough-command grep above) and did not affect the
  pause-count result, but it means the "permanent" grab is not exclusive against a same-priority
  system requester once real audio starts arriving. Worth a look in a future round; out of scope for
  what R5 asked.
- Playback did not resume until a manual media-key press ~14 s after the pause — no autonomous
  un-pause was observed.

## R6 — path 3, protocol-driven grab

**INCONCLUSIVE for the GAIN check; PASS for the RELEASE-never-gated check.** Settings:
`static-audio-focus=false`, `playback-focus-mode=0`, live link (same session as R1).

- `AapControlService.audioFocusRequest | Audio Focus Request: RELEASE` fired naturally at connect
  (`13:29:43.410`), followed immediately by `AapAudio.requestFocusChange | Releasing audio focus`
  (`13:29:43.417`) — confirms RELEASE reaches `AapAudio` and is honoured, not silently dropped.
- No GAIN-type `Audio Focus Request: <REQUEST>` line ever appeared. Tried, in order: letting Spotify
  play normally (only produced path 1's own channel-open line, not a protocol-level request);
  `am start -a android.intent.action.VOICE_COMMAND`; a posted local notification
  (`cmd notification post`, though its `sound` was `null` — a caveat, not a clean negative);
  `input keyevent 231` (`KEYCODE_VOICE_ASSIST`); a media-key pause/play cycle to force a fresh channel.
  None produced a phone-side `AudioFocusRequestNotification`.
- Did not attempt placing an actual phone call — judged too disruptive/risky to script blind on a
  device that could dial a real number.
- Matches the brief's own stated expectation for this run.

## R7 — static mode unaffected without Bluetooth

**INCONCLUSIVE on hardware.** Settings: `static-audio-focus=true`, `playback-focus-mode=0`.

- Three approaches tried:
  1. Phone Bluetooth off *before* any connect attempt: Native AA's handshake itself requires
     Bluetooth, so no session ever formed (50 s wait, no `SSL handshake complete`).
  2. Established session (phone BT on, `bluetoothMedia=true` confirmed) → phone BT off (confirmed via
     `A2dpSinkService: Active Device = null`) → `headunit://disconnect` then `headunit://connect` deep
     links. This got the target condition (`bluetoothMedia=false`) but the deep links did not produce
     a new SSL handshake, so `AapService.requestPermanentAudioFocus` never re-ran under it.
  3. Fresh `force-stop` + relaunch with Bluetooth already off: same result as approach 1 — no session.
- The logical equivalent is covered by `PlaybackFocusPolicyTest`'s "auto takes permanent focus when no
  bluetooth media link is up" (one of R0's 20 green tests), so the code path is verified even though
  this round couldn't observe it live.

## R8 — settings survives backup and restore

**PASS.** No scriptable trigger exists for export/import (no deep link or exported action reaches
either — confirmed by reading `SettingsFragment.kt`'s export/import call sites, which are all behind
an `AlertDialog` and, on this SDK, a Storage Access Framework document picker). Drove the UI directly
for this one run, per house rule 2's "only add [non-scripted approach] when nothing fits" — settings
themselves were still only ever changed via `settings.xml`, never through the app's own list.

- Set `playback-focus-mode=2`, exported via Settings → Export settings → Save to app backup folder
  (which on this SDK/device routes through a Downloads SAF picker, not a true legacy path) → SAVE.
- **Confirmed present in the exported JSON:** `"playback-focus-mode": 2` in
  `open-headunit-settings-20260808-134652.json`.
- Reset to `playback-focus-mode=0` in `settings.xml`, force-stop, relaunched, imported the same file
  via Settings → Import settings → Choose backup file. Toast confirmed `Settings imported: 59 applied,
  0 skipped`.
- **Confirmed restored:** `settings.xml` read back `playback-focus-mode` value `2` after import.

## R9 — settings UI control visible under Static Audio Focus

**UNTESTABLE, per the brief's own instruction not to scroll.** Settings: `static-audio-focus=true`,
`enable-audio-sink=true`. Deep-linked to Settings (`extra_destination=0`, the default landing).

- The screen that loads shows the **General** category (Auto-Optimize Settings, Connection mode, App
  Language) — not Audio.
- Confirmed from the settings list source (`SettingsFragment.kt:1500-1597`) that the Audio category,
  and the `playbackFocusMode` control inside it, is preceded by General, Wireless/Connection,
  Automation, Navigation, Graphics/Video, and Input categories in the list order — several screens
  down from where the deep link lands, not adjacent to it.
- Per the brief: marked UNTESTABLE rather than scrolled past. A regression here would need either a
  destination id that lands directly on the Audio category, or a relaxation of the no-scroll rule for
  this specific run.

## Summary

| # | Result |
|---|---|
| R0 build/unit-test gate | PASS — 20/20 `PlaybackFocusPolicyTest`, full suite green |
| R1 path 1 regression | PASS — unchanged after rebuild |
| R2 #802-style repro, Automatic | PASS (no repro) — lock/unlock never closes the AA audio channel here |
| R3 #802-style positive control, Always | PASS (reproduced) — via pre-existing per-channel churn, not lock/unlock specifically |
| R4 path 2 gate | PASS |
| **R5 path 2 headline: pause count** | **PASS — exactly 1 pause, 0 repeats over 3m9s; prediction confirmed** |
| R6 path 3 GAIN | INCONCLUSIVE — never observed, RELEASE-never-gated sub-check PASS |
| R7 static mode without Bluetooth | INCONCLUSIVE — Native AA needs Bluetooth to connect at all on this rig |
| R8 settings backup/restore | PASS |
| R9 UI control placement | UNTESTABLE — Audio category not on the landing screen |

No crashes, no ANRs, no FATAL EXCEPTION in any capture this round.

**Headline for whoever reads this first:** R5 confirms the model — Static Audio Focus's permanent
grab produces exactly one AVRCP PAUSE and does not cycle, unlike the dynamic Always-mode path's
~3.5 s churn (round 6's A4a, re-confirmed independently in this round's R3). The #802-style report is
reproducible under Always mode but through the already-known churn mechanism, not a lock/unlock-specific
one, and does not reproduce at all under Automatic (the default) on this rig.
