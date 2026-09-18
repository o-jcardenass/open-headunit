# self-mode-call-foreground, round 2 results: the disabled component cannot be switched on

**Candidate:** `origin/main` @ `ea7aa7e0` ("releasing 3.3.0-beta1") — same build round 1 ran, no baseline, no code change, no build.
**APK md5:** `5a5a16bc00ab5539dbb9cb145f07cd40` (matches round 1)
**Unit:** M2007J20CG, `ro.build.version.sdk=35`, `ro.build.version.release=15`; Android Auto `17.5.663204-release`
**Date:** 2026-08-25

## Setup notes

- Only one device was connected (`4f4027e9`, M2007J20CG) — the phone itself, correct for a Self Mode
  round; no second head-unit rig is used by this thread.
- `ro.build.version.sdk` read **35**, not the 34 round 1 recorded and this brief expected — the phone
  took an OTA to Android 15 between rounds. Doesn't change the round's premise: the dex gate is
  `SDK_INT >= 33`, which still holds at 35. Noted here so the next brief in this thread doesn't quote
  34 as current.
- Round 1's raw logcat captures are not preserved on this branch (only its markdown results), so R1's
  "free evidence from round 1's captures" step could not be performed. Went straight to the reboot.
- `adb shell svc power reboot` needed an explicit one-time operator approval (blocked by the harness's
  own auto-mode classifier as a device-reboot action) before R1 could run. Approved, then it ran
  exactly as scripted.
- R2's `pm enable` failed before any call was placed, which per the brief's own §6 instruction ("If it
  errors, quote the error and stop; that alone answers the round") means no second handset was ever
  needed and R3 does not apply (gated on R2 reaching an enabled reading).
- `hur-wifi-test-scripts/` was not used this round — no build, no app relaunch, no settings write. The
  round-1 `log-level=2` was confirmed unchanged, not rewritten.

## R0 — state gate

**PASS**

- Settings written: none
- Discard-rule check: n/a, no session run
- Decisive values:
  - `com.andrerinas.headunitrevived` versionName = `3.3.0-beta1`
  - APK md5 = `5a5a16bc00ab5539dbb9cb145f07cd40` (matches round 1)
  - Gearhead versionName = `17.5.663204-release` (matches round 1)
  - `ro.build.version.sdk` = `35` (round 1/brief expected 34 — see Setup notes); `ro.build.version.release` = `15`
  - `CarProjectionInCallServiceImpl` absent from both `disabledComponents:` and `enabledComponents:`
    in `pm dump` — the expected reading, manifest default (disabled) never overridden
  - `cmd package query-services -a android.telecom.InCallService --components | grep gearhead` → empty (expected)

## R1 — confirm the SDK 33 gate on this device, no call

**PASS**

- Settings written: `setprop log.tag.{GH,CAR,Telecom,InCallController}=VERBOSE`, applied twice (before
  the reboot and again immediately after, since `setprop` does not survive it)
- Discard-rule check: n/a, not a session run
- Decisive log lines:
  - Streamed capture (`logcat -b all -T 1 ...` running across the reboot) caught **0** of both target
    strings — confirms the brief's own prediction that the stream drops the earliest boot lines.
  - Ring-buffer dump immediately after boot (`logcat -b all -d`) caught them:
    `enabling InCallService is skipped` count=**1**, `ComponentInitRcvr` count=**4**.
    ```
    08-25 10:28:55.596452 10193 4656 4656 I CAR.ComponentInitRcvr: Setting ComponentInfo{.../FirstActivityImpl} in Gearhead to: enabled
    08-25 10:28:55.599879 10193 4656 4656 I CAR.ComponentInitRcvr: Setting ComponentInfo{.../CarUsbReceiver} in Gearhead to: disabled
    08-25 10:28:55.600953 10193 4656 4656 I CAR.ComponentInitRcvr: Setting ComponentInfo{.../CarUsbReceiverTPlus} in Gearhead to: enabled
    08-25 10:28:55.604233 10193 4656 4656 I CAR.ComponentInitRcvr: enabling InCallService is skipped.
    ```
  - No `Setting ComponentInfo{...CarProjectionInCallServiceImpl} in Gearhead to:` line anywhere in
    either capture — the receiver ran, wrote three unrelated components, and skipped ours exactly as
    the brief's dex teardown predicted.
- Measurements: reboot issued 10:28:13, adb reported the device back at 10:28:37 (~24s), `sys.boot_completed=1` confirmed on first poll after.

## R2 — the point of the round: enable it, then place the call

**FAIL** (the workaround does not exist) — decided before a call was ever needed.

```
$ adb -s $PHONE shell pm enable com.google.android.projection.gearhead/com.google.android.apps.auto.components.telecom.service.CarProjectionInCallServiceImpl
Exception occurred while executing 'enable':
java.lang.SecurityException: Shell cannot change component state for ComponentInfo{com.google.android.projection.gearhead/com.google.android.apps.auto.components.telecom.service.CarProjectionInCallServiceImpl} to 1
	at com.android.server.pm.PackageManagerService.setEnabledSettings(PackageManagerService.java:3959)
	...
exit=255
```

Per the brief's own instruction, this is the answer: no call was placed, no second handset was
needed, and none of items 2-5 in §8 have data (see below). The round's premise (§1: "`adb shell` holds
it; we never can [i.e. Open Headunit can't]") assumed `adb shell` itself *could* flip the setting. On
this Android 15 build it cannot — the SecurityException is thrown by `setEnabledSettings` itself, not
by a lower-level check Open Headunit could route around some other way.

## R3 — does it survive a reboot

**N/A**, per the brief's own gate: only runs "if R2 got as far as the component reading enabled." It
never did.

## R4 — restore, mandatory

**PASS** (a verification, not an actual restore — nothing was ever changed)

```
$ adb -s $PHONE shell pm default-state com.google.android.projection.gearhead/.../CarProjectionInCallServiceImpl
Exception occurred while executing 'default-state':
java.lang.SecurityException: Shell cannot change component state for ComponentInfo{...CarProjectionInCallServiceImpl} to 0
	at com.android.server.pm.PackageManagerService.setEnabledSettings(PackageManagerService.java:3959)
	...
exit=255
```

The identical exception fires in the restore direction too — symmetric denial, confirming there was
nothing to undo. `pm dump` afterward reproduced R0's reading exactly: `CarProjectionInCallServiceImpl`
absent from both `disabledComponents:` and `enabledComponents:`. `dumpsys uimode | grep
mCarModeEnabled` → `mCarModeEnabled=false` — Self Mode was never brought up this round (R2 stopped
before that step), so there was nothing to tear down there either.

The round's one wrong assumption was its own: `adb shell` cannot flip this component's enabled state
in either direction on this build, which is a cleaner, more decisive result than a partial workaround
would have been — it forecloses the "give the reporters a `pm enable` they can run" answer entirely,
rather than leaving it dependent on a call that might or might not have shown the swap working.

## Anything the brief did not ask about

- The SDK drift (34→35, i.e. an Android 14→15 OTA) between round 1 and this round is worth carrying
  into whatever comes next for this phone: round 1's own facts (default dialer, missing `Car Swapping
  ICS`/`am_on_top_resumed_*` events) were recorded at SDK 34 and have not been re-verified at 35.
  Nothing in this round contradicts them, but they were not re-checked either.
- This closes the "workaround" question for Discussion #883 more firmly than an INCONCLUSIVE would
  have: it's not just that Open Headunit lacks `CHANGE_COMPONENT_ENABLED_STATE` (already known), it's
  that even the elevated `adb shell` identity the brief assumed *would* hold it, doesn't, on a current
  phone. There is no adb-only recipe to hand a reporter either.
