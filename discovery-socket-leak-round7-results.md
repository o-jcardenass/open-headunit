# Discovery socket leak, round 7 results

**Candidate:** `fork/fix/773-headunit-server-socket-leak` @ `38a9e020` (four commits, squashed and rebased
onto `main` @ `a8830caa` since round 6)
**Baseline:** none needed — no run in this round is an A/B
**APK md5:** `e18f493694a38b461f361e87d7cc3d8e` (round 6's was `08c5493da211c5f4ebcdfdc88f0a25bf`, two
rewrites stale)
**Unit:** UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14, serial `27870808938846`, rooted. Phone:
Redmi M2007J20CG (`surya_eea`, MIUI, Android 15), serial `4f4027e9`, Gearhead `17.4.663004-release`.
**Date:** 2026-08-13

## Setup notes

**Scripts used:** `install_and_launch.sh` (build+install), `run_unit_tests.sh`, `set_hu_prefs.sh`.
Nothing new added. `git reset --hard fork/fix/773-headunit-server-socket-leak` landed exactly on
`38a9e020` as the brief specified; `git log` confirmed the four commits sit directly on `a8830caa`.

**Both the phone's hotspot and the phone's "Start head unit server" AA developer setting were down at
the start of the round**, the same pattern every prior round in this investigation has hit. Both were
turned on by hand once, at the top of the round (verified passively via `/proc/net/tcp`/`tcp6` for
ports 5277/5289 — never with `nc` or any active probe, per the standing rule). Head unit joined the
phone's hotspot (`Navegadortz`, 2462 MHz / 2.4 GHz ch 11) at `192.168.10.52`; phone at `192.168.10.199`.

**R22a and R24 could not literally tap the WiFi button as written, for two independent, source-confirmed
reasons — the scripted equivalent (`ACTION_START_WIRELESS_SCAN` sent directly via
`am start-foreground-service`) was substituted for both, verified to reach the identical
`initWifiMode(force=true)` + `startDiscovery()` code path the button dispatches:**

- **R22a**: `AapProjectionActivity` occupies the foreground for the whole duration of a live session on
  this build. It shares a task with `MainActivity` (`t874`; `MainActivity` sits underneath in the back
  stack), `am start -n MainActivity` does not bring it forward (confirmed via
  `dumpsys activity activities` and a `uiautomator dump`, not assumed), and back navigation opens an
  exit-confirmation dialog rather than returning to `HomeFragment`. Even if it had been reachable,
  `HomeFragment.kt:462-518`'s click handler is a hard no-op when `commManager.isConnected` for both
  mode 1 and mode 2 — it would not have dispatched anything.
- **R24**: the button *is* reachable on the home screen (no live session), but its mode-1 handler shows
  an "already scanning" Toast and returns early whenever `AapService.scanningState.value` is true
  (`HomeFragment.kt:465-481`) — true almost continuously given the ~10-13 s discovery cadence. Two real
  taps at the round-6 coordinates (`894,334`) produced zero effect server-side (no new
  `Initializing WiFi Mode` line followed either), confirming the no-op before switching to the intent.

Worth flagging for whoever writes the next brief that targets this button: on any build with this
`HomeFragment` code, a literal "locate and tap" instruction will silently no-op in both of these
configurations (session live, or a background sweep already running), not just on this rig.

**R24's automatic sweep landed on the modem-bridge subnet (`10.10.225.*`) instead of the phone
hotspot's network** — the head unit's own WiFi station had dropped on its own between R23 and R24,
the same rig WiFi-instability pattern round 6's R19 hit. Not chased: R24 only needs an in-progress
sweep to provoke the cross-instance guard, not a particular subnet, and the guard fired on the first
attempt regardless.

**R21 pass 2's `svc wifi enable` did not bring the station radio back on its own** (same known §7a
rig quirk as rounds 3/4 — flagged live by the operator watching the device, not assumed). Needed the
standard `cmd wifi connect-network "Navegadortz" wpa2 "12345678"` nudge, applied once the stall was
confirmed, reconnecting at the same IP roughly 4 minutes after `svc wifi enable`. Not read as a
candidate defect — the app had nothing to do until the radio was actually back, and discovery
correctly stayed dormant across the entire extended window (see R21 pass 2 below).

**Did not pre-check port 5277 with anything active** — verified passively via `/proc/net/tcp` and
`/proc/net/tcp6` throughout, per the standing rule.

**Manual restarts this round: 0.** The phone's hotspot and AA dev server needed a one-time manual
*enable* at the top of the round (not a restart of anything mid-round), and the AA dev server was
turned off for R24 and left off afterward (no later run in this round needed it back). No capture
ever needed the head unit's own server-side software restarted to reconnect.

## R0: build gate (this code's first compile anywhere)

**PASS.**

Builds clean. `run_unit_tests.sh`: **264/264**, exactly the round's predicted total (0 failures, 0
errors across all 30 test-result files). `DiscoveryModePolicyTest` 5, `LinkLossTeardownPolicyTest` 7,
`UnresponsivePeerPolicyTest` 8 — all unchanged from round 6 — plus the rebase's new
`KeyDebouncePolicyTest` 13 and `MediaKeyRoutingPolicyTest` 6. APK md5 `e18f493694a38b461f361e87d7cc3d8e`.
This code compiled clean on its first attempt anywhere.

## R21: link-loss dormancy (the point of the round) — run twice

**PASS, both passes.**

Settings: `wifi-connection-mode=2`, `helper-connection-strategy=3`, `log-level=1`. Phone hotspot
topology, session established and video confirmed before each teardown.

### Pass 1

- Session established: launch → `Handshake: Version response received` 22:15:44.900 → SSL complete
  22:15:44.992 → `First frame rendered` 22:15:46.829.
- `adb shell svc wifi disable` at 22:16:35.
- **1.** `with a live session — closing it now, while the link still` at **22:16:35.100**.
- **2.** `link-loss teardown finished in **240ms**` at 22:16:35.340.
- **3.** `leaving discovery down until a network comes back` at **22:16:37.417** (2.08 s after teardown
  finished).
- **4.** Zero `Starting scan...` / `Scanning subnet:` between line 3 and the resume line — confirmed
  by filtering the whole 60 s+ window: **0**.
- WiFi re-enabled at 22:17:56, reassociated on its own (no nudge needed) by ~22:18:04.
- **5.** `network is back after a link-loss teardown; discovery resumes` at **22:18:04.204** → scan at
  22:18:04.708 → `Found Headunit Server on 192.168.10.199:5277` at 22:18:04.738 → `Handshake: Version
  response received` at 22:18:04.923 → `Media Start Request VIDEO` 22:18:06.258 → `First frame
  rendered` 22:18:06.514.
- **No manual server restart.**

### Pass 2

- `adb shell svc wifi disable` at 22:18:51.
- **1.** teardown announcement at **22:18:51.173**.
- **2.** `link-loss teardown finished in **201ms**` at 22:18:51.372.
- **3.** `leaving discovery down...` at **22:18:53.443** (2.07 s after teardown finished).
- `svc wifi enable` issued at 22:20:09 did **not** bring the station radio back (the known §7a nudge
  quirk — see Setup notes). Nudged with `cmd wifi connect-network`, which reconnected at the same IP.
- **4.** Zero scans confirmed across the *entire* extended dormancy window, **318.3 s** (22:18:53.443
  to 22:24:11.746) — well beyond the brief's 60 s minimum, a stronger test than asked for, and it held.
- **5.** `network is back...` at **22:24:11.746** → scan 22:24:12.250 → `Found Headunit Server` at
  22:24:12.280 → `Handshake: Version response received` at 22:24:12.461 → `Media Start Request VIDEO`
  22:24:13.793 → `First frame rendered` 22:24:14.001.
- **No manual server restart** (only the station-radio nudge, not a server-side restart).

The mechanism held on both passes, in order, zero scans in either dormancy window (one at the exact
60 s the brief asked for, one 5x longer because of a rig quirk), and both self-reconnected cleanly.

## R22: the loop now says why it stopped

### (a) One deliberate re-init tap during a live session

**PASS.**

Same topology as R21, session continuously live from R21 pass 2's reconnect (not relaunched). Sent
the scripted equivalent of the WiFi-button tap (see Setup notes) at **22:28:33.165**:

```
22:28:33.165  AapService: Initializing WiFi Mode: 2 (Strategy: 3)
22:28:33.175  AapService: Discovery not started — a connection is live or being set up
22:28:33.176  AapService: Discovery not started — a connection is live or being set up
```

Fired **twice**, matching the brief's "once or twice" prediction exactly — traced to source:
`startWirelessServer()`'s own NSD-registration call to `startDiscovery()` plus the handler's explicit
second call both land on the same `commManager.isBusy` gate. `NetworkDiscovery: Starting scan...`
count after the trigger: **0**.

**What it did to the session: nothing.** Same `AapProjectionActivity` instance (`ActivityRecord@6a2ca0f`,
task `t874`) stayed resumed before and after, no second `Handshake: SSL handshake complete` line, no
`Decoder stopped`/`surfaceDestroyed` line anywhere in the capture. Video continued uninterrupted —
`stopWirelessServer()` (called unconditionally at the top of `initWifiMode`) only tears down the
discovery/wireless-server plumbing, confirmed by source read, never touching `commManager`.

### (b) Harvest, no dedicated device time

`AapService: Discovery loop ends — a connection is live or being set up`, per capture:

| Capture | Count |
|---|---|
| R21 pass 1 | 2 |
| R21 pass 2 | 1 |
| R22a | 1 |
| R23 | 5 |
| R24 | 0 |

Fired naturally in every capture with a live or forming connection — the re-arm path reaches its new
log. `AapService: Discovery loop ends — the wireless server is gone`: **0 in every capture**
(informational, as predicted — needs a mode change inside a 10 s re-arm window, which nothing this
round staged deliberately).

## R23: post-rebase spot-check of round 6's headline

**PASS.**

Settings: `wifi-connection-mode=1`, `log-level=1`, server running. 5 force-stop/relaunch cycles,
~20 s apart (issued 22:29:54, 22:30:14, 22:30:34, 22:30:55, 22:31:15).

- `Starting scan...`: **5**. `Scan interrupted`: **0**. `Found Headunit Server on`: **5**.
  `Auto-connecting to Headunit Server` (handedover): **5**. `Handshake: Version response received`
  (connects): **5/5**.
- **Per-instance shape**, checked by PID rather than file total: five distinct process instances
  (`19993, 20244, 20489, 20708, 20952`), **each exactly 1** `Starting scan...`.
- **0 manual server restarts.**

The rebase onto 3.2.4 changed nothing observable — round 6's clean 1-and-0 shape holds exactly.

## R24: the cross-instance guard, provoked in-process (R19 done right)

**PASS, on the first attempt.**

Settings: `wifi-connection-mode=1`, `log-level=1`. Phone's "Start head unit server" AA developer
setting turned off by hand, confirmed off via passive `/proc/net/tcp`/`tcp6` (port 5277 no longer
listening). App on its home screen (`MainActivity` resumed, confirmed via `dumpsys activity
activities` — no live session). `found`/`handedover`: **0/0**, trivially, as expected.

A background sweep was already in progress (targeting the modem-bridge subnet due to a dropped WiFi
station — see Setup notes). Sent the scripted equivalent twice, ~0.5 s apart:

```
22:35:02.655  AapService: Initializing WiFi Mode: 1 (Strategy: 3)
22:35:02.667  NetworkDiscovery: Scan interrupted
22:35:02.682  NetworkDiscovery: waiting for an in-flight probe before scanning     <- attempt 1
22:35:03.250  AapService: Initializing WiFi Mode: 1 (Strategy: 3)
22:35:03.304  NetworkDiscovery: Scan interrupted
22:35:03.325  NetworkDiscovery: waiting for an in-flight probe before scanning     <- attempt 2
```

`waiting for an in-flight probe before scanning` appeared on **attempt 1**, the very first trigger —
no need to spend the remaining budgeted attempts. `Scan interrupted` appears twice, exactly as the
brief said it would (deliberate cancellation, not scored as a FAIL). 0 refused-handovers.

The phone's AA dev server was left off afterward — no later run in this round needed it back.

## R20 (standing): nothing hands over a socket that gets refused

**PASS, zero everywhere.**

`grep -c 'CommManager: Connect already in progress; closing the handed-over socket'` across all five
captures (R21 pass 1, R21 pass 2, R22a, R23, R24): **0 in every file.**

## Anything the brief did not ask about

**R21 pass 2's dormancy window ran 5x longer than the brief's 60 s minimum**, purely because of the
rig's own radio-return stall, and zero scans fired anywhere across the full 318.3 s — a stronger,
unplanned stress test of the dormancy mechanism than the brief asked for, and it held cleanly.

**`AapService: network changed during the last scan; rescanning immediately` fired once per R23
relaunch cycle (5/5)**, not zero. Each fresh process's first `NetworkCallback.onAvailable` reads as a
"network changed" event even though the underlying WiFi network never actually changed between
cycles — informational, not a defect, and it did not disturb the clean 1-and-0 scan shape reported
above. `in-flight scan promoted to continuous` did not appear in any capture this round.

**Both R22a and R24 needed the same source-verified substitution** (see Setup notes) because
`HomeFragment`'s own click handler has independent guards — `commManager.isConnected` for mode 1/2,
and `AapService.scanningState.value` for mode 1 specifically — that make a literal tap land as a
silent no-op in exactly the two states these runs need to exercise. This is a fact about the button's
UI-side guards on any build carrying this code, not specific to this branch or this rig; worth
carrying into how future briefs describe "tap the WiFi button" runs.

## Net position

Round 6's core fix (reusing the `NetworkDiscovery` instance) needed no re-verification here — the
range-diff/tree-hash check in the brief's §1 carries that verdict across the rewrite, and R23's five
launches confirm the rebase changed nothing observable. **The fourth commit, compiled and exercised
here for the first time anywhere, holds cleanly on every measure**: the link-loss teardown closes the
session properly (181-240 ms, well under budget) and correctly holds discovery dormant — through a
60 s window on the first pass and an unplanned 318 s window on the second — until the network
actually returns, at which point the session reconnects on its own with no manual server restart
either time. The two new "why the loop stopped" log lines both fire exactly where the source says
they should, including under direct provocation. The one carried-over open item from round 6, R19's
INCONCLUSIVE cross-instance-guard result, is now resolved: provoked correctly in-process this time
(R24), the guard fires on the first attempt.
