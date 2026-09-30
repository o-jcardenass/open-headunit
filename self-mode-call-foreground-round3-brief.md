# self-mode-call-foreground, round 3 brief: putting the projection back over the call screen

**Candidate:** `fork/fix/883-self-mode-call-raise` @ `4d8679e7` (2 commits on `origin/main`
`ea7aa7e0`, the build rounds 1 and 2 ran). **Baseline:** `origin/main` @ `ea7aa7e0`, for R4 only.

Rounds 1 and 2 settled that this is Android Auto's defect and that no workaround exists outside the
app. This round measures the one thing that is left, which is the app doing it itself: in Self Mode,
put the projection back on top of the call screen. The reporters asked for exactly this, and one of
them rides a motorbike, where switching apps by hand is the actual problem.

Read first: **§0 of `self-mode-call-foreground-round1-brief.md`**, which is still the operating
manual for a Self Mode round and is **not** repeated here. Then this file. Nothing about the Self
Mode protocol has changed.

---

## 0. What rounds 1 and 2 settled, so this round does not re-prove it

Standing facts, inherited and not re-derived. Two of them forbid a verdict:

- **The head unit server toggle can look on while being off.** Tap **Start head unit server** in
  Android Auto's developer settings by hand and confirm from the log before believing the rig is
  ready.
- **The default dialer on this phone is `com.android.dialer`**, not the reporters'
  `com.google.android.dialer`. Every grep below uses the former.
- **Neither `Car Swapping ICS` nor `am_on_top_resumed_*` exists on this build.** No verdict here may
  rest on either.

| Fact | Evidence, round 1 |
|---|---|
| The call screen takes the screen on answer, not on ring | `START u0 {cmp=com.android.dialer/com.android.incallui.InCallActivity} ... from uid 10126 (BAL_ALLOW_SAW_PERMISSION)`, then `AapProjectionActivity: onPause` 23 ms later |
| Same for an outgoing call | direction-independent, confirmed in R4 |
| Our activity is only paused, never destroyed | `onResume` 17 ms after the Dialer's own activity finishes, with **no `START u0` for our package** |
| Android Auto's own call UI is live on the projected surface | `NonCarInCallServiceImpl` bound and feeding `GH.CallManager` for the call's duration |

That last row is why this is worth doing at all: the picture underneath the call screen is already
the call UI the reporters want.

---

## 1. What the candidate does

`AapProjectionActivity` opens a **raise episode** when it is paused by something that is not the
user, while a call is up, in Self Mode, with the setting on. A pure policy
(`aap/SelfModeCallRaisePolicy`) then decides each tick, and the raise itself is the service's
existing overlay trampoline — the same `BAL_ALLOW_SAW_PERMISSION` route the call screen used to
cover us.

```
covered (call active, no onUserLeaveHint)
  +600ms   raise attempt 1
  +1.2s    raise attempt 2, if still covered
  +1.2s    raise attempt 3, if still covered
  spent    stop pushing for the rest of the call
call ends, still covered
  +1s      one last raise, then the episode closes
```

Four bounds, all of which this round should see or see the absence of:

- **`onUserLeaveHint` gates the whole thing.** It fires on Home and Recents and not when an activity
  launches over us, so a deliberate exit is never argued with. R3 is the run for this.
- **Three attempts per call, then silence**, so somebody who wants the phone's call screen can have
  it. A call screen that relaunches itself cannot buy a second budget: attempts carry for 5 s.
- **One attempt a second after the call ends.** Android normally reveals the still-paused activity
  itself (17 ms, above); the second is there to let it, and the attempt is for the reporter whose
  projection never came back.
- **Self Mode only, on a setting.** `AapService.selfMode` and `raise-projection-during-call` both
  have to be true.

The call is read from `AudioManager.mode`, which needs no permission — `MODE_IN_CALL`, or
`MODE_IN_COMMUNICATION` when our own microphone uplink is not the one holding it. `MODE_RINGTONE`
opens an episode but never raises on its own; if no call registers within 2 s the episode closes
having done nothing.

The second commit clears `AapService.selfMode` on a failed Self Mode start and on service destroy.
It was set in two places and cleared in one, so a stale `true` reached the next session in that
process — which drops the media and speech audio sinks from service discovery. **R5 is that run.**

---

## 2. What is different about this round

- **The phone took an OTA between rounds 1 and 2 and now reads `ro.build.version.sdk=35`.** Round
  1's device facts were recorded at 34 and have not been re-verified. Re-confirm the default dialer
  in R0.
- **Android 15 is the open question, not the code.** The `BAL_ALLOW_SAW_PERMISSION` grant round 1
  saw was at Android 14 and was the Dialer's, not ours. If 15 refuses our overlay trampoline the
  raise silently fails: `launchViaOverlayTrampoline` returns success on a `startActivity` that was
  blocked. **No run may take `Overlay trampoline: startActivity succeeded` as proof anything
  happened.** The proof is always `AapProjectionActivity: onResume` and `topResumedActivity`.
- **There is a build this time.** Rounds 1 and 2 ran the installed APK unchanged.
- **Install with `-r`, not uninstall/reinstall.** A fresh install re-runs onboarding, which rewrites
  the settings this round depends on. Round 1 confirmed `-r` preserves `settings.xml` byte for byte;
  diff it before and after anyway.

---

## 3. Settings

| Key | Type | Value | Runs |
|---|---|---|---|
| `log-level` | int | `2` (INFO) | all |
| `raise-projection-during-call` | bool | absent (defaults to `true`) | R1, R2, R3, R5 |
| `raise-projection-during-call` | bool | `false` | R4 |

```
<int name="log-level" value="2" />
<boolean name="raise-projection-during-call" value="false" />
```

Every decisive app line below is `AppLog.i`, so INFO carries the whole round. Change nothing else —
in particular do not touch `enable-audio-sink`, except where R5 reads it.

---

## 4. Capture setup, before every capture

```bash
PHONE=<serial>
PKG=com.andrerinas.headunitrevived
GH=com.google.android.projection.gearhead

adb -s $PHONE shell logcat -G 64M
adb -s $PHONE shell setprop log.tag.GH VERBOSE
adb -s $PHONE shell setprop log.tag.CAR VERBOSE
adb -s $PHONE shell setprop log.tag.Telecom VERBOSE
adb -s $PHONE shell getprop log.tag.GH            # MUST print VERBOSE. If empty, stop and say so.
adb -s $PHONE shell am force-stop $GH             # it caches its level at process start

adb -s $PHONE shell am force-stop $PKG
adb -s $PHONE logcat -c
stdbuf -oL adb -s $PHONE logcat -b all -T 1 -v threadtime,uid,usec > rN.txt &
```

`-v threadtime,uid,usec` is not optional: the default format omits the uid, and the uid is how a
`START u0` is attributed.

---

## 5. The lines that decide the round

Ours, all `grep -F`-able:

```
AapProjectionActivity: covered during a call, will raise the projection (N attempts already spent)
AapProjectionActivity: raising the projection - attempt N of 3 during the call
AapProjectionActivity: raising the projection - the call ended and the projection is still covered
AapProjectionActivity: call raise finished - the projection is back in front
AapProjectionActivity: call raise finished - whatever covered the projection was not a call
AapProjectionActivity: onPause
AapProjectionActivity: onResume
Overlay trampoline: startActivity succeeded
AapService: No overlay permission, not raising the projection      <- a failure, see below
```

The platform's, and the pair that decides every run:

```
ActivityTaskManager: START u0 {cmp=com.android.dialer/com.android.incallui.InCallActivity} ... from uid <U>
ActivityTaskManager: START u0 {cmp=com.andrerinas.headunitrevived/...AapProjectionActivity} ... from uid <U>
```

The second one **did not exist in round 1** and is the round's own signature: it is us, and the uid
on it should be ours. If it is present and `onResume` does not follow, Android 15 refused the launch
and that is the round's finding.

If `AapService: No overlay permission, not raising the projection` appears, the overlay permission is
not granted on this phone. That is a setup fault, not a result: Self Mode's own launch path gates on
the same permission. Grant it and re-run.

---

## 6. Runs

Each of R1, R2, R4 needs a real call placed from a second handset by the person at the rig. No
verdict below depends on anyone watching a screen.

### R0, gate

```bash
./gradlew :app:assembleGithubDebug
./gradlew :app:testGithubDebugUnitTest
adb -s $PHONE install -r <apk>
```

PASS when: the build is clean; unit tests are **784** (round 1 measured 765 at `ea7aa7e0`, and this
branch adds 13 in `SelfModeCallRaisePolicyTest` and 6 in `CallStateTest`) with zero failures; the
installed md5 matches the built APK; `settings.xml` diffs empty across the install. Report the actual
test count either way. Also record, since the OTA invalidated round 1's readings:

```bash
adb -s $PHONE shell cmd telecom get-default-dialer
adb -s $PHONE shell getprop ro.build.version.sdk
adb -s $PHONE shell dumpsys package $GH | grep versionName
adb -s $PHONE shell appops get $PKG SYSTEM_ALERT_WINDOW
```

### R1, incoming call answered — the point of the round

Bring up Self Mode, confirm the session (`Handshake: SSL handshake complete`, throughput lines), take
three pre-call `Throughput over 5000ms:` windows, then have the second handset call and **answer it
from the phone's screen**. Hold the call ~30 s, then hang up from the second handset. While the call
is up:

```bash
adb -s $PHONE shell dumpsys activity activities | grep topResumedActivity
```

PASS when all four hold:

1. `AapProjectionActivity: onPause` follows the Dialer's `START u0`, as in round 1 — the defect still
   reproduces on this build.
2. At least one `raising the projection - attempt N of 3` line, followed by
   `AapProjectionActivity: onResume`. **Report N**: how many attempts it took is the round's most
   useful number, and it is what the 600 ms and 1.2 s constants would be retuned from.
3. `topResumedActivity` during the call is `com.andrerinas.headunitrevived/...AapProjectionActivity`.
4. The episode closes with `call raise finished - the projection is back in front`.

Also report, from the same capture:

- Whether the raise came through the trampoline (`Overlay trampoline: startActivity succeeded`) and
  the uid on our own `START u0`.
- `Throughput over 5000ms:` for the three windows after the raise, against the three before. A raise
  is a task reorder, so `dropped` should stay 0 and there should be **no `Configuring decoder:`**
  attributable to it. A decoder rebuild here would mean the reorder tore the surface down, which is
  the one way this change could cost picture.
- The gap between the Dialer's `START u0` and our `onResume`. That is what the user feels.

### R2, outgoing call

Place a call by hand from the phone's own Dialer with the session live. Same PASS conditions as R1,
items 1-4. Round 1 established the mechanism is direction-independent; this checks the fix is too.

### R3, the user's own exit is not argued with

With the session live and **no call**, press Home (`adb shell input keyevent KEYCODE_HOME`), wait
10 s, then return with `adb shell am start -n $PKG/com.andrerinas.openheadunit.aap.AapProjectionActivity`.

PASS when: **zero** `covered during a call` lines and **zero** `raising the projection` lines in the
whole run. Then repeat the Home press *during* a live call, held by the second handset, and require
the same zero.

The second half is the important one and it is the run this bound exists for. If a raise line appears
there, the feature will fight anyone who leaves the projection during a call, and that is a stop.

### R4, the setting off — control

Write `raise-projection-during-call=false` with the app stopped, then repeat R1 exactly.

PASS when: zero `covered during a call` and zero `raising the projection` lines, and the behaviour
matches round 1's — the projection returns by itself at hangup, `onResume` within a few tens of ms of
the Dialer's activity finishing, with no `START u0` for our package. This is also the check that the
setting genuinely gates the path.

### R5, the stale Self Mode flag

The second commit, and it needs no call. In one app process:

1. Stop the head unit server in Android Auto's developer settings, so Self Mode cannot connect.
2. Fire `ACTION_START_SELF_MODE` and confirm
   `SelfMode: Headunit Server (127.0.0.1:5277) is NOT running.`
3. Without force-stopping the app, start an ordinary session on the head-unit rig (USB is fine and
   is the least setup).
4. Grep that session's service discovery for the audio sinks.

PASS when: the USB session announces the media and speech sinks — `Media Sink Setup Request` lines
for `AUDIO` and `AUDIO1`, i.e. the announcement is *not* the Self Mode subset. On the baseline the
flag would still read true here and the sinks would be missing. If step 3 cannot be arranged in one
process, say so and report R5 as UNTESTABLE rather than approximating it.

---

## 7. Do not re-run

- Anything from round 1 or 2. The root cause and the absence of an external workaround are settled.
- `pm enable` of anything. Round 2 closed it in both directions.
- Any `nc`/`nmap`/port probe of 5277, for any reason (round 1 §0).

---

## 8. Report back

`self-mode-call-foreground-round3-results.md` on this branch, in the template's format. Beyond the
verdicts, the round is only useful if it carries these numbers:

1. **Attempts to a resumed projection**, per call, R1 and R2. If it is always 1, the retry ladder is
   dead weight; if it is 3, the first delay is too short.
2. **Dialer `START u0` to our `onResume`**, in ms, per call. The user-visible cost.
3. **Whether Android 15 honoured the trampoline for us**, stated as its own line, since it is the one
   thing that can invalidate the approach.
4. **`dropped` and `Configuring decoder:` around each raise.**
5. Anything the brief did not ask about, especially any sign of the raise and the call screen
   trading the foreground more than once.
