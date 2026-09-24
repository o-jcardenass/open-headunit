# bssid-from-interface-address round 1 — addendum: a successful two-phone run, logged on both sides

Follow-up to `bssid-round1-results.md`. R2/R3 could not get OHU past the two-phone Bluetooth stage
(`WIRELESS_SETUP_FAILED_TO_START_NO_HFP_FROM_HU_PRESENCE`). This addendum captures **Headunit
Reloaded V8.2.0** (`gb.xxy.hr`, sha256 `7ddcb31d…`, the APK on this branch) doing the same
phone-to-phone WiFi-Direct connection **successfully**, on the same rig, logged on both sides, so the
coding session can see exactly what Reloaded does that OHU does not.

**Rig:** D-MOTO (moto edge 30 neo) as head unit running Reloaded → Wireless; D-POCO (POCO X3 NFC)
projecting, Gearhead 17.5.663204. Reloaded's BT permissions granted via `pm grant`; its "Wireless"
sheet was opened and "POCO X3 NFC" tapped (its equivalent of OHU's poke). Full logs:
`evidence/bssid-round1/reloaded-successful-run.txt`; raw captures `reloaded-dmoto.txt` /
`reloaded-dpoco.txt` kept locally.

---

## The connection, both sides

**D-MOTO — Reloaded's own log (`HUR-*`):**

```
06:06:18.169  HUR-A2dpNudge:  Poking DC:B7:2E:5E:4E:59 (RFCOMM connect to A2DP source)...
06:06:18.721  HUR-A2dpNudge:  RFCOMM up to DC:B7:2E:5E:4E:59 - waiting up to 30s for Android Auto
06:06:18.944  HUR-WirelessBT: Phone connected over RFCOMM, starting handshake
06:06:18.998  HUR-WirelessBT: WifiVersionResponse: status=STATUS_SUCCESS serial=4f4027e9
06:06:19.070  HUR-WirelessBT: WifiInfoRequest -> sending creds: ssid=DIRECT-Cz-moto edge30 neo_oThE
                              key=(8 chars) bssid=DE:B3:88:55:B3:92 security=WPA2_PERSONAL ip=192.168.49.1:5288
06:06:20.075  HUR-WirelessBT: WifiStartResponse: status=STATUS_SUCCESS - phone should now open the projection socket
06:06:23.512  HUR-WirelessBT: WifiConnectStatus: STATUS_SUCCESS hint=
06:06:23.889  HUR-WirelessBT: VideoFocus acquired - dropping dummy HFP (bootstrap complete)
06:06:23.890  HUR-WirelessBT: HFP accept ended: java.io.IOException: read failed ...
```

**D-POCO — Gearhead 17.5, same seconds (`NO_HFP_FROM_HU_PRESENCE` = 0 for the whole run):**

```
06:06:17.914  BluetoothAdapter(gearhead): HEADSET connected
06:06:17.914  BluetoothAdapter(gearhead): A2DP connected
06:06:18.010  GH.WifiBluetoothRcvr: CONNECTION_STATE_CHANGED XX:E4:95 ... has state 1
06:06:18.050  GH.WifiBluetoothRcvr: CONNECTION_STATE_CHANGED XX:E4:95 ... has state 2       <- HFP stays CONNECTED
06:06:18.146  GH.WIRELESS.BT:  Creating rfcomm socket for device: A0:46:5A:97:E4:95 and uuid: 4de17a00-52cb-11e6-bdf4-0800200c9a66
06:06:18.232  GH.WPP.CONN:     Creating the transport in order to start listening
06:06:18.257  GH.WIRELESS.SETUP: WPP version: 1.0
06:06:18.371  ConnLoggerV2:    WIRELESS_WPP_VERSION_RESPONSE_SUCCESS
06:06:22.541  GH.WirelessFSM:  Launch projection 192.168.49.1 5288
06:06:23.254  GH.GhLifecycleService: onProjectionStart ...
```

D-MOTO ended on `gb.xxy.hr/.activities.Player`, D-POCO on the Maps projection surface — a live
session.

---

## What Reloaded does that OHU does not

| | OHU mode 3 (candidate `e6b19c3a`) | Headunit Reloaded V8.2.0 |
|---|---|---|
| poke target | HFP-AG (`0000111f`) then HSP-AG (`00001112`) | **A2DP source** (`HUR-A2dpNudge`, RFCOMM) |
| poke hold | ~15 s, then drops and re-pokes on a loop | **30 s, held continuously through the whole handshake** |
| dummy HFP record | registered on a phone; **dropped on the 15 s timer** | registered; **dropped only after `VideoFocus acquired`** ("bootstrap complete") |
| result on this rig | HFP connect flaps / fails; Gearhead → `NO_HFP_FROM_HU_PRESENCE` ×3–8 per run; 0 `Connection accepted` in 3 attempts | HEADSET **and** A2DP go to state 2 and stay; Gearhead dials the AA RFCOMM in 0.2 s; session in ~5 s |
| BSSID handed to the phone | `DE:B3:88:55:B3:92` (via the new IPv6 link-local route) | **`DE:B3:88:55:B3:92`** — identical |

**Two takeaways for the coding session:**

1. **The BSSID fix is correct.** Reloaded, connecting successfully to the same phone, hands it the
   exact address `DE:B3:88:55:B3:92` that OHU's new `Eui64BssidPolicy` route derives for this P2P
   group. Independent confirmation that the derived MAC is the BSSID the group actually uses — the
   thing round 1 §12 said it could not settle.

2. **The two-phone blocker is the poke, not the credentials.** Gearhead 17.5 will not start wireless
   setup unless it sees a Bluetooth profile (HFP or A2DP) from the head unit *connected and holding*.
   OHU's `triggerPoke()` opens an HFP-AG socket for 15 s and lets it drop; on two phones that
   connection is flaky and short, so `WIRELESS_SETUP_FAILED_TO_START_NO_HFP_FROM_HU_PRESENCE` fires
   before the RFCOMM handshake is ever attempted. Reloaded nudges A2DP, keeps the link up for a full
   30 s, and only tears its dummy HFP down once video focus is acquired. Matching that — a longer,
   held poke that stays connected across the handshake, and tearing down the stand-in records on the
   session-up signal rather than a timer — is very likely what closes the phone-to-phone gap
   (`NativeHandoffPolicy` / `triggerPoke` hold duration in `NativeAaHandshakeManager.kt`). This is
   plausibly one of the "four things" in `headunit-reloaded-wireless-findings.md`.

---

## Rig state after

Reloaded was already installed (pre-existing); refreshed to the same version and left. Its runtime
BT permissions were granted (`BLUETOOTH_SCAN/CONNECT/ADVERTISE`, `NEARBY_WIFI_DEVICES`). OHU
candidate (`176addb2…`) still installed on D-MOTO and D-HU; `settings.xml` on both was already
restored byte-identical in round 1. D-POCO: radios on, no OHU app.
