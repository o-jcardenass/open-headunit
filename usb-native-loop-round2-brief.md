# usb-native-loop, round 2 brief

**Revision 2026-10-25 (before publish).** The candidate moved from `bafdcb2e` to `f9a72d480` (`main`,
4.0.0-alpha). Changed: section 1, the Queue line, the USB recovery notes in sections 2 and 3, the R4-G
hold script and conditions, K1 item 2, and section 6.

Publish name on `transfer/rig-rounds`: `usb-native-loop-round2-brief.md`. Results go in
`usb-native-loop-round2-results.md`. Evidence goes in the release `rig-evidence-usb-native-loop` as the
asset `usb-native-loop-round2-captures.zip`. Round 1 never ran, so the release does not exist yet:
create it (template §7, "first round of a thread only").

**This round replaces round 1.** `usb-native-loop-round1-brief.md` is queued and was never run. Do not
run it. This brief keeps its layout and its helpers, adds `main` as the candidate, and adds a second
report that has the same USB layout.

This is a reproduction round. No fix is on trial. It measures what `main` does today and gives the red
baseline for two later fixes. It covers two reports:

1. **The USB loop report.** A TOPWAY TS7 head unit on 3.4.0, used over USB with the wireless mode set
   to Native AA. The session drops after about 10 minutes and then reconnects again and again. Helper
   mode works.
2. **The close-on-launch report.** An ECARX head unit (API 22) with a CarlinKit adapter on USB. After a
   session ends, the app closes, and every tap on the icon shows the splash and exits, until the
   adapter reboots.

| Run | Build | What it measures | Units |
|---|---|---|---|
| P | both | builds, APK identity, bond, settings backup, wireless adb, the hand step | D-POCO, D-MOTO, D-HU (radio only), D-SAM (identity only) |
| P3 | v.3.4.0 | which scripted drop is closest to a cable pull | D-POCO, D-MOTO |
| R1 | v.3.4.0 | a 25 min Native USB session with no action | D-POCO, D-MOTO |
| R2 | v.3.4.0 | one forced end: Native (R2-N, chained after R1) against Helper (R2-H) | D-POCO, D-MOTO |
| R3 | v.3.4.0 | R2-N again with WiFi not chosen as a connection mode | D-POCO, D-MOTO |
| R5 | `main` | a 25 min Native USB session with no action (the control for R1) | D-POCO, D-MOTO |
| R4-N, R4-H | `main` | R2 again on `main`, Native and Helper, plus the home-screen skip (H2) | D-POCO, D-MOTO |
| **R4-G** | `main` | **the point of the USB loop half: 70 s of failed USB tries, then does Native wake the phone that is still plugged in** | D-POCO, D-MOTO |
| **K1** | `main` | **the point of the close-on-launch half: "close the app on disconnect" on, a failed handshake, three icon launches** | D-POCO, D-MOTO |
| K1c | `main` | K1's control, the same setting off | D-POCO, D-MOTO |
| K2 | `main` | **optional:** cold icon launch on D-SAM (API 19), Native mode, nothing on USB | D-SAM |
| X | | restore Gearhead, settings, screen, radios | all |

**Estimated time.** Builds about 35 min (one cold, one warm). P 15 min. P3 8 min. Arm A (R1 and R2-N)
about 38 min. Arm B (R2-H) 12 min. Arm C (R3) 9 min. Arm D (R5, R4-N, R4-G) about 46 min. Arm E (R4-H)
12 min. K1 and K1c 12 min. K2 10 min. X 5 min. **Total about 3 h 20 min**, plus the thermal-gate waits.
The operator is needed once before the first run. If P3 picks `HAND`, the operator also pulls the cable
9 times, about 4 to 10 minutes apart.

**Queue line:** `usb-native-loop-round2-brief.md` (`main` `f9a72d480` and tag `v.3.4.0` `013b05bc`;
units D-POCO as USB host and D-MOTO, D-SAM optional for K2, D-HU Bluetooth only; replaces round 1).
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
- Version codes: `v.3.4.0` is 109 and `main` is 120. D-POCO may carry another build. Install with
  `adb install -r -d` only (`inst` in section 5). Never uninstall.
- **DEX identity symbols:** `ConnectionArbiter` exists in `main` and not in `v.3.4.0`. `UsbRecoveryTarget`
  exists in `f9a72d480` and not in `v.3.4.0` or the earlier `main` `bafdcb2e`.

Make `rig-data/rounds/usb-native-loop-round2/` and set `OUT` to it. Build both APKs first, in one
worktree, before any run: 3.4.0 first, then `main`.

**get_apks.sh**

```bash
OUT=rig-data/rounds/usb-native-loop-round2; mkdir -p $OUT/apk
WT=$(ls -d worktrees/* 2>/dev/null | head -1)        # any existing worktree; else create one with wt-new.sh
git -C $WT fetch --no-tags origin tag v.3.4.0 main
git -C $WT checkout --detach 013b05bc0e7f5a881ea1d4363b9275df69bd5f5a
git -C $WT rev-parse 'HEAD^{tree}'                    # MUST print e86e02a1885d9e719e4e11064a2d203d9f179e04
$RIG_TOOLKIT/rig_thermal.sh wait 70
HUR_DIR=$WT MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh   # copy the APK out of apks/ to $OUT/apk/v340.apk at once
git -C $WT checkout --detach f9a72d480d51105870f62df6f0a40a1276e98a34
git -C $WT rev-parse 'HEAD^{tree}'                    # MUST print ff8fe6c2ea0a30f9e44276c6e9d3152591173969
$RIG_TOOLKIT/rig_thermal.sh wait 70
HUR_DIR=$WT MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh   # copy the APK out of apks/ to $OUT/apk/main.apk at once
md5sum $OUT/apk/v340.apk $OUT/apk/main.apk            # record both in Setup notes; they MUST differ
for a in v340 main; do printf '%s %s %s\n' $a "$(unzip -p $OUT/apk/$a.apk 'classes*.dex' | strings | grep -c ConnectionArbiter)" "$(unzip -p $OUT/apk/$a.apk 'classes*.dex' | strings | grep -c UsbRecoveryTarget)"; done
# v340 MUST print 0 0, and main MUST print 1 or more in both columns
```

Never run `git fetch --prune-tags` in that worktree. If a tree line or the DEX check differs, or a build
fails, stop the round.

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
  the app is the default home app). K1 checks both.

K1 builds that wedge on purpose: D-MOTO stays in accessory mode with Android Auto disabled. K2 tests
the other ranked mechanism (the Native bring-up on a cold launch) on the oldest rig unit, as weak
evidence only.

## 3. What is different about this round

- **Layout.** D-POCO is the head unit and the USB host, on wireless adb, with its OTG port free.
  D-MOTO is the phone, plugged into D-POCO's OTG port, also on wireless adb. D-HU cannot host USB
  (`rig-quirks/topics/tooling.md`, "No USB accessory path"). D-SAM stays on its PC cable and takes part
  only in K2.
- **Hold D-HU's Bluetooth off before every bring-up and every drop.** D-MOTO is bonded to D-HU's radio,
  and Gearhead can dial a cached head unit instead of the one under test (`rig-quirks/units/D-POCO.md`).
  It self-reverts, so `dhu_bt_off` repeats. X turns it back on.
- **No video is graded.** A phone that stands in as the head unit carries no video. Grade session
  lifetime, session ends, reconnects and process exits only.
- **D-POCO's screen must stay on.** A session ends 16 s after the handshake if the projection screen
  never comes up, and `main` stops reconnecting by itself after two such ends. Every bring-up sets
  `svc power stayon true`, wakes the panel and dismisses the keyguard. A cycle with
  `the projection screen never came up` is `CONFOUND` and does not count.
- **D-MOTO's Bluetooth is off while each arm brings its first session up.** In Native mode the launch
  pokes the phone. With Bluetooth on, the first session could form over WiFi. After the first USB
  session it goes on and stays on for R1 to R5, because H1 and R4-G need the poke to reach the phone.
  K1 and K1c keep it off: they test a handshake, not a wake.
- **The wake poke targets D-MOTO only** (`native-poke-all-paired` false, `native-poke-bt-macs` =
  D-MOTO). Helper ignores both keys.
- **No station stand-down can cut D-POCO's wireless adb.** `StationStandDownPolicy.isAvailable` is false
  from API 35. P stops the round below 35.
- **D-POCO powers D-MOTO over OTG and cannot charge.** Each arm checks the battery first and does not
  start below 40%. A soak stops at 20%.
- **`MATCH! Starting AapService` and a second `createGroup SUCCESS` are measurements here, not discard
  rules.** Count them.
- **The drop must take the phone out of accessory mode.** On builds before 3.5.0 a session end that
  leaves the phone in accessory mode makes every later handshake fail in every mode: a plain retry
  recovered 0 of 11 cycles and a Gearhead force-stop plus `ACTION_CHECK_USB` 0 of 15
  (`archive/rounds/usb-reattach-round2-results.md`). That is why the forced end in R2, R3 and R4-N/H is
  not a Gearhead force-stop: on 3.4.0 it would fail Helper too and hide H1. It is a cable pull, or the
  port-role lever if P3 shows it acts like one.
- **Why not `svc usb setFunctions mtp` as the drop.** It brings the phone back in about 1 s. The re-arm
  starts 1.5 s after the end and the poke about 2 s after that, so USB can win before any poke, and the
  race H1 describes never runs. Round 1's brief has the full comparison. It stays the recovery step.
- **How R4-G and K1 hold the phone in accessory mode with no Android Auto.** `am force-stop` alone does
  not hold Gearhead down: its own Bluetooth receiver restarts it (`rig-quirks/topics/gearhead.md`).
  `pm disable-user --user 0 com.google.android.projection.gearhead` then `am force-stop` does. It worked
  on D-MOTO (not rooted) in `archive/rounds/usb-reattach-round2-results.md` (RG: `gh_disabled=1`,
  `gh_procs=0`, every handshake failed until `pm enable`). It does not clear Gearhead's data. **This
  brief authorizes it** for R4-G and for K1 plus K1c. Every run re-enables Gearhead at its end, and X
  does it again. Check with `pm list packages -d` and `ps -A`, never `dumpsys package` or `pidof`
  (`rig-quirks/topics/lifecycle.md`, memory of the rig: Gearhead runs under process-name suffixes).
  If `pm disable-user` is refused, R4-G and K1 are UNTESTABLE.
- **The USB permission grants.** Read `dumpsys usb` in P and in X and record both. Never tick "Always"
  in a dialog (template §7b).
- **The rig audio keys are a deliberate worst case.** This round does not grade audio and writes no
  audio key. Record D-POCO's values once in P.
- **`video-profile-starvation-cap` is deleted at the start of every arm**, because a head unit with no
  video sets it after three sessions with no frame. Read it back at each arm's end.

### Expected outcomes, said up front

- **R1 and R5: expected FAIL (no end in 25 min).** The rig has no Topway USB or power event. That
  rules out H3 and H5 on this rig only.
- **R2 INCONCLUSIVE** if R2-H also fails to recover: the drop then breaks 3.4.0 in any mode.
- **R2 INCONCLUSIVE** if no R2-N cycle shows a group or a poke before its first SSL (`ovl=0`).
- **R4-G INCONCLUSIVE** if USB stops trying within 8 s and the arbiter gives wireless back through the
  quiet path rather than the 60 s budget. The verdict still records the poke; the route is reported.
- **R4-G may raise one USB permission dialog** for the re-enumerated accessory (the targeted reconnect).
  The hold loop cues H2 when it sees one. Record it; it does not void the run.
- **K2 is optional and weak.** D-SAM is API 19 Samsung, not API 22 ECARX.

### Hand steps

One batched request before the first run (P1). Keep a terminal on `tail -n0 -F $OUT/hand-steps.log`.

| Id | When | Action | Why there is no verb |
|---|---|---|---|
| H0 | before P0 | Charge D-POCO and D-MOTO to 100% on their PC cables | a charger is hardware |
| H1 | P1 | Unplug D-POCO and D-MOTO from the PC. Plug D-MOTO into D-POCO's OTG port. On D-MOTO set "USB controlled by" to "This device". Unlock D-MOTO if it shows a PIN | a cable is hardware; the role and a PIN are system screens |
| H1p | P1, only if P0 found no bond | Pair D-MOTO with D-POCO in both phones' Bluetooth settings | pairing is a system dialog |
| H2 | fallback | Allow a USB dialog on D-POCO (`UsbPermissionActivity` or `UsbConfirmActivity`). **Never tick "Always"** | a system dialog |
| H3 | fallback | Unplug D-MOTO from D-POCO, wait 5 s, plug it back, set "USB controlled by" to "This device" | the last recovery step |
| PULL-PLUG | only if P3 picks `HAND` | Pull D-MOTO's cable out of D-POCO, count to 8, plug it back, set the USB role if D-MOTO asks | P3 found no scripted drop that acts like a cable |
| Hx | after X | Put D-POCO and D-MOTO back on their PC cables, run `adb usb` on each | a cable is hardware |

**Tell the operator in P1:** if P3 picks `HAND`, R2, R3 and R4-N/H need 9 cued pulls.

## 4. Settings keys

Write with the app stopped, with `put` (section 5). `put` starts every write from P's backup, so each
arm carries exactly the backup plus these keys. Read every key back with `rd` before the launch. A key
that does not read back voids the arm before launch: write it again. If `put` cannot write, use
`$RIG_TOOLKIT/set_prefs_runas_host.py` with the same keys and say so in Setup notes.

| Key | Type | A, D (Native) | B, E (Helper) | C (R3) | K1 | K1c | Why |
|---|---|---|---|---|---|---|---|
| `wifi-connection-mode` | int | `3` | `2` | `3` | `3` | `3` | the variable (P3 uses `0`) |
| `connection-modes` | string set | `usb,wifi` | `usb,wifi` | `usb` | `usb,wifi` | `usb,wifi` | R3 deselects WiFi |
| `kill-on-disconnect` | boolean | `false` | `false` | `false` | **`true`** | `false` | K1's variable |
| `enable-car-launcher` | boolean | `false` | `false` | `false` | `false` | `false` | a car launcher turns `killOnDisconnect` off |
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
stores at `v.3.4.0`.

**unl_keys.sh**

```bash
BASE="int:helper-connection-strategy=2 int:log-level=2 int:onboarding-version=2 bool:kill-on-disconnect=false bool:enable-car-launcher=false bool:auto-connect-single-usb=true bool:auto-connect-last-session=true bool:auto-start-on-usb=false bool:reopen-on-reconnection=true bool:use-libusb=false set:connection-modes=usb,wifi int:native-driver-selection-mode=0 bool:native-poke-all-paired=false set:native-poke-bt-macs=$MOTO_BT set:auto-start-bt-macs= bool:auto-start-on-boot=false bool:auto-start-on-screen-on=false del:video-profile-starvation-cap del:native-aa-wake-damage-verdict del:connection-issue-dismissed-at del:native-aa-wireless del:wifi-launcher-mode"
RDK='wifi-connection-mode|connection-modes|kill-on-disconnect|enable-car-launcher|helper-connection-strategy|log-level|auto-connect|auto-start|reopen-on-reconnection|use-libusb|native-driver-selection-mode|native-poke|video-profile-starvation-cap'
```

`MOTO_BT` must be set before this file is sourced. Later keys win in `sx.py`, so an arm adds its own
values after `$BASE` (for example `set:connection-modes=usb`).

## 5. Helpers and verbs

Save `unl_keys.sh`, `unl_lib.sh`, `sx.py`, `unl_run.sh` and (in section 7) `unl_hold.sh` in `$OUT`. Source the four `.sh` files in every shell
of the round. Run the whole round under `flock /tmp/ohu-rig.lock`. One adb call at a time per unit; the
log captures are the only streams beside them (house rule 8).

**unl_lib.sh**

```bash
# unl_lib.sh : source in every shell. Needs OUT, HU, PH, MOTO_BT, RIG_TOOLKIT. METHOD, GAP, PORTID after P3.
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
MAIN=$PKG/com.andrerinas.openheadunit.main.MainActivity
GH=com.google.android.projection.gearhead
DHU=27870808938846
SSL='SSL handshake complete'
SEND='AapService: Stopping GpsLocationService and NightModeManager since connection is disconnected'
send() { local a=$1; shift; adb -s "$HU" shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; sleep 0.3; }
mark() { send ACTION_LOG_MARKER --es text "$1" >/dev/null; adb -s "$PH" shell log -t OHURIG "$1" >/dev/null 2>&1; sleep 0.3; }
cue() { echo "$(date +%T) OPERATOR_STEP $1"; printf '\a'; echo "$(date +%T) $1" >> "$OUT/hand-steps.log"; }
note() { echo "$(date +%T) $*" | tee -a "$OUT/setup-notes.md"; }
reconn() { local i; for i in $(seq 1 12); do adb -s "$1" get-state 2>/dev/null | grep -q device && return 0
  adb connect "$1" >/dev/null 2>&1; sleep 5; done; note "ADB_LOST $1"; return 1; }
th_gate() { "$RIG_TOOLKIT/rig_thermal.sh" wait 75 || "$RIG_TOOLKIT/rig_thermal.sh" wait 75 || { note HOST_HOT; return 1; }; }
th_watch() { "$RIG_TOOLKIT/rig_thermal.sh" watch "$OUT/thermal-$1.log" & echo $! > "$OUT/th.pid"; }
th_stop() { [ -f "$OUT/th.pid" ] && kill "$(cat "$OUT/th.pid")" 2>/dev/null; rm -f "$OUT/th.pid"; }
batt() { adb -s "$HU" shell dumpsys battery | grep -a -m1 -oE 'level: [0-9]+' | grep -oE '[0-9]+'; sleep 0.3; }
busn() { adb -s "$HU" shell ls /sys/bus/usb/devices | tr -d '\r' | grep -cE '^[0-9]+-[0-9.]+$'; sleep 0.3; }
dlg() { adb -s "$HU" shell dumpsys window | grep -a -m1 mCurrentFocus | grep -aqE 'UsbPermissionActivity|UsbConfirmActivity|UsbResolverActivity'; }
state() { send ACTION_QUERY_STATE | grep -a 'data='; }
rd() { adb -s "$HU" shell run-as $PKG cat shared_prefs/settings.xml | grep -aoE "($1)[^/]*"; sleep 0.3; }
put() { adb -s "$HU" shell am force-stop $PKG; sleep 0.3
  cp "$OUT/backup-poco.xml" "$OUT/w.xml"; python3 -I "$OUT/sx.py" "$OUT/w.xml" "$@"
  adb -s "$HU" push "$OUT/w.xml" /data/local/tmp/ohu-w.xml >/dev/null; sleep 0.3
  adb -s "$HU" shell run-as $PKG cp /data/local/tmp/ohu-w.xml shared_prefs/settings.xml; sleep 0.3; }
livemd5() { adb -s "$HU" pull "$(adb -s "$HU" shell pm path $PKG | head -1 | cut -d: -f2 | tr -d '\r')" "$OUT/live.apk" >/dev/null; sleep 0.3; md5sum < "$OUT/live.apk" | cut -c1-32; }
inst() { local want; want=$(md5sum < "$1" | cut -c1-32); [ "$(livemd5)" = "$want" ] && return 0
  adb -s "$HU" shell am force-stop $PKG; sleep 0.3
  adb -s "$HU" shell run-as $PKG cat shared_prefs/settings.xml > "$OUT/pre-install-$(basename "$1").xml"; sleep 0.3
  adb -s "$HU" install -r -d "$1"; sleep 0.3
  adb -s "$HU" shell run-as $PKG cat shared_prefs/settings.xml > "$OUT/post-install-$(basename "$1").xml"; sleep 0.3
  diff "$OUT/pre-install-$(basename "$1").xml" "$OUT/post-install-$(basename "$1").xml" > "$OUT/install-delta-$(basename "$1").txt"
  [ "$(livemd5)" = "$want" ] || { note "INSTALL_MISMATCH $1"; return 1; }; }
dhu_bt_off() { adb -s $DHU shell svc bluetooth disable >/dev/null 2>&1; sleep 3; }
ph_bt() { adb -s "$PH" shell svc bluetooth "$1"; sleep 4; adb -s "$PH" shell dumpsys bluetooth_manager | grep -a -m1 -E 'enabled:|state:'; sleep 0.3; }
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
# gh_down C : disable Gearhead and kill it; gh_up C : enable it again. Both write to summary.tsv.
gh_down() { local r; r=$(adb -s "$PH" shell pm disable-user --user 0 $GH 2>&1 | tr -d '\r' | tr '\n' ' '); sleep 0.3
  adb -s "$PH" shell am force-stop $GH; sleep 3
  echo -e "$1\tdisable: $r gh_disabled=$(ghdis) gh_procs=$(ghn)" | tee -a "$OUT/summary.tsv"; }
gh_up() { adb -s "$PH" shell pm enable $GH >/dev/null 2>&1; sleep 1
  echo -e "$1\tgh_disabled_after=$(ghdis)" | tee -a "$OUT/summary.tsv"; }
cap_start() { RUN=$1; HUF="$OUT/$RUN.hu.txt"; PHF="$OUT/$RUN.ph.txt"
  adb -s "$HU" logcat -G 16M; sleep 0.3; adb -s "$HU" logcat -c; sleep 0.3
  stdbuf -oL adb -s "$HU" logcat -v time > "$HUF" & echo $! > "$OUT/hu.pid"
  adb -s "$PH" logcat -G 16M; sleep 0.3; adb -s "$PH" logcat -c; sleep 0.3
  stdbuf -oL adb -s "$PH" logcat -v time > "$PHF" & echo $! > "$OUT/ph.pid"; sleep 1; }
cap_stop() { local f; for f in hu ph; do [ -f "$OUT/$f.pid" ] && kill "$(cat "$OUT/$f.pid")" 2>/dev/null; rm -f "$OUT/$f.pid"; done; }
hu_ok() { kill -0 "$(cat "$OUT/hu.pid")" 2>/dev/null && return 0; reconn "$HU" || return 1
  stdbuf -oL adb -s "$HU" logcat -v time -T 1 >> "$HUF" & echo $! > "$OUT/hu.pid"; note "$RUN hu stream restarted"; }
ph_ok() { kill -0 "$(cat "$OUT/ph.pid")" 2>/dev/null && return 0; reconn "$PH" || return 1
  stdbuf -oL adb -s "$PH" logcat -v time -T 1 >> "$PHF" & echo $! > "$OUT/ph.pid"; note "$RUN ph stream restarted"; }
nl() { wc -l < "$HUF"; }
seen() { tail -n +"$(( $2 + 1 ))" "$HUF" | grep -aqF -- "$1"; }            # seen STR FROMLINE
waitfor() { local i; for i in $(seq 1 "$1"); do seen "$2" "$3" && return 0; [ $((i % 10)) = 0 ] && hu_ok; sleep 1; done; return 1; }
dump() { reconn "$HU"; adb -s "$HU" logcat -d -v time > "$OUT/$1.hu.dump"; sleep 0.3
  reconn "$PH"; adb -s "$PH" logcat -d -v time > "$OUT/$1.ph.dump"; sleep 0.3; }
hwin() { awk -v a="AutomationMarker: $2" -v b="AutomationMarker: $3" '{sub(/\r$/,"")} function at(s){i=index($0,s); return i>0 && substr($0,i+length(s))~/^[[:space:]]*$/} at(a){on=1} on{print} on&&at(b){exit}' "$1"; }
pwin() { awk -v a="$2" -v b="$3" '{sub(/\r$/,"")} /OHURIG/ && $NF==a {on=1} on{print} on && /OHURIG/ && $NF==b {exit}' "$1"; }
# win A B : head unit window from the stream, or from the dump if the stream lost it
win() { local w; w=$(hwin "$HUF" "$1" "$2"); if printf '%s\n' "$w" | grep -aqF "AutomationMarker: $2"; then printf '%s\n' "$w"
  else hwin "$OUT/$3.hu.dump" "$1" "$2"; fi; }
pw() { local w; w=$(pwin "$PHF" "$1" "$2"); if [ -n "$w" ]; then printf '%s\n' "$w"; else pwin "$OUT/$3.ph.dump" "$1" "$2"; fi; }
# the drop. PORT: D-POCO stops hosting for GAP s. HAND: a cued pull.
drop() { local i; case $METHOD in
  PORT) adb -s "$HU" shell dumpsys usb set-port-roles "$PORTID" sink device >> "$OUT/$1.drop.txt" 2>&1; sleep "$GAP"
        adb -s "$HU" shell dumpsys usb set-port-roles "$PORTID" source host >> "$OUT/$1.drop.txt" 2>&1 ;;
  HAND) cue "PULL-PLUG: pull D-MOTO's cable out of D-POCO, count to 8, plug it back; set USB controlled by to This device if D-MOTO asks"
        for i in $(seq 1 120); do [ "$(busn)" = 0 ] && break; sleep 1; done; mark "$1-out"
        for i in $(seq 1 120); do [ "$(busn)" -ge 1 ] && break; sleep 1; done ;;
  esac; }
# recover C : only after a cycle missed SSL. Records REC=GADGET, RELAUNCH, CABLE or NONE.
recover() { local c=$1 L
  mark "$c-rec1"; L=$(nl); adb -s "$PH" shell svc usb setFunctions mtp; sleep 8
  seen "$SSL" "$L" || send ACTION_CHECK_USB > "$OUT/$c-rec1.reply"
  if waitfor 60 "$SSL" "$L"; then echo -e "$c\tREC=GADGET" >> "$OUT/summary.tsv"; return 0; fi
  mark "$c-rec2"; L=$(nl); ph_bt disable >/dev/null; clean_hu; panel; launch
  waitfor 60 "$SSL" "$L" || { send ACTION_CHECK_USB > "$OUT/$c-rec2.reply"; waitfor 60 "$SSL" "$L"; }
  local ok=$?; ph_bt enable >/dev/null
  if [ $ok = 0 ]; then echo -e "$c\tREC=RELAUNCH" >> "$OUT/summary.tsv"; return 0; fi
  mark "$c-rec3"; L=$(nl); cue "H3: unplug D-MOTO from D-POCO, wait 5 s, plug it back, then set USB controlled by to This device on D-MOTO"
  waitfor 180 'Normal USB device attached:' "$L"; sleep 8
  seen "$SSL" "$L" || send ACTION_CHECK_USB > "$OUT/$c-rec3.reply"
  if waitfor 90 "$SSL" "$L"; then echo -e "$c\tREC=CABLE" >> "$OUT/summary.tsv"; return 0; fi
  echo -e "$c\tREC=NONE" >> "$OUT/summary.tsv"; return 1; }
# gcyc C : grade one cycle, window C-drop to C-end
gcyc() { local c=$1
  win "$c-drop" "$c-end" "$c" > "$OUT/$c.hwin"; pw "$c-drop" "$c-end" "$c" > "$OUT/$c.pwin"
  LC_ALL=C awk -v c="$c" '
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
  LC_ALL=C awk -v c="$c" 'function has(s) { return index($0, s) > 0 } { sub(/\r$/, "") }
  has("GH.WIRELESS") { wl++ } has("Creating rfcomm socket for device:") { rf++ }
  has("entering USB accessory mode") { ai++ } has("exited USB accessory mode") { ao++ }
  has("Critical error") { ce++ } has("ConnLoggerV2") { cl++ }
  END { printf "%s ph.lines=%d ph.wl=%d ph.rf=%d ph.acc_in=%d ph.acc_out=%d ph.crit=%d ph.connlog=%d\n", c, NR, wl, rf, ai, ao, ce, cl }' "$OUT/$c.pwin" | tee -a "$OUT/cycles.tsv"
  grep -aE 'ConnLoggerV2|GH\.WIRELESS' "$OUT/$c.pwin" > "$OUT/$c.phone-lines.txt"; }
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

`unl_run.sh` holds the arm drivers. Source it after `unl_lib.sh` and `unl_keys.sh`.

**unl_run.sh**

```bash
# up A MODE COMMIT EXPECT [extra keys] : first USB session of run A, D-MOTO Bluetooth off. EXPECT is NATIVE, HELPER, MANUAL or DESELECT.
up() { local A=$1 M=$2 C=$3 E=$4 L g; shift 4
  clean_hu; put $BASE int:wifi-connection-mode=$M "$@"; rd "$RDK" | tee "$OUT/$A.keys"
  ph_bt disable | tee "$OUT/$A.ph-bt-off"; dhu_bt_off
  adb -s "$PH" shell svc usb setFunctions mtp; sleep 5; panel
  th_gate || return 3; th_watch "$A"; cap_start "$A"; mark "$A-start"; L=$(nl)
  launch
  case $E in NATIVE|HELPER) waitfor 15 'WifiLauncher: Initializing WiFi Mode: ' "$L" ;; esac
  waitfor 60 "$SSL" "$L" || { dlg && cue "H2: allow the USB dialog on D-POCO, never tick Always"; send ACTION_CHECK_USB > "$OUT/$A-check.reply"; waitfor 60 "$SSL" "$L"; } || { echo "$A GATE no_ssl" | tee -a "$OUT/summary.tsv"; return 1; }
  sleep 30; state > "$OUT/$A.state"; g=ok
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
  echo "$A GATE $g skip=$(tail -n +"$((L+1))" "$HUF" | grep -acF 'HomeFragment: Native AA mode active. Skipping manual auto-connect attempt.')" | tee -a "$OUT/summary.tsv"
  [ "$g" = ok ]; }
# upr : up, one re-run on a failed gate. Same arguments as up.
upr() { up "$@" && return 0; cap_stop; th_stop; note "$1 gate failed, re-run once"; up "$@" && return 0
  echo "$1 UNTESTABLE gate" | tee -a "$OUT/summary.tsv"; cap_stop; th_stop; return 1; }
# cyc C : one drop and reconnect cycle; hold 180 s after the reconnect, then grade
cyc() { local c=$1 L
  dhu_bt_off; hu_ok; ph_ok
  mark "$c-drop"; L=$(nl); drop "$c"; mark "$c-dropped"
  if ! waitfor 170 "$SSL" "$L"; then mark "$c-norec"; recover "$c" || echo "$c ARM_STOP" | tee -a "$OUT/summary.tsv"; fi
  dlg && { cue "H2: allow the USB dialog on D-POCO, never tick Always"; echo "$c DIALOG" | tee -a "$OUT/summary.tsv"; }
  sleep 180; mark "$c-end"; dump "$c"; gcyc "$c"; }
# cycles A N : up to N+2 attempts until N cycles count (not VOID, not CONFOUND, no DIALOG)
cycles() { local A=$1 N=$2 n=0 t=0 k
  while [ $n -lt "$N" ] && [ $t -lt $((N+2)) ]; do t=$((t+1)); k="$A-c$t"; cyc "$k"
    grep -aq "^$k ARM_STOP" "$OUT/summary.tsv" && break
    grep -aE "^$k out=(VOID|CONFOUND)" "$OUT/cycles.tsv" >/dev/null || grep -aq "^$k DIALOG" "$OUT/summary.tsv" || n=$((n+1)); done
  echo "$A counted_cycles=$n attempts=$t" | tee -a "$OUT/summary.tsv"; }
# soak A : 1500 s with no action; stops at the 3rd session end or battery under 20%
soak() { local A=$1 L i n=0 b
  mark "$A-soak-start"; L=$(nl)
  for i in $(seq 1 300); do sleep 5
    [ $((i % 6)) = 0 ] && { hu_ok; ph_ok; }
    n=$(tail -n +"$((L+1))" "$HUF" | grep -acF "$SEND"); [ "$n" -ge 3 ] && break
    if [ $((i % 60)) = 0 ]; then b=$(batt); echo "$A soak t=$((i*5))s battery=$b ends=$n" | tee -a "$OUT/summary.tsv"; [ "$b" -lt 20 ] && break; fi
  done
  mark "$A-soak-end"; dump "$A-soak"
  hwin "$HUF" "$A-start" "$A-soak-start" | LC_ALL=C awk -v a="$A" 'function has(s) { return index($0, s) > 0 } { sub(/\r$/, "") }
    has("NativeAA: Attempting active poke to device") || has("NativeAA: waking the phone while the WiFi group is still forming") { p++ }
    has("Successfully poked") { ok++ } has("NativeAA: hands-free service level connection established") { slc++ }
    END { printf "%s soak-pre poke=%d poked=%d slc=%d\n", a, p, ok, slc }' | tee -a "$OUT/cycles.tsv"
  win "$A-soak-start" "$A-soak-end" "$A-soak" | LC_ALL=C awk -v a="$A" '
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
  pw "$A-soak-start" "$A-soak-end" "$A-soak" | LC_ALL=C awk -v a="$A" 'function has(s) { return index($0, s) > 0 }
    has("exited USB accessory mode") { ao++ } has("Critical error") { ce++ } has("GH.WIRELESS") { wl++ } has("ConnLoggerV2") { cl++ }
    END { printf "%s soak ph.lines=%d ph.acc_out=%d ph.crit=%d ph.wl=%d ph.connlog=%d\n", a, NR, ao, ce, wl, cl }' | tee -a "$OUT/cycles.tsv"; }
# endarm A : close one arm
endarm() { rd 'video-profile-starvation-cap' | tee -a "$OUT/$1.keys"; mark "$1-end"; cap_stop; th_stop; clean_hu; ph_bt disable >/dev/null; }
bgate() { local b; b=$(batt); echo "$1 battery_start=$b" | tee -a "$OUT/summary.tsv"; [ "$b" -ge 40 ] || { echo "$1 UNTESTABLE battery=$b" | tee -a "$OUT/summary.tsv"; return 2; }; }
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

Every app string below matched `git grep -F` in `app/src/main` at `f9a72d480` and `git grep -F` at
`v.3.4.0`, except the ones marked **main only**. Every one is `AppLog.i`, `AppLog.w` or `AppLog.e`
(several also have an `AppLog.d` twin, which INFO drops), so `log-level` 2 carries them. Several are
prefixes of longer source lines; the prefix is enough. Two source lines carry an em dash after the
part quoted here (`wired session ended`, `USB session established while wireless mode`), so grep the
prefix only.

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
| `AapService: USB handoff settled after` | `deferWirelessForUsbHandoff` | the deferral ended early |
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
| `with no session; wireless comes back beside it` | `ConnectionArbiter.spendUsbEpisode` | **main only**: same line, second half |
| `ended with no session; giving back` | `ConnectionArbiter.settle` | **main only**: the 8 s quiet path |
| `UsbLauncher: stale accessory` | `UsbLauncherManager.onUsbHandshakeEnded` | **main only**: the recovery ladder |
| `recovery used both steps; a replug is needed` | `UsbLauncherManager.onUsbHandshakeEnded` | **main only**: the ladder gave up |
| `UsbLauncher: targeted recovery found` | `UsbLauncherManager.reconnectRecoveryTarget` | **main only** (new in `f9a72d480`): the reconnect after a reset |
| `UsbLauncher: targeted recovery ended without one identifiable accessory` | `UsbLauncherManager.reconnectRecoveryTarget` | **main only** (new in `f9a72d480`) |
| `not reconnecting by itself, the projection screen could not be raised.` | `AapService` | **main only**: `CONFOUND` |

**Not in our tree** (phone and framework lines, grepped, not verified against our source): `GH.WIRELESS`,
`GH.`, `Creating rfcomm socket for device:`, `ConnLoggerV2`, `Critical error`, `entering USB accessory
mode`, `exited USB accessory mode`, the marker tag `OHURIG`, and for K2 `FATAL EXCEPTION`, `ANR in` and
`mResumedActivity`. The Gearhead and accessory strings are quoted from earlier D-MOTO captures on this
branch (`archive/rounds/usb-reattach-round1-brief.md` and its results).

## 7. Runs

### P. Prepare (cabled, then wireless)

Run P0 with both phones on their PC cables, after H0.

**p0.sh**

```bash
OUT=rig-data/rounds/usb-native-loop-round2; mkdir -p $OUT
$RIG_TOOLKIT/rig_preflight.sh D_POCO:wifi,bt D_MOTO:wifi,bt | tee $OUT/preflight.txt     # quote it in Setup notes
adb -s 4f4027e9 shell getprop ro.build.version.sdk | tee $OUT/poco-sdk.txt              # MUST be 35 or more; else stop the round
adb -s 4f4027e9 shell dumpsys battery | grep -a level; adb -s ZY22GC3BM4 shell dumpsys battery | grep -a level
MOTO_BT=$(adb -s ZY22GC3BM4 shell settings get secure bluetooth_address | tr -d '\r'); echo "MOTO_BT=$MOTO_BT" > $OUT/env.txt
adb -s 4f4027e9 shell dumpsys bluetooth_manager | grep -a -A16 'Bonded devices' > $OUT/poco-bonds.txt
grep -acF "$MOTO_BT" $OUT/poco-bonds.txt                                              # 0 means H1p is needed
adb -s ZY22GC3BM4 shell dumpsys bluetooth_manager | grep -a -m1 -E 'enabled:|state:' | tee $OUT/moto-bt-start.txt
adb -s ZY22GC3BM4 shell dumpsys package com.google.android.projection.gearhead | grep -a -m1 versionName | tee $OUT/gearhead.txt
adb -s ZY22GC3BM4 shell pm list packages -d | grep -acF com.google.android.projection.gearhead   # MUST be 0
adb -s 4f4027e9 shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tee $OUT/poco-home.txt   # MUST NOT name com.andrerinas.headunitrevived
for k in screen_brightness screen_brightness_mode; do echo "$k=$(adb -s 4f4027e9 shell settings get system $k | tr -d '\r')"; done | tee $OUT/poco-screen.txt
POCO_IP=$(adb -s 4f4027e9 shell ip -4 addr show wlan0 | grep -aoE 'inet [0-9.]+' | cut -d' ' -f2)
MOTO_IP=$(adb -s ZY22GC3BM4 shell ip -4 addr show wlan0 | grep -aoE 'inet [0-9.]+' | cut -d' ' -f2)
echo "POCO_IP=$POCO_IP" >> $OUT/env.txt; echo "MOTO_IP=$MOTO_IP" >> $OUT/env.txt; cat $OUT/env.txt
adb -s 4f4027e9 tcpip 5555; sleep 3; adb -s ZY22GC3BM4 tcpip 5555; sleep 3
adb connect $POCO_IP:5555; adb connect $MOTO_IP:5555
```

**P1: the one batched request.** Send H1 (and H1p if P0 counted 0), and the `HAND` warning. Wait for
the operator's "done". Then:

**p1.sh**

```bash
OUT=rig-data/rounds/usb-native-loop-round2; . $OUT/env.txt
HU=$POCO_IP:5555; PH=$MOTO_IP:5555; . $OUT/unl_lib.sh; . $OUT/unl_keys.sh; . $OUT/unl_run.sh; . $OUT/unl_hold.sh
reconn $HU && reconn $PH                                       # both MUST succeed; else the rig is broken: stop
busn                                                           # MUST be 1 or more (D-MOTO on the bus)
adb -s $PH shell svc usb getFunctions                          # record
adb -s $HU shell dumpsys usb | grep -a -i -A4 headunitrevived > $OUT/usb-grants-before.txt   # record, do not change
adb -s $HU shell am force-stop $PKG; sleep 0.3
adb -s $HU shell run-as $PKG cat shared_prefs/settings.xml > $OUT/backup-poco.xml    # the round's baseline; never overwrite it
grep -aoE '(use-aac-audio|audio-latency-multiplier|audio-queue-capacity|enable-audio-sink)[^/]*' $OUT/backup-poco.xml   # record once, never write
livemd5 | tee $OUT/poco-apk-before.txt
adb -s $DHU shell am force-stop $PKG; dhu_bt_off
adb -s $HU shell settings put system screen_brightness_mode 0; adb -s $HU shell settings put system screen_brightness 10
inst $OUT/apk/v340.apk; cat $OUT/install-delta-v340.apk.txt                          # quote the delta
```

Run the first line of `p1.sh` plus its `. $OUT/...` sources again in every new shell of the round.
`env.txt` also carries `PORTID`, `GAP` and `METHOD` once P3 has written them.

**PASS when all hold:** `poco-sdk.txt` reads 35 or more; `reconn` worked for both; `busn` is 1 or more;
`MOTO_BT` is set; the bond count is 1 or more; Gearhead is not disabled; `poco-home.txt` does not name
our package; `livemd5` equals `v340.apk`'s md5. **A failure in P stops the round.** The install delta
does not stop it (`put` reseeds from the backup), but quote it.

### P3. Which drop

Mode 0 (MANUAL), 3.4.0, Bluetooth off on D-MOTO, one capture `P3`. No wireless stack runs. Two trials of
the port-role lever. The phone must leave the bus, come back under its own identity and form a session
again.

**p3.sh**

```bash
adb -s $HU shell dumpsys usb -h > $OUT/poco-usb-help.txt 2>&1; adb -s $HU shell dumpsys usb ports > $OUT/poco-ports.txt 2>&1
grep -acF set-port-roles $OUT/poco-usb-help.txt                # 0 means PORT is not available: METHOD=HAND
PORTID=$(grep -aoE 'id=[A-Za-z0-9_.-]+' $OUT/poco-ports.txt | head -1 | cut -d= -f2); GAP=8; echo "PORTID=$PORTID" >> $OUT/env.txt; echo "GAP=$GAP" >> $OUT/env.txt
upr P3 0 013b05bc0e7f MANUAL || echo "P3 GATE FAILED"
p3t() { local c=P3-PORT-$1 L a back i r
  dhu_bt_off; mark $c-drop; L=$(nl)
  adb -s $HU shell dumpsys usb set-port-roles "$PORTID" sink device > $OUT/$c.off.txt 2>&1; sleep 3; a=$(busn); sleep $((GAP-3))
  adb -s $HU shell dumpsys usb set-port-roles "$PORTID" source host > $OUT/$c.on.txt 2>&1
  mark $c-dropped; back=NA; for i in $(seq 1 20); do [ "$(busn)" -ge 1 ] && { back=$i; break; }; sleep 0.7; done
  waitfor 90 "$SSL" "$L"; r=$?
  if [ "$back" = NA ]; then adb -s $HU shell dumpsys usb set-port-roles "$PORTID" source host >> $OUT/$c.on.txt 2>&1; sleep 10
    [ "$(busn)" -ge 1 ] || { adb -s $PH shell svc usb setFunctions mtp; sleep 10; }
    [ "$(busn)" -ge 1 ] || { cue "H3: unplug D-MOTO from D-POCO, wait 5 s, plug it back, then set USB controlled by to This device on D-MOTO"; for i in $(seq 1 180); do [ "$(busn)" -ge 1 ] && break; sleep 1; done; }; fi
  [ $r = 0 ] || { seen "$SSL" "$L" || send ACTION_CHECK_USB > $OUT/$c.check.reply; waitfor 60 "$SSL" "$L" || adb -s $PH shell svc usb setFunctions mtp; waitfor 60 "$SSL" "$L"; }
  adb -s $HU shell dumpsys usb ports > $OUT/$c.ports-after.txt 2>&1
  sleep 20; mark $c-end; dump $c; gcyc $c
  echo "$c bus_during=$a back_passes=$back ssl_rc=$r" | tee -a $OUT/summary.tsv; }
[ -n "$PORTID" ] && grep -aqF set-port-roles $OUT/poco-usb-help.txt && { p3t 1; p3t 2; }
endarm P3
```

**Decide `METHOD` and append `METHOD=<value>` to `$OUT/env.txt`:**

- **`PORT`** if both trials have `bus_during=0`, `back_passes` not `NA`, `out=REC_CLEAN`, `natt` 1 or
  more, and `t_ssl_ms` under 98000.
- else **`HAND`**. Tell the operator now that R2, R3 and R4-N/H need 9 cued pulls. If a PORT trial needed
  the H3 cue, do not use PORT again.

**PASS** when `METHOD` is `PORT`. **FAIL** when it is `HAND` (the round still runs, with cues). Add a
first PORT measurement to `rig-quirks/units/D-POCO.md` in the results commit.

### Arm A on 3.4.0: R1 (soak), then R2-N (forced end, Native)

**arm_a.sh**

```bash
. $OUT/env.txt; echo "METHOD=$METHOD"                         # METHOD MUST be set
bgate A && upr A 3 013b05bc0e7f NATIVE && { ph_bt enable | tee $OUT/A.ph-bt-on; soak A; cycles A 2; endarm A; }
```

**R1, read from the `A soak` lines.** Report `dur_s`, `ends`, `end_min`, `byebye`, `connlost`,
`accdet`, `rearm`, `grp`, `poke`, `accepted`, `native_in_session`, `mw`, the `soak-pre` line, and the
phone's `ph.acc_out`, `ph.crit` and `ph.wl`. If `lines=0` on the dump line, the stream line stands.

- **PASS (H3 or H5 seen on the rig):** `ends` 1 or more, and either `byebye` 1 or more with `soak-pre`
  `poke` or `slc` 1 or more (H3), or `native_in_session` 1 or more (H5).
- **FAIL (H3 and H5 not reproduced on this rig, the expected result):** `ends` 0 over `dur_s` 1200 or
  more.
- **INCONCLUSIVE:** `ends` 1 or more with neither predictor, or `dur_s` under 1200.

**R2-N, read from the two `A-c<n>` cycle lines** (the R2 verdict is after arm B).

### Arm B on 3.4.0: R2-H (forced end, Helper)

**arm_b.sh**

```bash
bgate B && upr B 2 013b05bc0e7f HELPER && { ph_bt enable | tee $OUT/B.ph-bt-on; sleep 120; cycles B 2; endarm B; }
```

**Cycle classes (both `gcyc` lines per cycle; the fields are in section 9).** `out` is `REC_CLEAN` (a USB
session within 180 s that held 180 s), `LOOP` (2 or more SSL, or a session end after the first SSL),
`NOREC` (no SSL within 180 s), `REC_WIFI` (the session came over WiFi), `CONFOUND` (a raise deadline),
`CHECK` (SSL with no USB and no WiFi line: the host reads the window) or `VOID`. `ovl=1` means a group
or a poke ran before the first SSL: the re-arm overlapped the USB re-attach.

**R2 verdict (H1 on 3.4.0):**

- **PASS (H1 supported):** 1 or more counted `A-c` cycles is `LOOP`, `NOREC` or `REC_WIFI` with `ovl=1`,
  and both counted `B-c` cycles are `REC_CLEAN`.
- **FAIL (H1 falsified):** both counted `A-c` cycles are `REC_CLEAN`, 1 or more with `ovl=1`, and both
  `B-c` cycles are `REC_CLEAN`. The re-arm ran inside the re-attach and did no harm.
- **INCONCLUSIVE:** a counted `B-c` cycle is not `REC_CLEAN`; or `ovl=0` in both `A-c` cycles; or
  fewer than 2 counted cycles in either arm.
- **Phone condition (mandatory, graded):** for every `A-c` cycle with `ph.lines` above 0, report
  `ph.wl` and `ph.rf`. H1's mechanism is confirmed on the phone when an `A-c` failing cycle has `ph.wl`
  or `ph.rf` 1 or more and every `B-c` cycle reads `ph.rf=0`. Quote one line per failing cycle from
  `<c>.phone-lines.txt`. If no cycle has `ph.lines` above 0, the phone condition is UNTESTABLE (phone
  adb) and the verdict rests on the head unit; say so.
- **H2 numbers (report, no verdict here):** `skip` and `sched` per cycle, and the `skip=` on each GATE
  line. Expect `skip` 1 on A's GATE and 0 on B's.

### Arm C on 3.4.0: R3 (forced end, Native, WiFi not chosen)

**arm_c.sh**

```bash
bgate C && upr C 3 013b05bc0e7f DESELECT set:connection-modes=usb && { ph_bt enable | tee $OUT/C.ph-bt-on; sleep 120; cycles C 1; endarm C; }
```

- **PASS (the deselect holds the stack down; a single clean recovery):** the counted `C-c` cycle is
  `REC_CLEAN` with `ini=0`, `grp=0`, `poke=0` and `desel` 1 or more. Phone: `ph.rf=0`.
- **FAIL:** the cycle is `LOOP`, `NOREC` or `REC_WIFI`, or `ini` 1 or more. A loop here points away from
  H1 and H3 toward H2 or H4; say which lines repeat.
- **INCONCLUSIVE:** no counted cycle in 3 attempts. **Setup failure** (re-run once): the GATE line shows
  `no_deselect_line` or `armed_anyway`.

### Install `main`

**inst_main.sh**

```bash
inst $OUT/apk/main.apk; cat $OUT/install-delta-main.apk.txt     # quote the delta; put reseeds from the backup
```

### Arm D on `main`: R5 (soak), R4-N (forced end, Native), then R4-G (the point)

**arm_d.sh**

```bash
bgate D && upr D 3 f9a72d480d51 NATIVE && { ph_bt enable | tee $OUT/D.ph-bt-on; soak D; cycles D 2; hold D-G; endarm D; }
```

`hold` is in `unl_hold.sh` below. `p1.sh` sources it.

**unl_hold.sh**

```bash
# hold C : R4-G. A live USB session; Android Auto disabled on D-MOTO; 75 s of failed USB tries; then restore.
hold() { local c=$1 L i s
  dhu_bt_off; hu_ok; ph_ok; ph_bt enable >/dev/null
  mark "$c-start"; L=$(nl); gh_down "$c"
  [ "$(ghdis)" -ge 1 ] || { echo -e "$c\tUNTESTABLE gearhead not disabled" | tee -a "$OUT/summary.tsv"; gh_up "$c"; return 1; }
  waitfor 30 "$SEND" "$L" || echo -e "$c\tno-session-end-in-30s" | tee -a "$OUT/summary.tsv"
  mark "$c-hold"; s=""
  for i in $(seq 1 15); do s="$s$(busn),"; echo -e "$c\tt=$((i*5))s gh_procs=$(ghn)" >> "$OUT/$c.ghprocs"
    dlg && { cue "H2: allow the USB dialog on D-POCO, never tick Always"; echo "$c DIALOG t=$((i*5))s" | tee -a "$OUT/summary.tsv"; }; sleep 3; done
  echo -e "$c\tbus_samples=$s" | tee -a "$OUT/summary.tsv"
  mark "$c-holdend"; dump "$c-hold"
  gh_up "$c"; mark "$c-restore"; L=$(nl)
  adb -s "$PH" shell svc usb setFunctions mtp
  for i in $(seq 1 120); do seen "$SSL" "$L" && break; sleep 1; done
  dlg && { cue "H2: allow the USB dialog on D-POCO, never tick Always"; echo "$c DIALOG" | tee -a "$OUT/summary.tsv"; }
  sleep 30; mark "$c-end"; dump "$c"
  win "$c-start" "$c-holdend" "$c-hold" | LC_ALL=C awk -v c="$c" '
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
  pw "$c-hold" "$c-holdend" "$c-hold" | LC_ALL=C awk -v c="$c" 'function has(s) { return index($0, s) > 0 }
    has("GH.") { gh++ } has("exited USB accessory mode") { ao++ } has("entering USB accessory mode") { ai++ }
    END { printf "%s hold ph.lines=%d ph.gh=%d ph.acc_in=%d ph.acc_out=%d\n", c, NR, gh, ai, ao }' | tee -a "$OUT/cycles.tsv"
  win "$c-restore" "$c-end" "$c" | LC_ALL=C awk -v c="$c" 'function has(s) { return index($0, s) > 0 }
    has("SSL handshake complete") && !has("Handshake: SSL") { s++ }
    has("AapService: USB session established while wireless mode") { u++ } has("WirelessServer: Incoming connection detected") { w++ }
    has("NativeAA: Attempting active poke to device") || has("NativeAA: waking the phone while the WiFi group is still forming") { p++ }
    END { printf "%s restore ssl=%d usb=%d wifi=%d poke=%d\n", c, s, u, w, p }' | tee -a "$OUT/cycles.tsv"
  pw "$c-restore" "$c-end" "$c" | LC_ALL=C awk -v c="$c" 'function has(s) { return index($0, s) > 0 }
    has("GH.WIRELESS") { wl++ } has("Creating rfcomm socket for device:") { rf++ }
    END { printf "%s restore ph.lines=%d ph.wl=%d ph.rf=%d\n", c, NR, wl, rf }' | tee -a "$OUT/cycles.tsv"
  seen "$SSL" "$L" || { recover "$c" || echo "$c ARM_STOP" | tee -a "$OUT/summary.tsv"; }; }
```

If the hold ends with no session at all, `recover` runs before the arm closes. `gh_up` re-enables
Gearhead whatever happened.

**R5, read from the `D soak` lines,** with the same fields as R1.

- **PASS (the control is quiet):** `ends` 0 over `dur_s` 1200 or more, and `native_in_session` 0.
- **FAIL:** `native_in_session` 1 or more (a Native part ran inside a USB session on `main`).
- **INCONCLUSIVE:** `ends` 1 or more, or `dur_s` under 1200. Report what followed each end.

**R4-N, read from the two `D-c<n>` lines** (verdict after arm E).

**R4-G, the point of the USB loop half,** read from the `D-G hold`, `D-G hold ph.`, `D-G restore` lines
and `bus_samples`. `fails` counts failed USB handshakes after Gearhead went down. Every `_ms` value is
measured from the first failure.

- **PASS (the surviving path is real on `main`):** all hold.
  1. `gh_disabled=1` and `gh_procs=0` on the `D-G disable:` line, and `ph.gh=0` (Gearhead logged nothing
     in the hold).
  2. `fails` 2 or more, and every value in `bus_samples` is 1 or more (the phone stayed on the bus).
  3. `ini` 1 or more and `poke` 1 or more, with `poke_ms` under 80000 (inside the hold).
- **FAIL (nothing wakes a plugged phone):** items 1 and 2 hold, `fail_span_ms` is 60000 or more, and
  `poke` is 0.
- **INCONCLUSIVE:** item 1 fails (re-run R4-G once, then INCONCLUSIVE), or `fails` under 2, or
  `fail_span_ms` under 60000 with `poke` 0.
- **UNTESTABLE:** `pm disable-user` was refused (`gh_disabled=0`).
- **Report beside the verdict:** which route gave wireless back (`spend` with `spend_ms`, or `giveback`
  with `giveback_ms`), `poked` (the hands-free connect to D-MOTO succeeded), `stale`, `giveup`,
  `targeted`, `targeted_none`, `sched`, `dfr`, `dexp`, `refused`, and any `D-G DIALOG` line. Expected on
  `f9a72d480`: `stale` 2 or more, `giveup` 1, `targeted` 0 or 1, and `sched` still rising after the
  give-up. A PASS by `giveback` alone means the USB tries stopped for 8 s; a PASS by
  `spend` means the 60 s budget ran out while USB still tried. Both are the same defect for the user.
- **Restore (report, no verdict):** `ssl`, `usb`, `wifi` and `poke` on the `restore` line, and the
  phone's `ph.wl` and `ph.rf`. `wifi` 1 or more, or `ph.rf` 1 or more, means the plugged phone was
  taken over Bluetooth and WiFi after Android Auto came back: the H1 shape on `main`.

**What a PASS would look like if nothing were wrong.** A Native poke with the phone off the bus is
correct behaviour. That is why item 2 needs every bus sample at 1 or more.

### Arm E on `main`: R4-H (forced end, Helper)

**arm_e.sh**

```bash
bgate E && upr E 2 f9a72d480d51 HELPER && { ph_bt enable | tee $OUT/E.ph-bt-on; sleep 120; cycles E 2; endarm E; }
```

**R4-N and R4-H verdict (H1 for a cable-shaped end on `main`):**

- **PASS (`main` recovers):** all 4 counted cycles (`D-c`, `E-c`) are `REC_CLEAN`.
- **FAIL (H1 survives a cable-shaped end on `main`):** 1 or more counted `D-c` cycle is `LOOP`, `NOREC`
  or `REC_WIFI`, and both `E-c` cycles are `REC_CLEAN`. Report `ovl`, `poke`, `poked`, `std`, `arb`.
- **INCONCLUSIVE:** an `E-c` cycle is not `REC_CLEAN`, or fewer than 2 counted cycles in either arm.
- **Phone condition (mandatory, graded):** as R2, on the `D-c` and `E-c` cycles.
- **H2 (graded here):** for each counted `D-c` cycle report `skip`, `sched` and `out`. **H2 does not
  hold for a non-user end** when every counted `D-c` cycle is `REC_CLEAN` with `sched` 1 or more: the
  service re-check brought USB back with no home-screen leg. **H2 holds** when a `D-c` cycle is `NOREC`
  with `sched` 0, and the matching `E-c` cycle recovered. Expect `skip=1` on D's GATE line and `skip=0`
  on E's. Note that `skip` cannot rise inside a cycle unless `HomeFragment` is created again.

### K1 and K1c on `main`: "close the app on disconnect" (the point of the close-on-launch half)

D-MOTO's Bluetooth stays off. One capture, `K1`, holds both runs.

**k1.sh**

```bash
# kl N TAG : one icon launch with the phone wedged; grade it
kl() { local t=$1 L i alive=1 ex=NA
  dhu_bt_off; mark "$t"; L=$(nl); launch
  for i in $(seq 1 45); do sleep 1; [ "$i" -ge 3 ] && [ -z "$(hupid)" ] && { alive=0; ex=$i; break; }; done
  dlg && { cue "H2: allow the USB dialog on D-POCO, never tick Always"; echo "$t DIALOG" | tee -a "$OUT/summary.tsv"; }
  [ $alive = 1 ] && sleep $((45 - i)); [ $alive = 1 ] && [ -z "$(hupid)" ] && alive=0
  adb -s "$HU" shell dumpsys activity activities | grep -a -m1 mResumedActivity > "$OUT/$t.resumed"; sleep 0.3
  mark "$t-end"
  win "$t" "$t-end" K1 | LC_ALL=C awk -v t="$t" -v alive=$alive -v ex=$ex -v res="$(grep -acF MainActivity "$OUT/$t.resumed")" '
    function has(s) { return index($0, s) > 0 } { sub(/\r$/, "") }
    has("App launched") { la++ } has("AapService creating...") { cr++ } has("Found device already in accessory mode: ") { acc++ }
    has("Handshake: Version request send failed") || has("Handshake: SSL performHandshake failed.") || has("Handshake: Version request/response failed after") || has("Handshake failed with exception") || has("Handshake: Version exchange timed out after") { f++ }
    has("MainActivity: Received finish request. Closing.") { fin++ } has("AapService destroying...") { de++ }
    has("AapService: killProcessOnDestroy is true. Triggering System.exit(0).") { k++ }
    has("HomeFragment: onResume. isConnected=") { hr++ } has("UsbLauncher: stale accessory") { st++ }
    END { cls = (k > 0 && alive == 0) ? "EXIT" : (alive == 1 && k == 0) ? "STAYS" : "OTHER"
      printf "%s class=%s alive45=%d exit_s=%s launched=%d created=%d acc=%d fails=%d finish=%d destroy=%d kill=%d home=%d stale=%d resumed_main=%d\n", t, cls, alive, ex, la, cr, acc, f, fin, de, k, hr, st, res }' | tee -a "$OUT/cycles.tsv"; }
bgate K1 || exit 2
ph_bt disable >/dev/null
upr K1 3 f9a72d480d51 NATIVE bool:kill-on-disconnect=true || exit 1
rd 'kill-on-disconnect|enable-car-launcher' | tee -a $OUT/K1.keys          # MUST read true and false
# E0: Android Auto goes down under a live session
mark K1-e0; L=$(nl); gh_down K1
for i in $(seq 1 30); do sleep 1; [ -z "$(hupid)" ] && break; done; echo -e "K1-e0\talive_after=$([ -n "$(hupid)" ] && echo 1 || echo 0) secs=$i" | tee -a $OUT/summary.tsv
mark K1-e0-end
win K1-e0 K1-e0-end K1 | grep -acF 'AapService: killProcessOnDestroy is true. Triggering System.exit(0).' | sed 's/^/K1-e0 kill=/' | tee -a $OUT/cycles.tsv
mark K1-wedge; ph_ok
kl K1-L1; kl K1-L2; kl K1-L3
mark K1-wedge-end
# K1c: the same wedge, the setting off
put $BASE int:wifi-connection-mode=3; rd 'kill-on-disconnect' | tee -a $OUT/K1.keys       # MUST read false
kl K1c-L1
pw K1-wedge K1-wedge-end K1 | LC_ALL=C awk 'function has(s) { return index($0, s) > 0 }
  has("exited USB accessory mode") { ao++ } has("entering USB accessory mode") { ai++ } has("GH.") { gh++ }
  END { printf "K1 wedge ph.lines=%d ph.acc_out=%d ph.acc_in=%d ph.gh=%d\n", NR, ao, ai, gh }' | tee -a $OUT/cycles.tsv
echo -e "K1\tgh_procs_end=$(ghn)" | tee -a $OUT/summary.tsv
gh_up K1; adb -s $PH shell svc usb setFunctions mtp; sleep 5
mark K1-end; dump K1; cap_stop; th_stop; clean_hu
```

The prediction from the code: E0 ends the session by a lost link and closes the app. Each of L1 to L3
is a cold start that finds the phone in accessory mode, fails the handshake and exits. K1c stays on
its home screen.

**K1 PASS (the mechanism is reproduced):** all hold.

1. The `K1 disable:` line reads `gh_disabled=1 gh_procs=0`.
2. Phone: `K1 wedge` reads `ph.gh=0` (Android Auto stayed down). Report `ph.acc_out` and `ph.acc_in`;
   expect 0. A value above 0 means a recovery step re-enumerated the phone; the run still counts while
   each launch finds the accessory (`acc` 1 or more).
3. In 2 or more of `K1-L1` to `K1-L3`: `class=EXIT`, `acc` 1 or more and `fails` 1 or more.

**K1 FAIL (the mechanism is refuted on this rig):** items 1 and 2 hold, and 2 or more launches are
`class=STAYS` with `acc` 1 or more and `fails` 1 or more.

**K1 INCONCLUSIVE:** `acc` is 0 in 2 or more launches (no handshake ran, so the wedge was not held), or
item 2 fails. **UNTESTABLE:** `gh_disabled=0`.

**Report beside the verdict:** E0's `alive_after`, `secs` and `kill` (the first close, with no launch);
for each launch `exit_s`, `finish`, `destroy`, `kill` and `stale`. A `class=OTHER` launch means the
process died with no kill line, or lived with one: quote that window.

**K1c PASS (the setting is the variable):** `class=STAYS`, `kill=0`, `home` 1 or more, `resumed_main=1`,
and `acc` 1 or more with `fails` 1 or more (the same failure ran). **FAIL:** `class` is `EXIT` or
`OTHER`. **INCONCLUSIVE:** `acc=0`.

### K2 on D-SAM (optional)

Run only if time and the thermal gate allow. D-SAM stays on its PC cable. No phone takes part, and no
Android Auto is involved, so no phone capture applies. D-SAM quirks: `am` rejects `-p` (none is used
here); there is no `sed` or `awk` on the device (all parsing is on the host); API 19 logs no activity
`START` line, so the grade reads `mResumedActivity`.

**k2.sh**

```bash
SAM=30041c35642d2200; SM=$PKG/com.andrerinas.openheadunit.main.MainActivity
adb -s $SAM shell am force-stop $PKG; sleep 0.3
adb -s $SAM shell date; date                                           # record both in Setup notes
adb -s $SAM shell run-as $PKG cat shared_prefs/settings.xml > $OUT/backup-sam.xml; sleep 0.3
adb -s $SAM pull "$(adb -s $SAM shell pm path $PKG | head -1 | cut -d: -f2 | tr -d '\r')" $OUT/sam-before.apk >/dev/null; md5sum $OUT/sam-before.apk
adb -s $SAM install -r -d $OUT/apk/main.apk                           # INSTALL_FAILED_UPDATE_INCOMPATIBLE: K2 UNTESTABLE, never uninstall
cp $OUT/backup-sam.xml $OUT/ws.xml; python3 -I $OUT/sx.py $OUT/ws.xml int:wifi-connection-mode=3 int:log-level=2 int:onboarding-version=2 int:native-driver-selection-mode=0 bool:native-poke-all-paired=false set:native-poke-bt-macs= set:auto-start-bt-macs= bool:kill-on-disconnect=false
adb -s $SAM push $OUT/ws.xml /data/local/tmp/ohu-w.xml >/dev/null; adb -s $SAM shell run-as $PKG cp /data/local/tmp/ohu-w.xml shared_prefs/settings.xml; sleep 0.3
adb -s $SAM shell run-as $PKG cat shared_prefs/settings.xml | tr -d '\r' | grep -aoE '(wifi-connection-mode|native-poke-all-paired|kill-on-disconnect)[^/]*'
for n in 1 2 3; do
  adb -s $SAM shell am force-stop $PKG; sleep 0.3; adb -s $SAM logcat -c; sleep 0.3
  stdbuf -oL adb -s $SAM logcat -v time > $OUT/K2-$n.txt & P=$!; sleep 1
  adb -s $SAM shell am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n $SM >/dev/null; sleep 30
  al=$(adb -s $SAM shell ps | grep -acF $PKG); sleep 0.3
  rs=$(adb -s $SAM shell dumpsys activity activities | grep -a -m1 mResumedActivity | grep -acF MainActivity); sleep 0.3
  kill $P
  printf 'K2-%s alive=%s resumed_main=%s launched=%s created=%s native=%s home=%s fatal=%s anr=%s kill=%s\n' $n $al $rs \
    "$(grep -acF 'App launched' $OUT/K2-$n.txt)" "$(grep -acF 'AapService creating...' $OUT/K2-$n.txt)" \
    "$(grep -acF 'WifiLauncher: Initializing WiFi Mode: NATIVE' $OUT/K2-$n.txt)" "$(grep -acF 'HomeFragment: onResume. isConnected=' $OUT/K2-$n.txt)" \
    "$(grep -acF 'FATAL EXCEPTION' $OUT/K2-$n.txt)" "$(grep -acF 'ANR in' $OUT/K2-$n.txt)" \
    "$(grep -acF 'AapService: killProcessOnDestroy is true. Triggering System.exit(0).' $OUT/K2-$n.txt)" | tee -a $OUT/cycles.tsv
done
adb -s $SAM shell am force-stop $PKG; sleep 0.3
adb -s $SAM push $OUT/backup-sam.xml /data/local/tmp/ohu-w.xml >/dev/null; adb -s $SAM shell run-as $PKG cp /data/local/tmp/ohu-w.xml shared_prefs/settings.xml
```

`run-as ... cat` on D-SAM returns `\r\r\n` endings; compare with CRs stripped. D-SAM keeps the `main`
build; record the old md5 in Setup notes.

- **PASS:** in all 3 launches `alive` 1 or more, `resumed_main=1`, `launched` 1 or more, `created` 1 or
  more, `native` 1 or more, `home` 1 or more, and `fatal`, `anr` and `kill` all 0.
- **FAIL:** any launch with `alive=0`, `fatal` or `anr` 1 or more, or `resumed_main=0` with no other
  activity of ours resumed. Keep the whole capture.
- **INCONCLUSIVE:** `native=0` (the Native bring-up never ran, so the run did not test it).

### X. Clean-up

**x.sh**

```bash
gh_up X; adb -s $PH shell pm list packages -d | grep -acF $GH          # MUST print 0
clean_hu; adb -s $HU shell svc power stayon false
while IFS='=' read -r k v; do adb -s $HU shell settings put system "$k" "$v"; sleep 0.3; done < $OUT/poco-screen.txt
put; rd 'wifi-connection-mode|log-level|auto-connect|native-poke|kill-on-disconnect'; diff <(adb -s $HU shell run-as $PKG cat shared_prefs/settings.xml) $OUT/backup-poco.xml
adb -s $HU shell dumpsys usb | grep -a -i -A4 headunitrevived > $OUT/usb-grants-after.txt; diff $OUT/usb-grants-before.txt $OUT/usb-grants-after.txt
grep -aqiE 'enabled: *true|state: *ON' $OUT/moto-bt-start.txt && ph_bt enable || ph_bt disable
adb -s $DHU shell svc bluetooth enable; sleep 3; adb -s $DHU shell dumpsys bluetooth_manager | grep -a -m1 -E 'enabled:|state:'
[ "$METHOD" = PORT ] && adb -s $HU shell dumpsys usb ports > $OUT/poco-ports-final.txt   # role MUST read source/host as in poco-ports.txt
$RIG_TOOLKIT/rig_cleanup.sh
```

`put` with no keys copies the backup unchanged. D-POCO keeps the `main` build; record that and the md5
in `poco-apk-before.txt`. Then cue Hx.

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
3. **R2 and R4-N/H:** a table of every cycle (arm, `out`, `t_ssl_ms`, `hsf+1`, `ovl`, `grp`, `poke`,
   `poked`, `dfr`, `arb`, `std`, `sched`, `skip`, `endpost`, `ph.wl`, `ph.rf`, `ph.acc_in`), with the R2,
   R3 and R4-N/H verdicts and the H2 reading.
4. **R1 and R5:** `dur_s`, `ends`, `end_min`, `native_in_session` and the verdicts. K2 if it ran.
5. **P3:** `METHOD`, with `bus_during`, `back_passes`, `natt`, `aatt` and `t_ssl_ms` per trial.

Give each verdict in the fixed format of `TESTING-TEMPLATE.md` §7. Put every deviation in Setup notes:
each `REC=` line, each `DIALOG`, each stream restart, each cue, each `gh_disabled` and `gh_procs`
reading, and the D-POCO PORT measurement.

The block below lists every app string the runs grep that is in `app/src/main` at both `f9a72d480` and
`013b05bc`. A source line that continues past the quoted text is matched by its prefix.

```decisive-strings
AutomationReceiver: 
AutomationMarker: 
SSL handshake complete
AapService: Stopping GpsLocationService and NightModeManager since connection is disconnected
AapService: wired session ended
AapService: USB session established while wireless mode
WifiLauncher: Initializing WiFi Mode: 
WifiLauncher: wireless bring-up requested, but WiFi is not one of the chosen
createGroup SUCCESS
NativeAA: Attempting active poke to device
NativeAA: waking the phone while the WiFi group is still forming
Successfully poked
NativeAA: Connection accepted from
NativeAA: hands-free service level connection established
!!! RECEIVED BYEBYE REQUEST FROM PHONE !!!
WirelessServer: Incoming connection detected
AapService: a USB projection attempt is in flight
AapService: the USB attempt did not settle within
AapService: USB handoff settled after
Successfully requested switch to accessory mode
Normal USB device attached:
USB accessory device attached, connecting.
Found device already in accessory mode: 
AapService: USB disconnect. Scheduling reconnect check in
HomeFragment: Native AA mode active. Skipping manual auto-connect attempt.
HomeFragment: onResume. isConnected=
AapService: raising the projection by
AapService: the projection screen never came up
MATCH! Starting AapService
Handshake: Version request send failed
Handshake: SSL performHandshake failed.
Handshake: Version request/response failed after
Handshake failed with exception
Handshake: Version exchange timed out after
AapRead: Connection lost. Stopping read loop.
USB Accessory detached.
App launched
AapService creating...
AapService destroying...
MainActivity: Received finish request. Closing.
AapService: killProcessOnDestroy is true. Triggering System.exit(0).
```

These app strings are in `app/src/main` at `f9a72d480` only. On a 3.4.0 capture a count of 0 is the
expected reading.

```decisive-strings-main-only
ConnectionArbiter: 
stood the wireless stack down until it ends
wireless bring-up refused while
USB has tried for
with no session; wireless comes back beside it
ended with no session; giving back
UsbLauncher: stale accessory
recovery used both steps; a replug is needed
UsbLauncher: targeted recovery found
UsbLauncher: targeted recovery ended without one identifiable accessory
not reconnecting by itself, the projection screen could not be raised.
```

These are grepped too, but they are not in our tree: Gearhead's, Android's and the framework's lines,
and our phone marker tag.

```external-strings
GH.WIRELESS
GH.
Creating rfcomm socket for device:
ConnLoggerV2
Critical error
entering USB accessory mode
exited USB accessory mode
OHURIG
FATAL EXCEPTION
ANR in
mResumedActivity
```
