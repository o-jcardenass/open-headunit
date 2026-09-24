# native-aa-wireless round 1

**Candidate:** `fork/fix/native-aa-wireless` at `4fc4753a`, three commits on `main` (`80a81099`),
2254 JVM tests. The branch was compacted from 19 commits to three after an audit; tree equality was
verified (`2ed42dac` on both the compacted tip and the pre-compaction tip `5825c533`), so the code is
the same code, but **no SHA from any earlier round resolves on this branch any more**. The
pre-compaction refs are `pre-compaction/native-aa-wireless` (`5e2f636b`, 12 commits) on `fork` and
`pre-compaction/native-aa-wireless-work` (`5825c533`, 19 commits) on the coding host.
**Baseline:** none for the new work. R1, R7 and R14 are graded against measurements recorded in the
`wpp-endpoint-depoison` round 5 results, not against a second APK.
**Unit:** D-HU (UNISOC MT50, Android 14). **Phone:** D-POCO (POCO X3 NFC), Gearhead 17.8.
**Also used:** D-SAM (API 19) for R16 only.
**Rounds so far:** this thread absorbed `wpp-endpoint-depoison` rounds 1 to 5 and
`992-hands-free-wake` round 1 (never run). Read `wpp-endpoint-depoison-round5-results.md` first.

## 0. What this round is, and the two things it cannot reach

An audit before the PR found two confirmed bugs, a credential fault older than the branch, and a
duplicated identity rule. The fixes are in, and **four behaviours now differ from what round 5
measured**. This round re-grades those four and covers everything on the branch that hardware can
reach at all.

**Two things on this branch are not gradable on this rig, and no run below pretends otherwise.**

- **The Bluetooth adapter cycle.** It runs only below API 33 (`LAST_CYCLEABLE_SDK = 32`) and only
  against a unit holding a hands-free client link. D-HU is API 34, so it never arms; D-SAM is API 19
  and advertises no hands-free profile at all, and `HEADSET_CLIENT` arrived in API 24. R17 grades the
  gate rather than the cycle: that D-HU never attempts one and never writes a verdict.
- **The external Bluetooth module route.** No unit in the rig has a `bttype:extra` module or a daemon
  on 3152, so the canonical peer address change is covered by JVM tests and nothing here.

Say both in Setup notes so the results do not read as coverage they are not.

## 1. What changed since round 5, and what that does to round 5's own results

Four behaviour changes, all unmeasured on hardware. **The first two invalidate parts of round 5.**

- **A group that is read rather than created no longer promotes the identity verdict.** Round 5's R2
  force-stopped, relaunched, read the surviving group and got `stable=yes` from comparing that group
  with the record it had written itself. Nothing distinguished "seen again" from "created again", so
  on a unit that re-addresses every create the endpoint went out on the strength of a group that had
  never actually repeated. The read path now hands back the verdict the *creates* earned, persisted
  in a new key `wifi-direct-last-identity-verdict`. **Round 5's R2 PASS is not reproducible by its
  own criteria and must not be carried forward.** R1 and R2 below replace it.
- **The access point is graded across bring-ups instead of from the locally-administered bit.** The
  branch used to read the bit alone: factory meant stable, anything else meant changed, on every
  callback, with no memory. Android has randomised soft AP addresses since 10 and the randomisation
  is persistent per configuration, so that denied the endpoint for good to most units. It now goes
  through the same policy a group does, with its own yardstick (`soft-ap-last-group-ssid`,
  `-bssid`, `soft-ap-last-identity-verdict`). D-HU's own AP address is factory, so the visible change
  here is that the **first** hotspot bring-up now withholds where it used to advertise. R7.
- **A refusal that describes this unit is no longer reported as the phone's stale endpoint**, and a
  dial arriving while projection is up is held silent rather than answered with a rejection. R5, R6.
- **Credentials with no passphrase are refused on both transports, and cost no wake poke.** R8.

Two smaller ones: the version exchange has no setting any more, so the Type 4 goes out on every
handshake (R10); and a new action ends the session while leaving the network up (R12).

## 2. Preparation

Same rig setup as round 5. R0 to R6 and R8 to R15 run on D-HU with D-POCO as the phone. R7 needs the
hotspot transport, R16 is D-SAM only.

Settings for the WiFi Direct runs, written with `set_hu_prefs.sh` and read back before the first
launch. Note `native-wifi-version-exchange` is **gone**: writing it now does nothing, and it is no
longer carried in a settings export.

```
wifi-connection-mode=3
native-ap-transport=0
wifi-direct-stable-identity=true
static-bssid=00:27:15:43:06:6a        (left set on purpose; R15 is graded on it being ignored)
static-p2p-bssid=0
```

Clear these before the first launch only, and note that two of them are new keys this round:
`wifi-direct-last-group-ssid`, `wifi-direct-last-group-bssid`, **`wifi-direct-last-identity-verdict`**,
`wifi-direct-group-name`, `wifi-direct-group-passphrase`, `wifi-direct-group-name-changes`,
**`soft-ap-last-group-ssid`**, **`soft-ap-last-group-bssid`**, **`soft-ap-last-identity-verdict`**,
`connection-issue-stale-endpoint`.

**The passphrase key is `wifi-direct-group-passphrase`, not `wifi-direct-passphrase`.** Round 5 lost
time to that.

On the phone, forget the head unit with `forget_car_gearhead.sh` and confirm `Accepted vehicles:
None`. Confirm Gearhead is stopped with `ps -A | grep gearhead`, not `pidof`. **Start from a phone
reboot.** Check the Bluetooth pairing is healthy before anything else: a stale one costs a whole
round and its signature is a phone that dials forever while pokes succeed at the socket level with no
`NativeAA: Connection accepted from` following. Unpair and re-pair rather than diagnosing it again.

**A round starting from a powered-off WiFi radio on this unit eats one failed launch to `BUSY`
before the radio settles.** Expected, not a failure.

## 3. Greps

```bash
# identity, on both transports
grep -n "group identity ssid=" hu.txt                    # the group's verdict and address=
grep -n "access point identity ssid=" hu.txt             # NEW: the AP's own verdict
grep -n "address=generated\|address=factory\|address=unreadable" hu.txt
grep -n "read rather than created" hu.txt                # NEW: the survivor arm
grep -n "is already up from" hu.txt                      # the group was read, not recreated
grep -n "Initial BSSID from" hu.txt
grep -n "BSSID source dump" hu.txt

# the endpoint and the dial
grep -n "advertising WPP over TCP\|not advertising WPP over TCP" hu.txt
grep -n "WppTcpServer:" hu.txt                           # every dial, in order
grep -n "rejecting this dial\|not withdrawing the endpoint" hu.txt
grep -n "it says nothing about the phone" hu.txt         # NEW: our own state, not the phone's
grep -n "holding it silent" hu.txt                       # NEW: a dial during projection

# credentials
grep -n "carry no passphrase" hu.txt                     # NEW: the refusal
grep -n "no passphrase to join with" hu.txt              # NEW: the poke that did not go out
grep -n "was named by hand" hu.txt                       # NEW: the AP-off override
grep -n "SUCCESS - Providing credentials" hu.txt
grep -n "Wrote TYPE 3\|Sending WifiStartRequest\|WifiVersionRequest" hu.txt

# session end
grep -n "ACTION_END_SESSION_STAY_ARMED received" hu.txt  # NEW
grep -n "keeping the .* network up for the phone's return" hu.txt
grep -n "Native AA user exit. Stopping active launcher." hu.txt
grep -n "Calling socket.connect()" hu.txt                # a poke that really went out

# the reuse hazard and the link
grep -cE "PROV_DISC|prov_disc|stuck retry" hu.txt
grep -n "SSL handshake complete" hu.txt                  # match without the Handshake: prefix
grep -o "p2p-wlan0-[0-9]*" hu.txt | sort -u

# phone side
grep -n "Won't persist Wifi Configuration\|Not persisting Wi-Fi" poco.txt
grep -n "Trying to start WPP on TCP with configuration" poco.txt
grep -n "WifiConnectionRejection\|No WPP on TCP configuration found" poco.txt
grep -n "BSSID_MISMATCH\|CONNECTED_TO_WRONG_SSID" poco.txt
```

`Handshake: SSL handshake complete` is `AppLog.d` and absent at INFO. Match on `SSL handshake
complete` without the prefix, and remember a VERBOSE capture carries two lines per session because
two log levels print it, not because there were two handshakes.

## 4. The runs

### R0: build gate

`compileGithubDebugKotlin` clean and `testGithubDebugUnitTest` green via the approved scripts, never
an ad hoc `./gradlew`. The coding host measured **2254 / 0**. Report the total and these suites:
`MacAddressPolicyTest` (10), `GroupIdentityStabilityPolicyTest` (31), `WppEndpointPolicyTest` (14),
`WppTcpServePolicyTest` (12), `WppMessagesTest` (19), `NativeCredentialsPolicyTest` (10),
`SessionEndGroupPolicyTest` (16), `BluetoothRadioCyclePolicyTest` (19), `UserExitHotspotPolicyTest`
(13), `SoftApBssidPolicyTest` (20), `P2pBssidSourcePolicyTest` (10), `P2pInterfaceNamePolicyTest` (5),
`AutomationCommandPolicyTest` (21).

**The APK identity check must use a symbol, not a string or a resource.** Version name and
versionCode do not move between candidates, and round 5 spent two attempts measuring the wrong build.
Use the new action, which no earlier build can contain:

```bash
PKG=com.andrerinas.headunitrevived
adb shell pm path $PKG          # pull that apk, then
unzip -p <apk> 'classes*.dex' | strings | grep -F 'ACTION_END_SESSION_STAY_ARMED'
unzip -p <apk> 'classes*.dex' | strings | grep -F 'MacAddressPolicy'
```

- **PASS**: both clean, both symbols present, md5 recorded, tip is `4fc4753a`.
- **FAIL**: anything red or either symbol absent. Stop; nothing below is gradable.

### R0a: the address gate

Launch once. Read the first `Initial BSSID from` line, the `BSSID source dump` block and the
`group identity` line.

- **PASS**: the source reads `IPv6 link-local`, the `group identity` line's `source=` agrees and
  carries `address=generated`, and **no line anywhere names `wlan0` as the group's address.**
- **FAIL**: the source names `sysfs / ip link`, `lastKnownBssid cache` or
  `NetworkInterface.hardwareAddress` on a group that is up, or any rung answers with the station's
  address. Quote the dump and stop: R1 onward all rest on the address being the group's own.

This gate is stricter than round 5's. Two rungs that run *earlier* than the one bounded last round
were bounded this time, so a station address reaching the verdict is now a regression rather than a
known gap.

### R1: a surviving group no longer promises its address repeats

**The headline change, and the one that retires a round 5 result.**

1. From the cleared state, launch. First create: expect `stable=no` with a reason naming the address
   as generated, and `not advertising WPP over TCP`.
2. Record the group's BSSID from the `group identity` line.
3. `am force-stop com.andrerinas.headunitrevived`. **Not** `headunit://exit`, which removes the group.
   Confirm it survived: `adb shell dumpsys wifip2p | grep -i "groupFormed\|networkName"`.
4. Relaunch.

- **PASS**: `is already up from`, the same BSSID as step 2, and the verdict line reads `stable=no`
  with the reason `this group was already up and was read rather than created, so nothing new was
  measured`. The endpoint stays withheld. `wifi-direct-last-group-bssid` is **unchanged** from step 2
  and `wifi-direct-last-identity-verdict` still reads `CHANGED`.
- **FAIL**: `stable=yes` on the read, which is the pre-fix behaviour, or the yardstick moves.
- **INCONCLUSIVE**: the group did not survive the force-stop. Say so; R2 needs it too.

### R2: a read hands back what the creates earned

The complement, and the only way to reach `STABLE` on a unit that re-addresses every create. This
seeds the verdict rather than earning it, which is legitimate here because the question under test is
whether the read path *preserves* a verdict, not how one is earned.

1. With the group from R1 still up, `am force-stop`.
2. Write the group's actual SSID and BSSID into `wifi-direct-last-group-ssid` and
   `wifi-direct-last-group-bssid`, and write `wifi-direct-last-identity-verdict=STABLE`.
3. Relaunch. The group survives and is read.
4. Let the phone complete a Bluetooth handshake.

- **PASS**: `stable=yes` with the read reason, `advertising WPP over TCP at 192.168.49.1:5299`, and
  the phone logs `Trying to start WPP on TCP with configuration` carrying this SSID and BSSID. Field 5
  and field 6 agreed, which is the invariant.
- **FAIL**: the verdict is not preserved, or the endpoint is withheld on a `STABLE` verdict, or the
  endpoint goes out while field 5 says DYNAMIC.
- **INCONCLUSIVE**: the phone never handshakes in five attempts.

Note the phone's own instrument is unreliable here: `17.8.163804` prints a generic
`Not persisting Wi-Fi configuration.` on the DYNAMIC and STATIC handshakes alike, so
`Trying to start WPP on TCP with configuration` appearing or not is what separates them, and that is
field 6 landing. Fields 5 and 6 are one verdict by design and cannot be separated without breaking
the invariant a test asserts. Do not report field 5 as independently measured.

### R3: the refused dial, and the phone letting go

Round 5's R3 recipe, which worked on the first try. Do not touch the phone.

1. With the endpoint stored from R2, `am force-stop`.
2. Clear **only** `wifi-direct-last-group-ssid`, `wifi-direct-last-group-bssid` and
   `wifi-direct-last-identity-verdict`. Leave the group up and every other setting alone.
3. Relaunch. The group is read again, but with no yardstick and no stored verdict the read arm hands
   back `UNPROVEN`, so the endpoint is withheld. Expect `not advertising WPP over TCP`.
4. Leave it ten minutes. The phone still holds the endpoint and can still join, because the address
   never moved. Quote every `WppTcpServer:` line in order beside the phone's lines.

- **PASS**: `rejecting this dial so the phone drops our endpoint`, the phone logs `Handling
  WifiConnectionRejection with reason: CONNECTION_REJECTION_REASON_INVALID_SETUP_TOKEN`, then
  `No WPP on TCP configuration found in storage for the head unit`, and an ordinary Bluetooth session
  follows.
- **PARTIAL**: the rejection goes out and the phone parses it but keeps dialling. A real result.
- **FAIL**: a dial arrives and neither the rejection nor R4's withholding line follows, or
  `session error:` appears instead.
- **INCONCLUSIVE**: no dial reaches the head unit. Say whether the phone rejoined, and quote any
  `BSSID_MISMATCH` count if it did not.

**Do not substitute a rename or a recreate.** Both make the network unreachable, which is the whole
reason this sequence keeps the group up.

### R4: the stranding guard

Graded from R3's window, not set up separately. A dial arriving after a completed handshake finds the
listeners shut, and the rejection must be held back.

- **PASS**: `not serving this dial, and not withdrawing the endpoint because the Bluetooth listeners
  are not open`, with no rejection on that dial.
- **FAIL**: a rejection goes out while the listeners are shut.
- **Not reached**: every dial arrived with the listeners open. **This arm has now gone five rounds
  without executing**, so record it as not reached rather than inventing a setup for it.

### R5: a dial during projection is held silent

**New, and reachable exactly when R2 succeeded**, because 17.8 re-dials a stored endpoint every ten
seconds for the length of a session.

1. Get a projecting session with the endpoint stored, which is R2's end state.
2. Let it project for three minutes without touching anything.

- **PASS**: `a dial arrived while projection is up; holding it silent` for each dial, **no** rejection
  on the wire, the session survives all of them, and no stale-endpoint banner is raised. Count the
  phone's `WPP_SOCKET_IO_EXCEPTION` lines and report them; they are expected and are the phone timing
  out its own read.
- **FAIL**: a rejection goes out during projection, or the session drops within 9 ms of one, or the
  main screen shows the stale-endpoint banner afterwards.
- **INCONCLUSIVE**: no dial arrives in the window. Say so and give the session length.

### R6: a dial while stopping is not blamed on the phone

**New.** `stop()` drops the listening port, and a dial already past TLS used to be answered with a
rejection and raise the record about a phone whose endpoint was perfectly good.

1. From a projecting session with the endpoint stored, send `headunit://exit`.
2. Immediately, within the same second, let the phone's next dial land.

Timing this by hand is unreliable, so grade it as an absence over the whole capture instead:

- **PASS**: no `rejecting this dial` anywhere that is not accounted for by R3, and no
  `connection-issue-stale-endpoint` timestamp written during or after a clean exit. If the narrow
  window is hit, `not serving this dial, and it says nothing about the phone` is the line.
- **FAIL**: the stale-endpoint record is raised on an exit, or a rejection goes out with the reason
  naming the server not listening.
- Read `connection-issue-stale-endpoint` out of `settings.xml` before and after and quote both.

### R7: the access point is measured, not assumed

**New, and the one run that changes hotspot behaviour.** D-HU's own AP address is factory, so this
grades the promotion across two bring-ups rather than the generated case.

1. Stop the app. Set `native-ap-transport=1`. Clear the three `soft-ap-last-*` keys.
2. Switch the hotspot on by hand and confirm it is up.
3. Launch. Read `access point identity ssid=`.
4. `headunit://exit`, relaunch, read it again.

- **PASS**: the first bring-up reads `stable=unproven` with `address=factory` and withholds the
  endpoint; the second reads `stable=yes (same name and same BSSID as the last group)` and
  advertises it. `soft-ap-last-group-bssid` is written after the first and unchanged after the second.
- **FAIL**: the first bring-up reads `stable=yes`, which is the pre-fix bit-only behaviour, or the
  second never promotes although the address repeated.
- **INCONCLUSIVE**: no access point resolves. Quote the `SoftApCredentials:` lines.

D-HU is not the unit this change exists for, since its address is factory either way. The unit it
matters on has a generated AP address, and the rig has none: say so. A no-regression result here is
the whole ask.

### R8: credentials with no passphrase are refused, and cost no poke

**New, and the cheapest run in the round.** Reachable on the hotspot transport by configuration.

1. Still on `native-ap-transport=1`. Set `hotspot-ssid` to the AP's real name and leave
   `hotspot-password` **empty**. Stop the app, write both, verify, relaunch.

- **PASS**: `these credentials for '<ssid>' carry no passphrase, and the phone refuses an open
  network. Not sending them.`, **no** `Wrote TYPE 3` and **no** `Calling socket.connect()` anywhere
  after it, and the main screen raises the hotspot-configuration record.
- **FAIL**: a Type 3 goes out, or a poke does. Either means the guard did not fire.
- Also confirm the poke gate separately: `not waking the phone for '<ssid>', which has no passphrase
  to join with` should appear on the credentials delivery.

Then set `hotspot-password` correctly and confirm the run recovers, so the refusal is not a wedge.

### R9: an access point the system says is off

**New, and it is a log-only grade.** With `hotspot-interface` named by hand, credentials are published
even when the framework reports the AP not enabled, which is a deliberate escape hatch that used to be
silent.

1. Set `hotspot-interface` to the AP interface name. Switch the hotspot **off**. Launch.

- **PASS**: either `the system reports no access point running, but <iface> was named by hand, so its
  credentials are being handed over on that claim`, or the resolve never gets that far because the
  interface has no address, in which case say which and quote the `SoftApCredentials:` lines.
- **FAIL**: credentials are handed over with the AP off and nothing says so.

This one is expected to be INCONCLUSIVE on most units, because an interface that is down usually has
no site-local address and the resolve stops earlier. That is a fine outcome; record which branch ran.

### R10: the version request has no setting left

1. Back to `native-ap-transport=0`. Run one ordinary Bluetooth handshake.

- **PASS**: `Sending WifiVersionRequest (Type 4)` on the handshake, `WifiVersionResponse` or the 3 s
  fallback following, and the session proceeds. Writing `native-wifi-version-exchange=false` into
  `settings.xml` beforehand changes nothing, which is the point.
- **FAIL**: no Type 4, or the handshake stalls waiting for a response that never comes.

Also confirm the key is gone from an export: run the settings export and grep it for
`native-wifi-version-exchange`. It must be absent.

### R11: the stale-endpoint record retires without a served dial

The record used to clear only on a dial we served, which a working rejection makes unreachable, so it
stood forever on the units the fix had repaired.

1. From R3's end state, where the record was raised and the phone then let go.
2. Let one ordinary Bluetooth handshake land with no dial refused beside it.

- **PASS**: `connection-issue-stale-endpoint` reads 0 in `settings.xml` afterwards and the banner is
  gone from the main screen.
- **FAIL**: the timestamp survives a clean handshake.
- **INCONCLUSIVE**: R3 did not raise the record in the first place.

### R12: End session, stay ready

**The new action.** Drive it through the automation surface, never the dialog.

```bash
adb shell am broadcast -a com.andrerinas.openheadunit.ACTION_END_SESSION_STAY_ARMED
```

It relays like any other extra-less command and is **not** one of the configuring actions, so it
does not need "Allow external configuration" turned on. If it is ignored, the receiver never saw it:
check the action spelling before concluding anything about the feature.

1. From a projecting session on WiFi Direct, send it.
2. Confirm the group: `adb shell dumpsys wifip2p | grep -i "groupFormed\|networkName"`.
3. Wait three minutes without touching the phone.

- **PASS**: `ACTION_END_SESSION_STAY_ARMED received`, the session ends, `keeping the ... network up
  for the phone's return`, `groupFormed: true` with the **same** network name and BSSID as before, and
  **no** `Calling socket.connect()` in the three minutes that follow. Report whether the phone
  reconnected on its own and how long it took.
- **FAIL**: the group is removed, or a poke goes out, or the service stops.
- Then reconnect by whatever route the phone takes and report the time to `SSL handshake complete`
  against R14's figures.

### R13: Stop and Exit are unchanged

The regression guard for R12. Both must still remove the network.

1. From a projecting session, send `headunit://exit`. Confirm `groupFormed: false` within 3 s.
2. Relaunch, get a session, and use the projection screen's own Stop Connection. Confirm the same.

- **PASS**: both remove the group, `Native AA user exit. Stopping active launcher.` appears for each,
  and no `the wireless teardown did not finish in` line.
- **FAIL**: either leaves the group up. That would mean the new action changed what a user exit means.

### R14: group reuse with the phone rejoining, and the reconnect times

Round 5's R5 repeated, because R1 changed what a read does and the timing claim needs re-reading. Ten
cycles: five `headunit://exit` and relaunch, five `am force-stop` and relaunch. The phone reconnects
on its own; do not touch it.

- **PASS**: the phone rejoins on all ten, `PROV_DISC` is zero for the whole capture, and the five
  reused-group cycles are materially faster to `SSL handshake complete` than the five that recreated.
  Round 5 measured 1.1 s against 8.9 to 9.5 s. **Report the ten times individually**, because the
  endpoint is now withheld on a read where round 5 advertised it, and the question is whether the
  speed came from the kept address or from the endpoint.
- **FAIL**: `PROV_DISC` is non-zero, or a reused group is no faster than a recreated one.
- This is the run that tells us whether R1's fix costs anything. It is the most important regression
  measurement in the round.

### R15: the static BSSID is per transport, and applies when saved

1. With detection working, set `static-p2p-bssid` to a plainly wrong address and launch.
2. Then set `static-bssid` to a wrong address, on the WiFi Direct transport.

- **PASS**: neither typed address is announced while a rung reads the group's own; the
  `BSSID source dump` shows detection answering and `source=` never names a setting. The access
  point's address is never announced for a group.
- **FAIL**: either typed value wins over a detected one, or the AP's address is announced as the
  group's.
- Then, through the settings UI this once, change the WiFi Direct BSSID and confirm the app asks for
  a restart. That is the one UI step in this round and it is graded by eye; say so.

### R16: D-SAM, below Q

D-SAM only. Two things, one launch.

1. Install the candidate and open the projection screen.

- **PASS**: the projection activity opens and no `NoClassDefFoundError` naming
  `AudioManager$OnModeChangedListener` appears. On a pre-API-31 device this is the strongest available
  check that the listener type is no longer named in a field declaration.
- **FAIL**: a class-resolution crash on opening projection.

2. Run four bring-ups and read `group identity`.

- **PASS**: the line carries `nameChanges=N/3` and the count walks forward across bring-ups rather
  than resetting, and the verdict reaches `RENAMED` by the third change with a reason naming the
  platform. The endpoint is withheld throughout.
- **FAIL**: the count resets, or an endpoint is advertised below Q.

### R17: the wake, as far as this rig reaches

Grades the gate, not the cycle. See §0.

1. Over the whole round, with `native-aa-wake-damage-verdict` cleared at the start.

- **PASS**: no adapter cycle is ever attempted on D-HU, `native-aa-radio-cycle-verdict` is still 0 at
  the end of the round, and `native-aa-wake-armings-without-session` moves only on armings that
  reached a stand-down and produced no session. Quote the value at the start and the end.
- **FAIL**: a cycle is attempted on API 34, or a radio-cycle verdict is written on a unit that never
  cycled. The second is exactly the bug the audit found, and this is the only hardware check for it.

## 5. What a result must carry

Beyond the standing format: the tip SHA and APK md5; the ten reconnect times from R14 individually;
the before and after values of `wifi-direct-last-identity-verdict`, `soft-ap-last-identity-verdict`,
`connection-issue-stale-endpoint`, `native-aa-radio-cycle-verdict` and
`native-aa-wake-armings-without-session`; and an explicit note that the adapter cycle and the external
Bluetooth module route were not reachable on this rig.

**Round 5's R2 result is superseded by R1 and R2 here.** Say so in the results so the next reader does
not reconcile them.
