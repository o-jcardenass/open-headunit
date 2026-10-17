# bring-up-status-pill-and-poke-readiness — round 7 results

**Candidate:** `testing/status-pill-aac-zbt` @ `77df7f6b` (R0–S1, F3) / `ec9352d5` (E5 only, see Setup notes)
**Baseline:** none needed (see brief §1)
**APK md5:** `6cc667c856d540b1706fc8cf2c5a3f23` (77df7f6b, both devices) / `05418f927c9434eaf845b299b4c06203` (ec9352d5, D-HU)
**Unit:** UNISOC MT50 (D-HU, `27870808938846`) · Redmi/POCO X3 NFC (D-POCO, `4f4027e9`), phone for R1/A1/B2/D1/D2/F1/F1b/S1/F3, head unit for A2/A3 · Motorola edge 30 neo (D-MOTO, `ZY22GC3BM4`), phone for A2/A3/B2 and hotspot host for Part C/S1
**Date:** 2026-09-11

## Setup notes

- Scripts used: `build_hur.sh`, `run_unit_tests.sh` (unmodified), `set_hu_settings_host.py` (D-HU, rooted), `set_hu_settings_runas.py` (D-POCO). New script added: `connect_hotspot.sh` (pushed to `/data/local/tmp` on D-HU) — a one-line `cmd wifi connect-network "SSID" wpa2 "password"` wrapper, needed because a spaced SSID does not survive inline `adb shell` quoting even backslash-escaped in this session's shell (matches TESTING-TEMPLATE §7a's documented `sh -c` quoting trap, extended here to `cmd wifi connect-network` itself, not just `sed`). Copied into `hur-wifi-test-scripts/` for the next round.
- **The brief was amended mid-round** (`f1b6605b3` → `e5bd8ed11`, candidate `77df7f6b` → `ec9352d5`, Part E added). Verified via `git diff --stat 77df7f6b ec9352d5`: every changed file is a `values*/strings.xml` locale resource, zero code changes. R0 through S1 and F3 below were run against `77df7f6b` before the amendment landed; only E5 was run against the rebuilt `ec9352d5`, and the string-only diff means R0–S1/F3's results stand unaffected by the SHA move.
- **D-MOTO's hotspot has no non-root path**: `cmd wifi start-softap`/`get-softap-config` both refuse with `SecurityException: Uid 2000 does not have access`, confirmed again this round. Operated by driving Settings → Hotspot & tethering → Wi-Fi hotspot → "Extend compatibility" (on = 2.4 GHz, off = 5 GHz) directly via `adb shell input tap`, minimum taps, no scrolling. Restored to off/off afterward.
- **D-MOTO locked with a PIN mid-round**, not just the swipeable lock screen a plain screen-timeout gives it earlier in the same round — session-dependent (a trust-agent grace window, presumably). adb has no path past a PIN; the user unlocked it in person once. Worth flagging for the next round that needs D-MOTO's screen.
- **D-HU's `cmd wifi` build has no `disable-network` subcommand**, only `forget-network` (destructive) — there is no non-destructive way to stop D-HU's stronger, higher-priority home network (`Pegue Cdesta`, 5745 MHz, ≈−20 dBm) from winning a reconnect race against a freshly-joined temporary hotspot (≈−32 dBm). Part C's 2.4 GHz runs (C1/C3/C5) were won by issuing the `connect_hotspot.sh` connect and the app launch back-to-back, faster than the roam-back; this worked every time it was tried but is a race, not a guarantee, for anyone repeating this.
- **`google.navigation:` deep links hit an "Open with" chooser on D-POCO** because this build's own manifest registers OHU as a candidate handler for that intent scheme — picking "Maps" (and "Just once") is a one-time, minimum-tap exception to the no-UI-driving rule, same class as round 6's settings-search tap.
- **F3's GPS mocking never reached Android Auto's own location fix.** `cmd location providers set-test-provider-location` against the raw `gps` provider (the `mock_drive.sh` mechanism used successfully in other threads) left the phone's Android Auto session reporting a permanent `0 km/h` and never advanced the projected maneuver banner past the first instruction, despite Google Maps on the phone itself computing and starting real turn-by-turn guidance against the same mocked fixes (confirmed by screenshot: 6 min/2.7 km route, ETA counting down). Reads as a Play Services fused-location vs. raw-LocationManager-provider gap specific to what Android Auto's own nav rendering consumes — a new rig limitation, not exercised by earlier mock-drive rounds because none of them graded Android Auto's own turn-by-turn audio.
- Raw logcat captures and every screenshot referenced by filename live in `hur-wifi-test-scripts/round-bring-up-r7/` on the rig machine. Key screenshots are copied into `evidence/bring-up-status-pill-and-poke-readiness-round7/` in this repo.

## R0 — build, gate, install, identity

**PASS**

- `BUILD SUCCESSFUL`; gate **1818 tests, 0 failures** — matches the brief exactly.
- Installed on D-HU and D-POCO with `adb install -r`; live `md5sum` of each `pm path` base.apk: `6cc667c856d540b1706fc8cf2c5a3f23` on both, matching each other and the host build.
- DEX grep on the installed base APK: `AutoConnectAttemptPolicy` × 4 (new this commit, absent from round 6's build).

## R1 — the pill, with an auto-connect in flight

**PASS.** D-HU not `pm clear`ed; `last-connected-native-mac` confirmed non-empty before launch. Clean-run protocol (D-POCO Bluetooth off at launch, back on 18 s later).

- 12 pill steps logged, first `ARMED` (13:13:29.341), `WAITING_FOR_PHONE` before `WAKING_PHONE`.
- `Auto-connect: a phone is answering, taking the full screen.` present at 13:13:57.007 — the promotion happened.
- Last step `STARTING_PROJECTION` (13:13:57.219), and both it and the preceding `SECURING` carry the `(not shown, the overlay owns the screen)` suffix. This is exactly the fix's target behaviour.
- `hidden` never appeared (0 occurrences) — the pill capture window ended with the session still live, so this is a vacuous pass of "at most once," not a tested teardown line.
- Wall clock `ARMED` → `STARTING_PROJECTION`: **27.878 s**.
- Discard-rule check: `createGroup SUCCESS`=1, `MATCH!`=0, `SSL handshake complete`=1, single `p2p-wlan0-0` — clean.

## Part A — the auto-start offer

### A1 — the stored trigger is cleared even while the selector is up

**PASS.** D-HU, two paired phones, `native-driver-selection-mode=1`, `auto-start-bt-macs` set to D-POCO's MAC, `auto-start-offer-answered-macs` deleted, both read back.

- `HomeFragment: more than one phone is paired, so the Bluetooth auto-start device is cleared.` present.
- `auto-start-bt-macs` reads back empty (`<set name="auto-start-bt-macs" />`) in both `settings.xml` and `settings_device_protected.xml`.
- `ACTION_NATIVE_AA_PROMPT_SHOWN received` present.
- `driver candidates: 2 phone, 0 unknown, 0 not a phone`.

### A2 — the question is asked on a later resume

**Mixed: condition 2 FAILs; conditions 1, 3, 4, 5 PASS via a mechanism the give-up line does not gate.** D-POCO as head unit, one paired phone (D-MOTO), `auto-start-bt-macs`/`auto-start-offer-answered-macs` deleted, `last-connected-native-mac` confirmed naming D-MOTO, D-MOTO's Bluetooth off throughout.

1. **PASS.** `HomeFragment: Unambiguous driver (motorola edge 30 neo) - auto-connecting directly without prompt` at first launch (13:17:41.374); no dialog.
2. **FAIL.** `Auto-connect: nothing answered this attempt (mode=…)` never appeared. Watched continuously for **7 min 39 s** (13:17:41.374 → 13:25:20.729) on a single unchanged PID (16552, confirmed by every `OPENHU` line in the capture) — no activity recreation occurred. 19 `WAKING_PHONE` cycles logged in that window, i.e. roughly 3× the stated 150 s bound with no give-up. Read from source: `startAutoConnectWatchdog()` (`MainActivity.kt:616-626`) is called exactly once, from `beginAutoConnect → showAutoConnectUi()`, and schedules `lifecycleScope.launch { delay(150_000L); … }`; nothing else on this path calls `.cancel()` on it. The delay's completion callback simply never ran in this capture — no crash, no exception, no `endAutoConnect` log, no reset of `autoConnectInProgress`. **This is a real, reproducible defect** in the watchdog's delivery, independent of anything condition 3 below found.
3. **PASS, but not for the reason the brief expects.** Pressing Home and reopening (13:25:xx, well before any give-up) showed the offer dialog immediately (screenshot: `evidence/…/a2-offer-dialog-first-reopen.png`). Read from source: round 6's fix (`61c1dd82`) changed `HomeFragment.checkAutoStartOffer`'s gate from `!autoConnectInProgress` to `screenIsFree = !driverCheckOwnsScreen && activeDialog == null && !MainActivity.overlayOwnsScreen()` (`HomeFragment.kt:690-692`), and `overlayOwnsScreen()` (`MainActivity.kt:1344-1345`) is `autoConnectInProgress && autoConnectMode == OVERLAY`. Because D-MOTO's Bluetooth never answers, `autoConnectMode` stays `PILL_THEN_OVERLAY` forever, so `overlayOwnsScreen()` is `false` from the first instant and the offer's gate is satisfied on **any** resume — it does not wait for, or need, the watchdog at all. So the round's headline user-facing defect (the offer never asking on a later resume) is fixed and does not depend on the broken watchdog; the watchdog bug is a separate, quieter one (dead telemetry plus a stuck `autoConnectInProgress` flag for the life of the process, which A4 below shows has a real consequence).
4. **PASS.** `auto-start-offer-answered-macs` confirmed absent (`grep -c` → 0) while the dialog was on screen and unanswered.
5. **PASS.** Tapping Yes wrote `A0:46:5A:97:E4:95` into both `auto-start-offer-answered-macs` and `auto-start-bt-macs`, logged `HomeFragment: Bluetooth auto-start turned on for motorola edge 30 neo (A0:46:5A:97:E4:95) at the user's request.`; relaunching afterward showed no dialog (screenshot: `evidence/…/a2-relaunch-no-dialog.png`).

### A3 — the dialog survives the relaunch, and a back press is remembered

**PASS.** A2's setup plus `screen-orientation=2` on D-POCO; `auto-start-offer-answered-macs` deleted; three-minute wait honoured before the reopen.

1. Two `HomeFragment.onResume` lines **0.326 s** apart at cold launch (13:28:00.934, 13:28:01.260) — consistent with round 6's 0.406 s measurement of the same quirk.
2. No `WindowLeaked`, no `FATAL EXCEPTION`. PID unchanged (17418) through the Home+reopen step; the later force-stop+relaunch step correctly got a fresh PID (17684) — expected, not a discard-rule hit.
3. Dialog on screen at +5 s after reopen (screenshot: `evidence/…/a3-dialog-persists.png`); `auto-start-offer-answered-macs` confirmed absent immediately before the Back press.
4. `KEYCODE_BACK` dismissed it and wrote `A0:46:5A:97:E4:95` into `auto-start-offer-answered-macs`; a subsequent force-stop+relaunch showed no dialog (screenshot: `evidence/…/a3-relaunch-no-dialog.png`).

### A4 — a stuck wireless attempt no longer blocks a USB connection

**UNTESTABLE.** `adb shell dumpsys usb` on D-HU reports `host_connected=false` — this rig's MT50 has no USB host mode at all, confirmed directly rather than assumed. Per the brief's own instruction, reported as UNTESTABLE without substituting a different trigger. The behaviour A4 targets (a stuck `autoConnectInProgress` blocking `beginAutoConnect` for every later attempt including USB) is real per A2's finding above, but this rig cannot exercise the USB half of it.

## Part B — the veto that matters

### B2 — a vetoed auto-start names the veto that was actually firing

**PASS.** D-POCO as head unit, `auto-start-bt-macs`=D-MOTO's MAC, D-POCO WiFi off (handshake attempt opens, cannot complete), then D-MOTO's Bluetooth brought up mid-attempt.

- Attempt genuinely in flight: `NativeAA: ACTIVELY LISTENING on Android Auto UUID … Waiting for phone to connect back!` at 13:33:39.385.
- Real arrival: `AutoStartReceiver.onReceive | BT Device connected: motorola edge 30 neo … MATCH! Starting AapService via Bluetooth Auto-start...` at 13:34:13.503.
- Veto quoted whole: `AapService: Bluetooth auto-start: nothing to do, a handshake attempt is already in flight.` — the `attemptInFlight` arm, ungraded after two prior rounds.

## Part C — the station stand-down mode

All five run on D-MOTO's hand-operated hotspot (2.4 GHz for C1/C3/C5, 5 GHz for C2/C4) or, for C2/C4, on D-HU's own already-joined home network (also 5 GHz, so no hotspot needed for those two).

- **C1, AUTO stands down from a 2.4 GHz station. PASS.** D-HU on `Hotspotcito Chingon` at 2412 MHz. `StationStandDown: asked this unit to leave its WiFi network so the group can have the radio to itself (mode=AUTO, station on 2412MHz, 5GHz=true, disableNetwork returned false). It is rejoined when the session ends.` then `StationStandDown: this unit has left its WiFi network.`
- **C2, AUTO stays joined on a 5 GHz station. PASS.** D-HU on `Pegue Cdesta` at 5745 MHz (its normal home network, no hotspot needed). No `asked this unit to leave`; `StationStandDown: this unit's own WiFi network is on 5745 MHz, and a group beside a 5 GHz station is the state measured to run clean; the station stays joined.`
- **C3, NEVER stays joined. PASS.** D-HU on `Hotspotcito Chingon` at 2412 MHz, `stand-down-station-mode=2`. No `asked this unit to leave`; `StationStandDown: the setting keeps this unit joined to its own WiFi network, so the group shares the radio with it.`
- **C4, ALWAYS stands down regardless of band. PASS.** D-HU on `Pegue Cdesta` at 5745 MHz, `stand-down-station-mode=1`. `StationStandDown: asked this unit to leave its WiFi network so the group can have the radio to itself (mode=ALWAYS, station on 5745MHz, 5GHz=true, disableNetwork returned false)…` — C2's positive control, same station state, opposite outcome.
- **C5, the permission gate. PASS.** D-HU on `Hotspotcito Chingon` at 2412 MHz, `stand-down-station-mode=1`, `SYSTEM_ALERT_WINDOW` revoked. No stand-down; `StationStandDown: This unit's Android will only let the app drop its own WiFi connection while the app has the "display over other apps" permission. Granting it frees the group's radio.`

Overlay permission re-granted after C5. D-HU's temporary hotspot networks forgotten and the home network rejoined after every run in this part.

## Part D — the re-dial hold-open and the decoder guard

### D1 — a re-dial while projecting sends nothing

**INCONCLUSIVE**, honestly — three attempts, none produced a re-dial. Live Native AA session (D-HU/D-POCO), lever: D-POCO airplane mode for under 2 s (1.5 s, 0.8 s, 1.9 s across the three attempts). `WppTcpServer: projection already up; …` never appeared in any attempt. The session stayed healthy through all three: no gap in `Throughput over …ms: rendered=` lines, no second `SSL handshake complete`. Not forced further, per the brief.

### D2 — a disconnect keeps a live session's decoders

**INCONCLUSIVE**, and one genuine finding along the way. Three attempts, `headunit://disconnect` followed immediately by a re-arm lever, then a reconnect.

- **Attempt 1** used `ACTION_START_WIRELESS` as the re-arm (per brief §3a). It silently no-opped: `WifiLauncherManager: WiFi Mode NATIVE.mode with same start-configuration is already initialized.` (`WifiLauncherManager.kt:37-40`'s `hasSameStartConfiguration` guard treats the launcher as still active immediately after a user disconnect, since nothing clears `active` on that path). **No new session formed at all** from this attempt — worth flagging separately: §3a documents `ACTION_START_WIRELESS` as a reliable disconnect re-arm, and it is not, at least not immediately after a disconnect.
- **Attempts 2 and 3** switched to `ACTION_NATIVE_AA_POKE` (the other documented lever), which did form fresh sessions, but neither hit the specific race: `AapService: a session is already connected, so its decoders are left running` never appeared. Gap disconnect→reconnect: attempt 2 ≈ 90 s, attempt 3 ≈ 35 s — both are ordinary fresh bring-ups, not overlaps.
- A closer read of attempt 1's log shows the poke itself *did* succeed at the RFCOMM layer (`Successfully poked … Holding 20000ms…`) but `AapService: Native AA user exit. Stopping active launcher.` fired ~1.3 s later and tore the just-reopened listeners down before the phone could dial back — a race the disconnect wins, cleanly, every time tried.
- Reported INCONCLUSIVE per the brief ("three attempts without the line is INCONCLUSIVE, not FAIL"), with the `ACTION_START_WIRELESS` no-op reported as a separate, real finding about §3a's own accuracy.

## Part F — the AAC branch's remainder

### F1 — the cap fires on the band the session is on

**Conditions 1, 2, 4, 5 PASS; condition 3 unconfirmed for the second round running — reproduces round 6.** D-HU: `wifi-direct-band=2`, `use-aac-audio` deleted, `narrow-band-profile-cap=true`, `enable-audio-sink=true` confirmed true before the run.

- **Attempt 1.** Group at 2462 MHz (< 4000 MHz, condition 1). `[ServiceDiscovery] AAC audio announced by the 2.4 GHz cap (Use AAC Audio is off)` (condition 2). `[ServiceDiscovery] This session's network is on 2.4 GHz` present (condition 5, assembled). `logNarrowBandProfile`: "asked for at most 720p and 30 fps" quoted whole, `NegotiatedResolution is: 1280x720` (condition 4). Then, **10.207 s after** `SSL handshake complete` (14:14:39.687), the session dropped: `AapService: session state disconnected (link_lost)` — before Spotify could be relaunched to reach condition 3. This is the same failure signature round 6 reported.
- **Attempt 2.** A different failure: the P2P group churned (`createGroup SUCCESS` × 2 in one attempt) and the phone never answered the repeated BT pokes at all across ~90 s (known rig quirk: poke connectivity varies session to session). No handshake reached. Not counted as a repeat of the band-instability finding.
- Condition 3 (`Media Sink Setup Request: 2` + `AudioDecoder.start:` with `isAac=true, source=setup`) remains unconfirmed on hardware.

### F1b — the cap is not what is destabilizing the session

**Report, no verdict, as specified.** Identical to F1 except `narrow-band-profile-cap=false`. Three sessions:

| Session | Outcome |
|---|---|
| 1 | **Never established.** Stuck in a version-request/response retry loop (`Handshake: Version request/response failed after 3 attempt(s)`, repeating every ≈46 s) for 3+ consecutive cycles / ≈3.5 min observed, despite the Bluetooth-layer RFCOMM connection landing fine each time. Coexistence line: `WifiDirectManager: This unit is connected to another WiFi network on 5745 MHz while hosting the WiFi Direct group on 2412 MHz.` |
| 2 | Connected (SSL handshake at 14:23:xx), survived **3 min 38 s** of continuous Spotify playback with **no disconnect**, but throughput degraded from 40-49 fps down to single digits (5-13 fps) over the window, with a couple of `skipped=2` frames — a slow bleed, not a clean crash. |
| 3 | Same pattern: connected, survived **2 min 58 s**, degraded to 4-7 fps by the end, no disconnect. |

**Reading:** neither of the brief's two clean outcomes ("just as unstable" vs. "stable") is quite what happened. With the cap off, sessions either fail to establish at all or establish and then starve for throughput; none crashed mid-session the way F1's one clean attempt did. That is at least consistent with the cross-band radio split being the real physical constraint in both cap states — the cap changes the *shape* of the failure (a hard link-teardown vs. a slow bleed / failure-to-associate) rather than removing it. This is measurement, not a verdict; S1 below adds a third data point.

### S1 — the stand-down question the coexistence finding raises

**No verdict, as specified — measurement only.** `wifi-direct-band=2`, D-HU joined to D-MOTO's **5 GHz** hotspot (5180 MHz).

1. **AUTO (`stand-down-station-mode=0`).** `StationStandDown: this unit's own WiFi network is on 5180 MHz, and a group beside a 5 GHz station is the state measured to run clean; the station stays joined.` — station does stay joined, exactly as designed. But the session itself never completed: stuck at pill step `PHONE_JOINING` for **4 min 35+ s** despite three separate BT pokes each landing `Successfully poked … Connection accepted from POCO X3 NFC` (at 14:36:47, 14:37:20, 14:38:07, 14:38:50) — the RFCOMM/credentials layer worked every time, but the WiFi/TCP handoff never happened.
2. **ALWAYS (`stand-down-station-mode=1`), same hotspot.** Station left immediately (`asked this unit to leave … 5GHz=true`), and the session connected cleanly (SSL handshake ≈58 s after launch) and ran **stable**: continuous 34-41 fps for 98+ s observed, no degradation, no disconnect.

**What this shows:** AUTO's own reasoning ("a group beside a 5 GHz station runs clean") held for the narrow claim it makes (the station does stay joined without incident to itself), but under this exact band split the session under AUTO never got past `PHONE_JOINING`, while ALWAYS — which leaves the station — connected fast and stayed rock-solid. That is the asymmetry the brief flagged as an open policy gap, now measured rather than argued.

### F3 — the 16 kHz guidance channel, with a real maneuver

**INCONCLUSIVE.** D-HU/D-POCO, `use-aac-audio=true`, `enable-audio-sink=true` confirmed true, `log-level=0`, `wifi-direct-band=0`. Session connected normally (SSL handshake, all four media channels set up). Mocked D-POCO's GPS along the existing `mock_drive_route.json` (Medellín) via `cmd location providers set-test-provider-location`, started real turn-by-turn navigation in Google Maps (confirmed via the app's own UI: 6 min / 2.7 km route to a point ~1.85 km down the same route, ETA counting down, `Start` tapped) — but the phone's Android Auto session never registered any movement: speed stayed `0 km/h` throughout, and the projected maneuver banner never advanced past the very first instruction (`Cl. 110 towards Cra. 65`) across ~10 minutes of continuous mocked fixes. `AAC Decoder started for 16000 Hz, 1 channels` never appeared, not even once. Time budget (~25 min including an unrelated "Open with" chooser detour, see Setup notes) exceeded the brief's ~15-minute cap; stopped rather than pushed further. Reads as a rig limitation in how this GPS-mocking method interacts with Play Services' fused location (which Android Auto's own nav rendering consumes), not a candidate defect — see Setup notes.

## Part E — the module dialogs, reworded (added mid-round)

### E5 — evidence only, no verdict

Run against the rebuilt candidate `ec9352d5` (see Setup notes for why). `setprop rw.zlink.bt.type extra`, app force-stopped and relaunched, `wifi-connection-mode` moved away from and back to Native via Settings → search "Wireless" → tap "Native" to force the dialog fresh each time.

1. **`external-bt-zbt-transport=false`.** Title `Native Wireless cannot work on this head unit`; body: "Your phone pairs with this unit's own Bluetooth module (rw.zlink.bt.type=extra), not with the Bluetooth that Android gives to apps, so the wireless handshake would never reach it." / "This app can only reach that module through a ZJ/ZLink service, which this unit is not running. Use USB or a WiFi mode instead." Matches the brief's expected wording exactly. Screenshot: `evidence/…/e5-dialog1-no-module.png`.
2. **`external-bt-zbt-transport=true`.** Title `Native Wireless through the ZJ/ZLink module`; body: "…so the handshake will be sent through the module's ZJ/ZLink service." / "Experimental, and that one service is the only way in. If your phone never starts Android Auto, use USB or a WiFi mode." **Correctly uses future tense ("will be sent") and makes no claim that the service answered or was asked** — the one thing the brief said was worth flagging if wrong, and it is not wrong here. Screenshot: `evidence/…/e5-dialog2-via-module.png`.

Neither dialog contains an em dash or asks to export a log. Wording matches on both counts. `setprop` cleared to `none` and `external-bt-zbt-transport` restored to `false` afterward.

## 7. Report back

1. **R1 conditions 2 and 3.** Both PASS: the promotion happened, and both suppressed steps (`SECURING`, `STARTING_PROJECTION`) carry the "(not shown, the overlay owns the screen)" reason.
2. **A2 condition 2.** FAIL — the give-up line never appeared in 7 min 39 s / 19 wake cycles on one unchanged process. This is a real, separate defect in `startAutoConnectWatchdog`'s delivery; see A2 for the source trace.
3. **A2 condition 3 and A3 condition 3.** Both PASS. The offer's ASK arm reached hardware for the first time in seven rounds — twice, in fact (A2 and A3) — but via the overlay-ownership gate, independent of the broken watchdog above.
4. **A4.** UNTESTABLE — this rig's MT50 has no USB host mode (`dumpsys usb`: `host_connected=false`), confirmed directly.
5. **B2.** The `attemptInFlight` veto, quoted whole — the arm still ungraded until this round.
6. **F1 condition 3, and F1b.** F1 condition 3 remains unconfirmed (session died at 10.2 s post-handshake, reproducing round 6). F1b: 1/3 sessions never established, 2/3 survived 2+ minutes with throughput degrading to single-digit fps but no disconnect — a different failure shape from F1's clean crash, both consistent with the cross-band radio split rather than the cap itself.
7. **S1.** AUTO leaves the station joined as designed but the session itself never completed (stuck 4m35s+ at `PHONE_JOINING` despite three successful BT pokes); ALWAYS left the station and connected fast and stable (34-41 fps sustained for 98+ s). The asymmetry the brief predicted is now measured.

**Standing question:** F1 condition 3 and F3 are still the two things between the AAC work and losing its "(Experimental)" label, and both are INCONCLUSIVE again this round — F1 for the same reason as round 6 (the session doesn't survive long enough), F3 for a new reason (this rig's GPS-mocking method doesn't reach Android Auto's own fused-location consumer). Neither has a known lever on this rig yet.

## Anything the brief did not ask about

- **`ACTION_START_WIRELESS` is not a reliable re-arm immediately after `headunit://disconnect`** (D2, attempt 1): it silently no-ops via `WifiLauncherManager`'s `hasSameStartConfiguration` guard, which does not account for a disconnect having just torn the session down. `ACTION_NATIVE_AA_POKE` works instead but is far too slow (seconds of RFCOMM handshake) to ever land the specific "session already connected" race D2 is chasing — a race that may need a real, near-instant phone-side reconnect rather than anything this rig can script.
- **The offer's fix (A2/A3) and its own watchdog telemetry (A2 condition 2) are now known to be independent of each other.** The dialog reaching the screen was this round's headline pass; the dead watchdog log is a separate, quieter defect worth its own look — `autoConnectInProgress` still never resets on its own for a phone that never answers at all, it is just that nothing downstream depends on it clearing anymore.
- **`connect_hotspot.sh`** (new, in `hur-wifi-test-scripts/`) generalizes past the spaced-SSID quoting trap for any future round that needs to join a temporary hotspot from D-HU's rooted shell.
- **D-MOTO can lock with a PIN**, not always just the swipeable lock screen — behaviour differed between two points in the same round with no adb action in between. Worth planning around if a future round needs D-MOTO's screen unattended.
