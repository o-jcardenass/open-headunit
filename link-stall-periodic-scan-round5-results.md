# link-stall-periodic-scan — round 5 results

**Candidate:** `fork/fix/wifi-direct-lifecycle` @ `224cae32`, plus one local fix `307d85f2` (see Setup
notes) — no baseline, one APK for the whole round.
**APK md5:** `33e5403fc0dfe2389fac11a590e186d3`
**Unit:** UNISOC MT50_YT610E4GFPSL_U, Android 14, `c2.unisoc.hevc.decoder`/`c2.unisoc.avc.decoder`
hardware codecs, phone POCO X3 NFC (`surya`), MIUI, API 27 sensor absent — this rig's own scan cadence
is UNISOC's, not the reporters' MediaTek `ac8227l` (see brief §2, honored throughout below).
**Date:** 2026-08-19/20

**The brief was revised mid-round** (commit `02e5dbbc`, pushed while R1-R2 were already captured):
the `wifi_scan_always_available` arm was withdrawn as a no-op while WiFi is enabled, and R3 was
redefined from "scanning suppressed" to "what actually sets the scan cadence" — a 15-minute
characterization run against AOSP's 20/40/80/160s periodic-scan schedule, with a display off/on
probe. R0-R2 and R4 below were run and reported against the *original* brief before the revision
landed; **the R3 write-up below is the redefined run**, executed after rebasing onto the revised
brief. The original R3 (`wifi_scan_always_available=0`, no suppressive effect observed) is kept
below for the record since it independently corroborates the revision's own correction — the
observed *lack* of an effect was already the right empirical answer, just not the interesting
question.

## Setup notes

- `hur-wifi-test-scripts/` inventory: `build_hur.sh`, `run_unit_tests.sh`, `install_and_launch.sh`,
  `set_hu_prefs.sh` used as-is. `recv_gaps.py`, `wire_bitrate.py` reused from rounds 3-4.
  `scan_vs_gaps.py` added this round exactly as specified in the brief, saved to
  `hur-wifi-test-scripts/scan_vs_gaps.py`.
- **R0's unit-test gate failed on first run and was fixed, off the pinned SHA.** 552 tests ran
  (matching the brief) but `P2pOperatingChannelPolicyTest` — `the 2_4 GHz channels use the other
  base, so a mix-up would be visible` — failed deterministically:
  `P2pOperatingChannelPolicy.frequencyMhzFor(14)` returned 2477 MHz via the linear formula
  (`2407 + channel*5`), but channel 14 is a real-world special case (2484 MHz, breaks the normal
  5 MHz spacing, Japan-only) that the implementation didn't special-case while the test asserted the
  correct real value. Escalated to the user per the brief's own "a FAIL stops the round" rule and
  the standing house rule on build-gate failures; user chose to fix and continue. Fix: one line,
  `channel == 14 -> 2484` added ahead of the `channel <= 14` branch in
  `P2pOperatingChannelPolicy.kt:82-86`, committed as `307d85f2` on top of `224cae32`. Rebuilt,
  552/552 pass. The bug is inert for this round and for runtime in general: the app only ever
  requests channel 36 or 149 (`CHANNEL_LOWER`/`CHANNEL_UPPER`), never 14.
- **R2's network join needed a password the auto mode classifier correctly blocked from an adb
  command line.** `cmd wifi connect-network` has no by-saved-ID reconnect form, only
  `<ssid> <security> <passphrase>`. The user connected the head unit to `Pegue Cdesta` manually
  instead. Between R2 and R3 the network was removed with `cmd wifi forget-network 2` (no password
  needed) to cleanly return the station to unassociated for R3 — **a future round wanting 5 GHz
  association again will need the user to reconnect it manually**, same as this round.
- Swipes were issued with `adb shell input swipe 200 700 500 700 300` (720x1440 physical screen,
  a generic horizontal pan) in a 25 s-cadence background loop; no existing script did this, and one
  wasn't added since it's a two-line loop specific to each run's duration.
- Spotify was driven with the standard `force-stop` + `monkey -c LAUNCHER` pattern from round 3,
  waiting the rig's documented ~26 s monkey injection delay, then confirmed `PAUSED` via
  `dumpsys media_session` and resumed with `input keyevent 126` sent through the head unit (this
  reliably produced `state=PLAYING` every time — worth noting since Spotify opening paused, not
  auto-playing, is itself new information for this rig).
- A first attempt at a live Spotify-state watchdog (Monitor tool, polling `dumpsys media_session`
  during R1) had a regex bug — `grep -oE "state=[A-Z]+"` matched the wrapper token
  `state=PlaybackState` before the real inner `state=PLAYING`/`state=PAUSED`, so it produced one
  false alarm and then went silently useless. Stopped it and relied on the post-run AUDIO coverage
  ratio instead, which is authoritative per round 3's own methodology.
- R0's short-capture scan-tag probe needed 125 s (35 s + 90 s), not the single short capture the
  brief implies, before a scan marker appeared — record this if a future round's R0 wants a faster
  gate.
- **The redefined R3's 15-minute capture crossed local midnight (23:53→00:10)**, which broke both
  `recv_gaps.py` and `wire_bitrate.py` — their `secs()` helper parses only the `HH:MM:SS` field, so a
  capture spanning the day boundary reads as a ~24h span with a huge bogus dead-time figure. Fixed by
  a one-off normalization pass (add 24 to the hour field on every line dated after the first date seen
  in the capture) before running the analysis scripts; not added as a permanent script change since
  it's a rare edge case, but worth a `secs()` fix in `recv_gaps.py`/`wire_bitrate.py` if midnight-
  crossing captures become routine.
- **`settings.xml` was restored to the pre-round backup after R4**, before the brief's mid-round
  revision was discovered — this reverted `video-codec`/`view-mode`/`force-software-decoding` back to
  their pre-round values. Caught immediately (settings verify before the redefined R3's capture
  started) and the round's common settings were re-applied with `set_hu_prefs.sh` before proceeding.
  A final restore-and-diff was done again at the true end of the round, after the redefined R3.
- The `NetworkLocationScanner`/`WificondScannerImpl Scan Timeout` `AlarmManager` lines that turned out
  to be the round's most useful new evidence are **not** matched by the brief's own scan-tag regex
  (`startScan|WifiScanRequestProxy|WifiScanningService|SCAN_RESULTS|scan results|ScanRequestProxy|
  WifiNative.*[Ss]can`) — they were found by a manual, targeted `grep -n "callingPackage"` search
  around the unexplained gaps after the schedule analysis raised the question. Worth adding
  `AlarmManager.*Scan|NetworkLocationScanner` to the standard regex for the next round on this thread.

## R0 — gate and preconditions

**PASS** (after the fix above)

- `build_hur.sh`, `run_unit_tests.sh`: 552/552 after the `P2pOperatingChannelPolicy` fix. APK md5
  `33e5403fc0dfe2389fac11a590e186d3`.
- `settings.xml` backed up before any change; restored and diffed byte-for-byte identical at the end
  (see final section).
- **API 29+ band regression check, quoted verbatim:**
  ```
  22:52:25.695 WifiDirectManager: Requesting Native AA P2P group on 5GHz band.
  22:52:25.730 WifiDirectManager: 5GHz createGroup SUCCESS!
  ```
  No line mentioning an operating channel appeared anywhere in the probe capture; no
  `setWifiP2pChannels`/`P2pOperatingChannelPolicy`/`CHANNEL_LOWER`/`CHANNEL_UPPER` reference either.
  The pre-Q path is confirmed unreachable on this Android 14 unit.
- **Scan tags this unit actually prints** (from a 125 s combined probe capture, one leftover
  `debug-force-memory-profile=CONSTRAINED` key from a prior round was cleared as part of common
  setup):
  ```
  D/WifiNl80211Manager( 1075): Scan result ready event
  D/WifiNative( 1075): Scan result ready event
  D/ActivityManager( 1075): Skip enqueue broadcast because no receiver with Intent { act=android.net.wifi.SCAN_RESULTS ... }
  ```
  `WifiNl80211Manager` and `WifiNative` fire the identical line at the same timestamp for every real
  scan completion (one genuine event, two log call sites) — deduplicated on `WifiNative` alone for
  every count below. Only 1 event appeared in the first 35 s; extending to 125 s total was needed to
  confirm the instrument works at all. Regex used throughout: `Scan result ready event|SCAN_RESULTS`.

## R1 — station enabled, not associated

**PASS** (zero stalls; this is the brief's own expected/predicted outcome, not a failure)

- Settings: `log-level=0`, `wifi-connection-mode=3`, `view-mode=0`, `fps-limit=60`,
  `force-software-decoding=false`, `video-codec=H.264`, no fault-injection/debug-force keys.
- Station state: `dumpsys wifi` → `Supplicant state: DISCONNECTED` before launch and at run end
  (unchanged) — this is the rig's own default state, unforced.
- Discard-rule check: clean. `AapService.onCreate`=1 (genuine; a second textual match was a stack
  frame inside a caught, benign `BluetoothHelper.adapterForService` reflection failure, not a second
  call), `createGroup SUCCESS`=1, `MATCH! Starting AapService`=1 (the documented self-wake from our
  own poke — the brief's stated benign exception), `p2p-wlan0-4`→`p2p-wlan0-5` (index bump across a
  previous teardown — also the stated benign exception), `Magic Garbage`=0, one real SSL handshake
  (logged twice from `AapSslContext.performHandshake` and `AapTransport.handshake`, same timestamp).
- Swipes issued: 24 (10 min at 25 s cadence).
- `recv_gaps.py`: `RECV lines 41357 over 733.3s`, `stalls > 1.2s 0`, `dead time 0.0s`, `audio
  delivered 192.1 kB/s = 100.0% of real time`.
- `wire_bitrate.py`: AUDIO 1.417 Mbit/s, VIDEO 0.564 Mbit/s, 23174 frame starts = 31.6 fps.
- **AUDIO coverage** (wire_bitrate kB/s ÷ recv_gaps kB/s): 177.1 / 192.1 = **92.2%**.
- Underruns (`disabled due to previous underrun`): 0. `inbound link quiet`: 0.
- `scan_vs_gaps.py`: `scan markers 18, stalls > 1.2s 0` (18 counts both `WifiNl80211Manager` and
  `WifiNative` lines plus the `SCAN_RESULTS` broadcast skip; deduplicated to **7 distinct scan
  events**). Distinct-event gaps: 27.0s, 155.4s, 4.4s, 160.2s, 160.2s, 37.1s — **mean 90.7s, median
  96.3s**, nowhere near the theorized 8.1-11.6s dense cadence. First scan didn't fire until 94 s
  after the SSL handshake completed.

## R2 — station associated to a 5 GHz network

**PASS**

- Same settings as R1. Station joined to `Pegue Cdesta` (5500 MHz, RSSI -33, `Supplicant state:
  COMPLETED`) before launch, confirmed unchanged at run end.
- Discard-rule check: clean. `onCreate`=1, `createGroup SUCCESS`=1, `MATCH!`=1 (benign self-wake),
  single `p2p-wlan0-6` (no index bump this run), `Magic Garbage`=0, one real SSL handshake.
- Swipes issued: 24.
- `recv_gaps.py`: `RECV lines 43516 over 665.4s`, `stalls > 1.2s 0`, `audio delivered 192.1 kB/s =
  100.0%`.
- `wire_bitrate.py`: AUDIO 1.445 Mbit/s, VIDEO 0.583 Mbit/s, 26735 frame starts = 40.2 fps.
- **AUDIO coverage:** 180.6 / 192.1 = **94.0%**.
- Underruns: 0. `inbound link quiet`: 0.
- `scan_vs_gaps.py`: `scan markers 6, stalls > 1.2s 0` → **3 distinct scan events** over 665.4s
  (gaps 183.6s, 451.2s).

**The R1 vs R2 comparison (the round's primary result):** associating the station roughly halved the
scan rate — R1 1 scan per 104.8s vs R2 1 per 221.8s (7 events/733s vs 3 events/665s) — confirming the
brief's directional claim that an unassociated-but-enabled station scans harder than an associated
one. But R1 already had **zero** stalls, so there is no reporter-style clean-vs-sick stall split to
reproduce on this hardware: both arms are clean, one just scans less than the other.

## R3 (original brief, superseded) — scanning suppressed

**PASS** — setting took, no observable effect, reported as the brief instructs ("do not spend time
forcing it"). **Superseded by the redefined R3 below**; kept because the null result independently
confirms the revision's own finding that `wifi_scan_always_available` does nothing while WiFi is on.

- R1's settings and station state again (unassociated, confirmed via `Supplicant state: DISCONNECTED`
  before and after), plus `settings put global wifi_scan_always_available 0` — verified `0` before
  launch and `0` at run end. **Restored to its original unset (`null`) state** after the round via
  `settings delete global wifi_scan_always_available`.
- Discard-rule check: clean. `onCreate`=1, `createGroup SUCCESS`=1, `MATCH!`=1, single
  `p2p-wlan0-7`, `Magic Garbage`=0, one real SSL handshake.
- Swipes issued: 24.
- `recv_gaps.py`: `RECV lines 50232 over 673.8s`, `stalls > 1.2s 0`, `audio delivered 192.1 kB/s =
  100.0%`.
- `wire_bitrate.py`: AUDIO 1.429 Mbit/s, VIDEO 0.685 Mbit/s, 33630 frame starts = 49.9 fps.
- **AUDIO coverage:** 178.6 / 192.1 = **93.0%**.
- Underruns: 0. `inbound link quiet`: 0.
- `scan_vs_gaps.py`: `scan markers 23, stalls > 1.2s 0` → **9 distinct scan events** over 673.8s
  (1 per 74.9s) — **higher** than R1's 7 events/733s (1 per 104.8s), not lower. The setting had **no
  suppressive effect** on this unit's scan cadence; if anything the difference runs the wrong
  direction, consistent with normal run-to-run noise on a small sample rather than a real increase.
  `wifi_scan_always_available` on this Android 14/UNISOC stack appears to govern scanning *while WiFi
  is off* (per its own `cmd wifi status` wording: "Wifi scanning is only available when wifi is
  enabled"), not the periodic scan of an enabled-but-disconnected station, which is what R1/R3 both
  exercise.

## R3 (redefined) — what actually sets the scan cadence on this unit

**PASS.** 15-minute live session (unassociated station, same as R1), screen untouched for the first
10 minutes, then two display off/on power cycles (`input keyevent 26` ×4), then 5 more minutes.
Capture crossed local midnight (23:53-00:10); timestamps normalized before analysis (see Setup
notes) — raw capture is `r3b_capture.txt`, normalized copy `r3b_capture_norm.txt`.

- Settings and station state: same as R1 (unassociated, `Supplicant state: DISCONNECTED` before and
  after).
- Discard-rule check: clean. `onCreate`=1, `createGroup SUCCESS`=1, `MATCH!`=1 (benign), two
  `p2p-wlan0-{0,1}` (benign index bump, exception conditions both verified), `Magic Garbage`=0, one
  real SSL handshake. No `onDisconnected`/`Unexpected disconnect`/underrun anywhere — **the display
  power-toggle did not disrupt the AAP session at all**, consistent with the session living on the
  WiFi P2P link rather than the display state.
- Swipes issued: 36 (25s cadence for the full ~16.5 min span, continuing through the toggle window).
- `recv_gaps.py` (normalized capture): `RECV lines 72880 over 998.8s`, `stalls > 1.2s 0`, `audio
  delivered 192.0 kB/s = 100.0% of real time`.
- `wire_bitrate.py`: AUDIO 1.461 Mbit/s, VIDEO 0.761 Mbit/s, 47295 frame starts = 47.4 fps.
- **AUDIO coverage:** 182.6 / 192.0 = **95.1%**.
- Underruns: 0. `inbound link quiet`: 0.
- **The `dumpsys wifi | grep -aiE "periodic single scan|full band scan|scan"` instrumentation the
  brief asked for produces nothing on this unit.** `Dump of WifiConnectivityManager` →
  `WifiConnectivityManager - Log Begin ----` / `... Log End ----` with **no lines between them** —
  the `localLog` is empty on this Android 14/UNISOC build. `Last periodic single scan started` and
  `No full band scan due to ongoing traffic` never appear anywhere in the dump. This instrument is
  dead on this hardware; the schedule characterization below relies entirely on the logcat
  `WifiNative: Scan result ready event` markers.
- **14 distinct scan events over 998.8s** (dedup'd on `WifiNative`, same method as R1-R3-original).
  Timestamps and gaps (toggle window 00:05:22-00:05:32, marked):
  ```
  23:55:21.205
  23:56:16.798   +55.593s
  23:58:23.379   +126.581s
  23:58:56.951   +33.572s
  00:01:25.510   +148.559s
  00:01:37.003   +11.493s
  00:04:17.156   +160.153s
  00:05:31.884   +74.728s   <- inside the toggle window (toggle ended 00:05:32)
  00:05:51.683   +19.799s
  00:06:31.921   +40.238s
  00:07:28.033   +56.112s
  00:07:52.811   +24.778s
  00:10:29.796   +156.985s
  00:10:34.091   +4.295s
  ```
- **Does the cadence match AOSP's 20/40/80/160s schedule?** Partially, and only right after the
  toggle. The two gaps immediately following the toggle (**19.8s, then 40.2s**) are a close match to
  `PERIODIC_SCAN_INTERVAL_MS=20s` followed by its first doubling — consistent with the brief's
  `handleScreenStateChanged()` → `startPeriodicScan()` reset hypothesis. But the ramp does not
  continue cleanly: the next gap is 56.1s, not ~80s, and the one after that drops back to 24.8s,
  which the doubling schedule cannot produce at all. Pre-toggle gaps (55.6, 126.6, 33.6, 148.6, 11.5,
  160.2s) do not fit the clean progression either, though two values (33.6s, 160.2/157.0s) land
  within ~15% of schedule steps. **Verdict: suggestive but not clean** — real support for a reset
  effect at the toggle, no support for a sustained pure-AOSP ramp either side of it.
- **A real, named third scanner explains the pattern far better than the AOSP schedule alone.**
  `com.google.android.gms`'s `NetworkLocationScanner` alarm (`AlarmManager: ... listenerTag=
  NetworkLocationScanner ... callingPackage=com.google.android.gms callingUid=10101`, work-sourced to
  `WorkSource{1001 com.unisoc.phone}`) fires within **30-200ms of nine of the fourteen scan events**,
  including three matches inside 40ms:
  ```
  23:55:21.245 NetworkLocationScanner alarm  ->  23:55:21.205 scan   (Δ40ms)
  24:01:25.540 NetworkLocationScanner alarm  ->  24:01:25.510 scan   (Δ30ms)
  24:07:28.062 NetworkLocationScanner alarm  ->  24:07:28.033 scan   (Δ29ms)
  24:10:29.913 NetworkLocationScanner alarm  ->  24:10:29.796 scan   (Δ117ms)
  ```
  This is Google Play Services' network-location WiFi scan, attributed to a UNISOC vendor work
  source, not a pure-AOSP `WifiConnectivityManager` periodic scan and not a third-party app in the
  sense the brief meant — but it **is** the "unexplained scan, name the calling package" case the
  brief asked for. It also means the two clean post-toggle gaps (19.8s/40.2s) can't be attributed to
  the AOSP reset alone with confidence: `NetworkLocationScanner` alarms sit at 00:05:25.491 and
  00:05:28.202, inside/adjacent to the same window, and location scanners are known to run more
  aggressively right after a screen wake for their own reasons.
  Two `WificondScannerImpl Scan Timeout` alarms (`callingPackage=android callingUid=1000`) were also
  found near the two shortest, most schedule-incompatible gaps (11.5s, 4.3s) — these are the
  framework's own scan-response watchdog, not a trigger, so they explain the *infrastructure* around
  those events without explaining *why* a scan fired there.
- **`scan_vs_gaps.py`** (normalized capture, includes duplicate line-pair counting like R1-R3):
  `scan markers 37, stalls > 1.2s 0`, `scan interval mean=25.4s max=160.1s` (raw, undeduplicated —
  see the manual dedup above for the real per-event figures).
- **§5 answer for the redefined R3:** the framework's pure 20/40/80/160s schedule is **not** the sole
  or even primary explanation on this unit — a real, named third scanner (`com.google.android.gms`
  `NetworkLocationScanner`, vendor work-sourced) correlates far more tightly with the observed events
  than the AOSP ramp does. Per the brief's own framing, this keeps the theory alive as "a vendor/
  location scanner, not a pure-framework one" for explaining a period the framework's own schedule
  cannot produce — matching the brief's second named alternative rather than refuting the mechanism.
  Whether the reporters' Android 8.1 units run an equivalent location-scanning component, unthrottled
  and possibly at a different cadence, is outside what this rig can answer.
- **Gap shape (§5's new criterion):** not applicable — R1, R2 and both R3 runs had **zero** stalls
  > 1.2s throughout, so there is no stall list to characterize as bursty vs. a solid hole. Reported
  as the brief instructs: a number (zero), not papered over.

## R4 — the lifecycle fixes

**PASS** on both checks.

1. **Re-arm after a user exit.** Established a session, exited via `headunit://exit` (confirmed
   `isUserExit=true`, `Native AA user exit. Stopping handshake manager.`, `Not restarting discovery`
   — the app correctly does *not* self-restart after a real user exit). Relaunched the app (the
   scripted equivalent of the user reconnecting): listeners reopened
   (`ACTIVELY LISTENING on Android Auto UUID`), a fresh group formed
   (`5GHz createGroup SUCCESS!`), the phone reconnected and completed a full second session
   (`Connection accepted from`, `Incoming connection detected from /192.168.49.57`,
   `SSL handshake complete`, hardware AVC decoder init). **No** `Native AA join watchdog` teardown
   fired against the newly joined group — the only watchdog-tagged lines in this window were a
   benign `removeGroup before recreate failed (reason=BUSY)` at startup (the brief's own documented
   benign line) and the unrelated video decoder's own keyframe-request watchdog.
2. **Manual poke button after a completed session.** Took three attempts to isolate correctly (see
   Setup notes below) since the branch's own auto-recovery reopens listeners within ~1.5s of an
   *unexpected* disconnect, and a freshly-created service opens listeners as part of its own
   `onCreate`/`initWifiMode()` — both of which mask the re-arm branch by the time the poke intent's
   handler runs. The clean isolation: relaunch → let the session complete a **full** handoff
   including the WiFi/TCP side (`AA Server socket closed after successful handoff` +
   `Incoming connection detected` + `SSL handshake complete`, all present, distinct from the BT-only
   partial handoffs seen in the earlier attempts) → immediately fire
   `ACTION_NATIVE_AA_POKE` at the connected phone's MAC on the *same, still-running* service instance
   (no disconnect, no restart). Result, quoted verbatim:
   ```
   23:47:53.485 AapService: Received manual Native-AA poke request for DC:B7:2E:5E:4E:59
   23:47:53.486 AapService: Native AA listeners are closed — re-arming before the poke.
   23:47:53.538 NativeAA: ACTIVELY LISTENING on Android Auto UUID ... Waiting for phone to connect back!
   23:47:58.011 NativeAaHandshakeManager.pokeDevice: Calling socket.connect() for POCO X3 NFC via HFP-AG...
   23:47:59.362 NativeAaHandshakeManager.pokeDevice: Successfully poked POCO X3 NFC via HFP-AG. Holding 15000ms...
   ```
   Confirms both halves of the fix: listeners reopen on demand, and the poke that would previously
   "do nothing at all" now runs. `Setup note:` the poke intent is scriptable via an explicit
   component even though `AapService` is `exported=false` (adb shell is exempt):
   ```
   adb shell am start-foreground-service \
     -n com.andrerinas.headunitrevived/com.andrerinas.openheadunit.aap.AapService \
     -a com.andrerinas.openheadunit.ACTION_NATIVE_AA_POKE \
     --es extra_mac "<paired BT MAC>"
   ```

## §5 criteria, answered

1. **Mechanism present?** No. R1 had zero stalls > 1.2s, so the "≥60% within 2s of a scan +
   matching interval" test never had anything to evaluate. True in every arm (R1, R2, both R3 runs).
2. **Mechanism absent?** **Yes — this is the outcome.** R1: 0 stalls > 1.2s. Per the brief this is a
   **PASS of R1**, not a failure, exactly as pre-registered.
3. **R2 comparison:** associating the station roughly halved the scan rate (7/733s → 3/665s), which
   reproduces the *directional* mechanism (unassociated scans harder) even though there was no stall
   difference to go with it — both arms are clean on this hardware.
4. **R3 (redefined) — does the theory survive?** Not on pure AOSP-schedule grounds alone: the clean
   20/40/80/160s ramp only appears (partially, two steps) right after the display toggle, and is
   absent elsewhere. But it survives via a **named third scanner**: `com.google.android.gms`'s
   `NetworkLocationScanner`, work-sourced to `com.unisoc.phone`, correlates within 30-200ms of 9 of
   14 scan events across the whole run. Per the brief's own framing this is exactly "a vendor/
   location scanner… to stay alive" — the theory is not refuted, it is narrowed to needing an
   equivalent location-scanning component on the reporters' units, which this rig cannot confirm or
   deny for their Android 8.1 build.

**Per the brief's own §2 limit: none of this refutes the theory for `ac8227l` on Android 8.1.** This
UNISOC/Android-14 rig's scan cadence (mostly tens to ~160s between events, with brief exceptions down
to 4-12s that are themselves explained by framework/GMS infrastructure, not the reporters' mechanism)
is 1-2 orders of magnitude sparser than MediaTek's stated 8.1s dual-band-scan constant, and never
produced a stall across the full ~2600s of combined R1/R2/both-R3 capture. The honest conclusion is
exactly what §2 anticipated: **this rig cannot carry #839/#824's fault at all** — its own radio does
not blank the P2P group on the timescale the theory needs — but it *does* newly show that a specific,
named, non-AOSP scanner (GMS location services) is live and firing on cadence-relevant hardware here,
which is worth checking for on the reporters' side if their own logs are ever revisited.

## `wifi_scan_always_available` — restore confirmation

Set to `0` for the original R3, verified `0` immediately before and after that run, then explicitly
deleted (`settings delete global wifi_scan_always_available`) to return it to its original unset/
`null` state, confirmed by a `settings get` returning `null`. Not touched again for the redefined R3
(withdrawn per the brief revision, so left at its restored default).

## Discard-rule counts, all captures

| Capture | onCreate | createGroup SUCCESS | MATCH! | p2p-wlan0 index | Magic Garbage | Verdict |
|---|---|---|---|---|---|---|
| R0 probe | — | 1 | 0 | — | 0 | n/a (no session) |
| R1 | 1 | 1 | 1 (benign) | 4→5 (benign) | 0 | clean |
| R2 | 1 | 1 | 1 (benign) | 6 (no bump) | 0 | clean |
| R3 (original, superseded) | 1 | 1 | 1 (benign) | 7 (no bump) | 0 | clean |
| R3 (redefined) | 1 | 1 | 1 (benign) | 0→1 (benign) | 0 | clean |
| R4 | multiple by design (lifecycle round) | — | — | — | 0 | n/a, see R4 write-up |

`settings.xml` restored once after R4 and diffed byte-identical against the pre-round backup at that
point, then **re-diverged** for the redefined R3 run (which needed the round's common settings
re-applied — see Setup notes) and **restored and re-diffed byte-identical a second time** at the true
end of the round, after the redefined R3 completed.

## Anything the brief did not ask about

- **Spotify opens paused, not playing, on this rig every time**, needing the documented
  `input keyevent 126` nudge through the head unit before each measured run. Not new behavior, but
  not previously written down as a round-setup step either — worth folding into the standard
  clean-run protocol's Spotify section for the next round that needs audio load.
- **The R0 unit-test failure is a pre-existing bug on the candidate SHA unrelated to this round's own
  work** (`fix/wifi-direct-lifecycle`, `fix/p2p-legacy-5ghz`, `fix/video-stack` — none of which touch
  `P2pOperatingChannelPolicy`'s channel-14 arithmetic). It was likely already broken before this
  round; worth checking whether it's broken on `main` too, since the fix (`307d85f2`) is currently
  only a local commit on top of the candidate stack, not pushed anywhere.
- The `am start-foreground-service` route to `ACTION_NATIVE_AA_POKE` worked from a cold app state
  (service not running) just as well as from a warm one — useful for any future round wanting to
  script the poke button without a UI tap, now documented above.
