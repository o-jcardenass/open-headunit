# five-ghz-channel — round 2 results

**Candidate:** `fork/feat/native-aa-wpp-tcp-and-hfp-link` @ `6f1ee214` (6 commits on `origin/main` @ `a7076ff4`), `3.3.0`
**Baseline:** none — candidate-only, same tip as round 1. The comparison is between station states.
**APK md5:** `5e1c871dcf7b70c46b02e43498a2a955` (candidate, unchanged from round 1) — verified by `pm path` md5 on D-HU, `versionCode=103 versionName=3.3.0`.
**Unit:** D-HU = UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14, BT `11:46:03:10:33:59`. D-POCO = POCO X3 NFC (`surya`), Android 15, Gearhead `17.5.663204`, driver country CO, BT `DC:B7:2E:5E:4E:59`.
**Date:** 2026-09-01

## Verdicts

| Run | Verdict | One line |
|---|---|---|
| R0 identity gate | **PASS** | installed APK md5 `5e1c871d…` matches round 1 exactly; no rebuild, no unit-test re-run |
| R1 the matrix, station up (**the round**) | **PASS — outcome 1** | station up on `Pegue Cdesta` / 5260 MHz for all three launches; every requested channel formed the group on the **first** `createGroup` attempt at exactly the pinned frequency. The `-2` branch does not apply to this chip; the retry ladder is **still unexercised on hardware.** |
| R2 ladder cost re-paid? | **not run** | pre-registered: R2 runs only if R1 gave outcome 2 or 3. R1 gave outcome 1. |
| R3 phone join, station up | **PASS** | D-POCO joined the pinned 5180 MHz group, listed it in its own scan at 5180 MHz, and reached `Incoming connection detected` + `SSL handshake complete`, with the station associated before, during and after. |
| R4 harvest | done | counts below |

**Shipping read:** `cc85d7ab`'s retry ladder guards a failure mode this rig's WiFi chip does not have — it carries a station and a P2P group owner on two different 5 GHz channels concurrently, so a requested off-station channel returns a group, not `-2`. On hardware the pin is a plain success in both station states. The ladder's correctness stays on the JVM tests (`FiveGhzChannelPolicyTest` / `WifiP2pOperatingChannelPolicyTest`); nothing in this round exercised or regressed it. No blocker for the branch.

---

## Setup notes

**Rig state at round start — the station was down, and was restored on the rig, not by adb.**
D-HU booted this round unassociated (`mWifiInfo SSID: <unknown ssid>`, `Supplicant state: DISCONNECTED`), continuing the state `p2p-bringup-loop-round1-results.md` left it in and round 1 ran in. `Pegue Cdesta` was saved and in range (‑17 dBm, BSSID `f4:52:46:60:8d:4e`, **5260 MHz** — channel 52, DFS), config healthy (`NETWORK_SELECTION_ENABLED`, `hasEverConnected: true`), but the saved network carried `allowAutojoin=false` and there is no `cmd wifi` verb to re-enable per-network autojoin (`clear-user-disabled-networks` + a `svc wifi` cycle did not touch that flag). `cmd wifi connect-network` would have fixed it but needs the passphrase; extracting that from the device credential store was declined. **The operator re-joined `Pegue Cdesta` from the head unit's system WiFi settings by hand** (one tap, uses the stored key, re-enables autojoin). This is the §7a "repair, not re-association" the brief authorised. After it, `dumpsys wifi` read `SSID: "Pegue Cdesta", BSSID: f4:52:46:60:8d:4e, Supplicant state: COMPLETED, IP: 192.168.1.15, Frequency: 5260MHz` and every run below was launched against that.

**Brief erratum — the station channel is 5260 MHz, not 5500.** §7a and this brief both record `Pegue Cdesta` at 5500 MHz. It re-associated at **5260 MHz** (ch 52) this round. Still DFS, still not one of the five offered channels (36/40/44/48/149), so the brief's premise is intact: every run requested a frequency the station was not on.

**Scripts used**
- `hur-wifi-test-scripts/five_ghz_matrix.sh` — R1 (three HU-only bring-ups: 36, 149, automatic). Added a `dumpsys wifi` station read printed and logged immediately before each `am start` (the brief said the script "only wants the station read adding"). No other change; `SETTLE_S=60` per the brief, `CHANNELS="36 149 0"`, `OUTDIR=round-five-ghz-channel-r2`.
- `hur-wifi-test-scripts/five_ghz_r2.sh` — R3 (one full session, `CHANNELS="36"`). Added the same station read before `am start`; made the output dir honour `OUTDIR`. `WAIT_S=120`.
- `hur-wifi-test-scripts/set_hu_prefs.sh` — called by both scripts for the settings writes.
Both edited scripts left in that folder for the next round.

**Radio state discipline.** R1 ran D-HU only: both phones' WiFi **and** Bluetooth disabled and verified (`settings get global wifi_on/bluetooth_on` = 0/0) before the matrix, so nothing could wake `AapService` (`auto-start-bt-macs` holds D-POCO's MAC). R3 brought D-POCO's radios back after the group settled. The head unit's own Bluetooth was left alone (it is not switchable off on this rig, §7a).

**Discard-rule checks — all four captures clean.** `createGroup SUCCESS` = exactly 1 in every run; `createGroup failed` / `retries exhausted` = 0 everywhere; `AapRead: Magic Garbage` = 0; `p2p-wlan0-N` monotonic `0 → 1 → 2 → 3`, one interface per group. R3's one `MATCH! Starting AapService` (`AutoStartReceiver.onReceive | MATCH! ... via Bluetooth Auto-start`, 16:28:21.486) is the phone's own Bluetooth reconnect after its radio was re-enabled — zero group churn attached, `createGroup SUCCESS` stayed 1, one `Handshake: SSL handshake complete` — benign per §7a's refined rule, identical to round 1's R2.

**Restore.** App force-stopped, `settings.xml` restored from the pre-round backup — md5 `1ac92489f8cbba7d7ad56f413d8eeb59` before and after, byte-identical, no `.bak` left on device. D-MOTO WiFi re-enabled; D-POCO left WiFi+BT on (its start state). Candidate APK untouched on D-HU. The head unit is now associated to `Pegue Cdesta` (5260 MHz) — the state §7a wants it in — where round 1 left it disconnected. No lingering `logcat` processes. The station association was **not** changed, only repaired.

---

## R0 — identity gate

**PASS**

- `adb shell pm path` → `/data/app/~~_Zy0Bor990xOXHcSybc0Fw==/com.andrerinas.headunitrevived-QOtcjOwHDzwniN2a3lAg8Q==/base.apk`
- `md5sum` = **`5e1c871dcf7b70c46b02e43498a2a955`** — exact match to the brief's expected value and to round 1's built APK.
- `dumpsys package`: `versionCode=103`, `versionName=3.3.0`, `lastUpdateTime=2026-09-01 14:14:05`.
- Per the brief: match → no build, no unit-test re-run. Round 1 measured 1113/0 with all five policy classes at their predicted counts; that stands.

---

## R1 — the matrix, with the station up. **This is the round.**

**PASS — pre-registered outcome 1** ("the group forms on the pinned frequency while the station is up").

- Settings written each run: `wifi-connection-mode=3`, `native-ap-transport=0`, `wifi-direct-band=1`, `hotspot-band=1`, `wifi-5ghz-channel=<36|149|0>`, `static-bssid=0`, `log-level=1`, `native-wifi-version-exchange=false`.
- Radio state: both phones WiFi+BT off (verified 0/0). D-HU only.
- Discard check: clean (see Setup notes).

| channel set | station SSID / freq at launch | createGroup first try? | ladder lines | launch → group | frequency arrived |
|---|---|---|---|---|---|
| **36** | `Pegue Cdesta` / **5260 MHz**, `COMPLETED` | **yes** — `5GHz createGroup SUCCESS!` 30 ms after the request | **0** | ~3.7 s (16:23:34 launch → 16:23:37.659 `onGroupInfoAvailable`) | **5180 MHz**, `5180 MHz was asked for`; `dumpsys wifip2p` `frequency: 5180` |
| **149** | `Pegue Cdesta` / **5260 MHz**, `COMPLETED` | **yes** — SUCCESS 41 ms after the request | **0** | ~3.5 s (16:24:39 → 16:24:42.532) | **5745 MHz**, `5745 MHz was asked for`; `frequency: 5745` |
| **0** (automatic) | `Pegue Cdesta` / **5260 MHz**, `COMPLETED` | **yes** — SUCCESS 38 ms after the request | **0** | ~3.4 s (16:25:44 → 16:25:47.373) | **5240 MHz** (ch 48), **no `was asked for` suffix** — `5 GHz channel is automatic, so the driver picks within the band.`; `frequency: 5240` |

Sample (channel 149):
```
16:24:42.317  WifiDirectManager: Requesting Native AA P2P group on 5GHz band. Chosen by the user.
16:24:42.318  WifiDirectManager: 5 GHz channel is channel 149 (5745 MHz), asked for as a fixed 5745 MHz.
16:24:42.358  WifiDirectManager: 5GHz createGroup SUCCESS!
16:24:42.532  WifiDirectManager: onGroupInfoAvailable: SSID: DIRECT-X4-Navegadortz2, BSSID: 0E:5E:04:CB:87:92 (source=IPv6 link-local), GO: true, IFACE: p2p-wlan0-1, Freq: 5745 MHz (5GHz), 5745 MHz was asked for
```

**Which of the three pre-registered outcomes:** outcome 1. The group formed on the *requested* off-station frequency (5180, 5745) on the first `createGroup` call, every time, with the station associated on 5260 MHz throughout. This chip carries more than one concurrent 5 GHz channel — a station on ch 52 and a P2P GO on ch 36/149 at once — so `wpas_p2p_setup_freqs()` never returns `-2` for it and the `cc85d7ab` retry ladder does not engage. It **remains unexercised on hardware**; this round did not cover it, it showed the branch of the code that skips it.

**The automatic control, same state:** with no frequency requested the driver picked **5240 MHz** (ch 48) — adjacent to the station's ch 52 but **not** equal to it, and **not** a UNII-3 channel this time. So on this rig, in this station state, the automatic pick is neither forced onto the home-network channel (the `force_freq` branch) nor rolled onto an invisible UNII-3 channel (round 1's automatic landed on 5805 MHz). The driver's own group-owner selection just varies between bring-ups; the pin removes that variance.

---

## R2 — is the ladder's cost paid again on every bring-up?

**Not run**, as the brief pre-registers: "Only run this if R1 produced outcome 2 or 3." R1 produced outcome 1 — the ladder never fired once, so there is no per-bring-up cost to measure. `pinnedChannelAbandoned` was never set.

---

## R3 — does a phone still join, with the station up?

**PASS**

- Settings: as R1 with `wifi-5ghz-channel=36`.
- Radio state: D-HU launched first with the station up (`Pegue Cdesta` / 5260 MHz, `COMPLETED`), group settled 15 s, then D-POCO Bluetooth + WiFi enabled (`svc`). Session wait 120 s.
- Discard check: clean — `createGroup SUCCESS` = 1, one `Handshake: SSL handshake complete`, one group `p2p-wlan0-3`, one benign `MATCH!` (phone BT reconnect).

| what | value |
|---|---|
| station at launch | `Pegue Cdesta`, BSSID `f4:52:46:60:8d:4e`, `COMPLETED`, 5260 MHz |
| requested | channel 36 → `asked for as a fixed 5180 MHz` |
| createGroup | `5GHz createGroup SUCCESS!`, first try, 28 ms after the request (16:28:06.678) |
| group frequency | **5180 MHz**, `5180 MHz was asked for`; `dumpsys wifip2p` `frequency: 5180` |
| D-POCO scan (`cmd wifi list-scan-results`) | `c2:f7:6a:32:cf:c5  5180  -24 dBm  DIRECT-5N-Navegadortz2` — **listed, at the pinned 5180 MHz** |
| `NativeAA: Connection accepted from` | POCO X3 NFC (`DC:B7:2E:5E:4E:59`), 16:28:21.662 |
| `WirelessServer: Incoming connection detected` | `from /192.168.49.60`, 16:28:23.644 — **the line that matters** |
| `SSL handshake complete` | 16:28:23.892 (`AapSslContext.performHandshake`) / `Handshake: SSL handshake complete` 16:28:23.893 |
| station after the run | `Pegue Cdesta`, `COMPLETED`, 5260 MHz — never dropped |

The phone got onto the network and completed the AAP session with the head unit joined to its home WiFi the whole time. Round 1's two clean sessions were with the station down; this is the same result in the state a user's head unit is actually in.

---

## R4 — harvest

No run repeated. Per-capture counts (`grep -ac`, all four captures):

| capture | `createGroup SUCCESS` | `createGroup failed` | `retries exhausted` | `was asked for` | `MATCH! Starting AapService` | `Handshake: SSL handshake complete` |
|---|---|---|---|---|---|---|
| R1 ch36 | 1 | 0 | 0 | 3 | 0 | 0 |
| R1 ch149 | 1 | 0 | 0 | 3 | 0 | 0 |
| R1 ch0 (auto) | 1 | 0 | 0 | **0** (automatic — correct, no suffix) | 0 | 0 |
| R3 ch36 | 1 | 0 | 0 | 4 | 1 (phone BT reconnect) | 1 (the session) |

`p2p-wlan0-N` union across all four captures: `p2p-wlan0-0 … p2p-wlan0-3` — exactly 4 interfaces for 4 group bring-ups (3 R1 + 1 R3), monotonic, one per run. No leak, no churn.

Station read verbatim, before each launch (all four `COMPLETED` on `Pegue Cdesta` / BSSID `f4:52:46:60:8d:4e` / 5260 MHz):
- R1 ch36: `RSSI: -18, Link speed: 433Mbps, ... Rx Link speed: 433Mbps, Frequency: 5260MHz`
- R1 ch149: `RSSI: -18, Link speed: 433Mbps, ... Rx Link speed: 6Mbps, Frequency: 5260MHz`
- R1 ch0: `RSSI: -18, Link speed: 292Mbps, ... Rx Link speed: 6Mbps, Frequency: 5260MHz`
- R3 ch36: `RSSI: -19, Link speed: 292Mbps, ... Rx Link speed: 6Mbps, Frequency: 5260MHz`

BSSID source dump still prints once per group (the `6f1ee214` passenger): `source=IPv6 link-local` resolves an EUI-64 MAC every time (`0E:5E:04:CB:87:92`, `5E:5F:69:1B:91:4F`, `C2:F7:6A:32:CF:C5`, …), every legacy MAC rung masked/null, the "every source tried, none named an address" line fired 0 times.

---

## The numbers that decide the shipping question

1. **Station state at every launch:** associated to `Pegue Cdesta`, `Supplicant state: COMPLETED`, **5260 MHz**, for all four launches (three R1 + R3). Read from `dumpsys wifi`, not assumed. The round did **not** run unjoined.
2. **R1's three rows:** requested 5180 / 5745 / automatic; first `createGroup` attempt succeeded in all three; arrived on 5180 MHz / 5745 MHz / 5240 MHz.
3. **The ladder did not fire** — 0 `createGroup failed`, 0 `retries exhausted`, 0 `already refused` across the whole round. This chip's STA+P2P concurrency means the `-2` case the ladder handles is not reachable on it. So the ladder's on-hardware behaviour is still unmeasured, and this round says so rather than claiming coverage.
4. **R2:** not applicable (outcome 1).
5. **R3:** session formed; D-POCO's own scan reported the group at **5180 MHz**, the pinned frequency.

## Anything the brief did not ask about

- **The station's Rx link rate collapses to 6 Mbit/s while a P2P group is (or was recently) up and never fully recovers between runs** — 433 → 6 Mbit/s from ch36 onward, still 6 at R3. Tx and the association itself are unaffected (`COMPLETED` throughout, Tx 433 → 292). This is the single-radio STA/P2P multi-channel-concurrency cost: the chip time-slices one radio across ch 52 and ch 36/149, and the STA side pays for it. It is exactly *why* R1 got outcome 1 — the chip supports MCC — but a user projecting while the head unit is also pulling from home WiFi would see the home link degraded, not dropped. Not a regression (no pinned-channel code path touches this) and not measured before because round 1 ran with the station down.
- **The automatic (`wifi-5ghz-channel=0`) pick differs run-to-run**: round 1 → 5805 MHz (UNII-3, station down); round 2 → 5240 MHz (UNII-1, station up on 5260). Two data points, two different channels, neither the station's. The invisible-UNII-3 hazard the setting targets is real but intermittent on this rig's driver; the pin is what makes the outcome deterministic.
- Re-joining a saved network whose `allowAutojoin` has been turned off is not doable through `adb` on this ROM (no `cmd wifi` verb for it; `clear-user-disabled-networks` addresses a different flag). If the rig drops its association again, it needs a UI tap to come back — worth a §7a line.
