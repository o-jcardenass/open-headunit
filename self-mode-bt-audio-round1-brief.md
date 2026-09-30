# self-mode-bt-audio, round 1 brief: does anything we do stop media reaching a Bluetooth device

**Candidate:** `fix/session-lifecycle-and-video-concealment` @ `ada271e7` on `fork`
(`o-jcardenass/open-headunit`). **No baseline.** One APK for the whole round.

```bash
git fetch fork --tags
git checkout -B fix/session-lifecycle-and-video-concealment fork/fix/session-lifecycle-and-video-concealment
git rev-parse HEAD          # must print ada271e7...
git log --oneline -5
# ada271e7 Video: hold the last good frame instead of melting it after a lost access unit
# 98613903 Video: split the watchdog clocks the concealment window will race
# 504a2b1a Logs: say which build produced a capture, and when SSL unwraps nothing
# 3099a268 Video: return the input buffer when the feed throws
# 9c1c50f2 Video: a corrupt access unit reaches the focus cycle, at every budget position, all drive long
```

This is a **new thread** and there is nothing to re-read. It shares a branch with the
`render-side-concealment` and `wire-corruption-escalation` threads, but nothing this round measures is
touched by their commits, and none of their runs need repeating here.

## 0. This round runs on the phone, not the head unit

Read this section before planning anything. This is the channel's first **Self Mode** round and most
of `TESTING-TEMPLATE.md`'s standing protocol does not apply to it.

Self Mode means Open Headunit and Android Auto are **the same device**. The app connects to Android
Auto's own head unit server over loopback:

```
AapService.kt:3198  "SelfMode: AA 17.4+ detected. Connecting directly to Headunit Server on 127.0.0.1:5277..."
AapService.kt:3200   commManager.connect("127.0.0.1", 5277)
```

Consequences, all of them load-bearing:

- **Every `adb` line in this round is `-s <phone>`.** The head unit's only possible role is as the
  Bluetooth audio sink, and only if that is the sink you use (see §2).
- **§4's clean-run protocol does not apply.** There is no P2P group, no credentials, no airplane-mode
  sequence. Bring-up is: install, write settings with the app stopped, launch, fire
  `ACTION_START_SELF_MODE`.
- **The discard rules do not apply either.** `createGroup SUCCESS`, `p2p-wlan0-N` and the second-SSL
  rule are all about the wireless transport and are inert on loopback. Do not report an arm as
  unverifiable for their absence. The one contamination signal that still counts is an unintended
  reconnect inside an arm; say so if you see one.
- **Never send anything to port 5277 that is not the app.** Any connection Android Auto's server
  accepts that then fails to complete the AAP version exchange wedges the server **permanently**,
  including a `nc <ip> 5277 </dev/null` that connects and closes on EOF. Once wedged, every later
  connection is accepted, never served and never closed. No `nc`, no `nmap`, no `curl`, no port scan,
  for any reason, at any point in this round.
- **An unclean session death wedges it too.** Between arms, end the session with
  `headunit://disconnect` and let it close, then force-stop. Do not kill the app mid-session.
- **Recovery from a wedged server is manual and silent**: stop and start "Start head unit server" in
  Android Auto's developer settings on the phone. There is no log line for it, so if an arm's session
  will not form, suspect this before suspecting the branch.

**One setup step cannot be scripted.** Android Auto developer settings → **Start head unit server**,
on the phone, by hand, before the round. If it is not running, the log says:

```
SelfMode: Headunit Server (127.0.0.1:5277) is NOT running.
```

and the app raises a toast pointing at the same setting.

## 1. Why this round exists

Issue #874. A reporter runs Open Headunit 3.2.6 in Self Mode on a single Galaxy S20 FE with Android
Auto 17.5. Media plays through the phone's speaker, and goes silent the moment **any** Bluetooth
audio device connects, whether earbuds, a headset or his car's Bluetooth. He has tried every
combination of the app's audio settings, and reports the same on older versions of the app.

His two captures (one with Audio Sink off, one with it on) establish three things:

1. **No audio ever crosses the link, in either capture.** The only `Media Sink Setup Request` lines
   are `3 on channel VIDEO` and `1 on channel AUDIO2`, and the only `Media Start Request` is for
   VIDEO. The app decodes and plays zero audio in both.
2. **That is by design in Self Mode.** `ServiceDiscoveryResponse.kt:159-191` puts the media (`ID_AUD`)
   and speech (`ID_AU1`) sinks behind `settings.enableAudioSink` **and** `!AapService.selfMode`, so in
   Self Mode neither is ever announced and the Audio Sink switch changes nothing on the wire. Android
   Auto therefore keeps playback on the phone's own output. `ID_AU2` (system sounds) is announced
   unconditionally, even with the sink off.
3. **The app makes no audio-routing call in this configuration.** No `setMode`, no
   `setSpeakerphoneOn`, no `startBluetoothSco`, no `setCommunicationDevice`, no `setPreferredDevice`,
   no `MediaProjection` and no `AudioPolicy` anywhere in `app/src`. `MicRecorder`'s SCO path is the
   only real routing manipulation and it runs only when `mic-input-source` selects Bluetooth SCO;
   both captures show `MicRecorder: Stopping. Active: false`.

**The reproduction has already been attempted by hand, twice, and failed both times.** On two phones,
a personal device and a Galaxy S24+, Self Mode with a Bluetooth audio device connected kept playing
over Bluetooth. The **only** way to produce the reporter's silence was unchecking **Media audio**
for that device in the phone's own Bluetooth settings. That is Android's per-device A2DP
switch, a manual per-device toggle, which fixes the shape of the fault: the stream is not going to a
wrong device, A2DP media is not carrying it at all.

The app cannot be what turns that off. Repo-wide there is no `setConnectionPolicy`,
`getProfileProxy`, `BluetoothA2dp`, `BluetoothHeadset`, `setActiveDevice`, `createBond` or adapter
enable/disable in `app/src/main`. The only Bluetooth profile calls are two **read-only**
`getProfileConnectionState` probes, `BluetoothHelper.kt:130` and `:189`.

**So this round is not "does it reproduce".** It is two things:

- turn those two hand results into one logged, quotable measurement we can put in front of a reporter;
- add the arm neither hand test isolated: **the app's service running with no projection session at
  all**, which still calls `enableCarMode(0)` and still holds an active `MediaSession`.

## 2. What is different about this round

- **The Bluetooth sink is yours to choose, and you must name it.** Either real Bluetooth earbuds
  paired to the phone, or the head unit itself as an A2DP sink (`A2dpSinkService`) if that link is
  up. State which in Setup notes and use the same one for all four arms. Do not switch sinks
  mid-round.
- **The A2DP link is unreliable on this rig by record.** `TESTING-TEMPLATE.md` §7a: three rounds,
  three behaviours, once refusing to come up for fifteen minutes across every technique tried.
  **Confirm the link immediately before arm A and do not spend the round forcing it.** If arm A cannot get media
  onto Bluetooth, the whole run is **INCONCLUSIVE**: rig state, not a finding about the branch.
- **This build's session banner does not name the audio sink setting.** `LogExporter.sessionBanner`
  reports build, device, video, wifi and log level, and nothing about audio. Prove which arm you are
  in from the log lines in §4 instead, and from a `settings.xml` read.
- **`shared_prefs` ownership is unknown on the phone.** It is root-owned on the head unit, which makes
  the app's own writes silently never reach disk. `stat` the directory on the phone at the start and
  report what you find; if it is root-owned, `chown` it to the app's uid:gid before the round.
- **A fresh install re-runs onboarding** and rewrites resolution, DPI and codec. Diff `settings.xml`
  against a fresh backup after installing and state the delta, including what onboarding chose for
  `enable-audio-sink`.
- **Grep every capture with `-a`** (§7a), without exception.

## 3. Settings keys

| Key | Type | Value | Arms |
|---|---|---|---|
| `enable-audio-sink` | boolean | `false` | A, B, C |
| `enable-audio-sink` | boolean | `true` | D |
| `log-level` | int | `1` (DEBUG) | all |

`log-level=1` is deliberate and sufficient. Nothing this round depends on is guarded by
`AppLog.LOG_VERBOSE`, and DEBUG is the level the reporter's own captures were taken at, so ours are
directly comparable to his.

Elements, ready to paste:

```
<boolean name="enable-audio-sink" value="false" />
<boolean name="enable-audio-sink" value="true" />
<int name="log-level" value="1" />
```

Use `hur-wifi-test-scripts/set_pref.sh <key> <type> <value>` rather than an inline `sh -c`, per §7a.

## 4. The lines that decide the arms

All verified with `grep -F` against `ada271e7`.

Which arm you are in:

```
Audio sink is off in Settings. Skipping the media and speech audio    <- arms A/B/C, sink off
Audio Sink disabled - skipping permanent audio focus request.         <- arms A/B/C
Audio Sink disabled - skipping system audio focus request for channel <- arms A/B/C
Audio Focus Request:                                                  <- arm D, the sink-on path
Sending immediate AudioFocusNotification
```

`Audio sink is off in Settings` exists **only on this branch**. It is not in `main` and not
in `v.3.2.6`, which is why this branch is the candidate rather than `main`. Everything else in this list
is byte-identical to `v.3.2.6`: `git diff v.3.2.6 ada271e7` on `ServiceDiscoveryResponse.kt` is that
one line plus a comment rewrite, and `AapControl.kt`, `AapAudio.kt`, `AudioDecoder.kt` and
`MicRecorder.kt` are unchanged. **What this round measures is the reporter's own 3.2.6 behaviour.**

What the wire carries:

```
Media Sink Setup Request:            <- expect VIDEO and AUDIO2 only, in every arm
Media Start Request                  <- expect VIDEO only, in every arm
BT MAC Address is empty. Skip bluetooth service
SelfMode: AA 17.4+ detected
```

Counting them:

```bash
grep -a "Media Sink Setup Request:" cN.txt
grep -a "Media Start Request"       cN.txt
grep -ac "Audio sink is off in Settings" cN.txt
grep -a  "Audio Focus Request:"     cN.txt
```

## 5. Runs

### R0 - gate

Build and unit tests on `ada271e7` (`build_hur.sh`, `run_unit_tests.sh`), then install on the phone.
Record, all of it in Setup notes:

- the phone: manufacturer, model, Android version;
- Android Auto's version: `adb -s <phone> shell dumpsys package com.google.android.projection.gearhead | grep -m1 versionName`, which must read 17.5.x;
- `adb -s <phone> shell run-as com.andrerinas.headunitrevived stat shared_prefs`;
- the `settings.xml` delta after onboarding;
- the Bluetooth sink you will use, and its address.

**PASS:** build clean, unit tests clean, Android Auto reads 17.5.x. A failure here stops the round.

### R1 - four arms on one live A2DP link

**The point of the round.** One Bluetooth link, one media stream playing throughout, four arms back
to back. §7a is explicit that no technique reliably brings this link up, so once it is up, sequence
the arms without touching the phone's radios and without re-pairing.

Start the media once, in arm A, and leave it playing. Media keys drive it:

```bash
adb -s <phone> shell input keyevent KEYCODE_MEDIA_PLAY_PAUSE
```

| Arm | State | What it exercises |
|---|---|---|
| **A** | App force-stopped, Android Auto not projecting | The control. If media is not on Bluetooth here, the run is INCONCLUSIVE and nothing after it means anything |
| **B** | `MainActivity` launched, Self Mode **not** started | `enableCarMode(0)` and the always-active `MediaSession`, with no AAP session at all. The arm neither hand test isolated |
| **C** | Self Mode projecting, `enable-audio-sink=false` | The reporter's exact configuration |
| **D** | Self Mode projecting, `enable-audio-sink=true` | That the switch is disconnected in Self Mode |

Arm commands:

```bash
PKG=com.andrerinas.headunitrevived
MAIN=$PKG/com.andrerinas.openheadunit.main.MainActivity

# A - control
adb -s <phone> shell am force-stop $PKG

# B - service up, no session. Do NOT fire ACTION_START_SELF_MODE.
adb -s <phone> shell am start -n $MAIN

# C - projecting, sink off  (settings already written, app stopped, then:)
adb -s <phone> shell am start -n $MAIN
adb -s <phone> shell am start -a com.andrerinas.openheadunit.ACTION_START_SELF_MODE

# between arms, always:
adb -s <phone> shell am start -a android.intent.action.VIEW -d "headunit://disconnect"
sleep 3
adb -s <phone> shell am force-stop $PKG
```

**Four instruments, captured identically in every arm** so the arms are directly comparable. Dump the
first two whole to per-arm files and quote the deciding lines out of them; the section names vary by
vendor and you are better placed to find them than this brief is.

```bash
adb -s <phone> shell dumpsys bluetooth_manager > armX-bt.txt      # A2DP state for the sink, and any per-device policy
adb -s <phone> shell dumpsys audio             > armX-audio.txt   # routing
adb -s <phone> shell dumpsys media.audio_flinger > armX-flinger.txt  # which device carries the active track
adb -s <phone> shell dumpsys uimode                               # car mode, and in which arms
```

Plus one thing that is not a dumpsys: **read the per-device Media audio state** for the sink in every
arm, and **never change it**. Turning it off is already known to reproduce the symptom, so flipping it
proves nothing. What the round needs to know is whether anything else moves it.

**PASS:** media stays on Bluetooth in all four arms, matching both hand tests, **and** the announced
service set is unchanged between C and D: `Media Sink Setup Request` for VIDEO and AUDIO2 only, no
AUDIO or AUDIO1, and no audio `Media Start Request` anywhere in the round.

**FAIL:** media leaves Bluetooth in B, C or D. That is the reproduction the hand tests missed, and the
arm it first appears in names the cause. B means the app's own system-wide side effects, C or D
means the projection session.

**INCONCLUSIVE:** arm A cannot get media onto Bluetooth, or the link drops mid-round. Say which, and
at which arm.

**A PASS here has to be readable as a real one.** C and D both pass on a wire that carries no audio
either way, so pair every count with the measurement that proves the arm was reachable: the announced
service list from the log, and a live active track on a **named** device from the flinger dump. An arm
with no active track anywhere is a dead media app, not a routing result.

### R2 - car-mode probe, **only if B, C or D failed**

Do not run this on a passing round; there is nothing for it to explain.

With the failing arm's state live:

```bash
adb -s <phone> shell dumpsys uimode
adb -s <phone> shell cmd uimode car no
adb -s <phone> shell dumpsys uimode
```

then re-capture all four instruments. **PASS/FAIL:** whether leaving car mode restores media to
Bluetooth. If the shell refuses to clear car mode, record **INCONCLUSIVE**, an outcome pre-registered
here, and the coverage then moves to a build with the `enableCarMode` call gated
behind a setting.

## 6. Do not re-run

- Anything from the `render-side-concealment` or `wire-corruption-escalation` threads. They share this
  branch and are already answered.
- Any attempt to reproduce the reporter's silence by turning **Media audio** off. That is already
  measured on two phones and is the round's premise, not a question.
- Any wireless-transport setup. This round never leaves loopback.

## 7. Report back

Four answers, and the round is decided by the first two:

1. The device carrying the active media track in arms **A, B, C and D**, side by side.
2. The per-device **Media audio** state in each arm, and whether anything moved it.
3. Whether an audio `Media Start Request` ever appeared. Expected: never.
4. `dumpsys uimode` per arm: whether the app puts this phone in car mode, and from which arm on.

If the answer to 1 is "Bluetooth, in all four", say so plainly. That is the result this round is for,
and it is what goes back to the reporter.
