# Audio focus (issue #744/#681) — round 6 results

**Candidate:** `fix/audio-focus-pauses-bt-source` @ `c0f3ec12`       **Baseline:** `origin/main` @ `c318b4e4`
**APK md5:** `9a8e0c1f7bc3cb1f98cca48099065f9a` (candidate). No baseline APK built — no run this round
needed an A/B comparison; A4a serves as the in-branch positive control instead.
**Unit:** headunit `27870808938846` (UNISOC MT50_YT610E4GFPSL_U, Android 14) / phone `4f4027e9`
(Redmi M2007J20CG, POCO X3 NFC)
**Date:** 2026-08-08

## Setup notes

- Fetched cleanly, no rewrite: `git fetch fork && git checkout -B fix/audio-focus-pauses-bt-source fork/fix/audio-focus-pauses-bt-source` landed exactly on `c0f3ec12`.
- **Scripts used:** `hur-wifi-test-scripts/build_hur.sh` for the APK. No existing script ran the
  unit tests, so `run_unit_tests.sh` was added this round (companion to `build_hur.sh`, same
  style/JDK path) — left in place for future rounds.
- **No physical USB accessory path exists on this rig.** `dumpsys usb` shows the head unit in USB
  *device* mode only (`host_connected=false`) — it is talking to the PC for adb, not to the phone.
  There is also no shared regular WiFi network both devices can join (only each other's P2P groups
  and one auto-join-disabled home SSID visible from the head unit). The only usable transport was
  therefore the existing Native AA wireless setup (`wifi-connection-mode=3`), which the brief
  explicitly allows ("any transport is fine").
- **A3's literal method (USB, head-unit Bluetooth off) does not work on this rig, but not for the
  reason the brief guessed.** Disabling the *head unit's* Bluetooth radio (`svc bluetooth disable`)
  gets silently reverted — `AdapterState` shows `OffState → ... → OnState` via `USER_TURN_ON` about
  14 seconds later, reproduced twice, once with zero other adb activity in between. Grepped the
  full app source for any `BluetoothAdapter`/`.enable()` call: **none exists** — this is not our
  app doing it, it is something OS/OEM-level on this particular unit (`head-unit-make: Royal
  Enfield`), consistent with this rig's known history of other locked-down Bluetooth/hotspot
  behaviour. Confirmed harmless workaround: disabling **the phone's** Bluetooth instead drops the
  head unit's A2DP link (`A2dpSinkStateMachine` → `STATE_DISCONNECTED`) while leaving the already-
  established Native AA TCP session (port 5288, confirmed via `netstat`) completely unaffected —
  its own BT socket is already closed by the time the WiFi handoff completes, exactly as
  `CLAUDE.md` describes. Used this for A3 and A4b. Worth adding to the standing template for
  future rounds on this rig.
- To force a **fresh** audio channel (needed because the existing channel from a prior run doesn't
  re-evaluate focus on every track, only on channel open), force-stopped and relaunched Spotify on
  the phone (`am force-stop com.spotify.music` then `monkey -p com.spotify.music -c
  android.intent.category.LAUNCHER 1`) rather than relying on media keys alone.
- Every settings write used the `run-as` + pushed-script method from `TESTING-TEMPLATE.md` §1
  (inline `sh -c` with embedded sed continues to have quoting problems over adb; a small script
  pushed and run locally on-device is reliable).
- Restored the original `shared_prefs/settings.xml` (backed up before any change) at the end of
  the round, including `log-level` back to its prior value.

## A0 — build and unit-test gate

**PASS.** `testGithubDebugUnitTest`: all green, including `PlaybackFocusPolicyTest` at **14**
assertions (brief said "13" — one more than expected, not a discrepancy worth chasing).
`assembleGithubDebug` succeeded; candidate APK md5 `9a8e0c1f7bc3cb1f98cca48099065f9a`, confirmed
identical to the md5 read back from the installed package on-device.

## A1 — is this rig an A2DP sink?

**Positive — A2 and A4a are live.** `Enabled Profile Services` lists `A2dpSinkService`, and
`A2dpSinkStateMachine` for the phone's device address showed `state=Connected`. `bt_btif` logged
`A2DP Stream opened` / `Stream started` for the phone's address, and the accompanying
`requestAudioFocus()` came from `com.android.bluetooth`'s `A2dpSinkStreamHandler` — the exact
mechanism the branch exists to work around.

## A2 — Automatic, media link up: focus left alone

**PASS.** `playback-focus-mode` absent (reads Automatic), `enable-audio-sink=true`,
`static-audio-focus=false`, phone Bluetooth on and A2DP-connected.

- `AapAudio: AA audio started (AUDIO) - leaving system audio focus alone (mode=AUTO,
  bluetoothMedia=true, latched=false)` — exactly one occurrence.
- Zero `acquiring transient system audio focus` anywhere in the 82-second capture.
- Exactly one `Media Start Request AUDIO`, zero `Media Sink Stop Request: AUDIO`, continuous
  `state=PLAYING` for the whole window.
- One `AudioTrackWrapper.createAudioTrack` at start, zero `AudioTrackWrapper thread finished.`
  before the run ended.

## A3 — Automatic, no media link: focus still taken (#658 intact)

**PASS**, via the phone-side Bluetooth-off method (see Setup notes).

- `AapAudio: AA audio started (AUDIO) - acquiring transient system audio focus (mode=AUTO,
  bluetoothMedia=false)` on a fresh channel opened with the phone's Bluetooth off.
- Matching `AapAudio: last AA audio channel stopped - releasing transient system audio focus` when
  the channel closed (Spotify force-stopped).

## A4 — overrides and persistence

**(a) Always, media link up — positive control. PASS, reproduces #744 on purpose.**

`acquiring transient system audio focus (mode=ALWAYS, bluetoothMedia=true)`, then churn matching
the reporter's original log almost exactly:

| Event | Offset from acquire |
|---|---|
| `state=PAUSED` | +167 ms, +213 ms, +234 ms (three cycles) |
| `Media Sink Stop Request: AUDIO` | +3.43 s, +3.47 s (two full cycles observed) |

Churn repeats every ~3.4-3.5 s. This did **not** occur under Automatic in A2 — the contrast is the
proof the fix addresses the real mechanism.

**(b) Never, no media link. PASS.**

`AapAudio: AA audio started (AUDIO) - leaving system audio focus alone (mode=NEVER,
bluetoothMedia=false, latched=false)` — no acquire, confirming Never overrides even a clean
negative probe.

**(c) Setting is read, honoured and persistent. PASS.**

- `playback-focus-mode` read back as `2` after the A4b run, unchanged after a full
  `am start` → 10s → `am force-stop` cycle.
- With `static-audio-focus=true`: a channel opened (`Media Start Request AUDIO` fired once) with
  **zero** `acquiring`/`leaving` lines of any kind — the dynamic path never runs when static mode
  owns focus, confirming the gate holds regardless of `playback-focus-mode`.

## A5 — self-heal latch

**UNTESTABLE on this rig**, per the brief's own rule: A2 showed `bluetoothMedia=true` (the probe
works cleanly here), so the blind-probe condition the latch exists for cannot be constructed from
settings alone. Coverage is `PlaybackFocusPolicyTest`.

## A6 — regressions

- **Audio sink off:** `enable-audio-sink=false` — the AUDIO channel never even opened (no `Media
  Start Request AUDIO` at all), and the pre-existing log line `AapService.requestPermanentAudioFocus
  | Audio Sink disabled - skipping permanent audio focus request` fired as expected. **PASS.**
- **Static audio focus untouched:** covered by A4c's static-focus run above — zero
  `acquiring`/`leaving` lines with the setting on. **PASS.**
- **No FATAL EXCEPTION:** none in any of this round's six capture files.
- **Navigation/assistant channel — inconclusive, flagged rather than scored.** Triggered turn-by-
  turn guidance via `google.navigation:q=...`, confirmed it routed through the AA session
  (`mCurrentFocus` on the phone showed Maps' `GhostActivity`, and a head-unit screenshot showed live
  turn guidance with an "Unmuted" indicator and ETA card). You then manually triggered guidance
  again. In neither case did `AapControlMedia` ever log a `Media Start Request` for `AUDIO1` or
  `AUDIO2` — only the one-time channel *setup* at connect (`Media Sink Setup Request: 1 on channel
  AUDIO1` / `AUDIO2`, both `Config response: status: HEADUNIT`). So this round cannot say whether a
  navigation prompt would trip the focus/latch logic, only that no prompt was observed to open
  either channel on this rig in this session. Not treating this as a PASS or FAIL — it's a real gap
  in this round's coverage, not a property of the branch.

## Summary

| # | Result |
|---|---|
| A0 build/unit-test gate | PASS (14/14 `PlaybackFocusPolicyTest`, full suite green) |
| A1 A2DP sink check | POSITIVE |
| A2 Automatic + link up | PASS |
| A3 Automatic + no link | PASS (phone-side BT-off method) |
| A4a Always (positive control) | PASS — reproduces #744 |
| A4b Never + no link | PASS |
| A4c persistence + static gate | PASS |
| A5 self-heal latch | UNTESTABLE on this rig |
| A6 audio-sink-off / static-untouched / no crash | PASS |
| A6 navigation/assistant channel | INCONCLUSIVE — channel never observed to open |

**The three numbers the brief asked for:**

1. **Rig is an A2DP sink** — yes, confirmed via `A2dpSinkService` + `A2dpSinkStateMachine
   state=Connected`.
2. **`bluetoothMedia=` tracked the link correctly in both directions** — `true` with the link up
   (A2), `false` with it down (A3/A4b), and it changed within the same session as the link state
   changed, not just at connect time.
3. **Always reproduces the cutting, Automatic does not** — yes, cleanly, with timing that matches
   the original reporter's log (~3.4s cycle here vs. the reporter's own ~3-4s pattern in the
   original #744 log).

`the adapter would not report its A2DP state` never appeared. A5 was not reachable on this rig.
A4c confirmed the Settings control's underlying value behaves correctly; no screenshot of the
control itself was taken (optional, not chased).
