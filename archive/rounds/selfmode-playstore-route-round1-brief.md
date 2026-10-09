# selfmode-playstore-route, round 1 brief

## 1. Build and baseline

**Candidate:** `fork/fix/head-unit-server-silence-and-log-attribution` @ `58802778`, candidate-only.
**Two APKs from one tree**, both flavors:

```bash
git fetch fork fix/head-unit-server-silence-and-log-attribution
git checkout -B fix/head-unit-server-silence-and-log-attribution fork/fix/head-unit-server-silence-and-log-attribution
git log --oneline -4
# 58802778 Silent head unit server: name the cause the APK shows, not the one we assumed
# 378d8d4c Self Mode: a duplicate launch is not a second launch, and loopback needs no network
# ad070f2f Logging: make the exported file usable on Android 13+, and say which build wrote it
# d1fef63a Merge pull request #899 ... (= current main)
```

**History was rewritten twice on 2026-08-27 and may move again before you build.** Identify the
branch by those three subjects and the count, not by the SHA, and **record the SHA you actually
built** in Setup notes. The rebases folded `fix/log-export-anr-and-self-mode-double-launch` into the
first two commits rather than stacking on it, so its three commits from the `log-and-selfmode-fixes`
round are here in regrouped form. That work is verified and is not under test again (§7).

No baseline APK. Nothing in this round is an A/B against `main`; the comparison is between two
configurations of the same candidate, and between two flavors of it.

**`playstoreDebug` has never been compiled by anyone.** CI builds `github` only and the workflow
comment saying the flavor cannot compile is stale: `app/src/playstore/.../VpnControl.kt` exists and
matches the github copy member for member. R0 is therefore the first build of the flavor the Play
Store actually ships. If it fails, that is a real finding, reported verbatim, and the round carries
on with the github APK.

## 2. What this is and why it exists

Issue #897 is a Self Mode failure on Android Auto 17.5.663204. The reporter's phone accepts our TCP
connection on `127.0.0.1:5277` and then sends nothing at all, three times over six seconds, on a
freshly force-stopped Android Auto with a freshly started head unit server. Our side is measured
clean: the connection succeeds, we write the version request, and every read times out rather than
failing.

Taking the 17.5 APK apart explains the silence. `DeveloperHeadUnitNetworkService` is a **proxy, not
an AAP server**. On accept it logs `Head unit connected`, makes a socket pair, starts both pump
threads, and calls `startDuplexConnection` into its own `:car` process over a synchronous binder
transaction. There it fires a `START_DUPLEX` activity carrying the file descriptor and waits **with
no timeout** until its setup state machine completes. Until that happens our bytes sit in the socket
pair and nothing comes back. That is the reported signature exactly.

What the teardown does not say is why that wait ends on our devices and not on the reporter's. The
log answers it in one line, printed on all three of their attempts:

```
HomeFragment.startSelfMode | Device is offline and VPN is not available in this build. Self Mode may fail.
```

That line needs **two** conditions at once (`HomeFragment.kt:211`): `activeNetwork == null` **and**
`VpnControl.isVpnAvailable() == false`. So it says the reporter was **offline** and on the **Play
Store build**, and those two facts interact:

- `HomeFragment.startSelfMode()` raises the dummy VPN only when the device is offline.
- The Play Store flavor has no dummy VPN to raise (`app/src/playstore/.../VpnControl.kt:30`), because
  Google does not allow the fake VPN in a Play listing.

So an **offline Play Store device is the one configuration in which Self Mode runs with no network of
any kind**, and it is a configuration nobody has ever tested, because the flavor has never been built. The
same phone on the same Android Auto version works on the github build: `log-and-selfmode-fixes` round
1, R3, one session held ~110 s at 52-56 fps on D-POCO.

**The hypothesis this round tests: Android Auto's own setup state machine cannot complete with no
active network, the dummy VPN is what supplies one, and the Play Store build therefore cannot do
Self Mode offline.** A tablet in a car with no SIM and no WiFi is the normal Self Mode setup, so if
this holds it is not an edge case.

## 3. What is different about this round

### The entry point is the experiment

This is the whole design, and a run that uses the wrong one silently tests nothing. Two ways to start
Self Mode reach different code:

| Entry point | Raises the dummy VPN? | Why |
|---|---|---|
| `auto-start-self-mode=true` + launch `MainActivity`, **no intent** | **yes** | reaches `HomeFragment.startSelfMode()` through the auto-connect order (`HomeFragment.kt:162-172`) |
| `am start -a com.andrerinas.openheadunit.ACTION_START_SELF_MODE` | **no** | `AutomationActivity` forwards straight to `AapService`, never touching `HomeFragment` |

`SelfLauncherManager.adoptDummyVpn()` only *adopts* a VPN that `HomeFragment` already started; it
never starts one. So the intent route is genuinely VPN-free on the github build, which makes R1 and
R2 a one-variable A/B **on one APK**. `post-beta1-self-mode` round 1 already recorded the auto-start
route raising the VPN on offline devices, so the positive arm is known reachable.

Both runs still launch `MainActivity`, so that the only difference between them is which path started
Self Mode.

### VPN consent has to be pre-granted, or R1 and R7 stall on a dialog

`VpnControl.consentIntent()` returns a non-null `Intent` until the user has approved the VPN once,
and nothing scripted can answer that system dialog. Before those runs:

```bash
adb -s <dev> shell appops set com.andrerinas.headunitrevived ACTIVATE_VPN allow
adb -s <dev> shell appops get com.andrerinas.headunitrevived ACTIVATE_VPN     # expect: allow
```

If it will not take, R1/R7 are **UNTESTABLE**, not FAIL, and R2/R3 lose their comparison.

### Gearhead's own log is the point of the capture

Ours says the peer went silent; only Android Auto's log says where it stopped. Three strings are
confirmed present in the `17.5.663204` dex, and **which is the last to appear names the stalled
stage**:

| Line | Where | Reading if it is the last one |
|---|---|---|
| `Network server running on port %d` | the accept loop, tag `GH.DHUService` | absent: the server never bound |
| `Head unit connected` | `DeveloperHeadUnitNetworkService.a(Socket)` | the socket was accepted and handed on |
| `startDuplexConnection` | the `:car` process, tag `CAR.SERVICE` | the setup state machine never finished, the expected case |

Their log **level** is not provable from the dex, because Gearhead logs through a wrapper. So:
capture unfiltered, raise the tags first as insurance, and **self-check**. If
`Network server running on port` is absent while `:5277` is demonstrably listening, the tags are
being filtered and the run is repeated with the props set, not reported as absence.

```bash
adb -s <dev> shell setprop log.tag.GH.DHUService VERBOSE
adb -s <dev> shell setprop log.tag.CAR.SERVICE VERBOSE
adb -s <dev> shell setprop log.tag.GH.GhCarClientCtor VERBOSE
```

### Android Auto must not be force-stopped, and the phones must not be rebooted

Force-stopping Gearhead kills `DeveloperHeadUnitNetworkService`, and only a manual tap on "Start head
unit server" in Android Auto's developer settings brings it back, which cannot be scripted and the
round is unattended. `KILL_GH=0` everywhere. Check `:5277` before each phone run:

```bash
adb -s <dev> shell "cat /proc/net/tcp6 /proc/net/tcp | grep -i ':1495'"    # 0x1495 = 5277
```

Not listening means those runs are **UNTESTABLE**, not FAIL.

### D-MOTO gets Android Auto 17.5 first

D-MOTO is on `17.3.662854`, which takes the legacy 5288 route and cannot answer this round's
question. Sideload `17.5.663204` (md5 `ba7edcf5afc1e72c1455341ede13a683`) with `adb install -r`; the
signature matches the Play copy so it is an ordinary upgrade. If that build cannot be obtained, take
whatever Play offers and record the version. **Any 17.4+ version satisfies the round**, and the log
line `SelfMode: Installed AA version:` states what it decided. D-HU stays on 17.3 deliberately, so
the rig keeps its legacy-route coverage.

### `main` changed the failure path the night before this round

`d1fef63a` (PR #899) rewrote `SelfLaunchResolveHelper` and `PermissionTrampolineActivity`. Every
failing run below reaches it, because `handleNeverConnect()` runs it after the launch deadline. It is
**not under test and not ours**, and its output must not be read as a defect in the candidate:

- A sub-second return from Android Auto's permission activity now means "permissions were already
  granted" and routes to the other-causes checks.
- Two new lines, replacing ones earlier briefs quoted:
  `SelfMode: AA permissions were already granted or activity failed to start; checking other causes.`
  (W) and `SelfMode: AA's permission screen was shown for missing permissions.` (I).
- **Its verdict is a toast with no log line at all.** On a phone where Gearhead is not a system app,
  every Self Mode timeout now shows "Android Auto is not a system app" whatever the real cause was.
  If a failing run is worth a screenshot, this is why.

### Two more things that will look like defects and are not

- **The dummy VPN watchdog prints the bare word `SelfMode` and nothing else.**
  `SelfLauncherManager.kt:215` calls `AppLog.w("SelfMode", "AapService: Self Mode brought the dummy
  VPN up ...")`, and there is no two-argument overload, so it resolves to the varargs one and
  `String.format` discards the message. Grep for a line ending `| SelfMode`, not for the text.
- **`ret=0` is a read timeout, not EOF.** `SocketProjectionConnection.recvBlocking` maps EOF and
  `IOException` to `-1`, so a silent-peer episode shows as timeouts with the socket still open.

### Pre-registered as not a failure

- `playstoreDebug` failing to compile makes R3-R6 and R8 **UNTESTABLE**. R1 and R2 are the point of
  the round and need only the github APK.
- `:5277` not listening on a phone makes that device's runs **UNTESTABLE**.
- R8 is last on purpose: it needs D-HU's WiFi off, and §7a records that `svc wifi enable` does not
  reliably bring the station radio back. If it will not restore, say so and stop there. **R8 is the
  run to drop if the round runs long.**

## 4. Settings keys this round needs

Written into `shared_prefs/settings.xml` with the app stopped, per template §1.

| Key | Type | Value | Runs |
|---|---|---|---|
| `log-level` | int | `<int name="log-level" value="2" />` | all |
| `auto-start-self-mode` | boolean | `<boolean name="auto-start-self-mode" value="true" />` | R1, R3, R4, R5, R6, R7, R8 |
| `auto-start-self-mode` | (none) | **delete the element** | R2 |
| `auto-connect-last-session` | (none) | **delete the element** | all |
| `auto-connect-single-usb` | (none) | **delete the element** | all |
| `auto-connect-delay-seconds` | int | `<int name="auto-connect-delay-seconds" value="0" />` | all |
| `log-capture-enabled` | boolean | `<boolean name="log-capture-enabled" value="true" />` | all |

`log-source` must stay `0` or absent. Diff `settings.xml` against a fresh backup at round start and
state the delta in Setup notes. Both flavors share `applicationId` and the debug key, so
`adb install -r` swaps them in place and `settings.xml` survives. Confirm with `run-as cat` either
side of every swap, and confirm the live md5 before every run.

## 5. The lines that decide every run

All verified with `grep -F` against `58802778`.

**Which flavor is live**, one of these per run:

```
VpnControl: Starting DummyVpnService (GitHub Build, excludeSelf=
VpnControl: no dummy VPN in the Play Store build; ignoring the request
```

Also `LogExporter: session |` in the exported `HUR_Log_*.txt`, which names build and flavor
directly. Quote it for every run; it is the record of which APK produced the capture.

**Whether the device was offline, and whether the VPN came up:**

```
Device is offline. Preparing Dummy VPN for Self Mode.                                   (github, offline)
Device is offline and VPN is not available in this build. Self Mode may fail.           (playstore, offline)
VPN permission already granted. Starting VPN service.
DummyVpnService: tun established (excludeSelf=
```

Neither offline line printing means the device had an active network. Corroborate the tun with
`adb shell ip link show tun0`.

**Route and launch:**

```
SelfMode: Installed AA version:
SelfMode: AA 17.4+ detected. Connecting directly to Headunit Server on 127.0.0.1:5277...
SelfMode: AA < 17.4 detected. Starting WirelessServer on 5288 and running legacy triggers...
Auto start selfmode
Socket low-latency options: tcpNoDelay=          <- the TCP connect succeeded
```

**Success:**

```
SSL handshake complete
Throughput over 5000ms: rendered=
```

**The #897 signature, all three together:**

```
Socket low-latency options: tcpNoDelay=                                                  present
Handshake: the peer accepted the connection and then sent nothing at all.                present (E)
SelfMode: nothing connected within 10000ms of the launch                                 present (E)
```

`SelfMode: Headunit Server (127.0.0.1:5277) is NOT running.` is a **different** failure: the connect
was refused, not answered with silence. If that appears, the server was not up and the run is
UNTESTABLE.

After three silent episodes on the same endpoint:

```
CommManager: 127.0.0.1:5277 has accepted 3 connections in a row without answering any of them.
```

**Gearhead's side** (§3): `Network server running on port`, `Head unit connected`,
`startDuplexConnection`.

**From `main`, expected on any failing run, not a defect:**

```
SelfMode: AA permissions request took
SelfMode: AA permissions were already granted or activity failed to start; checking other causes.
SelfMode: AA's permission screen was shown for missing permissions.
```

## 6. Runs

Every run: unfiltered `stdbuf -oL adb -s <dev> logcat -v threadtime > rN.txt` started **before** the
launch, held 90 s after the launch, and the app's `HUR_Log_*.txt` pulled afterwards. `grep -a`
throughout.

### R0: build both flavors + unit tests (gate)

```bash
taskset -c 0,2,4,6 hur-wifi-test-scripts/build_hur.sh          # githubDebug
# copy the APK out of apks/ IMMEDIATELY - build_hur.sh rm's it before the next build
./gradlew :app:assemblePlaystoreDebug                          # first build ever
./gradlew :app:testGithubDebugUnitTest
```

- **PASS**: `assembleGithubDebug` clean, **`assemblePlaystoreDebug` clean**, `testGithubDebugUnitTest`
  green at **780 tests** (last round's 775 plus `LoopbackBindPolicyTest`, 5 cases). Record both md5s;
  they must differ.
- **PARTIAL**: github builds, playstore does not. **Quote the compiler output verbatim**, which is the
  most valuable thing this round can return, and continue with R1 and R2 only.
- **FAIL**: github does not build, or the tests are not green. Stops the round.

### R1: github, offline, VPN raised (D-POCO). The working arm.

Setup: install the **github** APK, verify md5. `appops set ... ACTIVATE_VPN allow`. Write
`auto-start-self-mode=true`, `log-level=2`, `log-capture-enabled=true`; delete
`auto-connect-last-session` and `auto-connect-single-usb`. Precheck `:5277` is listening.
`cmd connectivity airplane-mode enable`, verify **both** radios down with `dumpsys` (never
`settings get global`). Start the capture, launch `MainActivity`, send **no** intent. Hold 90 s.

- **PASS**: `Device is offline. Preparing Dummy VPN for Self Mode.` and `tun established`, then
  `AA 17.4+ detected`, then `SSL handshake complete` and throughput for ≥ 60 s.
- **FAIL**: no session. If the failure carries the #897 signature, the hypothesis is already in
  trouble and R2 is still worth running.

### R2: github, offline, no VPN (D-POCO). **THE POINT OF THE ROUND.**

Identical to R1 in every respect except: **delete** `auto-start-self-mode`, and 5 s after launching
`MainActivity` send

```bash
adb -s 4f4027e9 shell am start -a com.andrerinas.openheadunit.ACTION_START_SELF_MODE
```

- **PASS (hypothesis confirmed)**: no `Preparing Dummy VPN` line and no `tun established`, the TCP
  connect succeeds (`Socket low-latency options:`), and the run reproduces the #897 signature: the
  peer-silent line and `nothing connected within 10000ms`. Report which Gearhead line was the last
  to appear.
- **PASS (hypothesis refuted)**: a session forms and holds without the VPN. Equally a result; say so
  plainly, because it moves the cause into the playstore source set and R3 becomes the point.
- The two outcomes are both PASS. What would make this **FAIL** is not being able to tell them
  apart: no `Socket low-latency options:` line at all, meaning the connect never happened.

**Pair the verdict with a number:** the wall-clock gap between `Socket low-latency options:` and the
peer-silent line, and the count of `Head unit connected` in Gearhead's lines. A silence with zero
`Head unit connected` is a different fault from a silence with one.

### R3: playstore, offline, auto-start (D-POCO). The reporter's exact configuration.

Install the **playstore** APK (`adb install -r`, confirm md5 and that `settings.xml` survived).
Otherwise identical to R1: auto-start entry, airplane mode on.

- **PASS**: `VpnControl: no dummy VPN in the Play Store build` (or its absence with
  `Device is offline and VPN is not available in this build`), and the run's outcome recorded either
  way. **Expected to match R2**, whichever way R2 went.
- **FAIL**: the capture cannot be attributed to a flavor: no `LogExporter: session |` banner and no
  VPN line.

### R4: playstore, online (D-POCO). Is a network enough?

`cmd connectivity airplane-mode disable`, then nudge and verify **both** radios
(`svc wifi enable`, `svc bluetooth enable`, `dumpsys`). Confirm a validated default route exists
(`adb shell ip -4 route`). Auto-start entry.

- **PASS**: neither offline line appears, and a session forms and holds ≥ 60 s.
- **FAIL**: it fails online too, in which case the network is not the variable and R5 is pointless;
  skip it and say why.

### R5: playstore, joined but with no internet (D-POCO). Is *any* network enough?

Bring up a soft AP on D-HU and join it from the phone with no route to the internet:

```bash
adb -s 27870808938846 shell cmd wifi start-softap OHU-TEST wpa2 testtest1234 -b 5
adb -s 4f4027e9 shell cmd connectivity airplane-mode enable
adb -s 4f4027e9 shell svc wifi enable
adb -s 4f4027e9 shell cmd wifi connect-network "OHU-TEST" wpa2 "testtest1234"
adb -s 4f4027e9 shell ip -4 route                       # a default route via wlan0 is the check
```

Airplane mode first, then WiFi on, so the unvalidated WiFi is the only candidate for the default
network. Otherwise Android keeps cellular as the default and the run tests nothing. Auto-start entry.

- **PASS**: neither offline line appears (proving `activeNetwork` was non-null), and the run's
  outcome is recorded. A session forming means any network suffices; a failure means Android Auto
  wants real connectivity.
- **INCONCLUSIVE**: an offline line still prints, meaning the join did not become the default
  network. Report the `ip route` output.
- Stop the soft AP afterwards (`cmd wifi stop-softap`).

### R6: playstore, offline, auto-start (D-MOTO). Does the failure generalise?

Upgrade Gearhead to 17.5 first (§3) and record the version the log reports. Otherwise identical to R3.

- **PASS**: the run reproduces R3's outcome, whatever it was.
- **UNTESTABLE**: `SelfMode: AA < 17.4 detected` still appears, meaning the upgrade did not take.

### R7: github, offline, auto-start (D-MOTO). Does the fix generalise?

Identical to R1 on D-MOTO.

- **PASS**: `tun established`, then a session that holds ≥ 60 s.

### R8: playstore, offline, legacy route (D-HU). Run last. Droppable.

What a Play Store user gets on older Android Auto with no VPN available. D-HU keeps 17.3, so this is
the 5288 route. `svc wifi disable` on D-HU for the offline condition, auto-start entry.

- **PASS**: the run's outcome is recorded either way, with `AA < 17.4 detected`,
  `Wireless Server listening on port 5288`, and whether `SelfMode: nothing connected within 30000ms`
  fires (note the **30000**, which is how you tell the legacy deadline from the 17.4+ one).
- **Restore D-HU's WiFi afterwards and verify it.** If it will not come back, that is the documented
  §7a hazard, not a finding: say so and stop.

## 7. Do not re-run

- **Anything the `log-and-selfmode-fixes` round settled.** All three of its commits are in this
  candidate in regrouped form and were verified on all three devices: no `LogAccessDialog`, the
  export does not ANR, the 17.4+ double launch is coalesced, the legacy happy path and re-arm are
  intact. Regrouping changed no code.
- **github + online Self Mode on D-POCO.** Settled by that round's R3: one session, ~110 s, 52-56
  fps, on this exact Gearhead build. That is why this round has no such cell.
- **The head-unit-server wedge mechanism.** Settled from the APK teardown; the round measures where
  it stalls on these devices, not whether the proxy works that way.
- **The loopback bind fix.** It is in the candidate and removes a 1.5 s wait per attempt. Nothing here
  measures it; it just means the timings below are not inflated by it.

## 8. Report back

1. **Did `assemblePlaystoreDebug` compile?** Yes/no, both md5s, and the verbatim error if not. This
   is the first ever attempt and it decides whether the flavor can be put in CI.
2. **R1 against R2, one line each.** Did the github build form a session offline *with* the VPN, and
   what happened *without* it. This is the answer the round exists for.
3. **R3 against R4 against R5.** Does the Play Store build work offline, online, and on a network
   with no internet. Three yes/no answers decide what we tell every Play Store user.
4. **For every failing run: which Gearhead line was the last to appear**, and the count of
   `Head unit connected`.
5. **R6 and R7**, one line each: does whatever R2/R3 showed hold on a second device.

Also, briefly: the Gearhead version on each device after any upgrade, whether the log tags needed
`setprop` to appear at all, whether `appops ... ACTIVATE_VPN allow` took on each device, and anything
in the captures this brief did not ask about.
