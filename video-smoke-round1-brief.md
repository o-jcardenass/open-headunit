# video-smoke-auto: three projection backends on three API levels, run at will on any commit

Not a queued round. `rig-lib/video_smoke.py` runs it on demand: it fetches the ref it is given from the remote the ref
names, writes that commit into this brief, builds it, runs the session with no model and no hand step, and prints one
line per run. Evidence goes to `rig-data/rounds/video-smoke-auto-round<yyyymmddHHMM>/`. Results are not committed; the
runner's summary is the report. A queued round can also use a copy of this brief rendered with
`video_smoke.py render --sha SHA --out <topic>-round<N>-brief.md`.

Each unit runs TextureView, SurfaceView and GLES, two sessions each, and its TextureView sessions are the reference
for its other two modes. The units are the three that carry video on this rig (a phone as head unit does not), at
API 17, 19 and 34, so a fault on either side of a `Build.VERSION` gate in the projection path shows up here.

Units: D-SAM (API 19, Native AA) with D-POCO; D-HU (API 34, Native AA) with D-MOTO; D-HP (API 17, USB) with the
dongle and D-MOTO behind it. D-POCO keeps its D-SAM bond and D-MOTO keeps its D-HU bond, so no run needs a pair or a
forget. Only radios change between stages, and `rig_requires.py check --fix` switches them.
Hand steps: none. Injected input: none.
Time: build 5 to 20 min, prep 12, D-SAM runs 18, D-HU runs 9, D-HP runs 12, close 6, plus thermal waits.
**About 60 min with a cached build, 75 with a cold one.**
Queue line units: D-SAM, D-HU, D-HP, D-POCO, D-MOTO. It uses every unit, so no other round can pair with it (§3b).

**First use.** `fork/fix/surfaceview-crop` at `ff21d2716` sizes a SurfaceView by its layout below API 24
(`SurfaceViewFitPolicy`). `surfaceview-crop` rounds 1 and 2 proved it on D-SAM only. This smoke adds the other side
of the gate (D-HU, API 34), a second pre-N unit (D-HP, API 17), and GLES and TextureView on all three as a guard.

```rig-requires
lib 0f6b5a7a1
candidate V ff21d27165c125e4d3d93e51744e335eed4a9588
unit D-SAM role=headunit wifi=on bt=on battery=50
unit D-HU role=headunit wifi=on bt=on
unit D-HP@prep,PT,PS,PG,close role=headunit
unit D-POCO@ST,SS,SG role=phone wifi=on bt=on bond=D-SAM nobond=D-HU aa=D-SAM battery=30
unit D-POCO@MT,MS,MG wifi=off bt=off
unit D-POCO@PT,PS,PG wifi=off bt=off
unit D-MOTO@ST,SS,SG wifi=off bt=off
unit D-MOTO@MT,MS,MG role=phone wifi=on bt=on bond=D-HU battery=30
unit D-MOTO@PT,PS,PG role=phone wifi=on bt=on battery=30
hardware the USB dongle on D-HP's powered OTG Y-cable, D-HP on wireless adb (video_smoke.py finds or sets it up)
run prep cap=2700 script=prep.sh grade=grade-prep.sh
run ST cap=1500 script=view.sh grade=grade-view.sh
run SS cap=1500 script=view.sh grade=grade-view.sh point=yes
run SG cap=1500 script=view.sh grade=grade-view.sh
run MT cap=1200 script=view.sh grade=grade-view.sh
run MS cap=1200 script=view.sh grade=grade-view.sh point=yes
run MG cap=1200 script=view.sh grade=grade-view.sh
run PT cap=1200 script=view.sh grade=grade-view.sh
run PS cap=1200 script=view.sh grade=grade-view.sh point=yes
run PG cap=1200 script=view.sh grade=grade-view.sh
run close cap=1200 script=close.sh always=yes
stop prep FAIL,UNTESTABLE skip=all
stop ST UNTESTABLE skip=SS,SG
stop MT UNTESTABLE skip=MS,MG
stop PT UNTESTABLE skip=PS,PG
```

| Run | Unit, route | `view-mode` | What it decides | Min |
|---|---|---|---|---|
| prep | D-SAM, D-HU, D-HP | | backups, the prior APKs, installs, identity (md5, `ACTION_QUERY_STATE` commit) | 12 |
| ST | D-SAM, Native AA with D-POCO | 1 TEXTURE | D-SAM's reference picture and frame rate | 6 |
| **SS** | D-SAM | 0 SURFACE | **a pre-N SurfaceView fills what TextureView fills (API 19)** | 6 |
| SG | D-SAM | 2 GLES | GLES fills what TextureView fills (API 19) | 6 |
| MT | D-HU, Native AA with D-MOTO | 1 TEXTURE | D-HU's reference | 3 |
| **MS** | D-HU | 0 SURFACE | **SurfaceView on the N-and-up side of the gate is unchanged (API 34)** | 3 |
| MG | D-HU | 2 GLES | GLES on API 34 | 3 |
| PT | D-HP, USB dongle with D-MOTO | 1 TEXTURE | D-HP's reference | 4 |
| **PS** | D-HP | 0 SURFACE | **a pre-N SurfaceView fills what TextureView fills (API 17)** | 4 |
| PG | D-HP | 2 GLES | GLES on API 17 | 4 |
| close | D-SAM, D-HU, D-HP | | D-SAM's network, the prior APKs on D-SAM and D-HU, every `settings.xml`, the stay-awake settings | 6 |

The stop rules are counts of one: a TextureView run that forms no session in two attempts per session stops its unit's
other two runs, because they share its link. A TextureView FAIL does not stop them; they then grade INCONCLUSIVE at
best (section 7).

## 1. Build and baseline

| Arm | Ref | Commit |
|---|---|---|
| V | whatever `video_smoke.py run --ref` names (first use: `fork/fix/surfaceview-crop`) | `ff21d27165c125e4d3d93e51744e335eed4a9588` |

`video_smoke.py` builds V itself with `$RIG_TOOLKIT/wt-new.sh video-smoke-auto candidate <sha>`,
`$RIG_TOOLKIT/rig_thermal.sh wait 70`, and `HUR_DIR=<wt> MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh`, then copies
the APK to `$EV/apk/V.apk` and removes the worktree. `--apk FILE` skips the build. A by-hand copy of this brief builds
the same way. For the first use, the dev host ran the JVM suite at `ff21d2716`: 4390 tests, 0 failures. No rig unit
tests.

The `lib` line pins `rig-lib` at `0f6b5a7a1`, which holds every `rl_` helper the scripts call. `video_smoke.py` runs
from the checkout, not from the pin, so committing it needs no re-pin. Re-pin only when a script here needs a newer
`rl_` helper: change the `lib` line in `rig-lib/video_smoke_brief.md` to `git log -1 --format=%h -- rig-lib`.

## 2. What this is and why

The app has three projection backends, chosen by `view-mode` in `AapProjectionActivity.setupProjectionView`
(`Using TextureView`, `Using GlProjectionView`, `Using SurfaceView`). Each one sizes and places the picture on its own
path (`ProjectionViewScaler`, `SurfaceViewFitPolicy` below API 24, `GlProjectionView`), and two of those paths branch on
the API level. A change to one path has broken a backend on one API level before and shown nothing on the others:
on `main` a pre-N SurfaceView lit 396 permille of D-SAM's screen against TextureView's 957 (`dsam-surfaceview-crop`
round 1), and `surfaceview-crop` rounds 1 and 2 fixed it on D-SAM only.

So every run asks the same four questions of one backend on one unit, against that unit's own TextureView sessions:
did the activity build the backend the settings asked for, did the decoder render, does the picture fill what
TextureView fills, and did nothing crash or end the session.

**What a PASS would look like if the backend drew nothing.** A backend that never draws still forms a session, and the
decoder can still render into a surface nobody shows. The screenshot then reads black (`rows=none`, box 0), so
condition 3 fails. A backend that falls back to SurfaceView prints `Falling back to SurfaceView for this session`, so
condition 1 fails. Neither state can pass.

## 3. What is different from main-smoke-auto

- **The picture is graded from a raw screenshot**, as in `surfaceview-crop` rounds 1 and 2: `screencap` without `-p`,
  pulled to the host, and `crop_tools.py measure` gives the lit box in buffer pixels. Every comparison is between
  sessions of one unit, so a portrait buffer (D-SAM, D-HP) needs no turning.
- **TextureView is each unit's reference.** If a TextureView run fails, its unit's other runs cannot grade the picture
  or the frame rate, and they grade INCONCLUSIVE instead of PASS (a FAIL of their own still counts).
- **Admission is the `surfaceview-crop` round 2 S2 rule.** A session counts when it formed SSL and its settings
  read-back holds the run's `view-mode`. Its picture is compared only when the last `Throughput over` line before the
  screenshot rendered frames. A session that formed and did not render is not left out quietly: condition 2 fails.
- **The frame-rate floor is loose on purpose.** Android Auto idles a static screen, and nothing moves on the phone in
  this smoke, so an absolute fps would grade the phone's idle state. A backend's median rendered fps must reach a
  quarter of its unit's TextureView median, and only when that median is 8 fps or more. A broken render path runs at
  0 to 2 fps; an idle one varies by a factor of about two between sessions.
- **D-HP is on wireless adb** with the dongle on its powered Y-cable (`rig-quirks/units/D-HP.md`). A raw screenshot is
  2.4 MB, about 20 s to pull. When `video_smoke.py` cannot reach D-HP, it renders the brief without PT, PS and PG.
- **D-HP keeps the candidate after the smoke**; only its settings go back. Its install is skipped when the installed
  APK already has the candidate's size; `ACTION_QUERY_STATE` then proves the commit.
- **D-SAM's station is held off before each launch** and given back in `close`, as in `surfaceview-crop` round 2.
- **The rig's audio settings are not written** on D-SAM or D-HU. They are a deliberate worst case and not this smoke's
  subject. D-HP writes `enable-audio-sink` false as `main_smoke_brief.md` M6 does.
- Read `rig-quirks/topics/tooling.md`, `topics/projection.md`, `units/D-SAM.md`, `units/D-HP.md`,
  `units/D-SAM-and-D-HP.md`, `units/D-HU.md`, `units/D-POCO.md`, `units/D-MOTO.md` and `units/D-DONGLE.md`.

## 4. Settings keys

Written by `write_keys` with the app stopped, and read back into `$EV/<run>-<k>-settings.xml` for each session.

| Key | Type | All units | D-SAM | D-HU | D-HP |
|---|---|---|---|---|---|
| `onboarding-version` | int | 2 | | | |
| `allow-external-configuration` | boolean | true | | | |
| `log-level` | int | 2 (INFO) | | | |
| `kill-on-disconnect`, `enable-floating-button`, `pending-renderer-confirm`, `narrow-band-profile-cap`, `force-software-decoding`, `forced_scale`, `hud_mirroring` | boolean | false | | | |
| `video-profile-starvation-cap` | | delete | | | |
| `fullscreen-mode` | int | 1 IMMERSIVE | | | |
| `video-fit-mode` | int | 0 FILL | | | |
| `inset-left`, `inset-top`, `inset-right`, `inset-bottom` | int | 0 | | | |
| `screen-orientation` | int | 2 LANDSCAPE | | | |
| `view-mode` | int | the run's: 1 (?T), 0 (?S), 2 (?G) | | | |
| `resolutionId` | int | | 3 | 2 | 2 |
| `dpi-pixel-density` | int | | 175 | | |
| `wifi-connection-mode` | int | | 3 | 3 | 0 |
| `connection-modes` | set | | `wifi` | `wifi` | `usb` |
| `native-ap-transport` | int | | 0 | 0 | |
| `external-bt-zbt-transport` | boolean | | false | false | |
| `native-poke-all-paired` | boolean | | true | false | false |
| `native-poke-bt-macs` | set | | delete | D-MOTO's address | |
| `native-preferred-device-mac`, `last-connected-native-mac` | string | | delete | D-MOTO's address | |
| `native-mac` | | | delete | delete | |
| `native-driver-selection-mode` | int | | | 0 | 0 |
| `wifi-direct-stable-identity` | boolean | | true | | |
| `wifi-direct-rotation-pending` | boolean | | false | | |
| `enable-audio-sink` | boolean | | true | | false |
| `video-codec` | string | | | | `H.264` |
| `auto-start-on-usb`, `use-libusb` | boolean | | | | false |
| `auto-connect-last-session`, `reopen-on-reconnection` | boolean | | | | true |
| `station-stand-down-network-id` | | | delete before each launch; written back in `close` | | |

The D-SAM keys are `surfaceview-crop` round 2's, the D-HU keys are `main_smoke_brief.md` M1's without its audio keys,
and the D-HP keys are M6's.

## 5. Verbs and device commands

- `send ACTION_QUERY_STATE`: the commit the installed build reports (prep).
- `send ACTION_DISCONNECT`, then `am start -a android.intent.action.VIEW -d headunit://exit`, then `am force-stop`:
  every exit (`appexit`). The force-stop follows a clean exit and is never the exit itself.
- `adb shell log -t AutomationMarker <label>` on the head unit and on the phone: every marker (`mark`).
- `am start -n .../MainActivity`: every launch. On mode 3 the launch arms the stack, so no run sends
  `ACTION_START_WIRELESS_SCAN`.
- `rl_radio <phone> wifi|bt on|off`: the phone off the link before a Native launch and back on 2 s after it (§4).
- `screencap /sdcard/ohu-vs.raw`, `pull`, `rm`: the screenshot.
- `svc power stayon true` on the head unit for the runs; `close` writes the saved `stay_on_while_plugged_in` back.

## 6. The deciding lines

App strings checked with `git grep -F` in `app/src/main` at `ff21d2716`: all exist. `vs_parse.py` and the grade read
them in `$EV/<run>-hu.txt` from `<run>-<k>-go` to `<run>-<k>-x`, the rendered-frames check to `<run>-<k>-shot`. Phone
lines are in `$EV/<run>-ph.txt` from `<run>-1-go` to `<run>-end`.

| Id | Line (literal part) | Level | Read |
|---|---|---|---|
| L-SSL | `SSL handshake complete` | INFO | a session formed (admission) |
| L-THR | `Throughput over` + `ms: rendered=N (Xfps)` | INFO | the decoder renders; N and X are read |
| L-USE-T / L-USE-S / L-USE-G | `Using TextureView` / `Using SurfaceView` / `Using GlProjectionView` | INFO | the backend the activity built |
| L-FALL | `Falling back to SurfaceView for this session` | WARN | a backend gave up by itself |
| L-MISM | `Surface mismatch!` | INFO | the canvas moved under a live session |
| S-CRASH | `Process: com.andrerinas.headunitrevived` | system | a `FATAL EXCEPTION` block |
| S-ANR | `ANR in com.andrerinas` | system | an ANR |
| S-MARK | `AutomationMarker: <run>-` | system / phone | the marker lines on both units |
| P-CRIT | `Critical error` | phone | Android Auto ended the session; the 5 s after each `-x` or `-r` marker left out |
| L-GIVE | `WiFi network is enabled again and should rejoin` | INFO | `close`: D-SAM's network given back |

## 7. Runs

Every view run is the same script, `view.sh`, and the same grade, `grade-view.sh`. The run id names the unit (S D-SAM,
M D-HU, P D-HP) and the backend (T TextureView, S SurfaceView, G GLES). A run opens both captures, then runs two
sessions. A session writes the keys, launches, waits for SSL (cap 150 s on D-SAM, 120 s on D-HU, 60 s on D-HP), waits
for `Throughput over` (cap 60 s), settles 10 s, marks `-shot`, takes the screenshot, marks `-x` and exits. A session
with no SSL marks `-r`, exits and tries once more.

**Conditions, the same for every view run** (graded only over sessions that were admitted, section 3):

1. `<run>.1`: the backend's own `Using` line in every session; `<run>.1b`: `Falling back to SurfaceView for this
   session` 0.
2. `<run>.2`: the last `Throughput over` line before `-shot` rendered frames, in every session. `<run>.2b` (not on
   TextureView): the median rendered fps is 25 percent or more of the unit's TextureView median, when that median is
   8 fps or more; a report line otherwise.
3. On TextureView: `<run>.3`, every lit box is 600 permille or more of the screen; `<run>.3b`, every box edge within
   8 px of the two sessions' median. On SurfaceView and GLES: `<run>.3`, every lit box is 750 permille or more of the
   TextureView median box; `<run>.3b`, every box edge within 8 px of the TextureView median edges.
4. `<run>.4`: `Surface mismatch!` 0; `<run>.4b`: no `FATAL EXCEPTION` or ANR for the app; `<run>.4c`: phone
   `Critical error` 0 outside the 5 s after each exit; `<run>.4d`: the phone capture holds the same `<run>-` markers
   as the head unit's, which proves the phone window is real.

- **PASS** when every condition holds.
- **FAIL** when one does not. Report the run's `<run>-sessions.txt`, its raw frames and the failing COND lines.
- **INCONCLUSIVE** when nothing failed but the unit's TextureView run did not pass, so conditions 3 and 2b had no
  reference.
- **UNTESTABLE** when no session formed SSL in two attempts per session, the host stayed hot, or (D-HP) the battery read
  below 40% or the host saw no USB device.

### prep. Backups, installs and identity on D-SAM, D-HU and D-HP

**Decision:** PASS lets the view runs grade the candidate. FAIL or UNTESTABLE stops the smoke: an install or identity
fault, so fix it and run again.

### ST. D-SAM, TextureView

**Decision:** PASS gives SS and SG their reference. FAIL means TextureView itself regressed on API 19: look at
`ProjectionViewScaler.updateScale` and `TextureProjectionView` first, and SS and SG grade INCONCLUSIVE.

### SS. D-SAM, SurfaceView (a point of the smoke)

**Decision:** PASS means a pre-N SurfaceView still fills the screen on API 19, so the layout fit (`SurfaceViewFitPolicy`)
holds. FAIL on condition 3 points at the fit, as `main` measured (396 permille); FAIL on 1 or 2 points at
`setupProjectionView` or the surface callbacks.

### SG. D-SAM, GLES

**Decision:** PASS clears GLES on API 19. FAIL points at `GlProjectionView` on a GLES 2 driver from 2014, not at the
SurfaceView fit, which GLES does not use.

### MT. D-HU, TextureView

**Decision:** PASS gives MS and MG their reference. FAIL means TextureView regressed on API 34, the backend most units
run, so it blocks the commit whatever the other runs say.

### MS. D-HU, SurfaceView (a point of the smoke)

**Decision:** PASS means the API 24 and up side of the gate still scales a SurfaceView by the view, as before the fit.
FAIL means the `sdkInt >= 24` gate leaks: re-check `SurfaceViewFitPolicy`'s gate before anything else.

### MG. D-HU, GLES

**Decision:** PASS clears GLES on API 34. FAIL points at `GlProjectionView` on a modern driver; read its
`GlProjectionView: onSurfaceChanged` and first-frame lines in the window.

### PT. D-HP, TextureView

**Decision:** PASS gives PS and PG their reference. FAIL means TextureView regressed on API 17 or on the USB route;
compare with MT and ST to tell which.

### PS. D-HP, SurfaceView (a point of the smoke)

**Decision:** PASS means the layout fit holds on a second pre-N unit, at API 17 and a 1024x600 panel. FAIL on condition 3
with SS passing means the fit depends on D-SAM's panel or API level: re-plan the fit from PS's raw frames.

### PG. D-HP, GLES

**Decision:** PASS clears GLES on API 17. FAIL points at `GlProjectionView` on the oldest GLES driver on the rig.

### close. Network, APKs and settings back (always runs)

**Decision:** PASS leaves every unit at its baseline for the next round. FAIL means the next round on that unit
restores it by hand first; `close` changes no other verdict.

## 8. Do not re-run

- **The margin race in Normal screen mode** (`surfaceview-crop` round 2): not this smoke. It runs Immersive only.
- **Insets, rotation and the bar layers** (`surfaceview-crop` round 1 S4, S5, S6): the fit's own edge cases, settled on
  `ff21d2716`. This smoke grades the plain fill on every backend instead.
- **Audio, Headunit Server, Self Mode and the dongle's AOA cycle**: `main_smoke_brief.md` covers them.

## 9. Report back

1. The summary line per run, and the worst verdict.
2. For each unit: the TextureView median box and fps, and each other backend's worst box ratio, worst edge offset and
   median fps (the `<run>-sessions.txt` files).
3. Every FAIL's COND lines and its raw frames.

## Scripts

**lib.sh**

```bash
. "$RIG_TOOLKIT/rig_devices.sh"
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
MAIN=$PKG/com.andrerinas.openheadunit.main.MainActivity
APK=$EV/apk/V.apk; SCR=$EV/scripts
DHU=$D_HU; SAM=$D_SAM; POCO=$D_POCO; MOTO=$D_MOTO; HPU=$D_HP
HP_W=$(cat "$EV/hp.addr" 2>/dev/null); HP_W=${HP_W:-$HPU}
POCO_BT=$(cat "$EV/poco.btaddr" 2>/dev/null); POCO_BT=${POCO_BT:-DC:B7:2E:5E:4E:59}
MOTO_BT=$(cat "$EV/moto.btaddr" 2>/dev/null); MOTO_BT=${MOTO_BT:-A0:46:5A:97:E4:95}
CAND_SHA=ff21d2716   # video_smoke.py writes the commit under test here
unit_of() { case $1 in "$DHU") echo hu ;; "$SAM") echo sam ;; "$HPU"|"$HP_W") echo hp ;; *) echo other ;; esac; }
# The run id names the unit (S D-SAM, M D-HU, P D-HP) and the backend (T TEXTURE 1, S SURFACE 0, G GLES 2).
case ${RUN:-} in
  S[TSG]) U=sam; HU=$SAM; PH=$POCO; ROUTE=native; SSL_CAP=150 ;;
  P[TSG]) U=hp; HU=$HP_W; PH=$MOTO; ROUTE=dongle; SSL_CAP=60 ;;
  *) U=hu; HU=$DHU; PH=$MOTO; ROUTE=native; SSL_CAP=120 ;;
esac
case ${RUN:-} in ?S) VIEW=0 ;; ?G) VIEW=2 ;; *) VIEW=1 ;; esac

send() { local a=$1; shift; adb -s "$HU" shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; }
mark() { adb -s "$HU" shell log -t AutomationMarker "$1"; adb -s "$PH" shell log -t AutomationMarker "$1"
  echo "$1 $(date +%T)" >> "$EV/marks.txt"; }
launch() { adb -s "$HU" shell am start -n $MAIN >/dev/null; }
running() { local p; p=$(adb -s "$1" shell pidof $PKG 2>/dev/null | tr -d '\r')
  case "$p" in *[0-9]*) return 0 ;; esac; adb -s "$1" shell ps 2>/dev/null | grep -aqF "$PKG"; }
# appexit: Disconnect and headunit://exit only while the app runs (an exit on a stopped app starts the service)
appexit() { if running "$HU"; then send ACTION_DISCONNECT >/dev/null; rl_sleep 5
  adb -s "$HU" shell am start -a android.intent.action.VIEW -d "headunit://exit" >/dev/null; rl_sleep 5; fi
  adb -s "$HU" shell am force-stop $PKG; sleep 1; }
untestable() { echo "$*" > "$EV/$RUN-untestable.txt"; echo "UNTESTABLE $*"; exit 20; }
slow_step() { rl_step "WAIT cap=$1s $2"; }   # a long step rig_run.sh must not call a stall (installs over wireless adb)
bat() { adb -s "$HU" shell dumpsys battery | grep -a -m1 level | tr -dc 0-9; }
phone_link() { rl_radio "$PH" wifi "$1"; rl_radio "$PH" bt "$1"; }   # phone_link on|off
wake() { if adb -s "$HU" shell dumpsys power | grep -aqE 'mScreenOn=false|mWakefulness=Asleep|mWakefulness=Dozing'; then adb -s "$HU" shell input keyevent 26; fi; }   # system

# settings
COMMON="scalar:int:onboarding-version:2 scalar:boolean:allow-external-configuration:true scalar:int:log-level:2 scalar:boolean:kill-on-disconnect:false scalar:boolean:enable-floating-button:false scalar:boolean:pending-renderer-confirm:false del:video-profile-starvation-cap scalar:boolean:narrow-band-profile-cap:false scalar:boolean:force-software-decoding:false"
VIDEO="scalar:int:fullscreen-mode:1 scalar:int:video-fit-mode:0 scalar:boolean:forced_scale:false scalar:boolean:hud_mirroring:false scalar:int:inset-left:0 scalar:int:inset-top:0 scalar:int:inset-right:0 scalar:int:inset-bottom:0 scalar:int:screen-orientation:2"
SAMK="scalar:boolean:enable-audio-sink:true set:connection-modes:wifi scalar:int:wifi-connection-mode:3 scalar:int:native-ap-transport:0 scalar:boolean:native-poke-all-paired:true scalar:boolean:external-bt-zbt-transport:false scalar:boolean:wifi-direct-stable-identity:true scalar:boolean:wifi-direct-rotation-pending:false scalar:int:resolutionId:3 scalar:int:dpi-pixel-density:175 del:native-poke-bt-macs del:native-mac del:last-connected-native-mac del:native-preferred-device-mac"
HUK="set:connection-modes:wifi scalar:int:wifi-connection-mode:3 scalar:int:native-ap-transport:0 scalar:boolean:external-bt-zbt-transport:false scalar:int:native-driver-selection-mode:0 scalar:boolean:native-poke-all-paired:false set:native-poke-bt-macs:$MOTO_BT scalar:string:native-preferred-device-mac:$MOTO_BT scalar:string:last-connected-native-mac:$MOTO_BT del:native-mac scalar:int:resolutionId:2"
HPK="scalar:int:wifi-connection-mode:0 set:connection-modes:usb scalar:boolean:auto-start-on-usb:false scalar:boolean:auto-connect-last-session:true scalar:boolean:reopen-on-reconnection:true scalar:boolean:use-libusb:false scalar:boolean:enable-audio-sink:false scalar:string:video-codec:H.264 scalar:int:resolutionId:2 scalar:int:native-driver-selection-mode:0 scalar:boolean:native-poke-all-paired:false"
keys_of() { case $U in sam) echo "$COMMON $VIDEO $SAMK" ;; hp) echo "$COMMON $VIDEO $HPK" ;; *) echo "$COMMON $VIDEO $HUK" ;; esac; }
setkeys() { local s=$1; shift; adb -s "$s" shell am force-stop $PKG
  if [ "$s" = "$DHU" ]; then python3 -I "$RIG_TOOLKIT/set_hu_settings_host.py" "$s" "$@"
  else python3 -I "$RIG_TOOLKIT/set_prefs_runas_host.py" "$s" "$@"; fi >> "$EV/keys.log" 2>&1; }
keys_read() { adb -s "$1" shell run-as $PKG cat shared_prefs/settings.xml | tr -d '\r' > "$EV/$2-settings.xml"; }
write_keys() { setkeys "$HU" $(keys_of) "scalar:int:view-mode:$VIEW"; keys_read "$HU" "$RUN-$1"; }   # write_keys K
hold_station_off() {   # D-SAM, before a launch: save the stand-down network id once, then delete it so the launch gives nothing back
  local id
  id=$(adb -s "$SAM" shell run-as "$PKG" cat shared_prefs/settings.xml | tr -d '\r' | grep -ao 'name="station-stand-down-network-id" value="[0-9]*"' | grep -ao '[0-9]*"$' | tr -d '"')   # not-literal: a settings.xml line
  [ -n "$id" ] && [ ! -s "$EV/netid.txt" ] && echo "$id" > "$EV/netid.txt"
  echo "netid_seen=${id:-none}" >> "$EV/$RUN-station.txt"
  python3 -I "$RIG_TOOLKIT/set_prefs_runas_host.py" "$SAM" "del:station-stand-down-network-id" >> "$EV/$RUN-station.txt" 2>&1; }

# identity SERIAL NAME: md5 of the live APK (not on D-HP: a pull over wireless adb takes minutes) and ACTION_QUERY_STATE
identity() { local s=$1 u=$2 f="$EV/live-$2.apk" m=same w=same q
  if [ "$u" != hp ]; then
    adb -s "$s" pull "$(adb -s "$s" shell pm path $PKG | head -n 1 | cut -d: -f2 | tr -d '\r')" "$f" >/dev/null 2>&1
    m=$(md5sum "$f" 2>/dev/null | cut -d' ' -f1); w=$(md5sum "$APK" 2>/dev/null | cut -d' ' -f1); rm -f "$f"
  fi
  HU=$s; launch; rl_sleep 3; send ACTION_QUERY_STATE > "$EV/query-$u.txt" 2>&1; appexit
  q=$(grep -ac "${CAND_SHA:0:8}" "$EV/query-$u.txt")
  echo "$u live=$m want=$w query=$q" >> "$EV/ident.log"
  if [ -n "$m" ] && [ "$m" = "$w" ] && [ "${q:-0}" -ge 1 ]; then echo "IDENT_OK $u" >> "$EV/ident.log"
  else echo "IDENT_BAD $u" >> "$EV/ident.log"; fi; }

# settings restore: D-HU as root (run-as cannot read /data/local/tmp there), the others through restore_settings.sh
restore_prefs() { local s=$1 u=$2
  [ -s "$EV/backup-$u.xml" ] || return 0
  adb -s "$s" push "$EV/backup-$u.xml" /data/local/tmp/settings-restore.xml >/dev/null
  if [ "$s" = "$DHU" ]; then
    printf '%s\n' "F=/data/data/$PKG/shared_prefs/settings.xml" 'cp /data/local/tmp/settings-restore.xml $F' \
      "chown \$(stat -c %u:%g /data/data/$PKG) \$F; chmod 660 \$F" > "$EV/restore-hu.sh"
    adb -s "$s" push "$EV/restore-hu.sh" /data/local/tmp/restore-hu.sh >/dev/null; adb -s "$s" shell sh /data/local/tmp/restore-hu.sh
    adb -s "$s" shell rm -f /data/local/tmp/restore-hu.sh
  else
    adb -s "$s" push "$RIG_TOOLKIT/restore_settings.sh" /data/local/tmp/restore_settings.sh >/dev/null
    adb -s "$s" shell run-as $PKG sh /data/local/tmp/restore_settings.sh
    adb -s "$s" shell rm -f /data/local/tmp/restore_settings.sh
  fi
  adb -s "$s" shell rm -f /data/local/tmp/settings-restore.xml
  keys_read "$s" "restored-$u"
  diff <(grep -v '^$' "$EV/backup-$u.xml") <(grep -v '^$' "$EV/restored-$u-settings.xml") > "$EV/restore-diff-$u.txt"; }
# restore_unit SERIAL NAME: the prior APK back, its md5 checked, settings.xml restored; returns 1 on a mismatch
restore_unit() { local s=$1 u=$2 m w r=0
  slow_step 900 "restore $u"
  HU=$s; appexit
  if [ -s "$EV/prior-$u.apk" ]; then
    adb -s "$s" install -r -d "$EV/prior-$u.apk" > "$EV/restore-install-$u.txt" 2>&1
    adb -s "$s" pull "$(adb -s "$s" shell pm path $PKG | head -n 1 | cut -d: -f2 | tr -d '\r')" "$EV/restored-$u.apk" >/dev/null 2>&1
    m=$(md5sum "$EV/restored-$u.apk" 2>/dev/null | cut -d' ' -f1); w=$(md5sum "$EV/prior-$u.apk" | cut -d' ' -f1)
    echo "$u prior=$w live=$m" >> "$EV/restore.log"; [ -n "$m" ] && [ "$m" = "$w" ] || r=1
  fi
  restore_prefs "$s" "$u" || r=1
  adb -s "$s" shell settings put global stay_on_while_plugged_in "$(cat "$EV/stayon-$u.txt" 2>/dev/null || echo 0)"
  return "$r"; }

# ---- one view run ----
run_open() {   # thermal gate, D-HP's battery and dongle, the screen awake, the phone app stopped, both captures on
  local b n
  rm -f "$EV/$RUN-gate.txt"
  rl_thermal_gate || { echo HOST_HOT > "$EV/$RUN-thermal.txt"; return 1; }
  if [ "$U" = hp ]; then
    adb connect "$HU" >/dev/null 2>&1
    b=$(bat); echo "$RUN start bat=$b" >> "$EV/$RUN-notes.txt"
    if [ -n "$b" ] && [ "$b" -lt 40 ]; then untestable "D-HP battery $b% is below 40%; is the Y-cable powered?"; fi
    n=$(adb -s "$HU" shell ls /sys/bus/usb/devices 2>/dev/null | tr -d '\r' | grep -acE '^[0-9]+-[0-9.]+$')   # not-literal: a sysfs name
    [ "${n:-0}" -ge 1 ] || untestable "D-HP sees no USB device; reseat the dongle on the Y-cable"
  fi
  adb -s "$HU" shell svc power stayon true; wake
  adb -s "$PH" shell am force-stop "$PKG"; adb -s "$PH" shell input keyevent KEYCODE_HOME
  rl_cap_start "$RUN-hu" "$HU"; rl_cap_start "$RUN-ph" "$PH"; }
shot() {   # shot TAG: a raw screencap pulled to $EV/TAG.raw, and its lit box in $EV/TAG-lit.txt
  adb -s "$HU" shell screencap /sdcard/ohu-vs.raw
  adb -s "$HU" pull /sdcard/ohu-vs.raw "$EV/$1.raw" >/dev/null 2>&1
  adb -s "$HU" shell rm -f /sdcard/ohu-vs.raw
  python3 -I "$SCR/crop_tools.py" measure "$EV/$1.raw" > "$EV/$1-lit.txt" 2>&1; }
vsess() {   # vsess K: session K of the run: keys, launch, SSL, a rendering decoder, the screenshot, a clean exit
  local k=$1 att F="$EV/$RUN-hu.txt" O
  for att in 1 2; do
    rl_step "$RUN session $k, attempt $att"
    if [ "$ROUTE" = native ]; then phone_link off; fi
    write_keys "$k"
    if [ "$U" = sam ]; then hold_station_off; fi
    wake; mark "$RUN-$k-go"; O=$(rl_offset "$F")
    launch
    if [ "$ROUTE" = native ]; then rl_sleep 2; phone_link on; fi
    if rl_wait_line "$F" 'SSL handshake complete' "$SSL_CAP" "$O" >/dev/null; then
      echo "$k ssl=1 att=$att" >> "$EV/$RUN-gate.txt"
      rl_wait_line "$F" 'Throughput over' 60 "$O" >/dev/null || echo "$k thr=0" >> "$EV/$RUN-gate.txt"
      rl_sleep 10
      mark "$RUN-$k-shot"; shot "$RUN-$k"
      mark "$RUN-$k-x"; appexit; return 0
    fi
    echo "$k ssl=0 att=$att" >> "$EV/$RUN-gate.txt"
    mark "$RUN-$k-r"; appexit
  done
  mark "$RUN-$k-x"; return 1; }

# ---- grading ----
NFAIL=0; INC=""
c() { local o; o=$(rl_cond "$@"); echo "$o"; case "$o" in "COND $1 FAIL"*) NFAIL=$((NFAIL + 1)) ;; esac; }
inc() { INC="$INC${INC:+; }$*"; }
fin() { if [ -n "$INC" ] && [ "$NFAIL" -eq 0 ]; then rl_verdict INCONCLUSIVE "$INC"; fi; }
median() { printf '%s\n' "$@" | sort -n | awk 'NF{a[++n]=$1} END{print (n ? a[int((n+1)/2)] : 0)}'; }
```

**crop_tools.py**

```python
# Lit-box measures from a raw screencap (header, then RGBA_8888), from surfaceview-crop rounds 1 and 2.
#   measure RAW                      one line: w= h= rows=a-b cols=c-d box_permille=N (exit 2: not a raw frame)
#   box --ref LIT... --cand LIT...   MIN MED (candidate boxes) REF (reference median) RATIO DEV (worst edge offset) N;
#                                    a candidate with no lit box (black) gives MIN=0 RATIO=0 DEV=999
# Edges are buffer pixels. Compare only frames of one unit, so a portrait buffer needs no turning.
import re, statistics, struct, sys


def load(path):
    try:
        data = open(path, "rb").read()
    except OSError:
        return None
    if len(data) < 16:
        return None
    w, h, fmt = struct.unpack("<III", data[:12])
    head = len(data) - w * h * 4
    if fmt != 1 or head < 12 or head > 16:
        return None
    return w, h, data[head:]


def measure(path):
    img = load(path)
    if not img:
        print(f"crop_measure: not an RGBA_8888 raw frame: {path}"); sys.exit(2)
    w, h, px = img
    row_lit = [0] * h; col_lit = [0] * w
    for y in range(h):
        base = y * w * 4
        for x in range(0, w, 2):
            i = base + x * 4
            if px[i] * 299 + px[i + 1] * 587 + px[i + 2] * 114 > 24000:
                row_lit[y] += 2; col_lit[x] += 2
    rows = [y for y in range(h) if row_lit[y] >= w * 0.02]
    cols = [x for x in range(0, w, 2) if col_lit[x] >= h * 0.02]
    if not rows or not cols:
        print(f"w={w} h={h} rows=none cols=none box_permille=0"); return
    area = (rows[-1] - rows[0] + 1) * (cols[-1] - cols[0] + 2)
    print(f"w={w} h={h} rows={rows[0]}-{rows[-1]} cols={cols[0]}-{cols[-1]} box_permille={min(1000, area * 1000 // (w * h))}")


def edges(path):
    try:
        t = open(path, errors="replace").read()
    except OSError:
        return None
    m = re.search(r"rows=(\d+)-(\d+) cols=(\d+)-(\d+) box_permille=(\d+)", t)
    return tuple(int(g) for g in m.groups()) if m else None


def box(args):
    ref, cand, cur = [], [], None
    for a in args:
        if a in ("--ref", "--cand"):
            cur = ref if a == "--ref" else cand
        elif cur is not None:
            cur.append(a)
    refs = [e for e in map(edges, ref) if e]
    cands = [edges(c) for c in cand]
    if not refs or not cands or None in cands:   # a black or unreadable candidate frame fails the comparison
        print(f"MIN=0; MED=0; REF=0; RATIO=0; DEV=999; N={len(cands)}"); return
    med = [int(statistics.median(e[i] for e in refs)) for i in range(5)]
    vals = sorted(c[4] for c in cands)
    dev = max(max(abs(c[i] - med[i]) for i in range(4)) for c in cands)
    ratio = vals[0] * 1000 // med[4] if med[4] else 0
    print(f"MIN={vals[0]}; MED={int(statistics.median(vals))}; REF={med[4]}; RATIO={ratio}; DEV={dev}; N={len(cands)}")


cmd = sys.argv[1] if len(sys.argv) > 1 else ""
if cmd == "measure" and len(sys.argv) > 2:
    measure(sys.argv[2])
elif cmd == "box":
    box(sys.argv[2:])
else:
    print("usage: crop_tools.py measure RAW | box --ref LIT... --cand LIT..."); sys.exit(2)
```

**vs_parse.py**

```python
# Throughput numbers from one session of a head-unit capture, as shell assignments.
# usage: vs_parse.py CAPTURE GO SHOT X
#   THR: Throughput over lines from GO to SHOT; LAST_REND: rendered= of the last of them (0 when none)
#   FPS: the median rendered fps of the Throughput over lines from GO to X (0 when none)
import re, statistics, sys

cap, go, shot, x = (sys.argv[1:] + ["", "", "", ""])[:4]
try:
    lines = open(cap, errors="replace").read().splitlines()
except OSError:
    lines = []


def window(a, b):
    out, on = [], False
    for line in lines:
        if not on and a and ("AutomationMarker: " + a) in line:
            on = True
        if on:
            out.append(line)
            if b and ("AutomationMarker: " + b) in line:
                break
    return out


RX = re.compile(r"Throughput over \d+ms: rendered=(\d+) \((\d+)fps\)")
pre = [m for m in map(RX.search, window(go, shot)) if m]
full = [m for m in map(RX.search, window(go, x)) if m]
fps = int(statistics.median(int(m.group(2)) for m in full)) if full else 0
print(f"THR={len(pre)}; LAST_REND={pre[-1].group(1) if pre else 0}; FPS={fps}")
```

**crit_count.py**

```python
# Count lines holding NEEDLE between markers FROM and TO, leaving out the 5 s after every marker whose label
# ends in -x or -r (an exit or a retry): Android Auto's own teardown lands up to 2.2 s later. From surfaceview-crop.
# usage: crit_count.py CAPTURE FROM TO NEEDLE. Prints nothing (exit 1) when the window is missing.
import re, sys
from datetime import datetime, timedelta

if len(sys.argv) < 5:
    sys.exit(1)
cap, a, b, needle = sys.argv[1:5]
END = re.compile(r"AutomationMarker: \S*-(?:x|r)\s*$")


def ts(line):
    m = re.match(r"(\d\d-\d\d) (\d\d:\d\d:\d\d\.\d{3})", line)
    return datetime.strptime("2026-" + m.group(1) + " " + m.group(2), "%Y-%m-%d %H:%M:%S.%f") if m else None


try:
    lines = open(cap, errors="replace").read().splitlines()
except OSError:
    sys.exit(1)
win, on = [], False
for line in lines:
    if not on and ("AutomationMarker: " + a) in line:
        on = True
    if on:
        win.append(line)
        if ("AutomationMarker: " + b) in line:
            break
if not win:
    sys.exit(1)
cuts = [t for t in (ts(line) for line in win if END.search(line)) if t]
n = 0
for line in win:
    if needle in line:
        t = ts(line)
        if t and any(c <= t <= c + timedelta(seconds=5) for c in cuts):
            continue
        n += 1
print(n)
```

### Scripts for prep

**prep.sh**

```bash
rl_step "APK and phone addresses"
md5sum "$APK" > "$EV/prep-apk.txt"
for u in poco moto; do
  if [ "$u" = poco ]; then s=$POCO; d=DC:B7:2E:5E:4E:59; else s=$MOTO; d=A0:46:5A:97:E4:95; fi
  a=$(adb -s "$s" shell settings get secure bluetooth_address | tr -d '\r')
  case "$a" in ??:??:??:??:??:??) echo "$a" > "$EV/$u.btaddr" ;;
    *) echo "$d" > "$EV/$u.btaddr"; echo "$u address unread ($a); the brief's value used" >> "$EV/notes.txt" ;; esac
done
grep '^D-HP ' "$(dirname "$EV")/unit-serials.txt" 2>/dev/null | tail -n 1 | cut -d' ' -f2 > "$EV/hp.addr"   # not-literal: written by video_smoke.py
HP_W=$(cat "$EV/hp.addr"); HP_W=${HP_W:-$HPU}
UNITS="$SAM $DHU"   # hp.skip: video_smoke.py could not reach D-HP and left PT, PS and PG out
[ -f "$EV/hp.skip" ] || { adb connect "$HP_W" >/dev/null 2>&1; UNITS="$UNITS $HP_W"; }
for s in $UNITS; do
  u=$(unit_of "$s"); HU=$s
  slow_step 900 "prep $u: backup, prior APK, install, identity"
  adb -s "$s" shell am force-stop $PKG
  adb -s "$s" shell run-as $PKG cat shared_prefs/settings.xml | tr -d '\r' > "$EV/backup-$u.xml"
  adb -s "$s" shell settings get global stay_on_while_plugged_in | tr -d '\r' > "$EV/stayon-$u.txt"
  adb -s "$s" shell getprop ro.build.version.sdk | tr -d '\r ' > "$EV/sdk-$u.txt"
  p=$(adb -s "$s" shell pm path $PKG | head -n 1 | cut -d: -f2 | tr -d '\r')
  if [ "$u" = hp ]; then   # D-HP keeps the candidate; an APK of the same size is not sent again over wireless adb
    if [ -n "$p" ] && adb -s "$s" shell ls -l "$p" | grep -aqF " $(stat -c %s "$APK") "; then echo "hp: same size, install skipped" >> "$EV/notes.txt"
    else adb -s "$s" install -r -d "$APK" > "$EV/install-$u.txt" 2>&1; fi
  else
    adb -s "$s" pull "$p" "$EV/prior-$u.apk" >/dev/null 2>&1
    md5sum "$EV/prior-$u.apk" >> "$EV/prior-md5.txt" 2>&1
    adb -s "$s" install -r -d "$APK" > "$EV/install-$u.txt" 2>&1
  fi
  setkeys "$s" $COMMON
  identity "$s" "$u"
done
adb -s "$MOTO" shell svc power stayon true
```

**grade-prep.sh**

```bash
U="sam hu"; [ -f "$EV/hp.skip" ] || U="$U hp"
for u in $U; do
  c "I.$u" eq "$(grep -acF "IDENT_OK $u" "$EV/ident.log")" 1 "$u runs ${CAND_SHA:0:8} (md5 on USB units, ACTION_QUERY_STATE commit)"   # not-literal
  case $u in sam) want=19 ;; hu) want=34 ;; *) want=17 ;; esac
  c "A.$u" eq "$(cat "$EV/sdk-$u.txt" 2>/dev/null)" "$want" "$u runs API $want, the level this smoke covers"
done
```

### Scripts for the view runs (ST, SS, SG, MT, MS, MG, PT, PS, PG)

**view.sh**

```bash
run_open || exit 0
for k in 1 2; do vsess "$k"; done
mark "$RUN-end"
rl_cap_stop "$RUN-hu"; rl_cap_stop "$RUN-ph"
```

**grade-view.sh**

```bash
F="$EV/$RUN-hu.txt"; P="$EV/$RUN-ph.txt"; REFF="$EV/ref-$U.txt"
n=0; used=0; rend=0; fall=0; mism=0; lits=""; fpsl=""
[ "$VIEW" = 1 ] && rm -f "$REFF"
: > "$EV/$RUN-sessions.txt"
for k in 1 2; do
  a="$RUN-$k-go"; s="$RUN-$k-shot"; x="$RUN-$k-x"
  if [ "$(rl_cnt "$F" "$a" "$x" 'SSL handshake complete')" -lt 1 ]; then echo "$k left out: no SSL in two attempts" >> "$EV/$RUN-sessions.txt"; continue; fi
  if ! grep -aq "name=\"view-mode\" value=\"$VIEW\"" "$EV/$RUN-$k-settings.xml" 2>/dev/null; then   # not-literal: a settings.xml line
    echo "$k left out: settings read-back is not view-mode $VIEW" >> "$EV/$RUN-sessions.txt"; continue; fi
  n=$((n + 1))
  case $VIEW in
    1) u=$(rl_cnt "$F" "$a" "$x" 'Using TextureView') ;;
    2) u=$(rl_cnt "$F" "$a" "$x" 'Using GlProjectionView') ;;
    *) u=$(rl_cnt "$F" "$a" "$x" 'Using SurfaceView') ;;
  esac
  [ "${u:-0}" -ge 1 ] && used=$((used + 1))
  fall=$((fall + $(rl_cnt "$F" "$a" "$x" 'Falling back to SurfaceView for this session')))
  mism=$((mism + $(rl_cnt "$F" "$a" "$x" 'Surface mismatch!')))
  THR=0; LAST_REND=0; FPS=0; eval "$(python3 -I "$SCR/vs_parse.py" "$F" "$a" "$s" "$x")"
  adm=no
  if [ "${THR:-0}" -ge 1 ] && [ "${LAST_REND:-0}" -gt 0 ]; then
    adm=yes; rend=$((rend + 1)); lits="$lits $EV/$RUN-$k-lit.txt"; fpsl="$fpsl $FPS"; fi
  echo "$k use=$u thr=$THR last_rendered=$LAST_REND fps=$FPS picture_compared=$adm $(cat "$EV/$RUN-$k-lit.txt" 2>/dev/null)" >> "$EV/$RUN-sessions.txt"
done
fps=$(median $fpsl)
if [ -s "$EV/$RUN-thermal.txt" ]; then rl_verdict UNTESTABLE "host thermal: the gate failed twice"
elif [ -f "$EV/$RUN-untestable.txt" ]; then rl_verdict UNTESTABLE "$(cat "$EV/$RUN-untestable.txt")"
elif [ "$n" -eq 0 ]; then rl_verdict UNTESTABLE "no session formed SSL in two attempts per session (see $RUN-gate.txt and $RUN-sessions.txt)"
else
  c "$RUN.1" eq "$used" "$n" "the backend's own Using line in every session that formed ($used of $n)"
  c "$RUN.1b" eq "$fall" 0 "Falling back to SurfaceView for this session"
  c "$RUN.2" eq "$rend" "$n" "a rendering decoder at the screenshot in every session that formed ($rend of $n; median fps $fps)"
  if [ -z "$lits" ]; then
    c "$RUN.3" ge 0 1 "no session rendered at its screenshot, so no picture can be compared"
  elif [ "$VIEW" = 1 ]; then
    eval "$(python3 -I "$SCR/crop_tools.py" box --ref $lits --cand $lits)"
    c "$RUN.3" ge "${MIN:-0}" 600 "every TextureView lit box is 600 permille or more of the screen (min $MIN, median $MED)"
    c "$RUN.3b" le "${DEV:-999}" 8 "every TextureView box edge within 8 px of the sessions' median (worst $DEV)"
  elif [ -s "$REFF" ]; then
    rfps=$(sed -n 's/^FPS=//p' "$REFF"); rlits=$(grep -v '^FPS=' "$REFF")
    eval "$(python3 -I "$SCR/crop_tools.py" box --ref $rlits --cand $lits)"
    c "$RUN.3" ge "${RATIO:-0}" 750 "every lit box is 750 permille or more of the TextureView median (min $MIN, TextureView $REF)"
    c "$RUN.3b" le "${DEV:-999}" 8 "every box edge within 8 px of the TextureView median (worst $DEV)"
    if [ "${rfps:-0}" -ge 8 ]; then
      c "$RUN.2b" ge "$(( ${fps:-0} * 100 / rfps ))" 25 "median rendered fps $fps against TextureView's $rfps, percent"
    else
      c "$RUN.2b" ge 0 0 "report: median rendered fps $fps, TextureView $rfps (below 8, so no floor)"
    fi
  else
    inc "the unit's TextureView run did not pass, so the picture and the frame rate have no reference"
  fi
  c "$RUN.4" eq "$mism" 0 "Surface mismatch!"
  crash=$(( $(rl_cnt "$F" "$RUN-1-go" "$RUN-end" 'Process: com.andrerinas.headunitrevived') + $(rl_cnt "$F" "$RUN-1-go" "$RUN-end" 'ANR in com.andrerinas') ))   # system
  c "$RUN.4b" eq "$crash" 0 "FATAL EXCEPTION or ANR for the app"
  c "$RUN.4c" eq "$(python3 -I "$SCR/crit_count.py" "$P" "$RUN-1-go" "$RUN-end" 'Critical error')" 0 "phone Critical error, the 5 s after each exit left out"   # phone
  hm=$(rl_cnt "$F" "$RUN-1-go" "$RUN-end" "AutomationMarker: $RUN-"); pm=$(rl_cnt "$P" "$RUN-1-go" "$RUN-end" "AutomationMarker: $RUN-")
  c "$RUN.4d" eq "${pm:-0}" "${hm:-0}" "the phone capture holds the head unit's $RUN markers"
  if [ "$VIEW" = 1 ] && [ "$NFAIL" -eq 0 ] && [ -n "$lits" ]; then { echo "FPS=$fps"; printf '%s\n' $lits; } > "$REFF"; fi
  fin
fi
```

### Scripts for close

**close.sh**

```bash
rc=0
HU=$SAM; PH=$POCO
NETID=$(head -1 "$EV/netid.txt" 2>/dev/null)
if [ -n "$NETID" ]; then   # D-SAM's network back: the app gives it back when it starts with the id set
  rl_step "D-SAM: network back"
  adb -s "$SAM" shell am force-stop $PKG
  python3 -I "$RIG_TOOLKIT/set_prefs_runas_host.py" "$SAM" "scalar:int:station-stand-down-network-id:$NETID" > "$EV/close-netid.txt" 2>&1
  rl_cap_start close-hu "$SAM"; O=$(rl_offset "$EV/close-hu.txt")
  launch
  rl_wait_line "$EV/close-hu.txt" 'WiFi network is enabled again and should rejoin' 60 "$O" > "$EV/close-give.txt" || rc=1
  appexit; rl_cap_stop close-hu
fi
restore_unit "$SAM" sam || rc=1
restore_unit "$DHU" hu || rc=1
if [ ! -f "$EV/hp.skip" ]; then
  HU=$HP_W; appexit; restore_prefs "$HP_W" hp || rc=1
  adb -s "$HP_W" shell settings put global stay_on_while_plugged_in "$(cat "$EV/stayon-hp.txt" 2>/dev/null || echo 0)"
fi
adb -s "$MOTO" shell svc power stayon false
echo "netid=${NETID:-none} rc=$rc" > "$EV/close-result.txt"
exit "$rc"
```

```decisive-strings
SSL handshake complete
Throughput over
Using TextureView
Using SurfaceView
Using GlProjectionView
Falling back to SurfaceView for this session
Surface mismatch!
WiFi network is enabled again and should rejoin
Process: com.andrerinas.headunitrevived
ANR in com.andrerinas
Critical error
AutomationMarker:
```
