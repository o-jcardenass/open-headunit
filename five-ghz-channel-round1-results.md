# five-ghz-channel — round 1 results

**Candidate:** `fork/feat/native-aa-wpp-tcp-and-hfp-link` @ `6f1ee214` (6 commits on `origin/main` @ `a7076ff4`), `3.3.0`
**Baseline:** none — every run is candidate-only, the comparison is between channels.
**APK md5:** `5e1c871dcf7b70c46b02e43498a2a955` (candidate) — built here, installed on D-HU, verified by `pm path` md5.
**Unit:** D-HU = UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14, BT `11:46:03:10:33:59` (not masked on this unit), permanently associated to `Pegue Cdesta` / `Navegadortz2` on 5 GHz. D-POCO = POCO X3 NFC (`surya`), **Android 15** (brief said 12), Gearhead `17.5.663204`, driver country **CO**, BT `DC:B7:2E:5E:4E:59`.
**Date:** 2026-09-01

## Verdicts

| Run | Verdict | One line |
|---|---|---|
| R0 build gate | **PASS** | 1113 / 0 exact; all 5 named classes at predicted counts; candidate symbols in DEX |
| R1 channel matrix (**the round**) | **PASS** | every pinned channel arrived on exactly its named frequency; automatic rolled onto 5805 MHz |
| R2 phone-join | **PASS** (both channels) | D-POCO joined 5180 and 5745, full session each; the "one joins / other doesn't" split did not occur — both devices are country CO |
| R3 access point | **INCONCLUSIVE** | the honest-line path is unreachable here: `getSoftApConfiguration()` returns null, so the app starts the hotspot unconfigured and never logs the channel request or the accepted/refused line. No dishonest claim was made. |
| R4 QR route | **INCONCLUSIVE** | Gearhead's `DeepLinkResolver` is exported and handled the intent (no browser fall-through), but `DEEP_LINK_ENABLED` is **disabled** on this Gearhead build — it logged `QR_CODE_NOT_SUPPORTED` and showed an error. Feature is inert phone-side. |
| R5 harvest | done | counts below; BSSID rewrite validated |

---

## Setup notes

**Scripts used**
- `hur-wifi-test-scripts/build_hur.sh` — R0 build. `run_unit_tests.sh` — R0 tests.
- `hur-wifi-test-scripts/set_hu_prefs.sh` — every settings write (multi-key, one relaunch).
- **New this round:** `hur-wifi-test-scripts/five_ghz_matrix.sh` (R1: six HU-only bring-ups, one per channel, greps the request/outcome lines) and `hur-wifi-test-scripts/five_ghz_r2.sh` (R2: two full sessions with phone-scan). Left in that folder. R3/R4 were run inline (single run + control each).

**Deviations / brief errata**
- **D-POCO is Android 15, not Android 12** as the brief's device table states. Gearhead `17.5.663204`, driver country CO (`mCountryCodeFromDriverCO`), `SupportedChannelListIn5g` = `[36,40,44,48,149,153,157,161,165, + DFS]`. Because D-POCO's domain is the same CO as D-HU and permits UNII-3, **R2 cannot produce the "one channel joins, the other does not" finding** the brief hoped for — both 36 and 149 are joinable by this phone. R2 is therefore a regression check (a pinned channel still forms a session), not the domain-split test. A phone carrying a UNII-3-restricted domain would be needed for that, and none is on this rig.
- **R3's decisive lines never printed.** The brief's PASS condition 1 (`SoftApConfigCompat: requesting 5 GHz channel 36 for the access point`) is gated behind reading the current AP config. On this unit `getSoftApConfiguration()` returns null (`SoftApConfigCompat.readSoftApConfiguration: could not read the current access point configuration: null`), so the app takes the `cannot read this device's access point configuration ... Starting the hotspot unconfigured` branch and never reaches the channel-request or accepted/refused lines. This matches the standing rig note that this unit cannot read its own hotspot config. Not a dishonest claim, so not a FAIL — reported INCONCLUSIVE.
- **R3 out-of-band control:** `cmd wifi start-softap OHU5TEST wpa2 testtest1234 -b 5` brings a SoftAP up on **5745 MHz** (channel 149), BSSID `00:27:15:43:06:6a`, confirmed in D-POCO's scan. `cmd wifi force-softap-channel enabled 36` is **rejected by this ROM's wifi shell** ("Invalid argument ... must be a valid WLAN channel"), so even the shell cannot pin channel 36 here; `force-softap-band enabled 5` is accepted but ACS still picks 5745. So: this unit *will* host a 5 GHz access point, the app just never gets to ask for a channel.
- **R4** needed no valid credentials for its actual question (is the resolver route open). URL built by hand with the shell-AP creds from the R3 control, per the brief's python. HU BT MAC `11:46:03:10:33:59` read straight from `dumpsys bluetooth_manager` — not masked on this unit.
- Standard rig facts held: WiFi Direct groups log `5GHz createGroup SUCCESS!`; groups are never reused (fresh SSID + interface index each run); `shared_prefs/` root-owned (all writes were root writes). `settings.xml` restored **byte-identical** (md5 `1ac92489f8cbba7d7ad56f413d8eeb59` before and after). No `settings.xml.bak` left. Candidate left installed on D-HU. D-POCO radios restored on, on its own build, bond intact. No lingering `logcat` processes.

---

## R0 — build gate

**PASS**

- `assembleGithubDebug` clean; `testGithubDebugUnitTest` **1113 tests / 0 failures / 0 errors** (measured from `test-results/testGithubDebugUnitTest/*.xml`, exact match to the brief's 1113/0).
- Per class, measured: `FiveGhzChannelPolicyTest` **7**, `WifiP2pOperatingChannelPolicyTest` **22**, `NativeGroupBandPolicyTest` **26**, `SoftApBandPolicyTest` **12**, `ProjectionQrPolicyTest` **9** — all exact.
- DEX symbol check on the built APK: `FiveGhzChannelPolicy` 3, `ProjectionQrPolicy` 10 (R4's marker), `WifiP2pOperatingChannelPolicy` 4, `NativeGroupBandPolicy` 9, `SoftApBandPolicy` 4 — all non-zero.
- Installed APK md5 on D-HU = `5e1c871dcf7b70c46b02e43498a2a955`, matches the build.

---

## R1 — the channel matrix. **This is the round.**

**PASS**

- Settings written each run: `wifi-connection-mode=3`, `native-ap-transport=0`, `wifi-direct-band=1`, `hotspot-band=1`, `wifi-5ghz-channel=<value>`, `static-bssid=0`, `log-level=1`, `native-wifi-version-exchange=false`.
- Radio state: D-POCO WiFi + Bluetooth **off** (verified `dumpsys`), so no session and no self-wake. D-HU only.
- Discard-rule check: **clean** — `createGroup SUCCESS` = exactly **1** in every run, `MATCH! Starting AapService` = **0** in every run, `p2p-wlan0-N` monotonic +1 per run (0 → 5), no second SSL handshake.

| `wifi-5ghz-channel` | log: frequency asked for | frequency arrived (`onGroupInfoAvailable` + `dumpsys wifip2p`) | ladder fired? | group formed? |
|---|---|---|---|---|
| **0** (automatic) | `5 GHz channel is automatic, so the driver picks within the band.` — **no `was asked for` suffix** | **5805 MHz** (UNII-3, ch 161) | no | yes, `p2p-wlan0-0` |
| **36** | `channel 36 (5180 MHz), asked for as a fixed 5180 MHz` | **5180 MHz**, `5180 MHz was asked for` | no | yes, `p2p-wlan0-1` |
| **40** | `channel 40 (5200 MHz), asked for as a fixed 5200 MHz` | **5200 MHz**, `5200 MHz was asked for` | no | yes, `p2p-wlan0-2` |
| **44** | `channel 44 (5220 MHz), asked for as a fixed 5220 MHz` | **5220 MHz**, `5220 MHz was asked for` | no | yes, `p2p-wlan0-3` |
| **48** | `channel 48 (5240 MHz), asked for as a fixed 5240 MHz` | **5240 MHz**, `5240 MHz was asked for` | no | yes, `p2p-wlan0-4` |
| **149** | `channel 149 (5745 MHz), asked for as a fixed 5745 MHz` | **5745 MHz**, `5745 MHz was asked for` | no | yes, `p2p-wlan0-5` |

The four deciding channels — **40, 44, 48, 149** — each arrived on exactly the frequency they named. The request reaches the radio. `dumpsys wifip2p` corroborates every one: `frequency: 5180/5200/5220/5240/5745` on the live group and matching `channelFrequency=` connection events, with `band=5, freq=5xxx` in the WifiP2p connection stats for the pinned runs vs `band=3, freq=0` (`GROUP_OWNER_BAND_5GHZ`) for the automatic run.

The automatic control did exactly what the brief said it would if the setting did nothing — except it did *not* land on 5180. It rolled onto **5805 MHz**, a UNII-3 channel many phone regulatory domains forbid: the invisible-channel hazard, reproduced live in the one run where the pin was off.

**No channel is refused by this unit.** The ladder (`createGroup retries exhausted`, `already refused by this unit`) fired 0 times across all six runs. All five offered channels host a group first try.

Sample (channel 44):
```
14:17:26.655 WifiDirectManager: Requesting Native AA P2P group on 5GHz band. Chosen by the user.
14:17:26.656 WifiDirectManager: 5 GHz channel is channel 44 (5220 MHz), asked for as a fixed 5220 MHz.
14:17:26.694 WifiDirectManager: 5GHz createGroup SUCCESS!
14:17:26.866 WifiDirectManager: onGroupInfoAvailable: SSID: DIRECT-LD-Navegadortz2, BSSID: B2:61:EF:14:91:1C (source=IPv6 link-local), GO: true, IFACE: p2p-wlan0-3, Freq: 5220 MHz (5GHz), 5220 MHz was asked for
```

---

## R2 — can the phone actually join it?

**PASS** — both channels.

- Settings: as R1 with `wifi-5ghz-channel` = 36 then 149.
- Radio state: D-HU launched first, settled 15 s, then D-POCO Bluetooth + WiFi enabled (`svc`). Session wait 100 s.
- Discard-rule check: **clean** — `createGroup SUCCESS` = 1 each, single `Handshake: SSL handshake complete` (D-line) each, `p2p-wlan0` +1 each (5→6, 6→7). `MATCH! Starting AapService` = 1 each — the phone's own Bluetooth reconnect after I re-enabled its radio, **zero group churn attached**, benign per §7a's refined rule.

| channel | HU group freq | D-POCO scan (`cmd wifi list-scan-results`) | `Connection accepted from` | `Incoming connection detected` | `SSL handshake complete` |
|---|---|---|---|---|---|
| **36** | 5180 MHz (`5180 MHz was asked for`) | `8a:1d:22:94:bf:72  5180  -22 dBm  DIRECT-RM-Navegadortz2` | yes, POCO X3 NFC (`DC:B7:2E:5E:4E:59`) | `from /192.168.49.227` | yes |
| **149** | 5745 MHz (`5745 MHz was asked for`) | `0a:eb:51:69:85:6b  5745  -24 dBm  DIRECT-JM-Navegadortz2` | yes, POCO X3 NFC | `from /192.168.49.117` | yes |

Both channels: the phone listed the SSID in its own scan at the pinned frequency, and formed a complete session through to SSL. The domain-split finding the brief was built to catch did not appear because **D-POCO's driver country is CO**, the same as D-HU, and CO permits the whole 36–165 list including UNII-3 (`dumpsys wifi` → `SupportedChannelListIn5g[...149,153,157,161,165...]`). On this device pair the setting is a convenience, not load-bearing; it would become load-bearing only against a phone whose domain refuses UNII-3.

---

## R3 — the access point

**INCONCLUSIVE**

- Settings: `wifi-connection-mode=3`, `native-ap-transport=1`, `hotspot-band=1`, `wifi-5ghz-channel=36`, `static-bssid=0`, `log-level=1`, `native-wifi-version-exchange=false`.
- Radio state: D-POCO WiFi on (for the scan), Bluetooth off.

What the app logged:
```
HotspotManager: Band preference is 5 GHz only, set by the user; trying 5 GHz.
SoftApConfigCompat: enableHotspot called (API=34)
SoftApConfigCompat: could not read the current access point configuration: null
SoftApConfigCompat: cannot read this device's access point configuration and no name is set in the app, so the band cannot be requested without risking the existing one. Starting the hotspot unconfigured.
HotspotManager: This device would not take a band request, so the access point is on whatever band it already had configured, which this app cannot read.
HotspotManager: Every start path was tried on 5 GHz and no access point came up within 6s each. On a non-privileged install this usually cannot be done from an app — switch the hotspot on in system settings instead.
```
`dumpsys wifi` afterwards: `Soft AP state is: false` — **no hotspot came up at all** from the app.

The brief's PASS condition 1 (channel-request line) and condition 2 (accepted/refused-vs-scan honesty check) are both **unreachable on this rig**: the code path that logs them is behind a successful `getSoftApConfiguration()` read, which returns null here. The app made **no** false claim — it explicitly logs that the band is "whatever it already had configured, which this app cannot read." So this is not the FAIL the honest-line rewrite was meant to prevent; it is a rig limitation. Coverage of the honest lines rests on `SoftApBandPolicyTest` (12) and `ProjectionQrPolicyTest` (9).

**Control (shell vs app):** `cmd wifi start-softap OHU5TEST wpa2 testtest1234 -b 5` → SoftAP up on `wlan2` at **5745 MHz** (`SoftApInfo{... frequency= 5745, bssid=00:27:15:43:06:6a}`), and D-POCO's scan confirmed `00:27:15:43:06:6a  5745  -26 dBm  OHU5TEST`. `cmd wifi force-softap-channel enabled 36` is **rejected by this ROM** ("Invalid argument ... must be a valid WLAN channel"); `force-softap-band enabled 5` is accepted but ACS lands on 5745 regardless. So the unit **does** host a 5 GHz access point — the limit on channel 36 specifically is (a) the app cannot read the config to modify it, and (b) even the privileged shell path on this ROM won't pin an arbitrary channel. Cannot separate "app refused" from "radio refused" for channel 36 here because the app never issues the request.

---

## R4 — is the QR provisioning route open at all?

**INCONCLUSIVE** (route exists at the resolver, closed at the experiment gate)

URL fired at D-POCO:
`https://androidauto.com/projection/setup?data=CghPSFU1VEVTVBIRMDA6Mjc6MTU6NDM6MDY6NmEaDHRlc3R0ZXN0MTIzNCIMMTkyLjE2OC40My4xKLMpMhExMTo0NjowMzoxMDozMzo1OTgI`
(SSID `OHU5TEST`, BSSID `00:27:15:43:06:6a`, passkey `testtest1234`, IP `192.168.43.1`, port `5299`, BT MAC `11:46:03:10:33:59`, security `WPA2_PERSONAL=8`).

1. **The resolver handled it — no browser fall-through.**
   ```
   ActivityTaskManager: START u0 {act=android.intent.action.VIEW dat=https://androidauto.com/...
     cmp=com.google.android.projection.gearhead/com.google.android.apps.auto.wireless.deeplink.DeepLinkResolver} ... result code=0
   ActivityTaskManager: Displayed com.google.android.projection.gearhead/...DeepLinkResolver for user 0: +267ms
   ```
2. **But the experiment gating it is off:**
   ```
   GH.QR: Experiment DEEP_LINK_ENABLED is disabled. Showing error.
   GH.ConnLoggerV2: ... QR_CODE_NOT_SUPPORTED ...
   ```
   The resolver drew an error screen, not a provisioning confirmation. One tap writes **no** record.
3. **No session** (`wifi-connection-mode=3`, no live hotspot) — expected, and not a FAIL for this round.

So the previously-unestablished default is established: **`DEEP_LINK_ENABLED` is disabled by default on Gearhead `17.5.663204`.** The branch's QR route is inert on this phone through no fault of the branch — the URL is well-formed and reaches the right activity. Whether other Gearhead builds enable the experiment is unmeasured.

---

## R5 — harvest

No runs repeated. Per-capture counts (`grep -ac`, all captures):

| capture | `createGroup SUCCESS` | `was asked for` | `MATCH! Starting AapService` | 2nd `Handshake: SSL handshake complete` |
|---|---|---|---|---|
| R1 ch0 | 1 | **0** (automatic — correct, no suffix) | 0 | 0 |
| R1 ch36 | 1 | 3 | 0 | 0 |
| R1 ch40 | 1 | 3 | 0 | 0 |
| R1 ch44 | 1 | 3 | 0 | 0 |
| R1 ch48 | 1 | 3 | 0 | 0 |
| R1 ch149 | 1 | 3 | 0 | 0 |
| R2 ch36 | 1 | 4 | 1 (phone BT reconnect) | 1 (the session) |
| R2 ch149 | 1 | 4 | 1 (phone BT reconnect) | 1 (the session) |
| R3 | 0 (no hotspot) | 0 | 0 | 0 |

`p2p-wlan0-N` union across R1–R3: `p2p-wlan0-0 … p2p-wlan0-7` — exactly 8 interfaces for 8 group bring-ups (6 R1 + 2 R2), monotonic, one per run. Expected, not a leak.

**BSSID source dump** (the `6f1ee214` passenger — "stop saying no source can read this unit's address"). Every R1 group printed one; ch40 shown:
```
== BSSID source dump (iface=p2p-wlan0-2) ==
  static override (Settings)       = 0
  getGroupOwnerBssid()             = null
  IPv6 link-local (p2p-wlan0-2)    = 36:43:6b:50:ad:77      <-- resolves
  IPv6 link-local (any p2p/ap)     = 36:43:6b:50:ad:77      <-- resolves
  NetworkInterface.hardwareAddress = 00:00:00:00:00:00      (masked)
  lastKnownBssid (null)            = null
  requestDeviceInfo                = 02:00:00:00:00:00      (masked)
  group.owner.deviceAddress        = 02:00:00:00:00:00      (masked)
  sysfs / ip link                  = 36:43:6b:50:ad:77      <-- resolves
  Settings.Secure p2p address      = null
  reflection over WifiP2pGroup     = null
== end BSSID source dump ==
```
The `IPv6 link-local` rung (and `sysfs / ip link`) resolve a real EUI-64 MAC on every group; the legacy MAC rungs are all masked or null. The rewritten "every source has been tried and none named an address" line fired **0 times** across the round, and `onGroupInfoAvailable` reports `source=IPv6 link-local` every time. The rewrite's premise holds on this unit.

---

## The three numbers that decide the shipping question

1. **R1's matrix:** 36→5180, 40→5200, 44→5220, 48→5240, 149→5745 — every pinned channel arrived on its named frequency; automatic rolled to 5805 MHz. **The pin reaches the radio.** `setGroupOperatingFrequency` works as the commit claims.
2. **Channels this unit refuses:** none. The ladder never fired. All five offered channels host a group first try.
3. **R2:** both 36 and 149 formed a full session; D-POCO's own scan saw each at the pinned frequency. The domain-split scenario is not testable on this rig (both devices country CO).

**What a PASS would have looked like if the change did nothing:** every run forming a healthy group, every log line naming the user's channel, and every group on 5180 MHz. Instead, four of the six pinned runs landed on 5200 / 5220 / 5240 / 5745 — frequencies this driver does not pick on its own (the automatic run proved that by rolling to 5805). The change does what it says.

## Secondary, not asked

- The automatic (`wifi-5ghz-channel=0`) bring-up landing on **5805 MHz** is a live demonstration of the bug the setting targets — that is a UNII-3 channel outside many phones' domains. This rig's own `mSafeChannelFrequencyList` is `5200,5745,5825,5220,5765,5240,5785,5180,5805`, i.e. the driver's group-owner roll genuinely spans UNII-1 and UNII-3.
- D-HU exposes its real Bluetooth MAC (`11:46:03:10:33:59`) to a non-privileged app — unusual, and it makes the QR route's `bluetooth_mac` field trivially fillable here even though R4 could not exercise the route end to end.
- `WppTcpServer: listening for Android Auto on TCP 5299` prints on every launch on the hotspot transport (seen in R3), independent of `native-wifi-version-exchange` — as the brief noted it would.
