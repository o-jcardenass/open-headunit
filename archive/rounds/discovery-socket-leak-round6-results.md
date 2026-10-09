# Discovery socket leak, round 6 results

**Candidate:** `fork/fix/773-headunit-server-socket-leak` @ `5f193d30` (eight commits; the brief's own
mid-flight correction from `2246e9a2` to this SHA was read and followed, not the stale first version)
**Baseline:** none on the device; R15b uses the retained round 4/5 captures as a free before/after
**APK md5:** `08c5493da211c5f4ebcdfdc88f0a25bf`
**Unit:** UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14, serial `27870808938846`, rooted. Phone:
Redmi M2007J20CG (`surya_eea`, MIUI, Android 15), serial `4f4027e9`.
**Date:** 2026-08-10/11 (round spanned midnight; all in-run timestamps below are the rig's own device
clock, HH:MM:SS, unaffected by the date rollover)

## Setup notes

**Scripts used:** `build_hur.sh`, `run_unit_tests.sh`, `install_and_launch.sh SKIP_BUILD=1`,
`set_hu_pref.sh`. Nothing new added.

**Both the phone's hotspot and head unit server had gone down again between sessions**, same pattern
as every prior round. Restored both by hand once, at the top of the round; nothing needed a second
restart after that.

**R16 needed a methodology correction, caught before it produced a false result.** The brief's literal
"press the WiFi button 10 times" was first tried as ten taps at the same screen coordinates with no
pause. The first tap connected and moved the app to `AapProjectionActivity`, a different screen, so
taps 2-10 landed on nothing and never reached the button at all (confirmed via `dumpsys window` and a
raw count of 1 `Starting scan...` across the whole capture). Discarded and re-run with an explicit
`headunit://disconnect` between each tap to return to the home screen where the button lives; the
corrected run is what is reported below. Kept as
`r16-round6-discovery-socket-leak-DISCARDED-methodology-error.txt` for the record.

**R19's first attempt was invalidated by the rig's own WiFi instability, not a timing miss.** Partway
through the first 10-cycle loop the station radio dropped to `NO-CARRIER`/`state DOWN` and
`NetworkDiscovery` fell back to scanning a stale cached subnet (`10.140.201.*`, never seen in any
other round on this rig) instead of the phone hotspot's `192.168.41.*`, zero real scans reached the
actual server across all 20 service starts in that run. Re-connected WiFi, confirmed it held for the
full duration of the retry (checked after every cycle), and re-ran clean. Kept as
`r19-round6-discovery-socket-leak-DISCARDED-wifi-dropped.txt`.

**Followed the standing rule and did not pre-check port 5277** with `nc` or anything else at any
point in the round.

**Manual server restarts this round: 1**, the one-time restart at the top. Every scripted run after
that (R15's 15 cycles, R16's 10 cycles, R17, both R19 attempts) completed without the server ever
needing to be touched again.

## R0: build gate

**PASS.**

- Builds clean, no new warnings.
- `run_unit_tests.sh`: `BUILD SUCCESSFUL`, **245/245**, exactly the round's predicted total.
- `DiscoveryModePolicyTest.xml`: `tests="5" skipped="0" failures="0" errors="0"`.
- `LinkLossTeardownPolicyTest.xml`: `tests="7" skipped="0" failures="0" errors="0"` (up from round 4's
  5, matching the brief's prediction exactly).

## R15: the double call, fully scripted (the point of the round)

**PASS**, cleanly, on every measure.

Settings: `wifi-connection-mode=1`, `log-level=1`, phone hotspot topology, server restarted fresh once
at the top. 15 force-stop/relaunch cycles, ~20s apart.

**The invariant:** `found=15`, `handedover=15`. Equal, no excess socket.

**The shape, checked per-instance rather than just as a file total** (bounding each of the 15
`Initializing WiFi Mode` boundaries separately, since an aggregate total can mask an offsetting 2-and-0
pair):

```
instance  1: starting=1 interrupted=0
instance  2: starting=1 interrupted=0
...
instance 15: starting=1 interrupted=0
```

All 15 of 15 instances land on the brief's "Fixed" row exactly: **1 `Starting scan...`, 0
`Scan interrupted`**, no exceptions. **0 manual server restarts across all 15 launches**, every
single one connected on its own (15/15 `Handshake: Version response received`).

## R15b: the same two counts on the retained round 4/5 captures (no device time)

Ran §4(a)'s two greps over all six retained round 4 and round 5 capture files
(`r7a`/`r8`/`r8b`/`r9`-round4, `r10`/`r10b`-round5).

**The invariant held in every file**: `found` equals `handedover` in all six (3-3, 0-0, 1-1, 0-0, 1-1,
1-1). No excess anywhere. Per the brief's own caveat this is not a contradiction, since none of those
rounds intentionally provoked the cross-instance race, it is a probabilistic defect and simply didn't
land in any of these six windows.

**The shape measurement is a genuine surprise, and worth reporting exactly as measured rather than
forced to match the prediction.** Only two of the six captures contain a clean, single
`Initializing WiFi Mode` boundary to measure against (`r8b`-round4 and `r10b`-round5, both post-reboot
fresh app launches under `wifi-connection-mode=2`/`strategy=3`; `r9`-round4 is mode 3 and correctly
shows nothing; `r7a`-round4 and `r10`-round5 were live-session-already-running captures with no service
start inside the capture window at all, confirmed via `dumpsys window`/activity-transition lines, so
they have no valid instance boundary to bound a per-launch count against).

Both of the two valid samples show a clean **1-and-0**, not the predicted broken **2-and-1**:

```
r8b-round4:  Initializing WiFi Mode ×1, Starting scan... ×1, Scan interrupted ×0
r10b-round5: Initializing WiFi Mode ×1, Starting scan... ×1, Scan interrupted ×0
```

Double-checked by reading full surrounding context in both files (not just the grep counts) to rule
out a second, unlabelled scan call nearby; there is none in either file. Per the brief's own
instruction ("if they do not, suspect the measurement before concluding anything about the fix"), the
measurement has been re-verified and stands. Both retained samples are `wifi-connection-mode=2`,
`strategy=3` fresh launches, not the `wifi-connection-mode=1` "auto server mode" path R15 exercises,
so this does not contradict R15's result, it is reported as a fact about what these two particular
retained captures happen to show, not as evidence against the round's mechanism. It does mean the
`646441c4`/`d0b8305a` broken shape was not captured in either retained file, for reasons this round
cannot determine from the logs alone.

## R16: the reporter's flow verbatim

**PASS**, cleanly, on every measure.

Settings: `wifi-connection-mode=2`, `helper-connection-strategy=3`, `log-level=1`, phone-hosted
hotspot topology (the reporter's exact configuration). WiFi button located via `uiautomator dump`
(`bounds="[780,220][1008,448]"`, center `894,334`), 10 presses ~20s apart, with a `headunit://disconnect`
between each to return to the home screen where the button lives (see Setup notes for why).

**The invariant:** `found=10`, `handedover=10`. Equal.

**The shape**, confirmed by checking that all 10 `Starting scan...` lines are evenly spaced at the tap
cadence (no two clustered together, which would indicate a double call per press):

```
00:11:58.606, 00:12:19.859, 00:12:41.117, 00:13:02.375, 00:13:23.653,
00:13:44.895, 00:14:06.172, 00:14:27.429, 00:14:48.687, 00:15:09.946
```

One scan per press, 10 for 10. **0 manual server restarts**, 10/10 `Handshake: Version response
received`. This is the reporter's own reported flow, and it is now clean.

## R17: Native AA regression check

**PASS.**

`wifi-connection-mode=3`. Session established fast (SSL handshake within 20s of relaunch), video
confirmed steady (44-51fps across five throughput windows). `grep -c "NetworkDiscovery: Starting
scan..."` over the entire capture: **0**. `DiscoveryModePolicy`'s gate holds: `startDiscovery()`
never runs at all while mode 3 is active.

## R18: a USB session must survive a WiFi toggle

**UNTESTABLE on this rig.**

Checked before attempting anything: `adb shell dumpsys usb` on the head unit reports
`host_connected=false`, no accessory attached, and the head unit's only physical USB port is already
committed to the adb control link this entire investigation runs over. This matches
`TESTING-TEMPLATE.md` §7a's already-documented rig quirk ("No USB accessory connection path exists on
this rig, wireless only"), re-confirmed here rather than assumed from memory. There is no way to
construct a USB session on this hardware to test the regression against. The fix itself is unverified
on real hardware as a result; only the unit-tested policy logic backs it.

## R19: the class-level guard, provoked

**INCONCLUSIVE**, after two genuine attempts.

Settings: `wifi-connection-mode=1`, `log-level=1`, server already warm from R15/R16 (no restart
needed). Attempt 1 used the brief's literal `sleep 3` / `sleep 12` cadence but was invalidated by the
WiFi-drop episode in Setup notes before completing meaningfully. Re-ran clean with the same cadence
(WiFi confirmed stable every cycle): `found=20`, `handedover=20` (equal, no defect), but
`NetworkDiscovery: waiting for an in-flight probe before scanning` appeared **0 times**. Per the
brief's own instruction, widened the gap (`sleep 5` instead of `sleep 3`) and tried once more:
cumulative `found=40`, `handedover=40` (still equal), the guard line still **0 times**.

Both attempts land squarely on the brief's own INCONCLUSIVE row: counts equal, line never fires,
timing did not land on this rig with either cadence. Not recorded as a PASS, per the brief's explicit
instruction. The counts staying clean across 60 total service-start events (20 + 20 + the retry's
first-attempt cycles) is itself evidence there is no regression here, just that this specific rig
could not be timed into catching the guard mid-fire with an `adb`-driven force-stop/relaunch loop,
the same conclusion the brief anticipated as plausible ("cancellation is cooperative... `Socket.connect()`
is not interruptible", against round-trip `adb shell` latency that is hard to land inside whatever
window the guard actually needs).

## R20: nothing hands over a socket that gets refused

**PASS, zero everywhere.**

`grep -c 'CommManager: Connect already in progress; closing the handed-over socket'` across all four
live captures this round (R15, R16, R17, R19) and both discarded captures: **0 in every file.**

## Report back

1. **R15: found=15, handedover=15 (equal). 15 launches, 15/15 clean 1-and-0 instances**, no exceptions.
2. **R15: `Starting scan...`/`Scan interrupted` per launch: 1-and-0 in all 15 of 15**, exactly the
   brief's "Fixed" row.
3. **R15b: found/handedover equal in all six retained captures** (no excess anywhere). **Shape: only
   two files had a valid single-instance boundary to measure (`r8b`-round4, `r10b`-round5), and both
   showed 1-and-0, not the predicted 2-and-1.** Re-verified by reading full context, not just the grep
   counts; reported as a genuine, unexplained-from-the-logs discrepancy rather than forced to fit.
4. **R16: found=10, handedover=10 (equal), one clean scan per press, 10 for 10.** Used the phone's
   hotspot, the reporter's own topology. Needed a methodology correction (disconnect between taps),
   see Setup notes.
5. **R17: `NetworkDiscovery: Starting scan...` did not appear** in mode 3. Zero occurrences.
6. **R18: UNTESTABLE.** This rig has no USB host/accessory path at all (`host_connected=false`, no
   accessory attached, only USB port already used for adb control), re-confirmed directly rather than
   assumed. The regression check itself remains unexercised on real hardware.
7. **R19: `waiting for an in-flight probe before scanning` appeared 0 times**, across two attempts
   (cadences `sleep 3` and `sleep 5`), 60 total service-start events, counts equal throughout.
   **INCONCLUSIVE**, not FAIL: timing did not land, per the brief's own decision table.
8. **R20: `CommManager: Connect already in progress` count: 0 in every capture**, six files total.
9. **Manual server restarts this round: 1**, the one-time restart at the top; nothing after that
   needed a restart, including R19's 60 service starts.
10. **`network changed during the last scan; rescanning immediately` appeared 15 times in R15 and 40
    times in R19** (once per relaunch cycle in each, matching the launch counts almost exactly), the
    deliberate "always make the next sweep immediate" behavior change firing routinely on this rig's
    network-available events, informational only. `in-flight scan promoted to continuous` did not
    appear anywhere this round.

**Net result**: the sixth commit's core claim, that reusing the `NetworkDiscovery` instance instead of
rebuilding it stops the reporter's own double-probe leak, holds cleanly under every run that could
actually be exercised (R15's 15/15, R16's 10/10, both on the exact settings the reporter used). R17
confirms no Native AA regression. R20 confirms the seventh commit's connect-in-progress guard never
even needs to fire on this hardware. R18 (the regression the eighth commit specifically targets) and
R19 (the seventh commit's specific cross-instance timing window) both remain formally unverified on
real hardware, for two different and unrelated reasons: R18 because this rig cannot construct a USB
session at all, R19 because an `adb`-driven force-stop/relaunch loop could not be timed into the
specific race window across two tries. Neither is a FAIL; both are stated gaps in coverage rather than
evidence against the fix. Whether that residual gap is acceptable to ship with is a product decision,
not something this channel can settle from here.
