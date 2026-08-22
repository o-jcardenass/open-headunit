# connection-failure-banner, round 2 brief: the raise path this rig can actually produce

**Candidate:** `fix/session-lifecycle-and-diagnostics` @ `bd3d7b99` on `fork`
(`o-jcardenass/open-headunit`). This is the head of draft PR #867, which now carries every branch
this thread has been about. **No baseline.** One APK for the whole round.

```bash
git fetch fork
git checkout -B fix/session-lifecycle-and-diagnostics fork/fix/session-lifecycle-and-diagnostics
git rev-parse HEAD          # must print bd3d7b99...
git log --oneline -4
# bd3d7b99 Head unit hotspot: let the user pick the band, and show each lever where it is read
# d5faa03d Native AA: keep the reason a connection failed, and say it on the main screen
# 6d0665bf Native AA: stop dropping the credentials the hotspot transport resolves
# afd8b7ca Native AA: ask the unit what it can tell a phone, before the user connects
```

**Fetch and reset rather than pull, and expect the old branches to be gone.** An earlier draft of
this file said `feat/hotspot-band-control` @ `1a30045c`. That ref and the two beside it
(`fix/809-native-hotspot-credentials-race`, `fix/connection-failure-banner`) were deleted on
2026-08-21, locally and on `fork`, because every line of them is in the branch above. If a checkout
on this rig still sits on one of them, `git fetch --prune fork` and check out the candidate; a
`git pull` there will fail rather than move you. The only content difference between `1a30045c` and
this tip was three `CHANGELOG.md` lines, stripped because that file is the repo owner's and not
ours.

Two rewrites happened on 2026-08-21, both content-preserving and both verified:

- the three commits round 1 built were reworded, same trees, so `6f9c4158` became `6dca0275`,
  `137d28ee` became `93d38598` and `5450f1e3` became `84d5fa87`;
- those six commits were then compacted into the three above, grouped by component. `6d0665bf`
  reproduces `84d5fa87`'s tree exactly, and the tip reproduced `1a30045c`'s until the changelog
  lines were removed from it; `d5faa03d` is a new state (the banner complete, the band work not yet
  applied). The tags that kept those SHAs reachable were deleted with the branches, so treat every
  pre-compaction SHA in this file as a citation only. Nothing in this round needs to build one.

**Nothing round 1 measured is invalidated by either rewrite.**

---

## 0. Before anything else: fix the rig

Round 1 found `/data/data/com.andrerinas.headunitrevived/shared_prefs/` owned `root:root` on this
unit, with the app running as uid 10168. The app can read `settings.xml` but cannot write it, because
`SharedPreferences.apply()` renames a temp file into that directory. **Every setting the app writes
itself is lost on exit**, silently, for any key. That is what made round 1's R2c INCONCLUSIVE.

This round depends on the app's own writes in R2 and R4, so fix it first rather than working around
it:

```bash
adb shell stat -c '%U:%G %a' /data/data/com.andrerinas.headunitrevived/shared_prefs
# if it is root:root, take the app's own uid:gid and chown to it
adb shell run-as com.andrerinas.headunitrevived id
adb shell su -c 'chown -R u0_a168:u0_a168 /data/data/com.andrerinas.headunitrevived/shared_prefs'
adb shell stat -c '%U:%G %a' /data/data/com.andrerinas.headunitrevived/shared_prefs
```

Use the uid the `id` command actually prints; `u0_a168` is round 1's number, not a promise. If the
unit has no root shell, say so and the disk-persistence halves of R2 and R4 become INCONCLUSIVE
again, verified in-process the way round 1 did instead. **Report which of the two happened**, because
it changes what every later run's evidence is worth. This belongs in §7a once it is settled.

---

## 1. Why this round exists

Round 1 passed everything it ran, and then found four things it had not been asked to look for. All
four are fixed in `bd3d7b99` and none of them has been on hardware.

It also proved the round 1 brief wrong about this rig in a way that unlocks the run that was missing.
That brief asserted §7a says this unit *can* read `getSoftApConfiguration()`, so
`HOTSPOT_CONFIG_UNREADABLE` could not be produced here and the three raise paths were out of scope.
The opposite is true and round 1 measured it: with no manual override set, the reflection refused and
the resolve loop printed

```
SoftApCredentials: The access point on wlan2 is up, but this device will not let apps read its name
```

which is the condition, raised by the hardware, with nothing seeded. **So this round tests a raise
path for the first time**, and everything downstream of it end to end.

The four fixes under test:

| # | What round 1 saw | What `bd3d7b99` does |
|---|---|---|
| 1 | The banner asked for a hotspot name and password that were already set | A condition whose own remedy is in place is not shown. The record is kept, not cleared |
| 2 | The remedy tap surfaced the name row only, and both are required | Both rows carry one shared search phrase, and the banner seeds the same string |
| 3 | "This unit could not read its own WiFi address" | "...its own WiFi MAC address (BSSID)", matching the Static BSSID row |
| 4 | A hotspot record showed while WiFi Direct was selected | Each condition is filtered by the route that raises it |

**Finding 1's mechanism was not what it looked like, and the fix is built on the real one.** The
condition cannot re-raise once a manual name is set, because `decide()` returns `CONFIG_UNREADABLE`
only when the resolved SSID is empty. What was on screen was the record from an earlier run, which
only a *successful credential publish* clears. So the fix hides it rather than clearing it, and R2
below is written to tell those two apart: the banner must go and the stamp must stay.

---

## 2. What is different about this round

- **No baseline build, no A/B.** Every run is a state change on one APK, which makes the positive
  controls cheap: each suppression run flips the setting back and the banner must return in the same
  session. **A suppression run without its control is not a pass**, because "no banner" is also what
  an unseeded record looks like.
- **R1 is the point of the round** and is the only run whose input is the hardware rather than a
  seeded stamp.
- **R1 needs the access point up and no manual override.** Those are the same conditions that
  produced the line by accident in round 1. §7a warns `cmd wifi start-softap` is transient here, so
  confirm with `dumpsys` immediately before the launch, not five minutes earlier.
- **Do not leave `hotspot-ssid` set going into R1.** It is what round 1 used for every run after its
  first attempt, so it is the likeliest leftover on the rig, and it makes R1 unreachable rather than
  failing: the condition cannot be raised at all with a name on file.
- **The banner is not a toast.** `hotspot_config_unreadable_toast` also fires on this path and is a
  different surface with a different lifetime. R1 reads the log line and the stamp; the toast is a
  bonus observation, not a PASS condition.
- The phone is needed for R5 only. Everything else is head-unit-side.

---

## 3. Settings keys this round needs

| Key | Type | Element | Why |
|---|---|---|---|
| `wifi-connection-mode` | int | `<int name="wifi-connection-mode" value="3" />` | Native AA. R4c uses `2`. |
| `native-ap-transport` | int | `<int name="native-ap-transport" value="1" />` | `1` = head unit hotspot, `0` = WiFi Direct. Set explicitly every time; R4 flips it. |
| `helper-connection-strategy` | int | `<int name="helper-connection-strategy" value="2" />` | R4c only, so mode 2 is a real Helper configuration. |
| `hotspot-ssid` | string | `<string name="hotspot-ssid">OHU-TEST</string>` | **Deleted for R1.** Set for R2. |
| `hotspot-password` | string | `<string name="hotspot-password">testtest1234</string>` | Same. R2 tests the pair separately. |
| `static-bssid` | string | `<string name="static-bssid">AA:BB:CC:DD:EE:FF</string>` | R2c only. |
| `log-level` | int | `<int name="log-level" value="1" />` | DEBUG. See below. |
| `connection-issue-hotspot-config` | long | `<long name="connection-issue-hotspot-config" value="1755800000000" />` | Seeded record. |
| `connection-issue-bssid` | long | `<long name="connection-issue-bssid" value="1755800000000" />` | Seeded record. |
| `connection-issue-bt-silent` | long | `<long name="connection-issue-bt-silent" value="1755800000000" />` | Seeded record. |
| `connection-issue-dismissed-at` | long | `<long name="connection-issue-dismissed-at" value="0" />` | Delete the key to reset. |

**DEBUG, not VERBOSE and not INFO**, for the same reason as round 1: the lines below sit at four
priorities and none of the files involved contains a `LOG_VERBOSE` guard, checked by grepping for
the guard rather than the call. VERBOSE only brings this unit's driver flood closer to wrapping the
ring buffer inside a run.

---

## 4. The lines that decide every run

All copied from `bd3d7b99` and re-verified with `grep -F` against that tip. Use `grep -a`, always.

| Grep string | Level | Means |
|---|---|---|
| `SoftApCredentials: The access point on` | e | **the hotspot condition raised by itself.** R1's whole point. |
| `SoftApCredentials: SUCCESS - Providing credentials from` | i | the provider resolved a network, which is also the only thing that clears that condition. |
| `MainActivity: showing the connection issue banner for` | i | the banner went up, and the suffix names the condition. Once per condition, not per resume. |
| `MainActivity: could not read the connection issue record` | w | the read threw and the banner degraded to nothing. Should never appear. |
| `ConnectionIssues: settings unavailable, not recording` | d | preferences unreachable at a raise or clear. Should never appear. |
| `AapService: Native AA on the head unit hotspot` | i | the hotspot transport was chosen and the provider started. |
| `AapService: Received WiFi credentials from manager` | i | the delivery landed. |
| `NativeAA: triggerPoke() delay starting (2s)` | d | a poke was scheduled. |
| `NativeAA: BSSID is still masked/empty` | e | the BSSID condition raised. Expected zero all round. |
| `NativeAA: the phone connected over Bluetooth and answered nothing we sent` | w | the Bluetooth condition raised. Expected zero all round. |
| `Handshake: SSL handshake complete` | d | a session went live. |

---

## 5. Runs

### R0 - build and unit gate

```bash
./gradlew :app:assembleGithubDebug
./gradlew :app:testGithubDebugUnitTest
```

**PASS:** clean build, suite green, with these counts:

| Suite | Test methods |
|---|---|
| `ConnectionIssueBannerPolicyTest` | 24 (11 before this round's commit) |
| `ConnectionIssuesTest` | 8 |
| `CredentialsHandoffTest` | 8 |
| `SoftApBandPolicyTest` | 10 |

Total: expect **676**. Round 1 reported 657 against 657 `@Test` annotations, and this tip carries
676, so the two should match again. Report the number either way.

### R1 - the hotspot condition, raised by the hardware. **The point of the round.**

The first time any of the three raise paths has been on a rig.

```bash
PKG=com.andrerinas.headunitrevived
adb shell am force-stop $PKG
# settings: wifi-connection-mode=3, native-ap-transport=1, log-level=1
# DELETE hotspot-ssid and hotspot-password, and all four connection-issue-* keys
adb shell run-as $PKG cat shared_prefs/settings.xml    # confirm both overrides are gone
adb shell cmd wifi start-softap OHU-TEST wpa2 testtest1234 -b 5
adb shell dumpsys wifi | grep -i SoftApInfo            # confirm it is up, note the frequency
adb logcat -c
stdbuf -oL adb logcat -v time > r1.txt &
adb shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity
# leave it 60 s, then force-stop and read settings.xml
```

**PASS, all four:**

- `SoftApCredentials: The access point on` at least 1;
- `connection-issue-hotspot-config` is now **non-zero** in `settings.xml` (this is the app's own
  write, so §0 has to have worked; if §0 failed, this half is INCONCLUSIVE and the log line alone is
  the result);
- relaunching `MainActivity` shows a banner beginning "This unit will not tell the app its hotspot
  name and password", with exactly one
  `showing the connection issue banner for HOTSPOT_CONFIG_UNREADABLE`;
- `SoftApCredentials: SUCCESS - Providing credentials from` is **0**, which is what makes the raise
  the honest outcome rather than a race.

**Pair it with the reachability number.** Report the `SoftApInfo` frequency and whether the AP was
still up at the end. If the AP was never up, the provider sits in `NO_AP_YET`, prints nothing, and
this run is **INCONCLUSIVE**, not a fail. If the reflection unexpectedly succeeds on this unit, the
provider publishes instead and the condition is unreachable here after all: report that, it
contradicts round 1 and is more important than the run.

### R2 - the remedy retires the banner, and keeps the record

Straight out of R1's state, with the record standing and the AP still up.

**R2a, the name alone is not the remedy.** Force-stop, set `hotspot-ssid=OHU-TEST` only, leave
`hotspot-password` deleted, relaunch.

**PASS:** the banner is **still there**. A blank password is sent as an open network the phone
refuses, so the condition is not fixed and the banner must not say it is.

**R2b, the pair is.** Force-stop, also set `hotspot-password=testtest1234`, relaunch.

**PASS, both parts:**

- no banner, and no new `showing the connection issue banner` line;
- `connection-issue-hotspot-config` is **still non-zero** in `settings.xml`. This is the half that
  separates the fix from a clear, and a zero here is a **FAIL** even though the screen looks right.

**R2c, the same shape on the other condition.** Force-stop, delete the hotspot overrides again, seed
`connection-issue-bssid` only, set `native-ap-transport=0`, relaunch and screenshot; then force-stop,
set `static-bssid=AA:BB:CC:DD:EE:FF`, relaunch.

**PASS:** banner naming `BSSID_UNAVAILABLE` first, none after, and `connection-issue-bssid` unchanged
throughout. Then set `static-bssid=0` and relaunch: **the banner is back** - `0` is what the setting
holds when unset and is not an address. That last step is the control; without it this run is passed
by a banner that was never going to show.

### R3 - the tap reaches both fields

Seed `connection-issue-hotspot-config` only, `native-ap-transport=1`, no overrides set. Launch,
wait 4 s (round 1 found a tap inside ~2 s of a cold start can land before the listener is attached),
tap the banner body, not the close button. One screenshot.

**PASS, all four:** Settings opens; the search box reads exactly `Hotspot name and password`; **both**
`Hotspot name (manual)` and `Hotspot password (manual)` are on screen; the keyboard is not open over
the list. Stay in Basic mode throughout, do not switch to Advanced, and do not scroll.

Round 1 passed this with one row on screen. Two is the fix.

### R4 - a record only shows on a route that can produce it

Three flips, each with its own control. Seed all three stamps for every part, so what is being
measured is the filter and not which record exists.

**R4a, hotspot record, WiFi Direct selected.** `wifi-connection-mode=3`, `native-ap-transport=0`.

**PASS:** the banner does **not** name `HOTSPOT_CONFIG_UNREADABLE`. It should name
`BSSID_UNAVAILABLE` or `BLUETOOTH_SENT_NO_DATA`, whichever stamp is newest. Then flip
`native-ap-transport=1` and relaunch: it **must** name `HOTSPOT_CONFIG_UNREADABLE`, because on that
route the hotspot stamp is relevant and the BSSID one is not.

Seed the stamps so the newest is the one that must be *hidden* in each half, and say which order you
used. That is what makes the run about the filter rather than about the sort.

**R4b, BSSID record, hotspot transport.** The mirror of R4a, and it is the same two launches read the
other way round: with `native-ap-transport=1` the banner must not name `BSSID_UNAVAILABLE`. The
hotspot transport survives a masked address by sending an empty one, so it never raises that.

**R4c, Helper mode.** `wifi-connection-mode=2`, `helper-connection-strategy=2`, all three stamps
seeded.

**PASS:** **no banner at all**, and no `showing the connection issue banner` line. All three
conditions are raised inside Native AA. Then set `wifi-connection-mode=3` and relaunch: the banner is
back. This reverses what round 1's R5b observed and called intentional, so if the screen reads worse
this way, say so - that judgement can only be made in front of one.

### R5 - nothing above broke a real session

One normal Native AA session, `native-ap-transport=0`, phone as usual, all four `connection-issue-*`
keys deleted first.

**PASS:** `Handshake: SSL handshake complete`, and afterwards all four keys still absent or zero.
This is the regression guard for R4's filter: a relevance rule that also stopped the *clears* from
running would look identical to a pass everywhere above and only show up here.

### R6 - round-wide invariant: nothing raises by itself

Over every capture except R1's, where the hotspot condition raising is the result:

```bash
grep -ac "the phone connected over Bluetooth and answered nothing we sent" *.txt
grep -ac "BSSID is still masked/empty" *.txt
grep -ac "MainActivity: could not read the connection issue record" *.txt
grep -ac "ConnectionIssues: settings unavailable, not recording" *.txt
```

**PASS:** all zero. A false "your Bluetooth is broken" tells a user to abandon a mode that works, and
this rig completes Native AA sessions routinely, so one unseeded raise here outranks any pass above.

---

## 6. Do not re-run

Settled by round 1 and unaffected by anything in `bd3d7b99`:

- **R1 of round 1**, the credential handover race. Both arms, settled, and the mechanism is now in
  the commit message.
- **The banner surviving a reboot**, the layout in landscape, the WiFi Direct pill overlap, and the
  absent-means-absent check.
- **Portrait layout.** This panel is natively 720x1440 presented as fixed landscape by an OS-level
  compensation and no adb lever moved it across two attempts. It is UNTESTABLE here, permanently.
  Do not spend time on it again.
- **The dismissal rules** and **the never-beside-a-live-session rule.** Both passed, and `bd3d7b99`
  did not touch either.
- **The preflight dialog and the compatibility check**, untouched by all four commits.

---

## 7. Report back

Four numbers decide whether this ships:

1. **§0**: whether the `chown` worked. Everything that reads `settings.xml` back depends on it.
2. **R0**: the total, against 676, and the four suite counts.
3. **R1**: the count of `SoftApCredentials: The access point on`, and whether
   `connection-issue-hotspot-config` was non-zero afterwards. Those two together are the first
   hardware evidence any raise path has ever produced.
4. **R2b**: whether the stamp survived the banner going away. That one number is the difference
   between the fix and a clear.

Everything else is a verdict per §6 of the template. The parts most likely to come back with
something wrong are R4a's stamp ordering, where the brief is asking for care rather than a command,
and R3, where the search phrase has never been matched on a device.
