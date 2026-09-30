# bring-up-status-pill-and-poke-readiness — round 2 results

**Candidate:** `fork/feat/bring-up-status-pill-and-poke-readiness` @ `ef66abbf` (same SHA as round 1; this
round re-runs round 1's brief in full, not a new brief)       **Baseline:** none (see brief §1)
**APK md5:** `222016d536d46c712bf769e0fd255663` (`com.andrerinas.headunitrevived_3.4.0-beta1_debug.apk`,
versionCode 106) — identical to round 1's build, confirming a deterministic build from the same SHA.
**Unit:** D-HU = UNISOC MT50 (`MT50_YT610E4GFPSL_U`, Android 14, single BT radio, adbd root), bonded
name "Navegadortz2" `11:46:03:10:33:59`. D-POCO = POCO X3 NFC (`M2007J20CG`, Android 11, not rooted,
debuggable OHU) `DC:B7:2E:5E:4E:59`. D-MOTO = motorola edge 30 neo (`miami`, not rooted)
`A0:46:5A:97:E4:95`.
**Date:** 2026-09-10

## Setup notes

### Scripts used (`hur-wifi-test-scripts/`, a sibling dir, not this repo)
- `build_hur.sh`, `run_unit_tests.sh` (R0). `set_hu_settings_host.py` (D-HU, rooted).
  `set_hu_settings_runas.py` (D-POCO, run-as). No new script was needed.

### Why this round exists
Round 1 (`bring-up-status-pill-and-poke-readiness-round1-results.md`, `a2ca4a41e`) already fully
graded this exact SHA against this exact brief: 13 PASS, 1 FAIL (P3), 1 pre-registered INCONCLUSIVE
(P6). This round is a deliberate full re-run of the same brief against the same SHA, requested to
confirm reproducibility and because the D-HU unit had been disconnected from the rig host between
sessions. No code changed between rounds.

### Deviations / new findings this round (not in round 1)

1. **D-HU logd clock freeze did not recur.** Round 1 noted ~25s of frozen wall-clock timestamps on
   every D-HU capture; this round's P1/P3/P5 captures show clean monotonic timestamps throughout, no
   freeze window. Timing figures below are direct wall-clock, not line-order bounds.

2. **A stray bonded Bluetooth device, "FX Plus" (`D0:D9:4F:C2:C7:1E`), is a two-way radio /
   intercom, not a phone**, and it interfered with every Part B run the same way round 1's stray
   Galaxy S24+ did — but this is a different, more informative case, flagged mid-round by the user
   after observing OHU attempt to connect to it. Root-caused against the current classifier
   (`DriverCandidatePolicy.classify()`, `app/src/main/java/.../connection/wifi/modes/nativeaa/DriverCandidatePolicy.kt:67-97`,
   already in `main` via `289595df`, i.e. predates this branch):
   - FX Plus's Bluetooth class is `0x001F00` (`adb shell dumpsys bluetooth_manager`), whose
     major-device-class field (`0x1F`) is the Bluetooth SIG's own **Uncategorized** value — not a
     value the round-4 classifier fix rules out (`NON_PHONE_MAJORS` covers
     Wearable/Peripheral/Imaging/Health/Toy only; Uncategorized is none of those).
   - FX Plus advertises the Hands-Free Audio Gateway record (it answered our poke's HFP-AG/HSP-AG
     `socket.connect()` attempts, same as a real phone would), so `classify()`'s fallback rule
     (`if (gateway) return PHONE`, line 84) applies: an unclassifiable device that advertises the
     Audio Gateway record is treated as a phone by design.
   - Effect observed: `driver candidates: 2 phone` (FX Plus + D-MOTO) every run; FX Plus never
     appears in the `hidden:` list (Navegadortz2/BC8-Android/Magnetic Speaker do, correctly). The
     driver selector auto-connected to FX Plus as the "Unambiguous driver" in W1/W1b/W2/D1 and issued
     3-6 manual pokes to it per run (all `read failed`, since it isn't a real AA phone), and the
     "poke all paired phones" fallback also reached it.
   - **This is a real, reproducible gap distinct from round 4's fix**, not something `ef66abbf`
     introduced (the code predates this branch). It did not affect the graded PASS/FAIL conditions
     below (all of which are keyed to D-MOTO or D-HU by name/MAC), but it is worth a follow-up: the
     classifier's "unclassifiable + gateway record = phone" fallback should not extend to a major
     class of literally "Uncategorized" the way it currently does. See recommendation in "Anything
     the brief did not ask about" below.

3. **WiFi Direct group churn correlated with FX Plus's presence.** Round 1's discard-rule bar was
   `createGroup SUCCESS=1` per run (clean). This round measured 2-3 in several Part B/C runs (W1: 3,
   W1b: multiple after the discard-relevant `MATCH!`, D1: 2), all with `MATCH! Starting AapService=0`
   in the same runs — so none of these are the self-wake loop from `AutoStartReceiver`. Every extra
   `createGroup SUCCESS` line coincided with FX Plus connect/disconnect cycling nearby. Did not change
   any run's substantive verdict (the decisive lines for D-MOTO/D-HU were unaffected), but the
   discard-rule bar of exactly 1 does not hold on this rig session with a live stray gateway-class
   accessory present. Recorded per-run below rather than re-derived each time.

4. **D2/D3 blocked by a D-HU network condition, not a code defect: reported INCONCLUSIVE.** With
   D-HU as head unit, `WirelessServer` bound port 5288 to the IPv6 wildcard only in two consecutive
   fresh app-process launches (`adb shell cat /proc/net/tcp6` showed `[::]:5288 LISTEN`;
   `/proc/net/tcp` had no entry at all). D-POCO joined the P2P group correctly both times (got IP
   `192.168.49.50`/`.50`, `ping 192.168.49.1` succeeded at ~0.1ms, `Requesting package name:
   com.google.android.projection.gearhead` confirms Gearhead did the join), but its IPv4 TCP
   connection to `192.168.49.1:5288` was actively refused (`nc -z` → `Connection refused`) both
   times, so `WirelessServer: Incoming connection detected` never printed and no session could form.
   Correlated condition: D-HU's own station WiFi (`wlan0`) was concurrently associated to its home
   network "Pegue Cdesta" as `IsPrimary: 1` while the P2P group ran as a secondary interface — the
   same STA+P2P multi-channel-concurrency state noted as a throughput cost in `five-ghz-channel
   round 1` (433→6 Mbit/s), here suspected (not proven) as also affecting which interface the
   listening socket's connections resolve on. P1 (also D-HU-as-head-unit, earlier in this same
   session) worked cleanly, and P1 was not checked for whether D-HU's STA WiFi was associated at
   that time — so this is not proven to be new since round 1, only newly observed. Evidence saved:
   `evidence/d2-untestable-wifi-state.txt`, `-tcp6.txt`, `-tcp4.txt`. Not chased further mid-round
   (would mean forgetting the "Pegue Cdesta" network, a persistent rig-wide WiFi config change
   outside this round's scope) — flagged as a follow-up instead.

## R0 — build and unit-test gate

**PASS**

- Build: `build_hur.sh` → `BUILD SUCCESSFUL`, apk md5 `222016d536d46c712bf769e0fd255663` — identical
  to round 1's build (deterministic from `ef66abbf`).
- Unit tests: `run_unit_tests.sh` → **1616 tests, 0 failures, 0 errors, 0 skipped**.
- Install: `adb install -r` on D-HU and D-POCO; live `md5sum` of `pm path` base.apk matches on both.
- Identity: `ACTION_QUERY_STATE` on both → `"commit":"ef66abbfac12"`.

---

## Part A — status pill, D-HU head unit, D-POCO phone

### P1 — Native AA cold bring-up to projection

**PASS**

- Settings (D-HU): `wifi-connection-mode=3`, `log-level=2`, `auto-start-bt-macs` empty,
  `native-preferred-device-mac` deleted.
- Radio state: D-POCO airplane-on + `svc wifi disable` before launch (21:42:19); D-HU launched BT
  ON / WiFi up; D-POCO radios restored at 21:43:16 (~57s post-launch, verified ON).
- Discard-rule check: clean — `MATCH!`=0, `createGroup SUCCESS`=1, `SSL handshake complete`=1,
  `Magic Garbage`=0.

Ordered `status pill step:` values (direct wall-clock, no clock-freeze this run):

| # | time | value | rank | note |
|---|---|---|---|---|
| 1 | 21:42:20.575 | ARMED | 10 | first value |
| 2 | 21:42:20.643 | PREPARING_NETWORK | 20 | |
| 3 | 21:42:20.906 | WAITING_FOR_PHONE | 40 | |
| — | 21:42:20.922 | — | — | `startNativeAaQuietHost() requested` |
| 4 | 21:42:20.924 | PREPARING_NETWORK | 20 | **the one rank decrease — 2ms after the requested line, the allowed exception** |
| 5 | 21:42:21.104 | WAKING_PHONE | 45 | |
| 6 | 21:43:23.605 | PHONE_ANSWERED | 50 | |
| 7 | 21:43:24.059 | SENDING_CREDENTIALS | 55 | |
| 8 | 21:43:25.073 | PHONE_JOINING | 60 | |
| 9 | 21:43:29.681 | CONNECTING | 70 | |
| 10 | 21:43:29.802 | SECURING | 80 | |
| 11 | 21:43:29.971 | STARTING_PROJECTION | 90 | last value |

- Count 11 (≥6 ✓). First=ARMED, last=STARTING_PROJECTION. `WAITING_FOR_PHONE` precedes
  `WAKING_PHONE` ✓. Rank monotonic apart from the one allowed decrease ✓. `SSL handshake complete`
  at 21:43:29.966 ✓.
- Poke retry loop reached `pokeDevice()` three times while D-POCO was unreachable (21:42:21.157,
  21:42:36.736, 21:43:22.579), then `Successfully poked POCO X3 NFC via HFP-AG` (21:43:23.426, 7.4s
  after D-POCO's radios came back) → `Connection accepted` (21:43:23.601, +0.175s) → `SSL handshake
  complete` (21:43:29.966, +6.365s). Wall-clock first pill → `STARTING_PROJECTION`: 69.4s, of which
  ~57s is the deliberate D-POCO-radios-off wait; actual phone-join work (poke success → projection)
  ≈ 6.5s.

### P2 — the pill does not cover the home controls

**PASS**

- Setup: D-HU mode 3, D-POCO BT+WiFi OFF (no phone). `uiautomator dump` + `screencap` ~10s after
  launch; pill showed `WAKING_PHONE` ("Android Auto is starting… / Waking your phone") — same as
  round 1, the pill keeps advancing past `ARMED` on this rig with no phone present.
- Screen: 1440×720 landscape, density 240, no UI scale override — identical to round 1.
- `auto_connect_pill` bounds: `[565,625][874,696]` (309×71px) — **identical to round 1's
  measurement**, confirming no layout regression.
- Other clickable nodes (identical to round 1): `self_mode_button [84,220][312,448]`,
  `usb_button [432,220][660,448]`, `wifi_button [780,220][1008,448]`,
  `settings_button [1128,220][1356,448]`, `exit_button [1284,624][1416,696]`.
- Top buttons end y=448, pill starts y=625: no overlap. `exit_button` shares the pill's row but
  starts at x=1284 vs the pill's end at x=874: 410px gap. **No intersection.**

Evidence: `evidence/p2-ui.xml`, `evidence/p2-screen.png`.

### P3 — a failed attempt falls back to the resting line

**FAIL** (condition 3 not met — reproduces round 1 exactly)

- Setup: D-HU mode 3, D-POCO BT+WiFi OFF for the whole run, 21:45:26 → 22:48:11 (~165s, > 120s).
- Discard-rule check: clean (`MATCH!`=0, one `createGroup SUCCESS`).
- `status pill step:` count: **5** (≥3 ✓): ARMED, PREPARING_NETWORK, WAITING_FOR_PHONE,
  PREPARING_NETWORK (allowed exception, immediately after `startNativeAaQuietHost() requested`),
  WAKING_PHONE — all within the first 1.5s, then nothing more for the remaining ~163s.
- `WAITING_FOR_PHONE` reached ✓. **No later `status pill step: ARMED` after the highest stage** —
  the pill sat on `WAKING_PHONE` for the entire run. `status pill step: hidden` count: 0 ✓.
- Mechanism, confirmed identical to round 1: the poke retry loop ran the whole time with no
  give-up — `Attempting active poke to device: POCO X3 NFC` at 21:45:29.103, 21:45:44.666,
  21:46:30.550, 21:47:16.363, 21:48:02.196 (HFP-AG then HSP-AG each time, `read failed, socket
  might closed or timeout, read ret: -1`). `WAKING_PHONE` (rank 45) is the last pill value reported;
  no `beginAttempt`, no reset to `ARMED`/`WAITING_FOR_PHONE` between retries.
- **This is the same defect round 1 found, reproduced on a fresh SHA-identical build**: P3's premise
  (a failed attempt falls back to the resting line) does not hold for the Native AA path with an
  unreachable phone. Full capture kept: `p3-dhu.txt`.

### P4 — a different mode produces a different sequence

**PASS**

- Settings: `wifi-connection-mode=1`, otherwise as P1. `WifiLauncher: Initializing WiFi Mode: AUTO`
  confirmed (21:48:40.353).
- `status pill step:` values: ARMED → SEARCHING (count 2, ≥2 ✓), `NSD Registered: AAWireless`
  follows (21:48:41.311). `WAKING_PHONE`=0 ✓, `SENDING_CREDENTIALS`=0 ✓, `SEARCHING`=1 ✓.
- Confirms P1's sequence is mode-specific, not a universal artifact.

### P5 — the pill renders two lines

No PASS/FAIL of its own. **Both lines present, legible, not truncated, not overlapping**, captured
mid-`WAKING_PHONE` at 21:49:33 launch +~5s (`evidence/p5-try3.png`): bold "Android Auto is
starting…" with a spinner glyph, lighter "Waking your phone" beneath. A first attempt
(`evidence/p5-pill.png`) landed during a `WifiDirectManager` 5GHz-band toast (`Native AA WiFi
Direct: 5GHz (5200 MHz), 5GHz requested`) that visually covered the pill entirely — this
independently reconfirms round 1's addendum finding (the toast and the pill share the same
bottom-centre screen position and compete for it on every bring-up on a 5GHz-pinned unit like this
rig). A retry ~3s later, in the gap between toasts, got the clean two-line capture used above.

### P6 — USB

**INCONCLUSIVE** (pre-registered)

- `dumpsys usb` on D-HU: `host_connected=false`. No USB accessory path on this rig.

---

## Part B — the wake poke behind a car kit's link, D-POCO head unit, D-MOTO phone, D-HU as the car kit

**Link precondition.** Confirmed before every Part B run via D-POCO's `dumpsys bluetooth_manager`:
`==== StateMachine for 11:46:03:10:33:59 (Navegadortz2) ==== ... state=Connected`
(`HeadsetStateMachine`, the gateway-role link). D-HU's own adapter needed one `svc bluetooth
disable` toggle (self-reverts ~15s) to bring the link up initially; it then held for the whole of
Part B and D1. Evidence: `evidence/w1-precond-dpoco-ag.txt`, `evidence/w1-precond-dhu-before.txt`.
**App on D-HU force-stopped throughout Part B and D1** (verified).

### W1 — the poke goes out with the car kit's link up

**PASS** (rig caveat: FX Plus, see Setup notes)

- Settings (D-POCO): `wifi-connection-mode=3`, `log-level=2`, `native-poke-bt-macs={A0:46:5A:97:E4:95}`
  (D-MOTO), `native-poke-all-paired=true`, `auto-start-bt-macs` empty.
- Radio: D-MOTO BT off at launch (21:53:28), on 8s after `ACTIVELY LISTENING`/`createGroup SUCCESS`
  (both at 21:53:29-30).

| # | condition | result |
|---|---|---|
| 1 | `with a hands-free link up:` for D-MOTO | **`the link is in the gateway role`** (`GATEWAY_ONLY`), repeated at 21:54:31, :56, 21:55:22, :48, 21:56:13 |
| 2 | `Calling socket.connect() for <D-MOTO>` ≥1 | yes, HFP-AG and HSP-AG each cycle |
| 3 | `head unit already holds a Bluetooth hands-free link` = 0 for D-MOTO | **0** |
| 4 | precondition dumpsys | `Connected` on D-POCO's `HeadsetStateMachine` for Navegadortz2, confirmed before launch |
| 5 | `SSL handshake complete` | 21:56:34.864, following `Successfully poked motorola edge 30 neo via HFP-AG` (21:56:16.011) → `Connection accepted` (21:56:26.499) |

Decision line, verbatim: `NativeAA: poking motorola edge 30 neo (A0:46:5A:97:E4:95) with a
hands-free link up: the link is in the gateway role, so its other end is this unit's own car kit or
headset.`

**Rig caveat (see Setup notes item 2):** the same run's driver-candidates line read `2 phone, 0
unknown, 3 not a phone` (FX Plus + D-MOTO as phone), and FX Plus was auto-selected as driver and
manually poked repeatedly (all `read failed`) throughout the run. Did not affect any of the 5
conditions above, all of which are keyed to D-MOTO. `createGroup SUCCESS`=3 this run (see item 3);
`MATCH!`=0.

### W1b — the same, triggered the reporter's way

**PASS**

- Settings: as W1 plus `auto-start-bt-macs={11:46:03:10:33:59}` (D-HU).
- Sequence: launch (21:59:16) → `ACTIVELY LISTENING`+`createGroup SUCCESS` (21:59:16-17) →
  `headunit://exit` (21:59:24, `WifiDirectManager: Stopping and cleaning up...` at 21:59:23.168) →
  `svc bluetooth disable` on D-HU (21:59:37) → self-revert → `MATCH! Starting AapService via
  Bluetooth Auto-start...` (21:59:48.741) → `5GHz createGroup SUCCESS!` (21:59:49.956, 1.2s later) →
  D-MOTO BT on (22:03:40, manually — see note) → poke/dial-back sequence.
- Discard: **exactly one** `MATCH!` (21:59:48.741), one `createGroup SUCCESS` immediately after it.

| # | condition | result |
|---|---|---|
| — | exactly one `MATCH!` | **1** |
| — | `createGroup SUCCESS` after it | yes, 1.2s later |
| 1 | `with a hands-free link up:` for D-MOTO | **`the link is in the gateway role`**, repeated 22:00:51 → 22:03:50 |
| 2 | `Calling socket.connect() for <D-MOTO>` | yes |
| 3 | `head unit already holds...` = 0 for D-MOTO | **0** |

End to end: `Successfully poked motorola edge 30 neo via HFP-AG` (22:03:52.404, 12s after D-MOTO's
BT was turned on) → `Connection accepted` (22:03:57.399) → `SSL handshake complete`
(22:04:06.399). Same rig caveat: `createGroup SUCCESS` also fired at 22:00:50 and twice more
(`onStandardCreateSucceeded`, 22:01:51 and 22:02:51) with FX Plus cycling nearby; none preceded by a
second `MATCH!`.

### W2 — a seeded car-kit MAC is not poked, and the phones are

**PASS** (rig caveat: FX Plus, see Setup notes)

- Settings: `native-poke-bt-macs={11:46:03:10:33:59}` (D-HU), `native-poke-all-paired=true`,
  `auto-start-bt-macs` empty.

| # | condition | result |
|---|---|---|
| 1 | `chosen for Auto Start but not a phone` naming D-HU, before first poke | yes — 22:05:00.567 |
| 2 | `No wake poke phone selected, and poking all paired devices is on` | yes — 22:05:00.568 |
| 3 | `Attempting active poke to device:` for D-MOTO, never for D-HU | D-MOTO: yes (22:06:02.377). D-HU: **never** |
| 4 | `driver candidates:` shows D-HU under `hidden:` with reason | yes — verbatim below |
| 5 | `as the wake poke device.` naming D-MOTO; readback = D-MOTO alone | `Saving A0:46:5A:97:E4:95 (motorola edge 30 neo) as the wake poke device.` at 22:06:09.750 (fired on `Connection accepted`, 8.7s **before** this run's single `SSL handshake complete` at 22:06:18.430 — a benign timing detail since this run had only one session, unlike round 1's multi-session churn where the save came after an earlier device's SSL). Readback from `settings.xml` after the run: `A0:46:5A:97:E4:95` alone — car-kit MAC gone. |

`driver candidates:` line, verbatim: `BluetoothHelper: driver candidates: 2 phone, 0 unknown, 3 not
a phone - hidden: Navegadortz2 (gateway but class not phone), BC8-Android (hands free unit),
Magnetic Speaker (hands free unit)`

**Rig caveat:** FX Plus was again driver-auto-selected and manually poked (25 log lines mentioning
it); it is not part of `native-poke-bt-macs` in this run so its presence doesn't touch condition 3's
"never for D-HU" check, but it is a second, uncounted phone-classified candidate throughout.

### W2b — positive control, the fallback off

**PASS**

- Settings: as W2 with `native-poke-all-paired=false`, `native-poke-bt-macs` restored to D-HU's MAC.
- `chosen for Auto Start but not a phone` naming Navegadortz2: yes — 22:07:18.792.
- `No wake poke phone selected, so nothing is poked`: yes — 22:07:18.793 (repeated at 22:08:20,
  22:08:21).
- `Attempting active poke to device:` count in 62s: **0**.
- No dial-back session formed in this window (not required — brief allows either outcome).

### W3 — the manual poke from the picker

**PASS** (`finished`-line caveat, same as round 1)

- Setup: from W1's state, `native-poke-bt-macs={A0:46:5A:97:E4:95}`, `native-poke-all-paired=true`.
  Confirmed no session up before firing (`ACTION_QUERY_STATE` → `"state":"Disconnected"`).
- Scripted manual poke: `adb shell am broadcast -f 0x00000020 -n
  com.andrerinas.headunitrevived/com.andrerinas.openheadunit.automation.AutomationReceiver -a
  ...ACTION_NATIVE_AA_POKE --es extra_mac A0:46:5A:97:E4:95` → `result=0, ok:true` at 22:09:14.

| ts | line |
|---|---|
| 22:09:13.318 | `AutomationReceiver: com.andrerinas.openheadunit.ACTION_NATIVE_AA_POKE` |
| 22:09:13.330 | `NativeAA: Manual poke requested for motorola edge 30 neo` |
| 22:09:13.332 | `NativeAA: Attempting manual poke to motorola edge 30 neo...` |
| 22:09:13.335 | `NativeAA: poking motorola edge 30 neo (...) with a hands-free link up: the link is in the gateway role...` |
| 22:09:13.337 | `NativeAA: Calling socket.connect() for motorola edge 30 neo via HFP-AG...` |
| 22:09:14.829 | `NativeAA: Successfully poked motorola edge 30 neo via HFP-AG. Holding 20000ms...` |
| 22:09:15.787 | `NativeAA: Connection accepted from motorola edge 30 neo` |
| 22:09:24.947 | `AapSslContext.performHandshake: SSL handshake complete.` |

- `with a hands-free link up:` and `Calling socket.connect() for <D-MOTO>` both appear directly
  after `Manual poke requested`, 5-7ms later — the reporter's failing shape (8-12ms with **nothing**
  between requested and finished) is absent; here there are four intermediate lines.
- **`Manual poke to motorola edge 30 neo finished.` did not print**, same as round 1 — the poke
  succeeded and a session formed inside the 20s hold, cancelling the hold coroutine before the
  post-`pokeDevice()` log ran.
- Wall-clock `Manual poke requested` → `Calling socket.connect()`: **7ms**. → `Successfully poked`:
  **1.5s**. (Faster than round 1's 0.82s/2.28s, likely because D-MOTO's connection state was already
  primed from the automatic poke loop's own attempts moments earlier in the same run.)

---

## Part C — auto-disconnect ignores only our own socket closes

### D1 — the lag this round sets the constant from (D-POCO head unit, D-MOTO phone)

**PASS**

- Settings (D-POCO): W1 config plus `auto-disconnect-bt-macs={A0:46:5A:97:E4:95}`,
  `auto-disconnect-bt-delay-seconds=5`.
- Session formed via **D-MOTO's own spontaneous dial-back** (`Connection accepted from motorola
  edge 30 neo` at 22:11:24.640), not the poke loop — D-MOTO had recently seen this head unit
  multiple times already this round, matching brief §2's own documented caveat about a
  recently-seen phone dialing back before the loop reaches `pokeDevice()`. A manual poke broadcast
  fired moments later (22:11:26.416) landed on an already-forming connection. `SSL handshake
  complete` at 22:11:33.373. **Touched nothing for 100s afterward.**
- `stayed away; ending the session` count: **0** ✓.
- One `not ending the session for` line: `AapService: Bluetooth auto-disconnect: not ending the
  session for A0:46:5A:97:E4:95 (up=true, ownCloseMs=9807).` — numeric, **< 15000** ✓.
- Discard: `createGroup SUCCESS`=2 (rig caveat, see Setup notes item 3), `MATCH!`=0, `SSL`=1.

**Report data:**
- Only `went away; ending the session in` line: 22:11:38.100.
- Nearest preceding own-close line: `NativeAA: BT Handshake socket closed.` at 22:11:33.295.
- **Gap: 4.805s.**
- `ownCloseMs` on the matching `not ending` line: **9807**.
- **Number that would set `OWN_SOCKET_CLOSE_GRACE_MS` from this run: 9807ms** — consistent with
  round 1's 9420ms from a different session pairing. The 15000ms constant clears both with margin.

### D2 — a real disconnect at 20s ends the session (D-HU head unit, D-POCO phone)

**INCONCLUSIVE** — not a code FAIL; see Setup notes item 4 for the full mechanism.

- Precondition confirmed: D-POCO's `A2DPSinkStateMachine`/`HeadsetClientStateMachine` both
  `Connected` on D-HU before launch (`evidence/d2-precond-dhu-before.txt`).
- D-HU's `WirelessServer` bound port 5288 to the IPv6 wildcard only (confirmed via
  `/proc/net/tcp6` vs `/proc/net/tcp`) in **two consecutive fresh launches**. D-POCO joined the P2P
  network correctly both times (IP assigned, ping succeeds) but its IPv4 connection to
  `192.168.49.1:5288` was refused (`nc -z` → `Connection refused`) both times, so no session ever
  formed and the auto-disconnect grace timer was never exercised. D-HU's own station WiFi was
  concurrently associated to "Pegue Cdesta" as the primary network in both attempts — the same
  STA+P2P concurrency state that `five-ghz-channel round 1` measured as a throughput cost; here it
  is only correlated, not proven causal, with the connection refusal.
- Not chased further (would require forgetting D-HU's saved "Pegue Cdesta" network, a persistent
  rig-wide change outside this round's scope). Recommend a follow-up round that either forgets that
  network first or captures whether P1 (which worked, earlier in this same session) had D-HU's STA
  WiFi associated at the time.

### D3 — known limit, a disconnect at ~SSL+2s (D-HU head unit, D-POCO phone)

**INCONCLUSIVE** — same blocker as D2; no session could form to test against.

---

## Report-back summary (brief §7)

1. **W1 line 1 reason text:** `the link is in the gateway role` (`GATEWAY_ONLY`) on both W1 and
   W1b, for D-MOTO, with the car kit's link `Connected` on D-POCO's `HeadsetStateMachine`. W1b:
   exactly one `MATCH!`, `createGroup SUCCESS` 1.2s later, full session.
2. **D1 largest gap:** **4.805s** (own-close → `went away`). `ownCloseMs` seen: **9807** (this
   round), vs **9420** (round 1) — both comfortably under the 15000ms constant.
3. **W2 `driver candidates:` line, verbatim:** `BluetoothHelper: driver candidates: 2 phone, 0
   unknown, 3 not a phone - hidden: Navegadortz2 (gateway but class not phone), BC8-Android (hands
   free unit), Magnetic Speaker (hands free unit)`. **`native-poke-bt-macs` readback after W2:**
   `A0:46:5A:97:E4:95` alone.
4. **P1 `status pill step:` ordered list with timestamps:** see P1 table. One rank decrease,
   `WAITING_FOR_PHONE`(40)→`PREPARING_NETWORK`(20), 2ms after `startNativeAaQuietHost() requested`
   — the allowed exception. No decrease outside that.
5. **`dumpsys` link preconditions:** recorded per run in `evidence/*-precond-*.txt`. Every Part B
   run had D-HU `Connected` on D-POCO's `HeadsetStateMachine`.

## Verdict roll-up

| Run | Round 1 | Round 2 | Notes |
|---|---|---|---|
| R0 | PASS (1616/0) | **PASS** (1616/0, identical md5) | deterministic build confirmed |
| P1 | PASS | **PASS** | |
| P2 | PASS | **PASS** | bounds identical to round 1 |
| P3 | **FAIL** | **FAIL** | same mechanism, reproduced exactly |
| P4 | PASS | **PASS** | |
| P5 | no verdict | no verdict | two lines legible; toast-covers-pill addendum reconfirmed |
| P6 | INCONCLUSIVE (no USB) | **INCONCLUSIVE** (no USB) | pre-registered both rounds |
| W1 | PASS | **PASS** | new rig caveat: FX Plus classifier gap found |
| W1b | PASS | **PASS** | |
| W2 | PASS | **PASS** | |
| W2b | PASS | **PASS** | |
| W3 | PASS | **PASS** | |
| D1 | PASS (9420ms) | **PASS** (9807ms) | two independent measurements agree |
| D2 | PASS | **INCONCLUSIVE** | blocked by D-HU network condition this round, see Setup notes |
| D3 | PASS | **INCONCLUSIVE** | same blocker |

## Anything the brief did not ask about

- **FX Plus classifier gap (new this round, user-flagged mid-round).** See Setup notes item 2 for
  the full mechanism. Recommendation for a follow-up: `DriverCandidatePolicy.classify()` should not
  let an Audio-Gateway-advertising device with an entirely uncategorized (`0x1F`) major device class
  fall through to `PHONE` — that is precisely the class of accessory (a cheap intercom, a two-way
  radio) least likely to set a proper class and most likely to advertise HFP-AG for its own
  call-passthrough feature. This is a pre-existing gap (predates `ef66abbf`), not a regression from
  this branch.
- **WiFi Direct group churn this round, absent in round 1's clean captures**, correlated with
  FX Plus's connect/disconnect cycling (Setup notes item 3). Worth checking whether a live
  gateway-class stray device near the rig can itself trigger `WifiDirectManager` group
  re-negotiation independent of `MATCH!`/`AutoStartReceiver` — a different path than the
  already-documented self-wake loop.
- **D2/D3's IPv6-only `WirelessServer` bind** (Setup notes item 4) is worth a dedicated look: two
  consecutive fresh app-process launches on D-HU bound port 5288 to `::` only, with no IPv4 listener
  ever created, while P1 (also on D-HU) worked. `WirelessServer.kt:105` calls
  `bind(InetSocketAddress(5288))`, which resolves the local address via the JVM's cached
  IPv4/IPv6-preference decision — a decision made once per process based on ambient network state,
  not re-evaluated per bind. If that's the mechanism, an explicit `InetSocketAddress(Inet4Address,
  port)` (or binding both families) would remove the dependency on process-start-time network state
  entirely, rather than leaving whether a real Android Auto phone can ever reach the server up to
  which address family the JVM guessed.
- **P3 remains a real, reproduced finding** (Setup notes / round 1's own write-up covers the
  mechanism in full): the Native AA poke retry loop has no give-up and no `beginAttempt`, so the
  pill latches on `WAKING_PHONE` indefinitely when the phone never appears, rather than returning to
  a resting stage. Confirmed byte-for-byte reproducible on an independent capture.
