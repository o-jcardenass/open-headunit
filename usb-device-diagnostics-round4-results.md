# usb-device-diagnostics — round 4 results

**Candidate:** `fork/feat/usb-device-list-diagnostics` — tested at `a9df2135` (7 commits on `main`
@ `4849903d`). **Branch was squashed to 2 commits mid-round**: new tip `9b85bcb4`
(`1c863598` "identify a phone by its descriptors, and let the user veto a device" +
`9b85bcb4` "stop Android offering us as the handler for every device on the bus"). `git diff
a9df2135 9b85bcb4` is **comment/KDoc-only** (one word in `UsbDeviceIdentityPolicy` KDoc, KDoc
rewrap in `UsbBlacklistPolicy`) — no logic change, so every result below applies to `9b85bcb4`
unchanged.
**Baseline:** none. Round 3 (`bb56a286`) is the before-state; its captures are in
`evidence/usb-device-diagnostics-round3/`.
**APK md5:** `c3bfdd6aee5dc61ca1b34d66b9bb4975` (`3.3.0-beta4`, versionCode from `defaultConfig`)
**Host (USB host):** motorola edge 30 neo, Android 14, over wireless adb. Second phones: Xiaomi
POCO X3 NFC (serial `4f4027e9`) and Samsung (`SAMSUNG_Android`, `04E8:6860`). Dock =
ASIX AX88179B ethernet + Fresco Logic billboard + Generic card reader on one hub; BT dongle =
UGREEN BT701 (`0A12:4007`).
**Date:** 2026-08-31

## Result summary

| Run | Verdict |
|---|---|
| R0 build gate | **PASS** |
| R1 blacklisted device refused on every path | **PASS** |
| R2 device list refuses its own start button | **PASS** |
| R3 key survives a USB mode change | **PASS** |
| R4 unprefixed (old-build) entry still matches | **PASS** |
| R5 mirror applies before the user unlocks | **FAIL** |
| R6 regression — round 3 verdicts unmoved | **PASS** |
| R7 permission prompts per connect (measurement) | **INCONCLUSIVE** |

## Setup notes

- **Wireless adb port changed twice.** The moto dropped off wireless adb once mid-round (right
  after the R2 teardown, cause not established — WiFi blip or the rapid re-enumeration) and once
  deliberately at the R5 reboot. Ports: `38729` → `40867` → `39739`. Each time the operator
  re-enabled Wireless debugging and read the new port off the phone.
- **The moto's own `dumpsys usb` broke after heavy re-plugging** — `java.lang.IllegalArgumentException`
  in `com.android.server.usb.descriptors.UsbDescriptorParser.parseDescriptors` (a stuck
  `ConnectionRecord` for a malformed descriptor). ROM-side, not OHU. Cleared on the R5 reboot. Read
  `dumpsys usb` for R7 only after that reboot.
- **The `Ignoring USB device attached in service` log line reports the wrong reason for a
  blacklisted device.** `UsbLauncherListener.onUsbAttach` (line 25) logs
  `UsbDeviceCompat.matchReason(device)`, which is the identity-policy verdict, not the blacklist
  verdict. For a blacklisted-but-otherwise-valid phone it prints
  `Ignoring USB device attached in service (VID: 1478): accepted: ADB` — the device is still
  refused (the method returns without acting), but the line says "accepted". The brief's R1 table
  expects `… rejected: blacklisted by the user` here; that string never appears on this path. The
  authoritative blacklist string (`rejected: blacklisted by the user`) does appear, in the
  `UsbDiagnostics:` dump on the service-scan / USB-button / attach callers.
- **Charging-only mode on this Poco still enumerates as `18D1:4EE1` with `if0 06/01/01`** — it does
  not present an empty config. "PTP" and "file transfer" both also present `06/01/01` on this
  Xiaomi (not `FF/FF/00`-named-MTP); `matchReason` labels all of them `accepted: PTP`.
- **The blacklist verdict is read only from the device-protected mirror**
  (`Settings.isUsbDeviceBlacklisted(context, device)` → `createDeviceProtectedStorageContext()`),
  never from `settings.xml`. Editing `settings.xml` alone changes nothing; the app must be launched
  once so `App.onCreate` (line 62, inside the `isUserUnlocked()` guard) copies the list across.
  Confirmed by reading `/data/user_de/0/<pkg>/shared_prefs/settings_device_protected.xml` via
  `run-as` after every write.
- **Scripts.** Existing: `build_hur.sh` + `run_unit_tests.sh` (R0), `set_pref.sh` (scalar keys).
  **Added `hur-wifi-test-scripts/set_usb_blacklist.sh`** — the earlier `set_pref.sh` /
  `set_prefs_runas.sh` only handle scalar `<boolean>/<int>/<string>`; the blacklist is a
  `<set name="usb-blacklist">` string-set and the on-device inline `sed` route mangles the
  attribute quotes (`name="usb-blacklist"` → `name=usb-blacklist`, invalid XML, whole prefs file
  then reverts to defaults on next load — hit this once, recovered from the backup). The new script
  pulls `settings.xml`, edits it on the host with python (real quotes), pushes it back via
  `run-as cp`. Round capture helper: `round-usb-device-diagnostics-r4/cap.sh`.
- **Persistent USB grants were created during R2 and left on the rig** (see R7). The operator
  ticked "always" on an R2 permission dialog; this writes a persistent, serial-keyed grant that
  `pm clear` and "Clear defaults" do not touch. Clearing needs an uninstall (§7b) — not done.
- Settings restored **byte-identical** to `moto-settings-backup.xml` at the end
  (`diff <(sort backup) <(sort live)` clean); device-protected mirror re-synced to the original
  `0a12:4007` entry. Candidate APK left installed (`adb install -r`, never uninstalled).

## R0 — build gate

**PASS**

- `./gradlew assembleGithubDebug` → `BUILD SUCCESSFUL in 3m 26s`
- `./gradlew testGithubDebugUnitTest` → **984 tests, 0 failures, 0 errors, 0 skipped** (exact,
  brief predicted 984/0)
- `UsbBlacklistPolicyTest` = **11** (new, brief predicted 11)
- `UsbDeviceIdentityPolicyTest` = **20** (brief predicted 18→20)

## R1 — a blacklisted device is refused on every path that acts

**PASS**

- Settings written: `usb-blacklist` = `{name:xiaomi poco x3 nfc}`, `log-level` = `0`,
  `wifi-connection-mode` = `1` (temporary, to start `AapService` via a WiFi Auto scan for the
  service-scan/attach-listener paths; restored to `2` after).
- Radio state: n/a (USB round). Poco plugged with USB debugging **on** so the attach path fires
  on this host (a debugging-off Poco is claimed by `com.android.mtp` and `UsbAttachedActivity`
  never launches — §7b).
- Discard-rule check: clean. The two `beginAutoConnect` lines in the capture name
  `"manual WiFi headunit server scan"` and `"manual USB list (AOA switch)"` (the latter is R2's
  un-blacklisted reconnect) — neither names the blacklisted phone.
- Self-check string present: `rejected: blacklisted by the user` (the key was written correctly).
- Poco enumerated as **`05C6:90DB`** here — a VID:PID that was never blacklisted; matched by the
  `name:xiaomi poco x3 nfc` key.

Decisive lines (Poco `05C6:90DB`, blacklisted):

| Path | Line |
|---|---|
| USB attach | `20:55:31.744 UsbAttachedActivity.onCreate \| UsbAttachedActivity: Ignored blacklisted USB device (VID: 0x05C6, PID: 0x90DB)` |
| service scan | `20:55:27.324 UsbDiagnostics: USB list sees 1 USB device(s), 0 usable for Android Auto` + `UsbDiagnostics:   Xiaomi POCO X3 NFC (VID: 05C6 PID: 90DB) [class 00/00/00, no permission] rejected: blacklisted by the user \| if0 FF/FF/30 … if5 FF/42/01` |
| attach listener | `20:55:27.326 UsbLauncherListener.onUsbAttach \| Ignoring USB device attached in service (VID: 1478): accepted: ADB` — device refused (method returns), **but logs the identity reason, not the blacklist reason** (see Setup notes) |
| USB button | list screen opened, Poco row present and labelled **Blacklisted** (uiautomator: `android:id/button1` text `"Blacklisted"`); **no** `USB button: Single device found`, **no** `beginAutoConnect` |

Re-verified with `AapService` confirmed running (`ServiceRecord{…AapService}`) and a fresh
unplug/replug at `20:56:56`: same three lines, `0 usable`.

FAIL check: **zero** `beginAutoConnect` / `connectAndSwitch` / `Switching USB device to accessory
mode` / `Requesting USB permission` naming the Poco, across the whole capture.

## R2 — the device list refuses its own start button

**PASS**

- Settings: `usb-blacklist` = `{name:xiaomi poco x3 nfc}` (from R1).
- Start button pressed on the blacklisted Poco (`android:id/button2`, the device-name row):
  **"Blacklisted" toast** shown (screenshot `r2_start_blacklisted_toast.png`; a `ty=TOAST` window
  from `pkg=com.andrerinas.headunitrevived` at `20:59:37.087`, dismissed `20:59:39.671`).
  **Zero** `connectAndSwitch` / `Switching USB` / `beginAutoConnect` / `Requesting USB permission`
  in the log; bus unchanged (Poco not re-enumerated to accessory mode).
- Allow toggle pressed once: row went **Blacklisted → Ignored**; `usb-blacklist` emptied to
  `<set name="usb-blacklist" />` in **both** `settings.xml` and the device-protected mirror
  (mirror updated live via `storeUsbBlacklist` → `syncUsbBlacklistToDeviceStorage`).
- Start button pressed again (Poco now un-blacklisted): connects normally —
  `21:00:19.052 UsbAccessoryMode.connectAndSwitch \| Result: true` →
  `21:00:19.063 beginAutoConnect \| Auto-connect: begin (manual USB list (AOA switch)…)` →
  Poco re-enumerated `18D1:2D01` `accepted: already in accessory mode` →
  `Requesting USB permission for … (VID: 18D1 PID: 2D01)` → operator allowed →
  `21:01:02.247 AapSslContext.performHandshake \| SSL handshake complete` →
  `21:01:03.844 VideoDecoder … First frame rendered (hardware decode)` →
  `21:01:08.799 Throughput over 5001ms: rendered=212 (42fps), fed=214 (42fps), dropped=0`,
  foreground `AapProjectionActivity`.

Un-blacklisting is not a one-way door — the device came all the way back to a full USB projection
session.

## R3 — the key survives a USB mode change

**PASS**

- Settings: `usb-blacklist` = `{name:xiaomi poco x3 nfc}` (re-added after R2, re-mirrored).
- Verdict read via the USB-button `UsbDiagnostics:` dump for each mode. Modes the Poco produced,
  with the VID:PID it presented and the verdict:

| Mode (Poco) | VID:PID | Interfaces | Verdict |
|---|---|---|---|
| **already in accessory mode** | `18D1:2D01` | `FF/FF/00` + `FF/42/01` | `rejected: blacklisted by the user` |
| file transfer, USB debugging **on** | `05C6:90DB` | 6× `FF/FF/*` + `FF/42/01` | `rejected: blacklisted by the user` |
| file transfer, USB debugging **on** (alt enum, same replug) | `18D1:4EE2` | `06/01/01` + `FF/42/01` | `rejected: blacklisted by the user` |
| file transfer, USB debugging **off** | `18D1:4EE1` | `06/01/01` | `rejected: blacklisted by the user` |
| PTP, USB debugging **off** | `18D1:4EE5` | `06/01/01` | `rejected: blacklisted by the user` |
| charging only | `18D1:4EE1` | `06/01/01` | `rejected: blacklisted by the user` |

**6 distinct VID:PIDs, one blacklist entry (`name:xiaomi poco x3 nfc`), same verdict every time.**
The accessory-mode arm — the one the old VID:PID key missed outright, and which the service scan
checks first — is `rejected: blacklisted by the user` at `21:10:43.243`:
`Xiaomi POCO X3 NFC (VID: 18D1 PID: 2D01) [class 00/00/00, permission] rejected: blacklisted by
the user`. No auto-connect, no permission request on any mode.

USB debugging **off** did not suppress the attach path here (contrary to the §7b expectation): at
`21:14:16.614` `UsbAttachedActivity.onCreate \| UsbAttachedActivity: Ignored blacklisted USB
device (VID: 0x18D1, PID: 0x4EE1)` fired for the debugging-off `4EE1` enumeration too. A
persistent grant already existed for that identity (`[class 00/00/00, permission]`), which changes
AOSP's `resolveActivity` path.

## R4 — an entry from an older build still matches

**PASS**

- Settings: `usb-blacklist` = `{0930:6545, 18d1:4ee1}` — bare `vvvv:pppp`, no `name:` / `vidpid:`
  prefix (the old-build format). Both mirrored.
- Poco in charging / file-transfer mode → enumerates `18D1:4EE1` →
  `21:17:41.342 UsbDiagnostics: USB button sees 1 USB device(s), 0 usable for Android Auto` +
  `Xiaomi POCO X3 NFC (VID: 18D1 PID: 4EE1) [class 00/00/00, permission] rejected: blacklisted by
  the user`. The read-leniency `legacy` branch in `UsbBlacklistPolicy.isBlacklisted` matches the
  bare entry.
- Negative check (the flip side — bare entries are exact, so an old list "degrades visibly"):
  Poco switched to **PTP** → enumerates `18D1:4EE5` →
  `21:18:40.577 Xiaomi POCO X3 NFC (VID: 18D1 PID: 4EE5) [class 00/00/00, no permission]
  accepted: PTP` + `21:18:42.601 UsbLauncherManager.requestPermission \| Requesting USB permission
  for … (VID: 18D1 PID: 4EE5)`. The bare `18d1:4ee1` does not match `4ee5`; the phone reappears as
  connectable rather than being silently covered — which is exactly why the name-keying of
  `1c863598` was needed.

## R5 — the mirror applies before the user unlocks

**FAIL** — screen lock was already set (PIN + fingerprint), so this is not UNTESTABLE.

- Settings: `usb-blacklist` = `{name:xiaomi poco x3 nfc}`, mirrored. Verified the device-protected
  mirror carried the entry before the reboot.
- Procedure: moto rebooted; Poco (file transfer) plugged in **at the lock screen, before any
  unlock** (`isUserUnlocked()` == false, confirmed by the crash text below); held ~20 s; then
  unlocked. Wireless adb was down during BFU, so the window was recovered from `logcat -b all`
  afterwards (`-b events`/`-b system`/`-b crash` survived; the `-b main` buffer had already rolled
  past the window — moto ROM spam).
- **Expected** (brief): `UsbAttachedActivity: Ignored blacklisted USB device` line, and no AOA
  switch. **Got:** no such line — the process crashes in `Application.onCreate` before
  `UsbAttachedActivity.onCreate` runs.

Decisive lines (`evidence/…/r5_bfu_crash.log`, `r5_bfu_events_filtered.log`):

```
21:31:46.620  wm_create_activity  … UsbAttachedActivity, USB_DEVICE_ATTACHED
21:31:46.636  am_proc_start       … com.andrerinas.headunitrevived … UsbAttachedActivity  (pid 4181)
21:31:46.913  E AndroidRuntime(4181): FATAL EXCEPTION: main
              java.lang.RuntimeException: Unable to create application com.andrerinas.openheadunit.App:
              java.lang.IllegalStateException: SharedPreferences in credential encrypted storage are
              not available until after user is unlocked
                at com.andrerinas.openheadunit.utils.Settings.getPrefs(Settings.kt:36)
                at com.andrerinas.openheadunit.utils.Settings.getDebugForceMemoryProfile(Settings.kt:402)
                at com.andrerinas.openheadunit.AppComponent$videoDecoder$1.invoke(AppComponent.kt:20)
                at com.andrerinas.openheadunit.decoder.video.VideoDecoder.<init>(VideoDecoder.kt:323)
                at com.andrerinas.openheadunit.AppComponent.<init>(AppComponent.kt:19)
                at com.andrerinas.openheadunit.App$component$2.invoke(App.kt:24)
                at com.andrerinas.openheadunit.App.getComponent(App.kt:23)
                at com.andrerinas.openheadunit.App.onCreate(App.kt:46)
21:31:46.916  wm_finish_activity  … UsbAttachedActivity, force-crash
21:31:48.180  wm_create_activity  … UsbAttachedActivity  (second enumeration)
21:31:48.483  am_crash            … same IllegalStateException  (pid 4204)
```

- **3 crashes this boot** for the package: one at `21:29:37` (pre-plug — a boot receiver:
  `BootCompleteReceiver` / `AutoStartReceiver`), two on the plug enumerations.
- **Root cause:** `App.kt:46` `component.suExecutor.register()` forces the lazy `AppComponent`
  **before** the `if (isUserUnlocked())` guard on line 48. Constructing `AppComponent` builds a
  `VideoDecoder`, whose constructor reads `settings.debugForceMemoryProfile` (`Settings.kt:402` →
  credential-encrypted prefs), which throws at BFU.
- **This is pre-existing on `main`**, not a round-4 change: `4849903d` has the identical
  `App.kt:46`; the `debugForceMemoryProfile` read in the `AppComponent` video-decoder lambda came
  from `a3990e4c` ("Video: rebuild the projection pipeline from the stream…"), not from any of the
  USB commits. `1c863598`'s 3 added `App.kt` lines are all inside the `isUserUnlocked()` guard.
- **Net effect:** the blacklisted device was **not** connected to — zero `AapService.onStartCommand`
  / `Switching USB device to accessory` / `connectAndSwitch` all boot, and the bus never showed an
  accessory-mode re-enumeration. But that is because the whole app crash-loops at BFU, **not**
  because the `58a7398e` mirror check refused it. `1c863598`'s headline "survives a locked boot"
  is not realized on any device that actually reaches the before-first-unlock state: the
  device-protected mirror, the `directBootAware` receivers and the auto-start-on-boot path are all
  pre-empted by the `App.onCreate` crash.

Fix is a `main` change, not this branch's: guard the DI-graph access (`component.…`) behind
`isUserUnlocked()`, or make the `VideoDecoder` memory-profile lambda lazy / null-tolerant at BFU.

## R6 — regression, round 3's verdicts have not moved

**PASS**

- Settings: `usb-blacklist` = `{}` (cleared, mirror cleared, `<set name="usb-blacklist" />`,
  valid XML confirmed).

| Arm | Device / enum | Line | Verdict |
|---|---|---|---|
| adapter, composite | `ASIX AX88179B 0B95:1790 [class 00/00/00]`, 7 ifaces incl. `if1 02/0D/00` (MBIM), `if4 02/06/00` (ECM) | `rejected: CDC network adapter` | matches R3 |
| adapter, vendor-only | `ASIX AX88179B 0B95:1790 [class FF/FF/00]`, `if0 FF/FF/00 (1xbulkIn+2xbulkOut+1 other)` | `rejected: vendor-class device, not a phone` | matches R3 |
| billboard | `Fresco Logic Generic Billboard Device 1D5C:7102 [class 11/00/00]`, `if0 11/00/00 (no endpoints)` | `rejected: device class 0x11 is not a phone` | matches R3 |
| second phone | Poco `18D1:4EE1` (file transfer), then `18D1:2D00` (accessory) | `accepted: PTP` / `accepted: already in accessory mode` | matches R3 |
| BT dongle alone | `UGREEN BT701 0A12:4007 [class 00/00/00]`, HID+audio | `rejected: no Android interface`; **zero `systemui.usb.*`** in `-b events`/`-b system` since the plug; operator confirmed no dialog | matches R3 |
| empty bus | — | `UsbDiagnostics: … sees 0 USB device(s)` + `nothing is on the bus…` advisory, **both at `I/` (INFO)**, no exception | matches R3 |
| **Samsung, file transfer, debugging off** (`ad082fff` arm) | `SAMSUNG SAMSUNG_Android 04E8:6860 [class 00/00/00]`, `if0 06/01/01` + **`if1 02/02/01` (CDC ACM control)** + `if2 0A/00/00` (CDC data) | `accepted: PTP` | ✅ |

The adapter's two enumerations were both captured in one session — UsbHostManager shows it
alternating `mName=…/005 mClass=0` ↔ `mName=…/007 mClass=255,mSubclass=255` across replugs
(`r6_ethadapter_usbhost.log`), exactly as round 3 characterised it. The Samsung's CDC ACM pair
(`02/02/01` control + `0A/00/00` data) is the shape `ad082fff` exists for: the data interface is
byte-identical to an ECM adapter's, but the veto now keys on the control subclass (`02` = ACM,
not ECM `06` / EEM `07` / NCM `0D` / MBIM), so it is `accepted: PTP`, never rejected as a network
adapter. No `beginAutoConnect` / `Single device found` on any rejected device; the Samsung and
the un-blacklisted Poco did auto-connect on the USB-button tap (correct — single usable device),
permission dialogs were denied, no session pursued.

## R7 — measurement only, permission prompts per connect

**INCONCLUSIVE** — a persistent grant already exists for the package, so the structural
two-prompts-per-connect behaviour cannot be measured without clearing it, and clearing needs the
§7b uninstall (rewrites resolution/DPI/codec + settings restore). Not taken.

`dumpsys usb` (read after the R5 reboot, when the parser was healthy again),
`profile_group_settings.device_preferences`:

```
{ vendor_id=6353 (0x18D1)  product_id=20193 (0x4EE1)  class=0 subclass=0 protocol=0
  manufacturer_name=Xiaomi  product_name=POCO X3 NFC  serial_number=4f4027e9
  user_package={ user_id=0  package_name=com.andrerinas.headunitrevived } }
{ vendor_id=6353 (0x18D1)  product_id=11520 (0x2D00)  class=0 subclass=0 protocol=0
  manufacturer_name=Xiaomi  product_name=POCO X3 NFC  serial_number=4f4027e9
  user_package={ user_id=0  package_name=com.andrerinas.headunitrevived } }
permissions_manager.device_permissions={ device_name=/dev/bus/usb/002/003  uids=10491 }
```

Two persistent, **serial-keyed** grants (Poco file-transfer `4EE1` and accessory `2D00`) — the
"tick always" form (§7b). These were created during R2 when the operator ticked "always" on the
permission dialog. Consequence: on this rig, a fresh Poco connect now prompts **zero** times
(both the pre-switch and post-switch identities are pre-granted), which is why R6's Poco arm
auto-connected with no dialog. **This changes the rig for the next round** and can only be cleared
by an uninstall.

## Anything the brief did not ask about

- **`UsbLauncherListener.kt:25` logs the wrong reason for a blacklisted device** (detailed in
  Setup notes). Cosmetic — the refusal is correct — but a user reading their log to understand why
  a phone won't connect would see `accepted: ADB` on the in-service attach path and
  `rejected: blacklisted by the user` on the scan path for the same device in the same second.
  One-line fix: log the blacklist verdict when `Settings.isUsbDeviceBlacklisted` is the reason
  `isConnectable` returned false.
- **`App.onCreate` crashes at before-first-unlock** (R5 root cause) — a `main` bug that breaks
  every locked-boot entry point OHU has (USB attach, `BootCompleteReceiver`, `AutoStartReceiver`),
  and makes the entire device-protected-storage / `directBootAware` mechanism dead weight on any
  device that reaches BFU. Pre-dates this branch. Worth its own `main` fix and possibly its own
  round once fixed.
- **The dock presents a fourth device** — `Generic USB3.0 Card Reader (05E3:0749)`,
  `if0 08/06/50` (mass-storage SCSI) — correctly `rejected: no Android interface`. Not in the
  round 3 write-up.
- **`0A12:4007`** (the BT dongle) was in the moto's blacklist from a prior round
  (`<string>0a12:4007</string>` in `moto-settings-backup.xml`). Restored as-is. It is rejected on
  descriptors alone regardless, so it never mattered to any arm.
- The moto's `dumpsys usb` descriptor-parser exception (Setup notes) is a latent ROM bug any USB
  round on this host can trip with enough replugging; a reboot is the only fix found.
