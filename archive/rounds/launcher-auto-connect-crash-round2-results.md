# launcher-auto-connect-crash, round 2 results

**Candidate:** `fix/launcher-auto-connect-crash` @ `e5f818b8849904d5f26cfa76b498eca85594cbda` (2 commits on the base)
**Control:** the candidate plus `control.patch` (replay veto removed), reports `e5f818b88499-dirty`
**Baseline:** `main` @ `145a0c762f0a87386ca63b54518ea5e861b290be` (not built)
**APK md5:** candidate `4a99e2f92ca657f65086a717b15116f9` / control `1bab669446155a93bcf97116ef14f633`
**Unit:** D-HU (UNISOC MT50, Android 14) as head unit, D-POCO (POCO X3 NFC, Android 15, Gearhead 17.9.664004) as phone
**Date:** 2026-10-07

## Round summary

| Run | Verdict |
|---|---|
| R0 | PASS |
| R7-PC (control) | INCONCLUSIVE |
| R6B | FAIL (one clause: no recreate line within 75 s of the wake) |
| R6 | PASS (3 of 3 arrivals) |
| R7 (candidate) | INCONCLUSIVE |

## Setup notes

- Quirk files read: `rig-quirks/topics/tooling.md`, `rig-quirks/units/D-HU.md`, `rig-quirks/units/D-POCO.md`. Template read without 7b and 8.
- Pre-flight, `rig_preflight.sh D_HU:wifi,bt D_POCO:wifi,bt`: PREFLIGHT OK. D_HU wifi 1, bt 1, Awake; D_POCO wifi 1, bt 1, Awake.
- Section 0 reads: phone address `DC:B7:2E:5E:4E:59`; D-HU bond count 1; both `stat` lines `u0_a176:u0_a176` (no chown needed); Gearhead `17.9.664004-release`; D-MOTO plugged in.
- **D-MOTO airplane mode.** D-MOTO (`ZY22GC3BM4`) was plugged in, Bluetooth `state: OFF` and airplane flag 0 at the start. My first `airplane-mode enable` was refused by the session permission classifier, so the first part of the pre-flight and the unit-test and build gate ran with D-MOTO in its original state (Bluetooth already OFF). After the operator granted the command it was run: Bluetooth `state: OFF`, airplane flag 1, from about 12:24 onward. R7-PC, R6B, R6 and R7 all ran with D-MOTO in airplane mode. It was set back to airplane flag 0 at the end.
- Scripts: `build_hur_cool.sh`, `run_unit_tests.sh` (existing); `lacc2_lib.sh`, `lacc2_grade.py`, `control.patch` copied verbatim from the brief, `ohu_setkeys.py` copied from round 1. New in `hur-wifi-test-scripts/launcher-auto-connect-crash-round2/`: `lacc2_r7_session.sh` (R7 cycle, void rules, stop rule, heartbeat), `lacc2_r6_session.sh` (R6B then R6), `keys.env`, `restore2.sh`. No script needed fixing against the real line format.
- The host ran everything in the foreground itself, with no `rig-executor` agent. Builds and the two session scripts were started with `run_in_background` and watched by monitors.
- Build gate: 2775 tests, 0 failures, 0 errors, 0 skipped, counted from the JUnit XML; `WirelessSleepHoldTest` present. `git apply --check` passed; the worktree status after the patch was exactly ` M .../aap/AapService.kt`, and 0 files after the revert. DEX string `the held wireless bring-up is dropped, because`: candidate 1, control 0.
- Host thermal: each build waited until the package temperature read 70C or less. The first build's guard stopped the gradle tree at 89C and resumed at 68C (12:16:59 to 12:22:20). Run gates all started at 58C to 72C. `throttle_pkg` stayed at 690 for every run (delta 0); the per-run maximum was 72C (R7-PC), 65C (R6B), 66C (R6), 67C (R7). `th_report` printed an empty line inside the session script (the watcher was killed before its first sample was read), so the figures above come from the `.thermal` files read afterwards.
- Settings baseline `settings-backup-HU.xml` is byte-identical to round 1's, so the delta is zero. Keys written after each install (control, then candidate) and read back; `mirror_check` printed the MAC after the first launch on each APK. Final restore: the helper rewrote the file in a different layout (`cmp` differed at byte 37), so the backup was pushed as root and `cmp` then printed RESTORED.
- Identity: control `live.apk` md5 equals `lacc2-control.apk`, `ACTION_QUERY_STATE` commit `e5f818b88499-dirty`; candidate `live.apk` md5 equals `lacc2-candidate.apk`, commit `e5f818b88499`.
- The R7 session was launched at 13:00:39, one second before the R6 script wrote its last line (13:00:40). The overlap was R6's final `phone_restore`; R7 resets the phone radios before its first capture, so I do not think it mattered, but it is stated here.
- Another thread's `rig_thermal.sh watch` (pid 78277, `self-mode-vpn-release-round1`) was running throughout and was left alone.
- Windows are by shell markers, so the "old buffer first" caveat does not apply; every capture was cleared with `logcat -c` at its start.
- The D-HU log level was INFO (`log-level=2`) throughout.

## R0, build and identity

**PASS**

- 2775 tests, 0 failures, `WirelessSleepHoldTest` among them.
- `git apply --check` succeeded; the control built.
- md5 candidate `4a99e2f9...` and control `1bab6694...`, different.
- DEX check: candidate 1, control 0.

## R7-PC, positive control on the control APK

**INCONCLUSIVE**

Five non-void cycles, none void. The double rebuild was not reached in any of them.

| Cycle | bt_to_screen_s | bt_force_offsets_s | double_reached | short.init_native | short.dropped | wake_to_ssl_s |
|---|---|---|---|---|---|---|
| 1 | 0.080 | [] | false | 1 | 0 | 17.234 |
| 2 | 0.082 | [] | false | 1 | 0 | 11.483 |
| 3 | 0.078 | [] | false | 1 | 0 | 16.995 |
| 4 | 0.077 | [] | false | 1 | 0 | 17.690 |
| 5 | 0.082 | [] | false | 1 | 0 | 18.477 |

- Every `short` Bluetooth auto-start line was a veto, none a forced rebuild. `nothing to do, a handshake attempt is already in flight.` (c1, c4, c5); `nothing to do, a handshake is running on a group that is still up.` (c1, c3, c4, c5); `Native AA is armed with a live group, leaving it alone.` (c1, c3, c4, c5); `nothing to do, the network has been asked for and has not answered yet.` and `the Native AA group is still being created, so it is left to answer.` (c2).
- `short.dropped` is 0 in all five, as the control requires.
- Each cycle: `dark.held_rec` 1, `rearm_line` `WakeDetect: re-arming the wireless bring-up held while the unit was asleep (force=true, trigger=SCREEN_ON)`.
- Settings: the section 4 keys. Radios: `phone_wifi_off` then `phone_bt_set on`, read back with `dumpsys`; `screen_off` printed ASLEEP in all cycles.

This is the outcome section 3 predicted: a Bluetooth auto-start against an armed stack is always vetoed, so the control cannot produce the double rebuild on this rig.

## R6B, the armed stack's 60 s recreate in the dark

**FAIL**

One condition failed: with `decision` `dropped`, `screen_on_to_recovery_s` must be 75 or less and it is null. Every other condition held.

- Dark: `screen_off` 1; `group_ok` 0, `create_attempt` 0, `recreate` 0, `init` 0; `held_rec` exactly 1; `our_proc` 0. After: `rearm` exactly 1 with `force=true`; `decision` `dropped`; short `init_native` 0; all `group_ok` 1; phone `all.car` 0. Void check: none.
- Decisive lines (D-HU capture `R6B-c1.hu.logcat`):
  - 12:50:38.829 `WakeDetect: SCREEN_OFF`
  - 12:51:22.858 `WifiDirectManager: Native AA join watchdog fired but a Bluetooth handshake or handoff is in flight - deferring recovery.`
  - 12:52:22.864 `WifiDirectManager: Native AA recovery (no phone joined within 60s) held while the screen is off. The screen coming on re-arms the group.`
  - 12:52:52.454 `WakeDetect: re-arming the wireless bring-up held while the unit was asleep (force=true, trigger=SCREEN_ON)`
  - 12:52:53.989 `WakeDetect: the held wireless bring-up is dropped, because a handshake attempt is already in flight`
  - 12:53:22.866 the same watchdog `deferring recovery` line again, 30.4 s after the wake.
- Numbers: dark `match` 0, `poke` 0, `accept` 19, `defer` 1, `radio_hold` 0, `bt_auto_lines` none in dark or short; `screen_on_to_decision_s` 1.536; `screen_on_to_recovery_s` null. The capture window ends 80 s after the wake.

The re-armed watchdog did fire after the wake (12:53:22.866, +30.4 s) but deferred itself, because the phone's Bluetooth handshake loop (D-POCO Bluetooth on, WiFi off) kept a handshake in flight, so no `recovery (...): recreate attempt` or `held` line came inside the 75 s. The next fire would have been at about +90 s, outside the window. The clause as written looks only for a recovery line, and the grader's `recovery_any` does not match the defer line, so I graded the letter of the condition. Whether a deferred fire should count is a brief question; the fix 1 behaviour in the dark (held, no group, no recreate) was clean.

## R6, Bluetooth auto-start into a stopped service with the screen off

**PASS**

Three cycles, all with an arrival, none void, no extra cycles needed.

| Cycle | dark match | hold | hold_before_any_arm | dark init/create_attempt/group_ok/listen/poke/accept/wifi_enable | first_screen_on | rearm, force | screen_on_to_rearm_s | decision, screen_on_to_decision_s | wake_to_ssl_s | throughput after ssl | phone dark car / after car |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | 1 | 1 | true | all 0 | `screen was off for -1s` | 1, true | 0.001 | init_native, 1.682 | 9.742 | true | 0 / 91 |
| 2 | 1 | 1 | true | all 0 | `screen was off for -1s` | 1, true | 0.000 | init_native, 1.518 | 10.522 | true | 0 / 97 |
| 3 | 1 | 1 | true | all 0 | `screen was off for -1s` | 1, true | 0.001 | init_native, 1.737 | 8.024 | true | 0 / 103 |

- Also: dark `fgs_fail` 0, `create` 1, `our_proc` 0 in every window; `after.init_native` 1 in each cycle; `bt_auto_lines` for `after`: none in any cycle.
- 3 of 3 arrival cycles reached `SSL handshake complete`.

## R7, the double trigger on the candidate

**INCONCLUSIVE**

Every non-void cycle passes every condition (5 of 5), but the verdict rule needs R7-PC PASS and at least one R7 cycle with `double_reached` true. Neither held: R7-PC is INCONCLUSIVE and `double_reached` is false in all five cycles. So the veto itself was not exercised. What the data does show is that no cycle produced a second rebuild.

| Cycle | bt_to_screen_s | bt_force_offsets_s | double_reached | dark group_ok / recreate / held_rec / hold / defer | short.init_native | short.busy | quiet_host_min_gap_s | after.ssl | wake_to_ssl_s | phone dark / after car |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | 0.083 | [] | false | 0 / 0 / 1 / 0 / 0 | 1 | 0 | null | 1 | 17.312 | 0 / 106 |
| 2 | 0.077 | [] | false | 0 / 0 / 1 / 0 / 0 | 1 | 0 | null | 1 | 11.456 | 0 / 109 |
| 3 | 0.078 | [] | false | 0 / 0 / 1 / 0 / 1 | 1 | 0 | null | 1 | 18.654 | 0 / 112 |
| 4 | 0.086 | [] | false | 0 / 0 / 1 / 0 / 0 | 1 | 0 | null | 1 | 18.851 | 0 / 115 |
| 5 | 0.085 | [] | false | 0 / 0 / 1 / 0 / 1 | 1 | 0 | null | 1 | 12.300 | 0 / 118 |

- Every cycle: `rearm` exactly 1 with `force=true`; `decision` `init_native` (the first rebuild after the replay line, the Bluetooth auto-start's or the replay's); `our_proc` 0 in every window; `throughput_after_ssl` true.
- Short-window `bt_auto_lines` were all vetoes of the same kinds as R7-PC: `nothing to do, a handshake attempt is already in flight.` (c1, c3, c4), `... a handshake is running on a group that is still up.` (c1, c2), `... Native AA is armed with a live group, leaving it alone.` (c1, c2), `... the network has been asked for and has not answered yet.` and `... the Native AA group is still being created, so it is left to answer.` (c5).
- Largest `short.init_native` 1; total `short.busy` 0.

## Report-back figures (section 9)

1. R6B: dark `group_ok` 0, dark `recreate` 0, dark `held_rec` 1; `decision` after the wake `dropped` (handshake attempt already in flight).
2. R7: 5 of 5 non-void cycles passed; 0 of 5 cycles with `double_reached` true (so 0 with `drop_follows_force`); largest `short.init_native` 1; total `short.busy` 0.
3. R7-PC: 0 of 5 non-void cycles with `short.init_native` of 2 or more.
4. R6: 3 of 3 arrival cycles reached `SSL handshake complete`.

## Evidence

Captures, JSON, scripts and `control.patch` are in `launcher-auto-connect-crash-round2-captures.zip` on release `rig-evidence-launcher-auto-connect-crash`, sha256 `0845806ceb9f6ebc21576bb60bb4dd6bc9e67d2da0858eb6483d9a7ddc92f155` (21498122 bytes).

## Anything the brief did not ask about

- In both R7-PC and R7 the Bluetooth auto-start was a veto in every cycle (`nothing to do ...`) and no `forceRearmWireless=true` line appeared at all, because the stack was already armed or its group was being created when the phone arrived. As in round 1, a forced Bluetooth auto-start against an armed stack does not happen on this rig with this lever, so the double-trigger window is closed from the lever side. A lever that reaches `forceRearmWireless=true` inside 1.5 s of `SCREEN_ON` would need the stack to be not armed at the sleep, which is the R6 shape.
- In R6B the phone kept dialling Bluetooth every few seconds with its WiFi off (dark `accept` 19, `gh_wpp` 570 phone lines), which is what keeps the watchdog deferring. Any brief that wants to see the re-armed watchdog actually recreate has to stop the phone's Bluetooth dialling before the screen wakes, as R7 does.
- `lacc2_grade.py` `recovery_any` matches `Native AA recovery (` but not the `join watchdog fired ... deferring recovery` line, so a deferral shows as null in `screen_on_to_recovery_s`.
