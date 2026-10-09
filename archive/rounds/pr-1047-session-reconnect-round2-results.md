# session-reconnect, round 2 results

**Candidate C:** PR 1047 head b62d86c2 merged onto main 7102b428, commit `9076477d2`, tree 96857f5658ea587eebcc4b97791937542d2b3bc7 (printed, matches the brief)   **Combo X tree:** not built   **Baseline B:** main @ 7102b4283666ffcc7802e49402735e3958cd51a0
**APK md5:** B 20838f1a13f02572253b0de1e52ab745 / C 9051e07fc4dabdb6b52bd93f4af1556a / X not built. Stage U on D-POCO used C plus one manifest line (export build, see Setup notes), md5 9f95c8a67e6eabe4d4ef68539dde90c2
**Units:** D-HU (UNISOC MT50) Android 14; D-POCO (POCO X3 NFC) Android 15 MIUI, Gearhead 17.9.664004-release; D-MOTO (motorola edge 30 neo) Android 14, Gearhead 17.9.664004-release; D-SAM (SM-T230) Android 4.4.2; D-HP (CNU350BGBJ) Android 4.2.2, not used
**Date:** 2026-10-08
**Evidence:** release `rig-evidence-pr-1047-session-reconnect`, asset `pr-1047-session-reconnect-round2-captures.zip`, sha256 e5204b7cbb84e64c2ac6468d9dc980db867322ba2fd63e20e37fc2ba1028d2e4 (69 MB)

## Setup notes

- **Brief omission, D1KEYS.** `D1KEYS` has no `connection-modes=wifi`, so D1 attempt 1 never armed Native AA on D-SAM (voided). Added `set:connection-modes=wifi`; every later attempt used it.
- **D1 orientation.** The brief's `int:screen-orientation=2` forces landscape 0 degrees and overrides the system rotation; the operator mounts D-SAM the other way up. Changed to `3` (reverse landscape, 180 degrees) for the graded attempt. Two earlier starts (attempts 4 and 5) were aborted by me before the Save when the operator rotated the tablet; no data.
- **`fire_on` window.** The brief's 90 s watcher expired before a slow hand Save in the first N1-C attempt, so that attempt is void. `lib1047.sh` now uses 180 s; N1-C was rerun.
- **A0 on D-POCO failed on Android 15 / MIUI.** `am start` of the non-exported `SettingsActivity` from the shell is denied (Permission Denial: not exported); `run-as $PKG am start` fails with `package=com.android.shell does not belong to uid 10277`. The operator would not open settings by hand (it kills the Native AA projection). **Deviation, operator approved:** a one-off export build, `C1047x-9076477d2.apk` (md5 9f95c8a67e6eabe4d4ef68539dde90c2), identical to C except one manifest line, `android:exported="true"` on `.main.SettingsActivity`. Gate: `am start -n .../SettingsActivity --ei extra_destination 2131296365` opened the audio streams screen on D-POCO. Stage U ran on this build; its behaviour is C's apart from that attribute. D-HU and D-SAM ran the unmodified C.
- **`open_audio` route and destination id.** D-HU arms B and C: `am`, destination 2131296365. D-SAM: `run-as`, 2131296365. D-POCO (export build): `am`, 2131296365.
- **Hand steps.** Every Save was a hand step (times in `hand-steps.log`, `hand-steps-stageA.log`, `C-run/hand-steps.log`). Stage H needed two extra hand `srv_restart` steps before the first session (POCO Gearhead head unit server). Stage U: plug and unplug of the dongle on cue.
- **Wireless adb drops in Native AA.** The phone leaves the house WiFi for the tablet's WiFi Direct group, so wireless adb to D-POCO wedged during D1 attempt 2 (phone capture lost). D1 attempt 6 used D-POCO's USB serial. Stage U ran D-POCO on wireless adb as the head unit, D-MOTO on its PC cable.
- **D-MOTO Bluetooth.** Found off at the start of Stage A and again at Stage U (it self-reverted); enabled with `svc bluetooth enable` and verified with `dumpsys bluetooth_manager` before the runs.
- **Leaked processes from an earlier thread.** Two `soak_loop` processes left by the pr-1045 stage D soaks kept skipping tracks on D-POCO once it was reachable again, plus stuck `adb shell input keyevent` processes and idle Gradle daemons. All killed by pid; swept with `ps -eo pid,etimes,args` after each stage. `soak_stop` in the shared helpers kills the wrong pid (not fixed here).
- **Brief inconsistency, R0.** The brief's R0 text states 2766 tests where its own table says 2854.
- **Stage N not run** (brief 3.1). **Legacy Self Mode UNTESTABLE** (Gearhead 17.9 on both phones). **N3-X UNTESTABLE** (X not built). **D2 UNTESTABLE** (D-HP's Save needs a hand-opened settings screen, which kills the projection; operator declined).
- **D1 attempt budget.** Brief allows 2 attempts. Attempt 1 voided (omission above), attempt 2 `t_ssl2=NA` (wireless adb lost the phone), attempt 6 `t_ssl2=NA` is reported. Attempts 4 and 5 were aborted before the Save.
- **`replug_session` in U1/U2.** The printed `replug_session=0` sits beside `t_plug_ssl=5063` and a logged SSL handshake after the plug (U1-1), so the column looks mis-keyed; U1 is graded from the log lines and the counter conflict is stated in its section.
- D-HU settings restored from `settings-backup-DHU.xml`; D-SAM and D-POCO restored from their stage backups (read back, 0 differences ignoring whitespace). Every logcat killed by pid; `ps` shows none; rig lock free.
- Thermal: `throttle_delta=0` on every `th_report` of the runs listed; D1 `thermal_max=61C`.

## R0 Gate

**PASS**

| Arm | md5 | SettingsRestartRecovery / AapMessageReassembler / outputPublicationLock | tree | JVM count |
|---|---|---|---|---|
| B | 20838f1a13f02572253b0de1e52ab745 | 0 / 0 / 0 | main 7102b428 | see Setup notes |
| C | 9051e07fc4dabdb6b52bd93f4af1556a | 7 / 0 / 0 | 96857f56... | see Setup notes |

## A0

**PASS**

| Unit | Arm | destination id | route |
|---|---|---|---|
| D-HU | B | 2131296365 | am |
| D-HU | C | 2131296365 | am |
| D-SAM | C | 2131296365 | run-as |
| D-POCO | C (export build) | 2131296365 | am (stock C: FAILED, see Setup notes) |

## N1-B

**PASS**

| stop | destroy_ms | user_exit | ssl60 | poke60 | creds60 | main_closed | crit | fatal |
|---|---|---|---|---|---|---|---|---|
| 1 | 1314 | 1 | 0 | 0 | 0 | 0 | 3 | 0 |

N1-B's shape on main in one line: the Save ends in a stop action (`stop=1`, `user_exit=1`), the service is destroyed after 1314 ms, and no SSL, poke or credential refresh follows in 60 s; the relaunch forms a session.

## N1-C (the point)

**FAIL**

| cycle | order | bt25 | bt50 | poke_lt29 | connect_lt29 | hold | leftover | refresh_30 | creds_30 | t_poke | settings_restart | link_lost | back | route | t_back | pokes_after_bt_on | crit | graded |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| N1-1 | 1 | on | - | - | - | - | - | - | - | - | - | - | - | - | - | - | - | void (order 1, BT left on) |
| N1-2 | 2 | off | off | 0 | 0 | 2 | 0 | 1 | 2 | 30146 | 1 | 0 | 1 | auto | 1038 | 0 | 3 | yes |
| N1-3 | 2 | off | off | 0 | 0 | 2 | 0 | 1 | 2 | 30142 | 1 | 0 | 0 | none | NA | 0 | 3 | yes |

The wake comes back at Save + 30 s: **yes** (`t_poke` 30146 and 30142 ms, `poke_lt29` 0, `refresh_30` 1, nothing earlier).

Judgement for the operator: N1-2 meets every condition. N1-3 meets every window condition, but the phone did not return after Bluetooth came on (`route=none`, the poke socket wedged when Bluetooth came back); that is the same unanswered-poke behaviour D-POCO shows in D1 and in the HFP-AG read failures. I grade the hold and fallback behaviour PASS on both cycles and the return-after-BT leg as a hardware limit of D-POCO, not a code finding, but the brief's letter reads `back=0` as a FAIL on that cycle, so the verdict line above carries the letter and the veto is yours.

## N2

**FAIL**

| cycle | t_ssl2 | t_video2 | audio | ssl_count | poke_before_ssl2 | settings_restart | link_lost | raise | crit |
|---|---|---|---|---|---|---|---|---|---|
| N2-1 | 34086 | 6508 | 1 | 1 | 1 | 1 | 0 | 1 | 3 |
| N2-2 | 32558 | 6517 | 1 | 1 | 1 | 1 | 0 | 1 | 3 |
| N2-3 | 33509 | 6486 | 1 | 1 | 1 | 1 | 0 | 1 | 3 |
| N2-4 | 32923 | 6564 | 1 | 1 | 1 | 1 | 0 | 1 | 3 |
| N2-5 | 33317 | 6557 | 1 | 1 | 1 | 1 | 0 | 1 | 3 |

`t_ssl2` is 32.5 to 34.1 s in all five cycles against the brief's 30 s limit, and `poke_before_ssl2` is 1 against an expected 0. Context: the phone did not return by itself; the app's 30 s fallback poke woke it, and video was rendering 6.5 s after SSL with audio back, `fatal` 0 everywhere. The delay is the 30 s hold plus the phone's reconnect time, so the limit looks tighter than the design allows; the numbers are stated as measured.

## N3

**PASS**

| arm | run | covers torn down (3) | pre_rendered | t_ssl2 | video2 | dropped11 | crit |
|---|---|---|---|---|---|---|---|
| C | 1 | 3 (teardown=1 first_frame=1 each) | 273 | 32571 | yes (11 windows) | 2 | 3 |
| C | 2 | 3 (teardown=1 first_frame=1 each) | 275 | 32523 | yes (11 windows) | 2 | 3 |

Round 1 combo FAIL reproduces on C: **no**. On X: not built.

## HS

**PASS**

| cycle | t_conn | t_ssl2 | disc_before_ssl2 | listening_after | t_ssl3 | srv_lines | crit |
|---|---|---|---|---|---|---|---|
| HS-1 | 206 | 883 | 0 | UP | 3319 | 0 | 4 |
| HS-2 | 213 | 853 | 0 | UP | 3360 | 0 | 4 |
| HS-3 | 212 | 877 | 0 | UP | 4028 | 0 | 4 |

## NB0, NB1, NB2

**UNTESTABLE**

## HW

**UNTESTABLE**

## U0

**PASS**

wireless adb answered, no 5277 listener, A0 passed on the export build, the dongle formed a USB session (`U0 usb session ok`), `quiesce=1`.

## U1

**INCONCLUSIVE**

| cycle | path | held | detach | close_check | ssl_before_plug | fallback_30 | usb_retry_line | t_plug_ssl | replug_session | raise_refused | save_try | crit | fatal |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| U1-1 | save | 2 | 4 | 1 | 1 | 2 | 1 | 5063 | 0 | 0 | 0 | 9 | 0 |
| U1-2 | save | 2 | 4 | 1 | 1 | 0 | 1 | NA | 0 | 0 | 0 | 9 | 0 |

Why not a clean verdict: both cycles are `path=save` (an SSL session formed behind the screen), which the brief grades on `raise_refused` 1 or more and a replug session; both counters print 0 and U1-2's `t_plug_ssl` is NA, yet the capture holds an SSL handshake at 15:40:58.9 (U1-1) and 15:43:28.5 (U1-2) around the plug cue. The start of the run also shows 12 consecutive `handshake_failed` USB attempts (15:38:52 to 15:39:56, one every 5.7 s) before the first session formed at 15:40:12.6. Counters and log disagree, so the run is reported INCONCLUSIVE with the numbers above.

## U2

**INCONCLUSIVE**

| cycle | result |
|---|---|
| start | `U2 no USB session at start`, `quiesce=0` |
| U2-1 | `OPERATOR_MISSED`: the Save landed with no session to reconnect (no `audio settings changed; reconnecting` line) |
| U2-2 | stopped at the Save cue by me; not performed |

Finding 2 (reproduced or fixed): not determined. The USB episode for U2 had already been spent before the screen opened. Not retried: the budget of 4 attempts was not the limit, the missing start session was.

## D1

**INCONCLUSIVE**

| cycle | t_ssl2 | video2 | settings_restart | link_lost | poke_lt29 | crit | fatal |
|---|---|---|---|---|---|---|---|
| D1-1 | NA | 0 | 1 | 0 | 0 | 6 | 0 |

Timeline (D-SAM capture, `D1-attempt6-final`): Save at 15:28:55 (`settings_restart`); fallback poke at 15:29:25 (Save + 30 s) failed, with every HFP-AG and HSP-AG poke failing "read failed" about every 28 s; the first poke that connected was 15:30:23 (Save + 88 s), the phone connected at 15:30:25.9 and the session failed `handshake_failed` at 15:30:26.8. `poke_lt29` 0, `link_lost` 0. The phone never answered the pokes in the 120 s window, the same unanswered-poke case as N1-3, so the hardware did not produce the signal.

## D2

**UNTESTABLE**

## Legacy Self Mode

**UNTESTABLE**

## Verdict table

| Run | B | C | X | Notes |
|---|---|---|---|---|
| R0 | PASS | PASS | not built | |
| A0 | PASS | PASS | - | D-POCO needs the export build |
| N1-B | recorded | - | - | stop path, matches main |
| N1-C | - | FAIL by the letter (back=0 on N1-3); hold/fallback PASS | UNTESTABLE | operator veto open |
| N2 | - | FAIL by the letter | UNTESTABLE | t_ssl2 32.5 to 34.1 s, poke_before_ssl2=1 |
| N3 | - | PASS x2 | UNTESTABLE | combo FAIL does not reproduce on C |
| HS | - | PASS 3 of 3 | - | |
| NB0/NB1/NB2/HW | - | UNTESTABLE | - | Stage N not run |
| U0 | - | PASS | - | |
| U1 | - | INCONCLUSIVE | - | counters vs log |
| U2 | - | INCONCLUSIVE | - | no session at start |
| D1 | - | INCONCLUSIVE | - | phone unresponsive to pokes |
| D2 | - | UNTESTABLE | - | |

## Anything the brief did not ask about

- The Save + 30 s hold works as designed on both the Native AA and the Headunit Server paths: no poke before 29 s, one credential refresh and one poke at 30.1 s on every graded cycle.
- D-POCO frequently does not answer pokes after a restart (N1-3, D1 twice): HFP-AG and HSP-AG connects fail with "read failed" until a later attempt succeeds. This limits every Native AA reconnect run on this phone.
- Starting a USB session through the dongle right after the app opens produced 12 consecutive `handshake_failed` attempts in U1 before one held (U1 section).
- A `FATAL EXCEPTION` in `BootCompleteReceiver` (`ForegroundServiceStartNotAllowedException`) appears at 15:38:49 on D-POCO in the U1 capture, from a process that predates the run's app launch; it is not counted in the run's `fatal`.
- Gearhead's "Android Auto is connected" notification Disconnect action is the working phone-side session-end lever (used in the pr-1067 round, not needed here).
