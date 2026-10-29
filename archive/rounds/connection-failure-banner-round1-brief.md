# connection-failure-banner, round 1 brief: the reason a connection failed, kept and shown

**Candidate:** `fix/connection-failure-banner` @ `6f9c4158` on `fork` (`o-jcardenass/open-headunit`).
**Baseline (R1 only):** `afd8b7ca`, the branch point, which is also the current tip of the release
work already tested under the `release-test` thread.

**Three commits, and not one line of any of them has ever been compiled.** There is no Android
toolchain on the authoring machine, so R0 is a real gate and not a formality.

```bash
git fetch fork
git checkout -B fix/connection-failure-banner fork/fix/connection-failure-banner
git rev-parse HEAD          # must print 6f9c4158693d7435fc5465ced19eb09daafd8c69
git log --oneline -4
# 6f9c4158 Say why the connection failed on the screen the user is actually looking at
# 137d28ee Native AA: write down why a connection failed, so it can outlive the drive
# 5450f1e3 Native AA: stop dropping the credentials the hotspot transport resolves
# afd8b7ca Native AA: ask the unit what it can tell a phone, before the user connects
```

**History was rewritten on 2026-08-21, so fetch and reset rather than pull.**
`fix/809-native-hotspot-credentials-race` is still pushed and still exists, but it is now an
ancestor of this branch: its tip `5450f1e3` is this branch's first commit. Building that ref gets
you one third of the round and none of the banner. The rebase was verified content-preserving in
both directions (each half's diff against the combined tip is byte-identical to what it was before),
and the pre-rebase tip is tagged `banner-pre-stack` on the authoring machine only, not pushed.

---

## 1. Why this round exists

Two separate faults on one branch, sharing one file. Both were found by reading, not by a rig.

### `5450f1e3`: on the hotspot transport, the one credential delivery a run makes is dropped

From a reporter's capture, everything the app is supposed to do, inside 60 ms:

```
19:49:11.893  AapService: Native AA on the head unit hotspot ... resolving access point credentials.
19:49:11.899  NativeAA: Starting Bluetooth Handshake Servers
19:49:11.916  NativeAA: ACTIVELY LISTENING on Android Auto UUID
19:49:11.937  SoftApCredentials: SUCCESS - Providing credentials from wlan0: SSID=..., BSSID=<real>
```

and then, for the remaining 2m40s of the capture, none of these, not once:

```
AapService: Received WiFi credentials from manager    0
NativeAA: triggerPoke() delay starting (2s)           0
NativeAA: Attempting active poke to device            0
NativeAA: Connection accepted from                    0
```

`onNativeCredentials` logs at INFO unconditionally on both of its branches, so neither line printing
means the callback field was null when the delivery invoked it. `AapService.onCreate` started the
provider and wired the listener afterwards. The provider resolves on IO, and with the access point
already up it published on its first poll iteration, 44 ms later, while the main thread was still
inside `onCreate`.

**It is unrecoverable for the run rather than merely late.** A publish that returns PUBLISHED makes
the resolve loop return, so it never polls again and there is no second delivery to catch. The
handshake manager's credentials stay null, so no poke is ever scheduled and there is nothing to put
in a Type 3 response even if the phone dials in on its own. The unit holds a working access point
and an open Bluetooth listener and looks, in a log, exactly like one idling.

WiFi Direct shares the wiring order and does not lose the race, because `startNativeAaQuietHost()`
goes through the async P2P framework and takes seconds. Only the hotspot transport is affected, and
in every version that has had it.

Three changes, and R1 is designed to say which of them did the work:

- the listener block moves above the transport start, next to the manager construction it belongs
  to (every setter is a field assignment and every lambda reads its managers at invoke time, so
  this is an ordering change and nothing else);
- `aap/CredentialsHandoff.kt` latches the value when nobody is listening yet and replays it on
  registration, so the class cannot throw away the one delivery a run makes;
- `publish()` says so when nothing was listening, because until that line existed the log of a unit
  that never woke its phone was identical to a healthy one waiting for the user.

### `137d28ee` and `6f9c4158`: the app works out why a connection failed and then throws the answer away

The verdicts are already exact and already produced. What they are not is durable. They arrive
mid-drive as a log line and a toast over the projection screen, and the notification `afd8b7ca`
added alongside them is gone the moment somebody swipes it. By the time the user is in front of the
app with a keyboard, nothing anywhere says what went wrong, and the app's own main screen, which
`MainActivity` deliberately returns them to when an attempt fails, says nothing at all.

So three conditions are now recorded where they are detected and cleared where they are disproved,
as four wall-clock stamps in `settings.xml`, and the newest standing one is shown as a banner on the
main screen with a tap through to the setting that fixes it.

| Condition | Raised where | Cleared where |
|---|---|---|
| `HOTSPOT_CONFIG_UNREADABLE` | the access point is up and will not tell us its name | a successful credential publish |
| `BSSID_UNAVAILABLE` | the WiFi Direct route aborts on a masked BSSID at Type 3 time | a usable BSSID at the same check |
| `BLUETOOTH_SENT_NO_DATA` | **new**: we wrote and the phone answered nothing | any inbound message at all |

The third one is the point of the pair. A handshake where the phone opened the channel, our write
returned, and nothing ever came back had no user-visible signal whatsoever, and it is the single
most misread failure in the tracker: a stack that accepts the write and airs nothing logs every send
exactly like a healthy one. It reuses the predicate already sitting there driving the handshake
backoff rather than inventing a second one, because that predicate already excludes the aborts that
are ours rather than the radio's.

**That predicate needed a fix to be true, and the fix changes existing behaviour.** `spokeToPhone`
was set when the version request and the start request went out, but not when the credentials
themselves did, and not for a ping response. With the version exchange off by default the phone can
open the exchange itself, and then the only thing we ever send is the credentials, so the flag stayed
false through exactly the case worth recording. It is now set after every write returns, which also
means such a handshake counts against `consecutiveHandshakeFailures` where it did not before.

### The honest limit, stated first

**Only one of the three conditions can be produced on this rig, and it is not the new one.** §7a is
explicit that this unit refuses `setSoftApConfiguration()` but *can read* `getSoftApConfiguration()`,
so `HOTSPOT_CONFIG_UNREADABLE` cannot fire here. `BSSID_UNAVAILABLE` needs the six-deep fallback
chain to come up empty, which it does not on this unit, and there is no settings lever that forces
it: a placeholder written into `static-bssid` fails `SoftApBssidPolicy.isUsable`, so the override is
discarded and the chain runs anyway. `BLUETOOTH_SENT_NO_DATA` needs a unit whose Bluetooth accepts a
write and puts nothing on the air, which is a reporter ask.

So this round does not test the raise paths, and no run below is written as though it could. **What
it does test is the other three quarters:** the race in R1, the clear paths on a real session in R4,
and the banner itself in R2, R3 and R5, driven from records seeded directly into `settings.xml` the
way §1 of the template already requires every other piece of state to be set. Raising is covered by
the JVM suites in R0 and by the next reporter log.

---

## 2. What is different about this round

- **The banner runs read the record, they do not create it.** Seeding a stamp is one `settings.xml`
  write and is exactly as authoritative as the connection path writing the same value, because the
  connection path writes nothing else. This is not a substitute run standing in for an INCONCLUSIVE;
  it is the whole of what the banner half consists of.
- **R1 is the only run that needs a baseline build.** Everything else is one APK.
- **R1 needs the access point already up before the app is launched.** That is the entire race
  window. §7a warns that `start-softap` is transient on this rig and that `-b 5` is not optional;
  confirm the AP is actually up with `dumpsys wifi | grep -i SoftApInfo` immediately before the
  launch, not five minutes earlier.
- **R1's baseline arm can pass for the wrong reason**, and the brief asks for the number that tells
  the difference. If the access point is not up when the app starts, the resolve loop polls for
  seconds instead of milliseconds, the listener is wired long before it publishes, and the baseline
  arm looks fixed. Report the millisecond gap between `Native AA on the head unit hotspot` and
  `SUCCESS - Providing credentials` on **both** arms. The reporter's was 44 ms. Anything above about
  a second means the window never opened and the arm is INCONCLUSIVE, not a pass.
- **The three raise paths are expected to produce nothing all round.** That is not a failed run, it
  is R6's invariant: nothing the tester did not seed may appear in those three keys.
- §7a's `build_hur.sh` warning applies: it clears `apks/` before each build, so copy each APK into a
  round-specific folder as soon as it exists.
- No poke is load-bearing anywhere in this round. R1 reads whether a poke was *attempted*, which is
  a decision the app makes, not whether one connected, which §7a says varies between sessions here.

---

## 3. Settings keys this round needs

| Key | Type | Element | Why |
|---|---|---|---|
| `wifi-connection-mode` | int | `<int name="wifi-connection-mode" value="3" />` | Native AA, the only transport this rig has. R5 uses `2` for one run. |
| `native-ap-transport` | int | `<int name="native-ap-transport" value="1" />` | The head unit's own hotspot. **R1 and R4b only.** `0` (WiFi Direct) everywhere else, set explicitly rather than left to a leftover. |
| `log-level` | int | `<int name="log-level" value="1" />` | DEBUG. See below. |
| `connection-issue-bt-silent` | long | `<long name="connection-issue-bt-silent" value="1755800000000" />` | Seeded record, `BLUETOOTH_SENT_NO_DATA`. |
| `connection-issue-bssid` | long | `<long name="connection-issue-bssid" value="1755800000000" />` | Seeded record, `BSSID_UNAVAILABLE`. |
| `connection-issue-hotspot-config` | long | `<long name="connection-issue-hotspot-config" value="1755800000000" />` | Seeded record, `HOTSPOT_CONFIG_UNREADABLE`. |
| `connection-issue-dismissed-at` | long | `<long name="connection-issue-dismissed-at" value="0" />` | The banner's dismissal stamp. Delete the key to reset it. |

**These four are `long`, which this channel has not written before.** The element form is
`<long name="KEY" value="N" />`. §1's removal template covers it, since its first `sed` is
`<[a-z]+ name="KEY" ...>` and `long` matches. Verify with `run-as cat` before every launch as usual.

**The values are wall-clock milliseconds since the epoch, not `elapsedRealtime`.** That is
deliberate and is the one thing about the storage worth knowing: the banner renders a moment that
has to survive a reboot. `1755800000000` is a real, recent past instant and is fine as a seed.
Anything non-zero and below "now" behaves identically; `0` means the condition is not standing.

**Why DEBUG and not VERBOSE, and not INFO.** The lines this round reads are at three priorities.
The banner line, the credential delivery and the poke attempt are `AppLog.i`; the Bluetooth-silent
warning and the held-credentials warning are `AppLog.w`; `NativeAA: triggerPoke() delay starting` and
the two `ConnectionIssues:` degradation lines are `AppLog.d`. **None of the four files touched here
contains a single `LOG_VERBOSE` guard**, checked by grepping for the guard rather than the call as
§1 requires, so DEBUG carries every line in §4 and VERBOSE would only bring this unit's driver flood
closer to wrapping the ring buffer inside a run.

---

## 4. The lines that decide every run

All copied from `6f9c4158` and verified with `grep -F` against the branch. Use `grep -a`, always
(§7a).

| Grep string | Level | Means |
|---|---|---|
| `AapService: Native AA on the head unit hotspot` | i | the hotspot transport was chosen and the provider started. R1's clock starts here. |
| `SoftApCredentials: SUCCESS - Providing credentials from` | i | the provider resolved a network. R1's clock stops here. |
| `AapService: Received WiFi credentials from manager` | i | **the delivery landed.** Zero of these with a SUCCESS above is the whole defect. |
| `SoftApCredentials: the access point resolved before anything was listening for it` | w | the latch caught a delivery the reordering did not prevent. |
| `NativeAA: triggerPoke() delay starting (2s)` | d | the poke was scheduled, which only happens once credentials arrive. |
| `NativeAA: Attempting active poke to device` | i | a poke was attempted. Whether it connects is not this round's business. |
| `MainActivity: showing the connection issue banner for` | i | the banner went up, and the suffix names which condition. Prints once per condition, not once per resume. |
| `MainActivity: could not read the connection issue record` | w | the read threw and the banner degraded to nothing. Should never appear. |
| `ConnectionIssues: settings unavailable, not recording` | d | preferences were unreachable at a raise or clear. Should never appear. |
| `NativeAA: the phone connected over Bluetooth and answered nothing we sent` | w | **the new condition raised.** Expected zero all round; see R6. |
| `NativeAA: BSSID is still masked/empty` | e | the BSSID condition raised. Expected zero all round; see R6. |
| `SoftApCredentials: The access point on` | e | the hotspot-config condition raised. Expected zero all round; see R6. |
| `Handshake: SSL handshake complete` | d | a session went live. |

---

## 5. Runs

### R0 - build and unit gate

Both builds, and the test suite on the candidate.

```bash
./gradlew :app:assembleGithubDebug
./gradlew :app:testGithubDebugUnitTest
```

**PASS:** both APKs build clean, and the candidate's suite is green with these four suites present
at these counts:

| Suite | Test methods |
|---|---|
| `ConnectionIssuesTest` | 8 |
| `ConnectionIssueBannerPolicyTest` | 11 |
| `CredentialsHandoffTest` | 8 |
| `WppHandshakeSessionTest` | 27 (26 before this branch, plus one) |

Total: expect **697**. The arithmetic, so a mismatch can be attributed rather than argued about:
`afd8b7ca` reports 669 and carries 629 `@Test` annotations, this tip carries 657, and the 28 new
annotations are the four rows above. The 40-test gap between annotations and reported total is
parameterised suites expanding and is the same on both revisions.

**FAIL stops the round.** Nothing here has been compiled and a compile error is the likeliest
single outcome of this whole brief. Report the error text verbatim; a one-line fix committed on top
is welcome and has been useful twice before in this channel.

Record both md5s and confirm they differ.

### R1 - the hotspot credential handover race, A/B. **The point of the round.**

The only run with a baseline, and the only run that tests `5450f1e3`.

Setup, identically on both arms:

```bash
PKG=com.andrerinas.headunitrevived
adb shell am force-stop $PKG
# settings: wifi-connection-mode=3, native-ap-transport=1, log-level=1
# and all four connection-issue-* keys DELETED, so R6's invariant is readable afterwards
adb shell cmd wifi start-softap OHU-TEST wpa2 testtest1234 -b 5
adb shell dumpsys wifi | grep -i SoftApInfo        # confirm it is actually up, and note the band
adb logcat -c
stdbuf -oL adb logcat -v time > r1-<arm>.txt &
adb shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity
# leave it 90 s. The phone's part does not matter here; nothing below needs the phone at all.
```

**The phone is not involved in R1.** Everything measured happens between the app starting and the
credentials being delivered inside the head unit, which is why this is the one run in the round that
can be trusted not to be at the mercy of §7a's A2DP weather.

**Baseline arm (`afd8b7ca`), PASS means the defect reproduces:**

- `SUCCESS - Providing credentials from` at least 1, and
- `Received WiFi credentials from manager` exactly **0**, and
- `Attempting active poke to device` exactly **0**, and
- the gap between `Native AA on the head unit hotspot` and `SUCCESS - Providing credentials` is
  **under about a second**.

That last condition is the reachability check, and it is not optional. If the gap is seconds, the
access point was not up in time, the race window never opened, and the arm is **INCONCLUSIVE**:
restart the soft AP, confirm it with `dumpsys`, and run it again before reading anything into it.

**Candidate arm (`6f9c4158`), PASS:**

- `SUCCESS - Providing credentials from` at least 1, and
- `Received WiFi credentials from manager` at least 1, and
- `triggerPoke() delay starting (2s)` at least 1, and
- the same sub-second gap, so the two arms are comparable.

**Report the count of `the access point resolved before anything was listening for it` on the
candidate arm, whatever it is.** It distinguishes which of the two fixes did the work: **0** means
the reordering alone was enough and the latch was never needed, any positive number means the
reordering did not close the window and the latch caught it. Both are passes for connectivity. Only
one of them is the true story, and nobody knows which yet.

### R2 - the banner, from a seeded record

One APK, no phone, no radios. Five short checks on the candidate. Between each, force-stop, rewrite
the keys, verify with `run-as cat`, relaunch.

**R2a, it appears and says the right thing.** Seed `connection-issue-bssid` only. Launch
`MainActivity`. Screenshot.

**PASS:** the banner is visible below the top of the screen, its text begins "This unit could not
read its own WiFi address", and the log carries exactly one
`MainActivity: showing the connection issue banner for BSSID_UNAVAILABLE`.

**R2b, the newest wins.** Seed all three, with `connection-issue-hotspot-config` the largest value,
`connection-issue-bssid` a second smaller, `connection-issue-bt-silent` smaller still.

**PASS:** exactly **one** banner is on screen, its text begins "This unit will not tell the app its
hotspot name and password", and the log line names `HOTSPOT_CONFIG_UNREADABLE` and nothing else.
Three stacked banners, or the wrong one, is a FAIL.

**R2c, dismissal is per occurrence.** From R2b's state, tap the close button at the right of the
banner. Then, in order:

1. banner disappears immediately;
2. force-stop, read `settings.xml`: `connection-issue-dismissed-at` is now larger than all three
   raise stamps;
3. relaunch: no banner, and no new `showing the connection issue banner` line;
4. force-stop, rewrite `connection-issue-bssid` to a value **larger** than `connection-issue-dismissed-at`,
   relaunch: the banner is back, naming `BSSID_UNAVAILABLE`.

**PASS** is all four. Step 4 is the one that matters: a dismissal that survived the next failure
would make the banner useless on the second drive.

**R2d, it survives a reboot.** With a banner standing, `adb shell svc power reboot` (not
`adb reboot`, per §7a). After boot, launch `MainActivity`.

**PASS:** the same banner, naming the same condition.

**R2e, never beside a live session.** Seed a stamp, then form a normal Native AA session
(`native-ap-transport=0`, phone as usual) and, while it is up, bring the main screen forward without
tearing the session down. `KEYCODE_HOME` is the least invasive route, and §7a notes it does *not*
tear the projection surface down on this unit, which is exactly what this run wants.

**PASS:** no banner while the session is live, and no `showing the connection issue banner` line
during that window. **Pair it with the reachability number:** report the `Handshake: SSL handshake
complete` timestamp and confirm the session was still up when the screenshot was taken. A missing
banner on a screen whose session had already dropped proves nothing.

If the main screen cannot be brought forward without ending the session, this is **INCONCLUSIVE**
and the JVM test covers the rule. Say so and move on.

**Onboarding suppression is deliberately not a run here.** Reaching it means setting
`onboarding-version` back and walking the wizard, which §7a says rewrites resolution, DPI and video
codec, three variables this round would then be carrying into every later run. It is covered by
`ConnectionIssueBannerPolicyTest`.

### R3 - the tap lands on the row that fixes it, in Basic mode

The most likely thing on this branch to be quietly wrong, because it goes through a mechanism
nothing else uses. The banner does not use `extra_destination`, which opens a whole screen; it seeds
the settings search box, because search is the only thing that overrides the Basic and Advanced
filter, and `Static BSSID` is Advanced-only. A Basic-mode user sent to Settings any other way would
arrive at a list that does not contain the row the banner just named.

`SettingsFragment` opens on the Basic tab by default, so no settings write is needed to be in the
failing condition. **Do not switch to Advanced.**

For each of the three, seed only that stamp, launch `MainActivity`, tap the banner body (not the
close button), and take one screenshot of whatever Settings shows. No scrolling: the search filter
should leave a very short list.

| Seeded stamp | Search box should read | Row that must be visible |
|---|---|---|
| `connection-issue-bssid` | `Static BSSID` | Static BSSID |
| `connection-issue-hotspot-config` | `Hotspot name (manual)` | Hotspot name (manual) |
| `connection-issue-bt-silent` | `Wireless Mode` | Wireless Mode |

**PASS:** all three land on Settings with the search box pre-filled with that exact text, the named
row on screen, and the keyboard **not** open over the list.

**Then check the extra is consumed.** Rotate the device, or background and foreground Settings once.
**PASS:** the search box keeps whatever it holds and the query is not re-applied over it. A query
that re-seeds on every recreate would overwrite anything the user typed after arriving.

If a row is genuinely off the first screen, §7a's bounded-search rule applies: swipe, `uiautomator
dump`, grep for the label, at most three times, and FAIL if it is not found. Do not blind-swipe.

### R4 - the clear paths, on a real session

The half of the record that this rig *can* exercise, and the half that bounds the worst outcome:
a banner that outlives its own remedy.

**R4a, WiFi Direct.** `native-ap-transport=0`. Seed **all three** stamps. Form a normal Native AA
session and let it reach `Handshake: SSL handshake complete`. Then force-stop and read `settings.xml`.

**PASS, all three parts:**

- `connection-issue-bssid` is now `0` or absent (the handshake read a usable BSSID);
- `connection-issue-bt-silent` is now `0` or absent (the phone sent us something);
- `connection-issue-hotspot-config` is **unchanged** at its seeded value.

The third is not an oversight, it is the independence property: the WiFi Direct route says nothing
about whether the hotspot configuration is readable, and a route that cleared a condition it never
looked at would be the more serious bug of the two. Then relaunch `MainActivity` and confirm the
banner now names `HOTSPOT_CONFIG_UNREADABLE`, the only one still standing.

**R4b, the hotspot transport.** `native-ap-transport=1`, soft AP up as in R1. Seed
`connection-issue-hotspot-config` only. Let the provider resolve.

**PASS:** `connection-issue-hotspot-config` is `0` or absent, and the banner is gone on the next
launch.

R4b needs only the resolve, not a completed session, so it passes or fails within seconds of the
launch regardless of what the phone is doing.

### R5 - layout on this panel, and the absent control

**R5a, both orientations.** With a seeded stamp, screenshot the main screen in landscape and in
portrait. The banner lives in `activity_main.xml` rather than in either `fragment_home.xml` variant
precisely so that one implementation serves both.

**PASS:** in both, the banner is fully on screen, its text is not clipped, the close button is
reachable, and it does not cover the home tiles or the exit control.

**R5b, the pill.** Set `wifi-connection-mode=2` (Helper), which is what makes `wifi_direct_info`
visible, and seed a stamp.

**PASS:** the pill and the banner are both readable and do not overlap. The banner is offset 52dp
from the top for exactly this, and that number is a guess made without a device.

Note that a record raised on the Native AA path shows while the app is in Helper mode. That is
intentional: the record describes what the hardware did, and a mode change does not undo it. Say in
the results whether it reads as right or as confusing on a real screen, because that judgement can
only be made in front of one.

**R5c, absent means absent.** All four `connection-issue-*` keys deleted. Screenshot the main
screen in both orientations.

**PASS:** pixel-identical to the same screens without this branch installed, with no gap, no
padding and no stray divider where the banner would be. If a before-and-after comparison is awkward,
compare against R5a's screenshots with the banner region excluded and say what you compared.

### R6 - the round-wide invariant: nothing raises by itself

Not a run, a check to make at the end, over every capture the round produced.

```bash
grep -ac "the phone connected over Bluetooth and answered nothing we sent" *.txt
grep -ac "BSSID is still masked/empty" *.txt
grep -ac "The access point on" *.txt
```

**PASS:** all zero, on every capture, and any `connection-issue-*` key that is non-zero at the end
of the round is one the tester seeded.

This is the bound on the worst outcome in the whole branch. A false "your Bluetooth is broken"
tells a user to abandon a mode that works, and this rig completes Native AA sessions routinely, so
a single unseeded raise here is a more important result than any pass above. If one appears, keep
the whole capture and report the surrounding fifty lines.

---

## 6. Do not re-run

- **`afd8b7ca` itself.** Its first compile and its 669-test gate were done under the `release-test`
  thread's A0 on 2026-08-21 and passed. R0 builds it again only as R1's baseline APK; there is no
  need to re-verify its suite.
- **Anything about the preflight dialog or the compatibility check.** They are untouched by all
  three commits, deliberately. The dialog that says "Great news! Your device supports Native
  Wireless mode" is unchanged, including its wording, and if it behaves differently that is a
  regression worth reporting but it is not something this round is looking for.
- **Whether a poke connects.** §7a says that varies between sessions here. R1 reads only whether one
  was attempted.
- **The three raise paths.** Covered above in §1's honest limit. Do not construct a substitute.

---

## 7. Report back

Four numbers decide whether this ships:

1. **R0**: the total test count and the four suite counts, against the 697 / 8 / 11 / 8 / 27
   predicted. Any deviation, even a green one, is worth a line.
2. **R1**: the two `Received WiFi credentials from manager` counts, baseline and candidate, and the
   two millisecond gaps that prove the window was open on both arms.
3. **R1, separately**: the candidate's count of `the access point resolved before anything was
   listening for it`. Zero or non-zero changes what the commit message should say about which fix
   is load-bearing.
4. **R6**: three zeroes, or the capture.

Everything else is a verdict per §6 of the template. The parts most likely to come back with
something the authoring side got wrong are R3 (a mechanism nothing else in the app uses) and R5's
52dp offset (a number chosen without a device), and both are cheap to correct once measured.
