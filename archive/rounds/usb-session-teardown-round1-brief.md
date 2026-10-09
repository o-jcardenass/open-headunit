# usb-session-teardown, round 1 brief: who closes the socket when a USB device moves

**Candidate:** `origin/3.3.0-beta1` @ `0ff9e620` (`andreknieriem/open-headunit`). **No baseline, no
fix on trial.** One APK for the whole round.

```bash
git fetch origin
git checkout -B 3.3.0-beta1 origin/3.3.0-beta1
git rev-parse HEAD          # must print 0ff9e620...
```

This is a **diagnosis round, not a validation round.** Nothing here tests a candidate fix. It exists
to attribute a fault whose author the reporter's own capture cannot name, and its output is an
attribution plus four numbers, not a PASS on a branch.

This is a **new thread.** The only other file to read is `self-mode-bt-audio-round1-brief.md` §0,
which carries the Self Mode protocol this round reuses verbatim. Do not read that brief's other
sections; they are a different fault.

---

## 0. This round runs on the phone, in Self Mode, and mostly over wireless ADB

Read `self-mode-bt-audio-round1-brief.md` §0 first and treat every rule in it as binding here:
Self Mode is loopback, so `TESTING-TEMPLATE.md` §4's clean-run protocol and all three discard rules
are inert; **nothing but the app may ever connect to port 5277**, because one connection that fails
the AAP version exchange wedges Android Auto's server permanently; an unclean session death wedges it
the same way, so end every run with `headunit://disconnect` and let it close before force-stopping;
and **Start head unit server** in Android Auto's developer settings is a manual pre-round step with
no log line for its absence.

Two things are new to this round.

**A USB cable cannot be used to drive the phone.** The USB-C port is the thing under test, so the
adb transport has to be wireless for R2 through R6. Set it up before anything else:

1. Developer options, **Wireless debugging**, on. Phone and PC on the same WiFi.
2. **Pair device with pairing code**, then `adb pair <ip>:<pair-port>` and enter the code.
3. `adb connect <ip>:<port>`, using the port on the **main** Wireless debugging screen. It is a
   different port from the pairing one, and the pairing port changes every time that dialog opens.
4. `adb devices` must show `<ip>:<port>  device`.

Wireless debugging drops when WiFi drops. If a capture ends early, run `adb devices` before believing
the log: a truncated capture from a dropped transport looks exactly like a session that died.

**This round needs two physical USB devices**, and which classes they are is load-bearing (§2).

---

## 1. Why this round exists

A user running Open Headunit 3.2.6 in Self Mode on a Xiaomi M2007J20CG (api 35) reports that
plugging or unplugging a USB Bluetooth adapter mid-session ends the session, and that a loose USB-C
port on a bike produced the same thing from vibration alone. He also recalls it once on a Wireless
Helper session, with the USB device on the same machine that was running Open Headunit. His question
is whether USB can interrupt a session it is not the transport for.

His capture `HUR_Log_20260824_193244_096.txt` establishes the correlation and cannot explain it.

**What is established.** Three Self Mode sessions over `127.0.0.1:5277`. The third is the clean case:

```
19:33:25.250  Throughput over 5001ms: rendered=281 (56fps), fed=281, dropped=0, concealed=0
19:33:25.299  UsbReceiver.onReceive | USB Intent: … USB_DEVICE_DETACHED
19:33:25.301  UsbReceiver.onReceive | USB Intent: … USB_DEVICE_DETACHED
19:33:25.773  AapMediaPlayback: status mediaSource='Spotify' … state=PAUSED
19:33:26.219  AapRead: Connection closed (EOF). Disconnecting.
```

918 ms from the detach to the death, on a link that was carrying 56 fps with `dropped=0` and no
framing errors of any kind. The Spotify PAUSED at 25.773 arrived **over the AAP link**, so the link
was alive 446 ms before it died. The device is VID 2578 (`0x0A12`, Cambridge Silicon Radio).

**What is not established, and why.**

- Open Headunit logged **nothing** between the detach and the death, and its detach handler is a
  no-op for this session by construction: `AapService.onUsbDetach` is guarded on
  `commManager.isConnectedToUsbDevice(device)`, which is two casts to `UsbAccessoryConnection` and
  `LibusbAccessoryConnection` (`CommManager.kt:234-236`), and the live connection was a
  `SocketAccessoryConnection`. The app also registers no `AUDIO_BECOMING_NOISY` receiver, no
  `AudioDeviceCallback`, no `ACL_DISCONNECTED` and no `android.hardware.usb.action.USB_STATE`
  receiver anywhere, so an audio-route change cannot reach our code at all.
- **The capture is filtered to Open Headunit's pid.** Every line in it is pid 4937. Android Auto's
  side is simply not in the file, which is the single reason the author cannot be named.
- **"EOF" in our log does not mean the peer sent FIN.** `SocketAccessoryConnection.recvBlocking`
  (`SocketAccessoryConnection.kt:63-83`) returns `-1` for *any* `IOException`, and
  `AapReadRecoveryPolicy` maps that to `DISCONNECT_EOF`. The line means "the read failed".
- **The second session does not fit the story at all.** It died 5.9 s **before** its USB attach
  broadcast, with the same silence around it. Either that adapter's enumeration lagged the physical
  insertion by seconds, or sessions on this phone die on their own. R1 exists to settle that, and
  nothing else in this round means anything until it does.

Three hypotheses are live, and the round is built to separate them:

| # | Author | Signature to look for |
|---|---|---|
| H1 | Android Auto tears down its own projection on a USB or audio-device change | Gearhead lines in the two seconds before the close (§5) |
| H2 | The framework destroys the socket because its bound `Network` went away | `SocketAccessoryConnection.kt:154` binds **every** outbound socket, loopback included, to a WiFi `Network`. A `NetworkAgent` loss or `destroySockets` for that netId in the window |
| H3 | Open Headunit ends it through the one unguarded USB path | `AapService.onUsbAccessoryDetach` (`:2142-2155`) disconnects on **any** `ACTION_USB_ACCESSORY_DETACHED` with no device identity and no transport check. `UsbReceiver` dispatches that action before it even extracts `EXTRA_DEVICE` (`UsbReceiver.kt:33-36`). It did not fire in the reporter's capture; R6 asks whether it ever fires on this hardware |

H3 is the one that would make the reporter's question a yes about our code rather than about Android
Auto's, and it is the only one whose fix is unambiguously ours.

---

## 2. What is different about this round

- **Two USB devices, and their classes are the measurement.** You need one device the phone
  enumerates as **USB audio class**, and one that is **not audio and not Bluetooth** (a plain flash
  drive, a keyboard, a USB-Ethernet dongle). R3 against R4 is the whole discriminator: if only the
  audio device kills the session, the trigger is the audio-route change, not USB. Name both devices
  and their VID:PID in Setup notes.
- **Establish what the reporter's adapter class actually is, for ours.** Capture `dumpsys usb` with
  each device in and out (§3). If the audio device does not enumerate as audio class on this phone,
  say so and report R3 and R4 as the same arm.
- **`log-level` must be `0`, VERBOSE, not the usual DEBUG.** The `RECV:` lines this round measures
  against are emitted at DEBUG priority but guarded by `AppLog.LOG_VERBOSE`
  (`AapMessageIncoming.kt:74-76`), so DEBUG produces none of them and the "was the link quiet before
  it closed" question becomes unanswerable. Check the guard, not the call.
- **Capture unfiltered, and keep Gearhead's process in it (§5).** A pid-filtered capture is exactly
  the limitation that made the reporter's log unattributable; reproducing it here wastes the round.
- **Note the wall-clock second at which you physically move each device.** That timestamp is the
  anchor for every measurement in §6, and it is the one piece no log records.
- **Grep every capture with `-a`** (`TESTING-TEMPLATE.md` §7a), without exception.

---

## 3. Setup and settings keys

| Key | Type | Value | Runs |
|---|---|---|---|
| `log-level` | int | `0` (VERBOSE) | all |
| `log-source` | int | `0` (LOGCAT) | all |
| `wifi-connection-mode` | int | `1` (Auto) | R1-R4, R6 |
| `wifi-connection-mode` | int | `2` (Wireless Helper) | R5 |

Ready to paste:

```
<int name="log-level" value="0" />
<int name="log-source" value="0" />
<int name="wifi-connection-mode" value="1" />
```

`log-source=0` is written explicitly rather than left to the default. It is `LOGCAT` on this branch
(`Settings.kt:178-180`), but a build variant that defaults it to `APPLOG_FILE` produces a **silently
empty** logcat capture, which has cost a round before.

Use `hur-wifi-test-scripts/set_pref.sh <key> <type> <value>` for the phone, per
`self-mode-bt-audio-round1-results.md`'s Setup notes. The head-unit scripts `set_hu_pref.sh` and
`set_hu_prefs.sh` are hardcoded to a rooted shell and will not work here.

USB device identification, once, before the runs:

```bash
adb -s <phone> shell dumpsys usb > usb-with-audio-device.txt      # audio dongle plugged
adb -s <phone> shell dumpsys usb > usb-with-other-device.txt      # non-audio device plugged
adb -s <phone> shell dumpsys usb > usb-empty.txt                  # nothing plugged
```

Report each device's VID:PID and its enumerated class from those three files.

---

## 4. Capture protocol, every run

```bash
adb -s <phone> shell am force-stop com.andrerinas.headunitrevived
adb -s <phone> logcat -c
stdbuf -oL adb -s <phone> logcat -v threadtime > rN.txt &     # started BEFORE the launch, always
```

`stdbuf -oL` is not optional. **No tag filter, no pid filter**: the lines that decide this round are
the ones that are not ours. Keep the app's own exported `HUR_Log_*.txt` alongside each `rN.txt`.

`-v threadtime` is required rather than `-v time`, because §5 and §6 both filter on the pid column,
which `-v time` does not print.

---

## 5. Capturing Android Auto's own logs

**This is the section that makes the round worth running.** Without Gearhead's lines, R2 can only
repeat what the reporter's capture already said.

### 5a. Raise Gearhead's log level before the round

Android Auto is a release build and most of its logging sits behind `Log.isLoggable(TAG, VERBOSE)`,
which is off by default and which `setprop` turns on. Do all three of these, then verify:

```bash
# 1. Android Auto's developer mode, by hand on the phone:
#    Android Auto settings -> scroll to Version -> tap it ~10 times -> "Developer settings" appears
#    In there, enable developer mode, and enable debug/verbose logging if the build offers it.
#    "Start head unit server" lives in this same menu and is needed anyway (see §0).

# 2. Raise the tag families Gearhead uses. Persistent across the round, cleared on reboot.
for t in GH GH.CarService GH.CarClientConnector GH.LifetimeManager GH.ProjectionLifecycle \
         GH.UsbMonitor GH.ConnectionController CAR.SERVICE CAR.USB CAR.PROJECTION; do
  adb -s <phone> shell setprop log.tag.$t VERBOSE
done

# 3. Restart Gearhead so it re-reads the properties.
adb -s <phone> shell am force-stop com.google.android.projection.gearhead
```

**Verify it took, before spending a run on it.** Start a Self Mode session, then:

```bash
adb -s <phone> shell pidof com.google.android.projection.gearhead
awk '$3=='<gh-pid>'' rN.txt | wc -l
```

If that count is zero or in the low single digits for a live projecting session, the property route
did not work on this build. Say so in Setup notes and fall back to 5c. **Do not report R2 as an
attribution if Gearhead's line count is zero**; report it INCONCLUSIVE with the count.

Gearhead runs in more than one process. `pidof` may print several pids; capture and filter on all of
them.

### 5b. Filter by pid, not by tag

Tag names are a guess; pids are exact. With `-v threadtime` the pid is field 3:

```bash
GH_PIDS=$(adb -s <phone> shell pidof com.google.android.projection.gearhead)
for p in $GH_PIDS; do awk -v p=$p '$3==p' rN.txt; done | sort -k1,2 > rN-gearhead.txt
```

Commit `rN-gearhead.txt` alongside `rN.txt` for every run that ends in a session death.

### 5c. Fallback, if 5a does not raise anything

`adb -s <phone> bugreport rN-bugreport.zip`, taken **within two minutes** of the session death so the
log buffer still holds the window. Its `bugreport-*.txt` carries the full system log with every pid,
at whatever level each process actually emitted, plus `dumpsys usb`, `dumpsys connectivity` and the
battery/USB history. It is bulkier and slower than 5a but it needs nothing enabled in advance.

Android Auto's developer menu may also offer **Save logs** or **Start bug report**, which writes to
the phone's storage. If the build has it, use it for R2 and pull the file; if it does not, say so.

---

## 6. Runs

Every run: Self Mode, Google Maps on screen with the map moving, per
`self-mode-bt-audio-round1-brief.md` §0 for bring-up.

### R0. Gate

Build and unit tests on `0ff9e620` (`build_hur.sh`, `run_unit_tests.sh`), install on the phone.
Record in Setup notes:

- phone manufacturer, model, Android version;
- `adb -s <phone> shell dumpsys package com.google.android.projection.gearhead | grep -m1 versionName`;
- `adb -s <phone> shell run-as com.andrerinas.headunitrevived stat shared_prefs`;
- the `settings.xml` delta after onboarding, which rewrites resolution, DPI and codec;
- both USB devices, VID:PID and enumerated class, from §3;
- whether §5a raised Gearhead's log level, with the verification line count.

**PASS:** build clean, unit tests clean, wireless adb stable, Gearhead line count non-zero on a live
session. A build or test failure stops the round.

### R1. Control: does a session survive on its own?

**Run this first and do not proceed past a FAIL.** No USB device anywhere near the phone for the
whole run. Start Self Mode, leave it projecting with the map moving for **three minutes**, touch
nothing, then stop the capture.

In the reporter's log the second session died at 9.0 s with no USB event within 5.9 s in either
direction. If sessions on this phone die on their own every ten to fifteen seconds, the correlation
the whole round rests on is an artefact.

- **PASS:** still alive at three minutes. Report the session lifetime as a number.
- **FAIL:** it dies. Report the lifetime, the `Connection closed (EOF)` timestamp and the §5b
  Gearhead slice, and **stop the round**. That result is more important than anything R2 onward
  could produce.

### R2. Detach a USB audio device mid-session

**The point of the round.** Audio device plugged in before starting. Start Self Mode, wait for
video, let it run **60 seconds**, then unplug it. Keep capturing for 30 seconds after.

The 60 s settle is deliberate: both broken sessions in the reporter's capture were under 15 s old,
and a young session dying is a different claim from a settled one dying.

- **PASS** means the session survived, which would put the reporter's fault on his hardware.
- **FAIL** means it died, which is the reproduction. Report all four §7 numbers and attribute to H1,
  H2 or H3 from the greps.

### R3. Detach the non-audio device mid-session

Identical to R2 with the non-audio device. **This is the discriminator.**

- Session dies here too: the trigger is the USB event itself.
- Session survives here but died in R2: the trigger is the audio-route change, and USB is incidental.
  That is a different fix and a different conversation with the reporter.

### R4. Attach the audio device mid-session

Nothing plugged. Start Self Mode, wait for video, let it run **60 seconds**, then plug the audio
device in. Keep capturing for 30 seconds after.

Also record the gap between the physical insertion and the first `USB Intent:` line. The reporter's
capture has an attach broadcast 5.9 s adrift of the session it supposedly explains, and whether
enumeration on this phone really lags that far is worth one number.

### R5. Wireless Helper, same device

Repeat R2 with `wifi-connection-mode=2` and a Wireless Helper session. **Name the
`helper-connection-strategy` value you used**, because it decides which transport class the session
is and therefore which of H1, H2 and H3 can even apply. If the strategy needs a phone-side broadcast
that Gearhead 17.4+ disabled, this run is **UNTESTABLE** and should be reported as such rather than
worked around.

### R6. Does `ACTION_USB_ACCESSORY_DETACHED` ever fire on this phone?

The probe for H3. This action fires when the phone is the USB **peripheral** and an accessory-mode
**host** goes away, not when a device the phone hosts is unplugged. Try, in this order, and stop at
the first that produces the broadcast: plug the phone into the head-unit rig with Open Headunit's own
AOA path in play; plug the phone into another Android device acting as an accessory host; plug it
into a car head unit if one is reachable.

```bash
grep -ac "USB_ACCESSORY_DETACHED"          rN.txt
grep -a  "USB Accessory detached"          rN.txt
```

- **Any hit at all** promotes H3 from theoretical to real on this hardware and jumps the fix queue,
  because that handler ends **any** live session, wireless included, with no transport check.
- **No hit after all three attempts:** report **UNTESTABLE**, not PASS. "We could not make it fire"
  is not "it cannot fire".

---

## 7. The four numbers, for every run that ends in a session death

Anchor on `T`, the timestamp of `AapRead: Connection closed (EOF). Disconnecting.`

```bash
# 1. Did Open Headunit end it? Every line we would print on a USB-driven teardown.
grep -anE "USB Intent:|Ignoring non-Android USB device|USB Accessory detached|Connection closed \(EOF\)|AapTransport quitting|Self Mode disconnected|Unclean WiFi disconnect" rN.txt

# 2. Did the accessory action fire? (H3)
grep -ac "USB_ACCESSORY_DETACHED" rN.txt

# 3. Who else was awake in the two seconds before T? (H1)
#    Use the pid slice from 5b first; the tag grep is a cross-check, not the evidence.
grep -anE "Gearhead|GH\.|CAR\.|UsbDeviceManager|UsbPortManager|UsbHostManager" rN.txt

# 4. Did the network under the socket go away? (H2)
grep -anE "ConnectivityService|NetworkAgent|netId|destroySockets|onLost|Switching default" rN.txt

# 5. Was the link quiet before it closed, or cut mid-stream? Needs log-level=0.
grep -a "RECV:" rN.txt | tail -40
```

| Measure | From | What it decides |
|---|---|---|
| `T` minus the USB broadcast timestamp | 1 | whether the USB event leads the death at all. The reporter's was 918 ms |
| `T` minus the last `RECV:` line | 5 | under ~200 ms means the peer cut a live stream; seconds means the link went quiet first and something else finished it |
| count from 2 | 2 | any non-zero result promotes H3 |
| any hit from 3 or 4 inside `[T-2s, T]` | 3, 4, 5b | names the author. A Gearhead teardown line is H1 and the fix is not ours. A `NetworkAgent` loss or `destroySockets` on the WiFi netId is H2 |

Also report, once, from any successful Self Mode session:

```bash
grep -a "Bound socket to network:" rN.txt
```

This should appear for the `127.0.0.1:5277` connection, which is H2's precondition: the loopback
socket is pinned to a WiFi `Network` it never uses.

---

## 8. Reporting

`usb-session-teardown-round1-results.md`, in `TESTING-TEMPLATE.md` §7's format. Two additions
specific to this round:

- **State the attribution explicitly**, as H1, H2, H3, or "not attributable, and why". That sentence
  is the round's output; the verdicts are supporting evidence for it.
- **Attach `rN-gearhead.txt` for every session death**, and the bugreport zip if §5c was used.

Give numbers, not adjectives: "918 ms", not "just after".
