# pr-1042-disabled-home-buttons, round 1 brief: grey out the connection buttons the user did not choose

Publish name on `transfer/rig-rounds`: `pr-1042-disabled-home-buttons-round1-brief.md`. Results go in
`pr-1042-disabled-home-buttons-round1-results.md`. Evidence goes in a new release
`rig-evidence-pr-1042-disabled-home-buttons` as the asset
`pr-1042-disabled-home-buttons-round1-captures.zip`.

An outside contributor's change greys out and disables the home screen's Self Mode, USB and WiFi
buttons when that connection type is not ticked in Settings. The Settings button never changes. The
label under each button stays visible at 40% opacity. The Customization screen greys the same
previews and hides the colour rows for unticked types. One build, D-HU only for R1 to R3, D-HU and
D-POCO for R4 and R5. About 90 minutes plus one build.

**The point of the round is R4**: a live Native AA session, the projection in picture-in-picture,
the home screen in front, and only WiFi ticked. R5 is its positive control.

## 1. Build and baseline

| Arm | Ref | SHA | `commit` in `ACTION_QUERY_STATE` |
|---|---|---|---|
| **P** (change as pushed) | upstream `pull/1042/head` | `3404e4e41e1ea6af91eb58ab80fcbf7fbef74288` | `3404e4e41e1e` |

P is two commits (`8b1fb729`, `3404e4e4`) on upstream `93c065dd` (2026-09-30). It is built as pushed,
not merged with today's `main`. No baseline arm is built: R5 is a settings change on P, and it is the
control. This is the thread's first round. No history was rewritten.

```bash
git fetch https://github.com/andreknieriem/open-headunit.git pull/1042/head
git cat-file -e 3404e4e41e1ea6af91eb58ab80fcbf7fbef74288 && echo P-ok
git worktree add ../p1042-P 3404e4e41e1ea6af91eb58ab80fcbf7fbef74288
cp local.properties ../p1042-P/
```

If the `echo` line does not print, stop the round.

### R0. Build and identity gate

Make `hur-wifi-test-scripts/pr-1042-disabled-home-buttons-round1/` and set `OUT` to it. Wait for the
host to cool (`th_wait 70`), build, and copy the APK out at once (tooling quirk: `build_hur.sh`
deletes the previous APK). `build_hur_cool.sh` or `build_hur.sh` is fine if it builds the worktree
named here; the contract is this:

```bash
cd ../p1042-P && ./gradlew :app:assembleGithubDebug --max-workers=2 && mkdir -p $OUT/apk && cp app/build/outputs/apk/github/debug/*.apk $OUT/apk/P.apk
cd ../p1042-P && ./gradlew :app:testGithubDebugUnitTest --max-workers=2
md5sum $OUT/apk/P.apk
unzip -p $OUT/apk/P.apk 'classes*.dex' | strings | grep -cF 'applyButtonVisibility'
AAPT2=$(ls -d $ANDROID_HOME/build-tools/*/aapt2 | tail -1)
$AAPT2 dump resources $OUT/apk/P.apk | grep -a 'id/customizationFragment'
```

**PASS when all hold:** the build succeeds; the unit tests report 0 failures and 0 errors; the DEX
count reads 1 or more; the `aapt2` line prints a resource id. Record that id (for example
`0x7f0a00c4`) as `CUST_ID`; R3 needs it. After the install, `state` must show `commit` equal to the
table. **A failure in R0 stops the round.**

## 2. What this is and why

The ticket's `diagnosis.md` found one defect by reading the code and one product question.

- **H1, the defect.** While a session is live, the Self Mode button's label reads "To Android Auto"
  and a press opens the projection (`HomeFragment`, `self_mode_button` click,
  `updateProjectionButtonText`). The USB button does the same. The WiFi button does nothing while
  connected. P disables Self Mode on `!showsSelf()` and USB on `!showsUsb()`, with no regard to the
  live session. So a WiFi-only user with a live session gets a dimmed "To Android Auto" that does
  nothing, and no home screen button leads back to the projection.
- **Where H1 can show.** `MainActivity.onResume` raises the projection by itself when a session is
  live, unless `App.isPiPActive`. So the home screen is in front with a live session only while the
  projection is in picture-in-picture. R4 builds exactly that state.
- **H2, the product question.** `Settings.connectionModes` migrates the old single choice once: an
  absent `connection-modes` with `primary-connection` 2 (WiFi) or 3 (Native AA) becomes `{wifi}`.
  Before P that set only hid settings rows. On P it also greys home buttons the user never unticked.
  R2 measures it. It is the owner's decision, so R2 records and does not fail the PR.

The rest (R1, R3) checks that the change does what its description says.

## 3. What is different about this round

- **The verdicts read view flags, not pixels.** `adb shell dumpsys activity top` prints every view
  of the resumed activities as `ClassName{hash FLAGS ... app:id/<name>}`. In the 9-character FLAGS
  token, character 1 is visibility (`V` visible, `I` invisible, `G` gone), character 3 is `E` when
  the view is enabled and `.` when it is not, and character 7 is `C` when it is clickable. This is
  `View.toString`, so it needs no scroll and no idle screen. `uiautomator dump` is not used: it can
  wait forever for an idle state while video plays in a picture-in-picture window.
- **Label opacity is not in any dump.** The reviewer's request (labels stay visible, dimmed) is
  graded on the label's visibility flag `V`. The 40% dimming is judged from the screenshot and is
  recorded as unconfirmed by the scripts.
- **R4 is expected to FAIL on P.** That FAIL is the hardware proof of H1. It is pre-registered, not
  a surprise. A P run where the Self Mode button reads enabled in R4 is the unexpected result.
- **R5 runs before R4.** R5 proves that this rig can reach the state (session live, projection
  pinned, home screen in front). If R5 cannot reach it, R4 is INCONCLUSIVE.
- **D-POCO stays in airplane mode for R1 to R3.** No session is wanted there. Writing
  `native-poke-all-paired` false with an empty wake list keeps D-HU from poking any phone (tooling
  quirk: the default pokes every bonded phone).
- **D-HU's `shared_prefs/` can be root-owned** (D-HU quirk). R2 grades a value the app writes. Record
  `ls -ld /data/data/com.andrerinas.headunitrevived/shared_prefs` once, and `chown` it to the app's
  uid:gid if it is wrong. Say what it read first in Setup notes.
- **The rig's audio settings are a deliberate worst case.** Never write `use-aac-audio`,
  `audio-latency-multiplier`, `audio-queue-capacity` or `enable-audio-sink`. Read them back and
  record them.
- **Never run adb calls in parallel against one unit.** The background logcat captures are the only
  exception.

### Hand steps

Each one is a press on our own app's UI. The template forbids that unless a brief names it, so these
are named here. `cue` prints `OPERATOR_STEP` and logs it in `$OUT/hand-steps.log`.

| Id | When | What the operator does | Why there is no verb |
|---|---|---|---|
| HS1 | R4 and R5, after `input keyevent 4` opens the projection's exit dialog | On D-HU, press the dialog row "Picture-in-Picture" | No automation verb enters picture-in-picture; `AapProjectionActivity.enterPiP` is reached only from that dialog row |
| HS2 | R4 and R5, after the button flags are read | On D-HU's home screen, press the Self Mode button (the one whose label reads "To Android Auto") | The button is the control under test. `ACTION_RAISE_PROJECTION` goes through the service and never touches the button |

The verdicts rest on the view flags. HS2 adds what a user would see after the press; it corroborates
and is graded only in R5.

## 4. Settings keys

Write with the app stopped. Take a fresh backup first (`prefs_cat`), and state the delta against it
in Setup notes. Write keys with `hu_put` (root, then `chown` and `chmod 660`) from `ohu_lib.sh`, in
the key syntax below, which `pr-1001-devserver-p2p` round 1 used. Read every key back before the
launch. Restore D-HU from its backup at the end and diff it against the backup.

```bash
adb -s $DHU shell am force-stop $PKG
prefs_cat > $OUT/settings-backup-DHU.xml; BASEXML=$OUT/settings-backup-DHU.xml
```

`$MAC` is D-POCO's Bluetooth address. Take it from D-HU's
`dumpsys bluetooth_manager` "Bonded devices" list and record it in Setup notes.

```bash
BASE1="int:wifi-connection-mode=3 int:native-ap-transport=0 int:native-driver-selection-mode=0 bool:native-poke-all-paired=false set:native-poke-bt-macs= bool:auto-start-self-mode=false bool:auto-connect-last-session=false int:log-level=2 int:onboarding-version=2 del:video-profile-starvation-cap"
# R1 cases: the connection-modes value is the only thing that changes
KEYS_R1A="$BASE1 set:connection-modes=wifi"
KEYS_R1B="$BASE1 set:connection-modes=usb"
KEYS_R1C="$BASE1 set:connection-modes=self"
KEYS_R1D="$BASE1 set:connection-modes="
KEYS_R1E="$BASE1 set:connection-modes=usb,wifi,self"
# R2: no connection-modes key at all, legacy single choice WiFi
KEYS_R2="$BASE1 del:connection-modes int:primary-connection=2"
# R3: Customization screen, WiFi only
KEYS_R3="$BASE1 set:connection-modes=wifi"
# R4, R5: a Native AA session with D-POCO
BASE45="int:wifi-connection-mode=3 int:native-ap-transport=0 int:native-driver-selection-mode=0 bool:native-poke-all-paired=false set:native-poke-bt-macs=$MAC str:last-connected-native-mac=$MAC str:native-preferred-device-mac=$MAC bool:auto-start-self-mode=false bool:auto-connect-last-session=false bool:kill-on-disconnect=false int:log-level=2 int:onboarding-version=2 del:native-aa-wake-damage-verdict del:video-profile-starvation-cap"
KEYS_R4="$BASE45 set:connection-modes=wifi"
KEYS_R5="$BASE45 set:connection-modes=wifi,self"
```

**Check two writes by reading them back.** `set:connection-modes=` must give an empty set element,
`<set name="connection-modes" />`. `set:native-poke-bt-macs=` must give the same shape for its key.
If `hu_put` writes anything else for either, fix the empty-set case in `ohu_setkeys.py`, say so in
Setup notes, and carry on. If it cannot be fixed, mark R1-D UNTESTABLE (R1-E covers "all enabled").

`ACTION_LOG_MARKER` is not in `AutomationCommandPolicy.CONFIGURING` at P, so no
`allow-external-configuration` is needed. Every line this round greps is INFO or WARN, so
`log-level` 2 carries all of them.

## 5. Helpers and verbs

Copy `ohu_lib.sh`, `ohu_setkeys.py` and `ptr_lib.sh` into `$OUT` from
`hur-wifi-test-scripts/projection-teardown-and-relays-round3/`, as `pr-1001-devserver-p2p` round 1
did. From them use only `prefs_cat`, `hu_put`, `apk_check`, `th_wait`, `th_gate` and `th_report`.
Save the two files below in `$OUT` and source `p1042_lib.sh` last, so its functions win.

`$OUT/flags.py`, which reads `dumpsys activity top` output and prints one line per view id asked for:

```python
import re, sys
want = sys.argv[2:]
seen = {}
for line in open(sys.argv[1], encoding="utf-8", errors="replace"):
    m = re.search(r"\{[0-9a-f]+ (\S{9}) .*?id/([A-Za-z0-9_]+)\}", line)
    if m and m.group(2) in want and m.group(2) not in seen:
        f = m.group(1)
        seen[m.group(2)] = "vis=%s enabled=%s clickable=%s" % (
            {"V": "visible", "I": "invisible", "G": "gone"}.get(f[0], f[0]),
            "yes" if f[2] == "E" else "no", "yes" if f[6] == "C" else "no")
for w in want:
    print("%s %s" % (w, seen.get(w, "ABSENT")))
```

`$OUT/p1042_lib.sh`:

```bash
# p1042_lib.sh : source after ohu_lib.sh and ptr_lib.sh. Needs OUT and RUN.
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
MAIN=$PKG/com.andrerinas.openheadunit.main.MainActivity
SETA=$PKG/com.andrerinas.openheadunit.main.SettingsActivity
DHU=27870808938846; POCO=4f4027e9; HU=$DHU; PH=$POCO
HOME_IDS="self_mode_button usb_button wifi_button settings_button self_mode_text usb_text wifi_text"
CUST_IDS="preview_btn_self_mode preview_btn_usb preview_btn_wifi preview_btn_settings row_color_self_mode row_color_usb row_color_wifi row_color_settings"
send() { local a=$1; shift; adb -s $DHU shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; sleep 0.3; }
mark() { send ACTION_LOG_MARKER --es text "$1" >/dev/null; if [ -n "$PHCAP" ]; then adb -s $POCO shell log -t OHURIG "$1"; sleep 0.3; fi; }
HF() { echo "$OUT/$RUN.hu.txt"; }
PF() { echo "$OUT/$RUN.ph.txt"; }
cap_start() {
  adb -s $DHU logcat -G 16M; adb -s $DHU logcat -c
  stdbuf -oL adb -s $DHU logcat -v time > "$(HF)" & echo $! > "$OUT/$RUN.hu.pid"
  if [ -n "$PHCAP" ]; then
    for t in CAR.GAL CAR.GAL.GAL.LITE; do adb -s $POCO shell setprop log.tag.$t VERBOSE; done
    adb -s $POCO logcat -G 16M; adb -s $POCO logcat -c
    stdbuf -oL adb -s $POCO logcat -v time > "$(PF)" & echo $! > "$OUT/$RUN.ph.pid"; fi
  sleep 1; }
cap_stop() { sleep 1; for f in "$OUT/$RUN.hu.pid" "$OUT/$RUN.ph.pid"; do [ -f "$f" ] && kill "$(cat "$f")" 2>/dev/null; rm -f "$f"; done; }
_win() { awk -v a="$1$2" -v b="$1$3" 'function at(s){i=index($0,s); return i>0 && substr($0,i+length(s))~/^[[:space:]]*$/} at(a){on=1} on{print} on&&at(b){exit}' "$4"; }
hwin() { _win "AutomationMarker: " "$1" "$2" "$(HF)"; }
pwin() { _win "OHURIG: " "$1" "$2" "$(PF)"; }
c() { grep -acF -- "$1"; }
rend() { grep -acE 'Throughput over [0-9]+ms: rendered=[1-9]'; }
lines() { wc -l < "$(HF)"; }
waitline() { local s=$1 str=$2 f=${3:-$(HF)} from=${4:-0} i
  for i in $(seq 1 "$s"); do tail -n +"$((from+1))" "$f" | grep -aqF -- "$str" && return 0; sleep 1; done; return 1; }
focus() { adb -s $DHU shell dumpsys window | grep -a -m1 mCurrentFocus; sleep 0.3; }
pinned() { adb -s $DHU shell dumpsys activity activities | grep -a -ci pinned; sleep 0.3; }
# viewflags TAG IDS : dump the resumed activities' views, save them, print the flags of IDS
viewflags() { adb -s $DHU shell dumpsys activity top > "$OUT/$RUN.$1.top.txt"; sleep 0.3
  python3 -I "$OUT/flags.py" "$OUT/$RUN.$1.top.txt" $2 | tee "$OUT/$RUN.$1.flags"; }
shot() { adb -s $DHU shell screencap -p /sdcard/s.png; sleep 0.3; adb -s $DHU pull /sdcard/s.png "$OUT/$RUN.$1.png" >/dev/null; sleep 0.3; }
state() { send ACTION_QUERY_STATE | grep -a 'data='; }
cue() { echo "$(date +%T) OPERATOR_STEP $1"; printf '\a'; echo "$(date +%T) $1" >> "$OUT/hand-steps.log"; }
clean_hu() { adb -s $DHU shell am start -a android.intent.action.VIEW -d "headunit://exit" >/dev/null; sleep 3; adb -s $DHU shell am force-stop $PKG; }
```

`clean_hu` is only for a running app: `headunit://exit` on a stopped app starts the service, and its
`onCreate` arms the stack.

Install with `adb -s $DHU install -r $OUT/apk/P.apk`, set `WANT_MD5`, run `apk_check`, then `state`.

**Verbs and commands used, and nothing else acts on the app:**

| Action | Command |
|---|---|
| arm Native AA, as the WiFi button | `send ACTION_START_WIRELESS_SCAN` |
| user exit from the projection | `send ACTION_DISCONNECT` |
| marker | `mark <label>` |
| build identity | `state` |
| launch, or bring the home screen in front | `adb -s $DHU shell am start -n $MAIN` |
| open the Customization screen | `adb -s $DHU shell am start -n $SETA --ei extra_destination $((CUST_ID))` |
| open the projection's exit dialog | `adb -s $DHU shell input keyevent 4` (BACK; a scripted keyevent) |

Every verb must print `AutomationReceiver: ` in the head unit capture. A step with no such line never
landed: that step is void, not a FAIL.

### Precheck, once before R4

Apps stopped. Record the output in Setup notes.

```bash
adb -s $POCO shell dumpsys package com.google.android.projection.gearhead | grep -a -m1 versionName
adb -s $DHU shell dumpsys bluetooth_manager | grep -a -A8 'Bonded devices'     # D-POCO listed; take $MAC
adb -s $POCO shell dumpsys wifip2p | grep -a -m2 -E 'isGroupOwner|groupFormed'  # isGroupOwner false
adb -s $DHU shell dumpsys wifip2p | grep -a -m2 -E 'isGroupOwner|groupFormed'
adb -s $DHU shell getprop ro.build.version.sdk                                   # 26 or more for picture-in-picture
```

## 6. The deciding lines

Every app string below matched `git grep -F` in `app/src` at `3404e4e4`.

| Line (grep -F) | Source | Used for |
|---|---|---|
| `AutomationReceiver: ` | `AutomationReceiver` | a verb landed |
| `AutomationMarker: ` | `AutomationEffectRunner` | markers |
| `HomeFragment: onResume. isConnected=` | `HomeFragment.onResume` | the home screen resumed; the line ends `true` or `false`. `true` means the Self Mode label was set to "To Android Auto" |
| `MainActivity: Active session detected, bringing projection to front` | `MainActivity.onResume` | must be 0 after the home screen is brought in front in R4 and R5; any hit means picture-in-picture was not active |
| `SSL handshake complete` | `AapSslContext` (INFO) | a session formed |
| `Throughput over ` | `VideoDecoder` | picture; regex `Throughput over [0-9]+ms: rendered=[1-9]` |
| `AapService: Disconnected.` | `AapService` | session end; must be 0 before the exit marker |
| `Disconnect action received.` | `AapService` | the user exit landed |
| `AapService: Force-starting WIFI-Scan from UI` | `AapService` | the arm verb landed |
| `MATCH! Starting AapService` | `AutoStartReceiver` | discard check: record |

One Gearhead line, graded on D-POCO's capture. It is not in our source. `pr-1001-devserver-p2p`
round 1 used it on D-POCO's 17.9.

| Line | Where | Used for |
|---|---|---|
| `received ByeByeRequest` | phone capture (`CAR.GAL.GAL.LITE`, set VERBOSE by `cap_start`) | our exit reached a live session on the phone |

Two framework outputs, not log lines: the view FLAGS token from `dumpsys activity top` (section 3)
and the word `pinned` in `dumpsys activity activities`.

## 7. Runs

Every run: `th_gate` first, then the steps in order. Report `th_report $RUN` in each section.
Order: R1 (A to E), R2, R3, R5, R4.

### R1. Home screen button states, five selections, no phone

Run ids `R1-A` to `R1-E`. D-POCO in airplane mode for the whole run.

```bash
for X in A B C D E; do
  RUN=R1-$X; eval KEYS=\$KEYS_R1$X
  adb -s $DHU shell am force-stop $PKG; hu_put "$BASEXML" $KEYS     # read back
  th_gate; cap_start; mark $RUN-start
  adb -s $DHU shell am start -n $MAIN; sleep 8
  focus | tee $OUT/$RUN.focus; viewflags home "$HOME_IDS"; shot home
  mark $RUN-end; clean_hu; cap_stop; th_report $RUN
done
```

Expected enabled flags (`enabled=` in `$RUN.home.flags`):

| Case | `connection-modes` | `self_mode_button` | `usb_button` | `wifi_button` | `settings_button` |
|---|---|---|---|---|---|
| A | `wifi` | no | no | yes | yes |
| B | `usb` | no | yes | no | yes |
| C | `self` | yes | no | no | yes |
| D | empty set | yes | yes | yes | yes |
| E | `usb,wifi,self` | yes | yes | yes | yes |

The rule behind the table: a button reads `enabled=yes` when its own type is in the set, or when
the set is empty. `settings_button` always reads yes.

**PASS (each case), all of:**

1. `$RUN.focus` contains `MainActivity`, and `hwin $RUN-start $RUN-end | c 'HomeFragment: onResume. isConnected=false'` is 1 or more.
2. For each of the four buttons, `enabled=` matches the rule, and `clickable=` equals `enabled=`.
3. `self_mode_text`, `usb_text` and `wifi_text` read `vis=visible` in every case (the reviewer's
   request: labels stay on screen).
4. No line reads `ABSENT`.

Record from each screenshot, as unconfirmed: whether a disabled button is grey and its label
dimmer than an enabled one.

### R2. Legacy single choice, no `connection-modes` key (H2)

```bash
RUN=R2
adb -s $DHU shell ls -ld /data/data/$PKG/shared_prefs | tee $OUT/R2.prefsdir   # record; chown if root-owned
adb -s $DHU shell am force-stop $PKG; hu_put "$BASEXML" $KEYS_R2     # read back: no connection-modes key
th_gate; cap_start; mark R2-start
adb -s $DHU shell am start -n $MAIN; sleep 8
focus | tee $OUT/R2.focus; viewflags home "$HOME_IDS"; shot home
mark R2-end; clean_hu; cap_stop; th_report R2
adb -s $DHU shell cat /data/data/$PKG/shared_prefs/settings.xml | grep -a -A3 'connection-modes' | tee $OUT/R2.written
```

**PASS, all of:** the flags equal R1-A's (self no, usb no, wifi yes, settings yes), and
`$OUT/R2.written` shows a `connection-modes` set holding `wifi` alone. If `R2.prefsdir` showed a
root-owned directory that was not fixed, condition 2 is INCONCLUSIVE (D-HU quirk), not FAIL.

A PASS here means H2 is real on hardware: a user who once chose WiFi now has Self Mode and USB
greyed without having unticked them. Report it as a finding; it does not fail the PR.

### R3. Customization screen, WiFi only

```bash
RUN=R3
adb -s $DHU shell am force-stop $PKG; hu_put "$BASEXML" $KEYS_R3     # read back
th_gate; cap_start; mark R3-start
adb -s $DHU shell am start -n $SETA --ei extra_destination $((CUST_ID)); sleep 6
focus | tee $OUT/R3.focus; viewflags cust "$CUST_IDS"; shot cust
adb -s $DHU shell input keyevent 4; sleep 1
mark R3-end; clean_hu; cap_stop; th_report R3
```

**PASS, all of:**

1. `$OUT/R3.focus` contains `SettingsActivity`.
2. `preview_btn_self_mode` and `preview_btn_usb` read `enabled=no`; `preview_btn_wifi` and
   `preview_btn_settings` read `enabled=yes`. All four read `vis=visible`.
3. `row_color_self_mode` and `row_color_usb` read `vis=gone`; `row_color_wifi` and
   `row_color_settings` read `vis=visible`.

If every Customization id reads `ABSENT`, the deep link did not reach the screen: re-run once with
`CUST_ID` re-read from `aapt2`, then mark R3 UNTESTABLE. Do not scroll.

### R5. Positive control: Self Mode ticked, session live, projection pinned

Run before R4. `PHCAP=1` for this run, so D-POCO's capture runs and the markers reach it.

```bash
RUN=R5; PHCAP=1
adb -s $POCO shell cmd connectivity airplane-mode disable; sleep 5
adb -s $POCO shell svc wifi enable; adb -s $POCO shell svc bluetooth enable; sleep 5
adb -s $POCO shell dumpsys bluetooth_manager | grep -a -m2 -iE 'enabled|state'    # on
adb -s $DHU shell am force-stop $PKG; hu_put "$BASEXML" $KEYS_R5     # read back
th_gate; cap_start; mark R5-start
adb -s $DHU shell am start -n $MAIN; sleep 5
mark R5-arm; L=$(lines); send ACTION_START_WIRELESS_SCAN
waitline 120 'SSL handshake complete' "$(HF)" $L; echo "ssl=$?" | tee -a $OUT/R5.summary
sleep 15; focus | tee $OUT/R5.focus-session                      # expect AapProjectionActivity
mark R5-pip; adb -s $DHU shell input keyevent 4; sleep 2
cue "HS1: press Picture-in-Picture on D-HU, then press Enter here"; read _
sleep 3; adb -s $DHU shell am start -n $MAIN; sleep 4
mark R5-home; focus | tee $OUT/R5.focus-home; pinned | tee $OUT/R5.pinned
viewflags home "$HOME_IDS"; shot home
cue "HS2: press the Self Mode button (To Android Auto) on D-HU, then press Enter here"; read _
sleep 3; focus | tee $OUT/R5.focus-after; mark R5-pressed
mark R5-x-go; send ACTION_DISCONNECT > $OUT/R5-x.reply; sleep 8; mark R5-x-done
mark R5-end; clean_hu; cap_stop; th_report R5; PHCAP=
```

If `ssl=1`, the session did not form: re-run once, then mark R5 and R4 UNTESTABLE.

**State reached** when all hold. If any does not, R5 is INCONCLUSIVE and so is R4:

1. `ssl=0`, and `hwin R5-arm R5-pip | rend` is 1 or more.
2. `$OUT/R5.focus-session` contains `AapProjectionActivity`.
3. `$OUT/R5.pinned` is 1 or more.
4. `$OUT/R5.focus-home` contains `MainActivity`.
5. `hwin R5-pip R5-home`: `HomeFragment: onResume. isConnected=true` 1 or more, and
   `MainActivity: Active session detected, bringing projection to front` 0.
6. `hwin R5-start R5-x-go | c 'AapService: Disconnected.'` is 0.

**PASS** when the state was reached and all of:

1. `self_mode_button` reads `enabled=yes clickable=yes`. `usb_button` reads `enabled=no`.
2. `$OUT/R5.focus-after` contains `AapProjectionActivity` (HS2 brought the projection back).
3. Phone: `pwin R5-x-go R5-x-done | c 'received ByeByeRequest'` is 1 or more (the session was live
   on the phone until our exit).

### R4. The point of the round: only WiFi ticked, session live, projection pinned

The same steps as R5 with `RUN=R4`, `KEYS_R4` and every `R5` label changed to `R4`. Run R4 only
when R5 reached its state.

**State reached**: the same six conditions as R5, on R4's files and markers. If any does not hold,
R4 is INCONCLUSIVE.

**PASS** when the state was reached and `self_mode_button` reads `enabled=yes clickable=yes`.
**FAIL** when the state was reached and `self_mode_button` reads `enabled=no`. FAIL is the expected
result on P (H1).

Record in both cases:

- `usb_button` and `wifi_button` flags. Expected: usb no, wifi yes.
- `$OUT/R4.focus-after`: whether HS2 left `MainActivity` in front (the press did nothing) or raised
  `AapProjectionActivity`.
- Phone: `pwin R4-x-go R4-x-done | c 'received ByeByeRequest'`. It must be 1 or more for the result
  to stand; 0 makes R4 INCONCLUSIVE, because the session was not live on the phone.

### Stop rule

- A setup failure (wrong commit, a missing `AutomationReceiver: ` line, a key that did not read
  back) is re-run once. A second setup failure makes that run UNTESTABLE.
- Every run runs at most 2 times, and the second time only after a setup failure or a discard.
- R4 runs once its state is reached. It is not repeated to turn a FAIL into a PASS.
- Discard rule for R4 and R5: a second `SSL handshake complete` between the arm marker and the exit
  marker. A discarded run is re-run once.

End of round: restore D-HU's `settings.xml` from `$BASEXML` as root (D-HU quirk), `chown` and
`chmod` it, diff it against the backup, and put D-POCO back in airplane mode.

## 8. Do not re-run

- The PR compiles: `:app:compileGithubDebugKotlin` merged with `main` at `0cbff004` exited 0 on the
  review host. R0 builds P as pushed.
- `MainActivity.onResume` raising the projection when a session is live and picture-in-picture is
  off. That is `main` behaviour that P does not touch; R5's condition 5 only checks it stays quiet.

## 9. Report back

1. **R4, the point of the round:** `self_mode_button`'s `enabled=` with the state reached, and where
   HS2 left the focus. `enabled=no` with focus left on `MainActivity` confirms H1.
2. **R5:** the same two values. `enabled=yes` and focus on `AapProjectionActivity` proves the setup
   and the button's route.
3. **R1 and R3:** cases that matched the rule out of five, and R3's verdict. Also R2's verdict and
   what `R2.written` showed.

Also give D-POCO's Gearhead `versionName`, the APK md5, every hand step taken (from
`hand-steps.log`), and the screenshot observations on dimming from R1.

## 10. Decisive strings

Every log string the runs grep, one per line. Lines 1 to 10 are ours and each matches `git grep -F`
in `app/src` at `3404e4e4`. Line 11 is Gearhead's, graded on the phone capture.

```decisive-strings
AutomationReceiver: 
AutomationMarker: 
HomeFragment: onResume. isConnected=
MainActivity: Active session detected, bringing projection to front
SSL handshake complete
Throughput over 
AapService: Disconnected.
Disconnect action received.
AapService: Force-starting WIFI-Scan from UI
MATCH! Starting AapService
received ByeByeRequest
```
