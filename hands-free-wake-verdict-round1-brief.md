# Round 1 brief, a measured wake refusal that could never be re-measured

Read `TESTING-TEMPLATE.md` first. This is a new thread and cites no prior round's brief. It does
borrow one setup recipe from `projection-raise-round3-brief.md`, quoted below so that file does not
have to be read.

The point of this round is two armings in run V1. Everything else is a control or a guard.

---

## 1. Build

**Candidate:** `fix/992-hands-free-wake` @ `945e1b20` on `fork`, two commits on `main` @ `80a81099`
(`v.3.4.0`). No history was rewritten; the branch is new and has been pushed once.

```bash
git fetch fork && git checkout -B fix/992-hands-free-wake fork/fix/992-hands-free-wake
```

**Baseline:** none is built for this round. Every graded condition below is decided on the candidate
alone, by a settings key that puts the app back into the refusing state. Section 6 says for each run
what a green would mean if the change did nothing, and which number separates the two.

One optional baseline arm exists in V5 and needs no build either: it reuses
`candidate-665332d8.apk` if that file is still on the rig from the `self-mode-media-session` thread
(md5 `28455e33b31ede223e76b84fc0a016d4`). Skip V5's baseline arm if it is gone.

### R0, build gate

`run_unit_tests.sh`, then `build_hur.sh`.

- Full suite **2184** tests, green. `main` reads 2164.
- `BluetoothRadioCyclePolicyTest` **17** must exist. If the class is missing, the wrong commit is
  checked out.
- `NativeAaWakeDamagePolicyTest` **11**, up from 8.
- `HandsFreeWakeEscalationPolicyTest` **23** and `BluetoothWakePolicyTest` **26**, unchanged.

Record the APK md5 and confirm which is live per §5. **If R0 fails, stop and report.**

---

## 2. What this is

Android Auto starts a wireless session on a Bluetooth *event*, not on a connected state. A head unit
whose radio auto-connects hands-free and holds it across a session and past its end raises no further
event, so the phone never re-triggers. The app's answer is to poke the phone's hands-free record,
which forces the phone's Audio Gateway to close the link it already holds and open ours. That is the
event, and it costs the unit its own hands-free link, which no API can restore.

Because the cost is a property of the unit's own Bluetooth stack rather than something a user can
answer, the app measures it once: the first escalated wake is a probe, the device is left unpoked for
30 s, and `NativeAaWakeDamagePolicy` records whether the link came back. A unit whose link stayed down
is recorded `DESTRUCTIVE` and never escalates again.

**That measurement was a one-way door, and a field log shows what it costs.** A reporter's Android 8
head unit on released 3.4.0 produced, in one 7m45s arming: 32 `Attempting active poke`, 32
`Not poking`, **zero** `Calling socket.connect()`, zero handshake, with the WiFi Direct group up, the
identity stable, the WPP TCP server listening and the Android Auto Bluetooth listener open the whole
time. The cause printed once at each of that capture's two armings:

```
NativeAA: waking a phone over a hands-free link it holds is measured to cost this
unit its hands-free link for good, so none will go out.
```

Their own successful runs the same day are the control: on the armings where the hands-free link
happened to be **absent**, the poke went out and `Connection accepted` followed **1.2 s** later. The
poke is the only thing that connects that unit, and the verdict had disabled it permanently, with no
reset, no re-measurement and nothing on screen.

Two defects, one commit each.

**`945e1b20`, the verdict.** `verdictFrom` read only the hands-free link 30 s after the wake and never
asked whether the wake had produced a session, so a wake that paid for itself was recorded as
destructive. It now takes `wakeStartedAaSession`, and a wake the phone answered is `SAFE` whatever the
link reads. Separately, a `DESTRUCTIVE` verdict is no longer permanent: armings that stand a poke down
over a hands-free link and end with no session are counted in
`native-aa-wake-armings-without-session`, and at **5** the refusal lifts and the next wake re-measures
the unit. A settings row, "Re-measure the Bluetooth wake", shows the stored verdict and clears it.

The same commit adds `BluetoothRadioCyclePolicy`, a second lever that **this rig cannot exercise**;
see section 3.

**`72ad8bca`, an unrelated defect in the same reporter's log.** `AapProjectionActivity` declared its
audio-mode listener field as `AudioManager.OnModeChangedListener`, an API 31 interface, so the class
failed to resolve on older Android however well the calls themselves were guarded. Three
`NoClassDefFoundError` blocks in their log, on an API 27 unit, during auto-connect. The field is now
`Any?` and the real type appears only inside the existing SDK guards.

---

## 3. What is different about this round

**The second lever cannot be graded on this rig, and that is not a failure of the round.** Below
Android 13 `BluetoothAdapter.disable()`/`enable()` still work, and cycling the head unit's own radio
raises the same Bluetooth event without taking anything from the phone: the ACL drops, the unit's own
stack reconnects, and the phone sees `ACL_CONNECTED` and the hands-free connect for an address that is
connected with its profile. `BluetoothRadioCyclePolicy.cycleDecision` refuses outright above
`LAST_CYCLEABLE_SDK = 32`, because the call is a no-op from Android 13 and would strand the user's
Bluetooth for nothing.

No rig unit can reach it:

- **D-HU is Android 14.** The SDK gate refuses on its first line. Verify rather than assume, with the
  command in section 4; if D-HU has been reflashed below 13 since this was written, say so in Setup
  notes and stop before V3, because the whole round changes shape.
- **D-SAM is API 19 and D-HP is API 17**, both under the gate, but neither can produce the state the
  cycle answers. The stand-down needs this unit to hold a hands-free *client* link to the phone, and
  D-SAM advertises no Hands-Free profile of its own, which is exactly why the app registers a stand-in
  record there. D-HP runs `wifi-connection-mode=1` only and has no Native AA at all.

So the cycle is covered by **17 JVM tests** and by nothing on hardware. V3 grades the one thing this
rig *can* say about it: that it stays out of the way on Android 13 and above, and that it was
genuinely evaluated and refused rather than never reached.

**One further run is expected INCONCLUSIVE and is not graded.** The new `wakeStartedAaSession` arm
needs the stand-down to mature *and* the phone to dial back inside the 30 s probe window. On this rig
those are mutually exclusive: with Gearhead enabled D-POCO answers in 0.4 to 4 s so the 90 s
stand-down never matures, and with Gearhead disabled it cannot dial back at all. It is covered by the
JVM test `a wake that brought the phone in is never condemned, whatever the link reads`. If a dial-back
ever does land inside a probe window during V1, capture the verdict line; do not engineer one.

**The escalated wake's own reconnect fires `AutoStartReceiver` once**, as it has always done. A
`MATCH! Starting AapService` after a wake is expected and is not a fault.

**Use `headunit://exit`, never `am force-stop`, to end an arming.** The counter this round turns on is
incremented in `NativeAaHandshakeManager.stop()`, which a force-stop never reaches. `headunit://exit`
reaches it through `AapService.onDestroy` -> `wifiLauncherManager.stop(LAST)` -> `handshakeManager.stop()`,
and prints `AapService: Native AA user exit. Stopping active launcher.` on the way. Force-stop only
after that line has appeared, and only to read preferences back.

---

## 4. Settings keys this round needs

Written into `shared_prefs/settings.xml` with the app stopped, per §1. Element types per §1's
"Element types": the three new keys are `<int>`, and the two sets are `<set>` of `<string>`.

| Key | Type | V1 | V2 | Meaning |
|---|---|---|---|---|
| `native-aa-wake-damage-verdict` | int | `2` | `2` | 0 unmeasured, 1 safe, 2 the refusing state |
| `native-aa-wake-armings-without-session` | int | `4` | `0` | armings that stood a poke down and got nowhere |
| `native-aa-radio-cycle-verdict` | int | `0` | `0` | leave unmeasured; the SDK gate refuses before it is read |
| `wifi-connection-mode` | int | `3` | `3` | Native AA |
| `connection-modes` | string-set | must contain `wifi` | same | 3.4.0 refuses to arm the wireless stack without it |
| `native-poke-bt-macs` | string-set | D-POCO's MAC only | same | one target, so the driver loop cannot pick another phone |
| `native-poke-all-paired` | bool | `false` | same | with the list set, leave this off |
| `last-connected-native-mac` | string | D-POCO's MAC | same | the pairing must have run Android Auto here or nothing escalates |
| `log-level` | - | VERBOSE | VERBOSE | the refusal repeats at DEBUG after its first INFO line |

```xml
<int name="native-aa-wake-damage-verdict" value="2" />
<int name="native-aa-wake-armings-without-session" value="4" />
<int name="native-aa-radio-cycle-verdict" value="0" />
<boolean name="native-poke-all-paired" value="false" />
<set name="connection-modes">
    <string>wifi</string>
</set>
<set name="native-poke-bt-macs">
    <string>AA:BB:CC:DD:EE:FF</string>
</set>
<string name="last-connected-native-mac">AA:BB:CC:DD:EE:FF</string>
```

`AA:BB:CC:DD:EE:FF` stands for D-POCO's Bluetooth address in all three places; read it off the unit
rather than off this brief.

**`connection-modes` is not optional.** Since 3.4.0 `WirelessSelectionPolicy.refusesBringUp` stops
the wireless stack arming at all when the user never chose a wireless connection, so without `wifi`
in that set the app launches and nothing happens: no group, no listeners, no poke loop, and every run
below reads as a silent FAIL that is really a setup error. If the first arming produces no
`ACTIVELY LISTENING` line, this is why.

**The status pill's X holds the stack down until a bring-up is user-requested.** If a previous thread
left it cancelled, the same silence results. The WiFi button on the home screen clears it; so does a
fresh install.

Reading the counter back between armings, with the app stopped:

```bash
PKG=com.andrerinas.headunitrevived
adb shell run-as $PKG cat shared_prefs/settings.xml | grep -F "native-aa-wake-armings-without-session"
```

Verify D-HU's release before V3, and put the answer in Setup notes:

```bash
adb -s 27870808938846 shell getprop ro.build.version.sdk    # expected 34
```

**Gearhead on D-POCO, for every V1 and V2 arming.** The stand-down only matures if the phone does not
answer on its own, so hold it away exactly as `projection-raise` round 3 did, and confirm the
processes are dead as well, because `pm disable-user` does not kill one that is already running:

```bash
adb -s 4f4027e9 shell pm disable-user --user 0 com.google.android.projection.gearhead
adb -s 4f4027e9 shell am force-stop com.google.android.projection.gearhead
adb -s 4f4027e9 shell pidof com.google.android.projection.gearhead    # must print nothing
```

Re-enable it at the end of the round.

D-POCO's hands-free link to D-HU must be **up** at every arming, or the stand-down never happens and
every run below is untestable. Confirm before each arming:

```bash
adb -s 4f4027e9 shell dumpsys bluetooth_manager | grep -A3 "Profile: HeadsetService"
```

---

## 5. The lines that decide every run

All verified with `grep -F` against `945e1b20`. Match on the fragment given, not on a whole line.

| # | Fragment | Where it comes from |
|---|---|---|
| L1 | `NativeAA: waking a phone over a hands-free link it holds is ` | once per arming, names the stored verdict |
| L2 | `measured to cost this unit its hands-free link for good, so none will go out ` | L1's refusing tail, now followed by the count |
| L3 | `not yet measured on this unit, so the first one is the measurement` | L1's unmeasured tail |
| L4 | `NativeAA: Not poking ` | one refusal, INFO the first time then DEBUG |
| L5 | `NativeAA: Calling socket.connect() for ` | **the poke actually leaving.** The number that decides V1 |
| L6 | `NativeAA: Successfully poked ` | the phone answered the socket |
| L7 | `despite the hands-free ` | the escalated wake going out |
| L8 | `so the hands-free link this unit ` | the wake's 30 s give-back holding the loop off |
| L9 | `NativeAA: the wake either brought the phone in or the hands-free link ` | verdict written as SAFE |
| L10 | `NativeAA: the hands-free link is still down ` | verdict written as DESTRUCTIVE |
| L11 | `NativeAA: cycling this unit's Bluetooth to wake ` | the radio cycle. **Must never appear on D-HU** |
| L12 | `after cycling this unit's ` | the cycle's own quiet window |
| L13 | `AapService: Native AA user exit. Stopping active launcher.` | the teardown that increments the counter |

L2 now carries the count, so a single line answers most of V1 and V2:

```
... so none will go out until 5 armings have waited it out (4 so far) or
"Re-measure the Bluetooth wake" is used.
```

---

## 6. Runs

Capture per §2, `stdbuf -oL`, started before every launch. One capture file per arming, named so the
two V1 armings cannot be confused.

### V1, a latched refusal lifts once enough armings have got nowhere. The point of the round

Set the keys as the V1 column of section 4, Gearhead held away, hands-free link up.

**Arming A.** Launch, leave it **120 s**, then `headunit://exit` and wait for L13. Then force-stop
and read the counter. No escalation is possible at this counter value however long it runs, so extra
time here costs nothing.

- L1/L2 present once, and L2 reads `(4 so far)`.
- L4 at least four times.
- **L5 exactly 0 times.** L7 absent.
- Counter reads **5** afterwards.

**Arming B.** Relaunch. Leave it **180 s**, then `headunit://exit`.

- L1/L2 present once, and L2 reads `(5 so far)`.
- L4 several times, then **L7 once**, about 90 s after the **first L4**, which is where the
  stand-down is stamped, not after the arming line.
- **L5 at least 1.** Report its timestamp against the arming line.
- L8 present, and **L5 does not appear again inside the 30 s after L7**.
- L9 or L10 written about 30 s after L7, and `native-aa-wake-damage-verdict` afterwards is `1` or
  `2` accordingly. Either value is a PASS; it is a measurement of D-HU, not of this change.

**PASS:** arming A has `L5 = 0` and leaves the counter at 5, and arming B has `L5 >= 1` with exactly
one L7.

**FAIL:** arming B still has `L5 = 0`, or arming A has `L5 >= 1`, or the counter does not move.

**INCONCLUSIVE:** the stand-down never happened at all, that is L4 = 0 in either arming. That means
D-HU was not holding the hands-free link and the premise was absent; say so and give the
`dumpsys bluetooth_manager` output.

**If the change did nothing**, arming B would look exactly like arming A: refusals and no poke. The
pair of `L5` counts, `0` then `>= 1`, is the whole result. Report both numbers explicitly even
though they are implied by the verdict.

### V2, the counter moves on its own, and says so

Keys as the V2 column, so the counter starts at **0**. One arming, 120 s, then `headunit://exit`.

- L2 reads `(0 so far)`.
- L4 at least four times, **L5 = 0**, L7 absent.
- Counter reads **1** afterwards.

**PASS:** all four. **FAIL:** the counter stays at 0 with L4 present, or L2 reads a different count.

This run exists because V1 starts the counter at 4 by hand, which would pass even if nothing ever
incremented it. Pair the two.

### V3, the radio cycle stays out of the way on Android 13 and above

No separate setup. Over **all three armings of V1 and V2 together**:

- **L11 = 0 and L12 = 0.**
- Paired measurement, so the zero is not vacuous: **L7 >= 1**, from V1 arming B. That proves the
  stand-down path was reached and the cycle was evaluated and refused on the SDK gate, rather than the
  code never running at all.

**PASS:** L11 = 0 with L7 >= 1. **FAIL:** L11 >= 1 on an Android 14 unit. **INCONCLUSIVE:** L11 = 0
with L7 = 0, which says nothing.

Report D-HU's `ro.build.version.sdk` alongside.

### V4, the re-measure row, optional and not a verdict

Not graded, and no verdict depends on it. With `native-aa-wake-damage-verdict=2`, deep-link the
settings screen:

```bash
adb shell am start -n $PKG/com.andrerinas.openheadunit.main.SettingsActivity --ei extra_destination 0
```

The row is "Re-measure the Bluetooth wake", in the Native AA section, and its value should read
**"Held back on this unit"**. It sits well down the list and §3 forbids scrolling it with adb, so if
`uiautomator dump` does not reach it, say so and move on. If it is visible, one screenshot is worth
having. Do not tap it during V1 or V2.

### V5, the API 31 listener resolves on an old Android

**D-SAM**, API 19, set up as it normally runs there per §7a: `set_pref.sh`, the host-side
`settings.xml` edit, `screen-orientation=2`, and charged from a separate supply.

Launch the app and let it reach auto-connect. Then:

```bash
grep -c "OnModeChangedListener" <capture>     # candidate: expect 0
```

**PASS:** 0 on the candidate. **FAIL:** any hit.

**This is a regression guard, not a proof, unless the baseline arm runs.** A zero here is also what a
unit that never loaded the class would produce. If `candidate-665332d8.apk` is still on the rig,
install it, repeat the same launch, and report its count: a non-zero baseline against a zero candidate
is the real result. If that APK is gone, report the candidate count alone and say the baseline was
unavailable. The defect itself is already evidenced on an API 27 unit in the field.

---

## 7. Do not re-run

- **The escalated wake's timing, give-back and verdict mechanics.** Measured end to end twice on this
  rig already, on D-HU against D-POCO and against D-MOTO, at 91.4 s and 91.2 s after arming with a
  30 s give-back and the verdict written 30 s later. V1 arming B re-observes them only because it has
  to produce the wake anyway; they are not what is being graded and a small drift in either is not a
  FAIL.
- **Whether the wake connects the phone.** Settled, and not what this round is about.
- **Anything about WiFi Direct, the group identity, credentials or the WPP exchange.** The field log
  that motivated this round has all of them correct; nothing here touches them.
- **The radio cycle on hardware.** Section 3. Do not improvise a unit for it.

---

## 8. Report back

Five numbers decide whether this ships:

1. `L5` count in V1 arming A and in arming B. Expected `0` then `>= 1`. This is the round.
2. The counter read back after V1 arming A (expect 5) and after V2 (expect 1).
3. `L7` count across the round, and the gap from the arming line to it in V1 arming B.
4. `L11` count across the round (expect 0), beside D-HU's `ro.build.version.sdk`.
5. V5's `OnModeChangedListener` count on the candidate, and on the baseline if it ran.

Plus, in Setup notes: whether D-POCO's hands-free link to D-HU was up at each arming, whether
Gearhead's processes were confirmed dead, and the verdict value left in `settings.xml` at the end of
the round. **Reset `native-aa-wake-damage-verdict` and `native-aa-wake-armings-without-session` to 0
before handing the rig to another thread**, because a latched verdict stands every later poke down
and has cost other rounds their premise.
