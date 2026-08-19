# release/next — round 6 brief

## 1. Build

**Candidate:** `fork/fix/video-stack` @ `9a1257ca` (full: see the fetch below).

```bash
git fetch fork fix/video-stack
git checkout fork/fix/video-stack
```

**Three new commits on round 5's candidate:**

```
* 9a1257ca  Video: keep the last focus cycle for the moment the stream goes quiet   <- new
* 77378e5d  WiFi Direct: say when the discovery loop runs, because today no          <- new
              reporter log can
* 1c617273  Transport: say when the inbound link goes quiet, not just when the       <- new
              picture does
* 64033c4a  Projection: a relaunch onto an idle screen can still ask for the …       <- round 5's tip
* bf38924d  Projection: say which of the four situations put the renderer banner up
* 4cf2451d  Video: a dropped fragment is still decrypted, so the session survives it
* … (round 5's list below this is unchanged)
```

**No baseline APK.** `versionCode` unchanged at **98**. None of the three has been compiled; R0 is
their first compile, and `LinkGapMonitor.kt` is an entirely new file.

**This round carries two threads on one candidate.** R1-R3 are the video escalation, at INFO. R4-R5
are the `link-stall-periodic-scan` thread, at VERBOSE, and R4 does double duty as the video thread's
clean control so the round is five runs rather than seven.

## 2. What this is and why it exists

### Thread A — round 5 answered its question, and exposed a different one

Round 5's R1 did everything it was asked to. Mode 5 sustained a session through a full 30-fault
budget, the reader stage produced 17 / 40 / 1 against the control's 0 / 0 / 0, and the hold fired for
the first time ever. What it also showed is that the hold is nearly unreachable, and the arithmetic
is plain once the timeline is laid out:

```
14:47:38.843  holding the cycle until it settles (0/3 spent)    <- the only one, all run
14:49:00.012  cycling video focus (1/3)
14:50:08.010  cycling video focus (2/3)
14:51:30.013  cycling video focus (3/3)
14:54:57.775  fault injection budget spent                      <- 207 seconds later
14:55:07 / :22 / :37   Decoder stopped: restart: sync_stall  x3
14:55:42.305  first non-zero rendered=                          <- the 44.5s
```

**All three cycles were spent while the wire was still losing frames** — the exact waste the hold was
written to prevent — and the recovery you measured came from the decoder's own restart ladder, not
from the escalation, which had nothing left. Your write-up called that distinction out and it was
right.

`CORRUPTION_QUIET_MS` was 2 s, derived as twice the keyframe-request throttle: reasoning about our
own report pacing rather than about the stream. The check that reads it runs 2 s after the unrepaired
clock arms, so the only corruption that could ever defer a cycle was corruption landing inside that
same 2 s window. Your run injected a fault every ~12 s. One hold in six checks is what that predicts,
and one hold is what the log has.

Two changes. The quiet window is now **15 s**, sized against the spacing you measured (30 faults
across ~450 s). And **the last cycle of a session is held for real quiet with no ceiling** — cycles 1
and 2 are still worth spending speculatively on a wire that may never settle, because more remain;
the last one is worth only what it buys at the moment the loss stops, which is a keyframe 0.5-1.6 s
later. A session whose wire never settles now ends with one cycle unspent, which costs nothing: that
session was not repairable by a keyframe anyway.

A wider window alone would have taxed the fault this escalation exists for. An isolated dropped frame
stamps the corruption at the instant the clock arms, so it would have been deferred by the full 13 s
difference. It still fires at exactly 2 s, guarded by requiring the corruption to have arrived at
least one escalation window *after* the arm. **R3 is the guard on that** and is the one thing this
change could plausibly regress.

### Thread B — the periodic link outage, and a suspect worth one run

Two reporters on Android 8.1 head units describe the same fault in the same words — the music cuts in
and out — and on both it is the whole link, on every channel at once, on a fixed cadence. One
profiled at 1.59 s dead every 11.57 s (14.1% over 487.7 s); the other at three silences of 5.96 s,
6.11 s and 5.17 s starting 10.05 s and 11.19 s apart, **78% dead time**, with the decoder reading
`dropped=0`, `fed == rendered` and `inputWait` under 92 ms through all of it. The same unit over USB
holds 50-59 fps across twelve consecutive windows with no gap at all.

Neither was readable from what the app prints. The only instrument that could see it was an offline
script over `RECV:` lines, which exist solely at VERBOSE — the first case took a 151,366-line export
to recognise and the second could not be diagnosed at all, because that reporter had been asked for
INFO by us. So the same measurement now runs in the app at INFO, reporting the fields the script
reports (`LinkGapMonitor`, commit `1c617273`).

The suspect is source-verified and narrow. `WifiDirectManager.discoveryRunnable` calls
`discoverPeers()` every 10 s while `isClientConnected` is false, and it is armed only from
`makeVisible()` — mode **2 / strategy 1** (Wireless Helper + WiFi Direct). Switch to Native AA
afterwards and `stop()` is skipped, because `WifiModePolicy.usesWifiDirect` is true for both modes;
nothing else cancels the runnable, and on the native path `isClientConnected` never flips, because
the phone joins out-of-band over Bluetooth so `clientList` stays empty. The loop then runs for the
whole session. `discoverPeers()` puts the P2P radio into find mode, sweeping the social channels — on
a single-radio unit hosting a group, that is seconds of silence for everything already on it, on a
10 s cadence.

The repo already asserts that harm, at `WifiDirectManager.kt:401`: *"discoverPeers() takes the group
owner off-channel every 10s"*. It was fixed at one call site only. This is the same mechanism
arriving by a different route.

**Nothing in this candidate fixes it.** R5 is a reproduction attempt. The only code change on this
side is that the loop's one log line was promoted from DEBUG to INFO and now says whether a session
is live — which is what makes R4's free check below worth anything.

## 3. What is different about this round

- **R1's key line has changed meaning.** A hold reporting `(0/3 spent)` or `(1/3 spent)` is a cycle
  deferred that the ceiling will release within a minute. A hold reporting **`(2/3 spent)`** is the
  reserved cycle, and that is the one this round is about. Report the two separately.
- **The hold line is now rate-limited.** Ten in full, then one a minute carrying
  `(and N more checks since the last report)`. A reserved cycle can hold for minutes at a 2 s
  re-check, which unthrottled would be ~100 identical lines. **Count the suppressed total, not the
  line count.**
- **R1's PASS is a mechanism, not a number.** Round 5 hit 44.5 s with the escalation contributing
  nothing at all, so a good number on its own proves nothing. What has to be true is that the
  recovery is attributable to a `cycling video focus` line and *not* to a
  `Decoder stopped: restart: sync_stall` → `Codec initialized:` pair.
- **The ≤10 s target from round 5 is withdrawn and replaced by ≤20 s.** Detecting that a wire has
  gone quiet takes about one fault interval, and yours was ~12 s. 15 s of quiet plus 2 s of
  escalation plus ~1.5 s of keyframe is ~18 s, and that is the honest floor for this fault spacing.
- **R2 runs immediately after R1, on the same screen state, established the same way.** Round 5's R3
  took 1029 s to spend a budget that took 393 s in round 4, which made its recovery gap incomparable
  to R1's. **Report time-to-budget-spent for both**, so comparability is visible rather than assumed.
  If the two differ by more than about 2x, say so and treat the gap comparison as void — that is a
  finding about method, not a failure.
- **R4 and R5 are VERBOSE (`log-level=0`), not INFO.** They need `RECV:` lines for `recv_gaps.py`.
  This is the only reason; `LinkGapMonitor` itself reports at INFO.
- **R5 requires one hand-tap in the app's settings UI, and the brief authorises it.** §0 of the
  template says never to configure through the UI, for a good reason — launching the app starts
  `AapService`, so a run begins before configuration finishes. That reason does not apply here, and
  the rule's usual alternative actively destroys the thing under test: `set_hu_prefs.sh` needs the
  app stopped, which drains the Handler holding the leaked callback. **The leak only exists across a
  mode change inside one process.** Round 2 of the `link-stall-periodic-scan` thread set the
  precedent for documented hand setup; record the tap and its timestamp in Setup notes.
- **A null result on R5 is still informative this time.** The promoted line reports `onSuccess` or
  `onFailure`, so even if Android 14 refuses a P2P find while a group owner has a connected client,
  we learn that — which is exactly what round 2's levers could not tell us. The rig is Android 14;
  both reporters are Android 8.1. Say which happened.
- **`LinkGapMonitor`'s `period~` field is the interval between gaps, not between cycles.** On a
  paired-gap waveform it reports the intra-pair spacing (~5980 ms), not the ~10.5 s cycle. Read it
  alongside the count, and **cross-check every report against `recv_gaps.py` on the same capture** —
  this is the instrument's first hardware run, and validating it against the script it was ported
  from is part of the round.
- **`banner_watch.sh` runs for the whole of every run.** It earned its place; round 5 lost about a
  minute of R1 to the round-4 matcher failing silently on the uppercased `YES`.
- **Round 5's R4 and R5 are dropped.** The relaunch fix passed 4/4 with no alternation and the idle
  guard was clean. Settled.
- Standing: grep the bare token `FAULT INJECTED` (reader-stage faults print under `AapRead:`); grep
  with `-a`; below **30 `FAULT INJECTED`** an injection run is INCONCLUSIVE; discard rule counts SSL
  handshakes and `p2p-wlan0-N` indices reaching the measurement window.

## 4. Settings

Types: `log-level`, `view-mode`, `wifi-connection-mode`, `helper-connection-strategy`,
`debug-video-fault-injection`, `debug-video-fault-rate`, `debug-video-fault-budget` are **int**;
`video-codec` is **string**. "delete" means the removal half of the template's §1 only. "as usual"
means whatever `set_hu_prefs.sh` sets for an ordinary Native AA run.

| Key | R1 | R2 | R3 | R4 | R5 |
|---|---|---|---|---|---|
| `log-level` | `2` (INFO) | `2` | `2` | **`0` (VERBOSE)** | **`0`** |
| `view-mode` | `0` (SURFACE) | `0` | `0` | `0` | `0` |
| `video-codec` | `H.264` | `H.264` | `H.264` | `H.264` | `H.264` |
| `wifi-connection-mode` | as usual | as usual | as usual | as usual | **`2`, then 3 by hand** |
| `helper-connection-strategy` | — | — | — | — | **`1` (WiFi Direct)** |
| `debug-video-fault-injection` | `5` | `2` | `5` | delete | delete |
| `debug-video-fault-rate` | `3` | `3` | **`300`** | delete | delete |
| `debug-video-fault-budget` | `30` | `30` | **`2`** | delete | delete |
| `force-software-decoding` | delete | delete | delete | delete | delete |
| `software-video-decoder` | delete | delete | delete | delete | delete |

R1 and R2 are round 5's R1 and R3 unchanged. R3 is new. R4 and R5 belong to thread B.

## 5. The lines that decide the round

**New on this candidate:**

| Line | Level | Means |
|---|---|---|
| `- holding the cycle until it settles (` (composed: `…(N/3 spent)`) | W | **R1's key line.** `(2/3 spent)` is the reserved cycle; anything lower is an ordinary deferral |
| `more checks since the last report)` (composed) | W | the hold's print budget summarising. Its number is the real hold count |
| `AapTransport: inbound link quiet ` (composed: `…N times in Nms: dead=Nms (N%), longest=Nms, period~Nms`) | I | **R5's key line.** Silent by design when a window has no gaps |
| `Discovery active - peer search running` | I | the discovery loop ran. **R4 wants zero** |
| `while an Android Auto session is connected` | I | …and it ran under a live session, which is the anomaly |
| `WifiDirectManager: Discovery failed: ` | W | the OS refused the find. Report the reason code — a refusal is a result |

**Carried from round 5, and what each run counts:**

| Line | Level | Means |
|---|---|---|
| `AapRead: DELTA_CHANGED on VIDEO` (composed) | W | the framing audit sees the hole |
| `fragment run lost bytes, requesting keyframe to recover stream` | W | the detection reaching recovery. Expect it **above** the `DELTA_CHANGED` count — that is correct, see §7 |
| `AapVideo: fault injection budget spent after` / `AapRead: …` | W | **R1's and R2's stopwatch starts here** |
| `Throughput over ` (composed: `…rendered=N (Nfps), fed=N …`) | I | the stopwatch stops at the first non-zero `rendered=` |
| `cycling video focus` | W | a cycle spent, with `(N/3)` and the unrepaired time |
| `retaking video focus` | W | its second half |
| `Decoder stopped: restart: sync_stall` | W | **the rival mechanism.** R1 has to distinguish this from a cycle |
| `Codec initialized:` | I | one per codec build |
| `VideoDecoder: keyframe decoded - the picture is repaired` | I | the repair, and the only line that means it |
| `Decrypted payload too short` / `Connection closed (EOF). Disconnecting.` | E / I | round 4's TLS desync. Zero expected; a return would be new |
| `the renderer confirmation banner is up (` (composed) | W | the banner, with which of four situations armed it. Report the reason string |
| `but restart suppressed` (composed) | W | the sync-stall watchdog out of budget. Expected under sustained loss, out of scope |

**Read-desync, counted across all five runs — every one ends the session:**
`AapRead: fragment total read returned`, `AapRead: body read returned`, `AapRead: declared message size`,
`Disconnecting to resync`.

## 6. Runs

### R0 — build and unit-test gate

`build_hur.sh`, then `run_unit_tests.sh`.

- **PASS:** compiles, suite reports **525** tests — round 5's 513, plus 8 for `LinkGapMonitor` and 4
  for the reserved cycle. A run reporting 521 means the escalation tests did not land; 517 means
  `LinkGapMonitor`'s did not.
- **FAIL:** stops the round. Unbuilt files: `LinkGapMonitor.kt` (new), `LinkGapMonitorTest.kt` (new),
  `KeyframeCycleEscalationPolicy.kt`, `AapTransport.kt`, `AuditReportPolicy.kt`,
  `WifiDirectManager.kt`.

### R1 — the reserved cycle

Mode **`5`**, rate `3`, budget `30`, H.264, `SURFACE`, INFO. **Four minutes minimum, at least two of
them after the budget-spent line.** Round 5's R1 settings exactly.

- **PASS, both:**
  1. **At least one `holding the cycle until it settles` reporting `(2/3 spent)`.** That is the
     reserved cycle being held. Quote the first in full, and give the total including the
     `more checks since the last report` counts.
  2. **Budget-spent → first non-zero `rendered=` is ≤ 20 s**, *and* the recovery is attributable to a
     `cycling video focus` line rather than to `sync_stall` restarts. Give the timestamps of the
     budget-spent line, the last `cycling video focus`, every `sync_stall` restart in between, and
     the first non-zero `rendered=`, so the attribution can be read rather than taken.
- **FAIL:** no `(2/3 spent)` hold anywhere; or the third cycle is spent before the budget-spent line;
  or still `rendered=0` 60 s after it.
- **Also record:** every `cycling video focus` with its `(N/3)` and unrepaired time, total
  `DELTA_CHANGED on VIDEO`, total `fragment run lost bytes`, total `Codec initialized:`, total
  `FAULT INJECTED`, and **time from launch to budget-spent**.

### R2 — the control, run immediately after R1

Mode **`2`**, rate `3`, budget `30`, H.264, `SURFACE`, INFO. **Same screen state as R1, reached the
same way.** Four minutes.

- **PASS:** **zero** `DELTA_CHANGED on VIDEO`, **zero** `fragment run lost bytes`, **zero** hold
  lines. Mode 2 is blind to the hole by construction and this confirms R1 is measuring something real.
- **Record time-to-budget-spent** and the recovery gap. If it is more than ~2x R1's
  time-to-budget-spent, **say so and mark the gap comparison void** — that is what happened in round
  5 and it is worth catching during the round rather than after.
- **R1 means nothing without this run.**

### R3 — the isolated drop did not get taxed

Mode **`5`**, rate **`300`**, budget **`2`**, H.264, `SURFACE`, INFO. **Six minutes**, or until both
faults have landed plus two minutes.

The quiet window went from 2 s to 15 s. This is the check that the common case — one lost frame on an
otherwise clean wire, which is what the whole escalation was built for — still escalates at the same
instant it always has.

- **PASS:** for the first fault, **`cycling video focus` reports between `2000ms` and `2100ms`
  unrepaired, with no `holding the cycle` line before it.**
- **FAIL:** a hold line precedes the first cycle, or the cycle reports ~15000 ms. Either means the
  guard on the isolated case is not working and the change taxes the common fault to pay for the
  sustained one.
- **INCONCLUSIVE:** fewer than 2 `FAULT INJECTED` in six minutes. At rate 300 the injector prints its
  candidate count every 15 s — if candidates are accumulating but faults are not, the screen is not
  fragmenting enough; move the map and extend rather than lowering the rate, since a lower rate
  stops the faults being isolated and this run needs them isolated.

### R4 — clean control, and it carries three jobs

No injection, `SURFACE`, **VERBOSE (`log-level=0`)**, Native AA reached the usual way
(`set_hu_prefs.sh` + force-stop). **Five minutes minimum.** Run `recv_gaps.py` over the capture.

- **As thread B's control — PASS:** `recv_gaps.py` reports 0 stalls / 0.0% dead, and **zero**
  `inbound link quiet` lines. Round 2 of the `link-stall-periodic-scan` thread pre-validated this at
  31480 `RECV:` lines over 428.4 s, so a non-zero result here is a finding in its own right and
  changes what R5 means.
- **As thread A's clean guard — PASS:** **zero** `holding the cycle until it settles`, **zero**
  `cycling video focus`, **one** `Codec initialized:`. A hold or a cycle on a session nobody broke
  would mean the widened window is firing on nothing.
- **As the free check on the whole thread-B theory — PASS: `grep -c "Discovery active - peer search
  running"` is 0.** If the loop is running on an ordinary single-mode Native AA session, then it does
  not need a mode change to leak and the §2 analysis is wrong — **that is the finding**, and R5 stops
  being the interesting run. Costs nothing to check.

### R5 — the experiment

**VERBOSE.** This is the one run that needs hand setup, authorised in §3.

1. With the app stopped, write `wifi-connection-mode=2` and `helper-connection-strategy=1`.
2. Launch. Let the P2P group form — wait for `createGroup SUCCESS` and for
   `Discovery active - peer search running` to start appearing on its 10 s cadence.
3. **Without stopping the app**, open OHU's settings and switch the connection mode to Native AA
   (mode 3). One tap. Record its wall-clock time in Setup notes.
4. Connect the phone and run a normal session for **at least five minutes**.
5. `recv_gaps.py` over the capture.

- **PASS (the leak reproduces):** the waveform appears — repeated gaps of seconds on a ~10-11 s
  period — in `recv_gaps.py` *and* in `inbound link quiet` lines, with
  `Discovery active - peer search running` continuing through the connected session. If those lines
  carry `while an Android Auto session is connected`, that is the chain end to end.
- **NEGATIVE (also a result):** the session is clean and `Discovery active` stops once Native AA
  takes over. Report whether the loop kept logging at all, and whether the lines say `onSuccess` or
  `Discovery failed: N`. **A refusal by Android 14 is a valid and reportable outcome** — it means the
  rig cannot host the experiment, not that the theory is wrong, and it is more than the previous
  round could establish.
- **Report regardless:** count of `Discovery active - peer search running`, how many carried the
  session-connected suffix, count and reason codes of `Discovery failed:`, every `inbound link quiet`
  line in full, and the `recv_gaps.py` output next to them.
- If the mode switch in the UI proves impossible without scrolling the settings list, **stop and
  report that** rather than working around it. It is the one step the round cannot substitute.

### R6 — read-desync silence (no new run)

Counted across R1-R5. **PASS: zero** of the four lines in §5. Round 4's four session deaths produced
zero of these, which is what proved that failure was above the framing layer; a non-zero count now
would mean something genuinely new.

## 7. Do not re-run, and do not re-investigate

- **`fragment run lost bytes` above the `DELTA_CHANGED` count is correct.** Round 5 measured 40
  against 17 and flagged it; the arithmetic is exact and by design. The keyframe request fires before
  the audit's print budget, deliberately — a suppressed log line must never suppress the repair. That
  budget allows 10 in full then one a minute, so an 8-minute run prints 10 + 7 = 17 while all 40 asks
  go out. The printed lines carry `(and N more since the last report)`; that is where the rest are.
- **Round 5's R3 recovery gap of 253.9 s.** Void by method, not a regression. R2 above replaces it.
- **The 123 s stall in round 5's discarded run.** Same warm-relaunch logic at its other threshold, it
  self-corrected in 1.3 s, a phone-side force-stop 64 s earlier cannot be ruled out, and the clean
  redo did not reproduce it. Worth a dedicated round some day; not this one.
- **#852's relaunch and idle behaviour** (round 5's R4 and R5). 4/4 clean reopens and five zeroes.
  Settled.
- **The TLS desync.** Fixed and confirmed across a full 30-fault budget. The lines stay in §5 only as
  a regression watch.
- **`parameter sets changed mid-session` = 2, the `-29 bytes/fragment` baseline, focus mode 4,
  `HIDE_START_CODE`, the ~68-69 s keyframe cadence.** All settled in earlier rounds.
- **The `restart suppressed` ladder under sustained loss.** The decoder's 15 s keyframe patience, out
  of scope, expected in R1.

## 8. Report back

1. **Was the reserved cycle actually held?** (R1.) The `(2/3 spent)` hold count, the first quoted in
   full, and the suppressed totals alongside.
2. **What brought the picture back?** (R1.) The timestamp chain — budget-spent, last
   `cycling video focus`, every `sync_stall` restart between them, first non-zero `rendered=` — and
   your reading of which one did it. Round 5's answer was "the restart ladder"; this round is asking
   whether that changed.
3. **Are R1 and R2 comparable this time?** Time-to-budget-spent for both, and an explicit void call
   if they are not.
4. **Did the isolated drop still escalate at 2 s?** (R3.) The first `cycling video focus` line in
   full, and whether any hold preceded it.
5. **Is a clean session untouched?** (R4.) Zero holds, zero cycles, one codec init — or the finding.
6. **Does the discovery loop run without a mode change?** (R4.) One number:
   `grep -c "Discovery active - peer search running"`. If it is not zero, say so first; it reframes
   the whole thread.
7. **Does the leak reproduce, and does it silence the link?** (R5.) The `recv_gaps.py` profile, the
   `inbound link quiet` lines, and the discovery counts side by side. A clean negative and an
   Android-14 refusal are both real answers — say which.
8. **Does `LinkGapMonitor` agree with `recv_gaps.py`?** Its first hardware run. Any disagreement is
   worth more than either number alone.
9. **Anything in a capture none of the above asked about.**
