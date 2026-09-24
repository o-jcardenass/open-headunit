# hands-free-wake — round 2 results

**Candidate:** `fix/audio-sink-and-wireless-bring-up` @ `186633489`       **Baseline:** same APK, the wake setting off (its default)
**APK md5:** `fbfe418a5be0da4f739b4dd2f39be30c` (`com.andrerinas.headunitrevived_3.4.0-beta3_debug.apk`)
**Unit:** D-HU (block 1/2, Part O), phones D-MOTO and D-POCO, plus a laptop ("BlueZ 5.82") and a
speaker ("Magnetic Speaker") for O4
**Date:** 2026-09-17

## Setup notes

- **`ACTION_CANCEL_WIRELESS` and the other automation actions are broadcasts, not activities.** The
  brief's own `adb shell am start -a com.andrerinas.openheadunit.ACTION_CANCEL_WIRELESS` fails with
  "unable to resolve Intent" — `AutomationReceiver` is a manifest `<receiver>`, not an activity.
  The working form is `am broadcast -a com.andrerinas.openheadunit.ACTION_CANCEL_WIRELESS -p
  com.andrerinas.headunitrevived` (the `-p` flag matches a prior round's own gotcha: without it the
  broadcast is silently dropped on this rig). Used for O1's cancel and O2's start-scan.
- **The in-app "Search settings" field has a soft-keyboard/coordinate quirk.** After `input text`,
  the keyboard can cover the row you mean to tap, so a same-position tap lands on a keyboard key
  instead and gets typed into the search box (`"Wireless Modeo"`, `"Android Auto Modereless
  Modereless Mode"`). A `KEYCODE_BACK` after typing dismisses the keyboard without closing the
  screen, after which `uiautomator dump` bounds are reliable. Needed for O3 and O5.
- **The wake row on Auto-start settings is gated on `wifi-connection-mode == NATIVE`, not on
  `connection-modes` containing `wifi`.** O5's second screenshot showed no wake row at all until
  `wifi-connection-mode` was set back to `3`; it had been left at `1` (Headunit Server) by O3's own
  positive control. Worth knowing for anyone re-running O5 without resetting that first.
- **D-POCO's classic-Bluetooth bond with D-HU dropped mid-round** (disappeared from D-HU's own
  `Bonded devices` list, with an unconfirmed SSP re-pair request from D-POCO's side visible in
  `shim::legacy::btm`). Fixed by tapping the stale "Saved devices" entry on D-POCO's Bluetooth
  settings screen once (re-bonded silently, no PIN dialog appeared by the time it was checked). One
  screen tap, escalated to the user first since it needed the classifier's permission for the
  `pm disable-user` step that also blocked here; see below.
- **`pm disable-user` / `pm enable` on a phone's Gearhead package is denied by this session's auto-mode
  classifier** ("Modify Shared Resources") on the first attempt. The user approved it explicitly for
  this round; every later disable/enable cycle in this round used the same lever without further
  incident. This is the mechanism the brief itself specifies for ending a session cleanly (section 5),
  so a future round on this same channel will hit the same prompt.
- **A capture's `adb logcat -c` can lose the very first line of interest if it races the action that
  produces it.** Measured directly: a relaunch's "ACTIVELY LISTENING" / "createGroup SUCCESS" lines,
  and (very likely) H2r's true first "Not poking" line, printed in the gap between issuing the
  triggering action and this session's next tool call clearing the buffer. The fix used from O1
  onward: clear the buffer and start the capture in the *same* command as the action, not two
  separate tool calls. Earlier attempts (H2r, H5, C1's first pass) sometimes read a "first" event
  later than the app's own internal clock reports — the escalation log line's self-reported interval
  (e.g. "it has not started Android Auto in 90s") is the authoritative figure, not wall-clock
  arithmetic against the first *captured* line.
- **This rig had no shared WiFi LAN between D-HU and any phone at round start.** Neither device had a
  `wlan0` IP; D-HU's only live interface was a `seth_lte0`-style one at `100.118.187.x`. The operator
  supplied two different networks later in the round to unblock O4 and O6 (see those sections): a
  laptop and a spare-phone stand-in for O4's Bluetooth peripherals, and first a home WiFi network, then
  D-MOTO's own phone hotspot, for O6's WiFi requirement. The home WiFi network turned out to have
  client/AP isolation (D-HU could not ping D-POCO at all, 100% loss); D-MOTO's hotspot does not isolate
  and gave both devices a working `10.182.90.0/24` link (D-HU `10.182.90.90`, D-POCO `10.182.90.190`,
  0% loss).
- **O4 needs bonded Bluetooth peripherals this rig does not have out of the box**: two devices "not
  ruled out as phones" (a laptop plus a second phone, per the brief) and one hands-free device that is
  then powered off. The operator supplied a laptop (already bonded as "BlueZ 5.82") and pointed out
  that D-MOTO, already bonded to D-HU from Block 1, is itself an honest second-phone stand-in; the
  round's own "Magnetic Speaker" bonded device stood in for the out-of-range vehicle. See O4 below.
- **`NetworkDiscovery`'s subnet scan always sweeps `100.118.187.*`**, an unrelated LTE-style interface's
  subnet, regardless of which network is actually active on `wlan0` — confirmed on every scan across
  both the "no WiFi at all" state and the later D-MOTO-hotspot state (`10.182.90.0/24`), so this is not
  simply "no network was up." This is a genuine, reproducible defect in the candidate that blocks the
  *scripted* (WiFi-button/discovery) route to Headunit Server mode entirely on this rig; the
  `headunit://connect?ip=<IP>` deep link was used to route around it for O6. See O6 below.
- **No manual `headunit://connect?ip=` session on D-POCO ever reached real projection.** Across four
  independent connect attempts spanning both WiFi conditions, every one completed the SSL handshake and
  reached the `STARTING_PROJECTION` status-pill step, and none of them ever reached `TransportStarted`
  (the state that would mean video/audio channels actually opened) — see O6 for the full breakdown.
  This is a serious, reproducible defect distinct from anything O6 itself grades.
- **The first H2r attempt's raw capture was not preserved** (overwritten before its value was
  understood); its key lines are transcribed verbatim with timestamps in the R-H2r section and in
  `evidence/H2r-notes.txt`, but there is no `.txt.gz` for that specific attempt. Attempts 2 and the
  H5 and C1 captures are all preserved in full.
- Scripts used: `build_hur.sh`, `run_unit_tests.sh`, `install_and_launch.sh`, `set_hu_pref.sh`,
  `set_hu_settings_host.py`, `restore_settings.sh` (all pre-existing, unmodified). No new script was
  added; the round's own capture-orchestration shell functions were one-off and not saved to the
  scripts directory (they do not generalize past this brief's specific "wait for a log line, then
  capture N more minutes" shape already covered by ad hoc `adb logcat` + `sleep`).

## R0 — Gate

**PASS**

- `./gradlew :app:testGithubDebugUnitTest` → **2102 tests, 0 failures** (matches the brief exactly).
- Installed APK identity confirmed two ways: `md5sum` on the built APK (`fbfe418a5be0da4f739b4dd2f39be30c`)
  and a dex string check for markers unique to this candidate (`ACTION_CANCEL_WIRELESS`×3, `alone for
  now`, `POKES_AFTER_ESCALATION`, `the status pill's X stopped`, `network is back after a link-loss
  teardown` — all present).

## Block 1: D-HU with D-MOTO

### H2r — Does the wake work on a phone that is not D-POCO

**UNTESTABLE**, on three separate, fully-executed attempts, each for a different reason. D-MOTO's own
Android Auto reconnects through the *ordinary* (non-escalated) path faster than the 90 s stand-down
the escalation needs to mature, so the mechanism this run exists to grade was never actually reachable
on this phone in this round.

**Attempt 1** (candidate arm; precondition established by cycling D-HU's own Bluetooth adapter per
section 5, Gearhead left disabled through the whole capture to keep the state clean):
- D-HU's own `HeadsetClientStateMachine` for D-MOTO connected `08:50:27.817`, then disconnected on its
  own `08:51:50.863` — an **83 s hold**, under the 90 s the escalation needs. (Phone-side dumpsys had
  shown `Connected` at the moment of the section-5 check; the drop happened afterward, inside the
  capture window, so it was not visible until compared against D-HU's own side.)
- Once the link read `no link`, the app correctly resumed *ordinary* (un-escalated) pokes
  (`Calling socket.connect()` at `08:52:51.522` and `08:53:21.777`) — expected behaviour given the
  guard's input, not a bug.
- `Not poking` never appeared in the 9-minute wait window; `despite the hands-free` never appeared.

**Attempt 2** (Gearhead re-enabled before cycling D-HU's Bluetooth adapter this time — the reverse
order from attempt 1): dual-sided polling (D-HU client-role *and* D-MOTO AG-role, every 15 s) showed
the link **continuously `Connected` for the full 9+ minute window** (`09:07:14`–`09:16:23`), so the
link-instability explanation from attempt 1 does not generalize. Instead, the capture's very first
line (`09:07:12`, ~28 s after the `09:06:44.020` HFP reconnect) already showed a **live, streaming AAP
session** — D-MOTO's Gearhead auto-launched Android Auto on its own, through the ordinary path, before
any poke was needed. Zero `Calling socket.connect()`, zero `wake poke starting`, zero `Not poking` in
the whole capture: the app's poke loop never got a turn.

**Attempt 3** (Gearhead re-enabled *without* any Bluetooth cycle, to see if avoiding a fresh ACL event
changes anything): watched every 10 s. Idle (no session) through `t+50s`, then a live session
(`AapProjectionActivity` foregrounded, TCP `ESTABLISHED`) at `t+60s`. So even with no deliberate
reconnect event at all, Gearhead auto-relaunched within about a minute of being re-enabled against an
already-held hands-free link.

**Net finding:** D-MOTO answers Android Auto's own Bluetooth-event auto-launch reliably within
30–90 s, whichever way the precondition is re-established. That is the mirror image of round 1's
D-POCO result (which never reopens its own channel) and is a real, useful data point — this phone was
never the case the escalation exists for. See C1 for the phone that is.

Evidence: `evidence/H2r-attempt2.txt.gz` (head-unit log, attempt 2), `evidence/H2r-attempt2-phone.txt.gz`
(D-MOTO's own logcat, attempt 2), `evidence/H2r-attempt2-dual-hfp-poll.txt` (dual-sided polling,
attempt 2), `evidence/H2r-notes.txt` (full three-attempt timeline with attempt 1's transcribed lines).

### H3r — What the wake costs now, and whether the bound holds

**UNTESTABLE.** H3r grades what happens after an escalated wake fires; none fired in any H2r attempt
(see above), so there is nothing to measure the cost of. The hands-free link was never taken by a
poke on D-MOTO in this round.

### H5 — The wake still escalates on a unit that has only been rebooted

**UNTESTABLE**, same root cause as H2r, on the specific mechanism the run exists to grade — but with a
genuine confirmation of the round's actual point (persistence across reboot) buried inside it.

- Step 1–2: landed one ordinary session with D-MOTO, ended it, read back `last-connected-native-mac` =
  `A0:46:5A:97:E4:95` (non-empty — the run's own precondition for gradability holds).
- Step 3: rebooted D-HU (`11:22:06` issued, `boot_completed` `11:22:40`). Fresh app process, relaunched
  immediately, Gearhead re-enabled on D-MOTO at the same time.
- D-HU's own `HeadsetClientService` never showed a state machine for D-MOTO at any of 45 polls over 450 s
  post-boot (A2DP/AVRCP auto-reconnected fine; HFP-client specifically did not, despite a connection
  policy of `HEADSET_CLIENT=100`/auto-connect). Despite that, a full **ordinary** Native AA handshake
  completed at `11:23:00.409` ("WiFi session landed... releasing Bluetooth connection" /
  "AA Server socket closed after successful handoff"), about 20 s after the fresh process armed — via
  the normal WiFi Direct path, not a wake poke (zero `Calling socket.connect()`, zero `wake poke
  starting`, zero `Not poking` in the whole capture).

**What this does confirm, even though it could not grade the escalation itself:** the fresh
post-reboot process reached a working session at all, with `last-connected-native-mac` correctly read
back non-empty and no crash or refusal blocking it. The specific claim H5 exists to test — the
escalated wake surviving a reboot's loss of in-process state — needs a phone that does not self-heal
this fast; see C1.

Evidence: `evidence/H5.txt.gz`, `evidence/H5-watch.txt` (10 s-interval netstat/activity poll),
`evidence/H5-notes.txt`.

## Block 2: D-POCO, the known negative

### C1 — The bound holds where the wake does not work

**Better-than-expected PASS, 2/2, and a departure from what the brief pre-registered.** Round 1's own
H2 FAIL (escalated wake fired, `socket.connect()` succeeded, Android Auto never reopened) is C1's
premise for grading only H3r's bound. On this rig, right now, that premise did not hold: **both**
attempts landed a full accept and a complete session.

**Attempt 1** (precondition: both sides confirmed `Connected`, no session, via D-HU's own Bluetooth
cycle while D-POCO's Gearhead was disabled then re-enabled):
- First captured `Not poking` line at `10:47:52.026` was **DEBUG-level**, meaning it is not the true
  first occurrence — that one predates this capture's `logcat -c` and is lost. Use the escalation's
  own self-report instead.
- Escalated wake `10:49:07.144`: *"it has not started Android Auto in **90s**"* — the app's own
  internal clock, immune to the capture-clear artifact, confirms the full 90 s stand-down matured
  (Wake 1 of 2).
- HFP-AG poke attempt failed immediately (`read failed, socket might closed or timeout`); HSP-AG poke
  succeeded `10:49:08.275`. **`Connection accepted from` `10:49:09.114`** — under 1 s after the
  successful poke. `WirelessServer: Incoming connection detected` `10:49:13.050`. **`SSL handshake
  complete` `10:49:13.257`.**
- **Escalated-wake-to-SSL-complete: ~6.1 s** (the reporter's own 66 s precedent, from round 1's brief,
  is the outer bound this sits comfortably inside).

**Attempt 2** (session re-ended, precondition re-established, second capture):
- Escalated wake `10:56:53.026`, again self-reported exactly *"90s"* (Wake 2 of 2 — the escalation
  budget carried across both attempts since the app process was never restarted between them).
- HFP-AG poke succeeded on the first try `10:56:53.841`, held 15 s; a second HFP-AG poke at
  `10:57:23.880` succeeded `10:57:24.294`. `Connection accepted from` `10:57:24.865` (<1 s later).
  `WirelessServer` incoming `10:57:28.308`. `SSL handshake complete` `10:57:28.578`.
- **Escalated-wake-to-SSL-complete: ~35.5 s.**

Both times the 90 s stand-down was exact per the app's own clock, and both times the phone answered
and formed a genuine session — H3r's bound (link back within 3 minutes of a stand-down) was never
exercised because the phone never had to stand down at all. **This is new information, not a repeat
of round 1's finding**: something about D-POCO's own state, or Gearhead build, has changed since round
1 in a way that made the escalated wake succeed reliably here. What exactly changed is out of scope
for this round to determine — flagged as worth a note for whoever tracks the branch's overall status,
not something this round's own evidence can attribute to the fix versus phone-side drift.

Evidence: `evidence/C1-attempt1.txt.gz`, `evidence/C1-attempt2.txt.gz`, `evidence/C1-dual-hfp-poll.txt`,
`evidence/C1-notes.txt`, `evidence/C1-attempt2-notes.txt`.

## Part O: the four the owner asked for, and one reporter's issue

### O1 — The bring-up can be stopped, and it stays stopped

**PASS**, with one sub-claim UNTESTABLE.

- Before cancel: stack actively armed (`ACTIVELY LISTENING`×1, `createGroup SUCCESS`×1,
  `Calling socket.connect()`×8, all pre-cancel). Screenshot (`evidence/O1-pill.png`) shows the round X
  control at the right-hand end of the pill.
- `ACTION_CANCEL_WIRELESS` broadcast at `11:08:54` → *"the status pill's X stopped the wireless
  bring-up. It stays down until the WiFi button or a wireless setting asks for it."* Pill went to
  `hidden`, group cleanly torn down.
- 3 minutes unattended: `ACTIVELY LISTENING` and `createGroup SUCCESS` stayed at 1 (no new
  occurrences); `Calling socket.connect()` stayed at 8 (all pre-cancel, 0 after).
- Bluetooth-bounce sub-step: the literal script (bounce **D-HU's own** adapter) produced no
  `AutoStartReceiver` activity at all — no bonded phone reconnected in that window, so nothing tried
  to re-arm and there was nothing for the guard to refuse. **Supplementary check** (re-enabled
  D-MOTO's Gearhead, known from H2r to reconnect reliably within ~90 s, to generate a genuine event):
  waited 125 s total, still zero re-arming, D-HU's own `HeadsetClientStateMachine` for D-MOTO never
  even engaged, netstat showed only a stale `FIN_WAIT1` tearing down from an earlier session. The
  specific `"stopped it from the status pill"` confirmation line (`WifiLauncherManager.kt:140-141`)
  never printed in either sub-step — not because the guard failed, but because `AutoStartReceiver`
  never logged a match at all to be refused. **This one sub-claim is UNTESTABLE** on this rig within
  the time tried; the broader claim (a cancelled stack does not spontaneously re-arm) is not, and held
  cleanly through both windows.

Evidence: `evidence/O1.txt.gz`, `evidence/O1-pill.png`, `evidence/O1-notes.txt`.

### O2 — Asking for wireless by hand is the way back up

**PASS.** `ACTION_START_WIRELESS_SCAN` broadcast at `11:16:44` while O1's cancel was still in effect →
*"the user asked for a wireless connection, so the stop from the status pill is lifted"* immediately
(`11:16:44.399`), then *"Initializing WiFi Mode: NATIVE"* same timestamp. Full bring-up followed
(`createGroup SUCCESS` `11:16:45.130`, credentials delivered, poke loop resumed). A session was not
required and none was graded.

Evidence: `evidence/O2-notes.txt` (excerpted in `evidence/O1.txt.gz`, same continuous capture).

### O3 — Leaving the settings screen does not restart the wireless bring-up

**PASS**, both faults it distinguishes ruled out.

- Step 1: opened Settings via deep link (`11:17:08`) → *"the settings screen is open, so the wireless
  stack stops until it closes"* immediately, clean teardown (port 5288 released, group removed).
- Step 2 (nothing-wireless-changed): the intended non-wireless settings.xml edit failed silently (a
  `sed` quoting issue under nested `run-as`), so effectively nothing was changed at all — which still
  satisfies "no wireless key moves." Closed via Back (`11:17:17`) → *"the settings screen closed with
  nothing wireless changed, so the wireless stack stays down"*. 60 s unattended: zero
  `Initializing WiFi Mode`.
- Step 3 (positive control): reopened Settings, used the in-app search to find "Wireless Mode",
  changed it from Native to Headunit Server **through the UI**, tapped Save → *"a wireless setting was
  saved while the settings screen is open; re-arming when it closes"* (`11:21:39.236`). Closed via Back
  (`11:21:48`) → *"the settings screen closed with a wireless setting saved behind it, re-arming
  wireless mode AUTO"* (`11:21:47.867`), then **exactly one** `Initializing WiFi Mode: AUTO`
  (`11:21:49.408`; `AUTO` is `WifiLauncherMode`'s internal name for Headunit Server).

Evidence: `evidence/O1.txt.gz` (same continuous capture spans O1–O3), `evidence/O3-notes.txt`.

### O4 — A chosen auto-start Bluetooth device survives

**PASS.** Unblocked mid-round once the operator supplied a laptop and pointed out that D-MOTO, already
bonded to D-HU from Block 1, is itself an honest stand-in for the brief's "second phone" (see Setup
notes) — no fabricated hardware, an actual second phone.

Preconditions: `log-level=0`, `wifi-connection-mode` absent (defaults to `NATIVE` per `Settings.kt`'s
getter, matching the reporter's own state), `connection-modes={self}`. Overlay permission denied and
read back (`SYSTEM_ALERT_WINDOW: deny`). Bonded devices used: BlueZ 5.82 (laptop, "not ruled out as a
phone" candidate #1), D-MOTO (motorola edge 30 neo, candidate #2, an actual second phone), Magnetic
Speaker (hands-free device, powered off before the in-app picker step to stand in for the out-of-range
vehicle).

Set both Auto-start on Bluetooth and Auto-disconnect on Bluetooth to Magnetic Speaker
(`2E:F0:03:99:D6:FB`) via the in-app device picker, tapped Save —
`"AutoStartFragment: saved auto-start 1 device(s), auto-disconnect 1, wake 1"` confirms the save ran
(the `wake 1` is `native-poke-bt-macs`'s pre-existing single entry from earlier in the round, not
something this run set or cleared).

Six read-backs of `auto-start-bt-macs` / `auto-disconnect-bt-macs` in `settings.xml`, app force-stopped
before each read:
1. Immediately after Save: both `{2E:F0:03:99:D6:FB}`.
2. Arriving on the home screen: both `{2E:F0:03:99:D6:FB}`.
3. A session run and ended: the UI's Self Mode button is blocked by a "Background Start Permission"
   dialog whose Cancel aborts the launch (the overlay is denied on purpose), so
   `ACTION_START_SELF_MODE` was broadcast directly instead. The resulting session did not fully connect
   on this AA build/unit (a Self Mode reliability issue unrelated to what O4 grades), but ran and was
   ended cleanly via `ACTION_STOP_SERVICE` (`"SelfMode: stopping Self Mode"`, `MainActivity`/
   `AapService` destroyed). Read: both `{2E:F0:03:99:D6:FB}`.
4. The app exited: `ACTION_STOP_SERVICE` had already fully exited the app as part of ending the
   session, collapsing checkpoints 3 and 4 into the same state. Read: both `{2E:F0:03:99:D6:FB}`.
5. A relaunch: both `{2E:F0:03:99:D6:FB}`.
6. Reopening the Automation screen: both rows still read "Magnetic Speaker"
   (`evidence/O4-reopened.png`, `evidence/O4-reopened-scrolled.png`), the "Background Start Permission"
   notice row still present rather than anything having cleared. Read: both `{2E:F0:03:99:D6:FB}`.

Build-identity greps (`"more than one phone is paired"`, `"Overlay permission not granted"`) stayed at
0 across the whole run, confirming the installed APK is this candidate throughout.

`"driver candidates:"` never appeared in this run's log, at any of the six checkpoints. Traced in the
candidate's source: every call site (`HomeFragment`'s native-mode WiFi-button handler,
`checkAutoStartOffer()`, `AapProjectionActivity.maybeOfferAutoStart()`) is gated behind
`wifi-connection-mode == NATIVE`'s *active-WiFi* path or `connection-modes` including `wifi`, neither of
which applies with `connection-modes={self}` — Self-only is the reporter's own precondition per the
brief. This means the brief's own "report the driver candidates: roll-up verbatim" instruction cannot
be satisfied in Self-only mode on this candidate build: a brief/build mismatch, not a defect in the
Save path this run otherwise exercises cleanly.

**VERDICT O4: PASS.** Both keys held their chosen address at all six reads, both rows read as set on
reopening, and the screen showed the overlay notice row rather than clearing anything. No reproduction
of the reporter's Save-path defect (auto-disconnect-bt-macs emptying) on this rig with this device
combination.

Evidence: `evidence/O4.txt.gz`, `evidence/O4-reopened.png`, `evidence/O4-reopened-scrolled.png`.

### O5 — The wake rows are gone on a Self-only unit

**PASS**, plus a build-behaviour finding worth carrying forward (see Setup notes).

- First screenshot (`connection-modes={self}`): Auto-start settings shows Start on boot, Start on
  screen on, Auto-start on Bluetooth, Auto-disconnect on Bluetooth (both "Not set") — **no** "Device to
  wake for wireless Android Auto" row, **no** poke-all-paired toggle, visible on the near-first screen
  (`evidence/O5-1-selfonly.png`, `evidence/O5-2-selfonly-scrolled.png` — the second is one small scroll
  that only reveals content already adjacent to the fold, not a hunt through a long list).
- Second screenshot (`connection-modes={self,wifi,usb}`, `wifi-connection-mode=3` after correcting for
  the O3-leftover value of `1`): wake row is back — *"Device to wake for wireless Android Auto: POCO
  X3 NFC"* — auto-start and auto-disconnect rows still present
  (`evidence/O5-3-withwifi-native.png`, `evidence/O5-4-withwifi-native-scrolled.png`).

Evidence: as listed above, plus `evidence/O5-notes.txt`.

### O6 — The head unit server is closed on ACC-off, and redialled when WiFi returns

**INCONCLUSIVE**, unblocked mid-round by the operator (see Setup notes), but still not fully
testable — not for lack of a WiFi LAN this time, but because no session this rig produced ever left
`STARTING_PROJECTION` to become a genuinely settled one. That stall is a serious, reproducible finding
in its own right, reported in full below and in "Anything the brief did not ask about," but it is not
what O6 itself grades, so O6's own two sub-claims are graded separately.

**Getting the precondition in place:** `wifi-connection-mode=1` set; D-POCO's "Start head unit server"
was reached and started (confirmed listening on TCP `5277`/`0x149D` via `/proc/net/tcp6`) after
navigating: system Settings → search "Android Auto" → App info → "Additional settings in the app" →
scroll to Version → tap ×10 → the screen's own 3-dot overflow menu → "Developer settings" → "Start head
unit server". UI-only; no scriptable shortcut was found for any step. The operator first supplied a
home WiFi network, which turned out to have client/AP isolation (D-HU could not ping D-POCO at all,
100% loss) — confirmed as the specific blocker with a same-conditions ping test before switching.
Switching both devices to D-MOTO's own phone hotspot resolved it (0% loss, `10.182.90.0/24`). `NetworkDiscovery`'s own scripted join path never worked in
either condition (see below), so all four connect attempts below used the documented
`headunit://connect?ip=<D-POCO's IP>` deep link directly.

**Attempt A** (home-WiFi-then-hotspot session, precondition for testing ACC-off): SSL handshake
complete `12:00:11.423`, status pill `STARTING_PROJECTION` `12:00:11.429`. 41 s later, with the
session still showing no protocol activity beyond that point, `com.fyt.boot.ACCOFF` was broadcast
(`12:00:52.709`) to exercise the fix under test directly. It fired correctly and immediately:
`"AapService: ACC_POWER_LOST with a live session — closing it now, while the link still works..."`
(`12:00:52.718`), completing in **170 ms** (`"link-loss teardown finished in 170ms"`, `12:00:52.888`).
**This is O6's own "closed on ACC-off" mechanism working exactly as designed** — but it closed a
session that was itself stuck at `STARTING_PROJECTION`, not a genuinely live, projecting one, so it is
a qualified confirmation of the mechanism rather than a full-fidelity test of the reporter's scenario.

**Redial after the ACC-off close:** `ACTION_START_WIRELESS_SCAN` was broadcast afterward to exercise
the "redialled when WiFi returns" half. `NetworkDiscovery` ran repeatedly (`12:01:49` through
`12:02:17`, once every ~12 s) but every gateway/subnet scan swept `100.118.187.*` — the same wrong,
unrelated LTE-style subnet as the original "no WiFi at all" finding, despite `wlan0` carrying a live,
validated `10.182.90.90` network the whole time (confirmed via the same capture's own
`NetworkMonitor: Capabilities changed` line). **This is a confirmed, reproducible defect in
`NetworkDiscovery`'s interface selection**, not a rig-hardware limitation: it never found D-POCO, so
the redial half of O6's claim could not be exercised through the app's own scripted path at all.

**Attempt B** (a second manual connect via the deep link, D-POCO's server not restarted since Attempt
A): reproduced the classic "deaf server" bug the app's own code already names and explains. Version
request retried three times (`12:02:30.837`, `12:02:33.043`, `12:02:35.248`), then:
`"the peer accepted the connection and then sent nothing at all... Force stop Android Auto on the
phone, and reboot it if that does not help"` (`12:02:37.456`) → `"session state failed (peer_silent)"`
(`12:02:37.473`). The message is excellent, but **the home screen showed no trace of it**
(`evidence/O6-deaf-banner.png`, `evidence/O6-deaf-banner2.png`): both screenshots show the stale
`"Android Auto is starting… Securing the connection"` banner, unchanged from before the failure. This
looks like the same known consequence of a session left open when the phone's own head unit server
never got a clean close — i.e., a plausible knock-on effect of Attempt A/other stalled sessions never
resolving on the phone side, though this round's evidence cannot prove that causal chain, only the
sequence.

D-POCO's server needed a manual Gearhead force-stop and restart to recover, twice more, for two further
attempts:

**Attempt C** (after the first Gearhead restart): SSL handshake complete `12:12:27.780`, pill
`STARTING_PROJECTION` `12:12:27.788`. Self-disconnected 36 s later: `"session state disconnected
(link_lost)"` (`12:13:03.523`), screen showing a full "Connection lost / Retrying…" overlay
(`evidence/O6-attemptC-connection-lost.png`). Same shape as Attempt A, minus the manual ACC-off (this
one timed itself out).

**Attempt D** (after a second Gearhead restart): SSL handshake complete `12:16:28.336`, pill
`STARTING_PROJECTION` `12:16:28.342`. This time it did not self-disconnect at all — it hung for 4+
minutes with zero protocol activity, home screen showing a persistent "Android Auto is starting…"
banner (`evidence/O6-attemptD-stuck.png`), until manually ended via `headunit://disconnect`.

**Net finding across all four attempts:** every single one completed the SSL handshake and reached
`STARTING_PROJECTION`; **none ever reached `TransportStarted`** (real video/audio channel open). Three
different terminal shapes were observed (external ACC-off teardown, a ~36 s self-timeout to
`link_lost`, and an indefinite hang with no self-timeout at all), which argues against a single simple
timing fluke and for a real, reproducible defect in whatever is supposed to carry the session from
handshake to projection on this rig/build combination — separate from the ACC-off/redial fix O6 itself
grades, and separate from the already-known deaf-server bug.

**VERDICT O6: INCONCLUSIVE.** The "closes cleanly on ACC-off" mechanism is confirmed working (Attempt
A), but only against a stalled, not a genuinely live, session — a qualified result, not a clean PASS.
The "redialled when WiFi returns" half was never reachable: `NetworkDiscovery`'s own interface-selection
bug blocks the scripted route, and no session on this rig ever settled long enough to test a
WiFi-toggle redial against manually either. Both gaps trace to defects this round found rather than to
a rig hardware limitation, so this is reported as INCONCLUSIVE rather than UNTESTABLE.

Evidence: `evidence/O6.txt.gz` (original no-WiFi attempt), `evidence/O6-attemptA-accoff.txt.gz` +
`evidence/O6-attemptA-accoff-cont.txt.gz` (Attempt A, ACC-off teardown), `evidence/O6-state-attemptA.png`,
`evidence/O6-discovery-retry.txt.gz` (repeated wrong-subnet scans between attempts),
`evidence/O6-attemptB-deafserver.txt.gz`, `evidence/O6-state-attemptB.png`, `evidence/O6-deaf-banner.png`,
`evidence/O6-deaf-banner2.png` (Attempt B, deaf-server repro), `evidence/O6-attemptC-retry.txt`,
`evidence/O6-attemptC-connection-lost.png` (Attempt C), `evidence/O6-attemptD-retry.txt`,
`evidence/O6-attemptD-stuck.png` (Attempt D).

**Addendum, same afternoon, unprompted:** the identical stall reproduced again after the round was
otherwise wrapped up and D-HU's settings had already been restored to `wifi-connection-mode=3`
(Native AA) — this time on a genuine Native AA / WiFi Direct session, not the Headunit Server path
O6 was testing. The home screen's own banner read `"Android Auto is starting… WiFi Direct on 5 GHz
(5805 MHz)"` (`evidence/O6-native-stall.png`), and `dumpsys bluetooth_manager` confirmed the peer was
D-POCO (`A2DPSinkStateMachine` state=Connected). `netstat` showed one `CLOSE_WAIT` and one
`FIN_WAIT1` socket lingering on port 5288 (Native AA's own TCP port) for the whole observation window,
unchanged between two checks about two minutes apart. Watched for 5+ minutes: zero protocol activity
beyond the same `GpsLocation: fix received` noise seen in every other attempt, no self-timeout at
all — the same indefinite-hang shape as Attempt D, this time on the mode O6 was not even testing.
**This confirms the `STARTING_PROJECTION` stall is not specific to the Headunit Server / manual-connect
path this round used to route around `NetworkDiscovery`'s bug — it also reproduces on ordinary Native
AA wireless sessions**, which materially broadens the earlier finding: whatever is failing to carry a
session from handshake to real projection is not confined to O6's own test path. The handshake/mode
lines from this session's own start were lost to the rig's small default logcat ring buffer (GPS fix
lines every ~1 s filled it before the state was checked) — a genuine gap, not withheld evidence; a
future capture of this specific finding should raise the logcat buffer size (`-b main -g` or a larger
`-G`) before relying on `logcat -d` retroactively.

Evidence: `evidence/O6-native-stall.png`, `evidence/O6-native-stall-final.png` (banner and screen,
~90 s apart), `evidence/O6-native-stall.txt` (the ring-buffer dump caught after the handshake had
already scrolled out), `evidence/O6-native-stall-cont.txt` (forward capture from first observation to
manual disconnect, ~6 minutes, GPS noise only).

**Second addendum, same afternoon: a reliable trigger for the stall, found by the operator.** Ending an
Android Auto session on the phone's own side (tapping the phone's on-screen "Exit" inside the AA
projection, then "Finish later" on AA's own "Exit setup?" prompt) reproduced the stall twice in a row,
cleanly, on demand. The operator's own framing, verbatim: *"the pill/wireless reconnect process
shouldn't be present right after a disconnection even after a bye-bye, that is not a wireless failure
but a user disconnection."*

Both reproductions show the identical sequence, down to the millisecond shape:
1. The phone sends a clean `ByeByeRequest`: `"!!! RECEIVED BYEBYE REQUEST FROM PHONE !!! Reason:
   USER_SELECTION"` (`12:55:23.519` and again `12:58:07.863`).
2. The head unit correctly classifies this as `session state disconnected (phone_left)`, with
   `isUserExit=false, isClean=true` (`AapProjectionActivity: Finishing because state
   isUserExit=false, isClean=true, killOnDisconnect=false`) — this is not a bug in how the disconnect
   itself is classified.
3. `AapService: Native AA session ended; keeping the WIFI_DIRECT network up for the phone's return`,
   and `NativeAA: the phone ended the session itself, so the listeners reopen without waking it` — the
   code deliberately does **not** poke or force a reconnect here; three separate `onNativeCredentials`
   call sites all log `"the phone ended the last session itself. Skipping auto-poke until it comes
   back"` in the same half-second. This part of the design is sound and was not what the operator's
   principle is about.
4. The **phone itself** redials on its own initiative through the listener the head unit left open —
   `NativeAA: Connection accepted from POCO X3 NFC` arrives **501 ms** after the listeners reopened
   (`12:55:24.560`, ~500 ms after `24.059`) in the first repro; a fresh handshake follows immediately,
   and the pill is back at `CONNECTING`/`STARTING_PROJECTION` within ~1.3 s of the original bye-bye.
5. That new session then stalls at `STARTING_PROJECTION` exactly like every other one this round —
   still stuck 51+ s later when last checked in the second reproduction.

**The asymmetry the operator's principle points at:** a head-unit-*initiated* exit (`isUserExit=true`,
either from the exit dialog's "Stop" or the `headunit://exit` deep link) gets an explicit
`"User exit cooldown active for 5000ms"` before anything is allowed to reconnect. A phone-*initiated*
clean exit (`isUserExit=false, isClean=true`, exactly this scenario) gets no equivalent cooldown or
suppression at all — the code correctly refrains from actively poking the phone, but it also does
nothing to stop the phone from redialling immediately on its own, even though the disconnect was just
as deliberate and just as clean as the head-unit-initiated case. Whether that gap is worth closing (by
extending the existing cooldown to `phone_left` as well as `user_exit`) is a design call for whoever
owns this code, not something this round can decide — reported as a precise, reproducible finding
rather than a prescribed fix.

This is also the **sixth** independent confirmation this round of the `STARTING_PROJECTION` stall
(four manual Headunit Server connects, one unprompted Native AA session, and now two bye-bye-triggered
Native AA reconnects), reinforcing that whatever blocks a session from reaching `TransportStarted` is
a general defect triggered by starting *any* new session on this rig right now, not a property of any
one connection path.

Evidence: `evidence/O6-byebye-reconnect-repro.txt.gz` (both reproductions, one continuous capture,
`12:49`-`12:59`), `evidence/O6-byebye-reconnect-repro.png` (the second reproduction's stalled state).

## Anything the brief did not ask about

- **D-POCO's classic-Bluetooth bond with D-HU silently dropped mid-round**, unprompted by anything
  this round did on purpose (see Setup notes). It re-bonded with a single tap on a stale "Saved
  devices" entry in D-POCO's own Bluetooth settings, no PIN confirmation needed by the time it was
  checked. Worth watching for in any future round that runs long on this phone pairing.
- **A head unit's own Bluetooth-adapter cycle can substitute for a phone-side event** in triggering
  Android Auto's ordinary (non-escalated) auto-launch on D-MOTO — confirmed three independent ways in
  H2r/H5. This is a genuinely useful lever for future rounds that want a fast, no-wake-needed session
  on this specific phone, and a trap for any round that wants to isolate the *escalated* wake's effect
  from ordinary reconnection on D-MOTO specifically: that isolation needs D-POCO, not D-MOTO, on this
  rig as of this round.
- **This rig had no working shared WiFi at round start, from the "no `wlan0` IP anywhere" angle** — not
  new (memory already documents the hotspot-config problem), but a slightly different fact than the
  previously-documented hotspot-SSID-unreadable one. Resolved for this round only by the operator
  supplying an external network (see O6); this is a standing rig limitation for any future round that
  needs Headunit Server / common-WiFi (NSD) modes without that kind of help.
- **`NetworkDiscovery`'s subnet scan never adapts to the interface actually carrying traffic.** Every
  scan this round, in every WiFi condition (none, home WiFi, D-MOTO's hotspot), swept the same
  `100.118.187.*` range — an unrelated LTE-style subnet — and never once probed the real `wlan0`
  subnet even when it was live, validated, and the only network with connectivity. This is the single
  biggest blocker to a scripted (WiFi-button/discovery-driven) Headunit Server round on this rig and is
  a genuine candidate defect worth its own investigation, independent of anything O6 grades.
- **No session with D-POCO ever reached real projection, in seven independent attempts across two
  connection modes and three different triggers.** Four manual Headunit Server connects, one
  unprompted ordinary Native AA session, and two Native AA reconnects triggered by a clean
  phone-initiated bye-bye (see O6's addenda) — every single one completed the SSL handshake, reached
  `STARTING_PROJECTION`, and never reached `TransportStarted`. This is not confined to whatever O6's
  own testing methodology does differently from ordinary use, and is significant enough to flag as a
  standalone, general defect regardless of what caused it.
- **A clean, phone-initiated Android Auto exit is followed by an unwanted automatic reconnect, with no
  cooldown.** Found and reproduced twice by the operator: ending the session from the phone's own AA
  UI ("Exit" → "Finish later") sends a clean `ByeByeRequest` (`isUserExit=false, isClean=true` on the
  head unit's side), and the phone redials through the head unit's still-open listener within about
  half a second, landing back at `STARTING_PROJECTION` (where it then stalls, per the above). The head
  unit's own code deliberately does not poke or force this reconnect — three separate log lines confirm
  it explicitly skips auto-poking because "the phone ended the last session itself" — but a
  head-unit-*initiated* exit gets an explicit 5-second cooldown before any reconnect is allowed
  (`"User exit cooldown active for 5000ms"`) that a phone-*initiated* clean exit does not get at all.
  The operator's own framing: a bye-bye is a deliberate disconnection, not a wireless failure, and
  shouldn't be treated as one by immediately showing a reconnecting pill. See O6's second addendum for
  the full, millisecond-level trace and evidence.
- **The home screen shows no information when a session fails with `peer_silent` (the "deaf server"
  bug).** The app's own log carries an excellent, actionable diagnostic
  (`"the peer accepted the connection and then sent nothing at all... Force stop Android Auto on the
  phone..."`), but the home screen banner stays on its stale "Android Auto is starting…" text
  (`evidence/O6-deaf-banner.png`, `evidence/O6-deaf-banner2.png`) instead of surfacing any of it. A
  user hitting this in the field has no on-screen indication of what happened or what to do about it.

---

## Addendum, 2026-09-17, written from the captures after the round: the stall is the raise

This section corrects the round's own reading of the `STARTING_PROJECTION` stall. It changes two
verdicts above and nothing else. It was written from the evidence already in this directory; no new
hardware time was spent.

**The instrument was wrong.** `TransportStarted` is a Kotlin state name and **never appears as a log
string**, in any capture, on any build. Grading "did it project" on that grep returns zero for a
session that projected perfectly, which is why "no session ever reached real projection" survived
review. The lines that do prove projection are `Service Discovery Response`, `Channel Open
Response`, `Media Sink Setup Request: N on channel VIDEO` and `Throughput over …ms: rendered=`.

**Two of this round's own sessions with D-POCO did project,** before the stalls, on this build:

```
10:49:13.257  SSL handshake complete                       (C1 attempt 1)
10:49:13.688  Service Discovery Request      ->
10:49:13.707  Service Discovery Response     <-
10:49:14.044  Channel Open Request/Response x6   SENSOR VIDEO INPUT AUDIO2 AUDIO MIC
10:49:14.083  Media Sink Setup Request: 3 on channel VIDEO
10:49:14.989  Configuring decoder: c2.unisoc.avc.decoder for 1280x720
10:49:20.055  Throughput over 5004ms: rendered=43, fed=48, dropped=0
```

C1 attempt 2 is the same with `rendered=46`, and `H5.txt.gz` carries four more. Two of the
bye-bye-repro sessions projected as well (`12:54:00` and `12:58:01`), with 169 RECV / 191 SEND and
90 Ping pairs. So the defect is not "any new session on this rig".

**What separates a session that projects from one that hangs is in the pill line.** The read loop is
started by `AapProjectionActivity` once it has a surface, so no projection screen means no transport
and a live socket with nothing on it:

| Session | Pill line | Projection | Outcome |
|---|---|---|---|
| 12:54:00, 12:58:01 | `STARTING_PROJECTION (not shown, the overlay owns the screen)` | `onCreate` +150 ms | full session |
| 12:55:25, 12:58:13 | `STARTING_PROJECTION` | never | RECV=0, SEND=0 |
| 12:12:27 (Attempt C) | `STARTING_PROJECTION` | `onCreate` 35 s late, by hand | read loop 6 ms after the surface; phone had already sent EOF |
| 12:16:28 (Attempt D) | `STARTING_PROJECTION` | never | hung 6 m 10 s |

The suffix means an auto-connect attempt was in flight, and that path raises the projection itself
with a plain `startActivity` **from a foreground activity**, where the background-launch restriction
does not apply. The sessions without it had only the service's raise, which on API 29+ with no
permission to draw over other apps posts a full-screen-intent notification that Android 14 demotes.
Nothing came up until somebody tapped. In Attempt D `HomeFragment.onResume` prints one second before
the handshake, so the app was **visibly on screen** and still fell through to the notification.

**This round created that condition itself.** O4's `appops set … SYSTEM_ALERT_WINDOW deny` ran at
11:49, nothing restored it, and a `settings.xml` restore cannot: `appops` is not in that file.
Every stall is after it and both C1 sessions are before it. The brief's own fault, not the
operator's: it denies the permission and never gives it back, and O5 and O6 run after O4.

**Attempt C is the proof in one trace:** handshake at `12:12:27.784`, 35 s of nothing,
`AapProjectionActivity.onCreate` at `12:13:02.714`, `Start Aap transport read loop` at
`12:13:03.010` (6 ms after the surface), `session state projecting` at `.064`, and `link_lost` at
`.523` because the phone had already given up.

**Two verdicts change.** "No session with D-POCO ever reached real projection" is withdrawn: four
did. The `STARTING_PROJECTION` stall stands as a real defect, but its cause is the projection raise,
not anything carrying a session from handshake to picture.

**A third finding here is worse than the round recorded it.** After
`session state failed (peer_silent)` at `12:02:37.473`, the pill stayed on **SECURING for 3 m 39 s**,
until `12:06:16.972`. The screenshots labelled "no on-screen trace" are that: a pill still claiming
it was securing a connection that had already failed.

**What is unchanged.** `NetworkDiscovery` sweeping the wrong subnet is a real defect and independent
of all of the above. So is the reconnect with no cooldown after a clean phone-initiated exit. Both
are fixed on the branch, along with the raise, the pill and the deaf-server banner; round 3 grades
them.
