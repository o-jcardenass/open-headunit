# wpp-endpoint-depoison — round 3 results

**Candidate:** `fork/fix/wpp-endpoint-depoison` @ `8b3e15f3`, plus one round-3 commit `6b4eb0d9d` (below)
**Baseline:** `main` @ `80a81099` (no baseline APK; this thread has no A/B)
**APK md5:** `371d1cfee61a5f4e6246388dceea90e3` (unmodified `8b3e15f3`, used for R0's confirmation only) /
`bca4665cca18df2865d697eccc5b9c23` (with `6b4eb0d9d`, used for every run below R0)
**Unit:** D-HU (UNISOC MT50), Android 14; phone D-POCO (POCO X3 NFC), Gearhead `17.8.163804-release.daily`
**Date:** 2026-09-19

## Setup notes

- **The brief's R1 could not be reproduced as written, and code reading found why.**
  `WifiDirectManager.startNativeAaQuietHost()` unconditionally tore the P2P group down and recreated
  it on every bring-up (`recreateNativeGroup()` calls `removeGroup` then `createGroup` with no prior
  read of a live group). Five clean relaunch attempts under the brief's exact settings, each spaced
  out and checked against a thermal reading, all graded `stable=no`, BSSID incrementing every time
  (`7E:8D:8D:C0:C3:F0` → `52:4B:5F:97:06:4D` → `0A:4A:09:8B:CA:39` → `06:66:76:BB:45:1D` →
  `1A:69:3B:3D:6C:AA`, interface `p2p-wlan0-0` through `-4`). This matches the brief's own §6
  contingency ("the leftover group is never read on relaunch... stop rather than improvising a
  different poison") — except the cause was structural, not a timing miss, so a code change was
  the only way to test what the brief actually asks the rest of the round to grade. The user
  directed this in-session rather than stopping at INCONCLUSIVE.
- **One mid-round deviation, not otherwise in scope**: commit `6b4eb0d9d` on
  `fix/wpp-endpoint-depoison`, "Native AA: read a surviving group instead of always tearing it
  down". Before `recreateNativeGroup()`, `startNativeAaQuietHost()` now calls `requestGroupInfo()`
  once; if a live group already carries the name `chooseNativeGroupIdentity()` is about to request,
  it is read via the same completion path a create success uses (`onGroupInfoAvailable`) instead of
  torn down. Gated on `wifi-direct-stable-identity`. Compiles clean, `testGithubDebugUnitTest`
  2180/0 unaffected (re-run after the change, see R0). Committed locally on
  `fix/wpp-endpoint-depoison` at `6b4eb0d9d`; **not pushed** — that decision is left to the
  coding session.
- **A background logcat capture died silently for ~5.5 minutes** (13:37:16–13:42:43) when a `Bash`
  tool call was interrupted mid-run; its `nohup`ed children were killed with it. Nothing decisive
  falls in that gap — it covers the tail of R1 attempt 4/5 under the *old* code, already graded
  `stable=no` from lines either side of the gap — but flagging it per the standing rule. Captures
  from 13:42:43 onward used `disown` in addition to `nohup` and ran clean the rest of the round.
- **The user manually toggled `wifi-direct-stable-identity` off via the phone's/unit's own UI
  during that dead window**, drawing a group under a throwaway name (`DIRECT-FS-Navegadortz2`).
  Caught on the next settings readback, corrected back to `true` via `set_pref.sh`, and the R1
  attempt count was not charged for it — it is not one of the five graded attempts.
- **R1's baseline (attempt 1 of the post-fix run) landed under a different SSID than the pre-fix
  run's leftover group** (`DIRECT-IP-MT50YT610E4GFPSLU` vs `DIRECT-RX-Navegadortz2`), because the
  five `wifi-direct-last-group-*`/`wifi-direct-group-name`/`-passphrase` keys were re-cleared before
  reinstalling, per the brief's own "clear before the first launch" rule applied to the restarted
  round.
- **R2/R3 could not be produced by natural churn under the fixed code**, because the fix's whole
  point is that a plain relaunch no longer produces a stale address — the phone dialled
  `192.168.49.1:5299` about 20s after R1's own advertise line and a full session came up (video,
  audio, SSL) without ever being poisoned. To still exercise `WppTcpServePolicy`'s withhold/serve
  branch, the round deliberately forced a rename mid-session (cleared `wifi-direct-group-name` /
  `-passphrase` only, leaving `wifi-direct-last-group-*` as the phone's own record, then force-stop
  + relaunch). This is a genuinely different action than the brief's own R1 (a force-stop that
  leaves the group standing); it is the closest reachable analogue under the fixed code, done at the
  user's direction, and is called out here rather than silently substituted for the brief's design.
- **The forced rename left the phone's Gearhead client in a state this round could not clear
  cleanly.** It connected to an unrelated already-known network (`Pegue Cdesta`) instead of our
  group, then its own RFCOMM retry hit `THROTTLE_LIMIT_EXCEEDED` and stayed there — confirmed
  repeating on every cycle for over two minutes. `am force-stop` on Gearhead plus a head-unit-side
  and phone-side Bluetooth toggle (the known lever for this rig, see project memory) reopened the
  poke/RFCOMM path but the phone still never opened the Android Auto Bluetooth channel; the app's
  own diagnostic (`NativeAA: The phone has answered 3 wake pokes but has never opened the Android
  Auto channel...`) is worth a look on its own merits but is very likely describing the same
  wedged session rather than a real second-Bluetooth-device conflict, since nothing about this
  rig's pairing changed mid-round. This is why **R4 is also INCONCLUSIVE**, not a finding about the
  access-point transport — the same phone-side state blocked it.
- **A new reusable script**, `forget_car_gearhead.sh` (`DEVICE=<serial> ./forget_car_gearhead.sh
  [vehicle-name]`), replaces the manual `uiautomator dump` + `input tap` sequence round 1/2 used
  ad hoc for "forget the head unit on the phone" — dynamic bounds lookup, not hardcoded coordinates,
  minimum taps (Settings → App info → Additional settings in the app → Vehicles → `<car>` → Forget).
  Verified working twice this round.
- Captures: continuous logcat from both devices across the whole round (pre-fix R1 attempts through
  the R4 recovery attempts), plus the `settings.xml` backup taken before the round. Evidence:
  `rig-evidence-wpp-endpoint-depoison-round3`
  (`wpp-endpoint-depoison-round3-captures.zip`, sha256
  `2c139915eac1df31d7daeafc8957819da56d3883acdd653a1f11b06dd2e4ac07`).

## R0 — build gate

**PASS**

- `compileGithubDebugKotlin` clean (via `build_hur.sh`) both before and after `6b4eb0d9d`.
- `testGithubDebugUnitTest` **2180 / 0** after `6b4eb0d9d` (via `run_unit_tests.sh`, `JAVA_HOME=
  /opt/android-studio/jbr`), matching round 2's count exactly. Per-suite: `WppTcpServePolicyTest`
  **9**, `WppEndpointPolicyTest` **10**, `WppMessagesTest` **17**, `P2pBssidSourcePolicyTest` **7**,
  `SoftApBssidPolicyTest` **20**, `WppHandshakeSessionTest` **30** — all match the brief.
- A stray ad hoc `./gradlew testGithubDebugUnitTest` (without the script's `JAVA_HOME`) failed on
  `compileGithubDebugJavaWithJavac`/`javaCompiler` — a JDK selection problem with the direct
  invocation, not a code issue; the approved script's own run (immediately before and after) was
  clean both times. Noted per the standing rule against ad hoc gradle invocations.

## R1 — poison the phone over WiFi Direct

**PASS** (five attempts under the brief's original code all graded INCONCLUSIVE-shaped `stable=no`;
PASS is under `6b4eb0d9d`, first attempt)

- Settings written: `wifi-connection-mode=3`, `native-ap-transport=0`, `native-wifi-version-
  exchange=true`, `wifi-direct-stable-identity=true`, `static-bssid=00:27:15:43:06:6a`,
  `static-p2p-bssid=0`. Five identity keys cleared before the first launch.
- Baseline launch: `13:52:02.276 WifiDirectManager: group identity: no kept network yet, so
  DIRECT-IP-MT50YT610E4GFPSLU is drawn now and kept for every later create.` Group formed
  `4A:07:A4:F0:AC:CF`, `stable=unproven`.
- `am force-stop`, confirmed group survives (`groupFormed: true ... mGroup network:
  DIRECT-IP-MT50YT610E4GFPSLU`), relaunched.
- Decisive lines, quoted with timestamps:
  ```
  13:52:55.319 WifiDirectManager: group identity: asking for the kept network
               DIRECT-IP-MT50YT610E4GFPSLU again, so a phone that saved it can rejoin...
  13:52:55.517 WifiDirectManager: a group named DIRECT-IP-MT50YT610E4GFPSLU is already up from
               before this bring-up; reading it instead of tearing it down.
  13:52:55.641 WifiDirectManager: group identity ssid=DIRECT-IP-MT50YT610E4GFPSLU ... bssid=
               4A:07:A4:F0:AC:CF stable=yes (same name and same BSSID as the last group)
  13:53:00.482 NativeAA: advertising WPP over TCP at 192.168.49.1:5299
  13:53:02.106 [phone] GH.WPP.TCP: Trying to start WPP on TCP with configuration: ...
               ipAddress=192.168.49.1, port=5299
  ```
- Discard-rule check: not clean on the first four relaunch attempts under the original code
  (5 attempts, all `stable=no`), clean (first attempt) once `6b4eb0d9d` was applied.
- **Beyond the brief's own PASS bar**: the phone completed its Bluetooth handshake and dialled
  inside the same bring-up that advertised the endpoint, and a full projection session came up
  unprompted (`13:53:02.238 AapTransport: Start Aap transport handshake`, `13:53:02.402 SSL
  handshake complete`, H.264 1280x720 decode configured, audio track started). The fix's effect is
  that a force-stop/relaunch under stable identity no longer produces a stale endpoint at all in the
  simple case — poisoning it at all needed a deliberate rename (see R2/R3 below).

## R2 — the measurement

**INCONCLUSIVE**

- Could not be produced by natural churn (see Setup notes) — forced via a deliberate rename
  mid-session instead.
- Decisive lines:
  ```
  13:57:36.551 WifiDirectManager: group identity: no kept network yet, so
               DIRECT-9B-MT50YT610E4GFPSLU is drawn now and kept for every later create.
  13:57:37.753 WifiDirectManager: group identity ssid=DIRECT-9B-MT50YT610E4GFPSLU ... bssid=
               12:48:19:23:58:A1 stable=unproven (the name changed since the last group
               (DIRECT-IP-MT50YT610E4GFPSLU), so the address cannot be compared yet)
  13:59:24.887 [phone] GH.WirelessNetRequest: Connected to network Pegue Cdesta while expected
               DIRECT-IP-MT50YT610E4GFPSLU
  14:01:02.118 [phone] GH.ConnLoggerV2: ... WIRELESS_CONNECTING_RFCOMM ... throttle
               THROTTLE_LIMIT_EXCEEDED  (repeating on every retry cycle for 2+ minutes)
  ```
- No `WppTcpServer: connection from` ever appears in the capture for this window — the phone never
  reached the head unit at the network layer, so `WppTcpServePolicy`'s withhold/serve branch was
  never exercised. The finding is squarely about the phone's own Gearhead client (it associated
  with an unrelated already-known network, then throttled its own RFCOMM retry), not about
  `fix/wpp-endpoint-depoison`.

## R3 — the stranding guard

**Not reached** — graded from R2's window, which never produced a dial. See R2.

## R4 — the regression that matters

**INCONCLUSIVE**

- Settings written: `native-ap-transport=1`, `hotspot-ssid=Navegadortz2`, `hotspot-password=
  12345678`, `hotspot-interface=wlan2`, `auto-enable-hotspot=false`, `static-bssid=
  00:27:15:43:06:6a` (unchanged). Head unit's vehicle entry re-forgotten on the phone
  (`forget_car_gearhead.sh`, confirmed "Accepted vehicles: None").
- SoftAP started by hand: `cmd wifi start-softap Navegadortz2 wpa2 12345678 -b 5`, confirmed at
  **5240 MHz**, BSSID `00:27:15:43:06:6a`, interface `wlan2`.
- The head unit correctly picked up the access-point credentials (`SoftApCredentials: SUCCESS -
  Providing credentials from wlan2: SSID=Navegadortz2, IP=192.168.143.137, BSSID=
  00:27:15:43:06:6A`) and repeatedly poked the phone, but the phone never opened the Android Auto
  Bluetooth channel across 5+ minutes and three separate recovery attempts (`am force-stop` on
  Gearhead, head-unit-side Bluetooth toggle, phone-side Bluetooth toggle, all three together).
  Decisive line: `14:04:37.476 NativeAA: The phone has answered 3 wake pokes but has never opened
  the Android Auto channel on radio [Navegadortz2]. Its Android Auto is most likely bound to a
  different Bluetooth device...`
- This almost certainly continues the same wedged Gearhead session R2/R3's forced rename put the
  phone into (same session ID, `251e1319-87e4-410f-b893-7ec115bbc015`, was still active in the
  capture at the time of the R4 attempt) rather than a real second-device conflict — nothing about
  the rig's Bluetooth pairing changed mid-round. **Zero `rejecting this dial` lines appear**, which
  is consistent with PASS as far as it goes, but no dial of any kind reached the head unit in this
  window, so the run cannot be scored either way. Needs a clean re-run once the phone's Gearhead
  session is confirmed clear (a longer cool-down, or a fresh capture started from a phone reboot,
  would be the next round's first step for this run specifically).

## R5 — the banner

**Not reached** — depends on a graded R2, which this round could not produce.

## R6 — the setting split

**PASS**

- Graded from R1's five pre-fix attempts and the post-fix baseline/reuse pair (six
  `onGroupInfoAvailable: SSID` lines total in the graded window): every one names `source=IPv6
  link-local`, `source=static override` and `source=access point setting (stand-in)` appear **zero**
  times, `nothing on this device reported the group's own address` **zero** times, and the
  announced BSSID matches the same line's own detection source in every case.
- `static-bssid` held `00:27:15:43:06:6a` (the access point's address) throughout R1/R2/R3, matching
  the brief's requirement.

## Anything the brief did not ask about

- **The bounty finding is R1 itself.** Reading a surviving group instead of always tearing it down
  did not just make R1's specific poison test reproducible — the very first bring-up under the fix
  went from a poisoned endpoint to a complete, working wireless session in under 5 seconds with zero
  manual intervention beyond the initial launch. That is a materially better outcome than the
  round's own design was probing for, and worth weighing as a standalone fix candidate independent
  of this thread's original question.
- **The fix trades one failure mode for a narrower one.** Before `6b4eb0d9d`, a relaunch always
  re-poisoned the phone (this thread's whole problem). After it, a relaunch is safe *unless*
  something forces a rename while the phone still holds an old record — and this round's own R2/R3
  attempt shows that when that does happen, the phone's recovery is not graceful: it landed on an
  unrelated known network and then throttled its own Bluetooth retry rather than cleanly falling
  back. Whether an ordinary (non-test-forced) rename can occur in the field under `6b4eb0d9d` is
  unmeasured this round — worth a dedicated brief.
- **Gearhead's RFCOMM throttle appears to be a per-session counter with no observed within-session
  reset.** It stayed at `THROTTLE_LIMIT_EXCEEDED` for the rest of this round regardless of Bluetooth
  toggles on either device; only ending the app (which none of this round's `am force-stop` calls on
  Gearhead actually achieved — its `:projection`/`:shared`/`:car`/`:provider` processes kept running
  throughout under different process-name suffixes, invisible to a plain `pidof`) seems able to
  clear it. `ps -A | grep gearhead` is the reliable liveness check on this phone, not `pidof
  <package>`.

---

## Addendum: what the captures say, and one correction to R6

Written by the coding session after re-reading `full_hu.txt` and `full_poco.txt` from
`rig-evidence-wpp-endpoint-depoison-round3`. **No grade changes.** R0/R1/R6 PASS, R2/R4
INCONCLUSIVE, R3/R5 not reached, no FAIL, exactly as reported.

### The brief's R1 was structurally unreachable, and that is the brief's fault

The round-3 brief asserted that a relaunch reads the group a force-stop leaves up, giving a genuine
`STABLE` window. It does not. At `8b3e15f3`, `startNativeAaQuietHost()` ends in
`recreateNativeGroup(forceStandard = false)` with no `requestGroupInfo()` before it, and that is
`removeGroup` then create. The five `stable=no` attempts were the code, not a timing miss, and the
operator's reading of it is correct. The brief should not have been written that way.

### 1. The poison is measured first-hand, on the phone's own storage

Three rounds in, this is the first direct sight of the state the thread exists to repair. Before
`13:53:02` D-POCO logged `No WPP on TCP configuration found in storage for the head unit, will not
start WPP on TCP` **219 times** (`13:33:38.866` to `13:52:59.571`). From `13:53:02.106` it holds:

```
GH.WPP.TCP: Trying to start WPP on TCP with configuration: WifiProjectionProtocolOnTcpConfiguration(
  wifiConfiguration=WifiConfiguration(ssid=DIRECT-IP-MT50YT610E4GFPSLU, bssid=4A:07:A4:F0:AC:CF,
  securityMode=WPA2_PERSONAL, mostRecentlyUsedFrequenciesMhz=[5200]), ipAddress=192.168.49.1, port=5299)
```

It still holds exactly that at `14:06:13.099`, eight minutes after the group was renamed out of
existence, across a Gearhead process restart (pid `28113` to `14924`). Over that span it logged
`Connected to network Pegue Cdesta while expected DIRECT-IP-MT50YT610E4GFPSLU` **34 times**,
`WIRELESS_WIFI_CONNECTED_TO_WRONG_SSID` 34 times and `NETWORK_UNAVAILABLE_NETWORK_NOT_FOUND` 22
times. Nothing cleared the record. R1 did precisely what it was written to do.

### 2. A rejection cannot reach a phone that cannot join the network

This is the design lesson for round 4, and it is why R2's substitution could not have worked. A
rename changes the network the phone is looking for, so it never reached `192.168.49.1:5299` at the
network layer and `WppTcpServePolicy` was never asked. That is not a rig limit. A recreate under the
**kept name** moves the address only, which is the state the gate grades `CHANGED` while the phone
can still be handed its way back on by Bluetooth. Round 4 must move the address and never the name.

### 3. The chain announced the station's MAC as the group's BSSID, twice

The dump at `13:57:36.855`, on the relaunch, with the group still up on `p2p-wlan0-12`:

```
  IPv6 link-local (p2p-wlan0-12)   = null
  NetworkInterface.hardwareAddress = 00:00:00:00:00:00
  requestDeviceInfo                = 02:00:00:00:00:00
  group.owner.deviceAddress        = 02:00:00:00:00:00
getMacFromShell: Target failed, scanning ALL interfaces in sysfs...
Last resort MAC found on wlan0: 06:54:f8:6e:60:b2
  sysfs / ip link                  = 06:54:f8:6e:60:b2
```

The live group's real address was `4A:07:A4:F0:AC:CF` and had not moved. The app then graded it
`stable=no (same name but the BSSID moved from 4A:07:A4:F0:AC:CF to 06:54:F8:6E:60:B2)`, wrote the
station's address into `wifi-direct-last-group-bssid`, and sent it to the phone twice as
`Credentials updated. SSID=DIRECT-IP-MT50YT610E4GFPSLU, IP=192.168.49.1, BSSID=06:54:F8:6E:60:B2`.
The same wrong address went out twice more under `DIRECT-RX`. A phone associates on name and address
together, so this is a contributing cause of R2's failure on our side, alongside the rename.

Fixed on the candidate as `0847fd38`: the blind sysfs scan is restricted to P2P interfaces, and an
address from another interface no longer sets the identity verdict, is remembered as the next
group's yardstick, or is cached as a reading. 2192 tests, up from 2180.

### 4. The served path ran 27 times and failed 27 times, and it is shipped code

Every dial from `13:53:02.458` to `13:57:25.072`, one every 10.1 s, was accepted and completed TLS
(`TLSv1.2, TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256`). Because the AAP session on 5288 had come up
20 ms ahead of the first dial, every one took the `projectionSessionUp()` branch and was held
silent. The phone's own view of that hold:

```
13:53:02.149 GH.WIRELESS.SETUP: Scheduling WPP first message timeout trigger ... attempt: 5
13:53:12.185 GH.WPP.IO: Failed to read 4 bytes at offset 0
13:53:12.185 GH.WPP.IO: java.net.SocketTimeoutException: Read timed out
13:53:12.193 GH.WIRELESS.SETUP: State changed to RFCOMM_READ_WRITE_FAILURE
13:53:12.193 GH.WIRELESS.SETUP: Triggering WPP restart. Reason=WPP_SOCKET_IO_EXCEPTION
13:53:12.200 GH.ConnLoggerV2: WIRELESS_SETUP_WPP_RESTART_WITH_DELAY ... throttle THROTTLE_LIMIT_EXCEEDED
13:53:12.207 GH.WPP.TCP: Trying to start WPP on TCP with configuration: ...   <-- redial
```

26 of the 27 held channels closed `after 0 pings`, so on 17.8 the phone does not use the held
channel as a health check at all: it waits ~10 s for a first message, times out, and restarts WPP,
for the length of the session. The AAP session survives, which is what the branch wanted, but the
silence costs a TLS handshake every 10 s and feeds Gearhead's own restart throttle.

**This is `6885df91`, on `main`, not `020c6904`**, so the candidate's serve path is unchanged. What
the candidate changes is that advertising on WiFi Direct is what makes this reachable there. It
needs its own thread; it is not a round-4 run.

### 5. Zero `PROV_DISC` lines across the reuse

`grep -cE "PROV_DISC|prov_disc|checkStuckRetryBurst|stuck retry"` over the whole head unit capture
is **0**. The specific hazard the "never reuse a P2P group" rule names did not appear. One session,
on the Native path, which is the path that bisection never covered.

### Correction to R6

R6's grade stands: `source=static override` and `source=access point setting (stand-in)` are zero
across the graded window with the access point's `00:27:15:43:06:6a` in `static-bssid` throughout,
and that is what the run was for.

The sentence "every one names `source=IPv6 link-local`" is **withdrawn**. Four
`onGroupInfoAvailable: SSID` lines do not:

```
DIRECT-RX-Navegadortz2,        BSSID: 06:54:F8:6E:60:B2 (source=sysfs / ip link)
DIRECT-RX-Navegadortz2,        BSSID: 06:54:F8:6E:60:B2 (source=lastKnownBssid cache)
DIRECT-IP-MT50YT610E4GFPSLU,   BSSID: 06:54:F8:6E:60:B2 (source=sysfs / ip link)
DIRECT-IP-MT50YT610E4GFPSLU,   BSSID: 06:54:F8:6E:60:B2 (source=lastKnownBssid cache)
```

The same address under two different group names on two different interfaces is what finding 3
describes. R6's related claim that "the announced BSSID matches the same line's own detection
source" is true as written and is also the problem: the source was wrong.

### One more thing the round did not ask about

Round 1's R3 already measured what a reconnect costs when it runs entirely over TCP with no RFCOMM
step: `00:04:21.976` dial, `00:04:22.056` TLS, `00:04:22.998` WPP handshake complete,
`00:04:23.082` SSL, `00:04:24.413` first `Media Sink Setup Request`. **2.44 s to video against
9.03 s** for the same unit's Bluetooth first connect in the same run. Taken with finding 1, that
makes the endpoint a reconnect accelerator worth having rather than only a hazard to be gated, and
`6b4eb0d9d` is what makes it reachable on WiFi Direct at all. It cannot ever be the first
connection: the endpoint reaches the phone only as field 6 of `WifiVersionRequest`, over Bluetooth,
which is what the 219 `No WPP on TCP configuration found` lines say from the other side.

### 6. A force-stop on Gearhead does not clear a stored endpoint, and that corrects a standing note

The project note on this record has said since 2026-09-01 that the endpoint lives in the running
Gearhead process rather than on disk, so "restarting Gearhead clears it as surely as forgetting the
head unit does". On 17.8 that is wrong, and this round measures it directly:

```
09-19 14:01:20.076   881 Zygote: Process 28113 exited due to signal 9
09-19 14:05:26.258 14924 on.gearhead:car: Using CollectorTypeCC GC.
09-19 14:06:13.099 14924 GH.WPP.TCP: Trying to start WPP on TCP with configuration: ...
                         ssid=DIRECT-IP-MT50YT610E4GFPSLU, bssid=4A:07:A4:F0:AC:CF,
                         ipAddress=192.168.49.1, port=5299
```

`am force-stop` killed the process outright, a new one came up four minutes later, and it dialled
the same stored configuration. Whether the record is on disk or in a sibling process the force-stop
missed is not separable from a logcat, but the operational answer is the same: **only forgetting the
head unit on the phone clears it**, which `forget_car_gearhead.sh` now does in one command.

That also explains part of round 3's own recovery difficulty: the `am force-stop` calls were not
clearing what the round needed cleared, which is why the round 4 brief's step 0 is a phone reboot.
