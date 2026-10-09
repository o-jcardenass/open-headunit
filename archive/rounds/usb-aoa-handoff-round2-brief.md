# usb-aoa-handoff: round 2 brief

Host: **Poco X3 NFC** as the head unit, over wireless adb, its USB-C port free at the start of every
run. Read **§7b of `TESTING-TEMPLATE.md` before planning any step of this round.** Every quirk in it
applies here, and three of them decide whether a verdict means anything.

This file is append-only. Corrections arrive as new commits and a `git pull` fast-forwards, so no
brief you have already read changes under you.

Read `usb-aoa-handoff-round1-results.md` first. Round 1's conclusions still stand, but **the branch
has been rewritten twice since it ran** and three of the things it measured no longer exist in the
form it measured them.

---

## 1. Build and baseline

```bash
git fetch fork
git checkout testing/wireless-plus-automation   # 1f4b85ce5a5936ca6f00ff5f1f52ad82a5a5b6cf
```

| Input | SHA | Role |
|---|---|---|
| `fix/usb-attach-clear-defaults` | `b236103c` | **the candidate.** Two commits on `main` @ `c4dd2ba1` |
| `feat/automation-command-surface` | `41a3866b` | **the instrument, not the subject** |
| `fix/wireless-bring-up-and-5ghz-channel` | `e6ed1ad2` | **a third input, and not a subject either**, but see the deferral note in §2 |
| `testing/wireless-plus-automation` | `1f4b85ce` | the merge of those three, and what you build |

### History was rewritten, twice. Every SHA round 1 names is gone.

Round 1 built `fork/fix/usb-attach-clear-defaults` @ `361cfed3` against its own parent `e3077047`.
Neither resolves any more. The branch was re-ported onto a refactored `main` on 2026-09-03, then
reviewed and rewritten on 2026-09-05. Its two commits now are:

| SHA | What |
|---|---|
| `ee16f522` | USB: connect a phone that is not a Pixel without clearing defaults |
| `b236103c` | USB: claim the accessory device before a fast-reverting dongle bails |

Unit gate on the testing branch: **1414 / 0** (`testGithubDebugUnitTest`). Any other count means the
wrong tree was built; stop and say so rather than running the round.

**This round has no baseline APK.** Round 1's A/B was against the branch's own parent, and that
parent no longer exists. The one run that needs a control gets it from a settings change on the
candidate instead (R8), which §8 of the template prefers anyway.

## 2. What this is, and what changed under round 1

Two commits. One of them was on **both** arms of round 1's A/B and has therefore never been tested at
all; the other is a rewrite of what round 1 did test.

**`ee16f522`, a phone that is not a Pixel could not connect over USB.** The attach activity gated on
`vendorId == 0x18D1`, and the fallback that catches units where the attach never reaches an activity
gated on it too. Everything else fell through to the manual USB button. The reason it looked
defensible is that `Settings.isConnectingDevice()` returns false when the `allow-devices` pref set is
**absent**, so a fresh install with no allow list read as "nothing is permitted" rather than "never
configured". `UsbAttachPolicy` states the rule once, for both call sites:

```
isGoogleVendor      -> true
autoStartOnUsb      -> true
!allowListConfigured -> true      // empty list means never configured
else                -> deviceAllowed
```

Round 1's brief called this "the already-landed allow-list fix on both sides" and explicitly put it
under "do not re-run". It has still never been measured. **It is the point of this round.**

**`b236103c`, the accessory-mode race, rewritten.** Round 1 measured the original of this and its
handoff behaviour passed 3/3 (one switch, zero `didn't handle`, dongle patience 408-411 ms). The
review then removed most of the protection built around it, so what passed is not what you are
testing:

- **`UsbSwitchClaim` is no longer ORed into `isSwitchingToProjection()`.** It is read only by the
  attach fallback, through `isActivitySwitchInFlight()`. The wider read also silenced the
  accessory-attach connect, the 1500 ms detach re-sync and the 3 s reconnect scan inside its TTL,
  which are that path's recovery rather than its competition. **R3 is the regression guard for the
  narrowed read** and it is the one run most likely to catch something.
- **The activity's 3 s accessory-device poll is gone**, along with its background
  `startForegroundService`. There is no USB exemption in Android 12's background-FGS list, and the
  service's own accessory-attach path does that job event-driven now. So round 1's §5 lines
  `Accessory mode reached after <N>ms` and `Device never re-enumerated in accessory mode within
  3000ms` **no longer exist in the source**. Do not grep for them; their absence is not a finding.
- **`USB_TIMEOUT_IN_MS` is 1000**, not the 500 round 1 tested and not `main`'s 100. 100 ms was under
  USB 2.0's own 500 ms allowance for a first data packet, so a slow phone read as "not an AOA
  device"; 1000 matches `usbhelper.c` and other AOAP implementations.
- **Endpoint selection is one shared `UsbEndpointSelectionPolicy`**, asked by both
  `StandardUsbProjectionConnection` and `LibusbProjectionConnection`. Round 1's build had fixed only
  the first, so the libusb route's endpoint choice is new since R2 passed.
- The permission wait itself is unchanged in shape: poll `hasPermission` every 200 ms for up to
  1000 ms before falling back to a dialog, because the auto-grant for a re-enumerated `2D00` rides in
  on the manifest-matched activity launch and our runtime receiver sees the attach ~40 ms earlier.
- New: an attach that is refused now **hands off to the service** (`ACTION_CHECK_USB`) instead of
  calling `finish()` and ending there. Once this activity is the default USB handler, a bare
  `finish()` was the end of the road for that plug.
- New: the manifest gives `UsbAttachedActivity` `noHistory`, `excludeFromRecents` and an empty
  `taskAffinity`, so it runs in its own task and is destroyed as it leaves the foreground. No intent
  filter and no `usb_device_filter.xml` entry changed.

**The wireless deferral is no longer this branch's.** `WirelessBringUpDeferralPolicy` and its two
lines moved onto `fix/wireless-bring-up-and-5ghz-channel` on 2026-09-05. They are still in this APK,
because that branch is in the merge, but they are not what this round is measuring, and
`wireless-bring-up-and-5ghz` round 2 already passed them. Round 1's **R4 and R5 are retired here**;
see §7.

## 3. What is different about this round

- **The clean-run protocol in §4 does not apply.** It is written for a wireless round. A USB round is:
  force-stop, `logcat -c`, start **both** captures, then plug. Say in Setup notes that you used this
  instead.
- **Two capture readers are needed** (§7b). The Poco's ROM spam rolls the buffer, so the OHU reader
  must be tag-filtered and the descriptor needs its own:
  ```bash
  stdbuf -oL adb logcat -v time OPENHU:V '*:S'      > rN-ohu.txt  &
  stdbuf -oL adb logcat -v time -s UsbHostManager:D > rN-usb.txt  &
  ```
- **Wireless adb is mandatory**, because the port is occupied by whatever is under test.
  `adb tcpip 5555` while on cable, then `adb connect <poco-ip>:5555`. It survives the cable being pulled.
- **`dumpsys usb` before the round, and put it in Setup notes.** A stale grant or default handler
  makes every dialog verdict meaningless.
  ```bash
  adb shell dumpsys usb | grep -i -A4 headunitrevived
  ```
- **Never tick "always"** on any dialog. It is sticky per identity and changes the rig for every round
  after this one.
- **R7 and R8 need a phone, not the dongle**, and that phone must have **USB debugging OFF**. §7b:
  debugging adds an `FF/42/01` interface, which stops the phone being an MTP device by AOSP's own
  test and sends the attach down a different path. A previous round read that difference as a
  regression. Use the **Motorola edge 30 neo** as the attached device, with debugging off, and drive
  the Poco over wireless adb only.
- **Pre-registered INCONCLUSIVE, so none of these is read as a failure.** R9 if the dongle never
  reaches accessory mode. R2 and R3 if the standard-route USB link is still degraded. That is
  exactly what made round 1's R1 inconclusive, and that round's own queued item still stands:
  it needs a better cable, a fresh OTG adapter or a second dongle. **Do not spend the round fighting
  the dongle.** Two attempts per run, then say so and move on. R7, R8, R10 and R11 do not need the
  dongle at all and should be run first for that reason.
- **This round does not require a projecting session anywhere.** Every verdict below is a decision
  line. If a session forms, report it; if none does, no run here fails for it.

## 4. Settings keys

Written with the app stopped, per §1. Use `set_prefs_runas.sh`; the Poco is **not rooted**.

| Key | Element | Note |
|---|---|---|
| `log-level` | `<int name="log-level" value="0" />` | Verbose. See the note below |
| `use-libusb` | `<boolean name="use-libusb" value="false" />` | R7, R8, R10, R11, R3. **R2 uses `true`** |
| `auto-start-on-usb` | `<boolean name="auto-start-on-usb" value="false" />` | R7 and R8, where it must be false or it short-circuits the decision under test |
| `allow-devices` | **absent** for R7 | R8 writes one bogus entry; see below |

`allow-devices` is a **StringSet**, which §1's element table does not cover, so it needs its own
element-scoped pattern:

```bash
# R7: remove it entirely, so the list reads "never configured"
adb shell run-as $PKG sh -c 'sed -i -E "s#<set name=\"allow-devices\">.*</set>##g" shared_prefs/settings.xml'

# R8: one entry that is not the attached device
adb shell run-as $PKG sh -c '
  f=shared_prefs/settings.xml
  sed -i -E "s#<set name=\"allow-devices\">.*</set>##g" $f
  sed -i "s|</map>|<set name=\"allow-devices\"><string>NOT-THE-ATTACHED-DEVICE</string></set></map>|" $f
'
adb shell run-as $PKG cat shared_prefs/settings.xml   # verify, every time
```

Never leave a `settings.xml.bak` beside the file: SharedPreferences reads a stray `.bak` as an
aborted write and restores it over the edit.

**Why `log-level=0` and not 2.** Every line this round's verdicts turn on is INFO or WARN and the
branch adds no DEBUG lines at all, so a level-2 capture would decide every run. Verbose is for one
piece of context: `UsbDeviceDiagnostics.logDeviceList` prints its bus table at INFO **only when zero
devices are usable**, and at VERBOSE otherwise. On a successful attach the table is the thing you
most want and level 2 is where it disappears.

## 5. The lines that decide the runs

All verified with `git grep -F` against `b236103c`; each appears in exactly one file under
`app/src/main/java`.

**Candidate-only, commit 1 (`ee16f522`):**

```
Not switching <device> here (not on the allow list); letting the service decide      INFO
Fallback: force=true and found single normal-mode Android device <device>. Switching to accessory mode.   INFO
Fallback: force=true but <N> candidate USB devices are attached; not guessing which is the phone          INFO
Usb re-attached in normal mode; asking the service to check it                       INFO
Could not hand the USB attach to the service: <message>                              WARN
```

**Candidate-only, commit 2 (`b236103c`):**

```
Accessory-mode permission arrived after <N>ms: <device>       INFO   the wait worked
A USB accessory switch is already in flight; leaving <device> to it                  INFO
AOA switch threw for <device>                                 ERROR
Unable to find an endpoint pair on the accessory interface    ERROR
```

**On this build and on `main` alike:**

```
Switching USB device to accessory mode <device>
Sending acc start
Acc start sent (len=<N>). Waiting for re-enumeration...
Success controlTransfer len: <N>  acc_ver: <N>
Error controlTransfer len: <N>
Found device already in accessory mode: <device>
Requesting USB permission for <device>
Accessory-mode device has no permission (re-enumerated); requesting permission: <device>
UsbAttachedActivity didn't handle <device>. Trying from service...
Established connection:
```

**Lines round 1 used that no longer exist**, because the review removed the activity's 3 s poll:
`Accessory mode reached after <N>ms` and `Device never re-enumerated in accessory mode within
3000ms`. Do not grep for them and do not read their absence as a finding.

One level change to know about: `Usb in accessory mode` moved from ERROR to INFO. Same string. A
capture filtered at `E` will no longer show it.

**Grep the message text, not the class prefix.** `AppLog` derives the `<Class>.<method> | ` prefix
from the live stack frame, so at the lambda and coroutine sites it renders as a synthetic name.

Counting commands:

```bash
grep -ac "Sending acc start"                       rN-ohu.txt   # AOA switches attempted
grep -ac "didn't handle"                           rN-ohu.txt   # the fallback firing
grep -ac "Requesting USB permission for"           rN-ohu.txt   # our own prompts
grep -ac "Switching USB device to accessory mode"  rN-ohu.txt
grep -ac "letting the service decide"              rN-ohu.txt
grep -aoE "systemui\.usb\.[A-Za-z]+"               rN-usb.txt | sort | uniq -c
```

`-a` is not optional. These captures come back long enough that `grep` auto-detects one as binary
and then prints *nothing at all* for a count, which reads exactly like a zero (§7a).

**Name the dialog by its activity, never by its wording** (§7b): `UsbConfirmActivity` /
`UsbResolverActivity` is the system chooser, `UsbPermissionActivity` is ours. A report that says
"no dialog" without naming which has measured nothing. Two prompts per connect is structural: one
pre-switch, one post-switch at the re-enumerated bus path. A third is a finding. Report the
identities, not the count.

## 6. Runs

Run them in this order. The four that need no dongle come first, so a degraded USB link cannot cost
the round everything.

### R0: build gate

The build stamps its own commit:

```bash
./gradlew :app:assembleGithubDebug        # prints "Building from commit: 1f4b85ce5a59"
```

Identity check, three classes this branch introduces that `main` does not have:

```bash
unzip -p <apk> 'classes*.dex' | strings | grep -c -e UsbSwitchClaim \
                                               -e UsbEndpointSelectionPolicy \
                                               -e UsbAccessoryHandoffPolicy
# non-zero here, 0 on any build of main
```

Unit gate **1414 / 0**, APK md5 recorded, `adb install -r` only. **If R0 fails, stop and report.**

---

### R7 (new): a phone that is not a Pixel gets an AOA switch. **The point of the round.**

Setup:

1. `allow-devices` removed entirely, `auto-start-on-usb=false`, `use-libusb=false`, app force-stopped.
   Read `settings.xml` back and confirm `allow-devices` is genuinely absent, not empty-and-present.
2. `dumpsys usb` captured.
3. Both captures started.
4. Plug in the **Motorola edge 30 neo, USB debugging OFF**, in its normal (MTP or charging) mode.
   Leave it alone for 60 s.

**PASS:**

1. `Switching USB device to accessory mode <device>` appears, naming the Motorola.
2. `Sending acc start` follows it, and the descriptor reader shows the phone re-enumerating at
   `18d1:2d0x`.
3. No `Not switching ... letting the service decide` for that device.

**FAIL:** the attach produces neither a switch nor a hand-off line: the plug is swallowed.

**What a PASS here would look like if the change did nothing.** `main`'s refusal string
(`Skipping device <device> (not allowed and USB auto-start disabled)`) does not exist on this branch
at all, so its **absence proves nothing** and cannot be used as the evidence. The positive line is
the evidence, and R8 is the control that shows the decision is really being made rather than
bypassed. Report both runs together or neither means much.

Also report, because they are what makes the run interpretable:

- the phone's `uniqueName` as the log prints it, because R8 needs to *not* use this string;
- the full `Added device UsbDevice[...]` descriptor block from `rN-usb.txt`;
- the dialog identities.

**INCONCLUSIVE** if the Poco does not enumerate the Motorola at all (nothing in `rN-usb.txt`). That is
a cable or OTG-adapter fault, not a verdict. Two attempts.

---

### R8 (new): a configured allow list that excludes the device still refuses, and hands off. **The control.**

Same as R7 but with `allow-devices` holding one entry that is **not** the attached phone (use the
literal `NOT-THE-ATTACHED-DEVICE`, or the string R7 reported for a different device). This makes
`allowListConfigured=true` and `deviceAllowed=false`, which is the one branch of `UsbAttachPolicy`
that still refuses.

**PASS**, both:

1. `Not switching <device> here (not on the allow list); letting the service decide` appears.
2. **Then** `Fallback: force=true and found single normal-mode Android device <device>. Switching to
   accessory mode.` appears, from the service. The hand-off runs `ACTION_CHECK_USB`, which the
   service turns into `checkAlreadyConnected(force = true)`. That is the second half of the fix and
   the reason a refusal is no longer the end of the road.

**FAIL:** the first line appears and the second never does, or the activity switches anyway (the
allow list is being ignored).

This is the run that carries the round. It reaches the refusing branch and the accepting branch of
the same policy from one settings change on one build, so a green in R7 and a green here together
mean the decision is live. Report the ms between the two lines.

---

### R10 (new): the AOA control transfer no longer gives up early.

Read out of R7's capture if that run switched; otherwise a plug of its own, `use-libusb=false`.

**PASS:** `Success controlTransfer len: 2  acc_ver: <N>` appears, and `Error controlTransfer len: -1`
does not.

**What a PASS would look like if the change did nothing.** If the Motorola answers the first control
transfer inside 100 ms, `main` would pass this too and the run proves nothing. So **report the
timing, not the verdict**: the ms from `Switching USB device to accessory mode` to the first
`Success controlTransfer`. Under ~100 ms makes this a regression guard; anything between 100 ms and
1000 ms is a device the old constant would have refused, and is the demonstration.

`use-libusb=false` is load-bearing: with libusb on, the switch goes through `UsbNative()` and never
reaches the Kotlin control transfers at all.

---

### R11 (new): the attach trampoline does not linger.

No dongle needed. Plug the Motorola in, wait for the activity to do whatever it does, unplug, wait
10 s, plug it in again.

**PASS**, both:

1. `adb shell dumpsys activity recents | grep -i UsbAttachedActivity` is empty after the first
   attach, so `excludeFromRecents` and `noHistory` are in effect.
2. The second attach does **not** produce
   `Usb re-attached in normal mode; asking the service to check it`. That line is `onNewIntent`'s,
   and `onNewIntent` can only be reached by an instance that survived the first attach.

**FAIL:** the activity is in Recents, or that line appears, which would mean a stale instance
survived and the process-wide switch claim is guarding the wrong thing.

Judge it on that line's absence, not on `USB Intent:`. Both `UsbAttachedActivity.onCreate` and
`UsbReceiver` log `USB Intent:`, so its presence does not say which one ran.

Cheap and unattended apart from the plugging. Do not skip it: the manifest change is why
`UsbSwitchClaim` has to be process-wide rather than an activity field.

---

### R3 (re-run): replug, five times. **Regression guard for the narrowed switch claim.**

Candidate, `use-libusb=false`, the **dongle** in the port. Unplug, wait 10 s, replug. Five cycles.

**PASS:** `Sending acc start` count is exactly 5, `didn't handle` count is 0.

`didn't handle` appearing at all means the claim did not cover the window and is a **FAIL** even if a
session still forms. Round 1 got 0 across ten replug windows with the claim ORed into
`isSwitchingToProjection()`; it is now read only by the attach fallback, so this is the run that says
whether the narrower read still covers the same window.

Report the five `Sending acc start` → `Established connection` times as five numbers, and the
`didn't handle` count. Round 1's reference is dongle patience 408-411 ms.

**INCONCLUSIVE** if fewer than three of the five cycles enumerate at all. Round 1 recorded the
standard-route link as progressively degraded on this rig; `didn't handle` events that belong to the
post-failure ~20 s service-retry loop are **not** fresh-plug races and must not be counted as the
claim failing; separate them by timestamp against the plug, as round 1 did.

---

### R2 (re-run): cold plug, libusb route.

Candidate, `use-libusb=true`, dongle. One plug, 90 s.

**PASS:** as round 1, one `Sending acc start`, zero `didn't handle`, and the route behaves as it did
then.

It is re-run for one reason: `UsbEndpointSelectionPolicy` now decides
`LibusbProjectionConnection`'s endpoints as well as the standard route's, and round 1's build did not
do that. Report whether `Unable to find an endpoint pair on the accessory interface` appears (it
should not) and whether a session forms as cleanly as round 1's did.

**INCONCLUSIVE** on the same terms as R3.

## 7. Do not re-run

- **Round 1's R4 and R5.** Their subject, `WirelessBringUpDeferralPolicy`, moved off this branch onto
  `fix/wireless-bring-up-and-5ghz-channel` on 2026-09-05, and `wireless-bring-up-and-5ghz` round 2
  already passed it there. Both lines are still in this APK; they belong to the other thread's
  verdict, not this one's. If you see them, note them and carry on.
- **Round 1's R6**, the USB-C Bluetooth audio adapter. It passed on both builds, and nothing in
  either rewritten commit touches `UsbDeviceIdentityPolicy`.
- **Round 1's R1.** Its standard-route A/B needs a baseline APK that no longer exists and a USB link
  this rig has not had since. R3 and R2 carry what is still answerable from it.
- **`UsbEndpointSelectionPolicy`'s ordering rules.** A real `2D00` exposes one interface with one bulk
  pair, so its outcome on hardware is identical to `main`'s. `UsbEndpointSelectionPolicyTest` covers
  it and R2 covers the only part that could differ.
- **The 10 s `UsbSwitchClaim` TTL expiry.** Reachable only by force-killing the app mid-switch, which
  is not worth a rig round.

## 8. Report back

`usb-aoa-handoff-round2-results.md`, in `TESTING-TEMPLATE.md` §7's format. The numbers that decide whether this ships:

1. **R7 and R8 together.** Did the non-Pixel phone get a switch with no allow list, and did it get
   the refusal-plus-hand-off with one? The phone's `uniqueName`, the descriptor block, the dialog
   identities, and the ms between R8's two lines. **If only one of the two ran, say which; neither
   is worth much alone.**
2. **R10's timing**, from `Switching USB device to accessory mode` to the first
   `Success controlTransfer`, in ms, and whether any `Error controlTransfer len: -1` appeared.
3. **R3's `didn't handle` count**, and the five per-cycle times. This is the regression the review
   could have introduced.
4. **R11's two checks**, one line each.
5. **R2**, if the link allowed it: one line on whether the libusb route still forms a session and
   whether the endpoint line appeared.

**The one thing to watch for that is nobody's criterion:** whether any *third* USB permission dialog
identity shows up in `rN-usb.txt` on any run. Two per connect is structural: one pre-switch, one at
the re-enumerated bus path. A third means either an extra re-enumeration cycle or two of the app's
four `requestPermission` call sites racing, and it would be the first sighting of either. Report the
identities rather than the count, and its absence is not a FAIL.

## Evidence to capture

Into `evidence/usb-aoa-handoff-round2/`:

- `rN-ohu.txt.gz` and `rN-usb.txt.gz` per run, both readers, always both
- the decisive excerpt per run as `r<N>_console.txt`
- `dumpsys-usb-before.txt` and `dumpsys-usb-after.txt`
- the full `Added device UsbDevice[...]` descriptor block for every device plugged in, in
  `descriptors.txt`, with every form seen if one alternates
- `settings-backup-poco.xml`, taken before anything is written
