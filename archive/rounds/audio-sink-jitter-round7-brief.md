# audio-sink-jitter, round 7 brief: the widening, waited where it can be reached

## 1. Build and baseline

| | Branch | SHA | Commits | Gate |
|---|---|---|---|---|
| Candidate | `fix/audio-sink-and-wireless-bring-up` | `de44dc18` | 7 on `main` | 2022 tests, 0 failures |
| Baseline | `main` | `5ce51c5e` | | |

```bash
git fetch fork
git checkout -B audio-sink fork/fix/audio-sink-and-wireless-bring-up   # de44dc18
git log --oneline -7
# de44dc18 Native AA: the refusal gap is waited where a wake round starts
# 78f7e70b Transport: the video ceiling's own numbers, measured at the ceiling
# 8441d044 Native AA: the endpoint withhold now covers the dial, and creates cannot race
# 9ecc0077 Misc: the overlay, the USB reconnect, and a Share button that killed the app
# d9295370 Native AA: bring-up is gated and bounded, and old Android says what it cannot do
# 8a42674c Transport: video gets its own thread, and its ack is the flow control
# e8222786 Audio: the sink is measured, sized by the dial, and keeps what it has
```

**Nothing was rewritten. The six commits round 6 graded still carry those SHAs**, so a tree left over
from round 6 fast-forwards. One new commit sits on top, and it touches the Native AA wake path only.

`de44dc18` is what R1 and J1r grade. `8441d044` is what W1r grades.

**Baseline is needed for R0 only.** Everything else is candidate only.

## 2. What this round is

Round 6 closed two of the three fixes round 5 forced out of us. **N7r2 and T3 are done and this round
repeats neither**: the double-create guard fired, delivered one group and one SSID, and cut the
bring-up from 50.7 s to 9.67 s; and a 17.5-minute session put the `videoQueue` high-water at **5**
with `videoShed` at 0 in all 35 windows, which closes the video-ceiling question for good.

What is left is J1, which **FAILed**, and two runs that were blocked before they could grade
anything.

**J1's FAIL was real and is now root-caused.** The widening was wired at the foot of the poke retry
loop. A refusal can only be counted after the phone has opened the Bluetooth channel and been sent
its credentials, and sending them cancels that loop on purpose, so the gap at the foot was never
reached: `ResumePoke` then started a fresh loop that poked at once. That is why the counter climbed,
the notice printed, and the cadence stayed at 15.2 s. `de44dc18` waits the same gap at the **head** of
each wake round as well, from a stamp taken when the refusal is counted, so whichever loop is alive
honours it. Round 6's own numbers were the proof: a loop alive at the foot would have given about
30 s (a 15 s poke hold plus the 15 s gap), and 15.4 s is shorter than that.

Read `audio-sink-jitter-round6-results.md` first. Its Setup notes carry rig facts this round depends
on, in particular the four hotspot keys that have to be cleared on a revert.

Same rig: **D-SAM as head unit, D-POCO as the phone**, API 19 on a 2.4 GHz-only radio.

**Run order: R0, R1, J1r, S1r2, W1r.** W1r last, because it re-poisons D-POCO's Gearhead and S1r2
needs the phone's Android Auto in a known state.

## 3. Settings keys this round needs

Round 4's table still applies. These are the differences:

```xml
<int name="log-level" value="0" />                              <!-- VERBOSE, every run -->
<int name="wifi-connection-mode" value="3" />                   <!-- Native AA -->
<boolean name="native-wifi-version-exchange" value="true" />    <!-- W1r ONLY, restore to false -->
<int name="native-ap-transport" value="1" />                    <!-- W1r ONLY, see W1r -->
```

`native-wifi-version-exchange` defaults to `false`, and with it off the WPP version exchange never
runs and W1r grades nothing.

**Reverting W1r means five writes, not one.** Round 6 found that flipping `native-ap-transport` back
to `0` while leaving `hotspot-ssid`, `hotspot-password`, `static-bssid` and `hotspot-interface` set
makes the next WiFi Direct group form on the hotspot's own static BSSID and read
`identity stable=yes`, which is a false reading that would void any run graded on it. Clear all four
(`""`, `""`, `"0"`, `""`) and read them back.

`video-profile-starvation-cap` and `playback-focus-self-defeating` are still latches. Read both back
after every run as well as before one.

## 4. Runs

### R0. Gate

**PASS:** `./gradlew :app:testGithubDebugUnitTest` reads **2022** tests, 0 failures, and the installed
APK's md5 matches the one just built. The figure moved from round 6's 2017: five of those are the new
`JoinRefusalPolicy.remainingDelayMs` cases, which J1r grades on hardware.

**Hash the APK with a real `adb pull` and `md5sum`.** Round 6 found `adb shell cat <apk> | md5sum`
returns a different hash for a byte-identical file; that pipe is not a valid identity check.

---

### R1. A healthy wake is not slowed down

**Candidate only, and cheap. Run it before J1r.**

The new wait sits at the head of every wake round, so a stamp that is not cleared properly would
delay the poke on a phone that never refused anything. This is the guard against that, and it is the
run that says the fix costs nothing when nothing is wrong.

One ordinary bring-up with nothing held down, from a cold start of the app, through to a rendered
session.

**PASS:**

- **No `NativeAA: the phone has refused this network N times in a row, so the next wake waits` line
  anywhere in the run.** That single absence is what R1 grades.
- `NativeAA: Attempting active poke to device` follows the credentials within the usual couple of
  seconds. Report the interval from `SUCCESS - Providing credentials` to the first poke.
- Time from launch to `WirelessServer: Incoming connection detected`, against round 6's **9.67 s**.

**FAIL:** the wait line appearing on a run with no refusal in it, or the first poke arriving more
than about 5 s later than round 6's.

Then **end the session cleanly and bring it up once more.** A landed session clears the stamp, so the
second bring-up must also be free of the wait line. A wait line on the second bring-up means the
stamp survives a session, which is a FAIL.

---

### J1r. The refusal widening, now waited where it can be reached

**Candidate only. This is the run the round exists for.**

Round 6 measured eleven consecutive pokes at a flat 15.0 to 15.5 s while the refusal count climbed
well past five. The counter, the notice and the policy arithmetic were all correct; the gap was
waited somewhere the refusal path never reaches.

**Use round 6's own lever:** `s1_wifi_holddown_tight.sh`, D-POCO's WiFi down on a 3 s cadence. It
produced a clean run of `WIFI_NETWORK_UNAVAILABLE(-11)` refusals and is the cheapest way to the
shape. Say which lever was used either way.

**Measure the gaps, not the count.** Report the interval between consecutive
`NativeAA: Attempting active poke to device` lines, in order, as the same table round 6 gave, for at
least seven refusals. Hold the lever long enough to get past the fifth: at the widened gap that is
several minutes, so plan for roughly ten.

**PASS, all four:**

- `NativeAA: the phone has refused this network 2 times in a row, so retries are slowing down` fires
  once, as it did in round 6.
- `NativeAA: the phone has refused this network N times in a row, so the next wake waits <M>ms`
  appears at INFO, and `<M>` is a real number of ms rather than 0. **Quote the first one.** This line
  is new in `de44dc18` and is the direct evidence the gap is being waited.
- Measured gaps of **at least 30 s** from the second refusal.
- Measured gaps of **at least 120 s** from the fifth.

**FAIL:** a flat ~15 s cadence, which is round 6's reading and the pre-fix behaviour; or the wait line
appearing with the cadence unchanged, which would mean the wait is being cancelled rather than served.

**Then release the lever and confirm the cadence comes back.** A landed session clears the count and
the stamp. Read this half loosely and say so in the results: round 6 found D-POCO's `dumpsys wifip2p`
reporting `P2pDisabledState` for minutes after a rapid WiFi bounce while the radio in fact worked, so
the phone's own recovery is slow and uncertain to observe here. If a session forms at all after the
release, that is enough.

Report these as numbers whatever the verdict:

```bash
grep -c "Attempting active poke to device" j1r.txt   # round 6: 15
grep -c "refused this network"             j1r.txt   # round 6: 1
grep -c "so the next wake waits"           j1r.txt   # round 6: line did not exist
```

---

### S1r2. The stale-group recreate, with a lever that holds

**Candidate only. Round 6 was blocked here; the lever is what changes, not the run.**

Round 6 found that `am force-stop com.google.android.projection.gearhead` does not hold: Gearhead's
own Bluetooth-triggered receiver restarts it and it answers the very next poke, every time. The two
documented ways to hold it down (`pm disable-user`, a background force-stop loop) were both refused by
the rig session's permission scope. That is a tooling limit, not a rig or code one, and it should not
be retried the same way.

**The lever that holds is forgetting the vehicle on D-POCO**, in Android Auto's own settings. It is a
one-time UI action, it lasts the rest of the round, and it leaves the phone's Bluetooth stack
answering the HFP poke, which is exactly what the counter measures: `pokesSinceLastAccept` is
incremented by a **successful** poke and reset only when the phone opens the Android Auto channel.
Forgetting the vehicle needs `uiautomator dump` plus taps; round 5 did it and the recipe is in
`TESTING-TEMPLATE.md` section 7a.

1. Bring up normally and let a real session render, so
   `this group has carried a session, so the join watchdog will not recreate it if the phone leaves`
   fires. **Confirm that line before going on**; without it the branch under test is not armed.
2. Forget this vehicle on D-POCO. Leave both radios alone.
3. Watch for at least six poke cycles, which is roughly five minutes.

**PASS:**

- `NativeAA: Successfully poked POCO X3 NFC via HFP-AG` each cycle, with **no**
  `NativeAA: Connection accepted from`. That pair is the state the counter counts, and confirming it
  in the first two cycles is what says the lever held.
- At the fourth such poke:
  `WifiDirectManager: Native AA - the phone has ignored 4 wake pokes since this group last carried a session, so the network it saved is not reaching it either; recreating the group.`
- Exactly one `createGroup SUCCESS` follows it.
- Re-consent on the phone and it returns **without anyone pressing the WiFi button**. Report how long
  it took.

**FAIL:** the counter climbs past four with no recreate, or the recreate fires while a session is live
or an exchange is in flight, or it fires more than the bound allows.

**If the lever still does not hold, report it blocked and stop.** Do not spend the round hunting a
third way; say which cycle the phone answered on, and the thread will take it from there.

**Then do the control.** Leave the vehicle forgotten and keep watching: the recreates must stay
bounded, no more than four, then `phone still not connected after N recreations`. An unbounded
recreate loop is a FAIL even if the first half passed.

```bash
grep -c "MATCH! Starting AapService"  log.txt   # round 4: 0
grep -c "createGroup SUCCESS"         log.txt   # round 4: 0 in the stuck window; round 5: 2
grep -c "Attempting active poke"      log.txt   # round 5: 12
grep -c "ignored .* wake pokes"       log.txt   # round 5: 0, round 6: not reached
```

---

### W1r. The serve path, on the arm a user can actually reach

**Candidate only. This is a narrower run than round 6's W1, deliberately.**

Round 6 could not grade W1 at all: across four attempts D-POCO's Gearhead decided the head unit's
Bluetooth had disappeared and never dialled 5299, so `WppTcpServePolicy` was never exercised. That
was read as rig Bluetooth flakiness, and it may be, but the staging had a structural problem
underneath it that this round fixes.

**The withhold-then-dial case is not reachable on this pair, and round 7 must not spend attempts on
it.** `WppEndpointPolicy.decide` withholds only on a non-hotspot strategy, or when nothing is
listening, which the fix deliberately never produces. On WiFi Direct, D-SAM is below API 29, reads
RENAMED and therefore **never advertises an endpoint at all**, so the phone can never hold one to
dial back with. Round 6's arming step got round that by caching an endpoint on the hotspot transport
and then reverting to WiFi Direct, but a cached endpoint on a network that no longer exists cannot
produce a dial either: that is what
`WIRELESS_WIFI_PROJECTION_PROTOCOL_TCP_NETWORK_UNAVAILABLE` then
`WiFi Projection Protocol cannot start as HU is not present` is. There is no staging on D-SAM and
D-POCO that puts a **reachable** cached endpoint behind a `Withhold` verdict. It is pre-registered
UNTESTABLE in section 5, covered by `WppTcpServePolicyTest`'s exact-complement test and by round 5's
N4r, which saw the endpoint withheld on seven real creates.

**What is reachable, and what W1r grades, is the other arm: a dial we *would* have advertised is
still served.** That is the half a user meets, and the half a gate like this can break. Round 6's
brief had it the wrong way round in its section 5; the hotspot strategy advertises whatever the group
identity reads, so the advertise case is live on this unit.

**Stay on the hotspot transport for the whole run.** This is the one change from round 6: arm and
grade in the same configuration rather than arming and reverting.

1. `native-ap-transport=1`, round 5's hotspot values from `wpp-over-tcp-round5-brief.md` section 3,
   and `native-wifi-version-exchange=true`.
2. Bring up once. Confirm `NativeAA: advertising WPP over TCP at 0.0.0.0:5299` and that
   `[TX] Sending WifiVersionRequest (Type 4)` carries an endpoint. Quote the line. **If it does not,
   stop here and report that.** Let the phone join and the session form, so the endpoint is cached
   against a network that is still up.
3. **Without changing any setting**, end the session and bring up again. The phone should now dial
   the endpoint it cached rather than waiting on Bluetooth.

**PASS:**

- `WppTcpServer: connection from 192.168.x.x` appears.
- **No `WppTcpServer: not serving this dial:` for it.** The gate must let this dial through.
- `WppTcpServer: [TX] WifiInfoResponse (Type 3) with credentials` goes out on the TCP path, and the
  session forms.

**FAIL:** a dial that is refused, which would mean the gate is refusing what the same session had just
advertised; or `WppTcpServer: listening for Android Auto on TCP 5299` with no `connection from` on the
second bring-up, **if** the version request carried an endpoint on the first.

**If the phone never dials again, that is INCONCLUSIVE, not a FAIL**, and round 6's four attempts
already say it is a likely outcome. Two attempts is enough; say how many were made and what D-POCO's
own log gave as the reason.

**Note the known risk and report against it:** `hotspot-route-needs-5ghz` says a 2.4 GHz hotspot
drops within seconds, and D-SAM is 2.4 GHz only. The dial happens early, before video, so the run can
still grade; but if the AP drops, say when, because that separates "the gate refused it" from "there
was no network to dial over".

**Restore `native-wifi-version-exchange=false`, `native-ap-transport=0` and all four hotspot keys
afterwards, and confirm each by readback.** See section 3.

---

## 5. Pre-registered UNTESTABLE on this rig

Unchanged from round 6 section 5, and all still apply: anything needing 5 GHz or a cross-band split,
the positive `wifi-direct-stable-identity` case, `deletePersistentGroup`, the USB bus-composition
rework, round 3's Part P, whether the phone counts the flow-control window in messages or frames, and
whether refusing a dial makes an already-poisoned Gearhead fall back to Bluetooth.

**Corrected this round.** Round 6's section 5 pre-registered the wrong half of `WppTcpServePolicy`.
It is the **withhold-then-dial** case that has no path on this pair, for the reason set out in W1r,
not the advertise case; the advertise case is live on the hotspot strategy and is what W1r grades.

## 6. Report back

`audio-sink-jitter-round7-results.md`, format in `TESTING-TEMPLATE.md` section 7, one section per run
id above.

Four things this round wants named explicitly whatever the verdicts are:

1. **The poke-to-poke intervals in J1r**, in order, as seconds, in the same table round 6 gave.
2. **The first `so the next wake waits <M>ms` line in J1r**, quoted, or said plainly to be absent.
3. **Whether any `so the next wake waits` line appeared in R1 at all.** Its absence is the whole run.
4. **Whether `WifiInfoResponse` went out on the TCP path in W1r**, and on which bring-up.

`de44dc18` is graded by R1 and J1r and by nothing else: until this round runs it is unmeasured, and
the results should say so in those words if either run ends short of a verdict.
