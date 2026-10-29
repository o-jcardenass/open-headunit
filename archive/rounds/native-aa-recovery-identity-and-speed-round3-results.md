# native-aa-recovery-identity-and-speed — round 3 results

**Candidate:** `fix/native-aa-recovery-identity-and-speed` @ `545595d6` (6 commits on `main` `12706e26`)
**Baseline:** same branch @ `16530fc9` tree, served by round 2's APK `7d1f93f5`
**APK md5:** candidate `bdf1cccd19453d8abb35fbad3fe8d5a8` / baseline `96c326cc64be6c3fcf3b35d743694cb5`
**Units:** D-MOTO = motorola edge 30 neo (Android 14, Gearhead 17.5.663234) as head unit, driver = D-POCO;
D-POCO = POCO X3 NFC / M2007J20CG (Android 15, Gearhead 17.5.663214) as head unit, driver = D-MOTO. Neither rooted.
**Date:** 2026-09-09

---

## Verdict summary

| Run | Verdict | One line |
|---|---|---|
| R0 | **PASS** | candidate 1487 / 0, stamp `545595d64e9f` clean, `DriverCandidatePolicy` in DEX |
| R1a (D-MOTO) | **FAIL** | candidate classifies **4 devices PHONE**, roll-up `4 phone` not `1 phone`; 3 of them (Navegadortz2, FX Plus, KY Pro) advertise HFP/HSP-AG but are not phones — the §3 "design goes back for another pass" finding, ×3 |
| R1b (D-POCO) | **FAIL** | roll-up `2 phone`; Navegadortz2 classified PHONE by the Audio Gateway record despite its own device class `0x0408` (hands-free) |
| R2 (D-MOTO, A/B) | **INCONCLUSIVE** | both arms **crash** on the ambiguous-driver selector before connecting to anything; the specific sighting ("Starting Android Auto with <watch>") cannot be reproduced on this rig because the selector path never completes |
| R3, R4, R5, R6 | **not run** | premise invalidated — see below |

**The round was stopped after R1a / R1b / R2.** R3 ("`sel1` has exactly one device row, D-POCO"),
R5 (clean single-group bring-up) and R6 (preferred pick) are all written against "exactly one phone
candidate, the startup path never shows the selector" (brief §3). On this rig the selector **is**
shown (2–4 phones) and **crashes** every launch, so none of those runs can produce the signal they
ask for. Escalating per `TESTING-TEMPLATE.md` §3a: *a run's result changes what the remaining runs
should be and the brief did not say which way to go.*

---

## Setup notes

- **Scripts used:** `build_hur.sh` (candidate build), `run_unit_tests.sh` (gate),
  `set_hu_settings_runas.py` (all pref writes, both phones — neither is rooted). No new script added.
- **Baseline install** used `adb install -r -d` (candidate `3.3.2` → baseline `3.3.0-beta4` is a
  versionCode downgrade); `run-as cat` confirmed `settings.xml` survived. Candidate reinstalled and
  both phones' `settings.xml` restored from host backups at the end; both phones left on the
  pre-round APK `959467e2989744c5db9356923949a433`.
- **Brief errata found:**
  1. §1 unit-gate detail: `NativeDriverSelectionPolicyTest` is **71** cases total on the candidate
     (67 + 4), not "67 total". The 1487 / 0 grand total is exact.
  2. §5 lists `NativeAA: N paired device(s) are not phones and are not poked.` under "New on the
     candidate, absent from the baseline." **It is present on the baseline too** (`7d1f93f5`):
     `R2_baseline.txt` 11:54:01.752 `NativeAA: 5 paired device(s) are not phones and are not poked.`
     The real candidate/baseline tell is the wording of the line above it —
     `Poking every paired phone...` (candidate) vs `Poking all of them...` (baseline) — and that
     part held.
  3. §5 / §9 assume `dumpsys bluetooth_manager` prints per-device class and UUIDs. On **D-MOTO**
     (`motorola edge 30 neo`) the "Bonded devices" block prints name + type only, no class, no UUID.
     D-POCO prints the class hex but still no UUIDs. Device classes below for D-POCO's four bonds
     are from its `dumpsys`; D-MOTO's are unavailable, so the app's own verdict line is the only
     source there.
- **Bonded inventory was nothing like the brief's guess.** No "Redmi Watch 9"; the watch is a
  **Redmi Watch 5 Active** and it is `DUAL`, not LE-only. D-MOTO carries **ten** bonds including
  three devices — `Navegadortz2`, `FX Plus`, `KY Pro` — that advertise an Audio Gateway record. The
  MT50 is not bonded to either phone.
- **`native-poke-bt-macs` re-seeds** from every completed handshake, as the brief warns. Deleted and
  read back before every launch.
- Radio lever: driver phone's `svc wifi` / `svc bluetooth` were already on and settled for every
  run (brief R1 wants the driver phone up before launch, not §4's airplane sequence).

---

## Bonded inventory

### D-MOTO (motorola edge 30 neo) — 10 bonds, `dumpsys` prints no class / no UUID

| Name | Address | Type | App verdict (candidate, R1a first enumeration) |
|---|---|---|---|
| WH-1000XM4 | 80:99:E7:19:60:60 | BR/EDR | NOT_A_PHONE — hands-free unit / A2DP-Sink, no AG |
| Navegadortz2 | 11:46:03:10:33:59 | DUAL | **PHONE — advertises the Audio Gateway record** |
| carplay_box_F96B | 84:1D:E8:E0:F9:6B | BR/EDR | NOT_A_PHONE — hands-free unit / A2DP-Sink, no AG |
| Redmi Watch 5 Active 551A | 04:DA:28:6D:55:1A | DUAL | NOT_A_PHONE — hands-free unit / A2DP-Sink, no AG |
| FX Plus | D0:D9:4F:C2:C7:1E | DUAL | **PHONE — advertises the Audio Gateway record** |
| BC8-Android | 11:47:9D:51:0C:00 | BR/EDR | NOT_A_PHONE — hands-free unit / A2DP-Sink, no AG |
| KY Pro | D0:D9:4F:A0:88:AF | BR/EDR | **PHONE — advertises the Audio Gateway record** |
| Mi True Wireless EBs Basic 2 | 20:1B:88:9B:D6:66 | DUAL | NOT_A_PHONE — hands-free unit / A2DP-Sink, no AG |
| Daiku Speaker | 90:82:82:56:BF:9B | BR/EDR | NOT_A_PHONE — hands-free unit / A2DP-Sink, no AG |
| POCO X3 NFC (the driver phone) | DC:B7:2E:5E:4E:59 | BR/EDR | PHONE — advertises the Audio Gateway record |

Roll-up (R1a, all 10 enumerated): `BluetoothHelper: driver candidates: 4 phone, 0 unknown, 6 not a phone -
hidden: WH-1000XM4 (hands free unit), carplay_box_F96B (hands free unit), Redmi Watch 5 Active 551A
(hands free unit), BC8-Android (hands free unit), Mi True Wireless EBs Basic 2 (hands free unit),
Daiku Speaker (hands free unit)` — 11:47:29.103, INFO.

> R2_candidate's enumeration returned **9** bonds (Navegadortz2 absent) and rolled up `3 phone, 0
> unknown, 6 not a phone`. `adapter.bondedDevices` is not always complete on this phone; R1a had all 10.

### D-POCO (POCO X3 NFC) — 4 bonds, class hex from `dumpsys`

| Name | Address | Type | `dumpsys` class | App verdict (candidate, R1b) |
|---|---|---|---|---|
| Magnetic Speaker | …D6:FB | BR/EDR | `0x240404` (dev-class `0x0404` = wearable headset) | NOT_A_PHONE — hands-free unit / A2DP-Sink |
| Navegadortz2 | 11:46:03:10:33:59 | DUAL | `0x7E0408` (dev-class `0x0408` = **hands-free**; service bits: networking, rendering, capturing, object-transfer, audio, telephony) | **PHONE — advertises the Audio Gateway record** |
| motorola edge 30 neo (driver phone) | A0:46:5A:97:E4:95 | DUAL | `0x5A020C` (dev-class `0x020C` = phone / smartphone) | PHONE — advertises the Audio Gateway record |
| BC8-Android | 11:47:9D:51:0C:00 | BR/EDR | `0x240420` (dev-class `0x0420` = car audio) | NOT_A_PHONE — hands-free unit / A2DP-Sink |

Roll-up (R1b): `driver candidates: 2 phone, 0 unknown, 2 not a phone - hidden: BC8-Android (hands
free unit), Magnetic Speaker (hands free unit)` — 11:56:xx, INFO.

---

## R0 — build gate

**PASS.** Candidate `testGithubDebugUnitTest` **1487 tests / 0 failed** (test-results XML tally).
Build log `Building from commit: 545595d64e9f` (no `-dirty`). `unzip -p … classes*.dex | strings |
grep -c DriverCandidatePolicy` → 24 on the candidate, **0** on the baseline APK.
`DriverCandidatePolicyTest` 17 `@Test`, `NativeDriverSelectionPolicyTest` 71 total.
Baseline APK md5 `96c326cc64be6c3fcf3b35d743694cb5` confirmed on disk, stamp `7d1f93f544f0`.

---

## R1a — every bonded device gets the right verdict (D-MOTO). **FAIL.**

- Settings written: `wifi-connection-mode=3`, `native-ap-transport=0`, `native-driver-selection-mode=1`,
  `native-driver-selection-timeout=10`, `wifi-5ghz-channel=0`, `log-level=1`;
  deleted `native-poke-bt-macs`, `native-preferred-device-mac`, `last-connected-native-mac`,
  `native-poke-all-paired` (all read back absent).
- Radio state: driver phone (D-POCO) WiFi + Bluetooth on and settled before launch; no bonded
  device confirmed connected to D-MOTO at launch (`HeadsetStateMachine` for the watch was
  `Disconnected` since 11:14).
- Discard-rule check: **contaminated** — 2× `createGroup SUCCESS` (11:47:32.692, 11:48:33.860),
  2× `AapService creating` (crash + service restart). Not re-run: the run already FAILs on its
  stated criteria and the second group is a consequence of the finding, not rig flakiness.

**PASS criteria (brief R1):**

1. one `driver candidate` line per bonded address on the first enumeration — **met**, all 10.
2. driver phone reads `PHONE, advertises the Audio Gateway record` — **met** (POCO X3 NFC).
3. the watch reads `NOT_A_PHONE` with `Bluetooth LE only` **or** a class reason — **partial**: verdict
   is `NOT_A_PHONE` (correct) but the reason is `advertises the Hands-Free unit or A2DP Sink record
   and no Audio Gateway`. The Redmi Watch 5 Active carries an HFP-HF / A2DP-Sink record (it relays
   call audio), so the record rule fires before the class rule. Verdict right, reason not the one
   the brief predicted.
4. the dongle (`carplay_box_F96B`) and `BC8-Android` read `NOT_A_PHONE, advertises the Hands-Free
   unit or A2DP Sink record and no Audio Gateway` — **met**.
5. MT50 — not bonded, N/A.
6. roll-up reads `1 phone` — **NOT met: `4 phone`.**

**The finding (brief §3).** `Navegadortz2`, `FX Plus`, `KY Pro` each read
`PHONE, advertises the Audio Gateway record`. Decisive lines:

```
11:47:28.952  BluetoothHelper: driver candidate Navegadortz2 (11:46:03:10:33:59) -> PHONE, advertises the Audio Gateway record
11:47:29.006  BluetoothHelper: driver candidate FX Plus (D0:D9:4F:C2:C7:1E) -> PHONE, advertises the Audio Gateway record
11:47:29.037  BluetoothHelper: driver candidate KY Pro (D0:D9:4F:A0:88:AF) -> PHONE, advertises the Audio Gateway record
```

`Navegadortz2`'s own device class (read on D-POCO, R1b) is `0x0408` — a **hands-free unit**, i.e. a
car head unit, not a phone. It advertises `0000111f` (HFP-AG) anyway (a full-Android head unit with
its own dialer serves both roles). `DriverCandidatePolicy.classify` puts the record ahead of the
class unconditionally ("role beats class"), so a hands-free-class device that also advertises AG is
called a phone. `FX Plus` and `KY Pro` are the same shape (class unreadable on D-MOTO, but the
baseline did **not** poke them — see R2 — so baseline classified them as non-phones by class).

**Wake-poke behaviour observed** (this is R4a data, read from the same capture):

- `NativeAA: No wake poke device selected, and poking all paired devices is on. Poking every paired
  phone...` — 11:47:30.286 (candidate wording ✓).
- `NativeAA: 6 paired device(s) are not phones and are not poked.` — 11:47:32.380 (N = 6 = R1a
  hidden count ✓).
- Then the loop poked, in order: `Navegadortz2` (11:47:33.398), `FX Plus` (11:48:38.372),
  `KY Pro` (11:48:48.721), `POCO X3 NFC` (11:48:59.040). The first three **failed**
  (`read failed, socket might closed or timeout, read ret: -1` on both HFP-AG and HSP-AG); only
  `POCO X3 NFC` connected (`Successfully poked POCO X3 NFC via HFP-AG` 11:48:59.984).
- `no paired device advertises the Audio Gateway record` — **absent** ✓ (criterion 4 of R4a).
- Net: the poke loop spent **~86 s** (11:47:33 → 11:48:59) dialing three non-phones before it
  reached the real phone. `WirelessServer: Incoming connection detected from /192.168.49.79`
  11:49:08.245, `SSL handshake complete` 11:49:08.424 — **~101 s after launch**, against round 2's
  ~10–12 s for a clean bring-up.

**R4a criteria:** 1 met (candidate wording), 2 met (N = 6 = hidden count), 4 met; **3 not met** —
`Attempting active poke` named `Navegadortz2`, `FX Plus`, `KY Pro` besides the driver phone. That
failure is entirely downstream of the R1 misclassification.

**R5a** (regression guard, same capture): **FAIL** — 2× `createGroup SUCCESS`, and the UI process
crashed. Not a usable clean-bring-up measurement on this run.

---

## R1b — every bonded device gets the right verdict (D-POCO). **FAIL.**

- Settings as R1a, written on D-POCO via `set_hu_settings_runas.py`; deleted keys read back absent;
  `native-driver-selection-mode` was `0` (DISABLED) pre-round and `last-connected-native-mac` held
  D-MOTO's MAC — both changed per brief §4.
- Radio state: driver phone (D-MOTO) WiFi + Bluetooth on and settled.
- Discard-rule check: clean — 1× `createGroup SUCCESS`, 1 real SSL handshake (the "2" in a naive
  grep is the paired `AapSslContext` + `AapTransport` log lines for one event).
- Transport: **WiFi Direct** (`createGroup SUCCESS`, `192.168.49.x`), not SoftAP — as round 2 R11.

**PASS criteria:** 1 met (all 4 enumerated), 2 met (driver phone `motorola edge 30 neo` reads PHONE
by AG record), 4 met (`BC8-Android`, `Magnetic Speaker` → NOT_A_PHONE), 5 N/A;
**6 NOT met — `2 phone`, not `1 phone`.** `Navegadortz2` reads `PHONE, advertises the Audio Gateway
record` again, and here its class is visible: `0x0408` hands-free.

**Poke behaviour (R4b data):** `Poking every paired phone...` ✓;
`NativeAA: 2 paired device(s) are not phones and are not poked.` (N = 2 = R1b hidden count ✓);
poked `Navegadortz2` first — and on D-POCO the poke **succeeded**
(`Successfully poked Navegadortz2 via HFP-AG. Holding 15000ms...` 11:56:30.786) — held 15 s, then
poked `motorola edge 30 neo` (`Successfully poked … via HFP-AG` 11:56:47.798).
`Incoming connection detected from /192.168.49.48` 11:56:57.885, `SSL handshake complete`
11:56:58.141 — **~35 s after launch** (the 15 s hold on Navegadortz2 is most of the overshoot vs a
clean bring-up). Criterion 3 not met (poked `Navegadortz2`); criteria 1, 2, 4 met.

**R5b** (regression guard): `Incoming connection detected` → `SSL handshake complete`, **one**
`createGroup SUCCESS`, one create chain — the bring-up itself is clean; only the poke-target list
and the ~15 s hold on a non-phone are wrong, both downstream of R1.

---

## R2 — startup connects to the phone, never to a non-phone (D-MOTO, both arms). **INCONCLUSIVE.**

The sighting run. On this rig **both arms crash on the driver selector before connecting to
anything**, so the "connects to X and not to Y" question cannot be answered here.

### Crash — present on candidate **and** baseline

```
FATAL EXCEPTION: main
java.lang.IllegalStateException: Fragment HomeFragment{…} not attached to a context.
    at androidx.fragment.app.Fragment.requireContext(Fragment.java:972)
    at com.andrerinas.openheadunit.main.HomeFragment.showNativeAaDeviceSelector$lambda$45(HomeFragment.kt:1020)   [candidate]
    at com.andrerinas.openheadunit.main.HomeFragment.showNativeAaDeviceSelector$lambda$54(HomeFragment.kt:1023)   [baseline]
    at com.andrerinas.openheadunit.main.HomeFragment$$ExternalSyntheticLambda6.onShow(D8$$SyntheticClass:0)
    at android.app.Dialog$ListenersHandler.handleMessage(Dialog.java:1476)
```

- Reproduced **candidate 3 / 3** launches (R1a 11:47:30.298, R2_candidate 11:52:29.475, R1b on
  D-POCO 11:56:24.786) and **baseline 1 / 1** (R2_baseline 11:54:01.663).
- Mechanism: `showNativeAaDeviceSelector()` builds the dialog and calls `dialog.show()`; the
  `dialog.setOnShowListener { … requireContext() … }` block (`HomeFragment.kt:1017-1026`, **not
  touched by this commit**) is posted to the main looper and runs after `MainActivity`'s
  splash→home relaunch (the window is letterboxed on both phones and `finishDrawing of relaunch`
  fires ~1.3 s in). By the time `onShow` runs the fragment is detached → `requireContext()` throws
  → uncaught on the main thread → the UI process dies.
- The **AapService** (separate start) survives the UI crash and goes on to form a session headless,
  which is why every run still reached `SSL handshake complete` eventually.
- This is **not a candidate regression** — same crash, same call site, both arms. But it means the
  driver-selector UI is unusable on any head unit with more than one "phone" bonded, which is the
  exact population `fix/native-aa-recovery-identity-and-speed` is written for. It should be fixed
  before the selector is relied on (guard the `setOnShowListener` body with `isAdded` / `context ?:
  return`, as the countdown `onTick`/`onFinish` in the same method already do).

### Candidate vs baseline — what each treats as a driver

Baseline logs no per-device line; its classification is read from which devices the all-paired poke
dialed.

| Device | Baseline (`isLikelyPhone`) | Candidate (`DriverCandidatePolicy`) |
|---|---|---|
| Redmi Watch 5 Active | **phone** (poked, `Attempting active poke to device: Redmi Watch 5 Active 551A` 11:54:15.373) | NOT_A_PHONE ✅ |
| carplay_box_F96B (AA/CarPlay dongle) | **phone** (poked 11:54:05.025) | NOT_A_PHONE ✅ |
| BC8-Android (car audio) | **phone** (poked) | NOT_A_PHONE ✅ |
| WH-1000XM4, Mi EBs, Daiku Speaker, Magnetic Speaker | not a phone | NOT_A_PHONE = |
| FX Plus | not a phone (not poked) | **PHONE** ❌ regression |
| KY Pro | not a phone (not poked) | **PHONE** ❌ regression |
| Navegadortz2 | not a phone (not poked)¹ | **PHONE** ❌ regression |
| POCO X3 NFC / motorola edge 30 neo (real phones) | phone | PHONE = |

¹ Navegadortz2 was absent from the baseline enumeration too, so "not poked" there is not conclusive,
but on D-POCO (R1b, full enumeration) its class `0x0408` would make it a non-phone by class and it is
only the AG record that lifts it.

Baseline roll-up equivalent: `NativeAA: 5 paired device(s) are not phones and are not poked.`
(11:54:01.752) with `Poking all of them...`.

**Reading:** the candidate is a real improvement for the watch, the dongle and the car-audio unit
(baseline drives all three, candidate hides them), and a real **regression** for `FX Plus`,
`KY Pro` and `Navegadortz2` — devices that advertise an HFP/HSP Audio Gateway SDP record but are
not phones. The "role beats class" rule needs a tie-break: a device that advertises **both** an
Audio Gateway record **and** a non-phone device class (`0x0408` hands-free, `0x0420` car audio,
wearable/peripheral major) is not a phone. That single guard would have made R1a `1 phone` and R1b
`1 phone`.

### Session latencies (all slower than round 2's ~10–12 s, all from poking non-phones first)

| Run | launch → `SSL handshake complete` |
|---|---|
| R1a (candidate, D-MOTO) | ~101 s (poked Navegadortz2 + FX Plus + KY Pro, all failed, before POCO) |
| R2_candidate (D-MOTO) | ~35 s |
| R2_baseline (D-MOTO) | ~45 s (poked carplay_box + watch, failed, before POCO) |
| R1b (candidate, D-POCO) | ~35 s (15 s hold on a successful Navegadortz2 poke) |

---

## R3 / R6 — not run

Both are candidate-only D-MOTO runs whose PASS criteria assume exactly one phone candidate:
R3 wants `sel1` to have "exactly one device row, D-POCO"; R6 wants the roll-up to stay `1 phone`
with the watch's MAC set as `native-preferred-device-mac`. With 4 phones classified and the
selector crashing on show, neither can be inspected or judged. They should be re-briefed after the
class tie-break lands.

---

## Anything the brief did not ask about

- **`adapter.bondedDevices` is not stable on the motorola edge 30 neo** — returned 10 devices in
  R1a and 9 (Navegadortz2 missing) in R2_candidate two minutes later, no bond change in between.
  Any logic that counts phones off one enumeration can see a different count launch to launch.
- **The `setOnShowListener` crash is a standing bug in `HomeFragment.showNativeAaDeviceSelector`**,
  independent of this branch. It is latent on the MT50 only because that rig has exactly two phones
  bonded and `NativeDriverSelectionPolicy.shouldShowSelector` returns false for the unambiguous
  case. Any user with two phones (or, on baseline, a phone + a dongle) hits it on every launch.
- **A successful poke to `Navegadortz2`** (R1b) means the all-paired poke will open an RFCOMM
  HFP-AG connection to the user's car head unit every wake cycle and hold it 15 s — harmless here
  but not intended, and it is 15 s of added latency on the reported "slow to connect".
- **`carplay_box_F96B`** is the Bluetooth side of the wireless Android Auto / CarPlay dongle the
  reporter mentioned; the candidate correctly hides it. `KY Pro` / `FX Plus` / `Navegadortz2` look
  like Android-based car head units or nav boxes (own dialer, own AG record); the candidate does
  not.
