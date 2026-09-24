# Discovery socket leak: round 4 brief

## 1. Build and baseline

**Candidate:** `fork/fix/773-headunit-server-socket-leak` @ `646441c4` — five commits. The first
four are round 3's `766546a3` unchanged; the fifth is an attempt at the gap R6 found.

**Baseline:** none. Everything else on this branch is settled across three rounds.

```bash
git fetch fork && git merge --ff-only fork/fix/773-headunit-server-socket-leak
```

History was not rewritten; `766546a3` is an ancestor. Rebuild and reinstall — `git diff 766546a3
646441c4` is five files, all new code paths rather than changes to tested ones.

## 2. What this is and why it exists

**Your R6 is the reason this round exists, and it changed a conclusion I had already written down.**
Two of two rejoins deafening the server, against round 2's one that did not, settled that round 2
was luck. A peer that vanishes without closing wedges the head unit server exactly as a silent probe
does, and nothing on this branch touched that, because we leak nothing there — the interface goes
away underneath a live session.

The one lever is timing. A session that *closes* does not wedge the server; that is the difference
between a drive ending with the app disconnecting and one ending with the power cut, and it is the
only account that fits the reporter saying 3.1.0 worked across drives. So the fifth commit takes
every warning the system gives us before a link dies:

- **`ACTION_SHUTDOWN`** — already received, until now only logged. This is the reporter's real case.
- **`WIFI_STATE_DISABLING`** — not previously listened for at all. This is your R6 trigger, and a
  user toggling WiFi.

On either, if a session is live and rides that link, the app sends the ByeBye, closes the socket,
and **blocks until that has happened** before letting the broadcast go.

**Be clear about what this cannot do.** Driving out of range, an access point restarting, and a
power cut with no orderly shutdown all arrive with no warning at all. Nothing here helps those, and
no run below pretends to test them. This round asks one question: **when we do get a warning and do
close in time, does the server stay usable?**

**It may simply not work.** The interface may already be down by the time `WIFI_STATE_DISABLING` is
broadcast, in which case the ByeBye never reaches the wire. That is why a failed send is now logged
— see §4. A FAIL here is a real answer, not a wasted round.

## 3. What is different about this round

- **R7 and R8 are the same measurement with different triggers.** Run both even if the first fails;
  they can genuinely differ, since a shutdown gives a longer window than a WiFi toggle.
- **R9 is a regression check on Native AA** and is not optional. The new WiFi hook must *not* fire
  for a mode-3 session, because a P2P group outlives the station toggle on some chipsets and
  tearing one down would cost a 45-90s reconnect to prevent nothing. Unit tests cover the decision;
  R9 checks the wiring.
- **`ACTION_SHUTDOWN` needs a real reboot**, not a force-stop. `adb reboot` is the trigger.
- **Budget: expect 2-3 manual server restarts.** Each run that ends with a deafened server needs
  one. If R7 passes, it needs none for that run — which is itself the signal.
- Same settings as round 3 (`wifi-connection-mode=2`, `helper-connection-strategy=3`,
  `log-level=1`), and the standing rule still holds: **never pre-check port 5277 with anything.**

## 4. The lines that decide every run

Verified with `grep -F` against `646441c4`.

```
with a live session — closing it now, while the link still
link-loss teardown finished in
but this session does not ride that link; leaving it alone
AapTransport stopping and sending byebye
AapTransport: send failed (ret=
Handshake: Version response received (ret=
Handshake: the peer accepted the connection and then sent nothing at all.
without answering any of them. Slowing discovery to one attempt every
```

**`AapTransport: send failed (ret=` is the one that explains a failure.** It is new, and silent
unless a write actually fails. If a run fails *and* that line is present next to the teardown, the
answer is "the link was already gone and this approach cannot work". If a run fails and that line is
**absent**, the ByeBye reached the wire and the server wedged anyway — which would mean a clean
close is not what releases it, and the whole hypothesis is wrong. Those are very different answers;
please report which one you saw.

## 5. Runs

### R0: build gate

`build_hur.sh`, then `run_unit_tests.sh`.

- **PASS**: builds; suite green; `LinkLossTeardownPolicyTest` present and passing (5 new tests, so
  expect 238 total against round 3's 233); `UnresponsivePeerPolicyTest` still 8/8.
- **FAIL**: report the compiler output and stop.

### R7: a WiFi toggle no longer deafens the server  ← **the point of the round**

Round 3's R6, repeated exactly, so the results compare directly. Server restarted fresh, app
connected and confirmed (`Handshake: Version response received`), then:

```bash
adb -s <hu> shell svc wifi disable; sleep 3; adb -s <hu> shell svc wifi enable
```

Expect first, in the moments after the disable:

```
AapService: WIFI_STATION_DISABLING with a live session — closing it now, while the link still works...
AapTransport stopping and sending byebye
AapService: link-loss teardown finished in <N>ms
```

- **PASS**: the app reconnects on its own after the rejoin and holds — a
  `Handshake: Version response received` with **no** manual server restart. Do this **twice**;
  round 3 showed one trial proves nothing here.
- **FAIL**: the rejoin still leaves the server deaf and needs a manual restart, exactly as R6.
  **Report whether `AapTransport: send failed (ret=` appears**, per §4 — that is what separates
  "no window existed" from "a clean close is not what releases it".
- Report `link-loss teardown finished in <N>ms` either way. If it is near the 1500 ms budget rather
  than the expected sub-200 ms, the close was blocking on a dead socket, which is itself the answer.

### R8: a reboot no longer deafens the server

The reporter's actual case. Server restarted fresh, app connected and confirmed, then:

```bash
adb -s <hu> reboot
```

Wait for the unit to come back, let the app start, and let it try to connect **without touching the
phone at all**.

- **PASS**: it connects, no manual server restart. The `DEVICE_SHUTDOWN` teardown lines should
  appear in the capture from before the reboot — start the capture before the reboot and keep it.
- **FAIL**: it cannot connect until the server is restarted by hand.
- **INCONCLUSIVE** is legitimate: if this unit does not deliver `ACTION_SHUTDOWN` on `adb reboot`
  (quick-boot units often skip it), the teardown lines will be absent entirely. Say so — that is a
  fact about the rig worth having, and it would mean the reporter's own unit may not get this
  either.

### R9: Native AA is not torn down by a station-WiFi toggle

`wifi-connection-mode=3`. Establish a normal Native AA session, confirm it is projecting, then
`svc wifi disable` on the head unit.

- **PASS**: the log shows `but this session does not ride that link; leaving it alone`, and the
  session keeps projecting — or, if the P2P group does go down on this chipset, it goes down
  *without* the teardown lines, and the app recovers as it always has.
- **FAIL**: the teardown lines appear for a mode-3 session. That is the wiring ignoring the policy,
  and it would be a real regression for the only transport this rig normally uses.

## 6. Do not re-run

- R3b, R1, R2, R4, R5 from earlier rounds. All settled; the fifth commit adds new paths rather than
  changing tested ones.
- Anything trying to test an *unwarned* link loss (out of range, AP restart, power cut). Nothing
  here addresses those and no result would be actionable.

## 7. Report back

1. **R7's verdict, twice**, and whether `AapTransport: send failed (ret=` appeared. This decides
   whether the approach works at all.
2. **R7's `link-loss teardown finished in <N>ms`.**
3. **R8's verdict, or that `ACTION_SHUTDOWN` never arrived on this unit.**
4. **R9: did the teardown correctly stay out of the way of Native AA.**

If R7 and R8 both fail with `send failed` present, say so plainly — that closes the approach, and
the branch ships with the gap documented rather than papered over.
