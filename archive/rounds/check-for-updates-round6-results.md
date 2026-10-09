# check-for-updates, round 6 results: a third-party browser as the download path on old Android

**Candidate:** none (no build, no app install)       **Baseline:** none
**APK md5:** n/a. Installed app on both units: `versionName=3.5.0-beta3`
**Unit:** D-SAM (SM-T230, Android 4.4.2, stage A) and D-HP (HP Slate 7 Plus, Android 4.2.2, stage B). Host clock checked: D-SAM `date` 16:33:48 against host 16:34:03 (matches, within the time between the two commands).
**Date:** 2026-10-06

## Setup notes

- **Brief:** `check-for-updates-round6-brief.md` at `ac31a428`. Quirk files read: `rig-quirks/topics/tooling.md`, `rig-quirks/units/D-SAM-and-D-HP.md`, `rig-quirks/units/D-SAM.md`, `rig-quirks/units/D-HP.md` (the last two only their first lines).
- **Pre-flight** (`rig_preflight.sh D_SAM:wifi D_HP:wifi`, first attempt used the wrong role names `D-SAM`, so the table below is from the second attempt):

  | ROLE | ADB | WIFI | BT | SCREEN | BT-PROFILES |
  |---|---|---|---|---|---|
  | D_SAM | ok | 1 | 1 | Awake | HFP:none A2DP:none |
  | D_HP | ok | 1 | 1 | Awake | HFP:none A2DP:none |

  It first FAILED on the rig lock: an orphaned `bash stage2.sh` from `projection-teardown-and-relays-round3` held `/tmp/ohu-rig.lock`. It was killed at the operator's instruction and the pre-flight then passed. Later, a `sleep` child of my own earlier lock holder survived its `flock` and made a second pre-flight fail; I killed it by pid.
- **Stage order was B then A.** D-SAM's WiFi was `DISCONNECTED` (`Supplicant state: DISCONNECTED`, no scan results at first, then the AP visible at 5745 MHz -13 dBm and 2437 MHz -30 dBm but not associating), which fails the brief's preparation check. I do not switch radios, so I ran stage B (D-HP) first. The operator joined D-SAM to the AP by hand, then stage A ran.
- **Run by the host directly**, not by `rig-executor` (rule: no subagent for scripted hardware rounds). Helpers are the brief's `mark`, `shot`, `dump`, `top`, kept in `/tmp/claude-1000/h.sh`. Markers are the shell `log -p w -t RIGMARK` form, as the brief says. No `hur-wifi-test-scripts` script was added or changed; `rig_preflight.sh` and `rig_thermal.sh` were used.
- **Preparation read-back:** on both units `org.mozilla.firefox` was absent and no `fennec` or `headunitrevived` file was in `Download` before the round. Browser packages: D-HP `com.android.browser`, `com.android.browser.provider`, `com.android.chrome`; D-SAM `com.android.browser.provider`, `com.samsung.android.scloud.proxy.sbrowser`, `com.sec.android.app.sbrowser` (Internet, 1.5.28), `com.sec.android.app.sbrowsertry`, `com.android.chrome`.
- **The plain VIEW intent already shows a chooser on both units** (before Firefox is installed): D-HP lists Browser and Chrome, D-SAM lists Chrome and Internet. So R1 used the brief's fallback of naming the first stock browser, and R2 did the same so that no "Always" or "Just once" was chosen:
  - D-HP: `-n com.android.browser/.BrowserActivity`.
  - D-SAM: `-n com.sec.android.app.sbrowser/.SBrowserMainActivity` (the first browser package in the preparation list; Internet is the stock browser).
  R5 was run with no component, as the brief says. On D-HP, R2's first attempt without a component showed the chooser (`R2-dialog.png`); BACK closed it and the run restarted with `-n` (marker `R2-dhp-restart-with-browser`).
- **Hand steps:** none. D-HP R3 showed Firefox's "add to Home screen" overlay (`Continue to Website`); the brief's single BACK dismissed it (`R3-b` dump has 0 of that text). No tap was sent in the round.
- **R5 setup:** HOME was pressed before the intent so that Firefox was not in front.
- **R6 not run:** the installed `versionName` on both units is `3.5.0-beta3`, which does not sort below the release.
- **Thermal:** `rig_thermal.sh wait 75` before each stage and `watch` during each. D-HP stage max 71C, D-SAM stage max 69C, `throttle_pkg` stayed 2458 throughout. No run voided.
- **Capture:** `stdbuf -oL adb logcat -v time` per stage, alive at the end of both stages. Markers read back with `grep -aF RIGMARK`. The release-assets redirect host does not appear in logcat (0 lines); R4 rests on the downloaded file's size and sha256.
- **Evidence:** release `rig-evidence-check-for-updates`, asset `check-for-updates-round6-captures.zip`, sha256 `b1e080ccd6e0d6eb9c043a4730837ef3362a8a85e5a56f7d43e1f9fa4e592c8b`. The zip holds stage logcats, dumps, screenshots, thermal logs and grades; the two downloaded APKs are left out, their hashes are in R2 and R4.
- **Cleanup:** the two downloaded APKs and the scratch files `/sdcard/s.png` and `/sdcard/d.xml` were removed from both units.

## R1-dhp: control, the stock browser on the release page

**PASS**

- Plain intent: `ResolverActivity`, entries `Browser`, `Chrome` (dump `R1.xml`, `R1.png`).
- Retry with `-n com.android.browser/.BrowserActivity`, markers `16:23:12` to `16:23:46` for the first attempt. Resumed activity `com.android.browser/.BrowserActivity`. Counts in `R1b.xml`: `Security warning` 0, `problems with the security certificate` 0, `Webpage not available` 0, `3.5.0-beta3` 0, `Firefox` 0. The error text is inside a WebView, so the dump does not carry it; the dump has `Connection problem`. Screenshot `R1b.png`: "Webpage not available" with the dialog "Connection problem: Couldn't establish a secure connection."

The page does not load, which reproduces the reporter's bug.

## R1-dsam: control, the stock browser on the release page

**PASS**

- Plain intent: `ResolverActivity`, entries `Chrome`, `Internet` (`R1.xml`, `R1.png`).
- Retry with `-n com.sec.android.app.sbrowser/.SBrowserMainActivity`. Counts in `R1b.xml`: `Security warning` 0, `problems with the security certificate` 0, `Webpage not available` 0, `Firefox` 0, `3.5.0-beta3` 1. That 1 is the URL inside the text `https://github.com/andreknieriem/open-headunit/releases/tag/v.3.5.0-beta3 failed to load`, not page content. Screenshot `R1b.png`: "SSL connection error. Unable to make a secure connection to the server."

The page does not load.

## R2-dhp: the stock browser fetches Firefox over HTTP

**PASS**

- File reached 45326851 bytes within 20 s of the start (`ls -l` at 10 s read 31178104, at 20 s read 45326851), no dialog and no tap.
- sha256 of the pulled copy: `b30c7aa24919eb36a82a742cfc488eab745b607ede0be49ab20060cc9bab96cc`.
- `pm install -r`: `Success`. `pm list packages org.mozilla.firefox`: `package:org.mozilla.firefox`.

## R2-dsam: the stock browser fetches Firefox over HTTP

**PASS**

- File at 45326851 bytes at the first poll (10 s), no dialog and no tap.
- sha256: `b30c7aa24919eb36a82a742cfc488eab745b607ede0be49ab20060cc9bab96cc`.
- `pm install -r`: `Success`. `pm list packages`: `package:org.mozilla.firefox`.

## R3-dhp: Firefox opens the release page

**PASS**

- Resumed activity `org.mozilla.firefox/org.mozilla.gecko.BrowserApp`. `R3-a.xml`: title `Release 3.5.0-beta3 · andreknieriem/open-headunit · GitHub`, plus the overlay `Continue to Website`. One BACK, then `R3-b`: overlay count 0.
- `R3-final.xml` counts: `3.5.0-beta3` 1, `Secure Connection Failed` 0, `Warning: Potential Security Risk Ahead` 0, `Did Not Connect` 0. Screenshot `R3-final.png`: the release notes and the Assets section.

## R3-dsam: Firefox opens the release page

**PASS**

- `R3-a.xml` already carries the title `Release 3.5.0-beta3 · andreknieriem/open-headunit · GitHub`, no overlay.
- `R3-final.xml` counts: `3.5.0-beta3` 1, the three Firefox error texts 0. Screenshot `R3-final.png`: padlock in the address bar, release notes, Assets section.

## R4-dhp: Firefox downloads the release APK from GitHub's asset host

**PASS**

- `ls -l` read 13726138 bytes at the first poll (10 s), name `com.andrerinas.headunitrevived_3.5.0-beta3.apk`.
- sha256: `db95450f4f2cdad287553601fe2719bf0b27a06080ffddb76c3710d8b511fbbb`. Not installed.

## R4-dsam: Firefox downloads the release APK from GitHub's asset host

**PASS**

- `ls -l` empty at 10 s, 13726138 bytes at 20 s, same name.
- sha256: `db95450f4f2cdad287553601fe2719bf0b27a06080ffddb76c3710d8b511fbbb`. Not installed.

## R5-dhp: the app's intent offers Firefox

**PASS**

- `R5-top.txt`: `android/com.android.internal.app.ResolverActivity`. Chooser entries in `R5.xml`: `Browser`, `Chrome`, `Firefox`. `Firefox` count 1. BACK closed it, no default set.

## R5-dsam: the app's intent offers Firefox

**PASS**

- `R5-top.txt`: `android/com.android.internal.app.ResolverActivity`. Entries: `Chrome`, `Firefox`, `Internet`. `Firefox` count 1. BACK closed it, no default set.

## R6: the real button

**Not run.** `versionName` is `3.5.0-beta3` on both units, so the button does not show.

## Round verdict: PASS

Answers to the brief's three questions, both units:

1. **R3, Firefox 68.11.0 opens the release page with no security error:** yes on D-HP (4.2.2) and yes on D-SAM (4.4.2).
2. **R4, Firefox downloads the release APK with a matching sha256:** yes on both, `db95450f...fbbb`.
3. **R2, the stock browser fetches Firefox over plain HTTP with a matching sha256:** yes on both, `b30c7aa2...96cc`.

## Anything the brief did not ask about

- **A chooser is already there on both tablets without Firefox**, so a user of the stock browser meets a chooser (D-HP: Browser or Chrome; D-SAM: Chrome or Internet) before the stock browser's TLS failure. Which entry a user picks decides whether they hit the bug. The brief's R1 wording ("no component is named, so whatever the unit uses today opens the link") did not hold on either unit.
- **The two stock browsers fail differently:** D-HP's `com.android.browser` shows "Webpage not available" with a "Connection problem" dialog; D-SAM's Internet shows "SSL connection error". Neither contains any of the brief's three listed stock texts in its `uiautomator` dump (D-HP's page text sits in a WebView). The screenshot was what graded both, as the brief allows.
- **D-SAM lost its WiFi association for at least 1.5 hours** before the round (state change records stop at 14:50, `DISCONNECTED`, empty scan results at first). It needed an operator join.
- **Download speed:** D-HP downloaded Firefox in 20 s or less and the release APK in 10 s or less; D-SAM in 10 s and 20 s.
