# Discovery socket leak: round 5 brief

## 1. Build and baseline

**Candidate:** `fork/fix/773-headunit-server-socket-leak` @ `646441c4` — **unchanged from round 4.**
No new commit, no new SHA, nothing to rebuild. If the round 4 APK
(`e88f603db2d639d690735b7874e50d8b`) is still installed, use it as-is and skip R0 entirely; only
re-verify the md5 if the device has been reflashed since.

**Baseline:** none.

## 2. Why there is a round 5: R8's brief was wrong, not R8's unit

Round 4 settled the round's actual question — R7 is a clean double PASS and reverses R6. R8 is the
only loose end, and the fault is mine.

**`adb reboot` does not broadcast `ACTION_SHUTDOWN`.** It is not a framework reboot at all: `adbd`
sets the `sys.powerctl` system property, `init` picks that up and reboots the device directly.
`ActivityManager` is never asked to shut anything down, so `ShutdownThread` — the only thing that
ever sends `ACTION_SHUTDOWN` — never runs. Round 4's §3 told you "`adb reboot` is the trigger". It
is not, and no amount of care on the rig side could have made that run work.

So R8 is not a fact about this unit, and the sentence in round 4's results that begins "whatever
kills the session during this unit's `adb reboot` sequence" should be read as describing `init`
tearing the process down, not an Android shutdown. The question "does the reporter's real case —
a unit powering off — close the session in time?" is still completely unmeasured.

**The framework path is `svc power reboot`**, which calls `IPowerManager.reboot(...)` and goes
through `ShutdownThread`, which broadcasts `ACTION_SHUTDOWN` *before* it tears the radios down. That
ordering is the whole reason the fifth commit hooks it: there is a real window there, and this round
finds out how wide it is.

**There is a second ambiguity in R8 worth closing while you are here.** By the results' own clocks,
the session died about 4.6 s *before* the reboot was issued — `AapRead: Connection closed (EOF)` at
device 16:07:07.473, which is roughly host 16:07:08.4 given the stated 0.9 s offset, against a
trigger at host 16:07:13. If the session was already gone, `maybeTearDownBeforeLinkGoes` early-
returns on `isConnected` and logs **nothing at all**, which would explain the silence without any
reference to `ACTION_SHUTDOWN` at all. Either the session really did die early or the trigger
timestamp drifted; both are easy to rule out this time by confirming the session live seconds before
the trigger and recording the trigger time from the same clock as the log.

## 3. What is different about this round

- **Small round.** Two runs, one of them conditional. It exists to answer one question.
- **`WakeDetect: SHUTDOWN` is now a first-class signal** and is the line that separates the two
  failure modes. It is logged unconditionally, before any session check, so it answers "did the
  broadcast arrive?" on its own — even with no session live. Round 4's §4 list omitted it, which is
  why its silence was uninterpretable.
- **The capture has to outlive the device.** Run `adb logcat` redirected to a host file *before* the
  trigger and leave it running; the file keeps everything that arrived before adb dropped. Do not
  rely on reading the device buffer after the reboot.
- **The teardown has a 1500 ms budget and `ShutdownThread` allows an ordered broadcast 10 s**, so
  even a slow teardown cannot hang the shutdown. If the unit takes visibly longer to go down than
  usual, that is worth reporting but is not a hang.
- Same settings as round 4 (`wifi-connection-mode=2`, `helper-connection-strategy=3`,
  `log-level=1`), and the standing rule holds: **never pre-check port 5277 with anything**, `nc`
  included. Verify it passively via `/proc/net/tcp6` if you need to.
- **Budget: 1-2 manual server restarts.** As in round 4, a run that needs none is itself the signal.

## 4. The lines that decide every run

Verified with `grep -F` against `646441c4`.

```
WakeDetect: SHUTDOWN (system shutting down
with a live session — closing it now, while the link still
but this session does not ride that link; leaving it alone
link-loss teardown finished in
AapTransport stopping and sending byebye
AapTransport: send failed (ret=
Handshake: Version response received (ret=
Handshake: the peer accepted the connection and then sent nothing at all.
```

The trigger name is prepended at runtime, so on screen those two middle lines read
`AapService: DEVICE_SHUTDOWN with a live session — …` and
`AapService: DEVICE_SHUTDOWN, but this session does not ride that link…`. Grep for the fragments as
listed above — the composed form does not exist in the source and `grep -F` will not find it.
Whether the prefix says `DEVICE_SHUTDOWN` or `WIFI_STATION_DISABLING` tells you which hook fired.

Read them in that order — each one only means something given the one above it:

| What you see | What it means |
|---|---|
| No `WakeDetect: SHUTDOWN` | The broadcast never reached us. Go to R11. |
| `WakeDetect: SHUTDOWN` but no `closing it now` | A session was not live at that moment. Re-run; this is the R8 ambiguity, not a finding. |
| `closing it now` then `send failed (ret=` | The window is too narrow on this unit — the link was already gone. A real, negative answer. |
| `closing it now`, no `send failed`, server still deaf | The ByeBye reached the wire and the server wedged anyway. **The hypothesis is wrong**, and that matters more than anything else in this round. |
| `closing it now`, no `send failed`, server fine afterwards | PASS. |

## 5. Runs

### R10 — a framework reboot with a live session (the point of the round)

1. Restart the head unit server on the phone by hand, fresh. Confirm it is listening passively.
2. Settings as §3. Launch, establish a session, and **confirm it live within ~10 s of the trigger** —
   `Handshake: Version response received` plus video moving. Note the host clock time of that
   confirmation.
3. Start the host-side `adb logcat` capture if it is not already running.
4. `adb shell svc power reboot` — note the exact host clock time you issued it.
5. Let the unit come back. Nudge WiFi with `cmd wifi connect-network` as needed (§7a quirk; it cost
   three runs in round 4 and is not a finding).
6. Launch the app. **Do not restart the head unit server.**

**PASS** — the decisive lines appear per §4's table and the app reconnects with no manual server
restart. **FAIL** — it cannot connect until you restart the server by hand; say which §4 row you
landed on. **INCONCLUSIVE** — the session was not live when `WakeDetect: SHUTDOWN` fired.

Report the `link-loss teardown finished in <N>ms` figure. Round 4's WiFi-toggle numbers were 181 ms
and 198 ms; a shutdown may well be slower, and how close it comes to 1500 ms is useful either way.

### R11 — conditional: does the handler work at all? (only if R10 logs no `WakeDetect: SHUTDOWN`)

`ACTION_SHUTDOWN` is a protected broadcast, so shell (uid 2000) cannot send it — §7a already records
that refusal for the phone. Root can, if this build allows it:

```bash
adb root && adb shell am broadcast -a android.intent.action.ACTION_SHUTDOWN
```

If `adb root` is refused, R11 is **UNTESTABLE** — say so and stop; do not look for another way in.

With a live session, this exercises the handler with the network fully up. It tests the **code path
only** and proves nothing about the real shutdown window, so give it its own verdict and label it
that way. It is still worth having: it separates "our handler is broken" from "this unit's shutdown
sequence never tells us", and only the second of those is the reporter's problem.

### R12 — optional, and only if the bench can do it safely

If the rig can be powered down and back up at the ACC line or the wall the way a car does, one
trial of that is the closest thing to the reporter's actual case that exists here. Same procedure
and same verdicts as R10. Skip it if it means anything awkward — R10 is the run that matters.

## 6. What not to spend time on

- **Unwarned losses.** Out of range, an AP restarting, a hard power cut with no orderly shutdown:
  nothing on this branch helps those and no run here should pretend to test them.
- **The WiFi radio not coming back after `svc wifi disable`.** Known rig quirk, hit in rounds 3 and
  4, unrelated to the candidate. Nudge it and move on; it does not need re-characterising.
- **Re-running R7.** It passed twice on the same server instance. It is settled.

## 7. Report back

1. **R10's verdict**, and which row of §4's table you landed on — that is the whole result.
2. **Was `WakeDetect: SHUTDOWN` present?** Yes or no, on its own, regardless of everything else.
3. **`link-loss teardown finished in <N>ms`**, if it appeared.
4. **Was `AapTransport: send failed (ret=` present?** Yes or no.
5. **The two timestamps** — session confirmed live, and trigger issued — from the same clock, so
   R8's ambiguity cannot repeat.
6. **R11's verdict if it ran**, labelled as code-path-only, or UNTESTABLE if `adb root` is refused.
7. **How many manual server restarts the round cost**, and where.
