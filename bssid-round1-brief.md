# bssid-from-interface-address, round 1 brief: can a phone read its own BSSID at last

**Candidate:** `fork/fix/bssid-from-interface-address` @ `e6b19c3a`, six commits on `10bd1ea9`.
**Baseline:** `fork/fix/809-five-ghz-channel-choice` @ `10bd1ea9` — the candidate's own parent, not
`main`. Using the parent is the point: it isolates these six commits from the ten beneath them.

```bash
git fetch fork
git rev-parse fork/fix/bssid-from-interface-address    # e6b19c3a...
git rev-parse fork/fix/809-five-ghz-channel-choice     # 10bd1ea9...
```

| SHA | What |
|---|---|
| `c016149f` | The BSSID is derived from the interface's own IPv6 link-local address. **The point of the round.** |
| `bad30e9d` | WPP status codes are named in the log instead of printed as bare integers |
| `25a0b7a8` | Record renamed `AndroidAuto`; stand-in HFP record skipped where the adapter has one; AT responder rewritten |
| `51346963` | `body_type` no longer announced |
| `9e0db2b1` | The Android Auto record can be published insecure, off by default |
| `e6b19c3a` | Three recovered diagnostic fields read from the phone's replies |

**Two builds**, candidate and baseline. R2/R3 are a matched pair on the same hardware, so build both
before starting.

---

## 1. Why this round exists

Wireless mode 3 does not complete phone-to-phone. It stops in one identifiable place: the handshake
refuses to send credentials when it has no real BSSID, and a phone standing in for a head unit
cannot produce one. Every rung of the existing chain is a masked API, a shell-out or reflection, and
`getHardwareAddress()` has returned `02:00:00:00:00:00` to ordinary apps since Android 6.0.

`c016149f` adds the one source that is not blocked. `NetworkInterface.getInetAddresses()` was never
restricted, and where the kernel built an interface's IPv6 link-local address by the EUI-64 rule
that address **contains the MAC**: bytes 11 and 12 are the `ff:fe` marker, bytes 8-10 and 13-15 are
the six MAC bytes, and bit 1 of byte 8 is the flipped U/L bit.

Whether that is true of this rig's hardware is exactly what R1 settles, in one shell command and no
build. The marker check makes the route inert rather than wrong where it is not true, so the code
ships either way, but the answer decides how to read every other run.

**Read R1's answer before judging R2.** If neither phone has an EUI-64 link-local, R2 cannot pass
and is INCONCLUSIVE rather than FAIL, and that is a finding worth as much as a pass.

---

## 2. Devices and roles

Three devices, and this round needs all three.

| Role | Device | What it runs |
|---|---|---|
| **Head unit under test** | **D-MOTO** | the OHU build, wireless mode 3 |
| **Projecting phone** | **D-POCO** | Android Auto 17.5, nothing of ours |
| **Regression head unit** | **D-HU** | the OHU build, wireless mode 3 |

D-MOTO as the head unit and D-POCO as the phone, because D-POCO's Gearhead is the one every recent
round has driven and D-MOTO is the spare. **Swap them if D-MOTO cannot create a P2P group** and say
so in Setup notes; the round is still valid either way, and which phone played which role is a
result, not a detail.

The two phones must be **Bluetooth-paired to each other** before R2. Pair them by hand once, at the
start; the poke refuses an unbonded device by design, so an unpaired pair produces a silent round
that looks like a code failure. Confirm with `adb -s <D-POCO> shell dumpsys bluetooth_manager | grep -i bonded`.

D-HU stays on its own runs (R4, R5). It is not part of the two-phone rig.

---

## 3. Settings

Written to `shared_prefs/settings.xml` with the app stopped, per the template. Keys, with the values
this round wants:

| Key | Type | Value | Why |
|---|---|---|---|
| `wifi-connection-mode` | int | `3` | Native AA |
| `native-ap-transport` | int | `0` | WiFi Direct. **Not the hotspot arm** |
| `static-bssid` | string | `0` | **Must be the unset sentinel.** A usable override short-circuits the whole chain and the round measures nothing |
| `log-level` | int | `1` | DEBUG. The decisive lines are INFO, but the reader's own failure lines and the HFP traffic are DEBUG |
| `native-wifi-version-exchange` | bool | `false` | Default. Keeps the TCP endpoint out of this round |
| `insecure-aa-rfcomm-listener` | bool | `false` | Default, except in R6 |

`static-bssid` is the one that silently invalidates a run. Read it back after writing and quote the
value in the results.

---

## 4. R0 — build gate

Both builds, `assembleGithubDebug` and `testGithubDebugUnitTest`.

- Candidate: **1049 tests, 0 failures**. Baseline: **1006, 0 failures**, so the delta is **+43**.
  Both numbers were measured here, not computed.
- Four classes must be present by name in the candidate and absent from the baseline:
  `Eui64BssidPolicyTest` (15), `HfpAtResponderTest` (13), `HfpServiceRecordPolicyTest` (5),
  `WppStatusTest` (3). Two existing classes grow: `SoftApBssidPolicyTest` 13 to 17 and
  `WppMessagesTest` 11 to 14.
- Record both APK md5s.

A failed build gate is an escalation, per the template.

---

## 5. R1 — does the route exist on this hardware? (no build, no app)

The cheapest run in the round and the one that interprets the rest. Do it on **D-MOTO and D-HU**,
each with a P2P group up.

Bring a group up the way the app does, then read the interface:

```bash
# with the candidate installed and a mode-3 session attempted, or any P2P group present
adb -s <dev> shell ip addr show | grep -E '^[0-9]+: (p2p|ap|swlan|wlan)'   # name the real interface
adb -s <dev> shell ip -6 addr show                                          # all of them
adb -s <dev> shell ip -6 addr show dev p2p-wlan0-0                          # substitute the real name
```

Verdict, per device:

- **PASS (route exists)** — the link-local reads `fe80::xxxx:xxff:fexx:xxxx`, with `ff:fe` in the
  middle. Quote the address in full.
- **PASS (route absent)** — a link-local with no `ff:fe`, or no link-local at all. This is a real
  result, not a failure: the interface uses RFC 7217 stable-privacy addressing and the marker check
  is doing its job. Say which.

Quote the raw output either way. This is the only evidence that separates "the route returned
nothing" from "the code never ran", and it belongs in the results verbatim.

---

## 6. R2 — the point: two-phone Native AA on the candidate

D-MOTO runs the **candidate**. D-POCO drives Android Auto.

Before the run, on D-POCO, **forget this head unit in Android Auto** (Android Auto settings, the
head unit list). A stored record from an earlier round would let the phone skip the exchange this
run is measuring.

Capture on D-MOTO with `stdbuf -oL`, per the template. Capture D-POCO too if you can spare the
shell; a phone-side `GH.*` capture would be a bonus, not a requirement.

**PASS requires all three:**

1. The source dump appears, once per group:
   ```
   WifiDirectManager: == BSSID source dump (iface=...)
   ```
   with eleven `WifiDirectManager:   <label> = <value>` lines under it. **Quote the whole block.**
   It is the single most valuable artifact of this round and it says what every source on this
   hardware answered.

2. The chain resolves to a real address, from any rung, and names it:
   ```
   WifiDirectManager: onGroupInfoAvailable: SSID: DIRECT-..., BSSID: XX:XX:XX:XX:XX:XX (source=...)
   ```
   Quote the `source=` value. If it is `IPv6 link-local` the new route is what carried it, which is
   the hypothesis. Any other source is still a pass for this run and a finding for the write-up.

3. The phone gets onto the network:
   ```
   WirelessServer: Incoming connection detected from /192.168.49.x
   ```
   This is the line that matters. Everything before it can look healthy while the phone never
   arrives.

**FAIL** if this appears at all:
```
NativeAA: BSSID is still masked/empty (...) at Type 3 time
```
That is the abort this round exists to remove. Quote it with the source dump that preceded it.

Also record, whether or not it passes: whether a projection session followed (`SSL handshake
complete`, then throughput windows), and the frequency the group came up on. The 5 GHz commits
beneath this branch are in both arms, so any band behaviour is theirs and not this round's.

---

## 7. R3 — the same run on the baseline, same hardware

Identical to R2, on D-MOTO, with the **baseline** APK. Same settings, same forget-the-head-unit
step first.

**The expected result is a FAIL of the R2 conditions**, and that is the control: the baseline should
abort at `BSSID is still masked/empty` and never reach `Incoming connection detected`. If the
baseline also completes a session, then something other than these six commits is the variable and
R2's pass means nothing. Say so plainly if it happens.

Run R3 **after** R2, and forget the head unit on D-POCO again in between. Round 4 of the wpp-over-tcp
thread lost a run to a phone-side config cached during that same round.

---

## 8. R4 — the regression run on the real head unit

D-HU, **candidate**, mode 3, WiFi Direct, its usual phone. One ordinary session.

The chain was reordered and its masked-address tests were changed from two string comparisons to a
shape check, so this arm exists to prove a unit that already worked still does.

**PASS:** a session forms as it always has, and the source dump names whichever rung won. Quote the
`source=` value; on a real head unit `sysfs / ip link` answering is the expected outcome and the new
route contributing nothing is fine.

**Watch for one thing specifically.** The new shape check rejects any source answering with
something that is not an address at all, where the old test only rejected two exact placeholder
strings. If a rung that used to win now loses, the dump will show it, and that is a finding for the
write-up rather than a failure of the run.

---

## 9. R5 — the Bluetooth surface, observational

No separate run. Read these out of the R2 and R4 captures.

- **The service record name.** It is now `AndroidAuto` rather than `AA BT Listener`. Nothing logs
  it directly; if D-POCO shows a device name for the head unit anywhere in its Android Auto UI,
  note what it says.
- **The HFP record gate.** On D-HU, which has its own Bluetooth stack, expect:
  ```
  NativeAA: radio [...] already advertises Hands-Free, so the stand-in HFP record is not registered
  ```
  On D-MOTO, a phone, expect that line to be **absent** — a phone advertises Audio Gateway, not
  Hands-Free, so it still registers ours. Report which device printed it. Either way is a pass; the
  point is which.
- **The AT responder.** Likely **UNTESTABLE**. Five previous rounds and every reporter log have
  produced no HFP accept at all, so the responder may simply never run. If `NativeAA: HFP connection
  accepted` does appear, quote every `HFP RX:` and `HFP TX:` pair that follows; that would be the
  first observation of this path on hardware.

---

## 10. R6 — the insecure record, only if R2 did not pass

Skip this entirely if R2 passed. It is a security trade, not an improvement, and there is no reason
to measure it against a working configuration.

If R2 failed at the Bluetooth stage rather than the BSSID stage — the phone never connected back
over RFCOMM at all — re-run R2 with `insecure-aa-rfcomm-listener` set to `true` on D-MOTO.

Expect at start-up:
```
NativeAA: publishing the Android Auto record as insecure, at the user's request.
```

**Set it back to `false` afterwards** and say in Setup notes that you did.

---

## 11. R7 — harvest the new diagnostics, opportunistic

Nothing to set up. Grep every capture from this round:

- Status names. Any `status=` on a `[RX]` line now reads `NAME(n)`, for instance
  `status=WIFI_INCORRECT_CREDENTIALS(-3)` or `status=SUCCESS(0)`. Quote every distinct one seen.
  A `status=unknown(n)` is the most interesting possible result: it means a code outside the twelve
  recovered names, and the number is worth having.
- `WifiConnectStatus ... hint="..."` — the phone's own words for a refusal. Never observed before;
  it may never appear.
- `WifiStartResponse ip=...:PORT` — a port on field 2, likewise never observed.
- `WifiVersionResponse ... device=... lifetime=...` — field 6.

Absence of all three is an expected and reportable result. Presence of any is new information about
the protocol.

---

## 12. What this round cannot settle

Say so in the results rather than stretching a verdict:

- Whether the derived MAC is the BSSID the group actually **beacons**. The log proves the app read a
  plausible address, not that it is the right one. **If a third device is free**, any WiFi analyser
  app reads the BSSID of the `DIRECT-*` SSID directly, and comparing it to the logged value settles
  it in seconds. That comparison is worth more than another session attempt, so do it if you can.
- Whether an insecure record helps, unless R6 ran.
- Whether the AT responder's new replies are correct on the wire, unless an HFP accept happened.
