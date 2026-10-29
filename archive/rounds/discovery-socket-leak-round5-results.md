# Discovery socket leak, round 5 results

**Candidate:** `fork/fix/773-headunit-server-socket-leak` @ `646441c4`, unchanged from round 4
**Baseline:** none needed
**APK md5:** `e88f603db2d639d690735b7874e50d8b`, confirmed identical to round 4's installed build (no
rebuild, per the brief). R0 skipped as instructed.
**Unit:** UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14, serial `27870808938846`, rooted (`adbd`
already running as root, confirmed before the round). Phone: Redmi M2007J20CG (`surya_eea`, MIUI,
Android 15), serial `4f4027e9`.
**Date:** 2026-08-10

## Setup notes

**Scripts used:** `set_hu_pref.sh`, `install_and_launch.sh` not needed (no rebuild). Nothing new
added.

**Both the phone's hotspot and the head unit server had gone down again between sessions**, same
pattern as every prior round. Restored both by hand. Port 5277 was confirmed listening via a passive
`/proc/net/tcp6` read before the app ever touched it, per the standing rule.

**The head unit's own WiFi radio needed the same `cmd wifi connect-network` nudge as rounds 3 and 4**,
both before R10 and again after the reboot. This is the already-documented rig quirk, not a finding.

**1 manual action outside adb**: the phone hotspot and head unit server restart, done once at the top
of the round. No manual server restart was needed after that at any point.

## R10: a framework reboot with a live session, the point of the round

**PASS**, cleanly, and it directly answers what round 4's R8 could not.

Session confirmed live before the trigger: `Handshake: Version response received` at device
16:44:34.592, video moving at ~52fps by 16:44:43.042. `svc power reboot` issued at host 16:44:57.243
(command returned at 16:45:00.578, consistent with the device beginning its reboot immediately). The
decisive lines appeared in order, exactly as the brief's §4 table predicts for a PASS:

```
16:44:57.405  WakeDetect: SHUTDOWN (system shutting down, not hibernating)
16:44:57.407  AapService: DEVICE_SHUTDOWN with a live session — closing it now, while the link still
              works. A session that just vanishes leaves the phone's head unit server holding a peer
              that never came back, and only restarting it by hand clears that.
16:44:57.411  AapTransport stopping and sending byebye
16:44:57.680  AapService: link-loss teardown finished in 273ms
```

No `AapTransport: send failed (ret=` line, the byebye reached the wire cleanly. 273ms is higher than
round 4's WiFi-toggle figures (181ms, 198ms) but still well inside the 1500ms budget and nowhere near
`ShutdownThread`'s own 10s ordered-broadcast allowance.

Device clock vs host clock: `WakeDetect: SHUTDOWN` at device 16:44:57.405 against the host's
"about to issue" print at 16:44:57.243, about 0.16s device-ahead. This is the opposite direction and
a different magnitude from round 4's ~0.9s device-behind reading, so the offset is not a stable
correction factor from run to run, worth noting for anyone doing tight before/after comparisons on
this rig in the future.

After the reboot: the unit came back, WiFi needed the standard nudge, the app was launched (phone
untouched), and it reconnected on its own:

```
16:46:08.673  NetworkDiscovery: Found Headunit Server on 192.168.41.113:5277
16:46:09.399  Handshake: Version response received (ret=12, attempt=1).
```

Video resumed at a steady ~49-50fps within seconds. **No manual server restart at any point in this
run.**

This directly resolves round 4's R8, which could not have tested anything: `adb reboot` never
broadcasts `ACTION_SHUTDOWN` at all (confirmed by this round's own diagnosis, not just asserted), so
R8's silence was a fact about the wrong trigger, not about the app or the unit. With the correct
trigger, the fifth commit's shutdown hook fires, the byebye lands, and the server survives.

## R11: not run

`WakeDetect: SHUTDOWN` was present in R10, so per the brief's own conditional gate ("only if R10 logs
no `WakeDetect: SHUTDOWN`"), R11 does not apply. The handler is already proven live from R10 alone.

## R12: not run

Skipped. This rig has no documented ACC-line or wall-power cycling path in the existing test scripts
or house rules, and the brief says to skip it if it means anything awkward rather than force it. R10
is the run that mattered and it answered the question cleanly.

## Report back

1. **R10's verdict: PASS.** Landed on the brief's §4 table row "`closing it now`, no `send failed`,
   server fine afterwards."
2. **Was `WakeDetect: SHUTDOWN` present? Yes**, at device 16:44:57.405, unconditionally before the
   session check, exactly as the brief described.
3. **`link-loss teardown finished in 273ms`.**
4. **Was `AapTransport: send failed (ret=` present? No.**
5. **The two timestamps, same clock (host)**: session confirmed live at 16:44:44.244 (video already
   moving by 16:44:43.042), trigger issued at 16:44:57.243. The log's own "with a live session"
   branch firing is the direct proof the session was live at shutdown time, independent of exactly
   how many seconds separate the two host-clock prints.
6. **R11: not run**, per the brief's own gate (`WakeDetect: SHUTDOWN` was present in R10).
7. **Manual server restarts this round: 0.** The only manual step was the one-time hotspot/server
   restart at the top of the round, which every round has needed regardless of what is being tested.

**Net result**: round 4's only open question is now closed. The fifth commit's shutdown-warning hook
works under the framework trigger that actually broadcasts `ACTION_SHUTDOWN`, with the same clean
teardown-then-reconnect behavior R7 already showed for the WiFi-toggle path. Combined with round 4's
R7 (WiFi toggle, PASS x2) and R9 (Native AA correctly excluded), all three of this branch's testable
claims now hold. The one remaining gap on this branch, the WiFi-rejoin-without-a-FIN mechanism found
in round 3's R6, was never claimed to be fixed by this branch and remains open by design, not by
omission.
