# native-aa-recovery-identity-and-speed: round 1 brief

Three fixes on one branch, none of them measured on hardware. Two are recoveries from states a
reporter reached and could not get out of; the third makes an ordinary Native AA connection form
faster. The branch compiles and its unit suite passes at 1450 / 0; what no round has scored yet is
any of the three behaving as intended on a unit.

This file is append-only. Corrections arrive as new commits and a `git pull` fast-forwards, so no
brief you have already read changes under you.

Read `TESTING-TEMPLATE.md` first. This brief only says what is specific to this round.

---

## 1. Build and baseline

```bash
git fetch fork
git checkout fix/native-aa-recovery-identity-and-speed   # b5617bd5
```

| Input | SHA | Role |
|---|---|---|
| `fix/native-aa-recovery-identity-and-speed` | `b5617bd5` | **the candidate.** Three commits on `main` |
| `origin/main` | `12706e26` | **the baseline.** The candidate's own base |

The three commits, and the run that scores each:

| SHA | Subject | Scored by |
|---|---|---|
| `97430ddf` | WiFi Direct and Native AA: recover a wedged create and a stopped poke | R7, R8 |
| `03776bf6` | WiFi Direct: a new network identity goes on the air on every API level | R9 |
| `b5617bd5` | Native AA: connect faster by not paying waits the bring-up never needed | R1 to R6 |

**Unit gate: 1450 / 0** (`testGithubDebugUnitTest`) on the candidate; **1414 / 0** on the baseline.
The 36 new cases reconcile exactly:

| Class | Cases |
|---|---|
| `P2pCreateWedgePolicyTest` | 8 |
| `P2pIdentityRotationPolicyTest` | 10 |
| `GroupIpResolutionPolicyTest` | 4 |
| `StationStandDownSettlePolicyTest` | 5 |
| `EarlyWakePolicyTest` | 9 |

Any other count means the wrong tree was built; stop and say so rather than running the round.

**No history was rewritten after this brief was written.** If `b5617bd5` does not resolve, say so
and stop; do not run the round against a branch tip you had to guess at.

Identity check, in order of strength:

1. Build stamp. `ACTION_QUERY_STATE` reports `"commit":"b5617bd5...."`. This is the only check that
   discriminates the two arms on its own.
2. DEX symbols. These six classes exist on the candidate and **none** exist on `main`:
   `P2pCreateWedgePolicy`, `P2pIdentityRotationPolicy`, `P2pPersistentGroupPurge`,
   `GroupIpResolutionPolicy`, `StationStandDownSettlePolicy`, `EarlyWakePolicy`.
3. APK md5, recorded for both arms.

## 2. What the three fixes are

### a. A group create the platform accepted but never finished

A `createGroup` the framework accepts puts it in a state where every later `createGroup` **and**
`removeGroup` answers a blanket BUSY for two full minutes. The retry ladder is guaranteed futile
inside that window, and the terminal rung used to raise a "WiFi Direct group refused" banner on a
unit that hosts groups perfectly well. `cancelConnect` is the one call that state accepts.

The fix spends one cancel per stuck create at each of the three BUSY sites and retries at the same
rung. `CREATE_STALL_FLOOR_MS` is 8 s, well above a healthy create and well below the 20 s the
group-info retry loop spends, so a create that is merely slow is never cut off.

**This is hard to provoke deliberately and R7 does not try.** It is a watch item across the whole
round: if a BUSY appears in any capture, the cancel line must appear with it.

### b. A poke button that could not repair anything

When the handshake manager fails to start, the reason is logged once at arming time and has rotated
out of the buffer long before the user presses anything. Every later repair returned on the same
flag, in silence: no Android Auto listener, no service record, and a wake that still logged
`Successfully poked` because the poke path reads no manager state at all. The capture reads like a
healthy wake that the phone ignored.

The fix caches why `start()` gave up, splits the button's branch on a new "was it ever started"
question so it can start a manager that never ran, and makes the silent exit say so.

Arming with **Bluetooth off** is the way into that state, and R8 uses it.

### c. Connecting faster

Four fixed waits, none of them measured when they were written:

- The credentials waited for the group interface to report an IP, up to fifteen one-second reads,
  and then fell back to `192.168.49.1` anyway. A P2P group owner is always that address. It now
  reads once as owner and delivers.
- Group info was asked for a second after the create succeeded. The null case is already covered by
  a twenty-attempt retry, so the wait only ever cost a second it could not save. It is asked for now.
- The station stand-down window was a flat 1.5 s post. The same supplicant read that verified it
  afterwards now answers early, and 1.5 s becomes a ceiling instead of a cost.
- **The largest one:** the wake poke only ever started when the credentials arrived, so the phone
  was not woken until the group was up, its info had returned and its IP had resolved. The handshake
  does not need that ordering. It sends the version exchange first and waits for credentials
  afterwards, so the phone's own wake latency can run alongside the group forming. The wake now
  starts as soon as the Bluetooth listeners are open.

The poke job's own 2 s entry delay is now a wait for the listeners, bounded at the same 2 s.

## 3. Driving the app

Standard automation surface, as in every round:

```bash
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
send() { adb shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit."$@"; }
```

**One run needs a real tap and cannot be scripted.** The new-identity row sends
`ACTION_ROTATE_WIFI_DIRECT_IDENTITY` to `AapService`, which is `android:exported="false"`, so it is
reachable only through `onStartCommand`. `am broadcast` returns `result=0` with no receiver, and
`am start-service` from uid 2000 is refused. R9 is therefore a screen tap:

**Settings, WiFi Direct section, the row "New WiFi Direct network identity".** It raises a
confirmation dialog; the positive button is the action. Report the row's subtitle before and after.

If this rig has root, `su -c 'am start-service -n ...'` is an alternative; say in Setup notes which
one you used.

## 4. Settings keys

| Key | Element | Note |
|---|---|---|
| `wifi-connection-mode` | `<int name="wifi-connection-mode" value="3" />` | Native AA, every run |
| `native-ap-transport` | `<int name="native-ap-transport" value="0" />` | WiFi Direct, not the hotspot. Every run |
| `native-driver-selection-mode` | `<int name="native-driver-selection-mode" value="0" />` | every run. Restore the rig's `2` afterwards |
| `log-level` | `<int name="log-level" value="0" />` | **VERBOSE, every run.** Three decisive lines are DEBUG |
| `native-poke-bt-macs` | StringSet, the phone's MAC alone | every run, so the loop pokes only it |
| `wifi-5ghz-channel` | `<int name="wifi-5ghz-channel" value="0" />` | automatic, every run |
| `wifi-direct-stable-identity` | `<boolean name="wifi-direct-stable-identity" value="true" />` | R9 |

`log-level` stores the **ordinal**, not an Android priority: `0` is VERBOSE, `1` DEBUG, `2` INFO.
This round needs `0` throughout, which is the opposite of some earlier rounds' INFO runs.

`native-poke-bt-macs` is a StringSet and needs its own element-scoped edit; it also seeds itself
from the auto-start list the first time it is read and writes that back, so read it back **after** a
launch. Never leave a `settings.xml.bak` beside the file.

## 5. The lines that decide every run

Verified with `git grep -F` against `b5617bd5`. Each appears in exactly one file under
`app/src/main/java`.

New on the candidate, absent from `main`:

```
WifiLauncherNative: creating the group
NativeAA: waking the phone while the WiFi group is still forming.
NativeAA: wake poke starting (listeners ready after
NativeAA: a wake poke is already running for these credentials
WifiDirectManager: the stuck group creation was cancelled; asking for a group again.
WifiDirectManager: cancelling the stuck group creation was refused
NativeAA: the Android Auto listeners cannot be reopened because the handshake
AapService: the Native AA handshake servers are not running
NativeAA: not waiting for credentials, because the handshake servers are not running.
WifiDirectManager: a new WiFi Direct identity was asked for now
WifiDirectManager: persistent profile purge:
AapService: the new WiFi Direct identity waits for the next create:
```

Present on both arms, and used for ordering and counts:

```
AapService creating
StationStandDown: this unit has left its WiFi network.
WifiDirectManager: 5GHz createGroup SUCCESS!
WifiDirectManager: SUCCESS - Providing credentials to listener.
WifiDirectManager: Waiting for IP on interface
NativeAA: Calling socket.connect() for
NativeAA: ACTIVELY LISTENING on Android Auto UUID
NativeAA: [TX] Wrote TYPE 3
WirelessServer: Incoming connection detected from
Handshake: SSL handshake complete
```

The wedge line is a format string; grep the fixed part:

```
WifiDirectManager: a group this unit accepted
```

and expect the shape `a group this unit accepted <N>ms ago never formed, and it refuses every new
one until it gives up on that by itself (120s). Cancelling it instead of waiting.`

## 6. The runs

Both arms for R1 to R6. Candidate only for R7 to R9, with the baseline's behaviour named as the
control where the brief says so.

### R0 - build gate

Test count, both APK md5s, the build stamp, the six DEX symbols. **Stop the round if the count is
not 1450 / 0 on the candidate.**

### R1 - an ordinary Native AA session still forms, and how long it takes

**Three runs per arm, alternating.** This is both the regression guard and the headline number.

- Precondition: phone's radios on and settled, head unit force-stopped, one clean launch.
- Report per run, not averaged: `AapService creating` to `Handshake: SSL handshake complete`.
- Also report `AapService creating` to `WirelessServer: Incoming connection detected from`, which
  isolates our half from the phone's TLS.

PASS is a session on all six runs. The timing is a **measurement, not a criterion**: report it even
if the candidate is slower, and do not retry to get a better number.

Earlier rounds put this at about 21 s on this rig against a different base. Treat that as the order
of magnitude, not as the baseline; this round measures its own.

### R2 - the wake now happens while the group forms

**Candidate only; the baseline is the control and must show the opposite.**

In the candidate capture, the first `NativeAA: Calling socket.connect() for` must appear **before**
the first `WifiDirectManager: SUCCESS - Providing credentials to listener.`

`NativeAA: waking the phone while the WiFi group is still forming.` must appear exactly once per
bring-up.

In the baseline capture, the connect must come **after** the first providing-credentials line. Both
orderings are a reported result; report the gap in seconds each way.

### R3 - the early wake is not thrown away when credentials land

Candidate. Count `NativeAA: wake poke starting (listeners ready after` per bring-up: expect
**exactly one**. More than one means the credential delivery restarted the loop and the head start
was lost.

`NativeAA: a wake poke is already running for these credentials` is the line that shows the
restart being refused. It is DEBUG, hence the VERBOSE setting.

### R4 - the stand-down still produces exactly one group

Candidate. This is the run most able to reopen a known bug, so it is the one to read carefully.

- `WifiLauncherNative: creating the group <N>ms after the stand-down (still joined=<x>)` appears
  **once**, and **report N**. If the station leaves quickly, N is well under 1500.
- `StationStandDown: this unit has left its WiFi network.` present.
- `createGroup SUCCESS` = **1** per bring-up, and one `p2p-wlan0-N` index.

Two group-create chains in one bring-up is a **FAIL**, not a discard.

### R5 - the credentials still name the live group

Candidate. `NativeAA: [TX] Wrote TYPE 3` must name the SSID of the **most recent**
`SUCCESS - Providing credentials to listener.` line above it. Quote both with timestamps.

### R6 - the IP wait is no longer paid

Candidate, as group owner: `WifiDirectManager: Waiting for IP on interface` = **0**.
On the baseline, report whatever it is; it is often 0 there too, and that is fine. A non-zero count
on the candidate means the owner branch was not taken and is worth reporting.

### R7 - the create wedge, as a watch item

Not provoked deliberately. Across every capture in the round, on both arms:

- Count `a group this unit accepted`. If it is 0, report "not exercised". That is an acceptable
  outcome and not a failure of the round.
- If it is non-zero on the candidate, one of `the stuck group creation was cancelled` or
  `cancelling the stuck group creation was refused` must follow it. A wedge line with neither is a
  **FAIL**.
- If `createGroup ... BUSY` appears on the **baseline** and no group forms for about two minutes,
  that is the defect reproducing and is a valuable result. Say so.

### R8 - the poke button can start servers that never started

Candidate, with the baseline as the control. This is the round's other point.

1. Force-stop the app. `settings.xml` already has mode 3.
2. **Turn the head unit's Bluetooth off** (`svc bluetooth disable`; confirm with
   `dumpsys bluetooth_manager`, not `settings get global bluetooth_on`).
3. Launch the app. The handshake manager will fail to start.
4. Turn Bluetooth back on and let it settle.
5. Tap the home screen WiFi button once.

**Candidate PASS:** the capture carries `AapService: the Native AA handshake servers are not
running (...), so nothing could answer the phone. Starting them before the poke.` and then
`NativeAA: ACTIVELY LISTENING on Android Auto UUID`. The parenthesised reason should name Bluetooth
being off when the mode was armed.

**Baseline control:** the tap produces the reopening line and then nothing, with no
`ACTIVELY LISTENING` after it, while a poke may still log `Successfully poked`. Confirm that shape;
it is what makes the fix worth having.

Report whether a session then formed. That is informative but is not the criterion.

### R9 - a new network identity reaches the air

Candidate. **Needs a tap, see section 3.**

This rig is Android 14, so the branch takes its API 29 and above path: the app names the group and
mints a new name and passphrase. The legacy path below API 29, which purges the platform's stored
profile because the app cannot name a group there, is **UNTESTABLE on this rig** and must be
reported as such rather than guessed at.

1. Form a Native AA session, then exit it, so a group of ours exists.
2. Note the current SSID from the most recent `SUCCESS - Providing credentials to listener.`
3. Tap the row, confirm the dialog. Record the toast text and the row's subtitle.
4. Watch for `WifiDirectManager: a new WiFi Direct identity was asked for now (...)`, or
   `AapService: the new WiFi Direct identity waits for the next create: <reason>` when it defers.
5. Connect again and read the SSID off the next providing-credentials line.

**PASS:** the SSID after differs from the SSID before, and the phone forms a session on the new
network. If the app deferred, the reason line must name why, and the next create must carry the new
identity.

Also report `WifiDirectManager: persistent profile purge:` if it appears at all. On Android 14 it
should not.

## 7. Discard rules, with one exception this round

The standing rules apply: `MATCH! Starting AapService`, a second `createGroup SUCCESS` where one
group was expected, a bump in `p2p-wlan0-N`, `Magic Garbage detected in header`, or a second
`SSL handshake complete`.

**The exception:** a second create that follows
`WifiDirectManager: the stuck group creation was cancelled; asking for a group again.` is the fix
working, not churn. Check for that line before discarding a capture with two creates. R4 is the one
run where two creates is a FAIL regardless, because it has nothing to do with the wedge.

R8 deliberately runs with Bluetooth off for part of its capture and forms no session in that window.
That is not a discard.

## 8. Numbers to report

1. R1: six timings, three per arm, `AapService creating` to `SSL handshake complete`, and the same
   six to `Incoming connection detected`.
2. R2: the gap in seconds between the first `Calling socket.connect() for` and the first
   `SUCCESS - Providing credentials`, signed, on both arms.
3. R3: the count of `wake poke starting (listeners ready after` per bring-up, and the N in it.
4. R4: the N in `creating the group <N>ms after the stand-down`, per bring-up.
5. R6: `Waiting for IP on interface` count, both arms.
6. R7: `a group this unit accepted` count across the whole round, both arms.
7. R8: whether `ACTIVELY LISTENING` follows the tap, on each arm.
8. R9: SSID before and after, and the toast text.

## 9. Anything the brief did not ask about

The usual section, and this round has more room for it than most: no round has scored any of these
three fixes on a unit, so anything the app says that this brief did not predict is worth writing
down.
