# pr-1063-restart-audio - round 1 results

**Candidate (M):** `main` @ `145a0c762f0a87386ca63b54518ea5e861b290be` plus `codex/automation-audio-restart` @ `421514779f442206f622b33759af9ef6bfd99e07`, merged locally as `3897b7b3df0441bfc8be92950219f4b5a929693f` (tree `6e0ac4bfdff0d0a18db17a5677f05eec9e950a11`, matches the brief)   **Baseline (B):** `main` @ `145a0c762f0a87386ca63b54518ea5e861b290be`
**APK md5:** M `3b74b20f7b51efec9d24f0eaacffb99a` / B `3100817484e47e9a6418753308e92ed1`
**Unit:** D-HP (HP Slate 7 Plus, Android 4.2.2, API 17) as head unit in Headunit Server mode, `use-aac-audio` true; phone D-POCO, Android Auto `17.9.664004-release`, developer head unit server on port 5277
**Date:** 2026-10-06

## Setup notes

- **Brief resolved from a commit that carried five briefs.** The SHA named five threads in one commit; the operator chose all five in order. This file covers the first. Quirk files read: `rig-quirks/topics/tooling.md`, `rig-quirks/topics/gearhead.md`, `rig-quirks/units/D-HP.md`, `rig-quirks/units/D-POCO.md`.
- **Pre-flight** (`rig_preflight.sh D_HP:wifi D_POCO:wifi`, run twice, identical): D_HP adb ok, WiFi 1, BT 1, Awake, HFP none, A2DP none. D_POCO adb ok, WiFi 1, BT 0, Awake, HFP none, A2DP none. PREFLIGHT OK.
- **Build gates.** B: 2738 JVM tests, 0 failures, 0 errors. M: 2738 JVM tests, 0 failures, 0 errors, `AutomationCommandPolicyTest` present (the XML exists in both trees' results). Builds with `build_hur_cool.sh` (thermal guard), each APK copied out of `apks/` at once.
- **M compile warning (the brief asked for it):** `AapService.kt:3027:13 Duplicate label in when`. It is a warning, the build passed. The same output also lists `MsgType.kt:44:13 Duplicate label in when`, which predates the merge.
- **Worktree deviation.** The two scratch worktrees had no `local.properties`, so the first build failed with `SDK location not found`. I copied the main checkout's `local.properties` (`sdk.dir=/home/oscar/Android/Sdk`) into both and rebuilt. No source change.
- **Host thermal.** Build phase reached 93C once (`thermal-build.log` maximum); the guard pauses Gradle at 86C and no capture was recording. Per-run thermal maximum: R1-B 63C, R1-M 60C, R2-M 59C, `throttle_pkg` flat at 2458 (delta 0 each), so no run was voided.
- **First attempt voided (rig state, not the build).** R1-B on B did not open a session: discovery found the server at `192.168.1.8:5277` and connected, then twice `Handshake: the peer accepted the connection and then sent nothing at all` (18:56:40 and 18:57:24, `SESSION_FAIL_SSL`). Port 5277 listened, so the pre-check passed; the dev server was wedged. The operator force-stopped Android Auto on D-POCO and restarted the head unit server (hand step H1). I stopped the script, kept those files under `attempt1/`, and reran everything from R1-B. That was one open failure on B, not two in a row, so it is not an INCONCLUSIVE.
- **Brief erratum, decisive string cadence.** Section 5 says `audio sink AUDIO over` is emitted "one per 5 s". It is emitted once per `LinkGapMonitor.WINDOW_MS`, `30_000L` (`LinkGapMonitor.kt:220`), and every captured line reads `over 30000ms` to `over 30009ms`. A 20 s window therefore holds 0 or 1 such line, so the brief's `sink >= 2` per window cannot be met on any build. See R1.
- **Scripts.** `ohu_lib.sh`, `ohu_setkeys.py`, `ptr_lib.sh` copied from `projection-teardown-and-relays-round3/`; `rr_lib.sh` as written in the brief; new `r0_build.sh` (both builds and tests) and `r1_session.sh` (R1-B, install M, R1-M with R3, R2-M, settings restore). All in `pr-1063-restart-audio-round1/`.
- **Settings.** Backup first (`settings-backup-HP.xml`). Written with `tab_put` with the app stopped, read back per run: `wifi-connection-mode` 1 (R1) and 0 (R2), `log-level` 2 (backup had 0), `onboarding-version` 2, `connection-modes` wifi, `video-profile-starvation-cap` deleted. `use-aac-audio` was already `true` in the backup, so it was not written; `audio-queue-capacity` 50 and `audio-latency-multiplier` 16 untouched. Baseline restored at the end: the live file equals the backup after stripping carriage returns (md5s differ only because of them).
- **Grading.** Every count below comes from my own greps over the window between two markers. No executor was used, so there was no second count to compare.
- **Windowed greps.** Markers `R1-x-v1-go` to `-done` etc. A discard rule check over each whole capture: `Magic Garbage detected in header` 0 and `MATCH! Starting AapService` 0 on both R1 captures and R2-M.
- **Operator listening (H2).** Cues fired at 19:08:24, 19:08:46 (B) and 19:11:07, 19:11:30 (M). The operator's words were logged with the time said and without a window, see R1.

## R1-B

**FAIL**

Graded against the brief's stated condition list. The one condition that fails is `sink >= 2`, which cannot be met (Setup notes). Every other count is what the brief expects of B.

| Window | recv | svc | tracks | aac | sink | rendered | quit | ssl | fatal | ph_crit | ph_disc | playing |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| pre (10 s) | - | - | - | - | 1 | 2 | - | - | - | - | - | - |
| v1 | 1 | 1 | 1 | 1 | 0 | 4 | 0 | 0 | 0 | 0 | 0 | 1 |
| v2 | 1 | 1 | 1 | 1 | 0 | 4 | 0 | 0 | 0 | 0 | 0 | 1 |

Decisive lines, v1 (`R1-B.logcat`):

```
19:08:24.132 AutomationReceiver: com.andrerinas.openheadunit.ACTION_RESTART_AUDIO
19:08:24.142 AapService: Received request to restart audio
19:08:24.142 AapAudio: Restarting all audio tracks
19:08:24.182 AAC Decoder started for 48000 Hz, 2 channels (Sync)
```

Reachability held: `pre` had `sink` 1 and `rendered` 2, `Media Start Request AUDIO` 1, `use-aac-audio` true. Session reached SSL at 19:07:42.1 (47 s after the `R1-B-start` marker at 19:06:54.7).

## R1-M

**FAIL**

The point of the round. By the brief's letter this is a FAIL because `sink` is 0 in both windows against a required 2 or more. That condition is unattainable by cadence (Setup notes), B shows the identical 0, and every condition that can discriminate the merge is met, including the one the brief named as the M-specific check.

| Window | recv | svc | tracks | aac | sink | rendered | quit | ssl | fatal | ph_crit | ph_disc | playing |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| pre (10 s) | - | - | - | - | 1 | 2 | - | - | - | - | - | - |
| v1 | 1 | **0** | 1 | 1 | 0 | 4 | 0 | 0 | 0 | 0 | 0 | 1 |
| v2 | 1 | **0** | 1 | 1 | 0 | 4 | 0 | 0 | 0 | 0 | 0 | 1 |

`svc` is 1 on B and 0 on M in both windows, which is the difference section 2 predicts: M's earlier `when` branch runs and the `AapService:` line is no longer printed. Decisive lines, v1 (`R1-M.logcat`):

```
19:11:07.792 AutomationReceiver: com.andrerinas.openheadunit.ACTION_RESTART_AUDIO
19:11:07.802 AapAudio: Restarting all audio tracks
19:11:07.852 AAC Decoder started for 48000 Hz, 2 channels (Sync)
```

(no `AapService: Received request to restart audio` between them.) Whole-capture sink lines on M, `over 30001ms` at 19:11:03.2 (before the v1 cue), 19:12:00.3 and 19:12:30.3: the 30 s cadence, none falling inside a 20 s window by chance of timing. Identity: `ACTION_QUERY_STATE` reported `commit` `3897b7b3df04`, M's merge commit. Both replies `result=0`.

Session reached SSL at 19:10:31.9 (30 s after the `R1-M-start` marker at 19:10:01.8). The operator remarked it seemed slower to connect than main; in this run M connected faster than B (30 s against 47 s), both single samples.

**H2, the operator's words** (reported, never graded; times are when it was said, window not stated):

- 19:09:40, after the B windows: "Music stopped".
- 19:12:18, after the M windows: "I heard stutters but i'm unsure if they're related to this. Also music stopped and restarted".
- 19:12:44: "music stopped". That time is after both M windows; R1-M had been closed with `ACTION_EXIT` and R2-M had started, so this is probably the session ending, which I did not establish.

The B and M windows were not told apart by the operator, so no per-window attribution of the stop, the restart or the stutters is possible. No log counter shows a stutter (`underruns=0, silentCycles=0` on every sink line).

## R3

**PASS**

Run inside the R1-M session, `CAP` = `R1-M.logcat`. B not run, per the brief.

| Verb | recv | ref | night | raise line | resume | top |
|---|---|---|---|---|---|---|
| `ACTION_REFRESH_SENSORS` | 1 | 1 | 1 | - | - | - |
| `aap.action.REFRESH_SENSORS` | 1 | 1 | 1 | - | - | - |
| `ACTION_RAISE_PROJECTION` | 1 | 0 | 1 | `raising the projection by DIRECT (overlay=true, foreground=false)` | 1 | 1 |
| `aap.action.RAISE_PROJECTION` | 1 | 0 | 1 | `raising the projection by DIRECT (overlay=true, foreground=false)` | 1 | 1 |

Each raise window had exactly one raise line, `DIRECT`, as the API 17 route requires. `raise projection ignored, no session` 0 in all four. All replies `result=0`.

## R2-M

**PASS**

No session (`wifi-connection-mode` 0), one capture, `R2-M.logcat` 48 lines.

| Check | Value |
|---|---|
| `AutomationReceiver:` lines containing `RESTART_AUDIO` | 2 |
| `AapAudio: Restarting all audio tracks` | 0 |
| `AapService: Received request to restart audio` | 0 |
| `SSL handshake complete` | 0 |
| `FATAL EXCEPTION` | 0 |
| process alive at the end (`R2.alive`) | 1 |

Both replies `{"ok":true}`: `ACTION_RESTART_AUDIO` at 19:12:45.9 and `aap.action.RESTART_AUDIO` at 19:12:57.5. `thermal_max` 57C at last sample, throttle counter 2458 throughout.

## Anything the brief did not ask about

- **The brief's `sink >= 2` rule should be fixed** before this brief is reused: either lengthen the window to 65 s or more, or drop `sink` from the pass list and grade the restart on `tracks`, `aac` and `rendered` as above. As written it fails every arm for a reason unrelated to the code.
- **The `listening` pre-check proves a socket, not a working server.** A wedged Android Auto developer server passes it and fails 10 s into the handshake. Consider a one-line check of the phone's `Network server running on port` count growing, or a first-attempt SSL gate before the arm's clock starts.
- **Open items for the operator:** the stutters were heard once, on an unknown window, with no counter movement. A separate listening round that names each window and pairs it with a sink or AudioTrack underrun count would settle whether the restart itself is audible.
- **Evidence:** `pr-1063-restart-audio-round1-captures.zip`, asset of release `rig-evidence-pr-1063-restart-audio`; sha256 `d7a78a4d0386c6da132569e0073bbcf7ece72b2daf90debe16d5302f2ff07789`.
