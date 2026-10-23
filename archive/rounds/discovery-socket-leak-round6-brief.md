# Discovery socket leak: round 6 brief

> **This brief changed after it was first pushed, and the SHA moved.** It was written against
> `2246e9a2`; a code review of that commit found three holes in it and one regression it had
> introduced, and the fixes are commits 7 and 8. If you already built `2246e9a2`, **that build is
> stale — rebuild.** Nothing that was already written down has been altered: §4's measurements and
> R15/R15b/R16/R17 stand as they were, except for the one note in §4 that told you to ignore a
> missing log line, which is now a verdict. R18-R20 are new. See §2a.

## 1. Build and baseline

**Candidate:** `fork/fix/773-headunit-server-socket-leak` @ **`5f193d30`** — eight commits. The
first five are round 5's `646441c4` unchanged.

**Baseline:** none on the device. There is a **free baseline in your retained captures** — see R15b,
which costs no device time at all.

```bash
git fetch fork && git merge --ff-only fork/fix/773-headunit-server-socket-leak
```

History was not rewritten; `646441c4` and `2246e9a2` are both ancestors. **Rebuild and reinstall** —
neither round 5's APK (`e88f603db2d639d690735b7874e50d8b`) nor anything built from `2246e9a2` is
this build. Record the new md5.

## 2. Why there is a sixth commit: the branch did not fix the reporter's case

Rounds 1-5 confirmed everything they tested, and R10 closed the last of them. Re-reading the
reporter's own log against the source then showed the branch does not fix *him*, and the gap is a
plain one.

`NetworkDiscovery` serialises concurrent scans **within one instance**. `AapService.startDiscovery()`
answered every scan request by stopping the old instance and constructing a **new** one — whose
`scanJob` is null, so its guard had nothing to wait for and it probed port 5277 while the discarded
instance still had a probe in flight. Cancellation is cooperative and `Socket.connect()` is not
interruptible, so the discarded probe runs to completion, reaches the server, and is thrown away.

The reporter's log shows it directly: `Found Headunit Server` **13** times against
`Auto-connecting to Headunit Server` **10**. Three sockets opened to the head unit server and
abandoned. Closing them afterwards does not help — your own `nc -w 3` accident established that the
damage is done at `accept()` — so the only fix is that the second probe never opens.

The sixth commit reuses the instance instead of rebuilding it, and adds a class-level in-flight job
so two *different* instances cannot probe at once either.

**Why five rounds missed it.** R2 tested the scan race via the network-available path, which calls
`startScan()` on the existing instance and was genuinely fixed. Nothing exercised the
`startDiscovery()` path, which is the one the user's WiFi button reaches.

### There are two regressions here, from two different releases

Worth knowing because it explains why a fix that looked complete was not, and it makes R15b's
expectation exact.

- **3.1.1** added the `onAvailable` `stop(); startScan()` race (`c36fc8e7`). That is what the
  reporter originally complained about, and commits 1-5 fix it.
- **3.2.0-beta2** added a second one on the same path: `d0b8305a` ("Register NSD for the Hotspot
  helper strategies too") widened `shouldRegisterNsd` from `mode == 1 || (mode == 2 && strategy == 0)`
  to include strategies **3 and 4**. `startWirelessServer()` therefore began calling
  `startDiscovery()` on the reporter's exact configuration, on top of the call the WiFi button
  already makes — **two calls per press.** Commit 6 fixes that one.

`d0b8305a` is in every release from `v.3.2.0` onward, including `v.3.2.1`, which is the build the
reporter installed to produce his log. It is **not** in `v.3.1.1`. So his log shows the newer
regression, and the double `Starting scan...` in it is the direct fingerprint of that commit.

**Consequence for R15b:** rounds 4 and 5 ran on `646441c4`, which contains `d0b8305a`, so those
captures **should** show the broken 2-and-1 shape. If they do not, suspect the measurement before
concluding anything about the fix.

## 2a. Why there are then a seventh and an eighth: the sixth had holes

Found by reading commit 6 back against the paths that reach it. None of this was found by a run, and
none of it invalidates rounds 1-5 — but two of the three would have made R19 impossible to pass and
the third is a regression this branch introduced in round 4's commit.

- **The class-level guard never engaged.** It tested `inFlightScan.isActive`. A job that has just
  been cancelled is in *Cancelling*, where `isActive` is already `false` while its probes are still
  unwinding — and every cross-instance case exists *because* the old instance was stopped, since
  `stopWirelessServer()` cancels the scan and nulls the field in the same breath. So the guard
  skipped the one state it was written to catch, and `waiting for an in-flight probe before
  scanning` could not fire on `initWifiMode`, `ACTION_BT_AUTO_START` or `ACTION_STOP_WIRELESS`. The
  test is now "not finished". **This is what R19 measures.**
- **A rescan during a connect still opened a socket to 5277.** `isConnected` excludes `Connecting`
  by design, so a sweep landing in the connect-and-handshake window — up to twelve seconds — found
  the server, handed the socket over, and `connect()` refused it at its own `Connecting` guard and
  closed it. Your `nc -w 3` accident already established that closing does not undo an `accept()`.
  Discovery now asks `isBusy`. **This is what R20 measures**, and the line it looks for is one
  commit 2 added precisely so this would be visible.
- **A WiFi toggle tore down a healthy USB session.** The teardown added in round 4's commit decided
  from `wifiConnectionMode`, which is a stored setting and says nothing about what is running. A USB
  drive under the default mode 1 was being disconnected by the WiFi radio going off — and head units
  toggle it on their own. It now asks the session. **This is what R18 measures.**

One deliberate behaviour change came with them: 3.1.1's "scan the moment the network arrives" was
being given up by commit 6, because the kick is dropped whenever a sweep is already running. It now
makes the *next* sweep immediate instead, so nothing is ever cancelled to go faster. If you see
`network changed during the last scan; rescanning immediately`, that is it working.

## 3. What is different about this round

- **The verdict is two numbers, not an impression.** See §4.
- **R15 needs no tapping and no phone-side setup.** The auto server mode calls `startDiscovery()`
  twice inside `initWifiMode` itself (once via `startWirelessServer`, once explicitly), so **every
  service start reproduces the double call**. A force-stop/relaunch loop is the whole run.
- **R15b is free.** Your round 4 and round 5 captures were taken on the broken build; the same two
  greps on those files give a same-hardware before/after. No device time.
- **R16 is the reporter's flow and needs one tap**, because the WiFi button's action
  (`ACTION_START_WIRELESS_SCAN`) reaches a service declared `exported="false"` — there is no adb
  route to it. Locate the button rather than guessing coordinates:
  ```bash
  adb shell uiautomator dump /sdcard/ui.xml
  adb shell cat /sdcard/ui.xml | tr '>' '\n' | grep -i wifi_button   # read the bounds
  adb shell input tap <cx> <cy>
  ```
  This is a deliberate exception to §0's no-tapping rule: the tap **is** the thing under test, not
  run setup. Note it in Setup notes.
- **Test count changes: expect 245**, not 238 (238 + 5 in `DiscoveryModePolicyTest` + 2 more in
  `LinkLossTeardownPolicyTest`, which goes from 5 to 7).
- Standing rule unchanged: **never pre-check port 5277 with anything**, `nc` included. Verify it
  passively via `/proc/net/tcp6`.
- **Budget: 1-2 manual server restarts**, plus the usual one-time hotspot/server restart at the top
  that every round has needed. A run that needs none beyond that is itself the signal.
- **Three runs were added after this brief was first pushed** — R18, R19, R20 in §5. R20 costs no
  device time (it is a grep over the round's own captures) and R18 needs only a USB cable. If you
  have to cut something, cut R19 before either of them and say so.

## 4. The lines that decide every run

Verified with `grep -F` against `5f193d30`.

```
NetworkDiscovery: Starting scan...
NetworkDiscovery: Scan interrupted
NetworkDiscovery: Found Headunit Server on
Auto-connecting to Headunit Server at
NetworkDiscovery: waiting for an in-flight probe before scanning
NetworkDiscovery: in-flight scan promoted to continuous
One-shot scan finished.
Handshake: Version response received (ret=
CommManager: Connect already in progress; closing the handed-over socket
NetworkMonitor: a scan was already in flight; the next one will not wait
AapService: network changed during the last scan; rescanning immediately
AapService: link-loss teardown finished in
, but this session does not ride that link; leaving it alone
```

> The last one is a fragment on purpose. The full line starts with the trigger name, which is
> substituted at runtime (`WIFI_STATION_DISABLING, but this session does not ride…`). Grep the
> fragment, never a line reconstructed by hand.

**Two measurements settle R15 and R16.**

**(a) The invariant.** Every probe that finds the server must be handed over:

```bash
echo "found:      $(grep -ac 'NetworkDiscovery: Found Headunit Server on' log.txt)"
echo "handedover: $(grep -ac 'Auto-connecting to Headunit Server at'      log.txt)"
```

These must be **equal**. Any excess is a socket opened to port 5277 and abandoned, which is the
defect. The reporter's log is 13 vs 10.

> One legitimate exception: if a scan finds the server *after* a session is already up, the handover
> is refused and the socket closed deliberately, and that path logs nothing. If the counts differ by
> one and the extra `Found` sits after a successful `Version response received`, say so rather than
> calling it a FAIL — that is the `isConnected` guard doing its job.

**(b) The shape of one service start.** Per `Initializing WiFi Mode`, count:

| | `Starting scan...` | `Scan interrupted` |
|---|---|---|
| Broken (rounds 1-5 builds, and the reporter's log) | 2 | 1 |
| Fixed (this build) | **1** | **0** |

That is the crispest evidence in the round: the second `startDiscovery()` now returns early instead
of tearing down a healthy scan and starting a rival one.

`waiting for an in-flight probe before scanning` is the *other* guard — the class-level one — and
fires only when a scan starts while a previous instance's job is still running, i.e. after a mode
change. It is still a rarer path than R15 and R16 exercise, so its absence there means nothing.
**R19 provokes it deliberately, and there its absence is a FAIL** — on `2246e9a2` that line could
not fire on this path at all (§2a), which is the whole reason R19 exists.

## 5. Runs

### R0 — build gate

Build, install, `run_unit_tests.sh`. Expect **245/245**, `DiscoveryModePolicyTest.xml` with
`tests="5" failures="0" errors="0"` and `LinkLossTeardownPolicyTest.xml` with
`tests="7" failures="0" errors="0"`. Report the new md5.

### R15 — the double call, fully scripted (the point of the round)

Settings: `wifi-connection-mode=1`, `log-level=1`. Phone: head unit server running, restarted fresh
once at the top. Head unit and phone on the same network — the external AP is fine here, this run is
about the scan and not the topology.

Force-stop and relaunch **15 times**, ~20 s apart so each service start finishes its first scan:

```bash
for i in $(seq 1 15); do
  adb shell am force-stop com.andrerinas.headunitrevived
  adb shell am start -n com.andrerinas.headunitrevived/com.andrerinas.openheadunit.main.MainActivity
  sleep 20
done
```

**PASS** — counts equal per §4(a); one `Starting scan...` and zero `Scan interrupted` per
`Initializing WiFi Mode` per §4(b); no manual server restart needed across all 15.
**FAIL** — `Found` exceeds `Auto-connecting`, or the server needs a manual restart.

Report both raw counts and the launch count, not just the verdict.

### R15b — the same two counts on your retained broken-build captures (no device time)

Run §4(a)'s two greps over the round 4 and round 5 capture files. Those were `646441c4`, before this
fix. If `Found` exceeds `Auto-connecting` there, you have a same-hardware before/after and R15's
result means something concrete rather than being an absence.

Also count `Starting scan...` and `Scan interrupted` per `Initializing WiFi Mode` in those files.
Per §2 they should show the broken **2 and 1** shape, because `646441c4` contains `d0b8305a`.

If the `Found`/`Auto-connecting` counts happen to be equal there, that is worth knowing and **is not
a contradiction** — that race is probabilistic and neither round was trying to provoke it. The 2-and-1
shape, by contrast, is deterministic and should be there. Report both either way; do not spend time
chasing a discrepancy in the first number.

### R16 — the reporter's flow verbatim

Settings: `wifi-connection-mode=2`, `helper-connection-strategy=3`, `log-level=1`. **Phone hosts the
hotspot**, head unit joins it (`cmd wifi connect-network`), head unit server running on the phone —
the topology round 5 was already on (`192.168.41.x`). Strategy 3 is labelled "Phone Hotspot (Host)"
in the app, and this is exactly what the reporter does.

With the app already running, press the WiFi button **10 times**, ~20 s apart. Same two measurements
as R15.

If the phone's hotspot cannot be brought up this session, run R16 on the external AP and say so —
the scan logic does not depend on which AP it is, only the reporter's story does.

### R17 — Native AA regression check

`wifi-connection-mode=3`. Establish a session and confirm video. `startDiscovery()` must return
before constructing anything, so **`NetworkDiscovery: Starting scan...` must not appear at all**
while mode 3 is active. Any occurrence is a regression in the new `DiscoveryModePolicy` gate.

### R18 — a USB session must survive a WiFi toggle (the regression, §2a)

`wifi-connection-mode=1`, `log-level=1`. Connect the phone **over USB** and get a picture. Then, with
the session live:

```bash
adb shell svc wifi disable
sleep 20
adb shell svc wifi enable
```

**PASS** — the session keeps running, video included, and the log carries
`, but this session does not ride that link; leaving it alone`.
**FAIL** — the session ends. On `2246e9a2` and on round 4's and round 5's builds it does; the
teardown was deciding from the stored wireless mode rather than from the USB session in front of it.

Do this before R15 if the rig's WiFi radio is being difficult (§7a) — it is the one run here that
wants WiFi off, and a radio that will not come back is easier to deal with at the start.

### R19 — the class-level guard, provoked (the point of commit 7)

`wifi-connection-mode=1`, `log-level=1`, head unit server running. The guard fires when a scan starts
while a *previous instance's* job is still unwinding, which means a mode re-init landing on top of a
live sweep. Force it:

```bash
for i in $(seq 1 10); do
  adb shell am force-stop com.andrerinas.headunitrevived
  adb shell am start -n com.andrerinas.headunitrevived/com.andrerinas.openheadunit.main.MainActivity
  sleep 3          # deliberately short: catch the service while its first sweep is still running
  adb shell am force-stop com.andrerinas.headunitrevived
  adb shell am start -n com.andrerinas.headunitrevived/com.andrerinas.openheadunit.main.MainActivity
  sleep 12
done
```

**PASS** — `NetworkDiscovery: waiting for an in-flight probe before scanning` appears at least once,
and §4(a)'s two counts are still equal.
**FAIL** — the counts diverge.
**INCONCLUSIVE** — counts equal but the line never appears: the timing did not land, so widen the
`sleep 3` a little and try once more. Say which it was; do not record it as a PASS.

A force-stop is a process restart, so the *static* guard is fresh each time — what this really
exercises is `stopWirelessServer()` inside `initWifiMode`, which is the same shape and the same
code. If you can reach it without the force-stop (changing wireless mode in Settings back and forth
while a scan runs), that is a better provocation; use it if it is cheap.

### R20 — nothing hands over a socket that gets refused (commit 7)

No separate run. Over **every** capture in this round:

```bash
grep -c 'CommManager: Connect already in progress; closing the handed-over socket' log.txt
```

**PASS** — zero, everywhere. **FAIL** — any occurrence: discovery opened a socket to port 5277
during a connect in flight, and that connection was refused and closed, which is exactly what wedges
the server. Report the count per capture even when it is zero.

## 6. What not to spend time on

- **Anything shutdown-related.** R10 answered it: PASS, 273 ms, no failed send, server survived.
  Settled, along with R7's WiFi toggle.
- **Unwarned link losses** (out of range, AP restart, hard power cut). Nothing on this branch helps
  those and no run here tests them.
- **The WiFi radio not coming back after `svc wifi disable`.** Known rig quirk, §7a. Nudge and move
  on.
- **A high `Found` count before checking §4(a)'s exception.** A post-connection find is legitimate.

## 7. Report back

1. **R15: the two counts and the launch count.** This is the headline.
2. **R15: `Starting scan...` and `Scan interrupted` per service start** — the table in §4(b).
3. **R15b: the same two counts from the retained round 4 and round 5 captures.**
4. **R16: the same two counts**, and whether you used the phone's hotspot or the external AP.
5. **R17: did `NetworkDiscovery: Starting scan...` appear in mode 3?** Yes or no.
6. **R18: did the USB session survive the WiFi toggle**, and did the "does not ride that link" line
   appear? This is the regression check; answer it even if everything else fails.
7. **R19: did `waiting for an in-flight probe before scanning` appear**, and how many times.
8. **R20: the count of `Connect already in progress` per capture** — zero everywhere is the answer.
9. **How many manual server restarts the round cost**, and where.
10. Whether `in-flight scan promoted to continuous` or `network changed during the last scan;
   rescanning immediately` appeared anywhere — informational, not a verdict.
