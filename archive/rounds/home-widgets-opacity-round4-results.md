# home-widgets-opacity, round 4 results

**Candidate:** `fork/feat/home-widgets-and-opacity` @ `4f04c16873ced36676b1ba534d25760605459bf2` (tree `da76401ab5ce`)   **Baseline:** none (one arm)
**APK md5:** C `2680bca4182e093a9e400e131635b254`
**Unit:** D-HU, UNISOC MT50, Android 14 (sdk 34), rooted, locale en-US, idle with no phone
**Date:** 2026-10-27

## Setup notes

- Build gate passed: `mainStore=1`, `oldKeep=0`, worktree HEAD `4f04c16873ce`, tree matched. Installed with `adb install -r -d`: `Success`, `apk_identity exit 0`, `ACTION_QUERY_STATE` commit `4f04c16873ce`. No unit tests run (brief).
- Scripts: the brief's blocks, extracted with `extract_brief_blocks.py` (`build.sh`, `preflight.sh`, `install.sh`, `hu.sh`) and run unchanged; toolkit `rig_thermal.sh`, `build_hur_cool.sh`, `apk_identity.sh`, `set_hu_settings_host.py`. No script added or changed. Quirk files read: `topics/tooling.md`, `units/D-HU.md`.
- Pre-flight (`rig_preflight.sh D_HU:wifi`) FAILED only for the stray Gradle daemon of this round's own build, started beside it: D_HU adb ok, WIFI 1, BT 1, Awake, HFP none, A2DP none. `shared_prefs` owner `u0_a176:u0_a176`, `HU_HOME=com.android.launcher3`, settings backup 10353 bytes.
- Taps (injected, within the brief's budget of 3 per run, each on a node with exactly one match): L1 3 (`settingSwitch`, `Open Headunit`, `Set as default`), L3 2 (`settingSwitch`, `settingsSave`). No `NO_UNIQUE_NODE`, `DUMP_FAILED` or `TAP_BUDGET`.
- Thermal: host max 65C, `throttle_pkg=0` over the run.
- The install and the run were done by a foreground `rig-executor`; the host re-read the counts files and grepped `hu.txt` itself. All counts agreed.
- L3: `resumed-after-back` named `SettingsActivity`. The brief says to void the run in that case. `L3-c.png` (taken before Save) shows no dialog, only the search field `Car` and the switch off. The first BACK most likely closed the search filter rather than the activity, but no screenshot was taken after BACK, so that is unconfirmed. Graded by the brief's rule.
- Re-run: the operator thought the settings screen had been left open. Before the re-run D-HU was on `com.android.launcher3` with nothing of ours resumed and no logcat running. The re-run (full `hu.sh`, 01:51:24 to 01:52:49) gave the same result: `resumed-after-back` named `SettingsActivity` again (`1d7aad3 ... t2206`), so an open settings screen is ruled out. Run 1 files are in the archive under `run1/`.

## L1: the grant ends with launcher mode stored (passed in run 1 and again in the re-run)

**PASS**

- Settings written: seed (`enable-car-launcher=false` plus the brief's fixed keys); `holder-before` `com.android.launcher3` (= `HU_HOME`), `switch-before` `false`, `running-after-stop` 0 before every run.
- Radio state: unchanged (D-HU WiFi 1, BT 1, no phone).
- Discard rules: not applicable (no connection).
- Decisive lines (L1 window 01:45:46 to 01:46:14):
  - `01:46:08.920 MainActivity: the app is the home app with launcher mode not stored yet, storing car launcher mode` (count 1)
  - `asked the system for the home role` 1; `Package name cannot be null or empty` 0; `home role granted after` 0 (not graded)
- `holder-after` `com.andrerinas.headunitrevived`; `key-live` and `key-after` both `<boolean name="enable-car-launcher" value="true" />`; `fatal=0`.

The grant landed on `MainActivity` and it stored the key in the same branch, so the round 3 fault is fixed. Taps at 01:45:58, 01:46:04, 01:46:10.

Re-run (window 01:51:24 to 01:51:53): identical. `01:51:47.635 MainActivity: ... storing car launcher mode` count 1, `Package name cannot be null or empty` 0, `holder-after` `com.andrerinas.headunitrevived`, `key-live` and `key-after` `value="true"`, `fatal=0`, `running-after-stop` 0. **PASS** again.

## L3: off and Save, then a fresh screen, stays off

**INCONCLUSIVE**

- Brief rule: `resumed-after-back` must not name `SettingsActivity`; it did (`com.andrerinas.openheadunit.main.SettingsActivity`), so the run is void by the brief.
- All other conditions read as the brief wants: `switch-before` `true`; `set launcher alias enabled=false` 2 in the window (01:46:36, 01:46:42); `key-after-save` `value="false"`; `holder-after-switch` `com.android.launcher3`; `holder-before-reopen` `com.android.launcher3`; `reopen-window storing` 0; `switch-reopen` `false`; `key-after` `value="false"`; `fatal=0`. Taps at 01:46:38 and 01:46:44.
- Window 01:46:26 to 01:47:10, reopen mark 01:47:00.

Re-run (window 01:52:04 to 01:52:49): the same on every reading (`set launcher alias enabled=false` 2 at 01:52:14 and 01:52:21, `key-after-save` `value="false"`, `holder-before-reopen` `com.android.launcher3`, `reopen-window storing` 0, `switch-reopen` `false`, `key-after` `value="false"`, `fatal=0`), and `resumed-after-back` again `SettingsActivity`. The condition fails the same way twice, which points at the check, not at the screen state.

Had the BACK condition not applied, every other condition is a PASS, so a re-run with a BACK that provably leaves the settings screen (or a screenshot after BACK) would settle it.

## Anything the brief did not ask about

- The L3 BACK check looks like a brief defect: the settings screen was opened filtered with `extra_search_query`, so the first BACK probably only closes the search field. A second BACK or a check after `stopapp` would be a better condition.
- `set launcher alias enabled=true` printed at 01:45:56.527, before the first L1 tap (01:45:58), when the settings screen opened with the seeded key. It is not from the grant; the cause was not chased.
- Restore: the home holder is back to `com.android.launcher3`. `settings.xml` differs from the backup only in `wifi-direct-readback-bssid` and `wifi-direct-last-group-bssid` (`C2:F5:96:F0:46:DB` now, `46:26:27:32:FC:90` before), which is WiFi Direct bookkeeping. WiFi 1 and BT 1 as at the start. Closing pre-flight `D_HU:wifi`: PREFLIGHT OK, no strays, lock free. The candidate APK stays installed.
- Evidence: release `rig-evidence-home-widgets-opacity`, asset `home-widgets-opacity-round4-captures.zip`, sha256 `e549f9ea34ca7748492b7f58cb53f04d05eb1d0ce05e6b7b453662432b412915`. It holds both runs' captures, UI dumps and screenshots (run 1 under `run1/`).
