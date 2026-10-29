# usb-dialog, round 2 results

**Candidate:** `fork/fix/usb-permission-dialog` @ `63f20a8fc83366e386ef29c746f846bf5794137e` (tree `6aa22f1e`)       **Baseline:** none built; round 1 measured `main` `947b3ae9`, no USB file changed to `main` `2018d8f09`
**APK md5:** `3d1e486d4119922fde492138a17db97e` (candidate) / none (baseline)
**Unit:** D-HP (HP Slate 7 Plus, Android 4.2.2, API 17) and D-POCO (Android 15, API 35) as USB hosts, one at a time over wireless adb; D-MOTO (Android 14) as the wired phone, Bluetooth off, `sys.usb.config` `mtp,adb` before the round, its normal identity read `22B8:2E81` (ADB, no MTP) on both hosts, `mtp=no` on every decision line
**Date:** 2026-10-27

**Evidence:** release `rig-evidence-usb-dialog`, asset `usb-dialog-round2-captures.zip`, sha256 `798f10e773e48ebb1c146408d01a57e93680b8801f17167f19315a3c344faab9`. The verdicts rest on the lines quoted below, not on the asset.

## Setup notes

- **How the round actually ran.** The brief's `stage.sh` did not run to the end on D-HP, so stage H is three captures: `H` (seed failed, R5a, R5b, R5t, R5c, F4a, F4b void), `H4` (add-on, same host: seed, R4a, R4b, R4c, F4a, F4b by the correct rule) and the full stage `P` on D-POCO. R4 and F4 on D-HP are graded from `H4`, and R5 on D-HP from `H`. The add-on was a host decision the operator agreed to, because `H`'s seed failed and R4a is the point of the round.
- **Seed on D-HP, first stage (`H`): SEED_FAILED, three attempts.** seed1: the operator answered Android's "Open" dialogs with OK but left ours, route not recorded. seed2: no plug seen in 120 s (operator did not see the cue). seed3: ours cancelled, Android's "Open" dialog for the same plug then vanished with no OK, no session (`NO_SESSION`). The brief says R4 is then INCONCLUSIVE and the stage goes on to R5. `stage.sh` did not do that: `seed` returns 1 and only 2 exits, so it started `R4a-p1` (void by design, `routeSeen=no`). I killed the script at 11:38 and ran `scripts/stageH2.sh` and then `stageH3.sh` (R5 onward) on the same capture. Brief script bug.
- **Add-on on D-HP (`H4`), seed OK at `H4-seed1`** (`recorded=0`, pid 6320 held to the end of R4c). The route verdict on API 17 lives in the process only, so `setkeys f4` (a force-stop) lost it before F4: F4a on D-HP read `routeSeen=yes` in its decision line (`12:23:12.612`), where the brief expected `no`; the cause was not examined.
- **Two add-on restarts that are not findings:** the first launch (`ST=H2`) stopped after seed1 `NO_PLUG` (operator away from the rig); the second (`ST=H3`) lost R4a to two `NO_PLUG` cycles. In the second, D-MOTO's port read `power_role=sink`, `data_role=device`, `USB powered: true`, `host_connected=false` and D-HP's USB bus listed no device; reseating the OTG adapter in D-HP's port fixed it (rig connector, not code). The third (`ST=H4`) is the one graded.
- **`NO_UNPLUG_LINE`** fired on most cycles that the operator unplugged late: the script waits 90 s for `USB_DEVICE_DETACHED`. The two `USB_DEVICE_DETACHED` lines near each plug are the phone's own AOA re-enumeration, not the unplug. Time only.
- **Scripts changed or added** (all in `rig-data/rounds/usb-dialog-round2/scripts/`): `lib.sh` got a key-argument `writeprefs` (round 1's is a fixed block and cannot take `$BASEKEYS`, so "copy unchanged" cannot work), `. rig_devices.sh` at the top, a `cue()` that appends the run's rule, and a `tapusb` fallback to `text="Connect over USB"`; `cycle.sh` got `KSTART` for the first cycle number, and a 45 s sleep after the LOOK cue; `stageH2/H3/H4/H5/H6.sh` and `stageP2.sh` are the stage variants (slower cues: a NEXT line with a 12 s pause, the rule on every cue, 25 s after each COUNT; `SEED_FAILED` skips R4).
- **R5t tap target.** `tapusb` found 0 matches for `resource-id usb_button` or `text="USB"` on D-HP (first launch: `H-R5t-p1 TAP_TARGET matches=0`, no tap sent). This build's home shows a primary button `text="Connect over USB"` (clickable, bounds `[289,501][822,639]`), which calls `onUsbClicked` when the primary connection is USB, the same function as the tile. The patched fallback tapped it once per graded cycle (D-HP `11:49:47` at 555,570; D-POCO `12:49:22` at 522,1849; `usb_button` is absent from the D-POCO dump as well). `taps.txt` also holds one D-HP tap at `11:48:04` from an aborted launch (reused cycle label). Total on stage H: 2, on stage P: 1, within the brief's limit.
- **Reused cycle label.** A restart reused `H-R5t-p1`, so `since` began at the old marker and the plug wait returned at once; killed, and the cycle ran as `H-R5t-p2` (`KSTART=1`).
- **F4 on D-HP in stage `H` is void.** The LOOK and UNPLUG cues were 11 s apart (the script waited only for the session), the operator did not see the instructions and answered ours with OK, no tick. Re-run in `H4` with a 45 s LOOK window.
- **A pause.** The operator asked for a pause in `H3`; I froze the script with `SIGSTOP` and resumed with `SIGCONT`. Part of the 120 s plug wait ran out meanwhile (`H3-R4a-p1 NO_PLUG`), so a pause eats into a plug wait.
- **`timeline.sh` truncates each line at 240 characters**, so `action=` prints as `ho`, `wai`, `a` in `cycles.tsv`. Every `action` and `source` quoted here comes from the raw `UsbLauncher: permission for ` line in the capture (`grading-extract.txt`). `timeline.sh` also does not list `AutomationReceiver: ` lines (L-RX); those come from the capture.
- **Operator counts.** Taken at each COUNT cue and recorded in `marks.txt`. Every count matches the host except R5c on both hosts (see R5c).
- **Prepare.** `dumpsys usb` on D-POCO (API 35) has no `device_preferences` block and does not name D-MOTO's serial: `serial_outside=0` before (Prepare) and after (`P-end`); `defaults=0`. So no lasting grant, no uninstall question. D-HP `defaults=0 serial_outside=0` before and after. Both APKs were installed `-r -d`: `apk_identity.sh` MATCH `3d1e486d`, `ACTION_QUERY_STATE` `commit=63f20a8fc833` on both. Live APK before install: md5 `77ecb9b4` on both. Host and unit clocks agreed to 1 s. D-POCO `SYSTEM_ALERT_WINDOW` was `allow`. D-MOTO `stay_on_while_plugged_in` was 15 (D-HP 7, D-POCO 15), all three restored.
- **Settings.** `BASEKEYS` plus `del:usb-attach-route-boot del:video-profile-starvation-cap` written on each stage with the app stopped, every key read back; per run `listen-for-usb-devices` and `auto-start-on-usb` by `setkeys` (`comp=enabled`/`disabled` read back from `dumpsys package` each time). No `usb-blacklist` or `enable-car-launcher` key on either host. Each host's `settings.xml` restored identical to its backup (diff clean, carriage returns stripped). Hosts' `screen-orientation` left as found (D-HP 2).
- **Quirk files read:** `topics/tooling.md`, `units/D-HP.md`, `units/D-SAM-and-D-HP.md`, `units/D-POCO.md`, `units/D-MOTO.md`, template section 7b.
- **Preflight at the start** (read-only, `rig_preflight.sh D_HP:wifi D_POCO:wifi D_MOTO:wifi`; the brief's role spelling `D-HP` with a hyphen printed `invalid variable name` and `PREFLIGHT OK`, a false OK, so it was re-run with underscores):

```
ROLE     ADB      WIFI   BT     SCREEN    BT-PROFILES
D_HP     ok       1      1      Awake     HFP:none A2DP:none
D_POCO   ok       1      1      Awake     HFP:none A2DP:none
D_MOTO   ok       1      0      Awake     HFP:none A2DP:none
```

- **Closing preflight** (after the operator put the cables back and D-MOTO's Bluetooth, left on by the round, was switched off and read back; an earlier closing read with the cables still on the OTG layout failed only because D-POCO and D-MOTO are found by their USB link):

```
ROLE     ADB      WIFI   BT     SCREEN    BT-PROFILES
D_HP     ok       1      1      Awake     HFP:none A2DP:none
D_POCO   ok       1      1      Awake     HFP:none A2DP:none
D_MOTO   ok       1      0      Awake     HFP:none A2DP:none
PREFLIGHT OK
```
- **Build:** worktree `usb-dialog__scratch__63f20a8f__20261027`, `build_hur_cool.sh`, exit 0; `UsbPermissionAskPolicy` string count in `classes*.dex`: 23. Brief's `wt-new.sh usb-dialog` has one argument; the tool needs `<topic> <role> <ref>`. No unit tests run.
- **Thermal:** `rig_thermal.sh wait` passed before each stage and the build (57 to 64 C at the gates), `watch` ran during each stage.

## seed (no verdict)

D-HP `H-seed1..3`: failed (above). D-HP `H4-seed1`: `H4 seed OK at H4-seed1 pid=6320 recorded=0`; D-POCO `P-seed1`: `P seed OK at P-seed1 pid=422 recorded=1`. The seed plug itself raised our dialog for A on both hosts because `routeSeen` was still `no` at the 2 s decision (`H4-seed1`: `perm_A=1 req_A=1`, `confirm_A=1 confirm_B=1`).

## R4a

**PASS**

Listen on, no defaults, first connect, three cycles per host, each after a Clear defaults (`defaults=0` after every clear; `H4-R4a-p1` and `P-R4a-p1` had none to clear). Rule: tick and OK at once.

| Cycle | stacked | req_A / req_B | perm_A / perm_B | confirm_A / confirm_B | dec_A | dec_B | open_at_ms | ssl / ms / phone_acc | after (pairs) |
|---|---|---|---|---|---|---|---|---|---|
| D-HP p1 | 0 | 0 / 0 | 0 / 0 | 1 / 1 | wait | wait | 0 | 1 / 4 / 1 | 8888/11905, 6353/11521 |
| D-HP p2 | 0 | 0 / 0 | 0 / 0 | 1 / 1 | none | wait | 0 | 1 / 4 / 1 | same |
| D-HP p3 | 0 | 0 / 0 | 0 / 0 | 1 / 1 | wait | wait | 0 | 1 / 4 / 1 | same |
| D-POCO p1 | 0 | 0 / 0 | 0 / 0 | 1 / 1 | wait | wait | 0 | 1 / 4 / 1 | same |
| D-POCO p2 | 0 | 0 / 0 | 0 / 0 | 1 / 1 | wait | wait | 0 | 1 / 4 / 1 | same |
| D-POCO p3 | 0 | 0 / 0 | 0 / 0 | 1 / 1 | none | wait | 0 | 1 / 4 / 1 | same |

`fatal=0` and `pid` unchanged (6320 and 422) in every cycle. `mtp_A=no` throughout, so the `mtp=no` expectation (`confirm_A=1`, `confirm_B=1`, `perm=0`, `req=0`) holds on every cycle. Operator count: 2 Android "Open" dialogs per plug on both hosts, none of ours (matches `confirm_A + confirm_B = 2`).

Decisive lines (D-HP `H4-R4a-p1`):
- `12:13:42.342 UsbLauncher: permission for VID: 22B8 PID: 2E81 ... identity=normal mtp=no filter=match activity=on listen=on routeSeen=yes attach=2103ms source=automatic action=wait`
- `12:13:46.472 ... identity=accessory ... routeSeen=yes attach=1097ms source=automatic action=wait`, then `Accessory-mode device has no permission yet; Android shows its own dialog for it:`

`dec_A` was empty on D-HP p2 and D-POCO p3 (no decision line for A in those cycles); the cause was not examined. Round 1 on `main` read 3 or 4 dialogs and 2 requests per plug; this build reads 2 dialogs (both Android's) and 0 requests.

D-HP's first-stage R4 (`H`) is INCONCLUSIVE (`SEED_FAILED`) and superseded by this add-on.

## R4b

**PASS**

One cycle per host after a Clear defaults, rule: as R4a until WAIT, then wait for ANSWER, then tick and OK.

| Host | perm_B | req_B | dec_B | b_answer_ms | ssl / phone_acc | stacked | fatal |
|---|---|---|---|---|---|---|---|
| D-HP | 0 | 0 | wait | 24620 | 1 / 1 | 0 | 0 |
| D-POCO | 0 | 0 | wait | 15209 | 1 / 1 | 0 | 0 |

- D-HP: `12:17:32.552 ... identity=accessory ... routeSeen=yes attach=1116ms source=automatic action=wait`
- D-POCO: `12:41:04.727 ... identity=accessory ... routeSeen=yes attach=1045ms source=automatic action=wait`

On `main` the service stacked its own dialog for B 1 s after the attach (round 1); here B stays with Android's dialog for 24.6 s and 15.2 s with nothing of ours over it. Operator count: 2 Android "Open" on each host.

## R4c

**PASS**

Three replugs per host, no clear, defaults in force, rule: touch nothing.

| Host | cycles | dialog starts (confirm, perm, resolver) | req_A / req_B | decision lines | ssl / ms / phone_acc | L-SW | L-ACTOK | fatal |
|---|---|---|---|---|---|---|---|---|
| D-HP | p1, p2, p3 | 0, 0, 0 | 0 / 0 | 0 | 1 / 4 / 1 each | 1 each | 1 each | 0 |
| D-POCO | p1, p2, p3 | 0, 0, 0 | 0 / 0 | 0 | 1 / 4 / 1 each | 1 each | 1 each | 0 |

The plan asked for 0 decision lines and all six cycles printed none, so not even a `wait` appeared. Operator count: 0 on all six plugs.

## R5a

**PASS**

After `setkeys listen-off` (`H listen-off comp=disabled want=disabled pid=3529`, `P listen-off comp=disabled want=disabled pid=3083`). Three replugs per host, 20 s each, rule: touch nothing.

| Host | dialog starts | req_A / req_B | dec_A | dec_B | ssl | phone_acc |
|---|---|---|---|---|---|---|
| D-HP p1, p2, p3 | 0, 0, 0 | 0 / 0 | hold | none | 0 | 0 |
| D-POCO p1, p2, p3 | 0, 0, 0 | 0 / 0 | hold | none | 0 | 0 |

- D-HP: `11:39:24.112 ... identity=normal mtp=no filter=match activity=off listen=off routeSeen=no attach=2093ms source=automatic action=hold`
- D-POCO: `12:45:17.490 ... identity=normal ... activity=off listen=off routeSeen=yes attach=2025ms source=automatic action=hold`

`main` showed 2 of our dialogs per plug here (round 1). Operator count: 0 on all six plugs. There was no lasting grant on D-POCO, so no condition is INCONCLUSIVE.

## R5b

**PASS**

One cycle per host. 8 s after the plug, `ACTION_CHECK_USB`. Rule: OK on every dialog, no tick.

| Host | RX line | dec_A | src_A | dec_B (src) | req_A / req_B | perm_A / perm_B | confirm | ssl / ms / phone_acc | fatal |
|---|---|---|---|---|---|---|---|---|---|
| D-HP | `11:44:33.632` | hold, ask | automatic, user | ask (automatic) | 1 / 1 | 1 / 1 | 0 / 0 | 1 / 4 / 1 | 0 |
| D-POCO | `12:47:54.831` | hold, ask | automatic, user | ask (automatic) | 1 / 1 | 1 / 1 | 0 / 0 | 1 / 4 / 1 | 0 |

- D-HP: `11:44:33.632 AutomationReceiver: com.andrerinas.openheadunit.ACTION_CHECK_USB`, then `11:44:33.682 ... identity=normal ... source=user action=ask`, then `11:44:44.212 ... identity=accessory ... attach=1059ms source=automatic action=ask`.

Operator count: 2 dialogs, both ours "Allow" (D-HP); on D-POCO 2 dialogs, OK pressed, no tick box offered on them.

## R5t

**PASS**

One cycle per host, as R5b with one injected tap on the home's primary USB button (see Setup notes for the target).

| Host | tap row | dec_A / src_A | UsbPermissionAsks rows for A | dec_B (src) | req_A / req_B | perm_A / perm_B | ssl / ms / phone_acc | fatal |
|---|---|---|---|---|---|---|---|---|
| D-HP | `H-R5t-p2 TAP ... 11:49:47` | hold, ask / automatic, user | 1 (`11:49:47.712`) | ask (automatic) | 1 / 1 | 1 / 1 | 1 / 4 / 1 | 0 |
| D-POCO | `P-R5t-p1 TAP ... 12:49:22` | hold, ask / automatic, user | 1 (`12:49:21.459`) | ask (automatic) | 1 / 1 | 1 / 1 | 1 / 4 / 1 | 0 |

Operator count: 2 dialogs, both ours, on each host. The tap did not hit the node the brief names, so this run verifies the primary button's path into `onUsbClicked` and not the `usb_button` tile's listener.

## R5c

**PASS**

One cycle per host. 8 s after the plug, two `ACTION_CHECK_USB` in one shell call. Rule: OK on every dialog, no tick.

| Host | RX lines (gap) | dec_A | req_A / req_B | perm_A / perm_B | ssl / ms / phone_acc | fatal | open_at_ms / unfinished |
|---|---|---|---|---|---|---|---|
| D-HP | `11:52:08.012`, `11:52:08.582` (570 ms) | hold, ask, ask | 2 / 1 | 2 / 1 | 1 / 4 / 1 | 0 | 1 / 1 |
| D-POCO | `12:50:49.528`, `12:50:49.579` (51 ms) | hold, ask, ask | 2 / 1 | 2 / 1 | 1 / 4 / 1 | 0 | 1 / 1 |

The brief allows two dialogs for A (fix: a user press may ask over an open entry). Operator count: 2 on both hosts, host: 3 starts (A, A, B) with 2 finishes logged, one start still open when the first `Media Sink Setup Request` arrived. That disagreement is a finding, not a verdict: one dialog was not seen or not logged as finished. No crash and no ANR on either host.

## F4a

**REPORTED**

After `setkeys f4` (Listen on, `auto-start-on-usb` on, `reopen-on-reconnection` on), F4a after a Clear defaults, rule: touch nothing until LOOK, then answer in the chat, then Recents once. The first D-HP F4 (stage `H`) is void (see Setup notes); this is the `H4` run.

| Host | order of rows | main_after_confirm_ms | focus at LOOK | focus after the cycle | operator at LOOK | Recents | ssl | Android dialog finish |
|---|---|---|---|---|---|---|---|---|
| D-HP | CONFIRM_START A `12:23:10.452`, RAISE x3 and MAIN_START `12:23:10.522`, DEC A (`wait`) `12:23:12.612` | 70 | `MainActivity` | `MainActivity` | none visible | nothing | 0 | `12:24:49.762` |
| D-POCO | CONFIRM_START A `12:52:22.835`, RAISE x2 and MAIN_START `12:52:22.868`, DEC A (`wait`) `12:52:24.922` | 33 | `MainActivity` | `MainActivity` | none visible | nothing | 0 | `12:52:54.277` |

No `PERM_START` and no request on either host. Operator count over the whole plug: 0 dialogs on both. `focus.txt` agrees with the `START` order (`MainActivity` in front on both hosts; D-POCO did not leave `mCurrentFocus` stale this time). `RAISE` is `Reopen on reconnection: launching MainActivity (USB normal attach ...)` plus `Boot auto-start: launching ...` (direct on D-HP, overlay trampoline on D-POCO).

## F4b

**REPORTED**

F4b right after F4a (no default was stored by F4a, because nothing was answered), same rule.

| Host | order of rows | main_after_confirm_ms | focus at LOOK | operator at LOOK | Recents | ssl | Android dialog finish |
|---|---|---|---|---|---|---|---|
| D-HP | CONFIRM_START A `12:28:02.662`, RAISE and MAIN_START `12:28:02.742`, DEC A (`wait`) `12:28:04.812` | 80 | `MainActivity` | none visible | nothing | 0 | `12:28:35.082` |
| D-POCO | CONFIRM_START A `12:57:10.012`, RAISE and MAIN_START `12:57:10.038`, DEC A (`wait`) `12:57:12.085` | 26 | `MainActivity` | none visible | nothing | 0 | `12:57:32.329` |

Answer to the open question: `MainActivity` started 26 to 80 ms after Android's confirm dialog on all four cycles and the dialog could not be seen by the operator, in the foreground or in Recents, and no session formed in 150 s. The dialog's finish came 6 to 17 s after the LOOK cue in three of four cycles (D-HP F4a took 99 s), which fits the Recents press ending it, not proven. This is the case "an Android dialog hidden under a raised `MainActivity`" the brief named.

## Anything the brief did not ask about

- **A user on this build with "Start on USB" on and Listen on is stuck at the first plug.** Android's confirm dialog is covered by `MainActivity` 26 to 80 ms after it appears, on both hosts, and with no default stored there is no way to answer it; no session formed in any F4 cycle. That is the old behaviour of the raise, not of this candidate's change, but the candidate's `wait` (service stays out) now leaves nothing at all to answer. A fix is outside this branch.
- **Seed instructions are fragile.** "Open: OK, Allow: Cancel" works only if Android's "Open" dialog is answered before ours stacks over it 2 s later. The first stage's three seeds failed on that. A stored default from an earlier tick makes the seed free (Android starts our activity without a dialog), which the brief mentions and the rule contradicts ("tick nothing").
- **Stage cues need to be slower and carry the rule.** Four things went wrong in the first 40 minutes of the round for lack of that (missed UNPLUG cue, wrong F4 rule, a missed seed, a rule read late). The updated `cue()` is in `scripts/lib.sh`.
- **`stage.sh` bugs for the brief's next copy:** `SEED_FAILED` does not skip R4; the first cycle label reused after a restart poisons `since`; `timeline.sh` truncates `action=` at 240 characters; `writeprefs` takes arguments but round 1's does not; the LOOK cue needs a 45 s pause; `tapusb` finds no `usb_button` on API 17 or on this home layout.
- **R5c leaves one dialog start unfinished** on both hosts while a session formed. Which of the three it was is not decided here.
- **`USB_DEVICE_DETACHED` on this unit is the AOA re-enumeration**, so "unplug time from logcat" rarely arrives.
- **Evidence kept:** `grading-extract.txt` (every graded cycle's decision, request and verb lines), `cycles.tsv`, per-cycle `.timeline.tsv`, `H-*`, `H4-*` and `P-*` slices, instrument files, `defaults.tsv`, `focus.txt`, `taps.txt`, `marks.txt`, `hand-steps.log`, the three captures `H-hu.txt`, `H4-hu.txt`, `P-hu.txt`.
