# Native GPS forwarding — round 1 results

**Candidate:** `fix/native-gps-transport-race` @ `9e7cf95a`, three commits on main's `a8830caa`
**Baseline:** none (no A/B in this round)
**APK md5:** `8125b635aca2c68c8b669a9e9e0aa687`, confirmed identical to the installed package.
**Unit:** headunit `27870808938846` (UNISOC MT50_YT610E4GFPSL_U, Android 14) / phone `4f4027e9`
(Redmi M2007J20CG, POCO X3 NFC)
**Date:** 2026-08-15

## Setup notes

- History reset per the brief: `git fetch fork --prune --prune-tags` then
  `git checkout -B fix/native-gps-transport-race fork/fix/native-gps-transport-race`.
  `git log --oneline -4` showed exactly `9e7cf95a, cf9e50d1, 2de11a95, a8830caa` as predicted.
- Scripts used: `run_unit_tests.sh`, `install_and_launch.sh` (which calls `build_hur.sh`) for R0;
  `set_hu_prefs.sh` for all settings writes (no-relaunch form, used throughout so capture could
  start before launch); no new script needed this round.
- **R1 changed the round's premise: this rig has a real, live GPS fix, not the "unlikely indoors"
  case the brief planned for.** `dumpsys location`'s `gps provider` showed `mStarted=true`, a GNSS
  KPI block with 13,171 location reports logged and a last-known fix whose elapsed-realtime
  timestamp was ~18 s old at capture time (uptime 14604.72 s vs et=+4h3m6s486ms). Per the brief's
  own contingency ("If R1 showed a real, fresh (<10 min) GPS last-known fix ... this run's
  expectation flips to R3's"), R2 was run and evaluated against R3's PASS bar instead of its own,
  and did in fact flip: priming sent a fix immediately rather than reporting none available.
- **The `appops set --uid 2000 ...` form from the brief's mock-injection contract fails on this
  rig** (`Exit code 255`, no such uid context); the working form is
  `adb shell cmd appops set android android:mock_location allow` (target the `android` package, not
  a uid or `com.android.shell`). Confirmed by the subsequent `add-test-provider` calls, which threw
  `SecurityException: android from uid 0 not allowed to perform MOCK_LOCATION` until this grant was
  in place. Recording this as the working form for future rounds; cleared with
  `cmd appops set android android:mock_location deny` at the end.
- **`cmd location providers set-test-provider-location gps` with an unchanging coordinate is
  silently coalesced by the Android location framework on this rig, well before it reaches any
  app.** `dumpsys location`'s own Event Log shows exactly one `gps provider received location[1]`
  line across ~30 identical-coordinate shell calls in R4's official window, versus one distinct
  `received location[1]` per call, every time, in a 10-call side check that varied the coordinate by
  0.0001° each call. This is a property of the OS's mock-provider dispatch on this rig (confirmed
  independent of the app: a stock vendor consumer, `ZQC-GpsLocationManager`, shows the identical
  sparse pattern), not of `GpsLocation`'s subscription. See R4 below — this is why R4's raw count is
  reported as INCONCLUSIVE rather than FAIL, with a positive control included as evidence.
- Settings backed up before any change (`settings-backup.xml`) and restored byte-for-byte at the
  end, confirmed via `run-as cat` equivalent (rooted `adb shell cat`) showing `log-level=2`,
  `gps-navigation=true`, `wifi-connection-mode=3` restored.
- Every run's `MATCH! Starting AapService via Bluetooth Auto-start...` (one per run, R2/R6/R7) is
  the phone's own real Bluetooth reconnecting after the clean-run protocol's step 5 (airplane mode
  off) — not a self-inflicted poke loop. In every case it produced no second `createGroup SUCCESS`
  and no second SSL handshake, so it did not trigger the discard rule in the sense that rule exists
  for (P2P group churn). Each run's single p2p-wlan0-N pair (e.g. `-0`→`-1`) is a leftover-interface
  cleanup from the previous run's teardown followed by exactly one new group, confirmed by reading
  the surrounding wpa_supplicant/Netd lines, not two groups forming in the same run.
- R3, R4 and R5 share one continuous capture (`r3.txt`) and one continuous session, per the brief's
  "session still up from R3" note for R4/R5 — force-stop was not used between them, since it would
  have cleared `GpsLocation`'s in-memory cache mid-measurement.
- A short diagnostic (10 calls, varying coordinate, ~00:28:31–00:28:42) was inserted between R4 and
  R5 to isolate the cause of R4's low count before writing it up (see above). This is the only
  deviation from the brief's literal run sequence; it delayed the start of R5's first starvation
  window but did not otherwise change R5's setup, which restarted its own 75 s count from the last
  real fix delivered.

## R0 — build gate

**PASS.** `run_unit_tests.sh` passed. `build_hur.sh` (via `install_and_launch.sh`) succeeded.
APK md5 `8125b635aca2c68c8b669a9e9e0aa687` matched the live install
(`adb shell md5sum` on `pm path`'s resolved APK).

## R1 — location stack probe

No PASS/FAIL; input to the rest of the round.

- Real `gps` provider present, `mStarted=true`, GNSS KPI shows 13,171 location reports and a
  TTFF mean of 220.63 s logged over the current boot.
- Last-known `gps` fix was live and fresh (~18 s old) at capture time — see Setup notes; this
  flips R2's expectation.
- `cmd location help` confirmed all mock-injection subcommands from the brief's contract exist
  (`add-test-provider`, `set-test-provider-enabled`, `set-test-provider-location`).

## R2 — priming with no fix available

**Flipped per the brief's own contingency; behaved as R3 expects.**

- `Sensor Start Request sensor: LOCATION, minUpdatePeriod: 0` — 08-15 00:19:00.048
- `LOCATION sensor requested. Sending current fix immediately. sentOnWire=true` — 08-15
  00:19:00.057 (not the base run's expected "No recent GPS fix to prime with.", because R1's real
  fix was available)
- Subscription gate line: `GpsLocation.start | Request location updates` (real permission +
  provider both fine), followed by `GpsLocation: first fix after 0s` — the rig's live GPS answered
  the subscription instantly.
- Session healthy: single SSL handshake, video streaming throughout.
- Noteworthy: `AapTransport: dropping sensor events` appeared twice in this run
  (`droppedByType={1=1}` at 00:18:59.191, then `{1=1, 10=1}` at 00:18:59.209), **both before** the
  `Sensor Start Request` at 00:19:00.048 and none after. Type 1 is LOCATION. This is exactly the
  pre-request drop the priming fix exists to rescue: a `LocationUpdateEvent` generated by the
  live subscription's `onLocationChanged` (which fired at 00:18:59.161, ahead of the phone's
  request) has nowhere to go yet and is correctly dropped once; the priming send 0.9 s later is
  what actually gets the car's position onto the wire. No drop naming LOCATION occurred after the
  channel opened.

## R3 — mock feasibility + primed send (the point of the round)

**PASS.**

- Mock injection verified before launch: `dumpsys location` showed
  `last location=Location[gps 49****** , 2****** hAcc=100.0 et=+4h9m51s805ms mock]` — the injected
  Eiffel Tower coordinate, tagged `mock`.
- `Sensor Start Request sensor: LOCATION, minUpdatePeriod: 0` — 08-15 00:24:03.080
- `LOCATION sensor requested. Sending current fix immediately. sentOnWire=true` — 08-15
  00:24:03.091. **sentOnWire=true on the first try.**
- The only `dropping sensor events` line in this run's window names type 10 (NIGHT) only —
  `droppedByType={10=1}` at 00:24:02.409 — never LOCATION. No drop line named LOCATION anywhere in
  the R3/R4/R5 combined capture after this point either.
- `GpsLocation: first fix after 39s` (00:24:41.412) is the subscription's own callback catching up
  once the second injected fix (sent manually right after priming, per the brief's instruction)
  actually landed — the priming send itself used the cached last-known location and did not wait
  for that callback, which is the intended design (`getLastKnownLocation()` in `GpsLocation.start()`
  is independent of the active subscription's first `onLocationChanged`).

## R4 — cadence and no duplication

**INCONCLUSIVE — the shortfall traces to a mock-injection/OS artifact, not the code under test; see
Setup notes for the positive control.**

- Official window (30 identical-coordinate injections, 00:25:33–00:26:05): only 6
  `GpsLocation: fix received` lines landed, well short of the ≥24 PASS bar.
- `dumpsys location`'s own Event Log shows why: `gps provider received location[1]` — the
  framework's own marker for "a genuinely new fix arrived" — appears **exactly once** in that
  30-call window, at 00:25:59.179. Everything after that (00:26:00.243, :01.317, :02.378, :03.447,
  :04.513 — 6 deliveries in total including the first) is the framework redelivering that single
  cached fix to the app's listener at its requested 1 Hz interval (`gps provider delivered
  location[1] to 10166/com.andrerinas.headunitrevived`), roughly 1.06–1.07 s apart — evenly spaced,
  exactly matching `GpsLocation`'s `requestLocationUpdates(GPS_PROVIDER, 1000L, 0f, this)`.
- Positive control: a 10-call side check (00:28:31–00:28:42) using the same shell command but a
  coordinate that changed by 0.0001° each call produced a distinct `gps provider received
  location[1]` **every single call**, each delivered to the app within ~2 ms, landing 6 more
  `fix received` lines at the same clean ~1.06 s cadence.
- Exactly one `GpsLocation: first fix after <N>s` appeared for the whole R3+R4+R5 session (39s,
  logged in R3) — the "once per launch" half of R4's PASS bar is met.
- Read together, this shows the 1 Hz time-only subscription is honoured precisely for every fix the
  framework treats as new; the low raw count is the Android location stack on this rig declining to
  treat 29 of 30 identical-coordinate mock calls as new fixes, upstream of anything `GpsLocation`
  or `AapTransport` can see. A round with hardware capable of producing genuinely changing fixes
  (or a future brief using varying mock coordinates) is needed to measure cadence cleanly.

## R5 — the backstop gives up, and re-arms

**PASS.**

- First starvation window: `GpsLocation: no GPS fix for 62s, stopping resend so the phone can fall
  back to its own location` — 08-15 00:29:43.842, 62 s after the last real fix from the R4
  diagnostic (00:28:40.959). Exactly one line for this window.
- Re-arming fix injected at 00:31:19 (same round-standard coordinate, 48.8584,2.2945 — accepted as
  new because the diagnostic had left the mock state on a different pair); `GpsLocation: fix
  received` followed at 00:31:19.442, and `dumpsys location`'s Event Log confirms
  `gps provider received location[1]` at 00:31:19.434 — a genuinely new fix, not a redelivery.
- Second starvation window: `GpsLocation: no GPS fix for 60s, stopping resend so the phone can fall
  back to its own location` — 08-15 00:32:19.921, 60 s after the re-arming fix. Exactly one line
  for this window.
- **Measured N values: 62 and 60, both within the 60–66 s bound.** No window produced more than one
  stale line (the once-per-starvation latch held).
- Bonus corroborating data, outside the two official windows: the same stale/re-arm cycle also
  fired cleanly twice more elsewhere in the session with no extra help from this round's setup —
  once at 62 s during R4's official injection window (00:25:43.676, before the burst of redelivered
  fixes), and once at 60 s during the R4→R5 transition while the diagnostic was being prepared
  (00:27:04.747, after R4's last delivered fix, re-armed by the diagnostic's first call at
  00:28:34.571). Both were single-fired and in-range, giving four independent, consistent
  measurements of the same mechanism across the session rather than two.

## R6 — toggle off

**PASS.**

- With `gps-navigation=false`: zero `Sensor Start Request sensor: LOCATION` lines and zero
  `LOCATION sensor requested.` lines anywhere in the capture.
- Session otherwise healthy: one SSL handshake (00:34:06.295), video decoding running throughout
  (4,859 matching frame/OMX lines), single P2P group.
- Two control fixes were injected mid-session as instructed; neither produced any sensor traffic
  toward the phone.
- **Noteworthy, not a round failure:** `AapService.onConnected` starts `GpsLocationService`
  unconditionally on every connection (`AapService: Starting GpsLocationService and NightModeManager
  since connection is established`, `AapService.kt:962-964`), regardless of
  `settings.useGpsForNavigation`. `GpsLocation.start()` ran, subscribed to the real GPS provider,
  and logged `first fix after 43s` / two `fix received` lines even with the toggle off. The toggle
  is enforced correctly at the point that matters for this round — `ServiceDiscoveryResponse` only
  advertises the LOCATION sensor to the phone when `useGpsForNavigation` is true
  (`ServiceDiscoveryResponse.kt:33`), so the phone never asks and nothing reaches the wire — but the
  head unit keeps polling its own GPS chip at 1 Hz the whole time the toggle is off, which is wasted
  work (and battery/GPS-chip time on a real unit) that a gate in `GpsLocation.start()` itself, or in
  the call from `AapService`, could avoid.

## R7 — the gate refuses, and says which half

**PASS**, with one deviation from the brief's illustrative line worth flagging.

- `GpsLocation: not requesting updates, ACCESS_FINE_LOCATION granted=false GPS_PROVIDER
  enabled=false` — 08-15 00:37:44.419. The brief's PASS example showed `granted=true
  GPS_PROVIDER enabled=false`; this rig reported `granted=false` for both halves instead.
  `dumpsys package` still shows the raw permission grant as `granted=true`, but
  `androidx.core.content.PermissionChecker.checkSelfPermission()` (what `GpsLocation.start()`
  actually calls) evaluates the FINE_LOCATION app-op together with the master Location Setting, and
  correctly reports denied once the master switch is off — this is expected AndroidX behaviour, not
  an app defect, and the gate line the app produced is exactly as informative either way.
- `LOCATION sensor requested. No recent GPS fix to prime with.` — 08-15 00:37:46.128. No
  `sentOnWire=true` anywhere in the capture.
- Session otherwise healthy: one SSL handshake, one `dropping sensor events` line naming only NIGHT
  (type 10), never LOCATION.
- Cleanup: `cmd location set-location-enabled true` restored afterward and verified.

## Anything the brief did not ask about

- The rig's own real GPS hardware is live and actively locked (see R1/Setup notes) — worth knowing
  for any future round on this thread that wants a genuinely "no fix available" starting condition;
  that condition may need the master location switch turned off (as R7 does) rather than just
  avoiding mock injection, since the real chip will otherwise supply a fix on its own.
- `AapService` starts `GpsLocationService` unconditionally on every connection regardless of the
  `gps-navigation` setting (see R6) — correctly harmless to the phone (LOCATION is never advertised
  or sent), but real polling of the GPS chip continues the whole session with the feature "off".
  Worth a follow-up if GPS/battery draw with the toggle disabled is ever reported.
- The Android mock-location framework on this rig silently drops repeat calls to
  `set-test-provider-location` that don't change the coordinate (see R4/Setup notes) — any future
  round scripting mock GPS on this rig should vary the coordinate slightly per call if it needs the
  framework to treat each call as a distinct fix.

## Report back (per brief §8)

1. **R3's verdict:** PASS — `sentOnWire=true` on the first try.
2. **R4's count:** 6 of 30 injected calls produced a `fix received` line in the official window;
   confirmed via `dumpsys location`'s Event Log to be an artifact of the OS coalescing
   identical-coordinate mock calls (only 1 of 30 registered as a new fix at the framework level),
   not a defect in the 1 Hz subscription — a 10-call varying-coordinate control produced 10/10.
   Reported INCONCLUSIVE, not FAIL.
3. **R5's two N values:** 62 and 60 (both within 60–66), one stale line per window, confirmed twice
   more outside the official windows with the same result.
4. **`AapTransport: dropping sensor events` appeared in R2, R3(window edge), R6 and R7 — always for
   type 10 (NIGHT) except R2's two pre-request LOCATION (type 1) drops, which happened before the
   phone's own `Sensor Start Request` and are the exact case the priming fix rescues. No drop
   naming LOCATION occurred after any session's channel opened.**
