# bring-up-status-pill-and-poke-readiness: round 9 brief

**Candidate:** `fork/feat/native-aa-enhancements-extbt` @ `a8917a7a`
**Baseline:** none needed. See §1.
**Gate:** 1831 tests / 0 failures
**Read `TESTING-TEMPLATE.md` first.** This brief only says what is different about this round.

---

## 0. What this is and why it exists

Round 8 FAILed one run out of ten and passed everything else, including Part C's first clean sweep
and F1's condition 3. This round grades the fix for that one FAIL, and the first hardware run of a
change nothing has ever seen.

**A2b's give-up bound still could not be re-armed, and the reason was not the one round 8's own
brief predicted.** Round 7's fix said in its comment that the bound is "re-armed by whichever
activity is alive". It was not. The only re-arm sat inside `beginAutoConnect`'s early return, so it
needed something to call `beginAutoConnect` a *second* time, and the trigger this round is about
never does: `HomeFragment.checkNativeDriverSelectionOnStartup()` is that path's only caller and the
companion flag `hasCheckedNativeDriverSelection` is already set and survives the rebuild.
`onResume` called `endAutoConnectIfExpired()`, which only **ends** a bound already spent and never
**arms** one still ahead, and the watchdog coroutine lives on the activity's own scope and died with
the old instance. Round 8 measured the result exactly: 270 s of the pill cycling
`WAITING_FOR_PHONE` / `WAKING_PHONE`, the arm line printed once, and no give-up ever.

The fix makes `ensureAutoConnectWatchdog()` the single spelling of the decision, calls it from
`onResume` straight after ending an expired attempt, and has `beginAutoConnect` end a spent attempt
*before* its own guard so a stale flag cannot swallow the next attempt. **A re-arm now says so on
the line**, which is what makes this round gradable at a glance rather than by timing.

**The auto-start offer no longer appears on the home screen.** Its ASK arm has reached a screen
twice in nine rounds, and the reason is structural rather than a bug: a phone can poke this unit
awake by itself, so in the sessions the question is about nobody is looking at the home screen at
all. The question is asked at the session's **first rendered frame** instead, on a bar across the
bottom of the projection built the same way the renderer-confirmation bar is, with a **20 s
countdown that answers No**. An expiry marks the phone answered exactly as the No button does, so
the question is put once per phone and never nags. The two-phone clean-up arm did not move: it is
still the home screen's, and it still only rewrites a setting.

None of that second change has been on hardware. **Part O is the round.**

---

## 1. Build and baseline

No baseline build. Every run grades either a line that exists on no earlier build, or a surface that
no earlier build has.

**History was rewritten since round 8.** The round 8 fix folded into the branch's Connection commit,
which rewrote the four SHAs above it, so **`e466171a` no longer resolves**. The branch still carries
five commits and is still the only branch of ours.

```bash
git fetch fork && git checkout -B rig fork/feat/native-aa-enhancements-extbt
git log --oneline -1        # expect a8917a7a
```

Build, gate and install per `TESTING-TEMPLATE.md` §2 and §5. The gate is **1831 / 0** (1829 before,
plus five new `AutoStartOfferPolicy` cases and minus three it replaced).

---

## 2. What is different about this round

- **The recreate lever changed, and round 8's brief had it wrong.** `ACTION_RECREATE_MAIN` cannot be
  fired from adb at all. §3a below is the lever that works and `TESTING-TEMPLATE.md` §7a now carries
  it.
- **A2 is the control for A2b, not just a regression.** Its whole job this round is to establish
  that the arm line appears **once** when nothing is recreated, so A2b's **twice** means the
  recreation and not the build.
- **A2e is new**: two recreations in one attempt. It is the run that separates "the bound was
  re-armed for what was left" from "the bound restarted", because after two rotations a restarting
  bound is minutes late and a continuing one is not.
- **Part O needs D-HU and cannot use a phone as the head unit.** The offer fires on the first
  *rendered frame*, and a phone standing in as head unit never gets one: Gearhead withholds video
  from it every run. So Part O is D-HU only.
- **Part O needs D-HU to have exactly one paired phone, which is not its current state**, and O5
  needs the opposite. Read §2a before touching anything.
- **F3, A4 and the whole of Part C are not re-run.** See §6.

## 2a. The pairing conflict, and the order that resolves it

`AutoStartOfferPolicy.decide` asks the question only when the unit is paired to **exactly one**
phone, and clears the stored trigger only when it is paired to **two or more**. Those are the same
unit in opposite states, so **O5 and O1 to O4 cannot both run on D-HU without a pairing change in
between** - the same shape as round 6's A1/B2 collision.

`TESTING-TEMPLATE.md` §7a records D-MOTO as bonded to D-HU since round 5, so D-HU currently reports
**two** paired phones. Therefore:

1. **Run O5 first**, in that existing two-phone state.
2. Then remove one pairing by hand on D-HU (either D-MOTO or D-POCO, your choice) and **say in the
   results which one you removed**.
3. Run O1 to O4.
4. Restore the pairing afterwards and say so.

**Verify the state, do not assume it.** The authoritative read is the roll-up in a Verbose capture:

```bash
grep -a "driver candidates:" capture.txt | tail -3
```

It reads `BluetoothHelper: driver candidates: N phone, N unknown, N not a phone`. Note it is logged
at INFO **only when the composition changes** and at DEBUG otherwise, so take it from a Verbose
capture and do not count its occurrences. If it says `2 phone` when O1 is about to start, **O1 to O4
are UNTESTABLE** and should be reported that way rather than run.

---

## 3. Settings keys this round needs

Written in `shared_prefs/settings.xml` with the app stopped, never in the UI. D-HU is rooted;
D-POCO goes through `run-as`. **Read every one of them back before launching and report the
read-back.**

### D-POCO, as head unit (Part A)

| Key | Type | Value | Used by |
|---|---|---|---|
| `wifi-connection-mode` | int | `3` | A2, A2b, A2e |
| `native-driver-selection-mode` | int | `1` (AUTO) | A2, A2b, A2e |
| `screen-orientation` | int | **`0`** (SYSTEM) | A2, A2b, A2e |
| `auto-start-bt-macs` | set | **delete the element** | A2, A2b, A2e |
| `auto-start-offer-answered-macs` | set | **delete the element** between every attempt | A2, A2b, A2e |

`screen-orientation=0` is `SCREEN_ORIENTATION_USER`, which is what makes §3a's lever work. Do not
set it to anything else, and unlike round 8 **do not edit it mid-attempt**: that edit was never
needed and it muddies which relaunch was which.

**D-POCO carries exactly one paired phone (D-MOTO), and D-MOTO's Bluetooth stays off** for the whole
of Part A. That is the state the bound exists for: an attempt aimed at a phone that never answers.

### D-HU (Part O)

| Key | Type | Value | Used by |
|---|---|---|---|
| `wifi-connection-mode` | int | `3` | all of Part O |
| `auto-start-bt-macs` | set | **delete the element** | O1, O2, O4 |
| `auto-start-bt-name` | string | **delete the element** | O1, O2, O4 |
| `auto-start-offer-answered-macs` | set | **delete the element** before O1, O2 and O4; **leave alone** before O3 | O1 to O4 |
| `auto-start-bt-macs` | set | one MAC, any paired device | **O5 only** |
| `log-level` | - | Verbose | all |

Every Part O run that must re-ask the question needs **both** `auto-start-bt-macs` and
`auto-start-offer-answered-macs` cleared: either one alone answers NOTHING.

**Read the keys back after a run with the app stopped** (`headunit://exit`, then force-stop, then
read `settings.xml`). The offer writes them with `apply()`, which is asynchronous, and a read taken
from under a live session can catch the file before the write lands and turn a PASS into a
phantom FAIL.

---

## 3a. Recreating an activity, which is what A2b and A2e turn on

**The broadcast round 8 was given does not work and never did.** `MainActivity` registers that
receiver `RECEIVER_NOT_EXPORTED`, so a shell-uid broadcast is never dispatched to the app however it
is addressed; `am` still answers `Broadcast completed: result=0`, and
`dumpsys activity broadcasts history` shows it enqueued and never delivered. Do not use it.

Rotate the unit for real instead:

```bash
adb shell settings put system accelerometer_rotation 0   # once, before the run
adb shell settings put system user_rotation 1            # the recreation
adb shell settings put system user_rotation 0            # a second recreation, for A2e
```

`MainActivity`'s manifest `configChanges` is `keyboardHidden|uiMode` only, so a rotation genuinely
destroys and rebuilds it. **Confirm each recreation** with either of:

- two `WindowManager: finishDrawing of relaunch` events, or
- a second `MainActivity.logLaunchSource` line.

**Grade on the arm-line count, never on the PID.** Android rebuilds an activity inside the same
process, so an unchanged PID says nothing either way. Report it, but do not reason from it.

Restore `accelerometer_rotation` and `user_rotation` at the end of the round.

---

## 4. The lines that decide every run

All checked with `git grep -F` against `a8917a7a`. **assembled** means the line is built at runtime
from several literals and only the fragment named can be grepped.

| Line | Where it decides |
|---|---|
| `Auto-connect: begin (` | A2, A2b, A2e |
| `Auto-connect: this attempt gives up in ` | A2, A2b, A2e |
| `, re-armed)` | A2b, A2e. **assembled**, and new this build |
| `Auto-connect: nothing answered this attempt (mode=` | A2, A2b, A2e |
| `HomeFragment: Unambiguous driver` | A2, A2b, A2e |
| `MainActivity: status pill step: ` | A2, A2b, A2e |
| `the Bluetooth auto-start offer is up for` | O1, O2, O3, O4. **New this build** |
| `the Bluetooth auto-start offer was answered` | O1, O2. **New this build** |
| `HomeFragment: more than one phone is paired, so the Bluetooth auto-start device is cleared.` | O5 |
| `BluetoothHelper: driver candidates: ` | §2a, and every Part O run |
| `Handshake: SSL handshake complete` | O1 to O3, that a session formed |

The offer's two lines are assembled from `%s`, so their full shape is:

```
AapProjectionActivity: the Bluetooth auto-start offer is up for <name> (<mac>).
AapProjectionActivity: the Bluetooth auto-start offer was answered <outcome>.
```

and `<outcome>` is exactly one of **`yes`**, **`no`** or **`by running out`**. Those three strings
are the whole grade of O1 and O2; quote the line, do not paraphrase it.

The arm line's full shape is
`Auto-connect: this attempt gives up in 68s (mode=PILL_THEN_OVERLAY, re-armed)`, and the
`, re-armed` suffix is present only on a bound this activity inherited rather than started.

---

## 5. Runs

### R0 - build, gate, install, identity

The gate is **1831 / 0**. Install on D-HU and D-POCO and confirm `pm path` + `md5sum` match the
built APK on both.

**Identity:** no new class this build, so the round 8 DEX grep does not apply. Grep the installed
base APK's `classes*.dex` for **`maybeOfferAutoStart`**, which exists on no earlier build. A second,
independent check is the string `re-armed`.

---

### Part A - the bound survives an activity rebuild

D-POCO as head unit, D-MOTO paired with its **Bluetooth off throughout** (verify `state: OFF` before
each attempt). Delete both auto-start keys and read them back before every attempt.

All three start the same way: launch the app cold, confirm
`HomeFragment: Unambiguous driver (motorola edge 30 neo) - auto-connecting directly without prompt`
and `Auto-connect: begin (Native-AA driver: ...)`, and keep the screen awake
(`svc power stayon true`).

#### A2 - the control: nothing is recreated, and the bound is armed once

Touch nothing for the whole window.

**PASS requires:**

1. `Auto-connect: this attempt gives up in ` appears **exactly once** in the whole capture, names
   **150**, and carries **no** `, re-armed`.
2. `Auto-connect: nothing answered this attempt (mode=PILL_THEN_OVERLAY), ending it` appears, and
   the wall clock from `Auto-connect: begin (` to it is **150 s ± 15 s**.
3. The pill is still up afterwards on whatever step the stack is on. A pill rewound to `ARMED` here
   is a FAIL.
4. No `FATAL EXCEPTION`, no `WindowLeaked`.

This is also A2b's control, so report its exact count and seconds even though round 8 passed it.

#### A2b - one recreation re-arms what is left - **this is the point of the round**

Start as above. At **60 s ± 10 s** into the attempt, recreate the activity with §3a's lever
(`user_rotation 1`). Confirm the recreation by §3a's two markers before reading anything else.

**PASS requires:**

1. Evidence of the rebuild, per §3a.
2. `Auto-connect: this attempt gives up in ` appears **exactly twice**. The first names **150** and
   carries no suffix; the second names **noticeably less** (around 90 if the lever landed at 60 s)
   and **carries `, re-armed`**.
3. `Auto-connect: nothing answered this attempt (mode=` appears, and the wall clock from the
   **first** `Auto-connect: begin (` to it is **150 s ± 15 s**.
4. No `FATAL EXCEPTION`, no `WindowLeaked`.

**What each failure shape means, so the result is useful either way.** One arm line and no give-up
is round 8's defect unchanged. Two arm lines but a give-up at roughly 210 s means the bound
restarted from the rebuild instead of continuing, which is a different and much smaller bug. A
second arm line naming 150 rather than about 90 is the same thing seen a step earlier. Report the
seconds each line names, always.

#### A2e - two recreations, and the deadline still does not move

Start as above. Recreate at **40 s ± 10 s** (`user_rotation 1`) and again at **90 s ± 10 s**
(`user_rotation 0`).

**PASS requires:**

1. Evidence of both rebuilds.
2. `Auto-connect: this attempt gives up in ` appears **exactly three times**, each naming less than
   the one before it, with `, re-armed` on the second and third and not on the first.
3. `Auto-connect: nothing answered this attempt (mode=` appears, and the wall clock from the
   **first** `Auto-connect: begin (` to it is **still 150 s ± 15 s**.

Condition 3 is the whole value of this run. A bound that restarted on each rebuild would end at
roughly 240 s here, which is far enough outside the window that no judgement call is needed.

---

### Part O - the auto-start offer at projection start

**D-HU only** (§2, §2a). Read §2a's ordering before starting: **O5 runs first**, then the pairing
change, then O1 to O4. Capture at Verbose throughout, and take a **screenshot** of the bar in O1 and
O2 - the bar is the deliverable and a log line alone cannot show it is legible or that its buttons
are reachable.

Every O run that forms a session is an ordinary Native AA connection to the remaining paired phone;
nothing about the connection itself is under test, so use whatever bring-up this rig does most
reliably, and report `Handshake: SSL handshake complete` so a failed session is not read as a
missing offer.

#### O5 - two paired phones still clear the stored trigger, on the home screen

D-HU in its current **two-phone** state. Set `auto-start-bt-macs` to one MAC (any paired device) and
`auto-start-bt-name` to anything, with the app stopped. Launch the app and sit on the home screen
for 30 s without connecting.

**PASS requires:**

1. `HomeFragment: more than one phone is paired, so the Bluetooth auto-start device is cleared.`
2. `auto-start-bt-macs` is empty or absent in `settings.xml` afterwards.
3. The `driver candidates:` roll-up says **`2 phone`** or more, which is what makes the run mean
   anything. Quote it.

If condition 3 reads `1 phone`, this run proved nothing and should be reported INCONCLUSIVE rather
than PASS - the clean-up would have been correct to skip.

#### O1 - the offer appears at the first frame, and Yes turns auto-start on

After the pairing change of §2a. Clear `auto-start-bt-macs`, `auto-start-bt-name` and
`auto-start-offer-answered-macs`; read all three back. Connect and let the projection come up.
**Tap Yes** on the bar.

**You have 20 s from the bar appearing**, after which it answers No for you and O1 is spent: the
keys have to be cleared and the session formed again. Be at the unit before the picture arrives, or
drive the tap with `input tap` at coordinates read off a `screencap` taken the moment the bar shows.

**PASS requires:**

1. `the Bluetooth auto-start offer is up for <name> (<mac>).` appears, **after** the picture is up,
   and `<name>` is the phone that connected.
2. A screenshot showing the bar across the bottom of the projection with its question, its
   countdown, and both buttons.
3. `the Bluetooth auto-start offer was answered yes.`
4. Afterwards, `auto-start-bt-macs` contains that phone's MAC and `auto-start-bt-name` its name.
5. The device-storage mirror carries it too, which is what the boot and USB receivers read. D-HU is
   rooted, so read it directly:

   ```bash
   adb shell su -c 'cat /data/user_de/0/com.andrerinas.headunitrevived/shared_prefs/settings_device_protected.xml'
   ```

6. The bar is gone from the screen once answered, and the projection is undisturbed underneath.

#### O2 - the countdown answers No on its own

Clear the same three keys again (the app must be stopped, so this is a fresh session). Connect, let
the projection come up, and **do not touch the bar**.

**PASS requires:**

1. `the Bluetooth auto-start offer is up for ` appears.
2. A screenshot taken **part-way through**, showing a countdown number lower than the one it opened
   with. Say what both numbers were. Do not time this by hand: run a `screencap` every 3 s from
   before the handshake completes until well after, and keep the two frames that show different
   numbers.
3. `the Bluetooth auto-start offer was answered by running out.` appears **20 s ± 3 s** after the
   "is up" line. Report the measured figure.
4. Afterwards `auto-start-bt-macs` is still empty or absent, and `auto-start-offer-answered-macs`
   **does** contain the phone's MAC. Both halves matter: the first is "it did not turn itself on",
   the second is "it counted as an answer".
5. The bar is gone and the projection is undisturbed.

#### O3 - asked once per phone, and not again

Immediately after O2, **leave `auto-start-offer-answered-macs` exactly as O2 left it**, clear
nothing, and form another session.

**PASS requires:** `the Bluetooth auto-start offer is up for ` appears **nowhere** in the capture,
and the session otherwise reaches `Handshake: SSL handshake complete` and a picture. This is the run
that proves the timeout really was a No and not a deferral.

#### O4 - the home screen no longer asks

Clear all three keys again. Launch the app and sit on the **home screen for 90 s without
connecting**, with one paired phone and `wifi-connection-mode=3`.

**PASS requires:**

1. No dialog or bar appears on the home screen. Take a screenshot at about 60 s.
2. `the Bluetooth auto-start offer is up for ` appears nowhere.
3. `auto-start-offer-answered-macs` is still empty afterwards, so nothing was consumed by a question
   that was never put.

**This is the positive control for the whole change.** On every build up to and including
`e466171a`, this is precisely where the dialog appeared, and round 7 caught it there twice. If it
still appears here the trigger did not move.

---

## 6. Do not re-run

Settled, and not worth this round's time:

- **A2c** (a sleeping unit still gives up) passed on round 8 at 150.007 s, and the deadline
  arithmetic it grades is untouched by this fix.
- **A4** is UNTESTABLE on both rig units: neither D-HU nor D-POCO reports `host_connected=true`.
- **C1 to C6** are a clean sweep on round 8 and nothing in this candidate touches
  `StationStandDown`, `StationStandDownPolicy` or `P2pBandPreference`.
- **D2** passed on round 8 with a negative control of its own, and `WifiLauncherManager` is
  unchanged.
- **F1 and F1b** are done: F1 reached condition 3 and F1b's three clean sessions settled the
  cross-band question. Nothing in this candidate touches the audio or band path.
- **F3** stays retired for round 7's reason: a test location provider never reaches Android Auto's
  own nav rendering.

## 7. Report back

Whatever else the results carry, these decide the shipping question:

1. **For each of A2, A2b and A2e:** the number of `Auto-connect: this attempt gives up in ` lines,
   the seconds each one names, which of them carry `, re-armed`, and the wall clock from the first
   `Auto-connect: begin (` to the give-up. Three counts and three durations; that is the round's
   core.
2. **Whether the offer bar appeared on hardware at all**, with the screenshot. Nine rounds have not
   managed to put this question on a screen.
3. **The measured seconds from "is up" to "answered by running out"** in O2, and whether
   `auto-start-bt-macs` stayed empty across it.
4. **The `driver candidates:` roll-up quoted for every Part O run**, so each one can be read against
   the pairing state it actually ran in.
5. Which pairing was removed and whether it was restored.
