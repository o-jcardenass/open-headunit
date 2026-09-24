# selfmode-playstore-route — round 1 results

**Candidate:** `fix/head-unit-server-silence-and-log-attribution` @ `58802778` (3 commits on `d1fef63a`:
`58802778` / `378d8d4c` / `ad070f2f`), candidate-only, no baseline.
**APK md5:** github `7d793c0cd716c9b984554bb5796684f4` (21,915,623 B) /
playstore `47eed0db99e78d66a9fb8ebed44933bb` (19,147,534 B) — different, as required.
**Units:** D-POCO = Xiaomi M2007J20CG, board sm6150, Android 15 (API 35), AA 17.5.663204.
D-MOTO = motorola edge 30 neo, Android 14 (API 34), AA upgraded 17.3.662854 → 17.5.663204 this round.
D-HU = UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14, AA 17.3.662864 (kept on 17.3).
**Date:** 2026-08-27

## Bottom line

**The hypothesis is right for the legacy route and wrong for the 17.4+ route — and #897 is on the
17.4+ route.**

- **17.4+ route (AA 17.5, direct connect to `:5277`):** an offline Play Store build does Self Mode
  fine. R3 — the reporter's exact configuration (playstore, no network of any kind, auto-start) —
  formed a full SSL session and held it at 53-54 fps for the whole window, with the
  `Device is offline and VPN is not available in this build` line in the log. R2 showed the same on
  github via the VPN-free intent route (0.525 s connect→SSL). R4 (online) and R5 (network, no
  internet) also pass. **No network of any kind is needed on this route** — the app just opens a
  loopback socket to Gearhead's already-running proxy. So whatever #897 is, "playstore + offline
  can't do Self Mode on 17.5" is **not** it. The reporter's peer-goes-silent-after-accepting
  signature was not reproduced in any of R2/R3/R4/R5.

- **Legacy route (AA < 17.4, `WirelessServer` on `:5288`):** an offline device **does** need the
  dummy VPN, so the Play Store build **cannot** do offline Self Mode here. R8 (playstore, offline,
  legacy) → Gearhead's `WirelessFSM` starts and aims projection at `127.0.0.1:5288` but the
  connection never lands, `nothing connected within 30000ms`. R8c, the same-rig same-round control
  on github (VPN comes up) → connection lands in 2.6 s, session forms. The only variable between
  them is the VPN.

**R9 (added after the round): the likely #897 mechanism is Gearhead's First-Run Experience.** After
forgetting the head unit on D-POCO and retrying offline, the session still formed — but the capture
now shows the path a *fresh* connection takes: `CAR.FRX.CHECKS | isFrxRequired=true`, then
`cakewalkMinimumRequirements=…true, isSupportedOsVersion=…true, appsUpToDate=…true` →
`shouldSkipLegacyFrxForCakewalk true, min requirements met` → `shouldRunLegacyFrx=false` → the
connection proceeds and SSL completes. If any of those checks were **false** on the reporter's phone
(old Android, or outdated Google app / Maps / TTS / Search — plausible on a SIM-less tablet), Gearhead
would run the **legacy FRX** instead, which is a full interactive account/ToS/permissions flow that
almost certainly needs a network. Offline + legacy-FRX-required = `:car` blocks on a setup screen it
can't finish = the peer goes silent = #897. Our bench phones all have current Google apps, so they
take the "cakewalk" fast path and skip it. See R9.

**`assemblePlaystoreDebug` compiles.** First build ever. The workflow comment claiming the flavor
cannot compile is stale, exactly as the brief expected — `app/src/playstore/.../VpnControl.kt` is
present and complete.

## Setup notes

- **`hur-wifi-test-scripts/` scripts used:** `build_hur.sh` (R0 github, via `taskset -c 0,2,4,6`),
  `run_unit_tests.sh` (R0 tests), `set_prefs_runas.sh` (all settings writes on both phones),
  `round-log-and-selfmode-fixes/run_selfmode_full.sh` (every capture run — unfiltered logcat +
  MainActivity launch + optional `ACTION_START_SELF_MODE` intent + HUR_Log pull; run with
  `KILL_GH=0` every time per the brief).
- **Script added:** `hur-wifi-test-scripts/build_hur_playstore.sh` — sibling of `build_hur.sh` that
  runs `:app:assemblePlaystoreDebug` and copies the APK to `apks/` with a `-playstore` suffix. Left
  in place for the next round. (No round-specific capture script was needed; the prior round's
  `run_selfmode_full.sh` covered every run.)
- **Gearhead 17.5 for D-MOTO** was obtained by pulling the four split APKs
  (`base` + `arm64_v8a` + `en` + `xxhdpi`, D-MOTO density 420 = xxhdpi) from D-POCO, which already
  ran 17.5.663204, and `adb install-multiple -r`. Clean upgrade, `versionName` went to
  `17.5.663204-release`. The md5-`ba7edcf5…` copy the brief named was not on disk and not needed.
- **D-POCO `settings.xml`** at round start was byte-identical to the `log-and-selfmode-fixes` round's
  backup. Round delta actually applied per run is listed under each R below. Restored byte-identical
  at the end (`diff` → IDENTICAL).
- **`appops … ACTIVATE_VPN allow` was already `allow`** on both phones from a prior round and took
  again cleanly. No VPN consent dialog was ever seen (R1's VPN came up silently via
  `VPN permission already granted`).
- **Gearhead log tags** (`GH.DHUService`, `CAR.SERVICE`, `GH.GhCarClientCtor`) were raised with
  `setprop … VERBOSE` before the playstore runs. Self-check per brief §3: `Head unit connected` and
  `startDuplexConnection` both appeared in every session that formed, so the `GH.DHUService` /
  `CAR.SERVICE` tags were **not** being filtered. `Network server running on port %d` never appeared
  in any capture — expected: it is logged once when the head-unit-server accept loop first binds
  (at the AA-dev-settings toggle), not per connection, and the server was already up before every
  capture window.
- **D-POCO "offline"** = `cmd connectivity airplane-mode enable`; verified each time with
  `dumpsys wifi` (`Wi-Fi is disabled`), `dumpsys bluetooth_manager` (`state: OFF`) and
  `dumpsys connectivity` (`Active default network: none`). Coming back online needed
  `svc wifi enable` + `svc bluetooth enable` + **`svc data enable`** (the SIM only auto-raises an
  IMS-only PDN; the internet APN needs `svc data enable`) — §7a already notes the radios don't
  self-restore here.
- **:5277 (`ss -ltn | grep 5277`)** was listening on D-POCO for the whole round (enabled in a prior
  round; never force-stopped Gearhead, `KILL_GH=0`). **Not listening on D-MOTO** — see R6/R7.
- **`ss` segfaults on D-MOTO**; used `/proc/net/tcp{,6}` state `0A` + port `0x1495` instead.
- PC thermal: `no_turbo=1` already set, builds `taskset -c 0,2,4,6`. github build ~1 min (warm
  cache), playstore build 3 m 54 s (first ever, full native + kapt + Kotlin), no throttle, no
  power event.
- **D-HU was not associated to any Wi-Fi AP before the round started** — `dumpsys wifi` at 17:27
  (before any device work) already logged `Ignoring auto join disabled SSID: "Pegue Cdesta"`, and
  auto-join stayed disabled for its three saved networks throughout. R8/R8c toggled the radio off
  and back on; `wifi_on` returned to `1` / `Wi-Fi is enabled` both times (restoration requirement
  met) but it does not re-associate on its own, and adb has no `enable-network` verb on this build.
  This is a pre-existing rig condition, not something the round changed.
- **Gearhead was never force-stopped** (`KILL_GH=0` every run). `:5277` was still `LISTENING` on
  D-POCO and D-MOTO at the end.
- **Left installed:** candidate `58802778` on all three — playstore on D-POCO, github on D-MOTO and
  D-HU. `settings.xml` restored byte-identical to the round-start backup on all three (verified with
  `diff`).

## R0 — build both flavors + unit tests (gate)

**PASS** (exact)

- `assembleGithubDebug`: clean. APK `com.andrerinas.headunitrevived_3.3.0-beta2_debug.apk`,
  md5 `7d793c0cd716c9b984554bb5796684f4`.
- `assemblePlaystoreDebug`: **clean — first build ever.** `BUILD SUCCESSFUL in 3m 54s`. Native code
  (`buildCMakeDebug` for all 4 ABIs), kapt, and `compilePlaystoreDebugKotlin` all completed. APK
  `com.andrerinas.headunitrevived_3.3.0-beta2_debug.apk` (playstore/debug),
  md5 `47eed0db99e78d66a9fb8ebed44933bb`. md5s differ ✓.
- `testGithubDebugUnitTest`: **780 tests / 0 failures / 0 errors / 0 skipped** — exactly the
  predicted 780. `LoopbackBindPolicyTest` (5) and `SelfLaunchCoalescePolicyTest` (5) both present,
  test XMLs freshly written.

## R1 — github, offline, VPN raised (D-POCO). The working arm.

**PASS**

- APK: github, live md5 `7d793c0cd716c9b984554bb5796684f4` ✓
- Settings written: `auto-start-self-mode=true` (already), `log-level=2` (already),
  `auto-connect-delay-seconds=0` (already), `log-source=0` (already), `+log-capture-enabled=true`,
  `-auto-connect-last-session`, `-auto-connect-single-usb`.
- Radio state: `airplane-mode enable`; verified `Wi-Fi is disabled`, BT `state: OFF`,
  `Active default network: none`. `:5277` listening.
- Discard-rule check: clean — `createGroup SUCCESS`=0 (Self Mode is loopback, no P2P group),
  `MATCH! Starting AapService`=0, `SSL handshake complete`=1.
- Decisive log lines:
  - `17:55:31.987  HomeFragment.startSelfMode | Device is offline. Preparing Dummy VPN for Self Mode.`
  - `17:55:31.989  HomeFragment.startSelfMode | VPN permission already granted. Starting VPN service.`
  - `17:55:31.989  VpnControl.startVpn | VpnControl: Starting DummyVpnService (GitHub Build, excludeSelf=false)`
  - `17:55:32.559  DummyVpnService.startVpn | DummyVpnService: tun established (excludeSelf=false)`
  - `17:55:33.020  SelfMode: Installed AA version: 17.5.663204-release (major=17, minor=5)`
  - `17:55:33.021  SelfMode: AA 17.4+ detected. Connecting directly to Headunit Server on 127.0.0.1:5277...`
  - `17:55:33.834  AapSslContext.performHandshake | SSL handshake complete.`
  - Throughput: `17:55:47` rendered=298 (59fps) dropped=0 … `17:57:02` rendered=271 (54fps) dropped=0
    (capture ended 17:57:04; session alive the full hold).
  - HUR_Log banner: `LogExporter: session | build=3.3.0-beta2 (101) github/debug | device=Xiaomi M2007J20CG board=sm6150 api=35 | … | logLevel=INFO`
- No #897 signature.
- Measurements: connect→SSL 0.81 s. Session held ≥ 89 s at 54-59 fps, `dropped=0` throughout.

## R2 — github, offline, no VPN (D-POCO). THE POINT OF THE ROUND.

**PASS — hypothesis refuted.**

- APK: github, live md5 `7d793c0cd716c9b984554bb5796684f4`.
- Settings written: baseline minus `auto-start-self-mode` (deleted); `log-capture-enabled=true`,
  `log-level=2`, `auto-connect-delay-seconds=0`, `log-source=0`;
  `-auto-connect-last-session`, `-auto-connect-single-usb`.
- Radio state: still `airplane-mode enable` — `Wi-Fi is disabled`, BT `OFF`,
  `Active default network: none`.
- Entry: `MainActivity` launched, then `am start -a com.andrerinas.openheadunit.ACTION_START_SELF_MODE`
  at +5 s (via `AutomationActivity`, no `HomeFragment`).
- Discard-rule check: clean — `createGroup SUCCESS`=0, `MATCH!`=0, `SSL handshake complete`=1.
- Decisive log lines:
  - `17:57:58.598  AutomationActivity.onCreate | AutomationActivity: Received intent. Action: com.andrerinas.openheadunit.ACTION_START_SELF_MODE`
  - `17:57:58.659  SelfMode: AA 17.4+ detected. Connecting directly to Headunit Server on 127.0.0.1:5277...`
  - `17:57:58.686  SocketProjectionConnection.applyLowLatencySocketOptions | Socket low-latency options: tcpNoDelay=true, …` — **TCP connect succeeded**
  - `17:57:59.211  AapSslContext.performHandshake | SSL handshake complete.`
  - Throughput `17:58:17` rendered=267 (53fps) dropped=0 … `17:58:57` rendered=265 (53fps) dropped=0
    (capture ended 17:59:28; session alive the full hold).
  - **No `Preparing Dummy VPN`, no `tun established`, no `Device is offline …` line at all** (the
    intent route never touches `HomeFragment.startSelfMode()`).
  - Gearhead: `Head unit connected` ×1, `startDuplexConnection` ×1 — the proxy accepted and the
    duplex connection to `:car` completed.
  - HUR_Log banner: `… build=3.3.0-beta2 (101) github/debug …`
- No #897 signature.
- **Number pairing the verdict:** connect (`Socket low-latency options:` 17:57:58.686) → SSL
  complete 17:57:59.211 = **0.525 s**. `Head unit connected` count = **1**. No peer-silent episode,
  so no `Socket→silence` gap exists to measure.
- Reading: a full Self Mode session forms and holds at 53-54 fps with **no VPN and no network of any
  kind** on the github build. The brief's positive-arm assumption ("Android Auto's setup state
  machine cannot complete with no active network") does not hold. Per the brief this moves the cause
  into the playstore source set and makes R3 the point — and R3 then also passed.

## R3 — playstore, offline, auto-start (D-POCO). The reporter's exact configuration.

**PASS** (matches R2 — a session forms)

- APK: **playstore**, live md5 `47eed0db99e78d66a9fb8ebed44933bb`; `settings.xml` survived the
  `install -r -d` swap (verified `run-as cat` both sides).
- Settings written: `auto-start-self-mode=true` (re-added), `log-capture-enabled=true`,
  `log-level=2`, `auto-connect-delay-seconds=0`, `log-source=0`; last-session / single-usb absent.
- Radio state: `airplane-mode enable` — `Wi-Fi is disabled`, BT `OFF`, `Active default network: none`.
  `:5277` listening.
- Discard-rule check: clean — `createGroup SUCCESS`=0, `MATCH!`=0, `SSL handshake complete`=1.
- Decisive log lines:
  - `18:00:29.903  HomeFragment.startSelfMode | Device is offline and VPN is not available in this build. Self Mode may fail.` — **the exact #897 log line, reproduced**
  - `18:00:30.704  SelfMode: AA 17.4+ detected. Connecting directly to Headunit Server on 127.0.0.1:5277...`
  - `18:00:30.712  Socket low-latency options: tcpNoDelay=true, …`
  - `18:00:31.036  SelfMode: Launch of 'v17.4+' had no issues`
  - `18:00:31.337  SSL handshake complete.`
  - Throughput `18:00:39` rendered=268 (53fps) dropped=0 … `18:01:14` rendered=270 (54fps) dropped=0
    (4 windows; capture ended 18:02:01; session alive the full hold).
  - Gearhead: `Head unit connected` ×1, `startDuplexConnection` ×1.
  - HUR_Log banner: `… build=3.3.0-beta2 (101) playstore/debug | device=Xiaomi M2007J20CG board=sm6150 api=35 | … | logLevel=INFO` — capture attributed to the playstore flavor.
- No #897 signature. No `VpnControl: no dummy VPN …` line — expected: on the playstore flavor
  `isVpnAvailable()` is false, so `startSelfMode()` takes the `else if (activeNetwork == null)`
  branch and logs the "not available in this build" line without ever calling `startVpn()`.
- Reading: **the reporter's exact configuration (playstore + fully offline + auto-start + AA 17.5)
  works here.** connect→SSL 0.63 s, session held ≥ 90 s at 53-54 fps, `dropped=0`.

## R4 — playstore, online (D-POCO). Is a network enough?

**PASS**

- APK: playstore, md5 `47eed0db…`. Settings: `auto-start-self-mode=true` etc. (unchanged from R3).
- Radio state: `airplane-mode disable` + `svc wifi/bluetooth/data enable`. Default network =
  cellular `internet.movistar.com.co` (network 170, `INTERNET&NOT_RESTRICTED`), `ping 8.8.8.8` 54 ms.
  `Active default network: 170`. `:5277` listening.
- Discard-rule check: clean — `createGroup SUCCESS`=0, `MATCH!`=0, `SSL`=1.
- Decisive log lines:
  - **Neither `Device is offline …` line appears.**
  - `18:03:55.760  SelfMode: AA 17.4+ detected. Connecting directly to Headunit Server on 127.0.0.1:5277...`
  - `18:03:55.858  SelfMode: Launch of 'v17.4+' had no issues`
  - `18:03:56.283  SSL handshake complete.`
  - Throughput `18:04:19` rendered=269 (53fps) … `18:05:24` rendered=299 (59fps), `dropped=0`
    (capture ended 18:05:27).
  - Gearhead: `Head unit connected` ×1, `startDuplexConnection` ×1. playstore/debug banner.
- No #897 signature. Session held ≥ 90 s, 53-60 fps, `dropped=0`. It does not fail online, so R5 was
  still run.

## R5 — playstore, joined but with no internet (D-POCO). Is *any* network enough?

**PASS**

- APK: playstore, md5 `47eed0db…`. Settings unchanged (`auto-start-self-mode=true`).
- Radio state: D-HU soft AP `OHU-TEST` on `wlan2` @ 5785 MHz (`cmd wifi start-softap OHU-TEST wpa2
  testtest1234 -b 5`, `SAP is enabled successfully`). D-POCO: `airplane-mode enable` first, then
  `svc wifi enable`, `cmd wifi connect-network "OHU-TEST" wpa2 "testtest1234"`. Result: WIFI network
  171 on `wlan0`, `192.168.14.248`, default route `0.0.0.0/0 -> 192.168.14.243`,
  Score policies `EVER_EVALUATED&EVER_USER_SELECTED` (**not** `IS_VALIDATED`), `ping 8.8.8.8`
  100 % packet loss — a real network with no internet. `Active default network: 171`.
  `:5277` listening.
- Discard-rule check: clean — `createGroup SUCCESS`=0, `MATCH!`=0, `SSL`=1.
- Decisive log lines:
  - **Neither `Device is offline …` line appears** — `activeNetwork` was non-null (network 171)
    despite no internet.
  - `18:06:53.983  SelfMode: AA 17.4+ detected. Connecting directly to Headunit Server on 127.0.0.1:5277...`
  - `18:06:54.064  SelfMode: Launch of 'v17.4+' had no issues`
  - `18:06:54.564  SSL handshake complete.`
  - Throughput `18:07:33` rendered=301 (60fps) … `18:07:58` rendered=300 (60fps), `dropped=0`
    (capture ended 18:08:25).
  - Gearhead: `Head unit connected` ×1, `startDuplexConnection` ×1. playstore/debug banner.
- No #897 signature. Session held ≥ 90 s at 59-60 fps, `dropped=0`. A no-internet network is enough
  — though R2/R3 already showed *no* network is enough too.

## R6 — playstore, offline, auto-start (D-MOTO). Does the failure generalise?

**PASS** — reproduces R3 on a second device.

- APK: **playstore**, live md5 `47eed0db99e78d66a9fb8ebed44933bb`. Gearhead upgraded 17.3.662854 →
  **17.5.663204** (`SelfMode: Installed AA version: 17.5.663204-release` confirms it took — not
  `AA < 17.4`).
- **Deviation:** D-MOTO's dev "Start head unit server" (`:5277`) had never been enabled on this
  phone. `:5277` not listening pre-registers as UNTESTABLE; instead an operator enabled the toggle
  by hand (the one non-scriptable step, TESTING-TEMPLATE §0) and `:5277` came up on tcp6.
  `car_developer_mode` was also set to `1` via `settings put global` (restored after). `ss` segfaults
  on D-MOTO so `:5277` was read from `/proc/net/tcp6` state `0A`.
- Settings written: `auto-start-self-mode=true`, `log-level=2`, `log-capture-enabled=true`,
  `auto-connect-delay-seconds=0`, `log-source=0`; last-session / single-usb absent.
- Radio state: `airplane-mode enable` — `Wi-Fi is disabled`, BT `state: OFF`,
  `Active default network: none`. `:5277 LISTENING`.
- Discard-rule check: clean — `createGroup SUCCESS`=0, `MATCH!`=0, `SSL`=1.
- Decisive log lines:
  - `18:30:49.523  HomeFragment.startSelfMode | Device is offline and VPN is not available in this build. Self Mode may fail.`
  - `18:30:50.158  SelfMode: AA 17.4+ detected. Connecting directly to Headunit Server on 127.0.0.1:5277...`
  - `18:30:50.211  Socket low-latency options: tcpNoDelay=true, …`
  - `18:30:51.170  SSL handshake complete.`
  - Throughput `18:31:15` rendered=258 (51fps) dropped=0 … `18:31:30` rendered=260 (52fps) dropped=0
    (capture ended 18:32:22; session alive the full hold).
  - Gearhead: `Head unit connected` ×1, `startDuplexConnection` ×1.
  - HUR_Log banner: `… build=3.3.0-beta2 (101) playstore/debug | device=motorola motorola edge 30 neo board=miami api=34 …`
- No #897 signature. Session held ≥ 90 s at 51-52 fps, `dropped=0`. The offline Play Store build
  does Self Mode on AA 17.5 on this device too.

## R7 — github, offline, auto-start (D-MOTO). Does the fix generalise?

**PASS**

- APK: **github**, live md5 `7d793c0cd716c9b984554bb5796684f4`. D-MOTO still on AA 17.5 (17.4+ route).
- Settings: unchanged from R6 (`auto-start-self-mode=true` etc.). `appops … ACTIVATE_VPN` = `allow`
  (already, from a prior round).
- Radio state: `airplane-mode enable` — `Wi-Fi is disabled`, `Active default network: none`.
  `:5277 LISTENING`.
- Discard-rule check: clean — `createGroup SUCCESS`=0, `MATCH!`=0, `SSL`=1.
- Decisive log lines:
  - `18:33:01.907  HomeFragment.startSelfMode | Device is offline. Preparing Dummy VPN for Self Mode.`
  - `18:33:01.909  VPN permission already granted. Starting VPN service.`
  - `18:33:01.910  VpnControl: Starting DummyVpnService (GitHub Build, excludeSelf=false)`
  - `18:33:02.480  DummyVpnService: tun established (excludeSelf=false)`
  - `18:33:02.552  SelfMode: AA 17.4+ detected. Connecting directly to Headunit Server on 127.0.0.1:5277...`
  - `18:33:04.472  SSL handshake complete.`
  - Throughput `18:33:17` rendered=262 (52fps) … `18:34:28` rendered=261 (52fps), `dropped=0`
    (capture ended 18:34:35; session held ≥ 85 s).
  - Gearhead: `Head unit connected` ×1, `startDuplexConnection` ×1. github/debug banner.
- No #897 signature. `tun established`, then a session that held the whole window. The fix
  generalises to the second device.

## R8 — playstore, offline, legacy route (D-HU). Run last.

**PASS** (run executed, outcome recorded) — **and it surfaced the round's real finding: see R8c.**

- APK: **playstore**, live md5 `47eed0db99e78d66a9fb8ebed44933bb`; `settings.xml` survived the swap.
- Settings written: `auto-start-self-mode=true`, `log-level=2`, `log-capture-enabled=true`,
  `auto-connect-delay-seconds=0`, `log-source=0`; last-session / single-usb absent.
- Radio state: `svc wifi disable` on D-HU — `Wi-Fi is disabled`, `Active default network: none`.
- Discard-rule check: clean — `createGroup SUCCESS`=0, `MATCH!`=0.
- Decisive log lines:
  - `18:19:59.122  HomeFragment.startSelfMode | Device is offline and VPN is not available in this build. Self Mode may fail.`
  - `18:19:59.898  Wireless Server listening on port 5288`
  - `18:20:00.213  SelfMode: Installed AA version: 17.3.662864-release (major=17, minor=3)`
  - `18:20:00.214  SelfMode: AA < 17.4 detected. Starting WirelessServer on 5288 and running legacy triggers...`
  - `18:20:01.443  SelfLauncherLegacy.run | SelfMode: Launching AA Wireless Startup via Activity...`
  - `18:20:01.453  SelfMode: Launch of 'v17.3 and older' had caused an error`
    (`Permission Denial: … WirelessStartupActivity … not exported from uid 10171` — but Gearhead
    then started it from its own uid, `START_SUCCESS`)
  - `18:20:01.457  SelfLauncherBroadcast.run | SelfMode: Broadcast fallback 1 (WirelessStartupReceiver) sent.`
  - `18:20:01.465  GH.WSR | Starting wireless startup activity.`  ← **Gearhead's trigger fired**
  - `18:20:01.715  GH.WirelessFSM | Launch projection 127.0.0.1 5288`  ← **Gearhead tried to connect**
  - `18:20:31.465 (E)  SelfMode: nothing connected within 30000ms of the launch`  ← the **30000** legacy deadline
  - No `WirelessServer: Incoming connection detected`, no `SSL handshake complete`. **No session.**
  - HUR_Log banner: `… build=3.3.0-beta2 (101) playstore/debug | device=UNISOC MT50_YT610E4GFPSL_U board=uis7861_6h10 api=34 …`
- Reading: on the legacy route, playstore, fully offline — Gearhead's wireless FSM *starts* and
  aims projection at `127.0.0.1:5288`, but the connection never lands and the 30 s deadline expires.
  Whether that is the missing VPN or something else is answered by R8c.

## R8c — CONTROL (added): github, offline, legacy route (D-HU). Same rig, same round.

**PASS** — session forms. This isolates R8's failure to the missing dummy VPN.

- APK: **github**, live md5 `7d793c0cd716c9b984554bb5796684f4`. Identical settings to R8. Same
  `svc wifi disable`, `Active default network: none`. Same D-HU, same AA 17.3.662864, ~5 min after R8.
- Decisive log lines:
  - `18:24:44.544  HomeFragment.startSelfMode | Device is offline. Preparing Dummy VPN for Self Mode.`
  - `18:24:44.547  VpnControl: Starting DummyVpnService (GitHub Build, excludeSelf=false)`
  - `18:24:45.447  DummyVpnService: tun established (excludeSelf=false)`
  - `18:24:45.302  Wireless Server listening on port 5288`
  - `18:24:45.667  SelfMode: AA < 17.4 detected. Starting WirelessServer on 5288 and running legacy triggers...`
  - `18:24:46.564  GH.WirelessFSM | Launch projection 127.0.0.1 5288`
  - `18:24:47.180  WirelessServer: Incoming connection detected from /127.0.0.1`  ← **connection landed**
  - `18:24:47.654  SSL handshake complete.`  → throughput. `nothing connected within 30000ms` = 0.
  - HUR_Log banner: `… github/debug | device=UNISOC MT50 …`
- **The only thing that changed between R8 (no session) and R8c (session) is github vs playstore,
  i.e. dummy-VPN present vs absent.** Both fully offline, same rig, same route, same AA build, same
  round. So on the **legacy (< 17.4) route, an offline device needs the dummy VPN** — Gearhead's
  `WirelessFSM` cannot complete the projection connection to `127.0.0.1:5288` with no network of any
  kind. The Play Store build therefore **cannot** do offline Self Mode on the legacy route.
- This is consistent across rounds: `post-beta1-self-mode` round 2 R3 formed a legacy Self Mode
  session on this same D-HU while offline — on the **github** build, where the VPN comes up.

## R9 — CONTROL (added, post-round): forget the head unit, retry offline (D-POCO + D-MOTO)

**PASS on D-POCO** (session still forms) / **UNTESTABLE on D-MOTO** (`:5277` had dropped again) —
**but the D-POCO capture exposes the #897 mechanism: Gearhead's First-Run Experience (FRX).**

Idea: forgetting the head unit forces Gearhead's `:car` to run *first-connection* setup on the
next attempt instead of resuming cached state — the closest we can get on the bench to the
reporter's conditions. Operator forgot the head unit in AA → "Previously connected cars" on both
phones (the one non-scriptable step).

- **D-POCO** (playstore, fully offline — Wi-Fi + BT off, `Active default network: none`,
  `:5277 LISTENING`, live md5 `47eed0db…`): session formed, single SSL handshake at `18:54:55.049`,
  video 53-60 fps continuous to the end of the 90 s hold, `nothing connected` = 0, no peer-silent
  line. **But the capture now shows the fresh-connection path** (absent from R3, where the car was
  already known):
  - `18:54:54.812  CAR.SERVICE.FCD.LITE | FIRST_ACTIVITY_LAUNCHED with reason: CAR_SERVICE`
  - `18:54:54.980-55.265  VERSION / SSL / SDP negotiation STARTED…COMPLETED`
  - `18:54:55.308  CAR.FRX.CHECKS | cakewalkMinimumRequirements=…value=true, isSupportedOsVersion=…value=true, appsUpToDate=…value=true`
  - `18:54:55.308  CAR.FRX.CHECKS | shouldSkipLegacyFrxForCakewalk true, min requirements met`
  - `18:54:55.308  CAR.FRX.CHECKS | isFrxRequired=true` … `shouldRunLegacyFrx=false`
  - `18:54:55.322  CAR.SETUP.LITE | Triggering FRX: car connection is not allowed`
  - `18:54:55.314  CarInfo authorization: UNKNOWN` → `AUTHORIZATION STARTED` → connection proceeds
  - The version checks it ran: `com.google.android.apps.maps`, `…googlequicksearchbox`,
    `…tts`, `…projection.gearhead` all `installed ver >= minimum required ver`.
- **D-MOTO**: `:5277` was no longer listening by the time R9 was set up (the manual toggle from R6
  does not survive Gearhead ending its projection / a car being forgotten), so this arm is
  UNTESTABLE without another manual re-enable. D-MOTO's Google apps were checked and are all
  current, so it would have taken the same "cakewalk" fast path as D-POCO.

**Reading.** On a *first* connection `isFrxRequired=true`. D-POCO passes
`cakewalkMinimumRequirements` / `isSupportedOsVersion` / `appsUpToDate`, so
`shouldSkipLegacyFrxForCakewalk=true`, `shouldRunLegacyFrx=false`, and the connection is allowed
through without an interactive setup. **If any of those were false on the reporter's phone** — an
older Android, or an outdated Google app / Maps / TTS / Search, both very plausible on a SIM-less
Wi-Fi-less tablet that rarely updates — Gearhead would fall to the **legacy FRX**: a full
account/ToS/permissions flow that all but certainly needs a network. Offline + legacy-FRX-required
→ `:car` sits on a setup step it cannot finish → our loopback peer goes silent →
`nothing connected within 10000ms`. That is the #897 signature, and it is entirely inside
Gearhead's `:car` process. Our bench phones have current Google apps, so they never hit the legacy
path. **Not proven** (we could not make D-POCO fail the cakewalk checks), but it is the first
concrete, log-backed candidate mechanism and it points the fix squarely at the phone: update
Android Auto + Google + Maps, or complete AA's first-run once with a network, then Self Mode works
offline thereafter.

## Report back (brief §8)

1. **Did `assemblePlaystoreDebug` compile?** **Yes**, first attempt, `BUILD SUCCESSFUL in 3m 54s`,
   full native + Kotlin + kapt. github md5 `7d793c0cd716c9b984554bb5796684f4`,
   playstore md5 `47eed0db99e78d66a9fb8ebed44933bb` (different). The flavor can go in CI; the
   workflow's "cannot compile" comment is stale.
2. **R1 vs R2:** github build, offline, **with** the dummy VPN (R1) → full session, 54-59 fps, held
   the whole window. github build, offline, **without** any VPN (R2, intent route) → **also** a full
   session, 53-54 fps, held the whole window, connect→SSL 0.525 s, `Head unit connected` ×1. The VPN
   is not doing the thing the hypothesis said it does.
3. **R3 vs R4 vs R5:** the Play Store build forms and holds a Self Mode session **offline** (R3,
   the reporter's exact config), **online** (R4), and **on a joined network with no internet** (R5).
   Three yes's. Every configuration in this round works.
4. **For every failing run: last Gearhead line + `Head unit connected` count.** No run failed. Every
   session that formed showed `Head unit connected` ×1 and `startDuplexConnection` ×1; the last
   Gearhead line in each was `startDuplexConnection` followed by the normal projection traffic.
5. **R6 / R7:** whatever R2/R3 showed holds on a second device. **R6** (D-MOTO, playstore, offline,
   AA upgraded to 17.5): full session, 51-52 fps, held — same as R3. **R7** (D-MOTO, github,
   offline): `tun established`, full session, 52 fps, held — same as R1. Neither showed the #897
   signature.

### The one substantive result: R8 vs R8c

On the **legacy route** the dummy VPN *does* matter. Same rig, same round, same offline state,
same AA 17.3, same auto-start entry — only the flavor changed:

| | Flavor | VPN | Gearhead `WirelessFSM` | `WirelessServer: Incoming connection detected` | Session |
|---|---|---|---|---|---|
| R8  | playstore | none | `Launch projection 127.0.0.1 5288` | **never** | **no** — `nothing connected within 30000ms` |
| R8c | github | `tun established` | `Launch projection 127.0.0.1 5288` | at +2.6 s | **yes** — SSL + throughput |

So: **the Play Store build cannot do offline Self Mode on the legacy (< 17.4) route.** For a
Play Store user on an older Android Auto with a SIM-less / Wi-Fi-less tablet, Self Mode will not
connect. On 17.4+ it is fine (R3/R6). This is worth surfacing to the #897 reporter as a question:
if they were ever on the legacy route it would explain a failure, but their log says
`AA 17.4+ detected`, and on that route R3 (their exact config) works here.

## Anything the brief did not ask about

- **`playstoreDebug` APK is ~2.7 MB smaller than `githubDebug`** (19.15 MB vs 21.92 MB) — no
  `DummyVpnService`, no `BIND_VPN_SERVICE` component, no github `res/values` VPN strings.
- `exporterLogLevel` reports `INFO` in every banner — the fork's `DEBUG` default (the
  "debug-build caveat" in `CLAUDE.md`) is **not** present in this candidate. Good.
- No `LogAccessDialog` and no app-spawned `logcat` process in any capture on any of the three
  devices, with `log-capture-enabled=true`, on API 34 and API 35 — the `ad070f2f`
  `maxSdkVersion="32"` scoping works. `HUR_Log` files captured 60-99 KB each with the
  `LogExporter: session |` banner and the app's own Java + native lines.
- `VideoDecoder.logThroughput` prints only ~1 line per 15-40 s at `log-level=2` (INFO), not every
  5 s — enough to confirm a session is alive across the hold but not a dense series. The brief's R1
  PASS text says "throughput for ≥ 60 s"; on this log level that is 3-4 samples spanning the window,
  not a per-5 s series.
- **R8 legacy-launcher noise (not a defect):** `SelfLauncherLegacy` tries to start
  `WirelessStartupActivity` directly and gets `Permission Denial: … not exported from uid 10171`,
  logging `SelfMode: Launch of 'v17.3 and older' had caused an error` — then the
  `SelfLauncherBroadcast` fallback fires and Gearhead *does* start its own wireless startup
  (`GH.WSR | Starting wireless startup activity`). So the "caused an error" line is expected on this
  path and the broadcast fallback is what actually works. Present in both R8 and R8c.
- **On the 17.4+ route the `main` #899 "other causes" lines never printed** in R2-R7 — because a
  session formed every time and `handleNeverConnect()` was never reached. They *did* print in R8
  (`AA permissions were already granted or activity failed to start; checking other causes.`) after
  the 30 s legacy timeout, exactly as the brief said to expect on a failing run.
- **D-MOTO's Gearhead upgrade** (17.3.662854 → 17.5.663204, splits pulled from D-POCO) was a clean
  `install-multiple -r`; AA's own dev "head unit server" state did **not** survive as enabled and
  had to be turned on by hand afterwards.
