# self-mode-call-foreground, round 2 brief: can the disabled component be switched on

**Candidate:** `origin/main` @ `ea7aa7e0` ("releasing 3.3.0-beta1"), **the same build round 1 ran and
the one already installed on the phone.** No baseline, no code change, **no build**.

Round 1 answered the question it was written for and found the root cause. This round tests the one
thing that root cause leaves open, which is whether a workaround exists. Everything the reporters are
owed is already in hand; this decides whether they also get something to do about it.

```bash
adb -s $PHONE shell dumpsys package com.andrerinas.headunitrevived | grep versionName
# must print 3.3.0-beta1; if it does not, stop and say so, do not reinstall
```

Read first: `self-mode-call-foreground-round1-results.md` (this thread's round 1), then §0 of
`self-mode-call-foreground-round1-brief.md`, which is still the operating manual for a Self Mode
round and is **not** repeated here. Nothing about the Self Mode protocol has changed.

---

## 0. What round 1 settled, so this round does not re-prove it

Three of round 1's own Setup notes are now standing facts for this thread. They are inherited, not
re-derived, and two of them forbid a verdict:

- **The head unit server toggle can look on while being off.** Round 1's first R1 attempt died on
  `SelfMode: Headunit Server (127.0.0.1:5277) is NOT running.` with the developer settings screen
  visible in the foreground. Tap **Start head unit server** by hand and confirm from the log, every
  time, before believing the rig is ready.
- **The default dialer on this phone is `com.android.dialer`,** not the Pixel reporters'
  `com.google.android.dialer`. Every grep below uses the former. Do not widen it.
- **Neither `Car Swapping ICS` nor `am_on_top_resumed_*` exists on this build.** Round 1 grepped for
  both and got zero, with `am_proc_start` present in the same capture proving the event buffer was
  collected. **No verdict in this round may rest on either.**

And the measured facts this round builds on, all from round 1:

| Fact | Evidence |
|---|---|
| Android Auto claims automotive projection in Self Mode | `CAR.SYS: Successfully set automotive projection.`, agreeing with Telecom within 7 ms |
| Telecom identifies the right component and rejects it | `found:ComponentInfo{.../CarProjectionInCallServiceImpl} isRequestedtype:true isEnabled:false` |
| The Dialer takes the screen on answer | `START u0 {cmp=com.android.dialer/com.android.incallui.InCallActivity} ... from uid 10126`, then `AapProjectionActivity: onPause` 23 ms later |
| Same for an outgoing call | `CarProjectionInCallServiceImpl` absent from the live bound list, `NonCarInCallServiceImpl` present |
| The projection returns by itself | `AapProjectionActivity: onResume` 17 ms after the Dialer's own activity finishes, no `START u0` for our package |

---

## 1. Why this round exists

After round 1 reported, Android Auto `17.5.663204-release` was torn down offline
(`~/ohu-fixes-handoff/gearhead-17.5.663204-release/`). It explains `isEnabled:false` exactly.

**Both** of Android Auto's in-call services ship `android:enabled="false"` in its manifest, so both
depend on a runtime `setComponentEnabledSetting`. `NonCarInCallServiceImpl` is enabled from nine call
sites, which is why round 1 saw it bound and working. `CarProjectionInCallServiceImpl` is written
from exactly one place in all four dex files:
`com.google.android.apps.auto.carservice.gmscorecompat.ComponentInitReceiver.b(Context, Intent)`.
Its tail, disassembled with offsets:

```
013c: sget          v1, Landroid/os/Build$VERSION;->SDK_INT I
0140: if-lt         v1, v4(=33), +00eh          ; jumps to 0x015c
0144: ...
014c: const-string  v8, "enabling InCallService is skipped."
0154: invoke-static ...                          ; tag CAR.ComponentInitRcvr
015a: return-void
015c: sget-object   v1, Lthf;->b                 ; CarProjectionInCallServiceImpl
   .. invoke-static ComponentInitReceiver->c(Context, ComponentName, Z)   ; the only write
```

`v4` is `33`. **On `SDK_INT >= 33` the method logs `enabling InCallService is skipped.` and returns
before ever touching the component**, so it keeps the manifest's disabled state permanently. This
phone is Android 14. Both reporters are on newer. Telecom's car-mode swap needs that component
present *and enabled*, so on any modern phone the swap cannot fire at all, on any head unit, real or
Self Mode. In a real car nobody notices, because the Dialer's takeover lands on the phone's screen
while Android Auto's own call UI holds the car screen. Self Mode makes those the same screen.

**That receiver is registered for `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED` only**, and on Android
13+ both return without writing. So there should be nothing in Android Auto to undo a manual enable.
That is the whole hypothesis of this round.

This is not fixable in Open Headunit and this round does not try. Enabling another app's component
needs `CHANGE_COMPONENT_ENABLED_STATE`, which is signature level. `adb shell` holds it; we never can.

---

## 2. What is different about this round

- **No build and no unit tests.** R0 is a state gate, not a compile gate. If `build_hur.sh` gets run
  anyway that is harmless, but nothing here needs it and the installed APK must not change.
- **This round changes state on the phone that is not ours**, which no round in this channel has done
  before. R4 restores it and is not optional. If the round is abandoned part way for any reason, run
  R4 before stopping.
- **R2 could leave a call with no visible way to end it.** If the swap does happen, Android Auto's
  car in-call UI takes over and it is unknown whether it renders usably in Self Mode. Two fallbacks,
  either is fine: hang up from the second handset, or
  `adb -s $PHONE shell input keyevent KEYCODE_ENDCALL`. Have one ready before dialling.
- **R2 needs the second handset again**, same as round 1. R0, R1, R3 and R4 need no call and no
  second phone.
- **Expected INCONCLUSIVE, said up front:** if R2 shows the component enabled and the swap still does
  not happen, that is a real and useful result, not a failure. Report what Telecom logged instead.

---

## 3. Settings

**None.** This round writes no preference. Round 1's `log-level=2` is already on the phone; confirm
it rather than rewriting it:

```bash
adb -s $PHONE shell run-as com.andrerinas.headunitrevived cat shared_prefs/settings.xml | grep log-level
# expect: <int name="log-level" value="2" />
```

If it reads anything else, set it with `hur-wifi-test-scripts/set_pref.sh` with the app stopped, and
say so in the results.

---

## 4. Capture

Unchanged from round 1, and the `uid` column is still the thing that attributes an activity start.
One tag is added, and it is the one that carries the new evidence:

```bash
adb -s $PHONE shell logcat -G 64M
adb -s $PHONE shell setprop log.tag.GH VERBOSE
adb -s $PHONE shell setprop log.tag.CAR VERBOSE
adb -s $PHONE shell setprop log.tag.Telecom VERBOSE
adb -s $PHONE shell setprop log.tag.InCallController VERBOSE
adb -s $PHONE shell getprop log.tag.CAR       # must print VERBOSE
adb -s $PHONE shell am force-stop com.google.android.projection.gearhead
stdbuf -oL adb -s $PHONE logcat -b all -T 1 -v threadtime,uid,usec > rN.txt &
```

`CAR.ComponentInitRcvr` is under the `CAR.` tag prefix already set above. Note that `setprop` does
not survive a reboot, so **R1 and R3 must re-apply the four `setprop` lines after the phone comes
back and before anything else**. Grep every capture with `-a`.

---

## 5. The lines that decide the round

The instrument is one field on one line, and round 1 already captured its other value, so the two
rounds diff directly:

```
InCallController: found:ComponentInfo{com.google.android.projection.gearhead/com.google.android.apps.auto.components.telecom.service.CarProjectionInCallServiceImpl} isRequestedtype:true isEnabled:true
                                                                                                                                                                                        ^^^^ round 1 read false
```

Supporting, in the order they would appear if the swap works:

```
CAR.ComponentInitRcvr: enabling InCallService is skipped.
CAR.ComponentInitRcvr: Setting %s in Gearhead to: %s
Telecom: InCallController: onCallAdded
Telecom: InCallController: changeCarModeApp: ... => ComponentInfo{...CarProjectionInCallServiceImpl}
Telecom: InCallController: trackCallingUserInterfaceStarted: com.android.dialer is now calling UX
ActivityTaskManager: START u0 {cmp=com.android.dialer/com.android.incallui.InCallActivity} ... from uid <U>
AapProjectionActivity: onPause
AapProjectionActivity: onResume
Throughput over 5000ms: rendered=..., fed=..., dropped=...
```

The last four are app-side and were re-verified with `grep -F` against `ea7aa7e0` while this brief was
written. The `Telecom:` and `CAR.` lines are quoted from round 1's own capture and from the dex.

---

## 6. Runs

### R0, state gate

Nothing here is a call. Record all five, then stop if any disagrees with round 1.

```bash
adb -s $PHONE shell getprop ro.build.version.sdk                          # expect 34
adb -s $PHONE shell dumpsys package com.google.android.projection.gearhead | grep versionName
                                                                          # expect 17.5.663204-release
adb -s $PHONE shell md5sum $(adb -s $PHONE shell pm path com.andrerinas.headunitrevived | head -1 | cut -d: -f2 | tr -d '\r')
                                                                          # expect 5a5a16bc00ab5539dbb9cb145f07cd40
adb -s $PHONE shell pm dump com.google.android.projection.gearhead | grep -n -i -A4 "disabledComponents\|enabledComponents"
adb -s $PHONE shell cmd package query-services -a android.telecom.InCallService --components 2>/dev/null | grep -i gearhead
```

**PASS** when SDK, Android Auto version and APK md5 all match round 1, and the component's current
state is recorded. Expect `CarProjectionInCallServiceImpl` to appear in **neither** the enabled nor
the disabled list, because the manifest default is disabled and nothing has ever overridden it. An
empty result from the last command for that component is the expected reading and is not a failure of
the command; say which of the two you saw.

**FAIL** if the installed APK md5 differs from round 1. Stop, do not reinstall, report it.

### R1, confirm the SDK 33 gate on this device, no call

Free evidence first, from round 1's captures, before touching the phone:

```bash
grep -a -c "enabling InCallService is skipped" <round-1 captures>
grep -a -c "ComponentInitRcvr" <round-1 captures>
```

Both are likely zero, because the receiver only fires on boot and package replace. Record the counts
either way, then run the reboot:

```bash
# start the capture FIRST, it must be running across the boot
stdbuf -oL adb -s $PHONE logcat -b all -T 1 -v threadtime,uid,usec > r1.txt &
adb -s $PHONE shell svc power reboot        # not `adb reboot`, per TESTING-TEMPLATE 7a
# wait for the device, then immediately re-apply the four setprop lines from 4
adb -s $PHONE wait-for-device
```

`adb reboot` bypasses `ActivityManager` entirely; `svc power reboot` goes through `IPowerManager`.
The logcat stream will drop over the reboot, so restart it as soon as `wait-for-device` returns and
accept that the earliest boot lines may be missed. If they are, `logcat -b all -d` immediately after
the device returns will still hold them in the ring buffer, which is the more reliable read of the
two. Use both.

**PASS** when `CAR.ComponentInitRcvr: enabling InCallService is skipped.` appears at least once after
the boot, with no `Setting com.google...CarProjectionInCallServiceImpl in Gearhead to:` line
anywhere. That is the static finding confirmed on hardware.

**FAIL**, and it would be an interesting one, if the component *is* set on boot. Capture the
surrounding `CAR.ComponentInitRcvr` lines verbatim; the whole premise of this round is then wrong.

**INCONCLUSIVE** if neither string appears at all, which most likely means the boot lines were missed
rather than that the receiver did not run. Retry once with the `logcat -b all -d` read.

### R2, the point of the round: enable it, then place the call

```bash
adb -s $PHONE shell pm enable com.google.android.projection.gearhead/com.google.android.apps.auto.components.telecom.service.CarProjectionInCallServiceImpl
adb -s $PHONE shell pm dump com.google.android.projection.gearhead | grep -A4 enabledComponents
```

The `pm enable` prints its own confirmation line. If it errors, quote the error and stop; that alone
answers the round. **Then** bring Self Mode up exactly as round 1 did, confirm the session with a
`Throughput over` line, take 30 s of pre-call baseline, and have the second handset ring the phone:
ring 5 s, answer, hold 5 s, hang up. Keep `KEYCODE_ENDCALL` ready.

**PASS** (the workaround works) when all of:

1. `isEnabled:true` on the `found:ComponentInfo{...CarProjectionInCallServiceImpl}` line.
2. No `START u0 {cmp=com.android.dialer/...InCallActivity}` anywhere in the call window.
3. No `AapProjectionActivity: onPause` in the call window.
4. `Throughput over` still reporting during the call, at a rate comparable to the pre-call baseline.

Condition 4 is what stops conditions 2 and 3 passing for the wrong reason. A dead session also
produces no `START u0` and no `onPause`; a live one with real frames is the proof the call happened
over a working projection. Quote the paired before and during numbers, as round 1 did.

**FAIL** (the workaround does not work) when `isEnabled:true` but the Dialer still takes the screen.
Then report what Telecom did instead: whether `changeCarModeApp` appears, whether
`CarProjectionInCallServiceImpl` ever reaches `onConnected`, and whether
`trackCallingUserInterfaceStarted: com.android.dialer is now calling UX` still fires. Round 1 saw
that component four times, all four the same rejected enumeration line and never a bind, so **any
`onConnected` for it is new information** and worth quoting whatever the verdict.

**INCONCLUSIVE** if the enumeration line does not appear at all this run. It is a Telecom VERBOSE
line; check `getprop log.tag.Telecom` before concluding anything.

### R3, does it survive a reboot

Only if R2 got as far as the component reading enabled. Reboot with `svc power reboot`, re-apply the
`setprop` lines, then re-read:

```bash
adb -s $PHONE shell pm dump com.google.android.projection.gearhead | grep -A4 enabledComponents
grep -a "ComponentInitRcvr" r3.txt
```

**PASS** when the component still reads enabled after the boot. That makes it a workaround a reporter
can be given once and forget.

**FAIL** when it reverts, which would mean something outside `ComponentInitReceiver` writes it and the
teardown missed a path. Quote every `CAR.ComponentInitRcvr` line from the boot.

This run decides how the reporters are answered, so do not skip it even if R2 passed cleanly.

### R4, restore, and it is mandatory

```bash
adb -s $PHONE shell pm default-state com.google.android.projection.gearhead/com.google.android.apps.auto.components.telecom.service.CarProjectionInCallServiceImpl
adb -s $PHONE shell pm dump com.google.android.projection.gearhead | grep -A4 "disabledComponents\|enabledComponents"
```

**`pm default-state`, not `pm disable`.** The manifest default is already disabled, so `pm disable`
would leave an explicit override behind where the phone had none, and every later round in this
thread would start from a state round 1 did not have.

Then end Self Mode the way round 1 did, with `headunit://exit` followed by a force-stop, and confirm:

```bash
adb -s $PHONE shell dumpsys uimode | grep mCarModeEnabled     # expect mCarModeEnabled=false
```

**PASS** when the component is back to appearing in neither list and car mode is off.

---

## 7. Do not re-run

- Anything under F1 to F11 of round 1's brief. In particular nothing tests our own `enableCarMode`
  (round 1 confirmed on-device that Telecom ignores us), `showWhenLocked`, or a notification cancel.
- Round 1's R2 and R4 in their original form. Their result is established for both call directions
  and R2 here is the same call with one thing changed.
- R3 and R5 from round 1, still unrun and still not needed. R5 in particular would only re-establish
  what round 1's own bound-service list already shows, which is that the Dialer path never touches
  our package.
- Do not try to make Open Headunit enable the component. It needs
  `CHANGE_COMPONENT_ENABLED_STATE`, a signature permission, and no amount of manifest work gets it.

---

## 8. Report back

Six things, all of them a number, a string or a yes/no:

1. **R1**: does `enabling InCallService is skipped.` appear after a boot, yes or no, with the count.
2. **R2**: the full `found:ComponentInfo{...CarProjectionInCallServiceImpl} ... isEnabled:` line,
   verbatim.
3. **R2**: the count of `START u0 {cmp=com.android.dialer/...InCallActivity}` in the call window, with
   the `from uid` of each if any.
4. **R2**: the count of `AapProjectionActivity: onPause` in the call window, **paired with** the
   `Throughput over` lines from before and during the call, so a zero cannot be read as a dead
   session.
5. **R2**: whether `CarProjectionInCallServiceImpl` ever reached `onConnected`, yes or no.
6. **R3**: the component's enabled state after the reboot.

Item 6 is the one that decides what the reporters are told. Items 2 and 3 together decide whether
there is anything to tell them at all.
