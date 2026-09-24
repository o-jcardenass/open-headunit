# Logcat access on Android 13+ and the in-app log capture

PC-only research. No hardware round. Written because two threads
(`post-beta1-self-mode`, `post-beta1-latency-instruments`) kept hitting the
system log-access consent dialog and the in-app "Export Logs" ANR, and the
question "how do we make a device support logcat" needed a real answer.

## TL;DR

- The app began requesting `android.permission.READ_LOGS` in `f98de46b`
  (PR #887, "Fix empty log files on older Android versions"), which shipped in
  `v.3.3.0-beta1`. It is **not** in `v.3.2.6` or `v.3.3.0-alpha`.
- On Android 13+ (API 33) a **granted** `READ_LOGS` is what pulls the app's
  `logcat` capture onto `LogcatManagerService`: the consent dialog while the
  app is foreground, a silent downgrade to own-UID logs while it is a
  background service. Nothing (`pm grant`, a system property, a `Settings` or
  `DeviceConfig` key, an adb switch) suppresses that for a normal-UID app.
- **Ungranted**, the app is an ordinary logd reader: it gets its own UID's
  lines immediately, no dialog. That is exactly how 3.2.x behaved, and it
  still captures everything the app process writes, Java and native.
- Fix, on `fork/fix/log-export-anr-and-self-mode-double-launch`:
  - `809adff3` scopes the manifest permission to `android:maxSdkVersion="32"`.
    Pre-13 it still buys system-wide logs for free; 13+ it is not requested,
    so the dialog can never appear and no `pm grant` can bring it back.
  - `a5cd6fc8` moves the "Export Logs" ring-buffer dump off the main thread
    behind a 10 s bound (the ANR seen in `post-beta1-latency-instruments` R7).

## The mechanism, verified against AOSP `android14`

Files: `system/logging/logd/LogReader.cpp`, `system/logging/logd/LogPermissions.cpp`,
`frameworks/base/services/core/java/com/android/server/logcat/LogcatManagerService.java`,
`frameworks/base/data/etc/platform.xml`.

1. `platform.xml` maps `android.permission.READ_LOGS` to the Linux
   supplementary group `log` (gid 1007). Granting the permission puts the app
   process in that group at spawn.

2. logd, `LogReader::onDataAvailable`, decides per reader:

   ```cpp
   if (clientIsExemptedFromUserConsent(cli)   // uid < AID_APP_START (10000)
       || !clientHasLogCredentials(cli)        // not privileged: own-UID logs only
       || only_read_event_logs) {
       reader_list_->AddAndRunThread(...);     // granted immediately, no dialog
   } else {
       reader_list_->AddPendingThread(...);    // wait for LogcatManagerService
   }
   ```

   `clientHasLogCredentials` is true when uid/gid is ROOT/SYSTEM/LOG **or the
   process has supplementary group `log`**. So a granted `READ_LOGS` makes the
   app "privileged" (can read every UID) and therefore forces it down the
   `else` branch. A normal app uid is >= 10000, so `clientIsExemptedFromUserConsent`
   is always false for it.

3. `LogcatManagerService.processNewLogAccessRequest`:

   ```java
   if (isInstrumented) { onAccessApprovedForClient(client); return; }   // am instrument
   if (!shouldShowConfirmationDialog(client)) { onAccessDeclinedForClient(client); return; }
   // else: start com.android.systemui/.logcat.LogAccessDialogActivity
   ```

   `shouldShowConfirmationDialog` is `procState == PROCESS_STATE_TOP`. So:
   - app foreground: the dialog is shown. `PENDING_CONFIRMATION_TIMEOUT_MILLIS`
     is 400 s (70 s on a debuggable build) before it auto-declines.
   - app is a background/foreground service: **auto-declined**, silently, and
     the reader is downgraded to own-UID (`entry->Revoke()`).
   - an approval lasts only `STATUS_EXPIRATION_TIMEOUT_MILLIS` = 60 s, but a
     reader that is already running is not cut off when that expires; only a
     *new* `logcat` invocation re-prompts.

   There is no `checkPermission(READ_LOGS)` anywhere in this class. Holding the
   permission does not help here; it is what routed the request here.

4. The only ways a non-system app skips the gate: run under `am instrument`
   (auto-approved), be platform-signed with uid < 10000, or an Xposed hook on
   `LogcatManagerService.onAccessApprovedForClient`. The rig has a `su` daemon
   but no Magisk/LSPosed, so only instrumentation was viable there, and it
   changes app runtime semantics.

## What "own-UID logcat" (the ungranted path) actually contains

Everything the app process writes: `android.util.Log` / Timber from Kotlin,
**and** native `__android_log_write` from the bundled libraries (`OMXClient`,
ffmpeg HEVC, the MediaCodec client), across every tag and thread. It loses
only *other* processes: SurfaceFlinger, ActivityManager, `system_server`, the
Gearhead process, kernel. For the in-app capture that is an acceptable loss.
Anyone who needs the full system log uses host-side `adb logcat` (adb shell is
`AID_SHELL`, uid 2000 < 10000, so it is exempt and never prompts).

## Timeline of the churn

| Commit | In | Effect |
|---|---|---|
| `93c62605` (Mar 2026) | pre-beta1 | continuous `logcat -v threadtime *:V` capture. System-wide filter, but no `READ_LOGS`, so own-UID only. |
| `f98de46b` (#887) | `v.3.3.0-beta1` | adds `<uses-permission READ_LOGS>`. On 13+, once granted, this is the regression. |
| `bae47b23` | after beta1 | added `isLogcatSupported()` probe: spawned `logcat -d -t 1` from the `Settings.logSource` getter, i.e. on every process start, on the main thread. Measured firing the consent dialog 55 times in one scripted session, once taking SystemUI down. |
| `c8f9d4f5` | after beta1 | removed the probe. Detection is now purely reactive: `LogExporter.launchLogcatPipe`'s zero-byte branch switches to `APPLOG_FILE` only when a capture that actually ran produced nothing. |
| `a5cd6fc8` | `fork/fix/log-export-anr-and-self-mode-double-launch` | export ring-buffer dump off the main thread, 10 s bound, `Process.destroy()` to end it (coroutine cancellation does not reach `copyTo`). |
| `809adff3` | same branch | `READ_LOGS` scoped to `maxSdkVersion="32"`. |

## Why not the other options

- **`pm grant READ_LOGS`**: on 13+ this is what *causes* the dialog, not a fix.
  With `809adff3` it also fails ("not requested" above `maxSdkVersion`).
- **A pre-flight probe** (`bae47b23`): any speculative `logcat` spawn is itself
  a consent request. Removed for that reason and should not come back. With the
  permission scoped, "does this ROM allow logcat" is answered without a spawn:
  own-UID `logcat` works on every mainstream device, and a genuinely
  locked-down ROM is still caught by the reactive zero-byte branch.
- **Default to `APPLOG_FILE` on 13+**: considered. Not needed once the
  permission is scoped, because own-UID `logcat` on 13+ is strictly richer
  than `APPLOG_FILE` (it also has the native lines) and equally dialog-free.
  `APPLOG_FILE` stays as the reactive fallback and an explicit user choice.
- **Instrumentation wrapper / Xposed**: real bypasses, but both are test-rig
  scaffolding, not something to ship, and instrumentation is a runtime
  confound for a latency round.

## For the hardware session

- After `809adff3`, on all three rig devices (D-HU API 34, D-POCO API 35,
  D-MOTO API 34) the app never holds `READ_LOGS`. Expect: no
  `LogAccessDialogActivity` at launch or mid-session, `HUR_Log_*.txt`
  non-empty with the app's own `OPENHU` and native lines, `pm grant ...
  READ_LOGS` failing.
- Rounds that need the **full system log** should keep using the host-side
  `stdbuf -oL adb logcat` capture. The in-app capture is app-scoped by design
  on 13+; a round that verifies the in-app capture mechanism (an R7-type run)
  should assert against app-scoped content, not full-system content.
- The `post-beta1-self-mode` round 2 section 7a candidate ("Allow-tap window
  then leave `logcat` alone") is moot once the branch with `809adff3` is the
  build under test: there is no dialog to tap.
