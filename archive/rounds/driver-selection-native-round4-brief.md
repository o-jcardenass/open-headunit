# driver-selection-native: round 4 brief

Round 3 measured the fix branch and eight of its nine runs passed. The deadline fix worked, the
dismissed-dialog fix worked, the corrected precondition settled the one run that was never a defect,
and every regression guard held. One run did not pass, for the second time: **R5, Switch Phone
reaching the phone the driver chose.**

Round 3 pinned down why, and the cause was not the one round 3's own reading proposed. This round is
short: it measures the fix for that, and re-runs only the guards whose code the fix touches.

Rig: **D-HU** (MT50) as the head unit, **D-POCO** and **D-MOTO** both bonded to it as driver phones.
Both stay bonded for every run in this brief. Nothing is unpaired this round.

Read **§3 of `TESTING-TEMPLATE.md`** and then **§3 of this brief**. The template's §3 predates the
automation receiver and does not describe it; §3 below does, and it supersedes the template wherever
the two disagree about how to drive the app.

**Brief revision 2, 2026-09-04.** If your copy does not carry this block, it is revision 1 and the
SHAs in §1 have moved. Nothing about the runs changed. The fix branch was compacted from six commits
to two, which rewrote both branch tips: the candidate is now `8bf8340d`, stamp `8bf8340d0c82`, and
the fix branch is `df516ce4`. The tree is byte-identical to revision 1's and the unit gate is still
1333 / 0, so only §1, §5's verification line and R0's stamp check differ. **Build revision 1's APK
and R0 will fail on the stamp.** Every SHA revision 1 named is kept reachable by the tag
`driver-selection-pre-compaction-20260904`.

This file is append-only. Corrections arrive as new commits and a `git pull` fast-forwards, so no
brief you have already read changes under you.

---

## 1. Build and the testing branch

**One APK this round.** There is no baseline build: round 1 measured `main` on this rig with these
phones, rounds 2 and 3 measured the fix branch, and all of those numbers are quoted inline wherever a
run needs one.

| | Branch | SHA |
|---|---|---|
| **Candidate** | `fork/testing/driver-selection-plus-automation` | `8bf8340d` |

Same branch as rounds 2 and 3, rebuilt. It is already pushed. **Both branch tips were rewritten this
round**, so a plain `git pull` on an existing local copy will refuse to fast-forward: reset onto the
remote rather than merging.

```bash
git fetch fork
git checkout testing/driver-selection-plus-automation
git reset --hard fork/testing/driver-selection-plus-automation      # 8bf8340d

# how it was made, and how to remake it if an input moves:
#   git checkout -b testing/driver-selection-plus-automation fork/fix/native-driver-selection-headless
#   git merge --no-edit fork/pr/automation-command-surface
# Clean both times, no conflicts, no manual resolution.
```

Its two inputs, both on `fork`:

| Input | Tip | What it is |
|---|---|---|
| `fix/native-driver-selection-headless` | `df516ce4` | **two** commits on the upstream driver-selection head `d103ce7a`, which is itself on `main` `ce2897c4`. Compacted from the six that rounds 2 and 3 measured, and provably so: the first reproduces round 2's tree exactly and the second reproduces the tree round 3 measured plus this round's fix. The second commit is what this round is about. |
| `pr/automation-command-surface` | `2f21242e` | unchanged since round 2: the automation receiver, and a build stamp that puts the commit into the log. Not under test; it is the instrument. |

**Identity check, exact as in round 3.** The build stamps its own commit:

```bash
./gradlew :app:assembleGithubDebug        # prints "Building from commit: 8bf8340d0c82"
adb shell am broadcast -f 0x00000020 -n com.andrerinas.headunitrevived/com.andrerinas.openheadunit.automation.AutomationReceiver \
  -a com.andrerinas.openheadunit.ACTION_QUERY_STATE
# the reply JSON on data= must carry "commit":"8bf8340d0c82"
```

Anything ending in `-dirty` means the tree had uncommitted changes when it was built. Stop and clean
it: a dirty build cannot be tied to this brief.

Unit gate, measured off-rig: **1333 / 0**, unchanged by the compaction. (The fix branch alone is
1304; round 3's merge was 1324.)
`:app:compileGithubDebugKotlin` succeeds off-rig, so a build failure on the rig is a toolchain
problem, not the branch.

Round 3 hit no install blocker, because the rig already carried a debug build. If it has since been
reverted to the release-signed app, `adb install -r` will refuse again and the remedy is still
approved: back up `settings.xml` **and** the device-protected mirror, `adb uninstall`, install,
restore, verify byte-identical.

---

## 2. What round 3 settled, and what this round is for

Fixed and proven on hardware across rounds 2 and 3. **None of these needs re-proving on its own.**
Four of them have a guard run below, because this round's change touches the same code; the other
four do not, and §7 says so:

- the headless wake poke (5 pokes in 120 s, first at 2.199 s, zero deferral lines, against round 1's
  0 and 9)
- the unanswered-prompt deadline reading the timeout setting (25.773 s at 10, 45.843 s at 30,
  20.070 s apart, against round 2's 61.54 and 61.625)
- the dismissed dialog and the poke that follows it (gate open 0.199 s after Home, poke 0.904 s
  after, against round 2's 54.47 s)
- cancel permanence (a phone accepted first time, zero refusal lines)
- the P2P group teardown on Switch Phone (1 `createGroup SUCCESS` on 1 interface index, against round
  1's 2 and 2)
- "close app on disconnect" killing the switch (pid alive, selector on screen)
- `Off` mode's picker, with the precondition correctly satisfied
- one bonded phone, about 26 s launch to session, against round 1's 36.3 s

### R5, and why round 3's reading was only half of it

Round 3 measured the second `SSL handshake complete` landing **34.47 s** after `Driver selected` and
belonging to D-MOTO, not D-POCO: every `WirelessServer: Incoming connection detected` in the capture
carried the same client IP, `192.168.49.189`, and D-POCO's never appeared at all. The guard visibly
worked, refusing D-MOTO **152** times while it retried Bluetooth at roughly 150 ms.

Round 3 read that as the 30 s exclusive window expiring about 4.5 s before D-POCO's join could
complete. The code says the window is only half the story:

- **The chosen phone was woken exactly once.** `selectDriver()` ends in a single 20 s Bluetooth hold
  and then stops. Nothing re-pokes it, because the round-robin loop stands down while a chosen
  driver's poke owns the slot. So D-POCO's only wake attempt finished about 10 s *before* the gate
  opened, and a longer window on its own would have changed nothing.
- **Picking a phone un-refused the one just switched away from.** The pick cleared the switch-away
  stamps, so the 60 s window opened by the switch itself, which was still running at 14:07:45, was
  discarded the moment the driver chose.

Two smaller holes in the same gate were found and closed with it: an expired prompt returned straight
out of the accept gate without consulting the switch rules at all, and every refusal wrote its own
log line, which is where the 152 came from.

The fix, the second commit on the fix branch: the chosen phone is woken up to **three** times, 20 s
each with a 15 s gap, and it is the only phone the accept gate lets in for as long as that wake is
running, capped at **120 s**. The switch-away refusal now survives the pick. Refusals log once per phone per window and are counted
after that.

Still unfixed on purpose: `Auto` and `Always` are the same mode, because both arms of the history
branch return true and the author's own tests pin that. Do not treat a prompt appearing with history
present as a finding.

---

## 3. The automation receiver

Unchanged from rounds 2 and 3, repeated here so this brief stands alone.

```bash
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
send() { adb shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit."$@"; }
```

**The package and the action prefix genuinely differ** (`headunitrevived` versus `openheadunit`), and
getting it wrong does nothing at all, silently. Paste the lines, do not retype them.

**`-f 0x00000020` is `FLAG_INCLUDE_STOPPED_PACKAGES`, and without it the first command of every run
is dropped.** `am force-stop` sets the package's stopped flag, and Android does not deliver
broadcasts to a stopped package even to an explicit component.

**Every `send` must produce `AutomationReceiver: <action>` at INFO.** If that line is missing the
broadcast never landed, whatever the shell printed, and the run is void rather than a FAIL. Check it
on the first command of each run before waiting out the capture.

| Command | Why it matters here |
|---|---|
| `ACTION_START_WIRELESS` | Arms the wireless mode without launching MainActivity, which is the headless case R1 is about. |
| `ACTION_NATIVE_AA_POKE --es extra_mac <MAC>` | A poke at one named phone. It runs the **same** code path as picking that phone in the selector, which is what R5 uses to avoid a timed tap. |
| `ACTION_NATIVE_AA_CANCEL_POKE` | The Cancel the selector's Back press sends. R5b uses it directly. |
| `ACTION_LOG_MARKER --es text <label>` | Writes `AutomationMarker: <label>` at WARN. Put one before and after every step. |
| `ACTION_QUERY_STATE` | Build identity and session state in one ordered broadcast; the reply comes back on `data=`. |

`ACTION_SET_LOG_LEVEL`, `ACTION_START_LOG_CAPTURE` and `ACTION_EXPORT_LOG` are gated behind
`allow-external-configuration`. **This round does not use them.**

### Four traps, all from earlier rounds

**`native-poke-bt-macs` is written by the app itself.** A completed handshake sets it to the phone
that just connected whenever the list is empty. So a "no history" precondition decays as soon as any
run forms a session: clear it and read it back **before every run that needs no history**, not once
per round. This is what voided round 2's R7.

**`auto-start-bt-macs` in `settings.xml` is resynced into the device-protected mirror on every
launch,** and `native-poke-bt-macs` re-seeds from it when the key is absent. Deleting a key is not
the same as clearing it. Write explicit empty sets and read both files back:

```bash
adb shell run-as $PKG cat shared_prefs/settings.xml | grep -oE '(auto-start|native-poke)-bt-macs[^/]*'
adb shell cat /data/user_de/0/$PKG/shared_prefs/settings_device_protected.xml | grep -oE '(auto-start|native-poke)-bt-macs[^/]*'
```

**Back up the device-protected mirror on the first touch, not after the first write.** Round 3 had to
reconstruct it from a read taken before any edit. Copy it aside before the round's first change to it.

**Wait for a dialog by polling the capture, not by sleeping.** Round 3's R3 was discarded and re-run
because a fixed sleep let the prompt's own 25 s deadline fire before the next step landed. Poll the
capture file for `PROMPT_SHOWN received` and fire the next command within about 0.2 s of it.

### Where a tap is unavoidable

Two runs need the screen: **R5 and R5b**, both tapping the Switch Phone row in the exit dialog. Both
ask for a screenshot rather than a claim. **Round 3 missed R5's screenshot**; take it this time,
`screencap` and not only a `uiautomator dump`.

**Neither run needs a second, timed tap.** Picking a phone in the selector and sending
`ACTION_NATIVE_AA_POKE --es extra_mac <MAC>` reach the same function, so the tap on Switch Phone is
the only one either run needs. **The selector raised by Switch Phone carries no countdown**, so there
is no timer to beat. Leave the dialog on screen and do not press Back on it in R5, which would cancel
rather than dismiss. R5b presses Cancel on purpose, and does it through the broadcast.

### Pre-registered as possibly INCONCLUSIVE

R5 needs D-POCO to wake and dial back over Bluetooth. Round 3 never saw it do so, and this round's
whole change is about giving it three chances instead of one. **If all three wake rounds fire and
D-POCO still never opens an RFCOMM connection, that is a phone-side answer, not a shorter-window
one**: record it that way, with the wake timestamps, rather than as an ask for a longer gate.

R4 needs a phone to open an RFCOMM connection after a cancel. Cycling D-MOTO's Bluetooth produced
exactly that in rounds 2 and 3, so it is known to work on this rig. If it genuinely does not dial in,
record INCONCLUSIVE and say which levers were tried.

---

## 4. Settings keys this round needs

Paste-ready elements. Every one gets read back before the launch, every run.

| Key | Type | Element |
|---|---|---|
| `wifi-connection-mode` | int | `<int name="wifi-connection-mode" value="3" />` |
| `native-ap-transport` | int | `<int name="native-ap-transport" value="0" />` |
| `native-driver-selection-mode` | int | `<int name="native-driver-selection-mode" value="1" />` (0 Off, 1 Auto, 2 Always) |
| `native-driver-selection-timeout` | int | `<int name="native-driver-selection-timeout" value="10" />` |
| `native-preferred-device-mac` | string | `<string name="native-preferred-device-mac"></string>` |
| `last-connected-native-mac` | string | `<string name="last-connected-native-mac"></string>` |
| `native-poke-bt-macs` | set | `<set name="native-poke-bt-macs" />` |
| `native-poke-all-paired` | boolean | `<boolean name="native-poke-all-paired" value="true" />` |
| `auto-start-bt-macs` | set | `<set name="auto-start-bt-macs" />` (and the mirror, see §3) |
| `kill-on-disconnect` | boolean | `<boolean name="kill-on-disconnect" value="false" />` |
| `log-level` | int | `<int name="log-level" value="2" />` |

Never delete the two string keys, clear them to an empty element: an absent string reads as its
default, and a blank element is what the read-back check can see.

**`log-level=2` (INFO) decides every run below.** Every decisive line in §5 is `AppLog.i` or higher.
Use `0` (VERBOSE) only if a run goes wrong and needs more context, and say so in the results.

MACs, unchanged: **D-MOTO `A0:46:5A:97:E4:95`**, **D-POCO `DC:B7:2E:5E:4E:59`**.

---

## 5. The lines that decide every run

All verified with `grep -F` against this candidate's tree before this brief was written, except the
band in the `createGroup SUCCESS` line, which is interpolated at runtime. Anything not on this list, treat as
context.

**Should not appear at all this round:**

```
NativeAA: Cancelling background multi-device poke loop because selection prompt is active.
AapService: Native AA user exit. Stopping active launcher.
```

The first is the method a round-2 defect lived in. It was deleted, so a single occurrence means the
wrong tree was built and R0 missed it.

**New this round, and what proves the fix:**

```
connection attempts before this one.
```

A line fragment on purpose: it begins with a count. This is the refusal summary, printed once when
the gate lets somebody in after having turned attempts away. It replaces the 152 identical lines
round 3 recorded, so the refusal fragments below should now appear about **once per phone**, with
this line carrying the total.

**Already proven, used as guards:**

```
NativeAA: a driver switch is starting, so
waits until that phone has had its turn.
is the phone this switch moved away from, so it is not let back in yet.
NativeAA: the driver prompt went unanswered — waking every paired phone again.
NativeAA: the cancelled prompt has expired — accepting
NativeAA: a phone arrived over Bluetooth — the cancelled prompt no longer stands.
NativeAA: a chosen driver's wake poke is running — not replacing it with the multi-device loop.
NativeAA: Multi-driver selection is active and awaiting user choice — deferring automated multi-device poke loop.
AapService: Native AA session ended; keeping the
```

The three fragments at the top each begin or end with a MAC address. Grep the fragment.

**The ordinary landmarks:**

```
AapService: ACTION_NATIVE_AA_PROMPT_SHOWN received
AapService: ACTION_NATIVE_AA_SWITCH_DEVICE received
AapProjectionActivity: User requested switch driver
NativeAA: cancelPoke() called — user explicitly canceled driver selection.
NativeAA: User explicitly canceled driver selection — refusing connection from
NativeAA: Driver selected:
NativeAA: Attempting active poke to device
NativeAA: Attempting manual poke to
NativeAA: Manual poke to
NativeAA: Successfully poked
NativeAA: Connection accepted from
WifiDirectManager: 5GHz createGroup SUCCESS!
WifiDirectManager: Standard createGroup SUCCESS!
WirelessServer: Incoming connection detected
AapSslContext.performHandshake | SSL handshake complete
AapService destroying
AutomationReceiver:
AutomationMarker:
```

`Attempting manual poke to` and `Manual poke to ... finished.` now bracket **each** wake round, so a
chosen driver produces up to three of each. Every earlier build could only ever emit one, because the
wake was a single hold. Three is the fix working, not a loop.

Count groups with the pattern that catches both spellings, and count interfaces alongside it, because
one group can log twice:

```bash
grep -c "createGroup SUCCESS" log.txt
grep -o "p2p-wlan0-[0-9]*" log.txt | sort -u
```

---

## 6. Runs

Seven runs, and only two of them are new work. Every run uses the `send` helper from §3, `-f` flag
included. Bracket each step with `ACTION_LOG_MARKER`, and confirm `AutomationReceiver:` appears for
the first command of each run before waiting out a capture.

### R0: build gate

Build, install, unit-test on the rig. PASS needs all three: the build prints
`Building from commit: 8bf8340d0c82` with no `-dirty`; `ACTION_QUERY_STATE` replies with the same
commit; the unit gate reads **1333 / 0**. Record the APK md5.

Any other unit count means the wrong tree was built. Stop and say so rather than running the round.

---

### R1: headless bring-up. **Regression guard.**

The wake poke's job now stays claimed for up to 90 s rather than 20 s while a chosen driver is being
woken. Nobody is chosen in this run, so nothing should change, and that is what this guard checks.

Setup: two phones bonded, both radios off, `native-driver-selection-mode=1`, timeout `10`, both MAC
strings empty, `native-poke-bt-macs` and `auto-start-bt-macs` empty in **both** files, all read back.
Then, **without launching MainActivity at all**:

```bash
send ACTION_LOG_MARKER --es text R1-start
send ACTION_START_WIRELESS
# let it run 120 s
send ACTION_LOG_MARKER --es text R1-end
```

The marker is deliberately first: delivering any broadcast clears the stopped flag and creates the
app process, so the marker landing is also the proof that `ACTION_START_WIRELESS` will land.

**PASS**: at least one `Attempting active poke to device` inside the 120 s, and **zero**
`Multi-driver selection is active` lines. Report the poke count and the seconds from
`createGroup SUCCESS` to the first poke. Round 3 read 5 pokes at 2.199 s, round 2 5 at 2.20 s, `main`
5 at 2.264 s.

**FAIL**: any `Multi-driver selection is active` line, or a poke count of 0.

---

### R2: the deadline still reads the timeout setting. **Regression guard, one sub-run.**

The accept gate's expired-prompt branch changed, so the deadline path is re-run once rather than
trusted. Timeout `10` only; round 3 already measured both ends of the scale.

Setup as R1 except MainActivity **is** launched normally, so the dialog appears, and nobody touches
it. Clear `native-poke-bt-macs` first.

Measure from `ACTION_NATIVE_AA_PROMPT_SHOWN received` to `the driver prompt went unanswered`.

**PASS**: about **25 s**, within a couple of seconds. Round 3 read 25.773 s.

**FAIL**: about 60 s, which is round 2's failure signature and would mean the deadline is reading the
credential cadence again.

Report as well: the count of `Multi-driver selection is active` lines (round 3 read 1) and the
seconds from `went unanswered` to the first `Attempting active poke to device` (round 3 read 0.012 s).

---

### R4: cancel stops the poke without deafening the unit. **Regression guard, and directly touched.**

Cancel now has to kill a wake loop that can run 90 s, not a single 20 s hold, and the cancel refusal
is logged once per phone rather than per attempt. Both are in this run's path.

Setup as R2, timeout `10`, but put D-MOTO's MAC in `auto-start-bt-macs` (both files) so the Bluetooth
arrival path is armed. This is the one run that wants that key set. Launch MainActivity, wait for the
prompt, then:

```bash
send ACTION_LOG_MARKER --es text R4-cancel
adb shell input keyevent KEYCODE_BACK
```

Confirm `cancelPoke() called` and that pokes stop. **Leave the app running** so the Android Auto
listeners stay open, wait **35 s**, then cycle D-MOTO's Bluetooth off and on.

**PASS**, both: no `Attempting manual poke to` or `Attempting active poke to device` after the cancel
marker, and the phone is then accepted and a session forms, with the log naming which lever did it,
either `the cancelled prompt has expired` or `a phone arrived over Bluetooth`. Report which. Round 3
read the second, with zero refusal lines.

**FAIL**: any poke after the cancel, or repeated `User explicitly canceled driver selection` with no
session.

**Expected and not a failure**: refusals inside the first 30 s after the cancel. There should now be
at most one line per phone, plus a `connection attempts before this one.` summary if more were turned
away.

---

### R5: Switch Phone reaches the phone the driver chose. **The point of the round.**

Setup: `native-preferred-device-mac` = D-MOTO's MAC, timeout `10`, `kill-on-disconnect=false`,
**both phones' radios on**, `native-poke-bt-macs` cleared and read back.

Launch, let the countdown resolve to D-MOTO with no touch, and get a session. Then:

```bash
send ACTION_LOG_MARKER --es text R5-switch
adb shell input keyevent KEYCODE_BACK          # opens the exit dialog
# tap the Switch Phone row, then wait for ACTION_NATIVE_AA_PROMPT_SHOWN received
adb shell screencap -p /sdcard/r5_selector.png     # round 3 missed this
send ACTION_LOG_MARKER --es text R5-pick-poco
send ACTION_NATIVE_AA_POKE --es extra_mac DC:B7:2E:5E:4E:59
# watch 180 s, not 120: the wake budget alone is 90 s
```

**PASS**, all five:

- a second `SSL handshake complete`, and the `Connection accepted from` line before it names
  **POCO X3 NFC**. This is the criterion rounds 2 and 3 both missed.
- `NativeAA: a driver switch is starting, so` present, naming D-MOTO's MAC.
- exactly **1** distinct `p2p-wlan0-N` across the whole capture. Rounds 2 and 3 both read 1; round 1
  read 2. This half already passes and is guarded here.
- `AapService: Native AA session ended; keeping the` present.
- `AapService: Native AA user exit. Stopping active launcher.` **absent**.

**FAIL**: the second handshake belongs to D-MOTO. That is rounds 2 and 3.

Report all six of these, because they are what round 3 could not answer and they decide what happens
next whichever way the run goes:

1. every `Attempting manual poke to POCO X3 NFC` timestamp. Expect **1 to 3**. Round 3's build could
   only ever emit one, so this count is the most direct evidence that the right tree is running.
2. whether `Successfully poked` ever names POCO X3 NFC, and at what time.
3. every `Connection accepted from` line, with the device name and the timestamp.
4. the client IP on every `WirelessServer: Incoming connection detected`. Round 3 read the same IP,
   `192.168.49.189`, for both sessions, which is how it knew the second one was D-MOTO.
5. the count of each refusal fragment, and whether `connection attempts before this one.` appeared
   with what total. Round 3 read 152 refusal lines and no summary.
6. the seconds from `NativeAA: Driver selected:` to the second handshake. Round 3 read 34.47 s.

**If all three wake rounds fire and D-POCO never opens an RFCOMM connection at all**, say so
explicitly. That is a phone-side finding, and a longer gate would not have helped.

---

### R5b: the driver changes their mind. **New.**

The gate can now hold every other phone off for up to 120 s, so this run proves the driver can end it
early rather than waiting it out.

Self-contained on purpose, because it must work whichever way R5 went. Set up exactly as R5 and repeat
its steps up to and including the pick, then cancel **within about 20 s of it**, before D-POCO has had
time to answer:

```bash
# ... same setup and same steps as R5, through the ACTION_NATIVE_AA_POKE at D-POCO
send ACTION_LOG_MARKER --es text R5b-cancel      # about 20 s after the pick
send ACTION_NATIVE_AA_CANCEL_POKE
# watch 90 s
```

If D-POCO happens to connect before the 20 s are up, the run cannot be done that way: say so, and
report it as R5 passing twice rather than as an R5b result.

**PASS**, all three:

- `cancelPoke() called` within about 2 s of the marker.
- **zero** `Attempting manual poke to` after the marker. The wake loop must stop, not run out its
  remaining rounds.
- **zero** `waits until that phone has had its turn.` after the marker, and no
  `is the phone this switch moved away from` either. Both windows are cleared by the cancel, so
  D-MOTO is no longer being refused on either ground. The span that decides this is the minute
  **after** the cancel's own 30 s window closes: inside it the cancel refuses first and neither line
  can print anyway.

**FAIL**: manual pokes continue past the cancel, or D-MOTO is still refused a minute later.

Refusals inside the first 30 s **after** the cancel are the cancel's own window and are expected;
they say `User explicitly canceled driver selection`, which is a different line from the two above.
Do not read them as a failure.

If D-MOTO happens to dial in and form a session after that 30 s, that is a bonus observation and not
part of the verdict; report it if it happens.

---

### R9: a chosen driver's poke is not trampled. **Regression guard, and directly touched.**

The manual poke now owns its slot for up to 90 s instead of 20 s, so the guard line's count is
expected to change. The verdict does not.

Setup as R1, but after the group is up:

```bash
send ACTION_LOG_MARKER --es text R9-poke
send ACTION_NATIVE_AA_POKE --es extra_mac A0:46:5A:97:E4:95     # D-MOTO
# watch for 180 s
```

**PASS**, all three: `Attempting manual poke to motorola edge 30 neo` follows the marker, **at least
one** `a chosen driver's wake poke is running` appears while it runs, and **zero**
`Attempting active poke to device: POCO X3 NFC` in that window.

Report the count of `Attempting manual poke to motorola edge 30 neo`. Expect up to **3**, spaced
about 35 s apart, where every earlier build could only emit one. Report the guard-line count too:
round 3 read 3 of them across an 88 s window, and more here is expected rather than suspicious,
because the slot is held longer.

**FAIL**: the round-robin resumes and pokes D-POCO while the manual poke is still running.

**INCONCLUSIVE, not PASS**: zero round-robin pokes *and* zero `a chosen driver's wake poke is
running`. That combination means no credential redelivery happened during the manual poke, so the
guard was never reached and the quiet log proves nothing.

---

## 7. Do not re-run

- **`main`'s numbers.** Round 1 measured them on this rig with these phones: 5 pokes at 2.264 s,
  36.3 s single-phone connect, the "Select Bluetooth Device" list on the `Off` button.
- **R3, the dismissed dialog.** It runs through the round-robin poke loop, which this round does not
  touch, and round 3 measured it at 0.199 s and 0.904 s.
- **R6, kill-on-disconnect.** Nothing in the disconnect path changed. Round 3 read a live pid and the
  selector on screen.
- **R7, the `Off` picker.** Nothing in the history-resolution path changed, and round 3 satisfied its
  precondition correctly for the first time.
- **R8, one bonded phone.** No chosen target and no switch exist in that configuration, so none of
  this round's code is reached. Round 3 read about 26 s. **Nothing is unpaired this round**, which is
  also why R8 is absent.
- **Getting a phone's Bluetooth to *profile*-connect to the head unit.** No lever on this rig does
  it, and no run here depends on it.
- **Whether `Auto` differs from `Always`.** Unfixed on purpose, see §2.

---

## 8. Report back

Three things decide whether this ships:

1. **R5**: which phone the second `SSL handshake complete` belongs to, and the six numbers listed
   under that run. Rounds 2 and 3 both read D-MOTO; this round needs D-POCO.
2. **R5b**: whether the cancel stopped the wake loop and cleared both refusal windows.
3. **R9**: the manual-poke count and the guard-line count, and that D-POCO was never poked by the
   round-robin during it.

Plus, in one line each: R0's commit, md5 and unit count; R1's poke count and deferral-line count; R2's
measured deadline; and whether R4 accepted the phone again and which lever did it.

One opportunistic step, not a criterion and not a run: round 3 recorded an operator seeing
"Android Auto is starting" with both phones' radios off, and tracing it against the code and every
capture found no path that would do that. If it appears again, capture the timestamp and a screenshot.
Without a repro it stays an open observation.
