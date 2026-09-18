# release-test — round 1 brief: the two branches merged, and a third-party PR nobody has compiled

Two parts, one round. **Part A** is the release candidate: `fix/session-lifecycle-and-diagnostics`
and `feat/render-side-concealment` merged into one tree, which is how they will ship and how they
have never been measured. **Part B** is upstream PR #845 from an outside contributor.

**Run Part A first, to completion, on a device with only one build installed.** Part B deliberately
installs a *second* package and that changes what Part A is measuring. There is an uninstall step
between them and it is not optional.

## 1. Build and baseline

**Candidate:** `release/test`, which you build by merging the two branches. It does not exist on any
remote yet, so it has no SHA until you make one. Record the SHA you get.

```bash
git fetch fork
git checkout -B release/test fork/fix/session-lifecycle-and-diagnostics
git merge fork/feat/render-side-concealment
git rev-parse HEAD          # record this in your results header
git log --oneline -12
```

Expected parents: `afd8b7ca` (session-lifecycle, 5 commits over `main`) and `d30fe1d8`
(render-side-concealment, 6 commits over `main`). The merge is clean here on a dry run: four files
are touched by both branches (`AapTransport.kt`, `AapProjectionActivity.kt`,
`ProjectionWatchdogPolicy.kt`, `ServiceDiscoveryResponse.kt`) and all four resolve without
conflict. **If git asks you to resolve anything, stop and escalate** — it means one of the branches
moved after this brief was written.

**Baseline:** `origin/main` @ `562c8dcf`. Build it too and record both md5s. Part A does not need an
A/B for its verdicts, but B2 needs a second installable APK and the baseline is the honest one to
use.

**History was rewritten.** Both branches were rebased onto the new `main` after their last rounds,
so **every SHA in every existing brief on this transfer branch is stale**. In particular
`feat/render-side-concealment` absorbed the four commits that used to live on
`fix/wire-corruption-escalation`, so it is now one 6-commit branch, not "8 commits on a baseline
branch" as `render-side-concealment-round1-brief.md` describes it. Do not try to check out any SHA
that brief names.

**Two commits in Part A have never been briefed or run at all**, and one of them has never been
compiled by anything: `afd8b7ca` says so in its own commit message ("there is no Android toolchain
on the authoring machine, so CI is the first compile of every line here"), and `8fc20bf0` has no
thread row in the README. A0 is a real gate for both.

Expected unit-test total on the merged tree: **669** (main 565, +64 from session-lifecycle, +40
from render-side-concealment, no shared test files). If you get a different number, say so in Setup
notes with the breakdown; it means the merge dropped something.

## 2. What this is and why it exists

Everything in Part A has passed a round already, but always on its own branch. The reason to run it
again merged is the four-file overlap, and specifically `AapTransport.kt` and
`ProjectionWatchdogPolicy.kt`, where two independent pieces of work now sit on top of each other:
session-lifecycle added the link-gap instruments and the watchdog's idle-awareness, and
render-side-concealment added the keyframe-cycle refund and the concealment window that races the
same watchdog clocks. `02bc5f39` split `lastOutputMs` from `lastFrameRenderedMs` precisely so the
concealment window would not be read as a stall. **A merged tree is the first place that split can
actually be wrong.**

Part B is upstream PR #845. It began as a change to the AAP read loop, which its author has already
withdrawn; what is left is a Nearby tunnel fix, an `AppLog` stack-trace fix, and a debug build that
installs alongside a store build as `com.andrerinas.headunitrevived.dev`. A desk review found five
blocking issues in it. **CI has never run on that branch** (both workflows sit at
`action_required`, awaiting maintainer approval, and the head commit has zero check runs), so B0 is
the first compile anyone has done of it, including its own merge resolution. That result alone is
worth the round.

## 3. What is different about this round

- **Part B installs a second package.** `com.andrerinas.headunitrevived.dev`. Everything you know
  about `$PKG` stops being true for those runs. See §3a below, it is the biggest trap here.
- **B2 is expected to FAIL in the sense of "the thing does not work".** That is the *point* of the
  run, not a problem with it: the desk review says two builds cannot share port 5288, and B2 is how
  we find out whether that is true on real hardware. Record what happens; a clean bind by both
  would refute the review, which is just as useful.
- **The USB half of `8fc20bf0` is UNTESTABLE on this rig** (§7a: no USB accessory path,
  `host_connected=false`). Do not attempt it. Only the poke-loop cancellation half is reachable and
  that is A5. Its coverage is `UsbSessionQuiescePolicyTest` (8 tests) in A0.
- **The unjoined arm of `79ad8ad4` is UNTESTABLE on this rig** (§7a: permanently associated to
  `Pegue Cdesta`, 5500 MHz). Only the joined arm is checkable, as A6. Its coverage is three JVM
  tests in `StationCoexistencePolicyTest`.
- **The pre-flight dialog of `afd8b7ca` is UNTESTABLE by the house rules.** It fires from an
  `onOptionSelected` callback in Settings, it cannot be triggered by writing `settings.xml`, §7a
  says settings categories are not deep-linkable, and the template bans scrolling the settings list
  with adb. Its coverage is `NativeCredentialsPreflightPolicyTest` (17 tests) in A0. The *other*
  half of that commit, the static-BSSID rejection, is reachable and is A7.
- **Nearby is reachable this round.** The testing phone has the Wireless Helper installed, so B4
  and B5 are real runs rather than the pre-registered dead end they would otherwise have been. B5
  needs a *failed* connection attempt to set up and that may not be forcible; it is pre-registered
  INCONCLUSIVE if you cannot produce one.
- **Rate 87 is still pre-calibrated** for the default post-connect screen (0.971 flag-10
  candidates/s, measured in wire-corruption round 1). Reuse it. §7a: injection does nothing at the
  default rate of 300, so it must be set explicitly.
- **The idle-screen candidate trickle is real** (60-70 s stalls in candidate flow at healthy fps).
  If a single-fault run has not fired by its cap, extend observation on the same live session
  rather than restarting, as `render-side-concealment-round1-results.md` C3 did.

### 3a. The `$PKG` trap in Part B

The rig's scripts hardcode `PKG=com.andrerinas.headunitrevived`. During Part B the build under test
is `com.andrerinas.headunitrevived.dev`. So `set_hu_pref.sh`, `set_hu_prefs.sh`, `set_pref.sh`, any
`run-as`, any `adb pull` of a log directory, and the §5 md5 check **all silently address the wrong
app** unless you override the package. Symptoms are exactly the kind that eat a round: settings that
"did not apply", a capture with no new lines in it, an md5 that matches the build you were not
testing.

Before any Part B run, set it explicitly and verify:

```bash
DEVPKG=com.andrerinas.headunitrevived.dev
adb shell pm list packages | grep headunitrevived     # expect BOTH lines in B1 onward
adb shell md5sum $(adb shell pm path $DEVPKG | cut -d: -f2 | tr -d '\r')
adb shell run-as $DEVPKG cat shared_prefs/settings.xml | head
```

Log files for that build land in `/sdcard/Android/data/com.andrerinas.headunitrevived.dev/files/`,
not the path you are used to. State in Setup notes which package each run configured.

## 4. Settings keys this round needs

All runs: `log-level=2`. Every new line on both branches prints at INFO, WARN or ERROR and
`log-level=2` carries all of them. Do not use VERBOSE: §7a says this unit's driver stack floods
logcat and VERBOSE costs evidence by wrapping the ring buffer.

§7a standing rule: `settings.xml` survives between rounds and carries the last thread's non-defaults.
**Diff against a fresh backup at the start of this round and state the delta in Setup notes, even if
it is zero.** The last thread here was `render-side-concealment`, so expect leftover
`debug-video-fault-*` keys.

| Key | Type | Value | Why |
|---|---|---|---|
| `log-level` | int | `2` | INFO. Carries every line in §5. |
| `wifi-connection-mode` | int | `3` | Native AA. The only usable transport on this rig (§7a), and the mode the VPN lever and poke loop need. |
| `enable-audio-sink` | bool | `true` | A false here makes the audio gap series structurally unreachable. media-gap round 1 lost a run to exactly this. Set it to `false` only for A6. |
| `keep-dummy-vpn-during-session` | bool | `true` | The VPN lever. A4 only. |
| `view-mode` | int | `1` | TextureView, as in session-vpn round 2. |
| `debug-video-fault-injection` | int | `3` for A3; delete for A2 | Truncation. §7a: injection is inert without an explicit rate. |
| `debug-video-fault-rate` | int | `87` | Pre-calibrated for the default post-connect screen. |
| `debug-video-fault-budget` | int | `1` | One fault. |
| `static-bssid` | string | see A7 | A7 only. Clear it again afterwards or A2's group formation inherits it. |
| `wifi-connection-mode` | int | `2` | B4, B5, B6 only. |
| `helper-connection-strategy` | int | `2` for B4/B5, `0` for B6 | 2 = Google Nearby, 0 = common WiFi / NSD. |
| `log-source` | int | `1` | B3 only (`APPLOG_FILE`). Set it back to `0` afterwards. |

Confirm the exact key strings against the merged tree before writing them; `Settings.kt` is the
authority and this table is transcribed.

## 5. The lines that decide every run

All verified with `git grep -F` against `afd8b7ca` and `d30fe1d8` on 2026-08-21. Remember `grep -a`,
always (§7a).

**Three of these are split across a string concatenation in source, so grep for the prefix only.**
They are marked (prefix).

```
Part A — session-lifecycle
inbound video quiet                            media gap series, INFO
inbound audio quiet                            media gap series, INFO
inbound link quiet                             whole-link series, INFO
uplink blocked on                              send-side stall series, INFO
is connected to another WiFi network           coexistence, joined arm (the only reachable arm)
not connected to any other WiFi network        coexistence, unjoined arm (UNTESTABLE here)
Disconnecting the other network                MUST BE ABSENT - deleted prescription, regression guard
Audio sink is off in Settings                  service discovery says the sink is off
dummy VPN requested (owner=                    VPN acquired, with its owner
releasing the dummy VPN (owner=                VPN released, with its reason
tun established (excludeSelf=                  the tun actually came up, and in which mode
Dummy VPN stopped                              THE DESCRIPTOR CLOSED - zero of these was round 1's failure
USB session established while wireless mode    (prefix) USB quiesce - UNTESTABLE on this rig
cancelling the poke retry loop                 (prefix) poke loop cancelled on the connect event
credentials pre-flight for                     (prefix) the pre-flight ran, with its verdict table
the static BSSID setting (                     (prefix) a mistyped BSSID was rejected
came up without a network name                 a nameless P2P group is now logged, not dropped

Part A — render-side-concealment
holding the picture after                      (prefix) concealment window opened
picture restored                               (prefix) window closed on a decoded keyframe
resuming on the damaged stream                 window hit the 3500ms cap
access unit the framing audit found short      the holed unit was discarded, not decoded
concealed=                                     new throughput field
keyframe decoded - the picture is repaired     repair confirmed
quiet stream earned back                       MUST BE ABSENT in every injection run
Decrypted payload too short                    MUST BE ABSENT on flags without bit 0x01
LogExporter: session |                         the build banner
unwrap produced no application data            SSL zero-unwrap, burst-budgeted
Error after feeding input buffer               input-buffer return path
Could not return the input buffer after a failed feed   the pool is still leaking

Kill lines — must not appear in any Part A run except where a run says otherwise
Decoder stall detected
Display stall
Rebuilding projection view
```

Two notes on em dashes. `cancelling the poke retry loop` and `USB session established while
wireless mode` both carry a literal `—` in source; grep the ASCII substring given above and you
will not have to think about locale. And `Disconnecting the other network` is verified **absent**
from the branch, which is the regression guard, not an error in this brief.

```
Part B — PR #845
Wi-Fi Bandwidth Upgrade successful             tunnel built
Bandwidth upgrade timed out after 10s          the 10s guard fired, with the best quality seen
Attaching the inbound STREAM that arrived before the socket existed    the race fix firing
Inbound STREAM arrived before the socket existed                       (prefix) the hold path
phone never sent its half of the stream tunnel within                  (prefix) the new 12s cap
Nearby stream tunnel incomplete                the IOException the cap throws
silent on 5289                                 (prefix) the new sweep tally
BindException                                  B2's subject
nothing is listening on port 5288              (prefix) the downstream abort
```

Verify the Part B strings against the PR branch yourself in B0; they are transcribed from the diff,
not from a compiled tree, and if B0 fails they are moot.

## 6. Runs

### A0 — build and unit-test gate. Part A's gate.

Build `release/test` and `origin/main` @ `562c8dcf`, record both md5s, confirm they differ.
`run_unit_tests.sh` on the candidate.

Expect **669** green. Named suites that must be present and green:
`LinkGapMonitorTest` (17), `UplinkStallMonitorTest` (5), `StationCoexistencePolicyTest` (11),
`ProjectionWatchdogPolicyTest` (23), `DummyVpnPolicyTest` (7), `UsbSessionQuiescePolicyTest` (8),
`NativeCredentialsPreflightPolicyTest` (17), `CorruptionConcealmentPolicyTest` (16),
`AuditRecoveryPolicyTest` (10), `KeyframeCycleEscalationPolicyTest` (40), `AapMessageFramingTest` (4).

**FAIL:** the merge does not compile, or any test is red, or the total is not 669. Any of those
stops Part A and is the round's headline result. `afd8b7ca` has never been compiled by anything, so
a failure here is a genuine possibility and is a useful result, not a wasted round.

**Copy both APKs out of `apks/` immediately** — §7a, `build_hur.sh` deletes the previous APK before
it builds.

### A1 — the point of Part A: one fault, on the merged tree

`debug-video-fault-injection=3`, rate=87, budget=1, mode 3, clean session.

This is `render-side-concealment` C1 re-run on the merge. It matters because the concealment window
and the link-gap instruments now share `AapTransport` and `ProjectionWatchdogPolicy`.

**PASS, all of:**
- One `FAULT INJECTED`, then `Previous frame was truncated!`.
- `holding the picture after frame truncated` within ~100 ms of the truncation.
- `cycling video focus (1/3)` at ~2 s, then `picture restored <N>ms ... (keyframe decoded)` with
  **N < 3500** and within a few hundred ms of round 1's **2683 ms**.
- `concealed=` > 0 in the throughput window covering the fault, `= 0` in every other window, and
  `rendered=` never 0 in any full window.
- **Zero kill lines.** This is the merge-specific condition: a `Display stall` or `Rebuilding
  projection view` here would mean the watchdog clock split lost to the idle-awareness changes.
- Zero `resuming on the damaged stream`, zero `quiet stream earned back`.

**FAIL:** any kill line; `resuming on the damaged stream`; N at or over 3500; a second `holding`
line for one fault.

If the merge changed nothing, this run looks exactly like round 1's C1. That is the pass. A
regression here shows up as a kill line or a materially different N, not as a missing feature.

### A2 — clean control, 10 minutes

All `debug-video-fault-*` keys **deleted**, `static-bssid` cleared, mode 3, default post-connect
screen, 10 minutes.

**PASS, all of:**
- All four gap series (`inbound video quiet`, `inbound audio quiet`, `inbound link quiet`,
  `uplink blocked on`) print **zero** lines, with throughput steady at the unit's usual 45-50 fps.
  **Pair the zero with the fps number** — media-gap round 2's zero was short-circuited by a healthy
  frame rate rather than evaluated, and a zero with no fps beside it proves nothing.
- `holding the picture` = 0, `access unit the framing audit found short` = 0, `concealed=0` in
  **every** throughput window.
- `quiet stream earned back` = 0 (nothing was spent, so nothing may come back).
- `Decrypted payload too short` = 0.
- `unwrap produced no application data` <= 10 total, none in the back half.
- `Configuring decoder` = 1. Zero kill lines. `createGroup SUCCESS` = 1 (§7a discard rule).
- Exactly one `LogExporter: session |` banner, with `build=` matching the APK under test.

**FAIL:** any concealment line on a clean stream; any non-zero `concealed=` window; a gap series
line at 45+ fps.

### A3 — the previously-wedging fault survives the merge

`debug-video-fault-injection=5`, rate=87, budget=1.

**PASS:** `AapRead: DELTA_CHANGED on VIDEO`, then `access unit the framing audit found short`, then
a `holding` / `picture restored` pair with N < 3500, and **`Configuring decoder` stays at 1** for
the whole session. Zero kill lines.

**FAIL:** no discard line; `Configuring decoder` > 1 attributable to the fault; any kill line.

### A4 — the VPN teardown still takes the tun down

`keep-dummy-vpn-during-session=true`, mode 3, plus
`adb shell appops set com.andrerinas.headunitrevived ACTIVATE_VPN allow`.

Session-vpn round 2 passed this at `82814ec0`; it is here because the merge touches `AapService`.
**No verdict may rest on a log line** — round 1 got every line printed correctly by code that did
nothing.

Connect, confirm `tun established (excludeSelf=true)`, then scripted `headunit://disconnect`.

**PASS, all four:**
1. `adb shell ip link show tun0` reports the device does not exist.
2. `adb shell pidof com.andrerinas.headunitrevived` is the **same number** before and after. A
   `force-stop` also removes the tun and proves nothing.
3. Exactly one `Dummy VPN stopped`.
4. No `DummyVpnService` `ServiceRecord` left in `dumpsys activity services`.

**FAIL:** `tun0` still `UP` with the pid unchanged. Attach the `dumpsys` block including `hasBound`
and `createTime`.

### A5 — the poke loop is cancelled on the connect event

Never briefed before. Mode 3. §7a recipe: **phone Bluetooth off**, launch the app so the RFCOMM
listeners and the group come up while the phone is unreachable, wait ~8 s so the poke loop is
genuinely running, then **phone Bluetooth back on**.

**PASS:** `cancelling the poke retry loop` appears **within a second or two** of
`Handshake: SSL handshake complete`. The reporter capture this was written against showed the old
behaviour at +4.1 s, so anything at or above ~4 s is a FAIL, not a pass with a slow clock.

**FAIL:** the line never appears, or lands more than ~3 s after the handshake.

**Must not appear:** `USB session established while wireless mode`. The quiesce is gated on
`!isWirelessSession` and this is a wireless session; if it fires here the gate is inverted.

### A6 — the two new session-start lines

Two short sessions, not one.

**A6a:** `enable-audio-sink=false`, mode 3, connect. **PASS:** exactly one
`Audio sink is off in Settings` at service-discovery time. **FAIL:** absent.
**Set `enable-audio-sink` back to `true` immediately afterwards.**

**A6b:** `enable-audio-sink=true`, mode 3, connect. **PASS:** exactly one
`is connected to another WiFi network` line for the group (this rig is permanently joined, §7a),
and it **must not** carry `Disconnecting the other network`. **FAIL:** the prescription is back, or
more than one coexistence line for a single group.

`not connected to any other WiFi network` is **UNTESTABLE** here. Say so; do not try to unjoin the
rig.

### A7 — a mistyped static BSSID is rejected at entry

The reachable half of `afd8b7ca`. Set `static-bssid` to `AA-BB-CC` (invalid: three groups, dashes),
launch, connect in mode 3.

**PASS:** `the static BSSID setting (` line naming the rejected value, and the fallback chain runs
as if nothing was set. **FAIL:** the value is accepted and reaches
`WifiDirectManager: Initial BSSID from App settings:`, which is the pre-fix behaviour and surfaces
30 s later as a message blaming location services.

Then set a **valid** BSSID in lower case with dashes (e.g. `02:1a:11:ff:3c:4d` written as
`02-1a-11-ff-3c-4d`) and confirm it is normalised to colon-separated upper case in
`Initial BSSID from App settings:`.

**Clear `static-bssid` afterwards.**

### — uninstall step, between the parts —

```bash
adb uninstall com.andrerinas.headunitrevived.dev   # expect "Failure" if Part B has not run yet
adb shell pm list packages | grep headunitrevived  # expect exactly one line before Part B
```

If Part A needs re-running for any reason after Part B has started, uninstall the `.dev` package
first and say in Setup notes that you did.

### B0 — does PR #845 compile at all? The point of Part B.

```bash
git fetch origin pull/845/head:pr845
git checkout pr845
git log --oneline -1     # expect 66e16e0b
```

Build `:app:assembleGithubDebug` and run `:app:testGithubDebugUnitTest`.

**PASS:** both succeed. Record the test total. **FAIL:** either fails — and that is the single most
valuable result of this round, because upstream CI has never run on this branch and nobody knows.
**A failure here ends Part B.** Capture the full compiler output verbatim, not a summary; it goes
back to the PR author.

Note the APK filename will still say `com.andrerinas.headunitrevived_<version>.apk` even though the
package inside it is `.dev` — that is one of the review findings, not a build mistake. Identify it
by md5, not by name.

### B1 — the side-by-side install, and what the launcher says

With the Part A candidate (or `main`) still installed, `adb install -r` the #845 APK.

**PASS:** `pm list packages | grep headunitrevived` shows **both** packages, and the store build
still launches and works. **FAIL:** the install is refused. If it is refused with
`INSTALL_FAILED_DUPLICATE_PERMISSION`, quote the full error; that would mean the permission
placeholder does not work as the PR claims.

Then, the locale check. The desk review says the "Open Headunit Dev" label only applies in the
default locale. Set the device to a non-English locale that the app ships (`de`, `ru`, `ja`, `es`,
`it`, `pl` all qualify), and look at the launcher.

**Two icons both reading "Open Headunit"** confirms the finding. **One reading "Open Headunit Dev"**
refutes it. Either answer is a result; screenshot it. Set the locale back afterwards.

### B2 — can two head unit builds share port 5288?

Both packages installed, **both** configured for mode 3 (`wifi-connection-mode=3`, `log-level=2`).
Remember §3a: configure the `.dev` package with `DEVPKG`, not `$PKG`.

Start the **store** build first, let it settle 15-20 s and confirm it holds the port. Then launch
the `.dev` build. Capture both.

**Record, in this order:**
1. Does the `.dev` build log `BindException` or `EADDRINUSE`?
2. Does it then log `nothing is listening on port 5288`?
3. Does the wireless-server rebuild budget start burning (`Rebuilding the wireless server on 5288`)?
4. Can the `.dev` build accept a wireless session at all while the store build runs?

**This run has no PASS/FAIL.** It is a measurement that settles a review finding either way. A clean
bind by both would refute the review; the collision confirms it. Report what happened with the
lines and timestamps.

Then reverse the order (`.dev` first, store second) and record whether the symptom follows the
second starter. That distinguishes "second one loses" from something order-independent.

### B3 — the AppLog stack trace reaches a file capture

`.dev` package. `log-source=1` (`APPLOG_FILE`), `log-capture-enabled=true`, `log-level=2`.

Provoke an error that carries a throwable. B2's `BindException` is the convenient one: if B2
produced it, this run is free — just read the capture. If not, any `AppLog.e(msg, throwable)` site
will do.

**PASS:** the exported `HUR_Log_*.txt` from
`/sdcard/Android/data/com.andrerinas.headunitrevived.dev/files/` contains the error line **followed
by a stack trace**, not a blank line. **FAIL:** the message with no trace under it.

Also confirm the negative half: an error line with **no** throwable must not be followed by a stray
blank line.

Set `log-source` back to `0` afterwards.

### B4 — the Nearby stream tunnel, happy path

The testing phone has the Wireless Helper, so this is reachable. `.dev` package,
`wifi-connection-mode=2`, `helper-connection-strategy=2` (Google Nearby), `log-level=2`. Phone on
the same Wi-Fi.

**PASS, all of:**
- `Wi-Fi Bandwidth Upgrade successful` and a tunnel that carries an actual session (SSL handshake
  completes, video starts).
- **Time from `Connected successfully!` to the tunnel being built.** Record the number. The review
  says the new 12 s stream cap sits above a 10 s handshake budget, so a healthy tunnel should land
  far inside both; a number anywhere near 10 s is the interesting case.
- Zero `phone never sent its half of the stream tunnel within` and zero
  `Nearby stream tunnel incomplete`.

**FAIL:** the tunnel never builds, or it builds and the handshake then fails with
`Nearby stream tunnel incomplete`.

**Also record, whether or not it passes:** does `Attaching the inbound STREAM that arrived before
the socket existed` ever appear? That is the race the PR was written to fix, and knowing how often
it actually fires on real hardware is worth as much as the pass.

Run it **three times** and report all three timings. One sample says nothing about a race.

### B5 — does a failed attempt poison the next one?

The review's most serious Nearby finding: `lastQuality` is never cleared on a *failed* connection
result, so a stale HIGH can make the next attempt build the tunnel without waiting for a real
bandwidth upgrade.

Setup: start a connection to the phone and **make it fail** before it reaches `STATUS_OK` — killing
the Wireless Helper on the phone mid-connect, or turning the phone's Wi-Fi off at the moment the
connection is requested, are the two cheapest attempts. Then, **without restarting the head unit
app and without leaving the discovery session**, connect to the same phone again.

**PASS (the fix would be needed and is absent):** on the second attempt,
`Wi-Fi Bandwidth Upgrade successful` appears **immediately** after `Connected successfully!`, with
no intervening `Bandwidth changed ... Quality=3` for that attempt. That confirms the finding.

**PASS (finding refuted):** the second attempt waits for a fresh bandwidth upgrade as normal.

**INCONCLUSIVE, pre-registered:** you cannot produce a connection result that is neither `STATUS_OK`
nor a full disconnect. Say so and move on; the finding stands on code reading either way and this
run is a bonus.

### B6 — the subnet sweep tally

`.dev` package, `wifi-connection-mode=2`, `helper-connection-strategy=0` (common Wi-Fi / NSD), so
the /24 sweep runs. Phone **not** running the helper, so the sweep finds nothing on 5289.

**PASS:** exactly one summary line per sweep matching `silent on 5289`, and **no** per-address probe
lines. **FAIL:** the 254 per-address lines are still there, or no summary line appears at all.

**Also record:** do the three numbers in that line add up? The review says they do not partition
(a host answering on 5277 is counted both as responded and as silent). If you have anything on the
subnet listening on 5277, note what the line said.

## 7. Do not re-run

Settled in their own threads and not repeated here: the escalation timing and baseline GOP
comparison (`wire-corruption-escalation` round 1 R1/R2), the storm shape
(`render-side-concealment` round 1 C5), the negative control on the undetectable hole (C3), the
VPN re-arm after a Bluetooth cycle (`session-vpn-lever` round 2 R3), and the 5288 repair chain
(`native-aa-5288` round 1 R2/R3).

A1 re-measures the repair interval only because the merge puts the concealment window and the link
instruments on the same two files.

## 8. Report back

1. **A0's verdict and test total.** Whether `afd8b7ca` compiles is new information regardless of
   what follows it.
2. **A1's `picture restored <N>ms`** and its distance from round 1's 2683 ms, plus the kill-line
   count (must be 0).
3. **A2's four gap-series counts, each paired with the session's fps**, and the `concealed=` values
   of every non-zero throughput window.
4. **A5's interval** from `Handshake: SSL handshake complete` to `cancelling the poke retry loop`,
   as a number.
5. **B0's verdict**, with the full compiler output if it failed.
6. **B2's answer to "can two builds share 5288"**, in one sentence, with the lines that support it,
   and whether the symptom followed the second starter when you reversed the order.
7. **B4's three tunnel-build timings**, and whether `Attaching the inbound STREAM` ever fired.
8. Anything Part B did to the device that Part A would not have. That section has produced more
   real findings than some rounds' runs.
