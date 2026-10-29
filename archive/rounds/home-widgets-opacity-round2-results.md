# home-widgets-opacity - round 2 results

**Candidate:** fork/feat/home-widgets-and-opacity @ 8407923931729edd64601c70e1012adaf37d340a (tree 43fc958577bacfd500ae7627919109f05886cdf5)       **Baseline:** none (settings arms only, per brief)
**APK md5:** C 2cf665923eec2e7b8fcd277c9820cce2 (apk_identity exit 0 on both units, ACTION_QUERY_STATE commit 840792393172, no dirty suffix)
**Unit:** D-HU (UNISOC MT50, sdk 34, 720x1440, density 240; screenshots 1440x720) and D-SAM (SM-T230, sdk 19, 800x1280, density 213; screenshots are the 800x1280 portrait buffer, map=rotB)
**Date:** 2026-10-26

## Setup notes

- Quirk files read: tooling, D-HU, D-SAM, D-SAM-and-D-HP. Template read without 7b/8.
- SHA 7d8b1f3c resolved to home-widgets-opacity round 2. Sync: in sync, no RESTART.
- Opening pre-flight: D_HU wifi=1 bt=1 Awake; D_SAM wifi=1 bt=1 Awake; both PREFLIGHT OK. Restore step 6 must leave wifi/bt on for both.
- D-HU sdk 34 anim 1.0 wm 720x1440 d240 root ok; D-SAM sdk 19 anim 0 wm 800x1280 d213, battery 100, screen-orientation stored 2. D-SAM clock 21:28:48 vs host 21:29:13 (25 s behind). D-HU home role com.android.launcher3 (not PKG). HU_OWNER=u0_a176:u0_a176.
- rig_cleanup list showed only the round's own gradle daemon (build running).
- Brief scripts extracted with extract_brief_blocks.py; run by host in background (discipline 2), no rig-executor used.
- Build md5 2cf66592..., gates OK (Policy=4, pressedFill=1), tree matched; ACTION_LOG_MARKER not in CONFIGURING, no allow-external-configuration needed. Both installs apk_identity 0, commit 840792393172 (no dirty).
- runs-hu.sh printed "script exit=143": that is the exit status of `wait` on the killed thermal watch (th_end), not a failure. All three runs completed, fatal=0, "resumed" for each; logcat pid killed (ps clean).
- M-0 brief defect: `homeq.py stats` took the screen size from the dialog dump, whose nodes only reach [1080,538], so it printed `map=UNKNOWN png=1440x720 screen=1080x538`. As written M-0 would be INCONCLUSIVE. Re-ran the same stats on the same screenshot with the home dump M-0.xml (screen 1440x720, identity, as in H-0) plus the dialog's panel node appended (scripts-oneoff/M-0-menu-combined.xml; output M-0-menu-corrected.txt): clear=0.000 excessmed=-7. No device action; the capture is unchanged.
- H-0 RECORD (H-0.png): no tile outline can be seen on the bar tiles, the card or the add tiles; labels and icons read (white labels clear; grey "Add app" / "Add a widget" labels on green are low contrast but readable). A lilac round shape at the top of the card is the card's own icon.
- D-SAM: runs-sam.sh also ended "script exit=143" (th_end wait), all eight runs completed, fatal=0, each "resumed", bgfail=0; no logcat left. S-100 mapping rotB. S-B10/20/40 read map=UNKNOWN as the brief expects (busy image has no known color): graded by eye from rotated screenshots (scripts-oneoff/montage-L.png, montage-B.png, rotated 90 degrees CCW and scaled).
- S-0 focus stopped at step 1 (LEFT key) on usb_button, a bar tile; the brief says any bar tile will do. The focus read and its rest read are both on usb_button. D-SAM took the first key as a focus move, not just leaving touch mode.
- D-SAM clock 25 s behind host at pre-flight; D-SAM battery 100.
- Opening pre-flight table (restored at the end): D_HU adb ok WIFI 1 BT 1 Awake HFP none A2DP none; D_SAM adb ok WIFI 1 BT 1 Awake HFP none A2DP none. Both PREFLIGHT OK. D-SAM stored `screen-orientation` 2 (backup restored in step 6).
- Build gates: `HomeSurfaceOpacityPolicy=4`, `pressedFill=1`; worktree tree hash matched.

## H-100

**PASS**

- `settings_button map=identity clear=0.000 excessmed=-5`; `home_driver_bar clear=0.000`; `home_primary_action clear=0.000` (below 0.10, so the action clause of H-0 is valid). `H-100 resumed`, `bgfail=0`, `fatal=0`.

## H-0

**PASS**

- At rest: `settings_button clear=0.798`, `home_driver_bar clear=0.942`, `home_primary_action clear=0.894` (needs 0.50, 0.50, 0.40).
- Hold: `holdshell=0`; `hold settings_button clear=0.000 fillmed=212`, which is 0.798 below the rest value (needs 0.20 or less and 0.30 below).
- RECORD: no tile outline can be seen in `H-0.png`; labels and icons can be read (the grey "Add app" and "Add a widget" labels on the green half are low contrast but legible).

## M-0

**PASS**

- `menu=1`. As printed by the brief's helper the panel line was `map=UNKNOWN png=1440x720 screen=1080x538`: the dialog dump only has nodes up to [1080,538], so the helper took that for the screen size (brief defect, see Setup notes). Same helper, same screenshot, screen size taken from the home dump of the same run: `home_actions_panel map=identity clear=0.000 excessmed=-7 fillmed=-999 box=[360,182,1080,538]` (needs clear 0.05 or less and excessmed 30 or less).
- `M-0-menu.png` shows a dark opaque panel with the home dimmed behind it.

## S-100

**PASS**

- `settings_button map=rotB clear=0.000 excessmed=-6`; bar `clear=0.000`; action `clear=0.000`.

## S-0

**PASS**

- Focus loop stopped at step 1 (key 21) on `usb_button`.
- Rest: `rest usb_button clear=0.907` (needs 0.50 or more).
- Focused: `focus usb_button map=rotB clear=0.000 fillmed=148` (needs clear 0.20 or less and fillmed 110 to 190).

## SP (RECORD)

- `swipeprobe=0 bytes=0`: `input swipe 3 400 3 400 500` on API 19 printed nothing, so D-SAM took the duration argument. Not proven that a hold reached the app (the touch was on the bare edge).

## S-L10, S-L20, S-L40 (RECORD)

| Run | settings_button fillmed | clear | expected fillmed |
|---|---|---|---|
| S-L10 | 203 | 0.000 | about 228 |
| S-L20 | 159 | 0.000 | about 202 |
| S-L40 | 87 | 0.000 | about 150 |

- By eye (rotated screenshots): at 10 the tile reads as a tile, faint; at 20 it reads clearly; at 40 it reads strongly.
- The three resting fills sit 25, 43 and 63 below the brief's expected values. The S-0 pressed tile at the 40% floor read `fillmed=148`, 61 above the resting S-L40 value of 87, so the resting tile at a value is not the same fill as a pressed tile at that floor on this unit (the brief expected agreement within 2).

## S-B10, S-B20, S-B40 (RECORD)

- No pixel verdict (`map=UNKNOWN`, as expected for the busy image).
- By eye: at 10 the tile edges are barely distinguishable from the image, labels and icons still read; at 20 the tile is faintly visible; at 40 the tile is clearly visible.

## Anything the brief did not ask about

- `runs-hu.sh` and `runs-sam.sh` both ended `script exit=143`, the status of `wait` on the killed thermal watch; every run completed.
- D-SAM's first D-pad key (LEFT) already moved focus onto a bar tile, so touch mode did not swallow it.
- The brief's helper cannot size the screen from a dialog dump; a fix is to take the size from the run's home dump.

## Evidence

- `home-widgets-opacity-round2-captures.zip` in release `rig-evidence-home-widgets-opacity` (sha256 `7840c0580306d9bd3e1c632de67869ec081fde0f124d2d8004a72f00fe1510d8`).

## Closing

- Restore: `hu SETTINGS_BACK`, `sam SETTINGS_BACK`; background file after delete reads "No such file or directory" on both units. Closing pre-flight: D_HU adb ok WIFI 1 BT 1 Awake, D_SAM adb ok WIFI 1 BT 1 Awake, both PREFLIGHT OK; the build's idle Gradle daemon was killed with `rig_cleanup.sh kill` and the rig read clean. Build worktree removed. The candidate APK stays installed on both units.
