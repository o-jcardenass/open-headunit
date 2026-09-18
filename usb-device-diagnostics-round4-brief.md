# usb-device-diagnostics — round 4 brief

**Candidate:** `feat/usb-device-list-diagnostics` @ `a9df2135` (7 commits on `main` @ `4849903d`)
**Baseline:** none. Round 3 is the before-state and its captures are in
`evidence/usb-device-diagnostics-round3/`.
**Round 3 results:** `usb-device-diagnostics-round3-results.md`, all PASS.

Round 3 cleared the diagnostic, the device predicate and the manifest filter. Three commits have
landed since and none has been on hardware. Two of them are the blacklist, which no round has ever
exercised at all.

**Read `TESTING-TEMPLATE.md` §7b before planning this round.** It is new, it is about running USB
rounds on a phone host, and two of its bullets change how runs here have to be set up.

## 1. What changed since round 3

| commit | what | round 3 saw it |
|---|---|---|
| `9cf6efbe` | the diagnostic | yes |
| `bfda7808` | `UsbDeviceIdentityPolicy` | yes |
| `4f0cc471` | the narrowed manifest filter | yes |
| `bb56a286` | the device-class veto, billboard, endpoint counts | yes |
| `ad082fff` | the CDC veto keys on the control subclass | **no** |
| `58a7398e` | the blacklist gates every path, and survives a locked boot | **no** |
| `a9df2135` | the blacklist key is the device name, not VID:PID | **no** |

**`ad082fff`.** Round 3's Samsung ships a CDC ACM pair in ordinary file transfer, and its data
interface (`0A/00/00` with a bulk pair) is byte-identical to the ethernet adapter's ECM data. The
network veto keyed on the CDC classes, so it matched that phone. It never fired because PTP is
decided first, but the phone was one missing interface name from a false reject. The veto now keys
on the control subclass, ECM/EEM/NCM/MBIM against ACM's `0x02`.

**`58a7398e`.** A blacklisted device was still connected to. The check ran in one of seven call
paths and was skipped entirely when the user had not unlocked. One gate now,
`UsbDeviceCompat.isConnectable`, used by the USB button, the service scan, the attach listener,
`UsbAttachedActivity` and the device list's own start button, which used to render a row
"Blacklisted" in red and connect to it anyway. The list is mirrored to device-protected storage the
way the auto-start settings already are.

**`a9df2135`.** A phone's VID:PID changes with its USB mode: this branch's own evidence has one
handset as `18D1:4EE1`, `05C6:90DB` and `18D1:2D01` with `Xiaomi` / `POCO X3 NFC` throughout. The
key is now manufacturer plus product, falling back to VID:PID for devices with no string
descriptors.

## 2. Two log strings this round depends on

- A blacklisted device prints `rejected: blacklisted by the user` in the `UsbDiagnostics:` dump.
  That is the self-check for every arm below: if it does not appear, the key was written wrong and
  the run is measuring nothing.
- The endpoint summary prints counts (`1xbulkIn+2xbulkOut+1 other`), not flags. Round 3 already
  used this; any older grep needs updating.

## 3. Writing a blacklist entry

Per §1 of the template, write `shared_prefs/settings.xml` with the app stopped. It is a string set,
which is a different shape from the scalar prefs earlier rounds wrote:

```xml
<set name="usb-blacklist">
    <string>name:xiaomi poco x3 nfc</string>
</set>
```

**The key is the manufacturer and the product, joined by one space, lower-cased, with a `name:`
prefix.** It is the `UsbDiagnostics:` line's device name with the ` (VID: … PID: …)` part removed.
For `Xiaomi POCO X3 NFC (VID: 18D1 PID: 4EE1)` the key is `name:xiaomi poco x3 nfc`.

**The device-protected mirror is written by the app, not by your edit.** Launch the app once after
writing and before judging anything: `App.onCreate` copies the list across. A run that writes the
file and goes straight to plugging in is testing the unmirrored state.

Confirm the entry took before every arm, by plugging the device in and reading the verdict line.
Do not infer it from the file.

## 4. Runs

### R0 — build gate

Build and unit-test `a9df2135`. Expect `BUILD SUCCESSFUL` and **984 tests, 0 failures**.
`UsbBlacklistPolicyTest` is new at **11**; `UsbDeviceIdentityPolicyTest` goes 18 to **20**.

### R1 — a blacklisted device is refused on every path that acts

`log-level=0`. Blacklist the **second phone** (§3), not the adapter: the adapter is now rejected on
its descriptors anyway, so it cannot tell the blacklist apart from the policy.

Drive each path in turn and read the verdict. `AapService` is `exported="false"`, so there is no
`am startservice` route to the scan; reach it through the attach fallback or the app's own launch.

| path | how to reach it | expected |
|---|---|---|
| USB attach | plug the phone in | `UsbAttachedActivity: Ignored blacklisted USB device` |
| service scan | plug in with `AapService` already running and wait out the 2 s attach fallback | `service scan (force=true) sees N …`, `rejected: blacklisted by the user`, `0 usable` |
| attach listener | same attach, service already running | `Ignoring USB device attached in service … rejected: blacklisted by the user` |
| USB button | tap it with the phone the only device on the bus | **the device-list screen opens**, no `USB button: Single device found`, no `beginAutoConnect` |

The USB-button expectation is not "nothing happens". With the phone blacklisted the count of usable
devices drops to zero, and zero or several is what opens the list. Round 3 also saw that screen
raise a system `UsbPermissionActivity` for one of the listed devices; if it does so again, record it
and carry on, it is a known wrinkle and not a connect.

**FAIL if** any `beginAutoConnect`, `connectAndSwitch`, `Switching USB device to accessory mode` or
`Requesting USB permission` line names the blacklisted phone.

### R2 — the device list refuses its own start button

Same blacklist. Open the USB device list. The phone's row must still be **listed** and labelled
`Blacklisted` in red: it has to stay visible or it can never be taken off the list.

Press its **start** button. Expected: a `Blacklisted` toast and nothing else. This is the defect the
commit exists for, so quote the full window either way.

Then cycle the allow button once (Blacklisted goes to Ignored), press start again, and confirm the
device connects normally. Un-blacklisting has to restore it or the feature is a one-way door.

### R3 — the key survives a USB mode change

The point of `a9df2135`. With the phone blacklisted in **one** mode, sweep the others and confirm
every one is still `rejected: blacklisted by the user`. Whatever modes the phone will give:
file transfer, PTP, charging, and with USB debugging both on and off.

Then the arm that matters most, because the old key missed it entirely: get the phone to appear
**already in accessory mode** (`18D1:2D0x`). Easiest route is to un-blacklist it, let it switch,
then blacklist it and force a service scan while it is still in accessory mode. The service scan
checks accessory-mode devices before anything else, so this is where a VID:PID key let a blacklisted
phone straight back in.

Report the VID:PID for every mode alongside the verdict. Different numbers with the same verdict is
the whole result.

### R4 — an entry from an older build still matches

Write a bare, unprefixed entry for one device and confirm it is still refused:

```xml
<set name="usb-blacklist">
    <string>0930:6545</string>
</set>
```

The blacklist is beta and users will be told to redo theirs for the stable release, so this is
read leniency rather than a migration. It exists so an existing list degrades visibly rather than
silently, which is the failure class rounds 1 to 3 kept producing.

### R5 — the mirror applies before the user unlocks

**Needs a screen lock on the host.** Without one `isUserUnlocked()` is true from boot and this path
is never reached, so the run is **UNTESTABLE** rather than PASS. Say which it was.

Set a PIN on the host, blacklist the phone, reboot, and plug the phone in **at the lock screen**
before unlocking. Expected: the same `Ignored blacklisted USB device` line, and no AOA switch.
Before the fix this cold-boot path switched a device the user had refused.

Remove the PIN afterwards and say so in Setup notes.

### R6 — regression, round 3's verdicts have not moved

Nothing here should have changed. Re-run round 3's R1 to R5 with **no** blacklist entries at all:

- the adapter, both enumerations: `rejected: CDC network adapter` and
  `rejected: vendor-class device, not a phone`, no auto-connect
- the billboard: `rejected: device class 0x11 is not a phone`
- the second phone: `accepted:` in every mode it presents
- the Bluetooth dongle alone: zero `systemui.usb.*`
- the empty bus: the advisory at INFO, no exception

Plus one arm `ad082fff` added: the **Samsung** in file transfer with debugging off must be
`accepted: PTP`. Its CDC ACM pair is what the old veto matched, so this is the regression check for
that commit and it needs the Samsung specifically, not the Poco.

### R7 — measurement only, how many permission prompts one connect costs

No PASS or FAIL. The maintainer saw the "Allow Open Headunit to access the USB device?" prompt three
times in one sitting; two per connect are structural and a third is not. Template §7b explains the
mechanism and the count.

**Read `dumpsys usb` first.** If a persistent grant already exists for the package, say so and mark
this INCONCLUSIVE. Clearing one needs §7b's uninstall exception, which costs the setup wizard and a
settings restore, so take it only if the rest of the round is already done.

With no grant present, capture one full connect and report the **identities**, not the number:

```bash
adb logcat -v time OPENHU:V '*:S' | grep -E "Requesting USB permission|no permission"
```

Three distinct VID:PIDs is structural. The same identity twice is a defect and worth its own round.

## 5. Do not re-run

- Whether `getInterfaceCount()` aggregates configurations. Round 3 settled it: the composite form's
  seven interfaces span three configs.
- Whether the manifest narrowing regresses a file-transfer phone. Round 3's baseline arm settled it,
  and §7b carries the mechanism.
- The AOA probe on a non-phone. Round 2 measured it harmless.

## 6. Report back

The standing format in `TESTING-TEMPLATE.md` §7. Two things this round wants specifically:

- **The verdict line for every arm, quoted.** The whole round turns on one string, and an arm that
  cannot show it has not been run.
- **Which modes the second phone actually gave you.** Round 2 and round 3 both found modes that
  could not be produced on a dev phone. An arm that could not be reached is UNTESTABLE and worth
  more than a PASS on an arm that was never exercised.
