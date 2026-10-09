# driver-selection-native: round 6 brief

Round 5 passed every one of its nine runs. R10 proved the starting screen no longer appears with no
phone connected, R11 proved the pill escalates when one arrives, R12 proved a reachable phone still
gets the full screen at once, R13 proved the wake picker offers only phones, and R5b finally ran and
showed Cancel drops the exclusive gate as well as the wake. Nothing in that list is re-opened here.

This round exists because of what R5 found on its way to passing, and because that finding was
attributed to the wrong cause.

## The round 5 R5 finding, corrected

Round 5's R5 needed three attempts. Attempts 1 and 2 failed identically: the switch to D-POCO never
reached D-POCO, and 60 s later D-MOTO reconnected to itself. The results file attributes this to
**Bluetooth radio contention** and recommends disconnecting the outgoing phone's audio profiles at
switch time. **Both the attribution and the recommendation are withdrawn.** The results file is
append-only and still says otherwise; this section supersedes it, and the recommendation must not be
carried into a future round as settled.

D-POCO's poke was never contended, because it was never sent. A poke that reaches the radio logs
`NativeAA: Calling socket.connect() for <phone> via HFP-AG`. In `r5_candidate.txt` and
`r5_retry_candidate.txt` there is **not one** such line for D-POCO. Every POCO poke ran from
`Attempting manual poke to POCO X3 NFC...` to `Manual poke to POCO X3 NFC finished.` in **8 to 12
ms**, which is a guard returning early, not an RFCOMM connect failing. Attempt 3's capture has the
line for both phones, which is why it passed.

The guard reads whether **this head unit** holds a hands-free link, and that read is adapter-wide: it
answers "is any hands-free link up", never "is this phone's link up". During a switch it therefore
reports the **outgoing** phone's link and stands down the wake aimed at the **incoming** one. The
guard's own reasoning, that a live hands-free link *is* the connection a poke exists to create, is
true only while the link belongs to the phone being poked.

Two things follow, and both were misread:

- **The control test proved the opposite of what it looked like.** `svc bluetooth disable` on D-MOTO
  then poking D-POCO worked, in 636 ms. Disabling D-MOTO's radio did not free the air; it cleared
  the adapter-wide state the guard reads.
- **The 60 s give-up is the same bug, not a second one.** A stood-down poke returns instantly, so the
  three wake rounds collapse to their two 15 s gaps and the wake is over in **30 s** instead of the
  **90 s** a real wake holds. The exclusive gate is tied to the wake being active, so it lapsed early
  and the switch-away refusal ran out at 60 s. The capture matches exactly: switch 13:52:42.252,
  driver chosen 13:52:54.082, three no-op pokes 15.0 s apart ending 13:53:24.117, `turned away 282
  connection attempts` at 13:53:42.292.

**What round 5 got right, and it is the useful part:** whether a switch works depends on whether the
outgoing phone still holds live Bluetooth profiles, and not on how long ago its session ended. That
is exactly the variable this round now sets on purpose instead of leaving to chance.

**Why the recommendation is not implemented.** An app cannot do it. The head unit plays the A2DP
**sink** and HFP **client** roles, and those classes have never been in the public SDK on any Android
release: `android.jar` carries only `BluetoothA2dp` and `BluetoothHeadset`, the source and gateway
roles a phone plays, and neither exposes `disconnect` at all. The hidden sink and client
`disconnect` methods are marked for apps targeting SDK 30 or lower; this app targets 36, so
reflection reaches them with a `NoSuchMethodException`. `BluetoothDevice.disconnect()` needs a
privileged permission a normal app cannot hold, and drops every profile rather than the audio ones.
It is also unwanted: this repo already records that dropping a link the phone has accepted makes the
phone read the head unit's Bluetooth as gone and stop retrying wireless setup.

Rig: **D-HU** (MT50) as the head unit, **D-POCO** and **D-MOTO** both bonded to it as driver phones.
Both stay bonded for every run. No third device is needed this round.

Read **§3 of `TESTING-TEMPLATE.md`** and then **§3 of this brief**. §3 below supersedes the template
wherever the two disagree about how to drive the app.

This file is append-only. Corrections arrive as new commits and a `git pull` fast-forwards, so no
brief you have already read changes under you.

---

## 1. Build and the testing branch

**One APK this round.** No baseline build. Every number a run is compared against was measured on
this rig with these phones in rounds 1 to 5 and is quoted inline where it is needed.

| | Branch | SHA |
|---|---|---|
| **Candidate** | `fork/testing/driver-selection-plus-automation` | `7520686c` |

Both inputs moved since round 5, so reset rather than merge.

```bash
git fetch fork
git checkout testing/driver-selection-plus-automation
git reset --hard fork/testing/driver-selection-plus-automation      # 7520686c

# how it was made, and how to remake it if an input moves:
#   git checkout -b testing/driver-selection-plus-automation fork/fix/native-driver-selection-headless
#   git merge --no-edit fork/pr/automation-command-surface
# Clean, no conflicts, no manual resolution.
```

Expected build stamp `7520686c2578`. Unit gate **1357 / 0**.

## 2. What changed, and what this round is for

Two commits on `fix/native-driver-selection-headless`, on top of everything round 5 measured.

**The wake now asks whose hands-free link it is.** Two signals, certain first: during a driver switch
the outgoing phone's address is already known, so a wake aimed at any other phone proceeds; otherwise
a target that is not connected at all cannot be the one holding the link. An unreadable answer is
**not** treated as "not connected", so a stack that cannot answer keeps the old, cautious behaviour.
When the guard is overridden it says so in one line.

**The skip line now names each phone.** It used to print at INFO once and at DEBUG forever after, so
one line about D-MOTO hid every later stand-down of D-POCO. That single detail is why this took two
rounds to find, and it is why round 5's INFO captures look silent where the decision was being made.

**The selector says which phones are actually there.** A row that is not connected read the neutral
`Paired`; it now reads `Disconnected, will be woken`. And the pill raised for such a phone used to
fall back to `Android Auto is starting`, which is precisely the claim round 5 removed from the
overlay; it now names the phone and says it is being woken. A head unit that cannot read link state
labels **nothing** rather than calling every phone absent, which is the correct answer below API 21
and on any adapter that refuses the read.

### What is still open, and is not this round

- The outgoing phone's RFCOMM reconnect flood is real and untouched: round 5 measured 282 accept and
  close cycles in under a minute. It is bounded and it costs log volume, not a session. Do not treat
  its presence as a failure.
- Nothing here changes the exclusive gate's constants.

## 3. Driving the app

Everything in round 5's §3 still applies. Three additions.

**The precondition is the whole round, and it is no longer left to chance.** R14 only tests anything
if D-MOTO is holding live Bluetooth profiles to D-HU at the moment of the switch. Round 5 passed on
its third attempt precisely because that had stopped being true. Confirm it, do not assume it:

```bash
adb -s D-HU shell dumpsys bluetooth_manager | grep -iE "HeadsetClientStateMachine|A2DPSinkStateMachine"
```

`HeadsetClientStateMachine` must read `Connected` (round 5 saw `BTA_HF_CLIENT_OPEN_ST` for the same
state). If it does not, toggle D-MOTO's Bluetooth off and on, let it settle back onto D-HU, and check
again before switching. Record what the check said in the results either way.

**Do not run `svc bluetooth disable` anywhere in R14.** That is the round 5 control test, it clears
the exact state under test, and using it would hide the fix rather than prove it.

**The pill and the overlay still render the same words**, so judge R10 and R15 on the view resource
id in a `uiautomator dump` (`auto_connect_pill` against `auto_connect_loading_overlay`) and on the
logged `mode=`, never on the text alone.

## 4. Settings keys this round needs

Unchanged from round 5. `native-driver-selection-mode`, `native-driver-selection-timeout`,
`native-preferred-device-mac`, `last-connected-native-mac`, `native-poke-bt-macs`,
`kill-on-disconnect`, `log-level`, `wifi-connection-mode`, `native-ap-transport`. Write them with
`set_hu_settings_host.py` as before, and remember `native-poke-bt-macs` re-seeds itself from the last
completed handshake, so clear and read it back before **every** attempt.

Log level **2** for every run.

## 5. The lines that decide every run

All verified with `grep -F` against this candidate's tree before this brief was written.

**New this round, and what proves the fix:**

```
NativeAA: Calling socket.connect() for
even though a
Not poking
```

The first is the line the whole round turns on: it is absent from both of round 5's failed attempts
and present in the one that passed. The second is the guard being overridden and saying why. The
third is the guard standing a wake down, and it now appears once per phone rather than once per run.

**Carried from round 5, still decisive:**

```
Auto-connect: begin (Native-AA driver:
btConnected=
Auto-connect: a phone is answering, taking the full screen.
NativeAA: a driver switch is starting, so
turned away
SSL handshake complete
WirelessServer: Incoming connection detected from
createGroup SUCCESS
```

**On screen, not in the log:**

```
Disconnected, will be woken
is disconnected, waking it...
```

---

## 6. Runs

Six runs: the build gate, two new, three guards.

### R0: build gate

Standard. Build printed `Building from commit: 7520686c2578` with no `-dirty`, `ACTION_QUERY_STATE`
replies with the same, unit gate reads **1357 / 0**, APK md5 recorded and confirmed installed.

---

### R14: the switch reaches the chosen phone while the outgoing phone still holds its link. **The point of the round.**

This is round 5's R5 run with its failing precondition created on purpose instead of avoided.

Setup exactly as round 5's R5: `native-preferred-device-mac` = D-MOTO, timeout `10`,
`kill-on-disconnect=false`, both radios on, `native-poke-bt-macs` cleared and read back.

1. Let the countdown resolve to D-MOTO and wait for the session to form.
2. **Confirm the precondition** per §3, and record what `dumpsys` said.
3. `KEYCODE_BACK`, `uiautomator dump` to locate **Switch Phone**, tap its centre.
4. When the selector appears, `ACTION_NATIVE_AA_POKE --es extra_mac DC:B7:2E:5E:4E:59`.

**PASS:**

1. `NativeAA: Calling socket.connect() for POCO X3 NFC` is present. **This one line is the fix.**
   Round 5's attempts 1 and 2 had zero.
2. **zero** `Not poking POCO X3 NFC` lines after the switch.
3. the override line naming the reason is present, and names D-POCO.
4. the second `SSL handshake complete` belongs to **POCO X3 NFC**, on a client IP distinct from
   D-MOTO's. Round 4 and round 5 both read `.50` against `.189`.
5. `a driver switch is starting, so <D-MOTO MAC> is not let straight back in.` present.
6. `createGroup SUCCESS` count is **1** for the whole run.

Report the delay from the pick to `Calling socket.connect()`, and the delay from there to POCO's
RFCOMM connection. Round 5's clear-radio control measured 636 ms for the connect and about 4.9 s to
the RFCOMM landing; a contended radio may well be slower, and **slower is not a failure here** as
long as the session forms.

If D-MOTO wins the session back anyway, that is a real result and not a botched run: report the
`turned away` count and the time from `beginDriverSwitch` to the second handshake, which is what
separates "the wake never fired" from "the wake fired and lost a race".

---

### R15: the selector names a phone that is not there. **New.**

Setup as R14, then turn **D-POCO's Bluetooth off** and leave D-MOTO's on. Open the selector and do
not touch it until the dump and screenshot are taken.

**PASS:**

1. the D-POCO row reads `Disconnected, will be woken`; the D-MOTO row reads the connected label. A
   screenshot and a `uiautomator dump` both, since one is the user's view and the other is
   greppable.
2. picking D-POCO logs `btConnected=false` and `mode=PILL_THEN_OVERLAY`, and the pill on screen reads
   `POCO X3 NFC is disconnected, waking it...` rather than `Android Auto is starting`. Dump inside
   the pre-handshake window; round 5 measured about 8 s as a comfortable margin.
3. `auto_connect_pill` present in that dump, `auto_connect_loading_overlay` absent.

Neither row may read `Disconnected` while that phone is in fact connected. If **both** rows read the
neutral `Paired`, that is the unreadable-stack path and is a legitimate outcome on some hardware, not
a failure: say so and quote the two rows.

---

### R12: a phone that is there still gets the full screen at once. **Regression guard, and directly touched.**

The status text handed to the indicator changed, so re-run round 5's R12 unaltered: both radios on,
D-MOTO's profile link confirmed live, launch, let the countdown resolve.

**PASS:** `btConnected=true` and `mode=OVERLAY` on the same pick, and a dump inside the pre-handshake
window shows `auto_connect_loading_overlay` present and `auto_connect_pill` absent. Round 5 read this
on its second attempt at 13:46:23.215.

---

### R10: no phone, no starting screen. **Regression guard, and directly touched.**

The pill's text changed, so prove the pill itself did not. Setup and steps exactly as round 5's R10:
both phones' Bluetooth and WiFi off, `native-preferred-device-mac` = D-MOTO, mode AUTO, timeout 10.

**PASS:** `btConnected=false`, `mode=PILL_THEN_OVERLAY`, `auto_connect_pill` present and
`auto_connect_loading_overlay` absent in a dump about 9 s after the pick, and the poke still fires.
Round 5 measured 9.968 s from `PROMPT_SHOWN` to the pick and the pill's own watchdog hiding it at
30.02 s. The pill text is now the driver's name and its state rather than `Android Auto is starting`;
that is the intended change, not a regression.

---

### R1: headless bring-up. **Regression guard, and directly touched.**

The wake guard changed, so the unattended loop is re-measured. Setup exactly as round 5's R1: both
MAC strings empty, `native-poke-bt-macs` and `auto-start-bt-macs` empty in both files and read back,
both phones' radios off, no `am start`, `ACTION_LOG_MARKER` then `ACTION_START_WIRELESS`, 120 s.

**PASS:** 5 pokes alternating both phones, first poke about **2.23 s** after the first `createGroup
SUCCESS` (round 5 read 2.228 s, round 4 2.225 s, round 3 2.199 s), and **zero** `paired device(s) are
not phones` lines. With both radios off there is no hands-free link to read, so the new signals must
not fire: **zero** override lines is part of PASS here.

---

## 7. Do not re-run

R2, R5, R5b, R11, R13. All passed in round 5 and none of their paths changed. R5 is superseded by R14,
which is the same run with a precondition that round 5 could not hold.

## 8. Report back

1. **R14**: whether `Calling socket.connect() for POCO X3 NFC` appeared, the `dumpsys` state recorded
   at step 2, and the pick-to-connect and connect-to-RFCOMM delays.
2. **R15**: the two row labels, the pill text, and the two view ids from the dump.
3. **R1**: poke count, first-poke delay, `not phones` count, override-line count.

One line each for R0, R10 and R12.

**The one thing to watch for that is nobody's criterion:** whether the override line ever names a
phone during R1 or R10, where no hands-free link should exist at all. It firing there would mean the
new signals are reading something the round did not set up.
