# Discovery socket leak: round 7 brief

## 1. Build and baseline

**Candidate:** `fork/fix/773-headunit-server-socket-leak` @ **`38a9e020`** — four commits.

**History was rewritten twice since round 6, deliberately.** The eight commits round 6 ran were
squashed to four (one per concern), and the branch was then rebased onto upstream `main` @
`a8830caa` (the 3.2.4 release plus the media-key work). So neither `5f193d30` nor `7f9250eb` is an
ancestor and a fast-forward will refuse; reset instead:

```bash
git fetch fork
git checkout fix/773-headunit-server-socket-leak
git reset --hard fork/fix/773-headunit-server-socket-leak    # must land on 38a9e020
```

The rewrite does not orphan round 6's result. Two checks were run before this brief was written:
`git diff 5f193d30 c6b933eb` is comments-only (not one executable line), which carries round 6's
verdict onto the squashed commits; and `git range-diff` across the rebase shows every commit
content-identical (the one non-`=` entry is import-context lines from main's media-key change),
with the rebased tree byte-identical to the precomputed merge tree. What the checks do **not**
cover is the fourth commit — see §2.

**Baseline:** none needed. No run in this round is an A/B.

**Rebuild and reinstall** (`adb install -r`), record the new md5. Round 6's APK
(`08c5493da211c5f4ebcdfdc88f0a25bf`) is two rewrites stale.

## 2. Why there is a round 7: the fourth commit has never been run — or compiled — anywhere

Round 6 settled the branch's story for the reporter: his double-probe leak is fixed on his exact
settings (R15 15/15, R16 10/10), Native AA is unharmed (R17), and the connect-in-progress guard
never needs to fire on this hardware (R20). None of that is in question and none of it is re-run
at full scale here.

What round 6 never saw, in any form, is the fourth commit ("Say why the discovery loop stopped,
and do not sweep a network that just went away"). It post-dates the round. And because this
repository's CI only runs on pull requests and pushes to `main`, **no machine anywhere has
compiled it yet** — R0 is its first compile, and this round is its first execution. It changes
two behaviours:

- **The two silent loop exits now say why.** `startDiscovery()`'s busy-gate and the end-of-sweep
  re-arm could both end the discovery loop without a word — the one thing a submitted log cannot
  be read for. Both now log at INFO.
- **A link-loss teardown leaves discovery down until a network returns.** The ordinary answer to a
  disconnect is to restart discovery 2 s later — which, right after a WiFi teardown, means
  sweeping whatever interface enumerates first instead of the network that just left. On this rig
  that is the modem bridge, and rounds 1–3 all measured it: blind 10 s sweeps of `10.243.202.*`
  until WiFi came back. The fourth commit skips that restart and lets the network's return revive
  the loop (`onAvailable` → `startScan()` on the instance, which a teardown does not null).

There is also one piece of round 6 unfinished business. R19 was INCONCLUSIVE, and the post-round
analysis found it **could never have fired**: the cross-instance guard's `inFlightScan` is a
companion-object field, so it is process-scoped — and R19 provoked with force-stop/relaunch
cycles, which give every cycle a fresh process where the field is null. No cadence would have
worked. The provocation has to stay inside one process, which is what R24 does.

## 3. What is different about this round

- **Test count changes again: expect 264**, not 245. The rebase brought main's media-key work
  along: `KeyDebouncePolicyTest` (13) and `MediaKeyRoutingPolicyTest` (6) on top of round 6's 245.
- **R0 is a real gate this round, not a formality** — it is this code's first compile anywhere. If
  it fails, stop and escalate; nothing downstream means anything.
- **R21 toggles the head unit's WiFi radio**, so §7a's radio-return quirk applies in full: budget
  for `svc wifi enable` not taking, verify `settings get global wifi_on`, nudge with
  `cmd wifi connect-network`, and never read the stall as a candidate defect.
- **R21 has a cellular caveat, stated up front.** The dormancy flag is cleared by *any*
  `onAvailable` — the network monitor is transport-agnostic by design. If this rig's modem brings
  up a network while WiFi is down, the `discovery resumes` line will fire early and sweeps of the
  modem subnet resume — which is exactly today's behaviour, i.e. a graceful degradation, not a
  crash. §5 says how to score that; it is a finding to report, not a FAIL of the mechanism.
- **R22's single tap during a live session is deliberate**, so the discard rule about unintended
  reconnects does not apply to that capture — but what the tap does to the session is itself
  evidence; record it either way.
- **Budget: the usual one-time hotspot/server restart at the top, then 0.** Round 6 needed no
  restart across 60+ service starts; this round has fewer. Any mid-round manual server restart is
  reportable on its own.

## 4. Settings keys this round needs

| Key | Element |
|---|---|
| `wifi-connection-mode` | `<int name="wifi-connection-mode" value="1" />` (R23/R24) or `2` (R21/R22) |
| `helper-connection-strategy` | `<int name="helper-connection-strategy" value="3" />` (R21/R22 only) |
| `log-level` | `<int name="log-level" value="1" />` (all runs) |

Every decisive line below is plain `AppLog.i` with no `LOG_VERBOSE` guard (checked at the guard,
per the standing rule), so `log-level=1` carries everything.

## 5. The lines that decide every run

Verified with `grep -F` against `38a9e020`.

New in the fourth commit — the subjects of this round:

```
AapService: Discovery not started — a connection is live or being set up
AapService: Discovery loop ends — a connection is live or being set up
AapService: Discovery loop ends — the wireless server is gone
leaving discovery down until a network comes back
NetworkMonitor: network is back after a link-loss teardown; discovery resumes
```

> The `leaving discovery down` line is a fragment on purpose — the full message is a two-part
> string concatenation in the source. Grep the fragment, never a line reconstructed by hand. The
> dashes in the first three are em-dashes (—); copy them from this brief, do not retype.

Carried over from previous rounds:

```
NetworkDiscovery: Starting scan...
NetworkDiscovery: Scan interrupted
NetworkDiscovery: Scanning subnet:
NetworkDiscovery: Found Headunit Server on
Auto-connecting to Headunit Server at
NetworkDiscovery: waiting for an in-flight probe before scanning
NetworkDiscovery: in-flight scan promoted to continuous
CommManager: Connect already in progress; closing the handed-over socket
AapService: network changed during the last scan; rescanning immediately
AapService: link-loss teardown finished in
with a live session — closing it now, while the link still
Handshake: Version response received (ret=
```

`NetworkDiscovery: Scanning subnet:` is the line that names the subnet a deep sweep is on
(`Scanning subnet: 192.168.41.*`), and it is how R21 tells a legitimate rescan from a
modem-bridge sweep. Rounds 1–3's blind sweeps were `10.243.202.*`.

## 6. Runs

### R0 — build gate (this code's first compile anywhere)

Build, install, `run_unit_tests.sh`. Expect **264/264**: round 6's 245 plus
`KeyDebouncePolicyTest.xml` `tests="13"` and `MediaKeyRoutingPolicyTest.xml` `tests="6"`.
`DiscoveryModePolicyTest` stays 5, `LinkLossTeardownPolicyTest` stays 7,
`UnresponsivePeerPolicyTest` stays 8. Report the new md5. **A failure here stops the round —
escalate**; it would be the first compile failing, which is a finding about the rebase, not
about the rig.

### R21 — link-loss dormancy (the point of the round)

Settings: `wifi-connection-mode=2`, `helper-connection-strategy=3`, `log-level=1`. Phone hotspot
topology, head unit joined to it, server running — round 6's R16 setup exactly. Establish a
session and confirm video.

Then, with the session live:

```bash
adb shell svc wifi disable
sleep 60                      # six would-be 10s sweep cycles on the old behaviour
adb shell svc wifi enable     # plus §7a nudges until the head unit is back on the hotspot
```

Expected sequence in the capture, in order:

1. `with a live session — closing it now, while the link still` — the teardown announcement.
2. `AapService: link-loss teardown finished in` — expect a number well under 1500 ms (round 4's
   R7 measured 181/198 ms for the same close).
3. ~2 s later: `leaving discovery down until a network comes back`.
4. **Nothing from `NetworkDiscovery` for the rest of the 60 s window.** Count
   `Starting scan...` and `Scanning subnet:` between line 3 and line 5 — both must be **0**.
5. After WiFi is back: `NetworkMonitor: network is back after a link-loss teardown; discovery
   resumes`, then a scan whose `Scanning subnet:` names the hotspot's subnet, then the session
   reconnects on its own — `Handshake: Version response received` with **no manual server
   restart**. That last part is the whole branch working end-to-end: the teardown closed the
   session properly, so the server survived it.

**PASS** — all five, in order, zero scans inside the window, zero manual restarts.
**FAIL** — any scan lands between teardown and the resume line, or the resume line never comes
once WiFi is confirmed back, or the server needed a restart to reconnect.
**The cellular caveat (§3):** if line 5 fires while `wifi_on` is still 0, the modem cleared the
flag. Score the window from line 3 to that early resume instead (still must be scan-free), record
the resume timestamp against the WiFi state, and report which subnet the resumed sweep named.
That outcome is the mechanism working with a rig-specific network arriving early — report it
exactly, do not force it to PASS or FAIL silently.

Run R21 twice. The second pass is cheap (the topology is already up) and a dormancy bug that
depends on receiver or callback state would show on the repeat, not the first pass.

### R22 — the loop now says why it stopped

Two parts, one cheap, one nearly free.

**(a) One deliberate WiFi-button tap during a live session.** Same topology as R21, session live
~30 s with video confirmed. Locate the button via `uiautomator dump` (round 6 found it at
`bounds="[780,220][1008,448]"`, center `894,334` — re-dump rather than trusting the coordinates)
and tap **once**. The button's action re-initialises the wireless mode and requests discovery
while the session is up, so expect
`AapService: Discovery not started — a connection is live or being set up` — once or twice
(the re-init requests discovery twice; both hit the same gate). **PASS** — the line appears and
`Starting scan...` does **not**. **FAIL** — a scan starts while the session is live.
Separately, record what the tap did to the session (did video continue; did a second
`Handshake: SSL handshake complete` appear). Whatever happens is pre-existing behaviour worth
knowing, not this branch's defect — report it, do not chase it.

**(b) Harvest, no device time.** In every capture this round where a session connected, the
sweep that found the server still runs to completion and schedules its re-arm; ~10 s after the
sweep finishes, `AapService: Discovery loop ends — a connection is live or being set up` should
appear. Count it per capture. Zero everywhere would mean the re-arm path is not reaching its new
log — worth reporting, not worth a dedicated run.

Also grep every capture for `Discovery loop ends — the wireless server is gone`. It needs a
mode change to land inside a 10 s re-arm window, which nothing in this round stages
deliberately. Report the count (likely 0) — informational only, no verdict.

### R23 — post-rebase spot-check of round 6's headline

Settings: `wifi-connection-mode=1`, `log-level=1`, server running. R15's loop at a third of the
scale — **5** force-stop/relaunch cycles, ~20 s apart:

```bash
for i in $(seq 1 5); do
  adb shell am force-stop com.andrerinas.headunitrevived
  adb shell am start -n com.andrerinas.headunitrevived/com.andrerinas.openheadunit.main.MainActivity
  sleep 20
done
```

**PASS** — `found` == `handedover`, every instance shows 1 `Starting scan...` / 0
`Scan interrupted` (per-boundary, as round 6 measured it), 5/5 connect, no manual restart.
The range-diff already proves the code is round 6's; this run proves the rebase onto 3.2.4
changed nothing observable. Five launches is enough for that question — do not extend it.

### R24 — the cross-instance guard, provoked in-process (R19 done right)

Settings: `wifi-connection-mode=1`, `log-level=1`. **Stop the head unit server on the phone for
this run** (Android Auto developer settings) — with nothing to find, a sweep runs the full
254-host subnet and stays mid-probe for many seconds, which is the window R19 never had. No
session can form, so `found`/`handedover` are trivially 0/0; the guard line is the whole run.

App on its home screen, service running. A second or two after a `Starting scan...`, tap the
WiFi button **twice, ~500 ms apart** (same uiautomator method as R22a). Both taps land in one
process: each re-init cancels the sweep and nulls the instance, so the second request builds a
fresh instance whose guard must find the prior probe still unwinding.

**PASS** — `NetworkDiscovery: waiting for an in-flight probe before scanning` appears at least
once across up to 5 attempts.
**INCONCLUSIVE** — never appears after 5 attempts; record the cadences tried. Do not record as
PASS, and do not spend more than 5 attempts — the JVM cannot test this (it is a timing window),
but 60 clean service starts in round 6 already bound the risk.

`Scan interrupted` **will** appear in this capture — the taps cancel sweeps deliberately. The
1-and-0 shape rule does not apply here; do not report it as a FAIL.

Restart the head unit server afterwards if any later run needs it.

### R20 (standing) — nothing hands over a socket that gets refused

Over every capture in this round:
`grep -c 'CommManager: Connect already in progress; closing the handed-over socket'` — report
the count per capture. Zero everywhere is the expected answer.

## 7. Do not re-run

- **R15 at full scale, R15b, R16, R17.** Round 6 settled them; the range-diff check in §1 is
  what carries those verdicts across the rewrite. R23's five launches are the only overlap this
  round needs.
- **Anything shutdown- or teardown-policy-related beyond R21.** Round 4's R7 and round 5's R10
  answered the WiFi-toggle close and the shutdown close; R21 exercises the same close as a step,
  not as a question.
- **R18 (USB session vs WiFi toggle).** Permanently UNTESTABLE on this rig
  (`host_connected=false`, the port is the adb link). The policy tests carry it; nothing changed.
- **Pre-checking port 5277 with anything.** Standing rule; verify passively via `/proc/net/tcp6`.

## 8. Report back

1. **R0: 264/264, and the md5.** Say explicitly whether the build compiled clean — it is the
   first compile of this code anywhere.
2. **R21: the five-line sequence with timestamps, the scan count inside the window (must be 0),
   the teardown duration in ms, and whether the reconnect needed a server restart.** Both passes.
   If the cellular caveat fired: the early-resume timestamp against `wifi_on`, and the subnet the
   resumed sweep named.
3. **R22a: did `Discovery not started` appear, how many times, and what the tap did to the
   session.**
4. **R22b: the per-capture count of `Discovery loop ends — a connection is live or being set
   up`**, and the (likely 0) count of the `wireless server is gone` variant.
5. **R23: found/handedover, the per-instance shape, connects out of 5.**
6. **R24: did `waiting for an in-flight probe before scanning` appear, and on which attempt.**
7. **R20: the refused-handover count per capture.**
8. **Manual server restarts this round, and where.**
9. Anything `in-flight scan promoted to continuous` or `network changed during the last scan`
   showed up around — informational.
