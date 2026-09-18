# audio-sink-jitter — round 6 results

**Candidate:** `fix/audio-sink-and-wireless-bring-up` @ `78f7e70b0`       **Baseline:** `main` @ `5ce51c5e6`
**APK md5:** candidate `94332b292e5d41aa119200950502a841` (installed APK pulled back and hashed, matches the build just pushed) / baseline not built — R0 does not compare against it this round
**Unit:** D-SAM (Samsung SM-T230, `degaswifi`, board PXA1088), Android 4.4.2 (API 19), 2.4 GHz-only radio, head unit. D-POCO (POCO X3 NFC) as phone.
**Date:** 2026-09-16

## Setup notes

- Candidate branch checked out fresh (`git checkout -B audio-sink fork/fix/audio-sink-and-wireless-bring-up`); `git log --oneline -6` matched the brief's six SHAs exactly.
- Scripts used: `run_unit_tests.sh`, `build_hur.sh` (via `install_and_launch.sh`, `HU=30041c35642d2200`), `set_pref_hostedit.sh` for every settings.xml edit, `mock_drive.sh` (default Medellín→Marinilla route, `PHONE=4f4027e9`, run once for 1000s, stopped naturally). New this round: none — everything fit an existing script.
- **Order run: R0, N7r2, S1r (blocked before it ran), J1, T3, W1.** W1 run last per the brief's own warning that it re-poisons D-POCO's Gearhead.
- **S1r could not be run.** It needs Gearhead held down on D-POCO through an entire ~5-minute poke-cycle window. A single `force-stop` doesn't hold — Gearhead's own auto-restart (a Bluetooth-triggered receiver) answers the very next poke every time, confirmed directly: `NativeAA: Successfully poked POCO X3 NFC via HFP-AG` was immediately followed by `NativeAA: Connection accepted from` on every cycle even with `force-stop` reissued every ~15s. `pm disable-user --user 0 com.google.android.projection.gearhead` (the brief's own documented fallback) and a background `while true; do force-stop; sleep 2; done` loop were both refused by this session's own permission classifier (`Modify Shared Resources` / `Interfere With Workloads` respectively — third-party-app interference, not a rig or device limitation). Asked the operator; told to skip and report it as blocked rather than pursue further workarounds. **Verdict: UNTESTABLE this round, for a session-tooling reason, not a rig or code limitation.** A future round run with that permission granted (or from a session with broader Bash permissions) should be able to run it as briefed.
- **A real gotcha found and fixed mid-round, worth carrying into `TESTING-TEMPLATE.md` if not already covered by the existing note:** W1's arming step (§4 of the brief) sets `native-ap-transport=1` plus the hotspot keys, then says to set `native-ap-transport=0` "back to WiFi Direct" — but does **not** say to clear `hotspot-ssid` / `hotspot-password` / `static-bssid` / `hotspot-interface`. Doing exactly what the brief says leaves those hotspot values live, and the next WiFi Direct group forms with `identity stable=yes` on the hotspot's own `static-bssid` (`00:27:15:43:06:6A`) instead of a normal P2P-negotiated BSSID — this is precisely the `wpp-over-tcp-round5-brief.md`-documented gotcha ("Round 4's first R3 attempt was void because `static-bssid` and `hotspot-ssid` were still carrying the hotspot arm's values"), reproduced here on a different thread's brief. Caught before any run was graded on it; cleared all four keys back to their WiFi-Direct defaults (`hotspot-ssid=""`, `hotspot-password=""`, `static-bssid="0"`, `hotspot-interface=""`) and re-ran cleanly. **Any future brief's hotspot-arm-then-revert recipe should explicitly say to clear these four, not just flip the transport back.**
- **W1's real test could not get a clean signal despite four attempts** (fresh relaunch, Gearhead force-stop, D-POCO Bluetooth bounce, and a final fully hands-off attempt). See W1's own section — the arming step's endpoint-caching half worked, but D-POCO's Gearhead consistently reported `WiFi Projection Protocol cannot start as HU is not present` / `WIRELESS_SETUP_CAR_BLUETOOTH_DISAPPEAR_BEFORE_WSEM` before ever dialling the head unit's TCP listener, even on the attempt where nothing was touched after launch. This reads as the rig's own documented Bluetooth/A2DP flakiness (`TESTING-TEMPLATE.md` §7a: "The A2DP link comes and goes on its own schedule, and nothing visible controls it") rather than anything the candidate branch does, but it means `WppTcpServer`'s own withhold decision — the actual thing W1 exists to grade — never got exercised this round.
- Both latches (`video-profile-starvation-cap`, `playback-focus-self-defeating`) read `false` throughout; neither tripped this round.
- `native-wifi-version-exchange` restored to `false` after W1, all hotspot keys restored to empty/`"0"`, `native-ap-transport` restored to `0`, `wifi-direct-band` confirmed still `2` — all confirmed by readback against the round's own settings backup taken before R0.

---

## R0. Gate

**PASS**

- `./gradlew :app:testGithubDebugUnitTest`: **2017** tests, 0 failures (summed from every `test-results/testGithubDebugUnitTest/*.xml`), matching the brief's stated gate exactly (up from round 5's 2011; the brief's own note: six of those are `WppTcpServePolicyTest`, graded on hardware by W1).
- Installed APK pulled back from D-SAM and hashed: `94332b292e5d41aa119200950502a841`, matching the build just pushed (verified via a real `pull` + `md5sum`, not the flaky `adb shell cat | md5sum` pipe, which returned a different hash for the identical file — a pipe artifact, not a real mismatch. File sizes matched exactly at 23419189 bytes either way).

---

## N7r2. The guard, with the hole closed

**PASS**, cleanly, on the second staged attempt (the first attempt's manual timing landed the broadcast ~6s after the settings-close walk had already succeeded — not a race at all, just late; redone with both actions issued in one shell round-trip, ~880ms apart, matching round 5's own timing).

- `AapService: the settings screen closed, re-arming wireless mode NATIVE` — **04:54:07.693**
- `AapService: Force-starting WIFI-Scan from UI` (the broadcast) — **04:54:09.485** (1792ms later; the delayed re-arm from the close itself was already in flight by then)
- **The guard line fired:** `WifiLauncher: a Native AA bring-up was re-armed 272ms ago, so this one is not started on top of it.` — exactly the line the brief asks for.
- **Exactly one `createGroup SUCCESS`:** 04:54:10.336.
- **Exactly one SSID delivered:** `DIRECT-oV-Navegadortz3`, four credential deliveries (04:54:10.736 / 11.397 / 11.427 / 14.220), all the same name — matches the documented "3–4 deliveries per group" pattern, not a second group.
- **Time from the first trigger (the real settings-close, 04:54:07.693) to `WirelessServer: Incoming connection detected`:** first occurrence at **04:54:17.363**, **9.67 s** — against round 5's **50.7 s**. (A second "Incoming connection detected" line followed at 04:54:30.345 with no second SSL handshake and no second `createGroup`; read as a retried/duplicate TCP SYN on the same session, not a discard-rule hit — only one `Handshake: SSL handshake complete` appears, at 04:54:30.656.)
- Session rendered: `First frame rendered (hardware decode)` at 04:54:36.041.

No `the Native AA group create abandoned` or `a later bring-up owns the group now` lines appeared.

**The "does the refusal eat a real one" sub-check: PASS.** Tore the session down, changed `wifi-direct-band` from `2` (FORCE_2_4GHZ) to `0` (AUTO) with the app stopped, relaunched, then fired `ACTION_START_WIRELESS` directly (the same intent the settings-save UI sends) rather than tapping Save. The restart went through and the new setting took effect, visible in the band-preference log text changing from the FORCE_2_4GHZ wording to the AUTO one:

```
WifiDirectManager: Band preference is automatic (5 GHz, then whatever this unit will host).
```

(replacing the earlier session's `2.4 GHz only, set by the user`). `createGroup SUCCESS` followed normally. `wifi-direct-band` restored to `2` afterward, confirmed by readback.

*Noticed in passing, not part of this brief:* on this API 19 unit, `WifiManager.is5GHzBandSupported` doesn't exist (`Could not find method ... is5GHzBandSupported`), so both AUTO and the explicit band preferences land on `WifiDirectManager: this unit will not say whether it has a 5 GHz band ... so nothing here is decided on the band.` This is the same below-API-21/29 fallback gap `t230-native-aa-bringup` already found — not a new defect.

---

## S1r. The stale-group recreate, with a lever that reaches it

**UNTESTABLE** — blocked before it could run. See Setup notes for the full explanation: this session's own permission classifier refused both documented ways to hold Gearhead down (`pm disable-user`, a background force-stop loop), and a manually repeated single `force-stop` every ~15s does not hold — Gearhead answers the very next poke every single time regardless. This is a session-tooling limitation, not a rig or code limitation; the operator was asked and chose to skip rather than pursue further workarounds this round.

---

## J1. The refusal widening, which was wired to only one route

**FAIL.** The lever used was round 5's tight WiFi hold-down (`s1_wifi_holddown_tight.sh`, D-POCO's WiFi disabled on a 3s cadence) — a phone that reaches the head unit over Bluetooth and cannot join the network named, exactly the shape the brief asks for.

**The one-shot widening notice did fire**, confirming the refusal counter is live:

```
NativeAA: the phone has refused this network 2 times in a row, so retries are slowing down. It reaches us over Bluetooth and then cannot find the network we name. A hotspot switched on on the phone is the usual cause, because the phone's radio cannot host that and join us at the same time.
```
— 05:03:49.000, immediately after the first observed `WIFI_NETWORK_UNAVAILABLE(-11)` refusal.

**But the poke-to-poke cadence never widened.** Ten consecutive gaps measured across the sustained hold-down window, all essentially flat at ~15 s despite the refusal count climbing well past both the brief's REFUSALS_BEFORE_CEILING(5) and this thread's own "at least seven refusals" ask:

| # | `Attempting active poke` timestamp | gap to previous |
|---|---|---|
| 1 | 05:03:33.585 | — |
| 2 | 05:03:49.020 | 15.435 s |
| 3 | 05:04:03.945 | 14.925 s |
| 4 | 05:04:19.440 | 15.495 s |
| 5 | 05:04:34.405 | 14.965 s |
| 6 | 05:04:49.850 | 15.445 s |
| 7 | 05:05:05.315 | 15.465 s |
| 8 | 05:05:20.290 | 14.975 s |
| 9 | 05:05:35.735 | 15.445 s |
| 10 | 05:05:50.699 | 14.964 s |
| 11 | 05:06:06.164 | 15.465 s |

By poke 11 the refusal count is at least 10 in a row (one "2 times in a row" notice already seen, and every cycle since produced another `WIFI_NETWORK_UNAVAILABLE(-11)`). Per `JoinRefusalPolicy.retryDelayMs`, a `consecutiveRefusals` in `[2, 5)` should return `maxOf(normalDelayMs, 30_000L)` and `>= 5` should return `maxOf(normalDelayMs, 120_000L)` — the observed gap never left the ~15 s normal cadence anywhere in this eleven-poke run. This is the brief's own stated FAIL signature: *"a flat 15 s cadence, which is the pre-fix behavior."*

(Gaps after poke 11 — 42.4 s, 42.9 s, 17.5 s, 26.9 s — are **not** read as evidence of widening: they land inside the window where the WiFi hold-down was released and D-POCO's own WiFi-P2P subsystem was observed stuck in `P2pDisabledState` after the repeated `svc wifi disable`/`enable` cycling, an uncontrolled variable from releasing the lever, not the candidate settling into a widened cadence on its own. The release-and-recover closing check the brief asks for could not be cleanly confirmed for that reason — D-POCO's P2P stayed reported-disabled through two more explicit `svc wifi enable` calls and an `airplane-mode` bounce, though a real session did go on to form minutes later once W1's own bring-up launched, so the phone's actual radio was not permanently broken, just slow/uncertain to report its own state back to `dumpsys`.)

Measured on request:

```
grep -c "Attempting active poke to device" j1.txt   # 15 across the whole capture
grep -c "refused this network"                 j1.txt   # 1 (by design — logged once at the transition)
```

The mechanism: `NativeAaHandshakeManager.kt:1884` computes `JoinRefusalPolicy.retryDelayMs(consecutiveJoinRefusals, POKE_RETRY_GAP_MS)` and the comment right above it (`// The only gap [JoinRefusalPolicy] governs`) confirms this is meant to be the wired-in path the brief describes — but the measured cadence says the widened value from that call isn't reaching the actual `delay()`, or `consecutiveJoinRefusals` isn't what's climbing here. Not root-caused further; that's a code-review question, not a hardware-round one.

---

## T3. One last question about the video ceiling

**No FAIL condition, as the brief says.** Full session: `debug-video-feed-hold-ms=0`, branch's own settings otherwise, one mock-drive route (default Medellín→Marinilla) with Spotify playing throughout, **17.5 minutes** (35 dispatch windows × ~30 s each — comfortably past the 15-minute ask).

- **`videoQueue` high-water across every window: 5.** Full distribution across the 35 windows: value 1 → 9 windows, 2 → 11, 3 → 11, 4 → 2, 5 → 2. Never approached double digits, let alone the 256 ceiling.
- **`videoShed` total: 0**, in every single window.
- **`AapTransport: the video thread is 256 messages behind, shedding` never fired.**
- No disconnect, no `AapTransport quitting`, session held the whole 17.5 minutes.

This closes the question the brief asks: the ceiling is unreachable in normal use on this rig, consistent with round 5's own 2–5 reading over three shorter windows, now confirmed over a full session almost 6× as long.

---

## W1. The server now refuses the dial it would refuse to advertise

**INCONCLUSIVE.** The arming half of the run worked; the grading half never got a signal to grade, across four separate attempts.

**Arming (§ steps 1–3): confirmed.** With `native-ap-transport=1` and round 5's hotspot values (`hotspot-ssid=Navegadortz2`, `hotspot-password=12345678`, `static-bssid=00:27:15:43:06:6a`, `hotspot-interface=wlan2`, `hotspot-band=1`, `auto-enable-hotspot=false`, `native-wifi-version-exchange=true`), the arming bring-up produced:

```
NativeAA: [TX] Sending WifiVersionRequest (Type 4) v4.2
NativeAA: advertising WPP over TCP at 0.0.0.0:5299
```

— an endpoint went out, satisfying the brief's own stop-here-if-not condition. D-POCO's own log shows the phone's immediate response was a rejection (`WifiVersionResponse v4.2 status=NO_SUPPORTED_WIFI_CHANNELS(-8)`), but D-POCO's Gearhead nonetheless went on to log `WIRELESS_WIFI_PROJECTION_PROTOCOL_HU_SUPPORTS_WPP_OVER_TCP` on every subsequent wireless-setup attempt this round — the belief was cached regardless of the rejected response. `native-ap-transport` restored to `0` and the hotspot keys cleared (see Setup notes) before the real test.

**The real test (§ "Then run it"): never reached a decision.** Four attempts — a fresh relaunch, a relaunch after force-stopping Gearhead on D-POCO, a relaunch after bouncing D-POCO's Bluetooth adapter off/on, and a final attempt where nothing was touched on either device after launch — all ended the same way on D-POCO's side:

```
WIRELESS_WIFI_PROJECTION_PROTOCOL_TCP_NETWORK_UNAVAILABLE
WIRELESS_WIFI_PROJECTION_PROTOCOL_TCP_STOPPED
WiFi Projection Protocol cannot start as HU is not present.
WIRELESS_SETUP_CAR_BLUETOOTH_DISAPPEAR_BEFORE_WSEM
```

Gearhead's own wireless-setup session repeatedly decided the head unit's Bluetooth presence had disappeared before it could open a WiFi Projection Protocol connection at all — before ever reaching a TCP dial to port 5299. Correspondingly, **`WppTcpServer` on the head unit side logged only `listening for Android Auto on TCP 5299` in every attempt, never `connection from`, never `not serving this dial`, never `[TX] WifiInfoResponse (Type 3) with credentials`.** The one field the brief asks to be named explicitly — **whether any `WifiInfoResponse` went out on the TCP path — did not happen, because no dial ever arrived to answer.** The head unit's own `MainActivity` auto-connect watchdog gave up each time after its ~149 s window (`Auto-connect: nothing answered this attempt`).

This reads as the rig's documented Bluetooth/A2DP flakiness (`TESTING-TEMPLATE.md` §7a — "the A2DP link comes and goes on its own schedule, and nothing visible controls it," confirmed unprompted-recovery and unprompted-failure in both directions across earlier rounds) rather than anything in the candidate: the fourth attempt touched nothing after launch and still failed identically. Per house rules, an honest INCONCLUSIVE here is the correct call rather than manufacturing a substitute result — this run genuinely could not get the signal this cycle.

**Ten-minute wedge check: no wedge observed.** `grep -c "GH.WPP.TCP: Restarting WPP over TCP"` on D-POCO's full logcat buffer across the whole round: **0** — a real difference from round 5's accidental finding of "every 35 to 55 seconds for about 15 minutes," though this isn't a clean apples-to-apples comparison, since (unlike round 5) no TCP dial from D-POCO to the head unit was ever observed at all this round for the reason above — there was nothing for Gearhead to get wedged retrying.

---

## Anything the brief did not ask about

- **The `set_pref_hostedit.sh`-only method held up cleanly for every setting this round**, including the four-key hotspot group and the single-scalar `wifi-direct-band` toggle — no `sed`-not-found or quoting failures on any of the ~20 individual writes made this round.
- **`WifiDirectManager: the phone has ignored N wake pokes` never appeared this round** (the S1r lever was never reached), so that log line remains unverified on hardware since round 4's zero-poke reading; still open for a future round with the right permission scope.
- **D-POCO's WiFi-P2P subsystem reporting `P2pDisabledState` while station WiFi itself reads `Wi-Fi is enabled`** is worth flagging as a possible new rig quirk for `TESTING-TEMPLATE.md` §7a: seen twice this round after a rapid `svc wifi disable`/`enable` sequence (once after the tight hold-down script's cadence, once after a manual double-bounce), and it did **not** stop a real P2P join from completing minutes later (T3's session formed and rendered fine despite `dumpsys wifip2p` reading `P2pDisabledState` moments before launch) — so it looks like a stale/lagging `dumpsys` read rather than an actual disabled radio, but nobody should trust that dump at face value on this phone immediately after a WiFi bounce.
