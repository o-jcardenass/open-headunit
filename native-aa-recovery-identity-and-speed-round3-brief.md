# native-aa-recovery-identity-and-speed: round 3 brief

One commit on top of round 2's candidate, and it changes who the driver selector and the wake poke
consider a phone. Round 2 measured the bring-up on the MT50 and one phone-to-phone session; this round
measures nothing on the MT50 at all. It runs on **D-MOTO and D-POCO as the head units**, because those
two are bonded to devices that are not phones, and that is exactly the population the commit exists
for.

This file is append-only. Read `TESTING-TEMPLATE.md` first; this brief only says what is specific to
this round.

---

## 1. Build and baseline

**History was rewritten since round 2.** Round 2's three top commits (`74eb8414`, `583729a6`,
`7d1f93f5`) were squashed into one, `16530fc9`, whose tree is byte-identical to `7d1f93f5`'s. The new
commit sits on top of that.

```bash
git fetch fork
git checkout fix/native-aa-recovery-identity-and-speed
git reset --hard fork/fix/native-aa-recovery-identity-and-speed     # 545595d6
```

| Input | SHA | Role |
|---|---|---|
| `fix/native-aa-recovery-identity-and-speed` | `545595d6` | **the candidate.** 6 commits on `main` `12706e26` |
| the same branch at | `16530fc9` | **the baseline.** Tree-identical to round 2's candidate `7d1f93f5` |

**The baseline needs no build.** Round 2's candidate APK, `round-native-aa-recovery-r2/` md5
`96c326cc64be6c3fcf3b35d743694cb5`, stamp `7d1f93f544f0`, is the same tree as `16530fc9` and serves
as the baseline arm as it is. Only the stamp string differs, and the stamp is how the two arms are
told apart.

| SHA | Subject | Scored by |
|---|---|---|
| `545595d6` | Native AA: a driver candidate is a phone by its Bluetooth role | R1, R2, R3, R4, R6 |

**Unit gate: 1487 / 0** on the candidate. The 21 new cases: `DriverCandidatePolicyTest` 17 (new
class) and `NativeDriverSelectionPolicyTest` +4 (67 total). Any other count means the wrong tree was
built; stop and say so.

Identity, in order of strength: the build stamp (`"commit":"545595d6...."` from `ACTION_QUERY_STATE`,
`Building from commit: 545595d6` in the build log, no `-dirty`), then the DEX symbol
`DriverCandidatePolicy`, present on the candidate and absent from the baseline
(`unzip -p <apk> 'classes*.dex' | strings | grep -c DriverCandidatePolicy`), then both md5s.

## 2. What the commit does, and why

On D-MOTO the driver selector said **"Starting Android Auto with Redmi Watch 9"**, a smartwatch, and
the list also offered the Bluetooth side of a wireless Android Auto dongle and a car radio named
BC8-Android. All three were treated as candidate driver phones.

The only phone test in the app was a deny-list on the Bluetooth device class. It rejected wearables,
peripherals and five audio-output subclasses and called everything else a phone, including a device
whose class could not be read at all. A watch bonded over Bluetooth LE has no readable class, so it
passed. A dongle and a car radio sit in the audio class with a hands-free or car-audio subclass, which
the list did not name, so they passed too. A class list can never name every device that does calls.

The commit classifies by the bond's cached service records instead, and the class is only the
fallback. A phone advertises the **Audio Gateway** record, HFP AG `0000111f` or HSP AG `00001112`,
which is the very record the wake poke dials. A head unit, a dongle, a radio, a headset or a watch
with call relay advertises the **Hands-Free unit** record `0000111e` or A2DP Sink `0000110b` and no
gateway. A bond over LE only can never carry the RFCOMM link and is ruled out outright. What the
records and the class cannot decide is **unknown**, offered only when no phone is bonded at all, so
the old "if the filter empties the list, show everything" fallback is gone.

Every list goes through one seam now: the selector, the startup auto-connect, the WiFi button and
the all-paired wake poke. The connected count the selector reads is scoped the same way, so a
connected watch no longer hides the one phone that is unambiguously there. The selector's "Show all"
toggle no longer changes what the countdown connects to. A preferred phone the user picked can lift an
unknown device to phone but never one that was ruled out; the last-connected MAC, which only a
completed handshake writes, always counts as a phone.

Every bonded device now gets one log line saying its verdict and the reason, and one roll-up line
says how many phones were found and what was hidden. Those lines are the round.

## 3. What is different about this round

**The MT50 is not used.** Its bonded list is two phones and it cannot reproduce the sighting. Both
rig phones act as the head unit in turn, each with the other as the driver phone: D-MOTO with D-POCO
as the phone, then D-POCO with D-MOTO as the phone. Round 2's R11 did the second pairing on this branch;
D-MOTO as a Native AA head unit is new to this channel, so record in Setup notes whatever it needed.

**Which devices are bonded to each phone is not known to this brief.** The user reports a Redmi Watch
9, an Android Auto dongle's Bluetooth side and a BC8-Android radio, and the MT50 itself is bonded to
both. R1 reads the truth off `dumpsys` before anything else, and every later verdict is read against
that inventory. Do not unbond anything.

**A verdict the brief cannot predict is a finding, not a broken run.** If R1 shows a non-phone whose
records carry `0000111f`, the role rule calls it a phone and the design goes back for another pass.
Report its `dumpsys` block and carry on; the rest of the round still measures what it measures.

**The selector with a countdown cannot be reached here.** Every non-phone is hidden, so each head
unit has exactly one phone candidate and the startup path never shows the selector. The "Show all
does not move the countdown target" property is therefore JVM-covered only (`NativeDriverSelectionPolicyTest`);
R3 reaches the selector through its deep link, which opens it without a countdown, and checks the
list and the toggle.

**Video is not a criterion** on a phone head unit, as in round 2's R11: a phone in portrait cannot
hold the projection activity foregrounded and the phone side withholds video. `SSL handshake
complete` is the session.

**D-POCO is not rooted**; `set_hu_settings_runas.py` is the pref editor there. Use the same route on
D-MOTO unless it is rooted, and say which in Setup notes.

## 4. Settings keys this round needs

Written on whichever phone is the head unit for the run, app stopped, read back before launch.

| Key | Element | Note |
|---|---|---|
| `wifi-connection-mode` | `<int name="wifi-connection-mode" value="3" />` | Native AA |
| `native-ap-transport` | `<int name="native-ap-transport" value="0" />` | WiFi Direct, as round 2 |
| `native-driver-selection-mode` | `<int name="native-driver-selection-mode" value="1" />` | AUTO. `0` disables the selector and R2's path with it |
| `native-driver-selection-timeout` | `<int name="native-driver-selection-timeout" value="10" />` | |
| `native-preferred-device-mac` | delete | R6 sets it to the watch's MAC on purpose |
| `last-connected-native-mac` | delete | a stored phone MAC counts as a phone without reading its records; the round wants the records read |
| `native-poke-bt-macs` | delete | empty means the all-paired poke path, which is under test. **It re-seeds itself from every completed handshake**, so delete and read back before **every** run |
| `native-poke-all-paired` | delete | absent reads as `true`, which is what the all-paired path needs |
| `log-level` | `<int name="log-level" value="1" />` | DEBUG. The per-device lines are `AppLog.d` with no verbose guard; `0` also works |
| `wifi-5ghz-channel` | `<int name="wifi-5ghz-channel" value="0" />` | as round 2 |

Restore both phones' `settings.xml` from their backups afterwards, and say what differed.

## 5. The lines that decide every run

All verified with `grep -F` against `545595d6` before this brief was written. `<name>` is the bonded
device's Bluetooth name, `<mac>` its address.

**New on the candidate, absent from the baseline:**

```
BluetoothHelper: driver candidate <name> (<mac>) -> PHONE, advertises the Audio Gateway record
BluetoothHelper: driver candidate <name> (<mac>) -> NOT_A_PHONE, advertises the Hands-Free unit or A2DP Sink record and no Audio Gateway
BluetoothHelper: driver candidate <name> (<mac>) -> NOT_A_PHONE, bonded over Bluetooth LE only
BluetoothHelper: driver candidate <name> (<mac>) -> NOT_A_PHONE, device class 0x0704 is not a phone
BluetoothHelper: driver candidate <name> (<mac>) -> PHONE, device class is phone
BluetoothHelper: driver candidate <name> (<mac>) -> PHONE, vouched for by a stored MAC
BluetoothHelper: driver candidate <name> (<mac>) -> UNKNOWN, no service record or device class says either way
BluetoothHelper: driver candidates: N phone, N unknown, N not a phone - hidden: <name> (<reason>), ...
NativeAA: No wake poke device selected, and poking all paired devices is on. Poking every paired phone...
NativeAA: N paired device(s) are not phones and are not poked.
NativeAA: no paired device advertises the Audio Gateway record, so nothing is poked. Choose the phone in Auto Start settings if this is wrong.
NativeAA: chosen wake target <name> (<mac>) reads as PHONE
```

The `driver candidate` lines are DEBUG and print once per enumeration, which is every poke round and
every resume. The roll-up is INFO the first time and whenever its composition changes, DEBUG in
between, so a grep for it at INFO returns the number of *changes* and not the number of enumerations.
The hidden list's reason is the verdict's reason in words: `hands free unit`, `low energy only`,
`class not phone`, `no signal`.

**The baseline's own version of the poke line**, so the arms can be told apart in the capture:

```
NativeAA: No wake poke device selected, and poking all paired devices is on. Poking all of them...
```

**Present on both arms and used for counts:**

```
HomeFragment: Unambiguous driver (<name>) - auto-connecting directly without prompt
HomeFragment: Connecting to Native-AA device: <name> (<mac>), btConnected=
MainActivity: EXTRA_SHOW_DRIVER_SELECTOR received
NativeAA: Attempting active poke to device: <name> (<mac>)...
NativeAA: Saving <mac> (<name>) as the wake poke device.
WirelessServer: Incoming connection detected from
Handshake: SSL handshake complete
createGroup SUCCESS
```

**On screen, not in the log**, read from a `uiautomator dump`:

```
Select Driver Phone
Show all Bluetooth devices (N)
Show only phones
Starting Android Auto with <name>
<name> is disconnected, waking it...
```

## 6. The runs

Each head unit gets **one clean bring-up capture** that R1, R4 and R5 are all read from; R2 is the
A/B on D-MOTO; R3 and R6 are short candidate-only runs on D-MOTO. Order: R0, then D-MOTO (R1a, R4a,
R5a from one capture; R2; R3; R6), then D-POCO (R1b, R4b, R5b from one capture).

### R0: build gate

Candidate count **1487 / 0**, stamp `545595d6` with no `-dirty`, `DriverCandidatePolicy` in the DEX,
md5 recorded. Baseline: round 2's APK md5 `96c326cc64be6c3fcf3b35d743694cb5` confirmed on disk, stamp
`7d1f93f544f0`, `DriverCandidatePolicy` absent from its DEX. Stop the round if the candidate count is
anything else.

### R1: every bonded device gets the right verdict. **The point of the round.**

Two captures, R1a on D-MOTO and R1b on D-POCO, each the bring-up capture shared with R4 and R5.

1. App force-stopped. Settings as §4. Read the bonded inventory **before launch** and keep it:

   ```bash
   adb -s <hu> shell dumpsys bluetooth_manager | grep -iA 30 "Bonded devices"
   ```

   For every bonded device record its name, address, class (hex), type (`BR/EDR`, `LE`, `DUAL`) and
   the UUID list, as `dumpsys` prints them. On a ROM whose `dumpsys` does not print UUIDs, say so; the
   app's own line still carries the verdict's reason.
2. The driver phone (the other rig phone) with Bluetooth and WiFi on and settled, the other bonded
   devices in whatever state they are in; note which were connected at launch.
3. Launch with `am start -n $MAIN`. Capture for **120 s** or until `SSL handshake complete`,
   whichever is later.

**PASS**, all of:

1. one `driver candidate` line for **every** address in the `dumpsys` inventory, on the first
   enumeration;
2. the driver phone reads `PHONE, advertises the Audio Gateway record`;
3. the Redmi Watch 9 reads `NOT_A_PHONE` with either `bonded over Bluetooth LE only` or
   `device class 0x07.. is not a phone` as its reason;
4. the dongle's Bluetooth side and BC8-Android, where bonded, read `NOT_A_PHONE, advertises the
   Hands-Free unit or A2DP Sink record and no Audio Gateway` (or a class reason if their records were
   never fetched, in which case quote the class);
5. the MT50, where bonded, reads `NOT_A_PHONE` (it advertises the Hands-Free unit);
6. the roll-up reads `1 phone` and its `hidden:` list names every non-phone with a reason.

A non-phone reading `UNKNOWN` is not a FAIL as long as a phone was found, because unknowns are hidden
whenever a phone exists; report it with its `dumpsys` block. A non-phone reading `PHONE` is the
finding §3 describes: report its UUID list and carry on.

### R2: startup connects to the phone and never to the watch. **Both arms, D-MOTO.**

The sighting itself. Setup as R1a. D-POCO's Bluetooth on and bonded; the watch on the wrist and
connected to D-MOTO if it will connect (note whether it was). Force-stop, launch with `am start -n
$MAIN`, do not touch anything, capture 120 s. Take a `uiautomator dump` about 3 s after launch and a
screenshot with it.

**Candidate PASS:**

1. `Connecting to Native-AA device:` names **D-POCO** and no other name in the whole capture;
2. no dialog titled `Select Driver Phone` in the dump, and no `Starting Android Auto with` or
   `is disconnected, waking it...` naming anything but D-POCO;
3. if D-POCO was Bluetooth-connected at launch, `Unambiguous driver (POCO X3 NFC)` is present;
4. `Attempting active poke to device:` never names the watch, the dongle or the radio;
5. a session forms: `Incoming connection detected` then `SSL handshake complete`, one
   `createGroup SUCCESS`.

**Baseline** (round 2's APK), same setup: report what it did rather than judging it. Expected is one
of two shapes, and which one depends on what was Bluetooth-connected at launch: the selector opens
listing the watch beside D-POCO (the paired count is two or more), or `Unambiguous driver (Redmi
Watch 9)` followed by `Connecting to Native-AA device: Redmi Watch 9`. Either reproduces the
sighting. Quote the line or the dump rows. If the baseline happens to pick D-POCO, say so; that does
not weaken the candidate's PASS but it means the rig did not reproduce the report.

### R3: the selector lists only phones, and "Show all" reveals the rest. **Candidate, D-MOTO.**

With the app already running from R2 (or launched fresh), open the selector through its deep link,
which shows it without a countdown:

```bash
adb -s <hu> shell am start -n $MAIN --ez show_driver_selector true
sleep 2 && adb -s <hu> shell uiautomator dump /sdcard/sel1.xml && adb -s <hu> pull /sdcard/sel1.xml
adb -s <hu> shell screencap -p /sdcard/sel1.png && adb -s <hu> pull /sdcard/sel1.png
```

Then find the toggle's centre in the dump, tap it once, and dump and screenshot again as `sel2`.

**PASS:**

1. `MainActivity: EXTRA_SHOW_DRIVER_SELECTOR received` in the log, `Select Driver Phone` in `sel1`;
2. `sel1` has exactly one device row, D-POCO, and a toggle reading `Show all Bluetooth devices (N)`
   with **N equal to the number of `NOT_A_PHONE` lines in R1a**;
3. `sel2` has N + 1 rows, every bonded name present, and the toggle reads `Show only phones`;
4. no `Connecting to Native-AA device:` line between the two dumps; nothing was tapped but the toggle.

Dismiss with `KEYCODE_BACK`. That sends the prompt-dismissed action and is not a selection.

### R4: the all-paired wake poke skips the non-phones. **Candidate, both head units.**

Read from the R1 captures. `native-poke-bt-macs` was empty at launch (read back, §4), so the poke
loop takes the all-paired branch.

**PASS:**

1. `Poking every paired phone...` present (the baseline wording `Poking all of them...` absent);
2. `N paired device(s) are not phones and are not poked.` with **N equal to the R1 hidden count**;
3. every `Attempting active poke to device:` line names the driver phone; none names any other
   bonded device;
4. `no paired device advertises the Audio Gateway record` is **absent** (a phone is bonded).

If no `Attempting active poke` line appears at all, that is the phone's own reconnect beating the
poke (`TESTING-TEMPLATE.md` §7a); criterion 3 is then vacuous and criteria 1 and 2 still decide.
After the session forms, `Saving <mac> (<name>) as the wake poke device.` writes the driver phone's
MAC into the poke list; report it and delete the key again before the next run.

### R5: an ordinary session still forms on each phone head unit. **Regression guard.**

Read from the R1 captures. **PASS:** `Incoming connection detected` then `SSL handshake complete`,
one `createGroup SUCCESS`, one create chain. Report `AapService creating` to `SSL handshake complete`
on each; round 2 read 11.71 s for D-POCO as head unit and 9 to 11 s on the MT50. The commit touches
no bring-up code, so a change here is a finding.

### R6: a preferred pick cannot lift the watch. **Positive control, candidate, D-MOTO.**

The one settings change that used to make a non-phone a phone everywhere. Setup as R2's candidate
arm, plus `native-preferred-device-mac` set to the **watch's MAC** from the R1a inventory. Force-stop,
launch, capture 60 s.

**PASS:**

1. the watch still reads `NOT_A_PHONE` with the same reason as in R1a, never `vouched for by a
   stored MAC`;
2. `Connecting to Native-AA device:` names D-POCO only;
3. the roll-up still reads `1 phone`.

Delete the key afterwards and read it back.

## 7. Discard rules

Round 2's. `MATCH! Starting AapService` may fire once per run off the phone's own reconnect and is
benign with zero group churn attached; count it. R3 opens no session and forms no group; a second
`createGroup SUCCESS` in R3's window, if the R2 session is still up underneath, is a discard.

## 8. Do not re-run

Round 2's R1 to R4, R7, R10 and R11. The commit touches the candidate enumeration and the poke
target list only; the bring-up, the wake timing, the create wedge and the early-wake stop are
unchanged and stay settled on the MT50.

## 9. Numbers to report

1. **R1a and R1b**: a table per head unit, one row per bonded device: name, address, `dumpsys` class,
   type, whether `0000111f` / `0000111e` / `0000110b` appear in its UUIDs, and the verdict line the
   app printed. This table is what the round is for.
2. **R2**: on each arm, the names `Connecting to Native-AA device:` carried, whether the selector
   appeared, and the dump rows if it did.
3. **R3**: N in the toggle label against the R1a `NOT_A_PHONE` count, and the row counts of `sel1`
   and `sel2`.
4. **R4**: the `not phones and are not poked` N against the R1 hidden count, on each head unit.
5. **R5**: the two timings.
6. **R6**: the watch's verdict line with the preferred MAC set.

One line for R0.

## 10. Anything the brief did not ask about

As always. In particular: any bonded device whose `dumpsys` UUID list is empty (records never
fetched), because that is the population the class fallback serves and the rig may be the first to
show one; and anything D-MOTO's stack does as a Native AA head unit that D-POCO's did not in round 2.
