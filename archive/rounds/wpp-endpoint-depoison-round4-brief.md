# wpp-endpoint-depoison round 4

**Candidate:** `fork/fix/wpp-endpoint-depoison`, tip to be confirmed at R0. It must contain
`0847fd38` ("only a P2P interface can answer what a group's address is") and the rig's own
`6b4eb0d9d` ("read a surviving group instead of always tearing it down"), rebased onto it.
**Baseline:** none. This thread has no A/B.
**Unit:** D-HU (UNISOC MT50, Android 14). **Phone:** D-POCO (POCO X3 NFC), Gearhead 17.8.
**Rounds so far:** 1, 2, 3. Read `wpp-endpoint-depoison-round3-results.md` and its addendum first.

## 1. What round 3 settled, so this round does not re-litigate it

- **R1 works and is a known-good step.** The phone's own storage carried
  `ssid=DIRECT-IP-..., bssid=4A:07:A4:F0:AC:CF, ipAddress=192.168.49.1, port=5299` from
  `13:53:02.106` and still carried it eight minutes later. Repeat it as written; do not redesign it.
- **R2 failed because the round changed the network's name.** A rename means the phone cannot find
  the network at all, so it never reaches port 5299 and the serve or refuse decision is never asked.
  This round moves the **address** and never the **name**. That is the whole difference.
- **The BSSID chain could answer with the station's MAC.** `0847fd38` fixes it. This round gates on
  it rather than trusting it, because a wrong address here silently invalidates every later run.
- **Do not grade the 10 s re-dial loop here.** A dial that arrives while projection is already up is
  held silent, the phone times out after ~10 s and re-dials, and that is shipped code on `main`
  (`6885df91`), not the candidate. It will appear in this round's captures. Note it and move on.

## 2. Preparation

Same rig setup as round 3. No access point runs in R1 to R3, so the WiFi HAL is never asked for two
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

Clear these five once, before the first launch only, and not again between runs:
`wifi-direct-last-group-ssid`, `wifi-direct-last-group-bssid`, `wifi-direct-group-name`,
`wifi-direct-passphrase`, `wifi-direct-group-name-changes`.

On the phone, forget the head unit with `forget_car_gearhead.sh` and confirm
`Accepted vehicles: None`. Then confirm Gearhead is genuinely stopped with `ps -A | grep gearhead`
and not `pidof`, which round 3 found does not see its `:projection`/`:shared`/`:car` processes.
**Start the round from a phone reboot.** Round 3 ended with a wedged Gearhead session that no
force-stop or Bluetooth toggle cleared, and its RFCOMM throttle has no observed within-session reset.

## 3. Greps

```bash
grep -n "group identity ssid=" hu.txt                       # the verdict, per group
grep -n "Initial BSSID from" hu.txt                         # which rung answered; the gate below
grep -n "advertising WPP over TCP\|not advertising WPP over TCP" hu.txt
grep -n "WppTcpServer:" hu.txt                              # every dial, in order
grep -n "rejecting this dial\|wrote type 10\|not withdrawing the endpoint" hu.txt
grep -n "Credentials updated. SSID=" hu.txt                 # what the phone was told
grep -cE "PROV_DISC|prov_disc|stuck retry" hu.txt           # the reuse hazard
grep -n "Trying to start WPP on TCP with configuration" poco.txt
grep -n "WifiConnectionRejection\|No WPP on TCP configuration found" poco.txt
```

## 4. The runs

### R0: build gate

`compileGithubDebugKotlin` clean and `testGithubDebugUnitTest` green via the approved scripts, never
an ad hoc `./gradlew`. Report the total and these suites: `P2pInterfaceNamePolicyTest`,
`P2pBssidSourcePolicyTest`, `GroupIdentityStabilityPolicyTest`, `WppTcpServePolicyTest`,
`WppEndpointPolicyTest`. The coding host measured **2192 / 0** with `0847fd38` alone; `6b4eb0d9d`
may add more. Record the tip SHA and the APK md5.

- **PASS**: both clean, and the tip contains both commits.
- **FAIL**: anything red. Stop; nothing below is gradable.

### R0a: the address gate

Launch once and read the first `Initial BSSID from` line.

- **PASS**: it reads `IPv6 link-local`, and the `group identity` line's `source=` agrees.
- **FAIL**: it names `sysfs / ip link`, `lastKnownBssid cache` or `NetworkInterface.hardwareAddress`
  on a group that is up. That is `0847fd38` not working and it invalidates every run below. Quote
  the whole `BSSID source dump` block and stop.

### R1: poison the phone

As round 3, which passed on its first attempt under the reuse commit.

1. Launch. A group forms. Quote the `group identity` line; expect `stable=unproven` with "first
   group under this name". Record the SSID and the BSSID.
2. `am force-stop com.andrerinas.headunitrevived`. **Not** `headunit://exit`, which removes the
   group. Confirm it survives: `adb shell dumpsys wifip2p | grep -i "groupFormed\|networkName"`.
3. Relaunch. Expect `a group named <SSID> is already up from before this bring-up; reading it
   instead of tearing it down`, then `stable=yes (same name and same BSSID as the last group)`, then
   `advertising WPP over TCP at 192.168.49.1:5299`.
4. On the phone, confirm the record was stored: `Trying to start WPP on TCP with configuration: ...
   ssid=<SSID>, bssid=<the BSSID from step 1>, ipAddress=192.168.49.1, port=5299`.

- **PASS**: the phone logs that configuration, carrying the same BSSID the head unit announced.
- **INCONCLUSIVE**: five attempts and it never lands. Say whether the leftover group was read.
- **FAIL**: `stable=yes` is reached and no endpoint goes out. That is the code.

### R2: the measurement

**The run this thread has been trying to reach for three rounds.** Do not clear anything and do not
touch the phone's settings.

1. `headunit://exit` on the head unit. This runs the teardown, so the group is removed and the
   interface goes with it, which is what keeps R0a's failure out of the run.
2. Relaunch. No live group exists, so a fresh one is created **under the kept name**: same SSID, new
   BSSID. Expect `stable=no (same name but the BSSID moved from ... to ...)` and then
   `not advertising WPP over TCP: this unit gives its WiFi Direct group a new address on every
   create`.
3. Leave it for ten minutes. The phone cannot rejoin on the address it stored, its Bluetooth
   handshake hands it the current one, it joins, and it dials `192.168.49.1:5299`, which is still us
   while the verdict is `CHANGED`. Quote every `WppTcpServer:` line in order beside the phone's
   `GH.*` lines over the same window.

- **PASS**: `rejecting this dial` and `[TX] wrote type 10` go out, the phone logs
  `Handling WifiConnectionRejection with reason`, then `No WPP on TCP configuration found in storage
  for the head unit`, and an ordinary Bluetooth session follows. The repair works on 17.8.
- **PARTIAL**: the rejection goes out and the phone parses it, but it keeps dialling 5299 and never
  logs the missing-configuration line. **That is a real result**, not a failed run, and it is the
  outcome this thread has to be prepared for. Quote everything between the two.
- **FAIL**: a dial arrives at `WppTcpServer: connection from 192.168.49.x` and neither
  `rejecting this dial` nor R3's line follows, or `session error:` appears instead.
- **INCONCLUSIVE**: no dial reaches the head unit in the window. Say whether the phone ever rejoined
  the group, since that is the precondition, and quote its
  `Connected to network ... while expected ...` lines if it went elsewhere.

**Do not substitute a rename if this does not work.** Round 3 tried that and it cost the rest of the
round. If step 2 does not produce a moved BSSID under the same name, say so and stop at R2.

### R3: the stranding guard

Graded from R2's window, not set up separately. A dial that arrives after a Bluetooth handshake has
completed for this session finds the listeners shut, and the rejection must be held back.

- **PASS**: `not serving this dial, and not withdrawing the endpoint because the Bluetooth listeners
  are not open`, with **no** `[TX] wrote type 10` on that dial.
- **FAIL**: a type 10 goes out while the listeners are shut.
- **Not reached**: every dial arrived with the listeners open. Say so; R2 is the run that matters.

If R3 fires before R2 has, end the session on the head unit and wait. The listeners reopen
(`reopening the Android Auto listeners for the phone's return`) and the phone is still dialling.

### R4: the group reuse

The run `6b4eb0d9d` owes, and the one CLAUDE.md's standing rule against reusing a P2P group is
waiting on. Five `headunit://exit` and relaunch cycles, then five `am force-stop` and relaunch
cycles, with no phone action in between.

- Quote each cycle's `group identity` line and say whether the group was read or recreated.
- Report `grep -cE "PROV_DISC|prov_disc|stuck retry"` for the whole capture.
- Report whether the phone rejoined and a session formed on each cycle, with the time from launch to
  `SSL handshake complete`.

- **PASS**: zero `PROV_DISC` lines, the reads happen on the force-stop cycles and the creates on the
  exit cycles, and the phone connects on every cycle.
- **FAIL**: a retry storm, or a cycle where the phone cannot join a group that was read.

### R5: the regression that matters

The hotspot arm, run **on its own** with no P2P group hosted at any point, so the HAL is never asked
for two interfaces. `native-ap-transport=1`, `hotspot-ssid=Navegadortz2`, `hotspot-password=12345678`,
`hotspot-interface=wlan2`, `auto-enable-hotspot=false`. Start the AP by hand and confirm the band.
Forget the head unit on the phone first. Connect, then disconnect and reconnect once.

- **PASS**: both connects reach a picture, and `rejecting this dial` is **zero** across the run.
- **FAIL**: a dial that would be advertised is rejected, or the reconnect does not form.
- **INCONCLUSIVE**: the phone never opens the Android Auto Bluetooth channel. Round 3 ended here.
  Reboot the phone and try once more before calling it.

### R6: the setting split

Free, graded from every run above.

- **PASS**: `source=static override` and `source=access point setting (stand-in)` are zero,
  `nothing on this device reported the group's own address` is zero, and every group-info line's
  announced BSSID agrees with its own `source=`, with `static-bssid=00:27:15:43:06:6a` set
  throughout R1 to R4.
- **FAIL**: the access point's address is announced for a WiFi Direct group.

## 5. Two shapes that are not failures

- **R2 INCONCLUSIVE because the phone joined something else.** If it lands on an unrelated known
  network rather than ours, that is the same shape round 3 hit and the run is not gradable. Say so
  and quote the lines; do not force it.
- **A wedged Gearhead.** `THROTTLE_LIMIT_EXCEEDED` repeating with no within-session reset, no
  Android Auto channel opening, and force-stop not clearing it. Reboot the phone, note the time
  lost, and restart from the run that was interrupted. Do not spend the round on recovery attempts.
