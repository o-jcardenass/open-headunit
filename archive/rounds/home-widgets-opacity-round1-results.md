# home-widgets-opacity - round 1 results

**Candidate:** fork/feat/home-widgets-and-opacity @ 2d41ab4e47ed1f4309050291ea9d2e4aa84cd3e2 (tree 1633c0255d14282d773e7a6d945666b4c07eebb5)       **Baseline:** none (settings arms only, per brief)
**APK md5:** C ef37be69e58aed02f388694b83e453aa (apk_identity exit 0, ACTION_QUERY_STATE commit 2d41ab4e47ed)
**Unit:** D-HU, UNISOC MT50, Android 14 (sdk 34), 720x1440 physical size, density 240, locale en-US; screenshots are 1440x720, so the home ran landscape
**Date:** 2026-10-26

## Setup notes

- Quirk files read: `rig-quirks/topics/tooling.md`, `rig-quirks/units/D-HU.md`. Template read without 7b and 8. TOOLS.md "Pick a tool" read. Scripts: the brief's blocks, extracted with `extract_brief_blocks.py`; `build_hur_cool.sh`, `apk_identity.sh` and `set_hu_settings_host.py` via those. No script added to `rig-toolkit/`.
- Opening pre-flight (`rig_preflight.sh D_HU:wifi`): D_HU adb ok, WIFI 1, BT 1, screen Awake, HFP none, A2DP none; PREFLIGHT OK; rig clean, lock free. uid=0 root. Home role `com.android.launcher3` (not the app). Settings backup 10053 bytes. `HU_OWNER=u0_a176:u0_a176`. `bg file before`: absent, so restore removes it. Baseline had `enable-car-launcher` false, `wifi-connection-mode` 3, `home-background-night-mode` 0.
- Build gate: tree check passed; `HomeSurfaceOpacityPolicy=4`, `show-home-widgets=1`. `ACTION_LOG_MARKER` is not in `CONFIGURING` at this commit, so `allow-external-configuration` was not needed.
- **Brief defect: the animations-off lever breaks `uiautomator dump` on D-HU.** The brief's pre-flight sets `animator_duration_scale` 0. Attempt 1 of `runs.sh` then gave `DUMP_FAILED` on all 7 runs (`ERROR: could not get idle state.`). The same error came with the app stopped on launcher3 and with `--compressed`. Control: `animator_duration_scale` 1.0 and the same dump gave `UI hierchary dumped`. Attempt 1's captures are in the zip under `attempt1/` and nothing is graded from them (every run resumed, fatal 0). Attempt 2 reran all of `runs.sh` with a new capture and the scale left at 1.0 (the unit's original value, so restore is a no-op on it). The brief's single re-run rule was applied to the whole script because the cause was systematic.
- Restore: `SETTINGS_BACK`, `BG_REMOVED`, animator scale 1.0. Closing pre-flight: D_HU adb ok, WIFI 1, BT 1, Awake, HFP none, A2DP none, PREFLIGHT OK. One idle Gradle daemon was the only stray after the build and was killed with `rig_cleanup.sh kill`; rig clean afterwards. The candidate APK stays installed on D-HU.
- Units that were not used: no phone, no Bluetooth or WiFi change.

## W-A

**PASS**

- Settings: `show-home-favorite-apps` true, `show-home-widgets` deleted.
- `card=1 fav=4 add=1 bar=1 fatal=0`; `W-A resumed`; after-keys: `show-home-widgets ABSENT`.
- D-HU is not compact: the favorites and the add tile are on page 1, so W-B to W-D are valid.

## W-B

**PASS**

- Settings: favorites false, `show-home-widgets` deleted.
- `card=1 fav=0 add=0 bar=1 fatal=0`; after-keys: `show-home-widgets ABSENT` (the getter follows the favorites value and writes nothing).

## W-C

**PASS**

- Settings: favorites true, `show-home-widgets` false.
- `card=1 fav=4 add=0 bar=1 fatal=0`; after-keys: `<boolean name="show-home-widgets" value="false" />`. On `main` this would read `add=1`.

## W-D

**PASS**

- Settings: favorites false, `show-home-widgets` true.
- `card=1 fav=0 add=1 bar=1 fatal=0`; after-keys: `<boolean name="show-home-widgets" value="true" />`. On `main` this would read `add=0`.

## O-100

**PASS**

- Settings: favorites and widgets true, background `.../files/custom_home_bg.png` (solid `#FF00FF`), night mode 2, opacity 100.
- Reach met: `bgfail=0`; `screen share=0.231 hit=38299 n=165888`.
- `barpx share=0.000 hit=0 n=17262 box=[18,18,174,702]`; `cardpx share=0.000 hit=0 n=70848 box=[192,18,1422,378]`. Both below 0.05.

## O-40

**PASS**

- Same settings, opacity 40.
- Reach met: `bgfail=0`; `screen share=0.892 hit=147943 n=165888`.
- `barpx share=0.916 hit=15815 n=17262` (needs 0.30 and 0.25 above O-100's 0.000); `cardpx share=0.840 hit=59495 n=70848` (needs 0.20 and 0.15 above 0.000). Both met.
- RECORD, not part of the verdict: in `O-40.png` the bar's and card's text and icons are still readable (white labels, the "Connect over WiFi" button stays opaque); the "Add a widget" and "Add app" tile labels are grey on magenta and the weakest contrast.

## C-REC

**RECORD**

- `C-REC-am.txt`: `am start` of `SettingsActivity` with `extra_destination 2131296480`; focus `com.andrerinas.headunitrevived/com.andrerinas.openheadunit.main.SettingsActivity`.
- Counts: `switch_show_favorite_apps=0 switch_show_widgets=0 slider_surface_opacity=0 text_widgets=0 text_opacity=0 fatal=0`.
- The destination opened is the Customization screen (title, "Background Image", Custom Image 0 KB preview). The rows sit below the first screen and the dump leaves off-screen rows out, so 0 is not a FAIL. No scroll was allowed, so presence of the two new rows is unconfirmed on hardware.
- Side observation: the preview reads "Custom Image (0 KB)" although the pushed magenta PNG is 175 bytes (a rounding of KB, probably).

## Anything the brief did not ask about

- The animations-off setting makes `uiautomator dump` fail on D-HU, which is a rig quirk worth adding to `rig-quirks/units/D-HU.md`: leave `animator_duration_scale` at 1.0 for any round that dumps.
- All seven runs: no `FATAL EXCEPTION` for the app, host thermal max 60C, `throttle_pkg=0`.

## Evidence

Release `rig-evidence-home-widgets-opacity`, asset `home-widgets-opacity-round1-captures.zip`, sha256 `7555366c59179f3014a2f5619052a1c7a585cd4dbceec74f56487d0e94985fb3`.
