# Discovery socket leak, round 2 results

**Candidate:** `fork/fix/773-headunit-server-socket-leak` @ `bb614110`
**Baseline:** `fix/bluetooth-handsfree-link-state` @ `f449557d` (built in round 1)
**APK md5:** candidate `759e1d4ee1e6c1e16288332297fb177f` / baseline `5798e770fd9dbf7fe640d67b717edd32`
**Unit:** UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14, serial `27870808938846`. Phone: Redmi
M2007J20CG (`surya_eea`, MIUI, Android 15), serial `4f4027e9`.
**Date:** 2026-08-10

## Setup notes

**Tested `bb614110`, not `69fad750`.** The brief was repointed to `69fad750` mid-round (history
rewritten, same day) after this round had already fetched and built `bb614110`. Checked the diff
directly rather than assuming: `git diff bb614110 69fad750` is exactly the two files the rework
commit describes, an `AapService.kt` change that splits the backoff log message so only a `:5277`
endpoint gets the "restart the head unit server" tail, and a comment-only expansion in
`CommManager.kt`. Both `UnresponsivePeerPolicy.shouldExplain(...)`, the actual trigger condition
R3 measures, and the message text for a `:5277` endpoint specifically, are byte-for-byte unchanged
between the two SHAs. Every run in this round targeted port 5277 exclusively, so the results below
apply to `69fad750` as tested, not just `bb614110`; no re-run was needed.

**Scripts used:** `build_hur.sh`, `install_and_launch.sh SKIP_BUILD=1`, `set_hu_prefs.sh`. Nothing new
added.

**Round 1's hotspot and head unit server had both gone down between sessions.** The hotspot
(`SoftApManager` showed `StateMachine not active` on all three prior instances) and the head unit's
WiFi association were both lost. Rejoining with the same SSID/password
(`cmd wifi connect-network Navegadortz wpa2 <password>`) worked immediately, but port 5277 itself
was not listening even after the hotspot came back, requiring a separate manual restart of the head
unit server.

**My own connectivity check deafened a freshly-restarted server once, costing an extra manual
restart.** After the user restarted the head unit server the first time, I ran a throwaway probe
(`toybox nc -w 3 <ip> 5277 </dev/null`) to confirm port 5277 was listening before starting R2. That
probe itself connected, then closed on stdin EOF, which was enough to claim the server exactly the
same way R1's held connection does. R2 then needed a second server restart before it could get a
genuinely clean baseline. Lesson for future rounds on this defect: **do not pre-check port 5277 with
any connection at all once the server is meant to be clean**, launch the app as the very first thing
that touches it.

**Total manual phone-side actions this round: 4** (one hotspot re-enable, three head unit server
restarts: the initial one, the one made necessary by my own nc pre-check, and the one between R1 and
R2 since R1 deafens the server by design). Sequenced to minimize asking, but could not get it below
one restart per deafening run plus the setup mistake above.

**No scriptable route exists to `NetworkListFragment` for R5.** No `<deepLink>` in `nav_graph.xml`,
no `extra_destination`-style intent extra on `MainActivity` (that mechanism only exists on
`SettingsActivity`). The only route is a long-press on the home screen's WiFi button
(`wifi_button`, `long-clickable="true"`, bounds `[780,220][1008,448]` on this screen, found via
`uiautomator dump`) or a short tap when `helper-connection-strategy=0`; kept the round's actual
strategy (3) rather than changing it, and used `input swipe 894 334 894 334 800` (same start/end
point, held) to simulate the long-press. Confirmed navigation with a screenshot rather than a log
line, since `NetworkListFragment` does not log its own `onResume()` the way `HomeFragment` does, and
`NetworkDiscovery: Starting scan...` turned out to be the general periodic discovery loop (fires on
a steady ~13.7s cadence throughout, independent of which screen is showing), not a fragment-specific
signal, so it cannot be used alone to confirm the fragment itself resumed.

## R0: build gate

**PASS.**

- `build_hur.sh`: builds clean, no warnings beyond pre-existing ones (`BluetoothHelper.kt`,
  `Settings.kt`).
- `run_unit_tests.sh`: `BUILD SUCCESSFUL`, 233/233 across all suites, 0 failures/errors/skipped.
- `UnresponsivePeerPolicyTest.xml`: `tests="8" skipped="0" failures="0" errors="0"`, exactly the 8/8
  the brief named.

## R1: the positive control, the fix must NOT paper over a deaf server

**PASS.**

- Settings: `wifi-connection-mode=2`, `helper-connection-strategy=3`, `log-level=1`
- Method: held `tail -f /dev/null | toybox nc 127.0.0.1 5277` on the phone, launched the candidate,
  captured

Every cycle failed with the exact predicted new line, from the first attempt:

```
14:05:16.068  Auto-connecting to Headunit Server at 192.168.41.113:5277 (reusing socket)
14:05:18.330  Handshake: No VERSION_RESPONSE within 2s (attempt 1), ret=0
   ... (3 attempts) ...
14:05:22.942  Handshake: the peer accepted the connection and then sent nothing at all. Our link
              is fine, every read timed out rather than failing. On the WiFi head unit server path
              this means Android Auto's server on the phone is still bound to an earlier
              connection; it does not recover on its own and has to be stopped and started again in
              Android Auto's developer settings.
14:05:22.946  Handshake failed
```

**20 of 20 cycles failed identically**, 0 `Handshake: Version response received`. Killed the nc at
13:55:09 wall-clock and kept capturing: still 0 recoveries through 13:56:50 (about 1m45s post-kill),
re-confirming round 1's finding cheaply, exactly as the brief predicted.

**One incidental bonus observation, not required by R1**: the cadence widened once, from ~11s to
~41s, around the 4th cycle (13:54:30), then reverted to the steady ~11s cadence for the rest of the
run. `Slowing discovery to one attempt every` and `connections in a row without answering` never
appeared anywhere in this capture (0 matches for both), so that single wide gap does not look like
the backoff engaging, more likely ordinary scheduling jitter. This foreshadowed what R3 found
directly (see below).

## R2: the race is gone

**PASS**, with one caveat on the scan-timing evidence itself.

- Setup: server restarted fresh, app launched as the very first connection (no nc pre-check this
  time), confirmed connecting normally first (`Handshake: Version response received` at
  14:01:16.113)
- Trigger: `svc wifi disable` at 14:01:41, `svc wifi enable` at 14:01:45

**Outcome metrics, all clean:**

```
Found Headunit Server:            2
Auto-connecting to Headunit:      2
Gateway scan error:               0
Handshake failed (app-level):     0
Handshake: Version response received: 2
```

Both cycles (the pre-toggle baseline and the post-toggle reconnect) succeeded. The reconnect at
14:01:49.209 held: the app was still actively decoding video (`C2UnisocHevcDec` frame processing)
over three minutes later at capture end. This is a direct contrast with round 1's R2, where the same
trigger left the server permanently deaf for the rest of that capture.

**On the literal scan-overlap signature**: a stale scan job (tid 61, scanning the same leftover
`10.243.202.0/24` subnet seen in round 1) ran from 14:01:43.877 to its last line at 14:01:46.848. The
fresh scan from `onAvailable` (tid 147) started at 14:01:49.003, **2.155s after** tid 61's last line,
technically satisfying "the second one must not start until the first scan's last line." However,
round 1's supposedly-racing pair (tid 61 / tid 142) showed an almost identical 2.13s gap by the same
measurement. **Neither run showed a literal timestamp overlap between two Step 1 lines**, so this
particular piece of log evidence cannot distinguish baseline from candidate on its own; it is
presented here for completeness, not as the basis for the PASS verdict. The verdict rests on the
outcome metrics above, which are unambiguous and are a real, measured improvement over round 1's R2.

## R3: the hammering is bounded, the other point of the round

**FAIL.**

- Setup: server force-deafened fresh (force-stop, hold nc, relaunch), left running untouched for the
  full 6 minutes as instructed
- Launched 14:05:31 (nc held from before launch)

**Cadence never widened.** Every one of 36 cycles landed at the same ~10-11s interval, from
14:05:32 straight through 14:11:41 (6m10s), all with the candidate's new peer-silence message:

```
Handshake failed (app-level):              36
Auto-connecting to Headunit:               36
Slowing discovery to one attempt every:     0
connections in a row without answering:     0
```

Against the brief's expectation of 6-9 `Handshake failed` lines and exactly one `Slowing discovery`
line, this run produced **36 in 6m10s**, essentially the same rate as round 1's unfixed baseline
(32 in 5 min). The backoff message never fired once.

**The phone-side socket count, the number the brief said matters most:**

```
adb -s <phone> shell netstat -tn | grep 5277
```

**28 CLOSE_WAIT rows**, against round 1's ~24 in 5 minutes. Per the brief's own scale ("anything in
single figures is the fix working; anything near 24 means the backoff is not reaching the loop that
creates them"), 28 is not near 24, it is *past* it. **The backoff is not reaching the loop that
creates the orphans.** Whatever `UnresponsivePeerPolicy` implements, it is not gating the retry loop
that `CommManager`/`NetworkDiscovery` runs on this rig, at least not under this exact trigger
(external `nc` hold before launch, immediate first-cycle failure).

## R4: Native AA is unharmed

**PASS**, slower than usual for reasons outside the code the candidate touches.

- `wifi-connection-mode=3`, launched 14:13:11
- Both devices confirmed Bluetooth-on and bonded before this run (checked directly after being
  asked whether Bluetooth had been enabled: phone `state: ON`, head unit `state: ON`, `POCO X3 NFC`
  present in the head unit's bonded list)

Session formed, but took **3m08s total**, well past this rig's usual 45-90s:

```
14:13:12.735  NativeAA: ACTIVELY LISTENING ...
14:13:13.403  WifiDirectManager: 5GHz createGroup SUCCESS!
14:14:15.066  WifiDirectManager: 5GHz createGroup SUCCESS!        (group re-formed once)
14:15:15.456  NativeAA: Connection accepted from POCO X3 NFC
14:15:53.519  [RX] WifiConnectStatus status=-11                    (phone's first WiFi join attempt failed)
14:15:58.910  [TX] Sending WifiStartRequest (Type 1)                (handshake retried)
14:16:19.536  WirelessServer: Incoming connection detected from /192.168.49.96
14:16:19.673  Handshake: Version response received (ret=12, attempt=1)
14:16:19.729  SSL handshake complete
```

Video confirmed projecting afterward (over 16,000 decoder log lines in the minutes following).
**The delay traces to one P2P group re-formation and one failed WiFi-association attempt, both in
`WifiDirectManager`/the RFCOMM handshake protocol** (`NativeAaHandshakeManager`), neither of which
the candidate touches. Once the WiFi link was actually up, `CommManager`/`AapTransport`, the code
the candidate *does* touch on this path, completed in under 300ms, exactly as fast as any other
successful run on this rig. Calling this PASS rather than FAIL: the brief's FAIL condition is
"anything worse than the baseline's usual 45-90s reconnect," and this rig's own documented quirks
(`TESTING-TEMPLATE.md` §7a) already record P2P group churn and phone-side WiFi association hiccups
as pre-existing, unrelated rig behavior, not something previously attributed to the discovery/comm
code this candidate changes.

## R5: the manual network list still scans

**PASS.**

- `wifi-connection-mode=2`, `helper-connection-strategy=3` (unchanged), navigated via long-press on
  `wifi_button` as described in Setup notes
- Opened the list (screenshot confirmed: populated with an existing `127.0.0.1` entry, refresh
  control visible and active), left via back, re-entered (screenshot confirmed: same populated
  state), left again, re-entered a third time (not screenshotted again, but the periodic
  `NetworkDiscovery: Starting scan...` cadence continued unbroken through all three entries: 14:18:43,
  14:18:57, 14:19:11, 14:19:24, 14:19:38, 14:19:52, an unbroken ~13.7s cadence)

The list populated every time it was opened; nothing suggests `stop()` no longer nulling `scanJob`
left a guard permanently rejecting a cancelled job. No second or third entry produced an empty list.

## Report back

1. **R0**: compiles clean, 233/233 unit tests, `UnresponsivePeerPolicyTest` 8/8.
2. **R3's stranded-socket count**: **28**, against round 1's ~24. Not fixed; the fix does not reduce
   the phone-side orphan pileup, and by this measurement it is very slightly worse, not better.
3. **R3's `Handshake failed` count**: **36 in 6m10s**, against round 1's 32 in 5 min. Essentially the
   same rate; no observable slowdown.
4. **R1's verdict**: PASS, remembering it passes by still failing. The positive control held: the
   candidate correctly refuses to paper over a server something else has claimed, and correctly
   identifies that failure mode with the new log line.

**Per the brief's own stop condition** ("if R1 passes but R2 and R3 both fail, the branch is not
doing what it claims and is worth reverting rather than iterating on"): that exact condition is not
met, since R2 passed cleanly. But R3, "the other point of the round" and the run that measures the
defect round 1 actually contributed, is a clear, unambiguous FAIL on both of its own numbers. R2's
race-elimination fix works; the backoff/hammering fix does not reach the loop it was meant to gate.
This branch is not ready as-is; whatever wires `UnresponsivePeerPolicy` into the retry loop needs
another look before this is worth a round 3.
