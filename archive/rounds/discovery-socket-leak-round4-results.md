# Discovery socket leak, round 4 results

**Candidate:** `fork/fix/773-headunit-server-socket-leak` @ `646441c4`
**Baseline:** none needed (rounds 1-3 are settled)
**APK md5:** candidate `e88f603db2d639d690735b7874e50d8b`
**Unit:** UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14, serial `27870808938846`. Phone: Redmi
M2007J20CG (`surya_eea`, MIUI, Android 15), serial `4f4027e9`.
**Date:** 2026-08-10

## Setup notes

**Scripts used:** `build_hur.sh`, `run_unit_tests.sh`, `install_and_launch.sh SKIP_BUILD=1`,
`set_hu_pref.sh`. Nothing new added.

**Candidate merged clean.** `fork/fix/773-headunit-server-socket-leak` @ `646441c4` has round 3's
`766546a3` as a direct ancestor, and the fifth commit's ancestor chain already includes
`fix/bluetooth-handsfree-link-state` (`f449557d`), so the branch checked out and built standalone
with no baseline juggling needed this time.

**Followed the standing rule and did not pre-check port 5277** with `nc` or anything else; verified
it was listening via a passive read of `/proc/net/tcp6` (no connection attempt) before launching the
app for the first time each round needed a fresh server.

**A recurring rig quirk cost time in R7, R8, and R9: `svc wifi enable` after `svc wifi disable` does
not reliably bring the WiFi radio back up on its own.** Every one of the three runs needed either a
`cmd wifi connect-network "Navegadortz" wpa2 "12345678"` nudge (R7 x2, R8) or a second manual
`svc wifi enable` (R9, where the radio was still reporting `wifi_on=0` after 5.5 minutes) before the
network layer actually came back, independent of anything the app itself was doing. This matches
round 3's setup notes, which hit the same thing. **This is a rig-level limitation, not a candidate
behavior**, and every verdict below is scoped to what happened after the network was confirmed back.

**Restart budget: 1 manual server restart used, against a budget of 2-3.** The server was restarted
fresh once at the start of R7 and never needed restarting again for the rest of the round (R7's two
trials, R8, and R9 all either held or self-recovered on the app side). This is itself the headline
result.

## R0: build gate

**PASS.**

- Builds clean (`assembleGithubDebug`), no new warnings.
- `run_unit_tests.sh`: `BUILD SUCCESSFUL`, 238/238, 0 failures/errors/skipped, exactly the round's
  predicted total (233 + 5 new).
- `LinkLossTeardownPolicyTest.xml`: `tests="5" skipped="0" failures="0" errors="0"`.
- `UnresponsivePeerPolicyTest.xml`: `tests="8" skipped="0" failures="0" errors="0"`, unchanged from
  round 3.

## R7: a WiFi toggle no longer deafens the server, twice (the point of the round)

**PASS**, both trials.

Settings: `wifi-connection-mode=2`, `helper-connection-strategy=3`, `log-level=1`. Server restarted
fresh once at the top of this run; both trials share that same server instance.

**Trial 1.** Session confirmed live (`Handshake: Version response received` at 16:02:19.459), then
`svc wifi disable` at 16:02:27 (host clock; device clock runs about 0.9s behind host across this
capture):

```
16:02:26.795  AapService: WIFI_STATION_DISABLING with a live session — closing it now, while the
              link still works. A session that just vanishes leaves the phone's head unit server
              holding a peer that never came back, and only restarting it by hand clears that.
16:02:26.801  AapTransport stopping and sending byebye
16:02:26.975  AapService: link-loss teardown finished in 181ms
```

No `AapTransport: send failed (ret=` line, the byebye reached the wire cleanly. After the WiFi-radio
nudge above, the app reconnected on its own: `Handshake: Version response received` at 16:04:39.777.
**No manual server restart.**

**Trial 2**, same session, same server, immediately after trial 1. `svc wifi disable` at 16:05:18:

```
16:05:18.023  AapService: WIFI_STATION_DISABLING with a live session — closing it now...
16:05:18.036  AapTransport stopping and sending byebye
16:05:18.219  AapService: link-loss teardown finished in 198ms
```

Again no `send failed`. Reconnected on its own after the nudge: `Handshake: Version response
received` at 16:06:14.865. **No manual server restart.**

Both teardown-finished times (181ms, 198ms) are close to the expected sub-200ms figure, not the
1500ms budget ceiling, the close was not blocking on a dead socket.

**This is the direct reversal of round 3's R6**, which found 0 of 2 rejoins held unaided against the
same trigger. The fifth commit's `WIFI_STATION_DISABLING` hook is the difference: it converts the
station-toggle case from "phone's server left holding a peer that never came back" into a clean,
acknowledged close before the link goes.

## R8: a reboot no longer deafens the server

**INCONCLUSIVE on the mechanism, but the practical outcome was good.**

Used the live session already established at the end of R7 trial 2 as the "fresh, confirmed" starting
point (`Handshake: Version response received` at 16:06:14.865) rather than spending a second manual
restart, it had already survived two clean teardown/reconnect cycles.

`adb reboot` issued at 16:07:13 (host clock). None of the eight decisive lines from the brief's §4
appeared anywhere in the pre-reboot capture. What the app actually logged was a raw disconnect:

```
16:07:07.473  AapRead: Connection closed (EOF). Disconnecting.
16:07:07.474  Quitting because ret < 0 (-1)
16:07:07.475  AapTransport quitting (clean=false)
```

`clean=false`: the socket died from underneath the app rather than the app choosing to close it.
This is exactly the brief's own predicted INCONCLUSIVE case: `ACTION_SHUTDOWN` either never arrived
on this unit or arrived after the network layer was already gone, so the fifth commit's shutdown path
was never exercised.

**Despite that, the server was not left wedged.** After the unit came back, WiFi was nudged back with
`cmd wifi connect-network` (same rig quirk as above), the app was launched, and it reconnected cleanly
with no manual server restart: `Handshake: Version response received` at 16:09:13.336. Worth having as
a fact about the rig either way: whatever kills the session during this unit's `adb reboot` sequence
does not, on its own, leave the phone-side server deaf, but that outcome cannot be credited to the
fifth commit's `ACTION_SHUTDOWN` handling, because that code path never ran.

## R9: Native AA is not torn down by a station-WiFi toggle

**PASS** on the actual regression check.

Settings: `wifi-connection-mode=3`. Phone Bluetooth was found off (`bluetooth_on=0`) and enabled first
(`svc bluetooth enable`), since Native AA's poke path needs it. Session established fast (SSL
handshake at 16:11:07.374/.375), video steady at ~49-50fps.

`svc wifi disable` issued at 16:11:33 (host clock) while the session was live and projecting. The
policy line the brief specified fired correctly:

```
16:11:32.501  AapService: WIFI_STATION_DISABLING, but this session does not ride that link; leaving
              it alone
```

No teardown-specific lines (no "with a live session, closing it now", no byebye tied to that path)
followed. The wiring correctly excluded the mode-3 session from the new hook, which is the actual
thing R9 exists to check.

The session died anyway, about 2ms later, but from a different cause entirely: this chipset tears
down its own P2P interface as a side effect of the station-WiFi toggle (`WifiP2pNative: Teardown P2P
interface` in the system log), something the brief explicitly anticipated as an acceptable outcome
("if the P2P group does go down on this chipset, it goes down without the teardown lines, and the app
recovers as it always has"). The app immediately began reinitializing Native AA mode
(`AapService: Initializing WiFi Mode: 3`) and started listening on the AA UUID at 16:11:34.360.

**Recovery stalled for 5.5 minutes for an unrelated reason**: the head unit's own WiFi radio, which
`svc wifi disable` had turned off, never came back on by itself. `settings get global wifi_on`
still read `0` at 16:17:03, and the app's own internal attempt to re-enable it for P2P group creation
did not flip the radio either. This is the same rig quirk hit in R7 and R8, just landing on a code
path that has no adb-side nudge script of its own. Once WiFi was manually re-enabled
(`svc wifi enable` at 16:17:38.989), the app completed a fresh Native AA handshake within 20 seconds
(SSL handshake at 16:17:48.562) and video resumed at a steady 48-51fps.

**Verdict scoped precisely**: the regression the brief is actually checking for, whether the new
teardown hook fires for a mode-3 session, did not happen, confirmed by the exact log line the brief
asked for. The P2P group loss and the 5.5-minute stall are both attributable to this chipset's
existing behavior and to this rig's known WiFi-radio-recovery limitation, not to anything in the
fifth commit.

## Report back

1. **R7's verdict, both trials: PASS.** `AapTransport: send failed (ret=` did **not** appear in
   either trial. The ByeBye reached the wire cleanly both times, and the app reconnected on its own
   with no manual server restart in either case.
2. **R7's `link-loss teardown finished in <N>ms`: 181ms (trial 1), 198ms (trial 2).** Both close to
   the expected sub-200ms figure, not the 1500ms budget ceiling.
3. **R8: `ACTION_SHUTDOWN` never arrived on this unit** (or arrived after the network layer was
   already gone), none of the eight decisive lines appeared, and the disconnect was a raw
   `clean=false` EOF. The server was not left wedged afterward regardless, but that cannot be
   credited to the fifth commit, since its shutdown-handling code never ran.
4. **R9: the teardown correctly stayed out of the way of Native AA.** The exact line the brief
   specified (`but this session does not ride that link; leaving it alone`) fired, and no
   teardown-specific lines followed. The session's actual loss and slow recovery were both caused by
   this chipset's own P2P-teardown-on-station-toggle behavior and a separate, already-known
   WiFi-radio rig quirk, neither of which is new in this round.

**Net result**: the fifth commit's core claim holds under its two testable triggers so far. R7 (WiFi
toggle) is a clean, repeated PASS and directly reverses round 3's R6 finding: the mechanism works
exactly as designed when the app gets the warning. R8 (reboot, the reporter's actual case) could not
exercise the new code at all on this unit, so it remains untested rather than failed; whether
`ACTION_SHUTDOWN` behaves differently on the reporter's own hardware is still open. R9 confirms the
new hook is correctly scoped to station-WiFi sessions and leaves Native AA alone.
