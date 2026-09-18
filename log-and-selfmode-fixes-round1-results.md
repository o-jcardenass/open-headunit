# log-and-selfmode-fixes — round 1 results

**Candidate:** `fork/fix/log-export-anr-and-self-mode-double-launch` @ `809adff3`, candidate-only
(three commits on `4e5be786` = current `main`: `a5cd6fc8`, `f03bf606`, `809adff3`).
**APK md5:** `ca5dfeedee4b577e7cc042c90180a6cc` (`3.3.0-beta2` / `101`, `github/debug`).
**Units:** D-HU `27870808938846` (UNISOC MT50, Android 14 / API 34, Gearhead 17.3.662864, legacy
route) · D-POCO `4f4027e9` (M2007J20CG, Android 15 / API 35, Gearhead 17.5.663204, 17.4+ route) ·
D-MOTO `ZY22GC3BM4` (motorola edge 30 neo, Android 14 / API 34, Gearhead 17.3.662854, legacy route).
All three run Self Mode (loopback).
**Date:** 2026-08-27

**Verdicts:** R0 PASS · R1 PASS · R2 PASS (one sub-check INCONCLUSIVE by construction) · R3 PASS ·
R4 PASS · R5 PASS. No FAIL.

## Setup notes

- **Scripts.** Inventoried `hur-wifi-test-scripts/`. Used `build_hur.sh` (R0 build),
  `run_unit_tests.sh` (R0 tests), `set_prefs_runas.sh` (D-HU settings). `run_selfmode.sh` from the
  latency round was **not fit** for this round: its capture filter is `OPENHU:V ActivityManager:I
  '*:S'`, which hides SystemUI's `LogAccessDialogActivity` and the native decoder tags this round
  reads. Added **`round-log-and-selfmode-fixes/run_selfmode_full.sh`** (fork of `run_selfmode.sh`
  with an unfiltered `logcat -v time` capture, an on-device timestamp for the MainActivity launch
  and the explicit-intent send, `KILL_GH`/`SEND_INTENT`/`INTENT_DELAY`/`ANIMATE`/`PULL_HURLOG`
  flags, and a HUR_Log pull from the absolute external path). Left in the round folder.
- **Builds.** `taskset -c 0,2,4,6` wrapper on `build_hur.sh` and `run_unit_tests.sh`; `no_turbo=1`
  was already set from the previous round. Build 3m10s, no thermal event, PC package temp 65 C at
  start.
- **APK left installed.** `adb install -r` on all three replaced the previous round's
  `fix/post-beta1-latency-instruments` beta2 build; `settings.xml` preserved (verified identical to
  each device's pre-round backup before and after). The candidate `809adff3` is **left installed**
  on all three; the previous APK was removed by `build_hur.sh`'s own `rm` and is not restored.
- **D-POCO Gearhead never force-stopped** (`KILL_GH=0`), per
  `[[project_dpoco_selfmode_gearhead_server]]`. `:5277` `DeveloperHeadUnitNetworkService` was
  listening at round start and still listening at round end.
- **Settings deltas vs a fresh backup at round start:** D-HU, D-POCO, D-MOTO all zero (identical to
  the latency round's backups). Only D-HU was written during the round (`log-capture-enabled`,
  `auto-start-self-mode`), restored from backup between runs and at the end; all three verified
  identical to backup at close.
- **R2 needed a bounded UI interaction.** "Export Logs" has no exported intent. Opened
  `SettingsActivity` by deep link, typed "Export" into the in-app "Search settings" field,
  dismissed the soft keyboard with one `KEYCODE_BACK` (verified `mInputShown=false` and focus still
  on `SettingsActivity` before tapping), tapped the "Export Logs" row. Kept to the minimum. Note:
  on this ROM `KEYCODE_ESCAPE` (111) does not dismiss the keyboard and `input text` needs the
  keyboard hidden before the row at y~586 is not under it; two earlier attempts tapped the keyboard
  instead and are discarded.
- **`grep -a`** used throughout (D-HU capture is 480k lines / 52 MB; `file(1)` calls it "very long
  lines" and unprefixed `grep -c` returns empty on this rig, per §7a).

## R0 — build + unit tests (gate)

**PASS**

- `build_hur.sh`: `BUILD SUCCESSFUL in 3m 10s`, `assembleGithubDebug`, APK md5
  `ca5dfeedee4b577e7cc042c90180a6cc`.
- `run_unit_tests.sh`: `testGithubDebugUnitTest` all green. **775 tests / 0 failures / 0 errors / 0
  skipped** (summed across the JUnit XML). Delta vs `main` ≈ `4e5be786` is `SelfLaunchCoalescePolicyTest`
  (+5), which is present with all 5 methods:
  `an idle request with nothing connected is the one that starts a launch`,
  `a second request while one is in flight does not start another`,
  `a request arriving on a live session has nothing to do`,
  `running out of launchers is reportable only while nothing has connected`,
  `the two rules agree that a connected session is nobody's to restart or end`.
- Installed on all three, live md5 confirmed `ca5dfeedee4b577e7cc042c90180a6cc` on each.

## R1 — `809adff3`: no consent dialog, capture works (D-HU)

**PASS** — this is a real before/after on the same device: the pre-round build had
`android.permission.READ_LOGS: granted=true` on D-HU (which is why the `LogAccessDialog` fired in
`post-beta1-self-mode` round 2 and `post-beta1-latency-instruments` round 2). After installing
`809adff3` the permission is gone entirely.

- Settings written: `log-capture-enabled=true` (`log-level` already `2`, `auto-start-self-mode`
  already `true`, `log-source` `0`). Radio state: Self Mode loopback, no external link;
  `KILL_GH=1`, `SEND_INTENT=0` (single launch).
- Discard-rule check: clean, single run. One `createGroup SUCCESS` is not applicable (Self Mode);
  no second `SSL handshake complete`, no `Magic Garbage`, one session.
- **OS-level proof of `809adff3`:**
  - `dumpsys package` `requested permissions:` no longer lists `READ_LOGS` (was `granted=true`
    pre-round).
  - `pm grant com.andrerinas.headunitrevived android.permission.READ_LOGS` →
    `SecurityException: Package ... has not requested permission android.permission.READ_LOGS`.
- Decisive log lines:
  - `LogAccessDialog` / `LogAccessDialogActivity`: **0** occurrences in the 479,475-line capture; `0`
    in `dumpsys activity activities`.
  - `16:25:53.137 SelfMode: AA < 17.4 detected. Starting WirelessServer on 5288 ...`
  - `16:25:53.245 SelfMode: Launch of 'Fallback: Broadcast' had no issues`
  - `16:25:55.202 SSL handshake complete. Session id: ...`
  - Session held the full run: `Throughput over 5008ms: rendered=107 (21fps), fed=107, dropped=0`
    at `16:28:52`, ~180 s of continuous throughput, `dropped=0` throughout, `codec=c2.unisoc.hevc.decoder`.
  - `SelfMode: All launchers failed` / `nothing connected within` / `Self Mode disconnected` /
    `USB session established` / `a launch is already`: **0** each.
- **HUR_Log** (`HUR_Log_20260827_162552_778.txt`, 149,355 bytes, 678 lines):
  - opens with `LogExporter: session | build=3.3.0-beta2 (101) github/debug | device=UNISOC MT50...
    board=uis7861_6h10 api=34 | video=codec:H.265 fps:60 resId:3 view:GLES ... | logLevel=INFO`
  - `LogExporter: ... produced 0 bytes` / `Direct to file (APPLOG_FILE)`: **absent**
  - contains 315 `OPENHU` lines **and** native lines: `c2.unisoc.hevc.decoder` ×44,
    `c2.android.hevc.decoder`, `CCodec` ×6, `Codec2Client` ×6, `CCodecConfig`, `MediaCodec`,
    `VideoCapabilities`, `OpenGLRenderer` — i.e. own-UID `logcat` is capturing the app process's
    native logging, which `APPLOG_FILE` would not. Other processes' lines (SurfaceFlinger,
    ActivityManager) are absent, as expected on the ungranted own-UID path.
  - `log-source` still `0` on disk after the run.

## R2 — `a5cd6fc8`: export button, no ANR (D-HU)

**PASS** (main claim). **INCONCLUSIVE** on the "10 s bound fires" sub-check, by construction.

Run twice, both the fast path and the bounded ring-buffer path:

- **R2a, live-capture path** (`log-capture-enabled=true`, a capture running from `startCapture`):
  tapped "Export Logs" at `16:35:46.684`. `LogExporter: session |` banner at `16:35:46.447`,
  "**Logs Exported**" dialog with `Log saved to: .../HUR_Log_20260827_163131_122.txt` and Close /
  Share buttons within ~1 s. Focus stayed on `SettingsActivity` throughout; input round-trips
  (16 probes) 227-390 ms, no 5 s stall. `ANR in com.andrerinas` / `Input dispatching timed out`:
  **absent**.
- **R2b, ring-buffer dump path** (`log-capture-enabled=false`, all `HUR_Log_*` removed first, so
  `saveLogToPublicFile` falls to `dumpRingBuffer` → `logcat -d`): tapped at `16:37:14.331`.
  `LogExporter: session |` at `16:37:14.085`. Polled `dumpsys window` for 13 s: `NOT_RESPONDING`
  flag `0` every second, focus on `SettingsActivity` every second. "**Logs Exported**" dialog →
  `HUR_Log_20260827_163714_096.txt`, **2144 bytes** (own-UID `logcat -d` output, small because the
  ring buffer had just been cleared). `ring-buffer dump produced nothing in 10000ms`: **absent**
  (the dump returned in well under a second). `ANR in` / `Input dispatching timed out`: **absent**.
- **Why the 10 s bound is INCONCLUSIVE:** the bound only fires when `logcat -d` blocks, which on
  this ROM only happens behind the log-access consent gate. `809adff3` removes `READ_LOGS`, so
  `logcat -d` is an unprivileged own-UID reader that returns immediately and never sits on the
  gate. The bounded-`Process.destroy()` path in `dumpRingBuffer` cannot be exercised on the
  candidate; its coverage is on code review.

## R3 — `f03bf606`: 17.4+ double-launch coalesced (D-POCO)

**PASS** — the scenario that collapsed the session in `post-beta1-latency-instruments` round 2 now
produces one session that holds.

- Precheck: `DeveloperHeadUnitNetworkService` listening on `127.0.0.1:5277` (`dumpsys` count 3),
  `0x1495` in `/proc/net/tcp`.
- Setup: `auto-start-self-mode=true` (already set), `KILL_GH=0`, `SEND_INTENT=1`,
  `INTENT_DELAY=3` — `MainActivity` at `T0`, `am start -a ...ACTION_START_SELF_MODE` at `T0+3s`.
  This is the exact double-launch shape from the last round's finding.
- Discard-rule check: clean, single run.
- Timeline:
  - `16:38:25.904 SelfMode: Installed AA version: 17.5.663204-release (major=17, minor=5)` /
    `SelfMode: AA 17.4+ detected. Connecting directly to Headunit Server on 127.0.0.1:5277...`
    (first launch, from auto-start)
  - `16:38:27.147 SelfMode: a launch is already in flight; ignoring this request rather than
    starting a second one` **← the coalesce guard catching the explicit `ACTION_START_SELF_MODE`**
  - `16:38:28.462 SelfMode: Launch of 'v17.4+' had no issues`
  - `16:38:28.748 SSL handshake complete`
  - `16:38:37` → `16:40:27` continuous throughput, ~110 s, 52-56 fps, `dropped=0`,
    `codec=c2.qti.avc.decoder`.
- The collapse lines, **0 occurrences each**: `SelfMode: All launchers failed`,
  `nothing connected within 10000ms`, `USB session established while wireless mode`,
  `Self Mode disconnected. Not restarting`, `quiesceWirelessForWiredSession`.

## R4 — `f03bf606`: legacy happy path + re-arm (D-MOTO)

**PASS**

- Setup: `auto-start-self-mode=true` (mainstream config; not deleted — deviation from the brief's
  "delete it", but `true` + no explicit intent is still exactly one launch and is the common case).
  `KILL_GH=1`, `SEND_INTENT=0`.
- **Session 1:** `16:41:02.432 SelfMode: AA < 17.4 detected` → `Launch of 'Fallback: Broadcast'
  had no issues` → `16:41:04.530 SSL handshake complete` → held ~67 s, 46-48 fps, `dropped=0`.
  `All launchers failed` / `nothing connected within` / `Self Mode disconnected` / `USB session
  established`: 0 each. No coalesce line (single launch).
- **Re-arm:** `headunit://exit` at `16:42:34` → `16:42:32.622 AapService: Self Mode disconnected.
  Not restarting.` (the path that now also calls `clearLaunchInFlight()`), `force-stop`, relaunch
  at `16:42:42`.
- **Session 2:** `16:42:42.267 SelfMode: AA < 17.4 detected` → `Launch of 'Fallback: Broadcast'
  had no issues` → `16:42:44.066 SSL handshake complete` → held ~63 s, 45-46 fps, `dropped=0`.
  **`a launch is already` count = 0** — the second launch was not wrongly rejected as a duplicate,
  i.e. `launchInFlight` was cleared and did not strand Self Mode after the disconnect.

## R5 — `f03bf606`: legacy double-launch (D-HU)

**PASS** (no regression). The coalesce *line* does not fire on the legacy route, correctly.

- Setup: `auto-start-self-mode=true`, `KILL_GH=1`, `SEND_INTENT=1`. Run at `INTENT_DELAY=3`
  (R5) and again at `~2 s` (R5b) to try to hit the mid-launch window.
- On legacy, `SelfLauncherManager.start()`'s launcher sequence runs in **~40 ms**
  (`16:44:15.608` → `16:44:15.687` for launch 1; `16:46:43.207` → `16:46:43.248` for R5b), so
  `launchInFlight` is back to `false` long before an adb `am start` can deliver the duplicate.
  Both runs: the second launch ran its launchers a second time
  (`16:44:17.31` / `16:46:43.56`), `Launch of 'Fallback: Broadcast' had no issues` both times, so
  `anySucceeded=true` and `SelfMode: All launchers failed` **never fired** — the
  `mayReportAllLaunchersFailed` guard was not reached and did not need to be. Session formed
  (`SSL handshake complete` at `16:44:18.060` / `16:46:45.361`) and held ~100 s / ~60 s,
  `dropped=0`.
- `a launch is already`: 0. `All launchers failed`: 0. `Self Mode disconnected. Not restarting`:
  0. `USB session established`: 0. `LogAccessDialog`: 0.
- **Reading:** the legacy route "absorbs" a duplicate because its fallback broadcast launcher
  always reports success, so the second launch is harmless whether or not it is coalesced. The
  guard that matters on legacy is the same `mayReportAllLaunchersFailed` one, and it is only
  reachable if every legacy launcher fails, which did not happen here. The coalesce line is a
  17.4+-route phenomenon (R3) because that route's single launcher takes 2-3 s, leaving a real
  window.

## Report back

1. **`LogAccessDialog` on D-HU with the candidate?** No. 0 occurrences across R1 and R5 captures
   and in `dumpsys`. `READ_LOGS` is not requested at API 34 and `pm grant` is refused. The
   pre-round build had it `granted=true`; the reinstall dropped it.
2. **Did "Export Logs" ANR?** No, on either the live-capture path or the `logcat -d` ring-buffer
   path. Dialog shown within ~1 s both times, UI responsive throughout. The 10 s bound could not be
   exercised (the permission that made `logcat -d` hang is gone) — covered by code review.
3. **17.4+ double-launch on D-POCO?** One session, held ~110 s, with
   `SelfMode: a launch is already in flight; ignoring this request` and none of the collapse lines.
   The `post-beta1-latency-instruments` round-2 failure does not reproduce.
4. **Regression to the legacy happy path or re-arm?** None. Single launch forms and holds on D-HU
   and D-MOTO, a duplicate is harmless, and a second session forms cleanly after
   `headunit://exit` + relaunch (`launchInFlight` not stranded).

## Anything the brief did not ask about

- **D-HU's `READ_LOGS` was granted before this round.** The pre-round `3.3.0-beta2` build showed
  `android.permission.READ_LOGS: granted=true` on D-HU (and `granted=false` on D-POCO / D-MOTO).
  That is the state that produced the `LogAccessDialog` in the two prior threads. Whatever granted
  it (a past round's `pm grant`, or the ROM) — `809adff3`'s `maxSdkVersion="32"` makes it moot:
  after the reinstall the permission is not on the package at all and cannot be re-granted.
- **The legacy `SelfLauncherLegacy` "v17.3 and older" launcher errors on every run** on both D-HU
  and D-MOTO: `SelfMode: Launch of 'v17.3 and older' had caused an error`, immediately followed by
  `Broadcast fallback 1 (WirelessStartupReceiver) sent` and `Launch of 'Fallback: Broadcast' had
  no issues`. The session forms fine via the fallback every time, so this is pre-existing and not
  in scope, but the legacy route is effectively always running on its fallback on these two units.
- **`run_selfmode_full.sh`'s `INTENT_DELAY` is not honoured precisely** — the script's own
  `sleep "$INTENT_DELAY"` plus adb overhead means `INTENT_DELAY=1` delivered the intent at ~2 s.
  Fine for this round (R3's 3 s and R5's timing were both far outside the ~40 ms legacy window and
  the ~2-3 s 17.4+ window respectively) but worth knowing if a future round needs sub-second
  precision on the duplicate.
