# session-reconnect, round 1 brief: serialised session replacement, the audio-settings reconnect, the USB handle race, and the three-branch combo

Run it after the framing and video-retirement round 1 briefs if the rig has them queued, but it does not depend on either: every arm here installs its own APK and reads its own captures. Where a number from those rounds would help, this brief says so and goes on without it.

## 1. Build and baseline

Three APKs, built with `build_hur.sh`, each copied out of `apks/` into a round folder the moment it is built (the script deletes the previous APK first, template section 7a, tooling). Name them by SHA.

| Arm | Code | Full SHA | Gate |
|---|---|---|---|
| **B** (baseline) | `main` | `7e9d813d66493f1cc978c0f6a259bdb613874bd1` | 2658 JVM tests, 0 failures |
| **K** (candidate) | outside contributor's branch `pr/split-session-reconnect` | `ac741d298d825149618f6529e40b741a53818136` | 2658 JVM tests, 0 failures (author's count; it adds no tests, only a python host harness) |
| **X** (combo) | `pr/split-aap-framing` + `pr/split-video-retirement` + K, merged locally | tree hash `8eb30402c3498a0ad30b363c3d71e091c066265c` | 2683 JVM tests, 0 failures |

The contributor's branches are not on the fork. Fetch them by URL:

```bash
git fetch https://github.com/emotionbug/open-headunit.git pr/split-session-reconnect
git fetch https://github.com/emotionbug/open-headunit.git pr/split-aap-framing
git fetch https://github.com/emotionbug/open-headunit.git pr/split-video-retirement

# K
git checkout -B arm-K ac741d298d825149618f6529e40b741a53818136
git log --oneline 7e9d813d..HEAD | wc -l            # 4

# X: rebuilt locally so nothing depends on a merge commit someone else made
git checkout -B arm-X dfb0d6f9653c544d9daddfe6c3d5b3cf6c947d97     # pr/split-aap-framing tip
git merge --no-edit e7c2949c1bd43df22c5d1b94dbacf7e4d3ebf385        # pr/split-video-retirement tip
git merge --no-edit ac741d298d825149618f6529e40b741a53818136        # K
git rev-parse 'HEAD^{tree}'    # MUST print 8eb30402c3498a0ad30b363c3d71e091c066265c
```

**If X's tree hash differs, every X run is UNTESTABLE** (record the hash that printed, do not build X, and carry on with B and K). The merge commit SHAs will differ from anyone else's because they carry a timestamp; the tree hash is the identity.

B needs no checkout beyond `git checkout -B arm-B 7e9d813d66493f1cc978c0f6a259bdb613874bd1`. Record the md5 of all three APKs from a real `adb pull` plus local `md5sum` (not a piped `cat`, template section 5); the three must differ.

**Which runs need B is marked `[B]` on every run.** A run with no `[B]` is candidate-only (K, X, or both as named).

### 1a. Identity gate, per arm, before any run on that arm

`versionName` and `versionCode` never move between candidates, so identity is a symbol check on the installed APK:

```bash
adb -s $DEV shell pm path $PKG                          # pull that apk to ./installed.apk
unzip -p installed.apk 'classes*.dex' | strings | grep -cF '<symbol>'
```

| Symbol | B | K | X |
|---|---|---|---|
| `transportLifecycleLock` (the session-reconnect branch) | 0 | >= 1 | >= 1 |
| `AapMessageReassembler` (the framing branch) | 0 | 0 | >= 1 |
| `outputPublicationLock` (the video-retirement branch) | 0 | 0 | >= 1 |

All three counts were taken against the source trees: `transportLifecycleLock` is 0 on `main` and 24 on K and X; `AapMessageReassembler` is 0 on `main` and K, 4 on X; `outputPublicationLock` is 0 on `main` and K, 6 on X. Also send `ACTION_QUERY_STATE` and record `commit`; for K it must start `ac741d29`, for B `7e9d813d`, for X it is the local merge commit (record it, do not grade it).

A symbol row that does not match its arm voids that arm's runs for the stage (UNTESTABLE for that stage), and the executor reinstalls once before giving up.

## 2. What this is and why it exists

K is an outside contributor's change in four commits. It changes how a session is replaced and ended:

- **Transport lifecycle.** `AapTransport` has a `lifecycleLock` and a `closing` flag. `quit()` notifies, closes the connection first, then waits for the handshake and joins the worker threads **with no timeout** (main joined for 1 s), and only then releases audio focus, the video decoder and the TLS context. A new `awaitTermination()` lets `CommManager.doDisconnect` wait for that before the shared decoders are reused.
- **CommManager state.** `transportLifecycleLock` plus `disconnectRequested` serialise connect, disconnect and the quit-callback publication, so the first disconnect decision wins and a stale transport cannot retire its replacement.
- **Audio settings.** Saving an audio-session setting no longer stops the whole service. `CommManager.applyAudioSettings()` ends the session with `disconnect(isUserExit = false)` when `AapAudio.needsSessionRestart()` (any change to sink, static focus, focus mode, separate streams, the three stream ids, AAC or the DSP equalizer), and otherwise just restarts the tracks. `restartAudio()` now routes through it.
- **USB.** `StandardUsbProjectionConnection.disconnect` closes a device handle that `openDevice` published while disconnect was waiting on its lock.

The review of K found no blocking defect and these things the rig must measure, because code reading cannot:

- **S1: an audio save now ends the session as a lost link, not as a user exit.** `Disconnected(isClean = false)` reaches `AapService`, which broadcasts `link_lost`, and in Native AA takes `SessionEndGroupPolicy.KEEP_AND_REARM` with a wake poke, so the phone is woken and the projection can be raised while the user is still on the Settings screen. Over USB it reconnects after the settings hold; in Headunit Server and Helper it restarts discovery in 2 s; in manual/IP mode nothing reconnects. A session that has not rendered a frame yet also counts toward `VideoStarvationPolicy`, whose cap silently forces 720p30 and AAC after three. On main the same Save stopped the service as a user exit, so this is better than main but wants its own classification. **The brief measures what actually happens, per transport.**
- **S2: the restart-audio path changed meaning.** On main `CommManager.restartAudio()` restarts tracks and the session lives. On K it ends the session whenever any audio-session key differs from the snapshot taken at connect. The host traced the automation verb `ACTION_RESTART_AUDIO` as inert on both builds (relayed as a service start that `AapService.onStartCommand` has no branch for), and the in-projection Quick Settings audio toggles (AAudio, latency multiplier, queue capacity) are not `AudioSessionConfig` fields, so neither reaches the session-ending branch. C2 is therefore a characterisation of "settings drifted, then something called `restartAudio()`" only; it is not a claim that any automation macro or Quick Settings toggle can end a session.
- **S3: Nearby.** `NearbySocket.disconnect()` does not release `inputLatch`, so a handshake read inside `waitForStream` holds `quit()` for up to 8 s; the exit hits `ServiceStopWaitPolicy`'s 5 s bound where main did not wait.
- **First failed handshake.** `hasEverConnected` is now set at `Connected`, so a first-in-process USB or Native attempt that opens and then fails its handshake runs `onDisconnected`, which can start a `checkAlreadyConnected(force)` loop. Checked here against the dongle storm shape (the dongle's version request retried, then a late second response poisoning TLS).
- **ByeBye cut short.** `quit()` closes the connection right after `stop()`'s 150 ms grace; main let a congested ByeBye finish inside a 1 s join.

X adds the framing branch (strict plaintext framing, a decrypt failure now disconnects at once) and the video-retirement branch (surface ownership, output-worker fencing; its review found a GLES warm-relaunch regression on return from Home). The review says no semantic conflict among the three; C7 is the one run that proves it on hardware.

## 3. What is different about this round

### 3.1 Cabling stages (five units, four USB ports on the test PC)

| Stage | Plugged | Runs |
|---|---|---|
| **A** | D-HU (head unit, Native AA over WiFi Direct), D-POCO (phone, paired, Bluetooth and WiFi on) | R0, C3, C1N, C1S, C2, C6a, C7, C1X |
| **B** | D-HU on the house LAN, plus one phone whose Android Auto head unit server is listening (D-MOTO or D-POCO, whichever answers gate G-SRV below) | C1H, C1M |
| **N** | D-HU and D-POCO as stage A, phone with the Wireless Helper app set to Google Nearby | C5 |
| **C** | D-POCO as head unit and USB host over OTG on wireless adb, the dongle (idle `18d1:4ee1`, accessory `18d1:2d00`), D-MOTO paired with the dongle, an operator present for the plugs | C4a-d, C1U, C6b, C7U |
| **D** | D-SAM (Native AA, D-POCO as the phone) and D-HP (Headunit Server only, same phone as stage B) | C8 |

Each stage names what is plugged in, and Setup notes say what was. Do the stages in this order, A then B then N then C then D, installing B, K, X in turn inside a stage (three installs per stage at most; run every `[B]` run of the stage on B first, then every K run, then X).

### 3.2 Hand steps (the only ones in the round)

| Id | Step | Reason no verb exists |
|---|---|---|
| H1 | Physically unplug or plug the dongle on D-POCO | A cable is hardware. **Who reads the cue:** the human operator at the rig, who must be present for all of Stage C and is told so before it starts. The cue is the `cue` function in C4: a line on the executor's console prefixed `OPERATOR:`, a terminal bell, and a timestamped line in `cues.log`. The executor then waits on a log line with a timeout, never a fixed time (unplug: `USB_DEVICE_DETACHED`; plug: `Found device already in accessory mode` or `Switching USB device to accessory mode`). **A missed cue:** no matching line within 120 s voids that cycle (not a FAIL), the cue is repeated once, and a second miss ends the run INCONCLUSIVE with `operator not present` in Setup notes. |
| H2 | Power D-MOTO off or on (C4d) | Same: no adb lever powers a phone off that is not rooted. |
| H3 | Toggle Gearhead's "Start head unit server" developer option on the server phone, only if gate G-SRV fails or the server went deaf | It lives in Gearhead's own UI, with no verb and no intent (template section 7a, gearhead). |

There is **no tap on the app anywhere in this brief**. The Settings screen is opened with its deep link and closed with `input keyevent 4`.

### 3.3 Why the audio save is driven the way it is, and why a root broadcast appears

The Save button in the audio settings and in the main settings list is the user-facing trigger; both call `CommManager.applyAudioSettings()` (verified in `AudioStreamSettingsFragment.saveSettings` and `SettingsFragment.saveSettings`). A write to `settings.xml` behind a running app is never seen (the process holds the prefs in memory, template section 1), so a file edit cannot trigger it. This round reaches the same function with two no-tap steps:

1. **The settings change** goes in through `ACTION_SET_SETTINGS` with an inline JSON, which writes the running app's own SharedPreferences (the same object `AudioSessionConfig.from(settings)` reads). It is gated by `allow-external-configuration`, which the round writes to `true` in `settings.xml` with the app stopped.
2. **The apply** is `CommManager.restartAudio()`, which is `applyAudioSettings()` on K. Its only entries are the `AapService` receiver for `com.andrerinas.openheadunit.aap.action.RESTART_AUDIO` and the Save buttons. **Read from the code, `send ACTION_RESTART_AUDIO` probably does nothing:** `AutomationCommandPolicy` relays it as `startForegroundService` with that action, `AapService.onStartCommand` has no branch for it (it falls to the `else` that only acts on a null action), and the receiver that calls `commManager.restartAudio()` is registered `RECEIVER_NOT_EXPORTED`, which on Android 13 and above rejects a shell broadcast. C2.0 measures this directly. The helper `restart_audio` below tries the verb, then the plain shell broadcast (works below API 33, where the flag has no effect), then a **root** broadcast (`su`, which the not-exported check lets through). The root broadcast is an OS-level lever to an app receiver, not an interaction with the app's UI. **The host approved it** as a named lever on rooted units. State plainly in the results what it exercises: `CommManager.restartAudio()` and so `applyAudioSettings()`, which is the function both Save buttons call, but **not the Save button path itself** (no fragment code, no toast, no service-restart logic runs).

What a UI Save would add that this does not: a toast, and for the main settings list some extra service logic for keys that need a service restart. The session-ending path is the same function either way. On an API 33+ unit with no root the route does not exist and C1 and C2 there are INCONCLUSIVE ("not reachable by automation").

### 3.4 Other rig facts that change runs

- **D-HU `shared_prefs/` may be root-owned**, which makes every value the app itself writes (including `video-profile-starvation-cap`) vanish on the next start. `stat` it at the start of the round, `chown` it to the app's uid:gid if it is `root:root`, and put what it read in Setup notes. All settings the round seeds are root writes via the push in section 4.
- **No taps on projected video**, none needed here.
- **D-POCO (API 35) refuses `ACTION_START_WIRELESS_SCAN` right after a force-stop** (`startForegroundService() not allowed`), so on D-POCO arm by launching `MainActivity` (`am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity`). D-HU accepted the verb before; the `bringup` helper tries the verb and falls back to the launch.
- **`headunit://exit` or `ACTION_EXIT` sent to a stopped app starts the service and the stop lands in the middle of a bring-up.** Every exit in this brief is sent to an app with a live session, never to a stopped one.
- **Native AA, `userExit` leaves `User exit cooldown active for 5000ms`.** Wait 10 s after an exit before arming again.
- **D-SAM (API 19)** has no `su`, no `sed`, and `am` rejects `-p`. D-SAM runs use the push-the-whole-file settings recipe (section 4) and the implicit broadcast without `-p`.
- **D-HP's Settings screen cannot be scripted**; C8 has no Settings-open variant. `svc wifi enable` there takes up to 30 s.
- **A forced-offline phone has no lever** (WiFi and Bluetooth self-revert), so no run here depends on starving a phone.
- **The cap and the rig audio worst case.** `use-aac-audio` on, `audio-latency-multiplier` and `audio-queue-capacity` low are deliberate and are never reset (memory `rig-audio-settings-are-a-deliberate-worst-case`). Back up `settings.xml` once at the start of the round, **restore from that backup at the start of every run**, and only delete `video-profile-starvation-cap` from it. Read back `playback-focus-self-defeating` (must be false) and `video-profile-starvation-cap` (must be absent) before every run.
- **Pre-registered INCONCLUSIVE outcomes**, so none is read as a failure: C1H and C1M and C8's D-HP leg if no phone passes gate G-SRV; C5 if the Helper app is not on Nearby; C1U, C2.2 and C1S-on-USB if root is unavailable and the verb and plain broadcast both fail to land; any C7 leg that needs album art if no media is playing on the phone; the H.265 leg if the phone announces H.264.

## 4. Settings keys, helpers, and the arm protocol

### 4.1 Keys

All as `shared_prefs/settings.xml` elements, written with the app stopped and read back before every launch (template section 1). The audio keys are whatever the backup holds; do not write them.

| Key | Type | Value | Runs |
|---|---|---|---|
| `allow-external-configuration` | boolean | `true` | every run (the SET_SETTINGS gate) |
| `log-level` | int | `2` (INFO) | every run |
| `onboarding-version` | int | `2` | every run (a fresh install re-runs the wizard otherwise) |
| `wifi-connection-mode` | int | `3` Native AA; `1` Headunit Server; `0` manual/IP; `2` Wireless Helper | per run |
| `helper-connection-strategy` | int | `2` (Google Nearby) | C5 only |
| `use-libusb` | boolean | `true` | C4, C1U, C6b, C7U (C4a also repeats with `false`, see there) |
| `auto-start-on-usb` | boolean | `true` | USB runs |
| `reopen-on-reconnection` | boolean | `true` | USB runs |
| `auto-connect-last-session` | boolean | `true` | USB runs |
| `kill-on-disconnect` | boolean | `false` | every run |
| `view-mode` | int | `1` (TEXTURE), `2` (GLES) only in C7 | |
| `video-codec` | string | `H.265` in C7 only | |
| `native-driver-selection-mode` | int | leave at the backup's value | |
| `video-profile-starvation-cap` | | `DEL` before every run (then read back absent) | |
| `native-aa-wireless`, `wifi-launcher-mode` | | `DEL` before every run (legacy keys that silently turn a mode back into 3) | |

Rig audio keys to read back and record at R0 (not to write): `use-aac-audio`, `audio-latency-multiplier`, `audio-queue-capacity`, `static-audio-focus`, `playback-focus-self-defeating`.

### 4.2 Shell helpers (create once, `source` them in every shell)

```bash
# ---- devices ----
HU=27870808938846; PH=4f4027e9; MOTO=ZY22GC3BM4; SAM=30041c35642d2200; HPS=CNU350BGBJ
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
# DEV is the unit under test in the current run: export DEV=$HU  (or $PH for stage C, $SAM, $HPS)

send() { a=$1; shift; adb -s $DEV shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; }
mark() { send ACTION_LOG_MARKER --es text "$1" >/dev/null; }

# ---- capture (always before the launch, stdbuf, 16M ring) ----
cap_start() { adb -s $DEV logcat -G 16M; adb -s $DEV logcat -c; stdbuf -oL adb -s $DEV logcat -v time > "$1" & echo $! > "$1.pid"; export CAP="$1"; }
cap_stop()  { kill "$(cat "$1.pid")" 2>/dev/null; sleep 1; ps aux | grep -c "[l]ogcat -v time"; }   # must print 0 before the next run

# ---- reading a capture (always grep -a; logcat -v time: field 2 is HH:MM:SS.mmm) ----
cnt()   { grep -aF -- "$2" "$1" | wc -l; }
first() { grep -aF -- "$2" "$1" | head -1; }
# win FILE MARK : lines between AutomationMarker: MARK-start and MARK-end
win()   { sed -n "/AutomationMarker: $2-start/,/AutomationMarker: $2-end/p" "$1"; }
# gap FILE A B : ms from the first line containing A to the first line at or after it containing B ("NA" if either is missing)
gap()   { tr -d '\000' < "$1" | awk -v A="$2" -v B="$3" 'function ms(s,t){split(s,t,/[:.]/);return ((t[1]*60+t[2])*60+t[3])*1000+t[4]} index($0,A)&&!a{a=ms($2)} a&&!b&&index($0,B){b=ms($2)} END{if(a!=""&&b!="")print b-a; else print "NA"}'; }
# after FILE REGEX : the capture from the first line matching REGEX onward (chain with /dev/stdin)
after() { tr -d '\000' < "$1" | sed -n "\#$2#,\$p"; }
# watch REGEX CMD... : background watcher, runs CMD once when REGEX first appears in NEW log lines; unwatch kills it
watch()   { ( stdbuf -oL adb -s $DEV logcat -T 1 | grep -m1 -aE "$1" >/dev/null && { shift; "$@"; } ) & echo $! > watch.pid; }
unwatch() { kill "$(cat watch.pid)" 2>/dev/null; pkill -f 'grep -m1 -aE' 2>/dev/null; }
after_ms() { sleep "$(awk "BEGIN{print $1/1000}")"; shift; "$@"; }
# waitfor FILE PATTERN MAXSEC BASECOUNT : 0 when count of PATTERN exceeds BASECOUNT before MAXSEC
waitfor() { for i in $(seq 1 "$3"); do [ "$(cnt "$1" "$2")" -gt "${4:-0}" ] && return 0; sleep 1; done; return 1; }

# ---- the settings change inside a running app (SET_SETTINGS) ----
# The inline JSON is built on the device, because adb shell re-parses quoting.
cat > setkey.sh <<'EOS'
#!/system/bin/sh
# setkey.sh KEY JSONVALUE   e.g.  setkey.sh static-audio-focus true
am broadcast -f 0x00000020 -n com.andrerinas.headunitrevived/com.andrerinas.openheadunit.automation.AutomationReceiver \
  -a com.andrerinas.openheadunit.ACTION_SET_SETTINGS \
  --es json "{\"format\":\"open-headunit-settings\",\"version\":1,\"settings\":{\"$1\":$2}}"
EOS
setkey() { adb -s $DEV push setkey.sh /data/local/tmp/setkey.sh >/dev/null; adb -s $DEV shell sh /data/local/tmp/setkey.sh "$1" "$2"; }
# Reply to read: data= must contain "imported":1. "imported":0 or an error means the key is not a backup key: the run is void.

# ---- the apply: CommManager.restartAudio() ----
# Tries the verb, then the plain shell broadcast, then a root broadcast; returns the route that LANDED.
restart_audio() {
  n0=$(cnt "$CAP" "AapService: Received request to restart audio")
  send ACTION_RESTART_AUDIO >/dev/null; sleep 2
  [ "$(cnt "$CAP" "AapService: Received request to restart audio")" -gt "$n0" ] && { echo via-verb; return 0; }
  if [ "$DEV" = "$SAM" ]; then P=""; else P="-p $PKG"; fi
  adb -s $DEV shell am broadcast -a com.andrerinas.openheadunit.aap.action.RESTART_AUDIO $P >/dev/null; sleep 2
  [ "$(cnt "$CAP" "AapService: Received request to restart audio")" -gt "$n0" ] && { echo via-plain-broadcast; return 0; }
  adb -s $DEV shell su -c "am broadcast --user 0 -a com.andrerinas.openheadunit.aap.action.RESTART_AUDIO -p $PKG" >/dev/null; sleep 2
  [ "$(cnt "$CAP" "AapService: Received request to restart audio")" -gt "$n0" ] && { echo via-root-broadcast; return 0; }
  echo NOT-LANDED; return 1
}
```

A step that prints `NOT-LANDED` voids that run as INCONCLUSIVE (no route reaches the receiver on that unit); say which routes were tried.

```bash
# ---- settings file edit (host side; keeps <set> keys intact) ----
cat > edit_prefs.py <<'EOP'
#!/usr/bin/env python3
# edit_prefs.py IN OUT KEY=TYPE:VALUE ... DEL:KEY ...      TYPE is int, boolean or string
import sys, xml.etree.ElementTree as ET
src, dst, *ops = sys.argv[1:]
tree = ET.parse(src); root = tree.getroot()
def drop(k):
    for e in list(root):
        if e.get('name') == k: root.remove(e)
for op in ops:
    if op.startswith('DEL:'): drop(op[4:]); continue
    k, rest = op.split('=', 1); t, v = rest.split(':', 1)
    drop(k); e = ET.SubElement(root, t, {'name': k})
    if t == 'string': e.text = v
    else: e.set('value', v)
tree.write(dst, encoding='utf-8', xml_declaration=True)
EOP

# put_prefs OPS... : app stopped; edits settings.xml from the round backup and installs it
put_prefs() {
  adb -s $DEV shell am force-stop $PKG
  python3 edit_prefs.py settings-backup.xml settings.new "$@"
  adb -s $DEV push settings.new /data/local/tmp/settings.new >/dev/null
  if adb -s $DEV shell su -c id 2>/dev/null | grep -q 'uid=0'; then
    OWN=$(adb -s $DEV shell su -c "stat -c %U:%G /data/data/$PKG")
    adb -s $DEV shell su -c "cp /data/local/tmp/settings.new /data/data/$PKG/shared_prefs/settings.xml; chown $OWN /data/data/$PKG/shared_prefs/settings.xml; chmod 660 /data/data/$PKG/shared_prefs/settings.xml"
  else   # D-SAM, D-HP, or a unit with no root: push-the-whole-file recipe
    adb -s $DEV shell run-as $PKG cp /data/local/tmp/settings.new shared_prefs/settings.xml
  fi
  adb -s $DEV shell run-as $PKG cat shared_prefs/settings.xml 2>/dev/null | tr -d '\r' | grep -o 'name="\(wifi-connection-mode\|allow-external-configuration\|video-profile-starvation-cap\)"[^/]*'
}
```

On D-HU, if `su` is refused for the app's data directory, copy the file as root the way the template does and say so in Setup notes. If `put_prefs` cannot read the settings back, the run is void.

### 4.3 The standard bring-up (Native AA), used by every Stage A run

```bash
# bringup OUT [FRESH] : arm Native AA and wait for THIS arming's SSL (max 120 s). FRESH=1 (default) force-stops the app and
# starts the capture; FRESH=0 keeps the running capture and process (use it for cycles 2..N of one capture).
# Every wait uses a base count read just before the arming, so an earlier session's SSL can never satisfy it.
# Needs log-level 2 (INFO): at DEBUG both "SSL handshake complete. Session id..." (INFO) and "Handshake: SSL handshake complete. TS:" (DEBUG) print, so counts double.
cnt_rendered() { grep -aE 'Throughput over [0-9]+ms: rendered=[1-9]' "$1" | wc -l; }
bringup() {
  OUT=$1; FRESH=${2:-1}
  if [ "$FRESH" = 1 ]; then adb -s $DEV shell am force-stop $PKG; cap_start "$OUT"; fi
  SSL0=$(cnt "$OUT" "SSL handshake complete"); TPUT0=$(cnt_rendered "$OUT")
  T0=$(date +%s)
  r=$(send ACTION_START_WIRELESS_SCAN | tr -d '\r')
  echo "$r" | grep -q 'not allowed\|error\|Exception' && adb -s $DEV shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity >/dev/null
  waitfor "$OUT" "SSL handshake complete" 120 "$SSL0" && echo "ssl_s=$(( $(date +%s) - T0 ))" || echo "FAIL no SSL in 120 s"
}
# carrying_video OUT : 3 NEW rendered Throughput lines since this bringup (max 60 s)
carrying_video() { for i in $(seq 1 60); do [ "$(cnt_rendered "$1")" -ge $((TPUT0+3)) ] && return 0; sleep 1; done; return 1; }
```

**Phone readiness before each Stage A bring-up** (D-POCO): `adb -s $PH shell dumpsys window | grep mCurrentFocus` must not be a Settings screen (`input keyevent KEYCODE_HOME` if it is); Bluetooth and WiFi verified on with `dumpsys bluetooth_manager | grep -i "enabled"` and `dumpsys wifi | grep -i "Wi-Fi is"`, never `settings get global`; D-HU's own saved P2P groups are not a variable here because the phone is the only paired one.

**Discard rules (template section 4)** apply to every capture. A capture with `MATCH! Starting AapService` outside a deliberate re-arm, a second `createGroup SUCCESS` where one group was expected, `AapRead: Magic Garbage detected in header`, or an SSL the run did not cause is voided and re-run once; a second void is INCONCLUSIVE with the reason. Runs C1 to C3 and C6 expect exactly the second SSL they cause and name it. After an exit and before the next arm: `sleep 10`.

### 4.4 Captures and evidence (template section 7)

The branch is markdown only; captures go to a release asset.

```bash
zip -1 -r pr-1047-session-reconnect-round1-captures.zip <your capture dir>
sha256sum pr-1047-session-reconnect-round1-captures.zip
# first round of this thread (the release does not exist yet):
gh release create rig-evidence-pr-1047-session-reconnect --repo o-jcardenass/open-headunit \
  --notes "pr-1047-session-reconnect rig captures, one asset per round" pr-1047-session-reconnect-round1-captures.zip
# if the release already exists:
gh release upload rig-evidence-pr-1047-session-reconnect --repo o-jcardenass/open-headunit \
  pr-1047-session-reconnect-round1-captures.zip
```

The results file cites the release, the asset filename and the sha256, and quotes every line a verdict rests on with its timestamp (the asset is only for re-examination). Every capture process is killed at the end of its run (`cap_stop`), and `ps aux | grep logcat` must show none left.

## 5. Every action as a verb

| Action | Command | Notes |
|---|---|---|
| Arm Native AA, no UI | `send ACTION_START_WIRELESS_SCAN` | falls back to launching `MainActivity` on API 31+ after a force-stop |
| Exit with a live session | `send ACTION_EXIT` | logs `Stop action received. Broadcasting finish request to activities.` |
| End the session, keep the service | `send ACTION_DISCONNECT` | the user-exit path, logs `Disconnect action received.` |
| Manual / IP connect | `send ACTION_CONNECT --es ip <addr> --ez no_ui true` | TCP to `:5277` |
| USB check | `send ACTION_CHECK_USB` | sends `user_requested`, lifts the pill's USB hold |
| Nearby connect | `send ACTION_NEARBY_CONNECT --es extra_endpoint_id <id>` | |
| Read the state | `send ACTION_QUERY_STATE` | `data=` carries `commit`, `connected`, `state` |
| Change a setting in the running app | `setkey KEY JSONVALUE` (4.2) | `ACTION_SET_SETTINGS`, gated, reply `"imported":1` |
| Restart audio / apply | `restart_audio` (4.2) | verb, then plain broadcast, then root broadcast; see 3.3 |
| Open Settings | `adb -s $DEV shell am start -n $PKG/com.andrerinas.openheadunit.main.SettingsActivity --ei extra_destination 0` | on D-SAM `run-as $PKG am start --user 0 -n ...` |
| Close Settings | `adb -s $DEV shell input keyevent 4` | BACK, a keyevent |
| Media next | `adb -s $DEV shell input keyevent KEYCODE_MEDIA_NEXT` | relays to the phone's player |
| Cover and return the projection (C7) | `run_r5_cover_return.sh` from `hur-wifi-test-scripts/` if listed in the inventory; else the pair in C7 | a covered surface is never assumed: C7 checks it |
| Stamp a step | `mark <label>` | `AutomationMarker: <label>` at WARN |

`send` must print `AutomationReceiver: <action>` in the capture for every verb above; a step with no such line never landed and its run is void, not a FAIL.

## 6. The lines that decide the runs

Each was checked with `grep -rF` against `app/src/main` on the K tree (`ac741d29`) and the X tree, with presence on B noted. Everything is INFO unless marked. The session-end reason suffixes (`link_lost`, `user_exit`, `phone_left`) are constants in `contract/src/main/java/.../HeadUnitIntent.kt`, composed onto `AapService: session state <state> (<reason>)` at run time.

| Line (substring) | Meaning | B | K / X |
|---|---|---|---|
| `CommManager: audio settings changed; reconnecting the projection session` | the candidate ended a session to renegotiate audio | absent | present |
| `AapService: Received request to restart audio` | the restart receiver ran (what `restart_audio` proves) | present | present |
| `AapAudio: Restarting all audio tracks` | tracks restarted, session lives | present | present |
| `AapService: session state` then `disconnected (link_lost)` / `(user_exit)` / `(phone_left)` | what third-party listeners were told | present | present |
| `AapService: Native AA session ended; keeping the` | KEEP_AND_REARM taken | present | present |
| `NativeAA: Attempting active poke to device` | a wake poke fired | present | present |
| `NativeAA: Calling socket.connect()` | the poke really connected (a stand-down prints only the attempt line) | present | present |
| `AapService: Not raising the projection, the settings screen is open` | the raise was refused because Settings is foreground | present | present |
| `AapService: raising the projection by` | the projection was raised | present | present |
| `Stop action received. Broadcasting finish request to activities.` | the service was told to stop | present | present |
| `AapService destroying` | the service reached `onDestroy` | present | present |
| `AapService: Native AA user exit. Stopping active launcher.` | the Native user-exit stop ran | present | present |
| `WifiDirectManager: Stopping and cleaning up...` | the P2P group is being given back | present | present |
| `AapService: the wireless teardown did not finish in` | the exit's 5 s bound hit | present | present |
| `AapTransport quitting (clean=` | transport retired | present | present |
| `AapTransport cleanup` (then `failed`) | a cleanup step threw | absent | present |
| `Failed to join threads`, `Starting handshake without connection` | the old error paths | present | **absent** |
| `AapService: USB disconnect. Scheduling reconnect check in` | USB reconnect armed | present | present |
| `AapService: Disconnected. Restarting discovery loop in 2s...`, `AapService: Unclean WiFi disconnect in Auto Mode. Retrying discovery in 2s...` | Helper/Server/Auto reconnect arming | present | present |
| `AapService: User exit with wirelessServer active. Not restarting discovery.` | the user-exit reading | present | present |
| `AapService: USB disconnect after user Exit. Skipping auto-reconnect` | USB user-exit reading | present | present |
| `AapService: userExitedAA is true. Skipping auto-poke.` | the poke was skipped as after a user exit | present | present |
| `sessions in a row ended without a single video` | the starvation advice (third starved session; the cap is written with it) | present | present |
| `ConnectionArbiter: ` with `preempts`, `stood the wireless stack down until it ends`, `ms to try again`, `USB has tried for`, `ended with no session; giving back`, `wireless bring-up refused while`, `a USB check waits for` | the tier arbiter | present | present |
| `CommManager: Connect already in progress; closing the handed-over socket` | a second connect met one in flight | present | present |
| `Switching USB device to accessory mode`, `Found device already in accessory mode` | a USB attempt began | present | present |
| `Failed to read AAP header`, `still receiving late VERSION_RESPONSEs at the deadline` | the dongle storm shapes | present | present |
| `SSL handshake complete` (INFO form `AapSslContext.performHandshake | SSL handshake complete.`; do not prefix with `Handshake:`) | a session formed | present | present |
| `Service Discovery Response`, `Channel Open Response`, `Media Sink Setup Request:`, `Throughput over` (`... rendered=N (Mfps)`) | projection proof; `TransportStarted` is never logged | present | present |
| `NearbySocket: Blocking read until InputStream is AVAILABLE via Nearby Payload`, `NearbyManager: Endpoint FOUND:` | a Nearby handshake is waiting; the endpoint id | present | present |
| `FATAL EXCEPTION` | a system line, not in source | | |
| `AapRead: Magic Garbage detected in header`, `MATCH! Starting AapService`, `createGroup SUCCESS` | the discard rules | present | present |
| `ORPHANED_FRAGMENT`, `TRUNCATED_RUN`, `DELTA_CHANGED` as printed `AapRead: <OUTCOME> on <channel> - channel=...` | the fragment audit | present | present |
| `AapVideo: Previous frame was truncated`, `AapVideo: Orphaned fragment`, `AapVideo: reassembly anomalies over` | video reassembly faults | present | present |
| `AapRead: invalid framing or TLS session` | framing branch's disconnect-on-bad-frame | absent | absent on K, **present on X** (identity line for X) |
| `Audio transport read channel=` | framing branch's slow-read line (WARN) | absent | absent on K, present on X |
| `AapRead: largest message body so far` | a large message (album art) arrived | present | present |
| `AapMediaPlayback: Failed to parse metadata` | a metadata parse failed | present | present |
| `New surface set:`, `cycling video focus`, `First frame rendered`, `Forcing restart (`, `Decoder stopped:` | the surface and decoder lifecycle (C7) | present | present |
| `Media Sink Stop Request: `, `Video Sink Stopped -> Ignored (Forced Keyframe Request)` | the phone stopped the video sink | present | present |
| `WifiLauncher: Initializing WiFi Mode:` (suffix `NATIVE`) | the stack was armed | present | present |
| `AutomationReceiver: ` and `AutomationMarker: ` | verb proof and run brackets | present | present |

`dumpsys` reads used (not log lines): `adb -s $DEV shell dumpsys wifip2p | grep -i groupFormed` for the group, `adb -s $DEV shell dumpsys activity activities | grep mResumedActivity` for the foreground activity.

## 7. Runs

**The point of the round** is C1 (S1, per transport) with C3 and C4 as the regression gates and C7 as the combo proof. Run order: R0, then Stage A (C3, C1N, C1S, C2, C6a, C7, C1X), Stage B (C1H, C1M), Stage N (C5), Stage C (C4a-d, C1U, C6b, C7U), Stage D (C8).

**Per-run frame for every run** (not restated below): restore the backup and write the run's keys with `put_prefs`; `DEL:video-profile-starvation-cap DEL:native-aa-wireless DEL:wifi-launcher-mode`; verify; start the capture with `cap_start` (before the launch); bracket the window with `mark <ID>-start` and `mark <ID>-end`; kill the capture with `cap_stop`; all counts are over the marker window (`win FILE <ID> > w.txt`, then `cnt`/`gap` on `w.txt`), and for a run with an `<ID>-apply` marker over the capture from that marker onward (`after FILE 'AutomationMarker: <ID>-apply' > apply.txt`), unless stated. Repeated cycles in one capture (C3) are read one window file per cycle, because `gap` reads the first occurrence in its file.

**Every wait for an SSL, a session or a line uses a base count read immediately before the action that should cause it** (`SSLB=$(cnt $CAP "SSL handshake complete")` then `waitfor $CAP "SSL handshake complete" <sec> $SSLB`), never an absolute count. **Every run is at `log-level=2` (INFO).** At DEBUG the SSL pattern prints twice per session, once in its INFO form and once as `Handshake: SSL handshake complete. TS:`; if a run ever needs DEBUG it waits on `SSL handshake complete. ` (with the full stop and space), which only the INFO forms carry.

**Stop rule.** A run that needs N cycles stops at N completed (non-void) cycles, or after N+3 attempts, whichever first; whatever completed is graded and the shortfall is stated.

### R0 Gate (all arms, all stages)

PASS needs, per arm: the three APK md5s recorded and different; the identity symbol table in 1a matching; `ACTION_QUERY_STATE` `commit` as stated; `adb install -r` succeeding (never an uninstall); settings read back after the install (an `install -r` can silently wipe `settings.xml`, so diff the backup against a fresh pull and state the delta); the host `./gradlew :app:testGithubDebugUnitTest` count equal to the table in section 1 for B and K and X (a failure stops the round for that arm); the `stat` of D-HU's `shared_prefs/` recorded; the rig audio keys and `video-profile-starvation-cap` read and recorded; `adb -s $HU shell su -c id` and `adb -s $PH shell su -c id` each recorded (uid=0 or the refusal text).

**Gate G-SRV** (Stage B and D-HP): on each candidate server phone,
`adb -s <phone> shell 'cat /proc/net/tcp6 /proc/net/tcp' | grep -i ':149D .* 0A'` must print a listening row (5277 is 0x149D, state 0A). If neither phone has one, H3 toggles the option once on D-MOTO and the gate is read again; a second failure makes C1H, C1M and C8's D-HP leg INCONCLUSIVE. Nothing but the app may connect to port 5277 (a connection that fails the AAP version exchange wedges Android Auto's server); never `nc` the port, only read the table.

### C3 User exit x10 from a live Native AA session `[B]` (K and X too)

The exit is the code path K changed most (the unbounded joins, `awaitTermination`, the order of release), so this run is the regression gate for it and runs on all three arms. Set `wifi-connection-mode=3`, `log-level=2` (INFO: the SSL counts below are only correct at INFO, see 4.3; the group is read with `dumpsys wifip2p`, not from a DEBUG line).

Each cycle `n` (1 to 10), in order:

1. Cycle 1: `bringup r_C3_<arm>.txt 1` (force-stop, capture started, armed). Cycles 2 to 10: `bringup r_C3_<arm>.txt 0` (**no force-stop, same capture, same process**; the previous cycle's exit stopped only the service, and the verb re-arms it). `bringup` reads its own base count of `SSL handshake complete` before arming, so only this cycle's SSL satisfies the wait. SSL within 120 s, else the cycle is void.
2. `carrying_video r_C3_<arm>.txt` (3 NEW rendered `Throughput over` lines since this cycle's `bringup`, max 60 s), else void.
3. `mark C3-n-start; send ACTION_EXIT`, record the wall clock.
4. `sleep 3; adb -s $DEV shell dumpsys wifip2p | grep -i groupFormed` and record the line (`groupFormed: false` is the pass); `sleep 5`, record it again.
5. `mark C3-n-end; sleep 10`.

Per cycle, extract the cycle's window to its own file (`win r_C3_<arm>.txt C3-$n > w$n.txt`, the marker `C3-$n-start` is sent in step 3 and `C3-$n-end` in step 5) and read every count and gap from `w$n.txt`; the discard rules (a second `createGroup SUCCESS`, `MATCH! Starting AapService`) also apply per window.

- `T_cleanup` = `gap FILE "AutomationReceiver: com.andrerinas.openheadunit.ACTION_EXIT" "WifiDirectManager: Stopping and cleaning up..."` (the verb's own log prints its action; use the full line as it appears in the capture, the substring `ACTION_EXIT` is enough if unique per cycle)
- `T_destroy` = the same gap to `AapService destroying`
- counts of `AapService: the wireless teardown did not finish in`, `AapService: Native AA user exit. Stopping active launcher.`, `Stop action received.`, `FATAL EXCEPTION`, `Failed to join threads` (B) or `AapTransport cleanup` (K, X)

**PASS (K and X)**, over the completed cycles (need >= 8 completed, else INCONCLUSIVE):

- `groupFormed: false` at +3 s on every cycle
- `the wireless teardown did not finish in` count 0 on every cycle
- `Native AA user exit. Stopping active launcher.` exactly once per cycle
- `T_destroy` <= 5500 ms on every cycle (the service's own 5 s bound plus 500 ms)
- median `T_destroy` of the arm <= (B's median `T_destroy`) + 1500 ms
- `FATAL EXCEPTION` 0; `AapTransport cleanup ... failed` 0

B is the reference: record its medians and any cycle that breaks the same bounds (if B breaks one, the bound is not a K defect and the host decides). Record per cycle `T_cleanup`, `T_destroy`, the two `groupFormed` reads.

### C1N Audio save mid-session, Native AA, Settings closed `[B]` (K, X; B is a control, not a regression gate)

`wifi-connection-mode=3`. **B is a control here:** on B the same two steps restart tracks only. B's real equivalent of a Save is service stop, which C3 already measures, so the B arm of C1N exists to prove the helper lands and to give a no-reconnect reference.

1. Read the rig value of `static-audio-focus` from the backup (absent means false). Call it `V`; the flip value `VFLIP` is its negation (the literal `true` or `false`). The key is a session-config field and harmless to flip.
2. `bringup r_C1N_<arm>.txt`; `carrying_video` (>= 3 rendered windows, so the session has rendered).
3. `SSLB=$(cnt $CAP "SSL handshake complete")`; `mark C1N-save`; `setkey static-audio-focus <VFLIP>` (reply must show `"imported":1`; it returns in well under 1 s); `mark C1N-apply`; `restart_audio` (record the route). `restart_audio` waits 2 s for each route it tries, so the apply lands between about 2 s (verb) and about 6 s (root broadcast) after the marker. Timing is measured from the line `AapService: Received request to restart audio`, not from the marker.
4. Wait up to 150 s for another `SSL handshake complete` (`waitfor $CAP "SSL handshake complete" 150 $SSLB`).
5. If a second SSL formed: `sleep 15`, `mark C1N-control`, `restart_audio` again with no setting change; `sleep 5`; `send ACTION_QUERY_STATE` (record `connected`).
6. `mark C1N-end`. Afterwards `adb -s $DEV shell am force-stop $PKG` and restore (the next run's frame does that).

Measure, from the window after `C1N-apply`: `audio_changed` = count of `CommManager: audio settings changed; reconnecting the projection session`; `stop_action` = count of `Stop action received.`; `link_lost` / `user_exit` = counts of `AapService: session state disconnected (link_lost)` and `(user_exit)`; `T_disc` = `gap apply.txt "AapService: Received request to restart audio" "AapService: session state disconnected"`; `T_ssl2` = `gap apply.txt "AapService: Received request to restart audio" "SSL handshake complete"` (the first SSL precedes the marker, so the first one at or after it is the second session's); counts in the 60 s after the apply of `NativeAA: Attempting active poke to device`, `NativeAA: Calling socket.connect()`, `AapService: Native AA session ended; keeping the`, `AapService: raising the projection by`, `AapService: Not raising the projection, the settings screen is open`, `AapService: userExitedAA is true. Skipping auto-poke.`, `FATAL EXCEPTION`, `AapRead: Magic Garbage detected in header`; and `T_video2`: `after FILE 'AutomationMarker: C1N-apply' | after /dev/stdin 'SSL handshake complete' | grep -aEm1 'Throughput over [0-9]+ms: rendered=[1-9]'` prints the first rendered window after the second SSL; report that line's timestamp minus the second SSL's.

**PASS (K and X):** `audio_changed` == 1; `stop_action` == 0; `user_exit` == 0; `link_lost` == 1; a second SSL within 150 s of the apply; `T_video2` found within 30 s after that SSL; the control step in 5 prints `AapAudio: Restarting all audio tracks` once, **zero** new `audio settings changed`, and `ACTION_QUERY_STATE` reads `"connected":true`; `FATAL EXCEPTION` 0; `AapRead: Magic Garbage` 0.

**PASS (B control):** `restart_audio` landed; `AapAudio: Restarting all audio tracks` == 1; `audio_changed` == 0; no `AapService: session state disconnected` within 30 s of the apply; `ACTION_QUERY_STATE` `"connected":true` at +30 s.

**S1 verdict line (K and X, a finding rather than a pass condition):** write `S1 reproduced` if, in the 60 s after the apply, `Attempting active poke to device` >= 1 OR `link_lost` == 1; otherwise `S1 not reproduced`. Always give the poke, connect and raise counts and `T_disc` and `T_ssl2` as numbers. **What a PASS would look like if the change did nothing:** B's PASS shape (tracks restarted, session alive) is exactly what K would do if `needsSessionRestart()` returned false, so on K the `audio_changed == 1` and `link_lost == 1` pair is what proves the new path ran; a K run with `audio_changed == 0` is FAIL, not a green "session survived".

### C1S Audio save with the Settings screen open, Native AA `[B]` optional (K, X)

This is the "projection raised over the user's Settings screen" half of S1. B is run only if time allows; it ends the session differently and is not a comparison for the raise question.

As C1N, with two additions before step 3: `adb -s $DEV shell am start -n $PKG/com.andrerinas.openheadunit.main.SettingsActivity --ei extra_destination 0`; `sleep 3`; `adb -s $DEV shell dumpsys activity activities | grep mResumedActivity` must show `SettingsActivity` (else void and retry once). After the apply, sample `mResumedActivity` at +10 s, +30 s and +60 s. At the end `input keyevent 4` and confirm `SettingsActivity` is gone. The session may or may not re-form while Settings is open; record both outcomes.

**PASS (K and X):** `mResumedActivity` shows `SettingsActivity` at all three samples; no `AapService: raising the projection by` line appears while a sample still reads `SettingsActivity` (a raise after the BACK keyevent at the end is allowed); `FATAL EXCEPTION` 0. **FAIL** if the projection took the foreground over Settings at any sample: that is S1's second consequence reproduced and the host classifies it. Record `Attempting active poke to device` and `Not raising the projection, the settings screen is open` counts.

### C2 The restart-audio path, Native AA `[B]` (K, X for C2.2)

One session per sub-run; `wifi-connection-mode=3`.

**C2.0 Verb reachability (B and K).** Session live and carrying video. `mark C2.0-start; send ACTION_RESTART_AUDIO; sleep 4`. Grade, over the window: the `AutomationReceiver:` line for the verb present (else void); `AapService: Received request to restart audio` count and `AapAudio: Restarting all audio tracks` count. Expected from the code, and **equal on both arms**: both 0, meaning the verb never reaches the receiver. Record the counts as a **finding**. If both are >= 1 the verb works and `restart_audio` will say `via-verb` for the rest of the round; either outcome is a PASS of the characterisation, a differing pair across arms is a FAIL to investigate.

**C2.1 Positive control, no setting change (B, K).** `restart_audio` with nothing changed. PASS on both arms: the receiver line landed (route recorded), `AapAudio: Restarting all audio tracks` == 1, `audio settings changed` == 0, `ACTION_QUERY_STATE` `"connected":true` at +10 s, no `session state disconnected`.

**C2.2 Config changed behind the app, then the restart (S2) (B and K).** `setkey static-audio-focus <VFLIP>`, `mark C2.2-apply`, `restart_audio`. Expected: **B** tracks restart and the session lives (same PASS shape as C2.1); **K** ends the session (`audio_changed` == 1, `link_lost` == 1) and re-forms within 150 s (second SSL). Grade each arm against its own expectation and state the S2 verdict: `S2 reproduced` when K ends the session and B does not. A macro that restarts audio after a settings import now drops projection; that is the finding.

**C2.3 A settings import alone (B, K).** `setkey static-audio-focus <VFLIP>` and nothing else; `sleep 30`. PASS on both: `ACTION_QUERY_STATE` `"connected":true`, no `session state disconnected` in the window, `audio settings changed` == 0. This shows the import is not itself a session end on either arm.

### C6a A user connect over a live session, and the replacement serialisation `[B]` (K, X)

This is the code path `CommManager` changed (`connect` replacing a session under `transportLifecycleLock`, the stale transport unable to retire its replacement). `wifi-connection-mode=3`.

1. `bringup`; `carrying_video`.
2. `mark C6a-start; send ACTION_CONNECT --es ip 192.0.2.1 --ez no_ui true` (an address nothing answers; the connect fails). Record the wall clock.
3. Read `SSLB=$(cnt $CAP "SSL handshake complete")` just before step 2's verb, then wait up to 180 s with `waitfor $CAP "SSL handshake complete" 180 $SSLB` (the wireless stack's give-back).
4. `mark C6a-end`.

Measure: the `ConnectionArbiter:` lines in order with timestamps (`preempts`, `ended with no session; giving back`); `Connect already in progress; closing the handed-over socket` count; `T_giveback` = gap from the verb's `AutomationReceiver:` line to the arbiter's `ended with no session; giving back` line; `T_ssl2` = gap from the verb to the second SSL; count of `SSL handshake complete` over the window; counts of `FATAL EXCEPTION`, `AapRead: Magic Garbage detected in header`, `Failed to join threads` (B) / `AapTransport cleanup` (K, X).

**PASS (K, X, and B as reference):** a `preempts` line naming a user claim over the wireless one; a `giving back` line; a second SSL within 180 s of the verb; total `SSL handshake complete` in the window == 1 (the new one only; the first session predates the marker) and no third; `FATAL EXCEPTION` 0; `AapTransport cleanup ... failed` 0. FAIL if K or X finishes with no second SSL in 180 s while B has one, or shows a second SSL formed twice (a stale transport reviving).

### C7 Combo: one 20 minute Native AA session carrying the three branches' decisive scenarios `[B]` (B and X; K is read from C1N)

This is the only run where the three branches meet. Run it on **B and X**; the K column in the comparison table is filled from C1N and C3. The same run script serves both arms, so numbers are comparable. Keys: `wifi-connection-mode=3`, `view-mode=2` (GLES), `video-codec=H.265`, `log-level=2`, and the backup's rig audio keys untouched. The phone (D-POCO) must be playing music so album art arrives. Source: VLC, as the audio-sink rounds did it.

```bash
adb -s $PH shell pm list packages | grep -c org.videolan.vlc                 # 0 means no VLC: album-art leg INCONCLUSIVE
adb -s $PH shell ls /sdcard/Music | head -20                                   # need >= 3 tracks; fewer or none: album-art leg INCONCLUSIVE
adb -s $PH shell appops set org.videolan.vlc MANAGE_EXTERNAL_STORAGE allow
TRACK="/sdcard/Music/$(adb -s $PH shell ls /sdcard/Music | head -1 | tr -d '\r')"
adb -s $PH shell am start -a android.intent.action.VIEW -d "file://$TRACK" -t audio/mpeg -n org.videolan.vlc/.StartActivity
sleep 5; adb -s $PH shell dumpsys media_session | grep -c 'state=PLAYING'      # >= 1 or the album-art leg is INCONCLUSIVE
```

If the executor's `hur-wifi-test-scripts/` lists a VLC launch script, use it instead. The album-art leg is INCONCLUSIVE when any of the above fails, or when P1 ends with fewer than 5 `AapRead: largest message body so far` lines; every other leg still runs and is graded. Keep the keep-alive watchdog (if the rig has one) off.

Timeline from the first `SSL handshake complete` (= T+0). Every phase is bracketed with `mark C7-Pn-start` and `-end`.

| Phase | When | Action |
|---|---|---|
| P0 | T+0 to T+1:00 | `bringup`; `carrying_video`; record `Media Sink Setup Request: N on channel VIDEO` (7 is H.265, 3 is H.264) |
| P1 healthy stream with album art | T+1:00 to T+6:00 | `mark C7-P1-start`; start playback (above); `for n in $(seq 1 13); do mark C7-skip-$n; adb -s $PH shell input keyevent KEYCODE_MEDIA_NEXT; sleep 20; done`; `mark C7-P1-end` |
| P2 GLES cover and return x3 | T+6:00 to T+9:30 | `mark C7-P2-start; cover_return 5 1; cover_return 45 2; cover_return 15 3; mark C7-P2-end` (function below) |
| P3 healthy idle | T+9:30 to T+12:00 | nothing |
| P4 audio save | T+12:00 | `mark C7-P4-start; SSLB=$(cnt $CAP "SSL handshake complete"); TPUT0=$(cnt_rendered $CAP); setkey static-audio-focus <VFLIP>; mark C7-P4-apply; restart_audio; waitfor $CAP "SSL handshake complete" 150 $SSLB` then `carrying_video $CAP`; `mark C7-P4-end` |
| P5 healthy after reconnect | second SSL to +3:00 | `mark C7-P5-start; sleep 180; mark C7-P5-end` |
| P6 exit | after P5 | `mark C7-P6-start; send ACTION_EXIT; sleep 3; adb -s $DEV shell dumpsys wifip2p \| grep -i groupFormed; sleep 5; mark C7-P6-end`; then the C3 per-cycle reads on `win $CAP C7-P6` |

**P2 cover and return.** If `run_r5_cover_return.sh` is in the inventory, run it with the three holds (5, 45, 15 s) and still apply the teardown proof below. Otherwise use this pair (the Settings cover and the launcher `monkey` return the video-retirement brief uses):

```bash
cover_return() {   # HOLD_SECONDS CYCLE_NUMBER
  S0=$(( $(cnt $CAP "Decoder stopped:") + $(cnt $CAP "New surface set:") ))
  mark C7-P2-$2-cover
  adb -s $DEV shell am start -n $PKG/com.andrerinas.openheadunit.main.SettingsActivity --ei extra_destination 0
  sleep "$1"
  mark C7-P2-$2-return
  adb -s $DEV shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null
  sleep 12
  S1=$(( $(cnt $CAP "Decoder stopped:") + $(cnt $CAP "New surface set:") ))
  echo "cycle $2 teardown_proof=$([ $S1 -gt $S0 ] && echo yes || echo no) T_return_ms=$(after $CAP "C7-P2-$2-return" | gap /dev/stdin "C7-P2-$2-return" "First frame rendered")"
}
```

**Prove the cover took effect before counting the cycle:** `teardown_proof=yes` means a `Decoder stopped:` or `New surface set:` line appeared between cover and return; `no` makes that cycle INCONCLUSIVE (a Home press does not tear a surface down on every backend, template section 7a, projection) and one more cycle is tried, up to 6 attempts for the 3 counted. Also record `adb -s $DEV shell dumpsys activity activities | grep mResumedActivity` after each return (the projection should be back in front; say what it was).

Measure per phase:

- **P1 (zero discards on a healthy stream, with album art and H.265):** over P1 and P3 and P5 windows, counts of each of `ORPHANED_FRAGMENT`, `TRUNCATED_RUN`, `DELTA_CHANGED` (lines beginning `AapRead:`), `AapVideo: Previous frame was truncated`, `AapVideo: Orphaned fragment`, `AapVideo: reassembly anomalies over`, `AapRead: invalid framing or TLS session` (X only), `AapMediaPlayback: Failed to parse metadata`; the number of `AapRead: largest message body so far` lines (proof album art bodies arrived; pair every count with this number, because a zero with no large body proves nothing); the median fps over the `Throughput over` windows of P1 (`(Mfps)` field) and the share of windows with `rendered=0`.
- **P2:** per cycle `T_return` = gap from the return marker to the first `First frame rendered` after it; whether `cycling video focus` appears within 3000 ms after `New surface set:`; counts of `Forcing restart (`.
- **P4:** the C1N measures (`audio_changed`, `link_lost`, `T_disc`, `T_ssl2`, `T_video2`, poke count).
- **P6:** the C3 per-cycle reads.

**PASS on X** (each bullet over the whole run):

- P1/P3/P5 healthy-window discard counts all 0, with at least 5 `AapRead: largest message body so far` lines over P1 (if fewer, the album-art claim is unproven: report INCONCLUSIVE for that leg and PASS or FAIL the rest)
- median P1 fps on X >= 0.9 x B's median P1 fps, and `rendered=0` windows on X <= B's count + 1
- P2: 3 cycles counted; every `T_return` <= max(5000 ms, B's matching cycle + 1500 ms); **record whether `cycling video focus` followed `New surface set:` on X against B** (the video-retirement branch's review predicts it is missing on X for the GLES return; that is a finding to report either way)
- P4: C1N's K PASS conditions, with a second SSL within 150 s and rendered video within 30 s of it, and **zero** discard lines in the first 60 s after the second SSL
- P6: the C3 PASS conditions for one cycle
- over the whole run: `FATAL EXCEPTION` 0; `Forcing restart (` on X <= B + 1; no `SSL handshake complete` beyond the two this run causes

**PASS on B** (reference): the same measures are recorded with no pass condition except `FATAL EXCEPTION` 0; a B run that cannot complete P1 to P6 is a rig failure and X's comparison is INCONCLUSIVE.

### C1X Three early audio saves and the starvation cap `[B]` (K only, B analog; run last in Stage A)

S1's third consequence: a session that has not rendered a frame yet and ends counts toward the cap, and three in a row write it. This run dirties state (the cap forces 720p30 and AAC on every later session), so it is last, and the cap is deleted from the settings before and after.

Procedure per attempt (max 6 attempts per arm): `bringup`-style arm of a fresh session; a watcher fires on the session's first SSL and sends the save as fast as the shell allows, in **one** device-side invocation to minimise latency:

```bash
cat > early.sh <<'EOS'
#!/system/bin/sh
# early.sh KEY VALUE : set the key then restart audio, two broadcasts in one shell
am broadcast -f 0x00000020 -n com.andrerinas.headunitrevived/com.andrerinas.openheadunit.automation.AutomationReceiver -a com.andrerinas.openheadunit.ACTION_SET_SETTINGS --es json "{\"format\":\"open-headunit-settings\",\"version\":1,\"settings\":{\"$1\":$2}}" >/dev/null
am broadcast -a com.andrerinas.openheadunit.aap.action.RESTART_AUDIO -p com.andrerinas.headunitrevived >/dev/null
EOS
adb -s $DEV push early.sh /data/local/tmp/early.sh >/dev/null
# on D-HU / D-POCO the last broadcast is rejected by the not-exported check; wrap it: su -c "am broadcast --user 0 -a ... -p ..." inside early.sh if the restart_audio helper reported via-root-broadcast
watch 'SSL handshake complete' adb -s $DEV shell sh /data/local/tmp/early.sh static-audio-focus <VFLIP>
# arm the watcher BEFORE the session starts (before bringup's arming verb), then unwatch after the attempt
```

An attempt **counts as early** when no `Throughput over ...: rendered=N` line with N greater than 0 appears between that attempt's SSL and its apply. After each attempt the session ends (K: the apply; B analog: the same early.sh then `send ACTION_DISCONNECT` 2 s later, because on B the apply does not end it and B's real Save equivalent is a service stop, which also counts the session). Flip `VFLIP` back to `V` between attempts so each attempt has something to change. Stop at 3 consecutive early attempts or 6 attempts.

**Measure:** the number of consecutive early attempts reached; the count of `sessions in a row ended without a single video` lines (the third one is the advice); the settings file's `video-profile-starvation-cap` after the app is stopped (only readable if D-HU's `shared_prefs/` was fixed in R0; otherwise the log line stands alone and Setup notes say so).

**PASS (K):** a characterisation, not a green: record `cap reached after N early attempts` or `cap not reached (E early of A attempts)`. PASS requires >= 3 consecutive early attempts (otherwise INCONCLUSIVE, the race could not be won) and that K and the B analog agree on whether the cap is written (K is no worse than B if both write it; K is a regression only if K writes it and B does not). **Cleanup is part of the run:** `DEL:video-profile-starvation-cap` from the settings, read back absent.

### C1H Audio save mid-session, Headunit Server (K and X; `[B]` control) (Stage B)

Gate G-SRV first. Keys: `wifi-connection-mode=1`; D-HU on the house LAN with the server phone on the same LAN (`adb -s $HU shell ip -4 addr` and the phone's address recorded; the mode discovers the phone by NSD).

As C1N, with the arming `send ACTION_START_WIRELESS_SCAN` and a second SSL budget of 90 s. The deciding lines after the apply are `AapService: Disconnected. Restarting discovery loop in 2s...` or `AapService: Unclean WiFi disconnect in Auto Mode. Retrying discovery in 2s...` (one of them must print within 3 s of the session-state `disconnected`), then the second SSL.

**PASS (K, X):** the C1N conditions with the second SSL within **90 s**, plus one of the two discovery-restart lines within 3 s of `T_disc`; the arm's capture shows `FATAL EXCEPTION` 0. **If no second SSL forms in 90 s**, before grading read G-SRV again (the table): if the server row is gone, the phone's server went deaf (a peer that vanished without a FIN) and the run is INCONCLUSIVE with H3 as the fix; if it is still listening and no handshake happened, it is a FAIL. **B control:** C1N's B PASS shape.

### C1M Audio save mid-session, manual / IP (K; `[B]` control) (Stage B)

Keys: `wifi-connection-mode=0`. Arm with `send ACTION_CONNECT --es ip <server phone address> --ez no_ui true` (record the reply), wait for SSL (45 s), `carrying_video`, then the C1N steps 3 and 4 with a **60 s** wait for an automatic second SSL.

**PASS (K):** `audio_changed` == 1; the session ended; **no** automatic second SSL within 60 s (the review's finding: manual mode has no reconnect; record it as a number, 0 or 1); then `send ACTION_CONNECT --es ip <addr> --ez no_ui true` forms an SSL within 45 s of the verb; `FATAL EXCEPTION` 0. **B control:** tracks restarted, session alive.

### C5 Nearby: exit during an in-flight handshake `[B]` (K, X optional) (Stage N)

Keys: `wifi-connection-mode=2`, `helper-connection-strategy=2`. The phone carries the Wireless Helper app set to Google Nearby (one-time UI setup done in an earlier thread; confirm it with the discovery line below, not by assumption).

1. `send ACTION_START_WIRELESS_SCAN`; wait up to 60 s for `NearbyManager: Endpoint FOUND:`. Take the endpoint id from that line. If none appears in 60 s: **UNTESTABLE** (record the Helper app's mode).
2. Arm a watcher, then connect:
   ```bash
   watch 'NearbySocket: Blocking read until InputStream is AVAILABLE via Nearby Payload' send ACTION_EXIT
   send ACTION_NEARBY_CONNECT --es extra_endpoint_id <id>
   # after the attempt: unwatch
   ```
3. If the watcher never fires in 30 s, the handshake did not reach the read: attempt again, up to 5 attempts, then INCONCLUSIVE.

**Measure:** `T_exit` = gap from the watcher's `AutomationReceiver:` exit line to `AapService destroying`; the count of `AapService: the wireless teardown did not finish in`; and a second leg on the same arm: re-arm, repeat steps 1 and 2 but send `ACTION_DISCONNECT` instead of `ACTION_EXIT` and `ACTION_NEARBY_CONNECT --es extra_endpoint_id <id>` again 1 s later; `T_reconnect` = gap from that second connect verb to the next `NearbyManager: Requesting connection to endpoint:`.

**PASS (characterisation of S3):** no `FATAL EXCEPTION`; `T_exit` and `T_reconnect` reported for B and K. FAIL only if K's `T_exit` exceeds 9000 ms or K never reaches `AapService destroying`. A K `T_exit` between 5000 and 9000 ms with B under 2000 ms is `S3 reproduced`, a finding and not a fail.

### C4 USB (Stage C, operator present; D-POCO is the head unit)

Keys for all of C4: `wifi-connection-mode=3` unless stated, `use-libusb=true` unless stated, `auto-start-on-usb=true`, `reopen-on-reconnection=true`, `auto-connect-last-session=true`, `kill-on-disconnect=false`, the legacy keys deleted. Each run brackets the whole window; **the operator is cued by this watcher, never by a clock**:

```bash
cue() { printf '\a'; echo "OPERATOR: $1 NOW"; echo "$(date +%T) $1" >> cues.log; }   # the operator watches this console
waitfor_detach() { waitfor "$CAP" "USB_DEVICE_DETACHED" 120 "$1"; }                 # $1 = detach count read just before the cue
waitfor_plug()   { waitfor "$CAP" "accessory mode" 120 "$1"; }                       # matches both USB-attempt lines; $1 = count before the cue
```

Take the first USB session of each arm once before grading so the app has `last-connection-type` USB: plug the dongle, wait for `SSL handshake complete`, `send ACTION_DISCONNECT`, then run the cases. Discard rules apply; a `MATCH! Starting AapService` here is a Bluetooth arrival and voids the plug.

**C4a Unplug mid-session x10 `[B]` (K, X not needed).** Per cycle, standard route (`use-libusb=false`) for cycles 1 to 5 and libusb (`use-libusb=true`) for cycles 6 to 10:

1. `SSLB=$(cnt $CAP "SSL handshake complete"); TPUT0=$(cnt_rendered $CAP); n1=$(cnt $CAP "accessory mode"); cue PLUG; waitfor_plug $n1`, then `waitfor $CAP "SSL handshake complete" 120 $SSLB`, then `carrying_video $CAP`.
2. `n0=$(cnt $CAP USB_DEVICE_DETACHED); cue UNPLUG`; `waitfor_detach $n0` (max 120 s). Record the detach line time `t_d`.
3. Within 10 s read: `AapService: USB disconnect. Scheduling reconnect check in`, `Disconnected. wasPlayingBeforeDisconnect=`, `AapTransport quitting (clean=`, and the session-state `disconnected` reason.
4. 5 s later `SSLB=$(cnt $CAP "SSL handshake complete"); cue PLUG; waitfor $CAP "SSL handshake complete" 60 $SSLB`. Record `T_replug` = gap from the plug's `Switching USB device to accessory mode` or `Found device already in accessory mode` to SSL.

**PASS (K):** over the completed cycles (need >= 8): the session-state `disconnected` within 3000 ms of `t_d` on every cycle; `AapService: USB disconnect. Scheduling reconnect check in` present on every cycle; `FATAL EXCEPTION` 0; `AapTransport cleanup ... failed` 0; a new SSL on every replug within 60 s with no second replug; median detach-to-`disconnected` of K <= B's median + 1000 ms. **No stuck "no reconnect" state** means every replug re-forms a session, which is the 60 s bound above.

**C4b Disconnect racing the AOA switch and open x5 `[B]` (K).** A software substitute for "unplug during the switch" (the operator cannot act inside the window): a watcher fires `ACTION_DISCONNECT` at a chosen offset after the accessory line, five offsets, one per attempt: 0, 150, 400, 900, 1800 ms.

```bash
# attempt k, with OFFSET_MS in {0,150,400,900,1800}
watch 'Switching USB device to accessory mode|Found device already in accessory mode' after_ms $OFFSET_MS send ACTION_DISCONNECT
cue PLUG
# ... run the attempt, then: unwatch
```

Then, 10 s after the disconnect line, `send ACTION_CHECK_USB` (lifts any hold; the dongle is still plugged) and wait up to 60 s for SSL.

**PASS (K):** on every attempt `FATAL EXCEPTION` 0, `AapRead: Magic Garbage detected in header` 0, **no `SSL handshake complete` between the `ACTION_DISCONNECT` and the `ACTION_CHECK_USB`** (a handle or transport leaking back to life), and an SSL within 60 s after `ACTION_CHECK_USB` on >= 4 of 5 attempts. Report `Failed to read AAP header` and `still receiving late VERSION_RESPONSEs at the deadline` counts (both expected 0). An attempt where the offset landed after SSL (an SSL line precedes the disconnect) is not counted; replace it, up to 8 attempts.

**C4c Replug storm x5 `[B]` (K).** `cue PLUG`, wait for the first `Found device already in accessory mode` or `Switching USB device to accessory mode`, then five times: `cue UNPLUG`, `waitfor_detach`, `sleep 2`, `cue PLUG`, `sleep 2`; then leave plugged and wait up to 90 s for SSL.

**PASS (K):** an SSL within 90 s of the last plug; `FATAL EXCEPTION` 0; the number of `Switching USB device to accessory mode` plus `Found device already in accessory mode` lines over the window <= 40 (record B's count; K above 2 x B's + 5 is a FAIL); `AapRead: Magic Garbage detected in header` 0; list every `ConnectionArbiter:` line.

**C4d First failed handshake in a fresh process `[B]` (K).** The new `hasEverConnected` behaviour. `wifi-connection-mode=3`. **H2:** D-MOTO powered off (so the dongle's phone is unreachable and the handshake fails), then `adb -s $DEV shell am force-stop $PKG` (the process is fresh), launch `MainActivity`, `cue PLUG`, record 120 s, **never touch the pill**.

Measure over the 120 s: count of `Found device already in accessory mode` plus `Switching USB device to accessory mode` (attempts); count of `AapService: USB disconnect. Scheduling reconnect check in`; counts of `AapService: userExitedAA is true. Skipping auto-poke.`, `ConnectionArbiter: ` lines by kind; counts of `WifiLauncher: Initializing WiFi Mode:` and `createGroup SUCCESS`; counts of `Failed to read AAP header` and `still receiving late VERSION_RESPONSEs at the deadline`. Then **H2 again:** power D-MOTO on, wait until Bluetooth and WiFi are up (`dumpsys` reads), `cue UNPLUG`, 15 s, `cue PLUG`, wait up to 120 s for SSL.

**PASS (K):** attempts in the 120 s <= 40 and <= 2 x B's attempts + 5; `userExitedAA is true. Skipping auto-poke.` 0; `FATAL EXCEPTION` 0; the SSL after D-MOTO returns within 120 s. A K attempt count above the bound is a FAIL (a retry storm the first-failed-handshake change introduced); report B's count either way. If D-MOTO's WiFi or Bluetooth cannot be confirmed up, the second half is INCONCLUSIVE.

### C1U Audio save mid-session, USB (K; `[B]` control) (Stage C)

Standard route, `use-libusb=false`, `wifi-connection-mode=0`, the USB keys. Plug, wait for SSL, `carrying_video`, then the C1N steps 3 and 4 with a **60 s** wait for a second SSL **without a replug**. The expected K behaviour is `AapService: USB disconnect. Scheduling reconnect check in` after the apply and a re-formed session. This run needs the restart route on D-POCO (root broadcast on API 35); if `restart_audio` prints `NOT-LANDED`, INCONCLUSIVE.

**PASS (K):** the C1N conditions with the second SSL within 60 s of the apply, plus the USB reconnect-check line within 3 s of `T_disc`; or if no second SSL forms in 60 s, record how long the hold lasted and the arbiter lines (the settings-screen hold may defer it) and grade FAIL only if no SSL forms within 180 s. **B control:** tracks restarted, session alive.

### C6b A USB plug against an in-flight wireless handshake, and the reverse `[B]` (K) (Stage C)

Five plugs, each: `wifi-connection-mode=3`, Native armed (`send ACTION_START_WIRELESS_SCAN`, confirm `WifiLauncher: Initializing WiFi Mode:` suffix `NATIVE`); D-MOTO paired with the dongle and also able to wake over Bluetooth. Fire the plug cue on the poke so the two attempts overlap:

```bash
watch 'NativeAA: Attempting active poke to device' cue PLUG
# ... run the plug, then: unwatch
```

Record the `ConnectionArbiter:` lines in order, which tier won (a USB claim should preempt a wireless handshake in flight), which SSL formed first, and that the loser comes back: for a USB win, `ended with no session; giving back` and then a wireless SSL after the USB session ends (`ACTION_DISCONNECT` then); for a wireless win, USB retries after the wireless session ends.

**PASS (K):** on every plug exactly one SSL forms first and its session lasts >= 20 s (`carrying_video`); the losing path's re-arm line (`giving back` or `USB has ... ms to try again`) appears within 3000 ms of that session ending or the loser's attempt ending (`1500 ms` is the arbiter's own settle); `FATAL EXCEPTION` 0; no second concurrent SSL. B is the reference for which tier won. A plug where the poke never fired inside 60 s is replaced, up to 8 attempts.

### C7U Combo, USB session (X; `[B]` reference) (Stage C)

On D-POCO plus the dongle plus D-MOTO, `wifi-connection-mode=0`, `view-mode=2`, `use-libusb=true`, `video-codec=H.265`. One 8 minute USB session: `cue PLUG`; SSL; P1-style healthy window of 4 minutes with `KEYCODE_MEDIA_NEXT` sent to D-MOTO every 20 s; then the C1U apply (`setkey` + `restart_audio`) and a wait up to 90 s for a re-formed session; then `send ACTION_EXIT` and the exit reads.

**PASS (X):** the healthy-window discard counts all 0 (with the `largest message body` count reported); the apply re-forms a session within 90 s; the exit leaves `AapService destroying` within 5500 ms; `FATAL EXCEPTION` 0; no `AapRead: invalid framing or TLS session` before the exit. B is run the same way for the comparison.

### C8 Old Android (Stage D) `[B]` (K)

**D-SAM (API 19), Native AA with D-POCO as the phone:** `wifi-connection-mode=3`, `screen-orientation=2`, settings through `put_prefs` (the push-the-whole-file branch; `set_prefs_runas_host.py` in the inventory does the same if `put_prefs` misbehaves). Charge the tablet from a separate supply first. Per arm: `bringup <file> 1`; `carrying_video`; **the C1N sequence once** (with its own `SSLB` base) (`setkey`, `restart_audio` as the implicit broadcast without `-p`); then **C3 three times** exactly as C3's cycle steps (cycle 1 follows the C1N reconnect, so it is the session already live; cycles 2 and 3 use `bringup <file> 0`, one window file per cycle) with `groupFormed` and `T_destroy` (at `log-level=2` like every run). D-SAM prints two `createGroup SUCCESS` before SSL, which is how the unit settles and not a discard.

**PASS (K):** C1N's K PASS with a second SSL within 180 s (slower tablet), C3's conditions on 3 of 3 cycles with `T_destroy` <= 5500 ms, `FATAL EXCEPTION` 0.

**D-HP (API 17), Headunit Server:** needs gate G-SRV. `wifi-connection-mode=1`. One session, the C1H sequence once, then `send ACTION_EXIT`. The Settings screen is never opened here. Poll `dumpsys wifi` for the station (WiFi enable takes up to 30 s). **PASS (K):** C1H's K PASS with a 120 s second-SSL budget and `T_destroy` <= 5500 ms. If the gate fails, INCONCLUSIVE.

### Closing step (after the last run, on every unit the round used)

```bash
for U in $HU $PH $SAM $HPS; do DEV=$U; put_prefs DEL:video-profile-starvation-cap DEL:allow-external-configuration; \
  adb -s $U shell run-as $PKG cat shared_prefs/settings.xml 2>/dev/null | tr -d '\r' | grep -c 'video-profile-starvation-cap'; done
```

`put_prefs` edits `settings-backup.xml`, so keep one backup file per unit (`settings-backup-<unit>.xml`, copied to that name before the call). Each count must print `0` (skip units that were not used; where `run-as` cannot read the file on a rooted unit, read it with `su -c cat`). Then restore each unit's round backup of `settings.xml` and record the final diff. A unit that still reads the cap is a finding for Setup notes, not a silent fix.

## 8. Do not re-run

Nothing from this thread has run before. Settled elsewhere and not repeated here: the version-retry and arbiter claims (the usb-version-retry thread's rounds); the video-black warm-relaunch mechanism and its timings (rounds 5 to 8 of that thread); the group-survival-across-exit recipe (10 of 10 on 2026-09-17). C3 repeats the exit recipe on purpose, as a regression gate against K's lifecycle change. The full framing matrix (injector parity, window under load, old-Android TLS) and the full video-retirement matrix (SURFACE and TEXTURE controls, codec pin over 20 cycles, screen-off, view-mode switch) belong to the two sibling briefs; C7 takes one scenario from each and stops there.

## 9. Report back

The numbers that decide shipping:

1. **S1, per transport:** for Native AA (Settings closed and open), Headunit Server, manual/IP and USB, the K row of `T_disc`, `T_ssl2`, the poke / connect / raise counts, whether the Settings screen kept the foreground, and the cap result of C1X against its B analog.
2. **S2:** C2.0's verb counts, C2.1's control, and C2.2's B-versus-K behaviour.
3. **The regression gates:** C3's per-arm tables (`groupFormed` at +3 s, `T_destroy`, timeout count), C4's replug table and attempt counts, C6's arbiter order, and the Nearby `T_exit` against B.
4. **The combo:** C7's comparison table (below), with X against B and K, and the single line `three branches coexist on hardware: yes or no`.

### Results skeleton (copy this, `pr-1047-session-reconnect-round1-results.md`)

```markdown
# session-reconnect, round 1 results

**Candidate K:** pr/split-session-reconnect @ ac741d298d825149618f6529e40b741a53818136   **Combo X tree:** <printed hash vs 8eb30402c3498a0ad30b363c3d71e091c066265c>   **Baseline B:** main @ 7e9d813d66493f1cc978c0f6a259bdb613874bd1
**APK md5:** B <md5> / K <md5> / X <md5>
**Units:** D-HU <serial, API>, D-POCO <API>, D-MOTO, D-SAM, D-HP (as plugged per stage)
**Date:** <yyyy-mm-dd>
**Evidence:** release `rig-evidence-pr-1047-session-reconnect`, asset `pr-1047-session-reconnect-round1-captures.zip`, sha256 <hash> (commands in 4.4)

## Setup notes
Every deviation from the brief and the protocol. Include: the `stat` of D-HU `shared_prefs/`; the `su` check on D-HU and D-POCO; which route `restart_audio` took per unit; the settings delta of each fresh backup; scripts used from `hur-wifi-test-scripts/`; hand steps taken (H1, H2, H3) with times; any run voided by a discard rule and its re-run count; any string that did not match.

## R0 Gate
| Arm | md5 | identity symbols | commit | JVM count |
|---|---|---|---|---|

## C3 exit x10 (per arm)
| Cycle | groupFormed +3 s | groupFormed +8 s | T_cleanup ms | T_destroy ms | timeout lines | user-exit line |
|---|---|---|---|---|---|---|
Medians per arm: B / K / X.

## C1N (B control, K, X)
| Arm | route | audio_changed | stop_action | link_lost | user_exit | T_disc ms | T_ssl2 s | T_video2 s | pokes (60 s) | connects (60 s) | raises | Not-raising lines | control restart ok |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
S1 verdict: reproduced / not reproduced.

## C1S
| Arm | resumed at +10 s | +30 s | +60 s | raises | Not-raising lines | second SSL s |
|---|---|---|---|---|---|---|

## C2
| Sub-run | Arm | receiver lines | Restarting-tracks lines | audio_changed | session after |
|---|---|---|---|---|---|
S2 verdict: reproduced / not reproduced / verb unreachable.

## C6a / C6b
| Run | Arm | arbiter lines in order | T_giveback ms | T_ssl2 s | SSL count |
|---|---|---|---|---|---|

## C7 / C7U comparison
| Measure | B | K (from C1N, C3) | X |
|---|---|---|---|
| median P1 fps | | n/a | |
| rendered=0 windows | | n/a | |
| discard-line counts P1/P3/P5 | | n/a | |
| largest-body lines (album art) | | n/a | |
| T_return per cycle (3) | | n/a | |
| cycling-video-focus after New surface (3) | | n/a | |
| audio save: T_disc / T_ssl2 / T_video2 | | | |
| exit: T_destroy / groupFormed +3 s | | | |

## C1X, C1H, C1M, C1U, C4a-d, C5, C8
One table each with the measures named in the run, per arm.

## Verdict table
| Run | B | K | X | Notes |
|---|---|---|---|---|

## Anything the brief did not ask about
```

## Decisive strings

```decisive-strings
CommManager: audio settings changed; reconnecting the projection session
AapService: Received request to restart audio
AapAudio: Restarting all audio tracks
AapService: session state
AapService: Native AA session ended; keeping the
AapService: Native AA user exit. Stopping active launcher.
NativeAA: Attempting active poke to device
NativeAA: Calling socket.connect()
AapService: Not raising the projection, the settings screen is open
AapService: raising the projection by
AapService: Disconnected. wasPlayingBeforeDisconnect=
AapService: USB disconnect. Scheduling reconnect check in
AapService: Disconnected. Restarting discovery loop in 2s...
AapService: Unclean WiFi disconnect in Auto Mode. Retrying discovery in 2s...
AapService: User exit with wirelessServer active. Not restarting discovery.
AapService: USB disconnect after user Exit. Skipping auto-reconnect
AapService: userExitedAA is true. Skipping auto-poke.
AapService: the wireless teardown did not finish in
AapService destroying
Stop action received. Broadcasting finish request to activities.
Disconnect action received.
WifiDirectManager: Stopping and cleaning up...
WifiLauncher: Initializing WiFi Mode:
AapTransport quitting (clean=
AapTransport cleanup
Failed to join threads
Starting handshake without connection
CommManager: Connect already in progress; closing the handed-over socket
sessions in a row ended without a single video
SSL handshake complete
Service Discovery Response
Channel Open Response
Media Sink Setup Request:
Media Sink Stop Request: 
Video Sink Stopped -> Ignored (Forced Keyframe Request)
Throughput over
Switching USB device to accessory mode
Found device already in accessory mode
ConnectionArbiter: 
 preempts 
stood the wireless stack down until it ends
ms to try again
USB has tried for
ended with no session; giving back
wireless bring-up refused while
a USB check waits for
Failed to read AAP header
still receiving late VERSION_RESPONSEs at the deadline
AapRead: Magic Garbage detected in header
MATCH! Starting AapService
createGroup SUCCESS
FATAL EXCEPTION
USB_DEVICE_DETACHED
NearbySocket: Blocking read until InputStream is AVAILABLE via Nearby Payload
NearbyManager: Endpoint FOUND:
NearbyManager: Requesting connection to endpoint:
AutomationReceiver: 
AutomationMarker: 
ORPHANED_FRAGMENT
TRUNCATED_RUN
DELTA_CHANGED
AapRead: %s on %s - %s%s
AapVideo: Previous frame was truncated
AapVideo: Orphaned fragment
AapVideo: reassembly anomalies over
AapRead: invalid framing or TLS session
Audio transport read channel=
AapRead: largest message body so far
AapMediaPlayback: Failed to parse metadata
New surface set:
cycling video focus
First frame rendered
Forcing restart (
Decoder stopped:
```
