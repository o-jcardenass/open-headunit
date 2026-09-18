# wpp-over-tcp — round 5 results

**Candidate:** `fork/fix/wpp-over-tcp` @ `ddf8b198` (six commits on `2f07eeec`, no rewrite) &nbsp;&nbsp; **Baseline:** none (every run is a settings change on the candidate)
**APK md5:** candidate `0d72f3a85f8bcd6f08afd0031e5eb82e` (`3.3.0-beta4`, `assembleGithubDebug`)
**Unit:** UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14, single BT radio, rooted `adb shell`
**Phone:** POCO X3 NFC (`M2007J20CG`, `4f4027e9`), Android 15, Gearhead `17.5.663204-release`
**Date:** 2026-09-01

## Verdict in one line

**R0 PASS, R1 PASS, R2 PASS, R3 PASS (both reconnects), R4 not run.**
The formally-open regression check is closed: with the run order fixed (WiFi Direct first, on a
phone with the head unit forgotten), the WiFi Direct **reconnect forms a session and the phone
never touches the stored endpoint** (`Trying to start WPP on TCP` = 0 across both R1 episodes). The
exchange now finishes on the hotspot: `WppTcpServer: handshake complete; projection session is up`
and `holding the control channel open for the session` both appear on every hotspot connect and
reconnect, and projection is stable throughout (R2 35 windows, R3 18+18 windows, `dropped=0` in
every one).

**The one measurement the round exists to produce: the phone pings the control channel `0` times.**
On all three hotspot sessions (R2, R3a, R3b) the phone opens the WPP-over-TCP socket, waits ~10.1 s
for an unsolicited first message from the head unit, gets none, and closes the channel with a read
timeout (`WPP on TCP connection failed to read`, exactly as round 4). `WppTcpServer.holdOpen`
therefore always ends with `the phone closed the control channel after 0 pings`. `ddf8b198` neither
helped nor hurt on this Gearhead build — the `failed to read` line the brief hoped was gone is
still there — but it is harmless: projection stability is identical to round 4 on every metric.

## Setup notes

### Rig / environment

- Transfer branch fetched and checked out at `5fef02d1`. `TESTING-TEMPLATE.md`, this thread's
  `wpp-over-tcp-round5-brief.md` and `wpp-over-tcp-round4-results.md` (cited by the brief) read in
  full. No other threads' files read.
- Candidate SHA verified: `git rev-parse fork/fix/wpp-over-tcp` → `ddf8b1987aee…`, six commits on
  `2f07eeec` (`8ff9510d`, `894093fc`, `968573ab`, `13a43a6d`, `a62cc22b`, `ddf8b198`), no rewrite.
- **R0 on the coding host**: `hur-wifi-test-scripts/run_unit_tests.sh` then `build_hur.sh`
  (`JAVA_HOME=/opt/android-studio/jbr`). Both clean.
- Scripts used: `run_unit_tests.sh` (R0), `build_hur.sh` (R0 APK), `set_hu_prefs.sh` (all settings
  writes). Nothing added or changed. Captures left in the sibling
  `hur-wifi-test-scripts/round-wpp-over-tcp-r5/` (not on this branch), same as rounds 3 and 4.
- Candidate installed with `adb install -r` (was carrying round 4's `6fbff645…`), live md5
  `0d72f3a…` confirmed against the built APK before the runs.
- `settings.xml` backed up app-stopped, restored **byte-identical** at the end (`diff` clean,
  re-chowned `10168:10168`). SoftAP stopped, `force-softap-band` reset to `disabled`. Candidate APK
  left installed. No stray `logcat` processes (`pgrep -af logcat` → only the shell wrapper).

### `settings.xml` delta

Pre-round backup carried `wifi-connection-mode=3`, `hotspot-band=1`, `wifi-direct-band=1`,
`log-level=2` (INFO — the brief says INFO is enough and it was), `view-mode=2` (GLES),
`native-ap-transport=0`, `native-wifi-version-exchange=false`, `auto-enable-hotspot=true`, and the
four hotspot keys already empty/`0` (`hotspot-ssid`, `hotspot-password`, `hotspot-interface` blank;
`static-bssid=0`). Keys changed during the round:

| key | R1 (WiFi Direct) | R2 / R3 (hotspot) |
|---|---|---|
| `native-wifi-version-exchange` | `false` → **`true`** | `true` |
| `auto-enable-hotspot` | `true` → **`false`** | `false` (brief's workaround, not a deviation) |
| `native-ap-transport` | `0` (unchanged) | `0` → **`1`** |
| `hotspot-ssid` | `""` (unchanged) | `""` → **`Navegadortz2`** |
| `hotspot-password` | `""` (unchanged) | `""` → **`12345678`** |
| `static-bssid` | `0` (unchanged) | `0` → **`00:27:15:43:06:6a`** |
| `hotspot-interface` | `""` (unchanged) | `""` → **`wlan2`** |
| `hotspot-band` | `1` (unchanged) | `1` (unchanged) |

So the R1 delta from backup was two keys; R2/R3 delta was seven. Final state restored
byte-identical to the backup.

### Clean-run protocol deviations (all per §7a, this rig)

- **Airplane mode cannot be driven from adb on the POCO** (§7a). Link-state lever was the phone's
  own Bluetooth adapter: `svc bluetooth disable` before HU bring-up, `svc bluetooth enable` after
  the HU had settled ~15–18 s. Verified with `dumpsys bluetooth_manager | grep state:` each time.
- **`MATCH! Starting AapService` is not contamination here** — it is the phone's own Bluetooth
  reconnect firing `AutoStartReceiver`, the documented Native-AA (re)connect mechanism on this rig
  (§7a, round 4 setup notes). It fired once per connect episode. The discard rule is a *second*
  `createGroup SUCCESS` in one episode (WiFi Direct) — that never happened. On the hotspot transport
  `createGroup SUCCESS` = 0 throughout (no P2P group).
- **Head unit brought up before the phone** every run (§7a).
- Capture flush verified each run: last capture timestamp within 1–2 s of the `kill` wall-clock.

### Hotspot arm: how the SoftAP was brought up

Same recipe as rounds 3 and 4. Rig **refuses `setSoftApConfiguration()`**, so a transient AP was
started on the same SSID/passphrase as the rig's persisted config:

```
adb shell cmd wifi force-softap-band enabled 5
adb shell cmd wifi start-softap Navegadortz2 wpa2 12345678 -b 5
```

`SoftApInfo`: **frequency 5765 MHz** (≥ 5180, so any video result is real, not the band),
BSSID `00:27:15:43:06:6a`, interface `wlan2`, IP `192.168.156.146/24`. `auto-enable-hotspot=false`
kept the AP up across the R2→R3 exit unchanged (10-min idle-shutdown timer, never reached).

### Phone state

- **Forgotten before R1** and **again before R2**, both by the operator in Android Auto's settings
  (not scriptable). R1's first connect logged
  `GH.WIRELESS.SETUP: No WPP on TCP configuration found in storage for the head unit` on both the
  connect and the reconnect (4 lines total) — step 0 took.
- Phone ↔ head unit Bluetooth **bond intact** throughout (`DC:B7:2E:5E:4E:59 POCO X3 NFC` in the
  HU's bonded list; `Navegadortz2` on the POCO).
- **End-of-round:** the phone holds the R2/R3 hotspot WPP-TCP config
  (`ssid=Navegadortz2, ipAddress=192.168.156.146, port=5299`) in its running Gearhead process. Not
  cleaned — the brief does not ask, and this is the expected end state that the extended withhold
  text now tells users how to recover from.

---

## R0 — build gate

**PASS**

`run_unit_tests.sh` then `build_hur.sh` at `ddf8b198`, coding host. `BUILD SUCCESSFUL`,
`compileGithubDebugKotlin` clean. From the JUnit XML:

| suite | tests | brief predicted |
|---|---|---|
| `WppHandshakeSessionTest` | **30** | 30 ✓ |
| `WppEndpointPolicyTest` | **6** | 6 ✓ |
| `WppTcpTlsTest` | **1** | 1 ✓ |
| `SingleKeyKeyManagerTest` | **4** | 4 ✓ |
| `WppMessagesTest` | **11** | 11 ✓ |
| whole suite | **986 / 0 / 0 / 0** | 986/0 ✓ |

Exact match to the brief.

---

## R1 — WiFi Direct, clean phone, connect and reconnect

**PASS** — and this closes round 4's formally-open regression check.

- Settings written: `native-wifi-version-exchange=true`, `auto-enable-hotspot=false`,
  `native-ap-transport=0`, `wifi-direct-band=1`, `hotspot-ssid`/`-password`/`-interface` empty,
  `static-bssid=0`, `wifi-connection-mode=3`, `log-level=2`
- Radio state: HU Wi-Fi enabled; phone BT off during HU bring-up (`svc bluetooth disable`), then
  `svc bluetooth enable` at 03:51:46 / 03:53:18(relaunch)
- Discard-rule check: **clean** — `createGroup SUCCESS` = 1 per launch (2 total, one per episode),
  `Magic Garbage` = 0, one `MATCH! Starting AapService` per episode, one SSL handshake per episode,
  `p2p-wlan0-0` then `p2p-wlan0-1` (one per launch — expected, each episode is a fresh launch)

### Connect (03:51:14 launch)

```
03:51:17.070  WifiDirectManager: 5GHz createGroup SUCCESS!
03:51:17.223  onGroupInfoAvailable: SSID=DIRECT-5A-Navegadortz2, IP=192.168.49.1, BSSID=b6:17:f3:a9:39:1c
03:51:46.223  NativeAA: not advertising WPP over TCP: a WiFi Direct group is renamed every time it is created,
              and the phone would keep dialling the one it stored. Withholding one does not clear one the phone
              already has: ... so if a WiFi Direct connection will not start, forget this head unit on the phone
03:51:46.460  NativeAA: [RX] Received Type 2      (Bluetooth handshake — RFCOMM)
03:51:50.797  WirelessServer: Incoming connection detected from /192.168.49.142
03:51:51.030  SSL handshake complete
03:51:52.800  First frame rendered (hardware decode)
```

### Reconnect (`headunit://exit` → force-stop → relaunch 03:53:18, phone untouched)

```
03:53:21.417  WifiDirectManager: 5GHz createGroup SUCCESS!
03:53:28.194  NativeAA: not advertising WPP over TCP: a WiFi Direct group is renamed ...
03:53:31.425  WirelessServer: Incoming connection detected from /192.168.49.29
03:53:31.647  SSL handshake complete
03:53:33.361  First frame rendered (hardware decode)
```

### Counts (whole R1 capture)

| | value |
|---|---|
| `advertising WPP over TCP at` (bare, the endpoint being sent) | **0** |
| `not advertising WPP over TCP: a WiFi Direct group is renamed…` | **2** (one per episode) |
| phone `Trying to start WPP on TCP` | **0** |
| phone `No WPP on TCP configuration found in storage for the head unit` | 4 (2 per episode) |
| phone `WPP on TCP connection failed to read` | **0** |
| `createGroup SUCCESS` | 2 (1 per launch) |
| `SSL handshake complete` / `WirelessServer: Incoming` / `First frame rendered` | 2 / 2 / 2 |
| `Magic Garbage` | 0 |
| projection | connect 46–54 fps, reconnect 46–51 fps, `dropped=0` every window |

**Verdict:** PASS on the brief's exact predicate — `advertising WPP over TCP` = 0, at least one
`not advertising…renamed` line, the phone never logs `Trying to start WPP on TCP`, and a session
forms on **both** the connect and the reconnect. The new withhold-text tail
("…forget this head unit on the phone") is present. Round 4's R3 contamination announces itself as
`Trying to start WPP on TCP`; that line is absent, so the check is clean, not merely "worked this
time."

---

## R2 — hotspot, first connect, and the exchange finishing

**PASS** — the exchange now reaches `handshake complete` and the channel is held.

- Settings written: `native-ap-transport=1`, `native-wifi-version-exchange=true`,
  `hotspot-ssid=Navegadortz2`, `hotspot-password=12345678`, `static-bssid=00:27:15:43:06:6a`,
  `hotspot-interface=wlan2`, `hotspot-band=1`, `auto-enable-hotspot=false`, `log-level=2`
- Radio state: transient SoftAP up, `SoftApInfo` **5765 MHz**, BSSID `00:27:15:43:06:6a`, iface
  `wlan2`; phone BT off during bring-up, `svc bluetooth enable` at 03:56:32
- Discard-rule check: **clean** — `createGroup SUCCESS` = 0 (hotspot, no P2P group), `Magic Garbage`
  = 0, one `MATCH! Starting AapService`, one AAP `SSL handshake complete`, one `session closed`

### Timeline (HU), verbatim

```
03:56:04.765  SoftApCredentials: SUCCESS - Providing credentials from wlan2: SSID=Navegadortz2, IP=192.168.156.146, BSSID=00:27:15:43:06:6A
03:56:04.906  WppTcpServer: listening for Android Auto on TCP 5299
03:56:32.266  NativeAA: Connection accepted from POCO X3 NFC ... on local radio [Navegadortz2]   (RFCOMM — see note)
03:56:35.925  WirelessServer: Incoming connection detected from /192.168.156.183
03:56:36.131  WppTcpServer: connection from 192.168.156.183
03:56:36.146  SSL handshake complete   (AAP session, same socket)
03:56:36.191  WppTcpServer: TLS handshake complete with 192.168.156.183 (TLSv1.2, TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256)
03:56:36.193  WppTcpServer: [TX] WifiVersionRequest (Type 4) v4.2
03:56:36.225  WppTcpServer: [RX] Type 5 (135 bytes)
03:56:36.226  WppTcpServer: [TX] WifiStartRequest (Type 1) -> 192.168.156.146:5288
03:56:36.258  WppTcpServer: [RX] Type 7 (2 bytes)
03:56:36.528  WppTcpServer: handshake complete; projection session is up        <-- NEW, the point of R2
03:56:36.530  WppTcpServer: holding the control channel open for the session    <-- NEW
03:56:36.788  WppTcpServer: [RX] Type 6 after the handshake (2 bytes)           <-- absorbed by holdOpen, not a stall
03:56:37.834  First frame rendered (hardware decode)
03:56:46.377  WppTcpServer: the phone closed the control channel after 0 pings  <-- ~9.85 s after handshake
03:56:46.383  WppTcpServer: session closed
```

Round 4 saw `4 → 5 → 1 → 7 → 6` then stopped in `AWAIT_INFO_REQUEST`. Here the exchange completes
on `Type 7` (`WifiStartResponse`, status 0), and the `WifiConnectStatus` (Type 6) that arrives
260 ms later is handled by `holdOpen`. `a62cc22b` works.

### Counts

| | value |
|---|---|
| `WppTcpServer: TLS handshake complete with` (`TLSv1.2, TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256`) | **1** |
| `WppTcpServer: connection from` | 1 |
| `WppTcpServer: session error` | **0** |
| `WppTcpServer: handshake complete; projection session is up` | **1** |
| `WppTcpServer: holding the control channel open for the session` | **1** |
| `WppTcpServer: the phone closed the control channel after N pings` | **1, N = 0** |
| `WirelessServer: Incoming connection detected` / AAP `SSL handshake complete` / `First frame rendered` | 1 / 1 / 1 |
| `createGroup SUCCESS` / `Magic Garbage` / `ByeBye` | 0 / 0 / 0 |

### Throughput — R2 vs round 4

35 windows over ~3 minutes (03:56:42 → 03:59:32). After the two startup windows (21 fps partial,
53 fps), **steady 45–47 fps, `dropped=0` in every one of the 35 windows**, `codec=c2.unisoc.hevc.decoder`.
Round 4 measured 50–51 fps. The ~4 fps gap is not attributable to `ddf8b198`: the control channel
was alive for only ~10 s of the 3-minute session, fps read 46 both before and after it closed
(03:56:46), and R1 this round (WiFi Direct, zero WPP-channel traffic) also settled at 46 fps. It is
normal MT50 session-to-session variance (different day, thermal state).

### Phone side (`GH.*`)

```
03:56:31.993  No WPP on TCP configuration found in storage for the head unit   (start of connect)
03:56:32.684  Send WifiStartResponse, protocol=RFCOMM, status=STATUS_SUCCESS
03:56:35.625  Send WifiConnectStatus, status=STATUS_SUCCESS
03:56:36.001  Trying to start WPP on TCP with configuration: ... ssid=Navegadortz2, ipAddress=192.168.156.146, port=5299
03:56:36.006  WPP on TCP connected to the WiFi network
03:56:36.039  WPP starting to listen for messages
03:56:36.123  Send WifiStartResponse, protocol=TCP, status=STATUS_SUCCESS
03:56:36.628  Send WifiConnectStatus, status=STATUS_SUCCESS
03:56:46.127  WPP on TCP connection failed to read   (java.net.SocketTimeoutException: Read timed out — 10.09 s after "starting to listen")
03:56:46.129  Stopping WPP on TCP  ->  WPP on TCP stopped
```

The phone opens the TCP channel, does the message exchange, then **waits to read an unsolicited
first message from the head unit** and times out after ~10.1 s. It never sends a ping (no `WPP_PING`
/ `WIRELESS_*PING*` event anywhere in the capture) and does not redial (`Trying to start WPP on TCP`
= 1). `WPP on TCP connection failed to read` **did appear**, which the brief said should be gone in
a passing R2 — see the finding below. It is not fatal: the projection session is already up on the
same socket and runs undisturbed.

**Verdict:** PASS. The brief's PASS predicate is `4/5/7` exchange → `handshake complete` →
`holding the control channel open` → projection forms and runs; all four happened, `session error`
= 0, TLS completed. The `failed to read` line and the `0` ping count are findings, not stated FAIL
conditions.

---

## R3 — hotspot reconnect

**PASS**, both reconnects. Phone not touched; `headunit://exit` → `am force-stop` → relaunch, SoftAP
kept up.

| | R3a (relaunch 04:00:06) | R3b (relaunch 04:00:46) |
|---|---|---|
| `NativeAA: Connection accepted from` | **0** | **0** |
| `WppTcpServer: connection from` | 1 | 1 |
| `TLS handshake complete with` (same cipher) | 1 | 1 |
| `WppTcpServer: session error` | 0 | 0 |
| `WppTcpServer: handshake complete; projection session is up` | **1** | **1** |
| `WirelessServer: Incoming connection detected` | 1 (`/192.168.156.183`) | 1 (`/192.168.156.183`) |
| `First frame rendered` | 1 | 1 |
| `the phone closed the control channel after N pings` | 1, **N = 0** | 1, **N = 0** |
| `createGroup SUCCESS` / `Magic Garbage` | 0 / 0 | 0 / 0 |
| **relaunch → `Incoming connection detected`** | **~6.5 s** (04:00:06 → 04:00:12.504) | **~6.1 s** (04:00:46 → 04:00:52.075) |
| projection | 49–51 fps, `dropped=0` (18 windows) | 46–52 fps, `dropped=0` (18 windows) |

Round 4 measured 7.4 s / 5.5 s for the same relaunch→session interval; this round 6.5 s / 6.1 s —
parity. On both reconnects the phone dialled the stored endpoint (`Trying to start WPP on TCP` = 2,
one per reconnect), and on both the channel closed after 0 pings ~9–10 s later, same as R2. No
loop, no brick — round 3's R3 infinite loop stays gone.

### R3a exchange (Type 6 lands *during* the exchange this time — still completes)

```
04:00:11.837  WppTcpServer: TLS handshake complete with 192.168.156.183 (TLSv1.2, TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256)
04:00:11.845  [TX] WifiVersionRequest (Type 4) v4.2
04:00:11.932  [RX] Type 5
04:00:11.942  [TX] WifiStartRequest (Type 1) -> 192.168.156.146:5288
04:00:11.972  [RX] Type 7
04:00:12.487  [RX] Type 6 (2 bytes)                                  <-- in-exchange, handled by runExchange
04:00:12.504  WirelessServer: Incoming connection detected from /192.168.156.183
04:00:12.759  WppTcpServer: handshake complete; projection session is up
04:00:12.760  WppTcpServer: holding the control channel open for the session
04:00:14.389  First frame rendered
04:00:22.102  WppTcpServer: the phone closed the control channel after 0 pings
```

`a62cc22b` completes the exchange whether Type 6 arrives before `handshake complete` (R3a, R3b) or
after it (R2). Robust to both orderings.

---

## R4 — the app stops stranding the hotspot

**Not run.** Optional second build (`fork/fix/809-five-ghz-channel-choice` @ `1e265ca3`), unchanged
from round 4 where it was also deferred. `auto-enable-hotspot=false` was used for R1–R3 as the
brief instructed, so the stranding defect never bit this round.

---

## Report-back answers (brief §7)

1. **R1 — did a session form on both the connect and the reconnect? YES.** Connect at 03:51:50
   (`/192.168.49.142`), reconnect at 03:53:31 (`/192.168.49.29`), both to `SSL handshake complete`
   + `First frame rendered`. **The phone logged `Trying to start WPP on TCP` zero times.** The
   round-4 regression check is closed clean.
2. **R2 — does `handshake complete; projection session is up` appear? YES**, at 03:56:36.528, one
   run, followed immediately by `holding the control channel open for the session`. **Throughput:**
   35 windows, steady **45–47 fps, `dropped=0` every window**, vs round 4's 50–51 fps — a ~4 fps
   gap that tracks normal MT50 variance (R1's WiFi Direct session this round also sat at 46 fps),
   not the new commit; fps was flat across the channel's open and close.
3. **Ping count: `0`, on all three hotspot sessions (R2, R3a, R3b).** The phone never pings the
   WPP-over-TCP control channel. It opens the socket, waits ~10.1 s for an unsolicited first
   message from the head unit, and closes the channel with a `SocketTimeoutException` read timeout
   (`WPP on TCP connection failed to read`). `WppTcpServer.holdOpen` consequently always logs
   `the phone closed the control channel after 0 pings`, ~9–10 s after `handshake complete`.
4. **R3 — relaunch to session with `Connection accepted from` = 0: YES**, twice. Intervals **6.5 s**
   and **6.1 s** (round 4: 7.4 s / 5.5 s). Both reconnects reached `handshake complete`.

**Setup facts the brief asked for:** hotspot **SSID `Navegadortz2`**, **BSSID `00:27:15:43:06:6a`**,
**`SoftApInfo` frequency 5765 MHz** (≥ 5180). Phone **was** forgotten before R1 and **again** before
R2 (both confirmed by `No WPP on TCP configuration found` on R1's connect and reconnect). **All runs
were on the one build** `0d72f3a…` @ `ddf8b198`; R4's second build was not installed.

## Shipping question

Round 4 named two things to finish before `native-wifi-version-exchange` could default **on**:

1. **The exchange must complete when the phone needs no credentials (hotspot).** Done — `a62cc22b`,
   confirmed by R2 and both R3 reconnects. The session machine now reaches `handshake complete` on
   the hotspot transport, in both Type-6 orderings.
2. **The cross-transport cached-endpoint case.** R1 shows that the clean-phone + WiFi-Direct-first
   ordering avoids the loop entirely, and `ddf8b198`'s sibling change gives the withhold line a
   tail telling the user to forget the head unit if a WiFi Direct connection will not start. That
   is a documentation fix, not a mechanism fix — a phone that did a hotspot WPP-TCP session earlier
   in the same Gearhead process still carries the endpoint. Unreachable with the flag off.

**New for the coding session:** `ddf8b198` holds the control channel open to answer pings, but on
Gearhead `17.5.663204` **the phone sends no pings** — it treats the channel as a setup channel it
reads once and abandons after a ~10 s read timeout, exactly as round 4 observed. Holding it open
changes nothing the phone does and does not remove the phone-side `WPP on TCP connection failed to
read` error line. It is harmless (projection is unaffected on all four sessions measured this
round), but if the goal of `ddf8b198` was to stop that phone-side error, it did not achieve it on
this build. Whether the phone pings on *other* Gearhead versions is still unmeasured; this is one
data point.

With the flag **off** (unchanged default) none of this is reachable and the rest of the branch is
safe to ship, as rounds 3 and 4 concluded.

## Anything the brief did not ask about

- **The WPP-over-TCP handshake and the AAP projection session share the one TCP connection**, as
  round 4 noted. `SSL handshake complete` (AAP) fires ~340 ms *before* `handshake complete`
  (WPP) in R2 — the projection session is up regardless of where the WPP session machine is.
- **`WppTcpServer: session closed` is new since round 4** (`ddf8b198`), logged when `holdOpen`
  returns after the phone closes the channel. `WppTcpServer: the session ended; closing the control
  channel after N pings` (the other holdOpen exit) was never hit — the phone always closed first.
- **The phone-side read timeout is a hard 10.1 s** on this build: `WPP starting to listen for
  messages` → `WPP on TCP connection failed to read` measured 10.09 s (R2), 10.17 s (R3a), 10.16 s
  (R3b). Consistent enough to be a fixed constant, not jitter.
- **`WppTcpServer: listening for Android Auto on TCP 5299` binds on every transport**, including
  WiFi Direct (R1 captures) — confirmed since round 3, still true at `ddf8b198`.
- The P2P group is named `DIRECT-5A-Navegadortz2` / `DIRECT-<xx>-Navegadortz2` on WiFi Direct this
  round (round 4 saw `DIRECT-B8-HeadUnit`). The base name follows `vehicle-display-name` /
  hotspot-ssid rather than being fixed — not investigated further, noted in case a later round
  keys on the group SSID.
