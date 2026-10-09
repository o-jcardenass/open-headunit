# pr-1045-framing, round 1 brief: an outside rewrite of AAP framing, TLS reads and message reassembly, against `main`

## 1. Build and baseline

| | Where | SHA | Gate |
|---|---|---|---|
| Candidate | `emotionbug/open-headunit`, branch `pr/split-aap-framing` | `dfb0d6f9653c544d9daddfe6c3d5b3cf6c947d97` (7 commits) | 2673 JVM tests, 0 failures |
| Baseline | `main` (local `main` and `origin/main` agree) | `7e9d813d66493f1cc978c0f6a259bdb613874bd1` | 2658 JVM tests, 0 failures |

The candidate is an outside contributor's branch, not ours, so it is not on the fork. It sits directly on the baseline (merge base `7e9d813d`), so the baseline is exactly "the same tree without the branch". History was not rewritten since this brief was drafted.

```bash
git fetch https://github.com/emotionbug/open-headunit.git pr/split-aap-framing
git checkout dfb0d6f9653c544d9daddfe6c3d5b3cf6c947d97
git log --oneline 7e9d813d..HEAD | wc -l          # 7
# baseline, in a second worktree or after the candidate is built and its APK copied out
git checkout 7e9d813d66493f1cc978c0f6a259bdb613874bd1
```

**Both builds are needed for almost every run.** Build each with `build_hur.sh`, and **copy each APK out of `apks/` into the round's own folder the moment it is built** (the script deletes the previous one). Name them `base-7e9d813d.apk` and `cand-dfb0d6f9.apk` and record both md5s from a real `adb pull` plus local `md5sum` (never `adb shell cat | md5sum`).

**Identity is a DEX symbol, never version name or a log string.** `AapMessageReassembler` and `TlsUnwrapLoop` exist only on the candidate (grepped: zero matches under `app/src/main` of the baseline). The helper `ident` in §5 checks it on the pulled APK. Install every build with `adb install -r` only, never uninstall, and **read every settings key back after every install**: an install over a live session has wiped `settings.xml` before.

Candidate commits, for orientation:

| commit | what it does |
|---|---|
| `021d9a48` | ping response echoes the phone's own timestamp instead of `System.nanoTime()` |
| `6d258175` | TLS: a delegated task finishing counts as unwrap progress |
| `adbde83b` | bounded envelopes and one per-channel message reassembler (`AapMessageReassembler`) |
| `6dbbaad6` | `Audio transport read/send` timing lines (new) |
| `5c2b9260` | the framing audit works on plaintext, the media ack is sent once per complete DATA message and carries the session captured at receipt |
| `301269ab` | diagnostics and reader size observations |
| `dfb0d6f9` | recovery scenarios kept at the reader unwrap boundary |

## 2. What this is and why it exists

The branch moves video, audio and control reassembly out of the per-channel handlers into one reassembler in the readers, replaces the learned "TLS overhead" accounting in the framing audit with the declared plaintext total, and rewrites the TLS unwrap loop. A code review plus a decompile of two Gearhead builds (17.5.663204 and 17.8.163744) confirmed the premise: the 4-byte total on a FIRST fragment is plaintext type plus payload, and Gearhead's own reader treats any mismatch as fatal. What is left is risk that only hardware can size:

1. **A narrower effective ack window.** The baseline read the message type from the first two payload bytes of every video message, so continuation fragments that happened to start `00 00` or `00 01` produced extra acks. On the phone's semaphore (`max_unacked` permits) every extra ack widens the window. The candidate sends exactly one ack per complete DATA message, so its effective window is narrower than the baseline ever was. That is protocol-correct, but it is the one change that can move fps, most of all on the 6-permit software-HEVC wireless window. After 400 consecutive 300 ms ack timeouts the phone tears the session down.
2. **Zero healthy-stream discards.** The new audit is stricter. On a healthy stream it must print nothing, and nothing must disconnect.
3. **Several "skip and carry on" cases are now disconnects** (a frame that decrypts to zero bytes, a length mismatch on a non-video channel, any `IOException` from a handler). None fires on the Gearhead builds we hold, so each run below proves it does not fire on real streams, old Android included.
4. **Injector parity.** Fault injection and the recovery it triggers must behave as on the baseline.
5. **Deferred ack across focus cycles.** Acks now carry the session captured at receipt, which must keep working when a focus cycle replaces the session while video is queued.

Phone-side evidence is part of the round. Gearhead's own log lines (verified in the decompiled 17.5 and 17.8 sources, §6c) say whether the phone is starved of acks and whether it still sees out-of-order pings.

## 3. What is different about this round

- **Two sweeps, not two arms per run.** Each unit gets a baseline sweep then a candidate sweep, so each unit sees exactly two APK installs. Run ids end `-B` (baseline) or `-C` (candidate). **A `-C` run is graded against its paired `-B` run.** A `-B` run is graded PASS when its reachability conditions are met and the window is complete, INCONCLUSIVE otherwise. It carries no judgement about the baseline.
- **The phone-side ping line is the positive control for the phone-side logger.** The baseline answers pings with `System.nanoTime()`, so on wireless every ping response is out of order and Gearhead should print `Received out of order ping response` (it rate-limits that line to one per 10 s, so expect roughly one per 10 s of session). If the baseline wireless run shows zero of them, the phone-side log lines are not being captured on that phone, and the phone-side counts for that unit are INCONCLUSIVE. The head-unit side decides the verdict. The phone only pings on wireless; USB runs have no phone-side control.
- **Several strings change on the candidate**, so a baseline grep and a candidate grep differ by design (§6b). None of the removed strings may be grepped on the candidate expecting a hit.
- **The rig's audio settings are a deliberate worst case**: `use-aac-audio` on, `audio-queue-capacity` 20 and a reduced `audio-latency-multiplier`. Read all three back with `readkey` and quote the exact saved values (D-HU was found at 8 for the multiplier on 2026-09-28), never write or reset them. Read every audio number as the thin arm.
- **D-HU hard-reboots under sustained multi-core spin load.** V3 (software HEVC) is the only run that loads it. Its abort rule is in the run.
- **D-HU cannot host USB** (`host_connected=false`). All USB runs use a phone as the head unit (§7, stages D and E). A phone in portrait can hold the projection activity foregrounded for only milliseconds, so video on those runs is best-effort and the video half of a USB run may be INCONCLUSIVE. Framing and TLS are graded from the log regardless.
- **Cabling.** Five units against four USB ports. Stages A to E each name what is plugged in. Stages D and E need an operator for a cable plug, which has no verb (hand step, reason: a physical plug is not a broadcast).
- **Pre-registered INCONCLUSIVE outcomes:**
  - an idle projected screen (V-runs need `fed` median of at least 25 fps; see reachability);
  - V5, if `capped=` in `[RES_CAP]` equals V2's (the unit's ceiling is already 1080p);
  - I5 or I3 landing fewer than 2 faults in an arm;
  - F4's phone-side count, if the baseline's ping control reads zero;
  - D-MOTO as phone (run M), if it cannot reach SSL in 3 attempts;
  - the dongle runs (D1, D2), if the baseline shows no late-`VERSION_RESPONSE` line in the first 5 plugs;
  - D-HP, if no phone's head unit server answers on 5277.
- **UNTESTABLE by design on this rig, covered on the JVM:** `TRUNCATED_RUN` and `ORPHANED_FRAGMENT` at the reader (no injector mode produces one at reader stage; mode 3 is assembler-stage and leaves the audit silent), a TLS 1.3 post-handshake record (Gearhead pins TLS 1.2), a hostile oversize total (the 8 MiB copy bound).

## 4. Settings keys this round needs

Write them with the app stopped (§1), using the multi-key writer for the unit (`set_hu_prefs.sh` on D-HU, `set_prefs_runas_host.py` on D-SAM, D-HP and phones; D-SAM has no `sed`). Force-stop again after the script if it relaunched the app, and read every key back before launching. Per run, delete first with both removal forms (§1), then write.

**Delete before every run** (latches and legacy keys): `native-aa-wireless`, `wifi-launcher-mode`, `video-profile-starvation-cap`, and all three `debug-video-fault-*` keys unless the run names them. `video-profile-starvation-cap` is a latch that forces 720p30 and AAC after three fumbled bring-ups; read it back as absent.

**Read and quote, never write:** `use-aac-audio`, `audio-latency-multiplier`, `audio-queue-capacity`, `view-mode`, `wifi-direct-band`. Quote `[RES_CAP] ... linkCapped=` for every wireless run.

| Key | Element |
|---|---|
| log level | `<int name="log-level" value="2" />` (INFO). **V4 only: `value="0"` (VERBOSE)**, the one run that needs `RECV:` |
| onboarding | `<int name="onboarding-version" value="2" />` |
| mode | `<int name="wifi-connection-mode" value="3" />` on stages A and B; `1` on stage C; `0` on stages D and E |
| codec | `<string name="video-codec">H.265</string>` or `H.264`, per run |
| software decode | `<boolean name="force-software-decoding" value="true" />` and `<int name="software-video-decoder" value="1" />` (V3 only); otherwise `value="false"` |
| resolution | `<int name="resolutionId" value="3" />` (1080p); V5 writes `5` |
| frame rate | `<int name="fps-limit" value="60" />` |
| USB path | `<boolean name="use-libusb" value="false" />` (standard) or `true` (libusb), stages D and E |
| orientation | `<int name="screen-orientation" value="2" />` (LANDSCAPE) on D-SAM and on a phone used as the head unit |
| injector | `<int name="debug-video-fault-injection" value="3" />` or `5`, `<int name="debug-video-fault-rate" value="N" />`, `<int name="debug-video-fault-budget" value="B" />` (I and C runs only) |

`ACTION_LOG_MARKER` is not gated on this candidate (`CONFIGURING` lists only the settings and log-capture verbs), so `allow-external-configuration` is not needed.

Per-run values:

| Run | mode | codec | software | resolutionId | log-level | injector |
|---|---|---|---|---|---|---|
| C3 | 3 | H.265 | no | 3 | 2 | mode 3, rate 100, budget 0 |
| C5 | 3 | H.265 | no | 3 | 2 | mode 5, rate 100, budget 0 |
| V1 | 3 | H.264 | no | 3 | 2 | off |
| V2, V2n | 3 | H.265 | no | 3 | 2 | off |
| V3 | 3 | H.265 | **yes** | 3 | 2 | off |
| V4 | 3 | H.265 | no | 3 | **0** | off |
| V5 | 3 | H.265 | no | **5** | 2 | off |
| I3 | 3 | H.265 | no | 3 | 2 | mode 3, rate from C3, budget 3 |
| I5 | 3 | H.265 | no | 3 | 2 | mode 5, rate from C5, budget 3 |
| K | 3 | H.265 | no | 3 | 2 | off |
| M | 3 | H.265 | no | 3 | 2 | off |
| O1 | 3 | H.264 | no | as found | 2 | off |
| O2 | 1 | H.264 | no | as found | 2 | off |
| U1, U2, U3, D1, D2 | 0 | H.265 | no | as found | 2 | off |

## 5. Helpers, and the extractor

Shell state does not persist between calls, so save the first block as `pr1045_helpers.sh` and the second as `pr1045_extract.py` in `hur-wifi-test-scripts/` (set `SCR` to that directory) and `source` the helpers at the top of every call. Each unit's serials go in `$X/env` once per stage:

```bash
# $X/env, one per stage (example: stage A)
HU=27870808938846     # D-HU. D-SAM 30041c35642d2200, D-HP CNU350BGBJ, D-POCO 4f4027e9, D-MOTO ZY22GC3BM4
PH=4f4027e9           # the phone (stage A: D-POCO)
```

```bash
# pr1045_helpers.sh
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
SCR=${SCR:-hur-wifi-test-scripts}
X=${X:-$HOME/pr1045-captures}; mkdir -p "$X"; [ -f "$X/env" ] && . "$X/env"

send()  { a=$1; shift; adb -s "$HU" shell am broadcast -f 0x00000020 -n "$RX" -a "com.andrerinas.openheadunit.$a" "$@"; }
mark()  { send ACTION_LOG_MARKER --es text "$1" >/dev/null; sleep 1; }
wait_for() { f=$1; pat=$2; t=${3:-120}; i=0; while [ $i -lt $t ]; do grep -aqF -e "$pat" "$f" && return 0; sleep 1; i=$((i+1)); done; return 1; }
hold()  { t=$1; while [ "$t" -gt 0 ]; do sleep 30; t=$((t-30)); done; }   # one call per hold of 540 s or less
extract() { python3 "$SCR/pr1045_extract.py" "$@"; }
readkey() { adb -s "$HU" shell run-as $PKG cat shared_prefs/settings.xml | grep -o "name=\"$1\"[^/]*"; }

cap_start() {   # cap_start RID  (clears both buffers, then starts both captures, detached)
  adb -s "$HU" logcat -G 16M; adb -s "$HU" logcat -c; adb -s "$PH" logcat -c
  setsid nohup stdbuf -oL adb -s "$HU" logcat -v time "${LCF[@]}" > "$X/$1.hu.logcat" 2>&1 &
  [ "$PH" != "$HU" ] && setsid nohup stdbuf -oL adb -s "$PH" logcat -v time > "$X/$1.ph.logcat" 2>&1 &
  sleep 1
}   # LCF is empty except on a phone used as the head unit, where $X/env sets LCF=(OPENHU:V '*:S') because the ROM's own spam rolls the buffer
cap_stop() { pkill -f "adb -s $HU logcat -v time"; pkill -f "adb -s $PH logcat -v time"; sleep 1; }

phone_off() { o=$(adb -s "$PH" shell cmd connectivity airplane-mode enable 2>&1)
              echo "$o" | grep -qiE 'error|exception|unknown|not found' && { adb -s "$PH" shell svc bluetooth disable; adb -s "$PH" shell svc wifi disable; echo AIRPLANE-FALLBACK; }
              sleep 6; }   # a fallback is a Setup-notes line
phone_on()  { adb -s "$PH" shell cmd connectivity airplane-mode disable; sleep 4
              adb -s "$PH" shell svc wifi enable; sleep 2; adb -s "$PH" shell svc bluetooth enable; sleep 6; }
              # verify with dumpsys wifi and dumpsys bluetooth_manager, never settings get global

# Gestures into the projected video are the template's standing exception to the no-input rule (section 7a): they reach the
# phone over the touch channel and never touch the app's own UI. D-HU is 1440x720. Sequential only: the loop is the only
# adb caller on the unit while it runs, so send no other adb command to that unit until swipe_stop prints swipe-stopped.
swipe_start() {
  setsid nohup bash -c ": pr1045swipe; while :; do adb -s $HU shell input swipe 500 400 1000 300 350; sleep 0.8; adb -s $HU shell input swipe 1000 300 500 400 350; sleep 0.8; done" >/dev/null 2>&1 & }
swipe_light() {  # soak: one pair every 30 s
  setsid nohup bash -c ": pr1045swipe; while :; do adb -s $HU shell input swipe 500 400 1000 300 350; sleep 0.8; adb -s $HU shell input swipe 1000 300 500 400 350; sleep 30; done" >/dev/null 2>&1 & }
swipe_stop() { pkill -f pr1045swipe; sleep 2; pkill -f "input swipe"; sleep 1; pgrep -f "pr1045swipe|input swipe" >/dev/null && echo "SWIPE-STILL-RUNNING" || echo "swipe-stopped"; }
count_of() { grep -acF -e "$2" "$1"; }   # count_of FILE PATTERN
wait_more() { f=$1; pat=$2; base=$3; t=${4:-60}; i=0; while [ $i -lt $t ]; do [ "$(grep -acF -e "$pat" "$f")" -gt "$base" ] && return 0; sleep 1; i=$((i+1)); done; return 1; }

ident() {   # ident cand|base : checks the APK actually installed on $HU
  p=$(adb -s "$HU" shell pm path $PKG | head -1 | cut -d: -f2 | tr -d '\r'); adb -s "$HU" pull "$p" "$X/live-$HU.apk" >/dev/null
  n=$(unzip -p "$X/live-$HU.apk" 'classes*.dex' | strings | grep -cF 'AapMessageReassembler'); md5sum "$X/live-$HU.apk"
  echo "reassembler-symbol-count=$n"
  case $1 in cand) [ "$n" -ge 1 ] && echo IDENT-OK || echo IDENT-WRONG;; base) [ "$n" -eq 0 ] && echo IDENT-OK || echo IDENT-WRONG;; esac
}

# begin_wireless RID [early] : settings already written and read back, app stopped. Native AA, clean-run protocol (template section 4).
# "early" is for injector runs: the start marker goes in the moment SSL completes, before any video, so no injected fault precedes the window.
begin_wireless() {
  phone_off; adb -s "$HU" shell am force-stop $PKG
  cap_start "$1"
  adb -s "$HU" shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity; sleep 18
  phone_on
  wait_for "$X/$1.hu.logcat" 'SSL handshake complete. ' 150 || { echo "BRINGUP-FAIL no SSL"; return 1; }
  [ "$2" = early ] && mark "$1-start"
  wait_for "$X/$1.hu.logcat" 'Throughput over' 40 || { send ACTION_RAISE_PROJECTION; wait_for "$X/$1.hu.logcat" 'Throughput over' 30 || { echo "BRINGUP-FAIL no picture"; return 1; }; }
  [ "$2" = early ] || sleep 30
}
# poll_faults FILE MAXSECONDS : returns 0 once 3 faults have been injected (then waits 60 s more), 1 if MAXSECONDS pass first
poll_faults() { f=$1; max=$2; t=0; while [ $t -lt $max ]; do n=$(count_of "$f" 'FAULT INJECTED (#'); [ "$n" -ge 3 ] && { sleep 60; return 0; }; sleep 30; t=$((t+30)); done; return 1; }
# finish RID : closes the window, ends the session, stops both captures
finish() { mark "$1-end"; sleep 2; send ACTION_DISCONNECT >/dev/null; sleep 6; cap_stop; adb -s "$HU" shell uptime; }
report() {  # report RID : writes and prints the extract for one run
  extract "$1" "$X/$1.hu.logcat" "$X/$1.ph.logcat" > "$X/$1.json"; cat "$X/$1.json"; }
```

```python
#!/usr/bin/env python3
# pr1045_extract.py RUN HU_CAPTURE [PHONE_CAPTURE]
"""Counts and timings for one run of the pr-1045-framing round. Prints one JSON block and no verdict.
HU_CAPTURE is sliced between the lines 'AutomationMarker: RUN-start' and 'AutomationMarker: RUN-end'.
PHONE_CAPTURE (optional) is counted whole: it is a per-run file."""
import json, re, statistics, sys
from datetime import datetime

run, hu = sys.argv[1], sys.argv[2]
phone = sys.argv[3] if len(sys.argv) > 3 else None

TS = re.compile(r'^(\d\d)-(\d\d) (\d\d):(\d\d):(\d\d)\.(\d+)')
def ts(line):
    m = TS.match(line)
    if not m:
        return None
    mo, d, h, mi, s, ms = m.groups()
    return datetime(2026, int(mo), int(d), int(h), int(mi), int(s), int(ms.ljust(6, '0')[:6])).timestamp()

def fmt(t):
    return None if t is None else datetime.fromtimestamp(t).strftime('%H:%M:%S.%f')[:-3]

with open(hu, 'rb') as f:
    lines = f.read().decode('latin-1').splitlines()

anomalies = []
a = next((i for i, l in enumerate(lines) if 'AutomationMarker: %s-start' % run in l), None)
b = None
for i in range(len(lines) - 1, -1, -1):
    if 'AutomationMarker: %s-end' % run in lines[i]:
        b = i
        break
if a is None or b is None or b <= a:
    anomalies.append('marker window not found, whole file used')
    a, b = 0, len(lines) - 1
win = lines[a:b + 1]

FIXED = [
 'AapVideo: discarding a', 'AapRead: invalid framing or TLS session', 'Incomplete AAP message',
 'AAP fragments exceed declared size', 'Invalid AAP message length', 'AAP message has no complete type',
 'AAP fragment routing changed', 'AAP reassembly budget exhausted', 'Invalid AAP frame length',
 'Error in processBulk', 'SSL Decrypt: no application data', 'AapRead: FIFO overflow',
 'AapRead: Handling error', 'AapRead: Error in read loop (ignored)', 'AapRead: Connection closed',
 'AapRead: Connection lost', 'AapRead: Fatal read error', 'AapRead: Magic Garbage',
 'AapVideo: Previous frame was truncated', 'AapMediaPlayback: Failed to parse',
 'AapTransport quitting', 'SSL handshake complete. ', 'Audio transport read channel=',
 'Audio transport send channel=', 'AapTransport: the video thread is', 'FAULT INJECTED (#',
 'cycling video focus', 'retaking video focus', 'holding the cycle until it settles',
 'no cycle available now', 'keyframe decoded - the picture is repaired', 'Media Start Request VIDEO',
 'SSL Handshake: discarded a late VERSION_RESPONSE', 'SSL Handshake: still receiving late VERSION_RESPONSEs',
 'SSL Handshake: Failed to read AAP header', 'SSL Handshake: Timed out after',
 # baseline spellings, zero on the candidate by construction
 'SSL Decrypt failed', 'Decrypted payload too short', 'unwrap produced no application data',
 'AapRead: Invalid message length', 'Failed to parse metadata (', 'fragment accounting established',
]
counts = {p: sum(1 for l in win if p in l) for p in FIXED}
for k in ('DELTA_CHANGED', 'TRUNCATED_RUN', 'ORPHANED_FRAGMENT'):
    counts['AapRead: %s on' % k] = sum(1 for l in win if ('AapRead: %s on ' % k) in l)
meta = re.compile(r'RECV: MUSIC_PLAYBACK .*type: 32771 flags: (\d+) ')
mf = [m.group(1) for m in (meta.search(l) for l in win) if m]
counts['RECV metadata 32771 first-fragment (flags 9)'] = mf.count('9')
counts['RECV metadata 32771 single (flags 11)'] = mf.count('11')
vf = {}
VF = re.compile(r'RECV: VIDEO .*flags: (\d+) ')
for l in win:
    m = VF.search(l)
    if m: vf[m.group(1)] = vf.get(m.group(1), 0) + 1
counts['RECV VIDEO flags histogram (11 single, 9 first, 8 middle, 10 last)'] = vf

THR = re.compile(r'Throughput over (\d+)ms: rendered=(\d+) \((\d+)fps\), fed=(\d+) \((\d+)fps\), dropped=(\d+), skipped=(\d+), concealed=(\d+), inputWait=(\d+)ms, enqueueWait=(\d+)ms, codec=([^\s,]+)')
rows = [m.groups() for m in (THR.search(l) for l in win) if m]
def pct(v, p):
    if not v: return None
    v = sorted(v); return v[min(len(v) - 1, int(round(p * (len(v) - 1))))]
thr = {'windows': len(rows)}
if rows:
    rf = [int(r[2]) for r in rows]; ff = [int(r[4]) for r in rows]; iw = [int(r[8]) for r in rows]
    run0 = best = 0
    for r in rf:
        run0 = run0 + 1 if r == 0 else 0
        best = max(best, run0)
    thr.update({'rendered_fps_median': statistics.median(rf), 'rendered_fps_p10': pct(rf, .1),
                'fed_fps_median': statistics.median(ff), 'dropped_sum': sum(int(r[5]) for r in rows),
                'skipped_sum': sum(int(r[6]) for r in rows), 'concealed_sum': sum(int(r[7]) for r in rows),
                'inputWait_mean_ms': round(statistics.mean(iw), 1), 'inputWait_max_ms': max(iw),
                'enqueueWait_max_ms': max(int(r[9]) for r in rows), 'longest_zero_rendered_run_windows': best,
                'windows_per_minute': round(len(rows) / max(1e-9, ((ts(win[-1]) or 0) - (ts(win[0]) or 0)) / 60), 2) if ts(win[0]) and ts(win[-1]) else None,
                'codecs': sorted(set(r[10] for r in rows))})
DSP = re.compile(r'transport dispatch over (\d+)ms: .*unread=(\d+)%, blocks=(\d+), longest=(\d+)ms on \S+, videoQueue=(\d+), videoShed=(\d+)')
dsp = [m.groups() for m in (DSP.search(l) for l in win) if m]
disp = {'lines': len(dsp)}
if dsp:
    disp.update({'videoQueue_max': max(int(d[4]) for d in dsp), 'videoShed_sum': sum(int(d[5]) for d in dsp),
                 'unread_pct_max': max(int(d[1]) for d in dsp), 'blocks_sum': sum(int(d[2]) for d in dsp)})
INB = re.compile(r'inbound rate over \d+ms: video=(\d+)kB/s \((\d+) msgs\), audio=(\d+)kB/s')
inb = [m.groups() for m in (INB.search(l) for l in win) if m]
inbound = {'lines': len(inb)}
if inb:
    inbound.update({'video_kBps_median': statistics.median(int(i[0]) for i in inb),
                    'video_msgs_per_window_median': statistics.median(int(i[1]) for i in inb)})

def first(pat, src):
    for l in src:
        m = re.search(pat, l)
        if m: return m.group(1)
    return None
FILE_PATS = ['SSL handshake complete. ', 'FAULT INJECTED (#', 'AapTransport quitting', 'createGroup SUCCESS', 'MATCH! Starting AapService',
             'AapRead: Magic Garbage detected in header', 'AutomationReceiver: ']
file_counts = {p: sum(1 for l in lines if p in l) for p in FILE_PATS}
ctx = {'maxUnacked': first(r'\(maxUnacked=(\d+)\)', lines), 'RES_CAP': first(r'(\[RES_CAP\] .*)$', lines),
       'sink_codec': first(r'Media Sink Setup Request: (\d+) on channel VIDEO', lines)}

INJ = re.compile(r'fault injection - \S+ 1-in-(\d+), (\d+) candidates seen, (\d+) injected')
inj = [(ts(l), m.groups()) for l in win for m in [INJ.search(l)] if m]
injector = {'summaries': len(inj)}
if inj:
    t_z, g_z = inj[-1]
    wsec = (ts(win[-1]) - ts(win[0])) if ts(win[0]) and ts(win[-1]) else None
    injector.update({'rate': int(g_z[0]), 'candidates_seen_last': int(g_z[1]), 'injected_last': int(g_z[2]),
                     'window_s': None if wsec is None else round(wsec, 1),
                     'candidates_per_s': None if not wsec else round(int(g_z[1]) / wsec, 3)})
fev = []
idx = [i for i, l in enumerate(win) if 'FAULT INJECTED (#' in l]
def nxt(pat, i, limit_s):
    t0 = ts(win[i])
    for j in range(i, len(win)):
        t = ts(win[j])
        if t is not None and t0 is not None and t - t0 > limit_s: return None
        if pat in win[j]: return j
    return None
for n, i in enumerate(idx):
    t0 = ts(win[i]); rec = {'n': n + 1, 'at': fmt(t0)}
    for key, pat, lim in (('truncated_ms', 'Previous frame was truncated', 90), ('discard_ms', 'AapVideo: discarding a', 90),
                          ('audit_ms', 'AapRead: DELTA_CHANGED on', 90), ('cycle_ms', 'cycling video focus', 90),
                          ('retake_ms', 'retaking video focus', 90), ('mediastart_ms', 'Media Start Request VIDEO', 90),
                          ('repair_ms', 'keyframe decoded - the picture is repaired', 90)):
        j = nxt(pat, i, lim)
        rec[key] = None if j is None or ts(win[j]) is None or t0 is None else round((ts(win[j]) - t0) * 1000)
    gap = None
    if n + 1 < len(idx) and ts(win[idx[n + 1]]) and t0: gap = round(ts(win[idx[n + 1]]) - t0, 1)
    rec['next_fault_gap_s'] = gap
    fev.append(rec)
retakes = []
for i, l in enumerate(win):
    if 'retaking video focus' in l:
        t0 = ts(l); j = nxt('keyframe decoded - the picture is repaired', i, 30)
        k = nxt('Media Start Request VIDEO', i, 30)
        mm = re.search(r'session=(\d+)', win[k]) if k is not None else None
        retakes.append({'at': fmt(t0), 'repair_ms': None if j is None else round((ts(win[j]) - t0) * 1000),
                        'mediastart_ms': None if k is None else round((ts(win[k]) - t0) * 1000),
                        'session': None if not mm else int(mm.group(1))})

out = {'run': run, 'hu_file': hu, 'window_lines': len(win), 'window_start': fmt(ts(win[0])) if win else None,
       'window_end': fmt(ts(win[-1])) if win else None, 'counts': counts, 'throughput': thr, 'dispatch': disp,
       'inbound': inbound, 'context': ctx, 'file_counts': file_counts, 'injector': injector, 'faults': fev,
       'focus_retakes': retakes, 'anomalies': anomalies}
if phone:
    with open(phone, 'rb') as f:
        pl = f.read().decode('latin-1').splitlines()
    P = ['Waiting for ack timeout, video frame dropped', 'Received out of order ping response',
         'Got mismatch session id in ack', 'VIDEO_ACK_TIMEOUT', 'Critical error']
    out['phone'] = {'file': phone, 'lines': len(pl), 'first_ts': fmt(ts(pl[0])) if pl else None,
                    'last_ts': fmt(ts(pl[-1])) if pl else None, 'counts': {p: sum(1 for l in pl if p in l) for p in P},
                    'gearhead_lines': sum(1 for l in pl if 'CAR.' in l or 'GH.' in l)}
print(json.dumps(out, indent=1))
```

**Every run ends with `report RID` and returns the JSON block plus the commands it ran.** Counts are anchored to the run's own markers (`<RID>-start`, `<RID>-end`), never to a whole-file grep. A run whose marker line is missing from its capture never landed and is void. The extractor was exercised on a synthetic capture before this brief was written; if it throws on a real capture, record the traceback in Setup notes and fall back to the greps in §6 on the same marker window.

**Contamination (template section 4), checked from `file_counts` of every wireless run:** `MATCH! Starting AapService` above 1, a second `createGroup SUCCESS` (D-SAM prints two before SSL, see its quirk file), `AapRead: Magic Garbage detected in header` above 0, or a second `SSL handshake complete. ` voids the run. Re-run it up to twice, then INCONCLUSIVE.

## 6. The lines that decide every run

Each line below was checked with `grep -rnF` against the candidate source at `dfb0d6f9` and, where the table says so, shown absent from the baseline. Format specifiers appear as in source. A line is graded on its printed form, which §6b maps.

### 6a. Lines present on both builds

```
Throughput over %dms: rendered=N (Nfps), fed=N (Nfps), dropped=, skipped=, concealed=, inputWait=Nms, enqueueWait=Nms, codec=, presented=   INFO, VideoDecoder.kt
AapTransport: transport dispatch over ..ms: ... unread=N%, blocks=N, longest=, videoQueue=N, videoShed=N                                   INFO, every 5 s, TransportDispatchMonitor.kt
inbound rate over ..ms: video=NkB/s (N msgs), audio=NkB/s ...                                                                               INFO, InboundRateMonitor.kt
Config response: %s (maxUnacked=%d)                      INFO, AapControl.kt: 12 wireless hardware, 6 wireless software HEVC, 16 or 8 on USB
Media Sink Setup Request: %d on channel %s                INFO: 3 = H.264, 7 = H.265 (the phone's own request)
Media Start Request %s: session=%d, config_index=%d       INFO, AapControl.kt
[RES_CAP] resolutionId=... capped=... linkCapped=...      INFO, HeadUnitScreenConfig.kt
FAULT INJECTION IS ON / FAULT INJECTED (#%d of %d candidates): %s on flag %d, len=%d / fault injection - <mode> 1-in-N, N candidates seen, N injected, budget n/n / fault injection budget spent after %d faults - the stream is clean from here     WARN, VideoFaultReporter.kt and AapReadMultipleMessages.kt
AapVideo: Previous frame was truncated! Resetting assembly state.
AapVideo: reassembly anomalies over %dms: truncated=%d, orphan=%d, headless=%d, overflow=%d
AapVideo: discarding a %d-byte access unit the framing audit found short                                                                    WARN
AapTransport: picture unrepaired for %dms - cycling video focus (%d/3)
AapTransport: retaking video focus to complete the keyframe cycle
AapTransport: picture unrepaired for ... holding the cycle until it settles / no cycle available now
VideoDecoder: keyframe decoded - the picture is repaired
AapTransport quitting (clean=...)                         INFO: the session-end line
SSL handshake complete. Session id: / No session id       INFO, AapSslContext.kt (the DEBUG "Handshake: SSL handshake complete" is absent at INFO, so never grep that one)
SSL Handshake: discarded a late VERSION_RESPONSE (answer to a retried version request)   INFO
SSL Handshake: still receiving late VERSION_RESPONSEs at the deadline                    ERROR
AapRead: Connection closed / AapRead: Connection lost. Stopping read loop. / AapRead: Fatal read error / AapRead: Error in read loop (ignored)
AapRead: FIFO overflow!                                   WARN: a log line on the baseline, a disconnect on the candidate
AutomationMarker: <label> (WARN)    AutomationReceiver: <action> (INFO)
createGroup SUCCESS    MATCH! Starting AapService
```

### 6b. Lines whose spelling changes on the candidate

| Baseline prints | Candidate prints | Meaning on the candidate |
|---|---|---|
| `Decrypted payload too short: N` (carry on) | removed; a zero-byte FIRST+LAST frame throws `AAP message has no complete type` | session ends |
| `SSL Decrypt failed` (a storm until EOF) | `AapRead: invalid framing or TLS session` once (socket reader), or `AapRead: Error in processBulk: <message>` (USB reader) | session ends at once |
| `SSL Decrypt: unwrap produced no application data` | `SSL Decrypt: no application data after consuming N bytes` (WARN, throttled) | a zero-produce record; harmless alone |
| `AapRead: Invalid message length (N). Resetting FIFO.` | `AapRead: Error in processBulk: Invalid AAP frame length N` | session ends |
| `Failed to parse metadata (fragmented)` / `(single packet)` | `AapMediaPlayback: Failed to parse metadata: <message>` | one message skipped |
| `AapRead: fragment accounting established for ...` | removed, no replacement | |
| none | `Incomplete AAP message on channel N`, `AAP fragments exceed declared size on channel N`, `Invalid AAP message length N on channel N`, `AAP message has no complete type`, `AAP fragment routing changed on channel N`, `AAP reassembly budget exhausted` | each an `IOException`, so the session ends |
| none | `Audio transport read channel=...` and `Audio transport send channel=...` (WARN, at most one per second each) | new diagnostics; they fire for any read of 50 ms or more once an audio sink has started, video reads included |

The audit line is printed from the format `AapRead: %s on %s - %s%s`, so its printed forms are `AapRead: DELTA_CHANGED on VIDEO - channel=2 fragments=N declaredTotal=N observed=N delta=N`, `AapRead: TRUNCATED_RUN on ...` and `AapRead: ORPHANED_FRAGMENT on ...`. It is throttled (`... (and N more since the last report)`), so a count of printed audit lines undercounts faults. Counting `AapVideo: discarding a` is not throttled.

### 6c. Phone-side lines (Gearhead), not in our tree

Read from the decompiled sources of Gearhead 17.5.663204 (`jdm.java`, `jqm.java`, `jdk.java`) and 17.8.163744 (`jdz.java`, `jrz.java`), not yet seen in a live capture:

```
Waiting for ack timeout, video frame dropped              (the 300 ms wait on the permit semaphore; the frame is still sent)
Received out of order ping response, received: %d, in queue: %d      (rate-limited by Gearhead to one per 10 s)
Got mismatch session id in ack. Expected: %d, got: %d, ch:%d
VIDEO_ACK_TIMEOUT    (a code identifier after 400 consecutive timeouts; not confirmed as a printed string)
Critical error       (CAR.SERVICE, a session-ending refusal)
```

Capture the phone with `adb -s $PH logcat -v time` (the helpers do) and grep the whole file with `-a`. Quote the phone's Gearhead version in Setup notes: `adb -s $PH shell dumpsys package com.google.android.projection.gearhead | grep versionName`. Gearhead's strings drift between builds, so a zero without the ping control means nothing.

## 7. Runs

Order inside each stage: baseline sweep, then candidate sweep, so each unit is installed exactly twice. Every long hold is split into calls of 540 s or less (`hold 540`, repeated), and every run is bracketed by `mark RID-start` and `finish RID`. Between runs, follow the §3a reset, which maps onto the helpers like this: `finish` ends the session (`ACTION_DISCONNECT`, markers, captures stopped); then `adb -s $HU shell am start -a android.intent.action.VIEW -d "headunit://exit"`, `sleep 3`, `adb -s $HU shell am force-stop $PKG`; then restore the unit's own `settings.xml` backup taken at the start of the stage (`settings-backup.xml`, pushed back with the unit's writer and `chown`ed to the app uid on D-HU as root, per its quirk file, then read back with `readkey`); then write only the next run's keys. `phone_off` at the head of `begin_wireless` is the phone half of the reset. After an APK swap the backup is restored before anything else. Escalate only for a failed build gate, a rig that will not boot, or an operator hand step that cannot be done.

Reachability numbers are asked for beside every count, so a green that proves nothing is recognisable. The conditions the change must be shown to have been exercised under are stated per run.

### R0. Gate (both trees)

1. `./gradlew :app:testGithubDebugUnitTest` through `run_unit_tests.sh` on the candidate: **2673 tests, 0 failures**. On the baseline: **2658, 0**. Any other count means the wrong tree was built; stop and say so.
2. `AapMessageReassemblerTest`, `AapReadPlaintextAuditTest`, `TlsUnwrapLoopTest` and `VideoTransportBoundaryTest` exist and pass on the candidate and do not exist on the baseline.
3. Build both APKs, copy them out, record both md5s. The two md5s differ.
4. After the first install of each on a unit: `ident cand` prints `IDENT-OK` for the candidate, `ident base` prints `IDENT-OK` for the baseline. `send ACTION_QUERY_STATE` once per install and quote the `commit` it returns (informational).

**PASS:** all four. **FAIL:** a red test, a wrong count, a wrong symbol. A build or test failure stops the round.

---

## Stage A. D-HU as head unit, D-POCO as phone, Native AA (the main stage)

**Cabling:** D-HU and D-POCO on adb (2 ports). **Before the stage:** D-POCO on its home screen with the screen on (`adb -s $PH shell dumpsys window | grep mCurrentFocus`; `input keyevent KEYCODE_HOME` if not), its Bluetooth bonded to D-HU, no other phone's Bluetooth on (`dumpsys bluetooth_manager`, D-HU serves one hands-free link). Quote `adb -s $PH shell dumpsys package com.google.android.projection.gearhead | grep versionName`, the D-HU `stat` of `shared_prefs/` and `uptime`. If the capture shows `THROTTLE_LIMIT_EXCEEDED`, reboot the phone and restart from the interrupted run (a force-stop does not clear it). Diff `settings.xml` against a fresh backup and state the delta in Setup notes.

**Order:** install the baseline APK and run the whole B list, then install the candidate APK and run the whole C list.

B list: **C3-B, C5-B, V1-B, V2-B, V2n-B, V3-B, V4-B, V5-B, I3-B, I5-B, K-B, M-B.**
C list: **V1-C, V2-C, V3-C, V4-C, V5-C, I3-C, I5-C, K-C, M-C.** (C3 and C5 are calibration, baseline only; V2n is the noise run, baseline only.)

### F1. Throughput under one ack per complete DATA message

Same phone, same settings, the map moving, both builds. **Motion source:** `swipe_start` pans the projected map. If `fed` median stays under 25 fps with the swipe loop running, start navigation on the phone (phone-side adb, the phone is not the app under test):
`adb -s $PH shell am start -a android.intent.action.VIEW -d 'google.navigation:q=Times+Square+New+York&mode=d'`
and leave the swipe loop running.

#### V1. H.264, hardware decode, wireless (10 min)

```bash
source $SCR/pr1045_helpers.sh
# settings per section 4 (V1), written with the app stopped and read back
begin_wireless V1-B || echo "RERUN"
mark V1-B-start; swipe_start; hold 540
```
then in a second call: `hold 60; swipe_stop; finish V1-B; report V1-B`. Do the same with `V1-C` after the candidate install.

Reachability, both arms: `Media Sink Setup Request: 3 on channel VIDEO` (`context.sink_codec` is `3`), `context.maxUnacked` is `12`, `throughput.windows` at least 100, `throughput.fed_fps_median` at least 25. A run that misses any of these is void for F1: re-run once with the navigation step, then INCONCLUSIVE.

#### V2. H.265, hardware decode, wireless (10 min), and V2n the noise run

Same commands with `V2-B`, `V2n-B` (baseline, the same settings, immediately repeated) and `V2-C`. `sink_codec` is `7`, `maxUnacked` is `12`. **V2n measures run-to-run noise on the baseline.** If `V2-B` and `V2n-B` differ in `rendered_fps_median` by more than 10 percent, the noise exceeds the F1 threshold for this configuration: V2-C is INCONCLUSIVE for F1 (its zero-discard conditions are still graded).

#### V3. H.265, software decode (bundled FFmpeg), wireless, window 6 (10 min)

Settings per section 4 (V3). Reachability: `context.maxUnacked` is `6` on both arms (this is the 6-permit window the review flagged), `codecs` names the FFmpeg decoder, `fed_fps_median` at least 15 (software decode may run below hardware rates; if both arms read under 15, report and mark F1 INCONCLUSIVE for V3).

**D-HU abort rule.** Before the run: `uptime` and `adb -s $HU shell cat /sys/class/thermal/thermal_zone*/temp | head -3`. If D-HU reboots (`adb -s $HU wait-for-device` returns after a drop, or `uptime` after the run is shorter than the run), stop V3 for that arm, record the time, do not retry more than once, and mark the arm INCONCLUSIVE. Software HEVC is a state this unit has run in earlier threads; no spin loops or any other CPU stress, ever.

#### F1 verdict, for each of V1-C, V2-C, V3-C against its `-B` run (**the point of the round**)

**PASS**, all of:
1. `rendered_fps_median(C)` is at least 0.90 times `rendered_fps_median(B)`;
2. `rendered_fps_p10(C)` is at least 0.85 times `rendered_fps_p10(B)`;
3. `inputWait_mean_ms(C)` is at most the larger of 1.5 times `inputWait_mean_ms(B)` and `inputWait_mean_ms(B)` plus 150;
4. `dropped_sum(C)` is at most `dropped_sum(B)` plus 10 over the 10 minutes;
5. `dispatch.videoShed_sum(C)` is 0, or at most `videoShed_sum(B)`; and no `AapTransport: the video thread is` line on C that B did not also print;
6. `counts['AapTransport quitting']` is 0 in both windows;
7. phone side, when the baseline's `Received out of order ping response` count is at least 1 (the control is live): `phone.counts['Waiting for ack timeout, video frame dropped']` on C is at most 1.5 times B's plus 10, and `phone.counts['VIDEO_ACK_TIMEOUT']` is 0;
8. phone side, same condition: `Received out of order ping response` on C is at most 1 and below B's count (**expected: the candidate's pings vanish**, because it now echoes the phone's own timestamp).

**FAIL:** any of 1, 2, 3, 5, 6 violated, or `VIDEO_ACK_TIMEOUT` above 0, or the session ends.
**INCONCLUSIVE:** a reachability line missed, V2n noise above 10 percent, or the abort rule fired. If only 7 and 8 cannot be judged (the ping control read 0 on B), the HU-side verdict stands and the results file says the phone-side half is unconfirmed.

**If the change did nothing**, B and C would read the same numbers. Report both columns even on PASS: `rendered_fps_median`, `p10`, `inputWait_mean_ms`, `dropped_sum`, `videoQueue_max`, `videoShed_sum`, `inbound.video_kBps_median`, and the phone's two counts. The difference is the finding.

### F2. Zero healthy-stream discards

**Applies to every healthy run in this round**, so it is graded on V1, V2, V3, V4, V5, K, M, O1, O2, U1, U2, U3 (and D1, D2 where a session formed), not on a run of its own. On the **candidate**, inside the marker window, every one of these must read 0 in the extractor:

```
AapVideo: discarding a            AapRead: DELTA_CHANGED on         AapRead: TRUNCATED_RUN on         AapRead: ORPHANED_FRAGMENT on
AapRead: invalid framing or TLS session                  Incomplete AAP message           AAP fragments exceed declared size
Invalid AAP message length        AAP message has no complete type  AAP fragment routing changed     AAP reassembly budget exhausted
Invalid AAP frame length          Error in processBulk              SSL Decrypt: no application data  AapRead: FIFO overflow
AapRead: Handling error           AapRead: Error in read loop (ignored)   AapRead: Fatal read error   AapRead: Connection closed
AapRead: Connection lost          AapVideo: Previous frame was truncated  AapMediaPlayback: Failed to parse  AapTransport quitting
```

and `file_counts['SSL handshake complete. ']` is exactly 1 (on the multi-session USB captures it equals the number of sessions that formed). The baseline's equivalents (`SSL Decrypt failed`, `Decrypted payload too short`, `unwrap produced no application data`, `AapRead: Invalid message length`, `Failed to parse metadata (`) are recorded on the `-B` runs and not graded: a baseline count above zero on a healthy stream, with the candidate at zero, is an improvement worth stating.

**The session must not end.** Any `AapTransport quitting` inside a window, or an `SSL handshake complete. ` count above 1, is a FAIL of that run.

#### V4. Album art and 20 track skips, VERBOSE (7 min)

The only run at `log-level=0`. It also proves fragmentation was exercised, because RECV lines carry the flags. D-HU floods logcat at VERBOSE, so the capture is the host-side stream and the buffer is raised first (`cap_start` does it). Settings per section 4 (V4).

**Precondition (verify, never assume):** D-POCO has a media player with at least 25 tracks with embedded album art and holds the active media session. Check: `adb -s $PH shell dumpsys media_session | grep -E 'state=PlaybackState|metadata:'` shows the player, and after `adb -s $PH shell input keyevent KEYCODE_MEDIA_PLAY` it reads `state=3` (PLAYING). If it does not, start a track by hand on D-POCO (hand step, reason: choosing and starting a track in a phone app has no verb on the app under test) and say so.

```bash
source $SCR/pr1045_helpers.sh
begin_wireless V4-B || echo RERUN
adb -s $PH shell input keyevent KEYCODE_MEDIA_PLAY; sleep 10
mark V4-B-start
for i in $(seq 1 20); do                      # one sequential loop: skip, then pan the map, never two adb callers at once
  adb -s $HU shell input keyevent KEYCODE_MEDIA_NEXT; sleep 3
  adb -s $HU shell input swipe 500 400 1000 300 350; sleep 0.8; adb -s $HU shell input swipe 1000 300 500 400 350; sleep 5
done
sleep 60; finish V4-B; report V4-B
```

(`KEYCODE_MEDIA_NEXT` from the head unit is relayed to the phone by the app's media session. If after the first 3 skips `counts['RECV metadata 32771 ...']` is still 0 in the capture so far, repeat the loop with `adb -s $PH shell input keyevent KEYCODE_MEDIA_NEXT` and record which route worked.)

Reachability: `counts['RECV metadata 32771 first-fragment (flags 9)']` plus `counts['RECV metadata 32771 single (flags 11)']` is at least 15 (one metadata message per skip), and **the first-fragment count is at least 1** (album art large enough to fragment, which is what exercises the non-video reassembly copy). The video flags histogram has at least one `9` and one `10`. Without those two, V4's zero-discard result is INCONCLUSIVE for the fragmented-metadata path.

**PASS (candidate):** the F2 zero list reads 0, `AapMediaPlayback: Failed to parse` is 0, the session is still up at `V4-C-end`, and reachability holds. **FAIL:** any zero-list line above 0 or a session end.

#### V5. The highest resolution the unit supports (5 min)

Settings per section 4 (V5: `resolutionId` 5). Read `context.RES_CAP`: if `capped=` equals the one on V2, V5 is a duplicate of V2 and is INCONCLUSIVE (say what the ceiling is). Otherwise grade as V2 with the F2 zero list and F1 conditions 1, 3 and 6, with `fed_fps_median` at least 15 on both arms.

#### K. Soak, 30 minutes, any session end

H.265 hardware, `swipe_light`, INFO. Between `K-B-start` and `K-B-end`, in four calls of 450 s (`hold 450`), no other action. At the start and the end of the window record the app's memory on D-HU (run `swipe_stop` first and `swipe_light` again after the start reading, so no two adb callers overlap):

```bash
adb -s $HU shell dumpsys meminfo $PKG | grep -E '^ *TOTAL'
```

**PASS (candidate):** the F2 zero list reads 0; `AapTransport quitting` is 0; `file_counts['SSL handshake complete. ']` is 1; `throughput.longest_zero_rendered_run_windows` is at most 4 (no 20 s of black); `throughput.windows` at least 330; and the end-minus-start growth of `TOTAL PSS` on C is at most the baseline's growth plus 50 MB (reassembler buffers must not leak). **FAIL:** any of those missed. **INCONCLUSIVE:** the baseline soak ended on its own, or the unit rebooted (record `uptime`); a session end on the baseline is itself reported.

#### M. D-MOTO as the phone (10 min per build)

Same as V2 with D-MOTO as `PH` (`PH=ZY22GC3BM4` in `$X/env`), D-POCO's Bluetooth switched off (`adb -s 4f4027e9 shell svc bluetooth disable`, verified with `dumpsys bluetooth_manager`; D-HU serves one hands-free link) and D-MOTO unlocked. It can be PIN-locked and adb cannot pass a PIN, so read the lock state first:

```bash
adb -s ZY22GC3BM4 shell dumpsys window | grep -E 'mDreamingLockscreen|isKeyguardShowing|mShowingLockscreen'
```

If any of them reads `true`, or `adb -s ZY22GC3BM4 shell input keyevent KEYCODE_WAKEUP` does not clear it, run M as a hand step: the operator unlocks D-MOTO once, then the run proceeds. If nobody can unlock it, M is UNTESTABLE (record the three grep lines). D-MOTO is not rooted, so its phone-side `logcat` is still the shell's and works. Grade F1 as V2 and F2 on the window. **UNTESTABLE:** D-MOTO cannot be unlocked or never reaches SSL in 3 attempts (say which). Restore D-POCO's Bluetooth afterwards.

### F3. Injector parity (modes 3 and 5)

**What is being compared:** the damage and the repair for the same injected loss on both builds. Mode 3 (`DROP_LAST_FRAGMENT`) injects at the assembler, so it is downstream of the audit on both builds and the audit stays silent (that silence plus `Previous frame was truncated` is its signature). Mode 5 (`DROP_MIDDLE_FRAGMENT_IN_READER`) injects in the reader after decrypt and before the audit, so the audit reports `DELTA_CHANGED`. Do not read the candidate's new `TRUNCATED_RUN` behaviour here: it needs a reader-stage lost LAST, which no mode produces (JVM only).

**Candidate scarcity is the binding constraint.** The rate cannot be chosen in advance: the same setting has landed 30 faults in 114 s on one screen and 30 in 725 s on another, and rate 300 landed none in 11 minutes. So calibrate first, on the baseline, with the same screen state and the swipe loop running.

#### C3 and C5. Calibration, baseline only, no verdict (3 min each)

Mode 3 then mode 5, `rate=100`, `budget=0`, `swipe_start`. At the end read `injector.candidates_per_s` from `report`.

```bash
source $SCR/pr1045_helpers.sh
# settings per section 4 (C3), app stopped, read back
begin_wireless C3-B early || echo RERUN
swipe_start; hold 180; swipe_stop; finish C3-B; report C3-B
```

```
rate = round(75 x candidates_per_s), at least 2     # about one fault every 75 s
```

Do this separately for mode 3 (`rate3`) and for mode 5 (`rate5`). If `candidates_seen_last` is under 10 for a mode, use `rate=2` for it. Report both candidate rates and both chosen rates. **C3 and C5 are PASS when `injector.summaries` is at least 3 (the injector was live), INCONCLUSIVE otherwise.**

#### I3 and I5. Three faults, then clean (up to 15 min each, both builds)

Settings per section 4 (I3: mode 3, `rate3`, `budget=3`; I5: mode 5, `rate5`, `budget=3`). `swipe_start` for the whole run. The same rate on both builds. **Stop rule:** 60 s after the third fault, or 15 minutes after the start marker, whichever comes first.

```bash
source $SCR/pr1045_helpers.sh
begin_wireless I3-B early || echo RERUN
swipe_start; poll_faults $X/I3-B.hu.logcat 480 && echo DONE || echo CONTINUE
# if CONTINUE, a second call:   source $SCR/pr1045_helpers.sh; poll_faults $X/I3-B.hu.logcat 420
swipe_stop; finish I3-B; report I3-B
```

The start marker is sent the moment SSL completes, before any video, so no fault precedes the window. **Check:** `counts['FAULT INJECTED (#']` in the window must equal `file_counts['FAULT INJECTED (#']` for the file; if not, a fault landed before the marker and the results file says so. Faults closer than 60 s are inside the focus-cycle cooldown: do not count them as independent samples, report the gaps (`faults[].next_fault_gap_s`).

**Grade each `-C` run against its `-B` run, with N the faults that landed:**

- **INCONCLUSIVE** if N is under 2 on either arm.
- Mode 3, **PASS** needs all of: every candidate fault has `truncated_ms` of at most 200; `counts['AapRead: DELTA_CHANGED on']` is 0 in the window (the audit stays silent, the signature); every fault has a non-null `repair_ms`; the median candidate `repair_ms` is at most 1.5 times the median baseline `repair_ms` plus 1000; `AapTransport quitting` is 0.
- Mode 5, **PASS** needs all of: every candidate fault has a non-null `discard_ms` or `audit_ms` of at most 1500 (detection); every fault has a non-null `repair_ms`; the median candidate `repair_ms` is at most 1.5 times the baseline's plus 1000; `counts['AapRead: invalid framing or TLS session']` and `AapTransport quitting` are 0.
- **FAIL:** any fault with `repair_ms` null while the baseline's faults all repaired; a candidate `repair_ms` above 8000 where the baseline's are below 8000; a session end; or detection missing on a mode 5 fault.

**If the change did nothing**, the candidate's table would match the baseline's. Report both columns, one row per fault: `at`, `truncated_ms`, `discard_ms`, `audit_ms`, `cycle_ms`, `repair_ms`, and the paired `Throughput over` window (so a fast repair on a stream that was not running is not read as a pass). A prior round measured the baseline's mode 3 repair at about 2.7 s via the focus cycle; treat that as context, not a threshold.

### F4. Deferred ack across focus cycles

Graded from the **I3 captures** (every I3 fault drives a focus cycle, up to 3 per session). For each `focus_retakes[]` entry on I3-C:

- `repair_ms` (from `retaking video focus` to `keyframe decoded - the picture is repaired`) is non-null and at most 3000. **That is the "rendered within 3 s after the session turns over" check**: the picture is back, so the phone is acking and sending.
- `session` (from the next `Media Start Request VIDEO: session=N`) is, when present, greater than the previous retake's `session`. A retake with no `Media Start Request VIDEO` line within 30 s is reported, not failed (the phone does not always send one).
- The window's `throughput.longest_zero_rendered_run_windows` is at most 2.
- Phone side, when the ping control on I3-B is live: `phone.counts['Got mismatch session id in ack']` on I3-C is at most I3-B's plus 2, and `phone.counts['VIDEO_ACK_TIMEOUT']` is 0.

**PASS:** all four, over at least 2 retakes. **FAIL:** a retake with `repair_ms` null or above 3000 where the baseline's all repaired, the mismatch count above the limit, or a session end. **INCONCLUSIVE:** fewer than 2 retakes landed, or the phone-side half unconfirmed (state which). Report both builds' retake tables and mismatch counts side by side.

---

## Stage B. D-SAM (API 19) as head unit, D-POCO as phone, Native AA (F5, F2)

**Cabling:** D-SAM and D-POCO on adb. **D-SAM facts, read its quirk file first:** no `su`, no `sed`; the multi-key writer is `set_prefs_runas_host.py`; `am` rejects `-p` (the helper uses `-n` only); it cannot host an access point (WiFi Direct on 2.4 GHz is the transport); its USB port does not charge it, so charge it separately and quote `dumpsys battery`; mount it in native portrait with `screen-orientation=2`; `logcat -c` does not clear its buffer, which is why every count is marker-anchored; its P2P group name is random every session; a clean session prints two `createGroup SUCCESS` before SSL, which is not contamination here. Quote `adb -s 30041c35642d2200 shell date` against the host's `date`. If `am` rejects a flag the `send` helper uses (`-f`), record the exact error: with no `AutomationReceiver:` line the run is void. Do not use `set_hu_pref.sh` and do not use `install_and_launch.sh` to switch arms (it installs whatever is newest in `apks/`): `adb -s $HU install -r <named apk>` and `ident` every time.

Order: baseline sweep (`O1-B`), then candidate (`O1-C`).

#### O1. Native AA on API 19, 10 minutes

```bash
source $SCR/pr1045_helpers.sh      # $X/env: HU=30041c35642d2200  PH=4f4027e9
begin_wireless O1-B || echo RERUN
mark O1-B-start; hold 540
# next call
hold 60; finish O1-B; report O1-B
```

No swipe loop (the screen is small and the unit is weak; the point is TLS and framing). Record the codec, `[RES_CAP]` and `maxUnacked`.

**PASS (candidate):** `file_counts['SSL handshake complete. ']` is 1; the F2 zero list reads 0, in particular `SSL Decrypt: no application data`, `AAP message has no complete type` and `AapRead: invalid framing or TLS session` (**the zero-length or no-application-data disconnect**); `AapTransport quitting` is 0; `throughput.windows` at least 60; and, if `rendered_fps_median(B)` is at least 5, `rendered_fps_median(C)` is at least 0.85 times it.
**FAIL:** the candidate reaches SSL fewer times than the baseline in 3 attempts, any zero-list line above 0, or the session ends before `O1-C-end`. If the baseline's own `unwrap produced no application data` count is above 0 on this unit, say so: it is the S1 trigger actually present on old Android, and the candidate must still hold the session.
**INCONCLUSIVE:** the baseline itself never reaches SSL in 3 attempts (D-SAM's own quirks), or no video renders on either arm (the TLS conditions are still graded and the fps comparison is not).

---

## Stage C. D-HP (API 17) as head unit, Headunit Server only (F5, F2)

**Cabling:** D-HP on adb, plus the phone on the same LAN (stage C uses D-MOTO first, D-POCO's head unit server is known down). Read `rig-quirks/units/D-HP.md` first: `svc wifi disable` does not take the radio down; `svc wifi enable` can take 25 to 30 s (poll `dumpsys wifi` until `Wi-Fi is enabled`, never assume); the Settings screen cannot be reached by script (not needed); the host-side capture has died silently on long runs, so poll every 60 s that the capture process is alive and restart it with `>>` append if not.

**Precondition (verify):** the phone's Android Auto head unit server is on (a developer toggle, hand step, reason: a phone-side settings toggle) and answers on `:5277`:

```bash
PHIP=$(adb -s ZY22GC3BM4 shell ip -4 addr show wlan0 | grep -o 'inet [0-9.]*' | cut -d' ' -f2)
timeout 3 bash -c "echo > /dev/tcp/$PHIP/5277" && echo REACHABLE || echo DOWN
```

If DOWN for both phones, O2 is **UNTESTABLE** (name which were tried).

#### O2. Headunit Server on API 17, 10 minutes

Settings per section 4 (O2). Bring-up:

```bash
source $SCR/pr1045_helpers.sh      # $X/env: HU=CNU350BGBJ  PH=ZY22GC3BM4
adb -s $HU shell am force-stop $PKG; cap_start O2-B
adb -s $HU shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity; sleep 20
send ACTION_CONNECT --es ip $PHIP
wait_for $X/O2-B.hu.logcat 'SSL handshake complete. ' 90 || echo "BRINGUP-FAIL"
sleep 30; mark O2-B-start; hold 540
```
then `hold 60; finish O2-B; report O2-B`, and the same for `O2-C`. Conditions and verdicts as O1 (the F2 zero list on the candidate, SSL exactly once, no `AapTransport quitting`, fps at least 0.85 times the baseline's if that is at least 5). The phone-side counts are reported only.

---

## Stage D. USB, phone as the head unit (F6, F2)

**Cabling:** D-MOTO as the host (head unit), on wireless adb with its USB-C port free; D-POCO as the device, plugged into D-MOTO's port over OTG. Read §7b of the template first (two permission prompts per connect are structural, "always" is sticky and must not be ticked, capture the descriptor with `adb logcat -v time -s UsbHostManager:D` on a second reader that is tag-filtered `OPENHU:V '*:S'` for the app, and read `dumpsys usb` before the stage and put what it said in Setup notes). In `$X/env` set `HU=` to D-MOTO's wireless adb address, `PH=` to D-POCO's wireless adb address (its USB-C port carries the OTG cable, so it is on wireless adb; if it cannot be reached, set `PH=$HU`, which turns the phone capture off, and say so), and `LCF=(OPENHU:V '*:S')` so D-MOTO's capture is tag-filtered.

Mode 0 (no wireless stack to compete with USB). Two paths, both builds:

| Run | path | build |
|---|---|---|
| U1-B, U1-C | standard (`use-libusb` false) | baseline, candidate |
| U2-B, U2-C | libusb (`use-libusb` true) | baseline, candidate |
| U3-B, U3-C | standard, one 10-minute session with 20 track skips | baseline, candidate |

#### U1 and U2. Ten sessions per arm

Session 1 is a cold plug: **hand step, reason: a physical plug has no verb.** The operator plugs D-POCO into D-MOTO and the tester waits for `SSL handshake complete. `. Sessions 2 to 10 are scripted, with the phone left plugged in:

```bash
source $SCR/pr1045_helpers.sh
cap_start U1-C-all
for n in 2 3 4 5 6 7 8 9 10; do
  mark U1-C-s$n-start
  c0=$(count_of $X/U1-C-all.hu.logcat 'SSL handshake complete. ')
  send ACTION_DISCONNECT >/dev/null; sleep 9
  send ACTION_CONNECT >/dev/null                      # the USB check, user_requested
  wait_more $X/U1-C-all.hu.logcat 'SSL handshake complete. ' $c0 60 && echo "s$n SSL" || echo "s$n NO-SSL"
  sleep 45; mark U1-C-s$n-end
done
cap_stop
for n in 2 3 4 5 6 7 8 9 10; do extract U1-C-s$n $X/U1-C-all.hu.logcat; done
```

(`ACTION_DISCONNECT` followed by an 8 to 9 s wait clears the 5 s user-exit cooldown before the USB check.) Session 1 gets its own markers by the same pair. A session counts as formed when the `SSL handshake complete. ` count rises within 60 s of the verb (`wait_more`, which compares against the count taken before the verb, so an earlier session's line is not read as this one's).

**Grade per arm:** `S` is the number of sessions out of 10 that formed.
**PASS (candidate):** `S(C)` is at least `S(B)` and at least 9; in every formed session the F2 zero list reads 0, in particular `Error in processBulk` and `Invalid AAP frame length` (the USB reader's new disconnects) and `AapRead: FIFO overflow`; `context.maxUnacked` is 16 (hardware) or 8 (software) on every formed session; and where a `Throughput over` line exists, `rendered_fps_median` is at least 0.85 times the baseline arm's.
**FAIL:** `S(C)` below `S(B)`, or below 9 with `S(B)` at least 9, or any zero-list line above 0, or a formed session that ends inside its 45 s.
**INCONCLUSIVE:** `S(B)` is under 5 (the rig could not form USB sessions; say why: read `dumpsys usb`), or no `Throughput over` rendered window on any arm (the video half only).

**`SSL Handshake: discarded a late VERSION_RESPONSE`** is reported for both arms. It is a dongle shape and phones rarely print it, so a zero on both arms is expected and is reported as "not exercised", not as a pass; stage E is where it is graded.

#### U3. USB, one 10-minute session with 20 skips and album art

One session on the standard path (`use-libusb` false), INFO, graded by the F2 zero list plus `AapMediaPlayback: Failed to parse` 0. Player precondition as V4 (D-POCO has at least 25 art-bearing tracks and holds the media session). Cold plug is a hand step as in U1.

```bash
source $SCR/pr1045_helpers.sh      # $X/env as stage D
adb -s $PH shell input keyevent KEYCODE_MEDIA_PLAY; sleep 10
adb -s $PH shell dumpsys media_session | grep -E 'state=PlaybackState|metadata:' > $X/U3-meta-before.txt; cat $X/U3-meta-before.txt
mark U3-B-start
for i in $(seq 1 20); do adb -s $HU shell input keyevent KEYCODE_MEDIA_NEXT; sleep 15; done
mark U3-B-end-skips
adb -s $PH shell dumpsys media_session | grep -E 'state=PlaybackState|metadata:' > $X/U3-meta-after.txt; cat $X/U3-meta-after.txt
hold 240; finish U3-B; report U3-B
```

(Use `U3-C` on the candidate. Run the loop in two calls if it nears the 9 minute limit: skips 1 to 10, then 11 to 20.) Skips landed when the `metadata:` description in `U3-meta-after.txt` differs from `U3-meta-before.txt`. If it does not change, repeat the loop with `adb -s $PH shell input keyevent KEYCODE_MEDIA_NEXT` in place of the `$HU` call and say so. If still unchanged, U3 is INCONCLUSIVE for the metadata half; the zero list is still graded. **PASS (candidate)** and **FAIL** as U1.

---

## Stage E. USB with the wireless AA dongle (F6, `VERSION_RESPONSE` retry)

**Cabling:** D-POCO as the host (head unit) over OTG on wireless adb, the dongle (idle `18d1:4ee1`, accessory `18d1:2d00`), D-MOTO paired with the dongle and in reach. D-POCO's Bluetooth stays on for the whole stage. In `$X/env` set `HU=` D-POCO's wireless adb address, `PH=$HU` (no phone capture on this stage) and `LCF=(OPENHU:V '*:S')`. Mode 0. **Never touch the status pill**, kill every watcher as soon as its run ends. D-MOTO's WiFi cannot be held off; to make the dongle's phone unreachable, power D-MOTO off. All plugs are hand steps (reason: a physical plug has no verb).

| Run | path | plugs per arm |
|---|---|---|
| D1-B, D1-C | libusb | 10 cold plugs |
| D2-B, D2-C | standard | 5 cold plugs |

The operator is cued by a banner; the tester never plugs. Add to `$X/env` nothing else; use these in the call:

```bash
cue() {  # cue "TEXT" "log line to wait for" SECONDS : prints a banner, then waits for the line or the timeout
  echo; echo "=========== OPERATOR: $1 ==========="; echo; printf '\a'
  [ -n "$2" ] && { wait_more "$CUEFILE" "$2" "$CUEBASE" "${3:-120}" && echo "cue-met" || echo "cue-MISSED"; } || sleep "${3:-30}"
}
```

Per plug `n` of arm `D1-C` (use `D2-C` for the standard path and `-B` on the baseline):

```bash
source $SCR/pr1045_helpers.sh      # $X/env as stage E
CUEFILE=$X/D1-C-all.hu.logcat
[ "$n" = 1 ] && { adb -s $HU shell am force-stop $PKG; cap_start D1-C-all; adb -s $HU shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity; sleep 15; }
mark D1-C-p$n-start
CUEBASE=$(count_of $CUEFILE 'SSL handshake complete. ')
cue "PLUG the dongle into D-POCO now (plug $n)" 'SSL handshake complete. ' 120
mark D1-C-p$n-sslcheck
sleep 45
mark D1-C-p$n-end
cue "UNPLUG the dongle now (plug $n)" "" 20
sleep 15
```

`cue-MISSED` means no SSL within 120 s of the cue. Do not repeat the cue blindly: record `p$n NO-SSL`, ask the operator by banner once more (`cue "PLUG again, plug $n retry" ...`), and count that retry as the same plug; a second miss is `p$n NO-SSL` and the sequence moves on after the unplug cue. An unplug cue has no log line to wait for, so it is a fixed wait of 20 s. After the last plug: `cap_stop`, then `for n in ...; do extract D1-C-p$n $X/D1-C-all.hu.logcat; done`. If no operator answers any banner for the first plug within 5 minutes, D1 and D2 are UNTESTABLE (hand steps, reason: a physical plug has no verb).

**Grade:** per arm, count the plugs that printed `SSL Handshake: discarded a late VERSION_RESPONSE` and the plugs that printed `SSL Handshake: still receiving late VERSION_RESPONSEs at the deadline`. **PASS (candidate):** each count on C is at least the baseline's count over the same number of plugs, formed sessions on C are at least the baseline's, and every formed session's F2 zero list reads 0. **FAIL:** the candidate prints the late-response lines fewer times than the baseline did (the TLS rewrite touched the handshake reads) or forms fewer sessions. **INCONCLUSIVE:** the baseline's first 5 plugs print neither line (not exercised: stop the run for that path, say so). **UNTESTABLE:** no operator for the plugs.

---

### Final step, after the last run of the round

On every unit the round touched, delete and read back the latch, then restore the unit's `settings-backup.xml` and read it back:

```bash
source $SCR/pr1045_helpers.sh      # per unit, with its $X/env
adb -s $HU shell am force-stop $PKG
# delete video-profile-starvation-cap with both removal forms (template section 1), then:
readkey video-profile-starvation-cap || echo "video-profile-starvation-cap absent"
```

Quote the empty read-back in Setup notes. Restore D-POCO's Bluetooth and re-enable any radio the round switched off.

## 8. Do not re-run

Nothing from earlier threads. In particular do not re-run the wire-corruption escalation or media-gap rounds: their mechanisms are on both builds, and this round only compares the two. Do not chase the phone-side counts on a phone whose ping control read zero: report them as unconfirmed.

## 9. Report back

The numbers that decide whether this PR goes back to its author as it is or with changes:

1. **F1:** for V1, V2, V3, the baseline and candidate `rendered_fps_median`, `rendered_fps_p10`, `inputWait_mean_ms`, `dropped_sum`, `videoShed_sum` and the phone's `Waiting for ack timeout` and `Received out of order ping response` counts, with the V2n noise figure. The software-HEVC (window 6) row is the one that decides the narrower-window question.
2. **F2:** the zero-list totals per run, `SSL handshake complete. ` per run, and the session-end count (expected 0) across V1 to V5, K, M, O1, O2, U1 to U3.
3. **F3:** the per-fault table for modes 3 and 5, both builds, with the median `repair_ms` each, and the calibration figures (`candidates_per_s`, chosen rates).
4. **F4:** the retake table (`repair_ms`, `session`) and the `Got mismatch session id in ack` counts, both builds.
5. **F5 and F6:** O1 and O2 SSL and zero-disconnect results, U1, U2 `S(B)` and `S(C)`, and the dongle's late-`VERSION_RESPONSE` plug counts.
6. **The soak:** any session end in 30 minutes per build, and the PSS growth.

**Captures** go to the release `rig-evidence-pr-1045-framing`, created on this round (the template keeps one release per thread and one asset per round), as the asset `pr-1045-framing-round1-captures.zip`, with its sha256 in the results file:

```bash
zip -1 -r pr-1045-framing-round1-captures.zip $X
sha256sum pr-1045-framing-round1-captures.zip
gh release create rig-evidence-pr-1045-framing --repo o-jcardenass/open-headunit \
  --notes "pr-1045-framing rig captures, one asset per round" pr-1045-framing-round1-captures.zip
```

Every quoted line the verdicts rest on goes into the results file with its timestamp: the file has to stand on its own.

## 10. Results file skeleton

Name: `pr-1045-framing-round1-results.md`, following template section 7 exactly. The bold verdict sits alone on the line under each `## <RunId>` heading, and the round has its own `## Round verdict: <VERDICT>` line.

```markdown
# pr-1045-framing, round 1 results

**Candidate:** emotionbug/open-headunit pr/split-aap-framing @ dfb0d6f9       **Baseline:** main @ 7e9d813d
**APK md5:** candidate <md5> / baseline <md5>
**Unit:** <D-HU chipset, API, screen> / <phones with Gearhead versionName> / <D-SAM, D-HP, USB host pairing>
**Date:** <yyyy-mm-dd>

## Setup notes
Deviations, wrong keys, strings that did not match, scripts used (pr1045_helpers.sh, pr1045_extract.py added), settings.xml delta, `stat` of shared_prefs, D-SAM clock reading, dumpsys usb reading, the phone-side ping control result per unit.

## R0 - gate
**PASS|FAIL**  (test counts 2673/2658, identity symbol, md5s)

## C3, C5 - calibration
**PASS|INCONCLUSIVE**  candidates_per_s, chosen rate3, rate5

## V1 | V2 | V2n | V3 | V4 | V5 | I3 | I5 | K | M | O1 | O2 | U1 | U2 | U3 | D1 | D2
(one section each; each -C run graded against its -B run)
**PASS|FAIL|INCONCLUSIVE|UNTESTABLE**
- Settings written:
- Radio state and how set:
- Discard-rule check:
- Reachability numbers (fed fps median, windows, maxUnacked, sink codec, metadata count, fragments seen, faults landed):
- Baseline column / candidate column of every graded number:
- Decisive log lines, quoted with timestamps:

| measure | B | C |
|---|---|---|

## F-summary
| F | arm(s) | verdict | the deciding number |
|---|---|---|---|
| F1 | V1, V2, V3 | | |
| F2 | all healthy runs | | |
| F3 | I3, I5 | | |
| F4 | I3 | | |
| F5 | O1, O2 | | |
| F6 | U1, U2, D1, D2 | | |
| soak | K | | |

## Round verdict: <PASS|FAIL|INCONCLUSIVE>

## Anything the brief did not ask about
```

```decisive-strings-baseline
SSL Decrypt failed
Decrypted payload too short
SSL Decrypt: unwrap produced no application data
AapRead: Invalid message length
Failed to parse metadata (fragmented)
Failed to parse metadata (single packet)
AapRead: fragment accounting established
```

```decisive-strings-gearhead
Waiting for ack timeout, video frame dropped
Received out of order ping response, received: %d, in queue: %d
Got mismatch session id in ack. Expected: %d, got: %d, ch:%d
VIDEO_ACK_TIMEOUT
```

```decisive-strings
AapRead: %s on %s - %s%s
DELTA_CHANGED
TRUNCATED_RUN
ORPHANED_FRAGMENT
AapVideo: discarding a %d-byte access unit the framing audit found short
AapRead: invalid framing or TLS session
Incomplete AAP message on channel
AAP fragments exceed declared size on channel
Invalid AAP message length
AAP message has no complete type
AAP fragment routing changed
AAP reassembly budget exhausted
Invalid AAP frame length
AapRead: Error in processBulk:
SSL Decrypt: no application data after consuming
AapRead: FIFO overflow!
AapRead: Handling error
AapRead: Error in read loop (ignored)
AapRead: Fatal read error
AapRead: Connection closed
AapRead: Connection lost. Stopping read loop.
AapRead: Magic Garbage detected
AapVideo: Previous frame was truncated! Resetting assembly state.
AapVideo: reassembly anomalies over
AapMediaPlayback: Failed to parse metadata:
AapMediaPlayback: Failed to parse playback status
AapTransport quitting (clean=
SSL handshake complete. 
Audio transport read channel=
Audio transport send channel=
AapTransport: the video thread is
FAULT INJECTION IS ON
FAULT INJECTED (#%d of %d candidates): %s on flag %d, len=%d
fault injection - 
fault injection budget spent after %d faults - the stream is clean from here
AapTransport: picture unrepaired for
cycling video focus
AapTransport: retaking video focus to complete the keyframe cycle
holding the cycle until it settles
no cycle available now
VideoDecoder: keyframe decoded - the picture is repaired
Media Start Request %s: session=%d, config_index=%d
Media Sink Setup Request: %d on channel %s
Config response: %s (maxUnacked=%d)
Throughput over
inputWait=
transport dispatch over
videoQueue=
videoShed=
inbound rate over
[RES_CAP] resolutionId=
SSL Handshake: discarded a late VERSION_RESPONSE
SSL Handshake: still receiving late VERSION_RESPONSEs at the deadline
SSL Handshake: Failed to read AAP header
AutomationMarker: 
AutomationReceiver: 
RECV: %s
createGroup SUCCESS
MATCH! Starting AapService
AapMessageReassembler
TlsUnwrapLoop
```
