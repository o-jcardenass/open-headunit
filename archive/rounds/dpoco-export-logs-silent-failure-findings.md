# dpoco-export-logs-silent-failure — findings

**Build:** `main` @ `d9e519259` (versionName 3.5.0-alpha, versionCode 113, github/debug)
**Unit:** D-POCO (POCO X3 NFC, Android 15/API 35), acting as head unit, idle on the Settings screen
**Date:** 2026-09-24

## Origin

Reported directly by the user, not from a queued brief: "Export Logs" in Settings fails every time
on this device. Diagnosed live over adb/logcat against the running app; no code was changed. This
file is a first-look finding for the coding session to fix, not a round against a candidate/baseline
pair, so it is not paired with a brief and has no release asset (captures were two short logcat
excerpts, quoted inline below instead of zipped).

## Setup notes

- Device state read from `shared_prefs/settings.xml` via `run-as` (app is a debug build): at
  reproduction time, `log-source=1` (`APPLOG_FILE`, "Direct to file"), `log-location=1`
  (`DOWNLOADS`), `log-level=0` (`VERBOSE`), `log-capture-enabled=false`.
- Two reproductions, both captured with `stdbuf -oL adb logcat -v threadtime`, unfiltered:
  1. Tapped **Export Logs** directly. Failed silently (toast, no dialog).
  2. Tapped **Start Capturing Logs** → **Stop** → **Export Logs**, the user's own habitual sequence.
     Same silent failure. This second capture is the one the root cause below is traced against;
     `/storage/emulated/0/Android/data/com.andrerinas.headunitrevived/files` (stat only — `run-as`
     cannot list *inside* this path on this device, a known Android 11+ FUSE/SELinux restriction on
     `Android/data`, not an app bug) shows `Modify: 2026-09-24 23:42:53`, 13s before the export
     attempt, confirming the capture-file/directory creation actually ran on **Start**.
- `run-as`'s own SELinux domain (`u:r:runas_app:s0`, no MLS categories) cannot read the app's real
  external-storage path (`u:r:untrusted_app:s0:c19,c257,c512,c768` at runtime) — this is why file
  contents inside `Android/data/<pkg>/files/` couldn't be inspected directly; the app's own process
  is unaffected, this is purely an adb-tooling limitation.

## Finding — Export Logs returns null with zero diagnostics when `APPLOG_FILE`'s capture file is empty

**Root cause, traced to the exact lines** (`app/src/main/java/com/andrerinas/openheadunit/utils/`):

- `AppLog.Logger.File` (`AppLog.kt:41-107`) writes asynchronously: `println()` only enqueues a line;
  a background `drainLoop()` thread does the actual write and only flushes when the queue goes idle.
- `close()` (`AppLog.kt:116-...`) **deliberately does not wait** for that thread — the comment at
  `AppLog.kt:109-115` explains why (joining it on the main thread risks an ANR on slow storage).
- If the queue is empty when `drainLoop()` processes the close sentinel — i.e. nothing was logged in
  the window between **Start** and **Stop** — it deletes the file it just created, because it is
  0 bytes (`AppLog.kt:104-106`):
  ```kotlin
  if (file.exists() && file.length() == 0L) {
      file.delete()
  }
  ```
  On this rig, sitting idle on the Settings screen with no AA session produces essentially no
  `AppLog` traffic, so a quick Start→Stop reliably hits this.
- `AppLog.init()`'s early-return branch when capture is disabled (`AppLog.kt:150-154`) calls
  `closeAppLogFileLogger()`, which only nulls `appLogFileLogger` (`AppLog.kt:277-280`) — it never
  clears `lastAppLogFile`. `currentLogSource` is still set to `APPLOG_FILE` in this same branch
  (line 153), even though no file logger is actually active.
- `LogExporter.saveLogToPublicFile()`'s `APPLOG_FILE` branch (`LogExporter.kt:352-355`):
  ```kotlin
  if (AppLog.logSource == Settings.LogSource.APPLOG_FILE) {
      return@withContext (AppLog.currentLogFile ?: AppLog.lastLogFile)
          ?.takeIf { it.exists() && it.length() > 0 }
  }
  ```
  `currentLogFile` is null (logger closed); `lastLogFile` still points at the path that was just
  deleted, so `.exists()` is false, `takeIf` yields null, and the function returns `null`.
  **No `AppLog.e`/`AppLog.w` call fires anywhere on this path** — it is a silent `null`.
- `SettingsFragment.kt:3026-3027` receives `logFile == null` and shows the generic
  `R.string.failed_export_logs` toast — indistinguishable from every other failure mode.

Captured evidence (device-local time, both reproductions show the identical shape — one
`LogExporter: session` banner and then nothing else from the app):

```
09-24 23:43:06.888  4122 11747 W OPENHU  : [107] 2.invokeSuspend | LogExporter: session | build=3.5.0-alpha (113) github/debug commit=d9e519259733 | ...
09-24 23:43:09.542  1340  3048 W NotificationService: Toast already killed. pkg=com.andrerinas.headunitrevived token=android.os.BinderProxy@44e0d76
```

The 2.65s gap between the banner and the system's toast-kill-timeout message matches a
`Toast.LENGTH_SHORT` (`SettingsFragment.kt:3027`) firing and finishing normally before the OS's own
delayed cleanup handler runs — i.e. the failure toast did show, the user just has nothing telling
them why.

Note: the session banner itself (`AppLog.w(sessionBanner(...))`, `LogExporter.kt:348`) is logged
*before* the `APPLOG_FILE` check, and only reaches logcat because `LOGGER` is already back to
`Logger.Android()` by the time Export runs (post-Stop) — this is expected, not a second bug.

## Suggested fix

In `LogExporter.saveLogToPublicFile()`'s `APPLOG_FILE` branch (`LogExporter.kt:352-355`): when
`currentLogFile`/`lastLogFile` is null or the file is empty/gone, fall back to `dumpRingBuffer()`
(the same logcat-based path already used for the `LOGCAT` source) instead of returning `null`
outright, so a Start-then-immediately-Stop-then-Export still produces something. A secondary,
smaller fix: log *why* on the null path (e.g. `AppLog.w("LogExporter: APPLOG_FILE selected but no
capture file exists (empty capture?), falling back to ring buffer")`) so a future silent failure is
diagnosable from a user's exported log without needing a live repro.

## Anything not asked about

Not investigated: whether the same empty-file-delete race can strand a *non-empty* capture that
rolled segments (`capturePreviousFile`/`captureFile` join path, `LogExporter.kt:361-384`) — this
finding is scoped to the `APPLOG_FILE`-source, zero-traffic case actually reproduced here.
