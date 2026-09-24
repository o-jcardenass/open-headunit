# audio-sink-jitter — round 4 results

**Candidate:** `fix/audio-sink-and-wireless-bring-up` @ `8bb8b8449`       **Baseline:** `main` @ `5ce51c5e6`
**APK md5:** candidate `ce7d9663dbd7d07f89f38e6b331de23c` (installed APK pulled back and hashed, matches the build just pushed) / baseline `002201852fcbee84f501d84af7a0c2b2` (built for identity only, not installed)
**Unit:** D-SAM (Samsung SM-T230, `degaswifi`, board PXA1088), Android 4.4.2 (API 19), 2.4 GHz-only radio, head unit. D-POCO (POCO X3 NFC) as phone. Both connected; D-HU and D-MOTO were not used (brief scopes this round to D-SAM + D-POCO only) even though they were physically available this session.
**Date:** 2026-09-16

## Setup notes

- **D-SAM's system clock is a fixed ~12h00m behind host/D-POCO time** and adb has no permission to set it (`date -s` silently no-ops). Every D-SAM log timestamp in this file needs +12h to line up with D-POCO/host timestamps quoted alongside it. Not something this round could fix; flagging so the next round doesn't re-discover it.
- `set_pref.sh` was **not** usable on D-SAM: its pushed on-device helper needs `sed`, which this unit does not have (confirmed, matches TESTING-TEMPLATE.md §7a). Used `set_pref_hostedit.sh` (host-side python3 edit + `run-as cp`) for every setting change instead, and a couple of raw python edits for the `<set>`-typed `connection-modes` key and for removing `audio-latency-multiplier` entirely (A1 needs it *absent*, not `false`), since `set_pref_hostedit.sh` only adds/replaces one scalar key.
- `SettingsActivity` is not exported on this build. `am start -n ... SettingsActivity` from plain `adb shell` fails with a `SecurityException` (not exported). `adb shell run-as $PKG am start --user 0 -n ...` works (the launch then comes from the app's own UID). N6 needed this.
- `am broadcast -a ... -p <pkg>` and `am start ... -p <pkg>` both fail on this Android 4.4.2 build with a `NullPointerException` in the `am` command itself — confirmed for `headunit://disconnect` (brief already knew this for N3) and it also applies to `ACTION_START_WIRELESS_SCAN`. Dropped `-p` everywhere.
- **Brief gap found in N4's settings:** the WPP-endpoint decision (`WppEndpointPolicy`, the thing N4 grades) is gated behind `native-wifi-version-exchange`, which defaults to `false` and is not in the brief's §4 settings table. With it left at the default, `sendWifiVersionRequest` never runs and N4 produces nothing to grade. Enabled it for N4's six bring-ups only, restored to `false` (the default) immediately after. Flagging this because a future round that runs the settings table as written will see N4 silently do nothing.
- **New rig quirk, D-POCO:** its WiFi adapter **also self-reverts back on** a few seconds after `svc wifi disable`, the same way the documented Bluetooth self-reverts do on other units. A single `svc wifi disable` was not enough to produce M3's "reconnect gives up" case — needed a repeat-disable loop (every 15s) to actually starve it long enough to hit `BASE_EXPIRED`.
- Scripts used: `run_unit_tests.sh`, `build_hur.sh`, `install_and_launch.sh` (`HU=30041c35642d2200`), `set_pref_hostedit.sh`. No existing script fit the reliability run's shape (long unattended wait + periodic health telemetry), so `dsam_reliability_watch.sh` was written for it and left in `hur-wifi-test-scripts/`.
- M1 (log export + Share) and M2's backup/restore round-trip both have no adb-reachable trigger (an internal function call off a dialog button, and the SAF document picker respectively) — done as manual taps per the operator's confirmation, minimum taps used, results verified against the log capture rather than taken on their word alone.
- A3's trigger (get the phone to re-send Media Sink Setup via a nav guidance state) was attempted via a `google.navigation:` intent but a system disambiguation dialog intercepted it and a second attempt with an explicit component + `&` in the URI got mis-split by the remote shell; not chased further given time already spent. Marked INCONCLUSIVE, not FAILED.

---

## R0. Gate

**PASS**

- `./gradlew :app:testGithubDebugUnitTest`: 2003 tests, 0 failures (`test-results/*.xml` totals), matches the brief's stated gate exactly.
- Candidate branch checked out fresh from `fork/fix/audio-sink-and-wireless-bring-up`; `git log --oneline -4` matches the brief's four SHAs exactly (`8bb8b8449`, `b400c4360`, `4910c7105`, `076a2dd9b`).
- Installed APK pulled back from `/data/app/com.andrerinas.headunitrevived-3.apk` and hashed: `ce7d9663dbd7d07f89f38e6b331de23c`, matching the build that was pushed moments before (confirms `install_and_launch.sh` installed what it just built, not a stale APK with the same filename).

---

## Part N: Native AA

All runs `wifi-connection-mode=3`.

### N1. The status pill ends with the session

**PASS**

- Session brought up and let project (720p/30fps rendering confirmed).
- Ended from Android Auto's own UI on D-POCO (operator confirmed; no adb-reachable trigger exists — the phone's screen is a `GhostActivity` placeholder with nothing for `uiautomator` to find while projecting).
- `!!! RECEIVED BYEBYE REQUEST FROM PHONE !!! Reason: USER_SELECTION` at `23:14:31.364`, then `status pill step: hidden` at `23:14:32.015` (+651ms — not literally "within a frame," but the only main-thread work between the two is teardown bookkeeping, no other pill transition intervenes), then `the phone ended the session itself, so the listeners reopen without waking it.` at `23:14:32.285`, then `ACTIVELY LISTENING on Android Auto UUID` re-armed at `23:14:32.305`.
- **No `Attempting active poke` for 5 minutes watched** after the Bye-bye (open question: D-POCO did **not** come back on its own within 5 minutes).
- Documented recovery confirmed: `ACTION_START_WIRELESS_SCAN` (the WiFi button) brought it straight back — `createGroup SUCCESS` → poke → `SSL handshake complete` at `23:20:50.174`, video rendering again by `23:20:55.078`.

### N2. An unclean end still wakes the phone, after a settle

**PASS**

- D-POCO's WiFi toggled off/on (2s) mid-session, no Bye-bye sent.
- `AapTransport quitting (clean=false)` at `23:21:27.160`, `Native AA session ended; keeping the WIFI_DIRECT network up for the phone's return.` at `23:21:27.580`, `the session just ended, so the first poke waits 3810ms for the phone's WiFi to settle.` at `23:21:28.761`, `Attempting active poke` at `23:21:32.585`.
- **Measured gap: 3.82s** from the settle-announcement line to the poke (matches the printed 3810ms almost exactly); **5.43s** from the transport's own `quit` line to the poke.
- D-POCO's own log shows **`Send WifiConnectStatus, status=STATUS_SUCCESS`** for this reconnection — **no `WIFI_NETWORK_UNAVAILABLE(-11)`** this run, unlike the 0.5s gap that reproduced `-11` on 2026-09-15. The 5s-ish settle appears to be enough on this pairing.
- Session recovered fully (`SSL handshake complete` at `23:21:40.643`).
- **Caveat, not a fail:** the status pill itself logged no `hidden`/`WAITING_FOR_PHONE`/`WAKING_PHONE` transitions during this run. Root cause traced: `AapProjectionActivity` (not `MainActivity`) owns the foreground while a session is live and projecting, and `MainActivity.renderStagePill` only logs when it runs. The reconnect itself is real and fast (`AapProjectionActivity`'s own `reconnect is under way (connected), holding the overlay` → `Hiding reconnecting overlay - frames resumed` in ~17s), it is simply invisible to the pill's own log line under this exact condition (a still-projecting session, as opposed to N1's already-idle one). Worth a look, but distinct from "a pill that stays frozen showing a wrong state."

### N3. The local Disconnect button

**PASS.** `headunit://disconnect` (no `-p`, per brief): `status pill step: hidden` at `23:23:07.007`, `Native AA user exit. Stopping active launcher.` at `23:23:08.799`, launcher stopped.

### N4. The group name this unit cannot control

**PASS**, with two things worth flagging (see Setup notes for the settings-gap one).

- Three *real* bring-up cycles (full disconnect → re-trigger → wait for the version-exchange line) were not enough: two of the six actual attempts run came back `asked=nothing (not this app's create)` reusing a stale group rather than a genuine fresh create, which **reset** `GroupIdentityStabilityPolicy`'s name-change counter each time. It took **6 real bring-ups** before the withhold reason finally read the exact RENAMED text: `this unit's Android is too old to name its own WiFi Direct group, and the platform has picked a new name every create, so there is nothing for the phone to remember.` (run 6, `23:29:44.625`). The intervening reasons were UNPROVEN and CHANGED text, not RENAMED — all of them still correctly **withhold** the endpoint (the safety property never lapses), only the specific reason text takes longer to settle than "three in a row" suggests when a stale-group delivery intervenes.
- Session still formed over Bluetooth every single time regardless of the WPP endpoint being withheld (confirmed via `SSL handshake complete` following each of the 6 attempts).
- `persistent=no (temporary)` throughout; `stable=` progressed unproven → no → no (the platform names it) across the run.

### N5. The video cap on a unit that cannot read its own band

**PASS, fully.** `narrow-band-profile-cap=true`, `resolutionId` untouched.

- `[RES_CAP] resolutionId=0 realScreen=1280x800 usable=1280x800 portrait=false locked=false chosen=_1920x1080 capped=_1280x720 changed=true linkCapped=_1280x720` — fired automatically on every session this round (checked across 3 separate bring-ups).
- Reason text matches exactly: `This unit's Android is too old to report which band it is on. The phone is being asked for at most 720p and 30 fps... Turn off "Lower video on a 2.4 GHz link" in Video settings to be given what you asked for instead.`
- **Picture survived every single session this round** — no 1080p `EPIPE` death was seen anywhere, including the 40-minute reliability run below.
- **Answers the round's question 3: yes, the manual 720p workaround in TESTING-TEMPLATE.md §7a can be retired for this class of device.** The cap fires unconditionally without anyone setting `resolutionId` by hand.

### N6. The settings screen holds the stack down

**PASS.** Opened via `run-as $PKG am start --user 0 -n ... SettingsActivity` (plain `am start -n` is refused, not exported — see Setup notes). `the wireless stack stops until it closes` fired the instant it opened; zero `createGroup`/`Attempting active poke` in the 60s it was held open; `the settings screen closed, re-arming wireless mode NATIVE` fired on close.

### N7. Two bring-ups cannot race

**PASS on outcome** (exactly 1 `createGroup SUCCESS` from two `ACTION_START_WIRELESS_SCAN` broadcasts fired back-to-back), **but the expected guard line never appeared.** Reading the trace: `ACTION_START_WIRELESS_SCAN`'s "Force-starting WIFI-Scan from UI" path calls `WifiLauncherManager.setActive` → full `WifiDirectManager.stop()` (tears down sockets, unregisters the receiver) → re-init → `startNativeAaQuietHost()`, and the second broadcast's effect was to **restart the whole stack**, not to hit `NativeBringUpReentryPolicy.isDuplicate()` and get told `"...still running, so this one is not started on top of it."` The single-group safety property held, but this particular trigger doesn't exercise the same code path the guard line is written for. A future round wanting to see that exact line should race two *organic* re-arms (e.g. a settings-close and a phone-arrival within the same second) rather than two admin force-scans.

### N8. The watchdog holds for a phone that never dialled

**PASS.** D-POCO's Bluetooth off, mode armed, 2 minutes watched: `Native AA — the phone has not opened the Android Auto Bluetooth channel on this unit, so it was never handed these credentials; recreating the WiFi group would not be the repair. Leaving it up.` fired once; exactly 1 `createGroup SUCCESS` in the whole window, no recreate.

### N9. Wireless does not arm on a cable-only unit

**PASS.** `connection-modes={usb}` (app stopped, restored to `{wifi}` afterward and confirmed by readback): `wireless bring-up requested, but WiFi is not one of the chosen connection modes. Not arming it; the WiFi button still works.` on launch, no group, no poke. `ACTION_START_WIRELESS_SCAN` (the WiFi button) still worked and brought the stack up (`ARMED` → poke → `createGroup SUCCESS`) despite the mode not being in the set.

---

## Part A: the audio sink on API 19

Read round 4 brief §2 — this grades the API 19 fallback path (`getUnderrunCount`/`getBufferCapacityInFrames` are API 24, `getBufferSizeInFrames` is API 23, `MediaCodec.Callback` is API 21), not the audio work rounds 1–2 graded. **No person was listening for these runs** (unattended session on a lab bench); "against what was heard" below is therefore the instrument's own count only, not corroborated by ear.

### A1. The shipped default, out of the box

**PASS.** Fresh prefs, `audio-latency-multiplier` key removed entirely (confirmed absent via readback), `use-aac-audio=false`. ~7 minutes observed.

- `AudioDecoder.start: channel=6, ..., latencyMultiplier=16, queueCapacity=50` — confirms the shipped default depth applies with the key absent.
- `AudioTrackWrapper: AUDIO can hold 53488 frames (1114ms) of the 213952 bytes asked for, keeps 1114ms filled, banking 400ms before playing and 550ms after an underrun`.
- Steady-state window: `audio sink AUDIO over 30211ms: underruns=0, ..., capacity=53488 frames (1114ms), effective=53488 frames (1114ms), target=19200 frames (400ms)` — **`target=` reads exactly 400ms as the brief expects**, `capacity=`/`effective=` are real (not 0/1). Held clean (`underruns=0`) across every window in the ~7-minute observation.
- `playback started with 18432 frames banked (target 19200) after 219ms`.

### A2. The dial

**PASS.** Three ~1-minute sessions, `audio-latency-multiplier` 2 → 8 → 16.

| multiplier | capacity | effective | target |
|---|---|---|---|
| 2 | 19200 frames (400ms) | 19200 frames (400ms) | 5142 frames (107ms) |
| 8 | 26744 frames (557ms) | 26744 frames (557ms) | 9600 frames (200ms) |
| 16 | 53488 frames (1114ms) | 53488 frames (1114ms) | 19200 frames (400ms) |

All three figures rise monotonically at each step, exactly as the brief expects. `capacity=` on this unit is computed from the bytes asked for, not read back from the track (per brief §2's own note about the API 19 path). No underruns fired in any of the three short sessions, so "fewer breaks at 16 than at 2" could not be distinguished by ear or by count — nothing to compare in a quiet run.

### A3. A re-setup leaves a live sink alone

**INCONCLUSIVE.** Could not script the trigger (a nav guidance state resending Media Sink Setup) in the time available: a `google.navigation:` intent hit a system disambiguation dialog instead of launching Maps directly, and a follow-up attempt with an explicit component broke because the `&` in the intent URI got mis-split by the on-device remote shell. Not chased further. No evidence either way this run.

### A4. The sink deepens its own cushion

**INCONCLUSIVE**, per the brief's own pre-registered path — the substitute lever (D-POCO WiFi off 2s / on, 4 times) did not produce four clean gaps. Verified with a timestamp analysis of 400 `RECV: AUDIO Media Data` arrival times spanning the four toggles: only **1 gap > 300ms** (25.4s long), the other three toggles produced no measurable RECV interruption at all. Correspondingly only **1 re-bank** fired (`AudioTrackWrapper: AUDIO underran, re-banking 257ms (re-bank 1)`), not the three needed to reach the "keeps underrunning... banking from here" check. `audio-latency-multiplier` was 2 for this run as specified.

### A5. Overflow sheds the oldest

**Not reached.** `Audio queue is full at N chunks, shedding the oldest` never appeared (0 matches across the whole capture) — consistent with A4 only producing one brief interruption, nowhere near enough load to fill the queue. Per the brief, absence here is not a FAIL.

### A6. AAC on the synchronous path

**PASS.** `use-aac-audio=true`, ~5 minutes. Audio played the whole time (Spotify `PLAYING`/`STOPPED` transitions logged normally on track changes), no crash, no `the AAC sink cannot keep up, shedding decoded frames`, session survived intact (no `Disconnected unexpectedly`/Bye-bye in the window). First time this thread has run AAC through `decodeSync` (not the async `MediaCodec.Callback` path, which is API 21) on real hardware.

---

## Part T: the transport

### T1. The dispatch line on a healthy session

**FAIL** against the literal wording ("`blocks=0` in every window after the first"). Checked across two separate sessions (the A6 AAC session and the A1 session): `blocks` does **not** settle to 0 — it recurs at 1, 2, 3, 5 across most 30-second windows in both, not just the first. Example run (A1 session): `blocks=5` (first window, on `CONTROL`), then `1, 2, 0, 0, 0, 2, 1, 1, 0` across the next 9 windows. `unread=` stayed low (8–9%) and `videoQueue` stayed in the low single digits/tens throughout both sessions, and `videoShed=0` in every window — so the two other sub-criteria the brief cares about (unread%, videoQueue magnitude) are healthy; it is specifically the "`blocks` settles to zero" claim that does not hold on this hardware.

### T2. The backlog stays bounded with the video feed held

**FAIL.** `debug-video-feed-hold-ms=200`, media playing, watched for **7 minutes** (extended past the brief's 4 to confirm the trend, since it was still rising at 4).

`videoQueue` across consecutive 30s windows: 76 (first, transient) → **19, 19, 19** → 31 → 38 → 64 → 68 → 102 → 78 → 92 → **120**. This is a real, sustained non-settling climb (19 baseline to 120 and still rising when observation stopped), **not** round 2's catastrophic 5602 — over an order of magnitude smaller and never threatened the socket — but it is climbing window after window rather than settling, which is exactly what the brief's FAIL condition describes, just at a far smaller scale. `blocks` stayed mostly 0 with occasional 1/3, `videoShed=0` in every window (nothing ever got shed despite the growing backlog).

**The audio line stayed completely clean throughout** (`underruns=0` in every window, `depth=` 700–1090ms, steady).

**The picture stayed whole.** A screenshot taken during the climb (queue≈120, `evidence/audio-sink-jitter-round4/t2-videoqueue-picture.png`) shows a sharp, uncorrupted map render; the on-screen overlay reads `FPS: 4`, `Frame: 218ms`, confirming the phone did throttle its send rate exactly as the brief describes as the intended tradeoff — the failure here is specifically that the queue itself doesn't bound, not that the picture degrades.

---

## Part M: the rest

### M1. The Share button that killed the app

**PASS** (operator-performed: export a log, press Share; no adb trigger exists for either — `LogExporter.shareLogFile` is called from a dialog click handler, not wired to any exported component). Operator confirmed: no force-close, share sheet opened. Log capture corroborates: two `LogExporter: session | build=3.4.0-beta3 (108) github/debug commit=8bb8b8449e32 | device=samsung SM-T230 ... api=19 ...` lines logged cleanly around the export, no `FATAL EXCEPTION`, app stayed alive and resumed normal operation immediately after (a fresh wireless bring-up followed within seconds).

### M2. The performance overlay and its position

**PASS.**

- `show-fps-counter=true`: overlay draws (confirmed via screencap, `evidence/audio-sink-jitter-round4/m2-overlay-position0.png`).
- `overlay-position` 0 → 1: the overlay visibly moved between the two screenshots (`m2-overlay-position0.png` vs `m2-overlay-position1.png`). **Caveat on "left"/"right":** D-SAM renders its forced-landscape UI into a portrait framebuffer (this unit must be mounted portrait per TESTING-TEMPLATE.md §7a), so a raw `screencap` shows the movement rotated 90° from how a viewer sees it (top↔bottom in the raw capture, not left↔right) — confirmed as a real position change in the app's own coordinate space, not confirmed against absolute screen-left/screen-right without correcting for that rotation.
- Setting is labelled "Show Performance Overlay" in the UI now; key is still `show-fps-counter` (compatibility, as the brief notes).
- Settings backup export → import round-trip (operator-performed, no adb trigger — SAF document picker): operator confirmed it survived. Read back on-device afterward: `overlay-position=1`, matching what was set before the round trip.

### M3. The reconnect grace

**PASS**, but needed the D-POCO WiFi self-revert quirk fought off first (see Setup notes) to actually reach the give-up path rather than a quick recovery like N2's.

- `AapProjectionActivity: reconnect is under way (connected), holding the overlay` while D-POCO's WiFi was flapping.
- With D-POCO's WiFi held down via a repeat-disable loop: `Reconnect gave up after 20s (BASE_EXPIRED). Finishing activity.`

---

## D-SAM reliability run (beyond the brief)

Not part of the round 4 brief — added this round because the user had a real session running on D-SAM that disconnected unexpectedly after roughly 20 minutes. **Candidate only**, all Part A/N settings restored to the round's worst-case defaults first (`audio-latency-multiplier=16`, `use-aac-audio=false`, `debug-video-feed-hold-ms=0`, `narrow-band-profile-cap=true`, `native-wifi-version-exchange=false`, `connection-modes={wifi}`), `show-fps-counter=true` left on for passive telemetry, Spotify playing throughout. Bring-up done, then left **completely untouched** for 40 minutes while a script sampled battery/thermal/memory/A2DP every 60s and watched the live log for any disconnect signature.

**Battery/thermal/memory:** flat and unremarkable for the full 40 minutes — battery held at 100% (USB-powered, not the historically-noted 9%-brownout state), thermal zones 36–40°C (millidegree readings, both zones), free RAM steady around 885–935MB out of 1.35GB total. None of these explain what happened below.

**The first bring-up of this run itself failed and needed a retry** (worth noting as background, distinct from the main finding): the very first Native AA attempt reached `SECURING` and then `AapTransport quitting (clean=false)` right after `AA Server socket closed after successful handoff` — the SSL handshake never actually completed. A second automatic poke cycle 8 seconds later (`HFP-AG` failed silently, `HSP-AG` on retry) succeeded and the session that ran for the rest of the test came up cleanly. First-attempt handoff failures are evidently not rare on this pairing.

**The main finding — a real, unprompted disconnect after 40:02 of continuous uptime:**

At D-SAM time `01:03:43.376` (`13:03:43` real time), with nothing done to either device from this session: `AapTransport quitting (clean=false)` followed immediately by `AapProjectionActivity: Disconnected unexpectedly.` **D-POCO's own log gives the root cause directly**: `wpa_supplicant: wlan0: CTRL-EVENT-DISCONNECTED bssid=e6:58:e7:0e:de:1e reason=3 locally_generated=1` — the phone's own WiFi stack chose to leave the P2P group (`locally_generated=1` means this was not the head unit or a radio failure; `reason=3` is `DEAUTH_LEAVING`). Immediately preceding it, D-POCO's log shows a background WiFi scan (`scan start scan id 42534` at `13:03:39.588`, completing at `13:03:43.088`, "31 bss" found) running exactly across the disconnect.

**This looks like the same mechanism `link-stall-periodic-scan`'s own investigation flagged as untestable on its rig** ([[project_periodic_link_stall_investigation]]) — a saved-network-driven OS background scan disrupting an active WiFi Direct session — now caught with a concrete `wpa_supplicant` reason code on this pairing. It is not deterministic: D-POCO ran exactly 3 background scans during the session (at connect, ~22 minutes in, ~40 minutes in) and only the third coincided with a drop; the first two did not. **This lines up with the user's "~20 minutes" report as plausible bad luck on an earlier scan cycle, not a fixed 20-minute timer** — the scan cadence itself looks roughly ~20 minutes apart but irregular (22m then 18.5m between the three observed here), and whether a given scan actually knocks the link down looks probabilistic rather than guaranteed.

**Recovery did not happen on its own.** For **9+ minutes** after the drop, the app's own poke loop fired every ~35–45 seconds (`Attempting active poke to device: POCO X3 NFC` → `Successfully poked ... via HFP-AG` → `Bluetooth reader ended: bt socket closed, read return: -1`, repeating), while D-POCO's own Gearhead retried on its own faster cadence (`Supplicant state: DISCONNECTED` → `WIRELESS_WIFI_SCAN_ISSUED` → `WIRELESS_WIFI_SCAN_RESULTS_NETWORK_NOT_FOUND`, every ~12–17s), and neither side converged: **the app's poke never recreates the P2P group, and D-POCO can no longer find the old one by scan even though the head unit reports `groupFormed: true` the whole time.** No `createGroup SUCCESS` and no `AutoStartReceiver` hits fired during this whole stuck window — so this candidate's #760-style self-wake/group-churn concern is **not** what happened here; this is a different failure mode: a stable BT-poke loop that simply never refreshes the WiFi side.

**Confirmed fix:** manually firing `ACTION_START_WIRELESS_SCAN` (the documented WiFi-button lever) while stuck immediately forced a fresh `createGroup SUCCESS`, and the phone reconnected within 6 seconds (poke → credentials → `SSL handshake complete` → first frame rendered). This is the exact same recovery N1 documents for its own scenario, and it works here too — the gap is that **nothing on the app side currently decides to take that step on its own** after this specific kind of drop; the built-in poke loop just keeps re-poking the same stale group indefinitely (at least 9+ minutes observed, no ceiling or give-up message found).

---

## 8. Answers to the round's three required questions

1. **Does D-POCO return on its own after N1?** No — watched 5 minutes with zero self-recovery; the WiFi button (documented way out) brought it back immediately when pressed.
2. **N2's measured settle gap:** ~3.82s from the settle-announcement line to the poke (~5.43s from the transport's own quit line). No `WIFI_NETWORK_UNAVAILABLE(-11)` this run, unlike the 0.5s gap on 2026-09-15 — the current ~5s figure looks sufficient on this pairing, at least once.
3. **Does N5 retire the manual 720p cap for this device class?** Yes — the cap fired automatically and correctly on every session this round, including the 40-minute reliability run, with no manual `resolutionId` setting and no `EPIPE` death anywhere.

## Anything the brief did not ask about

- **The scan-triggered disconnect above is the headline finding of this round** — it reproduces a real user-reported fault with a concrete phone-side root cause (`wpa_supplicant ... locally_generated=1 reason=3`, correlated with a background scan) and shows the existing recovery path (BT poke loop) does not self-heal from it, while the existing WiFi-button lever does. This is new, hardware-obtained evidence for a mechanism [[project_periodic_link_stall_investigation]] previously called untestable on its own rig.
- D-POCO's WiFi adapter self-reverts back on a few seconds after `svc wifi disable`, joining the list of already-documented BT self-revert quirks on other units (new quirk for TESTING-TEMPLATE.md §7a).
- `SettingsActivity` is not exported on this build; `run-as $PKG am start --user 0 -n ...` is the working substitute on an unrooted unit (new note for the D-SAM-only section of TESTING-TEMPLATE.md §7a).
- `native-wifi-version-exchange` defaulting to `false` and being absent from this brief's settings table means N4 as literally written grades nothing unless a future brief either adds the key or explicitly says to leave the feature off and grade the negative case instead.
