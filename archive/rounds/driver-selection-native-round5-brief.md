# driver-selection-native: round 5 brief

Round 4 settled the run it existed for. **R5 passed, twice, independently**: the second SSL handshake
belonged to the switched-to phone, on its own client IP, with zero refusal churn against round 3's
152 lines. R1, R2 and R9 all held. Two runs produced no verdict and neither was the fix's fault - R4
lost its precondition to the rig's Bluetooth, and R5b's trigger does not exist in the build.

This round is about something round 4 found by watching the unit rather than by grepping its own
criteria, and about the observation rounds 3 and 4 both carried unresolved.

**The "Android Auto is starting" screen with no phone connected is real, and it is now explained.**
Round 3 traced it to `AapProjectionActivity`, found projection only launches after a handshake, and
concluded no path existed. That was the wrong screen. It is MainActivity's own full-screen
auto-connect overlay, raised by the driver countdown *before* the wake poke is even sent, on a
target chosen from stored history with no check that any phone is there. It then sits for a 30 s
watchdog. Round 4's own R5 capture shows it at 12:05:06.838, one millisecond before the
`beginAutoConnect` line.

Rig: **D-HU** (MT50) as the head unit, **D-POCO** and **D-MOTO** both bonded to it as driver phones.
Both stay bonded for every run. One run wants a third, non-phone device bonded; see R13.

Read **§3 of `TESTING-TEMPLATE.md`** and then **§3 of this brief**. §3 below supersedes the
template wherever the two disagree about how to drive the app.

**Brief revision 2.** If your copy does not carry this block, it is revision 1 and its SHAs have
moved. What changed: `ACTION_NATIVE_AA_CANCEL_POKE` was added to the automation surface, so **R5b is
back and is run this round**. Both branch tips moved with it, the candidate is now `42133e6d`, stamp
`42133e6d9e8a`, and the unit gate is 1340 / 0. **Build revision 1's APK and R0 will fail on the
stamp.** Revision 1 said R5b could not run and listed it under "do not re-run"; both of those are
withdrawn. Revision 1's §6 also miscounted its own runs as seven; there are nine with R5b back.
Nothing about any other run changed.

This file is append-only. Corrections arrive as new commits and a `git pull` fast-forwards, so no
brief you have already read changes under you.

---

## 1. Build and the testing branch

**One APK this round.** No baseline build. Every number a run is compared against was measured on
this rig with these phones in rounds 1 to 4 and is quoted inline where it is needed.

| | Branch | SHA |
|---|---|---|
| **Candidate** | `fork/testing/driver-selection-plus-automation` | `42133e6d` |

**Both branch tips were rewritten since round 4**, so a plain `git pull` on an existing local copy
will refuse to fast-forward. Reset onto the remote rather than merging.

```bash
git fetch fork
git checkout testing/driver-selection-plus-automation
git reset --hard fork/testing/driver-selection-plus-automation      # 42133e6d

# how it was made, and how to remake it if an input moves:
#   git checkout -b testing/driver-selection-plus-automation fork/fix/native-driver-selection-headless
#   git merge --no-edit fork/pr/automation-command-surface
# Clean, no conflicts, no manual resolution.
```

Its two inputs, both on `fork`:

| Input | Tip | What it is |
|---|---|---|
| `fix/native-driver-selection-headless` | `e86638aa` | **three** commits on the upstream driver-selection head `d103ce7a`, itself on `main` `ce2897c4`. The first two are what rounds 2 to 4 measured, unchanged. The third is this round's subject. |
| `pr/automation-command-surface` | `5a2ae3c7` | the automation receiver and the build stamp, plus one commit this round: `ACTION_NATIVE_AA_CANCEL_POKE` is now a relayed action. Not under test; it is the instrument. |

**Identity check, exact as in rounds 3 and 4.** The build stamps its own commit:

```bash
./gradlew :app:assembleGithubDebug        # prints "Building from commit: 42133e6d9e8a"
adb shell am broadcast -f 0x00000020 -n com.andrerinas.headunitrevived/com.andrerinas.openheadunit.automation.AutomationReceiver \
  -a com.andrerinas.openheadunit.ACTION_QUERY_STATE
# the reply JSON on data= must carry "commit":"42133e6d9e8a"
```

Anything ending in `-dirty` means the tree had uncommitted changes when it was built. Stop and clean
it: a dirty build cannot be tied to this brief.

Unit gate, measured off-rig: **1340 / 0**. (The fix branch alone is 1309, the automation branch 1290;
round 4's merge was 1333.)
`:app:compileGithubDebugKotlin` succeeds off-rig, so a build failure on the rig is a toolchain
problem, not the branch.

---

## 2. What changed, and what this round is for

Three things, one commit.

**The connect UI no longer claims a phone that is not there.** `connectToNativeDevice` used to raise
the full-screen overlay unconditionally, before sending the poke. It now asks whether the target has
a live Bluetooth link. A target that does keeps the overlay, unchanged. A target that does not gets
the small non-blocking pill instead, and the pill becomes the overlay the moment the connection
actually advances. **The poke is sent either way**, so nothing about waking a phone changed - only
what the screen claims while it happens.

**Native AA wake targets are phones.** Only a phone running Android Auto can answer, and a poke
spent on a speaker or a watch holds the hands-free slot for nothing. The all-paired wake list and the
wake-device picker in Auto Start settings now filter the same way the driver selector already did.
Both fall back to the unfiltered list if the filter would leave it empty, so an unreadable device
class cannot make the unit go mute.

**One new log line** marks the pill becoming the overlay, so the escalation is measurable rather than
a screenshot taken at the right instant.

Nothing else moved. The accept gate, the wake rounds, the switch-away window, the prompt deadline and
the poke loop are all untouched, which is why four of this round's runs are guards.

### What is still open, and is not this round

**R5b runs this round.** The round 4 erratum is fixed: `ACTION_NATIVE_AA_CANCEL_POKE` is now a
relayed automation action, so the run's own trigger exists. It is briefed in §6.

**R4 is not re-briefed.** Its precondition is a phone re-opening a Bluetooth ACL on demand, and round
4 spent about 55 minutes failing to get one. Nothing in this round's commit touches that path.

---

## 3. The automation receiver

Unchanged from rounds 2 to 4, repeated here so this brief stands alone.

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
broadcast never landed, whatever the shell printed, and the run is void rather than a FAIL.

| Command | Why it matters here |
|---|---|
| `ACTION_START_WIRELESS` | Arms the wireless mode without launching MainActivity. R1's headless case. |
| `ACTION_NATIVE_AA_POKE --es extra_mac <MAC>` | A poke at one named phone, on the **same** code path as picking that phone in the selector. R5 and R12 use it to avoid a timed tap. |
| `ACTION_NATIVE_AA_CANCEL_POKE` | The Cancel the selector's Back press sends, with no extras. **New this round**, and R5b's trigger. |
| `ACTION_LOG_MARKER --es text <label>` | Writes `AutomationMarker: <label>` at WARN. Put one before and after every step. |
| `ACTION_QUERY_STATE` | Build identity and session state in one ordered broadcast; the reply comes back on `data=`. |

`ACTION_SET_LOG_LEVEL`, `ACTION_START_LOG_CAPTURE` and `ACTION_EXPORT_LOG` are gated behind
`allow-external-configuration`. **This round does not use them.**

### The one instrument this round gets wrong if you take a shortcut

**The pill and the overlay show the same words.** Both render "Android Auto is starting…". Grepping a
`uiautomator dump` for that text therefore proves nothing at all, and would fail every new run in this
round for the wrong reason.

Tell them apart by **resource id**, never by text:

| What is on screen | Node in the dump |
|---|---|
| the small pill, top of the home screen | `com.andrerinas.headunitrevived:id/auto_connect_pill` |
| the full-screen loading overlay | `com.andrerinas.headunitrevived:id/auto_connect_loading_overlay` |

A hidden view is not in the dump, so presence of the id is the test. Take a `screencap` alongside
every dump: the two are unmistakable to the eye, and a picture settles an argument a grep cannot.

The log is the primary instrument and the dump is corroboration. `beginAutoConnect` prints its own
mode, so the decision is on the record before anything is drawn.

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

**Back up the device-protected mirror on the first touch, not after the first write.**

**Wait for a dialog by polling the capture, not by sleeping.** Round 3's R3 was discarded and re-run
because a fixed sleep let the prompt's own deadline fire before the next step landed. Poll the
capture file for `PROMPT_SHOWN received` and fire the next command within about 0.2 s of it.

### Pre-registered as possibly UNTESTABLE

**R13 needs a bonded device that is not a phone** - a Bluetooth speaker, headset or watch, bonded to
D-HU alongside the two driver phones. If no such device can be bonded on this rig, say so and record
R13 UNTESTABLE. Do **not** substitute a phone with its class spoofed, and do not unpair either driver
phone to make room. Every other run in this brief assumes exactly the two phones bonded, so if R13
runs, run it **last** and unpair the accessory before reporting.

**R11 needs a phone to arrive while the pill is up.** The lever is switching the target phone's
radios on after the countdown has already resolved. If the phone never dials in within 30 s of its
radios coming on, the pill will have timed out on its own watchdog and the escalation cannot be
observed: record INCONCLUSIVE with the radio-on timestamp, rather than as a FAIL of the escalation.

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

MACs, unchanged: **D-MOTO `A0:46:5A:97:E4:95`**, **D-POCO `DC:B7:2E:5E:4E:59`**.

---

## 5. The lines that decide every run

All verified with `grep -F` against this candidate's tree before this brief was written, except the
band in the `createGroup SUCCESS` line, which is interpolated at runtime. Anything not on this list,
treat as context.

**New this round, and what proves the fix:**

```
Auto-connect: begin (Native-AA driver:
btConnected=
Auto-connect: a phone is answering, taking the full screen.
paired device(s) are not phones and are not poked.
AapService: ACTION_NATIVE_AA_CANCEL_POKE received
NativeAA: cancelPoke() called — user explicitly canceled driver selection.
```

The first ends with `, mode=` and the mode name, and **the mode is the whole verdict of three runs**:
`mode=OVERLAY` means the unit decided a phone was reachable, `mode=PILL_THEN_OVERLAY` means it decided
one was not. The second is a fragment of the `HomeFragment: Connecting to Native-AA device:` line and
carries `true` or `false`; the two must always agree, and a run where they disagree is a finding on
its own. The third prints once when the pill is replaced by the overlay. The fourth begins with a
count and appears only when something bonded was held back from the wake list. The last two are
R5b's, and they are new to this round only in the sense that nothing could reach them before: the
code path is the one round 4's R4 exercised by pressing Back.

**Should not appear at all this round:**

```
NativeAA: Cancelling background multi-device poke loop because selection prompt is active.
AapService: Native AA user exit. Stopping active launcher.
```

The first is the method a round-2 defect lived in. It was deleted, so a single occurrence means the
wrong tree was built and R0 missed it.

**Already proven, used as guards:**

```
NativeAA: a driver switch is starting, so
waits until that phone has had its turn.
is the phone this switch moved away from, so it is not let back in yet.
NativeAA: the driver prompt went unanswered — waking every paired phone again.
NativeAA: a chosen driver's wake poke is running — not replacing it with the multi-device loop.
NativeAA: Multi-driver selection is active and awaiting user choice — deferring automated multi-device poke loop.
AapService: Native AA session ended; keeping the
connection attempts before this one.
```

The three fragments at the top each begin or end with a MAC address. Grep the fragment.

**The ordinary landmarks:**

```
HomeFragment: Connecting to Native-AA device:
AapService: ACTION_NATIVE_AA_PROMPT_SHOWN received
AapService: ACTION_NATIVE_AA_SWITCH_DEVICE received
AapProjectionActivity: User requested switch driver
NativeAA: Driver selected:
NativeAA: Attempting active poke to device
NativeAA: Attempting manual poke to
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

Count groups with the pattern that catches both spellings, and count interfaces alongside it, because
one group can log twice:

```bash
grep -c "createGroup SUCCESS" log.txt
grep -o "p2p-wlan0-[0-9]*" log.txt | sort -u
```

---

## 6. Runs

Nine runs: the build gate, five new (R10, R11, R12, R13, R5b) and three guards (R1, R2, R5). Run
them in the order written, with the one exception §3 gives R13, which is run last and needs an
accessory bonded. Every run uses the `send` helper from §3, `-f` flag included. Bracket each step
with `ACTION_LOG_MARKER`, and confirm `AutomationReceiver:` appears for the first command of each run
before waiting out a capture.

### R0: build gate

Build, install, unit-test on the rig. PASS needs all three: the build prints
`Building from commit: 42133e6d9e8a` with no `-dirty`; `ACTION_QUERY_STATE` replies with the same
commit; the unit gate reads **1340 / 0**. Record the APK md5.

Any other unit count means the wrong tree was built. Stop and say so rather than running the round.

---

### R10: no phone, no starting screen. **The point of the round.**

The reported fault, reproduced from history alone with nothing to connect to.

Setup: both phones bonded but **both radios off** (`svc bluetooth disable`, `svc wifi disable` on
each, confirm `bluetooth_on=0` on both). `native-driver-selection-mode=1`, timeout `10`,
`native-preferred-device-mac=A0:46:5A:97:E4:95` (D-MOTO), `last-connected-native-mac` empty,
`native-poke-bt-macs` cleared and read back empty in both files, `kill-on-disconnect=false`.

The preferred MAC is what makes the countdown resolve with nothing present. Do not clear it.

Launch MainActivity normally. The selector appears. **Do not touch it.** Let the countdown run out.
Then, within about 2 s of the countdown resolving, take a `uiautomator dump` and a `screencap`. Leave
the capture running a further 60 s so the watchdog window is covered.

**PASS**, all four:

1. `HomeFragment: Connecting to Native-AA device: motorola edge 30 neo (A0:46:5A:97:E4:95), btConnected=false`
2. `Auto-connect: begin (Native-AA driver: motorola edge 30 neo, mode=PILL_THEN_OVERLAY)`
3. the dump carries `auto_connect_pill` and **not** `auto_connect_loading_overlay`
4. the poke still fires: at least one `Attempting manual poke to motorola edge 30 neo`

**FAIL**: `mode=OVERLAY`, or `auto_connect_loading_overlay` in the dump, or `btConnected=true` with
both radios off, or no poke at all. The fourth is the one that matters most - suppressing the screen
must not have suppressed the wake.

Report the seconds from `PROMPT_SHOWN received` to the `Connecting to Native-AA device` line
(round 4 read 9.983 s and 9.960 s), and whether the pill was still on screen 30 s later.

---

### R11: the pill becomes the overlay when a phone does arrive. **New.**

The other half of the same fix. A deferred attempt must not stay deferred once a phone answers.

Setup exactly as R10. Run R10 first if you like and continue from it, or start fresh; either is
fine, say which. Once the pill is up and confirmed by dump, **switch D-MOTO's Bluetooth and WiFi
back on** and mark the moment:

```bash
send ACTION_LOG_MARKER --es text R11-radios-on
# ...enable D-MOTO's radios...
send ACTION_LOG_MARKER --es text R11-radios-on-done
```

Then let it run until a session forms or 180 s pass, whichever comes first. Take a `uiautomator dump`
and `screencap` as soon as a picture appears, and another about 5 s after the first
`WirelessServer: Incoming connection detected`.

**PASS**, all three:

1. `Auto-connect: a phone is answering, taking the full screen.` appears exactly once
2. a dump taken after that line carries `auto_connect_loading_overlay` and **not** `auto_connect_pill`
3. the session completes: `SSL handshake complete`, then `AapProjectionActivity` on screen

**FAIL**: the escalation line never appears while the connection state advances, or the pill is still
in the dump after it does.

**INCONCLUSIVE**, pre-registered: D-MOTO never dials in within 30 s of its radios coming on, so the
pill's own watchdog ends the attempt before anything could escalate. Record the radio-on timestamp
and the last `Auto-connect:` line, and do not read it as a FAIL.

---

### R12: a phone that is there still gets the full screen at once. **New, and the regression that matters.**

The fix must not have made the normal case worse. This is R5's setup, judged on one extra line.

Setup: **both phones' radios on**, D-MOTO profile-connected to D-HU if the rig will give you that,
`native-preferred-device-mac=A0:46:5A:97:E4:95`, timeout `10`, `native-poke-bt-macs` cleared and read
back empty. Launch MainActivity, let the countdown resolve untouched.

**PASS**: `btConnected=true` **and** `mode=OVERLAY` on the same pick, and the dump taken within 2 s of
the countdown resolving carries `auto_connect_loading_overlay`.

**FAIL**: `mode=PILL_THEN_OVERLAY` for a phone with a live Bluetooth link. That is the fix being too
eager, and it is the outcome to watch for.

**Read this before calling it.** `btConnected` reflects a live Bluetooth ACL at the instant of the
pick, not whether the phone is powered on nearby. If D-MOTO's radios are on but it has no ACL to
D-HU, `btConnected=false` is **correct** and the run is not a FAIL - it is the same situation as R10
and should be reported as such, with `dumpsys bluetooth_manager | grep -A2 <MAC>` showing whether a
`StateMachine` existed. Round 4's R4 spent 55 minutes proving this rig will not always give you an
ACL on demand. If you cannot get one, say so and report R12 INCONCLUSIVE rather than guessing.

---

### R13: the wake picker offers phones. **New. Run last. See §3 for its precondition.**

Bond one non-phone Bluetooth device to D-HU - a speaker, headset or watch - alongside both driver
phones. Then open Auto Start settings and the **wake poke device** picker (not the auto-start picker
and not the auto-disconnect one; all three look alike and only the wake one filters).

**Pick the accessory carefully, because the filter is a denylist and not every accessory is on it.**
It hides wearables, peripherals, imaging devices, and audio devices reporting as loudspeaker,
headphones, wearable headset, portable audio or hi-fi audio. It deliberately does **not** hide a
hands-free car kit, a computer, or anything whose Bluetooth class is unreadable, because a device it
cannot classify is one it must not silently drop. So a hands-free car kit still appearing in the wake
picker is **correct behaviour and not a FAIL** - it is the denylist working as specified. Use a plain
Bluetooth speaker, a pair of headphones or a watch. Record what the accessory reports:

```bash
adb shell dumpsys bluetooth_manager | grep -iA3 <accessory MAC>
```

If the only accessory available is a hands-free car kit, R13 is UNTESTABLE on this rig rather than a
failure; say which device was tried and what class it reported.

**PASS**, both:

1. the wake poke picker lists the two phones and **not** the accessory
2. the auto-start and auto-disconnect pickers **still list all three** - those two are deliberately
   unfiltered, and an accessory disappearing from them is a FAIL, not a bonus

Screenshot all three pickers. Then, with `native-poke-bt-macs` empty and
`native-poke-all-paired=true`, run a 120 s headless bring-up as in R1 and check the wake list itself:

3. `1 paired device(s) are not phones and are not poked.` appears
4. no `Attempting active poke to device` names the accessory

Unpair the accessory before reporting, and confirm both phones are still bonded.

**UNTESTABLE** if no non-phone can be bonded on this rig. Say so; do not improvise a substitute.

---

### R1: headless bring-up. **Regression guard, and directly touched.**

The all-paired wake list is now filtered, and this is the run that walks it. Both driver phones
classify as phones, so **nothing should change** - that is the whole point of the guard.

Setup: two phones bonded, both radios off, `native-driver-selection-mode=1`, timeout `10`, both MAC
strings empty, `native-poke-bt-macs` and `auto-start-bt-macs` empty in **both** files, all read back.
No third device bonded. Then, **without launching MainActivity at all**:

```bash
send ACTION_LOG_MARKER --es text R1-start
send ACTION_START_WIRELESS
# let it run 120 s
send ACTION_LOG_MARKER --es text R1-end
```

The marker is deliberately first: delivering any broadcast clears the stopped flag and creates the
app process, so the marker landing is also the proof that `ACTION_START_WIRELESS` will land.

**PASS**: at least one `Attempting active poke to device`, **zero** `Multi-driver selection is active`
lines, and **zero** `paired device(s) are not phones` lines - with only two phones bonded, nothing
should be held back. Report the poke count and the seconds from `createGroup SUCCESS` to the first
poke. Round 4 read 5 pokes at 2.225 s, round 3 5 at 2.199 s, `main` 5 at 2.264 s.

**FAIL**: a poke count of 0, or a poke count below 5 with a `not phones` line present. That would mean
the filter is rejecting a real phone, which is the one way this change can break waking a unit.

---

### R2: the deadline still reads the timeout setting. **Regression guard.**

The countdown's expiry path now carries an extra argument, so the deadline is re-measured rather than
trusted. Timeout `10` only.

Setup as R1 except MainActivity **is** launched normally, so the dialog appears, and nobody touches
it. Clear `native-poke-bt-macs` first.

Measure from `ACTION_NATIVE_AA_PROMPT_SHOWN received` to `the driver prompt went unanswered`.

**PASS**: about **25 s**, within a couple of seconds. Round 4 read 25.765 s, round 3 25.773 s.

**FAIL**: about 60 s, round 2's failure signature, which would mean the deadline is reading the
credential cadence again.

---

### R5: Switch Phone reaches the phone the driver chose. **Regression guard, and directly touched.**

Picking a phone in the selector now runs through the reachability check on its way to the poke, so
the run round 4 passed is re-run once to prove the pick path still lands where it did.

Setup and steps exactly as round 4's R5: `native-preferred-device-mac` = D-MOTO, timeout `10`,
`kill-on-disconnect=false`, both radios on, `native-poke-bt-macs` cleared and read back. Let the
countdown resolve to D-MOTO, wait for the session, then `KEYCODE_BACK` to the exit dialog,
`uiautomator dump` to locate **Switch Phone**, tap its centre, and when the selector re-appears send
`ACTION_NATIVE_AA_POKE --es extra_mac DC:B7:2E:5E:4E:59` to pick D-POCO.

**Take the selector screenshot.** Round 3 missed it; round 4 got it.

**PASS**, the same five criteria round 4 used:

1. the second `SSL handshake complete` belongs to **POCO X3 NFC**, on a client IP distinct from
   D-MOTO's
2. `a driver switch is starting, so <D-MOTO MAC> is not let straight back in.` present
3. `createGroup SUCCESS` count is **1** for the whole run
4. `AapService: Native AA session ended; keeping the` present
5. **zero** `AapService: Native AA user exit.` lines

Round 4 read `.50` for D-POCO against `.189` for D-MOTO, 1 group, and 0 refusal lines. Report the
`btConnected=` value on both picks alongside them: this run is the one place both a reachable and an
unreachable pick can appear in the same capture.

---

### R5b: the driver changes their mind. **New, and the run round 4 could not do.**

Round 4 could not run this because its trigger did not exist. It does now. The question is unchanged:
**does Cancel end the chosen driver's exclusive gate early, or does the gate run its own clock out?**

Run this immediately after R5, from a fresh setup, so the pick is the same one R5 makes.

Setup exactly as R5. Let the countdown resolve to D-MOTO, wait for the first session, `KEYCODE_BACK`,
tap **Switch Phone**, and wait for the selector. Then pick D-POCO **and cancel in the same adb
invocation**, so the gap is one round trip and not two:

```bash
send ACTION_LOG_MARKER --es text R5b-pick-then-cancel
adb shell "am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.ACTION_NATIVE_AA_POKE --es extra_mac DC:B7:2E:5E:4E:59;            am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.ACTION_NATIVE_AA_CANCEL_POKE"
```

**Both broadcasts must log `AutomationReceiver:`.** If the second one logs
`Automation: refused - unknown action`, the wrong tree was built and R0 missed it; stop and say so,
because that is exactly round 4's erratum and it would void the run.

Then watch for 180 s.

**PASS**, all four:

1. `AapService: ACTION_NATIVE_AA_CANCEL_POKE received` and
   `NativeAA: cancelPoke() called — user explicitly canceled driver selection.` both present
2. **zero** `Attempting manual poke to` and `Attempting active poke to device` lines after the cancel,
   across the whole remaining capture
3. **zero** `waits until that phone has had its turn.` lines after the cancel: the exclusive gate is
   no longer refusing on the chosen driver's behalf
4. no second `SSL handshake complete` inside the 120 s the gate would otherwise have held

**FAIL**: a poke after the cancel, or refusal lines still naming D-POCO more than a second or two
after it. That would mean Cancel stops the wake but leaves the gate standing, which is the defect
this run exists to find.

**Read this before calling the silence afterwards a bug.** The cancel handler sets `userExitedAA`,
and that gates **every** credential-triggered auto-poke until something explicitly clears it: a
genuine new `WirelessServer` connection, or `ACTION_BT_AUTO_START`. Round 4 flagged this from the
code. So a quiet unit after a successful cancel is the design, not a second defect. Expect
`AapService: userExitedAA is true. Skipping auto-poke.` on each group recreation and report the
count; it is context, not a criterion.

**Pre-registered contingency, carried over from round 4.** D-POCO answers in about 3.1 to 3.5 s on
this rig. If the session forms before the cancel lands anyway, the run cannot be done this way:
say so, report it as **R5 passing a second time** with R5's five criteria, and give the measured gap
between the pick and the cancel so a future round knows how much margin it needs.

---

## 7. Do not re-run

- **`main`'s numbers.** Round 1 measured them on this rig with these phones.
- **R3, R6, R7, R8.** Nothing in this round's commit touches the dismissed-dialog path, the
  disconnect path, the history-resolution path or the single-phone case. Rounds 3 and 4 measured them.
- **R4, cancel.** Its precondition is a phone re-opening a Bluetooth ACL on demand, which round 4
  could not obtain in 55 minutes across two attempts. Nothing this round touches that path.
- **R9, the chosen driver's poke.** Round 4 read 3 manual pokes and 6 guard lines. The wake loop is
  untouched this round; R1 already walks the list-building code that did change.
- **Whether `Auto` differs from `Always`.** Unfixed on purpose, and not a finding.

---

## 8. Report back

Three things decide whether this ships:

1. **R10**: the `mode=` on the pick, the `btConnected=` value, which resource id was in the dump, and
   whether the poke still fired.
2. **R11**: whether the escalation line appeared, and whether the overlay replaced the pill after it.
3. **R12**: that a reachable phone still reads `btConnected=true` and `mode=OVERLAY`.
4. **R5b**: whether Cancel stopped the wake **and** dropped the exclusive gate, or only the wake.

Plus, in one line each: R0's commit, md5 and unit count; R1's poke count, first-poke seconds and
`not phones` line count; R2's measured deadline; R5's second-handshake owner and group count; and
R13's three screenshots or the reason it was untestable; and R5b's measured gap between the pick and
the cancel.

**One thing to watch for that is nobody's criterion.** `btConnected` and the `mode=` on the following
line are two readings of one decision. If they ever disagree in any capture, that is a defect worth
more than any run in this brief - quote both lines with their timestamps.
