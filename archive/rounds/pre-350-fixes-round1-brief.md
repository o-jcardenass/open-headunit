# pre-350-fixes, round 1 brief: five small upstream changes before the 3.5.0 release

Publish name on `transfer/rig-rounds`: `pre-350-fixes-round1-brief.md`. Results go in
`pre-350-fixes-round1-results.md`. Evidence goes in a new release `rig-evidence-pre-350-fixes` as the
asset `pre-350-fixes-round1-captures.zip`.

Five outside contributions are candidates for the release. Each one is small, and each one gets one
short run here. One integration build carries all five. D-HU, D-POCO and D-SAM. About 2.5 hours plus
two builds.

| Run | Change | Unit |
|---|---|---|
| R1 | Text dialogs: Enter presses OK, a tap on the dialog body hides the keyboard | D-HU, then D-SAM |
| R2 | Self Mode: the app's media session stays inactive, so the local player keeps the media keys | D-POCO (Self Mode), D-HU (ordinary session) |
| R3 | Input: letter keycodes are announced; the night key moves off `KEYCODE_N` | D-HU + D-POCO |
| R4 | Home screen: an unticked Self Mode button stays live while a session runs | D-HU + D-POCO |
| R5 | Night mode: a new "Automatic (System)" choice follows the system dark theme | D-HU + D-POCO |

**The point of the round is R3.** It is the only change whose effect on Android Auto is unknown. The
others are regression guards with one decisive reading each.

## 1. Build and baseline

| Arm | Ref | SHA | `commit` in `ACTION_QUERY_STATE` |
|---|---|---|---|
| **C** (candidate) | `fork/review/pre-350-fixes` | `1fc51814` | `1fc51814` (first 12 characters of the full SHA) |
| **A** (baseline) | `origin/main` | `4ee1d58f` | `4ee1d58f` (first 12 characters of the full SHA) |

C is `main` at `4ee1d58f` plus five merges, one per change, in run order R1 (`c8908925`), R2
(`1f80e763`), R3 (`902cc281`), R4 (`94588411`), R5 (`c581e5a7`). No history was rewritten. This is
the thread's first round. A is needed for R2's control and for R3's baseline and migration start.

```bash
git fetch fork review/pre-350-fixes && git fetch origin main
git rev-parse fork/review/pre-350-fixes origin/main     # print both full SHAs; record them
git worktree add ../p350-C fork/review/pre-350-fixes
git worktree add ../p350-A origin/main
cp local.properties ../p350-C/ && cp local.properties ../p350-A/
```

If the first SHA does not start with `1fc51814` or the second with `4ee1d58f`, stop the round.

### R0. Build and identity gate

Make `hur-wifi-test-scripts/pre-350-fixes-round1/` and set `OUT` to it. Wait for the host to cool
(`th_wait 70`). Build each arm and copy its APK out at once (tooling quirk: `build_hur.sh` deletes
the previous APK). The contract:

```bash
cd ../p350-C && ./gradlew :app:assembleGithubDebug --max-workers=2 && mkdir -p $OUT/apk && cp app/build/outputs/apk/github/debug/*.apk $OUT/apk/C.apk
cd ../p350-C && ./gradlew :app:testGithubDebugUnitTest --max-workers=2
th_wait 70
cd ../p350-A && ./gradlew :app:assembleGithubDebug --max-workers=2 && cp app/build/outputs/apk/github/debug/*.apk $OUT/apk/A.apk
md5sum $OUT/apk/C.apk $OUT/apk/A.apk
for s in TextDialogKeyPolicy MediaSessionActivationPolicy migrateLegacyNightKey applyButtonVisibility 'NightModeManager: SYSTEM uiMode='; do
  printf '%s C=%s A=%s\n' "$s" "$(unzip -p $OUT/apk/C.apk 'classes*.dex' | strings | grep -cF "$s")" "$(unzip -p $OUT/apk/A.apk 'classes*.dex' | strings | grep -cF "$s")"; done
AAPT2=$(ls -d $ANDROID_HOME/build-tools/*/aapt2 | tail -1)
$AAPT2 dump resources $OUT/apk/C.apk | grep -a 'id/vehicleInfoFragment'
```

**PASS when all hold:** both builds succeed; the unit tests report 3208 tests, 0 failures and 0
errors; the two md5s differ; each of the five symbols counts 1 or more in C and 0 in A; the `aapt2`
line prints a resource id. Record that id (for example `0x7f0a0123`) as `VEH_ID`; R1 needs it.
After every install, `state` must show the `commit` of the arm the table names. **A failure in R0
stops the round.**

## 2. What this is and why

The release is in a feature freeze, so only fixes go in. These five passed review and JVM tests.
Each one changes something a JVM test cannot see: a dialog window, the system media-session stack,
what Android Auto does with a keycode list, a view's enabled flag, and the system's night mode.

- **R1, text dialogs.** On a short, wide head-unit panel the on-screen keyboard covered the whole
  text dialog, OK included. Enter only added a new line, so values such as the manual hotspot name
  could not be saved. The change makes every field single-line, makes Enter on the last field press
  OK, hides the keyboard on a tap anywhere in the dialog body that is not a field, and resizes the
  window above the keyboard. The class is `TextDialogKeyPolicy`.
- **R2, Self Mode media session.** In Self Mode, Android Auto, this app and the user's media player
  share one device. The app's media session was active there, which let it take the media widget
  and the media buttons from the real player. The change keeps the app's session inactive for a
  loopback session and active for an ordinary one. It asks the live session
  (`CommManager.isLoopbackSession`), not the Self Mode launcher flag. An earlier round on this rig
  (`self-mode-media-session-round1-results.md`, R1) measured `main` in Self Mode: the app's session
  read `active=true` with `state=STOPPED`, and the local player still held the media buttons. So
  this rig does not reproduce the user's symptom, but `active=` is a direct reading of the change.
- **R3, letter keycodes.** The head unit announces no letter keycodes in its service discovery. So
  when a text field takes focus on the head unit (Maps search), Android Auto wakes the phone and
  opens the **phone's** keyboard. A reporter measured that on the phone as `mInputShown=true` with
  `com.google.android.apps.maps/...ghost.GhostActivity` in front. The change announces A to Z,
  punctuation, editing keys and modifiers. **Nobody knows yet whether the list alone is enough**;
  Android Auto may key off something else. R3 answers that. The change also moves the app's own
  night key off `KEYCODE_N`, which is now a letter, to a private code `65539`. A saved keymap that
  holds the old night entry is migrated once, when the keymap is read, and `65539` is never
  announced to the phone.
- **R4, home buttons.** Unticked connection types are now dimmed and disabled on the home screen.
  `pr-1042-disabled-home-buttons-round1-results.md` R4 proved a defect on hardware: with only WiFi
  ticked and a session in picture-in-picture, the Self Mode button ("To Android Auto") was disabled
  and the press did nothing. The author fixed it in `94588411`: the Self Mode button stays live while
  a session runs, and the buttons refresh when the connection state changes.
- **R5, night mode follows the system.** A new night mode choice, value 8, "Automatic (System)",
  reads the system's `uiMode` night bit and listens for configuration changes.

## 3. What is different about this round

- **Five changes, one candidate build.** A FAIL in one run blocks only that change. Run every run
  whatever the earlier verdicts were.
- **R3 can come back INCONCLUSIVE, and that is a real answer.** If C still opens the phone's
  keyboard, the keycode list alone does not move Android Auto. Say so; do not search for another
  lever.
- **R3 and R2 need taps into the projected video**, the template's standing exception (§3): opening
  Maps and its search field in R3, nothing else. They go to the phone over the touch channel.
- **R1 needs taps on the app's own dialog.** No verb opens a settings row or a dialog. They are
  named hand steps (below), done as injected taps from a `uiautomator dump`, at most 5 per unit.
- **D-POCO plays two roles.** It is the phone for D-HU in R2b, R3, R4 and R5, and it runs Self Mode
  on itself in R2a and R2c. Force-stop the app on D-HU before every D-POCO Self Mode run, so D-HU
  does not poke it.
- **D-HU's `shared_prefs/` can be root-owned** (D-HU quirk). R3 grades a value the **app** writes
  (the migrated keymap). Record `ls -ld /data/data/com.andrerinas.headunitrevived/shared_prefs` once,
  `chown` it to the app's uid:gid if it is wrong, and say what it read first in Setup notes.
- **The rig's audio settings are a deliberate worst case.** Never write `use-aac-audio`,
  `audio-latency-multiplier`, `audio-queue-capacity` or `enable-audio-sink`. Read them back and
  record them.
- **Never run adb calls in parallel against one unit.** The background logcat captures are the only
  exception.
- **`dumpsys activity top` times out when old tasks are left on D-HU** (pr-1042 round 1). Force-stop
  Files, Clock, the OEM settings app, stock Settings and Chrome before R4.

### Hand steps

Every one is an injected `input tap`. Take the coordinates from a `uiautomator dump` (or from a
screenshot when video plays) and list each tap with its time in `$OUT/hand-steps.log` through `cue`.

| Id | Run | Target | Why there is no verb |
|---|---|---|---|
| HS1 | R1 | the "Vehicle display name" row on the vehicle info screen | no verb opens a settings row |
| HS2 | R1 | the "Make" row on the same screen | the same |
| HS3 | R1 | the text field of the open dialog, only if the keyboard is not up | the dialog may not raise the keyboard by itself |
| HS4 | R1 | the hint text of the "Make" dialog, which is the dialog body and not a field | the control under test |
| HS5 | R3 | in the projection, the Maps icon, then the Maps search field (2 taps) | projected video, the standing exception |
| HS6 | R4 | the "Picture-in-Picture" row of the projection's exit dialog | `enterPiP` is reached only from that row |
| HS7 | R4 | the Self Mode button ("To Android Auto") on the home screen | the control under test |
| HS8 | R2 | on D-POCO, the play button of the local player, only if `input keyevent 126` did not start it | the player is not ours |
| HS9 | R2 | on D-POCO, Gearhead's "Start head unit server", only if the precheck finds no server | developer setting, UI only |

## 4. Settings keys

Write with the app stopped. Take a fresh backup of each unit first and state the delta against it in
Setup notes. On D-HU write with `hu_put` (root, then `chown` and `chmod 660`) from `ohu_lib.sh`, in
the key syntax below. On D-POCO use `set_prefs_runas.sh`. On D-SAM edit a local copy and
`run-as $PKG cp` it in (D-SAM quirk: no `sed`). Read every key back before the launch. Restore every
unit from its backup at the end and diff it against the backup.

**Before the round, fix the empty-set bug in your copy of `ohu_setkeys.py`** (tooling quirk):
`for item in (v.split(',') if v else []):`. Then write `set:native-poke-bt-macs=` once to a scratch
copy and confirm it gives `<set name="native-poke-bt-macs" />`.

`$MAC` is D-POCO's Bluetooth address. Take it from D-HU's `dumpsys bluetooth_manager` "Bonded
devices" list and record it.

```bash
# D-HU, no phone wanted (R1)
QUIET="int:wifi-connection-mode=3 int:native-ap-transport=0 int:native-driver-selection-mode=0 bool:native-poke-all-paired=false set:native-poke-bt-macs= bool:auto-start-self-mode=false bool:auto-connect-last-session=false int:onboarding-version=2 del:video-profile-starvation-cap"
KEYS_R1="$QUIET int:log-level=2"
# D-HU with a Native AA session to D-POCO (R2b, R3, R4, R5)
SESS="int:wifi-connection-mode=3 int:native-ap-transport=0 int:native-driver-selection-mode=0 bool:native-poke-all-paired=false set:native-poke-bt-macs=$MAC str:last-connected-native-mac=$MAC str:native-preferred-device-mac=$MAC bool:auto-start-self-mode=false bool:auto-connect-last-session=false bool:kill-on-disconnect=false int:onboarding-version=2 del:native-aa-wake-damage-verdict del:video-profile-starvation-cap"
KEYS_R2B="$SESS int:log-level=2 int:media-key-routing=0"
KEYS_R3="$SESS int:log-level=2 set:key-codes=42-131"
KEYS_R4="$SESS int:log-level=2 set:connection-modes=wifi"
KEYS_R5="$SESS int:log-level=1 int:night-mode=8"
```

On D-POCO for R2a and R2c: `auto-start-self-mode` false, `log-level` 1, `onboarding-version` 2.

Notes on three keys:

- `key-codes` is a string set of `logical-physical` pairs. `42-131` is the keymap an older build
  saved when the user bound the night key to F1: logical `KEYCODE_N` (42) on physical `KEYCODE_F1`
  (131). Write it **only under A**; C must read it and migrate it.
- `night-mode` 8 is the new SYSTEM value. A has no value 8.
- `ACTION_LOG_MARKER` is not in `AutomationCommandPolicy.CONFIGURING` at C or A, so no
  `allow-external-configuration` is needed.

## 5. Helpers and verbs

Copy `ohu_lib.sh`, `ohu_setkeys.py` and `ptr_lib.sh` into `$OUT` from
`hur-wifi-test-scripts/projection-teardown-and-relays-round3/`, and `flags.py` and `p1042_lib.sh`
from `hur-wifi-test-scripts/pr-1042-disabled-home-buttons-round1/`. From the first three use only
`prefs_cat`, `hu_put`, `apk_check`, `th_wait`, `th_gate` and `th_report`. Source `p1042_lib.sh`, then
save and source `$OUT/p350_lib.sh` last, so its functions win:

```bash
# p350_lib.sh : source after ohu_lib.sh, ptr_lib.sh and p1042_lib.sh. Needs OUT and RUN.
SAM=30041c35642d2200
# ime UNIT : is the soft keyboard up on UNIT
ime() { adb -s $1 shell dumpsys input_method | grep -a -m1 -o 'mInputShown=[a-z]*'; sleep 0.3; }
# udump UNIT TAG : uiautomator dump, saved; prints EditText count and the dialog button bounds
udump() { adb -s $1 shell uiautomator dump /sdcard/u.xml >/dev/null; sleep 0.3
  adb -s $1 pull /sdcard/u.xml "$OUT/$RUN.$2.xml" >/dev/null; sleep 0.3
  printf 'edittext=%s ' "$(grep -ao 'class="android.widget.EditText"' "$OUT/$RUN.$2.xml" | wc -l)"
  grep -ao 'resource-id="android:id/button1"[^>]*bounds="[^"]*"' "$OUT/$RUN.$2.xml" | grep -ao 'bounds="[^"]*"' || echo 'button1=ABSENT'; }
# has UNIT TAG TEXT : does the saved dump TAG on UNIT contain TEXT
has() { grep -acF -- "$3" "$OUT/$RUN.$2.xml"; }
# msess UNIT : one line per media session: package, active, playback state
msess() { adb -s $1 shell dumpsys media_session > "$OUT/$RUN.ms.$(date +%H%M%S).txt"; sleep 0.3
  adb -s $1 shell dumpsys media_session | awk '/package=/{p=$1} /active=/{a=$1} /state=PlaybackState/{match($0,/state=[0-9]+/); print p, a, substr($0,RSTART,RLENGTH)} /Media button session is/{print}'; sleep 0.3; }
phone_ime() { adb -s $POCO shell dumpsys input_method | grep -a -m1 -o 'mInputShown=[a-z]*'; sleep 0.3
  adb -s $POCO shell dumpsys window | grep -a -m1 mCurrentFocus; sleep 0.3; }
phshot() { adb -s $POCO shell screencap -p /sdcard/s.png; sleep 0.3; adb -s $POCO pull /sdcard/s.png "$OUT/$RUN.$1.phone.png" >/dev/null; sleep 0.3; }
```

`p1042_lib.sh` already gives `send`, `mark`, `cap_start`, `cap_stop`, `hwin`, `pwin`, `c`, `rend`,
`lines`, `waitline`, `focus`, `pinned`, `viewflags`, `shot`, `state`, `cue` and `clean_hu`, all
aimed at D-HU (`$DHU`) with D-POCO (`$POCO`) as the phone. R2a and R2c run the app on D-POCO, so for
those two runs set `DHU=$POCO` before sourcing nothing else, and set it back to `27870808938846`
after. R1b runs on D-SAM: set `DHU=$SAM` the same way.

**Verbs and commands used, and nothing else acts on the app:**

| Action | Command |
|---|---|
| arm Native AA, as the WiFi button | `send ACTION_START_WIRELESS_SCAN` |
| Self Mode, as its button | `send ACTION_START_SELF_MODE` |
| user exit from the projection | `send ACTION_DISCONNECT` |
| marker | `mark <label>` |
| build identity | `state` |
| launch, or bring the home screen in front | `adb -s $DHU shell am start -n $MAIN` |
| open the vehicle info screen | `adb -s $DHU shell am start -n $SETA --ei extra_destination $((VEH_ID))` |
| type into the focused field | `adb -s $DHU shell input text <word>` |
| Enter, BACK, keys, media keys | `adb -s $DHU shell input keyevent <code>` |
| open the projection's exit dialog | `adb -s $DHU shell input keyevent 4` |

Every verb must print `AutomationReceiver: ` in the app's capture. A step with no such line never
landed: that step is void, not a FAIL. `clean_hu` only for a running app (`headunit://exit` on a
stopped app starts the service and arms the stack).

### Precheck, once

Apps stopped. Record every output in Setup notes.

```bash
adb -s $POCO shell dumpsys package com.google.android.projection.gearhead | grep -a -m1 versionName
adb -s $DHU shell dumpsys bluetooth_manager | grep -a -A8 'Bonded devices'      # D-POCO listed; take $MAC
adb -s $POCO shell dumpsys wifip2p | grep -a -m2 -E 'isGroupOwner|groupFormed'   # isGroupOwner false
adb -s $DHU shell ls -ld /data/data/com.andrerinas.headunitrevived/shared_prefs
adb -s $DHU shell dumpsys input_method | grep -a -m1 mCurMethodId             # an IME exists
adb -s $SAM shell dumpsys input_method | grep -a -m1 mCurMethodId
adb -s $DHU shell cmd uimode night                                              # record; R5 restores it
adb -s $POCO shell pm list packages | grep -aE 'spotify|videolan'               # a local player
adb -s $POCO shell grep -ac ':149D' /proc/net/tcp6                              # Gearhead's head unit server (R2)
```

If the last line reads 0, R2a and R2c need HS9 before they run.

## 6. The deciding lines

Every app string below matched `git grep -F` in `app/src/main` at `1fc51814`.

| Line (grep -F) | Source | Level | Used for |
|---|---|---|---|
| `AutomationReceiver: ` | `AutomationReceiver` | INFO | a verb landed |
| `AutomationMarker: ` | `AutomationEffectRunner` | WARN | markers |
| `SSL handshake complete` | `AapSslContext` | INFO | a session formed |
| `Throughput over ` | `VideoDecoder` | INFO | picture; regex `Throughput over [0-9]+ms: rendered=[1-9]` |
| `CommManager: TX Key -> AA=` | `CommManager.sendKey` | INFO | a key went to Android Auto; the code follows `AA=` |
| `Received request to resend night mode state` | `AapService` | INFO | the night key reached the app (R3) |
| `MediaSession: State updated to ` | `AapService.updateMediaSessionState` | INFO | the app's media session was driven (R2) |
| `Runtime MEDIA_BUTTON ignored in Self-Mode` | `AapService` | DEBUG | a media key reached our receiver in Self Mode and was left alone (R2a, record only) |
| `SelfMode: AA 17.4+ detected. Connecting directly to Headunit Server` | `SelfLauncherManager` | INFO | Self Mode took the loopback route (R2) |
| `NightModeManager: SYSTEM uiMode=` | `NightModeManager` | DEBUG | the new choice read the system bit; the line ends `isNight=true` or `false` (R5) |
| `NightMode update: ` | `AapService.setupNightMode` | INFO | a night state went to Android Auto; ends `true` or `false` (R5) |
| `HomeFragment: onResume. isConnected=` | `HomeFragment.onResume` | INFO | the home screen resumed (R4) |
| `MainActivity: Active session detected, bringing projection to front` | `MainActivity.onResume` | INFO | must be 0 after the home screen is brought forward in R4 |
| `AapService: Disconnected.` | `AapService` | INFO | session end |
| `MATCH! Starting AapService` | `AutoStartReceiver` | INFO | discard check: record |

Gearhead lines, graded on D-POCO's capture. They are not in our source.

| Line | Used for |
|---|---|
| `received ByeByeRequest` (tag `CAR.GAL.GAL.LITE`, set VERBOSE by `cap_start`) | the session was live on the phone until our exit |
| `Critical error` | Android Auto refused something we sent; must be 0 in every session run |

Framework outputs, not log lines: `mInputShown=` from `dumpsys input_method`, `mCurrentFocus` from
`dumpsys window`, the `msess` lines from `dumpsys media_session` (playback `state=3` is PLAYING,
`state=2` is PAUSED), the view FLAGS from `dumpsys activity top`, and `pinned` from
`dumpsys activity activities`.

## 7. Runs

Every run: `th_gate` first, then the steps in order. Report `th_report $RUN` in each section. Order:
R3A, R3C, R1a, R1b, R4, R5, R2b, R2a, R2c. That order keeps installs to four: A on D-HU, C on D-HU,
C on D-SAM, then C and A on D-POCO.

Session bring-up, used by R2b, R3, R4 and R5 (`PHCAP=1`; `$K` is the run's keys):

```bash
adb -s $POCO shell cmd connectivity airplane-mode disable; sleep 5
adb -s $POCO shell svc wifi enable; adb -s $POCO shell svc bluetooth enable; sleep 5
adb -s $POCO shell dumpsys bluetooth_manager | grep -a -m2 -iE 'enabled|state'    # on
adb -s $POCO shell input keyevent KEYCODE_HOME; sleep 1                          # D-POCO quirk: idle screen
adb -s $DHU shell am force-stop $PKG; hu_put "$BASEXML" $K                       # read back
th_gate; cap_start; mark $RUN-start
adb -s $DHU shell am start -n $MAIN; sleep 5
mark $RUN-arm; L=$(lines); send ACTION_START_WIRELESS_SCAN
waitline 120 'SSL handshake complete' "$(HF)" $L; echo "ssl=$?" | tee -a $OUT/$RUN.summary
sleep 15; focus | tee $OUT/$RUN.focus-session                                   # expect AapProjectionActivity
```

If `ssl=1`, re-run the bring-up once. A second `ssl=1` makes that run UNTESTABLE.

Session end, used by the same runs:

```bash
mark $RUN-x-go; send ACTION_DISCONNECT > $OUT/$RUN-x.reply; sleep 8; mark $RUN-x-done
mark $RUN-end; clean_hu; cap_stop; th_report $RUN; PHCAP=
```

**Every session run is valid only when** `hwin $RUN-arm $RUN-x-go | rend` is 1 or more,
`hwin $RUN-start $RUN-x-go | c 'AapService: Disconnected.'` is 0,
`pwin $RUN-x-go $RUN-x-done | c 'received ByeByeRequest'` is 1 or more, and
`pwin $RUN-start $RUN-end | c 'Critical error'` is 0. Report all four numbers. A `Critical error`
is a FAIL of that run's change, not a void run.

### R3. The point of the round: does the phone's keyboard stay away?

**R3A, baseline.** Install A on D-HU (`adb -s $DHU install -r -d $OUT/apk/A.apk`, `apk_check`,
`state`). `RUN=R3A; K=$KEYS_R3`, then the bring-up. Then:

```bash
shot projection
cue "HS5: tap the Maps icon in the projection, wait 5 s, then tap the Maps search field. Then press Enter here"; read _
sleep 3; mark R3A-search
phone_ime | tee $OUT/R3A.phone-ime; phshot search; shot search
adb -s $DHU shell input keyevent 4; sleep 2      # leave the search field
```

Then the session end. Then, with the app stopped:

```bash
adb -s $DHU shell run-as $PKG cat shared_prefs/settings.xml | grep -a -o 'name="key-codes".*</set>' | tee $OUT/R3A.keycodes
```

**R3A is the reproduction.** Expected: `R3A.phone-ime` reads `mInputShown=true`, with Maps'
`GhostActivity` in focus. If it reads `mInputShown=false`, this rig does not reproduce the defect:
R3C's keyboard reading then has nothing to compare against and is INCONCLUSIVE, but R3C's key and
migration conditions still grade.

**R3C, candidate.** Install C over A (`adb -s $DHU install -r $OUT/apk/C.apk`, `apk_check`, `state`).
**Do not run `hu_put` in R3C's bring-up**: it rebuilds the file from the backup and would erase the
stored `42-131` that C has to migrate. Replace that one line of the bring-up with this read, which
must print `42-131` (an `install -r` can wipe the file, tooling quirk; if it did, re-run R3A):

```bash
adb -s $DHU shell am force-stop $PKG
adb -s $DHU shell run-as $PKG cat shared_prefs/settings.xml | grep -a -o 'name="key-codes".*</set>' | tee $OUT/R3C.keycodes-before
```

`RUN=R3C`, then the rest of the bring-up. Then:

```bash
cue "HS5: tap the Maps icon in the projection, wait 5 s, then tap the Maps search field. Then press Enter here"; read _
sleep 3; mark R3C-search
phone_ime | tee $OUT/R3C.phone-ime; phshot search; shot search
mark R3C-type; for k in 29 30 31; do adb -s $DHU shell input keyevent $k; sleep 0.5; done; sleep 2
shot typed; mark R3C-typed
adb -s $DHU shell input keyevent 4; sleep 2
mark R3C-night; adb -s $DHU shell input keyevent 131; sleep 3; mark R3C-night-done
mark R3C-n; adb -s $DHU shell input keyevent 42; sleep 3; mark R3C-n-done
```

Then the session end. Then, with the app stopped, read the keymap as R3A did into `R3C.keycodes`.

**PASS** when the session is valid and all of:

1. **Keyboard (the point):** `R3C.phone-ime` reads `mInputShown=false`, and R3A read
   `mInputShown=true`.
2. **Letters go out:** `hwin R3C-type R3C-typed` has `TX Key -> AA=29`, `TX Key -> AA=30` and
   `TX Key -> AA=31`, each 1 or more.
3. **Night key migrated:** `hwin R3C-night R3C-night-done` has `TX Key -> AA=65539` 1 or more,
   `Received request to resend night mode state` 1 or more, and `TX Key -> AA=42` 0.
4. **A bare N is a letter now:** `hwin R3C-n R3C-n-done` has `TX Key -> AA=42` 1 or more and
   `Received request to resend night mode state` 0.
5. **Migration persisted:** `R3C.keycodes` contains `65539-131` and does not contain `42-131`.
   `R3A.keycodes` contained `42-131`.
6. Phone: `pwin R3C-start R3C-end | c 'Critical error'` is 0 (the longer keycode list was accepted).

**FAIL** when the session is valid and any of 2 to 6 does not hold. **INCONCLUSIVE** for condition 1
alone when R3A did not reproduce, or when R3C reads `mInputShown=true` too: then the list alone does
not move Android Auto, and the change is not a fix for the phone keyboard. Say which.

Record in all cases: `R3C.search.png` and `R3C.typed.png` (does Android Auto show its own keyboard,
and do the letters `abc` appear in the search box), and the phone focus line from both arms.

### R1. Text dialogs (C)

**R1a, D-HU.** Install C if R3C did not leave it (`state`). D-POCO in airplane mode. `RUN=R1a`.

```bash
adb -s $DHU shell am force-stop $PKG; hu_put "$BASEXML" $KEYS_R1                 # read back
th_gate; cap_start; mark R1a-start
adb -s $DHU shell am start -n $MAIN; sleep 5
adb -s $DHU shell am start -n $SETA --ei extra_destination $((VEH_ID)); sleep 3
udump $DHU list; focus
cue "HS1: tap the 'Vehicle display name' row (bounds from $OUT/R1a.list.xml). Press Enter here"; read _
sleep 2; ime $DHU | tee $OUT/R1a.ime-open; udump $DHU open1 | tee $OUT/R1a.open1
#  if ime-open reads false: cue "HS3: tap the text field", then repeat the ime line
adb -s $DHU shell input text R1VALUE; sleep 1
mark R1a-enter; adb -s $DHU shell input keyevent 66; sleep 2
udump $DHU after1 | tee $OUT/R1a.after1; echo "value-shown=$(has $DHU after1 R1VALUE)" | tee -a $OUT/R1a.after1
cue "HS2: tap the 'Make' row. Press Enter here"; read _
sleep 2; ime $DHU | tee $OUT/R1a.ime-open2; udump $DHU open2 | tee $OUT/R1a.open2; shot open2
#  if ime-open2 reads false: cue "HS3: tap the text field", then repeat the ime line
cue "HS4: tap the hint text of the Make dialog, not the field. Press Enter here"; read _
sleep 2; mark R1a-body; ime $DHU | tee $OUT/R1a.ime-body; udump $DHU body | tee $OUT/R1a.body; shot body
adb -s $DHU shell input keyevent 4; sleep 1; adb -s $DHU shell input keyevent 4; sleep 1
mark R1a-end; adb -s $DHU shell am force-stop $PKG; cap_stop; th_report R1a
```

**PASS** when all of:

1. `R1a.open1` reads `edittext=1` and `R1a.ime-open` reads `mInputShown=true` (the dialog was up
   with its keyboard).
2. After Enter, `R1a.after1` reads `edittext=0` and `value-shown=` 1 or more (Enter pressed OK and
   the row took the value).
3. `R1a.ime-open2` reads `mInputShown=true`, then `R1a.ime-body` reads `mInputShown=false` and
   `R1a.body` reads `edittext=1` (the body tap hid the keyboard and left the dialog open).
4. `R1a.body` prints `button1` bounds, not `ABSENT` (OK is on screen with the keyboard down).
5. No `FATAL EXCEPTION` in the capture.

Record the `button1` bounds in `R1a.open2` (OK with the keyboard up) and the panel size from
`adb -s $DHU shell wm size`: whether OK stayed on screen above the keyboard is an observation, not
a condition, because it depends on this panel's height.

**R1b, D-SAM (API 19).** Install C on D-SAM (`adb -s $SAM install -r $OUT/apk/C.apk`, then the md5
check). Write `onboarding-version` 2 and `log-level` 2 with the D-SAM method. Set `DHU=$SAM`,
`RUN=R1b`, and run R1a's steps with `R1b` labels, D-SAM's own `uiautomator` bounds (D-SAM-and-D-HP
quirk: tap the dump's bounds, not the screenshot's). Set `DHU=27870808938846` after.

**PASS** on the same five conditions. If D-SAM's keyboard never reads `mInputShown=true`, conditions
1 and 3 are INCONCLUSIVE and 2, 4 and 5 still grade.

### R4. Home screen: Self Mode stays live during a session (C)

Force-stop the leftover apps named in section 3. `RUN=R4; K=$KEYS_R4`, then the bring-up. Then the
recipe from pr-1042 round 1, unchanged except for the last block:

```bash
mark R4-pip; adb -s $DHU shell input keyevent 4; sleep 2
cue "HS6: tap Picture-in-Picture on D-HU. Press Enter here"; read _
sleep 3; adb -s $DHU shell am start -n $MAIN; sleep 4
mark R4-home; focus | tee $OUT/R4.focus-home; pinned | tee $OUT/R4.pinned
viewflags home "$HOME_IDS"; shot home
cue "HS7: tap the Self Mode button (To Android Auto) on D-HU. Press Enter here"; read _
sleep 3; focus | tee $OUT/R4.focus-after; mark R4-pressed
```

Then the session end, except: do **not** run `clean_hu` before the last read. Replace the session
end with:

```bash
mark R4-x-go; send ACTION_DISCONNECT > $OUT/R4-x.reply; sleep 8; mark R4-x-done
adb -s $DHU shell am start -n $MAIN; sleep 4; mark R4-after-exit
viewflags exit "$HOME_IDS"; shot exit
mark R4-end; clean_hu; cap_stop; th_report R4; PHCAP=
```

**State reached** when: `$OUT/R4.pinned` is 1 or more; `$OUT/R4.focus-home` contains `MainActivity`;
`hwin R4-pip R4-home` has `HomeFragment: onResume. isConnected=true` 1 or more and
`MainActivity: Active session detected, bringing projection to front` 0. If not, R4 is INCONCLUSIVE.

**PASS** when the session is valid, the state was reached, and all of:

1. `R4.home.flags`: `self_mode_button` reads `enabled=yes clickable=yes`.
2. `$OUT/R4.focus-after` contains `AapProjectionActivity` (the press raised the projection).
3. `R4.exit.flags`: `self_mode_button` reads `enabled=no` (the button dims again once the session
   ends, without a settings change).

Record: `usb_button` and `wifi_button` in both flag files. Expected: usb `enabled=no` in both, wifi
`enabled=yes` in both. An unticked USB button stays dimmed during a session in this change; that is
by design here, not a FAIL.

**FAIL** when the state was reached and condition 1 reads `enabled=no` (the pr-1042 round 1 result
again), or condition 3 reads `enabled=yes`.

### R5. Night mode follows the system (C)

`RUN=R5; K=$KEYS_R5`, then the bring-up. Then:

```bash
mark R5-dark; adb -s $DHU shell cmd uimode night yes; sleep 10; mark R5-dark-done; shot dark
mark R5-light; adb -s $DHU shell cmd uimode night no; sleep 10; mark R5-light-done; shot light
```

Then the session end. Then restore the night setting the precheck recorded
(`adb -s $DHU shell cmd uimode night <value>`) and read it back.

**PASS** when the session is valid and all of:

1. `hwin R5-dark R5-dark-done` has `NightModeManager: SYSTEM uiMode=32 isNight=true` 1 or more and
   `NightMode update: true` 1 or more.
2. `hwin R5-light R5-light-done` has `NightModeManager: SYSTEM uiMode=16 isNight=false` 1 or more and
   `NightMode update: false` 1 or more.
3. `hwin R5-arm R5-dark | c '[ServiceDiscovery] Announcing NIGHT sensor support. Strategy: SYSTEM'`
   is 1 or more (the stored value 8 was read as SYSTEM).

**INCONCLUSIVE** when `cmd uimode night yes` prints an error or `adb -s $DHU shell cmd uimode night`
does not read back `yes` after it: then the unit did not change its night mode and the app had
nothing to follow. **FAIL** when the unit's mode changed and condition 1 or 2 does not hold.

Record: `R5.dark.png` and `R5.light.png` (whether the projected Android Auto turned dark, then
light). That is visual and unconfirmed by the scripts.

### R2. Self Mode leaves the media session to the local player

**R2b, ordinary session on D-HU (C).** This is the control for the change's other half: an
ordinary session must still own the media session. `RUN=R2b; K=$KEYS_R2B`, then the bring-up. Then:

```bash
mark R2b-ms; msess $DHU | tee $OUT/R2b.hu-ms
mark R2b-keys; adb -s $DHU shell input keyevent 126; sleep 3; msess $POCO | tee $OUT/R2b.ph-ms1
adb -s $DHU shell input keyevent 85; sleep 3; msess $POCO | tee $OUT/R2b.ph-ms2; mark R2b-keys-done
```

Then the session end.

**PASS** when the session is valid and all of:

1. `R2b.hu-ms` shows `package=com.andrerinas.headunitrevived` with `active=true`.
2. `hwin R2b-arm R2b-keys-done | c 'MediaSession: State updated to '` is 1 or more.
3. `hwin R2b-keys R2b-keys-done` has `TX Key -> AA=126` and `TX Key -> AA=85`, each 1 or more.
4. The phone's playing app changes state between `R2b.ph-ms1` and `R2b.ph-ms2` (3 then 2, or 2 then
   3). If no app on D-POCO reports a playback state at all, condition 4 is INCONCLUSIVE and 1 to 3
   still grade.

**R2a, Self Mode on D-POCO (C).** D-HU's app force-stopped first. Install C on D-POCO
(`adb -s $POCO install -r $OUT/apk/C.apk`, md5 check). Write the D-POCO keys. Set `DHU=$POCO`,
`RUN=R2a`. HS9 first if the precheck found no head unit server.

```bash
adb -s $POCO shell cmd connectivity airplane-mode disable; sleep 5
th_gate; cap_start; mark R2a-start
adb -s $POCO shell monkey -p com.spotify.music 1; sleep 5        # or VLC on a folder, whichever the precheck found
adb -s $POCO shell input keyevent 126; sleep 3
msess $POCO | tee $OUT/R2a.ms-before                              # the player reads state=3
#  if no player reads state=3: cue "HS8: press play in the player", then repeat the msess line
adb -s $POCO shell am start -n $MAIN; sleep 3
mark R2a-self; L=$(lines); send ACTION_START_SELF_MODE
waitline 60 'SSL handshake complete' "$(HF)" $L; echo "ssl=$?" | tee -a $OUT/R2a.summary
sleep 15; mark R2a-ms; msess $POCO | tee $OUT/R2a.ms-session
mark R2a-keys; adb -s $POCO shell cmd media_session dispatch play-pause; sleep 3; msess $POCO | tee $OUT/R2a.ms-k1
adb -s $POCO shell cmd media_session dispatch play-pause; sleep 3; msess $POCO | tee $OUT/R2a.ms-k2; mark R2a-keys-done
mark R2a-end; clean_hu; cap_stop; th_report R2a
```

The key goes in with `cmd media_session dispatch`, not `input keyevent`: in Self Mode our projection
window has focus on D-POCO and would take an `input keyevent` itself, which is not the path this
change touches. `dispatch` hands the key to the system's media-button session, which is exactly what
the change decides. `clean_hu` here aims at `$DHU`, which is D-POCO for this run. One capture carries both sides, because
the app and Android Auto run on the same device.

**PASS** when `ssl=0`, `hwin R2a-self R2a-ms | c 'SelfMode: AA 17.4+ detected'` is 1 or more, and all
of:

1. **The point of R2:** `R2a.ms-session` shows `package=com.andrerinas.headunitrevived` with
   `active=false`.
2. `R2a.ms-session`'s `Media button session is` line names the player, not our package.
3. The player's state changes on each key: `R2a.ms-k1` differs from `R2a.ms-session` and `R2a.ms-k2`
   differs from `R2a.ms-k1` (3 to 2 to 3).
4. `hwin R2a-keys R2a-keys-done | c 'TX Key -> AA=85'` is 0 (the key did not go into the loopback
   session). Record `hwin R2a-keys R2a-keys-done | c 'Runtime MEDIA_BUTTON'` too.

If `ssl=1`, re-run once after HS9; a second `ssl=1` makes R2a and R2c UNTESTABLE (Gearhead's head
unit server wedge, `self-mode-media-session-round1-results.md` Setup notes).

**R2c, the control (A on D-POCO).** Install A on D-POCO (`install -r -d`, md5 check). `RUN=R2c` and
R2a's steps with `R2c` labels. **Expected:** `R2c.ms-session` shows our package with `active=true`,
as `self-mode-media-session` round 1 measured on `main`. That difference against R2a's `active=false`
is what proves R2a measured the change. If R2c also reads `active=false`, R2a's condition 1 proves
nothing: report both and mark R2a INCONCLUSIVE on condition 1.

Leave C installed on D-POCO at the end (`install -r`, md5 check), and set `DHU=27870808938846`.

### Stop rule

- A setup failure (wrong commit, a missing `AutomationReceiver: ` line, a key that did not read back)
  is re-run once. A second setup failure makes that run UNTESTABLE.
- Every run runs at most 2 times, and the second time only after a setup failure or a discard.
- No run is repeated to turn a FAIL into a PASS.
- Discard rule for every session run: a second `SSL handshake complete` between the arm marker and
  the exit marker, or `MATCH! Starting AapService` 1 or more in that window. A discarded run is
  re-run once.

End of round: restore each unit's `settings.xml` from its backup (D-HU as root, D-HU quirk; D-SAM by
`run-as cp`), diff each against its backup, restore D-HU's night mode, and put D-POCO back in
airplane mode.

## 8. Do not re-run

- The home screen button states with no session, the legacy single-choice migration and the
  Customization screen. `pr-1042-disabled-home-buttons-round1-results.md` R1 to R3 passed them, and
  `94588411` does not touch those paths.
- `main`'s own Self Mode media behaviour beyond R2c's one reading. `self-mode-media-session` rounds 1
  and 2 measured it.
- The candidate compiles and its JVM suite is green (3208 tests on the review host); R0 rebuilds it
  anyway as the gate.

## 9. Report back

1. **R3:** `mInputShown=` on the phone in R3A and in R3C, and whether `abc` appeared in the projected
   search box. This decides whether the letter-keycode change ships as a fix.
2. **R2a against R2c:** our package's `active=` in each, and whether the player changed state on
   each key in R2a.
3. **R4:** `self_mode_button`'s `enabled=` during the session and after the exit, and where HS7 left
   the focus.
4. **R1a, R1b, R5, R2b:** the verdict and the numbers each condition named.

Also give D-POCO's Gearhead `versionName`, both APK md5s, the `shared_prefs` `ls -ld` line, every
hand step from `hand-steps.log`, and the four session-validity numbers for each session run.

## 10. Decisive strings

Every log string the runs grep, one per line. Lines 1 to 14 are ours and each matched `git grep -F`
in `app/src/main` at `1fc51814`. Lines 15 and 16 are Gearhead's.

```
AutomationReceiver: 
AutomationMarker: 
SSL handshake complete
Throughput over 
CommManager: TX Key -> AA=
Received request to resend night mode state
MediaSession: State updated to 
Runtime MEDIA_BUTTON ignored in Self-Mode
SelfMode: AA 17.4+ detected. Connecting directly to Headunit Server
NightModeManager: SYSTEM uiMode=
NightMode update: 
[ServiceDiscovery] Announcing NIGHT sensor support. Strategy: 
HomeFragment: onResume. isConnected=
MainActivity: Active session detected, bringing projection to front
received ByeByeRequest
Critical error
```
