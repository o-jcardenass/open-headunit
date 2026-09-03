# USB AOA path: dongle switch→open handoff findings

Date: 2026-09-03
Rig: Poco X3 NFC (M2007J20CG, Android 15, sm6150) running HUR `com.andrerinas.headunitrevived`
3.3.0 githubDebug, as the head unit / USB host (OTG). Wireless adb over `adb tcpip 5555`
(dongle occupies the only USB-C port).
Dongle: Carlinkit-class AA USB dongle, idle `18d1:4ee1` ("Google / Pixel 4",
serial 99021FFAZ006X3, single MTP/PTP interface), accessory mode `18d1:2d00`
(FF/FF/00 "Android Accessory Interface", bulk `0x81` IN / `0x02` OUT @ 512).
Proxies the AAP session to a phone (Motorola edge 30 neo, `A0:46:5A:97:E4:95`) over its own
radio + Bluetooth; **only answers AAP when a phone is associated over BT.**

## What works

End-to-end USB projection is solid once the session is up:

- version exchange: request 10B → response 12B (`00 03 00 08 00 02 00 01 00 07 00 00`,
  peer negotiates AAP **1.7**, status 0)
- SSL handshake ~60 ms
- Service Discovery "Android", negotiated 1920x1080@60, codec `c2.qti.avc.decoder` (HW H.264)
- steady state: ~50 fps decoded, **0 dropped / 0 skipped / 0 concealed**, decode latency
  11-13 ms (p95 14 ms), audio + media metadata flowing, stable for minutes
- `quiesceWirelessForWiredSession` correctly shut the wireless stack down for the USB session

Nothing to change in the transport / SSL / decoder path.

## The problem: switch→open handoff loses a race against fast-reverting dongles

The dongle holds AOA accessory mode only briefly if nothing claims its interface. Measured on
this run: **~390 ms** on the first attempt, ~1.3 s on the second. A real phone holds accessory
mode indefinitely; this dongle (and likely others) does not.

HUR's path from "ACC_REQ_START sent" to "interface claimed" is too long to win that race
reliably:

```
UsbAccessoryMode.switch()  ->  Thread.sleep(500), return
UsbAttachedActivity        ->  toast + finish()          [holds nothing open]
dongle re-enumerates 4ee1 -> 2d00                        (~400 ms)
OS delivers USB_DEVICE_ATTACHED  ->  cold activity launch OR service's UsbLauncherListener
permission check (2d00 is a fresh UsbDevice instance, no permission yet)
checkAlreadyConnected(force=true)  ->  usbOpen  ->  claimInterface  ->  transport
```

Timeline from the failing first attempt:

```
17:17:25.81  ACC_REQ_START sent
17:17:25.82  UsbAttachedActivity: toast + finish()
17:17:26.22  re-enumerated as 18d1:2d00
17:17:26.26  UsbLauncherListener.onUsbAttach -> checkAlreadyConnected(force=true)
             2d00 has no permission yet; usbOpen would wait 3x1s for the popup
17:17:26.61  dongle reverts to MTP  (~390 ms in accessory mode)
17:17:30.62  re-attaches as 18d1:4ee1
17:17:32.65  UsbLauncherListener fallback: "didn't handle... trying from service" -> switch #2
17:17:35.3   2nd USB permission grant (fresh 4ee1 instance = fresh permission)
17:17:37.09  switch #2's 2d00 held ~1.3 s, opened with permission, AapService starts
```

Net: two AOA switches, two permission prompts, ~11 s wasted, then a clean session. On a dongle
with a shorter revert timer, or a user who is slow to tap the second dialog, this can fail to
connect at all. Reporter-facing symptom: "USB dongle takes two tries / needs the cable replugged
/ two 'allow USB' prompts / sometimes never connects."

Why attempt 2 succeeded: by then the manifest auto-grant for `18d1:2d00`
(`usb_device_filter.xml` has `<usb-device vendor-id="6353" product-id="11520" />`) had taken
effect, so `UsbAttachedActivity.onCreate` saw `[permission]` immediately on cold launch, and the
dongle happened to hold accessory mode ~1.3 s that time. It is timing luck, not a guarantee.

## Improvements, prioritized

### 1. Close the switch→open gap (primary fix)

`UsbAttachedActivity.kt:168-178`: after `UsbAccessoryMode.connectAndSwitch` returns true, the
activity only shows a toast and `finish()`es. It throws away the head start.

Instead, the code that ran the switch should immediately enter a tight poll loop
(~100 ms interval, ~3 s budget) over `usbManager.deviceList` for a device where
`UsbDeviceCompat.isInAccessoryMode(it)` is true, and open + claim it directly the moment it
appears, bypassing the broadcast round-trip, the `finish()`/relaunch, and the
`ATTACH_FALLBACK_DELAY_MS` timer. Because `18d1:2d00` is in `usb_device_filter.xml`, the manifest
auto-grants device permission, so the poll should find it permissioned within a few hundred ms of
re-enumeration.

This is the difference between "wins reliably" and "wins only if the dongle's timer is generous."

Precedent for this style of recovery already exists: `UsbLauncherManager.kt:90-116`
(`MAX_STALE_ACCESSORY_RETRIES` → force AOA re-enumeration on repeated handshake failure).

### 2. `ATTACH_FALLBACK_DELAY_MS = 2000` is too coarse for the accessory re-attach case

`UsbLauncherManager.kt:301`. That 2 s delay exists for MediaTek units that don't deliver
`USB_DEVICE_ATTACHED` to activities on cold start. For a dongle that reverts in under a second, a
2 s fallback guarantees a first-cycle miss. The `isInAccessoryMode` branch in
`UsbLauncherListener.kt:34-37` already calls `checkAlreadyConnected(force=true)` immediately, but
if that fails on a missing permission there is no fast retry, so it falls through to the slow
detached path. Add a short bounded retry (e.g. 5 x 200 ms) in the accessory branch before giving
up to the fallback timer.

### 3. Wireless stack (incl. a BT HFP poke) arms before an already-attached USB device wins

The dongle was on the bus at `17:17:17.5`, before `AapService.onCreate` at `17:17:17.9`. The
service still brought up the WiFi Direct group, the port 5288 server, and a **Bluetooth HFP-AG
poke to the phone** (repeating `AT+CIND?` every 2 s, visible to the phone as a hands-free
connection), then ran `quiesceWirelessForWiredSession` to tear it all down at `17:17:37`.

When `usbManager.deviceList` is non-empty at service start (or a USB attach is in flight),
wireless bring-up, especially the BT poke, should be deferred until the USB path has had a chance
to win.

Caveat: partly a rig artifact here. The same phone is both the dongle's BT peer and paired to the
Poco, which is what fired `AutoStartReceiver` ("MATCH! Starting AapService via Bluetooth
Auto-start"). In a real car that head-unit-to-phone pairing would not exist. But the
unconditional WiFi Direct group create + 5288 server on every service start is real regardless.

### 4. Minor

- `UsbAccessoryMode.kt:110` `USB_TIMEOUT_IN_MS = 100` is aggressive for the AOA control transfers
  against a proxying dongle (all transfers completed in 1-2 ms here, but zero margin). Consider
  ~500 ms for `ACC_REQ_GET_PROTOCOL` at least.
- `StandardUsbProjectionConnection.initEndpoint` takes the first IN and first OUT endpoint on
  interface 0 without checking `usb.util.endpoint_type == BULK`. Fine for the 2-EP accessory
  interface, trivial to harden.
- `ACTION_USB_DEVICE_PERMISSION` and `USB_DEVICE_ATTACHED` are each delivered and handled twice
  per event (`UsbReceiver.onReceive` logs the duplicate). Double work + log noise.
- `accessory_filter.xml` (`<usb-accessory />`) exists in `res/xml/` but nothing in the manifest
  references it. Dead file, or an unfinished `USB_ACCESSORY_ATTACHED` path. HUR talks to the
  accessory as a `UsbDevice` (bulk transfers), not via `UsbManager.openAccessory()`, so wiring it
  up would be a separate API; noting only that the file is currently inert.

## libusb route (tested 2026-09-03, same rig)

`Settings.useLibusb` (`use-libusb`, default false) switches `UsbAccessoryMode.connectAndSwitch`
to `UsbNative.accModeSwitch()` (native AOA switch: one `wrap` + call, vs 8 Kotlin
`controlTransfer` JNI hops) and the connection to `LibusbProjectionConnection` (native JNI bulk
transfers) instead of `StandardUsbProjectionConnection`.

Both routes connect and run a clean session (~50 fps, 0 dropped/skipped/concealed, 12 ms decode).
Differences observed (one run each, so not conclusive):

| | Standard (`UsbManager`) | libusb (`UsbNative` + JNI) |
|---|---|---|
| AOA switch | 8 separate `controlTransfer` calls | one native `accModeSwitch`, ~9 ms |
| switches needed this run | 2 (dongle reverted once, ~11 s lost) | 1, dongle held |
| version handshake | response attempt 1, but "USB reads recovered after 1 errors" | response attempt 1, clean, ~13 ms |
| switch → first frame | ~24 s (incl. double-switch) | ~3.8 s |
| 2d00 re-enum permission | manifest auto-grant | explicit `checkAlreadyConnected` request + grant (new log line: "Accessory-mode device has no permission (re-enumerated); requesting permission") |

The single-vs-double switch is partly dongle timing variance (accessory-mode patience was ~390 ms
one run, ~1.7 s the next). But the native switch is genuinely tighter and leaves less window for
the dongle to bail, and native bulk transfers have different timeout/retry semantics than
Android's `bulkTransfer`. Worth a second run each to confirm; the libusb route looks at least as
good and plausibly more robust for fast-reverting / marginal-link dongles. Not shipped as default.

## Relation to issue #800 (random disconnects with wireless USB AA dongle)

Issue: <https://github.com/andreknieriem/open-headunit/issues/800> (closed wontfix).
Log analysed: s4toruu-x's `HUR_Log_20260901_135255_327.txt` (comment 5494289760).

Setup: aliexpress head unit, `board=trinket api=30` (Android 11, low-end Qualcomm), dongle spoofs
"Nothing TUNA-QRD_SN:A262FD58" (`18d1:4ee1` → `2d00`), HUR 3.3.0, negotiated 1280x720@60 HW
H.264, TEXTURE view.

Pattern in the log (one cycle captured):
- session runs badly: `TextureProjectionView: displayed 140 frames in 628527ms (0fps)` = 10.5 min
  of near-zero video
- `13:49:28` `USB_DEVICE_DETACHED` -> HUR reconnects, recovers frames in ~5 s
- `13:49:33-48` video 10-27 fps (should be 60), then `13:49:53` collapses to ~0
- `13:50:00` `AapTransport inbound rate: video=27kB/s, audio=118kB/s` -> **audio keeps flowing,
  video does not**
- `13:50:05` onward: `reportIdlePicture | picture idle ... but the link spoke 35ms ago - Android
  Auto has stopped sending, not disconnected`; ~90 s of "connected but no frames", HUR sends
  unsolicited `VideoFocusEvent(gain=true)` every 2 s, phone ignores all
- `13:51:24` another `USB_DEVICE_DETACHED` -> full disconnect

Root cause: the head unit's USB 2.0 link is electrically marginal. Conclusive evidence:
1. The reporter's own fix: an OEM "Force USB 1.1" setting -> "never fails again" (just lower fps).
   USB 1.1 FS (12 Mbps, 3.3 V) is far more tolerant of bad cable/connector/EMI than USB 2.0 HS
   (480 Mbps, 400 mV).
2. Disconnects are physical `USB_DEVICE_DETACHED`, not software.
3. Small bulk transfers (audio, 118 kB/s) survive while large ones (video) collapse - the classic
   marginal-HS-link signature.

**Our handoff findings do not address the root cause and neither can HUR** - andreknieriem's
wontfix is correct for the disconnects themselves. What our work does touch:

- Findings #1 (fast switch→open) and the libusb route reduce the ~5 s black screen this reporter
  eats on every reconnect. Palliative. `use-libusb=true` is a one-tap thing worth them trying.
- **The ~90 s "connected, audio flowing, zero video, focus requests ignored" zombie window is a
  HUR gap** distinct from the handoff findings: none of the recovery escalations
  (`maybeRequestVideoFocus`, `maybeRecoverWarmRelaunch`, `maybeRecoverFromDisplayStall`) force a
  **transport restart** when the link is half-alive (control+audio up, video dead) and the
  surface is stable (so the warm-relaunch path, gated on `lastSurfaceSetMs`, never fires). A
  bounded escalation - "N unsolicited focus gains over M seconds with zero video bytes while the
  link is otherwise healthy -> restart the transport" - would at least give AA a chance to
  re-setup the video sink instead of waiting for the USB stack to drop. Speculative benefit on a
  marginal link, and a recovery mechanism for a hardware problem, so low priority / may not be
  wanted.

Config advice for the reporter (no code): drop `fps-limit` to 30 and/or lower `resolutionId` -
cuts video bulk-IN bandwidth, may stay under the marginal link's reliable ceiling on USB 2.0
without the USB-1.1 fps penalty.

Note: Dennis-NL commented "was fixed, regressed" - if pursued, bisect
`StandardUsbProjectionConnection` read-timeout / retry tuning across releases. Not chased here.

## Test harness notes

- Wireless adb: `adb tcpip 5555` while on cable, then `adb connect <poco-wlan-ip>:5555`. Survives
  the cable being pulled; the dongle-to-phone link is a separate radio so the Poco's wifi stays
  free. Poco was on `192.168.1.10`, PC `192.168.1.11`.
- HUR log level: `log-level` in `shared_prefs/settings.xml`, ordinal into
  `LogExporter.LogLevel` (VERBOSE=0, DEBUG=1, INFO=2, WARNING=3). Must be 0 to get the handshake
  detail on logcat; `AppLog` gates `v()`/`d()` before they reach logcat. Set with the app
  force-stopped. Does NOT need `log-capture-enabled` (that spawns an in-app logcat and triggers
  the SystemUI log-access dialog); a plain `adb logcat` gets everything.
- The dongle only answers AAP when a phone is associated to it over Bluetooth; with no phone it
  answers the AOA control requests then powers its USB side down after a few minutes.
- PC-side AOA probe + AAP version-request tool `aoa_probe.py` (`list` / `probe VID:PID` /
  `aap [VID:PID]`) lives with the coding session, not on this branch. It drives the dongle from a
  Linux host: enumerates descriptors, replays the AOA control sequence (`usbhelper.c`'s), and on
  the switched `2d00` device sends `Messages.versionRequest` (`00 03 00 06 00 01 00 01 00 02`) and
  parses the `VERSION_RESPONSE`. Needs `pyusb` + `libusb-1.0`. The dongle only answers AAP past
  the switch when a phone is BT-associated to it.
- This rig ran with the dongle in the **Poco X3 NFC's** USB-C port (OTG), the Poco as head unit,
  the **Motorola edge 30 neo** as the projecting phone over the dongle's own radio/BT. No cable
  for adb during the session, so wireless adb is mandatory for any USB round on this hardware.
