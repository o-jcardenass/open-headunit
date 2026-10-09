# projection-raise — round 1 results

**Candidate:** fix/audio-sink-and-wireless-bring-up @ `59229efa4`       **Baseline:** not used this round
**APK md5:** `327372f998f0d1a0ef3562158052fb09` (one build for the whole round; installed-APK pull matched)
**Unit:** D-HU = UNISOC MT50_YT610E4GFPSL_U, Android 14, single BT radio
**Date:** 2026-09-17

## Setup notes

- **Gate:** `./gradlew :app:testGithubDebugUnitTest` read **2127 tests, 0 failures** — matches the brief
  exactly. Built via `hur-wifi-test-scripts/build_hur.sh` + `run_unit_tests.sh` (through
  `install_and_launch.sh`).
- **`ACTION_NATIVE_AA_POKE` needs `--es extra_mac <MAC>`.** A bare broadcast returns
  `{"error":"extra_mac is required","ok":false}`. Not called out anywhere N1 reads, and cost the first
  attempt at N1.
- **D-HU's house WiFi ("Pegue Cdesta") needed a manual UI tap to reconnect**, same known rig quirk as
  prior rounds (autojoin disabled, no adb verb re-enables it) — this time WiFi was fully *off*, not just
  disconnected, so `svc wifi enable` was needed first too. Used for N3/N4's shared-LAN precondition.
- **The AA developer "Start head unit server" toggle has no scriptable trigger**; found and used the
  exact navigation path (Settings → search "Android Auto" → App info → "Additional settings in the
  app" → scroll to About → tap "Version" ×10 → confirm the "Allow development settings?" dialog →
  overflow ("More options") → "Start head unit server"). Confirmed listening via `:149D` in
  `/proc/net/tcp6`. Dev mode itself persists across a Gearhead force-stop; only the server process needs
  restarting each time it's stopped.
- **A stray Settings/Bluetooth task resurfaced on top of OHU** on both D-HU and D-MOTO more than once
  after a session ended (leftover from the UI navigation above) — `am stack remove <taskId>` was needed
  each time before OHU would reliably stay on screen.
- **D-HU's own Bluetooth adapter name is "Navegadortz2"** (MAC-masked "local radio" name in NativeAA
  log lines) — easy to misread as a bonded remote device on first glance.
- No new `hur-wifi-test-scripts/` script was added. Everything beyond `build_hur.sh`/`run_unit_tests.sh`/
  `install_and_launch.sh`/`set_pref.sh` was ad hoc `adb`/`uiautomator`/`screencap`, because the runs
  needed live visual navigation (WiFi/Bluetooth/Gearhead-dev settings, and the AA "Exit setup?" dialog
  rendered *inside* the projected video) that nothing existing covers.

## 1. Build and baseline gate

**PASS.** 2127 tests, 0 failures. Installed APK md5 matches the built APK md5 (pulled and hashed from
the device, not `adb shell cat | md5sum`).

## N1 — A session the phone starts by itself comes up without the overlay permission

**PASS**

- Arm 1 (foreground, overlay denied): `AapService: raising the projection by DIRECT (overlay=false,
  foreground=true)` at `15:16:02.976`; `AapProjectionActivity.onCreate` at `15:16:03.138`, 220ms after
  `Handshake: Handshake successful` (`15:16:02.918`). All four projection lines present:
  `Service Discovery Response` (`15:16:03.519`), `VIDEO Channel Open Response` (`15:16:03.667`),
  `Media Sink Setup Request: 3 on channel VIDEO` (`15:16:03.705`), `Throughput over 5010ms: rendered=43
  (8fps)` (`15:16:09.614`).
- Arm 2 (backgrounded, overlay still denied): `AapService: raising the projection by NOTIFICATION
  (overlay=false, foreground=false)` at `15:17:20.895`, with `AapService: no permission to draw over
  other apps, so the projection is a notification the user has to tap. Turn that permission on to have
  it come up by itself.` (same timestamp, repeated `15:17:28.909`).
- Overlay permission restored to `allow` at the end of the run (step 6), read back.

## N2 — A projection that never comes up ends the session instead of hanging

**PASS**

- `AapService: the handshake finished 8000ms ago and the projection screen has not come up, so nothing
  is reading the session. Raising it again.` — `15:18:11.711`
- `AapService: the projection screen never came up, so this session can carry nothing. Ending it so the
  phone can start a new one.` — `15:18:19.727` (8016ms later; ~16s total from handshake, under the 20s
  bound)
- `netstat -tn | grep 5288`: `ESTABLISHED` at +5s, no entry (closed) at +20s and +60s.

## N3 — Discovery sweeps the network that is carrying traffic

**PASS**

- Precondition: D-HU and D-MOTO both on "Pegue Cdesta" (`192.168.1.0/24`); ping D-HU→D-MOTO 0% loss
  before starting.
- `NetworkDiscovery: Scanning subnet: 192.168.1.* (from the joined WiFi network)` — `15:24:12.831`
- `NetworkDiscovery: Found Headunit Server on 192.168.1.5:5277` — `15:24:13.033`
- Session projected: SSL handshake, all four projection lines, `rendered=41 (8fps)` at `15:24:21.875`.
- First-ever scripted PASS via the Headunit Server route on this rig (per the brief's own note).

## N4 — A failed handshake takes the pill down with it

**INCONCLUSIVE**

Three distinct repro strategies tried against D-MOTO's dev head-unit server, none produced
`session state failed (peer_silent)`:

1. **Force-stop Gearhead mid-session.** Produces a clean EOF/`link_lost` disconnect, not `peer_silent`
   — and once Gearhead's dev server process is gone, the discovery loop never finds anything to
   reconnect to (mDNS record expires fast): 60s, zero reconnect attempts logged.
2. **`ACTION_DISCONNECT` (abrupt local teardown) immediately followed by `ACTION_START_WIRELESS_SCAN`**,
   without restarting Gearhead. Reconnected cleanly every cycle. Found in passing: the dev "Start head
   unit server" tool self-disconnects on its own roughly every 20s regardless of anything we do, and
   always hands back a clean reconnect.
3. **iptables `OUTPUT`/`INPUT` `DROP` to the phone's IP** during an active session, simulating a link
   that "just vanishes" without going through our own graceful-teardown code. Same ~20s self-disconnect/
   reconnect cycle; the drop rule made no observable difference.

Root cause for why (2) can't reproduce it: `AapService.maybeTearDownBeforeLinkGoes` fires proactively on
`WIFI_STATION_DISABLING` and any Native-triggered teardown, and says so in its own log line: *"A session
that just vanishes leaves the phone's head unit server holding a peer that never came back, and only
restarting it by hand clears that."* The code's own graceful paths are built specifically to avoid the
deaf-server condition, so triggering them can't reproduce it — a genuine deaf server needs a link that
dies without going through any of those paths, which three tries on D-MOTO couldn't manufacture.

Not tried: D-POCO (round 2's original repro site) as the head-unit-server phone — set-up cost (fresh
dev-mode unlock plus a WiFi-LAN join) weighed against the round's remaining scope.

## N5 — A clean phone-side exit is not a reconnect

**PASS** (both required runs)

Repro mechanism found and used: tap "Exit" inside the AA UI rendered on D-HU's own screen (top-right
corner of the projection), which raises AA's own **"Exit setup? / Finish later / Disable"** dialog;
tapping "Finish later" sends a clean `ByeByeRequest` — matches round 2's own description of the lever
exactly.

- Run 1: `!!! RECEIVED BYEBYE REQUEST FROM PHONE !!! Reason: USER_SELECTION` — `15:35:59.549`. Pill
  hidden `15:36:00.221`; pill visible again (unsuppressed) `15:36:04.177` = **4.628s** after bye-bye.
  `ACTIVELY LISTENING` present throughout. Phone reconnected, all four projection lines, 8fps.
- Run 2: BYEBYE `15:36:41.600`; pill visible again `15:36:46.117` = **4.517s** after bye-bye. Clean
  reconnect.

**WiFi-button-inside-window sub-check: INCONCLUSIVE.** `ACTION_START_WIRELESS_SCAN` is the *wrong*
trigger for Native AA mode — `HomeFragment.kt` shows the real WiFi button calls
`connectToNativeDevice()` → `beginAutoConnect(..., PILL_THEN_OVERLAY)` → `showAutoConnectPill()`
directly, an unconditional bypass of the `phoneLeftQuiet` suppression that no broadcast reaches.
Reaching the physical button needs `HomeFragment` on screen, which only happens for well under a second
after "Finish later" before `AapProjectionActivity`'s DIRECT route reclaims the foreground — confirmed
even `am start -n MainActivity` mid-session is absorbed as a no-op ("intent delivered to currently
running top-most instance"). Two attempts used the wrong action; two used the right UI target but landed
the tap after the natural ~4.5s unsuppression had already happened. Not settled either way.

## N6 — Does this rig still have a phone that needs the escalated wake

**Not pass/fail — reporting the six outcomes, as asked.**

Precondition each run: end session (`headunit://exit`), cycle D-HU's BT adapter (`svc bluetooth
disable`, ~14-16s auto re-enable), confirm `mCurrentState: Connected` on the phone's own
`HeadsetStateMachine` for D-HU's MAC, launch `MainActivity` (arms the RFCOMM listener), watch.

| Run | Phone  | Outcome            | `ACTIVELY LISTENING` → `Connection accepted` |
|---|--------|---------------------|-----------------------------------------------|
| 1 | D-POCO | ordinary reconnect | ~4.0s (`15:46:06.400` → `15:46:10.412`) |
| 2 | D-POCO | ordinary reconnect | ~0.4s (`15:47:29.436` → `15:47:29.827`) |
| 3 | D-POCO | ordinary reconnect | ~2.4s (`15:48:12.527` → `15:48:14.916`) |
| 1 | D-MOTO | ordinary reconnect | ~2.1s (`15:52:35.184` → `15:52:37.303`) |
| 2 | D-MOTO | ordinary reconnect | ~2.1s (`15:53:19.582` → `15:53:21.684`) |
| 3 | D-MOTO | ordinary reconnect | ~2.7s (`15:53:56.445` → `15:53:59.126`) |

No phone reached the escalation in any of the six runs. Matches the round's own framing — no phone on
this rig still reproduces the negative the escalation exists for. **The wake thread's open question
closes on this: neither current test phone needs the escalated wake anymore on this rig.**

## W1 — The wake gives the hands-free link back, and says what it measured

**UNTESTABLE**

D-POCO only, as specified. Precondition established correctly every attempt (BT cycled,
`mCurrentState: Connected` confirmed each time; the arming line read verbatim every time: `NativeAA:
waking a phone over a hands-free link it holds is not yet measured on this unit, so the first one is the
measurement.`).

Six consecutive attempts total (three inside N6, three dedicated W1 attempts including one full
6-minute capture) all show D-POCO answering the ordinary way within 0.4-4s of `ACTIVELY LISTENING` —
never once approaching the 90s threshold the escalation needs to fire. The brief's own §4 anticipates
exactly this ("D-POCO FAILed this twice in the wake round's round 1 and PASSed it twice in round 2, on
the same rig, unexplained"). On this rig, right now, the answer is consistently the non-escalating side
(6/6) — not flaky between attempts this session, just currently on the side of the coin W1 can't
measure. Items 2-6 (wake timestamp, the zero-poke-in-30s check, the unpoked-window lines, the verdict
line, and the `dumpsys` cross-check) could not be exercised: nothing ever escalated.

## W2 — The verdict holds across an arming

**UNTESTABLE** — same root cause as W1 (no verdict was ever stored to re-check). Secondary data point:
the arming line still read `not yet measured on this unit` on the final W1 attempt (itself a fresh
arming after two prior cycles) — internally consistent, nothing falsely claims a measurement that never
happened.

## W3 — The setting is gone and nothing else moved

**PASS**

1. Search "wake": **zero rows.** Search "hands-free" and "handsfree": zero wake-related rows; only the
   neighboring `Complete the Bluetooth connection` row appears, confirming it was not accidentally
   removed alongside the wake setting.
   **Finding, not a candidate defect:** that neighboring toggle currently reads **off**
   (`native-aa-complete-hfp-slc=false` in `settings.xml`) on this rig, while its own description text
   says "On by default." Most likely a manual override carried over from an earlier round on this rig
   — not reset as part of this round (out of scope; origin unconfirmed). Flagged so it isn't mistaken
   for a fresh regression.
2. Closed Settings with nothing changed (back button, no Save): **zero** `ACTION_START_WIRELESS`
   broadcasts in the 5s after.
3. Cold start, both test phones' Bluetooth off ("phone away"), watched 3 minutes: **zero** escalated
   wakes (`it has not started Android Auto in 90s` / `waking ... despite`) at any point.

## Anything the brief did not ask about

1. **`AapService.ACTION_STOP_SERVICE`'s teardown looks like an unguarded race**, found investigating an
   operator question about a driver-selection side test, not part of the brief. `headunit://exit` →
   `ACTION_STOP_SERVICE` calls `commManager.disconnect(sendByeBye=true)` then `stopForeground(true);
   stopSelf()` with **no await** (`AapService.kt:2612-2622`). The actual P2P-group teardown
   (`wifiLauncherManager.stop()`) runs in a `serviceScope.launch(Dispatchers.IO){}` coroutine fired from
   `onDisconnected()`'s state collector — on the same `serviceScope` that `onDestroy()` cancels
   (`AapService.kt:2581`) moments later. A direct test (exit, wait 3s, **no relaunch**, check `dumpsys
   wifip2p`) showed the group *was* torn down cleanly that time (`groupFormed: true → false`), so this
   is an unguarded race that did not lose in the one direct test run — not a confirmed bug. Worth an
   explicit await or moving the teardown off `serviceScope` before ruling it harmless; not graded here.
2. **Driver selection can override an in-progress connection to a different phone.** With D-MOTO holding
   the active hands-free link and P2P group (no AAP session running), an explicit
   `ACTION_NATIVE_AA_POKE` to D-POCO succeeded, and D-POCO won the race to actually join the (freshly
   recreated — different BSSID, confirmed) P2P group and complete the AAP session, while D-MOTO's own
   concurrent reconnect attempt lost out. Confirmed via each phone's own `192.168.49.x` IP assignment.
   D-MOTO's Bluetooth HFP link itself was untouched throughout (`state=Connected` the whole time); only
   the WiFi-Direct/AAP session was contested. Observed behavior, not graded pass/fail.
3. Connecting D-MOTO's HFP to D-HU via the phone's own Bluetooth Settings UI **displaced D-POCO's
   pre-existing HFP connection outright** — D-HU's `HeadsetClientService` only ever shows one connected
   device's `StateMachine` at a time. Phone-side/OS Bluetooth-stack behavior, not app code, but relevant
   context for finding 2 above.
