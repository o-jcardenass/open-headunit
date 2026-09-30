# p2p-bringup-loop, round 1 brief: a regression check, because the defect itself is out of reach here

## 0. The honest limit, stated first

**The fault this branch fixes cannot be reproduced on this rig, and no run below tries.**

The defect is in the pre-Android-10 branch of `WifiDirectManager.createQuietGroup()`. This rig is
Android 14. On API 34 `WifiP2pOperatingChannelPolicy.appliesTo(34)` is false, `attemptChannels()`
returns an empty list, and every path in the `SDK_INT >= Q` block above it returns, so the channel
ladder and all three `WifiP2pChannelCompat` call sites are dead code here. The 2 s timeout that is
the actual fix can never execute on this hardware.

What this round asks is the other question: **the same change also gates every WiFi Direct bring-up
on Android 14, and this round is whether that gate broke anything.** Two of the three edits are
live on this rig:

- a new `P2pStateChangePolicy` decides whether a `WIFI_P2P_STATE_CHANGED_ACTION` broadcast starts a
  bring-up and whether it clears the group latches. Every mode-3 and helper-mode WiFi Direct
  bring-up goes through it.
- `ConnectionIssue.HOTSPOT_NOT_RUNNING`, new, raised on the Native AA hotspot transport. Reachable
  here and the only genuinely new *behaviour* the rig can exercise.

R6 is expected INCONCLUSIVE and is marked as such. That is a result, not a failure.

---

## 1. Build and baseline

| Arm | Build | SHA | Role |
|---|---|---|---|
| **A** | `origin/main` | `a211dd48` | baseline. Establishes this rig's own numbers. |
| **B** | `fork/fix/907-p2p-channel-request-wedge` | `ef5080cd` | candidate, two commits on A. Expected to match A everywhere except R6. |

```bash
git fetch origin && git fetch fork
git rev-parse origin/main                                  # a211dd48b75b6c318159dc94a8c8707ae58033e4
git rev-parse fork/fix/907-p2p-channel-request-wedge        # ef5080cd7986d9dfa1a10b7f5c9849e8ff6d8060
git log --oneline a211dd48..fork/fix/907-p2p-channel-request-wedge
#   ef5080cd Native AA hotspot: record that no access point is up, don't just log it
#   021a36f9 WiFi Direct: a channel request nobody answers must not wedge the group
```

No history was rewritten; a plain `checkout` is enough. Both carry
`applicationId com.andrerinas.headunitrevived`, so **batch by build, not by run**: two installs.

**Build gate.** `run_unit_tests.sh` must be green, and specifically:

- `P2pStateChangePolicyTest`: **8 tests, 0 failures**, new file this round.
- `ConnectionIssueBannerPolicyTest`: must contain, by name,
  `only the hotspot route can be blocked by there being no access point` and
  `no setting is a remedy for there being no access point`.

A red gate is an escalation, not a run.

---

## 2. What the defect was, so the runs make sense

Reporter #907, a Qualcomm sm6150 unit on **Android 9**. Below Android 10 the only way to request a
band is the hidden `WifiP2pManager.setWifiP2pChannels`. On that driver the call reloads the P2P
interface, so its `ActionListener` is never invoked. `standardCreateGroup()` is reachable only from
that callback, so `createGroup` was never called at all: **4127 attempts, zero successes, zero
failures, and no `p2p-wlan0-N` interface in the whole log.**

The reload also raises `WIFI_P2P_STATE` DISABLED then ENABLED a few milliseconds apart. The
receiver read that as the user toggling WiFi Direct, cleared `isGroupCreatingOrCreated`, and started
another bring-up, which reloaded the interface again, at **~45 iterations a second on the main
thread**, for 92 seconds, wiping `credentialsEpoch` each time. The handshake therefore reported
`SSID=<null>, IP=<null>` on all 8 of the phone's Bluetooth connections and never sent a WPP byte.
The loop also overran chatty and destroyed 81% of the reporter's own log.

The two edits that are live on Android 14:

- `P2pStateChangePolicy` tells our own interface reload from a real toggle using one timestamp,
  `lastP2pRequestAtMs`, stamped by `markP2pRequest()` at all 10 P2P calls on the bring-up path. The
  window is **2000 ms**.
- `legacyChannelAttempt` is no longer reset in `startNativeAaQuietHost()`. **Inert here**, because that
  field is only read on the pre-Q path.

**The regression risk is specific and worth naming**, because it is what R3 and R4 are for:

1. `shouldStartBringUp` now also refuses inside the 2 s window. If a bring-up legitimately fails
   fast and P2P bounces, the ENABLED that follows will not restart it. **R3.**
2. `shouldResetOnDisable` now declines to clear `isGroupCreatingOrCreated`, `isConnected`,
   `isClientConnected` and `nativeRecreateCount` inside that window. A genuine WiFi Direct
   disable landing within 2 s of one of our own P2P calls would leave the latch stuck true and no
   later broadcast would start a bring-up. **R4.**

---

## 3. What is different about this round

- **The premise above is falsifiable, and checking it is free.** On both arms, in every capture,
  these two must appear **zero** times:

  ```
  no band request below Android 10
  no answer in
  ```

  If either appears on this rig, the whole framing in §0 is wrong and that is the single most
  valuable thing this round can report. Grep for them once per capture and report the counts.
- **This rig is permanently joined to `Pegue Cdesta` (5500 MHz).** R5 depends on that being true.
  It is what makes `wlan0` a station rather than an access point. Verify before R6, do not assume:
  ```bash
  adb shell dumpsys wifi | grep -iE "mWifiInfo|SSID|Frequency" | head
  ```
- **Discard rule for the WiFi Direct runs is the standing one**: a *second* `createGroup SUCCESS`
  inside one run, not any sign of churn. A `p2p-wlan0-N` bump *before* the first success is a stale
  group being torn down at launch, and a lone `MATCH! Starting AapService` with no group churn is
  the phone's own Bluetooth reconnect. Neither is contamination.
- **R5 needs `connection-issue-dismissed-at` deleted**, not just the stamp cleared. A dismissal left
  by an earlier run silently suppresses the banner and the run reads as "no banner" with the logic
  working correctly.
- **The banner is refreshed on `onResume()` only.** Every "confirm the banner" step means
  force-stop and relaunch.

---

## 4. Settings keys this round needs

Written with the §1 template, app stopped, verified by reading `settings.xml` back before launching.

| Key | Type | Value | Meaning |
|---|---|---|---|
| `wifi-connection-mode` | int | `3` | Native AA. `2` for R5. |
| `native-ap-transport` | int | `0` | WiFi Direct. `1` for R5/R6. |
| `helper-connection-strategy` | int | `1` | WiFi Direct. R5 only; ignored in mode 3. |
| `wifi-direct-band` | int | `0` | Auto. Leave alone unless a run says otherwise. |
| `auto-enable-hotspot` | bool | `false` | R6. Keeps the run about the record, not about the enable. |
| `log-level` | int | `0` | VERBOSE. |
| `connection-issue-hotspot-off` | long | *delete* | R6 must raise it, not inherit it. |
| `connection-issue-dismissed-at` | long | *delete* | see §3. |

`settings.xml` carries the previous thread's non-defaults. Read it once at the start of the round
and report anything unexpected in Setup notes.

---

## 5. The lines that decide every run

Verified with `grep -F` against `ef5080cd`. All are `AppLog.i`/`.w`/`.e`, so INFO carries them;
`log-level=0` is asked for anyway.

```
WifiDirectManager: WIFI_P2P_STATE_CHANGED_ACTION state=
WifiDirectManager: P2P enabled, auto-starting Native AA quiet host
WifiDirectManager: P2P enabled, auto-starting WiFi Direct visibility
WifiDirectManager: Attempting createGroup for Native AA (Attempt
WifiDirectManager: Standard createGroup SUCCESS!
createGroup SUCCESS!
WifiDirectManager: SUCCESS - Providing credentials
WirelessServer: Incoming connection detected
SoftApCredentials: No interface looks like an access point
SoftApCredentials: No usable access point after
MainActivity: showing the connection issue banner for
```

Counting commands:

```bash
grep -ac "createGroup SUCCESS"                            capture.txt   # >1 in one run is the discard
grep -ac "auto-starting Native AA quiet host"             capture.txt
grep -ac "Attempting createGroup for Native AA"           capture.txt
grep -ac "WIFI_P2P_STATE_CHANGED_ACTION"                  capture.txt
grep -ao  "p2p-wlan0-[0-9]*"                              capture.txt | sort -u
grep -ac "no band request below Android 10"               capture.txt   # premise check, expect 0
grep -ac "no answer in "                                  capture.txt   # premise check, expect 0
```

---

## 6. Runs

Arm A first, all its runs, then swap to arm B.

### R1: Native AA over WiFi Direct still connects (arm A and arm B)

**The point of the round for commit 1.** Mode 3, `native-ap-transport=0`. One clean connect to the
phone, projection up, then a normal user exit.

- **PASS**: exactly **1** `createGroup SUCCESS`; `SUCCESS - Providing credentials` present;
  `WirelessServer: Incoming connection detected` present; projection renders.
- **FAIL**: no group, no credentials, or ≥2 `createGroup SUCCESS`.
- **Also from this capture, both arms**: the two premise counts from §3. Expect `0` and `0`.

**What a PASS proves, and what it does not.** Arm A is expected to pass this too. On Android 14 the
fixed path is unreachable, so a green R1 on arm B is a *regression guard*, not evidence the fix
works. What decides it is the comparison with arm A, so **report the four counts from §5 for both
arms**, not just the verdict. A per-run delta of more than one in
`auto-starting Native AA quiet host` or `Attempting createGroup` is the finding.

### R2: repeated bring-ups are not swallowed by the 2 s gate (arm A and arm B)

**Five** connect / user-exit cycles back to back, as fast as the rig allows. This is regression
risk 1: a cycle whose bring-up is refused because the previous one stamped `lastP2pRequestAtMs`
within 2 s would show as a cycle that never forms a group.

- **PASS**: 5 cycles produce 5 groups. Every `state=2` broadcast that arrives with no group up is
  followed by an `auto-starting Native AA quiet host` within one second, **or** by an
  `Attempting createGroup` already in flight from the cycle before.
- **FAIL**: any cycle where the phone is present, no group exists, and no bring-up is attempted.

Report `auto-starting Native AA quiet host` and `createGroup SUCCESS` counts per arm. Arm A gives
the number a healthy rig produces; a **lower** count on arm B with a **lower** group count is the
regression. A lower count on arm B with the *same* group count is the fix working as intended and
is a PASS.

### R3: a real WiFi toggle still clears the latches (arm A and arm B)

**The highest-value run in the round**, and regression risk 2. With Native AA armed and no session:
disable the head unit's WiFi, wait 10 s, re-enable it, wait 60 s.

```bash
adb shell svc wifi disable ; sleep 10 ; adb shell svc wifi enable
```

- **PASS**: after WiFi returns, `P2P enabled, auto-starting Native AA quiet host` appears and a
  group forms.
- **FAIL**: the ENABLED broadcast arrives and no bring-up follows within 30 s. That is the latch
  stuck true, and it is a shipping blocker.

**Rig caveat, said up front:** `svc wifi enable` does not reliably bring the station radio back on
this unit. If WiFi does not return, retry once; if it still does not, this run is **INCONCLUSIVE**,
not FAIL, and say which it was. The signal we need is the `WIFI_P2P_STATE_CHANGED_ACTION state=2`
line, so if that appears the run is valid whether or not the station reassociates.

### R4: helper mode WiFi Direct is still made visible (arm B only)

`P2pStateChangePolicy.shouldStartBringUp` also gates `makeVisible()`. Mode 2,
`helper-connection-strategy=1`. Launch and leave for 60 s; no phone needed.

- **PASS**: `P2P enabled, auto-starting WiFi Direct visibility` appears at least once.
- **FAIL**: it appears zero times with `state=2` broadcasts present in the capture.

Arm A comparison is not needed unless this FAILs.

### R5: the hotspot record is raised, repeated, and shown (arm A and arm B)

**The one new reachable behaviour.** Mode 3, `native-ap-transport=1`, `auto-enable-hotspot=false`,
head unit hotspot **off**, `connection-issue-hotspot-off` and `connection-issue-dismissed-at` both
deleted. Launch, leave for **3 minutes** with the phone present so the handshake keeps asking, then
force-stop and relaunch to the main screen.

- **PASS (arm B)**: `SoftApCredentials: No usable access point after` appears **≥ 2** times in the
  3-minute window (this is the 60 s cooldown; once only means the cooldown did not work);
  `connection-issue-hotspot-off` is a non-zero long in `settings.xml` after the run; and after the
  relaunch `MainActivity: showing the connection issue banner for HOTSPOT_NOT_RUNNING` appears and
  the banner is on screen.
- **Expected on arm A**: the line appears **exactly once**, no `connection-issue-hotspot-off` key,
  no banner. That difference *is* the result. Record arm A's count, it is the control.
- **FAIL (arm B)**: the line appears once and only once, or the key is absent, or no banner.

Pair the count with the proof the path was reachable: quote one
`No interface looks like an access point` line with its interface list, so a zero count can be told
from "the AP existed and the branch never ran".

### R6: the record retires when an access point does come up (arm B, expected INCONCLUSIVE)

Continue from R5. Bring the head unit's own hotspot up by whatever means the rig actually has, then
relaunch.

- **PASS**: `connection-issue-hotspot-off` returns to `0` and no banner.
- **INCONCLUSIVE**: this rig cannot raise a usable SoftAP, which prior threads have found more than
  once. **Do not spend the round on it.** One attempt, then mark it and move on; the retire path is
  covered by the JVM tests.

---

## 7. Do not re-run

- Anything about the pre-Android-10 channel ladder or `setWifiP2pChannels`. Unreachable here, and
  §3's premise check is the only coverage this rig can give it.
- The #907 mechanism itself. It is settled from the reporter's logs and does not need reproducing.
- `WifiP2pOperatingChannelPolicy` band and channel selection. Unchanged this round and covered by
  `WifiP2pOperatingChannelPolicyTest`.

---

## 8. Report back

Beyond the per-run verdicts, these decide whether this ships:

1. **`createGroup SUCCESS` and `auto-starting Native AA quiet host` counts, arm A vs arm B, per
   run.** Same group count with an equal or lower bring-up count is the answer we want.
2. **R3's verdict.** A stuck latch is a blocker; an INCONCLUSIVE from the rig's `svc wifi` quirk is
   not, but say which.
3. **R5's arm A vs arm B line counts (expect 1 vs ≥2) and whether the banner appeared.**
4. **The two premise-check counts.** Anything but zero rewrites this brief.
