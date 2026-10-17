# pr-1067-tls-pump, round 2 brief: phone exits and one soak on Native AA, A/B, about 1.5 hours

One stage only: D-HU as head unit with D-POCO over Native AA. Ten phone exits per arm, then one 15-minute soak per arm. Every run captures the phone beside the head unit and runs at VERBOSE. **About 1.5 hours plus builds.** An operator must be present for the exits (about 40 minutes, 20 taps).

**This brief replaces `pr-1067-tls-pump-round1-brief.md`, which was never run. Do not run round 1.** It budgeted about 6 hours over three stages. This round keeps its point (the phone exits on a socket) and cuts the rest; section 8 says why.

## 1. Build and baseline

Two APKs. Build each with `build_hur_cool.sh` (or `build_hur.sh` under the thermal gate, `--max-workers=2`, rig-quirks `topics/tooling.md`) and copy it out of `apks/` the moment it is built.

| Arm | Code | Identity | Gate |
|---|---|---|---|
| **B** (baseline) | upstream `main` (3.5.0-beta4) | commit `7102b4283666ffcc7802e49402735e3958cd51a0`, tree `e96aa72a2ff556c9d1a5733c07d69c1b183a1b61` | JVM tests 2820, 0 failures |
| **C** (candidate) | B with the PR head `7e7cbf53814a5bdbc9436f17c5a2f501d148c6e0` (eight commits on `145a0c76`) merged in, built locally | tree `9a77cc00a41e65d1b4fb9c69bb740c211b798de9` | JVM tests 2827, 0 failures; `TlsApplicationPumpTest`, `AapTlsWriterTest`, `AapSslSessionTest`, `TlsEmptyTailReaderTest`, `TlsVideoAccountingTest`, `PeerCloseReasonTest`, `ApplicationSendReadinessTest` and `MicrophoneWriteFailureTest` present and passing |

```bash
git fetch https://github.com/andreknieriem/open-headunit.git main pull/1067/head
git cat-file -e 7e7cbf53814a5bdbc9436f17c5a2f501d148c6e0 || echo "PR HEAD MISSING"
git checkout -B arm-1067-B 7102b4283666ffcc7802e49402735e3958cd51a0
git rev-parse 'HEAD^{tree}'        # MUST print e96aa72a2ff556c9d1a5733c07d69c1b183a1b61
git checkout -B arm-1067-C 7102b4283666ffcc7802e49402735e3958cd51a0
git merge --no-edit 7e7cbf53814a5bdbc9436f17c5a2f501d148c6e0
git rev-parse 'HEAD^{tree}'        # MUST print 9a77cc00a41e65d1b4fb9c69bb740c211b798de9
git rev-parse --short HEAD          # the local merge commit; record it
```

**If `PR HEAD MISSING` prints, the merge conflicts, or a tree differs, stop and report the PR's new head.** The contributor rebases often; a new head needs a new brief. **Both arms are on today's `main`:** C is the PR merged onto B, so the arms differ by the PR alone. Do not push the merge commit.

**B may be reused.** The main-smoke round 1 built `7102b428` as md5 `62ccbb85dfed879a1151dea9610df817` with 2820 tests. If that APK is still on disk with that md5, reuse it and do not rebuild. Say which in Setup notes.

The eight test classes above must not exist on B. A build or test failure stops the round.

**Identity, per arm, before the first run on D-HU.** Pull the installed APK and hash it locally (`apk_check`). Then count a string only C carries:

```bash
unzip -p $OUT/live.apk 'classes*.dex' | strings | grep -cF 'encrypted write failed or incomplete'     # B: 0, C: 1 or more
```

Also `send ACTION_QUERY_STATE` and record `commit` (B starts `7102b428`; C is the local merge commit). A mismatch voids that arm's runs: reinstall once, then mark them UNTESTABLE.

## 2. What this is and why

The change replaces the single TLS `wrap` and `unwrap` with loops (`TlsApplicationPump`), gives each transport its own TLS engine, and sends any TLS control record the engine makes after the handshake through a new writer (`AapTlsWriter`). The fifth commit, new since round 1's brief, changes the fragment audit. The review is `~/projects/ohu-project/ohu-fixes-handoff/pr-1063-1067/pr-1067-review.md` on the authoring machine.

**Finding 1, and the point of this round.** The review found this blocker at `3b05fe81`. The sixth commit, `45cd6fde`, fixes it: a ByeBye or a Helper close marker now sets `peerRequestedClose` before any write, and `quit()` reports `clean || peerRequestedClose` under one lock. This round proves the fix on hardware. Two later commits change nothing it grades: `1c147e43` holds application writes until AuthComplete is on the wire, and `7e7cbf53` ends the session when a microphone write fails, as other writes already did. The defect, as found: on C, the send thread calls `quit()` with `clean = false` when an encrypted write fails. `quit()` acts only for its first caller. When the phone sends a ByeBye, `AapControl.byebyeRequest` sends the response, sleeps 500 ms on the poll thread, and only then calls `quit(clean = true)`. If the phone closes the link inside those 500 ms and a media ack or a sensor message goes out, the write fails (EPIPE after the phone's FIN), and the send thread quits first. The session then ends as a lost link: `AapProjectionActivity` shows the reconnecting overlay instead of finishing, `AapService` reports `link_lost`, and the app may retry a session the user ended. On B the same failed write only logs `AapTransport: send failed (ret=` and the session still ends clean. The review proved this from the code. This round measures how often it happens on hardware.

**What exposes it.** Only a ByeBye followed by a write failure inside the 500 ms. B shows how often that happens: every B exit with `AapTransport: send failed (ret=` between the ByeBye and the quitting line is an exit where C would go unclean.

**Second: the new audit (commit `3b05fe81`).** C requires the decrypted bytes of a fragmented message to equal the total the phone declares, exactly. If this phone's total differs by a fixed amount, C discards every large video frame. A healthy soak on C must therefore print no audit finding. The same audit is in PR #1045, whose round 2 grades it too.

**Third: post-handshake TLS records.** TLS 1.2 carries none in normal use. If the count is zero on both arms, the new control path never runs on this rig. That is a result, not a failure.

**Fourth: resumption.** C gives each transport a new engine but shares one `SSLContext`, so a reconnect in the same process should still resume. The session id on `SSL handshake complete. Session id:` shows it.

## 3. What is different about this round

- **The phone exit is a hand step (H1), with a reason.** No adb command and no app verb ends a session from the phone's side. A force-stop of Android Auto is not a ByeBye. The lever that works (projection-raise round 1): in Android Auto's own UI on D-HU's screen, tap **Exit**, then **Finish later** if Android Auto asks. That sends `ByeByeRequest` with `Reason: USER_SELECTION`. The script cues each exit with an `OPERATOR_STEP` line and waits for the ByeBye in the log. A cue with no ByeBye within 240 s is repeated once. A second miss ends that run, and the exits before it still count.
- **No other hand step.** If D-POCO cannot be woken, see the stop rules.
- **The whole round runs at VERBOSE** (`log-level` 0), because C prints its zero-output decrypt only there, and B's decrypt status line is VERBOSE too. D-HU floods logcat at this level. The capture streams to a file, and the soak checks its `Throughput over` count against its length to prove the capture kept up.
- **The phone logs `Critical error` lines at every session end, on every build.** Gearhead prints 1 to 5 when a session ends (`pr-1065-reconnect-timers` and `pr-1066-byebye-priority` round 1). So an exit window is never graded on that count. Inside the soak window no session ends, so it is graded there. A `Critical error` line that names a protocol refusal (for example `Multiple media configs received`) is a FAIL anywhere; quote it.
- **The microphone trigger is not an `AutomationReceiver` verb.** The soak opens the assistant through the app's exported `RemoteControlReceiver` with `--es command voice`. Mic counts are reported and never graded.
- **Video and music load.** Music plays on the phone; a swipe loop into the projected video (template section 7a) keeps the picture moving. The swipe stops before each exit cue, because the operator's tap and a scripted swipe on one screen would collide. Only one adb caller runs against a unit at a time.
- **Pre-registered outcomes.** P (post-handshake records) is expected to read zero on both arms. S is INCONCLUSIVE if B shows fewer than 2 repeated session ids. XW is INCONCLUSIVE if B shows fewer than 3 exposed exits.

## 4. Setup

Pre-flight: `rig_preflight.sh D_HU:wifi,bt D_POCO:wifi,bt`. Quote its output.

Make `hur-wifi-test-scripts/pr-1067-tls-pump-round2/`. Copy `ohu_lib.sh`, `ohu_setkeys.py` and `ptr_lib.sh` into it from `hur-wifi-test-scripts/projection-teardown-and-relays-round3/`. Save `tp_lib.sh` and `tp_extract.py` (below) beside them. List all five in Setup notes. Run each block as one script in the background, and watch `$OUT/hand-steps.log`; relay each new `OPERATOR_STEP` line to the operator at once.

```bash
OUT=$PWD; source ./ohu_lib.sh; source ./ptr_lib.sh; source ./tp_lib.sh
HU=27870808938846; PH=4f4027e9; PUT=hu_put; SW="500 400 1000 300"; HUNAME="D-HU (the head unit)"
KEYS="int:log-level=0 int:wifi-connection-mode=3 set:connection-modes=wifi int:onboarding-version=2 int:native-driver-selection-mode=0 del:video-profile-starvation-cap"
prefs_cat > $OUT/settings-backup-DHU.xml; BASEXML=$OUT/settings-backup-DHU.xml
WANT_MD5=<md5 of the arm's APK>      # set before every arm; apk_check compares the installed APK with it
```

Install each arm with `adb -s $HU install -r -d <named apk>`, never with a script that picks the newest APK. After each install, read every key in `KEYS` back.

`stat` `/data/data/$PKG/shared_prefs` on D-HU first and record the owner (rig-quirks `units/D-HU.md`). Record the Gearhead `versionName` on D-POCO once (`adb -s 4f4027e9 shell dumpsys package com.google.android.projection.gearhead | grep -m1 versionName`). Diff `settings.xml` against the backup and state the delta.

**Read and quote, never write:** `use-aac-audio`, `audio-latency-multiplier`, `audio-queue-capacity` (the rig's deliberate worst case; never reset them), `enable-audio-sink` (must read `true`; if `false`, the load is video only, so say so), `video-codec`, `resolutionId`.

Before the stage: D-MOTO's and D-SAM's Bluetooth off, and our app stopped on both (`send ACTION_EXIT`, then force-stop), because D-HU serves one hands-free link. D-POCO bonded to D-HU, its screen at home (`adb -s 4f4027e9 shell dumpsys window | grep mCurrentFocus`; `adb -s 4f4027e9 shell input keyevent KEYCODE_HOME` if not). If D-POCO was a head unit in an earlier round today, send it `headunit://exit` first (`adb -s 4f4027e9 shell am start -a android.intent.action.VIEW -d "headunit://exit"`, `sleep 3`, then force-stop).

**Verbs and commands.**

| Action | Command |
|---|---|
| state and identity | `send ACTION_QUERY_STATE` |
| marker | `mark <label>` (head unit) and `pmark <label>` (phone); `both <label>` does both |
| wireless re-arm | `send ACTION_START_WIRELESS_SCAN` (`arm_w` falls back to launching `$MAIN` if the reply holds `not allowed`) |
| raise the projection | `send ACTION_RAISE_PROJECTION` |
| end of run | `close_run <RUN>` (`ACTION_EXIT`, then force-stop) |
| music | `adb -s $PH shell input keyevent KEYCODE_MEDIA_PLAY`; if the phone is not playing, the projected play control `adb -s $HU shell input tap 272 657` (rig-quirks `topics/audio.md`) |
| assistant | `adb -s $HU shell am broadcast -n $PKG/com.andrerinas.openheadunit.app.RemoteControlReceiver -a com.android.music.musicservicecommand --es command voice` |
| phone exit | H1, cued by `exit_cycle` |

Every `send` must print `AutomationReceiver: <full action>` in the capture. A verb with no such line never landed: that step is void, not a FAIL.

**`tp_lib.sh`**:

```bash
# tp_lib.sh : source after ohu_lib.sh and ptr_lib.sh. Needs HU, PH, OUT, PUT, BASEXML, KEYS, WANT_MD5, SW, HUNAME.
pmark() { adb -s "$PH" shell log -t OHURIG "$1"; }
both()  { mark "$1"; pmark "$1"; }
cue()   { echo "$(date +%T) OPERATOR_STEP $1"; printf '\a'; echo "$(date +%T) $1" >> "$OUT/hand-steps.log"; }
SSLRE='SSL handshake complete\. (Session id|No session id)'
PICRE='Throughput over [0-9]+ms: rendered=[1-9]'
BYERE='RECEIVED BYEBYE REQUEST FROM PHONE'
RCV=$PKG/com.andrerinas.openheadunit.app.RemoteControlReceiver
# sess_open RUN : captures on, settings in; the bring-up follows
sess_open() { RUN=$1; CAP=$OUT/$RUN.logcat; apk_check || return 1; adb -s $HU shell am force-stop $PKG
  $PUT "$BASEXML" $KEYS; for t in CAR.GAL CAR.GAL.GAL.LITE CAR.GAL.SECURITY.LITE; do adb -s $PH shell setprop log.tag.$t VERBOSE; done
  pcap_start "$RUN"; cap_start; sleep 1; both $RUN-open; }
close_run() { both $1-close; sleep 1; pcap_stop; cap_stop; th_report $1; send ACTION_EXIT >/dev/null; sleep 3; adb -s $HU shell am force-stop $PKG
  python3 -I tp_extract.py "$CAP" "$PCAP" > $OUT/$1.json; }
music() { local p; adb -s $PH shell input keyevent KEYCODE_MEDIA_PLAY; sleep 8
  p=$(adb -s $PH shell dumpsys media_session | grep -c 'state=PLAYING')
  if [ "$p" = 0 ]; then adb -s $HU shell input tap 272 657; sleep 8; p=$(adb -s $PH shell dumpsys media_session | grep -c 'state=PLAYING'); fi
  echo "$(date +%T) $RUN playing=$p" >> $OUT/$RUN.music; }
# swipe_for SECS : drags the projected picture back and forth; the only adb caller on $HU while it runs
swipe_for() { local end=$((SECONDS+$1)) r; r=$(echo $SW | awk '{print $3,$4,$1,$2}')
  while [ $SECONDS -lt $end ]; do adb -s $HU shell input swipe $SW 350; sleep 0.8; adb -s $HU shell input swipe $r 350; sleep 0.8; done; }
arm_w() { local r; r=$(send ACTION_START_WIRELESS_SCAN | tr -d '\r'); echo "$r" | grep -q 'not allowed\|Exception' && adb -s $HU shell am start -n $MAIN >/dev/null; }
# picture : the projection is up and rendering, or it is raised once
picture() { local L=$1; waitfor 40 "$PICRE" $L || { send ACTION_RAISE_PROJECTION >/dev/null; waitfor 30 "$PICRE" $L; }; }
# first_session RUN : launch, wait for SSL, one re-arm; returns 3 when no session forms
first_session() { local L; L=$(nl); adb -s $HU shell am start -n $MAIN >/dev/null
  waitfor 150 "$SSLRE" $L || { arm_w; waitfor 150 "$SSLRE" $L || { echo "NO_FIRST_SESSION $1" | tee -a $OUT/$1.summary; return 3; }; }
  picture $L; send ACTION_QUERY_STATE > $OUT/$1.state; }
# exit_cycle ID : load, then H1, then 10 s of teardown. Returns 1 when the operator missed the cue twice.
exit_cycle() { local L; music; L=$(nl); swipe_for 20
  waitfor 5 "$PICRE" $L || echo "LOAD_FAIL $1" | tee -a $OUT/$RUN.summary
  both $1-go; L=$(nl)
  cue "On $HUNAME: end Android Auto from its own UI. Tap Exit, then Finish later if asked ($1)"
  if ! waitfor 240 "$BYERE" $L; then cue "REPEAT on $HUNAME: Exit, then Finish later ($1)"
    waitfor 240 "$BYERE" $L || { both $1-done; echo "OPERATOR_MISSED $1" | tee -a $OUT/$RUN.summary; return 1; }; fi
  sleep 10; both $1-done; }
# recon_w ID : the phone may redial by itself; otherwise the WiFi button verb
recon_w() { local L R=auto; L=$(nl); both $1r-start
  if ! waitfor 45 "$SSLRE" $L; then R=verb; arm_w; waitfor 120 "$SSLRE" $L || R=FAIL; fi
  [ $R != FAIL ] && { picture $L || R=NOPIC; }
  echo "$1 reconnect=$R" | tee -a $OUT/$RUN.summary; both $1r-end; [ $R = auto ] || [ $R = verb ]; }
# soak_blocks N : N blocks of 5 minutes; each is 270 s of swipes, a marker, the assistant, then music again
soak_blocks() { local n; for n in $(seq 1 $1); do swipe_for 270; mark $RUN-b$n; sleep 1
  adb -s $HU shell am broadcast -n $RCV -a com.android.music.musicservicecommand --es command voice >/dev/null; sleep 20
  adb -s $PH shell input keyevent KEYCODE_MEDIA_PLAY; sleep 8; done; }
```

`waitfor`, `nl`, `mark`, `send`, `hu_put`, `apk_check`, `cap_start`, `cap_stop`, `pcap_start`, `pcap_stop`, `th_report`, `prefs_cat`, `MAIN`, `PKG` come from `ohu_lib.sh` and `ptr_lib.sh`, as in the pr-1066 round. If a script does not match the real line format, fix it, say so in Setup notes, and keep going.

**`tp_extract.py`** (counts only, no verdicts; run by `close_run`, or by hand as `python3 -I tp_extract.py <hu capture> <phone capture> > <run>.json`):

```python
#!/usr/bin/env python3
# tp_extract.py CAP [PCAP] : one JSON object for a run. CAP is the head unit capture (logcat -v time),
# PCAP the phone capture (logcat -v epoch, markers from `log -t OHURIG`). Counts only, no verdicts.
import sys, re, json

LINE = re.compile(r'^\d\d-\d\d (\d\d):(\d\d):(\d\d)\.(\d{3}) ([VDIWEF])/([^(]*)\(\s*(\d+)\): ?(.*)$')
SSL = re.compile(r'SSL handshake complete\. (?:Session id: (\S+)|No session id)')
QUIT = re.compile(r'AapTransport quitting \(clean=(true|false)\)')
STATE = re.compile(r'AapService: session state disconnected \((\w+)\)')
THR = re.compile(r'Throughput over \d+ms: rendered=(\d+)')
MARK = re.compile(r'AutomationMarker: (\S+)\s*$')
SUPP = re.compile(r'\(and (\d+) more since the last report\)')
ST_B0 = re.compile(r'SSL Decrypt Status: \w+, Produced: 0,')
BYE = '!!! RECEIVED BYEBYE REQUEST FROM PHONE !!!'
UNEXP = 'AapProjectionActivity: Disconnected unexpectedly.'
FINCLEAN = 'AapProjectionActivity: Finishing because state isUserExit=false, isClean=true'
ENCW = 'AapTransport: encrypted write failed or incomplete'
SENDF = 'AapTransport: send failed (ret='
TLSX = 'AapRead: TLS state cannot continue'
DECF = 'SSL Decrypt failed'
ZU_B = 'SSL Decrypt: unwrap produced no application data'
SHORT0 = 'Decrypted payload too short: 0 '
P0_M = 'SSL Decrypt: produced 0, consumed'
AUDIT = {'audit_delta_changed': 'AapRead: DELTA_CHANGED on ', 'audit_truncated': 'AapRead: TRUNCATED_RUN on ',
         'audit_orphaned': 'AapRead: ORPHANED_FRAGMENT on '}
FIXED = {
    'bye': BYE, 'unexpected': UNEXP, 'finish_clean': FINCLEAN, 'enc_write_failed': ENCW,
    'send_failed_old': SENDF, 'tls_cannot_continue': TLSX, 'ssl_decrypt_failed': DECF,
    'zero_unwrap_B': ZU_B, 'short0': SHORT0, 'produced0_M': P0_M,
    'status_any_B': 'SSL Decrypt Status: ', 'produced_any_M': 'SSL Decrypt: produced ',
    'magic_garbage': 'Magic Garbage detected in header', 'match_wake': 'MATCH! Starting AapService',
    'ctx_conscrypt': 'Creating SSLContext with Conscrypt provider',
    'ctx_default': 'Creating SSLContext with default provider',
    'audit_established_B': 'AapRead: fragment accounting established',
    'video_discard': 'AapVideo: discarding a ',
    'mic_request': 'Mic request: ', 'voice_start': 'Voice Session Notification: START',
    'media_start_audio': 'Media Start Request AUDIO: ', 'failed_join': 'Failed to join threads',
}


def parse(path):
    out, prev, off = [], None, 0
    with open(path, errors='replace') as f:
        for raw in f:
            m = LINE.match(raw.rstrip('\r\n'))
            if not m:
                continue
            h, mi, s, ms = map(int, m.group(1, 2, 3, 4))
            t = ((h * 60 + mi) * 60 + s) * 1000 + ms
            if prev is not None and t + off < prev - 3600000:
                off += 86400000
            t += off
            prev = t
            out.append((t, int(m.group(7)), m.group(8)))
    return out


def with_suppressed(lines, needle):
    hit = [x for _, _, x in lines if needle in x]
    return len(hit) + sum(int(m.group(1)) for x in hit for m in [SUPP.search(x)] if m)


def counts(lines):
    c = {k: sum(1 for _, _, x in lines if v in x) for k, v in FIXED.items()}
    c['zero_unwrap_B_suppressed'] = with_suppressed(lines, ZU_B) - c['zero_unwrap_B']
    for k, v in AUDIT.items():
        c[k] = with_suppressed(lines, v)          # printed lines plus the throttled count they carry
    c['audit_total'] = sum(c[k] for k in AUDIT)
    c['status_produced0_B'] = sum(1 for _, _, x in lines if ST_B0.search(x))
    c['ssl'] = sum(1 for _, _, x in lines if SSL.search(x))
    q = [QUIT.search(x).group(1) for _, _, x in lines if QUIT.search(x)]
    c['quit_clean_true'] = q.count('true')
    c['quit_clean_false'] = q.count('false')
    thr = [(t, int(THR.search(x).group(1))) for t, _, x in lines if THR.search(x)]
    c['throughput_windows'] = len(thr)
    c['throughput_max_gap_ms'] = max([b[0] - a[0] for a, b in zip(thr, thr[1:])], default=None)
    r = sorted(v for _, v in thr)
    c['rendered_median'] = r[len(r) // 2] if r else None
    return c


def phc(w):
    c = {k: sum(1 for _, _, y in w if v in y) for k, v in
         (('zero_unwrap_B', ZU_B), ('short0', SHORT0), ('produced0_M', P0_M))}
    c['status_produced0_B'] = sum(1 for _, _, y in w if ST_B0.search(y))
    return c


def between(lines, t0, t1):
    return [l for l in lines if t0 <= l[0] <= t1]


def main():
    L = parse(sys.argv[1])
    res = {'lines': len(L), 'all': counts(L)}
    mt = {}
    for t, _, x in L:
        m = MARK.search(x)
        if m:
            mt.setdefault(m.group(1), t)
    # Post-handshake records: per INFO SSL line, the 10 s after it and the whole session after it.
    sess = []
    for i, (t, pid, x) in enumerate(L):
        if not SSL.search(x):
            continue
        end = L[-1][0]
        for j in range(i + 1, len(L)):
            if QUIT.search(L[j][2]) or SSL.search(L[j][2]):
                end = L[j][0]
                break
        sess.append({'t': t, 'pid': pid, 'session_id': SSL.search(x).group(1) or 'none',
                     'first10s': phc(between(L, t, t + 10000)), 'session': phc(between(L, t, end)),
                     'session_ms': end - t})
    res['sessions'] = sess
    # Resumption: a handshake whose id equals the previous handshake's id in the same process.
    rep, recon, last = 0, 0, {}
    for s in sess:
        if s['pid'] in last:
            recon += 1
            if s['session_id'] != 'none' and s['session_id'] == last[s['pid']]:
                rep += 1
        last[s['pid']] = s['session_id']
    res['reconnects_in_process'] = recon
    res['reconnects_same_session_id'] = rep
    # Finding 5: a baseline send failure that the picture outlived by more than 10 s.
    f5 = []
    for i, (t, _, x) in enumerate(L):
        if SENDF in x:
            tq = next((u for u, _, y in L[i + 1:] if QUIT.search(y)), L[-1][0])
            thr = [u for u, _, y in L[i + 1:] if u <= tq and THR.search(y)]
            f5.append({'t': t, 'picture_after_ms': (thr[-1] - t) if thr else 0})
    res['send_failed_old_events'] = len(f5)
    res['send_failed_old_survived_10s'] = sum(1 for e in f5 if e['picture_after_ms'] > 10000)
    # Exits: windows <ID>-go .. <ID>-done.
    exits = []
    for name, t0 in mt.items():
        if not name.endswith('-go') or name[:-3] + '-done' not in mt:
            continue
        ID = name[:-3]
        W = between(L, t0, mt[ID + '-done'])
        e = {'id': ID, 'bye': sum(1 for _, _, x in W if BYE in x)}
        tb = next((t for t, _, x in W if BYE in x), None)
        e['ssl_before_bye'] = sum(1 for t, _, x in W if SSL.search(x) and (tb is None or t < tb))
        e['magic_garbage'] = sum(1 for _, _, x in W if FIXED['magic_garbage'] in x)
        if tb is not None:
            q = next(((t, QUIT.search(x).group(1)) for t, _, x in W if t >= tb and QUIT.search(x)), None)
            tq = q[0] if q else mt[ID + '-done']
            e['first_quit_clean'] = q[1] if q else 'none'
            e['bye_to_quit_ms'] = (q[0] - tb) if q else None
            A = [l for l in W if l[0] >= tb]
            e['enc_write_failed_bye_to_quit'] = sum(1 for t, _, x in A if t <= tq and ENCW in x)
            e['send_failed_old_bye_to_quit'] = sum(1 for t, _, x in A if t <= tq and SENDF in x)
            e['unexpected'] = sum(1 for _, _, x in A if UNEXP in x)
            e['finish_clean'] = sum(1 for _, _, x in A if FINCLEAN in x)
            st = next((STATE.search(x).group(1) for _, _, x in A if STATE.search(x)), 'none')
            e['state_reason'] = st
            e['tls_cannot_continue'] = sum(1 for _, _, x in A if TLSX in x)
            e['ssl_decrypt_failed'] = sum(1 for _, _, x in A if DECF in x)
            e['defect'] = int(e['first_quit_clean'] == 'false' or e['unexpected'] > 0 or st == 'link_lost')
        exits.append(e)
    res['exits'] = sorted(exits, key=lambda e: mt[e['id'] + '-go'])
    # Other windows: <X>-start .. <X>-end (the soak window)
    win = {}
    for name, t0 in mt.items():
        if name.endswith('-start') and name[:-6] + '-end' in mt:
            c = counts(between(L, t0, mt[name[:-6] + '-end']))
            c['span_ms'] = mt[name[:-6] + '-end'] - t0
            win[name[:-6]] = c
    res['windows'] = win
    if len(sys.argv) > 2:
        P = [l.rstrip('\r\n') for l in open(sys.argv[2], errors='replace')]
        pm = {}
        for i, l in enumerate(P):
            if 'OHURIG' in l:
                pm.setdefault(l.split()[-1], i)
        ph = {'critical_error': sum('Critical error' in l for l in P),
              'fatal_exception': sum('FATAL EXCEPTION' in l for l in P), 'windows': {}}
        for n, i in pm.items():
            for a, b in (('-go', '-done'), ('-start', '-end')):
                if n.endswith(a) and n[:-len(a)] + b in pm:
                    seg = P[i:pm[n[:-len(a)] + b] + 1]
                    ph['windows'][n[:-len(a)]] = {'critical_error': sum('Critical error' in l for l in seg),
                                                 'fatal_exception': sum('FATAL EXCEPTION' in l for l in seg),
                                                 'critical_lines': [l for l in seg if 'Critical error' in l][:10]}
        res['phone'] = ph
    print(json.dumps(res, indent=1))


main()
```

## 5. The deciding lines

Head unit lines, checked with `git grep -F` against the B tree (`7102b428`) and the merged C tree (`9a77cc00`). "Composed" means the source builds the printed text from a format; the printed form is what the extractor greps.

| Line | Source | Level | B | C | Used for |
|---|---|---|---|---|---|
| `!!! RECEIVED BYEBYE REQUEST FROM PHONE !!! Reason: ` | `AapControl.kt` | INFO | yes | yes | the exit landed |
| `Sending BYEYERESPONSE` (spelled so in source) | `AapControl.kt` | INFO | yes | yes | report |
| `Calling aapTransport.quit(clean=true)` | `AapControl.kt` | INFO | yes | yes | report: the clean call, 500 ms after the ByeBye |
| `AapTransport quitting (clean=` then `true)` or `false)` | `AapTransport.kt` | INFO | yes | yes | **the verdict**: the first one after the ByeBye |
| `Quitting because ret < 0 (` | `AapTransport.kt` | INFO | yes | yes | report |
| `AapProjectionActivity: Disconnected unexpectedly.` | `AapProjectionActivity.kt` | WARN | yes | yes | defect |
| `AapProjectionActivity: Finishing because state isUserExit=false, isClean=true` (composed) | `AapProjectionActivity.kt` | INFO | yes | yes | report |
| `AapService: session state disconnected (phone_left)` or `(link_lost)` (composed) | `AapService.kt` | INFO | yes | yes | defect when `link_lost` |
| `AapTransport: encrypted write failed or incomplete` | `AapTransport.kt` | WARN | no | yes | C's failed write |
| `AapTransport: send failed (ret=` | `AapTransport.kt` | WARN | yes | no | B's failed write: exposure and F5 |
| `AapRead: TLS state cannot continue` | `AapReadSingleMessage.kt`, `AapReadMultipleMessages.kt` | ERROR | no | yes | soak zero list |
| `SSL Decrypt failed` | `AapSslContext.kt` | ERROR | yes | yes | soak zero list |
| `AapRead: DELTA_CHANGED on `, `AapRead: TRUNCATED_RUN on `, `AapRead: ORPHANED_FRAGMENT on ` (composed, throttled; may end `(and N more since the last report)`) | `AapRead.kt` | WARN | yes (B's older audit) | yes | soak: the new audit |
| `AapRead: fragment accounting established for ` | `AapRead.kt` | INFO | yes | no | report: B's audit settled |
| `AapVideo: discarding a ` (composed, `%d-byte access unit the framing audit found short`) | `AapVideo.kt` | WARN | yes | yes | soak: the audit threw a frame away |
| `SSL Decrypt: unwrap produced no application data` (throttled) | `AapSslContext.kt` | WARN | yes | no | P on B |
| `SSL Decrypt Status: OK, Produced: 0, Consumed: ` (composed) | `AapSslContext.kt` | VERBOSE guard | yes | no | P on B, unthrottled |
| `Decrypted payload too short: 0  chan: ` (composed) | `AapMessageIncoming.kt` | ERROR | yes | yes | P on B |
| `SSL Decrypt: produced 0, consumed ` (composed) | `AapSslContext.kt` | VERBOSE guard | no | yes | P on C |
| `SSL handshake complete. Session id: ` / `SSL handshake complete. No session id (full handshake).` | `AapSslContext.kt` | INFO | yes | yes | one per session; resumption |
| `Handshake: SSL handshake complete. TS: ` | `AapTransport.kt` | DEBUG | yes | yes | never counted: at VERBOSE it doubles a plain grep |
| `Creating SSLContext with default provider` | `AapSslContext.kt` | DEBUG | yes | yes | provider, recorded |
| `Throughput over ` (regex `Throughput over [0-9]+ms: rendered=[1-9]`) | `VideoDecoder.kt` | INFO | yes | yes | load gate, soak cadence |
| `Media Start Request AUDIO: ` (composed) | `AapControl.kt` | INFO | yes | yes | music load |
| `Voice Session Notification: START`, `Mic request: ` | `AapControl.kt` | INFO | yes | yes | mic, report only |
| `Failed to join threads` | `AapTransport.kt` | ERROR | yes | yes | report |
| `Magic Garbage detected in header`, `MATCH! Starting AapService` | readers, receiver | | yes | yes | discard rules |
| `AutomationReceiver: `, `AutomationMarker: ` | automation | INFO, WARN | yes | yes | verbs landed, windows |

Phone lines (Gearhead, not in our tree), in the phone capture: `Critical error` and `FATAL EXCEPTION`. Markers on the phone are `OHURIG` lines from `pmark`.

## 6. Runs

Order: R0, XW-B, KW-B, install C, XW-C, KW-C. **The point of the round is XW.** P, F5 and S are read from the same captures.

Between runs: `close_run` ends the run; restore the backup with `$PUT`; write only the next run's keys.

### R0. Gate

1. JVM tests on both trees (or B's earlier count if B is reused), 0 failures each. The five C-only test classes exist and pass on C, and do not exist on B. Record both counts.
2. Both APKs built (or B reused) and copied out; two different md5s.
3. After each install: the identity check in section 1 prints the expected count, and `ACTION_QUERY_STATE` returns the expected `commit`.

**PASS:** all three. A build or test failure, or a tree other than `9a77cc00...`, stops the round.

### XW-B and XW-C. Ten phone exits per arm (**the point of the round**)

```bash
xw() { local A=$1 n=0 g=0; sess_open XW-$A || return 1
  first_session XW-$A || { close_run XW-$A; return 3; }
  while [ $g -lt 10 ] && [ $n -lt 14 ]; do n=$((n+1))
    exit_cycle XW-$A-x$n || break
    g=$((g+1)); sleep 2
    recon_w XW-$A-x$n || { sleep 5; recon_w XW-$A-x$n-again || break; }
  done
  close_run XW-$A; }
xw B      # then KW-B; then install C, WANT_MD5=<C md5>, identity check; xw C
```

`g` counts attempts that reached a ByeBye. The grader drops any exit the discard rules void, so up to 4 extra attempts are allowed (`n` stops at 14).

**Discard rules, per exit (void, not graded):** the window `<ID>-go` to `<ID>-done` holds `Magic Garbage detected in header`, or an SSL line before the ByeBye (`ssl_before_bye` above 0); or `LOAD_FAIL <ID>` was printed. A `MATCH! Starting AapService` inside the window is reported, not voided (the phone's own reconnect can raise it after a ByeBye).

Per exit, from `XW-<arm>.json` `exits[]`: `bye`, `first_quit_clean`, `bye_to_quit_ms`, `enc_write_failed_bye_to_quit` (C), `send_failed_old_bye_to_quit` (B), `unexpected`, `finish_clean`, `state_reason`, `tls_cannot_continue`, `ssl_decrypt_failed`, `defect`. From `phone.windows[<ID>]`: `critical_error` and `critical_lines` (reported, see section 3).

**Grade, per arm.** A graded exit has `bye` 1 and passed the discard rules. `d` is the number of graded exits with `defect` 1 (the first quitting line after the ByeBye reads `clean=false`, or `Disconnected unexpectedly.`, or `state_reason` `link_lost`). On B, `e_B` is the number of graded exits with `send_failed_old_bye_to_quit` of 1 or more: each is an exit where the write failed inside the 500 ms, so C would have quit unclean.

- **FAIL (the fix in `45cd6fde` does not hold):** `d` on XW-C is 1 or more. Quote, for each defect exit, the ByeBye line, every `AapTransport: encrypted write failed or incomplete` line, the first quitting line and the session state line, with timestamps.
- **PASS:** `d` on XW-C is 0, XW-C has 8 or more graded exits, `d` on XW-B is 0, and the race happened often enough to expose C: `e_B` on XW-B is 3 or more, or `e_C` on XW-C is 3 or more. `e_C` is the number of graded C exits with `enc_write_failed_bye_to_quit` of 1 or more; each one is a direct proof, because the write failed inside the 500 ms and the exit still ended clean.
- **INCONCLUSIVE:** `d` on XW-C is 0 and both `e_B` and `e_C` are under 3 (the race did not happen often enough to expose C), or either arm has under 8 graded exits, or XW-B itself shows `d` above 0 (then quote it: B has a second unclean path, and the arms are compared by `d` only).
- **Either arm:** a `critical_lines` entry that names a protocol refusal is a FAIL of that arm; quote it.

Report per arm: the median `bye_to_quit_ms` (B should be about 500), the count of `reconnect=` results by kind (`auto`, `verb`), and `tls_cannot_continue` and `ssl_decrypt_failed` after the ByeBye (expected 0, because the poll thread sleeps through the 500 ms).

### KW-B and KW-C. Fifteen-minute soak per arm

```bash
kw() { local A=$1; sess_open KW-$A || return 1
  first_session KW-$A || { close_run KW-$A; return 3; }
  music; both KW-$A-start
  soak_blocks 3
  both KW-$A-end; close_run KW-$A; }
kw B      # after XW-B, same install; kw C after XW-C
```

From `KW-<arm>.json`: `all.ssl` (whole file), and in `windows["KW-<arm>"]`: `ssl`, `quit_clean_true`, `quit_clean_false`, `tls_cannot_continue`, `ssl_decrypt_failed`, `enc_write_failed`, `audit_delta_changed`, `audit_truncated`, `audit_orphaned`, `audit_total`, `audit_established_B`, `video_discard`, `throughput_windows`, `throughput_max_gap_ms`, `rendered_median`, `span_ms`, `voice_start`, `mic_request`, `media_start_audio`; `phone.windows["KW-<arm>"].critical_error`; `send_failed_old_events` and `send_failed_old_survived_10s` (B).

- **Capture health first:** `throughput_windows` is at least 0.9 times `span_ms / 5000` on B. If not on B, the capture or the picture failed: rerun B once. A second miss makes KW INCONCLUSIVE.
- **PASS (KW-C), all of:**
  1. One session: `all.ssl` 1; in the window `ssl` 0 and both quitting counts 0.
  2. TLS zero list: `tls_cannot_continue` 0, `ssl_decrypt_failed` 0, `enc_write_failed` 0.
  3. **The new audit:** `audit_total` 0 and `video_discard` 0. If B also shows audit lines in its window, C may have at most B's count; say so.
  4. Picture: `throughput_windows` at least 0.95 times B's; `throughput_max_gap_ms` 15000 or less, or no more than B's plus 5000; `rendered_median` at least 0.9 times B's.
  5. Phone: `critical_error` in the soak window 0, or no higher than B's.
- **FAIL:** any condition above not met while B met it. For an audit failure, quote the first five `AapRead: DELTA_CHANGED on` lines in full (they carry `declaredTotal` and `observedTotal`), with timestamps. For any other, quote the first zero-list line and the 20 lines before it.
- Report the mic counts and `media_start_audio` (at least 1, or the soak had no music channel; say so).

### P. Post-handshake TLS records (from every capture, no run of its own)

For every session in the four json files, read `sessions[].first10s` and `sessions[].session`. Report per arm:

- B: the sums of `status_produced0_B` (VERBOSE, unthrottled), `zero_unwrap_B` plus `zero_unwrap_B_suppressed` (WARN, throttled), and `short0`.
- C: the sum of `produced0_M` (VERBOSE).
- Reachability: `all.status_any_B` above 0 on every B capture and `all.produced_any_M` above 0 on every C capture. Either at 0 means VERBOSE did not take effect on that capture, and its P counts are void.

**Record, no PASS or FAIL.** All zero on both arms means this phone sends no TLS-only record after the handshake, so the PR's control path (`AapTlsWriter.sendControl`) never ran on this rig. Say that in those words. Any count above zero: quote the first five lines with timestamps and the 10 lines after each, and say whether the session survived (`AapTransport quitting` within 30 s on C is then a FAIL of the control frame shape).

### F5. A baseline send failure the session outlived (from the B captures)

Read `send_failed_old_events` and `send_failed_old_survived_10s` in XW-B and KW-B. Report both sums. **`send_failed_old_survived_10s` of 1 or more refutes the review's finding 5**: that session kept rendering for more than 10 s after a failed write, which C would have ended at once. Quote the failed-write line and the last `Throughput over` line before the next quitting line. Failures inside an exit window, after a ByeBye, are expected and cannot survive.

### S. Resumption on a reconnect in the same process (from XW)

Read `reconnects_in_process` and `reconnects_same_session_id` in XW-B and XW-C. Quote every `SSL handshake complete.` line with its session id.

- **PASS:** where B shows `reconnects_same_session_id` of 2 or more, C's share (`same / in_process`) is at least B's share minus 0.2.
- **FAIL:** B resumes (2 or more) and C's share falls more than 0.2 below it.
- **INCONCLUSIVE:** B shows fewer than 2 repeated ids (this phone does not resume here).

## 7. Stop rules

- XW: 10 graded exits per arm, at most 14 attempts (`n`). An arm ends early after two operator misses in a row (`OPERATOR_MISSED`) or a reconnect that fails twice; the exits before it still count.
- KW: one session of 15 minutes. A B session that ends early is rerun once. A C session that ends early is not rerun: it is the result.
- No first session on an arm after one re-arm (`NO_FIRST_SESSION`): run `rig_preflight.sh D_HU:wifi,bt D_POCO:wifi,bt` once more, toggle D-HU's Bluetooth adapter off and on (rig-quirks `topics/bt.md`), and try once more. A second failure makes that arm's runs INCONCLUSIVE.
- Host thermal: per rig-quirks `topics/tooling.md`, through `apk_check`'s gate. If the host stays too hot for 30 minutes, stop; the remaining runs are UNTESTABLE (host thermal).

## 8. Do not run, and why this round is short

- **Round 1's stage U (USB) and stage O (the API 19 tablet).** Finding 1 is the same send path on every transport, and a socket exposes it most often. pr-1045 round 1 already covered the TLS read rewrite on old Android and USB. If the TLS pump later changes how a provider behaves, a separate short round will cover D-SAM.
- **Round 1's 30-minute soaks.** One 15-minute soak per arm gives the TLS zero list, the audit check and the picture rate. A longer soak adds time, not evidence.
- **The user-initiated exit (`ACTION_DISCONNECT`) and the ByeBye we send.** The pr-1066 round measured them. This round grades only the ByeBye the phone sends.

## 9. Report back

1. XW: `d` on each arm, `e_B`, `e_C`, graded exits per arm, and every defect exit's quoted lines. **These decide the merge.**
2. KW: one row per arm with every field named in KW, and the audit lines quoted if any printed.
3. P: the B sums and the C sum, with the reachability counts.
4. F5: `send_failed_old_survived_10s` summed over the B captures.
5. S: both shares, or why it is INCONCLUSIVE.

Results go in `pr-1067-tls-pump-round2-results.md` in the template's skeleton (section 7), one `## <RUN>` section per run id (R0, XW-B, XW-C, KW-B, KW-C) plus P, F5 and S, with `th_report` output in each run section. Write the file after every run. Captures and the json files go to the fork as release `rig-evidence-pr-1067-tls-pump`, one asset `pr-1067-tls-pump-round2-captures.zip`, cited with its sha256.

The phone lines are Gearhead's and do not grep in our tree: `Critical error`, `FATAL EXCEPTION`. The block below lists every head unit string the runs grep, for mechanical re-verification. Composed strings appear here as their literal source fragments.

```decisive-strings
AutomationReceiver: 
AutomationMarker: 
!!! RECEIVED BYEBYE REQUEST FROM PHONE !!!
Sending BYEYERESPONSE
Calling aapTransport.quit(clean=true)
AapTransport quitting (clean=
Quitting because ret < 0 (
AapProjectionActivity: Disconnected unexpectedly.
AapProjectionActivity: Finishing because state isUserExit=
AapService: session state 
AapTransport: encrypted write failed or incomplete
AapTransport: send failed (ret=
AapRead: TLS state cannot continue
SSL Decrypt failed
AapRead: %s on %s - %s%s
AapRead: fragment accounting established for 
AapVideo: discarding a 
SSL Decrypt: unwrap produced no application data
 more since the last report)
SSL Decrypt Status: 
SSL Decrypt: produced 
Decrypted payload too short: 
SSL handshake complete. Session id: 
SSL handshake complete. No session id (full handshake).
Handshake: SSL handshake complete. TS: 
Creating SSLContext with default provider
Throughput over 
Media Start Request 
Mic request: 
Voice Session Notification: START
Failed to join threads
Magic Garbage detected in header
MATCH! Starting AapService
```
