# pr-1141-cover-keeps-decoder, round 1 brief: keep the decoder through a short cover

Publish name on `transfer/rig-rounds`: `pr-1141-cover-keeps-decoder-round1-brief.md`. Results go in
`pr-1141-cover-keeps-decoder-round1-results.md`. Evidence goes in a new release
`rig-evidence-pr-1141-cover-keeps-decoder` as the asset `pr-1141-cover-keeps-decoder-round1-captures.zip`.

Two builds: the candidate C and the baseline B. Units: D-HU (head unit), D-POCO (phone). D-MOTO is
held in airplane mode if it is plugged in. About 75 minutes, builds included. No hand step is
expected (section 3).

| Run | Arm | View mode | What it proves | Session |
|---|---|---|---|---|
| P | both | | build gate, APK identity, rig state | no |
| **R1** | C | TEXTURE | **the point of the round: a 6 s cover keeps the decoder, and the picture is there on return** | yes |
| R2 | C | TEXTURE | in R1's session: a 30 s cover releases the decoder at 20 s; what happens after the release | (R1's) |
| R3 | C | GLES | the same 6 s cover on a SurfaceView backend | yes |
| R4 | C | SURFACE | the same 6 s cover on a SurfaceView backend | yes |
| R5 | B | TEXTURE | the baseline: the same 6 s cover on the code before the change | yes |
| X | | | restore | no |

## 1. Build and baseline

| Arm | Ref | SHA | Tree |
|---|---|---|---|
| **C** (candidate) | upstream `pull/1141/head` | `24dd22194ce946c1340d3d85af093c1146aea8f1` | `32ff75261ef53afc6f6ad5a78a5b53f0dbed11cb` |
| **B** (baseline) | upstream `main`, the candidate's parent | `afe6a534811970893b7a8f745617cd84bd04a021` | `30145c1828fa27ae30f6c81e24c6ecf0d12e2031` |

This is the first round of the thread. C is B plus one commit, `24dd2219` (Projection: keep the
decoder through a short cover), from an outside contributor. It is not on the fork. Fetch it from
the upstream repository by URL, so the rig's remote names do not matter:

**fetch.sh**
```bash
U=https://github.com/andreknieriem/open-headunit.git
git fetch $U pull/1141/head main
git cat-file -t 24dd22194ce946c1340d3d85af093c1146aea8f1      # must print commit
git cat-file -t afe6a534811970893b7a8f745617cd84bd04a021      # must print commit
git rev-parse 24dd22194ce946c1340d3d85af093c1146aea8f1^{tree}   # must print 32ff75261ef53afc6f6ad5a78a5b53f0dbed11cb
git rev-parse afe6a534811970893b7a8f745617cd84bd04a021^{tree}   # must print 30145c1828fa27ae30f6c81e24c6ecf0d12e2031
```

If a line differs, stop the round.

- Build both arms in one worktree: `git checkout --detach <sha>`, check the tree, then
  `$RIG_TOOLKIT/rig_thermal.sh wait 70` and `HUR_DIR=<wt> MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh`.
  Build B first, copy its APK at once to `$OUT/apk/B.apk`, then C to `$OUT/apk/C.apk`. Record both
  md5s. They must differ.
- The rig runs no unit tests. The candidate's 3313 JVM tests passed on the dev host at this SHA, 0
  failures. **A build failure stops the round.**
- DEX identity symbol: `unzip -p $OUT/apk/C.apk 'classes*.dex' | strings | grep -acF DecoderCoverPolicy`
  must print 1 or more, and the same on `B.apk` must print 0.
- Install in P, step 7. Before R5, install B (R5's block does it).

## 2. What this is and why

A reverse camera or a 360 camera app covers the projection. When it goes away, the projection
comes back black for 0.5 s to 1 s, worse while the map moves. The contributor's diagnosis:
`AapProjectionActivity.onStop()` stops the video decoder (`Decoder stopped: activity_stopped`). On
the way back the decoder is rebuilt and it shows nothing until a keyframe arrives. The contributor
measured this on an emulator only. Nobody has measured it on a real head unit.

The candidate posts the stop 20 s later (`DecoderCoverPolicy.KEEP_DECODER_MS`) and cancels it in
`onStart()`. So a cover shorter than 20 s should leave the decoder running. A longer cover prints
`AapProjectionActivity: the projection stayed covered, releasing the decoder` and stops it as before.

Two questions from the code review that only hardware can answer:

- **On a SurfaceView backend (SURFACE, GLES) the change may do nothing.** A stopped activity's
  SurfaceView loses its surface. `onSurfaceDestroyed` then stops the decoder
  (`Decoder stopped: surfaceDestroyed`) and releases video focus, whatever `onStop` does. TEXTURE
  (the default, `view-mode` 1) keeps its `SurfaceTexture` through a stop. R3 and R4 measure which
  is true on D-HU.
- **On TEXTURE the 20 s release may last only until the next frame.** `VideoDecoder.decode()`
  builds a new codec whenever none exists and the surface is valid, and nothing stops the phone
  sending video while the activity is stopped. So after the release the next frame can rebuild the
  codec while the projection is still covered. R2 counts `Configuring decoder: ` after the release.
  This is the same on B, so it is a finding, not a FAIL.

## 3. What is different about this round

- **The cover is a real activity, not a Home press.** A Home press never tore down the projection
  surface on D-HU (`rig-quirks/topics/projection.md`). Each cover starts the stock Settings app over
  the projection with `am start -a android.settings.SETTINGS`, and BACK ends it. That is what a
  camera app does when it closes. Every cycle checks that `AapProjectionActivity: onStop` printed;
  a cycle without it is void, not a FAIL.
- **If BACK does not bring the projection back within 5 s, the script sends `ACTION_RAISE_PROJECTION`.**
  The cycle records which way it came back (`ret=back` or `ret=raise`). Both are valid.
- **The picture is graded from screenshots by mean brightness.** `luma.py` (section 5) reads a raw
  `screencap` and prints the mean luma of the centre of the screen. Each cycle takes one shot before
  the cover and two after the return. `night-mode` is 1 (DAY), so the Android Auto map is bright and
  a black frame stands out. The shots are sequential with `sleep 0.3`, never in parallel (house rule 8).
- **The map does not move.** Android Auto sends few frames on a still map. That makes a missing
  keyframe wait longer, which is the case the change is for. Record the `Throughput over` lines; do
  not try to move the map.
- **Do not write the rig's audio settings.** Every write starts from D-HU's own backup.
- **`Critical error` lines on the phone follow every session end we cause.** Grade them only inside
  the cycle windows, which end before `endrun`.
- **Expected INCONCLUSIVE:** none. R3 and R4 may show the decoder stopped by `surfaceDestroyed`.
  That is a measurement of the backend, and section 7 says how to grade it.

### Hand steps

None is expected. P batches the only possible ask:

| Id | When | Action | Why there is no verb |
|---|---|---|---|
| H1 | P finds `Navegadortz3` in D-POCO's bonded list, or D-POCO missing from D-HU's | The operator forgets `Navegadortz3` on D-POCO, and pairs D-POCO with D-HU if the bond is missing | unpairing and pairing are system dialogs (`units/D-POCO.md`) |

If H1 does not fix the bond, every session run is UNTESTABLE.

## 4. Settings keys

Every run writes `$BASE` plus its own keys with `put` (section 5), from D-HU's round-start backup.
Read the keys back with `rd` before the run's first command. A key that does not read back with its
value voids the run before it starts: write it again.

**base.sh**
```bash
BASE="int:wifi-connection-mode=3 int:native-ap-transport=0 int:native-driver-selection-mode=0 bool:native-poke-all-paired=false set:native-poke-bt-macs= set:auto-start-bt-macs= bool:auto-connect-last-session=false bool:auto-start-on-boot=false bool:auto-start-on-screen-on=false bool:auto-start-on-usb=false bool:auto-start-self-mode=false bool:kill-on-disconnect=false bool:allow-external-configuration=false int:onboarding-version=2 int:log-level=2 int:night-mode=1 del:video-profile-starvation-cap del:native-aa-wake-damage-verdict"
```

| Run | Keys |
|---|---|
| R1, R2, R5 | `$BASE int:view-mode=1` (TEXTURE) |
| R3 | `$BASE int:view-mode=2` (GLES) |
| R4 | `$BASE int:view-mode=0` (SURFACE) |

Why the less obvious keys:

- `view-mode` is read when the projection activity is created, so each backend needs its own session.
- `night-mode=1` is DAY. `0` is AUTO and can turn the map dark mid-run.
- `native-poke-all-paired=false` and the empty MAC lists mean nothing pokes a phone except the
  run's own `ACTION_NATIVE_AA_POKE`.
- `video-profile-starvation-cap` is cleared because an earlier thread can leave it latched.
- `log-level=2` (INFO) carries every app line in section 6. `ACTION_LOG_MARKER` is not in
  `AutomationCommandPolicy.CONFIGURING` on either arm, so markers work with
  `allow-external-configuration` false.

## 5. Harness and verbs

Make `rig-data/rounds/pr-1141-cover-keeps-decoder-round1/` and set `OUT` to it. Copy `ptr_lib.sh`
from `rig-data/rounds/automation-screen-disconnect-round1/` into `$OUT` (if it is missing, cut it
from `archive/rounds/projection-teardown-and-relays-round1-brief.md`). Use only `th_gate` and
`th_report` from it. Save `sx.py` from `automation-screen-disconnect-round2-brief.md` section 5 into
`$OUT` unchanged. Source `ptr_lib.sh`, then `$OUT/cover_lib.sh`, so the second one wins. List every
file in Setup notes.

**luma.py**
```python
# luma.py RAW : mean luma (0-255) of the centre 60% of a raw `adb exec-out screencap` (RGBA_8888)
import struct, sys
d = open(sys.argv[1], 'rb').read()
w, h = struct.unpack('<II', d[:8])
off = len(d) - w * h * 4
x0, x1, y0, y1 = int(w * .2), int(w * .8), int(h * .2), int(h * .8)
s = n = 0
for y in range(y0, y1, 8):
    row = off + y * w * 4
    for x in range(x0, x1, 8):
        i = row + x * 4
        s += .299 * d[i] + .587 * d[i + 1] + .114 * d[i + 2]; n += 1
print(int(s / n))
```

**cover_lib.sh**
```bash
# cover_lib.sh : source after ptr_lib.sh. Needs OUT and OWN. RUN is set per run; PHCAP=1 captures D-POCO.
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
F=/data/data/$PKG/shared_prefs/settings.xml
DHU=27870808938846; POCO=4f4027e9; MOTO=ZY22GC3BM4
send() { local a=$1; shift; adb -s $DHU shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; sleep 0.3; }
mark() { send ACTION_LOG_MARKER --es text "$1" >/dev/null
  if [ -n "$PHCAP" ]; then adb -s $POCO shell log -t OHURIG "$1"; sleep 0.3; fi; }
HF() { echo "$OUT/$RUN.hu.txt"; }
PF() { echo "$OUT/$RUN.ph.txt"; }
cap_start() { adb -s $DHU logcat -G 16M; sleep 0.3; adb -s $DHU logcat -c; sleep 0.3
  stdbuf -oL adb -s $DHU logcat -v time > "$(HF)" & echo $! > "$OUT/$RUN.hu.pid"
  if [ -n "$PHCAP" ]; then adb -s $POCO logcat -G 16M; sleep 0.3; adb -s $POCO logcat -c; sleep 0.3
    stdbuf -oL adb -s $POCO logcat -v time > "$(PF)" & echo $! > "$OUT/$RUN.ph.pid"; fi
  sleep 1; }
cap_stop() { sleep 1
  for f in "$OUT/$RUN.hu.pid" "$OUT/$RUN.ph.pid"; do [ -f "$f" ] && kill "$(cat "$f")" 2>/dev/null; rm -f "$f"; done
  if grep -aqF "AutomationMarker: $RUN-start" "$(HF)"; then echo "wrapped=no" >> "$OUT/$RUN.summary"
  else echo "wrapped=yes" | tee -a "$OUT/$RUN.summary"; fi; }
# hwin A B : head unit lines from marker A to marker B (to the end of the file if B is absent)
hwin() { awk -v a="AutomationMarker: $1" -v b="AutomationMarker: $2" '{sub(/\r$/,"")} function at(s){i=index($0,s); return i>0 && substr($0,i+length(s))~/^[[:space:]]*$/} at(a){on=1} on{print} on&&at(b){exit}' "$(HF)"; }
# pwin A B : the same on the phone capture, by the OHURIG markers
pwin() { awk -v a="$1" -v b="$2" '{sub(/\r$/,"")} /OHURIG/ && $NF==a {on=1} on{print} on && /OHURIG/ && $NF==b {exit}' "$(PF)"; }
# n TEXT LIT [LIT2] : count of lines in TEXT that hold every LIT
n() { local t=$1; shift; local out; out=$(printf '%s\n' "$t")
  for l in "$@"; do out=$(printf '%s\n' "$out" | grep -aF -- "$l"); done
  if [ -z "$out" ]; then echo 0; else printf '%s\n' "$out" | grep -ac .; fi; }
rend() { printf '%s\n' "$1" | grep -acE 'Throughput over [0-9]+ms: rendered=[1-9]'; }
tms() { grep -aF -m1 -- "$1" | awk '{split($2,t,":"); printf "%d\n", (t[1]*3600+t[2]*60+t[3])*1000}'; }
# gap TEXT A B : ms from the first line holding A to the first line holding B, or NA
gap() { local a b; a=$(printf '%s\n' "$1" | tms "$2"); b=$(printf '%s\n' "$1" | tms "$3")
  if [ -n "$a" ] && [ -n "$b" ]; then echo $((b-a)); else echo NA; fi; }
# lines : the head unit capture's line count now. waitsince B S STR : wait up to S s for STR after line B.
lines() { wc -l < "$(HF)"; }
waitsince() { local b=$1 s=$2 str=$3 i; for i in $(seq 1 "$s"); do tail -n +"$((b+1))" "$(HF)" | grep -aqF -- "$str" && return 0; sleep 1; done; return 1; }
rd() { adb -s $DHU shell cat $F | grep -aoE "($1)[^/]*"; sleep 0.3; }
put() { adb -s $DHU shell am force-stop $PKG; sleep 0.3
  cp "$OUT/backup-D-HU.xml" "$OUT/w.xml"; python3 -I "$OUT/sx.py" "$OUT/w.xml" "$@"
  adb -s $DHU push "$OUT/w.xml" /data/local/tmp/ohu-w.xml >/dev/null; sleep 0.3
  adb -s $DHU shell "cp /data/local/tmp/ohu-w.xml $F && chown $OWN $F && chmod 660 $F"; sleep 0.3; }
state() { send ACTION_QUERY_STATE | grep -a 'data='; }
clean_hu() { adb -s $DHU shell am start -a android.intent.action.VIEW -d "headunit://exit" >/dev/null; sleep 3
  adb -s $DHU shell am force-stop $PKG; sleep 0.3; }
# shot TAG : one raw screenshot of D-HU, its luma into $RUN.shots with the host time
shot() { adb -s $DHU exec-out screencap > "$OUT/$RUN-$1.raw"; sleep 0.3
  echo "$1 host=$(date +%s%3N) luma=$(python3 -I "$OUT/luma.py" "$OUT/$RUN-$1.raw")" >> "$OUT/$RUN.shots"; }
# session : wake D-POCO from a stopped app and wait for a picture. Writes ssl= and live= to the summary.
session() { mark $RUN-cmd; send ACTION_NATIVE_AA_POKE --es extra_mac $POCO_BT > "$OUT/$RUN-cmd.reply"
  local b; b=$(lines)
  waitsince "$b" 120 'SSL handshake complete'; echo "ssl=$?" | tee -a "$OUT/$RUN.summary"
  b=$(lines); waitsince "$b" 40 'Throughput over'; sleep 6
  mark $RUN-live; echo "live=$(rend "$(hwin $RUN-cmd $RUN-live)")" | tee -a "$OUT/$RUN.summary"; }
# cycle L HOLD : cover the projection for HOLD s with Settings, return with BACK (or the raise verb), shoot, stay up 8 s
cycle() { local L=$1 H=$2 b
  shot $L-pre; mark $L-cover; b=$(lines)
  adb -s $DHU shell am start -a android.settings.SETTINGS >/dev/null; sleep 0.3
  waitsince "$b" 5 'AapProjectionActivity: onStop'; echo "$L stop=$?" >> "$OUT/$RUN.summary"
  sleep "$H"
  mark $L-back; b=$(lines); echo "$L back host=$(date +%s%3N)" >> "$OUT/$RUN.shots"
  adb -s $DHU shell input keyevent 4; sleep 0.3
  if waitsince "$b" 5 'AapProjectionActivity: onResume'; then echo "$L ret=back" >> "$OUT/$RUN.summary"
  else send ACTION_RAISE_PROJECTION > "$OUT/$RUN-$L-raise.reply"; waitsince "$b" 5 'AapProjectionActivity: onResume'
    echo "$L ret=raise rc=$?" >> "$OUT/$RUN.summary"; fi
  shot $L-r1; shot $L-r2
  rm -f "$OUT/$RUN-$L-pre.raw" "$OUT/$RUN-$L-r2.raw"
  sleep 8; mark $L-end; }
# endrun : user exit, then a clean stop of the head unit app
endrun() { mark $RUN-x; send ACTION_DISCONNECT > "$OUT/$RUN-x.reply"; sleep 8
  mark $RUN-end; clean_hu; cap_stop; th_report $RUN; PHCAP=; }
```

`cycle`'s `sleep "$H"` is the cover time the run measures, so it is a fixed sleep on purpose.

**Verbs and commands used. Nothing else acts on the app:**

| Action | Command |
|---|---|
| wake D-POCO and start a session | `send ACTION_NATIVE_AA_POKE --es extra_mac $POCO_BT` (inside `session`) |
| cover the projection | `adb -s $DHU shell am start -a android.settings.SETTINGS` (a system app, not ours) |
| end the cover | `adb -s $DHU shell input keyevent 4` (BACK, a keyevent, allowed by §0) |
| bring the projection forward if BACK did not | `send ACTION_RAISE_PROJECTION` |
| user exit | `send ACTION_DISCONNECT` |
| marker | `mark <label>` (labels have no spaces) |
| build identity | `state` |
| hard stop | `clean_hu` |

Every `send` must give an `AutomationReceiver: ` line in the head unit capture. A step with no such
line never landed: that step is void, not a FAIL. Never run two adb calls against one unit at the
same time.

## 6. The deciding lines

All are INFO or above, so `log-level=2` carries them. Each was checked with `git grep -F` on both
SHAs; "C only" means it does not exist on B.

| Line (literal to grep) | Where it prints | Arms |
|---|---|---|
| `AapProjectionActivity: onStop` | the activity stopped: the cover is real | both |
| `AapProjectionActivity: onResume` | the projection is back in front | both |
| `Decoder stopped: activity_stopped` | the decoder stopped from `onStop` (B at once, C after 20 s) | both |
| `Decoder stopped: surfaceDestroyed` | the surface went away and took the decoder | both |
| `SurfaceCallback: onSurfaceDestroyed. Surface: ` | the activity saw its surface go | both |
| `AapProjectionActivity: the projection stayed covered, releasing the decoder` | the 20 s release fired | C only |
| `Configuring decoder: ` | a codec was built | both |
| `First frame rendered (hardware decode)` | a new codec's first output | both |
| `VideoDecoder: keyframe reached the codec (` | a keyframe was fed | both |
| `VideoDecoder: keyframe decoded - the picture is repaired` | a rebuilt codec has a real picture | both |
| `Throughput over ` with `rendered=` | the 5 s video counter | both |
| `SSL handshake complete` | a session formed | both |
| `AapTransport stopping and sending byebye (` | a session ended | both |
| `AutomationReceiver: ` and `AutomationMarker: ` | a verb landed, a marker | both |
| `FATAL EXCEPTION` | a crash (framework line) | both |
| phone: `Critical error` | Android Auto rejected something | both |

## 7. Runs

Order: P, R1 with R2, R3, R4, R5, X. Every run: `th_gate` first. Report `th_report $RUN` in each
section. Write the results file after every run.

**Every run is valid only when** its `$RUN.summary` reads `wrapped=no`, `ssl=0` and `live=` 1 or
more, and `MATCH! Starting AapService` is 0 in `hwin $RUN-start $RUN-end`. If `ssl=1`, reboot
D-POCO (P step 4) and run the run once more. A second `ssl=1` makes the run UNTESTABLE.

**A cycle is valid only when** its `stop=0` and `ret=back` or `ret=raise rc=0`. An invalid cycle is
void. Grade each run on its valid cycles and report how many there were.

**Stop rule:** if R1's first 3 cycles all read `stop=1`, the Settings cover does not stop the
activity on D-HU. Mark R1 to R5 INCONCLUSIVE, run X and report.

`gap` uses the time of day. Do not let a run cross midnight.

### P. Precheck, once (about 15 min, plus the builds)

Record every output in Setup notes. Save `luma.py`, `sx.py` and `cover_lib.sh` into `$OUT`, copy
`ptr_lib.sh` in, and source `ptr_lib.sh`, then `cover_lib.sh`.

1. Run the build gate in section 1.
2. D-HU's shell must be root: `adb -s $DHU shell id -u` must print `0`. If not, stop the round.
3. D-HU settings and owner:

   **p3.sh**
   ```bash
   adb -s $DHU shell am force-stop $PKG; sleep 0.3
   adb -s $DHU shell cat $F > $OUT/backup-D-HU.xml
   adb -s $DHU shell stat -c '%U:%G %a' /data/data/$PKG/shared_prefs     # record, even when correct
   OWN=$(adb -s $DHU shell stat -c '%U:%G' $F | tr -d '\r'); echo "OWN=$OWN"
   grep -aoE '(view-mode|night-mode|log-level|video-codec|use-aac-audio)[^/]*' $OUT/backup-D-HU.xml
   adb -s $DHU shell md5sum $(adb -s $DHU shell pm path $PKG | cut -d: -f2 | tr -d '\r')   # the build left by the last round
   ```
   If the `shared_prefs` directory is not owned by the app's user, `chown` it to `$OWN` and say so.
4. Phones. If `adb devices | grep -ac ZY22GC3BM4` prints 1, put D-MOTO in airplane mode and read its
   Bluetooth off (`adb -s $MOTO shell cmd connectivity airplane-mode enable`, then
   `adb -s $MOTO shell dumpsys bluetooth_manager | grep -a -m1 -E 'enabled:|state:'`). Then D-POCO,
   a fresh start:

   **p4.sh**
   ```bash
   adb -s $POCO shell am force-stop $PKG; sleep 0.3
   adb -s $POCO reboot; adb -s $POCO wait-for-device
   for i in $(seq 1 30); do   # poll every 2 s, cap 60 s: boot done and Gearhead running
     [ "$(adb -s $POCO shell getprop sys.boot_completed | tr -d '\r')" = 1 ] && [ -n "$(adb -s $POCO shell pidof com.google.android.projection.gearhead | tr -d '\r')" ] && break; sleep 2; done; echo "boot_poll=$i"
   adb -s $POCO shell dumpsys bluetooth_manager | grep -a -m1 -E 'enabled:|state:'      # must read on; else svc bluetooth enable
   adb -s $POCO shell dumpsys wifi | grep -a -m1 'Wi-Fi is'                            # must read enabled; else svc wifi enable
   adb -s $POCO shell input keyevent KEYCODE_HOME; sleep 1
   adb -s $POCO shell dumpsys wifip2p | grep -a -m2 -E 'isGroupOwner|groupFormed'        # isGroupOwner must be false
   adb -s $POCO shell dumpsys package com.google.android.projection.gearhead | grep -a -m1 versionName
   POCO_BT=$(adb -s $POCO shell settings get secure bluetooth_address | tr -d '\r'); echo "POCO_BT=$POCO_BT"   # round history: DC:B7:2E:5E:4E:59
   adb -s $POCO shell dumpsys bluetooth_manager | grep -a -A20 'Bonded devices' > $OUT/poco-bonds.txt
   grep -ac Navegadortz2 $OUT/poco-bonds.txt     # must be 1 or more
   grep -ac Navegadortz3 $OUT/poco-bonds.txt     # must be 0
   ```
   If `isGroupOwner` reads true, send
   `adb -s $POCO shell am start -a android.intent.action.VIEW -d "headunit://exit"`, wait 3 s,
   force-stop it, and read again. If a bond check fails, ask H1 once, then read again.
5. D-HU state:

   **p5.sh**
   ```bash
   adb -s $DHU shell dumpsys bluetooth_manager | grep -a -m1 -E 'enabled:|state:'      # must read on
   adb -s $DHU shell dumpsys bluetooth_manager | grep -a -A20 'Bonded devices' | grep -ac "$POCO_BT"   # must be 1 or more
   adb -s $DHU shell appops get $PKG SYSTEM_ALERT_WINDOW            # must read allow
   adb -s $DHU shell cmd package resolve-activity -a android.settings.SETTINGS | grep -a -m2 -E 'packageName|name='
   ```
   If `SYSTEM_ALERT_WINDOW` does not read `allow`, run `adb -s $DHU shell appops set $PKG SYSTEM_ALERT_WINDOW allow`, read it again and say so.
   The `resolve-activity` lines name the cover app. Record them.
6. Confirm no stale processes: `adb -s $DHU shell ps -A | grep -ac headunitrevived` reads 0.
7. Install C and check identity:

   **p7.sh**
   ```bash
   adb -s $DHU install -r -d $OUT/apk/C.apk
   adb -s $DHU shell cat $F > $OUT/post-install-D-HU.xml
   diff $OUT/backup-D-HU.xml $OUT/post-install-D-HU.xml     # record; if it differs, push the backup back (put with no keys)
   RUN=P; state | tee $OUT/P.state                           # commit must start 24dd2219
   adb -s $DHU shell am force-stop $PKG
   ```

**A failure in step 1, 2 or 7 stops the round.**

### R1. TEXTURE, five 6 s covers on C (the point of the round, about 6 min with R2's setup)

**r1.sh**
```bash
RUN=R1; PHCAP=1; th_gate; put $BASE int:view-mode=1; rd 'view-mode|night-mode|log-level|wifi-connection-mode' | tee $OUT/R1.keys
cap_start; mark R1-start
session
for i in 1 2 3 4 5; do cycle R1-c$i 6; done
mark R1-tail; sleep 25; mark R1-done
```

Then run R2 at once, in the same capture. R2 ends the run with `endrun`.

For each cycle `L` (`R1-c1` to `R1-c5`): `COV=$(hwin $L-cover $L-back)`, `RET=$(hwin $L-back $L-end)`,
`CYC=$(hwin $L-cover $L-end)`, and from `$OUT/R1.shots` the luma of `$L-pre`, `$L-r1`, `$L-r2`.
Run-wide: `ALL=$(hwin R1-live R1-done)`,
`PALL=$(pwin R1-live R1-done)`.

**PASS when all hold:**

1. At least 4 valid cycles.
2. For every valid cycle: `n "$COV" 'Decoder stopped: '` is 0.
3. For every valid cycle: `n "$CYC" 'Configuring decoder: '` is 0.
4. For every valid cycle: `n "$RET" 'AapProjectionActivity: onResume'` is 1.
5. For every valid cycle: luma `r1` is at least half of luma `pre`. If `pre` is below 40 the map is too dark to grade: this condition is INCONCLUSIVE for that cycle; say so.
6. In `ALL`: `AapProjectionActivity: the projection stayed covered, releasing the decoder`,
   `SSL handshake complete`, `AapTransport stopping and sending byebye (` and `FATAL EXCEPTION`
   are each 0.
7. In `PALL`: `Critical error` is 0.

Condition 6's first count is the cancel check: cycles repeat every ~15 s and `R1-tail` waits 25 s,
so a release that `onStart` failed to cancel would fire inside `ALL`.

**What a PASS would look like if the change did nothing.** On B, condition 2 reads 1
(`Decoder stopped: activity_stopped`) in every cycle, and condition 3 is 1 or more. The `stop=0`
gate proves the cover was real, and `live=` proves the stream was running before it.

Record per cycle: `n "$COV" 'AapProjectionActivity: onStop'`, `rend "$COV"` (whether the hidden
decoder kept rendering), `n "$RET" 'VideoDecoder: keyframe reached the codec ('`,
`n "$CYC" 'SurfaceCallback: onSurfaceDestroyed. Surface: '`, the three luma values, the `back` to
`r1` host-time difference in ms, and `ret=`.

### R2. TEXTURE, two 30 s covers on C (in R1's session, about 2 min)

Run only if R1's session is still live (`n "$(hwin R1-tail R1-done)" 'AapTransport stopping and sending byebye ('` is 0). Otherwise R2 is UNTESTABLE; run `endrun` and go to R3.

**r2.sh**
```bash
for i in 1 2; do cycle R2-c$i 30; done
mark R2-done
endrun
```

Windows per cycle as in R1, with `L` = `R2-c1`, `R2-c2`. `PALL=$(pwin R2-c1-cover R2-done)` (the
phone markers are named the same).

**PASS when all hold, for every valid cycle:**

1. `n "$COV" 'AapProjectionActivity: the projection stayed covered, releasing the decoder'` is 1.
2. `gap "$COV" 'AapProjectionActivity: onStop' 'the projection stayed covered'` is between 19000 and 21500.
3. `n "$COV" 'Decoder stopped: activity_stopped'` is 1 or more.
4. In `hwin R2-c1-cover R2-done`: `SSL handshake complete`, `AapTransport stopping and sending byebye (` and `FATAL EXCEPTION` are each 0.
5. In `PALL`: `Critical error` is 0.

Record per cycle (this is the review's question, not a condition):
`n "$COV" 'Configuring decoder: '`, `gap "$COV" 'the projection stayed covered' 'Configuring decoder: '`,
`n "$COV" 'First frame rendered (hardware decode)'`, the `rend` count of the `Throughput over`
lines in `COV` that come after the release line, `gap "$RET" 'AutomationMarker: '"$L"'-back' 'VideoDecoder: keyframe decoded - the picture is repaired'`,
and the three luma values. A `Configuring decoder: ` within a few seconds of the release, with the
projection still covered, means the release lasted only until the next frame.

### R3. GLES, three 6 s covers on C (about 5 min)

**r3.sh**
```bash
RUN=R3; PHCAP=1; th_gate; put $BASE int:view-mode=2; rd 'view-mode|night-mode|log-level' | tee $OUT/R3.keys
cap_start; mark R3-start
session
for i in 1 2 3; do cycle R3-c$i 6; done
mark R3-tail; sleep 25; mark R3-done
endrun
```

Windows as in R1, with `L` = `R3-c1` to `R3-c3`, `ALL=$(hwin R3-live R3-done)`,
`PALL=$(pwin R3-live R3-done)`.

**PASS when all hold** (this run guards against a regression; it does not require the change to help):

1. At least 2 valid cycles.
2. For every valid cycle: `n "$RET" 'AapProjectionActivity: onResume'` is 1, and luma `r2` is at
   least half of luma `pre`.
3. In `ALL`: `AapProjectionActivity: the projection stayed covered, releasing the decoder`,
   `SSL handshake complete`, `AapTransport stopping and sending byebye (` and `FATAL EXCEPTION`
   are each 0.
4. In `PALL`: `Critical error` is 0.

Record per cycle (the measurement the round wants):
`n "$COV" 'Decoder stopped: surfaceDestroyed'`, `n "$COV" 'SurfaceCallback: onSurfaceDestroyed. Surface: '`,
`n "$COV" 'Decoder stopped: activity_stopped'`, `n "$CYC" 'Configuring decoder: '`,
`gap "$RET" 'AutomationMarker: '"$L"'-back' 'VideoDecoder: keyframe decoded - the picture is repaired'`,
and the three luma values. Say in one line per cycle: decoder kept, or decoder stopped by which reason.

### R4. SURFACE, three 6 s covers on C (about 5 min)

Exactly R3 with `RUN=R4`, `int:view-mode=0` and the labels `R4-*`:

**r4.sh**
```bash
RUN=R4; PHCAP=1; th_gate; put $BASE int:view-mode=0; rd 'view-mode|night-mode|log-level' | tee $OUT/R4.keys
cap_start; mark R4-start
session
for i in 1 2 3; do cycle R4-c$i 6; done
mark R4-tail; sleep 25; mark R4-done
endrun
```

Grade and record as R3.

### R5. Baseline: TEXTURE, five 6 s covers on B (about 7 min)

**r5.sh**
```bash
adb -s $DHU shell am force-stop $PKG; sleep 0.3
adb -s $DHU install -r -d $OUT/apk/B.apk
RUN=R5pre; state | tee $OUT/R5.state       # commit must start afe6a534
RUN=R5; PHCAP=1; th_gate; put $BASE int:view-mode=1; rd 'view-mode|night-mode|log-level' | tee $OUT/R5.keys
cap_start; mark R5-start
session
for i in 1 2 3 4 5; do cycle R5-c$i 6; done
mark R5-done
endrun
```

If `R5.state` does not show `afe6a534`, the install did not take: install B again and check once
more. A second miss makes R5 UNTESTABLE.

Windows as in R1, with `L` = `R5-c1` to `R5-c5`.

**PASS (the defect reproduces in the log) when:** at least 3 valid cycles have
`n "$COV" 'Decoder stopped: activity_stopped'` of 1 or more.

Record per cycle, the same fields as R1 plus
`gap "$RET" 'AutomationMarker: '"$L"'-back' 'VideoDecoder: keyframe decoded - the picture is repaired'`
and `n "$CYC" 'First frame rendered (hardware decode)'`. **The luma values here are the number the
round exists for**: say how many cycles had luma `r1` below half of `pre`, and the same for `r2`.

### X. Restore

**x.sh**
```bash
adb -s $DHU shell am force-stop $PKG; sleep 0.3
adb -s $DHU install -r -d $OUT/apk/C.apk
put                                   # no keys: D-HU's round-start backup
adb -s $DHU shell cat $F > $OUT/restored-D-HU.xml; diff $OUT/backup-D-HU.xml $OUT/restored-D-HU.xml   # must be empty
```

If D-MOTO went into airplane mode in P, take it out and read its radios back on. Zip `$OUT`
(the `.raw` shots included) as the round's asset.

## 8. Do not re-run

Nothing; this is the thread's first round.

## 9. Report back

1. R1: valid cycles, and in how many `Decoder stopped: ` and `Configuring decoder: ` were 0 and luma
   `r1` held at least half of `pre`.
2. R5 against R1: in how many valid cycles luma `r1` fell below half of `pre` on B, and on C.
3. R3 and R4: per backend, whether a 6 s cover stopped the decoder anyway, and by which reason.
   R2: the `Configuring decoder: ` count after the release, and its gap from the release line.
