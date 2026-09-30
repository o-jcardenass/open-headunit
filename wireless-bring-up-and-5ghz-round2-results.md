# wireless-bring-up-and-5ghz — round 2 results

**Brief:** `wireless-bring-up-and-5ghz-round2-brief.md`, revision 2
**Candidate:** `fix/wireless-bring-up-and-5ghz-channel` @ `e68636db`, merged into
`testing/wireless-plus-automation` @ `5753dfd9` (`5753dfd9a63c5d2b88a02e6babb60259af7f8059`) with
`feat/automation-command-surface` @ `41a3866b` as the instrument.
**Built tree:** `testing/wireless-plus-automation`, build stamp `5753dfd9a63c` (not dirty)
**APK md5:** `ba6293923a059f4503931503a47159d2` (MT50)
**Unit gate:** 1389 / 0 (`testGithubDebugUnitTest`)
**Units:**
- R1, R6, R7, R8: MT50 (`MT50_YT610E4GFPSL_U`, UNISOC T610, Android 14, 1440x720), joined to
  `Pegue Cdesta` at 5500 MHz. Poked / connecting phone = POCO X3 NFC (`DC:B7:2E:5E:4E:59`).
- R5: POCO X3 NFC (`M2007J20CG`, Android 13) as head unit over wireless adb (`192.168.1.10:5555`),
  a phone behind a Carlinkit dongle on its USB port. APK md5 `ba6293923a059f4503931503a47159d2`.

**Date:** 2026-09-05

## Verdicts

| Run | Verdict | One line |
|---|---|---|
| R0 build gate | **PASS** | stamp `5753dfd9a63c`, `PokeOverlapPolicy` + `AutomationCommandPolicy` in the DEX, gate 1389/0, `QUERY_STATE` `commit`=`5753dfd9a63c` |
| R6 manual poke no longer races the automatic one | **FAIL** | the "another poke is already connecting" line fires (once, 3/3), but the manual poke's 4000 ms wait expires ~11 s before this rig's ~15.4 s Bluetooth-off `connect()` returns, so a second `socket.connect()` for the same MAC still opens and the two overlap |
| R7 a poke failure says why at the default level | **PASS** | `NativeAA: Poke via HFP-AG to POCO X3 NFC (DC:B7:2E:5E:4E:59) failed: read failed, socket might closed or timeout, read ret: -1` at `I/`, `log-level=2` |
| R8 a cancelled poke stops after one record | **PASS** | the cancelled automatic poke logs exactly one `Calling socket.connect()` and no `HSP-AG` after it, 3/3 |
| R1 an ordinary Native AA session still forms | **PASS** | `AapService creating` -> `SSL handshake complete` **21.4 s** (round 1: 21.2 s); 1 group, 1 SSL, no churn |
| R5 the USB hold now says how it ended | **PASS** (backstop path) | `the USB attempt did not settle within 8475ms — arming wireless anyway` appears once, hold entirely before any `createGroup`, wireless armed; round 1 saw neither line |

---

## Setup notes

### Deviations / conditions

1. **`native-driver-selection-mode` set to `0` for every run**, restored to the rig baseline `2`
   afterwards, exactly as round 1. `log-level=2` for R6/R7/R8, `0` for R1. `native-poke-bt-macs`
   already held only the POCO's MAC (`DC:B7:2E:5E:4E:59`) and was left as-is; it read back unchanged
   after each launch. `wifi-connection-mode=3`, `wifi-5ghz-channel=0` throughout.

2. **`ACTION_SET_SETTINGS` / `ACTION_SET_LOG_LEVEL` not used** (gated behind
   `allow-external-configuration`, and the brief says this round does not use them). Settings went
   into `settings.xml` with the app force-stopped, via `set_hu_prefs.sh`.

3. **The automation receiver worked as specified.** `AutomationReceiver: <action>` logged at INFO
   for every `send`; `-f 0x00000020` delivered to the force-stopped package; `ACTION_QUERY_STATE`
   replied on `data=` with `"commit":"5753dfd9a63c"`; `ACTION_LOG_MARKER` wrote
   `AutomationMarker: <label>` at WARN; `ACTION_NATIVE_AA_POKE --es extra_mac <MAC>` reached
   `manualPoke` (logged `NativeAA: Manual poke requested for POCO X3 NFC (DC:B7:2E:5E:4E:59)`), with
   no `HomeFragment` / `Auto-connect: begin` / pill, as the brief anticipated.

4. **R6's timed trigger is a file poll, not a `logcat | grep -m1` pipeline.** `adb logcat` never
   exits on its own, so `... | grep -m1 ... && send ...` hangs the subshell after the match and the
   broadcast never fires (first R6 attempt, `r6`, was void for exactly this). The working watcher
   polls the growing capture file every 200 ms for a *new* `Calling socket.connect() for POCO X3 NFC`
   and fires the broadcast the instant one appears. Landed 0.18-0.24 s after the automatic
   `socket.connect()` in all three valid runs.

5. **This rig's Bluetooth-off `socket.connect()` blocks ~15.4 s, not the ~3 s the brief's §4
   assumes.** The brief expected "its `socket.connect()` then blocks for the ~3.05 s a refusal
   takes". With the POCO's Bluetooth genuinely `state: OFF`, the RFCOMM connect on the MT50 runs the
   full Android connect timeout and fails with `read failed, socket might closed or timeout, read
   ret: -1` after **15.40-15.46 s** (measured, three runs), per profile. The reporter's POCO/Moto
   capture in the brief shows a ~0.4 s active refusal; that is a phone that is *present and
   refusing*, not one that is *off*. This mismatch is the whole of R6's FAIL - see below.

6. **R5 rig needed an operator setup mid-round.** The POCO started the session with the PC's adb
   cable in its USB port and would not re-join WiFi through any adb verb (`Uid 2000 does not have
   access to enable-network` / `reconnect`; a `svc wifi` cycle did not re-associate). It was
   switched to `adb tcpip 5555`, then the operator tapped it onto `Pegue Cdesta`, freed the USB
   port, and plugged in a phone behind the Carlinkit dongle. The candidate
   (`ba6293923a059f4503931503a47159d2`) installed over the round-1 debug build; prefs set with
   `set_prefs_runas.sh` (`wifi-connection-mode=3`, `native-ap-transport=0`, `wifi-direct-band=1`,
   `wifi-5ghz-channel=0`, `native-driver-selection-mode=0`, `log-level=0`). Pre-round `settings.xml`
   captured (`evidence/.../settings-backup-poco.xml`); `wifi-direct-band` restored to its pre-round
   `0` afterwards; the POCO keeps the debug candidate (its release build is not available locally,
   same as round 1).

### Scripts

- New: `hur-wifi-test-scripts/wireless_bringup_5ghz_r2_poke.sh` (R6/R7/R8: MT50 Native AA bring-up
  with the POCO's Bluetooth off, file-poll watcher that fires `ACTION_NATIVE_AA_POKE` the instant the
  automatic poke enters `connect()`; judged on poke lines only, no session).
- New: `hur-wifi-test-scripts/wireless_bringup_5ghz_r2_usbhold.sh` (R5: POCO as head unit over
  wireless adb, polls `/dev/bus/usb` and launches on a device-present window so the deferral engages;
  greps the two new hold-ended lines).
- Used: `build_hur.sh`, `run_unit_tests.sh`, `set_hu_prefs.sh`, `wireless_bringup_5ghz_run.sh` (R1),
  `set_prefs_runas.sh` (POCO).

---

## R0 — build gate

**PASS**

- `./gradlew :app:assembleGithubDebug` -> `Building from commit: 5753dfd9a63c` (no `-dirty`).
- `unzip -p <apk> 'classes*.dex' | strings | grep -cF`: `PokeOverlapPolicy` = **5**,
  `AutomationCommandPolicy` = **27**. Round-1 APK's `f44bc175...` would read 0 on both.
- Unit gate: **1389 / 0** (matches the brief; 1358 candidate-only + the automation branch's tests).
- `send ACTION_QUERY_STATE` ->
  `{"action":"...ACTION_QUERY_STATE","versionName":"3.3.1","versionCode":105,"commit":"5753dfd9a63c","flavor":"github","connected":false,"wireless":false,"wifiMode":"NATIVE","state":"Disconnected","ok":true}`
- APK md5 `ba6293923a059f4503931503a47159d2`, installed `adb install -r`, on-device md5 matches.

---

## R6 — a manual poke no longer races an automatic one

**FAIL** — the two `socket.connect()` calls for the same MAC still overlap in time.

Three valid runs (`r6b`, `r6c`, `r6d`); one earlier attempt (`r6`) was **void** (watcher pipeline
hung, broadcast never landed - Setup note 4). The three valid runs are identical in shape.

### `r6b` timeline (the reference run)

| Time | Thread | Line |
|---|---|---|
| 21:15:48.906 | [2] | `WifiDirectManager: 5GHz createGroup SUCCESS!` |
| 21:15:50.575 | [2] | `AutomationMarker: r6-armed` |
| 21:15:51.230 | [62] | `NativeAA: Attempting active poke to device: POCO X3 NFC (DC:B7:2E:5E:4E:59)...` (automatic) |
| 21:15:51.246 | [62] | `NativeAA: Calling socket.connect() for POCO X3 NFC via HFP-AG (...)` — **1st socket** |
| 21:15:51.483 | [2] | `AutomationReceiver: com.andrerinas.openheadunit.ACTION_NATIVE_AA_POKE` (broadcast landed, +0.24 s) |
| 21:15:51.502 | [2] | `NativeAA: Manual poke requested for POCO X3 NFC (DC:B7:2E:5E:4E:59)` |
| 21:15:51.512 | [61] | `NativeAA: another poke is already connecting to POCO X3 NFC — waiting for it rather than opening a second socket.` |
| 21:15:55.593 | [61] | `NativeAA: Attempting manual poke to POCO X3 NFC...` (+4.08 s after the broadcast — the 4000 ms `CONNECT_SETTLE_WAIT_MS` bound plus one poll) |
| 21:15:55.603 | [61] | `NativeAA: Calling socket.connect() for POCO X3 NFC via HFP-AG (...)` — **2nd socket, opened while the 1st is still blocking** |
| 21:16:06.670 | [62] | `NativeAA: Poke via HFP-AG to POCO X3 NFC (...) failed: read failed, socket might closed or timeout, read ret: -1` — 1st socket closes, **15.42 s** after it opened |
| 21:16:22.073 | [61] | `NativeAA: Poke via HFP-AG to POCO X3 NFC (...) failed: ...` — 2nd socket closes |
| 21:16:22.080 | [61] | `NativeAA: Calling socket.connect() ... via HSP-AG (...)` (manual poke's 2nd record) |
| 21:16:37.470 | [61] | `NativeAA: Poke via HSP-AG ... failed: ...` |
| 21:16:37.477 | [61] | `NativeAA: Manual poke to POCO X3 NFC finished.` |
| 21:16:49.8-50.9 | [74/75/76] | `NativeAA: a chosen driver's wake poke is running — not replacing it with the multi-device loop.` ×3 |

### Against the four PASS criteria

1. `another poke is already connecting to POCO X3 NFC — waiting for it ...` appears **exactly once**
   in every run (r6b/r6c/r6d). **Met.** Overlap *detection* works.
2. "no second `Calling socket.connect()` for the same MAC between the automatic poke's connect and
   its outcome." **Not met.** The manual poke's `Calling socket.connect()` (21:15:55.603) falls
   between the automatic poke's connect (21:15:51.246) and its outcome (21:16:06.670). The two
   sockets are open concurrently for **~11.1 s** (r6b), ~11.2 s (r6c), ~11.4 s (r6d).
3. "The manual poke's `Attempting manual poke to ...` comes after the automatic attempt's outcome."
   **Not met.** `Attempting manual poke` at 21:15:55.593, automatic outcome at 21:16:06.670 —
   manual comes ~11 s *before* it.
4. Failure lines name a profile and a reason. **Met.**

Criteria 2 and 3 fail, so the run is **FAIL** by the brief's own definition ("two `Calling
socket.connect()` lines for the same MAC overlap in time").

### Not a void

The pre-registered voids do not apply. `Attempting manual poke` landed 4.08 s after the automatic
`socket.connect()` (inside the ~15.4 s window, not "more than ~3 s after" or "before" it — the ~3 s
in the brief was a wrong estimate of this rig's window), the broadcast demonstrably landed
(21:15:51.483, +0.24 s), and no handshake landed during the wait. The overlap is real and
reproducible 3/3.

### Mechanism

`PokeOverlapPolicy.CONNECT_SETTLE_WAIT_MS = 4_000L` is documented as sized for "The observed HFP-AG
refusal takes ~3.05 s". On the reporter's phone (present, actively refusing) that holds. On this
rig with the phone's Bluetooth **off**, `socket.connect()` runs the full ~15.4 s Android RFCOMM
connect timeout. The manual poke logs the "waiting" line, polls `pokeConnectingTo` for 4000 ms,
then `PokeOverlapPolicy.step(...)` returns `PROCEED` (`waitedMs >= CONNECT_SETTLE_WAIT_MS`) and the
manual poke opens its own socket — ~11 s before the automatic one's `finally` clears
`pokeConnectingTo`. Result: two concurrent RFCOMM sockets to the same phone, all records failing,
no session — the exact symptom family (`read ret: -1` on every record) the commit set out to
remove, just with 3 failed records instead of 4.

### What did work (the commit's other three behaviours)

- **Overlap detection** — the "another poke is already connecting" line, once per run, 3/3.
- **Cancelled automatic poke stops after one record** — R8, below.
- **Multi-device loop stands aside** — `a chosen driver's wake poke is running — not replacing it
  with the multi-device loop.` fired 3× in r6b when a `triggerPoke` cycle coincided with the manual
  poke. (r6c/r6d capture windows ended before the next `triggerPoke`, so 0 there — not a
  regression, just timing.)

### Suggested fix direction (not verified)

Either raise `CONNECT_SETTLE_WAIT_MS` to outlast a Bluetooth-off connect timeout (~16 s), or — when
the bound expires with `pokeConnectingTo` still equal to `target` — have the manual poke **abandon**
rather than `PROCEED` (the automatic poke is already reaching this phone; a second socket adds
nothing but the overlap). The latter keeps the wait short in the common case.

**Delay `AutomationReceiver: ACTION_NATIVE_AA_POKE` -> `Attempting manual poke`:** 4.11 s / 4.11 s /
4.11 s (r6b/r6c/r6d) — the 4000 ms overlap-wait bound plus one 100 ms poll.

---

## R7 — a poke failure says why, at the default log level

**PASS** (read out of R6's captures)

- `log-level=2` (INFO). Line, quoted verbatim from `r6b` (21:16:06.670, level `I/`):

  ```
  NativeAA: Poke via HFP-AG to POCO X3 NFC (DC:B7:2E:5E:4E:59) failed: read failed, socket might closed or timeout, read ret: -1
  ```

- Present at INFO in all three runs, for both the HFP-AG and HSP-AG records, each naming the profile
  and carrying the reason. The pre-fix behaviour (`AppLog.d`, invisible at INFO) is gone.

---

## R8 — a cancelled poke stops after one record

**PASS** (read out of R6's captures)

The automatic poke is the coroutine the manual poke cancelled (`pokeJob?.cancel()`):

| Run | Automatic thread | Its `Calling socket.connect()` lines | `HSP-AG` after its `HFP-AG`? |
|---|---|---|---|
| r6b | [62] | 1 (`HFP-AG` 21:15:51.246 only) | no |
| r6c | [62] | 1 (`HFP-AG` 21:17:57.626 only) | no |
| r6d | [60] | 1 (`HFP-AG` 21:19:07.956 only) | no |

Every `HSP-AG` connect in the captures belongs to the *manual* poke's thread. The automatic poke,
once cancelled, hits `currentCoroutineContext().ensureActive()` at the top of the second loop
iteration and stops after its first record instead of spending the second — the
`ensureActive()` addition works.

Note the pairing the brief calls out: R8 passing while R6 fails means the cancellation check took,
but the 4000 ms overlap-wait bound is simply too short for this rig's connect timeout. The two are
independent; the cancellation half is sound.

---

## R1 — an ordinary Native AA session still forms (regression guard)

**PASS**

- Settings: `wifi-connection-mode=3`, `native-ap-transport=0`, `wifi-direct-band=1`,
  `wifi-5ghz-channel=0`, `native-driver-selection-mode=0`, `log-level=0`. POCO's Bluetooth + WiFi
  on.
- MT50 station: `Pegue Cdesta` 5500 MHz, `Supplicant state: COMPLETED`, RSSI -31 -> -26 through the
  run.
- Decisive lines (`hu_r1.txt`):
  - `21:20:56.576  AapService.onCreate | AapService creating...`
  - `21:20:57.000  StationStandDown: this unit has left its WiFi network.` (unconditional stand-down, no setting)
  - `21:20:57.559  WifiDirectManager: 5GHz createGroup SUCCESS!` (single)
  - `21:21:17.713  WirelessServer: Incoming connection detected from /192.168.49.50`
  - `21:21:17.993  SSL handshake complete. Session id: JHTHeI/wiy9E/...`
- **`AapService creating` -> `SSL handshake complete`: 21.42 s** (21:20:56.576 -> 21:21:17.993).
  Round 1 read 21.2 s; the ~32 s bound is not approached.
- Discard-rule: 1 `createGroup SUCCESS`, 1 SSL session, 1 `AapService creating`, 1 `Incoming
  connection detected`. `MATCH! Starting AapService` = 1 (benign poke self-wake, no second group).
  The capture shows `p2p-wlan0-10` (a stale group left from `r6d`, removed at 21:20:57.005
  `P2P-GROUP-REMOVED ... reason=REQUESTED`) then `p2p-wlan0-11` created for this session — one group
  formation, not churn.
- Group frequency (`dumpsys wifip2p`): `frequency: 5240` (automatic pick; round 1 picked 5240 in one
  run, 5805 in another — within the band, as expected).
- Station rejoined `Pegue Cdesta` COMPLETED at teardown + 5 s.
- `StationStandDown.restore()` logged its WARN branch (`the platform refused to re-enable this
  unit's WiFi network`), the standing rig quirk from round 1 note 4; the actual rejoin is the signal
  and it happened.

---

## R5 — the USB hold now says how it ended

**PASS** (via the 8 s backstop; the "settled" line did not occur, and should not have here)

- POCO as head unit over wireless adb, a phone behind a Carlinkit dongle on the USB port,
  `host_connected=true`, devices on `/dev/bus/usb/001` and `002` at launch. `log-level=0`.
- Launch timed to a device-present window (21:31:26.004), so the deferral had something to act on at
  `onCreate`.
- Decisive lines (`poco_r5.txt`):
  - `21:31:26.945  AapService.deferWirelessForUsbHandoff | AapService: a USB projection attempt is in flight — holding the wireless bring-up for up to 8000ms`
  - repeated at :28.377, :29.386, :30.391, :31.399, :32.406, :33.414, :34.417 — **8 in-flight lines, ~7.5 s of holding**
  - `21:31:35.421  AapService.deferWirelessForUsbHandoff | AapService: the USB attempt did not settle within 8475ms — arming wireless anyway`  ← **the new backstop line**
  - `21:31:35.459  WifiDirectManager: startNativeAaQuietHost() requested. Removing old group if any...`
  - `21:31:36.060  WifiDirectManager: Attempting createGroup for Native AA (Attempt 0)...`
  - `21:31:36.083  WifiDirectManager: 5GHz createGroup SUCCESS!`
- **The hold is entirely before any `createGroup`** — first in-flight line 21:31:26, first
  `createGroup` 21:31:36; `createGroup SUCCESS` during the hold = 0.
- `USB handoff settled after` = **0**, `the USB attempt did not settle within` = **1**,
  `startNativeAaQuietHost` = 1, `createGroup SUCCESS` = 1.
- **Which of the two lines: the backstop.** N = 8475 ms (≥ the 8000 ms `DEFER_BUDGET_MS`; the ~1 s
  recheck interval overshoots slightly). The flaky Carlinkit link never completed an AAP session in
  the window (`SSL handshake complete` = 0, same as round 1), so the USB attempt genuinely did not
  settle and the budget backstop is the correct outcome — and now it *says so* instead of being
  silent, which is the whole of this fix. Round 1 saw neither line (the only line that existed then
  was guarded on a nulled job and unreachable).
- Single attempt; no need for a second (the deferral engaged, bounded itself, released with wireless
  armed).

---

## Answers to the brief's §9

1. **R6 outcome:** FAIL. `AutomationReceiver: ACTION_NATIVE_AA_POKE` -> `Attempting manual poke` =
   4.11 s, 3/3 runs (the 4000 ms overlap bound + one poll). Landing inside the window took **1**
   attempt per run once the watcher was fixed (the first attempt, `r6`, was void on a script bug,
   not a missed window).
2. **R7 failure line, at `log-level=2`:**
   `NativeAA: Poke via HFP-AG to POCO X3 NFC (DC:B7:2E:5E:4E:59) failed: read failed, socket might
   closed or timeout, read ret: -1` — carries a reason. PASS.
3. **R1 service-start to `SSL handshake complete`:** 21.42 s, against round 1's 21.2 s. PASS.
4. **R5:** the backstop line —
   `AapService: the USB attempt did not settle within 8475ms — arming wireless anyway` — came back
   (the flaky dongle link never settled, so the 8 s budget fired; round 1 hit this same path but
   silently). PASS.

**Watch item:** `NativeAA: a chosen driver's wake poke is running — not replacing it with the
multi-device loop.` **does** appear after the manual poke wins (r6b, 3×), so the automatic
multi-device loop correctly stays out of the way. The other half of the mutual exclusion is sound;
it is only the connect-overlap serialisation (the 4 s bound vs this rig's 15.4 s connect) that
fails.

## Evidence

`evidence/wireless-bring-up-and-5ghz-round2/`:

- `hu_r6b.txt.gz`, `hu_r6c.txt.gz`, `hu_r6d.txt.gz`, `hu_r1.txt.gz`, `phone_r1.txt.gz`,
  `poco_r5.txt.gz` — full logcat
- `r6b_console.txt`, `r6c_console.txt`, `r6d_console.txt`, `r1_console.txt`, `r5_console.txt` —
  decisive excerpts
- `wifip2p_r1.txt`, `hu_station_after_r1.txt`, `hu_station_teardown_r1.txt`
- `settings-backup-mt50.xml`, `settings-backup-poco.xml`
