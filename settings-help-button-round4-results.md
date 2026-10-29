# settings-help-button, round 4 results

**Candidate:** `fork/docs/call-audio-needs-hfp` @ `1c25716f93b4ec8c20b6dd11ebd9c79b0491d973` (tree `a93c5eb8917db424f0e1251172370270bb1b1cc7`)       **Baseline:** none (one arm, on `main` `54256dcb`)
**APK md5:** `c3f4023a1b3b686463ff5ac818f1fc1f` (candidate; the live APK pulled from D-HU and from D-SAM has the same md5)
**Unit:** D-HU (UNISOC MT50, Android 14, 1440x720 landscape at density 240, Bluetooth off for Part A) and D-SAM (SM-T230, API 19, 1280x800 at 213 dpi). No phone, no session.
**Date:** 2026-10-27

## Setup notes

- Came from `main`. `transfer/rig-rounds` fast-forwarded to the fork tip; `rig_sync.py sync` printed `in sync` and no RESTART. Quirk files read: `topics/tooling.md`, `units/D-HU.md`, `units/D-SAM.md`. Template read without 7b and 8.
- Opening pre-flight (`rig_preflight.sh D_HU D_SAM`, no radios required because the brief uses no phone):

  | Role | ADB | WiFi | BT | Screen | BT profiles |
  |---|---|---|---|---|---|
  | D_HU | ok | 1 | 1 | Awake | HFP:none A2DP:none |
  | D_SAM | ok | 1 | 1 | Awake | HFP:none A2DP:none |

  `PREFLIGHT OK`. The closing pre-flight, after D-HU Bluetooth was switched back on, printed the same table and `PREFLIGHT OK`. `rig_cleanup.sh list`: `rig clean: no strays, lock free`. One idle Gradle daemon was killed with `rig_cleanup.sh kill` after the build.
- Clocks: D-HU 01:00:54 against host 01:00:56. D-SAM 01:00:31 against host 01:00:54, so D-SAM ran about 23 s behind. D-HU reads battery level 0 with AC false (head unit); D-SAM level 100, AC powered.
- Build: worktree via `wt-new.sh`, tree read back as `a93c5eb8`, `rig_thermal.sh wait 70`, `build_hur_cool.sh`, exit 0. Installed with `adb install -r -d` on both units, both `Success`. Identity on each unit: `ACTION_QUERY_STATE` `commit":"1c25716f93b4`; live APK md5 equals the built md5; `grep -c settings-rail-collapsed` over the dex strings read 1 on both. The host ran no unit tests.
- Markers: `ACTION_LOG_MARKER` is not in `CONFIGURING` of `AutomationCommandPolicy.kt` at this commit, so no `allow-external-configuration` write was needed.
- Helpers: ui.py, lib.sh, panel.sh and every `rN.sh`/`sN.sh` were taken verbatim from the brief. Two edits to lib.sh and preflight.sh only: `OUT` made absolute, `rig_devices.sh` sourced, and preflight.sh sources lib.sh by absolute path. Host drivers `partA1.sh` to `partA4.sh` and `partB.sh` call them. Settings were written with `set_hu_settings_host.py` (D-HU) and `set_prefs_runas_host.py` (D-SAM), app force-stopped first, every key read back (`writes.txt`).
- Settings found and delta. D-HU found: `connection-modes` wifi,self; `log-level` 2; `app-language` empty; `screen-orientation` 2; `gps-navigation` true. D-HU `shared_prefs/` read `drwxrwx--x u0_a176 u0_a176` (app-owned, correct). D-SAM found: `app-language` empty, `log-level` 2, `screen-orientation` 2, `advanced-settings-active` true. Section 4 keys were written on both. Both units were restored from `settings-backup-<serial>.xml`; the diff of restored against backup is empty on both (D-SAM with CRs and blank lines stripped). D-SAM backup is 8192 bytes and ends in `</map>`. Restore on D-HU was as root with `chown u0_a176:u0_a176` and `chmod 660`.
- D-HU Bluetooth was switched off for Part A (`enabled: false`, `state: OFF` read back) and on again at the end (`enabled: true`, `state: ON`). Density: found 240 (no override), `panel WIDE` set 240 and read w=869dp, `panel NARROW` set 443 and read w=471dp, restored with `wm density reset`, reads `Physical density: 240`.
- R2 first pass was void as designed and retried. `r2` as written swipes 441 px (y 690 to 249). Dump 0 holds the Automation item clipped to 15 px with no title node (that is the empty entry in `order=`), and one swipe flings about six items, so Automation was never read. First-pass strings, kept in `marks.txt`: ADV `General|Wireless Connection|Graphics|Video|Audio||Input Settings|Navigation|Theming|More Features|Backup and reset|Debug|Info` (2 swipes), BAS `...|Audio||Input Settings|Navigation|Theming|More Features|Info` (1 swipe). Both arms were run once more as `R2-ADV-b` and `R2-BAS-b` with `r2b.sh`, which is `r2.sh` with a 250 px swipe (y 660 to 410) and nothing else changed. Those are the graded reads. In both, the one empty entry is the clipped Automation row of dump 0, and Automation itself appears in the next dump.
- S2 `cfg=` helper misread. `conf()` takes the first `w..dp` line of `dumpsys activity activities` on API 19, which was another activity's landscape config (`w961dp h576dp land`). The `SettingsActivity` record's own config in the same dump is `w600dp h936dp port`, the dump frame is `0,0,800,1280` and the screenshot shows the chip row, so the unit did rotate. The brief's rule ("`cfg=` holds `land` means INCONCLUSIVE") was not applied for that reason; S2 is graded on the activity's own config and layout.
- Every window shows `init=0 match=0 crash=0` (guard lines below). D-SAM refuses `logcat -c` (`klogctl: Operation not permitted`), so every D-SAM count is windowed by marker.
- Round inputs, from `taps.log` (run, target, kind, x, y): R1 fold tap 58,172; R1 jump tap 54,463; R1 tooltip press 54,559; R1 unfold tap 58,172; R2-ADV rail swipe 192,690 (x2); R2-BAS rail swipe 192,690; R4 help tap 980,286; R5 help-1 tap 806,286; R5 help-2 tap 690,286; R2-ADV-b rail swipe 192,690 (x3); R2-BAS-b rail swipe 192,690 (x2); R6 jump tap 91,433; S1 fold tap 52,153; S1 jump tap 48,413. At most 4 per run. Every target came from a dump.
- Thermal: `rig_thermal.sh wait` passed before every part, host package 60C to 67C, `throttle_pkg=0` at the end of every watch log.
- Restore was done before approval rather than after (D-HU and D-SAM settings, density, Bluetooth, logcat captures killed by pid). Zip, upload, commit and worktree removal wait for approval.

## R1

**PASS**

D-HU WIDE, w=869dp, one build. Decisive lines from `R1.txt`:

```
R1-a rail=1 toggle=Hide section names n=6 titles=5 descs=0 railW=256dp listX1=256dp listW=704dp sel=General
R1-b rail=1 toggle=Show section names n=6 titles=0 descs=6 railW=72dp listX1=72dp listW=888dp
R1-c rail=1 toggle=Show section names titles=0 descs=6 sel=Graphics first=Graphics hdrs=Graphics
R1-pref-after-fold name="settings-rail-collapsed" value="true"
R1-d rail=1 toggle=Show section names titles=0 descs=6 railW=72dp          (after a force-stop and relaunch)
R1-e rail=1 toggle=Hide section names titles=5 descs=0 railW=256dp
R1-pref-after-unfold name="settings-rail-collapsed" value="false"
R1 init=0 match=0 crash=0
```

`titles=5` against `n=6` in the unfolded rail: the sixth item (Automation) is clipped at the panel edge and has no title node in the dump. `railW` 72dp is inside 60 to 80dp. `listX1` fell from 256dp to 72dp, 184dp, so the list took the room. Own re-count in the window: `FATAL EXCEPTION` 0, `AutomationReceiver: ` 1 (the marker sends were counted by the marker line). `R1-tip-27870808938846.png`: a popup shows the name `Video` beside the pressed icon; information only.

## R2

**PASS** (both arms, from the retry; the first pass is described in Setup notes)

Arm ADV, `R2-ADV-b`, 3 swipes:

```
order=General|Wireless Connection|Graphics|Video|Audio||Automation|Input Settings|Navigation|Theming|More Features|Backup and reset|Debug|Info
```

Arm BAS (`advanced-settings-active=false`, read back), `R2-BAS-b`, 2 swipes:

```
order=General|Wireless Connection|Graphics|Video|Audio||Automation|Input Settings|Navigation|Theming|More Features|Info
```

With the one empty entry (the clipped Automation row of dump 0) removed, both strings equal the brief's expected strings exactly. The old order (More Features and Navigation above Graphics, `UI`, `Backup`, `Reset` as sections) does not appear. `advanced-settings-active` read back `true` at the end. Strictly read, `order=` as printed has the empty entry, so a reader who wants the letter of the brief should treat this as PASS on the retry only.

## R3

**PASS**

Connection type `usb` only (read back):

```
R3 summary=NONE	help=0	switch=NA	under=General	onscreen=1 first=General hdrs=General
R3 init=0 match=0 crash=0
```

`under=General`, `onscreen=1`, `hdrs=` holds no `Wireless Connection`. `connection-modes` was put back to `usb,wifi` and read back.

## R4

**PASS**

```
R4-a summary=Android Auto takes the car's position from this unit, not from the phone.	help=1	switch=true	under=Navigation	onscreen=1 old=0
R4-dialog.txt: title_ok=1 text_ok=1 ok_label=OK fits=1
R4-c ... switch=true	under=Navigation	onscreen=1 dlg=0
```

`cnt R4 'help opened for gpsNavigation'` 1, `cnt R4 'SettingHelpDialog: help opened for '` 1. Guard `init=0 match=0 crash=0`.

## R5

**PASS**

```
R5-1-a summary=Display status toast popups on connection, disconnect, and mode switching.	help=1	switch=true	under=General
R5-1-dialog title_ok=1 text_ok=1 ok_label=OK fits=1
R5-1-c switch=true ... dlg=0
R5-2-a summary=Ask Android Auto not to show its own clock.	help=1	switch=false	under=Theming
R5-2-dialog title_ok=1 text_ok=1 ok_label=OK fits=1
R5-2-c switch=false ... dlg=0
```

`cnt R5 'help opened for showToastMessages'` 1 and `cnt R5 'help opened for hideClock'` 1. Guard `init=0 match=0 crash=0`.

## R6

**PASS**

D-HU NARROW, density 443, w=471dp (under 560). Stored `settings-rail-collapsed` was `false`.

```
R6-a w=471dp rail=1 toggle=NONE cats=1 n=5 titles=0 descs=5 railW=66dp listX1=66dp listW=453dp sel=General
R6-b rail=1 toggle=NONE n=5 titles=0 descs=5 railW=66dp sel=Graphics first=Graphics hdrs=Graphics
R6 init=0 match=0 crash=0
```

## R7

**INCONCLUSIVE**

`screen-orientation=4` was written and read back, the app was force-stopped, and the unit did not rotate:

```
R7-a cfg=w869dp h480dp land  rail=1 toggle=Hide section names cats=1 n=6 titles=5 descs=0 horiz=0 railW=256dp
R7 init=0 match=0 crash=0
```

`wm size` reads `Physical size: 720x1440` and the display stays 1440x720 landscape, so the portrait chip row could not be tested on this unit (no rotation). S2 covers the portrait layout on D-SAM.

## S1

**PASS**

D-SAM landscape (`screen-orientation=2`), w=961dp (560 or more, so the R1 reading applies).

```
S1-a w=961dp rail=1 toggle=Hide section names n=7 titles=7 descs=0 railW=295dp listX1=295dp listW=665dp sel=General
S1-b rail=1 toggle=Show section names n=7 titles=0 descs=7 railW=72dp listX1=72dp listW=889dp
S1-c rail=1 toggle=Show section names titles=0 descs=7 sel=Graphics first=Graphics hdrs=Graphics
S1 init=0 match=0 crash=0
```

`listX1` fell from 295dp to 72dp (223dp). No crash.

## S2

**PASS**

D-SAM with `screen-orientation=4` (read back):

```
S2-a cfg=w961dp h576dp land  rail=0 toggle=NONE cats=1 n=5 titles=5 descs=0 horiz=1 railW=-1dp listX1=0dp listW=600dp sel=General
S2 init=0 match=0 crash=0
```

The printed `cfg=` is the helper's misread (see Setup notes). The activity's own config is `w600dp h936dp port`, the dump frame is 800x1280, and `S2-a-30041c35642d2200.png` shows the Basic/Advanced toggle, the search box and a one-line chip row (General, Wireless Connection, Graphics, Video, Audio). `rail=0 toggle=NONE cats=1`, `titles=5`, `horiz=1`. No crash.

## Guard lines

```
R1 init=0 match=0 crash=0
R2-ADV init=0 match=0 crash=0
R2-BAS init=0 match=0 crash=0
R3 init=0 match=0 crash=0
R4 init=0 match=0 crash=0
R5 init=0 match=0 crash=0
R2-ADV-b init=0 match=0 crash=0
R2-BAS-b init=0 match=0 crash=0
R6 init=0 match=0 crash=0
R7 init=0 match=0 crash=0
S1 init=0 match=0 crash=0
S2 init=0 match=0 crash=0
```

## Anything the brief did not ask about

- **Rail row spacing (operator observation, measured).** The operator noted that the gap between the rail rows looks too big and asked whether the chevron radius causes it. D-HU dumps at density 240 (1.5 px/dp), WIDE: a folded rail item is 84x93 px (56x62dp) with 3 px (2dp) between items, so the pitch is 96 px (64dp); the `categoryIcon` inside is 42 px (28dp), which leaves about 51 px (34dp) of empty space between neighbouring icons. The chevron (`settingsRailToggle`) is a 93x93 px (62dp) square `ImageButton` with `SettingsIconButton` padding 16dp and no corner radius of its own; its 62dp equals `settings_icon_button_size` in `values-h700dp-land`. Item height comes from the dimen `settings_rail_item_height` (68dp in `values-h700dp-land`, 48dp in `values-h460dp`, 40dp default) plus `layout_marginBottom` 2dp in `item_settings_category_rail.xml`. So the spacing follows the item height, not a chevron radius; rounded corners are on the selected pill. In the settings list the cards are 3 px apart, so "rows" is taken to mean the rail rows. Why a 480dp-tall panel gets 62dp items rather than the 48dp bucket was not chased. D-SAM measured: unfolded rail 295dp wide with 7 items visible.
- The first rail item that is clipped at the panel edge has no title node, so `ui rail` prints `titles=5` for `n=6` when unfolded. Any later brief that wants `titles` equal to `n` on D-HU should use the folded rail or read after a swipe.
- A single 441 px rail swipe on D-HU flings about six items, which is too long for the bounded search in `r2.sh`; 250 px reads every section once.
- `conf()` in lib.sh is unreliable on API 19 when more than one activity config is in `dumpsys activity activities`; it should read the `SettingsActivity` record.
- Evidence: `settings-help-button-round4-captures.zip` in release `rig-evidence-settings-help-button` (sha256 `4cd25a1b093665ce6bbb6404f267bd4792ae9a7d8bbd434c8e20f4251c72474d`).
