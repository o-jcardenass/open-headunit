# pr-1063-restart-audio, round 1 brief: the restart-audio verb with the outside contributor's change merged onto main, on D-HP with D-POCO

A small round: one live session per arm on D-HP, one no-session check, and a sibling-verb probe. About 45 minutes plus builds.

## 1. Build and baseline

Two APKs. Build each with `build_hur_cool.sh` (or `build_hur.sh` under the thermal gate, `--max-workers=2`, template section 7a tooling) and copy it out of `apks/` the moment it is built. Name each by its arm.

| Arm | Code | Identity | Gate |
|---|---|---|---|
| **B** (baseline) | upstream `main` | commit `145a0c762f0a87386ca63b54518ea5e861b290be` | JVM tests, 0 failures |
| **M** (candidate) | `main` plus the contributor's branch `codex/automation-audio-restart` (`421514779f442206f622b33759af9ef6bfd99e07`), merged locally | tree `6e0ac4bfdff0d0a18db17a5677f05eec9e950a11` | JVM tests, 0 failures, `AutomationCommandPolicyTest` present |

```bash
git fetch https://github.com/andreknieriem/open-headunit.git main
git fetch https://github.com/emotionbug/open-headunit.git codex/automation-audio-restart
git checkout -B arm-B 145a0c762f0a87386ca63b54518ea5e861b290be
git checkout -B arm-M 145a0c762f0a87386ca63b54518ea5e861b290be
git merge --no-edit 421514779f442206f622b33759af9ef6bfd99e07
git rev-parse 'HEAD^{tree}'        # MUST print 6e0ac4bfdff0d0a18db17a5677f05eec9e950a11
git rev-parse HEAD                 # record it: M's ACTION_QUERY_STATE commit must start with it
```

If the tree hash differs, do not build M and stop the round. If M fails to compile, stop the round and quote the compiler output: that is the finding. The Kotlin compiler is expected to warn about a duplicate `when` branch on M (section 2); a warning is not a failure. Run `run_unit_tests.sh` on M and count from the JUnit XML.

**Why M and not the branch tip alone.** The branch is based on `2ca3b1f1`, eight commits behind `main`. One of those eight, `4c75aba3` ("AapService: handle the three relayed automation verbs"), already fixes the same defect on `main`, for all three verbs. So the branch tip alone measures nothing new, and the only question left is what the merge does. History was not rewritten.

**Identity, before every run on an arm.** M adds no new symbol, so identity is the md5 plus the build stamp. Pull the installed APK and hash it locally (`apk_check`, section 4). Then `send ACTION_QUERY_STATE` and record `commit`: B must start `145a0c76`, M must start with the merge commit recorded above. A mismatch voids that arm's runs; reinstall once, then mark them UNTESTABLE.

## 2. What this is and why

`AutomationReceiver` relays `ACTION_RESTART_AUDIO` (and the legacy `aap.action.RESTART_AUDIO`) as a service start. On `2ca3b1f1`, `AapService.onStartCommand` had no branch for it, so the verb answered `ok` and did nothing. pr-1047-session-reconnect round 1 (C2.0) and projection-teardown-and-relays round 1 (R5-B) measured that. The contributor's change adds one branch at the top of the `when`: `ACTION_RESTART_AUDIO -> commManager.restartAudio()`.

`main` already has a branch for it lower in the same `when` (`ACTION_RESTART_AUDIO -> restartAudio()`), which logs `AapService: Received request to restart audio` and then calls the same function. Projection-teardown-and-relays round 1 measured that code (R5-C, R5P-C). On M both branches exist, and Kotlin takes the first. So on M the service path restarts the tracks but no longer prints the `AapService:` line. The receiver path (the in-projection Quick Settings) is not changed and still prints it.

What the round decides: on M, the verb still restarts audio during a live session with no session loss, it is safe with no session, and the two sibling verbs still work.

## 3. What is different about this round

- **The unit is D-HP, not D-HU.** D-HU has no speaker (template section 7a, audio), and D-HP does. D-HP runs Headunit Server mode only, against D-POCO's head unit server. This is the pairing projection-teardown-and-relays round 3 used.
- **D-HP runs AAC** (`use-aac-audio` read `true` in pr-1045-framing round 1). The restart is proved from AAC lines, which exist only on that path. Read the key back at the start. If it is `false`, write `true`: that is the rig's deliberate worst case (memory `rig-audio-settings-are-a-deliberate-worst-case`), not a reset. Do not touch `audio-latency-multiplier` or `audio-queue-capacity`.
- **D-HP's capture is tag-filtered and restarts itself** (`cap_start` in `ptr_lib.sh` does this below API 21).
- **Hand steps, with reasons.** H1: start the head unit server on D-POCO if it is down (`srv_restart`, a UI-only developer toggle in Android Auto). H2: the operator listens to D-HP at each restart cue (a speaker has no adb read). H2 is reported and never graded. If no operator is present, skip H2 and say so.
- **No tap on the app anywhere.** HOME is `input keyevent 3`, an OS step.

## 4. Setup

Make `hur-wifi-test-scripts/pr-1063-restart-audio-round1/`. Copy `ohu_lib.sh`, `ohu_setkeys.py` and `ptr_lib.sh` into it from `hur-wifi-test-scripts/projection-teardown-and-relays-round3/`. List them in Setup notes. Then, in that folder:

```bash
HU=CNU350BGBJ; PH=4f4027e9; OTHER=; PUT=tab_put; OUT=$PWD
source ./ohu_lib.sh; source ./ptr_lib.sh
adb -s $HU shell run-as $PKG cat shared_prefs/settings.xml > $OUT/settings-backup-HP.xml; BASEXML=$OUT/settings-backup-HP.xml
grep -oE '(use-aac-audio|audio-latency-multiplier|audio-queue-capacity)[^/]*' $BASEXML     # record in Setup notes
PHIP=$(adb -s $PH shell ip -4 addr show wlan0 | grep -o 'inet [0-9.]*' | cut -d' ' -f2); echo PHIP=$PHIP
adb -s $PH shell dumpsys package com.google.android.projection.gearhead | grep -m1 versionName   # record
cat > rr_lib.sh <<'EOF'
# rr_lib.sh : source after ohu_lib.sh and ptr_lib.sh
pmark() { adb -s "$PH" shell log -t OHURIG "$1"; }
both()  { mark "$1"; pmark "$1"; }
# hseg A B / pseg A B : head unit / phone lines from marker A to marker B
hseg() { awk -v a="$1" -v b="$2" '/AutomationMarker:/&&$NF==a{on=1} on{print} on&&/AutomationMarker:/&&$NF==b{exit}' "$CAP"; }
pseg() { awk -v a="$1" -v b="$2" '/OHURIG/&&$NF==a{on=1} on{print} on&&/OHURIG/&&$NF==b{exit}' "$PCAP"; }
c() { grep -acF -- "$1"; }
rendered() { grep -acE 'Throughput over [0-9]+ms: rendered=[1-9]'; }
cue() { echo "$(date +%T) OPERATOR_STEP $1"; printf '\a'; echo "$(date +%T) $1" >> "$OUT/hand-steps.log"; }
EOF
source ./rr_lib.sh
WANT_MD5=<md5 of the arm's APK>      # set before each arm; apk_check compares the installed APK with it
```

**Settings keys** (written with `tab_put` and the app stopped; `tab_put` reads them back):

| Key | Value | Runs |
|---|---|---|
| `wifi-connection-mode` | int `1` (Headunit Server) | R1, R3 |
| `wifi-connection-mode` | int `0` (manual: nothing connects on its own) | R2 |
| `log-level` | int `2` (INFO) | all |
| `onboarding-version` | int `2` | all |
| `connection-modes` | set `wifi` | all |
| `use-aac-audio` | boolean `true`, only if the backup does not already hold it | all |
| `video-profile-starvation-cap` | delete | all |

`KEYS="int:wifi-connection-mode=1 int:log-level=2 int:onboarding-version=2 set:connection-modes=wifi del:video-profile-starvation-cap"` (append `bool:use-aac-audio=true` only if the backup does not hold it).

**Verbs.** `send` prefixes `com.andrerinas.openheadunit.`, so the legacy spelling is `send aap.action.RESTART_AUDIO`.

| Action | Command |
|---|---|
| restart audio | `send ACTION_RESTART_AUDIO`, then `send aap.action.RESTART_AUDIO` |
| refresh sensors | `send ACTION_REFRESH_SENSORS`, then `send aap.action.REFRESH_SENSORS` |
| raise the projection | `send ACTION_RAISE_PROJECTION`, then `send aap.action.RAISE_PROJECTION` |
| connect fallback | `send ACTION_CONNECT --es ip $PHIP` |
| end of run | `send ACTION_EXIT`, `sleep 3`, `adb -s $HU shell am force-stop $PKG` |
| state | `send ACTION_QUERY_STATE` |

Every verb must print `AutomationReceiver: <full action>` in the capture. A verb with no such line never landed: that window is void, not a FAIL.

## 5. The deciding lines

All INFO unless marked, checked with `grep -F` against the B and M trees. Counted in `$CAP` (head unit) or `$PCAP` (phone), inside the named marker window.

| Line | Source | B | M |
|---|---|---|---|
| `AutomationReceiver: com.andrerinas.openheadunit.ACTION_RESTART_AUDIO` (composed: `AutomationReceiver: ` plus the action) | `AutomationReceiver.kt` | present | present |
| `AapService: Received request to restart audio` | `AapService.kt` | present | present (receiver path only) |
| `AapAudio: Restarting all audio tracks` | `AapAudio.kt` | present | present |
| `AAC Decoder started for` | `AudioTrackWrapper.kt`, a track rebuilt | present | present |
| `audio sink AUDIO over` (composed: `audio sink ` plus the channel) | `AudioSinkHealthMonitor.kt`, one per 5 s while the AAC media sink is fed | present | present |
| `Media Start Request AUDIO` (composed) | `AapControl.kt`, the media channel opened | present | present |
| `AapService: Received request to refresh all sensors` | `AapService.kt` | present | present |
| `NightMode update: ` | `AapService.kt` | present | present |
| `AapService: raising the projection by ` | `AapService.kt` | present | present |
| `AapService: raise projection ignored, no session` | `AapService.kt` | present | present |
| `AapProjectionActivity: onResume` | `AapProjectionActivity.kt` | present | present |
| `SSL handshake complete` | `AapSslContext.kt` | present | present |
| `Throughput over ` (regex `Throughput over [0-9]+ms: rendered=[1-9]`) | `VideoDecoder.kt` | present | present |
| `AapTransport quitting (clean=` | `AapTransport.kt`, a session ended | present | present |
| `WifiLauncher: Initializing WiFi Mode: ` | `WifiLauncherManager.kt` | present | present |
| `FATAL EXCEPTION` | Android | n/a | n/a |
| phone: `Critical error` | Gearhead | n/a | n/a |
| phone: `Head unit disconnected` | Gearhead | n/a | n/a |
| phone: `state=PLAYING` | `dumpsys media_session` | n/a | n/a |

## 6. Runs

Order: R1-B, then install M, then R1-M (with R3 inside it), then R2-M. **The point of the round is R1-M.**

### Session open, used by R1

```bash
open_hs() { RUN=$1; CAP=$OUT/$RUN.logcat; apk_check || return 1
  listening || srv_restart "$RUN" || return 2          # H1 only when the server is down
  adb -s $HU shell am force-stop $PKG; $PUT "$BASEXML" $KEYS
  for t in CAR.GAL CAR.GAL.GAL.LITE; do adb -s $PH shell setprop log.tag.$t VERBOSE; done
  pcap_start "$RUN"; cap_start; sleep 1; both $RUN-start
  adb -s $HU shell am start -n $MAIN >/dev/null; L=$(nl)
  waitfor 45 'SSL handshake complete' $L || { mark $RUN-fallback-connect; send ACTION_CONNECT --es ip $PHIP >/dev/null
    waitfor 60 'SSL handshake complete' $L || { echo SESSION_FAIL_SSL; return 3; }; }
  waitfor 60 'Throughput over [0-9]+ms: rendered=[1-9]' $L || { echo SESSION_FAIL_NO_FRAMES; return 3; }
  send ACTION_QUERY_STATE | tee $OUT/$RUN.state
  adb -s $PH shell input keyevent KEYCODE_MEDIA_PLAY
  waitfor 30 'Media Start Request AUDIO' $L || echo NO_MEDIA_CHANNEL
  sleep 15; }
# rwin RUN TAG VERB : one restart window, 20 s
rwin() { both $1-$2-go; send $3 > $OUT/$1-$2.reply; cue "LISTEN to D-HP now: say if music stops and when it returns ($1-$2)"
  sleep 20; both $1-$2-done; adb -s $PH shell dumpsys media_session | grep -c 'state=PLAYING' > $OUT/$1-$2.playing; }
close_run() { both $1-end; sleep 1; pcap_stop; cap_stop; th_report $1
  send ACTION_EXIT >/dev/null; sleep 3; adb -s $HU shell am force-stop $PKG; }
```

If `open_hs` returns 3 twice in a row on an arm, that arm's R1 is INCONCLUSIVE (no session); say which line was last reached.

### R1. Restart audio twice in a live session with music playing, B and M

```bash
open_hs R1-B && { both R1-B-pre; sleep 10; both R1-B-pre-done
  rwin R1-B v1 ACTION_RESTART_AUDIO; rwin R1-B v2 aap.action.RESTART_AUDIO; }; close_run R1-B
# install M (adb -s $HU install -r <M apk>), set WANT_MD5 to M's md5, then the same block with R1-M
```

Extract per window `W` in `pre`, `v1`, `v2` (`hseg R1-x-W-go R1-x-W-done`, and `pre` from `R1-x-pre` to `R1-x-pre-done`):

```bash
A=B    # A=M for R1-M; CAP and PCAP must name that run's files
for W in v1 v2; do S=$(hseg R1-$A-$W-go R1-$A-$W-done); P=$(pseg R1-$A-$W-go R1-$A-$W-done)
  echo "$W recv=$(echo "$S"|grep -a 'AutomationReceiver: '|c 'RESTART_AUDIO') svc=$(echo "$S"|c 'AapService: Received request to restart audio') tracks=$(echo "$S"|c 'AapAudio: Restarting all audio tracks') aac=$(echo "$S"|c 'AAC Decoder started for') sink=$(echo "$S"|c 'audio sink AUDIO over') rendered=$(echo "$S"|rendered) quit=$(echo "$S"|c 'AapTransport quitting (clean=') ssl=$(echo "$S"|c 'SSL handshake complete') fatal=$(echo "$S"|c 'FATAL EXCEPTION') ph_crit=$(echo "$P"|c 'Critical error') ph_disc=$(echo "$P"|c 'Head unit disconnected') playing=$(cat $OUT/R1-$A-$W.playing)"; done
S=$(hseg R1-$A-pre R1-$A-pre-done); echo "pre sink=$(echo "$S"|c 'audio sink AUDIO over') rendered=$(echo "$S"|rendered)"
```

**Reachability first.** `pre` must show `sink` >= 1 and `rendered` >= 1. If `sink` is 0, no audio reached the sink before the verb, so the run cannot show a restart: INCONCLUSIVE, with the `Media Start Request AUDIO` count and `use-aac-audio` value.

**PASS (R1-M):** in each of `v1` and `v2`: `recv` 1, `tracks` 1, `aac` >= 1, `sink` >= 2, `rendered` >= 2, `quit` 0, `ssl` 0, `fatal` 0, `ph_crit` 0, `ph_disc` 0, `playing` >= 1. And `svc` is 0 in both windows (M's own branch runs, as section 2 predicts). **FAIL:** any other value. A `svc` of 1 on M is not a defect of the audio path, but it means the build is not M: recheck identity.

**R1-B expected:** the same, except `svc` 1 in both windows. B is the comparison for `svc` only.

**H2 (report only):** quote what the operator said for each window and the time they said it. If they heard music stop and not return, write that in the run's section even when the counts PASS.

### R3. The sibling verbs on M, inside the R1-M session

Run this between R1-M's `v2` and `close_run R1-M`, on the same session.

```bash
for V in ACTION_REFRESH_SENSORS aap.action.REFRESH_SENSORS; do both R3-$V-go; send $V > $OUT/R3-$V.reply; sleep 5; both R3-$V-done; done
for V in ACTION_RAISE_PROJECTION aap.action.RAISE_PROJECTION; do adb -s $HU shell input keyevent 3; sleep 3
  both R3-$V-go; send $V > $OUT/R3-$V.reply; sleep 5; top > $OUT/R3-$V.top; both R3-$V-done; done
```

Extract after `close_run R1-M`, with `CAP` naming R1-M's capture:

```bash
for V in ACTION_REFRESH_SENSORS aap.action.REFRESH_SENSORS ACTION_RAISE_PROJECTION aap.action.RAISE_PROJECTION; do S=$(hseg R3-$V-go R3-$V-done)
  echo "$V recv=$(echo "$S"|c "AutomationReceiver: com.andrerinas.openheadunit.$V") ref=$(echo "$S"|c 'AapService: Received request to refresh all sensors') night=$(echo "$S"|c 'NightMode update: ') raise=$(echo "$S"|grep -a 'AapService: raising the projection by ') resume=$(echo "$S"|c 'AapProjectionActivity: onResume') top=$(grep -c AapProjectionActivity $OUT/R3-$V.top 2>/dev/null)"; done
```

**PASS:** each refresh window has `recv` 1, `ref` 1 and `night` >= 1. Each raise window has `recv` 1, exactly one `raise` line and it reads `by DIRECT`, `resume` >= 1, and `top` >= 1. API 17 always takes the `DIRECT` route, so another route is a FAIL. **FAIL:** any count of 0 where these conditions need 1. B is not run here: projection-teardown-and-relays round 1 measured the same handlers (R5-C, R5P-C), and the merge does not touch them.

### R2. Restart audio with no session, M

```bash
RUN=R2-M; CAP=$OUT/$RUN.logcat; apk_check; adb -s $HU shell am force-stop $PKG
$PUT "$BASEXML" ${KEYS/wifi-connection-mode=1/wifi-connection-mode=0}
cap_start; sleep 1                     # no phone capture: nothing here reaches Android Auto
send ACTION_RESTART_AUDIO > $OUT/R2-v1.reply; sleep 10; mark R2-M-mid
send aap.action.RESTART_AUDIO > $OUT/R2-v2.reply; sleep 10; mark R2-M-end
adb -s $HU shell ps | grep -c headunitrevived > $OUT/R2.alive
cap_stop; th_report R2-M; send ACTION_EXIT >/dev/null; sleep 3; adb -s $HU shell am force-stop $PKG
```

The first verb goes to a stopped app on purpose: `-f 0x00000020` delivers it, and the relay starts the service.

**PASS:** in the whole capture, `AutomationReceiver: ` lines that contain `RESTART_AUDIO` 2, `AapAudio: Restarting all audio tracks` 0, `AapService: Received request to restart audio` 0, `SSL handshake complete` 0, `FATAL EXCEPTION` 0, and `R2.alive` >= 1. Both replies read `ok`. **FAIL:** a crash, a `Restarting` line, or a dead process.

**Stop rule:** one pass of R1-B, R1-M, R3 and R2. Re-run a run only once, and only when a discard rule hits (`Magic Garbage detected in header`, an SSL inside a verb window, `MATCH! Starting AapService`). A second hit makes it INCONCLUSIVE.

## 7. Do not re-run

- The defect on the merge base `2ca3b1f1`: R5-B and R5P-B of projection-teardown-and-relays round 1, and C2.0 of pr-1047-session-reconnect round 1.
- `main`'s three handlers on D-HU and D-HP: R5-C and R5P-C of projection-teardown-and-relays round 1.
- The branch tip `42151477` on its own: section 1 says why it adds nothing.

## 8. Report back

1. R1-M: per window `recv`, `svc`, `tracks`, `aac`, `sink`, `rendered`, `quit`, `ssl`, `playing`, and the operator's words.
2. R1-B `svc` against R1-M `svc` (the expected 1 against 0).
3. R2-M and R3 verdicts, plus whether M's build printed the duplicate-branch warning (quote it).

Results go in `pr-1063-restart-audio-round1-results.md` in the template's skeleton (section 7). Put `th_report` output in each run's section. Captures go to the fork as release `rig-evidence-pr-1063-restart-audio`, one asset `pr-1063-restart-audio-round1-captures.zip`, cited with its sha256.

```decisive-strings
AutomationReceiver: 
AutomationMarker: 
AapService: Received request to restart audio
AapAudio: Restarting all audio tracks
AAC Decoder started for
audio sink 
Media Start Request 
AapService: Received request to refresh all sensors
NightMode update: 
AapService: raising the projection by 
AapService: raise projection ignored, no session
AapProjectionActivity: onResume
SSL handshake complete
Throughput over 
AapTransport quitting (clean=
WifiLauncher: Initializing WiFi Mode: 
Magic Garbage detected in header
MATCH! Starting AapService
```
