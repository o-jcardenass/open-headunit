# driver-selection-native: round 2 brief

Round 1 reproduced three blockers in the upstream driver-selection branch. This round measures the
fixes for them, and it is the first round on this channel that gets to use the app's own automation
receiver, which removes most of round 1's taps.

Rig: **D-HU** (MT50) as the head unit, **D-POCO** and **D-MOTO** both bonded to it as driver phones.
Both stay bonded for R1 to R7 and R9; R8 unpairs one on purpose and re-pairs afterwards.

**Brief revision 2, 2026-09-04.** If your copy does not carry this block, it is revision 1 and four
things changed after it. Re-read those four rather than the whole file: the `send` helper needs
`-f 0x00000020` or the first command of every run is dropped (§3); R5's verdict is the count of
distinct `p2p-wlan0-N`, not `createGroup SUCCESS`, which refresh churn inflates (§6); R9 records
INCONCLUSIVE rather than PASS when the guard line never appears (§6); and R5 joins R6 and R7 in the
list of runs that need a tap (§3). This file is append-only from here: revisions arrive as new
commits and a `git pull` will fast-forward, so no brief you have already read will change under you.

Read **§3 of `TESTING-TEMPLATE.md`** and then **§3 of this brief**. The template's §3 predates the
automation receiver and does not describe it; §3 below does, and it supersedes the template wherever
the two disagree about how to drive the app.

---

## 1. Build and the testing branch

**One APK this round.** There is no baseline build: round 1 measured `main` on this rig, with these
phones, on 2026-09-04, and those numbers are the control. Where a run needs a control it is a
settings change on the same APK, which §8 of the template prefers anyway.

| | Branch | SHA |
|---|---|---|
| **Candidate** | `fork/testing/driver-selection-plus-automation` | `a333306e` |

That branch is a merge, and it exists so this round has the automation surface without waiting for it
to land anywhere. It is already pushed. **You own it from here**: if either input moves, remake it
rather than cherry-picking onto it.

```bash
git fetch fork
git checkout testing/driver-selection-plus-automation      # a333306e, already on the fork

# how it was made, and how to remake it if an input moves:
#   git checkout -b testing/driver-selection-plus-automation fork/fix/native-driver-selection-headless
#   git merge --no-edit fork/pr/automation-command-surface
# The merge was clean, no conflicts, no manual resolution.
```

Its two inputs, both on `fork`:

| Input | Tip | What it is |
|---|---|---|
| `fix/native-driver-selection-headless` | `0e51be3e` | two commits on the upstream driver-selection head `d103ce7a`, which is itself on `main` `ce2897c4`. This is what the round measures. |
| `pr/automation-command-surface` | `2f21242e` | two commits on `main` `ce2897c4`: the automation receiver, and a build stamp that puts the commit into the log. Not under test; it is the instrument. |

**Identity check, and it is exact this round.** The build stamps its own commit, so there is no need
to grep the DEX for a symbol:

```bash
./gradlew :app:assembleGithubDebug        # prints "Building from commit: a333306e6882"
adb shell am broadcast -f 0x00000020 -n com.andrerinas.headunitrevived/com.andrerinas.openheadunit.automation.AutomationReceiver \
  -a com.andrerinas.openheadunit.ACTION_QUERY_STATE
# the reply JSON on data= must carry "commit":"a333306e6882"
```

Anything ending in `-dirty` means the tree had uncommitted changes when it was built. Stop and clean
it: a dirty build cannot be tied to this brief.

Unit gate, measured off-rig: **1309 / 0**. (The fix branch alone is 1280, `main` is 1259; the extra
29 are the automation branch's own policy tests.) `:app:compileGithubDebugKotlin` succeeds off-rig,
so a build failure on the rig is a toolchain problem, not the branch.

Round 1's install blocker will bite again from a cold start: the rig's shipped app is release-signed
and `adb install -r` refuses a debug build over it. If that happens, the round-1 remedy is approved
in advance: back up `settings.xml`, `adb uninstall`, install, restore, verify byte-identical.

---

## 2. What this is and why it exists

The upstream branch adds a driver-selection dialog to Native AA wireless mode: a list of bonded
phones, a countdown to a resolved target, a preferred-device setting, a Switch Phone entry in the
projection exit dialog, and a gate on the Bluetooth accept loop. Round 1 found that all of it works
when somebody is in front of the screen, and three things break when nobody is.

**The wake poke stopped on any unit with two bonded phones.** The poke loop deferred on a predicate
computed from settings and the bond list, with no dialog involved, so a unit that came up on a boot
or a Bluetooth auto-start never woke either phone. Round 1 measured **0 pokes in 120 s against
`main`'s 5**, the first of those at 2.264 s. The fix asks the screen instead: it defers only while a
prompt is genuinely up, and only for `timeout + 15 s`, after which it says so and wakes everybody
again.

**Dismissing the dialog by leaving the app latched the accept gate shut.** `onPause` dismisses the
dialog, which reaches the dismiss listener and never the cancel listener, so the prompt flag stayed
set and every incoming phone was refused. The fix adds `ACTION_NATIVE_AA_PROMPT_DISMISSED`, the
mirror of the existing `..._PROMPT_SHOWN`, sent from the dismiss listener.

**Cancel was permanent.** Round 1 pressed Back, cycled a phone's Bluetooth, and watched that phone
reach the still-open listeners and get **refused 20 times in 1.4 seconds**, with no route back except
a finger on the WiFi button. The fix scopes the refusal to one poke cycle (30 s) and clears the flag
on `start()`, `stop()`, the session-end re-arm, and Bluetooth auto-start.

Two more, both measured in round 1 and both fixed here. **Switch Phone tore the P2P group down**
(2 `createGroup SUCCESS` on two interface indices, 46.9 s end to end) because it disconnected as a
user exit; it now passes `isUserExit = false`. **"Close app on disconnect" killed the app 0.643 s
into a switch** instead of showing a selector; the switch now opts out of that.

One thing deliberately unfixed: `Auto` and `Always` are still the same mode, because both arms of the
history branch return true. The author's own unit tests pin that behaviour, so which way it should go
is their decision, not a review fix. Do not treat a prompt appearing with history present as a
finding.

---

## 3. What is different about this round: the automation receiver

The candidate carries an exported receiver that takes commands as **broadcasts**. This is new to this
channel, and it is why round 2 has fewer taps than round 1.

```bash
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
send() { adb shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit."$@"; }
```

Two things about that helper are load-bearing.

**The package and the action prefix genuinely differ** (`headunitrevived` versus `openheadunit`), and
getting it wrong does nothing at all, silently. Paste the lines, do not retype them.

**`-f 0x00000020` is `FLAG_INCLUDE_STOPPED_PACKAGES`, and without it the first command of every run
is dropped.** `am force-stop` sets the package's stopped flag, and Android does not deliver
broadcasts to a stopped package even to an explicit component. R1 is the run this would silently
void, because it is the one that never launches the activity to clear the flag.

**Every `send` must produce `AutomationReceiver: <action>` at INFO.** If that line is missing the
broadcast never landed, whatever the shell printed, and the run is void rather than a FAIL. Check it
on the first command of each run before waiting out the capture.

What this round uses:

| Command | Why it matters here |
|---|---|
| `ACTION_START_WIRELESS` | **Arms the wireless mode without launching MainActivity**, so no dialog is ever shown. This is the headless case R1 is about, and there was no way to produce it before. |
| `ACTION_NATIVE_AA_POKE --es extra_mac <MAC>` | A poke at one named phone, from a shell. Note the memory that `am broadcast` could not reach this: that was true before this branch, and is what it fixes. `extra_mac` is required here, so this cannot reproduce the MAC-less case R7 covers. |
| `ACTION_LOG_MARKER --es text <label>` | Writes `AutomationMarker: <label>` at WARN. **Put one before and after every step.** Round 1's R6 could not tell a poke caused by a tap from the loop's own 30 s cadence; a marker settles that. |
| `ACTION_QUERY_STATE` | Build identity and session state in one ordered broadcast; the reply comes back on `data=`. |

`ACTION_SET_LOG_LEVEL`, `ACTION_START_LOG_CAPTURE` and `ACTION_EXPORT_LOG` exist too but are gated
behind `allow-external-configuration`. **This round does not use them.** Set the log level in
`settings.xml` as usual and capture with `logcat` as usual; house rule 3 is unchanged, and leaving
that gate off keeps one less thing different about the rig.

### Two traps, both from round 1

**`auto-start-bt-macs` in `settings.xml` is resynced into the device-protected mirror on every
launch.** Round 1 lost two fully-run R1 attempts to this. Any run below that says "no history" needs
the key cleared in **both** places, and re-read after the launch:

```bash
adb shell run-as $PKG cat shared_prefs/settings.xml | grep -o 'auto-start-bt-macs[^/]*'
adb shell cat /data/user_de/0/$PKG/shared_prefs/settings_device_protected.xml | grep -o 'auto-start-bt-macs[^/]*'
```

**`native-poke-bt-macs` re-seeds itself from `auto-start-bt-macs` when the key is absent.** Deleting
it is not the same as clearing it. Write an explicit empty set, `<set name="native-poke-bt-macs" />`,
and read it back.

### Where a tap is unavoidable

Three runs need the screen: **R5, R6 and R7**, the same count as round 1. R5 and R6 tap the Switch
Phone row in the exit dialog and then a phone in the selector; R7 taps the home screen's WiFi button.
All three ask for a screenshot rather than a claim.

What has changed is not the count but the coverage. Round 1's recoveries were taps and are now
scripted, and two runs that could not be attempted at all then, the headless bring-up and the
dismissed dialog, are R1 and R3 here. The exit dialog itself still opens with `KEYCODE_BACK`, which
is `onBackPressedDispatcher` and not a coordinate.

### Pre-registered as possibly INCONCLUSIVE

R4 needs a phone to open an RFCOMM connection to the head unit's Android Auto UUID after a cancel.
Round 1 produced exactly that by cycling D-MOTO's Bluetooth while the app was left running, so it is
known to work on this rig. It is **not** the same thing as the profile connection round 1 could never
produce, so do not give up on it early. If it genuinely does not dial in, record INCONCLUSIVE and say
which levers were tried.

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
default, and for these that happens to be the same, but a blank element is what the read-back check
can see.

**`log-level=2` (INFO) decides every run below.** Every decisive line in §5 is `AppLog.i` or higher.
Use `0` (VERBOSE) only if a run goes wrong and needs more context, and say so in the results.

---

## 5. The lines that decide every run

All verified with `grep -F` against `a333306e` before this brief was written. Anything not on this
list, treat as context.

**The defect, on the old code. None of these should appear this round except where a run says so:**

```
NativeAA: Multi-driver selection is active and awaiting user choice — deferring automated multi-device poke loop.
NativeAA: User explicitly canceled driver selection — refusing connection from
NativeAA: Selection prompt active (target=
AapService: Native AA user exit. Stopping active launcher.
```

**The fix, saying it worked:**

```
NativeAA: the driver prompt went unanswered — waking every paired phone again.
NativeAA: the driver prompt is gone without a choice — the accept gate is open again.
NativeAA: the driver prompt has been unanswered too long — accepting
NativeAA: the cancelled prompt has expired — accepting
NativeAA: a phone arrived over Bluetooth — the cancelled prompt no longer stands.
NativeAA: a chosen driver's wake poke is running — not replacing it with the multi-device loop.
AapService: ACTION_NATIVE_AA_PROMPT_DISMISSED received
AapService: Native AA session ended; keeping the
```

**The ordinary landmarks:**

```
AapService: ACTION_NATIVE_AA_PROMPT_SHOWN received
AapService: ACTION_NATIVE_AA_SWITCH_DEVICE received
AapProjectionActivity: User requested switch driver
NativeAA: cancelPoke() called — user explicitly canceled driver selection.
NativeAA: Driver selected:
NativeAA: Attempting active poke to device
NativeAA: Attempting manual poke to
WifiDirectManager: 5GHz createGroup SUCCESS!
WifiDirectManager: Standard createGroup SUCCESS!
WirelessServer: Incoming connection detected
AapSslContext.performHandshake | SSL handshake complete
AapService destroying
AapService: killProcessOnDestroy is true
AapService: session state
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

Every run below uses the `send` helper from §3, `-f` flag included. Bracket each step with
`ACTION_LOG_MARKER` so the results can be read without guessing which line belongs to which step, and
confirm `AutomationReceiver:` appears for the first command of each run before waiting out a capture.

### R0: build gate

Build, install, unit-test on the rig. PASS needs all three: the build prints
`Building from commit: a333306e6882` with no `-dirty`; `ACTION_QUERY_STATE` replies with the same
commit; the unit gate reads **1309 / 0**. Record the APK md5.

Any other unit count means the wrong tree was built. Stop and say so rather than running the round.

---

### R1: headless, the mode comes up with nobody at the screen. **The point of the round.**

Round 1's failure, in the form the fix is aimed at.

Setup: two phones bonded, both radios off, `native-driver-selection-mode=1`, timeout `10`, both MAC
strings empty, `native-poke-bt-macs` an empty set, `auto-start-bt-macs` empty in **both** files.
Read every one back. Then, **without launching MainActivity at all**:

```bash
# capture is already running before this line, per house rule 5
send ACTION_LOG_MARKER --es text R1-start
send ACTION_START_WIRELESS
# let it run 120 s
send ACTION_LOG_MARKER --es text R1-end
```

The marker is deliberately first: delivering any broadcast clears the stopped flag and creates the
app process, so the marker landing is also the proof that `ACTION_START_WIRELESS` will land. It does
not start `AapService`, so the headless premise holds.

**PASS**: at least one `Attempting active poke to device` inside the 120 s, and **zero**
`Multi-driver selection is active` lines. Report the poke count and the seconds from
`createGroup SUCCESS` to the first poke; `main` managed 2.264 s and 5 pokes.

**FAIL**: any `Multi-driver selection is active` line at all. There is no dialog in this run, so a
deferral here means the poke is still consulting settings rather than the screen.

**What a PASS would look like if the change did nothing:** a poke also happens if the mode is `Off`,
or if fewer than two phones are bonded, or if a stale MAC gave the selector a target to resolve. So
report alongside the count: the read-back of `native-driver-selection-mode` (must be `1`), the bonded
device count from `dumpsys bluetooth_manager` (must be 2 or more), and the names in the poke lines
(both phones must appear, which is the round-robin and proves nothing was pre-targeted).

---

### R2: attended, an unanswered prompt gives up on a clock, and the clock is the setting

The other half of the same fix, and this round's positive control.

Two sub-runs, same setup as R1 except MainActivity **is** launched normally, so the dialog appears,
and nobody touches it. Sub-run **a** with `native-driver-selection-timeout=10`, sub-run **b** with
`30`. Read the key back between them.

**PASS**: in each sub-run, `Multi-driver selection is active` lines while the prompt is up (correct:
somebody might be looking), then exactly one `the driver prompt went unanswered`, then
`Attempting active poke to device`. Measure from `ACTION_NATIVE_AA_PROMPT_SHOWN received` to the
`went unanswered` line: expect about **25 s** in (a) and about **45 s** in (b), which is the timeout
plus a 15 s grace.

**FAIL**: no `went unanswered` line inside 90 s in either sub-run, or the two sub-runs measuring the
same delay. The second is the important one: equal delays mean the deadline is not reading the
setting, and a fixed hidden constant is not what was written.

---

### R3: the dialog dismissed by leaving the app

Round 1 could not test this, because it needs the phone to dial in. It does not any more: the fix is
observable head-unit-side.

Setup as R2(a), timeout `10`. Launch MainActivity, wait for
`ACTION_NATIVE_AA_PROMPT_SHOWN received`, then:

```bash
send ACTION_LOG_MARKER --es text R3-home
adb shell input keyevent KEYCODE_HOME
```

**PASS**: `ACTION_NATIVE_AA_PROMPT_DISMISSED received` and
`the driver prompt is gone without a choice` within about 2 s of the marker, and a poke follows
**well before** the 25 s deadline R2(a) measured. Report the seconds from the marker to the first
poke.

**What a PASS would look like if the change did nothing:** a poke at roughly 25 s would be the
deadline firing, not the dismiss. The number that separates them is the gap, so report it even if the
lines are all present.

---

### R4: cancel stops the poke without deafening the unit

Setup as R2(a), timeout `10`, but put D-MOTO's MAC in `auto-start-bt-macs` (both files) so the
Bluetooth arrival path is armed. This is the one run that wants that key set. Expect
`native-poke-bt-macs` to end up holding the same MAC, since it re-seeds from that key, which is fine
here and means any poke targets D-MOTO alone. Launch MainActivity, wait for the prompt, then:

```bash
send ACTION_LOG_MARKER --es text R4-cancel
adb shell input keyevent KEYCODE_BACK
```

Confirm `cancelPoke() called` and that pokes stop. **Leave the app running** so the Android Auto
RFCOMM listeners stay open, wait **35 s**, then cycle D-MOTO's Bluetooth off and on, which is what
made the phone dial in during round 1.

**PASS**: the phone is accepted and a session forms, and the log names which lever did it, either
`the cancelled prompt has expired` or `a phone arrived over Bluetooth`. Report which.

**FAIL**: repeated `User explicitly canceled driver selection` with no session, which is round 1's
result unchanged.

**Expected and not a failure**: refusals inside the first 30 s after the cancel. That window is
deliberate, so that a poke already on the wire cannot drag the user into a session they just
declined. If the phone happens to dial in early, note the time and wait for the second attempt.

---

### R5: Switch Phone keeps the network

Setup: `native-preferred-device-mac` = D-MOTO's MAC, timeout `10`, `kill-on-disconnect=false`,
D-MOTO's radios on, D-POCO bonded with radios off. Launch, let the countdown resolve with no touch,
and get a session. Then `KEYCODE_BACK` to open the exit dialog, tap **Switch Phone**, and pick
D-POCO in the selector.

**PASS**, all four:

- exactly **1** distinct `p2p-wlan0-N` across the whole capture. Round 1 recorded 2. **This is the
  verdict**, not the `createGroup SUCCESS` count: a credential refresh re-reads a live group and does
  not change the interface index, so the index is the question "was the group torn down and remade"
  asked directly.
- `AapService: Native AA session ended; keeping the` present.
- `AapService: Native AA user exit. Stopping active launcher.` **absent**.
- a second `SSL handshake complete`, to D-POCO.

Report the `createGroup SUCCESS` count alongside it, but do not fail the run on it on its own: round
1's R1 saw 3 of those in 120 s from refresh churn during a credential wait, with no second group.
Report the seconds from `User requested switch driver` to that second handshake. Round 1 took 46.9 s
with a group teardown in the middle; a shorter time is the point, the interface count is the verdict.

---

### R6: Switch Phone with "close app on disconnect" on

R5's setup plus `kill-on-disconnect=true`. Get the session, `KEYCODE_BACK`, tap Switch Phone.

**PASS**: **no** `AapService destroying` and **no** `killProcessOnDestroy is true` within 10 s of
`User requested switch driver`; `adb shell pidof com.andrerinas.headunitrevived` still returns a pid;
and the driver selector is on screen. Screenshot it.

**FAIL**: round 1's result, which was the process gone 0.643 s after the switch request.

Needs a tap on the Switch Phone row. Take the screenshot before doing anything else.

---

### R7: `Off` mode's WiFi button offers a picker

`native-driver-selection-mode=0`, both MAC strings empty, both phones bonded and radios off. Launch,
then:

```bash
send ACTION_LOG_MARKER --es text R7-tap
```

and tap the home screen's WiFi button once.

**PASS**: the driver picker opens. Screenshot it. Round 1 got a toast and nothing else here, where
`main` opened a "Select Bluetooth Device" list.

Needs a tap; there is no automation command for that button, and the automation poke requires a MAC
so it cannot stand in for the MAC-less case this run is about. The marker is what separates the tap's
effect from the poke loop's own cadence, which is what defeated round 1's version of this run.

---

### R8: one bonded phone only. **Regression guard, do not skip, run last.**

The majority configuration in the field. D-MOTO unpaired through the system Bluetooth settings, only
D-POCO bonded. Mode `1`, both MAC strings empty. Clean-run protocol per template §4: head unit
launched first, D-POCO's radios restored 18 s later, up to 90 s allowed.

**PASS**: **zero** `ACTION_NATIVE_AA_PROMPT_SHOWN received` (which is how "no dialog" is checked
without a screenshot), zero `Multi-driver selection is active`, zero `went unanswered`, and launch to
`SSL handshake complete` within 5 s of round 1's **36.3 s**.

Re-pair D-MOTO afterwards and confirm both phones bonded before closing the round.

---

### R9: a chosen driver's poke is not trampled. **Regression guard.**

New behaviour in the fix, and cheap to check. Setup as R1, but after the group is up:

```bash
send ACTION_LOG_MARKER --es text R9-poke
send ACTION_NATIVE_AA_POKE --es extra_mac A0:46:5A:97:E4:95     # D-MOTO
# watch for 60 s
```

**PASS**, both halves: `Attempting manual poke to motorola edge 30 neo` follows the marker, **and at
least one** `a chosen driver's wake poke is running` appears while it runs, **and zero**
`Attempting active poke to device: POCO X3 NFC` in that window.

**FAIL**: the round-robin resumes and pokes D-POCO while the manual poke is still running, which is
the pre-existing hole this closes.

**INCONCLUSIVE, not PASS**: zero round-robin pokes *and* zero `a chosen driver's wake poke is
running`. That combination means no credential redelivery happened during the manual poke, so the
guard was never reached and the quiet log proves nothing. Redeliveries come in bursts every 60 s or
so, so extend the watch to 120 s and try once more before recording it.

---

## 7. Do not re-run

- **`main`'s numbers.** Round 1 measured them on this rig with these phones: 5 pokes at 2.264 s,
  36.2 s single-phone connect, the "Select Bluetooth Device" list on the `Off` button. They are the
  control for this round and do not need re-measuring.
- **Getting a phone's Bluetooth to *profile*-connect to the head unit.** Three levers were tried in
  round 1 and none worked. No run here depends on it. R4 needs an RFCOMM connection to the still-open
  listeners, which is a different thing and did work.
- **Locale and string checks, the Play Store flavour seam, and the generated protobuf.** All checked
  off-rig and clean.
- **Whether `Auto` differs from `Always`.** Unfixed on purpose, see §2.

---

## 8. Report back

Four numbers decide whether this ships:

1. **R1**: the poke count in 120 s, and the count of `Multi-driver selection is active` lines. Round 1
   read 0 and 9; this round needs the first non-zero and the second exactly 0.
2. **R2**: the two measured delays from `PROMPT_SHOWN` to `went unanswered`, at timeout 10 and 30.
   They must differ by about 20 s.
3. **R5**: the `createGroup SUCCESS` count and the distinct `p2p-wlan0-N` count across the switch.
   Round 1 read 2 and 2; this round needs 1 and 1.
4. **R8**: launch to `SSL handshake complete` in seconds, against round 1's 36.3 s.

Plus, in one line each: whether R3 recovered without waiting out the deadline, whether R4 ever
accepted the phone again and which lever did it, and whether R6 left the app alive.
