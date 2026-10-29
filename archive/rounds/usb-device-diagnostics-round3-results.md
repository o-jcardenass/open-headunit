# usb-device-diagnostics — round 3 results

**Candidate:** `feat/usb-device-list-diagnostics` @ `bb56a286` (4 commits on `main` @ `4849903d`)
**Baseline:** `main` @ `4849903d` (the pre-branch build, match-all `usb_device_filter.xml`)
**APK md5:** candidate `4f96e4b54a8a516a98e97cac58ce0f86` (`3.3.0-beta4`, versionCode 103) /
baseline `ec6f67eaa99f1f1f8613ac92fc88f7b4` (`3.3.0-beta3`, versionCode 102)
**Unit:** NOT the MT50 rig. USB host = **motorola edge 30 neo** (`miami`, Snapdragon 695,
Android 14), on wireless adb `192.168.1.5:38729` so the USB-C port was free for OTG the whole
round. Second phones plugged into the moto: **Xiaomi Poco X3 NFC** (`4f4027e9`, Android 15
LineageOS) and **Samsung** (`SAMSUNG_Android`, serial `R5CY90XYBED`). Dock / flash disk / BT
dongle same family as rounds 1 and 2.
**Date:** 2026-08-31

---

## Headline

**Both of round 2's FAILs are cleared.**

1. **R1 (ethernet adapter still accepted): FIXED by `bb56a286`.** The adapter's vendor-only
   enumeration (`mClass=255`, single unnamed `FF/FF/00` interface, no CDC sibling) is now
   `rejected: vendor-class device, not a phone`, and its composite enumeration (`mClass=0`, all
   three configs flattened, CDC siblings visible) is `rejected: CDC network adapter`. Both forms
   captured in one replug with full `UsbHostManager` descriptors behind them. No auto-connect on
   either.

2. **F1 (phone in file transfer no longer offered to OHU): NOT a regression from `4f0cc471`.**
   Measured this round with the baseline arm round 2 never ran. A phone in File-transfer mode with
   **USB debugging off** raises **no** system USB dialog and does not launch OHU **on the match-all
   baseline `4849903d` either**. Confirmed on both the Poco and the Samsung. What actually happens
   on both builds: the host's own MTP stack (`com.android.mtp`) claims the `06/01/01` interface and
   posts a `Connected to <device>` notification on the `USB` channel. There is no chooser to
   suppress. Round 2's "it worked before `4f0cc471`" was the debugging-**on** case, where the phone
   also exposes an ADB interface (`FF/42/01`) and that path *does* raise `UsbConfirmActivity` on the
   candidate (seen in R2).

So `4f0cc471` is neither helping nor hurting the file-transfer path, and `bb56a286` fixes the one
real defect. The branch has no measured blocker left.

---

## Setup notes

- **Host is the moto edge 30 neo, on wireless adb, port free for OTG** (same arrangement as round
  2). R0 and R5 are PC / any-device; R1 to R4 are the moto's OTG bus. All OHU verdicts and all
  descriptors are read moto-side; the phones-under-test in R4 have debugging off, so there is no
  adb to them by design.
- **No USB default handler was ever set for OHU on this host.** `dumpsys package
  com.andrerinas.headunitrevived | grep -A30 "Preferred Activities"` is empty before and after
  every R4 attach, on both builds. The brief's "Settings, Apps, Open Headunit, Open by default,
  Clear defaults" step was therefore a no-op (nothing to clear), so "no dialog" here is not
  "launched silently as the default handler" being mistaken for "not offered". OHU's process was
  also force-stopped before each R4 attach and `pidof` confirmed it never came back.
- **`bb56a286` builds as versionName `3.3.0-beta4`, versionCode 103** (unchanged from round 2's
  `4f0cc471`, no bump). Baseline `4849903d` builds as `3.3.0-beta3`, versionCode 102.
  `adb install -r -d` used for every swap; `settings.xml` verified byte-identical after each
  (`run-as cat | diff`), and restored byte-identical from a pre-round backup at the end.
- **Motorola logcat needs source-side tag filters** (round 2's finding, held again). Captures used
  `-s UsbHostManager:D ActivityTaskManager:I OPENHU:V` and `logcat -c` + `setsid stdbuf -oL`.
  `pkill -f` on a pattern matching the shell wrapper killed the parent shell once (exit 144, the
  known trap); switched to pid-based kills.
- **The candidate's "USB list sees N" verdict header** is now the string for every trigger except
  the in-app button (`USB button sees N`) and the foreground service (`service scan (force=true)
  sees N`). The endpoint summary prints counts (`1xbulkIn+2xbulkOut+1 other`), not flags, as the
  brief says. Every `bulkIn+bulkOut` grep from earlier rounds updated accordingly.
- **The adapter alternates between its two enumerations within a single settle**, not just plug to
  plug. In `r1_replug1_usbhost.log` it enumerated composite (`002/005`, `mClass=0`) at `19:04:40`,
  was removed at `19:04:42.1`, and re-added vendor-only (`002/007`, `mClass=255`) at `19:04:42.8`,
  all inside one replug. Both OHU verdicts landed on the matching form.
- **Scripts:** `build_hur.sh`, `run_unit_tests.sh` (R0); `set_pref.sh` (log-level); a round-local
  `cap.sh` helper in `hur-wifi-test-scripts/round-usb-device-diagnostics-r3/` (arms the two
  readers, taps the USB button once, tears down). `restore_settings.sh` for the settings restore.
- **Device restored:** candidate APK reinstalled and left installed (`-r -d`). `settings.xml`
  pushed back byte-identical (`diff` clean). All `adb logcat` readers killed by pid, confirmed
  zero. Dock / dongle / phones unplugged.

---

## R0 — build gate

**PASS**

- `assembleGithubDebug` via `build_hur.sh`: **BUILD SUCCESSFUL** (1m 09s). APK md5
  `4f96e4b54a8a516a98e97cac58ce0f86`, versionCode 103 / `3.3.0-beta4`.
- `testGithubDebugUnitTest` via `run_unit_tests.sh`: **BUILD SUCCESSFUL**, result XML totals
  **tests=971, failures=0, errors=0, skipped=0**.
- `UsbDeviceIdentityPolicyTest` went **13 to 18** (`tests="18"` in its result XML), matching the
  brief's "966 plus five".

---

## R1 — the adapter, both forms

**PASS** — both enumerations `rejected`, neither auto-connects, both with a fresh `UsbHostManager`
descriptor on record.

- Settings written: `log-level=0`.
- Bus: the round-2 dock (Generic USB3.0 Card Reader `05E3:0749`, ASIX AX88179B `0B95:1790`, Fresco
  Logic Billboard `1D5C:7102`; Kingston DataTraveler G3 `0930:6545` appeared late on one plug).
- Discard-rule check: n/a (not a connection run). Plugged once, then one unplug/replug; the replug
  alone produced both enumerations. Verdict identical across every logDeviceList in the window.

Decisive lines, `evidence/usb-device-diagnostics-round3/r1_replug1_ohu_filtered.log`:

```
19:04:40.069  ASIX AX88179B (VID: 0B95 PID: 1790) [class 00/00/00, no permission] rejected: CDC network adapter | if0 FF/FF/00 (1xbulkIn+2xbulkOut+1 other) | if1 02/0D/00 (1 other) | if2 0A/00/01 (no endpoints) | if3 0A/00/01 (1xbulkIn+1xbulkOut) | if4 02/06/00 (1 other) | if5 0A/00/00 (no endpoints) | if6 0A/00/00 (1xbulkIn+1xbulkOut)
19:04:42.878  ASIX AX88179B (VID: 0B95 PID: 1790) [class FF/FF/00, no permission] rejected: vendor-class device, not a phone | if0 FF/FF/00 (1xbulkIn+2xbulkOut+1 other)
19:04:05.336  Fresco Logic, Inc Generic Billboard Device (VID: 1D5C PID: 7102) [class 11/00/00, no permission] rejected: device class 0x11 is not a phone | if0 11/00/00 (no endpoints)
```

- **No `USB button: Single device found` and no `beginAutoConnect`** for the adapter on either form
  (grep clean across `r1_plug1_ohu.log` and `r1_replug1_ohu_filtered.log`). The USB-button path saw
  `0 usable` every time.
- **Billboard now reads `rejected: device class 0x11 is not a phone`**, as asked.
- Card reader: `rejected: no Android interface` (correct).

`UsbHostManager` descriptors, verbatim in `r1_replug1_usbhost.log`:

- **composite form** `002/005` `mClass=0`: config 1 `if0 Network_Interface FF/FF/00` (endpoints
  0x81 interrupt-in, 0x82 bulk-in, 0x03 bulk-out, 0x05 bulk-out); config 2 `02/0D/00` + `0A/00/01`;
  config 3 `02/06/00` + `0A/00/00`. The app's flat interface list shows all seven, so `getInterfaceList()`
  does aggregate configs on this Android 14 device (settles brief §1) and the CDC siblings are
  visible to the veto.
- **vendor-only form** `002/007` `mClass=255`: config 1 only, `if0 Network_Interface FF/FF/00`,
  same four endpoints, no CDC anywhere. Only the device class is left to judge it on, and the new
  veto does.

**Endpoint counts for the adapter's `FF/FF/00` interface:** `1xbulkIn + 2xbulkOut + 1 other` (the
"1 other" is the 0x81 interrupt-in at `mMaxPacketSize=16`). The second bulk OUT (0x03 and 0x05,
both `mMaxPacketSize=512`) is the one thing the old flag-based summary hid. A phone in file transfer
on this rig shows `1xbulkIn+1xbulkOut` (Poco `06/01/01`) or `1xbulkIn+1xbulkOut+1 other`, so a
"two bulk OUT" rule would in fact separate this adapter from every phone form captured this round.
Worth considering as a cheap second signal, though the device-class veto already covers it.

---

## R2 — the phone is still accepted in every mode

**PASS** — every form the Poco presented was `accepted:`, none `rejected:`. The device-class veto
sits after the ADB/PTP/accessory checks in the accessory branch, so a phone never reaches it.

- Settings: `log-level=0`. Poco plugged into the moto directly, USB debugging **on** for this run.

| Poco USB form (as enumerated) | VID:PID | interfaces | verdict |
|---|---|---|---|
| file transfer, Google VID | `18D1:4EE2` | `if0 06/01/01` + `if1 FF/42/01` | **accepted: PTP** |
| PTP selected | `18D1:4EE6` | `if0 06/01/01` + `if1 FF/42/01` | **accepted: PTP** |
| "No data transfer" selected | `05C6:90DB` (kept ADB) | `FF/FF/30`,`FF/FF/40`,`FF/FF/50`,`FF/FF/80`,`FF/FF/70`,**`FF/42/01`** | **accepted: ADB** |
| after OHU's accessory switch | `18D1:2D01` | `if0 FF/FF/00` + `if1 FF/42/01` | **accepted: already in accessory mode** |

```
19:14:17.217  Xiaomi POCO X3 NFC (VID: 18D1 PID: 4EE6) [class 00/00/00, no permission] accepted: PTP | if0 06/01/01 (1xbulkIn+1xbulkOut+1 other) | if1 FF/42/01 (1xbulkIn+1xbulkOut)
19:14:33.089  Xiaomi POCO X3 NFC (VID: 05C6 PID: 90DB) [class 00/00/00, no permission] accepted: ADB | if0 FF/FF/30 (1xbulkIn+1xbulkOut) | ... | if5 FF/42/01 (1xbulkIn+1xbulkOut)
19:13:23.315  Xiaomi POCO X3 NFC (VID: 18D1 PID: 2D01) [class 00/00/00, no permission] accepted: already in accessory mode | if0 FF/FF/00 (1xbulkIn+1xbulkOut) | if1 FF/42/01 (1xbulkIn+1xbulkOut)
```

Same as round 2: a dev phone with debugging on never drops to a bare charging/MTP state
("No data transfer" still carried the ADB interface). The candidate additionally completed a full
projection session over the accessory-mode USB link during this run (Maps + Spotify on the moto),
so the accept verdict was exercised end to end, not just logged.

---

## R3 — no dialog for things that are not phones

**PASS** for the Bluetooth dongle and (expected) for the dock. **INCONCLUSIVE** for the bare flash
disk (no OTG adapter to isolate it, same as rounds 1 and 2).

- Settings: default `log-level`.

| device | system USB dialog | note |
|---|---|---|
| **Bluetooth-audio dongle alone** (TaiYiLian UGREEN-BT701, `0A12:4007`) | **none** — zero `systemui.usb.*` START in the whole dongle-alone window (`r3_btdongle.log`, attach at `19:10:10`, nothing after) | OHU verdict `rejected: no Android interface | if0 03/00/00 ...`, `0 usable`; service logged `Ignoring non-Android USB device` |
| **dock with the ASIX** | `systemui.usb.UsbResolverActivity` displayed (`+115ms`), **once per enumeration** | expected. The `FF/FF/00` interface (present in config 1 even in the composite form) matches the manifest vendor-class entry, and no manifest can suppress it. The adapter cycled composite -> vendor-only during the replug, so two resolver launches were seen; a stable single enumeration is one. No `UsbConfirmActivity` or `UsbPermissionActivity` for the card reader or billboard. |
| **USB flash disk** | not isolable | only reachable via the dock; `08/06/50` matches no filter entry so it cannot raise an OHU dialog, but "no dialog" was not observed directly |

`grep "systemui.*usb"` over the dongle-alone window (after `19:10:10`): **0 lines.**

---

## R4 — F1, controlled

**PASS (no regression).** The candidate shows no dialog for a phone in File transfer with USB
debugging off, and so does the match-all baseline `4849903d`. F1 as a regression from `4f0cc471`
is refuted. Confirmed on two OEMs.

Per phone: OHU force-stopped, no USB default handler (verified empty before and after), descriptor
captured moto-side with `-s UsbHostManager:D`, dialog and launch watched with
`-s ActivityTaskManager:I` plus a foreground-activity / `pidof` check, then the same on baseline
`4849903d`, then candidate reinstalled.

### Xiaomi Poco X3 NFC, File transfer, USB debugging OFF

**Descriptor (candidate and baseline identical):** `vidpid 18d1:4ee1`, `mClass=0`, config 1
`mName=android`, one interface:

```
UsbInterface[mId=0,mAlternateSetting=0,mName=MTP,mClass=6,mSubclass=1,mProtocol=1,mEndpoints=[
  UsbEndpoint[mAddress=129,mAttributes=2,...]   bulk in
  UsbEndpoint[mAddress=1,mAttributes=2,...]     bulk out
  UsbEndpoint[mAddress=130,mAttributes=3,...]]] interrupt in
```

That is the clean `06/01/01` PTP class, named "MTP" — **not** the vendor-specific `FF/FF/xx` round
2 assumed (round 2 saw `FF/FF/30...` because debugging was on). The candidate's
`usb_device_filter.xml` **has** `<usb-device class="6" subclass="1" protocol="1" />`, so the
descriptor does not explain the missing dialog.

| build | dialog | OHU launched | preferred activity |
|---|---|---|---|
| **candidate `bb56a286`** | none — zero `systemui.usb.*`, zero `ActivityTaskManager` START | no — foreground stayed `com.qqlabs.minimalistlauncher`, `pidof` empty | none before, none after |
| **baseline `4849903d`** (match-all `<usb-device />`) | none — same | no — same | none |

Baseline's registered filter confirmed match-all in `dumpsys usb`
(`vendor_id=-1 product_id=-1 class=-1 subclass=-1 protocol=-1` for `UsbAttachedActivity`), and it
still did not fire. What did happen on both builds: a notification, `pkg=android` `channel=USB`,
`android.title="Connected to POCO X3 NFC"` — the host MTP stack claiming the device.

### Samsung (`SAMSUNG_Android`, `R5CY90XYBED`), File transfer, USB debugging OFF

**Descriptor (candidate and baseline identical):** `vidpid 04e8:6860`, `mClass=0`, config
`sec_acm` / `mtp_acm`:

```
if0  mName=MTP                                mClass=6,  mSubclass=1, mProtocol=1
if1  mName=CDC Abstract Control Model (ACM)   mClass=2,  mSubclass=2, mProtocol=1
if2  mName=CDC ACM Data                       mClass=10, mSubclass=0, mProtocol=0
```

Same `06/01/01` MTP interface (plus a CDC ACM pair Xiaomi/Google phones do not carry).

| build | dialog | OHU launched |
|---|---|---|
| **candidate `bb56a286`** | none | no — foreground `com.qqlabs.minimalistlauncher`, `pidof` empty |
| **baseline `4849903d`** | none | no — same |

Notification `android.title="Connected to SAMSUNG_Android"`, `channel=USB`, on both builds.

### What this means

- The file-transfer / debugging-off path never had a USB chooser on this host, on any filter. The
  host's `com.android.mtp` claims the `06/01/01` interface and notifies instead of prompting.
- Round 2's F1 ("regressed on three OEMs") measured the *debugging-on* descriptor
  (`05C6:90DB` + `FF/42/01`). That path raises `UsbConfirmActivity` on the candidate (R2 this round,
  `19:12:05`, and round 2 R3). So OHU is still offered for a phone that exposes ADB, on the
  narrowed filter.
- The manifest narrowing `4f0cc471` therefore does not regress the common wired path, because that
  path produces no dialog regardless. The recommendation to revert it for F1's sake does not hold
  up to the baseline measurement.
- Not covered: whether a phone can be made to present a *pure* AOSP MTP with no ADB and no host
  MTP handler installed (a stripped head-unit ROM). On a normal Android host, "no chooser for a
  file-transfer phone" is host behavior, not an OHU filter width question.

---

## R5 — the empty bus, and the Verbose path

**PASS**

Runs on the moto (host), nothing attached (`host_connected=false`).

- Default `log-level=2` (`r5a_default.log`):
  ```
  18:58:34.314 I UsbDiagnostics: USB list sees 0 USB device(s), 0 usable for Android Auto
  18:58:34.315 I UsbDiagnostics: nothing is on the bus. Either the port carries no data, the unit is not in USB host mode, or a wireless adapter is waiting for its phone before it presents itself.
  ```
  Both at INFO.
- `log-level=0`, force-stop, relaunch (`r5b_verbose.log`): identical two lines, still INFO (always
  INFO on an empty bus), **no exception**, **no `could not read the USB device list`**.

---

## Verdict summary

| run | verdict |
|---|---|
| R0 build + unit tests | **PASS** (971/0, `UsbDeviceIdentityPolicyTest` 13 to 18) |
| R1 adapter both forms | **PASS** (`rejected: CDC network adapter` / `rejected: vendor-class device, not a phone`, no auto-connect, billboard `0x11`, both descriptors captured) |
| R2 phone accepted in every mode | **PASS** |
| R3 no dialog for non-phones | **PASS** (BT dongle zero; dock one resolver per enumeration, expected); **INCONCLUSIVE** (bare flash disk, not isolable) |
| R4 F1 controlled | **PASS (no regression)** — candidate == match-all baseline; no chooser for a debugging-off file-transfer phone on either, Poco and Samsung |
| R5 empty bus + Verbose path | **PASS** |

**Ship:** the branch. `bb56a286` fixes round 2's R1 FAIL, and round 2's F1 FAIL is refuted by the
baseline arm. No measured blocker remains.

Two smaller items for the maintainer's judgement, neither blocking:

- **`4f0cc471` can stay.** It does not regress the file-transfer path (no dialog there on any
  filter). It still lets a debugging-on phone through via the ADB entry. If it is kept, the PTP
  entry `<usb-device class="6" subclass="1" protocol="1" />` is currently dead weight on this
  class of host (the host MTP stack wins the race) but harmless, and it would matter on a host with
  no MTP handler.
- **The "two bulk OUT" shape** is, on everything captured this round, unique to the ethernet
  adapter's `FF/FF/00` interface. Not needed given the device-class veto, but a cheap belt-and-braces
  signal if a future adapter also reports `mClass=0` in its vendor-only form.

## Anything the brief did not ask about

- **With `0 usable` devices the USB button opens a device-list screen** (all entries shown
  "Ignored"), and that screen fires a system `UsbPermissionActivity` for one of the listed devices
  (saw it request the billboard). Different from round 2, where the ASIX was the sole "usable"
  device and the button hit `beginAutoConnect` directly. The candidate's list screen is the
  correct non-auto-connecting behavior; the stray permission prompt from it is a minor UX wrinkle,
  not a connect.
- **OHU stopped its own service once during the round** (`AapService.onStartCommand | Stop action
  received. Broadcasting finish request to activities.` at `19:12:01`, ~4 s before the Poco
  attached, with no adb command issued). Recovered on relaunch. Cause not chased; noting it because
  a self-issued stop that lands right as a device attaches would mask a verdict.
- **`HomeFragment`'s USB button** still calls `beginAutoConnect` the instant exactly one device
  passes `isAndroidDevice`, ignoring the allow / auto-start guard `UsbAttachedActivity` respects
  (seen in R2: `USB button: Single device found ... auto-connecting` with no guard check). Already
  on the backlog per round 2; did not fire for a non-phone this round because the adapter is now
  rejected.
- **The `UsbDiagnostics` output held up well** across ~40 captures. The count-based endpoint
  summary is a real improvement: `1xbulkIn+2xbulkOut+1 other` immediately flags the adapter's
  double bulk-OUT that the old `bulkIn+bulkOut` string hid.
