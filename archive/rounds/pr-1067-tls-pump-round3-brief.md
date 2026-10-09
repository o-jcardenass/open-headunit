> **SUPERSEDED (2026-10-09). Do not run.** The PR head this brief pins was rebased. `pr-stack-1047-1067-round1-brief.md` replaces it.

# pr-1067-tls-pump, round 3 brief: one soak on the candidate, behind a load gate, about 40 minutes

One stage only: D-HU as head unit with D-POCO over Native AA. One run: KW-C, a 15-minute soak on the candidate. **No baseline arm, no phone exits and no operator.** Round 2 (`pr-1067-tls-pump-round2-results.md`) failed nothing. This round closes the one gap that a rerun can close: the soak whose phone sent a tenth of the reference load.

## 1. Build: reuse round 2's candidate

The candidate is round 2's, unchanged: PR head `7e7cbf53814a5bdbc9436f17c5a2f501d148c6e0` merged onto main `7102b4283666ffcc7802e49402735e3958cd51a0`, tree `9a77cc00a41e65d1b4fb9c69bb740c211b798de9`, 2827 JVM tests.

1. Check that the PR did not move:

```bash
git ls-remote https://github.com/andreknieriem/open-headunit.git refs/pull/1067/head
# MUST print 7e7cbf53814a5bdbc9436f17c5a2f501d148c6e0
```

   If it prints another SHA, stop and report it. The PR moved, and this brief did not check the new code.
2. Reuse round 2's candidate APK if it is on disk with md5 `7df7c7f5cbad7f4b6928e4c90068986c`. Say so in Setup notes.
3. If it is not on disk, rebuild it with the commands in `pr-1067-tls-pump-round2-brief.md` §1 (arm C only). The tree must print `9a77cc00...`, and the JVM tests must read 2827 with 0 failures. Record the new md5.
4. Install with `adb -s $HU install -r -d <apk>`. Identity as round 2 §1: `encrypted write failed or incomplete` counts 1 or more in the pulled DEX, and `ACTION_QUERY_STATE` returns `db3153cddb75` (reused) or the new local merge commit.

**`main` moved to `f749b84c` after round 2. Do not rebase onto it.** The three new commits change the floating button and the update link only. Keeping the candidate on `7102b428` keeps round 2's reference valid.

## 2. What is open and why there is no baseline arm

Round 2's KW-C met every condition but one: `rendered_median` was 127 against KW-B's 150, a ratio of 0.85 against a bar of 0.90. In the same window the phone sent 68 kB/s of video against 694 kB/s on KW-B. The candidate kept up with all of it: `dropped` 0, `videoShed` 0, `unread` 1%. The C arm ran second, after 10 phone exits and reconnects, and `pr-1045-framing` round 2 shows the same drop in load on its second arm. So the likely cause is the phone's screen content, not the receive path.

**The reference is round 2's KW-B, frozen.** A new baseline arm costs a second install and 15 more minutes, and it measures the phone, not the PR. Instead, the soak opens only when the phone sends a load close to the reference, and the window's own load is checked again at the end.

| KW-B, round 2 (the reference) | Value |
|---|---|
| window | 900487 ms, 180 `Throughput over` windows, max gap 5014 ms |
| `rendered_median` (per 5 s window) | 150 |
| inbound video, median over 30 s windows | 694 kB/s, 1841 msgs |
| TLS zero list, audit, `video_discard` | all 0 |
| phone `critical_error` in the window | 0 |
| settings in force | `log-level` 0, `enable-audio-sink` false (video only), `resolutionId` 2, `video-codec` H.264, `audio-latency-multiplier` 8, `audio-queue-capacity` 20 |

**A low load can itself be a regression.** If the head unit returns acks late, the phone lowers its video rate. So a missed gate is graded from the phone capture too (§5).

## 3. What is different about this round

- **The soak starts behind a load gate.** After the first session forms, the script starts navigation on the phone, swipes for 65 s and reads the load from the head unit log. The gate needs a fed rate of 25 fps or more and inbound video of 400 kB/s or more. On a miss it restarts navigation, raises the projection and tries once more.
- **The settings must match the reference.** Read and quote `enable-audio-sink`, `resolutionId`, `video-codec`, `audio-latency-multiplier` and `audio-queue-capacity`. Never write them. If one differs from the table in §2, say so: condition 4 is then INCONCLUSIVE, and the other conditions are still graded.
- **No hand step.** The run needs no operator.
- **Pre-registered outcomes:** P (post-handshake records) is expected to read zero again. That is a record, not a failure.

## 4. Setup

Pre-flight: `rig_preflight.sh D_HU:wifi,bt D_POCO:wifi,bt`. Quote its output.

Make `hur-wifi-test-scripts/pr-1067-tls-pump-round3/`. Copy `ohu_lib.sh`, `ohu_setkeys.py`, `ptr_lib.sh`, `tp_lib.sh` and `tp_extract.py` into it from `hur-wifi-test-scripts/pr-1067-tls-pump-round2/`. Save `load3.py` and `kw3_lib.sh` (below) beside them. Run the block in §5 as one script in the background.

Before the run:

1. D-MOTO's Bluetooth off; D-SAM's Bluetooth off, or on with no link (Android 4.4.2 has no adb verb for it). Our app stopped on both (`send ACTION_EXIT`, then force-stop).
2. D-POCO unlocked and on its home screen (`adb -s 4f4027e9 shell dumpsys window | grep mCurrentFocus`; `adb -s 4f4027e9 shell input keyevent KEYCODE_HOME` if not). If D-POCO was a head unit today, send it `headunit://exit` first, then `sleep 3` and force-stop.
3. `pgrep -af th_watch`. Kill any such process by its pid: round 2 lost time to a leaked loop that held the rig lock.
4. `stat` `/data/data/$PKG/shared_prefs` on D-HU and record the owner. Quote the Gearhead `versionName` on D-POCO.

```bash
OUT=$PWD; source ./ohu_lib.sh; source ./ptr_lib.sh; source ./tp_lib.sh; source ./kw3_lib.sh
HU=27870808938846; PH=4f4027e9; PUT=hu_put; SW="500 400 1000 300"; HUNAME="D-HU (the head unit)"
KEYS="int:log-level=0 int:wifi-connection-mode=3 set:connection-modes=wifi int:onboarding-version=2 int:native-driver-selection-mode=0 del:video-profile-starvation-cap"
prefs_cat > $OUT/settings-backup-DHU.xml; BASEXML=$OUT/settings-backup-DHU.xml
WANT_MD5=<md5 of the candidate APK>
```

These are round 2's keys. After the install, read every key in `KEYS` back.

**`kw3_lib.sh`** (source after `tp_lib.sh`):

```bash
# kw3_lib.sh : round 3 additions. Needs everything tp_lib.sh needs.
nav_start() { adb -s $PH shell am start -a android.intent.action.VIEW -d 'google.navigation:q=Times+Square+New+York&mode=d' >/dev/null; }
# load_gate : 65 s of swipes, then the load they produced. LOAD-OK, or one retry, then LOAD-LOW.
load_gate() { local k r
  for k in 1 2; do mark KW-C-gate; swipe_for 65
    r=$(python3 -I load3.py "$CAP" KW-C-gate); echo "gate$k $r" | tee -a $OUT/KW-C.gate.txt
    echo "$r" | python3 -I -c 'import json,sys; d=json.load(sys.stdin); sys.exit(0 if (d.get("fed_fps_median") or 0) >= 25 and (d.get("video_kBps_median") or 0) >= 400 else 1)' && { echo LOAD-OK; return 0; }
    nav_start; sleep 5; send ACTION_RAISE_PROJECTION >/dev/null; sleep 10
  done; echo LOAD-LOW; return 1; }
# kw3 : one soak on the candidate. Returns 4 when the load gate missed twice.
kw3() { local rc=0; sess_open KW-C || return 1
  first_session KW-C || { close_run KW-C; return 3; }
  nav_start; sleep 5; music
  if load_gate; then both KW-C-start; soak_blocks 3; both KW-C-end
  else echo "LOAD_FAIL KW-C" | tee -a $OUT/KW-C.summary; rc=4; fi
  close_run KW-C
  grep -c 'Waiting for ack timeout, video frame dropped' "$PCAP" > $OUT/KW-C.acktimeouts.txt
  [ $rc = 0 ] && python3 -I load3.py "$CAP" KW-C-start KW-C-end > $OUT/KW-C.load3.json
  return $rc; }
```

**`load3.py`** (counts only, no verdicts):

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

`load3.py` was tested on a synthetic capture before this brief was written. `sess_open`, `first_session`, `music`, `swipe_for`, `soak_blocks`, `close_run`, `both`, `CAP` and `PCAP` come from round 2's `tp_lib.sh` and `ptr_lib.sh`. If a script does not match the real line format, fix it, say so in Setup notes, and continue.

## 5. KW-C. Fifteen-minute soak, graded against the frozen reference (the point of the round)

```bash
kw3; echo "kw3 rc=$?"
```

`rc=4` means the load gate missed twice. `rc=3` means no first session formed: follow round 2 §7's rule (run `rig_preflight.sh` once more, toggle D-HU's Bluetooth adapter off and on, try once more).

From `KW-C.json` (`tp_extract.py`, as in round 2): `all.ssl`, and in `windows["KW-C"]`: `ssl`, `quit_clean_true`, `quit_clean_false`, `tls_cannot_continue`, `ssl_decrypt_failed`, `enc_write_failed`, `audit_total`, `video_discard`, `throughput_windows`, `throughput_max_gap_ms`, `rendered_median`, `span_ms`; `phone.windows["KW-C"].critical_error`. From `KW-C.load3.json`: `fed_fps_median`, `rendered_fps_median`, `video_kBps_median`, `video_msgs_median`. From `KW-C.gate.txt`: both gate lines. From `KW-C.acktimeouts.txt`: the phone's ack timeout count.

**Gate rule (`rc=4`):** **FAIL** if `KW-C.acktimeouts.txt` reads 10 or more: the phone lowered its rate because acks ran late. **INCONCLUSIVE** otherwise: the phone content did not produce the load. Quote both gate lines.

**Reachability (`rc=0`):** `throughput_windows` at least 0.9 times `span_ms / 5000` (the capture kept up), and `video_kBps_median` in the window at least 416 kB/s (0.6 times the reference). If the window's video is below 416 kB/s, condition 4 is INCONCLUSIVE: run KW-C once more, and the second result is final.

**PASS, all of:**
1. One session: `all.ssl` 1; in the window, `ssl` 0 and both quitting counts 0.
2. TLS zero list: `tls_cannot_continue` 0, `ssl_decrypt_failed` 0, `enc_write_failed` 0.
3. The new audit: `audit_total` 0 and `video_discard` 0.
4. **The picture against the reference:** `throughput_windows` at least 171; `throughput_max_gap_ms` at most 15000; `rendered_median` at least 135 (0.9 times 150); `rendered_fps_median` at least 0.95 times `fed_fps_median` (the head unit renders what it is fed).
5. Phone: `critical_error` in the soak window 0, and the ack timeout count at most 10.

**FAIL:** any of 1 to 5 missed with reachability met. For an audit failure, quote the first five `AapRead: DELTA_CHANGED on` lines in full, with timestamps. For any other, quote the first zero-list line and the 20 lines before it.

**If the change did nothing,** this run would still PASS: the soak measures that the new TLS pump costs no frame rate under the reference load. It is a regression check, not a proof of the fix. The fix itself (finding 1) is proved by the JVM tests, because the race needs the phone to close the socket inside 500 ms, and it did not do so in 20 exits.

### P. Post-handshake TLS records (from the KW-C capture, no run of its own)

As round 2 §6 P, C only: report `produced0_M` summed over `sessions[]`, with `all.produced_any_M` above 0 as reachability. All zero means the phone sent no TLS-only record, so `AapTlsWriter.sendControl` did not run. Say that in those words.

### Final step

Restore `settings-backup-DHU.xml` with `$PUT` and diff it against the backup. Check that no logcat reader and no `th_watch` loop is left running (`ps`, `pgrep -af th_watch`). Quote all of it in Setup notes.

## 6. Do not run

| Round 2 run | Why not |
|---|---|
| R0 | The candidate APK is the same, or a rebuild of the same tree that R0 already passed. The identity check in §1 replaces it. |
| XW-B, XW-C | 20 phone exits, 0 defects. ByeBye to quit was 502 to 505 ms every time, so the phone never closed inside the window and the race never fired. More exits on this phone add time, not evidence. A rerun needs a fault lever (for example a debug delay before the quit), and the PR does not have one. |
| KW-B | Frozen as the reference (§2). |
| F5 | The candidate ended the session 104 ms after a failed write, as designed. Round 2's one baseline hit belonged to a session that had already ended. |
| S | This phone does not resume a session on a reconnect: 0 repeated ids in 35 reconnects. |

## 7. Report back

1. **KW-C:** both gate lines, `rc`, every field named in §5, and the zero-list lines if any printed. **These decide the merge question for the soak.**
2. **P:** the `produced0_M` sum with its reachability count.

Results go in `pr-1067-tls-pump-round3-results.md` in the template's skeleton (section 7), with `## R-identity`, `## KW-C` and `## P` sections and `th_report` output in the KW-C section. Captures go to the existing release `rig-evidence-pr-1067-tls-pump` as the asset `pr-1067-tls-pump-round3-captures.zip`, cited with its sha256.

The phone lines are Gearhead's and do not grep in our tree: `Critical error`, `Waiting for ack timeout, video frame dropped`. The block below lists every head unit string this round greps. Each was checked with `git grep -F` against the candidate tree `9a77cc00`.

```decisive-strings
AutomationReceiver: 
AutomationMarker: 
AapTransport quitting (clean=
AapTransport: encrypted write failed or incomplete
AapRead: TLS state cannot continue
SSL Decrypt failed
AapRead: %s on %s - %s%s
AapVideo: discarding a 
SSL Decrypt: produced 
SSL handshake complete. Session id: 
SSL handshake complete. No session id (full handshake).
Throughput over 
fed=$fed (${fedFps}fps)
inbound rate over 
Magic Garbage detected in header
MATCH! Starting AapService
```
