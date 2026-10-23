# wireless-bring-up-and-5ghz — round 3 results

**Brief:** `wireless-bring-up-and-5ghz-round3-brief.md`
**Candidate:** `fix/wireless-bring-up-and-5ghz-channel` @ `e6ed1ad2`, merged into
`testing/wireless-plus-automation` @ `1f4b85ce` (`1f4b85ce5a593e45e0e2a0ae5155347c9c727c93`) with
`feat/automation-command-surface` @ `41a3866b` (instrument) and `fix/usb-attach-clear-defaults` @
`b236103c` (third input, not a subject).
**Built tree:** `testing/wireless-plus-automation`, build stamp `1f4b85ce5a59` (not dirty)
**APK md5:** `b892fcf6cc3740bee42e254ba8277559` (MT50)
**Unit gate:** 1414 / 0 (`testGithubDebugUnitTest`)
**Unit:** MT50 (`MT50_YT610E4GFPSL_U`, UNISOC T610, Android 14, 1440x720), joined to `Pegue Cdesta`
at 5500 MHz. Poked / connecting phone = POCO X3 NFC (`DC:B7:2E:5E:4E:59`), Gearhead not involved
(no session in R6/R7/R8).
**Date:** 2026-09-05

## Verdicts

| Run | Verdict | One line |
|---|---|---|
| R0 build gate | **PASS** | stamp `1f4b85ce5a59`, gate 1414/0, `QUERY_STATE` `commit`=`1f4b85ce5a59`, `UsbSwitchClaim` present in DEX (round-2 APK reads 0), md5 `b892fcf6…` |
| R6 (re-run) manual poke no longer races the automatic one — **the point of the round** | **PASS, shape A (serialised), 3/3** | the waiting line fires exactly once per run; the manual `socket.connect()` opens 0.02–0.10 s **after** the automatic one's failure line, never during it. Round 2's ~11 s overlap is gone. |
| R7 (re-run) a poke failure says why at the default level | **PASS** | `NativeAA: Poke via HFP-AG to POCO X3 NFC (DC:B7:2E:5E:4E:59) failed: read failed, socket might closed or timeout, read ret: -1` at `I/`, `log-level=2`, every run |
| R8 (re-run) a cancelled poke stops after one record | **PASS, 3/3** | the automatic poke logs exactly one `Calling socket.connect()` (HFP-AG) and no HSP-AG after it |
| R1 (re-run) an ordinary Native AA session still forms — **regression guard** | **PASS** | `AapService creating` → `SSL handshake complete` **21.22 s** (round 2: 21.42 s); 1 group, 1 SSL, 1 `Incoming connection detected`, projection activity + GLES view up |
| R9 (new) the automatic poke loop waits for itself too — **watch item** | **0 sightings** (not a FAIL) | every `another poke is already connecting` line in the round is preceded by the manual `ACTION_NATIVE_AA_POKE` broadcast; the automatic-gate path was never entered |

---

## Setup notes

### Deviations / conditions

1. **R1 was run twice, and the canonical capture (`hu_r1.txt`) is the second.** The POCO X3 NFC
   started this round reachable **only over wireless adb** (`192.168.1.10:5555`), so its WiFi station
   radio could not be toggled without losing adb. The first R1 (`hu_r1_btonly.txt`, kept as
   supplementary evidence) gated on Bluetooth only, leaving the phone associated to `Pegue Cdesta`
   (5500 MHz) while it joined the group on 5785 MHz. The app's own
   `WifiDirectManager.logStationCoexistence` warned about exactly this
   ("One radio has to retune between the two"), and the BT-on→SSL leg took 12.2 s against a
   round-2-style ~3.7 s, pushing `onCreate`→SSL to 33.33 s. The operator then connected the POCO by
   USB cable (serial `4f4027e9`); R1 was re-run with the round-2 methodology (both phone radios off,
   launch, settle 18 s, both radios on) and is the number reported above (21.22 s). The BT-only run
   is not a FAIL — it formed a clean single session with no improper waiting from `e6ed1ad2` — it is
   just not comparable to round 2's clock.

2. **`native-driver-selection-mode` set to `0` for every run**, restored to the rig baseline `2`
   afterwards. `log-level=2` for R0/R6/R7/R8, `0` for R1 (both R1 runs). `wifi-connection-mode=3`,
   `wifi-5ghz-channel=0`, `wifi-direct-band=1`, `native-ap-transport=0` unchanged from the rig
   baseline throughout.

3. **`native-poke-bt-macs` already held only `DC:B7:2E:5E:4E:59`** and read back unchanged after
   every launch — no write needed, as round 2 anticipated. No `settings.xml.bak` was ever created
   beside the file.

4. **The `WifiDirectManager: … createGroup SUCCESS!` line carries a frozen wall-clock timestamp on
   this rig, intermittently.** In `hu_r6b/r6c/r6d` and `hu_r1_btonly` it is stamped `09-05 22:20:30`
   or `22:20:31` regardless of when the run actually launched (22:26–22:31), while every surrounding
   line is stamped correctly and the line itself appears in correct **write-order** in the file. The
   canonical R1 stamped it correctly (`22:37:38.600`). Group formation was judged from write-order
   plus the credential-update and poke lines that depend on it (a poke cannot start without
   credentials). This did not affect any verdict — createGroup timing is not an R6/R7/R8 criterion,
   and R1's `onCreate`→SSL span uses `AapService creating` and `SSL handshake complete`, both stamped
   correctly.

5. **`logcat -c` is unreliable on this rig** (known). Every R6 capture carried a stale ~22:20 buffer
   block ahead of the real run. Counts and timelines were taken from the real-run window
   (timestamp-filtered); the stale block contains no `Calling socket.connect()` or `another poke`
   lines, so the poke-line counts are clean as reported.

6. **R6 capture window.** Round 2 used a 45 s post-fire window. `r6b` used 78 s and ran past the
   app's designed 60 s "no phone joined — recreate the group" recovery (`recoverNativeGroup`), which
   produced a second `createGroup SUCCESS` and a second `Attempting manual poke` cycle at
   `22:28:02`, ~45 s after the decisive window closed. `r6c`/`r6d` used 48 s and end cleanly before
   that recovery. All three are valid for R6 (the decisive automatic+manual interaction completes
   within ~47 s of the automatic connect); `r6b`'s post-window recovery is by-design and is noted,
   not counted against it.

7. **Both poke records failed on every R6 run** (`read ret: -1`, ~15.4 s per record) with the
   POCO's Bluetooth genuinely OFF — the same as round 2, and the condition that makes the race
   reproducible. In the canonical R1, the automatic poke's HFP-AG record **succeeded**
   (`Successfully poked POCO X3 NFC via HFP-AG. Holding 15000ms…`) once the phone's radios came
   back, so poke connectivity on this rig still varies by session (TESTING-TEMPLATE §7a).

### Scripts

- Used unchanged: `build_hur.sh`, `run_unit_tests.sh`, `set_hu_prefs.sh`,
  `wireless_bringup_5ghz_r2_poke.sh` (R6/R7/R8 — worked as-is, file-poll watcher, no change),
  `wireless_bringup_5ghz_run.sh` (canonical R1, `POCO=4f4027e9`).
- **New: `hur-wifi-test-scripts/wireless_bringup_5ghz_r3_r1.sh`** — R1 with a Bluetooth-only
  clean-run gate for when the POCO is only on wireless adb (WiFi cannot be cut). Produced
  `hu_r1_btonly.txt`. Left in the folder; superseded for this round by the USB-cabled canonical run
  but useful next time the phone is wireless-only.

---

## R0 — build gate

**PASS**

- `./gradlew :app:assembleGithubDebug` → `Building from commit: 1f4b85ce5a59` (no `-dirty`).
- Unit gate **1414 / 0** (`testGithubDebugUnitTest`), matching the brief exactly (round 2 read
  1389; the USB branch's four test classes + two new `PokeOverlapPolicyTest` cases account for the
  delta).
- `unzip -p <apk> classes*.dex | strings | grep -c UsbSwitchClaim` → non-zero (round 2's APK reads
  0 on this — the USB branch joined the testing branch after round 2). `PokeOverlapPolicy` /
  `AutomationCommandPolicy` also present but no longer discriminating, as the brief states.
- `send ACTION_QUERY_STATE` →
  `{"action":"…ACTION_QUERY_STATE","versionName":"3.3.1","versionCode":105,"commit":"1f4b85ce5a59","flavor":"github","connected":false,"wireless":false,"wifiMode":"NATIVE","state":"Disconnected","ok":true}`
- APK md5 `b892fcf6cc3740bee42e254ba8277559`, installed `adb install -r`, on-device md5 matches.
- `AutomationReceiver: <action>` logged at `I/` for every broadcast; `-f 0x00000020` delivered to
  the force-stopped package.

---

## R6 — a manual poke no longer races an automatic one (the point of the round)

**PASS — shape A (serialised) — 3 valid runs out of 3.** Each landed inside the window on the
**first attempt** (the watcher fires on the automatic `socket.connect()`, and the window is now the
full ~15.4 s that connect blocks for).

### Timelines (all three identical in shape)

**`r6c`** (reference):

| Time | Thread | Line |
|---|---|---|
| 22:30:02.097 | [61] | `Calling socket.connect() for POCO X3 NFC via HFP-AG` — **automatic, 1st socket** |
| 22:30:02.298 | [2] | `AutomationReceiver: …ACTION_NATIVE_AA_POKE` (broadcast landed, +0.20 s) |
| 22:30:02.325 | [60] | `another poke is already connecting to POCO X3 NFC — waiting for it rather than opening a second socket.` — **once** |
| 22:30:17.551 | [61] | `Poke via HFP-AG … failed: … read ret: -1` — **1st socket closes** (15.45 s) |
| 22:30:17.599 | [61] | `Attempting manual poke to POCO X3 NFC…` — **after** the automatic outcome |
| 22:30:17.610 | [61] | `Calling socket.connect() for POCO X3 NFC via HFP-AG` — **manual, 2nd socket, 1st already closed** |
| 22:30:32.997 | [61] | manual HFP-AG fails |
| 22:30:33.004 | [61] | `Calling socket.connect() … via HSP-AG` — manual's 2nd record |
| 22:30:48.479 | [61] | manual HSP-AG fails |
| 22:30:48.486 | [61] | `Manual poke to POCO X3 NFC finished.` |

### Against the criteria (all four met, 3/3)

1. `another poke is already connecting …` appears **exactly once** — r6b/r6c/r6d each = 1.
2. **No second `Calling socket.connect()` for the same MAC between the automatic connect and its
   outcome.** The socket.connect / failure timestamps per run:
   - r6b: connect 22:27:01.389 → fail 22:27:16.789; next connect 22:27:16.892 (**+0.10 s after**)
   - r6c: connect 22:30:02.097 → fail 22:30:17.551; next connect 22:30:17.610 (**+0.06 s after**)
   - r6d: connect 22:31:15.055 → fail 22:31:30.455; next connect 22:31:30.478 (**+0.02 s after**)
   Every subsequent connect starts **after** the prior one's failure. **No pair overlaps in time,
   in any run.**
3. `Attempting manual poke` comes **after** the automatic attempt's outcome line — r6b/r6c/r6d.
4. Every failure line names a profile (HFP-AG / HSP-AG) and a reason (`read ret: -1`).

**FAIL condition (two concurrent `socket.connect()` for the same MAC): not observed in any run.**
That was round 2's result and it is gone.

### Delay from `ACTION_NATIVE_AA_POKE` to `Attempting manual poke` (the number that failed round 2)

| Run | Broadcast landed | Attempting manual poke | Delay |
|---|---|---|---|
| r6b | 22:27:01.678 | 22:27:16.878 | **15.20 s** |
| r6c | 22:30:02.298 | 22:30:17.599 | **15.30 s** |
| r6d | 22:31:15.171 | 22:31:30.472 | **15.30 s** |

Round 2 measured **4.11 s, 3/3** — the fixed 4000 ms `CONNECT_SETTLE_WAIT_MS` bound, which expired
~11 s before this rig's Bluetooth-off connect returns. The new wait tracks the automatic connect's
real duration (it releases the instant the slot frees at ~15.4 s, well short of the 20 s
`Step.ABANDON` bound), so the manual poke proceeds only once there is genuinely no socket open.
Shape B (the 20 s abandon) was not reached and should not have been — a single failing record here
is ~15.4 s, comfortably inside 20 s.

### Watch item — `a chosen driver's wake poke is running — not replacing it with the multi-device loop.`

Still appears: **3×** in `r6b` (fired by the 60 s group-recovery's fresh `triggerPoke` while the
manual poke held its slot), 0 in `r6c`/`r6d` (capture ended before the recovery). The multi-device
loop correctly stands aside while the manual/chosen-driver poke owns the slot — the other half of
the mutual exclusion is intact, and holding the slot ~4× longer did not strain it.

---

## R7 — a poke failure says why, at the default log level

**PASS** (read out of R6's captures). `log-level=2` (INFO). Verbatim from `r6c` (22:30:17.551,
`I/`):

```
NativeAA: Poke via HFP-AG to POCO X3 NFC (DC:B7:2E:5E:4E:59) failed: read failed, socket might closed or timeout, read ret: -1
```

Present at INFO for both HFP-AG and HSP-AG records in all three runs, each naming the profile and
carrying the reason. `Calling socket.connect()` never appears without a matching outcome line at
this level.

---

## R8 — a cancelled poke stops after one record

**PASS, 3/3** (read out of R6's captures).

| Run | Automatic thread | Its `Calling socket.connect()` lines | HSP-AG after its HFP-AG? |
|---|---|---|---|
| r6b | [62] | 1 (HFP-AG 22:27:01.389 only) | no |
| r6c | [61] | 1 (HFP-AG 22:30:02.097 only) | no |
| r6d | [61] | 1 (HFP-AG 22:31:15.055 only) | no |

Every HSP-AG connect in the captures belongs to the manual poke. The automatic poke does one record
and stops — the `ensureActive()` check still takes. Pairing the brief calls out (R8 passing is what
keeps R6 in shape A rather than shape B): confirmed — one failing record is ~15.4 s, two would be
~31 s and past the 20 s bound.

---

## R1 — an ordinary Native AA session still forms (regression guard)

**PASS** — `hu_r1.txt`, canonical (round-2 methodology, POCO on USB `4f4027e9`).

- Settings: `wifi-connection-mode=3`, `native-ap-transport=0`, `wifi-direct-band=1`,
  `wifi-5ghz-channel=0`, `native-driver-selection-mode=0`, `log-level=0`. Both POCO radios off →
  launch → settle 18 s → both on.
- MT50 station at launch: `Pegue Cdesta` 5500 MHz, COMPLETED, RSSI -22.
- Decisive lines (`hu_r1.txt`):
  - `22:37:36.222  AapService.onCreate | AapService creating...`
  - `22:37:38.038  StationStandDown: this unit has left its WiFi network.`
  - `22:37:38.600  WifiDirectManager: 5GHz createGroup SUCCESS!` (single, correctly stamped)
  - `22:37:40.806  NativeAA: Calling socket.connect() for POCO X3 NFC via HFP-AG` (automatic poke)
  - `22:37:55.016  NativeAA: Successfully poked POCO X3 NFC via HFP-AG. Holding 15000ms...` (poke succeeded once radios returned)
  - `22:37:56.926  NativeAA: Handoff still settling — not starting a poke that would compete with the phone's WiFi association.`
  - `22:37:57.214  WirelessServer: Incoming connection detected from /192.168.49.50`
  - `22:37:57.438  SSL handshake complete. Session id: gToElpCP7sA6T0EOJ/…`
  - `22:37:57.x  AapProjectionActivity.setupProjectionView | Using GlProjectionView` — projection came up
- **`AapService creating` → `SSL handshake complete`: 21.22 s** (22:37:36.222 → 22:37:57.438).
  Round 2: 21.42 s. **Faster by 0.20 s; the ~32 s bound is nowhere near.**
- **Pairing the verdict with the number:** the automatic poke loop cost this run nothing. There was
  one poke in flight, so `awaitPokeSlot` was never entered (`another poke is already connecting` =
  0), the policy answered PROCEED on the first poll, and the timing landed on round 2's number to
  within 0.2 s. No sign of the loop waiting on anything it should not.
- Discard-rule: 1 `createGroup SUCCESS`, 1 SSL session, 1 `AapService creating`, 1 `Incoming
  connection detected`, `p2p-wlan0-0` only (one interface). `MATCH! Starting AapService` = 1
  (22:37:54.879, the POCO's Bluetooth reconnect hitting `AutoStartReceiver` — benign, no second
  group). **Clean.**
- Group frequency (`dumpsys wifip2p`): **5180 MHz** (automatic pick; round 1 saw 5240 / 5805,
  round 2 saw 5240 — within band, as expected).
- Station rejoined `Pegue Cdesta` COMPLETED at teardown +5 s. `StationStandDown.restore()` logged
  its WARN branch (the standing rig quirk); the rejoin is the signal and it happened.

**Supplementary — `hu_r1_btonly.txt`** (Bluetooth-only gate, POCO on wireless adb): also formed a
clean single session (1 group, 1 SSL, 1 Incoming, projection up), but `onCreate`→SSL was **33.33 s**
because the phone held its `Pegue Cdesta` association through the join and did 5500/5785 MHz
station-coexistence — the app's `logStationCoexistence` warning fired for exactly this. The poke
loop showed no improper waiting there either; the one 15.5 s block was the automatic poke's HFP-AG
connect while the phone's BT was deliberately off, and it recovered to a successful HSP-AG poke 5 s
after BT returned. Reported for completeness; not the R1 verdict.

---

## R9 — the automatic poke loop waits for itself too (watch item, not a criterion)

**0 automatic-gate sightings.** Every `NativeAA: another poke is already connecting to …` line in
the round (one per R6 run, three total) is preceded in the same capture by the manual
`AutomationReceiver: …ACTION_NATIVE_AA_POKE` broadcast — they are all the manual poke waiting, i.e.
R6's, already counted there. `another poke has been connecting to … for 20000ms` (the abandon
shape) appears **0** times anywhere. R1 and R1-btonly have 0 of either line.

As the brief says, zero is the likely answer and not a FAIL — the automatic half of the fix fires
only when a credential *change* restarts the poke loop mid-connect, and unchanged redeliveries are
deduped before reaching it (`triggerPoke() called again with unchanged credentials … not restarting
it` — seen twice in R1). It remains unexercised on hardware.

---

## Answers to the brief's §9

1. **R6: PASS, shape A (serialised), 3/3.** Delay `ACTION_NATIVE_AA_POKE` → `Attempting manual
   poke`: **15.20 / 15.30 / 15.30 s** (r6b/r6c/r6d), against round 2's failing **4.11 s**. Each run
   landed inside the window on the **first attempt**.
2. **No `Calling socket.connect()` pair for the same MAC overlapped in time, in any run.** Every
   subsequent connect starts 0.02–0.10 s **after** the previous one's failure line (r6b 22:27:16.789
   → 22:27:16.892; r6c 22:30:17.551 → 22:30:17.610; r6d 22:31:30.455 → 22:31:30.478).
3. **R1 `AapService creating` → `SSL handshake complete`: 21.22 s**, against round 2's 21.42 s.
   (Bluetooth-only-gate supplementary run: 33.33 s, inflated by phone-side station coexistence, not
   by `e6ed1ad2`.)
4. **R9: 0** automatic-gate sightings, and 0 `for 20000ms` abandon lines. Expected; the path is
   still hardware-unmeasured.

**One line each:** R0 PASS (stamp `1f4b85ce5a59`, gate 1414/0). R7 PASS (`Poke via HFP-AG … failed:
… read ret: -1` at INFO). R8 PASS 3/3 (automatic poke = exactly 1 `socket.connect()`, no HSP-AG
after).

**The nobody's-criterion watch item:** `a chosen driver's wake poke is running — not replacing it
with the multi-device loop.` still appears after the manual poke wins (3× in r6b). Holding the slot
~4× longer did not strain that guard.

## Shipping question

`e6ed1ad2` removes round 2's overlap: the manual poke now waits out the automatic poke's whole
connect (measured 15.2–15.3 s here) instead of a fixed 4 s, and the two RFCOMM sockets are
serialised on every run. The ordinary session path is unaffected (R1 21.22 s vs 21.42 s). The 20 s
`Step.ABANDON` bound and the automatic-loop wait (`triggerPoke()`'s device loop) are both still
JVM-only — not reachable on this rig's session and poke cadence. Nothing blocks the branch.

## Evidence

`evidence/wireless-bring-up-and-5ghz-round3/`:

- `hu_r6b.txt.gz`, `hu_r6c.txt.gz`, `hu_r6d.txt.gz`, `hu_r1.txt.gz`, `phone_r1.txt.gz`,
  `hu_r1_btonly.txt.gz` — full logcat
- `r6b_console.txt`, `r6c_console.txt`, `r6d_console.txt`, `r1_console.txt` — decisive excerpts
- `wifip2p_r1.txt`, `hu_station_after_r1.txt`, `hu_station_teardown_r1.txt`
- `settings-backup-mt50.xml` — taken before anything was written
