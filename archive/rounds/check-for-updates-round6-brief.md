# check-for-updates, round 6 brief: a third-party browser as the download path on old Android

## 1. Build and baseline

**No build this round, and no install of the app.** Every run drives a browser, not Open Headunit. Leave whatever app build is on each unit in place, and record its identity once per unit for the Setup notes:

```bash
adb -s <serial> shell dumpsys package com.andrerinas.headunitrevived | grep -a versionName
```

Two third-party files are under test. Both are pinned by hash, and the run grades the hash of the copy the tablet itself downloaded.

| File | URL | Bytes | sha256 |
|---|---|---|---|
| Firefox for Android 68.11.0, ARM | `http://archive.mozilla.org/pub/mobile/releases/68.11.0/android-api-16/en-US/fennec-68.11.0.en-US.android-arm.apk` | 45326851 | `b30c7aa24919eb36a82a742cfc488eab745b607ede0be49ab20060cc9bab96cc` |
| Open Headunit release 3.5.0-beta3 | `https://github.com/andreknieriem/open-headunit/releases/download/v.3.5.0-beta3/com.andrerinas.headunitrevived_3.5.0-beta3.apk` | 13726138 | `db95450f4f2cdad287553601fe2719bf0b27a06080ffddb76c3710d8b511fbbb` |

The Firefox link is plain `http://` on purpose: the archive answers `200 OK` with no redirect to HTTPS, so a browser that cannot do modern TLS can still fetch it. Firefox 68.11.0 has `minSdkVersion 16` and `armeabi-v7a` native code. Its VIEW handler for `http` and `https` is the alias `org.mozilla.firefox/.App`.

## 2. What this is and why it exists

Round 5 fixed the update check itself on D-HP and D-SAM. A reporter on Android 4.2.2 then found the next step broken: the dialog's "Open GitHub Releases" button hands the release page to the system browser, and that browser fails on GitHub with certificate and TLS errors. Round 4 and 5 measured why: `github.com` chains to `USERTrust ECC Certification Authority`, which no Android 4.x root store carries, and Android 4.2's browser has no TLS 1.2.

The project will not add an in-app downloader. The proposed answer is a reply to the user, with no code change: install Firefox 68.11.0, which ships its own TLS stack (NSS) and its own roots. The app opens the page with a plain `Intent.ACTION_VIEW` plus `FLAG_ACTIVITY_NEW_TASK` (`UpdateChecker.openGitHubReleases`), so Firefox appears in Android's chooser as soon as it is installed.

This round proves the four steps of that reply on the two old tablets: the stock browser fails (control), the stock browser can still fetch Firefox over HTTP, Firefox opens the release page, and Firefox downloads the release APK through GitHub's redirect host `release-assets.githubusercontent.com`.

**Already settled on the host, do not test:** releases 3.4.0, 3.5.0-beta1 and 3.5.0-beta3 carry the same signing certificate (SHA-256 `615bc3c7a74839918d01517e49397f503a5473f89266af55a26d485c1e86155d`), so one GitHub release installs over another and keeps its data. The release APK has `sdkVersion 16`. **Do not install the downloaded release APK on either unit**: the rig builds are signed with a debug key, so the install is refused, and the uninstall that clears it wipes settings.

## 3. What is different about this round

- **The reporter's version is 4.2.2, which is D-HP, not D-SAM.** D-SAM (4.4.2) is the requested unit and runs first as stage A. D-HP runs the same runs as stage B, because it is the exact match. If D-HP is not cabled, stage B is UNTESTABLE and the round still reports stage A.
- **No phone is involved.** Nothing in this round touches Android Auto, so no phone capture is required. Keep the phone in airplane mode so the app on the tablet does not wake it.
- **Markers come from the shell `log` command, not `ACTION_LOG_MARKER`**, because no run acts on the app and a broadcast would start its process. Use `adb -s <serial> shell log -p w -t RIGMARK <label>`. The line reads `W/RIGMARK(<pid>): <label>`.
- **No timing is graded.** Run `th_gate` from `ptr_lib.sh` once before each stage, as the template asks, and skip `th_watch`.
- **Hand steps exist in this round, and none of them is on our app.** They are taps on a third-party browser's dialog (Firefox's first-run screen, a download confirmation). A shell cannot answer those, and no verb exists because the app is not involved. Each one is one tap at the `uiautomator dump` bounds (rule in `rig-quirks/units/D-SAM-and-D-HP.md`), with a screenshot before it.
- **R1 is expected to show an error.** Its PASS means the error reproduces, which is the reporter's bug.

## 4. Settings keys

None. Do not write `settings.xml`, and do not launch Open Headunit, except in the optional R6.

## 5. Shell helpers

Define these once per stage. `S` is the serial of the stage's unit: D-SAM `30041c35642d2200`, D-HP `CNU350BGBJ`. Never run two adb calls against one unit at the same time.

```bash
S=30041c35642d2200            # stage B: S=CNU350BGBJ
OUT=~/rig/check-for-updates-r6/$S; mkdir -p $OUT
FX_URL=http://archive.mozilla.org/pub/mobile/releases/68.11.0/android-api-16/en-US/fennec-68.11.0.en-US.android-arm.apk
REL_PAGE=https://github.com/andreknieriem/open-headunit/releases/tag/v.3.5.0-beta3
REL_APK=https://github.com/andreknieriem/open-headunit/releases/download/v.3.5.0-beta3/com.andrerinas.headunitrevived_3.5.0-beta3.apk
mark() { adb -s $S shell log -p w -t RIGMARK "$1"; }
shot() { adb -s $S shell screencap -p /sdcard/s.png; sleep 0.3; adb -s $S pull /sdcard/s.png $OUT/$1.png; }
dump() { adb -s $S shell uiautomator dump /sdcard/d.xml >/dev/null; sleep 0.3; adb -s $S pull /sdcard/d.xml $OUT/$1.xml >/dev/null; }
top()  { adb -s $S shell dumpsys activity activities | grep -a -m1 -E 'mFocusedActivity|mResumedActivity'; }
```

Start the capture before the first run of each stage and keep it running for the whole stage. `logcat -c` and `-G` are refused on these units, so the markers bound each window.

```bash
stdbuf -oL adb -s $S logcat -v time > $OUT/stage.txt &
```

On D-HP, the host capture has died silently in long runs. Check it with `kill -0 <pid>` at the end of each run, and restart it with `>>` if it died.

### Preparation, once per stage

```bash
adb -s $S shell pm list packages org.mozilla.firefox      # must print nothing
adb -s $S shell ls /sdcard/Download/ | grep -aE 'fennec|headunitrevived'   # must print nothing
adb -s $S shell pm list packages | grep -aiE 'browser|chrome|sbrowser'      # record in Setup notes
adb -s $S shell dumpsys wifi | grep -a -m1 'mNetworkInfo'                   # must show CONNECTED
```

If Firefox is already installed or either file is already in `Download`, stop the stage and report it. Do not uninstall or delete what you did not put there.

## 6. Lines and observables that decide the runs

None of the deciding evidence comes from Open Headunit's log. The runs are graded on these, in order of weight:

1. **A file hash.** `sha256sum` of the file pulled from `/sdcard/Download/`, against the table in §1.
2. **`pm` output.** `Success` from `pm install`, and `package:org.mozilla.firefox` from `pm list packages`.
3. **A `uiautomator dump` grep**, on the saved `.xml`, with `grep -acF '<text>'`. The texts:
   - Release page loaded: `3.5.0-beta3`
   - Firefox security error: `Secure Connection Failed`, `Warning: Potential Security Risk Ahead`, `Did Not Connect`
   - Stock browser security error: `Security warning`, `problems with the security certificate`, `Webpage not available`
   - Chooser: `Firefox`
4. **A screenshot**, graded by the host. It is required in every run. Firefox draws page content itself, so a dump can miss the page text. In that case the screenshot decides.

The marker line in `stage.txt` is `W/RIGMARK(<pid>): <label>`. Grep it with `grep -aF 'RIGMARK'`.

## 7. Runs

Run R1 to R5 in order on stage A (D-SAM), then the same on stage B (D-HP). Run labels carry the unit, for example `R3-dsam-start`.

### R1: control, the stock browser on the release page

```bash
mark R1-<unit>-start
adb -s $S shell am start -a android.intent.action.VIEW -d $REL_PAGE
sleep 30; top > $OUT/R1-top.txt; dump R1; shot R1
mark R1-<unit>-end
adb -s $S shell input keyevent 4; sleep 1; adb -s $S shell input keyevent 3
```

No component is named, so whatever the unit uses today opens the link. That is what a user has. If a chooser appears instead, record its entries from the dump, press BACK, and repeat with `-n` set to the first entry from the preparation step's package list.

- **PASS (bug reproduced):** the page does not load. The dump has a stock error text from §6, **or** `3.5.0-beta3` is 0 and the screenshot shows an error or a blank page.
- **FAIL:** the release page loads (`3.5.0-beta3` at least 1, or the screenshot shows it). That is a finding, not a broken run: it means this unit is not the reporter's case. Continue the stage.
- Report: the package and activity from `R1-top.txt`, the counts of every §6 text, and the screenshot name.

### R2: the stock browser fetches Firefox over HTTP

```bash
mark R2-<unit>-start
adb -s $S shell am start -a android.intent.action.VIEW -d $FX_URL
```

Then poll every 10 s, up to 180 s:

```bash
adb -s $S shell ls -l /sdcard/Download/ | grep -a fennec
```

If the line has not appeared after 20 s, `dump R2-dialog; shot R2-dialog`. If the dump shows a button with the text `OK`, `Download` or `Save`, that is a hand step: tap it once at its dumped bounds, then keep polling. Note the tap in Setup notes.

When the size reads `45326851`:

```bash
adb -s $S pull /sdcard/Download/fennec-68.11.0.en-US.android-arm.apk $OUT/
sha256sum $OUT/fennec-68.11.0.en-US.android-arm.apk
adb -s $S shell pm install -r /sdcard/Download/fennec-68.11.0.en-US.android-arm.apk
adb -s $S shell pm list packages org.mozilla.firefox
mark R2-<unit>-end
```

`pm install` from the shell does not need "Unknown sources". A user does, and the reply tells them to turn it on. That toggle is not graded here.

- **PASS:** the file reaches 45326851 bytes within 180 s, its sha256 is `b30c7aa2...96cc`, `pm install` prints `Success`, and `pm list packages` prints `package:org.mozilla.firefox`.
- **FAIL:** any of the four is missing. Record which one, and the last `ls -l` size.
- **If R2 fails, do not stop the stage.** Push the host copy and install it, so R3 to R5 still run, and say so in Setup notes:

```bash
adb -s $S push <host copy of the Firefox APK> /data/local/tmp/fx.apk
adb -s $S shell pm install -r /data/local/tmp/fx.apk
adb -s $S shell rm /data/local/tmp/fx.apk
```

### R3: Firefox opens the release page (the point of the round)

```bash
mark R3-<unit>-start
adb -s $S shell am start -a android.intent.action.VIEW -d $REL_PAGE -n org.mozilla.firefox/.App
sleep 20; dump R3-a; shot R3-a
```

Firefox may show a first-run screen over the page. If `R3-a.png` shows one, press BACK once (`adb -s $S shell input keyevent 4`), wait 3 s, and take `R3-b`. If it is still there, tap its close or skip control once at the dumped bounds (a hand step), and take `R3-c`. Then:

```bash
sleep 20; dump R3-final; shot R3-final
mark R3-<unit>-end
```

- **PASS:** `R3-final` shows the 3.5.0-beta3 release page: `3.5.0-beta3` at least 1 in `R3-final.xml`, **or** the screenshot shows the page title. Every Firefox error text from §6 counts 0.
- **FAIL:** any Firefox error text counts at least 1, or the screenshot shows an error page.
- Report: the counts, the screenshot names, and any hand step taken.

### R4: Firefox downloads the release APK from GitHub's asset host

The link redirects to `release-assets.githubusercontent.com`, a different host from R3. This run proves that host too.

```bash
mark R4-<unit>-start
adb -s $S shell am start -a android.intent.action.VIEW -d $REL_APK -n org.mozilla.firefox/.App
```

Poll every 10 s, up to 180 s:

```bash
adb -s $S shell ls -l /sdcard/Download/ | grep -a headunitrevived
```

The hand-step rule from R2 applies, with the same button texts. When the size reads `13726138`:

```bash
adb -s $S pull /sdcard/Download/com.andrerinas.headunitrevived_3.5.0-beta3.apk $OUT/
sha256sum $OUT/com.andrerinas.headunitrevived_3.5.0-beta3.apk
mark R4-<unit>-end
```

Firefox may add a suffix such as `-1` to the name. Pull whatever name the `ls` shows.

- **PASS:** the file reaches 13726138 bytes within 180 s and its sha256 is `db95450f...fbbb`.
- **FAIL:** no file, a different size after 180 s, or a different hash. If a page or a dialog shows instead, `dump R4-fail; shot R4-fail`.
- **Do not install this file** (§2).

### R5: the app's intent offers Firefox

This is the intent `UpdateChecker.openGitHubReleases` sends, with no component, now that two browsers can answer it.

```bash
mark R5-<unit>-start
adb -s $S shell am start -a android.intent.action.VIEW -d $REL_PAGE
sleep 5; dump R5; shot R5; top > $OUT/R5-top.txt
adb -s $S shell input keyevent 4
mark R5-<unit>-end
```

Do not choose "Always" or "Just once". BACK closes the chooser and leaves no default behind.

- **PASS:** a chooser is shown and `Firefox` counts at least 1 in `R5.xml`. Also PASS if no chooser shows and `R5-top.txt` names `org.mozilla.firefox`, which means a default already points at Firefox.
- **FAIL:** the stock browser opens directly with no chooser. That means a default was already set for the stock browser, and a user in that state has to clear it. Record the package in `R5-top.txt`.

### R6 (optional, hand step): the real button

Run this only if the app's `versionName` from §1 sorts below `3.5.0-beta3`, because the button shows only when an update exists. Otherwise mark it not run. If it runs: open the app's Settings, then About, press "Check for updates", then press "Open GitHub Releases" (a hand step, because the settings screen cannot be scripted on these units). `dump R6; shot R6`. **PASS** under the R5 rule.

### Cleanup, end of each stage

```bash
adb -s $S uninstall org.mozilla.firefox
adb -s $S shell rm /sdcard/Download/fennec-68.11.0.en-US.android-arm.apk
adb -s $S shell rm /sdcard/Download/com.andrerinas.headunitrevived_3.5.0-beta3.apk
adb -s $S shell rm /sdcard/s.png /sdcard/d.xml
adb -s $S shell pm list packages org.mozilla.firefox      # must print nothing
```

Remove only the files this round created, under the names it recorded. Leaving Firefox installed would put a chooser in front of every later round that opens a link.

### Stop rule

Each stage runs R1 to R5 exactly once. No re-runs, except one retry of R2 or R4 if the unit drops off `adb devices` during the poll. Stop the round after stage B, or after stage A if D-HP is not cabled.

## 8. Do not re-run

- Round 5's R1 to R3: the update check itself on D-HP, D-SAM and D-HU. Settled PASS.
- Round 5's R4, the failure-dialog title. It needs an offline old unit and is still open, but it is not this round's question.
- Release signing and the release APK's `minSdk`. Settled on the host (§2).

## 9. Report back

Per unit, three answers decide whether the reply ships as written:

1. **R3:** does Firefox 68.11.0 open the GitHub release page with no security error? (yes / no, and the screenshot)
2. **R4:** does Firefox download the release APK through `release-assets.githubusercontent.com` with a matching sha256? (yes / no, and the hash)
3. **R2:** can the stock browser fetch Firefox over plain HTTP, matching sha256? (yes / no)

Results file: `check-for-updates-round6-results.md`. Captures go to the release `rig-evidence-check-for-updates` as `check-for-updates-round6-captures.zip`. That release was swept into `rig-evidence-legacy` on 2026-09-30, so create it again for this round (template §7).

Return one JSON block per run, with these fields: `run`, `unit`, `verdict`, `marker_start`, `marker_end`, `counts` (every §6 text), `file_bytes`, `sha256`, `pm_output`, `top_activity`, `hand_steps`, `screenshots`.
