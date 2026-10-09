# post-beta1-self-mode, round 1 brief

## 1. Build and baseline

| Arm | Branch | SHA | Purpose |
|---|---|---|---|
| **candidate** | `fix/post-beta1-self-mode-and-log-probe` | `8bc6fcce` | the fix |
| **baseline** | `main` | `71930d54` | reproduces both defects |

```bash
git fetch fork fix/post-beta1-self-mode-and-log-probe
git checkout 8bc6fcce
```

Two commits off `main`, no rebase, no history rewrite. `main` is the baseline because both defects
were introduced *after* 3.3.0-beta1 and are present on `main` today.

**This round needs two APKs.** `build_hur.sh` deletes `apks/` before each build, so copy each APK
into a round folder the moment it is built, and check the md5 before every install. Install with
`adb install -r -d`; round 1 of `post-beta1-latency-instruments` confirmed on all three devices that
this preserves `settings.xml`.

`@Test` count: **770** on the candidate against **765** on `main`. The five new ones are
`SelfLaunchTimeoutPolicyTest`.

## 2. What this is and why it exists

3.3.0-beta1 (`ea7aa7e0`) had working Self Mode. Current `main` asks for log permission on every app
open and fails Self Mode with VPN-related errors. Only three functional commits sit in that window
and two of them are the cause, one symptom each.

**Defect 1, from `bae47b23`.** `Settings.logSource`'s getter resolved its default by calling
`LogExporter.isLogcatSupported()`, which spawns `logcat` and blocks in `waitFor()`. The default was
computed *before* `getOrElse`, so the probe ran even when the stored preference already answered the
question, and `App.onCreate` reaches it through `AppLog.init`. Main thread, every process start. On
Android 13+ each spawn raises the system log-access consent dialog, which `pm grant READ_LOGS` does
not suppress. Your own round 1 measured it firing 55 times across scripted relaunches on D-HU, once
taking SystemUI down with it and once leaving the app alive with no log output for 90 s.

The fix restores the plain prefs read and deletes the probe. The useful half of `bae47b23` stays and
needed no probe: `launchLogcatPipe` already detects a ROM that refuses logcat from the capture that
actually ran, and auto-switches to `APPLOG_FILE`.

**Defect 2, from `8e5743d4` (in merge `a3da6af1`, upstream PR #892).** The launcher refactor added a
2500 ms watchdog that called the also-new `CommManager.emitError`, which calls `disconnect()`. The
`Error` emit is what unblocks the teardown, because `disconnect()` returns early only on an
already-`Disconnected` state. On the AA < 17.4 route that teardown removes both things the phone
still has to arrive on: `AapService.onDisconnected` drops the dummy VPN whose `Network` object was
handed to Gearhead as `PARAM_SERVICE_WIFI_NETWORK`, and `scheduleReconnectIfNeeded` calls
`wifiLauncherManager.stop()`, which closes the port 5288 server. Gearhead needs 15 to 20 s on that
route, and `SelfLauncherLegacy.runWifiLauncher` already spends up to one of them waiting for the VPN
to become the active network. So the deadline expired on every attempt and killed sessions that were
working.

The fix adds `SelfLaunchTimeoutPolicy`: a deadline each route can meet, and no route may disconnect
on a timeout. `CommManager.reportError` says the same thing without the teardown.

**Defect 3, same commit, always downstream of defect 2.** `PermissionTrampolineActivity`'s own class
comment says AA's permission activity closes at once when everything is granted; `onActivityResult`
then treated any return under a second as a failure, so a healthy check produced a "Failed to start
Android Auto" toast every time. You saw this on every legacy attempt on both AA-17.3 devices. A
returned result now means permissions are not the problem whatever it cost.

**The dummy VPN is not a fault and nothing in this branch removes it.** It is how Self Mode gets a
non-null `activeNetwork` for its own process when the unit is offline, which is why
`DummyVpnPolicy.Owner.SELF_MODE` exists. Reading it as the problem is what this round is partly here
to correct.

## 3. What is different about this round

**Your round-1 results attributed defect 2 to something older than `71930d54`.** That conclusion is
corrected above: `git log ea7aa7e0..71930d54` is three commits, and the watchdog is in one of them.
Nothing in that file needs re-running, but do not carry its attribution forward.

**There are two ways to start Self Mode and they are not equivalent.** This is the one thing most
likely to silently waste the round.

- `am start -a com.andrerinas.openheadunit.ACTION_START_SELF_MODE` goes to `AutomationActivity`,
  which starts `AapService` directly. `HomeFragment.startSelfMode()` never runs, so
  `VpnControl.startVpn` never runs, so **there is no dummy VPN on this route at all**.
- `auto-start-self-mode=true` plus a plain `MainActivity` launch goes through
  `HomeFragment.startSelfMode()`, which is the only caller of `VpnControl.startVpn`.

Every run below uses the second route. §4's settings table is built for it.

**The dummy VPN only comes up when the device has no active network.**
`HomeFragment.startSelfMode()` guards on `connectivityManager.activeNetwork == null`. A device on
WiFi or mobile data takes Self Mode with no VPN at all. That is fine for most of this round, because
the half of the teardown that kills the session regardless is the port 5288 close. R5 is the one run
that needs the VPN up, and it says how to create the condition and how to prove it happened.

**Pre-registered, so none of these reads as a failure:**

- **R5 may be INCONCLUSIVE.** If the offline precondition cannot be held on a device that is
  connected to the PC by adb over USB, say so and move on. R3 and R4 still decide the round.
- **D-MOTO's Gearhead error screen has two possible readings and both are useful.** Your round 1
  attributed `CarErrorDisplayActivityImpl` to the all-uid non-bypassable tun. It may instead have
  been the network vanishing under Gearhead at 2.5 s, which is exactly what the fix removes. R6
  distinguishes them. Either answer is a result, not a failure.
- **A device that connects on the baseline too would mean the deadline was met by luck.** R4 is the
  arm that can come back "did nothing". If baseline D-HU connects, report the time to
  `WirelessServer: Incoming connection detected` on both arms rather than a bare pass/fail: under
  2.5 s on the baseline means that device is simply fast enough to beat the old deadline, and the
  round then rests on D-MOTO plus the JVM tests.

**Verify before assuming, and put the answer in Setup notes:**

```bash
adb -s <dev> shell dumpsys package com.google.android.projection.gearhead | grep -m1 versionName
adb -s <dev> shell dumpsys connectivity | grep -m3 -i "active default network\|NetworkAgentInfo"
```

Gearhead version decides which route each device takes, and it drifts between rounds. Round 1 saw
17.3.662864 on D-HU, 17.5.663204 on D-POCO and 17.3.662854 on D-MOTO, which put D-HU and D-MOTO on
the legacy route and D-POCO on the head unit server route. Confirm rather than inherit.

## 4. Settings keys this round needs

All three devices, written with the app force-stopped, `set_prefs_runas.sh` from round 1.

| Key | Type | Value | Why |
|---|---|---|---|
| `auto-start-self-mode` | boolean | `true` | the only route that reaches `HomeFragment.startSelfMode()`, §3 |
| `auto-connect-last-session` | boolean | `false` | comes first in the default priority order and would win |
| `auto-connect-single-usb` | boolean | `false` | same |
| `auto-connect-delay-seconds` | int | `0` | default, but round 1 left non-defaults behind |
| `log-level` | int | `2` | INFO. Every line in §5 is INFO or above and unguarded; VERBOSE only costs ring buffer on D-HU |
| `log-capture-enabled` | boolean | **absent** for R1, `true` for R2 | this key is the whole of R1 |
| `log-source` | int | `0` | LOGCAT. Absent reads as this on both arms after the fix |

Diff `settings.xml` against a fresh backup at the start and state the delta in Setup notes even if it
is zero. Round 1 found `wifi-connection-mode` silently normalised to `1` on all three devices during
failed Self Mode attempts; check it again at the end of R3 and R4 and report it either way.

## 5. The lines that decide every run

All verified with `grep -F` against `8bc6fcce`. Use `grep -a`, always.

**New on the candidate:**

```
SelfMode: nothing connected within 30000ms of the launch
SelfMode: AA's permissions are in order; the launch timed out for another reason.
SelfMode: AA's permission activity could not be started.
SelfMode: could not start AA's permission activity:
```

**The teardown, which must NOT appear within the deadline on the candidate:**

```
AapService: releasing the dummy VPN (owner=
AapService: Self Mode disconnected. Not restarting.
```

**Route and progress, both arms:**

```
SelfMode: Installed AA version:
SelfMode: AA < 17.4 detected. Starting WirelessServer on 5288 and running legacy triggers...
SelfMode: AA 17.4+ detected. Connecting directly to Headunit Server on 127.0.0.1:5277...
SelfMode: Launching AA Wireless Startup via Activity...
SelfMode: Launch of '
SelfMode: All launchers failed
SelfMode: AA permissions request took
SelfMode: Failed, timed out!
WirelessServer: Incoming connection detected from
Throughput over
```

**VPN, when it is up:**

```
AapService: dummy VPN requested (owner=
DummyVpnService: tun established (excludeSelf=
```

**Logging:**

```
LogExporter: session |
LogExporter: log source is APPLOG_FILE; logcat capture is disabled
LogExporter: Logcat capture produced 0 bytes
```

`SelfMode: nothing connected within 30000ms of the launch` is the composed form. The source reads
`nothing connected within ${deadlineMs}ms of the launch`, and `SelfLaunchTimeoutPolicy.LEGACY_DEADLINE_MS`
is `30_000L`, so grep the prefix `SelfMode: nothing connected within` and read the number back rather
than matching the whole line.

**On the baseline, for R4 and R6.** These four are verified against `71930d54` and are what the
defects look like:

```
SelfMode: All launchers failed (timeout)
SelfMode: Permission activity closed immediately or failed.
AapService: Self Mode disconnected. Not restarting.
AapService: releasing the dummy VPN (owner=
```

Two notes on levels. `Handshake: SSL handshake complete` is `AppLog.d` and will not appear at
`log-level=2`; use `WirelessServer: Incoming connection detected from` and `Throughput over` as the
proof a session formed, both `AppLog.i` and unguarded. `LogAccessDialogActivity` is a **system**
string, not ours, so it is not grep-verifiable against the branch; it is what round 1 observed and R1
below gives a detector that does not depend on it.

## 6. Runs

### R0: build and unit-test gate

Both arms. `./gradlew :app:assembleGithubDebug` and `./gradlew :app:testGithubDebugUnitTest`.

- **PASS:** both build. Candidate 770 tests 0 failures, baseline 765 tests 0 failures.
- Record both md5s. Nothing below runs until this passes.

### R1: the log dialog, all three devices. POINT OF THE ROUND, half 1

`log-capture-enabled` absent. External capture per §2, started before the launch.

Per device, per arm: force-stop, `adb logcat -c`, start the capture, `am start -n $MAIN`, then poll
the foreground activity every 500 ms for 15 s:

```bash
adb -s <dev> shell dumpsys activity activities | grep -m1 -iE "mResumedActivity|topResumedActivity"
```

- **PASS (candidate):** `MainActivity` is the resumed activity within a few seconds, on every device,
  with no system log-access dialog in front of it, and no relaunch produces one.
- **Expected (baseline):** the log-access consent dialog takes the foreground instead, and
  `MainActivity` does not resume until it is answered. This is the reproduction.
- **If the baseline does not show it on a device**, that device has either already granted consent
  durably or is below API 33. Report its API level and consent state rather than treating it as a
  candidate pass. The candidate arm is still meaningful on its own: it must never spawn `logcat`.
- Corroborate independently of the dialog string, which is what makes this run robust:

```bash
adb -s <dev> shell ps -A -o USER,PID,PPID,NAME | grep -a -i logcat
```

  On the candidate with capture off there must be **no** `logcat` process owned by the app's user.
  Sample it repeatedly across the launch.

### R2: capture still works on the candidate

D-HU only. `log-capture-enabled=true`, `log-level=2`, `log-source=0`. Launch, let a Self Mode
session run 60 s, then export the app's own log.

- **PASS:** the app's `HUR_Log_*.txt` exists, is non-empty, and contains `LogExporter: session |`.
- This guards against the fix having broken the feature it touched. A pass here plus a pass in R1 is
  the whole shipping question for defect 1.
- If the device is one where logcat is genuinely restricted, `LogExporter: Logcat capture produced 0
  bytes` followed by the file source switching is also a PASS, and is the auto-switch working
  without a probe. Say which of the two happened.

### R3: legacy Self Mode on the candidate. POINT OF THE ROUND, half 2

D-HU and D-MOTO, the two AA-17.3 devices. Settings per §4. Capture, launch `MainActivity`, leave it
alone for 90 s.

- **PASS, all of:**
  1. `SelfMode: AA < 17.4 detected.` appears, confirming the route.
  2. `WirelessServer: Incoming connection detected from` appears, and `Throughput over` lines follow.
  3. **Neither** `AapService: releasing the dummy VPN (owner=` **nor** `AapService: Self Mode
     disconnected. Not restarting.` appears in the first 30 s.
  4. No `SelfMode: nothing connected within` at all, since the session formed.
  5. **No "Failed to start Android Auto" toast**, and no `SelfMode: AA's permission activity could
     not be started.` This is defect 3.
- **Report the elapsed time** from `SelfMode: Launching AA Wireless Startup via Activity...` to
  `WirelessServer: Incoming connection detected from`. It is the number R4 is compared against and
  the one that says whether the old 2500 ms deadline was ever survivable on this device.
- Diff `settings.xml` afterwards and report whether `wifi-connection-mode` moved.

### R4: the same on the baseline, the A/B

Same two devices, same settings, baseline APK. Verify the md5 before the capture.

- **Expected:** at about 2.5 s, `SelfMode: All launchers failed (timeout)` appears, followed by
  `AapService: Self Mode disconnected. Not restarting.` and, where a VPN was up, `AapService:
  releasing the dummy VPN (owner=SELF_MODE, reason=SESSION_ENDED)`. No
  `WirelessServer: Incoming connection detected from` follows. `SelfMode: nothing connected within`
  cannot appear on this arm; that string does not exist in `71930d54`.
- **Defect 3 corroborates here too:** expect `SelfMode: Permission activity closed immediately or
  failed.` after a `SelfMode: AA permissions request took <N> ms` where N is well under 1000, which
  is the inverted success test firing. R7 is where its fix is checked.
- **This is what makes R3 mean something.** A candidate PASS with a baseline that also connects
  proves nothing about the mechanism, so report the R3-versus-R4 elapsed times side by side, not
  just the verdicts.

### R5: with the dummy VPN actually up

D-HU. The precondition is `activeNetwork == null`, which the app checks before it starts the VPN.

```bash
adb -s <dev> shell svc wifi disable
adb -s <dev> shell svc data disable        # if the device has a modem
adb -s <dev> shell dumpsys connectivity | grep -m3 -i "active default network"
```

Then run R3's procedure on the candidate and R4's on the baseline.

- **Precondition met** only if `AapService: dummy VPN requested (owner=SELF_MODE)` and
  `DummyVpnService: tun established (excludeSelf=false)` both appear. If they do not, the device
  still had a network and this run is **INCONCLUSIVE**, not a failure. Say so and restore the radios.
- **PASS (candidate):** the tun stays established for the whole 90 s, with no `AapService: releasing
  the dummy VPN`.
- **Expected (baseline):** the release line lands about 2.5 s in.
- Per the template's rig notes, `svc wifi enable` does not reliably restore the radio. Verify with
  `settings get global wifi_on` and nudge twice if needed. Restore both radios before anything else.

### R6: D-MOTO's Gearhead error screen

D-MOTO, both arms, watching for Gearhead's own error activity:

```bash
adb -s ZY22GC3BM4 shell dumpsys activity activities | grep -a -i "CarErrorDisplay"
```

Two readings, both worth having:

- **It appears on the baseline and not on the candidate:** the error was the network vanishing under
  Gearhead at 2.5 s, and round 1's attribution to the all-uid tun was wrong. Self Mode is testable on
  D-MOTO after all.
- **It appears on both:** round 1's attribution stands, D-MOTO stays UNTESTABLE for Self Mode, and
  the branch is neither helped nor harmed by it. Route it to a `session-vpn-lever` follow-up.

### R7: the timeout still reports when a launch genuinely fails. Positive control

D-HU, candidate only. Run R3's setup, then kill Gearhead 3 s after the launch so nothing can ever
arrive:

```bash
adb -s <dev> shell am force-stop com.google.android.projection.gearhead
```

Hold for 45 s, which clears the 30 s deadline.

- **PASS, all of:**
  1. `SelfMode: nothing connected within 30000ms of the launch` appears, at about 30 s and not 2.5 s.
  2. `SelfMode: Failed, timed out!` follows it.
  3. `SelfMode: AA permissions request took <N> ms` appears, and is followed by `SelfMode: AA's
     permissions are in order; the launch timed out for another reason.` and **not** by `SelfMode:
     AA's permission activity could not be started.` That is defect 3's fix on the branch where it
     matters.
  4. **Still no** `AapService: releasing the dummy VPN` and no `AapService: Self Mode disconnected.
     Not restarting.` The report happens; the teardown does not.
- This is the run that proves the fix did not simply delete the reporting the upstream author added.
- If Gearhead restarts itself and connects anyway, the run is INCONCLUSIVE. Report the timestamps.

### R8: the 17.4+ route is unaffected

D-POCO, candidate only. Same settings, same procedure as R3.

- **PASS:** `SelfMode: AA 17.4+ detected.` appears, the session forms, and none of the teardown lines
  or timeout lines appear at all. No VPN is involved on this route.
- The point is a regression guard: this route was already healthy in round 1 and must stay that way.
- **Do not** open and abandon a connection to `127.0.0.1:5277` by hand. That server binds one
  connection for the life of its process and an abandoned one leaves it deaf until Android Auto is
  restarted by hand.

## 7. Do not re-run

- Anything from `post-beta1-latency-instruments-round1-results.md`. Different branch, different
  question. Its R0 finding stands: `InboundRateMonitorTest.kt:67` expects `2000L` where `1000L` is
  correct. That test is not on this branch and does not affect R0 here.
- The decoder instruments, `presented=`, `decodeLatency=`, the codec ladder. Untouched by this branch.
- The MediaTek rung. Still no MediaTek component on any of the three devices.

## 8. Report back

Six numbers and two verdicts decide whether this ships.

1. **R1:** for each device and arm, seconds until `MainActivity` resumed, and whether a
   `logcat` process owned by the app appeared with capture off.
2. **R3 against R4:** for D-HU and D-MOTO, elapsed seconds from `Launching AA Wireless Startup` to
   `Incoming connection detected`, on both arms. If the baseline never reaches the second line, say
   so and give the timestamp of the teardown instead.
3. **R5:** whether the tun was established at all, and if so how long it stayed up on each arm.
4. **R6:** which of the two readings D-MOTO gave.
5. **R7:** the timestamp of `nothing connected within 30000ms`, and whether either teardown line
   appeared anywhere in that capture.
6. **R2 and R8:** plain PASS or FAIL.

Also worth a line each if you see them: whether `wifi-connection-mode` moved during any Self Mode
attempt, and whether the log-access consent state on any device changed under you mid-round.
