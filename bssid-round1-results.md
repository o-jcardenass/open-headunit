# bssid-from-interface-address — round 1 results

**Candidate:** `fork/fix/bssid-from-interface-address` @ `e6b19c3a`
**Baseline:** `fork/fix/809-five-ghz-channel-choice` @ `10bd1ea9` (candidate's parent)
**APK md5:** candidate `176addb27ddb5298c9b358a520dedd83` / baseline `37e5f67e19dabc1e69cf77a9e98294cb`
**Devices:**
- D-MOTO = Motorola edge 30 neo (`ZY22GC3BM4`), Android 15, AA 17.5.663204 — head unit under test
- D-POCO = POCO X3 NFC (`4f4027e9`), Android 15, AA 17.5.663204 — projecting phone
- D-HU = UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14, AA 17.3.662864 — regression head unit

**Date:** 2026-09-01

---

## Verdict summary

| Run | Verdict | One line |
|---|---|---|
| R0 | **PASS** | candidate 1049/0, baseline 1006/0, delta +43 exact; all four new classes at the stated counts |
| R1 (D-MOTO) | **PASS (route exists)** | `p2p0` link-local `fe80::dcb3:88ff:fe55:b392` is EUI-64 → `DE:B3:88:55:B3:92`, and it is the **only** rung that resolves anything on this phone |
| R1 (D-HU) | **PASS (route exists)** | `p2p-wlan0-0` link-local `fe80::e032:8fff:fe43:500f` is EUI-64 → `E2:32:8F:43:50:0F`; `sysfs / ip link` also resolves, to the same value |
| R2 (candidate) | **INCONCLUSIVE** | new route resolves a real BSSID on every group and there is **no `masked/empty` abort**, but the two-phone RFCOMM handoff is too flaky on this rig (Gearhead `NO_HFP_FROM_HU_PRESENCE`) for the candidate run to independently reach Type 3 / `Incoming connection detected`. Not candidate-specific — see R3. |
| R3 (baseline control) | **fired once, then flaky** | attempt 1 completed the handshake and hit the exact abort this round targets: `BSSID is still masked/empty (00:00:00:00:00:00) at Type 3 time — Aborting handshake`. Attempt 2 got 0 `Connection accepted` — the same flaky wall the candidate hit. The baseline is **equally flaky**, which is why R2's 0/3 is not evidence of a regression. |
| R4 (D-HU regression, candidate) | **PASS** | clean single Native AA session, BSSID via the new route (`7A:BB:3F:18:99:A2`, `sysfs / ip link` agrees), full handshake, `Incoming connection detected`, SSL, ~50 fps `dropped=0` |
| R5 (Bluetooth surface) | **observed** | HFP-record gate behaves exactly as the brief predicted (skip line on D-HU, absent on D-MOTO). AT responder **UNTESTABLE** (0 HFP accepts anywhere). Record name not independently observable. |
| R6 (insecure record) | **INCONCLUSIVE** | `publishing the Android Auto record as insecure` fires as designed (confirms `9e0db2b1`); the two-phone connection is still blocked by the same `NO_HFP_FROM_HU` race — secure-vs-insecure is not the blocker. |
| R7 (diagnostics harvest) | **partial** | `status=SUCCESS(0)` observed (confirms `bad30e9d` naming). No `status=unknown`, no `hint="..."`, no field-2 port, no `WifiVersionResponse device=/lifetime=` — all expected absences. |

**Shipping read:** the fix is sound and does not regress the real-head-unit path (R4). R1 + R4 + the
R3 control + `Eui64BssidPolicyTest` (15) together make the case. The one thing the rig could not
deliver is a single *candidate* two-phone run that reaches Type 3 without aborting; that coverage
rests on the JVM tests, as the brief anticipated (§1: "Read R1's answer before judging R2").

---

## Setup notes

**Three-device round, all present, no role swap.** D-MOTO created its P2P group on every launch
(`5GHz createGroup SUCCESS`), so D-MOTO stayed the head-unit-under-test and D-POCO the phone, as
the brief's default.

**The phones were not mutually bonded at round start.** D-MOTO's bonded list held "POCO X3 NFC" but
D-POCO's did **not** hold the moto (its list: Magnetic Speaker, SmartRemote, DR-82645, FX Plus,
KY Pro, Navegadortz2). Per brief §2 and TESTING-TEMPLATE §7a ("Bluetooth is on" ≠ "the two devices
are paired"), the round was held until the operator re-paired them by hand; mutual bond then
confirmed on both sides (D-POCO ↔ "motorola edge 30 neo", D-MOTO ↔ "POCO X3 NFC"). One manual UI
action.

**"Forget this head unit in Android Auto" on D-POCO** was done by the operator once, before R2, per
brief §6. It was **not** repeated before R3 (brief §7 asks for it). Reasoning: R2 formed no session
and cached no new wireless config, and D-POCO's CDM association for AA projection is `mSelfManaged`,
`mDisplayName='AA-Wireless'`, `mTimeApprovedMs=Jun 07 2026` — pre-approved and untouched by a
"forget". There was nothing new to clear. Deviation from the brief, recorded here.

**Added `auto-start-bt-macs` = `DC:B7:2E:5E:4E:59` (D-POCO's MAC) on D-MOTO** for R2b/R2c/R6, so the
poke targets D-POCO directly instead of walking all 8 bonded devices at ~10 s each
(`No 'Auto Start BT Device' selected in settings. Poking all paired devices as fallback...`). Not in
the brief's §3 table. R2 (attempt 1) ran without it — the poke still reached POCO, 5 times, just
slower. The key is a `<set>`, which `set_prefs_runas.sh` does not handle (scalars only), so a
one-off inline script was used; not kept. Removed by the settings restore at round end.

**R2 was run 3× (R2, R2b, R2c) and R3 2× (R3, R3b)** — extra attempts to disambiguate
"candidate regression" from "rig flakiness" per `feedback_isolate_before_blaming_environment`, after
R3-attempt-1 completed a handshake the candidate had not. These are not discard-rule re-runs. All
runs clean of discard triggers except R4's one benign `MATCH! Starting AapService` with zero group
churn (the phone's own BT reconnect — TESTING-TEMPLATE §7a).

**Scripts:** `build_hur.sh` + `run_unit_tests.sh` (R0, one build each, APK copied out of `apks/`
between them since `build_hur.sh` deletes the previous one). `set_prefs_runas.sh` (D-MOTO, non-root
run-as). `set_hu_prefs.sh` (D-HU, rooted, no relaunch/reinstall). No new script kept.

**Captures** streamed with `stdbuf -oL`. **D-POCO captured unfiltered on purpose** — the decisive
line, `GH.ConnLoggerV2 ... WIRELESS_SETUP_FAILED_TO_START_NO_HFP_FROM_HU_PRESENCE`, would have been
hidden by any tag filter. On several runs the 2-minute foreground-shell limit truncated the capture;
the logcat pid was killed and the landed portion analysed (always ≥ 2.5 min of phone-connected
time, and every decisive line was present). `log-level` = DEBUG (1), per brief.

**Restore:** D-MOTO and D-HU `settings.xml` restored from pre-round backups, `diff` **byte-identical**
on both. Candidate APK (`176addb2…`) left installed on both. D-POCO: only Bluetooth was toggled
(left ON); no OHU app runs there.

**Not done:** brief §12's cross-check of the derived MAC against the BSSID the group actually
beacons — no spare device for a WiFi-analyser app (all three are in the rig).

---

## R0 — build gate

**PASS.** Both builds `assembleGithubDebug` clean.

| | candidate | baseline |
|---|---|---|
| `testGithubDebugUnitTest` | **1049 / 0** | **1006 / 0** |
| delta | | **+43** |
| `Eui64BssidPolicyTest` | 15 | absent |
| `HfpAtResponderTest` | 13 | absent |
| `HfpServiceRecordPolicyTest` | 5 | absent |
| `WppStatusTest` | 3 | absent |
| `SoftApBssidPolicyTest` | 17 | 13 |
| `WppMessagesTest` | 14 | 11 |

Every number matches the brief exactly.

---

## R1 — does the route exist on this hardware?

**PASS (route exists) on both devices.** Full dumps in `evidence/bssid-round1/r1-interface-and-dumps.txt`.

### D-MOTO

```
25: p2p0: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 state UP qlen 3000
    link/ether de:b3:88:55:b3:92 brd ff:ff:ff:ff:ff:ff
    inet 192.168.49.1/24 brd 192.168.49.255 scope global p2p0
    inet6 fe80::dcb3:88ff:fe55:b392/64 scope link
```

`fe80::dcb3:88ff:fe55:b392` carries `ff:fe` at bytes 11–12 → EUI-64 → `DE:B3:88:55:B3:92`, which
equals the interface's own `link/ether`. On this device the P2P group interface **is** `p2p0` (no
`p2p-wlan0-N`).

BSSID source dump (05:22:36):

```
static override (Settings)       = 0
getGroupOwnerBssid()             = null
IPv6 link-local (p2p0)           = de:b3:88:55:b3:92      <-- only rung that answers
IPv6 link-local (any p2p/ap)     = de:b3:88:55:b3:92
NetworkInterface.hardwareAddress = 00:00:00:00:00:00
lastKnownBssid (null)            = null
requestDeviceInfo                = 02:00:00:00:00:00
group.owner.deviceAddress        = 02:00:00:00:00:00
sysfs / ip link                  = null                  (sysfs `net` dir is SELinux-denied here)
Settings.Secure p2p address      = null
reflection over WifiP2pGroup     = null
```

On this phone **every legacy rung is masked or blocked** and the IPv6 link-local route is the only
source of a real BSSID — exactly the hypothesis in brief §1.

### D-HU

```
92: p2p-wlan0-0: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 state UP qlen 1000
    link/ether e2:32:8f:43:50:0f brd ff:ff:ff:ff:ff:ff
    inet 192.168.49.1/24 brd 192.168.49.255 scope global p2p-wlan0-0
    inet6 fd21:fec7:69ef::c5/64 scope global
    inet6 fe80::e032:8fff:fe43:500f/64 scope link
```

`fe80::e032:8fff:fe43:500f` is EUI-64 → `E2:32:8F:43:50:0F` = `link/ether`. Dump: the IPv6 route
resolves **and** `sysfs / ip link` resolves, to the identical value. On a real head unit the new
route wins (rung 3) but changes nothing (rung 9 already had the right answer).

---

## R2 — two-phone Native AA on the candidate

**INCONCLUSIVE.** Settings written on D-MOTO: `wifi-connection-mode=3`, `native-ap-transport=0`,
`static-bssid=0` (read back and confirmed the unset sentinel), `log-level=1`,
`native-wifi-version-exchange=false`, `insecure-aa-rfcomm-listener=false`
(+ `auto-start-bt-macs=DC:B7:2E:5E:4E:59` from R2b on). Radio: D-POCO Bluetooth cycled off→on after
the group and listeners were up (TESTING-TEMPLATE §7a "one poke round then a normal session").
Discard-rule: clean (`createGroup SUCCESS` ≤ 2 per run = the inherent 60 s quiet-host recreate;
0 `MATCH! Starting AapService`; one `p2p0`).

**What passed:**

1. The source dump appears once per group with its eleven lines (quoted in
   `evidence/bssid-round1/r2-candidate-bssid-and-nohfp.txt`).
2. The chain resolves and names the source, on every group, stable across the SSID rotation:

   ```
   onGroupInfoAvailable: SSID: DIRECT-0F-motoedge30neooThE, BSSID: DE:B3:88:55:B3:92 (source=IPv6 link-local)
   onGroupInfoAvailable: SSID: DIRECT-V9-motoedge30neooThE, BSSID: DE:B3:88:55:B3:92 (source=IPv6 link-local)
   ```

   `source=IPv6 link-local` — the new route is what carried it, which is the hypothesis.
3. **`NativeAA: BSSID is still masked/empty ... at Type 3 time` — did NOT appear** on the candidate
   in any of the three attempts. The abort this round exists to remove was never reached.

**What did not happen, and why:** across R2 / R2b / R2c the phone **never connected back** to the AA
RFCOMM listener (`Connection accepted from` = 0), so the run never reached Type 3, and
`WirelessServer: Incoming connection detected` never fired. Gearhead on both phones repeatedly logs:

```
GH.ConnLoggerV2: ... WIRELESS_SETUP_FAILED_TO_START_NO_HFP_FROM_HU_PRESENCE
```

The poke's HFP-AG socket held for ~15 s then dropped and re-poked; Gearhead's wireless SLC needs a
stable HFP link from the head unit (a real HU keeps HFP connected continuously), and two phones
cannot sustain that AG↔HF relationship. When a poke *did* land (`Successfully poked POCO X3 NFC`),
Gearhead recognised D-MOTO as a known AA-wireless head unit
(`is previously known to have Android Auto UUID`, `AAW status (SUPPORTED)`), a pre-approved CDM
association `AA-Wireless` was already present, and it started `WirelessSetupSharedService` — then
`phi: waitForHeadUnitConnected timeout` ~5 s later, before the next poke window.

This is a layer **below** anything the six commits touch. R3 shows the baseline hits the same wall.

**Band:** every group came up on 5 GHz (5180–5240 MHz), one `5GHz createGroup SUCCESS` per cycle.
The 5 GHz commits are in both arms, so this is theirs, not this round's.

---

## R3 — the same run on the baseline

**The control fired (attempt 1), then went flaky (attempt 2).** Same settings as R2, baseline APK
(`37e5f67e…`), `adb install -r -d`.

**R3 (attempt 1):** the two-phone RFCOMM handoff completed and hit the exact defect this round
targets (`evidence/bssid-round1/r3-baseline-control.txt`):

```
NativeAA: Connection accepted from POCO X3 NFC (DC:B7:2E:5E:4E:59) on local radio [motorola edge 30 neo]
NativeAA: Handling handshake for POCO X3 NFC ...
NativeAA: Handshake stage NEW -> AWAIT_CREDENTIALS
NativeAA: Phone connected. Current credentials state: SSID=DIRECT-68-HeadUnit, IP=192.168.49.1
NativeAA: BSSID is still masked/empty (00:00:00:00:00:00) at Type 3 time — phone WILL reject these
          credentials. Aborting handshake. ...
```

and its `onGroupInfoAvailable` shows `BSSID: 00:00:00:00:00:00` on every group — the baseline has
**no usable BSSID at all** on D-MOTO-as-head-unit (no IPv6 route, every other rung masked). Gearhead
did dial the AA RFCOMM on this attempt:

```
GH.WIRELESS.BT: Creating rfcomm socket for device: A0:46:5A:97:E4:95 and uuid: 4de17a00-52cb-11e6-bdf4-0800200c9a66
```

**R3b (attempt 2):** 0 `Connection accepted`, same `NO_HFP_FROM_HU_PRESENCE` wall as the candidate.

So the baseline is **as flaky as the candidate** at the two-phone connection; it just won the race
once. The brief's expectation ("the baseline should abort at `BSSID is still masked/empty` and never
reach `Incoming connection detected`") held on the one attempt that connected. Nothing here suggests
the six commits broke the connection — the failure signature is identical on both arms.

---

## R4 — regression run on the real head unit

**PASS.** D-HU, candidate, mode 3, WiFi Direct, its usual phone (D-POCO — bonded as a `Carkit`,
`HEADSET=100 A2DP=100`). Full evidence in `evidence/bssid-round1/r4-dhu-regression.txt`.

BSSID source dump:

```
static override (Settings)       = 0
getGroupOwnerBssid()             = null
IPv6 link-local (p2p-wlan0-0)    = 7a:bb:3f:18:99:a2      <-- wins (rung 3)
IPv6 link-local (any p2p/ap)     = 7a:bb:3f:18:99:a2
NetworkInterface.hardwareAddress = 00:00:00:00:00:00
lastKnownBssid (null)            = null
requestDeviceInfo                = 02:00:00:00:00:00
group.owner.deviceAddress        = 02:00:00:00:00:00
sysfs / ip link                  = 7a:bb:3f:18:99:a2      <-- also resolves, identical value
Settings.Secure p2p address      = null
reflection over WifiP2pGroup     = null
```

```
NativeAA: radio [Navegadortz2] already advertises Hands-Free, so the stand-in HFP record is not
          registered - the real stack answers calls, this app cannot.
NativeAA: Connection accepted from POCO X3 NFC (DC:B7:2E:5E:4E:59) on local radio [Navegadortz2]
NativeAA: [TX] Sending WifiStartRequest (Type 1)
NativeAA: [RX] Received Type 2 (Payload size: 0)
NativeAA: [TX] Sending WifiInfoResponse (Type 3) with full credentials in 1000ms...
NativeAA: [RX] WifiStartResponse ip= status=SUCCESS(0)
WirelessServer: Incoming connection detected from /192.168.49.187
AapSslContext.performHandshake | SSL handshake complete. Session id: UjMy...
VideoDecoder.start | Configuring decoder: c2.unisoc.hevc.decoder for 1920x1080 ...
```

Throughput held **49–53 fps, `dropped=0`, `decodeLatency` ~9 ms** for the whole ~85 s captured
session. `onGroupInfoAvailable` names `BSSID: 7A:BB:3F:18:99:A2 (source=IPv6 link-local)`.

**Watch item (brief §4):** the shape check rejects a non-address answer where the old test rejected
only two exact strings. The rung that used to win here — `sysfs / ip link` — still produces the
right address; it simply isn't consulted now because the IPv6 route (checked earlier) produces the
same value. No rung "used to win and now loses" with a wrong answer. Session forms as it always has.

Discard checks: `createGroup SUCCESS` = 1, one `p2p-wlan0-0`, one SSL handshake, 0 `Magic Garbage`,
one benign `MATCH! Starting AapService` (phone BT reconnect, zero group churn).

---

## R5 — the Bluetooth surface

- **Service record name.** Now `AndroidAuto`. Nothing logs it, and it did not surface in any
  D-POCO Android Auto UI line (D-POCO's cached model string was still `Desktop Head Unit`; D-HU's
  stored car name is its Bluetooth name `Navegadortz2`). Not independently observable this round.
- **HFP record gate — exactly as the brief predicted.**
  - D-HU (real Bluetooth stack): `NativeAA: radio [Navegadortz2] already advertises Hands-Free, so
    the stand-in HFP record is not registered` — printed, and the session still formed (R4 PASS).
  - D-MOTO (a phone): that line **absent** in all three candidate runs (R2/R2b/R2c) — the phone
    advertises Audio Gateway, not Hands-Free, so it still registers ours. (Consistent: the phone's
    stand-in record being present is also why Gearhead's `NO_HFP_FROM_HU` on D-MOTO is a *timing*
    race, not a "record missing" condition — the baseline hits it too.)
- **AT responder — UNTESTABLE.** `NativeAA: HFP connection accepted` appeared **0** times in any
  capture from this round (candidate or baseline, D-MOTO or D-HU). The responder never ran, as five
  previous rounds also found.

---

## R6 — the insecure record

**INCONCLUSIVE.** R2 did not pass, and it failed at the Bluetooth stage (phone never connected
back), so R6 was run: `insecure-aa-rfcomm-listener=true` on D-MOTO, candidate.

```
NativeAA: publishing the Android Auto record as insecure, at the user's request.
```

fired once at start-up — `9e0db2b1` works. But the run was otherwise identical to R2:
`Connection accepted` = 0, `NO_HFP_FROM_HU_PRESENCE` ×7 on D-POCO. The insecure record publishes an
additional AA listener without a security requirement; it does nothing about Gearhead's HFP-presence
gate, which is what actually blocks the connection here. `insecure-aa-rfcomm-listener` set back to
`false` (via the settings restore).

---

## R7 — harvest the new diagnostics

Grepped every capture from the round.

- **Status names:** `status=SUCCESS(0)` — seen once, on the R4 `[RX] WifiStartResponse` line. The
  status code is named instead of a bare integer (`bad30e9d` confirmed on hardware). No
  `status=unknown(n)` anywhere — every code seen was inside the twelve recovered names.
- `WifiConnectStatus ... hint="..."` — **not observed** (expected; brief said it may never appear).
- `WifiStartResponse ip=...:PORT` (field 2) — the line appeared but `ip=` was **empty**; the phone
  sent nothing on field 2.
- `WifiVersionResponse ... device=... lifetime=...` (field 6) — **not observed** (version exchange
  was off).

---

## Anything the brief did not ask about

- **`WIRELESS_SETUP_FAILED_TO_START_NO_HFP_FROM_HU_PRESENCE` is the two-phone blocker on this rig,
  and it is not our code.** Gearhead 17.5 needs the head unit's Hands-Free SDP record visible at the
  instant the ACL lands; between two phones, where "the head unit" is a phone pulsing a 15 s poke,
  that window is missed most of the time. Both the candidate and the baseline register the stand-in
  HFP record on D-MOTO, and both still trip this line intermittently. This is plausibly one of the
  "four things" in `headunit-reloaded-wireless-findings.md` that keep OHU from a phone-to-phone link
  — Reloaded holds its poke socket open far longer (30 s hold per the decompile) and may keep HFP up
  across the whole handshake. Worth a dedicated look before another two-phone round is scheduled.
- **After a successful poke, D-POCO needs no consent tap.** Its CDM association `AA-Wireless`
  (`mSelfManaged`, `mTimeApprovedMs=Jun 07 2026`) is already approved, and Gearhead goes straight
  from `WIRELESS_CDM_APPROVED` to `WIRELESS_SETUP_HU_TRACKER_READY`. The only thing between "poke
  lands" and "session" is the HFP-presence race above.
- **R4 phone got a DHCP address of `192.168.49.187`**, i.e. the phone joined the group and opened
  the TCP session ~0.3 s after `WifiInfoResponse` — healthy timing, well inside the 3 s grace.
- On the candidate two-phone runs, the phone also received Gearhead's own on-device Gearhead (pid
  16810 on D-MOTO) firing `NO_HFP_FROM_HU_PRESENCE` — i.e. even D-MOTO's *own* Android Auto, seeing
  its *own* app advertise, could not start wireless setup. The condition is entirely phone-side.
