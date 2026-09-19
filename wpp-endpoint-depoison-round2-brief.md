# wpp-endpoint-depoison, round 2 brief: the measurement round 1 could not reach

**Build:** `fork/fix/wpp-endpoint-depoison` @ `fcee3ea2`, **three** commits on `80a81099` (current `main`).

```bash
git fetch fork
git rev-parse fork/fix/wpp-endpoint-depoison    # fcee3ea2...
git rev-parse 80a81099                          # the base
```

| SHA | What |
|---|---|
| `6da23cf0` | `wireless.proto` models the rejection, the real setup info and the access point, regenerated with protoc 25.1 |
| `020c6904` | A WPP TCP dial we refuse is answered with a rejection instead of a bare close |
| `fcee3ea2` | WiFi Direct gets its own static BSSID setting, separate from the access point's |

---

## 1. Why this round exists, and what round 1 actually found

Round 1 scored R0/R1/R3 PASS with no FAIL, and R2/R4 INCONCLUSIVE. It reported the cause as a rig
radio condition: the phone could not join the head unit's WiFi Direct group on any of three
bring-ups. **That reading is wrong, and the evidence for the real cause is in round 1's own
captures.**

The unit was carrying `static-bssid=00:27:15:43:06:6a`, the **access point's** `wlan2` address, left
over from the `wpp-over-tcp` thread, and round 1's §3 said to leave everything but
`native-ap-transport` unchanged when moving to WiFi Direct. `WifiDirectManager` took that address
verbatim as the **group's** BSSID, ahead of every detection rung:

```
WifiDirectManager: onGroupInfoAvailable: SSID: DIRECT-RB-Navegadortz2, BSSID: 00:27:15:43:06:6A (source=static override), GO: true, IFACE: p2p-wlan0-0
WifiDirectManager: group identity ... stable=yes (the static BSSID setting fixes the address the phone is told) source=static override
```

while the same capture's source dump had the group's real address one rung below, moving on every
create as this unit is known to do:

```
static override (Settings)       = 00:27:15:43:06:6a
getGroupOwnerBssid()             = null
IPv6 link-local (p2p-wlan0-0)    = d2:65:d0:00:51:73
IPv6 link-local (p2p-wlan0-1)    = c6:26:e0:80:0d:2c
```

The phone joins on name **and** address together, so it was given a network that does not exist:

```
GH.WIRELESS.SETUP: Info response received. Received credentials=WifiConfiguration(ssid=DIRECT-RB-Navegadortz2, bssid=00:27:15:43:06:6A, ...)
GH.WIRELESS.SETUP: State changed to CONNECTING_WIFI          23:55:05.571
GH.WIRELESS.SETUP: State changed to ABORTED_WIFI             23:55:41.647
```

That is the whole failure, it was deterministic, and `TESTING-TEMPLATE.md` §7a already warns about
it. There is no radio regression: D-POCO joined this unit's group cleanly on the afternoon of the
same day. `fcee3ea2` separates the two settings so the collision cannot recur, and **R6 grades it**.

**Round 1's R2 could not have produced the measurement even with a working radio.** A dial is
refused only when `WppEndpointPolicy.decide` returns `Withhold`, which needs
`strategy != HOTSPOT && identity != STABLE`. R1 poisoned the phone with a **hotspot** endpoint and
then took that network down. The phone then fails at the network layer and never dials at all,
which is exactly what round 1's own side finding recorded. A refusal needs the phone to **reach**
us and be turned away.

**So R2 changes the network, not the strategy.** `WppTcpServer` binds the wildcard address, so a
dial arriving over a still-live access point is accepted while `callbacks.strategy()` already reads
`WIFI_DIRECT`. Leaving the hotspot up while switching `native-ap-transport` produces the one state
that exercises the rejection: a reachable endpoint the head unit refuses. It also removes the
WiFi Direct join from R2's critical path entirely.

This is the only R2 design this unit can run. Poisoning over WiFi Direct instead would need the
endpoint advertised there, and on a unit that re-addresses its group on every create the policy
will never advertise it, which is correct and is not to be worked around.

---

## 2. What is different about this round

- **Record the phone's Gearhead version, exactly**, before the round and in the results:
  `adb shell dumpsys package com.google.android.projection.gearhead | grep versionName`. The clear
  was read on 17.5 and not reproduced on 17.8. Round 1 ran `17.8.163804-release.daily`.
- **`static-bssid` stays set this round, deliberately.** With `fcee3ea2` in the build it must no
  longer reach the group, and leaving it set is what proves that. This is R6.
- **A refused dial completes TLS before it is refused**, because telling the phone anything requires
  a channel. `TLS handshake complete with <ip>` on a refused dial is expected, not a regression.
- **The hotspot route still needs 5 GHz.** Below 5180 the session dies within seconds having sent no
  frame, and that is the band, not this branch.
- **`WifiVersionResponse ... status=NO_SUPPORTED_WIFI_CHANNELS(-8)` is inert.** Every capture on file
  shows it on sessions that go on to connect; nothing branches on it. Do not chase it.
- INFO is enough for every run here.

---

## 3. Preparation

Standing method as ever: `TESTING-TEMPLATE.md`, settings written to `shared_prefs/settings.xml` with
the app stopped, never through the UI. Diff `settings.xml` against a fresh backup at the start of
each part and state the delta even if zero.

**The access point must be started by hand before every hotspot-transport run**, and round 1 lost an
attempt to this. The app's own `SoftApCredentialsProvider` cannot bring it up:

```bash
adb shell cmd wifi start-softap Navegadortz2 wpa2 12345678 -b 5     # -b 5 is not optional
adb shell dumpsys wifi | grep -i SoftApInfo                          # record the frequency
adb shell dumpsys wifi | grep "Wi-Fi is"                             # station radio, before every part
```

That last line is not optional either: round 1 lost a part to a station radio that was off after a
SoftAP was stopped, and the phone's own unreachable-network retries then dropped the very record the
run depended on.

**R1, R3 and R6 (hotspot up, endpoint advertised):** `wifi-connection-mode=3`,
`native-ap-transport=1`, `native-wifi-version-exchange=true`, `hotspot-band=1`,
`auto-enable-hotspot=false`, plus this rig's known-working hotspot values: SSID `Navegadortz2`,
password `12345678`, `static-bssid=00:27:15:43:06:6a`, `hotspot-interface=wlan2`.

**R2 and R4 (the access point stays up; only the strategy moves):** `native-ap-transport=0`, and
**leave the access point running and the phone associated to it**. Confirm with `SoftApInfo` after
the restart that it is still up; if the switch takes it down on this ROM, say so and stop, because
the run cannot be scored without it.

Clear these five so the group's identity is genuinely not `STABLE`, which is what makes a dial
refusable. Round 1 did not, which is why it read `stable=yes`:

```
wifi-direct-last-group-ssid   wifi-direct-last-group-bssid   wifi-direct-group-name
wifi-direct-group-passphrase  wifi-direct-group-name-changes
```

Leave `static-p2p-bssid` unset (`0`) throughout. Do **not** clear the phone between R1 and R2: the
whole point is that it still holds R1's endpoint. Between R2 and R3, forget the head unit on the
phone and confirm the clean state, because R2 either cleared the record or proved it cannot, and R3
must start from neither.

---

## 4. The lines that decide every run

Head unit side, verified with `grep -F` against `fcee3ea2`.

**The endpoint went out (R1):**
```
NativeAA: advertising WPP over TCP at <ip>:5299
WppTcpServer: listening for Android Auto on TCP 5299
```

**The dial was refused and the phone was told so.** The point of R2:
```
WppTcpServer: rejecting this dial so the phone drops our endpoint and goes back to Bluetooth:
WppTcpServer: [TX] wrote type 10 (2 bytes)
```

**The refusal was swallowed because there was no route back.** The point of R4:
```
WppTcpServer: not serving this dial, and not withdrawing the endpoint because the Bluetooth listeners are not open for the phone to fall back to:
```

**The group is announced on its own address (R6), and never on the access point's:**
```
WifiDirectManager: onGroupInfoAvailable: ... BSSID: <addr> (source=IPv6 link-local)
WifiDirectManager: group identity ssid=... stable=
WifiDirectManager: nothing on this device reported the group's own address     <- stand-in; must NOT appear here
```

**Unchanged and still decisive:**
```
WppTcpServer: connection from <ip>
WppTcpServer: TLS handshake complete with <ip>
WirelessServer: Incoming connection detected from /<ip>
NativeAA: Connection accepted from
```

**Should not appear at all:**
```
WppTcpServer: session error:
```

Phone side, tag `GH.*`:
```
Trying to start WPP on TCP with configuration          <- the record is live
No WPP on TCP configuration found in storage for the head unit   <- the record is gone
Handling WifiConnectionRejection with reason           <- our type 10 arrived and parsed
Retrying connection attempt on all channels by restarting WPP
Attempting to connect Bluetooth RFCOMM                 <- the fallback this round is chasing
```
`Handling WifiConnectionRejection with reason` is the single most valuable line in the round. Quote
it with its reason, and quote the ten lines after it whatever they say.

---

## 5. Runs

### R0: build gate

`run_unit_tests.sh` on the coding host. Counts at `fcee3ea2`:

- `P2pBssidSourcePolicyTest` **5**, new with this commit
- `WppMessagesTest` 17, `WppTcpServePolicyTest` 9, `WppEndpointPolicyTest` 10,
  `WppHandshakeSessionTest` 30, `SoftApBssidPolicyTest` 17, all unchanged
- whole suite **2175 / 0**, up from 2170 by exactly the five new cases

Cleared on the coding host at `fcee3ea2`: `compileGithubDebugKotlin` clean, 2175 tests, 0 failures,
JDK 17.

### R1: poison the phone deliberately

Unchanged from round 1, which passed it. Settings per §3, access point up on 5 GHz, phone cleared of
this head unit first so the record R2 works on is the one this run created.

1. Connect, let projection run a minute.
2. `headunit://exit`, **confirm `AapService` is gone from `dumpsys activity services`**, then
   `am force-stop` and relaunch `MainActivity`, phone untouched. Round 1's naive three-second gap
   raced the app's own reconnect and produced a contaminated capture.
3. The reconnect must happen **without the phone opening RFCOMM**, which is what proves it is
   dialling the stored endpoint.

- **PASS**: `advertising WPP over TCP at <ip>:5299` at least once, a session on the first connect,
  and on the reconnect `WppTcpServer: connection from` with no `NativeAA: Connection accepted from`
  before it, plus the phone logging `Trying to start WPP on TCP with configuration`.
- **FAIL**: no endpoint advertised, or the reconnect runs over Bluetooth. Either means there is no
  stale record and **R2 cannot be scored**; say so and stop rather than running R2 anyway.

### R2: the measurement

Do not touch the phone, and **do not take the access point down**. Set `native-ap-transport=0`,
clear the five identity keys in §3, and restart the head unit.

1. Confirm with `dumpsys wifi | grep SoftApInfo` that the access point is still up and with the
   phone that it is still associated. If either is false, stop: §3 says why.
2. Leave it for three full dial attempts, about two minutes.
3. Quote every `WppTcpServer:` line in order, and the phone's `GH.*` lines across the same window.

- **PASS**: the rejection goes out, the phone logs `Handling WifiConnectionRejection with reason`,
  and then `No WPP on TCP configuration found in storage for the head unit` followed by an RFCOMM
  attempt and an ordinary session. The 17.5 reading is correct and the repair works.
- **PARTIAL**: the rejection goes out and the phone parses it, but it keeps dialling 5299 and never
  logs the missing-configuration line. **This is the 17.8 reading and it is a real result**, not a
  failed run. Quote the version and everything between the two, and the round has still answered its
  question.
- **FAIL**: no `rejecting this dial` line at all, or `session error:` in its place. That is the code,
  not the phone.
- **INCONCLUSIVE**: the phone never dials 5299 in the window. Say which of the two §3 preconditions
  did not hold; if both held, R1's record did not survive and R1 is redone.

**Isolate the two mechanisms.** Round 1 found, by accident, that a plain unreachable network on
17.8 was by itself enough to make the phone give up the stored endpoint, with the radio off and no
TCP interaction at all. That is why this run keeps the network reachable throughout: a clear here
is attributable to the rejection only because nothing else could have caused it. If the access
point drops at any point in the window, the run is INCONCLUSIVE regardless of what the phone did.

### R3: the regression that matters

Forget the head unit on the phone, confirm clean, and put the head unit back on the hotspot settings
of R1.

- **PASS**: an ordinary connect and reconnect, projection both times, and **zero**
  `rejecting this dial` lines in the whole run. A dial that would be served must never be rejected.
- **FAIL**: any rejection line, or a reconnect that no longer forms a session.

### R4: the stranding guard

Round 1 could not hold this run's precondition: `svc bluetooth disable` self-reverts on this unit in
about 14 s, and the transport switch calls `NativeAaHandshakeManager.start()` a second time behind
it, which opened the listeners anyway. Do not repeat that method.

Reach the state from the app instead. The guard asks `canRunRfcomm()`, which is
`isRunning && !aaListenersClosedForSession && !aaListenerLost`, and the listeners close **for the
session** once a Bluetooth handshake has completed. So: let a Bluetooth handshake complete, then run
R2's switch without restarting the app, so a dial arrives while the listeners are closed.

- **PASS**: the dial is refused, the `not withdrawing the endpoint because the Bluetooth listeners
  are not open` line appears, and **no** `[TX] wrote type 10`.
- **FAIL**: a type 10 goes out anyway.
- **Desk check instead**: if that state cannot be reached in one attempt, say so and stop. The
  decision itself is covered by `WppTcpServePolicyTest`, and burning the round on the setup costs
  more than it proves.

### R5: the banner, optional and desk-gradeable

After R2, on the head unit's main screen.

- **PASS**: one banner saying the phone keeps trying a network address this unit no longer uses,
  appearing once rather than once per dial, and gone after R3's first served dial.
- Not a blocking verdict. Report what was on screen.

### R6: the setting that cost round 1

Graded on the bring-ups R2 already needs, with `static-bssid` still set to the access point's
address per §3.

- **PASS**: every `onGroupInfoAvailable` line in the WiFi Direct window names a detected source
  (`IPv6 link-local`, `getGroupOwnerBssid()`, `sysfs / ip link`), **never** `static override` and
  never `access point setting (stand-in)`; the `group identity` line no longer reads
  `stable=yes (the static BSSID setting fixes the address the phone is told)`; and the address it
  names matches the `IPv6 link-local (<iface>)` row of the same capture's source dump.
- Also report whether the phone associates to the group at all on this build. Round 1 could not, and
  this is the run that says whether that is now repaired.
- **FAIL**: `source=static override` on a WiFi Direct group, or an announced address that does not
  match any detected row in the dump.

---

## 6. What a result here decides

A PASS on R2 makes the head unit able to repair a phone it poisoned, which no release has ever been
able to do, and retires "forget the head unit" as the only remedy. A PARTIAL still closes the
question the two decompiler passes left open, in the direction that says the clear is
version-dependent, and the code costs nothing either way because the refusal it replaces was
strictly worse. R6 is independent of all of that: it decides whether a setting that silently broke
every WiFi Direct bring-up on a unit that also runs a hotspot is fixed. Only a FAIL sends anything
back to the coding host.
