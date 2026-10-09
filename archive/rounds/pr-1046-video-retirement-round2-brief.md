# pr-1046-video-retirement, round 2 brief: the rebased surface retirement, GLES screen-off returns and a reconnect, A/B on D-HU against the new main

## 0. Pre-flight, and the time budget

The round runs unattended after one read of D-POCO's Bluetooth bonds. There is at most one hand step, and it comes before R0.

Make the folder and save the scripts first (section 5). Then run this block.

```bash
OUT=~/hur-wifi-test-scripts/pr-1046-round2; mkdir -p $OUT; cd $OUT      # adjust to where hur-wifi-test-scripts/ lives
HU=27870808938846; PH=4f4027e9; OTHER=; PUT=hu_put; source ./ohu_lib.sh; source ./ptr_lib.sh; source ./v2_lib.sh
adb devices                                                              # record every serial it prints
adb -s $PH shell svc bluetooth enable; sleep 5
adb -s $PH shell dumpsys bluetooth_manager | grep -a -A8 'Bonded devices' | tee $OUT/poco-bonds.txt
grep -c Navegadortz2 $OUT/poco-bonds.txt      # D-HU's radio. 1 or more: no request needed
grep -c Navegadortz3 $OUT/poco-bonds.txt      # D-SAM's radio. Record the count; do not ask about it
adb -s $PH shell dumpsys package com.google.android.projection.gearhead | grep -a -m1 versionName    # record: Gearhead version
```

**If `Navegadortz2` counts 0, send this request once and wait for the answer:**

> **Pre-flight request.** On D-POCO, pair with D-HU's Bluetooth radio, `Navegadortz2`. Every run in this round is Native AA on D-HU, and D-POCO must wake it.

After the answer, read the bonds once more into `poco-bonds-after.txt`. If `Navegadortz2` still counts 0, every run is UNTESTABLE: write the results file and stop. **Do not wait and do not ask again.**

**If `adb devices` lists D-MOTO (`ZY22GC3BM4`), set `OTHER=ZY22GC3BM4`.** `phone_air_on` then turns its radios off too, so D-HU cannot spend its wake pokes on it. Projection-teardown-and-relays round 2 lost 90 s of one bring-up to pokes aimed at D-MOTO.

| Step | Estimated wall clock |
|---|---|
| R0: two builds, unit tests, identity | 30 min |
| Pre-flight, backups, starting state | 10 min |
| Baseline arm: V1G, V1H, V2T, V2S, V5, V8, V9 | 65 min |
| Candidate install | 3 min |
| Candidate arm: the same seven runs | 65 min |
| Restore, zip, upload, results | 15 min |
| **Round total** | **about 3 h 10 min** |

A failed bring-up adds one re-run of that run. A host thermal wait adds up to 15 min per run. A crash segment in V9 adds about 2 min.

## 1. Build and baseline

| Arm | Where | SHA | Tree | Gate |
|---|---|---|---|---|
| Candidate C | B with the PR head `b749525d8c4b05cfcbb2a631afb727c344731e5d` (9 commits) merged in, built locally | the local merge commit | `b0efa0bdacfc749c4c9adb0c5a48c4c3737c667c` | 2836 JVM tests, 0 failures |
| Baseline B | `andreknieriem/open-headunit` `main` (3.5.0-beta4) | `7102b4283666ffcc7802e49402735e3958cd51a0` | `e96aa72a2ff556c9d1a5733c07d69c1b183a1b61` | 2820 JVM tests, 0 failures |

**History was rewritten.** Round 1 built `e7c2949c`, four commits on `7e9d813d`. The author then rebased onto `145a0c76` and force-pushed. The branch is nine commits on `145a0c76`. **Both arms are on today's `main`:** C is the branch merged onto B with no conflict, so the two arms differ by the branch alone.

**The baseline is not round 1's baseline.** `7102b428` carries the projection-teardown-and-relays fix (merged as `1d9c0404`), and since `bbec49fc` it holds every automatic wireless bring-up while the screen is off. A live session is not affected; a session that ends during a screen-off return is re-armed when the screen comes on. Section 3 says what that changes for each run. No round 1 baseline number is a comparison in this round: every run builds a fresh baseline arm.

```bash
git fetch https://github.com/andreknieriem/open-headunit.git main pull/1046/head
git cat-file -e b749525d8c4b05cfcbb2a631afb727c344731e5d || echo "PR HEAD MISSING"
git checkout -B arm-B 7102b4283666ffcc7802e49402735e3958cd51a0
git rev-parse 'HEAD^{tree}'                    # must be e96aa72a2ff556c9d1a5733c07d69c1b183a1b61
git checkout -B arm-C 7102b4283666ffcc7802e49402735e3958cd51a0
git merge --no-edit b749525d8c4b05cfcbb2a631afb727c344731e5d
git rev-parse 'HEAD^{tree}'                    # must be b0efa0bdacfc749c4c9adb0c5a48c4c3737c667c
git rev-parse --short HEAD                      # the local merge commit; record it
```

**If the PR head is missing, the merge conflicts or a tree differs, stop and ask.** Do not push the merge commit. **B may be reused:** the main-smoke round 1 built `7102b428` as md5 `62ccbb85dfed879a1151dea9610df817` with 2820 tests. If that APK is still on disk with that md5, reuse it and say so in Setup notes. The branch then carries code this brief did not check.

Build each arm from a detached worktree at its SHA, the way projection-teardown-and-relays round 3 did:

- Copy `r0_build.sh` from `../projection-teardown-and-relays-round3/`. Change `HUR_DIR`, `OUT` and the SHA to this round's.
- Copy `local.properties` into each new worktree before the build, or Gradle fails with `SDK location not found`.
- Cap the workers with `export GRADLE_OPTS=-Dorg.gradle.workers.max=2`.
- Copy each APK out of `apks/` as soon as it is built, as `cand.apk` and `base.apk`, because `build_hur.sh` deletes the previous one.
- Install with `adb -s $HU install -r -d <named apk>`. Never install through `set_hu_pref.sh`.

### Host thermal gate (every run)

The rules in `rig-quirks/topics/tooling.md` stand. `apk_check` (in `ptr_lib.sh`) calls `th_gate`: it waits until the CPU package reads below 75C, for at most 15 min, then logs every 20 s to `$OUT/<RUN>.thermal`. If the host does not cool in 15 min, wait 15 more min with no adb work. If it is still at 75C or above, stop the round and mark every run not yet started UNTESTABLE (host thermal). Put `th_report <RUN>` in each run's section. Write the results file after every run.

**Re-run a timing run once** if its throttle delta is above 0 or `thermal_max` is 90C or above. Every run in this round grades a time, so the rule applies to all of them except V9, whose counts stand either way.

### R0. Gate

Before the builds, run `th_wait 70` and start `th_watch $OUT/R0.thermal &`.

1. Run the six `git` checks above. Record each output.
2. Build both arms. Record both md5s.
3. Run `run_unit_tests.sh` on the candidate. Count from the JUnit XML.
4. Run the identity greps:

```bash
for a in cand.apk base.apk; do printf '%s ' $a
  for s in detachSurfaceIfCurrent SurfaceRecoveryState surfaceStopGeneration outputPublicationLock detachCurrentSurface; do
    printf '%s=%s ' "$s" $(unzip -p $a 'classes*.dex' | strings | grep -cF "$s"); done; echo; done
```

**PASS:** all of these hold:

- the trees and the merge-base match, and the commit count is 7;
- 2752 tests and 0 failures on the candidate, with `SurfaceRecoveryStateTest`, `VideoOutputEventsTest`, `VideoOutputRetirementTest` and `VideoSurfaceRetirementTest` present;
- the two md5s differ;
- in `cand.apk`, the first four strings count 1 or more and `detachCurrentSurface` counts 0;
- in `base.apk`, the first four count 0 and `detachCurrentSurface` counts 1 or more.

A build or test failure stops the round. After each install, send `ACTION_QUERY_STATE` and record the reply's `commit`: it is the local merge commit on the candidate and begins `7102b428` on the baseline.

## 2. What this is and why it exists

The candidate is an outside contributor's rewrite of how `VideoDecoder` and `AapProjectionActivity` share a `Surface`. Round 1 (`pr-1046-video-retirement-round1-results.md`) graded `e7c2949c` against `7e9d813d`.

**Round 1 was a FAIL, on one defect.** On GLES, a screen-off return keeps the same `Surface`. The old candidate treated a same-`Surface` callback as a resize and skipped the warm-relaunch re-arm. So the codec restarted on a P-frame and nothing asked the phone for a keyframe:

| Run | Screen-off return | T baseline | T candidate |
|---|---|---|---|
| V1G, GLES H.264 | 4, 5 | 1.75, 1.83 s | no picture in 90 s, no picture in 90 s |
| V1H, GLES H.265 | 4, 5, 6 | 1.79, 1.71, 1.95 s | 86.83, 99.03, 7.13 s |

On those returns the old candidate logged no focus-cycle line and 41 to 49 plain unsolicited gains, which do nothing. Home and launcher returns recreated the GLES surface on both builds in round 1, so they never reached the defect. TEXTURE failed the return rule too, but its baseline was also broken on screen-off returns. SURFACE, the codec pin, the singleTask relaunch, the density recreate, the overload cadence, fault injection and both old tablets passed. V1M was UNTESTABLE.

**The same root cause broke a reconnect.** The once-per-surface latch (`warmRelaunchCycleSpent`) was cleared only inside the skipped block. So a latch spent on a screen-off or cover return stayed spent into the next session. It then refused the new session's first-frame nudge, and the phone set up video and never streamed. `pr-1047-session-reconnect-round1-addendum-results.md` measured this 3 of 3 times on a combined build.

**What the candidate does now** (`b749525d`; read from the code):

- **One detach method.** `VideoDecoder.detachSurfaceIfCurrent(surface, reason)` nulls `mSurface`, records `detachedSurface` and stops. A second detach of the same surface returns early. `ProjectionView.surfaceDestroyed` keeps main's order: detach first, then the callbacks.
- **A stop counter.** `stop()` increments `surfaceStopGeneration` for `activity_stopped` and `screen_off_sleep`. `onStop` calls `stopIfCurrentSurface` on the activity's `ownedSurface` and keeps `mSurface`.
- **The re-arm keys on that counter.** `SurfaceRecoveryState.onSurfaceChanged(targetChanged, stopGeneration)` returns true when the target changed or the generation moved since it last looked. That re-arms once per new generation: `settleFocusCycle()`, `armWarmRelaunch()` (which now only sets `lastSurfaceSetMs` and posts the 850 ms check) and the unsolicited gain at `TransportStarted`.
- **The TEXTURE path from main stays.** `returnRearmRunnable` runs 300 ms after `onResume`. If no surface callback came since the resume, it consumes the generation through the same `onSurfaceChanged(false, ...)`, logs `AapProjectionActivity: returned to a kept surface with a stopped decoder; re-arming keyframe recovery`, re-arms, and sends one unsolicited gain. So a later same-surface callback for that stop cannot re-arm a second time.
- **The latch resets at every session boundary.** A collector resets `SurfaceRecoveryState` on `Disconnected`, `Connecting` and `HandshakeComplete`, even while the activity is stopped. Since `c04aa0e0` the same collector also cancels a pending focus-cycle regain and zeroes the surface clock (`lastSurfaceSetMs`), so an old session's recovery cannot fire into the new one. The first-frame nudge V8 counts is gated by the latch, not by that clock, so V8's expectations do not change.
- **The focus regain is bound to its transport** (`1cd38b07`). A focus-cycle release returns a token that holds the transport. The delayed regain goes out only when that transport is still the live one and the state is `TransportStarted`. `AapProjectionActivity: retaking video focus to complete the keyframe cycle` now prints only when the regain is sent.
- **Dimensions are passed on after every codec configure** (`b749525d`). `VideoDecoder` now calls the dimensions listener on every output-format event of the current codec, also when the size is the same. Nothing new goes on the wire. Expect `[AapProjectionActivity] Received video dimensions: WxH` once per decoder rebuild, not once per session. This brief grades neither line.
- **The singleTask relaunch.** `recreateProjectionView` and the new `onDestroy` step detach only the activity's own `ownedSurface`. A late teardown from an old instance cannot stop the new instance's decoder.
- **Output-thread logs.** They are queued and now dispatched even after their worker retires, with an explicit origin: `Throughput over` prints as `VideoDecoder.logThroughput | Throughput over ...`.
- **Skips are logged.** `Decoder stop (<reason>) skipped: surface is no longer current`, `Decoder detach (<reason>) skipped: surface is no longer current`, and three `... skipped: activity has no owned surface` lines.

**What main carries now, from the teardown fix** (`projection-teardown-and-relays-round1-results.md` to `-round3-results.md`):

- `detachSurface` before the callbacks, which stopped a codec being built on a dying surface. On the old main, round 2 of that thread measured `early_init_cycles` 11 of 20 on SURFACE H.265; the fix measured 0 of 20.
- `ReturnRearmPolicy` and `returnRearmRunnable`, the TEXTURE fix. On TEXTURE screen-off returns the old main had no picture in 30 s on 4 of 6 returns; the fix put a picture up in 1.72 to 1.96 s, with one re-arm line 0.30 to 0.31 s after each resume.
- The `ACTION_RAISE_PROJECTION` branch in `AapService`. The raise verb did nothing on round 1's baseline. **It works on both arms now**, so every return in this round uses it.

The JVM tests cover the decision rules. Only the rig covers an Activity, a View and a phone.

## 3. What is different about this round

- **The point of the round is V1G-C and V1H-C**: GLES screen-off returns on H.264 and H.265, where round 1 failed. V2T is the TEXTURE re-arm, now with main's path inside the candidate. V8 is the reconnect after a spent latch. V2S, V5 and V9 are guards.
- **Every run needs a fresh baseline arm.** On the new main:
  - V1G and V1H: main still re-arms on every surface callback, as in round 1, and now also through `returnRearmRunnable`. Expect T about 1.7 to 1.9 s, as the teardown fix measured on GLES (1.67 to 1.87 s).
  - V2T: the baseline is no longer broken on screen-off returns. Expect T about 1.7 to 2.0 s and one re-arm line per return.
  - V2S: no expected change, but the pairing needs the same build.
  - V5: the raise verb now works on the baseline. Round 1's V5R part 1 compared a working candidate raise against a dead one.
  - V8 is new. V9 now has the crash fix on both arms, so it is a regression guard on both.
- **Every return is the raise verb**, `send ACTION_RAISE_PROJECTION`. Screen-off returns wake with `input keyevent 224`. The activity resumes by itself; the verb is sent only if it does not, after `wm dismiss-keyguard`. Round 1 used the same keyevents.
- **Picture time T** is seconds from `AapProjectionActivity: onResume` to the first picture line: `VideoDecoder: keyframe decoded - the picture is repaired`, or `First frame rendered (hardware decode)` with nothing after it. No picture in the 30 s observe window counts as 999. A gray first frame (`... - no keyframe has decoded`) is not a picture. Thirty seconds decide every threshold this round uses (1, 5 and 10 s).
- **A kept surface** is a return with no `New surface set: ` and no `TextureProjectionView: Surface available` from 1 s before `onResume` to 3 s after it, and no `SurfaceCallback: onSurfaceDestroyed` between the go marker and 1 s after `onResume`. `v2_returns.py` prints it as `kept_surface`. That is the path the round 1 defect took.
- **The unsolicited gains the re-arm sends have no log line of their own.** `returnRearmRunnable` sends one gain right after its re-arm line, so that line stands for it one to one. The gain from a same-surface `onSurfaceChanged` that re-arms is not logged at INFO either. So this round counts gains as the re-arm line plus four logged lines, and counts focus-cycle lines (`relaunched surface has no picture after `). **A second re-arm for one stop shows only as a second focus-cycle line in that return, and only if the picture is still missing at the second 850 ms check.** `cb_after_rearm` says whether a same-surface callback that could grant one arrived. `SurfaceRecoveryStateTest` covers the rule itself.
- **Phone capture is in every run.** `hu_open` starts `adb logcat -v epoch` on D-POCO, the return and cycle functions stamp host wall-clock windows, and `run_end` cuts it with `v2_phone.py`. Two conditions are graded from it: `Critical error` per return window, and `Car connection state changed` per V8 reconnect window.
- **Lessons from the last three rounds, already in the helpers:** D-HU's adb shell is root and has no `su`; phone radios go off with `svc`, not airplane mode; `adb pull` has no `-q`, so there are no screenshots; one capture per run segment; kill by pid, never `pkill -f`.
- **The rig's audio settings are a deliberate worst case.** Do not change `use-aac-audio`, `audio-latency-multiplier`, `audio-queue-capacity` or `enable-audio-sink`. Read them back and record them. The last three rounds read D-HU at `false`, `8`, `20`, `false`.
- **D-HU hard-reboots under sustained multi-core load.** No run adds load. If `adb devices` loses D-HU, stop the round (rig broken).
- **Never run adb calls in parallel against one unit.**
- **Pre-registered outcomes that are not failures:**
  - V1G-C or V1H-C is INCONCLUSIVE when fewer than 3 of its 6 screen-off returns are on a kept surface with a decoder stop before the resume.
  - V2T-C is INCONCLUSIVE when no screen-off return is on a kept surface.
  - V5's density half is INCONCLUSIVE when a density window has no `onResume` (the activity was not recreated).
  - A V8 cycle in which the phone does not reconnect within 90 s ends that run, and the cycle is INCONCLUSIVE.
  - V9-C is INCONCLUSIVE when fewer than 8 Home cycles log a dying-surface stop.
- **UNTESTABLE, with the reason, and not run:** a live view-mode switch (`recreateProjectionView`; its broadcast is a `LocalBroadcastManager` one that adb cannot reach); picture-in-picture (no verb); rotation (D-HU is not rotated, on the operator's instruction). `VideoSurfaceRetirementTest` covers the first.

## 4. Settings keys

Write the keys with the app stopped, through `hu_put` (it writes as root and reads back). Read every key back before the launch. **Back up D-HU's `settings.xml` at the start as `settings-backup-HU.xml`.** Diff it against `../projection-teardown-and-relays-round2/settings-backup-HU.xml` and state the delta in Setup notes.

| Key | Type | Value | Notes |
|---|---|---|---|
| `view-mode` | int | `2` GLES, `1` TEXTURE, `0` SURFACE | per run; enum `SURFACE(0) TEXTURE(1) GLES(2)` |
| `video-codec` | string | `H.264` | `H.265` in V1H and V9. Compared as a literal string |
| `resolutionId` | int | `3` | 1080p |
| `force-software-decoding` | boolean | `false` | |
| `software-video-decoder` | int | delete | |
| `debug-video-fault-injection`, `debug-video-fault-rate`, `debug-video-fault-budget` | int | delete | |
| `video-profile-starvation-cap` | boolean | delete | a latch that forces 720p30 and AAC after three fumbled sessions |
| `wifi-connection-mode` | int | `3` | Native AA |
| `log-level` | int | `2` | INFO. Every app line in section 6 is INFO or WARN |
| `night-mode` | int | `1` | DAY |
| `native-driver-selection-mode` | int | `0` | stack armed, no selector countdown |
| `onboarding-version` | int | `2` | |
| `kill-on-disconnect` | boolean | `false` | V8 needs the projection activity to survive a session end. Record the value found |
| `connection-modes` | string set | read only | `preflight` fails unless it holds `wifi`; the last rounds read `wifi,self` |

`ACTION_LOG_MARKER` is not gated on either arm (`AutomationCommandPolicy.CONFIGURING` does not list it), so `allow-external-configuration` is not needed. Leave every key not listed exactly as found.

```bash
COMMON="str:video-codec=H.264 int:resolutionId=3 bool:force-software-decoding=false del:software-video-decoder del:debug-video-fault-injection del:debug-video-fault-rate del:debug-video-fault-budget del:video-profile-starvation-cap int:wifi-connection-mode=3 int:log-level=2 int:night-mode=1 int:native-driver-selection-mode=0 int:onboarding-version=2 bool:kill-on-disconnect=false"
```

A later `str:video-codec=...` in the same spec list replaces the one in `COMMON`, because `ohu_setkeys.py` drops a key before it adds it.

## 5. Every action as a verb, and the helpers

Every action on the app is a `send ...` verb (template section 3).

| Step | Command | Note |
|---|---|---|
| marker | `send ACTION_LOG_MARKER --es text <label>` (helper `mark`) | prints `AutomationMarker: <label>` at WARN |
| identity | `send ACTION_QUERY_STATE` | record the reply's `commit` |
| return to the projection | `send ACTION_RAISE_PROJECTION` (helper `rv`) | logs `AapService: raising the projection by <route>` on both arms |
| end the session, keep the network | `send ACTION_END_SESSION_STAY_ARMED` | V8 only. Not a user exit: the activity shows its reconnecting overlay and the phone comes back by itself (native-aa-wireless round 1 R12: 2.749 s to SSL on D-HU) |
| exit between runs | `send ACTION_EXIT`, `sleep 3`, then `am force-stop` | in `run_end` |

The app launch is `am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity` (template section 3), inside `run_open`. It is never a return.

OS-level steps, allowed: `input keyevent 3` (HOME), `223` (SLEEP), `224` (WAKEUP), `wm dismiss-keyguard`, `wm density`, `svc wifi|bluetooth enable|disable`, `dumpsys`.

**The only hand step is the pairing request in section 0, and only if `Navegadortz2` is missing.**

### Scripts

Make `hur-wifi-test-scripts/pr-1046-round2/`. Copy into it, unchanged:

- `ohu_lib.sh`, `ohu_setkeys.py`, `ptr_lib.sh`, `ptr_crash.py` from `../projection-teardown-and-relays-round3/`;
- `ohu_pair.py` from `../pr-1046-round1/`.

Then save the five files below beside them. List every file in Setup notes. If a script does not match the real line format, fix it, say so in Setup notes, and keep going.

Source order: `ohu_lib.sh`, then `ptr_lib.sh`, then `v2_lib.sh`. `v2_lib.sh` replaces `ptr_lib.sh`'s `cyc_home`, `cyc_off` and `run_end`, and uses `hu_open`, `stamp`, `apk_check`, `pcap_stop`, `cap_stop`, `clockcheck` and `preflight` from it.

**`v2_lib.sh`**:

```bash
# v2_lib.sh, pr-1046 round 2 : source it AFTER ohu_lib.sh and ptr_lib.sh (both from ../projection-teardown-and-relays-round3/).
# Every return is the raise verb, which works on both arms of this round.
rv() { send ACTION_RAISE_PROJECTION >/dev/null; }
# obs <from-line> : a picture, or 30 s, then a 3 s soak
obs() { waitfor 30 "$PIC" "$1" || echo "NO_PICTURE_30S"; sleep 3; }
# ret_off <RUN> <n> : screen off 5 s, wake; the raise verb only if the activity does not resume by itself within 6 s
ret_off() { local G L; G=$(nl); mark "$1-r$2-S-go"; stamp "r$2-S" start; adb -s "$HU" shell input keyevent 223; sleep 5
  L=$(nl); adb -s "$HU" shell input keyevent 224; sleep 2
  if ! waitfor 6 'AapProjectionActivity: onResume' "$L"; then mark "$1-r$2-S-raise"; adb -s "$HU" shell wm dismiss-keyguard; rv
    waitfor 20 'AapProjectionActivity: onResume' "$L" || echo "NO_RESUME $1-r$2"; fi
  obs "$G"; stamp "r$2-S" end; }
# ret_home <RUN> <n> [2] : Home 5 s, then the raise verb; twice, 1 s apart, when the third argument is 2
ret_home() { local G L; G=$(nl); mark "$1-r$2-H-go"; stamp "r$2-H" start; adb -s "$HU" shell input keyevent 3; sleep 5
  L=$(nl); rv; [ "$3" = 2 ] && { sleep 1; rv; }
  waitfor 20 'AapProjectionActivity: onResume' "$L" || echo "NO_RESUME $1-r$2"; obs "$G"; stamp "r$2-H" end; }
# ret_front <RUN> <n> : two raises 1 s apart while the projection is already in front (singleTask re-delivery)
ret_front() { mark "$1-r$2-F-go"; stamp "r$2-F" start; rv; sleep 1; rv; sleep 8; stamp "r$2-F" end; }
# ret_density <RUN> <n> <value|reset> : wm density destroys and rebuilds the projection activity
ret_density() { local G; G=$(nl); mark "$1-r$2-D-go"; stamp "r$2-D" start; adb -s "$HU" shell wm density "$3"; sleep 12; obs "$G"; stamp "r$2-D" end; }
# cyc_home / cyc_off : ptr_lib.sh's crash cycles, with the raise verb in place of the am start return
cyc_home() { local G L; G=$(nl); mark "$1-r$2-H-go"; stamp "r$2-H" start; adb -s "$HU" shell input keyevent 3; sleep 3
  L=$(nl); rv; waitfor 60 'AapProjectionActivity: onResume' "$L" || echo "NO_RESUME $1-r$2"
  waitfor 20 "$PIC" "$G" || true; sleep 3; stamp "r$2-H" end; }
cyc_off() { local G L; G=$(nl); mark "$1-r$2-S-go"; stamp "r$2-S" start; adb -s "$HU" shell input keyevent 223; sleep 3
  L=$(nl); adb -s "$HU" shell input keyevent 224; sleep 2
  if ! waitfor 6 'AapProjectionActivity: onResume' "$L"; then adb -s "$HU" shell wm dismiss-keyguard; rv
    waitfor 30 'AapProjectionActivity: onResume' "$L" || echo "NO_RESUME $1-r$2"; fi
  waitfor 20 "$PIC" "$G" || true; sleep 3; stamp "r$2-S" end; }
# run_end <RUN> : replaces ptr_lib.sh's. Phone extract first, then the unit capture, then every extractor
run_end() { mark "$1-end"; sleep 1
  if [ -n "$PCAPPID" ]; then pcap_stop; clockcheck "$1-end"
    python3 -I v2_phone.py "$PCAP" "$OUT/$1.windows.tsv" > "$OUT/$1.phone.json"; fi
  cap_stop
  tail -n 1 "$CAP" | cut -c1-40; adb -s "$HU" shell date      # last capture line within 5 s of the unit clock, else INCONCLUSIVE
  python3 -I ptr_crash.py "$CAP" "$1" > "$OUT/$1.crash.json"
  python3 -I v2_returns.py "$CAP" "$1" > "$OUT/$1.returns.json"
  case "$1" in V8-*) python3 -I v2_reconnect.py "$CAP" "$1" > "$OUT/$1.reconnect.json";; esac
  send ACTION_EXIT >/dev/null; sleep 3; adb -s "$HU" shell am force-stop $PKG; }
# vret <RUN> <view-mode> <codec> <screen-off returns> <Home returns> : V1G, V1H, V2T, V2S
vret() { hu_open $1 $COMMON int:view-mode=$2 str:video-codec=$3 || return 1
  local n=0 i; for i in $(seq 1 $4); do n=$((n+1)); ret_off $1 $n; done
  for i in $(seq 1 $5); do n=$((n+1)); ret_home $1 $n; done
  run_end $1; }
# v5 <RUN> : singleTask re-delivery in front, Home with a double raise, and two activity recreates. GLES H.264
v5() { hu_open $1 $COMMON int:view-mode=2 || return 1
  local n PHYS OVR BACK
  for n in 1 2 3; do ret_front $1 $n; done
  for n in 4 5 6; do ret_home $1 $n 2; done
  PHYS=$(adb -s "$HU" shell wm density | tr -d '\r' | grep -i physical | grep -o '[0-9]*$' | head -1)
  OVR=$(adb -s "$HU" shell wm density | tr -d '\r' | grep -i override | grep -o '[0-9]*$' | head -1); BACK=${OVR:-reset}
  ret_density $1 7 $((PHYS*5/4)); ret_density $1 8 $BACK
  run_end $1; adb -s "$HU" shell wm density $BACK; adb -s "$HU" shell wm density | tee -a $OUT/density.log; }
# v8 <RUN> : three cycles of two screen-off returns, then a session end that keeps the network. GLES H.264
v8() { hu_open $1 $COMMON int:view-mode=2 || return 1
  local c n=0 L
  for c in 1 2 3; do
    n=$((n+1)); ret_off $1 $n; n=$((n+1)); ret_off $1 $n
    L=$(nl); mark "$1-c$c-go"; stamp "c$c" start; send ACTION_END_SESSION_STAY_ARMED >/dev/null
    if ! waitfor 90 'SSL handshake complete' "$L"; then echo "NO_RECONNECT $1-c$c"; mark "$1-c$c-end"; stamp "c$c" end; break; fi
    sleep 60; mark "$1-c$c-end"; stamp "c$c" end
  done
  run_end $1; }
```

**`v2_returns.py`** (round 1's `ohu_returns.py` and the teardown thread's `ptr_returns.py`, plus this round's fields; `ohu_pair.py` reads its output unchanged):

```python
#!/usr/bin/env python3
"""v2_returns.py <capture> <RUN> : per-return metrics as JSON, pr-1046 round 2.

A window opens at '<RUN>-r<N>-<route>-go' (S screen off, H Home, F already in front, D density
recreate) and closes at the next '<RUN>...-go' or '<RUN>...-end' marker, or after 150 s.
Device times from `logcat -v time`, in ms. ohu_pair.py reads the output unchanged.
"""
import json, re, sys

cap, run = sys.argv[1], sys.argv[2]
TS = re.compile(r'^(\d\d)-(\d\d) (\d\d):(\d\d):(\d\d)\.(\d{3})')
rows = []
with open(cap, 'rb') as f:
    for n, raw in enumerate(f, 1):
        line = raw.decode('utf-8', 'replace').rstrip('\r\n')
        m = TS.match(line)
        if m:
            mo, d, h, mi, s, ms = map(int, m.groups())
            rows.append(((((d * 24 + h) * 60 + mi) * 60 + s) * 1000 + ms, n, line))

def first(pred, lo, hi):
    for t, n, l in rows:
        if lo <= t <= hi and pred(l):
            return t, n, l
    return None

def count(sub, lo, hi):
    return sum(1 for t, n, l in rows if lo <= t <= hi and sub in l)

R = re.escape(run)
GO = re.compile(r'AutomationMarker: ' + R + r'-r(\d+)-([HSFD])-go\s*$')
EDGE = re.compile(r'AutomationMarker: ' + R + r'(-\S+)?-(go|end)\s*$')
REARM = 'AapProjectionActivity: returned to a kept surface with a stopped decoder; re-arming keyframe recovery'
SCB = '[UI_DEBUG] [AapProjectionActivity] onSurfaceChanged'
CYCLE = 'relaunched surface has no picture after '
UNSOL = {'nudge': 'relaunched surface still has no picture - requesting video focus (unsolicited)',
         'first_frame_watchdog': 'Watchdog: No video received yet. Requesting Keyframe (Unsolicited Focus)...',
         'connected_no_frames': 'AapProjectionActivity: connected but no frames - requesting video focus (unsolicited)',
         'transport_recovery': 'AapTransport: Requesting recovery keyframe (unsolicited focus gain).'}
SKIPS = {'stop_or_detach_not_current': 'skipped: surface is no longer current',
         'no_owned_surface': 'skipped: activity has no owned surface',
         'stale_destroy': 'SurfaceCallback: onSurfaceDestroyed for a stale surface - ignoring'}
FORBID = ['times in a row without rendering a frame', 'Both codec types failed',
          'Giving up to avoid an infinite restart loop']
LIFE = tuple('Decoder stopped: ' + x for x in
             ('surfaceDestroyed', 'onDetachedFromWindow', 'projectionViewRecreate', 'activity_stopped', 'screen_off_sleep'))
STOPS = ('Decoder stopped: activity_stopped', 'Decoder stopped: screen_off_sleep')
TP = re.compile(r'Throughput over \d+ms: rendered=([0-9]+)')
PKG = 'com.andrerinas.headunitrevived'
gos = [(t, n, int(m.group(1)), m.group(2)) for t, n, l in rows for m in [GO.search(l)] if m]
LAST = rows[-1][0] if rows else 0
out = {'run': run, 'returns': []}
for t0, n0, k, route in gos:
    e = next((t for t, n, l in rows if n > n0 and EDGE.search(l)), None)
    t1 = min(e if e is not None else LAST, t0 + 150000)
    r = {'return': k, 'route': route, 'window_s': round((t1 - t0) / 1000, 1)}
    tps = [t for t, n, l in rows if t0 <= t <= t1 and 'Throughput over ' in l]
    r['throughput_lines'] = len(tps)
    r['throughput_max_gap_s'] = round(max(b - a for a, b in zip(tps, tps[1:])) / 1000, 1) if len(tps) > 1 else None
    r['decoder_stopped'] = [l.split('Decoder stopped: ')[-1].strip() for t, n, l in rows
                            if t0 <= t <= t1 and 'Decoder stopped: ' in l]
    r['codec_init'] = count('Codec initialized:', t0, t1)
    r['on_new_intent'] = count('AapProjectionActivity: onNewIntent received', t0, t1)
    r['raise_route_lines'] = count('AapService: raising the projection by', t0, t1)
    r['cycle_lines'] = count(CYCLE, t0, t1)
    r['rearm_lines'] = count(REARM, t0, t1)
    r['unsolicited_logged'] = {u: count(s, t0, t1) for u, s in UNSOL.items()}
    r['nudges'] = r['unsolicited_logged']['nudge']
    r['skips'] = {u: count(s, t0, t1) for u, s in SKIPS.items()}
    r['forbidden'] = {f: count(f, t0, t1) for f in FORBID if count(f, t0, t1)}
    r['fallback_surfaceview'] = count('Falling back to SurfaceView for this session', t0, t1)
    r['second_ssl_or_garbage'] = count('SSL handshake complete', t0, t1) + count('Magic Garbage detected in header', t0, t1)
    r['fatal_signal'] = count('Fatal signal', t0, t1)
    r['gray_lines'] = count('no keyframe has decoded', t0, t1)
    res = first(lambda l: 'AapProjectionActivity: onResume' in l, t0, t1)
    if not res:
        r['no_onResume'] = True
        out['returns'].append(r)
        continue
    tr = res[0]
    r['resume_after_go_s'] = round((tr - t0) / 1000, 2)
    r['stopped_before_resume'] = sum(count(s, t0, tr) for s in STOPS)
    lo, hi = tr - 1000, tr + 3000
    sp = {'UI_DEBUG_onSurfaceChanged': count(SCB, lo, hi),
          'New_surface_set': count('New surface set: ', lo, hi),
          'Gl_onSurfaceChanged': count('GlProjectionView: onSurfaceChanged', lo, hi),
          'Texture_available': count('TextureProjectionView: Surface available', lo, hi),
          'surfaceDestroyed_cb': count('SurfaceCallback: onSurfaceDestroyed', t0, tr + 1000)}
    r['surface_path'] = sp
    r['kept_surface'] = sp['New_surface_set'] == 0 and sp['Texture_available'] == 0 and sp['surfaceDestroyed_cb'] == 0
    ra = first(lambda l: REARM in l, tr, t1)
    r['rearm_rel_resume_s'] = round((ra[0] - tr) / 1000, 2) if ra else None
    sc = first(lambda l: SCB in l, tr, t1)
    r['surface_cb_rel_resume_s'] = round((sc[0] - tr) / 1000, 2) if sc else None
    r['double_arm'] = bool(ra and sc)
    r['cb_after_rearm'] = sum(1 for t, n, l in rows if ra and n > ra[1] and t <= t1 and SCB in l)
    ns = [n for t, n, l in rows if t0 <= t <= t1 and 'New surface set: ' in l]
    r['lifecycle_stops_after_last_new_surface'] = (
        sum(1 for t, n, l in rows if n > ns[-1] and t <= t1 and any(s in l for s in LIFE)) if ns else None)
    ci = first(lambda l: 'Codec initialized:' in l, t0, t1)
    rep = first(lambda l: 'keyframe decoded - the picture is repaired' in l, t0, t1)
    clean = first(lambda l: 'First frame rendered (hardware decode)' in l
                  and 'no keyframe has decoded' not in l, ci[0] if ci else t0, t1)
    pics = [x[0] for x in (rep, clean) if x]
    tp = min(pics) if pics else None
    r['picture_rel_resume_s'] = round(max(0, tp - tr) / 1000, 2) if tp else None
    r['picture_before_resume'] = bool(tp and tp < tr)
    cy = first(lambda l: CYCLE in l, tr - 1000, t1)
    r['cycle_rel_resume_s'] = round((cy[0] - tr) / 1000, 2) if cy else None
    tpr = first(lambda l: bool(TP.search(l)) and int(TP.search(l).group(1)) > 0, tr, t1)
    r['throughput_after_resume_s'] = round((tpr[0] - tr) / 1000, 2) if tpr else None
    out['returns'].append(r)

fx = 0
for i, (t, n, l) in enumerate(rows):
    if 'FATAL EXCEPTION' in l and any(('Process: ' + PKG) in rows[j][2] for j in range(i + 1, min(i + 4, len(rows)))):
        fx += 1
out['crash'] = {'fatal_signal': count('Fatal signal', 0, 10 ** 12), 'aborting': count('ABORTING', 0, 10 ** 12),
                'died': sum(1 for t, n, l in rows if ('Process ' + PKG + ' (pid') in l and 'has died' in l),
                'fatal_exception_ours': fx}
ints, st = [], None
for t, n, l in rows:
    if 'Codec initialized:' in l:
        if st is not None:
            ints.append((st, t))
        st = t
    elif 'Decoder stopped: ' in l and st is not None:
        ints.append((st, t)); st = None
if st is not None:
    ints.append((st, LAST))
exp = obs = 0
for a, b in ints:
    if b - a >= 5000:
        exp += (b - a) // 5000
        obs += count('Throughput over ', a, b)
out['cadence'] = {'expected_windows': exp, 'observed_windows': obs, 'ratio': round(obs / exp, 3) if exp else None}
out['throughput_prefix'] = {'total': count('Throughput over ', 0, 10 ** 12),
                            'prefixed': count('VideoDecoder.logThroughput | Throughput over ', 0, 10 ** 12),
                            'samples': [l.split('Throughput over ')[0][-70:] for t, n, l in rows if 'Throughput over ' in l][:2]}
out['counts'] = {k: count(k, 0, 10 ** 12) for k in
                 ['Decoder stopped: ', 'Codec initialized:', 'Codec exception in output thread', 'Decoder stall detected',
                  'Forcing restart (', REARM, CYCLE, 'skipped: surface is no longer current',
                  'skipped: activity has no owned surface', 'MATCH! Starting AapService', 'createGroup SUCCESS',
                  'Magic Garbage detected in header', 'SSL handshake complete']}
print(json.dumps(out, indent=1))
```

**`v2_reconnect.py`**:

```python
#!/usr/bin/env python3
"""v2_reconnect.py <capture> <RUN> : one row per V8 reconnect cycle, as JSON.

A cycle opens at '<RUN>-c<N>-go' (the ACTION_END_SESSION_STAY_ARMED verb follows it) and closes at
'<RUN>-c<N>-end'. spent_before counts focus-cycle lines between the previous cycle's end (or
'<RUN>-start') and this go: a spent latch is the precondition. Device times, in ms.
"""
import json, re, sys

cap, run = sys.argv[1], sys.argv[2]
TS = re.compile(r'^(\d\d)-(\d\d) (\d\d):(\d\d):(\d\d)\.(\d{3})')
rows = []
with open(cap, 'rb') as f:
    for n, raw in enumerate(f, 1):
        line = raw.decode('utf-8', 'replace').rstrip('\r\n')
        m = TS.match(line)
        if m:
            mo, d, h, mi, s, ms = map(int, m.groups())
            rows.append(((((d * 24 + h) * 60 + mi) * 60 + s) * 1000 + ms, n, line))

def first(pred, lo, hi):
    for t, n, l in rows:
        if lo <= t <= hi and pred(l):
            return t, n, l
    return None

def count(sub, lo, hi):
    return sum(1 for t, n, l in rows if lo <= t <= hi and sub in l)

def marker(name):
    pat = re.compile(r'AutomationMarker: ' + re.escape(name) + r'\s*$')
    return next(((t, n) for t, n, l in rows if pat.search(l)), None)

GO = re.compile(r'AutomationMarker: ' + re.escape(run) + r'-c(\d+)-go\s*$')
TPOK = re.compile(r'Throughput over \d+ms: rendered=[1-9]')
LAST = rows[-1][0] if rows else 0
st = marker(run + '-start')
prev_t = st[0] if st else (rows[0][0] if rows else 0)
out = []
for t0, n0, k in [(t, n, int(m.group(1))) for t, n, l in rows for m in [GO.search(l)] if m]:
    e = marker('%s-c%d-end' % (run, k))
    t1 = e[0] if e else LAST
    c = {'cycle': k,
         'spent_before': count('relaunched surface has no picture after ', prev_t, t0),
         'verb': count('AutomationReceiver: com.andrerinas.openheadunit.ACTION_END_SESSION_STAY_ARMED', t0, t1),
         'received': count('AapService: ACTION_END_SESSION_STAY_ARMED received', t0, t1),
         'network_kept': count('AapService: Native AA session ended; keeping the ', t0, t1),
         'overlay_shown': count('AapProjectionActivity: Unexpected disconnect. Showing reconnecting overlay', t0, t1),
         'finishing': count('AapProjectionActivity: Finishing because state', t0, t1),
         'activity_destroyed': count('AapProjectionActivity.onDestroy called.', t0, t1),
         'surface_cb': count('[UI_DEBUG] [AapProjectionActivity] onSurfaceChanged', t0, t1),
         'ssl_count': count('SSL handshake complete', t0, t1),
         'fatal_signal': count('Fatal signal', t0, t1)}
    s = first(lambda l: 'SSL handshake complete' in l, t0, t1)
    c['ssl_after_go_s'] = round((s[0] - t0) / 1000, 2) if s else None
    if s:
        ts = s[0]
        rel = lambda x: round((x[0] - ts) / 1000, 2) if x else None
        rep = first(lambda l: 'keyframe decoded - the picture is repaired' in l, ts, t1)
        clean = first(lambda l: 'First frame rendered (hardware decode)' in l and 'no keyframe has decoded' not in l, ts, t1)
        pics = [x for x in (rep, clean) if x]
        c.update({'sink_setup_after_ssl': count('Media Sink Setup Request: ', ts, t1),
                  'codec_init_after_ssl_s': rel(first(lambda l: 'Codec initialized:' in l, ts, t1)),
                  'picture_after_ssl_s': rel(min(pics)) if pics else None,
                  'first_frame_nudges': count('Watchdog: No video received yet', ts, t1),
                  'first_frame_nudge_after_ssl_s': rel(first(lambda l: 'Watchdog: No video received yet' in l, ts, t1)),
                  'frame_windows_60s': sum(1 for t, n, l in rows if ts <= t <= ts + 60000 and TPOK.search(l)),
                  'overlay_hidden': count('Hiding reconnecting overlay - ', ts, t1)})
    out.append(c)
    prev_t = t1
print(json.dumps({'run': run, 'cycles': out}, indent=1))
```

**`v2_phone.py`** (the teardown thread's `ptr_phone.py`, cut down to the lines this round grades):

```python
#!/usr/bin/env python3
"""v2_phone.py <phone-capture> <windows.tsv> : Gearhead lines per window, as JSON.

The phone capture is `adb -s 4f4027e9 logcat -v epoch`. windows.tsv holds one row per window: label,
host start in epoch ms, host end in epoch ms (written by stamp in ptr_lib.sh). Lines are matched by
message text, not by tag, so a tag spelling change does not hide them.
"""
import json, re, sys

cap, tsv = sys.argv[1], sys.argv[2]
TS = re.compile(r'^\s*(\d+)\.(\d{3})\s')
LINES = {'critical_error': 'Critical error', 'out_of_car': 'OutOfCarLifecycle',
         'car_state': 'Car connection state changed'}
rows = []
with open(cap, 'rb') as f:
    for raw in f:
        l = raw.decode('utf-8', 'replace').rstrip('\r\n')
        m = TS.match(l)
        if m:
            rows.append((int(m.group(1)) * 1000 + int(m.group(2)), l))
out = []
for w in open(tsv):
    p = w.split()
    if len(p) < 3:
        continue
    label, t0, t1 = p[0], int(p[1]), int(p[2])
    win = [l for t, l in rows if t0 <= t <= t1]
    r = {'window': label, 'lines': len(win)}
    for k, s in LINES.items():
        hits = [l for l in win if s in l]
        r[k] = len(hits)
        if hits:
            r[k + '_first'] = hits[0][:200]
    out.append(r)
print(json.dumps({'capture': cap, 'phone_lines': len(rows), 'windows': out}, indent=1))
```

## 6. The lines that decide every run

Every app line below was checked with `git grep -F` against `app/src/main` at both the merged candidate tree `b0efa0bd` and `7102b428`. **All are present on both arms** except the four marked **candidate only**. Lines marked *(composed)* render from a format string; the runs grep the rendered form, and the block at the end lists the source fragment. The phone lines are Gearhead's and are not in this tree: projection-teardown-and-relays round 2 recorded `CAR.SERVICE: Car connection state changed: PREFLIGHT->CONNECTED` and the `Critical error` form on Gearhead 17.9. System lines come from the platform. Grep every capture with `grep -a`.

| Meaning | Line | Source | File and window |
|---|---|---|---|
| return reached the activity | `AapProjectionActivity: onResume` | `AapProjectionActivity.kt` | unit capture, per return |
| activity stopped | `AapProjectionActivity: onStop` | same | per return |
| singleTask re-delivery | `AapProjectionActivity: onNewIntent received` | same | V5 F and H returns |
| the activity instance ended | `AapProjectionActivity.onDestroy called.` | same | V5 D returns, V8 cycles |
| the raise verb acted | `AapService: raising the projection by` | `AapService.kt` | per return, report |
| verb landed | `AutomationReceiver: com.andrerinas.openheadunit.<ACTION>` *(composed)* | `AutomationReceiver.kt` | every step |
| marker | `AutomationMarker: ` | `AutomationEffectRunner.kt` | every window |
| a surface callback reached the activity | `[UI_DEBUG] [AapProjectionActivity] onSurfaceChanged` | `AapProjectionActivity.kt` | per return |
| a different Surface was claimed | `New surface set: ` | `VideoDecoder.kt` | per return, `kept_surface` |
| GLES re-reported its surface | `GlProjectionView: onSurfaceChanged` | `GlProjectionView.kt` | report |
| TextureView made a new SurfaceTexture | `TextureProjectionView: Surface available` | `TextureProjectionView.kt` | `kept_surface` |
| surface torn down | `SurfaceCallback: onSurfaceDestroyed` | `AapProjectionActivity.kt` | `kept_surface` |
| a late teardown was ignored | `SurfaceCallback: onSurfaceDestroyed for a stale surface - ignoring` | same | V5, report |
| decoder stopped, with its reason *(composed)* | `Decoder stopped: ` + `activity_stopped`, `screen_off_sleep`, `surfaceDestroyed`, `onDetachedFromWindow`, `projectionViewRecreate`, `New surface`, `restart: ...` | `VideoDecoder.kt`, `DecoderStopPolicy.kt` | per return, V9 |
| a stop or detach for a surface no longer current *(composed)* | `skipped: surface is no longer current` | `VideoDecoder.kt` (1 site on B, 2 on C) | report |
| **candidate only:** a skip with no owned surface *(composed)* | `skipped: activity has no owned surface` | `AapProjectionActivity.kt` | report |
| codec built | `Codec initialized:` | `VideoDecoder.kt` | per return, V8, V9 |
| **picture, form 1** | `VideoDecoder: keyframe decoded - the picture is repaired` | `VideoDecoder.kt` | T |
| **picture, form 2** (line ends here) | `First frame rendered (hardware decode)` | `VideoDecoder.kt` | T |
| **gray, not a picture** | `First frame rendered (hardware decode) - no keyframe has decoded` | `VideoDecoder.kt` | report |
| steady state *(composed)* | `Throughput over <N>ms: rendered=<N>` | `VideoDecoder.kt` | bring-up, V8, OL |
| **candidate only:** the output-log origin | `VideoDecoder.logThroughput \| Throughput over ` | `VideoDecoder.kt` (`AppLog.tagged`) | OL. On B the same prefix comes from `AppLog`'s stack walk |
| the warm-relaunch focus cycle fired *(composed)* | `relaunched surface has no picture after ` | `AapProjectionActivity.kt` | per return, V8 precondition |
| **the return re-arm** (one unlogged gain follows it) | `AapProjectionActivity: returned to a kept surface with a stopped decoder; re-arming keyframe recovery` | `AapProjectionActivity.kt` | per return |
| logged unsolicited gain 1 | `relaunched surface still has no picture - requesting video focus (unsolicited)` | `AapProjectionActivity.kt` | per return |
| logged unsolicited gain 2, the first-frame nudge | `Watchdog: No video received yet. Requesting Keyframe (Unsolicited Focus)...` | same | V8 |
| logged unsolicited gain 3 | `AapProjectionActivity: connected but no frames - requesting video focus (unsolicited)` | same | per return |
| logged unsolicited gain 4 | `AapTransport: Requesting recovery keyframe (unsolicited focus gain).` | `AapTransport.kt` | per return |
| restart at a stall | `Decoder stall detected`, `Forcing restart (` | `VideoDecoder.kt` | report |
| output-thread exception | `Codec exception in output thread` | `VideoDecoder.kt` | OL, report |
| **forbidden anywhere** | `times in a row without rendering a frame`, `Both codec types failed`, `Giving up to avoid an infinite restart loop` | `VideoDecoder.kt` | rule (c) |
| backend demoted for the session | `Falling back to SurfaceView for this session` | `AapProjectionActivity.kt` | rule (d) |
| session formed | `SSL handshake complete` | `AapSslContext.kt` (INFO form) | discard rules, V8 |
| V8: the verb's handler ran | `AapService: ACTION_END_SESSION_STAY_ARMED received` | `AapService.kt` | V8 |
| V8: the network stayed up *(composed)* | `AapService: Native AA session ended; keeping the ` | `AapService.kt` | V8, report |
| V8: the activity stayed for the reconnect | `AapProjectionActivity: Unexpected disconnect. Showing reconnecting overlay` | `AapProjectionActivity.kt` | V8 precondition |
| V8: the activity finished instead | `AapProjectionActivity: Finishing because state` | same | V8 precondition |
| V8: the overlay came down *(composed)* | `Hiding reconnecting overlay - ` | same | report |
| phone chose a codec *(composed)* | `Media Sink Setup Request: 3 on channel VIDEO` (H.264), `... 7 ...` (H.265) | `AapControl.kt` | `preflight`, V8 |
| preflight: WiFi mode | `WifiLauncher: Initializing WiFi Mode: NATIVE` | `WifiLauncherManager.kt` | `preflight` |
| preflight: view backend *(composed)* | `Projection backend: viewMode=GLES ` (or `TEXTURE `, `SURFACE `) | `AapProjectionActivity.kt` | `preflight` |
| preflight: hotspot path (must be 0) | `HotspotManager:`, `SoftApCredentials:`, except `HotspotManager: Setting hotspot enabled=false` | `HotspotManager.kt`, `SoftApCredentialsProvider.kt` | `preflight` |
| discard rules | `MATCH! Starting AapService`, `createGroup SUCCESS`, `Magic Garbage detected in header` | receivers, `WifiDirectManager.kt`, `AapReadSingleMessage.kt` | every run |
| V9 crash script | `Decoder restart requested: ` | `VideoDecoder.kt` | `ptr_crash.py`, report |
| **system: native crash** | `Fatal signal`, `ABORTING` | libc, debuggerd | every run |
| **system: Java crash in our process** | `FATAL EXCEPTION` with `Process: com.andrerinas.headunitrevived` within 3 lines | AndroidRuntime | every run |
| **system: our process died** | `Process com.andrerinas.headunitrevived (pid` with `has died` | ActivityManager | every run |
| **phone: Gearhead ended or refused the car link** | `Critical error` | Gearhead `CAR.SERVICE` | D-POCO capture, per return window, graded |
| **phone: a car session started** | `Car connection state changed` | Gearhead `CAR.SERVICE` | D-POCO capture, per V8 cycle, graded |
| phone: the projection process ended | `OutOfCarLifecycle` | Gearhead | D-POCO capture, report |
| **identity, in the DEX** | `detachSurfaceIfCurrent`, `SurfaceRecoveryState`, `surfaceStopGeneration`, `outputPublicationLock` (candidate only); `detachCurrentSurface` (baseline only) | | R0 |

## 7. Runs

Run ids carry the arm: `-B` baseline, `-C` candidate. **Do every baseline run first, then install the candidate and repeat**, so D-HU changes build once. Order in each arm: **V1G, V1H, V2T, V2S, V5, V8, V9**. **The point of the round is V1G-C and V1H-C.**

Starting state, read and recorded in Setup notes, never assumed:

```bash
cd $OUT; HU=27870808938846; PH=4f4027e9; PUT=hu_put        # OTHER as section 0 set it
adb -s $HU shell date; adb -s $PH shell date; date                          # clocks agree to the second
adb -s $HU shell stat -c %U:%a /data/data/$PKG/shared_prefs                 # record; the last rounds read u0_a176:771
adb -s $HU shell cat /data/data/$PKG/shared_prefs/settings.xml > $OUT/settings-backup-HU.xml; BASEXML=$OUT/settings-backup-HU.xml
grep -aoE '(use-aac-audio|audio-latency-multiplier|audio-queue-capacity|enable-audio-sink|connection-modes|kill-on-disconnect)[^/]*' $BASEXML   # record; change only kill-on-disconnect, through COMMON
adb -s $PH shell dumpsys window | grep -a mCurrentFocus                     # D-POCO idle or at home; if not, input keyevent KEYCODE_HOME
adb -s $HU shell dumpsys bluetooth_manager | grep -a -m3 -iE 'enabled|state' # D-HU Bluetooth on
adb -s $HU shell appops get $PKG SYSTEM_ALERT_WINDOW                        # record: the raise route depends on it
adb -s $HU shell wm density | tr -d '\r'                                     # record: Physical, and any Override
```

Install and identity, once per arm:

```bash
adb -s $HU shell am force-stop $PKG; adb -s $HU install -r -d base.apk; WANT_MD5=$(md5sum base.apk | cut -d' ' -f1); send ACTION_QUERY_STATE    # baseline arm
adb -s $HU shell am force-stop $PKG; adb -s $HU install -r -d cand.apk; WANT_MD5=$(md5sum cand.apk | cut -d' ' -f1); send ACTION_QUERY_STATE    # candidate arm
```

`apk_check` pulls the live APK and compares its md5 with `WANT_MD5` before every run. A mismatch voids the run.

**Every capture** is `logcat -v time` with `stdbuf -oL`, started before the launch by `run_open`. After every run, `cap_stop` must print 0 (no logcat left), and the last capture line must be within 5 s of the unit's clock; if either fails, that run is INCONCLUSIVE. `pcap_stop` prints `phone_lines=<N>`; **a run with `phone_lines=0` is a setup failure**: re-run it once and never grade the empty one.

**Discard rule, every run except V8 and V9:** discard the run and re-run it once if the unit capture between `<RUN>-start` and `<RUN>-end` holds `MATCH! Starting AapService`, `createGroup SUCCESS`, `Magic Garbage detected in header` or `SSL handshake complete` (the session's own lines all come before `<RUN>-start`). A second discard makes the run INCONCLUSIVE. V8 expects one `SSL handshake complete` per cycle; V9 does not discard on an SSL that follows a crash.

**A failed bring-up** (`SESSION_FAIL_*` from `run_open`, or `PREFLIGHT_FAIL`) is a setup failure. Re-run that run once; a second failure makes it UNTESTABLE.

**Stop rule for the round.** Stop and mark every run not yet started UNTESTABLE when any of these is true:

- `adb devices` no longer lists D-HU;
- the host thermal rule in section 1 fires;
- three runs in a row end UNTESTABLE on a failed bring-up.

### The return rule (RET), from round 1

Run `python3 -I ohu_pair.py $OUT/<ID>-B.returns.json $OUT/<ID>-C.returns.json > $OUT/<ID>.pair.json`. A return is a FAIL return when any of these holds:

- **(a)** T on the candidate is above **10.0 s** and T on the baseline is below **3.0 s** (key `FAIL_a_over_10s_where_baseline_under_3s`);
- **(b)** T on the candidate exceeds the baseline's by more than **2.0 s**; rule (b) fires only when **two or more** returns do this (keys `FAIL_b_delta_over_2s_returns`, `FAIL_b_fires`);
- **(c)** a candidate window holds a forbidden line and the baseline window of the same return holds none (key `FAIL_c_forbidden_only_candidate`);
- **(d)** a candidate window has no `onResume`, or logs `Falling back to SurfaceView for this session` where the baseline does not (key `FAIL_d_no_resume_or_view_fallback_or_front_stop`).

A return whose window holds a second SSL or `Magic Garbage detected in header` in either arm is void (key `void`). More than three void returns re-run the run once.

### The phone rule (PH), used by V1G, V1H, V2T, V2S, V5 and V9

From `<ID>-C.phone.json`: `critical_error` is 0 in every return or cycle window. A window with 1 or more is a FAIL, unless the baseline's window with the same label also has 1 or more; then report both lines with their device times and do not grade it. Quote every `critical_error_first` line either way.

### V1G and V1H. GLES screen-off and Home returns, H.264 and H.265, both arms (**the point of the round**)

```bash
PF_MODE=NATIVE PF_VIEW=GLES PF_SINK=3; vret V1G-B 2 H.264 6 2      # baseline arm; vret V1G-C 2 H.264 6 2 in the candidate arm
PF_MODE=NATIVE PF_VIEW=GLES PF_SINK=7; vret V1H-B 2 H.265 6 2      # baseline arm; vret V1H-C 2 H.265 6 2 in the candidate arm
python3 -I ohu_pair.py $OUT/V1G-B.returns.json $OUT/V1G-C.returns.json > $OUT/V1G.pair.json
python3 -I ohu_pair.py $OUT/V1H-B.returns.json $OUT/V1H-C.returns.json > $OUT/V1H.pair.json
```

Returns 1 to 6 are screen off (route S), 7 and 8 are Home (route H). A **B1 return** is a screen-off return with `kept_surface` true and `stopped_before_resume` of 1 or more: the path round 1 failed on.

**PASS (each of V1G-C and V1H-C),** all of these:

- every screen-off return has T (`picture_rel_resume_s`) at most **5.0 s**;
- every B1 return was repaired by the re-arm, not by a later watchdog: `picture_rel_resume_s` at most **1.0 s**, or `cycle_rel_resume_s` between **0.5 and 2.0 s**;
- every return has `cycle_lines` at most 1 and `rearm_lines` at most 1;
- RET: `FAIL_a`, `FAIL_c` and `FAIL_d` are empty, `FAIL_b_fires` is false, and `void` has at most 3 returns;
- `crash` in `<ID>-C.returns.json` reads 0 for `fatal_signal`, `aborting`, `died` and `fatal_exception_ours`;
- the phone rule PH holds.

**FAIL** if any of them is not met. **INCONCLUSIVE** if fewer than 3 of the 6 screen-off returns are B1 returns; then report which surface lines the returns did see (`surface_path`).

**If the change did nothing,** the candidate repeats round 1: on the B1 returns no cycle line, `nudges` in the tens, and T of 999 or above 80 s. Expect the baseline at T about 1.7 to 1.9 s with a cycle line about 1.0 s after `onResume`.

**Report per return, both arms, as one table per run:** route, T, `kept_surface`, `stopped_before_resume`, `cycle_rel_resume_s`, `rearm_lines`, `rearm_rel_resume_s`, `surface_cb_rel_resume_s`, `nudges`, the sum of `unsolicited_logged`, `gray_lines`, `decoder_stopped`, and `skips`.

### V2T. TEXTURE screen-off and Home returns, H.264, both arms

```bash
PF_MODE=NATIVE PF_VIEW=TEXTURE PF_SINK=3; vret V2T-B 1 H.264 6 2      # baseline arm; vret V2T-C 1 H.264 6 2 in the candidate arm
python3 -I ohu_pair.py $OUT/V2T-B.returns.json $OUT/V2T-C.returns.json > $OUT/V2T.pair.json
```

**PASS (V2T-C),** all of these:

- every screen-off return has T at most **5.0 s**;
- every screen-off return with `kept_surface` true and `stopped_before_resume` of 1 or more has `rearm_lines` equal to **1**, with `rearm_rel_resume_s` between **0.2 and 1.5 s**;
- every return has `rearm_lines` at most 1 and `cycle_lines` at most 1;
- RET as in V1G, the crash counts 0, and PH holds.

**FAIL** if any of them is not met. **INCONCLUSIVE** if no screen-off return has `kept_surface` true.

**The re-arm at most once per stop.** For each screen-off return, report the unsolicited gains as `rearm_lines` (one unlogged gain each) plus the sum of `unsolicited_logged`, and report `cb_after_rearm`. A return with `cycle_lines` of 2 or more already fails above. If every return has `cb_after_rearm` 0, say so: no same-surface callback arrived after a re-arm, so this run could not have shown a second grant either way.

**If the change did nothing** (the rebase lost main's TEXTURE re-arm), the candidate shows `rearm_lines` 0 and some T of 999 or about 27 s, as the old main did in projection-teardown-and-relays round 1 (R3-B). Expect the baseline at T about 1.7 to 2.0 s with one re-arm line about 0.3 s after `onResume`.

### V2S. SURFACE screen-off and Home returns, H.264, both arms

```bash
PF_MODE=NATIVE PF_VIEW=SURFACE PF_SINK=3; vret V2S-B 0 H.264 3 2      # baseline arm; vret V2S-C 0 H.264 3 2 in the candidate arm
python3 -I ohu_pair.py $OUT/V2S-B.returns.json $OUT/V2S-C.returns.json > $OUT/V2S.pair.json
```

**PASS (V2S-C),** all of these: RET as in V1G; `double_arm` false on every candidate return; no candidate return with `cycle_lines` above 0 where the baseline's same return has 0 (SURFACE's own teardown already releases focus, so the escalation must stay silent); the crash counts 0; PH holds. **FAIL** if any is not met. **Report:** `decoder_stopped` and `skips` per return for both arms. Round 1 found the old candidate logged half the baseline's `Decoder stopped:` lines on SURFACE with nothing saying why; the candidate now logs a skip line for each.

### V5. singleTask re-delivery and an activity recreate over a live projection, GLES H.264, both arms

```bash
PF_MODE=NATIVE PF_VIEW=GLES PF_SINK=3; v5 V5-B      # baseline arm; v5 V5-C in the candidate arm
python3 -I ohu_pair.py $OUT/V5-B.returns.json $OUT/V5-C.returns.json > $OUT/V5.pair.json
```

Returns 1 to 3 are two raises 1 s apart with the projection already in front (route F). Returns 4 to 6 are Home, then two raises 1 s apart (route H). Returns 7 and 8 change `wm density` (route D): `density` is not in the projection activity's `configChanges`, so each change destroys the activity and builds a new one, and the old instance's GLES teardown lands after the new instance claims the decoder. That is the case the candidate's `ownedSurface` scoping exists for.

**PASS (V5-C),** all of these:

- **F returns:** an empty `decoder_stopped`, `codec_init` 0, `cycle_lines` 0 and `throughput_max_gap_s` at most **7.5**; a candidate stop where the baseline's same return has none is FAIL;
- **H returns:** RET (a) to (d);
- **D returns:** an `onResume` in each window; T at most **5.0 s**; `lifecycle_stops_after_last_new_surface` equal to 0 (no teardown stopped the new instance's decoder after it claimed its surface); `second_ssl_or_garbage` 0;
- the crash counts 0, and PH holds.

**FAIL** if any is not met. **INCONCLUSIVE** for the D half only, if a D window has no `onResume` (the activity was not recreated). Read `wm density` back after the run and put what it says in Setup notes; it must match what the starting state recorded.

**Report per return, both arms:** `on_new_intent`, `raise_route_lines`, `decoder_stopped`, `codec_init`, `skips` (the stale-surface and no-owned-surface lines are how the candidate says it ignored an old instance), T and `lifecycle_stops_after_last_new_surface`.

### V8. Reconnect after a session end with a spent latch, GLES H.264, both arms

```bash
PF_MODE=NATIVE PF_VIEW=GLES PF_SINK=3; v8 V8-B      # baseline arm; v8 V8-C in the candidate arm
```

Each cycle runs two screen-off returns, then `ACTION_END_SESSION_STAY_ARMED`. The verb ends the session without a user exit and keeps the WiFi Direct group up, so the phone comes back by itself and a new session forms while the same projection activity and the same `Surface` stay. The screen-off returns spend the warm-relaunch latch first. On the old candidate that latch then refused the new session's first-frame nudge, so the phone set up video and never streamed. **Stop rule:** three cycles, or the first cycle whose phone does not reconnect within 90 s.

From `V8-<arm>.reconnect.json`, a cycle **reaches the mechanism** when `verb` is 1, `received` is 1, `overlay_shown` is 1, `finishing` is 0, `activity_destroyed` is 0 and `spent_before` is 1 or more. A cycle that misses one of these is a regression guard only; say which field missed.

**PASS (V8-C):** at least 2 cycles reach the mechanism and reconnect (`ssl_after_go_s` not null), and in every such cycle:

- `codec_init_after_ssl_s` is at most **15**;
- `picture_after_ssl_s` is at most **15**;
- `frame_windows_60s` is at least **3**;
- from `V8-C.phone.json`, the cycle's window (`c<N>`) has `car_state` of 1 or more: the phone formed the new session.

**FAIL:** a cycle that reached the mechanism and reconnected, with no picture within 60 s of the SSL, or `frame_windows_60s` below 3. **INCONCLUSIVE:** fewer than 2 cycles reach the mechanism and reconnect. If `car_state` is 0 in every window of both arms, Gearhead prints a different line on this build (`rig-quirks/topics/gearhead.md`): record that, quote what the phone capture shows at the cycle's SSL time, and grade the run on the unit's lines alone.

**If the change did nothing,** a cycle that reached the mechanism shows `sink_setup_after_ssl` 1 or more, `first_frame_nudges` 0, no `Codec initialized:` and no `Throughput over` for the rest of the cycle. **Report per cycle, both arms:** every field of the cycle row, and `first_frame_nudges` with `first_frame_nudge_after_ssl_s`. A picture with `first_frame_nudges` 0 means the phone streamed without a nudge, so that cycle did not need the reset; say so. The returns inside V8 are report only.

### V9. SURFACE H.265 crash cycling, the teardown crash class, both arms

```bash
CYC="cyc_home cyc_off"; PF_MODE=NATIVE PF_VIEW=SURFACE PF_SINK=7
crashloop V9-B 20 hu_open $COMMON int:view-mode=0 str:video-codec=H.265      # baseline arm
crashloop V9-C 20 hu_open $COMMON int:view-mode=0 str:video-codec=H.265      # candidate arm
for f in $OUT/V9-B-s*.crash.json $OUT/V9-C-s*.crash.json; do echo "$f $(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["summary"])' $f)"; done
for f in $OUT/V9-B-s*.returns.json $OUT/V9-C-s*.returns.json; do echo "$f $(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["crash"])' $f)"; done
```

This is projection-teardown-and-relays round 2's R2 with the raise verb as the return. 20 cycles alternate Home and screen off, 10 of each. **Stop rule:** 20 cycles or 3 crash segments, whichever comes first. A crash ends the segment and `crashloop` opens a new one. Sum each field over every segment of an arm.

**PASS (V9-C),** all of these:

- `fatal_count`, `aborting_count` and `died_count` (`ptr_crash.py`) are 0, and `fatal_exception_ours` (`v2_returns.py`) is 0, in every segment;
- `race_count` and `early_init_cycles` are 0 in every segment (no codec built on a dying surface);
- PH holds over every cycle window;
- 20 cycles ran, with `home_cycles_with_detach_stop` at least 8 and `off_cycles_with_activity_stop` at least 8.

**FAIL:** any crash line in our process, a `race_count` or `early_init_cycles` above 0, or a failed PH. **INCONCLUSIVE:** fewer than 8 Home cycles with a detach stop. **V9-B is a regression guard on main, not a positive control:** main carries the crash fix, and the fix measured 0 of 20 in that thread. Report it beside the candidate and give it the same conditions.

### OL. Output-thread logs, graded across the round's captures

From every `<ID>-C.returns.json` and `<ID>-B.returns.json` of V1G, V1H, V2T, V2S, V5 and V8:

- **Prefix (candidate):** `throughput_prefix.prefixed` equals `throughput_prefix.total` in every candidate capture. **FAIL** if a candidate capture has a `Throughput over` line without the `VideoDecoder.logThroughput | ` origin. Quote one sample per arm.
- **Cadence:** sum `cadence.observed_windows` and `cadence.expected_windows` per arm. **FAIL** if the candidate's ratio is below the baseline's by more than **0.05**, or below **0.80**.
- **Report only:** `Codec exception in output thread`, `Decoder stall detected` and `Forcing restart (` per run and arm. Round 1 found the exception line 2 to 4 times per baseline run and never on the old candidate, which could not tell a lost line from an absent exception.

## 8. Do not re-run

- **The codec pin over 20 background cycles (round 1 V3S, V3G):** both arms held it, and the rebase does not touch `codecTypePinned` (0 lines of the diff).
- **Software MediaCodec overload and fault injection (round 1 V6A, V6B):** both passed on the old candidate. OL re-checks the output-log retention they were guarding.
- **The tablets (round 1 V7S on D-SAM, V7P on D-HP):** both passed on the old candidate. This round spends its time on D-HU's regression; a later round adds them if this one passes.
- **A second phone (round 1 V1M):** UNTESTABLE for a Gearhead cache on D-MOTO that only a data clear fixes.
- **Whether Home tears down a GLES surface on D-HU:** the rig has answered both ways (pr-1046 round 1: yes; projection-teardown-and-relays round 1 and 2: no). This round measures it per return in `kept_surface` and grades on that, so it needs no run of its own.

## 9. Report back

The numbers that decide whether the branch is ready to merge:

1. **V1G and V1H:** one table per run with T for both arms per return, beside `kept_surface`, `cycle_rel_resume_s`, `rearm_lines` and `nudges`, and the count of B1 returns. State plainly whether a GLES screen-off return still loses its picture on the candidate.
2. **V8:** per cycle, both arms: whether it reached the mechanism, `ssl_after_go_s`, `picture_after_ssl_s`, `first_frame_nudges` and the phone's `car_state`.
3. **V2T:** T per return for both arms, `rearm_lines` and `cb_after_rearm` per return, and the unsolicited gains per stop.
4. **V9 and the crash counts of every run:** both arms side by side.

Results go in `pr-1046-video-retirement-round2-results.md` in the template's skeleton (section 7), one `## <id>` section per run id with a bolded verdict alone on its line: R0, V1G-B, V1G-C, V1H-B, V1H-C, V2T-B, V2T-C, V2S-B, V2S-C, V5-B, V5-C, V8-B, V8-C, V9-B, V9-C, OL. A `-B` section has no verdict on the branch: give it **PASS** when the run executed cleanly, and say so.

```markdown
# pr-1046-video-retirement, round 2 results

**Candidate:** PR head b749525d merged onto main 7102b428, tree b0efa0bd, merge commit <sha>       **Baseline:** main @ 7102b4283666ffcc7802e49402735e3958cd51a0
**APK md5:** <candidate> / <baseline>
**Unit:** D-HU (UNISOC MT50, Android 14) with D-POCO (Gearhead <version>)
**Date:** <yyyy-mm-dd>

## Setup notes

The settings.xml delta against round 2 of projection-teardown-and-relays, both md5s, the identity greps, the ACTION_QUERY_STATE commits, the bond reads and any request sent, the audio keys and kill-on-disconnect as found, the starting-state block output, every script used or added, every deviation and every string that did not match.

## <id> (one per run id above)

**PASS** | **FAIL** | **INCONCLUSIVE** | **UNTESTABLE**

- Settings written:
- Radio state and how it was set:
- Discard-rule check: clean / re-run N times
- Thermal: th_report output
- Decisive lines, quoted with device timestamps:
- Measurements: the per-return or per-cycle table the run asks for

## Anything the brief did not ask about
```

**Final step after the last run:** restore `settings-backup-HU.xml` as root with the app stopped, `chown u0_a176:u0_a176` and `chmod 660` it, then delete `video-profile-starvation-cap` and read it back. The count must print 0, and the restored file must diff clean against the backup apart from that key.

```bash
$PUT "$BASEXML" del:video-profile-starvation-cap
adb -s $HU shell cat /data/data/$PKG/shared_prefs/settings.xml | grep -ac video-profile-starvation-cap     # must print 0
adb -s $HU shell wm density | tr -d '\r'                                                                   # must match the starting state
```

Captures go to the existing release **`rig-evidence-pr-1046-video-retirement`** as one asset, `pr-1046-video-retirement-round2-captures.zip` (template section 7). Leave the APKs out and quote their md5s. Cite the asset name and its sha256 in the results file.

```bash
zip -1 -r pr-1046-video-retirement-round2-captures.zip $OUT -x '*.apk'
sha256sum pr-1046-video-retirement-round2-captures.zip
gh release upload rig-evidence-pr-1046-video-retirement --repo o-jcardenass/open-headunit pr-1046-video-retirement-round2-captures.zip
```

## 10. Strings, for mechanical re-verification

Every string the runs grep, one per line, in source form. Lines 1 to 59 are in `app/src/main` on both arms. Lines 60 and 61 are in the candidate only (`VideoDecoder.logThroughput` is the explicit origin `AppLog.tagged` prints; on the baseline the same prefix comes from `AppLog`'s stack walk). Lines 62 to 69 are platform or Gearhead lines and are not in this tree. Lines 70 to 74 are the identity symbols, grepped in the DEX: the first four are in the candidate only and `detachCurrentSurface` is in the baseline only.

```decisive-strings
AapProjectionActivity: onResume
AapProjectionActivity: onStop
AapProjectionActivity: onNewIntent received
AapProjectionActivity.onDestroy called.
AapService: raising the projection by
AutomationReceiver: 
AutomationMarker: 
[UI_DEBUG] [AapProjectionActivity] onSurfaceChanged
New surface set: 
GlProjectionView: onSurfaceChanged
TextureProjectionView: Surface available
SurfaceCallback: onSurfaceDestroyed
SurfaceCallback: onSurfaceDestroyed for a stale surface - ignoring
Decoder stopped: 
surfaceDestroyed
onDetachedFromWindow
projectionViewRecreate
screen_off_sleep
activity_stopped
skipped: surface is no longer current
Codec initialized:
Throughput over 
First frame rendered (hardware decode)
no keyframe has decoded
VideoDecoder: keyframe decoded - the picture is repaired
relaunched surface has no picture after 
relaunched surface still has no picture - requesting video focus (unsolicited)
AapProjectionActivity: returned to a kept surface with a stopped decoder; re-arming keyframe recovery
Watchdog: No video received yet. Requesting Keyframe (Unsolicited Focus)...
AapProjectionActivity: connected but no frames - requesting video focus (unsolicited)
AapTransport: Requesting recovery keyframe (unsolicited focus gain).
Decoder stall detected
Forcing restart (
Codec exception in output thread
times in a row without rendering a frame
Both codec types failed
Giving up to avoid an infinite restart loop
Falling back to SurfaceView for this session
SSL handshake complete
AapService: ACTION_END_SESSION_STAY_ARMED received
AapService: Native AA session ended; keeping the 
AapProjectionActivity: Unexpected disconnect. Showing reconnecting overlay
AapProjectionActivity: Finishing because state
Hiding reconnecting overlay - 
Media Sink Setup Request: 
Projection backend: viewMode=
WifiLauncher: Initializing WiFi Mode: 
HotspotManager:
SoftApCredentials:
HotspotManager: Setting hotspot enabled=
MATCH! Starting AapService
createGroup SUCCESS
Magic Garbage detected in header
Decoder restart requested: 
ACTION_RAISE_PROJECTION
ACTION_END_SESSION_STAY_ARMED
ACTION_LOG_MARKER
ACTION_QUERY_STATE
ACTION_EXIT
skipped: activity has no owned surface
VideoDecoder.logThroughput
Fatal signal
ABORTING
FATAL EXCEPTION
Process: com.andrerinas.headunitrevived
has died
Critical error
Car connection state changed
OutOfCarLifecycle
detachSurfaceIfCurrent
SurfaceRecoveryState
surfaceStopGeneration
outputPublicationLock
detachCurrentSurface
```
