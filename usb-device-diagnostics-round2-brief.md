# usb-device-diagnostics — round 2 brief

**Candidate:** `feat/usb-device-list-diagnostics` @ `4f0cc471` (3 commits on `main` @ `4849903d`)
**Baseline:** none needed. Round 1 is the baseline and its captures are in
`evidence/usb-device-diagnostics-round1/`.
**Round 1 results:** `usb-device-diagnostics-round1-results.md`, all PASS.

Round 1 was paused partway. Nothing from it needs re-running on the rig except R0 and the empty-bus
arm, both listed below, because the branch has grown from one commit to three and the two new ones
change what a device list says.

## 1. What changed since round 1

| commit | what |
|---|---|
| `9cf6efbe` | the diagnostic itself, unchanged, round 1 validated it |
| `bfda7808` | the rules move into a pure `UsbDeviceIdentityPolicy`, and a USB ethernet adapter is no longer treated as an Android accessory |
| `4f0cc471` | `usb_device_filter.xml` narrows from match-all to the triples the policy accepts |

Round 1 found the defect `bfda7808` fixes. An **ASIX AX88179A** gigabit-ethernet controller, built
into the dock used that round, was `accepted: AOAP` and OHU auto-connected to it. Its CDC control
interface is `FF/FF/00` with a bulk pair, which is the genuine accessory triple, so the rule could
not simply be tightened. What separates it from a phone is the interface name, and failing that its
CDC siblings.

Both are proven against the round 1 descriptors in `UsbDeviceIdentityPolicyTest`. **This round is
the hardware check of the same two devices.**

## 2. Where this runs

The MT50 rig cannot host USB (§7a, `host_connected=false`), so R1 to R4 need the host-capable phone
round 1 used, with the same dock, flash disk, Bluetooth dongle and second phone. R0 and R5 run
anywhere and are the rig's half if the phone is not available.

**Any USB ethernet adapter will do, not the one round 1 used.** The rule keys on a vendor-class
`FF/FF/00` interface with a bulk pair on a device that also carries CDC interfaces, and nothing in
it names a chip. Most USB-C docks that carry ethernet present exactly that shape, because the
controller ships a vendor-specific configuration for its own driver alongside a CDC one for
generic hosts.

Which case a given dock is in decides what R1 can claim, so establish it first. Attach the dock
with nothing else plugged in and read the raw descriptors:

Attach the dock over **wireless adb**, because the dock occupies the port a USB adb link would be
using. Round 1 ran that way (`adb connect <phone>:<port>`) and the port was free the whole round.

```bash
adb logcat -c && echo "plug the dock in now" && sleep 10 && \
  adb logcat -d | grep -A25 "USB device attached"
```

The ten seconds are the window to plug it in. `UsbHostManager` prints these at DEBUG, which is
logcat's default, so no setting is needed for this step.

- **A vendor-class `mClass=255,mSubclass=255,mProtocol=0` interface plus any `mClass=2` or
  `mClass=10` interface.** This is the shape the fix is for. R1 runs as written.
- **CDC interfaces only, no vendor-class one.** The old predicate never accepted it either, so it
  cannot show the fix working. It still verifies the adapter is refused, on the
  `rejected: no Android interface` rule rather than the new one. Say which rule fired and mark R1
  INCONCLUSIVE for the CDC veto specifically.

In the first case there is a cheap true A/B available, because round 1 left its own candidate
installed. Attach the dock on that build first and record the verdict, which should be
`accepted: AOAP` plus an auto-connect line, then install this round's and repeat. That is worth
more than either capture alone and costs one install.

Report the adapter's VID:PID and interface list whichever case it is. Round 1's is an ASIX
AX88179A (`0B95:1790`) and is a JVM fixture; a second real descriptor is worth having as another.

The HDMI port on such a dock is normally DisplayPort alt-mode rather than a USB device, so expect
it not to appear on the bus at all. If something video-shaped does appear, capture it: an unplanned
device is exactly what round 1's best finding came from.

## 3. Settings keys

| key | element | value | why |
|---|---|---|---|
| `log-level` | `<int name="log-level" value="0" />` | 0 (VERBOSE) | R2 only. Everything else runs at the default 2. |

`exporterLogLevel` is the Kotlin property; the pref key is `log-level` and it stores a
`LogExporter.LogLevel` **ordinal**, so `VERBOSE=0, DEBUG=1, INFO=2`. Round 1 caught this brief
naming the wrong key. Read the value back before starting R2: at INFO that run measures nothing.

**Why only R2 needs it.** `UsbDeviceDiagnostics` prints at INFO when the count of usable devices is
zero and at Verbose otherwise, so a *rejected* device is visible at the default level and an
*accepted* one is not. R1, R3 and R4 all turn on a rejection.

## 4. The lines that decide every run

The verdict text is assembled at runtime as `"accepted: " + reason` or `"rejected: " + reason`, so
the full strings below are **not** greppable in the source. The reason halves are, in
`UsbDeviceIdentityPolicy.kt`. Grep the log for the whole string; grep the source for the half.

```
UsbDiagnostics: <caller> sees N USB device(s), M usable for Android Auto
UsbDiagnostics:   <name> (VID: xxxx PID: xxxx) [class xx/xx/xx, permission] <verdict> | if0 ...
```

Every verdict the policy can produce:

| verdict | means |
|---|---|
| `accepted: already in accessory mode` | VID 18D1, PID 2D00 or 2D01 |
| `accepted: ADB` / `RNDIS` / `PTP` / `IAD` / `MTP` | one unambiguous descriptor triple |
| `accepted: AOAP` | vendor class, interface named `Android Accessory Interface` |
| `accepted: AOAP (unnamed vendor interface)` | vendor class, no name, no CDC siblings |
| `rejected: CDC network adapter` | **new.** vendor class, but the device is a NIC |
| `rejected: no Android interface` | nothing matched |
| `rejected: Apple VID` | 05AC |
| `rejected: device class 0xNN is not a phone` | audio, printer, mass storage, hub or video |

The other line R1 turns on, from `HomeFragment`:

```
USB button: Single device found - <name>, auto-connecting
```

## 5. Runs

### R0 — build gate

`build_hur.sh`, then `run_unit_tests.sh`.

**PASS:** build succeeds and **966** tests pass, 0 failures. Round 1 was 953; the 13 new ones are
`UsbDeviceIdentityPolicyTest`. A failure here stops the round.

### R1 — the ethernet adapter is no longer projected to. The run this round exists for.

Default `log-level`. Attach the dock, whichever one §2 established. Press the USB button.

**PASS:** the ethernet adapter's line reads `rejected: CDC network adapter`, and **no**
`Single device found - <that adapter>, auto-connecting` appears anywhere in the capture.

**FAIL** if it still says `accepted: AOAP`, or if the reject prints but the auto-connect line
appears anyway. The second is the worse failure: it means the verdict is not gating the connection
path, which is a different defect from the one this round is checking. Round 1's capture carries
both lines to compare against.

Quote the adapter's line in full including its interface list, because the fix keys on the
siblings and the list is what a later reader has to judge it from.

The flash disk comes in on the same dock and should read `rejected: no Android interface`
alongside it, so the capture shows both rejection rules in one bus.

### R2 — the phone is still accepted in every mode, and now says MTP

`log-level` 0. Repeat round 1's sweep on the second phone over USB-C, direct, no dock: default with
debugging on, charging only, file transfer, PTP, PTP plus ADB.

**PASS:** every mode `accepted:`, none rejected.

**Expected difference from round 1:** charging-only and file-transfer now read `accepted: MTP`
where round 1 recorded `accepted: AOAP`. Same verdict, more accurate reason, and it is the visible
sign the name check is running. Report the exact string for each mode as a table.

Restore `log-level` to 2 afterwards.

### R3 — Android stops offering us for things that are not phones

Default `log-level`. Attach one device at a time, directly, not through the dock, and capture from
before the attach.

| device | expected |
|---|---|
| USB flash disk | no system USB dialog at all |
| Bluetooth audio dongle | no system USB dialog at all |
| second phone, file transfer | dialog appears and names Open Headunit, as before |
| the dock with the ASIX | dialog still appears. **Expected, not a failure.** |

**PASS:** zero `systemui.usb.Usb*Activity` displayed for the first two, at least one for the phone.

The ASIX row is the honest limit of this change: the manifest filter can express neither the
interface name nor the CDC siblings, so the dialog stays and only R1's in-app reject stops us
connecting. Record it rather than treating it as a regression.

The scriptable evidence is the `Displayed .../systemui.usb.*` lines. A screenshot of what the
dialog names is useful corroboration but is not what the verdict rests on.

### R4 — the phone still auto-starts

Default `log-level`. Attach the second phone in file-transfer mode and tap through the dialog.

**PASS:** `UsbAttachedActivity` runs, its `UsbDiagnostics: USB attach` line appears, and the phone
is `accepted:`. This is the regression the narrowing was most likely to cause, so it gets its own
run even though R3 covers the dialog.

### R5 — the empty bus, and the Verbose path

Runs on either device. With nothing attached, launch the app.

**PASS at default level:** `sees 0 USB device(s), 0 usable for Android Auto` followed by the
`nothing is on the bus` advisory, both at INFO.

Then set `log-level` 0, force-stop, relaunch, repeat. **PASS:** same lines, no exception, no
`could not read the USB device list`. Weak on an empty bus by construction, which is why it is
last. Restore `log-level` to 2.

## 6. Do not re-run

- Round 1's dedupe check. `UsbDeviceDiagnostics` is byte-identical in `bfda7808` and `4f0cc471`.
- Anything about the blacklist. It is unchanged on this branch and predates 3.3.0.
- The wireless AA adapter. Still nobody has one, and it stays UNTESTABLE until somebody does.

## 7. Report back

Standing format, `usb-device-diagnostics-round2-results.md`. Beyond it:

- For R1 and R2, the full `UsbDiagnostics:` device line, not a summary. The interface list is the
  evidence.
- For R2, a mode-by-mode table of the exact verdict string.
- If R1 fails, the whole capture, and check whether the ASIX enumerated in its class 00 form or its
  class FF form. It cycles between the two and only one carries the CDC siblings in round 1's dump.
