# pr-1067-tls-pump, round 1 brief: does a phone ByeBye still end the session cleanly once a failed write quits the transport

Phone-initiated exits under load, A/B, on Native AA (D-HU with D-POCO) and USB (D-MOTO as host, D-POCO as device), then a 30-minute soak on each transport and a 10-minute soak on the API 19 tablet. Every run captures the phone beside the head unit and runs at VERBOSE. About 6 hours plus builds. An operator must be present for stages W and U.

## 1. Build and baseline

Two APKs. Build each with `build_hur_cool.sh` (or `build_hur.sh` under the thermal gate, `--max-workers=2`, rig-quirks `topics/tooling.md`) and copy it out of `apks/` the moment it is built.

| Arm | Code | Identity | Gate |
|---|---|---|---|
| **B** (baseline) | upstream `main` | commit `145a0c762f0a87386ca63b54518ea5e861b290be` | JVM tests, 0 failures |
| **M** (candidate) | `main` plus the contributor's branch `codex/tls-post-handshake` (`719b69637eb0ffd7458f214d31b8db6caacdd523`, four commits), merged locally | tree `6747cb3777d1b90228ee03da222d1566c867ba10` | JVM tests, 0 failures; `TlsApplicationPumpTest`, `AapTlsWriterTest`, `AapSslSessionTest` and `TlsEmptyTailReaderTest` present and passing |

```bash
git fetch https://github.com/andreknieriem/open-headunit.git main
git fetch https://github.com/emotionbug/open-headunit.git codex/tls-post-handshake
git checkout -B arm-1067-B 145a0c762f0a87386ca63b54518ea5e861b290be
git checkout -B arm-1067-M 145a0c762f0a87386ca63b54518ea5e861b290be
git merge --no-edit 719b69637eb0ffd7458f214d31b8db6caacdd523
git rev-parse 'HEAD^{tree}'        # MUST print 6747cb3777d1b90228ee03da222d1566c867ba10
```

The branch names carry `1067` so they do not reset the `arm-B` and `arm-M` branches of the pr-1066 round. If the tree hash differs, do not build M and stop the round. A build or test failure stops the round. The four test classes above must not exist on B.

**Why a merged arm.** The branch is based on `2ca3b1f1`, eight commits behind `main`. None of the eight touches `AapTransport`, `AapSslContext`, the readers, `AapMessageIncoming` or `AapControl`, and the merge is clean. Four of them touch code this round grades, though: `a6979313` and `1f97d4d0` change `AapProjectionActivity`, which prints `Disconnected unexpectedly.`; `4c75aba3` and `46fd6218` change `AapService`, which prints the session state; `a70ba7e2` changes how `CommManager` joins a connect, which carries every reconnect in the resumption check. With the branch tip as the candidate, a difference in those lines could come from `main` and not from the change. M against B differs by the change alone, and B is the `main` the change would land on.

**Identity, per arm, on every head unit, before the first run on it.** Pull the installed APK and hash it locally (`apk_check`). Then count a string only M carries:

```bash
unzip -p $OUT/live.apk 'classes*.dex' | strings | grep -cF 'encrypted write failed or incomplete'     # B: 0, M: 1 or more
```

Also `send ACTION_QUERY_STATE` and record `commit` (B starts `145a0c76`; M is the local merge commit). A mismatch voids that arm's runs on that unit: reinstall once, then mark them UNTESTABLE.

## 2. What this is and why

The change replaces the single TLS `wrap` and `unwrap` with loops (`TlsApplicationPump`), gives each transport its own TLS engine (`newSession()`), and sends any TLS control record the engine produces after the handshake through a new writer (`AapTlsWriter`). Our review found it sound and found one blocking defect. The review is `~/projects/ohu-project/ohu-fixes-handoff/pr-1063-1067/pr-1067-review.md` on the authoring machine; its findings are summarised here.

**Finding 1, blocking, and the point of this round.** On M, the send thread calls `quit()` with `clean = false` when an encrypted write fails. `quit()` acts only for its first caller. When the phone sends a ByeBye, `AapControl.byebyeRequest` sends the response, sleeps 500 ms on the poll thread, and only then calls `quit(clean = true)`. If the phone closes the link inside those 500 ms and a media ack or a sensor message goes out, the write fails and the send thread quits first. The session then ends as a lost link: `AapProjectionActivity` shows the toast and the reconnecting overlay instead of finishing, `AapService` reports `link_lost`, and Auto mode starts a retry for a session the user ended. On USB the failed write is `bulkTransfer` returning -1 after the phone ends the accessory; on a socket it is EPIPE after the phone's FIN. On B the same failed write only logs `AapTransport: send failed (ret=` and the session still ends clean.

**What exposes it.** Only a ByeBye followed by a write failure inside the 500 ms. B shows how often that happens: every B exit with `AapTransport: send failed (ret=` between the ByeBye and the quitting line is an exit where M would have gone unclean. That count is the reachability number for the whole round.

**Second question: does the phone send post-handshake TLS records at all?** A TLS 1.2 session carries none in normal use, and pr-1045 round 1 recorded that Gearhead pins TLS 1.2. If the count is zero on both arms, the new control path never runs on this rig, and the review's finding 3 (the control frame shape) stays unmeasured. That is a result, not a failure.

**Third question, finding 5: does a write failure always end a session already?** M ends the session at the first failed write. The review argues that B was already dead after one, because TLS advances its sequence before the write. If a B session logs `AapTransport: send failed (ret=` and then keeps rendering for more than 10 s, that argument is wrong and M ends sessions B survives.

**Fourth: session resumption.** M gives each transport a new engine but shares one `SSLContext`, so a reconnect in the same process should still be able to resume. The session id on `SSL handshake complete. Session id:` shows it.

## 3. What is different about this round

- **The phone exit is a hand step (H1), with a reason.** No adb command and no app verb ends a session from the phone's side. A force-stop of Android Auto is not a ByeBye, and during projection the phone's own screen is a placeholder with nothing to script (audio-sink-jitter round 4). The lever that works is the one projection-raise round 1 found: in Android Auto's own UI on the head unit's screen, tap **Exit**, then **Finish later** if Android Auto asks. That sends `ByeByeRequest` with `Reason: USER_SELECTION`. The operator taps; the script cues each exit with an `OPERATOR_STEP` line and waits for the ByeBye in the log.
- **Other hand steps, and no others.** H2: unplug and replug D-POCO's OTG cable on D-MOTO and accept the system USB dialog without ticking "always" (stage U; a cable and a system dialog have no verb). H3: unlock D-MOTO if it shows a PIN (rig-quirks `units/D-MOTO.md`). H4, only if stage O cannot wake D-POCO: forget D-HU's Bluetooth radio on D-POCO, and pair it again after the round (rig-quirks `units/D-POCO.md`). **Who reads the cue:** the operator, through `OPERATOR_STEP` lines from `cue`. A cue with no ByeBye in the log within 240 s is repeated once. A second miss ends that run, and the exits before it still count.
- **The whole round runs at VERBOSE** (`log-level` 0), because M prints its zero-output decrypt only there, and B's unthrottled decrypt status line is VERBOSE too. Every count below says which level carries it. D-HU floods logcat at this level; the capture streams to a file, so a wrapped ring buffer does not matter, but every soak checks its `Throughput over` count against its length to prove the capture kept up.
- **The microphone trigger is not an `AutomationReceiver` verb.** No verb opens the assistant. The scripted route is the app's exported `RemoteControlReceiver` with `--es command voice`, as mic-uplink rounds 2 and 3 used. D-POCO's assistant could not reach its backend in mic-uplink round 1, so the microphone may never open on this phone. Mic counts are reported and never graded.
- **D-HU cannot host USB** (`host_connected=false`), so stage U uses D-MOTO as the head unit, as pr-1045 round 1 stage D and pr-1066 round 1 stage U do. Read template section 7b first. D-POCO's USB port is loose (pr-1045 round 1), so a moved cable re-enumerates it.
- **Video and music load.** Music plays on the phone; a swipe loop into the projected video (the standing exception, template section 7a) keeps the picture moving. The swipe stops before each exit cue, because the operator's tap and a scripted swipe on one screen would collide. Only one adb caller runs against a unit at a time (house rule 8).
- **Pre-registered outcomes.** P (post-handshake records) is expected to read zero on both arms. The resumption check is INCONCLUSIVE if B shows no repeated session id. XW or XU is INCONCLUSIVE if B shows fewer than 3 exposed exits (section 6). XU is UNTESTABLE with no operator.

## 4. Setup

Make `hur-wifi-test-scripts/pr-1067-tls-pump-round1/`. Copy `ohu_lib.sh`, `ohu_setkeys.py` and `ptr_lib.sh` into it from `hur-wifi-test-scripts/projection-teardown-and-relays-round3/`. Save `tp_lib.sh` and `tp_extract.py` (below) beside them. List all five in Setup notes. Run each stage as one script in the background, and watch `$OUT/hand-steps.log`; relay each new `OPERATOR_STEP` line to the operator at once.

For each stage, set the variables, source the files, and back up the unit's settings once:

```bash
source ./ohu_lib.sh; source ./ptr_lib.sh; source ./tp_lib.sh
prefs_cat > $OUT/settings-backup-$HU.xml; BASEXML=$OUT/settings-backup-$HU.xml
WANT_MD5=<md5 of the arm's APK>      # set before every arm; apk_check compares the installed APK with it
```

Install each arm with `adb -s $HU install -r -d <named apk>`, never with a script that picks the newest APK. After each install, read every key in section 4's table back (an install has wiped `settings.xml` before).

On D-HU, `stat` `/data/data/$PKG/shared_prefs` first and record the owner (rig-quirks `units/D-HU.md`). Record the Gearhead `versionName` on D-POCO once (`adb -s 4f4027e9 shell dumpsys package com.google.android.projection.gearhead | grep -m1 versionName`). Diff `settings.xml` against the backup at the start of each stage and state the delta in Setup notes.

**Read and quote, never write:** `use-aac-audio`, `audio-latency-multiplier`, `audio-queue-capacity` (the rig's deliberate worst case; never reset them), `enable-audio-sink` (must read `true`; if `false`, no music channel opens and the load is video only, so say so), `video-codec`, `resolutionId`.

**Settings keys**, written with the app stopped by `$PUT` (`hu_put` on D-HU, `tab_put` on D-MOTO and D-SAM), which reads them back. `ACTION_LOG_MARKER` is not gated on M (`AutomationCommandPolicy.CONFIGURING` lists only the settings and log verbs), so `allow-external-configuration` is not needed.

| Key | Stage W (D-HU) | Stage U (D-MOTO) | Stage O (D-SAM) |
|---|---|---|---|
| `log-level` | int `0` (VERBOSE) | int `0` | int `0` |
| `wifi-connection-mode` | int `3` | int `0` | int `3` |
| `connection-modes` | set `wifi` | set `usb` | set `wifi` |
| `onboarding-version` | int `2` | int `2` | int `2` |
| `native-driver-selection-mode` | int `0` | | int `0` |
| `use-libusb` | | bool `false` | |
| `screen-orientation` | | int `2` | int `2` |
| `video-profile-starvation-cap` | delete | delete | delete |

```bash
KEYS_W="int:log-level=0 int:wifi-connection-mode=3 set:connection-modes=wifi int:onboarding-version=2 int:native-driver-selection-mode=0 del:video-profile-starvation-cap"
KEYS_U="int:log-level=0 int:wifi-connection-mode=0 set:connection-modes=usb int:onboarding-version=2 bool:use-libusb=false int:screen-orientation=2 del:video-profile-starvation-cap"
KEYS_O="int:log-level=0 int:wifi-connection-mode=3 set:connection-modes=wifi int:onboarding-version=2 int:native-driver-selection-mode=0 int:screen-orientation=2 del:video-profile-starvation-cap"
```

**Verbs and commands.**

| Action | Command |
|---|---|
| state and identity | `send ACTION_QUERY_STATE` |
| marker | `mark <label>` (head unit) and `pmark <label>` (phone); `both <label>` does both |
| wireless re-arm | `send ACTION_START_WIRELESS_SCAN` (`arm_w` falls back to launching `$MAIN` if the reply holds `not allowed`) |
| USB check | `send ACTION_CHECK_USB` |
| raise the projection | `send ACTION_RAISE_PROJECTION` |
| end of run | `close_run <RUN>` (`ACTION_EXIT`, then force-stop) |
| music | `adb -s $PH shell input keyevent KEYCODE_MEDIA_PLAY`; on D-HU, the projected play control `adb -s $HU shell input tap 272 657` if the phone is not playing (rig-quirks `topics/audio.md`) |
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
# sess_open RUN : captures on, settings in; the stage's own bring-up follows
sess_open() { RUN=$1; CAP=$OUT/$RUN.logcat; apk_check || return 1; adb -s $HU shell am force-stop $PKG
  $PUT "$BASEXML" $KEYS; for t in CAR.GAL CAR.GAL.GAL.LITE CAR.GAL.SECURITY.LITE; do adb -s $PH shell setprop log.tag.$t VERBOSE; done
  pcap_start "$RUN"; cap_start; sleep 1; both $RUN-open; }
close_run() { both $1-close; sleep 1; pcap_stop; cap_stop; th_report $1; send ACTION_EXIT >/dev/null; sleep 3; adb -s $HU shell am force-stop $PKG
  python3 -I tp_extract.py "$CAP" "$PCAP" > $OUT/$1.json; }
music() { local p; adb -s $PH shell input keyevent KEYCODE_MEDIA_PLAY; sleep 8
  p=$(adb -s $PH shell dumpsys media_session | grep -c 'state=PLAYING')
  if [ "$p" = 0 ] && [ "$HU" = 27870808938846 ]; then adb -s $HU shell input tap 272 657; sleep 8; p=$(adb -s $PH shell dumpsys media_session | grep -c 'state=PLAYING'); fi
  echo "$(date +%T) $RUN playing=$p" >> $OUT/$RUN.music; }
# swipe_for SECS : drags the projected picture back and forth; the only adb caller on $HU while it runs
swipe_for() { local end=$((SECONDS+$1)) r; r=$(echo $SW | awk '{print $3,$4,$1,$2}')
  while [ $SECONDS -lt $end ]; do adb -s $HU shell input swipe $SW 350; sleep 0.8; adb -s $HU shell input swipe $r 350; sleep 0.8; done; }
arm_w() { local r; r=$(send ACTION_START_WIRELESS_SCAN | tr -d '\r'); echo "$r" | grep -q 'not allowed\|Exception' && adb -s $HU shell am start -n $MAIN >/dev/null; }
# picture : the projection is up and rendering, or it is raised once
picture() { local L=$1; waitfor 40 "$PICRE" $L || { send ACTION_RAISE_PROJECTION >/dev/null; waitfor 30 "$PICRE" $L; }; }
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
# recon_u ID : the phone may come back by itself; then the USB verb; then H2
recon_u() { local L R=auto; L=$(nl); both $1r-start
  if ! waitfor 30 "$SSLRE" $L; then R=verb; send ACTION_CHECK_USB >/dev/null
    if ! waitfor 30 "$SSLRE" $L; then R=plug
      cue "On D-MOTO: unplug D-POCO's OTG cable, wait 3 s, plug it back, accept the USB dialog without ticking always ($1)"
      if ! waitfor 180 "$SSLRE" $L; then send ACTION_CHECK_USB >/dev/null; waitfor 60 "$SSLRE" $L || R=FAIL; fi; fi; fi
  [ $R != FAIL ] && { picture $L || R=NOPIC; }
  echo "$1 reconnect=$R" | tee -a $OUT/$RUN.summary; both $1r-end; [ $R != FAIL ] && [ $R != NOPIC ]; }
# soak_blocks N : N blocks of 5 minutes; each is 270 s of swipes, a marker, the assistant, then music again
soak_blocks() { local n; for n in $(seq 1 $1); do swipe_for 270; mark $RUN-b$n; sleep 1
  adb -s $HU shell am broadcast -n $RCV -a com.android.music.musicservicecommand --es command voice >/dev/null; sleep 20
  adb -s $PH shell input keyevent KEYCODE_MEDIA_PLAY; sleep 8; done; }
```

`waitfor`, `nl`, `mark`, `send`, `hu_put`, `tab_put`, `MAIN`, `PKG` come from `ohu_lib.sh` and `ptr_lib.sh` as in the pr-1066 round. `cap_start` there takes a full capture at API 21 and up, and a tag-filtered capture that restarts itself below API 21 (D-SAM). If a script does not match the real line format, fix it, say so in Setup notes, and keep going.

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
FIXED = {
    'bye': BYE, 'unexpected': UNEXP, 'finish_clean': FINCLEAN, 'enc_write_failed': ENCW,
    'send_failed_old': SENDF, 'tls_cannot_continue': TLSX, 'ssl_decrypt_failed': DECF,
    'zero_unwrap_B': ZU_B, 'short0': SHORT0, 'produced0_M': P0_M,
    'status_any_B': 'SSL Decrypt Status: ', 'produced_any_M': 'SSL Decrypt: produced ',
    'magic_garbage': 'Magic Garbage detected in header', 'match_wake': 'MATCH! Starting AapService',
    'conscrypt_installed': 'Conscrypt installed as security provider',
    'ctx_conscrypt': 'Creating SSLContext with Conscrypt provider',
    'ctx_default': 'Creating SSLContext with default provider',
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


def counts(lines):
    c = {k: sum(1 for _, _, x in lines if v in x) for k, v in FIXED.items()}
    c['zero_unwrap_B_suppressed'] = sum(int(m.group(1)) for _, _, x in lines if ZU_B in x for m in [SUPP.search(x)] if m)
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
    # Other windows: <X>-start .. <X>-end (the soak windows)
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
                                                 'fatal_exception': sum('FATAL EXCEPTION' in l for l in seg)}
        res['phone'] = ph
    print(json.dumps(res, indent=1))


main()
```

## 5. The deciding lines

Head unit lines, checked with `grep -F` against the B tree (`145a0c76`) and the M tree (`6747cb37`). "Composed" means the source builds the printed text from a format; the printed form is what the extractor greps.

| Line | Source | Level | B | M | Used for |
|---|---|---|---|---|---|
| `!!! RECEIVED BYEBYE REQUEST FROM PHONE !!! Reason: ` | `AapControl.kt` | INFO | yes | yes | the exit landed |
| `Sending BYEYERESPONSE` (spelled so in source) | `AapControl.kt` | INFO | yes | yes | report |
| `Calling aapTransport.quit(clean=true)` | `AapControl.kt` | INFO | yes | yes | report: the clean call, 500 ms after the ByeBye |
| `AapTransport quitting (clean=` then `true)` or `false)` | `AapTransport.kt` | INFO | yes | yes | **the verdict**: the first one after the ByeBye |
| `Quitting because ret < 0 (` | `AapTransport.kt` | INFO | yes | yes | report |
| `AapProjectionActivity: Disconnected unexpectedly.` | `AapProjectionActivity.kt` | WARN | yes | yes | defect |
| `AapProjectionActivity: Finishing because state isUserExit=false, isClean=true` (composed) | `AapProjectionActivity.kt` | INFO | yes | yes | report |
| `AapProjectionActivity: Unexpected disconnect. Showing reconnecting overlay` | `AapProjectionActivity.kt` | INFO | yes | yes | report |
| `AapService: session state disconnected (phone_left)` or `(link_lost)` (composed) | `AapService.kt` | INFO | yes | yes | defect when `link_lost` |
| `AapTransport: encrypted write failed or incomplete` | `AapTransport.kt` | WARN | no | yes | M's failed write |
| `AapTransport: send failed (ret=` | `AapTransport.kt` | WARN | yes | no | B's failed write: exposure and finding 5 |
| `AapRead: TLS state cannot continue` | `AapReadSingleMessage.kt`, `AapReadMultipleMessages.kt` | ERROR | no | yes | soak zero list |
| `SSL Decrypt failed` | `AapSslContext.kt` | ERROR | yes | yes | soak zero list |
| `SSL Decrypt: unwrap produced no application data` (throttled; may end `(and N more since the last report)`) | `AapSslContext.kt` | WARN | yes | no | P on B |
| `SSL Decrypt Status: OK, Produced: 0, Consumed: ` (composed) | `AapSslContext.kt` | VERBOSE guard | yes | no | P on B, unthrottled |
| `Decrypted payload too short: 0  chan: ` (composed) | `AapMessageIncoming.kt` | ERROR | yes | yes, but M returns before it for an empty first frame | P on B |
| `SSL Decrypt: produced 0, consumed ` (composed) | `AapSslContext.kt` | VERBOSE guard | no | yes | P on M |
| `SSL handshake complete. Session id: ` / `SSL handshake complete. No session id (full handshake).` | `AapSslContext.kt` | INFO | yes | yes | one per session; resumption |
| `Handshake: SSL handshake complete. TS: ` | `AapTransport.kt` | DEBUG | yes | yes | never counted: at VERBOSE it doubles a plain `SSL handshake complete` grep |
| `Conscrypt installed as security provider` | `ConscryptInitializer.kt` | INFO, below API 21 only | yes | yes | stage O provider |
| `Creating SSLContext with Conscrypt provider` / `Creating SSLContext with default provider` | `AapSslContext.kt` | DEBUG | yes | yes | provider, every stage |
| `Throughput over ` (regex `Throughput over [0-9]+ms: rendered=[1-9]`) | `VideoDecoder.kt` | INFO | yes | yes | load gate, soak cadence |
| `Media Start Request AUDIO: ` (composed) | `AapControl.kt` | INFO | yes | yes | music load |
| `Voice Session Notification: START`, `Mic request: ` | `AapControl.kt` | INFO | yes | yes | mic, report only |
| `Failed to join threads` | `AapTransport.kt` | ERROR | yes | yes | report |
| `Magic Garbage detected in header`, `MATCH! Starting AapService` | readers, receiver | | yes | yes | discard rules |
| `WifiLauncher: Initializing WiFi Mode: ` (`NATIVE`) | `WifiLauncherManager.kt` | INFO | yes | yes | stage W and O arm check |
| `Found device already in accessory mode`, `Switching USB device to accessory mode` | USB launcher | INFO | yes | yes | stage U plug |
| `AutomationReceiver: `, `AutomationMarker: ` | automation | INFO, WARN | yes | yes | verbs landed, windows |

Phone lines (Gearhead, not in our tree), in the phone capture: `Critical error` (CAR.SERVICE, a session-ending refusal) and `FATAL EXCEPTION`. Markers on the phone are `OHURIG` lines from `pmark`.

## 6. Stages and runs

Order: R0, stage W (B, then M), stage U (B, then M), stage O (B, then M). Run ids carry the arm. **The point of the round is XW and XU**, finding 1 on a socket and on USB. P, F5 and S are read from the same captures and need no runs of their own.

Between runs, follow the template's section 3a reset: `close_run` ends the run; restore the unit's backup with `$PUT`; write only the next run's keys.

### R0. Gate

1. JVM tests on both trees, 0 failures each. The four M-only test classes exist and pass on M, and do not exist on B. Record both counts.
2. Both APKs built and copied out; two different md5s.
3. On every head unit, after each install: the identity check in section 1 prints the expected count, and `ACTION_QUERY_STATE` returns the expected `commit`.

**PASS:** all three. A build or test failure, or a tree hash other than `6747cb37...`, stops the round.

### Stage W. Native AA over WiFi Direct, D-HU with D-POCO

Cabling: D-HU and D-POCO on USB adb. Before the stage: D-MOTO's and D-SAM's Bluetooth off, and our app stopped on both (`send ACTION_EXIT`, then force-stop), because D-HU serves one hands-free link. D-POCO bonded to D-HU, its screen at home (`adb -s 4f4027e9 shell dumpsys window | grep mCurrentFocus`; `adb -s 4f4027e9 shell input keyevent KEYCODE_HOME` if not).

```bash
HU=27870808938846; PH=4f4027e9; PUT=hu_put; KEYS=$KEYS_W; SW="500 400 1000 300"; HUNAME="D-HU (the head unit)"
```

#### XW-B and XW-M. Ten phone exits per arm (**the point of the round**)

```bash
xw() { local A=$1 n=0 g=0 L; sess_open XW-$A || return 1
  L=$(nl); adb -s $HU shell am start -n $MAIN >/dev/null
  waitfor 150 "$SSLRE" $L || { arm_w; waitfor 150 "$SSLRE" $L || { echo "NO_FIRST_SESSION XW-$A" | tee -a $OUT/XW-$A.summary; close_run XW-$A; return 3; }; }
  picture $L; send ACTION_QUERY_STATE > $OUT/XW-$A.state
  while [ $g -lt 10 ] && [ $n -lt 14 ]; do n=$((n+1))
    exit_cycle XW-$A-x$n || break
    g=$((g+1)); sleep 2
    recon_w XW-$A-x$n || { sleep 5; recon_w XW-$A-x$n-again || break; }
  done
  close_run XW-$A; }
xw B      # then: install the M apk; WANT_MD5=<M md5>; identity check; xw M
```

`g` counts attempts that reached a ByeBye; the grader drops any exit the discard rules void, so up to 4 extra attempts are allowed (`n` stops at 14).

**Discard rules, per exit (void and do not grade):** the exit window `<ID>-go` to `<ID>-done` holds `Magic Garbage detected in header`, or an SSL line before the ByeBye (`ssl_before_bye` above 0); or `LOAD_FAIL <ID>` was printed. A `MATCH! Starting AapService` inside the window is reported, not voided (the phone's own reconnect can raise it after a ByeBye).

Per exit, from `XW-<arm>.json` `exits[]`: `bye`, `first_quit_clean`, `bye_to_quit_ms`, `enc_write_failed_bye_to_quit` (M), `send_failed_old_bye_to_quit` (B), `unexpected`, `finish_clean`, `state_reason`, `tls_cannot_continue`, `ssl_decrypt_failed`, `defect`. From `phone.windows[<ID>]`: `critical_error`.

**Grade, per arm.** A graded exit has `bye` 1 and passed the discard rules. `d` is the number of graded exits with `defect` 1 (the first quitting line after the ByeBye reads `clean=false`, or `Disconnected unexpectedly.`, or `state_reason` `link_lost`). On B, `e_B` is the number of graded exits with `send_failed_old_bye_to_quit` of 1 or more: each is an exit where the write failed inside the 500 ms, so M would have quit unclean.

- **FAIL (finding 1 confirmed):** `d` on XW-M is 1 or more. Quote, for each defect exit, the ByeBye line, every `AapTransport: encrypted write failed or incomplete` line, the first quitting line and the session state line, with timestamps.
- **PASS:** `d` on XW-M is 0, XW-M has 8 or more graded exits, `d` on XW-B is 0, `e_B` on XW-B is 3 or more, and phone `critical_error` is 0 in every XW-M exit window (or no higher than in XW-B's).
- **INCONCLUSIVE:** `d` on XW-M is 0 and `e_B` is under 3 (the race the finding needs did not happen often enough on B to expose M), or either arm has under 8 graded exits, or XW-B itself shows `d` above 0 (then quote it: B has a second unclean path, and the arms are compared by `d` only).
- **What a PASS looks like if the change did nothing:** the identity check catches a B build under the M name. What it looks like if the race never happens: `e_B` 0, which is why `e_B` is a PASS condition and not a report.

Report per arm the median `bye_to_quit_ms` (B should be about 500), the count of `reconnect=` results by kind (`auto`, `verb`), and `tls_cannot_continue` and `ssl_decrypt_failed` after the ByeBye (finding 6: a phone close_notify on M prints both; expected 0 here, because the poll thread sleeps through the 500 ms).

#### KW-B and KW-M. Thirty-minute soak per arm

```bash
kw() { local A=$1 L; sess_open KW-$A || return 1
  L=$(nl); adb -s $HU shell am start -n $MAIN >/dev/null
  waitfor 150 "$SSLRE" $L || { arm_w; waitfor 150 "$SSLRE" $L || { echo "NO_FIRST_SESSION KW-$A" | tee -a $OUT/KW-$A.summary; close_run KW-$A; return 3; }; }
  picture $L; music; both KW-$A-start
  soak_blocks 6
  both KW-$A-end; close_run KW-$A; }
kw B      # after XW-B, same install; kw M after XW-M
```

From `KW-<arm>.json`: `all.ssl` (whole file), and in `windows["KW-<arm>"]`: `ssl`, `quit_clean_true`, `quit_clean_false`, `tls_cannot_continue`, `ssl_decrypt_failed`, `enc_write_failed`, `throughput_windows`, `throughput_max_gap_ms`, `rendered_median`, `span_ms`, `voice_start`, `mic_request`, `media_start_audio`; `phone.windows["KW-<arm>"].critical_error`; `send_failed_old_events` and `send_failed_old_survived_10s` (B).

- **Capture health first:** `throughput_windows` is at least 0.9 times `span_ms / 5000` on B. If not on B, the capture or the picture failed: rerun B once; a second miss makes KW INCONCLUSIVE.
- **PASS (KW-M), all of:** `all.ssl` 1; in the window `ssl` 0 and both quitting counts 0 (one session, no end, no reconnect); `tls_cannot_continue` 0, `ssl_decrypt_failed` 0, `enc_write_failed` 0; `throughput_windows` at least 0.95 times B's; `throughput_max_gap_ms` no more than 15000, or no more than B's plus 5000; phone `critical_error` 0 (or no higher than B's).
- **FAIL:** any condition above not met while B met it. Quote the first zero-list line with its timestamp and the 20 lines before it.
- Report `rendered_median` (M against B), the mic counts, and `media_start_audio` (at least 1, or the soak had no music channel; say so).

### Stage U. USB, D-MOTO as host with D-POCO over OTG

Read template section 7b. Record `dumpsys usb` on D-MOTO before the stage. Put both phones on wireless adb (`adb tcpip 5555`), so D-MOTO's port is free for the OTG cable. Before the stage: our app stopped on D-HU and D-SAM (`send ACTION_EXIT`, then force-stop), so nothing pokes D-POCO. Check D-MOTO's lock state (`dumpsys window | grep -i keyguard`); if it shows a PIN, cue H3. Our APK on D-MOTO is installed per arm like any head unit.

```bash
HU=<D-MOTO ip:5555>; PH=<D-POCO ip:5555>; PUT=tab_put; KEYS=$KEYS_U; HUNAME="D-MOTO (the USB head unit)"
read W H < <(adb -s $HU shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1 | tr x ' '); [ $W -lt $H ] && { t=$W; W=$H; H=$t; }
SW="$((W*35/100)) $((H*55/100)) $((W*70/100)) $((H*40/100))"
plug_first() { local L; L=$(nl); cue "On D-MOTO: plug D-POCO's OTG cable in, accept the USB dialog without ticking always ($1)"
  waitfor 180 "$SSLRE" $L || { send ACTION_CHECK_USB >/dev/null; waitfor 90 "$SSLRE" $L; } && picture $L; }
```

#### XU-B and XU-M. Ten phone exits per arm (**the point of the round**)

```bash
xu() { local A=$1 n=0 g=0; sess_open XU-$A || return 1
  adb -s $HU shell am start -n $MAIN >/dev/null; sleep 5
  plug_first XU-$A || { echo "NO_FIRST_SESSION XU-$A" | tee -a $OUT/XU-$A.summary; close_run XU-$A; return 3; }
  send ACTION_QUERY_STATE > $OUT/XU-$A.state
  while [ $g -lt 10 ] && [ $n -lt 14 ]; do n=$((n+1))
    exit_cycle XU-$A-x$n || break
    g=$((g+1)); sleep 2
    recon_u XU-$A-x$n || break
  done
  close_run XU-$A; }
xu B      # then install M on D-MOTO, identity check, xu M
```

Discard rules, extract fields and grade as XW, applied to XU. Report how each reconnect came back (`auto`, `verb`, `plug`): a phone that leaves the accessory after its ByeBye needs H2 every time, and that is a rig fact, not a defect. **UNTESTABLE:** no operator. **INCONCLUSIVE** as XW, or fewer than 2 sessions formed on XU-B.

#### KU-B and KU-M. Thirty-minute soak per arm

```bash
ku() { local A=$1; sess_open KU-$A || return 1
  adb -s $HU shell am start -n $MAIN >/dev/null; sleep 5
  plug_first KU-$A || { echo "NO_FIRST_SESSION KU-$A" | tee -a $OUT/KU-$A.summary; close_run KU-$A; return 3; }
  music; both KU-$A-start; soak_blocks 6; both KU-$A-end; close_run KU-$A; }
ku B      # after XU-B; ku M after XU-M
```

Conditions as KW, applied to KU. If the projection on D-MOTO will not hold the foreground (`picture` fails twice), the video half is INCONCLUSIVE and the TLS zero list and the one-session condition are still graded.

### Stage O. The API 19 tablet, D-SAM with D-POCO, Native AA

Read rig-quirks `units/D-SAM.md` and `units/D-SAM-and-D-HP.md`. Charge D-SAM from a separate supply and quote `dumpsys battery`. Quote `adb -s 30041c35642d2200 shell date` against the host's `date`. `am` rejects `-p` there, and `send` uses `-n` only. D-HU's and D-MOTO's Bluetooth off for the stage. A clean D-SAM session prints two `createGroup SUCCESS` before SSL; that is not contamination here. D-SAM can take up to 3.5 minutes to form a session.

```bash
HU=30041c35642d2200; PH=4f4027e9; PUT=tab_put; KEYS=$KEYS_O
ko() { local A=$1 k=0 L; while [ $k -lt 3 ]; do k=$((k+1)); sess_open KO-$A || return 1
    L=$(nl); adb -s $HU shell am start -n $MAIN >/dev/null
    if waitfor 330 "$SSLRE" $L; then break; fi
    echo "NO_SESSION KO-$A try $k" | tee -a $OUT/KO-$A.summary; close_run KO-$A; done
  [ $k -le 3 ] && grep -aqE "$SSLRE" $CAP || return 3
  music; both KO-$A-start; sleep 600; both KO-$A-end; close_run KO-$A; }
ko B      # then install M on D-SAM, identity check, ko M
```

No swipe loop and no assistant on D-SAM: it is a weak unit, and the point is the TLS provider and engine on old Android. If three tries on B form no session and the head unit log shows the "has never opened the Android Auto channel" warning, cue H4 (forget D-HU's radio on D-POCO) and try once more. If D-SAM still cannot form a session, run the same 10 minutes on D-HP in Headunit Server mode instead, with D-MOTO's head unit server turned on by hand (a phone-side developer toggle, no verb), as pr-1045 round 1 stage C did, and say so.

From `KO-<arm>.json`: `all.conscrypt_installed`, `all.ctx_conscrypt`, `all.ctx_default`, and the KW zero list and one-session conditions in `windows["KO-<arm>"]`.

- **Provider (record):** which line printed. Expected on D-SAM: `Conscrypt installed as security provider` 1 and `Creating SSLContext with Conscrypt provider` 1, on both arms. Expected on D-HU and D-MOTO (API 21 and up, from the W and U captures): `Creating SSLContext with default provider`. A different provider on M than on B is a FAIL.
- **PASS (KO-M):** the provider matches B's; `all.ssl` 1; in the window `ssl` 0, both quitting counts 0, `tls_cannot_continue` 0, `ssl_decrypt_failed` 0, `enc_write_failed` 0; `throughput_windows` at least 0.9 times B's; phone `critical_error` 0 (or no higher than B's).
- **INCONCLUSIVE:** B never forms a session on D-SAM or D-HP.

### P. Post-handshake TLS records (from every capture, no run of its own)

For every session in every X, K and O json, read `sessions[].first10s` and `sessions[].session`. Report per arm and per stage:

- B: the sums of `status_produced0_B` (VERBOSE, unthrottled), `zero_unwrap_B` plus `zero_unwrap_B_suppressed` (WARN, throttled), and `short0`.
- M: the sum of `produced0_M` (VERBOSE).
- Reachability: `all.status_any_B` above 0 on every B capture and `all.produced_any_M` above 0 on every M capture. Either at 0 means VERBOSE did not take effect on that capture, and its P counts are void.

**Record, no PASS or FAIL.** All zero on both arms means this phone sends no TLS-only record after the handshake, so the PR's control path (`AapTlsWriter.sendControl`) never ran on this rig. Say that in those words. Any count above zero: quote the first five lines with timestamps and the 10 lines after each, and say whether the session survived (`AapTransport quitting` within 30 s on M is then a FAIL of the control frame shape, finding 3).

### F5. A baseline send failure the session outlived (from every B capture)

Read `send_failed_old_events` and `send_failed_old_survived_10s` in every B json (XW, KW, XU, KU, KO). Report both sums. **`send_failed_old_survived_10s` of 1 or more refutes the review's finding 5**: that session kept rendering for more than 10 s after a failed write, which M would have ended at once. Quote the failed-write line and the last `Throughput over` line before the next quitting line. Failures inside an exit window, after a ByeBye, are expected and cannot survive, because the session ends 500 ms later.

### S. Resumption on a reconnect in the same process (from XW and XU)

Read `reconnects_in_process` and `reconnects_same_session_id` in XW-B, XW-M, XU-B and XU-M. Quote every `SSL handshake complete.` line with its session id.

- **PASS:** on each transport where B shows `reconnects_same_session_id` of 2 or more, M's share (`same / in_process`) is at least B's share minus 0.2.
- **FAIL:** B resumes (2 or more) and M's share falls more than 0.2 below it.
- **INCONCLUSIVE:** B shows fewer than 2 repeated ids on that transport (this phone or provider does not resume here, so there is nothing to keep).

## 7. Stop rules

- X runs: 10 graded exits per arm, at most 14 attempts (`n`). An arm ends early after two operator misses in a row (`OPERATOR_MISSED`) or a reconnect that fails twice; the exits before it still count.
- K runs: one session of 30 minutes (KO: 10 minutes). A B session that ends early is rerun once. An M session that ends early is not rerun: it is the result.
- Host thermal: per rig-quirks `topics/tooling.md`, through `apk_check`'s gate. A stage that stays too hot for 30 minutes stops, and its remaining runs are UNTESTABLE (host thermal).

## 8. Do not re-run

- The framing, TLS read and reassembly rewrite on old Android and USB: pr-1045 round 1 (its O1 already recorded 0 `unwrap produced no application data` on D-SAM on the then baseline).
- The user-initiated exit (`ACTION_DISCONNECT`) and the ByeBye we send: the pr-1066 round. This round grades only the ByeBye the phone sends.

## 9. Report back

1. Per transport (XW, XU): `d` on each arm, `e_B`, graded exits per arm, and every defect exit's quoted lines. **These decide the merge.**
2. P: the four B sums and the M sum per stage, with the reachability counts.
3. F5: `send_failed_old_survived_10s` summed over every B capture.
4. The soak table: one row per K run with every field named in KW, the provider lines, and S per transport.

Results go in `pr-1067-tls-pump-round1-results.md` in the template's skeleton (section 7), one `## <RUN>` section per run id (R0, XW-B, XW-M, KW-B, KW-M, XU-B, XU-M, KU-B, KU-M, KO-B, KO-M) plus P, F5 and S, with `th_report` output in each run section. Write the file after every run. Captures and the json files go to the fork as release `rig-evidence-pr-1067-tls-pump`, one asset `pr-1067-tls-pump-round1-captures.zip`, cited with its sha256.

The phone lines are Gearhead's and do not grep in our tree: `Critical error`, `FATAL EXCEPTION`. The block below lists every head unit string the runs grep, for mechanical re-verification. Strings marked composed in section 5 appear here as their literal source fragments.

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
AapProjectionActivity: Unexpected disconnect. Showing reconnecting overlay
AapService: session state 
AapTransport: encrypted write failed or incomplete
AapTransport: send failed (ret=
AapRead: TLS state cannot continue
SSL Decrypt failed
SSL Decrypt: unwrap produced no application data
 more since the last report)
SSL Decrypt Status: 
SSL Decrypt: produced 
Decrypted payload too short: 
SSL handshake complete. Session id: 
SSL handshake complete. No session id (full handshake).
Handshake: SSL handshake complete. TS: 
Conscrypt installed as security provider
Creating SSLContext with Conscrypt provider
Creating SSLContext with default provider
Throughput over 
Media Start Request 
Mic request: 
Voice Session Notification: START
Failed to join threads
Magic Garbage detected in header
MATCH! Starting AapService
WifiLauncher: Initializing WiFi Mode: 
Found device already in accessory mode
Switching USB device to accessory mode
createGroup SUCCESS
```
