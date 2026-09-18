# native-aa-recovery-identity-and-speed — round 4 results

**Candidate:** `fix/native-aa-recovery-identity-and-speed` @ `289595df` (6 commits on `main` `12706e26`)
**Baseline:** round 3's candidate APK `545595d6` (git commit gone from the fork; identified by md5 + stamp)
**APK md5:** candidate `eeebeb94cafd521dfcc011b5134d5b5f` / baseline `bdf1cccd19453d8abb35fbad3fe8d5a8`
**Units:** D-MOTO = motorola edge 30 neo (Android 14, Gearhead 17.5) as head unit, driver = D-POCO;
D-POCO = POCO X3 NFC / M2007J20CG (Android 15, Gearhead 17.5) as head unit, driver = D-MOTO. Neither rooted.
**Date:** 2026-09-09

---

## Verdict summary

| Run | Verdict | One line |
|---|---|---|
| R0 | **PASS** | candidate 1489 / 0, `DriverCandidatePolicyTest` 19 / `NativeDriverSelectionPolicyTest` 71, stamp `289595df2f02` clean, `GATEWAY_BUT_CLASS_NOT_PHONE` in candidate DEX / absent from baseline |
| R1a (D-MOTO) | **PASS** (see caveat) | tie-break works: `FX Plus` and `KY Pro` now read `NOT_A_PHONE … class 0x0404 is not a phone` (were `PHONE` in round 3), roll-up `1 phone`. Only 9 of 10 bonds enumerated — `Navegadortz2` never in the moto's `adapter.bondedDevices` (round 3 quirk, now persistent), so counts are −1 vs the brief |
| R1b (D-POCO) | **PASS** | all 4 bonds enumerated; `Navegadortz2` → `NOT_A_PHONE, advertises the Audio Gateway record but device class 0x0408 is not a phone` (was `PHONE` in round 3); roll-up `1 phone, 0 unknown, 3 not a phone` exactly as briefed |
| R2a (D-MOTO) | **PASS** | no crash, PID stable, `Connecting to Native-AA device:` names POCO X3 NFC and nothing else, `Unambiguous driver (POCO X3 NFC)`, session in ~4 s |
| R2b (D-MOTO, A/B) | **PASS** | baseline crashes on demand (`FATAL EXCEPTION` … `not attached to a context` at `HomeFragment.kt:1020`, new PID); candidate survives the same relaunch (no FATAL, same PID, `finishDrawing of relaunch` present) |
| R3 (D-MOTO) | **PASS** (see caveat) | `sel1` = 1 phone row + toggle `Show all Bluetooth devices (8)`; after one tap, toggle `Show only phones` and 9 rows (every enumerated D-MOTO name). Brief's `(9)`/10 assume `Navegadortz2`, absent on this rig |
| R4a (D-MOTO) | **PASS** | `Poking every paired phone…`, `8 paired device(s) are not phones and are not poked.`, only POCO X3 NFC poked; round 3's ~86 s of failed non-phone pokes gone |
| R4b (D-POCO) | **PASS** | `3 paired device(s) are not phones and are not poked.`, only `motorola edge 30 neo` poked; round 3's 15 s hold on a `Navegadortz2` poke gone |
| R5a (D-MOTO) | **PASS** | one `createGroup SUCCESS`, `Incoming connection detected` → `SSL handshake complete`, no FATAL; `AapService creating` → `SSL` **11.4 s** (round 3 ~101 s, round 2 ~11.7 s) |
| R5b (D-POCO) | **PASS** (on retry) | first bring-up formed no session — the moto driver phone held stale WiFi-Direct group-owner state from the D-MOTO block; cleared with a WiFi cycle, retried clean: `AapService creating` → `SSL` **7.9 s** (round 3 ~35 s, round 2 ~11.7 s) |
| R6a (D-MOTO) | **PASS** | watch pinned as `native-preferred-device-mac` still reads `NOT_A_PHONE` with the R1a reason, no `vouched for by a stored MAC`; connects to POCO X3 NFC; roll-up `1 phone` |
| R6b (D-MOTO) | **PASS** (partial) | `Navegadortz2` pinned: no phantom phone (roll-up `1 phone`), connects to POCO X3 NFC only. `Navegadortz2` is not enumerated on D-MOTO so there is no verdict line to read — the full positive control lands on R1b/D-POCO, where it enumerates and reads `NOT_A_PHONE` despite the pin being irrelevant there |

**Both of round 3's findings are fixed.** The tie-break (`gateway record + non-phone class ⇒ not a
phone`) makes `FX Plus`, `KY Pro` (D-MOTO) and `Navegadortz2` (D-POCO) read `NOT_A_PHONE`, and every
roll-up on both head units reads `1 phone`. The `setOnShowListener` crash guard turns round 3's
every-launch crash into a survived relaunch — proven by an on-demand A/B (R2b): the baseline APK
still crashes with the exact `HomeFragment.kt:1020` stack, the candidate does not.

Connect times collapsed with the classification: **11.4 s** (D-MOTO) and **7.9 s** (D-POCO) launch
→ SSL, versus round 3's ~101 s and ~35 s spent poking misclassified non-phones first.

---

## Setup notes

- **Scripts used:** `build_hur.sh` + `run_unit_tests.sh` (R0), `set_hu_settings_runas.py` (every
  pref write, both phones — neither rooted). **New scripts added** and left in `hur-wifi-test-scripts/`:
  - `native_aa_recovery_r4.sh` — parametrised Native-AA bring-up / selector capture (HU + driver
    serials, pref block, optional `--ez show_driver_selector` cold/warm, optional uiautomator dump,
    PID at launch/end, verdict/poke/session landmark extraction).
  - `native_aa_recovery_r4_r3sel.sh` — R3 only: launch, let it auto-connect, `headunit://disconnect`
    back to `HomeFragment`, then warm selector deep link + two dumps around one toggle tap.
- **Baseline needed no build** — round 3's candidate APK `545595d6` (`bdf1cccd…`, stamp
  `545595d64e9f`) is the baseline arm as-is. Copied into `round-native-aa-recovery-r4/` as
  `baseline-545595d6.apk`; candidate as `candidate-289595df.apk`.
- **R2b A/B install:** candidate ↔ baseline are both versionCode 106, same signer — `adb install
  -r -d` swapped cleanly, `settings.xml` survived (`run-as cat` confirmed). Candidate reinstalled on
  D-MOTO after the baseline arm.

### Deviations from the brief

1. **`Navegadortz2` is not in D-MOTO's bond list, on any launch this round.** Round 3 saw the moto's
   `adapter.bondedDevices` return 10 then 9 (Navegadortz2 dropping). This round it was a stable **9
   across all 7 D-MOTO launches** (R1a, R1a2, R2a, R2b×2, R3, R6a, R6b — 0 with Navegadortz2). So on
   D-MOTO the brief's "ten in all", roll-up "9 not a phone", `N = 9` not-poked, toggle "(9)" and
   "sel2 has ten rows" are all **−1** on this rig: roll-up reads `1 phone, 0 unknown, 8 not a phone`,
   `8 paired device(s) are not phones`, toggle `(8)`, sel2 9 rows. Everything that *is* enumerated is
   correct, and `Navegadortz2`'s candidate verdict is confirmed on D-POCO (R1b), where all 4 bonds
   always enumerate. R1a and R1b were each re-launched once per the brief; the count did not change.
2. **R3 could not be reached "with the app already running from R2a" as written.** With one
   classified phone the startup path auto-connects and `AapProjectionActivity` is foreground within
   ~4 s; a warm `show_driver_selector` deep link fired after that is consumed by `MainActivity`
   (`EXTRA_SHOW_DRIVER_SELECTOR received` logs) but `HomeFragment.onResume` runs behind the
   projection window and `HomeFragment.onPause` dismisses the dialog before it can be dumped.
   Reached instead by: launch → let it connect → `am start -a … -d headunit://disconnect` (returns
   to `HomeFragment`; no orientation relaunch, and `hasCheckedNativeDriverSelection` is already true
   so `onResume` does not re-auto-connect) → warm `am start -n MAIN --ez show_driver_selector true`.
   `native-driver-selection-mode` stayed `1` throughout. `NativeDriverSelectionPolicy.shouldShowSelector`
   returns false for `pairedCount <= 1` in *every* mode, so the deep-link `requestDriverSelection`
   path (HomeFragment.kt:678, unconditional) is the only way the selector is reachable on this fleet.
3. **R3 sel2 enumeration used `input swipe` on the selector's own `ListView`** (`driverDeviceList`,
   ~2 rows visible at a time). This is the driver-selector dialog, not the settings list; minimum
   swipes to read all 9 `deviceName` rows.
4. **R5b: the first D-POCO bring-up formed no session** and it is a rig artifact, not a code
   finding. The moto phone (driver for the D-POCO block) had served as the head unit for the entire
   D-MOTO block and still held WiFi-Direct **group-owner** state (`dumpsys wifip2p`:
   `groupFormed: true isGroupOwner: true groupOwnerIpAddress: 192.168.49.1`), so it could not join
   D-POCO's group as a client. HUR did everything right — `5GHz createGroup SUCCESS!`, credentials
   delivered, `Successfully poked motorola edge 30 neo via HFP-AG` ×3 over ~2 min — but
   `WirelessServer: Incoming connection detected` never appeared. A `svc wifi disable/enable` on the
   moto (`groupFormed: false` confirmed after) fixed it; the retry (`R1b2`) connected in 7.9 s.
   **New rig rule: when the two phones swap head-unit / driver roles inside a round, cycle the
   incoming driver's WiFi before its first bring-up.**
5. **`set_hu_settings_runas.py`** was used for every pref write (both phones non-rooted). Deleted
   keys read back absent before every run.
6. **Brief §5 log-line text confirmed, with the class hex being the device's own:**
   `driver candidate <name> (<mac>) -> NOT_A_PHONE, advertises the Audio Gateway record but device
   class 0x0404 is not a phone` (D-MOTO `FX Plus` / `KY Pro`), `… device class 0x0408 is not a
   phone` (D-POCO `Navegadortz2`). Roll-up reason `gateway but class not phone` — verbatim.
7. **Both phones restored:** pre-round APK `959467e2989744c5db9356923949a433` reinstalled on both;
   `settings.xml` pushed back from pre-round host backups, `diff` byte-identical on both.

---

## Bonded inventory (this round)

### D-MOTO (motorola edge 30 neo) — 9 enumerated every launch (`Navegadortz2` never returned)

| Name | Address | App verdict (candidate) |
|---|---|---|
| POCO X3 NFC (driver phone) | DC:B7:2E:5E:4E:59 | PHONE — advertises the Audio Gateway record |
| FX Plus | D0:D9:4F:C2:C7:1E | **NOT_A_PHONE — advertises the Audio Gateway record but device class `0x0404` is not a phone** |
| KY Pro | D0:D9:4F:A0:88:AF | **NOT_A_PHONE — advertises the Audio Gateway record but device class `0x0404` is not a phone** |
| WH-1000XM4 | 80:99:E7:19:60:60 | NOT_A_PHONE — Hands-Free / A2DP-Sink, no AG |
| carplay_box_F96B | 84:1D:E8:E0:F9:6B | NOT_A_PHONE — Hands-Free / A2DP-Sink, no AG |
| Redmi Watch 5 Active 551A | 04:DA:28:6D:55:1A | NOT_A_PHONE — Hands-Free / A2DP-Sink, no AG |
| BC8-Android | 11:47:9D:51:0C:00 | NOT_A_PHONE — Hands-Free / A2DP-Sink, no AG |
| Mi True Wireless EBs Basic 2 | 20:1B:88:9B:D6:66 | NOT_A_PHONE — Hands-Free / A2DP-Sink, no AG |
| Daiku Speaker | 90:82:82:56:BF:9B | NOT_A_PHONE — Hands-Free / A2DP-Sink, no AG |
| _Navegadortz2_ | _11:46:03:10:33:59_ | _not enumerated on D-MOTO this round_ |

`FX Plus` and `KY Pro` both print device class **`0x0404`** (an audio device with a wearable-headset
subclass — one of the classes the tie-break rules out). First read of either on this rig.

Roll-up (R1a, all 9 enumerated), 13:10:29-ish, INFO:
```
BluetoothHelper: driver candidates: 1 phone, 0 unknown, 8 not a phone - hidden: WH-1000XM4 (hands free unit), carplay_box_F96B (hands free unit), Redmi Watch 5 Active 551A (hands free unit), FX Plus (gateway but class not phone), BC8-Android (hands free unit), KY Pro (gateway but class not phone), Mi True Wireless EBs Basic 2 (hands free unit), Daiku Speaker (hands free unit)
```

### D-POCO (POCO X3 NFC) — 4, all enumerated every launch

| Name | Address | `dumpsys` class | App verdict (candidate) |
|---|---|---|---|
| motorola edge 30 neo (driver phone) | A0:46:5A:97:E4:95 | `0x5A020C` (dev-class `0x020C` phone) | PHONE — advertises the Audio Gateway record |
| Navegadortz2 | 11:46:03:10:33:59 | `0x7E0408` (dev-class `0x0408` hands-free) | **NOT_A_PHONE — advertises the Audio Gateway record but device class `0x0408` is not a phone** |
| BC8-Android | 11:47:9D:51:0C:00 | `0x240420` (car audio) | NOT_A_PHONE — Hands-Free / A2DP-Sink, no AG |
| Magnetic Speaker | …D6:FB | `0x240404` | NOT_A_PHONE — Hands-Free / A2DP-Sink, no AG |

Roll-up (R1b):
```
BluetoothHelper: driver candidates: 1 phone, 0 unknown, 3 not a phone - hidden: Navegadortz2 (gateway but class not phone), BC8-Android (hands free unit), Magnetic Speaker (hands free unit)
```

---

## R0 — build gate

**PASS.** Candidate `testGithubDebugUnitTest` **1489 tests / 0 failed / 0 errors** (test-results XML
tally). `DriverCandidatePolicyTest` **19**, `NativeDriverSelectionPolicyTest` **71** — the two-more
count the brief predicted. Build log `Building from commit: 289595df2f02` (no `-dirty`).
`unzip -p <apk> classes*.dex | strings | grep -c GATEWAY_BUT_CLASS_NOT_PHONE` → **1** on the
candidate, **0** on the baseline. Candidate md5 `eeebeb94cafd521dfcc011b5134d5b5f`; baseline md5
`bdf1cccd19453d8abb35fbad3fe8d5a8` confirmed on disk, stamp `545595d64e9f`, symbol absent.

---

## R1a — every bonded device gets the right verdict (D-MOTO). **PASS** (9 of 10 bonds; see caveat).

- Settings written: `wifi-connection-mode=3`, `native-ap-transport=0`, `native-driver-selection-mode=1`,
  `native-driver-selection-timeout=10`, `log-level=1`, `wifi-5ghz-channel=0`; deleted
  `native-preferred-device-mac`, `last-connected-native-mac`, `native-poke-bt-macs`,
  `native-poke-all-paired` (read back absent).
- Radio state: driver phone (D-POCO) WiFi + Bluetooth on and settled before launch.
- Discard-rule check: clean — 1× `createGroup SUCCESS`, one session.
- Re-launched once (`R1a2`) per the brief — same 9 bonds, same verdicts, same roll-up.

**Verdict lines (decisive), 13:10:28-29, DEBUG:**
```
BluetoothHelper: driver candidate POCO X3 NFC (DC:B7:2E:5E:4E:59) -> PHONE, advertises the Audio Gateway record
BluetoothHelper: driver candidate FX Plus (D0:D9:4F:C2:C7:1E) -> NOT_A_PHONE, advertises the Audio Gateway record but device class 0x0404 is not a phone
BluetoothHelper: driver candidate KY Pro (D0:D9:4F:A0:88:AF) -> NOT_A_PHONE, advertises the Audio Gateway record but device class 0x0404 is not a phone
BluetoothHelper: driver candidate WH-1000XM4 / carplay_box_F96B / Redmi Watch 5 Active 551A / BC8-Android / Mi True Wireless EBs Basic 2 / Daiku Speaker -> NOT_A_PHONE, advertises the Hands-Free unit or A2DP Sink record and no Audio Gateway
```
Roll-up: `1 phone, 0 unknown, 8 not a phone` (would be `9 not a phone` with `Navegadortz2`).

**Caveat.** `Navegadortz2` is absent from the moto's bond enumeration on every launch this round, so
its expected line (`NOT_A_PHONE … class 0x0408 …`) and the 10-device / `9 not a phone` totals can't
be produced here. The point of the round — `FX Plus` and `KY Pro` (round 3's regressions) now read
`NOT_A_PHONE` and the roll-up reads `1 phone` — is fully demonstrated. `Navegadortz2`'s verdict is
confirmed on D-POCO (R1b).

---

## R1b — every bonded device gets the right verdict (D-POCO). **PASS.**

- Settings as R1a, written on D-POCO. Radio: driver phone (D-MOTO) WiFi + Bluetooth on and settled.
- Discard-rule check: the first bring-up formed no session (rig — see R5b); the classification and
  roll-up are read from that capture and re-confirmed on the retry `R1b2`, both identical.

**Verdict lines (decisive), DEBUG:**
```
BluetoothHelper: driver candidate motorola edge 30 neo (A0:46:5A:97:E4:95) -> PHONE, advertises the Audio Gateway record
BluetoothHelper: driver candidate Navegadortz2 (11:46:03:10:33:59) -> NOT_A_PHONE, advertises the Audio Gateway record but device class 0x0408 is not a phone
BluetoothHelper: driver candidate BC8-Android (11:47:9D:51:0C:00) -> NOT_A_PHONE, advertises the Hands-Free unit or A2DP Sink record and no Audio Gateway
BluetoothHelper: driver candidate Magnetic Speaker (2E:F0:03:99:D6:FB) -> NOT_A_PHONE, advertises the Hands-Free unit or A2DP Sink record and no Audio Gateway
```
Roll-up: `1 phone, 0 unknown, 3 not a phone - hidden: Navegadortz2 (gateway but class not phone), BC8-Android (hands free unit), Magnetic Speaker (hands free unit)` — exactly the brief's §201 prediction.

`Navegadortz2` was `PHONE` on round 3's candidate; on `289595df` the class rule takes it back.

---

## R2a — startup connects to the phone, never to a non-phone (D-MOTO). **PASS.**

- Setup as R1a. Force-stop, `am start -n MAIN`, untouched, 120 s capture, uiautomator dump at ~5 s.
- Discard-rule check: clean — 1× `createGroup SUCCESS`.

1. no `FATAL EXCEPTION` in the capture; **PID 5566 at launch and at 120 s** (SAME). ✅
2. `HomeFragment: Connecting to Native-AA device: POCO X3 NFC (DC:B7:2E:5E:4E:59), btConnected=false`
   — the only name in the whole capture. ✅
3. `HomeFragment: Unambiguous driver (POCO X3 NFC) - auto-connecting directly without prompt`;
   no `Select Driver Phone` (dump at 5 s empty — app already in `AapProjectionActivity`). ✅
4. `NativeAA: Successfully poked POCO X3 NFC via HFP-AG. Holding 20000ms...` — the only poke line;
   **no `Attempting active poke to device:` line at all** (POCO's own HFP reconnect completed the
   SLC before the poke's attempt logged — brief-anticipated, criterion vacuous). ✅
5. `WirelessServer: Incoming connection detected from` 13:12:54.003 → `Handshake: SSL handshake
   complete` 13:12:54.202, one `createGroup SUCCESS`. ✅

`AapService creating` 13:12:50.266 → `SSL handshake complete` 13:12:54.202 = **3.9 s**.

---

## R2b — the selector survives the relaunch (D-MOTO, both arms). **PASS.**

Cold start with `--ez show_driver_selector true`, 30 s capture, PID at launch and at 30 s.

### Baseline (`545595d6`) — the crash, reproduced on demand

```
09-09 13:14:55.681 I/WindowManager: finishDrawing of relaunch: Window{… com.andrerinas.headunitrevived/…main.MainActivity} 1059ms
09-09 13:14:56.307 E/AndroidRuntime: FATAL EXCEPTION: main
09-09 13:14:56.307 E/AndroidRuntime: java.lang.IllegalStateException: Fragment HomeFragment{d93e3} (…) not attached to a context.
09-09 13:14:56.307 E/AndroidRuntime:   at androidx.fragment.app.Fragment.requireContext(Fragment.java:972)
09-09 13:14:56.307 E/AndroidRuntime:   at com.andrerinas.openheadunit.main.HomeFragment.showNativeAaDeviceSelector$lambda$45(HomeFragment.kt:1020)
09-09 13:14:56.307 E/AndroidRuntime:   at …HomeFragment$$ExternalSyntheticLambda6.onShow(D8$$SyntheticClass:0)
09-09 13:14:56.307 E/AndroidRuntime:   at android.app.Dialog$ListenersHandler.handleMessage(Dialog.java:1476)
```
`EXTRA_SHOW_DRIVER_SELECTOR received` ×1. **PID launch = 6712, PID at 30 s = 6801 (CHANGED)** — the
UI process died and restarted. This is round 3's crash on demand.

### Candidate (`289595df`) — survives

1. **no `FATAL EXCEPTION`, no `not attached to a context`; PID 6181 at launch and at 30 s (SAME).** ✅
2. `09-09 13:13:44.192 I/WindowManager: finishDrawing of relaunch: Window{… main.MainActivity} 973ms`
   — the orientation relaunch happened, so the guard (not luck) kept the process up. ✅
3. `EXTRA_SHOW_DRIVER_SELECTOR received` ×1. `HomeFragment: Connecting to Native-AA device: POCO X3
   NFC` — the only name. ✅

Dump at ~3 s: `HomeFragment` home screen — `POCO X3 NFC is disconnected, waking it...`, `WiFi`,
`USB`, `Self Mode`, `Settings`, `Exit`. No `Select Driver Phone`. The replacement fragment took the
startup path: `HomeFragment: Unambiguous driver (POCO X3 NFC) - auto-connecting directly without
prompt`, and the session formed (`SSL handshake complete` 13:14:01.186). Exactly the brief's
"deep-link request consumed by the first fragment, replacement fragment takes the startup path".

---

## R3 — the selector lists only phones, "Show all" reveals the rest (D-MOTO). **PASS** (9 rows; see caveat).

- Reached via the disconnect-then-warm-deep-link path (Setup notes §2). App PID **9002 before the
  deep link and after it (SAME)**; no `finishDrawing of relaunch` after the deep link; no
  `FATAL EXCEPTION`; no `Connecting to Native-AA device:` line between the two dumps.
- `MainActivity: EXTRA_SHOW_DRIVER_SELECTOR received` present.

**`sel1`** (`R3_sel1.xml` / `.png`): dialog title `Select Driver Phone`. `ListView` `driverDeviceList`
has **exactly one** device row:
```
deviceName   = "POCO X3 NFC"
deviceStatus = "Disconnected, will be woken"
badgeText    = "Last Connected"
```
Toggle `btnToggleFilter` = **`Show all Bluetooth devices (8)`**  *(brief predicted `(9)` = the R1a
hidden count; on this rig that count is 8 — self-consistent)*.

**`sel2`** (after one tap on the toggle centre, `R3_sel2.xml` / `.png` + scrolled enumeration):
toggle now reads **`Show only phones`**; the list holds **9 rows** — `POCO X3 NFC`, `FX Plus`,
`KY Pro`, `BC8-Android`, `carplay_box_F96B`, `Daiku Speaker`, `Mi True Wireless EBs Basic 2`,
`Redmi Watch 5 Active 551A`, `WH-1000XM4` — every name from this round's D-MOTO enumeration.
*(Brief predicted 10, incl. `Navegadortz2`.)*

Dismissed with `KEYCODE_BACK`.

---

## R4a — the all-paired wake poke dials the phone first and only (D-MOTO). **PASS.**

Read from the R1a capture. `native-poke-bt-macs` empty at launch.

1. `NativeAA: No wake poke device selected, and poking all paired devices is on. Poking every paired phone...` ✅
2. `NativeAA: 8 paired device(s) are not phones and are not poked.` — **N = 8** (brief `9`; the 9th
   is the unenumerated `Navegadortz2`). ✅ against the enumerated set.
3. No `Attempting active poke to device:` line names anything; `Navegadortz2` / `FX Plus` / `KY Pro`
   never appear in a poke line. ✅
4. `NativeAA: Successfully poked POCO X3 NFC via HFP-AG` — the only `Successfully poked` line. ✅

Round 3 read ~86 s of failed pokes to `Navegadortz2` + `FX Plus` + `KY Pro` before POCO. **Gone —
0 s.**

---

## R4b — the all-paired wake poke dials the phone first and only (D-POCO). **PASS.**

Read from the R1b captures.

1. `Poking every paired phone...` ✅
2. `NativeAA: 3 paired device(s) are not phones and are not poked.` — **N = 3** = R1b hidden count. ✅
3. `NativeAA: Attempting active poke to device: motorola edge 30 neo (A0:46:5A:97:E4:95)...` — the
   only `Attempting active poke` line; `Navegadortz2` / `BC8-Android` / `Magnetic Speaker` never
   appear. ✅
4. `NativeAA: Successfully poked motorola edge 30 neo via HFP-AG` — the only one. ✅

Round 3's 15 s hold on a successful `Navegadortz2` poke on D-POCO: **gone.**

---

## R5a — an ordinary session still forms, and the time comes back (D-MOTO). **PASS.**

Read from the R1a capture. `Incoming connection detected` 13:10:59.779 → `SSL handshake complete`
13:11:00.017; one `createGroup SUCCESS` (13:10:51.342), one create chain; no `FATAL EXCEPTION`.

`AapService creating` 13:10:48.584 → `SSL handshake complete` 13:11:00.017 = **11.4 s**.
(R1a2 re-launch: 13:11:54.212 → 13:12:05.057 = **10.8 s**.)

| | round 4 | round 3 | round 2 |
|---|---|---|---|
| D-MOTO `AapService creating` → `SSL` | **11.4 s** | ~101 s | ~11.7 s |

Round 2's clean figure, restored.

---

## R5b — an ordinary session still forms, and the time comes back (D-POCO). **PASS** (on retry).

**First bring-up: no session (rig, not code).** `WifiDirectManager: 5GHz createGroup SUCCESS!`,
`SUCCESS - Providing credentials` ×several, `NativeAA: Successfully poked motorola edge 30 neo via
HFP-AG` ×3 over ~2 min — but `WirelessServer: Incoming connection detected` **never appeared** in
130 s. The moto driver phone still held WiFi-Direct group-owner state from serving as the head unit
through the whole D-MOTO block (`dumpsys wifip2p`: `groupFormed: true isGroupOwner: true
groupOwnerIpAddress: 192.168.49.1`), so it could not join D-POCO's group. Not a code finding.

**After `svc wifi disable/enable` on the moto** (`groupFormed: false` verified), retry `R1b2`:
`Incoming connection detected` 13:29:07.086 → `SSL handshake complete` 13:29:07.333; one
`createGroup SUCCESS` (5 GHz, 5180 MHz); one create chain; no `FATAL EXCEPTION`.

`AapService creating` 13:28:59.400 → `SSL handshake complete` 13:29:07.333 = **7.9 s**.

| | round 4 | round 3 | round 2 |
|---|---|---|---|
| D-POCO `AapService creating` → `SSL` | **7.9 s** | ~35 s | ~11.7 s |

---

## R6a — a preferred pick cannot lift the watch (D-MOTO). **PASS.**

Setup as R2a + `native-preferred-device-mac = 04:DA:28:6D:55:1A` (written and read back). Force-stop,
launch, 60 s.

1. `BluetoothHelper: driver candidate Redmi Watch 5 Active 551A (04:DA:28:6D:55:1A) -> NOT_A_PHONE,
   advertises the Hands-Free unit or A2DP Sink record and no Audio Gateway` — the R1a reason, **no
   `vouched for by a stored MAC`.** ✅
2. `HomeFragment: Connecting to Native-AA device: POCO X3 NFC (DC:B7:2E:5E:4E:59)` — the only name;
   `Unambiguous driver (POCO X3 NFC)`. ✅
3. Roll-up `1 phone, 0 unknown, 8 not a phone`. ✅

Key deleted afterwards, read back absent. Session formed in ~17 s (PID 10533 stable).

---

## R6b — a preferred pick cannot lift `Navegadortz2` (D-MOTO). **PASS** (partial).

Setup as R6a but `native-preferred-device-mac = 11:46:03:10:33:59` (written and read back).

- `Navegadortz2` is **not enumerated** on D-MOTO (0 `driver candidate` lines, as in R1a), so there is
  no verdict line to read and criterion 1 cannot be scored on this head unit.
- No phantom phone: roll-up stays `1 phone, 0 unknown, 8 not a phone`; `Connecting to Native-AA
  device: POCO X3 NFC` only; `Unambiguous driver (POCO X3 NFC)`; no `vouched` / `preferred` line. ✅
- The full positive control (`Navegadortz2` enumerated and still `NOT_A_PHONE` regardless of a pin)
  is covered by R1b on D-POCO, where it enumerates.

Key deleted afterwards, read back absent. Session formed in ~22 s (PID 10757 stable).

---

## Numbers the brief asked for

1. **R1a / R1b verdict tables** — above (Bonded inventory + R1a/R1b). `FX Plus` and `KY Pro` class
   hex on D-MOTO: **`0x0404`** (both). Roll-ups verbatim:
   - D-MOTO: `1 phone, 0 unknown, 8 not a phone - hidden: WH-1000XM4 (hands free unit), carplay_box_F96B (hands free unit), Redmi Watch 5 Active 551A (hands free unit), FX Plus (gateway but class not phone), BC8-Android (hands free unit), KY Pro (gateway but class not phone), Mi True Wireless EBs Basic 2 (hands free unit), Daiku Speaker (hands free unit)`
   - D-POCO: `1 phone, 0 unknown, 3 not a phone - hidden: Navegadortz2 (gateway but class not phone), BC8-Android (hands free unit), Magnetic Speaker (hands free unit)`
2. **R2a** — `Connecting to Native-AA device:` carried **POCO X3 NFC** and no other name. PID **5566
   at launch and at 120 s**.
3. **R2b** — baseline: `finishDrawing of relaunch` **yes**, `FATAL EXCEPTION` **yes**, PID **6712 →
   6801 (changed)**, dump empty (crashed at ~3 s). candidate: `finishDrawing of relaunch` **yes**,
   `FATAL EXCEPTION` **no**, PID **6181 → 6181 (same)**, dump = `HomeFragment` home screen ("… is
   disconnected, waking it…"), replacement fragment logged `Unambiguous driver (POCO X3 NFC)` and
   connected.
4. **R3** — toggle label **`(8)`**; `sel1` **1 row**, `sel2` **9 rows**.
5. **R4** — not-poked N: **8** (D-MOTO), **3** (D-POCO). `Attempting active poke` ordered list:
   D-MOTO none (POCO reached on the first poke, only `Successfully poked POCO X3 NFC`); D-POCO
   `[motorola edge 30 neo]` only.
6. **R5** — D-MOTO **11.4 s** (round 3 ~101 s, round 2 ~11.7 s); D-POCO **7.9 s** (round 3 ~35 s,
   round 2 ~11.7 s).
7. **R6a / R6b** — pinned device verdict: R6a `Redmi Watch 5 Active 551A … -> NOT_A_PHONE, advertises
   the Hands-Free unit or A2DP Sink record and no Audio Gateway`; R6b `Navegadortz2` not enumerated
   on D-MOTO, no phantom, roll-up `1 phone`.

**R0**: candidate 1489 / 0; `DriverCandidatePolicyTest` 19, `NativeDriverSelectionPolicyTest` 71;
stamp `289595df2f02` clean; `GATEWAY_BUT_CLASS_NOT_PHONE` present (candidate) / absent (baseline);
md5 candidate `eeebeb94…` / baseline `bdf1cccd…`.

---

## Anything the brief did not ask about

- **`Navegadortz2` is now persistently missing from the moto's `adapter.bondedDevices`.** Round 3
  saw 10 then 9; this round it was **9 on all 7 D-MOTO launches, 0 with `Navegadortz2`**. Whatever
  hides it (an LE-only re-bond, a stack quirk), it is no longer intermittent on this unit — any
  phone-count logic reading one enumeration on this phone will simply never see it.
- **The candidate's wake-poke "Holding" is 20000 ms; the baseline's is 15000 ms** (both visible in
  the R2b A/B). Not flagged as wrong — it is the `CHOSEN_WAKE` hold, and 20 s is what
  `NativeDriverSelectionPolicy` documents (`b5617bd5`). Noting the A/B delta only.
- **On D-MOTO the candidate's first poke logs only `Successfully poked`, never `Attempting active
  poke to device:`** — that line appears only on the *retry* rounds (seen on D-POCO R1b). The
  phone's own HFP auto-reconnect completes the RFCOMM SLC before the poke's attempt is logged, so a
  fast connect leaves no "Attempting" line. Anything counting `Attempting active poke` lines will
  under-count real pokes.
- **`SUCCESS - Providing credentials to listener.`** fired **4-8×** per group across the round (the
  known multi-delivery). No behavioural issue observed downstream.
- **D-POCO's Native-AA session ran on 5 GHz (5180 MHz) WiFi-Direct**, SSID `DIRECT-MW-Navegadortz`.
  The P2P device name base is literally `Navegadortz` — a coincidental string collision with the
  bonded nav-box of the same name, unrelated (P2P device name vs a bonded BR/EDR device).
- **When the two phones swap head-unit / driver roles inside one round, the incoming driver keeps
  WiFi-Direct group-owner state from its stint as head unit** and cannot join the new head unit's
  group until its WiFi is cycled. Cost R5b one capture. Folded into Setup notes §4 as a rig rule.

---

## Addendum — real two-phone bond on D-POCO (post-round, operator-added)

After the round, a **Galaxy S24+** (`24:A4:52:CF:70:EF`, device class `0x5A020C` = phone) was paired
to D-POCO, giving it **two real phones bonded** (`motorola edge 30 neo` + `Galaxy S24+`). The app
"kept crashing." This is the same standing selector crash R2b targets, now reached by a genuine
second phone instead of a forced deep link — a stronger reproduction. Both phones' `settings.xml`
was at the pre-round restore.

### Pre-round build (`959467e2`, the restore) — crashes on every launch

```
09-09 13:45:24.132 E/AndroidRuntime: FATAL EXCEPTION: main
09-09 13:45:24.132 E/AndroidRuntime: java.lang.IllegalStateException: Fragment HomeFragment{692796f} (…) not attached to a context.
09-09 13:45:24.132 E/AndroidRuntime:   at androidx.fragment.app.Fragment.requireContext(Fragment.java:972)
09-09 13:45:24.132 E/AndroidRuntime:   at com.andrerinas.openheadunit.main.HomeFragment.showNativeAaDeviceSelector$lambda$54(HomeFragment.kt:1023)
09-09 13:45:24.132 E/AndroidRuntime:   at com.andrerinas.openheadunit.main.HomeFragment$$ExternalSyntheticLambda6.onShow(D8$$SyntheticClass:0)
09-09 13:45:24.132 E/AndroidRuntime:   at android.app.Dialog$ListenersHandler.handleMessage(Dialog.java:1502)
```

`showNativeAaDeviceSelector$lambda$54` / `HomeFragment.kt:1023` — the exact **baseline** stack from
round 3 and round 4's R2b baseline arm. `959467e2` (main `12706e26` + ultrawide commits) predates the
guard, so it hits this on every launch as soon as a second phone is bonded. FATAL ×1 per launch, PID
changes.

### Round-4 candidate (`289595df`) — no crash

Same phone, same bond, same `settings.xml`:

- **Plain launch:** FATAL **0**, PID stable across 28 s. Classifier correct —
  `driver candidates: 2 phone, 0 unknown, 3 not a phone - hidden: Navegadortz2 (gateway but class
  not phone), BC8-Android (hands free unit), Magnetic Speaker (hands free unit)` (`motorola edge 30
  neo -> PHONE, vouched for by a stored MAC`, `Galaxy S24+ -> PHONE, advertises the Audio Gateway
  record`). `motorola edge 30 neo` was BT-connected so `shouldShowSelector` returned false
  (`connectedCount == 1`, unambiguous) and the service auto-woke it; session formed headless,
  `SSL handshake complete`.
- **Forced selector + relaunch** (`--ez show_driver_selector true`, cold): `MainActivity:
  EXTRA_SHOW_DRIVER_SELECTOR received`, `finishDrawing of relaunch` for `MainActivity` present (the
  crash-triggering relaunch), FATAL **0**, PID 18567 stable, replacement fragment took the startup
  path (`motorola edge 30 neo is disconnected, waking it...`) and connected.

**The user's crash is fixed by this branch.** D-POCO left on the candidate `eeebeb94…`
(`289595df`) rather than the crashing pre-round build; D-MOTO left on `959467e2` (one phone bonded,
does not hit it). Captures: `dpoco_2phone_preround.txt`, `dpoco_2phone_candidate.txt`,
`dpoco_2phone_cand_selector.txt` in `round-native-aa-recovery-r4/`.
