# native-aa-recovery-identity-and-speed: round 4 brief

Round 3 stopped after R1a, R1b and R2 with two findings, and this round is their re-test. The
candidate changes exactly two things against the APK round 3 built: a device whose class rules it
out is not a phone even when it advertises the Audio Gateway record, and the driver selector no
longer kills the process when the activity is relaunched while it opens. Same rig, same roles as
round 3: **D-MOTO and D-POCO as the head units**, each with the other as the driver phone.

This file is append-only. Read `TESTING-TEMPLATE.md` first; this brief only says what is specific to
this round.

---

## 1. Build and baseline

**History was rewritten since round 3.** The tip commit was amended in place: round 3's candidate
`545595d6` no longer exists on the fork, and `289595df` stands where it stood, on the same five
commits below it.

```bash
git fetch fork
git checkout fix/native-aa-recovery-identity-and-speed
git reset --hard fork/fix/native-aa-recovery-identity-and-speed     # 289595df
```

| Input | SHA | Role |
|---|---|---|
| `fix/native-aa-recovery-identity-and-speed` | `289595df` | **the candidate.** 6 commits on `main` `12706e26` |
| round 3's candidate APK | `545595d6` | **the baseline.** md5 `bdf1cccd19453d8abb35fbad3fe8d5a8`, stamp `545595d64e9f` |

**The baseline needs no build.** Round 3's candidate APK is the baseline arm as it is: every line
this round reads exists on both arms, and the two differences are the ones under test. Its git
commit is gone from the fork, so identify it by md5 and stamp only.

| SHA | Subject | Scored by |
|---|---|---|
| `289595df` | Native AA: classify driver candidates by Bluetooth role, not device class | R1, R2, R3, R4, R6 |

**Unit gate: 1489 / 0** on the candidate, two more than round 3: `DriverCandidatePolicyTest` 19 (was
17) and `NativeDriverSelectionPolicyTest` 71, unchanged. Any other count means the wrong tree was
built; stop and say so.

Identity, in order of strength: the build stamp (`"commit":"289595df...."` from `ACTION_QUERY_STATE`,
`Building from commit: 289595df` in the build log, no `-dirty`), then the DEX symbol
`GATEWAY_BUT_CLASS_NOT_PHONE`, present on the candidate and absent from the baseline
(`unzip -p <apk> 'classes*.dex' | strings | grep -c GATEWAY_BUT_CLASS_NOT_PHONE`; the baseline
carries `DriverCandidatePolicy` too, so that symbol no longer separates the arms), then both md5s.

## 2. What changed since round 3, and why

**The tie-break.** Round 3's R1 found `Navegadortz2` (class `0x0408`, hands-free), `FX Plus` and
`KY Pro` reading `PHONE, advertises the Audio Gateway record`. They are Android head units or nav
boxes with their own dialer, so they serve both halves of the profile, and the record rule ran
before the class rule. Now a device whose class rules it out (wearable, peripheral, imaging, health,
toy, or an audio device with a hands-free, car-audio, headset, headphones, speaker, microphone,
portable or hi-fi subclass) is `NOT_A_PHONE` whatever it advertises, with a reason that names both
facts. The record still decides when the class is unreadable or says nothing: a gateway on a device
of computer or uncategorized class is a phone, as before. A preferred-phone pick still lifts only an
unknown device, so it cannot lift one of the three either. The three become `NOT_A_PHONE`, the D-MOTO
roll-up should read `1 phone` and the D-POCO one `1 phone`, and the all-paired poke should dial the
driver phone first and only.

**The crash guard.** Round 3's R2 found both arms dying on every launch that showed the selector:
`MainActivity.onCreate` applies the stored screen orientation, the unit rotates, and the relaunch that
follows about 1 s after the first resume lands between the selector's `show()` and its queued
`onShow`, which ran `requireContext()` on the destroyed fragment. `Dialog.dismiss()` removes no
pending show message. The show and dismiss listeners now return when the fragment has no context.
The relaunch itself still happens; what changes is that the process survives it. The fragment that
replaces the first one then takes the ordinary startup path.

Nothing else moved. The enumeration, the seam, the selector's list, the poke target list and every
log line round 3 verified are as they were.

## 3. What is different about this round

**The inventory is known now.** Round 3 recorded every bond on both phones, and the expected verdict
of every one of them is written into R1 below. Do not unbond anything; a bond that is missing from
an enumeration is a finding (round 3 saw D-MOTO return 10 bonds, then 9 two minutes later).

**Round 3's errata are folded in.** The watch is a Redmi Watch 5 Active, bonded DUAL, and reads
`NOT_A_PHONE` by the Hands-Free record, not by LE. `N paired device(s) are not phones and are not
poked.` prints on both arms. D-MOTO's `dumpsys bluetooth_manager` prints neither class nor UUIDs, so
on D-MOTO the app's own verdict line is the only source of a device's class, and the new reason
prints it.

**The startup selector is not reachable on the candidate.** With `1 phone` on each head unit the
startup path auto-connects and never shows the selector, which is the design. The crash guard is
therefore exercised through the deep link on a **cold** start (R2b): the extra is read in
`onCreate`, the first fragment opens the selector on its first resume, and the orientation relaunch
follows as it did in round 3. On the baseline that is the round 3 crash; on the candidate the process
lives. What the candidate shows afterwards is not a criterion, only a report: the deep-link request
is consumed by the first fragment, so the replacement fragment takes the startup path and is expected
to log `Unambiguous driver (POCO X3 NFC)` and connect.

**The baseline is round 3's candidate, and round 3 already measured it.** R1 and R4 are candidate-only
on both head units. R2b is the one A/B, on D-MOTO. Do not re-measure the baseline's R1 verdicts;
round 3's results file has them.

**Video is not a criterion** on a phone head unit; `SSL handshake complete` is the session.

**D-POCO is not rooted** and neither is D-MOTO; `set_hu_settings_runas.py` on both.

## 4. Settings keys this round needs

As round 3, written on whichever phone is the head unit for the run, app stopped, read back before
launch.

| Key | Element | Note |
|---|---|---|
| `wifi-connection-mode` | `<int name="wifi-connection-mode" value="3" />` | Native AA |
| `native-ap-transport` | `<int name="native-ap-transport" value="0" />` | WiFi Direct |
| `native-driver-selection-mode` | `<int name="native-driver-selection-mode" value="1" />` | AUTO |
| `native-driver-selection-timeout` | `<int name="native-driver-selection-timeout" value="10" />` | |
| `native-preferred-device-mac` | delete | R6 sets it on purpose, twice |
| `last-connected-native-mac` | delete | a stored phone MAC is a phone without its records being read |
| `native-poke-bt-macs` | delete | **re-seeds from every completed handshake**; delete and read back before **every** run |
| `native-poke-all-paired` | delete | absent reads as `true` |
| `log-level` | `<int name="log-level" value="1" />` | DEBUG |
| `wifi-5ghz-channel` | `<int name="wifi-5ghz-channel" value="0" />` | |

Restore both phones' `settings.xml` from their backups afterwards, and say what differed.

## 5. The lines that decide every run

All verified with `grep -F` against `289595df`. `<name>` is the bonded device's name, `<mac>` its
address.

**New on the candidate, absent from the baseline:**

```
BluetoothHelper: driver candidate <name> (<mac>) -> NOT_A_PHONE, advertises the Audio Gateway record but device class 0x0408 is not a phone
```

The class hex is the device's own (`0x0408` hands-free, `0x0420` car audio, `0x07..` wearable, and
so on). In the roll-up's `hidden:` list the same reason reads `gateway but class not phone`.

**Present on both arms, read as in round 3:**

```
BluetoothHelper: driver candidate <name> (<mac>) -> PHONE, advertises the Audio Gateway record
BluetoothHelper: driver candidate <name> (<mac>) -> NOT_A_PHONE, advertises the Hands-Free unit or A2DP Sink record and no Audio Gateway
BluetoothHelper: driver candidates: N phone, N unknown, N not a phone - hidden: <name> (<reason>), ...
NativeAA: No wake poke device selected, and poking all paired devices is on. Poking every paired phone...
NativeAA: N paired device(s) are not phones and are not poked.
NativeAA: Attempting active poke to device: <name> (<mac>)...
NativeAA: Successfully poked <name> via HFP-AG
HomeFragment: Unambiguous driver (<name>) - auto-connecting directly without prompt
HomeFragment: Connecting to Native-AA device: <name> (<mac>), btConnected=
MainActivity: EXTRA_SHOW_DRIVER_SELECTOR received
WirelessServer: Incoming connection detected from
Handshake: SSL handshake complete
createGroup SUCCESS
```

**System lines for R2b**, from the same `logcat` capture, not `-s OPENHU`:

```
finishDrawing of relaunch: Window{... com.andrerinas.headunitrevived/com.andrerinas.openheadunit.main.MainActivity}
FATAL EXCEPTION: main
java.lang.IllegalStateException: Fragment HomeFragment{...} not attached to a context.
```

**On screen**, from a `uiautomator dump`: `Select Driver Phone`, `Show all Bluetooth devices (N)`,
`Show only phones`.

## 6. The runs

Each head unit gets **one clean bring-up capture** that R1, R4 and R5 are read from. R2a, R2b, R3 and
R6 are on D-MOTO. Order: R0, then D-MOTO (R1a, R4a, R5a from one capture; R2a; R2b both arms; R3;
R6a; R6b), then D-POCO (R1b, R4b, R5b from one capture).

### R0: build gate

Candidate count **1489 / 0**, stamp `289595df` with no `-dirty`, `GATEWAY_BUT_CLASS_NOT_PHONE` in the
DEX, md5 recorded. Baseline: md5 `bdf1cccd19453d8abb35fbad3fe8d5a8` confirmed on disk, stamp
`545595d64e9f`, the symbol absent from its DEX. Stop the round if the candidate count is anything
else.

### R1: every bonded device gets the right verdict. **The point of the round, again.**

Setup as round 3's R1: app force-stopped, settings as §4, deleted keys read back absent, driver phone
with Bluetooth and WiFi on and settled, no other bond deliberately connected. Launch with `am start
-n $MAIN`, capture 120 s or until `SSL handshake complete`, whichever is later. Read the first
enumeration only.

**R1a, D-MOTO. PASS**, all of:

| Device | Expected verdict line |
|---|---|
| POCO X3 NFC | `PHONE, advertises the Audio Gateway record` |
| Navegadortz2 | `NOT_A_PHONE, advertises the Audio Gateway record but device class 0x0408 is not a phone` |
| FX Plus | `NOT_A_PHONE, advertises the Audio Gateway record but device class 0x.... is not a phone` |
| KY Pro | `NOT_A_PHONE, advertises the Audio Gateway record but device class 0x.... is not a phone` |
| Redmi Watch 5 Active 551A, carplay_box_F96B, BC8-Android, WH-1000XM4, Mi True Wireless EBs Basic 2, Daiku Speaker | `NOT_A_PHONE, advertises the Hands-Free unit or A2DP Sink record and no Audio Gateway` |

and the roll-up reads **`1 phone, 0 unknown, 9 not a phone`** with all nine named under `hidden:`.
Report the class hex the app prints for `FX Plus` and `KY Pro`; it is the first time either has been
read on this rig. One `driver candidate` line per address in round 3's inventory, ten in all; if the
enumeration returns fewer, say which is missing and re-launch once.

**R1b, D-POCO. PASS**, all of: `motorola edge 30 neo` reads `PHONE, advertises the Audio Gateway
record`; `Navegadortz2` reads `NOT_A_PHONE, advertises the Audio Gateway record but device class
0x0408 is not a phone`; `BC8-Android` and `Magnetic Speaker` read the Hands-Free reason; the roll-up
reads **`1 phone, 0 unknown, 3 not a phone`**.

A device reading `PHONE` that is not the driver phone is the finding round 3 already made, one level
deeper: quote its line and its class, and carry on.

### R2a: startup connects to the phone and never to a non-phone. **Candidate, D-MOTO.**

The sighting run, now reachable. Setup as R1a. Force-stop, launch with `am start -n $MAIN`, do not
touch anything, capture 120 s. Take a `uiautomator dump` about 3 s after launch and a screenshot.

**PASS:**

1. no `FATAL EXCEPTION` in the capture and the app's PID at launch is the PID at 120 s;
2. `Connecting to Native-AA device:` names **POCO X3 NFC** and no other name in the whole capture;
3. `Unambiguous driver (POCO X3 NFC)` present if D-POCO was Bluetooth-connected at launch; no
   `Select Driver Phone` in the dump either way;
4. `Attempting active poke to device:` never names anything but POCO X3 NFC;
5. a session forms: `Incoming connection detected` then `SSL handshake complete`, one
   `createGroup SUCCESS`.

The baseline's version of this run is round 3's R2 and is not repeated.

### R2b: the selector survives the relaunch. **Both arms, D-MOTO.**

The crash run. Setup as R1a. Force-stop, then open the selector on a **cold** start:

```bash
adb -s <hu> shell am start -n $MAIN --ez show_driver_selector true
sleep 3 && adb -s <hu> shell uiautomator dump /sdcard/r2b.xml && adb -s <hu> pull /sdcard/r2b.xml
adb -s <hu> shell screencap -p /sdcard/r2b.png && adb -s <hu> pull /sdcard/r2b.png
```

Capture 30 s with plain `logcat` (the relaunch and crash lines are system lines). Record the app's
PID right after launch and again at 30 s.

**Both arms** must show `EXTRA_SHOW_DRIVER_SELECTOR received` once and `finishDrawing of relaunch`
for `MainActivity` after it. If the relaunch line is absent the mechanism was not exercised, and the
run is INCONCLUSIVE, not PASS; say so and try once more.

**Baseline**: `FATAL EXCEPTION: main` with `not attached to a context` and a new PID, or no PID, at
30 s. That is round 3's crash reproduced on demand, and it is what the candidate is measured against.

**Candidate PASS:**

1. no `FATAL EXCEPTION` in the capture, the same PID at 30 s;
2. `finishDrawing of relaunch` present, so the guard and not luck is what kept the process up;
3. `Connecting to Native-AA device:` names nothing but POCO X3 NFC, if it appears at all.

Report what the dump shows 3 s after launch and what the log says the replacement fragment did
(`Unambiguous driver` is expected). If `Select Driver Phone` is on screen instead, that is fine too;
say which.

### R3: the selector lists only phones, and "Show all" reveals the rest. **Candidate, D-MOTO.**

Round 3's R3 as written, now reachable. With the app **already running** from R2a, so no relaunch
follows:

```bash
adb -s <hu> shell am start -n $MAIN --ez show_driver_selector true
sleep 2 && adb -s <hu> shell uiautomator dump /sdcard/sel1.xml && adb -s <hu> pull /sdcard/sel1.xml
adb -s <hu> shell screencap -p /sdcard/sel1.png && adb -s <hu> pull /sdcard/sel1.png
```

Find the toggle's centre in the dump, tap it once, dump and screenshot again as `sel2`.

**PASS:**

1. `EXTRA_SHOW_DRIVER_SELECTOR received` in the log, `Select Driver Phone` in `sel1`, no
   `FATAL EXCEPTION`;
2. `sel1` has exactly one device row, POCO X3 NFC, and a toggle reading `Show all Bluetooth devices
   (9)`, the R1a hidden count;
3. `sel2` has ten rows, every name from round 3's D-MOTO inventory present, and the toggle reads
   `Show only phones`;
4. no `Connecting to Native-AA device:` line between the two dumps.

Dismiss with `KEYCODE_BACK`.

### R4: the all-paired wake poke dials the phone first and only. **Candidate, both head units.**

Read from the R1 captures. `native-poke-bt-macs` was empty at launch.

**PASS:**

1. `Poking every paired phone...` present;
2. `N paired device(s) are not phones and are not poked.` with **N = 9** on D-MOTO and **N = 3** on
   D-POCO;
3. every `Attempting active poke to device:` line names the driver phone; none names `Navegadortz2`,
   `FX Plus` or `KY Pro`;
4. no `Successfully poked` line names anything but the driver phone.

Round 3 read ~86 s of failed pokes on D-MOTO and a 15 s hold on `Navegadortz2` on D-POCO before the
phone was reached; both should be gone. If no `Attempting active poke` line appears at all, that is
the phone's own reconnect beating the poke; criterion 3 is vacuous and 1 and 2 decide.

### R5: an ordinary session still forms, and the time comes back. **Regression guard.**

Read from the R1 captures. **PASS:** `Incoming connection detected` then `SSL handshake complete`,
one `createGroup SUCCESS`, one create chain, no `FATAL EXCEPTION`. Report `AapService creating` to
`SSL handshake complete` on each. Round 3 read ~101 s on D-MOTO and ~35 s on D-POCO with the
non-phones poked first; round 2 read 11.7 s on D-POCO with a clean list. Anything near round 2's
figure is the tie-break paying back; anything near round 3's is a finding, and the poke order in
the capture says why.

### R6: a preferred pick cannot lift a ruled-out device. **Positive control, candidate, D-MOTO.**

Two launches, setup as R2a plus `native-preferred-device-mac` set to:

- **R6a** the watch, `04:DA:28:6D:55:1A`;
- **R6b** `Navegadortz2`, `11:46:03:10:33:59`, the new case: a device the gateway record would have
  lifted.

Force-stop, launch, capture 60 s each. **PASS**, both:

1. the pinned device reads `NOT_A_PHONE` with the same reason as in R1a, never `vouched for by a
   stored MAC`;
2. `Connecting to Native-AA device:` names POCO X3 NFC only;
3. the roll-up still reads `1 phone`.

Delete the key afterwards and read it back.

## 7. Discard rules

Round 3's. A second `createGroup SUCCESS` in an R1 capture is a discard unless a crash explains it,
and on the candidate a crash is the finding. R2b's baseline arm is expected to crash and the crash
is its result, not a discard.

## 8. Do not re-run

Round 2's R1 to R4, R7, R10 and R11. Round 3's R1 and R2 on the baseline: they are this round's
baseline readings and are in the round 3 results file.

## 9. Numbers to report

1. **R1a and R1b**: the verdict table, one row per bonded device, with the class hex the app printed
   for `FX Plus` and `KY Pro`, and the two roll-up lines verbatim.
2. **R2a**: the names `Connecting to Native-AA device:` carried; PID at launch and at 120 s.
3. **R2b**: on each arm, whether `finishDrawing of relaunch` appeared, whether `FATAL EXCEPTION`
   did, the PID at launch and at 30 s, and what the dump showed.
4. **R3**: N in the toggle label and the row counts of `sel1` and `sel2`.
5. **R4**: the `not phones and are not poked` N on each head unit, and the ordered list of names
   `Attempting active poke` carried.
6. **R5**: the two timings, beside round 3's and round 2's.
7. **R6a and R6b**: the pinned device's verdict line.

One line for R0.

## 10. Anything the brief did not ask about

As always. In particular: the number of `driver candidate` lines per enumeration on D-MOTO across the
whole round, because round 3 saw the bonded list shrink by one between launches; any device whose
class the app prints that does not match what round 3's D-POCO `dumpsys` showed for the same device;
and anything the replacement fragment does after the R2b relaunch that the startup path would not.
