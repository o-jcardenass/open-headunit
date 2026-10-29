# settings-help-button, round 1 results

**Candidate:** `fork/feat/settings-help-button` @ `ba1a5c6d59bce102be40d234ba0fe3d61fdf7f6d` (tree `e585a6bd86fde901ec7a3929d6186a121e335f5f`)   **Baseline:** none (one arm)
**APK md5:** `dd03b73336c0f3fe2b962b3208e7e999` (candidate; `apk_identity.sh` MATCH on both units; `ACTION_QUERY_STATE` commit `ba1a5c6d59bc`; `SettingHelpDialog` strings in the live dex: 6)
**Unit:** D-HU UNISOC MT50, Android 14, 1440x720, found density 240 (no override), restored; D-SAM SM-T230, Android 4.4.2, 800x1280 portrait, 576dp tall
**Date:** 2026-10-22
**Brief:** transfer commit `28972630d`

## Operator decision

**The operator does not approve the changes on this branch.** Reasons, in their words reduced to the points:

- Many settings have no (?) icon, and some of the missing ones need it. The rig only checked rows that show a (?) (Audio Sink, Wireless Mode, Auto Start, the five toolbars); it did not audit which rows lack one, so the hardware PASS verdicts above say the button works where it exists, not that coverage is right.
- The AAC audio text still says it is experimental, and it stopped being experimental a while ago. Checked in the candidate's `strings.xml`: `use_aac_audio_description` reads "(Experimental)" and `use_aac_audio_summary` reads "(experimental)". `aaudio_output` also says "(experimental)", not examined further.

The results file is published as the record of what the hardware showed. It is not a ship recommendation.

Evidence: release `rig-evidence-settings-help-button` on the fork, asset `settings-help-button-round1-captures.zip`, sha256 `5a4eeaca6448bfad3e250dff11a906249c84a243143b6b92d98dec7bff5067f3` (dumps, screenshots, both logcat captures, scripts, markers; the APK is left out, md5 above).

## Round summary

- R1 (the point): PASS on D-HU T2 and T1. UNTESTABLE on D-SAM (the seeded search is not applied there).
- R2 PASS, R3 PASS at both tiers (with one brief condition not met, see R3), R4 PASS at T2, T1, R4b and on D-SAM, R5 PASS on D-HU and UNTESTABLE on D-SAM, R6 PASS on D-HU and for the Keymap part on D-SAM, R8 PASS.
- No FAIL against the candidate. Five defects in the brief's helpers are in Setup notes; two of them (`row_of` and the R6 tap order) changed how runs were read.
- One finding on the candidate: `extra_search_query` does nothing on D-SAM's narrow tab layout.

## Setup notes

- Brief SHA resolved from transfer commit 28972630d (queue brief). Main checkout had no transfer branch free: `transfer/rig-rounds` is checked out in worktree rig-sync__scratch__8228cb07__20261015 (clean). Used that worktree to ff-merge fork/transfer/rig-rounds (6837c399b -> 28972630d) and read the brief; main checkout stayed on `main`. rig_sync: in sync, no RESTART.
- Quirk files read: topics/tooling.md, units/D-HU.md, units/D-SAM.md, units/D-SAM-and-D-HP.md. TOOLS.md Pick-a-tool table read.
- Opening pre-flight (preflight-open.txt): D_HU wifi=1 bt=1 Awake, D_SAM wifi=1 bt=1 Awake, no strays, lock free. Step 6 restores BT on both to 1 (brief also says svc bluetooth enable).
- D-HU natural: physical density 240, settings h0=480dp (BARS=0), so natural tier is above T2 (brief said 'very likely T2'). tier T2 set density 288 -> h=400dp. Dex: SettingHelpDialog strings count 6. shared_prefs owner u0_a176 (not root). app-language backup: string present (value not shown by pre-keys grep); log-level found 2.
- BRIEF DEFECT ui.py: row_of() returned the nearest ancestor holding settingName, which is the ViewGroup of name+(?) only; summary and switch are siblings of that ViewGroup inside the row card. R1-T2 first pass: 'summaries=0 switches=0' and C check vacuous. Fixed row_of (climb while the ancestor holds exactly one settingName, stop at settingsRecyclerView); original saved as scripts/ui.py.orig. R1-T2 regraded offline from its own dumps (no re-run needed). rowH/nameH now = row card height.
- BRIEF DEFECT R3 dump 1: `open -` shows the unscrolled top of the settings list, where no row has a (?) at T2 (rows=0 fail=0). The brief's 'N>=1 in each of the three dumps' cannot hold without scrolling. R3-T2 graded on dumps 2 and 3 (N=1 each, fail=0, with the fixed row_of so check C is no longer vacuous).
- BRIEF DEFECT R5: the 'calls' list is longer than the screen; the Audio Sink row starts at y=702 of 720, so its settingName and summary nodes are not in the dump (only its (?) at [554,716][640,720], label 'Help for Audio Sink'). Brief checks 'sink' (text count) and summary read 0/rows=0. Graded on the (?) content-desc, which is present only on a row of the filtered list. The same 'calls' search also lists 'Stay in Android Auto during calls' (matches on its name) and 'Bluetooth address' (reason not examined).
- R2 settings.xml read-back equals the backup for enable-audio-sink (value=false both).
- BRIEF DEFECT r6.sh: the Keymap toolbar tap ran after the loop had opened the Loading screen, so the stored Keymap coordinate (1232,57) hit the wrong screen; no L-HELP screen:keymap in R6 (1 tap, no dialog). Also the About dump was stale (identical md5 to the Loading screen dump, uiautomator dump failure leaves the old /sdcard file; lib.sh dump() does not clear it). Fixed lib.sh dump() to rm the on-device and host file first. Re-ran as R6b (scripts/r6b.sh, Keymap opened first, tap, shot after; About and Permissions with fresh dumps): screen:keymap L-HELP=1, About helpcount=0 (title 'About & Support'), Permissions helpcount=0, guard 0/0. R6 graded from R6 plus R6b. Earlier dumps from R1..R5 were taken with the old dump(); each is a distinct fresh screen (checked by content: R5 and R6 titles; R1-R4 text-gated).
- Part A: tiers T2 288dpi/400dp, T1 384dpi/300dp; found density 240 (no override) restored (Physical density 240). D-HU backup restored by root cp+chown u0_a176+chmod 660; diff vs backup IDENTICAL (CR/blank stripped). D-HU BT left off until approve step (brief says enable after Part A; skill restores radios on approve).
- D-SAM: `svc bluetooth` does not exist on API 19 (prints svc usage) and `service call bluetooth_manager 8 i32 0` is refused for the shell uid (missing BLUETOOTH_ADMIN permission). Bluetooth could not be switched off, so D-SAM stayed bt=1 through Part B. Protection is the guard only (0 init / 0 MATCH per window). D-SAM battery 100, AC powered. Clock: D-SAM 20:04:19 vs host 20:04:42 (23 s apart, includes adb latency of the pair of calls; read as in sync within ~23 s). `stat` is missing on D-SAM (pre-stat empty). screen-orientation found = 4 (brief text said D-SAM needs 2); left as found per brief.
- D-SAM layout: h=576dp (tier not T1/T2 range; above 459dp), layout is the narrow one with Basic/Advanced tabs, a Search settings box and category chips. Brief assumed the D-HU category-rail layout.
- FINDING (not a brief fault): `extra_search_query` is not applied on D-SAM. R1-SAM (3 dump tries), R1-SAMb (6 s wait, screenshot R1-SAMb-a) and a hand-quoted diagnostic (`run-as am start --es extra_search_query calls`, diag-sam dump) all show the search box empty and the full list; the zzqx control in R5-SAM also shows the unfiltered list. Code: SettingsFragment.applyRequestedSearchQuery calls header?.showQuery(query); on this layout that has no visible effect. R1-SAM and R5-SAM graded UNTESTABLE per stop rule (void twice); not worked around by typing or scrolling.
- R1-SAMb is the stop-rule retry of R1-SAM (marks R1-SAMb-start/end). R1-SAM has no guard line (voided before the end of the run); whole-capture counts on both units: 0 `WifiLauncher: Initializing WiFi Mode:` and 0 `MATCH! Starting AapService`.
- R4-SAM and R3-style rows for D-SAM: the only (?) row on the first unfiltered screen is Wireless Mode, clipped at the bottom edge ([186,1220][250,1280]); `ui rows` A=B=C=D=1, fail=0, rowH=81 nameH=29 (clipped, so rowH is a lower bound). This is not the brief's R1-SAM-a rows check, which could not run.
- API 19: `logcat -G` unsupported (ignored), `stat` missing, capture is default 256K buffer on D-SAM; windows were short.
- No executor agent was used: runs were driven by the host from the brief's own lib.sh functions, foreground, one at a time (the lib.sh functions need one shell). Scripts in $EV/scripts: extract.py (host extractor, since extract_brief_blocks.py wants backticked names), brief blocks verbatim, r6b.sh (one-round), ui.py (fixed row_of; .orig kept), lib.sh (OUT made absolute, dump() clears stale file).
- Injected taps (taps.log, all from dumps): see results file.
- Thermal: gates passed each time (gate readings 68C to 75C). The watch log was reused under the name A and B, so it holds only the last call of each part: max 80C (A) and 81C (B), throttle_pkg counter constant at 42. The build reached 95C once and Gradle paused for 2 s.

Opening pre-flight (`preflight-open.txt`):

```
ROLE     ADB      WIFI   BT     SCREEN    BT-PROFILES
D_HU     ok       1      1      Awake     HFP:none A2DP:none
D_SAM    ok       1      1      Awake     HFP:none A2DP:none
PREFLIGHT OK
```

Settings written on both units (app stopped, read back): `log-level=1`, `onboarding-version=2`, `app-language=en`, `app-theme=2`, `use-extreme-dark-mode=false`, `ui-scale-settings-percent=100`. Found values: `log-level=2`, `onboarding-version=2`, `screen-orientation` 2 (D-HU) and 4 (D-SAM, left as found), `app-language` empty. Per run: R4b `ui-scale-settings-percent=150`, R8 `app-theme=3`, both put back and read back. Both `settings.xml` restores diff identical to the backup (CR and blank lines stripped). Bluetooth is restored at the approve step.

Closing pre-flight, after restoring D-HU Bluetooth (`svc bluetooth enable`, read back `enabled: true state: ON`; D-SAM `bluetooth_on=1` unchanged) and one `rig_cleanup.sh kill` for an idle Gradle daemon:

```
ROLE     ADB      WIFI   BT     SCREEN    BT-PROFILES
D_HU     ok       1      1      Awake     HFP:none A2DP:none
D_SAM    ok       1      1      Awake     HFP:none A2DP:none
PREFLIGHT OK
```

## R1-T2

**PASS**

- D-HU, 400dp tall (density 288). One tap: `R1-T2 help 597 330 19:56:33`.
- `19:56:33.853 SettingHelpDialog.show | SettingHelpDialog: help opened for enableAudioSink`; one L-HELP line in the window, one in total.
- Dialog: `title_ok=1 text_ok=1 ok_label=OK fits=1 scroll=0`. Switch before `false`, after `false`. `dialog_after_back=0`.
- Rows: `Audio Sink A=1 B=1 C=1 D=1 help=[554,287][640,373] rowH=150 nameH=39`, so the label is `Help for Audio Sink`. Summary equals U-SUM. Guard `init=0 match=0`.
- Read with the fixed `ui.py` from the run's own dumps (the brief's `row_of` returned no switch or summary).

## R1-T1

**PASS**

- D-HU, 300dp tall (density 384). One tap: `R1-T1 help 284 396 20:02:53`. L-HELP `enableAudioSink` 1, all 1.
- Dialog `title_ok=1 text_ok=1 ok_label=OK fits=1 scroll=1`. Switch `false` before and after. `dialog_after_back=0`.
- `Audio Sink A=1 B=1 C=1 D=1 help=[227,339][342,454] rowH=190 nameH=49`. Summary equals U-SUM. Guard 0/0.

## R1-SAM

**UNTESTABLE**

- Run twice (R1-SAM, then R1-SAMb as the stop-rule retry): `extra_search_query` is not applied on D-SAM, so no dump holds `Audio Sink` (3 dump tries, then a 6 s wait). The first screen of the unfiltered list has no Audio Sink row, and the brief bans typing and scrolling. A hand-quoted `run-as am start --es extra_search_query calls` gave the same empty box (`diag-sam` dump). Screenshot `R1-SAMb-a-30041c35642d2200.png` shows the Settings screen with the Basic/Advanced tabs and an empty "Search settings" box.
- Nothing was tapped for this run. The R7 label check on D-SAM (content-desc) is covered by R4-SAM's node `Help for Wireless Mode` (`D=1`) only.

## R2

**PASS**

- `R2 s0=false s1=true s2=false`. Two row taps on the summary text (`838 393` at 19:57:14 and 19:57:19). `cnt R2 'SettingHelpDialog: help opened for '` = 0.
- `enable-audio-sink` read-back `value=false` after the force-stop, equal to the backup. Guard 0/0.

## R3-T2

**PASS** (dumps 2 and 3); the brief's N >= 1 for dump 1 cannot hold

- `R3-T2-1 rows=0 fail=0` (the unscrolled top of the full list has no row with a (?)), `R3-T2-2 rows=1 fail=0`, `R3-T2-3 rows=1 fail=0`.
- Audio Sink `rowH=150 nameH=39`; Wireless Mode `rowH=232 nameH=39`. No name wrapped (nameH ratio 1.0). With the fixed `row_of`, check C reads the real switch, not a vacuous pass.
- Dump 1 is a brief defect (needs a scroll to show any help row), so it is reported, not graded as a FAIL.

## R3-T1

**PASS** (dumps 2 and 3); dump 1 as at T2

- `R3-T1-1 rows=0 fail=0`, `R3-T1-2 rows=1 fail=0`, `R3-T1-3 rows=1 fail=0`.
- Audio Sink `rowH=190 nameH=49`; Wireless Mode `rowH=302 nameH=49`. The 48dp button did not leave a 1-line name wrapped.

## R4-T2

**PASS**

- Tap `R4-T2 help 650 337 19:57:55`. L-HELP `wifiConnectionMode` 1, all 1. `title_ok=1 text_ok=1 ok_label=OK fits=1 scroll=0`. Guard 0/0.

## R4-T1

**PASS**

- Tap `R4-T1 help 352 406 20:03:32`. L-HELP `wifiConnectionMode` 1, all 1. `title_ok=1 text_ok=1 ok_label=OK fits=1 scroll=0`. Guard 0/0.

## R4b

**PASS**

- T1 at `ui-scale-settings-percent=150`. Tap `R4b help 529 606 20:03:55`. L-HELP `wifiConnectionMode` 1. `title_ok=1 text_ok=1 ok_label=OK fits=1 scroll=1`.

## R4-SAM

**PASS**

- D-SAM unfiltered first screen shows the Wireless Mode (?) at `[186,1220][250,1280]`. Tap `R4-SAM help 218 1250 20:07:32`. L-HELP `wifiConnectionMode` 1, all 1. `title_ok=1 text_ok=1 ok_label=OK fits=1 scroll=0`. Guard 0/0.
- Row geometry on that dump: `Wireless Mode A=1 B=1 C=1 D=1 rowH=81 nameH=29`, `fail=0` (row clipped at the screen edge, so `rowH` is a lower bound).

## R5-T2

**PASS** (on the (?) label; the brief's text count reads 0)

- `calls` lists Audio Sink: its (?) node `Help for Audio Sink` is at `[554,716][640,720]`, the row starts at y=702 of 720, so its name and summary nodes are not in the dump. The brief's `sink` text count is 0 and its summary read is empty; both are the first-screen clip, not a missing row. The summary has no "call" (R1-T2 summary file).
- `zzqx` control: `control_none=1`. `none=0` for `calls`. Also listed: `Stay in Android Auto during calls` (matches on its name) and `Bluetooth address`.

## R5-SAM

**UNTESTABLE**

- Same cause as R1-SAM. The `zzqx` control shows the full unfiltered list (`control_none=0`, `sink=0`), so the search is not seeded.

## R6

**PASS** (R6 plus the R6b rerun)

- Auto Start: `help=0 toolbarhelp=0 order=NA`, rows `rows=2 fail=0`. One row tap, `R6 row-help 269 490 19:59:28`, L-HELP id `autoStartOnBoot` (not `screen:`).
- Five screens: Keymap `help=1 toolbarhelp=1 order=OK`; Auto-Connect `help=1 toolbarhelp=1 order=OK`; DPI `help=1 toolbarhelp=1 order=OK`; Customization `help=1 toolbarhelp=1 order=NA`; Loading screen `help=1 toolbarhelp=1 order=NA`.
- In R6 the Keymap toolbar tap (`1232 57` at 20:00:06) ran with the Loading screen in front and gave no dialog, and the About dump was a stale copy of the Loading screen. R6b (Keymap opened first, `R6b toolbar-help 1232 57 20:01:23`): `20:01:23` L-HELP `screen:keymap` 1, About `helpcount=0` (title "About & Support"), Permissions `helpcount=0`. Guard 0/0 in both.

## R6-SAM

**PASS**

- Keymap: `help=1 toolbarhelp=1 order=OK`, tap `R6-SAM toolbar-help 634 42 20:08:28`, L-HELP `screen:keymap` 1, all 1. Guard 0/0.

## R8

**PASS**

- Judged by the host from the images: `crop-R8-cmp.png` (top: R1-T2, Dark; bottom: R8, Extreme dark). Dark shows a filled grey disk with a dark question mark, Extreme dark an outline ring with a question mark. `R8-rows.txt`: `Audio Sink A=1 B=1 C=1 D=1 fail=0`.

## Numbers that decide shipping

1. R1: L-HELP 1 and 1 on D-HU T2 and T1, dialog `fits=1`, switch `false`/`false` at both; D-SAM not run.
2. Rows: `fail=0` in every graded dump (T2: Audio Sink rowH 150, T1: rowH 190). D-SAM only the Wireless Mode row (rowH 81, clipped).
3. R6: five screens `help=1` and `order=OK` for Keymap, Auto-Connect, DPI; About and Permissions `helpcount=0`.
- D-HU density found 240, tiers read `h=400dp` at 288 and `h=300dp` at 384, restored to 240. D-SAM `h=576dp`.
- Every injected tap (`taps.log`): R1-T2 help 597,330; R2 row 838,393 and row-back 838,393; R4-T2 help 650,337; R6 row-help 269,490 and toolbar-help 1232,57; R6b toolbar-help 1232,57; R1-T1 help 284,396; R4-T1 help 352,406; R4b help 529,606; R4-SAM help 218,1250; R6-SAM toolbar-help 634,42. At most 2 per run.
- TalkBack was not switched on. Whether a TalkBack user on Android 4.x can still reach the row's own click is not tested.

## Anything the brief did not ask about

- `extra_search_query` is ignored on D-SAM's narrow layout (see R1-SAM). The code path is `SettingsFragment.applyRequestedSearchQuery` calling `header?.showQuery`. A user typing in the box was not tested. Whether a user-facing caller of the extra (the wifi scan entry in `MainActivity`) is affected on a tablet like this is not known.
- The dialog's `scrollable` flag is not stable: R1-T1 (a 1-sentence text at 300dp) read `scroll=1` while R4-T1 (393 characters at the same tier) read `scroll=0`. Treat `scroll=` as information only.
- The `calls` search also lists `Bluetooth address`, whose name and visible summary have no "calls"; the reason was not examined.
- D-HU's natural settings height is 480dp (above T2), so the unmodified D-HU is neither tier.
- D-SAM cannot have Bluetooth switched off from the shell (API 19). The guard stayed at 0 on both units' whole captures.
