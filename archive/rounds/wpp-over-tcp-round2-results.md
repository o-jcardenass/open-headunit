# wpp-over-tcp — round 2 results

**Candidate:** `fork/fix/wpp-over-tcp` @ `8ff9510d` &nbsp;&nbsp; **Baseline:** `origin/main` @ `2f07eeec`
**APK md5:** candidate `92749fcd08d41e3ae2467299659587eb` / baseline `25c581be513d44c5ac0189bf60202ac1`
&nbsp;&nbsp;(both `3.3.0-beta4`, `assembleGithubDebug`)
**Unit:** UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14, BT adapter name `Navegadortz2` (`11:46:03:10:33:59`)
**Phone:** POCO X3 NFC (`4f4027e9`, `M2007J20CG`), Android 15, Gearhead `17.5.663204-release`
**Date:** 2026-09-01

## Verdict in one line

**R0 PASS, R1 PASS (session still forms on the first connect, both builds), R2 FAIL — the point of
the round.** The candidate's WPP-over-TCP endpoint is advertised and the phone dials it, but (a) the
TLS handshake to our `WppTcpServer` on 5299 is **rejected by the phone every time**, and (b) the
phone **stores the endpoint** — which embeds the current P2P group's SSID/BSSID — and on the next
reconnect loops on that now-stale network forever, never re-running the Bluetooth handshake, so
**no session forms at all until the head unit is manually cleared from the phone's Android Auto
settings.** Turning `native-wifi-version-exchange` back off prevents new damage but does **not**
recover an already-affected phone. As implemented the feature is worse than the pre-17.4 breakage it
targets; do not ship it on by default, and the `DEBUG` exporter-log default must not ship either.

The candidate touches no file in the WifiDirect layer and R1 Arm A (baseline) behaves identically to
Arm B for session formation, so nothing here is a regression *in the candidate's own diff* — the new
hazard is entirely in what the WPP-over-TCP advertisement does to the phone's stored state.

## Setup notes

### Rig / environment

- Transfer branch fetched, checked out at `6d297c1b`. `TESTING-TEMPLATE.md` and
  `wpp-over-tcp-round1-brief.md` read in full. Round 1 results (`8d369ac5`, R0 FAIL) not re-read
  beyond confirming it was the proto-regen build failure the brief already summarises.
- Candidate/baseline SHAs verified: `git rev-parse fork/fix/wpp-over-tcp` → `8ff9510d95c9…` ✓,
  `git rev-parse 2f07eeec` → `2f07eeec18d3…` ✓ (candidate is one commit on current `main`, no
  history rewrite since the brief).
- **R0 was run on the coding host** (`hur-wifi-test-scripts/run_unit_tests.sh`, JDK
  `/opt/android-studio/jbr`) as the brief now allows. `compileGithubDebugKotlin` clean.
- Both APKs built with `hur-wifi-test-scripts/build_hur.sh` and copied out of `apks/` immediately
  (it deletes `com.andrerinas.headunitrevived_*.apk` before each build, and both builds share the
  `3.3.0-beta4` filename). Saved as `round-wpp-over-tcp/candidate-8ff9510d.apk` and
  `round-wpp-over-tcp/baseline-2f07eeec.apk`.
- Scripts used: `run_unit_tests.sh` (R0), `build_hur.sh` (both builds), `set_hu_prefs.sh` (settings,
  no-relaunch multi/single-key), `banner_watch.sh` (renderer-confirm banner — it never fired this
  round). Nothing added or changed.
- Arm switching done with `adb install -r <named-apk>` + live-md5 verification every time, per
  §7a; `settings.xml` edited directly, never via a relaunching script.

### `settings.xml` delta

Pre-round backup diffed against a fresh `cat`: the only key this round changed was
`native-wifi-version-exchange` (`false` → `true` for R1/R2, back to `false` for R3, restored to the
backup value `false` at the end). Everything else was left as the rig carried it. Load-bearing
carried-over non-defaults noted for the record:

| key | value | note |
|---|---|---|
| `wifi-connection-mode` | `3` | Native AA — the round's mode, already set |
| `native-ap-transport` | `0` | WiFi Direct — already set |
| `wifi-direct-band` | `1` | **FORCE_5GHZ** — carried from an earlier thread; see the 5 GHz note below. The brief did not name this key. |
| `log-level` | `2` | INFO — sufficient, all the round's lines are `.i`/`.w` |
| `head-unit-make` / `vehicle-make` | `Google` | carried over (template §7a header says `Royal Enfield`); irrelevant at v4.2 which clears the allowlist gate |
| `view-mode` | `2` | GLES |

Final state: `settings.xml` restored byte-identical to the backup (`diff` clean); candidate APK
(`92749fcd…`) left installed; no stray capture processes; Gearhead force-stopped on the phone to
clear an accumulated RFCOMM throttle (see R3b). The phone holds **no** stored WPP-over-TCP config at
end of round (cleared by the operator before R3b, and R3b with the flag off never wrote one).

### A pre-existing stale config ate the first hour

The first four connect attempts (candidate R1 ×2 at 5 GHz, one 2.4 GHz diagnostic, baseline R1 ×1)
**all failed the same way** and cost about an hour before the cause was found: the phone already
held a **stale** `WifiProjectionProtocolOnTcpConfiguration(ssid=DIRECT-B0-Navegadortz2,
bssid=9E:AE:FB:DD:16:60, ipAddress=192.168.49.1, port=5299)` from testing that **predates this
session** — it appears in none of this round's captures and `port=5299` is `WppTcpServer.PORT`, so
only this feature branch could have written it. Gearhead dialled it on every connect, got
`NETWORK_NOT_FOUND`, and never fell back to RFCOMM. Force-stopping Gearhead and cycling the phone's
BT/Wi-Fi did not clear it. Only "forget this head unit" in the phone's Android Auto settings did.
**This is the same mechanism R2 reproduces deliberately below** — it had simply already happened to
this phone once before the round started.

### 5 GHz scan-frequency interaction (not the blocker, but real)

With `wifi-direct-band=1` the group comes up on a 5 GHz channel the platform picks (5180 / 5745 /
5785 / 5805 seen). The phone logs, every attempt:
`GH.WirelessNetRequest: Wi-Fi frequency is not specified: [5220, 5805, 5200, 5745, 5240, 5180];
size = 15; limit = 5` and `WIRELESS_WIFI_FREQUENCY_NOT_SPECIFIED` — its `WifiNetworkSpecifier` can
only carry 5 of 15 candidate 5 GHz frequencies and we do not tell it which one, so a group on a
channel outside that set (e.g. 5785) is simply `SCAN_RESULTS_NETWORK_NOT_FOUND`. Once the phone has
*fresh* credentials with the group's `mostRecentlyUsedFrequenciesMhz` it finds it fine (R1 Arm A
and Arm B both connected in ~5 s). It only bites when combined with the stale-config loop, where the
phone never gets fresh frequencies.

### Rig-state drift worth noting

- Template §7a says the rig is "permanently joined to `Pegue Cdesta`, 5500 MHz". This round it was
  **not** joined to any station network (`Wi-Fi is enabled … Supplicant state: DISCONNECTED`,
  `logStationCoexistence` logged "not connected to any other WiFi network" every group). Harmless
  for a P2P-group round; flagged for the next brief that asserts a station association.
- The MT50's `NativeAA` listener uses one radio only: `on local radio [Navegadortz2]`. No
  dual-radio fan-out this round.
- `BluetoothHelper.adapterForService(…IBluetoothAudioProviderFactory/default) failed:
  NoSuchMethodException … BluetoothAdapter.<init>` prints at `E` once at every launch, from the
  six-deep adapter-handle fallback chain (`getAllBluetoothAdapterHandles`). Caught and handled — the
  AA listener comes up fine on the next line. Present on baseline too; not new, not fatal.

## R0 — build gate

**PASS**

`./gradlew :app:testGithubDebugUnitTest` at `8ff9510d`, coding host. `BUILD SUCCESSFUL`, including
`compileGithubDebugKotlin` (round 1's failure point). From the JUnit XML:

- `WppMessagesTest` — **10** tests, 0 failures, 0 skipped
- `ProjectionDeepLinkTest` — **8** tests, 0 failures, 0 skipped
- whole suite — **971** tests, 0 failures, 0 skipped, across **97** classes

Matches the brief's prediction exactly (18/0 on the two named suites, 971/0 overall).
Also present alongside: `WppFramingTest`, `WppHandshakeSessionTest`.

## R1 — a Native AA connection still works, with the endpoint advertised

Both arms run with a **clean phone** (operator cleared the stale stored config first). Phone left
connected over BT; HU app launched; phone re-triggered with one BT off/on. `wifi-direct-band=1`,
`native-wifi-version-exchange=true`.

### R1 Arm A — baseline `2f07eeec`  →  **PASS**

- Settings: `wifi-connection-mode=3`, `native-ap-transport=0`, `native-wifi-version-exchange=true`,
  `wifi-direct-band=1`, `log-level=2`
- Radio: phone BT+Wi-Fi on throughout; one `svc bluetooth` off/on to re-trigger
- Discard check: **clean** — `createGroup SUCCESS` ×1, `SSL handshake complete` ×1, `MATCH! Starting
  AapService` ×0, `Magic Garbage` ×0, single interface `p2p-wlan0-0`
- Decisive lines:
  ```
  00:22:59.300  NativeAA: Connection accepted from POCO X3 NFC … on local radio [Navegadortz2]
  00:22:59.642  NativeAA: [TX] Sending WifiVersionRequest (Type 4) v1.1        ← baseline announces 1.1
  00:22:59.728  WifiDirectManager: 5GHz createGroup SUCCESS!
  00:22:59.784  NativeAA: [RX] WifiVersionResponse v1.1 status=-8
  00:23:04.986  WirelessServer: Incoming connection detected from /192.168.49.236
  00:23:05.280  SSL handshake complete
  00:23:07.644  First frame rendered (hardware decode)
  ```
- `createGroup SUCCESS` → `Incoming connection detected`: **5.26 s**
- `advertising WPP over TCP`: **0** (correct — 1.1 is below the 4.1 gate, no field 6)
- Projection: 10 throughput windows, `c2.unisoc.hevc.decoder`, 23–42 fps, **`dropped=0` every
  window**, `decodeLatency` 9–12 ms

### R1 Arm B — candidate `8ff9510d`  →  **PASS as a session; the WPP-over-TCP channel fails**

- Same settings, same procedure, candidate APK (`92749fcd…`) verified live
- Discard check: **clean** — `createGroup SUCCESS` ×1, `SSL handshake complete` ×1, `MATCH` ×0,
  `Magic Garbage` ×0, single interface `p2p-wlan0-1`
- Decisive lines:
  ```
  00:24:19.429  WppTcpServer: listening for Android Auto on TCP 5299
  00:24:19.996  WifiDirectManager: 5GHz createGroup SUCCESS!
  00:24:23.476  NativeAA: Connection accepted from POCO X3 NFC … on local radio [Navegadortz2]
  00:24:23.795  NativeAA: [TX] Sending WifiVersionRequest (Type 4) v4.2       ← candidate announces 4.2
  00:24:23.796  NativeAA: advertising WPP over TCP at 192.168.49.1:5299       ← real address, not 0.0.0.0
  00:24:23.977  NativeAA: [RX] WifiVersionResponse v4.2 status=-8             ← phone negotiated 4.2
  00:24:25.495  WirelessServer: Incoming connection detected from /192.168.49.106
  00:24:25.743  SSL handshake complete
  ```
- `createGroup SUCCESS` → `Incoming connection detected`: **5.50 s** (parity with baseline)
- **Advertised address form: `192.168.49.1` (the resolved GO gateway), not `0.0.0.0`.**
- Projection: 41 throughput windows before the R2 exit, 48–50 fps, **`dropped=0` every window**,
  `decodeLatency` 9–11 ms. Session held rock-solid for ~3.5 min until `headunit://exit`.
- **But** the phone immediately dials 5299 and the TLS handshake fails, on a loop:
  ```
  00:24:25.584  WppTcpServer: connection from 192.168.49.106
  00:24:35.627  WppTcpServer: session error: connection closed
  00:24:35.628  WppTcpServer: session closed
  00:24:35.658  WppTcpServer: connection from 192.168.49.106     (repeats every ~10 s)
  ```
  Over R1+R2: `WppTcpServer: connection from` ×**21**, `session error: connection closed` ×**20**,
  `WppTcpServer: TLS handshake complete with` ×**0**.
  Phone side, in a tight loop:
  ```
  chromium: [ERROR:net/socket/ssl_client_socket_impl.cc:964] handshake failed;
            returned -1, SSL error code 1, net_error -101
  ```
  (`SSL error code 1` = `SSL_ERROR_SSL` protocol error; `net_error -101` = `ERR_CONNECTION_RESET`.)
  **The phone's TLS client does not accept our `WppTcpServer`** — this is the one question §1 said
  static analysis could not settle. The inverted SSL role (phone = client, HU = server, same v1
  cert) is not accepted by Gearhead 17.5's Chromium TLS stack.

## R2 — reconnect, and see whether the phone dials TCP

**FAIL** (this is the point of the round)

At R1 Arm B the phone stored the endpoint (confirmed from the phone log):
```
GH.WPP.TCP: Trying to start WPP on TCP with configuration:
  WifiProjectionProtocolOnTcpConfiguration(
    wifiConfiguration=WifiConfiguration(ssid=DIRECT-OQ-HeadUnit, bssid=BE:9E:16:68:74:10,
      securityMode=WPA2_PERSONAL, mostRecentlyUsedFrequenciesMhz=[5745, 5765]),
    ipAddress=192.168.49.1, port=5299)
```
`DIRECT-OQ-HeadUnit` was the group up at that instant — so the stored config is correct *for that
one group only*.

Reconnect procedure: `headunit://exit` (tears down the mode-3 group), `am force-stop`, relaunch
`MainActivity` at 00:28:02. Result:

- HU makes a **new** random group: `DIRECT-OI-Navegadortz2` (5180), then `recoverNativeGroup`
  recreate 1/4 → `DIRECT-7M-Navegadortz2` (5240) at the 60 s mark
- `createGroup SUCCESS` ×3 after the relaunch, `recoverNativeGroup` ×1
- **`NativeAA: Connection accepted from …` ×0** — no fresh Bluetooth handshake
- **`WirelessServer: Incoming connection detected` ×0**, **`SSL handshake complete` ×0** — no session
- Phone side, on a loop (~every 40 s, then throttled):
  ```
  00:28:36.132  GH.WIRELESS.SETUP: State changed to CONNECTING_RFCOMM
  00:28:36.132  GH.WPP.TCP: Trying to start WPP on TCP with configuration:
                  …ssid=DIRECT-OQ-HeadUnit, bssid=BE:9E:16:68:74:10 … port=5299     ← STALE
  00:28:43.157  GH.WirelessNetRequest: Failed to find network within PT7S
  00:28:52.202  WIRELESS_WIFI_SCAN_RESULTS_NETWORK_NOT_FOUND
  00:29:12.212  GH.WPP.TCP: Restarting WPP over TCP, reason: NETWORK_UNAVAILABLE_NETWORK_NOT_FOUND
  00:29:17.283  WIRELESS_CONNECTING_RFCOMM … throttle THROTTLE_LIMIT_EXCEEDED
  ```

The phone enters `CONNECTING_RFCOMM`, then immediately abandons it to dial the stored TCP config,
loops on the dead network, restarts WPP over TCP, and never completes a fresh handshake. **Native
AA is bricked for this phone** — it only recovered when the operator did "forget this head unit" in
the phone's Android Auto settings.

**Mechanism.** The advertised endpoint's payload embeds *this group's* `ssid`/`bssid`. The MT50
generates a fresh random group name on every service start and on every 60 s `recoverNativeGroup`
recreate, so a stored endpoint is stale within a minute of being written. Gearhead 17.5 then
prioritises the stored TCP config over a fresh RFCOMM handshake and has no fallback when the network
is gone. This is exactly the risk `sendWifiVersionRequest`'s own KDoc names ("the phone stores it
and dials it on the next connection instead of running this handshake again") — the trigger just
isn't "a port nothing answers", it's "a Wi-Fi network that no longer exists".

## R3 — positive control: turn the endpoint off

### R3a — flag OFF, phone still holding the R1 stored config  →  confirms the flag does not heal

`native-wifi-version-exchange=false`, phone **not** cleared, relaunch + BT cycle.

- HU: `advertising WPP over TCP` ×0 (correct), `WppTcpServer: connection from` ×0
- Phone: still `GH.WPP.TCP: Trying to start WPP on TCP with configuration: …ssid=DIRECT-OQ-HeadUnit
  … port=5299` — the stored config is sticky and independent of our current advertisement
- `SSL handshake complete` ×0, `WirelessServer: Incoming connection` ×0, `recoverNativeGroup` ×1 —
  **still no session**

So the brief's stated mitigation (§3: flip the flag to `false`) prevents *new* poisoning but does
**not** recover a phone that already stored an endpoint. That needs "forget this head unit" on the
phone.

### R3b — flag OFF, phone cleared  →  **PASS** (first connect); reconnect **INCONCLUSIVE**

`native-wifi-version-exchange=false`, operator cleared the head unit from the phone first, candidate
APK still installed.

First connect (00:33):
```
00:33:32.397  WppTcpServer: listening for Android Auto on TCP 5299       (server still binds — unconditional)
00:33:35.395  NativeAA: Connection accepted from POCO X3 NFC …
00:33:40.055  WirelessServer: Incoming connection detected from /192.168.49.26
00:33:40.300  SSL handshake complete
```
- `advertising WPP over TCP` ×**0**, `WppTcpServer: connection from` ×**0**
- Phone: `GH.WIRELESS.SETUP: No WPP on TCP configuration found in storage for the head unit, will
  not start WPP on TCP.` (the §4 "endpoint not taken" landmark)
- `createGroup SUCCESS` ×1, `MATCH` ×0, `recoverNativeGroup` ×0 — clean single group
- So candidate + flag off + clean phone == baseline. **The flag is a working kill switch for an
  un-poisoned phone.**
- Video note: this session's throughput went 13 fps for one window then `rendered=0, fed=0` and
  stayed there — `fed=0` means the phone stopped sending video (phone-side; SSL session stayed up).
  Not seen in R1 Arm A/B where video ran 23–50 fps. Phone had been through ~15 connect cycles by
  this point; most likely phone screen/projection state, not a decoder fault. Flagged, not chased.

Reconnect (`headunit://exit` + relaunch, 00:34:49): **INCONCLUSIVE.** No fresh
`Connection accepted from`, `recoverNativeGroup` ×1, no new session in the window. Phone side:
`WIRELESS_CONNECTING_RFCOMM … throttle THROTTLE_LIMIT_EXCEEDED` on a ~5 s loop, Gearhead session
`e37f3a63` now **1 h 3 m old** — the same session object across the entire round, and by now
rate-limiting its own RFCOMM attempts after ~1 h of my connect/brick cycling. This is accumulated
phone-side test wear, not a code signal; a Gearhead restart (done at end of round) clears it. The
first-connect result above is the R3b verdict that counts.

## R4 — hotspot transport

**Not run.** The brief marks it optional ("Do not spend the round on it") and R2's FAIL is a stop
condition ("If R1 cannot form a session at all, stop, report it"). The round's budget went to
isolating R1/R2 against a baseline, which was the more valuable use.

## Report-back answers (brief §8)

1. **R1 verdict:** a Native AA session still forms with the version exchange on — **PASS**, ~5.5 s
   group-to-session, projection 48–50 fps `dropped=0`, at parity with baseline (~5.3 s). No
   regression in session formation.
2. **R2 — how many of three reconnects produced `WppTcpServer: connection from`:** the phone dials
   5299 on **every** connect including the first (21 dials across R1+R2), but **TLS never completes
   once** (`session error: connection closed` ×20, `TLS handshake complete with` ×0). Phone-side
   error: `chromium … handshake failed; returned -1, SSL error code 1, net_error -101`. And on the
   first true *reconnect* (fresh service start) the phone is bricked by its own stored stale
   endpoint and forms **no session at all**.
3. **Advertised address form:** `192.168.49.1` — the resolved P2P group-owner gateway, not
   `0.0.0.0`. The resolved address is doing the work, not the gateway fallback.

## Anything the brief did not ask about

- **The stored-endpoint hazard is the headline, and it is a coding-session problem.** Two coupled
  faults: (1) the advertised endpoint embeds an **ephemeral** P2P group identity that the HU itself
  discards within ~60 s (`recoverNativeGroup`) or on the next launch, and (2) Gearhead has no
  fallback from a stored-but-dead WPP-over-TCP config to a fresh RFCOMM handshake — that half we do
  not control. Whatever the fix, it cannot be "advertise the current group's SSID/BSSID and hope":
  candidate directions are a **persistent** P2P group (stable SSID/BSSID across recreations, via
  `cmd wifip2p` persistent-group / a fixed netns name), or not advertising the Wi-Fi specifics at
  all and letting the phone re-handshake for them, or gating the whole advertisement behind "our
  group identity is stable for this session".
- **Our TLS server on 5299 is independently broken** — even when the stored network was current (R1
  Arm B), the phone's TLS client reset every connection (`SSL_ERROR_SSL`). The WPP-over-TCP channel
  never carried a byte this round. Worth settling *why* (cert chain? the v1 no-EKU cert in the
  server role? TLS version / cipher list? SNI?) before any more rig time goes to this branch — a
  packet capture or `AapSslContext` server-side error logging would answer it; right now
  `WppTcpServer` only logs `session error: connection closed` with no cause.
- **`recoverNativeGroup` recreating the group every 60 s is the amplifier.** Even absent the WPP-TCP
  feature, a phone that is slow to join (5 GHz scan-frequency limit; fresh reconnect after a long
  BT gap) races a 60 s teardown that changes the SSID out from under it. Seen on baseline too. Not
  this branch's bug, but this branch turns a recoverable slow-join into a permanent brick.
- **Debug-build caveat still stands:** `Settings.exporterLogLevel` — this round's builds are off
  `2f07eeec` so INFO, but the fork's `fix/native-regression` (which other stacked branches sit on)
  defaults it to `DEBUG`. Don't ship that.
- **`native-wifi-version-exchange` currently defaults ON in the candidate.** Given R2, it must go
  back to default-OFF before this branch is shippable, and the release notes should tell affected
  users to "forget" the head unit on their phone.
