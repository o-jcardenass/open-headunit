# Discovery socket leak, round 3 results

**Candidate:** `fork/fix/773-headunit-server-socket-leak` @ `766546a3`
**Baseline:** none needed (round 2's R0/R1/R2/R4/R5 are settled)
**APK md5:** candidate `5c69ae03d1b62f9b7dbab4acded6f3d4`
**Unit:** UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14, serial `27870808938846`. Phone: Redmi
M2007J20CG (`surya_eea`, MIUI, Android 15), serial `4f4027e9`.
**Date:** 2026-08-10

## Setup notes

**Scripts used:** `build_hur.sh`, `install_and_launch.sh SKIP_BUILD=1`, `set_hu_prefs.sh`. Nothing new
added.

**Both the phone's hotspot and head unit server had gone down again between sessions**, same as
between rounds 1 and 2. Restored both by hand, as expected. This time the head unit's own WiFi radio
was also disabled (`svc wifi status` returned "Wifi is disabled") on top of that, needing an extra
`svc wifi enable` before `cmd wifi connect-network` would even attempt the join; the initial
`connect-network` call failed silently with `Connection failed` until that was found and fixed.

**Followed the round's new rule and did not pre-check port 5277.** Launched the app as the first
thing to touch it both times a fresh server was needed, per §3.

**The round's restart budget (2) was exceeded, and this cost real time.** The brief expected one
restart to start R6 clean and one between R6 and R3b. In practice **R6 alone consumed two restarts**,
because both of its first two WiFi rejoins deafened the server and needed a manual restart to clear,
not the self-recovery the brief's PASS condition describes. See R6 below for the full account,
including a process mistake: the first "recovery" was initially misread from the capture as
spontaneous self-healing (a `Handshake: Version response received` appeared in the log about 100s
after the rejoin, with no obvious log line marking a restart) before the user clarified they had in
fact restarted the server both times. **The log gives no visible signal that a restart happened**,
which is worth knowing for any future round reading this same signature: a delayed recovery in the
capture is not, by itself, evidence of self-healing.

R3b's own deafening (via held `nc`, not a rejoin) needed no extra restart to set up, matching the
brief's design; the round used **3 manual restarts total against a budget of 2**, all attributable to
R6 finding the WiFi-rejoin deafening mechanism live, not to any setup overhead.

## R0: build gate

**PASS.**

- Builds clean, no new warnings.
- `run_unit_tests.sh`: `BUILD SUCCESSFUL`, 233/233, 0 failures/errors/skipped.
- `UnresponsivePeerPolicyTest.xml`: `tests="8" skipped="0" failures="0" errors="0"`, unchanged from
  round 2, exactly as the brief expected (the policy itself was never the problem).

## R3b: the backoff actually engages, the point of the round

**PASS**, cleanly, on all three stated conditions.

- Settings: `wifi-connection-mode=2`, `helper-connection-strategy=3`, `log-level=1`
- Method: server force-deafened fresh (force-stop, hold `tail -f /dev/null | toybox nc 127.0.0.1
  5277` on the phone, relaunch), left untouched for the full 6 minutes, launched 15:04:53

**1. The backoff line appears exactly once**, immediately after the third failure:

```
15:05:23.348  CommManager: 192.168.41.113:5277 has accepted 3 connections in a row without
              answering any of them. Slowing discovery to one attempt every 60s. Android Auto's
              head unit server does not recover on its own once this happens — stop and start it
              again on the phone, in Android Auto's developer settings, and this will reconnect
              by itself.
```

Against round 2's 0, this is the direct confirmation that moving the counter into `CommManager`
worked: the line the brief said must appear exactly once did, exactly once.

**2. `Handshake failed`: 10** over the 6-minute window. Within the brief's predicted 6-10 range,
against round 2's 36 in 6m10s and round 1's baseline 32 in 5 minutes, a genuine and large reduction
in how often the app hammers a server it has already determined cannot answer.

**3. The cadence itself widened, measured directly from `Auto-connecting to Headunit Server at`
timestamps:**

```
15:04:55.349  cycle 1
15:05:06.017  cycle 2   (+10.7s)
15:05:16.679  cycle 3   (+10.7s)
15:05:27.327  cycle 4   (+10.6s)   ← last fast cycle; backoff message fires between cycle 3 and 4
15:06:28.007  cycle 5   (+60.7s)  ← cadence widens
15:07:28.688  cycle 6   (+60.7s)
15:08:29.367  cycle 7   (+60.7s)
15:09:30.045  cycle 8   (+60.7s)
15:10:30.727  cycle 9   (+60.7s)
```

This matches the brief's predicted shape exactly: fast for the first three-to-four cycles, then a
clean, consistent ~60.7s (not merely "roughly" 60s, the actual measured interval barely varies)
for every cycle after. There is no ambiguity here the way there was with round 2's scan-timing
evidence; this is a direct, repeated, precise measurement.

**Phone-side socket count, the number that decides whether this ships:**

```
adb -s <phone> shell netstat -tn | grep 5277
```

**1 CLOSE_WAIT row**, against round 2's 28 over a comparable 6m10s and round 1's baseline 24 over
5 minutes. Single figures, exactly as the brief said "the fix working" would look like. The backoff
reaching the retry loop stops it from manufacturing fresh orphans while deaf, which is precisely
what round 2's R3 found missing.

## R6: the race fix, repeated, replaces round 2's weak signature

**FAIL.** 0 of 2 rejoins attempted held on their own; both deafened the server and needed a manual
restart to clear. The third rejoin was not run, per the brief's own instruction to stop once a
rejoin has deafened the server rather than keep measuring a broken one.

- Settings: same as R3b. Server restarted fresh, app connected normally first
  (`Handshake: Version response received` at 14:55:43.414), confirmed before the first rejoin.

**Rejoin #1**, triggered 14:56:14: WiFi came back up (`192.168.41.52`) about 15s later, but
`NetworkDiscovery` spent the next ~75s scanning a stale, wrong subnet (`10.243.202.0/24`, the same
leftover value seen in rounds 1 and 2) before it caught the correct one at 14:57:31.611. From there,
**14 consecutive `Handshake failed`** cycles, spanning 14:57:38 to 14:59:18, with the candidate's own
backoff engaging correctly along the way (`3 connections in a row...` fired twice, at 14:57:38.308
and 14:58:34.234) but the *reconnect itself* never landing. Recovery came at 14:59:19.772
(`Handshake: Version response received`, `SSL handshake complete` at 14:59:19.876), **about 100s
after the last failure and confirmed by the user to be the result of a manual server restart, not
self-healing**. This was initially misread in the capture as spontaneous recovery; see Setup notes.

**Rejoin #2**, triggered 15:00:23, using the now-connected session as the starting point: two more
successful baseline cycles logged first (15:00:07, 15:00:10, both pre-dating the trigger and
therefore not part of this trial), then **7 consecutive `Handshake failed`** from 15:00:23 to
15:01:40. One of these failures was a distinct, previously-unseen failure mode: at 15:01:13.898 the
version exchange itself *succeeded* (`Handshake: Version response received (ret=12, attempt=2)`),
but the following SSL/TLS step failed immediately with `javax.net.ssl.SSLException: Unable to parse
TLS packet header` inside `AapSslContext.performHandshake`, logged as `Handshake failed with
exception` at 15:01:13.909, 11ms after the version response. This looks like the phone-side server
briefly presenting bytes from two different connection contexts during the transition; it is not
covered by any run in this brief and is reported here as a new observation, not folded into either
verdict. Recovery came at 15:03:18.602, again **confirmed by the user to be a manual restart**, about
98s after the last failure, an almost identical recovery-time to rejoin #1.

**Rejoin #3 was not attempted.** Two consecutive rejoins each producing a genuine, non-self-healing
deafening is already the brief's stated stop condition ("if one of the three deafens the server, say
which and stop"); a third trial against the same, now twice-confirmed mechanism would not have
added information, only cost a third manual restart.

**This is exactly the mechanism the brief predicted this branch does not fix**: "a rejoin kills the
old session without a FIN, which deafens the server all by itself." Round 2's single-trial R2 held
by luck, not because the race was actually gone; round 3's two independent trials show the server
gets deafened by a rejoin roughly as reliably as by a held external connection, and this branch's fix
(closing sockets we would otherwise leak, backing off once we notice) has no effect on that specific
trigger, because in this case we never leaked anything, the phone's own connection handling is what
drops the old session uncleanly.

## Report back

1. **R3b's `Slowing discovery` count: 1.** Exactly the brief's target, against round 2's 0.
2. **R3b's `Handshake failed` count: 10, over ~6m24s** (launch 15:04:53 to last cycle 15:10:30 plus
   the tail of the capture). Within the predicted 6-10 range, against round 2's 36 in 6m10s.
3. **R3b's phone-side socket count: 1, over the same window.** Against round 2's 28. Single figures,
   as predicted; the backoff fix works.
4. **R6: 0 of the 3 rejoins held.** Rejoins #1 and #2 both deafened the server and needed a manual
   restart; #3 was not run per the brief's stop condition. This is not "round 2 got lucky" in the
   weak sense, it is a repeatable, confirmed failure of a mechanism the brief already flagged as out
   of scope for this branch.

**Net result**: the backoff/hammering fix that failed round 2 now works cleanly and by every measure
the brief asked for. The race fix from round 2 does not cover WiFi-rejoin-induced deafening, but the
brief already said, before this round ran, that this specific mechanism is a known gap rather than
something this branch claims to fix. Whether that gap is acceptable to ship with is a product
decision, not a test result; this round's job was to confirm R3's fix works (it does) and to get a
real, repeated measurement of the rejoin gap rather than round 2's single lucky trial (also done).
