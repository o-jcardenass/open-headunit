# Discovery socket leak: round 3 brief

## 1. Build and baseline

**Candidate:** `fork/fix/773-headunit-server-socket-leak` @ `766546a3` — four commits. The first
three are round 2's `69fad750` unchanged; the fourth is the fix for what R3 found.

**Baseline:** none needed. Round 2's R0/R1/R2/R4/R5 are settled and are not re-run.

```bash
git fetch fork && git checkout 766546a3
```

**History was not rewritten this time.** `69fad750` is an ancestor, so if you still have round 2's
checkout, `git fetch fork && git merge --ff-only fork/fix/773-headunit-server-socket-leak` works.
A rebuild and reinstall are still required: `git diff 69fad750 766546a3` is two files.

## 2. What this is and why it exists

**Your R3 was right, and it found a defect the unit tests could not.** `UnresponsivePeerPolicy` and
its 8 passing tests were fine. Nothing ever called them. The counter was hung off
`ConnectionState.Error`, which is **never delivered to anybody**: `connectionState` is a
`MutableStateFlow`, so collection is conflated, and `startHandshake()` emits `Error` and then calls
`disconnect()` — not a suspend fun, sets `.value` immediately — with no suspension point in
between. The value is already `Disconnected` before any collector resumes.

Two independent measurements say so, and one of them was available before round 2 ever ran:

- **Your R3:** 0 `connections in a row without answering` against 36 handshake failures.
- **The #773 reporter's own log:** the same `when` block's `Disconnected` branch logs 4 times and
  its `Error` branch 0 times, across 11 handshake failures.

The fix moves the counting into `CommManager`, in the same continuation that discovers the failure,
and has the rescheduler read the count rather than try to observe the event. **This round exists to
confirm that one change and nothing else.**

Two corrections to round 2's own reporting, neither of which changes its verdicts:

- **28 stranded sockets is not worse than 24, it is the same rate.** 28 over 6m10s is 4.5/min; round
  1's 24 over 5m is 4.8/min. The FAIL stands on the cadence and the absent log line; the socket
  count simply did not move.
- **R2's scan-timing caveat was well spotted and the brief's fault.** That signature could not
  distinguish baseline from candidate, as you showed with round 1's near-identical 2.13s gap. R6
  below replaces it with a discriminator that can, because R2's real evidence — the reconnect held —
  was a single trial.

**Your nc pre-check finding is now a standing rule** and is worth more than it cost: any connection
to port 5277 claims the server, including a `-w 3` probe that closes immediately. §3 carries it.

## 3. What is different about this round

- **Do not pre-check port 5277 with anything.** Your round-2 discovery: a throwaway
  `toybox nc -w 3 <ip> 5277 </dev/null` deafened a freshly restarted server and cost an extra manual
  restart. Launch the app as the first thing that touches the port; if you need to know the server
  is up, the app's own first cycle tells you.
- **Everything else from round 2's Setup notes still holds**, including the manual hotspot and head
  unit server taps, and the `NetworkListFragment` long-press route you worked out
  (`input swipe 894 334 894 334 800` on `wifi_button`) if you re-enter that screen for any reason.
- **Budget: 2 manual server restarts** (one to start clean for R6, one between R6 and R3 since each
  deafening run needs its own). Fewer than round 2 because R1, R4 and R5 are not re-run.
- `log-level=1` (DEBUG), settings exactly as round 2 (`wifi-connection-mode=2`,
  `helper-connection-strategy=3`).

## 4. The lines that decide this round

Verified with `grep -F` against `766546a3`.

```
connections in a row
without answering any of them. Slowing discovery to one attempt every
Handshake: the peer accepted the connection and then sent nothing at all.
Auto-connecting to Headunit Server at
Handshake failed
Handshake: Version response received (ret=
NetworkDiscovery: Found Headunit Server on
NetworkDiscovery: Gateway scan error
```

**The backoff line moved class and was re-split.** It is now emitted by `CommManager`, not
`AapService`, and reads at runtime:

```
CommManager: 192.168.41.113:5277 has accepted 3 connections in a row without answering any of
them. Slowing discovery to one attempt every 60s. Android Auto's head unit server does not
recover on its own once this happens — stop and start it again on the phone, in Android Auto's
developer settings, and this will reconnect by itself.
```

Round 2's `discovery to one attempt every` still matches; `Slowing discovery to one attempt every`
never did and has been replaced above with a fragment that is contiguous in source.

## 5. Runs

### R0: build gate

`build_hur.sh` then `run_unit_tests.sh`.

- **PASS**: builds, suite green, `UnresponsivePeerPolicyTest` still 8/8 (unchanged this round —
  the policy was never the problem).
- **FAIL**: report the compiler output and stop.

### R3b: the backoff actually engages  ← **the point of the round**

Round 2's R3, repeated verbatim so the numbers are directly comparable. Server force-deafened
fresh (force-stop the app, hold `tail -f /dev/null | toybox nc 127.0.0.1 5277` on the phone,
relaunch), left untouched for **6 minutes**.

- **PASS**, all three:
  1. `without answering any of them. Slowing discovery to one attempt every` appears **exactly
     once** — round 2 had 0.
  2. `Handshake failed` count is **6-10** over the 6 minutes — round 2 had 36, round 1's baseline
     32 in 5 minutes.
  3. The interval between `Auto-connecting to Headunit Server at` lines is ~10s for the first three
     or four cycles, then ~60s for the rest.
- **FAIL**: the line is still absent, or the cadence never widens.

Then the phone-side count, the number that decides whether this ships:

```bash
adb -s <phone> shell netstat -tn | grep 5277        # count CLOSE_WAIT rows
```

Round 2 measured 28 over 6m10s. **Expect single figures.** Report the raw count and the exact
capture duration, so the rate can be compared rather than the total.

### R6: the race fix, repeated  ← **new, and it replaces R2's weak signature**

R2 passed on a single trial, and the log evidence the brief asked for turned out not to
discriminate. This run tests the outcome instead, three times.

Server restarted fresh. App launched, confirmed connected (`Handshake: Version response received`).
Then, three times with ~30 s between them:

```bash
adb -s <hu> shell svc wifi disable; sleep 3; adb -s <hu> shell svc wifi enable
```

- **PASS**: all three rejoins reconnect and hold — three more `Handshake: Version response
  received`, no run of `Handshake failed` that never recovers.
- **FAIL**: any rejoin leaves the server permanently deaf for the rest of the capture, the way
  round 1's R2 did.
- **INCONCLUSIVE** is a legitimate outcome here: if one of the three deafens the server, say which
  and stop, because the remaining rejoins are then measuring a broken server rather than the fix.

This matters because the mechanism is genuinely uncertain. A rejoin kills the old session without a
FIN, which deafens the server all by itself and which **this branch does not fix**. Round 1 saw
that; round 2 did not. Three trials will not settle it either, but 3/3 holding makes "round 2 got
lucky" much less likely, and 1/3 or 2/3 tells us the branch does not cover this case — which is
worth knowing before anyone tells the reporter it is fixed.

## 6. Do not re-run

Round 2 settled these and they cost real manual restarts:

- **R1** (the positive control). PASS, 20/20, and the new log line confirmed. The fourth commit does
  not touch that path.
- **R4** (Native AA). PASS. A separate review since then confirmed mode 3 never constructs a
  `NetworkDiscovery` at all; the 3m08s was P2P group churn and a failed WiFi association, both
  pre-existing rig behaviour.
- **R5** (the manual network list). PASS, and the navigation route is written down if it is ever
  needed again.
- Anything about the reporter's end-to-end setup, or USB, or Bluetooth.

## 7. Report back

1. **R3b's `Slowing discovery` count** — must be exactly 1, was 0.
2. **R3b's `Handshake failed` count and the capture duration** — against 36 in 6m10s.
3. **R3b's phone-side socket count and the same duration** — against 28 in 6m10s.
4. **R6: how many of the three rejoins held** — 3/3, or which one did not.

If R3b fails again, do not iterate further on this rig: report and stop. Two rounds saying the
backoff does not engage would mean the loop it is meant to gate is not the loop that runs here, and
that is a code question rather than a hardware one.
