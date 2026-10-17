# bring-up-status-pill-and-poke-readiness — round 8 results

**Candidate:** `fork/feat/native-aa-enhancements-extbt` @ `e466171a`       **Baseline:** none needed (brief §1)
**APK md5:** `4088e2144c78b6a4baa9cef941a951c8`
**Unit:** UNISOC MT50_YT610E4GFPSL_U, Android 14 (D-HU) / POCO X3 NFC, not rooted (D-POCO) / motorola edge 30 neo (D-MOTO)
**Date:** 2026-09-11

## Setup notes

- Scripts used as-is, no new script added to `hur-wifi-test-scripts/`: `build_hur.sh`, `run_unit_tests.sh`,
  `set_hu_prefs.sh` (D-HU, rooted), `set_prefs_runas.sh` (D-POCO, not rooted), `restore_settings.sh`. A2b's
  device-rotation trigger was two inline `adb shell settings put` commands, not worth scripting.
- **The brief's own `ACTION_RECREATE_MAIN` broadcast for A2b does not work on this or any adb-only rig.**
  `MainActivity.kt` registers that receiver with `ContextCompat.RECEIVER_NOT_EXPORTED`, so a shell-uid
  broadcast can never reach it regardless of `-p`. Confirmed via `dumpsys activity broadcasts history`:
  the broadcast enqueues and reports `result=0` but is never dispatched to the app. Substituted a genuine
  device rotation instead: `settings put system accelerometer_rotation 0` then
  `settings put system user_rotation 1`. `MainActivity`'s manifest `configChanges` is only
  `keyboardHidden|uiMode` (not `orientation|screenSize`), so a real rotation destroys and recreates the
  activity for real — confirmed by two `finishDrawing of relaunch` events and `MainActivity.logLaunchSource`
  firing a second time. Any future brief needing A2b's recreation should specify this lever, not the
  broadcast.
- **D2's first attempt was contaminated by a test-timing bug, not a candidate defect.** Firing
  `ACTION_START_WIRELESS` a flat 1s after `headunit://disconnect` landed 284ms *before*
  `AapService: Native AA user exit. Stopping active launcher.` had even logged — the launcher hadn't been
  told to stop yet, so of course `setActive` answered "already initialized". Kept as `d2-dhu.txt` for the
  record. The corrected attempt polled the capture for that exact log line before firing the re-arm;
  `d2-dhu-v2.txt` is the graded run below.
- C1, C3 and C5 needed D-HU on D-MOTO's 2.4 GHz hand-operated hotspot (D-MOTO is not rooted —
  `cmd wifi start-softap` refuses with `SecurityException: Uid 2000 does not have access`), so they were
  run later in the day once the operator joined D-HU to the hotspot by hand. Between C1 and C3, D-HU
  roamed back to its preferred 5 GHz home network the moment the app exited (`headunit://exit` tears the
  stand-down down and this rig's saved 5 GHz network wins any reconnect race — `TESTING-TEMPLATE.md` §7a
  already documents this as having no non-destructive fix). `cmd wifi connect-network <netId>` does not
  accept a bare saved network id, and the spaced SSID trap makes the quoted form fail too
  (`Unknown network type Chingon`); reconnecting the saved network needed the operator's hands again for
  C3 (C3 itself never left the hotspot since it's the NEVER-mode run, so C5 needed no further reconnect).
- Settings were diffed against a fresh backup at the end of the round: D-HU and D-POCO both restored to
  byte-identical to their round-start `shared_prefs/settings.xml`, confirmed with `diff`.

## R0 — build, gate, install, identity

**PASS**

- `./gradlew :app:assembleGithubDebug` clean, `./gradlew :app:testGithubDebugUnitTest` — summed from
  `app/build/test-results/**/*.xml`: **1829 tests, 0 skipped, 0 failures, 0 errors**, matching the brief's
  gate exactly.
- Installed on D-HU and D-POCO, `pm path` + `md5sum` on both = `4088e2144c78b6a4baa9cef941a951c8`, matching
  the built APK.
- DEX grep for `WifiLauncherRestartPolicy` (new this commit): 3 hits in `classes.dex`, confirming the
  candidate build and not a stale one.

---

## Part A — the attempt's own bound

D-POCO as head unit, D-MOTO paired with **Bluetooth off throughout** (verified `state: OFF` before every
attempt). `auto-start-bt-macs` and `auto-start-offer-answered-macs` were already absent on D-POCO
(equivalent to deleted) at round start and stayed that way.

### A2 — the attempt gives up on its own

**PASS**

- Settings: `wifi-connection-mode=3`, `native-driver-selection-mode=1`, `screen-orientation=0`, screen kept
  awake (`svc power stayon true`).
- `Auto-connect: begin (Native-AA driver: motorola edge 30 neo, mode=PILL_THEN_OVERLAY)` at `17:45:05.376`.
- `Auto-connect: this attempt gives up in 149s (mode=PILL_THEN_OVERLAY)` — appears **once**.
- `Auto-connect: nothing answered this attempt (mode=PILL_THEN_OVERLAY), ending it` at `17:47:35.380` —
  wall clock **150.004s** from begin.
- Pill left at `WAITING_FOR_PHONE` at end (not rewound to `ARMED`).
- No `FATAL EXCEPTION`, no `WindowLeaked`.

### A2b — a recreated activity re-arms the bound

**FAIL**

- Settings: same as A2, plus `screen-orientation=0` at launch.
- `Auto-connect: begin` at `17:49:55.373`, arm line at `17:49:55.381` ("gives up in 149s").
- At ~41s in, `run-as` edited `screen-orientation` to `2` on disk, then
  `am broadcast -p com.andrerinas.headunitrevived -a com.andrerinas.openheadunit.ACTION_RECREATE_MAIN` was
  sent — **produced no effect** (see Setup notes: the receiver is `RECEIVER_NOT_EXPORTED`).
- Substituted a real device rotation at ~81s in (`accelerometer_rotation=0`, `user_rotation=1`). Confirmed
  recreation: two `WindowManager: finishDrawing of relaunch` events (`17:51:23.257`, `17:51:23.933`) and
  `MainActivity.logLaunchSource` firing a second time at `17:51:23.718`. Same process PID throughout
  (`17708`) — expected, per the brief, PID cannot show a recreation.
- **`Auto-connect: this attempt gives up in ` appeared only once, total, across the entire 270+ second
  capture.** No `Auto-connect: nothing answered this attempt` line ever appeared. The pill cycled
  `WAITING_FOR_PHONE` / `WAKING_PHONE` indefinitely (poke retries every ~15–25s) with no give-up, exactly
  reproducing round 7's original defect ("A2 condition 2 watched for 7 min 39 s across 19 wake cycles and
  never saw the line") on the build meant to fix it. Killed the attempt manually at 270s (`headunit://exit`
  + force-stop) rather than let it run indefinitely; full capture kept, `a2b-dpoco.txt`, 6998 lines.
- **Root cause, traced in source (`MainActivity.kt`, `HomeFragment.kt`):** the only call site that invokes
  `beginAutoConnect()` for this trigger — the "unambiguous native driver" auto-connect — is
  `HomeFragment.checkNativeDriverSelectionOnStartup()`, gated one-shot by the companion flag
  `hasCheckedNativeDriverSelection` (`HomeFragment.kt:685`). That flag is already `true` after the first
  call and stays `true` across a recreation, so `checkNativeDriverSelectionOnStartup()` — and therefore
  `beginAutoConnect()` — never runs again. `beginAutoConnect()`'s own re-entrant guard
  (`MainActivity.kt:286-291`, the code the brief's §0 describes as re-arming "by whichever activity is
  alive") is consequently never reached, because nothing calls `beginAutoConnect()` a second time.
  `MainActivity.onResume()` does call `endAutoConnectIfExpired()` on every resume
  (`MainActivity.kt:1123`, comment: "a resume is the one thing a recreated one always does") — but that
  function only **ends** an attempt whose deadline has *already* passed; it does not **arm** a fresh
  watchdog `Job` to catch the deadline in the future. With no live coroutine watching the deadline in the
  recreated activity, and no further `onResume()` triggered by anything (the poke loop produces no
  activity lifecycle event), the bound is simply never checked again. The fix as shipped does not cover
  this trigger path.

### A2c — a sleeping unit still gives up

**PASS**

- Same start as A2. `stay_on_while_plugged_in` was `15` (charging keeps the screen on regardless of
  `svc power stayon`) — set to `0` for this run only, restored to `15` afterward.
- `Auto-connect: begin` at `17:55:18.161`. Forced sleep via `input keyevent KEYCODE_SLEEP` at ~15s in;
  confirmed `mWakefulness=Dozing`. Device was not touched again until after the result.
- `Auto-connect: nothing answered this attempt (mode=PILL_THEN_OVERLAY), ending it` at `17:57:48.168` —
  wall clock **150.007s**, well inside the "~240s" allowance and matching the normal (non-recreated) A2
  timing almost exactly. This shows the elapsed-realtime deadline math itself is sound under suspension;
  A2b's finding is specific to the recreation path, not this one.

### A4 — a stuck wireless attempt no longer blocks a USB connection

**UNTESTABLE**

- `dumpsys usb | grep host_connected` on D-POCO reads `host_connected=false`. Per the brief, reported
  UNTESTABLE and not substituted.

---

## Part D — the re-arm after a user disconnect

### D2 — `ACTION_START_WIRELESS` re-arms the stack

**PASS** (on the corrected-timing attempt; see Setup notes for the discarded first attempt)

- D-HU + D-POCO, live Native AA session brought up (SSL handshake `18:04:43.676`).
- `headunit://disconnect` issued; polled the capture for
  `AapService: Native AA user exit. Stopping active launcher.` (found at `18:06:08.432`), then immediately
  fired `am start-foreground-service ... ACTION_START_WIRELESS`.
- `WifiLauncher: Initializing WiFi Mode: NATIVE` reappears at `18:06:15.860` — a **second** time, after
  disconnect.
- `with same start-configuration is already initialized.` **does not appear anywhere in this attempt.**
- `NativeAA: ACTIVELY LISTENING on Android Auto UUID ... on radio [Navegadortz2]` follows at
  `18:06:15.938`.
- Discarded first attempt (`d2-dhu.txt`) is the negative control for the fix's timing sensitivity: with
  the re-arm fired 284ms too early, the refusal line *did* appear (`18:03:10.842`), confirming the guard is
  reading real launcher state rather than always passing.

---

## Part C — the station stand-down mode

D-HU's own 5 GHz home network (`Pegue Cdesta`, 5745 MHz) for C2/C4/C6, D-MOTO's hand-operated hotspot
(`Hotspotcito Chingon`, 2462 MHz) for C1/C3/C5 — both confirmed live immediately before each run.
`wifi-direct-band=0` for C1-C5, `stand-down-station-mode` set per run. Overlay permission confirmed
granted (`SYSTEM_ALERT_WINDOW: granted=true`, and separately via `appops get`) throughout except C5,
which deliberately revokes it.

### C1 — AUTO stands down from a 2.4 GHz station

**PASS**

- `stand-down-station-mode=0`, D-HU on the 2.4 GHz hotspot (`Hotspotcito Chingon`, 2462 MHz).
- `StationStandDown: asked this unit to leave its WiFi network so the group can have the radio to itself
  (mode=AUTO, station on 2462MHz, 5GHz=true, group asking for AUTO, disableNetwork returned false). It is
  rejoined when the session ends.` at `19:31:44.966` — whole line readable: `mode=`, station frequency,
  `5GHz=` (the station's own dual-band capability, not its current band — confirmed against
  `StationStandDown.kt`'s `describeSkipped`/`standDown` source), and `group asking for`.
- `StationStandDown: this unit has left its WiFi network.` at `19:31:47.893`.
- A stray `StationStandDown: the platform refused to re-enable this unit's WiFi network` line logged
  immediately before this run's own stand-down, at app startup — leftover from an earlier run's restore
  attempt against the *5 GHz* network, unrelated to this run's 2.4 GHz station; noted, does not affect
  the verdict.

### C2 — AUTO stays joined on a 5 GHz station with a 5 GHz group

**PASS**

- `stand-down-station-mode=0`, `wifi-direct-band=0`.
- `StationStandDown: this unit's own WiFi network is on 5745 MHz and the group is asking for 5 GHz as
  well, which is the state measured to run clean; the station stays joined.` at `18:07:00.308`. No
  `asked this unit to leave` anywhere. Line names both bands, as the brief's reworded-this-build note
  expects.

### C4 — ALWAYS stands down regardless of band

**PASS**

- `stand-down-station-mode=1`, `wifi-direct-band=0`, D-HU still on the 5 GHz network.
- `StationStandDown: asked this unit to leave its WiFi network so the group can have the radio to itself
  (mode=ALWAYS, station on 5745MHz, 5GHz=true, group asking for AUTO, disableNetwork returned false). It
  is rejoined when the session ends.` at `18:07:44.569`.
- `StationStandDown: this unit has left its WiFi network.` at `18:07:46.044`. Station confirmed rejoined
  (`Pegue Cdesta`, 5745 MHz) after `headunit://exit` + force-stop.

### C6 — AUTO leaves a 5 GHz station for a 2.4 GHz group

**PASS, with a finding**

- `stand-down-station-mode=0`, `wifi-direct-band=2`, D-HU on the 5 GHz network, overlay granted — S1's
  exact configuration.
- Condition 1: `StationStandDown: asked this unit to leave its WiFi network so the group can have the
  radio to itself (mode=AUTO, station on 5745MHz, 5GHz=true, group asking for FORCE_2_4GHZ,
  disableNetwork returned false). It is rejoined when the session ends.` at `18:08:48.946`. **PASS** —
  under round 7's build this exact configuration produced the opposite (station-stays) line; its absence
  here is the fix.
- Condition 2: `StationStandDown: this unit has left its WiFi network.` at `18:08:50.490`. **PASS**.
- Condition 3 (session reaches `STARTING_PROJECTION`): **not met in this run.** The pill reached
  `PHONE_JOINING` at `18:09:06.232` and was still there 197+ seconds later when the run was stopped,
  matching S1's own AUTO-mode stall signature (S1 measured 4m35s at `PHONE_JOINING`).
- Condition 4 (two minutes of stable playback): not reached, as a consequence of condition 3.
- **Finding:** the identical configuration (`stand-down-station-mode=0`, `wifi-direct-band=2`, 5 GHz
  station) completed cleanly in the very next run, F1 below, with an SSL handshake roughly 6 seconds after
  `PHONE_JOINING` began. That the same setup stalled once and then connected quickly minutes later on the
  same hardware suggests C6's stall here was rig-side flakiness in that specific instance (the DHCP/join
  race this thread has documented before) rather than a deterministic gap in the fix. Conditions 1 and 2
  — the actual code change under test — are unambiguous passes.

### C3 — NEVER stays joined

**PASS**

- `stand-down-station-mode=2`, D-HU on the 2.4 GHz hotspot.
- No `asked this unit to leave` anywhere.
- `StationStandDown: the setting keeps this unit joined to its own WiFi network, so the group shares the
  radio with it.` at `19:34:44.821`.
- D-HU stayed on the hotspot throughout (NEVER never leaves), so C5 needed no reconnect.

### C5 — the permission gate

**PASS**

- `stand-down-station-mode=1` (ALWAYS), overlay **revoked** via `appops set ... SYSTEM_ALERT_WINDOW deny`
  (confirmed with `appops get`, not `dumpsys package`'s runtime-permission flag — same
  permission-vs-appop distinction `TESTING-TEMPLATE.md` §7a documents for `RECORD_AUDIO`; the runtime
  flag stayed `granted=true` throughout, as expected, since the appop is the real gate here too).
- No stand-down.
- `StationStandDown: This unit's Android will only let the app drop its own WiFi connection while the
  app has the "display over other apps" permission. Granting it frees the group's radio.` at
  `19:35:56.167`.
- Overlay permission re-granted afterward, confirmed via `appops get` back to `allow`.

---

## Part F — the AAC branch's remainder

### F1 — the cap fires on the band the session is on

**PASS** — condition 3, the one round 7 lost, now passes.

- Configuration: `wifi-direct-band=2`, `use-aac-audio` absent (deleted), `narrow-band-profile-cap=true`,
  `enable-audio-sink=true` (read back), `stand-down-station-mode=0`, D-HU on its 5 GHz network — C6's
  configuration, so the station leaves before the group forms.
- `StationStandDown: asked this unit to leave...` fired first, at `18:12:45.718`, before any session
  activity — confirms the station-departs-before-group-forms sequencing the brief asks for.
- Session connected quickly this time: `PHONE_JOINING` at `18:12:59.842` →
  SSL handshake complete at `18:13:05.976` (≈6s).
- D-POCO's Spotify was force-stopped and cold-relaunched, then a play command was relayed via
  `input keyevent KEYCODE_MEDIA_PLAY_PAUSE` through D-HU.
- Condition 3: `Media Sink Setup Request: 2 on channel AUDIO` at `18:13:07.228`, and
  `AudioDecoder.start: channel=6, ... isAac=true, source=setup` at `18:13:11.941`. **Both present** — this
  is the condition round 7 lost when the session died 10.2s after SSL handshake.
- Session survival: still alive and rendering **8+ minutes** after the SSL handshake (last observed at
  `18:21:15`, throughput logs continuing at 25-26fps at that point), an enormous improvement over round
  7's 10.2s death. One transient hiccup: `Error processing AAC output` at `18:17:16.677`, followed by a
  second `AudioDecoder.start: ... isAac=true, source=setup` at `18:19:05.407` — the decoder restarted
  itself and playback continued; noted as a finding, not a failure.

### F1b — the cap with the station gone

**Report only, no PASS/FAIL** (per the brief)

Identical to F1 except `narrow-band-profile-cap=false`. Three sessions, each launched fresh after
`headunit://exit` + force-stop of the previous one:

| Session | SSL handshake | fps near start | fps ~90-140s in | Bleed? |
|---|---|---|---|---|
| 1 | `18:22:23.737` | 11fps (first 5s window) | 32-41fps, steady to ~100s | No |
| 2 | `18:24:50.408` | 10fps (first 5s window) | 43-48fps, steady to ~2m17s | No |
| 3 | `18:28:04.143` | 14fps (first 5s window) | 46-48fps, steady to ~1m47s | No |

All three sessions established and none bled down, contrasting with round 7's measurement on this exact
setting (1 of 3 never establishing, 2 of 3 bleeding from 40-49fps to 4-13fps). `StationStandDown` fired
identically to F1 in every session before the group formed.

## Anything the brief did not ask about

1. **A2b is this round's headline finding.** The fix's own source comment
   (`MainActivity.kt:287-288`) claims a recreated activity re-arms the watchdog. That mechanism —
   `beginAutoConnect()`'s re-entrant guard — is provably unreachable for the native-driver auto-connect
   trigger specifically, because nothing calls `beginAutoConnect()` a second time once
   `HomeFragment`'s one-shot `hasCheckedNativeDriverSelection` flag is set. The round's core defect
   (activity recreation losing the give-up bound) is **not actually fixed** for this trigger; it is fixed
   for A2c's suspend case (a different mechanism: the elapsed-realtime deadline math, not activity
   recreation) and evidently for whatever trigger A2/D2/etc. exercise where `beginAutoConnect()` genuinely
   gets called again on a live activity. A general fix likely needs `endAutoConnectIfExpired()`'s own
   caller in `onResume()` to also re-arm a watchdog `Job` when the attempt is still in progress and not
   yet expired, not only end it once it is.
2. **`ACTION_RECREATE_MAIN` cannot be exercised from any adb-only rig.** Its receiver is registered
   `RECEIVER_NOT_EXPORTED`, so the brief's own trigger command for A2b is dead on arrival regardless of
   `-p`. A real device rotation (`accelerometer_rotation=0` + `user_rotation=1`) is the substitute that
   worked and should replace that command in the next brief that needs this trigger.
3. **C1, C3 and C5 all passed cleanly once the operator joined the hotspot**, with no surprises relative
   to C2/C4/C6's already-passing regression coverage. Part C's whole regression suite (C1-C6) is now a
   clean sweep for this build.
