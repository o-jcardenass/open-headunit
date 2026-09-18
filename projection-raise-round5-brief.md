# projection-raise, round 5 brief: give the two hands-free limits a voice, and one hand on D-HP's WiFi

## What this round is

Round 4 was a clean sweep. W3r, B3r2, A1r, A2 and L4r2 all PASSed, the candidate is hardware-clear,
and the thread was marked DONE with nothing queued. This round exists for two reasons that came out
of round 4 rather than out of a failure in it.

- **Round 4's own closing note** recorded, as a live operator observation rather than a scripted
  run, that D-SAM could not win D-POCO's hands-free connection away from D-HU. The same shape came
  up inside W3r's own setup, from the other direction: D-POCO's radio had to be switched off before
  D-MOTO could take D-HU's slot.
- **Neither of those is a defect and neither is repairable here.** A phone's Audio Gateway serves
  one hands-free device and a head unit serves one hands-free link, and no Android release lets an
  app disconnect either. What *was* wrong is that the app said nothing about either state, which is
  why both had to be found by hand. The candidate now says so, and this round grades that.

The fourth run is unfinished business: the below-Lollipop WiFi rejoin, the last thing keeping the
pull request a draft. Every scripted lever for it is closed, but round 4 changed what is possible,
because L4r2 established that a physical tap on D-HP is an instrument this rig has.

**None of this is hardware-measured.** Every claim below is from source and from round 4's results.

## 1. Build and baseline

| | Branch | SHA | Commits | Gate |
|---|---|---|---|---|
| Candidate | `fix/audio-sink-and-wireless-bring-up` | `ba009519` | 5 on `main` | 2164 tests, 0 failures |
| Baseline | none this round | | | |

```bash
git fetch fork
git checkout -B projection-raise-r5 fork/fix/audio-sink-and-wireless-bring-up
./gradlew :app:testGithubDebugUnitTest   # 2164, 0 failures
```

**The branch was rebuilt again, not appended to.** The Native AA and AAP commits are new and round
4's `952a0ffc` no longer exists, so reset any local copy rather than pulling it. A gate reading 2153
is round 4's tip. The three commits below Native AA are unchanged in content.

One APK, installed on whichever unit each stage names.

## 2. The rig and the preconditions

Unchanged from round 4. Five devices, four USB ports, the head unit under test is always
USB-attached.

Three things round 4 had to discover the hard way are preconditions here, because H1 and H2 both
build a hands-free precondition and both are vulnerable to exactly what contaminated W3r:

- **`pm list packages -d | grep <pkg>` is the only reliable disabled check.** `dumpsys package`'s
  per-user `enabled=0` means the DEFAULT state, not disabled.
- **`pm disable-user` does not kill a running process.** Check `ps -A | grep <pkg>` as well, and
  `am force-stop` if anything is alive. A live process keeps the phone fully capable.
- **Clear D-HU's persistent WiFi Direct group before any run that arms one specific phone**, if a
  second paired phone is present: `wifi-direct-stable-identity=false`, clear
  `wifi-direct-group-name`, `wifi-direct-last-group-ssid`, `wifi-direct-last-group-bssid` and
  `wifi-direct-group-passphrase`, then `cmd wifip2p delete-saved-group <id>` for each saved group.
  Round 4 watched an unintended phone rejoin in ~6 s with no Bluetooth involved at all.

`native-aa-wake-damage-verdict` is **not** graded this round, but it latches by design and round 4
left D-HU's at whatever its last wake wrote. Read it before H2 and clear it to `0`, because a
latched DESTRUCTIVE verdict stands every poke down and H2 needs pokes to go out.

Restore each touched unit from a full `settings.xml` backup between runs, as round 4 did.

---

## Stage A - D-SAM as head unit, D-POCO, D-HU present

### H1. The stand-in against a phone that already has a real hands-free link

**Why.** Round 3 measured D-SAM's stand-in winning a real hands-free pair from D-POCO and satisfying
Gearhead's presence gate inside a 20 s poke hold, and that remains true when nothing else holds the
slot. Round 4's operator saw the other case: with D-POCO already connected to D-HU, D-SAM cannot
take it. That is expected and is not a defect. What was wrong is that it was **completely silent**:
only a hands-free connection that *establishes* was ever logged, so a hold that opened a socket,
spoke `AT+BRSF=0` and was never answered produced no line at all, at any log level.

**Run.** Two arms, both on D-SAM as head unit with Verbose logging and `use-aac-audio` irrelevant.

*Arm 1, the slot taken.* Bring D-POCO's hands-free up against **D-HU** first and confirm it from the
phone: `dumpsys bluetooth_manager`, `Profile: HeadsetService`, the `StateMachine for <D-HU MAC>`
block at `mCurrentState: Connected`. Leave D-HU holding it. Then arm D-SAM in Native AA mode with
`native-aa-complete-hfp-slc=true` and `native-poke-bt-macs` naming D-POCO, and let it run three poke
rounds.

*Arm 2, the control.* Disconnect D-POCO from D-HU (switching D-HU's Bluetooth radio off is the
lever round 4 used), leave everything else identical, and run three more poke rounds.

**Report.** For each arm: whether `Successfully poked` appeared, and then the two lines that matter,
quoted with timestamps:

```
NativeAA: the hands-free connection to <name> (<mac>) did not complete in 20000ms:
NativeAA: hands-free service level connection established
```

Also take the phone-side `dumpsys bluetooth_manager` during each hold and say which head unit's
state machine reads `Connected`.

**PASS.** Arm 1 prints the "did not complete" line, and its reason names that the phone **answered
nothing at all** rather than that it stopped partway. Arm 2 prints the "established" line as round 3
did, and no "did not complete" line. The point of the pair is that the new line fires on the refusal
and stays quiet on success.

If arm 1 instead prints "stopped answering after CIND_TEST" or similar, that is **not a FAIL** - it
is a different and more interesting result, meaning the phone did answer us and then stalled. Quote
it exactly and say so.

---

## Stage B - D-HU as head unit, D-MOTO chosen, D-POCO present

### H2. Driver selection past another phone's hands-free link

**Why.** With one phone holding D-HU's hands-free link and a different phone chosen as the driver,
the wake correctly goes out: the link is not the target's, so nothing of the target's is taken. What
the log never said is the consequence. D-HU's own `HeadsetClientService` reports
`mMaxHeadsetConnections: 1`, so a unit already holding a link has none left to give the phone it
just woke, and Android Auto on that phone will not start wireless setup without one. The candidate
now says that on the poke line and raises a main-screen banner for it.

**Run.** Three arms on D-HU, `wifi-connection-mode=3`, `last-connected-native-mac` naming D-MOTO,
`native-aa-wake-damage-verdict` cleared, Gearhead left **enabled** on both phones this time.

*Arm 1, the target reachable.* D-POCO connected to D-HU over hands-free and confirmed `Connected` in
`dumpsys bluetooth_manager` before arming. D-MOTO paired, Bluetooth **on**, Android Auto not
started. Arm the wireless stack and let three poke rounds run.

*Arm 2, the target's Bluetooth off.* Identical, except D-MOTO's Bluetooth is **switched off** before
arming. This arm is the point of the run: the record is written at the moment the wake is decided
rather than after a socket connects, so a phone whose radio is off must still produce it.

*Arm 3, the slot free.* Disconnect D-POCO entirely, D-MOTO's Bluetooth back on, everything else
identical, three more poke rounds.

**Report.** For each arm, quoted with timestamps:

- the poke line, `NativeAA: poking <name> (<mac>) with a hands-free link up:`, in full including
  anything after the reason;
- whether the main screen shows a banner afterwards, and its text. Leave the app on the main screen
  and photograph or `uiautomator dump` it rather than describing it;
- `adb shell run-as $PKG cat shared_prefs/*.xml | grep connection-issue-hands-free-held`, before and
  after each arm.

**PASS.** Arms 1 and 2 both carry the trailing clause about a unit serving one hands-free link at a
time, both show the banner, and both write a non-zero `connection-issue-hands-free-held`. Arm 3
carries no such clause and the stamp reads `0`.

**Also measure, as a number rather than a verdict:** in arms 1 and 2, does D-MOTO's hands-free ever
reach `Connected` against D-HU while D-POCO holds it? Read D-HU's own `dumpsys bluetooth_manager`
under `HeadsetClientService` and say what the per-device state machines read. Neither round has read
this, and it is what decides whether the ceiling is the head unit's slot or the phone's. Do not
grade it; report it.

### H3. The banner retires itself

**Why.** No setting fixes this condition, so nothing in Settings can retire its record. The poke
loop has to do it on the first pass that reads the link free, or the banner would outlive its cause
and the user would have no way to clear it.

**Run.** Straight out of H2 arm 1, with the banner standing: disconnect D-POCO from D-HU and let one
further poke round run without changing any setting.

**PASS.** The banner is gone from the main screen and `connection-issue-hands-free-held` reads `0`,
with no settings change and no app restart.

**Also check the dismissal holds.** With the banner standing again, tap its dismiss control, then
let **three** more poke rounds run, which is about 45 s. The banner must stay down. It is raised once
per occurrence rather than on every pass precisely so a dismissal is not overtaken 15 s later; a
banner that comes back by itself is a FAIL and worth quoting the stamp for.

---

## Stage C - D-HP as head unit, D-POCO, Headunit Server only

### L6. The below-Lollipop WiFi rejoin, by hand

**Why.** On releases too old for a network callback, nothing kicked discovery when WiFi came back;
`WifiJoinKickPolicy` and a broadcast receiver do now. It is the last thing on this branch that no
round has graded, and it has been UNTESTABLE every time because the radio could not be taken down:
neither `svc wifi disable` nor `settings put global wifi_on 0` works on this Android 4.2.2 build,
alone or chained, and the same is true on D-SAM.

Round 4 changed the picture twice. L4r2 established that a physical tap on D-HP works fine once a
human reaches the screen, and round 4 found that unit's radio **off** at launch and brought it up
with `svc wifi enable`, taking 25 to 30 s to actually come up. So the radio can move on this unit;
what has no script is taking it down.

**Run.** D-HP in `wifi-connection-mode=1`, Headunit Server, with D-POCO's head unit server running
on `:5277` and a session established and rendering. Capture armed to a file before anything starts,
per this rig's standing rule about host-side capture dying.

Then, by hand on the glass: open D-HP's own system Settings, switch WiFi **off**, wait for the app
to notice, switch WiFi **back on**, and wait. Poll `dumpsys wifi` rather than assuming, and allow
the full 25 to 30 s for the radio to come up.

**Report.** Quoted with timestamps: what the app logged when the radio went down, whether a
reconnect or discovery attempt is logged when it returns, and whether a session forms again without
any further touch. Say how long the radio actually took to come up, both directions.

**PASS.** The app notices the loss rather than sitting on a dead socket, and it redials by itself
when WiFi returns, with no tap beyond the two on the WiFi toggle itself.

If the WiFi toggle in D-HP's Settings turns out to be unreachable or the radio will not go down even
by hand, that is a clean UNTESTABLE and the third one for this run. Say so plainly and stop; do not
spend the round on it. Every scripted route is already documented as closed.

---

## 3. What this round cannot settle

- **Whether the escalated wake achieves a connect.** Unchanged, and out of scope here.
- **Whether a tablet can be provisioned as a wireless car.** The blocker is Gearhead's missing
  WPP-over-TCP configuration entry and the only route to one is the QR deep link, which coheres with
  a hotspot rather than a WiFi Direct group renamed on every create.
- **Whether either hands-free limit can be worked around.** It cannot, on any Android release, and
  this round does not try. `BluetoothHeadsetClient` has never been in the public SDK and its hidden
  `disconnect` is unreachable at this app's target SDK. H1 and H2 grade whether the app *says* so,
  never whether it can beat it.
- **Which device holds the slot, by name.** The app cannot read that honestly, so neither the log
  line nor the banner claims it. Do not grade them for naming a device.

## 4. Not repeated

Every round 4 run passed: W3r, B3r2, A1r, A2 and L4r2 all stand. W1r2, W2r2, N5r2, E1, N4r, B1, B4,
L1 and L5 passed in earlier rounds. L2 stays dropped, L3 is closed, B2r needs nothing further.
