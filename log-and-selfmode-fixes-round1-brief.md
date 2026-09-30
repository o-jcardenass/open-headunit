# log-and-selfmode-fixes — round 1 brief

## 1. Build and baseline

**Candidate:** `fork/fix/log-export-anr-and-self-mode-double-launch` @ `809adff3`, candidate-only.

```bash
git fetch fork fix/log-export-anr-and-self-mode-double-launch
git checkout -B fix/log-export-anr-and-self-mode-double-launch fork/fix/log-export-anr-and-self-mode-double-launch
git log --oneline -4
# 809adff3 Logging: stop requesting READ_LOGS on Android 13+
# f03bf606 Self Mode: a second launch request is a duplicate, not a second launch
# a5cd6fc8 Logging: the export button cannot wait on logcat forever, or on the main thread
# 4e5be786 Merge pull request #898 ... (= current main)
```

No baseline APK. `809adff3` and `a5cd6fc8` are candidate-only. `f03bf606`'s regression is already
on the hardware record: `post-beta1-latency-instruments` round 2's D-POCO notes measured
`auto-start-self-mode` + an explicit `ACTION_START_SELF_MODE` double-launching and the second launch
tripping `quiesceWirelessForWiredSession | USB session established` on the 17.4+ route, collapsing to
`nothing connected within 10000ms`. This round shows the fix; it does not re-measure the break.

History since last round: none, this is a fresh branch off `4e5be786`.

## 2. What this is and why it exists

Three commits, one branch:

- **`a5cd6fc8`** — `LogExporter.saveLogToPublicFile` fell through to a `logcat -d` + `copyTo` +
  `waitFor()` that ran synchronously in a fragment click handler, i.e. on the main thread. On a ROM
  that gates `logcat` behind the system consent dialog, an untapped dialog means `waitFor` never
  returns and the app ANRs. Seen in `post-beta1-latency-instruments` round 2, R7. The dump is now
  `suspend` / `Dispatchers.IO`, both callers wrap it in `lifecycleScope.launch`, and the dump is
  bounded at `RING_BUFFER_TIMEOUT_MS = 10_000` served by `Process.destroy()`.

- **`f03bf606`** — `auto-start-self-mode` and an explicit `ACTION_START_SELF_MODE` both reach
  `SelfLauncherManager.start()` and nothing stopped both running. On the AA 17.4+ route there is one
  launcher dialing `127.0.0.1:5277`; the second request runs out of launchers, logs
  `SelfMode: All launchers failed` and calls `emitError`, which disconnects the session the first
  request just established. `SelfLaunchCoalescePolicy` adds two guards: one launch at a time
  (`shouldStart`), and "all launchers failed" is reportable only while nothing has connected
  (`mayReportAllLaunchersFailed`). `launchInFlight` is `@Volatile`, cleared by the launch itself and
  by `clearLaunchInFlight()` on a Self Mode disconnect, so a stop mid-launch cannot strand the
  feature.

- **`809adff3`** — `READ_LOGS` (added in `f98de46b` / #887, shipped in `v.3.3.0-beta1`) is scoped to
  `android:maxSdkVersion="32"`. On Android 13+ a granted `READ_LOGS` makes the app a privileged logd
  reader and forces every `logcat` spawn through `LogcatManagerService` (consent dialog when
  foreground, silent own-UID downgrade when a background service); nothing suppresses that. Scoped
  out, the app is an ordinary reader on 13+: logd serves its own UID's lines, Java and native,
  immediately and with no dialog. Full mechanism in `logcat-access-android13-findings.md`.

## 3. What is different about this round

- Three devices, all running Self Mode (loopback: Gearhead + the head-unit app on one device).
  - **D-HU** `27870808938846` (UNISOC MT50, Android 14 / API 34) — legacy Self Mode route. The
    device that showed the `LogAccessDialog`. `READ_LOGS` currently not granted.
  - **D-POCO** `4f4027e9` (M2007J20CG, Android 15 / API 35) — **AA 17.4+ route**, dev head-unit
    server on `127.0.0.1:5277`. **Do not force-stop Gearhead** (`KILL_GH=0` in
    `run_selfmode.sh`): it kills `DeveloperHeadUnitNetworkService` on `:5277` and only the AA
    Developer-settings "Start head unit server" toggle brings it back (operator-only). If `:5277`
    is not listening at round start, R3 is **UNTESTABLE** here, not a FAIL.
  - **D-MOTO** `ZY22GC3BM4` (motorola edge 30 neo, Android 14 / API 34) — legacy Self Mode route.
- `run_selfmode.sh`'s default capture filter (`OPENHU:V ActivityManager:I '*:S'`) hides SystemUI and
  native tags. This round needs an unfiltered capture (§2) to see `LogAccessDialogActivity` and the
  native decoder lines. A round-specific capture script is expected in Setup notes.
- The "Export Logs" control has no exported intent. R2 needs a bounded UI interaction (open
  `SettingsActivity`, use the in-app "Search settings" field, tap the row); keep it to the minimum
  and record it in Setup notes.
- On the candidate, the ANR's original hang path is unreachable: `809adff3` removes the permission,
  so `logcat -d` returns own-UID output in well under a second and never sits on the consent gate.
  R2's "the 10 s bound fires" sub-check is therefore **INCONCLUSIVE by construction**; R2 verifies
  the export completes and the UI never goes unresponsive.

## 4. Settings keys this round needs

| Key | Type | Value | Runs |
|---|---|---|---|
| `log-capture-enabled` | boolean | `true` | R1, R2 |
| `log-level` | int | `2` (INFO) | R1, R2 |
| `auto-start-self-mode` | boolean | `true` | R3, R5 |
| `auto-start-self-mode` | boolean | delete / `false` | R4 |

`log-source` must stay `0` (LOGCAT) or absent throughout; a run that finds it flipped to `1`
(APPLOG_FILE) reports that as the result. Diff `settings.xml` against a fresh backup at round start
and state the delta in Setup notes.

## 5. The lines that decide every run

Verified with `git show` against `809adff3`.

**`809adff3` (R1):**
- Consent dialog, must be **absent**: `com.android.systemui/.logcat.LogAccessDialogActivity` in any
  `START u0` / `ActivityTaskManager` line, and the string `LogAccessDialog` anywhere.
- `LogExporter.kt:100` — `LogExporter: session |` — must be present in the app's `HUR_Log_*.txt`.
- `LogExporter.kt:232` — `LogExporter: Logcat capture produced 0 bytes` — must be **absent**.
- `LogExporter.kt:236` path — `Automatically switching to Direct to file (APPLOG_FILE)` — **absent**.

**`f03bf606` (R3, R5):**
- Coalesce, one of these present: `SelfMode: a launch is already in flight; ignoring this request
  rather than starting a second one` / `SelfMode: a launch is already connected; ignoring this
  request rather than starting a second one` (`SelfLauncherManager.kt:104-108`).
- Belt-and-braces guard: `SelfMode: launchers failed but a session is connected; leaving it alone`
  (`SelfLauncherManager.kt:174`).
- Route detection: `SelfMode: AA 17.4+ detected. Connecting directly to Headunit Server on
  127.0.0.1:5277...` (D-POCO) / `SelfMode: AA < 17.4 detected. Starting WirelessServer on 5288 and
  running legacy triggers...` (D-HU, D-MOTO).
- The collapse, all must be **absent** on the candidate: `SelfMode: All launchers failed` followed
  by a disconnect, `SelfMode: nothing connected within 10000ms of the launch`, `AapService: USB
  session established while wireless mode`, `AapService: Self Mode disconnected. Not restarting.`
- Healthy session: `SSL handshake complete` (`AapSslContext.kt:97/99`) and/or `SelfMode: Launch of
  '<name>' had no issues` (`SelfLauncherManager.kt:151`).

**`a5cd6fc8` (R2):**
- ANR, must be **absent**: `ANR in com.andrerinas.headunitrevived`, `Input dispatching timed out`.
- Bound firing, expected **absent** on the candidate: `LogExporter: the logcat ring-buffer dump
  produced nothing in 10000ms` (`LogExporter.kt`).
- Success: the in-app "Logs exported" dialog, or a fresh `HUR_Log_*.txt` in the app's files dir.

## 6. Runs

### R0 — build + unit tests (gate)

```bash
taskset -c 0,2,4,6 hur-wifi-test-scripts/build_hur.sh
hur-wifi-test-scripts/run_unit_tests.sh
# then: adb -s <dev> install -r <apk> on all three; confirm live md5 on each
```

- **PASS**: `assembleGithubDebug` clean, `testGithubDebugUnitTest` all pass,
  `SelfLaunchCoalescePolicyTest` present (5 test methods). Record the exact total and the candidate
  md5.
- **FAIL** stops the round.

### R1 — `809adff3`: no consent dialog, capture works (D-HU) — POINT OF THE ROUND

Setup: back up `settings.xml`; write `log-capture-enabled=true`, `log-level=2` (app stopped);
verify `adb -s 27870808938846 shell pm grant com.andrerinas.headunitrevived
android.permission.READ_LOGS` is **rejected**, and `dumpsys package` shows `READ_LOGS` requested but
not granted. Start an unfiltered `stdbuf -oL adb logcat -v time` capture, then launch Self Mode with
the app foreground and keep the screen animating ~3 min (`run_selfmode.sh` pattern, `KILL_GH=1`,
`SEND_INTENT=0`). Pull the app's `HUR_Log_*.txt` from
`/storage/emulated/0/Android/data/com.andrerinas.headunitrevived/files/`.

- **PASS**: no `LogAccessDialogActivity` / `LogAccessDialog` in the capture or in `dumpsys activity
  activities`; a Self Mode session forms (`SSL handshake complete`); the `HUR_Log_*.txt` is
  non-empty, opens with `LogExporter: session |`, and contains both app `OPENHU` lines and at least
  one native line (`OMXClient` / `CCodec` / `c2.*` / `ACodec`); `LogExporter: ... produced 0 bytes`
  absent; no `APPLOG_FILE` switch; `log-source` still `0` on disk.
- **FAIL**: the dialog appears, or the capture switches to `APPLOG_FILE`, or `HUR_Log` is empty.

### R2 — `a5cd6fc8`: export button, no ANR (D-HU)

Setup: continue from R1 with capture still enabled and a session live. Open the settings screen,
use the in-app "Search settings" field to reach "Export Logs", tap it. Within the next 15 s send a
few `input keyevent` / `input tap` events and take a screenshot, to prove the UI is still
responsive. Check `dumpsys activity` / the capture for an ANR.

- **PASS**: no `ANR in com.andrerinas.headunitrevived`, no `Input dispatching timed out`; the app
  stays foreground and responds to input; an export file is produced or the "Logs exported" dialog
  shows.
- **INCONCLUSIVE (sub-check)**: `LogExporter: the logcat ring-buffer dump produced nothing in
  10000ms` cannot be exercised here (the permission is gone) — note it, coverage is on code review.
- **FAIL**: the app ANRs, or the export produces nothing and logs an error.

### R3 — `f03bf606`: 17.4+ double-launch coalesced (D-POCO) — POINT OF THE ROUND

Precheck: `adb -s 4f4027e9 shell "cat /proc/net/tcp6 /proc/net/tcp | grep -i ':1495'"` (0x1495 =
5277) or `ss -ltn`, to confirm the dev head-unit server is listening. If not → **UNTESTABLE**.

Setup: `KILL_GH=0`; write `auto-start-self-mode=true` (app stopped); start an unfiltered capture;
launch `MainActivity`; **+3 s** fire `am start -a com.andrerinas.openheadunit.ACTION_START_SELF_MODE`
(this is `run_selfmode.sh KILL_GH=0 SEND_INTENT=1`). Hold 90 s.

- **PASS**: `SelfMode: AA 17.4+ detected`; a coalesce line fires (`a launch is already in flight`
  or `already connected; ignoring this request`); a session forms and holds ≥ 60 s (`SSL handshake
  complete`, throughput continuing); **none of** `SelfMode: nothing connected within 10000ms`,
  `SelfMode: All launchers failed` + a disconnect, `USB session established while wireless mode`,
  `Self Mode disconnected. Not restarting.`
- **FAIL**: the session collapses, or any of those absent-lines appears with a teardown.

### R4 — `f03bf606`: legacy happy path + re-arm intact (D-MOTO)

Setup: delete `auto-start-self-mode` (or set `false`); single launch, **no** explicit intent
(`SEND_INTENT=0`); legacy route. Let a session form and hold 60 s. Then a clean
`headunit://exit`, wait 5 s, `force-stop`, relaunch, and let a **second** session form.

- **PASS**: first session forms and holds (`AA < 17.4 detected`, `SSL handshake complete`, no
  teardown in 60 s); the second session also forms after the exit/relaunch (proves
  `clearLaunchInFlight()` on disconnect did not leave `launchInFlight` stuck); no `All launchers
  failed`.
- **FAIL**: the second launch never connects and logs the coalesce line (would mean `launchInFlight`
  was stranded).

### R5 — `f03bf606`: legacy double-launch also clean (D-HU)

Setup: `auto-start-self-mode=true` + explicit `ACTION_START_SELF_MODE` at +3 s
(`KILL_GH=1 SEND_INTENT=1`); legacy route. Hold 90 s.

- **PASS**: a session forms and holds ≥ 60 s; if the duplicate arrived mid-launch, the coalesce
  line fired; no `All launchers failed` that leads to a disconnect, no `Self Mode disconnected`.
  (Legacy absorbed this on baseline too, so this is a no-regression check.)
- **FAIL**: teardown, or a duplicate launch disconnects the session.

## 7. Do not re-run

- The `READ_LOGS` / consent-dialog *mechanism* — settled in `logcat-access-android13-findings.md`.
- The self-mode double-launch *break* on 17.4+ — already on the hardware record
  (`post-beta1-latency-instruments` round 2).
- `SelfLaunchTimeoutPolicy` (`mayDisconnect=false`, 30 s legacy deadline) — that is the earlier
  `post-beta1-self-mode` fix, already tested and on `main`.

## 8. Report back

1. Did the `LogAccessDialog` appear on D-HU with the candidate? (expected: no)
2. Did the in-app "Export Logs" ANR? (expected: no)
3. On D-POCO's 17.4+ route, did `auto-start` + explicit intent produce one session that held, with a
   coalesce line and none of the collapse lines? (expected: yes)
4. Any regression to the legacy Self Mode happy path or its re-arm? (expected: none)
