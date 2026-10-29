# import-settings-unsaved-changes, round 2 results

- **Candidate:** C `2ab6f3981f107be636aa179350170341dafb9ced` (`fork/fix/import-settings-unsaved-changes`), tree `178930ffd4fde635b233746ecf8c57a0178b92a8` verified.
- **Baseline:** none (brief: one build, no baseline APK).
- **APK md5:** C `c443de2da30a524c06e3a010d7e06935` (`apk_check` live = want; `SettingsPendingCopy` in DEX, 3 string hits; `ACTION_QUERY_STATE` commit `2ab6f3981f12`).
- **Unit:** D-HU (Android 14). Phones in airplane mode, no phone capture.
- **Date:** 2026-10-17.

## Setup notes

- Transfer branch is checked out in worktree worktrees/rig-sync__scratch__8228cb07__20261015 (clean, at fork tip 570ae161); main checkout cannot check it out. Worked from that worktree; main checkout stayed on main.
- rig_sync.py: "in sync", no RESTART.
- Quirk files read: topics/tooling.md (thermal, injected taps entries), units/D-HU.md.
- Pre-flight (D_HU D_POCO D_MOTO, no radio needs: the runs only open the settings screen; phones go to airplane mode in S):
  D_HU wifi=1 bt=1 Awake HFP:none A2DP:none | D_POCO wifi=1 bt=1 Dozing HFP:..:33:59 A2DP:up | D_MOTO wifi=1 bt=0 Asleep. PREFLIGHT OK, no strays, lock free.
  Role names are D_HU etc (D-HU spelling fails with 'invalid variable name').
- libimp.sh deviation: D-HU adb shell is already uid 0 and `su` does not exist ("su: inaccessible or not found"), so the brief's three `su -c '...'` calls (stat, hu_put.sh, prefs cat) were run directly. Patched copy: libimp.sh; original: libimp.sh.orig.
- ohu_setkeys.py copied from automation-screen-disconnect-round1 (projection-teardown-and-relays-round3 copy absent).
- Injected taps are used under the tooling.md "Injected taps" standing instruction and the brief; every tap is in taps.log.
- Closing will restore phone radios to the pre-flight table (MOTO bluetooth stays OFF), not blanket-enable as the brief's closing step 5 says.
- Build: C built from detached worktree at 2ab6f398 (tree 178930ff verified) with build_hur_cool.sh, exit 0; APK apk/C.apk md5 c443de2da30a524c06e3a010d7e06935; SettingsPendingCopy in DEX (strings count 3). Unit tests not run (brief).
- R0: settings.xml before/after install identical (delta none). shared_prefs stat before: u0_a176:u0_a176 (10176:10176), same as /data/data/<pkg>; no chown needed. ACTION_QUERY_STATE commit=2ab6f3981f10.
- S: airplane-mode enabled on POCO and MOTO. POCO read bt/wifi disabled. MOTO WiFi still enabled after airplane (known quirk): ran `svc wifi disable` on MOTO, read back "Wi-Fi is disabled", wifi_on=0. MOTO bt was off already (pre-flight). Closing restores POCO wifi1 bt1, MOTO wifi1 bt0, airplane off.
- S: SYSTEM_ALERT_WINDOW was already `allow` before; user_rotation=1 accelerometer_rotation=1 (recorded). Backups pushed to /sdcard/Android/data/<pkg>/files/ (push accepted; owned root:root mode 644, readable). Folder used: the app external files dir. Existing HUR_Log_* files there left alone.
- Files in round folder: libimp.sh(.orig), ohu_setkeys.py, the 3 backup jsons, r0.sh, s.sh, plus per-scenario scripts.
- th_watch from libimp.sh th_gate is a background loop that inherited the flock fd and outlived S0, blocking the next flock; killed by pid (74175) and run_close wrapped in hdr.sh to kill $THPID. Not a rig fault.
- S0, PC-1 and every run: OPEN_ROUTE was `am` each time. Brief section 7 R3 and R6 `creates` expectation (1) is wrong on C: an import always recreates the settings screen, even for backup C that equals the stored values (R6-1: wm_on_create_called at 16:29:43.880 and 16:30:02.690). R6 `creates=2` in all three; not a graded condition. R1 `creates=2` in the visit-2 window is open + recreate, and f (screen-orientation=2) confirms the import ran.
- R3 not executable as written: the recreate after the import clears the search filter, so the unfiltered General list shows and the Floating Launcher Button row is off screen (R3-1.after-import.ui.xml lists General rows only). `tap_on float` printed `matches=0` at tap 4 and tap 5 in all 5 runs, so each run is void by the brief's rule. Reaching the row would need a scroll, typed text or a re-open with a query (a new visit, not what the brief measures); not improvised. Needs a brief change.
- Taps: PC-1 1, R1 4 each, R3 3 each (tap 4 and 5 refused by NO_TARGET, nothing tapped), R6 3 each. All in taps.log.
- Screenshot taken at s0a: S0-s0a.png.
- Thermal: pkg 54C to 68C at run starts, throttle_pkg=0 throughout. Host busy `gradle` daemon (pid 71612, idle) was left alone.
- Final run order differed from the brief only by R3-4, R3-5 following R6 (to complete the 5 allowed R3 runs after confirming the cause).

## R0
**PASS**

Tree and SHA as in the brief, build exit 0, `adb install -r` Success, `apk_check` true, commit `2ab6f3981f12`, `SettingsPendingCopy` present. `settings.xml` before and after the install are identical (delta none). `shared_prefs` is `u0_a176:u0_a176`, the app's own uid, so no chown.

## S0
**PASS**

`OPEN_ROUTE` am x3; every `target` resolved (Import settings 720 693 / 720 414, Floating Launcher Button 678 521, tabAdvanced 1067 138); Save `enabled=false`; `tabAdvanced checked=false`.

## PC-1
**PASS**

After the toggle tap (16:21:39): screen in front, Save `enabled=true`. First BACK: `has "Unsaved Changes"` = 1, screen still in front. After the stop `enable-floating-button=false`. The dump finds the dialog on this unit, so the zeros below mean something.

## R1
**PASS**

Graded 3 of 3 (R1-1 to R1-3); stop rule met. Dialogs on BACK after the import (check g): **0 of 3**. Checks b and c: Save `false` 3 of 3, `Unsaved Changes` 0 of 3 after the tab tap.

| Run | a stored | b Save after tab | c dialog | d discard prompt | e creates | f orientation / mic / adv | g dialog | Save after import | tab after recreate |
|---|---|---|---|---|---|---|---|---|---|
| R1-1 | true | false | 0 | 0 | 2 | 2 / 1 / true | 0 | false | true |
| R1-2 | true | false | 0 | 0 | 2 | 2 / 1 / true | 0 | false | true |
| R1-3 | true | false | 0 | 0 | 2 | 2 / 1 / true | 0 | false | true |

Each run: 4 taps, `landed=3`, `onset` false after both BACKs. Window for `creates`: `R1-k-import-start` to `-end`; independently re-counted with awk, same value. Decisive lines: R1-1 `16:22:52.397 wm_on_create_called ... SettingsActivity` (open) and `16:23:08.795` (recreate after the import).

## R3
**INCONCLUSIVE**

0 graded of 5. All five runs are void at tap 4 and tap 5 (`NO_TARGET matches=0`): the recreate after the import clears the search filter, so the Floating Launcher Button row is off screen. Checks that were reached held in 5 of 5: Save `enabled=false` after the import (b), `mic-input-source=6` and `static-bssid=02:11:22:33:44:55` stored (a), and the same values and `enable-floating-button=false`, `native-driver-selection-mode=0`, `screen-orientation=0` after the stop (f). Check d (the point of R3) was never reached. `has "Unsaved Changes"` after BACK was 0 in all five, but no toggle had happened, so that proves nothing about H2.

## R6
**PASS**

Graded 3 of 3. Per run: the discard-pending prompt count 0, screen in front after the import, picker gone (backup file name count 0), Save `false`, after BACK `Unsaved Changes` 0 and `onset` false. Dialogs on BACK: 0 of 3. `creates` = 2 in all three (the brief expected 1; see Setup notes).

## Round verdict: PASS with R3 INCONCLUSIVE

## Anything the brief did not ask about

- An import recreates the settings screen even when no value changes. Not a defect of C measured here, but the brief's "expect 1" is wrong and R3 cannot be built on a filtered list.
- R3 needs a redesign that reaches the Floating Launcher Button row after the recreate within 5 taps and without scroll or typed text (for example a second visit opened with a search query, which tests whether a re-opened screen still carries the pending copy, not the same thing as the brief's single visit).
- `libimp.sh` `th_gate` leaves its watcher alive holding the rig lock after a run; run_close now kills it.

## Closing state

Settings baseline restored (key-by-key equal to the pre-round `settings.xml`); three backup files removed from the app files folder; airplane mode off on both phones; closing pre-flight: D_HU wifi 1 bt 1, D_POCO wifi 1 bt 1 (A2DP up), D_MOTO wifi 1 bt 0, no strays, lock free. Same as the opening table.

## Evidence

Release asset `rig-evidence-import-settings-unsaved-changes`, file `import-settings-unsaved-changes-round2-captures.zip`, sha256 `25447ca3a510e5619c636d48e6b025ed9540579cef6c43903354b7e648f051ef`.
