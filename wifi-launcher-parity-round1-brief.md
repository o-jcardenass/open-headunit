# wifi-launcher-parity, round 1 brief: what the WiFi refactor stopped doing

**Three builds this round, and that is the point of it.** Upstream rewrote the whole wireless
bring-up path for 3.3.0. The dispatch survived the rewrite; a set of behaviours that lived in
`AapService` and had no obvious home in the new launcher objects did not. This round measures each
one on the refactor, on the code it replaced, and on a fix, so that a failure can be attributed to
the branch rather than to this rig.

| Arm | Build | SHA | Role |
|---|---|---|---|
| **A** | `origin/3.3.0-alpha` | `e8fe4611` | the refactor as upstream has it. Every run below is expected to FAIL here. |
| **B** | `origin/main` | `048f4eaf` | the code it replaced. A FAIL here is a **rig finding**, and voids that run in A and C. |
| **C** | `fork/fix/wifi-launcher-parity` | `63a3f699` | the candidate, four commits on top of A. Expected to match B. |

```bash
git fetch origin && git fetch fork
git rev-parse origin/3.3.0-alpha            # e8fe4611afeaeb1afe68861f52c458ef96b2a43f
git rev-parse 048f4eaf                      # 048f4eaf663cbb9d9589ebd9c4233d4a262759a3
git rev-parse fork/fix/wifi-launcher-parity  # 63a3f699cda5b89349df9aa05563821c2f388440
```

`origin/3.3.0-beta1` is the same commit as `origin/3.3.0-alpha`; either name gets arm A.
No history was rewritten. A plain `checkout` is enough for all three.

**Run the arms in the order A, B, C, and batch by build, not by run.** All three carry
`applicationId com.andrerinas.headunitrevived`, so they cannot be installed beside each other and
every arm is a swap. Install once, do every run for that arm, then swap. Three installs, not
twenty-seven.

---

## 1. Why this round exists

`AapService.initWifiMode()` was ~110 lines of `if (mode == 1 || mode == 2 || mode == 3)` and
`when (strategy)`. 3.3.0 replaces it with a `WifiLauncher` per mode, a `WifiLauncherManager` that
swaps them, and a `WifiLauncherSharedServices` that starts and stops the WiFi Direct manager, the
5288 server and the discovery loop from three predicates each launcher answers. `AapService` loses
740 lines.

Read as a translation it is faithful: every mode and strategy still has a branch, the stored ints
and preference keys are unchanged, and both legacy migrations survive. The failures are all in the
same place, and all of the same kind: work that used to happen in `AapService` around the dispatch,
which the new objects have no slot for.

Three shapes recur, and they are worth knowing before reading the runs.

**Something is asked of `active` after `active` has been nulled.** `WifiLauncherManager.stop()`
sets `active = null`. Two decisions in `onDisconnected` and the Bluetooth auto-start re-arm all read
`active` afterwards, so they can only answer no. The re-arm is the worst of the three: it exists to
revive a Native AA mode that a user exit stopped, and it tests `active is WifiLauncherNative`, which
is null in exactly that state.

**Something was gated on a strategy that both strategies need.** The soft-AP credentials listener
and the 5288 server were both wired inside the WiFi Direct arm of the Native launcher. On the head
unit hotspot transport, the provider resolves the access point, publishes to nobody, and stops
looking.

**Something had no home, so it was dropped.** The soft-AP half of the user-exit teardown is gone
outright: `UserExitHotspotPolicy`, `HotspotManager.restart` and `hotspotTeardownProvenUnsafe` have
no caller in `app/src/main` on arm A. So do `DiscoveryModePolicy` and
`UsbSessionQuiescePolicy.shouldStopWifiDirectGroup`. Their unit tests all still pass.

### The honest limit, stated first

This was found by reading, not by running. Nothing below has been reproduced on hardware, and two
of the seven have a plausible reason to be less severe in practice than the source suggests:
R7's defect is repaired lazily by a path that does work, and R4 needs a configuration most users do
not have. **Report what the lines say, per arm.** A run that does not reproduce on arm A is a real
result and the most useful thing this round can produce.

---

## 2. What is different about this round

- **Arm B is not optional and is not a formality.** Every run's verdict is a triple. `A=FAIL,
  B=PASS, C=PASS` is a confirmed defect and a working fix. `A=FAIL, B=FAIL` is this rig, and that
  run says nothing about either branch however C comes out. `A=PASS` means the reading was wrong,
  which is worth more than the rest of the round.
- **R5 is pre-registered UNTESTABLE**, and its first step is the 30-second check that proves it.
  §7a records this rig's Android Auto at `17.5.663204-release`, and the code path R5 targets is
  behind `if (major < 17 || (major == 17 && minor < 4))`. Do the check, record the version, mark it
  UNTESTABLE and move on. Do not spend the round trying to downgrade Android Auto.
- **R4 needs a tap that cannot be scripted.** The dummy VPN only starts when this app is already the
  prepared VPN app, and that consent is a system dialog. The `session-vpn-lever` thread has had it
  granted on this rig before; if it has been revoked since and cannot be re-granted, R4 is
  **UNTESTABLE** and that is a result.
- **Every APK swap wipes settings, and this rig's `shared_prefs` is root-owned**, so the app's own
  writes never reach disk and a setting that looks set can be neither. After every install, write
  `settings.xml` with the app stopped and **read all of §3's keys back** before the first run of
  that arm counts. A run started on an unverified config is discarded, not reported.
- Nothing here needs a poke to connect, a head-unit-side WiFi scan, or the head unit's Bluetooth
  switched off. All three are known-broken or impossible on this rig per §7a.
- **Bring the head unit up before the phone** on every run, and reuse a live A2DP link once you
  have one.

---

## 3. Settings keys this round needs

Write these with the app stopped, then read every one back. `log-level` and the two mode keys are
needed by every run; the rest are per-run and named in the run.

| Key | Type | Element | Used by |
|---|---|---|---|
| `log-level` | int | `<int name="log-level" value="1" />` | all. DEBUG, see below |
| `wifi-connection-mode` | int | `<int name="wifi-connection-mode" value="3" />` | R1, R2, R7 (Native) |
| | | `<int name="wifi-connection-mode" value="2" />` | R3, R6, R8 (Helper) |
| `native-ap-transport` | int | `<int name="native-ap-transport" value="1" />` | R1, R7 (hotspot) |
| | | `<int name="native-ap-transport" value="0" />` | R2 (WiFi Direct) |
| `helper-connection-strategy` | int | `<int name="helper-connection-strategy" value="4" />` | R3 (head unit hotspot) |
| | | `<int name="helper-connection-strategy" value="2" />` | R6, R8 (Nearby) |
| `auto-enable-hotspot` | bool | `<boolean name="auto-enable-hotspot" value="true" />` | R3 |
| `keep-dummy-vpn-during-session` | bool | `<boolean name="keep-dummy-vpn-during-session" value="true" />` | R4 |

**DEBUG, not VERBOSE.** Every line this round reads is `AppLog.i`, `AppLog.w` or `AppLog.d`; none is
behind `if (AppLog.LOG_VERBOSE)`. The one `AppLog.d` that decides a run is
`AapService: Starting the wireless server on 5288`, which DEBUG carries. VERBOSE would only bring
this unit's driver flood closer to wrapping the ring buffer inside a run.

`native-ap-transport` is the key name on all three arms. Arm A and C read it into an enum and arm B
reads it as an int, but the key, the values and the meaning are identical, so one element works for
every arm.

---

## 4. Driving the round

Everything here is scriptable. No run's verdict depends on a screen being looked at.

```bash
PKG=com.andrerinas.headunitrevived
# a user exit, which is what R2 and R3 need. commManager.disconnect() defaults isUserExit=true.
adb shell am start -a android.intent.action.VIEW -d "headunit://disconnect"
# connect back
adb shell am start -a android.intent.action.VIEW -d "headunit://connect"
# R6: connect to a Nearby endpoint by id, exactly as tapping the list does
adb shell am startservice -n $PKG/com.andrerinas.openheadunit.aap.AapService \
  -a com.andrerinas.openheadunit.ACTION_NEARBY_CONNECT --es extra_endpoint_id "<ID>"
# R4: a mode change, which is what runs stopWirelessServer()
adb shell am startservice -n $PKG/com.andrerinas.openheadunit.aap.AapService \
  -a com.andrerinas.openheadunit.ACTION_START_WIRELESS
```

---

## 5. The lines that decide every run

Verbatim from the source at each SHA. Grep with `grep -F`.

```
SoftApCredentials: SUCCESS - Providing credentials from        the AP was resolved
AapService: Received WiFi credentials from manager             the listener got them
NativeAA: Credentials updated. SSID=                           the handshake got them
NativeAA: Attempting active poke to device:                    the phone is being woken
NativeAA: Successfully poked                                   the wake landed

AapService: Bluetooth auto-start — Native AA handshake manager was stopped, re-arming.
MATCH! Starting AapService                                     the ACL_CONNECTED arrived
Handshake: SSL handshake complete                              a session formed

AapService: CommManager teardown complete. Restarting the hotspot so the phone leaves the network.
AapService: Stopping the connection does not switch this device's hotspot
AapService: This device did not bring its access point back after

AapService: releasing the dummy VPN (owner=                    the owning teardown
VpnControl: Stopping DummyVpnService (GitHub Build)            the VPN actually went down

SelfMode: Installed AA version:                                R5's gate
SelfMode: AA < 17.4 detected. Starting WirelessServer on 5288
SelfMode: AA 17.4+ detected. Connecting directly to Headunit Server

NearbyManager: Endpoint FOUND:                                 the id R6 uses
NearbyManager: Requesting connection to endpoint:
NearbyManager: Failed to request connection:
NearbyManager: Stopping discovery and disconnecting

WirelessServer: binding port 5288...                           a bind was attempted
WirelessServer: Incoming connection detected from              the phone arrived
AapService: Starting the wireless server on 5288 -             a start was decided
AapService: the Bluetooth handshake found port 5288 unbound.   the late repair
AapService: port 5288 is bound now.

WifiDirectManager: Standard createGroup SUCCESS!               a P2P group was created
NativeAA: ACTIVELY LISTENING                                   RFCOMM listeners open
Throughput over 5000ms:                                        R8's reachability number
```

Two lines exist **only on arm C**, and are how you confirm the candidate is the build installed:

```
AapService: Nearby is not the running transport — arming it before connecting.
NetworkMonitor: no discovery loop to kick
```

Three lines exist on arms **B and C but not on arm A**, which is the R3 defect stated as a grep:

```
AapService: CommManager teardown complete. Restarting the hotspot so the phone leaves the network.
AapService: Stopping the connection does not switch this device's hotspot
AapService: This device did not bring its access point back after
```

Every other line in this list is present on all three, which is what makes the triple readable.
All of them were checked with `grep -F` against each of the three trees while this brief was
written; `VpnControl:` lines live in `app/src/github/`, not `app/src/main/`, if you go looking.

---

## 6. Runs

**R1 is the point of the round.** R8 is what makes the rest of it attributable: if R8 fails on all
three arms, nothing else in this round can be read.

### R0 — build gate, every arm

Compile and run the unit suite at all three SHAs. Record the counts. Arm C is a **first-ever
compile**, so a failure here is real and stops the round; report it and escalate.

**PASS**: three clean builds, three suites green. Report each count. On arm C, name the counts for
`WifiLauncherCapabilityTest`, `UserExitHotspotPolicyTest`, `UsbSessionQuiescePolicyTest` and
`LinkLossTeardownPolicyTest` specifically — all four gained cases.

### R1 — Native AA over the head unit hotspot never gets its credentials

`wifi-connection-mode=3`, `native-ap-transport=1`, `auto-enable-hotspot=true`. Bring the head unit's
hotspot up first, launch the app, leave it for three minutes with the phone paired and in range.

**PASS**: `SUCCESS - Providing credentials from` is followed by
`Received WiFi credentials from manager` **and** `NativeAA: Credentials updated. SSID=` **and**
`Attempting active poke to device`.

**FAIL**: `SUCCESS - Providing credentials from` appears and none of the other three ever does.

**Pair the count with reachability.** If `SUCCESS - Providing credentials from` never appears at
all, the access point was not resolved and the run is **INCONCLUSIVE**, not a FAIL: the defect is
downstream of that line and was never reached. Say which, and quote the last
`SoftApCredentials:` line in the capture.

**Positive control, one key, no second build.** On whichever arm passes, set
`native-ap-transport=0` and re-run: the WiFi Direct transport should pass on every arm. That is the
cheapest proof that the difference is the transport and not the rig's hotspot.

### R2 — after a Native AA user exit, Bluetooth cannot re-arm it

`wifi-connection-mode=3`, `native-ap-transport=0`. Form a session. Then
`headunit://disconnect`, wait for the disconnect to settle, and leave the app running while the
phone's Bluetooth reconnects on its own (§7a: this happens within seconds on this rig; do not poke).

**PASS**: `MATCH! Starting AapService` is followed by
`Bluetooth auto-start — Native AA handshake manager was stopped, re-arming.` and a second
`Handshake: SSL handshake complete`.

**FAIL**: `MATCH! Starting AapService` appears and the re-arm line never does.

**Reachability**: if `MATCH! Starting AapService` never appears, the phone never came back over
Bluetooth and the run is INCONCLUSIVE. Count them: `grep -c "MATCH! Starting AapService"`.

**Second thing to record, free from the same capture.** The ordering half of the same fix. On arm C
`awaitDisconnectComplete` runs before the launcher stops, so the exit line should appear *after*
CommManager has finished. Report the gap between the disconnect deep link and that line on each arm;
no PASS condition rides on it. **The line is worded differently on arm B**, so grep for both:

```
AapService: Native AA user exit. Stopping active launcher.     arms A and C
AapService: Native AA user exit. Stopping handshake manager.   arm B
```

### R3 — a user exit leaves the head unit hotspot up

`wifi-connection-mode=2`, `helper-connection-strategy=4`, `auto-enable-hotspot=true`. Let the app
bring the hotspot up, form a session over it, then `headunit://disconnect`.

**PASS**: `Restarting the hotspot so the phone leaves the network` appears, and the access point
cycles (confirm with `dumpsys wifi | grep -i "ap state"` or `cmd wifi` before and after). A
`This device did not bring its access point back after` line is **also a PASS** for this run: the
decision was taken and the radio refused, which is the learn-once branch working.

**FAIL**: neither line appears and the access point is still up sixty seconds after the exit.

**Reachability**: `auto-enable-hotspot=true` is what makes the DISABLE branch the one taken. If the
hotspot never came up, there is nothing to take down; INCONCLUSIVE.

### R4 — a mode change takes the user's VPN down

`wifi-connection-mode=3`, `native-ap-transport=0`, `keep-dummy-vpn-during-session=true`.

First: confirm this app is the prepared VPN app. If it is not and consent cannot be granted, mark
R4 **UNTESTABLE** and go on.

Form a session, confirm `tun0` exists, then fire `ACTION_START_WIRELESS`.

**PASS**: `tun0` still exists afterwards, and `VpnControl: Stopping DummyVpnService` does not appear.

**FAIL**: `VpnControl: Stopping DummyVpnService (GitHub Build)` appears with **no**
`AapService: releasing the dummy VPN (owner=` line before it, and `tun0` is gone. The missing
release line is the whole signal: it says the teardown that took the VPN down did not own it.

### R5 — Self Mode arms the wrong thing (pre-registered UNTESTABLE)

Run this first, it costs thirty seconds:

```bash
adb shell dumpsys package com.google.android.projection.gearhead | grep versionName
```

If the major/minor is 17.4 or later, the code path this run targets is unreachable: Self Mode takes
the `AA 17.4+ detected. Connecting directly to Headunit Server` branch and never touches the
launcher. Record the version, mark R5 **UNTESTABLE**, and move on. §7a already records
`17.5.663204-release`, so this is the expected outcome; the check exists so the round states a rig
fact rather than assuming one.

If it somehow is below 17.4: enable Self Mode with `native-ap-transport=1` and check whether
`SelfMode: AA < 17.4 detected` is followed by `WirelessServer: binding port 5288...` (**PASS**) or by
`WifiDirectManager: Standard createGroup SUCCESS!` and `NativeAA: ACTIVELY LISTENING` with no bind
(**FAIL**).

### R6 — tapping a discovered Nearby device cannot connect to it

`wifi-connection-mode=2`, `helper-connection-strategy=2`. Launch, wait for
`NearbyManager: Endpoint FOUND: <name> (<id>)`, take the id, and fire `ACTION_NEARBY_CONNECT` with
it, exactly as §4 shows.

**PASS**: `Requesting connection to endpoint: <id>` and no
`NearbyManager: Failed to request connection`, followed by a session.

**FAIL**: `NearbyManager: Stopping discovery and disconnecting` appears **between** the endpoint
being found and the connection being requested, and the request then fails.

**What a PASS would look like if the change did nothing.** Nearby auto-connect can form the session
on its own from `onEndpointFound`, before your explicit request ever lands, and that would read as a
PASS on every arm. So take the id from a capture where
`Auto-connect check: Enabled=false` — set auto-connect off for this run — or, if it cannot be
switched off, report the timestamps of `Endpoint FOUND`, the auto-connect check, and your
`am startservice`, so the two can be told apart. A run where auto-connect got there first is
INCONCLUSIVE.

**Note on §7a**: force-stopping the phone helper invalidates endpoint ids, so do not restart it
between finding the id and using it.

### R7 — the hotspot transport binds 5288 late

`wifi-connection-mode=3`, `native-ap-transport=1`. Same setup as R1. This run reads the same capture
R1 produces; it does not need its own session.

**PASS**: `WirelessServer: binding port 5288...` appears at mode arm time, before the first
`NativeAA: Attempting active poke`, and
`the Bluetooth handshake found port 5288 unbound` never appears.

**FAIL**: `found port 5288 unbound` appears, and the bind follows it rather than preceding it.

**Reachability**: this needs a handshake to reach the credentials stage, which on arm A it cannot
(R1). If R1 FAILs on an arm, R7 on that arm is **INCONCLUSIVE by construction** and should be
reported as such, not as a FAIL. That is expected on arm A and it is fine: R7's real question is
whether B and C bind the port at arm time, which is decided by the timestamp of
`binding port 5288...` alone and does not need a handshake at all.

### R8 — the control, and the run that makes the round readable

`wifi-connection-mode=2`, `helper-connection-strategy=2`. One clean ten-minute session per arm.

**PASS**: a session forms, `Throughput over 5000ms:` shows a steady frame rate for the whole window,
and none of §5's failure lines appear.

**FAIL on any arm**: this rig cannot currently hold a clean Helper/Nearby session, and **every other
run in this round is void**. Say so and stop; do not report the rest as findings.

Record the throughput numbers alongside the zero counts. A window of zeroes at 0 fps is not a
control.

---

## 7. Do not re-run

- Two builds sharing port 5288. The `release-test` thread's B2 already measured it: the second
  build kills whichever session the first had, both launch orders. That is why this round is a swap
  and not a coexistence test, and it is not being re-proven.
- The 5288 rebuild bound and its backoff. `native-aa-5288` round 1 R3 settled it at 3 per 60s.
- Whether a poke connects on this rig. §7a covers it and no run here depends on one.

---

## 8. Report back

Per run, the triple: `A / B / C`. Then the three numbers that decide whether this goes upstream:

1. **How many runs are `A=FAIL, B=PASS`.** That is the count of confirmed regressions, and it is
   what the upstream report will be built on.
2. **How many of those are `C=PASS`.** That is what the fix branch actually fixes.
3. **How many runs are `B=FAIL`.** That is the rig's contribution, and every one of them subtracts
   a row from the first two.

Then, separately and briefly: anything on arm C that arm A and arm B both did fine. Four commits of
new code on a path this size has room to break something that was working, and R8 is only one
session.
