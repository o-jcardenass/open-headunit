# wpp-endpoint-depoison, round 3 brief: poison over WiFi Direct, where the address never moves

**Build:** `fork/fix/wpp-endpoint-depoison` @ `8b3e15f3`, **three** commits on `80a81099` (current
`main`). Unchanged from round 2; no new APK is needed if round 2's is still on the unit, and its
md5 was `371d1cfee61a5f4e6246388dceea90e3`.

```bash
git fetch fork
git rev-parse fork/fix/wpp-endpoint-depoison    # 8b3e15f3...
git rev-parse 80a81099                          # the base
```

| SHA | What |
|---|---|
| `6da23cf0` | `wireless.proto` models the rejection, the real setup info and the access point, regenerated with protoc 25.1 |
| `020c6904` | A WPP TCP dial we refuse is answered with a rejection instead of a bare close |
| `8b3e15f3` | WiFi Direct gets its own static BSSID setting, separate from the access point's |

---

## 1. Why this round exists

Round 2 scored R0/R1/R3/R6 PASS with no FAIL, and R2 INCONCLUSIVE. Its R2 design needed the access
point up while the app hosted a WiFi Direct group, and this unit's WiFi HAL cannot hold both:

```
HalDevMgr: bestIfaceCreationProposal is null, requestIface=P2P, existingIface=[name=wlan2 type=AP, name=wlan0 type=STA]
WifiService: stopSoftAp uid=1073
hostapd: wlan2: AP-DISABLED
```

Thirty milliseconds apart, and `uid=1073` is the tethering stack rather than this app. The station
was never associated to anything in the whole capture, so standing it down does not free the slot
either. **That design cannot be run on this rig at any number of retries**, and round 2's own
addendum records it as a standing rig fact.

**This round poisons over WiFi Direct instead, which removes the access point from the measurement
entirely.** Round 2's brief ruled that out, on the grounds that a unit which re-addresses its group
on every create will never be advertised an endpoint. Three things it did not take into account:

1. **The endpoint survives every create.** On WiFi Direct the advertised address is the group
   owner's, and that is `192.168.49.1` in every group this unit will ever make. A poisoned record
   therefore keeps pointing at us however many times the group is torn down. Round 2's record
   pointed at the access point's `192.168.143.137`, which stopped existing the moment the AP went
   down, and that is why nothing ever dialled.
2. **The half that stops the phone rejoining repairs itself.** The phone pins SSID and BSSID
   together, and the BSSID moves on every create, so on its own it could never get back on. Round 2
   measured the repair: the phone runs its Bluetooth handshake continuously beside the dial loop,
   and a completed handshake rewrites the record's **network** while leaving its **address**
   untouched. Its own captures show it dialling the access point's address under the new WiFi Direct
   group's name and BSSID.
3. **Every new group grades `CHANGED`, so every dial on it is refused.** `decide` withholds,
   `servesDial` is false, and the phone is reachable at `192.168.49.1` because it is joined to the
   group we are hosting. Reachable and refused, with no second interface anywhere.

**The poison is a deliberate test action, not a policy bypass.** `GroupIdentityStabilityPolicy`
returns `STABLE` when the group it is looking at has the same name and the same BSSID as the last
one this unit hosted. A `force-stop` runs no teardown, so the group outlives the process, and the
relaunch's first group-info read is that same group: same name, same address, honestly assessed.
The app then tears it down and creates a new one, which grades `CHANGED` as it should. The window
is real, it is short, and it is what R1 below aims at.

---

## 2. What is different about this round

- **No access point runs during R1, R2 or R3.** Stop it before the round and confirm an empty
  `SoftApInfo`. Only R4 brings one up, and nothing hosts a WiFi Direct group while it is up.
- **The transport does not change mid-round** for R1 to R3. `native-ap-transport=0` throughout, so
  nothing asks the HAL for two interfaces and round 2's failure cannot recur.
- **R1 is a retry step, not a one-shot.** Every force-stop and relaunch produces a fresh `STABLE`
  window, because the group the last run left up is the one it last remembered. If the phone's
  handshake does not land inside the first window, do it again. Five attempts before calling it.
- **Two outcomes are wanted from the same dial, and which one arrives is a timing matter.** A
  completed Bluetooth handshake closes the RFCOMM listeners for the session, and the rejection is
  deliberately withheld while they are shut, because a phone with no route back would be stranded.
  So a dial arriving then gives R3's line instead of R2's. Both are results. The listeners reopen at
  session end (`reopening the Android Auto listeners for the phone's return`), and the phone keeps
  dialling indefinitely, so the other outcome follows if you wait.
- **Record the phone's Gearhead version exactly**, before the round and in the results:
  `adb shell dumpsys package com.google.android.projection.gearhead | grep versionName`. The clear
  this thread is chasing was read on 17.5 and not reproduced on 17.8. Rounds 1 and 2 ran
  `17.8.163804-release.daily`.
- **`static-bssid` stays set to the access point's address throughout**, and `static-p2p-bssid`
  stays unset. R6 re-grades the split for free on bring-ups R1 needs anyway.
- **A refused dial completes TLS before it is refused.** `TLS handshake complete with <ip>` on a
  refused dial is expected, not a regression.
- **`WifiVersionResponse ... status=NO_SUPPORTED_WIFI_CHANNELS(-8)` is inert.** Every capture on
  file shows it on sessions that go on to connect. Do not chase it.
- INFO is enough for every run here.

---

## 3. Preparation

Standing method as ever: `TESTING-TEMPLATE.md`, settings written to `shared_prefs/settings.xml` with
the app stopped, never through the UI. Back up `settings.xml` at the start, diff at the end, restore.

**Before anything, clear both ends.**

```bash
adb shell cmd wifi stop-softap
adb shell dumpsys wifi | grep -i SoftApInfo        # must be empty for R1 to R3
adb shell dumpsys wifi | grep "Wi-Fi is"           # station radio on, before every part
```

Forget the head unit on the phone and confirm it, so no endpoint from round 2 survives into this
one:

```
GH.WIRELESS.SETUP: No WPP on TCP configuration found in storage for the head unit
```

**Settings for R1, R2, R3 and R6**, written once and left alone:

```
wifi-connection-mode=3            native-ap-transport=0
native-wifi-version-exchange=true wifi-direct-stable-identity=true
static-bssid=00:27:15:43:06:6a    static-p2p-bssid=0
```

`wifi-direct-stable-identity` is on by default; confirm it has not been left false by an earlier
thread, because the verdict is `UNPROVEN` for every group when it is off and nothing can ever be
advertised.

**Clear these five before the first launch, and never again during the round.** R1 depends on the
app's own memory of the last group it hosted, so wiping them mid-round destroys the window:

```
wifi-direct-last-group-ssid   wifi-direct-last-group-bssid   wifi-direct-group-name
wifi-direct-group-passphrase  wifi-direct-group-name-changes
```

**Do not forget the head unit on the phone between R1 and R3.** The whole point is that it still
holds R1's endpoint. Forget it once more before R4, because R2 either cleared the record or proved
it cannot, and R4 must start from neither.

Have the phone bonded, its Bluetooth on and Android Auto running throughout, so the poke is answered
promptly. A phone whose radio is off makes the poke fail with `read failed, socket might closed or
timeout`, which cost round 2 its opening minutes.

---

## 4. The lines that decide every run

Head unit side, verified with `grep -F` against `8b3e15f3`.

**The group was assessed, and on what:**
```
WifiDirectManager: onGroupInfoAvailable: SSID: ... BSSID: ... (source=...)
WifiDirectManager: group identity ssid=... stable=
```

**The endpoint went out over WiFi Direct.** The point of R1, and the address must be `192.168.49.1`:
```
NativeAA: advertising WPP over TCP at 192.168.49.1:5299
WppTcpServer: listening for Android Auto on TCP 5299
```

**It was withheld**, which is the ordinary state on every group but the leftover one:
```
NativeAA: not advertising WPP over TCP: this unit gives its WiFi Direct group a new address on every create
```

**The dial was refused and the phone was told so.** The point of R2:
```
WppTcpServer: connection from 192.168.49.
WppTcpServer: rejecting this dial so the phone drops our endpoint and goes back to Bluetooth:
WppTcpServer: [TX] wrote type 10 (2 bytes)
```

**The refusal was swallowed because there was no route back.** The point of R3:
```
WppTcpServer: not serving this dial, and not withdrawing the endpoint because the Bluetooth listeners are not open for the phone to fall back to:
NativeAA: reopening the Android Auto listeners for the phone's return.
```

**Phone side, the record and the reaction:**
```
GH.WPP.TCP: Trying to start WPP on TCP with configuration: ... ipAddress=192.168.49.1, port=5299
GH.WIRELESS.SETUP: Handling WifiConnectionRejection with reason
GH.WIRELESS.SETUP: No WPP on TCP configuration found in storage for the head unit
```

**Should not appear at all:**
```
WppTcpServer: session error:
WifiDirectManager: ... (source=static override)
WifiDirectManager: nothing on this device reported the group's own address
```

---

## 5. The runs

### R0: build gate

Unchanged candidate, so this is a repeat of round 2's R0 and can be quoted from it if the same
checkout is still in place.

- **PASS**: `compileGithubDebugKotlin` clean and `testGithubDebugUnitTest` **2180 / 0**, with
  `WppTcpServePolicyTest` 9, `WppEndpointPolicyTest` 10, `WppMessagesTest` 17,
  `P2pBssidSourcePolicyTest` 7, `SoftApBssidPolicyTest` 20.
- **FAIL**: any other count. Stop and report.

### R1: poison the phone over WiFi Direct

1. With the five keys cleared and the settings above written, launch the app. A group forms. Quote
   the `group identity` line: it must read `stable=unproven` with "first group under this name".
   Record the SSID and the BSSID.
2. `am force-stop com.andrerinas.headunitrevived`. **Do not** use `headunit://exit`, which runs the
   teardown and removes the group. Confirm the group is still up:
   `adb shell dumpsys wifip2p | grep -i "groupFormed\|networkName"`.
3. Relaunch. Watch for the leftover group being read first, with the **same** BSSID as step 1 and
   `stable=yes` "same name and same BSSID as the last group", and then for
   `advertising WPP over TCP at 192.168.49.1:5299`.
4. The phone must complete a Bluetooth handshake inside that window, before the app tears the group
   down and creates the next one. Confirm on the phone that the record was stored:
   `Trying to start WPP on TCP with configuration: ... ipAddress=192.168.49.1, port=5299`.
5. If the window closed first, repeat steps 2 to 4. **No re-seeding is needed on a retry**: the app
   remembers whatever group it last hosted, so every force-stop leaves a matching pair behind. Five
   attempts, then stop.

- **PASS**: the phone logs the `192.168.49.1:5299` configuration. It is poisoned, and with an
  address that will still be ours after every future create.
- **INCONCLUSIVE**: five attempts and the handshake never landed inside the window, or the relaunch
  never reads the leftover group at all. Say which, and quote the relaunch's first
  `onGroupInfoAvailable`. Nothing after this run is gradable without it.
- **FAIL**: the leftover group is read and graded `stable=yes` but no endpoint goes out. That is the
  code.

### R2: the measurement

Do not touch the phone and do not clear anything. Let the app run on WiFi Direct.

What should happen on its own: the app recreates the group with a new address, the phone cannot
rejoin on the address it stored, its Bluetooth loop hands it the current one, it joins, and it dials
`192.168.49.1:5299`, which is us. Give it ten minutes and quote every `WppTcpServer:` line in order
beside the phone's `GH.*` lines over the same window.

- **PASS**: `rejecting this dial` and `[TX] wrote type 10` go out, the phone logs
  `Handling WifiConnectionRejection with reason`, then
  `No WPP on TCP configuration found in storage for the head unit`, and an ordinary Bluetooth
  session follows. The 17.5 reading holds on 17.8 and the repair works.
- **PARTIAL**: the rejection goes out and the phone parses it, but it keeps dialling 5299 and never
  logs the missing-configuration line. **That is a real result**, not a failed run, and it is the
  outcome this thread has to be prepared for on 17.8. Quote everything between the two.
- **FAIL**: a dial arrives at `WppTcpServer: connection from 192.168.49.x` and neither
  `rejecting this dial` nor R3's line follows, or `session error:` appears instead. That is the code.
- **INCONCLUSIVE**: no dial reaches the head unit in the window. Say whether the phone ever rejoined
  the group, since that is the precondition.

### R3: the stranding guard

Graded from the same window as R2, not set up separately. A dial that arrives while a Bluetooth
handshake has already completed for this session finds the listeners shut, and the rejection must be
held back.

- **PASS**: `not serving this dial, and not withdrawing the endpoint because the Bluetooth listeners
  are not open` appears, with **no** `[TX] wrote type 10` on that dial.
- **FAIL**: a type 10 goes out while the listeners are shut.
- **Not reached**: every dial in the window arrived with the listeners open. Say so; R2 is the run
  that matters.

If R3 fires first and R2 has not, end the session on the head unit and wait. The listeners reopen
(`reopening the Android Auto listeners for the phone's return`) and the phone is still dialling.

### R4: the regression that matters

Forget the head unit on the phone, confirm clean, then move the unit to the access point arm on its
own: `native-ap-transport=1`, the hotspot values from round 2 (`Navegadortz2` / `12345678` /
`hotspot-interface=wlan2`), and the access point started by hand, which the app cannot do:

```bash
adb shell cmd wifi start-softap Navegadortz2 wpa2 12345678 -b 5    # -b 5 is not optional
adb shell dumpsys wifi | grep -i SoftApInfo                         # record the frequency
```

Nothing hosts a WiFi Direct group while this runs, so the HAL is never asked for two interfaces.

- **PASS**: an ordinary connect and reconnect, projection both times, and **zero**
  `rejecting this dial` lines in the whole run. A dial that would be served must never be rejected.
- **FAIL**: any rejection line, or a reconnect that no longer forms a session.

### R5: the banner, optional and desk-gradeable

On the head unit's main screen after R2.

- **PASS**: one banner saying the phone keeps trying a network address this unit no longer uses,
  shown once rather than once per dial, and gone after R4's first served dial.
- Not a blocking verdict. Report what was on screen.

### R6: the setting split, free

Graded from R1's and R2's own bring-ups, with `static-bssid` still set to the access point's address
throughout.

- **PASS**: every `onGroupInfoAvailable` names a detected source, `source=static override` and
  `source=access point setting (stand-in)` appear **zero** times, `nothing on this device reported
  the group's own address` never appears, and the group's announced BSSID matches a detection rung
  in the source dump.
- **FAIL**: any of those.

---

## 6. If it does not work

Two shapes are worth naming ahead of time so neither is reported as a code fault.

**The leftover group is never read on relaunch.** Then R1 is INCONCLUSIVE and this design is dead on
this unit too, the same way round 2's was. Quote the relaunch's first `onGroupInfoAvailable` and the
`group identity` line beside it, and stop rather than improvising a different poison.

**The phone never rejoins the group after the address moves.** R2 is then INCONCLUSIVE, and the
finding is about Gearhead rather than about the branch: it means a completed Bluetooth handshake
does not refresh the stored BSSID after all, which contradicts round 2's own measurement and is
worth its own paragraph in the results.

Neither is a reason to retry with the access point up. That combination is not available on this
unit.
