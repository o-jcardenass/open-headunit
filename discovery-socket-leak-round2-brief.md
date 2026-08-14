# Discovery socket leak: round 2 brief

## 1. Build and baseline

**Candidate:** `fork/fix/773-headunit-server-socket-leak` @ `69fad750` — three commits, **never
compiled anywhere.** R0 is a real gate this round, not a formality.

**Baseline:** `fix/bluetooth-handsfree-link-state` @ `f449557d` — the branch you substituted in
round 1, and the candidate's base. Your substitution was right and is now the official baseline:
`origin/main` @ `f9b56737` genuinely does not compile, for exactly the reason you found.

```bash
git fetch fork && git checkout 69fad750    # candidate
git checkout f449557d                      # baseline (already built in round 1)
```

**History was rewritten after this brief was first queued**, from `bb614110` to `69fad750`. If you
fetched the earlier tip, `git fetch fork && git checkout 69fad750` — a fast-forward will not work.
Nothing executable changed: `git diff bb614110 69fad750` is a comment in `CommManager.kt` and one
log message in `AapService.kt`, both from a review of whether the branch regressed Native AA. It
does not, and the review is why the log message moved — see §5.

`git diff f449557d 69fad750` is six files, all in the discovery and connection path; nothing in
`BluetoothHelper.kt`, `NativeAaHandshakeManager.kt` or the decoder.

Build with `build_hur.sh`, install with `install_and_launch.sh SKIP_BUILD=1`, record both md5s.

## 2. What this is and why it exists

Round 1 answered the question it was built for and changed the shape of the fix. Two things came
out of it.

**Your R1 confirmed the mechanism, and made it sharper than the brief modelled.** The head unit
server does not merely prefer the first connection; it binds to one and never rebinds for the life
of its process. Closing the offending connection did not recover it, 34 further cycles did not,
four minutes of waiting did not, cycling the hotspot did not. Only restarting the server did. That
is a stronger statement than the brief asked you to test and it is the one the fix is built on.

**Your R2 found something the brief did not ask about, and it is arguably the bigger defect.** Once
deaf, the app kept hammering: 32 failed handshakes in five minutes, and the phone accumulated ~24
CLOSE_WAIT sockets, one per cycle, none of which could ever have succeeded. We were manufacturing
fresh stranded connections against a server we had already established cannot answer.

So the candidate does two things, not one:

- **Stops us creating orphans.** The 5277 handover is wrapped in try/finally and a cancelled scan
  now closes the socket instead of dropping it; `stop()` no longer nulls `scanJob`, so
  `startScan()` joins the outgoing scan rather than running beside it; the 254 subnet probes became
  children of the scan job so `stop()` can actually reach them; `CommManager.connect(socket)` closes
  the socket it refuses; and `onAvailable` no longer does `stop(); startScan()`.
- **Stops us hammering a server that cannot answer.** Three consecutive handshakes where the peer
  accepts and sends nothing drop discovery to one attempt a minute, with one line in the log saying
  what the user has to do about it.

**What round 1 did not settle, and this round does not either:** whether the reporter's deafness is
caused by our leak or by his previous drive ending without a clean close. Your R2 deafened the
server with a Wi-Fi toggle and no leak in sight, which would have done the same on 3.1.0. Both
causes are real; the candidate only removes ours. Do not treat "the reporter is still broken" as a
verdict on this branch — that is not what any run here measures.

## 3. What is different about this round

- **R1 is a positive control that must FAIL to connect.** The fix stops us creating orphans; it
  cannot rescue a server something else has already claimed. A candidate that *recovers* from a
  held `nc` would mean the mechanism was never what round 1 measured. Read its PASS condition
  carefully — it is inverted relative to every other run here.
- **Everything from round 1's Setup notes still applies** and is now known rather than guessed:
  the phone's hotspot cannot be started over adb on this MIUI phone (`SecurityException: Uid 2000`)
  and needs one tap by the user; the head unit server needs one more; and the head unit **can**
  join a phone-hosted hotspot (`cmd wifi connect-network`, first try, `192.168.41.52/24`). Budget
  for those two manual actions up front, plus one more per run that needs to start from a working
  server.
- **Each run that deafens the server costs a manual restart to undo.** R1 and R3 both do. Sequence
  them so the user is asked as few times as possible, and say in Setup notes how many you needed.
- **`log-level=1` (DEBUG)** again, for the same reason as round 1 plus the new debounce line.
- R4 is the only run using Native AA (`wifi-connection-mode=3`), and it is there precisely because
  the candidate touches `CommManager` and `AapTransport`, which that path shares.

## 4. Settings keys this round needs

Via `set_hu_prefs.sh`, app stopped. Back up `settings.xml` first and restore it at the end.

```xml
<int name="wifi-connection-mode" value="2" />
<int name="helper-connection-strategy" value="3" />
<int name="log-level" value="1" />
```

R4 alone flips `wifi-connection-mode` to `3`; everything else runs on the block above.

## 5. The lines that decide every run

Verified with `grep -F` against `69fad750`. Match on the message text after the `|`.

New in the candidate:

```
NetworkDiscovery: Handover of
aborted; closing the probe socket
CommManager: Connect already in progress; closing the handed-over socket
Handshake: the peer accepted the connection and then sent nothing at all.
connections in a row without answering any of
discovery to one attempt every
NetworkMonitor: Ignoring repeat onAvailable within debounce window
```

**The backoff message now has two forms**, and which one you get is part of what R3 checks. Both
start with `<endpoint> has accepted N connections in a row without answering any of them. Slowing
discovery to one attempt every 60s.` Only when the endpoint is a head unit server — port **5277**,
which is what R3 uses — does it continue with the `stop and start it again on the phone, in Android
Auto's developer settings` advice. On any other peer (the phone dialling our own server on 5288, the
Nearby helper) the message stops after the first sentence, deliberately: those users have no head
unit server to restart. R3 runs against 5277 and must see the long form.

Carried over from round 1:

```
NetworkDiscovery: Starting scan...
NetworkDiscovery: Scan interrupted
NetworkDiscovery: Step 1 - Quick Gateway Scan
NetworkDiscovery: Found Headunit Server on
Auto-connecting to Headunit Server at
Handshake: No VERSION_RESPONSE within 2s (attempt
Handshake: Version response received (ret=
Handshake failed
```

The last two lines of the backoff message are assembled from concatenated pieces, so grep the
fragments above rather than the whole sentence.

## 6. Runs

### R0: build gate

The candidate has never been compiled. Build it, and run `run_unit_tests.sh`.

- **PASS**: builds, and the suite is green including the new 8/8 `UnresponsivePeerPolicyTest`.
- **FAIL**: report the compiler output and stop. Nothing below is meaningful against a branch that
  does not build.

### R1: the positive control — the fix must NOT paper over a deaf server

Repeat round 1's R1 exactly, on the candidate: hold `toybox nc 127.0.0.1 5277` on the phone, launch
the app, capture.

- **PASS**: the head unit still fails, still with `Handshake: No VERSION_RESPONSE within 2s` and
  `Handshake failed`, and now also logs `Handshake: the peer accepted the connection and then sent
  nothing at all.` A server somebody else has claimed is not ours to fix.
- **FAIL**: the head unit connects anyway. That would contradict round 1's own finding and means one
  of the two rounds measured something other than what it thought. Report and stop.

Then kill the `nc` and confirm it still does *not* recover without a server restart — round 1's
result, re-confirmed cheaply on the candidate.

### R2: the race is gone  ← **one of the two points of the round**

Server restarted by hand so it is working, app connected normally first, then the round-1 trigger:

```bash
adb -s <hu> shell svc wifi disable; sleep 3; adb -s <hu> shell svc wifi enable
```

Round 1 saw tid 61's scan still running when `onAvailable` launched tid 142 five seconds later.

- **PASS**: no two `NetworkDiscovery: Step 1 - Quick Gateway Scan` lines from different tids overlap
  — the second one must not start until the first scan's last line, and a `Starting scan...` with no
  `Step 1` after it for several seconds is the join working, not a hang. Over the whole capture,
  `grep -c "Found Headunit Server"` equals `grep -c "Auto-connecting to Headunit"`, and
  `grep -c "Gateway scan error"` is 0.
- **FAIL**: two gateway scans running at once, or `Found` exceeding `Auto-connecting`.

`NetworkMonitor: Ignoring repeat onAvailable within debounce window` may or may not appear; it
depends on how many callbacks the rejoin produces and is not a condition either way.

### R3: the hammering is bounded  ← **the other point of the round**

The run that measures the finding *you* contributed. Deafen the server the cheap way — hold an `nc`
as in R1 — then leave the app running for **6 minutes** without touching anything.

- **PASS**: the first three handshake attempts come at the old ~10 s cadence, then the cadence drops
  to roughly one a minute. Across the 6 minutes expect on the order of **6-9** `Handshake failed`
  lines, against round 1's 32 in 5 minutes. `Slowing discovery to one attempt every` appears
  **exactly once**, not once per cycle.
- **FAIL**: the cadence stays at ~10 s, or the explanation repeats every cycle.

Then the number that matters most, on the phone, at the end of the 6 minutes:

```bash
adb -s <phone> shell netstat -tn | grep 5277        # count CLOSE_WAIT rows
```

Round 1 measured ~24 stranded sockets in 5 minutes. **Report the count.** Anything in single figures
is the fix working; anything near 24 means the backoff is not reaching the loop that creates them.

### R4: Native AA is unharmed

`wifi-connection-mode=3`, the transport this rig normally uses. The candidate does not touch
`NetworkDiscovery` on this path — `startDiscovery()` returns immediately — but it does touch
`CommManager.startHandshake` and `AapTransport.handshake`, which mode 3 shares.

- **PASS**: a normal session forms, `Handshake: Version response received` then `SSL handshake
  complete`, video projects.
- **FAIL**: anything worse than the baseline's usual 45-90 s reconnect on this rig.

### R5: the manual network list still scans

The one plausible regression from `stop()` no longer nulling `scanJob`: `NetworkListFragment` calls
`stop()` on leave and `startScan()` on enter, and a guard that rejected a cancelled job would leave
the list permanently empty.

Open the network list, leave it, re-enter it, twice.

- **PASS**: the list populates every time, and `NetworkDiscovery: Starting scan...` appears on each
  entry.
- **FAIL**: the second or third entry produces no scan.

## 7. Do not re-run

- Round 1's R1 recovery investigation. Restarting the server is the only lever; hotspot cycling and
  waiting are both settled negatives, and re-proving them costs the user another manual restart.
- Whether the head unit can join the phone's hotspot. Round 1 established it can.
- Anything trying to make the reporter's own setup work end to end. His deafness may have a cause
  this branch does not touch (see §2), and no run here can separate the two.
- USB anything, Bluetooth anything.

## 8. Report back

1. **R0.** Whether it compiles, and the unit test count.
2. **R3's stranded-socket count on the phone** after 6 minutes, against round 1's ~24. This is the
   number that says whether the finding you contributed is actually fixed.
3. **R3's `Handshake failed` count** over the same 6 minutes, against round 1's 32 in 5.
4. **R1's verdict**, remembering it passes by still failing.

If R0 fails, stop at R0. If R1 passes but R2 and R3 both fail, the branch is not doing what it
claims and is worth reverting rather than iterating on.
