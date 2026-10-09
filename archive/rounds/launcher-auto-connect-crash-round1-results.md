# launcher-auto-connect-crash, round 1 results

**Candidate:** `fix/launcher-auto-connect-crash` @ `0b8e7d59188c6d9358e5ab5a930723d1fb105520`   **Baseline:** `main` @ `145a0c762f0a87386ca63b54518ea5e861b290be` (not built; each run carries its own control)
**APK md5:** `423b6592ff5c04c3e551bb54c81fa1b7` (candidate, `com.andrerinas.headunitrevived_3.5.0-beta3_debug.apk`), baseline not built
**Unit:** D-HU (UNISOC MT50, Android 14, adbd uid 0) as head unit and as HOME for R5; D-POCO (POCO X3 NFC, Android 15, Gearhead 17.9.664004-release) as phone. D-MOTO was plugged in and was not touched (see Setup notes).
**Date:** 2026-10-07

## Round summary

| Run | Verdict |
|---|---|
| R0 build, tests, identity | PASS |
| R6-PC | PASS |
| R6 (attempt 2; attempt 1 void) | PASS |
| R6B | FAIL |
| R5-PC | PASS (with a brief defect, below) |
| R5 | PASS (with a brief defect, below) |

Report-back items from section 9 of the brief:

1. R5: 5 of 5 dark-start cycles with 0 `init`, 0 `group_ok`, 0 `poke` and exactly 1 `hold` in the dark window. Largest `screen_on_to_init_native_s` is 1.521.
2. R6: 3 of 3 arrival cycles held in the dark and rebuilt with `force=true` and reached `SSL handshake complete`. Largest `wake_to_ssl_s` is 23.649.
3. R6B: `group_ok` in the dark is 1 (a no-join recovery recreate, not the Bluetooth arrival). `bt_auto_lines`: `nothing to do, a handshake attempt is already in flight.` (4 times).

## Setup notes

Pre-flight (`rig_preflight.sh D_HU:wifi,bt D_POCO:wifi,bt`, PREFLIGHT OK):

```
ROLE     ADB      WIFI   BT     SCREEN    BT-PROFILES
D_HU     ok       1      1      Awake     HFP:none A2DP:none
D_POCO   ok       1      1      Awake     HFP:XX:XX:XX:XX:33:59 A2DP:up
```

Section 0 values: phone Bluetooth address `DC:B7:2E:5E:4E:59`; D-HU bond to it present (1 match); stock HOME holder `com.android.launcher3` (resolves to `com.zqc.launcher.HomeActivity`); `shared_prefs` owner for both `/data/data/<pkg>` and `/data/user_de/0/<pkg>` is `u0_a176:u0_a176` (correct, writable by the app). D-MOTO (`ZY22GC3BM4`) is in `adb devices`; I did NOT put it in airplane mode, which the brief asks for when it is plugged in. Its state was not read, so I cannot say it did not interfere; no log line in the D-HU captures names it.

Quirk files read: `rig-quirks/topics/tooling.md`, `units/D-HU.md`, `units/D-POCO.md`, `topics/lifecycle.md`, and a grep of `topics/bt.md` and `topics/wifi.md` for the Bluetooth self-revert and airplane entries.

Settings: backup `settings-backup-HU.xml` diffed against `projection-teardown-and-relays-round2/settings-backup-HU.xml`. Delta: `native-aa-wake-armings-without-session` 1 to 2, `wifi-direct-last-group-bssid` and `wifi-direct-readback-bssid` `CA:90:3A:45:6A:C6` to `BE:16:99:F1:6A:E3`. Nothing the round depends on. Keys written per the brief via `hu_put` (R6 keys with `enable-car-launcher=false`, R5 keys with `true`), read back before every launch. At the end the file was restored byte-identical (`cmp` clean).

Scripts: new folder `hur-wifi-test-scripts/launcher-auto-connect-crash-round1/` with `ohu_setkeys.py` (copied from `projection-teardown-and-relays-round1/`), `lacc_lib.sh`, `lacc_grade.py` (verbatim from the brief), plus `lacc_env.sh`, `launch.sh`, `session_R6PC.sh`, `session_R6.sh`, `session_R6B.sh`, `session_R5.sh`, `end_round.sh`. Existing scripts used: `rig_preflight.sh`, `rig_thermal.sh`, `build_hur_cool.sh` (wraps `build_hur.sh`), `run_unit_tests.sh`. Each session ran on the host under `flock /tmp/ohu-rig.lock`, started with `run_in_background`, with a heartbeat in `evidence/.../<run>.status`. No executor agent was used; the host ran every session script itself.

Deviations and errors, all of them:

1. **`su -c` does not exist on D-HU.** adbd already runs as uid 0 and there is no `su` binary (`su: inaccessible or not found`). The brief's `su -c '...'` wrappers in `hukill`, `mirror_check` and `hu_put` were replaced with plain shell commands. Nothing else changed in those helpers.
2. **Build.** `build_hur_cool.sh` (thermal-guarded) instead of `--max-workers=2`. The first build failed at once on a missing `local.properties` in the scratch worktree (untracked file); I copied it from the main checkout. The guard stopped the Gradle tree once at 87C and resumed at 67C; the thermal log peaked at 91C with `throttle_pkg` delta 0. APK copied out of `apks/` at once.
3. **Stray-logcat pre-check bug.** My first R6-PC launch aborted on its own pre-check (`ps aux | grep -c logcat` matched the launcher shell whose command text contained the word). No device command had run. The check was changed to `pgrep -x adb -a | grep -c ' logcat '`. Twice a session launch right after another session returned `flock` rc=1 with no output (the lock was still held for a moment); relaunching worked.
4. **Thermal gate was a no-op.** `th_pkg` in the brief's `lacc_lib.sh` prints two numbers when `rig_thermal.sh status` is parsed (`pkg=` also matches `throttle_pkg=`), so `th_wait` raised `integer expression expected` and never waited. Found in the R5 session log, fixed in `lacc_lib.sh` afterwards. Every run's thermal log still shows `thermal_max` 63C to 71C and `throttle_delta=0`, so no run met the 90C or throttle re-run rule.
5. **R6 attempt 1 is void (setup defect, kept in `R6-attempt1-void/`).** In all 5 cycles the phone's Bluetooth read `state: ON` after `phone_air_on` (R6-PC had read `OFF`), so `phone_bt_on` changed nothing, no ACL link formed, and `MATCH!` appeared 0 times (`NO_ARRIVAL` x5, `AutoStartReceiver` 0 hits). That is the brief's INCONCLUSIVE route, but the cause was the precondition, so I repaired the lever and re-ran: added `phone_bt_off` (`svc bluetooth disable`, read back `OFF` with `dumpsys`, up to 3 tries, cycle UNTESTABLE if it fails) after `phone_air_on`. Attempt 2 read `phone BT OFF (try 1)` in every cycle. The same gate was added to R6B, whose brief text does not turn phone Bluetooth off either.
6. **R6B launch order.** I started the capture before launching the app and waited for `createGroup SUCCESS` from the capture start, instead of launching first; it avoids a gap before the arm lines.
7. **`group_ok >= 1` in R5-PC and R5 cannot hold on this rig (brief defect).** The P2P group survives the process kill. The new process logs `a group named DIRECT-QS-MT50YT610E4GFPSLU is already up from before this bring-up; reading it` and then `Group formed. Owner: true`, with no `createGroup`. So `group_ok` and `create_attempt` are 0 in the after window and in R5-PC, with `main` as much as the candidate. I graded on `WifiDirectManager: Group formed` in the after window (R5: 1 to 2 per cycle) and say so. If the reviewer wants the literal clause, those two runs read FAIL on that one condition.
8. **Self-inflicted wake in R5 and R5-PC.** `MATCH! Starting AapService via Bluetooth Auto-start...` appears once per R5-PC cycle and once in each R5 after window, with `Bluetooth auto-start: nothing to do, a handshake attempt is already in flight.`; it is our own poke's ACL_CONNECTED. D-POCO's Bluetooth was on during R5 (left on after R6B; the brief only says airplane mode). The dark windows have `match=0`. The discard rule is not applied because the arrival did nothing (no recreate, no second group); flagged for the reviewer.
9. **How each R5 process started.** R5-PC: Android restarted the process itself inside 8 s (no `home-intent` marker). R5: in all 5 cycles the 8 s wait ended and the `home-intent` marker is present, so the HOME intent started the process (`Start proc ... for top-activity`). Android did not relaunch HOME by itself while the screen was off.
10. After the round, the candidate APK stays installed. The `CarLauncherAlias` component was left as the last launch set it; settings carry `enable-car-launcher=false` again, so the next launch turns it off.

## R0 build, unit tests, identity

**PASS**

- Build: `build_hur_cool.sh` exit 0, candidate commit string `0b8e7d59188c`.
- Unit tests, counted from the JUnit XML: 2762 tests, 0 failures, 0 errors, 0 skipped; `WirelessSleepHoldTest` present.
- Identity: `md5sum` of the named APK and of the pulled `live.apk` are both `423b6592ff5c04c3e551bb54c81fa1b7`. `WirelessSleepHold` appears 3 times and `ScreenPower` 4 times in the DEX strings. `ACTION_QUERY_STATE` reply: `"commit":"0b8e7d59188c"`, `versionCode 116`, `wifiMode NATIVE`.
- `adb install -r` left `settings.xml` byte-identical to the backup.

## R6-PC positive control, Bluetooth auto-start into a stopped service, screen on

**PASS**

- Settings written: R6 keys, `enable-car-launcher=false`. Mirror check printed the MAC.
- Radio state: phone airplane mode on, phone Bluetooth `OFF` (read from `dumpsys`) before the trigger, then `svc bluetooth enable`.
- Discard-rule check: `MATCH!` is the trigger, otherwise clean. Thermal max 65C, delta 0.
- Decisive lines: `08:38:19.765 AutoStartReceiver.onReceive | MATCH! Starting AapService via Bluetooth Auto-start...` then `08:38:19.992 WifiLauncherManager.setActive | WifiLauncher: Initializing WiFi Mode: NATIVE`.
- Numbers: `match` 1, `fgs_fail` 0, `hold` 0, `match_to_init_native_s` 0.227, `group_ok` 1, `poke` 1, `accept` 6. Phone: `phone.all.car` 0, `gh_wpp` 118 (not graded).

## R6 Bluetooth auto-start into a stopped service while the screen is off, 3 cycles

**PASS**

Attempt 1 (5 cycles, no arrival, phone Bluetooth left on) is void, see Setup note 5. Attempt 2:

| Cycle | dark: match / create / hold / init+group+listen+poke+accept+wifi_enable | `first_screen_on` | `rearm_line` | rearm s | init_native s | `wake_to_ssl_s` | picture | phone dark.car / after.car | our_proc |
|---|---|---|---|---|---|---|---|---|---|
| 1 | 1 / 1 / 1 / 0 | `-1s` | `force=true, trigger=SCREEN_ON` | 0.0 | 1.726 | 6.479 | true | 0 / 27 | 0 |
| 2 | 1 / 1 / 1 / 0 | `-1s` | `force=true` | 0.001 | 1.518 | 23.649 | true | 0 / 27 | 0 |
| 3 | 1 / 1 / 1 / 0 | `-1s` | `force=true` | 0.001 | 1.524 | 6.743 | true | 0 / 27 | 0 |

- `hold_before_any_arm` true in all 3; `fgs_fail` 0; `bt_auto_lines`: `BtAutoStartActions(clearUserExit=true, forceRearmWireless=true, armWirelessIfIdle=false)`.
- Host cross-check by `grep -c` inside each dark window agrees with the script: `hold` 1, `init` 0, `group` 0, `match` 1.
- Decisive lines, cycle 1: `09:09:59.246 MATCH! Starting AapService via Bluetooth Auto-start...`; `09:09:59.530 WifiLauncher: wireless bring-up held while the screen is off. The screen coming on re-arms it.`; `09:10:30.143 WakeDetect: re-arming the wireless bring-up held while the unit was asleep (force=true, trigger=SCREEN_ON)`; `09:10:35.940 SSL handshake complete`.
- Phone `dark.gh_wpp` 19 in each cycle.

## R6B guard, an armed stack meets Bluetooth while the screen is off

**FAIL**

Stated condition `group_ok` is 0 and `create_attempt` is 0 failed: both are 1. `init` 0, `our_proc` 0, `phone.all.car` 0 hold.

- Numbers (window `all`, bt-on to end, 90 s): `match` 4, `poke` 3, `accept` 2, `hold` 0, `radio_hold` 0, `group_ok` 1, `create_attempt` 1, `create` 0. Every `bt_auto_lines` entry: `AapService: Bluetooth auto-start: nothing to do, a handshake attempt is already in flight.` All four arrivals were vetoed, as the brief expected.
- Decisive lines: `09:17:12.133 WifiDirectManager.recoverNativeGroup | WifiDirectManager: Native AA recovery (no phone joined within 60s): recreate attempt 1/4.`, then `09:17:12.640 WifiDirectManager.createQuietGroup | WifiDirectManager: Attempting createGroup for Native AA (Attempt 0)...` and `09:17:12.677 5GHz createGroup SUCCESS!`. The screen went off at 09:16:27.860 (`WakeDetect: SCREEN_OFF`); the original group was created at 09:16:11.936, 60 s before the recreate.
- Reading: the rebuild in the dark came from the armed stack's own 60 s no-join recovery (the phone's WiFi is off in this run, so no join can happen), not from a Bluetooth arrival and not through the sleep hold. The candidate does not hold that recreate while the screen is off, and the brief's rule is "no group is rebuilt in the dark, by any path". Whether the reporter's unit reaches this path is not shown here. Full capture kept (`R6B.hu.logcat`, `R6B.phone.logcat`).

## R5-PC positive control, process death with the screen on, 2 cycles

**PASS**

- Settings written: R5 keys (`enable-car-launcher=true`); HOME role held by the app, `resolve-activity` printed `.../CarLauncherAlias`; top resumed activity was ours.
- Per cycle: `create` 1, pids differ (12044 to 12431, 12431 to 12870), `hold` 0, `screen_on` 0, `create_to_init_native_s` 0.152 and 0.155, `init_native` 1, `listen` 1, `poke` 2 (both cycles), `our_proc` 0.
- `group_ok` 0 in both: the group persisted (Setup note 7). `Group formed` was read in both. No `home-intent` marker in either cycle (Android started the process itself).
- Decisive line, cycle 1: `09:19:39.533 AapService creating...` then `09:19:39.685 WifiLauncher: Initializing WiFi Mode: NATIVE`, and `09:19:39.918 a group named DIRECT-QS-MT50YT610E4GFPSLU is already up from before this bring-up; reading it`.

## R5 process death with the screen off, then a wake, 5 cycles (the point of the round)

**PASS**

5 of 5 cycles had a dark start (no extra cycles needed). Pids: 12870 to 13504, 13504 to 14207, 14207 to 14901, 14901 to 15611, 15611 to 16326; all `Start proc ... for top-activity`; `home-intent` marker present in all 5.

| Cycle | dark create / hold / init / group / listen / poke / wifi_enable | `hold_before_any_arm` | `first_screen_on` | `rearm_line` | rearm s | init_native s | after `Group formed` | our_proc |
|---|---|---|---|---|---|---|---|---|
| 1 | 1 / 1 / 0 / 0 / 0 / 0 / 0 | true | `-1s` | `force=false, trigger=SCREEN_ON` | 0.001 | 1.516 | 1 | 0 |
| 2 | 1 / 1 / 0 / 0 / 0 / 0 / 0 | true | `-1s` | `force=false` | 0.001 | 1.521 | 2 | 0 |
| 3 | 1 / 1 / 0 / 0 / 0 / 0 / 0 | true | `-1s` | `force=false` | 0.001 | 1.519 | 2 | 0 |
| 4 | 1 / 1 / 0 / 0 / 0 / 0 / 0 | true | `-1s` | `force=false` | 0.001 | 1.519 | 2 | 0 |
| 5 | 1 / 1 / 0 / 0 / 0 / 0 / 0 | true | `-1s` | `force=false` | 0.001 | 1.519 | 2 | 0 |

- Decisive lines, cycle 1: `09:21:14.611 AapService creating...`; `09:21:14.741 WifiLauncher: wireless bring-up held while the screen is off. The screen coming on re-arms it.`; `09:21:27.717 WakeDetect: SCREEN_ON (screen was off for -1s)`; `09:21:27.718 WakeDetect: re-arming the wireless bring-up held while the unit was asleep (force=false, trigger=SCREEN_ON)`; `09:21:29.233 WifiLauncher: Initializing WiFi Mode: NATIVE`.
- Host `grep -c` per window agrees: dark `hold` 1 and `init` 0, after `re-arming` 1, in all 5.
- Literal `group_ok >= 1` in the after window reads 0 in all 5, with the same cause as R5-PC (Setup note 7). After window `match` is 1 per cycle with `nothing to do`, see Setup note 8. Thermal max 71C, delta 0.

## Anything the brief did not ask about

- The 5 R5 dark-start cycles all re-armed with `force=false`, while the R6 arrivals re-armed with `force=true`.
- R6B points to a gap rather than a bug in what the brief tested: the candidate holds the bring-up, but the armed stack's `recoverNativeGroup` timer still fires in the dark. If the reporter's sequence leaves an armed stack with no phone joined, a 60 s window is enough to recreate once in the sleep. Worth a decision before the reporter round: hold that recreate while the screen is off, or accept it.
- Airplane mode on D-POCO does not turn its Bluetooth off if it was on before; the brief's R6 and R6B preconditions need an explicit `svc bluetooth disable` plus a `dumpsys` read-back.
- The brief's `th_pkg` and `su -c` lines need the two fixes in Setup notes 1 and 4 before this protocol is reused.

Evidence: captures and JSON in `launcher-auto-connect-crash-round1-captures.zip` (release `rig-evidence-launcher-auto-connect-crash`; asset `launcher-auto-connect-crash-round1-captures.zip`, sha256 `9ec5cb7178a4eaa342cc63e581e5246414c7f4a52bbb748def0229cc6bf2115c`).
