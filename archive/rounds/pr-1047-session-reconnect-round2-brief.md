# session-reconnect, round 2 brief: the audio settings Save, the 30 s wake hold, and the transports round 1 did not reach

This round tests the rebased head of the outside contributor's session-reconnect change. Round 1 ran Stage A only and FAILed on the combo arm. This round re-runs the Save on the rebased code, adds the case our former blocker was about (a Native AA Save where the phone does not come back), and reaches the four transports round 1 left unrun. It needs no other round first.

## 1. Build and baseline

Three APKs. Build each with `build_hur.sh` and copy it out of `apks/` the moment it is built, because the script deletes the previous APK (`rig-quirks/topics/tooling.md`). Name each file by its SHA or tree.

| Arm | Code | Identity | Gate |
|---|---|---|---|
| **B** (baseline) | `main` (3.5.0-beta4) | commit `7102b4283666ffcc7802e49402735e3958cd51a0`, tree `e96aa72a2ff556c9d1a5733c07d69c1b183a1b61` | 2820 JVM tests, 0 failures |
| **C** (candidate) | B with the PR head `b62d86c29d005c7cedce81d65d48fd2649b8e4f6` (22 commits) merged in | tree `96857f5658ea587eebcc4b97791937542d2b3bc7` | 2854 JVM tests, 0 failures |
| **X** (combo, optional) | B with the three PR heads merged in the order below | tree `f1f58d5538eaa50a51665f7ea62a1771b648ef12` | 2905 JVM tests, 0 failures |

```bash
git fetch https://github.com/andreknieriem/open-headunit.git main pull/1047/head pull/1046/head pull/1045/head
git cat-file -e b62d86c29d005c7cedce81d65d48fd2649b8e4f6 || echo "C MISSING"
git checkout -B arm-B 7102b4283666ffcc7802e49402735e3958cd51a0
git rev-parse 'HEAD^{tree}'                        # MUST print e96aa72a2ff556c9d1a5733c07d69c1b183a1b61
git checkout -B arm-C 7102b4283666ffcc7802e49402735e3958cd51a0
git merge --no-edit b62d86c29d005c7cedce81d65d48fd2649b8e4f6    # PR 1047 head
git rev-parse 'HEAD^{tree}'                        # MUST print 96857f5658ea587eebcc4b97791937542d2b3bc7
# X: built locally, so nothing depends on a merge commit someone else made
git checkout -B arm-X 7102b4283666ffcc7802e49402735e3958cd51a0
git merge --no-edit 9b8f83664cc5081f3d023ac699c0b5fe757334c6    # PR 1045 head
git merge --no-edit b749525d8c4b05cfcbb2a631afb727c344731e5d    # PR 1046 head
git merge --no-edit b62d86c29d005c7cedce81d65d48fd2649b8e4f6    # PR 1047 head (C)
git rev-parse 'HEAD^{tree}'                        # MUST print f1f58d5538eaa50a51665f7ea62a1771b648ef12
```

**Both arms are on today's `main`.** The PR's 22 commits sit on `145a0c76`; C is the PR head merged onto B with no conflict, so B and C differ by the PR and nothing else. Nothing on the transfer branch was rewritten since round 1.

**If `C MISSING` prints, C's merge conflicts, or C's tree differs, stop the round and escalate:** the PR head moved. **If X's tree differs, or a merge conflicts, or X fails its unit tests, do not build X.** Every X run is then UNTESTABLE; record what printed and carry on with B and C. The merge commits stay local: do not push them. **B may be reused:** the main-smoke round 1 built `7102b428` as md5 `62ccbb85dfed879a1151dea9610df817` with 2820 tests. If that APK is still on disk with that md5, reuse it and say so in Setup notes.

### 1a. Identity gate, per arm, before any run on that arm

```bash
adb -s $HU shell pm path $PKG                 # pull that apk to ./installed.apk, then:
unzip -p installed.apk 'classes*.dex' | strings | grep -cF '<symbol>'
```

| Symbol | B | C | X |
|---|---|---|---|
| `SettingsRestartRecovery` (this PR) | 0 | 1 or more | 1 or more |
| `AapMessageReassembler` (PR 1045) | 0 | 0 | 1 or more |
| `outputPublicationLock` (PR 1046) | 0 | 0 | 1 or more |

The source trees agree: `SettingsRestartRecovery` is in 0 files on B and 4 on C and X; `AapMessageReassembler` is in 0 on B and C and 4 on X; `outputPublicationLock` is in 0 on B and C and 1 on X. Also send `ACTION_QUERY_STATE` and record `commit`: B's must start `7102b428`; C's and X's are local merge commits (record them). Record every APK md5 from a real `adb pull` plus a local `md5sum`. The three must differ.

## 2. What this is and why it exists

On `main`, saving an audio setting with a session live stops the whole service. The audio streams screen and the main settings list both send `ACTION_STOP_SERVICE`, so the session ends as a user exit and nothing comes back until the user acts again. The PR replaces that. `CommManager.applyAudioSettings()` ends only the projection session when an `AudioSessionConfig` field changed (sink, static focus, focus mode, separate streams, the three stream ids, AAC, the DSP equalizer). It logs `CommManager: audio settings changed; reconnecting the projection session` and publishes `Disconnected(reason = SETTINGS_RESTART)`. `AapService` then retries the same route once, by transport, inside a 30 s window (`SettingsRestartRecovery.WINDOW_MS`):

- an outgoing IP session (Headunit Server, legacy-free Self Mode) redials its `restartEndpoint`;
- USB calls `UsbLauncherManager.restartForSettings()`;
- Nearby calls `NearbyManager.restartForSettings()`, which prefers the same phone until the window ends;
- Native AA re-arms with `rearmAfterSessionEnd(wakePhone = true)`, and `NativeAaHandshakeManager.wakesPhone()` refuses every automatic poke while `holdsSettingsWake` is true;
- everything else restarts discovery.

At the end of the window, if no session has formed, `resumeAutomatic` hands control back to the ordinary rules. For Native AA with the launcher still active that is `WifiLauncherNative.refreshAfterWake()`: it re-reads the group, and the credential delivery runs `triggerPoke()`.

Round 1 (`pr-1047-session-reconnect-round1-results.md` and its addendum) measured the older head `ac741d29` on Stage A only. The Save ended the session as a lost link 12 to 41 ms after the apply and a new SSL formed 2.0 to 6.6 s later, with video. The exit loop and the user-connect serialisation were at least as good as `main`. The combo arm lost video after GLES cover cycles followed by the Save, 3 times out of 3, while K alone passed the same sequence. The addendum put the loss on PR 1046, not this PR.

Our review of round 2 (`reply-1047-r2`) asked for a reason of its own, which the author added, and then found one blocker: the wake gate held until a new connect attempt, so **a Native AA phone that does not come back after the Save was never woken again**. Round 1 never exercised that case, because every reconnect there ran with the phone coming back by itself. The author bounded the gate to the 30 s window. Our review of the current head (`pr-1047-review-r3`) found the asks met and left these for the rig:

- **Point of the round:** the Native AA wake comes back at Save + 30 s when the phone does not return by itself.
- **Finding 1 (should fix):** in Helper WiFi Direct, the 30 s fallback runs `restartDiscovery()`, which reaches `checkGroupAndCreate()` and recreates the group, possibly under a phone that is still joining it. A smaller form exists on Nearby.
- **Finding 2 (should fix):** on USB, the Save's own retry is held while the settings screen is open (`HOLD_FOR_SETTINGS`). If the user stays on the screen past 30 s, the fallback arms wireless before the held USB check runs.
- **Finding 3 (note, changed in `afb121d3`):** `ACTION_DISCONNECT` during the window now revokes the Save, so the 30 s fallback does not fire. It still does not set `userExitedAA`, and in Native mode the revoke lifts the wake hold at once, so the next credential delivery can poke the phone within seconds of the disconnect. This round does not build a run for it.
- **Fixed in `b62d86c2`, was a should-fix, not reached by this round:** `SettingsFragment.saveSettings` sent `ACTION_STOP_WIRELESS` before `applyAudioSettings()` when wireless is switched off or set to Manual in the same Save, and that action now revokes the pending Save. The session ends and nothing reconnects. The audio streams screen this round saves from sends no wireless intent.
- **Finding 6 (note):** `SettingsRestartRecovery` logs nothing when it fires. So this brief grades the fallback from the lines its routes print (`AapService: WiFi Direct credential refresh requested.`, `WifiLauncher: Initializing WiFi Mode:`, `NetworkDiscovery: Starting scan...`, `NearbyManager: Starting Nearby (Discoverer only)...`).
- **New on `dbf1e3a1` (changes U1 and U2):** the Save's own USB retry now carries the Save and is no longer held by the open settings screen. It can form a USB session behind the screen; the projection raise is still refused there (`AapService: Not raising the projection, the settings screen is open`). A re-attach that arrives through the ordinary USB attach broadcast carries no Save and is still held. On this rig the dongle drops off and comes back in normal mode at every session end (3.5), so either path can run. U1 and U2 therefore report which path ran and grade each path on its own terms.
- **Also new:** `31c71b6b` (a first session that ends before its live states are seen still ends cleanly) and `27328bb7` (a Save on a Self Mode session restores the Self route and its VPN). Neither changes a run here.
- **Leftover:** while the hold is on, `triggerPoke()` still prints `NativeAA: the phone ended the last session itself`, which is not what happened. Count it; it is not a FAIL.

## 3. What is different about this round

### 3.1 Why round 1 left four stages unrun, and the plan for each

Round 1's results say why, run by run: the Headunit Server runs needed the server toggle on a phone (a hand step), the Nearby run needed the Wireless Helper app (not installed), the USB runs needed the dongle and an operator at the rig, and the tablet runs needed D-SAM and D-HP plugged in. The operator also had a time limit, which cut the cycle counts of Stage A. This round plans around each:

| Round 1 gap | This round |
|---|---|
| No operator for long | **Every Save in this round is a hand step** (3.3), so the operator is present for the whole round. Cycle counts are small, and the stages run in priority order (3.2). If the operator leaves, stop at that stage and mark the rest INCONCLUSIVE "operator not present". |
| Server toggle | `srv_restart` from `projection-teardown-and-relays` round 3 (H3 below), with the operator present. |
| No Wireless Helper | **Not fixable on this rig, so Stage N is not run.** Android Auto `17.9.664004-release` on D-POCO and D-MOTO refuses the helper's launch (`Activity launch failed (Permission Denial: ... WirelessStartupActivity ... not exported)`), and its broadcast fallbacks do not start Android Auto either. `pr-1064-nearby-attempts` round 1 formed 0 of 3 Nearby sessions on `main` that way, and the helper run in `pr-1065-reconnect-timers` round 1 formed none on either arm. |
| No USB | D-POCO as head unit and USB host on wireless adb, the dongle on its OTG port, D-MOTO on a cable as the dongle's phone, as `pr-1065-reconnect-timers-round1-brief.md` Stage U does. |
| Tablets | Stage D, last. One Save each. |

### 3.2 Stages, in this order

| Stage | Plugged into the test PC | Runs | Arms |
|---|---|---|---|
| **A** | D-HU (head unit, Native AA), D-POCO (phone), D-MOTO (radios off for the stage) | R0, A0, N1-B, **N1-C (the point)**, N2, N3 | B, C, X |
| **H** | D-HU on the house network in Headunit Server mode, D-POCO with its head unit server on | HS | C |
| **N** | not run this round (3.1, 3.7) | NB0, NB1, NB2, HW | none |
| **U** | D-POCO as head unit on wireless adb with the dongle on OTG, D-MOTO on a cable | U0, U1, U2 | C |
| **D** | D-SAM (Native AA, D-POCO as phone), D-HP (Headunit Server, D-POCO's server), D-POCO | D1, D2 | C |

Install C on D-HU after Stage A's X runs, so Stages H and N run on C.

### 3.3 The Save is a hand step, and why no verb can make it

The Save is the only trigger for the code under test, and no automation verb reaches it. On C, `applyAudioSettings()` has exactly two callers: `AudioStreamSettingsFragment.saveSettings` and `SettingsFragment.saveSettings`. Round 1 reached it through `restartAudio()`, but commit `63c53ba3` made `restartAudio()` rebuild tracks only, so `ACTION_RESTART_AUDIO` and the root broadcast no longer end a session. `ACTION_SET_SETTINGS` writes the preferences and calls neither function. The Save button is enabled only when the screen itself holds a change, so an import followed by a press does not work either.

So the brief makes the Save as follows, with every part scripted except two taps:

1. **The script opens the audio streams screen directly.** `audioStreamSettingsFragment` is a destination in `nav_graph.xml`, and `SettingsActivity` navigates to any destination id passed as `extra_destination`. `dest_id` reads that id from the installed APK with `aapt2`, per arm, so a resource id that moved between builds cannot send the screen elsewhere. The first row on that screen is the "Separate audio streams" toggle and the Save button is in the toolbar, so nothing needs a scroll.
2. **The operator flips "Separate audio streams" once and presses Save** (hand step H-SAVE). `separate-audio-streams` is an `AudioSessionConfig` field, so on C every Save ends the session. It is not one of the rig's three deliberate audio worst-case keys, and the closing step restores it.
3. **The script waits for the Save line, not for the operator.** Every timing in this round is measured from `CommManager: audio settings changed; reconnecting the projection session` on C, or from `Stop action received. Broadcasting finish request to activities.` on B. `disconnect()` sets `settingsRestartUntilMs` inside the same lock right after the Save line prints, so the 30 s window starts at that line within a few milliseconds.
4. **The script closes the settings screen with BACK** (`input keyevent 4`, a scripted keyevent) unless the run says to keep it open.

The cue is a line on the executor's console that starts `OPERATOR:`, a terminal bell, and a timestamped `OPERATOR_STEP` line in `$OUT/hand-steps.log`. **A missed cue:** no Save line within 120 s repeats the cue once. A second miss voids that cycle and stops the stage with "operator not present" in Setup notes. A flip and a Save take two taps, so the operator's speed changes nothing that is graded.

### 3.4 Hand steps (the only ones in the round)

| Id | Step | Reason no verb exists |
|---|---|---|
| H-SAVE | On the screen the script opened, flip "Separate audio streams" once, then press Save at the top right | The Save is the code under test and has no verb (3.3). |
| H-SAVE-HP | On D-HP only: open Settings from the home screen, open the audio streams screen, flip the toggle, press Save | D-HP's settings screen has no scripted route; four were tried (`rig-quirks/units/D-HP.md`). Scrolling by hand is allowed; scripted scrolling is not. |
| H1 | Move D-POCO between its PC cable and wireless adb; plug and unplug the dongle when cued | A cable is hardware. |
| H2 | Allow the system USB permission dialog on D-POCO with "always", only if one appears | A system dialog, not our app. Record `dumpsys usb` before and after. |
| H3 | Start Android Auto's head unit server in its developer settings on D-POCO (inside `srv_restart`) | No adb command starts it. |
| H4 | Unlock D-MOTO once if it shows a PIN | adb has no way past a PIN (`rig-quirks/units/D-MOTO.md`). |
| H5 | Stage N only, which is not run this round: pick the helper's Connection Mode by hand if `exec-in` cannot write it | The helper has no settings intent. |

There is no tap on our app anywhere else in this brief.

### 3.5 Rig facts that change the runs

- **The phone that does not come back is made with its Bluetooth.** Native AA cannot start a session with the phone's Bluetooth off, because the handshake needs RFCOMM (`rig-quirks/topics/bt.md`). D-HU re-addresses its group on every create, so it advertises no WPP-over-TCP endpoint that would let the phone return over WiFi alone. A live session does not need the phone's Bluetooth: on this rig the phone drops its ACL 0 to 5 s after SSL in most sessions anyway. So N1 switches D-POCO's Bluetooth off before the Save. If that ends the session by itself, N1 switches to the other order (Save first, then Bluetooth off from a watcher on the Save line). A poke at a phone whose Bluetooth is off blocks about 15.4 s in `socket.connect()` and then fails; that is expected and is not graded.
- **D-POCO's Bluetooth can self-revert** (`rig-quirks/topics/bt.md`). N1 reads it at Save + 25 s and Save + 50 s with `dumpsys`; a cycle where it reads on is void.
- **A persistent WiFi Direct group lets another paired phone rejoin with no Bluetooth** (`rig-quirks/units/D-HU.md`). D-MOTO is cabled in Stage A only so that its Bluetooth and WiFi can be switched off by script.
- **`native-aa-wake-damage-verdict` latches and stands every later poke down** (`rig-quirks/topics/bt.md`). Stage A writes it to `0`.
- **D-POCO (API 35) refuses `ACTION_START_WIRELESS_SCAN` right after a force-stop.** Start our app there with `am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity`.
- **`SettingsActivity` is not exported.** D-HU's adb shell is root, so `am start` works there. On D-POCO and D-SAM, `open_audio` falls back to `run-as $PKG am start --user 0 -n ...`, which launches from the app's own uid (`rig-quirks/units/D-SAM.md`). D-HP has no route (H-SAVE-HP).
- **The settings screen pauses the wireless stack only when it opens with no session live** (`SettingsScreenPausePolicy.pauses`). Every Save here opens the screen over a live session, so nothing pauses. Do not reopen the settings screen inside a window: that would pause the stack and change the run.
- **The phone logs `Critical error` lines at every session end we cause, on every build.** Gearhead prints 1 to 5 (`reason:1`, `io error`, `Failed to read message`) when the head unit ends a session, on `main` too, over USB and sockets (`pr-1065-reconnect-timers` and `pr-1066-byebye-priority` round 1, 2026-10-06). Every cycle here ends a session with the Save, so `crit` is reported and never graded. A `Critical error` line that names a protocol refusal (for example `Multiple media configs received`) is a FAIL; quote it.
- **`listening` proves a socket, not a working server.** A wedged Android Auto head unit server passes it and then fails the handshake with `Handshake: the peer accepted the connection and then sent nothing at all` (`pr-1063-restart-audio` round 1). So HS and D2 give a failed first session one `srv_restart` before they call the server down.
- **The dongle drops off USB at every session end and comes back by itself** (`pr-1065-reconnect-timers` round 1). It detaches about 1.6 s after the session ends, re-attaches as the normal-mode device (`PID 4EE1`) about 4.5 s later, and then asks for USB permission. So a USB Save makes a detach and an attach with no operator action. U1 waits for that re-attach before it cues the unplug. In U0, tick "Always" on the first permission dialog (H2), or every later cycle waits on it. The first USB attempt after a launch can fail on stale data (`Drained ... bytes of stale USB data`, then `SSLException: Unable to parse TLS packet header`) and retry; that is not a failure.
- **Every watcher reads the capture file.** A `logcat | grep` pipe never fired on this rig (`pr-1047` round 1).
- **D-POCO stays bonded to D-HU, and that can stop D-SAM waking it** (`rig-quirks/units/D-POCO.md`). Do not unpair anything: it would change the rig for later rounds. If D1's bring-up fails that way, D1 is INCONCLUSIVE.
- **The rig audio settings are a deliberate worst case** (`use-aac-audio`, `audio-latency-multiplier`, `audio-queue-capacity`). Do not change them. Read them back and record them. Delete `video-profile-starvation-cap` before every run.
- **D-HU's `shared_prefs/` can be root-owned.** `stat` it at R0 and record what it read; `chown` it to the app's uid if it is `root:root`. Round 1 read `u0_a176:771`.

### 3.6 What the rig cannot do

- **Legacy Self Mode needs Android Auto older than 17.4 on a phone, and no rig phone is known to carry one.** D-POCO read `17.9.664004-release` in `projection-teardown-and-relays` round 3. R0 reads both phones' versions. Unless one reads below 17.4, the legacy Self Mode Save is **UNTESTABLE**, and no run is written for it. The code (`86ce01f9`) stays covered by its JVM tests and the review.
- **A phone that is both on USB and paired over Bluetooth to the same head unit does not exist here.** The USB device is the dongle, and D-MOTO pairs with the dongle, not with D-POCO. So U2 measures whether wireless arms and pokes before the held USB check, and names the poke's target. It cannot show the plugged phone being pulled onto wireless.
- **The Wireless Helper cannot start Android Auto 17.9 on either rig phone** (3.1). So review finding 1 (the Helper WiFi Direct group recreated at 30 s) and its Nearby form stay review findings, measured by no run.
- **D-HP's settings screen cannot be opened by script.** D2 uses H-SAVE-HP.

### 3.7 Pre-registered INCONCLUSIVE and UNTESTABLE outcomes

So that none is read as a failure: N1-C with fewer than 2 graded cycles in 6 attempts; N3-X when X is not built; the audio leg of N2 when the first session prints no `AudioDecoder.start: channel=` line (nothing is playing); Stage H when `srv_restart` times out; Stage N (NB0, NB1, NB2, HW): UNTESTABLE and not run, unless R0 reads a Gearhead other than `17.9.664004-release` on D-POCO or D-MOTO, in which case ask the operator before running NB0 as written; Stage U when U0 fails; D1 when D-SAM cannot wake D-POCO; D2 when the server cannot be brought up; legacy Self Mode as above.

## 4. Settings keys

Write with the app stopped and read back before every launch. D-HU: `hu_put` (root). D-POCO as head unit: `pocoput`. D-SAM and D-HP: `tab_put`. All three take `ohu_setkeys.py` specs. Take one backup per unit at the start of the round and write every run from it.

| Key | Type | A (D-HU) | H (D-HU) | N (D-HU) | U1 (D-POCO) | U2 (D-POCO) | D1 (D-SAM) | D2 (D-HP) |
|---|---|---|---|---|---|---|---|---|
| `wifi-connection-mode` | int | `3` | `1` | `2` | `1` | `3` | `3` | `1` |
| `helper-connection-strategy` | int | | | `2` (NB), `1` (HW) | | | | |
| `log-level` | int | `2` | `2` | `2` | `2` | `2` | `2` | `2` |
| `onboarding-version` | int | `2` | `2` | `2` | `2` | `2` | `2` | `2` |
| `kill-on-disconnect` | boolean | `false` | `false` | `false` | `false` | `false` | `false` | `false` |
| `native-aa-wake-damage-verdict` | int | `0` | | | | `0` | `0` | |
| `auto-connect-last-session` | boolean | | | `false` | `true` | `true` | | |
| `auto-start-on-usb`, `reopen-on-reconnection` | boolean | | | | `true` | `true` | | |
| `use-libusb` | boolean | | | | `false` | `false` | | |
| `connection-modes` | string set | | `wifi` | `wifi` | `usb,wifi` | `usb,wifi` | | `wifi` |
| `screen-orientation` | int | | | | | | `2` | |
| `view-mode`, `video-codec` | int, string | N3 only: `2`, `H.265` | | | | | | |
| `video-profile-starvation-cap`, `native-aa-wireless`, `wifi-launcher-mode` | | delete | delete | delete | delete | delete | delete | delete |

Read and record, do not write: the three rig audio keys, `separate-audio-streams`, `native-poke-bt-macs`, `native-poke-all-paired`, `native-driver-selection-mode`. `ACTION_LOG_MARKER` is not in `AutomationCommandPolicy.CONFIGURING` on either B or C, so `allow-external-configuration` is not needed.

```bash
AKEYS="int:wifi-connection-mode=3 int:log-level=2 int:onboarding-version=2 bool:kill-on-disconnect=false int:native-aa-wake-damage-verdict=0 del:video-profile-starvation-cap del:native-aa-wireless del:wifi-launcher-mode"
A3KEYS="$AKEYS int:view-mode=2 str:video-codec=H.265"
HKEYS="int:wifi-connection-mode=1 int:log-level=2 int:onboarding-version=2 bool:kill-on-disconnect=false set:connection-modes=wifi del:video-profile-starvation-cap del:native-aa-wireless del:wifi-launcher-mode"
NKEYS="int:wifi-connection-mode=2 int:helper-connection-strategy=2 bool:auto-connect-last-session=false int:log-level=2 int:onboarding-version=2 bool:kill-on-disconnect=false set:connection-modes=wifi del:video-profile-starvation-cap del:native-aa-wireless del:wifi-launcher-mode"
WKEYS="int:wifi-connection-mode=2 int:helper-connection-strategy=1 bool:auto-connect-last-session=false int:log-level=2 int:onboarding-version=2 bool:kill-on-disconnect=false set:connection-modes=wifi del:video-profile-starvation-cap del:native-aa-wireless del:wifi-launcher-mode"
U1KEYS="int:wifi-connection-mode=1 int:log-level=2 int:onboarding-version=2 bool:kill-on-disconnect=false bool:auto-connect-last-session=true bool:auto-start-on-usb=true bool:reopen-on-reconnection=true bool:use-libusb=false set:connection-modes=usb,wifi del:video-profile-starvation-cap del:native-aa-wireless del:wifi-launcher-mode"
U2KEYS="${U1KEYS/wifi-connection-mode=1/wifi-connection-mode=3} int:native-aa-wake-damage-verdict=0"
D1KEYS="int:wifi-connection-mode=3 int:log-level=2 int:onboarding-version=2 bool:kill-on-disconnect=false int:screen-orientation=2 int:native-aa-wake-damage-verdict=0 del:video-profile-starvation-cap del:native-aa-wireless del:wifi-launcher-mode"
D2KEYS="int:wifi-connection-mode=1 int:log-level=2 int:onboarding-version=2 bool:kill-on-disconnect=false set:connection-modes=wifi del:video-profile-starvation-cap del:native-aa-wireless del:wifi-launcher-mode"
```

## 5. The lines that decide the runs

Each unit line was checked with `grep -F` against `app/src/main` (and `contract/src` for the reason) of B (`7102b428`), the merged C tree (`96857f56`) and the X tree. Every one prints at INFO, so `log-level=2` carries all of them. `AapService: session state <state> (<reason>)` is composed at run time; the reasons are constants in `HeadUnitIntent.kt`.

| Line (fixed substring) | File | Meaning | B | C, X |
|---|---|---|---|---|
| `CommManager: audio settings changed; reconnecting the projection session` | unit | **the Save on C** (anchor) | no | yes |
| `Stop action received. Broadcasting finish request to activities.` | unit | **the Save on B** (anchor); on C a service stop | yes | yes |
| `MainActivity: Received finish request. Closing.` | unit | B's Save closed the home screen | yes | yes |
| `AapService destroying` | unit | the service stopped | yes | yes |
| `AapService: session state ` then `disconnected (settings_restart)` / `(link_lost)` / `(user_exit)` | unit | what the session end was called | `settings_restart` no | yes |
| `AapService: automatic wake is paused by the session policy. Keeping listeners available.` | unit | **the wake hold refused a credential-driven poke** | no | yes |
| `NativeAA: the phone ended the last session itself` | unit | `triggerPoke()` refused (the review's leftover wording on C) | yes | yes |
| `AapService: WiFi Direct credential refresh requested.` | unit | a group re-read (the fallback's Native route) | yes | yes |
| `WifiDirectManager: SUCCESS - Providing credentials to listener.` | unit | credentials delivered | yes | yes |
| `AapService: Received WiFi credentials from manager` | unit | the launcher received them and decides the poke | yes | yes |
| `WifiLauncher: Initializing WiFi Mode: ` then `NATIVE` / `AUTO` / `HELPER` | unit | a wireless bring-up started | yes | yes |
| `NativeAA: Attempting active poke to device` | unit | **a wake poke started** | yes | yes |
| `NativeAA: Calling socket.connect()` | unit | the poke really dialled | yes | yes |
| `AapService: Native AA session ended; keeping the ` | unit | the ordinary Native session-end path (must not run on a C Save) | yes | yes |
| `AapService: Disconnected. Restarting discovery loop in 2s...` | unit | the ordinary discovery restart | yes | yes |
| `AapService: USB disconnect. Scheduling reconnect check in ` | unit | the ordinary USB retry | yes | yes |
| `AapService: userExitedAA is true. Skipping auto-poke.` | unit | a user-exit stand-down | yes | yes |
| `AapService: raising the projection by `, `AapService: Not raising the projection, the settings screen is open` | unit | the raise after a reconnect | yes | yes |
| `AapProjectionActivity: onResume` | unit | the projection is in front | yes | yes |
| `SSL handshake complete` | unit | a session formed (INFO form; never prefix `Handshake:`) | yes | yes |
| `Throughput over ` with `rendered=` | unit | video reached the decoder | yes | yes |
| `Media Sink Setup Request: ` | unit | the phone set up a sink (`7 on channel VIDEO` is H.265) | yes | yes |
| `AudioDecoder.start: channel=` | unit | **an audio sink started** | yes | yes |
| `sessions in a row ended without a single video` | unit | the starvation advice | yes | yes |
| `UsbLauncher: USB auto-connect held while the settings screen is open; ` | unit | **a USB check with no Save was held by the screen** (the attach-broadcast path; the Save's own retry no longer prints it) | yes | yes |
| `AapService: the settings screen closed with a USB auto-connect held ` | unit | **the held USB check ran at the close** | yes | yes |
| `USB Intent: ` with `USB_DEVICE_DETACHED` | unit | the dongle was unplugged | yes | yes |
| `Found device already in accessory mode`, `Switching USB device to accessory mode` | unit | a USB attempt began | yes | yes |
| `stopping the wireless stack for the duration of it` | unit | the wired-session quiesce at a USB SSL | yes | yes |
| `NetworkDiscovery: Starting scan...` | unit | discovery ran | yes | yes |
| `AapService: ACTION_END_SESSION_STAY_ARMED received` | unit | an ordinary session end landed | yes | yes |
| `NearbyManager: Stopping discovery and disconnecting from any active endpoint...` | unit | Nearby stopped | yes | yes |
| `NearbyManager: Starting Nearby (Discoverer only)...` | unit | Nearby discovery started | yes | yes |
| `NearbyManager: Endpoint FOUND: ` | unit | `<model> (<id>)` | yes | yes |
| `NearbyManager: Auto-connect check: Enabled=` | unit | **the ordinary auto-connect rule ran** (C skips it while the preference holds) | yes | yes |
| `NearbyManager: Requesting connection to endpoint: ` | unit | an attempt began | yes | yes |
| `AapService: Connecting to Nearby endpoint ` | unit | the connect verb reached the service | yes | yes |
| `createGroup SUCCESS` | unit | a P2P group was created | yes | yes |
| `New surface set:`, `Decoder stopped:`, `First frame rendered` | unit | surface and decoder lifecycle (N3) | yes | yes |
| `AapVideo: Dropped Flag 11 packet` | unit | round 1's lost-video shape | yes | yes |
| `MATCH! Starting AapService`, `AapRead: Magic Garbage detected in header` | unit | discard rules | yes | yes |
| `AutomationReceiver: `, `AutomationMarker: ` | unit | verb proof and markers | yes | yes |
| `FATAL EXCEPTION` | unit, phone | a crash (system line) | | |
| `Critical error` | phone | Android Auto ended a session with a critical error (Gearhead's `CAR.SERVICE` line) | | |
| `Network server running on port` | phone | Gearhead's head unit server started (the H3 proof) | | |
| `NearbyStrategy: Advertising as`, `NearbyStrategy: Connected to`, `AA is now flowing through proxy` | phone | the helper advertises, a head unit connected to it, Android Auto carries the session (helper at `8ac36c9`, checked in `pr-1064-nearby-attempts-round1-brief.md`) | | |

## 6. Shell setup and every action

Make the folder `hur-wifi-test-scripts/pr-1047-session-reconnect-round2/`. Copy `ohu_lib.sh`, `ohu_setkeys.py` and `ptr_lib.sh` into it from `hur-wifi-test-scripts/projection-teardown-and-relays-round3/`. Save `lib1047.sh` below beside them. Source them in that order. From the first two this brief uses `PKG`, `send`, `mark`, `nl`, `waitfor`, `hu_put`, `tab_put` (`ohu_lib.sh`) and `apk_check`, `th_report`, `cap_start`, `cap_stop`, `pcap_start`, `pcap_stop`, `clockcheck`, `top`, `back`, `fastwait`, `listening`, `srv_restart` (`ptr_lib.sh`). The thermal rules in `rig-quirks/topics/tooling.md` apply: `apk_check` calls `th_gate` before every run. Run each stage as one script in the background under `flock /tmp/ohu-rig.lock`, watch `$OUT/hand-steps.log`, and relay each new `OPERATOR_STEP` line to the operator at once. If a helper does not match the real line format, fix it, say so in Setup notes, and keep going. List every file in Setup notes.

```bash
HU=27870808938846; PH=4f4027e9; MOTO=ZY22GC3BM4; SAM=30041c35642d2200; HPS=CNU350BGBJ; DHU=$HU
OUT=~/hur-wifi-test-scripts/pr-1047-session-reconnect-round2; mkdir -p $OUT; cd $OUT
PUT=hu_put; BASEXML=$OUT/settings-backup-DHU.xml; UNIT=D-HU
source ./ohu_lib.sh; source ./ptr_lib.sh; source ./lib1047.sh
ARM=B; WANT_MD5=<B md5>        # set ARM and WANT_MD5 again after every install
```

**`lib1047.sh`**:

```bash
# lib1047.sh : source after ohu_lib.sh and ptr_lib.sh. Needs HU, PH, MOTO, OUT, CAP, PCAP, ARM, UNIT, PUT, BASEXML.
SA=$PKG/com.andrerinas.openheadunit.main.SettingsActivity
MAIN=$PKG/com.andrerinas.openheadunit.main.MainActivity
SAVE_C='CommManager: audio settings changed; reconnecting the projection session'
SAVE_B='Stop action received. Broadcasting finish request to activities.'
REND='Throughput over [0-9]+ms: rendered=[1-9]'
# ms <HH:MM:SS.mmm> : ms since midnight; NA stays NA
ms() { [ "$1" = NA ] && { echo NA; return; }; echo "$1" | awk -F'[:.]' '{print (($1*60+$2)*60+$3)*1000+$4}'; }
# lno <from-line> <fixed> : absolute line number of the first match at or after <from-line>; 0 if none
lno() { local n; [ "$1" -gt 0 ] || { echo 0; return; }; n=$(tail -n +"$1" "$CAP" | grep -anF -m1 -- "$2" | cut -d: -f1); [ -n "$n" ] && echo $(( $1 + n - 1 )) || echo 0; }
tat() { if [ "$1" -gt 0 ]; then sed -n "$1p" "$CAP" | awk '{print $2}'; else echo NA; fi; }
# dt <line-a> <line-b> : ms from line a to line b; NA if either is 0
dt() { local a b; a=$(ms "$(tat $1)"); b=$(ms "$(tat $2)"); { [ "$a" = NA ] || [ "$b" = NA ]; } && { echo NA; return; }; echo $((b-a)); }
# cnt <from> <to|$> <fixed> : matches in capture lines from..to
cnt() { sed -n "$1,$2p" "$CAP" | grep -acF -- "$3"; }
pcnt() { grep -acF -- "$1" "$PCAP"; }
cue() { printf '\a'; echo "OPERATOR: $1 NOW"; echo "$(date +%T) OPERATOR_STEP $1" >> "$OUT/hand-steps.log"; }
# cntw <a_ms> <b_ms> <fixed> : lines from SAVE_LN on whose time minus T0 is within [a, b]
cntw() { tail -n +"$SAVE_LN" "$CAP" | awk -v t0="$T0" -v a="$1" -v b="$2" -v s="$3" 'function m(x,p){split(x,p,/[:.]/);return ((p[1]*60+p[2])*60+p[3])*1000+p[4]} index($0,s){d=m($2)-t0; if(d>=a&&d<=b)n++} END{print n+0}'; }
# firstw <fixed> : ms after T0 of the first line from SAVE_LN on that contains <fixed>; NA if none
firstw() { tail -n +"$SAVE_LN" "$CAP" | awk -v t0="$T0" -v s="$1" 'function m(x,p){split(x,p,/[:.]/);return ((p[1]*60+p[2])*60+p[3])*1000+p[4]} index($0,s){print m($2)-t0; f=1; exit} END{if(!f)print "NA"}'; }
# hold_until <ms> : sleep until <ms> after the host saw the Save line
hold_until() { local d; d=$(( H0 + $1 - $(date +%s%3N) )); [ $d -gt 0 ] && sleep "$(awk -v d=$d 'BEGIN{print d/1000}')"; return 0; }
# dest_id : the audio streams screen's destination id, read from the APK apk_check just pulled
dest_id() { local a; a=$(ls -1 ${ANDROID_HOME:-$HOME/Android/Sdk}/build-tools/*/aapt2 2>/dev/null | tail -n 1)
  DEST=$(printf '%d' "$("$a" dump resources "$OUT/live.apk" | grep -aF 'id/audioStreamSettingsFragment' | grep -aoE '0x[0-9a-f]{8}' | head -1)" 2>/dev/null)
  echo "$(date +%T) $UNIT $ARM audioStreamSettingsFragment=$DEST" | tee -a "$OUT/dest.log"; [ "${DEST:-0}" -gt 0 ]; }
on_audio() { [ "$(adb -s "$HU" shell dumpsys activity top | grep -acF AudioStreamSettingsFragment)" -gt 0 ]; }
# open_audio : the audio streams screen, by am start, else by run-as (non-root units)
open_audio() { adb -s "$HU" shell am start -n $SA --ei extra_destination $DEST >/dev/null 2>&1; sleep 3; on_audio && { OPEN_ROUTE=am; return 0; }
  adb -s "$HU" shell run-as $PKG am start --user 0 -n $SA --ei extra_destination $DEST >/dev/null 2>&1; sleep 3; on_audio && { OPEN_ROUTE=run-as; return 0; }
  OPEN_ROUTE=none; return 1; }
# close_settings : BACK only while a settings screen is in front, at most 3 times
close_settings() { local i; for i in 1 2 3; do top | grep -q SettingsActivity || break; adb -s "$HU" shell input keyevent 4; sleep 1.5; done
  CLOSE_LN=$(nl); mark "$RUN-closed"; echo "$(date +%T) $RUN after-close $(top | head -1 | tr -d '\r')" >> "$OUT/top.log"; }
# hand_save <label> [hand-only] : open the screen, cue the operator, wait for the Save line. Sets SAVE_LN, T0, H0.
hand_save() { local L k S; S=$SAVE_C; [ "$ARM" = B ] && S=$SAVE_B; L=$(nl)
  if [ "$2" != hand-only ]; then open_audio || { echo -e "$1\tNO_AUDIO_SCREEN" | tee -a "$OUT/$RUN.tsv"; return 2; }; fi
  mark "$1-save-cue"
  for k in 1 2; do
    if [ "$2" = hand-only ]; then cue "H-SAVE-HP $1: on $UNIT open Settings, open the audio streams screen, flip 'Separate audio streams' once, press Save"
    else cue "H-SAVE $1: on $UNIT flip 'Separate audio streams' once, then press Save at the top right"; fi
    if fastwait $([ "$2" = hand-only ] && echo 300 || echo 120) "$S" "$L"; then
      SAVE_LN=$(lno "$L" "$S"); T0=$(ms "$(tat $SAVE_LN)"); H0=$(date +%s%3N); mark "$1-saved"; return 0; fi
  done; echo -e "$1\tOPERATOR_MISSED" | tee -a "$OUT/$RUN.tsv"; return 1; }
# fire_on <from-line> <ERE> <cmd...> : background; polls the capture every 0.05 s, runs <cmd> once on the first match (90 s max)
fire_on() { local from=$1 re=$2; shift 2
  ( end=$((SECONDS+90)); while [ $SECONDS -lt $end ]; do
      if tail -n +"$from" "$CAP" | grep -aqE -- "$re"; then echo "$(date +%s%3N) fire [$re]" >> "$OUT/fire.log"; "$@" >/dev/null 2>&1; exit 0; fi
      sleep 0.05; done; echo "$(date +%s%3N) nofire [$re]" >> "$OUT/fire.log" ) & FIREPID=$!; }
phone_bt() { adb -s "$1" shell dumpsys bluetooth_manager | grep -a -m2 -iE '^ *(enabled|state):' | tr -d '\r' | tr -s ' \n' ' '; }
bt_off() { phone_bt "$1" | grep -qiE 'enabled: false|state: OFF'; }
# up_session <secs> <from-line> : SSL, the projection in front, and a rendered window
up_session() { waitfor "$1" 'SSL handshake complete' "$2" || return 1
  waitfor 40 'AapProjectionActivity: onResume' "$2" || send ACTION_RAISE_PROJECTION >/dev/null
  waitfor 60 "$REND" "$2"; }
# r_open <RUN> <spec...> : capture both sides, write settings, mark the start. The caller launches.
r_open() { RUN=$1; shift; CAP=$OUT/$RUN.logcat; apk_check || return 1; dest_id || echo "DEST_UNKNOWN $RUN" | tee -a "$OUT/$RUN.tsv"
  clockcheck "$RUN-start"; adb -s "$HU" shell am force-stop $PKG; pcap_start "$RUN"; cap_start; sleep 1
  $PUT "$BASEXML" "$@" || return 1; mark "$RUN-start"; }
r_close() { mark "$1-end"; sleep 1; send ACTION_EXIT >/dev/null; sleep 3; adb -s "$HU" shell am force-stop $PKG
  pcap_stop; cap_stop; th_report "$1"; }
# second phone (D-MOTO) capture and the helper, for Stage N
pcap2_start() { PCAP2=$OUT/$1.moto.logcat; adb -s "$MOTO" logcat -c; adb -s "$MOTO" logcat -G 16M >/dev/null 2>&1
  stdbuf -oL adb -s "$MOTO" logcat -v epoch > "$PCAP2" & PCAP2PID=$!; }
pcap2_stop() { [ -n "$PCAP2PID" ] || return 0; kill $PCAP2PID 2>/dev/null; wait $PCAP2PID 2>/dev/null; PCAP2PID=
  tr -d '\r' < "$PCAP2" > "$PCAP2.tmp" && mv "$PCAP2.tmp" "$PCAP2"; }
p2cnt() { grep -acF -- "$1" "$PCAP2"; }
HPKG=com.andrerinas.wirelesshelper.debug; HACT=$HPKG/com.andrerinas.wirelesshelper.MainActivity
hkill() { adb -s "$1" shell am force-stop $HPKG; }
hup() { adb -s "$1" shell am start -a android.intent.action.VIEW -d wirelesshelper://start -n $HACT >/dev/null 2>&1; }
# epid <model> : the newest endpoint id the unit found for that phone model
epid() { grep -aF "NearbyManager: Endpoint FOUND: $1 (" "$CAP" | tail -n 1 | tr -d '\r' | sed -E 's/.*\(([^()]*)\)[[:space:]]*$/\1/'; }
# pocoput <base.xml> <spec...> : D-POCO as head unit (not rooted), written through run-as on stdin, read back
pocoput() { python3 ohu_setkeys.py "$1" new.xml "${@:2}" || return 1
  adb -s "$HU" exec-in "run-as $PKG sh -c 'cat > shared_prefs/settings.xml'" < new.xml
  adb -s "$HU" shell run-as $PKG cat shared_prefs/settings.xml | grep -aoE '(wifi-connection-mode|log-level|auto-connect-last-session|auto-start-on-usb|use-libusb|separate-audio-streams|video-profile-starvation-cap)[^/]*'; }   # the starvation key must NOT print
usb_live() { waitfor 90 'SSL handshake complete' "$1" && waitfor 30 "$REND" "$1"; }
```

Every action in this round:

| Action | Command | Notes |
|---|---|---|
| The WiFi button (arm) | `send ACTION_START_WIRELESS_SCAN` | D-HU and D-SAM; D-POCO refuses it after a force-stop |
| Raise the projection | `send ACTION_RAISE_PROJECTION` | only inside `up_session` when the projection did not come forward |
| End a session, keep the stack | `send ACTION_END_SESSION_STAY_ARMED` | HS's control session end |
| Pick a Nearby phone | `send ACTION_NEARBY_CONNECT --es extra_endpoint_id <id>` | |
| Exit the projection | `send ACTION_DISCONNECT` | between Nearby cycles |
| Stop the service | `send ACTION_EXIT` | only to a running app |
| Mark a step | `mark <label>` | `AutomationMarker: <label>` at WARN |
| Read the build | `send ACTION_QUERY_STATE` | `commit` on the `data=` line |
| Open the audio streams screen | `open_audio` | not a verb; template section 3 deep-links settings this way |
| Close the settings screen | `close_settings` | `input keyevent 4`, a scripted keyevent |
| Media play and next | `adb -s $HU shell input keyevent KEYCODE_MEDIA_PLAY` / `KEYCODE_MEDIA_NEXT` | relayed to the phone's player |
| A cover (N3) | `adb -s $HU shell am start -n $SA --ei extra_destination 0` | the settings screen over the projection |
| The return (N3) | `back` (`ptr_lib.sh`) | the same intent the service builds for a raise |
| Launch our app | `adb -s $HU shell am start -n $MAIN` | D-POCO, D-SAM, D-HP |
| The Save | H-SAVE / H-SAVE-HP | 3.3 |

Every `send` must print `AutomationReceiver: <action>` in the capture. A step with no such line never landed; its cycle is void, not a FAIL.

## 7. Runs

**The point of the round is N1-C.** N2 is the ordinary Save on the same transport, N1-B is the baseline's Save, N3 asks whether round 1's FAIL still reproduces, and the other stages reach the transports round 1 did not.

**Per-run rules.** Before each run: the identity gate (1a) when the APK changed, a settings read-back, and the discard rules of template section 4 per cycle: `MATCH! Starting AapService`, `AapRead: Magic Garbage detected in header`, or a second `createGroup SUCCESS` inside one Native cycle where no group change was asked for voids that cycle (D-SAM's two `createGroup SUCCESS` before its first SSL are how it settles, not a discard). Every count is over the cycle's own lines. **Every run keeps a phone capture beside the unit capture** (`pcap_start` inside `r_open`) and grades at least one condition from it.

### R0 Gate (once)

PASS needs: the three md5s recorded and different (two if X is not built); C's commit and X's tree as stated; the 1a table matching per arm; `commit` recorded; `run_unit_tests.sh` on each arm with 0 failures from the JUnit XML, C's count 2766 (a failure stops that arm); `adb install -r` succeeding, never an uninstall; each unit's settings backup diffed against the file after each install, with the delta stated; D-HU's `stat -c %U:%G` of `shared_prefs/`; the rig audio keys and `separate-audio-streams` as found; Gearhead's versionName on D-POCO and D-MOTO (`dumpsys package com.google.android.projection.gearhead | grep versionName`); the helper is not needed this round (3.1); `adb -s $HU shell dumpsys bluetooth_manager | grep -a -A20 'Bonded devices'` on D-HU.

### A0 The audio streams screen opens, per arm (Stage A, before the arm's first Save)

```bash
RUN=A0-$ARM; CAP=$OUT/$RUN.logcat; apk_check; dest_id; cap_start
adb -s $HU shell am start -n $MAIN >/dev/null; sleep 10
open_audio; echo "route=$OPEN_ROUTE"; adb -s $HU shell screencap -p /sdcard/s.png; adb -s $HU pull -q /sdcard/s.png $OUT/A0-$ARM.png
close_settings; cap_stop
```

PASS: `dest_id` printed a non-zero id, `open_audio` returned 0 (route `am` on D-HU), and the screenshot shows the "Separate audio streams" toggle and the Save button. FAIL on an arm makes every Save run of that arm UNTESTABLE; say which route failed. Repeat A0 on D-POCO at U0 and on D-SAM at D1 (route `run-as` expected there).

### Stage A preparation (once)

```bash
adb -s $MOTO shell svc bluetooth disable; adb -s $MOTO shell svc wifi disable; sleep 5
phone_bt $MOTO; adb -s $MOTO shell dumpsys wifi | grep -a -m1 'Wi-Fi is'        # both must read off
adb -s $PH shell dumpsys window | grep mCurrentFocus                             # not a Settings screen; else input keyevent KEYCODE_HOME
phone_bt $PH                                                                      # must read on
adb -s $HU shell am force-stop $PKG; adb -s $HU shell cat /data/data/$PKG/shared_prefs/settings.xml > $BASEXML
```

Re-enable D-MOTO's radios at the end of Stage A (`svc bluetooth enable`, `svc wifi enable`) and read both back.

### N1-B The baseline's Save (B, 1 cycle, max 2 attempts)

What `main` does on Save: `AudioStreamSettingsFragment.saveSettings` sends `ACTION_STOP_SERVICE` when a session is live. The service stops with a ByeBye as a user exit, `MainActivity` closes, and nothing reconnects until the user acts. The phone's Bluetooth stays on.

```bash
r_open N1-B $AKEYS; L=$(nl); send ACTION_START_WIRELESS_SCAN >/dev/null; up_session 120 $L || echo "N1-B first session failed"
sleep 15; p0=$(pcnt 'Critical error')
hand_save N1-B-1 && { close_settings; hold_until 60000; }
E=$(nl); echo -e "N1-B-1\tstop=$(cnt $SAVE_LN $E "$SAVE_B")\tdestroy_ms=$(dt $SAVE_LN $(lno $SAVE_LN 'AapService destroying'))\tuser_exit=$(cntw 0 3000 '(user_exit)')\tssl60=$(cntw 0 60000 'SSL handshake complete')\tpoke60=$(cntw 0 60000 'NativeAA: Attempting active poke to device')\tcreds60=$(cntw 0 60000 'AapService: Received WiFi credentials from manager')\tmain_closed=$(cnt $SAVE_LN $E 'MainActivity: Received finish request. Closing.')\tcrit=$(( $(pcnt 'Critical error') - p0 ))\tfatal=$(cnt $SAVE_LN $E 'FATAL EXCEPTION')" | tee -a $OUT/N1-B.tsv
L=$(nl); adb -s $HU shell am start -n $MAIN >/dev/null; up_session 120 $L && echo "N1-B relaunch session ok" | tee -a $OUT/N1-B.tsv
r_close N1-B
```

**PASS (a characterisation of `main`):** `stop` 1; `destroy_ms` 5500 or less; `user_exit` 1; `ssl60` 0 and `poke60` 0 (nothing came back by itself in 60 s); the relaunch forms a session; `fatal` 0. Phone: report `crit`; it is not graded (3.5). Any other shape is a FAIL of the description above, and the results say what B did instead.

### N1-C A Native AA Save where the phone does not come back (the point of the round; C, 3 graded cycles, max 6 attempts)

The claim: the wake hold refuses every automatic poke for 30 s after the Save, then the fallback re-reads the group and the poke runs. The phone's Bluetooth is off, so the phone cannot return by itself and the 30 s mark is reached with the same `Disconnected` state.

```bash
r_open N1-C $AKEYS; L=$(nl); send ACTION_START_WIRELESS_SCAN >/dev/null; up_session 120 $L || echo "N1-C first session failed"
ORDER=1; graded=0
n1_cycle() { local k=$1 S B25 B50 R L2 back=0 route=none p0 pk
  S=$(nl); mark N1-$k-go; p0=$(pcnt 'Critical error')
  if [ $ORDER = 1 ]; then
    adb -s $PH shell svc bluetooth disable; sleep 15
    if ! bt_off $PH || [ "$(cnt $S '$' 'AapService: session state disconnected')" -gt 0 ] || ! waitfor 10 "$REND" $S; then
      echo -e "N1-$k\tvoid-order1\tbt=$(phone_bt $PH)" | tee -a $OUT/N1-C.tsv; ORDER=2; LAST=void; n1_recover $k; return; fi
    hand_save N1-$k || { LAST=void; n1_recover $k; return; }
  else
    fire_on $S 'CommManager: audio settings changed; reconnecting the projection session' adb -s $PH shell svc bluetooth disable
    hand_save N1-$k || { kill $FIREPID 2>/dev/null; LAST=void; n1_recover $k; return; }
  fi
  close_settings
  hold_until 25000; B25=$(phone_bt $PH); hold_until 50000; B50=$(phone_bt $PH); E=$(nl)
  R="N1-$k\torder=$ORDER\tbt25=$(echo $B25 | grep -qiE 'enabled: false|state: OFF' && echo off || echo ON)\tbt50=$(echo $B50 | grep -qiE 'enabled: false|state: OFF' && echo off || echo ON)"
  R="$R\tpoke_lt29=$(cntw 0 29499 'NativeAA: Attempting active poke to device')\tconnect_lt29=$(cntw 0 29499 'NativeAA: Calling socket.connect()')"
  R="$R\thold=$(( $(cntw 0 29499 'AapService: automatic wake is paused by the session policy') + $(cntw 0 29499 'NativeAA: the phone ended the last session itself') ))\tleftover=$(cntw 0 29499 'NativeAA: the phone ended the last session itself')"
  R="$R\trefresh_30=$(( $(cntw 29500 33000 'AapService: WiFi Direct credential refresh requested.') + $(cntw 29500 33000 'WifiLauncher: Initializing WiFi Mode: NATIVE') ))\tcreds_30=$(cntw 29500 33000 'AapService: Received WiFi credentials from manager')"
  R="$R\tt_poke=$(firstw 'NativeAA: Attempting active poke to device')\tssl_lt50=$(cntw 0 50000 'SSL handshake complete')"
  R="$R\tsettings_restart=$(cntw 0 3000 '(settings_restart)')\tlink_lost=$(cntw 0 3000 '(link_lost)')\tuser_exit=$(cntw 0 3000 '(user_exit)')\tkeep=$(cntw 0 50000 'AapService: Native AA session ended; keeping the ')\tstop=$(cntw 0 50000 "$SAVE_B")"
  n1_recover $k
  R="$R\tback=$BACK\troute=$ROUTE\tt_back=$TBACK\tpokes_after_bt_on=$PKBACK\tcrit=$(( $(pcnt 'Critical error') - p0 ))\tfatal=$(cnt $S '$' 'FATAL EXCEPTION')"
  echo -e "$R" | tee -a $OUT/N1-C.tsv
  LAST=graded; echo -e "$R" | grep -q 'bt25=ON\|bt50=ON' && LAST=void; [ "$(cntw 0 50000 'SSL handshake complete')" -gt 0 ] && LAST=void; }
# n1_recover <k> : phone Bluetooth on, then the ordinary rules must bring the phone back
n1_recover() { local L; adb -s $PH shell svc bluetooth enable; sleep 3; phone_bt $PH >> $OUT/N1-C.bt.log
  L=$(nl); BACK=0; ROUTE=none
  if up_session 120 $L; then BACK=1; ROUTE=auto
  else send ACTION_START_WIRELESS_SCAN >/dev/null; up_session 120 $L && { BACK=1; ROUTE=wifi-button; }; fi
  TBACK=$(dt $L $(lno $L 'SSL handshake complete')); PKBACK=$(cnt $L '$' 'NativeAA: Attempting active poke to device'); sleep 20; }
for k in 1 2 3 4 5 6; do n1_cycle $k; [ "$LAST" = graded ] && graded=$((graded+1)); [ $graded -ge 3 ] && break; [ "$BACK" = 0 ] && break; done
r_close N1-C
```

**A cycle is void** when D-POCO's Bluetooth read ON at +25 s or +50 s (it self-reverted), or an SSL formed within 50 s of the Save (the phone came back some other way; record how, from the phone capture). **Stop rule:** stop at the 3rd graded cycle, after 6 attempts, or when `n1_recover` fails both routes. Fewer than 2 graded cycles makes N1-C INCONCLUSIVE.

**PASS, on every graded cycle:**

- `poke_lt29` 0 and `connect_lt29` 0: no poke in the first 29.5 s after the Save;
- `hold` 1 or more: a credential delivery reached the hold inside the window, so the "no poke" above was tested and not merely unreached;
- `refresh_30` 1 or more and `creds_30` 1 or more: the fallback re-read the group within 3 s of the 30 s mark;
- `t_poke` between 29500 and 33000 ms;
- `settings_restart` 1, `link_lost` 0, `user_exit` 0, `keep` 0, `stop` 0;
- `back` 1 with `route=auto` (the ordinary loop brought the phone back after its Bluetooth came on);
- `fatal` 0. Report `crit` per cycle from the phone capture; it is not graded (3.5).

**A late wake:** `t_poke` between 33000 and 45000 ms is still a PASS for the blocker. Report it as `late wake` with the number.

**FAIL** on any graded cycle with a poke before 29.5 s, with no poke by Save + 45 s (`t_poke` NA or above 45000), or with `route=wifi-button` and `pokes_after_bt_on` 0 (the automatic wake stayed off: the old blocker). `route=wifi-button` with pokes after Bluetooth came on is a poke the phone did not answer, which this rig does sometimes (`rig-quirks/topics/bt.md`); record it, it is not a FAIL. **What a PASS would look like if the change did nothing:** on the head round 2 reviewed, the hold never ended, so `t_poke` would be NA and `route` would need the WiFi button with 0 pokes. Report `leftover` (the misleading wording) as a count.

### N2 A Native AA Save where the phone comes back (C, 5 cycles, max 7 attempts)

Round 1 measured the reconnect on the older head. This run repeats it on the rebased code, with video and audio after it.

```bash
r_open N2-C $AKEYS; L=$(nl); send ACTION_START_WIRELESS_SCAN >/dev/null; up_session 120 $L || echo "N2-C first session failed"
adb -s $HU shell input keyevent KEYCODE_MEDIA_PLAY; sleep 20
AUDIO_OK=$(cnt $L '$' 'AudioDecoder.start: channel='); echo "N2 audio precondition=$AUDIO_OK" | tee -a $OUT/N2-C.tsv   # 0: the audio leg is INCONCLUSIVE
done=0
for k in 1 2 3 4 5 6 7; do
  S=$(nl); p0=$(pcnt 'Critical error'); mark N2-$k-go
  hand_save N2-$k || continue
  close_settings
  waitfor 60 'SSL handshake complete' $SAVE_LN; LS=$(lno $SAVE_LN 'SSL handshake complete')
  waitfor 30 "$REND" ${LS:-$SAVE_LN}; LV=$(tail -n +$(( LS>0 ? LS : SAVE_LN )) "$CAP" | grep -anE -m1 "$REND" | cut -d: -f1)
  sleep 5; adb -s $HU shell input keyevent KEYCODE_MEDIA_PLAY; sleep 25; E=$(nl)
  T2=$(firstw 'SSL handshake complete')
  echo -e "N2-$k\tt_ssl2=$T2\tt_video2=$( [ -n "$LV" ] && [ $LS -gt 0 ] && dt $LS $(( LS + LV - 1 )) || echo NA )\taudio=$( [ $LS -gt 0 ] && cnt $LS $E 'AudioDecoder.start: channel=' || echo NA )\tssl_count=$(cntw 0 40000 'SSL handshake complete')\tpoke_before_ssl2=$( [ "$T2" != NA ] && cntw 0 $T2 'NativeAA: Attempting active poke to device' || echo NA )\tsettings_restart=$(cntw 0 3000 '(settings_restart)')\tlink_lost=$(cntw 0 3000 '(link_lost)')\tuser_exit=$(cntw 0 3000 '(user_exit)')\tstop=$(cnt $SAVE_LN $E "$SAVE_B")\tkeep=$(cnt $SAVE_LN $E 'AapService: Native AA session ended; keeping the ')\tdisc=$(cnt $SAVE_LN $E 'AapService: Disconnected. Restarting discovery loop in 2s...')\traise=$(cnt $SAVE_LN $E 'AapService: raising the projection by ')\tstarve=$(cnt $SAVE_LN $E 'sessions in a row ended without a single video')\tcrit=$(( $(pcnt 'Critical error') - p0 ))\tfatal=$(cnt $S $E 'FATAL EXCEPTION')" | tee -a $OUT/N2-C.tsv
  done=$((done+1)); [ $done -ge 5 ] && break; sleep 20
done
r_close N2-C
```

**PASS, over at least 4 completed cycles:** on every cycle `t_ssl2` 30000 ms or less (round 1: 1979 to 6572 ms on the older head); `t_video2` 30000 ms or less; `audio` 1 or more (if the precondition held); `ssl_count` 1 (one reconnect, no second); `poke_before_ssl2` 0; `settings_restart` 1, `link_lost` 0, `user_exit` 0, `stop` 0, `keep` 0, `disc` 0; `starve` 0; `fatal` 0. Phone: report `crit`; it is not graded (3.5). A cycle with no SSL in 60 s is a FAIL. **What a PASS would look like if the change did nothing:** a Save that only restarted tracks prints no Save line, and `hand_save` waits for that line, so no cycle can pass without the session-ending path running.

### N3 Round 1's FAIL sequence: skips, GLES covers, then the Save (C 2 runs, X 2 runs)

Round 1 lost video after this sequence on the combo arm (3 of 3) and kept it on this PR alone. This run asks whether it still reproduces on the rebased C, and on the current combo X. Keys: `A3KEYS` (GLES, H.265). D-POCO plays music (Spotify, as round 1).

```bash
n3_run() { local L S c h; r_open N3-$ARM-$1 $A3KEYS; L=$(nl); send ACTION_START_WIRELESS_SCAN >/dev/null; up_session 120 $L || echo "N3 first session failed"
  adb -s $HU shell input keyevent KEYCODE_MEDIA_PLAY; sleep 10
  for c in 1 2 3 4 5 6; do adb -s $HU shell input keyevent KEYCODE_MEDIA_NEXT; sleep 10; done
  for c in 1 2 3; do h=$(echo "5 10 5" | cut -d' ' -f$c); S=$(nl); mark N3-cover-$c
    adb -s $HU shell am start -n $SA --ei extra_destination 0 >/dev/null; sleep $h; mark N3-return-$c; back
    waitfor 15 'AapProjectionActivity: onResume' $S || echo "N3 no resume $c"; sleep 12
    echo -e "N3-$ARM-$1-cover$c\tteardown=$(( $(cnt $S '$' 'Decoder stopped:') + $(cnt $S '$' 'New surface set:') ))\tfirst_frame=$(cnt $S '$' 'First frame rendered')" | tee -a $OUT/N3.tsv; done
  PRE=$(grep -aE 'Throughput over [0-9]+ms: rendered=' "$CAP" | tail -n 3 | grep -aoE 'rendered=[0-9]+' | cut -d= -f2 | awk '{s+=$1} END{print s+0}')
  hand_save N3-$ARM-$1 || { r_close N3-$ARM-$1; return; }; close_settings
  waitfor 60 'SSL handshake complete' $SAVE_LN; LS=$(lno $SAVE_LN 'SSL handshake complete'); sleep 60; E=$(nl)
  V2=$( [ $LS -gt 0 ] && sed -n "$LS,${E}p" "$CAP" | grep -acE "$REND" || echo 0 )
  echo -e "N3-$ARM-$1\tpre_rendered=$PRE\tt_ssl2=$(firstw 'SSL handshake complete')\tvideo2_windows=$V2\tvideo2=$( [ $V2 -ge 3 ] && echo yes || echo NO )\tdropped11=$(cnt $SAVE_LN $E 'AapVideo: Dropped Flag 11 packet')\tsink7=$(cnt $SAVE_LN $E 'Media Sink Setup Request: 7 on channel VIDEO')\tcrit=$(pcnt 'Critical error')\tfatal=$(cnt 1 '$' 'FATAL EXCEPTION')" | tee -a $OUT/N3.tsv
  r_close N3-$ARM-$1; }
n3_run 1; n3_run 2          # on C; then install X (1a gate) and run again: n3_run 1; n3_run 2
```

**PASS (C):** on both runs, `pre_rendered` above 0 and `video2=yes` (3 rendered windows within 60 s of the reconnect's SSL), `fatal` 0. Phone: report `crit`; it is not graded (3.5). A run with `pre_rendered` 0 lost the picture during the covers, before the Save (the addendum's CV shape, PR 1046's side): report it as `picture lost before the Save` and grade the run INCONCLUSIVE for this PR. **X (characterisation):** report `pre_rendered`, `video2` and `dropped11` per run, and the line `round 1 combo FAIL reproduces on X: yes or no`. An X `video2=NO` is a finding for the PR 1046 thread and does not fail C.

### HS A Save in Headunit Server mode (Stage H; C, 3 cycles, max 5)

The Save redials the same server (`restartEndpoint`, `ip to 5277`). The server must answer the redial and then the next ordinary session with no toggle on the phone. D-HU and D-POCO on the house network: read `dumpsys wifi | grep -iE "mWifiInfo"` on both and record the SSID and frequency.

```bash
r_open HS-C $HKEYS; listening || srv_restart HS-pre || echo "HS UNTESTABLE: server"
L=$(nl); send ACTION_START_WIRELESS_SCAN >/dev/null; up_session 90 $L || { srv_restart HS-pre2; L=$(nl); send ACTION_START_WIRELESS_SCAN >/dev/null; up_session 90 $L || echo "HS UNTESTABLE: server wedged"; }
done=0; sdown=0
for k in 1 2 3 4 5; do
  S=$(nl); p0=$(pcnt 'Critical error'); n0=$(pcnt 'Network server running on port'); mark HS-$k-go
  hand_save HS-$k || continue; close_settings
  waitfor 30 'SSL handshake complete' $SAVE_LN; T2=$(firstw 'SSL handshake complete'); sleep 10
  UP=$(listening && echo UP || echo DOWN)
  A=$(nl); send ACTION_END_SESSION_STAY_ARMED >/dev/null; waitfor 60 'SSL handshake complete' $A; T3=$(dt $A $(lno $A 'SSL handshake complete')); sleep 10; E=$(nl)
  echo -e "HS-$k\tt_conn=$(firstw 'AapService: session state connecting')\tt_ssl2=$T2\tdisc_before_ssl2=$( [ "$T2" != NA ] && cntw 0 $T2 'AapService: Disconnected. Restarting discovery loop in 2s...' || echo NA )\tsettings_restart=$(cntw 0 3000 '(settings_restart)')\tlink_lost=$(cntw 0 3000 '(link_lost)')\tlistening_after=$UP\tt_ssl3=$T3\tsrv_lines=$(( $(pcnt 'Network server running on port') - n0 ))\tcrit=$(( $(pcnt 'Critical error') - p0 ))\tfatal=$(cnt $S $E 'FATAL EXCEPTION')" | tee -a $OUT/HS-C.tsv
  if [ "$T2" = NA ] || [ "$T3" = NA ]; then listening || { sdown=$((sdown+1)); echo -e "HS-$k\tserver-down" | tee -a $OUT/HS-C.tsv; srv_restart HS-$k; L=$(nl); send ACTION_START_WIRELESS_SCAN >/dev/null; up_session 90 $L; continue; }; fi
  done=$((done+1)); [ $done -ge 3 ] && break; sleep 20
done
r_close HS-C
```

**PASS, on every completed cycle:** `t_conn` 5000 ms or less and `t_ssl2` 30000 ms or less; `disc_before_ssl2` 0 (the redial, not discovery, formed the session); `settings_restart` 1, `link_lost` 0; `listening_after` UP; `t_ssl3` 60000 ms or less (the server answered the next session); `fatal` 0. Phone: `srv_lines` 0 (no server restart was needed inside the cycle). Report `crit`; it is not graded (3.5). **FAIL:** a cycle with no `t_ssl2` or no `t_ssl3` while the server still listens (the redial wedged it). **Server down:** a `server-down` row after a Save while `ACTION_END_SESSION_STAY_ARMED` steps never produce one is a FAIL if it happens twice; if the ordinary step also produces one, the run is INCONCLUSIVE (the server's own fault, `rig-dpoco-headunit-server-down`).

### NB0 Nearby sessions form at all (Stage N gate; C, 3 attempts)

**Not run this round** (3.1, 3.7). NB0 to HW are kept for a round on a phone whose Android Auto accepts the helper's launch.

Install and configure the helper on both phones as `pr-1064-nearby-attempts-round1-brief.md` sections 1b, 4.3 and 4.4 say (`connection_mode` 4, Nearby). Both phones and D-HU on the house network; record each SSID and frequency. Force-stop our app on both phones and any release helper.

```bash
MODEL_A=$(adb -s $PH shell getprop ro.product.model | tr -d '\r'); MODEL_B=$(adb -s $MOTO shell getprop ro.product.model | tr -d '\r')
r_open NB0-C $NKEYS; pcap2_start NB0-C; hkill $PH; hkill $MOTO
adb -s $HU shell am start -n $MAIN >/dev/null; sleep 10
for k in 1 2 3; do hkill $PH; hup $PH; L=$(nl); send ACTION_START_WIRELESS_SCAN >/dev/null
  waitfor 40 "NearbyManager: Endpoint FOUND: $MODEL_A \\(" $L || { echo "NB0-$k no endpoint" | tee -a $OUT/NB0.tsv; continue; }
  p0=$(pcnt 'AA is now flowing through proxy'); send ACTION_NEARBY_CONNECT --es extra_endpoint_id "$(epid "$MODEL_A")" >/dev/null
  up_session 60 $L && s=1 || s=0; echo -e "NB0-$k\tsession=$s\tproxy=$(( $(pcnt 'AA is now flowing through proxy') - p0 ))" | tee -a $OUT/NB0.tsv
  send ACTION_DISCONNECT >/dev/null; sleep 6; done
pcap2_stop; r_close NB0-C
```

**PASS:** 2 or more of 3 with `session=1` and `proxy` 1 or more (phone-graded). **If C scores 0 of 3,** install B and run NB0 once on B: B also 0 makes Stage N INCONCLUSIVE ("Nearby helper sessions do not form on this phone", with the last 30 `HUREV_` lines of the phone capture); B at 2 or more makes NB0 a FAIL of C, and NB1 and NB2 are skipped.

### NB1 A Nearby Save where the phone comes back, with a second phone advertising (C, 2 cycles, max 4)

The Save grants one retry of the same phone even with `auto-connect-last-session=false`, and ignores every other phone while the 30 s preference holds.

```bash
r_open NB1-C $NKEYS; pcap2_start NB1-C; hkill $MOTO; hup $MOTO; hkill $PH; hup $PH
adb -s $HU shell am start -n $MAIN >/dev/null; sleep 10; L=$(nl); send ACTION_START_WIRELESS_SCAN >/dev/null
waitfor 40 "NearbyManager: Endpoint FOUND: $MODEL_A \\(" $L; send ACTION_NEARBY_CONNECT --es extra_endpoint_id "$(epid "$MODEL_A")" >/dev/null; up_session 60 $L
done=0
for k in 1 2 3 4; do
  S=$(nl); pa0=$(pcnt 'AA is now flowing through proxy'); pb0=$(p2cnt 'NearbyStrategy: Connected to'); mark NB1-$k-go
  hand_save NB1-$k || continue; close_settings
  waitfor 30 'SSL handshake complete' $SAVE_LN; T2=$(firstw 'SSL handshake complete'); sleep 10; E=$(nl)
  echo -e "NB1-$k\tt_ssl2=$T2\treq_before_ssl2=$( [ "$T2" != NA ] && cntw 0 $T2 'NearbyManager: Requesting connection to endpoint: ' || echo NA )\tverb=$(cnt $SAVE_LN $E 'AapService: Connecting to Nearby endpoint ')\tfoundB=$( [ "$T2" != NA ] && cntw 0 $T2 "NearbyManager: Endpoint FOUND: $MODEL_B (" || echo NA )\tautocheck=$( [ "$T2" != NA ] && cntw 0 $T2 'NearbyManager: Auto-connect check: Enabled=' || echo NA )\tsettings_restart=$(cntw 0 3000 '(settings_restart)')\tproxyA=$(( $(pcnt 'AA is now flowing through proxy') - pa0 ))\tconnB=$(( $(p2cnt 'NearbyStrategy: Connected to') - pb0 ))\tfatal=$(cnt $S $E 'FATAL EXCEPTION')" | tee -a $OUT/NB1-C.tsv
  done=$((done+1)); [ $done -ge 2 ] && break; sleep 20
done
pcap2_stop; r_close NB1-C
```

**PASS, on every completed cycle:** `t_ssl2` 30000 ms or less; `req_before_ssl2` 1 or more with `verb` 0 (the Save's grant, not a user connect); `autocheck` 0 (the preference held); `settings_restart` 1; `fatal` 0. Phones (graded): `proxyA` 1 or more on D-POCO and `connB` 0 on D-MOTO. Report `foundB`: a cycle with `foundB` 0 did not offer the second phone inside the window, so its `autocheck` 0 proves less; say so.

### NB2 A Nearby Save where the phone does not come back (C, 2 cycles, max 4)

The preference must expire at 30 s, after which the ordinary rules apply again.

```bash
r_open NB2-C $NKEYS; pcap2_start NB2-C; hkill $MOTO; hup $MOTO; hkill $PH; hup $PH
adb -s $HU shell am start -n $MAIN >/dev/null; sleep 10; L=$(nl); send ACTION_START_WIRELESS_SCAN >/dev/null
waitfor 40 "NearbyManager: Endpoint FOUND: $MODEL_A \\(" $L; send ACTION_NEARBY_CONNECT --es extra_endpoint_id "$(epid "$MODEL_A")" >/dev/null; up_session 60 $L
done=0
for k in 1 2 3 4; do
  S=$(nl); pb0=$(pcnt 'AA is now flowing through proxy'); px0=$(p2cnt 'AA is now flowing through proxy'); mark NB2-$k-go
  fire_on $S 'CommManager: audio settings changed; reconnecting the projection session' adb -s $PH shell am force-stop $HPKG
  hand_save NB2-$k || { kill $FIREPID 2>/dev/null; continue; }; close_settings
  hold_until 10000; hkill $MOTO; hup $MOTO
  hold_until 36000; hkill $MOTO; hup $MOTO; waitfor 40 "NearbyManager: Endpoint FOUND: $MODEL_B \\(" $(nl)
  A=$(nl); send ACTION_NEARBY_CONNECT --es extra_endpoint_id "$(epid "$MODEL_B")" >/dev/null; up_session 60 $A && ok=1 || ok=0; E=$(nl)
  echo -e "NB2-$k\tssl_lt30=$(cntw 0 29499 'SSL handshake complete')\treq_lt30=$(cntw 0 29499 'NearbyManager: Requesting connection to endpoint: ')\tautocheck_lt30=$(cntw 0 29499 'NearbyManager: Auto-connect check: Enabled=')\tfoundB_lt30=$(cntw 0 29499 "NearbyManager: Endpoint FOUND: $MODEL_B (")\tfallback_30=$(cntw 29500 33000 'NearbyManager: Starting Nearby (Discoverer only)...')\tautocheck_gt30=$(cntw 30000 999999 'NearbyManager: Auto-connect check: Enabled=')\treq_30_to_verb=$(sed -n "$SAVE_LN,${A}p" "$CAP" | awk -v t0=$T0 'function m(x,p){split(x,p,/[:.]/);return ((p[1]*60+p[2])*60+p[3])*1000+p[4]} index($0,"NearbyManager: Requesting connection to endpoint: ")&&m($2)-t0>=30000{n++} END{print n+0}')\tverb_session=$ok\tproxyA=$(( $(pcnt 'AA is now flowing through proxy') - pb0 ))\tproxyB=$(( $(p2cnt 'AA is now flowing through proxy') - px0 ))\tfatal=$(cnt $S $E 'FATAL EXCEPTION')" | tee -a $OUT/NB2-C.tsv
  send ACTION_DISCONNECT >/dev/null; sleep 6; hkill $PH; hup $PH; L=$(nl); send ACTION_START_WIRELESS_SCAN >/dev/null
  waitfor 40 "NearbyManager: Endpoint FOUND: $MODEL_A \\(" $L; send ACTION_NEARBY_CONNECT --es extra_endpoint_id "$(epid "$MODEL_A")" >/dev/null; up_session 60 $L
  done=$((done+1)); [ $done -ge 2 ] && break
done
pcap2_stop; r_close NB2-C
```

**A cycle is void** when `ssl_lt30` is 1 or more (D-POCO came back before its helper died). **PASS, on every completed cycle:** `foundB_lt30` 1 or more (the second phone was offered inside the window), with `req_lt30` 0 and `autocheck_lt30` 0 (it was ignored); `autocheck_gt30` 1 or more (the ordinary rule ran again after the expiry); `req_30_to_verb` 0 (with auto-connect off, nothing connected by itself); `verb_session` 1; `fatal` 0. Phones (graded): `proxyB` 1 or more on D-MOTO and `proxyA` 0 on D-POCO. **Record for the review** `fallback_30`: a value of 1 or more is the fallback restarting discovery at 30 s (finding 1's smaller form on Nearby).

### HW Helper over WiFi Direct: does the 30 s fallback recreate the group under a joining phone? (C; a gate, then 2 cycles; characterisation)

Set the helper on D-POCO to WiFi Direct: `connection_mode` 3 in its own code, written as in `pr-1064-nearby-attempts-round1-brief.md` section 4.4 with `value="3"`; read it back; H5 if `exec-in` fails. D-MOTO's helper stays stopped.

```bash
r_open HW-C $WKEYS; hkill $MOTO; hkill $PH; hup $PH
adb -s $HU shell am start -n $MAIN >/dev/null; sleep 10; L=$(nl); send ACTION_START_WIRELESS_SCAN >/dev/null
up_session 120 $L || { echo "HW UNTESTABLE: no Helper WiFi Direct session in 120 s" | tee -a $OUT/HW-C.tsv; r_close HW-C; }
for k in 1 2; do
  S=$(nl); p0=$(pcnt 'AA is now flowing through proxy'); mark HW-$k-go
  fire_on $S 'CommManager: audio settings changed; reconnecting the projection session' adb -s $PH shell am force-stop $HPKG
  hand_save HW-$k || continue; close_settings
  hold_until 26000; hup $PH; hold_until 120000; E=$(nl)
  echo -e "HW-$k\tgroups_0_29=$(cntw 0 29499 'createGroup SUCCESS')\tgroups_29_45=$(cntw 29500 45000 'createGroup SUCCESS')\tt_disc=$(firstw 'AapService: Disconnected. Restarting discovery loop in 2s...')\tt_ssl=$(firstw 'SSL handshake complete')\tproxy=$(( $(pcnt 'AA is now flowing through proxy') - p0 ))\tfatal=$(cnt $S $E 'FATAL EXCEPTION')" | tee -a $OUT/HW-C.tsv
  [ "$(firstw 'SSL handshake complete')" = NA ] && { L=$(nl); send ACTION_START_WIRELESS_SCAN >/dev/null; up_session 120 $L; }
done
r_close HW-C
```

**Verdict per cycle:** `finding 1 reproduced` when `groups_29_45` is 1 or more and `t_ssl` is NA or above 90000 (the group was recreated as the phone came back, and the phone did not get in); `recreated, joined anyway` when `groups_29_45` is 1 or more and a session formed; `not reached` when `groups_29_45` is 0 (record `t_disc`: a failed redial takes the ordinary path before 30 s and the fallback never runs). **PASS:** `fatal` 0 and the session comes back on the cycle or after the WiFi button. Phone: `proxy` 1 or more on every cycle with an SSL. Restore the helper to `connection_mode` 4 afterwards.

### U0 Stage U setup and gate (once)

Follow `pr-1065-reconnect-timers-round1-brief.md` U0 steps for wireless adb, D-POCO's own head unit server (must not listen), D-MOTO's Bluetooth (must be on), and `dumpsys usb` before. Do not start `fake5277.py`. Then:

```bash
HU=$POCO_IP:5555; PH=$MOTO; PUT=pocoput; BASEXML=$OUT/settings_backup_pr1047_poco.xml; UNIT=D-POCO
adb -s $HU shell am force-stop $PKG; adb -s $HU shell run-as $PKG cat shared_prefs/settings.xml > $BASEXML
adb -s $HU install -r <C apk>; ARM=C; WANT_MD5=<C md5>       # then the 1a gate and A0 on D-POCO
```

PASS: wireless adb answers; D-POCO's port table shows no 5277 listener; A0 on D-POCO passes (route `run-as` expected); with `U1KEYS`, a launch and `L=$(nl); cue "H1: plug the dongle into D-POCO's OTG port"; usb_live $L` forms a USB session. A failed U0 makes U1 and U2 UNTESTABLE with the reason.

### U1 A USB Save, then unplug (C, 2 cycles, max 4)

Review round 2's dropped restart: Save, then no device. The retry is held by the settings screen, the close checks USB and finds nothing, and the 30 s fallback must still hand back to the ordinary rules, so a later plug forms a session.

```bash
r_open U1-C $U1KEYS; L=$(nl); adb -s $HU shell am start -n $MAIN >/dev/null; usb_live $L || echo "U1 no USB session at start"
done=0
for k in 1 2 3 4; do
  S=$(nl); p0=$(pcnt 'Critical error'); mark U1-$k-go
  hand_save U1-$k || continue
  waitfor 20 'USB Intent: .*USB_DEVICE_ATTACHED' $SAVE_LN || echo "U1-$k no self re-attach in 20 s" | tee -a $OUT/U1-C.tsv   # the dongle's own drop and return (3.5)
  CU=$(nl); cue "H1: unplug the dongle from D-POCO"; waitfor 120 'USB Intent: .*USB_DEVICE_DETACHED' $CU
  close_settings; hold_until 42000
  P=$(nl); cue "H1: plug the dongle back into D-POCO"; usb_live $P && ok=1 || ok=0; E=$(nl)
  LP=$(lno $P 'Found device already in accessory mode'); [ $LP -eq 0 ] && LP=$(lno $P 'Switching USB device to accessory mode')
  echo -e "U1-$k\theld=$(cntw 0 20000 'UsbLauncher: USB auto-connect held while the settings screen is open; ')\tdetach=$(cnt $CU $P 'USB_DEVICE_DETACHED')\tclose_check=$(cnt $CLOSE_LN $P 'AapService: the settings screen closed with a USB auto-connect held ')\tssl_before_plug=$(cnt $SAVE_LN $P 'SSL handshake complete')\tfallback_30=$(( $(cntw 29500 33000 'WifiLauncher: Initializing WiFi Mode: ') + $(cntw 29500 33000 'NetworkDiscovery: Starting scan...') ))\tusb_retry_line=$(cnt $SAVE_LN $E 'AapService: USB disconnect. Scheduling reconnect check in ')\tt_plug_ssl=$(dt $LP $(lno $P 'SSL handshake complete'))\treplug_session=$ok\tcrit=$(( $(pcnt 'Critical error') - p0 ))\tsave_try=$(( $(cnt $SAVE_LN $CU 'Found device already in accessory mode') + $(cnt $SAVE_LN $CU 'Switching USB device to accessory mode') + $(cnt $SAVE_LN $CU 'Single USB auto-connect: connecting to') ))\traise_refused=$(cnt $SAVE_LN $CLOSE_LN 'AapService: Not raising the projection, the settings screen is open')\tfatal=$(cnt $S $E 'FATAL EXCEPTION')" | tee -a $OUT/U1-C.tsv
  done=$((done+1)); [ $done -ge 2 ] && break; sleep 20
done
r_close U1-C
```

**Name the path of each cycle first.** `path=save` when `ssl_before_plug` is 1 or more (the Save's own retry formed a USB session behind the screen). `path=held` when `ssl_before_plug` is 0 and `held` is 1 or more. Otherwise `path=none`. Record `path`, `save_try` and `raise_refused` on the cycle's row.

**PASS, on every completed cycle:** `detach` 1 or more after the unplug cue; `replug_session` 1 with `t_plug_ssl` 60000 ms or less; `fatal` 0; and, by path:
- `path=held`: `close_check` 1 or more; `fallback_30` 1 or more (the fallback handed back to the ordinary rules at 30 s); `usb_retry_line` 0.
- `path=save`: `raise_refused` 1 or more (the session formed, and the screen stayed in front). `fallback_30` and `usb_retry_line` are reported, not graded, because the unplug ends a live session through the ordinary path.
- `path=none`: `fallback_30` 1 or more.

Phone (D-MOTO): report `crit`; it is not graded (3.5). **FAIL:** a replug that forms no session in 90 s (the state stuck), or `path=held` with `fallback_30` 0.

### U2 A USB Save with the settings screen kept open past 30 s (finding 2; C, 2 cycles, max 4)

```bash
r_open U2-C $U2KEYS; L=$(nl); adb -s $HU shell am start -n $MAIN >/dev/null; usb_live $L || echo "U2 no USB session at start"
echo "quiesce=$(cnt $L '$' 'stopping the wireless stack for the duration of it')" | tee -a $OUT/U2-C.tsv      # 1 or more: wireless was armed before USB
done=0
for k in 1 2 3 4; do
  S=$(nl); p0=$(pcnt 'Critical error'); mark U2-$k-go
  hand_save U2-$k || continue
  hold_until 42000; close_settings; C0=$CLOSE_LN; waitfor 60 'SSL handshake complete' $C0; E=$(nl)
  T_CLOSE=$(( $(ms "$(tat $C0)") - T0 ))
  echo -e "U2-$k\tt_close=$T_CLOSE\theld=$(cntw 0 20000 'UsbLauncher: USB auto-connect held while the settings screen is open; ')\tarm_30_to_close=$(cntw 29500 $T_CLOSE 'WifiLauncher: Initializing WiFi Mode: NATIVE')\tgroups_30_to_close=$(cntw 29500 $T_CLOSE 'createGroup SUCCESS')\tpokes_30_to_close=$(cntw 29500 $T_CLOSE 'NativeAA: Attempting active poke to device')\tpoke_targets=$(sed -n "$SAVE_LN,${C0}p" "$CAP" | grep -aF 'NativeAA: Attempting active poke to device' | sed 's/.*device: //' | sort -u | paste -sd';')\tssl_before_close=$(cnt $SAVE_LN $C0 'SSL handshake complete')\tclose_check=$(cnt $C0 $E 'AapService: the settings screen closed with a USB auto-connect held ')\tt_close_ssl=$(dt $C0 $(lno $C0 'SSL handshake complete'))\tusb_attempt=$(( $(cnt $C0 $E 'Found device already in accessory mode') + $(cnt $C0 $E 'Switching USB device to accessory mode') ))\tcrit=$(( $(pcnt 'Critical error') - p0 ))\tusb_before_first_ssl=$(FS=$(lno $SAVE_LN 'SSL handshake complete'); [ $FS -eq 0 ] && echo NA || echo $(( $(cnt $SAVE_LN $FS 'Found device already in accessory mode') + $(cnt $SAVE_LN $FS 'Switching USB device to accessory mode') + $(cnt $SAVE_LN $FS 'Single USB auto-connect: connecting to') )))\traise_refused=$(cnt $SAVE_LN $C0 'AapService: Not raising the projection, the settings screen is open')\tfatal=$(cnt $S $E 'FATAL EXCEPTION')" | tee -a $OUT/U2-C.tsv
  done=$((done+1)); [ $done -ge 2 ] && break; sleep 20
done
r_close U2-C
```

**Name the path of each cycle first,** as in U1: `path=save` when `ssl_before_close` is 1 or more, `path=held` when it is 0 and `held` is 1 or more, otherwise `path=none`.

**Finding 2 verdict per cycle:** `finding 2 reproduced` when `arm_30_to_close` is 1 or more (wireless armed while USB still waited for the screen); `finding 2 fixed` when `path=save` and `arm_30_to_close` is 0. Report `groups_30_to_close`, `pokes_30_to_close` and `poke_targets` as numbers and names.

**PASS, by path:**
- `path=save`: `usb_before_first_ssl` 1 or more (the session behind the screen is USB, not wireless); `raise_refused` 1 or more; `fatal` 0. `close_check`, `t_close_ssl` and `usb_attempt` are reported, not graded.
- `path=held` or `path=none`: `close_check` 1 or more when `held` is 1 or more; `t_close_ssl` 60000 ms or less with `usb_attempt` 1 or more (USB came back after the close despite the armed wireless stack); `fatal` 0.

Phone (D-MOTO): report `crit`; it is not graded (3.5). **FAIL:** `path=save` with `usb_before_first_ssl` 0 (a wireless session formed behind the screen), or on the other paths no SSL within 60 s of the close, or a first SSL after the close with no USB attempt line before it.

**Closing Stage U:** as `pr-1065-reconnect-timers-round1-brief.md` closes it: `send ACTION_EXIT` to the running app, `headunit://exit`, `sleep 3`, force-stop, restore D-POCO's backup with `pocoput` and read it back, `cue "H1: unplug the dongle; put D-POCO back on its PC cable"`, `adb -s $POCO_IP:5555 usb`, then `HU=$DHU; PH=4f4027e9; PUT=hu_put; BASEXML=$OUT/settings-backup-DHU.xml; UNIT=D-HU`. D-POCO is a phone again from here, which is why it gets `headunit://exit` first (`rig-quirks/topics/wifi.md`).

### D1 One Save on D-SAM, Native AA with D-POCO (Stage D; C, 1 cycle, max 2 attempts)

Charge D-SAM from a separate supply first. Check its clock against the host (`adb -s $SAM shell date`) and record it. Install C with `adb -s $SAM install -r`, run the 1a gate, and A0 on D-SAM (route `run-as` expected).

```bash
HU=$SAM; PUT=tab_put; BASEXML=$OUT/settings-backup-DSAM.xml; UNIT=D-SAM
adb -s $HU shell am force-stop $PKG; adb -s $HU shell run-as $PKG cat shared_prefs/settings.xml > $BASEXML
r_open D1-C $D1KEYS; L=$(nl); adb -s $HU shell am start -n $MAIN >/dev/null; up_session 180 $L || echo "D1 first session failed"
S=$(nl); p0=$(pcnt 'Critical error'); hand_save D1-1 && close_settings
waitfor 120 'SSL handshake complete' ${SAVE_LN:-$S}; LS=$(lno ${SAVE_LN:-$S} 'SSL handshake complete'); waitfor 60 "$REND" ${LS:-$S}; sleep 10; E=$(nl)
echo -e "D1-1\tt_ssl2=$(firstw 'SSL handshake complete')\tvideo2=$( [ $LS -gt 0 ] && cnt $LS $E 'rendered=' || echo 0 )\tsettings_restart=$(cntw 0 3000 '(settings_restart)')\tlink_lost=$(cntw 0 3000 '(link_lost)')\tpoke_lt29=$(cntw 0 29499 'NativeAA: Attempting active poke to device')\tcrit=$(( $(pcnt 'Critical error') - p0 ))\tfatal=$(cnt $S $E 'FATAL EXCEPTION')" | tee -a $OUT/D1-C.tsv
r_close D1-C
```

**PASS:** `t_ssl2` 120000 ms or less (a slower unit); a rendered window after it; `settings_restart` 1, `link_lost` 0; `poke_lt29` 0; `fatal` 0. Phone: report `crit`; it is not graded (3.5). If the first session never forms and the capture repeats the warning that the phone has never opened the Android Auto channel, D1 is INCONCLUSIVE (D-POCO's bond to D-HU, `rig-quirks/units/D-POCO.md`); do not unpair anything.

### D2 One Save on D-HP, Headunit Server with D-POCO's server (Stage D; C, 1 cycle, max 2 attempts)

D-HP has no WiFi Direct and no hotspot, so mode 1 is the only mode it runs. Its capture is tag-filtered (`cap_start` does that below API 21). `svc wifi enable` can take 25 to 30 s; poll `dumpsys wifi` for the station before the launch. Install C with `adb -s $HPS install -r` and run the 1a gate. `dest_id` is not needed (H-SAVE-HP).

```bash
HU=$HPS; PUT=tab_put; BASEXML=$OUT/settings-backup-DHP.xml; UNIT=D-HP
adb -s $HU shell am force-stop $PKG; adb -s $HU shell run-as $PKG cat shared_prefs/settings.xml > $BASEXML
r_open D2-C $D2KEYS; listening || srv_restart D2-pre || echo "D2 UNTESTABLE: server"
L=$(nl); adb -s $HU shell am start -n $MAIN >/dev/null; up_session 120 $L || { srv_restart D2-pre2; adb -s $HU shell am force-stop $PKG; L=$(nl); adb -s $HU shell am start -n $MAIN >/dev/null; up_session 120 $L || echo "D2 UNTESTABLE: server wedged"; }
S=$(nl); p0=$(pcnt 'Critical error'); hand_save D2-1 hand-only
cue "H-SAVE-HP D2-1: press BACK on D-HP until the projection or the home screen is in front"
waitfor 120 'SSL handshake complete' ${SAVE_LN:-$S}; sleep 10; UP=$(listening && echo UP || echo DOWN); E=$(nl)
echo -e "D2-1\tt_ssl2=$(firstw 'SSL handshake complete')\tsettings_restart=$(cntw 0 3000 '(settings_restart)')\tdisc_before_ssl2=$(cntw 0 $(firstw 'SSL handshake complete' | sed 's/NA/120000/') 'AapService: Disconnected. Restarting discovery loop in 2s...')\tlistening_after=$UP\tcrit=$(( $(pcnt 'Critical error') - p0 ))\tfatal=$(cnt $S $E 'FATAL EXCEPTION')" | tee -a $OUT/D2-C.tsv
r_close D2-C
```

**PASS:** `t_ssl2` 120000 ms or less; `settings_restart` 1; `disc_before_ssl2` 0; `listening_after` UP; `fatal` 0. Phone: report `crit`; it is not graded (3.5). The BACK presses on D-HP are part of the hand step, because the screen there is reached by hand and its depth is not known to the script.

### Legacy Self Mode

**UNTESTABLE** unless R0 reads a Gearhead versionName below 17.4 on D-POCO or D-MOTO (3.6). If one does, record it in Setup notes and do not improvise a run; the next round will brief it.

### Closing step

Restore each unit's round backup of `settings.xml` (D-HU as root, `rig-quirks/units/D-HU.md`; D-POCO with `pocoput`; the tablets with `tab_put`), read each back, and record the diff, which must be empty. `separate-audio-streams` must read as it did at R0. Leave any helper that an earlier round installed, and say so. Leave D-POCO's head unit server in the state it was found, and say which. Re-enable D-MOTO's radios if Stage A left them off. Kill every capture and watcher: `ps aux | grep -c "[l]ogcat"` must print 0.

## 8. Do not re-run

- **The exit loop** (round 1 C3 and addendum R-C3x: 10 of 10 on K and X, `T_destroy` 275 to 348 ms against B's median 1234 ms). The PR's four new commits do not touch `AapTransport.quit()` or the exit path.
- **A user connect over a live session** (round 1 C6a, identical on all arms).
- **The starvation cap after early saves** (round 1 C1X: not written on K or B). C no longer counts a settings restart toward it (`noteSessionEnded(... settingsRestart = ...)`), and its JVM tests cover that.
- **The Settings-open raise refusal** (round 1 C1S, both arms PASS). N2 closes the screen, and U2 keeps it open on USB.
- **`ACTION_RESTART_AUDIO`** (round 1 C2): inert on both builds, and on C `restartAudio()` no longer ends a session.
- **The arbiter tiers and the USB budget** (`usb-version-retry` rounds 1 to 3); **the Nearby happy path** (`release-test` round 1).

## 9. Report back

1. **N1-C, the point:** per graded cycle, `poke_lt29`, `hold`, `refresh_30`, `t_poke`, `route` and `pokes_after_bt_on`, and the line `the wake comes back at Save + 30 s: yes or no`.
2. **The reconnects:** N2's `t_ssl2`, `t_video2` and `audio` per cycle; HS's `t_ssl2`, `t_ssl3` and `listening_after`; NB1's `t_ssl2`; D1 and D2's `t_ssl2`. And N1-B's shape, in one line.
3. **The findings and the old FAIL:** U2's `finding 2 reproduced` per cycle with the poke targets; HW's verdict per cycle; NB2's `fallback_30`; and N3's line `round 1 combo FAIL reproduces on C / on X: yes or no`.

### Results skeleton (copy into `pr-1047-session-reconnect-round2-results.md`)

```markdown
# session-reconnect, round 2 results

**Candidate C:** PR 1047 head b62d86c2 merged onto main 7102b428, tree <printed vs 96857f5658ea587eebcc4b97791937542d2b3bc7>   **Combo X tree:** <printed vs f1f58d5538eaa50a51665f7ea62a1771b648ef12, or not built>   **Baseline B:** main @ 7102b4283666ffcc7802e49402735e3958cd51a0
**APK md5:** B <md5> / C <md5> / X <md5>
**Units:** D-HU <API>, D-POCO <API, Gearhead>, D-MOTO <API, Gearhead>, D-SAM <API>, D-HP <API>
**Date:** <yyyy-mm-dd>
**Evidence:** release `rig-evidence-pr-1047-session-reconnect`, asset `pr-1047-session-reconnect-round2-captures.zip`, sha256 <hash>

## Setup notes
Deviations; scripts used and added; every hand step with its time (from `hand-steps.log`); the `open_audio` route and destination id per unit and arm; D-HU's `shared_prefs` stat; the rig audio keys and `separate-audio-streams` as found and as restored; network of each unit; void cycles and why; any string that did not match.

## R0 Gate
| Arm | md5 | SettingsRestartRecovery / AapMessageReassembler / outputPublicationLock | commit or tree | JVM count |
|---|---|---|---|---|

## A0
| Unit | Arm | destination id | route | screenshot |
|---|---|---|---|---|

## N1-B
| stop | destroy_ms | user_exit | ssl60 | poke60 | creds60 | main_closed | relaunch ok | crit | fatal |
|---|---|---|---|---|---|---|---|---|---|

## N1-C (the point)
| cycle | order | bt25 | bt50 | poke_lt29 | connect_lt29 | hold | leftover | refresh_30 | creds_30 | t_poke | settings_restart | link_lost | back | route | t_back | pokes_after_bt_on | crit | graded |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
The wake comes back at Save + 30 s: yes / no.

## N2
| cycle | t_ssl2 | t_video2 | audio | ssl_count | poke_before_ssl2 | settings_restart | link_lost | raise | crit |
|---|---|---|---|---|---|---|---|---|---|

## N3
| arm | run | covers torn down (3) | pre_rendered | t_ssl2 | video2 | dropped11 | crit |
|---|---|---|---|---|---|---|---|
Round 1 combo FAIL reproduces on C: yes / no. On X: yes / no / not built.

## HS
| cycle | t_conn | t_ssl2 | disc_before_ssl2 | listening_after | t_ssl3 | srv_lines | crit |
|---|---|---|---|---|---|---|---|

## NB0, NB1, NB2
One table each with the columns the run prints.

## HW
| cycle | groups_0_29 | groups_29_45 | t_disc | t_ssl | proxy | verdict |
|---|---|---|---|---|---|---|

## U1, U2
One table each with the columns the run prints, plus `path` per cycle. Finding 2 reproduced or fixed: per cycle.

## D1, D2
One row each.

## Verdict table
| Run | B | C | X | Notes |
|---|---|---|---|---|

## Anything the brief did not ask about
```

Evidence goes to a release asset (template section 7): zip the round folder, `sha256sum` it, and add it to the existing release `rig-evidence-pr-1047-session-reconnect` with `gh release upload`.

## Decisive strings

Unit lines are from `app/src/main` (and `contract/src` for `settings_restart`) of B, C and the X tree; section 5 marks the ones that exist on C and X only. `USB_DEVICE_DETACHED` and `USB_DEVICE_ATTACHED` are the intent actions our `USB Intent: ` line prints. `Critical error` and `Network server running on port` are Android Auto lines on the phone, and `FATAL EXCEPTION` is a system line; none is in our source. The `NearbyStrategy:` lines and `AA is now flowing through proxy` are from the helper at `8ac36c9`.

```decisive-strings
AutomationReceiver: 
AutomationMarker: 
CommManager: audio settings changed; reconnecting the projection session
Stop action received. Broadcasting finish request to activities.
MainActivity: Received finish request. Closing.
AapService destroying
AapService: session state 
settings_restart
link_lost
user_exit
AapService: automatic wake is paused by the session policy. Keeping listeners available.
NativeAA: the phone ended the last session itself
AapService: WiFi Direct credential refresh requested.
WifiDirectManager: SUCCESS - Providing credentials to listener.
AapService: Received WiFi credentials from manager
WifiLauncher: Initializing WiFi Mode: 
NativeAA: Attempting active poke to device
NativeAA: Calling socket.connect()
AapService: Native AA session ended; keeping the 
AapService: Disconnected. Restarting discovery loop in 2s...
AapService: USB disconnect. Scheduling reconnect check in 
AapService: userExitedAA is true. Skipping auto-poke.
AapService: raising the projection by 
AapService: Not raising the projection, the settings screen is open
AapProjectionActivity: onResume
SSL handshake complete
Throughput over 
Media Sink Setup Request: 
AudioDecoder.start: channel=
sessions in a row ended without a single video
UsbLauncher: USB auto-connect held while the settings screen is open; 
AapService: the settings screen closed with a USB auto-connect held 
USB Intent: 
USB_DEVICE_DETACHED
Found device already in accessory mode
Switching USB device to accessory mode
stopping the wireless stack for the duration of it
NetworkDiscovery: Starting scan...
AapService: ACTION_END_SESSION_STAY_ARMED received
NearbyManager: Stopping discovery and disconnecting from any active endpoint...
NearbyManager: Starting Nearby (Discoverer only)...
NearbyManager: Endpoint FOUND: 
NearbyManager: Auto-connect check: Enabled=
NearbyManager: Requesting connection to endpoint: 
AapService: Connecting to Nearby endpoint 
createGroup SUCCESS
New surface set:
Decoder stopped:
First frame rendered
AapVideo: Dropped Flag 11 packet
MATCH! Starting AapService
AapRead: Magic Garbage detected in header
FATAL EXCEPTION
Critical error
Network server running on port
NearbyStrategy: Advertising as
NearbyStrategy: Connected to
AA is now flowing through proxy
```
