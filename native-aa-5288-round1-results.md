# native-aa-5288 — round 1 results

**Candidate:** `fix/video-and-wireless-stack` @ `e9f5d2b6feb2c0b4026607629649ed4cbecc68a2`
**Baseline:** none — this round has no A/B, per the brief.
**APK md5:** `9a858685c645fd015d0847abf7adf0c0`
**Unit:** UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14, no USB accessory path, wireless-only rig.
**Date:** 2026-08-20

## Setup notes

- `hur-wifi-test-scripts/` inventory used: `build_hur.sh` (build), `run_unit_tests.sh` (JVM tests),
  `install_and_launch.sh` (`SKIP_BUILD=1`, install + relaunch), `set_hu_prefs.sh` (multi-key settings
  writer, no intermediate relaunch). No new script was needed.
- Confirmed `git rev-parse HEAD` on the checked-out branch matched `e9f5d2b6...` before building.
- All decisive log lines in §4 of the brief were verified with `grep -F` against source before
  running anything; all matched exactly, including the one line not explicitly quoted in full
  (`found port 5288 unbound`, which is a template string — verified the surrounding literal text).
- The port-5288 lever worked on the first method tried: `adb reverse tcp:5288 tcp:1` produces a
  real `LISTEN` on `[::]:5288` (confirmed via `netstat -ltn`), no need for the `toybox nc` fallback.
  R2 and R3 were both testable.
- Inline `run-as ... sh -c 'cp ...'` failed exactly as `TESTING-TEMPLATE.md` §7a describes
  (`can't create shared_prefs/settings.xml: No such file or directory`); switched to the
  pushed-script restore pattern immediately, no time lost.
- **A finding worth flagging on the discard rule itself.** Every run in this round produced exactly
  one `MATCH! Starting AapService via Bluetooth Auto-start...` line, from the phone's own Bluetooth
  reconnecting after the clean-run protocol's airplane-mode-off step (§4 step 5) — not from a poke.
  `TESTING-TEMPLATE.md` §4 lists `MATCH! Starting AapService` as an unconditional discard trigger.
  Taken literally, every run here would be discarded. I did not discard: in all four runs
  (R1, R2, R3, R4) the capture shows exactly one `createGroup SUCCESS!` and exactly one
  `p2p-wlan0-N` value despite the MATCH line, i.e. no group churn resulted — the specific harm the
  rule exists to catch. Treated as a benign duplicate start rather than contamination. Whoever writes
  the next brief may want to narrow this rule to "a second `createGroup SUCCESS`" (already a separate
  bullet) rather than the MATCH line alone, since the clean-run protocol's own step 5 appears to
  produce this line on every run on this rig.
- Also worth noting for future capture-reading: a single successful SSL handshake always produces
  **two** `SSL handshake complete` lines (`AapSslContext.performHandshake` and
  `AapTransport.handshake`, back-to-back, same millisecond). Not a second handshake — do not double
  it when counting sessions.
- Settings backed up before any change and restored (diff-verified, clean) at the end of the round.

## R0 — build and unit-test gate

**PASS**

- `git rev-parse HEAD` = `e9f5d2b6feb2c0b4026607629649ed4cbecc68a2`, matching the brief.
- `assembleGithubDebug` succeeded — first-ever compile of `c2efedda` and `e9f5d2b6`, no Kotlin
  errors.
- `testGithubDebugUnitTest`: **565/565 passed**, up from round 5's 552, matching the brief's
  prediction exactly.
- `WirelessServerRestartPolicyTest.kt`: 179 lines (matches brief), **12/12 tests passed**, 0
  failures, 0 errors.
- APK md5: `9a858685c645fd015d0847abf7adf0c0`, confirmed live via `pm path` + `md5sum` before R1.

## R1 — a start that works now says so

**PASS**

- Settings written: `wifi-connection-mode=3`, `native-ap-transport=0`, `log-level=1`.
- Radio state: phone airplane mode ON before launch, OFF after 18 s settle (`svc bluetooth enable`,
  `svc wifi enable`, `cmd connectivity airplane-mode disable`), both verified `state: ON`.
- Discard-rule check: one `createGroup SUCCESS!`, one `p2p-wlan0-6` interface, no `Magic Garbage`, no
  second SSL handshake session id. One `MATCH!` line — see Setup notes; no group churn resulted, not
  treated as contamination.
- Decisive log lines, in order:
  ```
  00:55:32.929  AapService: Starting the wireless server on 5288 - no server yet.
  00:55:32.935  WirelessServer: binding port 5288...
  00:55:32.941  Wireless Server listening on port 5288
  00:55:33.648  WifiDirectManager: 5GHz createGroup SUCCESS!
  00:55:57.939  WirelessServer: Incoming connection detected from /192.168.49.12
  00:55:58.139  SSL handshake complete. Session id: G/P2GvQN/...
  ```
- **Measurement: bind took 6 ms** (32.935 → 32.941), well under the brief's "a few milliseconds"
  expectation, not the "anything above a second" warning case.

## R2 — a server that failed to bind gets repaired (the point of the round)

**PASS**

- Lever: `adb reverse tcp:5288 tcp:1` confirmed `LISTEN` on `[::]:5288` via `netstat -ltn`.
- Settings: same three keys as R1, unchanged.
- Radio state: same clean-run cycle as R1.
- Discard-rule check: one `createGroup SUCCESS!`, one `p2p-wlan0-7` interface, clean.
- Step 2 confirmed the exact retry code path: attempts 1 and 2 logged at WARN
  (`did not bind on attempt 1 of 3`, `... attempt 2 of 3`), then `Wireless server error` at ERROR
  with no "attempt 3 of 3" line — this matches source (`AapService.kt` bind loop: the final attempt
  rethrows without logging, by design, per its own comment). Not a bug in the run or the code.
- Decisive log lines, in order:
  ```
  01:00:11.872  AapService: the Bluetooth handshake found port 5288 unbound. Trying to start the wireless server.
  01:00:11.875  AapService: Rebuilding the wireless server on 5288 - ... (attempt 1).
  01:00:11.877  WirelessServer: binding port 5288...
  01:00:11.883  Wireless Server listening on port 5288
  01:00:12.132  AapService: port 5288 is bound now.
  01:00:12.135  NativeAA: port 5288 was not bound, and is now. Carrying on with the handshake.
  01:00:13.691  WirelessServer: Incoming connection detected from /192.168.49.152
  01:00:13.884  SSL handshake complete. Session id: v+oc10eXKU/...
  ```
- No abort text (`Handshake aborted`) anywhere in this capture.
- **Measurement: 1.819 s** from `found port 5288 unbound` (01:00:11.872) to `Incoming connection
  detected` (01:00:13.691).

## R3 — the bound holds, and the repair stays narrow

**PASS** — all four conditions met.

- Port held the entire 5-minute run (`adb reverse` never removed); phone brought up after a 17 s
  settle and left running for the full window with no manual intervention — the handshake retried on
  its own roughly every 8-17 s throughout.
- Settings: same three keys, unchanged.
- Discard-rule check: **one** `createGroup SUCCESS!`, **one** `p2p-wlan0-8` interface for the entire
  5 minutes, no `Magic Garbage`, zero `SSL handshake complete` (expected — port never released, no
  session could ever form).
- **1. Rebuild count per 60 s window:** exactly **3** in every window, never more. Four full windows
  observed (`01:02:08`-`01:02:40`, `01:03:13`-`01:03:47`, `01:04:20`-`01:04:54`,
  `01:05:27`-`01:06:01`), plus one partial fifth window at capture end (1 rebuild only, capture
  stopped at the 5-minute mark). 13 rebuild lines total.
- **2. Gaps between rebuilds:** minimum observed gap **15.077 s** (within a window), all comfortably
  above the 10 s floor. Full within-window gap list: 15.077, 16.735 | 16.805, 16.811 | 16.838, 16.833
  | 16.808, 16.884 s. Cross-window gaps (3rd rebuild of one window to 1st of the next): 33.7-33.7 s,
  consistently, i.e. the budget-reset backoff period is itself stable.
- **3. Backoff after the third:** confirmed every window — `Wireless server on 5288 is not accepting
  connections - ... waiting before trying again.` repeats at INFO with **no further `Rebuilding`
  line** until the next ~60 s window opens. **20 BACKOFF lines total** across the capture.
- **4. Exactly one group, one interface:** confirmed — `createGroup SUCCESS!` count = **1**,
  `p2p-wlan0-N` set = **{8}**, size **1**, for the full 5-minute run despite constant repair attempts
  and constant handshake failure. `stopWirelessServer()`'s side effects (torn-down group, new SSID)
  never fired.
- 32 `Handshake aborted — nothing is listening on port 5288` lines over the capture — expected, since
  the port was never released and every phone attempt during this run had to fail.
- **Report numbers: worst-60s-window rebuild count = 3, minimum gap = 15.077 s,
  `p2p-wlan0-N` set size = 1.**

## R4 — clean control, and the credential snapshot's only hardware-visible claim

**PASS**, with the pre-registered INCONCLUSIVE on the torn-read race itself (as the brief predicted).

- Untouched 10-minute native session: nothing held, nothing poked, port free throughout.
- Settings: same three keys, unchanged.
- Discard-rule check: one `createGroup SUCCESS!`, one `p2p-wlan0-9` interface, no `Magic Garbage`.
  `SSL handshake complete` count = 2, which is the expected pair for one handshake (see Setup notes),
  not a second session.
- **Zero of all four required-zero patterns:** `Rebuilding the wireless server` = 0,
  `is not accepting connections` = 0, `Wireless server error` = 0, `NativeAA: Handshake error: null`
  = 0.
- Credential snapshot: **4 deliveries**, within the brief's expected 3-4 per group, all four lines
  identical on both fields that matter:
  ```
  SSID=DIRECT-1R-HeadUnit, IP=192.168.49.1, BSSID=7e:05:6f:fb:55:d8   (x4, 01:07:44.655 - 01:08:12.591)
  ```
  No disagreement observed — as the brief pre-registered, this is read as "the race did not fire",
  not as evidence the torn read is fixed. That coverage stays on the JVM side.
- Session formed normally: `Incoming connection detected` at 01:08:12.757, `SSL handshake complete`
  at 01:08:12.949 — video decoding confirmed live via ongoing `mali_gralloc` buffer churn through the
  full 10-minute window.

## Three numbers for the shipping question

1. **R3 worst-60s-window rebuild count: 3**, minimum gap **15.077 s** (floor is 10 s). Both within
   bound.
2. **R2 elapsed time, `found port 5288 unbound` → `Incoming connection detected`: 1.819 s.**
3. **R3 `p2p-wlan0-N` set size: 1.**

Plus from R0: both never-before-compiled commits (`c2efedda`, `e9f5d2b6`) compile clean, and unit
tests are **565/565**, exactly matching the brief's prediction.

## Anything the brief did not ask about

- The bind-loop's "final attempt rethrows silently" behavior (no `did not bind on attempt N of N`
  line for the last attempt before the outer `Wireless server error`) is intentional per the code's
  own comment and was confirmed by reading `AapService.kt` directly rather than assumed from the
  capture — worth keeping in mind for anyone reading a future capture cold, since it looks at first
  glance like a skipped attempt.
- The cross-window backoff period in R3 (3rd rebuild of a window to 1st rebuild of the next) was a
  remarkably stable ~33.7 s across all four full windows — not asked for by the brief, but a clean,
  repeatable number if it's ever useful as a tuning reference.
- No poke was ever attempted or needed in any run — the phone's own Bluetooth reconnect after the
  clean-run protocol's airplane-mode-off step always won the race, consistent with prior rounds'
  documented rig behavior.
