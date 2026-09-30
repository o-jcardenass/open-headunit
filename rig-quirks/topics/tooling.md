# adb, builds, settings and capture

Read for every round: adb and shell traps, build and install, settings.xml, markers, grep, fresh installs. Moved verbatim from `TESTING-TEMPLATE.md` §7a on 2026-09-30. **Unless an entry names a unit, it was measured on D-HU.**

- **A heard fault can be stamped into the capture.** `ACTION_LOG_MARKER --es text <label>` prints
  `AutomationMarker: <label>` at WARN, so a tester listening to a run can put the moment they heard
  something into the log beside the instruments. `-f 0x00000020` is required, as for every automation
  broadcast. Worth using for any audible or visible fault the instruments might miss entirely: the
  count of markers that fall in a window where nothing was logged is the size of that blind spot.
- **`adb install -r` can silently wipe `settings.xml`.** Reinstalling over a live session with the
  same `versionCode` reset a head unit's settings to a handful of connection-bookkeeping keys,
  onboarding flags included, despite a `force-stop` immediately before. It happened once going
  baseline to candidate and not on the reverse or on later cycles, and the cause was not chased down.
  Reseed and read every key back before the next launch rather than assuming the install preserved
  them; a round that reads a default it never set has measured nothing.
- **`ACTION_RECREATE_MAIN` cannot be fired from adb, and no `-p` fixes it.** `MainActivity`
  registers that receiver with `ContextCompat.RECEIVER_NOT_EXPORTED`, so a shell-uid broadcast is
  never dispatched to the app; `am` still answers `Broadcast completed: result=0`, and
  `dumpsys activity broadcasts history` shows it enqueued and never delivered. Round 8 lost an
  attempt to it. **To recreate an activity, rotate the unit for real:**

  ```bash
  adb shell settings put system accelerometer_rotation 0
  adb shell settings put system user_rotation 1     # 0 restores
  ```

  `MainActivity`'s manifest `configChanges` is `keyboardHidden|uiMode` only, so a rotation genuinely
  destroys and rebuilds it: two `WindowManager: finishDrawing of relaunch` events and a second
  `MainActivity.logLaunchSource`. **Grade a recreation on those, never on the PID** - Android rebuilds
  an activity inside the same process. Note `AapProjectionActivity` carries `orientation|screenSize`
  in its own `configChanges`, so the same lever does **not** recreate the projection activity.
- **No USB accessory path.** `dumpsys usb` reports device mode only (`host_connected=false`); the
  port is the adb link to the PC. There is also no shared regular WiFi both devices can join. Native
  AA wireless (`wifi-connection-mode=3`) is the only usable transport, so treat any brief that says
  "connect over USB" as needing a substitute.
- **Always grep a capture with `-a`.** Logs come back long enough that `file(1)` calls them "ASCII
  text, with very long lines", and `grep` then auto-detects one as **binary**: `grep -c` prints
  *nothing at all* and exits 1, rather than printing `0`. Every count of an absent pattern and every
  count of a present one look identical from the shell — a refused count reads as "pattern not
  found". Round 2 of the video-pipeline stack lost real time to this on one capture before noticing
  and redoing the round's greps. So `grep -ac`, `grep -a -o`, `grep -aP`, without exception, and if a
  count comes back empty rather than `0`, that is the bug and not the answer.

- **Inline `sh -c` over adb is unreliable, for `sed` and `cp` alike** — the quoting does not survive.
  Confirmed again in round 8: `run-as $PKG sh -c 'cp …'` fails with `cp: Needs 1 argument`, twice out
  of two attempts, while the pushed-script form worked first time, twice out of two. Push a small
  script and run it on-device, always:
  ```bash
  adb push set_pref.sh /data/local/tmp/ && adb shell run-as $PKG sh /data/local/tmp/set_pref.sh
  ```
  `hur-wifi-test-scripts/` has a generalised `set_pref.sh <key> <type> <value>` from round 8 —
  use it rather than writing a new one-key script per run.
- **Only nav-graph fragments are deep-linkable; settings *categories* are not.**
  `SettingsActivity`'s `extra_destination` calls `navController.navigate(id)`, so it can only open a
  whole sub-screen (dark mode, keymap, and so on). Audio, Graphics, Input and the rest are categories
  inside the one long `settingsFragment` list, several screens below where any deep link lands.

  This makes "deep-link and screenshot without scrolling" impossible for most controls — round 8's R9
  was UNTESTABLE for exactly that reason, and the fault was the brief's. If a control's on-screen
  presence genuinely has to be checked, use a **bounded search**: swipe, `uiautomator dump`, grep for
  the label, repeat up to a stated maximum, and fail if it is not found by the end. That is not what
  the no-scroll rule bans — the ban is on a fixed number of blind swipes followed by an assumption.
  Better still, verify list membership and ordering from `SettingsFragment.kt` and spend the run
  elsewhere.
- **`adb reboot` is not an Android shutdown.** `adbd` sets the `sys.powerctl` property and `init`
  reboots directly; `ActivityManager` is never involved, so `ShutdownThread` never runs and
  `ACTION_SHUTDOWN` is never broadcast. Anything testing shutdown behaviour needs
  `svc power reboot` / `svc power shutdown`, which go through `IPowerManager`. Round 4's R8 was
  written with `adb reboot` and could not have worked; the brief was wrong, not the unit.
- **`build_hur.sh` deletes the previous APK before it builds.** Its own
  `rm -f com.andrerinas.headunitrevived_*.apk` clears `apks/` first, so a two-build round that builds
  A then B is left holding only B. **Copy each APK out of `apks/` into a round-specific folder as soon
  as it is built**, before starting the next one. Found in the video-black round 1, which A/B'd two
  tags.
- **Release tags are not monotonic in versionCode, so an A/B across tags can be a downgrade.**
  `v.3.2.4` is versionCode 97 and `v.3.2.3` is 96, so installing the older tag second fails with
  `INSTALL_FAILED_VERSION_DOWNGRADE`. Use **`adb install -r -d`** (`-d` = allow downgrade), which is
  safe between two debuggable builds and preserves `settings.xml` exactly as `-r` alone does — verify
  with `run-as cat` before and after, as the video-black round 1 did. Do **not** reach for
  uninstall/reinstall: §5's reason still holds, a fresh install re-runs the setup wizard and rewrites
  resolution, DPI and video codec.
- **`settings.xml` survives between rounds and carries the previous thread's non-defaults.** This
  cuts both ways. Media-gap round 2 needed no settings writes at all because round 1 had left the
  file exactly right, which saved a `force-stop` cycle; the same property silently imports another
  thread's log level, view mode or codec into a round that never asked for it. **Diff against a fresh
  backup at the start of every round** and state the delta (even if zero) in Setup notes, as round 2
  did. Note also that a test-APK install re-runs onboarding on a fresh install and rewrites
  resolution, DPI and codec — see §5.
- **`set_hu_pref.sh` cannot be used to switch arms.** It relaunches through
  `install_and_launch.sh SKIP_BUILD=1`, which installs whatever is newest in the shared `apks/`
  folder rather than the arm under test. It silently reinstalled the candidate part-way through an
  arm-A run; the md5 check caught it before any capture was taken. For an A/B, install a named APK
  with `adb install -r <specific-apk>` and verify the md5 every single time, and edit `settings.xml`
  directly rather than through a script that relaunches.
- **`adb shell cat <apk> | md5sum` does not agree with a real `pull` plus `md5sum`.** Round 6 got
  two different hashes for a byte-identical 23419189-byte file. The pipe is a transport artefact;
  pull the file and hash it locally whenever a run grades APK identity.

### Fresh installs, and the three things that wake up with one

A fresh install is not a neutral starting state. All four of these were found in one round, each
after it had already contaminated a run.

- **A build signed with a different debug key is refused** with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`,
  even at the same version code. The uninstall that clears it wipes settings, calibration and
  onboarding state, which is what wakes the three below. Confirm with the operator before
  uninstalling, and take a `settings.xml` backup you can diff against afterwards.
- **The onboarding wizard intercepts every cold launch while `onboarding-version` is below 2.**
  `MainActivity.checkSetupFlow()` starts it from `onResume` whatever the launch source, so it lands
  on top of whatever the round was about to photograph. Seed
  `<int name="onboarding-version" value="2" />` before anything else. The 2 is
  `OnboardingActivity.CURRENT_ONBOARDING_VERSION` and a later release may raise it.
- **The driver-phone selector fires on a fresh install whenever devices are bonded** and
  `last-connected-native-mac` / `native-preferred-device-mac` are empty, and its countdown can
  auto-connect a real phone on its own. One round had exactly that happen mid-diagnosis. Write
  `<int name="native-driver-selection-mode" value="0" />` (DISABLED; AUTO is 1, ALWAYS is 2) for any
  round that needs the stack armed but not connecting.
- **Clearing `native-poke-bt-macs` does not stop the unit poking phones.**
  `native-poke-all-paired` is a separate key and **defaults to true**, and an empty wake list under
  it means every bonded phone gets poked rather than none. A round wanting a quiet head unit writes
  `<boolean name="native-poke-all-paired" value="false" />` explicitly. A brief that says "no phone
  should be poked" and clears only the MAC lists has not asked for what it means.
