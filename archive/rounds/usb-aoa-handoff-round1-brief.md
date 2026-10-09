# usb-aoa-handoff — round 1 brief

Host: **Poco X3 NFC** as the head unit, dongle in its USB-C port over OTG, wireless adb.
Read **§7b of `TESTING-TEMPLATE.md` before planning any step of this round.** Every quirk in it
applies here, and two of them decide whether a verdict means anything.

---

## 1. Build and baseline

| | Branch | SHA |
|---|---|---|
| **Candidate** | `fork/fix/usb-attach-clear-defaults` | `361cfed3` |
| **Baseline** | same branch, its parent | `e3077047` |

```bash
git fetch fork
git log --oneline e3077047..361cfed3      # expect exactly two commits
```

The baseline is the branch's own parent, not `main`, so the A/B isolates exactly the two commits
under test and carries the already-landed allow-list fix on both sides. No history was rewritten.

**Identity check** — the candidate carries three classes the baseline does not:

```bash
# on each APK
unzip -p <apk> classes*.dex | strings | grep -c -e UsbAccessoryHandoffPolicy \
                                              -e UsbSwitchClaim \
                                              -e WirelessBringUpDeferralPolicy
# candidate: non-zero    baseline: 0
```

Unit gate on the candidate: **1010 / 0**. Baseline is 997.

---

## 2. What this is and why it exists

Two commits, from a coding session that drove this dongle directly.

**`8514e06f` — the accessory-mode race.** The dongle holds AOA accessory mode only until something
claims its interface: measured **390 ms** on one attempt and ~1.3 s on the next. A real phone waits
indefinitely. Our path from `ACC_REQ_START` to `claimInterface` was too long to win that reliably.

The actual failure was permission, not speed. `usb_device_filter.xml` lists `18D1:2D00`, so the
system auto-grants — but the grant rides in on the manifest-matched `UsbAttachedActivity` launch,
and `UsbLauncherManager`'s runtime receiver gets the attach broadcast **first**, measured 40 ms
after re-enumeration. It read `hasPermission == false`, raised a dialog, and the dongle reverted
350 ms later. Three changes:

- poll `hasPermission` for 1 s before falling back to the dialog;
- `UsbSwitchClaim`, a process-static TTL claim the activity stakes around its own switch, so the
  2 s attach fallback stops starting a **second concurrent** AOA switch on the same device;
- the activity polls for the re-enumerated `2D00` and hands off the moment it appears.

Plus `USB_TIMEOUT_IN_MS` 100 → 500, and `initEndpoint` preferring bulk endpoints.

**`361cfed3` — wireless deferral.** `AapService.onCreate` armed WiFi Direct, the 5288 server and
(mode 3) a Bluetooth poke *before* it looked at the USB bus, so a plugged-in dongle got the whole
wireless stack raised around it and torn back down ~20 s later. The USB probe now runs first, and
wireless holds up to 8 s behind an accessory-mode device or a live switch claim.

The trigger is deliberately **not** "a USB device is attached". `UsbDeviceIdentityPolicy` accepts an
unnamed `FF/FF/00` bulk-pair interface rather than guessing, so a permanently attached peripheral
could otherwise delay every start. R6 is that regression guard and is not optional.

---

## 3. What is different about this round

- **The clean-run protocol in §4 does not apply.** It is written for a wireless round (airplane
  mode, P2P group, 90 s). A USB round is: force-stop, `logcat -c`, start **both** captures, then
  plug. Say in Setup notes that you used this instead.
- **Two capture readers are needed** (§7b). The Poco's ROM spam rolls the buffer, so the OHU reader
  must be tag-filtered, and the descriptor needs its own:
  ```bash
  stdbuf -oL adb logcat -v time OPENHU:V '*:S'      > rN-ohu.txt  &
  stdbuf -oL adb logcat -v time -s UsbHostManager:D > rN-usb.txt  &
  ```
- **Wireless adb is mandatory** — the dongle occupies the only port. `adb tcpip 5555` while on
  cable, then `adb connect <poco-ip>:5555`. It survives the cable being pulled; the dongle-to-phone
  link is its own radio, so the Poco's WiFi stays free.
- **`dumpsys usb` before the round, and put it in Setup notes.** A stale grant or default handler
  makes every dialog verdict meaningless.
  ```bash
  adb shell dumpsys usb | grep -i -A4 headunitrevived
  ```
- **Never tick "always"** on any dialog. It is sticky per identity and changes the rig for every
  round after this one (§7b).
- **The dongle only answers AAP when a phone is associated to it over Bluetooth.** Keep the Motorola
  edge 30 neo paired to the dongle and awake. With no phone, the dongle answers the AOA control
  requests and then powers its USB side down after a few minutes.
- **On this rig the Motorola is paired to the Poco as well**, which fires `AutoStartReceiver`
  ("MATCH! Starting AapService via Bluetooth Auto-start"). That is a rig artifact, not a defect, and
  it means §4's discard rule on that string **does not apply to R4/R5**. Note each occurrence
  instead of discarding.
- **R2 may be INCONCLUSIVE and that is fine.** The libusb route has one run behind it on each side.

---

## 4. Settings keys

Written with the app stopped, per §1. Use `set_hu_prefs.sh` for any run setting more than one.

| Key | Element |
|---|---|
| `log-level` | `<int name="log-level" value="0" />` |
| `use-libusb` | `<boolean name="use-libusb" value="false" />` |
| `auto-start-on-usb` | `<boolean name="auto-start-on-usb" value="true" />` |
| `wifi-connection-mode` | `<int name="wifi-connection-mode" value="3" />` |

`log-level=0` is for context only — **every line this round's verdicts turn on is INFO or WARN**, so
a level-2 capture would still decide them.

**Read `wifi-connection-mode` back before R4 and R5 rather than assuming it.** Mode 3 is chosen for
those two because it is the loudest: it prints `createGroup SUCCESS`, the 5288 bind and the poke.

---

## 5. The lines that decide the runs

All verified with `grep -F` against `361cfed3`.

**Candidate-only, commit 1:**

```
Accessory-mode permission arrived after <N>ms: <device>            INFO   the retry worked
Accessory-mode device has no permission (re-enumerated); requesting permission:   INFO   it gave up
Accessory mode reached after <N>ms: <device>                       INFO   the activity's poll hit
Device never re-enumerated in accessory mode within 3000ms         WARN   the poll timed out
```

**Candidate-only, commit 2:**

```
AapService: a USB projection attempt is in flight — holding the wireless bring-up for up to 8000ms
AapService: USB handoff settled after <N>ms — arming wireless now
```

**On both builds:**

```
UsbAttachedActivity.onCreate | Switching USB device to accessory mode <device>
UsbAccessoryMode.switch | Sending acc start
UsbAccessoryMode.switch | Acc start sent (len=<N>). Waiting for re-enumeration...
UsbLauncherManager.checkAlreadyConnected | Found device already in accessory mode: <device>
UsbLauncherManager.requestPermission | Requesting USB permission for <device>
UsbLauncherListener | UsbAttachedActivity didn't handle <device>. Trying from service...
StandardUsbProjectionConnection.usbOpen | Established connection:
StandardUsbProjectionConnection.initEndpoint | Unable to find bulk endpoints
AapService.quiesceWirelessForWiredSession | USB session established while wireless mode
AapService.rearmWirelessAfterWiredSession | wired session ended — re-arming wireless mode
WifiDirectManager | ... createGroup SUCCESS
```

Counting commands:

```bash
grep -c "Sending acc start"                    rN-ohu.txt   # AOA switches attempted
grep -c "didn't handle"                        rN-ohu.txt   # the fallback firing = a second switch
grep -cE "Requesting USB permission for"       rN-ohu.txt   # our own prompts
grep -oE "systemui\.usb\.[A-Za-z]+"            rN-usb.txt | sort | uniq -c   # which dialog, §7b
```

**Name the dialog by its activity, never by its wording** (§7b): `UsbConfirmActivity` /
`UsbResolverActivity` is the system chooser, `UsbPermissionActivity` is ours. A report that says
"no dialog" without naming which has measured nothing.

---

## 6. Runs

### R0 — build gate

Both APKs built, md5s recorded and different, identity check above passes, unit gate 1010/0 on the
candidate. `adb install -r` only.

### R1 — cold plug, standard route — **this is the point of the round**

Candidate and baseline, `use-libusb=false`. App not running, dongle unplugged, both captures
started, then plug the dongle in once and leave it alone for 90 s.

**PASS (candidate):** exactly **one** `Sending acc start`; **zero** `didn't handle`; `Sending acc
start` → `Established connection` under 1.5 s; session reaches `SSL handshake complete`.

**What a PASS would look like if the change did nothing** — and this is the part that matters:
if the dongle happened to hold accessory mode for over a second on that plug, the baseline passes
too and R1 proves nothing. So **pair the count with the measurement that proves the path was
reachable**, and report all three:

1. Was `Accessory-mode permission arrived after <N>ms` present, and what was N? If it is absent,
   permission was already there and the retry never ran — say so; the verdict then rests on the
   baseline arm.
2. `Acc start sent` → first `UsbHostManager` line showing `18d1:2d00` — the dongle's patience on
   this plug, in ms.
3. The dialog identities from `rN-usb.txt`.

**The baseline arm is the evidence.** If the baseline also connects in one switch with no prompt,
the dongle was patient that run and R1 is **INCONCLUSIVE for both** — repeat up to three times
before recording it. Do not turn an inconclusive into a pass.

### R2 — cold plug, libusb route

As R1 with `use-libusb=true`, candidate only. Same PASS conditions. Also report `Sending acc start`
→ first rendered frame, against R1's, so the two routes finally have a second data point each.

### R3 — replug, five times

Candidate, `use-libusb=false`. Unplug, wait 10 s, replug. Five cycles.

**PASS:** `Sending acc start` count is exactly 5, `didn't handle` count is 0, and five sessions
formed. Report the per-cycle `Sending acc start` → `Established connection` times as five numbers.

`didn't handle` appearing at all means the switch claim did not cover the window and is a **FAIL**
even if the session still forms.

### R4 — dongle on the bus before the service starts

`wifi-connection-mode=3`, read back. Dongle plugged in and settled, app force-stopped, captures
started, then start the service (`am start` the main activity, or the boot receiver — say which).

**PASS:** `a USB projection attempt is in flight — holding the wireless bring-up` appears, and
**no** `createGroup SUCCESS`, no 5288 bind and no `Attempting active poke to device` before either
`USB session established while wireless mode` or `USB handoff settled after`. Report the ms between
service start and whichever of those two came first.

**If the dongle is already at `18d1:2d00` when the service starts, that is the strongest form of
this run** — note which identity it was on.

### R5 — no USB device at all — **regression guard, do not skip**

`wifi-connection-mode=3`, nothing plugged in. Start the service.

**PASS:** the deferral line is **absent**, and `createGroup SUCCESS` arrives no later than it does
on the baseline in the same setup. A wireless-only unit must pay nothing for this change. Report
service start → `createGroup SUCCESS` for both builds.

### R6 — the USB-C Bluetooth audio adapter — **regression guard, do not skip**

The adapter attached, no dongle, `wifi-connection-mode=3`. Start the service.

**PASS:** the `UsbDiagnostics` dump reports the adapter **`rejected:`** with `0 usable for Android
Auto`; no `Sending acc start`; the deferral line absent; `createGroup SUCCESS` on time as in R5.

**If the dump says `accepted:`** — record the full descriptor from `rN-usb.txt` and stop. That is a
pre-existing `UsbDeviceIdentityPolicy` fault the app already has today, independent of this branch,
and it needs its own fix before this round's R4/R5 mean anything. Confirm it on the **baseline** too
so it is unambiguous which build owns it.

Per §7b, replug the adapter until its descriptor stops changing and report every form seen — a
peripheral can alternate between two enumerations inside one plug.

---

## 7. Do not re-run

- Anything about the allow-list / non-Pixel attach behaviour. That is `e3077047`, already on both
  sides of this A/B, and it is not what this round is measuring.
- The `#800` reporter's fault. It was re-read on 2026-09-03 and their handoff is not the problem —
  740 ms, one switch, no prompt. See `usb-aoa-dongle-findings.md`.

---

## 8. Report back

`usb-aoa-handoff-round1-results.md`, in §7's format. The numbers that decide whether this ships:

1. **R1, both builds:** switches, `didn't handle` count, dialog identities, the dongle's patience in
   ms, and whether the permission-retry line fired.
2. **R3:** the five `Sending acc start` → `Established connection` times, and the `didn't handle`
   count.
3. **R4:** ms from service start to the first of `USB session established` / `handoff settled`, and
   whether any wireless bring-up line preceded it.
4. **R5 and R6:** service start → `createGroup SUCCESS` on both builds. If either regressed, that
   alone blocks the branch.
