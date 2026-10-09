# update-direct-apk-link, round 1 brief

## 1. Build and baseline

- **Candidate:** `fix/update-direct-apk-link` @ `4ebe61bc`, one commit on `main` `7102b428`. New branch, no history rewritten.
- **No baseline build.** The behaviour under test does not exist on `main`.
- **This round needs a test-only version name, and it is the one local edit allowed.** The branch builds as `3.5.0-beta4` and the newest GitHub release is `v.3.5.0-beta3`, so an unedited build answers "no update available" and the code under test never runs. Lower `versionName` only. Keep `versionCode = 117`, so the APK installs over what the units carry. Never commit the edit.

```bash
git fetch fork fix/update-direct-apk-link
git checkout --detach 4ebe61bc
sed -i 's/versionName = "3.5.0-beta4"/versionName = "3.5.0-beta1"/' app/build.gradle.kts
grep -n 'versionName = ' app/build.gradle.kts          # must read "3.5.0-beta1"
# build with hur-wifi-test-scripts/build_hur.sh (assembleGithubDebug), copy the APK out of apks/
git checkout -- app/build.gradle.kts                    # undo the edit straight after the build
```

The APK file name will carry `3.5.0-beta1`. That is expected.

**Build gate, on every unit after install** (`adb install -r -d <apk>`; `-d` covers a unit that carries a later build):

```bash
PKG=com.andrerinas.headunitrevived
adb -s <serial> shell dumpsys package $PKG | grep versionName      # versionName=3.5.0-beta1
unzip -p <apk> 'classes*.dex' | strings | grep -F 'UpdateLinkPolicy'  # at least one hit
send ACTION_QUERY_STATE                                             # data= carries commit starting 4ebe61bc
```

The `commit` may end in `-dirty` because of the version edit. That is expected. Any other prefix fails the gate.

## 2. What this is and why

On Android 4.x, "Check for updates" opened the newest release's web page. The unit's stock browser cannot open GitHub at all, because it has no TLS 1.2. A reporter installed Firefox 68, which does have it: the page loaded, but its list of files never appeared. GitHub fills that list after the page loads with a script, and Firefox 68 cannot run it.

The candidate changes one thing. Below Android 5.0 (`ConscryptInitializer.isNeededForTls12()`), the "Open GitHub releases" button opens the release APK's own download link (`.../releases/download/<tag>/<name>.apk`). That link is a plain redirect to the file and needs no script. The button skips the `_debug.apk` asset. On Android 5.0 and newer, the button opens the release page as before. The choice is `UpdateLinkPolicy`, covered by 5 JVM tests.

The rig has to answer two questions the JVM tests cannot:

1. Does Firefox 68 on a 4.x unit download the file from that link?
2. Does the app hand that link to the browser on a 4.x unit, and the page link on a new unit?

## 3. What is different this round

- **No phone and no Android Auto in any run.** Nothing here touches a session, so no phone capture is needed. Keep every phone in airplane mode or unplugged so nothing connects mid-run.
- **The units need internet.** Check each one before its run: `adb -s <serial> shell ping -c 1 -W 5 github.com` must print `1 received` (or `1 packets received`). If it does not, the run is UNTESTABLE; say which unit and stop that run.
- **Firefox 68 must be installed on D-SAM and D-HP.** Fetch it on the PC over plain HTTP and install it with adb. Both tablets are ARM.

```bash
curl -L -o fennec-68.11.0-arm.apk \
  http://archive.mozilla.org/pub/mobile/releases/68.11.0/android-api-16/en-US/fennec-68.11.0.en-US.android-arm.apk
adb -s <serial> install -r fennec-68.11.0-arm.apk
adb -s <serial> shell pm list packages | grep -F org.mozilla.firefox     # must print package:org.mozilla.firefox
```

  On first launch Firefox 68 may show a welcome screen. Dismiss it by hand and note it in Setup notes.
- **The update check reads GitHub live.** If the owner publishes a new release before this round runs, the expected tag and file name change. Every condition below is written so it holds for whatever the newest release is. Today the newest is `v.3.5.0-beta3`, whose release APK is `com.andrerinas.headunitrevived_3.5.0-beta3.apk`, 13726138 bytes.
- **Hand steps are needed, and this is why.** No `AutomationReceiver` verb runs the update check or presses a dialog button, and Android's "Complete action using" chooser belongs to the system. So three steps are named hand steps: the "Check for updates" row, the dialog's "Open GitHub releases" button, and the browser chooser. On the tablets, dump then tap the dump's bounds (`rig-quirks/units/D-SAM-and-D-HP.md`). Everything else is scripted.
- **D-HP's settings screen cannot be reached by script** (`rig-quirks/units/D-HP.md`), so R2 reaches it by hand too. D-SAM and D-HU open it on the row directly with the search extra below.
- **No run is expected INCONCLUSIVE.** If the app's own request to GitHub fails, the run is INCONCLUSIVE, not FAIL: that request is older than this change.

## 4. Settings keys

None. The round changes no setting. Do not reset the units' existing `settings.xml`.

The deciding line logs at INFO. The `AutomationReceiver: ` line from `ACTION_QUERY_STATE` is also INFO, so its presence in the capture proves INFO reaches the log. If it is absent, the run is void: record the unit's `log-level` value in Setup notes.

## 5. Actions

Define once per shell, with `<serial>` per unit (D-SAM `30041c35642d2200`, D-HP `CNU350BGBJ`, D-HU `27870808938846`):

```bash
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
S=<serial>
send() { a=$1; shift; adb -s $S shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; }
```

Open settings on the row, D-SAM (not exported there, so through `run-as`):

```bash
adb -s $S shell run-as $PKG am start --user 0 -n $PKG/com.andrerinas.openheadunit.main.SettingsActivity --es extra_search_query "Check for updates"
```

Open settings on the row, D-HU:

```bash
adb -s $S shell am start -n $PKG/com.andrerinas.openheadunit.main.SettingsActivity --es extra_search_query "Check for updates"
```

The list is filtered to the row. Do not scroll. If the row is not on screen, take a screenshot, write so in Setup notes and stop that run.

## 6. The deciding lines

All in the head unit capture `rN.txt`, between `AutomationMarker: RN-start` and `AutomationMarker: RN-end`. Each was checked with `grep -F` against `4ebe61bc`.

| Line | Source | Meaning |
|---|---|---|
| `SettingsFragment: opening update link ` | `main/SettingsFragment.kt` | the URL the button handed to the browser; the rest of the line is that URL |
| `UpdateChecker: Update check failed` | `utils/UpdateChecker.kt` | the app's own request to GitHub failed; the run is INCONCLUSIVE |
| `UpdateChecker: Failed to open GitHub releases URL` | `utils/UpdateChecker.kt` | no app on the unit opens the link |
| `AutomationReceiver: ` | `automation/AutomationReceiver.kt` | a verb landed (INFO) |
| `AutomationMarker: ` | `automation/AutomationEffectRunner.kt` | run window (WARN) |

Extract the URL from the line with:

```bash
grep -F 'SettingsFragment: opening update link ' rN.txt | sed 's/.*opening update link //'
```

## 7. Runs

Every run starts with the capture (§2 of the template), on the unit under test:

```bash
adb -s $S shell am force-stop $PKG
adb -s $S logcat -G 16M
adb -s $S logcat -c; stdbuf -oL adb -s $S logcat -v time > rN.txt &
adb -s $S shell rm -f '/sdcard/Download/com.andrerinas.headunitrevived_*'
send ACTION_QUERY_STATE
send ACTION_LOG_MARKER --es text RN-start
```

and ends with:

```bash
send ACTION_LOG_MARKER --es text RN-end
adb -s $S shell screencap -p /sdcard/rN.png && adb -s $S pull /sdcard/rN.png
kill %1
```

Run them in the order R0, R1, R2, R3. **Stop rule:** if R0 fails, stop the round and report; R1 and R2 cannot pass without it.

### R0, D-SAM: Firefox 68 downloads a release APK by its direct link (premise)

No Open Headunit code runs here. It checks the browser and the download host on this unit.

1. Run the start block above (`N=0`).
2. `adb -s $S shell am start -a android.intent.action.VIEW -d https://github.com/andreknieriem/open-headunit/releases/tag/v.3.5.0-beta3 -n org.mozilla.firefox/org.mozilla.gecko.BrowserApp`
   If `am` prints `Activity class ... does not exist`, use `-n org.mozilla.firefox/.App` instead and note it.
3. `sleep 30`, then `adb -s $S shell screencap -p /sdcard/r0-page.png && adb -s $S pull /sdcard/r0-page.png`. This records whether the file list loads on the page. Information only, not graded.
4. `adb -s $S shell am start -a android.intent.action.VIEW -d https://github.com/andreknieriem/open-headunit/releases/download/v.3.5.0-beta3/com.andrerinas.headunitrevived_3.5.0-beta3.apk -n org.mozilla.firefox/org.mozilla.gecko.BrowserApp` (or the `.App` component from step 2).
5. Hand step, only if Firefox asks: accept the download. Write in Setup notes what Firefox showed.
6. Poll every 10 s, up to 180 s, until the size stops changing:
   `adb -s $S shell ls -l /sdcard/Download/ | grep -F headunitrevived`
7. Run the end block.

**PASS:** `/sdcard/Download/com.andrerinas.headunitrevived_3.5.0-beta3.apk` exists and its size is `13726138`.
**FAIL:** no such file after 180 s, or a different size. Attach both screenshots.

Report the size and the seconds from step 4 to a stable size.

### R1, D-SAM (API 19): the app hands the browser the APK link. **This is the point of the round.**

1. Run the start block (`N=1`). Then `adb -s $S shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity` and `sleep 15`.
2. Open settings on the row with the D-SAM command in §5.
3. Hand step: tap the "Check for updates" row. Wait for the dialog, up to 15 s.
4. The dialog must be "Update available". If it says no update is available, the version edit did not take: stop and fail the build gate. If it says the check failed, the run is INCONCLUSIVE.
5. Hand step: tap "Open GitHub releases".
6. Hand step: if Android shows "Complete action using", choose Firefox and "Just once". Accept the download if Firefox asks.
7. Poll `/sdcard/Download/` as in R0, up to 180 s.
8. Run the end block.

Let `URL` be the extracted URL, `TAG` the path segment after `/releases/download/`, and `FILE` the last path segment.

**PASS, all of:**
- exactly 1 `SettingsFragment: opening update link ` line in the window;
- `URL` starts with `https://github.com/andreknieriem/open-headunit/releases/download/`;
- `FILE` ends in `.apk` and does not end in `_debug.apk`;
- `/sdcard/Download/FILE` exists, and its size equals the asset's size from
  `gh api repos/andreknieriem/open-headunit/releases/tags/TAG --jq ".assets[] | select(.name==\"FILE\") | .size"`;
- 0 `UpdateChecker: Failed to open GitHub releases URL` lines.

**FAIL:** `URL` contains `/releases/tag/`, or `FILE` ends in `_debug.apk`, or the file is missing or the wrong size after 180 s.

**What a PASS would look like if the change did nothing:** the line would carry `/releases/tag/` and no file would download. That is why the URL's shape is graded and not only the download.

Do not install the downloaded APK.

### R2, D-HP (API 17): the same on the reporter's Android version

Skip it if D-HP is not cabled, and say so. Install Firefox 68 on it first, as in §3.

The steps and conditions are R1's, with one change to step 2: D-HP's settings screen cannot be scripted. Hand step: from the home screen tap Settings, type `Check for updates` into the search box, and go on from step 3.

**PASS** and **FAIL** as R1.

### R3, D-HU (API 34): the app still hands the browser the release page (control)

1. Run the start block (`N=3`). Then `adb -s $S shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity` and `sleep 15`.
2. Open settings on the row with the D-HU command in §5.
3. Hand steps: tap "Check for updates", then "Open GitHub releases" in the "Update available" dialog. Same rules as R1 step 4.
4. `sleep 10`, then run the end block. Do not download anything.

**PASS, all of:**
- exactly 1 `SettingsFragment: opening update link ` line in the window;
- `URL` starts with `https://github.com/andreknieriem/open-headunit/releases/tag/`.

**FAIL:** `URL` contains `/releases/download/`.

A `UpdateChecker: Failed to open GitHub releases URL` line here only means D-HU has no browser. It does not change the verdict; note it.

## 8. Do not re-run

Nothing. This is the thread's first round.

## 9. Report back

1. R1's and R2's `URL`, and the downloaded file's size against the asset's size.
2. R3's `URL`.
3. R0's seconds to a complete download, and whether R0's page screenshot shows the file list.
