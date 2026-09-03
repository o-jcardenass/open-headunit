# wpp-over-tcp — round 4 results

**Candidate:** `fork/fix/wpp-over-tcp` @ `13a43a6d` (four commits on `2f07eeec`, no rewrite) &nbsp;&nbsp; **Baseline:** none (every run is a settings change on the candidate)
**APK md5:** candidate `6fbff645b46e0bcf4616310950a21518` (`3.3.0-beta4`, `assembleGithubDebug`)
**Unit:** UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14, single BT radio
**Phone:** POCO X3 NFC (`M2007J20CG`), Android 15, Gearhead `17.5.663204-release`
**Date:** 2026-09-01

## Verdict in one line

**R0 PASS, R1 PARTIAL, R2 PASS, R3 connect PASS / reconnect FAIL (contaminated), R4 not run.**
The TLS-client fix works: **`WppTcpServer` completes a mutual TLS handshake on every dial**
(`TLSv1.2, TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256`), which is what rounds 2 and 3 could not do. A
projection session forms and runs stably at 50 fps on the hotspot transport, and **R2 is the
feature working for the first time**: the hotspot reconnect forms a full session with
`NativeAA: Connection accepted from` = **0** — no Bluetooth handshake at all — in ~5–7 s, twice,
with no loop and no brick. Round 3's R3 infinite-loop brick is gone.

What is **not** finished is one state-machine transition. On the hotspot the phone is already on the
network, so it never sends `WifiInfoRequest` (Type 2); it sends `7` then `6` and stops.
`WppHandshakeSession` sits in `AWAIT_INFO_REQUEST` waiting for a Type 2 that will never come, so
**`WppTcpServer: handshake complete; projection session is up` is never logged** and the phone
prints a spurious `WPP on TCP connection failed to read` ~10 s later. Neither is fatal — the
projection session is already up on the same socket via the normal AAP handshake — but the WPP
session machine never reaches `DONE` on this transport. That is the PARTIAL.

## Setup notes

### Rig / environment

- Transfer branch fetched and checked out at `76d91018`. `TESTING-TEMPLATE.md`, this thread's
  `wpp-over-tcp-round4-brief.md` and `wpp-over-tcp-round3-results.md` (cited by the brief) read in
  full. No other threads' files read.
- Candidate SHA verified: `git rev-parse fork/fix/wpp-over-tcp` → `13a43a6d1059c107a26439569f75957781d5cb1d`,
  four commits on `2f07eeec` (`8ff9510d`, `894093fc`, `968573ab`, `13a43a6d`), no rewrite.
- **R0 on the coding host** (`hur-wifi-test-scripts/run_unit_tests.sh`, then `build_hur.sh`, JDK
  `/opt/android-studio/jbr`). Both clean.
- Scripts used: `run_unit_tests.sh` (R0), `build_hur.sh` (R0 APK), `set_hu_prefs.sh` (all settings
  writes). Nothing added or changed. Captures/logs left in the sibling
  `hur-wifi-test-scripts/round-wpp-over-tcp-r4/` (not on this branch), same as round 3.
- Candidate installed with `adb install -r`; `settings.xml` preserved and restored **byte-identical**
  to the pre-round backup at the end (`diff` clean, re-chowned). Live md5 `6fbff645…` confirmed
  before the runs.

### `settings.xml` delta

Pre-round backup taken app-stopped. Baseline already carried `wifi-connection-mode=3`,
`hotspot-band=1`, `wifi-direct-band=1`, `log-level=2` (INFO — the brief says INFO is enough and it
was), `view-mode=2` (GLES), `head-unit-make`/`vehicle-make=Google`, and
`auto-start-bt-name=POCO X3 NFC` / `auto-start-bt-macs=DC:B7:2E:5E:4E:59`. Keys changed during the
round:

| key | values used | note |
|---|---|---|
| `native-wifi-version-exchange` | `false` → `true` (R1–R3) | the arm switch |
| `native-ap-transport` | `0` → `1` (R1, R2) → `0` (R3) | hotspot vs WiFi Direct |
| `auto-enable-hotspot` | `true` → `false` (R1–R3) | the brief's workaround; **not a deviation** (brief §2) |
| `hotspot-ssid` | `""` → `Navegadortz2` (R1, R2) → `""` (R3) | required (§7a: rig can't read own SoftAP config) |
| `hotspot-password` | `""` → `12345678` (R1, R2) → `""` (R3) | " |
| `static-bssid` | `0` → `00:27:15:43:06:6a` (R1, R2) → `0` (R3) | the SoftAP's real BSSID |
| `hotspot-interface` | `""` → `wlan2` (R1, R2) → `""` (R3) | the AP interface (§7a) |

Final state: **restored byte-identical to the backup.** SoftAP stopped, `force-softap-band` reset
to `disabled`, P2P group not formed. Candidate APK left installed. No stray capture processes
(`ps aux | grep logcat` → 0).

### Clean-run protocol deviations (all per §7a, this rig)

- **Airplane mode cannot be driven from adb on the POCO** (§7a). Link-state lever was the phone's
  own Bluetooth adapter: `svc bluetooth disable` before bring-up, `svc bluetooth enable` after the
  head unit had settled ~15 s.
- **`MATCH! Starting AapService` is not contamination here** — it is the phone's own Bluetooth
  reconnect firing `AutoStartReceiver`, the documented Native-AA (re)connect mechanism on this rig.
  The discard rule is a *second* `createGroup SUCCESS` in one connect (§7a); that never happened on
  a scored run. On the hotspot transport `createGroup SUCCESS` = 0 throughout (no P2P group).
- **Head unit brought up before the phone** each run (§7a).

### Hotspot arm: how the SoftAP was brought up

Same as round 3. Rig **refuses `setSoftApConfiguration()`**, so a persisted 5 GHz config cannot be
written; a **transient** AP was used on the same SSID/passphrase as the rig's persisted config:

```
adb shell cmd wifi force-softap-band enabled 5
adb shell cmd wifi start-softap Navegadortz2 wpa2 12345678 -b 5
```

`SoftApInfo`: **frequency 5745 MHz** (≥ 5180, so any video result is real, not the band),
BSSID `00:27:15:43:06:6a`, interface `wlan2`, IP `192.168.156.146/24`. Held up unchanged across
R1 and R2 (`auto-enable-hotspot=false`, so the app did not tear it down on exit — the round-3
defect that R4 addresses).

### Phone state at the start

- Phone ↔ head unit Bluetooth **bond intact** (`Navegadortz2` / `…33:59` in the POCO's bonded
  list).
- Phone had **no stored WPP-on-TCP configuration** at the start of R1: first-connect landmark
  `GH.WIRELESS.SETUP: No WPP on TCP configuration found in storage for the head unit` (brief §3
  Step 0 satisfied without a manual forget — round 3 ended clean).
- Phone had **no stale saved WiFi networks** for `Navegadortz2` / `OHU-TEST` (`cmd wifi
  list-networks` clean — round 3 forgot them and they stayed forgotten).
- The head unit's persistent `CarInfoInternal` on the phone shows `wifiProjectionProtocolOnTcp=false`
  at the start of **every** run this round — the WPP-TCP endpoint is **never persisted to the
  phone's car database.** What the phone does carry is an in-memory WPP-TCP config inside the
  running Gearhead process (see R3).

## R0 — build gate

**PASS**

`run_unit_tests.sh` then `build_hur.sh` at `13a43a6d`, coding host. `BUILD SUCCESSFUL`,
`compileGithubDebugKotlin` clean. From the JUnit XML:

- `WppTcpTlsTest` — **1** test, 0 failures (new this round) ✓
- `WppEndpointPolicyTest` — **5** tests, 0 failures ✓
- `SingleKeyKeyManagerTest` — **4** tests, 0 failures ✓ (read `res/raw/cert` / `privkey` fine)
- `WppMessagesTest` — **11** tests, 0 failures ✓
- whole suite — **982** tests, **0** failures, **0** errors, **0** skipped ✓

Matches the brief's prediction exactly (1 / 5 / 4 / 11, 982/0).

## R1 — hotspot, first connect: does the TLS handshake complete now

**PARTIAL** — TLS completes; the WPP message exchange after it does not reach
`handshake complete; projection session is up`. Run twice (`r4-r1-*` and `r4-r1b-*`); the second
was the cleaner single-connect capture and is quoted.

- Settings written: `native-ap-transport=1`, `native-wifi-version-exchange=true`,
  `hotspot-ssid=Navegadortz2`, `hotspot-password=12345678`, `static-bssid=00:27:15:43:06:6a`,
  `hotspot-interface=wlan2`, `auto-enable-hotspot=false`, `log-level=2`
- Radio state: transient SoftAP up, `SoftApInfo` **5745 MHz**, BSSID `00:27:15:43:06:6a`; phone BT
  off during bring-up, then `svc bluetooth enable`
- Discard-rule check: **clean** — `createGroup SUCCESS` = 0, `Magic Garbage` = 0, one
  `MATCH! Starting AapService` (r1b), one AAP `SSL handshake complete`

### r1b timeline (HU), verbatim

```
03:08:03.115  WppTcpServer: listening for Android Auto on TCP 5299
03:08:19.538  WppTcpServer: connection from 192.168.156.183
03:08:19.624  WppTcpServer: TLS handshake complete with 192.168.156.183 (TLSv1.2, TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256)
03:08:19.636  WppTcpServer: [TX] WifiVersionRequest (Type 4) v4.2
03:08:19.731  WppTcpServer: [RX] Type 5 (135 bytes)                 (WifiVersionResponse)
03:08:19.740  WppTcpServer: [TX] WifiStartRequest (Type 1) -> 192.168.156.146:5288
03:08:19.770  WppTcpServer: [RX] Type 7 (2 bytes)                   (WifiStartResponse, status 0)
03:08:20.280  WppTcpServer: [RX] Type 6 (2 bytes)                   (WifiConnectStatus)  <-- exchange stops here
03:08:20.299  WirelessServer: Incoming connection detected from /192.168.156.183
03:08:20.343  NativeAA: session is up — cancelling the poke retry loop
03:08:20.442  AapTransport.startHandshake ...
03:08:20.493  SSL handshake complete. Session id: Os+lvQbosHx3PvBEszf4/TkKhIMSkBysi+fchywOfv0=
03:08:20.495  Handshake: Status OK sent: 8
03:08:2x       First frame rendered (hardware decode)
```

**Last stage the log shows:** `WppTcpServer` sits after `[RX] Type 6`. It never logs
`handshake complete; projection session is up` and never logs `handshake failed`. Stage-transition
lines are `AppLog.d` and were below the INFO log level, but the source path is unambiguous:
`WppHandshakeSession` is in `AWAIT_INFO_REQUEST` (entered on `[TX] Type 1`); `onAwaitInfoRequest`
has no handler for `CONNECT_STATUS` (type 6) or for `WppEvent.TcpSessionUp`, so both are dropped
(`else -> emptyList()`). In `WppTcpServer`'s loop the `if (callbacks.projectionSessionUp())`
branch `continue`s **before** the `StageTimeout` check is reached, so the 15 s
`INFO_REQUEST_TIMEOUT_MS` never fires either. The coroutine spins harmlessly feeding `TcpSessionUp`
until the session is torn down.

### Counts

| | r1 | r1b |
|---|---|---|
| `WppTcpServer: TLS handshake complete with` | **2** (`TLSv1.2, TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256`) | **1** (same) |
| `WppTcpServer: connection from` | 2 | 1 |
| `WppTcpServer: session error` | **0** | **0** |
| `WppTcpServer: [TX] WifiInfoResponse (Type 3)` | 0 | 0 (phone never sent Type 2, so no Type 3) |
| `WppTcpServer: handshake complete; projection session is up` | **0** | **0** |
| `WirelessServer: Incoming connection detected` | 3 | 1 |
| AAP `SSL handshake complete` | 2 | 1 |
| `createGroup SUCCESS` / `Magic Garbage` | 0 / 0 | 0 / 0 |
| `First frame rendered` | 1 | 1 |
| projection after first frame | **stable 50–51 fps, `dropped=0` every window, 2+ min, `c2.unisoc.hevc.decoder`, decodeLatency 9 ms** | same, 50 fps |

### Phone side (r1a, the fuller capture)

The phone connected over RFCOMM first and ran the **Bluetooth** handshake to completion
(`NativeAA: [RX] Received Type 2` → `[TX] WifiInfoResponse (Type 3)` → `[RX] Type 7`). The HU then
closed the RFCOMM listeners on the 3 s grace; the phone, mid-flow, logged
`RFCOMM_READ_WRITE_FAILURE` and **restarted as `Starting manager of type: TCP`**, dialled 5299, and
completed TLS. Over TCP it then did `WifiStartResponse, protocol=TCP, status=STATUS_SUCCESS` and
`Send WifiConnectStatus, status=STATUS_SUCCESS`, went to `PROJECTION_INITIATED`, and got its GAL
socket (`Received GAL socket result … result=Completed`). Projection came up. ~10 s later:

```
E/GH.WPP.TCP: WPP on TCP connection failed to read
   (java.net.SocketTimeoutException: Read timed out — the phone's own WPP first-message
    12 s timeout on a channel the HU is no longer driving; projection already running)
```

`WPP on TCP connection failed to read` **did appear**, which the brief said should not appear in a
passing R1. In round 3 that line meant no session at all (10 s stall → EOF, TLS never completed).
Here TLS completes, a projection session forms and is stable, and the line is a benign timeout on
the setup channel after the fact. Different failure, much smaller.

### R1 answer

- `TLS handshake complete with` = **2 / 1** across the two runs. Protocol/cipher:
  **`TLSv1.2, TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256`**. A client certificate was presented and
  accepted — this is the result rounds 2 and 3 were trying to reach.
- The WPP message exchange does **not** reach `handshake complete; projection session is up`
  because `WppHandshakeSession.onAwaitInfoRequest` does not handle the phone skipping `Type 2`
  (which it always does on the hotspot, being already on the network). Projection forms anyway on
  the same socket.
- `SoftApInfo` **5745 MHz**. The ordinary AAP session over the hotspot formed and ran exactly as in
  round 3.

## R2 — hotspot reconnect: does it start with no Bluetooth handshake

**PASS** — and this is the feature working for the first time. Run twice (`r4-r2-*`, `r4-r2b-*`).

Setup: after R1, **phone not touched**; `headunit://exit`, `am force-stop`, relaunch `MainActivity`;
SoftAP kept up across the exit.

```
r4-r2  (relaunch 03:10:17)
  03:10:23.653  WppTcpServer: connection from 192.168.156.183
  03:10:23.724  WppTcpServer: TLS handshake complete with 192.168.156.183 (TLSv1.2, TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256)
  03:10:23.7..  [TX] Type 4 -> [RX] Type 5 -> [TX] Type 1 -> [RX] Type 7 -> [RX] Type 6
  03:10:24.395  WirelessServer: Incoming connection detected from /192.168.156.183
  03:10:24.601  SSL handshake complete. Session id: S0+zgRUWpBI1YUDhPk1wHY9pnMEwmJGDyOcTQL56RgM=
  03:10:2x      First frame rendered
r4-r2b (relaunch 03:12:16)
  03:12:20.762  WppTcpServer: connection from 192.168.156.183
  03:12:20.829  WppTcpServer: TLS handshake complete with 192.168.156.183 (TLSv1.2, TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256)
  03:12:21.495  WirelessServer: Incoming connection detected from /192.168.156.183
  03:12:21.690  SSL handshake complete. Session id: lMNT8ALjHsc3wzi1XkSLPHZAfgaepNIK5icqaweanLI=
```

| | r2 | r2b |
|---|---|---|
| **`NativeAA: Connection accepted from`** | **0** | **0** |
| `WppTcpServer: connection from` | 1 | 1 |
| `TLS handshake complete with` | 1 | 1 |
| `WppTcpServer: session error` | 0 | 0 |
| `WirelessServer: Incoming connection detected` | 1 | 1 |
| `First frame rendered` | 1 | 1 |
| repeat dials / loop | **none** (1 `connection from`, 1 phone-side `WPP on TCP connection failed to read`) | none |
| relaunch → `Incoming connection detected` | **~7.4 s** | **~5.5 s** |
| projection | stable 50–51 fps, `dropped=0` every window, 50+ s | same |

**No Bluetooth handshake ran at all.** The phone dialled the stored TCP endpoint, joined, and a
projection session formed. Round 3's R3 ran this exact sequence and got **6 dials / 5 TLS failures,
looping every 31 s, no session, brick recovered only by forgetting the head unit.** Here: one dial,
one benign control-channel timeout, a working stable session, twice. TCP reconnect (7.4 s / 5.5 s)
is at parity with round 3's RFCOMM reconnect (7.85 s), marginally faster.

## R3 — WiFi Direct regression

**Connect: PASS. Reconnect: FAIL — but the failure is test-order contamination, not a regression
from `13a43a6d`.** First attempt (`r4-r3-*`) is **void**: `static-bssid` / `hotspot-ssid` from R1/R2
were still set, so the P2P group came up named `DIRECT-0F-Navegadortz2` with the forced BSSID
`00:27:15:43:06:6a`, and the phone could not associate to a BSSID it had cached as SSID
`Navegadortz2`. Round 3's R1 ran with `static-bssid=0` and empty hotspot keys; those were cleared
and the run redone (`r4-r3b-*`). The phone's WiFi + BT were cycled between the two to clear the
stale association.

### Connect 1 (`r4-r3b-hu.txt`)

**PASS**

```
03:17:47.885  WifiDirectManager: 5GHz createGroup SUCCESS!
03:17:48.016  onGroupInfoAvailable: SSID: DIRECT-B8-HeadUnit, BSSID: b2:1e:a6:38:cb:bf, IFACE: p2p-wlan0-1, Freq: 5220 MHz
03:18:04.160  NativeAA: not advertising WPP over TCP: a WiFi Direct group is renamed every time it is created, and the phone would keep dialling the one it stored
03:18:08.498  WirelessServer: Incoming connection detected from /192.168.49.2
03:18:08.722  SSL handshake complete. Session id: IXsOwYhz4MNfMMeMO+EOm8qmZu406mf8MgNhHIEQlVw=
03:18:xx      First frame rendered — 49 fps, dropped=0
```

`advertising WPP over TCP` = **0**, `not advertising WPP over TCP: a WiFi Direct group is renamed…`
= **1**, `createGroup SUCCESS` = 1, single `p2p-wlan0-1`, `Magic Garbage` = 0. Session formed on a
fresh WiFi Direct connect exactly as round 3 measured. **No fresh poisoning.**

### Reconnect (`r4-r3b-recon-*`)

**FAIL** — no session in 2+ min.

```
Phone: GH.WPP.TCP: Trying to start WPP on TCP with configuration:
       WifiConfiguration(ssid=DIRECT-B8-HeadUnit, bssid=B2:1E:A6:38:CB:BF, ...MRUFrequenciesMhz=[5220]),
       ipAddress=192.168.156.146, port=5299          <-- stale: hotspot IP + previous group's SSID/BSSID
       WIRELESS_CONNECTING_WIFI (7000) -> (12000) -> (17000) -> loops, never associates
HU:    recoverNativeGroup: no phone joined within 60s -> recreate attempt 1/4  (createGroup SUCCESS = 2)
       "The phone has answered 3 wake pokes but has never opened the Android Auto channel…"
       WirelessServer: Incoming connection detected = 0, SSL handshake complete = 0
```

**Why this is not a `13a43a6d` regression.** `WppEndpointPolicy.decide` returns `Withhold` for
every non-hotspot strategy, so the HU's `WifiVersionRequest` on WiFi Direct carries **no endpoint**
(`not advertising WPP over TCP` logged, connect 1). The phone's `Trying to start WPP on TCP` on the
reconnect is driven entirely by a WPP-TCP config **the phone cached in memory during R1/R2's
hotspot sessions** — the Gearhead process (`pid 15162`) was never restarted across the whole round.
It merged the current group's SSID/BSSID into that cached config but kept the stale hotspot IP
`192.168.156.146`, then looped on a network that no longer exists (the P2P group is renamed on
every create) and never fell back to RFCOMM. Nothing on `13a43a6d` — or anywhere in the branch —
touches the WiFi Direct association path or that cached-config behaviour.

Round 3's R1 (the equivalent run) passed because its phone had **never** done a hotspot WPP-TCP
session, so there was no cached config to contaminate the WiFi Direct reconnect. This round ran
R1/R2 first, so there was.

**This still surfaces a real finding** (see below): `WppEndpointPolicy.Withhold` stops the HU
*advertising* a new endpoint on WiFi Direct, but does nothing to clear one the phone already
cached from a prior hotspot session. Such a phone will loop on every WiFi Direct reconnect until
the head unit is forgotten. With `native-wifi-version-exchange` **off** (the shipping default) no
WPP-TCP config is ever created, so this cannot arise in a shipped build.

A clean re-run of R3 (phone with the head unit forgotten, or Gearhead freshly started, before any
hotspot run) is needed to close the regression check formally.

## R4 — the app stops stranding the hotspot

**Not run.** Optional, second build off `fork/fix/hotspot-stranded-on-service-destroy` @ `3d9d7325`;
deferred. `auto-enable-hotspot=false` was used for R1–R3 as the brief instructed, so the round-3
stranding defect never bit this round.

## Report-back answers (brief §7)

1. **R1 — `TLS handshake complete with` count: 2 (r1) / 1 (r1b).** Protocol and cipher:
   **`TLSv1.2, TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256`** every time. A client certificate was
   presented and accepted. `WppTcpServer: session error` = 0. The WPP message exchange stops at
   `[RX] Type 6` and never logs `handshake complete; projection session is up`, because the phone
   never sends `Type 2` on the hotspot and `WppHandshakeSession.onAwaitInfoRequest` has no path out
   on `Type 6` / `TcpSessionUp`. Projection forms anyway and runs stable at 50 fps.
2. **R2 — did a session form with `NativeAA: Connection accepted from` = 0? YES**, twice, in
   ~7.4 s and ~5.5 s from relaunch. No Bluetooth handshake, no loop, no brick. This is the feature
   working; round 3's R3 brick is gone.
3. **R3 — WiFi Direct: connect still behaves as round 3** (endpoint withheld, session forms, no
   fresh poisoning). The **reconnect failed**, but from a WPP-TCP config the phone cached during
   this round's own R1/R2 hotspot runs, not from anything on the candidate. Needs a clean-phone
   re-run to formally clear the regression check.

**Shipping question.** The TLS server now works end to end (R1, R2) — the blocker rounds 2 and 3
named is fixed. The hotspot reconnect, which round 3 bricked, now forms a session with zero
Bluetooth (R2). Two things remain before `native-wifi-version-exchange` could be turned **on** by
default:
- `WppHandshakeSession` must handle the phone completing the exchange without a `WifiInfoRequest`
  (hotspot: phone already on-network) — today the session machine never reaches `DONE` on that
  transport and the phone logs a spurious `WPP on TCP connection failed to read`.
- The cross-transport cached-endpoint case (R3 reconnect) needs a decision: either the phone should
  be told to drop the endpoint when it is withheld, or the HU should keep the WiFi Direct group's
  identity stable enough for a stored dial to land.

With the flag **off** (unchanged default) none of this is reachable and the rest of the branch is
safe to ship, exactly as round 3 concluded.

Setup-note facts the brief asked for: hotspot **SSID `Navegadortz2`**, **BSSID
`00:27:15:43:06:6a`**, **`SoftApInfo` frequency 5745 MHz** (≥ 5180). Phone was **not** cleared
before R1 and did not need to be (`No WPP on TCP configuration found`). R4 was not attempted, so
all runs were on the one build `6fbff645…` @ `13a43a6d`.

## Anything the brief did not ask about

- **The WPP-over-TCP handshake and the projection session share one TCP connection.** After
  `[RX] Type 6` the HU's normal `AapTransport` handshake runs on the same socket
  (`Version response recv ret: 12` → `SSL handshake complete` → `Status OK sent: 8`), so the
  projection session is up ~200 ms after Type 6 regardless of the WPP session machine stalling. The
  stall is invisible to the user.
- **On the hotspot, the phone runs the RFCOMM handshake *and* the TCP handshake** on the first
  connect (R1a): RFCOMM completes (types 4/5/1/2/3/7), the HU closes RFCOMM on the 3 s grace, the
  phone hits `RFCOMM_READ_WRITE_FAILURE` and restarts on TCP. The 3 s `closeAaListeners()` grace
  (CLAUDE.md flags it as a race) is landing *before* the phone finishes RFCOMM here. On the
  reconnect (R2) there is no RFCOMM at all — straight to TCP.
- **`WppTcpServer` binds 5299 unconditionally**, on every transport and with the flag off (seen in
  the R3 WiFi Direct captures: `WppTcpServer: listening for Android Auto on TCP 5299`). Confirmed
  from round 3; still true.
- **The phone keeps a WPP-TCP config alive for the whole Gearhead process lifetime**, separate from
  the persistent car DB (`wifiProjectionProtocolOnTcp=false` throughout). It updates the network
  identity in that cached config from each connection's `WifiInfoResponse` but does not drop it
  when the HU withholds the endpoint. This is the R3-reconnect mechanism and is worth a line in
  `TESTING-TEMPLATE.md` §7a: **a WPP-TCP round contaminates every later run in the same session
  until Gearhead is restarted or the head unit forgotten.**
- **`NO_COMPATIBLE_WIFI_CHANNEL_FOUND` / `WiFi channels not supported: []`** appears on the phone on
  the `native-wifi-version-exchange=true` path on *both* transports, transiently, and is not fatal
  on its own — the phone proceeds to `CONNECTING_WIFI` / `CONNECTED_WIFI` anyway. It was only fatal
  in the void R3 run, where the SSID/BSSID mismatch (leftover `static-bssid`) was the real cause.
- **`SingleKeyKeyManagerTest` and `WppTcpTlsTest` ran clean on the coding host** — no
  file-not-found on `res/raw/cert` / `privkey`.
