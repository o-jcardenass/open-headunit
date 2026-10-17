# Round 6 — brief. `fix/audio-focus-pauses-bt-source`

**A different branch from rounds 1-5.** This is not the hotspot work; it shares only this transfer
channel. Nothing here touches the hotspot, WiFi Direct or the handshake.

**Build:** `fix/audio-focus-pauses-bt-source` @ `c0f3ec12` on `o-jcardenass/open-headunit`.
**Baseline:** `origin/main` @ `c318b4e4`. Three commits over it, plain, no rewrite.

```bash
git fetch fork && git checkout -B fix/audio-focus-pauses-bt-source fork/fix/audio-focus-pauses-bt-source
git log --oneline -4          # c0f3ec12, 76488d94, 3cab504c, c318b4e4
```

`TESTING-TEMPLATE.md` carries the standing method — capture rules, the settings write template, the
automation surface, install discipline, verdicts and the results format. Everything below is
specific to this round.

**Run the whole round without checking in.** Every run's setup is a paste-able command block, and
every run's gate resolves from evidence you can collect yourself. A0 failing is the one stop
condition; A1 negative and A5 unreachable are answers the brief already anticipates, not questions —
mark them INCONCLUSIVE / UNTESTABLE and carry on. Escalate only per `TESTING-TEMPLATE.md` §3a.

---

## What this is and why it exists

Issue #744: a reporter's music cuts out every few seconds whenever Bluetooth is on, on 3.2.x but
never on 3.1.1. Issue #681 is the same defect, reported two weeks earlier and closed as an Android
Auto problem. It is ours.

`1cbe8495` (in `v.3.2.0-beta1`) made the app take `AUDIOFOCUS_GAIN_TRANSIENT` whenever Android Auto
opens an audio channel, so a local player such as a car radio pauses while AA plays. On a head unit
that is **also the phone's Bluetooth A2DP sink**, that backfires: AOSP's `A2dpSinkStreamHandler`
answers `AUDIOFOCUS_LOSS_TRANSIENT` by sending an AVRCP passthrough PAUSE to the source device —
which is the same phone streaming Android Auto to us. We pause the music we are playing.

The reporter's log, twice in ten seconds:

```
10:35:37.794  AapAudio: AA audio started (AUDIO) - acquiring transient system audio focus
10:35:38.462  AapMediaPlayback: status mediaSource='TIDAL' ... state=PAUSED     <- +661 ms
10:35:41.865  AapControlMedia: Media Sink Stop Request: AUDIO                   <- AA gives up
10:35:41.868  AapAudio: Releasing playback transient audio focus
10:35:43.727  AapMediaPlayback: status ... state=PLAYING                        <- phone resumes
10:35:43.844  AapAudio: ... acquiring transient system audio focus
10:35:43.903  AapMediaPlayback: status ... state=PAUSED                         <- +59 ms
```

3.1.1 never called `requestAudioFocus` at all on that phone, which is why it worked.

**The fix.** `PlaybackFocusPolicy` (pure, 13 JVM tests) decides whether to acquire, from the
pre-existing `staticAudioFocus` / `enableAudioSink` gates, a Bluetooth media-link probe
(`BluetoothHelper.isA2dpMediaLinkActive` — `getProfileConnectionState` for `A2DP` and the hidden
`A2DP_SINK` = 11), and a runtime latch. A new Settings → Audio control, **Pause Other Audio During
Playback**, offers Automatic / Always / Never; Automatic is the default. Always is the old 3.2.x
behaviour, kept because issue #658 asked for it on a unit with an FM radio.

---

## What is different about this round

**A1 gates A2 and A4a, and may end the round early.** The defect needs the rig to be an A2DP
**sink** — the phone's audio playing out of the head unit's speakers over Bluetooth. Many Android
devices are sources only and can never be sinks. If this rig is not one, A2 and A4a are
**INCONCLUSIVE on this hardware**, and that is a result. The decision is pinned by JVM tests for
exactly this reason.

**Log level DEBUG (1), not VERBOSE.** Every line this round needs is `AppLog.i`, `.d` or `.w`. Given
this unit's driver stack floods logcat and wraps the ring buffer inside one run, VERBOSE costs
evidence and buys none.

**Any transport is fine.** Nothing here is transport-specific. Use whatever connects most reliably;
USB is simplest.

**A3 removes the Bluetooth media link by connecting over USB with the head unit's Bluetooth off.**
That is one adb command and needs nobody present:

```bash
adb shell svc bluetooth disable
```

Do not try to do it in mode 3 — that transport needs Bluetooth for the RFCOMM handshake, so
switching the radio off there changes two variables at once. Over USB, Bluetooth plays no part in the
connection, so turning it off isolates the A2DP link cleanly.

(Unticking **Media audio** for the head unit in the phone's Bluetooth settings is the other way to
drop A2DP alone while leaving RFCOMM up, and is the more surgical test. There is no reliable adb
command for it, so it is optional: do it only if the rig is attended anyway, and say so.)

### Settings keys

| Setting | Element |
|---|---|
| Playback focus mode (0 Automatic, 1 Always, 2 Never) | `<int name="playback-focus-mode" value="0" />` |
| Audio sink — must stay on | `<boolean name="enable-audio-sink" value="true" />` |
| Static audio focus — must stay off | `<boolean name="static-audio-focus" value="false" />` |
| Log level — DEBUG | `<int name="log-level" value="1" />` |

An absent `playback-focus-mode` reads as Automatic, so for A2/A3 you may delete the key instead of
writing `0`.

```bash
# set the mode (V = 0 | 1 | 2), app stopped
PKG=com.andrerinas.headunitrevived
adb shell am force-stop $PKG
adb shell run-as $PKG sh -c '
  f=shared_prefs/settings.xml
  sed -i -E "s#<[a-z]+ name=\"playback-focus-mode\"[^>]*/>##g" $f
  sed -i -E "s#<string name=\"playback-focus-mode\">[^<]*</string>##g" $f
  sed -i "s|</map>|<int name=\"playback-focus-mode\" value=\"V\" /></map>|" $f
'
adb shell run-as $PKG cat shared_prefs/settings.xml | grep -o 'playback-focus-mode[^/]*'
```

### The lines that decide every run

Verified with `grep -F` against `c0f3ec12`:

```
AapAudio: AA audio started (AUDIO) - acquiring transient system audio focus (mode=…, bluetoothMedia=…)
AapAudio: AA audio started (AUDIO) - leaving system audio focus alone (mode=…, bluetoothMedia=…, latched=…)
AapAudio: last AA audio channel stopped - releasing transient system audio focus
AapAudio: media stopped Nms after taking audio focus (n/2)
AapAudio: taking system audio focus is stopping the phone's own playback … not acquiring it again this session
BluetoothHelper: the adapter would not report its A2DP state; assuming a media link is up
AapControlMedia: Media Start Request AUDIO: session=N
AapControlMedia: Media Sink Stop Request: AUDIO
AapMediaPlayback: status mediaSource='…', playbackSeconds(u32)=N, state=PLAYING|PAUSED
```

Both decision lines carry `bluetoothMedia=`, so **every run records what the probe answered**
whichever way it went. That value is the single most useful thing in the capture.

A one-line extractor for any run:

```bash
grep -E "AapAudio: AA audio started|Media (Start Request AUDIO|Sink Stop Request: AUDIO)|state=(PLAYING|PAUSED)|media stopped|not acquiring it again|would not report its A2DP" rN.txt
```

**The `state=` lines only appear if the phone's media app reports playback status.** The reporter's
TIDAL did; not every app does. If you see none, use `Media Start Request AUDIO` /
`Media Sink Stop Request: AUDIO` pairs instead — a healthy track is one Start with no Stop until the
track ends.

---

## Runs

### A0 — build and unit tests (gate)

Use `hur-wifi-test-scripts/build_hur` (and whatever install script sits beside it) rather than
driving Gradle by hand — see `TESTING-TEMPLATE.md` §5, and record which scripts you used. If none of
them runs the unit tests, that is the one thing to add:

```bash
ls -la hur-wifi-test-scripts/          # inventory first, every round
./hur-wifi-test-scripts/build_hur      # or whatever the inventory shows for a debug build
```

The contract this step has to satisfy, whichever script gets you there — the unit tests must run,
not just the assemble:

```bash
./gradlew :app:testGithubDebugUnitTest :app:assembleGithubDebug \
  -Dorg.gradle.java.home=/opt/android-studio/jbr
```

No baseline APK is needed this round: A4a is the control, and it runs on the candidate.

**None of these three commits has been through a compiler** — there is no Android toolchain on the
machine they were written on. Most likely to fail:

- `aap/PlaybackFocusPolicy.kt` — new file; nested `Mode` enum with a `companion object` inside an
  `object`, and `msSinceAcquire in 0L until SELF_DEFEATING_WINDOW_MS`;
- `PlaybackFocusPolicyTest` — 13 assertions, the only coverage the decision has;
- `AapAudio.kt` — new `Context` constructor parameter, threaded from `AapTransport.kt:144`;
- `BluetoothHelper.isA2dpMediaLinkActive` — `BluetoothProfile` import, hidden `A2DP_SINK` literal;
- `SettingsFragment.kt` — `SegmentedButtonSettingEntry` + `InfoBanner` usage, and the
  `PlaybackFocusPolicy.Mode` pending value threaded through six plumbing sites;
- `strings.xml` — seven new strings, one with an escaped apostrophe and an em dash.

**FAIL here stops the round.** Paste the compiler output verbatim; do not work around it. A
`PlaybackFocusPolicyTest` failure is a real finding — it means the decision table does not say what
the fix claims.

### A1 — is this rig an A2DP sink at all? **(gate for A2 and A4a)**

No app involvement, and no listening required — the framework answers this. Pair the phone, then
start playback on it without touching it:

```bash
adb -s <phone> shell input keyevent KEYCODE_MEDIA_PLAY
sleep 5

# head unit — the decisive checks, in order of authority
adb shell dumpsys bluetooth_manager | grep -iE "a2dp|avrcp|profile" | head -40
adb shell service list | grep -i a2dp
adb shell dumpsys audio | grep -iE "a2dp|bluetooth|player" | head -30
adb shell dumpsys media.audio_flinger | grep -iE "a2dp|Output thread" | head -20
```

**It is a sink** if the head unit shows an A2DP **sink** profile connected — `A2dpSinkService`, or a
`Profile: A2DP_SINK` / `A2dpSink` entry in a connected state — and `dumpsys audio` shows an active
player or a non-idle output while the phone is playing.

**It is not a sink** if the only A2DP entry is the *source* service (`A2dpService` with no sink), or
no A2DP service exists at all. Record A2 and A4a as **INCONCLUSIVE — rig is not an A2DP sink** and
go straight to A3.

If the two signals disagree, the head unit's connected-profile list wins; note the disagreement.
Sound actually coming out of the speakers is welcome confirmation but is not the gate, and no verdict
this round depends on anyone hearing it.

Keep all four outputs whichever way it went: they are what
`getProfileConnectionState(A2DP | A2DP_SINK)` had to work with, and A3 shows what it returned.

### A2 — Automatic with the media link up: focus is left alone **(the point of this round)**

Requires A1 positive. `playback-focus-mode` absent or `0`, `enable-audio-sink=true`,
`static-audio-focus=false`, Media audio **ticked**. Connect, then start a track of at least 60 s:

```bash
adb shell input keyevent KEYCODE_MEDIA_PLAY_PAUSE
```

**PASS** requires all of:

- `AapAudio: … leaving system audio focus alone (mode=AUTO, bluetoothMedia=true, latched=false)`;
- **no** `acquiring transient system audio focus` anywhere in the run;
- exactly **one** `Media Start Request AUDIO` for the track, with **no** matching
  `Media Sink Stop Request: AUDIO` until the track ends or you stop it;
- if `state=` lines appear: continuous `PLAYING`, no `PAUSED` you did not cause;
- the head unit's own AudioTrack stays alive for the whole track — one
  `AudioTrackWrapper.createAudioTrack` at the start and **no** `AudioTrackWrapper thread finished.`
  until the track ends. That is the scriptable stand-in for "the sound kept playing"; hearing it is
  welcome confirmation but no verdict depends on it.

**FAIL** if `bluetoothMedia=false` while music is genuinely playing over Bluetooth: the probe cannot
see the link on this hardware. Not fatal to the fix — the latch in A5 is the backstop for exactly
that — but it is the most important negative this round can produce. Say it loudly, keep the full
capture and A1's `dumpsys` output, and note that A5 has just become the run that matters.

### A3 — Automatic with no media link: focus is still taken (issue #658 intact)

The half that proves the fix did not simply disable the feature. USB connection, head unit's
Bluetooth off, mode irrelevant:

```bash
adb shell svc bluetooth disable
sleep 3
adb shell dumpsys bluetooth_manager | grep -i "enabled"      # confirm it is actually down
# connect over USB, then start a track
adb shell input keyevent KEYCODE_MEDIA_PLAY_PAUSE
```

**PASS**: `acquiring transient system audio focus (mode=AUTO, bluetoothMedia=false)`, and a matching
`last AA audio channel stopped - releasing transient system audio focus` when the track ends.

**FAIL**: `bluetoothMedia=true` with the radio off. The probe is answering on something other than
the A2DP link, which would mean Automatic never takes focus on any unit with Bluetooth paired —
silently regressing #658.

Re-enable afterwards, before any later run: `adb shell svc bluetooth enable`.

Also worth recording: `BluetoothHelper: the adapter would not report its A2DP state`. That means
neither profile could be read and the probe defaulted to "assume a link is up" — deliberate, on the
grounds that a radio playing over AA is an annoyance and silence is a broken app. If it appears,
`bluetoothMedia` reads `true` everywhere, A2 and A3 cannot distinguish anything, and A5 becomes the
run that matters.

### A4 — the overrides, and the positive control

**(a) Always, with the media link up — reproduce the bug on purpose.** `playback-focus-mode=1`,
otherwise A2's setup.

The most valuable single measurement of the round: this should make #744 **reappear** on this rig.
Expect `acquiring transient system audio focus (mode=ALWAYS, bluetoothMedia=true)`, then the churn —
`Media Sink Stop Request: AUDIO` within roughly 3-5 s of each start, repeating about every 6 s, with
`state=PAUSED` shortly after each grant if the app reports status.

**PASS**: the churn appears under Always and did not under Automatic in A2. That pair is the proof
the fix addresses the real mechanism rather than coincidentally hiding it.

**If Always does not reproduce it**, that matters just as much: this rig does not exhibit the defect,
A2's pass means nothing on its own, and the fix stays verified only by the reporter's logs and the
JVM tests. Say so plainly rather than reporting A2 as a win.

**(b) Never.** `playback-focus-mode=2` with A3's setup (USB, head unit Bluetooth off). Expect
`leaving system audio focus alone (mode=NEVER, bluetoothMedia=false)` and no acquire — Never must
override even a clean probe.

**(c) The setting is read, honoured and persistent.** All of this is `settings.xml` plus the log —
do not open the settings screen, and do not scroll it.

The three writes in (a) and (b) already prove the value is *read*, because each produced a different
`mode=` in the decision line. What remains is that it survives:

```bash
PKG=com.andrerinas.headunitrevived
# after the A4(b) run, with the app stopped
adb shell run-as $PKG cat shared_prefs/settings.xml | grep -o 'playback-focus-mode[^/]*'   # expect value="2"
adb shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity
sleep 10
adb shell am force-stop $PKG
adb shell run-as $PKG cat shared_prefs/settings.xml | grep -o 'playback-focus-mode[^/]*'   # still value="2"
```

**PASS**: the value is unchanged after a full launch-and-stop cycle, and the next connection still
logs `mode=NEVER`. The app rewrites `settings.xml` on exit, so a key it does not understand would be
dropped here — that is what this checks.

Then the gate, also from the log rather than the screen. Write `static-audio-focus=true` and connect:
there must be **no** `acquiring` or `leaving` line at all, whatever `playback-focus-mode` says. Static
mode owning focus outright is the reason the control is hidden in the UI in that state; the log is
the authoritative half and the hidden row merely follows it.

If you want the control confirmed visually, one screenshot of the Audio section is enough — but it is
optional, and not worth hunting for:

```bash
adb shell screencap -p /sdcard/s.png && adb pull /sdcard/s.png
```

### A5 — the self-heal latch **(probably UNTESTABLE here — read before running)**

The latch exists only for units where the probe is blind. It needs Automatic **and**
`bluetoothMedia=false` **and** the phone still cutting its own audio: two media channels closing
within 5 s of a grant, then the app stops asking for focus for the rest of the connection.

That combination cannot be forced from settings — Always bypasses the latch by design, and Automatic
with a working probe never acquires. So:

- **If A2 showed `bluetoothMedia=true`** (probe works): record **UNTESTABLE on this rig** and move on.
  The latch is covered by `PlaybackFocusPolicyTest`. Do not contrive a substitute.
- **If A2 showed `bluetoothMedia=false` with a real link** (probe blind): A5 is live and is the most
  important run of the round. Play two short tracks back to back:

```bash
adb shell input keyevent KEYCODE_MEDIA_PLAY_PAUSE; sleep 20
adb shell input keyevent KEYCODE_MEDIA_NEXT;       sleep 20
adb shell input keyevent KEYCODE_MEDIA_NEXT
```

  Expect, in order:

```
AapAudio: media stopped Nms after taking audio focus (1/2)
AapAudio: media stopped Nms after taking audio focus (2/2)
AapAudio: taking system audio focus is stopping the phone's own playback … not acquiring it again this session
```

  then `leaving system audio focus alone (mode=AUTO, bluetoothMedia=false, latched=true)` for every
  later track, and the audio settling for the rest of the session. **PASS** is the latch tripping
  within two tracks and audio staying up afterwards. Report both `Nms` values.

### A6 — regressions

Quick; none should show anything.

- **Audio sink off changes nothing.** `enable-audio-sink=false`: no `AapAudio:` focus lines of either
  kind. Pre-existing gate, must still hold.
- **Static audio focus untouched.** `static-audio-focus=true`: no `acquiring` / `leaving` lines at
  all — static mode manages focus permanently and must never reach the dynamic path.
- **Navigation and assistant still get their channels.** Automatic, media link up, start navigation
  on the phone. Expect `Media Start Request AUDIO1` (speech) or `AUDIO2` (system) with a matching
  `AudioDecoder.start: channel=4` or `channel=5`, and **no** `media stopped Nms after taking audio
  focus` from a prompt — only the media channel counts toward the latch, and a prompt tripping it is
  a defect. Prompts being audible is confirmation, not the verdict.
- **No `FATAL EXCEPTION`**, and no hitch in the picture at track boundaries. The probe runs on the
  AAP transport thread once per track start; if it were slow it would show there.

## Do not re-run

Any hotspot run. This branch shares no commits with that work.

---

## Report back

Three things decide whether this ships as it stands:

1. **Is the rig an A2DP sink** (A1). Everything conditional hangs off it; a negative is a good
   answer, it just moves the verdict onto the JVM tests and the reporter's logs.
2. **What `bluetoothMedia=` said** in A2 and A3, and whether it tracked the Media audio tick. That
   one value is the fix's whole detection story.
3. **Whether Always reproduces the cutting and Automatic does not** (A4a vs A2). Without that pair,
   nothing here is proven on hardware.

A line each on: whether `the adapter would not report its A2DP state` ever appeared, whether A5 was
reachable, and whether the Settings control behaved (A4c).

Results as `audio-focus-round6-results.md`, in the shape `TESTING-TEMPLATE.md` §7 specifies.

This brief was written without a device and without a compiler. If a settings key is named wrong, a
log string does not match, or A1's method does not work on this rig, the **Setup notes** section is
the most useful thing you can send back.
