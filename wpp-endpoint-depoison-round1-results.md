# wpp-endpoint-depoison — round 1 results

**Candidate:** `fork/fix/wpp-endpoint-depoison` @ `020c6904`       **Base:** `main` @ `80a81099` (no baseline APK; this round has no A/B)
**APK md5:** `00ff2f8cd663af6d0d66c4824d450e2e` (candidate, confirmed as the live install both at first launch and re-checked mid-round)
**Unit:** D-HU (UNISOC MT50), Android 14, hotspot interface `wlan2`; phone D-POCO (POCO X3 NFC), Gearhead `17.8.163804-release.daily`
**Date:** 2026-09-18/19

**Evidence:** `rig-evidence-wpp-endpoint-depoison-round1` (`wpp-endpoint-depoison-round1-captures.zip`, sha256
`587933c79bdb8b401dc50748c260f7280529f5c99167784c2bb8a80f592c6afa`) — every logcat capture from every attempt,
including the discarded/aborted ones referenced in Setup notes.

## Setup notes

- **Phone runs Gearhead 17.8.163804**, the build the brief says is the *less*-evidenced reading (the 17.5 reading
  is the one that shows a real clear). Every result below is on 17.8 only; no 17.5 device is on this rig.
- **`hur-wifi-test-scripts/build_hur.sh` and `run_unit_tests.sh`** built R0. `install_and_launch.sh SKIP_BUILD=1`
  installed it (`adb install -r`, never uninstall/reinstall).
- **The rig's hotspot needs a manual `cmd wifi start-softap Navegadortz2 wpa2 12345678 -b 5` before every
  hotspot-transport run.** The app's own `SoftApCredentialsProvider` cannot bring the AP up itself, and R1's
  first attempt was lost to exactly this — 90s of `No interface named 'wlan2'. Present: ... p2p-wlan0-0` and then
  `No usable access point after 90s`, because I launched before starting the softap. `TESTING-TEMPLATE.md` §3
  doesn't say this in so many words for this thread; the `wpp-over-tcp` briefs' §3 do, and this brief's own §3
  cites "this rig's known-working hotspot values" without repeating the start-softap step. Confirmed working
  values reused from that thread: SSID `Navegadortz2`, password `12345678`, static BSSID `00:27:15:43:06:6a`,
  interface `wlan2`; `cmd wifi start-softap` puts it on 5745 or 5765 MHz depending on the run, both confirmed via
  `dumpsys wifi | grep SoftApInfo` before every hotspot run.
- **Forgetting the head unit's vehicle entry on D-POCO has no adb route** (confirmed again this round): Settings →
  apps → Android Auto → "Additional settings in the app" → Vehicles → the "Google" entry → Forget, via
  `uiautomator dump` + `input tap`, minimum taps. Done twice (before R1, before R3) to guarantee each poisoning
  run created its own fresh record rather than reusing one from an earlier thread.
- **A single accidental WiFi-off run corrupted R2's premise once and cost a full extra poison/exit/switch cycle.**
  After R1 passed, I switched to WiFi Direct for R2 without first checking `dumpsys wifi | grep "Wi-Fi is"` — the
  radio was off (`Wi-Fi is disabled`, cause not identified; stopping the SoftAP may leave station mode off on this
  ROM rather than restoring it). No P2P group could form (`WifiDirectManager: WiFi is off and this Android does
  not let an app switch it on`), and — this is the important part — the phone's own `NETWORK_UNAVAILABLE_
  NETWORK_NOT_FOUND` retries against the now-unreachable hotspot address were, by themselves, enough for it to
  drop the record R1 had just given it (`GH.WIRELESS.SETUP: No WPP on TCP configuration found in storage for the
  head unit`, before any TCP dial reached the candidate's `WppTcpServer` at all — the radio was off, so nothing
  could). See "Side finding" below; R1 was re-run (§ R1b) to give R2 a fresh record before its own window.
- **`wifi-direct-stable-identity=true` was already persisted from earlier threads on this exact P2P SSID
  (`DIRECT-RB-Navegadortz2`), which invalidates R2's own stated premise.** The brief says "The group's identity
  is unproven on a first bring-up, so the endpoint is withheld." On this rig it was not unproven: `identity
  stable=yes` every time (`WifiDirectManager.onGroupInfoAvailable`), so `WppEndpointPolicy.decide()` fell through
  to `Advertise`, and the log shows `NativeAA: advertising WPP over TCP at 192.168.49.1:5299` on every WiFi Direct
  bring-up this round, never `NativeAA: not advertising WPP over TCP: ...`. This is a brief erratum for this rig:
  a genuinely "first bring-up" identity state would need `wifi-direct-stable-identity`, `wifi-direct-group-name`,
  `wifi-direct-last-group-ssid`, `wifi-direct-last-group-bssid` and `wifi-direct-group-name-changes` all cleared
  first, which the brief's own §3 does not ask for and I did not do (out of scope to decide unilaterally mid-round).
- **The phone could not join this head unit's WiFi Direct group at all, on any of three separate bring-ups**,
  independent of the identity-stability question above. Every attempt: RFCOMM/WPP-TCP credential handoff
  completed, `GH.WIRELESS.SETUP: State changed to CONNECTING_WIFI`, then 36-45 s later `State changed to
  ABORTED_WIFI` / `Send WifiConnectStatus, status=STATUS_WIFI_NETWORK_UNAVAILABLE`. Confirmed from the phone's own
  `dumpsys wifip2p` each time: `groupFormed: false`, while the head unit's own `dumpsys wifip2p` showed
  `groupFormed: true isGroupOwner: true` — the group forms, the phone simply never associates to it. This is a
  rig/radio condition, not something either R2 or R4 could get past to reach the code path they exist to test.
  Two of three attempts also logged `WifiVersionResponse ... status=NO_SUPPORTED_WIFI_CHANNELS(-8)` on the phone's
  RFCOMM channel-negotiation reply, which may or may not be related; not chased further given the WiFi join
  itself never succeeded regardless.
- **R4's "Bluetooth listeners never open" precondition was not actually held.** `svc bluetooth disable` on D-HU
  self-reverts in ~14 s (documented rig quirk); the app's own transport-switch (hotspot torn down, WiFi Direct
  group recreated) triggers an internal `WifiDirectManager` stop/restart that calls `NativeAaHandshakeManager
  .start()` a second time. The first call correctly saw Bluetooth disabled (`NativeAA: Bluetooth adapter not
  available or disabled`, logged once, as designed); the second call, 13 s later, ran after the self-revert and
  opened the listeners anyway (`NativeAA: Starting Bluetooth Handshake Servers`, then `ACTIVELY LISTENING`). No
  method to hold the head unit's own Bluetooth off for the ~16 s this transport switch takes is known on this rig
  (per `TESTING-TEMPLATE.md` §7a, head-unit Bluetooth is "not switchable off" at all beyond the self-reverting
  window). This did not end up mattering for R4's verdict, since the WiFi-join failure above blocked it anyway,
  but it means a future attempt at this run needs a different method to hold Bluetooth down through the switch,
  or to disable it only after the transport has settled rather than before launch.
- Diffed `settings.xml` against a fresh backup at the round's start (kept in the evidence zip) and restored it
  byte-for-byte at the end (`diff` confirmed clean). Delta during the round: `native-ap-transport`,
  `native-wifi-version-exchange`, `hotspot-band`, `auto-enable-hotspot`, `hotspot-ssid`, `hotspot-password`,
  `static-bssid`, `hotspot-interface` (all per §3); nothing else was touched.
- `log-level=2` (INFO) throughout, as the brief says is enough; every decisive line quoted below is `AppLog.i` or
  `AppLog.w`, none behind a `LOG_VERBOSE` guard, confirmed against source before the round (`grep -n` against
  `020c6904`).
- D-HU's Bluetooth adapter was left in its self-reverted (on) state at the end of the round, matching how the
  round found it.

## R0 — build gate

**PASS**

- `./gradlew assembleGithubDebug` at `020c6904`: `BUILD SUCCESSFUL`.
- `./gradlew testGithubDebugUnitTest`: **2170 / 0** (whole suite), matching the brief exactly.
- Per-class counts (from `app/build/test-results/testGithubDebugUnitTest/TEST-*.xml`):
  `WppMessagesTest` **17**, `WppTcpServePolicyTest` **9**, `WppEndpointPolicyTest` **10**,
  `WppHandshakeSessionTest` **30** — all match the brief's stated counts.

## R1 — poison the phone deliberately

**PASS**

- Settings written: `native-ap-transport=1`, `native-wifi-version-exchange=true`, `hotspot-band=1`,
  `auto-enable-hotspot=false`, `hotspot-ssid=Navegadortz2`, `hotspot-password=12345678`,
  `static-bssid=00:27:15:43:06:6a`, `hotspot-interface=wlan2`.
- Radio state: SoftAP up on 5745 MHz (`dumpsys wifi | grep SoftApInfo`), confirmed before launch.
- Discard-rule check on the clean reconnect capture: `createGroup SUCCESS` count 0 (hotspot transport, not P2P),
  `MATCH! Starting AapService` count 0, `SSL handshake complete` count 1 — clean.
- First connect: `23:38:31.056 WppTcpServer: listening for Android Auto on TCP 5299`,
  `23:38:32.491 NativeAA: advertising WPP over TCP at 192.168.164.46:5299`,
  `23:38:37.627 AapSslContext.performHandshake | SSL handshake complete`.
- Reconnect (`headunit://exit`, confirmed `AapService` gone from `dumpsys activity services` before force-stop and
  relaunch — the naive exit→force-stop with only a 3 s gap raced the app's own reconnect once and produced a
  contaminated 3-SSL-handshake capture, kept as `r1_p1_connect_and_messy_reconnect.txt`/`r1_poco_messy.txt` in the
  evidence zip; re-run cleanly as below):
  `23:43:41.847 WppTcpServer: connection from 192.168.164.183` with **zero** `NativeAA: Connection accepted from`
  anywhere in the capture, `23:43:41.931 TLS handshake complete`, `23:43:42.825 SSL handshake complete`. Phone:
  `23:43:38.419 GH.WPP.TCP: Trying to start WPP on TCP with configuration: ...ssid=Navegadortz2,
  bssid=00:27:15:43:06:6A...ipAddress=192.168.164.46, port=5299` (12 occurrences across the reconnect window, all
  matching R1's own endpoint). The reconnect ran over the stored TCP endpoint with no RFCOMM step at all, as R1's
  PASS bar requires.

## R2 — the measurement

**INCONCLUSIVE** — the phone never joined this head unit's WiFi Direct group, on any of the runs attempted
(3 separate bring-ups; see Setup notes), so no dial ever reached `WppTcpServer` and the rejection code path was
never exercised in either direction.

- Settings: `native-ap-transport=0`, everything else unchanged from R1.
- Every `WppTcpServer:` line across the (re-run, post-re-poison) window: only
  `23:54:34.188 WppTcpServer: listening for Android Auto on TCP 5299` — no `connection from`, no
  `rejecting this dial`, no `session error:`.
- Phone side: RFCOMM credential handoff completed (`Info response received. Received
  credentials=WifiConfiguration(ssid=DIRECT-RB-Navegadortz2, bssid=00:27:15:43:06:6A...)`), then
  `23:55:41.647 State changed to ABORTED_WIFI` / `Send WifiConnectStatus, status=STATUS_WIFI_NETWORK_UNAVAILABLE`.
  `dumpsys wifip2p` on D-POCO at the time: `groupFormed: false isGroupOwner: false`.
- **Side finding, not this run's own signal but relevant to the round's open question:** during the earlier
  accidental WiFi-off attempt, the phone dropped its *own* stored WPP-TCP record
  (`No WPP on TCP configuration found in storage for the head unit`) after a single
  `NETWORK_UNAVAILABLE_NETWORK_NOT_FOUND`, with the head unit's WiFi radio fully off and no TCP-level interaction
  of any kind — meaning `020c6904`'s `ConnectionRejection` was never sent and could not have been the cause. On
  17.8.163804 at least, a plain unreachable-network condition is *also* sufficient to make Gearhead give up the
  stored endpoint, independent of anything the candidate does. This doesn't answer R2's question (whether the
  candidate's rejection clears the record on a *reachable* network the head unit is actively refusing), but it
  does mean 17.8 is not purely "never clears," and a repeat of this round should isolate the two mechanisms rather
  than let a network-layer failure stand in for the TCP-layer one.

## R3 — the regression that matters

**PASS**

- Settings: head unit back on R1's hotspot settings; D-POCO's vehicle entry forgotten and confirmed clean
  beforehand.
- First connect: `00:03:12.940 NativeAA: advertising WPP over TCP at 192.168.164.46:5299`,
  `00:03:20.774 SSL handshake complete`, `00:03:21.970`/`00:03:22.075 Media Sink Setup Request` (VIDEO, AUDIO2).
- Reconnect: `00:04:21.976 WppTcpServer: connection from 192.168.164.183`,
  `00:04:22.056 TLS handshake complete`, `00:04:22.998 handshake complete; projection session is up`,
  `00:04:23.082 SSL handshake complete`, `00:04:24.413`/`00:04:24.525 Media Sink Setup Request` (VIDEO, AUDIO2).
- `rejecting this dial` count across both halves of this run: **0** (`grep -ac` on `r3_hu_p1.txt` and
  `r3_hu_p2.txt`, both 0). A dial that would be served was never rejected, on either the first connect or the
  reconnect.

## R4 — the stranding guard

**INCONCLUSIVE** — for the same underlying reason as R2 (the phone cannot join this head unit's WiFi Direct group
right now), and the run's own precondition (Bluetooth listeners held closed) also did not hold for its full
duration; see Setup notes for both.

- Setup: R3's just-completed reconnect left the phone holding a live, working stored endpoint
  (`192.168.164.46:5299`), which stands in for "poison the phone again by repeating R1" — a deviation from the
  brief's literal wording, noted here rather than silently absorbed. `native-ap-transport` switched to `0`;
  `svc bluetooth disable` issued on D-HU immediately before launch.
- `NativeAaHandshakeManager.start()`'s first call correctly saw Bluetooth disabled:
  `00:07:18.428 E NativeAA: Bluetooth adapter not available or disabled`. Bluetooth self-reverted at
  `00:07:26.311` (`AdapterState: OffState → ... → TurningBleOnState`); a second, internally-triggered `start()`
  call at `00:07:31.627 NativeAA: Starting Bluetooth Handshake Servers` opened the listeners anyway
  (`00:07:31.659 NativeAA: ACTIVELY LISTENING on Android Auto UUID`).
- Despite RFCOMM being open (unintentionally), the WiFi join still failed exactly as in R2:
  `00:09:11.467 State changed to ABORTED_WIFI` / `STATUS_WIFI_NETWORK_UNAVAILABLE`; `dumpsys wifip2p` on D-POCO:
  `groupFormed: false`.
- No `WppTcpServer: connection from` ever appears in this run's capture (only the two `listening for Android Auto
  on TCP 5299` lines, one per internal restart) — no dial reached the server, so neither the PASS line
  (`not withdrawing the endpoint because the Bluetooth listeners are not open`) nor the FAIL line
  (`[TX] wrote type 10`) had any chance to appear. Confirmed absent, but as a consequence of the run never
  reaching the code under test, not as evidence either way.

## R5 — the banner

**Not reached.** R5 grades the head unit's screen state "after R2"; R2 never produced a rejection or a session to
put a banner state in front of, so there was nothing to observe. Not scored as FAIL or UNTESTABLE — simply
downstream of a run that didn't happen.

## Anything the brief did not ask about

- The rig-wide WiFi Direct join failure documented in R2/R4's Setup notes is not specific to this branch or this
  round — nothing in `020c6904` touches WiFi Direct group formation or association. It's worth flagging to
  whichever thread next needs a WiFi-Direct-transport run on this rig, since it will block that run the same way.
- `WifiVersionResponse ... status=NO_SUPPORTED_WIFI_CHANNELS(-8)` on the RFCOMM channel-negotiation reply appeared
  on two of the three WiFi Direct attempts. Not chased down (out of scope for this round, and the WiFi-join
  failure blocked everything downstream of it regardless), but worth a line in `TESTING-TEMPLATE.md` §7a if a
  future round on this rig hits it again and has time to isolate it.
