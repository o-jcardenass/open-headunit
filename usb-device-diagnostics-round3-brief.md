# usb-device-diagnostics — round 3 brief

**Candidate:** `feat/usb-device-list-diagnostics` @ `bb56a286` (4 commits on `main` @ `4849903d`)
**Baseline:** `4849903d` (`main`, the pre-branch build). **Required this round**, see §4.
**Round 2 results:** `usb-device-diagnostics-round2-results.md`.

Round 2 reported two FAILs. One is real and is fixed in `bb56a286`. The other is an observation
whose stated mechanism does not survive round 1's own evidence, and this round is built to settle it
properly rather than to argue about it.

## 1. What round 2 established, and what it did not

**R1's observation is right and the fix was wrong.** `bfda7808` rejected the ethernet adapter's
composite form and hardware still accepted it. The stated cause was that
`UsbDevice.getInterfaceCount()` returns only the active configuration. It does not: AOSP's
`getInterfaceList()` sums every interface of every `UsbConfiguration`, alternate settings included,
and round 1 logged that happening on this same adapter at `11:18:54`, seven interfaces:
`if0 FF/FF/00 | if1 02/0D/00 | if2 0A/00/01 | if3 0A/00/01 | if4 02/06/00 | if5 0A/00/00 |
if6 0A/00/00`, which is config 1 plus config 2 plus config 3 flattened.

What is actually happening is that **the adapter has two enumerations and alternates between them**.
Round 1 captured both, seconds apart:

| form | device class | interfaces | in round 1 at |
|---|---|---|---|
| composite | `00/00/00` | `FF/FF/00` plus six CDC | `11:18:54`, `11:18:56` |
| vendor-only | **`FF/FF/00`** | `FF/FF/00` alone | `11:17:15`, `11:18:58` |

The CDC-sibling veto covers the first. The second carries no CDC interface anywhere, so nothing
vetoed it. `bb56a286` adds the device-class veto: an Android gadget always defers to its interfaces
and reports device class 0, in every mode captured across both rounds, so a vendor class on the
*device* descriptor is the adapter declaring itself proprietary. It is the only difference between
that form and a phone in file transfer, whose interface carries the same triple, the same bulk pair
and the same interrupt endpoint.

**F1's mechanism is refuted; F1's observation is unmeasured.** F1 says
`<usb-device class="255" subclass="255" protocol="0" />` catches only AOSP-pure `f_mtp` and that
real OEMs use non-zero protocol bytes. Round 1's `UsbHostManager` capture of the moto edge 30 neo,
one of F1's own three phones, in file transfer:

```
UsbConfiguration[mId=1,mName=mtp,
  UsbInterface[mId=0,mName=MTP,mClass=255,mSubclass=255,mProtocol=0, ...]]
```

Exactly `FF/FF/00`, in a config with no ADB interface. The descriptor F1 cites instead is
`05C6:90DB`, Qualcomm's diag/modem/adb composite from debugging being on, and it contains
`FF/42/01`, which the filter also lists. AOSP `DeviceFilter.matches(UsbDevice)` tests the device
descriptor and then iterates every interface, returning true on any hit; the attributes parse as
decimal. R3 of round 2 recorded that same phone in file transfer raising `UsbConfirmActivity`
offering Open Headunit.

So the filter should match. Something else produced "no dialog". The F1 check ran after adb had
lapsed, so **no descriptor was captured for any of the three phones with debugging off**, there was
no baseline arm, and "no dialog" was never separated from "OHU launched silently as the default
handler". §4 is built to answer it.

## 2. What changed in the candidate

| commit | what |
|---|---|
| `9cf6efbe` | the diagnostic. Unchanged, round 1 and 2 both validated it. |
| `bfda7808` | the pure `UsbDeviceIdentityPolicy`. Unchanged. |
| `4f0cc471` | the narrowed `usb_device_filter.xml`. **Unchanged, deliberately.** F1 is being measured, not pre-emptively reverted. |
| `bb56a286` | **new.** The device-class veto, billboard in the excluded classes, and two instrumentation fixes below. |

Two log strings changed and the brief's greps depend on them:

- The accessory else-branch was `accepted: AOAP (unnamed vendor interface)` for *any* name we do
  not match, so round 2 could not tell a blank name from `Network_Interface` and concluded the
  wrong one. It is now `accepted: AOAP (vendor interface named 'X')`, or `named nothing`.
- The endpoint summary counted flags, so a second bulk OUT was invisible. It now prints
  `2xbulkIn+1xbulkOut+1 other` style counts. Every `bulkIn+bulkOut` grep from earlier rounds needs
  updating.

## 3. Where this runs

Same as round 2. The MT50 rig cannot host USB, so R1 to R4 need the host-capable phone, its dock,
flash disk, Bluetooth dongle and second phone. R0 and R5 run anywhere.

**Wireless adb on the host is required and must be held for the whole round.** Round 2 proved it
works; the F1 check failed only because the link was allowed to drop. If it drops, reconnect before
running anything, do not proceed on observation alone.

**Filter logcat at the source on the Motorola host.** An unfiltered stream loses every OHU line in
ROM spam and a post-hoc `logcat -d` misses the window. Use `adb logcat -v time OPENHU:V '*:S'` for
every OHU capture. For the descriptor captures in §4 and R1 you need the opposite, so run a second
reader: `adb logcat -v time -s UsbHostManager:D`.

## 4. Runs

### R0 — build gate

Build and unit-test `bb56a286`. Expect `BUILD SUCCESSFUL` and **971 tests, 0 failures** (966 plus
five in `UsbDeviceIdentityPolicyTest`, which goes from 13 to 18).

### R1 — the adapter, both forms

`log-level=0`. Same dock. **Plug and replug until both enumerations have been seen**, because the
adapter alternates and one plug proves nothing. For each plug capture the `UsbHostManager: Added
device` block as well as the OHU verdict line, so the vendor-only form finally has a real descriptor
on record. Round 2's `asix-ax88179b-descriptors.txt` has none behind it and its `mClass=0`
contradicts the app's own `[class FF/FF/00]` from the same plug, so treat that file as superseded by
whatever this round captures.

PASS requires, on **both** forms:

- vendor-only form: `rejected: vendor-class device, not a phone`
- composite form: `rejected: CDC network adapter`
- no `USB button: Single device found` and no `beginAutoConnect` for the adapter, on either
- the billboard device now reads `rejected: device class 0x11 is not a phone`

Report the endpoint counts the new summary prints for the adapter's `FF/FF/00` interface. It is the
first time they are visible and it decides whether a shape rule is worth having as well.

### R2 — the phone is still accepted in every mode

Unchanged from round 2 and expected to stay PASS. The device-class veto sits in the accessory
branch, so a phone declaring ADB or PTP never reaches it. Sweep whatever modes the second phone will
give and record the verdict for each.

### R3 — no dialog for things that are not phones

Unchanged from round 2. Bluetooth dongle alone must still raise zero `systemui.usb.*`. The dock
still raises one and that is expected: the adapter's device descriptor is `FF/FF/00`, so it matches
the vendor-class entry before interfaces are even reached, and no manifest can suppress it.

### R4 — F1, controlled

**This is the run the round exists for.** Three phones: Xiaomi Poco X3 NFC, motorola edge 30 neo,
Samsung. For each, in **File transfer / Android Auto** mode with **USB debugging off**.

Before the first attach of each phone, on the host: Settings, Apps, Open Headunit, Open by default,
Clear defaults. A default handler launches OHU with no dialog, which looks identical to not being
offered, and round 2 could not rule it out.

Per phone, three things, in this order:

1. **The descriptor.** `adb logcat -v time -s UsbHostManager:D` on the host across the attach.
   Record the whole `Added device UsbDevice[...]` block including `mClass` and every
   `UsbInterface[...]` line. **This is the blocking measurement.** If the interface is `FF/FF/00`,
   the filter matches and the observation has some other cause.
2. **Dialog and launch, separately.** Record whether any `systemui.usb.*` activity is displayed,
   *and* whether OHU came to the foreground without one. Take
   `adb shell dumpsys package com.andrerinas.headunitrevived | grep -A30 "Preferred Activities"`
   before and after the attach.
3. **The baseline arm.** Install `4849903d` (the pre-branch `main`, match-all filter) and repeat 1
   and 2 with the same phone in the same mode. Round 2 asserted "it worked before `4f0cc471`"
   without measuring it, so this round measures it. Reinstall the candidate afterwards.

PASS is the dialog appearing on the candidate, or the descriptor explaining why it does not. FAIL
with the descriptor attached is just as useful and is the outcome that decides whether the manifest
narrowing survives.

### R5 — the empty bus

Unchanged. Both log levels, no exception, the advisory at INFO.

## 5. Do not re-run

- The `log-level` key. Round 2 confirmed `<int name="log-level" value="0" />` is VERBOSE and `2` is
  INFO.
- The AOA probe being harmless on a non-phone. Round 2 measured it: the adapter answered
  `Error controlTransfer len: -1` and `connectAndSwitch` returned false, with no ill effect.
- Whether `getInterfaceCount()` aggregates configurations. It does; see §1.

## 6. Report back

The standing format in `TESTING-TEMPLATE.md` §7. Two things this round specifically wants:

- **Every descriptor capture, verbatim, in `evidence/usb-device-diagnostics-round3/`.** Round 2's
  conclusions rested on a reconstructed file with no capture behind it and that is what has to be
  avoided. Raw `UsbHostManager` output only, no transcription.
- **`HomeFragment`'s USB button** calls `beginAutoConnect` whenever exactly one device passes the
  predicate, ignoring the allow / auto-start guard `UsbAttachedActivity` respects. That is already
  scheduled as its own step and does not need investigating again, but note it if it fires.
