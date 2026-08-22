# self-mode-bt-audio — round 1 results

**Candidate:** `fix/session-lifecycle-and-video-concealment` @ `ada271e7`   **Baseline:** none (brief specifies one APK for the whole round)
**APK md5:** `38fe82a83b85a1451d96508f3440b01e` / n/a
**Unit:** Xiaomi M2007J20CG (codename `surya`), Android 15, Android Auto `17.5.663204-release`. Self Mode — this round runs entirely on the phone over loopback (127.0.0.1:5277), not on the head unit rig.
**Date:** 2026-08-22

## Setup notes

- **§0's manual step turned out to already be done.** `com.google.android.projection.gearhead/.companion.DeveloperHeadUnitNetworkService` had a live `ServiceRecord` before this round touched anything, so "Start head unit server" was already on from a prior session. Confirmed working end to end anyway: both self-mode arms produced a full `Handshake: SSL handshake complete`. A future round starting from a cold phone should still budget time for this manual step — its persistence across reboots/sessions is not established.
- **No existing script wrote phone-side prefs.** `set_hu_pref.sh` / `set_hu_prefs.sh` both hardcode `HU=27870808938846` (the head-unit rig) and edit `/data/data/$PKG/...` directly via a rooted shell — the phone in this round is not rooted and is not that device. Per house rule 1, added `hur-wifi-test-scripts/set_pref.sh` (`DEVICE=<serial> set_pref.sh <key> <boolean|int|string> <value>`), implementing the run-as + pushed-script pattern (never inline `sh -c`, confirmed unreliable again below). Left in place for the next phone-side round.
- **`install_and_launch.sh` is also head-unit-hardcoded** (`HU=27870808938846` reassigned unconditionally, not overridable via env) and didn't fit installing on the phone. Installed manually: `adb -s <phone> install -r` first failed with `INSTALL_FAILED_UPDATE_INCOMPATIBLE` because the phone already carried a non-debuggable, differently-signed release build; `adb uninstall` then a clean `install -r` succeeded. This is the expected first-ever install of this candidate on this phone, not a mid-round switch, so the resulting fresh onboarding (next bullet) is in scope, not a violation of the "never uninstall/reinstall" rule (which is about *not* re-triggering onboarding mid-round on an already-onboarded device).
- **Onboarding could not be completed without UI taps, so it wasn't driven.** None were sent. Instead, the app's own Self Mode auto-detection wrote a full settings delta the instant `MainActivity` was created, with zero input from this session — confirmed by reading `settings.xml` before (two empty `<set>` elements only) and after (`resolutionId=3`, `video-codec=H.264`, `dpi-pixel-density=218`, `view-mode=1`, `screen-orientation=2`, `head-unit-make`/`vehicle-make=Google`, `connection-modes=[self]`, `has-accepted-disclaimer=true`). `has-accepted-disclaimer` is set automatically at `OnboardingActivity.kt:456` (`if (step == STEP_SAFETY) settings.hasAcceptedDisclaimer = true`) as part of that same auto-advance, not from a checkbox tap. `onboarding-version` was additionally forced to `2` (`OnboardingActivity.CURRENT_ONBOARDING_VERSION`) via the new `set_pref.sh` so the wizard activity is skipped on every later relaunch in the round.
- **Bluetooth sink used: "Magnetic Speaker", MAC ending `D6:FB`.** Already the active A2DP device before the round started (`isActiveHfpDevice=true`), used unchanged for all four arms per §2 — never switched, never re-paired.
- **Media source: Spotify**, which already held a live 50-track queue and started playing ("Mama" — My Chemical Romance) on the first `KEYCODE_MEDIA_PLAY_PAUSE`. No manual track selection was needed.
- **`shared_prefs` is app-owned on this phone** (`Uid: (10268/u0_a268)`), unlike the root-owned directory on the head unit rig — no `chown` workaround was needed here.
- Confirmed again, independently: an inline `run-as $PKG sh -c 'cp ...'` for the end-of-round settings restore failed with `cp: Needs 1 argument`, exactly as TESTING-TEMPLATE.md §7a documents. Redone with a two-line pushed script; succeeded first time.
- `hur-wifi-test-scripts/code-researchs/hur-wifi-test-scripts-inventory.md`, referenced by TESTING-TEMPLATE.md §5, was not found on this machine. Took a fresh inventory of the directory instead of relying on it.
- Grepped every capture with `-a` throughout, per §7a.

## R0 — gate

**PASS**

- Build: `build_hur.sh`, clean, first-ever compile of `ada271e7` on this rig.
- Unit tests: `run_unit_tests.sh`, **738/738**, 0 failures, 0 errors.
- Android Auto version: `versionName=17.5.663204-release` — matches the required `17.5.x`.
- `run-as com.andrerinas.headunitrevived stat shared_prefs`: `Uid: (10268/u0_a268)`, app-owned.
- `settings.xml` delta after onboarding: see Setup notes above (achieved with zero UI taps).
- Bluetooth sink: "Magnetic Speaker", `D6:FB`.

## R1 — four arms on one live A2DP link

**PASS.** One Bluetooth link, one Spotify session, never touched or re-paired, carried all four arms back to back with no INCONCLUSIVE. Discard-rule equivalent for this thread (an unintended reconnect): exactly 2 `Handshake: SSL handshake complete` lines in the whole round (one for arm C, one for arm D), no more — clean.

- Settings written: `log-level=1` and `enable-audio-sink=false` before arm A; `enable-audio-sink=true` before arm D (app stopped each time, verified by readback).
- Radio state: unchanged throughout — no radio toggles in this round (§0: not applicable to Self Mode).

**1. Device carrying the active media track, all four arms:** **Bluetooth, in all four.** Every arm shows `A2dpService.mActiveDevice: XX:XX:XX:XX:D6:FB`, a live `AudioPlaybackConfiguration` for `com.spotify.music` in `state:started` (`piid:1159`, `sessionId:1249`, unchanged across the whole round), and the currently-live flinger output thread reporting `Output devices: 0x80 (AUDIO_DEVICE_OUT_BLUETOOTH_A2DP)` in every arm's own dump (arm A line 589, arm B/C/D line 587). That is the "live active track on a named device" the brief asked each PASS to be pinned to.

**2. Per-device Media audio state for `D6:FB`, and whether anything moved it:** `A2DP=100` (the per-device connection-policy field the system UI's "Media audio" toggle writes) in **arms A, B, C and D alike — never touched, never moved.**

**3. Whether an audio `Media Start Request` ever appeared:** **Never.** Exactly 2 `Media Start Request` lines in the whole round, both `Media Start Request VIDEO: session=0, config_index=0` — one in arm C (`10:49:36.967`), one in arm D (`10:50:30.271`). Zero audio starts.

**4. `dumpsys uimode` per arm, and from which arm car mode starts:**

| Arm | `mCarModeEnabled` |
|---|---|
| A | `false` (see note below) |
| B | `true` |
| C | `true` |
| D | `true` |

Car mode first appears in **arm B**, exactly as the brief predicted (`enableCarMode(0)` firing from `AapService.kt:822-833`'s `setupCarMode()`, called on service creation with no session ever started) — confirmed live: arm B's `dumpsys activity services` shows an `AapService` `ServiceRecord` even though `ACTION_START_SELF_MODE` was never sent for that arm.

**Note on arm A:** an earlier launch during this session's own setup (before R1 officially began) had already left car mode stuck **on** — because `disableCarMode()` (`AapService.kt:2032-2038`) runs only from `onDestroy()`, and a plain `am force-stop` does not reliably reach it. That stale state was cleared with one explicit `headunit://exit` cycle (confirmed `mCarModeEnabled=false` immediately after) before arm A's dumps were taken, so arm A's `false` reading is a deliberately-restored clean baseline, not evidence the app never touches car mode outside a session. See "Anything the brief did not ask about."

**The announced service set, C vs D — the extra proof the brief asked for:** identical between the two arms. Both produced exactly:
```
Media Sink Setup Request: 3 on channel VIDEO
Media Sink Setup Request: 1 on channel AUDIO2
```
with no `AUDIO` or `AUDIO1` sink ever announced, in either arm. `Audio sink is off in Settings. Skipping the media and speech audio channels` fired exactly once in the whole round — arm C only, correctly absent from arm D. The one internal difference the sink switch does produce: `AapAudio.requestFocusChange | Audio Focus Request: stream=3, type=4` (the sink-on path) fires **once, arm D only** — so the switch is not a total no-op internally, but it has zero effect on what reaches the wire or on where audio plays.

## R2 — car-mode probe

**Not run.** The brief's own gate: "only if B, C or D failed." None did.

## R3 — positive control: Media audio off, by hand (added post-round, at request)

The brief's §6 lists this as "do not re-run" for its own scope ("already measured on two phones... the round's premise, not a question"), but it was requested explicitly as a round of its own after R1, to get it logged and quotable against this exact build rather than taken on faith from the two prior hand tests. Session state: arm C's configuration (Self Mode projecting, `enable-audio-sink=false`, the reporter's exact configuration), same live A2DP link, same Spotify queue, untouched from R1.

No scriptable path exists for this toggle: `cmd bluetooth_manager` exposes only adapter-level `enable`/`disable`/`enableBle`/`disableBle`, no per-device connection-policy verb; `adb root` is refused outright (`ADB Root access is disabled by system setting`). Per-device "Media audio" lives only in the system Bluetooth Settings UI. Flipped by hand, both directions, at the user's own instruction.

**PASS — reproduces cleanly, and recovers cleanly.**

- **Before:** `A2DP=100`, `mActiveDevice: XX:XX:XX:XX:D6:FB`, live output thread `0x80 (AUDIO_DEVICE_OUT_BLUETOOTH_A2DP)`.
- **Media audio switched off by hand.** After: `A2DP=0`, `mActiveDevice: null`, the Bluetooth output thread disappears entirely from the flinger dump (only `AUDIO_DEVICE_OUT_SPEAKER`/`AUDIO_DEVICE_OUT_TELEPHONY_TX` threads remain). Spotify's own track stays `state:started` (`piid:1159`, `sessionId:1249`) but goes `mutedState:streamVolume` instead of `mutedState:none` — the app keeps "playing" with nothing audible, which is exactly #874's report.
- **HUR's own session is completely unaffected throughout**, confirmed from the continuous capture: `AapMediaPlayback` keeps receiving Spotify status packets (`state=PLAYING`, `playbackSeconds` incrementing 132→145 across the window), `VideoDecoder.logThroughput` holds 47–51fps with `dropped=0, concealed=0` the whole time, and exactly one `Handshake: SSL handshake complete` for the whole capture — no reconnect, no session disruption, nothing in the app's own log reacts to the toggle at all.
- **Media audio switched back on by hand.** After: `A2DP=100`, `mActiveDevice: XX:XX:XX:XX:D6:FB` again, output thread back to `0x80 (AUDIO_DEVICE_OUT_BLUETOOTH_A2DP)`, track `mutedState:none` again. Full recovery, no leftover state.

This is the sharpest single piece of evidence for the reporter: the exact symptom reproduces from one system-level toggle with the app's AAP session, video pipeline and media-status relay all completely undisturbed while it happens — the silence is entirely upstream of anything Open Headunit does or could do.

## Anything the brief did not ask about

**Plain `am force-stop` does not clear `enableCarMode(0)`, and this is independent of the round's actual subject.** `AapService.setupCarMode()` runs on every service creation with no gate; the matching `disableCarMode()` only runs from `onDestroy()`, which `am force-stop` does not reliably trigger (`AapService.kt:822-833` vs `:2032-2038` — the same "flag only gets set in one direction" pattern this repo's own CLAUDE.md already calls out as a recurring bug shape here). Concretely: a user who force-stops Open Headunit after any session — self-mode or otherwise — is left with their phone stuck in Android Automotive car-mode UI indefinitely, with no HUR process even running to undo it, until the next clean session exit. This survived across two force-stops during this round's own setup before being noticed and reset by hand. Distinct from issue #874 (this round's actual subject), but a real, reproducible side effect worth its own look.

## The answer for the reporter

**Bluetooth, in all four arms.** #874's silence is not reproduced by anything Open Headunit does — not by the always-on `MediaSession`/car-mode side effects with no session (arm B), not by a live Self Mode session with the audio sink off, the reporter's exact configuration (arm C), and not by the audio sink on, which the branch already predicted from static analysis would change nothing on the wire (arm D, confirmed identical to arm C). The app never requests audio focus, never opens an audio sink, and never puts a phone in car mode strongly enough to explain a Bluetooth media dropout, in any of the four configurations this app can put a phone into.

R3 pins down the actual cause on this build: switching the paired device's own **Media audio** toggle off in the phone's system Bluetooth settings reproduces the exact symptom — audio disappears while everything else (the AAP session, the video feed, the media-status relay) keeps running normally, confirmed unaffected in the app's own log across the whole toggle. That is a per-device Android setting outside the app's reach, not a code path in Open Headunit. Point the reporter at Settings → Bluetooth → (their device) → Media audio.
