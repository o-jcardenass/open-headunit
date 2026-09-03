# usb-device-diagnostics — round 1 brief

## 1. Build and baseline

Candidate only, no baseline APK needed.

```bash
git fetch fork
git checkout -B usb-device-diagnostics fork/feat/usb-device-list-diagnostics   # 21478e23
```

One commit on top of `main` @ `4849903d` (the beta3 tag content). History has not been rewritten;
this branch is new and has never been on the rig.

Compiled and tested on the writing machine before this brief:
`:app:compileGithubDebugKotlin` clean with no warnings on the changed files, and
`:app:testGithubDebugUnitTest` 953/953, 0 failures. R0 should reproduce those numbers exactly.

## 2. What this is and why it exists

Three separate reporters have said a device does not appear in OHU's USB list: the phone in the
"Switch failed" report (issue 911), and two more on that same thread, one of them a wireless
Android Auto adapter. A fourth report (issue 908) is an adapter that never showed up on two
different head units.

None of them can be answered, because **every USB enumeration site in the app filters the list
through `UsbDeviceCompat.isAndroidDevice` before anything is logged.** So "no USB device found"
could mean the bus was empty or that we rejected what was on it, and no log we have ever received
can distinguish those. That ambiguity is the whole reason this thread exists. It is not a fix for
any of those reports; it is what makes the next report diagnosable.

`UsbDeviceDiagnostics.logDeviceList` now dumps the raw `UsbManager.deviceList` at all four
enumeration sites, with per-device VID, PID, manufacturer, product, the device class triple,
permission state, every interface's class/subclass/protocol, endpoint shape, and which rule of ours
accepted or rejected it.

The predicate itself is unchanged. `UsbDeviceCompat.matchReason` makes the same decision as
`isAndroidDevice` and only names the rule that fired; `hasAndroidInterface` now delegates to a
private `androidInterfaceMatch` that returns non-null in exactly the cases the old function returned
true. Nothing in the connection path reads any of it. **Any behaviour change on this branch other
than new log lines is a bug, and that is what R1 and R2 are really checking.**

## 3. What is different about this round

**This rig has no USB host mode, and that is not a problem for this round — it is the round.**
The standing template's §7a records `dumpsys usb` reporting device mode only
(`host_connected=false`), with the port being the adb link to the PC. So `UsbManager.deviceList`
is empty here, permanently.

That means:

- **The empty-bus arm is fully testable, and it is the arm that matters.** The reports this change
  exists for are all "nothing appeared". An empty bus is exactly the state the new INFO-level
  advisory was written for, and this rig is in that state by construction.
- **Every arm needing a real USB device is UNTESTABLE here.** Do not attempt a substitute. The
  per-device line format, `matchReason`'s branch names, and the endpoint summary cannot be exercised
  on this rig at all. That coverage is deliberately left to a JVM test in a later round, once the
  predicate is extracted into a pure policy; it is not this round's job and its absence is not a
  finding.

Say so plainly in the results rather than marking anything FAIL for it.

**Confirm the premise rather than assuming it.** The claim "this rig's USB bus is empty" is a rig
state, and rig state drifts. One command, in R1's setup, settles it:

```bash
adb -s <headunit> shell dumpsys usb | grep -i host_connected
```

If that ever reports `host_connected=true`, stop and say so in the results: the whole round was
written on the opposite premise and R1/R2's PASS conditions no longer mean what they say.

## 4. Settings keys this round needs

One, and only for R3.

| key | element | value | why |
|---|---|---|---|
| `log-level` | `<int name="log-level" value="0" />` | 0 (VERBOSE) | R3 only. R1 and R2 must run at the default level to prove the lines survive INFO. |

**Corrected after round 1.** This table first named `exporter-log-level` with value `2`. That key
does not exist. `exporterLogLevel` is the Kotlin property; the pref key is `log-level`
(`Settings.kt:1238`) and it stores a `LogExporter.LogLevel` **ordinal**, where `VERBOSE=0, DEBUG=1,
INFO=2`. So `2` is the INFO default, and a run made with the original table would have proved
nothing about the Verbose path. Same trap as the `RECV:` lines needing `log-level=0`.

Restore it after R3. Everything else stays as the rig already has it, including
`wifi-connection-mode`; this round never opens a session.

## 5. The lines that decide every run

All verified with `grep -F` against `21478e23`.

The header, one per scan (`N` and `M` are integers):

```
UsbDiagnostics: <caller> sees <N> USB device(s), <M> usable for Android Auto
```

The empty-bus advisory, which on this rig is the line that proves the code ran:

```
UsbDiagnostics: nothing is on the bus. Either the port carries no data, the unit is not in USB host mode, or a wireless adapter is waiting for its phone before it presents itself.
```

The four caller strings, verbatim, exactly as they appear inside the header line:

```
USB button
USB list
USB attach
service scan (force=true)
service scan (force=false)
```

The read-failure path, which must **not** appear:

```
UsbDiagnostics: <caller> could not read the USB device list:
```

Grep with `-a` (§7a: long lines make `grep` treat a capture as binary, and `grep -c` then prints
nothing rather than `0`, so an absent pattern and a present one look identical).

## 6. Runs

### R0 — build and unit tests. Not the point of the round, but do it first.

`./gradlew :app:assembleGithubDebug` and `:app:testGithubDebugUnitTest` via the rig's existing
scripts in `hur-wifi-test-scripts/`.

**PASS:** compiles clean; **953/953 tests, 0 failures**. This is the first ever compile of this
branch, and R0 is also the only check that the native half still links, since the writing machine
has no NDK and never runs `assembleGithubDebug`.

**FAIL:** any compile error, or a test count other than 953 (say which tests moved).

### R1 — the point of the round: an empty bus says so, at INFO.

Setup: fresh install of the candidate, clean-run protocol, **default log level** (do not set
Verbose). Capture started before launch. Confirm `host_connected` per §3.

Open the app, then drive the two reachable callers:

```bash
adb -s <headunit> shell am start -a android.intent.action.VIEW -d "headunit://connect"
```

(§3 of the template: with no `ip` param this maps to `ACTION_CHECK_USB`, which is the `service scan`
caller.) Then press the USB button on the home screen.

**PASS**, all four:

1. At least one `UsbDiagnostics: ... sees 0 USB device(s), 0 usable for Android Auto` line.
2. The `nothing is on the bus` advisory appears.
3. Both appear **at INFO**, with the log level left at default. This is the whole design claim: the
   dump reaches us without asking a reporter to raise their log level first.
4. `dumpsys usb` confirmed `host_connected=false`, so the zero is a real empty bus.

**What a PASS would look like if the change did nothing:** no `UsbDiagnostics` lines at all. The
condition is the presence of the lines, so the two states are distinguishable. Report the raw count
`grep -ac "UsbDiagnostics:" <capture>` alongside the verdict, not just "seen".

**FAIL:** zero `UsbDiagnostics` lines; or they appear only at Verbose; or the `could not read` line
appears; or a device count other than 0 while `host_connected=false`.

### R2 — the dedupe does not swallow a caller.

Same capture as R1 is fine; no reinstall.

Press the USB button **three times**, ~5 s apart, with the bus unchanged throughout.

**PASS:** the `USB button` header appears **exactly once**, not three times. The dedupe is keyed per
caller and per bus state, so an unchanged bus must not repeat.

Then fire `headunit://connect` again.

**PASS:** the `service scan` header still appeared for its own first call, independently of the USB
button's. Report both counts:

```bash
grep -ac "UsbDiagnostics: USB button sees" <capture>
grep -ac "UsbDiagnostics: service scan" <capture>
```

Expected: `1` and `1`.

**FAIL:** the USB button header repeats on an unchanged bus (dedupe broken), or it never appears at
all after the service scan already logged (dedupe too aggressive, swallowing a different caller).
The second is the more important failure and is why this run exists.

### R3 — the Verbose path does not crash on an empty bus.

Set `log-level` to 0 with the app stopped, restart, repeat R1's two triggers. Read the value
back before starting: at INFO this run measures nothing.

**PASS:** same lines present, no exception, no `could not read the USB device list`. Note this is a
weak run on this rig: with zero devices the INFO and Verbose paths emit nearly the same output, so it
proves absence of a crash and nothing more. Marked as such on purpose so it is not read as coverage
of the per-device format.

Restore `log-level` to 2 afterwards.

### R4 — regression guard: nothing else changed.

The app has no USB transport on this rig, so the guard is that the rest of the app is untouched.

Start a normal Native AA wireless session the way any other round does, let it run two minutes, and
exit.

**PASS:** session establishes and behaves as it does on `main`, and the capture contains **no**
`UsbDiagnostics` line beyond the ones the triggers above produced. Nothing in a wireless session
should be scanning USB.

**FAIL:** any change in session behaviour, or `UsbDiagnostics` lines appearing on a cadence during
the session, which would mean a scan is running somewhere it should not.

## 7. Do not re-run

- Anything from `usb-session-teardown`. Different thread, different fault, already answered and
  attributed to Android Auto rather than to us.
- Any attempt to attach a phone, an adapter or a hub to the head unit's port. §7a settles that this
  rig cannot host, and proving it again costs rig time for no information.

## 8. Report back

Four things decide whether this ships:

1. R0's test count.
2. R1's `grep -ac "UsbDiagnostics:"` count and whether the lines were at INFO with the default level.
3. R2's two counts, `1` and `1` expected.
4. R4's verdict on the wireless session.

Plus, if it is cheap: paste the full text of one header line and the advisory exactly as they
appeared. The wording is aimed at a reporter reading their own log, and this is the first chance to
see whether it reads well in context rather than in a source file.

---

## 9. Runs on real USB hardware, not on the rig

**Not for the rig agent.** These are for the maintainer's own phones, which can host USB where the
rig cannot. They are the only way to see the per-device line at all, and U2 in particular decides a
design question that is currently blocked.

Setup for all three: install the candidate APK on a phone that can act as a USB host (USB-C, OTG),
open OHU, start a capture, then plug the device in. The dump fires on attach, and again on the USB
button, so either trigger works.

```bash
adb -s <host phone> logcat -c
stdbuf -oL adb -s <host phone> logcat -v time > u.log &
# plug in, wait 10s, press the USB button in OHU
grep -a "UsbDiagnostics:" u.log
```

### U1 — a USB device that is not a phone (the USB-C Bluetooth dongle)

The point: prove the reject path prints something a reporter could act on, and see what Android
itself does before our code runs.

**Expect** one device line ending `rejected: no Android interface`. A Bluetooth dongle is class
`E0/01/01`, and our RNDIS rule is `E0/01/03`, so the protocol byte differs and it should not match.
If it comes back **accepted**, that is a real finding and worth more than a pass: it would mean the
positive heuristic is looser than believed.

**Also record, separately from the log:** did Android show a "Open Open Headunit to handle this
device?" dialog when you plugged it in? That is the live demonstration of the manifest filter being
`<usb-device />` match-all, and it is the whole case for narrowing it. A screenshot is enough.

### U2 — a second Android phone over USB-C. This is the one that unblocks a decision.

**Expect** one device line beginning `accepted:` and naming the rule that fired.

**The number that matters is which rule, and the full interface list.** Paste the whole line. The
plan's next step narrows `usb_device_filter.xml` from match-all to a set of descriptor triples, and
that set is currently drawn from what the code already believes rather than from any observation of
a real modern phone. If a phone in its default USB mode presents something not in our list, the
narrowed filter would stop offering OHU for it, and this line is the only cheap way to find that out
before shipping it.

Worth repeating with the phone's USB mode set to each of **charging only**, **file transfer / MTP**
and **PTP**, since the descriptors change with the mode and charging-only is the case most likely to
present nothing we match.

### U3 — the wireless Android Auto adapter, if you still have it

The phone-first hypothesis says most adapters bind no USB gadget at all until a phone reaches them
over their own WiFi, which would explain every "it never showed up" report without any fault in our
code. This is a two-part run and the order is the whole test:

1. Plug the adapter into the host phone **with no phone paired to it**. Press the USB button.
   **Expect** `sees 0 USB device(s)` and the `nothing is on the bus` advisory.
2. Pair a phone to the adapter's Bluetooth, wait for it to associate, then press the USB button
   again. **Expect** the device count to become 1.

If step 1 shows 0 and step 2 shows 1, the hypothesis is confirmed and the answer to those reporters
is one sentence of guidance rather than a code change. If step 1 already shows 1, this adapter is
one of the immediate-enumeration kind and the hypothesis does not explain their reports; say which
adapter it is, since both behaviours ship and nothing on the packaging distinguishes them.

Either way, paste the device line. There is no public VID/PID or descriptor dump for any retail
adapter, so this capture is the only hard data anyone has, and it belongs in the results file.
