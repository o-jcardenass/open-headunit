# usb-reattach, round 4 brief

Published name on the transfer branch: `usb-reattach-round4-brief.md`. Results file: `usb-reattach-round4-results.md`. Evidence: add the asset `usb-reattach-round4-captures.zip` to the existing release `rig-evidence-usb-reattach`. Do not make a new release.

This round re-tests one run of round 3. Round 3 (`usb-reattach-round3-results.md`) passed R2A, R2K, R2T, P1 and U0, and failed U1 on one count: `held=1` in both cycles. The fix for that hold is one call in `AapService`. This round runs Stage D only: U0 as the gate, then U1.

**The point of the round is U1.** With the settings screen open for 35 s after a USB Save, the dongle's return must not be held by the screen, and the dongle must get one switch, not two.

## 1. Build and baseline

- **Candidate:** branch `fix/usb-reattach` on the fork, SHA **`b48da528`** (`b48da5287ec0694d55e63b66803572412bc3c379`), tree `67051a2be3aac997e134fbb8797544cbad87e0d3`. Seven commits on `main` `71375a68` (`71375a689f704df1dc844e6a0656a58e471a12aa`).
- **History was rewritten.** Round 3 tested `bcf3b1a3`. The fix was squashed into the commit `Connection: keep a Save's USB retry until SSL`, so the last four commits have new SHAs and `bcf3b1a3` is no longer on the branch. The code diff between `bcf3b1a3` and `b48da528` is 3 lines in `AapService.kt` and 7 lines in `UsbLauncherManager.kt`, plus one test file.
- **Export build E only.** E is the candidate plus one manifest attribute, `android:exported="true"` on `.main.SettingsActivity`, as in round 3. D-POCO cannot open the settings screen from the shell without it. E is a local commit. Never push it. This round builds no other APK: the Closing reinstalls the APK that is on D-POCO at Prepare.
- **No baseline APK.** The control is round 3's U1 on `bcf3b1a3`, same rig, same dongle, same keys: `held=1` per cycle.
- **JVM tests:** 3086, 0 failures, our run on tree `67051a2b`. Do not run them on the rig.

```bash
RIG=$(dirname "$(find ~ -maxdepth 3 -type d -name rig-toolkit -print -quit)"); echo "$RIG"   # MUST print a non-empty path
OUT=$RIG/rig-data/rounds/usb-reattach-round4; APKS=$RIG/rig-data/apks/usb-reattach-round4; mkdir -p $OUT $APKS
git ls-remote fork fix/usb-reattach          # MUST print b48da5287ec0694d55e63b66803572412bc3c379
git fetch fork fix/usb-reattach
```

Make the worktree with `$RIG/rig-toolkit/wt-new.sh` (its usage is in `$RIG/rig-toolkit/TOOLS.md`), name `usb-reattach-r4`, at `b48da5287ec0694d55e63b66803572412bc3c379`. Set `WT` to its path. Then:

```bash
git -C $WT rev-parse 'HEAD^{tree}'                              # MUST print 67051a2be3aac997e134fbb8797544cbad87e0d3
git -C $WT checkout -B arm-E-r4
sed -i 's|android:name=".main.SettingsActivity"|android:name=".main.SettingsActivity"\n            android:exported="true"|' $WT/app/src/main/AndroidManifest.xml
git -C $WT diff --stat                                          # MUST print 1 file changed, 1 insertion(+)
git -C $WT -c user.name=rig -c user.email=rig@local commit -qam "rig: export SettingsActivity"
git -C $WT rev-parse HEAD^                                      # MUST print b48da5287ec0694d55e63b66803572412bc3c379
git -C $WT rev-parse --short=12 HEAD                            # E's commit: record it
```

Copy `local.properties` into `$WT` if it has none. Cool the host to 70C first, then build with `build_hur.sh` (`HUR_DIR=$WT`, `GRADLE_OPTS=-Dorg.gradle.workers.max=2`, because the script has no worker option). `build_hur.sh` deletes the previous APK, so copy the APK at once to `$APKS/export-b48da528.apk`. Stop the worktree's Gradle daemon with `$WT/gradlew --stop` before U0.

**Stop and escalate** if `git ls-remote` prints another SHA, or the tree differs.

### 1a. Identity gate on E, before U0

Pull the installed APK with a real `adb pull` to `$OUT/installed-E.apk`, then:

```bash
for s in checkOnRequest PROJECTION_UNRAISED StaleAccessoryRecoveryPolicy; do
  printf '%s\t' "$s"; unzip -p $OUT/installed-E.apk 'classes*.dex' | strings | grep -cF "$s"; done      # each 1 or more
aapt2 dump xmltree --file AndroidManifest.xml $OUT/installed-E.apk | grep -A4 'main.SettingsActivity' | grep -c 'exported(0x01010010)=true'   # MUST print 1
```

`checkOnRequest` is the new method. Round 3's E does not carry it, so a count of 0 means the old build. `send ACTION_QUERY_STATE` must reply with a `commit` equal to E's 12-character commit. Record the md5 from the `adb pull` and from a local `md5sum` of `$APKS/export-b48da528.apk`. They must be equal. Install with `adb install -r -d` only. Back up `settings.xml` before the install and diff it after.

## 2. What this is and why it exists

**The defect under test.** A USB Save that ends a session must keep its retry until SSL, even while the settings screen stays open (`pr-1047-session-reconnect-round3` U1 FAIL: `held` 5 per cycle, SSL 46 to 51 s after the Save). Round 3 measured the fix on `bcf3b1a3`: the session formed behind the open screen after 9.9 s and 9.8 s, against 46 to 51 s. One hold line was left per cycle.

**Why round 3 still held once.** The dongle returns in normal mode about 5 s after the Save (`USB_DEVICE_ATTACHED` at 15:33:08.010 in round 3). The settings screen is visible, so `UsbAttachedActivity` does not switch the device. It logs `UsbAttachedActivity: settings on show or the status pill's X holding; handing <device> to the service` and sends `ACTION_CHECK_USB` to `AapService`. On `bcf3b1a3` that branch called `checkAlreadyConnected(force = true, userRequested = ...)` with no Save. `AutoConnectHoldPolicy` then answered `HOLD_FOR_SETTINGS`: the line at 15:33:08.073. The 2 s attach fallback carried the Save and sent `acc start` at 15:33:10.078, and the replay at 15:33:10.585 formed the session. Every other attach path already carried the Save. The hand-off branch was the one path that did not.

**The fix (`e0e6e9a2`, in `Connection: keep a Save's USB retry until SSL`).** `UsbLauncherManager.checkOnRequest(userRequested)` scans with `pendingSettingsRestart()`, unless the user asked by hand. The `ACTION_CHECK_USB` branch of `AapService.onStartCommand` calls it. A user's own request stays free of the Save.

**Why Stage U is not re-run.** The automation verb `ACTION_CHECK_USB` always sets `EXTRA_USER_REQUESTED` to true (`AutomationCommandPolicy`). For a user request `checkOnRequest` passes no Save, which is the call that round 3 measured. The new path runs only when a Save is open, and only U1 opens one.

**What a PASS would look like if the change did nothing.** Round 3's shape: `held=1` per cycle, `handoff_next=held` (the hold line follows the hand-off line), and SSL near 9.8 s, because the replay still forms the session. So `t_ssl2` alone cannot tell the two builds apart. `held` and `handoff_next` can, and `handoff` 1 or more proves that the run reached the changed path.

**The new risk.** The hand-off scan now proceeds, so it can switch the dongle itself. The 2 s attach fallback then meets the busy USB attempt slot, so it must queue and replay accessory-only. Two `Sending acc start` lines for one return of the dongle would be a double switch. Round 3 reported `accstart=1`; this round grades it.

## 3. What is different about this round

**Stage D only, one cable layout.**

| Head unit | On D-POCO's OTG port | adb | Runs | Arm |
|---|---|---|---|---|
| D-POCO, wireless adb | the dongle `carplay_box_F96B` (idle `18d1:4ee1`, accessory `18d1:2d00`), paired to D-MOTO | D-POCO wireless, D-MOTO (`ZY22GC3BM4`) on its PC cable | U0, U1 | E |

**The host layout changed after round 3.** Scripts are in `$RIG/rig-toolkit/` (`TOOLS.md` is the inventory). This round's data goes in `$OUT`, its APKs in `$APKS`, and its worktree under `$RIG/worktrees/`. Round 3's Stage D libraries are in `$RIG/rig-data/rounds/usb-reattach-round3/stageD/`.

**Three round 3 rig-script defects, fixed in this round's shell (section 6).**

1. `th_gate` starts `th_watch` in the background and nothing stops it. The watcher keeps `/tmp/ohu-rig.lock` after the script ends. `libreattach4.sh` stops it on exit.
2. The library `pocoput` reads back too early and races the write. `libreattach4.sh` wraps it with a read-back that retries.
3. `rig_devices.sh` lists D-POCO's USB serial only, so it cannot see the wireless serial. Do the D-POCO pre-flight by hand (Prepare step 8).

**Expect a crash dialog after each force-stop, and do not grade it.** With `auto-start-on-usb=true` on API 35, Android re-delivers `LOCKED_BOOT_COMPLETED` when the stopped app is launched. `BootCompleteReceiver` then throws `ForegroundServiceStartNotAllowedException`, and an "Application Error" dialog appears on D-POCO. `BootCompleteReceiver.kt` has no diff against `main`, so it is not this branch. The session still formed in round 3. `clear_crash` (section 6) closes the dialog with one `KEYCODE_HOME` before each Save, as round 3 did by hand. It writes each close to `$OUT/dialogs.log`. U1's `fatal` window starts after the launch, so this crash does not reach it.

**D-POCO's battery.** It powers the dongle over OTG and cannot charge. Round 3 went from 63% to 46% across Stage D. Charge it to 60% or more before Prepare. `bat_gate` stops the round at 25%.

**D-POCO's bus can read empty after a cable change** (round 3, twice). If `/sys/bus/usb/devices` shows only the root hubs, give one reseat cue.

**The rig audio keys are a deliberate worst case.** They live on D-HU, which this round does not use. Do not write or reset them.

**Expected INCONCLUSIVE, said up front:**

- **U1:** U0 fails, or no completed cycle has `handoff` 1 or more (the dongle came back by a path that does not reach the change).

## 4. Hand steps

One batched request before the first run (Prepare step 7). After that, a cue appears only for a reseat, a system USB dialog, or the Closing.

| Id | Step | When | Why no verb |
|---|---|---|---|
| H0 | Unlock D-MOTO if it is behind a PIN | Prepare only | adb has no way past a PIN (`rig-quirks/units/D-MOTO.md`) |
| H1 | Unplug D-POCO from the PC. Plug the dongle into D-POCO's OTG port | Prepare, after wireless adb is up | A cable is hardware |
| H1r | Reseat the dongle in D-POCO's OTG port | Only if the bus reads empty; once | A cable is hardware |
| H1c | Unplug the dongle. Put D-POCO back on its PC cable | Closing | A cable is hardware |
| H2 | Allow a system USB dialog on D-POCO for the dongle. "Always" is allowed for the dongle | Fallback only | A system dialog, not our app |
| H7 | Open a terminal with `tail -n0 -F $OUT/hand-steps.log` and keep it in view | Prepare | A cue must reach the operator in time |

**Injected taps.** U1 makes each Save with two injected taps on dumped targets (`tap_save` in `lib1047r3.sh`): the "Separate Audio Streams" toggle by `:id/settingSwitch`, then Save by `:id/save_button_widget` with the text `Save (Reconnect needed)`. No verb reaches `applyAudioSettings()`. Budget: 4 taps per run (2 cycles), at most 5, enforced by `tap_xy`. A target that does not resolve voids the cycle. Never guess a coordinate. List every tap from `taps.log` in Setup notes.

## 5. Settings keys

Write them with the app stopped, from the Prepare backup (template §1). D-POCO is not rooted, so use `pocoput`. `log-level` 2 (INFO) carries every app line this round reads: each line in section 7 is `AppLog.i`, `AppLog.w` or `AppLog.e`. `ACTION_LOG_MARKER` is not in `AutomationCommandPolicy.CONFIGURING` at `b48da528`, so `allow-external-configuration` is not needed.

| Key | Type | U0, U1 | Why |
|---|---|---|---|
| `wifi-connection-mode` | int | `3` | an armed Native stack prints `WifiLauncher: Initializing WiFi Mode: NATIVE`, which U1 counts; the USB SSL must stand it down |
| `log-level` | int | `2` | INFO |
| `onboarding-version` | int | `2` | no wizard |
| `kill-on-disconnect` | boolean | `false` | |
| `auto-connect-last-session` | boolean | `true` | the hand-off scan finds the dongle as a known device |
| `auto-start-on-usb` | boolean | `true` | as round 3; it also causes the crash in section 3 |
| `reopen-on-reconnection` | boolean | `true` | |
| `use-libusb` | boolean | `false` | the switch takes the Java path (`Sending acc start`) |
| `connection-modes` | string set | `usb,wifi` | |
| `native-aa-wake-damage-verdict` | int | `0` | |
| `video-profile-starvation-cap` | | delete | a run of failed bring-ups can leave it set |
| `native-aa-wireless`, `wifi-launcher-mode` | | delete | legacy mode keys |

```bash
U1KEYS="int:wifi-connection-mode=3 int:log-level=2 int:onboarding-version=2 bool:kill-on-disconnect=false bool:auto-connect-last-session=true bool:auto-start-on-usb=true bool:reopen-on-reconnection=true bool:use-libusb=false set:connection-modes=usb,wifi del:video-profile-starvation-cap del:native-aa-wireless del:wifi-launcher-mode int:native-aa-wake-damage-verdict=0"
```

This is round 3's `U1KEYS`, written out. Check `ohu_setkeys.py` first: `grep -c 'if v else \[\]' ohu_setkeys.py` must print 1.

## 6. Shell setup

Copy `ohu_lib.sh`, `ohu_setkeys.py`, `ptr_lib.sh`, `lib1047.sh`, `lib1047r3.sh` and, if present, `env.sh` into `$OUT` from `$RIG/rig-data/rounds/usb-reattach-round3/stageD/`. If `lib1047r3.sh` is gone, cut it from section 6 of `archive/rounds/pr-1047-session-reconnect-round3-brief.md`. Every `ps aux | grep -c "[l]ogcat"` in them must already read `pgrep -fc "^adb .*logcat"`; fix any that does not. If `th_pkg` needs `../rig_thermal.sh`, link it as round 3 did. Save `libreattach4.sh` below beside them. Run U0 and U1 as one script under `flock /tmp/ohu-rig.lock`. One adb call at a time per unit; the two log captures are the only streams beside them (house rule 8).

```bash
POCO_IP=<D-POCO wlan0 address, Prepare step 6>; HU=$POCO_IP:5555; MOTO=ZY22GC3BM4; PH=$MOTO
cd $OUT; PUT=pocoput; BASEXML=$OUT/settings_backup_poco.xml; UNIT=D-POCO
source ./ohu_lib.sh; source ./ptr_lib.sh; source ./lib1047.sh; source ./lib1047r3.sh
[ -f ./env.sh ] && source ./env.sh
source ./libreattach4.sh
ARM=E; WANT_MD5=<E md5>
readers          # must print 0
```

**`libreattach4.sh`**:

```bash
# libreattach4.sh : source last. Needs HU, OUT, CAP, SAVE_LN, T0 (set by tap_save), THPID (set by th_gate).
PKG_ID=com.andrerinas.headunitrevived
HANDOFF="UsbAttachedActivity: settings on show or the status pill's X holding; handing "

# readers : logcat readers on the host; must print 0 before and after the round
readers() { pgrep -fc "^adb .*logcat"; }

# fix 1: stop the thermal watcher when the script ends
trap '[ -n "$THPID" ] && kill $THPID 2>/dev/null' EXIT

# keys_ok <spec...> : 0 when settings.xml on the unit holds every int/bool spec and no del spec
keys_ok() { adb -s "$HU" shell run-as $PKG_ID cat shared_prefs/settings.xml > "$OUT/keys_now.xml" 2>/dev/null
  python3 -I -c '
import sys,re
x=open(sys.argv[1]).read(); bad=[]
for s in sys.argv[2:]:
  k,_,r=s.partition(":"); n,_,v=r.partition("=")
  if k in ("int","bool") and not re.search(r"name=\"%s\" value=\"%s\""%(re.escape(n),re.escape(v)),x): bad.append(s)
  if k=="del" and ("name=\"%s\""%n) in x: bad.append(s)
print("keys_ok bad=%s"%",".join(bad) if bad else "keys_ok ok"); sys.exit(1 if bad else 0)' "$OUT/keys_now.xml" "$@"; }

# fix 2: pocoput with a read-back that retries, 3 tries 2 s apart
eval "orig_$(declare -f pocoput)"
pocoput() { local i; for i in 1 2 3; do orig_pocoput "$@"; sleep 2; keys_ok "${@:2}" | tee -a "$OUT/keys.log" | grep -q 'keys_ok ok' && return 0; done; return 1; }

# clear_crash : close the BootCompleteReceiver "Application Error" dialog if it is in focus (section 3)
clear_crash() { local f; f=$(adb -s "$HU" shell dumpsys window | grep -a mCurrentFocus | tr -d '\r')
  if echo "$f" | grep -aq 'Application Error'; then echo "$(date +%T) ${RUN:-} crash dialog closed: $f" >> "$OUT/dialogs.log"
    adb -s "$HU" shell input keyevent KEYCODE_HOME; sleep 2; fi; }

# hnext : for each hand-off line in [T0, T0+35000 ms], the first of seven lines that follows within 3000 ms. "none" if no line follows.
hnext() { tail -n +"$SAVE_LN" "$CAP" | awk -v t0="$T0" -v h="$HANDOFF" '
  function m(x,p){split(x,p,/[:.]/);return ((p[1]*60+p[2])*60+p[3])*1000+p[4]}
  BEGIN { n=split("UsbLauncher: USB auto-connect held while the settings screen is open; |Found known USB device with permission: |Found known USB device but no permission: |Found device already in accessory mode|Single USB auto-connect: connecting to|Fallback: force=true and found single normal-mode Android device|UsbLauncher: replaying the USB scan queued during the attempt", L, "|")
    split("held|known|known_noperm|found_acc|single|fallback|replay", N, "|") }
  { d = m($2) - t0 }
  w && d - wt > 3000 { out = out "none;"; w = 0 }
  w { for (i = 1; i <= n; i++) if (index($0, L[i])) { out = out N[i] ";"; w = 0; break } }
  index($0, h) && d >= 0 && d <= 35000 { if (w) out = out "none;"; w = 1; wt = d }
  END { if (w) out = out "none;"; print (out == "" ? "NA" : out) }'; }
```

Every action in this round:

| Action | Command | Notes |
|---|---|---|
| Launch our app | `adb -s $HU shell am start -n $MAIN` | D-POCO refuses a wireless verb after a force-stop |
| Stop the service | `send ACTION_EXIT` | only to a running app |
| Mark a step | `mark <label>` | `AutomationMarker: <label>` at WARN, `RIGMARK` on the phone |
| Read the build | `send ACTION_QUERY_STATE` | `commit` on the `data=` line |
| Open the audio streams screen | `open_audio` | not a verb; an `am start` into the exported `SettingsActivity` |
| Close the settings screen | `close_settings` | `input keyevent 4`, a scripted keyevent |
| Close the crash dialog | `clear_crash` | `KEYCODE_HOME`, only when the dialog has focus |
| The Save | `tap_save <label>` | two injected taps on dumped targets (section 4) |

Every `send` must print `AutomationReceiver: <action>` in the capture. A step with no such line never landed: its cycle is void, not a FAIL.

## 7. The lines that decide the runs

App lines were checked with `grep -F -r` against `app/src/main` and `contract/src` at `b48da528`. All print at INFO or above. Unit lines are counted in `$CAP` (`$OUT/U1-E.hu.logcat`), phone lines in `$OUT/U1-E.phone.logcat`. Each U1 count is inside a window that starts at the Save line (`SAVE_LN`, time `T0`) and runs the stated number of ms (`cntw`), or between two capture lines (`cnt`).

| Line (fixed substring) | File | Meaning |
|---|---|---|
| `AutomationReceiver: ` | unit | a verb landed; a missing one voids the step |
| `AutomationMarker: ` | unit | a marker |
| `CommManager: audio settings changed; reconnecting the projection session` | unit | **the Save (T0)** |
| `SettingsRestart: route=` with `retry=run`; `fallback=run`, `fallback=skip_superseded`, `fallback=cancel_superseded` | unit | the Save's retry and its fallback |
| `AapService: session state ` with `(settings_restart)` | unit | the Save's end |
| `UsbAttachedActivity: settings on show or the status pill's X holding; handing ` | unit | **the hand-off to the service: the changed path was reached** |
| `UsbLauncher: USB auto-connect held while the settings screen is open; ` | unit | **a USB check held by the screen (the round 3 FAIL)** |
| `Found known USB device with permission: `, `Found known USB device but no permission: `, `Found device already in accessory mode`, `Single USB auto-connect: connecting to`, `Fallback: force=true and found single normal-mode Android device` | unit | a scan that went on (for `hnext`) |
| `UsbLauncher: replaying the USB scan queued during the attempt` | unit | the attempt slot replayed a queued scan |
| `Sending acc start` | unit | **an AOA switch went out** (Java path) |
| `USB Intent: ` with `USB_DEVICE_DETACHED` or `USB_DEVICE_ATTACHED` | unit | a detach or an attach |
| `USB accessory device attached, connecting.`, `Switching USB device to accessory mode` | unit | the dongle's return |
| `SSL handshake complete` | unit | a session formed (never prefix `Handshake:`) |
| `stopping the wireless stack for the duration of it` | unit | the wired-session quiesce at SSL |
| `WifiLauncher: Initializing WiFi Mode: ` then `NATIVE` | unit | wireless armed |
| `NativeAA: Attempting active poke to device` | unit | a wake poke |
| `AapService: Not raising the projection, the settings screen is open` | unit | the raise refused behind the screen |
| `AapService: USB disconnect. Scheduling reconnect check in ` | unit | the 3 s USB check |
| `UsbLauncher: stale accessory ` | unit | a ladder line on the dongle (report) |
| `AapService: the settings screen closed with a USB auto-connect held ` | unit | a held check released by the close (report) |
| `Boot auto-start: received action=` | unit | the boot redelivery before the crash (report) |
| `MATCH! Starting AapService` | unit | discard rule |
| `FATAL EXCEPTION` | unit, phone | a crash (system line) |
| `Critical error` | phone | report only |
| `RIGMARK` | phone | phone markers |

**Composed strings.** `SettingsRestart: route=$route retry=` and `fallback=` join a literal and a value, and `session state $state ($reason)` does too. `UsbAttachedActivity: ... handing ${device.deviceName} to the service` has the device name in the middle. The scripts match the literal parts only. Only those are in the `decisive-strings` block. `decisive-strings-external` holds strings that `app/src` does not carry as a literal. `settings_restart` is one of them: the app prints it as the `$reason` of `AapService: session state `, but the literal is `HeadUnitIntent.REASON_SETTINGS_RESTART` in `contract/src/main/java/com/andrerinas/openheadunit/contract/HeadUnitIntent.kt`.

## 8. Runs

Estimated time: Prepare and one build 25 min, U0 8 min, U1 8 min, Closing 5 min. **Round total: about 45 min**, plus D-POCO's charge before Prepare. Write the results file after every run, as the thermal rule asks.

**Preflight in every run.** `readers` prints 0 before the run. The run's `.keys` file shows its keys read back (`keys_ok ok` in `$OUT/keys.log`) and the right `commit`. The capture holds `stopping the wireless stack for the duration of it` after the first session and `MATCH! Starting AapService` zero times. If a check fails, the run is a setup failure: fix the cause, re-run it once, and do not grade the failed attempt.

### Prepare (P)

D-POCO is on its PC cable (`4f4027e9`). The dongle is not plugged in. D-MOTO is on its PC cable.

1. **Build and identity of the source** (section 1, up to the copy of the APK).
2. **D-POCO battery:** `adb -s 4f4027e9 shell dumpsys battery | grep -a -m1 level`. 60% or more. Record it.
3. **D-POCO API level:** `adb -s 4f4027e9 shell getprop ro.build.version.sdk`. Record it.
4. **Back up D-POCO's APK and settings** with the app stopped:
   ```bash
   adb -s 4f4027e9 shell am force-stop com.andrerinas.headunitrevived
   adb -s 4f4027e9 pull $(adb -s 4f4027e9 shell pm path com.andrerinas.headunitrevived | cut -d: -f2 | tr -d '\r') $APKS/prior-installed.apk
   md5sum $APKS/prior-installed.apk          # record it; round 3 left C, md5 3e6bbe7b61bf2e3460bf05ae89894ce6
   adb -s 4f4027e9 shell run-as com.andrerinas.headunitrevived cat shared_prefs/settings.xml > $OUT/settings_backup_poco.xml
   diff <(sort $RIG/rig-data/rounds/usb-reattach-round3/settings_backup_poco.xml) <(sort $OUT/settings_backup_poco.xml)   # state the delta
   adb -s 4f4027e9 shell dumpsys usb | grep -i -A4 headunitrevived > $OUT/dumpsys-usb-grants-before.txt
   ```
5. **D-MOTO:**
   ```bash
   adb -s ZY22GC3BM4 shell dumpsys package com.google.android.projection.gearhead | grep -a -m1 versionName   # record; round 3 ran 17.9.664004-release
   adb -s ZY22GC3BM4 shell svc power stayon true
   adb -s ZY22GC3BM4 shell svc wifi enable; adb -s ZY22GC3BM4 shell svc bluetooth enable; sleep 3
   adb -s ZY22GC3BM4 shell dumpsys bluetooth_manager | grep -a -m2 -iE '^ *(enabled|state):'                 # MUST read enabled / ON
   adb -s ZY22GC3BM4 shell dumpsys bluetooth_manager | grep -a -i -A30 "Bonded devices" | grep -ac carplay_box_F96B   # MUST print 1 or more
   adb -s ZY22GC3BM4 shell dumpsys window | grep -a -E "mShowingLockscreen|mDreamingLockscreen"
   adb -s ZY22GC3BM4 shell input keyevent KEYCODE_HOME
   ```
6. **Wireless adb for D-POCO:**
   ```bash
   adb -s 4f4027e9 tcpip 5555; sleep 3
   POCO_IP=$(adb -s 4f4027e9 shell ip -4 addr show wlan0 | grep -o 'inet [0-9.]*' | cut -d' ' -f2); echo $POCO_IP
   adb connect $POCO_IP:5555; adb -s $POCO_IP:5555 shell getprop ro.product.model   # MUST print M2007J20CG
   ```
7. **The one batched request to the operator.** Send it once, then wait for the answer:
   > "Round 4 needs you now, and once at the end. 1) If D-MOTO is behind a PIN, unlock it. 2) Open the cue terminal: `tail -n0 -F <OUT>/hand-steps.log`. 3) Now: unplug D-POCO from the PC and plug the dongle into D-POCO's OTG port. Leave D-MOTO on its PC cable. 4) At the end a cue asks you to unplug the dongle and put D-POCO back on its PC cable. Another cue appears only if the dongle needs a reseat or a system USB dialog appears. 'Always' is allowed for the dongle."
8. **D-POCO pre-flight, by hand** (`rig_devices.sh` cannot see the wireless serial):
   ```bash
   adb -s $POCO_IP:5555 shell settings get global wifi_on                       # 1
   adb -s $POCO_IP:5555 shell settings get global bluetooth_on                  # 1
   adb -s $POCO_IP:5555 shell dumpsys power | grep -a -m1 mWakefulness          # Awake
   ```
9. **Shell:** section 6. `readers` must print 0. Kill any leftover `th_watch` from an earlier round by pid, never with `pkill -f`.

### U0. Stage D setup and gate (once)

```bash
RUN=U0; th_gate || exit 3; bat_gate || exit 4
adb -s $HU shell ls /sys/bus/usb/devices                                     # MUST show more than the root hubs; else cue "H1r: reseat the dongle in D-POCO's OTG port", once, then re-check
adb -s $HU shell cat /proc/net/tcp /proc/net/tcp6 | grep -aiE ':149D [0-9A-F]+:0000 0A' && echo "D-POCO SERVES 5277"   # MUST print nothing
adb -s $HU shell am force-stop com.andrerinas.headunitrevived
adb -s $HU install -r -d $APKS/export-b48da528.apk; ARM=E; WANT_MD5=<E md5>
```

Then: the 1a gate on E; the `settings.xml` diff after the install; then the screen check: `dest_id` prints a non-zero id, `open_audio` returns 0 with route `am`, `ui_dump && target settingSwitch && target save_button_widget` prints two coordinate pairs, then `close_settings`. No tap in U0. Then write the keys, launch and form the first session:

```bash
$PUT $BASEXML $U1KEYS; L=$(nl); adb -s $HU shell am start -n $MAIN >/dev/null; usb_live $L; clear_crash
echo "U0 quiesce=$(cnt $L '$' 'stopping the wireless stack for the duration of it')" | tee -a $OUT/U0.tsv
```

- **PASS:** wireless adb answers; no 5277 listener; the 1a gate and the screen check pass; `usb_live` reports a live USB session; `quiesce` 1 or more.
- A failed U0 makes U1 UNTESTABLE. Say why.

### U1. A USB Save with the settings screen open for 35 s (E, 2 cycles, 4 taps). THE POINT OF THE ROUND

Round 3's block, with `clear_crash` before each Save and three new counters: `handoff`, `handoff_next` and `pre_crash`.

```bash
r_open U1-E $U1KEYS; L=$(nl); adb -s $HU shell am start -n $MAIN >/dev/null; usb_live $L || echo "U1 no USB session at start"
echo "U1 preflight native=$(cnt $L '$' 'WifiLauncher: Initializing WiFi Mode: NATIVE') quiesce=$(cnt $L '$' 'stopping the wireless stack for the duration of it') pre_crash=$(cnt 1 $L 'FATAL EXCEPTION') boot=$(cnt 1 '$' 'Boot auto-start: received action=')" | tee -a $OUT/U1-E.tsv   # quiesce 1 or more
done=0
for k in 1 2; do
  clear_crash
  S=$(nl); p0=$(pcnt 'Critical error'); f0=$(pcnt 'FATAL EXCEPTION'); mark U1-$k-go
  tap_save U1-$k || continue
  hold_until 35000; C0=$(nl); close_settings; waitfor 60 'SSL handshake complete' $C0; E=$(nl)
  FS=$(lno $SAVE_LN 'SSL handshake complete')
  R="U1-$k\tretry=$(cntw 0 5000 'SettingsRestart: route=USB retry=run')\tdetach=$(cntw 0 35000 'USB_DEVICE_DETACHED')\tattach=$(cntw 0 35000 'USB_DEVICE_ATTACHED')\treattach=$(cntw 0 35000 'USB accessory device attached, connecting.')"
  R="$R\thandoff=$(cntw 0 35000 "$HANDOFF")\thandoff_next=$(hnext)\theld=$(cntw 0 35000 'UsbLauncher: USB auto-connect held while the settings screen is open; ')"
  R="$R\ttries_before_ssl=$( [ $FS -gt 0 ] && usb_tries $SAVE_LN $FS || echo NA )\tt_ssl2=$(ms_of $FS)"
  R="$R\tssl_before_close=$(cnt $SAVE_LN $C0 'SSL handshake complete')\tquiesce=$( [ $FS -gt 0 ] && cnt $FS $E 'stopping the wireless stack for the duration of it' || echo NA )\tarm_in_window=$(cntw 0 35000 'WifiLauncher: Initializing WiFi Mode: NATIVE')\tpokes_in_window=$(cntw 0 35000 "$POKE")"
  R="$R\tfallback_run=$(cntw 0 60000 'fallback=run')\tfallback_other=$(( $(cntw 0 60000 'fallback=skip_superseded') + $(cntw 0 60000 'fallback=cancel_superseded') ))\traise_refused=$(cnt $SAVE_LN $C0 'AapService: Not raising the projection, the settings screen is open')"
  R="$R\tsettings_restart=$(cntw 0 3000 '(settings_restart)')\tusb_retry_line=$(cnt $SAVE_LN $E 'AapService: USB disconnect. Scheduling reconnect check in ')\taccstart=$(cntw 0 35000 'Sending acc start')\tstale_steps=$(cntw 0 60000 'UsbLauncher: stale accessory ')\treplay=$(cntw 0 60000 'replaying the USB scan queued during the attempt')"
  R="$R\tclose_release=$(cnt $SAVE_LN $E 'AapService: the settings screen closed with a USB auto-connect held ')\tcrit=$(( $(pcnt 'Critical error') - p0 ))\tfatal=$(cnt $S $E 'FATAL EXCEPTION')\tphone_fatal=$(( $(pcnt 'FATAL EXCEPTION') - f0 ))"
  echo -e "$R" | tee -a $OUT/U1-E.tsv
  done=$((done+1)); [ $done -ge 2 ] && break; sleep 10
done
r_close U1-E; [ -n "$THPID" ] && kill $THPID 2>/dev/null
readers          # must print 0
```

- **PASS, on every completed cycle:** `held` 0; `handoff_next` holds no `held`; `accstart` 1 or less; `retry` 1; `ssl_before_close` 1 or more with `t_ssl2` 35000 ms or less; `quiesce` 1 or more; `arm_in_window` 0 and `pokes_in_window` 0; `fallback_run` 0; `raise_refused` 1 or more; `settings_restart` 1; `fatal` 0. Phone (D-MOTO, graded): `phone_fatal` 0. **And** `handoff` is 1 or more in at least one completed cycle.
- **FAIL, on any completed cycle:** `held` 1 or more; or `accstart` 2 or more (a double switch for one return of the dongle; quote both `Sending acc start` lines with their times); or `ssl_before_close` 0 with the dongle back on the bus (`attach` or `reattach` 1 or more); or `arm_in_window` 1 or more; or `fallback_run` 1 with a session formed before 30 s; or `phone_fatal` 1 or more.
- **INCONCLUSIVE:** every completed cycle has `handoff` 0, and no FAIL condition holds. The dongle then came back by a path that this change does not touch. Report which line followed `USB_DEVICE_ATTACHED` instead.
- **Report:** per cycle `handoff`, `handoff_next`, `t_ssl2`, `tries_before_ssl`, `detach`, `attach`, `reattach`, `fallback_other`, `usb_retry_line`, `replay`, `close_release`, `stale_steps` (quote any ladder line on the dongle), `crit`; and from the preflight line `pre_crash` and `boot`. Quote the hand-off line and the 3 lines after it in each cycle.
- **What a PASS would look like if the change did nothing:** round 3 on `bcf3b1a3`: `held=1`, `handoff_next=held`, `t_ssl2` 9912 ms and 9802 ms. `t_ssl2` does not separate the two builds; `held` and `handoff_next` do.
- **Stop rule:** 2 completed cycles, or 2 attempts. A cycle voided by `tap_save` is not completed. If U1 has fewer than 2 completed cycles, run it once more as `U1b-E` (same block, `r_open U1b-E`, a new tap budget). Never more than one `U1b-E`. Fewer than 1 completed cycle over both makes U1 INCONCLUSIVE.

### Closing

Run all of it, whatever happened before. If the round stopped early, start here.

1. `send ACTION_EXIT`. Then `adb -s $HU shell am start -a android.intent.action.VIEW -d headunit://exit`, `sleep 3`, `adb -s $HU shell am force-stop com.andrerinas.headunitrevived`.
2. **Reinstall the Prepare APK** so that E does not stay: `adb -s $HU install -r -d $APKS/prior-installed.apk`. Pull it again and check the md5 against Prepare step 4.
3. Restore D-POCO's backup with `pocoput $BASEXML` (no spec) and diff `settings.xml` against it. The diff must be empty.
4. `adb -s $HU shell dumpsys usb | grep -i -A4 headunitrevived > $OUT/dumpsys-usb-grants-after.txt`. Diff it against the Prepare copy and report any new grant.
5. `adb -s ZY22GC3BM4 shell svc power stayon false`.
6. `cue "H1c: unplug the dongle from D-POCO, and put D-POCO back on its PC cable"`. Then `adb -s $POCO_IP:5555 usb`.
7. `pgrep -fc "^adb .*logcat"` must print 0. `pgrep -f th_watch` must print nothing; kill any by pid.
8. The local `arm-E-r4` commit stays local. Do not push it.

### Stop rules

- `bat_gate` false (D-POCO at 25% or below): stop, run the Closing, and mark the rest UNTESTABLE (battery).
- U0 fails: mark U1 UNTESTABLE, run the Closing.
- U1 count rule: as in U1's stop rule, at most 2 runs (`U1-E`, `U1b-E`).
- The host thermal rules of `rig-quirks/topics/tooling.md` apply. `th_gate` gates U0, and `r_open` gates U1.

## 9. Do not re-run

- **R2A, R2K, R2T** (the stale-accessory ladder, the slot freed after each step, the trigger): round 3 PASS on `bcf3b1a3`. The diff since then touches only a scan sent while a Save is open, and the automation verbs send no Save (section 2).
- **P1** (the raise deadline bound): round 3 PASS. `ProjectionRaiseDeadlinePolicy` and the held end are unchanged.
- **RC5, RCK, RC1, RG, R3, R5u, R4p, R4w:** rounds 1 and 2, settled as round 3 section 9 says.
- **Items 3 and 4** (a replayed scan's second switch; a USB debt after a user exit): JVM tests only. U1 now grades `accstart` for item 3.
- **The `BootCompleteReceiver` crash on API 35:** not this branch (no diff against `main`). Report it; do not chase it.
- Two permission prompts per connect: structural (template §7b). Report any dialog; do not grade the count.

## 10. Report back

The numbers that decide shipping:

1. **U1, the hold:** per cycle `held`, `handoff` and `handoff_next`. The fix works when `held` is 0 and `handoff` is 1 or more.
2. **U1, no double switch:** per cycle `accstart` and `replay`.
3. **U1, the session behind the screen:** per cycle `ssl_before_close` and `t_ssl2`, against round 3's 9912 ms and 9802 ms.

Also report every cue, every injected tap, every line in `dialogs.log`, and the md5s of E and of the Prepare APK.

Put the captures in `usb-reattach-round4-captures.zip` on the release `rig-evidence-usb-reattach`, and quote its size and sha256 in the results file. Include `U0.tsv`, `U1-E.tsv` (and `U1b-E.tsv` if run), `keys.log`, `dialogs.log`, `hand-steps.log`, `taps.log`, `marks.log`, every `.keys` file, every `.thermal` file, and both logcat captures of each run.
