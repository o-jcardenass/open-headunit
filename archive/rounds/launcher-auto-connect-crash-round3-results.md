# launcher-auto-connect-crash, round 3 results

**Candidate:** fork `fix/launcher-auto-connect-crash` @ `bbec49fc6573d54b6cf8b2b0688904e487dfb515`   **Baseline:** `main` @ `145a0c762f0a87386ca63b54518ea5e861b290be` (not built); control = round 2 APK (`e5f818b88499-dirty`)
**APK md5:** candidate `5c12c485a08d480ffd006636e0bd9bbf` / control `1bab669446155a93bcf97116ef14f633`
**Unit:** D-HU (UNISOC MT50, Android 14) with D-POCO (Gearhead 17.9.664004) as the phone; D-MOTO in airplane mode, not used
**Date:** 2026-10-07

## Setup notes

- Pre-flight (first run) `PREFLIGHT OK`: D_HU wifi 1 bt 1 Awake, HFP none, A2DP none; D_POCO wifi 1 bt 1 Awake. Phone address `DC:B7:2E:5E:4E:59`; D-HU bond count 1; `shared_prefs` and `user_de` `shared_prefs` both `u0_a176:u0_a176`; D-MOTO plugged in (1). A later pre-flight once printed FAILED because its stray-logcat check matched the host's own command line; the immediate re-run was `PREFLIGHT OK` with no logcat running.
- Quirk files read: `topics/tooling.md`, `topics/lifecycle.md`, `units/D-HU.md`, `units/D-POCO.md`. Template read without 7b and 8.
- D-MOTO: `airplane-mode enable` set the flag (`airplane_mode_on` 1) but `dumpsys bluetooth_manager` still read `state: ON`. I ran `svc bluetooth disable` on D-MOTO (read back `state: OFF`); this is a deviation from the brief. At the end D-MOTO airplane mode was disabled and its Bluetooth enabled (`state: ON`).
- Control: the round 2 APK, md5 matched, not rebuilt. `install -r -d` succeeded. Live md5 equals the APK; `ACTION_QUERY_STATE` commit `e5f818b88499-dirty`. Candidate: `install -r`, live md5 equals the APK, commit `bbec49fc6573`. Settings were re-written after each install and `mirror_check` printed the MAC both times.
- Settings delta against round 2's backup: serialization only (`<string name=".." />` instead of `<string ..></string>`, XML declaration form). No key differs.
- Scripts used: `build_hur_cool.sh`, `run_unit_tests.sh`, `rig_preflight.sh`, `rig_thermal.sh`, and in the round folder `lacc2_lib.sh`, `ohu_setkeys.py` (copied unchanged from round 2). New this round: `lacc3_inject.sh`, `lacc3_inject_kill.sh`, `lacc3_extra.sh`, `lacc3_grade.py` (all verbatim from the brief), plus host-written `lacc3_r8_session.sh` (R8-PC and R8 loop with the void and stop rules) and `lacc3_r6_session.sh` (R6B then R6). Sessions ran as the host's own background scripts, not through an executor agent; the host graded from the JSON.
- `ACTION_LOG_MARKER` not used. Log level INFO (`log-level=2`).
- Grading note: `first_screen_on` from the grader carries the prefix `[2] 1.onReceive | `; the message itself equals `WakeDetect: SCREEN_ON (screen was off for -1s)`, which is what the R6 condition requires. A first host check that compared the whole string failed on the prefix only.
- Thermal: every run `throttle_delta=0`, `thermal_max` 62 to 67C. No run voided.
- Inject check on the control: `INJECT_OK`, line `AapService: Bluetooth auto-start: nothing to do, a session is already up.`, `am` printed `Starting service: Intent { act=...ACTION_BT_AUTO_START ... }`, no error.

## R0

**PASS**

- Candidate: 2783 tests, 0 failures, 0 errors, 0 skipped, counted from the JUnit XML; `WirelessSleepHoldTest` present.
- md5s differ (above). DEX check: candidate `1 1`, control `0 0`.

## R8-PC

**PASS**

3 cycles run, 3 non-void, 0 void. In each, the injected forced start preceded a `reentry` line (the control's signature); `start_drop` 0, `dropped` 0, `short.init_native` 1.

| Cycle | inject_s | rearm_to_inject_s | seq_short | second_forced |
|---|---|---|---|---|
| c1 | 0.072 | 0.071 | bt_force 0.072, init_native 0.075, reentry 1.537 | true |
| c2 | 0.208 | 0.207 | bt_force 0.208, init_native 0.210, reentry 1.698 | true |
| c3 | 0.203 | 0.202 | bt_force 0.203, init_native 0.205, reentry 1.694 | true |

Each cycle: dark `match` 1, dark `hold` 1, `after.ssl` 1, `phone.dark.car` 0. Inject line in all three: `AapService: Bluetooth auto-start: BtAutoStartActions(clearUserExit=true, forceRearmWireless=true, armWirelessIfIdle=false)`. The second forced bring-up was a `reentry` entry, never a second `init_native`.

## R6B

**PASS**

- `decision` `dropped`: `WakeDetect: the held wireless bring-up is dropped, because a handshake attempt is already in flight`, at `screen_on_to_decision_s` 1.526.
- `screen_on_to_recovery_s` 30.261, `recovery_line`: `WifiDirectManager: Native AA join watchdog fired but a Bluetooth handshake or handoff is in flight — deferring recovery.` This is the amended clause.
- Dark: `screen_off` 1, `held_rec` 1, `recreate` 0, `group_ok` 0, `create_attempt` 0, `init` 0, `match` 0, `poke` 0, `accept` 19, `defer` 1, `radio_hold` 0. After: `rearm` 1, `force=true`, `start_drop` 0. Short `init_native` 0. All `group_ok` 1. `our_proc` 0 in every window. `phone.all.car` 0. No `bt_auto` lines in dark or short.

## R6

**PASS**

3 cycles, 3 arrivals, 0 void. Every cycle: dark `match` 1, `hold` 1 with `hold_before_any_arm` true, `fgs_fail` 0, dark `init`/`group_ok`/`poke`/`accept`/`wifi_enable` 0; `first_screen_on` `WakeDetect: SCREEN_ON (screen was off for -1s)`; `rearm` 1 with `force=true`; `after.start_drop` 0; `after.init_native` 1; `after.already_init` 0; no `bt_auto` lines in `after`.

| Cycle | screen_on_to_rearm_s | screen_on_to_decision_s | wake_to_ssl_s |
|---|---|---|---|
| c1 | 0.001 | 1.742 | 9.736 |
| c2 | 0.001 | 1.673 | 9.852 |
| c3 | 0.001 | 1.528 | 9.537 |

`throughput_after_ssl` true and `phone.after.car` at least 1 in all three (SSL reached 3 of 3 arrival cycles); `phone.dark.car` 0; `our_proc` 0.

## R8

**PASS**

5 cycles, 5 non-void, 0 void. All 5 pass, all 5 `r8_order_ok` true. R8-PC is PASS, so the verdict is PASS.

| Cycle | inject_s | rearm_to_inject_s | seq_short | start_drop to already_init (s) | wake_to_ssl_s |
|---|---|---|---|---|---|
| c1 | 0.189 | 0.188 | bt_force 0.189, init_native 0.191, start_drop 1.676, already_init 1.678 | 0.002 | 13.589 |
| c2 | 0.206 | 0.205 | bt_force 0.206, init_native 0.209, start_drop 1.691, already_init 1.692 | 0.001 | 7.001 |
| c3 | 0.244 | 0.243 | bt_force 0.244, init_native 0.246, start_drop 1.729, already_init 1.731 | 0.002 | 6.299 |
| c4 | 0.082 | 0.081 | bt_force 0.082, init_native 0.084, start_drop 1.545, already_init 1.547 | 0.002 | 7.789 |
| c5 | 0.196 | 0.195 | bt_force 0.196, init_native 0.199, start_drop 1.677, already_init 1.679 | 0.002 | 13.304 |

Ranges: `inject_s` 0.082 to 0.244; `rearm_to_inject_s` 0.081 to 0.243. Largest `short.init_native` 1; total `short.reentry` 0; `dropped` 0, `busy` 0, `qh_reentry` 0; `quiet_host_min_gap_s` null. Every cycle: dark `match` 1, `hold` 1, `init`/`group_ok`/`create_attempt` 0, `after.ssl` 1, `throughput_after_ssl` true, `phone.dark.car` 0, `phone.after.car` 147 to 171, `our_proc` 0. The `am` output of all 5 injections was `Starting service: Intent { act=com.andrerinas.openheadunit.ACTION_BT_AUTO_START ... }` with no error. The `start_drop` line appears 1.545 to 1.729 s after `SCREEN_ON`, i.e. when the replay's settle ends, as the change specifies.

## Anything the brief did not ask about

- The control and the candidate differ in exactly the predicted line: control logs `reentry` at 1.537 to 1.698 s; candidate logs `start_drop` then `already_init` at 1.545 to 1.731 s. `init_native` is 1 in both, so the count alone does not separate them, as the brief warned.
- R6B reached the watchdog line on its deferral path, 30.261 s after the wake; the amended clause needed no recreate.
- Item 2 of the change (a stop ends the user's arming) has no direct run; R6B and R6 showed nothing else moved.
- Evidence: release asset `launcher-auto-connect-crash-round3-captures.zip` of `rig-evidence-launcher-auto-connect-crash` sha256 `22c858fb79e4cf9b64abdb9c15846af1a2221298d78a2049072392913b27ef11`.
