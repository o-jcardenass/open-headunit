# hfp-profile-presence, round 2: does it work without being told to?

**Candidate:** `fork/fix/hfp-profile-presence` @ `ee49a623` (4 commits on `e6b19c3a`)
**Baseline:** tag `hw-round-1-tested` @ `c5609e16`, which is the APK round 1 measured
(md5 `2f905faa867a3667ff04afd9465fe93f`). If it is still installed, no baseline build is needed.

Read `TESTING-TEMPLATE.md` first, and §7a before planning. This brief carries only what is specific
to the branch.

---

## 1. Why this round exists

Round 1 proved the mechanism: completing the hands-free service level connection made the projecting
phone count the head unit as connected with a profile, and a full two-phone session formed 3/3.
It also proved the reverse, 0/1 with the setting off.

**But every one of those runs wrote `native-aa-complete-hfp-slc` into `settings.xml` by hand.** The
setting has now been flipped on by default and given a UI row, and the default path has never run.
That is the whole question this round answers. Everything else here is regression cover for the two
things that changed around it.

Two commits since the tested tip:

- `74a70d12` the gate. One predicate now decides all three hands-free sockets. Before, the two
  **accepted** sockets opened the exchange unconditionally while only the poke was gated, so the
  setting was a half switch. The gate also stands down where a real hands-free link is already up,
  and it reads that link without the gateway role, because that role reports this unit's own
  headset rather than anything competing with standing in for the phone.
- `ee49a623` the default flip, the settings row, the backup key, and two renamed strings.

**One thing to be clear about before reading any result.** The accepted-socket path has never fired
on this rig or in any reporter log, across six rounds now. So the gate's stand-down rule is expected
to be **silent** here, and its absence from the logs is not a failure. What this round can actually
test is the default, the opt-out, and that nothing regressed.

Also dropped since round 1: the audio sink decoy, which was commit `c5609e16` and is gone entirely.
Nothing ever dialled it. Round 1's own numbers are the check on that, see R4.

---

## 2. Rig facts that shape the runs

Carried forward from round 1's setup notes, because they will bite again:

- **`ACTION_NATIVE_AA_POKE` cannot be fired from adb on D-MOTO.** `AapService` is
  `exported="false"` and the device is not rooted, so `am start-foreground-service` returns
  `Requires permission not exported from uid ...` and `am broadcast` is dropped silently. Template
  §7a's third option describes this working; it does not work on this device. **Use round 1's
  workaround:** set `auto-start-bt-macs` to D-POCO's MAC alone, so the auto-poke loop pokes only
  that device every 15 to 30 s through the same `pokeDevice()` path. Left empty, the fallback loop
  walks all 8 bonded devices at ~10 s each and may never reach D-POCO in a whole capture, which is
  what happened in round 1's R2b.
- **Never leave a `settings.xml.bak` beside the file.** SharedPreferences reads a stray `.bak` as an
  aborted write and restores it over the edit, silently. This cost round 1 an attempt. `rm -f` any
  stray one before each write.
- **D-MOTO's logcat needs the source filter** `OPENHU:V '*:S'`; unfiltered, ROM spam buries every
  line. D-POCO is captured unfiltered, and the phone-side profile state is read from D-POCO.
- **Airplane mode cannot be toggled from adb on D-POCO**, and every poke run needs D-POCO reachable
  over Bluetooth anyway. Leave its radios up and apply the discard-rule checks instead.
- **Bring the head unit up before the phone** (§7a), or two sessions race.
- **Poke connectivity varies session to session** (§7a). No run's verdict below depends on a poke
  connecting; a run where none connected is INCONCLUSIVE, not a FAIL.
- **A session can form with zero pokes**, because the phone's own reconnect can beat ours. See §6.
- **`build_hur.sh` deletes the previous APK.** Copy each build out of `apks/` immediately.

Check the bond by hand before starting; adb cannot restore it.

---

## 3. Devices

Same three as round 1.

| Role | Device | Notes |
|---|---|---|
| **D-MOTO** | motorola edge 30 neo, BT `A0:46:5A:97:E4:95` | head unit under test, wireless mode 3 |
| **D-POCO** | POCO X3 NFC, Android 12, Gearhead `17.5.663204`, BT `DC:B7:2E:5E:4E:59` | projecting phone, leave on its own build |
| **D-HU** | UNISOC MT50, Android 14, BT `11:46:03:10:33:59` | real head unit, regression arm |

---

## 4. Settings

`log-level=1` is DEBUG (the enum is VERBOSE 0, DEBUG 1, INFO 2). Every line this round needs is an
unguarded `AppLog.i` except `could not read radio`, which is an unguarded `AppLog.d`, so DEBUG
carries all of them and VERBOSE only costs ring buffer.

| Key | R1 | R2 | R3 (D-HU) |
|---|---|---|---|
| `wifi-connection-mode` | 3 | 3 | 3 |
| `native-ap-transport` | 0 | 0 | 0 |
| `static-bssid` | 0 | 0 | 0 |
| `log-level` | 1 | 1 | 1 |
| `native-wifi-version-exchange` | false | false | false |
| `insecure-aa-rfcomm-listener` | false | false | false |
| `auto-start-bt-macs` | `{DC:B7:2E:5E:4E:59}` | same | not needed |
| **`native-aa-complete-hfp-slc`** | **absent, deleted** | **false** | **absent, deleted** |

**R1's setting is the round.** The key must be *absent*, not written `true`. Round 1's R4 left it
`false` on D-MOTO at one point, so delete it explicitly with both removal forms from §1 of the
template and **verify it is gone before launching**:

```bash
adb -s <D-MOTO> shell run-as com.andrerinas.headunitrevived \
  cat shared_prefs/settings.xml | grep -c "native-aa-complete-hfp-slc"
# must print 0
```

If that prints anything other than 0, the run tests nothing.

---

## 5. Log lines, all verified against `ee49a623`

Head unit side, `OPENHU`:

```
NativeAA: radio [...] gets the stand-in HFP record, because it advertises no Hands-Free.
NativeAA: radio [...] gets the stand-in HFP record, because its records could not be read.
NativeAA: radio [...] already advertises Hands-Free, so the stand-in HFP record is not registered
NativeAA: a real hands-free link is up, so the stand-in does not open one
NativeAA: HFP responder active for ...
NativeAA: hands-free service level connection established
NativeAA: Successfully poked ... via HFP-AG
NativeAA: Connection accepted from ...
NativeAA: HFP connection accepted from ...
WirelessServer: Incoming connection detected from /192.168.49.x
Handshake: SSL handshake complete
```

The first three are **new this round** and are the round's own instrument: the register branch used
to print nothing at all, so a log could not tell a radio with no hands-free stack apart from one
whose records could not be read.

Phone side, D-POCO, unfiltered:

```
WIRELESS_SETUP_FAILED_TO_START_NO_HFP_FROM_HU_PRESENCE     the failure
WIRELESS_SETUP_SHARED_HFP_CONNECTING                       it proceeding
startWirelessSetup for <D-MOTO MAC>
```

Phone-side profile state, the primary read (round 1 confirmed this resolves on D-POCO):

```bash
adb -s <D-POCO> shell dumpsys bluetooth_manager | grep -A 40 "Profile: HeadsetService"
```

Look for `==== StateMachine for A0:46:5A:97:E4:95 ====`, its `mCurrentState`, and the timestamped
transition log. Capture the whole section rather than grepping one string.

---

## 6. Runs

### R0: build gate

Build the candidate. Report **test count and failures**, measured not computed.

- Expected **1071 / 0**. Round 1's tip measured 1069, so the delta is minus 4 decoy cases and plus 6
  gate cases.
- `HfpServiceRecordPolicyTest` should be **11** (was 9 at round 1's tip: 4 decoy cases removed, 6
  added).
- Confirm the decoy is actually gone from the built APK, not just the source. Per memory, a resource
  or string grep has passed on the wrong build twice, so grep the DEX for a symbol:
  ```bash
  unzip -p <apk> classes*.dex | strings | grep -c "AudioSinkDecoy\|Audio Sink"
  # expect 0
  ```
- Record the candidate APK md5.

A failure here stops the round.

### R1: the default, with nothing written. **This is the round.**

Candidate on D-MOTO, `native-aa-complete-hfp-slc` verified absent, everything else per §4.

Bring D-MOTO up first, let the group settle ~15 s, then D-POCO.

**PASS requires all four**, not just a session:

1. `hands-free service level connection established` = **1 or more**
2. D-POCO's `HeadsetStateMachine` for D-MOTO reaches **Connected**
3. `NO_HFP_FROM_HU_PRESENCE` = **0** for D-MOTO
4. `WirelessServer: Incoming connection detected` = **1 or more**, into a full SSL session

**Why all four and not just "a session formed".** A session can form on this rig with zero pokes
ever attempted, because the phone's own reconnect can beat ours (§7a). If that happens, a session is
not evidence the default reached the gate. Condition 1 is the one that cannot be faked: it only
prints when this build opened the exchange.

Also report, as the new instrument's first hardware outing: which `gets the stand-in HFP record`
variant printed on D-MOTO. That answers a question round 1 could not, see R4.

Run it **three times** if the first passes, to match round 1's 3/3 and make the comparison direct.
Two of three is still a PASS with the count stated.

### R2: the opt-out still beats the new default

Same as R1 but write `native-aa-complete-hfp-slc=false` and read it back verbatim.

**PASS:** `hands-free service level connection established` = **0**, `HFP responder active` = 0, and
the failure returns (`NO_HFP_FROM_HU_PRESENCE` non-zero, no session). That is round 1's R4 result
reproduced through the new gate rather than the old one.

A FAIL here means the setting no longer reaches the code that reads it, which would make the row
useless. Report it before R3.

### R3: real head unit regression, D-HU

Candidate on D-HU, phone D-POCO, key **absent** so it runs at the new default.

This arm matters more than it did in round 1, because the accepted-socket path changed and D-HU is
the device that could reach it.

**PASS:**

- `already advertises Hands-Free` = **1** (the skip branch, as in round 1), and therefore
  `gets the stand-in HFP record` = **0**
- `hands-free service level connection established` = **0** and `HFP responder active` = **0**, so
  the whole path stays inert on a real head unit even with the setting now on by default
- one clean session: `Connection accepted from` then `Incoming connection detected` then SSL, video
  running with `dropped=0`
- **`adapterForService(...) failed`** no longer appears at **E** level. Round 1's R5 caught it as an
  error; it is expected on a single-radio unit and is now DEBUG. Report the level it prints at.

### R4: harvest, and two greps that need no hardware

Per-capture counts for R1 to R3: `Successfully poked`, `HFP responder active`,
`hands-free service level connection established`, `Connection accepted from`,
`HFP connection accepted from`, `Incoming connection detected`, `SSL handshake complete`, and the
phone's `NO_HFP_FROM_HU_PRESENCE`.

Then two greps **over round 1's saved captures**, which answer open questions for free:

```bash
grep -c "could not read radio"       <round 1 D-MOTO captures>
grep -c "audio sink decoy accepted"  <round 1 D-MOTO captures>
```

- The first decides whether D-MOTO's reflective UUID read succeeded or fell through the unreadable
  branch. Round 1 ran at DEBUG so the line would have been captured if it fired. **Present** means
  registering on an unreadable adapter is load bearing and must never be inverted; **absent** means
  that branch is unexercised. Either answer is useful and neither is a FAIL.
- The second is the retrospective check on dropping the decoy. **0** means nothing ever dialled it
  and the removal is provably free. Non-zero means the removal is a behavioural change against the
  validated build and the PR has to say so.

If round 1's captures were not kept, say so and skip; do not re-run anything for these.

---

## 7. Not a verdict, but worth a line

The new settings row is at the bottom of the Native AA block, under Advanced, next to the handshake
toggle. House rules say the UI is never driven and settings go in `settings.xml`, so **do not test
it by tapping it**. If you happen to open the settings screen, report whether these two rows read
sensibly and whether the renamed one is clearer:

- **Finish the Bluetooth hands-free connection** (new)
- **Factory-style handshake** (renamed from "Modern handshake (version exchange)")

An observation, not a pass condition.

---

## 8. Report back

`hfp-profile-presence-round2-results.md` on this branch, in §7's format. The three numbers that
matter:

1. **R1's four conditions**, each with its count. Did the default alone produce a session, and did
   the SLC line print?
2. **R2's SLC count**, which should be 0, and whether the failure came back.
3. **R3's two counts on D-HU**: `already advertises Hands-Free` and
   `hands-free service level connection established`.

Plus R4's two greps if the round 1 captures survive.

**What would falsify the change:** R1 failing where round 1's explicitly written `true` succeeded.
That would mean the default is not reaching the gate, and the plumbing rather than the mechanism is
at fault. Say so plainly rather than reading it as the fix not working, because round 1 already
showed the mechanism does.
