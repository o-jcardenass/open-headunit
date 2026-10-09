# session-reconnect, round 3 brief: the Native AA Save wakes the phone at once, and the USB Save keeps its retry

> **ADDENDUM, read first:** `pr-1047-session-reconnect-round3-addendum.md` moves this round onto the merged stack build (tree `2c4b06fd`). It replaces sections 1 and 1a below. Everything else stands.

This round tests four new commits on the outside contributor's session-reconnect change. Round 2 found that a Native AA Save took 32.5 to 34.1 s to reconnect, because the Save held the wake for the whole 30 s window. Round 2 also could not grade the USB Save. The new commits answer both. This round measures the two, plus the Disconnect action inside the window. It needs no other round first.

**This round is cut to the runs that decide the two open items, and it should be the last one on this PR.** The owner will merge nothing else into `main` until the contributor's stacked PRs merge, so `main` stays at `77914f18` and the build below does not need a re-pin. Do not add runs or cycles beyond what is written here.

## 1. Build and baseline

Two APKs, both from one tree. Build each with `build_hur.sh` and copy it out of `apks/` the moment it is built, because the script deletes the previous APK (`rig-quirks/topics/tooling.md`). Cool the host to 70C first and build with `--max-workers=2`.

| Arm | Code | Identity | Gate |
|---|---|---|---|
| **C** (candidate) | the PR head `7cc36de8212368c4e0099bb286a36362b44cfee8` (27 commits on `main` `77914f18585ea4dc973312c1303b3b5cd878fdeb`, no merge needed) | tree `a13a8e4934ab32bee184338b2d947c44c530d195` | 2901 JVM tests, 0 failures |
| **E** (export build, Stage U only) | C plus one manifest attribute: `android:exported="true"` on `.main.SettingsActivity` | C's tree plus that line | not run; the change is one XML attribute |

```bash
git fetch https://github.com/andreknieriem/open-headunit.git main pull/1047/head
git cat-file -e 7cc36de8212368c4e0099bb286a36362b44cfee8 || echo "C MISSING"
git merge-base --is-ancestor 77914f18585ea4dc973312c1303b3b5cd878fdeb 7cc36de8212368c4e0099bb286a36362b44cfee8 || echo "C NOT ON MAIN"
git checkout -B arm-C 7cc36de8212368c4e0099bb286a36362b44cfee8
git rev-parse 'HEAD^{tree}'                        # MUST print a13a8e4934ab32bee184338b2d947c44c530d195
# E: one attribute on top of C, committed locally only
git checkout -B arm-E arm-C
sed -i 's|android:name=".main.SettingsActivity"|android:name=".main.SettingsActivity"\n            android:exported="true"|' app/src/main/AndroidManifest.xml
git diff --stat                                    # MUST print 1 file changed, 1 insertion(+)
git -c user.name=rig -c user.email=rig@local commit -qam "rig: export SettingsActivity"
```

**The author rebased onto current `main` and kept the PR side of the #1080 conflict in `CommManager.kt`, as we asked.** We checked the app code: `app/src/main` of `7cc36de8` is identical to our own merge of the previous head `07f105f0` onto `77914f18`, which passed 2901 JVM tests with 0 failures. The only commit after `07f105f0` adds a CI step and a README for the host-connection harness.

**If the PR head moved again before the round starts** (`git ls-remote https://github.com/andreknieriem/open-headunit.git pull/1047/head`), or `C MISSING` or `C NOT ON MAIN` prints, or C's tree differs, stop the round and escalate. No baseline arm is built. Round 2's C (`b62d86c2` on `7102b428`) is the baseline, by its measured numbers (section 2). The local `arm-E` commit stays local: do not push it.

### 1a. Identity gate, per APK, before any run on it

```bash
adb -s $HU shell pm path $PKG                 # pull that apk to ./installed.apk, then:
unzip -p installed.apk 'classes*.dex' | strings | grep -cF 'SettingsRestart: route='     # C and E: 1 or more. Round 2's C: 0.
```

On E only, also: `aapt2 dump xmltree --file AndroidManifest.xml installed.apk | grep -A4 'main.SettingsActivity' | grep -c 'exported.*0xffffffff'` must print 1. Send `ACTION_QUERY_STATE` and record `commit`: it must start `7cc36de8`. Record each APK md5 from a real `adb pull` plus a local `md5sum`. The two must differ.

## 2. What this is and why it exists

On `main`, an audio settings Save with a session live stops the whole service. The PR ends only the projection session (`CommManager: audio settings changed; reconnecting the projection session`), publishes `Disconnected(reason = SETTINGS_RESTART)`, and retries the same route once inside a 30 s window (`SettingsRestartRecovery.WINDOW_MS`). At the end of the window, if no session formed, it hands back to the ordinary rules.

**Round 2 (`pr-1047-session-reconnect-round2-results.md`), on head `b62d86c2`:**

- **N2:** a Native AA Save reconnected in 32.5 to 34.1 s on all 5 cycles, and only after the 30 s fallback poke. The phone's log says why. The Save ends the session with a `ByeByeRequest(USER_SELECTION)`, Android Auto records `PROTOCOL_BYEBYE_REQUESTED_BY_CAR`, and prints `GAL was deliberately disconnected, so do not restart`. So the phone never comes back by itself after a Save, and the 30 s wake hold was pure delay. Round 1, on an older head that allowed the poke at once, reconnected in 2.0 to 6.6 s.
- **U1 and U2 were INCONCLUSIVE.** Our review then found the cause in code. The first failed USB open published a new `Disconnected`, so the Save's retries 2 to 4 returned at once. The dongle's re-attach came in through `UsbLauncherListener.onUsbAttach` without the Save, and was held by the open settings screen. So with the screen kept open past 30 s, the fallback armed wireless while the dongle was still plugged in.

**The four new commits:**

| Commit | Change | Run |
|---|---|---|
| `e1d709b9` | The Save's Native retry releases the wake hold after the old transport is torn down and wakes the phone at once. The 30 s window now bounds only the fallback. `ACTION_DISCONNECT` now sets the user-exit flag and stands the Native wake down. | **N2 (the point)**, ND |
| `8a8f9a9f` | The Helper fallback no longer recreates a live WiFi Direct group or restarts Nearby under a phone that is joining. | none (3.4) |
| `70fffaca` | A failed USB open keeps the Save (`settingsRetryOwner`). A re-attach, a detach cooldown or a permission reply inside the window inherits it. The fallback waits for an open in progress. New INFO lines: `SettingsRestart: route=<route> retry=run`, `fallback=run`, `fallback=skip_superseded`, `fallback=cancel_superseded`. | U1 |
| `8e84fe9a` | A connected Self launch retires its 10 s deadline. The dead Self VPN restore is removed. | none (3.4) |

## 3. What is different about this round

### 3.1 Stages, in this order

| Stage | Plugged into the test PC | Runs | Arm | Estimated time |
|---|---|---|---|---|
| **A** | D-HU (head unit, Native AA), D-POCO (phone), D-MOTO (radios off for the stage) | R0, A0, **N2 (the point)**, N2R only if needed, ND | C | 25 min, unattended |
| **U** | D-POCO as head unit on wireless adb with the dongle on OTG, D-MOTO on a cable as the dongle's phone | U0, U1 | E | 20 min, operator for the cables only |

With the two builds (about 10 min: E is a warm rebuild of C), the round takes about 55 min. Stage A needs no operator. Stage U needs one at its start and end, for the cables. If no operator is there for Stage U, mark U0 and U1 INCONCLUSIVE "operator not present".

### 3.2 The Save is two injected taps

No automation verb reaches the code under test. On C, `applyAudioSettings()` has exactly two callers, `AudioStreamSettingsFragment.saveSettings` and `SettingsFragment.saveSettings`. `ACTION_SET_SETTINGS` writes the preferences and calls neither, and `ACTION_RESTART_AUDIO` rebuilds tracks only. So each Save is made with the operator's injected-tap allowance (`rig-quirks/topics/tooling.md`): **at most 5 taps per run, each on a target found in a `uiautomator dump`, never a search, a scroll, a swipe or typed text.** `tap_save` in `lib1047r3.sh` does it, and it counts and refuses a sixth tap.

1. `open_audio` opens the audio streams screen by deep link (round 2's route), and `on_audio` confirms it is in front.
2. A dump must show exactly one `:id/settingSwitch`. That is the "Separate Audio Streams" toggle, the only toggle on this screen (`AudioStreamSettingsFragment.updateSettingsList`). Tap 1 is its centre.
3. A second dump must show `:id/save_button_widget` with `enabled="true"` and the text `Save (Reconnect needed)`. That text proves the screen holds a change. Tap 2 is its centre.
4. The script waits up to 20 s for the Save line, and times everything from it.

**If a target does not resolve** (0 or 2 or more matches, or the Save button is not armed), `tap_save` taps nothing more, records the reason, and the cycle is void. Do not retry with a guessed coordinate. The screen has no text field, so no keyboard can open. Every tap goes in `$OUT/taps.log` with its run, its target and its coordinates, and Setup notes list them all.

**Tap budget per run:** N2 makes 2 Saves (4 taps). If N2 ends with fewer than 2 graded cycles, N2R repeats it once (4 taps). ND makes 1 Save, or 2 if the first cycle cannot be graded (2 or 4 taps). U1 makes 2 Saves (4 taps). A0 dumps the screen and taps nothing.

The only hand steps left are physical, and Stage U asks for them in one batch before U0:

| Id | Step | Reason no verb exists |
|---|---|---|
| H1 | Move D-POCO between its PC cable and wireless adb; plug the dongle into D-POCO's OTG port at U0, and unplug it at the end | A cable is hardware. |
| H2 | Allow the system USB permission dialog on D-POCO with "Always", only if one appears | A system dialog on MIUI; its layout is not ours to dump. |
| H4 | Unlock D-MOTO once if it shows a PIN | adb has no way past a PIN (`rig-quirks/units/D-MOTO.md`). |

### 3.3 Rig facts that change the runs

- **D-POCO often does not answer a poke after a restart** (round 2: N1-3 and D1, HFP-AG and HSP-AG connects fail with `read failed`). The immediate wake makes this matter more, because the first poke now decides the reconnect time. So N2 records a cycle whose first poke failed as `poke unanswered` and does not grade it (section 7). A poke at a phone whose Bluetooth is off blocks about 15.4 s in `socket.connect()` and then fails; that is expected.
- **A persistent WiFi Direct group lets another paired phone rejoin with no Bluetooth** (`rig-quirks/units/D-HU.md`). D-MOTO's radios are off for Stage A.
- **`native-aa-wake-damage-verdict` latches and stands every later poke down** (`rig-quirks/topics/bt.md`). Stage A writes it to `0`.
- **D-POCO's Bluetooth can self-revert** (`rig-quirks/topics/bt.md`). ND reads it at a fixed point with `dumpsys`; a cycle where it reads on when it must be off is void.
- **D-POCO (Android 15, MIUI) cannot open our settings screen from the shell** on stock C (`am start` is denied because `SettingsActivity` is not exported, and `run-as ... am start` fails on the uid). Round 2 used the export build with the operator's approval. This round does the same (arm E), for Stage U only. D-HU runs the unmodified C.
- **The dongle drops off USB at every session end and comes back by itself** (`archive/rounds/pr-1065-reconnect-timers-round1-results.md`): it detaches about 1.6 s after the session ends, re-attaches in normal mode (`PID 4EE1`) about 4.5 s later, and asks for USB permission. So a USB Save makes a detach and an attach with no operator action. The first USB attempt after a launch can fail on stale data and retry; round 2 saw 12 failed attempts in a row before one held. That is not a failure.
- **The settings screen pauses the wireless stack only when it opens with no session live.** Every Save here opens the screen over a live session, so nothing pauses. Do not reopen the settings screen inside a window.
- **The phone logs `Critical error` lines at every session end we cause, on every build.** Report `crit`; never grade it. A `Critical error` line that names a protocol refusal (for example `Multiple media configs received`) is a FAIL; quote it.
- **Every watcher reads the capture file.** A `logcat | grep` pipe never fired on this rig.
- **`ohu_setkeys.py` wrote an empty `set:` as one empty string** until a fix on 2026-10-08 (`rig-quirks/topics/tooling.md`). Check the copy you use: `grep -c 'if v else \[\]' ohu_setkeys.py` must print 1. If it prints 0, apply the one-line fix the quirk names.
- **The rig audio settings are a deliberate worst case** (`use-aac-audio`, `audio-latency-multiplier`, `audio-queue-capacity`). Do not change them. Read them back and record them. Delete `video-profile-starvation-cap` before every run.

### 3.4 What this round does not run, and why

- **The Helper fix (`8a8f9a9f`).** The Wireless Helper cannot start Android Auto 17.9 on either rig phone (round 2, section 3.1). The fix stays covered by its JVM tests (`HelperSettingsRefreshTest`, `NearbySettingsRestartTest`) and the review.
- **The Self Mode deadline (`8e84fe9a`).** Android Auto 17.9 needs its head unit server for Self Mode, which is a hand step on the phone, and the change only retires a timer. JVM tests (`SelfManualDeadlineTest`) and the host suite cover it.
- **N1 (a Native Save with the phone's Bluetooth off).** Round 2 already measured the hold and the 30 s fallback on that path. The new code only releases the hold earlier, which N2 measures directly.
- **U2 (a USB Save with the dongle unplugged).** It needs an unplug faster than the dongle's own 6 s return, which is a race the operator often loses. The fallback's wait for an open in progress is covered by `UsbSettingsRetryLoopTest` and the host suite.
- **Headunit Server (HS), the GLES cover sequence (N3) and D-SAM (D1):** see section 8.

### 3.5 Pre-registered INCONCLUSIVE outcomes

So that none is read as a failure: N2 with fewer than 2 graded cycles across N2 and N2R (D-POCO's unanswered pokes); a cycle voided by `tap_save` (a target did not resolve); ND with no graded cycle in 2 attempts; U1 when U0 fails; the audio leg of N2 when the first session prints no `AudioDecoder.start: channel=` line.

## 4. Settings keys

Write with the app stopped and read back before every launch. D-HU: `hu_put` (root). D-POCO as head unit: `pocoput`. Take one backup per unit at the start of the round and write every run from it.

| Key | Type | A (D-HU) | U1 (D-POCO) | U2 (D-POCO) |
|---|---|---|---|---|
| `wifi-connection-mode` | int | `3` | `3` | (base only) |
| `log-level` | int | `2` | `2` | `2` |
| `onboarding-version` | int | `2` | `2` | `2` |
| `kill-on-disconnect` | boolean | `false` | `false` | `false` |
| `native-aa-wake-damage-verdict` | int | `0` | `0` | |
| `auto-connect-last-session` | boolean | | `true` | `true` |
| `auto-start-on-usb`, `reopen-on-reconnection` | boolean | | `true` | `true` |
| `use-libusb` | boolean | | `false` | `false` |
| `connection-modes` | string set | | `usb,wifi` | `usb,wifi` |
| `video-profile-starvation-cap`, `native-aa-wireless`, `wifi-launcher-mode` | | delete | delete | delete |

`U2KEYS` is only the base that `U1KEYS` is built from; no U2 run exists this round. U1 runs Native AA as the wireless mode on purpose: an armed wireless stack prints `WifiLauncher: Initializing WiFi Mode: NATIVE`, which is what U1 counts. Read and record, do not write: the three rig audio keys, `separate-audio-streams`, `native-poke-bt-macs`, `native-poke-all-paired`. `ACTION_LOG_MARKER` is not in `AutomationCommandPolicy.CONFIGURING` on C, so `allow-external-configuration` is not needed.

```bash
AKEYS="int:wifi-connection-mode=3 int:log-level=2 int:onboarding-version=2 bool:kill-on-disconnect=false int:native-aa-wake-damage-verdict=0 del:video-profile-starvation-cap del:native-aa-wireless del:wifi-launcher-mode"
U2KEYS="int:wifi-connection-mode=1 int:log-level=2 int:onboarding-version=2 bool:kill-on-disconnect=false bool:auto-connect-last-session=true bool:auto-start-on-usb=true bool:reopen-on-reconnection=true bool:use-libusb=false set:connection-modes=usb,wifi del:video-profile-starvation-cap del:native-aa-wireless del:wifi-launcher-mode"
U1KEYS="${U2KEYS/wifi-connection-mode=1/wifi-connection-mode=3} int:native-aa-wake-damage-verdict=0"
```

## 5. The lines that decide the runs

Each unit line was checked with `grep -F` against `app/src/main` and `contract/src` of the C tree (`a13a8e49`). Every one prints at INFO, so `log-level=2` carries all of them. The `SettingsRestart:` lines are new in `70fffaca` and are composed at run time: the source holds `SettingsRestart: route=$route retry=` and `fallback=` followed by `"run"` or `"skip_superseded"`, so `retry=run`, `fallback=run` and `fallback=skip_superseded` print whole but do not grep whole in the source. `fallback=cancel_superseded` is one literal. The `<route>` is `NATIVE` for a Native AA Save and `USB` for a USB Save.

| Line (fixed substring) | File | Meaning |
|---|---|---|
| `CommManager: audio settings changed; reconnecting the projection session` | unit | **the Save** (anchor, T0) |
| `SettingsRestart: route=NATIVE retry=run`, `SettingsRestart: route=USB retry=run` | unit | the Save's retry started on that route |
| `fallback=run`, `fallback=skip_superseded`, `fallback=cancel_superseded` | unit | the 30 s fallback ran, found a newer state, or was cancelled at once |
| `AapService: session state ` then `disconnected (settings_restart)` / `(link_lost)` / `(user_exit)` | unit | what the session end was called |
| `AapService: automatic wake is paused by the session policy. Keeping listeners available.` | unit | the wake hold refused a credential-driven poke |
| `NativeAA: Attempting active poke to device` | unit | **a wake poke started** (printed after the wake permission check) |
| `NativeAA: Calling socket.connect()` | unit | the poke really dialled |
| `NativeAA: Successfully poked ` | unit | the phone accepted the poke socket |
| `NativeAA: Poke via ` with `failed:` | unit | the poke socket failed |
| `alone for now: this unit woke it over its hands-free link` | unit | the wake escalation gave up on that phone |
| `Disconnect action received.` | unit | `ACTION_DISCONNECT` reached the service |
| `NativeAA: deliberate session end; reopening listeners without an automatic wake.` | unit | **the Native wake stood down** (new on Disconnect in `e1d709b9`) |
| `AapService: userExitedAA is true. Skipping auto-poke.` | unit | a user-exit stand-down at a credential delivery |
| `AapService: WiFi Direct credential refresh requested.`, `AapService: Received WiFi credentials from manager` | unit | a group re-read, and the credentials it delivered |
| `WifiLauncher: Initializing WiFi Mode: ` then `NATIVE` | unit | a wireless bring-up started |
| `AapService: Native AA session ended; keeping the ` | unit | the ordinary Native session-end path (must not run on a Save) |
| `Stop action received. Broadcasting finish request to activities.` | unit | a service stop (must not run on a Save) |
| `SSL handshake complete` | unit | a session formed (INFO form; never prefix `Handshake:`) |
| `Throughput over ` with `rendered=` | unit | video reached the decoder |
| `AudioDecoder.start: channel=` | unit | an audio sink started |
| `AapService: raising the projection by `, `AapService: Not raising the projection, the settings screen is open` | unit | the raise after a reconnect, or its refusal behind the screen |
| `UsbLauncher: USB auto-connect held while the settings screen is open; ` | unit | **a USB check with no Save was held by the screen** (what round 2's re-attach did) |
| `USB accessory device attached, connecting.` | unit | the dongle came back in accessory mode (the `onUsbAttach` path) |
| `Accessory detach cooldown finished. Checking for re-connection...` | unit | the detach cooldown check |
| `USB permission granted for ` | unit | a permission reply arrived |
| `Found device already in accessory mode`, `Switching USB device to accessory mode`, `Single USB auto-connect: connecting to` | unit | a USB attempt began |
| `USB Intent: ` with `USB_DEVICE_DETACHED` / `USB_DEVICE_ATTACHED` | unit | the dongle left or came back |
| `stopping the wireless stack for the duration of it` | unit | the wired-session quiesce at a USB SSL |
| `AapService: USB disconnect. Scheduling reconnect check in ` | unit | the ordinary USB retry |
| `MATCH! Starting AapService`, `AapRead: Magic Garbage detected in header`, `createGroup SUCCESS` | unit | discard rules |
| `AutomationReceiver: `, `AutomationMarker: ` | unit | verb proof and markers |
| `GAL was deliberately disconnected, so do not restart` | phone (D-POCO) | **Android Auto will not come back by itself** (round 2's N2 phone capture) |
| `Critical error` | phone | reported, not graded |
| `FATAL EXCEPTION` | unit, phone | a crash (system line) |

## 6. Shell setup and every action

Make the folder `hur-wifi-test-scripts/pr-1047-session-reconnect-round3/`. Copy `ohu_lib.sh`, `ohu_setkeys.py`, `ptr_lib.sh` and `lib1047.sh` into it from `hur-wifi-test-scripts/pr-1047-session-reconnect-round2/` (that `lib1047.sh` already carries round 2's 180 s `fire_on` fix). Check `ohu_setkeys.py` as section 3.3 says. Save `lib1047r3.sh` below beside them. Source them in that order. The thermal rules in `rig-quirks/topics/tooling.md` apply: `apk_check` calls `th_gate` before every run. Run each stage as one script in the background under `flock /tmp/ohu-rig.lock`, watch `$OUT/hand-steps.log`, and relay each new `OPERATOR_STEP` line to the operator at once. If a helper does not match the real line format, fix it, say so in Setup notes, and keep going.

```bash
HU=27870808938846; PH=4f4027e9; MOTO=ZY22GC3BM4; DHU=$HU
OUT=~/hur-wifi-test-scripts/pr-1047-session-reconnect-round3; mkdir -p $OUT; cd $OUT
PUT=hu_put; BASEXML=$OUT/settings-backup-DHU.xml; UNIT=D-HU
source ./ohu_lib.sh; source ./ptr_lib.sh; source ./lib1047.sh; source ./lib1047r3.sh
ARM=C; WANT_MD5=<C md5>        # set ARM and WANT_MD5 again after every install
```

**`lib1047r3.sh`** (round 2's `lib1047.sh` gives `ms`, `lno`, `tat`, `dt`, `cnt`, `pcnt`, `cue`, `cntw`, `firstw`, `hold_until`, `dest_id`, `open_audio`, `close_settings`, `hand_save`, `fire_on`, `phone_bt`, `bt_off`, `up_session`, `r_open`, `r_close`, `pocoput`, `usb_live`):

```bash
# lib1047r3.sh : source after lib1047.sh.
POKE='NativeAA: Attempting active poke to device'
BYE='GAL was deliberately disconnected, so do not restart'
# firstin <fixed> <a_ms> <b_ms> : ms after T0 of the first line in [a, b] containing <fixed>; NA if none
firstin() { tail -n +"$SAVE_LN" "$CAP" | awk -v t0="$T0" -v s="$1" -v a="$2" -v b="$3" 'function m(x,p){split(x,p,/[:.]/);return ((p[1]*60+p[2])*60+p[3])*1000+p[4]} index($0,s){d=m($2)-t0; if(d>=a&&d<=b){print d; f=1; exit}} END{if(!f)print "NA"}'; }
# pokefail <a_ms> <b_ms> : failed poke sockets in the window
pokefail() { tail -n +"$SAVE_LN" "$CAP" | awk -v t0="$T0" -v a="$1" -v b="$2" 'function m(x,p){split(x,p,/[:.]/);return ((p[1]*60+p[2])*60+p[3])*1000+p[4]} index($0,"NativeAA: Poke via ")&&index($0,"failed:"){d=m($2)-t0; if(d>=a&&d<=b)n++} END{print n+0}'; }
# ms_of <line> : ms after T0 of a capture line; NA if 0
ms_of() { [ "$1" -gt 0 ] && echo $(( $(ms "$(tat $1)") - T0 )) || echo NA; }
# usb_tries <from> <to> : USB attempt lines in capture lines from..to
usb_tries() { echo $(( $(cnt $1 $2 'Found device already in accessory mode') + $(cnt $1 $2 'Switching USB device to accessory mode') + $(cnt $1 $2 'Single USB auto-connect: connecting to') )); }
# --- the Save as two injected taps (section 3.2) ---
eval "orig_$(declare -f r_open)"; r_open() { TAPS=0; orig_r_open "$@"; }      # the tap budget is per run
ui_dump() { adb -s "$HU" shell uiautomator dump /sdcard/ohu-ui.xml >/dev/null 2>&1; adb -s "$HU" exec-out cat /sdcard/ohu-ui.xml > "$OUT/ui.xml"; [ -s "$OUT/ui.xml" ]; }
# target <id> [text] : centre "x y" of the one node whose resource-id ends ":id/<id>" (and is enabled with that text); fails on 0 or 2+
target() { python3 -I -c '
import sys,re,xml.etree.ElementTree as ET
want,txt=sys.argv[2],(sys.argv[3] if len(sys.argv)>3 else None)
n=[e for e in ET.parse(sys.argv[1]).iter("node") if e.get("resource-id","").endswith(":id/"+want)]
if txt is not None: n=[e for e in n if e.get("enabled")=="true" and e.get("text","").lower()==txt.lower()]
if len(n)!=1: sys.exit("matches=%d"%len(n))
x1,y1,x2,y2=map(int,re.findall(r"\d+",n[0].get("bounds"))); print((x1+x2)//2,(y1+y2)//2)' "$OUT/ui.xml" "$@"; }
tap_xy() { [ "${TAPS:-0}" -ge 5 ] && { echo "$(date +%T) $RUN REFUSED tap 6 ($1)" >> "$OUT/taps.log"; return 1; }
  TAPS=$((TAPS+1)); echo "$(date +%T) $RUN tap$TAPS $1 $2 $3" >> "$OUT/taps.log"; adb -s "$HU" shell input tap "$2" "$3"; }
# tap_save <label> : open the screen, tap the toggle, tap Save, wait for the Save line. Sets SAVE_LN, T0, H0. 2 = void cycle.
tap_save() { local L xy; L=$(nl)
  open_audio || { echo -e "$1\tNO_AUDIO_SCREEN" | tee -a "$OUT/$RUN.tsv"; return 2; }
  ui_dump && xy=$(target settingSwitch) || { echo -e "$1\tNO_TOGGLE_TARGET" | tee -a "$OUT/$RUN.tsv"; return 2; }
  mark "$1-tap-toggle"; tap_xy toggle $xy || return 2; sleep 1.5
  ui_dump && xy=$(target save_button_widget 'Save (Reconnect needed)') || { echo -e "$1\tSAVE_NOT_ARMED" | tee -a "$OUT/$RUN.tsv"; return 2; }
  mark "$1-tap-save"; tap_xy save $xy || return 2
  fastwait 20 "$SAVE_C" "$L" || { echo -e "$1\tNO_SAVE_LINE" | tee -a "$OUT/$RUN.tsv"; return 2; }
  SAVE_LN=$(lno "$L" "$SAVE_C"); T0=$(ms "$(tat $SAVE_LN)"); H0=$(date +%s%3N); mark "$1-saved"; }
```

Every action in this round:

| Action | Command | Notes |
|---|---|---|
| The WiFi button (arm) | `send ACTION_START_WIRELESS_SCAN` | D-HU; a user request, so it also lifts a user-exit stand-down |
| Raise the projection | `send ACTION_RAISE_PROJECTION` | only inside `up_session` |
| The Disconnect action | `send ACTION_DISCONNECT` | ND only |
| Stop the service | `send ACTION_EXIT` | only to a running app |
| Mark a step | `mark <label>` | `AutomationMarker: <label>` at WARN |
| Read the build | `send ACTION_QUERY_STATE` | `commit` on the `data=` line |
| Open the audio streams screen | `open_audio` | not a verb; template section 3 deep-links settings this way |
| Close the settings screen | `close_settings` | `input keyevent 4`, a scripted keyevent |
| Media play | `adb -s $HU shell input keyevent KEYCODE_MEDIA_PLAY` | relayed to the phone's player |
| Launch our app | `adb -s $HU shell am start -n $MAIN` | D-POCO refuses `ACTION_START_WIRELESS_SCAN` after a force-stop |
| Phone Bluetooth | `adb -s $PH shell svc bluetooth disable` / `enable` | ND |
| The Save | `tap_save <label>` | two injected taps on dumped targets (3.2) |

Every `send` must print `AutomationReceiver: <action>` in the capture. A step with no such line never landed; its cycle is void, not a FAIL.

## 7. Runs

**The point of the round is N2.** ND checks that a Disconnect stops the wake (`e1d709b9`). U1 grades `70fffaca`.

**Per-run rules.** Before each run: the identity gate (1a) when the APK changed, a settings read-back, and the discard rules of template section 4 per cycle: `MATCH! Starting AapService`, `AapRead: Magic Garbage detected in header`, or a second `createGroup SUCCESS` inside one Native cycle where no group change was asked for voids that cycle. **Preflight per run:** the run's own capture must show `WifiLauncher: Initializing WiFi Mode: NATIVE` before its first session (Stage A and U1) or a USB SSL with `stopping the wireless stack for the duration of it` (U1). If it does not, the run is a setup failure: fix the settings and re-run it once, and never grade it. **Every run keeps a phone capture beside the unit capture** (`pcap_start` inside `r_open`) and grades at least one condition from it. Write the results file after every run.

### R0 Gate (once)

PASS needs: C's and E's md5s recorded and different; C's tree as stated; the 1a checks matching per APK; `commit` recorded; no `run_unit_tests.sh` this round: the PR's own CI run on `7cc36de8` passed its unit tests, and we ran 2901 with 0 failures on the same app code; `adb install -r` succeeding, never an uninstall; each unit's settings backup diffed against the file after each install, with the delta stated; D-HU's `stat -c %U:%G` of `shared_prefs/`; the rig audio keys and `separate-audio-streams` as found; Gearhead's versionName on D-POCO and D-MOTO; `ohu_setkeys.py` checked (3.3). Time: 3 min plus the builds.

### A0 The audio streams screen opens (D-HU, before the first Save)

As round 2's A0, on C only: `dest_id` prints a non-zero id, `open_audio` returns 0 with route `am`, and the screenshot shows the "Separate Audio Streams" toggle and the Save button. Then `ui_dump && target settingSwitch && target save_button_widget` must print two coordinate pairs (the Save button exists but is not armed yet, so do not pass a text here). No tap in A0. A FAIL makes every Stage A run UNTESTABLE. Repeat at U0 on D-POCO with E (route `am` expected). Time: 2 min.

### Stage A preparation (once)

```bash
adb -s $MOTO shell svc bluetooth disable; adb -s $MOTO shell svc wifi disable; sleep 5
phone_bt $MOTO; adb -s $MOTO shell dumpsys wifi | grep -a -m1 'Wi-Fi is'        # both must read off
adb -s $PH shell dumpsys window | grep mCurrentFocus                             # not a Settings screen; else input keyevent KEYCODE_HOME
phone_bt $PH                                                                      # must read on
adb -s $HU shell am force-stop $PKG; adb -s $HU shell cat /data/data/$PKG/shared_prefs/settings.xml > $BASEXML
```

Re-enable D-MOTO's radios at the end of Stage A and read both back.

### N2 A Native AA Save where the phone is woken at once (the point; C, 2 cycles, 4 taps; N2R repeats it once if needed)

The claim: after the Save, the retry wakes the phone within a few seconds, and the session comes back well inside the 30 s window. Round 2 measured 32.5 to 34.1 s on the old head.

```bash
r_open N2-C $AKEYS; L=$(nl); send ACTION_START_WIRELESS_SCAN >/dev/null; up_session 120 $L || echo "N2 first session failed"
echo "N2 preflight native=$(cnt $L '$' 'WifiLauncher: Initializing WiFi Mode: NATIVE')" | tee -a $OUT/N2-C.tsv     # 0: setup failure
adb -s $HU shell input keyevent KEYCODE_MEDIA_PLAY; sleep 10
AUDIO_OK=$(cnt $L '$' 'AudioDecoder.start: channel='); echo "N2 audio precondition=$AUDIO_OK" | tee -a $OUT/N2-C.tsv   # 0: the audio leg is INCONCLUSIVE
graded=0
for k in 1 2; do
  S=$(nl); p0=$(pcnt 'Critical error'); b0=$(pcnt "$BYE"); f0=$(pcnt 'FATAL EXCEPTION'); mark N2-$k-go
  tap_save N2-$k || continue
  close_settings
  waitfor 60 'SSL handshake complete' $SAVE_LN; LS=$(lno $SAVE_LN 'SSL handshake complete')
  waitfor 30 "$REND" ${LS:-$SAVE_LN}; LV=$(tail -n +$(( LS>0 ? LS : SAVE_LN )) "$CAP" | grep -anE -m1 "$REND" | cut -d: -f1)
  sleep 3; adb -s $HU shell input keyevent KEYCODE_MEDIA_PLAY; sleep 12; E=$(nl)
  T2=$(firstw 'SSL handshake complete'); TP=$(firstw "$POKE")
  R="N2-$k\tt_retry=$(firstw 'SettingsRestart: route=NATIVE retry=run')\tt_poke=$TP\tt_ssl2=$T2"
  R="$R\tpokefail_before_ssl2=$( [ "$T2" != NA ] && pokefail 0 $T2 || pokefail 0 60000 )\tpoked_before_ssl2=$( [ "$T2" != NA ] && cntw 0 $T2 'NativeAA: Successfully poked ' || echo NA )"
  R="$R\thold=$(cntw 0 30000 'AapService: automatic wake is paused by the session policy')\tssl_count=$(cntw 0 40000 'SSL handshake complete')"
  R="$R\tt_video2=$( [ -n "$LV" ] && [ $LS -gt 0 ] && dt $LS $(( LS + LV - 1 )) || echo NA )\taudio=$( [ $LS -gt 0 ] && cnt $LS $E 'AudioDecoder.start: channel=' || echo NA )"
  R="$R\tfallback_run=$(cntw 0 60000 'fallback=run')\tfallback_skip=$(cntw 25000 40000 'fallback=skip_superseded')"
  R="$R\tsettings_restart=$(cntw 0 3000 '(settings_restart)')\tlink_lost=$(cntw 0 3000 '(link_lost)')\tuser_exit=$(cntw 0 3000 '(user_exit)')\tkeep=$(cnt $SAVE_LN $E 'AapService: Native AA session ended; keeping the ')\tstop=$(cnt $SAVE_LN $E 'Stop action received.')"
  R="$R\traise=$(cnt $SAVE_LN $E 'AapService: raising the projection by ')\tbye_phone=$(( $(pcnt "$BYE") - b0 ))\tcrit=$(( $(pcnt 'Critical error') - p0 ))\tfatal=$(cnt $S $E 'FATAL EXCEPTION')\tphone_fatal=$(( $(pcnt 'FATAL EXCEPTION') - f0 ))"
  echo -e "$R" | tee -a $OUT/N2-C.tsv
  # graded unless the first poke failed and the session was late (D-POCO's unanswered poke)
  if [ "$T2" != NA ] && [ "$T2" -le 15000 ]; then graded=$((graded+1)); elif [ "$(pokefail 0 20000)" -gt 0 ]; then echo -e "N2-$k\tpoke unanswered" | tee -a $OUT/N2-C.tsv; else graded=$((graded+1)); fi
  [ $graded -ge 2 ] && break; sleep 10
  [ "$T2" = NA ] && { L=$(nl); send ACTION_START_WIRELESS_SCAN >/dev/null; up_session 120 $L || break; }
done
r_close N2-C
```

**A cycle is not graded** (`poke unanswered`) when `t_ssl2` is above 15000 or NA and `pokefail_before_ssl2` (failed poke sockets in the first 20 s) is 1 or more. Report it with its numbers. **Stop rule:** N2 makes 2 attempts. **N2R:** if N2 graded fewer than 2 cycles, run the same block once more as a new run, `r_open N2R-C $AKEYS` with `N2R-$k` labels and `$OUT/N2R-C.tsv`, 2 more attempts and its own 4-tap budget. Grade N2 and N2R together. Fewer than 2 graded cycles across both makes N2 INCONCLUSIVE.

**PASS, on every graded cycle:**

- `t_ssl2` 15000 ms or less (round 2's C: 32558 to 34086 ms);
- `t_poke` 5000 ms or less, and `t_retry` 3000 ms or less: the wake ran at once, not at 30 s;
- `ssl_count` 1;
- `fallback_run` 0, and `fallback_skip` 1: the fallback found the new session and did nothing;
- `settings_restart` 1, `link_lost` 0, `user_exit` 0, `keep` 0, `stop` 0;
- `t_video2` 30000 ms or less; `audio` 1 or more (if the precondition held); `raise` 1 or more;
- `fatal` 0;
- phone (graded): `bye_phone` 1 or more, which proves Android Auto did not plan to come back by itself, so the reconnect is our wake; `phone_fatal` 0. Report `crit`; it is not graded.

Report `hold` and `poked_before_ssl2` as numbers. **What a PASS would look like if the change did nothing:** `t_poke` near 30000, `t_ssl2` 32000 to 34000, and `fallback_run` 1, exactly round 2's shape. **FAIL** on any graded cycle with `t_poke` above 29500 or NA (the hold is still in force), `t_ssl2` above 15000 with no failed poke before it, `ssl_count` 2 or more, or `fallback_run` 1. Time: about 10 min.

### ND The Disconnect action inside the window stops the wake (C, 1 graded cycle, max 2 attempts)

The claim (`e1d709b9`): `ACTION_DISCONNECT` during the Save's window sets the user exit and stands the Native wake down, so no new poke starts until the user arms the stack again. Before the change, the next credential delivery could poke the phone seconds after the Disconnect.

```bash
r_open ND-C $AKEYS; L=$(nl); send ACTION_START_WIRELESS_SCAN >/dev/null; up_session 120 $L || echo "ND first session failed"
graded=0
for k in 1 2; do
  S=$(nl); mark ND-$k-go; b0=$(pcnt "$BYE")
  fire_on $S 'CommManager: audio settings changed; reconnecting the projection session' adb -s $PH shell svc bluetooth disable
  tap_save ND-$k || { kill $FIREPID 2>/dev/null; continue; }
  close_settings
  hold_until 8000; D=$(nl); send ACTION_DISCONNECT >/dev/null; TD=$(ms_of $(lno $D 'Disconnect action received.'))
  hold_until 12000; adb -s $PH shell svc bluetooth enable
  hold_until 50000; E=$(nl)
  R="ND-$k\tt_disc=$TD\tpokes_before_disc=$(cntw 0 ${TD/NA/8000} "$POKE")\tstand_down=$(cnt $D $E 'NativeAA: deliberate session end; reopening listeners without an automatic wake.')"
  R="$R\tpokes_after_disc=$(cntw $(( ${TD/NA/8000} + 1000 )) 50000 "$POKE")\tskip_line=$(cnt $D $E 'AapService: userExitedAA is true. Skipping auto-poke.')\tssl_after_disc=$(cnt $D $E 'SSL handshake complete')"
  R="$R\tfallback_run=$(cntw 0 50000 'fallback=run')\tfallback_skip=$(cntw 25000 45000 'fallback=skip_superseded')\tbt12=$(bt_off $PH && echo off || echo on)\tbye_phone=$(( $(pcnt "$BYE") - b0 ))\tfatal=$(cnt $S $E 'FATAL EXCEPTION')"
  L=$(nl); send ACTION_START_WIRELESS_SCAN >/dev/null; up_session 120 $L && R="$R\tre_arm=1\tt_rearm=$(dt $L $(lno $L 'SSL handshake complete'))" || R="$R\tre_arm=0"
  echo -e "$R" | tee -a $OUT/ND-C.tsv
  echo -e "$R" | grep -qE 'pokes_before_disc=[1-9]' && graded=$((graded+1))
  [ $graded -ge 1 ] && break; sleep 10
done
r_close ND-C
```

**A cycle is graded** when `pokes_before_disc` is 1 or more (the wake was running, so a zero after the Disconnect means something). **Stop rule:** stop at the 1st graded cycle or after 2 attempts. No graded cycle makes ND INCONCLUSIVE.

**PASS, on every graded cycle:** `t_disc` 10000 ms or less; `stand_down` 1 or more; `pokes_after_disc` 0 from 1 s after the Disconnect to Save + 50 s, with the phone's Bluetooth on from Save + 12 s (`bt12` reads on); `ssl_after_disc` 0; `fallback_run` 0; `re_arm` 1 (the WiFi button brings the phone back); `fatal` 0. Phone (graded): `bye_phone` 1 or more. Report `skip_line` and `fallback_skip`. A poke that was already inside `socket.connect()` at the Disconnect finishes by itself and prints no new `Attempting active poke` line, so it does not count. **What a PASS would look like if the change did nothing:** `pokes_after_disc` 1 or more within about 15 s of the Bluetooth coming back, and often an SSL. **FAIL:** `pokes_after_disc` 1 or more, or `ssl_after_disc` 1 or more. Time: about 5 min.

### U0 Stage U setup and gate (once)

Follow `archive/rounds/pr-1065-reconnect-timers-round1-brief.md` U0 steps for wireless adb, D-POCO's own head unit server (must not listen), D-MOTO's Bluetooth (must be on), and `dumpsys usb` before. Do not start `fake5277.py`. Then:

```bash
POCO_IP=<D-POCO wlan0 address>; HU=$POCO_IP:5555; PH=$MOTO; PUT=pocoput; BASEXML=$OUT/settings_backup_pr1047_poco.xml; UNIT=D-POCO
adb -s $HU shell am force-stop $PKG; adb -s $HU shell run-as $PKG cat shared_prefs/settings.xml > $BASEXML
adb -s $HU install -r <E apk>; ARM=E; WANT_MD5=<E md5>       # then the 1a gate and A0 on D-POCO
```

PASS: wireless adb answers; D-POCO's port table shows no 5277 listener; A0 on D-POCO passes with route `am`; with `U1KEYS`, a launch and `L=$(nl); cue "H1: plug the dongle into D-POCO's OTG port"; usb_live $L` forms a USB session, with `stopping the wireless stack for the duration of it` 1 or more after it. Tick "Always" on the first permission dialog (H2). A failed U0 makes U1 UNTESTABLE with the reason. Time: 10 min.

### U1 A USB Save with the settings screen kept open for 35 s (E, 2 cycles, 4 taps)

The claim (`70fffaca`): the Save keeps owning the dongle's return. Its retries survive a failed open, the re-attach in accessory mode carries the Save instead of being held by the screen, and a USB session forms behind the open screen. Wireless does not arm in the window. Round 2 could not grade this.

```bash
r_open U1-C $U1KEYS; L=$(nl); adb -s $HU shell am start -n $MAIN >/dev/null; usb_live $L || echo "U1 no USB session at start"
echo "U1 preflight native=$(cnt $L '$' 'WifiLauncher: Initializing WiFi Mode: NATIVE') quiesce=$(cnt $L '$' 'stopping the wireless stack for the duration of it')" | tee -a $OUT/U1-C.tsv   # both 1 or more
done=0
for k in 1 2; do
  S=$(nl); p0=$(pcnt 'Critical error'); f0=$(pcnt 'FATAL EXCEPTION'); mark U1-$k-go
  tap_save U1-$k || continue
  hold_until 35000; C0=$(nl); close_settings; waitfor 60 'SSL handshake complete' $C0; E=$(nl)
  FS=$(lno $SAVE_LN 'SSL handshake complete')
  R="U1-$k\tretry=$(cntw 0 5000 'SettingsRestart: route=USB retry=run')\tdetach=$(cntw 0 35000 'USB_DEVICE_DETACHED')\treattach=$(cntw 0 35000 'USB accessory device attached, connecting.')"
  R="$R\theld=$(cntw 0 35000 'UsbLauncher: USB auto-connect held while the settings screen is open; ')\ttries_before_ssl=$( [ $FS -gt 0 ] && usb_tries $SAVE_LN $FS || echo NA )\tt_ssl2=$(ms_of $FS)"
  R="$R\tssl_before_close=$(cnt $SAVE_LN $C0 'SSL handshake complete')\tquiesce=$( [ $FS -gt 0 ] && cnt $FS $E 'stopping the wireless stack for the duration of it' || echo NA )\tarm_in_window=$(cntw 0 35000 'WifiLauncher: Initializing WiFi Mode: NATIVE')\tpokes_in_window=$(cntw 0 35000 "$POKE")"
  R="$R\tfallback_run=$(cntw 0 60000 'fallback=run')\tfallback_other=$(( $(cntw 0 60000 'fallback=skip_superseded') + $(cntw 0 60000 'fallback=cancel_superseded') ))\traise_refused=$(cnt $SAVE_LN $C0 'AapService: Not raising the projection, the settings screen is open')"
  R="$R\tsettings_restart=$(cntw 0 3000 '(settings_restart)')\tusb_retry_line=$(cnt $SAVE_LN $E 'AapService: USB disconnect. Scheduling reconnect check in ')\tcrit=$(( $(pcnt 'Critical error') - p0 ))\tfatal=$(cnt $S $E 'FATAL EXCEPTION')\tphone_fatal=$(( $(pcnt 'FATAL EXCEPTION') - f0 ))"
  echo -e "$R" | tee -a $OUT/U1-C.tsv
  done=$((done+1)); [ $done -ge 2 ] && break; sleep 10
done
r_close U1-C
```

**PASS, on every completed cycle:** `retry` 1; `ssl_before_close` 1 or more with `t_ssl2` 35000 ms or less (the session formed behind the open screen); `tries_before_ssl` 1 or more (the session is USB); `quiesce` 1 or more; `held` 0 (the re-attach carried the Save and was not held); `arm_in_window` 0 and `pokes_in_window` 0 (wireless never armed under the plugged dongle); `fallback_run` 0; `raise_refused` 1 or more; `settings_restart` 1; `fatal` 0. Phone (D-MOTO, graded): `phone_fatal` 0. Report `detach`, `reattach`, `tries_before_ssl`, `fallback_other`, `usb_retry_line` and `crit`. **What a PASS would look like if the change did nothing:** round 2's shape, `held` 1 or more after the re-attach and, with the screen open past 30 s, `arm_in_window` 1 or more. **FAIL:** `ssl_before_close` 0 with the dongle back on the bus (`reattach` 1 or more), `held` 1 or more, `arm_in_window` 1 or more, or `fallback_run` 1 with a session formed before 30 s. Time: about 7 min.

**Closing Stage U:** as `archive/rounds/pr-1065-reconnect-timers-round1-brief.md` closes it: `send ACTION_EXIT` to the running app, `headunit://exit`, `sleep 3`, force-stop, restore D-POCO's backup with `pocoput` and read it back, `cue "H1: unplug the dongle; put D-POCO back on its PC cable"`, `adb -s $POCO_IP:5555 usb`, then `HU=$DHU; PH=4f4027e9; PUT=hu_put; BASEXML=$OUT/settings-backup-DHU.xml; UNIT=D-HU`. D-POCO is a phone again from here, which is why it gets `headunit://exit` first (`rig-quirks/topics/wifi.md`). **Reinstall stock C on D-POCO only if a later round needs it; E must not stay on D-POCO as its head unit build without a note in Setup notes.**

### Closing step

Restore each unit's round backup of `settings.xml` (D-HU as root; D-POCO with `pocoput`), read each back, and record the diff, which must be empty. `separate-audio-streams` must read as it did at R0. Re-enable D-MOTO's radios if Stage A left them off. Leave D-POCO's head unit server in the state it was found, and say which. Kill every capture and watcher: `ps aux | grep -c "[l]ogcat"` must print 0.

## 8. Do not re-run

- **Headunit Server (HS):** round 2 PASS 3 of 3, reconnect 0.85 to 0.88 s. The new commits do not touch the `restartEndpoint` redial.
- **The GLES cover sequence (N3):** round 2 PASS 2 of 2 on C. The new commits do not touch the decoder or the surface.
- **The baseline Save (N1-B):** round 2 recorded `main`'s shape (a service stop, nothing comes back in 60 s). `main` has not changed that path.
- **D-SAM (D1):** round 2 was INCONCLUSIVE because D-POCO did not answer the pokes. N2 measures the same Native change on D-HU.
- **The exit loop, the user connect over a live session, the starvation cap, `ACTION_RESTART_AUDIO`, the arbiter tiers:** settled in rounds 1 and 2 (round 2 section 8).
- **Stage N and HW (Helper):** UNTESTABLE on this rig (3.4).

## 9. Report back

1. **N2, the point:** per graded cycle, `t_poke`, `t_ssl2`, `fallback_run` and `bye_phone`, plus every `poke unanswered` cycle with its numbers, and the line `the Native Save reconnects inside 15 s: yes or no`.
2. **ND:** `pokes_after_disc`, `stand_down` and `re_arm`.
3. **USB:** U1's `ssl_before_close`, `held`, `arm_in_window` and `t_ssl2` per cycle.

### Results skeleton (copy into `pr-1047-session-reconnect-round3-results.md`)

```markdown
# session-reconnect, round 3 results

**Candidate C:** PR 1047 head 7cc36de8 on main 77914f18, tree <printed vs a13a8e4934ab32bee184338b2d947c44c530d195>   **Export build E:** C plus the SettingsActivity export
**APK md5:** C <md5> / E <md5>
**Units:** D-HU <API>, D-POCO <API, Gearhead>, D-MOTO <API, Gearhead>
**Date:** <yyyy-mm-dd>
**Evidence:** release `rig-evidence-pr-1047-session-reconnect`, asset `pr-1047-session-reconnect-round3-captures.zip`, sha256 <hash>

## Setup notes
Deviations; scripts used and added; every injected tap from `taps.log` (run, target, coordinates) and the tap count per run; every hand step with its time (from `hand-steps.log`); the `open_audio` route and destination id per unit; the rig audio keys and `separate-audio-streams` as found and as restored; void and unanswered cycles and why; any string that did not match; thermal per run.

## R0 Gate
| APK | md5 | `SettingsRestart: route=` count | commit or tree | JVM count |
|---|---|---|---|---|

## A0
| Unit | APK | destination id | route |
|---|---|---|---|

## N2 (the point)
| run | cycle | t_retry | t_poke | t_ssl2 | pokefail_before_ssl2 | poked_before_ssl2 | hold | ssl_count | t_video2 | audio | fallback_run | fallback_skip | settings_restart | link_lost | raise | bye_phone | crit | graded |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
The Native Save reconnects inside 15 s: yes / no.

## ND
| cycle | t_disc | pokes_before_disc | stand_down | pokes_after_disc | skip_line | ssl_after_disc | fallback_run | fallback_skip | bt12 | re_arm | t_rearm | bye_phone | graded |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|

## U0
One line: wireless adb, 5277 listener, A0, first USB session, quiesce.

## U1
| cycle | retry | detach | reattach | held | tries_before_ssl | t_ssl2 | ssl_before_close | quiesce | arm_in_window | pokes_in_window | fallback_run | fallback_other | raise_refused | usb_retry_line | crit |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|

## Verdict table
| Run | C | E | Notes |
|---|---|---|---|

## Anything the brief did not ask about
```

Evidence goes to a release asset (template section 7): zip the round folder, `sha256sum` it, and add it to the existing release `rig-evidence-pr-1047-session-reconnect` with `gh release upload`.

## Decisive strings

Unit lines are from `app/src/main` (and `contract/src` for `settings_restart`) of the C tree `a13a8e49`. `USB_DEVICE_DETACHED` and `USB_DEVICE_ATTACHED` are the intent actions our `USB Intent: ` line prints. `GAL was deliberately disconnected, so do not restart` and `Critical error` are Android Auto lines on the phone, from round 2's phone captures, and `FATAL EXCEPTION` is a system line; none is in our source.

```decisive-strings
AutomationReceiver: 
AutomationMarker: 
CommManager: audio settings changed; reconnecting the projection session
SettingsRestart: route=
retry=
fallback=
fallback=cancel_superseded
AapService: session state 
settings_restart
link_lost
user_exit
AapService: automatic wake is paused by the session policy. Keeping listeners available.
NativeAA: Attempting active poke to device
NativeAA: Calling socket.connect()
NativeAA: Successfully poked 
NativeAA: Poke via 
failed:
alone for now: this unit woke it over its hands-free link
Disconnect action received.
NativeAA: deliberate session end; reopening listeners without an automatic wake.
AapService: userExitedAA is true. Skipping auto-poke.
AapService: WiFi Direct credential refresh requested.
AapService: Received WiFi credentials from manager
WifiLauncher: Initializing WiFi Mode: 
AapService: Native AA session ended; keeping the 
Stop action received. Broadcasting finish request to activities.
SSL handshake complete
Throughput over 
AudioDecoder.start: channel=
AapService: raising the projection by 
AapService: Not raising the projection, the settings screen is open
UsbLauncher: USB auto-connect held while the settings screen is open; 
USB accessory device attached, connecting.
Accessory detach cooldown finished. Checking for re-connection...
USB permission granted for 
Found device already in accessory mode
Switching USB device to accessory mode
Single USB auto-connect: connecting to
USB Intent: 
USB_DEVICE_DETACHED
stopping the wireless stack for the duration of it
AapService: USB disconnect. Scheduling reconnect check in 
MATCH! Starting AapService
AapRead: Magic Garbage detected in header
createGroup SUCCESS
GAL was deliberately disconnected, so do not restart
Critical error
FATAL EXCEPTION
```
