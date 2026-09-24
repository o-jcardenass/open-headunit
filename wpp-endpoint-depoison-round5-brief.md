# wpp-endpoint-depoison round 5

**Candidate:** `fork/fix/wpp-endpoint-depoison` at `8e6c27db`, **plus the rig's own `6b4eb0d9d`**
("read a surviving group instead of always tearing it down") rebased on top. The whole round
depends on that commit; see §1.
**Baseline:** none. This thread has no A/B.
**Unit:** D-HU (UNISOC MT50, Android 14). **Phone:** D-POCO (POCO X3 NFC), Gearhead 17.8.
**Rounds so far:** 1, 2, 3, 4. Read `wpp-endpoint-depoison-round4-results.md` **and its addendum**
first. The addendum retracts the original run's central claim, so do not carry that claim forward.

## 0. Push the candidate before running it

`6b4eb0d9d` has existed only on the tester PC for three rounds. It is in no ref on `fork` and not in
the coding host's checkout, and the tester agent resets shared branches, so it is one `git checkout`
away from being lost. **Rebase it onto `8e6c27db` and push the branch** before R0. If that is not
possible, say so in the results and stop: R1 step 2 and everything after it need it.

## 1. What is new since round 4, and why the round is shaped around it

Three commits landed on the candidate after round 4, all **unmeasured on hardware**.

- `ac8e9433` ties `WifiInfoResponse` field 5 (`AccessPointType`) to the identity verdict. DYNAMIC is
  Android Auto's own signal **not to persist our credentials at all**; STATIC tells it to keep them.
  We used to send STATIC on WiFi Direct, which is what let a moving address be stored and then
  pinned. Now STATIC goes out exactly where the WPP endpoint would, and DYNAMIC everywhere else.
- `8e6c27db` reads the locally-administered bit of an address, which AOSP always sets on one it
  generated. Three effects: the read-back line carries `address=generated|factory`; **the hotspot is
  no longer exempt** and grades its own access point like any group; and a first bring-up on a
  generated address reports `CHANGED` with its reason rather than "the next one decides".
- `6b4eb0d9d` (the rig's) keeps a surviving group instead of recreating it, which is the only thing
  that holds the address still on a unit that re-addresses every create.

**Those three together are what make R3 reachable, and R3 is the run this thread has been trying to
get for four rounds.** The chain: a kept group means the same BSSID on the second bring-up, which
means `STABLE`, which means the endpoint goes out and field 5 says STATIC, which means the phone
stores both. Clearing the yardstick then puts the verdict back to `CHANGED` **while the group is
still up at the same address**, so the phone can still join and dial, and we refuse it. Every
previous attempt failed because the act that made us refuse also made the network unreachable.

**A verdict of `CHANGED` on a first bring-up is the new correct behaviour, not a regression.** Round
4 saw `stable=unproven (first group under this name)`. On the same unit this round it reads
`stable=no` with a reason naming the address as generated. Both withhold the endpoint; only the
wording changed.

## 2. Preparation

Same rig setup as round 4. No access point runs in R1 to R4, so the WiFi HAL is never asked for two
interfaces and round 2's `bestIfaceCreationProposal is null` wall is not in the way.

Settings, written with `set_pref.sh` and read back before the first launch:

```
wifi-connection-mode=3
native-ap-transport=0
native-wifi-version-exchange=true
wifi-direct-stable-identity=true
static-bssid=00:27:15:43:06:6a        (left set on purpose; R6 is graded on it being ignored)
static-p2p-bssid=0
```

Clear these five once, before the first launch only: `wifi-direct-last-group-ssid`,
`wifi-direct-last-group-bssid`, `wifi-direct-group-name`, `wifi-direct-passphrase`,
`wifi-direct-group-name-changes`. R3 clears two of them again, at the point it says to and not
before.

On the phone, forget the head unit with `forget_car_gearhead.sh` and confirm `Accepted vehicles:
None`. Confirm Gearhead is stopped with `ps -A | grep gearhead`, not `pidof`. **Start from a phone
reboot.**

**Check the Bluetooth pairing is healthy before anything else.** Round 4 lost a whole run to a stale
one, and its signature is a phone that dials a stale endpoint forever while pokes succeed at the
socket level with no `NativeAA: Connection accepted from` ever following. If that shape appears,
unpair and re-pair and restart the round rather than diagnosing it again.

## 3. Greps

```bash
grep -n "group identity ssid=" hu.txt                       # the verdict and, new, address=
grep -n "address=generated\|address=factory\|address=unreadable" hu.txt
grep -n "access point BSSID" hu.txt                         # the hotspot's own, R6
grep -n "Initial BSSID from" hu.txt
grep -n "advertising WPP over TCP\|not advertising WPP over TCP" hu.txt
grep -n "WppTcpServer:" hu.txt                              # every dial, in order
grep -n "rejecting this dial\|wrote type 10\|not withdrawing the endpoint" hu.txt
grep -cE "PROV_DISC|prov_disc|stuck retry" hu.txt           # the reuse hazard
grep -n "already up from before this bring-up" hu.txt       # the group was read, not recreated
grep -n "Won't persist Wifi Configuration" poco.txt         # field 5 landing, THE new instrument
grep -n "Trying to start WPP on TCP with configuration" poco.txt
grep -n "WifiConnectionRejection\|No WPP on TCP configuration found" poco.txt
```

`Won't persist Wifi Configuration (%s) since access point is DYNAMIC` is Gearhead's own line, read
out of the shipped dex of both 17.5 and 17.8. It is the only first-party proof that field 5 arrived
and was acted on, so quote it verbatim wherever it appears and say where it does not.

## 4. The runs

### R0: build gate

`compileGithubDebugKotlin` clean and `testGithubDebugUnitTest` green via the approved scripts, never
an ad hoc `./gradlew`. The coding host measured **2206 / 0** on `8e6c27db` alone; `6b4eb0d9d` added
no pure-policy tests last round, so expect the same or more. Report the total and these suites:
`MacAddressOriginPolicyTest` (6 expected), `GroupIdentityStabilityPolicyTest` (25),
`WppEndpointPolicyTest` (14), `WppMessagesTest` (19), `WppTcpServePolicyTest` (9),
`P2pBssidSourcePolicyTest`, `P2pInterfaceNamePolicyTest`. Record the tip SHA and the APK md5.

- **PASS**: both clean, and the tip contains `8e6c27db` and `6b4eb0d9d`.
- **FAIL**: anything red. Stop; nothing below is gradable.

### R0a: the address gate

Launch once. Read the first `Initial BSSID from` line and the `group identity` line.

- **PASS**: the source reads `IPv6 link-local`, the `group identity` line's `source=` agrees, and it
  now also carries `address=generated`.
- **FAIL**: the source names `sysfs / ip link`, `lastKnownBssid cache` or
  `NetworkInterface.hardwareAddress` on a group that is up, or `address=` is missing from the line.
  Quote the `BSSID source dump` block and stop.

Round 4's one-off first-launch stand-in read on a leftover, non-live persisted group is expected
again and is not a failure. It is a different group, and `address=` on it says nothing about ours.

### R1: the phone is told not to keep a moving network

**The first half of field 5, and the cheapest new measurement in the round.** On this first
bring-up the verdict is `CHANGED`, so field 5 must say DYNAMIC.

1. With the group from R0a up, let the phone complete an ordinary Bluetooth handshake.
2. Read the head unit: expect `not advertising WPP over TCP` with a reason naming the moving address.
3. Read the phone.

- **PASS**: the phone logs `Won't persist Wifi Configuration (...) since access point is DYNAMIC`,
  and no `Trying to start WPP on TCP with configuration` follows for this head unit.
- **FAIL**: the phone stores a configuration anyway, or logs nothing and later dials an endpoint.
- **INCONCLUSIVE**: the handshake never completes. Say how far it got.

### R2: the phone is told to keep one that will not move

**The second half.** `6b4eb0d9d` is what makes this reachable.

1. `am force-stop com.andrerinas.headunitrevived`. **Not** `headunit://exit`, which removes the group.
   Confirm the group survived: `adb shell dumpsys wifip2p | grep -i "groupFormed\|networkName"`.
2. Relaunch. Expect `a group named <SSID> is already up from before this bring-up; reading it
   instead of tearing it down`, then `stable=yes (same name and same BSSID as the last group)`, then
   `advertising WPP over TCP at 192.168.49.1:5299`.
3. Let the phone handshake again.

- **PASS**: the same BSSID as step 1, `stable=yes`, the endpoint goes out, **no** `Won't persist`
  line on this handshake, and the phone logs `Trying to start WPP on TCP with configuration: ...`
  carrying this round's SSID and that BSSID.
- **FAIL**: the group is torn down and recreated despite `6b4eb0d9d`, or `stable=yes` is reached and
  no endpoint goes out.
- **INCONCLUSIVE**: five attempts and the phone never lands. Say whether the group was read.

### R3: the measurement

**The run this thread has been trying to reach for four rounds.** Do not touch the phone.

1. Clear **only** `wifi-direct-last-group-ssid` and `wifi-direct-last-group-bssid`. Leave the group
   up and leave every other setting alone.
2. `am force-stop` and relaunch. The group is still up at the same address, so it is read again, but
   with no yardstick the verdict is `CHANGED` and the endpoint is withheld. Expect
   `not advertising WPP over TCP`.
3. Leave it for ten minutes. The phone still holds the endpoint from R2 and **can still join**,
   because the address never moved. It dials `192.168.49.1:5299` and we refuse. Quote every
   `WppTcpServer:` line in order beside the phone's `GH.*` lines over the same window.

- **PASS**: `rejecting this dial` and `wrote type 10` go out, the phone logs `Handling
  WifiConnectionRejection with reason`, then `No WPP on TCP configuration found in storage for the
  head unit`, and an ordinary Bluetooth session follows. The repair works on 17.8.
- **PARTIAL**: the rejection goes out and the phone parses it, but it keeps dialling 5299 and never
  logs the missing-configuration line. **That is a real result**, not a failed run, and it is the
  outcome to be prepared for. Quote everything between the two.
- **FAIL**: a dial arrives and neither `rejecting this dial` nor R4's line follows, or
  `session error:` appears instead.
- **INCONCLUSIVE**: no dial reaches the head unit in the window. Say whether the phone rejoined the
  group, and quote its `WIRELESS_WIFI_SCAN_RESULTS_BSSID_MISMATCH` count if it did not. A mismatch
  here would mean the address moved after all, which is a finding about `6b4eb0d9d`, not about the
  rejection.

**Do not substitute a rename or a recreate.** Round 3 tried a rename and round 4 tried a recreate,
and both made the network unreachable, which is the whole reason this sequence keeps the group.

### R4: the stranding guard

Graded from R3's window, not set up separately. A dial arriving after a completed handshake finds
the listeners shut, and the rejection must be held back.

- **PASS**: `not serving this dial, and not withdrawing the endpoint because the Bluetooth listeners
  are not open`, with **no** `wrote type 10` on that dial.
- **FAIL**: a type 10 goes out while the listeners are shut.
- **Not reached**: every dial arrived with the listeners open. Say so; R3 is the run that matters.

### R5: the group reuse, with the phone in it

**The clause round 4 owed.** Its R4 ran ten cycles and graded PASS, but the phone was occupied
throughout and never joined any of them, so zero `PROV_DISC` measured nothing: the hazard
CLAUDE.md's standing rule names is peer-side by definition. This time the phone must connect.

Five `headunit://exit` and relaunch cycles, then five `am force-stop` and relaunch cycles.

- Quote each cycle's `group identity` line and say whether the group was read or recreated.
- Report `grep -cE "PROV_DISC|prov_disc|stuck retry"` for the whole capture.
- **Report, per cycle, whether the phone rejoined and a session formed, with the time from launch to
  `SSL handshake complete`.** If the phone is busy with an earlier arm, wait for it to settle first.

- **PASS**: zero `PROV_DISC`, reads on the force-stop cycles and creates on the exit cycles, **and
  the phone connects on every cycle**.
- **PARTIAL**: the first two hold and the phone could not be made to join. Say so plainly rather
  than grading PASS; that is what happened last round.
- **FAIL**: a retry storm, or a cycle where the phone cannot join a group that was read.

### R6: the hotspot, which changed underneath this arm

The hotspot arm run **on its own**, no P2P group hosted at any point. `native-ap-transport=1`,
`hotspot-ssid=Navegadortz2`, `hotspot-password=12345678`, `hotspot-interface=wlan2`,
`auto-enable-hotspot=false`. Start the AP by hand and confirm the band. Forget the head unit first.
Connect, then disconnect and reconnect once.

The hotspot used to be exempt from every identity check. It is not any more, so this arm is a
no-regression check on a path that changed. D-HU's access point address is `00:27:15:43:06:6a`,
which is globally unique, so it should grade `factory` and behave exactly as round 4's R5 did.

- **PASS**: `access point BSSID ... is factory` in the log, both connects reach a picture, and
  `rejecting this dial` is zero across the run.
- **FAIL**: the AP grades `generated` and the run then withholds the endpoint or refuses a dial, or
  a connect that worked in round 4 does not now.
- **INCONCLUSIVE**: the phone never opens the Android Auto Bluetooth channel.

**We cannot test the case this change exists for.** A unit whose soft AP address is generated is the
one that would previously have been poisoned, and D-HU is not that unit. Do not report the PASS
above as evidence the new hotspot path works, only that it does not regress the old one.

### R7: the setting split

Free, graded from every run above, unchanged from round 4.

- **PASS**: `source=static override` and `source=access point setting (stand-in)` are zero,
  `nothing on this device reported the group's own address` is zero, and every group-info line's
  announced BSSID agrees with its own `source=`, with `static-bssid=00:27:15:43:06:6a` set
  throughout R0a to R5.
- **FAIL**: the access point's address is announced for a WiFi Direct group.

## 5. Shapes that are not failures

- **A first bring-up reading `stable=no` with a reason naming a generated address.** That is
  `8e6c27db` working. Round 4's `stable=unproven` on the same unit is the old wording.
- **`Won't persist Wifi Configuration ... since access point is DYNAMIC` on the phone.** That is the
  point of R1, not an error.
- **The leftover persisted group read once on the first launch.** Same as round 4.
- **A wedged Gearhead.** `THROTTLE_LIMIT_EXCEEDED` repeating with no within-session reset and no
  Android Auto channel opening. Reboot the phone, note the time lost, restart from the interrupted
  run. Do not spend the round on recovery attempts.
