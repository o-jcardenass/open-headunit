# driver-selection-native: round 3 brief

Round 2 measured the fix branch and got most of what it went for. Five of round 1's measured defects
are fixed on hardware and both regression guards held. Three runs did not pass. This round re-runs
those three, two of them against fixes and the third against a corrected precondition, and repeats
the guards.

Rig: **D-HU** (MT50) as the head unit, **D-POCO** and **D-MOTO** both bonded to it as driver phones.
Both stay bonded for R1 to R7 and R9; R8 unpairs one on purpose and re-pairs afterwards.

Read **§3 of `TESTING-TEMPLATE.md`** and then **§3 of this brief**. The template's §3 predates the
automation receiver and does not describe it; §3 below does, and it supersedes the template wherever
the two disagree about how to drive the app.

This file is append-only. Corrections arrive as new commits and a `git pull` fast-forwards, so no
brief you have already read changes under you.

---

## 1. Build and the testing branch

**One APK this round.** There is no baseline build: round 1 measured `main` on this rig, with these
phones, and round 2 measured the first two commits of the fix branch. Both sets of numbers are the
control, and they are quoted inline wherever a run needs one.

| | Branch | SHA |
|---|---|---|
| **Candidate** | `fork/testing/driver-selection-plus-automation` | `e926beb0` |

Same branch as round 2, moved forward. It is already pushed, and a `git pull` on it fast-forwards.
**You own it**: if either input moves, remake it rather than cherry-picking onto it.

```bash
git fetch fork
git checkout testing/driver-selection-plus-automation
git pull --ff-only                                    # e926beb0

# how it was made, and how to remake it if an input moves:
#   git checkout -b testing/driver-selection-plus-automation fork/fix/native-driver-selection-headless
#   git merge --no-edit fork/pr/automation-command-surface
# Clean both times, no conflicts, no manual resolution.
```

Its two inputs, both on `fork`:

| Input | Tip | What it is |
|---|---|---|
| `fix/native-driver-selection-headless` | `98468165` | five commits on the upstream driver-selection head `d103ce7a`, which is itself on `main` `ce2897c4`. This is what the round measures. Round 2 measured its first two. |
| `pr/automation-command-surface` | `2f21242e` | unchanged since round 2: the automation receiver, and a build stamp that puts the commit into the log. Not under test; it is the instrument. |

**Identity check, exact as in round 2.** The build stamps its own commit:

```bash
./gradlew :app:assembleGithubDebug        # prints "Building from commit: e926beb0569b"
adb shell am broadcast -f 0x00000020 -n com.andrerinas.headunitrevived/com.andrerinas.openheadunit.automation.AutomationReceiver \
  -a com.andrerinas.openheadunit.ACTION_QUERY_STATE
# the reply JSON on data= must carry "commit":"e926beb0569b"
```

Anything ending in `-dirty` means the tree had uncommitted changes when it was built. Stop and clean
it: a dirty build cannot be tied to this brief.

Unit gate, measured off-rig: **1324 / 0**. (The fix branch alone is 1295, round 2's merge was 1309.)
`:app:compileGithubDebugKotlin` succeeds off-rig at every one of the five commits, so a build failure
on the rig is a toolchain problem, not the branch.

The install blocker will bite again from a cold start: the rig's shipped app is release-signed and
`adb install -r` refuses a debug build over it. The remedy is approved in advance and worked twice:
back up `settings.xml`, `adb uninstall`, install, restore, verify byte-identical.

---

## 2. What round 2 settled, and what this round is for

Fixed and proven on hardware in round 2. **None of these needs re-proving on its own**, but each has
a run below anyway, because this round's changes touch the same code:

- the headless wake poke (5 pokes in 120 s, first at 2.20 s, zero deferral lines, against round 1's 0
  and 9)
- the dismissed-dialog accept latch (gate open 0.22 s after a Home press)
- cancel permanence (a phone accepted first time, zero refusal lines, against 20 refusals in 1.4 s)
- the P2P group teardown on Switch Phone (1 `createGroup SUCCESS` on 1 interface index, against 2
  and 2)
- "close app on disconnect" killing the switch (pid alive, selector on screen, against
  `System.exit(0)` 0.643 s in)

Three runs did not pass. Two were defects in the fix branch itself and are fixed here.

**R2 failed, and it is the same defect as R3's open question.** The unanswered-prompt deadline
measured **61.54 s** at timeout 10 and **61.625 s** at timeout 30, where 25 s and 45 s were wanted,
and R3's poke landed **54.47 s** after a dismiss that had opened the gate in 0.22 s. The deadline was
only ever read when something else called into the poke path, and on this unit the only thing that
does is a credential redelivery, about once a minute. So both sub-runs measured that cadence rather
than the setting. The fix moves the decision inside the poke loop, which now re-asks once a second
and holds instead of being cancelled outright.

**R5 failed on target selection.** The network half passed, but the phone the driver had just
switched away from reconnected on its own **1.05 s** after the newly chosen one was poked, and took
the session. Reopening the Android Auto listeners for the phone's return is what gives it the chance,
and nothing checked which phone was arriving. Two windows now do: a chosen driver is the only phone
accepted for 30 s, and the phone a switch moved away from is refused for 60 s while nobody has been
chosen. Both are bounded, so an unfinished switch heals itself.

**R7 was not a defect.** The run needed "no history" and only read back `native-preferred-device-mac`
and `last-connected-native-mac`. A completed handshake writes the phone's MAC into
`native-poke-bt-macs` when that list is empty, so R5 and R6's sessions had already put D-MOTO there,
and `Off` mode connecting to a known phone without asking is the feature working. R7 is re-run below
with that key cleared and read back. One narrow change did land: the poke list is a set the user can
put several phones in, and its first entry was being taken as the last-used driver arbitrarily. It
now counts only when it names exactly one phone, which does not change R7's own outcome.

Still unfixed on purpose: `Auto` and `Always` are the same mode, because both arms of the history
branch return true and the author's own tests pin that. Do not treat a prompt appearing with history
present as a finding.

---

## 3. The automation receiver

Unchanged from round 2, repeated here so this brief stands alone.

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
| `ACTION_LOG_MARKER --es text <label>` | Writes `AutomationMarker: <label>` at WARN. Put one before and after every step. |
| `ACTION_QUERY_STATE` | Build identity and session state in one ordered broadcast; the reply comes back on `data=`. |

`ACTION_SET_LOG_LEVEL`, `ACTION_START_LOG_CAPTURE` and `ACTION_EXPORT_LOG` are gated behind
`allow-external-configuration`. **This round does not use them.**

### Three traps, all from earlier rounds

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

**Unpairing uses the gear icon on the right of the row, not the row itself,** which tries to connect.
Re-pairing needs the phone's keyguard cleared, not just the screen woken.

### Where a tap is unavoidable

Three runs need the screen: **R5, R6 and R7**. R5 and R6 tap the Switch Phone row in the exit dialog;
R7 taps the home screen's WiFi button. All three ask for a screenshot rather than a claim.

**R5 no longer needs a second, timed tap.** Round 2 lost two attempts to the poke loop reconnecting
the previous phone within a few seconds of the switch, which is the thing this round fixes. Picking a
phone in the selector and sending `ACTION_NATIVE_AA_POKE --es extra_mac <MAC>` reach the same
function, so R5 uses the broadcast and the tap on Switch Phone is the only one it needs.

### Pre-registered as possibly INCONCLUSIVE

R4 needs a phone to open an RFCOMM connection to the head unit's Android Auto UUID after a cancel.
Cycling D-MOTO's Bluetooth produced exactly that in round 2, so it is known to work on this rig. If
it genuinely does not dial in, record INCONCLUSIVE and say which levers were tried.

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

MACs, from round 2's results: **D-MOTO `A0:46:5A:97:E4:95`**, **D-POCO `DC:B7:2E:5E:4E:59`**.

---

## 5. The lines that decide every run

All verified with `grep -F` against `e926beb0` before this brief was written, except the band in the
`createGroup SUCCESS` line, which is interpolated at runtime. Anything not on this list, treat as
context.

**Should not appear at all this round:**

```
NativeAA: Cancelling background multi-device poke loop because selection prompt is active.
AapService: Native AA user exit. Stopping active launcher.
```

The first is the method the round-2 defect lived in. It was deleted, so a single occurrence means the
wrong tree was built and R0 missed it.

**New this round, and what proves the two fixes:**

```
NativeAA: a driver switch is starting, so
waits until that phone has had its turn.
is the phone this switch moved away from, so it is not let back in yet.
```

The last two are line fragments on purpose: each begins with a MAC address. Grep the fragment.

**Already proven in round 2, used as guards:**

```
NativeAA: the driver prompt went unanswered — waking every paired phone again.
NativeAA: the driver prompt is gone without a choice — the accept gate is open again.
NativeAA: the cancelled prompt has expired — accepting
NativeAA: a phone arrived over Bluetooth — the cancelled prompt no longer stands.
NativeAA: a chosen driver's wake poke is running — not replacing it with the multi-device loop.
NativeAA: Multi-driver selection is active and awaiting user choice — deferring automated multi-device poke loop.
AapService: ACTION_NATIVE_AA_PROMPT_DISMISSED received
AapService: Native AA session ended; keeping the
```

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
NativeAA: Connection accepted from
HomeFragment: Connecting to Native-AA device:
WifiDirectManager: 5GHz createGroup SUCCESS!
WifiDirectManager: Standard createGroup SUCCESS!
WirelessServer: Incoming connection detected
AapSslContext.performHandshake | SSL handshake complete
AapService destroying
AapService: killProcessOnDestroy is true
AutomationReceiver:
AutomationMarker:
```

Count groups with the pattern that catches both spellings, and count interfaces alongside it, because
one group can log twice:

```bash
grep -c "createGroup SUCCESS" log.txt
grep -o "p2p-wlan0-[0-9]*" log.txt | sort -u
```

---

## 6. Runs

Every run uses the `send` helper from §3, `-f` flag included. Bracket each step with
`ACTION_LOG_MARKER`, and confirm `AutomationReceiver:` appears for the first command of each run
before waiting out a capture.

### R0: build gate

Build, install, unit-test on the rig. PASS needs all three: the build prints
`Building from commit: e926beb0569b` with no `-dirty`; `ACTION_QUERY_STATE` replies with the same
commit; the unit gate reads **1324 / 0**. Record the APK md5.

Any other unit count means the wrong tree was built. Stop and say so rather than running the round.

---

### R1: headless bring-up. **Regression guard.**

Round 2 passed this and the poke loop was rewritten since, so it is re-run rather than trusted.

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
`createGroup SUCCESS` to the first poke. Round 2 read 5 pokes at 2.20 s, `main` 5 at 2.264 s.

**FAIL**: any `Multi-driver selection is active` line, or a poke count of 0.

Report alongside the count: the read-back of `native-driver-selection-mode` (must be `1`), the bonded
device count from `dumpsys bluetooth_manager` (must be 2 or more), and the names in the poke lines
(both phones must appear).

---

### R2: the deadline reads the timeout setting. **The point of the round.**

Two sub-runs, same setup as R1 except MainActivity **is** launched normally, so the dialog appears,
and nobody touches it. Sub-run **a** with `native-driver-selection-timeout=10`, sub-run **b** with
`30`. Read the key back between them, and clear `native-poke-bt-macs` again between them.

Measure from `ACTION_NATIVE_AA_PROMPT_SHOWN received` to `the driver prompt went unanswered`.

**PASS**, all three:

- sub-run (a) measures about **25 s**, within a couple of seconds
- sub-run (b) measures about **45 s**, within a couple of seconds
- the two differ by about **20 s**

**FAIL**: the two delays within a few seconds of each other, whatever their absolute value. That is
round 2's result, and it means the deadline is still reading something other than the setting.

Report two more numbers, which corroborate rather than decide:

- the count of `Multi-driver selection is active` lines per sub-run. Round 2 read 3; this round
  should read **1**, because the hold now says so once per prompt rather than once per caller. More
  than one means the prompt was raised more than once, so say when.
- the seconds from the `went unanswered` line to the first `Attempting active poke to device`. It
  should be a couple of seconds, not another minute.

The deadline can only be noticed while a poke loop is running, and that loop starts on the first
credential delivery after the mode comes up. If (a) reads noticeably more than 25 s, report the
timestamp of the first `createGroup SUCCESS` in that sub-run as well: a group that came up after the
deadline had already passed explains a late line without the fix being wrong.

---

### R3: the dialog dismissed by leaving the app, and the poke that follows

Setup as R2(a), timeout `10`, `native-poke-bt-macs` cleared. Launch MainActivity, wait for
`ACTION_NATIVE_AA_PROMPT_SHOWN received`, then:

```bash
send ACTION_LOG_MARKER --es text R3-home
adb shell input keyevent KEYCODE_HOME
```

There is no countdown on the dialog in this configuration, because no target resolves without
history, so there is no timer to beat before pressing Home.

**PASS**, both halves:

- `ACTION_NATIVE_AA_PROMPT_DISMISSED received` and `the driver prompt is gone without a choice`
  within about 2 s of the marker. Round 2 read 0.22 s and this half already passed.
- the first `Attempting active poke to device` within about **5 s** of the marker. Round 2 read
  54.47 s, which is the half this round fixes.

Report the seconds from the marker to the poke either way. Two numbers are worth naming if you see
them: about 25 s means the deadline fired rather than the dismiss, and about 60 s means the poke loop
was not running at all and something else restarted it. Both are findings, not PASSes.

---

### R4: cancel stops the poke without deafening the unit. **Regression guard.**

Setup as R2(a), timeout `10`, but put D-MOTO's MAC in `auto-start-bt-macs` (both files) so the
Bluetooth arrival path is armed. This is the one run that wants that key set. Launch MainActivity,
wait for the prompt, then:

```bash
send ACTION_LOG_MARKER --es text R4-cancel
adb shell input keyevent KEYCODE_BACK
```

Confirm `cancelPoke() called` and that pokes stop. **Leave the app running** so the Android Auto
listeners stay open, wait **35 s**, then cycle D-MOTO's Bluetooth off and on.

**PASS**: the phone is accepted and a session forms, and the log names which lever did it, either
`the cancelled prompt has expired` or `a phone arrived over Bluetooth`. Report which. Round 2 read
the second, with zero refusal lines.

**FAIL**: repeated `User explicitly canceled driver selection` with no session.

**Expected and not a failure**: refusals inside the first 30 s after the cancel.

---

### R5: Switch Phone reaches the phone the driver chose. **The other point of the round.**

Setup: `native-preferred-device-mac` = D-MOTO's MAC, timeout `10`, `kill-on-disconnect=false`,
**both phones' radios on**. Round 2's brief said D-POCO could stay off, which was wrong: an
unreachable phone cannot complete the handshake this run is about.

Launch, let the countdown resolve to D-MOTO with no touch, and get a session. Then:

```bash
send ACTION_LOG_MARKER --es text R5-switch
adb shell input keyevent KEYCODE_BACK          # opens the exit dialog
# tap the Switch Phone row, then wait for ACTION_NATIVE_AA_PROMPT_SHOWN received
send ACTION_LOG_MARKER --es text R5-pick-poco
send ACTION_NATIVE_AA_POKE --es extra_mac DC:B7:2E:5E:4E:59
# watch 120 s
```

The broadcast runs the same function the selector's list runs, so this is picking D-POCO, not a
workaround for it. **The selector raised by Switch Phone carries no countdown**, so there is no timer
to beat and no need to rush the broadcast; round 2's two lost attempts were the poke loop
reconnecting D-MOTO, which is the thing this round fixes. Leave the dialog on screen and do not press
Back on it, which would cancel rather than dismiss.

**PASS**, all five:

- a second `SSL handshake complete`, and the `Connection accepted from` line before it names
  **POCO X3 NFC**. This is the criterion round 2 missed.
- `NativeAA: a driver switch is starting, so` present, naming D-MOTO's MAC.
- exactly **1** distinct `p2p-wlan0-N` across the whole capture. Round 1 recorded 2, round 2 recorded
  1. This half already passed and is guarded here.
- `AapService: Native AA session ended; keeping the` present.
- `AapService: Native AA user exit. Stopping active launcher.` **absent**.

**FAIL**: the second handshake goes to D-MOTO. That is round 2's result.

Report as well, because they are how the fix shows its working:

- whether either refusal fragment appears, `waits until that phone has had its turn.` or
  `is the phone this switch moved away from, so it is not let back in yet.`, and how many times. Zero
  is fine and only means D-MOTO never tried; it is not a failure on its own.
- the seconds from `User requested switch driver` to the second handshake, and from the R5-pick-poco
  marker to it. Round 1 took 46.9 s end to end with a group teardown in the middle. A handshake more
  than **30 s** after the marker is worth flagging: that is where the chosen phone stops being the
  only one accepted, so a late session could be either phone and the `Connection accepted from` line
  is what settles it.
- the `createGroup SUCCESS` count, for context only. Refresh churn inflates it and it is not the
  verdict.

---

### R6: Switch Phone with "close app on disconnect" on. **Regression guard.**

R5's setup plus `kill-on-disconnect=true`. Get the session, `KEYCODE_BACK`, tap Switch Phone. No
second broadcast needed; this run is only about the process surviving.

**PASS**: **no** `AapService destroying` and **no** `killProcessOnDestroy is true` within 10 s of
`User requested switch driver`; `adb shell pidof com.andrerinas.headunitrevived` still returns a pid;
and the driver selector is on screen. Screenshot it.

**FAIL**: round 1's result, the process gone 0.643 s after the switch request.

---

### R7: `Off` mode's WiFi button offers a picker

The re-run round 2's precondition voided.

Setup: `native-driver-selection-mode=0`, both MAC strings empty, **`native-poke-bt-macs` cleared in
both files and read back immediately before the tap**, both phones bonded and radios off. Then:

```bash
send ACTION_LOG_MARKER --es text R7-tap
```

and tap the home screen's WiFi button once.

**PASS**: the driver picker opens. Screenshot it.

**FAIL**: `HomeFragment: Connecting to Native-AA device:` inside a second or two of the marker, with
no picker. Round 2 got exactly that, and the read-back is what tells the two apart: **paste the
`native-poke-bt-macs` read-back into the results either way.** Without it the run cannot be scored,
because connecting straight to a known phone is correct behaviour when history exists.

`main` opens a "Select Bluetooth Device" list here, which is round 1's control.

---

### R8: one bonded phone only. **Regression guard, do not skip, run last but one.**

The majority configuration in the field. D-MOTO unpaired through the system Bluetooth settings (gear
icon, see §3), only D-POCO bonded. Mode `1`, both MAC strings empty, `native-poke-bt-macs` cleared.
Clean-run protocol per template §4: head unit launched first, D-POCO's radios restored 18 s later, up
to 90 s allowed.

**PASS**: **zero** `ACTION_NATIVE_AA_PROMPT_SHOWN received`, zero `Multi-driver selection is active`,
zero `went unanswered`, and launch to `SSL handshake complete` inside **40 s**. Round 2 read
27.79 s, round 1's `main` read 36.2 s. Report the number whatever it is: the zero counts are the
verdict, and the time is a trend.

Re-pair D-MOTO afterwards and confirm both phones bonded before closing the round.

---

### R9: a chosen driver's poke is not trampled. **Regression guard.**

Setup as R1, but after the group is up:

```bash
send ACTION_LOG_MARKER --es text R9-poke
send ACTION_NATIVE_AA_POKE --es extra_mac A0:46:5A:97:E4:95     # D-MOTO
# watch for 120 s
```

**PASS**, all three: `Attempting manual poke to motorola edge 30 neo` follows the marker, **at least
one** `a chosen driver's wake poke is running` appears while it runs, and **zero**
`Attempting active poke to device: POCO X3 NFC` in that window.

**FAIL**: the round-robin resumes and pokes D-POCO while the manual poke is still running.

**INCONCLUSIVE, not PASS**: zero round-robin pokes *and* zero `a chosen driver's wake poke is
running`. That combination means no credential redelivery happened during the manual poke, so the
guard was never reached and the quiet log proves nothing. Round 2 saw the guard line 3 times in 60 s,
so if 120 s produces none, say so.

---

## 7. Do not re-run

- **`main`'s numbers.** Round 1 measured them on this rig with these phones: 5 pokes at 2.264 s,
  36.2 s single-phone connect, the "Select Bluetooth Device" list on the `Off` button.
- **Round 2's five proven fixes**, listed in §2, as findings. Each has a guard run here because this
  round's changes touch the same code, but none of them is an open question.
- **Getting a phone's Bluetooth to *profile*-connect to the head unit.** No lever on this rig does
  it, and no run here depends on it. R4 needs an RFCOMM connection to the still-open listeners, which
  is a different thing and does work.
- **Locale and string checks, the Play Store flavour seam, and the generated protobuf.** All checked
  off-rig and clean.
- **Whether `Auto` differs from `Always`.** Unfixed on purpose, see §2.

---

## 8. Report back

Four numbers decide whether this ships:

1. **R2**: the two measured delays from `PROMPT_SHOWN` to `went unanswered`, at timeout 10 and 30.
   They must be about 25 s and about 45 s and must differ by about 20 s. Round 2 read 61.54 and
   61.625.
2. **R3**: the seconds from the Home marker to the first poke. Round 2 read 54.47 s; this round needs
   about 5 s or less.
3. **R5**: which phone the second `SSL handshake complete` belongs to, and the distinct
   `p2p-wlan0-N` count. Round 2 read D-MOTO and 1; this round needs D-POCO and 1.
4. **R7**: the `native-poke-bt-macs` read-back at tap time, and whether a picker opened.

Plus, in one line each: R1's poke count and deferral-line count, whether R4 accepted the phone again
and which lever did it, whether R6 left the app alive, R8's connect time, and R9's verdict with the
guard-line count behind it.
