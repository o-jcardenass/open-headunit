# bring-up-status-pill-and-poke-readiness: round 1 brief

**Candidate:** `fork/feat/bring-up-status-pill-and-poke-readiness` @ `ef66abbf`
**Baseline:** none needed. See §1.
**Gate:** 1616 tests / 0 failures
**Read `TESTING-TEMPLATE.md` first.** This brief only says what is different about this round.

This brief **supersedes `bring-up-status-pill-round1-brief.md`**, which was queued at `5aeb1a75` and
never run. Its six runs are Part A below, unchanged except for the SHA and the test count. Do not
read that file; everything it said that still applies is here.

---

## 0. What this is and why it exists

Five commits on `main` `f5c2e986`. Two are the status pill and the closed-listener fix Part A grades.
The other three answer a field report, and Parts B and C grade those.

**The report.** A user whose head unit is itself a phone, paired with the car's own Bluetooth kit,
had Bluetooth auto-start trigger off the car. The app launched, the WiFi Direct group formed, and the
wake poke to the real phone never went out: the log read `Attempting active poke to device: <car>`,
then `Not poking <car> ... this head unit already holds a Bluetooth hands-free link`, then
`Attempting active poke to device: <phone>` and nothing after it. The manual poke from the device
picker went `Manual poke requested` to `Manual poke to ... finished` with no `Calling socket.connect()`
between. Connecting the phone from the system Bluetooth settings made it work at once.

**Why.** The poke's guard reads `getProfileConnectionState` for the hands-free profiles, which
answers for the whole adapter. A head unit that is a phone serves the car's kit in the **gateway
role** (`BluetoothProfile.HEADSET`), and the guard read that link as "the phone is already linked,
nothing to wake". A phone is never on the far end of a gateway-role link, so that link can never be
the poke target's own. Second defect: the wake target list is seeded once from the auto-start trigger
list, which is not filtered to phones, so the car's own MAC became a wake target and was dialled.

**The fix, three commits.** `00b0d044`: `BluetoothWakePolicy.wakeDecision` takes the client-role link,
the gateway-role link, the target's own ACL state and the driver-switch answer, and a positive
"no client link" beside a gateway link pokes whatever the target reads; the log line names why a poke
went out with a link up. `2392b2a5`: `PokeTargetPolicy.targets` treats chosen MACs that classify as
not a phone as not chosen, falls back to every paired phone, and does not rewrite the setting.
`ef66abbf`: the same report asked why Bluetooth auto-disconnect ignores a loss inside the first
minute of a session. It was a fixed 60 s floor standing in for "our own handshake and poke sockets
close a few seconds after the handoff and the OS reports the phone gone". The floor is replaced by
`BtAutoDisconnectPolicy.OWN_SOCKET_CLOSE_GRACE_MS` (15 s, provisional, **this round sets it**),
fed by `NativeAaHandshakeManager.msSinceOwnSocketClose(mac)`, stamped at every close of a socket to a
phone.

## 1. Build and baseline

```bash
git fetch fork
git checkout feat/bring-up-status-pill-and-poke-readiness   # ef66abbf, 5 commits on main f5c2e986
```

History was rewritten twice: the pill commit was amended before the earlier brief, and the three new
commits were rebased once to fold a log-string change in. `5aeb1a75` still resolves and is the pill
commit; `aae4aa83`, `296934a3`, `d874b395`, `17715306`, `2da3a443`, `d954ecc1` do not. Take
`ef66abbf` from the README row when you start.

**No baseline APK.** Part A is graded on a line `main` does not have. Part B's decisive line is also
new, and `main` might already let the poke out on this rig through a different clause (a reflective
`isConnected()` read of the target), so an A/B would not separate the two; what this round records is
**which rule fired**, which the new line names. Part B has a settings-only positive control (W2b).
Part C has no control: the old floor is gone and nothing switches the grace off.

Build with `hur-wifi-test-scripts/build_hur.sh` per §5. Identity: `ACTION_QUERY_STATE` returns
`commit`; it must read `ef66abbf`. If the automation receiver does not answer, fall back to the DEX
grep for `ConnectionStageTracker` and `WakeDecision`, both of which older builds cannot carry.

The **same APK** is installed on D-HU (Part A, D2, D3) and on D-POCO (Parts B and D1). Copy it out
of `apks/` once and install that file on both, per §7a's `build_hur.sh` note.

## 2. What is different about this round

**Two head units.** Part A and D2/D3 run on D-HU as usual. Part B and D1 run with **D-POCO as the
head unit**, D-MOTO as the driver phone, and **D-HU as the "car kit"**: the MT50's own Bluetooth stack
connects its hands-free client to a bonded phone by itself, which puts a gateway-role link on D-POCO
exactly as the reporter's car does on his. The app on D-HU stays **force-stopped** for all of Part B
and D1 so it cannot run its own bring-up or poke. This pairing has formed sessions before (recovery
rounds 3 and 4: D-POCO head unit, D-MOTO phone, `creating` to `SSL` in 7.9 s), and it carries no
video, which does not matter here; `SSL handshake complete` is the session.

**The link is the precondition, and it has to be read, never assumed.** Before every Part B run, on
D-POCO:

```bash
adb -s <D-POCO> shell dumpsys bluetooth_manager | grep -iE "HeadsetStateMachine|mCurrentDevice|11:46:03:10:33:59" | head -20
```

`HeadsetService` is the phone's AG side. The `==== StateMachine for 11:46:03:10:33:59 ====` block
must read `Connected`. If it does not, toggle **D-HU's** adapter (`svc bluetooth disable` on D-HU,
it self-reverts in ~14 s) and re-read; that lever brought both phones' profiles back within 15 s in
the driver-selection round 6. Two attempts, then the run is INCONCLUSIVE, not a FAIL. D-HU may also
grab D-MOTO's profiles once D-MOTO's radio is on; that is fine and does not change any verdict, but
record D-HU's `dumpsys bluetooth_manager | grep -iE "HeadsetClientStateMachine|A2DPSinkStateMachine"`
before and after each run so a later reader knows what D-HU held.

**The poke must actually be attempted.** §7a: a phone that has recently seen this head unit dials
back 3 to 6 s after the listeners open and the loop never reaches `pokeDevice()`. Part B uses the
"one poke round" recipe: D-MOTO Bluetooth **off** at launch, wait for `ACTIVELY LISTENING` plus
`createGroup SUCCESS`, then 8 s, then D-MOTO Bluetooth on. The decisive lines print **before**
`socket.connect()`, so a poke that then fails to connect (D-MOTO still coming up) is still a PASS on
the decision; the session forming afterwards, by poke or by dial-back, is corroboration.

**Discard-rule note for W1b.** That run's trigger *is* `MATCH! Starting AapService`, raised by D-HU's
adapter coming back. One is the run; a **second** one is the discard.

**D1 measures a number this round exists to set.** Report it even if every other run fails.

**Expected INCONCLUSIVE:** P6 (no USB host on D-HU). D3 is a **known limit**, pre-registered: it is
expected to leave the session running, and that is the recorded answer, not a FAIL.

## 3. Settings keys this round needs

All written with the app stopped, read back before launch. `auto-start-bt-macs` is authoritative in
`settings.xml` and re-copies itself to the device-protected mirror on every launch, so clear or set
it there, never in the mirror alone. Delete `native-preferred-device-mac` on both head units for the
whole round (both removal forms from §1) so no selector or pin enters the picture.

| Key | Type | D-HU (Part A, D2, D3) | D-POCO (Part B, D1) | Why |
|---|---|---|---|---|
| `wifi-connection-mode` | int | `3` (P4: `1`) | `3` | Native AA |
| `log-level` | int | `2` | `2` | INFO. Every line cited below is an unguarded `AppLog.i` except the repeats noted in §4 |
| `auto-start-bt-macs` | StringSet | empty | empty; W1b: `11:46:03:10:33:59` | the auto-start trigger, and a discard-rule source when set |
| `native-poke-bt-macs` | StringSet | leave | W1/W1b/W3: `A0:46:5A:97:E4:95`; W2/W2b: `11:46:03:10:33:59` | the wake target list |
| `native-poke-all-paired` | boolean | leave | `true` (W2b: `false`) | the fallback the seeded-list fix relies on |
| `auto-disconnect-bt-macs` | StringSet | D2/D3: `DC:B7:2E:5E:4E:59` | D1: `A0:46:5A:97:E4:95` | the watched device |
| `auto-disconnect-bt-delay-seconds` | int | `5` | `5` | the user-visible delay |

Read `native-poke-bt-macs` back **after** W2 as well: the handshake is expected to rewrite it (§5, W2).

## 4. The lines that decide every run

Verified with `grep -F` against `ef66abbf`. Match on these exact substrings, and `grep -a` always.

| Line | Means |
|---|---|
| `MainActivity: status pill step: ` | the pill changed what it shows; value is a stage name or `hidden` |
| `WifiLauncher: Initializing WiFi Mode: ` | the stack armed |
| `WifiDirectManager: startNativeAaQuietHost() requested. Removing old group if any` | the restart edge allowed to rewind the pill |
| `WifiDirectManager: Attempting createGroup for Native AA` | `CREATING_NETWORK` |
| `NativeAA: ACTIVELY LISTENING on Android Auto UUID` | listeners open |
| `NativeAA: Attempting active poke to device: ` | the loop is about to call `pokeDevice()` |
| `NativeAA: Manual poke requested for ` | the manual entry point |
| `with a hands-free link up: ` | **the decision line.** The poke went out despite a link; the text after the colon names the rule: `the link is in the gateway role` (GATEWAY_ONLY), `the phone holds no connection to this unit` (TARGET_ABSENT), `the link is the phone being switched away from` (SWITCH_TARGET) |
| `head unit already holds a Bluetooth hands-free link` | the poke stood down. INFO the first time per device, DEBUG after |
| `NativeAA: Calling socket.connect() for ` | the poke reached the radio |
| `NativeAA: Successfully poked ` | it connected |
| `chosen for Auto Start but not a phone` | a chosen wake target was dropped as not a phone. INFO once per composition, DEBUG after |
| `No wake poke phone selected, and poking all paired devices is on` | the fallback ran |
| `BluetoothHelper: driver candidates: ` | the roll-up, INFO when its composition changes; carries `- hidden: <name> (<reason>)` |
| `as the wake poke device.` | the handshake wrote the connected phone into `native-poke-bt-macs` |
| `NativeAA: BT Handshake socket closed.` | our own close of the handshake socket |
| `NativeAA: HFP socket for ` | our own close of the hands-free stand-in socket |
| `went away; ending the session in ` | auto-disconnect armed its timer |
| `not ending the session for ` | the timer fired and was refused; carries `ownCloseMs=<n or null>` |
| `stayed away; ending the session the way the Exit button does` | auto-disconnect ended the session |
| `nothing is projecting; leaving the connection stack alone` | a loss with no session up, ignored by design |
| `SSL handshake complete` | the session formed |
| `MATCH! Starting AapService` | Bluetooth auto-start fired |

The fourteen pill stage names, in rank order: `ARMED`, `USB_ATTACHED`, `PREPARING_NETWORK`,
`SEARCHING`, `USB_SWITCHING`, `CREATING_NETWORK`, `WAITING_FOR_PHONE`, `WAKING_PHONE`,
`PHONE_ANSWERED`, `SENDING_CREDENTIALS`, `PHONE_JOINING`, `CONNECTING`, `SECURING`,
`STARTING_PROJECTION`. `USB_ATTACHED` and `PREPARING_NETWORK` share a rank, as do `USB_SWITCHING`
and `CREATING_NETWORK`.

`<D-MOTO>` in a quoted line stands for D-MOTO's bonded name as this build prints it: take it from
the first `Attempting active poke to device: ` line of the run, since no earlier capture on this
branch records it, and match the MAC `A0:46:5A:97:E4:95` where the line carries one.

The manual poke, scripted (the automation receiver is on `main` since `92c63322`):

```bash
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
adb -s <hu> shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.ACTION_NATIVE_AA_POKE --es extra_mac A0:46:5A:97:E4:95
```

`-f 0x00000020` is not optional after a `force-stop`. Every command logs `AutomationReceiver: ` at
INFO; if that line is absent the broadcast never landed and the run is void.

## 5. Runs

### R0: build and unit-test gate

Build the candidate, run `./gradlew :app:testGithubDebugUnitTest`, install on **both** head units
with `adb install -r`.

**PASS** requires all of: 1616 tests, 0 failures; `ACTION_QUERY_STATE` (or the DEX grep) proves
`ef66abbf` on both units; both installed md5s match the one built.

---

### Part A: the status pill, on D-HU with D-POCO as the phone

These are the six runs of the superseded brief. **P1 is the point of Part A.**

**P1: Native AA cold bring-up to projection.** `wifi-connection-mode=3`, clean-run protocol exactly.
PASS requires all of: `status pill step:` appears 6 or more times; the first value is `ARMED`,
`PREPARING_NETWORK` or `CREATING_NETWORK` (anything later as the first value is a FAIL);
`WAITING_FOR_PHONE` and `WAKING_PHONE` both appear, in that order; the last value is
`STARTING_PROJECTION`; reading the values in order and ignoring repeats, the rank never decreases
except immediately after a `startNativeAaQuietHost() requested` line (any other decrease is a FAIL
and the most valuable thing Part A can find); `SSL handshake complete` appears. Report the wall-clock
from the first `status pill step:` to `STARTING_PROJECTION`, and the full ordered list with
timestamps.

**P2: the pill does not cover the home controls.** App launched, pill up (`ARMED` in the capture),
no phone. `adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml p2-ui.xml`. PASS
requires: the node whose `resource-id` ends `auto_connect_pill` is present and its `bounds` does not
intersect the bounds of any other `clickable="true"` node. Report both rectangles as numbers, and
say if the unit has a non-default UI scale.

**P3: a failed attempt falls back to the resting line.** As P1 but the phone stays down for the whole
run, 120 s. PASS requires: `status pill step:` 3 or more times; at least `WAITING_FOR_PHONE`
reached; a later `status pill step: ARMED` after the highest stage. Not `hidden` while armed. If
`ARMED` never returns, quote the last three `status pill step:` lines with timestamps.

**P4: a different mode produces a different sequence (the discriminator).** `wifi-connection-mode=1`,
otherwise as P1. PASS requires: `SEARCHING` appears; `WAKING_PHONE` and `SENDING_CREDENTIALS` appear
**zero** times; `status pill step:` 2 or more times. If this shows the P1 sequence, P1's PASS is
worthless.

**P5: the pill renders two lines.** During P1, while the capture shows a stage between
`CREATING_NETWORK` and `PHONE_JOINING`: `adb shell screencap -p /sdcard/s.png && adb pull /sdcard/s.png p5-pill.png`.
Attach it and describe the two lines. No PASS condition of its own; say explicitly if the second
line is absent, truncated or overlapping.

**P6: USB.** Only with a USB host cable and a phone that enters accessory mode. PASS requires
`USB_ATTACHED` or `USB_SWITCHING` at least once. Expected **INCONCLUSIVE** on this rig; do not build
a substitute.

The rank and rewind rules are settled by `ConnectionStagePolicyTest` and `ConnectionStageTrackerTest`
under R0; do not construct a rewind by hand. A session that fails to form for a reason unrelated to
the pill is a discard under §4, not a FAIL of P1.

---

### Part B: the wake poke behind a car kit's link, on D-POCO with D-MOTO as the phone

Setup common to every run: app on D-HU force-stopped; D-POCO settings per §3; D-HU's hands-free link
to D-POCO **confirmed in `dumpsys` immediately before launch** (§2); D-MOTO Bluetooth off; capture
started on D-POCO before launch.

### W1: the poke goes out with the car kit's link up. **This is the point of the round.**

`native-poke-bt-macs` = D-MOTO, `native-poke-all-paired=true`, `auto-start-bt-macs` empty. Launch
`MainActivity` on D-POCO. After `ACTIVELY LISTENING` and `createGroup SUCCESS`, wait 8 s, D-MOTO
Bluetooth on (verify with `dumpsys`, the toggle lies). 90 s.

**PASS** requires all of:

1. `with a hands-free link up: ` appears at least once **for D-MOTO**, and the text after the colon
   is `the link is in the gateway role` or `the phone holds no connection to this unit`. Report
   which. Gateway is the expected reading on a phone ROM; target-absent means D-POCO's stack threw
   on the hidden client profile and the second rule carried it. Either is the fix working.
2. `NativeAA: Calling socket.connect() for <D-MOTO>` appears at least once.
3. `head unit already holds a Bluetooth hands-free link` appears **zero** times for D-MOTO.
4. The `dumpsys` read taken before launch shows D-HU `Connected` on D-POCO's `HeadsetStateMachine`.
   Without it the run is INCONCLUSIVE whatever the lines say, because the guard was never in the
   picture.
5. `SSL handshake complete` appears. Say whether it followed a `Successfully poked` or D-MOTO's own
   dial-back (`Connection accepted from` with no `Successfully poked` before it); both are PASS.

If `Attempting active poke to device: <D-MOTO>` appears and neither line 1 nor line 3
follows it, quote the ten lines after it; that is a third state the brief did not predict.

### W1b: the same, triggered the reporter's way

`auto-start-bt-macs` = D-HU's MAC on D-POCO, everything else as W1. Launch `MainActivity` once, then
`headunit://exit` (the group is torn down; that is the reporter's "app in the background" state).
Then toggle **D-HU's** adapter off; it reverts in ~14 s and raises a real `ACL_CONNECTED` from D-HU on
D-POCO. Then the W1 sequence for D-MOTO.

**PASS** requires: exactly one `MATCH! Starting AapService`; a `createGroup SUCCESS` after it; and W1's
conditions 1 to 3. This is the run that reproduces the report end to end: the trigger device is the
car kit, and the phone still gets poked.

### W2: a seeded car-kit MAC is not poked, and the phones are

`native-poke-bt-macs` = **D-HU's MAC** (what the seeding from an auto-start list naming the car
produces), `native-poke-all-paired=true`. Otherwise as W1.

**PASS** requires all of:

1. `chosen for Auto Start but not a phone` appears, naming D-HU (`MT50` or its bonded name, with
   `11:46:03:10:33:59`), before the first poke.
2. `No wake poke phone selected, and poking all paired devices is on` appears.
3. `Attempting active poke to device: ` appears for D-MOTO and **never** for D-HU.
4. `driver candidates: ` shows D-HU under `hidden:` with its reason. If D-HU is **not** hidden there,
   stop and report D-HU's class and UUIDs from D-POCO's `dumpsys bluetooth_manager`: that is a
   classifier finding and W2 is INCONCLUSIVE for the seeding fix.
5. After `SSL handshake complete`, `as the wake poke device.` appears naming D-MOTO, and
   `native-poke-bt-macs` read back from `settings.xml` after the run is D-MOTO's MAC alone.

### W2b: positive control, the fallback off

As W2 with `native-poke-all-paired=false`. **PASS** requires: `chosen for Auto Start but not a phone`
appears; `No wake poke phone selected, so nothing is poked` appears; `Attempting active poke to device: `
appears **zero** times in 60 s. This proves the drop is real: the car kit is not poked even when it
is the only thing chosen. Then D-MOTO's own dial-back may form a session; that is not a fault.

### W3: the manual poke from the picker

From W1's state (link confirmed, D-MOTO Bluetooth on but **no session**; if a session formed, run
`headunit://disconnect` and wait for `ACTIVELY LISTENING` again), fire the scripted manual poke from
§4 at D-MOTO.

**PASS** requires: `AutomationReceiver: ` for the command; `Manual poke requested for <D-MOTO>`;
then `with a hands-free link up: ` and `Calling socket.connect() for <D-MOTO>` **between**
that line and `Manual poke to <D-MOTO> finished.` Report the wall-clock from `requested`
to `finished`: the report's failing shape was 8 to 12 ms with nothing between.

---

### Part C: auto-disconnect ignores only our own socket closes

### D1: the lag this round sets the constant from (D-POCO head unit, D-MOTO phone)

From any Part B run that formed a session, or a fresh W1 setup with `auto-disconnect-bt-macs` =
D-MOTO on D-POCO. **Touch nothing for 90 s after `SSL handshake complete`.** Two phones hold no
profile link to each other, so the ACL drops when our sockets close; that is the event under test.

**PASS** requires: `stayed away; ending the session` appears **zero** times; every `not ending the
session for` line carries an `ownCloseMs=` that is a number, not `null`, and is below 15000.

**Report, whatever the verdict:** for every `went away; ending the session in` line, the timestamp
of the nearest preceding `BT Handshake socket closed.` or `HFP socket for ... closed.` line and the
gap between them, and the `ownCloseMs` on the matching `not ending` line. The largest gap is the
number that sets `OWN_SOCKET_CLOSE_GRACE_MS`. If no `went away` line appears at all, say so and
attach D-POCO's `dumpsys bluetooth_manager` for D-MOTO's block: it means the ACL never dropped and
the run measured nothing.

### D2: a real disconnect at 20 s ends the session (D-HU head unit, D-POCO phone)

`auto-disconnect-bt-macs` = D-POCO on D-HU, delay 5. **Precondition:** D-POCO's profiles live on
D-HU before launch (`A2DPSinkStateMachine` or `HeadsetClientStateMachine` `Connected`), so the ACL
survives the handoff; use the D-HU adapter toggle if needed, and confirm again ~10 s after
`SSL handshake complete`. If the link is down at that second read, D2 is INCONCLUSIVE.

At **20 s** after `SSL handshake complete`: `adb -s <D-POCO> shell svc bluetooth disable`, verify with
`dumpsys` that it stayed off.

**PASS** requires: `went away; ending the session in 5000ms` within 5 s of the toggle; then
`stayed away; ending the session the way the Exit button does` about 5 s later; then the session
ends (`user_exit` path, no reconnect). Report the wall-clock from the toggle to `stayed away`. On
`main` this run leaves the session running until the 60 s mark; that difference is the fix.

### D3: known limit, a disconnect at 3 s

As D2 but the toggle at **3 s** after `SSL handshake complete`, while our handshake socket may still
be open. Expected: `went away`, then `not ending the session for ... ownCloseMs=<small number>`, and
the session **survives**. Record it as the known limit of the rule, with the `ownCloseMs` value. A
session that ends here is not a FAIL either; report which it did.

## 6. Do not re-run

The wake decision table (11 cases), the target-list filtering (8 cases) and the grace arithmetic
(10 cases) are JVM-tested and covered by R0. Do not construct the driver-switch case
(`the link is the phone being switched away from`) by hand; the driver-selection round 6 measured it.
Nothing about the WiFi Direct group or the WPP handshake is under test; a session that fails to form
for a reason unrelated to the poke guard is a discard under §4, not a FAIL of Part B.

## 7. Report back

Beyond §7 of the template, these decide whether this ships:

1. **W1 line 1's reason text, and W1b's single `MATCH!` followed by `Calling socket.connect()`.**
   Everything else is secondary.
2. **D1's largest gap** between our own close line and `went away`, and every `ownCloseMs` seen.
3. **W2's `driver candidates:` line**, verbatim, and the `native-poke-bt-macs` read-back after it.
4. **The ordered list of `status pill step:` values from P1, with timestamps**, and whether any rank
   decrease occurred outside a `startNativeAaQuietHost()` line.
5. The `dumpsys` reads that establish the link precondition for each Part B and Part C run, so a
   PASS cannot rest on a link that was not there.
