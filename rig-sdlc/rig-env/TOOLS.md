# rig-toolkit: tool inventory

The shared, proven scripts for rig rounds. **Read this before writing a script.** If one of these does the job
(with env vars or arguments), use it. Round-specific scripts are still fine, but they live with their round
(`~/Coding/StudioProjects/rig-data/rounds/<topic>-round<N>/scripts/`) and are promoted here only when a second
round needs the same thing.

Paths: this folder is `~/Coding/StudioProjects/rig-toolkit/` (formerly `hur-wifi-test-scripts`, renamed 2026-10-09). Bulky output never goes here: captures, APKs and screenshots go to `~/Coding/StudioProjects/rig-data/`.

Every entry has the same fields, so a human can skim it and an agent can match a task to a tool:
**Does** (one line), **Use when**, **Usage**, **Inputs** (env vars and arguments), **Needs**, **Writes**, **Notes**.

## Pick a tool

| I need to... | Use |
|---|---|
| Know which device is which, check devices are present | `rig_devices.sh` (source it) |
| Check radios/links before a round | `rig_preflight.sh` |
| Find and stop leftovers after a round | `rig_cleanup.sh` |
| See if the branch's skills, agents, hooks or TOOLS.md changed vs this machine | `rig_sdlc_drift.sh` |
| Keep the host cool during builds/runs | `rig_thermal.sh`, `thermal_guarded.sh` |
| Build the app or the helper | `build_hur.sh`, `build_hur_cool.sh`, `build_hur_playstore.sh`, `build_wireless_helper.sh`, `build_all.sh` |
| Run unit tests | `run_unit_tests.sh` |
| Install an APK / prove the installed APK is the one I built | `install_all.sh`, `install_and_launch.sh`, `apk_identity.sh` |
| Publish a test APK | `upload_release_apk.sh` |
| Change app settings on a device | `set_hu_settings_host.py` (rooted), `set_prefs_runas_host.py` (not rooted), `set_pref_hostedit.sh` (one key, emulator), `set_prefs_runas.sh`, `seed_hu_settings_fresh.sh`, `restore_settings.sh` |
| Do one Native AA bring-up capture | `native_aa_bringup.sh` |
| Read a capture (stalls, bitrate, scans, load) | `recv_gaps.py`, `wire_bitrate.py`, `scan_vs_gaps.py`, `load3.py` |
| Help a run along (banner, screenshots, hotspot, forget car) | `banner_watch.sh`, `periodic_screencap.sh`, `connect_hotspot.sh`, `forget_car_gearhead.sh` |
| Feed a moving GPS position to the phone | `mock_drive.sh` |
| Test without hardware | `emu.sh`, `smoke.sh`, `selfmode_smoke.sh`, `selfmode_session_probe.sh` |
| Pull the ZLink app for inspection | `extract_zlink.sh` |
| Extract script blocks from a brief | `extract_brief_blocks.py` |
| Create a worktree / see what is safe to delete | `wt-new.sh`, `studio-clean.sh` |

## Rig and devices

### rig_devices.sh
- **Does:** one place for the rig's device roles, paths and two helpers.
- **Use when:** at the top of any round script. Serials change when hardware is replugged, so take them from here instead of typing one.
- **Usage:** `source rig_devices.sh`.
- **Provides:** roles `D_HU`, `D_POCO`, `D_MOTO`, `D_HP`, `D_SAM` (`D_T230` is an alias of `D_SAM`); `HUR_PKG`, `RIG_LOCK` (`/tmp/ohu-rig.lock`), `RIG_TOOLKIT`, `RIG_DATA`; functions:
  - `rig_check_devices D_HU D_POCO ...` returns 1 and prints `MISSING <role>=<serial>` for any role not in `adb devices`.
  - `rig_evidence_dir <thread> <N>` creates and prints `rig-data/rounds/<thread>-round<N>`.
  - `rig_marker <serial> <tag>` writes an `AutomationMarker:` line to that device's log (tag without spaces).
- **Needs:** `adb`. **Writes:** only what `rig_evidence_dir` creates.
- **Notes:** the serials in the file are the source of truth; several other scripts still default `HU` to the D_HU serial, so pass `HU=$D_HU` when the serial has changed.

### rig_preflight.sh
- **Does:** read-only pre-round check; prints one table (adb state, wifi, bluetooth, HFP/A2DP, screen) to paste into Setup notes.
- **Use when:** before every round, and again after any restart.
- **Usage:** `rig_preflight.sh ROLE[:need,need] ...`, e.g. `rig_preflight.sh D_HU:wifi,bt D_MOTO:wifi,bt D_POCO`.
- **Notes:** prints `PREFLIGHT OK` or `PREFLIGHT FAILED: stop, tell the operator, do not start the round`. A needed radio that is off is a FAIL (exit 1). It never turns anything on (the head unit's WiFi re-join needs an operator tap). It also fails on strays from `rig_cleanup.sh list` (stray logcat, held rig lock). Paired round: `RIG_KEEP_PIDS="<pids>"` and `RIG_KEEP_LOCKS="<lock paths>"` name the other round's logcat and lock; they are printed as `kept (paired round)` and do not fail.

### rig_cleanup.sh
- **Does:** finds (and optionally stops) what a round leaves behind: logcat captures, soak loops, thermal loops, orphaned adb input callers, idle Gradle daemons, the rig-lock holder.
- **Use when:** at the end of a round, before ending the session.
- **Usage:** `rig_cleanup.sh [list|kill]` (default `list`).
- **Notes:** exit 0 and `rig clean: no strays, lock free` when clean, exit 1 when it finds something. Paired round: `RIG_KEEP_PIDS` (space-separated pids) and `RIG_KEEP_LOCKS` (lock paths) are listed as `kept (paired round)`, never killed, and do not make the exit status 1. Do not use `kill` on a wanted Monitor pipe; use TaskStop for that.

### rig_sdlc_drift.sh
- **Does:** read-only report of what `fork/transfer/rig-rounds` carries under `rig-sdlc/` (agents, skills, hooks, `TOOLS.md`) that differs from the live copy on this machine. With `apply` it copies the agent and skills into `headunit-revived/.claude/`.
- **Use when:** at the start of a session in `headunit-revived`, and before any `/rig-round`.
- **Usage:** `rig_sdlc_drift.sh [check|apply]` (default `check`).
- **Needs:** network for `git fetch fork transfer/rig-rounds`; no worktree checkout, no device.
- **Notes:** exit 0 when everything matches, 1 when something differs. Compares `.claude/agents/*.md` and `.claude/skills/*/SKILL.md` of the main checkout, `~/.claude/hooks/rig-*`, and `rig-toolkit/TOOLS.md`. `apply` copies the branch over the live files one way, so it loses a rig edit that has not gone back to the branch yet: use `python3 -I rig-sdlc/rig-env/rig_sync.py sync` in the transfer worktree instead, which carries edits both ways. `check` stays useful as a quick read-only look.

### rig_thermal.sh
- **Does:** reads the host PC's CPU temperature and throttling (`/sys/class/thermal`, `/proc/cpuinfo`, cpu0 `thermal_throttle`), not a device's.
- **Usage:** `rig_thermal.sh status` | `wait [maxC]` (default 75, up to 10 min, exit 1 if it never cools) | `watch <logfile>` (appends every 20 s; run in background, kill by pid).

### thermal_guarded.sh
- **Does:** runs a command and pauses its whole process tree (SIGSTOP) when the CPU package is too hot, resumes (SIGCONT) when cool.
- **Use when:** Gradle builds on this host (it has reached 95 C).
- **Usage:** `HOT=88 COOL=70 thermal_guarded.sh <cmd> [args...]`. Freezes the whole descendant tree plus detached Gradle/Kotlin daemons (since 2026-10-10; before, only the client was frozen and the daemon kept heating the CPU). Default HOT 95, COOL 85 (raised on request: the host has run past 91C without a shutdown; the old 86/68 and 88/70 cost build time), polls every 2 s (`POLL`).

## Build and install

### build_hur.sh / build_hur_playstore.sh / build_wireless_helper.sh / build_all.sh
- **Does:** `assembleGithubDebug` (build_hur), `assemblePlaystoreDebug` (playstore flavor; note it does not compile on main, see CLAUDE.md), `assembleDebug` of wireless-helper, or both (build_all, helper first).
- **Usage:** `HUR_DIR=<worktree> [MAX_WORKERS=2] build_hur.sh` (`MAX_WORKERS` adds `--max-workers=N` to Gradle; default `HUR_DIR` is `headunit-revived/`). `build_hur_playstore.sh` and `build_wireless_helper.sh` take no env vars: they build `headunit-revived/` and `wireless-helper/` respectively. `build_all.sh` runs the helper first, then `build_hur.sh`.
- **Writes:** the APK into `rig-data/apks/` and the Gradle log into `rig-data/scratch/results/` (`hur_gradle_out.txt`, `hur_gradle_playstore_out.txt`, `wh_gradle_out.txt`). `JAVA_HOME` is set to Android Studio's JBR.
- **Notes:** per project rules, builds are run on request only. To build a worktree under `worktrees/`, use `build_hur.sh` with `HUR_DIR` (not the playstore script).

### build_hur_cool.sh
- **Does:** `build_hur.sh` under `thermal_guarded.sh` (stops Gradle at 95 C, resumes at 85 C).
- **Usage:** `HUR_DIR=<worktree> [HOT=95 COOL=85] [MAX_WORKERS=2] build_hur_cool.sh` (passes its environment through to `build_hur.sh`).

### run_unit_tests.sh
- **Does:** `testGithubDebugUnitTest` for headunit-revived. **Use when:** gate before a round (build_hur.sh does not run tests).
- **Usage:** `HUR_DIR=<worktree> run_unit_tests.sh`. **Writes:** `rig-data/scratch/results/hur_unittest_out.txt`.

### refresh_apks.sh
- **Does:** copies the newest built debug APKs of both projects into `rig-data/apks/`, replacing what was there.

### install_all.sh
- **Does:** installs one APK on every adb-connected device, de-duplicated by hardware serial (USB + TCP/IP counts once).
- **Usage:** `install_all.sh [path/to/app.apk]` (default: newest in `rig-data/apks/`). **Env:** `LAUNCH=1` relaunches after install.

### install_and_launch.sh
- **Does:** builds (via build_hur.sh), installs on the head unit, brings it to the foreground. Package and launcher activity are read from the APK, not hardcoded.
- **Env:** `SKIP_BUILD=1` reuse the newest APK in `rig-data/apks/` (override the folder with `APKS_DIR`); `HU=<serial>` target (default is the D_HU serial; use `emulator-5554` for the AVD).

### apk_identity.sh
- **Does:** proves the APK installed on each device is byte-identical to a given APK (pulls base.apk, compares md5 on the host); prints versionName and lastUpdateTime.
- **Usage:** `[PKG=<package>] apk_identity.sh <apk> <serial> [<serial> ...]` (`PKG` defaults to `com.andrerinas.headunitrevived`). Exit 0 only if every device matches.
- **Use when:** after every install in a round, and put the md5s in the results header.

### upload_release_apk.sh
- **Does:** uploads a built APK as an asset on the shared "OHU Fixes" test release on the fork, renamed `OHU<version><feature>_test.apk`.
- **Usage:** `upload_release_apk.sh [feature] [path/to/app.apk]`. Version is read from the APK itself. Same version+feature overwrites (`--clobber`).
- **Notes:** this publishes externally; confirm with the operator first. The repo (`o-jcardenass/open-headunit`) and the release tag (`v.3.4.0`) are fixed inside the script, so check them before use. `APKS_DIR` overrides the APK folder.

## Settings on a device

All of these edit the app's `settings.xml`. Force-stop the app first. None relaunch it, so several keys land atomically.
Never `cp settings.xml settings.xml.bak` inside a pref writer (see the project notes).

### set_hu_settings_host.py
- **Does:** pulls the ROOTED head unit's `settings.xml`, applies edits on the host, pushes it back.
- **Usage:** `set_hu_settings_host.py <serial> scalar:<int|boolean|string>:<key>:<value> ... set:<key>:<a>,<b> setclear:<key> del:<key> ...`

### set_prefs_runas_host.py
- **Does:** same operations for a NON-rooted device with a debuggable build, via `run-as`. Handles multi-line `<set>` deletes correctly.
- **Usage:** `[PKG=<package>] set_prefs_runas_host.py <serial> scalar:<int|boolean|string>:<key>:<value> ... set:<key>:<a>,<b> ... del:<key> ...`. It force-stops the app first, unlike the rooted tool.

### set_pref_hostedit.sh
- **Does:** sets ONE key on a debuggable build without needing `sed` on the device (old emulator images). **Usage:** `DEVICE=<serial> [PKG=<package>] set_pref_hostedit.sh <key> <boolean|int|string> <value>`. `DEVICE` is required. Used by `smoke.sh` and `selfmode_smoke.sh`.

### set_prefs_runas.sh
- **Does:** multi-key `run-as` editor (shell version). **Usage:** `DEVICE=<serial> [PKG=<package>] set_prefs_runas.sh SET key:type:value [SET ...] [DEL key ...]`; prints the resulting `settings.xml`. Its `DEL` only handles single-line elements, so for `<set>` blocks use `set_prefs_runas_host.py`. Kept because the Self Mode probe's instructions rely on it.

### seed_hu_settings_fresh.sh
- **Does:** creates `settings.xml` from nothing on the rooted head unit after `pm clear`, with the app's uid/gid and SELinux label.
- **Usage:** `[HU=<serial>] [PKG=<package>] seed_hu_settings_fresh.sh key:type:value ...` (`HU` defaults to the D_HU serial).

### restore_settings.sh
- **Does:** a 1-line script that copies `/data/local/tmp/settings-restore.xml` over `shared_prefs/settings.xml`. **Usage:** `adb push` your backup to `/data/local/tmp/settings-restore.xml`, `adb push` this script to `/data/local/tmp/restore_settings.sh`, then `adb -s <serial> shell run-as <pkg> sh /data/local/tmp/restore_settings.sh`. It does not stop the app; force-stop it first.
- **Use when:** end of every round (always back up before changing, restore at the end).

## Runs and captures

### native_aa_bringup.sh  (was `native_aa_recovery_r1.sh`)
- **Does:** one Native AA (mode 3, WiFi Direct) bring-up capture. Head-unit-only capture; the phone's stock Android Auto connects by itself.
- **Usage:** `RUN=R1c1 ARM=candidate native_aa_bringup.sh`
- **Env:** `RUN` (required), `ARM` (candidate|baseline, default `?`), `HU` and `PH` (head unit and phone serials; they default to the D_HU and D_POCO serials, so set them when they differ), `RECIPE` (`radios-on` default, or `bt-off-launch`), `SETTLE_S` (12), `WAIT_S` (120), `OUTDIR` (default `rig-data/rounds/native-aa-bringup`; for a real round pass `OUTDIR=rig-data/rounds/<topic>-round<N>`). Writes `<OUTDIR>/<RUN>_<ARM>.txt`.
- **Notes:** it was written for one round and is HU-only capture. It sets no preferences: write them first with the settings tools, app stopped. Check the script before relying on it for a different round. Replaces the older per-round bring-up scripts (see Legacy names).

### banner_watch.sh
- **Does:** polls the head unit UI for the "Do you see the Android Auto screen?" bar and taps YES. Matches case-insensitively; needs continuous polling (it can appear seconds after the handshake).
- **Usage:** `HU=<serial> banner_watch.sh <log-file>` (run in the background for the run, kill by pid). `HU` defaults to the D_HU serial; the log defaults to `./banner_watch.log` if you omit it, so always pass a path under `rig-data/`.

### periodic_screencap.sh
- **Does:** takes a screencap every `INTERVAL_S` seconds, `COUNT` times, named `<SHOT_DIR>/<TAG>-NN.png`.
- **Usage:** `[HU=<serial>] [SHOT_DIR=<dir>] [TAG=shot] [INTERVAL_S=3] [COUNT=12] periodic_screencap.sh`. `SHOT_DIR` defaults to the current directory, so set it to a path under `rig-data/`.
- **Notes:** never run several adb screencap loops in parallel against the rig (it froze the head unit once).

### connect_hotspot.sh
- **Does:** connects a rooted head unit's WiFi to a named network whose SSID may contain spaces (pushes a tiny script so adb's argument quoting cannot break it).
- **Usage:** `[HU=<serial>] connect_hotspot.sh "<ssid>" "<password>" [security]` (`security` defaults to `wpa2`; `open` for no password). `HU` defaults to the D_HU serial.

### forget_car_gearhead.sh
- **Does:** forgets the head unit's vehicle entry in Android Auto on the phone by driving the UI (no intent exists), with dynamic bounds lookup. Minimum taps.
- **Usage:** `DEVICE=<serial> forget_car_gearhead.sh [vehicle-name]` (default `Google`).
- **Notes:** UI-driving is a last resort by the house rules; say so in Setup notes.

### mock_drive.sh  (+ `mock_drive_route.gpx`, `mock_drive_route.json`)
- **Does:** feeds the phone's mock GPS a stream of fixes along a real road route so projected Maps keeps moving. One instance only; stop it with TaskStop, not pkill.
- **Usage:** `mock_drive.sh <duration_s> [route_file]` or `mock_drive.sh --fetch "lon,lat;lon,lat" [out_basename]`.
- **Env:** `PHONE`, `SPEED` (12 m/s), `TICK` (1 s), `PROVIDER` (gps), `OSRM`.

## Reading a capture (analysis; never verdicts, just numbers)

| Tool | Does | Usage |
|---|---|---|
| `recv_gaps.py` | Inbound stall profile of an AAP session from `RECV:` timestamps (stall count/duration, dead time, audio bitrate, nearest RIGMARK) | `recv_gaps.py <capture> [stall_threshold_s]` (threshold default 1.2 s) |
| `wire_bitrate.py` | Per-channel inbound wire cost from `RECV: <CH> ... size: N`; needs log-level 0 (VERBOSE) | `wire_bitrate.py <capture>` |
| `scan_vs_gaps.py` | Aligns RECV stalls with WiFi scan markers; a radio-blanking cause shows tight small positive offsets | `scan_vs_gaps.py <capture> <scan_regex> [threshold_s]` (default 1.2 s) |
| `load3.py` | Video load carried by a marker window (counts only) | `load3.py CAP START_LABEL [END_LABEL]` |

These four print a Python traceback, not a usage line, when run without arguments.

Never `Read`/`cat` a whole capture into an agent session (about 140k tokens); grep it or use these.

## No hardware needed

### emu.sh
- **Does:** boots/stops the Android Studio AVD so scripts can target it like the rig. **Usage:** `emu.sh up|down|status|list`. **Env:** `AVD` (default `Tablet_4.1.x`), `EMU_SERIAL` (default `emulator-5554`), `EMU_HEADLESS` (0), `BOOT_TIMEOUT` (120 s), `RESULTS_DIR` (default `rig-data/scratch/results`, holds the emulator log and pid). See `README-emulator.md`.

### smoke.sh
- **Does:** emulator-friendly smoke test: build, install, launch, exercise deep links/broadcasts, round-trip one setting, screenshot, check for crashes. Cannot test WiFi Direct, Bluetooth, Native AA or USB.
- **Usage:** `smoke.sh --emu` (boots the AVD via `emu.sh`) or `DEVICE=<serial> smoke.sh`; `HU=<serial>` is also accepted. **Env:** `SKIP_BUILD=1`, `OUT=<dir>` (default `rig-data/scratch/results/smoke`), `AVD=<name>` with `--emu`.

### selfmode_smoke.sh / selfmode_session_probe.sh
- **Does:** `selfmode_smoke.sh` is the emulator wrapper (`--emu` or `DEVICE=<serial>`; `OUT` default `rig-data/scratch/results/selfmode`, `HOLD` 60 s): it sets `auto-start-self-mode`, launches and reports how far the path got. `selfmode_session_probe.sh` is the underlying probe: `DEVICE=<serial> ARM=<candidate|baseline> OUT=<dir> [HOLD=90] [KILLGH=0] [DISMISS_LOGDIALOG=0]`; it does not change settings, so write them first (`set_prefs_runas.sh`) and leave the app force-stopped.
- **Notes:** a full Self Mode session needs Gearhead and its dev head-unit server; on a bare AVD the verdicts are app-path only.

## Misc

### extract_zlink.sh
- **Does:** pulls the ZLink system app (`com.zjinnova.zlink`, `CarZhiJian.apk`) off the connected head unit for offline inspection. Reference copy is in `rig-data/extracted/`.

### extract_brief_blocks.py
- **Does:** saves each fenced block that follows a `**name.ext**` line in a brief as `OUTDIR/name.ext`. **Usage:** `extract_brief_blocks.py BRIEF.md OUTDIR`.

### wt-new.sh
- **Does:** creates a worktree under `StudioProjects/worktrees/` named `<topic>__<role>__<sha8>__<yyyymmdd>` plus a `.WORKTREE-INFO` file (added once to the app repo's `info/exclude`, so it never makes the app report `-dirty`), and copies the main checkout's untracked `local.properties` (Gradle's `sdk.dir`) into it. **Usage:** `wt-new.sh <topic> <baseline|candidate|review|scratch> <git-ref>`; the last stdout line is the worktree's absolute path, alone (`WT=$(wt-new.sh ... | tail -1)`).

### studio-clean.sh
- **Does:** lists every worktree with SAFE / KEEP / REVIEW and why, any OHU round leftovers sitting directly in your home directory, and toolkit scripts missing from this file; `clean` removes SAFE ones and ages out old raw captures/APKs in `rig-data/` (dry run unless `--apply`).
- **Usage:** `studio-clean.sh [status|clean] [--apply] [--days N]`. Runs in status mode automatically at session end.

## Non-standard (persistent)

Scripts that are not general yet but must outlive their round (the next round is expected to reuse or extend them).
One line each: name, owning round, what it does, what would make it general. Empty today. A script that only
serves one round does not belong here: keep it in `rig-data/rounds/<round>/scripts/`.

| Script | Owner round | Does | To generalize |
|---|---|---|---|

## Legacy names (archived 2026-10-09)

127 one-round scripts were removed from this folder. They are in
`~/Coding/StudioProjects/rig-data/archive/round-scripts-20261009.tar.gz`
(`tar -tzf` to list, `tar -xzf ... <name>` to restore one). Older briefs may still name them:

| Old name(s) | Use now |
|---|---|
| `set_hu_pref.sh`, `set_hu_prefs.sh`, `r6_set_hu_prefs.sh`, `hwr_set_hu.sh`, `bs2_edit_prefs.py`, `set_hu_settings_runas.py` | `set_hu_settings_host.py` (rooted) or `set_prefs_runas_host.py` (not rooted) |
| `set_pref.sh`, `hwr_set_sam.sh` | `set_prefs_runas_host.py` |
| `set_autostart_btmac.sh`, `set_usb_blacklist.sh` | `set_hu_settings_host.py ... set:<key>:<values>` |
| `test_native_aa.sh`, `wds_bringup.sh`, `wireless_bringup_5ghz_*.sh`, `hfp_*.sh`, `native_aa_recovery_r4*.sh`, `r8`, `r9`, `r10` | `native_aa_bringup.sh` (+ settings tool above) |
| `pr1045_*`, `pr1047_*`, `pr_r2_*`, `ks_run`, `xs_run*`, `stack_lib`, `sd_*`, `sd2_*`, `svr*`, `bs2_*`, `bs3_run`, `ebm_*`, `hwr_*`, `udal_*`, `wds_r4_*`, `ultrawide_*`, `usb_diag_capture`, `usb_reattach_r0`, `spf_selfmode_run`, `bt_autodisc_*`, `five_ghz_*`, `e1_exit_teardown_cycles`, `home_wifi_reconnect_*`, `pr_readiness_*`, `s1_wifi_holddown*` | one-round drivers: restore from the archive if you must re-run that exact round; otherwise compose the tools above |
| `test_latest.sh`, `test_after_reboot.sh`, `test_app_restart.sh`, `test_connection_stability.sh`, `bisect_sweep.sh`, `dialog_tap_test.sh` | old wireless-helper-era tests: restore from the archive if needed |
| `pr1045_extract.py`, `pr1045r2_extract.py`, `svr_mkprefs.py` | round-specific parsers: archive |

## Adding a tool (mandatory)

Updating this file is part of creating the script, not a follow-up. A hook refuses to create a script in this folder
until its name is listed here, and `studio-clean.sh status` (run at every session end) lists any script that is not.

0. Decide where it lives: general and reusable, put it here with a full entry; persistent but not general, put it here
   with a row in "Non-standard (persistent)"; for one round only, keep it under `rig-data/rounds/<round>/scripts/`.
1. Search this file first. If a tool nearly fits, extend it with an env var or argument rather than copying it.
2. A new general script gets: a header comment with Does / Usage / Env / Needs / Writes, serials from `rig_devices.sh` or an env var (several older scripts still default `HU` to the D_HU serial; do not copy that), and output under `rig-data/`.
3. Add its entry here in the same format and a row in "Pick a tool". A script without an entry here does not exist for the next agent.
