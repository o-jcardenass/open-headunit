# usb-native-loop, round 2 brief

**Revision 2026-10-25 (before publish).** The candidate moved from `bafdcb2e` to `f9a72d480` (`main`,
4.0.0-alpha). Changed: section 1, the Queue line, the USB recovery notes in sections 2 and 3, the R4-G
hold script and conditions, K1 item 2, and section 6.

**Revision 2026-10-29 (before publish).** Moved onto `rig-lib` and a `rig-requires` block. The round runs
as one `rig_session.py` process. Same question, runs, conditions and builds. Each arm is now its own run,
with its own grade script. R4-G brings up its own session, and K1c is its own run.

Publish name on `transfer/rig-rounds`: `usb-native-loop-round2-brief.md`. Results go in
`usb-native-loop-round2-results.md`. Evidence goes in the release `rig-evidence-usb-native-loop` as the
asset `usb-native-loop-round2-captures.zip`. Round 1 never ran, so the release does not exist yet:
create it (template §7, "first round of a thread only").

**This round replaces round 1.** `usb-native-loop-round1-brief.md` is queued and was never run. Do not
run it.

This is a reproduction round. No fix is on trial. It measures what `main` does today and gives the red
baseline for two later fixes. It covers two reports:

1. **The USB loop report.** A TOPWAY TS7 head unit on 3.4.0, used over USB with the wireless mode set
   to Native AA. The session drops after about 10 minutes and then reconnects again and again. Helper
   mode works.
2. **The close-on-launch report.** An ECARX head unit (API 22) with a CarlinKit adapter on USB. After a
   session ends, the app closes, and every tap on the icon shows the splash and exits, until the
   adapter reboots.

```rig-requires
lib 0602b3d87
candidate v340 013b05bc0e7f5a881ea1d4363b9275df69bd5f5a
candidate main f9a72d480d51105870f62df6f0a40a1276e98a34
unit D-POCO role=headunit wifi=on bt=on battery=90
unit D-MOTO role=phone wifi=on bt=on bond=D-POCO nobond=D-HU aa=D-POCO battery=90
unit D-HU bt=off
unit D-SAM role=headunit
hardware the cable or OTG adapter that plugs D-MOTO into the USB-C port of D-POCO, at hand
confirm D-POCO and D-MOTO are charged to 100% on their PC cables
confirm D-MOTO unlocks with a swipe, or you will unlock it when run P asks
confirm if P3 picks HAND, you will pull and replug the D-MOTO cable 9 times, each when an OPERATOR line says PULL-PLUG
run P cap=1800 script=p.sh grade=grade-p.sh hand="when the OPERATOR line comes: unplug D-POCO and D-MOTO from the PC, plug D-MOTO into the USB-C port of D-POCO, on D-MOTO set USB controlled by to This device, unlock D-MOTO if it shows a PIN"
run P3 cap=1200 script=p3.sh grade=grade-p3.sh
run R1 cap=3900 script=r1.sh grade=grade-r1.sh
run R2 cap=1800 script=r2.sh grade=grade-r2.sh
run R3 cap=1200 script=r3.sh grade=grade-r3.sh
run R5 cap=3900 script=r5.sh grade=grade-r5.sh
run R4-G cap=1200 script=r4g.sh grade=grade-r4g.sh point=yes
run R4-H cap=1800 script=r4h.sh grade=grade-r4h.sh
run K1 cap=900 script=k1.sh grade=grade-k1.sh point=yes
run K1c cap=360 script=k1c.sh grade=grade-k1c.sh
run K2 cap=900 script=k2.sh grade=grade-k2.sh
run X cap=1200 script=x.sh grade=grade-x.sh always=yes hand="at the OPERATOR line at the end of X: put D-POCO and D-MOTO back on their PC cables"
stop P FAIL,INCONCLUSIVE,UNTESTABLE skip=all
stop R4-G UNTESTABLE skip=K1,K1c
stop K1 UNTESTABLE skip=K1c
```

| Run | Build | What it measures | Units |
|---|---|---|---|
| P | both | APK identity, settings backup, wireless adb, the OTG plug | D-POCO, D-MOTO, D-HU (radio only) |
| P3 | v.3.4.0 | which scripted drop is closest to a cable pull | D-POCO, D-MOTO |
| R1 | v.3.4.0 | arm A: a 25 min Native USB session with no action, then 2 forced ends (R2-N) | D-POCO, D-MOTO |
| R2 | v.3.4.0 | arm B: 2 forced ends in Helper (R2-H); the R2 verdict compares them with R2-N | D-POCO, D-MOTO |
| R3 | v.3.4.0 | arm C: R2-N again with WiFi not chosen as a connection mode | D-POCO, D-MOTO |
| R5 | `main` | arm D: a 25 min Native USB session with no action (the control for R1), then 2 forced ends (R4-N) | D-POCO, D-MOTO |
| **R4-G** | `main` | **the point of the USB loop half: 70 s of failed USB tries, then does Native wake the phone that is still plugged in** | D-POCO, D-MOTO |
| R4-H | `main` | arm E: 2 forced ends in Helper; the R4 verdict compares them with R4-N, plus the home-screen skip (H2) | D-POCO, D-MOTO |
| **K1** | `main` | **the point of the close-on-launch half: "close the app on disconnect" on, a failed handshake, three icon launches** | D-POCO, D-MOTO |
| K1c | `main` | K1's control, the same setting off, the same wedge | D-POCO, D-MOTO |
| K2 | `main` | cold icon launch on D-SAM (API 19), Native mode, nothing on USB | D-SAM |
| X | | restore Gearhead, settings, screen, radios; cables back to the PC | all |

**Estimated time.** P 15 min plus the plug. P3 8 min. R1 about 41 min. R2 12 min. R3 9 min. R5 about
41 min. R4-G 9 min. R4-H 12 min. K1 8 min. K1c 3 min. K2 5 min. X 5 min plus the replug. **Total about
2 h 50 min of runs**, plus the thermal-gate waits and the host's two builds (about 35 min, before the
session). If P3 picks `HAND`, the operator also pulls the cable 9 times, about 4 to 10 minutes apart.

**Queue line:** `usb-native-loop-round2-brief.md` (`main` `f9a72d480` and tag `v.3.4.0` `013b05bc`;
units D-POCO as USB host and D-MOTO, D-SAM for K2, D-HU Bluetooth only; replaces round 1).
It can pair with a round that uses only D-HP.

## 1. Build and baseline

| Arm build | Ref | Commit | Tree | `commit` in `ACTION_QUERY_STATE` |
|---|---|---|---|---|
| **v340** | tag `v.3.4.0` | `013b05bc0e7f5a881ea1d4363b9275df69bd5f5a` | `e86e02a1885d9e719e4e11064a2d203d9f179e04` | `013b05bc0e7f` |
| **main** | `origin/main` | `f9a72d480d51105870f62df6f0a40a1276e98a34` | `ff8fe6c2ea0a30f9e44276c6e9d3152591173969` | `f9a72d480d51` |

- `main` at `f9a72d480` is 4.0.0-alpha. It is not 3.5.0: it adds a reworked USB accessory recovery
  (`UsbRecoveryTarget`, `UsbRecoveryDevice`, a targeted reconnect after a reset), a new home screen and
  about 390 other changed files. What this round measures on `main` holds for `f9a72d480` only.
- No history was rewritten. Both refs are on `origin` (upstream). Fetch them from there.
- **JVM tests at `f9a72d480` on the dev host: 4031 tests, 0 failures, 0 skipped.** Do not run the unit
  tests on the rig. `v.3.4.0` is a release tag with nothing of ours on it, so no count applies.
- A `-dirty` suffix on `commit` is acceptable (the worktree holds an untracked `local.properties`).
- Version codes: `v.3.4.0` is 109 and `main` is 120. D-POCO may carry another build. The scripts install
  with `adb install -r -d` only (`inst`). Never uninstall.
- **DEX identity symbols:** `ConnectionArbiter` exists in `main` and not in `v.3.4.0`. `UsbRecoveryTarget`
  exists in `f9a72d480` and not in `v.3.4.0` or the earlier `main` `bafdcb2e`. Run P checks both.

The host builds both arms before the session, in one worktree, 3.4.0 first (skill step 3). Each APK goes
to `$EV/apk/<candidate name>.apk`, so the scripts read `$EV/apk/v340.apk` and `$EV/apk/main.apk`. Check
each tree after the checkout:

```text
git -C $WT fetch --no-tags origin tag v.3.4.0 main
git -C $WT checkout --detach 013b05bc0e7f5a881ea1d4363b9275df69bd5f5a
git -C $WT rev-parse 'HEAD^{tree}'      # MUST print e86e02a1885d9e719e4e11064a2d203d9f179e04
git -C $WT checkout --detach f9a72d480d51105870f62df6f0a40a1276e98a34
git -C $WT rev-parse 'HEAD^{tree}'      # MUST print ff8fe6c2ea0a30f9e44276c6e9d3152591173969
```

Each build waits on `$RIG_TOOLKIT/rig_thermal.sh wait 70`. Never run `git fetch --prune-tags` in that
worktree. If a tree line differs or a build fails, do not start the session.

## 2. What this is and why

### The USB loop report

No log came with it. The ticket's diagnosis is a code reading of `v.3.4.0` and `main`. Its five
hypotheses:

- **H1.** Any first end, then the Native re-arm races the USB reconnect. On 3.4.0
  `rearmWirelessAfterWiredSession` calls `setActiveFromSettings(force = true)` 1.5 s after the end, with
  no USB deferral. So a P2P group and a Bluetooth wake poke run while the phone comes back on USB, and
  the quiesce at the next USB open tears them down. Helper sends no poke. On `main` the re-arm goes
  through `deferWirelessForUsbHandoff`, and `ConnectionArbiter` holds wireless down between USB tries.
- **H2.** In Native mode `HomeFragment.attemptAutoConnect` logs `HomeFragment: Native AA mode active.
  Skipping manual auto-connect attempt.` and skips the last-session reconnect. Only the service's 3 s
  re-check brings USB back. This is still on `main`.
- **H3.** The wake poke before the session takes the phone's hands-free link, and the phone later ends
  the USB session itself (a `ByeByeRequest`).
- **H4.** The first end has nothing to do with the mode (the unit's USB or power). This rig has no such
  event, so this round cannot test H4.
- **H5.** A Native part outlives the quiesce and ends the session.

**The surviving path on `main`, and the point of this half.** `main` stands wireless down for one USB
plug-in for at most `USB_EPISODE_BUDGET_MS` (60 s) of failed tries.
`ConnectionPriorityPolicy.standsDownWireless(Owner.USB, usbEpisodeSpent = true)` returns false after
that. The arbiter then logs `USB has tried for 60 s with no session; wireless comes back beside it` and
gives wireless back. A USB attempt that stops for more than `USB_QUIET_MS` (8 s) gives wireless back
too (`... ended with no session; giving back wireless`). In Native mode either one arms the stack and
pokes the phone over Bluetooth while it is still plugged in. R4-G measures that.

**What the reworked USB recovery on `f9a72d480` does to that state.** A failed handshake on a phone in
accessory mode still runs the same two-step ladder (`StaleAccessoryRecoveryPolicy`: a re-switch, then a
USB reset, then give up). Three things are new:

- The ladder is bound to the device that failed (its bus path, VID, PID and identity) and to the
  attempt that owns the USB re-check. It acts on nothing else on the bus.
- Each step now logs `... returned <true|false>; observing the USB bus` and watches the bus for up to
  15 s (5 s for a detach, 10 s for the return).
- After a reset that re-enumerates the phone, it reconnects only the matching accessory
  (`targeted recovery found ...`), and it can ask for USB permission for it. If no automatic grant
  comes, that request shows `UsbPermissionActivity` on D-POCO.

None of this resets the 60 s budget or stops the service's 3 s re-check. The ladder holds the arbiter
for at most about 20 s, and every try lands inside the 8 s quiet window. So the prediction for R4-G is
unchanged: wireless comes back on the 60 s budget while the phone stays on the bus. With Android Auto
disabled, the reset re-enumerates the phone back into accessory mode, so the targeted reconnect fails
again and the ladder gives up (`recovery used both steps; a replug is needed`).

### The close-on-launch report

The four logs that came with it hold one healthy session and not the failure. The code review on
`main` ranks one mechanism first:

- `CommManager.startHandshake` ends a failed handshake with `disconnect(sendByeBye = false,
  isUserExit = false)`. `honorKillOnDisconnect` keeps its default `true`. So with "Close app on
  disconnect" on (`kill-on-disconnect`), a failed handshake broadcasts `ACTION_FINISH_ACTIVITIES`, and
  500 ms later `AapService.onDestroy` logs `killProcessOnDestroy is true` and calls `System.exit(0)`.
- A session that ends by a read error goes through `transportedQuited`, which also honours the setting.
- So a wedged adapter that stays on the bus in accessory mode turns every icon launch into: cold start,
  `Found device already in accessory mode`, a failed handshake, exit. That matches "splash, then the car
  launcher" on every tap.
- `f9a72d480` keeps this path as it was. The recovery ladder starts at the re-switch in each new
  process, because its step count lives in memory. The exit comes 500 ms after the failure, so a launch
  can end before the re-switch finishes. A re-switch did not re-enumerate the phone in 0 of 6 tries
  (`archive/rounds/usb-reattach-round2-results.md`), so the phone should stay in accessory mode between
  launches. With the setting off (K1c), the ladder runs to its give-up in about 20 to 30 s, which the
  45 s window covers.
- `Settings.killOnDisconnect` returns false when `isCarLauncherActive` (`enable-car-launcher` true, or
  the app is the default home app). P and K1 check both.

K1 builds that wedge on purpose: D-MOTO stays in accessory mode with Android Auto disabled. K2 tests
the other ranked mechanism (the Native bring-up on a cold launch) on the oldest rig unit, as weak
evidence only.

## 3. What is different about this round

- **Layout.** D-POCO is the head unit and the USB host, on wireless adb, with its USB-C port free.
  D-MOTO is the phone, plugged into that port, also on wireless adb. D-HU cannot host USB
  (`rig-quirks/topics/tooling.md`, "No USB accessory path"). D-SAM stays on its PC cable and takes part
  only in K2.
- **Wireless adb and the requirements check.** After P both phones answer only on `IP:5555`. P appends
  both addresses to the rounds dir's `unit-serials.txt`, so a relaunch after P reads them there and
  needs no cable.
- **D-HU's Bluetooth is held off for every run** (`rl_hold_off`). D-MOTO is bonded to D-HU's radio, and
  Gearhead can dial a cached head unit instead of the one under test (`rig-quirks/units/D-POCO.md`). It
  self-reverts, so a single switch does not hold. X turns it back on.
- **No video is graded.** A phone that stands in as the head unit carries no video. Grade session
  lifetime, session ends, reconnects and process exits only.
- **D-POCO's screen must stay on.** A session ends 16 s after the handshake if the projection screen
  never comes up, and `main` stops reconnecting by itself after two such ends. Every bring-up sets
  `svc power stayon true`, wakes the panel and dismisses the keyguard. A cycle with
  `the projection screen never came up` is `CONFOUND` and does not count.
- **D-MOTO's Bluetooth is off while each arm brings its first session up.** In Native mode the launch
  pokes the phone. With Bluetooth on, the first session could form over WiFi. After the first USB
  session it goes on for R1 to R4-H, because H1 and R4-G need the poke to reach the phone. K1 and K1c
  keep it off: they test a handshake, not a wake.
- **The wake poke targets D-MOTO only** (`native-poke-all-paired` false, `native-poke-bt-macs` =
  D-MOTO). Helper ignores both keys.
- **No station stand-down can cut D-POCO's wireless adb.** `StationStandDownPolicy.isAvailable` is false
  from API 35. P fails below 35, and the stop rule ends the round.
- **D-POCO powers D-MOTO over OTG and cannot charge.** Each arm reads the battery first and does not
  start below 40% (the run is UNTESTABLE). A soak stops at 20%.
- **`MATCH! Starting AapService` and a second `createGroup SUCCESS` are measurements here, not discard
  rules.** Count them.
- **The drop must take the phone out of accessory mode.** On builds before 3.5.0 a session end that
  leaves the phone in accessory mode makes every later handshake fail in every mode: a plain retry
  recovered 0 of 11 cycles and a Gearhead force-stop plus `ACTION_CHECK_USB` 0 of 15
  (`archive/rounds/usb-reattach-round2-results.md`). That is why the forced end in R1, R2, R3, R5 and
  R4-H is not a Gearhead force-stop: on 3.4.0 it would fail Helper too and hide H1. It is a cable pull,
  or the port-role lever if P3 shows it acts like one.
- **Why not `svc usb setFunctions mtp` as the drop.** It brings the phone back in about 1 s. The re-arm
  starts 1.5 s after the end and the poke about 2 s after that, so USB can win before any poke, and the
  race H1 describes never runs. Round 1's brief has the full comparison. It stays the recovery step.
- **How R4-G and K1 hold the phone in accessory mode with no Android Auto.** `am force-stop` alone does
  not hold Gearhead down: its own Bluetooth receiver restarts it (`rig-quirks/topics/gearhead.md`).
  `pm disable-user --user 0 com.google.android.projection.gearhead` then `am force-stop` does. It worked
  on D-MOTO (not rooted) in `archive/rounds/usb-reattach-round2-results.md` (RG: `gh_disabled=1`,
  `gh_procs=0`, every handshake failed until `pm enable`). It does not clear Gearhead's data. **This
  brief authorizes it** for R4-G, K1 and K1c. R4-G and K1c re-enable Gearhead at their end, K1 leaves it
  disabled for K1c, and X re-enables it again. Check with `pm list packages -d` and `ps -A`, never
  `dumpsys package` or `pidof` (`rig-quirks/topics/lifecycle.md`). If `pm disable-user` is refused, R4-G
  is UNTESTABLE and the stop rule skips K1 and K1c.
- **The USB permission grants.** P and X read `dumpsys usb` and record both. Never tick "Always" in a
  dialog (template §7b).
- **The rig audio keys are a deliberate worst case.** This round does not grade audio and writes no
  audio key. P records D-POCO's values once.
- **`video-profile-starvation-cap` is deleted at the start of every arm**, because a head unit with no
  video sets it after three sessions with no frame. Each arm reads it back at its end.

### Expected outcomes, said up front

- **R1 and R5: expected FAIL for R1 and PASS for R5 (no end in 25 min).** The rig has no Topway USB or
  power event. That rules out H3 and H5 on this rig only.
- **R2 INCONCLUSIVE** if R2-H also fails to recover: the drop then breaks 3.4.0 in any mode.
- **R2 INCONCLUSIVE** if no R2-N cycle shows a group or a poke before its first SSL (`ovl=0`).
- **R4-G INCONCLUSIVE** if USB stops trying within 8 s and the arbiter gives wireless back through the
  quiet path rather than the 60 s budget, with no poke. The grade notes the route.
- **R4-G may raise one USB permission dialog** for the re-enumerated accessory (the targeted reconnect).
  The hold loop cues H2 when it sees one. Record it; it does not void the run.
- **K2 is weak evidence.** D-SAM is API 19 Samsung, not API 22 ECARX.

### Hand steps

The session asks for the block's `hardware` and `confirm` lines and for the D-MOTO to D-POCO pair (if
it is missing) once, before P. Every other operator step is an `OPERATOR NOW:` line from a run, which
the session pushes to the operator.

| Id | When | Action | Why there is no verb |
|---|---|---|---|
| P-OTG | in P | Unplug D-POCO and D-MOTO from the PC. Plug D-MOTO into D-POCO's USB-C port. On D-MOTO set "USB controlled by" to "This device". Unlock D-MOTO if it shows a PIN | a cable is hardware; the role and a PIN are system screens |
| PULL-PLUG | only if P3 picks `HAND` | Pull D-MOTO's cable out of D-POCO, count to 8, plug it back, set the USB role if D-MOTO asks | P3 found no scripted drop that acts like a cable |
| H2 | fallback | Allow a USB dialog on D-POCO (`UsbPermissionActivity` or `UsbConfirmActivity`). **Never tick "Always"** | a system dialog |
| H3 | fallback | Unplug D-MOTO from D-POCO, wait 5 s, plug it back, set "USB controlled by" to "This device" | the last recovery step |
| X-PC | end of X | Put D-POCO and D-MOTO back on their PC cables | a cable is hardware |

## 4. Settings keys

`put` writes with the app stopped. It starts every write from P's backup, so each arm carries exactly
the backup plus these keys. `rd` reads the keys back into `$EV/<arm>.keys` before the launch. If `put`
cannot write, use `$RIG_TOOLKIT/set_prefs_runas_host.py` with the same keys and say so in Setup notes.

| Key | Type | R1, R5, R4-G (Native) | R2, R4-H (Helper) | R3 | K1 | K1c | Why |
|---|---|---|---|---|---|---|---|
| `wifi-connection-mode` | int | `3` | `2` | `3` | `3` | `3` | the variable (P3 uses `0`) |
| `connection-modes` | string set | `usb,wifi` | `usb,wifi` | `usb` | `usb,wifi` | `usb,wifi` | R3 deselects WiFi |
| `kill-on-disconnect` | boolean | `false` | `false` | `false` | **`true`** | `false` | K1's variable |
| `enable-car-launcher` | boolean | `false` | `false` | `false` | `false` | `false` | a car launcher turns `killOnDisconnect` off (the key is `main` only; 3.4.0 ignores it) |
| `helper-connection-strategy` | int | `2` | `2` | `2` | `2` | `2` | the default |
| `log-level` | int | `2` | `2` | `2` | `2` | `2` | INFO carries every app line in section 6 |
| `onboarding-version` | int | `2` | `2` | `2` | `2` | `2` | no wizard |
| `auto-connect-single-usb` | boolean | `true` | `true` | `true` | `true` | `true` | the app reconnects USB by itself |
| `auto-connect-last-session` | boolean | `true` | `true` | `true` | `true` | `true` | the reporter's setting; the leg Native skips (H2) |
| `auto-start-on-usb` | boolean | `false` | `false` | `false` | `false` | `false` | one reconnect path less |
| `reopen-on-reconnection` | boolean | `true` | `true` | `true` | `true` | `true` | the default |
| `use-libusb` | boolean | `false` | `false` | `false` | `false` | `false` | the Java AOA path |
| `native-driver-selection-mode` | int | `0` | `0` | `0` | `0` | `0` | no driver dialog |
| `native-poke-all-paired` | boolean | `false` | `false` | `false` | `false` | `false` | poke D-MOTO only |
| `native-poke-bt-macs` | string set | `$MOTO_BT` | `$MOTO_BT` | `$MOTO_BT` | `$MOTO_BT` | `$MOTO_BT` | as above |
| `auto-start-bt-macs` | string set | empty | empty | empty | empty | empty | Bluetooth auto-start is not under test |
| `auto-start-on-boot`, `auto-start-on-screen-on` | boolean | `false` | `false` | `false` | `false` | `false` | no other starter |
| `video-profile-starvation-cap`, `native-aa-wake-damage-verdict`, `connection-issue-dismissed-at`, `native-aa-wireless`, `wifi-launcher-mode` | | delete | delete | delete | delete | delete | same start state |

`ACTION_LOG_MARKER` is not in `AutomationCommandPolicy.CONFIGURING` on either build, so markers need no
`allow-external-configuration`. The `connection-modes` value `usb` is the key `ConnectionMode.USB`
stores at `v.3.4.0`. `BASE` in `unl_lib.sh` holds the common keys. Later keys win in `sx.py`, so an arm
adds its own values after `$BASE` (for example `set:connection-modes=usb`).

## 5. Helpers and verbs

`rig_session.py` sources `unl_lib.sh` and then `unl_arm_lib.sh` before every script and grade. `$OUT` is
`$EV`. P writes `$EV/env.txt` (`MOTO_BT`, `POCO_IP`, `MOTO_IP`), and P3 adds `PORTID`, `GAP` and
`METHOD`; the library reads it, so each run finds D-POCO and D-MOTO on wireless adb. Scripts write
counts to `$EV/cycles.tsv` and single facts to `$EV/facts.txt`, and the grade scripts read only those.

**unl_lib.sh**

```bash
# unl_lib.sh: sourced before every script and grade. Serials, verbs, settings writes, captures, windows.
. "${RIG_TOOLKIT:-$HOME/Coding/StudioProjects/rig-toolkit}/rig_devices.sh"
OUT=$EV; SX="$(dirname "${BASH_SOURCE[0]}")/sx.py"
[ -f "$OUT/env.txt" ] && . "$OUT/env.txt"
HU=${POCO_IP:+$POCO_IP:5555}; HU=${HU:-$D_POCO}      # D-POCO, the head unit; on wireless adb after P
PH=${MOTO_IP:+$MOTO_IP:5555}; PH=${PH:-$D_MOTO}      # D-MOTO, the phone; on wireless adb after P
DHU=$D_HU; SAM=$D_SAM; METHOD=${METHOD:-HAND}; GAP=${GAP:-8}
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
MAIN=$PKG/com.andrerinas.openheadunit.main.MainActivity
GH=com.google.android.projection.gearhead
V340=013b05bc0e7f; MAINC=f9a72d480d51
SSL='SSL handshake complete'
SEND='AapService: Stopping GpsLocationService and NightModeManager since connection is disconnected'
BASE="int:helper-connection-strategy=2 int:log-level=2 int:onboarding-version=2 bool:kill-on-disconnect=false bool:enable-car-launcher=false bool:auto-connect-single-usb=true bool:auto-connect-last-session=true bool:auto-start-on-usb=false bool:reopen-on-reconnection=true bool:use-libusb=false set:connection-modes=usb,wifi int:native-driver-selection-mode=0 bool:native-poke-all-paired=false set:native-poke-bt-macs=$MOTO_BT set:auto-start-bt-macs= bool:auto-start-on-boot=false bool:auto-start-on-screen-on=false del:video-profile-starvation-cap del:native-aa-wake-damage-verdict del:connection-issue-dismissed-at del:native-aa-wireless del:wifi-launcher-mode"
RDK='wifi-connection-mode|connection-modes|kill-on-disconnect|enable-car-launcher|helper-connection-strategy|log-level|auto-connect|auto-start|reopen-on-reconnection|use-libusb|native-driver-selection-mode|native-poke|video-profile-starvation-cap'

send() { local a=$1; shift; adb -s "$HU" shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; sleep 0.3; }
mark() { send ACTION_LOG_MARKER --es text "$1" >/dev/null; adb -s "$PH" shell log -t OHURIG "$1" >/dev/null 2>&1; sleep 0.3; }
cue() { echo "OPERATOR NOW: $1"; echo "$(date +%T) $RUN $1" >> "$OUT/hand-steps.log"; }
note() { echo "$(date +%T) $RUN $*" | tee -a "$OUT/setup-notes.md"; }
setf() { echo "$1 $2" >> "$OUT/facts.txt"; }                                 # setf KEY VALUE
fact() { awk -v k="$1" '$1 == k { v = $2 } END { print v }' "$OUT/facts.txt" 2>/dev/null; }
# kv TAG KEY: KEY's value on the last cycles.tsv line whose first word (or first two words) is TAG
kv() { awk -v t="$1" -v k="$2" '$1 != t && $1 " " $2 != t { next }
  { for (i = 2; i <= NF; i++) if (index($i, k "=") == 1) v = substr($i, length(k) + 2) } END { print v }' "$OUT/cycles.tsv" 2>/dev/null; }
num() { if [[ "$1" =~ ^-?[0-9]+$ ]]; then echo "$1"; else echo "${2:-0}"; fi; }   # num VALUE [DEFAULT]
untestable() { grep -a -m1 "^$1 UNTESTABLE" "$OUT/summary.tsv" 2>/dev/null; }    # untestable ARM
reconn() { local i; for i in $(seq 1 12); do adb -s "$1" get-state 2>/dev/null | grep -q device && return 0
  adb connect "$1" >/dev/null 2>&1; sleep 5; done; note "ADB_LOST $1"; return 1; }
batt() { local b; b=$(adb -s "$HU" shell dumpsys battery | grep -a -m1 -oE 'level: [0-9]+' | grep -oE '[0-9]+'); sleep 0.3; num "$b" 0; }
busn() { adb -s "$HU" shell ls /sys/bus/usb/devices | tr -d '\r' | grep -cE '^[0-9]+-[0-9.]+$'; sleep 0.3; }
dlg() { adb -s "$HU" shell dumpsys window | grep -a -m1 mCurrentFocus | grep -aqE 'UsbPermissionActivity|UsbConfirmActivity|UsbResolverActivity'; }   # system
state() { send ACTION_QUERY_STATE | grep -a 'data='; }
rd() { adb -s "$HU" shell run-as $PKG cat shared_prefs/settings.xml | grep -aoE "($1)[^/]*"; sleep 0.3; }
put() { adb -s "$HU" shell am force-stop $PKG; sleep 0.3
  cp "$OUT/backup-poco.xml" "$OUT/w.xml" && python3 -I "$SX" "$OUT/w.xml" "$@"
  adb -s "$HU" push "$OUT/w.xml" /data/local/tmp/ohu-w.xml >/dev/null; sleep 0.3
  adb -s "$HU" shell run-as $PKG cp /data/local/tmp/ohu-w.xml shared_prefs/settings.xml; sleep 0.3; }
livemd5() { adb -s "$HU" pull "$(adb -s "$HU" shell pm path $PKG | head -1 | cut -d: -f2 | tr -d '\r')" "$OUT/live.apk" >/dev/null; sleep 0.3; md5sum < "$OUT/live.apk" | cut -c1-32; }
inst() { local want b; want=$(md5sum < "$1" | cut -c1-32); b=$(basename "$1"); [ "$(livemd5)" = "$want" ] && return 0
  adb -s "$HU" shell am force-stop $PKG; sleep 0.3
  adb -s "$HU" shell run-as $PKG cat shared_prefs/settings.xml > "$OUT/pre-install-$b.xml"; sleep 0.3
  adb -s "$HU" install -r -d "$1"; sleep 0.3
  adb -s "$HU" shell run-as $PKG cat shared_prefs/settings.xml > "$OUT/post-install-$b.xml"; sleep 0.3
  diff "$OUT/pre-install-$b.xml" "$OUT/post-install-$b.xml" > "$OUT/install-delta-$b.txt"
  [ "$(livemd5)" = "$want" ] || { note "INSTALL_MISMATCH $1"; return 1; }; }
moto_bt() { rl_radio "$PH" bt "$1"; }                                           # moto_bt on|off
hupid() { adb -s "$HU" shell pidof $PKG 2>/dev/null | tr -d '\r'; sleep 0.3; }
# exit only a running app: headunit://exit on a stopped app starts the service and arms the stack
clean_hu() { if [ -n "$(hupid)" ]; then adb -s "$HU" shell am start -a android.intent.action.VIEW -d "headunit://exit" >/dev/null; sleep 3; fi
  adb -s "$HU" shell am force-stop $PKG; sleep 0.3; }
panel() { adb -s "$HU" shell svc power stayon true; sleep 0.3; adb -s "$HU" shell input keyevent KEYCODE_WAKEUP; sleep 0.3
  adb -s "$HU" shell wm dismiss-keyguard; sleep 0.3; adb -s "$PH" shell input keyevent KEYCODE_WAKEUP; sleep 0.3; adb -s "$PH" shell input keyevent KEYCODE_HOME; sleep 0.3; }
# the launcher icon's intent: MAIN plus LAUNCHER, not the USB attach path
launch() { adb -s "$HU" shell am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n $MAIN >/dev/null; sleep 0.3; }
ghn() { adb -s "$PH" shell ps -A | grep -acF "$GH"; sleep 0.3; }
ghdis() { adb -s "$PH" shell pm list packages -d | grep -acF "$GH"; sleep 0.3; }
# gh_down TAG: disable Gearhead and kill it; gh_up TAG: enable it again
gh_down() { local r d p; r=$(adb -s "$PH" shell pm disable-user --user 0 $GH 2>&1 | tr -d '\r' | tr '\n' ' '); sleep 0.3
  adb -s "$PH" shell am force-stop $GH; sleep 3; d=$(ghdis); p=$(ghn); setf "$1_gh_disabled" "$d"; setf "$1_gh_procs" "$p"
  echo -e "$1\tdisable: $r gh_disabled=$d gh_procs=$p" | tee -a "$OUT/summary.tsv"; }
gh_up() { adb -s "$PH" shell pm enable $GH >/dev/null 2>&1; sleep 1; echo -e "$1\tgh_disabled_after=$(ghdis)" | tee -a "$OUT/summary.tsv"; }
# caps TAG: clear both logs and start the two tracked captures $EV/TAG-hu.txt and $EV/TAG-ph.txt
caps() { CAPT=$1; HUF="$OUT/$1-hu.txt"; PHF="$OUT/$1-ph.txt"
  adb -s "$HU" logcat -G 16M; adb -s "$HU" logcat -c; sleep 0.3; rl_cap_start "$1-hu" "$HU"; HUPID=$RL_PID
  adb -s "$PH" logcat -G 16M; adb -s "$PH" logcat -c; sleep 0.3; rl_cap_start "$1-ph" "$PH"; PHPID=$RL_PID; }
# hu_ok, ph_ok: restart a stream that wireless adb dropped, appending to the same file
hu_ok() { kill -0 "$HUPID" 2>/dev/null && return 0; reconn "$HU" || return 1
  rl_bg "cap:$CAPT-hu-r" stdbuf -oL adb -s "$HU" logcat -v threadtime -T 1 >> "$HUF" 2>&1; HUPID=$RL_PID; note "hu stream restarted"; }
ph_ok() { kill -0 "$PHPID" 2>/dev/null && return 0; reconn "$PH" || return 1
  rl_bg "cap:$CAPT-ph-r" stdbuf -oL adb -s "$PH" logcat -v threadtime -T 1 >> "$PHF" 2>&1; PHPID=$RL_PID; note "ph stream restarted"; }
seen() { tail -c +"$(( $2 + 1 ))" "$HUF" 2>/dev/null | grep -aqF -- "$1"; }       # seen STR FROM_BYTE
# upto CAP STR FROM_BYTE: rl_wait_line in 10 s slices, so a stream that wireless adb dropped restarts in between
upto() { local left=$1 n; while [ "$left" -gt 0 ]; do n=$(( left < 10 ? left : 10 ))
    rl_wait_line "$HUF" "$2" "$n" "$3" >/dev/null && return 0; left=$(( left - n )); hu_ok; done; return 1; }
dump() { reconn "$HU"; adb -s "$HU" logcat -d -v threadtime > "$OUT/$1-hu.dump"; sleep 0.3
  reconn "$PH"; adb -s "$PH" logcat -d -v threadtime > "$OUT/$1-ph.dump"; sleep 0.3; }
# hwin FILE A B: head unit lines from marker A to marker B, exact label match; pwin: the same on the phone tag
hwin() { awk -v a="AutomationMarker: $2" -v b="AutomationMarker: $3" '{sub(/\r$/,"")} function at(s){i=index($0,s); return i>0 && substr($0,i+length(s))~/^[[:space:]]*$/} at(a){on=1} on{print} on&&at(b){exit}' "$1" 2>/dev/null; }
pwin() { awk -v a="$2" -v b="$3" '{sub(/\r$/,"")} /OHURIG/ && $NF==a {on=1} on{print} on && /OHURIG/ && $NF==b {exit}' "$1" 2>/dev/null; }
# win A B DUMPTAG: the head unit window from the stream, or from the dump if the stream lost it; pw: the phone's
win() { local w; w=$(hwin "$HUF" "$1" "$2"); if printf '%s\n' "$w" | grep -aqF "AutomationMarker: $2"; then printf '%s\n' "$w"
  else hwin "$OUT/$3-hu.dump" "$1" "$2"; fi; }
pw() { local w; w=$(pwin "$PHF" "$1" "$2"); if [ -n "$w" ]; then printf '%s\n' "$w"; else pwin "$OUT/$3-ph.dump" "$1" "$2"; fi; }
# drop C: the forced end. PORT: D-POCO stops hosting for GAP s. HAND: a cued pull.
drop() { local i; case $METHOD in
  PORT) adb -s "$HU" shell dumpsys usb set-port-roles "$PORTID" sink device >> "$OUT/$1.drop.txt" 2>&1; sleep "$GAP"
        adb -s "$HU" shell dumpsys usb set-port-roles "$PORTID" source host >> "$OUT/$1.drop.txt" 2>&1 ;;
  HAND) cue "PULL-PLUG: pull the D-MOTO cable out of D-POCO, count to 8, plug it back; set USB controlled by to This device if D-MOTO asks"
        rl_step "drop $1: waiting for the pull"; for i in $(seq 1 120); do [ "$(busn)" = 0 ] && break; sleep 1; done; mark "$1-out"
        rl_step "drop $1: waiting for the plug"; for i in $(seq 1 120); do [ "$(busn)" -ge 1 ] && break; sleep 1; done ;;
  esac; }
# recover C: only after a cycle missed SSL. Records REC=GADGET, RELAUNCH, CABLE or NONE in summary.tsv.
recover() { local c=$1 L ok
  mark "$c-rec1"; L=$(rl_offset "$HUF"); adb -s "$PH" shell svc usb setFunctions mtp; sleep 8
  seen "$SSL" "$L" || send ACTION_CHECK_USB > "$OUT/$c-rec1.reply"
  if upto 60 "$SSL" "$L"; then echo -e "$c\tREC=GADGET" >> "$OUT/summary.tsv"; return 0; fi
  mark "$c-rec2"; L=$(rl_offset "$HUF"); moto_bt off >/dev/null; clean_hu; panel; launch
  upto 60 "$SSL" "$L" || { send ACTION_CHECK_USB > "$OUT/$c-rec2.reply"; upto 60 "$SSL" "$L"; }
  ok=$?; moto_bt on >/dev/null
  if [ $ok = 0 ]; then echo -e "$c\tREC=RELAUNCH" >> "$OUT/summary.tsv"; return 0; fi
  mark "$c-rec3"; L=$(rl_offset "$HUF"); cue "H3: unplug D-MOTO from D-POCO, wait 5 s, plug it back, then set USB controlled by to This device on D-MOTO"
  upto 180 'Normal USB device attached:' "$L"; sleep 8
  seen "$SSL" "$L" || send ACTION_CHECK_USB > "$OUT/$c-rec3.reply"
  if upto 90 "$SSL" "$L"; then echo -e "$c\tREC=CABLE" >> "$OUT/summary.tsv"; return 0; fi
  echo -e "$c\tREC=NONE" >> "$OUT/summary.tsv"; return 1; }
# gcyc C: count one cycle, window C-drop to C-end, into cycles.tsv
gcyc() { local c=$1
  win "$c-drop" "$c-end" "$c" > "$OUT/$c.hwin"; pw "$c-drop" "$c-end" "$c" > "$OUT/$c.pwin"
  awk -v c="$c" '
  function has(s) { return index($0, s) > 0 }
  function ms() { split($2, t, ":"); return (t[1]*3600 + t[2]*60 + t[3]) * 1000 }
  { sub(/\r$/, "") }
  NR == 1 { t0 = ms() }
  has("AutomationMarker: " c "-norec") { stop = 1 }
  has("SSL handshake complete") && !has("Handshake: SSL") { nssl++; if (ts == "") ts = ms(); next }
  { pre = (ts == "" && !stop) }
  has("Handshake: Version request send failed") || has("Handshake: SSL performHandshake failed.") || has("Handshake: Version request/response failed after") || has("Handshake failed with exception") || has("Handshake: Version exchange timed out after") { if (pre) hsf++ }
  has("Successfully requested switch to accessory mode") { if (pre) sw++ }
  has("Normal USB device attached:") { if (pre) natt++ }
  has("USB accessory device attached, connecting.") { if (pre) aatt++ }
  has("AapService: wired session ended") { rearm++ }
  has("WifiLauncher: Initializing WiFi Mode: ") { if (pre) { ini++; if (mode == "") { m = $0; sub(/.*Initializing WiFi Mode: /, "", m); mode = m } } }
  has("WifiLauncher: wireless bring-up requested, but WiFi is not one of the chosen") { desel++ }
  has("createGroup SUCCESS") { if (pre) grp++ }
  has("NativeAA: Attempting active poke to device") || has("NativeAA: waking the phone while the WiFi group is still forming") { if (pre) poke++ }
  has("Successfully poked") { if (pre) poked++ }
  has("AapService: a USB projection attempt is in flight") { dfr++ }
  has("ConnectionArbiter: ") { arb++ }
  has("stood the wireless stack down until it ends") { std++ }
  has("WirelessServer: Incoming connection detected") { if (pre) wl++ }
  has("AapService: USB session established while wireless mode") { if (!pre) usb++ }
  has("AapService: USB disconnect. Scheduling reconnect check in") { sched++ }
  has("HomeFragment: Native AA mode active. Skipping manual auto-connect attempt.") { skip++ }
  has("AapService: Stopping GpsLocationService and NightModeManager since connection is disconnected") { if (pre) endpre++; else endpost++ }
  has("AapService: the projection screen never came up") { rf++ }
  has("not reconnecting by itself, the projection screen could not be raised.") { hold++ }
  has("MATCH! Starting AapService") { mw++ }
  END { dt = (ts != "") ? ts - t0 : "NA"
    if (NR == 0) out = "VOID"; else if (ts == "" || dt > 180000) out = "NOREC"
    else if (rf + hold > 0) out = "CONFOUND"; else if (nssl >= 2 || endpost > 0) out = "LOOP"
    else if (wl > 0) out = "REC_WIFI"; else if (usb == 0) out = "CHECK"; else out = "REC_CLEAN"
    printf "%s out=%s t_ssl_ms=%s nssl=%d hsf=%d sw=%d natt=%d aatt=%d rearm=%d ini=%d mode=%s desel=%d grp=%d poke=%d poked=%d ovl=%d dfr=%d arb=%d std=%d wl=%d usb=%d sched=%d skip=%d endpre=%d endpost=%d rf=%d hold=%d mw=%d\n",
      c, out, dt, nssl, hsf, sw, natt, aatt, rearm, ini, (mode == "" ? "-" : mode), desel, grp, poke, poked, (grp + poke > 0), dfr, arb, std, wl, usb, sched, skip, endpre, endpost, rf, hold, mw }' "$OUT/$c.hwin" | tee -a "$OUT/cycles.tsv"
  awk -v c="$c" 'function has(s) { return index($0, s) > 0 } { sub(/\r$/, "") }
  has("GH.WIRELESS") { wl++ } has("Creating rfcomm socket for device:") { rf++ }
  has("entering USB accessory mode") { ai++ } has("exited USB accessory mode") { ao++ }
  has("Critical error") { ce++ } has("ConnLoggerV2") { cl++ }
  END { printf "%s ph.lines=%d ph.wl=%d ph.rf=%d ph.acc_in=%d ph.acc_out=%d ph.crit=%d ph.connlog=%d\n", c, NR, wl, rf, ai, ao, ce, cl }' "$OUT/$c.pwin" | tee -a "$OUT/cycles.tsv"
  grep -aE 'ConnLoggerV2|GH\.WIRELESS' "$OUT/$c.pwin" > "$OUT/$c.phone-lines.txt"; }   # phone
# counted ARM: the counted cycle ids of an arm; nout ARM OUTS: how many of them have out= in the |-list OUTS
counted() { awk -v a="$1-c" 'index($1, a) == 1 && $2 == "count=1" { print $1 }' "$OUT/cycles.tsv" 2>/dev/null; }
nout() { local c n=0; for c in $(counted "$1"); do case "|$2|" in *"|$(kv "$c" out)|"*) n=$((n+1)) ;; esac; done; echo $n; }
# phsum ARM KEY: the sum of a phone field over the arm's counted cycles
phsum() { local c n=0; for c in $(counted "$1"); do n=$(( n + $(num "$(kv "$c" "$2")") )); done; echo $n; }
```

`sx.py` edits a local copy of `settings.xml`; `put` pushes it.

**sx.py**

```python
# sx.py FILE type:key=value ... ; type is int, bool, str, set (comma list) or del. Edits FILE in place.
import re, sys
f = sys.argv[1]; s = open(f).read()
for kv in sys.argv[2:]:
    t, rest = kv.split(':', 1); k, v = (rest.split('=', 1) + [''])[:2]
    s = re.sub(r'<\w+ name="%s"[^>]*/>' % re.escape(k), '', s)
    s = re.sub(r'<string name="%s">[^<]*</string>' % re.escape(k), '', s)
    s = re.sub(r'<set name="%s">.*?</set>' % re.escape(k), '', s, flags=re.S)
    if t == 'set':
        items = ''.join('<string>%s</string>' % i for i in (v.split(',') if v else []))
        el = '<set name="%s">%s</set>' % (k, items) if items else '<set name="%s" />' % k
    elif t in ('int', 'bool', 'str'):
        el = {'int': '<int name="%s" value="%s" />', 'bool': '<boolean name="%s" value="%s" />',
              'str': '<string name="%s">%s</string>'}[t] % (k, v)
    else:
        el = ''
    s = s.replace('</map>', el + '</map>')
open(f, 'w').write(s)
```

`unl_arm_lib.sh` holds the arm drivers.

**unl_arm_lib.sh**

```bash
# up A MODE COMMIT EXPECT [extra keys]: first USB session of arm A, D-MOTO Bluetooth off. EXPECT is NATIVE, HELPER, MANUAL or DESELECT.
up() { local A=$1 M=$2 C=$3 E=$4 L g; shift 4
  clean_hu; put $BASE int:wifi-connection-mode=$M "$@"; rd "$RDK" > "$OUT/$A.keys"
  moto_bt off > "$OUT/$A.ph-bt-off"
  adb -s "$PH" shell svc usb setFunctions mtp; sleep 5; panel
  rl_thermal_gate || return 3
  caps "$A"; mark "$A-start"; L=$(rl_offset "$HUF")
  launch
  case $E in NATIVE|HELPER) upto 15 'WifiLauncher: Initializing WiFi Mode: ' "$L" ;; esac
  upto 60 "$SSL" "$L" || { dlg && cue "H2: allow the USB dialog on D-POCO, never tick Always"; send ACTION_CHECK_USB > "$OUT/$A-check.reply"; upto 60 "$SSL" "$L"; } || { echo "$A GATE no_ssl" | tee -a "$OUT/summary.tsv"; return 1; }
  rl_sleep 30; state > "$OUT/$A.state"; g=ok
  grep -aqF "$C" "$OUT/$A.state" || g="wrong_apk"
  seen 'AapService: USB session established while wireless mode' "$L" || g="$g,not_usb"
  seen 'WirelessServer: Incoming connection detected' "$L" && g="$g,wireless_session"
  seen 'AapService: the projection screen never came up' "$L" && g="$g,no_screen"
  seen 'AapService: raising the projection by' "$L" || g="$g,no_raise"
  case $E in
    NATIVE) seen 'WifiLauncher: Initializing WiFi Mode: NATIVE' "$L" || g="$g,not_native" ;;
    HELPER) seen 'WifiLauncher: Initializing WiFi Mode: HELPER' "$L" || g="$g,not_helper" ;;
    DESELECT) seen 'WifiLauncher: wireless bring-up requested, but WiFi is not one of the chosen' "$L" || g="$g,no_deselect_line"
              seen 'WifiLauncher: Initializing WiFi Mode: ' "$L" && g="$g,armed_anyway" ;;
  esac
  echo "$A GATE $g skip=$(tail -c +"$((L+1))" "$HUF" | grep -acF 'HomeFragment: Native AA mode active. Skipping manual auto-connect attempt.')" | tee -a "$OUT/summary.tsv"
  [ "$g" = ok ]; }
# upr: up, one re-run on a failed gate. Same arguments as up.
upr() { up "$@" && return 0; rl_cap_stop "$1-"; rl_stop thermal; note "$1 gate failed, re-run once"; up "$@" && return 0
  echo "$1 UNTESTABLE gate" | tee -a "$OUT/summary.tsv"; rl_cap_stop "$1-"; rl_stop thermal; return 1; }
# cyc C: one drop and reconnect cycle; hold 180 s after the reconnect, then count it
cyc() { local c=$1 L
  hu_ok; ph_ok
  mark "$c-drop"; L=$(rl_offset "$HUF"); drop "$c"; mark "$c-dropped"
  if ! upto 170 "$SSL" "$L"; then mark "$c-norec"; recover "$c" || echo "$c ARM_STOP" | tee -a "$OUT/summary.tsv"; fi
  dlg && { cue "H2: allow the USB dialog on D-POCO, never tick Always"; echo "$c DIALOG" | tee -a "$OUT/summary.tsv"; }
  rl_sleep 90; rl_sleep 90; mark "$c-end"; dump "$c"; gcyc "$c"; }
# cycles A N: up to N+2 attempts until N cycles count (not VOID, not CONFOUND, no DIALOG)
cycles() { local A=$1 N=$2 n=0 t=0 k ok
  while [ $n -lt "$N" ] && [ $t -lt $((N+2)) ]; do t=$((t+1)); k="$A-c$t"; cyc "$k"
    if grep -aq "^$k ARM_STOP" "$OUT/summary.tsv"; then echo "$k count=0" >> "$OUT/cycles.tsv"; break; fi
    ok=1; grep -aE "^$k out=(VOID|CONFOUND)" "$OUT/cycles.tsv" >/dev/null && ok=0
    grep -aq "^$k DIALOG" "$OUT/summary.tsv" 2>/dev/null && ok=0
    echo "$k count=$ok" >> "$OUT/cycles.tsv"; n=$((n+ok)); done
  echo "$A cycles counted=$n attempts=$t" | tee -a "$OUT/cycles.tsv"; }
# soak A: 1500 s with no action; stops at the 3rd session end or battery under 20%
soak() { local A=$1 L i n=0 b
  mark "$A-soak-start"; L=$(rl_offset "$HUF")
  for i in $(seq 1 300); do sleep 5
    [ $((i % 6)) = 0 ] && { hu_ok; ph_ok; }
    n=$(tail -c +"$((L+1))" "$HUF" | grep -acF -- "$SEND"); [ "$n" -ge 3 ] && break
    [ $((i % 12)) = 0 ] && rl_step "soak $A t=$((i*5))s ends=$n"
    if [ $((i % 60)) = 0 ]; then b=$(batt); echo "$A soak t=$((i*5))s battery=$b ends=$n" | tee -a "$OUT/summary.tsv"; [ "$b" -lt 20 ] && break; fi
  done
  mark "$A-soak-end"; dump "$A-soak"
  hwin "$HUF" "$A-start" "$A-soak-start" | awk -v a="$A" 'function has(s) { return index($0, s) > 0 } { sub(/\r$/, "") }
    has("NativeAA: Attempting active poke to device") || has("NativeAA: waking the phone while the WiFi group is still forming") { p++ }
    has("Successfully poked") { ok++ } has("NativeAA: hands-free service level connection established") { slc++ }
    END { printf "%s soak-pre poke=%d poked=%d slc=%d\n", a, p, ok, slc }' | tee -a "$OUT/cycles.tsv"
  win "$A-soak-start" "$A-soak-end" "$A-soak" | awk -v a="$A" '
    function has(s) { return index($0, s) > 0 } function ms() { split($2, t, ":"); return (t[1]*3600 + t[2]*60 + t[3]) * 1000 }
    { sub(/\r$/, "") } NR == 1 { t0 = ms() } { tl = ms() }
    has("AapService: Stopping GpsLocationService and NightModeManager since connection is disconnected") { e++; at = at sprintf("%.1f,", (ms() - t0) / 60000) }
    has("!!! RECEIVED BYEBYE REQUEST FROM PHONE !!!") { bb++ }
    has("SSL handshake complete") && !has("Handshake: SSL") { s++ }
    has("AapRead: Connection lost. Stopping read loop.") { cl++ } has("USB Accessory detached.") { ad++ }
    has("AapService: wired session ended") { rr++ }
    has("createGroup SUCCESS") { g++; if (!e) gin++ }
    has("NativeAA: Attempting active poke to device") || has("NativeAA: waking the phone while the WiFi group is still forming") { p++; if (!e) pin++ }
    has("NativeAA: Connection accepted from") { acc++; if (!e) accin++ }
    has("MATCH! Starting AapService") { mw++ }
    END { printf "%s soak lines=%d dur_s=%d ends=%d end_min=%s byebye=%d ssl=%d connlost=%d accdet=%d rearm=%d grp=%d poke=%d accepted=%d native_in_session=%d mw=%d\n", a, NR, (tl - t0) / 1000, e, (at == "" ? "-" : at), bb, s, cl, ad, rr, g, p, acc, gin + pin + accin, mw }' | tee -a "$OUT/cycles.tsv"
  pw "$A-soak-start" "$A-soak-end" "$A-soak" | awk -v a="$A" 'function has(s) { return index($0, s) > 0 }
    has("exited USB accessory mode") { ao++ } has("Critical error") { ce++ } has("GH.WIRELESS") { wl++ } has("ConnLoggerV2") { cl++ }
    END { printf "%s soak ph.lines=%d ph.acc_out=%d ph.crit=%d ph.wl=%d ph.connlog=%d\n", a, NR, ao, ce, wl, cl }' | tee -a "$OUT/cycles.tsv"; }
# endarm A: close one arm
endarm() { rd 'video-profile-starvation-cap' >> "$OUT/$1.keys"; mark "$1-end"; rl_cap_stop "$1-"; rl_stop thermal; clean_hu; moto_bt off >/dev/null; }
bgate() { local b; b=$(batt); echo "$1 battery_start=$b" | tee -a "$OUT/summary.tsv"; [ "$b" -ge 40 ] || { echo "$1 UNTESTABLE battery=$b" | tee -a "$OUT/summary.tsv"; return 2; }; }
# hold C: R4-G. A live USB session; Android Auto disabled on D-MOTO; 75 s of failed USB tries; then restore.
hold() { local c=$1 L i s
  hu_ok; ph_ok; moto_bt on >/dev/null
  mark "$c-start"; L=$(rl_offset "$HUF"); gh_down "$c"
  [ "$(ghdis)" -ge 1 ] || { echo -e "$c\tUNTESTABLE gearhead not disabled" | tee -a "$OUT/summary.tsv"; gh_up "$c"; return 1; }
  upto 30 "$SEND" "$L" || echo -e "$c\tno-session-end-in-30s" | tee -a "$OUT/summary.tsv"
  mark "$c-hold"; s=""
  for i in $(seq 1 15); do s="$s$(busn),"; echo -e "$c\tt=$((i*5))s gh_procs=$(ghn)" >> "$OUT/$c.ghprocs"
    dlg && { cue "H2: allow the USB dialog on D-POCO, never tick Always"; echo "$c DIALOG t=$((i*5))s" | tee -a "$OUT/summary.tsv"; }; sleep 3; done
  setf "${c}_bus" "$s"; echo -e "$c\tbus_samples=$s" | tee -a "$OUT/summary.tsv"
  mark "$c-holdend"; dump "$c-hold"
  gh_up "$c"; mark "$c-restore"; L=$(rl_offset "$HUF")
  adb -s "$PH" shell svc usb setFunctions mtp
  upto 120 "$SSL" "$L"
  dlg && { cue "H2: allow the USB dialog on D-POCO, never tick Always"; echo "$c DIALOG" | tee -a "$OUT/summary.tsv"; }
  rl_sleep 30; mark "$c-end"; dump "$c"
  win "$c-start" "$c-holdend" "$c-hold" | awk -v c="$c" '
    function has(s) { return index($0, s) > 0 } function ms() { split($2, t, ":"); return (t[1]*3600 + t[2]*60 + t[3]) * 1000 }
    { sub(/\r$/, "") }
    has("AapService: Stopping GpsLocationService and NightModeManager since connection is disconnected") { e++; if (te == "") te = ms() }
    has("Handshake: Version request send failed") || has("Handshake: SSL performHandshake failed.") || has("Handshake: Version request/response failed after") || has("Handshake failed with exception") || has("Handshake: Version exchange timed out after") { f++; if (tf == "") tf = ms(); tl = ms() }
    has("Found device already in accessory mode: ") { acc++ } has("UsbLauncher: stale accessory") { st++ }
    has("recovery used both steps; a replug is needed") { gu++ }
    has("UsbLauncher: targeted recovery found") { tr++ } has("UsbLauncher: targeted recovery ended without one identifiable accessory") { tre++ }
    has("AapService: USB disconnect. Scheduling reconnect check in") { sch++ }
    has("AapService: a USB projection attempt is in flight") { dfr++ } has("AapService: the USB attempt did not settle within") { dexp++ }
    has("wireless bring-up refused while") { ref++ } has("stood the wireless stack down until it ends") { std++ }
    has("USB has tried for") { sp++; if (tsp == "") tsp = ms() } has("ended with no session; giving back") && has("wireless") { gb++; if (tgb == "") tgb = ms() }
    has("WifiLauncher: Initializing WiFi Mode: NATIVE") { if (tf != "") { ini++; if (ti == "") ti = ms() } }
    has("createGroup SUCCESS") { if (tf != "") g++ }
    has("NativeAA: Attempting active poke to device") || has("NativeAA: waking the phone while the WiFi group is still forming") { if (tf != "") { p++; if (tp == "") tp = ms() } }
    has("Successfully poked") { if (tf != "") ok++ }
    has("SSL handshake complete") && !has("Handshake: SSL") { if (te != "") ssl++ }
    function d(x) { return (x == "" || tf == "") ? "NA" : x - tf }
    END { printf "%s hold ends=%d fails=%d fail_span_ms=%s acc=%d stale=%d giveup=%d targeted=%d targeted_none=%d sched=%d dfr=%d dexp=%d refused=%d stood=%d spend=%d spend_ms=%s giveback=%d giveback_ms=%s ini=%d ini_ms=%s grp=%d poke=%d poke_ms=%s poked=%d ssl_during=%d\n",
      c, e, f, (tf == "" ? "NA" : tl - tf), acc, st, gu, tr, tre, sch, dfr, dexp, ref, std, sp, d(tsp), gb, d(tgb), ini, d(ti), g, p, d(tp), ok, ssl }' | tee -a "$OUT/cycles.tsv"
  pw "$c-hold" "$c-holdend" "$c-hold" | awk -v c="$c" 'function has(s) { return index($0, s) > 0 }
    has("GH.") { gh++ } has("exited USB accessory mode") { ao++ } has("entering USB accessory mode") { ai++ }
    END { printf "%s hold ph.lines=%d ph.gh=%d ph.acc_in=%d ph.acc_out=%d\n", c, NR, gh, ai, ao }' | tee -a "$OUT/cycles.tsv"
  win "$c-restore" "$c-end" "$c" | awk -v c="$c" 'function has(s) { return index($0, s) > 0 }
    has("SSL handshake complete") && !has("Handshake: SSL") { s++ }
    has("AapService: USB session established while wireless mode") { u++ } has("WirelessServer: Incoming connection detected") { w++ }
    has("NativeAA: Attempting active poke to device") || has("NativeAA: waking the phone while the WiFi group is still forming") { p++ }
    END { printf "%s restore ssl=%d usb=%d wifi=%d poke=%d\n", c, s, u, w, p }' | tee -a "$OUT/cycles.tsv"
  pw "$c-restore" "$c-end" "$c" | awk -v c="$c" 'function has(s) { return index($0, s) > 0 }
    has("GH.WIRELESS") { wl++ } has("Creating rfcomm socket for device:") { rf++ }
    END { printf "%s restore ph.lines=%d ph.wl=%d ph.rf=%d\n", c, NR, wl, rf }' | tee -a "$OUT/cycles.tsv"
  seen "$SSL" "$L" || { recover "$c" || echo "$c ARM_STOP" | tee -a "$OUT/summary.tsv"; }; }
# kl T: one icon launch with the phone wedged; count it into cycles.tsv
kl() { local t=$1 i alive=1 ex=NA
  mark "$t"; launch
  for i in $(seq 1 45); do sleep 1; [ "$i" -ge 3 ] && [ -z "$(hupid)" ] && { alive=0; ex=$i; break; }; done
  dlg && { cue "H2: allow the USB dialog on D-POCO, never tick Always"; echo "$t DIALOG" | tee -a "$OUT/summary.tsv"; }
  [ $alive = 1 ] && sleep $((45 - i)); [ $alive = 1 ] && [ -z "$(hupid)" ] && alive=0
  adb -s "$HU" shell dumpsys activity activities | grep -a -m1 mResumedActivity > "$OUT/$t.resumed"; sleep 0.3
  mark "$t-end"
  win "$t" "$t-end" "$CAPT" | awk -v t="$t" -v alive=$alive -v ex=$ex -v res="$(grep -acF MainActivity "$OUT/$t.resumed")" '
    function has(s) { return index($0, s) > 0 } { sub(/\r$/, "") }
    has("App launched") { la++ } has("AapService creating...") { cr++ } has("Found device already in accessory mode: ") { acc++ }
    has("Handshake: Version request send failed") || has("Handshake: SSL performHandshake failed.") || has("Handshake: Version request/response failed after") || has("Handshake failed with exception") || has("Handshake: Version exchange timed out after") { f++ }
    has("MainActivity: Received finish request. Closing.") { fin++ } has("AapService destroying...") { de++ }
    has("AapService: killProcessOnDestroy is true. Triggering System.exit(0).") { k++ }
    has("HomeFragment: onResume. isConnected=") { hr++ } has("UsbLauncher: stale accessory") { st++ }
    END { cls = (k > 0 && alive == 0) ? "EXIT" : (alive == 1 && k == 0) ? "STAYS" : "OTHER"
      printf "%s class=%s alive45=%d exit_s=%s launched=%d created=%d acc=%d fails=%d finish=%d destroy=%d kill=%d home=%d stale=%d resumed_main=%d\n", t, cls, alive, ex, la, cr, acc, f, fin, de, k, hr, st, res }' | tee -a "$OUT/cycles.tsv"; }
# wedge_line A B TAG: the phone's accessory and Gearhead counts between two markers
wedge_line() { pw "$1" "$2" "$3" | awk -v t="$3" 'function has(s) { return index($0, s) > 0 }
  has("exited USB accessory mode") { ao++ } has("entering USB accessory mode") { ai++ } has("GH.") { gh++ }
  END { printf "%s wedge ph.lines=%d ph.acc_out=%d ph.acc_in=%d ph.gh=%d\n", t, NR, ao, ai, gh }' | tee -a "$OUT/cycles.tsv"; }
```

**Verbs and commands used, and nothing else acts on the app:**

| Action | Command |
|---|---|
| icon launch | `launch` (`am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n $MAIN`) |
| the USB button, as a fallback only | `send ACTION_CHECK_USB` |
| build identity | `send ACTION_QUERY_STATE` (`state`) |
| marker | `mark <label>` (both units; no spaces in labels) |
| stop the service cleanly | `headunit://exit`, then `am force-stop` (`clean_hu`) |
| the drop | `drop` (`PORT` or `HAND`, chosen in P3) |
| Android Auto down and up on D-MOTO | `gh_down`, `gh_up` (`pm disable-user`, `am force-stop`, `pm enable`) |

Every verb prints `AutomationReceiver: ` in the head unit capture. A step with no such line never
landed: that step is void, not a FAIL. The drop, the radios, Gearhead and `launch` are system commands
and print no `AutomationReceiver:` line; `launch` prints `App launched` instead.

## 6. The deciding lines

Every app string below matched `git grep -F` in `app/src/main` at `f9a72d480` and at `v.3.4.0`, except
the ones marked **main only** (rechecked 2026-10-29). Every one is `AppLog.i`, `AppLog.w` or `AppLog.e`
(several also have an `AppLog.d` twin, which INFO drops), so `log-level` 2 carries them. Several are
prefixes of longer source lines; the prefix is enough. Two source lines carry an em dash after the
part quoted here (`wired session ended`, `USB session established while wireless mode`), so grep the
prefix only. `WifiLauncher: Initializing WiFi Mode: NATIVE` and `... HELPER` are that prefix plus the
mode the source prints (`${newLauncher.mode}`).

| Line (grep -F) | Source | Used for |
|---|---|---|
| `AutomationReceiver: ` | `AutomationReceiver` | a verb landed |
| `AutomationMarker: ` | `AutomationEffectRunner` (WARN) | markers |
| `SSL handshake complete` | `AapSslContext` (INFO) | a session formed; `gcyc` drops the `Handshake: SSL` DEBUG twin |
| `AapService: Stopping GpsLocationService and NightModeManager since connection is disconnected` | `AapService.onDisconnected` | a session end or a failed attempt |
| `AapService: wired session ended` | `rearmWirelessAfterWiredSession` | the re-arm after a USB session |
| `AapService: USB session established while wireless mode` | `quiesceWirelessForWiredSession` | the session is USB |
| `WifiLauncher: Initializing WiFi Mode: ` | `WifiLauncherManager` | a launcher started; the mode follows |
| `WifiLauncher: wireless bring-up requested, but WiFi is not one of the chosen` | `WifiLauncherManager.setActive` | R3: the deselect took effect |
| `createGroup SUCCESS` | `WifiDirectManager` | a P2P group came up |
| `NativeAA: Attempting active poke to device` | `NativeAaHandshakeManager` | a wake poke |
| `NativeAA: waking the phone while the WiFi group is still forming` | `triggerEarlyWake` | the early wake |
| `Successfully poked` | `NativeAaHandshakeManager` | the poke's hands-free connect succeeded |
| `NativeAA: Connection accepted from` | `NativeAaHandshakeManager` | the phone came back over Bluetooth |
| `NativeAA: hands-free service level connection established` | `NativeAaHandshakeManager` (WARN) | H3 predictor |
| `!!! RECEIVED BYEBYE REQUEST FROM PHONE !!!` | `AapControl` | H3: the phone ended the session |
| `WirelessServer: Incoming connection detected` | `WirelessServer` | a session over WiFi |
| `AapService: a USB projection attempt is in flight` | `deferWirelessForUsbHandoff` | the deferral held the bring-up |
| `AapService: the USB attempt did not settle within` | `deferWirelessForUsbHandoff` | the deferral ran out (8 s) |
| `Successfully requested switch to accessory mode` | `UsbLauncherManager` | an AOA switch |
| `Normal USB device attached:` | `UsbLauncherListener` | the phone came back under its own identity |
| `USB accessory device attached, connecting.` | `UsbLauncherListener` | the phone came back in accessory mode |
| `Found device already in accessory mode: ` | `UsbLauncherManager.checkAlreadyConnected` | K1: a launch found the wedged phone |
| `AapService: USB disconnect. Scheduling reconnect check in` | `scheduleReconnectIfNeeded` | the service's 3 s USB re-check |
| `HomeFragment: Native AA mode active. Skipping manual auto-connect attempt.` | `HomeFragment.attemptAutoConnect` | H2: the skip |
| `HomeFragment: onResume. isConnected=` | `HomeFragment` | the home screen came up |
| `AapService: raising the projection by` | `AapService` | the projection was raised |
| `AapService: the projection screen never came up` | `AapService` (ERROR) | `CONFOUND` |
| `MATCH! Starting AapService` | `AutoStartReceiver` | a self-wake; counted |
| `Handshake: Version request send failed` | `AapTransport` (WARN) | a failed handshake (one of five) |
| `Handshake: SSL performHandshake failed.` | `AapTransport` | as above |
| `Handshake: Version request/response failed after` | `AapTransport` | as above |
| `Handshake failed with exception` | `AapTransport` | as above |
| `Handshake: Version exchange timed out after` | `AapTransport` | as above |
| `AapRead: Connection lost. Stopping read loop.` | `AapReadMultipleMessages` | a lost link |
| `USB Accessory detached.` | `UsbLauncherListener` | an accessory detach |
| `App launched` | `MainActivity.logLaunchSource` | a launch reached `onCreate` |
| `AapService creating...` | `AapService.onCreate` | a service start |
| `AapService destroying...` | `AapService.onDestroy` | a service stop |
| `MainActivity: Received finish request. Closing.` | `MainActivity.finishReceiver` | K1: `ACTION_FINISH_ACTIVITIES` arrived |
| `AapService: killProcessOnDestroy is true. Triggering System.exit(0).` | `AapService.onDestroy` | K1: the process exit |
| `ConnectionArbiter: ` | `ConnectionArbiter` | **main only**: any arbiter decision |
| `stood the wireless stack down until it ends` | `ConnectionArbiter.claim` | **main only** |
| `wireless bring-up refused while` | `ConnectionArbiter.refusesBackground` | **main only** |
| `USB has tried for` | `ConnectionArbiter.spendUsbEpisode` | **main only**: the 60 s budget ran out |
| `ended with no session; giving back` | `ConnectionArbiter.settle` | **main only**: the 8 s quiet path |
| `UsbLauncher: stale accessory` | `UsbLauncherManager.onUsbHandshakeEnded` | **main only**: the recovery ladder |
| `recovery used both steps; a replug is needed` | `UsbLauncherManager.onUsbHandshakeEnded` | **main only**: the ladder gave up |
| `UsbLauncher: targeted recovery found` | `UsbLauncherManager.reconnectRecoveryTarget` | **main only** (new in `f9a72d480`): the reconnect after a reset |
| `UsbLauncher: targeted recovery ended without one identifiable accessory` | `UsbLauncherManager.reconnectRecoveryTarget` | **main only** (new in `f9a72d480`) |
| `not reconnecting by itself, the projection screen could not be raised.` | `AapService` | **main only**: `CONFOUND` |

On a 3.4.0 capture a **main only** count of 0 is the expected reading.

**Not in our tree** (phone and framework lines): `GH.WIRELESS`, `GH.`, `Creating rfcomm socket for
device:`, `ConnLoggerV2`, `Critical error`, `entering USB accessory mode`, `exited USB accessory mode`,
the marker tag `OHURIG`, and for K2 `FATAL EXCEPTION`, `ANR in` and `mResumedActivity`. The Gearhead
and accessory strings are quoted from earlier D-MOTO captures (`archive/rounds/usb-reattach-round1-brief.md`
and its results). No Android Auto Exit happens in this round, so no `Critical error` line needs an
exclusion; `ph.crit` is reported only.

## 7. Runs

### P. Prepare (cabled, then the OTG plug)

Before P the session has read the block: radios, the D-MOTO to D-POCO bond on both sides, batteries, and
the operator's yes. P reads the rest while both phones are on their PC cables, moves them to wireless
adb, asks for the OTG plug, then backs up D-POCO's settings and installs 3.4.0.

**p.sh**

```bash
rl_hold_off "$DHU" bt
adb -s "$DHU" shell am force-stop $PKG
rl_step "P: cabled reads"
setf sdk "$(adb -s "$D_POCO" shell getprop ro.build.version.sdk | tr -d '\r')"
for a in v340 main; do
  setf "md5_$a" "$(md5sum < "$OUT/apk/$a.apk" | cut -c1-32)"
  setf "arb_$a" "$(unzip -p "$OUT/apk/$a.apk" 'classes*.dex' 2>/dev/null | grep -ac ConnectionArbiter)"
  setf "urt_$a" "$(unzip -p "$OUT/apk/$a.apk" 'classes*.dex' 2>/dev/null | grep -ac UsbRecoveryTarget)"
done
MOTO_BT=$(adb -s "$D_MOTO" shell settings get secure bluetooth_address | tr -d '\r')
adb -s "$D_MOTO" shell dumpsys package $GH | grep -a -m1 versionName > "$OUT/gearhead.txt"
setf gh_disabled_start "$(adb -s "$D_MOTO" shell pm list packages -d | grep -acF "$GH")"
setf home_ours "$(adb -s "$D_POCO" shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | grep -acF "$PKG")"
for k in screen_brightness screen_brightness_mode; do echo "$k=$(adb -s "$D_POCO" shell settings get system $k | tr -d '\r')"; done > "$OUT/poco-screen.txt"
POCO_IP=$(adb -s "$D_POCO" shell ip -4 addr show wlan0 | grep -aoE 'inet [0-9.]+' | cut -d' ' -f2)
MOTO_IP=$(adb -s "$D_MOTO" shell ip -4 addr show wlan0 | grep -aoE 'inet [0-9.]+' | cut -d' ' -f2)
printf 'MOTO_BT=%s\nPOCO_IP=%s\nMOTO_IP=%s\n' "$MOTO_BT" "$POCO_IP" "$MOTO_IP" > "$OUT/env.txt"
setf moto_bt_set "$( [ -n "$MOTO_BT" ] && [ "$MOTO_BT" != null ] && echo 1 || echo 0)"
setf ips_set "$( [ -n "$POCO_IP" ] && [ -n "$MOTO_IP" ] && echo 1 || echo 0)"
adb -s "$D_POCO" tcpip 5555; sleep 3; adb -s "$D_MOTO" tcpip 5555; sleep 3
HU=$POCO_IP:5555; PH=$MOTO_IP:5555
printf 'D-POCO %s\nD-MOTO %s\n' "$HU" "$PH" >> "$(dirname "$EV")/unit-serials.txt"   # rig_requires.py reads it
reconn "$HU"; reconn "$PH"
cue "P-OTG: unplug D-POCO and D-MOTO from the PC, plug D-MOTO into the USB-C port of D-POCO, on D-MOTO set USB controlled by to This device, unlock D-MOTO if it shows a PIN"
ok=0
for i in $(seq 1 120); do
  if [ "$(busn)" -ge 1 ] && adb -s "$PH" get-state 2>/dev/null | grep -q device; then ok=1; break; fi
  if [ $((i % 6)) = 0 ]; then rl_step "P: waiting for the OTG plug, $((i * 5))s"; adb connect "$HU" >/dev/null 2>&1; adb connect "$PH" >/dev/null 2>&1; fi
  sleep 5
done
setf otg_ok "$ok"
reconn "$HU" && reconn "$PH"; setf adb_rc $?
setf busn "$(busn)"
adb -s "$PH" shell svc usb getFunctions > "$OUT/moto-usb-functions.txt"
adb -s "$HU" shell dumpsys usb | grep -a -i -A4 headunitrevived > "$OUT/usb-grants-before.txt"
adb -s "$HU" shell am force-stop $PKG; sleep 0.3
[ -s "$OUT/backup-poco.xml" ] || adb -s "$HU" shell run-as $PKG cat shared_prefs/settings.xml > "$OUT/backup-poco.xml"
grep -aoE '(use-aac-audio|audio-latency-multiplier|audio-queue-capacity|enable-audio-sink)[^/]*' "$OUT/backup-poco.xml" > "$OUT/audio-keys.txt"
livemd5 > "$OUT/poco-apk-before.txt"
adb -s "$HU" shell settings put system screen_brightness_mode 0; adb -s "$HU" shell settings put system screen_brightness 10
inst "$OUT/apk/v340.apk"
setf live_is_v340 "$( [ "$(livemd5)" = "$(fact md5_v340)" ] && echo 1 || echo 0)"
```

**grade-p.sh**

```bash
rl_cond 1 ge "$(num "$(fact sdk)")" 35 'D-POCO API level (35 or more, so no stand-down can cut its wireless adb)'
rl_cond 2 eq "$( [ -n "$(fact md5_v340)" ] && [ "$(fact md5_v340)" != "$(fact md5_main)" ] && echo 1 || echo 0)" 1 'the two APKs differ'
rl_cond 3 eq "$(num "$(fact arb_v340)" 9)" 0 'ConnectionArbiter in the v340 DEX'
rl_cond 4 eq "$(num "$(fact urt_v340)" 9)" 0 'UsbRecoveryTarget in the v340 DEX'
rl_cond 5 ge "$(num "$(fact arb_main)")" 1 'ConnectionArbiter in the main DEX'
rl_cond 6 ge "$(num "$(fact urt_main)")" 1 'UsbRecoveryTarget in the main DEX'
rl_cond 7 eq "$(num "$(fact moto_bt_set)")" 1 'D-MOTO Bluetooth address read'
rl_cond 8 eq "$(num "$(fact ips_set)")" 1 'both WiFi addresses read'
rl_cond 9 eq "$(num "$(fact gh_disabled_start)" 9)" 0 'Gearhead disabled on D-MOTO at the start'
rl_cond 10 eq "$(num "$(fact home_ours)" 9)" 0 'our app is the D-POCO home app (that turns kill-on-disconnect off)'
rl_cond 11 eq "$(num "$(fact otg_ok)")" 1 'D-MOTO on the D-POCO bus and on wireless adb'
rl_cond 12 eq "$(num "$(fact adb_rc)" 9)" 0 'wireless adb to both phones'
rl_cond 13 ge "$(num "$(fact busn)")" 1 'USB devices on the D-POCO bus'
rl_cond 14 eq "$(num "$(fact live_is_v340)")" 1 'v340 installed on D-POCO'
```

**PASS** when every condition holds. **A failure in P stops the round** (the stop rule), and X still
runs. The install delta in `install-delta-v340.apk.txt` does not stop it (`put` reseeds from the
backup), but quote it in Setup notes, with `gearhead.txt`, `audio-keys.txt` and both md5 values.

### P3. Which drop

Mode 0 (MANUAL), 3.4.0, Bluetooth off on D-MOTO, one capture `P3`. No wireless stack runs. Two trials of
the port-role lever. The phone must leave the bus, come back under its own identity and form a session
again. The script decides `METHOD` and appends it to `$EV/env.txt`.

**p3.sh**

```bash
rl_hold_off "$DHU" bt
adb -s "$HU" shell dumpsys usb -h > "$OUT/poco-usb-help.txt" 2>&1; adb -s "$HU" shell dumpsys usb ports > "$OUT/poco-ports.txt" 2>&1
PORTID=$(grep -aoE 'id=[A-Za-z0-9_.-]+' "$OUT/poco-ports.txt" | head -1 | cut -d= -f2); GAP=8; H3USED=0
inst "$OUT/apk/v340.apk"
upr P3 0 $V340 MANUAL || echo "P3 GATE FAILED" | tee -a "$OUT/summary.tsv"
p3t() { local c=P3-PORT-$1 L a back i r
  mark "$c-drop"; L=$(rl_offset "$HUF")
  adb -s "$HU" shell dumpsys usb set-port-roles "$PORTID" sink device > "$OUT/$c.off.txt" 2>&1; sleep 3; a=$(busn); sleep $((GAP-3))
  adb -s "$HU" shell dumpsys usb set-port-roles "$PORTID" source host > "$OUT/$c.on.txt" 2>&1
  mark "$c-dropped"; back=NA; for i in $(seq 1 20); do [ "$(busn)" -ge 1 ] && { back=$i; break; }; sleep 0.7; done
  upto 90 "$SSL" "$L"; r=$?
  if [ "$back" = NA ]; then adb -s "$HU" shell dumpsys usb set-port-roles "$PORTID" source host >> "$OUT/$c.on.txt" 2>&1; sleep 10
    [ "$(busn)" -ge 1 ] || { adb -s "$PH" shell svc usb setFunctions mtp; sleep 10; }
    if [ "$(busn)" -lt 1 ]; then H3USED=1; cue "H3: unplug D-MOTO from D-POCO, wait 5 s, plug it back, then set USB controlled by to This device on D-MOTO"
      rl_step "P3: waiting for the replug"; for i in $(seq 1 180); do [ "$(busn)" -ge 1 ] && break; sleep 1; done; fi; fi
  [ $r = 0 ] || { seen "$SSL" "$L" || send ACTION_CHECK_USB > "$OUT/$c.check.reply"; upto 60 "$SSL" "$L" || adb -s "$PH" shell svc usb setFunctions mtp; upto 60 "$SSL" "$L"; }
  adb -s "$HU" shell dumpsys usb ports > "$OUT/$c.ports-after.txt" 2>&1
  rl_sleep 20; mark "$c-end"; dump "$c"; gcyc "$c"
  echo "$c bus_during=$a back_passes=$back ssl_rc=$r" | tee -a "$OUT/cycles.tsv"; }
port_ok() { local c=P3-PORT-$1
  [ "$(kv "$c" bus_during)" = 0 ] && [ "$(kv "$c" back_passes)" != NA ] && [ -n "$(kv "$c" back_passes)" ] &&
  [ "$(kv "$c" out)" = REC_CLEAN ] && [ "$(num "$(kv "$c" natt)")" -ge 1 ] && [ "$(num "$(kv "$c" t_ssl_ms)" 999999)" -lt 98000 ]; }
METHOD=HAND
if [ -n "$PORTID" ] && grep -aqF set-port-roles "$OUT/poco-usb-help.txt"; then
  p3t 1; p3t 2
  port_ok 1 && port_ok 2 && [ "$H3USED" = 0 ] && METHOD=PORT
fi
printf 'PORTID=%s\nGAP=%s\nMETHOD=%s\n' "$PORTID" "$GAP" "$METHOD" >> "$OUT/env.txt"; setf method "$METHOD"; setf p3_h3 "$H3USED"
[ "$METHOD" = HAND ] && cue "P3 chose HAND: R1, R2, R3, R5 and R4-H need 9 cable pulls in all, each when an OPERATOR line says PULL-PLUG"
endarm P3
```

**grade-p3.sh**

```bash
for n in 1 2; do c=P3-PORT-$n
  rl_cond "${n}a" eq "$(num "$(kv "$c" bus_during)" 9)" 0 "$c: USB devices on the bus during the gap"
  rl_cond "${n}b" eq "$( [ -n "$(kv "$c" back_passes)" ] && [ "$(kv "$c" back_passes)" != NA ] && echo 1 || echo 0)" 1 "$c: the phone came back on the bus"
  rl_cond "${n}c" eq "$( [ "$(kv "$c" out)" = REC_CLEAN ] && echo 1 || echo 0)" 1 "$c: out=REC_CLEAN"
  rl_cond "${n}d" ge "$(num "$(kv "$c" natt)")" 1 "$c: the phone came back under its own identity (natt)"
  rl_cond "${n}e" lt "$(num "$(kv "$c" t_ssl_ms)" 999999)" 98000 "$c: ms from the drop to SSL"
done
rl_cond 3 eq "$(num "$(fact p3_h3)" 9)" 0 'a PORT trial needed the H3 replug'
rl_cond 4 eq "$( [ "$(fact method)" = PORT ] && echo 1 || echo 0)" 1 'METHOD is PORT'
```

**PASS** when `METHOD` is `PORT` (both trials clean, no H3). **FAIL** when it is `HAND`: the round still
runs, with cued pulls. No stop rule follows P3. Add a first PORT measurement to
`rig-quirks/units/D-POCO.md` in the results commit.

### R1. Arm A on 3.4.0: the soak, then R2-N (forced ends, Native)

**r1.sh**

```bash
rl_hold_off "$DHU" bt
inst "$OUT/apk/v340.apk"
bgate A && upr A 3 $V340 NATIVE && { moto_bt on > "$OUT/A.ph-bt-on"; soak A; cycles A 2; endarm A; }
```

R1 reads the `A soak` lines. Report `dur_s`, `ends`, `end_min`, `byebye`, `connlost`, `accdet`,
`rearm`, `grp`, `poke`, `accepted`, `native_in_session`, `mw`, the `soak-pre` line, and the phone's
`ph.acc_out`, `ph.crit` and `ph.wl`. The two `A-c<n>` cycles are R2-N, graded in R2.

- **PASS (H3 or H5 seen on the rig):** `ends` 1 or more, and either `byebye` 1 or more with `soak-pre`
  `poke` or `slc` 1 or more (H3), or `native_in_session` 1 or more (H5).
- **FAIL (H3 and H5 not reproduced on this rig, the expected result):** `ends` 0 over `dur_s` 1200 or
  more.
- **INCONCLUSIVE:** `ends` 1 or more with neither predictor, or `dur_s` under 1200.
- **UNTESTABLE:** the battery gate or the bring-up gate failed (`A UNTESTABLE` in `summary.tsv`).

**grade-r1.sh**

```bash
dur=$(num "$(kv 'A soak' dur_s)"); ends=$(num "$(kv 'A soak' ends)"); bb=$(num "$(kv 'A soak' byebye)")
pre=$(( $(num "$(kv 'A soak-pre' poke)") + $(num "$(kv 'A soak-pre' slc)") )); nis=$(num "$(kv 'A soak' native_in_session)")
pred=0; { [ "$bb" -ge 1 ] && [ "$pre" -ge 1 ]; } && pred=1; [ "$nis" -ge 1 ] && pred=1
[ "$(num "$(kv 'A soak' ph.lines)")" -gt 0 ] || note "R1: the phone capture has no lines in the soak window"
rl_cond 1 ge "$dur" 1200 'soak length in s'
rl_cond 2 ge "$ends" 1 'session ends in the soak (0 is the expected FAIL)'
rl_cond 3 eq "$pred" 1 'an H3 or H5 predictor beside the end'
if [ -n "$(untestable A)" ]; then rl_verdict UNTESTABLE "$(untestable A)"
elif [ -z "$(kv 'A soak' dur_s)" ]; then rl_verdict INCONCLUSIVE "no A soak line: the soak did not finish"
elif [ "$dur" -lt 1200 ]; then rl_verdict INCONCLUSIVE "the soak ran ${dur}s, under 1200"
elif [ "$ends" -ge 1 ] && [ "$pred" = 0 ]; then rl_verdict INCONCLUSIVE "$ends session ends with neither predictor"
fi
```

### R2. Arm B on 3.4.0: R2-H (forced ends, Helper), and the R2 verdict

**r2.sh**

```bash
rl_hold_off "$DHU" bt
inst "$OUT/apk/v340.apk"
bgate B && upr B 2 $V340 HELPER && { moto_bt on > "$OUT/B.ph-bt-on"; rl_sleep 120; cycles B 2; endarm B; }
```

**Cycle classes (both `gcyc` lines per cycle; the fields are in section 9).** `out` is `REC_CLEAN` (a USB
session within 180 s that held 180 s), `LOOP` (2 or more SSL, or a session end after the first SSL),
`NOREC` (no SSL within 180 s), `REC_WIFI` (the session came over WiFi), `CONFOUND` (a raise deadline),
`CHECK` (SSL with no USB and no WiFi line: the host reads the window) or `VOID`. `ovl=1` means a group
or a poke ran before the first SSL: the re-arm overlapped the USB re-attach. A counted cycle is one
that is not `VOID` or `CONFOUND` and raised no dialog (`count=1` in `cycles.tsv`).

**R2 verdict (H1 on 3.4.0), from the counted `A-c` and `B-c` cycles:**

- **PASS (H1 supported):** 1 or more counted `A-c` cycles is `LOOP`, `NOREC` or `REC_WIFI` with `ovl=1`,
  and both counted `B-c` cycles are `REC_CLEAN`.
- **FAIL (H1 falsified):** both counted `A-c` cycles are `REC_CLEAN`, 1 or more with `ovl=1`, and both
  `B-c` cycles are `REC_CLEAN`. The re-arm ran inside the re-attach and did no harm.
- **INCONCLUSIVE:** a counted `B-c` cycle is not `REC_CLEAN`; or `ovl=0` in both `A-c` cycles; or
  fewer than 2 counted cycles in either arm; or an `A-c` cycle is `CHECK`, or failed with `ovl=0`, and
  none failed with `ovl=1`.
- **Phone condition (mandatory, graded):** when the cycles carry phone lines, H1's mechanism is
  confirmed on the phone when a failing `A-c` cycle has `ph.wl` or `ph.rf` 1 or more and every `B-c`
  cycle reads `ph.rf=0`. A head-unit PASS that the phone does not confirm is INCONCLUSIVE. Quote one
  line per failing cycle from `<c>.phone-lines.txt`. If no cycle has phone lines, the phone condition
  is UNTESTABLE (phone adb), the verdict rests on the head unit, and the grade writes that to Setup notes.
- **H2 numbers (report, no verdict here):** `skip` and `sched` per cycle, and the `skip=` on each GATE
  line. Expect `skip` 1 on A's GATE and 0 on B's.

**grade-r2.sh**

```bash
nA=$(counted A | wc -l); nB=$(counted B | wc -l)
bbad=$(( nB - $(nout B REC_CLEAN) )); aclean=$(nout A REC_CLEAN)
afail=0; aovl=0; aph=0
for c in $(counted A); do v=$(num "$(kv "$c" ovl)"); aovl=$((aovl + v))
  case $(kv "$c" out) in LOOP|NOREC|REC_WIFI) if [ "$v" = 1 ]; then afail=$((afail + 1))
    [ $(( $(num "$(kv "$c" ph.wl)") + $(num "$(kv "$c" ph.rf)") )) -ge 1 ] && aph=$((aph + 1)); fi ;; esac; done
brf=0; for c in $(counted B); do [ "$(num "$(kv "$c" ph.rf)")" -ge 1 ] && brf=$((brf + 1)); done
phl=$(( $(phsum A ph.lines) + $(phsum B ph.lines) ))
rl_cond 1 ge "$nA" 2 'counted Native cycles (A)'
rl_cond 2 ge "$nB" 2 'counted Helper cycles (B)'
rl_cond 3 eq "$bbad" 0 'counted Helper cycles that did not recover cleanly'
rl_cond 4 ge "$afail" 1 'counted Native cycles that failed with the re-arm overlapping (ovl=1)'
if [ -n "$(untestable A)$(untestable B)" ]; then rl_verdict UNTESTABLE "$(untestable A) $(untestable B)"
elif [ "$nA" -lt 2 ] || [ "$nB" -lt 2 ]; then rl_verdict INCONCLUSIVE "counted cycles A=$nA B=$nB, fewer than 2"
elif [ "$bbad" -gt 0 ]; then rl_verdict INCONCLUSIVE "a Helper cycle did not recover: the drop breaks 3.4.0 in any mode"
elif [ "$aovl" = 0 ]; then rl_verdict INCONCLUSIVE "no Native cycle ran a group or a poke before its first SSL (ovl=0)"
elif [ "$afail" -ge 1 ]; then
  if [ "$phl" -gt 0 ]; then
    rl_cond 5 ge "$aph" 1 'phone: failing Native cycles where the phone dialled (ph.wl or ph.rf)'
    rl_cond 6 eq "$brf" 0 'phone: Helper cycles where the phone opened RFCOMM (ph.rf)'
    { [ "$aph" -ge 1 ] && [ "$brf" = 0 ]; } || rl_verdict INCONCLUSIVE "the head unit supports H1 and the phone does not confirm it"
  else note "R2: no phone lines in any cycle; the phone condition is UNTESTABLE and the verdict rests on the head unit"; fi
elif [ "$aclean" != "$nA" ]; then rl_verdict INCONCLUSIVE "a Native cycle is CHECK or failed with ovl=0"
fi
```

### R3. Arm C on 3.4.0: R2-N again, WiFi not chosen

**r3.sh**

```bash
rl_hold_off "$DHU" bt
inst "$OUT/apk/v340.apk"
bgate C && upr C 3 $V340 DESELECT set:connection-modes=usb && { moto_bt on > "$OUT/C.ph-bt-on"; rl_sleep 120; cycles C 1; endarm C; }
```

- **PASS (the deselect holds the stack down; a single clean recovery):** the counted `C-c` cycle is
  `REC_CLEAN` with `ini=0`, `grp=0`, `poke=0` and `desel` 1 or more. Phone: `ph.rf=0`.
- **FAIL:** the cycle is `LOOP`, `NOREC` or `REC_WIFI`, or `ini` 1 or more. A loop here points away from
  H1 and H3 toward H2 or H4; say which lines repeat.
- **INCONCLUSIVE:** no counted cycle in 3 attempts, or the cycle is `CHECK` (the host reads the window).
- **UNTESTABLE:** the GATE line still shows `no_deselect_line` or `armed_anyway` after `upr`'s one
  re-run, or the battery gate failed.

**grade-r3.sh**

```bash
c=$(counted C | head -1); o=$(kv "$c" out)
rl_cond 1 eq "$( [ "$o" = REC_CLEAN ] && echo 1 || echo 0)" 1 "the counted cycle is REC_CLEAN (out=${o:-none})"
rl_cond 2 eq "$(num "$(kv "$c" ini)" 9)" 0 'WiFi launchers started before the first SSL'
rl_cond 3 eq "$(num "$(kv "$c" grp)" 9)" 0 'P2P groups before the first SSL'
rl_cond 4 eq "$(num "$(kv "$c" poke)" 9)" 0 'wake pokes before the first SSL'
rl_cond 5 ge "$(num "$(kv "$c" desel)")" 1 'the deselect line'
rl_cond 6 eq "$(num "$(kv "$c" ph.rf)" 9)" 0 'phone: RFCOMM sockets opened by the phone'
if [ -n "$(untestable C)" ]; then rl_verdict UNTESTABLE "$(untestable C)"
elif [ -z "$c" ]; then rl_verdict INCONCLUSIVE "no counted cycle in 3 attempts"
elif [ "$o" = CHECK ]; then rl_verdict INCONCLUSIVE "out=CHECK: SSL with no USB and no WiFi line; read the window"
fi
```

### R5. Arm D on `main`: the soak, then R4-N (forced ends, Native)

**r5.sh**

```bash
rl_hold_off "$DHU" bt
inst "$OUT/apk/main.apk"
bgate D && upr D 3 $MAINC NATIVE && { moto_bt on > "$OUT/D.ph-bt-on"; soak D; cycles D 2; endarm D; }
```

Quote `install-delta-main.apk.txt` in Setup notes; `put` reseeds from the backup. R5 reads the `D soak`
lines, with the same fields as R1. The two `D-c<n>` cycles are R4-N, graded in R4-H.

- **PASS (the control is quiet):** `ends` 0 over `dur_s` 1200 or more, and `native_in_session` 0.
- **FAIL:** `native_in_session` 1 or more (a Native part ran inside a USB session on `main`).
- **INCONCLUSIVE:** `ends` 1 or more, or `dur_s` under 1200. Report what followed each end.

**grade-r5.sh**

```bash
dur=$(num "$(kv 'D soak' dur_s)"); ends=$(num "$(kv 'D soak' ends)"); nis=$(num "$(kv 'D soak' native_in_session)" 9)
[ "$(num "$(kv 'D soak' ph.lines)")" -gt 0 ] || note "R5: the phone capture has no lines in the soak window"
rl_cond 1 eq "$nis" 0 'Native parts inside the USB session (native_in_session)'
rl_cond 2 eq "$ends" 0 'session ends in the soak'
rl_cond 3 ge "$dur" 1200 'soak length in s'
if [ -n "$(untestable D)" ]; then rl_verdict UNTESTABLE "$(untestable D)"
elif [ -z "$(kv 'D soak' dur_s)" ]; then rl_verdict INCONCLUSIVE "no D soak line: the soak did not finish"
elif [ "$nis" = 0 ] && { [ "$ends" -ge 1 ] || [ "$dur" -lt 1200 ]; }; then rl_verdict INCONCLUSIVE "ends=$ends dur_s=$dur"
fi
```

### R4-G on `main`: the point of the USB loop half

R4-G brings up its own Native USB session, turns D-MOTO's Bluetooth on, and runs the hold: Gearhead is
disabled on D-MOTO, the USB tries fail for 75 s with the phone on the bus, then Gearhead comes back. If
item 1 below fails but the disable held, the script runs the hold once more (`D-G2`), and the grade
reads that one.

**r4g.sh**

```bash
rl_hold_off "$DHU" bt
inst "$OUT/apk/main.apk"
if bgate G && upr G 3 $MAINC NATIVE; then
  moto_bt on > "$OUT/G.ph-bt-on"; hold D-G
  if [ "$(fact D-G_gh_disabled)" = 1 ] && { [ "$(fact D-G_gh_procs)" != 0 ] || [ "$(kv 'D-G hold' ph.gh)" != 0 ]; }; then
    note "R4-G item 1 failed with the disable in place; one more hold"; hold D-G2; fi
  endarm G
fi
```

`fails` counts failed USB handshakes after Gearhead went down. Every `_ms` value is measured from the
first failure.

- **PASS (the surviving path is real on `main`):** all hold.
  1. `gh_disabled=1` and `gh_procs=0` on the `D-G disable:` line, and `ph.gh=0` (Gearhead logged nothing
     in the hold).
  2. `fails` 2 or more, and every value in `bus_samples` is 1 or more (the phone stayed on the bus).
  3. `ini` 1 or more and `poke` 1 or more, with `poke_ms` under 80000 (inside the hold).
- **FAIL (nothing wakes a plugged phone):** items 1 and 2 hold, `fail_span_ms` is 60000 or more, and
  `poke` is 0.
- **INCONCLUSIVE:** item 1 fails on the second hold too, or `fails` under 2, or a bus sample is 0, or
  `fail_span_ms` under 60000 with `poke` 0, or a poke came after 80000 ms.
- **UNTESTABLE:** `pm disable-user` was refused (`gh_disabled=0`), or a gate failed. The stop rule then
  skips K1 and K1c.
- **Report beside the verdict:** which route gave wireless back (`spend` with `spend_ms`, or `giveback`
  with `giveback_ms`; the grade writes it to Setup notes), `poked`, `stale`, `giveup`, `targeted`,
  `targeted_none`, `sched`, `dfr`, `dexp`, `refused`, and any `D-G DIALOG` line. Expected on
  `f9a72d480`: `stale` 2 or more, `giveup` 1, `targeted` 0 or 1, and `sched` still rising after the
  give-up. A PASS by `giveback` alone means the USB tries stopped for 8 s; a PASS by `spend` means the
  60 s budget ran out while USB still tried. Both are the same defect for the user.
- **Restore (report, no verdict):** `ssl`, `usb`, `wifi` and `poke` on the `restore` line, and the
  phone's `ph.wl` and `ph.rf`. `wifi` 1 or more, or `ph.rf` 1 or more, means the plugged phone was
  taken over Bluetooth and WiFi after Android Auto came back: the H1 shape on `main`.

**What a PASS would look like if nothing were wrong.** A Native poke with the phone off the bus is
correct behaviour. That is why item 2 needs every bus sample at 1 or more.

**grade-r4g.sh**

```bash
c=D-G; [ -n "$(kv 'D-G2 hold' fails)" ] && c=D-G2
dis=$(num "$(fact "${c}_gh_disabled")"); procs=$(num "$(fact "${c}_gh_procs")" 9); phgh=$(num "$(kv "$c hold" ph.gh)" 9)
fails=$(num "$(kv "$c hold" fails)"); span=$(num "$(kv "$c hold" fail_span_ms)"); ini=$(num "$(kv "$c hold" ini)")
poke=$(num "$(kv "$c hold" poke)"); pms=$(num "$(kv "$c hold" poke_ms)" 999999)
bus=$(fact "${c}_bus"); nbus=$(echo "$bus" | tr ',' '\n' | grep -c .); zbus=$(echo "$bus" | tr ',' '\n' | grep -cx 0)
note "R4-G ($c): spend=$(kv "$c hold" spend) spend_ms=$(kv "$c hold" spend_ms) giveback=$(kv "$c hold" giveback) giveback_ms=$(kv "$c hold" giveback_ms) poked=$(kv "$c hold" poked)"
rl_cond 1a eq "$dis" 1 'Gearhead disabled on D-MOTO (gh_disabled)'
rl_cond 1b eq "$procs" 0 'Gearhead processes left after the force-stop (gh_procs)'
rl_cond 1c eq "$phgh" 0 'phone: Gearhead lines in the hold (ph.gh)'
rl_cond 2a ge "$fails" 2 'failed USB handshakes in the hold'
rl_cond 2b ge "$nbus" 1 'bus samples taken'
rl_cond 2c eq "$zbus" 0 'bus samples with the phone off the bus'
rl_cond 3a ge "$ini" 1 'Native launchers started after the first failure'
rl_cond 3b ge "$poke" 1 'wake pokes after the first failure'
rl_cond 3c lt "$pms" 80000 'ms from the first failure to the first poke'
if [ -n "$(untestable G)" ]; then rl_verdict UNTESTABLE "$(untestable G)"
elif [ "$dis" != 1 ]; then rl_verdict UNTESTABLE "pm disable-user was refused (gh_disabled=$dis)"
elif [ "$procs" != 0 ] || [ "$phgh" != 0 ]; then rl_verdict INCONCLUSIVE "item 1 failed on $c: gh_procs=$procs ph.gh=$phgh"
elif [ "$fails" -lt 2 ]; then rl_verdict INCONCLUSIVE "fails=$fails, under 2"
elif [ "$nbus" -lt 1 ] || [ "$zbus" -gt 0 ]; then rl_verdict INCONCLUSIVE "the phone left the bus in $zbus of $nbus samples"
elif [ "$ini" -ge 1 ] && [ "$poke" -ge 1 ] && [ "$pms" -lt 80000 ]; then :
elif [ "$poke" = 0 ] && [ "$span" -ge 60000 ]; then :
else rl_verdict INCONCLUSIVE "poke=$poke poke_ms=$pms fail_span_ms=$span ini=$ini"
fi
```

### R4-H. Arm E on `main`: forced ends in Helper, and the R4-N and R4-H verdict

**r4h.sh**

```bash
rl_hold_off "$DHU" bt
inst "$OUT/apk/main.apk"
bgate E && upr E 2 $MAINC HELPER && { moto_bt on > "$OUT/E.ph-bt-on"; rl_sleep 120; cycles E 2; endarm E; }
```

**R4-N and R4-H verdict (H1 for a cable-shaped end on `main`), from the counted `D-c` and `E-c` cycles:**

- **PASS (`main` recovers):** all 4 counted cycles (`D-c`, `E-c`) are `REC_CLEAN`.
- **FAIL (H1 survives a cable-shaped end on `main`):** 1 or more counted `D-c` cycle is `LOOP`, `NOREC`
  or `REC_WIFI`, and both `E-c` cycles are `REC_CLEAN`. Report `ovl`, `poke`, `poked`, `std`, `arb`.
- **INCONCLUSIVE:** an `E-c` cycle is not `REC_CLEAN`, or fewer than 2 counted cycles in either arm, or a
  `D-c` cycle is `CHECK`.
- **Phone condition (mandatory, graded):** as R2. On a FAIL with phone lines, a failing `D-c` cycle
  needs `ph.wl` or `ph.rf` 1 or more and every `E-c` cycle `ph.rf=0`; else INCONCLUSIVE.
- **H2 (reported here, written to Setup notes by the grade):** for each counted `D-c` cycle `skip`,
  `sched` and `out`. **H2 does not hold for a non-user end** when every counted `D-c` cycle is
  `REC_CLEAN` with `sched` 1 or more: the service re-check brought USB back with no home-screen leg.
  **H2 holds** when a `D-c` cycle is `NOREC` with `sched` 0, and the matching `E-c` cycle recovered.
  Expect `skip=1` on D's GATE line and `skip=0` on E's. `skip` cannot rise inside a cycle unless
  `HomeFragment` is created again.

**grade-r4h.sh**

```bash
nD=$(counted D | wc -l); nE=$(counted E | wc -l)
ebad=$(( nE - $(nout E REC_CLEAN) )); dbad=$(( nD - $(nout D REC_CLEAN) )); dfail=$(nout D 'LOOP|NOREC|REC_WIFI')
dph=0; for c in $(counted D); do case $(kv "$c" out) in LOOP|NOREC|REC_WIFI)
  [ $(( $(num "$(kv "$c" ph.wl)") + $(num "$(kv "$c" ph.rf)") )) -ge 1 ] && dph=$((dph + 1)) ;; esac; done
erf=0; for c in $(counted E); do [ "$(num "$(kv "$c" ph.rf)")" -ge 1 ] && erf=$((erf + 1)); done
phl=$(( $(phsum D ph.lines) + $(phsum E ph.lines) ))
h2="R4-H H2:"; hold2=0; nohold=1
for c in $(counted D); do o=$(kv "$c" out); s=$(num "$(kv "$c" sched)"); h2="$h2 $c out=$o skip=$(kv "$c" skip) sched=$s"
  { [ "$o" = REC_CLEAN ] && [ "$s" -ge 1 ]; } || nohold=0; { [ "$o" = NOREC ] && [ "$s" = 0 ]; } && hold2=1; done
[ "$hold2" = 1 ] && h2="$h2 -> H2 holds (check the matching E cycle recovered)"; [ "$nohold" = 1 ] && [ "$nD" -ge 1 ] && h2="$h2 -> H2 does not hold for a non-user end"
note "$h2"
rl_cond 1 ge "$nD" 2 'counted Native cycles (D)'
rl_cond 2 ge "$nE" 2 'counted Helper cycles (E)'
rl_cond 3 eq "$ebad" 0 'counted Helper cycles that did not recover cleanly'
rl_cond 4 eq "$dbad" 0 'counted Native cycles that did not recover cleanly'
if [ -n "$(untestable D)$(untestable E)" ]; then rl_verdict UNTESTABLE "$(untestable D) $(untestable E)"
elif [ "$nD" -lt 2 ] || [ "$nE" -lt 2 ]; then rl_verdict INCONCLUSIVE "counted cycles D=$nD E=$nE, fewer than 2"
elif [ "$ebad" -gt 0 ]; then rl_verdict INCONCLUSIVE "a Helper cycle did not recover"
elif [ "$dbad" -gt 0 ] && [ "$dfail" = 0 ]; then rl_verdict INCONCLUSIVE "a Native cycle is CHECK; read the window"
elif [ "$dfail" -ge 1 ] && [ "$phl" -gt 0 ]; then
  rl_cond 5 ge "$dph" 1 'phone: failing Native cycles where the phone dialled (ph.wl or ph.rf)'
  rl_cond 6 eq "$erf" 0 'phone: Helper cycles where the phone opened RFCOMM (ph.rf)'
  { [ "$dph" -ge 1 ] && [ "$erf" = 0 ]; } || rl_verdict INCONCLUSIVE "the head unit shows H1 on main and the phone does not confirm it"
elif [ "$phl" = 0 ]; then note "R4-H: no phone lines in any cycle; the phone condition is UNTESTABLE"
fi
```

### K1 on `main`: "close the app on disconnect" (the point of the close-on-launch half)

D-MOTO's Bluetooth stays off. K1 ends with Gearhead still disabled and D-MOTO still in accessory mode,
so K1c finds the same wedge. K1c and X re-enable Gearhead.

**k1.sh**

```bash
rl_hold_off "$DHU" bt
inst "$OUT/apk/main.apk"
if bgate K1 && upr K1 3 $MAINC NATIVE bool:kill-on-disconnect=true; then
  rd 'kill-on-disconnect|enable-car-launcher' >> "$OUT/K1.keys"
  setf K1_kill_key "$(rd kill-on-disconnect | grep -acF 'value="true"')"   # not-literal: settings.xml
  setf K1_carl_key "$(rd enable-car-launcher | grep -acF 'value="false"')"   # not-literal: settings.xml
  mark K1-e0; gh_down K1
  for i in $(seq 1 30); do sleep 1; [ -z "$(hupid)" ] && break; done
  echo -e "K1-e0\talive_after=$([ -n "$(hupid)" ] && echo 1 || echo 0) secs=$i" | tee -a "$OUT/summary.tsv"
  mark K1-e0-end
  echo "K1-e0 kill=$(win K1-e0 K1-e0-end K1 | grep -acF 'AapService: killProcessOnDestroy is true. Triggering System.exit(0).')" | tee -a "$OUT/cycles.tsv"
  mark K1-wedge; ph_ok
  kl K1-L1; kl K1-L2; kl K1-L3
  mark K1-wedge-end
  wedge_line K1-wedge K1-wedge-end K1
  echo -e "K1\tgh_procs_end=$(ghn)" | tee -a "$OUT/summary.tsv"
  mark K1-end; dump K1; rl_cap_stop K1-; rl_stop thermal
fi
```

The prediction from the code: E0 ends the session by a lost link and closes the app. Each of L1 to L3
is a cold start that finds the phone in accessory mode, fails the handshake and exits.

**K1 PASS (the mechanism is reproduced):** all hold.

1. The `K1 disable:` line reads `gh_disabled=1 gh_procs=0`, and the keys read `kill-on-disconnect`
   true and `enable-car-launcher` false.
2. Phone: `K1 wedge` reads `ph.gh=0` (Android Auto stayed down). Report `ph.acc_out` and `ph.acc_in`;
   expect 0. A value above 0 means a recovery step re-enumerated the phone; the run still counts while
   each launch finds the accessory (`acc` 1 or more).
3. In 2 or more of `K1-L1` to `K1-L3`: `class=EXIT`, `acc` 1 or more and `fails` 1 or more.

**K1 FAIL (the mechanism is refuted on this rig):** items 1 and 2 hold, and 2 or more launches are
`class=STAYS` with `acc` 1 or more and `fails` 1 or more.

**K1 INCONCLUSIVE:** `acc` is 0 in 2 or more launches (no handshake ran, so the wedge was not held), or
item 1's `gh_procs` or item 2 fails, or neither PASS nor FAIL holds. **UNTESTABLE:** `gh_disabled=0`, or a
gate failed; the stop rule then skips K1c.

**Report beside the verdict:** E0's `alive_after`, `secs` and `kill` (the first close, with no launch);
for each launch `exit_s`, `finish`, `destroy`, `kill` and `stale`. A `class=OTHER` launch means the
process died with no kill line, or lived with one: quote that window.

**grade-k1.sh**

```bash
dis=$(num "$(fact K1_gh_disabled)"); procs=$(num "$(fact K1_gh_procs)" 9); phgh=$(num "$(kv 'K1 wedge' ph.gh)" 9)
ex=0; st=0; a0=0
for t in K1-L1 K1-L2 K1-L3; do cls=$(kv "$t" class); a=$(num "$(kv "$t" acc)"); f=$(num "$(kv "$t" fails)")
  [ "$a" = 0 ] && a0=$((a0 + 1))
  [ "$cls" = EXIT ] && [ "$a" -ge 1 ] && [ "$f" -ge 1 ] && ex=$((ex + 1))
  [ "$cls" = STAYS ] && [ "$a" -ge 1 ] && [ "$f" -ge 1 ] && st=$((st + 1)); done
rl_cond 1a eq "$dis" 1 'Gearhead disabled on D-MOTO (gh_disabled)'
rl_cond 1b eq "$procs" 0 'Gearhead processes left after the force-stop (gh_procs)'
rl_cond 1c eq "$(num "$(fact K1_kill_key)")" 1 'kill-on-disconnect reads true'
rl_cond 1d eq "$(num "$(fact K1_carl_key)")" 1 'enable-car-launcher reads false'
rl_cond 2 eq "$phgh" 0 'phone: Gearhead lines in the wedge (ph.gh)'
rl_cond 3 ge "$ex" 2 'launches that found the wedged phone, failed the handshake and exited'
if [ -n "$(untestable K1)" ]; then rl_verdict UNTESTABLE "$(untestable K1)"
elif [ "$dis" != 1 ]; then rl_verdict UNTESTABLE "pm disable-user was refused (gh_disabled=$dis)"
elif [ "$a0" -ge 2 ]; then rl_verdict INCONCLUSIVE "no handshake ran in $a0 launches: the wedge was not held"
elif [ "$procs" != 0 ] || [ "$phgh" != 0 ]; then rl_verdict INCONCLUSIVE "gh_procs=$procs ph.gh=$phgh"
elif [ "$ex" -lt 2 ] && [ "$st" -lt 2 ]; then rl_verdict INCONCLUSIVE "EXIT=$ex STAYS=$st of 3 launches"
fi
```

### K1c on `main`: K1's control

The same wedge, with `kill-on-disconnect` false. If Gearhead is no longer disabled, K1c disables it again
first. K1c then re-enables Gearhead and takes D-MOTO out of accessory mode.

**k1c.sh**

```bash
rl_hold_off "$DHU" bt
put $BASE int:wifi-connection-mode=3; rd 'kill-on-disconnect' >> "$OUT/K1c.keys"
setf K1c_kill_key "$(rd kill-on-disconnect | grep -acF 'value="false"')"   # not-literal: settings.xml
[ "$(ghdis)" -ge 1 ] || gh_down K1c
rl_thermal_gate; caps K1c; mark K1c-start
kl K1c-L1
gh_up K1c; adb -s "$PH" shell svc usb setFunctions mtp; sleep 5
mark K1c-end; dump K1c; rl_cap_stop K1c-; rl_stop thermal; clean_hu
```

**K1c PASS (the setting is the variable):** `class=STAYS`, `kill=0`, `home` 1 or more, `resumed_main=1`,
and `acc` 1 or more with `fails` 1 or more (the same failure ran). **FAIL:** `class` is `EXIT` or
`OTHER`. **INCONCLUSIVE:** `acc=0`, or `class=STAYS` with another condition missed.

**grade-k1c.sh**

```bash
t=K1c-L1; cls=$(kv "$t" class); a=$(num "$(kv "$t" acc)"); f=$(num "$(kv "$t" fails)")
rl_cond 1 eq "$(num "$(fact K1c_kill_key)")" 1 'kill-on-disconnect reads false'
rl_cond 2 eq "$( [ "$cls" = STAYS ] && echo 1 || echo 0)" 1 "the app stayed (class=${cls:-none})"
rl_cond 3 eq "$(num "$(kv "$t" kill)" 9)" 0 'process exits by the setting (kill)'
rl_cond 4 ge "$(num "$(kv "$t" home)")" 1 'the home screen came up'
rl_cond 5 eq "$(num "$(kv "$t" resumed_main)")" 1 'MainActivity resumed'
rl_cond 6 ge "$a" 1 'the launch found the wedged phone (acc)'
rl_cond 7 ge "$f" 1 'failed handshakes'
if [ "$a" = 0 ]; then rl_verdict INCONCLUSIVE "acc=0: no handshake ran"
elif [ "$cls" = STAYS ] && { [ "$f" = 0 ] || [ "$(num "$(kv "$t" home)")" = 0 ] || [ "$(num "$(kv "$t" resumed_main)")" != 1 ] || [ "$(num "$(fact K1c_kill_key)")" != 1 ]; }; then
  rl_verdict INCONCLUSIVE "the app stayed, and another condition missed"
fi
```

### K2 on D-SAM

D-SAM stays on its PC cable. No phone takes part, and no Android Auto is involved, so no phone capture
applies. D-SAM quirks: `am` rejects `-p` (none is used here); there is no `sed` or `awk` on the device
(all parsing is on the host); API 19 logs no activity `START` line, so the grade reads `mResumedActivity`.
`run-as ... cat` on D-SAM returns `\r\r\n` endings; compare with CRs stripped. D-SAM keeps the `main`
build; the script records the old md5.

**k2.sh**

```bash
SM=$PKG/com.andrerinas.openheadunit.main.MainActivity
adb -s "$SAM" shell am force-stop $PKG; sleep 0.3
echo "D-SAM date $(adb -s "$SAM" shell date | tr -d '\r') host date $(date)" >> "$OUT/setup-notes.md"
adb -s "$SAM" shell run-as $PKG cat shared_prefs/settings.xml > "$OUT/backup-sam.xml"; sleep 0.3
adb -s "$SAM" pull "$(adb -s "$SAM" shell pm path $PKG | head -1 | cut -d: -f2 | tr -d '\r')" "$OUT/sam-before.apk" >/dev/null
setf sam_md5_before "$(md5sum < "$OUT/sam-before.apk" | cut -c1-32)"
r=$(adb -s "$SAM" install -r -d "$OUT/apk/main.apk" 2>&1 | tr -d '\r' | tr '\n' ' '); echo "K2 install: $r" >> "$OUT/summary.tsv"
setf k2_install "$(echo "$r" | grep -acF Success)"
if [ "$(fact k2_install)" -ge 1 ]; then
  cp "$OUT/backup-sam.xml" "$OUT/ws.xml"; python3 -I "$SX" "$OUT/ws.xml" int:wifi-connection-mode=3 int:log-level=2 int:onboarding-version=2 int:native-driver-selection-mode=0 bool:native-poke-all-paired=false set:native-poke-bt-macs= set:auto-start-bt-macs= bool:kill-on-disconnect=false
  adb -s "$SAM" push "$OUT/ws.xml" /data/local/tmp/ohu-w.xml >/dev/null; adb -s "$SAM" shell run-as $PKG cp /data/local/tmp/ohu-w.xml shared_prefs/settings.xml; sleep 0.3
  adb -s "$SAM" shell run-as $PKG cat shared_prefs/settings.xml | tr -d '\r' | grep -aoE '(wifi-connection-mode|native-poke-all-paired|kill-on-disconnect)[^/]*' > "$OUT/K2.keys"
  for n in 1 2 3; do
    adb -s "$SAM" shell am force-stop $PKG; sleep 0.3; adb -s "$SAM" logcat -c; sleep 0.3
    rl_cap_start "K2-$n" "$SAM"; F="$OUT/K2-$n.txt"
    adb -s "$SAM" shell am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n $SM >/dev/null; rl_sleep 30
    al=$(adb -s "$SAM" shell ps | grep -acF $PKG); sleep 0.3
    rs=$(adb -s "$SAM" shell dumpsys activity activities | grep -a -m1 mResumedActivity | grep -acF MainActivity); sleep 0.3
    rl_cap_stop "K2-$n"
    la=$(grep -acF 'App launched' "$F"); cr=$(grep -acF 'AapService creating...' "$F")
    nat=$(grep -acF 'WifiLauncher: Initializing WiFi Mode: NATIVE' "$F")   # not-literal: the source prints the prefix plus the mode
    hm=$(grep -acF 'HomeFragment: onResume. isConnected=' "$F")
    fa=$(grep -acF 'FATAL EXCEPTION' "$F")   # system
    an=$(grep -acF 'ANR in' "$F")
    kl2=$(grep -acF 'AapService: killProcessOnDestroy is true. Triggering System.exit(0).' "$F")
    echo "K2-$n alive=$al resumed_main=$rs launched=$la created=$cr native=$nat home=$hm fatal=$fa anr=$an kill=$kl2" | tee -a "$OUT/cycles.tsv"
  done
fi
adb -s "$SAM" shell am force-stop $PKG; sleep 0.3
adb -s "$SAM" push "$OUT/backup-sam.xml" /data/local/tmp/ohu-w.xml >/dev/null; adb -s "$SAM" shell run-as $PKG cp /data/local/tmp/ohu-w.xml shared_prefs/settings.xml
```

- **PASS:** in all 3 launches `alive` 1 or more, `resumed_main=1`, `launched` 1 or more, `created` 1 or
  more, `native` 1 or more, `home` 1 or more, and `fatal`, `anr` and `kill` all 0.
- **FAIL:** any launch with `alive=0`, `fatal` or `anr` 1 or more, or `resumed_main=0` with no other
  activity of ours resumed. Keep the whole capture.
- **INCONCLUSIVE:** `native=0` in a launch (the Native bring-up never ran, so the run did not test it).
- **UNTESTABLE:** the install failed (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`); never uninstall.

**grade-k2.sh**

```bash
nat0=0
for n in 1 2 3; do t=K2-$n
  rl_cond "${n}a" ge "$(num "$(kv "$t" alive)")" 1 "$t: our process alive after 30 s"
  rl_cond "${n}b" eq "$(num "$(kv "$t" resumed_main)")" 1 "$t: MainActivity resumed"
  rl_cond "${n}c" ge "$(num "$(kv "$t" launched)")" 1 "$t: App launched"
  rl_cond "${n}d" ge "$(num "$(kv "$t" created)")" 1 "$t: the service started"
  rl_cond "${n}e" ge "$(num "$(kv "$t" home)")" 1 "$t: the home screen came up"
  rl_cond "${n}f" eq "$(( $(num "$(kv "$t" fatal)" 9) + $(num "$(kv "$t" anr)" 9) + $(num "$(kv "$t" kill)" 9) ))" 0 "$t: FATAL, ANR and exit lines"
  [ "$(num "$(kv "$t" native)")" = 0 ] && nat0=$((nat0 + 1)); done
if [ "$(num "$(fact k2_install)")" = 0 ]; then rl_verdict UNTESTABLE "the main install on D-SAM failed (see summary.tsv)"
elif [ "$nat0" -gt 0 ]; then rl_verdict INCONCLUSIVE "the Native bring-up never ran in $nat0 launches"
fi
```

### X. Clean-up

**x.sh**

```bash
gh_up X; setf x_gh_disabled "$(ghdis)"
clean_hu; adb -s "$HU" shell svc power stayon false
while IFS='=' read -r k v; do [ -n "$k" ] && adb -s "$HU" shell settings put system "$k" "$v"; sleep 0.3; done < "$OUT/poco-screen.txt"
if [ -s "$OUT/backup-poco.xml" ]; then put
  adb -s "$HU" shell run-as $PKG cat shared_prefs/settings.xml > "$OUT/settings-after.xml"
  setf x_settings_diff "$(diff "$OUT/settings-after.xml" "$OUT/backup-poco.xml" | grep -c '^[<>]')"; fi
adb -s "$HU" shell dumpsys usb | grep -a -i -A4 headunitrevived > "$OUT/usb-grants-after.txt"
diff "$OUT/usb-grants-before.txt" "$OUT/usb-grants-after.txt" > "$OUT/usb-grants-diff.txt"
[ "$METHOD" = PORT ] && adb -s "$HU" shell dumpsys usb ports > "$OUT/poco-ports-final.txt"
moto_bt on; rl_radio "$DHU" bt on
livemd5 > "$OUT/poco-apk-after.txt"
adb -s "$PH" usb; sleep 2; adb -s "$HU" usb; sleep 2
cue "X-PC: put D-POCO and D-MOTO back on their PC cables"
ok=0
for i in $(seq 1 120); do
  if adb -s "$D_POCO" get-state 2>/dev/null | grep -q device && adb -s "$D_MOTO" get-state 2>/dev/null | grep -q device; then ok=1; break; fi
  [ $((i % 6)) = 0 ] && rl_step "X: waiting for the PC cables, $((i * 5))s"
  sleep 5
done
setf x_cabled "$ok"
```

D-POCO keeps the `main` build; record that and both md5 values (`poco-apk-before.txt`,
`poco-apk-after.txt`) in Setup notes. `poco-ports-final.txt` MUST read source/host as in
`poco-ports.txt`. Quote `usb-grants-diff.txt`. The skill's restore step runs `rig_cleanup.sh` and the
radio baseline after approval, so X does not.

**grade-x.sh**

```bash
rl_cond 1 eq "$(num "$(fact x_gh_disabled)" 9)" 0 'Gearhead disabled on D-MOTO after X'
[ -s "$OUT/backup-poco.xml" ] && rl_cond 2 eq "$(num "$(fact x_settings_diff)" 9)" 0 'D-POCO settings.xml lines that differ from the backup'
rl_cond 3 eq "$(num "$(fact x_cabled)")" 1 'D-POCO and D-MOTO back on their PC cables'
```

## 8. Do not re-run

- **The stale accessory on builds before 3.5.0.** A plain retry recovers 0 of 11 cycles; `svc usb
  setFunctions mtp` recovers 15 of 15 (`archive/rounds/usb-reattach-round2-results.md`).
- **A Gearhead force-stop as a USB recovery.** It formed no session in 15 of 15 tries. `recover` does not
  use it.
- **The arbiter's normal path on `main`.** USB over an armed stack, a user connect over USB and the 8 s
  quiet window passed in `archive/rounds/usb-version-retry-round3-results.md`. The 60 s budget line never
  fired there, because the dongle came back only every 62 s. R4-G uses a phone that stays on the bus.
- **D-HU as a USB host.** It cannot host USB.
- **Unit tests.** The dev host ran them at `f9a72d480` (section 1).

## 9. Report back

The numbers that decide the next fixes:

1. **R4-G:** the verdict, `fails`, `fail_span_ms`, `bus_samples`, `ini_ms`, `poke_ms`, `poked`, which of
   `spend` or `giveback` fired and its `_ms`, and the `restore` line with the phone's `ph.wl` and `ph.rf`.
2. **K1 and K1c:** a table of E0 and every launch (`class`, `alive45`, `exit_s`, `acc`, `fails`,
   `finish`, `kill`, `home`), the phone's `K1 wedge` line, and both verdicts.
3. **R2 and R4-H:** a table of every cycle (arm, `out`, `t_ssl_ms`, `hsf+1`, `ovl`, `grp`, `poke`,
   `poked`, `dfr`, `arb`, `std`, `sched`, `skip`, `endpost`, `ph.wl`, `ph.rf`, `ph.acc_in`), with the R2,
   R3 and R4-N/H verdicts and the H2 reading.
4. **R1 and R5:** `dur_s`, `ends`, `end_min`, `native_in_session` and the verdicts. K2.
5. **P3:** `METHOD`, with `bus_during`, `back_passes`, `natt`, `aatt` and `t_ssl_ms` per trial.

All of these are lines in `$EV/cycles.tsv`, `$EV/summary.tsv` and `$EV/facts.txt`. Give each verdict in
the fixed format of `TESTING-TEMPLATE.md` §7. Put every deviation in Setup notes: each `REC=` line, each
`DIALOG`, each stream restart, each cue (`hand-steps.log`), each `gh_disabled` and `gh_procs` reading,
and the D-POCO PORT measurement. The grades already add their notes to `$EV/setup-notes.md`.
