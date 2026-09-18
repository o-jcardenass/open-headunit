# Headunit Reloaded "Wireless / WiFi Direct" two-phone connection — APK handoff

Not a hardware round. This is an inbound artifact from the coding session: the Headunit Reloaded
APK, committed so it can be decompiled and studied for how it does the thing our native mode
(wireless mode 3) currently cannot.

## What was reported

A tester connected **two phones** with Headunit Reloaded: one phone runs Reloaded in the head-unit
(receiver) role, the other phone runs Android Auto as the projecting device, and they link with no
car and no dedicated head unit hardware. In Reloaded's UI the connection type is called
**"Wireless"** and the transport method is **"WiFi Direct"**.

That is functionally our native AA wireless path (`Settings.wifiConnectionMode = 3`, "we
impersonate a real wireless AA head unit", see CLAUDE.md "Native AA wireless handshake"). Our
implementation does not currently complete a phone-to-phone connection; theirs does. The question
for whoever picks this up is **how Reloaded drives the Bluetooth RFCOMM handshake and the P2P group
bring-up differently from `NativeAaHandshakeManager` + `WifiDirectManager`**.

## The APK

- `evidence/headunit-reloaded-decompile/headunit-reloaded-v8.2.0-vc820.apk`
- Package `gb.xxy.hr`, versionName `Headunit Reloaded V8.2 .0`, versionCode `820`, minSdk 21,
  targetSdk 36. Single base APK, no splits.
- `sha256` `7ddcb31d76477efa7d35ff66a4cd175e6d7cf15630498aff7e0661c9179e35d2`, 6,656,605 bytes.
- Pulled with `adb pull` from the Motorola edge 30 neo (the USB-host test phone) on 2026-09-01.
- This is byte-identical to the APK analysed in `headunit-reloaded-decompile-findings.md`
  (same sha256, same versionCode 820). That pass looked only at the video/render pipeline and did
  not commit the APK. It is committed now so the wireless handshake can be examined without
  re-pulling.

## Suggested starting points for a decompile pass

The app keeps real class names only under its own top-level package `gb.xxy.hr` (everything else is
R8-obfuscated to single/double-letter packages). From the earlier pass, `TransporterService`,
`DispatcherActivity`, `MainActivity`, `UsbNative`, and a `proto/` package keep their names.

- `gb.xxy.hr.TransporterService` and anything it binds to: the connection orchestrator, our
  `AapService` equivalent.
- Grep the decompiled tree and the raw resources for the AA RFCOMM UUID
  `4de17a00-52cb-11e6-bdf4-0800200c9a66`, for `WifiP2pManager` / `createGroup` /
  `WifiP2pConfig` usage, and for the wireless handshake message type bytes (our
  `WifiStartRequest` type 1, phone type 2, `WifiInfoResponse` type 3).
- The `proto/` package: compare their wireless-handshake protobuf definitions to
  `app/src/main/proto/*.proto` and `aap/protocol/messages/`.
- Whether they run their own P2P group as Group Owner (like us) or join the phone's, what band
  they request, and how they time closing the Bluetooth listeners relative to the TCP session
  landing (our 3 s grace after type 3 is a known race).
- How they obtain the BSSID they hand the phone (our six-deep fallback chain exists because
  masked MACs make the phone reject credentials).

Full jadx output is not committed (thousands of files); regenerate with
`jadx -d src_out --show-bad-code headunit-reloaded-v8.2.0-vc820.apk` (jadx 1.5.6 was used before).
