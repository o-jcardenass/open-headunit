# usb-device-diagnostics — round 5 brief

**Candidate:** `feat/usb-device-list-diagnostics` @ `843b78d7` (3 commits on `main` @ `2f07eeec`)
**Baseline:** none, and none is needed. Round 4's own
`evidence/usb-device-diagnostics-round4/r5_bfu_crash.log` is the before-state for the only thing
this round measures.
**Round 4 results:** `usb-device-diagnostics-round4-results.md`. R0-R4 and R6 PASS, R7
INCONCLUSIVE, **R5 FAIL**.

Round 4 validated the blacklist end to end and cleared every regression arm. This round exists for
its one FAIL and its one aside. Nothing about the device predicate, the manifest filter or the
blacklist needs re-proving.

```bash
git fetch fork
git checkout feat/usb-device-list-diagnostics
git reset --hard fork/feat/usb-device-list-diagnostics
git log --oneline -3      # expect 843b78d7, 33b241f4, a3b0d290
```

**History was rewritten twice since round 4's brief, so every SHA that brief names is gone from the
branch.** The seven commits were squashed to two mid-round (`9b85bcb4`, the tree round 4 actually
measured), then the attach-path log fix was folded into commit 1 and the Direct Boot fix added.
Nothing is lost: `usb-diagnostics-pre-regroup` tags the original seven and
`usb-diagnostics-round4-tested` tags `9b85bcb4`. Both are pushed to `fork`.

## 1. What changed since round 4

| commit | what | round 4 saw it |
|---|---|---|
| `a3b0d290` | the diagnostic, the predicate, the blacklist | yes, all PASS |
| `a3b0d290` | **the attach-path verdict line** | **no**, this is new |
| `33b241f4` | **Direct Boot: the app crashed before the user's first unlock** | **no**, this is new |
| `843b78d7` | the narrowed manifest filter | yes, unchanged since round 3 |

### R5's defect, and why it was bigger than R5 scoped it

Round 4 got the root cause right. `App.kt:46` called `component.suExecutor.register()` two lines
above its own `isUserUnlocked()` guard, and the object graph is not buildable before the first
unlock: `AppComponent` builds a `VideoDecoder` whose field initialisers read the memory-profile
override and the fps limit, both of them credential-encrypted. Three crashes in one boot on round
4's host, one from a boot receiver and two from the plug enumerations.

Two things round 4 did not look at, both found by reading the same file:

- `App.kt:84-101` builds the three notification channels through `component.notificationManager`,
  also outside the guard. Moving line 46 on its own would have left the crash exactly where it was.
- Three more callers reach either the graph or a service that would build it, with no lock guard on
  that specific line: `UsbAttachedActivity.kt:136` (guarded by `autoStartOnUsb`, which is precisely
  the setting that gets a device that far at Direct Boot), and `WifiAutoStartReceiver` and
  `AutoStartReceiver`, whose existing `!isLocked` covers their `App.provide` but not the
  `startForegroundService` twenty lines below it.

What the fix does: nothing in `onCreate` touches the graph, the channels take their manager from
the system service, and everything needing credential storage moved into `initUnlockedOnce()`. A
locked start registers for `ACTION_USER_UNLOCKED` and runs it there instead. The four callers each
defer. `UsbAttachedActivity` still refuses a blacklisted device and still performs the AOA switch
while locked, which is the whole reason it is `directBootAware`, but leaves the session for after
the unlock.

**The risk this round is really measuring is the new one, not the old one.** Stopping the crash
leaves a process that outlives the lock screen. If `ACTION_USER_UNLOCKED` never reaches the
runtime-registered receiver, that process runs forever on default log level, default theme and
unwritten auto-start mirrors: a silent degradation, harder to diagnose than the crash was, and
invisible in any log that starts after unlock. R2 is the arm for it, and it is worth more than R1.

### The aside

Round 4 found `UsbLauncherListener.kt:25` printing `matchReason()`, the descriptor verdict, so a
blacklisted phone logged `accepted: ADB` on the in-service attach path while the scan path logged
`rejected: blacklisted by the user` for the same device in the same second. Both now go through one
`UsbDeviceCompat.connectableReason()`. R5 below is one grep.

## 2. What is different about this round

- **It cannot be run unattended.** R1, R2, R3 and R6 need a person at the device to plug a cable at
  a lock screen and unlock afterwards. Template §3a applies to everything else.

- **Do not clear the persistent USB grants, and do not uninstall.** R7 left two serial-keyed grants
  and default handlers for `18D1:4EE1` and `18D1:2D00`. They are not a nuisance this round, they are
  a precondition: a recorded default handler is what bypasses AOSP's MTP short-circuit (§7b) and
  makes `UsbAttachedActivity` launch at all for a file-transfer phone on a phone host. It is why
  round 4's R5 saw `wm_create_activity … UsbAttachedActivity` at the lock screen. Clear them and
  every Direct Boot arm here becomes UNTESTABLE.

- **Therefore use the Poco in file transfer, enumerating `18D1:4EE1`.** That is the identity the
  default handler is recorded against. Another mode may raise nothing at all on this host, which
  would be UNTESTABLE, not a FAIL. Confirm the identity from the descriptor before judging any arm.

- **Set `log-level` to INFO (`2`), not VERBOSE (`0`).** Every decisive line in this round is `I` or
  `W`. Round 4 lost its `-b main` window to Motorola ROM spam while wireless adb was down across
  the reboot, and had to recover from `-b events`, `-b system` and `-b crash`. Those buffers do not
  carry the lines this round needs. Verbose logging makes that loss more likely, not less.

- **Try to enlarge the log buffer, and say whether it took.** `adb shell logcat -G 16M` does not
  survive a reboot; `adb shell setprop persist.logd.size 16M` may be refused on a user build. Try
  both before the first reboot, report which was accepted, and do not treat either as given.

- **After each reboot, re-enable Wireless debugging and dump before anything else:**
  `adb logcat -b all -d > rN_bfu.log`. The port changes every time; round 4 saw `38729`, `40867`,
  `39739`.

- **Verify the screen lock rather than assuming it.** Round 4 reported the host already carries a
  PIN plus fingerprint, which is why its R5 was a FAIL and not UNTESTABLE. Confirm it is still set
  before the first reboot and say so in Setup notes.

## 3. Settings this round needs

Written to `shared_prefs/settings.xml` with the app stopped, per template §1. The blacklist is a
string set, so use `hur-wifi-test-scripts/set_usb_blacklist.sh` from round 4.

| key | element | value | why |
|---|---|---|---|
| `log-level` | `<int>` | `2` | INFO. See §2; the decisive lines are all I or W. |
| `usb-blacklist` | `<set>` | `<string>name:xiaomi poco x3 nfc</string>` | R1 and R5. Removed for R3. |
| `auto-start-on-usb` | `<boolean>` | `true` | R3 only. Off for R1. |
| `auto-start-wifi-ssid` | `<string>` | `OHU-UNLOCK-PROBE` | R2's probe. See below. |
| `auto-start-on-wifi` | `<boolean>` | `false` for R1-R3, `true` for R6 | keeps R6's session start out of the other arms |

**The ordering in §3 of round 4's brief still governs and matters more this round.** The
device-protected mirror is written by the app, not by your file edit, so the app must be launched
once after writing the blacklist and before any arm is judged.

**R2's probe depends on that same mechanism, run backwards.** After the mirroring launch, stop the
app and change `auto-start-wifi-ssid` in `settings.xml` only, to `OHU-UNLOCK-PROBE`. Do not launch
the app again. The mirror now disagrees with credential storage on that one key. If
`initUnlockedOnce()` really runs at unlock, the mirror must come to carry the new value; if only the
log line printed and the body did not run, it will not. Read the mirror with `run-as`, the way round
4 did:

```bash
PKG=com.andrerinas.headunitrevived
adb shell run-as $PKG cat /data/user_de/0/$PKG/shared_prefs/settings_device_protected.xml
```

Capture that file's contents **before** the reboot as well, so the comparison is against a recorded
state and not a remembered one.

## 4. The lines that decide every run

Copied from the branch at `843b78d7` and verified with `grep -F`.

| line | level | where | means |
|---|---|---|---|
| `App started in Direct Boot mode (locked). Settings access deferred.` | W | `App.kt` | **the reachability proof**, see below |
| `User unlocked: credential storage is available, applying settings` | I | `App.kt` | `initUnlockedOnce()` fired at unlock |
| `UsbAttachedActivity: Ignored blacklisted USB device` | I | `UsbAttachedActivity.kt` | the blacklist applied |
| `Switching USB device to accessory mode` | I | `UsbAttachedActivity.kt` | the AOA switch ran |
| `Usb in accessory mode, but the user has not unlocked yet and a session needs credential storage. Waiting for unlock.` | W | `UsbAttachedActivity.kt` | the session deferred correctly |
| `Usb in accessory mode but no permission. Requesting...` | I | `UsbAttachedActivity.kt` | the other acceptable R3 outcome |
| `Could not start UI from USB auto-start:` | W | `UsbAttachedActivity.kt` | expected and harmless at Direct Boot |
| `WifiAutoStartReceiver: device is locked, deferring WiFi auto-start until unlock.` | W | `WifiAutoStartReceiver.kt` | R6 |
| `AutoStartReceiver: device is locked, ignoring the Bluetooth event until the user unlocks.` | W | `AutoStartReceiver.kt` | R6 |
| `rejected: blacklisted by the user` | I | `UsbDeviceCompat.kt` | R5, and round 4's self-check |

**`App started in Direct Boot mode (locked)` is the run's own control, and it is free.** On
`9b85bcb4` the crash happened at `App.kt:46`, two lines *above* the branch that prints it, so that
line could never appear on the old build. Its presence therefore proves two things at once: the
before-first-unlock state was genuinely reached, and `onCreate` survived it. **An arm below that
cannot show that line has measured nothing**, whatever else it shows, and should be reported
UNTESTABLE rather than PASS. That is the difference between this fix working and the host simply
having been unlocked already.

## 5. Runs

### R0 — build gate

Build and unit-test `843b78d7`. Expect `BUILD SUCCESSFUL` and **984 tests, 0 failures**, the same
totals round 4 measured. No test count moves this round: none of the new code is reachable from the
JVM.

### R1 — the app survives a locked cold boot, and the blacklist still applies

The R5 re-run. Blacklist the Poco per §3, launch the app once to mirror it, confirm the entry took
by plugging in and reading the verdict, then stop the app. `auto-start-on-usb` off.

Reboot. Plug the Poco in **at the lock screen, in file transfer**, before unlocking. Hold ~20 s.
Unlock, re-enable Wireless debugging, dump `-b all` immediately.

**PASS** needs all three:

- `App started in Direct Boot mode (locked). Settings access deferred.` present. Without it, report
  UNTESTABLE.
- `UsbAttachedActivity: Ignored blacklisted USB device` present.
- Zero `FATAL EXCEPTION`, zero `am_crash` and zero `AndroidRuntime` for the package across the whole
  boot, in `-b crash` and `-b main` both.

**FAIL** on any crash for the package, or on any `Switching USB device to accessory mode`,
`connectAndSwitch` or `beginAutoConnect` naming the Poco.

Quote the pid that started at Direct Boot. R2 needs it.

### R2 — the unlock handover, and the point of the round

Same boot as R1, no second reboot. After unlocking:

- `User unlocked: credential storage is available, applying settings` appears, **from the same pid**
  R1 recorded, with no `am_proc_start` for the package in between. A new pid means the process died
  and restarted, which is the old behaviour wearing the new line, so say so.
- `settings_device_protected.xml` now carries `auto-start-wifi-ssid` = `OHU-UNLOCK-PROBE`, where the
  pre-reboot copy did not. This is the arm's real content: the log line proves the receiver fired,
  this proves the body ran.

**PASS** needs both. **FAIL** if the line never appears, or appears with the value unchanged.

If the line is absent, that is the single most useful result this round can produce, and the fix is
one line: `BootCompleteReceiver` already declares `android.intent.action.USER_UNLOCKED` in the
manifest, so the same call can hang off a receiver that is known to fire on this hardware. Say so
plainly and stop; do not chase it.

### R3 — a locked boot still switches a phone into accessory mode

Remove the blacklist entry, launch once to clear the mirror, confirm it is empty. Set
`auto-start-on-usb` `true`, launch once more so that mirrors too, then stop the app. Reboot, plug
the Poco in at the lock screen, hold ~30 s so the re-enumeration completes, then unlock and dump.

Expected sequence:

1. `App started in Direct Boot mode (locked)`
2. `Could not start UI from USB auto-start:` — expected, `MainActivity` is not `directBootAware`
3. `Switching USB device to accessory mode`, and a `Result: true`
4. the phone re-enumerates as `18D1:2D0x`, and **either** `Usb in accessory mode, but the user has
   not unlocked yet …` (if that identity has a grant, which `2D00` does) **or** `Usb in accessory
   mode but no permission. Requesting...` (if it comes up as `2D01`, which has none). Either is a
   PASS; report which.

**FAIL** on any crash, or on any `AapService` start before the unlock (`am_proc_start`,
`ServiceRecord{…AapService}`, `onStartCommand`).

This is the arm that proves the fix did not simply switch the locked path off. Step 3 is what
`directBootAware` on that activity is for.

### R4 — the unlocked path is unchanged

`onCreate` was restructured, so this guards every user who never sees a lock screen. No reboot
needed. Ordinary cold start of the app, unlocked:

- log level is honoured (set `log-level` to `0` for this run only and confirm D/V lines appear)
- the theme is applied as before
- a full USB projection session starts, **and its foreground notification appears**. That
  notification is the end-to-end proof the channel change works, and is worth more than reading
  `dumpsys notification`.

**FAIL** on a missing foreground notification, an unstyled UI, or a log level that does not take.

### R5 — the attach path names the right reason

One grep, no reboot. Blacklist the Poco again, launch once to mirror, start `AapService` the way
round 4's R1 did, and plug the Poco in.

Expected: `Ignoring USB device attached in service (VID: …): rejected: blacklisted by the user`.
Round 4 got `accepted: ADB` on that same line for the same device.

**FAIL** if it still prints a descriptor verdict.

### R6 — the other two deferrals, optional, run last

Only if the round has gone smoothly and there is appetite for one more reboot. Set
`auto-start-on-wifi` `true` with a real reachable SSID in `auto-start-wifi-ssid`, and put the paired
phone's MAC in the Bluetooth auto-start list. Launch once to mirror both. Reboot and wait at the
lock screen without plugging anything; let WiFi associate and let the phone reconnect over
Bluetooth.

Expected: `WifiAutoStartReceiver: device is locked, deferring WiFi auto-start until unlock.` and
`AutoStartReceiver: device is locked, ignoring the Bluetooth event until the user unlocks.`, no
crash, and no `AapService` before the unlock. After unlocking, `BootCompleteReceiver` should start
the service for the WiFi case; the Bluetooth event is not replayed by design, so its absence after
unlock is correct and not a finding.

Restore both settings afterwards.

## 6. Do not re-run

- Anything round 4 passed. The blacklist, the name key, the legacy entry, the mode sweep and every
  round 3 regression arm are settled.
- R7's permission-prompt count. It needs the uninstall §2 forbids this round, and it would destroy
  the precondition every Direct Boot arm here depends on.
- Whether the manifest narrowing regresses a file-transfer phone. Round 3 settled it with a
  match-all baseline arm and §7b carries the mechanism.

## 7. Report back

The standing format in `TESTING-TEMPLATE.md` §7. Three things specifically:

- **Whether `App started in Direct Boot mode (locked)` appeared, per arm.** Every Direct Boot verdict
  in this round is conditional on it, and an arm without it is UNTESTABLE rather than PASS.
- **The pid across the unlock**, and the two `settings_device_protected.xml` dumps either side of it.
  R2 is the run that decides whether this fix is finished.
- **Which log buffer the evidence came out of**, and whether the buffer enlargement in §2 was
  accepted. If `-b main` rolled again, say so: it changes what the next round can ask for, not just
  what this one found.
