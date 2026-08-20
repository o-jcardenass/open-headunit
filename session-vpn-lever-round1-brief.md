# session-vpn-lever — round 1 brief

## 1. Build and baseline

| | |
|---|---|
| Branch | `fix/session-lifecycle-and-diagnostics` |
| SHA | `6bd336ed` |
| Base | `main` @ `562c8dcf` |
| History rewritten? | **Yes, twice, and the branch was renamed.** It was `fix/keep-dummy-vpn-during-session`, which no longer exists on the fork. Its four media-gap commits were rebased onto the new `main`, so `f48baee7` and its parents are **not** ancestors of this branch. Fetch and reset; do not pull. |

```bash
git fetch fork --prune
git checkout -B session-vpn-lever fork/fix/session-lifecycle-and-diagnostics
git log --oneline -7          # expect 6bd336ed at the tip
```

Seven commits on top of `main`: the four from `media-gap-instrument` round 2 (unchanged content,
new SHAs), two new VPN commits, and the USB-quiesce commit moved here from another branch.

**Nothing on this branch has ever been compiled.** Not the VPN work, not the quiesce commit in this
context, and above all not the merge conflict resolved between them in `onConnected()` — that hunk
has existed for minutes and only on paper. R0 is a real gate, not a formality.

## 2. What this is and why it exists

Two reporters, different hardware, independently found that the app's dummy VPN being active makes a
periodic audio and video stutter go quiet. One of them also reported the VPN icon vanishing on their
second connection, with the stutter returning. Their log has the mechanism outright:

```
03:28:00.278  AapService.initWifiMode | AapService: Initializing WiFi Mode: 3 (Strategy: 2)
03:28:00.281  VpnControl.stopVpn      | VpnControl: Stopping DummyVpnService (GitHub Build)
```

Three milliseconds. `stopWirelessServer()` ended with `VpnControl.stopVpn(this)`, and
`initWifiMode()` calls that on every mode change, so the wireless server was taking down a VPN it
never owned. `DummyVpnPolicy` now holds the ownership rules and there is deliberately no reason code
for a wireless re-init.

The new setting `keep-dummy-vpn-during-session` is the lever itself: it brings the dummy VPN up for
an ordinary Native AA session, not only for offline Self Mode, so the thing both reporters stumbled
into can be turned on deliberately.

**Why it works is a theory, not a measurement.** The setting's own description says "so the head
unit's WiFi chip stops looking for other networks". Nothing has measured that. This round is not
being asked to confirm it — see §3.

## 3. What is different about this round

**This round cannot test whether the VPN fixes the stutter, and is not asked to.** The
`link-stall-periodic-scan` round 5 established that this UNISOC/Android-14 rig cannot carry the
reporters' fault at all on timescale grounds. What is being tested is that the lever exists, comes
up, stays up, and breaks nothing.

Three things are **UNTESTABLE here**, stated up front so no one spends the round on them:

- **The USB half of the quiesce commit.** §7a: no USB accessory path on this rig, Native AA wireless
  is the only transport. Its hardware coverage is impossible; `UsbSessionQuiescePolicyTest` in R0 is
  the whole of it. What R4 *does* cover is the other side of that commit — that a **wireless**
  session still takes its WiFi lock and still keeps its group.
- **The Self Mode VPN path and its 120 s watchdog.** `HomeFragment.startSelfMode()` only starts the
  VPN when `activeNetwork == null`, and §7a records this rig as permanently joined to `Pegue Cdesta`.
  It will never take that branch, and changing the rig's association has never been authorized.
- **The out-of-mode gate.** Proving no VPN starts on USB or Headunit Server needs a transport this
  rig does not have. Routed to the JVM test `sessionVpnNeverStartsOutsideNativeWireless` in R0.

**The consent problem is the round's real risk.** `VpnService.prepare()` normally needs a tap, and
writing `keep-dummy-vpn-during-session=true` into `settings.xml` bypasses the settings screen that
would have raised the dialog. R1 exists to settle that before anything depends on it. The app
diagnoses this itself: if consent is missing it logs a WARN naming the problem rather than failing
silently.

## 4. Settings keys this round needs

Per §7a, diff `settings.xml` against a fresh backup first and state the delta in Setup notes, even if
zero. Write with `hur-wifi-test-scripts/set_pref.sh`, app stopped.

| Key | Type | Value | Why |
|---|---|---|---|
| `log-level` | int | `2` | INFO. Every line below prints at INFO or above. |
| `wifi-connection-mode` | int | `3` | Native AA. The toggle only acts in this mode. |
| `view-mode` | int | `1` | TextureView, so the `queueBuffer` buckets exist |
| `enable-audio-sink` | bool | `true` | media-gap round 1 lost a run to this being `false` |
| `debug-video-fault-injection` | int | `0` | no injection this round |
| `keep-dummy-vpn-during-session` | bool | `true` for R1-R3, `false` for R4 | the lever |

## 5. The lines that decide every run

All verified with `grep -F` against `6bd336ed`. Remember `grep -a`, always (§7a).

| Grep for | Source | Means |
|---|---|---|
| `dummy VPN requested (owner=` | `AapService.kt:1853` | we asked for the tun, and for whom |
| `tun established (excludeSelf=` | `DummyVpnService.kt:64` | it actually came up, and in which mode |
| `prepared VPN app` | `AapService.kt` WARN | **consent is missing.** The self-diagnosis in §3 |
| `releasing the dummy VPN (owner=` | `AapService.kt:1864` | an owning teardown let it go |
| `VpnControl: Stopping DummyVpnService` | `VpnControl.kt:41` | the stop actually reached `VpnControl` |
| `DummyVpnService was not running` | `VpnControl.kt:49` | a stop that had nothing to stop |
| `Dummy VPN stopped` | `DummyVpnService.kt:74` | the descriptor closed. **Expect one per teardown, not two** |
| `AapService: Initializing WiFi Mode: ` | `AapService.kt:1667` | `initWifiMode()` proceeded past its guard |
| `WifiLock acquired (HIGH_PERF)` | `AapService.kt:1822` | the lock survived the quiesce commit's new guard |
| `stopping the wireless stack for the duration of it` | `AapService.kt:1772` | **must never appear.** Wireless-only rig |
| `createGroup SUCCESS` | — | more than 1 in a run is the §7a discard |

Two free riders for the `media-gap-instrument` thread, which its README row says to append to
whatever runs next. Both print once at session start; just report the counts.

| Grep for | Expect |
|---|---|
| `connected to another WiFi network on` | 1 per group, naming a frequency |
| `Audio sink is off in Settings` | 0, since `enable-audio-sink=true` |

## 6. Runs

Bring the head unit up before the phone, every run (§7a).

### R0 — build and unit tests. Gate.

`build_hur.sh`, then `run_unit_tests.sh`. Copy the APK out of `apks/` before anything else builds.

- **PASS**: compiles, and the suite is green. The branch carries **612 `@Test`** annotations; round 2
  of media-gap measured 594 at `93354419`, and the difference is the two log-line commits plus this
  round's new files. Report the actual count rather than matching mine.
- Named classes that must be present and green: `DummyVpnPolicyTest` (**7**),
  `UsbSessionQuiescePolicyTest` (**8**), `LinkGapMonitorTest` (**17**),
  `StationCoexistencePolicyTest` (**11**).
- **FAIL**: any compile error. Stop the round and report the error verbatim — the conflict
  resolution in `onConnected()` is the first thing to suspect.

### R1 — consent, and does the tun come up at all. Gate for R2 and R3.

Set the keys in §4 with the app stopped, then grant the VPN consent op and connect.

```bash
adb shell appops set com.andrerinas.headunitrevived ACTIVATE_VPN allow
adb shell appops get com.andrerinas.headunitrevived ACTIVATE_VPN
```

Launch the app explicitly (§7a: a force-stopped app's receivers do not fire), let the group settle
~15 s, bring the phone up, wait for the session.

- **PASS**: `dummy VPN requested (owner=SESSION)` **and** `tun established (excludeSelf=true)`, in
  that order, within a few seconds of the session forming.
- **The failure that is not a code failure**: `prepared VPN app` at WARN instead. That means the
  appop route did not satisfy `VpnService.prepare()`. Try once via the UI — open the app's settings,
  find "Keep the offline VPN up while connected" under the Android Auto section, tap it, accept the
  system dialog — then redo R1. If that also fails, mark **R1 UNTESTABLE**, skip R2 and R3, and run
  R4. Do not spend the round fighting it.
- **FAIL**: neither line and no WARN either, i.e. the code never even asked.

### R2 — the VPN survives a whole session, and comes back on the second one. **The point of the round.**

From R1's live session. Leave it alone for **10 minutes**, then
`headunit://disconnect`, wait 30 s, `headunit://connect`, and hold the second session 3 minutes.

- **PASS**, all four:
  1. **Zero** `VpnControl: Stopping DummyVpnService` between the first `tun established` and the
     `headunit://disconnect`. This is the whole fix.
  2. Exactly one `releasing the dummy VPN (owner=SESSION, reason=SESSION_ENDED)`, at the disconnect.
  3. `dummy VPN requested (owner=SESSION)` again on the second connection. This is the reporter's
     actual complaint.
  4. `Dummy VPN stopped` count equals the number of teardowns, **not twice** it.
- **Pair the zero with a number that proves it was reachable**: report the count of
  `AapService: Initializing WiFi Mode: ` inside the 10-minute window. If the change did nothing, each
  of those would be followed within ~5 ms by a stop line. **If that count is 0, run 1 proves nothing
  by itself** and R3 is what carries the round — say so rather than reporting a bare PASS.
- Also report: `createGroup SUCCESS` count (discard if > 1), and whether the session survived the
  full 10 minutes.

### R3 — force `initWifiMode` under a live VPN. The direct regression probe.

With a session live and the tun up, fire the action that reaches `initWifiMode(force = true)`:

```bash
adb shell am start-foreground-service -n com.andrerinas.headunitrevived/com.andrerinas.openheadunit.aap.AapService \
  -a com.andrerinas.openheadunit.ACTION_START_WIRELESS_SCAN
```

- **PASS**: `AapService: Initializing WiFi Mode: ` appears, and **no**
  `VpnControl: Stopping DummyVpnService` between it and the next disconnect line. Decide it on
  timestamps: the old code put the stop 3 ms after the init.
- **Expected and not a failure**: this rebuilds the P2P group, so the session may well drop
  afterwards. A drop produces a legitimate `releasing the dummy VPN (… reason=SESSION_ENDED)`. Only a
  stop line *before* any disconnect is a FAIL.
- **INCONCLUSIVE** if `Initializing WiFi Mode` never prints — the guard returned early and the probe
  did not fire.

### R4 — control, toggle off. Regression, and the quiesce commit's wireless half.

Set `keep-dummy-vpn-during-session=false`, app stopped, then a clean 10-minute Native AA session.

- **PASS**, all of:
  - **Zero** of every VPN line in §5. With the toggle off the branch must be invisible.
  - `WifiLock acquired (HIGH_PERF)` present **exactly once**. The quiesce commit rewrote that call
    site and this is a wireless session, so the lock must still be taken.
  - `stopping the wireless stack for the duration of it` **absent**. It would mean the quiesce
    misfired on a wireless session.
  - `createGroup SUCCESS` = 1.
- Report the two free-rider counts from §5 here.

## 7. Do not re-run

- Anything from `media-gap-instrument` round 2. Its four commits are on this branch with new SHAs but
  identical content, verified by `git range-diff`. R4's session doubles as their regression cover.
- The `LinkGapMonitor` 85 % ceiling. Round 2 could not summon the idle trickle and neither will this.
- Fault injection of any kind.

## 8. Report back

Three numbers decide whether this ships:

1. **R0**: does it compile, and the test count.
2. **R2.1 with its pairing**: stops-during-session (want 0) *alongside* the `Initializing WiFi Mode`
   count that says whether 0 meant anything.
3. **R3**: whether a forced `initWifiMode` under a live tun left it alone.

R4's zeroes matter mainly if one is not zero.
