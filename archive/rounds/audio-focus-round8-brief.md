# Audio focus — round 8 brief

Read `TESTING-TEMPLATE.md` first; everything standing lives there and is not repeated here. **Read
§7a before planning the runs** — one quirk in it decides the order of this entire round.

---

## 1. Build

**Candidate:** `fix/audio-focus-pauses-bt-source` @ `26032e65` on the `fork` remote.

> **History was rewritten since round 7.** The branch was rebuilt from `main` and force-pushed, so
> the five commits you saw last round no longer exist and a plain `git pull` will conflict or
> fast-forward wrongly. Fetch and reset:
>
> ```bash
> git fetch fork --prune --prune-tags
> git checkout -B fix/audio-focus-pauses-bt-source fork/fix/audio-focus-pauses-bt-source
> git log --oneline -3      # expect 26032e65, a2381b46, d2dff1df
> ```
>
> The round 6 and round 7 archive refs have been deleted; do not look for them.

**No baseline APK this round.** Every positive control is a settings change on the candidate rather
than a second build — `playback-focus-mode=1` (Always) restores the pre-fix behaviour on all the
paths this round touches. One build, and the control is a one-line pref write.

### R0 — build gate

Use the rig's scripts (§5): `run_unit_tests.sh`, then `build_hur.sh`.

- `PlaybackFocusPolicyTest` must report **20 tests, all green** (14 from round 6 plus 6 new).
- The full unit-test suite must be green.
- Record the APK md5 and confirm it is live on the device (§5).

**If R0 fails, stop and report.** Nothing below is meaningful without it.

---

## 2. What this is

The app takes system audio focus so another player on the head unit — a radio — pauses while Android
Auto plays. On a head unit that is also the phone's Bluetooth A2DP sink that backfires: AOSP's
`A2dpSinkStreamHandler` answers the focus loss with an AVRCP passthrough PAUSE aimed at the source
device, which is the same phone feeding us the AA stream. Round 6 confirmed the mechanism on this
rig, including that the competing `requestAudioFocus()` came from `com.android.bluetooth`.

Round 6 tested **one** grab path. The code has **three**, and this round is the first to exercise
the other two:

| Path | Where | Grab | Round 6 |
|---|---|---|---|
| **1** | `AapAudio.onAudioPlaybackStarted` — an AA audio channel opens | `GAIN_TRANSIENT` | tested |
| **2** | `AapService.requestPermanentAudioFocus` + `CommManager.startReading` — at connect, **Static Audio Focus only** | permanent `GAIN` | never run |
| **3** | `AapControl.audioFocusRequest` — the phone asks for focus over the protocol | whatever the phone asked for, including permanent `GAIN` | never run |

All three now ask `PlaybackFocusPolicy` and log the same `bluetoothMedia=` shape, so one grep covers
them.

**The prediction this round exists to test.** For path 2 the loss is permanent, not transient, so
AOSP's handler should pause the phone **once and never resume** — a session that starts silent
rather than one that cycles every ~3.4 s the way round 6's A4a did. That is read off the AOSP source
and **has never been measured**. R6 is where it gets confirmed or killed; a cycle there means the
model is wrong and the write-up needs correcting.

### Also in scope: issue #802

A separate reporter says *"locking and unlocking phone stops media playback"*, source phone, no log
yet. If it is this bug it is most likely path 1 — locking closes the audio channel, unlocking
reopens it, path 1 grabs, the sink pauses the phone — in which case the shipped fix already covers
it. Path 3 would produce the same symptom via a different message. R2 and R3 settle which, or rule
the family out entirely.

---

## 3. What is different about this round

**Do the link-dependent runs first, and do not cycle the phone's radios between them.** §7a: dropping
the A2DP link is deterministic, getting it back is not — round 7 lost two cycles including a full 8 s
off and never recovered, which cost that round its A2DP runs.

So this round deliberately **departs from §4's clean-run protocol** for R1-R6: run the airplane-mode
cycle **once**, to establish the AA session and let the A2DP link come up with it, then keep the
phone untouched across those six runs. Reset between them on the head unit side only:

```bash
PKG=com.andrerinas.headunitrevived
adb shell am start -a android.intent.action.VIEW -d "headunit://exit"
sleep 3
adb shell am force-stop $PKG
# write the next run's keys, verify, relaunch — phone's radios never touched
```

Before each of R1-R6, confirm the link is still up and say so in the results:

```bash
adb shell dumpsys bluetooth_manager | grep -iE "a2dp|avrcp|Connected"
```

If it drops partway, the remaining link-dependent runs are **INCONCLUSIVE** — rig flakiness, not a
finding. Carry on to R7-R9, which need no link.

**Log level: DEBUG (`log-level=1`), not VERBOSE.** §7a says this unit's driver stack floods logcat
and wraps the buffer. DEBUG carries every line this round decides on, including the latch counter,
which is the only `AppLog.d` among them. The A2DP and AVRCP evidence comes from framework lines in
logcat and is unaffected by the app's own log level. If a run's decisive line turns out to be missing
at DEBUG, redo that one run at VERBOSE and note it.

**R7 is expected to be INCONCLUSIVE and that is a fine result.** Path 3 only fires if the phone sends
an `AudioFocusRequestNotification`, and the #744 reporter's phone never sent one in a whole verbose
capture. If this rig's phone does not either, say so — do not invent a substitute.

---

## 4. Settings keys

Written into `shared_prefs/settings.xml` with the app stopped (§1). Push a script rather than inline
`sh -c` (§7a).

| Key | Type | Element |
|---|---|---|
| `enable-audio-sink` | boolean | `<boolean name="enable-audio-sink" value="true" />` |
| `static-audio-focus` | boolean | `<boolean name="static-audio-focus" value="false" />` |
| `playback-focus-mode` | int | `<int name="playback-focus-mode" value="0" />` |
| `log-level` | int | `<int name="log-level" value="1" />` |

`playback-focus-mode`: **0 = Automatic, 1 = Always, 2 = Never.** Absent reads as 0.

`enable-audio-sink` stays `true` for every run in this round; if it is false the app never asks for
focus at all and every run below is vacuous.

---

## 5. The lines that decide the runs

Verified with `grep -F` against `26032e65`. Everything is prefixed `OPENHU`.

**Path 1 (dynamic, per channel) — `AapAudio.kt`:**

```
AapAudio: AA audio started (<CH>) - acquiring transient system audio focus (mode=<M>, bluetoothMedia=<B>)
AapAudio: AA audio started (<CH>) - leaving system audio focus alone (mode=<M>, bluetoothMedia=<B>, latched=<L>)
AapAudio: last AA audio channel stopped - releasing transient system audio focus
```

**Path 2 (static, permanent) — `AapService.kt` and `CommManager.kt`:**

```
AapService: Static Audio Focus - acquiring permanent system audio focus (mode=<M>, bluetoothMedia=<B>)
AapService: Static Audio Focus - leaving system audio focus alone (mode=<M>, bluetoothMedia=<B>)
CommManager: Static Audio Focus - leaving system audio focus alone (mode=<M>, bluetoothMedia=<B>)
Static Audio Focus disabled - skipping permanent audio focus request; focus will be acquired on demand.
```

**Path 3 (protocol-driven) — `AapControl.kt` and `AapAudio.kt`:**

```
Audio Focus Request: <REQUEST>                    <- AapControl, the phone asked
AapAudio: phone asked for audio focus - leaving system audio focus alone (mode=<M>, bluetoothMedia=<B>, latched=<L>)
Audio Focus Request: stream=<S>, type=<T>         <- AapAudio, the grab is going ahead
Audio focus request result: GRANTED
Static Audio Focus active - skipping dynamic system focus request to prevent routing loss
```

**The latch (path 1 only) — `AapAudio.kt`:**

```
AapAudio: media stopped <N>ms after taking audio focus (<n>/2)
AapAudio: taking system audio focus is stopping the phone's own playback (the head unit is most likely its Bluetooth audio sink) - not acquiring it again this session
```

**The sink reacting (framework, always present in logcat):** `A2dpSinkStateMachine`,
`A2dpSinkStreamHandler`, `avrcp`, and `requestAudioFocus` from `com.android.bluetooth`.

**Reading the phone's player state, scripted** — this is the measurement, not an impression:

```bash
adb -s <phone> shell dumpsys media_session | grep -iE "state=PlaybackState|package"
```

---

## 6. Runs

### Link-dependent — run these first, in this order

#### R1 — path 1 unchanged after the rebuild

The regression gate. Round 6's A2 verdict must still hold on rebuilt history.

- Settings: `static-audio-focus=false`, `playback-focus-mode=0`.
- Start AA, get media playing, then force a fresh audio channel (§7a — media keys alone will not do
  it; restart the media app on the phone).
- **PASS:** the `leaving system audio focus alone` line appears with `bluetoothMedia=true`; no AVRCP
  PAUSE follows; media keeps playing for 60 s.
- **FAIL:** focus is acquired, or the phone pauses.

#### R2 — #802, Automatic

- Settings: as R1.
- With media playing and the session up:

```bash
adb -s <phone> shell input keyevent KEYCODE_SLEEP     # 223
sleep 10
adb -s <phone> shell input keyevent KEYCODE_WAKEUP    # 224
adb -s <phone> shell wm dismiss-keyguard
sleep 15
```

If the phone has a screen lock, `wm dismiss-keyguard` may leave it on the lock screen — remove the
PIN for this round or use a swipe, and say which in Setup notes.

- **PASS:** media is still playing 15 s after the unlock. Record the `dumpsys media_session` state
  before the lock and after the unlock.
- **FAIL:** playback stopped. Quote every focus line between the lock and the stop — that identifies
  which path did it.

#### R3 — #802 positive control, Always

Same as R2 with `playback-focus-mode=1`. **This is the run that tells us whether #802 is our bug at
all.**

- **PASS (bug reproduced, which is the expected outcome):** playback stops after the unlock, an
  acquire line appears, and an AVRCP PAUSE follows it. **State which path's acquire line fired** —
  `AA audio started … acquiring` is path 1, `Audio Focus Request: stream=` plus
  `Audio focus request result: GRANTED` is path 3.
- **INCONCLUSIVE:** playback survives even under Always. Then #802 is not this mechanism on this
  hardware, which is worth knowing and ends the #802 part of the round.

#### R4 — path 2 gate, static + Automatic + live link

- Settings: `static-audio-focus=true`, `playback-focus-mode=0`.
- Connect with the A2DP link up. The decision happens **at connect**, before any audio.
- **PASS:** `AapService: Static Audio Focus - leaving system audio focus alone (mode=AUTO,
  bluetoothMedia=true)` — and the `CommManager:` twin if that path is reached. No AVRCP PAUSE in the
  60 s after connect.
- **FAIL:** the permanent grab happens anyway, or the phone pauses.

#### R5 — path 2 positive control, static + Always + live link

**The point of the round.** Settings: `static-audio-focus=true`, `playback-focus-mode=1`.

- **PASS:** `AapService: Static Audio Focus - acquiring permanent system audio focus` appears, and
  the phone pauses.
- **Then measure the shape, which is the actual question.** Watch for 90 s and report:
  - how many AVRCP PAUSE events occurred — **the prediction is exactly one**;
  - whether playback resumed on its own;
  - if it did repeat, the interval between pauses, in ms.

  One pause and no resume confirms the model. A repeating cycle refutes it — that is a finding, not
  a failure, and it should be called out prominently.

#### R6 — path 3, protocol-driven grab

- Settings: `static-audio-focus=false`, `playback-focus-mode=0`, live link.
- Try to make the phone send an `AudioFocusRequestNotification`: start and stop a phone-side player,
  trigger the assistant, take a notification with sound, place and end a call if that is possible.
- **PASS:** at least one `Audio Focus Request: <REQUEST>` line from `AapControl` appears, followed by
  `AapAudio: phone asked for audio focus - leaving system audio focus alone`, and **no**
  `Audio focus request result: GRANTED` for it.
- **Also check RELEASE is never gated:** if any `RELEASE` request appears, it must still reach
  `AapAudio` and log `Releasing audio focus`. Report separately from the PASS above.
- **INCONCLUSIVE:** no `Audio Focus Request:` line appears at all after a genuine attempt. Expected;
  say what you tried.

### No link needed — run these whatever happened above

#### R7 — static mode is unchanged for everyone this bug does not affect

- Settings: `static-audio-focus=true`, `playback-focus-mode=0`. Phone's Bluetooth **off** (§7a: the
  head unit's own Bluetooth cannot be switched off on this rig; switch the phone's).
- **PASS:** `AapService: Static Audio Focus - acquiring permanent system audio focus (mode=AUTO,
  bluetoothMedia=false)` — the grab still happens. This proves the fix does not quietly disable
  Static Audio Focus for its actual users.
- **FAIL:** the grab is skipped with no Bluetooth link present.

#### R8 — the setting survives backup and restore

New this round; `playback-focus-mode` was missing from both of `SettingsBackupManager`'s lists.

- Set `playback-focus-mode=2` (Never), export settings through the app's backup export, and
  **confirm `playback-focus-mode` appears in the exported JSON with value 2.**
- Set it back to 0, import the file, and confirm it reads 2 again afterwards
  (`grep -o 'playback-focus-mode[^/]*'` on `settings.xml`).
- **PASS:** present in the JSON and restored correctly. **FAIL:** absent, or not restored.

#### R9 — the control is on screen under Static Audio Focus

The one run that needs a screenshot (§3), because a regression here is invisible in the log: the
tri-state used to be hidden while Static Audio Focus was on, and now must be shown.

- Settings: `static-audio-focus=true`, `enable-audio-sink=true`.
- Deep-link to settings, do **not** scroll to find things:

```bash
adb shell am start -n $PKG/com.andrerinas.openheadunit.main.SettingsActivity --ei extra_destination 0
adb shell screencap -p /sdcard/s.png && adb pull /sdcard/s.png
```

- **PASS:** "Pause Other Audio During Playback" is visible with three options, plus the extra hint
  line about Static Audio Focus applying for the whole session. If the Audio section is not on the
  first screen, say so and mark the run UNTESTABLE rather than scrolling.

---

## 7. Do not re-run

Settled in round 7 and unaffected by this branch's changes — do not spend the round re-proving them:

- `AppLog.Logger.File` ordering, drop markers and throughput.
- The `LogExporter` 16 MB cap and the absence of a restart loop.
- The cached log level.

Those three files are byte-identical to the commit round 7 tested.

---

## 8. Report back

`audio-focus-round8-results.md` on this branch, in §7's format. The numbers that decide shipping:

1. **R5's pause count** — one, or a repeating cycle with its interval in ms. This is the round's
   headline.
2. **R3's outcome and which path fired**, which settles whether #802 is this bug and whether the
   shipped fix already covers it.
3. **R1's verdict**, which says the rebuild did not disturb what round 6 verified.
4. Whether R6 reached path 3 at all.

Report INCONCLUSIVE honestly wherever the link or the phone would not cooperate. Round 7 lost its
A2DP runs to rig flakiness and saying so plainly was more useful than a manufactured substitute
would have been.
