# hands-free-wake and proto-schema, round 1 brief: the Bluetooth event and the new AAP proto

> **Revised 2026-09-17.** The candidate SHA changed: the branch was compacted from six commits back
> to five, folding `95926314` into the Native AA commit, so the tip is now **`940a6dab`** and
> **`95926314` no longer exists**. Nothing else moved: the tree is byte-identical, the gate is still
> 2052, and every run below is unchanged. **Re-read section 1 only.** If your copy names six commits,
> it is stale.
>
> **Also revised 2026-09-17:** D-SAM's system clock was fixed and now matches the other devices, so
> block 2 no longer asks for a 12 hour correction. Re-read the block 2 preamble.
>
> **Revised again 2026-09-17, and this one changes the build.** A reporter's new capture exposed a gap
> in the hands-free wake and it was closed, so the branch was compacted a third time. The tip is now
> **`885dff2d`**, the Native AA commit is **`caaceabc`**, and the gate reads **2059**, not 2052.
> **Unlike the last two revisions the tree really did change.** Re-read section 1, H2, H4 and the new
> **H5**, which is ungradable on any earlier build.

Supersedes `hands-free-wake-round1-brief.md` and `proto-schema-corrections-round1-brief.md`, neither
of which was run. Both threads are graded here because they share one APK, one rig session and a run
order that has to be decided globally rather than per thread. Do not run either of the old briefs.

## 1. Build and baseline

| | Branch | SHA | Commits | Gate |
|---|---|---|---|---|
| Candidate | `fix/audio-sink-and-wireless-bring-up` | `885dff2d` | 5 on `main` (`5ce51c5e`) | 2059 tests, 0 failures |
| Baseline | the same APK with a setting off | | | |

```bash
git fetch fork
git checkout -B hfw-proto fork/fix/audio-sink-and-wireless-bring-up
./gradlew :app:testGithubDebugUnitTest      # 2059, 0 failures
```

**The branch was compacted three times on 2026-09-17 and what is on `fork` is a rewrite.** Reset any older
local copy rather than pulling it; a pull will not fast-forward. `audio-sink-jitter round 7` graded `de44dc18`, which no longer exists;
its content is all still here, plus the hands-free wake and the schema corrections, plus one fix
written in answer to round 7 itself.

One APK for the whole round. Every lever is a setting, so nothing here rebuilds.

## 2. What this round is

Three things ride in one APK and this round grades all three, on both head units.

- **The Bluetooth event.** A reporter produced the first fully deterministic reproduction of "Native
  AA connects once, then never again until Bluetooth is reset". Android Auto starts wireless setup on
  a Bluetooth **event**, not on a connected state: `WifiBluetoothReceiver` filters `ACL_CONNECTED`
  plus the HFP and A2DP `CONNECTION_STATE_CHANGED`. A radio that holds hands-free across a session
  and past its end raises none, so the phone never re-triggers, and our own wake guard was refusing
  the one poke that could raise one. The change lets the stand-down yield after 90 s, at most twice
  per arming, 60 s apart, and only for a phone that has already run Android Auto on this unit.
- **The new AAP proto.** A field-by-field audit against Google's own schema landed as one commit.
  Most of it is renames with no wire effect. Two things put new bytes out and each ships **off**:
  `WifiVersionRequest` field 3, the bands our access point can offer, and `ServiceDiscoveryResponse`
  field 16, a `ConnectionConfiguration` asking for a 15 s ping timeout instead of 3 s and 64 KB
  socket buffers instead of 16 KB.
- **Round 7's own finding, now fixed.** `pokesSinceLastAccept` was cleared on any RFCOMM accept,
  before a single handshake byte, which is why round 7's S1r2 could not reach the stale-group
  watchdog at all. It is now cleared when a session actually lands. Part S is the run that says so.

**The open question the wake exists to settle.** Android Auto carries a flag
`WirelessProjectionInGearhead__filter_profile_connection_by_acl` whose default is unknown. If it is
on, the phone discards a profile event raised on an ACL that was already up, and a profile-level wake
cannot work at all. H2 is what answers it. **If H2 fails, do not iterate on the wake; the next lever
is an adapter cycle and that is a different design.**

## 3. The rig, and the one rule that orders everything

Two head units and two phones, and the order below is not a suggestion.

| Block | Head unit | Phone | Why |
|---|---|---|---|
| 1 | D-HU (`27870808938846`) | D-POCO (`4f4027e9`) | the real head unit, and the only one that can host an access point |
| 2 | D-SAM (`30041c35642d2200`) | D-POCO | API 19, the other end of the range, and a second unit for field 3 |
| 3 | D-HU | D-MOTO (`ZY22GC3BM4`) | a second Gearhead for field 3, then everything that arms an endpoint |

**The rule: D-POCO must never see `native-ap-transport=1`.** A head unit on its own access point
advertises a WPP-over-TCP endpoint unconditionally, the phone stores it for the life of its Android
Auto process, and **nothing in the protocol or in our code can take it back**. Round 7 lost its first
three bring-ups to an endpoint left over from round 6, cleared only by forgetting the vehicle by hand
on the phone. On WiFi Direct neither unit can advertise one (D-SAM's Android cannot name its own P2P
group, D-HU re-addresses its group on every create), so every other run in this round is safe for
either phone. Block 3 is last because it is the only block that arms one, and it closes by forgetting
the vehicle on D-MOTO.

**When the pairing changes, repoint the unit.** `native-poke-bt-macs` and `last-connected-native-mac`
persist per unit and a head unit still pointing at the previous round's phone will never let the new
one join its group, with no line saying why. Set both on D-HU before block 3 and read them back.

**Every grep in this brief takes `-a`.** Captures here are long enough that `grep` calls them binary
and prints nothing at all instead of `0`, which makes an absent pattern and a refused count look
identical.

## 4. Settings, and how to write them on each unit

Never through the UI: settings categories are not deep-linkable and the list is never scrolled with
adb. The method differs by unit and both are already on the rig.

- **D-HU** is rooted: `set_hu_pref.sh`.
- **D-SAM** has no `su`, no `sed` and no exported `SettingsActivity`. Edit a local copy of
  `shared_prefs/settings.xml` with `python3` on the host, push it, then
  `run-as $PKG cp <pushed file> shared_prefs/settings.xml`. `set_pref_hostedit.sh` is what round 7
  used for every write and it worked.

On D-HU, `stat` the `shared_prefs` directory before the first run: it has been root-owned, and when
it is, the app's own writes never reach disk while reads look fine.

```xml
<int name="log-level" value="0" />                                   <!-- VERBOSE, every run -->
<int name="wifi-connection-mode" value="3" />                        <!-- Native AA -->
<boolean name="native-aa-wake-over-hands-free-link" value="true" />  <!-- false for H1 only -->
<boolean name="native-wifi-version-exchange" value="false" />        <!-- true for P1 and W1 only -->
<boolean name="announce-connection-configuration" value="false" />   <!-- true for P2 only -->
<int name="native-ap-transport" value="0" />                         <!-- 1 for W1 only, D-MOTO only -->
```

`video-profile-starvation-cap` and `playback-focus-self-defeating` are latches. Read both back before
and after every run and say so.

## 5. The precondition the wake runs need, which the rig does not produce on its own

Every H run needs the phone holding a live hands-free link to the head unit **with no session
running**. That state does not occur naturally here: the wake poke's socket closes at handoff, and a
projecting phone drops its ACL seconds after the WiFi handoff and restores it only when the session
ends, which manufactures the very event the reporter's unit never raises.

**The lever is the head unit's adapter, not the phone's.** Cycle it, wait up to 15 s, then verify on
the phone before every run:

```bash
adb -s <phone> shell dumpsys bluetooth_manager | grep -a -A 5 "HeadsetService"
# want: the per-device StateMachine for the head unit's MAC reading mCurrentState=Connected
```

On D-HU the adapter re-enables itself about 14 s after `svc bluetooth disable`, so the disable is the
cycle; do not fight it. **Verify immediately before the step that needs it, never assume it.** A run
that starts without a confirmed `Connected` grades nothing and is recorded UNTESTABLE, not FAIL.

## Block 1: D-HU with D-POCO

### R0. Gate

**PASS:** `./gradlew :app:testGithubDebugUnitTest` reads **2059**, 0 failures, and the installed APK's
md5 matches the one just built. Hash with a real `adb pull` plus `md5sum`; `adb shell cat` piped to
`md5sum` is not an identity check.

### G1. Round 7 still holds on a branch that grew

Round 7 measured R1 and J1r on `de44dc18`, and the wake change edits the same poke path. This is the
cheap confirmation that nothing moved, not a re-run of round 7.

One cold bring-up to a picture, then the tight WiFi hold-down lever (`s1_wifi_holddown_tight.sh`)
until four refusals have gone by, then release.

```bash
grep -a "SUCCESS - Providing credentials"  log.txt
grep -a "Attempting manual poke"           log.txt
grep -ac "so the next wake waits"          log.txt
grep -a  "so the next wake waits"          log.txt | head -5
```

**PASS:** the session forms, the one-shot `retries are slowing down` notice fires once at refusal 2,
and the declared waits climb 15 s, 30 s, 120 s in the same shape round 7 measured. Report the
credentials-to-poke gap as its own number rather than against round 7's, which was measured on the
other unit. A difference in the widening shape is a finding, not automatically a FAIL.

### H0. Reproduce the reporter's failure on this rig

**Baseline arm: `native-aa-wake-over-hands-free-link` = `false`.** This is the run that says the rig
can show the fault at all. If it cannot, everything after it is ungraded and block 1's H runs stop.

1. Bring up a full Native AA session, the phone projecting.
2. End it from the head unit (user exit).
3. Establish the precondition in section 5 and confirm `mCurrentState=Connected`.
4. Leave the app armed for **5 minutes**, capturing throughout.

**PASS (fault reproduced):** zero `NativeAA: Connection accepted from`, and repeated
`NativeAA: Not poking ... already holds a Bluetooth hands-free link to it`. Report the refusal count.
The refusal repeats at DEBUG after the first, so this needs the VERBOSE capture section 4 asks for.

**If the phone reconnects on its own**, the rig does not hold the precondition the reporter's unit
holds. Record H0 as NOT-REPRODUCED, run H4 anyway, and move to Part S. That is a real outcome.

### H1. The setting off changes nothing

Baseline arm, and cheap. Repeat H0 exactly. **PASS:** identical shape, and **no**
`waking ... despite the hands-free link` line anywhere. A wake going out with the setting off is a
FAIL and voids H2.

### H2. The escalated wake reconnects the phone

**Candidate arm: setting `true`. This is the run the round exists for.** Repeat H0's sequence,
capturing for **6 minutes** from the moment the stand-down starts.

**Why 6 minutes, and what a reporter's capture already measured.** On #992's own unit, in this exact
failing state, one poke that escaped the guard by luck was answered 66 s later: `Calling
socket.connect()` at 06:25:03, `Connection accepted from` at 06:26:09, `SSL handshake complete` at
06:26:23. So the window has to cover 90 s of stand-down plus a reconnect that can take that long, and
twice over if the second escalation is to be seen. That reporter's own capture stopped 14 s before the
first escalation was due and therefore graded nothing, which is the mistake this window exists to
avoid. **Treat 66 s as the precedent for the reconnect, not as a bound**; report the interval measured
here as its own figure.

**PASS:**

- `NativeAA: waking <phone> ... despite the hands-free link` appears, the first one **not before 90 s**
  after the first `Not poking` line. Report that interval.
- `NativeAA: Calling socket.connect()` follows it. Its absence means the wake never reached the radio
  and nothing downstream is graded.
- `NativeAA: Connection accepted from <phone>` follows within about 60 s of a wake, and the session
  goes on to `WirelessServer: Incoming connection detected` and `SSL handshake complete`.
- **At most 2** `waking ... despite` lines **per arming**. Count armings in the same capture before
  calling a third a FAIL: the budget resets on every `start()`, and a settings save, a mode change or
  a group recreate re-arms the manager.

```bash
grep -ac "ACTIVELY LISTENING on Android Auto UUID" log.txt   # armings in this capture
grep -ac "despite the hands-free"                  log.txt   # escalated wakes
```

**UNTESTABLE, not FAIL, if the stand-down clock keeps being reset.** The 90 s is timed from the first
refusal and is cleared whenever the phone opens the Android Auto Bluetooth channel. Round 7 measured
this phone's Gearhead opening and abandoning that socket on poke cycles. So:

```bash
grep -ac "Connection accepted from" log.txt   # inside the stand-down window
```

Any accept between the first `Not poking` and the first `despite the hands-free` means the 90 s never
matured and H2 graded nothing. Report the count either way; it is the first thing to read.

**FAIL:** wakes go out, `socket.connect()` succeeds, and no `Connection accepted from` follows either
of them. That is the `filter_profile_connection_by_acl` case. Record it as such, and H3 still runs.

### H3. What the wake costs the hands-free link

**Runs on H2's capture; no separate bring-up.** This is what decides whether the default stays on. On
the phone, before the wake and then every 15 s for 3 minutes after it:

```bash
adb -s <phone> shell dumpsys bluetooth_manager | grep -a -A 5 "HeadsetService"
```

Report whether the link dropped, how long it stayed down, and whether it came back **untouched**.

**PASS:** the link never drops, or is back within 60 s unaided.
**FAIL:** still down at 3 minutes. That reproduces the 3 to 8 minute outage measured on an earlier
round, and the setting must ship default off whatever H2 said.

### H4. A healthy first connect is not disturbed

Candidate arm. The guard against the fix firing where nothing is wrong. From a cold app start with
**no** prior session in this process, bring up one ordinary session.

**PASS:** no `waking ... despite the hands-free link` anywhere, because the phone connects long before
the 90 s stand-down matures. Session forms normally; report launch to `Incoming connection detected`.

**The old reason for this PASS no longer holds, and that is deliberate.** The escalation used to need
an Android Auto channel opened in the current app process. It now also accepts a phone whose address is
stored in `last-connected-native-mac`, which on this rig it will be. A phone paired only for calls is
still never disturbed, because it never handshaked and so was never stored.

**FAIL:** any escalated wake on a first connect. That would mean the app disturbs the hands-free link
of a phone paired only for calls, which is the one thing this design promised not to do.

### H5. The wake still escalates on a unit that has only been rebooted

**Candidate arm, and as much the point of this round as H2.** Before this build the escalation needed
an Android Auto channel opened since the app last started, so a radio that auto-connects hands-free at
boot stood every poke down for good and the fix could never reach the state #992 opened with. It now
reads `last-connected-native-mac` too. **This arm is ungradable on any earlier build**; if the gate
reads 2052 rather than 2059, stop and rebuild.

1. Run one ordinary Native AA session so the phone's address is stored, then end it.
2. Read `last-connected-native-mac` back the way §7a says settings are read on this unit, and record
   what it said. An empty value here voids the rest of the run.
3. **Reboot the head unit.** Let it come up and let the radio auto-connect hands-free on its own. Do
   not connect a session.
4. Establish the precondition in §5 and confirm the link reads `Connected` with no session running.
5. Capture for **6 minutes** from the first `Not poking` line.

**PASS:** `NativeAA: waking ... despite the hands-free link` appears, the first one not before 90 s
after the first `Not poking`, in a capture whose first `ACTIVELY LISTENING` line is the first of this
process. Report that interval, and the accept count inside the window exactly as H2 asks for it.

**FAIL:** refusals run past 90 s and no escalated wake goes out. Read `last-connected-native-mac`
once more before grading that: an empty value means step 1 never stored it, and the run is UNTESTABLE.

**UNTESTABLE** on H2's terms as well, if an accept inside the window keeps resetting the clock.

### S1. The stale group, with the counter that round 7 found stuck

Round 7's S1r2, re-run against the fix. The count is now retired by a landed session rather than by a
bare socket accept, so a phone that dials and never joins should finally drive the watchdog.

1. Bring up a real session and let it render, then confirm the group is armed:
   `WifiDirectManager: this group has carried a session, so the join watchdog will not recreate it if
   the phone leaves`.
2. Forget the vehicle on the phone (Gearhead settings, Vehicles, Google, Forget; `uiautomator dump`
   plus minimum taps, no scriptable trigger exists). Leave both radios alone.
3. End the live session with **one** `force-stop` of Gearhead on the phone.
4. Leave the head unit armed and capture for **8 minutes**.

**PASS:** the poke count climbs past 4 without being zeroed by an RFCOMM accept, and
`WifiDirectManager: Native AA` ... `the phone has ignored N wake pokes since this group last carried a
session` fires, followed by a group recreate.

```bash
grep -a  "has answered .* wake pokes"    log.txt
grep -a  "has ignored .* wake pokes"     log.txt
grep -ac "Connection accepted from"      log.txt
grep -ac "createGroup SUCCESS"           log.txt
```

**FAIL:** accepts happen, the count still reads 0 in the `ignored N wake pokes` line, or the watchdog
never fires within 8 minutes with four or more pokes sent. Report the accept count beside the poke
count: round 7's whole finding was that the two were coupled, and the fix is that they are not.

### P3. Control: both proto settings off

One session, start to a live picture, then a clean exit. Both settings off.

```bash
grep -ac "SSL handshake complete"                   log.txt
grep -a  "Media Sink Setup Request: . on channel VIDEO" log.txt
grep -a  "Throughput over 5000ms"                   log.txt | tail -5
grep -a  "Asking for ping timeout"                  log.txt   # want: nothing
grep -a  "WifiVersionRequest (Type 4)"              log.txt   # want: nothing
```

**PASS** when the session forms and projects and neither new line appears. Either line here means a
setting is not actually off, and the rest of Part P is void until that is fixed.

### P1. Field 3: the version exchange on

Turn `native-wifi-version-exchange` on, transport left on WiFi Direct. Three bring-ups from a cold
arm, each to a live picture or to a recorded failure.

```bash
grep -a "NativeAA: \[TX\] Sending WifiVersionRequest (Type 4)" log.txt
grep -a "NativeAA: \[RX\] WifiVersionResponse"                 log.txt
grep -a "advertising WPP over TCP"                             log.txt
```

Record the whole `WifiVersionResponse` line per bring-up. Three fields decide it:

- `status=`: still `NO_SUPPORTED_WIFI_CHANNELS(-8)`, or something else.
- `channelType=`: present at all, and if so which value. This is the only place the phone ever says
  which band it wants. **Absent on every run is itself the finding.**
- `v4.2` or lower: the protocol the phone offered, which gates whether it reads field 3 at all.

**Carry the tension in rather than resolving it in advance.** Nineteen of nineteen captures on file
show `-8` on sessions that went on to connect, so `-8` alone is noise. **A P1 that changes nothing is
a result, not a failure**, and it retires the audit's best remaining lead.

**Also graded here, and it keeps the phone clean:** the third grep must read
`NativeAA: not advertising WPP over TCP: this unit gives its WiFi Direct group a new address on every
create` (or the not-yet-seen-to-repeat variant on a first bring-up). An `advertising WPP over TCP at`
line on WiFi Direct is a **FAIL and a stop**: report it, forget the vehicle on the phone before
anything else, and do not continue block 1.

**PASS** when three bring-ups reach a picture and the response line is captured on each. Grade the
lever separately from the session: a bring-up that fails with the exchange on is a FAIL of the
exchange, and it is already known that a dongle refuses it.

### P2. `ConnectionConfiguration`: a longer ping timeout

Version exchange back **off**. Turn `announce-connection-configuration` on.

**Read the ordering correctly or this run grades nothing.** The AAP version handshake happens *before*
`ServiceDiscoveryResponse` goes out, so `Handshake: the phone asks for ping timeout` is the **phone's
own ask of us**, not a reply to ours. There is no reply: the protocol gives the phone no way to
acknowledge field 16, which is why this has to be measured behaviourally.

```bash
grep -a "\[ServiceDiscovery\] Asking for ping timeout" log.txt
grep -a "Handshake: Version response received"         log.txt
grep -a "Handshake: the phone asks for ping timeout"   log.txt
grep -a "station scans:"                               log.txt
```

Hold a session for **at least 15 minutes** with the map moving, and record every `station scans:` line
with its cadence, whether the session survived each scan window, the `Throughput over 5000ms` rows
either side, and what ended the session if it ended.

**The survival arm has a precondition this rig may not meet, and that is pre-registered.** Scans only
happen on a station joined to something, D-HU's own `WifiScanner` service does not work by any route,
and there may be no ordinary WiFi network for it to join. If one is reachable, join the head unit's
station to it before the run and say so. If not:

- **PASS (reduced)** when the line goes out and a 15 minute session forms and holds no worse than P3.
  That grades "asking costs nothing", which is the honest deliverable here.
- **UNTESTABLE** for the survival claim when no `station scans:` line lands in the window. Not a FAIL.
- **FAIL** if the session ends sooner or more often than P3's control.

## Block 2: D-SAM with D-POCO

Same phone, so nothing has to be repointed. D-SAM is 2.4 GHz only, cannot host an access point by any
path, and **its clock no longer runs 12 hours behind**: it was set by hand on 2026-09-17 and matches
the other devices, so no correction is needed. Read `date` on it against the host's once before the
block and record what it said. Leave `resolutionId` alone; the app's own cap handles 1080p.

- **H0', H1', H2', H3', H4'** exactly as block 1, including the accept-count read that decides whether
  H2' graded anything. This is the second unit for the wake and the one whose Android is old enough to
  match the reporter's.
- **P1'** exactly as block 1, with one difference in what is expected: D-SAM's Android cannot name its
  own WiFi Direct group, so the withhold line should read `this unit's Android is too old to name its
  own WiFi Direct group`. That wording, on this unit, is the graded result.

The H runs are P3's control for this unit: both proto settings are off throughout them, so confirm
once, on the first H0' capture, that neither `Asking for ping timeout` nor `WifiVersionRequest (Type
4)` appears before turning anything on for P1'. Skip P2 and S1 here. P2 needs a station this unit has
no network to join, and S1 needs the watchdog arm block 1 already grades.

## Block 3: D-HU with D-MOTO, and nothing before it

Pair D-MOTO with D-HU, repoint `native-poke-bt-macs` and `last-connected-native-mac`, read both back.
Everything from here can leave an endpoint on the phone, which is why it is last and why it is this
phone.

### P1''. Field 3 on a second Gearhead

Block 1's P1, unchanged, on D-MOTO, still on WiFi Direct. Run it **before** W1, while this phone is
still clean. Two phones answering the same question is the difference between a data point and a
finding.

### W1. The serve path, on a unit that can host an access point

Round 7 could not run this at all: D-SAM has no RIL and no hotspot screen. D-HU can.

Turn the hotspot on by hand on D-HU first, then set `native-ap-transport=1`,
`native-wifi-version-exchange=true`, `auto-enable-hotspot=false`, and fill `hotspot-ssid`,
`hotspot-password` and `static-bssid` from the AP that is actually running. Do not guess the
interface name; read it off the unit.

```bash
grep -a "advertising WPP over TCP at"     log.txt
grep -a "SoftApCredentials:"              log.txt
grep -a "WppTcpServer:"                   log.txt
grep -a "No WiFi credentials available"   log.txt
```

**Stop here if there is no `advertising WPP over TCP at` line**, and report why: the credential
resolver gives up after 30 s and the handshake after 60 s, and both say what they were missing.

**PASS** when the endpoint goes out, the phone joins the access point and a session forms and
projects. Report whether `WifiInfoResponse` went out on the TCP path, which is the one thing round 7
could never reach.

### W2. A stale endpoint is refused rather than served

New, cheap, and the only run that has ever reached `WppTcpServePolicy`. W1 has just armed an endpoint
on D-MOTO that nothing can retract; this measures the guard that stops it doing harm.

Set `native-ap-transport=0` (back to WiFi Direct), turn the hotspot off, relaunch, and leave the unit
armed for 5 minutes while D-MOTO tries to reconnect. The TCP listener on 5299 is up on every
transport, so the phone's stored endpoint will be dialled.

**PASS:** `WppTcpServer: not serving this dial:` fires with a reason naming the group identity, the
socket is closed, and the phone is not handed a group it cannot keep. A Bluetooth handshake may or may
not follow; report which.

**FAIL:** the dial is served on WiFi Direct. That is the phone-brick shape and it is the reason the
gate exists.

### Closing block 3

Forget the vehicle on D-MOTO, restore every setting to the pre-round backup, and confirm by readback:
`native-wifi-version-exchange=false`, `announce-connection-configuration=false`,
`native-ap-transport=0`, hotspot keys cleared. Say in the results that the forget was done; the next
round on this phone depends on it.

## 6. Phone-side capture, worth more than anything else here

If `adb logcat` on the phone can be run during H2 or H2', grep for:

```
WIRELESS_SETUP_SHARED_HFP_CONNECTING
WIRELESS_SETUP_SHARED_ACTION_ACL_CONNECTED
WIRELESS_SETUP_FAILED_TO_START_NO_HFP_FROM_HU_PRESENCE
WIRELESS_SETUP_CANCELLED_HU_NOT_CONNECTED
WIRELESS_SETUP_CAR_BLUETOOTH_DISAPPEAR_BEFORE_WSEM
```

`..._HFP_CONNECTING` right after our wake settles the mechanism outright and makes H2 causal rather
than correlational. `..._CANCELLED_HU_NOT_CONNECTED` would mean our wake cancelled a setup already in
flight, which is a harm worth knowing about. `..._CAR_BLUETOOTH_DISAPPEAR_BEFORE_WSEM` is what round 7
saw from a poisoned phone and means the vehicle needs forgetting before the run counts.

## 7. What this round cannot settle

- **Whether the phone honours field 16 at all.** Only the session's survival is observable, so a clean
  15 minutes is weak evidence and two rounds' worth is not much stronger. Say so rather than calling
  it honoured.
- **Protocol 1.6.** Google gates the ack timestamps and the underflow notification on it, and
  announcing 1.6 obliges us to populate them. It is a spike and is deliberately out.
- **Whether the reporter's exact failure exists on this rig.** Both phones raise the Bluetooth event
  by accident when a session ends, which is the thing the reporter's radio never does. The
  precondition in section 5 manufactures the state; it does not make the rig into that unit.
