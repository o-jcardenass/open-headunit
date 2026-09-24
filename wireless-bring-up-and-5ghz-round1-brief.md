# wireless-bring-up-and-5ghz: round 1 brief

First round on this branch. It is a regression round more than a proving round: two of the three
changes alter behaviour that every Native AA connection already depends on, so the question is
whether an ordinary connection still works, not only whether the new work fires.

## Build and baseline

```bash
git fetch fork
git checkout fix/wireless-bring-up-and-5ghz-channel   # 65014ed8bf265ec53226e3767bdf7618e8df29b5
```

**History was rewritten since any earlier checkout of this work.** The branch was renamed from
`fix/809-five-ghz-channel-walk`, rebased onto the new `main` (`c4dd2ba1`, which now carries driver
selection), and compacted from eight commits to three. The old name is deleted on the fork and no
earlier SHA on it resolves. Delete any local copy and check out fresh.

Base is `main` at `c4dd2ba1`. 1351 JVM tests pass on the branch.

## What this is

Three changes, all on the WiFi Direct bring-up path that every Native AA session uses:

- **The pinned 5 GHz channel is now walked across its whole window** instead of asked for once. When
  every rung is refused, the unit's regulatory domain is logged and a banner tells the user the band
  is the lever left.
- **Leaving this unit's own WiFi network is unconditional**, where it used to be a toggle that
  defaulted off. Still bounded to the bring-up and reversed on teardown.
- **A USB projection attempt in flight holds the wireless bring-up** for up to 8 s, so a plugged-in
  dongle no longer gets a group, a 5288 server and a poke raised around it and torn straight back
  down.

The regression exposure is the middle one: units that never opted into the stand-down now get it on
every bring-up.

## What is different about this round

- **The rig is permanently joined to `Pegue Cdesta` at 5500 MHz (channel 100).** That is what makes
  R2 meaningful: the stand-down has a real association to drop. Verify it before starting, per §7a,
  and put the reading in Setup notes.
- **The MT50 cannot host USB, so R5 runs on the POCO as head unit** over wireless adb with its port
  free, per §7b. R1 to R4 run on the MT50 as usual.
- **`WifiScanner` is broken on the MT50**, so the group's channel is read from our own log line, never
  from a scan.
- **R4 may come back INCONCLUSIVE and that is not a failure.** It needs the unit to refuse every
  5 GHz rung. If the MT50 honours a pinned channel, the refusal path cannot be reached live and R4
  falls back to its seeded form, described in the run.

## Settings keys this round needs

| Key | Element | Note |
|---|---|---|
| `wifi-connection-mode` | `<int name="wifi-connection-mode" value="3" />` | Native AA, every run |
| `wifi-5ghz-channel` | `<int name="wifi-5ghz-channel" value="0" />` | 0 automatic; pinnable to 36, 40, 44, 48, 149 |
| `log-level` | `<int name="log-level" value="0" />` | Verbose, every run |
| `connection-issue-5ghz-channel-refused` | `<long name="connection-issue-5ghz-channel-refused" value="1755800000000" />` | R4 seeded form only |
| `connection-issue-dismissed-at` | delete it | **Always delete alongside the seed above**, §7a |

`stand-down-station-for-wifi-direct` **no longer exists**. If it is still in `settings.xml` from an
earlier thread it is now inert; leave it or clear it, and say which in Setup notes.

## The lines that decide every run

Verified with `grep -F` against `65014ed8`; each appears exactly once in `app/src/main/java`.

```
StationStandDown: this unit has left its WiFi network.
StationStandDown: this unit is not joined to another WiFi network, so
StationStandDown: this unit's WiFi network is enabled again and should rejoin
WifiDirectManager: == WiFi regulatory domain source dump ==
WifiDirectManager: this unit refused a group owner on every 5 GHz channel it
AapService: a USB projection attempt is in flight — holding the wireless bring-up
AapService: USB handoff settled after
```

Also used, and already familiar from earlier rounds:
`WifiDirectManager: Standard createGroup SUCCESS!`, `WirelessServer: Incoming connection detected`,
`Handshake: SSL handshake complete`.

## Runs

**R1 — an ordinary Native AA session still forms. This is the point of the round.**
`wifi-5ghz-channel=0`, defaults otherwise. Connect the phone the usual way.
PASS: `createGroup SUCCESS`, `WirelessServer: Incoming connection detected`, `SSL handshake complete`,
and a picture. Report time from service start to `SSL handshake complete`.
FAIL: no picture, or that time is more than ~1.5x the last driver-selection round's figure.
Also record: `AapService: a USB projection attempt is in flight` must **not** appear with no USB
attached. Its presence here is a FAIL on its own, whatever else passes.

**R2 — the stand-down runs with no setting, and gives the network back.**
Same as R1. The rig is joined to `Pegue Cdesta` throughout.
PASS: `StationStandDown: this unit has left its WiFi network.` appears with no setting written, and
after the session ends `StationStandDown: this unit's WiFi network is enabled again and should rejoin`
appears and `dumpsys wifi` shows the rig back on `Pegue Cdesta`.
FAIL: the rig does not rejoin, or R1's session does not form at all with the stand-down active.
If instead `this unit is not joined to another WiFi network, so` appears, the rig's association was
lost before the run: re-verify and re-run rather than reading it as a pass.

**R3 — a pinned channel is actually walked.**
`wifi-5ghz-channel=36`. Connect as in R1.
PASS: `== WiFi regulatory domain source dump ==` appears before the first channel request, and a
`WifiDirectManager: operating channel ...` line names 36 (5180 MHz) or a later rung in the ladder.
A session still forms.
FAIL: no operating-channel line, or the group forms with no channel request logged at all.

**R4 — positive control: every 5 GHz rung refused raises the banner.**
`wifi-5ghz-channel=149`, then 36 if 149 is honoured. Connect as in R1.
PASS (live form): `this unit refused a group owner on every 5 GHz channel it was offered` appears,
and after a force-stop and relaunch the main screen shows the 5 GHz banner.
If the unit honours the pinned channel, that path is unreachable live. Say so, then run the **seeded
form**: with the app stopped, write the `connection-issue-5ghz-channel-refused` stamp, delete
`connection-issue-dismissed-at`, relaunch, and confirm the banner appears and its tap target opens
the band setting. Report which form was used.

**R5 — the USB deferral holds the bring-up, on the POCO as head unit.**
POCO as head unit per §7b, port free, wireless adb. Start the app with a phone already plugged in
over USB.
PASS: `AapService: a USB projection attempt is in flight — holding the wireless bring-up` appears
**before** any `createGroup`, and then either `AapService: USB handoff settled after <N>ms — arming
wireless now` with N under 8000, or the USB session takes over. Wireless must end up armed either way.
FAIL: wireless never arms, or the hold outlasts 8 s without either outcome.
Note a phone acting as head unit carries no video; judge this run on the log lines only.

## Do not re-run

- Anything from the driver-selection rounds. Nothing in this branch touches the selector, the wake
  picker or the exclusive gate.
- The WPP handshake message sequence itself. This branch changes what channel the group lands on and
  when the bring-up starts, not the exchange.

## Report back

- R1's service-start to `SSL handshake complete` time, against the last round's figure.
- Whether the rig rejoined `Pegue Cdesta` after every run that stood it down.
- Which form R4 ran in, live or seeded.
- Any run where a session failed to form at all, with the capture.
