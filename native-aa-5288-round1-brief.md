# native-aa-5288, round 1 brief: the head unit hosts a network and then listens on nothing

**Candidate:** `fix/video-and-wireless-stack` @ `e9f5d2b6` on `fork` (`o-jcardenass/open-headunit`).
**565 JVM tests.** One APK for the whole round; there is no baseline build.

**Fetch and reset, do not pull.** History was rewritten on 2026-08-20. The three refs the recent
briefs pinned, `fix/video-stack`, `fix/p2p-legacy-5ghz` and `fix/wifi-direct-lifecycle`, were folded
into this single branch. They still exist and are still pushed, so a `pull` on one of them gets you
a superseded tree that builds and looks right.

```bash
git fetch fork
git checkout -B fix/video-and-wireless-stack fork/fix/video-and-wireless-stack
git rev-parse HEAD          # must print e9f5d2b6feb2c0b4026607629649ed4cbecc68a2
```

The compaction is content-preserving: the tip's tree equals the old tip's, `git diff 828bada5 HEAD`
is empty, and the pre-compaction tip is tagged `stack-pre-compaction-20260820` if you need the old
SHAs to resolve.

**Round 5's channel-14 fix is on this branch.** `307d85f2`, the one-line
`P2pOperatingChannelPolicy.frequencyMhzFor(14) -> 2484` you committed on top of `224cae32`, is folded
into `3a5d2e94` here, with the constant now shared with `P2pChannelPolicy` and a round trip through
both objects asserted. R0 will not hit that failure again. Thank you for catching it.

---

## 1. Why this round exists

**This is a new thread.** Everything else on this branch has been on hardware already or is queued
elsewhere. Two of its five commits have not, and have never been compiled anywhere: `c2efedda` and
`e9f5d2b6`, both on the Native AA handshake path. R0 is therefore a real gate, not a formality.

A reporter's head unit did every part of a wireless connection right and still never connected. It
formed its 5 GHz P2P group, woke the phone over Bluetooth, had its BSSID accepted, delivered
credentials three times per group, and then aborted the handshake with `nothing is listening on port
5288`. About every four seconds. For the entire length of the capture.

Two things made that state permanent and invisible.

**It latched.** `startWirelessServer()` opened with `if (wirelessServer != null) return`, which reads
"assigned" as "running". The bind happens asynchronously inside the coroutine the server launches; if
it throws, or the accept loop exits, the `finally` clears the listening flag and closes the socket
but leaves the object in the field. Every later call then returned immediately and nothing rebound.
Only a full `initWifiMode()` cleared that field, so the port stayed dead for the life of the mode.

**It was silent.** Of the four ways a start can go, two printed nothing at all: a start skipped by
that guard, and a coroutine cancelled before it bound (the old `catch` logged only `if (isActive)`).
So "never started", "started and skipped", "started and threw" and "started and cancelled" were
indistinguishable in a log. That is why two otherwise complete reporter captures cannot be used to
attribute this failure, and it is the larger half of what the fix does.

`aap/WirelessServerRestartPolicy.kt` now decides between leaving a server alone, starting one,
waiting for one that is still binding, and replacing one that died. The waiting case is load-bearing:
assigned-and-not-listening is normal for a second or so while the bind retries, and treating that as
dead would tear down a server about to succeed, whose replacement would then race it for the same
port. `job?.isActive` is what separates the two, and `SO_REUSEADDR` does not help there, because it
covers a port in `TIME_WAIT` and not one with a live listener on it.

Rebuilds are bounded at **3 per 60 s, 10 s apart**, with the budget cleared by a successful bind,
because the caller is a handshake that retries every few seconds and a port that can never bind would
otherwise become a rebuild loop worse than the stall it replaces. **R3 checks that bound**, and a
failure there matters more than anything else in this round.

### The honest limit, stated first

**This does not prove the reporter's root cause.** No bind failure is recorded in either of their
captures, and neither is a successful start. What the branch changes is that the *next* capture says
which. Do not report any run here as confirming the reporter's mechanism; report what the lines say.

### The second commit

`c2efedda` carries two smaller fixes on the same path, both defects in `main` rather than regressions
from the video work:

- `ServerSocket(5288).apply { reuseAddress = true }` set `SO_REUSEADDR` after the constructor had
  already bound, where it does nothing. While the previous peer sits in `TIME_WAIT` the rebind throws,
  so the phone is woken by the poke and handed nothing. The socket is created unbound now, the option
  set, then bound explicitly.
- The four credential fields were plain `var`s written by `WifiDirectManager`'s delivery thread and
  read by the handshake coroutine. A torn read hands the phone the SSID and passphrase of one group
  beside the BSSID of another, which Gearhead rejects as `WIFI_INVALID_BSSID` with no clue why; and
  the null check and the `!!` that followed it were separate reads, so an invalidate landing between
  them surfaced as `Handshake error: null` and named nothing. They are one immutable snapshot behind
  one `@Volatile` now.

Its third fix, the manual poke button re-arming closed listeners, **you already proved on hardware**
in round 5's R4.2, at 23:47:53.486 on 2026-08-19. It is not re-tested here. See §6.

---

## 2. What is different about this round

- **R2 and R3 need the rig to hold port 5288 against the app.** That lever has never been used in
  this channel, so it gets its own check step and its own escape hatch. If neither method binds the
  port, R2 and R3 are **UNTESTABLE**, and that is a result: say so in Setup notes and go on to R4.
- **The whole round is four measured runs and a gate**, because the parts of this branch that need
  more than that have already had it. Budget the time into R3 instead, which is the run with the
  most ways to go wrong.
- §7a's "both poke targets fail on this rig, usually but not always" still applies: **no run's
  verdict here depends on a poke connecting.** R2 and R3 need the phone to *arrive over Bluetooth*,
  which round 5 showed happens on its own within seconds on this rig, poke or no poke.
- This rig has **no USB accessory path and no shared WiFi**, so Native AA is the only transport it
  has. That makes it the right rig for this thread and there is no substitute run to design.

---

## 3. Settings keys this round needs

| Key | Type | Element | Why |
|---|---|---|---|
| `wifi-connection-mode` | int | `<int name="wifi-connection-mode" value="3" />` | Native AA. The only transport this rig has. |
| `native-ap-transport` | int | `<int name="native-ap-transport" value="0" />` | WiFi Direct, the default. Set it explicitly so the round is not at the mercy of a leftover. |
| `log-level` | int | `<int name="log-level" value="1" />` | DEBUG. See below. |

**Why DEBUG and not VERBOSE.** The policy's outcomes log at four different priorities:
`START`, `AWAIT` and `NO_OP` are `AppLog.d`, `REBUILD` is `AppLog.w`, `BACKOFF` is `AppLog.i`. None
of them is wrapped in `if (AppLog.LOG_VERBOSE)`; the guard was checked rather than the call, as §1
requires. DEBUG carries every line this round reads, and VERBOSE would only bring this unit's driver
flood closer to wrapping the ring buffer inside a run.

No fault-injection keys, no `debug-force-p2p-band-24`, no `debug-force-memory-profile` in any run.

---

## 4. The lines that decide every run

Copied from `e9f5d2b6` and verified with `grep -F`; each hits exactly once in `app/src/main/java`,
except `ACTIVELY LISTENING`, which has a primary and a secondary radio form. Two of them contain a
real em dash character in the source, so grep for a shorter substring if your shell fights you.

The start decision, `AapService.kt:2346`:

```
AapService: Starting the wireless server on 5288 - no server yet.
AapService: Wireless server not started - already listening.
AapService: Wireless server not started - a server is starting and has not finished binding its port.
AapService: Rebuilding the wireless server on 5288 - a server exists but its port is not bound (attempt N).
AapService: Wireless server on 5288 is not accepting connections - the port would not bind on the last attempt, waiting before trying again.
```

The bind itself, `AapService.kt:3188` onward:

```
WirelessServer: binding port 5288...
Wireless Server listening on port 5288
WirelessServer: port 5288 did not bind on attempt N of 3 (BindException: ...). Retrying in 700ms.
WirelessServer: stopped before port 5288 could be bound.
WirelessServer: port 5288 released (CancellationException).
Wireless server error
```

The repair, `AapService.kt:2459` and `NativeAaHandshakeManager.kt:1223`:

```
AapService: the Bluetooth handshake found port 5288 unbound. Trying to start the wireless server.
AapService: port 5288 is bound now.
AapService: port 5288 is still not bound 4000ms after trying to start it.
NativeAA: port 5288 was not bound, and is now. Carrying on with the handshake.
NativeAA: Handshake aborted — nothing is listening on port 5288 after 3s, and starting it here did not work either,
```

And the landmarks a session is judged by, unchanged from every other round:

```
WifiDirectManager: Standard createGroup SUCCESS!
WifiDirectManager: SUCCESS - Providing credentials to listener. SSID=..., IP=..., BSSID=...
WirelessServer: Incoming connection detected
Handshake: SSL handshake complete
```

**`WirelessServer: binding port 5288...` prints outside the coroutine on purpose.** It is what
separates "never asked" from "asked, and the answer never came". If you ever see it with none of the
three possible follow-ups, that absence is itself the finding and belongs in the report.

---

## 5. Runs

### R0: build and unit-test gate

**PASS / FAIL. A FAIL stops the round; escalate.**

Inventory `hur-wifi-test-scripts/` first and record which scripts you used (§5). `build_hur.sh`,
`run_unit_tests.sh` and `install_and_launch.sh` covered it in round 5.

- **Expect 565 tests**, up from round 5's 552. `WirelessServerRestartPolicyTest` is new: 179 lines,
  and if it is missing from the run you are on the wrong branch.
- **This is the first compile of `c2efedda` and `e9f5d2b6` anywhere.** A Kotlin error here is a real
  finding and the most useful thing this round could produce. Quote it in full.
- Record the APK md5 and confirm which APK is live before trusting any run.
- Back up `settings.xml`, restore and diff at the end.

### R1: a start that works now says so

The reporter-facing half. Nothing unusual in the setup: clean-run protocol, the three settings above,
one native session.

**PASS** requires, in order:

```
AapService: Starting the wireless server on 5288 - no server yet.
WirelessServer: binding port 5288...
Wireless Server listening on port 5288
```

then the ordinary landmarks through `WirelessServer: Incoming connection detected` and
`Handshake: SSL handshake complete`.

**FAIL** on any of: a missing line from that three, more than one bind sequence, or any occurrence of
`Rebuilding the wireless server` or `is not accepting connections` in a run where the port was never
contested.

Report the elapsed time from `binding port 5288...` to `Wireless Server listening on port 5288`. It
should be a few milliseconds. Anything above a second means the bind is retrying on a rig where
nothing should be holding the port, which is worth knowing on its own.

### R2: a server that failed to bind gets repaired. The point of the round.

The positive control, and it runs entirely on the candidate. No second APK.

**First, establish the lever.** With the app stopped, take port 5288 on the head unit and prove it is
taken:

```bash
adb reverse tcp:5288 tcp:1
adb shell netstat -ltn | grep -a 5288        # must show a LISTEN on 5288
```

If `adb reverse` does not produce a listener, try `adb shell toybox nc -L -p 5288` in a background
shell and re-check. **If neither binds it, R2 and R3 are UNTESTABLE.** Record which methods you tried
and what they printed, and move to R4. Do not invent a third method.

**Then the run:**

1. Port held. Clean-run protocol, settings written, capture started.
2. Launch the app and let it settle 15 s. Expect `WirelessServer: binding port 5288...`, then
   `did not bind on attempt 1 of 3`, `did not bind on attempt 2 of 3`, then `Wireless server error`
   at E. The server object is now assigned, not listening, and its coroutine has ended: the exact
   state that used to be permanent.
3. Release the port: `adb reverse --remove tcp:5288` (or kill the `nc`). Confirm with `netstat` that
   nothing holds it. Wait 5 s.
4. Bring the phone up and let the handshake run.

**PASS** requires the whole repair chain, in order:

```
AapService: the Bluetooth handshake found port 5288 unbound. Trying to start the wireless server.
AapService: Rebuilding the wireless server on 5288 - a server exists but its port is not bound (attempt 1).
WirelessServer: binding port 5288...
Wireless Server listening on port 5288
AapService: port 5288 is bound now.
NativeAA: port 5288 was not bound, and is now. Carrying on with the handshake.
```

followed by `WirelessServer: Incoming connection detected` and `Handshake: SSL handshake complete`.

**FAIL** on: the abort text (`Handshake aborted — nothing is listening on port 5288`), or no session
within 90 s of the phone coming up.

Discard rules still apply here. There should be exactly **one** `createGroup SUCCESS!` in this
capture, from the launch in step 2.

**Measure and report:** seconds from `found port 5288 unbound` to `Incoming connection detected`.

### R3: the bound holds, and the repair stays narrow

**This is the run whose failure would be worse than the bug.** Same setup as R2, but the port is
**never released**. Run for **5 minutes** with the phone arriving repeatedly, so the handshake keeps
asking.

**PASS** requires all four:

1. At most **3** `Rebuilding the wireless server on 5288` lines in any 60 s window.
2. Each of them at least **10 s** after the previous one.
3. After the third, `AapService: Wireless server on 5288 is not accepting connections - the port
   would not bind on the last attempt, waiting before trying again.` at INFO, and no further
   rebuilds inside that window.
4. **Exactly one** `createGroup SUCCESS!` in the whole capture, and **one** value from
   `grep -ao "p2p-wlan0-[0-9]*" rN.txt | sort -u`.

Condition 4 is the real subject. The repair replaces the `WirelessServer` object and must **never**
call `stopWirelessServer()`, which also sets `activeWifiMode = -1` and would make the poke and
auto-start paths force a full re-init: a torn-down P2P group and a new SSID, in the middle of the
handoff being rescued. A second group in this capture is that failure, and it is a FAIL.

**Report as numbers:** the rebuild count, the gap in seconds between each pair, the BACKOFF line
count, and the `p2p-wlan0-N` set.

Note that grep counts on these captures need `-a` (§7a): without it an absent pattern and a present
one both come back empty.

### R4: clean control, and the one thing the credential snapshot can be asked for

One untouched 10-minute native session, nothing held, nothing poked.

**PASS** requires zero of each: `Rebuilding the wireless server`, `is not accepting connections`,
`Wireless server error`, `NativeAA: Handshake error: null`.

Then the snapshot's only hardware-visible claim. Collect every line of the form:

```
WifiDirectManager: SUCCESS - Providing credentials to listener. SSID=..., IP=..., BSSID=...
```

Expect **3 to 4 per group**; that is normal and documented. **Every one of them must carry the same
SSID and the same BSSID.** A pair that disagrees is the torn read this commit is about, and would be
the best evidence this round could produce.

**Pre-registered, so it is not read as a failure:** a race that does not fire is not evidence it is
fixed. If the credential lines all agree, that is what the run says and nothing more; the coverage
for the torn read stays on the JVM side. An **INCONCLUSIVE** on that half with a PASS on the four
zero-counts is the expected and correct outcome.

---

## 6. Do not re-run

- **The manual poke re-arm.** Round 5's R4.2 settled it on this exact code:
  `AapService: Native AA listeners are closed — re-arming before the poke.` at 23:47:53.486, a fresh
  `ACTIVELY LISTENING` 52 ms later, and a successful HFP-AG poke after it. Your isolation recipe (let
  a **full** handoff complete, including `AA Server socket closed after successful handoff` and
  `Incoming connection detected`, then fire the intent at the *same still-running* service instance)
  is the only one that reaches the branch, and it is now in this thread's record rather than only in
  round 5's. Nothing on this branch changes it.
- **The WiFi Direct lifecycle fixes.** Round 5's R4.1, PASS.
- **The video half.** `a3990e4c` and `3a743c11` were validated in `video-pipeline-stack` round 2 and
  exercised again across `release-next` rounds 1 to 6.
- **The band levers.** `3a5d2e94` is what `link-stall-periodic-scan-round4-brief.md` is still queued
  for. That brief pins `c2983fc7`, from before the compaction, and **now builds this branch**;
  everything else in it stands, including its 541-test figure, which is 565 here.

---

## 7. Report back

Three numbers decide whether this ships:

1. **R3's rebuild count in its worst 60 s window**, and the gaps in seconds. Must be at most 3, at
   least 10 s apart.
2. **R2's elapsed time** from `found port 5288 unbound` to `Incoming connection detected`.
3. **R3's `p2p-wlan0-N` set size.** Must be 1.

Plus, from R0: whether the two never-compiled commits compile, and the test count.

Everything else goes in `native-aa-5288-round1-results.md` in the standard format (§7), committed to
this branch alongside this brief.
