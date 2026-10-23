# pr-1076-text-keycodes, round 1 brief: does advertising letter keycodes keep text entry on the head unit

A/B on one pairing, D-HU as head unit with D-POCO over Native AA. Three runs: which keyboard Android Auto opens for a Maps search (R1, the point), what typed letters do (R2), and whether a typed 'n' still reaches Android Auto when a night-mode key is mapped (R3). Every run captures the phone beside the head unit. About 2 hours plus builds.

## 1. Build and baseline

Two APKs. Build each with `build_hur_cool.sh` (or `build_hur.sh` under the thermal gate, `--max-workers=2`, rig-quirks `topics/tooling.md`) and copy it out of `apks/` the moment it is built.

| Arm | Code | Identity | Gate |
|---|---|---|---|
| **B** (baseline) | upstream `main` | commit `0cbff00432d13c164799fe2d20e79444be2c0983` | JVM tests, 0 failures |
| **M** (candidate) | the contributor's branch `fix/alphabetic-keycodes-text-entry`, one commit on `0cbff004` | commit `3b112e5b599c78547c993375dd45f6d48b2ea0a3` | JVM tests, 0 failures, `KeyCodeTest` present with 8 tests |

```bash
git fetch https://github.com/andreknieriem/open-headunit.git main
git fetch https://github.com/Duzttt/open-headunit.git fix/alphabetic-keycodes-text-entry
git checkout -B arm-B 0cbff00432d13c164799fe2d20e79444be2c0983
git checkout -B arm-M 3b112e5b599c78547c993375dd45f6d48b2ea0a3
git merge-base arm-M arm-B        # MUST print 0cbff00432d13c164799fe2d20e79444be2c0983
```

The branch sits directly on `0cbff004`, so no merge is needed and M differs from B by the change alone. If the fetch of the contributor's branch fails, use the pull-request ref instead: `git fetch https://github.com/andreknieriem/open-headunit.git pull/1076/head` and check out `3b112e5b`. If the SHA differs, stop the round. A build or test failure stops the round.

**Identity, per arm, before the first run on it.** Pull the installed APK and hash it locally (`apk_check`). Then count the new constant in the DEX:

```bash
unzip -p $OUT/live.apk 'classes*.dex' | strings | grep -cF 'KEY_NIGHT_MODE'     # B: 0, M: 1 or more
```

Also `send ACTION_QUERY_STATE` and record `commit` (B starts `0cbff004`, M starts `3b112e5b`). A mismatch voids that arm's runs: reinstall once, then mark them UNTESTABLE.

## 2. What this is and why

Users report that a text field focused on the head unit, for example the Maps search box, opens the **phone's** keyboard and turns the phone screen on, instead of a keyboard on the head unit. A second report says a tap on Search in Maps does nothing at all. The change under test claims to fix both.

What the change does:

- Adds `KEYCODE_A` to `KEYCODE_Z`, `DEL`, `FORWARD_DEL`, `ESCAPE`, 13 punctuation codes and 7 modifier codes to `KeyCode.supported`. That list goes to the phone in the service discovery response, and it is also the filter in `KeyCode.convert`. On B a letter is converted to `KEYCODE_UNKNOWN` before it is sent; on M it goes through.
- Moves night mode off `KEYCODE_N` (42) to a new internal code, `KEY_NIGHT_MODE = 65539`, and adds that code to the advertised list too. In Google's AAP schema 65539 is `KEYCODE_RADIO`.
- Remaps a logical 42 to 65539 when `key-codes` holds a night-mode mapping, to keep old mappings working.

Our review found three things that only hardware can settle, one per run:

1. **The premise.** An earlier round (`aa-numeric-keyboard-round1-results.md`) ran an older build of ours on D-HU with D-POCO. That build advertised no letter codes, and every text field in a car-app template still opened a full keyboard **on the head unit**. So letter codes may not be what decides the keyboard. That round used a template field, never Maps. R1 asks the question on Maps, on both arms.
2. **Typing.** With the change, a key from a physical keyboard reaches Android Auto as a letter. Whether Android Auto types it into the focused field is unknown. Every key goes out with `metastate = 0`, so Shift may do nothing. R2 types into the field.
3. **The night-mode remap swallows 'n'.** The remap fires on "a night mapping exists", not on "this key is the mapped one". So with any night mapping stored, a typed 'n' (42) becomes 65539. `AapTransport` turns 65539 into a local broadcast that only resends the night state, and nothing goes to the phone. On B every 'n' is swallowed that way, mapped or not. R3 measures it.

**Keyboards, from the phone's side** (both measured in `selfmode-keyboard-viewmode-round1-results.md`):

- Android Auto's **car keyboard** is drawn into the projected video. The phone logs a `TouchInputMethod` window (`CAR.WM ... :TouchInputMethod`).
- The **phone keyboard** is a phone activity. The phone logs `START u0 {cmp=.../PhoneKeyboardActivity}` and `Displayed .../PhoneKeyboardActivity`. A phone app's own keyboard on the phone shows as `mInputShown=true` in the phone's `dumpsys input_method`.

## 3. What is different about this round

- **Two devices, both on USB adb:** D-HU (`27870808938846`) and D-POCO (`4f4027e9`). Keep D-SAM's Bluetooth off (rig-quirks `units/D-POCO.md`: a stale bond can stop the wake). Check D-POCO's bonded list once and record it.
- **Taps into the projected video are the only way to reach Maps' search field**, and they are allowed (template section 3, the standing exception). `uiautomator` sees one opaque view there, so every tap uses a coordinate read off a `screencap` of D-HU (the method `aa-numeric-keyboard-round2b` used). Two known quirks: a tap above y 110 opens our own brightness and volume overlay, and the Android Auto app grid opens on the second tap of the bottom-left rail icon. Never tap the keyboard's own "use phone keyboard" key.
- **`adb -s $HU shell input keyevent` stands in for a physical keyboard** in R2 and R3. It goes through `AapProjectionActivity.dispatchKeyEvent` and `CommManager.sendKey` exactly as a USB or Bluetooth keyboard does. Never send `KEYCODE_BACK` (4) to D-HU in this round: it reaches our activity, not Maps.
- **Screenshots are evidence here.** Two conditions (the keyboard on the head unit screen, the text in the field) can only be read off a screenshot. The host reads those screenshots itself and names the file. Every verdict also has a count-based condition.
- **Pre-registered INCONCLUSIVE:** R1 when B already opens the car keyboard (the rig does not reproduce the report, see R1).

## 4. Setup

Make `hur-wifi-test-scripts/pr-1076-text-keycodes-round1/`. Copy `ohu_lib.sh`, `ohu_setkeys.py` and `ptr_lib.sh` into it from `hur-wifi-test-scripts/projection-teardown-and-relays-round3/`. List them in Setup notes. Save `kc_lib.sh` beside them:

```bash
# kc_lib.sh : source after ohu_lib.sh and ptr_lib.sh. Needs HU, PH, OUT, PUT, BASEXML, KEYS, CAP, PCAP.
pmark() { adb -s "$PH" shell log -t OHURIG "$1"; }
both()  { mark "$1"; pmark "$1"; }
hseg() { awk -v a="$1" -v b="$2" '/AutomationMarker:/&&$NF==a{on=1} on{print} on&&/AutomationMarker:/&&$NF==b{exit}' "$CAP"; }
pseg() { awk -v a="$1" -v b="$2" '/OHURIG/&&$NF==a{on=1} on{print} on&&/OHURIG/&&$NF==b{exit}' "$PCAP"; }
c() { grep -acF -- "$1"; }
shot() { sleep 0.3; adb -s $HU shell screencap -p /sdcard/kc.png; sleep 0.3; adb -s $HU pull /sdcard/kc.png "$OUT/$1.png" >/dev/null; }
tap()  { sleep 0.3; adb -s $HU shell input tap "$1" "$2"; echo "$(date +%T) tap $1 $2 $3" >> $OUT/taps.log; }
key()  { sleep 0.3; adb -s $HU shell input keyevent "$@"; }
ime()  { adb -s $PH shell dumpsys input_method | grep -acF 'mInputShown=true'; }
wake() { adb -s $PH shell dumpsys power | grep -m1 -o 'mWakefulness=[A-Za-z]*'; }
hufocus() { adb -s $HU shell dumpsys window | grep -m1 mCurrentFocus; }
arm_w() { local r; r=$(send ACTION_START_WIRELESS_SCAN | tr -d '\r'); echo "$r" | grep -q 'not allowed\|Exception' && adb -s $HU shell am start -n $MAIN >/dev/null; }
# sess_up RUN : captures on, settings in, session with a moving picture, or NO_SESSION
sess_up() { RUN=$1; CAP=$OUT/$RUN.logcat; local L; apk_check || return 1; adb -s $HU shell am force-stop $PKG
  $PUT "$BASEXML" $KEYS; pcap_start "$RUN"; cap_start; sleep 1; both $RUN-start
  L=$(nl); arm_w; waitfor 120 'SSL handshake complete' $L || { echo "NO_SESSION $RUN" | tee -a $OUT/$RUN.summary; return 2; }
  waitfor 60 'Throughput over [0-9]+ms: rendered=[1-9]' $L || { echo "NO_PICTURE $RUN" | tee -a $OUT/$RUN.summary; return 2; }
  send ACTION_QUERY_STATE > $OUT/$RUN.state; }
close_run() { both $1-end; sleep 1; pcap_stop; cap_stop; th_report $1; send ACTION_EXIT >/dev/null; sleep 3; adb -s $HU shell am force-stop $PKG; }
```

```bash
HU=27870808938846; PH=4f4027e9; PUT=hu_put
source ./ohu_lib.sh; source ./ptr_lib.sh; source ./kc_lib.sh
prefs_cat > $OUT/settings-backup-$HU.xml; BASEXML=$OUT/settings-backup-$HU.xml
WANT_MD5=<md5 of the arm's APK>      # set before every arm; apk_check compares the installed APK with it
```

Install each arm with `adb -s $HU install -r -d <named apk>`, never with a script that picks the newest APK. Then read every key back (rig-quirks `topics/tooling.md`: an install can wipe `settings.xml`).

**Before the first run, record in Setup notes:**

- the owner of `/data/data/$PKG/shared_prefs` on D-HU (`stat`; rig-quirks `units/D-HU.md`);
- whether `key-codes` is in the backup (`grep -o 'key-codes[^/]*' $BASEXML`); the round deletes it except in R3;
- D-POCO's Gearhead and Maps versions: `adb -s $PH shell dumpsys package com.google.android.projection.gearhead | grep -m1 versionName`, and the same for `com.google.android.apps.maps`;
- D-POCO's bonded list: `adb -s $PH shell dumpsys bluetooth_manager | grep -a -A8 'Bonded devices'`;
- `ime` on D-POCO at rest. It must read `0`; if not, run `adb -s $PH shell input keyevent KEYCODE_HOME` and read again;
- D-POCO's focus (`adb -s $PH shell dumpsys window | grep mCurrentFocus`); `input keyevent KEYCODE_HOME` on D-POCO if a Settings screen is up.

**Settings keys**, written with the app stopped by `hu_put`, which reads them back. Audio keys are never written (the rig's audio settings are a deliberate worst case).

| Key | R1, R2 | R3 mapped (R3-M1, R3-B1) | R3 unmapped (R3-M0) |
|---|---|---|---|
| `wifi-connection-mode` | int `3` | int `3` | int `3` |
| `connection-modes` | set `wifi` | set `wifi` | set `wifi` |
| `log-level` | int `2` (INFO) | int `2` | int `2` |
| `onboarding-version` | int `2` | int `2` | int `2` |
| `native-driver-selection-mode` | int `0` | int `0` | int `0` |
| `night-mode` | int `1` (DAY) | int `1` | int `1` |
| `key-codes` | delete | set `42-131` | delete |
| `video-profile-starvation-cap` | delete | delete | delete |

`42-131` is the old format of a night-mode mapping: logical action 42 (night mode, as stored before the change) on physical key 131 (`KEYCODE_F1`), which nothing else on D-HU uses.

```bash
KEYS_X="int:wifi-connection-mode=3 set:connection-modes=wifi int:log-level=2 int:onboarding-version=2 int:native-driver-selection-mode=0 int:night-mode=1 del:key-codes del:video-profile-starvation-cap"
KEYS_N="int:wifi-connection-mode=3 set:connection-modes=wifi int:log-level=2 int:onboarding-version=2 int:native-driver-selection-mode=0 int:night-mode=1 set:key-codes=42-131 del:video-profile-starvation-cap"
```

**Verbs and steps.**

| Action | Command |
|---|---|
| bring the session up | `arm_w` (`send ACTION_START_WIRELESS_SCAN`, falls back to `adb -s $HU shell am start -n $MAIN` if the reply holds `not allowed`) |
| state | `send ACTION_QUERY_STATE` |
| marker, both devices | `both <label>` |
| a key from a keyboard | `key <KEYCODE_...>` |
| a tap into the projected video | `tap <x> <y> <what>` |
| end of run | `close_run <RUN>` |

Every verb must print `AutomationReceiver: <full action>` in the capture. A verb with no such line never landed: that cycle is void, not a FAIL.

### Opening the Maps search field (used by R1 and R2)

`open_search <ID>` is a procedure, not a function, because it reads screenshots:

1. `shot <ID>-s0`. Find Android Auto's Maps. If a Maps icon is on the rail, tap its centre. If not, tap the bottom-left rail icon twice (the app grid) with `shot` between taps, then tap the Maps icon.
2. `shot <ID>-s1`. Find Maps' search control (a magnifying glass, or a "Search" box). Tap its centre.
3. Record each coordinate used in `$OUT/coords.txt` the first time. On later cycles, reuse the same coordinates, but `shot` before each tap and confirm the target is still there. If it moved, read it again and record the change.
4. At most 3 taps per step. If Maps or its search control is not found in 3, the cycle is UNTESTABLE (Setup notes say what the screen showed).

### Closing it

`shot <ID>-c0`, then tap Maps' own close or back arrow on the search screen (top-left of Maps' area, never above y 110). `shot <ID>-c1` must show no keyboard. If 2 taps do not close it, `send ACTION_DISCONNECT`, `sleep 8`, `arm_w`, and wait for `SSL handshake complete` (120 s) before the next cycle; note it.

## 5. The deciding lines

Head unit lines, checked with `grep -F` against the B and M trees. All INFO except where marked.

| Line | Source | B | M | Used for |
|---|---|---|---|---|
| `CommManager: TX Key -> AA=` (full: `TX Key -> AA=<code> (isPress=<b>) src=projection`) | `CommManager.kt` | present | present | what we sent, per key |
| `KeyCode: Unknown or unsupported keycode ` (WARN) | `KeyCode.kt` | present | present | a key B converts to UNKNOWN |
| `Unknown: ` (the whole message is `Unknown: <code>`) | `AapTransport.kt` | present | present | same, second line |
| `Received request to resend night mode state` | `AapService.kt` | present | present | a key that became the night action |
| `SSL handshake complete` | `AapSslContext.kt` | present | present | session up |
| `Throughput over ` (regex `Throughput over [0-9]+ms: rendered=[1-9]`) | `VideoDecoder.kt` | present | present | picture gate |
| `AutomationReceiver: `, `AutomationMarker: ` | `automation/` | present | present | verbs, markers |

Phone lines, in `pseg` on D-POCO's capture (Gearhead and the framework, not in our tree):

| Line | Meaning |
|---|---|
| `TouchInputMethod` | Android Auto's car keyboard window |
| `PhoneKeyboardActivity` | the phone keyboard (count `START u0` and `Displayed` lines together) |
| `GH.PhoneKeyboard` | report only |
| `Discovered input for display` | the phone's reading of our input service; report the whole line, B against M |
| `Critical error` | Android Auto ended the session on a protocol error |

## 6. Runs

Order: R1-B, R1-M, R2-B, R2-M, R3-M1, R3-M0, R3-B1. **The point of the round is R1.** Run the thermal gate (`th_gate`) before each run and `th_watch` during it, as `ptr_lib.sh` does.

### R1. Which keyboard does a Maps search open (R1-B, R1-M), 3 cycles per arm

```bash
KEYS=$KEYS_X; sess_up R1-<arm>
grep -a 'Discovered input for display' $PCAP | tail -1 > $OUT/R1-<arm>.carui     # report, B against M
```

Per cycle `n` (ID `R1-<arm>-k<n>`):

```bash
echo "R1-<arm>-k<n> ime_before=$(ime) wake_before=$(wake)" >> $OUT/R1-<arm>.summary
both R1-<arm>-k<n>-go
# open_search R1-<arm>-k<n>   (section 4; the last tap is the search control)
sleep 5; shot R1-<arm>-k<n>-kb
echo "R1-<arm>-k<n> ime=$(ime) wake=$(wake) phonefocus=$(adb -s $PH shell dumpsys window | grep -m1 -o 'mCurrentFocus=.*')" >> $OUT/R1-<arm>.summary
both R1-<arm>-k<n>-done
# close it (section 4)
```

Per cycle, from `pseg R1-<arm>-k<n>-go R1-<arm>-k<n>-done`: `tim` = count of `TouchInputMethod`, `pk` = count of `PhoneKeyboardActivity`, `crit` = count of `Critical error`. From the summary: `ime`. From `R1-<arm>-k<n>-kb.png`, read by the host: `hukb` = a keyboard is on the head unit screen (yes or no).

**Class per cycle:**

- **PHONE** if `pk` >= 1 or `ime` >= 1.
- **CAR** if `pk` = 0, `ime` = 0, and (`tim` >= 1 or `hukb` = yes).
- **NONE** otherwise. The tap did not focus a field: void the cycle and run one more in its place, at most 2 per arm.

The arm's class is the class of at least 2 of its 3 cycles. If no class has 2, the arm is INCONCLUSIVE.

**Outcome, R1-B against R1-M:**

| R1-B | R1-M | Verdict | Means |
|---|---|---|---|
| PHONE | CAR | **PASS** | letter codes decide the keyboard on this pairing; the premise holds |
| PHONE | PHONE | **FAIL** | the rig reproduces the report and the change does not fix it |
| CAR | PHONE | **FAIL** | the change sends text entry to the phone |
| CAR | CAR | **INCONCLUSIVE** | the rig does not reproduce the report; the head unit keyboard opens without letter codes |

Also FAIL: `crit` >= 1 on any M cycle with 0 on B (the phone refused something in M's service discovery, for example the 65539 code). Report `crit` per cycle on both arms whatever the verdict.

**What a PASS would look like if the change did nothing:** it cannot. R1-B and R1-M differ only by the change, so a different class between them is the change. What the round cannot see is a phone whose rule differs from D-POCO's; the report names that limit.

**Report:** per cycle `tim`, `pk`, `ime`, `wake` before and after, `hukb` with the screenshot file, `phonefocus`; the `Discovered input for display` line from each arm, quoted.

Stop rule: 3 counted cycles per arm.

### R2. What typed keys do (R2-B, R2-M), 2 cycles per arm

Same session setup as R1 (`KEYS=$KEYS_X; sess_up R2-<arm>`). Per cycle `n` (ID `R2-<arm>-t<n>`):

```bash
both R2-<arm>-t<n>-go
# open_search R2-<arm>-t<n>   (section 4)
sleep 3; hufocus >> $OUT/R2-<arm>.summary         # must name AapProjectionActivity, or the keys go elsewhere
both R2-<arm>-t<n>-word;  key KEYCODE_C; key KEYCODE_A; key KEYCODE_F; key KEYCODE_E; sleep 2; shot R2-<arm>-t<n>-word
both R2-<arm>-t<n>-shift; adb -s $HU shell input keycombination KEYCODE_SHIFT_LEFT KEYCODE_B; sleep 2; shot R2-<arm>-t<n>-shift
both R2-<arm>-t<n>-punct; key KEYCODE_COMMA; sleep 2; shot R2-<arm>-t<n>-punct
both R2-<arm>-t<n>-del;   key KEYCODE_DEL; sleep 2; shot R2-<arm>-t<n>-del
both R2-<arm>-t<n>-done
# close it (section 4)
```

If `input keycombination` answers with a usage error or `Unknown command`, run `key KEYCODE_SHIFT_LEFT KEYCODE_B` instead and note it. The word avoids 'n' on purpose (R3 measures 'n').

Per step, from `hseg` between that step's marker and the next one:

- `tx` = count of `CommManager: TX Key -> AA=`; expected 2 per key (press and release), so 8 for the word.
- `unk` = count of `KeyCode: Unknown or unsupported keycode `.
- `night` = count of `Received request to resend night mode state`; must be 0 on both arms.
- `field` = the text in Maps' search box on that step's screenshot, read by the host (write `(empty)` or the exact characters).

Phone, from `pseg R2-<arm>-t<n>-go R2-<arm>-t<n>-done`: `crit`, `pk`, and `ime` read once after the `-del` shot.

**PASS (R2-M), all of:** on both cycles, the word step has `tx` = 8 and `unk` = 0; `field` after the word step reads `cafe`; `night` = 0 everywhere; `crit` = 0; `pk` = 0. **FAIL:** `tx` = 8 and `unk` = 0 (the keys went out as letters) but `field` stays empty, or `crit` >= 1. **INCONCLUSIVE:** `hufocus` did not name `AapProjectionActivity`, or the search field never opened.

**R2-B is the control** and is expected to show `unk` = 4 for the word step and an empty `field`. If R2-B types `cafe`, Android Auto takes text some other way and the change is not what makes typing work: say so.

**Report, not graded:** `field` after the shift, comma and delete steps on both arms (whether Shift gives a capital `B` with `metastate = 0`, whether the comma appears, whether delete removes one character), each with its screenshot.

### R3. Does a typed 'n' reach Android Auto when a night key is mapped (R3-M1, R3-M0, R3-B1)

No text field is needed: grade on our own lines. Each run is one session.

```bash
KEYS=$KEYS_N; sess_up R3-M1          # R3-M0: KEYS=$KEYS_X.  R3-B1: KEYS=$KEYS_N on arm B.
hufocus >> $OUT/$RUN.summary         # must name AapProjectionActivity
for i in 1 2 3; do
  both $RUN-f$i; key KEYCODE_F1; sleep 3        # the mapped night key (not in R3-M0)
  both $RUN-n$i; key KEYCODE_N;  sleep 3        # a typed 'n'
done
both $RUN-done; close_run $RUN
```

In R3-M0 skip the `KEYCODE_F1` lines (no mapping, so F1 means nothing). Per step, from `hseg` between that step's marker and the next:

- `tx42` = count of `TX Key -> AA=42 `; `tx65539` = count of `TX Key -> AA=65539 `; `night` = count of `Received request to resend night mode state`.

**Expected by the code reading:**

| Run | F1 step | n step |
|---|---|---|
| R3-M1 (M, mapped) | `tx65539` 2, `night` 2 | `tx65539` 2, `night` 2, `tx42` 0 |
| R3-M0 (M, unmapped) | | `tx42` 2, `night` 0 |
| R3-B1 (B, mapped) | `tx42` 2, `night` 2 | `tx42` 2, `night` 2 |

**The verdict is on the R3-M1 n step.** **FAIL (the defect is real):** in at least 2 of 3 n steps, `tx42` = 0 and `night` >= 1. **PASS:** in at least 2 of 3, `tx42` = 2 and `night` = 0. Anything else is INCONCLUSIVE, with the counts.

**Positive controls:** the R3-M1 F1 steps must show `night` >= 1 in at least 2 of 3 (the mapped key still works on M), and R3-M0's n steps must show `tx42` = 2 and `night` = 0 in at least 2 of 3 (the remap needs the mapping). If either control fails, R3 is INCONCLUSIVE whatever the n step showed.

**What a pass would look like if the change did nothing:** R3-B1's n step. B swallows every 'n', so if R3-M1 matches R3-B1 on the n step, the change has not fixed that for any user who mapped night mode.

### Discard rules (every run)

Void a cycle or step and run one more in its place when its window holds `Magic Garbage detected in header`, `MATCH! Starting AapService`, or a second `SSL handshake complete`. At most 2 replacements per run.

## 7. Do not re-run

- Which keyboard a car-app template field opens on this pairing, and that `setKeyboardType` changes nothing: `aa-numeric-keyboard-round1-results.md` and round 2b.
- The phone keyboard's activity bouncing in Self Mode: `selfmode-keyboard-viewmode-round2-results.md`.

## 8. Report back

1. R1: the class per cycle and per arm, with `tim`, `pk`, `ime` and the screenshot file per cycle. This decides whether the letter codes do anything for the reported fault.
2. R2: `field` per step on both arms, with `tx` and `unk`. This decides whether typing on a physical keyboard works with the change.
3. R3: `tx42`, `tx65539` and `night` per step for the three runs. This decides whether the change needs a fix before it can merge.

Also report the Gearhead and Maps versions, and the two `Discovered input for display` lines.

Results go in `pr-1076-text-keycodes-round1-results.md` in the template's skeleton (section 7), one `## <RUN>` section per run id (R1-B, R1-M, R2-B, R2-M, R3-M1, R3-M0, R3-B1). Write the file after every run, with `th_report` output in each section. Captures and screenshots go to the fork as release `rig-evidence-pr-1076-text-keycodes`, one asset `pr-1076-text-keycodes-round1-captures.zip`, cited with its sha256.

The phone lines are Gearhead's and the framework's and do not grep in our tree: `TouchInputMethod`, `PhoneKeyboardActivity`, `GH.PhoneKeyboard`, `Discovered input for display`, `Critical error`, `mInputShown=true`, `mWakefulness`.

```decisive-strings
AutomationReceiver: 
AutomationMarker: 
CommManager: TX Key -> AA=
KeyCode: Unknown or unsupported keycode 
Unknown: 
Received request to resend night mode state
SSL handshake complete
Throughput over 
Magic Garbage detected in header
MATCH! Starting AapService
```
