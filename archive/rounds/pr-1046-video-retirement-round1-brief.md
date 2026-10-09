# pr-1046-video-retirement, round 1 brief: an outside rewrite of decoder workers and surface ownership, A/B on every backend

## 1. Build and baseline

| | Where | SHA | Gate |
|---|---|---|---|
| Candidate | `emotionbug/open-headunit`, branch `pr/split-video-retirement` | `e7c2949c1bd43df22c5d1b94dbacf7e4d3ebf385` | 2668 JVM tests, 0 failures |
| Baseline | `main` | `7e9d813d66493f1cc978c0f6a259bdb613874bd1` | 2658 JVM tests |

The candidate is an outside contributor's branch and is not on the fork. It is four commits on the baseline, so the baseline is exactly "the same tree without the branch". **Every run below is an A/B and needs both builds.** No history was rewritten.

```bash
git fetch https://github.com/emotionbug/open-headunit.git pr/split-video-retirement
git checkout e7c2949c1bd43df22c5d1b94dbacf7e4d3ebf385
git log --oneline 7e9d813d66493f1cc978c0f6a259bdb613874bd1..HEAD | wc -l      # 4
git merge-base HEAD 7e9d813d66493f1cc978c0f6a259bdb613874bd1                  # 7e9d813d66493f1cc978c0f6a259bdb613874bd1
```

Build each with `build_hur.sh` and **copy each APK out of `apks/` the moment it is built** (it deletes the previous one). Pull the installed APK and hash it locally (a piped `cat | md5sum` is not trustworthy). Version name and code do not move, so identity is a symbol plus the build stamp:

```bash
unzip -p cand.apk 'classes*.dex' | strings | grep -cF detachSurfaceIfCurrent    # >= 1 on the candidate, 0 on the baseline
unzip -p cand.apk 'classes*.dex' | strings | grep -cF outputPublicationLock     # >= 1 on the candidate, 0 on the baseline
send ACTION_QUERY_STATE      # the reply's commit begins e7c2949c on the candidate, 7e9d813d on the baseline
```

Install with `adb install -r <named apk>` every time, never through `set_hu_pref.sh` (it reinstalls whatever is newest in `apks/`). Record both md5s, confirm the live one before every run with `pm path` plus a pulled copy, and read the `commit` in `ACTION_QUERY_STATE` before every run.

### R0. Gate

`run_unit_tests.sh` on the candidate. **PASS:** 2668 tests, 0 failures, counted from the JUnit XML, including `VideoOutputRetirementTest` and `VideoSurfaceRetirementTest`; the two md5s differ; both identity greps pass. A build or test failure stops the round.

## 2. What this is and why it exists

The candidate does three things to `VideoDecoder`, `AapProjectionActivity` and `ProjectionView`:

- **Surface ownership.** The activity remembers the Surface it was given. `onStop`, the view recreate and a new `onDestroy` step stop or detach the decoder only if that Surface is still the decoder's. `stopIfCurrentSurface` keeps the decoder's Surface; the new `detachSurfaceIfCurrent` nulls it and stops.
- **Resize handling.** `onSurfaceChanged` for the same Surface the decoder still holds is now a resize: it skips `settleFocusCycle`, the warm-relaunch re-arm and the unsolicited focus gain.
- **Output-worker fencing.** The output thread captures its codec once and publishes under a new lock only while it is the current worker. Its log lines and callbacks are queued and dispatched outside the lock, and only while the worker is still current.

The JVM tests cannot cover an Activity or a View, so the rig is the only coverage the surface half gets. A code review found one blocking regression by reading, and this round decides it:

- **B1, the headline.** On GLES, Home or screen-off does not tear the Surface down (the renderer keeps the same Surface until the view detaches). Baseline re-runs its warm-relaunch re-arm on every return because `onSurfaceChanged` fires again. The candidate sees "same Surface" and skips it. Predicted result: the first return is repaired only by the reconnecting watchdog (about 5 s after `onResume`) instead of about 0.85 s, and later returns keep `warmRelaunchCycleSpent` true so only the inert gain goes out and the picture stays gray until the phone's own keyframe, up to about 69 s. A call screen over a GLES projection would take the same path.
- **TEXTURE is not cleared by the review and is treated here as untested.** The review says SURFACE and TEXTURE behave as on baseline. Video-black round 5 measured TextureView keeping its SurfaceTexture across a full-screen cover on D-HU (no `TextureProjectionView: Surface destroyed`), which is the same condition that causes B1 on GLES.
- **S1.** Output-thread lines are queued, so one is lost if the worker retires between publish and dispatch (`Throughput over` advances its counters first), and their `Class.method` prefix changes. The affected lines: `Throughput over`, `First frame rendered (hardware decode)`, `Decoder stall detected`, `Codec exception in output thread`, the concealment lines, `keyframe decoded - the picture is repaired`.
- **S2.** `detachSurfaceIfCurrent` returns false silently, where the old call logged `Decoder stop (...) skipped: surface is no longer current`, and `onStop`, recreate and `onDestroy` skip when `ownedSurface` is null. So `Decoder stopped:` lines can be missing with nothing saying why.

Rig facts this builds on, all measured earlier and not re-measured:

- Home and a full-screen cover never tear down a GLES or TEXTURE surface on D-HU; SURFACE is torn down by a cover (video-black rounds 1 and 5).
- Baseline's warm-relaunch escalation fires 850 ms after the surface is claimed and puts a repaired picture up about 2.0 s after the return (round 8, launcher route).
- The phone's keyframe period is a fixed ~69 s, and a focus-cycle release then regain pulls an on-demand IDR within 0.5 to 0.8 s.
- A rendered frame is not a picture: a codec rebuilt from cached parameter sets renders gray P-frames, so `rendered=` and `fps` stay healthy while the picture is gray. Grade picture on the two lines in section 6, never on the throughput counter.

## 3. What is different about this round

- **Two return routes, because the baseline's escalation hangs off a different event on each.** `ACTION_RAISE_PROJECTION` (route H, S) re-delivers the existing activity with `FLAG_ACTIVITY_REORDER_TO_FRONT`, so it is the clean "same Surface returns" case. The launcher route (route L: Settings cover, then `monkey ... LAUNCHER 1`) goes through `MainActivity.onResume` and may recreate the projection activity and its Surface. Both are run on every backend. `monkey` takes about 26 s to inject (video-black round 5), so grade only from device timestamps and never from host command times.
- **Screen off and on is `input keyevent 223` then `224`**, OS-level and not an app action, so it is allowed. After the wake the activity should resume by itself; the helper dismisses a keyguard and raises only if it does not.
- **Pre-registered INCONCLUSIVE outcomes**, none a failure:
  - the B1 path is never reached on a candidate GLES run (section 7, V1G);
  - V5D, if the density change does not recreate the activity;
  - V6A's strike-threshold half, if the baseline never reaches a stall under that load;
  - V7P, if D-POCO's head unit server is not listening on `:5277` (it is UI-only and was down after an earlier round).
- **UNTESTABLE, with the reason:** picture-in-picture (entered only from a dialog inside the projection UI, no verb); a call raised over the projection (Self Mode only); rotation (D-HU is not rotated by the operator's instruction, and the projection activity declares `orientation|screenSize` so rotation does not recreate it); a live view-mode switch (Quick Settings only; `ACTION_SET_SETTINGS` imports the value but does not broadcast the view-recreate). The first three route to `VideoSurfaceRetirementTest`; a hand step for any of them is optional and never a verdict.
- **The rig's audio settings are a deliberate worst case** (`use-aac-audio` on, `audio-latency-multiplier`, `audio-queue-capacity` 20). Do not touch them. Read them back and record them in Setup notes.
- **D-HU hard-reboots under sustained multi-core load.** V6A is the only load run, it is capped at 4 steady minutes, and `adb devices` losing D-HU during it stops the round (rig broken, escalate).
- **Never run adb calls in parallel against one unit.** Screenshots are sequential with `sleep 0.3`.

## 4. Settings keys

Write with the app stopped, on the host, with `ohu_setkeys.py` (section 5), so a `<set>` key can never be orphaned. Read every key back before launch. **Diff the unit's `settings.xml` against a fresh backup at the start of the round and state the delta in Setup notes.**

| Key | Type | Value | Notes |
|---|---|---|---|
| `view-mode` | int | `2` GLES, `1` TEXTURE, `0` SURFACE | per run; enum `SURFACE(0) TEXTURE(1) GLES(2)` |
| `video-codec` | string | `H.264` | `H.265` in V1H only. The string is compared literally |
| `resolutionId` | int | `3` | 1080p. Confirm what the phone asked for with `Media Sink Setup Request: 3 on channel VIDEO` |
| `force-software-decoding` | boolean | `false` | `true` in V6A only |
| `software-video-decoder` | int | delete | `0` (device MediaCodec) in V6A only. The default routes through bundled FFmpeg, which has no output thread and measures nothing here |
| `debug-video-fault-injection` | int | delete | `2` in V6B only |
| `debug-video-fault-rate` | int | delete | `3` in V6B only |
| `debug-video-fault-budget` | int | delete | `40` in V6B only |
| `video-profile-starvation-cap` | boolean | delete | a latch that forces 720p30 and AAC after three fumbled sessions; read it back before every run |
| `wifi-connection-mode` | int | `3` | `1` on D-HP |
| `log-level` | int | `2` | INFO carries every decisive line below; VERBOSE only wraps D-HU's ring buffer |
| `night-mode` | int | `1` | DAY, so the night-mode toggles in V1G and V2S move nothing else |
| `native-driver-selection-mode` | int | `0` | stack armed, no driver selector countdown |
| `onboarding-version` | int | `2` | only if the wizard would otherwise intercept the launch |

Left exactly as found, recorded in Setup notes: `use-aac-audio`, `audio-latency-multiplier`, `audio-queue-capacity`, `enable-audio-sink`, `fps-limit`, `screen-orientation`, every key not listed. On D-SAM and D-HP write only `wifi-connection-mode`, `log-level`, `native-driver-selection-mode` (D-SAM), `onboarding-version` and the starvation delete: never pin their view mode, codec or resolution, and read what they are in Setup notes.

```bash
COMMON="str:video-codec=H.264 int:resolutionId=3 bool:force-software-decoding=false del:software-video-decoder del:debug-video-fault-injection del:debug-video-fault-rate del:debug-video-fault-budget del:video-profile-starvation-cap int:wifi-connection-mode=3 int:log-level=2 int:night-mode=1 int:native-driver-selection-mode=0 int:onboarding-version=2"
```

## 5. Every action as a verb, and the helpers

Every action on the app is `send ...` (template section 3). The ones this round uses:

| Step | Command | Why not a tap |
|---|---|---|
| marker | `send ACTION_LOG_MARKER --es text <label>` | prints `AutomationMarker: <label>` at WARN; not gated on this build |
| return to the projection | `send ACTION_RAISE_PROJECTION` | `AapService.launchAapProjectionActivity` logs `raising the projection by <route>` |
| identity | `send ACTION_QUERY_STATE` | reply carries `commit` |
| connect to a head unit server (D-HP) | `send ACTION_CONNECT --es ip <addr>` | TCP to `:5277` |
| exit between runs | `send ACTION_EXIT`, then `sleep 3`, then `am force-stop` | stops the service; a deep link would cold-launch a stopped app |

OS-level steps, not app actions, and so allowed: `input keyevent 3` (HOME), `223` (SLEEP), `224` (WAKEUP), `am start -a android.settings.SETTINGS` (a cover), `monkey -p <pkg> -c android.intent.category.LAUNCHER 1` (the user's launcher return), `cmd uimode night yes|no`, `wm density`, `wm dismiss-keyguard`, `screencap`. **One hand step, D-HP only:** start the head unit server in Android Auto's developer settings on D-POCO, because that toggle is UI-only (V7P). No other hand step exists in this brief.

Save these four files in `hur-wifi-test-scripts/pr-1046-round1/` and list them in Setup notes. They were run against synthetic captures before this brief was written; if one does not match the real line format, fix it, say so in Setup notes, and keep going.

**`ohu_lib.sh`**, sourced after setting `HU` (unit serial), `PH` (phone serial), `OTHER` (the other phone, or empty), `OUT` (evidence dir), `PUT` (`hu_put` or `tab_put`) and `BASEXML` (the backup):

```bash
# ohu_lib.sh : source it. Needs HU (serial), PH (phone serial), CAP (capture file), OUT (evidence dir).
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
send() { a=$1; shift; adb -s "$HU" shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; }
mark() { send ACTION_LOG_MARKER --es text "$1" >/dev/null; sleep 0.3; }
nl() { echo $(( $(wc -l < "$CAP") + 1 )); }
# waitfor <secs> <ERE> <from-line> : 0 when a line at or after <from-line> matches
waitfor() { local end=$((SECONDS+$1)); while [ $SECONDS -lt $end ]; do
  tail -n +"$3" "$CAP" | grep -aqE -e "$2" && return 0; sleep 1; done; return 1; }
PIC='keyframe decoded - the picture is repaired|First frame rendered \(hardware decode\)$'
# raise <RUN> <n> <route> : the return half. ACTION_RAISE_PROJECTION first, monkey only as the named fallback
raise() { local L; L=$(nl); send ACTION_RAISE_PROJECTION >/dev/null
  if ! waitfor 8 'AapProjectionActivity: onResume' "$L"; then
    mark "$1-r$2-$3-fallback-monkey"; adb -s "$HU" shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
    waitfor 90 'AapProjectionActivity: onResume' "$L" || echo "NO_RESUME $1-r$2"; fi; }
# observe <from-line> : picture, or 90 s, then a 10 s soak
observe() { waitfor 90 "$PIC" "$1" || echo "NO_PICTURE_90S"; sleep 10; }
shot() { adb -s "$HU" shell screencap -p /sdcard/s.png && adb -s "$HU" pull -q /sdcard/s.png "$OUT/$1.png"; sleep 0.3; }
ret_H() { local G; G=$(nl); mark "$1-r$2-H-go"; adb -s "$HU" shell input keyevent 3; sleep 10
  raise "$1" "$2" H; [ -n "$SHOTS" ] && { sleep 1; shot "$1-r$2-1s"; sleep 2; shot "$1-r$2-4s"; sleep 7; shot "$1-r$2-12s"; }
  observe "$G"; }
ret_S() { local G L; G=$(nl); mark "$1-r$2-S-go"; adb -s "$HU" shell input keyevent 223; sleep 10
  L=$(nl); adb -s "$HU" shell input keyevent 224; sleep 2
  if ! waitfor 6 'AapProjectionActivity: onResume' "$L"; then adb -s "$HU" shell wm dismiss-keyguard; raise "$1" "$2" S; fi
  observe "$G"; }
ret_L() { local G L; G=$(nl); mark "$1-r$2-L-go"; adb -s "$HU" shell am start -a android.settings.SETTINGS >/dev/null; sleep 10
  L=$(nl); adb -s "$HU" shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
  waitfor 120 'AapProjectionActivity: onResume' "$L" || echo "NO_RESUME $1-r$2"
  observe "$G"; }
night() { mark "$1-r$2-F-go"; adb -s "$HU" shell cmd uimode night yes; sleep 6; adb -s "$HU" shell cmd uimode night no; sleep 6; }
# session_up : after the app launch and the phone's airplane mode going off
session_up() { local L; L=$(nl)
  waitfor 150 'SSL handshake complete' "$L" || { echo SESSION_FAIL_SSL; return 1; }
  waitfor 40 'AapProjectionActivity: onResume' "$L" || { send ACTION_RAISE_PROJECTION >/dev/null
    waitfor 20 'AapProjectionActivity: onResume' "$L" || { echo SESSION_FAIL_RAISE; return 1; }; }
  waitfor 60 'Throughput over [0-9]+ms: rendered=[1-9]' "$L" || { echo SESSION_FAIL_NO_FRAMES; return 1; }
  sleep 30; }
# win <RUN> : the capture lines between the run's own start and end markers
win() { sed -n "/AutomationMarker: $1-start\$/,/AutomationMarker: $1-end\$/p" "$CAP"; }
# hu_put <base.xml> <spec...> : D-HU only (rooted). Host-side edit, root copy, read back. App must be stopped.
hu_put() { python3 ohu_setkeys.py "$1" new.xml "${@:2}" || return 1
  OWN=$(adb -s "$HU" shell "su -c 'stat -c %u:%g /data/data/$PKG'" | tr -d '\r')
  printf 'cp /data/local/tmp/new.xml /data/data/%s/shared_prefs/settings.xml\nchown %s /data/data/%s/shared_prefs/settings.xml\nchmod 660 /data/data/%s/shared_prefs/settings.xml\n' "$PKG" "$OWN" "$PKG" "$PKG" > hu_put.sh
  adb -s "$HU" push new.xml /data/local/tmp/new.xml >/dev/null; adb -s "$HU" push hu_put.sh /data/local/tmp/hu_put.sh >/dev/null
  adb -s "$HU" shell "su -c 'sh /data/local/tmp/hu_put.sh'"
  adb -s "$HU" shell "su -c 'cat /data/data/$PKG/shared_prefs/settings.xml'" | grep -aoE '(view-mode|video-codec|resolutionId|log-level|wifi-connection-mode|night-mode|force-software-decoding|software-video-decoder|debug-video-fault-[a-z]+|video-profile-starvation-cap)[^/]*'; }
# tab_put <base.xml> <spec...> : D-SAM and D-HP (not rooted, no sed): host-side edit, run-as copy
tab_put() { python3 ohu_setkeys.py "$1" new.xml "${@:2}" || return 1
  adb -s "$HU" push new.xml /data/local/tmp/new.xml >/dev/null
  adb -s "$HU" shell run-as $PKG cp /data/local/tmp/new.xml shared_prefs/settings.xml
  adb -s "$HU" shell run-as $PKG cat shared_prefs/settings.xml | grep -aoE '(view-mode|log-level|wifi-connection-mode|night-mode|onboarding-version|video-profile-starvation-cap)[^/]*'; }   # the starvation key must NOT print; if it does, the write failed
# phone_up / phone_air : phone radios. Verify with dumpsys, never settings get global.
phone_air_on() { [ -n "$OTHER" ] && adb -s "$OTHER" shell cmd connectivity airplane-mode enable
  adb -s "$PH" shell cmd connectivity airplane-mode enable; sleep 8; }
phone_air_off() { adb -s "$PH" shell cmd connectivity airplane-mode disable; sleep 2
  adb -s "$PH" shell svc wifi enable; adb -s "$PH" shell svc bluetooth enable; sleep 4
  adb -s "$PH" shell dumpsys wifi | grep -a -m1 "Wi-Fi is"; adb -s "$PH" shell dumpsys bluetooth_manager | grep -a -m2 -iE "^ *(enabled|state):"; }
# run_open <RUN> <spec...> : clean-run protocol steps 1 to 6 (template section 4), then the start marker
run_open() { RUN=$1; shift; CAP=$OUT/$RUN.logcat
  phone_air_on
  adb -s "$HU" shell am force-stop $PKG; adb -s "$HU" logcat -G 16M; adb -s "$HU" logcat -c
  stdbuf -oL adb -s "$HU" logcat -v time > "$CAP" & CAPPID=$!
  sleep 1; $PUT "$BASEXML" "$@"
  adb -s "$HU" shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity >/dev/null; sleep 20
  phone_air_off
  session_up || { echo "SESSION_FAIL $RUN"; return 1; }
  mark "$RUN-start"; }
# run_close <RUN> : stop capture, extract, leave the unit clean
run_close() { mark "$1-end"; sleep 1; kill $CAPPID; wait $CAPPID 2>/dev/null
  ps aux | grep -c "[l]ogcat"        # must print 0, else kill the leftover before the next run
  tail -n 1 "$CAP" | cut -c1-40; adb -s "$HU" shell date     # last capture line within 5 s of the unit clock, or the capture died: INCONCLUSIVE
  python3 ohu_returns.py "$CAP" "$1" > "$OUT/$1.json"
  send ACTION_EXIT >/dev/null; sleep 3
  adb -s "$HU" shell am force-stop $PKG; }
```

**`ohu_setkeys.py`**:

```python
#!/usr/bin/env python3
"""ohu_setkeys.py <in.xml> <out.xml> int:view-mode=2 bool:key=true str:video-codec=H.264 del:key ...
Edits a SharedPreferences file on the host, so a multi-line <set> key can never be orphaned."""
import sys, xml.etree.ElementTree as ET

src, dst, *specs = sys.argv[1:]
tree = ET.parse(src)
root = tree.getroot()

def drop(k):
    for e in list(root):
        if e.get('name') == k:
            root.remove(e)

for sp in specs:
    if sp.startswith('del:'):
        drop(sp[4:])
        continue
    kind, rest = sp.split(':', 1)
    k, v = rest.split('=', 1)
    drop(k)
    e = ET.SubElement(root, {'int': 'int', 'bool': 'boolean', 'str': 'string'}[kind], name=k)
    if kind == 'str':
        e.text = v
    else:
        e.set('value', v)
tree.write(dst, encoding='utf-8', xml_declaration=True)
```

**`ohu_returns.py`**, which turns one capture into the JSON block each run reports:

```python
#!/usr/bin/env python3
"""ohu_returns.py <capture> <RUN> : per-return metrics, decoder cadence and per-minute counts as JSON.

A window opens at the marker '<RUN>-r<N>-<route>-go' (route H home, S screen off, L launcher,
F already in front, D density recreate) and closes at the next '-go' marker, '<RUN>-end', or 150 s.
Times are device times from `logcat -v time`, in ms. Run on the capture, never on a re-sorted copy.
"""
import json, re, sys

cap, run = sys.argv[1], sys.argv[2]
TS = re.compile(r'^(\d\d)-(\d\d) (\d\d):(\d\d):(\d\d)\.(\d{3})')
rows = []
with open(cap, 'rb') as f:
    for n, raw in enumerate(f, 1):
        line = raw.decode('utf-8', 'replace').rstrip('\n')
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

def marker_t(name):
    for t, n, l in rows:
        if re.search(r'AutomationMarker: ' + re.escape(name) + r'\s*$', l):
            return t
    return None

gos = []
for t, n, l in rows:
    m = re.search(r'AutomationMarker: (' + re.escape(run) + r'-r(\d+)-([HSLFD])-go)\s*$', l)
    if m:
        gos.append((t, int(m.group(2)), m.group(3)))
start, end = marker_t(run + '-start'), marker_t(run + '-end')
LAST = end if end else (rows[-1][0] if rows else 0)
FORBID = ['times in a row without rendering a frame', 'Both codec types failed',
          'Giving up to avoid an infinite restart loop']
TP = re.compile(r'Throughput over \d+ms: rendered=([0-9]+)')
out = {'run': run, 'returns': []}
for i, (t0, k, route) in enumerate(gos):
    t1 = min(gos[i + 1][0] if i + 1 < len(gos) else LAST, t0 + 150000)
    r = {'return': k, 'route': route, 'window_s': round((t1 - t0) / 1000, 1)}
    tps = [t for t, n, l in rows if t0 <= t <= t1 and 'Throughput over ' in l]
    r['throughput_lines'] = len(tps)
    r['throughput_max_gap_s'] = round(max((b - a) for a, b in zip(tps, tps[1:])) / 1000, 1) if len(tps) > 1 else None
    r['decoder_stopped'] = [l.split('Decoder stopped: ')[-1] for t, n, l in rows
                            if t0 <= t <= t1 and 'Decoder stopped: ' in l]
    r['codec_init'] = count('Codec initialized:', t0, t1)
    r['on_new_intent'] = count('AapProjectionActivity: onNewIntent received', t0, t1)
    r['cycle_lines'] = count('relaunched surface has no picture after ', t0, t1)
    r['forbidden'] = {f: count(f, t0, t1) for f in FORBID if count(f, t0, t1)}
    r['fallback_surfaceview'] = count('Falling back to SurfaceView for this session', t0, t1)
    r['second_ssl_or_garbage'] = count('SSL handshake complete', t0, t1) + count('Magic Garbage detected in header', t0, t1)
    res = first(lambda l: 'AapProjectionActivity: onResume' in l, t0, t1)
    if not res:
        r['no_onResume'] = True
        out['returns'].append(r)
        continue
    tr = res[0]
    r['resume_after_go_s'] = round((tr - t0) / 1000, 2)
    r['onStop_seen'] = bool(first(lambda l: 'AapProjectionActivity: onStop' in l, t0, tr))
    r['stop_skipped_lines'] = count('skipped: surface is no longer current', t0, tr + 1000)
    r['raise_route_line'] = count('AapService: raising the projection by', t0, tr + 2000)
    lo, hi = tr - 1000, tr + 3000
    r['surface_path'] = {
        'UI_DEBUG_onSurfaceChanged': count('[UI_DEBUG] [AapProjectionActivity] onSurfaceChanged', lo, hi),
        'UI_DEBUG_onSurfaceCreated': count('[UI_DEBUG] [AapProjectionActivity] onSurfaceCreated', lo, hi),
        'New_surface_set': count('New surface set: ', lo, hi),
        'Gl_onSurfaceChanged': count('GlProjectionView: onSurfaceChanged', lo, hi),
        'Texture_available': count('TextureProjectionView: Surface available', lo, hi),
        'surfaceDestroyed_cb': count('SurfaceCallback: onSurfaceDestroyed', t0, tr + 1000)}
    ci = first(lambda l: 'Codec initialized:' in l, t0, t1)
    r['codec_init_rel_resume_s'] = round((ci[0] - tr) / 1000, 2) if ci else None
    rep = first(lambda l: 'keyframe decoded - the picture is repaired' in l, t0, t1)
    clean = first(lambda l: 'First frame rendered (hardware decode)' in l
                  and 'no keyframe has decoded' not in l, ci[0] if ci else t0, t1)
    pics = [x[0] for x in (rep, clean) if x]
    tp = min(pics) if pics else None
    r['picture_rel_resume_s'] = round(max(0, tp - tr) / 1000, 2) if tp else None
    r['picture_before_resume'] = bool(tp and tp < tr)
    r['picture_by'] = (('repaired' if rep and tp == rep[0] else 'clean_first_frame') if tp else None)
    cy = first(lambda l: 'relaunched surface has no picture after ' in l, tr - 1000, t1)
    r['cycle_rel_resume_s'] = round((cy[0] - tr) / 1000, 2) if cy else None
    r['nudges'] = count('relaunched surface still has no picture - requesting video focus (unsolicited)', tr, t1)
    r['gray_lines'] = count('no keyframe has decoded', t0, t1)
    r['sink_stop_VIDEO'] = count('Media Sink Stop Request: VIDEO', t0, t1)
    r['sink_start_VIDEO'] = count('Media Start Request VIDEO: session=', t0, t1)
    r['forcing_restart'] = count('Forcing restart (', t0, t1)
    r['stall_detected'] = count('Decoder stall detected', t0, t1)
    tpr = first(lambda l: (TP.search(l) and int(TP.search(l).group(1)) > 0) is True, tr, t1)
    r['throughput_after_resume_s'] = round((tpr[0] - tr) / 1000, 2) if tpr else None
    out['returns'].append(r)

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
out['cadence'] = {'running_intervals': len(ints), 'expected_windows': exp, 'observed_windows': obs,
                  'ratio': round(obs / exp, 3) if exp else None}
if start and end:
    out['per_minute_throughput'] = [count('Throughput over ', start + 60000 * m, start + 60000 * (m + 1) - 1)
                                    for m in range(int((end - start) // 60000))]
out['throughput_prefix_samples'] = [l.split('Throughput over ')[0][-70:] for t, n, l in rows
                                    if 'Throughput over ' in l][:2]
out['counts'] = {k: count(k, 0, 10 ** 12) for k in
                 ['Decoder stopped: ', 'skipped: surface is no longer current', 'Codec initialized:',
                  'Throughput over ', 'Codec exception in output thread', 'Decoder stall detected',
                  'VideoDecoder: holding the picture after ', 'VideoDecoder: picture restored ',
                  'VideoDecoder: keyframe decoded - the picture is repaired',
                  'VideoDecoder: dropped a reference frame, requesting keyframe',
                  'FAULT INJECTED (#', 'Fatal signal']}
print(json.dumps(out, indent=1))
```

**`ohu_pair.py`**, which applies the return rule to a baseline and a candidate JSON:

```python
#!/usr/bin/env python3
"""ohu_pair.py <baseline.json> <candidate.json> : the return rule, applied. Prints one JSON block.
T is picture_rel_resume_s; a missing picture counts as 999. Windows whose session re-formed are void."""
import json, sys

b = {r['return']: r for r in json.load(open(sys.argv[1]))['returns']}
c = {r['return']: r for r in json.load(open(sys.argv[2]))['returns']}
T = lambda r: 999.0 if (r is None or r.get('no_onResume') or r.get('picture_rel_resume_s') is None) else r['picture_rel_resume_s']
rows, fa, fb, fc, fd, voided = [], [], [], [], [], []
for k in sorted(set(b) | set(c)):
    rb, rc = b.get(k), c.get(k)
    if rb is None or rc is None:
        rows.append({'return': k, 'note': 'missing in one arm'}); continue
    if rb.get('second_ssl_or_garbage') or rc.get('second_ssl_or_garbage'):
        voided.append(k); continue
    if rb['route'] == 'F':       # an already-in-front window has no picture time; graded on its counts
        rows.append({'return': k, 'route': 'F', 'stops_B': rb['decoder_stopped'], 'stops_C': rc['decoder_stopped'],
                     'codec_init_B': rb['codec_init'], 'codec_init_C': rc['codec_init'],
                     'cycle_B': rb['cycle_lines'], 'cycle_C': rc['cycle_lines'],
                     'max_gap_C': rc['throughput_max_gap_s']})
        if rc['decoder_stopped'] and not rb['decoder_stopped']: fd.append(k)
        continue
    tb, tc = T(rb), T(rc)
    row = {'return': k, 'route': rb['route'], 'T_B': tb, 'T_C': tc, 'delta': round(tc - tb, 2),
           'cycle_B': rb.get('cycle_rel_resume_s'), 'cycle_C': rc.get('cycle_rel_resume_s'),
           'nudges_B': rb.get('nudges'), 'nudges_C': rc.get('nudges'),
           'gray_B': rb.get('gray_lines'), 'gray_C': rc.get('gray_lines'),
           'stops_B': rb['decoder_stopped'], 'stops_C': rc['decoder_stopped'],
           'path_B': rb.get('surface_path'), 'path_C': rc.get('surface_path')}
    rows.append(row)
    if tc > 10.0 and tb < 3.0: fa.append(k)
    if tc - tb > 2.0: fb.append(k)
    if rc['forbidden'] and not rb['forbidden']: fc.append(k)
    if rc.get('no_onResume') or (rc['fallback_surfaceview'] and not rb['fallback_surfaceview']): fd.append(k)
b1 = [r['return'] for r in rows if r.get('route') in ('H', 'S') and r.get('path_C') and
      r['path_C']['UI_DEBUG_onSurfaceChanged'] >= 1 and r['path_C']['New_surface_set'] == 0 and
      any(s in ('activity_stopped', 'screen_off_sleep') for s in r['stops_C'])]
print(json.dumps({'void': voided, 'FAIL_a_over_10s_where_baseline_under_3s': fa,
                  'FAIL_b_delta_over_2s_returns': fb, 'FAIL_b_fires': len(fb) >= 2,
                  'FAIL_c_forbidden_only_candidate': fc, 'FAIL_d_no_resume_or_view_fallback_or_front_stop': fd,
                  'b1_path_reached_returns_candidate': b1, 'rows': rows}, indent=1))
```

Serials: D-HU `27870808938846`, D-POCO `4f4027e9`, D-MOTO `ZY22GC3BM4`, D-SAM `30041c35642d2200`, D-HP `CNU350BGBJ`.

## 6. The lines that decide every run

Every line was checked with `grep -rF` against the candidate tree at `e7c2949c` and against `main` at `7e9d813d`. **All are present in both trees** except the identity symbols. Lines marked *(composed)* are rendered from a format string in source, so the block at the end of this file carries the source form. Grep captures with `grep -a` always.

| Meaning | Line | Source file |
|---|---|---|
| return reached the activity | `AapProjectionActivity: onResume` | `AapProjectionActivity.kt` |
| activity stopped | `AapProjectionActivity: onStop` | same |
| singleTask re-delivery | `AapProjectionActivity: onNewIntent received` | same |
| which raise route was taken | `AapService: raising the projection by` | `AapService.kt` |
| surface callback reached the activity | `[UI_DEBUG] [AapProjectionActivity] onSurfaceChanged` | `AapProjectionActivity.kt` |
| a different Surface object was claimed | `New surface set: ` | `VideoDecoder.kt` |
| GLES re-reports its surface | `GlProjectionView: onSurfaceChanged` | `GlProjectionView.kt` |
| TextureView made a new SurfaceTexture | `TextureProjectionView: Surface available` | `TextureProjectionView.kt` |
| surface torn down | `SurfaceCallback: onSurfaceDestroyed` | `AapProjectionActivity.kt` |
| decoder stopped, with its reason (`activity_stopped`, `screen_off_sleep`, `surfaceDestroyed`, `onDetachedFromWindow`, `projectionViewRecreate`, `New surface`) | `Decoder stopped: ` | `VideoDecoder.kt` |
| old silent-skip line, S2 | `skipped: surface is no longer current` | `VideoDecoder.kt` |
| codec built | `Codec initialized:` | `VideoDecoder.kt` |
| **picture, form 1** | `VideoDecoder: keyframe decoded - the picture is repaired` | `VideoDecoder.kt` |
| **picture, form 2** (line ends here) | `First frame rendered (hardware decode)` | `VideoDecoder.kt` |
| **gray, not a picture** | `First frame rendered (hardware decode) - no keyframe has decoded` | `VideoDecoder.kt` |
| gray noticed by the activity | `no keyframe has decoded` | `AapProjectionActivity.kt` |
| warm-relaunch focus cycle fired | `relaunched surface has no picture after ` (ends `ms - cycling video focus`) | `AapProjectionActivity.kt` |
| warm-relaunch fell back to the inert gain | `relaunched surface still has no picture - requesting video focus (unsolicited)` | same |
| steady state, S1 | `Throughput over ` | `VideoDecoder.kt` |
| restart at a stall | `Forcing restart (` and `Decoder stall detected` | `VideoDecoder.kt` |
| output-thread exception | `Codec exception in output thread` | `VideoDecoder.kt` |
| **forbidden anywhere** | `times in a row without rendering a frame`, `Both codec types failed`, `Giving up to avoid an infinite restart loop` | `VideoDecoder.kt` |
| view backend demoted for the session (contaminates a GLES or TEXTURE run) | `Falling back to SurfaceView for this session` | `AapProjectionActivity.kt` |
| codec choice, V3 | `findBestCodec: ` (carries `selected=`) | `VideoDecoder.kt` |
| H.265 forced by 1440p, V3 | `Enforcing H.265` | `ServiceDiscoveryResponse.kt` |
| phone chose a codec *(composed)* | `Media Sink Setup Request: 3 on channel VIDEO` (H.264) / `7 on channel VIDEO` (H.265) | `AapControl.kt` |
| phone stopped or restarted the sink *(composed)* | `Media Sink Stop Request: VIDEO`, `Media Start Request VIDEO: session=` | `AapControl.kt` |
| fault injection | `FAULT INJECTED (#`, `fault injection budget spent after` | `VideoFaultReporter.kt` |
| concealment | `VideoDecoder: holding the picture after `, `VideoDecoder: picture restored ` | `VideoDecoder.kt` |
| reference frame shed | `VideoDecoder: dropped a reference frame, requesting keyframe` | `VideoDecoder.kt` |
| session formed | `SSL handshake complete` | `AapSslContext.kt` |
| discard rules | `MATCH! Starting AapService`, `createGroup SUCCESS`, `Magic Garbage detected in header` | receivers, `WifiDirectManager.kt`, `AapReadSingleMessage.kt` |
| link cap, for Setup notes | `linkCapped=` | `HeadUnitScreenConfig.kt` |
| marker | `AutomationMarker: ` | `AutomationEffectRunner.kt` |
| **identity, candidate only** | `detachSurfaceIfCurrent`, `outputPublicationLock` | in the DEX |

**The output-thread lines carry a different `Class.method` prefix on the candidate** (the queued lambda's frame, S1). Grade every line on its message text and never on its prefix. Never write a grep that starts with a class name for those lines. `ohu_returns.py` prints two sample prefixes per build for the report.

## 7. Runs

Run ids carry the build: `<ID>-B` baseline, `<ID>-C` candidate. **Do all baseline runs first, then install the candidate and repeat**, so each unit changes build once. Per unit order on D-HU: **V1G, V2T, V2S, V1H, V5R, V3S, V3G, V6B, V5D, V6A**, then the tablets (V7S, V7P), then V1M if time allows. The point of the round is **V1G**.

Every run is `run_open ... ; steps ; run_close` and writes `$OUT/<RUN>.logcat` and `$OUT/<RUN>.json`. Starting state for D-HU and D-POCO, verified and recorded, not assumed:

```bash
HU=27870808938846; PH=4f4027e9; OTHER=ZY22GC3BM4; PUT=hu_put; OUT=~/hur-wifi-test-scripts/pr-1046-round1; mkdir -p $OUT/apk; cd $OUT; source ./ohu_lib.sh      # adjust OUT to where hur-wifi-test-scripts/ lives on the rig
adb devices                                                              # all five listed
adb -s $HU shell date; date                                             # clock agreement, to the second
adb -s $HU shell "su -c 'stat -c %U:%a /data/data/$PKG/shared_prefs'"   # record. A root-owned directory loses the app's own writes
adb -s $HU shell "su -c 'cat /data/data/$PKG/shared_prefs/settings.xml'" > $OUT/settings-backup-HU.xml; BASEXML=$OUT/settings-backup-HU.xml
adb -s $PH shell dumpsys window | grep -a mCurrentFocus                  # D-POCO idle or at home; if Settings, KEYCODE_HOME
adb -s $OTHER shell dumpsys wifi | grep -a -m1 "Wi-Fi is"                # D-MOTO radios off for D-POCO runs
adb -s $HU shell appops get $PKG SYSTEM_ALERT_WINDOW                     # record: the raise route depends on it
adb -s $HU shell settings get system user_rotation                       # record: D-HU is not rotated
adb -s $HU shell wm density                                              # record: Physical and any Override
```

Every capture is `logcat -v time`, started before the launch with `stdbuf -oL`, after `adb logcat -G 16M`. **Kill the capture at the end of every run and confirm `ps aux | grep logcat` is empty**; `run_close` does both. Discard a run and re-run it once if its window (`win <RUN>`) holds any of `MATCH! Starting AapService`, a second `createGroup SUCCESS`, `Magic Garbage detected in header`, or a second `SSL handshake complete`. A second discard makes it INCONCLUSIVE.

### The return rule (RET), used by V1G, V1H, V2T, V2S, V5R part 2, V5D, V7S, V7P and V1M

Run `python3 ohu_pair.py <ID>-B.json <ID>-C.json`. T is `picture_rel_resume_s`: seconds from `AapProjectionActivity: onResume` to the first picture line (form 1 or form 2 of section 6); a return with no picture within its 90 s observe window counts as 999.

**FAIL** if any of:

- **(a)** a return where T on the candidate is above **10.0 s** and T on the baseline is below **3.0 s**;
- **(b)** two or more returns where T on the candidate exceeds the baseline's by more than **2.0 s**;
- **(c)** a forbidden line in a candidate window whose baseline window has none;
- **(d)** a candidate window with no `onResume`, or one that logged `Falling back to SurfaceView for this session` where the baseline did not.

A window whose session re-formed (`second_ssl_or_garbage` above 0) is void for both arms; more than three void windows re-run the run once, and a second time it is INCONCLUSIVE. **Never graded, always reported per return:** `surface_path`, `cycle_rel_resume_s`, `nudges`, `gray_lines`, `sink_start_VIDEO`, `decoder_stopped`.

**If the candidate did nothing different,** both builds give the same T and the same `surface_path`, and the rule passes. So a PASS on a GLES or TEXTURE run only counts as evidence about B1 when the **B1 path was reached**: `ohu_pair.py` lists `b1_path_reached_returns_candidate`, the H or S returns where the candidate saw `[UI_DEBUG] [AapProjectionActivity] onSurfaceChanged` after `onResume`, no `New surface set:`, and a `Decoder stopped: activity_stopped` or `screen_off_sleep` before it. Zero such returns on a candidate GLES run makes that run **INCONCLUSIVE on B1**, and the report says which surface lines it did see.

### V1G. GLES, Home and screen-off returns, both builds (**the point of the round**)

```bash
v12() {  # v12 <RUN> <view-mode> <codec> <night 0|1>
  run_open $1 $COMMON int:view-mode=$2 str:video-codec=$3 || return 1
  [ "$2" = 2 ] && SHOTS=1
  for n in 1 2 3; do ret_H $1 $n; done; SHOTS=
  for n in 4 5 6; do ret_S $1 $n; done
  for n in 7 8 9; do ret_L $1 $n; done
  [ "$4" = 1 ] && { night $1 10; night $1 11; night $1 12; }
  run_close $1; }
v12 V1G-B 2 H.264 1        # on the baseline build;  V1G-C 2 H.264 1 on the candidate
```

Returns 1 to 3 are Home, a 10 s hold, then the raise verb. Returns 4 to 6 are screen off, 10 s, wake. Returns 7 to 9 are a Settings cover, 10 s, then the launcher `monkey` return. Returns 10 to 12 are `cmd uimode night` toggles while the projection stays in front. On returns 1 to 3 the helper takes sequential screenshots at about 1 s, 4 s and 12 s after the raise; they are evidence for a human, never a verdict (a gray picture still renders, so the counter hides it).

**Grade:** the return rule over returns 1 to 9. Returns 10 to 12 (route F) must show an empty `decoder_stopped`, `codec_init` 0, `cycle_lines` 0 and a `throughput_max_gap_s` of at most **7.5**, on both builds; a candidate stop where the baseline has none is FAIL (d). Expected on the baseline: a cycle line about 0.85 to 1.0 s after `onResume` on every H and S return and T about 1.5 to 3 s. Predicted on the candidate if B1 is real: no cycle line on returns 2, 3, 5 and 6, one or more `nudges`, `gray_lines` above 0, and T of 5 s or more. **That prediction may be wrong; report what happened.**

### V2T and V2S. TEXTURE and SURFACE controls, both builds

`v12 V2T-B 1 H.264 0` and `v12 V2S-B 0 H.264 1` on the baseline; `V2T-C`, `V2S-C` on the candidate. Grade with the return rule. **V2S adds (e):** a candidate window with a `cycle_lines` above 0 where the baseline has none is FAIL, because SURFACE's own teardown already releases focus and the escalation must stay silent there. Report for V2T whether the candidate returns reach the B1 path, since the review did not expect it to.

### V1H. GLES on H.265 hardware, both builds

`v12 V1H-B 2 H.265 0` then `V1H-C`. Same grade as V1G. This covers the hardware HEVC codec, which has its own parameter-set handling after a restart.

### V3S and V3G. The codec pin over 20 background and foreground cycles, both builds

The recipe from the run that found the pin loss: 20 cycles at 1080p H.264, counting `findBestCodec` calls that select an HEVC decoder with no `Enforcing H.265`. V3S covers the surface-destroyed path (a Settings cover tears SURFACE down); V3G covers the `activity_stopped` path on GLES.

```bash
v3() {  # v3 <RUN> <view-mode> <settings|home>
  run_open $1 $COMMON int:view-mode=$2 || return 1
  for n in $(seq 1 20); do G=$(nl); mark "$1-r$n-H-go"
    if [ "$3" = settings ]; then adb -s $HU shell am start -a android.settings.SETTINGS >/dev/null; else adb -s $HU shell input keyevent 3; fi
    sleep 4; raise $1 $n H; sleep 8; done
  run_close $1; }
v3 V3S-B 0 settings ; v3 V3G-B 2 home       # baseline;  V3S-C and V3G-C on the candidate
```

```bash
win V3S-C | grep -a 'findBestCodec: ' | grep -aciE 'selected=.*(hevc|h265|h\.265)'   # must be 0
win V3S-C | grep -ac  'findBestCodec: '                                              # at least 20
win V3S-C | grep -acF 'Enforcing H.265'                                              # 0
win V3S-C | grep -acF 'Media Sink Setup Request: 3 on channel VIDEO'                 # at least 1
win V3S-C | grep -acF 'Media Sink Setup Request: 7 on channel VIDEO'                 # 0
win V3S-C | grep -acF 'Decoder stopped: surfaceDestroyed'                            # V3S: at least 20.  V3G: grep 'Decoder stopped: activity_stopped', at least 20
```

**PASS (each of V3S and V3G):** the candidate's HEVC-selected count is **0**, `Enforcing H.265` is 0, no `7 on channel VIDEO`, at least one `3 on channel VIDEO`, at least 20 `findBestCodec: ` lines, and at least 20 stops of the run's expected reason. **FAIL:** a candidate HEVC-selected count above 0 on an H.264 stream. **INCONCLUSIVE:** the phone asked for H.265 (`7`), or fewer than 20 stops of the expected reason, which says the cover did not do its job on this unit. Report the baseline's count beside it (the pin fix is on `main`, so it should also read 0). Every stop reason here is a surface-lifecycle reason, so a count above 0 means the pin was cleared.

### V5R. singleTask relaunch, GLES, both builds

```bash
v5r() {  # v5r <RUN>
  run_open $1 $COMMON int:view-mode=2 || return 1
  for n in 1 2 3; do mark "$1-r$n-F-go"; send ACTION_RAISE_PROJECTION >/dev/null; sleep 1; send ACTION_RAISE_PROJECTION >/dev/null; sleep 8; done
  for n in 4 5 6; do G=$(nl); mark "$1-r$n-H-go"; adb -s $HU shell input keyevent 3; sleep 4
    L=$(nl); send ACTION_RAISE_PROJECTION >/dev/null; sleep 1; send ACTION_RAISE_PROJECTION >/dev/null
    waitfor 8 'AapProjectionActivity: onResume' $L || { mark "$1-r$n-H-fallback-monkey"; adb -s $HU shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; waitfor 90 'AapProjectionActivity: onResume' $L; }
    observe $G; done
  run_close $1; }
```

**Grade:** part 1 (returns 1 to 3, route F): no `decoder_stopped`, `codec_init` 0, `cycle_lines` 0, `throughput_max_gap_s` at most 7.5, on both builds; a candidate stop where the baseline has none is FAIL. Part 2 (returns 4 to 6): the return rule (a) to (d), and `codec_init` at most 2 per window. This is the case the candidate claims to improve, so a candidate that is worse on part 2 is a real finding.

### V5D. An activity recreate with a live session, GLES, both builds

`wm density` is not in the projection activity's `configChanges`, so changing it destroys and rebuilds the activity: the candidate's `onDestroy` retirement step runs here and nowhere else in this brief.

```bash
v5d() {  # v5d <RUN>
  run_open $1 $COMMON int:view-mode=2 || return 1
  PHYS=$(adb -s $HU shell wm density | grep -i physical | grep -o '[0-9]*$' | head -1)
  OVR=$(adb -s $HU shell wm density | grep -i override | grep -o '[0-9]*$' | head -1); BACK=${OVR:-reset}
  NEW=$((PHYS*5/4)); G=$(nl); mark "$1-r1-D-go"; adb -s $HU shell wm density $NEW; sleep 12; observe $G
  G=$(nl); mark "$1-r2-D-go"; adb -s $HU shell wm density $BACK; sleep 12; observe $G
  run_close $1; adb -s $HU shell wm density $BACK; }
```

**Grade:** the return rule over the two windows, plus `second_ssl_or_garbage` 0 in both. **INCONCLUSIVE** if neither window has an `onResume` (the activity was not recreated); if the session dies in the first window, restore the density at once and call it INCONCLUSIVE. **`wm density` is restored in `run_close` and again at the end of the function; read it back with `adb shell wm density` and put what it says in Setup notes.**

### V6A. Software MediaCodec overload and the restart paths, both builds

`force-software-decoding=true` with `software-video-decoder=0` is the only lever that has ever made the output thread stall or shed frames on this rig. It exercises the output-thread fencing hardest: stall restarts, the codec fallback, and S1's cadence.

```bash
v6a() {  # v6a <RUN>
  run_open $1 $COMMON int:view-mode=0 bool:force-software-decoding=true int:software-video-decoder=0 || return 1
  sleep 240; mark "$1-steady-end"            # steady minutes 0 to 3 are per_minute_throughput[0..3]
  for n in 1 2 3; do ret_H $1 $n; done
  run_close $1; }
```

**Stop rule:** `adb devices` losing D-HU at any point stops the round. **Grade, candidate against baseline:**

- `per_minute_throughput[0..3]` (nominal 12): each candidate minute is at least **8** and at least the baseline's same minute minus 2. A candidate minute below that is FAIL, unless a `Decoder stall detected` or `Decoder stopped: restart` line sits inside that minute on the candidate (quote it).
- `cadence.ratio` of the candidate at least the baseline's **minus 0.05**.
- The three forbidden lines: any in the candidate and none in the baseline is FAIL. `Codec exception in output thread` on the candidate more than 2 above the baseline is FAIL.
- The return rule over the three H returns.
- **INCONCLUSIVE for the strike-threshold half** when the baseline has no `Decoder stall detected` and no `Forcing restart (` (the overload did not reach it): say so, and let the cadence and return conditions stand on their own. The strike path itself is also exercised by the candidate's own JVM test at the last commit.
- Report `Decoder stall detected`, `Forcing restart (`, `dropped a reference frame` and `Falling back to` (the codec fallback line contains `times in a row without rendering a frame`) per build.

### V6B. Fault injection, the concealment and repair lines, both builds

Dropping middle fragments drives the output-thread concealment and repair lines (the ones S1 says may be lost). **Expected injections: 40**, the budget, and the injector stops itself, so the damage is bounded and a clean tail follows.

```bash
v6b() {  # v6b <RUN>
  run_open $1 $COMMON int:view-mode=2 int:debug-video-fault-injection=2 int:debug-video-fault-rate=3 int:debug-video-fault-budget=40 || return 1
  waitfor 240 'fault injection budget spent after' $(nl) ; sleep 20; mark "$1-tail"; sleep 90
  run_close $1; }
```

**Grade:**

- `grep -ac 'FAULT INJECTED (#'` on `win` is at least **20** on both builds, else INCONCLUSIVE.
- For each of `VideoDecoder: keyframe decoded - the picture is repaired`, `VideoDecoder: picture restored `, `VideoDecoder: holding the picture after ` and `VideoDecoder: dropped a reference frame, requesting keyframe`: where the baseline count is at least 4, the candidate count is at least **half** of it. Below that is FAIL (output-thread lines lost).
- Tail: `sed -n '/AutomationMarker: V6B-.-tail$/,/AutomationMarker: V6B-.-end$/p' | grep -ac 'Throughput over '` is at least **15** (nominal 18 in 90 s) on the candidate, with `Decoder stall detected` 0 in the tail.
- Report both builds' counts side by side. **This run is a regression guard: with the injector deterministic, a candidate that loses lines is the finding.**

### V7S. D-SAM (API 19), one session, both builds

D-SAM runs Native AA with D-POCO as the phone. It has no `sed`, `su` or `awk`, so settings go through `tab_put`; `am` rejects `-p` (the helpers use `-n`); it cannot take a screen-off verb below API 20, so only Home returns run. **Record `dumpsys battery`** (the port does not charge it; below 15% say so in Setup notes) and `adb -s 30041c35642d2200 shell date` against the host. Its Marvell decoder has crashed in teardown before, so count `Fatal signal` on both builds.

```bash
HU=30041c35642d2200; PH=4f4027e9; OTHER=ZY22GC3BM4; PUT=tab_put
adb -s $HU shell run-as $PKG cat shared_prefs/settings.xml > $OUT/settings-backup-SAM.xml; BASEXML=$OUT/settings-backup-SAM.xml
V7="int:wifi-connection-mode=3 int:log-level=2 int:native-driver-selection-mode=0 int:onboarding-version=2 del:video-profile-starvation-cap"
v7() { run_open $1 $V7 || return 1; for n in 1 2 3; do ret_H $1 $n; done; run_close $1; }
# v7 V7S-B in the baseline phase, v7 V7S-C after the candidate is installed on D-SAM
```

D-SAM prints two `createGroup SUCCESS` and two `Handling handshake for` lines before SSL on a clean session; that is how it settles, not contamination. Its Bluetooth must be on. Read it before every D-SAM run: `adb -s 30041c35642d2200 shell dumpsys bluetooth_manager | grep -a -m3 -iE 'enabled|state'`. If it reads off, the hand step is to turn it on from Android's own Settings (no adb lever exists on API 19), and wait for the read to say on. If it is still off after one hand step, V7S is UNTESTABLE for both builds; if it cannot be read at all, INCONCLUSIVE. **Grade:** the return rule over three returns, plus `throughput_after_resume_s` at most **15** on the candidate, plus `Fatal signal` count on the candidate no higher than the baseline's. Report the view mode and codec it ran, read from the backup.

### V7P. D-HP (API 17), one session, both builds

D-HP has no WiFi Direct, so it runs Headunit Server mode (`wifi-connection-mode=1`) against D-POCO. **The precondition is a hand step:** D-POCO's Android Auto developer "Start head unit server" toggle, because it is UI-only and was found down after an earlier round. Verify before anything else, and **if nothing listens, V7P is UNTESTABLE for both builds** (record the output):

```bash
adb -s 4f4027e9 shell cat /proc/net/tcp | grep -i ':149D'                  # 0x149D is 5277
PHIP=$(adb -s 4f4027e9 shell ip -4 addr show wlan0 | grep -o 'inet [0-9.]*' | cut -d' ' -f2)
```

D-HP and D-POCO must be on the same LAN; D-HP's own WiFi cannot be toggled by adb, so do not try. D-HP cannot run `SettingsActivity` by script, and a host-side logcat has died silently on it, so put the capture tail check from `run_close` in Setup notes for this run.

```bash
HU=CNU350BGBJ; PH=4f4027e9; OTHER=; PUT=tab_put
adb -s $HU shell run-as $PKG cat shared_prefs/settings.xml > $OUT/settings-backup-HP.xml; BASEXML=$OUT/settings-backup-HP.xml
v7p() { RUN=$1; CAP=$OUT/$RUN.logcat
  adb -s $HU shell am force-stop $PKG; adb -s $HU logcat -G 16M; adb -s $HU logcat -c
  stdbuf -oL adb -s $HU logcat -v time > $CAP & CAPPID=$!; sleep 1
  tab_put $BASEXML int:wifi-connection-mode=1 int:log-level=2 int:onboarding-version=2 del:video-profile-starvation-cap
  adb -s $HU shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity >/dev/null; sleep 15
  send ACTION_CONNECT --es ip $PHIP
  session_up || { echo "SESSION_FAIL $RUN"; return 1; }
  mark "$RUN-start"; for n in 1 2 3; do ret_H $RUN $n; done; run_close $RUN; }
# v7p V7P-B in the baseline phase, v7p V7P-C after the candidate is installed on D-HP
```

**Grade:** as V7S (no `Fatal signal` rule needed unless one appears, in which case compare builds). On a unit with no WiFi radio lever the only baseline is the baseline build, so report both builds' T for every return.

### V1M. A second phone, GLES, both builds (run last, optional)

Repeat V1G's returns 1 to 3 with D-MOTO as the phone, on D-HU. Preconditions, verified by `dumpsys` and not by `settings get global`: D-POCO in airplane mode (`OTHER=4f4027e9`), D-MOTO unlocked, read with `adb -s ZY22GC3BM4 shell dumpsys window | grep -E 'isKeyguardShowing|mShowingLockscreen|mDreamingLockscreen'`. **D-MOTO can be behind a PIN and adb has no way past one: if any of the three reads true, V1M is UNTESTABLE for both builds and that is the end of it; if the command prints nothing, INCONCLUSIVE.** D-HU holds one hands-free link at a time, so confirm D-POCO is off before D-MOTO is armed. If D-MOTO does not connect in 150 s and `GH.WIRELESS.BT: Creating rfcomm socket for device:` in D-MOTO's `logcat -d` names a MAC that is not D-HU's, say so and stop; the only fix is a Gearhead data clear and it needs the operator's approval.

```bash
HU=27870808938846; PUT=hu_put; BASEXML=$OUT/settings-backup-HU.xml; PH=ZY22GC3BM4; OTHER=4f4027e9
v1m() { run_open $1 $COMMON int:view-mode=2 || return 1; for n in 1 2 3; do ret_H $1 $n; done; run_close $1; }
# v1m V1M-B in the baseline phase, v1m V1M-C in the candidate phase
```

**Grade:** the return rule over three returns.

### S1 and S2, graded across the whole round

From each JSON, over V1G, V1H, V2T, V2S, V5R, V6A and V6B:

- **S1 cadence.** `cadence.ratio` is `Throughput over` lines seen divided by 5 s windows the codec was running. Sum `observed_windows` and `expected_windows` per build. **FAIL** if the candidate's ratio is below the baseline's by more than **0.05**, or below **0.80**. A missing line at a stop boundary is not itself a failure because the baseline loses the same partial window; a lower ratio is.
- **S1 prefix.** Quote `throughput_prefix_samples` for each build. No verdict.
- **S2 silent skips.** Per build: `counts['skipped: surface is no longer current']`, and for each H return the `decoder_stopped` list. **Report only.** If a candidate H return has an empty `decoder_stopped` where the baseline has `activity_stopped`, say so: that is the silent skip, and it matters only through T.

## 8. Do not re-run

- Whether Home or a cover tears down a GLES, TEXTURE or SURFACE surface on D-HU, and the leg decomposition of a return (video-black rounds 1, 5, 6). This round reuses their facts and measures only the delta between two builds.
- The 850 ms escalation window itself and the 0.5 to 0.8 s focus-cycle keyframe (video-black round 8, dropped-frame rounds 4 and 5).
- Anything about handshakes, pokes, group identity or audio. Nothing on the branch touches them.

## 9. Report back

The numbers that decide whether we ask for changes before merge:

1. **V1G:** T per return for both builds in a 12-row table, the `cycle_rel_resume_s` and `nudges` beside it, and the count of B1-path-reached returns. State plainly whether B1 reproduced (rule (a) or (b) FAIL on GLES) or the path was never reached.
2. **V2T:** the same for TEXTURE, with whether it reached the B1 path.
3. **V3S and V3G:** the HEVC-selected count on each build out of the number of `findBestCodec: ` calls.
4. **S1 cadence:** the two ratios and the prefix samples.
5. **V6A and V6B:** forbidden-line and `Fatal signal` counts per build, the V6B line counts side by side, and whether the strike path was reached.
6. **V5R and V5D:** whether the improved singleTask case is improved, as numbers.

Results go in `pr-1046-video-retirement-round1-results.md` in the template's skeleton (section 7): one `## R<id>` section per run id with a bolded verdict alone on its line, the `ohu_pair.py` output summarised as a table under it, and a closing `## Anything the brief did not ask about`. Setup notes lists the delta of `settings.xml`, both md5s, the identity checks, every script used or added, the values read back for the audio keys, the `view-mode`, codec and resolution each tablet ran, and what the starting-state block printed.

The results file follows this skeleton (template section 7), with one `## R<id>` section per run id: R0, V1G-B, V1G-C, V2T-B, V2T-C, V2S-B, V2S-C, V1H-B, V1H-C, V3S-B, V3S-C, V3G-B, V3G-C, V5R-B, V5R-C, V5D-B, V5D-C, V6A-B, V6A-C, V6B-B, V6B-C, V7S-B, V7S-C, V7P-B, V7P-C, V1M-B, V1M-C.

```markdown
# pr-1046-video-retirement, round 1 results

**Candidate:** pr/split-video-retirement @ e7c2949c1bd43df22c5d1b94dbacf7e4d3ebf385       **Baseline:** main @ 7e9d813d66493f1cc978c0f6a259bdb613874bd1
**APK md5:** <candidate> / <baseline>
**Unit:** D-HU (UNISOC MT50, Android 14) with D-POCO; D-SAM (API 19); D-HP (API 17); D-MOTO (V1M)
**Date:** <yyyy-mm-dd>

## Setup notes

Settings.xml delta against the fresh backup, both md5s, identity checks, scripts used or added (ohu_lib.sh, ohu_setkeys.py, ohu_returns.py, ohu_pair.py), audio keys as found, view-mode/codec/resolution each tablet ran, the starting-state block output, every deviation and every string that did not match.

## R<id> (one per run id above)

**PASS** | **FAIL** | **INCONCLUSIVE** | **UNTESTABLE**

- Settings written:
- Radio state and how it was set:
- Discard-rule check: clean / re-run N times
- Decisive lines, quoted with device timestamps:
- Measurements: the ohu_pair.py table (T_B, T_C, delta, cycle, nudges, gray, stops, surface path), cadence ratio, counts asked for in the run

## Anything the brief did not ask about
```

**Final step after the last run, on every unit used:** restore the backup `settings.xml` with the app stopped, then delete `video-profile-starvation-cap` and read it back; the read must print nothing.

```bash
$PUT "$BASEXML" del:video-profile-starvation-cap     # D-HU: hu_put, tablets: tab_put; both read back
adb -s $HU shell run-as $PKG cat shared_prefs/settings.xml | grep -ac video-profile-starvation-cap    # tablets: must print 0
adb -s $HU shell "su -c 'cat /data/data/$PKG/shared_prefs/settings.xml'" | grep -ac video-profile-starvation-cap   # D-HU: must print 0
```

Captures go to the fork's release **`rig-evidence-pr-1046-video-retirement`**, one asset for this round, `pr-1046-video-retirement-round1-captures.zip`, created on this first round (template section 7); cite the asset name and its sha256 in the results file.

```bash
zip -1 -r pr-1046-video-retirement-round1-captures.zip $OUT
sha256sum pr-1046-video-retirement-round1-captures.zip
gh release create rig-evidence-pr-1046-video-retirement --repo o-jcardenass/open-headunit --notes "pr-1046-video-retirement rig captures, one asset per round" pr-1046-video-retirement-round1-captures.zip
```

Every string any run greps, in source form, one per line. The three *(composed)* lines render from `%s`/`%d` and `$reason` formats; the runs grep the rendered form given in section 6. The last three lines are not capture greps: `linkCapped=` is read in Setup notes, and `detachSurfaceIfCurrent` and `outputPublicationLock` are the identity symbols, grepped in the DEX.

```decisive-strings
AapProjectionActivity: onResume
AapProjectionActivity: onStop
AapProjectionActivity: onNewIntent received
AapService: raising the projection by
[UI_DEBUG] [AapProjectionActivity] onSurfaceChanged
[UI_DEBUG] [AapProjectionActivity] onSurfaceCreated
New surface set: 
GlProjectionView: onSurfaceChanged
TextureProjectionView: Surface available
SurfaceCallback: onSurfaceDestroyed
Decoder stopped: 
skipped: surface is no longer current
Codec initialized:
Throughput over 
First frame rendered (hardware decode)
no keyframe has decoded
VideoDecoder: keyframe decoded - the picture is repaired
relaunched surface has no picture after 
relaunched surface still has no picture - requesting video focus (unsolicited)
Forcing restart (
Decoder stall detected
Codec exception in output thread
times in a row without rendering a frame
Both codec types failed
Giving up to avoid an infinite restart loop
rendering a frame. Falling back to 
Falling back to SurfaceView for this session
findBestCodec: 
Enforcing H.265
Media Start Request %s: session=
Media Sink Setup Request: %d on channel %s
Media Sink Stop Request: 
FAULT INJECTED (#
fault injection budget spent after
VideoDecoder: holding the picture after 
VideoDecoder: picture restored 
VideoDecoder: dropped a reference frame, requesting keyframe
Failed to start decoder
SSL handshake complete
Magic Garbage detected in header
MATCH! Starting AapService
createGroup SUCCESS
AutomationMarker: 
AutomationReceiver: 
linkCapped=
detachSurfaceIfCurrent
outputPublicationLock
```
