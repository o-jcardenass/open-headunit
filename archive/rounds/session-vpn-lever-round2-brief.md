# session-vpn-lever — round 2 brief

## 1. Build and baseline

| | |
|---|---|
| Branch | `fix/session-lifecycle-and-diagnostics` |
| SHA | `82814ec0` |
| Base | `main` @ `562c8dcf` |
| History rewritten? | **No.** One new commit on top of round 1's `6bd336ed`, which is still the parent. Fast-forward is safe this time. |

```bash
git fetch fork --prune
git checkout -B session-vpn-lever fork/fix/session-lifecycle-and-diagnostics
git log --oneline -2          # expect 82814ec0 on top of 6bd336ed
```

Only two files changed since round 1, both in the github flavor:
`app/src/github/.../aap/DummyVpnService.kt` and `app/src/github/.../utils/VpnControl.kt`. Nothing in
`main` moved, no test changed, and the test count is still **612**.

## 2. What this is and why it exists

Round 1 passed R0, R1, R3 and R4 and failed one condition of R2, and that failure is the whole
reason for this round.

The teardown was not working. `VpnControl.stopVpn()` called `context.stopService()` on the strength
of a comment claiming `onDestroy()` would close the descriptor. It does not, for a VPN: the
framework binds to a `VpnService` the moment `establish()` succeeds, so `stopService()` clears the
started flag and stops there, a bound service is never destroyed, and `onDestroy()` never runs.
Round 1 measured the consequence directly. `tun0` held `<POINTOPOINT,UP,LOWER_UP>` from 17:17:08
through at least 17:40, across two teardowns that each printed

```
AapService: releasing the dummy VPN (owner=SESSION, reason=SESSION_ENDED)
VpnControl: Stopping DummyVpnService (GitHub Build)
```

and changed nothing on the wire. `Dummy VPN stopped` never printed once in the entire round.
`dumpsys activity services` showed `hasBound=true` with a `system/1000` client, and `createTime`
never advanced, so it was the same unstopped instance throughout. Only the `force-stop` going into
R4 removed the interface.

`82814ec0` stops routing the teardown through an Intent at all: `VpnControl.stopVpn()` now closes
the descriptor on the running service instance directly, and `stopService()` stays behind it only
for a service that was started but never established. Going back to the pre-`ef0a383e`
`startService(ACTION_STOP_VPN)` was not an option, because that throws on O+ when the app is losing
foreground and `AapService.onDestroy()` calls `stopForeground(true)` on the line before the teardown.

The same commit handles `onRevoke()`, which was unhandled and left `vpnInterface` non-null after a
revocation, so `startVpn()`'s idempotence guard would have refused to establish a new tun for the
rest of the process's life.

## 3. What is different about this round

**No verdict in this round may rest on a log line.** That is the lesson round 1 paid for: every log
line the last brief told the round to trust was printed, in the right order, at the right time, by
code that was doing nothing. The PASS conditions below are anchored on `ip link` and
`dumpsys activity services`, and the log lines are corroboration.

**The process must stay alive across the teardown, and the round must prove it did.** A `force-stop`
also removes the tun, so a run that killed the app proves nothing at all. Record `pidof` on both
sides of every teardown and report both numbers.

**Untestable here, stated up front so nobody spends the round on it: the revoke path.**
`appops set ... ACTIVATE_VPN deny` is not a documented `onRevoke` trigger and there is no second VPN
app on the rig to take the slot. `onRevoke()` is routed to code review, not to this round.

**Deliberately not re-run: the toggle-off control (round 1 R4).** It was all-zero, and this commit
touches nothing on the off path. Its absence here is a decision, not a gap.

**Two of round 1's own instructions were wrong for this rig and have been corrected in
`TESTING-TEMPLATE.md`**, so this brief simply relies on the corrected versions: phone radio state is
read with `dumpsys`, never `settings get global`, and `headunit://connect` is a USB-only no-op under
Native AA, whose re-arm is a phone-side Bluetooth off/on cycle.

## 4. Settings keys this round needs

Per §7a, diff `settings.xml` against a fresh backup first and state the delta in Setup notes, even
if zero. Write with `hur-wifi-test-scripts/set_pref.sh`, app stopped. Same five keys as round 1,
with the lever on for the whole round.

| Key | Type | Value | Why |
|---|---|---|---|
| `log-level` | int | `2` | INFO. Every line below prints at INFO or above. |
| `wifi-connection-mode` | int | `3` | Native AA. The toggle only acts in this mode. |
| `view-mode` | int | `1` | TextureView |
| `enable-audio-sink` | bool | `true` | media-gap round 1 lost a run to this being `false` |
| `debug-video-fault-injection` | int | `0` | no injection this round |
| `keep-dummy-vpn-during-session` | bool | `true` | the lever, on for R1-R3 |

## 5. The lines and the commands that decide every run

All verified with `grep -F` against `82814ec0`. Remember `grep -a`, always (§7a).

| Grep for | Source | Means |
|---|---|---|
| `dummy VPN requested (owner=` | `AapService.kt:1853` | we asked for the tun, and for whom |
| `tun established (excludeSelf=` | `DummyVpnService.kt:68` | it actually came up, and in which mode |
| `prepared VPN app` | `AapService.kt:1845` WARN | **consent is missing** |
| `releasing the dummy VPN (owner=` | `AapService.kt:1864` | an owning teardown let it go |
| `VpnControl: Stopping DummyVpnService` | `VpnControl.kt:41` | the stop reached `VpnControl` |
| `DummyVpnService was not running` | `VpnControl.kt:54` | a stop that found neither an instance nor a started flag |
| `Dummy VPN stopped` | `DummyVpnService.kt:86` | **the descriptor closed.** Zero of these was round 1's failure |
| `consent was revoked` | `DummyVpnService.kt:100` | the new revoke path fired. Expect 0 this round |
| `AapService: Initializing WiFi Mode: ` | `AapService.kt:1667` | `initWifiMode()` proceeded past its guard |
| `createGroup SUCCESS` | — | more than 1 per genuine user exit is the §7a discard |

The three commands that actually decide this round, all on the head unit:

```bash
PKG=com.andrerinas.headunitrevived
adb shell ip link show tun0                                    # UP, or "does not exist"
adb shell pidof $PKG                                           # must not change across a teardown
adb shell dumpsys activity services $PKG | grep -B2 -A8 DummyVpnService
```

In the `dumpsys` output, the fields that matter are whether a `ServiceRecord` for
`DummyVpnService` exists at all, its `createTime`, and `hasBound`. Round 1's failure looked like a
surviving record whose `createTime` never moved.

## 6. Runs

Bring the head unit up before the phone, every run (§7a).

### R0 — build and unit tests. Gate.

`build_hur.sh`, then `run_unit_tests.sh`. Copy the APK out of `apks/` before anything else builds.

- **PASS**: compiles, and the suite is green. **612 `@Test`** annotations, unchanged from round 1.
  `DummyVpnPolicyTest` present and **7/7**.
- **FAIL**: any compile error. Stop and report it verbatim. The two changed files are small and
  self-contained; a failure here is most likely the companion-object access to the service's private
  `stopVpn()`.
- No new test was added this round, on purpose: the defect is Android service-lifecycle behaviour
  with no pure-policy surface, and this repo does not use Robolectric. R2 is its only coverage.

### R1 — consent, and the tun comes up. Gate for R2 and R3.

Exactly round 1's R1, which passed, so keep it short.

```bash
adb shell appops set com.andrerinas.headunitrevived ACTIVATE_VPN allow
adb shell appops get com.andrerinas.headunitrevived ACTIVATE_VPN
```

Launch the app explicitly (§7a), let the group settle ~15 s, bring the phone up, wait for the
session.

- **PASS**: `dummy VPN requested (owner=SESSION)` then `tun established (excludeSelf=true)`, and
  `ip link show tun0` reports the interface **UP**. Record `pidof $PKG` now and call it `PID`.
- **The failure that is not a code failure**: `prepared VPN app` at WARN. Follow round 1's
  contingency (one UI tap on the toggle, then redo). If it still fails, mark **R1 UNTESTABLE** and
  stop the round.

### R2 — the teardown actually takes the tun down. **The point of the round.**

From R1's live session. Hold **3 minutes**, then a scripted user exit. There is no need to repeat
round 1's 10-minute hold: survival during a session was condition 1 and it passed.

```bash
adb shell ip link show tun0            # before: expect UP
adb shell pidof $PKG                   # before
adb shell am start -a android.intent.action.VIEW -d "headunit://disconnect"
sleep 10
adb shell ip link show tun0            # after
adb shell pidof $PKG                   # after
adb shell dumpsys activity services $PKG | grep -B2 -A8 DummyVpnService
```

- **PASS**, all four:
  1. `ip link show tun0` after the disconnect says the device does not exist. **This is the whole
     round.**
  2. `pidof` is the same number before and after. Without this, 1 proves nothing.
  3. Exactly **one** `Dummy VPN stopped` in the capture so far. Round 1 had zero.
  4. No `ServiceRecord` for `DummyVpnService` left in `dumpsys`.
- **FAIL**: `tun0` still UP with the pid unchanged. That is round 1's defect reproduced, and it
  should be reported with the `dumpsys` block verbatim, including `hasBound` and `createTime`.
- Also report: the count of `DummyVpnService was not running` (expect 0) and
  `releasing the dummy VPN (owner=SESSION, reason=SESSION_ENDED)` (expect exactly 1).

### R3 — it comes back, and it goes down again.

Round 1 could not tell a real re-establish from `startVpn()`'s idempotence guard silently absorbing
the call, because the tun had never gone down. With R2 passing, it can.

Re-arm with a **phone-side Bluetooth off/on cycle**, not `headunit://connect`:

```bash
adb -s <phone> shell svc bluetooth disable
sleep 5
adb -s <phone> shell svc bluetooth enable
adb -s <phone> shell dumpsys bluetooth_manager | grep -i "enabled\|state"   # verify, do not trust
```

Hold the second session 3 minutes, then disconnect it the same way R2 did.

- **PASS**, all four:
  1. `dummy VPN requested (owner=SESSION)` a second time.
  2. `tun established (excludeSelf=true)` a **second** time, and `ip link show tun0` UP again. A
     second session with no second `tun established` line means the guard absorbed it and the tun
     never really came down, whatever R2 reported.
  3. After the second disconnect: `tun0` gone again, `pidof` still unchanged from R1's `PID`.
  4. `Dummy VPN stopped` now reads exactly **2**, one per teardown, not four and not zero.
- **INCONCLUSIVE** if the phone never comes back. The re-arm depends on `AutoStartReceiver` seeing
  `ACL_CONNECTED`; if `MATCH! Starting AapService` never appears, say so and report R2 alone.
- Report `createGroup SUCCESS` across the whole round. Two genuine user exits mean new groups are
  expected; what would be a discard is churn without a preceding teardown.

## 7. Do not re-run

- The toggle-off control (round 1 R4). See §3.
- The forced `initWifiMode` probe (round 1 R3). It passed, and this commit does not touch that path.
  Round 1's unscored side effect stands as a note for whoever next works on
  `ACTION_START_WIRELESS_SCAN`: it cascaded into a second `initWifiMode` 1.5 s later and left the
  rig across three incomplete handshakes for ~2.5 minutes. Not this round's business.
- Anything from `media-gap-instrument`.
- Fault injection of any kind.

## 8. Report back

Three things decide whether this ships:

1. **R0**: does it compile.
2. **R2.1 paired with R2.2**: `tun0` gone, and the pid that proves the process was still alive when
   it went. Either number alone is worthless.
3. **R3.2**: a second `tun established`, which is what proves the tun genuinely came down rather
   than the guard hiding it.

`Dummy VPN stopped` counting 2 at the end is the log-side corroboration, and nothing more than that.
