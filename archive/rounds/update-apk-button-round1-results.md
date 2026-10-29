# update-apk-button, round 1 results

**Candidate:** origin/main @ f9a72d48 (contains the update-dialog change `f809e6ddf`), with a scratch edit per variant below
**Baseline:** the same commit unedited, rebuilt and reinstalled at the end of the round
**APK md5:** variant A not kept (the build was overwritten by variant B's) / variant B `6921e8a38b84bb1f37094eea488ed1bc` / baseline `63404eac1bef5f3a23593712775cd0bf`
**Unit:** UNISOC MT50 head unit (Android 14, 1440x720), HP Slate 7 Plus (Android 4.2.2), POCO X3 NFC (Android 15), motorola edge 30 neo; the SM-T230 was not tested
**Date:** 2026-10-25

## Setup notes

- The newest published release is `v.4.0.0-alpha`, the same as the installed build, so the update dialog never opens on unmodified main. Variant A is a scratch worktree of main with `versionName` set to `3.0.0` so the check finds a newer release. Variant B is variant A plus `newestApkUrl = null` in `UpdateChecker.check`, standing in for a release with no APK asset. Neither edit touched the main checkout.
- The commit is an ancestor of `v.4.0.0-alpha`, and that release has a non-debug APK asset (`com.andrerinas.headunitrevived_4.0.0-alpha.apk`), so a user on 4.0.0-alpha gets "Up to Date", not this dialog. A user still seeing only the GitHub button is on an older build, or on a release with no APK.
- POCO and Moto carried a build signed with a different key, so `install -r` failed with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. With the operator's approval both were uninstalled and reinstalled, which wiped app data (onboarding reran; terms accepted on POCO; Moto's location prompt answered "Don't allow"). They now hold a debug-signed main, not their original build.
- `SettingsActivity` is not exported, so on the non-root phones the route was MainActivity, then the Settings button, then the search field ("update"), then BACK to close the keyboard, then "Check for updates". Button positions were read from `uiautomator dump` where the home screen exposed them and from screenshots where it did not. Touch space on the HP is landscape (1280x736) while its screenshots are portrait.
- The SM-T230 stayed in `mWakefulness=Asleep` after `KEYCODE_WAKEUP` and keyevent 224. No power setting was changed, so it is UNTESTABLE this round. Variant A was installed on it and baseline was restored.
- Scripts: `build_hur_cool.sh` for builds, `wt-new.sh` for the scratch worktree. New one-round script `rig-data/rounds/update-dialog-round1/scripts/check_update.sh`.
- Neither button was tapped, so the browser handoff and the log line `SettingsFragment: opening update link (apk|release page)` were not exercised.
- Screenshots were not uploaded: several show the phones' own launcher or personal app names. Hashes are below and the files stay in `rig-data/rounds/update-dialog-round1/`.

## R1 - Variant A, release with an APK, four units

**PASS**

- Settings written: none; version name only, in the APK.
- Decisive text on each unit: title "Update Available", message "A newer version (4.0.0-alpha) is available. Download the APK, or open GitHub Releases to see what changed.", buttons "Download APK" (primary), "Open GitHub Releases", "Cancel".
- HP Slate on Android 4.2.2 fetched the release list over HTTPS and shows the same three buttons.

| Unit | Result | Screenshot sha256 (first 16) |
|---|---|---|
| MT50 head unit | 3 buttons | `b29574227b0300e3` mt50_dialog.png |
| HP Slate | 3 buttons | `eb0ffb0179987759` hp_dialog.png |
| POCO | 3 buttons | `c75b721be618dfc6` poco_dialog.png |
| Moto | 3 buttons | `a17a62f962a11dc7` moto_dialog.png |

## R2 - Variant B, release with no APK, MT50 only

**PASS** for the stated behaviour (only "Open GitHub Releases" and "Cancel" appear); a wording defect is noted below.

- Decisive text: buttons "Open GitHub Releases" and "Cancel" only; message unchanged: "Download the APK, or open GitHub Releases to see what changed."
- Screenshot sha256 `55478b4df898f0a2` mt50_noapk_dialog.png.

The message still tells the user to download the APK when no such button exists. This is probably what a reporter sees when a release has no APK asset. The message could switch to a release-page-only string when `apkUrl` is null.

## R3 - SM-T230

**UNTESTABLE**

The unit would not wake over adb and its power settings were left alone. It was restored to baseline.

## Anything the brief did not ask about

- There was no brief; the round was run from the pull request comment asking whether the change shipped in 4.0.0.
- All five units are back on baseline (versionName 4.0.0-alpha confirmed by `dumpsys package` on each).
