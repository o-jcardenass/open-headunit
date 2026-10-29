# pr-1146-video-preflight, round 1 brief: the wizard's video test, sound check and home suggestion

Publish name on `transfer/rig-rounds`: `pr-1146-video-preflight-round1-brief.md`. Results go in
`pr-1146-video-preflight-round1-results.md`. Evidence goes in a new release
`rig-evidence-pr-1146-video-preflight` as the asset `pr-1146-video-preflight-round1-captures.zip`.

One build, no baseline. Units: D-HU (head unit), D-POCO (phone), D-SAM (head unit, API 19, wizard
only). D-MOTO is held in airplane mode if it is plugged in. D-HP takes no part.

Queue line: `pr-1146-video-preflight-round1-brief.md` (C `90b3d74b`, upstream `pull/1146/head`, a contributor
wizard PR on `main` `e9377ff9`; one build, no baseline; units D-HU, D-POCO and D-SAM, D-MOTO airplane mode;
operator present for Stage O; W1 is the point).

Pairing: none. The round uses D-HU, D-POCO and D-SAM. A round on D-HP or D-MOTO alone can run beside it (§3b).

| Run | Unit | What it proves | Session | Taps |
|---|---|---|---|---|
| P | all | build gate, APK identity, rig state | no | 0 |
| **W1** | D-HU | **the point: the video test runs on hardware, the box comes ticked, and Next writes exactly what the test recommended** | no | 5 |
| W2 | D-HU | a session on W1's settings projects, announces them, and stores one health record | yes | 0 |
| W3 | D-HU | a session left from the home screen before it ends still stores a health record | yes | 0 |
| H1 | D-HU | a seeded codec suggestion shows, and Apply writes H.264; what shows on the next launch | no | 1 |
| H2 | D-HU | a seeded frame-rate suggestion, Not now hides it and it stays hidden | no | 1 |
| H3 | D-HU | no suggestion while a session is live; it comes back after the session | yes | 0 |
| S1 | D-SAM | the video test on Android 4.4 | no | 5 |
| O1 to O4 | D-HU, D-SAM | Stage O, the operator: the untick control and the guided sound check | some | operator |
| X | all | restore | no | 0 |

**Time budget.** Pre-flight 15 min beside the build (25 min). W1 6 min, W2 5 min, W3 5 min, H1 4 min,
H2 4 min, H3 5 min, S1 6 min, Stage O 25 min, restore 5 min, thermal waits about 8 min.
**About 1 h 55 min in all.** Stage O needs the operator for its 25 minutes; run it last.

**Injected taps.** The cap is 5 per run (`rig-quirks/topics/tooling.md`, "Injected taps"). Each tap
resolves its target in a fresh `uiautomator dump` by resource id, asserts exactly one match and any
state the run names (`enabled`, `checked`), and logs the coordinates (`tapid`, section 5). A target
that does not resolve voids that run. No run searches, scrolls or types.

**Hand steps (Stage O only).** The operator taps the wizard by hand in O1 to O4. Two reasons, both
outside the verb set: reaching the sound step takes 7 Next presses and its answers 2 to 4 more, which
is over the 5-tap cap with no verb to skip steps; and the sound check asks a person what they heard,
which only a person can answer. D-HU has no speaker, so on D-HU the operator presses the answer the
run names whatever they hear; on D-SAM they answer by ear.

## 1. Build and baseline

| Arm | Ref | SHA | Tree |
|---|---|---|---|
| **C** (candidate) | upstream `pull/1146/head` | `90b3d74bd0b0f0c7ffce36f718f25bbea6c8c96f` | `e8aca7a51a477706de40ce3a36a322bcb492b8fb` |

First round of the thread. C is the contributor's PR head as pushed: 17 commits on `main` `e9377ff9`,
the base most queued rounds use. The PR conflicts with today's `main` (30 files, the home and wizard
layouts and the translations), and the contributor will resolve that. No merge is built here, so the
round tests the PR exactly as submitted. It is not on the fork. Fetch it by URL:

**fetch.sh**
```bash
U=https://github.com/andreknieriem/open-headunit.git
git fetch $U pull/1146/head
git cat-file -t 90b3d74bd0b0f0c7ffce36f718f25bbea6c8c96f          # must print commit
git rev-parse 90b3d74bd0b0f0c7ffce36f718f25bbea6c8c96f^{tree}       # must print e8aca7a51a477706de40ce3a36a322bcb492b8fb
```

If a line differs, stop the round.

- One build: in the session's worktree, `git checkout --detach 90b3d74b`, check the tree, then
  `$RIG_TOOLKIT/rig_thermal.sh wait 70` and `HUR_DIR=<wt> MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh`.
  Copy the APK at once to `$OUT/apk/C.apk` and record its md5.
- The rig runs no unit tests. The dev host ran the JVM suite at this SHA: 3560 tests, 0 failures,
  0 skipped. **A build failure stops the round.**
- DEX identity symbol: `unzip -p $OUT/apk/C.apk 'classes*.dex' | strings | grep -acF VideoPreflightRunner`
  must print 1 or more. The class is new in this PR.
- Install in P on D-HU and D-SAM. D-POCO is the phone and keeps what it has.

## 2. What this is and why

The PR adds three things to the app. None of them has run on a real head unit yet; the contributor
tested on emulators only and asks for on-device testing.

1. **A video test in the setup wizard** (step 4 of 13, "Video"). It plays up to four synthetic clips
   (`res/raw/preflight_*`: H.264 at 480p, 720p and 1080p, H.265 at 1080p) through a fresh decoder
   into the same view type the projection uses (TextureView, SurfaceView or GLES). It reads a frame
   number painted into the picture to check what is drawn, and recommends a codec, a resolution and
   a view mode. A box "apply" comes ticked after the test, **also for a user who already set the app
   up**. Pressing Next past the Display step then writes the recommendation. Unticking keeps the
   current settings.
2. **A guided sound check** (step 8, "Sound"). It plays a test tone through the projection's audio
   output and asks: fine, nothing heard, or crackles. "Nothing heard" offers each audio channel in
   turn and keeps the first that sounds. "Crackles" turns AAudio off and plays again. Then it asks
   what the unit's own radio does during Android Auto (always pause, keep playing, let the app
   decide), which writes `playback-focus-mode`.
3. **A home screen suggestion.** Each projection that ends stores a short record in a separate
   preferences file, `shared_prefs/projection-health.xml`. When 2 of the last 5 records show the
   same local fault (a codec fallback, a renderer recovery, or more than 15% of fed frames not
   rendered), the home screen shows a banner with Apply and Not now. It hides while a session is up
   and while the connection issue banner is up.

The code review on the dev host found faults this round can measure. Each run says which it checks:

- **R-A (W1).** Ticking apply also writes `screen-orientation` (to LANDSCAPE or PORTRAIT), which the
  test does not measure. A user on SYSTEM or LANDSCAPE_REVERSE loses that setting.
- **R-B (W1).** On a panel up to 1920 wide, the recommendation is always H.264, even when the user
  stores H.265 and the H.265 clip passed. On one reporter's unit H.265 was the configuration with no
  audio underruns.
- **R-C (W3).** The health record reads the decoder's fed, dropped and skipped counters, which every
  decoder stop clears, including the stop when the projection leaves the screen. So a session the
  user leaves to the home screen before it ends is expected to store no record, and a codec
  fallback before any stop is forgotten.
- **R-D (H1).** The suggestion window is not cleared when one suggestion is applied. With the same
  stored sessions, the next launch can offer a second change before any new session has run.

A FAIL on one of these is the review finding confirmed on hardware, not a broken rig.

## 3. What is different about this round

- **The wizard is opened by setting `onboarding-version` to 0** with the app stopped, then launching
  `MainActivity`, which starts the wizard on its first resume. `has-completed-setup-wizard=true` makes
  it the re-run on an existing install, the case the PR changed. `has-accepted-disclaimer=true` ticks
  the safety box, and `connection-modes=wifi` enables Next on the connection step, so steps 1 to 5 need
  only Next.
- **The wizard writes the recommendation when Next leaves the Display step (step 5)**, not at the end.
  W1 and S1 stop there with a force-stop. The wizard never finishes, so `onboarding-version` stays 0;
  `put` writes 2 again before the next run.
- **D-HU's `shared_prefs` can be root-owned** (`rig-quirks/units/D-HU.md`). W1, H1 and H2 grade values
  the app writes. P records the `stat` and fixes the owner.
- **D-SAM:** no `-p` on `am`, no `sed`, settings go in by `run-as cp` (`rig-quirks/units/D-SAM.md`).
  `uiautomator dump` carries resource ids on API 19. Mount it in its usual position with
  `screen-orientation=2`.
- **No speaker on D-HU** (`rig-quirks/topics/audio.md`). The sound check is graded on D-HU by the
  settings each answer writes, and on D-SAM by the operator's ear.
- **The video test plays the clips on the unit's screen** in a small preview. Take one screenshot of
  the result per run (`shot`), as evidence of the verdict box only. No verdict rests on it.
- **The map does not need to move.** W2 and W3 need at least 150 fed frames for a health record to
  count (`ProjectionHealthPolicy.MIN_FED_FRAMES`). The runs hold the session 90 s and record the fed
  sum, so a short or idle session reads INCONCLUSIVE rather than FAIL.
- **W2 may show a "renderer" bar at the bottom of the projection.** When W1 changed `view-mode`, the
  wizard sets `pending-renderer-confirm`, and the first session shows a one-time question bar. It
  reverts nothing and clears itself. Do not tap it; record whether it showed.
- **SSL line.** Match `SSL handshake complete` with no `Handshake:` prefix (`log-level=2`).
- **Expected INCONCLUSIVE:** S1's SurfaceView rows (no read-back below Android 7.0, by design, "picture
  unverified"); W2 and W3's record conditions if the fed sum is below 150.

## 4. Settings keys

Every D-HU run writes `$BASE` plus its own keys with `put` or `putcur` (section 5). Read the keys back
with `rd` before the run's first command. A key that does not read back voids the run: write it again.

**base.sh**
```bash
BASE="int:wifi-connection-mode=3 int:native-ap-transport=0 int:native-driver-selection-mode=0 bool:native-poke-all-paired=false set:native-poke-bt-macs= set:auto-start-bt-macs= bool:auto-connect-last-session=false bool:auto-start-on-boot=false bool:auto-start-on-screen-on=false bool:auto-start-on-usb=false bool:auto-start-self-mode=false bool:kill-on-disconnect=false bool:allow-external-configuration=false int:onboarding-version=2 int:log-level=2 int:night-mode=1 del:video-profile-starvation-cap del:native-aa-wake-damage-verdict"
WIZ="int:onboarding-version=0 bool:has-completed-setup-wizard=true bool:has-accepted-disclaimer=true set:connection-modes=wifi"
```

| Run | Keys on top of `$BASE` |
|---|---|
| W1 | `$WIZ str:video-codec=H.265 int:resolutionId=1 int:view-mode=2 int:screen-orientation=0 int:fps-limit=60` |
| W2, W3 | `putcur int:onboarding-version=2` on the file W1 left (keeps W1's writes) |
| H1, H3 | `str:video-codec=H.265 int:view-mode=1 int:resolutionId=0 int:fps-limit=60` plus `ICLR` |
| H2 | `str:video-codec=H.264 int:view-mode=0 int:resolutionId=0 int:fps-limit=60` plus `ICLR` |
| S1 (D-SAM) | `int:log-level=2 int:screen-orientation=2 $WIZ str:video-codec=H.264 int:resolutionId=1 int:view-mode=2` on D-SAM's own backup |

Why the less obvious keys:

- W1's seed is chosen so the test has to move it: 480p (`resolutionId=1`) is below any panel the
  rig has, GLES (`view-mode=2`) is the last renderer the test tries, H.265 is R-B, and SYSTEM
  orientation (`0`) is R-A. A key the recommendation leaves at its seed proves nothing for that key;
  the run says so.
- `ICLR` is a `del:` for every `connection-issue-` key D-HU holds (`iclr`, section 5), so the
  connection issue banner cannot hide the suggestion.
- `view-mode`: 0 SURFACE, 1 TEXTURE, 2 GLES. `resolutionId`: 0 Auto, 1 480p, 2 720p, 3 1080p,
  4 "1440p (2K)", 5 "2160p (4K)". `playback-focus-mode`: 0 AUTO, 1 ALWAYS, 2 NEVER.
- `log-level=2` (INFO) carries every app line in section 6. `ACTION_LOG_MARKER` is not in
  `AutomationCommandPolicy.CONFIGURING` on C, so markers work with `allow-external-configuration` false.

## 5. Harness

Make `rig-data/rounds/pr-1146-video-preflight-round1/` and set `OUT` to it. Copy into `$OUT`, unchanged:
`ptr_lib.sh` from `rig-data/rounds/automation-screen-disconnect-round1/` (use only `th_gate` and
`th_report`), `sx.py` from `automation-screen-disconnect-round2-brief.md` section 5, and `cover_lib.sh`
and `luma.py` from `pr-1141-cover-keeps-decoder-round1-brief.md` section 5. Save the four files below.
Source `ptr_lib.sh`, then `cover_lib.sh`, then `pf_lib.sh`. List every file in Setup notes.

From `cover_lib.sh` the runs use `send`, `mark`, `cap_start`, `cap_stop`, `hwin`, `pwin`, `n`, `rend`,
`lines`, `waitsince`, `rd`, `put`, `state`, `clean_hu`, `session` and `endrun`, all on D-HU.

**uiq.py**
```python
# uiq.py <dump.xml> <id> [attr=value] : "x y" of the one node whose resource-id ends ":id/<id>"
# with a non-empty box; exit 1 on 0 or 2+ matches, exit 2 when attr=value does not hold.
# uiq.py count <dump.xml> <id> : number of such nodes.
import sys, re, xml.etree.ElementTree as ET
args = sys.argv[1:]
cnt = args[0] == 'count'
if cnt: args = args[1:]
path, rid = args[0], args[1]
def box(n):
    m = [int(v) for v in re.findall(r'-?\d+', n.get('bounds', ''))]
    return m if len(m) == 4 and m[2] > m[0] and m[3] > m[1] else None
hits = [n for n in ET.parse(path).getroot().iter('node')
        if (n.get('resource-id') or '').endswith(':id/' + rid) and box(n)]
if cnt: print(len(hits)); sys.exit(0)
if len(hits) != 1: print('matches', len(hits)); sys.exit(1)
if len(args) > 2:
    k, v = args[2].split('=', 1)
    if hits[0].get(k) != v: print('%s=%s' % (k, hits[0].get(k))); sys.exit(2)
b = box(hits[0]); print((b[0] + b[2]) // 2, (b[1] + b[3]) // 2)
```

**ph.py**
```python
# ph.py write OUT COUNT SESSION... : a projection-health.xml with COUNT and these session lines
# ph.py read FILE : count=, dismissed=, dismissed_at=, then one line per stored session
# SESSION is 12 fields joined by "|": endedAtMs|codec|viewMode|resolutionId|fpsLimit|codecFallback|
#   rendererRecoveries|forcedSurface|framesFed|framesDropped|framesSkipped|framesRendered
import sys, xml.etree.ElementTree as ET
from xml.sax.saxutils import escape
if sys.argv[1] == 'write':
    out, count, rows = sys.argv[2], int(sys.argv[3]), sys.argv[4:]
    body = '&#10;'.join(escape(r) for r in rows)
    open(out, 'w').write("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n"
        '    <string name="sessions">%s</string>\n    <int name="session-count" value="%d" />\n</map>\n' % (body, count))
    sys.exit(0)
try:
    root = ET.parse(sys.argv[2]).getroot()
except Exception:
    print('count=0'); print('dismissed=none'); print('dismissed_at=none'); sys.exit(0)
v = {e.get('name'): (e.get('value') if e.get('value') is not None else (e.text or '')) for e in root}
print('count=%s' % v.get('session-count', 0)); print('dismissed=%s' % v.get('dismissed-action', 'none'))
print('dismissed_at=%s' % v.get('dismissed-at', 'none'))
for line in (v.get('sessions') or '').split('\n'):
    if line: print('session ' + line)
```

**pf_lib.sh**
```bash
# pf_lib.sh : source after cover_lib.sh. Needs OUT, OWN (D-HU), POCO_BT. RUN is set per run.
SAM=30041c35642d2200
MAIN=$PKG/com.andrerinas.openheadunit.main.MainActivity
PH=/data/data/$PKG/shared_prefs/projection-health.xml
mkdir -p $OUT/ui
# putcur KEYS : like put, but edits D-HU's current file (keeps what the app wrote)
putcur() { adb -s $DHU shell am force-stop $PKG; sleep 0.3
  adb -s $DHU shell cat $F > "$OUT/w.xml"; sleep 0.3; python3 -I "$OUT/sx.py" "$OUT/w.xml" "$@"
  adb -s $DHU push "$OUT/w.xml" /data/local/tmp/ohu-w.xml >/dev/null; sleep 0.3
  adb -s $DHU shell "cp /data/local/tmp/ohu-w.xml $F && chown $OWN $F && chmod 660 $F"; sleep 0.3; }
# iclr : del: specs for every connection-issue- key on D-HU now
iclr() { adb -s $DHU shell cat $F | grep -aoE 'name="connection-issue-[^"]*"' | sed -E 's/name="([^"]*)"/del:\1/' | tr '\n' ' '; sleep 0.3; }
# ph_seed COUNT SESSION... / ph_clear / ph_read TAG : the health file on D-HU (root)
ph_seed() { python3 -I "$OUT/ph.py" write "$OUT/ph-seed.xml" "$@"
  adb -s $DHU push "$OUT/ph-seed.xml" /data/local/tmp/ohu-ph.xml >/dev/null; sleep 0.3
  adb -s $DHU shell "cp /data/local/tmp/ohu-ph.xml $PH && chown $OWN $PH && chmod 660 $PH"; sleep 0.3; }
ph_clear() { adb -s $DHU shell rm -f $PH; sleep 0.3; }
ph_read() { adb -s $DHU shell cat $PH > "$OUT/$RUN-ph-$1.xml" 2>/dev/null; sleep 0.3
  python3 -I "$OUT/ph.py" read "$OUT/$RUN-ph-$1.xml" | tee "$OUT/$RUN-ph-$1.txt"; }
# keys U TAG : the settings a wizard run can change, read from unit U's file
keys() { if [ "$1" = $DHU ]; then adb -s $1 shell cat $F; else adb -s $1 shell run-as $PKG cat shared_prefs/settings.xml; fi \
  | tr -d '\r' > "$OUT/$RUN-$2.xml"; sleep 0.3
  grep -aoE '(video-codec|"resolutionId|view-mode|screen-orientation|fps-limit|pending-renderer-confirm|onboarding-version|dpi-pixel-density|use-aaudio-output|playback-focus-mode|media-audio-stream|guidance-audio-stream|system-audio-stream|separate-audio-streams)[^/]*' \
    "$OUT/$RUN-$2.xml" | tee "$OUT/$RUN-$2.keys"; }
# dump U TAG : uiautomator dump and screenshot of unit U
dump() { adb -s $1 shell rm -f /sdcard/ohu-ui.xml; sleep 0.3
  adb -s $1 shell uiautomator dump /sdcard/ohu-ui.xml >/dev/null 2>&1; sleep 0.3
  adb -s $1 pull /sdcard/ohu-ui.xml "$OUT/ui/$RUN-$2.xml" >/dev/null 2>&1; sleep 0.3
  adb -s $1 shell screencap -p /sdcard/ohu-ui.png; sleep 0.3
  adb -s $1 pull /sdcard/ohu-ui.png "$OUT/ui/$RUN-$2.png" >/dev/null 2>&1; sleep 0.3
  [ -s "$OUT/ui/$RUN-$2.xml" ] || echo "$RUN $2 DUMP_FAILED" | tee -a "$OUT/$RUN.summary"; }
# tapid U TAG ID [attr=value] : dump, one match, the state holds, tap it. 5 per run.
tapid() { local u=$1 tag=$2 id=$3 st=$4 xy k
  k=$(cat "$OUT/taps-$RUN" 2>/dev/null || echo 0)
  [ "$k" -ge 5 ] && { echo "$RUN BUDGET_SPENT" | tee -a "$OUT/$RUN.summary"; return 3; }
  dump $u $tag
  xy=$(python3 -I "$OUT/uiq.py" "$OUT/ui/$RUN-$tag.xml" $id $st) || { echo "$RUN $tag $id NO_TARGET $xy" | tee -a "$OUT/$RUN.summary"; return 2; }
  adb -s $u shell input tap $xy; sleep 0.3
  echo $((k+1)) > "$OUT/taps-$RUN"; echo "$RUN $tag $id $xy $(date +%T)" | tee -a "$OUT/taps.log"; sleep 2; }
# ucount U TAG ID : dump, then the number of visible nodes with that id
ucount() { dump $1 $2; python3 -I "$OUT/uiq.py" count "$OUT/ui/$RUN-$2.xml" $3; }
# wizard U : launch, Next x3 to the Video step, wait for the test, check the box, Next x2. Writes vt= and apply=.
wizard() { local u=$1 b
  adb -s $u shell am start -n $MAIN >/dev/null; sleep 6
  mark $RUN-wiz
  tapid $u s0 onb_next enabled=true || return 1
  tapid $u s1 onb_next enabled=true || return 1
  b=$(lines); tapid $u s2 onb_next enabled=true || return 1
  waitsince "$b" 300 'VideoPreflight: recommended codec='; echo "vt=$?" | tee -a "$OUT/$RUN.summary"
  sleep 3; mark $RUN-result
  dump $u result
  python3 -I "$OUT/uiq.py" "$OUT/ui/$RUN-result.xml" onb_video_apply checked=true >/dev/null; echo "apply=$?" | tee -a "$OUT/$RUN.summary"
  tapid $u s3 onb_next enabled=true || return 1
  tapid $u s4 onb_next enabled=true || return 1
  sleep 3; mark $RUN-applied; }
# reco : the recommended codec, resolution name and view mode from the run's window
reco() { hwin $RUN-wiz $RUN-applied | grep -aF 'VideoPreflight: recommended codec=' | tail -1 \
  | sed -E 's/.*codec=([^ ]+) resolution=(.+) viewMode=([A-Z]+) verified=([a-z]+) notes=(.*)$/codec=\1\nres=\2\nview=\3\nverified=\4\nnotes=\5/' \
  | tee "$OUT/$RUN.reco"; }
```

`wizard` is written for D-HU, where `lines` and `waitsince` read the running capture. S1 sets
`DHU=$SAM` for its own block so the same helpers read D-SAM's capture (section 7, S1).

**Verbs and commands used. Nothing else acts on the app:**

| Action | Command |
|---|---|
| open the wizard (with `onboarding-version=0`) or the home screen | `adb -s <u> shell am start -n $MAIN` |
| a wizard button or a banner button | `tapid` (5 per run) |
| wake D-POCO and start a session | `send ACTION_NATIVE_AA_POKE --es extra_mac $POCO_BT` (inside `session`) |
| leave the projection for the launcher | `adb -s $DHU shell input keyevent 3` (HOME, a keyevent, allowed by §0) |
| user exit | `send ACTION_DISCONNECT` (inside `endrun`) |
| marker | `mark <label>` |
| build identity | `state` |
| hard stop | `clean_hu`, or `adb -s <u> shell am force-stop $PKG` on D-SAM |

Every `send` must give an `AutomationReceiver: ` line in the head unit capture. A step with no such
line never landed: that step is void, not a FAIL. Never run two adb calls against one unit at once.

## 6. The deciding lines

All are INFO or above, so `log-level=2` carries them. Each was checked with `grep -F` on C.

| Line | Meaning |
|---|---|
| `VideoPreflight: backend=` | one clip on one renderer: `fed= rendered= drawn= samples= bad= failure=` (`NONE` is a pass) |
| `VideoPreflight: recommended codec=` | the test's answer: `codec= resolution= viewMode= verified= notes=` |
| `AudioOutputTester: backend=` | one test tone: `AAUDIO` or `AUDIO_TRACK`, `underruns=`, `fallback=` |
| `AudioOutputTester: AAudio failed, using AudioTrack: ` | the tone fell back from AAudio |
| `Media Sink Setup Request: ` with `on channel VIDEO` | the phone's codec: `7` is H.265, `3` is H.264 |
| `Projection backend: viewMode=` | the renderer the projection built |
| `[RES_CAP] resolutionId=` | the resolution the session announced from |
| `Throughput over ` with `rendered=` and `fed=` | the 5 s video counter |
| `Decoder stopped: ` | a decoder stop and its reason (`activity_stopped` when the projection leaves the screen) |
| `SSL handshake complete` | a session formed |
| `AutomationReceiver: ` and `AutomationMarker: ` | a verb landed, a marker |
| `FATAL EXCEPTION` | a crash (framework line) |
| phone: `Critical error` | Android Auto rejected something |

`fsum A B` below is the sum of `fed=` over the `Throughput over` lines between markers A and B:
`hwin A B | grep -aF 'Throughput over' | grep -aoE 'fed=[0-9]+' | cut -d= -f2 | paste -sd+ | bc`.

## 7. Runs

Order: P, W1, W2, W3, H1, H2, H3, S1, Stage O, X. Every run: `th_gate` first and `th_report $RUN` in
its section. Write the results file after every run.

**A session run (W2, W3, H3) is valid only when** its `$RUN.summary` reads `wrapped=no`, `ssl=0` and
`live=` 1 or more, and `MATCH! Starting AapService` is 0 in `hwin $RUN-start $RUN-end`. If `ssl=1`,
reboot D-POCO (P step 4) and run the run once more. A second `ssl=1` makes it UNTESTABLE.

**Stop rule:** if W1 reads `vt=1` twice (no recommendation within 300 s on two tries), mark W1 to W3
FAIL, skip to H1, and report it as the first finding.

### P. Precheck, once (about 15 min, beside the build)

Record every output in Setup notes.

1. Run the build gate in section 1.
2. D-HU's shell must be root: `adb -s $DHU shell id -u` must print `0`. If not, stop the round.
3. D-HU settings, owner and panel:

   **p3.sh**
   ```bash
   adb -s $DHU shell am force-stop $PKG; sleep 0.3
   adb -s $DHU shell cat $F > $OUT/backup-D-HU.xml; sleep 0.3
   adb -s $DHU shell cat $PH > $OUT/backup-D-HU-ph.xml 2>/dev/null; sleep 0.3   # usually absent: C adds it
   adb -s $DHU shell stat -c '%U:%G %a' /data/data/$PKG/shared_prefs; sleep 0.3   # record, even when correct
   OWN=$(adb -s $DHU shell stat -c '%U:%G' $F | tr -d '\r'); echo "OWN=$OWN"
   adb -s $DHU shell wm size; sleep 0.3                      # the panel; R-B needs the width
   grep -aoE '(view-mode|video-codec|"resolutionId|screen-orientation|fps-limit|playback-focus-mode|use-aaudio-output)[^/]*' $OUT/backup-D-HU.xml
   ```
   If the `shared_prefs` directory is not owned by the app's user, `chown` it to `$OWN` and say so.
4. Phones. If `adb devices | grep -ac ZY22GC3BM4` prints 1, put D-MOTO in airplane mode and read its
   Bluetooth off. Then D-POCO: run `p4.sh` from `pr-1141-cover-keeps-decoder-round1-brief.md` P step 4
   unchanged. It sets `POCO_BT` and checks the bonds.
5. D-HU state: run `p5.sh` from the same brief, P step 5, unchanged.
6. D-SAM: `adb -s $SAM shell date` against the host `date`, one line each, and
   `adb -s $SAM shell run-as $PKG cat shared_prefs/settings.xml | tr -d '\r' > $OUT/backup-D-SAM.xml`.
7. Install C and check identity:

   **p7.sh**
   ```bash
   adb -s $DHU install -r -d $OUT/apk/C.apk
   adb -s $DHU shell cat $F > $OUT/post-install-D-HU.xml
   diff $OUT/backup-D-HU.xml $OUT/post-install-D-HU.xml     # record; if it differs, run put with no keys
   RUN=P; state | tee $OUT/P.state                           # commit must start 90b3d74b
   adb -s $DHU shell am force-stop $PKG
   adb -s $SAM install -r -d $OUT/apk/C.apk
   adb -s $SAM shell run-as $PKG cat shared_prefs/settings.xml | tr -d '\r' | diff $OUT/backup-D-SAM.xml -   # record
   adb -s $SAM shell am force-stop $PKG
   ```

**A failure in step 1, 2 or 7 stops the round.**

### W1. The video test on D-HU, re-run on an existing install (the point of the round)

**w1.sh**
```bash
RUN=W1; PHCAP=; rm -f $OUT/taps-W1; th_gate
put $BASE $WIZ str:video-codec=H.265 int:resolutionId=1 int:view-mode=2 int:screen-orientation=0 int:fps-limit=60
rd 'video-codec|resolutionId|view-mode|screen-orientation|onboarding-version|has-completed|connection-modes' | tee $OUT/W1.seed
keys $DHU before
cap_start; mark W1-start
wizard $DHU; echo "wizard=$?" | tee -a $OUT/W1.summary
mark W1-end; adb -s $DHU shell am force-stop $PKG; sleep 1
keys $DHU after; reco
cap_stop; th_report W1
hwin W1-wiz W1-applied | grep -aF 'VideoPreflight: backend=' | tee $OUT/W1.backends
```

If `wizard` returns non-zero (a target did not resolve), the run is void: say which tag, and run W1
once more from the top. A second void makes W1 UNTESTABLE.

Map the recommendation to keys: codec `H.265` / `H.264` is `video-codec` as written; the resolution
name maps by the table in section 4; `viewMode` SURFACE / TEXTURE / GLES is 0 / 1 / 2.

**PASS when all hold:**

1. `vt=0`, and `n "$(hwin W1-wiz W1-applied)" 'VideoPreflight: backend='` is 1 or more.
2. At least one `VideoPreflight: backend=` line reads `failure=NONE`.
3. `apply=0` (the box was ticked when the result showed).
4. In `W1-after.keys`: `video-codec`, `resolutionId` and `view-mode` each equal the mapped
   recommendation in `W1.reco`.
5. `FATAL EXCEPTION` is 0 in `hwin W1-start W1-end`.

**R-A, graded separately:** `screen-orientation` in `W1-after.keys` against `W1-before.keys` (seeded 0).
FAIL (finding confirmed) when it changed. Say what it read.

**R-B, graded separately, only if the `wm size` width is 1920 or less:** if any backend line for an
H.265 clip (`clip=` names it) reads `failure=NONE` and `W1-after.keys` has `video-codec` H.264, R-B is
FAIL (finding confirmed). If no H.265 clip ran, R-B is INCONCLUSIVE. Above 1920, R-B is INCONCLUSIVE.

**Record:** every `VideoPreflight: backend=` line verbatim, `W1.reco`, the before and after keys,
the seconds from the `W1-wiz` marker to the recommended line, and which keys the recommendation left at
their seed (those keys prove nothing about the apply). Attach `ui/W1-result.png`.

### W2. A session on W1's settings, and its health record

**w2.sh**
```bash
RUN=W2; PHCAP=1; th_gate
putcur int:onboarding-version=2; ph_clear
rd 'video-codec|resolutionId|view-mode|onboarding-version' | tee $OUT/W2.keys
cap_start; mark W2-start
adb -s $DHU shell am start -n $MAIN >/dev/null; sleep 15
session
sleep 90; mark W2-hold
endrun
RUN=W2; ph_read end
```

**PASS when all hold (the run must be valid):**

1. `Media Sink Setup Request: ` with `on channel VIDEO` in `hwin W2-cmd W2-live`: `7` when `W2.keys`
   has `video-codec` H.265, `3` when H.264.
2. `Projection backend: viewMode=` names the view mode `W2.keys` holds.
3. `[RES_CAP] resolutionId=` carries the id `W2.keys` holds. If a cap line on the same line says
   `linkCapped=true`, record it; the announced id may then be lower and condition 3 is met by the
   stored id alone.
4. `rend "$(hwin W2-live W2-hold)"` is 3 or more.
5. `ph_read end` prints `count=1` and one `session` line whose second field is the stored codec and
   third field the stored view mode name.
6. In `pwin W2-cmd W2-hold` on the phone: `Critical error` is 0.

Condition 5 is INCONCLUSIVE, not FAIL, when `fsum W2-live W2-x` is below 150. Record the fed sum and
the stored session line verbatim.

### W3. Leave the projection for the home screen, then end the session (R-C)

**w3.sh**
```bash
RUN=W3; PHCAP=1; th_gate
putcur int:onboarding-version=2                       # the health file keeps W2's record
RUN=W3; ph_read start
cap_start; mark W3-start
adb -s $DHU shell am start -n $MAIN >/dev/null; sleep 15
session
sleep 60; mark W3-home; b=$(lines)
adb -s $DHU shell input keyevent 3; sleep 0.3
waitsince "$b" 10 'Decoder stopped: '; echo "stop=$?" | tee -a $OUT/W3.summary
sleep 20
endrun
RUN=W3; ph_read end
```

**PASS when** the run is valid, `fsum W3-live W3-home` is 150 or more, and `count=` in `W3-ph-end.txt`
is one more than in `W3-ph-start.txt`.

**FAIL (R-C confirmed) when** the same holds for the fed sum and `count=` did not move.
INCONCLUSIVE when the fed sum before HOME is below 150, or `stop=1` (no decoder stop to test).
Record the `Decoder stopped: ` line verbatim, and `fsum W3-home W3-x`.

### H1. A codec suggestion, Apply, and the next launch (R-D)

Two seeded sessions, each with a codec fallback and a renderer recovery. With `video-codec` H.265 the
first suggestion is H.264. After Apply, the same two sessions also qualify for the SurfaceView
suggestion, since `view-mode` is TEXTURE.

**h1.sh**
```bash
RUN=H1; PHCAP=; rm -f $OUT/taps-H1; th_gate
put $BASE str:video-codec=H.265 int:view-mode=1 int:resolutionId=0 int:fps-limit=60 $(iclr)
T=$(($(date +%s)*1000))
ph_seed 2 "$((T-600000))|H.265|TEXTURE|0|60|1|1|0|3000|0|0|2950" "$((T-300000))|H.265|TEXTURE|0|60|1|1|0|3000|0|0|2950"
rd 'video-codec|view-mode|resolutionId' | tee $OUT/H1.keys
cap_start; mark H1-start
adb -s $DHU shell am start -n $MAIN >/dev/null; sleep 6
dump $DHU card1; python3 -I $OUT/uiq.py count $OUT/ui/H1-card1.xml projection_health_card | tee $OUT/H1.card1
tapid $DHU apply projection_health_apply; echo "tap=$?" | tee -a $OUT/H1.summary
sleep 2; mark H1-applied
adb -s $DHU shell am force-stop $PKG; sleep 1
keys $DHU after; ph_read after
adb -s $DHU shell am start -n $MAIN >/dev/null; sleep 6
dump $DHU card2; python3 -I $OUT/uiq.py count $OUT/ui/H1-card2.xml projection_health_card | tee $OUT/H1.card2
mark H1-end; adb -s $DHU shell am force-stop $PKG; cap_stop; th_report H1
```

**PASS when all hold:**

1. `H1.card1` is 1, and `ui/H1-card1.png` shows the banner at the top (record the
   `projection_health_message` text from the dump).
2. `tap=0`, and `video-codec` in `H1-after.keys` reads `H.264`.
3. `H1-ph-after.txt` reads `dismissed=CODEC_H264`.
4. `FATAL EXCEPTION` is 0 in `hwin H1-start H1-end`.

**R-D, graded separately:** `H1.card2`. 0 means the suggestion waited for a new session. 1 means a
second suggestion appeared on the same stored sessions: R-D FAIL (finding confirmed). Record its
message text from `ui/H1-card2.xml`.

### H2. A frame-rate suggestion, Not now, and it stays hidden

**h2.sh**
```bash
RUN=H2; PHCAP=; rm -f $OUT/taps-H2; th_gate
put $BASE str:video-codec=H.264 int:view-mode=0 int:resolutionId=0 int:fps-limit=60 $(iclr)
T=$(($(date +%s)*1000))
ph_seed 2 "$((T-600000))|H.264|SURFACE|0|60|0|0|0|3000|0|0|2000" "$((T-300000))|H.264|SURFACE|0|60|0|0|0|3000|0|0|2000"
cap_start; mark H2-start
adb -s $DHU shell am start -n $MAIN >/dev/null; sleep 6
dump $DHU card1; python3 -I $OUT/uiq.py count $OUT/ui/H2-card1.xml projection_health_card | tee $OUT/H2.card1
tapid $DHU later projection_health_later; echo "tap=$?" | tee -a $OUT/H2.summary
sleep 2; dump $DHU gone; python3 -I $OUT/uiq.py count $OUT/ui/H2-gone.xml projection_health_card | tee $OUT/H2.gone
adb -s $DHU shell am force-stop $PKG; sleep 1
keys $DHU after; ph_read after
adb -s $DHU shell am start -n $MAIN >/dev/null; sleep 6
dump $DHU card2; python3 -I $OUT/uiq.py count $OUT/ui/H2-card2.xml projection_health_card | tee $OUT/H2.card2
mark H2-end; adb -s $DHU shell am force-stop $PKG; cap_stop; th_report H2
```

**PASS when all hold:** `H2.card1` is 1; `tap=0`; `H2.gone` is 0; `H2-ph-after.txt` reads
`dismissed=FPS_30`; `fps-limit` in `H2-after.keys` still reads 60; `H2.card2` is 0;
`FATAL EXCEPTION` is 0 in `hwin H2-start H2-end`. Record the message text from `ui/H2-card1.xml`.

### H3. No suggestion while a session is live

**h3.sh**
```bash
RUN=H3; PHCAP=1; th_gate
put $BASE str:video-codec=H.265 int:view-mode=1 int:resolutionId=0 int:fps-limit=60 $(iclr)
T=$(($(date +%s)*1000))
ph_seed 2 "$((T-600000))|H.265|TEXTURE|0|60|1|0|0|3000|0|0|2950" "$((T-300000))|H.265|TEXTURE|0|60|1|0|0|3000|0|0|2950"
cap_start; mark H3-start
adb -s $DHU shell am start -n $MAIN >/dev/null; sleep 6
dump $DHU pre; python3 -I $OUT/uiq.py count $OUT/ui/H3-pre.xml projection_health_card | tee $OUT/H3.pre
session
sleep 20; mark H3-over
adb -s $DHU shell am start -n $MAIN >/dev/null; sleep 4
adb -s $DHU shell dumpsys window | grep -a -m1 mCurrentFocus | tee $OUT/H3.focus; sleep 0.3
dump $DHU live; python3 -I $OUT/uiq.py count $OUT/ui/H3-live.xml projection_health_card | tee $OUT/H3.live
mark H3-x; send ACTION_DISCONNECT > $OUT/H3-x.reply; sleep 8
adb -s $DHU shell am start -n $MAIN >/dev/null; sleep 4
dump $DHU after; python3 -I $OUT/uiq.py count $OUT/ui/H3-after.xml projection_health_card | tee $OUT/H3.after
mark H3-end; clean_hu; cap_stop; th_report H3; PHCAP=
```

**PASS when** the run is valid, `H3.pre` is 1, `H3.focus` names `MainActivity`, `H3.live` is 0 and
`H3.after` is 1. If `H3.focus` does not name `MainActivity`, the live check is void: say what it named.
In `pwin H3-cmd H3-x` on the phone: `Critical error` is 0.

### S1. The video test on D-SAM (Android 4.4)

**s1.sh**
```bash
RUN=S1; PHCAP=; rm -f $OUT/taps-S1; th_gate
adb -s $SAM shell am force-stop $PKG; sleep 0.3
cp $OUT/backup-D-SAM.xml $OUT/w-sam.xml
python3 -I $OUT/sx.py $OUT/w-sam.xml int:log-level=2 int:screen-orientation=2 $WIZ str:video-codec=H.264 int:resolutionId=1 int:view-mode=2
adb -s $SAM push $OUT/w-sam.xml /data/local/tmp/ohu-w.xml >/dev/null; sleep 0.3
adb -s $SAM shell run-as $PKG cp /data/local/tmp/ohu-w.xml shared_prefs/settings.xml; sleep 0.3
keys $SAM before
HU_SAVE=$DHU; DHU=$SAM                 # cap_start, mark, send, lines and waitsince now read D-SAM
cap_start; mark S1-start
wizard $SAM; echo "wizard=$?" | tee -a $OUT/S1.summary
mark S1-end; adb -s $SAM shell am force-stop $PKG; sleep 1
keys $SAM after; reco
cap_stop; DHU=$HU_SAVE; th_report S1
grep -aF 'VideoPreflight: backend=' $OUT/S1.hu.txt | tee $OUT/S1.backends
```

`send` on D-SAM: `am` there rejects `-p` only; `cover_lib.sh`'s `send` uses `-n` and `-f`, which work.
If `mark` prints an error on D-SAM, drop the markers, grade S1 on the whole capture, and say so.

**PASS when all hold:** `vt=0`; at least one `VideoPreflight: backend=` line reads `failure=NONE` on
`backend=TEXTURE` or `backend=GLES`; `apply=0`; the three keys in `S1-after.keys` equal the mapped
recommendation; `FATAL EXCEPTION` is 0 in `S1.hu.txt`.

Record every backend line verbatim. A `backend=SURFACE` line with `samples=0` is the expected
"picture unverified" on Android 4.4, not a FAIL. Attach `ui/S1-result.png`.

### Stage O. The operator (about 25 min, last)

The operator works the screen; the host runs the captures and reads the keys. Each run starts with
the host's setup block and ends with the host's read-back. The host says each step aloud and marks it
with `mark` (on D-HU) the moment the operator confirms.

**O1. Untick keeps the settings (D-HU, the W1 control).** Host: `RUN=O1; PHCAP=; th_gate; put $BASE
$WIZ str:video-codec=H.265 int:resolutionId=1 int:view-mode=2 int:screen-orientation=0; keys $DHU before;
cap_start; mark O1-start; adb -s $DHU shell am start -n $MAIN`. Operator: Next three times, wait for the
result, **untick "apply"**, Next twice. Host: `mark O1-end; adb -s $DHU shell am force-stop $PKG; keys $DHU
after; cap_stop`. **PASS when** `video-codec`, `resolutionId`, `view-mode` and `screen-orientation` in
`O1-after.keys` equal `O1-before.keys`.

**O2. Sound check answers on D-HU (no speaker; the settings are the evidence).** Host: `RUN=O2; put
$BASE $WIZ int:playback-focus-mode=0 bool:use-aaudio-output=true; keys $DHU before; cap_start; mark
O2-start; adb -s $DHU shell am start -n $MAIN`. Operator: Next to the Sound step (7 Next presses; on the
Video step wait for the result and leave the box as it is), then: press the play button; host marks
`O2-tone1`; answer **Crackles**; press play again if the screen asks; host marks `O2-tone2`; answer
**Sounds fine**; on the radio question choose **Always pause**. Then stop. Host: `mark O2-end;
adb -s $DHU shell am force-stop $PKG; keys $DHU after; cap_stop`.
**PASS when** `n "$(hwin O2-start O2-end)" 'AudioOutputTester: backend='` is 2 or more; the first
names `backend=AAUDIO` (D-HU is API 34, so AAudio is allowed) and the one after `O2-tone2` names
`backend=AUDIO_TRACK`; `use-aaudio-output` reads false and `playback-focus-mode` reads 1 in
`O2-after.keys`; `FATAL EXCEPTION` is 0.

**O3. "Nothing heard" and the channel list (D-HU).** Host: as O2 with `RUN=O3` and `put $BASE $WIZ`.
Operator: to the Sound step, play, answer **No sound**, press **Use** on the first channel row shown.
Host marks each step, then `keys $DHU after`. **PASS when** `media-audio-stream`,
`guidance-audio-stream` and `system-audio-stream` in `O3-after.keys` all hold the same value, and
`separate-audio-streams` reads false. Record which row the operator pressed and the stream value.

**O4. By ear on D-SAM (Android 4.4).** Host: D-SAM setup as in S1 (its own backup plus `$WIZ`), start
its capture, launch. Operator: to the Sound step, play, and answer what they actually heard. If
nothing: **No sound**, then play each channel in turn and press Use on the first they hear. Then
the radio question: **Let the app decide**. Host: `keys $SAM after`. **PASS when** the app did not crash
(`FATAL EXCEPTION` is 0), `AudioOutputTester: backend=AUDIO_TRACK` printed (no AAudio below API 28),
and the operator heard the tone on the first play or on one of the channels. Record what they heard on
each play, and `playback-focus-mode` (0 expected).

**Radio choice sticks (O2's tail, optional, 5 min).** If time allows: after O2, `putcur
int:onboarding-version=2`, run one session as in W2 (`RUN=O2s`), end it, and read `playback-focus-mode`
again. PASS when it still reads 1.

### X. Restore

**x.sh**
```bash
RUN=X; adb -s $DHU shell am force-stop $PKG; sleep 0.3
put                                    # no keys: D-HU's round-start backup
if [ -s $OUT/backup-D-HU-ph.xml ]; then adb -s $DHU push $OUT/backup-D-HU-ph.xml /data/local/tmp/ohu-ph.xml >/dev/null
  adb -s $DHU shell "cp /data/local/tmp/ohu-ph.xml $PH && chown $OWN $PH && chmod 660 $PH"; else ph_clear; fi
adb -s $DHU shell cat $F > $OUT/restored-D-HU.xml; diff $OUT/backup-D-HU.xml $OUT/restored-D-HU.xml   # must be empty
adb -s $SAM shell am force-stop $PKG; sleep 0.3
adb -s $SAM push $OUT/backup-D-SAM.xml /data/local/tmp/ohu-w.xml >/dev/null; sleep 0.3
adb -s $SAM shell run-as $PKG cp /data/local/tmp/ohu-w.xml shared_prefs/settings.xml; sleep 0.3
adb -s $SAM shell run-as $PKG rm -f shared_prefs/projection-health.xml
```

C stays installed on both units. If D-MOTO went into airplane mode in P, take it out and read its
radios back on. Zip `$OUT` (dumps and screenshots included) as the round's asset.

## 8. Do not re-run

Nothing; this is the thread's first round.

## 9. Report back

1. W1: the recommendation line, the backend lines, and whether the three keys matched it. Then R-A
   (orientation) and R-B (H.265 replaced) as confirmed or not.
2. W2 and W3: the health record count after each, with the fed sums. W3 decides R-C.
3. H1's second launch (R-D), and the pass or fail of H2, H3, S1 and Stage O, one line each.
