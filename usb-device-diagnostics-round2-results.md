# usb-device-diagnostics — round 2 results

**Candidate:** `feat/usb-device-list-diagnostics` @ `4f0cc471` (3 commits on `main` @ `4849903d`)
**Baseline:** none (candidate-only round; round 1's captures are the before-state)
**APK md5:** `00735779f04f8485e0e8f7ceceadcee9` (`com.andrerinas.headunitrevived_3.3.0-beta4_debug.apk`, versionCode 103)
**Unit:** NOT the MT50 rig. USB host = **motorola edge 30 neo** (`miami`, Snapdragon 695, Android 14),
connected over **wireless adb** (`192.168.1.5:38171`) so the USB-C port was free for OTG the whole
round. Second phone = **Xiaomi Poco X3 NFC** (`surya` / `4f4027e9`, SM7150, Android 15 LineageOS)
plugged into the moto over USB-C. Plus a **Samsung** (model not recorded) in the post-round check
below. Same USB-C dock, flash disk and Bluetooth dongle family as round 1.
**Date:** 2026-08-31

Round 1 ran on `surya` as host; this round `surya` is the *second phone* and the **moto edge 30 neo
is the host** (the maintainer connected it over wireless adb with the port free). R0 and R5 are the
rig's half; R1–R4 are the host-phone half.

---

## Headline — the manifest narrowing (`4f0cc471`) regresses wired auto-launch on every phone tested, and R1's predicate fix does not hold on hardware. The branch is not ready.

Two independent defects, one per code commit:

1. **`4f0cc471` (narrow `usb_device_filter.xml`): a phone in "file transfer / Android Auto" mode no
   longer raises the system dialog and OHU is no longer offered as a handler — confirmed on three
   OEMs (Xiaomi Poco X3 NFC, motorola edge 30 neo, Samsung), all with USB debugging off.** Only USB
   tethering still triggers it. This is the commonest way a phone is plugged in, and it worked before
   the commit (the old filter was `<usb-device />`, match-all). See **F1** below.

2. **`bfda7808` (`UsbDeviceIdentityPolicy` + CDC-sibling veto): a USB ethernet adapter is still
   `accepted` and OHU still auto-connects to it.** The policy is fed only the *active USB
   configuration's* interfaces, so an adapter that stays in its `FF/FF/00` configuration never
   exposes the CDC siblings the veto keys on. See **R1** below.

`9cf6efbe` (the diagnostic itself) is clean and should ship. The other two need rework before the
branch does.

---

## Setup notes

- **Host is the moto, not surya.** The brief assumed round 1's host (surya). The maintainer had the
  moto free on wireless adb instead, so that is the host here and surya is the second phone. The
  round-1 JVM fixtures are the *moto's* descriptors; this round the moto is on the other side of the
  cable, so R2's phone descriptors are surya's and differ from the fixtures.

- **PC thermal event before R0.** The build (`assembleGithubDebug`) had completed before a thermal
  throttle / power event; the unit-test run had not. On restart `testGithubDebugUnitTest` failed
  twice with `Could not read workspace metadata from …/transforms/…/metadata.bin` — a corrupt Gradle
  transforms cache from the power cut, plus a daemon serving the stale path from memory. Cleared
  `~/.gradle/caches/8.13/transforms/` and stopped the daemon; the third run passed clean. Same
  corrupt-transforms failure `post-beta1-self-mode` round 2 documented.

- **Motorola floods logcat; a plain `adb logcat -v time` stream loses the OHU lines.** The first
  three R1 capture attempts came back with **zero** `OPENHU` lines over 30–40 s despite the app
  running and logging — the unfiltered stream drowned in the ROM's own spam and the ring buffer
  rolled faster than a post-hoc `logcat -d` dump could catch. **Fix: filter at the source —
  `adb logcat -v time OPENHU:V '*:S'`.** Every decisive capture here uses that. Worth adding to
  `TESTING-TEMPLATE.md` §2 for this device. Also: `setsid`/`nohup` made `$!` the wrong pid, so stray
  readers piled up; and `pkill -f` on a pattern that also matched the shell wrapper string killed
  the parent shell (exit 144).

- **`log-level` key confirmed correct** (`0227a589`): `<int name="log-level" value="0" />` is
  VERBOSE, `2` is INFO. Read back before every VERBOSE run. `set_pref.sh` used for every write.

- **The `UsbDiagnostics:` verdict line is VERBOSE when any device is `accepted`, INFO when none is**
  (`reportAtInfo = accepted == 0`). So R1's *accepted* ASIX line only shows at `log-level=0`;
  R3/R5's *rejected* lines show at the default `2`.

- **`UsbAttachedActivity` launched directly** for the R2/R4 verdict reads:
  `am start -n $PKG/com.andrerinas.openheadunit.app.UsbAttachedActivity -a android.hardware.usb.action.USB_DEVICE_ATTACHED`
  (exported; round 1 used the same route). It logs `logDeviceList` before `resolveDevice`.

- **The F1 cross-OEM check was run by the maintainer after the scripted round**, with the moto's
  wireless adb already dropped (`connection refused`), so the debugging-off file-transfer descriptors
  of the three phones were **not** captured. What is recorded is the behaviour (no dialog on file
  transfer, dialog on tethering, three OEMs) and the Poco's *debugging-on* file-transfer descriptor
  from R2. The mechanism below is consistent with both but the exact debugging-off triples are a
  follow-up capture.

- **surya's USB mode could not be freely swept during the scripted part.** Dev phone, debugging on,
  default USB configuration pinned; "charging only" did not re-enumerate it to a bare MTP state.

- **No plain USB-C OTG adapter**, same as round 1 — the flash disk only enters via the dock.

- **Scripts:** `build_hur.sh`, `run_unit_tests.sh` (R0); `set_pref.sh` (log-level). **Added:**
  `hur-wifi-test-scripts/usb_diag_capture.sh`.

- **Device restored:** `settings.xml` diffed and pushed back **byte-identical** (`diff` clean); the
  app had drifted `log-level`, `last-connection-ip/-type/-usb-device` and set `aa174_notice_shown`.
  Candidate APK left installed (`-r`). Dock/dongle unplugged.

---

## F1 — a phone in file-transfer mode is no longer offered to OHU (regression from `4f0cc471`)

**FAIL** — confirmed on three OEMs, USB debugging off on all of them.

| phone | "File transfer / Android Auto" mode | "USB tethering" mode |
|---|---|---|
| Xiaomi Poco X3 NFC | **no system dialog, OHU not offered** | dialog appears |
| motorola edge 30 neo | **no system dialog, OHU not offered** | dialog appears |
| Samsung | **no system dialog, OHU not offered** | dialog appears |

Before `4f0cc471`, `usb_device_filter.xml` was `<usb-device />` (match-all), so every phone in every
mode raised the dialog. After it, the file is seven descriptor triples. The one meant to catch
file-transfer is:

```xml
<usb-device class="255" subclass="255" protocol="0" />
```

That triple (`FF/FF/00`) is only AOSP's pure `f_mtp` MTP interface. Real OEM phones present their
file-transfer interfaces as vendor-specific with **non-zero protocol bytes**. surya's file-transfer
descriptor, captured this round (R2, `r2_poco-modes.log`, debugging on):

```
if0 FF/FF/30 | if1 FF/FF/40 | if2 FF/FF/50 | if3 FF/FF/80 | if4 FF/FF/70 | if5 FF/42/01
```

Not one `FF/FF/00`. Android's `DeviceFilter.matches()` requires an exact `protocol` match, so none of
those hit the entry. USB tethering keeps working because RNDIS is a fixed standardised triple
(`E0/01/03`) every OEM uses identically, caught by `<usb-device class="224" subclass="1" protocol="3" />`.

There is no interface-descriptor signature for "phone in MTP mode" that a manifest `DeviceFilter` can
express — OEMs choose their own vendor protocol bytes. So the narrowing cannot be tuned into
correctness by adding another triple; the direction is wrong.

**Recommendation:** revert or drop `4f0cc471`. Keep a broad manifest filter and solve the dialog-spam
problem it targets at the layer that decides what OHU *acts on* — `UsbDeviceIdentityPolicy` plus the
existing blacklist — which is the layer R1 shows needs fixing anyway. A phone whose file-transfer
descriptor is `FF/FF/xx` is still correctly identified by the policy's vendor-class rule (the policy
matches on class+subclass+bulk-pair, not on `protocol == 0`); it is only the *manifest* that is too
strict.

**Not captured, follow-up:** the debugging-off file-transfer descriptors of all three phones (adb was
down for the F1 check). Worth having to confirm the exact triples and to size any interim manifest
entry.

---

## R0 — build gate

**PASS**

- `assembleGithubDebug` via `build_hur.sh`: **BUILD SUCCESSFUL** (4m 09s), APK produced, versionCode
  103 / `3.3.0-beta4`, md5 `00735779f04f8485e0e8f7ceceadcee9`.
- `testGithubDebugUnitTest` via `run_unit_tests.sh` (third attempt, after the transforms-cache fix):
  **BUILD SUCCESSFUL**, result XML totals **tests=966, failures=0, errors=0, skipped=0**.
- Delta from round 1's 953 is exactly **+13 = `UsbDeviceIdentityPolicyTest`** (`tests="13"`),
  matching the brief's "**966**".

---

## R1 — the ethernet adapter is no longer projected to (fix from `bfda7808`)

**FAIL** — the AX88179B is still `accepted` and OHU still auto-connects to it. Both of the brief's
failure conditions fired.

- Settings written: `log-level=0` (to see the accepted verdict).
- Bus: the round-2 dock — Kingston DataTraveler G3 flash disk (`0930:6545`), Generic USB3.0 Card
  Reader (`05E3:0749`), **ASIX AX88179B** (`0B95:1790`), Fresco Logic Billboard (`1D5C:7102`).
- Discard-rule check: n/a (not a connection run). Run twice — fresh plug and one unplug/replug —
  identical verdict both times.
- Ethernet **cable** was not attached (it kills the moto's wireless adb); irrelevant, the controller
  enumerates on the bus regardless and that is what is judged.

Decisive lines (`r1_asix-accepted.log`, identical in `…-replug.log`):

```
17:41:01.267 V UsbDiagnostics: USB button sees 4 USB device(s), 1 usable for Android Auto
17:41:01.267 V   Kingston DataTraveler G3  (VID: 0930 PID: 6545) [class 00/00/00, no permission] rejected: no Android interface | if0 08/06/50 (bulkIn+bulkOut)
17:41:01.267 V   Fresco Logic, Inc Generic Billboard Device (VID: 1D5C PID: 7102) [class 11/00/00, no permission] rejected: no Android interface | if0 11/00/00 (no endpoints)
17:41:01.267 V   Generic USB3.0 Card Reader (VID: 05E3 PID: 0749) [class 00/00/00, no permission] rejected: no Android interface | if0 08/06/50 (bulkIn+bulkOut)
17:41:01.267 V   ASIX AX88179B (VID: 0B95 PID: 1790) [class FF/FF/00, permission] accepted: AOAP (unnamed vendor interface) | if0 FF/FF/00 (bulkIn+bulkOut+1 other)
17:41:01.269 I HomeFragment.setupListeners$lambda$13 | USB button: Single device found - ASIX AX88179B (VID: 0B95 PID: 1790), auto-connecting
17:41:01.269 I MainActivity.beginAutoConnect | Auto-connect: begin (USB button auto-connect, mode=OVERLAY)
```

On the replug run the auto-connect also drove a `systemui.usb.UsbPermissionActivity` — a permission
prompt for an ethernet adapter.

**Root cause.** `UsbDeviceIdentityPolicy`'s KDoc says `interfaces` is "every interface across every
configuration". `UsbDeviceCompat.describe()` builds that list from `device.interfaceCount` /
`device.getInterface(i)`, which on this Android 14 device returns **only the active configuration's
interfaces**. The AX88179B stays in configuration 1 — a single `FF/FF/00` "Network_Interface" — on
every plug (`asix-ax88179b-descriptors.txt`). `UsbHostManager` logs all three configs, configs 2 and
3 carrying the CDC interfaces, but the app never sees them, so in `accessoryVerdict`:

- `iface.name == "Android Accessory Interface"` — no. The descriptor names it `Network_Interface`,
  but `UsbInterface.getName()` returned **blank** without an open connection (hence "unnamed"), so
  the name check cannot fire.
- `iface.name == "MTP"` — no.
- `device.interfaces.any { class 0x02 || 0x0A }` — **no**, the CDC siblings are in configs the app
  can't see → the veto never runs.
- falls to `else -> Verdict(true, "AOAP (unnamed vendor interface)")`.

**Fix:** iterate `device.getConfigurationCount()` / `getConfiguration(i).getInterface(j)` instead of
the flat list, and do not rely on `UsbInterface.getName()` before permission. A JVM fixture whose
*only app-visible* interface is `FF/FF/00` unnamed, no CDC sibling, would have caught this — round
1's fixture gives the policy the full multi-config list the app never receives.

Separately: `HomeFragment`'s USB-button handler calls `beginAutoConnect` unconditionally when exactly
one device passes `isAndroidDevice`, ignoring the `not allowed / auto-start disabled` guard that
`UsbAttachedActivity` respects (see R3). Worth a guard there too.

The flash disk, card reader and billboard device are all correctly `rejected`.

---

## R2 — the phone is still accepted in every mode

**PASS** — every form surya presented was `accepted:`, none rejected. (Round 1's "now says MTP"
difference untested — dev phone would not re-enumerate to a bare MTP state.)

Settings: `log-level=0`. surya plugged into the moto over USB-C, direct.

| surya USB form (as it enumerated) | VID:PID | if0 (+ extras) | verdict |
|---|---|---|---|
| file transfer, USB debugging **on** | 05C6:90DB | `FF/FF/30`,`FF/FF/40`,`FF/FF/50`,`FF/FF/80`,`FF/FF/70`, **`FF/42/01`** | **accepted: ADB** |
| after OHU's accessory switch | 18D1:2D01 | `FF/FF/00` + `FF/42/01` | **accepted: already in accessory mode** |
| PTP (Google-VID config) | 18D1:4EE1 | `06/01/01` | **accepted: PTP** |
| "charging only" selected | — | did not re-enumerate; stayed `18D1:4EE1` `06/01/01` | **accepted: PTP** |

```
17:50:11.678 V UsbDiagnostics:   Xiaomi POCO X3 NFC (VID: 05C6 PID: 90DB) [class 00/00/00, no permission] accepted: ADB | if0 FF/FF/30 ... | if5 FF/42/01 (bulkIn+bulkOut)
17:50:29.856 V UsbDiagnostics:   Xiaomi POCO X3 NFC (VID: 18D1 PID: 2D01) [class 00/00/00, no permission] accepted: already in accessory mode | if0 FF/FF/00 (bulkIn+bulkOut) | if1 FF/42/01 (bulkIn+bulkOut)
```

The policy verdict is correct in every mode. Note this is the *in-app* predicate, not the manifest
filter — F1 is about the manifest, which never runs the policy. The `FF/FF/30…` interface list here
is also the direct evidence for F1's mechanism.

---

## R3 — Android stops offering us for things that are not phones

**PASS** for the Bluetooth dongle and the phone; **PASS (expected)** for the dock/ASIX;
**INCONCLUSIVE** for the flash disk (no OTG adapter to isolate it).

This is the run the narrowing was *supposed* to make pass, and for a non-phone it does. F1 is the
cost: the same narrowing that suppresses the dongle also suppresses a real phone in file transfer.

| device | system USB dialog | note |
|---|---|---|
| **Bluetooth-audio dongle alone** (UGREEN BT701, `0A12:4007`) | **none** — zero `systemui.usb.*` in the whole dongle-alone window | round 1: this device raised `UsbConfirmActivity ×2`. Narrowing suppressed it. OHU verdict `rejected: no Android interface`, `0 usable`. |
| **second phone, file transfer + debugging on** (surya, during the scripted round) | `systemui.usb.UsbConfirmActivity` displayed (`+240ms`) | matched on the ADB interface `FF/42/01`, not on file transfer as such — cf. F1 |
| **dock with the ASIX** | `systemui.usb.UsbResolverActivity` displayed | **expected** — `FF/FF/00` matches the vendor-class entry |
| **USB flash disk** | not isolable | only reachable via the dock, where the ASIX's dialog is present; `08/06/50` matches no filter entry so it *cannot* raise an OHU dialog, but "no dialog" was not observed directly |

On the dock plug, OHU's `UsbAttachedActivity` launched for the ASIX (system resolver → OHU), then
logged `UsbAttachedActivity.onCreate | Skipping device ASIX AX88179B … (not allowed and USB
auto-start disabled)` — the **auto-start path does not connect** to the ASIX. It is the **USB
button** path (R1) that ignores that guard.

```
17:58:41.893 I UsbDiagnostics: USB attach sees 1 USB device(s), 0 usable for Android Auto
17:58:41.893 I   TaiYiLian UGREEN-BT701 (VID: 0A12 PID: 4007) [class 00/00/00, no permission] rejected: no Android interface | if0 03/00/00 (1 other) | if1 03/00/00 (2 other) | if2 01/01/00 (no endpoints) | if3 01/02/00 (no endpoints) | if4 01/02/00 (1 other) | if5 01/02/00 (1 other)
```

`grep "systemui.*usb"` over the dongle-alone window: **0 lines.**

---

## R4 — the phone still auto-starts

**PASS** — via the exported-activity route.

Settings: default `log-level`. surya on the bus (`18D1:4EE1` PTP form). `UsbAttachedActivity` launched
directly (setup notes).

```
17:54:04.725 I UsbAttachedActivity.onCreate | USB Intent: Intent { act=android.hardware.usb.action.USB_DEVICE_ATTACHED ... }
17:54:04.740 V UsbDiagnostics: USB attach sees 1 USB device(s), 1 usable for Android Auto
17:54:04.740 V   Xiaomi POCO X3 NFC (VID: 18D1 PID: 4EE1) [class 00/00/00, no permission] accepted: PTP | if0 06/01/01 (bulkIn+bulkOut+1 other)
17:54:04.743 I UsbAttachedActivity.onCreate | Switching USB device to accessory mode
```

**Caveat:** this proves `UsbAttachedActivity` handles the intent and accepts the phone *once Android
delivers the intent*. F1 shows Android does **not** deliver it for a file-transfer-mode phone on
these OEMs, so R4's PASS does not contradict F1 — the two test different links in the chain (F1: does
the OS offer OHU; R4: does OHU handle the offer).

---

## R5 — the empty bus, and the Verbose path

**PASS**

Runs on the moto (host), nothing attached (`host_connected=false`).

- Default `log-level=2` (`r5_empty-bus-default.log`):
  ```
  17:29:12.036 I UsbDeviceDiagnostics.logDeviceList | UsbDiagnostics: USB list sees 0 USB device(s), 0 usable for Android Auto
  17:29:12.037 I UsbDeviceDiagnostics.logDeviceList | UsbDiagnostics: nothing is on the bus. Either the port carries no data, the unit is not in USB host mode, or a wireless adapter is waiting for its phone before it presents itself.
  ```
  Both at INFO, as designed.
- `log-level=0`, force-stop, relaunch (`r5_empty-bus-verbose.log`): identical two lines, still INFO
  (always INFO on an empty bus), **no exception**, **no `could not read the USB device list`**.

---

## Verdict summary

| run | verdict |
|---|---|
| **F1 phone in file transfer no longer offered to OHU** (`4f0cc471`) | **FAIL** — regression, 3 OEMs, debugging off |
| R0 build + unit tests | **PASS** (966/0, `UsbDeviceIdentityPolicyTest` +13) |
| **R1 ethernet adapter no longer projected to** (`bfda7808`) | **FAIL** — `accepted: AOAP (unnamed vendor interface)` + auto-connect + permission prompt |
| R2 phone accepted in every mode (in-app policy) | **PASS** |
| R3 no dialog for non-phones | **PASS** (BT dongle, dock/ASIX); **INCONCLUSIVE** (flash disk) |
| R4 phone still auto-starts (once the intent is delivered) | **PASS** |
| R5 empty bus + Verbose path | **PASS** |

**Ship:** `9cf6efbe` — the diagnostic. It behaved perfectly across ~15 captures and its output is the
fastest way to see what OHU makes of the bus.

**Rework before the branch ships:**

- **`4f0cc471` — revert or drop.** A manifest `DeviceFilter` cannot express "phone in MTP mode"
  because OEM file-transfer interfaces are vendor-specific with arbitrary protocol bytes
  (`FF/FF/30`, `FF/FF/40`, … on surya; Samsung and Motorola will differ again). The narrowing
  regresses the commonest wired connection path on every OEM tested. Keep a broad filter; gate on
  `UsbDeviceIdentityPolicy` + the blacklist instead.
- **`bfda7808` — fix the interface enumeration.** `UsbDeviceCompat.describe()` must walk
  `device.getConfigurationCount()` / `getConfiguration(i)`, not the flat `device.getInterface(i)`
  which is active-config-only here, and must not depend on `UsbInterface.getName()` pre-permission.
  Add a fixture whose only visible interface is `FF/FF/00` unnamed.
- **`HomeFragment` USB button** — add the `auto-start disabled` guard the attach path already has,
  so a single non-phone that slips the policy is not auto-connected on a button tap.

## Anything the brief did not ask about

- **`UsbDiagnostics` output reads well under pressure** — header + `N usable` + per-device
  `accepted:`/`rejected: <reason>` + interface list was consistently the fastest read. Commit
  `9cf6efbe` is solid regardless of the other two.
- **The billboard device.** USB-C docks present a `class 0x11` (Billboard) device. `0x11` is not in
  `EXCLUDED_DEVICE_CLASSES`, so it falls to `rejected: no Android interface` rather than
  `rejected: device class 0x11 is not a phone`. Same outcome, less informative reason. Cheap to add.
- **This Motorola's logcat needs source-side filtering** (`OPENHU:V '*:S'`), not a post-hoc `-d`
  dump — cost ~4 wasted R1 attempts. See setup notes.
- **Follow-up capture wanted:** debugging-off file-transfer descriptors for the Poco, moto and
  Samsung, to nail the exact interface triples F1 turns on.
