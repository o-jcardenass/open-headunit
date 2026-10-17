# audio-sink-jitter, round 4 brief: the whole branch, on the only unit we have

## 1. Build and baseline

| | Branch | SHA | Commits | Gate |
|---|---|---|---|---|
| Candidate | `fix/audio-sink-and-wireless-bring-up` | `8bb8b844` | 4 on `main` | 2003 tests, 0 failures |
| Baseline | `main` | `5ce51c5e` | | |

```bash
git fetch fork
git checkout -B audio-sink fork/fix/audio-sink-and-wireless-bring-up   # 8bb8b844
git log --oneline -4
# 8bb8b844 Misc: the overlay, the USB reconnect, and a Share button that killed the app
# b400c436 Native AA: bring-up is gated and bounded, and old Android says what it cannot do
# 4910c710 Transport: video gets its own thread, and its ack is the flow control
# 076a2dd9 Audio: the sink is measured, sized by the dial, and keeps what it has
```

**Check the branch out fresh.** A round 3 checkout does not fast-forward to this. The branch was
compacted from six commits to four on 2026-09-16 and force-pushed, so every SHA round 3's brief
names is gone. Final-tree equality was the check, and all four commits compile alone.

Round 4 is **self-contained**. Do not read round 3's brief: it is queued against a SHA that no
longer exists and its Part P needs a second phone this round does not have.

**Baseline is needed for R0 only.** Everything else is candidate only.

## 2. What this round is, and what it is not

The rig for this round is **D-SAM as head unit and D-POCO as the phone, and nothing else**. D-HU and
D-MOTO are not available. That single fact decides the shape of the whole round.

D-SAM is a Samsung SM-T230 on Android 4.4.2, which is **API 19 on a 2.4 GHz-only radio**. Every API
gate in this branch falls the other way there. That cuts both ways, and both directions matter:

**Two changes on this branch were written for exactly this device and can only be graded here.**
`WifiBandCapability.bandUnreadable()` is `SDK_INT < 21`, so the narrow-band video cap now fires on a
unit that cannot report which band it is on, where before it silently never capped. And
`GroupIdentityStabilityPolicy`'s `appNamesGroup` is `SDK_INT >= 29`, so this unit cannot name its own
P2P group, the platform renames it every create, and the app is supposed to say so and withhold the
WPP endpoint rather than promise that the next create decides. N4 and N5 are the runs for those two,
and no other unit on the rig can produce them.

**The audio and transport halves run on a different code path here than anywhere they have been
graded.** `getUnderrunCount` and `getBufferCapacityInFrames` are API 24, `getBufferSizeInFrames` is
API 23, and `MediaCodec.Callback` is API 21. So on D-SAM the underrun count comes from a fallback
that watches the buffer run dry, `capacity=` is computed from the bytes asked for rather than read
back from the track, `effective=` falls back to the capacity, and AAC runs through `decodeSync`
rather than the async callback. **A PASS in Part A or Part T here is not coverage of the audio work**,
which rounds 1 and 2 graded on other hardware. It is coverage of the API 19 path, which has never
run on hardware on any build. Grade it as that and say so.

## 3. Rig constraints that will stop a run if missed

All of these are in `TESTING-TEMPLATE.md` section 7a and every one of them applies. They are
repeated here only because each has cost a previous round.

- **No `su`, and no `sed`, `awk`, `busybox` or `toybox`.** A settings change is a host-side `python3`
  edit of a copy of `shared_prefs/settings.xml`, a push, then `run-as $PKG cp`. Never
  `set_hu_pref.sh`, which assumes root and defaults to D-HU.
- **No root means no `iptables`**, so the link-gap lever rounds 2 and 3 used cannot run here. A4 says
  what to use instead.
- **The USB port is data-only and does not charge.** Charge from a separate supply before any run
  that measures the radio, or the round measures a browning-out unit at 9%.
- **Mount or hold it in native portrait with `screen-orientation=2`.** This is an installation
  constraint, not a preference.
- **Cap video at 720p, or read why it was capped for you.** At 1080p the link takes the socket down
  with `EPIPE` about 3.5 s in and never renders a frame. This candidate is expected to cap it without
  being asked, which is N5. Read `[RES_CAP] ... linkCapped=` before setting `resolutionId` by hand,
  and say in Setup notes which happened.
- **Build and install with `install_and_launch.sh` and `HU=30041c35642d2200`.**
- Inventory `hur-wifi-test-scripts/` before starting and use what fits.

## 4. Settings keys this round needs

```xml
<int name="log-level" value="0" />                              <!-- VERBOSE, every run -->
<int name="wifi-connection-mode" value="3" />                   <!-- Native AA, Part N -->
<boolean name="enable-audio-sink" value="true" />               <!-- read it back before run 1 -->
<boolean name="static-audio-focus" value="false" />
<int name="audio-latency-multiplier" value="16" />              <!-- 1, 2, 4, 8, 16; ABSENT means 16 -->
<int name="audio-queue-capacity" value="50" />
<boolean name="use-aac-audio" value="false" />
<int name="debug-video-feed-hold-ms" value="0" />               <!-- T2 only, max 250 -->
<int name="resolutionId" value="0" />                           <!-- see N5 before touching this -->
<boolean name="narrow-band-profile-cap" value="true" />         <!-- default true; N5 needs it on -->
<boolean name="show-fps-counter" value="false" />               <!-- M2 -->
<int name="overlay-position" value="0" />                       <!-- M2; 0 left, 1 right -->
<boolean name="video-profile-starvation-cap" value="false" />   <!-- read it back after every run -->
<boolean name="playback-focus-self-defeating" value="false" />  <!-- the same, and for the same reason -->
```

**Absent and 16 are different states**, and A1 depends on the difference: removing the key tests the
shipped default, writing 16 tests the same depth with the key present.

**The last two are latches, not preferences.** Read both back *after* every run as well as before
one. A round that finds either set part-way through has measured a different build state from that
point on and must say where.

**The rig's audio settings are a deliberate worst case** and are not to be reset permanently. A1
needs a fresh-prefs snapshot. Restore the worst-case set immediately after it and confirm the
restore in Setup notes.

## 5. The lines that decide every run

Copied from the source on `8bb8b844`, not from memory. Grep the fragment, not the whole line.

New on this candidate:

```
MainActivity: status pill step: hidden
NativeAA: the phone ended the session itself, so the listeners reopen without waking it.
NativeAA: the phone ended the last session itself
NativeAA: the session just ended, so the first poke waits Nms for the phone's WiFi to settle.
AapService: the wireless stack stops until it closes
AapService: the settings screen closed, re-arming wireless mode
WifiLauncher: wireless bring-up requested, but WiFi is not one of the chosen connection modes.
WifiDirectManager: still running, so this one is not started on top of it.
WifiDirectManager: the phone has not opened the Android Auto Bluetooth channel on this unit
This unit's Android is too old to report which band it is on
too old to name its own WiFi Direct group
LogExporter: could not share
AapProjectionActivity: reconnect is under way (
AapProjectionActivity: Reconnect gave up after
```

Carried from round 3 and still the lines that matter:

```
AapAudio: AUDIO is already set up, keeping the sink it has
AudioTrackWrapper: AUDIO underran, re-banking Nms (re-bank N)
AudioTrackWrapper: AUDIO keeps underrunning at Nms, banking Nms from here
AudioTrackWrapper: AUDIO can hold N frames (Nms) of the N bytes asked for, keeps Nms filled, banking Nms before playing and Nms after an underrun
AudioDecoder.start: channel=6, ..., latencyMultiplier=N, queueCapacity=N
audio sink AUDIO over 30000ms: underruns=N, silentCycles=N, rebanks=N, dropped=N, shed=N, depth=Nms, capacity=N frames, effective=N frames, target=N frames
transport dispatch over 30000ms: video=Nms, audio=Nms, other=Nms, unread=N%, blocks=N, longest=Nms on X, videoQueue=N, videoShed=N
AapTransport: the video thread is 256 messages behind, shedding
AapControlMedia.mediaSinkSetupRequest | Media Sink Setup Request: 2 on channel AUDIO
```

**`Handshake: SSL handshake complete` is DEBUG and absent at INFO.** Match on
`SSL handshake complete` without the prefix. At `log-level=0` both copies print, so two per session
is the two log levels and not two handshakes.

---

## 6. Runs

### R0. Gate

Build both, install the candidate, confirm identity before anything else. A release asset with the
same filename is not the same build, so fingerprint it rather than trusting the name.

**PASS:** `./gradlew :app:testGithubDebugUnitTest` reads 2003 tests, 0 failures, and the installed
APK's md5 matches the one just built.

---

## Part N: Native AA, the bring-up and the session end

D-SAM is the quiet host and D-POCO is the phone, `wifi-connection-mode=3` for every run.

**N1 is the run this round exists for.** It is a user-reported fault reproduced live on this exact
pairing on 2026-09-15 and fixed on this candidate.

### N1. The status pill ends with the session

**Candidate only.** Bring a session up and let it project. Then end it **from Android Auto's own UI
on D-POCO**, not from the head unit. That difference is the whole bug: a phone-initiated Bye-bye is
not a user exit, so it takes a different branch.

**PASS:** `status pill step: hidden` within a frame of `RECEIVED BYEBYE REQUEST`, then
`the phone ended the session itself, so the listeners reopen without waking it.`, then
`ACTIVELY LISTENING on Android Auto UUID`, and **no** `Attempting active poke` for as long as the
capture runs.

**FAIL:** the pill still reads `STARTING_PROJECTION` after the session ends, or a poke goes out
anyway.

**Then answer the open question, which is not part of the verdict.** Leave the capture running for
five minutes and record whether D-POCO comes back on its own, and how long it took. The fix stands
the wake down deliberately; whether an idle head unit ever gets the phone back without a button
press is unmeasured, and this is the first chance to measure it. If it does not return, press the
WiFi button on the home screen and confirm that does bring it back, because that is the documented
way out.

### N2. An unclean end still wakes the phone, after a settle

**Candidate only.** Bring a session up, then kill the link from the phone side: toggle D-POCO's WiFi
off and back on, or carry it out of range. No Bye-bye is sent, so this must take the other branch.

**PASS:** the pill goes hidden, then tracks the re-arm again (`WAITING_FOR_PHONE`, `WAKING_PHONE`),
and the first poke is about 5 s after the session ended rather than half a second, named by
`the session just ended, so the first poke waits Nms for the phone's WiFi to settle.`

**FAIL:** no settle line and a poke inside a second, or a pill that stays frozen.

**Report the measured gap** between the session-end line and `Attempting active poke`, as a number.
On 2026-09-15 that gap was 0.5 s and the phone answered `WifiConnectStatus status=WIFI_NETWORK_UNAVAILABLE(-11)`
with `no_bss_found` on its own side. **The 5 s is a guess.** If `-11` still appears, say so and give
the gap: the value needs raising and this run is the only evidence for what to.

### N3. The local Disconnect button, the control

**Candidate only.** `adb shell am start -a android.intent.action.VIEW -d "headunit://disconnect"`.
**Drop the `-p` flag**: 4.4.2's `am` rejects it with a `NullPointerException`.

**PASS:** `status pill step: hidden`, `Native AA user exit. Stopping active launcher.`, and the
launcher stopped. This path was already correct before the fix and must stay correct.

### N4. The group name this unit cannot control

**Candidate only.** Three Native AA bring-ups in a row, reading the group name off
`WifiDirectManager: group identity ssid=` each time. This unit renames the group on every create and
cannot do otherwise below API 29.

**PASS:** after the third differing name the identity assessment reads `RENAMED`, and the WPP
endpoint is withheld with a reason naming the platform, matching
`too old to name its own WiFi Direct group`. The session still forms over Bluetooth.

**FAIL:** the endpoint is offered anyway, or the app keeps reporting the identity as unproven
forever.

**This matters beyond the log line.** Forcing the TCP endpoint out on a unit whose address moves has
been measured to brick the phone side into a `NETWORK_NOT_FOUND` loop with no Bluetooth fallback, so
the withhold is load-bearing. Report `persistent=` and `stable=` off the identity line.

### N5. The video cap on a unit that cannot read its own band

**Candidate only**, `narrow-band-profile-cap=true`, and **`resolutionId` left alone**. Bring one
session up and let it render.

**PASS:** `[RES_CAP] ... linkCapped=` fires without anyone setting a resolution, the reason text
matches `This unit's Android is too old to report which band it is on`, and the session renders
instead of dying with `EPIPE` about 3.5 s in.

**FAIL:** no cap line and the 1080p death this unit has shown in four previous rounds, or a cap
whose reason names a band this unit cannot read.

**Report whether the picture survived**, plainly. This is the run that decides whether the manual
720p workaround in section 3 can be retired for this class of device.

### N6. The settings screen holds the stack down

**Candidate only.** With the mode armed and no session, open the settings screen and leave it open
for a minute, then close it.

**PASS:** `the wireless stack stops until it closes` while it is
open, no group create and no poke in that window, then `the settings screen closed, re-arming
wireless mode` about 1.5 s after it closes.

### N7. Two bring-ups cannot race

**Candidate only.** Trigger a second bring-up within about a second of the first, by sending
`ACTION_START_WIRELESS_SCAN` twice in quick succession.

**PASS:** `still running, so this one is not started on top of it.` and exactly one
`createGroup SUCCESS`. Count with `grep -c "createGroup SUCCESS"`.

**FAIL:** two creates, which on `main` is how the loser removes the winner's live group.

### N8. The watchdog holds for a phone that never dialled

**Candidate only.** Arm the mode with D-POCO's Bluetooth **off**, so the phone never opens the
Android Auto channel and is never handed credentials. Leave it for two minutes.

**PASS:** `the phone has not opened the Android Auto Bluetooth channel on this unit` and the group
left up, with no recreate. Count creates as in N7.

### N9. Wireless does not arm on a cable-only unit

**Candidate only.** Write `connection-modes` without WiFi, with the app stopped. Launch.

**PASS:** `wireless bring-up requested, but WiFi is not one of the chosen connection modes.`, no
group, no poke, and the WiFi button on the home screen still works and still brings the stack up.
**Restore `connection-modes` afterwards** and confirm the restore in Setup notes.

---

## Part A: the audio sink on API 19

Read section 2 first. Everything here grades the fallback path, not the path rounds 1 and 2 graded.
Media playing throughout every run, with a person listening, marking what they hear.

### A1. The shipped default, out of the box

**Candidate only**, fresh prefs with `audio-latency-multiplier` **absent** and `use-aac-audio=false`.
One session, about five minutes.

**PASS:** the `audio sink AUDIO` window line prints at all, `capacity=` and `target=` are real
figures rather than 0 or 1, `target=` reads 400 ms, and `AudioDecoder.start` carries
`latencyMultiplier=16`.

**FAIL:** no `audio sink` line, `capacity=0`, or `target=1`. Any of those means the instrument this
whole thread rests on reports nothing on an old unit, which matters well beyond this round.

Report every window in full, plus the `can hold N frames` construction line and
`playback started with N frames banked`. **Report `underruns` against what was heard.** The API 19
fallback is a different mechanism from the framework counter, so audible breaks with `underruns=0`
is a finding about the fallback, not about the sink.

**Restore the rig's worst-case audio settings immediately after this run.**

### A2. The dial

**Candidate only.** Three short sessions at `audio-latency-multiplier` 2, then 8, then 16.

**PASS:** `target=`, `capacity=` and `effective=` all rise at each step, and the listener hears fewer
breaks at 16 than at 2.

Report the three figures as a table, one row per setting. On API 19 `capacity=` is computed from the
bytes asked for rather than read back from the track, so say that beside the numbers.

### A3. A re-setup leaves a live sink alone

**Candidate only.** Get the phone to re-send Media Sink Setup mid-session, which a navigation
guidance state reliably does.

**PASS:** `Media Sink Setup Request: 2 on channel AUDIO` is followed by
`AUDIO is already set up, keeping the sink it has`, by **no** new `AudioDecoder.start: channel=6`,
and `capacity=` on the next sink line is the same number as on the previous one.

**FAIL:** a fresh `AudioDecoder.start` and a different `capacity=`, which is the defect this fix
exists for: the rebuilt track came back 400 ms where the first had been 698 ms.

### A4. The sink deepens its own cushion

**Candidate only**, `audio-latency-multiplier=2`. Four link gaps about 10 s apart, so four underruns
fall inside one minute.

**The lever rounds 2 and 3 used needs root and D-SAM has none.** Use the phone instead: toggle
D-POCO's WiFi off for about two seconds and back on, four times. That also tears the association
down, which an `iptables` drop did not, so **confirm real gaps in `RECV: AUDIO Media Data` arrival
timestamps before grading**, and note in Setup notes that the lever differs from rounds 2 and 3.

**PASS:** `keeps underrunning at Nms, banking Nms from here` after the third re-bank, the new figure
100 ms above the old, `target=` on the next window carrying it, and never above 400 ms however many
gaps are fired.

**INCONCLUSIVE, not FAIL,** if the substitute lever does not produce four clean underruns. Say so.

### A5. Overflow sheds the oldest

**Candidate only.** Under whatever load A4 produced, look for
`Audio queue is full at N chunks, shedding the oldest to keep the sink current`.

**PASS:** if it fires at all, `dropped=` rises on the sink line and the sink stays current rather
than falling behind. Absent is not a FAIL; report it as not reached.

### A6. AAC on the synchronous path

**Candidate only**, `use-aac-audio=true`. One session, about five minutes.

**PASS:** audio plays. This is the first time AAC has run through `decodeSync` on hardware, because
`MediaCodec.Callback` is API 21 and the async path this thread spent two rounds on is not the code
that runs here. Report `the AAC sink cannot keep up, shedding decoded frames` if it appears, and the
`AAC frames dropped before decode, N after` summary at thread end either way.

---

## Part T: the transport

### T1. The dispatch line on a healthy session

**Candidate only.** Any session from Part N or Part A, read the
`transport dispatch over 30000ms` windows.

**PASS:** `blocks=0` in every window after the first, `unread=` low, `videoQueue` in the low tens or
below, `videoShed=0`.

### T2. The backlog stays bounded with the video feed held

**Candidate only**, `debug-video-feed-hold-ms=200`, media playing, four minutes.

**PASS:** `videoQueue` settles rather than climbing, `videoShed=0` in every window, `blocks=0` after
the first window, the `audio sink` line clean in the same windows, and `Audio queue is full` at zero.

**FAIL:** `videoQueue` climbing window after window as it did in round 2, where it reached 5602 at
64 KiB a message, or the audio line degrading with it.

**Report the peak `videoQueue` against round 2's 5602**, and report the throughput line beside it so
a `videoQueue` of zero because the hold never took effect can be told from one bounded by the fix.
**Say what the picture looked like.** The ack waits for the decode now, so the phone throttles its
video and a slower but whole picture is the intended outcome. If it breaks up or washes out instead,
that matters more than the queue depth and should be reported first.

---

## Part M: the rest

### M1. The Share button that killed the app

**Candidate only, and this is the second run written for this device.** Export a log from the app,
then press Share.

**PASS:** the app does not force-close, and either the share sheet opens or a toast names the file
path with `LogExporter: could not share` in the log.

**FAIL:** a force-close, which is what `main` does below API 29 because the provider did not declare
the Downloads path.

**Answer the open question in `TESTING-TEMPLATE.md` section 7a while here:** report the actual path
the export lands on, so the `/storage/sdcard0` against `/storage/emulated/0` question stops being
carried as unverified.

### M2. The performance overlay and its position

**Candidate only.** `show-fps-counter=true`, then `overlay-position=0`, then `1`.

**PASS:** the overlay draws, and moves from left to right with the key. Then run a settings backup
and restore and confirm `overlay-position` survives the round trip.

Note the setting is labelled "Show Performance Overlay" now, not "Show FPS Counter"; the key is
unchanged for compatibility.

### M3. The reconnect grace

**Candidate only.** Kill the link mid-session as in N2 and watch the reconnecting overlay.

**PASS:** `reconnect is under way (...), holding the overlay` while there are signs of life, then
`Reconnect gave up after Ns` naming one of `BASE_EXPIRED`, `PROGRESS_WENT_STALE` or
`CEILING_REACHED`. Report which fired and the number of seconds.

---

## 7. Pre-registered UNTESTABLE on this rig

Recorded here so the runner marks them rather than discovering them, and so a later round knows they
were skipped by design rather than missed.

- **Anything needing 5 GHz or a cross-band split.** D-SAM's radio is 2.4 GHz only, so the station
  stand-down's AUTO and ALWAYS coverage cannot be exercised at all this round.
- **The positive `wifi-direct-stable-identity` case.** It has no code path below API 29. N4 grades
  the negative case, which is the only one this unit can reach.
- **`deletePersistentGroup`**, gated from Android 11.
- **The USB bus-composition rework.** D-SAM's port is data-only and there is no second host on the
  rig this round.
- **Part P of round 3**, phone-to-phone with D-POCO as head unit, because D-MOTO is not available.

## 8. Report back

`audio-sink-jitter-round4-results.md`, format in `TESTING-TEMPLATE.md` section 7, one section per run
id above.

Three things this round wants named explicitly in the results whatever the verdicts are:

1. **Whether D-POCO returns on its own after N1**, with the number of seconds or a plain "it did
   not". The fix stands the wake down on purpose and nobody has measured what that costs.
2. **The measured settle gap in N2**, and whether `-11` still appears. The 5 s is a guess and this is
   the only evidence for changing it.
3. **Whether N5 removed the need for the manual 720p cap**, so section 3's standing instruction can
   be retired for this class of device or kept.
