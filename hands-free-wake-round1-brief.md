# hands-free-wake, round 1 brief: waking a phone whose Bluetooth never disconnects

## 1. Build and baseline

| | Branch | SHA | Commits | Gate |
|---|---|---|---|---|
| Candidate | `fix/audio-sink-and-wireless-bring-up` | `e9c97fd8` | 5 on `main` | 2052 tests, 0 failures |
| Baseline | same branch, new setting off | | | |

```bash
git fetch fork
git checkout -B hands-free-wake fork/fix/audio-sink-and-wireless-bring-up
./gradlew :app:testGithubDebugUnitTest      # 2052, 0 failures
```

**The branch was compacted to five commits on 2026-09-17**, so what is on `fork` is a rewrite of
what was there before. Reset any older local copy of it rather than pulling it.

The baseline arm is the **same APK** with `native-aa-wake-over-hands-free-link` set to `false`, so
no second build is needed.

**This APK also carries the schema corrections** (the `AAP:` commit), folded in after the brief was
first written so the tester builds once. Most of it is renames with no wire effect; two asks sit behind
**Native AA wireless version exchange** and **Ask for a longer link timeout**, and **both ship off**.
**Leave both off in every run here.** They are graded by their own round
(`proto-schema-corrections-round1-brief.md`) and nothing in this one depends on them. If either is
found on, the run records the setting and grades UNTESTABLE rather than FAIL.

## 2. What this round is

A reporter produced the first fully deterministic reproduction of "Native AA connects once, then
never again until Bluetooth is reset". Their unit's radio auto-connects hands-free to the phone and
holds it across the session and past its end.

The mechanism is settled from the phone side by static analysis and does not need measuring here:
Android Auto's `WifiBluetoothReceiver` starts wireless setup on a Bluetooth **event**, filtering
`ACL_CONNECTED` plus the HFP and A2DP `CONNECTION_STATE_CHANGED`. A link that never changes raises
none, so the phone never re-triggers. Our wake poke is the only thing that could raise one, and the
hands-free guard refused it 17 times in their capture with zero pokes sent.

**What this round measures is whether the escalated wake actually produces a reconnect, and what it
costs.** The code change lets the stand-down yield after 90 s, at most twice per arming, 60 s apart,
and only for a phone that has already run Android Auto on the unit.

**The open question this round exists to settle.** Android Auto carries a flag
`WirelessProjectionInGearhead__filter_profile_connection_by_acl` whose default is unknown. If it is
on, the phone may discard a profile event raised on an ACL that was already up, and a profile-level
wake cannot work at all. H2 is what answers it. **If H2 fails, do not iterate on the wake; the next
lever is an adapter cycle and that is a different design.**

Rig: **D-SAM as head unit, D-POCO as the phone.** Run order: **R0, H0, H1, H2, H3, H4.**

## 3. The precondition, which the rig does not produce on its own

Every run here needs D-POCO holding a live hands-free link to D-SAM **with no session running**.
That state does not occur naturally on this rig: the wake poke's socket is closed at handoff, and a
projecting phone drops its ACL seconds after the WiFi handoff and restores it only when the session
ends.

**The lever is D-SAM's own Bluetooth adapter, not D-POCO's.** Cycle the head unit's adapter, wait
up to 15 s, then verify on D-POCO before every run:

```bash
adb -s <D-POCO> shell dumpsys bluetooth_manager | grep -A 5 "HeadsetService"
# want: the per-device StateMachine for D-SAM's MAC reading mCurrentState=Connected
```

**Verify it immediately before the step that needs it, never assume it.** A run that starts without
a confirmed `Connected` grades nothing and must be recorded UNTESTABLE rather than FAIL.

## 4. Settings keys

```xml
<int name="log-level" value="0" />                                        <!-- VERBOSE, every run -->
<int name="wifi-connection-mode" value="3" />                             <!-- Native AA -->
<boolean name="native-aa-wake-over-hands-free-link" value="true" />       <!-- false for H1 only -->
```

Write settings through the app, not by editing `shared_prefs` directly: the rig's `shared_prefs` is
root-owned and direct writes do not land. `video-profile-starvation-cap` is still a latch; read it
back before and after every run.

## 5. Runs

### R0. Gate

**PASS:** `./gradlew :app:testGithubDebugUnitTest` reads **2034** tests, 0 failures, and the installed
APK's md5 matches the one just built. Hash with a real `adb pull` plus `md5sum`; `adb shell cat` piped
to `md5sum` is not a valid identity check.

---

### H0. Reproduce the reporter's failure on this rig

**Baseline arm: `native-aa-wake-over-hands-free-link` = `false`.**

This is the run that says the rig can show the fault at all. If it cannot, everything after it is
ungraded and the round stops here.

1. Bring up a full Native AA session, D-POCO projecting.
2. End it from the head unit (user exit).
3. Establish the precondition in §3 and confirm `mCurrentState=Connected`.
4. Leave the app armed for **5 minutes**, capturing throughout.

**PASS (fault reproduced):** zero `NativeAA: Connection accepted from`, and repeated
`NativeAA: Not poking ... already holds a Bluetooth hands-free link to it`. Report the refusal count.

**If instead the phone reconnects on its own**, the rig does not hold the precondition the reporter's
unit holds. Record H0 as NOT-REPRODUCED, run H4 anyway, and stop. Say so plainly in the results; it
is a real outcome, not a failed run.

---

### H1. The setting off changes nothing

**Baseline arm, and cheap. Confirms the switch actually gates the behaviour.**

Repeat H0 exactly. **PASS:** identical shape to H0, and **no** `waking ... despite the hands-free
link` line anywhere. A wake going out with the setting off is a FAIL and voids H2.

---

### H2. The escalated wake reconnects the phone

**Candidate arm: setting `true`. This is the run the round exists for.**

Repeat H0's sequence, capturing for **5 minutes** from the moment the stand-down starts.

**PASS:**

- `NativeAA: waking <phone> ... despite the hands-free link` appears, the first one **not before 90 s**
  after the first `Not poking` line. Report that interval.
- `NativeAA: Calling socket.connect()` follows it. Its absence means the wake never reached the radio
  and nothing downstream is graded.
- `NativeAA: Connection accepted from <phone>` follows within about 60 s of a wake, and the session
  goes on to `WirelessServer: Incoming connection detected` and `SSL handshake complete`.
- **At most 2** `waking ... despite` lines in the whole run, at least 60 s apart. A third is a FAIL of
  the budget even if the session formed.

**FAIL:** wakes go out, `socket.connect()` succeeds, and no `Connection accepted from` follows either
of them. That is the `filter_profile_connection_by_acl` case. Record it as such, and H3 still runs.

---

### H3. What the wake costs the hands-free link

**Runs on H2's capture; no separate bring-up.** This is the run that decides whether the default
stays on.

On D-POCO, before the wake and then every 15 s for 3 minutes after it:

```bash
adb -s <D-POCO> shell dumpsys bluetooth_manager | grep -A 5 "HeadsetService"
```

Report: whether the link dropped at all, how long it stayed down, and whether it came back **without
anyone touching it**.

**PASS:** the link either never drops or is back within 60 s unaided.

**FAIL:** it is still down at 3 minutes. That reproduces the 3-to-8-minute outage measured on an
earlier rig round, and it means the setting must ship **default off** whatever H2 said.

---

### H4. A healthy first connect is not disturbed

**Candidate arm. The guard against the fix firing where nothing is wrong.**

From a cold app start with **no** prior session in this process, bring up one ordinary Native AA
session with D-POCO.

**PASS:** no `waking ... despite the hands-free link` line anywhere, because the phone has not opened
the Android Auto channel on this arming. Session forms normally; report launch to
`Incoming connection detected`.

**FAIL:** any escalated wake on a first connect. That would mean the app disturbs the hands-free link
of a phone paired only for calls, which is the one thing this design promised not to do.

Also confirm in the same capture: **at most one** `recreate attempt` while the phone has not dialled,
which grades the join-watchdog change.

## 6. Phone-side capture, worth more than anything else here

If `adb logcat` on D-POCO can be run during H2, grep for:

```
WIRELESS_SETUP_SHARED_HFP_CONNECTING
WIRELESS_SETUP_SHARED_ACTION_ACL_CONNECTED
WIRELESS_SETUP_FAILED_TO_START_NO_HFP_FROM_HU_PRESENCE
WIRELESS_SETUP_CANCELLED_HU_NOT_CONNECTED
```

`..._HFP_CONNECTING` appearing right after our wake settles the mechanism outright and makes H2's
verdict causal rather than correlational. `..._CANCELLED_HU_NOT_CONNECTED` would mean our wake
cancelled a setup that was already in flight, which is a harm worth knowing about.
