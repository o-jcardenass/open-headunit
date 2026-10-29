# usb-device-diagnostics — round 1 results

**Candidate:** `feat/usb-device-list-diagnostics` @ `21478e23` (one commit on `main` @ `4849903d`)
**Baseline:** not built — candidate-only round, no behaviour comparison needed
**APK md5:** `b09df77fb26bf3b0fdce95d6feedd980` (`com.andrerinas.headunitrevived_3.3.0-beta3_debug.apk`)
**Unit:** NOT the MT50 rig. Maintainer's own phone — Xiaomi Poco X3 NFC (`surya`), SoC SM7150
(Snapdragon 732G), **Android 15** (LineageOS 22, `lineage_surya-userdebug BP1A.250505.005`, patch
2026-08-01), 1080×2400 @ 440dpi. Connected over **wireless adb** (`192.168.1.189:43475`), so the
USB-C port was free for OTG the whole round.
**Date:** 2026-08-30

This round ran **§9 (U1–U3), the real-USB-hardware half**, per the maintainer's instruction — not
the §6 R0–R4 rig runs, which stay with the MT50 agent. R0 was still done (build gate). The phone is
a **one-off** for the USB runs; the MT50 remains this channel's rig and its §7a quirks are unchanged.

---

## Setup notes

- **Device is not the rig.** Everything in §3/§6 written on the "empty bus by construction, no host
  mode" premise does not apply here: this phone *is* a USB host (`android.hardware.usb.host`
  feature present) and `dumpsys usb` went `host_connected=true` the moment anything was plugged.
  The empty-bus arm was still observed incidentally (see R1-equiv below).

- **Brief §4 settings key is wrong.** §4 says set `exporter-log-level` to `2` for VERBOSE. The
  actual pref key is **`log-level`** (`Settings.KEY_LOG_LEVEL`, `Settings.kt:1200`), read as an
  **ordinal into `LogExporter.LogLevel`** where the order is `VERBOSE=0, DEBUG=1, INFO=2, WARNING=3,
  … SILENT`. So VERBOSE is **`log-level` = `0`**, and value `2` is INFO (the default), not VERBOSE.
  This matches the existing memory note "RECV: needs log-level=0 not 1". R3 on the rig should set
  `log-level` `0`, not `exporter-log-level` `2`, or it will run at INFO and prove nothing new.

- **Accepted-device lines are VERBOSE, and that shaped the whole round.** In `UsbDeviceDiagnostics`,
  `reportAtInfo = (accepted == 0)`. A bus with any *usable* device logs the header and every
  per-device line via `AppLog.v`, which `AppLog.isLoggable` drops entirely at the default INFO
  level — nothing reaches logcat. So:
  - U1 (device *rejected* → `accepted == 0`) logs at **INFO**, visible at the phone's default
    `log-level=2`.
  - U2 (phone *accepted* → `accepted == 1`) logs at **VERBOSE** only. `log-level` was set to `0`
    for the U2 sweep and restored to `2` at the end (readback confirmed both writes; key set
    matches `settings-backup.xml`).

- **Hardware used:** a USB-C Bluetooth-audio dongle (UGREEN BT701) plugged directly for U1a; a
  USB-C multiport hub/dock for the flash-disk part (U1b); a USB-C-to-USB-C cable to a second phone
  for U2. No plain USB-C-to-USB-A OTG adapter, so the flash disk could only come in via the dock.

- **The dock presents three functions at once:** a `General USB Flash Disk`
  (090C:1000), an **ASIX AX88179A USB-3 gigabit-ethernet controller** (0B95:1790), and the hub IC
  itself (VID 2316, which OHU's own code already skips: `Ignoring non-Android USB device attached
  in service (VID: 2316)`). The ethernet adapter is a genuine finding, not just noise — see R-U2
  and "Anything the brief did not ask about".

- **`UsbAttachedActivity` ("USB attach" caller) needs the system dialog confirmed.** On Android 15
  the `USB_DEVICE_ATTACHED` intent is intercepted by `com.android.systemui.usb.UsbConfirmActivity`
  / `UsbResolverActivity` / `UsbPermissionActivity`; OHU's own activity only runs if the user taps
  through. Under the hub's re-enumeration churn that was impractical to hit by hand, so this caller
  was exercised by launching it directly:
  `am start -n <pkg>/com.andrerinas.openheadunit.app.UsbAttachedActivity -a android.hardware.usb.action.USB_DEVICE_ATTACHED`
  (it is `exported`; `resolveDevice` then logs `No USB device in intent extras, falling back to
  single device`, but `logDeviceList` runs first regardless — line 38, before `resolveDevice`).

- **OHU auto-connects to anything `isAndroidDevice` accepts.** `HomeFragment` USB-button handler:
  when the list has exactly one entry it calls `MainActivity.beginAutoConnect` without waiting for
  the user. With the ethernet adapter or a phone as the sole entry this fired repeatedly and each
  attempt raised a `UsbPermissionActivity`. Counts in the VERBOSE capture: `UsbConfirmActivity` ×7,
  `UsbPermissionActivity` ×10, `UsbResolverActivity` ×3. The maintainer reported "USB appeared 3
  times" during the first plug. Not new to this branch, but it makes the match-all manifest filter
  visibly user-hostile with common hub hardware.

- **Scripts used:** `hur-wifi-test-scripts/build_hur.sh` (R0 build), `run_unit_tests.sh` (R0
  tests), `set_pref.sh` (log-level 0 then 2). No script added or changed. `install_and_launch.sh`
  was **not** used — it hardcodes the MT50 serial; installed with a bare `adb -s <phone> install
  -r` instead.

- **Captures** committed to `evidence/usb-device-diagnostics-round1/`:
  `u1.log` (first hub plug, `log-level=2`/INFO), `u1b.log` (full U1b + U2 sweep,
  `log-level=0`/VERBOSE), `u1_bt.log` (U1a Bluetooth dongle, INFO), `asix_raw.txt` / `moto_raw.txt`
  (raw `UsbHostManager` descriptor dumps), `all-usbdiagnostics-lines.txt` (every `UsbDiagnostics:`
  line from all three captures), `u1_bt_dialog.png` (OHU list showing the dongle "Ignored"),
  `u1_screen.png` (OHU home).

---

## R0 — build and unit tests

**PASS**

- `./gradlew assembleGithubDebug` via `build_hur.sh`: **BUILD SUCCESSFUL in 51s**, APK produced
  (`com.andrerinas.headunitrevived_3.3.0-beta3_debug.apk`, 21.8 MB). Native/CMake half linked (this
  is the first compile of the branch anywhere).
- `./gradlew testGithubDebugUnitTest` via `run_unit_tests.sh`: **BUILD SUCCESSFUL**. Result XML
  totals across all classes: **tests=953, failures=0, errors=0, skipped=0**. Matches the brief's
  953/953 exactly.

---

## R1-equivalent — empty bus says so, at INFO (observed, not the assigned run)

**PASS** (incidental — this is a phone, not the rig, but the arm is identical code)

- Settings written: none. `log-level` was at its default `2` (INFO).
- `dumpsys usb`: `host_connected=false` before anything was plugged.
- On app launch with an empty bus, at INFO:

  ```
  08-30 11:12:44.418 I/OPENHU ( ...) UsbDeviceDiagnostics.logDeviceList | UsbDiagnostics: USB list sees 0 USB device(s), 0 usable for Android Auto
  08-30 11:12:44.418 I/OPENHU ( ...) UsbDeviceDiagnostics.logDeviceList | UsbDiagnostics: nothing is on the bus. Either the port carries no data, the unit is not in USB host mode, or a wireless adapter is waiting for its phone before it presents itself.
  ```

- `grep -ac "UsbDiagnostics:" u1.log` before any plug: 2 (header + advisory). No `could not read
  the USB device list` line anywhere in either capture (`grep -ac` = 0 in both).
- The advisory also fired ~11× in `u1b.log` during hub detach/attach churn — deduped per caller by
  bus signature, so the repeats are real distinct `0-device` states, not a loop.

---

## R2-equivalent — dedupe does not swallow a caller

**PASS**

- Bus held unchanged (hub + stick, 2 devices) across the checks.
- USB button pressed **4 times** total, ~5 s apart. `grep -ac "UsbDiagnostics: USB button sees"
  u1b.log` = **1**.
- `grep -ac "UsbDiagnostics: service scan" u1b.log` = **1** — the service-scan caller logged its
  own first call independently of the USB button's.
- All four caller strings appeared verbatim, each keyed independently, each re-logging only on a
  genuine bus-signature change:

  ```
  UsbDiagnostics: USB list sees {0,1,2} USB device(s), {0,1} usable for Android Auto
  UsbDiagnostics: USB button sees {1,2} USB device(s), {1} usable for Android Auto
  UsbDiagnostics: USB attach sees 1 USB device(s), 1 usable for Android Auto
  UsbDiagnostics: service scan (force=true) sees {0,1,2} USB device(s), {0,1} usable for Android Auto
  ```

---

## R-U1 — a USB device that is not a phone (§9 U1)

**PASS** — reject path prints, at INFO, on all four callers. Run twice: a USB-C Bluetooth dongle
(the brief's device) and a USB flash disk (via the hub).

### U1a — USB-C Bluetooth adapter (UGREEN BT701)

- Settings: `log-level=2` (INFO, default). Dedicated capture `u1_bt.log`.
- The adapter is a **UGREEN CM144 / BT701** (`TaiYiLian UGREEN-BT701`, VID **0A12** = CSR/Qualcomm,
  PID 4007). It is **not** a raw HCI radio (`E0/01/01`) as the brief anticipated — it is a
  Bluetooth-*audio* dongle that enumerates to the host as a **USB Audio + HID composite**
  (`hasAudio/HID/Storage: true/true/false`). Six interfaces:
  `if0 03/00/00 | if1 03/00/00 | if2 01/01/00 | if3 01/02/00 | if4 01/02/00 | if5 01/02/00`
  (2×HID, 1×AudioControl, 3×AudioStreaming). No interface matches any Android rule.
- Decisive line, **at INFO**, identical across all four callers (`USB list`, `USB button`,
  `service scan (force=true)`, `USB attach`):

  ```
  08-30 11:28:51.951 I/OPENHU  UsbDiagnostics: USB list sees 1 USB device(s), 0 usable for Android Auto
  08-30 11:28:51.951 I/OPENHU  UsbDiagnostics:   TaiYiLian UGREEN-BT701 (VID: 0A12 PID: 4007) [class 00/00/00, no permission] rejected: no Android interface | if0 03/00/00 (1 other) | if1 03/00/00 (2 other) | if2 01/01/00 (no endpoints) | if3 01/02/00 (no endpoints) | if4 01/02/00 (1 other) | if5 01/02/00 (1 other)
  ```

  - `accepted == 0` → whole dump at INFO, as designed. `grep -ac "could not read the USB device
    list" u1_bt.log` = 0.
  - `matchReason` returns exactly `rejected: no Android interface`. The brief's stated expectation
    ("ending `rejected: no Android interface`") is met, even though the device class is not the
    `E0/01/01` it guessed.
  - **No auto-connect attempt.** Because the device is rejected, OHU did *not* fire
    `beginAutoConnect` against it (contrast the ASIX ethernet adapter below, which it did). Good —
    the reject verdict actually gates the connection path.
  - OHU's own **USB list screen renders it as "Ignored"** (orange), matching the log verdict
    (screenshot `u1_bt_dialog.png`).

- **Android dialog:** **yes.** `com.android.systemui/.usb.UsbConfirmActivity` displayed ×2
  (`Displayed ... for user 0: +110ms` at 11:28:52), maintainer confirmed the dialog on-screen. OHU
  was offered as a handler for a Bluetooth-audio dongle — the match-all `<usb-device />` filter
  again.

### U1b — USB flash disk (`General USB Flash Disk`, 090C:1000, via the hub)

- Same INFO path. Decisive line:

  ```
  08-30 11:13:48.438 I/OPENHU  UsbDiagnostics:   General USB Flash Disk (VID: 090C PID: 1000) [class 00/00/00, no permission] rejected: no Android interface | if0 08/06/50 (bulkIn+bulkOut)
  ```

  `08/06/50` = mass-storage / SCSI-transparent / bulk-only. Correctly `rejected` — our MTP rule is
  `08/06/01`, protocol differs.
- Android dialog: **yes** — `UsbConfirmActivity` and `UsbResolverActivity` both displayed on the
  first hub plug (11:13:46, 11:13:50); the maintainer reported the prompt appearing 3× on-screen.
  OHU offered as a handler for a **plain USB flash drive**.

**Combined:** two unrelated non-Android devices, both cleanly `rejected` at INFO, and Android
offered OHU for both — the live demonstration the brief wanted that `usb_device_filter.xml` being
match-all is worth narrowing.

---

## R-U2 — a second Android phone over USB-C (§9 U2, the run that unblocks the filter decision)

**PASS** — and the answer is: **on this phone, no USB mode produces a false negative; the current
heuristic accepts a real modern phone in every mode.** Every accepted line was captured at VERBOSE
(`log-level=0`).

Phone: **motorola edge 30 neo** (2022, Android 14, Snapdragon 695). USB-C-to-USB-C, direct (no
hub). Swept through the modes; each distinct descriptor set OHU saw and its verdict:

| Moto USB mode (as selected) | VID:PID | config | if0 (and extras) | `matchReason` verdict |
|---|---|---|---|---|
| default, USB debugging **on** | 22B8:2E81 | `adb` | `FF/42/01` bulk+bulk | **accepted: ADB** |
| MIDI + ADB (Google-VID accessory) | 18D1:4EE9 | `midi_adb` | `01/01/00` (no ep), `01/03/00` bulk+bulk, `FF/42/01` bulk+bulk | **accepted: ADB** |
| charging only / "no data" | 22B8:2E82 | `mtp` | `FF/FF/00` bulk+bulk+interrupt | **accepted: AOAP** |
| file transfer / MTP | 22B8:2E82 | `mtp` | `FF/FF/00` bulk+bulk+interrupt | **accepted: AOAP** (identical descriptor to charging — dedupe emitted nothing new, correctly) |
| PTP / camera | 22B8:2E83 | `ptp` | `06/01/01` bulk+bulk+interrupt | **accepted: PTP** |
| PTP + ADB | 22B8:2E84 | `ptp_adb` | `06/01/01` bulk+bulk+interrupt, `FF/42/01` bulk+bulk | **accepted: PTP** |

Representative decisive lines (VERBOSE):

```
08-30 11:20:50.539 V/OPENHU  UsbDiagnostics: USB attach sees 1 USB device(s), 1 usable for Android Auto
08-30 11:20:50.539 V/OPENHU  UsbDiagnostics:   motorola motorola edge 30 neo (VID: 22B8 PID: 2E82) [class 00/00/00, no permission] accepted: AOAP | if0 FF/FF/00 (bulkIn+bulkOut+1 other)
08-30 11:24:10.724 V/OPENHU  UsbDiagnostics:   motorola motorola edge 30 neo (VID: 22B8 PID: 2E83) [class 00/00/00, no permission] accepted: PTP | if0 06/01/01 (bulkIn+bulkOut+1 other)
```

**Reading for the `usb_device_filter.xml` narrowing decision:**

- The rule that catches this phone in its **charging-only and MTP** modes is **AOAP**
  (`FF/FF/00` + bulk-in + bulk-out). Modern Android's MTP function genuinely enumerates as
  vendor-class `FF/FF/00`, so the AOAP triple double-covers it. Charging-only was *not* the
  "presents nothing we match" case the brief worried about — this device still exposes the MTP
  function descriptor in that mode.
- PTP mode is caught by the **PTP** rule (`06/01/01`), still-image class, exactly as intended.
- The ADB interface (`FF/42/01`) is what catches a developer phone; a non-developer phone in
  charging/MTP relies on the AOAP match above.
- **No mode of this phone hit `rejected`.** A `usb_device_filter.xml` narrowed to the triples
  `FF/FF/00`+bulk, `06/01/01`, `FF/42/01`, `08/06(0x06)/01` (MTP-storage), `EF/04/01` (IAD),
  `E0/01/03` (RNDIS) would keep offering OHU for this phone in every mode tested. The narrowing
  does **not** regress this device.
- Caveat: one device, one vendor. A Samsung / Pixel charging-only descriptor was not tested and
  could differ. But the specific fear — "modern phone in default mode presents something we don't
  match" — did not reproduce here.

---

## R-U3 — wireless Android Auto adapter (§9 U3)

**UNTESTABLE** — the maintainer does not own a wireless AA adapter yet ("no AA adapter as I don't
have one yet"). The phone-first hypothesis (adapter binds no USB gadget until a phone reaches it
over its own WiFi) remains untested. No retail-adapter VID/PID/descriptor data was obtained this
round.

---

## Anything the brief did not ask about

**1. `isAndroidDevice` accepts a USB gigabit-ethernet adapter as an AOAP accessory. Real
false-positive.** The ASIX **AX88179A** (0B95:1790), a common USB-3 GbE controller built into the
maintainer's hub, is `accepted: AOAP` — in **both** of the descriptor sets it cycled through:

```
08-30 11:17:15.049 V/OPENHU  UsbDiagnostics:   ASIX AX88179A (VID: 0B95 PID: 1790) [class FF/FF/00, no permission] accepted: AOAP | if0 FF/FF/00 (bulkIn+bulkOut+1 other)
08-30 11:14:..     (class 00 enumeration) accepted: AOAP | if0 FF/FF/00 (bulkIn+bulkOut+1 other) | if1 02/0D/00 | if2 0A/00/01 | if3 0A/00/01 (bulkIn+bulkOut) | if4 02/06/00 | if5 0A/00/00 | if6 0A/00/00 (bulkIn+bulkOut)
```

The adapter's CDC-NCM composite has a vendor-specific control interface at if0 that is exactly
`FF/FF/00` with a bulk-in + bulk-out pair, which is the AOAP rule
(`ifaceClass==0xFF && ifaceSubclass==0xFF && ifaceProtocol==0x00 && hasBulkEndpoint`). OHU then
`USB button: Single device found - ASIX AX88179A, auto-connecting` and drove
`MainActivity.beginAutoConnect` against it.

Implication for the plan: **narrowing `usb_device_filter.xml` will not fix this**, because the
adapter matches the real AOAP accessory triple, not some loose heuristic. The AOAP rule cannot be
tightened without risking real accessory-mode devices. If OHU wants to stop trying to project to
ethernet dongles, the discriminator has to be elsewhere — e.g. only treat `FF/FF/00`+bulk as AOAP
when it is the device's *sole* interface, or when the device is already in accessory mode
(`isInAccessoryMode`), or bail when sibling interfaces are CDC (`02/xx` + `0A/xx`, i.e. an obvious
NIC). Worth a JVM test with this descriptor once the predicate is extracted.

**2. The `describe()` output is genuinely readable in context.** Full header + line as a reporter
would see it:

```
UsbDiagnostics: USB list sees 2 USB device(s), 1 usable for Android Auto
UsbDiagnostics:   ASIX AX88179A (VID: 0B95 PID: 1790) [class FF/FF/00, no permission] accepted: AOAP | if0 FF/FF/00 (bulkIn+bulkOut+1 other)
UsbDiagnostics:   General USB Flash Disk (VID: 090C PID: 1000) [class 00/00/00, no permission] rejected: no Android interface | if0 08/06/50 (bulkIn+bulkOut)
```

The `N usable for Android Auto` count and the per-device `accepted:`/`rejected:` reason answer
"why didn't my thing show up" directly. One nit: when a device is mid-re-enumeration the same
caller can log two device counts a few ms apart (`sees 1` then `sees 2`); harmless but a reporter
skimming might double-count. Not worth changing.

**3. The advisory text's third clause proved apt.** "…or a wireless adapter is waiting for its
phone before it presents itself" is exactly the U3 hypothesis, and it is the clause a real reporter
with an AA dongle most needs to read. Keep it.

**4. `hasMidi`/Google-VID accessory quirk.** When the Moto was toggled it briefly enumerated as
`18D1:4EE9` (Google VID) with a MIDI + ADB composite. OHU logged `accepted: ADB`. Not a problem,
just noting that "phone" descriptors are more varied than VID 22B8 alone.

---

## Verdict summary

| run | verdict |
|---|---|
| R0 build + unit tests | **PASS** (953/953) |
| R1-equiv empty bus at INFO | **PASS** (incidental, phone not rig) |
| R2-equiv dedupe | **PASS** |
| R-U1 non-phone reject path at INFO (BT dongle + flash disk) | **PASS** |
| R-U2 second phone accepted line (filter decision) | **PASS** — no false negative in any mode |
| R-U3 wireless AA adapter | **UNTESTABLE** (no adapter) |

**Ships:** the diagnostic itself is behaviour-clean and the output is good. Two follow-ups it
surfaced: (a) brief §4 / R3 must use `log-level=0`, not `exporter-log-level=2`; (b) the AOAP rule
false-positives on USB-ethernet adapters, which the planned manifest-filter narrowing will not
address — needs a predicate-level fix + JVM test using the ASIX descriptor recorded here.
