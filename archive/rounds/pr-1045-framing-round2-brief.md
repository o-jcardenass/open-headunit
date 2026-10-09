# pr-1045-framing, round 2 brief: the four follow-up commits on AAP reassembly, against `main`

## 1. Build and baseline

| Arm | Where | SHA | Gate |
|---|---|---|---|
| Candidate C | B with the PR head `9b8f83664cc5081f3d023ac699c0b5fe757334c6` (12 commits) merged in, built locally | tree `11f0bfc99564ab41c0fce09b99f7c348daf66691` | 2855 JVM tests, 0 failures |
| Baseline B | `main` (3.5.0-beta4) | commit `7102b4283666ffcc7802e49402735e3958cd51a0`, tree `e96aa72a2ff556c9d1a5733c07d69c1b183a1b61` | 2820 JVM tests, 0 failures |

**Both arms are on today's `main`.** The PR branch sits on `145a0c76`; C is that branch merged onto B, so the two arms differ by the PR alone and both carry what `main` shipped since. The merge has no conflict. History was rewritten since round 1: the author rebased the branch from `7e9d813d` onto `145a0c76` and force-pushed. `git range-diff` shows the first seven commits are patch-identical to the seven that round 1 tested at `dfb0d6f9`. Four commits are new.

```bash
git fetch https://github.com/andreknieriem/open-headunit.git main pull/1045/head
git cat-file -e 9b8f83664cc5081f3d023ac699c0b5fe757334c6 || echo "PR HEAD MISSING"
git checkout -B arm-B 7102b4283666ffcc7802e49402735e3958cd51a0
git rev-parse 'HEAD^{tree}'       # MUST print e96aa72a2ff556c9d1a5733c07d69c1b183a1b61
# candidate, after the baseline APK is copied out
git checkout -B arm-C 7102b4283666ffcc7802e49402735e3958cd51a0
git merge --no-edit 9b8f83664cc5081f3d023ac699c0b5fe757334c6
git rev-parse 'HEAD^{tree}'       # MUST print 11f0bfc99564ab41c0fce09b99f7c348daf66691
git rev-parse --short HEAD         # the local merge commit; record it, ACTION_QUERY_STATE reports it
```

**If `PR HEAD MISSING` prints, the merge conflicts, or a tree differs, stop and report.** The PR then moved, and this brief did not check the new code. The merge commit stays local: do not push it. **B may be reused:** the main-smoke round 1 built `7102b428` as md5 `62ccbb85dfed879a1151dea9610df817` with 2820 tests. If that APK is still on disk with that md5, reuse it and say so in Setup notes.

Build each tree with `build_hur.sh` and `--max-workers=2` after the host cools to 70C. Copy each APK out of `apks/` into the round's folder the moment it is built, because the script deletes the previous one. Name them `base-7102b428.apk` and `cand-9b8f8366-on-7102b428.apk`. Record both md5s from a real `adb pull` plus a local `md5sum`.

**Identity is a DEX symbol plus the reported commit.** `onDroppedMediaData` exists only on the candidate: it came with `dced3994`, so it also separates this candidate from round 1's `dfb0d6f9`. `ident2` in §5 checks it on the pulled APK and prints the `commit` that `ACTION_QUERY_STATE` returns. Install with `adb install -r -d <named apk>` only. Read every settings key back after every install.

| Commit | New since round 1 | What it changes |
|---|---|---|
| `be25fc99` to `bbf68ac5` | no, patch-identical to round 1's `021d9a48` to `dfb0d6f9` | ping echo, TLS unwrap progress, reassembler, audit, diagnostics |
| `fa8b2beb` | yes | a copied message (control, audio, metadata, short-FIRST video) grows its buffer as bytes arrive, no longer reserves the declared total up front |
| `dced3994` | yes | a FIRST or COMPLETE frame that decrypts to zero bytes is skipped; a length or budget error drops one message, not the session; a handler exception drops one message; a dropped media DATA returns its credit; the reassembler now runs before the audit |
| `599f2fb7` | yes | the `Audio transport read/send` diagnostics record audio channels (4, 5, 6) only |
| `6f707ce2` | yes | a discarded DATA run that a new FIRST replaces before its LAST still returns its credit, exactly once |
| `9b8f8366` | yes | a valid fragmented DATA run that a new FIRST or COMPLETE abandons before its LAST also returns its credit, exactly once; healthy runs still ack in their handlers |

The base moved from round 1's `7e9d813d` to `7102b428` (3.5.0-beta4). None of the `main` commits in between touches the reader, the reassembler, TLS or the audit. Both arms carry them, so round 1 numbers are context here, not a comparison.

## 2. What this is and why

This PR moves AAP message reassembly into one per-channel reassembler in the readers, audits fragment totals on plaintext, and rewrites the TLS unwrap loop. Round 1 compared it with `main` on five units for two days and found no regression (results file `pr-1045-framing-round1-results.md`). F1 to F5, O1, O2, U2, U3 and the soak passed. U1 failed in the same way on the baseline, the candidate and an older commit. D1 and D2 were not exercised.

Our report asked for two robustness changes and some small items. The author answered with the four commits above. Each ask that ended a session in round 1's candidate now drops one message instead. A dropped media DATA message must still return its sender credit, or the phone's window shrinks by one permit for each drop. On video that ends in a stall after `max_unacked` drops (12 on this rig's wireless runs). On audio it ends in silence.

So this round answers three questions on hardware:

1. **Does the new copy path carry large fragmented messages?** Album art metadata is the largest copied message on a real stream. `fa8b2beb` changed how its buffer is sized, and `dced3994` changed what happens when a copy goes wrong.
2. **Is there a regression under load?** A longer soak, wireless and USB, with heavy video, music and track changes.
3. **Do media credits leak?** No video or audio stall may follow a dropped message, and the frame rate must not decay over a long session.

## 3. What is different about this round

- **A/B on two units, as in round 1.** Each unit gets a baseline sweep, then a candidate sweep, so each unit sees two installs. Run ids end `-B` or `-C`. A `-C` run is graded against its paired `-B` run. A `-B` run is PASS when its reachability conditions hold and its window is complete, INCONCLUSIVE otherwise.
- **Two stages only.** Stage A is D-HU with D-POCO over Native AA. Stage D is USB with D-MOTO as the host and D-POCO as the device. D-SAM, D-HP and the dongle are not used (§8 says why).
- **No reassembler drop is expected on a healthy stream.** The new drop lines print as `AapRead: skipped message: <reason> on channel N (suppressed=N)`. On today's Gearhead every count should be 0. The credit check (CR, §7) then reads "not exercised" for reassembler drops. This is pre-registered and is not a FAIL.
- **No fault injector reaches a reassembler drop.** Mode 5 drops a middle fragment of a video run in the reader. A video run whose FIRST carries 15 bytes or more streams straight through the reassembler, so the reassembler never sees the hole. The audit and `AapVideo` handle it, which is what I5 measures. A copied run needs a malformed stream, a TLS 1.3 record or a 16 MiB backlog, and the rig can produce none of them. The JVM tests cover those cases (`AapMessageReassemblerTest`, `AapReadPlaintextAuditTest`: for example `replacement FIRST retires dropped media DATA exactly once before delivery` and `budget discard returns DATA credit and replacement never inherits its identity`).
- **The phone's ping line is again the positive control for the phone capture.** The baseline answers pings with `System.nanoTime()`, so on wireless Gearhead prints `Received out of order ping response` about once per 10 s. Round 1 measured 34 to 63 per run on the baseline and 0 on the candidate, on Gearhead 17.9.664004. If a wireless `-B` run shows 0, the phone capture failed for that run, and its phone-side conditions are INCONCLUSIVE.
- **The rig's audio settings are a deliberate worst case.** Read `use-aac-audio`, `audio-latency-multiplier` and `audio-queue-capacity` back and quote them. Never write or reset them. Round 1 read `false / 8 / 20` on D-HU. Read every audio number as the thin arm.
- **The head unit's own media session is the parse proof at INFO.** With `sync-media-session-aa-metadata` on, the app copies each parsed metadata message into its own `MediaSession`. If D-HU's session title matches the phone's playing title after the last skip, the last reassembled metadata message parsed. This needs no VERBOSE log.
- **The phone logs `Critical error` lines at every session end we cause, on every build.** Gearhead prints 1 to 5 (`reason:1`, `io error`, `Failed to read message`) when the head unit ends a session, on `main` too, over USB and sockets (`pr-1065-reconnect-timers` and `pr-1066-byebye-priority` round 1, 2026-10-06). The phone counts cover the whole phone capture, which includes the disconnect in `finish`. So no phone condition asks for 0: C may have at most B's count plus 1. A `Critical error` line that names a protocol refusal (for example `Multiple media configs received`) is a FAIL on C whatever B shows; quote it.
- **Do not shorten the soaks.** Round 1 cut its runs at the operator's request. If a soak must be cut, cut both arms to the same length, at least 10 minutes, and say so in Setup notes.
- **Pre-registered INCONCLUSIVE outcomes:**
  - CR for reassembler drops, when no candidate run prints `AapRead: skipped message:` (expected);
  - A1's art path, if no metadata run spans 3 or more frames on the candidate;
  - the title check, if the baseline arm's titles also disagree (the setting did not take);
  - the audio half of A2 and U2S, if the baseline's inbound audio is non-zero in fewer than 80 percent of its windows (music did not play);
  - U1, if a gadget reset on D-POCO does not re-enumerate it (P-U below);
  - any phone-side condition on a wireless run whose `-B` ping control reads 0.

## 4. Settings keys this round needs

Write keys with the app stopped (template §1). On D-HU use round 1's `pr1045_reset.sh` to restore the stage backup as root, then write the run's keys with `set_hu_prefs.sh` and force-stop again if it relaunched the app. On D-MOTO use `set_prefs_runas_host.py` as round 1 Stage D did. Read every key back with `readkey` before each launch.

**Delete before every run:** `native-aa-wireless`, `wifi-launcher-mode`, `video-profile-starvation-cap`, and the three `debug-video-fault-*` keys unless the run names them. Read `video-profile-starvation-cap` back as absent.

**Read and quote, never write:** `use-aac-audio`, `audio-latency-multiplier`, `audio-queue-capacity`, `view-mode`, `wifi-direct-band`. Quote `[RES_CAP] ... linkCapped=` and `Config response: ... (maxUnacked=N)` for every run.

**Read, and correct only if wrong:** `enable-audio-sink` must read `true`; if not, write `true` and say so. `playback-focus-self-defeating` must read `false` or be absent; if `true`, write `false` and say so.

| Key | Element |
|---|---|
| log level | `<int name="log-level" value="2" />` (INFO). **A1 only: `value="0"`** (VERBOSE), the one run that needs `RECV:` |
| onboarding | `<int name="onboarding-version" value="2" />` |
| mode | `<int name="wifi-connection-mode" value="3" />` on stage A; `value="0"` on stage D |
| connection modes | stage D only: the `connection-modes` set holds `usb` (round 1 had to write it on D-MOTO) |
| codec | `<string name="video-codec">H.265</string>` |
| decoder | `<boolean name="force-software-decoding" value="false" />` |
| resolution | `<int name="resolutionId" value="3" />` (1080p) on stage A; as found on stage D, quoted |
| frame rate | `<int name="fps-limit" value="60" />` on stage A; as found on stage D, quoted |
| media session sync | `<boolean name="sync-media-session-aa-metadata" value="true" />`, every run |
| USB path | stage D: `<boolean name="use-libusb" value="false" />` for U1, `value="true"` for U2S |
| orientation | stage D: `<int name="screen-orientation" value="2" />` (LANDSCAPE) |
| injector | I5 only: `<int name="debug-video-fault-injection" value="5" />`, `<int name="debug-video-fault-rate" value="100" />`, `<int name="debug-video-fault-budget" value="1" />` |

`ACTION_LOG_MARKER` is not gated on either arm (`AutomationCommandPolicy.CONFIGURING` lists only the settings and log verbs), so `allow-external-configuration` is not needed.

## 5. Helpers and the extractors

Round 1 left `pr1045_helpers.sh`, `pr1045_extract.py` and `pr1045_reset.sh` in `hur-wifi-test-scripts/`, with the fixes from its Setup note 3. Keep them. If one is missing, rebuild it from `pr-1045-framing-round1-brief.md` §5 and apply that Setup note. Save the two blocks below beside them as `pr1045r2_helpers.sh` and `pr1045r2_extract.py`. Source the helpers at the top of every call, because shell state does not persist between calls.

Set `SCR` to the scripts directory. Captures go to a new folder, `$HOME/pr1045r2-captures`. Each stage writes its serials into `$X/env` once:

```bash
# $X/env, stage A
HU=27870808938846     # D-HU
PH=4f4027e9           # D-POCO
# $X/env, stage D
# HU=<D-MOTO wireless adb address>   PH=<D-POCO wireless adb address>   LCF=(OPENHU:V '*:S')
```

```bash
# pr1045r2_helpers.sh : round 2 additions. Source at the top of every call.
SCR=${SCR:-hur-wifi-test-scripts}
X=${X:-$HOME/pr1045r2-captures}
. "$SCR/pr1045_helpers.sh"      # round 1: send, mark, wait_for, wait_more, hold, readkey, cap_start, cap_stop,
                                 # phone_off, phone_on, begin_wireless, finish, report, count_of
OUT=$X
report2() { python3 "$SCR/pr1045r2_extract.py" "$1" "$X/$1.hu.logcat" > "$X/$1.r2.json"; cat "$X/$1.r2.json"; }
both()    { report "$1"; report2 "$1"; }      # every run ends with this

ident2() {   # ident2 cand|base : the APK installed on $HU, and the commit it reports
  p=$(adb -s "$HU" shell pm path $PKG | head -1 | cut -d: -f2 | tr -d '\r'); adb -s "$HU" pull "$p" "$X/live.apk" >/dev/null
  md5sum "$X/live.apk"
  n=$(unzip -p "$X/live.apk" 'classes*.dex' | strings | grep -cF 'onDroppedMediaData'); echo "drop-credit-symbol-count=$n"
  send ACTION_QUERY_STATE | grep -o 'commit[^,]*'
  case $1 in cand) [ "$n" -ge 1 ] && echo IDENT-OK || echo IDENT-WRONG;; base) [ "$n" -eq 0 ] && echo IDENT-OK || echo IDENT-WRONG;; esac
}

# bw2 RID : Native AA bring-up with the start marker BEFORE the phone comes on, so a fault in the first second is inside the window
bw2() {
  phone_off; adb -s "$HU" shell am force-stop $PKG
  cap_start "$1"
  adb -s "$HU" shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity; sleep 18
  mark "$1-start"; phone_on
  wait_for "$X/$1.hu.logcat" 'SSL handshake complete. ' 150 || { echo "BRINGUP-FAIL no SSL"; return 1; }
  wait_for "$X/$1.hu.logcat" 'Throughput over' 40 || { send ACTION_RAISE_PROJECTION; wait_for "$X/$1.hu.logcat" 'Throughput over' 30 || { echo "BRINGUP-FAIL no picture"; return 1; }; }
}

# soak_start MODE SKIPDEV EVERY RID : one background loop, and the only adb caller on $HU while it runs.
# Every 15 s: MODE swipe pans the projected map on D-HU (1440x720), MODE noswipe does not. Every EVERY-th pass: one track skip on SKIPDEV.
soak_loop() {
  i=0
  while :; do
    if [ "$1" = swipe ]; then adb -s "$HU" shell input swipe 500 400 1000 300 350; sleep 0.8; adb -s "$HU" shell input swipe 1000 300 500 400 350; fi
    sleep 15; i=$((i+1))
    if [ $((i % $3)) -eq 0 ]; then adb -s "$2" shell input keyevent KEYCODE_MEDIA_NEXT; echo "$(date +%T) skip" >> "$X/$4.skips.log"; fi
  done; }
soak_start() { setsid nohup bash -c "HU=$HU; X=$X; $(declare -f soak_loop); soak_loop $1 $2 $3 $4" >/dev/null 2>&1 & echo $! > "$X/soak.pid"; sleep 1; }
soak_stop()  { p=$(cat "$X/soak.pid"); kill -- -"$p" 2>/dev/null; sleep 2; kill -0 "$p" 2>/dev/null && echo SOAK-STILL-RUNNING || echo soak-stopped; }

nav_start() { adb -s "$PH" shell am start -a android.intent.action.VIEW -d 'google.navigation:q=Times+Square+New+York&mode=d' >/dev/null; }
pss()       { adb -s "$HU" shell dumpsys meminfo $PKG | grep -aE '^ *TOTAL' | head -1; }

# playing_title SERIAL : "<package> | <description>" of every media session in state 3 (PLAYING)
playing_title() { adb -s "$1" shell dumpsys media_session | awk '
  /package=/{ if (p != "" && s == 3) print p " | " d; p = $0; sub(/.*package=/, "", p); s = 0; d = "" }
  /state=PlaybackState \{state=/{ x = $0; sub(/.*state=PlaybackState \{state=/, "", x); sub(/[^0-9].*/, "", x); s = x }
  /metadata:/{ d = $0; sub(/.*description=/, "", d) }
  END{ if (p != "" && s == 3) print p " | " d }'; }
# app_title SERIAL : the description of this app's own media session, whatever its state
app_title() { adb -s "$1" shell dumpsys media_session | awk -v k=headunitrevived '
  /package=/{ inb = ($0 ~ k) } inb && /metadata:/{ d = $0; sub(/.*description=/, "", d); print d; exit }'; }
titles() {  # titles RID : records both, sequentially (two units, one call each)
  { echo "phone: $(playing_title "$PH")"; echo "app:   $(app_title "$HU")"; } | tee "$X/$1.titles.txt"; }

# Stage D only. usb_reader RID : the framework's USB lines on the host, beside the app capture (template section 7b)
usb_reader() { setsid nohup stdbuf -oL adb -s "$HU" logcat -v time -s UsbHostManager:D > "$X/$1.usb.logcat" 2>&1 & echo $! > "$X/usbreader.pid"; sleep 1; }
usb_reader_stop() { kill "$(cat "$X/usbreader.pid")" 2>/dev/null; }
# usb_dialog : if a USB system dialog is in front on D-MOTO, cue the operator (hand step, reason: a system permission or chooser dialog has no verb)
usb_dialog() { for k in $(seq 1 10); do f=$(adb -s "$HU" shell dumpsys window | grep -a mCurrentFocus)
  echo "$f" | grep -qE 'UsbPermissionActivity|UsbConfirmActivity|UsbResolverActivity' && { echo "=== OPERATOR: accept the USB dialog on D-MOTO ONCE, never tick Always ==="; printf '\a'; mark "$RUN-dialog"; sleep 10; }; sleep 2; done; }
# u1_session ARM N : one scripted U1 session (N = 2..6) on capture U1-ARM-all. RESET must be set (section 7, P-U).
u1_session() { f=$X/U1-$1-all; mark "U1-$1-s$2-start"
  c0=$(count_of $f.hu.logcat 'SSL handshake complete. '); u0=$(count_of $f.usb.logcat 'Added device UsbDevice[')
  send ACTION_DISCONNECT >/dev/null; sleep 9
  adb -s "$PH" shell $RESET
  wait_more $f.usb.logcat 'Added device UsbDevice[' $u0 20 && echo "s$2 ENUM" || echo "s$2 NO-ENUM"
  usb_dialog
  if wait_more $f.hu.logcat 'SSL handshake complete. ' $c0 30; then echo "s$2 SSL-AUTO"
  else send ACTION_CHECK_USB >/dev/null
       wait_more $f.hu.logcat 'SSL handshake complete. ' $c0 45 && echo "s$2 SSL-VERB" || echo "s$2 NO-SSL"; fi
  sleep 45; mark "U1-$1-s$2-end"; }

# Host thermal (rig-quirks/topics/tooling.md), copied from projection-teardown-and-relays-round1-brief.md
th_pkg() { if [ -x ../rig_thermal.sh ]; then ../rig_thermal.sh status | grep -o 'pkg=[0-9]*' | cut -d= -f2; return; fi
  for z in /sys/class/hwmon/hwmon*; do [ "$(cat $z/name 2>/dev/null)" = coretemp ] && { echo $(( $(cat $z/temp1_input) / 1000 )); return; }; done
  echo $(( $(cat /sys/class/thermal/thermal_zone0/temp) / 1000 )); }
th_thr() { cat /sys/devices/system/cpu/cpu0/thermal_throttle/package_throttle_count 2>/dev/null || echo 0; }
th_wait() { local lim=${1:-75} n=0; while [ "$(th_pkg)" -ge "$lim" ]; do n=$((n+1)); [ $n -ge 90 ] && return 1; sleep 10; done; }
th_watch() { while :; do echo "$(date +%T) pkg=$(th_pkg)C throttle_pkg=$(th_thr)"; sleep 20; done >> "$1"; }
th_gate() { th_wait 75 || { echo "$(date +%T) HOST_TOO_HOT ${RUN:-run}" | tee -a "$OUT/thermal.log"; return 3; }
  [ -n "$THPID" ] && kill $THPID 2>/dev/null; th_watch "$OUT/${RUN:-run}.thermal" & THPID=$!
  echo "$(date +%T) ${RUN:-run} start pkg=$(th_pkg)C throttle_pkg=$(th_thr)" >> "$OUT/thermal.log"; }
th_report() { awk -F'[ =C]+' '{p=$3; t=$5; if(p>m)m=p; if(NR==1)t0=t} END{print "thermal_max=" m "C throttle_delta=" t-t0}' "$OUT/$1.thermal"; }
```

```python
#!/usr/bin/env python3
# pr1045r2_extract.py RUN HU_CAPTURE
"""Round 2 additions for pr-1045-framing. Prints one JSON block and no verdict.
Window: from the first 'AutomationMarker: RUN-start' to the last 'AutomationMarker: RUN-end'.
Run it beside the round 1 extractor (pr1045_extract.py), which still gives throughput, faults and phone counts."""
import json, re, statistics, sys
from datetime import datetime

run, hu = sys.argv[1], sys.argv[2]
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

ZERO = ['AapRead: skipped message: ', 'AapRead: invalid framing or TLS session', 'AAP fragment routing changed',
        'Invalid AAP frame length', 'AapRead: Error in processBulk', 'AapRead: FIFO overflow',
        'SSL Decrypt: no application data after consuming', 'AapRead: Error in read loop (ignored)',
        'AapRead: Fatal read error', 'AapRead: Connection closed', 'AapRead: Connection lost',
        'AapRead: Handling error', 'AapRead: Magic Garbage', 'AapVideo: discarding a', 'AapRead: DELTA_CHANGED on',
        'AapRead: TRUNCATED_RUN on', 'AapRead: ORPHANED_FRAGMENT on', 'AapVideo: Previous frame was truncated',
        'AapMediaPlayback: Failed to parse', 'AapTransport quitting']
OTHER = ['SSL handshake complete. ', 'Handshake: SSL handshake complete', 'FAULT INJECTED (#',
         'requesting keyframe to recover stream', 'Media Start Request AUDIO', 'Media Start Request VIDEO',
         # baseline spellings, zero on the candidate by construction
         'SSL Decrypt failed', 'Decrypted payload too short', 'unwrap produced no application data',
         'AapRead: Invalid message length', 'Failed to parse metadata (']
zero_list = {p: sum(1 for l in win if p in l) for p in ZERO}
other = {p: sum(1 for l in win if p in l) for p in OTHER}

def nxt(i, pat, limit_s):
    t0 = ts(win[i])
    for j in range(i + 1, len(win)):
        t = ts(win[j])
        if t is not None and t0 is not None and t - t0 > limit_s:
            return None
        if pat in win[j]:
            return j
    return None

THR = re.compile(r'Throughput over \d+ms: rendered=\d+ \((\d+)fps\)')
INB = re.compile(r'inbound rate over (\d+)ms: video=(\d+)kB/s \((\d+) msgs\), audio=(\d+)kB/s \((\d+) msgs\)')
SKIP = re.compile(r'AapRead: skipped message: (.*) \(suppressed=(\d+)\)')
skips = []
for i, l in enumerate(win):
    m = SKIP.search(l)
    if not m:
        continue
    reason, sup = m.group(1), int(m.group(2))
    ch = re.search(r'on channel (\d+)', reason)
    rec = {'at': fmt(ts(l)), 'reason': reason, 'channel': int(ch.group(1)) if ch else None, 'suppressed_before': sup}
    j = nxt(i, 'Throughput over', 15)
    rec['next_throughput_rendered_fps'] = int(THR.search(win[j]).group(1)) if j is not None and THR.search(win[j]) else None
    j = nxt(i, 'inbound rate over', 35)
    mm = INB.search(win[j]) if j is not None else None
    rec['next_inbound_video_kBps'] = int(mm.group(2)) if mm else None
    rec['next_inbound_audio_kBps'] = int(mm.group(4)) if mm else None
    rec['session_end_within_60s'] = nxt(i, 'AapTransport quitting', 60) is not None
    skips.append(rec)

AT = re.compile(r'Audio transport (read|send) channel=(\d+)')
at = {}
for l in win:
    m = AT.search(l)
    if m:
        k = '%s ch%s' % (m.group(1), m.group(2))
        at[k] = at.get(k, 0) + 1
non_audio = sum(v for k, v in at.items() if int(k.split('ch')[1]) not in (4, 5, 6))

inb = [m.groups() for m in (INB.search(l) for l in win) if m]
inbound = {'lines': len(inb)}
if inb:
    au = [int(x[3]) for x in inb]
    inbound.update({'audio_kBps_min': min(au), 'audio_zero_lines': sum(1 for x in au if x == 0),
                    'audio_nonzero_fraction': round(sum(1 for x in au if x > 0) / len(au), 3),
                    'video_kBps_min': min(int(x[1]) for x in inb)})
AQ = re.compile(r'inbound audio quiet (\d+) times? in (\d+)ms: dead=(\d+)ms \((\d+)%\), longest=(\d+)ms')
aq = [m.groups() for m in (AQ.search(l) for l in win) if m]
audio_quiet = {'lines': len(aq), 'longest_ms_max': max((int(x[4]) for x in aq), default=0)}

MPB = re.compile(r'RECV: MUSIC_PLAYBACK .*? flags: (\d+) size: (\d+)')
runs, cur, singles, orphans = [], None, 0, 0
for l in win:
    m = MPB.search(l)
    if not m:
        continue
    fl, sz = int(m.group(1)), int(m.group(2))
    if fl == 11:
        singles += 1
    elif fl == 9:
        if cur is not None:
            anomalies.append('MUSIC_PLAYBACK run replaced before LAST')
        cur = [sz]
    elif fl in (8, 10):
        if cur is None:
            orphans += 1
            continue
        cur.append(sz)
        if fl == 10:
            runs.append(cur)
            cur = None
mpb = {'complete_runs': len(runs), 'singles': singles, 'orphan_fragments': orphans, 'open_at_end': cur is not None,
       'max_fragments': max((len(r) for r in runs), default=0), 'max_run_bytes': max((sum(r) for r in runs), default=0),
       'runs_with_3plus_fragments': sum(1 for r in runs if len(r) >= 3)}

def thirds(vals):
    if len(vals) < 3:
        return None
    n = len(vals) // 3
    parts = [vals[:n], vals[n:2 * n], vals[2 * n:]]
    return [statistics.median(x) for x in parts]
rf = [int(m.group(1)) for m in (THR.search(l) for l in win) if m]
trend = {'throughput_windows': len(rf), 'rendered_fps_median_by_third': thirds(rf),
         'inbound_video_kBps_median_by_third': thirds([int(x[1]) for x in inb]),
         'inbound_audio_kBps_median_by_third': thirds([int(x[3]) for x in inb])}

LB = re.compile(r'largest message body so far: (\d+) bytes \(on (\S+)\)')
largest = {}
for l in lines:
    m = LB.search(l)
    if m:
        largest[m.group(2)] = max(largest.get(m.group(2), 0), int(m.group(1)))

out = {'run': run, 'hu_file': hu, 'window_lines': len(win), 'window_start': fmt(ts(win[0])) if win else None,
       'window_end': fmt(ts(win[-1])) if win else None, 'zero_list': zero_list, 'zero_list_total': sum(zero_list.values()),
       'other_counts': other, 'skipped_messages': skips, 'audio_timing_lines': at, 'audio_timing_non_audio_channel': non_audio,
       'inbound': inbound, 'trend': trend, 'audio_quiet': audio_quiet, 'music_playback_runs': mpb, 'largest_body_bytes_file': largest,
       'anomalies': anomalies}
print(json.dumps(out, indent=1))
```

Both blocks were run on a synthetic capture before this brief was written. If either throws on a real capture, put the traceback in Setup notes and fall back to the §6 greps on the same marker window.

**Every run ends with `both RID`** and returns the two JSON blocks plus the commands it ran. Counts are anchored to the run's own markers. A run whose start marker is missing never landed and is void.

**Contamination (template §4), from round 1's `file_counts` of every wireless run:** `MATCH! Starting AapService` above 1, a second `createGroup SUCCESS`, `AapRead: Magic Garbage detected in header` above 0, or a second INFO-level `SSL handshake complete. ` voids the run. On A1 (VERBOSE) the DEBUG line `Handshake: SSL handshake complete. TS:` also matches, so subtract `other_counts['Handshake: SSL handshake complete']` first. Re-run a void run at most twice, then mark it INCONCLUSIVE.

## 6. The lines that decide every run

Each line was checked with `git grep -F` against the merged candidate tree `11f0bfc9` and, where the table says so, against the baseline at `7102b428`. Format specifiers appear as in source.

### 6a. New on the candidate (absent on the baseline)

| Printed form | Level | Meaning |
|---|---|---|
| `AapRead: skipped message: <reason> (suppressed=N)` | WARN, `AapRead.kt` | one message dropped, the session goes on. Shares one print budget: 10 lines, then one per 60 s, with the rest counted in `suppressed=` |
| reason `no application data on channel N` | | a FIRST or COMPLETE frame decrypted to zero bytes (`dced3994`) |
| reason `message has no complete type on channel N` | | a single frame shorter than its 2-byte type |
| reason `invalid message length N on channel N` | | the 4-byte total is below 2, above 8 MiB, or below the FIRST fragment |
| reason `fragments exceed declared size on channel N` | | more bytes than the FIRST declared |
| reason `reassembly budget exhausted on channel N` | | the shared 16 MiB copy budget is full |
| reason `incomplete message on channel N` | | LAST arrived short of the declared total |
| reason `handler <Exception> on channel N: <message>` | | a handler threw; only this message is lost |
| reason `discard handler <Exception> on channel N: <message>` | | returning a dropped message's credit threw |
| `Audio transport read channel=N ...` / `Audio transport send channel=N ...` | WARN | after `599f2fb7`, N is 4, 5 or 6 only |
| `AapMediaPlayback: Failed to parse metadata: <message>` | WARN | one metadata message did not parse |

Still fatal on the candidate, and each ends the session: `AAP fragment routing changed on channel N` (logged through `AapRead: invalid framing or TLS session` on the socket reader), `AapRead: Error in processBulk: Invalid AAP frame length N` (USB reader). `SSL Decrypt: no application data after consuming N bytes` is a WARN for a zero-produce TLS record and is harmless alone.

**Round 1's candidate spellings are gone.** `Incomplete AAP message`, `AAP fragments exceed declared size`, `Invalid AAP message length`, `AAP message has no complete type` and `AAP reassembly budget exhausted` grep to zero in the `9b8f8366` tree. Round 1's extractor still counts them, so they read 0 by construction. Do not read those zeros as a result.

### 6b. On both arms

```
Throughput over %dms: rendered=N (Nfps), fed=N (Nfps), dropped=, ...           INFO, VideoDecoder.kt, every 5 s
AapTransport: inbound rate over Nms: video=NkB/s (N msgs), audio=NkB/s (N msgs), other=...   INFO, 30 s windows
AapTransport: inbound audio quiet N times in Nms: dead=Nms (N%), longest=Nms                 INFO, only when audio went quiet
AapTransport: transport dispatch over ... videoQueue=N, videoShed=N                            INFO
Config response: %s (maxUnacked=%d)      Media Sink Setup Request: %d on channel %s      [RES_CAP] resolutionId=
Media Start Request %s: session=%d, config_index=%d
RECV: %s      VERBOSE only (an AppLog.d inside `if (AppLog.LOG_VERBOSE)`), so A1 alone: "RECV: MUSIC_PLAYBACK <type name> type: N flags: N size: N dataOffset: N"
AapRead: largest message body so far: %d bytes (on %s)                                         INFO, per encrypted frame body
AapRead: %s on %s - %s%s   (DELTA_CHANGED, TRUNCATED_RUN, ORPHANED_FRAGMENT)                    WARN, throttled
AapVideo: discarding a %d-byte access unit the framing audit found short                        WARN
AapVideo: %s, requesting keyframe to recover stream                                             WARN
AapVideo: Previous frame was truncated! Resetting assembly state.
FAULT INJECTED (#%d of %d candidates): %s on flag %d, len=%d    fault injection - <mode> ...    WARN, prefixed AapRead: or AapVideo:
cycling video focus     AapTransport: retaking video focus to complete the keyframe cycle     VideoDecoder: keyframe decoded - the picture is repaired
AapTransport quitting (clean=...)       the session-end line
SSL handshake complete. Session id: / SSL handshake complete. No session id     INFO (the DEBUG "Handshake: SSL handshake complete. TS:" appears at VERBOSE only)
AapRead: invalid framing or TLS session (candidate) / AapRead: Error in processBulk / AapRead: FIFO overflow! / AapRead: Connection closed / AapRead: Connection lost. Stopping read loop. / AapRead: Fatal read error / AapRead: Error in read loop (ignored) / AapRead: Magic Garbage detected
Found device already in accessory mode:      Requesting USB permission for      INFO, USB
AutomationMarker: <label> (WARN)    AutomationReceiver: <action> (INFO)    createGroup SUCCESS    MATCH! Starting AapService
```

Baseline-only spellings, recorded on `-B` runs and never graded: `SSL Decrypt failed`, `Decrypted payload too short`, `SSL Decrypt: unwrap produced no application data`, `AapRead: Invalid message length`, `Failed to parse metadata (fragmented)`, `Failed to parse metadata (single packet)`. A baseline count above zero with the candidate at zero is an improvement worth stating.

### 6c. Phone side (Gearhead), not in our tree

From the decompiled 17.5 and 17.8 sources, as in round 1. Only the ping line has been seen in a live capture (Gearhead 17.9.664004, round 1):

```
Received out of order ping response, received: %d, in queue: %d      the positive control; wireless only
Waiting for ack timeout, video frame dropped                         the phone waited 300 ms for a video credit
Got mismatch session id in ack. Expected: %d, got: %d, ch:%d
VIDEO_ACK_TIMEOUT                                                    400 timeouts in a row; a code identifier, not confirmed as printed
Critical error                                                       CAR.SERVICE, a session-ending refusal
```

Quote `adb -s $PH shell dumpsys package com.google.android.projection.gearhead | grep versionName` in Setup notes.

### 6d. Framework, not in our tree

`Added device UsbDevice[` from `UsbHostManager` on the USB host, read by a second, tag-filtered reader (template §7b).

## 7. Runs

Order inside each stage: baseline sweep, then candidate sweep. Before every run, set `RUN=<RID>` and call `th_gate`; after it, call `th_report <RID>` and quote the line. If the throttle counter rose or the host reached 90C during a timing run, re-run it once. Write the results file after every run.

Between runs, follow template §3a: `finish` ends the session; then `adb -s $HU shell am start -a android.intent.action.VIEW -d "headunit://exit"`, `sleep 3`, `adb -s $HU shell am force-stop $PKG`; then restore the stage backup (`pr1045_reset.sh` on D-HU), write only the next run's keys, and read them back.

**The point of the round is A1, A2 and the CR rule.** A1 proves the new copy path on the largest real copied message. A2 is the regression soak, and its trend is the credit-leak measurement. I5 and the USB stage support them.

### R0. Gate (both trees)

1. Run `run_unit_tests.sh` on the candidate: **2855 tests, 0 failures**, counted from the JUnit XML. On the baseline: **2820, 0**. Any other count means the wrong tree; stop and say so.
2. On the candidate these tests exist and pass: `replacement FIRST retires dropped media DATA exactly once before delivery`, `budget discard returns DATA credit and replacement never inherits its identity`, `copied buffer growth preserves album art and ignores TLS backing capacity`, `empty TLS-only frames leave pending messages intact while empty LAST completes`, `video read cannot use the audio history slot or throttle a following audio report`.
3. Build both APKs and copy them out. The two md5s differ.
4. After each install: `ident2 cand` or `ident2 base` prints `IDENT-OK`. Quote the `commit` line (expected the local merge commit on the candidate, `7102b428...` on the baseline).

**PASS:** all four. **FAIL:** a red test, a wrong count, a wrong symbol. A build or test failure stops the round.

---

## Stage A. D-HU as head unit, D-POCO as phone, Native AA

**Cabling:** D-HU and D-POCO on adb. **Before the stage:**

1. D-POCO was a head unit in round 1's stage E. Send `adb -s 4f4027e9 shell am start -a android.intent.action.VIEW -d "headunit://exit"`, then `sleep 3`, then `adb -s 4f4027e9 shell am force-stop com.andrerinas.headunitrevived` (CLAUDE.md: a phone that was a head unit gets an exit before a role swap).
2. D-POCO on its home screen with the screen on: `adb -s $PH shell dumpsys window | grep mCurrentFocus`; if not home, `adb -s $PH shell input keyevent KEYCODE_HOME`.
3. D-MOTO's Bluetooth off (`adb -s ZY22GC3BM4 shell svc bluetooth disable`), verified with `dumpsys bluetooth_manager`. D-HU serves one hands-free link.
4. Quote D-POCO's bonded list: `adb -s $PH shell dumpsys bluetooth_manager | grep -a -A8 'Bonded devices'`. Do nothing about it unless a bring-up fails; then follow `rig-quirks/units/D-POCO.md` on stale bonds and Gearhead's cached MAC.
5. Quote the Gearhead `versionName`, D-HU's `stat` of `shared_prefs/`, `uptime`, and the three audio keys. Take the stage backup and diff it against round 1's; state the delta.
6. **Player precondition (verify, never assume):** D-POCO has a player with at least 25 tracks with embedded album art. After `adb -s $PH shell input keyevent KEYCODE_MEDIA_PLAY` and `sleep 5`, `playing_title $PH` prints one line. If it prints nothing, start a track on D-POCO by hand (hand step, reason: choosing a track in a phone app has no verb on the app under test) and say so.

If any capture shows `THROTTLE_LIMIT_EXCEEDED`, reboot the phone and restart from the interrupted run.

B list: **A1-B, A2-B, I5-B-s1, I5-B-s2, I5-B-s3.** C list: **A1-C, A2-C, I5-C-s1, I5-C-s2, I5-C-s3.**

### A1. Album art over the new copy path, VERBOSE (about 5 min per arm) (point of the round)

Settings per §4 with `log-level` 0. `fa8b2beb` and `dced3994` changed exactly this path, so this replaces round 1's V4. D-HU floods logcat at VERBOSE; the host-side stream keeps everything because `cap_start` raises the buffer first.

```bash
source $SCR/pr1045r2_helpers.sh
RUN=A1-B; th_gate
begin_wireless A1-B || echo RERUN
adb -s $PH shell input keyevent KEYCODE_MEDIA_PLAY; sleep 10
playing_title $PH | tee $X/A1-B.before.txt
mark A1-B-start
```

Second call:

```bash
source $SCR/pr1045r2_helpers.sh
for i in $(seq 1 20); do                      # one sequential loop: skip, then pan, never two adb callers on D-HU at once
  adb -s $HU shell input keyevent KEYCODE_MEDIA_NEXT; sleep 3
  adb -s $HU shell input swipe 500 400 1000 300 350; sleep 0.8; adb -s $HU shell input swipe 1000 300 500 400 350; sleep 5
done
sleep 30; titles A1-B
finish A1-B; both A1-B; th_report A1-B
```

Use `A1-C` on the candidate. If after the first 3 skips `grep -ac 'RECV: MUSIC_PLAYBACK' $X/A1-B.hu.logcat` is still 0, repeat the loop with `adb -s $PH shell input keyevent KEYCODE_MEDIA_NEXT` in place of the `$HU` keyevent, and say which route worked.

**Reachability, both arms:** `music_playback_runs.complete_runs` at least 10 (one metadata message per skip, most of them fragmented). On the candidate, `runs_with_3plus_fragments` at least 1: a run of 3 or more frames grows the copy buffer at least twice. Report `max_fragments` and `max_run_bytes` for both arms. Without the 3-frame run, the art half is INCONCLUSIVE and the zero list is still graded.

**PASS (A1-C), all of:**
1. `zero_list_total` is 0. In particular `AapRead: skipped message: ` is 0 and `AapMediaPlayback: Failed to parse` is 0.
2. `zero_list['AapTransport quitting']` is 0, and the INFO-level SSL count for the file is 1 (§5, subtract the DEBUG duplicate).
3. **Parse proof:** in `A1-C.titles.txt`, the `app:` description starts with the same title as the `phone:` description (the text before the first `, `). Graded only when `A1-B.titles.txt` also matches; if the baseline does not match, this condition is INCONCLUSIVE.
4. Phone side: `phone.counts['Critical error']` on C is at most B's plus 1, and no C line names a protocol refusal (§3).

**FAIL:** any of 1, 2 or 4 missed, or condition 3 missed while the baseline matched.

**If the change did nothing**, B and C read the same metadata numbers, because the wire is the same. The candidate's distinct evidence is its zero list plus the title match on the copy path. Report both columns: `complete_runs`, `singles`, `max_fragments`, `max_run_bytes`, `orphan_fragments`, the baseline-only `Failed to parse metadata (` count, and both title lines.

### A2. Wireless regression soak, 20 min per arm (point of the round)

Settings per §4 (INFO, H.265 hardware, 1080p). Heavy video from navigation plus a pan every 15 s, music playing, and a track skip about every 64 s (about 19 skips, so about 19 album-art messages).

```bash
source $SCR/pr1045r2_helpers.sh
RUN=A2-B; th_gate
begin_wireless A2-B || echo RERUN
nav_start; sleep 5
adb -s $PH shell input keyevent KEYCODE_MEDIA_PLAY; sleep 10
playing_title $PH | tee $X/A2-B.before.txt
pss | tee $X/A2-B.pss-start.txt
mark A2-B-start
soak_start swipe $HU 4 A2-B
```

Second and third calls, each: `source $SCR/pr1045r2_helpers.sh; hold 540`. Fourth call:

```bash
source $SCR/pr1045r2_helpers.sh
hold 120; soak_stop
pss | tee $X/A2-B.pss-end.txt
titles A2-B
finish A2-B; both A2-B; th_report A2-B
```

Use `A2-C` on the candidate. `soak_stop` must print `soak-stopped` before `pss` runs, so that only one adb caller touches D-HU. If it prints `SOAK-STILL-RUNNING`, run `kill -9 -- -$(cat $X/soak.pid)` and say so.

**Reachability, both arms:** `throughput.windows` at least 220; `throughput.fed_fps_median` at least 25 (round 1 JSON); `context.sink_codec` is 7; `A2-x.skips.log` has at least 15 lines. Audio half: `inbound.audio_nonzero_fraction` on B at least 0.8, else the audio conditions (6, 7) are INCONCLUSIVE.

**PASS (A2-C against A2-B), all of:**
1. `zero_list_total` is 0 on C.
2. `zero_list['AapTransport quitting']` is 0 and the file's SSL count is 1, on C.
3. `throughput.rendered_fps_median(C)` at least 0.90 times B's; `inputWait_mean_ms(C)` at most the larger of 1.5 times B's and B's plus 150; `dropped_sum(C)` at most B's plus 10; `dispatch.videoShed_sum(C)` 0 or at most B's.
4. **No credit decay on video:** `trend.rendered_fps_median_by_third[2]` on C at least 0.90 times `[0]` on C, unless B shows a decay of the same size (then report both and grade this condition INCONCLUSIVE). `throughput.longest_zero_rendered_run_windows` on C at most 4.
5. Memory: `TOTAL PSS` growth (end minus start) on C at most B's growth plus 50 MB.
6. **No credit decay on audio:** `trend.inbound_audio_kBps_median_by_third[2]` on C above 0, and `inbound.audio_zero_lines(C)` at most `audio_zero_lines(B)` plus 1.
7. `audio_timing_non_audio_channel` is 0 on C (`599f2fb7`; the baseline prints no such line).
8. Parse proof as A1 condition 3, from `A2-x.titles.txt`.
9. Phone side, when the B ping control is live (`Received out of order ping response` at least 1 on B): `Waiting for ack timeout, video frame dropped` on C at most 1.5 times B's plus 10; `VIDEO_ACK_TIMEOUT` 0 on C; `Critical error` on C at most B's plus 1 (§3); out-of-order pings on C at most 1.

**FAIL:** any of 1 to 8 missed (except where marked INCONCLUSIVE), `VIDEO_ACK_TIMEOUT` above 0 on C, `Critical error` on C above B's plus 1 or naming a protocol refusal, or a session end on C. **One retry rule:** if C's session ends and the 15 s before `AapTransport quitting` hold `link has been silent` and `WiFi read timeout` (round 1's I3 rig link stall), re-run A2-C once. A second session end is a FAIL.

**If the change did nothing**, B and C read the same numbers. Report both columns for every number in 3 to 6, both `pss` lines, both thirds lists, and the phone counts.

### A3. I5: one reader-stage fault per session, three sessions per arm

`dced3994` changed what the reader does after the audit flags a hole: the reassembler now runs before the audit, and the discard flag for the video worker depends on whether the message was delivered. Mode 5 drives exactly that path, so I5 is re-run. I3 (mode 3, assembler stage) is not. The rates are round 1's single-fault settings (Setup note 8 there): mode 5, rate 100, budget 1. The fault lands in the first second after SSL, so `bw2` places the start marker before the phone comes on.

```bash
source $SCR/pr1045r2_helpers.sh
RUN=I5-B-s1; th_gate
bw2 I5-B-s1 || echo RERUN
soak_start swipe $HU 1000 I5-B-s1            # pans only; 1000 passes never reach a skip
wait_for $X/I5-B-s1.hu.logcat 'FAULT INJECTED (#' 120 && echo FAULT || echo NO-FAULT
sleep 60; soak_stop
finish I5-B-s1; both I5-B-s1; th_report I5-B-s1
```

Repeat for `s2` and `s3`, then `I5-C-s1` to `s3` after the candidate install. **Stop rule:** a session with `NO-FAULT` is void. Take at most 5 attempts per arm to collect 3 sessions with exactly 1 fault each. Fewer than 2 valid sessions on an arm makes I5 INCONCLUSIVE.

**PASS (I5-C against I5-B), all of:**
1. Detection on every candidate fault: `faults[0].discard_ms` or `faults[0].audit_ms` non-null and at most 1500 (round 1 JSON).
2. Repair on every candidate fault: `faults[0].repair_ms` non-null; the median candidate `repair_ms` at most 1.5 times the median baseline `repair_ms` plus 1000.
3. `zero_list['AapRead: invalid framing or TLS session']` and `zero_list['AapTransport quitting']` are 0 on every candidate session.
4. **The stream continues after the drop:** the last `Throughput over` window in each candidate session has rendered at least 20 fps (`trend.rendered_fps_median_by_third[2]` at least 20).
5. `AapRead: skipped message: ` is 0 on every candidate session. A streamed video run never reaches the reassembler's drop path. A non-zero count means a copied video run took the hole: then grade those entries under CR as well.
6. Phone side, summed over the three sessions: `VIDEO_ACK_TIMEOUT` 0 on C; `Got mismatch session id in ack` on C at most B's plus 2; `Waiting for ack timeout, video frame dropped` on C at most B's plus 10.

**FAIL:** a candidate fault with no repair while every baseline fault repaired, detection missing, a candidate `repair_ms` above 8000 where the baseline's are below 8000, or a candidate session end. The retry rule of A2 applies to a session end, once per sample.

Report one row per fault for both arms: `at`, `discard_ms`, `audit_ms`, `cycle_ms`, `repair_ms`, the last rendered fps, and the phone's three counts. Round 1 measured a median repair of 2702 ms (B) and 2703 ms (C) at `dfb0d6f9`; that is context, not a threshold.

### CR. Credits never leak (rule over every candidate run)

CR applies to every entry in `skipped_messages` of every `-C` run: A1, A2, I5 and the stage D runs.

- **Expected:** no entry anywhere. Then CR reads **"not exercised for reassembler drops"**, which is the pre-registered outcome and not a FAIL. The rig-level credit evidence is then A2 conditions 4, 6 and 9, I5 conditions 4 and 6, and U2S conditions 4 and 7.
- **For each entry, all of:** `session_end_within_60s` is false. If `channel` is 2: `next_throughput_rendered_fps` at least 15. If `channel` is 4, 5 or 6 in a run with music playing: `next_inbound_audio_kBps` above 0. If `reason` starts `handler `, quote the line in full.
- **Count check:** the number of entries plus the sum of `suppressed_before` values is the true drop count. Report it per run.

**FAIL:** any entry that misses a condition. **PASS:** at least one entry, and every entry meets every condition.

---

## Stage D. USB, D-MOTO as host, D-POCO as device

**Cabling:** D-MOTO as the host on wireless adb, with its USB-C port free; D-POCO plugged into D-MOTO's port over OTG, on wireless adb (`adb tcpip 5555` as in round 1). Read template §7b first: two permission prompts per connect are structural; never tick "always"; read `dumpsys usb` on D-MOTO before the stage and quote it. D-POCO has a loose USB port (round 1); do not move the cable once the stage starts.

**Before the stage:** install `base-7102b428.apk` on D-MOTO with `adb -s $HU install -r -d`, run `ident2 base`, write the stage D keys and read them back. D-MOTO can be PIN-locked: check `adb -s $HU shell dumpsys window | grep -E 'isKeyguardShowing|mDreamingLockscreen'`; if `true`, the operator unlocks it once (hand step, reason: adb cannot pass a PIN). Quote D-POCO's Gearhead `versionName`. D-POCO's own Open Headunit stays stopped (`headunit://exit`, then force-stop, as in stage A step 1).

Every stage D run starts a second reader for the framework's USB lines (`usb_reader`, §5) beside the app capture. Stage D order: **P-U, U1-B, U2S-B**, then the candidate install, **U1-C, U2S-C**.

### P-U. Precheck: can D-POCO be re-enumerated without touching the cable?

Round 1's U1 failed on every build because nothing re-enumerated D-POCO after a session: it sat in accessory mode `18D1:2D01` and never answered the next handshake. A gadget reset on the phone should re-enumerate it like a fresh plug. This premise is new on this rig, so verify it first, with the app stopped on D-MOTO:

```bash
source $SCR/pr1045r2_helpers.sh
adb -s $HU shell am force-stop $PKG
usb_reader PU; sleep 2; u0=$(count_of $X/PU.usb.logcat 'Added device UsbDevice[')
adb -s $PH shell svc usb resetUsbGadget
wait_more $X/PU.usb.logcat 'Added device UsbDevice[' $u0 20 && echo PU-ENUM || echo PU-NO-ENUM
```

If `PU-NO-ENUM`, try `adb -s $PH shell svc usb setFunctions mtp` with the same `wait_more`, and record which command worked. If neither re-enumerates D-POCO, U1 is **INCONCLUSIVE** for the same cause as round 1: skip U1 on both arms, and start U2S sessions with an operator cold plug (hand step, reason: a physical plug has no verb). Quote the `Added device` block in Setup notes. `usb_reader_stop` afterwards.

### U1. USB reconnect after re-enumeration, standard path, 6 sessions per arm

Settings per §4 (`use-libusb` false). Session 1 is a cold plug: the operator plugs D-POCO into D-MOTO, the tester waits for `SSL handshake complete. ` (hand step, reason: a physical plug has no verb). Sessions 2 to 6 are scripted, with the cable left alone. `RESET` is the command that P-U proved.

Call 1, setup and session 1:

```bash
source $SCR/pr1045r2_helpers.sh
RUN=U1-B; th_gate
cap_start U1-B-all; usb_reader U1-B-all
adb -s $HU shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity; sleep 15
mark U1-B-s1-start
# operator cold plug now, then:
wait_for $X/U1-B-all.hu.logcat 'SSL handshake complete. ' 120 && echo "s1 SSL" || echo "s1 NO-SSL"
sleep 45; mark U1-B-s1-end
```

Calls 2, 3 and 4, two sessions each at most (each session takes up to 3 minutes):

```bash
source $SCR/pr1045r2_helpers.sh
RUN=U1-B; RESET="svc usb resetUsbGadget"     # or "svc usb setFunctions mtp", per P-U
u1_session B 2; u1_session B 3               # call 3: sessions 4 and 5; call 4: session 6
```

After session 6, in the same call as session 6:

```bash
usb_reader_stop; cap_stop
for n in 1 2 3 4 5 6; do python3 $SCR/pr1045_extract.py U1-B-s$n $X/U1-B-all.hu.logcat > $X/U1-B-s$n.json; python3 $SCR/pr1045r2_extract.py U1-B-s$n $X/U1-B-all.hu.logcat > $X/U1-B-s$n.r2.json; done
th_report U1-B
```

Use `C` in place of `B` on the candidate. `ACTION_CHECK_USB` goes out only when the app's own attach path has not formed a session within 30 s, because round 1's O2 showed that a verb which preempts an automatic connect breaks it.

**Grade per arm:** `S` is the number of sessions out of 6 that printed `SSL`. `E` is the number of scripted sessions that printed `ENUM`.

**PASS (U1-C against U1-B), all of:** `S(C)` at least `S(B)` and at least 5; in every formed candidate session `zero_list_total` is 0, in particular `AapRead: Error in processBulk`, `AapRead: FIFO overflow` and `AapRead: skipped message: `; `context.maxUnacked` is 16 on every formed session.
**FAIL:** `S(C)` below `S(B)`, or below 5 with `S(B)` at least 5, or a zero-list line above 0, or a formed session that ends inside its 45 s.
**INCONCLUSIVE:** `E` below 4 on either arm (the reset did not re-enumerate reliably), or `S(B)` below 4.

Report per session: `ENUM` or `NO-ENUM`, `SSL-AUTO`, `SSL-VERB` or `NO-SSL`, the dialogs cued (`$RUN-dialog` markers), and the `Requesting USB permission for` identities.

### U2S. USB regression soak, libusb path, 15 min per arm

Settings per §4 with `use-libusb` true. Round 1's U2 held one libusb session for 60 s only; both USB paths feed the same reader (`AapReadMultipleMessages`), so one long libusb session covers the reader and the thinner path at once. D-MOTO's screen geometry is not in the quirk files, so no pans: motion comes from navigation on D-POCO, and skips go to D-POCO's own player (round 1 U3 found that skips sent to the host did not land).

```bash
source $SCR/pr1045r2_helpers.sh
RESET="svc usb resetUsbGadget"          # per P-U; if P-U failed, the operator cold-plugs instead (hand step)
RUN=U2S-B; th_gate
cap_start U2S-B; usb_reader U2S-B
adb -s $HU shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity; sleep 15
adb -s $PH shell $RESET; usb_dialog
wait_for $X/U2S-B.hu.logcat 'SSL handshake complete. ' 45 || { send ACTION_CHECK_USB >/dev/null; wait_for $X/U2S-B.hu.logcat 'SSL handshake complete. ' 60 || echo "BRINGUP-FAIL"; }
nav_start; sleep 5; adb -s $PH shell input keyevent KEYCODE_MEDIA_PLAY; sleep 10
playing_title $PH | tee $X/U2S-B.before.txt
pss | tee $X/U2S-B.pss-start.txt
mark U2S-B-start
soak_start noswipe $PH 3 U2S-B
```

Second and third calls, each: `source $SCR/pr1045r2_helpers.sh; hold 450`. Fourth call:

```bash
source $SCR/pr1045r2_helpers.sh
soak_stop; pss | tee $X/U2S-B.pss-end.txt
titles U2S-B
mark U2S-B-end; sleep 2; send ACTION_DISCONNECT >/dev/null; sleep 6; usb_reader_stop; cap_stop
both U2S-B; th_report U2S-B
```

Use `U2S-C` after the candidate install. `cap_start` captures D-POCO's logcat as the phone capture, because `PH` is D-POCO.

**Reachability, both arms:** `throughput.windows` at least 165; `context.maxUnacked` is 16; `U2S-x.skips.log` has at least 15 lines; B's `inbound.audio_nonzero_fraction` at least 0.8 (else the audio part of condition 4 is INCONCLUSIVE). If no `Throughput over` window renders on either arm, the video half is INCONCLUSIVE and the framing conditions are still graded.

**PASS (U2S-C against U2S-B), all of:**
1. `zero_list_total` 0 on C, in particular `AapRead: Error in processBulk` (which carries `Invalid AAP frame length`), `AapRead: FIFO overflow` and `AapRead: skipped message: `.
2. `zero_list['AapTransport quitting']` 0 and one SSL in the window's file, on C.
3. `rendered_fps_median(C)` at least 0.90 times B's; `dropped_sum(C)` at most B's plus 10.
4. No credit decay: `trend.rendered_fps_median_by_third[2]` on C at least 0.90 times its `[0]` (unless B decays the same), and `trend.inbound_audio_kBps_median_by_third[2]` on C above 0.
5. `TOTAL PSS` growth on C at most B's growth plus 50 MB; `audio_timing_non_audio_channel` 0 on C.
6. Parse proof as A1 condition 3 (here `app:` is D-MOTO's session and `phone:` is D-POCO's).
7. Phone side (D-POCO capture): `Critical error` on C at most B's plus 1 (§3); `Waiting for ack timeout, video frame dropped` on C at most 1.5 times B's plus 10. USB carries no ping, so there is no phone-side control on this run; a zero on both arms is reported as "unconfirmed".

**FAIL:** any of 1 to 6 missed (except where INCONCLUSIVE), or `Critical error` on C above B's plus 1 or naming a protocol refusal, or a session end on C.

---

### Final step, after the last run

On D-HU and D-MOTO: force-stop the app, delete `video-profile-starvation-cap` with both removal forms, and read it back as absent. Restore each unit's stage backup and read it back. Restore D-MOTO's Bluetooth to its pre-round state and re-enable any radio the round switched off. Quote all of it in Setup notes.

## 8. Do not re-run

| Round 1 run | Why not this round |
|---|---|
| V1, V2, V2n, V3 (F1) | The new commits do not touch the streamed video path or the ack for a complete message. A2 re-measures fps A/B under heavier load anyway. |
| V4 | Replaced by A1, which runs the same steps on the new copy path. |
| V5 | D-HU's ceiling is 1080p, so V5 duplicates V2. |
| C3, C5, I3 and F4 | Mode 3 acts in `AapVideo`, downstream of the reader; the audit stays silent and the new reader code does not run for it. F4 was graded from I3. I5 uses round 1's measured single-fault rate, so no calibration is needed. |
| M (D-MOTO as phone) | Same code path as V2; A2 covers it. |
| K | Replaced by A2, which is twice as long. |
| O1 (API 19), O2 (API 17) | The new commits change no TLS code (`AapSslContext`, `TlsUnwrapLoop` untouched) and add no API above 16: a grep of the diff for `removeIf`, `stream()`, `Optional`, `forEach(`, `java.time` and similar found none. |
| U2, U3 | Replaced by U2S (libusb, 15 minutes, skips that land). |
| D1, D2 | Not run, and UNTESTABLE on this rig. On 10 plugs the baseline printed neither `SSL Handshake: discarded a late VERSION_RESPONSE` nor `SSL Handshake: still receiving late VERSION_RESPONSEs at the deadline`, so this dongle never answers late. The new commits do not touch the handshake. The dongle stage also needs D-POCO's release build replaced again, which round 1 left for the operator to restore. The late-response path stays on the JVM. |
| U1 in round 1's shape | A disconnect then a connect with no re-enumeration fails the same on every build, including one 11 commits older than round 1's baseline. It is a property of the rig and `main`, not of this PR. U1 above re-enumerates instead. |

## 9. Report back

The numbers that decide whether this PR is ready to merge:

1. **A1:** both arms' `complete_runs`, `max_fragments` and `max_run_bytes`; the candidate's zero list and the title match.
2. **A2:** B and C `rendered_fps_median`, `inputWait_mean_ms`, `dropped_sum`, the rendered and audio thirds, PSS growth, and the phone's `Waiting for ack timeout`, out-of-order ping and `VIDEO_ACK_TIMEOUT` counts.
3. **CR:** the number of `AapRead: skipped message:` entries per candidate run (true count with `suppressed`), and for each one the follow-up fps, audio rate and session state. Say "not exercised" if there were none.
4. **I5:** the per-fault table for both arms, with the medians.
5. **U1 and U2S:** `S(B)`, `S(C)`, `E`, and U2S's B and C columns as in item 2.

**Captures** go to the existing release `rig-evidence-pr-1045-framing` as the asset `pr-1045-framing-round2-captures.zip`, with its sha256 in the results file:

```bash
zip -1 -r pr-1045-framing-round2-captures.zip $X
sha256sum pr-1045-framing-round2-captures.zip
gh release upload rig-evidence-pr-1045-framing --repo o-jcardenass/open-headunit pr-1045-framing-round2-captures.zip
```

Every line a verdict rests on goes into the results file with its timestamp: the file has to stand on its own.

## 10. Results file skeleton

Name: `pr-1045-framing-round2-results.md`, following template §7. The bold verdict sits alone on the line under each `## <RunId>` heading.

```markdown
# pr-1045-framing, round 2 results

**Candidate:** PR head 9b8f8366 merged onto main 7102b428, tree 11f0bfc9, merge commit <sha>       **Baseline:** main @ 7102b428
**APK md5:** candidate <md5> / baseline <md5>
**Unit:** D-HU (<chipset, API>) with D-POCO (<Gearhead versionName>); D-MOTO host with D-POCO device over OTG
**Date:** <yyyy-mm-dd>

## Setup notes
Deviations, scripts used and added, settings delta, stat of shared_prefs, the three audio keys, dumpsys usb, P-U result, thermal lines, dialogs cued.

## R0 - gate
## A1 | A2 | I5 | CR | P-U | U1 | U2S
(one section each; each -C graded against its -B)
**PASS|FAIL|INCONCLUSIVE|UNTESTABLE**
- Settings written:
- Radio state and how set:
- Discard-rule check:
- Reachability numbers:
- Decisive log lines, quoted with timestamps:

| measure | B | C |
|---|---|---|

## Summary
| question | runs | verdict | the deciding number |
|---|---|---|---|
| copy path carries album art | A1 | | |
| no regression under load | A2, U2S | | |
| credits never leak | CR, A2 4/6/9, I5 4/6, U2S 4/7 | | |
| reader-stage repair | I5 | | |
| USB reconnect | U1 | | |

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
```

```decisive-strings-gearhead
Received out of order ping response, received: %d, in queue: %d
Waiting for ack timeout, video frame dropped
Got mismatch session id in ack. Expected: %d, got: %d, ch:%d
VIDEO_ACK_TIMEOUT
Critical error
```

```decisive-strings-framework
Added device UsbDevice[
```

```decisive-strings
AapRead: skipped message: %s (suppressed=%d)
no application data on channel
message has no complete type
invalid message length
fragments exceed declared size
reassembly budget exhausted
incomplete message
discard handler
AAP fragment routing changed on channel
AapRead: invalid framing or TLS session
Invalid AAP frame length
AapRead: Error in processBulk
AapRead: FIFO overflow!
SSL Decrypt: no application data after consuming
AapRead: Error in read loop (ignored)
AapRead: Fatal read error
AapRead: Connection closed
AapRead: Connection lost. Stopping read loop.
AapRead: Handling error
AapRead: Magic Garbage detected
AapRead: %s on %s - %s%s
DELTA_CHANGED
TRUNCATED_RUN
ORPHANED_FRAGMENT
AapVideo: discarding a %d-byte access unit the framing audit found short
AapVideo: Previous frame was truncated! Resetting assembly state.
AapVideo: %s, requesting keyframe to recover stream
AapMediaPlayback: Failed to parse metadata:
AapMediaPlayback: Failed to parse playback status:
AapTransport quitting (clean=
SSL handshake complete. 
Handshake: SSL handshake complete.
Audio transport read channel=
Audio transport send channel=
inbound rate over 
inbound $subject quiet 
SUBJECT_AUDIO = "audio"
Throughput over 
transport dispatch over
$prefix: FAULT INJECTED (#%d of %d candidates): %s on flag %d, len=%d
$prefix: fault injection - %s
cycling video focus
AapTransport: retaking video focus to complete the keyframe cycle
VideoDecoder: keyframe decoded - the picture is repaired
Media Start Request %s: session=%d, config_index=%d
Media Sink Setup Request: %d on channel %s
Config response: %s (maxUnacked=%d)
[RES_CAP] resolutionId=
RECV: %s
MUSIC_PLAYBACK
AapRead: largest message body so far: %d bytes (on %s)
AutomationMarker: 
AutomationReceiver: 
createGroup SUCCESS
MATCH! Starting AapService
Found device already in accessory mode: 
Requesting USB permission for 
sync-media-session-aa-metadata
onDroppedMediaData
AapMessageReassembler
```
