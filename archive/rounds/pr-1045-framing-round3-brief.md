> **SUPERSEDED (2026-10-09). Do not run.** The PR head this brief pins was rebased. `pr-stack-1047-1067-round1-brief.md` replaces it.

# pr-1045-framing, round 3 brief: the parse proof and the load soak, candidate only, about 1 hour

One stage only: D-HU as head unit with D-POCO over Native AA. Two runs, both on the candidate: A1-C (about 8 minutes) and A2-C (about 15 minutes). **No baseline arm and no USB stage.** Round 2 (`pr-1045-framing-round2-results.md`) failed nothing. This round closes the two gaps it left: the phone title that the helper could not read, and the load that A2 never reached.

## 1. Build: reuse round 2's candidate

The candidate is round 2's, unchanged: PR head `9b8f83664cc5081f3d023ac699c0b5fe757334c6` merged onto main `7102b4283666ffcc7802e49402735e3958cd51a0`, tree `11f0bfc99564ab41c0fce09b99f7c348daf66691`, 2855 JVM tests.

1. Check that the PR did not move:

```bash
git ls-remote https://github.com/andreknieriem/open-headunit.git refs/pull/1045/head
# MUST print 9b8f83664cc5081f3d023ac699c0b5fe757334c6
```

   If it prints another SHA, stop and report it. The PR moved, and this brief did not check the new code.
2. Reuse round 2's APK `C1045-8d7da0e94.apk` if it is on disk with md5 `633a1fec957223cfb98991aa9c6cba2e`. Say so in Setup notes.
3. If it is not on disk, rebuild it with the commands in `pr-1045-framing-round2-brief.md` §1 (arm C only). The tree must print `11f0bfc9...` and the JVM tests must read 2855, 0 failures. Record the new md5.
4. Install with `adb -s $HU install -r -d <apk>`. Then run `ident2 cand`. It must print `IDENT-OK`. Quote the `commit` line (`8d7da0e94fd5` for the reused APK, or the new local merge commit).

**`main` moved to `f749b84c` after round 2. Do not rebase onto it.** The three new commits change the floating button and the update link only. Keeping the candidate on `7102b428` keeps round 2's numbers comparable.

## 2. What is open and why there is no baseline arm

| Round 2 run | Verdict | Why | This round |
|---|---|---|---|
| A1 | INCONCLUSIVE | `playing_title` printed nothing on D-POCO, so the title check could not be graded. Every other condition held. | A1-C again, with a fixed helper |
| A2 | INCONCLUSIVE | `fed_fps_median` was 13 (B) and 9 (C) against a floor of 25: a pan every 15 s does not keep the picture moving | A2-C again, with continuous pans and a load gate |
| U2S | INCONCLUSIVE | the title check only; every other condition held on the libusb path | not rerun (§7) |

**No baseline arm, for three reasons.**

- A1's title check compares the app's title with the phone's own title. It needs no second APK.
- Round 2's baseline did not reach the A2 load either, so it gives no reference. A2-C is graded on absolute conditions: the head unit keeps up with what it receives, and the rate holds across the window.
- The phone's ping control ran on round 2's baseline only. Here the phone capture is checked a different way: Gearhead prints at least one `Critical error` line at every session end we cause (`finish`). A phone capture with 0 such lines failed, and its phone-side conditions are INCONCLUSIVE.

**The load gate.** Before A2's window opens, the script pans the map for 65 s and reads the frame rate the phone sent. Below 25 fps it restarts navigation, raises the projection and tries once more. A second miss ends A2 without a soak. This stops a soak that cannot answer its question, which cost 40 minutes in round 2.

**A low load can itself be a regression.** If the head unit returns acks late, the phone lowers its video rate. So a missed gate is graded from the phone capture too: `Waiting for ack timeout, video frame dropped` of 10 or more makes it a FAIL, not an INCONCLUSIVE.

## 3. What is different about this round

- **Only one APK is installed.** Run ids end `-C`.
- **The title helper is fixed** (§5). Round 2's version parsed the playback state as a number only. This version also parses a state that is printed as a name. Every titles file now carries the phone's raw media lines too. If the parsed `phone:` line is still empty, the grader reads the title from that raw block.
- **A2 is 10 minutes, not 20.** No reassembler drop is expected (round 2: 0 `AapRead: skipped message:` on every candidate run), so a longer window adds time, not evidence. The pans are continuous, as in `pr-1067-tls-pump-round2-brief.md`, whose soak held 30 fps.
- **Pre-registered outcomes:**
  - CR reads "not exercised for reassembler drops" when no run prints `AapRead: skipped message:`. This is expected and is not a FAIL.
  - The audio half of A2 is INCONCLUSIVE when `inbound.audio_nonzero_fraction` is below 0.8 (music did not play).
  - A2 is INCONCLUSIVE when the load gate misses twice and the phone shows fewer than 10 ack timeouts.

## 4. Settings

The keys are those in `pr-1045-framing-round2-brief.md` §4, stage A only. Restore the stage backup with `pr1045_reset.sh`, write the run's keys with `set_hu_prefs.sh`, and read every key back before each launch. A1-C uses `log-level` 0 (VERBOSE). A2-C uses `log-level` 2 (INFO). Delete `video-profile-starvation-cap` before each run and read it back as absent.

**Read and quote, never write:** `use-aac-audio`, `audio-latency-multiplier`, `audio-queue-capacity`, `view-mode`, `wifi-direct-band`. `enable-audio-sink` must read `true`; if it reads `false`, write `true` and say so.

## 5. Helpers

Round 2 left `pr1045r2_helpers.sh` (with its fixed `wait_more`), `pr1045r2_extract.py`, `pr1045_helpers.sh` and `pr1045_extract.py` in `hur-wifi-test-scripts/`. Keep them. Save the two blocks below beside them as `pr1045r3_helpers.sh` and `load3.py`. Source the helpers at the top of every call.

```bash
# $HOME/pr1045r3-captures/env
HU=27870808938846     # D-HU
PH=4f4027e9           # D-POCO
```

```bash
# pr1045r3_helpers.sh : round 3. Source at the top of every call.
SCR=${SCR:-hur-wifi-test-scripts}
X=$HOME/pr1045r3-captures; mkdir -p "$X"
. "$SCR/pr1045r2_helpers.sh"          # round 2 as run: send, mark, begin_wireless, finish, report, both, ident2, nav_start, pss, app_title, soak_stop, th_*
X=$HOME/pr1045r3-captures; OUT=$X

# playing_title SERIAL : fixed. Reads the state as "3", "PLAYING(3)" or "PLAYING".
playing_title() { adb -s "$1" shell dumpsys media_session | awk '
  /package=/{ if (p != "" && s == 3) print p " | " d; p = $0; sub(/.*package=/, "", p); s = 0; d = "" }
  /state=PlaybackState \{state=/{ x = $0; sub(/.*state=PlaybackState \{state=/, "", x)
    if (x ~ /^[A-Z_]+\(/) sub(/^[A-Z_]+\(/, "", x); else if (x ~ /^PLAYING/) x = "3"
    sub(/[^0-9].*/, "", x); s = x + 0 }
  /metadata:/{ d = $0; sub(/.*description=/, "", d) }
  END{ if (p != "" && s == 3) print p " | " d }'; }
# media_raw SERIAL : the raw lines the title comes from, for the grader when playing_title is empty
media_raw() { adb -s "$1" shell dumpsys media_session | grep -a -E 'package=|state=PlaybackState|description=' | head -40; }
titles() {  # titles RID : both titles, then the phone's raw block
  { echo "phone: $(playing_title "$PH")"; echo "app:   $(app_title "$HU")"; echo "--- phone raw"; media_raw "$PH"; } | tee "$X/$1.titles.txt"; }

# pan_for SECS : drags the projected map back and forth; the only adb caller on $HU while it runs
pan_for() { local end=$((SECONDS+$1))
  while [ $SECONDS -lt $end ]; do adb -s "$HU" shell input swipe 500 400 1000 300 350; sleep 0.8
    adb -s "$HU" shell input swipe 1000 300 500 400 350; sleep 0.8; done; }

# load_gate RID : 65 s of pans, then the frame rate the phone sent. LOAD-OK, or one retry, then LOAD-LOW.
load_gate() { local k r
  for k in 1 2; do mark "$1-gate"; pan_for 65
    r=$(python3 -I "$SCR/load3.py" "$X/$1.hu.logcat" "$1-gate"); echo "gate$k $r" | tee -a "$X/$1.gate.txt"
    echo "$r" | python3 -I -c 'import json,sys; d=json.load(sys.stdin); sys.exit(0 if (d.get("fed_fps_median") or 0) >= 25 else 1)' && { echo LOAD-OK; return 0; }
    nav_start; sleep 5; send ACTION_RAISE_PROJECTION >/dev/null; sleep 10
  done; echo LOAD-LOW; return 1; }

# soak3_start RID : continuous pans, and one track skip on D-HU every 40 s. Stop it with soak_stop.
soak3_loop() { local last=$SECONDS
  while :; do adb -s "$HU" shell input swipe 500 400 1000 300 350; sleep 0.8; adb -s "$HU" shell input swipe 1000 300 500 400 350; sleep 0.8
    if [ $((SECONDS-last)) -ge 40 ]; then adb -s "$HU" shell input keyevent KEYCODE_MEDIA_NEXT; echo "$(date +%T) skip" >> "$X/$1.skips.log"; last=$SECONDS; fi
  done; }
soak3_start() { setsid nohup bash -c "HU=$HU; X=$X; $(declare -f soak3_loop); soak3_loop $1" >/dev/null 2>&1 & echo $! > "$X/soak.pid"; sleep 1; }
```

```python
#!/usr/bin/env python3
# load3.py CAP START_LABEL [END_LABEL] : the video load a marker window carried. Counts only, no verdict.
# The window starts at the LAST 'AutomationMarker: START_LABEL' and ends at the first END_LABEL after it,
# or at the end of the file when END_LABEL is not given (the load gate).
import json, re, statistics, sys
cap, a = sys.argv[1], sys.argv[2]
b = sys.argv[3] if len(sys.argv) > 3 else None
L = open(cap, 'rb').read().decode('latin-1').splitlines()
def find(label, start=0):
    rx = re.compile(r'AutomationMarker: ' + re.escape(label) + r'\s*$')
    return [k for k in range(start, len(L)) if rx.search(L[k])]
s = find(a)
if not s:
    print(json.dumps({'error': 'start marker not found: ' + a}))
    sys.exit(0)
i, j = s[-1], len(L) - 1
if b:
    e = find(b, i)
    if not e:
        print(json.dumps({'error': 'end marker not found: ' + b}))
        sys.exit(0)
    j = e[0]
W = L[i:j + 1]
THR = re.compile(r'Throughput over \d+ms: rendered=\d+ \((\d+)fps\), fed=\d+ \((\d+)fps\)')
INB = re.compile(r'inbound rate over \d+ms: video=(\d+)kB/s \((\d+) msgs\)')
t = [tuple(map(int, m.groups())) for m in map(THR.search, W) if m]
v = [tuple(map(int, m.groups())) for m in map(INB.search, W) if m]
def med(x):
    return statistics.median(x) if x else None
print(json.dumps({'window_lines': len(W), 'throughput_windows': len(t),
                  'rendered_fps_median': med([r for r, _ in t]), 'fed_fps_median': med([f for _, f in t]),
                  'inbound_windows': len(v), 'video_kBps_median': med([k for k, _ in v]),
                  'video_msgs_median': med([n for _, n in v])}))
```

`playing_title` was tested on three sample formats and `load3.py` on a synthetic capture before this brief was written. If either fails on a real capture, put the output in Setup notes and continue: the raw block and the round 2 extractors still carry the numbers.

**Every run ends with `both RID`** (both extractors, as in round 2). **Contamination:** as round 2 §5. `MATCH! Starting AapService` above 1, a second `createGroup SUCCESS`, `Magic Garbage detected in header` above 0, or a second INFO-level `SSL handshake complete. ` voids the run. Re-run a void run once, then mark it INCONCLUSIVE.

## 6. Runs

**Before the stage:**

1. If D-POCO was a head unit today, send it `adb -s 4f4027e9 shell am start -a android.intent.action.VIEW -d "headunit://exit"`, then `sleep 3`, then `adb -s 4f4027e9 shell am force-stop com.andrerinas.headunitrevived`.
2. D-POCO unlocked and on its home screen (`adb -s $PH shell dumpsys window | grep mCurrentFocus`; `adb -s $PH shell input keyevent KEYCODE_HOME` if not).
3. D-MOTO's Bluetooth off (`adb -s ZY22GC3BM4 shell svc bluetooth disable`), checked with `dumpsys bluetooth_manager`. Before every launch, check that no `th_watch` loop from an earlier round holds the rig lock (`pgrep -af th_watch`); kill such a process by its pid.
4. Quote the Gearhead `versionName`, D-HU's `stat` of `shared_prefs/`, and the audio keys. Take the stage backup.
5. **Title check (the fix for round 2):** `adb -s $PH shell input keyevent KEYCODE_MEDIA_PLAY`, `sleep 5`, then `playing_title $PH`. It must print one line. If it prints nothing, run `media_raw $PH`, quote the output in Setup notes, and continue: the grader then reads titles from the raw blocks. If nothing is playing, start a track on D-POCO by hand (hand step, reason: choosing a track in a phone app has no verb on the app under test) and say so.

Before every run set `RUN=<RID>` and call `th_gate`; after it, call `th_report <RID>` and quote the line. Between the runs: `finish` ends the session; then `headunit://exit` on D-HU, `sleep 3`, force-stop, restore the backup, and write the next run's keys.

### A1-C. Album art over the new copy path, VERBOSE (point of the round)

Steps are round 2's A1 with the candidate arm only.

```bash
source $SCR/pr1045r3_helpers.sh
RUN=A1-C; th_gate
begin_wireless A1-C || echo RERUN
adb -s $PH shell input keyevent KEYCODE_MEDIA_PLAY; sleep 10
playing_title $PH | tee $X/A1-C.before.txt
mark A1-C-start
```

Second call:

```bash
source $SCR/pr1045r3_helpers.sh
for i in $(seq 1 20); do                      # one sequential loop: skip, then pan, never two adb callers on D-HU at once
  adb -s $HU shell input keyevent KEYCODE_MEDIA_NEXT; sleep 3
  adb -s $HU shell input swipe 500 400 1000 300 350; sleep 0.8; adb -s $HU shell input swipe 1000 300 500 400 350; sleep 5
done
sleep 30; titles A1-C
finish A1-C; both A1-C; th_report A1-C
```

**Reachability:** `music_playback_runs.complete_runs` at least 10, and `runs_with_3plus_fragments` at least 1.

**PASS, all of:**
1. `zero_list_total` is 0.
2. `zero_list['AapTransport quitting']` is 0, and the file holds one INFO-level `SSL handshake complete. ` (subtract `other_counts['Handshake: SSL handshake complete']` first).
3. **Title check:** in `A1-C.titles.txt`, the `app:` description starts with the same title as the phone's playing description (the text before the first `, `). Read the phone title from the `phone:` line, or from the `--- phone raw` block when that line is empty (the session whose state reads 3 or `PLAYING`), and say which.
4. Phone: `Critical error` at least 1 (capture health) and at most 4 (round 2 C read 3), and no line names a protocol refusal (for example `Multiple media configs received`).

**FAIL:** condition 1, 2 or 4 missed, or the titles differ. **INCONCLUSIVE:** reachability missed, or neither the `phone:` line nor the raw block shows a playing session.

### A2-C. Wireless load soak, 10 minutes, behind the load gate (point of the round)

Settings per §4 (INFO, H.265 hardware, 1080p, 60 fps). Navigation on the phone, music playing, continuous pans, a track skip every 40 s.

```bash
source $SCR/pr1045r3_helpers.sh
RUN=A2-C; th_gate
begin_wireless A2-C || echo RERUN
nav_start; sleep 5
adb -s $PH shell input keyevent KEYCODE_MEDIA_PLAY; sleep 10
playing_title $PH | tee $X/A2-C.before.txt
load_gate A2-C
```

**If the last line is `LOAD-LOW`:** run `finish A2-C; both A2-C; th_report A2-C`. Do not run the soak. Grade by the gate rule below.

**If it is `LOAD-OK`,** second call:

```bash
source $SCR/pr1045r3_helpers.sh
pss | tee $X/A2-C.pss-start.txt
mark A2-C-start
soak3_start A2-C
hold 300
```

Third call:

```bash
source $SCR/pr1045r3_helpers.sh
hold 300; soak_stop
pss | tee $X/A2-C.pss-end.txt
titles A2-C
finish A2-C; both A2-C; th_report A2-C
python3 -I $SCR/load3.py $X/A2-C.hu.logcat A2-C-start A2-C-end | tee $X/A2-C.load3.json
```

`soak_stop` must print `soak-stopped` before `pss` runs. If it prints `SOAK-STILL-RUNNING`, run `kill -9 -- -$(cat $X/soak.pid)` and say so.

**Gate rule (A2 with `LOAD-LOW`):** read the phone's `Waiting for ack timeout, video frame dropped` count from `both A2-C`. **FAIL** if it is 10 or more: the phone lowered its rate because acks ran late. **INCONCLUSIVE** otherwise: the phone content did not produce the load. Quote both `gate` lines.

**Reachability (A2 with `LOAD-OK`):** `throughput.windows` at least 110; `throughput.fed_fps_median` at least 25; `A2-C.skips.log` at least 12 lines. Audio half: `inbound.audio_nonzero_fraction` at least 0.8, else condition 6 is INCONCLUSIVE.

**PASS, all of:**
1. `zero_list_total` is 0.
2. `zero_list['AapTransport quitting']` is 0, and the file holds one SSL line.
3. **The head unit keeps up:** `throughput.rendered_fps_median` at least 0.95 times `throughput.fed_fps_median`; `dropped_sum` at most 10; `dispatch.videoShed_sum` 0.
4. **No credit decay on video:** `trend.rendered_fps_median_by_third[2]` at least 0.90 times `[0]`, and `throughput.longest_zero_rendered_run_windows` at most 4. If `trend.inbound_video_kBps_median_by_third[2]` is below 0.70 times `[0]`, the phone content changed: grade the first half of this condition INCONCLUSIVE and say so.
5. Memory: `TOTAL PSS` growth (end minus start) at most 61 MB (round 2's baseline growth of 11 MB plus 50).
6. **No credit decay on audio:** `trend.inbound_audio_kBps_median_by_third[2]` above 0.
7. `audio_timing_non_audio_channel` is 0.
8. Title check as A1 condition 3, from `A2-C.titles.txt`.
9. Phone: `VIDEO_ACK_TIMEOUT` 0; `Waiting for ack timeout, video frame dropped` at most 10; `Received out of order ping response` at most 1; `Critical error` at least 1 (capture health) and at most 4, none naming a protocol refusal.

**FAIL:** any of 1 to 9 missed, except where marked INCONCLUSIVE, or a session end. One retry: if the session ends and the 15 s before `AapTransport quitting` hold `link has been silent` and `WiFi read timeout` (a rig link stall), re-run A2-C once. A second session end is a FAIL.

### CR. Credits never leak

As round 2 §7 CR, over A1-C and A2-C. Expected: no `skipped_messages` entry, so CR reads "not exercised for reassembler drops".

### Final step

On D-HU: force-stop the app, delete `video-profile-starvation-cap` and read it back as absent, restore the stage backup and read it back. Turn D-MOTO's Bluetooth back on. Check that no logcat reader and no `th_watch` loop is left running. Quote all of it in Setup notes.

## 7. Do not re-run

| Round 2 run | Why not |
|---|---|
| R0 | The candidate APK is the same, or a rebuild of the same tree that R0 already passed. The identity check in §1 replaces it. |
| A1-B, A2-B, all baseline runs | Round 2's baseline gave no load reference, and the title check needs none (§2). |
| I5 | PASS: repair median 2858 ms against 2833 ms, detection 61 ms or less. |
| P-U, U1 | PASS: 6 of 6 sessions on both arms, 5 of 5 re-enumerations. |
| U2S | Every condition except the title check held: 29 fps over the whole window, 0 zero-list lines, 0 parse failures across 20 skips on the libusb path. The title check proves the parsed content, and A1-C proves it on the same copy path. The USB stage costs an hour of setup for that one check. |
| Round 1 runs | As listed in round 2 §8. |

## 8. Report back

1. **A1-C:** `complete_runs`, `max_fragments`, `max_run_bytes`, the zero list, and both titles (and where the phone title was read).
2. **A2-C:** both `gate` lines; `rendered_fps_median`, `fed_fps_median`, `dropped_sum`, `videoShed_sum`; the rendered, video and audio thirds; PSS growth; the phone's ack-timeout, out-of-order ping, `VIDEO_ACK_TIMEOUT` and `Critical error` counts.
3. **CR:** the number of `AapRead: skipped message:` entries per run, or "not exercised".

**Captures** go to the existing release `rig-evidence-pr-1045-framing` as the asset `pr-1045-framing-round3-captures.zip`, with its sha256 in the results file:

```bash
zip -1 -r pr-1045-framing-round3-captures.zip $X
sha256sum pr-1045-framing-round3-captures.zip
gh release upload rig-evidence-pr-1045-framing --repo o-jcardenass/open-headunit pr-1045-framing-round3-captures.zip
```

## 9. Results file skeleton

Name: `pr-1045-framing-round3-results.md`, following template §7. The bold verdict sits alone on the line under each `## <RunId>` heading.

```markdown
# pr-1045-framing, round 3 results

**Candidate:** PR head 9b8f8366 merged onto main 7102b428, tree 11f0bfc9, merge commit <sha>, APK md5 <md5> (reused or rebuilt)
**Unit:** D-HU (<chipset, API>) with D-POCO (<Gearhead versionName>)
**Date:** <yyyy-mm-dd>

## Setup notes
Deviations, scripts used and added, the title check result (or the raw block), stat of shared_prefs, the audio keys, thermal lines.

## A1-C
**PASS|FAIL|INCONCLUSIVE**

## A2-C
**PASS|FAIL|INCONCLUSIVE**

## CR
**PASS|FAIL|not exercised**

## Summary
| question | runs | verdict | the deciding number |
|---|---|---|---|
| copy path carries album art | A1-C | | |
| no regression under load | A2-C | | |
| credits never leak | CR, A2-C 4/6/9 | | |

## Round verdict: <PASS|FAIL|INCONCLUSIVE>

## Anything the brief did not ask about
```

The lines below are the head unit strings this round greps. Each was checked with `git grep -F` against the candidate tree `11f0bfc9`.

```decisive-strings
AapRead: skipped message: %s (suppressed=%d)
AapRead: invalid framing or TLS session
AapRead: Error in read loop (ignored)
AapRead: Fatal read error
AapRead: Magic Garbage detected
AapVideo: discarding a %d-byte access unit the framing audit found short
AapMediaPlayback: Failed to parse metadata:
AapTransport quitting (clean=
SSL handshake complete. 
Handshake: SSL handshake complete.
Audio transport read channel=
inbound rate over 
Throughput over 
fed=$fed (${fedFps}fps)
transport dispatch over
RECV: %s
MUSIC_PLAYBACK
AutomationMarker: 
AutomationReceiver: 
createGroup SUCCESS
MATCH! Starting AapService
sync-media-session-aa-metadata
onDroppedMediaData
```

```decisive-strings-gearhead
Received out of order ping response, received: %d, in queue: %d
Waiting for ack timeout, video frame dropped
VIDEO_ACK_TIMEOUT
Critical error
```
