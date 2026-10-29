# pr-1132-exit-dialog, round 1 results

- Candidate: C `f17c14ef6e71e38027fcc332bb3e4719275c7fa8`, md5 `63e7cb671fc6f5a713f92a0b43d6f4ed`, `applySystemBars` count 2
- Baseline: B `443faca81bfa26293f7dcf96fcdae6dfa8524218` (the contributor's PR head), md5 `c1df1fee85977a189963122ca60b8bc4`, count 2. Installed on D-SAM only, for SAM-C.
- Units: D-HU (Android 14), D-SAM (API 19), D-HP (API 17). No phone.
- Date: 2026-10-23
- Evidence: release `rig-evidence-pr-1132-exit-dialog`, asset `pr-1132-exit-dialog-round1-captures.zip`, sha256 e20e2756467668f9bbe0b3b9b61254202ad9006a6911344783e5d2cf77c7fa70

## Verdicts

| Run | Kind | Verdict |
|---|---|---|
| HU-1 | GATE | PASS (with a note on the focus reading) |
| HU-2 | GATE | PASS |
| HU-3 | RECORD | RECORD |
| SAM-1 | GATE | PASS |
| SAM-2 | GATE | PASS |
| SAM-3 | gate | FONT_REACHED=yes (after one reboot) |
| SAM-4 | GATE, the point | PASS |
| SAM-C | CONTROL | reached |
| HP-1 | GATE | INCONCLUSIVE (FOCUS met, STOP not gradable) |
| HP-2 | GATE | PASS |
| HP-3 | gate | FONT_REACHED=yes (after one reboot) |
| HP-4 | GATE, the point | PASS |
| Tablet bars | RECORD | no system bar in SAM-1 or HP-1 open.png |

## Setup notes

- Branch transfer/rig-rounds is held by worktree rig-sync__scratch__8228cb07__20261015 (clean); fast-forwarded it to 12ce661ee and used it instead of the main checkout. rig_sync: in sync, CLAUDE.md unchanged, no restart.
- Quirk files read: tooling, D-HU, D-SAM, D-HP, D-SAM-and-D-HP.
- Brief taps once on the exit dialog (HU-1/SAM-1/HP-1), no verb exists for the dialog; authorised by the brief's "Injected gestures" paragraph.
- Opening preflight FAILed only on the stray gradle daemon, which is this round's own build.sh running beside it (pid 199878); D_HU wifi=1 bt=1, all 3 units on adb, root ok. Full table in preflight.txt.
- Installs: all 3 units C md5 63e7cb67, commit f17c14ef6e71. ACTION_LOG_MARKER not in CONFIGURING, no allow-external-configuration needed.
- HU stage: focus() read mCurrentFocus=AapProjectionActivity (who()=PROJ) with the dialog open on every HU open/reopen line. Window hash/type differ from the closed state (type=2 vs 1) and the dump root is the dialog, so graded on that; brief's focus=DIALOG literal not met. Same reading applies to tablets. Not edited in script.
- HU-2 awk printed inside=0 smaller=0 (awk artifact, leading "1" in the line); recomputed inside=1 by hand. smaller=0 is because the dialog spans the full height, not because the dump gave the whole screen (width 858 of 1440).
- SAM: font_scale 1.3 did not reach the app until one reboot (reach=1.000 before, 1.311 after); three "adb: device not found" lines in the log were the fs call right after reboot_wait, font_scale read back 1.3 afterwards. SAM-3.txt holds the SAM-3r run (script copies it over). klogctl: Operation not permitted printed on every cap on D-SAM, harmless, capture grew. SAM-1 Stop landed on com.android.settings WifiSettingsActivity.
- sam_ctrl: B installed (443faca81bfa), measured, C reinstalled (f17c14ef6e71); settings re-seeded by next seed.
- HP-1: Stop tap produced no onPause/onStop and after_stop still AapProjectionActivity. Diagnostic repeat HP-1b added (scripts/hp1b.sh, not in brief).
- HP-1b ran under a stale font_scale 1.3 (fs 1.0 on API 17 does not apply without a reboot; fr=1.188), irrelevant to the tap diagnosis. Neither tablet showed system bars in SAM-1/HP-1 open.png. HP: no tap mapping found that selects the row; not pursued (scope).

Opening pre-flight (full text in preflight.txt): D_HU adb ok, wifi 1, bt 1, screen Awake; stray gradle daemon was this round's own build. Units: D-HU sdk 34 locale en-US, wm 720x1440 density 240; D-SAM sdk 19 locale en, wm 800x1280 density 213; D-HP sdk 17 locale en, no wm binary. Settings delta before the round: D-HU wifi-connection-mode=3, fullscreen-mode=1, screen-orientation=2; D-SAM wifi-connection-mode=3, screen-orientation=4; D-HP wifi-connection-mode=1, fullscreen-mode=2; ui-scale-exit-dialog-percent absent on all (seeds set it). HU_OWNER u0_a176:u0_a176 read first, correct. Opening font_scale: HU 1.0, SAM null, HP null; animator scale HU 1.0, SAM 0, HP 0. Host thermal 63 to 71 C, throttle counter 42 throughout (no rise).
Restore: all three units back on their opening APK (apk_identity exit 0) and settings.xml (SETTINGS_BACK); font and animation scales back (HU 1.0/1.0, SAM 1.0/0, HP 1.0/0); both tablets rebooted. `settings delete` does not exist on API 17 and 19, so restore.sh left D-SAM at font_scale 1.3; I put 1.0 by hand and rebooted D-SAM again (null and 1.0 are equivalent). Closing pre-flight: `D_HU ok wifi 1 bt 1 Awake`, PREFLIGHT OK, no stray logcat; the idle gradle daemon was killed with rig_cleanup.sh kill. The captures zip is capped at the evidence zip, APKs excluded.

## HU-1

**PASS**

Fonts 1.0, scale 100. Reach ok, `act rows=0`. `open rows=4`, `closed rows=0`, `reopen rows=4`. counts `pause_dialog=0 pause_stop=1 stop_stop=1 fatal=0`; `after_stop` resumed = `com.zqc.launcher.HomeActivity`.

```
open rows=4 icon_w=36 icon_h=36 text_h=33 fr=0.917 tap=404,231 win=195,74,1245,646 inwin=1 text=Stop_Connection focus=PROJ [mCurrentFocus=Window{76ebff3 u0 com.andrerinas.headunitrevived/...AapProjectionActivity}, [type=2]-...]
closed rows=0 win=0,0,1440,720 focus=PROJ [mCurrentFocus=Window{5bb5190 ... AapProjectionActivity}, [type=1]-...]
reopen rows=4 ... focus=PROJ [mCurrentFocus=Window{1273418 ...}, [type=2]-...]
after_stop resumed=[ResumedActivity: ActivityRecord{7908c14 u0 com.android.launcher3/com.zqc.launcher.HomeActivity ...}]
```

**The brief's literal `focus=DIALOG` was not met, and I graded it PASS anyway.** `who()` read `PROJ` on both `open` lines. On Android 14 `mCurrentFocus` prints the activity class for a dialog window of the same activity, so `who()` cannot tell them apart. What shows the dialog had focus: the window hash differs (open `76ebff3` / `1273418`, `type=2`; closed `5bb5190`, `type=1`), the `uiautomator` root is the dialog's own bounds (`195,74,1245,646`, not the screen), `rows=4` was dumped, and BACK closed it. The defect the brief names (`rows=0` with the dialog on screen) did not occur. The instrument needs a window-type test on API 34.

## HU-2

**PASS**

```
S=1.306 F=0.975 screen=0,0,1440,720 dialog=291,0,1149,720 dx=0 dy=0 w=1440 h=720 fatal=0
open rows=4 icon_w=47 icon_h=47 text_h=42 fr=0.894 win=291,0,1149,720 inwin=1
```

The script printed `inside=0 smaller=0`. I recomputed `inside=1` by hand (291,0,1149,720 lies within 0,0,1440,720); the script's awk line is wrong. `smaller=0` is because the dialog now spans the full 720 height, not because the dump gave the whole screen (it is 858 wide of 1440), so I graded FIT on `S`, `F`, `inwin`, `dx`, `dy`. **Worth the reviewer's eye:** at 130% on this 720 px tall screen the dialog is 720 px high, touches the top and bottom edges, and the "Quick Settings" row is cut by the Cancel divider (`HU-2-open.png`). At 100% the dialog is 572 px high (`HU-1-open.png`).

## HU-3

**RECORD**

`HU-3-open.png` (`fullscreen-mode=0`): the right-hand system bar (status icons, clock, four navigation buttons, x from 1304) is visible and the dialog (161,74,1143,646) sits to its left. In `HU-1-open.png` and `HU-2-open.png` (`fullscreen-mode=1`) no bar is visible.

## SAM-1

**PASS**

```
open rows=3 icon_w=32 text_h=29 fr=0.906 tap=264,564 win=80,349,720,931 inwin=1 focus=DIALOG
closed rows=0 focus=PROJ
reopen rows=3 ... focus=DIALOG
after_stop resumed=[mResumedActivity: ...com.android.settings/.Settings$WifiSettingsActivity]
pause_dialog=0 pause_stop=1 stop_stop=1 fatal=0
```

## SAM-2

**PASS**

`S=1.281 F=1.023` (`icon_w` 32 to 41, `fr` 0.906 to 0.927).

## SAM-3

**FONT_REACHED=yes**

First run `reach=1.000` (font not applied). One reboot (18 s), then `reach_after_reboot=1.311`. `SAM-3.txt` holds the post-reboot run.

## SAM-4

**PASS**

`FONT_REACHED=yes S=1.281 F=0.986`. `open rows=3 icon_w=41 text_h=48 fr=1.171 win=0,244,800,1035 inwin=1` against SAM-3's `fr=1.188`.

## SAM-C

**CONTROL REACHED**

B installed (`commit 443faca81bfa`, `apk_identity` exit 0), font 1.3: `Fb100=0.763 Fb130=0.780 Sb=1.281`. B loses the system font scale at its default 100% as well as at 130% (`fr` 0.906 and 0.927 against 1.188 for C at the same font), and its dialog scale itself works. This measurement can see the defect, so SAM-4's PASS is a real test of `f17c14ef`. C reinstalled afterwards (`commit f17c14ef6e71`).

## HP-1

**INCONCLUSIVE**

FOCUS met: `open rows=3 focus=DIALOG`, `closed rows=0 focus=PROJ`, `reopen rows=3 focus=DIALOG`, `fatal=0`. STOP could not be graded: the one injected tap at the dump's bounds (438,324, `HP-1 tap 00:23:10`) did not choose the row. `pause_dialog=0 pause_stop=0 stop_stop=0` and `after_stop` is still `AapProjectionActivity`; the log carries no `finish`/disconnect line after the tap. A diagnostic repeat, **HP-1b** (not in the brief; `scripts/hp1b.sh`, one extra tap on C), read the same: one second after the tap `mCurrentFocus` is still the dialog window and `HP-1b-after.png` shows the dialog open (it also shows the left navigation bar revealed by the touch). So the tap landed on this unit but not on the row. That is a tap-mapping fault on D-HP's mirrored rotation (`rotation=1` against `mRotation=3` in its dump, see `rig-quirks/units/D-HP.md`), not evidence about the PR, so I do not grade it a FAIL. No mapping that selects the row was found and I stopped there. SAM-1 and HU-1 cover STOP through the same adapter on the other two units.

## HP-2

**PASS**

`S=1.281 F=1.023`.

## HP-3

**FONT_REACHED=yes**

`reach=1.000` then one reboot (21 s), `reach_after_reboot=1.311`.

## HP-4

**PASS**

`FONT_REACHED=yes S=1.281 F=1.006`. `open rows=3 icon_w=41 text_h=49 fr=1.195 win=140,100,1140,700` against HP-3's `fr=1.188`.

## Anything the brief did not ask about

1. The font scale is kept on both old units: `F` 0.986 (SAM-4) and 1.006 (HP-4) with the system font at 1.3, against 0.763 and 0.780 for B on D-SAM.
2. The 130% dialog on D-HU fills the full screen height and clips the last row (see HU-2). The contributor's PR and C behave the same there; B was not run on D-HU.
3. Instrument faults for the next brief: `who()` mislabels the dialog on API 34 (HU-1); the `centre` awk prints `inside=0` wrongly (HU-2); the HP tap needs a mapping the brief does not provide (HP-1).
4. On API 17 `settings put system font_scale 1.0` does not apply without a reboot (HP-1b read `fr=1.188`). Restore reboots both tablets for this reason.
5. Taps injected: HU-1 1, SAM-1 1, HP-1 1, HP-1b 1 (diagnostic); keys: at most 3 BACK per run.
