# automation-screen-disconnect, round 1 results

**Candidate:** fork/feat/automation-screen-disconnect @ 5236a1b44855613124109dad3398322c2f522b1e (tree 2d8df4fa69e8d2c7191a6336d8d4e801198aee78, both match the brief)   **Baseline:** none (first round of the thread)
**APK md5:** C 55962b30a2f25111df5a79b7dfbff8c1 (same on the APK pulled back from D-HU)
**Unit:** D-HU, UNISOC MT50, Android 14 (API 34), boot_count 241. D-POCO phone and D-SAM (API 19) were installed or staged but never reached a session run.
**Date:** 2026-10-13

**Round state: stopped early by the operator after C0. The round is not complete. Only R0 and C0 ran; the power-loss runs could not be set up on D-HU, and the operator chose to repeat them on tablets and phones (D-SAM, D-POCO, D-MOTO) in a later round.**

## Setup notes

- **Scripts used:** `wt-new.sh` (candidate worktree), `rig_preflight.sh`, `rig_cleanup.sh kill`, `rig_thermal.sh`, `ohu_lib.sh`/`ohu_setkeys.py`/`ptr_lib.sh` copied from `wizard-dpi-layout-round2` (already carries the empty-`set:` fix). The brief's `ascd_lib.sh` and `sx.py` were extracted verbatim from the brief. Added to the round folder only: `env.sh` (keys, `health` check) and `restore_hu.sh`. No script added to `rig-toolkit/`.
- **Quirk files read:** `topics/tooling.md`, `topics/lifecycle.md`, `units/D-HU.md`, `units/D-POCO.md`, `units/D-SAM.md`, `units/D-SAM-and-D-HP.md`. `topics/bt.md` and `topics/wifi.md` were not read because no run reached them.
- **Paths:** the skill text names `hur-wifi-test-scripts/`; that folder is now `rig-toolkit/` (no symlink on this host). Used `rig-toolkit/`.
- **Pre-flight:** first run FAILED on one stray process, an idle Gradle daemon (pid 152993, 4 h old, no Android Studio running). Cleared with `rig_cleanup.sh kill`, second run `PREFLIGHT OK`. D_HU wifi=1 bt=1 Awake; D_POCO wifi=1 bt=1; D_SAM wifi=1 bt=1; no HFP/A2DP links on any.
- **Precheck readings (D-HU):** `shared_prefs` owner u0_a176:u0_a176 (correct, no chown needed). Battery `AC powered: false, USB powered: true, Wireless powered: false, present: true, level: 0`. `lock_settings get-disabled` true. `screen_off_timeout` 2147483647 (TIMEOUT0). `stay_on_while_plugged_in` 15. `SYSTEM_ALERT_WINDOW: allow`, `TURN_SCREEN_ON` default (no ops). Device admin count 0. D-HU bonded list: Magnetic Speaker, motorola edge 30 neo, POCO X3 NFC (MAC used for `$MAC`: DC:B7:2E:5E:4E:59). D-POCO Gearhead 18.0.164204-release.daily, not a P2P group owner. D-POCO is still bonded to both Navegadortz2 and Navegadortz3 (see the D-POCO quirk on stale bonds; never reached). D-SAM: `USB powered: true`, level 100, clock about 18 s behind the host.
- **Display and power writes, operator-approved:** `screen_off_timeout` 2147483647 -> 1800000 at 23:15:59, restored to 2147483647 at 23:18:59 and read back. `stay_on_while_plugged_in` never touched (T1 not reached), still 15. The operator asked to be told of any restart or oddity: `health` checks at each step show boot_count 241 throughout, uptime continuous (32240 s -> 32421 s), adb up, zero process deaths. No restart.
- **Settings delta:** `settings.xml` on D-HU was unchanged by the `install -r` (diff empty). C0 wrote the BASE keys + `$A0 $EP_OFF`. After the stop, `backup-HU.xml` was restored as root (chown u0_a176:u0_a176, chmod 660) and `diff` against the backup is empty. D-SAM `settings.xml` never written; diff against its backup is empty. The candidate APK is left installed on D-HU and D-SAM (`install -r`).
- **Thermal:** build peaked 87 C (`throttle_pkg` stayed 0). My unit-test step was meant to wait for cooling first but the wait call used a relative path that did not resolve inside the worktree and was silently skipped; tests ran at about 80 C, with no throttle. C0 `thermal_max=58C throttle_delta=0`.
- **Deviation, lever probe (outside the brief):** after C0 showed no power event, I ran `dumpsys battery` probes on D-HU with the app stopped (see C0 and the closing section). That is outside the brief's runs; it changed no setting and ended with `dumpsys battery reset`.
- **Not run:** V1, R3, R4, R1, R2, R6, R10, T1, grant, V3, V4, R5/R7, S1, S2. X had nothing to clean: the device admin grant was never set (count 0), the battery state was reset, the timeout restored, D-POCO left in airplane mode with Bluetooth off.

## R0 build and identity gate

**PASS**

- Build: `BUILD SUCCESSFUL in 3m 5s`, `build exit=0`.
- Unit tests: 3235 tests, 0 failures, 0 errors, 0 skipped.
- Symbols in the dex: `ExternalPowerDisconnectPolicy` 3, `ScreenOffAdminReceiver` 4, `ScreenOnRaisePolicy` 3, `external-power disconnect` 13.
- `ACTION_QUERY_STATE` on both D-HU and D-SAM: `"commit":"5236a1b44855-dirty"` (the expected `-dirty` from the copied `local.properties`).

## C0 control: does D-HU's ROM light the panel on a plug change

**INCONCLUSIVE**

C0 has no PASS or FAIL. It is not valid under the brief's own rule: `hwin C0-cut C0-end` holds 0 `WakeDetect: POWER_DISCONNECTED` and 0 `WakeDetect: POWER_CONNECTED` (the brief requires 1 each).

- Settings read back: `wifi-connection-mode` 3, `log-level` 2, `allow-external-configuration` false, `external-power-disconnect` false, delay 20, `external-power-screen-off` false.
- `ROM_WAKES_ON_UNPLUG=yes`: `C0-cut` marker 23:16:44.690, then `WakeDetect: SCREEN_ON (screen was off for 10s)` 23:16:45.441 (751 ms later); `C0.w-unplug` = `mWakefulness=Awake`.
- `ROM_WAKES_ON_PLUG=yes`: `C0-back` marker 23:17:09.408, then `WakeDetect: SCREEN_ON (screen was off for 10s)` 23:17:10.180 (772 ms later); `C0.w-plug` = `mWakefulness=Awake`.
- `C0.w0` and `C0.w1` (after `input keyevent 223`) read `mWakefulness=Dozing`, not `Asleep` as the brief says they must; so the "must read Asleep" precondition is not met by the brief's literal wording, though `WakeDetect: SCREEN_OFF` did print each time (23:16:34.646 and 23:16:59.322).
- `AapService: turning the screen on (` count in `hwin C0-start C0-end`: 0 (as required).
- `external-power disconnect` count: 0 (as required).

The lever did not produce the power event the brief says it produces. Probe with the app stopped, D-HU, logcat cleared before each: `dumpsys battery unplug` then `set ac 1`, `reset` first then `unplug` then `set ac 1`, and `set ac 0/1`, `set usb 1/0`. Each produced `android.intent.action.BATTERY_CHANGED` in the system broadcast queue (23:17:55.941, 23:17:58.288) and never `ACTION_POWER_CONNECTED` or `ACTION_POWER_DISCONNECTED`, in either logcat or `dumpsys activity broadcasts history`. The app's receiver itself is registered (`AapService.kt:2171-2172`, and SCREEN_ON/OFF lines arrived through the same receiver). The cause on the OS side is not established; it may be this ROM (`UNISOC/uis7861_6h10_Natv/uis7861_6h10:14/UP1A.231005.007/eng.wming.20260518.231016:user/release-keys`) or an Android 14 behaviour. A run on another unit is needed to tell.

## R3, R4, R1, R2, R6, R10, T1, R5/R7 power-loss runs on D-HU

**UNTESTABLE**

The brief's Section 3 says each lever is valid only when its `WakeDetect:` line appears. On D-HU it never appears, so no timer can start and none of these runs can be set up on this unit with the `dumpsys battery` lever. This is a statement about D-HU and this lever, not about the candidate. They were not attempted. The operator asked for them to be repeated on D-SAM, D-POCO and D-MOTO.

## Anything the brief did not ask about

- **D-HU's real power state changed.** At precheck it read `USB powered: true`. After `dumpsys battery reset` (twice: 23:18:59 and a re-read 5 s later) it reads `AC/USB/Wireless/Dock powered: false`, `status: 1`. Both states are from `dumpsys battery`. I do not know whether my probes caused this or whether the unit's real power source dropped on its own. Worth a look before the next round that uses D-HU.
- **`input keyevent 223` puts D-HU into `Dozing`, not `Asleep`.** Any brief that greps for `Asleep` after a 223 on this unit will read the wrong value. `Dozing` still produced `WakeDetect: SCREEN_OFF`.
- **The unit-test wait in the host's build wrapper was skipped silently** (relative path). Harmless here, but the same wrapper pattern would skip the cooling gate on a hot host without saying so.
- **Brief errata:** the brief says `dumpsys battery unplug` sends `POWER_DISCONNECTED`. Not true on D-HU (above). The brief should state which units the lever was measured on.
- Evidence: `automation-screen-disconnect-round1-captures.zip` (C0 capture, build and unit logs, health log, precheck, backups). uploaded as an asset of the release `rig-evidence-automation-screen-disconnect` on the fork; sha256 30aeb6413c6752c2268c5c8b2634a0be44cf7de45ac0b9c7700f5a4694e97ee2 (APKs left out).
