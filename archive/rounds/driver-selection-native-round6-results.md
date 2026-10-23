# driver-selection-native — round 6 results

**Candidate:** `fork/testing/driver-selection-plus-automation` @ `7520686c` (stamp `7520686c2578`)
**Baseline:** none this round (no A/B; the brief carries rounds 1-5 numbers inline)
**APK md5:** `55651f18912fda91fbae92c2b6c3e713`
**Unit:** UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14, D-HU; phones D-MOTO (motorola edge 30 neo, `A0:46:5A:97:E4:95`) and D-POCO (POCO X3 NFC, `DC:B7:2E:5E:4E:59`), both bonded to D-HU throughout
**Date:** 2026-09-05

## Verdict summary

| Run | Verdict |
|---|---|
| R0 build gate | **PASS** |
| R14 switch reaches the chosen phone while the outgoing phone still holds its link | **PASS** (all 6 criteria, incl. the override line — precondition held on the attempt that counted) |
| R15 selector names a phone that is not there | **PASS** (all 3 criteria; not the unreadable-stack path) |
| R12 a phone that is there still gets the full screen at once | **PASS** |
| R10 no phone, no starting screen | **PASS** |
| R1 headless bring-up | **PASS** |

The watch item — the override line firing where no hands-free link exists — **did not occur**: zero
override lines in R1 and zero in R10.

## Setup notes

- Inventory: `hur-wifi-test-scripts/` scripts used — `build_hur.sh`, `run_unit_tests.sh` (R0),
  `set_hu_settings_host.py` (every settings write: scalars, `setclear` for the `<set>` MAC lists).
  No new script needed.
- `settings.xml` backed up byte-identical at the start (`md5 8680de20674ee9fa02e59076e18a931e`) and
  restored to it at the end (`cp` + `chown 10174:10174` + `chmod 660`, md5 re-checked identical).
  The device-protected mirror carried a stale `auto-start-bt-macs` entry (D-POCO's MAC, left over
  from a prior session — the start-of-round `settings.xml` had the set empty); it was cleared to an
  empty set to match, and left that way (matches the original mirror backup).
- Both phones started the round with Bluetooth **on**, WiFi **on**; restored to that at the end.
  Radio state was varied per run as the brief specifies and is recorded under each run.
- Candidate APK confirmed installed before and after the round (`pm path` + `md5sum` =
  `55651f18912fda91fbae92c2b6c3e713`).
- Log level `2` for every run. Every `send` produced its `AutomationReceiver:` line.
- **Capture volume.** The outgoing phone's RFCOMM reconnect flood (brief §2, "bounded, costs log
  volume not a session") is large on this rig: R14b logged **602** `Connection accepted from
  motorola edge 30 neo` cycles in ~2.5 min (round 5 measured 282). Raw captures are kept gzipped;
  `*_openhu.txt` in evidence is the OPENHU-line extract with that flood and framework spam stripped.

### R14 precondition — how it was obtained, and what did not work

R14 tests nothing unless D-MOTO holds a **live** hands-free link to D-HU at the instant of the
switch (brief §3). On this rig that state is not the default: after a Native AA session forms, the
poke socket closes at handoff (`NativeAA: BT Handshake socket closed`) and the projecting phone
does **not** hold an independent HFP profile link — `HeadsetClientStateMachine` reads `Disconnected`
and `dumpsys` shows the phone's `ConnectionState: STATE_DISCONNECTED` to D-HU.

- **Toggling D-MOTO's own Bluetooth off/on did nothing.** Two full cycles, 75 s of settle each:
  D-MOTO never re-established any BT profile link to D-HU (`ConnectionState: STATE_DISCONNECTED`
  throughout; D-HU `HeadsetClientStateMachine curState=Disconnected`, record count unchanged).
- **Toggling D-HU's Bluetooth off/on worked.** Within 15 s of D-HU's adapter coming back, **both**
  phones reconnected their profiles: D-MOTO `ConnectionState: STATE_CONNECTED`, D-HU
  `HeadsetClientStateMachine curState=Connected`, D-HU AG-role `curState=Connected`,
  `A2DPSinkStateMachine curState=Connected`. Stable across 60 s, and still `Connected` when
  re-checked at the back-press immediately before the switch (`evidence/.../r14_precond_hfpclient.txt`).

This matches round 5's corrected finding: round 5 attempts 1-2 had the link by luck of the rig's BT
timing and attempt 3 did not. Round 6 set it on purpose via the D-HU adapter toggle.

### R15 — how "picking" a phone has to be done

`ACTION_NATIVE_AA_POKE --es extra_mac <MAC>` routes straight to
`NativeAaHandshakeManager.selectDriver` and never calls `HomeFragment.connectToNativeDevice`, so it
produces the poke/override lines but **no** `Connecting to Native-AA device:` / `Auto-connect: begin`
line and **no** pill. R15's criteria 2-3 (btConnected, mode, pill text/id) live on the fragment
row-click path, so R15 was run with a real `input tap` on the D-POCO selector row. A first R15
attempt using the broadcast is kept (`r15_switchphone-attempt.txt.gz`) — it shows the poke path is
correct (override line present, `Calling socket.connect() for POCO`) but is silent on the pill.

Also: with one phone BT-connected and set as `native-preferred-device-mac`, launch logs
`HomeFragment: Unambiguous driver (...) - auto-connecting directly without prompt` and never shows
the selector. R15's selector therefore comes from **Switch Phone** on a live D-MOTO session, not
from a launch prompt.

---

## R0 — build gate

**PASS**

- `build_hur.sh` printed `Built and copied: com.andrerinas.headunitrevived_3.3.1_debug.apk`; the
  APK DEX carries `commit=7520686c2578` (no `-dirty`), and the build log shows
  `SLogExporter: session | build=3.3.1 (105) github/debug commit=7520686c2578`.
- `ACTION_QUERY_STATE` replied
  `{"versionName":"3.3.1","versionCode":105,"commit":"7520686c2578","flavor":"github",...}` — matches.
- Unit gate: **1357 / 0** (`app/build/test-results/testGithubDebugUnitTest`, 126 XML files summed:
  `tests=1357 failures=0 errors=0 skipped=0`) — matches the brief exactly.
- APK md5 `55651f18912fda91fbae92c2b6c3e713`, confirmed installed (`pm path` + `md5sum`).
- Two new commits present on the tree: `876627e3` "a driver switch wakes the phone it chose",
  `c77806ac` "the driver selector says which phones are actually there".

---

## R14 — the switch reaches the chosen phone while the outgoing phone still holds its link

**PASS** — all six criteria. This is the run the round exists for.

**Precondition (step 2):** `dumpsys bluetooth_manager` on D-HU read
`HeadsetClientStateMachine ... curState=Connected` (and AG-role `Connected`,
`A2DPSinkStateMachine Connected`, D-MOTO `ConnectionState: STATE_CONNECTED`) — obtained by toggling
D-HU's Bluetooth adapter (D-MOTO's own toggle did not work; see Setup notes). Re-verified
`Connected` at the back-press. Full dump in `evidence/.../r14_precond_hfpclient.txt`.

**First session — D-MOTO (reachable):**

| time | line |
|---|---|
| 15:25:32.974 | `HomeFragment: Connecting to Native-AA device: motorola edge 30 neo (A0:46:5A:97:E4:95), btConnected=true` |
| 15:25:32.975 | `Auto-connect: begin (Native-AA driver: motorola edge 30 neo, mode=OVERLAY)` |
| 15:25:34.387 | `WifiDirectManager: 5GHz createGroup SUCCESS!` (`P2P-GROUP-STARTED ... ssid="DIRECT-TX-Navegadortz2" freq=5805`) |
| 15:25:46.386 | `WirelessServer: Incoming connection detected from /192.168.49.189` |
| 15:25:46.657 | `SSL handshake complete` |
| 15:25:48.612 | `VideoDecoder ... First frame rendered (hardware decode)` |

**The switch:**

| time | line |
|---|---|
| 15:26:46.609 | `AapService: ACTION_NATIVE_AA_SWITCH_DEVICE received (targetMac=null)` |
| 15:26:46.609 | `NativeAA: a driver switch is starting, so A0:46:5A:97:E4:95 is not let straight back in.` |
| 15:26:46.653 | `AapTransport stopping and sending byebye (DEVICE_SWITCH)` |
| 15:26:47.836 | `AapService: Native AA session ended; keeping the WIFI_DIRECT network up for the phone's return.` |
| 15:27:01.322 | `AutomationMarker: R14b-pick-POCO` (poke broadcast for `DC:B7:2E:5E:4E:59`) |
| 15:27:01.412 | `NativeAA: Driver selected: DC:B7:2E:5E:4E:59` |
| 15:27:01.422 | `NativeAA: Attempting manual poke to POCO X3 NFC...` |
| **15:27:01.429** | **`NativeAA: poking POCO X3 NFC (DC:B7:2E:5E:4E:59) even though a hands-free link is up — that link is another phone's, not this one's.`** |
| **15:27:01.439** | **`NativeAA: Calling socket.connect() for POCO X3 NFC via HFP-AG (0000111f-...)...`** |
| 15:27:02.522 | `NativeAA: Successfully poked POCO X3 NFC via HFP-AG. Holding 20000ms...` |
| 15:27:02.828 | `NativeAA: Connection accepted from POCO X3 NFC (DC:B7:2E:5E:4E:59) on local radio [Navegadortz2]` |
| 15:27:10.246 | `WirelessServer: Incoming connection detected from /192.168.49.50` |
| 15:27:10.519 | `SSL handshake complete` (session id `uDAutnQ/...`, distinct from D-MOTO's `nGezUJR5...`) |
| 15:27:11.001 | `AapService: session state projecting` |
| 15:27:12.281 | `First frame rendered (hardware decode)` |

**PASS criteria:**

1. ✅ `NativeAA: Calling socket.connect() for POCO X3 NFC` present (15:27:01.439). Round 5 attempts
   1-2 had **zero**.
2. ✅ Zero `Not poking POCO X3 NFC` after the switch. The only `Not poking` line in the whole
   capture is at 15:25:34.790 and names **motorola edge 30 neo** — the correct pre-switch skip of
   the poke to the phone that held the link during the initial connect.
3. ✅ Override line present and names D-POCO (15:27:01.429).
4. ✅ Second `SSL handshake complete` belongs to POCO X3 NFC on `192.168.49.50` — distinct from
   D-MOTO's `.189`. Exactly round 4/5's `.50` vs `.189`.
5. ✅ `NativeAA: a driver switch is starting, so A0:46:5A:97:E4:95 is not let straight back in.`
   present (15:26:46.609).
6. ✅ `createGroup SUCCESS` count = **1** for the whole run (`p2p-wlan0-1` interface index only;
   one `P2P-GROUP-STARTED`). The group stayed up across the switch.

**Measurements:**

- Pick → `Calling socket.connect()`: `Driver selected` 15:27:01.412 → 15:27:01.439 = **27 ms**
  (from the poke broadcast at 15:27:01.322 = 117 ms).
- `Calling socket.connect()` → POCO RFCOMM landing (`Connection accepted from POCO`): **1.389 s**
  (`Successfully poked` at 1.083 s). Round 5's clear-radio control read 636 ms / ~4.9 s; this run's
  contended radio was **not** slower to the RFCOMM landing.
- `Calling socket.connect()` → second `SSL handshake complete`: **9.08 s**.
- D-MOTO won nothing back: no third handshake, session is D-POCO's.

**Noted:** the outgoing phone's RFCOMM reconnect flood ran to **602** accept/close cycles (round 5:
282). Bounded, cost log volume only, session unaffected — per brief §2, not a failure.

---

## R15 — the selector names a phone that is not there

**PASS** — all three criteria. D-POCO Bluetooth **off**, D-MOTO Bluetooth **on**, live D-MOTO
session, Switch Phone opened, selector read and screenshot taken untouched, then D-POCO's row tapped.

**Criterion 1 — row labels** (`evidence/.../r15c_selector.{png,xml}`):

| row | name | status text |
|---|---|---|
| D-MOTO | `motorola edge 30 neo` | `🟢 In vehicle / Connected` + `⭐ Preferred` |
| D-POCO | `POCO X3 NFC` | `Disconnected, will be woken` |

Neither row reads `Disconnected` for a phone that is in fact connected. **Not** the unreadable-stack
path — the rows carry distinct labels (`In vehicle / Connected` vs `Disconnected, will be woken`),
not both the neutral `Paired`.

**Criteria 2-3 — picking D-POCO** (`input tap` on the row, `evidence/.../r15c_pill.{png,xml}`):

| time | line |
|---|---|
| 15:34:25.305 | `AutomationMarker: R15c-tap-POCO-row` |
| 15:34:25.508 | `HomeFragment: Connecting to Native-AA device: POCO X3 NFC (DC:B7:2E:5E:4E:59), btConnected=false` |
| 15:34:25.509 | `Auto-connect: begin (Native-AA driver: POCO X3 NFC, mode=PILL_THEN_OVERLAY)` |
| 15:34:25.547 | `NativeAA: Driver selected: DC:B7:2E:5E:4E:59` |

- Pill on screen (dump +3 s): node `com.andrerinas.headunitrevived:id/auto_connect_pill` present,
  child `auto_connect_pill_text` = **`POCO X3 NFC is disconnected, waking it...`** (not
  `Android Auto is starting`).
- `com.andrerinas.headunitrevived:id/auto_connect_loading_overlay` **absent** from the dump.
- `btConnected=false` and `mode=PILL_THEN_OVERLAY` agree.

---

## R12 — a phone that is there still gets the full screen at once

**PASS**

Both radios on, D-MOTO HFP link `Connected`, launch, countdown resolves to D-MOTO unambiguously.

| time | line |
|---|---|
| 15:35:13.627 | `HomeFragment: Unambiguous driver (motorola edge 30 neo) - auto-connecting directly without prompt` |
| 15:35:13.628 | `HomeFragment: Connecting to Native-AA device: motorola edge 30 neo (A0:46:5A:97:E4:95), btConnected=true` |
| 15:35:13.629 | `Auto-connect: begin (Native-AA driver: motorola edge 30 neo, mode=OVERLAY)` |
| 15:35:16.989 | dump taken (~3.4 s after `beginAutoConnect`, pre-handshake) |
| 15:35:19.619 | `SSL handshake complete` |
| 15:35:21.471 | `First frame rendered (hardware decode)` |

Dump (`evidence/.../r12_dump.{png,xml}`): `auto_connect_loading_overlay` present (with
`auto_connect_loading_cancel`, `_loading_default_content`, `_loading_default_text`);
`auto_connect_pill` **absent**. `btConnected=true` and `mode=OVERLAY` agree. The fix is not too
eager on the reachable case.

---

## R10 — no phone, no starting screen

**PASS**

Both phones' Bluetooth **and** WiFi off (confirmed `state: OFF` / `Wi-Fi is disabled` on each),
`native-preferred-device-mac` = D-MOTO, mode 1, timeout 10, `native-poke-bt-macs` and
`last-connected-native-mac` cleared and read back empty.

| time | line |
|---|---|
| 15:36:23.892 | `AapService: ACTION_NATIVE_AA_PROMPT_SHOWN received` |
| 15:36:24.429 | `WifiDirectManager: 5GHz createGroup SUCCESS!` |
| 15:36:33.891 | `HomeFragment: Connecting to Native-AA device: motorola edge 30 neo (A0:46:5A:97:E4:95), btConnected=false` |
| 15:36:33.893 | `Auto-connect: begin (Native-AA driver: motorola edge 30 neo, mode=PILL_THEN_OVERLAY)` |
| 15:36:33.961 | `NativeAA: Attempting manual poke to motorola edge 30 neo...` |
| 15:36:33.984 | `NativeAA: Calling socket.connect() for motorola edge 30 neo via HFP-AG ...` |
| 15:37:03.909 | `Auto-connect overlay: watchdog timeout, hiding` |

- **PROMPT_SHOWN → pick: 9.999 s** (round 5 read 9.968 s).
- `btConnected=false`, `mode=PILL_THEN_OVERLAY`.
- Dump at pick +13 s (`evidence/.../r10_dump.{png,xml}`): `auto_connect_pill` + `auto_connect_pill_text`
  present, `auto_connect_loading_overlay` **absent**. Pill text is
  `motorola edge 30 neo is disconnected, waking it...` — the intended change from
  `Android Auto is starting`.
- Poke **fires**: 2 `Attempting manual poke to motorola edge 30 neo` attempts, both with
  `Calling socket.connect()`.
- Pill's own watchdog hid it at pick +30.0 s; a dump at pick +45 s shows no `auto_connect*` node.
- `createGroup SUCCESS` = 1 at start, plus one `recoverNativeGroup` recreate at exactly +60 s
  (`no phone joined within 60s`) — expected with no phone, not a defect.
- **Override line count: 0.** (Watch item — correct, no hands-free link exists here.)

---

## R1 — headless bring-up

**PASS**

Both MAC strings empty, `native-poke-bt-macs` and `auto-start-bt-macs` empty in **both** files
(read back), both phones' radios off, no `am start`. `ACTION_LOG_MARKER` then
`ACTION_START_WIRELESS`, 120 s capture. Both broadcasts logged `AutomationReceiver:`.

| time | line |
|---|---|
| 15:38:12.275 | `WifiDirectManager: 5GHz createGroup SUCCESS!` |
| 15:38:14.495 | `NativeAA: Attempting active poke to device: motorola edge 30 neo (A0:46:5A:97:E4:95)...` |
| 15:38:45.389 | `NativeAA: Attempting active poke to device: POCO X3 NFC (DC:B7:2E:5E:4E:59)...` |
| 15:39:15.227 | `... motorola edge 30 neo ...` |
| 15:39:47.048 | `... POCO X3 NFC ...` |
| 15:40:15.535 | `... motorola edge 30 neo ...` |

- **5 pokes**, alternating MOTO / POCO / MOTO / POCO / MOTO, ~31 s apart.
- **First poke 2.220 s** after the first `createGroup SUCCESS` (round 5: 2.228 s, round 4: 2.225 s,
  round 3: 2.199 s).
- `paired device(s) are not phones` count: **0**.
- `Multi-driver selection is active` count: **0**.
- **Override line count: 0.** (Watch item — correct, radios off, no link to read.)
- `Not poking` count: 0.
- (Recovery `createGroup` at +60 s and a 2.4 GHz `Standard createGroup SUCCESS` at +120 s — the
  no-phone recovery ladder, expected.)

---

## Post-round change (not measured)

`8bed7833` on `fix/native-driver-selection-headless` renames the reachable-case overlay text
`connecting_driver` from `Native-AA driver: %s` to `Starting Android Auto with %s` — a user read
the old string on the black loading screen as a debug label. Resource string only; the
disconnected-phone pill text (`%s is disconnected, waking it...`, unchanged) and the `Auto-connect:
begin (Native-AA driver: ...)` log line are untouched, and R10/R12/R14/R15 judged the overlay/pill
by view id, not by this text — nothing in this round's verdicts changes. `testing/...` re-merged to
`1c87d607`.

## Anything the brief did not ask about

- **The `native-aa-complete-hfp-slc` poke behaviour.** On every poke the socket logs
  `Successfully poked ... Holding 20000ms...` but is closed early (`NativeAA: BT Handshake socket
  closed`) the moment the phone's TCP session lands at handoff — ~9 s in on R14b. This is why a
  persistent HFP link during a live session is not the rig's default state and R14's precondition
  had to be forced.
- **Interface index vs group count.** R14b's single group logged `p2p-wlan0-1` (round 5's captures
  showed `-0`/`-1`/`-2` for churned groups). One `createGroup SUCCESS`, one `P2P-GROUP-STARTED`.
- **RFCOMM reconnect flood scale.** 602 cycles in R14b vs round 5's 282 — the flood's rate looks
  tied to how long the outgoing phone stays powered and in range; both phones sat on the bench the
  whole round. Still bounded, still log-volume-only.

## Post-round: the branch was compacted to two commits

Not measured. Recorded here so the SHAs above stay navigable.

- The nine commits on `fix/native-driver-selection-headless` are now two, grouped by component:
  `b7425a4e` "Native AA: let the driver choose which phone gets the session" (22 files, the code)
  and `47a84b4b` "Translations: driver selection and the WiFi/Bluetooth strings in 20 locales"
  (40 files). Content-preserving: the new tip's tree hash equals `8bed7833`'s exactly and
  `git diff 8bed7833 <new tip>` was empty before anything else was added.
- Every SHA this file cites is held by a tag on the fork. `driver-selection-pre-compaction` is
  `8bed7833` and `driver-selection-testing-pre-compaction` is `1c87d607`, which keeps **`7520686c`**,
  the candidate this round measured, reachable along with its whole history.
- One behaviour-free change rides on the feature commit, aimed at the capture volume noted above.
  The `NativeAA: Connection accepted from ...` line is skipped when the address is one the accept
  gate is already turning away, and `refuseAtGate` now restates the refusal with its running count
  once a minute instead of falling silent after the first line. An R14-shaped run goes from 602
  lines to roughly five at every log level, and the `turned away N connection attempts` summary
  still reports the true total. Nothing about who is accepted, refused, poked or woken moves.
- New testing tip `f430ed05`, stamp `f430ed0563ef`, unit gate **1361 / 0**.

### Correction to the section above

The compaction first squashed the branch onto `main`, which swallowed the three commits of
`origin/driver-change-native-mode` (`d103ce7a`) that this work sits on top of. That was wrong and the
SHAs it produced (`b7425a4e`, `47a84b4b`, testing `f430ed05`) never left the bench. The branch is now
**two commits above `d103ce7a`**, split by component: `02b02a76` the link side (the accept gate, the
switch wake, the log cadence, the tests) and `3ed0bac2` the UI side (the selector labels and the
connect indicator). 13 files, +1137/-118. Content is unchanged from what is described above: the
tree at `3ed0bac2` is byte-identical to the squash it replaces, and the first commit compiles on its
own. New testing tip `cce12836`, stamp `cce12836ca6a`, unit gate **1361 / 0**. The tags still hold
everything the round cites.
