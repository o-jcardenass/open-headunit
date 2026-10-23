# wpp-over-tcp — round 3 results

**Candidate:** `fork/fix/wpp-over-tcp` @ `968573ab` (three commits on `2f07eeec`) &nbsp;&nbsp; **Baseline:** none needed (round 2 measured it; every run here is a settings change on the candidate)
**APK md5:** candidate `bec180b7356c84561158f50bfc71b9c2` (`3.3.0-beta4`, `assembleGithubDebug`)
**Unit:** UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14, BT adapter `Navegadortz2` (`11:46:03:10:33:59`), single radio
**Phone:** POCO X3 NFC (`4f4027e9`, `M2007J20CG`), Android 15, Gearhead `17.5.663204-release`
**Date:** 2026-09-01

## Verdict in one line

**R0 PASS, R1 PASS, R4 PASS (both transports) — but R2 PARTIAL and R3 FAIL: `894093fc` does not make
the feature safe on the hotspot transport either.** The default WiFi Direct transport can no longer
be poisoned (R1, the point of the round): the endpoint is never advertised there, the phone stores
nothing, and a session forms on both the first connect and the reconnect — the exact sequence that
got zero sessions in round 2's R2. But on the **hotspot** transport the endpoint *is* advertised
(by design), the phone stores it, and because `968573ab`'s TLS server **still never completes a
handshake** (`session error: SSLHandshakeException <- EOFException` on every dial, same 10 s stall
as round 2), the reconnect **bricks the phone exactly as round 2 did** — it loops
`CONNECTING_RFCOMM → stored WPP-TCP config → TLS fails → retry` every ~31 s and forms **no session
at all** until the head unit is forgotten on the phone. Round 2's brick was "network not found";
this one is "network found, TLS dead" — same outcome. The certificate-presentation fix landed as a
richer error line, not as a working handshake.

R4 confirms the kill switch on both transports: flag off → nothing advertised, nothing dialled,
session forms.

## Setup notes

### Rig / environment

- Transfer branch fetched and checked out at `ea8dd434`. `TESTING-TEMPLATE.md` and this thread's
  `wpp-over-tcp-round3-brief.md` read in full; `wpp-over-tcp-round2-results.md` re-read (same thread,
  immediately prior round, cited throughout the brief).
- Candidate SHA verified: `git rev-parse fork/fix/wpp-over-tcp` → `968573abd3ab…` ✓, three commits
  on `2f07eeec` (`8ff9510d`, `894093fc`, `968573ab`), no history rewrite since the brief.
- **R0 run on the coding host** as the brief allows (`hur-wifi-test-scripts/run_unit_tests.sh`, JDK
  `/opt/android-studio/jbr`). Build (`build_hur.sh`) and tests both clean.
- Scripts used: `build_hur.sh` (R0 build), `run_unit_tests.sh` (R0), `set_hu_prefs.sh` (all settings
  writes, multi-key no-relaunch). Nothing added or changed.
- Candidate installed with `adb install -r` (settings preserved, verified byte-identical before and
  after). Live md5 confirmed `bec180b7…` before every run.
- Round run in two sittings: R0 + R1 + R4 (WiFi Direct) first, then R2 + R3 + R4 (hotspot) after a
  manual "forget the head unit" on the phone (twice — before R2 and again before R4's hotspot half,
  because R3 bricked the phone). Both manual forgets were done by the operator; there is no
  scriptable path to it.

### `settings.xml` delta

Pre-round backup taken with the app force-stopped. Keys changed during the round:

| key | values used | note |
|---|---|---|
| `native-wifi-version-exchange` | `false` → `true` (R1, R2) → `false` (R4) | the arm switch |
| `native-ap-transport` | `0` (R1, R4-WD) → `1` (R2, R3, R4-HS) | WiFi Direct vs hotspot |
| `hotspot-ssid` | `""` → `Navegadortz2` | required on this rig (§7a: cannot read own SoftAP config) |
| `hotspot-password` | `""` → `12345678` | " |
| `static-bssid` | `0` → `00:27:15:43:06:6a` | the SoftAP's real BSSID, read from `SoftApInfo` |
| `hotspot-interface` | `""` → `wlan2` | the AP interface (§7a); naming it bypasses the "is it on air" check |
| `auto-enable-hotspot` | `true` → `false` | **had to change** — see the deviation note below |

Keys already at the brief's required values and left alone: `wifi-connection-mode=3`,
`wifi-direct-band=1` (FORCE_5GHZ), `hotspot-band=1`, `log-level=2` (INFO),
`head-unit-make`/`vehicle-make=Google`, `view-mode=2` (GLES).

Final state: `settings.xml` **restored byte-identical to the backup** (`diff` clean — pushed back
and re-chowned). The rig's transient `cmd wifi force-softap-band` / `force-softap-channel` overrides
were reset to `disabled` and the SoftAP stopped. Candidate APK (`bec180b7…`) left installed. No
stray capture processes. Phone left with WiFi/BT on, no stale saved networks, head unit forgotten.

### Clean-run protocol deviations (all per §7a, this rig)

- **Airplane mode cannot be driven from adb on the POCO** (§7a). Link-state lever was the phone's
  own Bluetooth adapter: `svc bluetooth disable` before each run, `svc bluetooth enable` after the
  head unit had settled — this raises the `ACL_CONNECTED` that `AutoStartReceiver` needs and is the
  documented way to (re)arm a Native AA session on this rig.
- **`MATCH! Starting AapService` appears once per connect** and is **not** contamination here: it is
  the phone's own Bluetooth reconnect firing `AutoStartReceiver`, i.e. the mechanism by which
  Native AA (re)connects on this rig after the phone's BT is re-enabled. Each occurrence carried
  exactly **one** `createGroup SUCCESS` and **one** `SSL handshake complete` (§7a: the discard is a
  *second* `createGroup SUCCESS` within one connect, which never happened).
- **Head unit brought up before the phone** each run (§7a): launch `MainActivity` with the phone's
  BT off, let the group settle ~14 s, then enable the phone's BT.
- Phone was **clean at the start of R1** — round 2 ended with nothing stored and nothing has run
  this thread since, so the brief's §3 Step 0 was satisfied without a manual forget. Confirmed by
  the R1-first-connect landmark (below). For R2 and R4-hotspot the operator did forget the head
  unit; R3 was run against the endpoint the phone stored during R2.

### Hotspot arm: how the SoftAP was brought up, and the deviation it forced

The brief wants the AP **persisted** on 5 GHz. This rig **refuses `setSoftApConfiguration()`**
(§7a), so a persisted 5 GHz config cannot be written. Instead:

- `adb shell cmd wifi force-softap-band enabled 5` then
  `adb shell cmd wifi start-softap Navegadortz2 wpa2 12345678 -b 5` — a **transient** AP, on the
  same SSID/passphrase as the rig's persisted config. adb shell is privileged so this works; the
  app is not and cannot (proven below). `SoftApInfo` came up **`frequency= 5765`** then `5785` on a
  restart, `bssid= 00:27:15:43:06:6a` (stable across restarts), interface `wlan2`, IP
  `192.168.156.146/24`. Both frequencies are ≥ 5180, so the brief's "sub-5180 = the band, not the
  branch" caveat does not apply — any R2/R3 video result is real.
- A transient AP is fine for R2→R3 here: there is no reboot between them, and the SSID **and BSSID
  are fixed** because I keep the one AP up and never restart it mid-arm — which is the exact
  property `894093fc` assumes the hotspot transport has.

**The deviation.** `auto-enable-hotspot` was `true` on the rig. On the first R3 attempt,
`headunit://exit` made `AapService.onDestroy` log `AapService: Auto-disabling hotspot...` →
`HotspotManager: Setting hotspot enabled=false` → `stopSoftAp` — **the app killed my SoftAP on
exit**. On relaunch it tried to bring it back and could not:

```
HotspotManager: Band preference is 5 GHz only, set by the user; trying 5 GHz.
HotspotManager: This device would not take a band request, so the access point is on whatever band it already had configured, which this app cannot read.
HotspotManager: Every start path was tried on 5 GHz and no access point came up within 6s each. On a non-privileged install this usually cannot be done from an app — switch the hotspot on in system settings instead.
```

So on this rig the app **can disable but not enable** the SoftAP. I set `auto-enable-hotspot=false`
and restarted the AP via adb so the R3 reconnect had a network to test against. That first R3
attempt is kept as `round-wpp-over-tcp/r3-r3-void-appkilledAP*.txt` and is not scored; the scored
R3 (`r3-r3b*.txt`) ran with `auto-enable-hotspot=false` and the AP held up throughout.

### Rig-state facts confirmed before the round (read-only)

- Phone ↔ head unit Bluetooth bond **intact** — POCO's bonded list includes `Navegadortz2`
  (`…33:59`), `mActiveDevice`, A2DP + HFP active.
- Head unit's **persisted** SoftAP config (`/data/misc/apexdata/com.android.wifi/WifiConfigStoreSoftAp.xml`,
  readable with root): SSID `Navegadortz2`, passphrase `12345678`, WPA2, `BandChannelMap` band `3`
  (2.4 + 5 GHz), channel `0` (ACS). The transient AP started for the hotspot arm reused this
  SSID/passphrase.
- Head unit 5 GHz channels the driver reports: `36,40,44,48,149,153,157,161,165` (country CO).
- Phone carried stale saved WiFi networks `Navegadortz2` (nid 2) and `OHU-TEST` (nid 3) from old
  rounds — **forgotten with `cmd wifi forget-network` before the hotspot arm** (a saved
  `Navegadortz2` with an unknown passphrase would collide with the head unit's SoftAP SSID).

## R0 — build gate

**PASS**

`build_hur.sh` then `run_unit_tests.sh` at `968573ab`, coding host. `BUILD SUCCESSFUL`,
`compileGithubDebugKotlin` clean. From the JUnit XML (99 classes):

- `WppEndpointPolicyTest` — **5** tests, 0 failures (new) ✓
- `SingleKeyKeyManagerTest` — **4** tests, 0 failures (new) ✓
- `WppMessagesTest` — **11** tests, 0 failures (was 10) ✓
- whole suite — **981** tests, **0** failures, **0** skipped ✓

Matches the brief's prediction exactly (5 / 4 / 11, 981/0). `SingleKeyKeyManagerTest` read its
`res/raw` material fine on this host — no file-not-found.

## R1 — WiFi Direct can no longer poison the phone (the point of the round)

**PASS**

- Settings written: `wifi-connection-mode=3`, `native-ap-transport=0`,
  `native-wifi-version-exchange=true`, `wifi-direct-band=1`, `log-level=2`
- Radio state: phone BT off during head-unit bring-up, then `svc bluetooth enable`; phone WiFi on
  throughout; head-unit BT on before launch
- Discard-rule check: **clean** — `createGroup SUCCESS` = 2 (one per connect, separate service
  instances), `SSL handshake complete` = 2 (distinct session ids), `MATCH! Starting AapService` = 2
  (the reconnect lever, one per connect), `Magic Garbage` = 0, `recoverNativeGroup` = 0, interfaces
  `p2p-wlan0-0` (connect 1) and `p2p-wlan0-1` (connect 2) — one fresh group per connect, no
  mid-connect churn

### Connect 1 — first connect

```
01:48:47.215  NativeAA: ACTIVELY LISTENING on Android Auto UUID (4de17a00-…) on radio [Navegadortz2]
01:48:47.371  WppTcpServer: listening for Android Auto on TCP 5299
01:48:48.001  WifiDirectManager: 5GHz createGroup SUCCESS!
01:48:48.234  NativeAA: Credentials updated. SSID=DIRECT-PK-Navegadortz2, IP=192.168.49.1, BSSID=d2:a5:8c:fa:95:0d
              (phone BT enabled here)
01:49:08.827  AutoStartReceiver: MATCH! Starting AapService via Bluetooth Auto-start...
01:49:09.217  NativeAA: Connection accepted from POCO X3 NFC (DC:B7:2E:5E:4E:59) on local radio [Navegadortz2]
01:49:09.543  NativeAA: not advertising WPP over TCP: a WiFi Direct group is renamed every time it is
              created, and the phone would keep dialling the one it stored
01:49:14.228  WirelessServer: Incoming connection detected from /192.168.49.55
01:49:14.461  SSL handshake complete. Session id: kJ7zWgfW7rBzJ3TLP2okjlUHwQp4dq7uAdzUAEpIQtA=
01:49:16.554  First frame rendered (hardware decode)
```

Phone side: `GH.WIRELESS.SETUP: No WPP on TCP configuration found in storage for the head unit, will
not start WPP on TCP.` (76 times across the capture; **`Trying to start WPP on TCP with
configuration` = 0**), then `GH.WirelessFSM: Launch projection 192.168.49.1 5288`.

### Connect 2 — reconnect (the half that refutes round 2's R2)

`headunit://exit` (01:51:15), `am force-stop`, relaunch `MainActivity` (01:51:17) — **phone not
touched**.

```
01:51:18.678  WifiDirectManager: 5GHz createGroup SUCCESS!
01:51:22.902  AutoStartReceiver: MATCH! Starting AapService via Bluetooth Auto-start...
01:51:22.975  NativeAA: Connection accepted from POCO X3 NFC (DC:B7:2E:5E:4E:59) on local radio [Navegadortz2]
01:51:23.297  NativeAA: not advertising WPP over TCP: a WiFi Direct group is renamed every time it is created…
01:51:26.531  WirelessServer: Incoming connection detected from /192.168.49.20
01:51:26.761  SSL handshake complete. Session id: A2OCsdOgaNsbt8vWz9neIHBGwIRN3ZjWPO7EY00uCVA=
```

The phone re-associated and formed a fresh session on its own, without any manual re-trigger.
Round 2 ran this identical sequence on `8ff9510d` and got **no session at all** because the phone
looped on the stored (now-stale) endpoint. Here there is no stored endpoint, so the phone falls
straight back to the RFCOMM handshake.

### Numbers the brief asked for

| | value |
|---|---|
| sessions formed across connect + reconnect | **2 / 2** |
| `advertising WPP over TCP` lines | **0** |
| `not advertising … a WiFi Direct group is renamed` lines | **2** |
| `WppTcpServer: connection from` | **0** |
| phone `Trying to start WPP on TCP with configuration` | **0** |
| connect 1: `createGroup SUCCESS` → `Incoming connection detected` | 26.2 s wall (phone deliberately unreachable for the first ~12 s); `Connection accepted` → `Incoming` = **5.01 s** |
| connect 2: `createGroup SUCCESS` → `Incoming connection detected` | **7.85 s**; `Connection accepted` → `Incoming` = **3.56 s** |

Round 2 comparison (createGroup → Incoming, phone reachable throughout): baseline 5.26 s, candidate
`8ff9510d` 5.50 s. Connect 2's 7.85 s is the comparable figure — same ballpark, ~2 s slower, and the
run's point is that a session forms on the reconnect at all.

Projection: 36 throughput windows, **`dropped=0` in every one**, `c2.unisoc.hevc.decoder`,
`decodeLatency` 8–11 ms. fps 17–51, mostly 29–30 on connect 1 and 46 on connect 2 — the low band is
the phone's parked-nav adaptive rate (a known rig characteristic), not a decoder issue; no
`rendered=0` window.

## R2 — hotspot, first connect: does the TLS handshake complete now

**PARTIAL** (the brief's middle verdict — dial arrives, handshake fails, but now with a class and
cause)

- Settings written: `native-ap-transport=1`, `native-wifi-version-exchange=true`,
  `hotspot-ssid=Navegadortz2`, `hotspot-password=12345678`, `static-bssid=00:27:15:43:06:6a`,
  `hotspot-interface=wlan2`, `auto-enable-hotspot=false`, `log-level=2`
- Radio state: transient SoftAP up (`cmd wifi start-softap … -b 5`), **`SoftApInfo` frequency
  5765 MHz**, BSSID `00:27:15:43:06:6a`; phone BT off during bring-up then `svc bluetooth enable`;
  phone's stale saved `Navegadortz2` / `OHU-TEST` forgotten first
- Discard-rule check: **clean** — no P2P group (hotspot transport), `createGroup SUCCESS` = 0,
  one `SSL handshake complete` (the AAP session), no `Magic Garbage`

```
02:03:08.518  SoftApCredentials: Using 'wlan2' (192.168.156.146) as named in settings.
02:03:08.540  SoftApCredentials: SUCCESS - Providing credentials from wlan2: SSID=Navegadortz2, IP=192.168.156.146, BSSID=00:27:15:43:06:6A
02:03:08.671  WppTcpServer: listening for Android Auto on TCP 5299
02:03:28.006  NativeAA: Connection accepted from POCO X3 NFC (DC:B7:2E:5E:4E:59) on local radio [Navegadortz2]
02:03:28.336  NativeAA: advertising WPP over TCP at 192.168.156.146:5299       ← advertised on the hotspot transport
02:03:30.293  WirelessServer: Incoming connection detected from /192.168.156.183   ← AAP session via RFCOMM
02:03:30.521  SSL handshake complete. Session id: N9YleCQiHRceBP75YADZ6Y7NuOQ0CjlbrXLQcGdMzTo=
02:03:30.636  WppTcpServer: connection from 192.168.156.183                    ← phone also dials 5299
02:03:32.252  First frame rendered (hardware decode)
02:03:40.735  E/ WppTcpServer: session error: SSLHandshakeException: connection closed <- EOFException: connection closed
02:03:40.736  WppTcpServer: session closed
```

Stack after the error line (verbatim, the second commit's whole point):
```
javax.net.ssl.SSLHandshakeException: connection closed
    at com.android.org.conscrypt.SSLUtils.toSSLHandshakeException(SSLUtils.java:356)
    at com.android.org.conscrypt.ConscryptEngineSocket.doHandshake(ConscryptEngineSocket.java:239)
    at com.android.org.conscrypt.ConscryptEngineSocket.startHandshake(ConscryptEngineSocket.java:218)
    at …WppTcpServer$handleConnection$2.invokeSuspend(WppTcpServer.kt:161)
    …
Caused by: java.io.EOFException: connection closed
    ... 19 more
```

Phone side:
```
02:03:27.778  GH.WIRELESS.SETUP: No WPP on TCP configuration found in storage for the head unit   ← clean start (forget worked)
02:03:30.504  GH.WPP.TCP: Trying to start WPP on TCP with configuration: WifiProjectionProtocolOnTcpConfiguration(
                wifiConfiguration=WifiConfiguration(ssid=Navegadortz2, bssid=00:27:15:43:06:6A,
                securityMode=WPA2_PERSONAL, mostRecentlyUsedFrequenciesMhz=[]),
                ipAddress=192.168.156.146, port=5299)                          ← phone STORES the hotspot endpoint
02:03:30.518  GH.WPP.TCP: WPP on TCP connected to the WiFi network             ← the network exists and is joinable
02:03:40.611  E/ GH.WPP.TCP: WPP on TCP connection failed to read             ← 10 s later, TLS never completed
02:03:40.614  GH.WPP.TCP: Stopping WPP on TCP
```

| | value |
|---|---|
| `advertising WPP over TCP at …:5299` | **1** |
| `WppTcpServer: connection from` | **1** |
| `WppTcpServer: TLS handshake complete with` | **0** |
| `WppTcpServer: session error:` chain | `SSLHandshakeException: connection closed <- EOFException: connection closed` (+ stack) |
| `WppTcpServer: [TX]` / `[RX]` WPP messages | **0** (handshake never got past TLS) |
| AAP session over the hotspot (via RFCOMM) | **formed** — 33 windows, `dropped=0` every one, 50 fps |
| `SoftApInfo` frequency | **5765 MHz** — ≥ 5180, so any video result is real, not the band |

**Why PARTIAL and not FAIL:** the logging change landed (round 2 got bare
`session error: connection closed`; now there is an exception class, a cause chain, and a stack
into `ConscryptEngineSocket.doHandshake` — the server is now *entering* the handshake, where round 2
chose no alias at all). **Why PARTIAL and not PASS:** TLS still never completes. The failure is a
10-second stall then EOF — the phone drops the socket with no TLS alert, consistent with the "no CN
/ no SAN → client hostname verification fails" limit the brief flagged, though the phone never says
so in as many words. Confirmed against `968573ab` — this replaces round 2's unmeasured status.

## R3 — hotspot, reconnect: does it start with no Bluetooth handshake

**FAIL** — and it is round 2's brick, on the hotspot transport

Ran despite R2 not producing a completed TLS handshake, because **the phone stored the endpoint**
(R2 phone log above) and that stored endpoint is exactly the hazard R3 exists to measure. Scored
run is `r3-r3b*.txt` (`auto-enable-hotspot=false`, AP kept up across the exit — the first attempt,
`r3-r3-void-appkilledAP*.txt`, is void because the app killed the AP on exit; see the deviation
note).

Reconnect: `headunit://exit`, `am force-stop`, relaunch `MainActivity`, **phone not touched**. AP
held at 5785 MHz, same BSSID, throughout.

```
02:12:06.262  SoftApCredentials: SUCCESS - Providing credentials from wlan2: … BSSID=00:27:15:43:06:6A
02:12:10.749  WppTcpServer: connection from 192.168.156.183        ┐
02:12:20.777  E/ WppTcpServer: session error: SSLHandshakeException … <- EOFException: connection closed   │ repeats
02:12:41.682  WppTcpServer: connection from 192.168.156.183        │ every
02:12:51.723  E/ WppTcpServer: session error: … EOFException       │ ~31 s
02:13:12.971  WppTcpServer: connection from 192.168.156.183        │
   …                                                               ┘  (6 dials, 5 errors, still looping at capture end 02:14:45)
```

Phone side, the same loop:
```
02:12:41.556  GH.WIRELESS.SETUP: State changed to CONNECTING_RFCOMM
02:12:41.557  GH.WPP.TCP: Trying to start WPP on TCP with configuration: …ssid=Navegadortz2, bssid=00:27:15:43:06:6A … port=5299
02:12:41.569  GH.WPP.TCP: WPP on TCP connected to the WiFi network
02:12:51.608  E/ GH.WPP.TCP: WPP on TCP connection failed to read
   …  (repeats: 6× "Trying to start WPP on TCP", 5× "failed to read", 0 successful projection)
```

| | value |
|---|---|
| `NativeAA: Connection accepted from POCO` (RFCOMM handshake completed) | **0** |
| `WirelessServer: Incoming connection detected` | **0** |
| `AapSslContext … SSL handshake complete` (AAP session) | **0** |
| `First frame rendered` | **0** |
| `WppTcpServer: connection from` / `session error` | **6 / 5**, looping every ~31 s |
| phone `Trying to start WPP on TCP` / `failed to read` | **6 / 5** |
| **projection session on the reconnect** | **none** |

**Mechanism.** Gearhead stores the WPP-over-TCP endpoint, and on every reconnect it starts
`CONNECTING_RFCOMM` but immediately abandons it for the stored TCP config. The network *is* there
(hotspot SSID/BSSID stable — `894093fc`'s assumption holds), so it joins fine — but our TLS server
never completes, so it times out after 10 s, waits ~20 s, and tries again. Forever. It never falls
back to a plain RFCOMM session. This is round 2's R2 verbatim; the only difference is round 2's
phone got `NETWORK_NOT_FOUND` (the WiFi Direct group had vanished) while here it gets "network
found, TLS dead." **Same outcome: no session on the reconnect, recovered only by forgetting the
head unit on the phone** (the operator did so before R4).

**What this says about `894093fc`.** Making the endpoint hotspot-only removes the *stale-network*
half of round 2's brick but not the brick itself. As long as `WppTcpServer`'s TLS handshake does
not actually complete, advertising the endpoint at all — on any transport — poisons the phone's
stored state and breaks the reconnect. The TLS server has to work end to end before this is
shippable, exactly as round 2's write-up concluded.

## R4 — kill switch, both transports

**PASS** (both arms)

### R4 WiFi Direct arm

- Settings: `native-wifi-version-exchange=false`, `native-ap-transport=0`
- Discard check: **clean** — `createGroup SUCCESS` = 1, `SSL handshake complete` = 1, single
  interface `p2p-wlan0-2`, no `recoverNativeGroup`, no `Magic Garbage`

```
01:52:58.512  WppTcpServer: listening for Android Auto on TCP 5299        ← server still binds (unconditional)
01:52:59.112  WifiDirectManager: 5GHz createGroup SUCCESS!
01:53:14.992  NativeAA: Connection accepted from POCO X3 NFC …
01:53:18.456  WirelessServer: Incoming connection detected from /192.168.49.236
01:53:18.673  SSL handshake complete. Session id: yaYpwCnRsiO5AEWHZrAZ/NMCL7gvDahimuT+frgTJlo=
```

`advertising WPP over TCP` = 0, `WppTcpServer: connection from` = 0, `Sending WifiVersionRequest` =
0, `not advertising WPP over TCP` = 0 (that log path is unreached with the flag off), phone
`No WPP on TCP configuration found`. Session formed. 15 windows, `dropped=0`, 49–50 fps.

### R4 hotspot arm

- Settings: `native-wifi-version-exchange=false`, `native-ap-transport=1` (hotspot keys from R2
  still in place), `auto-enable-hotspot=false`, AP up at 5785 MHz
- Radio state: operator forgot the head unit on the phone first (R3 had bricked it)

```
02:18:06.157  WppTcpServer: listening for Android Auto on TCP 5299
02:18:23.746  GH.WIRELESS.SETUP: No WPP on TCP configuration found in storage for the head unit, will not start WPP on TCP.
02:18:23.973  NativeAA: Connection accepted from POCO X3 NFC …
02:18:24.832  GH.WirelessFSM: Launch projection 192.168.156.146 5288        ← normal projection over the hotspot
02:18:25.287  SSL handshake complete. Session id: OWc2v80XNcnez4POwBgC+1f2QKYzgqL/3nH7XStGfFc=
02:18:29.4    (BT ACL drop, HCI_ERR_PEER_USER — first session torn down)
02:18:47.255  NativeAA: Connection accepted from POCO X3 NFC …               ← second attempt
02:18:49.297  SSL handshake complete. Session id: qVf8pAkvn6nSJ8g3sx1Ksf+HsHkZCVyE+hbq22S2Hig=
02:18:50.800  First frame rendered (hardware decode)
```

| | value |
|---|---|
| `advertising WPP over TCP` | **0** |
| `WppTcpServer: connection from` | **0** |
| `Sending WifiVersionRequest` | **0** |
| session | **formed** — 14 windows to capture end (02:20:03), `dropped=0` every one, 46 fps |

The session dropped once (a Bluetooth ACL timeout ~4 s after the first `SSL handshake complete`) and
re-formed ~20 s later, then held stable for 74 s. The drop is most likely the phone still settling
after the R3 stuck-association and the forget; it is not a WPP/branch signal (`WppTcpServer` was
silent throughout). Recorded, not scored against the verdict — the PASS conditions
(`advertising` = 0, `connection from` = 0, a session forms) were all met.

## Report-back answers (brief §7)

1. **R1 — sessions across connect + reconnect: 2. `advertising WPP over TCP` lines: 0.** The answer
   that says the default transport is safe. Phone stored nothing (`No WPP on TCP configuration
   found` throughout, `Trying to start WPP on TCP` = 0), and the withheld-endpoint line was the
   "WiFi Direct group is renamed" form both times.
2. **R2 — `TLS handshake complete with` count: 0.** The full `session error:` chain instead:
   `SSLHandshakeException: connection closed <- EOFException: connection closed`, with a stack into
   `com.android.org.conscrypt.ConscryptEngineSocket.doHandshake` / `WppTcpServer.kt:161`. Not a
   named certificate error — a 10-second stall then a bare EOF, phone-side `WPP on TCP connection
   failed to read`. The server now enters the handshake (round 2 did not), but the phone still
   never completes it.
3. **R3 — a session formed with `NativeAA: Connection accepted from` = 0? No.** `Connection accepted
   from` = 0 *and* `Incoming connection detected` = 0 *and* `SSL handshake complete` = 0 — **no
   session at all**. The phone loops on the stored hotspot endpoint (6 dials / 5 TLS failures in
   2.5 min, still going at capture end) and never falls back to RFCOMM. Recovered only by forgetting
   the head unit on the phone. This is round 2's brick, reproduced on the hotspot transport.

**Shipping question:** not shippable as-is. `894093fc` protects the default WiFi Direct transport
(R1 PASS), but the hotspot transport is still a brick on reconnect (R3 FAIL) for as long as
`WppTcpServer`'s TLS handshake does not complete (R2). The kill switch works (R4), so
`native-wifi-version-exchange` staying **off by default** is correct and sufficient to ship the rest
of the branch — but the feature itself needs the TLS server fixed and re-tested before it can be
turned on.

Setup-note facts the brief asked for: hotspot **SSID `Navegadortz2`**, **BSSID `00:27:15:43:06:6a`**,
**`SoftApInfo` frequency 5765 MHz (R2) / 5785 MHz (R3, R4)** — all ≥ 5180. The phone was
successfully cleared before R2 (`No WPP on TCP configuration found`) and again before R4's hotspot
half; R3 deliberately kept the endpoint the phone stored during R2.

## Anything the brief did not ask about

- **The candidate's WiFi-Direct behaviour is now exactly what round 2's write-up asked for.** Round
  2's "Anything the brief did not ask about" listed three candidate directions; `894093fc` took the
  "don't advertise the WiFi specifics on a transport whose group identity is unstable" one, and on
  this rig it does what it says — the endpoint is simply never offered on WiFi Direct, the phone
  never stores anything, and the reconnect that round 2 bricked now just works.
- **`WppTcpServer` still binds 5299 unconditionally**, on every transport and with the flag off
  (`WppTcpServer: listening for Android Auto on TCP 5299` in all three R1/R4 captures). Harmless —
  nothing dials it on the WiFi Direct arm — but worth knowing the listener is always up.
- **The reconnect no longer needs a manual re-trigger on this rig.** Round 2's R3b reconnect was
  INCONCLUSIVE because the phone's Gearhead session had accumulated an hour of RFCOMM throttling
  from the connect/brick cycling. This round's phone was fresh (Gearhead session `d4a21adf`,
  `THROTTLE_LIMIT_EXCEEDED` present but not blocking), and the reconnect formed a session in 7.85 s
  with no intervention. The throttle in round 2 was test wear, not a code signal — confirmed.
- **Head-unit BT name and SoftAP SSID are both `Navegadortz2`.** Not a problem in practice — the
  phone's `WifiConfiguration` in the stored endpoint carries the SSID + BSSID and matched the AP
  fine — but noted for anyone reading the R2/R3 captures.
- **On this rig the app can *disable* the SoftAP but not *enable* it.** `AapService.onDestroy` with
  `auto-enable-hotspot=true` successfully ran `stopSoftAp` (`canWriteSettings=true`), but
  `HotspotManager.setHotspotEnabled(true)` on the next launch failed every start path
  (`no access point came up within 6s each … cannot be done from an app`). So `auto-enable-hotspot`
  on this rig is a way to *lose* a working hotspot across a session exit with no way to get it back
  without adb or the system settings toggle. Worth a guard: do not auto-disable a hotspot the app
  did not itself enable.
- **The hotspot `SoftApCredentials` path worked cleanly** once the manual keys were set — `Using
  'wlan2' … as named in settings` → `SUCCESS - Providing credentials`, no `HOTSPOT_CONFIG_UNREADABLE`
  banner, no interface-guessing. The `hotspot-interface=wlan2` override is what made it
  deterministic; without it the provider would have had to guess between `wlan2` and `seth_lte0`.
- **`WppTcpServer` binds 5299 unconditionally on the hotspot arm too** (seen with the flag off in
  R4). On the hotspot transport this listener *is* reachable from the phone (unlike WiFi Direct
  where nothing dials it), so a dead 5299 there is a live loop target, not a harmless idle socket —
  which is the R3 mechanism.
