# bring-up-status-pill-and-poke-readiness — round 1 results

**Candidate:** `fork/feat/bring-up-status-pill-and-poke-readiness` @ `ef66abbf`       **Baseline:** none (see brief §1)
**APK md5:** `222016d536d46c712bf769e0fd255663` (`com.andrerinas.headunitrevived_3.4.0-beta1_debug.apk`, versionCode 106) / none
**Unit:** D-HU = UNISOC MT50 (`MT50_YT610E4GFPSL_U`, Android 14, single BT radio, adbd root), bonded name "Navegadortz2" `11:46:03:10:33:59`. D-POCO = POCO X3 NFC (`M2007J20CG`, Android 11, not rooted, debuggable OHU) `DC:B7:2E:5E:4E:59`. D-MOTO = motorola edge 30 neo (`miami`, not rooted) `A0:46:5A:97:E4:95`. Gearhead: D-POCO 17.5.663214, D-MOTO 17.5.663244.
**Date:** 2026-09-10

## Setup notes

### Scripts used (`hur-wifi-test-scripts/`, a sibling dir, not this repo)
- `build_hur.sh` — build the candidate (R0). `run_unit_tests.sh` — JVM gate (R0).
- `set_hu_settings_host.py <serial> scalar:.. set:.. setclear:.. del:..` — settings writes on **D-HU** (rooted). Force-stops, no relaunch.
- `set_hu_settings_runas.py <serial> ...` — same for **D-POCO** / **D-MOTO** (non-rooted, run-as). Force-stops, no relaunch.
- No new script was needed this round.

### Deviations / brief errata
- **D-HU logd timestamp freeze.** For roughly the first ~25 s of every capture on D-HU the logcat wall-clock is frozen (all lines stamp `HH:MM:00.xxx` with out-of-order milliseconds), then jumps forward and tracks real time again. Known MT50 quirk (five-ghz round 2 saw it on `createGroup SUCCESS`; here it covers the whole bring-up window). **All ordering in this report is by capture line number, which is monotonic and reliable; quoted timestamps in the frozen window are not.** Every "wall-clock" figure from a frozen window is therefore given as a bound, not a measurement.
- Brief §4 decision-line reason texts are substrings of the real lines: real is `the link is in the gateway role, so its other end is this unit's own car kit or headset` and `the phone holds no connection to this unit, so the link is not its`. Matched with `grep -aF` on the brief's substrings.
- Brief §4 says every cited line is an unguarded `AppLog.i` "except the repeats noted"; `No wake poke phone selected...` (both variants) and the unpaired-device skip are `AppLog.w`. Harmless at `log-level=2`.
- Clean-run protocol §4 step 1 (phone airplane mode): on D-POCO `cmd connectivity airplane-mode enable` drops BT but leaves WiFi up (as §7a notes); WiFi was then dropped with `svc wifi disable`. Coming back: `airplane-mode disable` + explicit `svc bluetooth enable` + `svc wifi enable`, both verified by `dumpsys`.

## R0 — build and unit-test gate

**PASS**

- Build: `build_hur.sh` → `BUILD SUCCESSFUL in 4m 2s`, `com.andrerinas.headunitrevived_3.4.0-beta1_debug.apk` md5 `222016d536d46c712bf769e0fd255663`.
- Unit tests: `run_unit_tests.sh` → `BUILD SUCCESSFUL`, **1616 tests, 0 failures, 0 errors, 0 skipped** (aggregated from `app/build/test-results/testGithubDebugUnitTest/*.xml`).
- Install: `adb install -r` on D-HU and D-POCO. Live `md5sum` of `pm path` base.apk on **both** = `222016d536d46c712bf769e0fd255663` (matches built).
- Identity: `ACTION_QUERY_STATE` on D-HU → `{"versionName":"3.4.0-beta1","versionCode":106,"commit":"ef66abbfac12","flavor":"github","wifiMode":"NATIVE",...}`. DEX `strings | grep -cE 'ConnectionStageTracker|WakeDecision|OWN_SOCKET_CLOSE_GRACE'` = 7 on both units.

---

## Part A — status pill, D-HU head unit, D-POCO phone

### P1 — Native AA cold bring-up to projection  **(the point of Part A)**

**PASS**

- Settings written (D-HU): `wifi-connection-mode=3`, `log-level=2`, `auto-start-bt-macs` empty, `native-preferred-device-mac` deleted. Left as-is: `native-poke-bt-macs={DC:B7:2E:5E:4E:59}`, `native-poke-all-paired=true`, `auto-disconnect-bt-macs` empty.
- Radio state: D-POCO airplane-on + `svc wifi disable` (BT OFF, WiFi disabled, verified) before launch; D-HU launched with BT ON / WiFi up; D-POCO radios restored ~27 s after launch (`airplane-mode disable` + `svc bluetooth enable` + `svc wifi enable`, both verified ON).
- Discard-rule check: **clean** — `MATCH! Starting AapService`=0, `createGroup SUCCESS`=1, `SSL handshake complete`=1, `Magic Garbage`=0, one `startNativeAaQuietHost() requested`. (`p2p-wlan0-0`→`p2p-wlan0-1` seen, but the `-0` group is the stale persistent group torn down at launch, logged `asked=nothing (not this app's create)`; per §7a not contamination.)

Ordered `status pill step:` values (by capture line; timestamps in `[]` are frozen-window, indicative only):

| # | line | value | rank | note |
|---|---|---|---|---|
| 1 | 1875 | ARMED | 10 | `[17:01:00.769]` first value |
| 2 | 1956 | PREPARING_NETWORK | 20 | |
| 3 | 2787 | WAITING_FOR_PHONE | 40 | after `ACTIVELY LISTENING` (L2168) |
| — | 2811 | — | — | `startNativeAaQuietHost() requested. Removing old group if any` |
| 4 | 2817 | PREPARING_NETWORK | 20 | **the one rank decrease — immediately after L2811, the allowed exception** |
| 5 | 3562 | WAKING_PHONE | 45 | |
| 6 | 31290 | PHONE_ANSWERED | 50 | clock unfrozen from here `[17:01:26.360]` |
| 7 | 31759 | SENDING_CREDENTIALS | 55 | |
| 8 | 32908 | PHONE_JOINING | 60 | |
| 9 | 33625 | CONNECTING | 70 | |
| 10 | 33792 | SECURING | 80 | |
| 11 | 34013 | STARTING_PROJECTION | 90 | `[17:01:28.668]` last value |

- Count: 11 (≥6). First = ARMED. Last = STARTING_PROJECTION. `WAITING_FOR_PHONE` (L2787) precedes `WAKING_PHONE` (L3562). `SSL handshake complete` present (L34013 area, `17:01:28.662`).
- **Rank monotonic apart from the single decrease at #4, which follows `startNativeAaQuietHost() requested` by one line.** No other decrease.
- Wall-clock first pill → `STARTING_PROJECTION`: device stamps give ~27.9 s, but ~24 s of that is the deliberate wait for D-POCO's radios (off until ~+27 s). Actual phone-join work: `Successfully poked POCO X3 NFC via HFP-AG` → `Connection accepted from POCO X3 NFC` (0.2 s) → `SSL handshake complete` ~2.3 s later; credentials-to-projection ≈ 3.4 s.

Poke path detail: the automatic loop *did* reach `pokeDevice()` this run (D-POCO unreachable at launch): `Attempting active poke to device: POCO X3 NFC` at L~ (17:01:00.501 frozen) and again 15 s later, then `Successfully poked POCO X3 NFC via HFP-AG. Holding 15000ms` once D-POCO's BT came up. So `WAKING_PHONE` was genuinely exercised, not skipped.

### P2 — the pill does not cover the home controls

**PASS**

- Setup: D-HU launched mode 3, D-POCO BT+WiFi OFF (no phone). `uiautomator dump` + `screencap` taken ~10 s after launch; pill text at that moment was "Android Auto is starting… / Waking your phone" (`WAKING_PHONE`) — on this rig with no phone the pill keeps advancing past `ARMED` through the poke loop, it does not sit on `ARMED`. Node identity and geometry are unaffected.
- Screen: 1440×720 landscape, density 240, **no UI scale override** (`wm density` reports physical only; no `dpi-pixel-density` effect on this display).
- `auto_connect_pill` node: `bounds="[565,625][874,696]"` (309×71 px), `clickable="true"`, present.
- All other `clickable="true"` nodes:
  - `self_mode_button` `[84,220][312,448]`
  - `usb_button` `[432,220][660,448]`
  - `wifi_button` `[780,220][1008,448]`
  - `settings_button` `[1128,220][1356,448]`
  - `exit_button` `[1284,624][1416,696]`
- Intersection test: the four top buttons end at y=448, the pill starts at y=625 — no vertical overlap. `exit_button` shares the pill's row (y 624–696) but starts at x=1284; the pill ends at x=874, a **410 px horizontal gap**. **The pill intersects none of them.**

Evidence: `evidence/.../p2-ui.xml`, `p2-screen.png`.

### P3 — a failed attempt falls back to the resting line

**FAIL** (condition 3 not met)

- Setup: D-HU mode 3, D-POCO BT+WiFi OFF for the whole run. Ran 155 s (17:05:05 → 17:07:40), longer than the brief's 120 s.
- Discard-rule check: clean (`MATCH!`=0, one `createGroup SUCCESS`).
- `status pill step:` count: **6** (≥3 ✓): ARMED, PREPARING_NETWORK, WAITING_FOR_PHONE, PREPARING_NETWORK, CREATING_NETWORK, WAKING_PHONE — all within the first 1.3 s, then nothing more for the remaining 154 s.
- `WAITING_FOR_PHONE` reached ✓. `WAKING_PHONE` reached.
- **No later `status pill step: ARMED` after the highest stage.** The pill sat on `WAKING_PHONE` for the entire run.
- `status pill step: hidden` count: 0 — never `hidden` while armed ✓.
- Last three `status pill step:` lines (only distinct values after WAITING_FOR_PHONE):
  - `17:05:07.983  ... status pill step: PREPARING_NETWORK`
  - `17:05:08.564  ... status pill step: CREATING_NETWORK`
  - `17:05:08.826  ... status pill step: WAKING_PHONE`

**Mechanism (matters for the coding side):** in mode 3 with an unreachable phone there is no "failed attempt" event to fall back from. The poke retry loop runs forever — `Attempting active poke to device: POCO X3 NFC` at 17:05:08, 17:05:23, 17:06:09, 17:06:55, 17:07:10, 17:07:26 (HFP-AG then HSP-AG, each `read failed`) — with no give-up, no `startNativeAaQuietHost()` re-init, and no `beginAttempt`. `WAKING_PHONE` (rank 45) is the last thing reported; the loop's later `CREATING_NETWORK` (rank 30) reports are dropped by `ConnectionStagePolicy.shouldApply`. So the pill is arguably *correct* ("still waking your phone"), but P3's premise — that a failed attempt resets the pill to `ARMED` — does not hold for the Native AA path on this rig. Either P3 should be re-scoped to mode 1/2 (where a discovery attempt does time out), or the mode-3 pill should drop to `ARMED`/`WAITING_FOR_PHONE` between poke retries. Full capture kept: `p3-FAIL-full.txt`.

### P4 — a different mode produces a different sequence (the discriminator)

**PASS**

- Settings: `wifi-connection-mode=1` (`WifiLauncher: Initializing WiFi Mode: AUTO` confirmed), otherwise as P1. D-POCO radios off at launch, restored ~27 s later.
- `status pill step:` values: **ARMED → SEARCHING** (count 2, ≥2 ✓), then nothing further (no phone-side AA headunit server on D-POCO, so no discovery hit — expected, P4 does not require a session).
- `status pill step: SEARCHING` count: **1** (≥1 ✓). `NSD Registered: AAWireless` follows.
- `status pill step: WAKING_PHONE` count: **0** ✓
- `status pill step: SENDING_CREDENTIALS` count: **0** ✓
- The mode-1 sequence (ARMED→SEARCHING, static) is plainly not the P1 sequence (ARMED→PREPARING_NETWORK→WAITING_FOR_PHONE→…→WAKING_PHONE→PHONE_ANSWERED→…). P1's PASS is therefore not mode-independent.

### P5 — the pill renders two lines

No PASS/FAIL of its own. **Both lines present, legible, not truncated, not overlapping.**

- Captured at `WAKING_PHONE` (mode 3, no phone — the stage that persists, between `CREATING_NETWORK` rank 30 and `PHONE_JOINING` rank 60). Screenshot `evidence/.../p5-pill.png`.
- Line 1 (bold): **"Android Auto is starting…"**, with a circular spinner glyph at its left.
- Line 2 (smaller, lighter): **"Waking your phone"**.
- The pill is a dark rounded capsule, bottom-centre, well clear of the `Exit` button at bottom-right. Minor cosmetic note: a second, slightly wider rounded-rect outline is faintly visible behind the text block (pill background vs. text container, or a touch/ripple state) — it does not clip or obscure either line.

### P6 — USB

**INCONCLUSIVE** (pre-registered in the brief)

- `dumpsys usb` on D-HU: `host_connected=false`. No USB accessory path exists on this rig (§7a). No `USB_ATTACHED` / `USB_SWITCHING` possible. No substitute built.

---

## Part B — the wake poke behind a car kit's link, D-POCO head unit, D-MOTO phone, D-HU as the car kit

**Link precondition (all Part B + D1 runs).** D-HU's Headset**Client** connects itself to D-POCO once D-POCO's BT is on. It did not come up on its own within 20 s; one `svc bluetooth disable` on **D-HU** (self-reverts ~14 s) brought it up and it then held for every Part B run. Confirmed immediately before each launch:
- D-POCO `dumpsys bluetooth_manager` → `==== StateMachine for …33:59 ====` (D-HU) → `mCurrentState: Connected`, `mConnectionState: 2`, `StateMachine: name=HeadsetStateMachine state=Connected` — this is the **gateway-role** link on D-POCO.
- D-HU held `A2DPSinkStateMachine state=Connected` + `HeadsetClientStateMachine state=Connected` to D-POCO throughout.
- Per-run precond dumps: `evidence/.../w1-precond-*.txt`, `w1b-precond-*.txt`, `w2-precond-*.txt`, `w2b-precond-*.txt`.

**App on D-HU force-stopped for all of Part B and D1** (verified `pidof` empty before each run). The D-HU BT toggle above did not launch it (manifest receivers dead while force-stopped, §7a).

### W1 — the poke goes out with the car kit's link up  **(the point of the round)**

**PASS**

- Settings (D-POCO): `wifi-connection-mode=3`, `log-level=2`, `auto-start-bt-macs` empty, `native-poke-bt-macs={A0:46:5A:97:E4:95}` (D-MOTO), `native-poke-all-paired=true`, `native-preferred-device-mac` deleted, `auto-disconnect-bt-macs` deleted.
- Radio: D-MOTO BT OFF at launch; D-POCO WiFi ON (first attempt aborted — D-POCO WiFi was left disabled from P5 and the app's own `WiFi is disabled but needed for Native AA. Attempting to enable...` never took; re-run after `svc wifi enable` ×2). After `ACTIVELY LISTENING` (17:16:25) + `5GHz createGroup SUCCESS!` (17:16:26), waited 8 s, D-MOTO BT on (17:16:45, verified `state: ON`).
- Discard: `createGroup SUCCESS`=1, `MATCH!`=0, `SSL handshake complete`=1 — clean.

| # | condition | result |
|---|---|---|
| 1 | `with a hands-free link up:` for D-MOTO, reason gateway-or-absent | **`the link is in the gateway role`** (`GATEWAY_ONLY`) — 17:16:27.077, 17:16:52.660. The expected reading on a phone ROM. |
| 2 | `Calling socket.connect() for <D-MOTO>` ≥1 | yes — 17:16:27.079 (HFP-AG), 17:16:32.239 (HSP-AG), 17:16:52.664 (HFP-AG) |
| 3 | `head unit already holds a Bluetooth hands-free link` = 0 for D-MOTO | **0** |
| 4 | pre-launch dumpsys D-HU `Connected` on D-POCO `HeadsetStateMachine` | yes (`w1-precond-dpoco-ag.txt`: `mCurrentState: Connected`) |
| 5 | `SSL handshake complete` appears | yes — 17:17:03.839, **following `Successfully poked motorola edge 30 neo via HFP-AG` (17:16:54.198) → `Connection accepted from motorola edge 30 neo` (17:16:54.575)**. Session formed **by the poke**, not a dial-back. |

Decision line, verbatim:
`NativeAA: poking motorola edge 30 neo (A0:46:5A:97:E4:95) with a hands-free link up: the link is in the gateway role, so its other end is this unit's own car kit or headset.`

### W1b — the same, triggered the reporter's way

**PASS**

- Settings: as W1 plus `auto-start-bt-macs={11:46:03:10:33:59}` (D-HU / "car kit").
- Sequence: launch `MainActivity` (17:19:23) → `ACTIVELY LISTENING` → `headunit://exit` (17:19:25, `WifiDirectManager: Stopping and cleaning up...`, group down — reporter's "app in background" state) → `svc bluetooth disable` on **D-HU** (17:19:49) → D-HU self-reverts and raises `ACL_CONNECTED` on D-POCO → `AutoStartReceiver.onReceive | MATCH! Starting AapService via Bluetooth Auto-start...` (17:20:01) → `5GHz createGroup SUCCESS!` (17:20:02) → wait 8 s past listeners+group → D-MOTO BT on (17:20:19).
- Discard: **exactly one** `MATCH! Starting AapService` (17:20:01), `createGroup SUCCESS`=1 (17:20:02, after the MATCH), `SSL handshake complete`=1.

| # | condition | result |
|---|---|---|
| — | exactly one `MATCH! Starting AapService` | **1** |
| — | a `createGroup SUCCESS` after it | yes, 1.2 s later |
| 1 | `with a hands-free link up:` for D-MOTO | **`the link is in the gateway role`** — 17:20:02.476, 17:20:27.882 |
| 2 | `Calling socket.connect() for <D-MOTO>` | yes — 17:20:02.482 (HFP-AG), 17:20:07 (HSP-AG), 17:20:52 (HFP-AG) |
| 3 | `head unit already holds a Bluetooth hands-free link` = 0 for D-MOTO | **0** |

End to end: the trigger device is the car kit (D-HU's ACL), the phone (D-MOTO) is still poked and connects — `Successfully poked motorola edge 30 neo` (17:20:30) → `Connection accepted from motorola` (17:20:37) → `SSL handshake complete` (17:20:50).

### W2 — a seeded car-kit MAC is not poked, and the phones are

**PASS** (with a rig caveat — see below)

- Settings: `native-poke-bt-macs={11:46:03:10:33:59}` (D-HU / car kit), `native-poke-all-paired=true`, `auto-start-bt-macs` empty. D-MOTO BT off at launch, on 8 s after group.
- Discard: `createGroup SUCCESS`=1, `MATCH!`=0. (`SSL handshake complete`=4 — see caveat; per §7a the discard signal is a *second createGroup*, which did not happen.)

| # | condition | result |
|---|---|---|
| 1 | `chosen for Auto Start but not a phone` naming D-HU, before first poke | yes — `not poking Navegadortz2 (11:46:03:10:33:59): chosen for Auto Start but not a phone...` at 17:22:42.071; `driver candidates` at 17:22:41.528; first `Attempting active poke` ~17:22:43 |
| 2 | `No wake poke phone selected, and poking all paired devices is on` | yes — 17:22:42.071 (`...Poking every paired phone...`) |
| 3 | `Attempting active poke to device:` for D-MOTO, **never** for D-HU | D-MOTO: yes. D-HU: **never**. (Also `Galaxy S24+ (24:A4:52:CF:70:EF)` — a foreign paired phone, see caveat; not a violation.) |
| 4 | `driver candidates:` shows D-HU under `hidden:` with reason | yes — verbatim below |
| 5 | after `SSL handshake complete`, `as the wake poke device.` naming D-MOTO; `native-poke-bt-macs` readback = D-MOTO alone | `Saving A0:46:5A:97:E4:95 (motorola edge 30 neo) as the wake poke device.` at 17:24:26, after an SSL handshake. **`native-poke-bt-macs` readback from `settings.xml` after the run = `A0:46:5A:97:E4:95` alone** — the car-kit MAC is gone. |

`driver candidates:` line, verbatim (report item 3):
`BluetoothHelper: driver candidates: 2 phone, 0 unknown, 3 not a phone - hidden: Navegadortz2 (gateway but class not phone), BC8-Android (hands free unit), Magnetic Speaker (hands free unit)`

**Rig caveat.** D-POCO carries a bond to a **Galaxy S24+ (`24:A4:52:CF:70:EF`)** that is live and AA-capable in RF range (not part of this rig's 3-device set). With `native-poke-all-paired=true` the fallback poked it too; it dialled back and cycled **4 short AA sessions** (`Saving 24:A4:52:CF:70:EF (Galaxy S24+) as the wake poke device.` first, then repeated connect/SSL/drop) before D-MOTO's poke stuck at 17:24:26 and D-MOTO's MAC became the final saved value. Every one of those saves was a *phone* replacing the car-kit MAC, so the fix's behaviour is confirmed; the specific phone that ends up saved is a rig artifact of the stray bond. The `native-poke-bt-macs` readback (D-MOTO alone, car-kit MAC gone) is the ground truth and it is correct.

### W2b — positive control, the fallback off

**PASS**

- Settings: as W2 with `native-poke-all-paired=false`; `native-poke-bt-macs={11:46:03:10:33:59}` (D-HU) restored after W2's handshake had rewritten it to D-MOTO.
- `chosen for Auto Start but not a phone` naming Navegadortz2 (D-HU): yes — 17:26:21.506.
- `No wake poke phone selected, so nothing is poked` : yes — 17:26:21.507 (`... Choose one in Auto Start settings.`).
- `Attempting active poke to device:` count in the whole run: **0**.
- D-MOTO's own dial-back formed a session afterward (`Connection accepted from motorola edge 30 neo` 17:26:50 → `SSL handshake complete` 17:26:59) — not a fault, as the brief notes.
- Proves the drop is real: the car kit is not poked even when it is the only chosen target, and even with a live foreign Galaxy S24+ also bonded (all-paired off, so it is never reached).

### W3 — the manual poke from the picker

**PASS** (with a `finished`-line caveat)

- Setup: from W1's state (gateway link confirmed `Connected`, D-MOTO BT on), `native-poke-bt-macs={A0:46:5A:97:E4:95}`, `native-poke-all-paired=true`.
- The scripted manual poke **landed on non-rooted D-POCO**: `adb shell am broadcast -f 0x00000020 -n <pkg>/…automation.AutomationReceiver -a …ACTION_NATIVE_AA_POKE --es extra_mac A0:46:5A:97:E4:95` → `Broadcast completed: result=0, data="{…"ok":true}"`. (This contradicts a standing note that `ACTION_NATIVE_AA_POKE` is unreachable from adb on a non-rooted phone — the explicit-component + `-f 0x00000020` form works. Recorded in [[project_native_aa_poke_trigger_and_bak_gotcha]].)
- `AutomationReceiver: com.andrerinas.openheadunit.ACTION_NATIVE_AA_POKE` — 17:28:28.303.

Sequence for the broadcast's invocation (thread [73]):
| ts | line |
|---|---|
| 17:28:28.331 | `AapService: Received manual Native-AA poke request for A0:46:5A:97:E4:95` |
| 17:28:28.333 | `NativeAA: Manual poke requested for motorola edge 30 neo (A0:46:5A:97:E4:95)` |
| 17:28:29.144 | `NativeAA: Attempting manual poke to motorola edge 30 neo...` (0.8 s credential wait first) |
| 17:28:29.148 | `NativeAA: poking motorola edge 30 neo (A0:46:5A:97:E4:95) with a hands-free link up: the link is in the gateway role, so its other end is this unit's own car kit or headset.` |
| 17:28:29.150 | `NativeAA: Calling socket.connect() for motorola edge 30 neo via HFP-AG` |
| 17:28:30.615 | `NativeAA: Successfully poked motorola edge 30 neo via HFP-AG. Holding 20000ms...` |
| 17:28:31.103 | `Connection accepted from motorola edge 30 neo` |
| 17:28:39.819 | `SSL handshake complete` |

- **`with a hands-free link up:` and `Calling socket.connect() for <D-MOTO>` both appear after `Manual poke requested for <D-MOTO>`.** The reporter's failing shape (`requested` → `finished` in 8–12 ms with nothing between) is definitively absent.
- **`Manual poke to motorola edge 30 neo finished.` did not print** — the poke succeeded so completely that a session formed during the 20 s hold and cancelled the hold coroutine before the post-`pokeDevice()` log ran. `headunit://disconnect` cannot re-arm mode 3 on this rig (§3 template note, confirmed here: it logs `Native AA user exit … status pill step: hidden`), so a clean no-session re-run is not possible via that route.
- Wall-clock `Manual poke requested` → `Calling socket.connect()` ≈ **0.82 s**; → `Successfully poked` ≈ **2.28 s**. Reporter's failing shape was 8–12 ms `requested`→`finished`.

---

## Part C — auto-disconnect ignores only our own socket closes

### D1 — the lag this round sets the constant from (D-POCO head unit, D-MOTO phone)

**PASS**

- Settings (D-POCO): W1 config plus `auto-disconnect-bt-macs={A0:46:5A:97:E4:95}` (D-MOTO), `auto-disconnect-bt-delay-seconds=5`.
- Session formed by poke; `SSL handshake complete` 17:38:03. **Touched nothing for 107 s** (to 17:39:50).
- `stayed away; ending the session` count: **0** ✓
- One `not ending the session for` line: `AapService: Bluetooth auto-disconnect: not ending the session for A0:46:5A:97:E4:95 (up=true, ownCloseMs=9420).` — `ownCloseMs=9420` is a number, not null, and **< 15000** ✓
- Discard: `createGroup SUCCESS`=1, `SSL`=1, `MATCH`=0.

**Report data (item 2):**
- The only `went away; ending the session in` line: **17:38:05.314**.
- Nearest preceding own-close line: `NativeAA: BT Handshake socket closed.` at **17:38:00.896** (no `HFP socket for … closed` line in the run).
- **Gap between them: 4.418 s.** This is the largest (and only) gap.
- `ownCloseMs` on the matching `not ending` line: **9420** (consistent: `fireBtAutoDisconnect` ran at 17:38:10.316, 9420 ms after the socket close at 17:38:00.896).
- **Number that would set `OWN_SOCKET_CLOSE_GRACE_MS` from this run: 9420 ms.** The current constant 15000 ms clears it with ~5.6 s of margin. One data point only — the brief asked for the measurement, not a fleet of them.

### D2 — a real disconnect ends the session (D-HU head unit, D-POCO phone)

**PASS** (via airplane mode; see notes)

- Settings (D-HU): `auto-disconnect-bt-macs={DC:B7:2E:5E:4E:59}` (D-POCO), `auto-disconnect-bt-delay-seconds=5`.
- Precondition: D-HU held `A2DPSinkStateMachine state=Connected` + `HeadsetClientStateMachine state=Connected` to D-POCO before launch and again 13 s after `SSL handshake complete` (17:41:24) — link survived the handoff, so D2 is not INCONCLUSIVE. `d2-precond-dhu.txt`.
- **First attempt at SSL+20 s**: `adb -s <D-POCO> shell svc bluetooth disable` → `btif_av_acl_disconnected: Peer …4e:59 : ACL Disconnected` → `went away; ending the session in 5000ms` (17:41:44.709). D-POCO's Bluetooth **self-reverted to ON within ~4 s** (faster than the §7a-documented ~45 s), the ACL reconnected, and `AapService: Bluetooth auto-disconnect: DC:B7:2E:5E:4E:59 is back; the pending disconnect is cancelled.` (17:41:48.816). *This is a bonus observation of the "device came back → cancel" branch working correctly.*
- **Sustained drop via `cmd connectivity airplane-mode enable`** (SSL+~100 s — the mark slipped because the first toggle was consumed by the self-revert; ownCloseMs is > 15000 either way, so the mark does not affect the mechanism):
  - `went away; ending the session in 5000ms unless it comes back.` — **17:43:04.083** (~1 s after the toggle at 17:43:03)
  - `stayed away; ending the session the way the Exit button does.` — **17:43:09.086** (5.003 s later)
  - `AapService: session state disconnected (user_exit)` — 17:43:09.108 → `Native AA user exit. Stopping active launcher.` → `status pill step: hidden` → back to `MainActivity`, **no reconnect**.
- Wall-clock toggle → `stayed away`: **≈ 6 s** (1 s to `went away`, then the 5 s delay).
- Discard: `createGroup SUCCESS`=1, `SSL`=1, `MATCH`=0.

### D3 — known limit, a disconnect at ~SSL+2 s

**PASS** — matches the pre-registered known-limit expectation

- Same D-HU settings as D2. Fresh session, `SSL handshake complete` 17:44:46.814; `NativeAA: BT Handshake socket closed.` 17:44:46.618 (≈ at SSL).
- `cmd connectivity airplane-mode enable` fired the instant SSL logged; ACL dropped ~SSL+1.5 s.
  - `went away; ending the session in 5000ms unless it comes back.` — **17:44:48.272**
  - `not ending the session for DC:B7:2E:5E:4E:59 (up=true, ownCloseMs=6659).` — **17:44:53.275**. `ownCloseMs=6659` < 15000 → **the auto-disconnect rule left the session alone**, exactly as the brief predicts for a loss inside the grace.
- The session did end **9.5 s later** by a different path: `AapService: session state disconnected (link_lost)` at 17:45:02.754 — because airplane mode also killed D-POCO's WiFi and the P2P/TCP transport died. That is not the auto-disconnect grace rule and not a FAIL; with no BT-only lever that holds a drop on this rig (self-revert in ~4 s), a WiFi-preserving version of D3 could not be run.
- `ownCloseMs` value seen: **6659**.
- Discard: `createGroup SUCCESS`=1, `SSL`=1, `MATCH`=0.

---

## Report-back summary (brief §7)

1. **W1 line 1 reason text:** `the link is in the gateway role` (`GATEWAY_ONLY`) — on both W1 and W1b, for D-MOTO, with the car kit's HFP link `Connected` on D-POCO's `HeadsetStateMachine`. **W1b:** exactly one `MATCH! Starting AapService` (17:20:01), `createGroup SUCCESS` 1.2 s later, then the gateway decision + `Calling socket.connect()` for D-MOTO, and a full session.
2. **D1 largest gap** (own-close line → `went away`): **4.418 s**. Every `ownCloseMs` seen across D1/D2/D3: **9420, 6659** (D2's sustained drop had ownCloseMs well over 15000 by SSL+100 s and correctly ended the session; not printed as a number on a `not ending` line). `OWN_SOCKET_CLOSE_GRACE_MS = 15000` clears the measured lag with margin.
3. **W2 `driver candidates:` line, verbatim:**
   `BluetoothHelper: driver candidates: 2 phone, 0 unknown, 3 not a phone - hidden: Navegadortz2 (gateway but class not phone), BC8-Android (hands free unit), Magnetic Speaker (hands free unit)`
   **`native-poke-bt-macs` readback after W2:** `A0:46:5A:97:E4:95` (D-MOTO) alone — the seeded car-kit MAC `11:46:03:10:33:59` is gone.
4. **P1 `status pill step:` ordered list with timestamps:** see P1 table above. **One rank decrease**, `WAITING_FOR_PHONE`(40)→`PREPARING_NETWORK`(20), and it is one capture line after `startNativeAaQuietHost() requested` — the allowed exception. No decrease outside that.
5. **`dumpsys` link preconditions:** recorded per run in `evidence/.../*-precond-*.txt`. Every Part B run had D-HU `Connected` on D-POCO's `HeadsetStateMachine`; D2 had D-POCO's A2dp/HeadsetClient `Connected` on D-HU before launch and 13 s after SSL.

## Verdict roll-up

| Run | Verdict |
|---|---|
| R0 | **PASS** (1616/0) |
| P1 | **PASS** (the point of Part A) |
| P2 | **PASS** |
| P3 | **FAIL** — pill never returns to `ARMED` after the highest stage on the mode-3 path (no give-up; poke loop runs forever). See mechanism note. |
| P4 | **PASS** |
| P5 | no verdict — two lines render cleanly |
| P6 | **INCONCLUSIVE** (pre-registered; no USB host) |
| W1 | **PASS** (the point of the round — gateway-role poke) |
| W1b | **PASS** (reporter's trigger reproduced end to end) |
| W2 | **PASS** (rig caveat: stray Galaxy S24+ bond) |
| W2b | **PASS** |
| W3 | **PASS** (`finished` line preempted by a successful session) |
| D1 | **PASS** — sets `OWN_SOCKET_CLOSE_GRACE_MS` reference: 9420 ms measured, 15000 has margin |
| D2 | **PASS** (via airplane mode; `svc bluetooth disable` self-reverts too fast) |
| D3 | **PASS** — matches the known-limit expectation (`not ending`, ownCloseMs=6659) |

## Anything the brief did not ask about

- **P3 is a real finding, not just a rig limit.** On the Native AA path with an unreachable phone, `NativeAaHandshakeManager`'s poke retry loop runs indefinitely (HFP-AG then HSP-AG, `read failed`, every ~15–45 s) with no give-up and no `beginAttempt`, so the pill latches on `WAKING_PHONE` forever. `ConnectionStagePolicy.shouldApply` then drops the loop's own later `CREATING_NETWORK` (rank 30 < 45) reports. There is no "attempt failed, back to resting" transition for mode 3 to fall back from. Either P3's premise only holds for modes 1/2 (discovery times out), or the mode-3 pill should drop to `ARMED`/`WAITING_FOR_PHONE` between poke rounds.
- **P5 cosmetic:** a faint second rounded-rect outline sits behind the pill's text block (pill background vs. text container, or a press/ripple state). It clips nothing but looks like a doubled border in a still.
- **`ACTION_NATIVE_AA_POKE` is reachable from adb on a non-rooted phone** via `am broadcast -f 0x00000020 -n <pkg>/…automation.AutomationReceiver -a …ACTION_NATIVE_AA_POKE --es extra_mac <MAC>` (`result=0, ok:true`, `AutomationReceiver:` logged). Prior guidance said it was not; the explicit-component + include-background-flag form works. Relevant to [[project_native_aa_poke_trigger_and_bak_gotcha]].
- **`svc bluetooth disable` on D-POCO now self-reverts in ~4 s**, far faster than the ~45 s in §7a's note, and it did so repeatedly this session. It still briefly arms the auto-disconnect timer before reverting, so it is a usable lever for the "device came back → cancel" branch but not for a sustained drop. `cmd connectivity airplane-mode enable` gives a sustained drop (but also kills WiFi).
- **A repeating RFCOMM cleanup to the car kit** (`bt_btif_sock_rfcomm … cleanup_rfc_slot … device: …33:59` every ~5 s, `app_uid` = OHU) ran on D-POCO for the whole time a Part B session/handshake was active — consistent with the HFP-SLC-completion poke against the connected car kit (`native-aa-complete-hfp-slc=true` in D-POCO's prefs). Not investigated further; noted in case it is unexpected churn.
- **Stray Galaxy S24+ (`24:A4:52:CF:70:EF`) bonded to D-POCO** is live and AA-capable in RF range. `native-poke-all-paired=true` reaches it and it completes full AA sessions against our advertised head unit identity — worth knowing the fallback is that broad, and worth un-bonding it from D-POCO before the next `all-paired` round on this rig.
- **D-HU logd clock freeze** (Setup notes) affected P1/P3/P5 timing precision. Line-order is intact; absolute timestamps in the first ~25 s of a D-HU capture are not.

---

## Addendum 2026-09-10 — the pill is covered by the WiFi Direct 5 GHz toast

Post-round observation on the rig screen, then confirmed against the P1 and P5 captures. Filed as a
follow-up commit, not a rewrite of the runs above.

**The status pill is visually hidden during bring-up by `WifiDirectManager`'s own `Toast.LENGTH_LONG`
toasts**, which announce the 5 GHz band request. It is not a pill-state change (`renderStagePill`
never logs `hidden` during a bring-up) — the toast is simply drawn bottom-centre, the same place the
`auto_connect_pill` sits (`[565,625][874,696]` on this 1440×720 display), and covers it.

Source: `WifiDirectManager.notifyNativeGroupStarted()` → `showToast("Native AA WiFi Direct: 5GHz
(<freq>), <mode>")`, plus `showToast("Native AA WiFi Direct started on 2.4GHz. Retrying 5GHz...")`
when the driver brings the group up on 2.4 GHz first, plus the client-unfriendly-channel toast. All
`Toast.LENGTH_LONG` (~3.5 s each). This rig has `wifi-direct-band = 5 GHz only, set by the user`, so
the 5 GHz-request toast fires on **every** Native AA bring-up here.

Correlation in the captures (D-HU, `show-toast-messages=true`):

| run | toast log line | `Toast#… visible=1` layer | pill stage then |
|---|---|---|---|
| P1 | `Native AA WiFi Direct: 5GHz (5220 MHz), unknown` @ 17:01:00.424 | `Toast#219` @ 17:01:00.681 | `WAKING_PHONE` (17:01:00.503) |
| P1 | `Native AA WiFi Direct: 5GHz (5805 MHz), 5GHz requested` @ 17:01:01.149 | `Toast#222` @ 17:01:04.734 | still `WAKING_PHONE` |
| P5 | `Native AA WiFi Direct: 5GHz (5745 MHz), unknown` @ 17:10:30.009 | `Toast#383` @ 17:10:30.199 | `WAKING_PHONE` (17:10:30.048) |
| P5 | `Native AA WiFi Direct: 5GHz (5765 MHz), 5GHz requested` @ 17:10:30.701 | `Toast#386` @ 17:10:34.287 | still `WAKING_PHONE` |

Two back-to-back `LENGTH_LONG` toasts cover the pill for roughly **7–8 s** across the
`CREATING_NETWORK` → `WAKING_PHONE` window — the exact stretch the pill exists to narrate.

**This also explains the "faint second rounded-rect behind the pill" noted under P5.** The P5
screenshot was taken at ~17:10:33, in the ~0.6 s gap between `Toast#383` expiring and `Toast#386`
appearing; the faint shape is a toast fading, not a pill artifact. The pill design itself is fine.

Not a P-run verdict change. For the coding side: on a unit with the 5 GHz band pinned, the pill and
these toasts compete for the same screen real estate on every connect. Options are to suppress the
`notifyNativeGroupStarted` toast when the pill is showing, move the pill clear of the toast anchor,
or fold the band/frequency into the pill's own second line.
