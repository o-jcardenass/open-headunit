# self-mode-call-foreground, round 1 brief: who raises the phone's Dialer over the projection

**Candidate:** `origin/main` @ `ea7aa7e0` ("releasing 3.3.0-beta1"). **No baseline, no code change.**
This round measures shipped behaviour; there is nothing under test to compare against.

```bash
git fetch origin
git checkout -B measure/3.3.0-beta1 ea7aa7e0
git rev-parse HEAD          # must print ea7aa7e0...
git log --oneline -1        # ea7aa7e0 releasing 3.3.0-beta1
```

This is a **new thread**. Nothing to re-read except `TESTING-TEMPLATE.md` and §0 below.

---

## 0. This round runs on the phone, and most of the standing protocol does not apply

Read this before planning anything. This is the channel's second **Self Mode** round. The first was
`self-mode-bt-audio-round1`, whose §0 said the same things and whose results confirmed them.

Self Mode means Open Headunit and Android Auto are **the same device**. The app connects to Android
Auto's own head unit server over loopback:

```
AapService.kt:2744  "SelfMode: AA 17.4+ detected. Connecting directly to Headunit Server on 127.0.0.1:5277..."
```

Consequences, all load-bearing:

- **Every `adb` line in this round is `-s <phone>`.** The head-unit rig has no role at all here.
- **`TESTING-TEMPLATE.md` §4's clean-run protocol does not apply.** There is no P2P group, no
  credentials and no airplane-mode sequence. Bring-up is: install, write settings with the app
  stopped, launch, fire `ACTION_START_SELF_MODE`.
- **The discard rules do not apply either.** `createGroup SUCCESS`, `p2p-wlan0-N` and the second-SSL
  rule are all about the wireless transport and are inert on loopback. Do not call a run
  unverifiable for their absence. The one contamination signal that still counts is an unintended
  reconnect inside a run.
- **Never send anything to port 5277 that is not the app.** Any connection Android Auto's server
  accepts that then fails to complete the AAP version exchange wedges the server **permanently**,
  including a `nc <ip> 5277 </dev/null` that connects and closes on EOF. No `nc`, no `nmap`, no
  `curl`, no port scan, for any reason, at any point.
- **An unclean session death wedges it too.** Between runs, end the session with
  `headunit://disconnect` and let it close, then force-stop. Never kill the app mid-session.
- **Recovery from a wedged server is manual and silent**: stop and start "Start head unit server" in
  Android Auto's developer settings on the phone. There is no log line for it, so if a run's session
  will not form, suspect this before suspecting anything else.

**One setup step cannot be scripted.** Android Auto developer settings, then **Start head unit
server**, on the phone, by hand, before the round. If it is not running the log says
`SelfMode: Headunit Server (127.0.0.1:5277) is NOT running.` and the app raises a toast.

**This round is not fully unattended, and that is deliberate.** R0, R1 and every capture setup are
scripted and yours. R2 to R5 each need a real phone call, placed from a second handset by the person
at the rig, which has been arranged. Everything else about those runs is scripted, and **no verdict
depends on anyone watching a screen**: every PASS condition below is a log line or a `dumpsys` field.

---

## 1. Why this round exists

Discussion **#883**. Two reporters, a Pixel 11 Pro and a Pixel 8 Pro, both running Open Headunit in
Self Mode. When a call arrives they see **two** answer popups, one from Android Auto on the projected
screen and one from the phone itself. Answering, or placing a call, brings the phone's **Dialer** to
the foreground in portrait and hides the projection. One reporter says it behaved correctly exactly
once and never again.

**The research is already done, and it is what makes this round small.** Every claim below was
checked against AOSP `packages/services/Telecomm`, `frameworks/base`, the AOSP Dialer and SystemUI,
plus a full grep of this repo. They are numbered so your results can cite them.

**Our side:**

- **F1, the app has no call awareness whatsoever.** Zero occurrences repo-wide of
  `TelephonyManager`, `PhoneStateListener`, `TelephonyCallback`, `TelecomManager`, `InCallService`,
  `CALL_STATE` or `NotificationListenerService`. `READ_PHONE_STATE` is declared
  (`AndroidManifest.xml:56`) and never used anywhere; `ANSWER_PHONE_CALLS` is not declared at all.
- **F2, we announce no phone service.** `control.proto:164-196` defines the whole surface
  (`PhoneStatus_State{InCall, Incoming, ...}`, `PhoneStatus_Call`, `PhoneStatusService` as
  `Service.phone_status_service = 10`) and `ServiceDiscoveryResponse.makeProto()` never adds it.
  `Channel.ID_PHONE = 12` and `ID_NOT = 11` are in no dispatch table. So Android Auto has no channel
  over which to hand us call events. The only in-call hint we send is `availableWhileInCall = true`
  on the **video** sink (`ServiceDiscoveryResponse.kt:101`).
- **F3, our own `enableCarMode(0)` cannot be the cause.** `AapService.setupCarMode()`
  (`AapService.kt:828-838`, called unconditionally from `onCreate`) enables system car mode under our
  package. The obvious theory is that this steals the car-mode slot from Android Auto. It does not:
  `InCallController.handleCarModeChange` drops any package that is not *already* a valid car-mode
  `InCallService`, logging `handleCarModeChange: not a valid InCallService`. R1 confirms this
  on-device in one dump, so **no run should be spent on it**.
- **F4, nothing re-raises the projection activity.** `AapProjectionActivity.onPause` clears
  `isForeground` and removes every watchdog callback. There is no `onStop` override and no
  `moveTaskToFront` anywhere in the repo. The four paths that can bring projection back
  (`ACTION_SCREEN_ON`, the ongoing-notification tap, `MainActivity.onResume`, the one-shot
  `HandshakeComplete` launch) fire on none of the events a call produces. So once the Dialer wins, we
  stay hidden until the user acts.

**The platform's side:**

- **F5, there is no "projection suppresses the dialer" flag; there is only a swap.**
  `CarSwappingInCallServiceConnection` holds two bindings. When a qualifying package enters car mode
  or claims automotive projection, Telecom **disconnects the dialer's `InCallService`** and binds the
  car-mode one instead. With the Dialer's service unbound it never receives `onCallAdded`, so it
  never starts `InCallActivity` and never posts the call notification. That is the entire mechanism.
- **F6, qualifying needs `CONTROL_INCALL_EXPERIENCE`, protection level
  `signature|privileged|role`.** `InCallController.getInCallServiceType` requires both the
  `android.telecom.IN_CALL_SERVICE_CAR_MODE_UI` metadata **and** that permission. **Confirmed against
  the shipping APK**, Android Auto `17.5.663204-release`, the exact build this rig runs: it declares
  two in-call services, and only one carries the metadata.

  ```
  com.google.android.apps.auto.components.telecom.service.NonCarInCallServiceImpl
      meta-data  android.telecom.INCLUDE_SELF_MANAGED_CALLS = true
  com.google.android.apps.auto.components.telecom.service.CarProjectionInCallServiceImpl
      meta-data  android.telecom.INCLUDE_SELF_MANAGED_CALLS = true
      meta-data  android.telecom.IN_CALL_SERVICE_CAR_MODE_UI = true
  ```

  It also declares `CONTROL_INCALL_EXPERIENCE`, `TOGGLE_AUTOMOTIVE_PROJECTION`,
  `ENTER_CAR_MODE_PRIORITIZED`, `MODIFY_PHONE_STATE` and `CALL_PRIVILEGED`. No user-grantable role
  grants any of them, and a third-party receiver app can never hold them, so "make Open Headunit the
  car-mode in-call UI" is not on the table. Teardown and full permission table:
  `~/ohu-fixes-handoff/gearhead-17.5.663204-release/README.md`.
- **F7, Android Auto stops setting `UI_MODE_TYPE_CAR` on Android 12 and later**, by Google's own
  documentation. The swap therefore runs off **automotive projection**
  (`requestProjection(PROJECTION_TYPE_AUTOMOTIVE)`), which reaches Telecom as
  `SystemStateHelper.onProjectionStateChanged` then
  `InCallController.handleSetAutomotiveProjection`. Anything that checks `getCurrentModeType()` to
  "detect Android Auto" reads false even when the swap is working perfectly.
- **F8, outgoing calls have no notification in the chain at all.** The Dialer's `InCallPresenter`
  calls `startActivity(InCallActivity...)` unconditionally whenever the in-call UI is not already
  visible. Only unbinding its service stops it. This is why the reporter sees the Dialer when
  *placing* a call, not just when answering.
- **F9, incoming calls go through a full-screen intent.** The Dialer posts `CATEGORY_CALL` plus
  `PRIORITY_MAX` plus `setFullScreenIntent(pi, true)`. SystemUI's `FullScreenIntentDecisionProvider`
  then picks heads-up versus full-screen and logs which branch it took.
- **F10, the "keyguard occluded" theory does not apply to us.** The most commonly cited cause of a
  full-screen takeover is an activity with `setShowWhenLocked`, which puts SystemUI on the
  `FSI_KEYGUARD_OCCLUDED` branch. Grep-verified at `ea7aa7e0`: this repo sets it nowhere. The only
  window flag is `FLAG_KEEP_SCREEN_ON` (`utils/SystemUI.kt:19`), and the projection activity's
  manifest entry (`AndroidManifest.xml:86-96`) has no `showWhenLocked`, `turnScreenOn` or
  `screenOrientation`. Retired before the round; the capture records which branch actually fired.

- **F11, Android Auto says out loud whether it took the projection lock, and the teardown found the
  strings.** One obfuscated class, `Ljff;` in `classes.dex`, is the only caller of
  `requestProjection`, `releaseProjection`, `enableCarMode` and `disableCarMode` in the whole app.
  `Ljff;->e()` calls `UiModeManager.requestProjection(1)` behind an `SDK_INT >= 31` check;
  `Ljff;->d()` is its **fallback**, entered only when `e()` failed, and it logs
  `starting car mode settings connType=%d` then branches on that `connType`. Everything it decides is
  logged under tag **`CAR.SYS`**, so the round can read Android Auto's own verdict at the moment it
  makes it rather than inferring it downstream. The strings are in §5.

**So the round has exactly one question to answer:**

> **Does Android Auto claim automotive projection for a *local head-unit-server* (Self Mode) session,
> or only for a real USB or wireless head unit?**

No public source answers it. It decides everything. If Android Auto does claim it, the Dialer's
service is unbound and no Dialer should ever appear, so a Dialer that appears anyway came from
outside the Telecom chain and we go looking for what. If it does not claim it, the Dialer is behaving
exactly as AOSP specifies, the fault is Android-Auto-side, and the reporter is owed that answer
rather than a workaround.

**R1 answers it, and R1 needs no call, no second phone and no build.**

---

## 2. What is different about this round

- **The rig is the phone, and its identity matters.** `self-mode-bt-audio-round1` ran on the
  **Xiaomi POCO X3 (`surya`), Android 15, Android Auto `17.5.663204-release`**. Record what you
  actually have. If Android Auto has self-updated since, record the new `versionName`, because F7's
  behaviour is version-dependent and the reporters are on a much newer Pixel.
- **Verify the SIM before anything else, do not assume it.** R2 to R5 need a real call. Nothing on
  this channel has ever established that this phone has a usable SIM. R1 checks it, and if there is
  no service the call runs are **UNTESTABLE** and the round stops after R1, which is still the run
  that matters. Do not invent a substitute; there is no adb command that simulates an incoming
  cellular call without root or a privileged role.
- **The platform log tags need turning on, and the failure is silent.** Android Auto caches its log
  level at process start, so the `setprop` must be followed by a force-stop. The `getprop` afterwards
  is not optional: a `setprop` that did not take produces a capture that looks clean and proves
  nothing.
- **`-v threadtime,uid,usec` is required, not cosmetic.** The `uid` column is the entire attribution
  and the default format omits it.
- **Package and namespace differ.** The package is `com.andrerinas.headunitrevived`, the classes are
  `com.andrerinas.openheadunit.*`. Mixing them is the fastest way to a zero-hit grep and a wrong
  conclusion. The Pixel-family default dialer is `com.google.android.dialer` with in-call activity
  `com.android.incallui.InCallActivity`; guides that say `com.android.dialer` give zero hits.
- **`shared_prefs` was app-owned on this phone** last round (`Uid: (10268/u0_a268)`), unlike the
  root-owned directory on the head-unit rig. Re-`stat` it and say what you find.
- **A fresh install re-runs onboarding** and rewrites resolution, DPI and codec. Last round's install
  needed an uninstall first (`INSTALL_FAILED_UPDATE_INCOMPATIBLE` against a differently-signed
  release build). Diff `settings.xml` against a fresh backup afterwards and state the delta.
- **Use `hur-wifi-test-scripts/set_pref.sh`** for the phone-side settings write. It was added last
  round for exactly this. `set_hu_pref.sh` and `set_hu_prefs.sh` are hardcoded to the head-unit rig's
  rooted shell and will not work here.
- **Grep every capture with `-a`** (§7a), without exception.

---

## 3. Settings keys

| Key | Type | Value | Runs |
|---|---|---|---|
| `log-level` | int | `2` (INFO) | all |

```
<int name="log-level" value="2" />
```

`log-level=2` is deliberate and sufficient, and it is the *highest* level that still carries every
line this round needs, which is what the template asks for. Verified at `ea7aa7e0`: all six decisive
app lines are `AppLog.i` and none sits behind an `AppLog.LOG_VERBOSE` guard. Nothing here needs
VERBOSE, and VERBOSE on this phone would cost evidence by wrapping the ring buffer.

Change nothing else. In particular do **not** touch `enable-audio-sink`. Self Mode ignores it
(settled in `self-mode-bt-audio-round1-results.md`) and changing it only adds a variable.

---

## 4. Capture setup, to run before every capture

```bash
PHONE=<serial>
PKG=com.andrerinas.headunitrevived
GH=com.google.android.projection.gearhead

adb -s $PHONE shell logcat -G 64M
adb -s $PHONE shell setprop log.tag.GH VERBOSE
adb -s $PHONE shell setprop log.tag.CAR VERBOSE
adb -s $PHONE shell setprop log.tag.Telecom VERBOSE
adb -s $PHONE shell setprop log.tag.InCallController VERBOSE
adb -s $PHONE shell getprop log.tag.GH            # MUST print VERBOSE. If empty, stop and say so.
adb -s $PHONE shell am force-stop $GH             # it caches its level at process start

adb -s $PHONE shell am force-stop $PKG
adb -s $PHONE logcat -c
stdbuf -oL adb -s $PHONE logcat -b all -T 1 -v threadtime,uid,usec > rN.txt &
```

`log.tag.*` is volatile and lost on reboot; re-run the block if the phone reboots mid-round.

Record the pids once per capture. They are how the three sides are told apart in one buffer:

```bash
adb -s $PHONE shell pidof $GH $PKG system_server com.google.android.dialer
```

---

## 5. The lines that decide every run

Verified with `grep -F` against `ea7aa7e0` for the app lines, and against AOSP source for the
platform lines. The `START u0` format is quoted from a real capture on this project's own rig.

**The attribution line, the single most important string in the round:**

```
ActivityTaskManager: START u0 {... cmp=com.google.android.dialer/com.android.incallui.InCallActivity} from uid <U>, pid <P>
```

`from uid <U>, pid <P>` names who *asked* for the launch, not who logged it. `uid 1000`, meaning
`system_server`, says a full-screen intent fired it. The Dialer's own uid says the Dialer started it
itself after Telecom bound its service.

**Telecom's decision chain:**

```
Telecom: InCallController: onCallAdded
Telecom: InCallController: defaultDialer: [ComponentInfo{com.google.android.dialer/...}]
Telecom: CarSwappingInCallServiceConnection: carmodechange: false => false
Telecom: InCallController: changeCarModeApp:
Telecom: InCallController: handleSetAutomotiveProjection: packageName=com.google.android.projection.gearhead
Telecom: InCallController: handleCarModeChange: not a valid InCallService
Telecom: InCallServiceBindingConnection: Attempting to bind to InCall
```

**Android Auto's own projection decision, tag `CAR.SYS`.** Verbatim from the shipping APK's
`Ljff;` (F11). These are the highest-value lines in the round, because they are Android Auto stating
what it did rather than us inferring it:

```
Successfully set automotive projection.
Failed to set automotive projection.
Security exception requesting automotive projection.
starting car mode settings connType=
Successfully released automotive projection.
Failed to release automotive projection.
Security exception attempting to release projection.
end car mode and restore settings
```

The first three are mutually exclusive and one of them fires per attempt.
`starting car mode settings connType=` appears **only** on the fallback path, so seeing it at all
means the projection request did not succeed, and the `connType` number it carries is the thing worth
writing down.

**Android Auto's side**, present only if it is bound as the in-call UI:

```
GH.InCallService: onCallAdded
GH.ICarCall: onCallAdded
GH.CallManager: getCalls:
```

**The foreground handoff, exact and unambiguous:**

```
am_on_top_resumed_lost_called
am_on_top_resumed_gained_called
```

**Our side**, all `AppLog.i` and all present at `ea7aa7e0`:

```
AapProjectionActivity: onPause                                        AapProjectionActivity.kt:950
AapProjectionActivity: onResume                                       AapProjectionActivity.kt:984
Throughput over                                                       VideoDecoder.kt:1997
Media Sink Setup Request:                                             AapControl.kt:82
BT MAC Address is empty. Skip bluetooth service                       ServiceDiscoveryResponse.kt:221
SelfMode: AA 17.4+ detected. Connecting directly to Headunit Server   AapService.kt:2744
```

**Grep set**, to run on every capture:

```bash
L=rN.txt
grep -anE "ActivityTaskManager: +START u0" $L
grep -anE "Telecom: +(InCallController|InCallServiceBindingConnection|CarSwappingInCallServiceConnection)" $L
grep -anE "handleSetAutomotiveProjection|handleCarModeChange|changeCarModeApp|carmodechange" $L
grep -anE "automotive projection|starting car mode settings connType=|end car mode and restore" $L
grep -anE " (GH|CAR)\.[A-Za-z0-9_.]+ *:" $L
grep -anE "notification_enqueue|fullScreenIntent|FullScreenIntent" $L
grep -anE "am_on_top_resumed_(gained|lost)_called" $L
grep -anF "AapProjectionActivity: on" $L
grep -anF "Throughput over " $L
```

---

## 6. Runs

### R0, gate

Build `ea7aa7e0` and install it on the phone. Record:

- APK md5, and confirm which APK is live (`md5sum` of `pm path`).
- Unit tests, count and result.
- `adb -s $PHONE shell dumpsys package $GH | grep versionName`.
- `run-as $PKG stat shared_prefs`, owner uid.
- `settings.xml` before and after install, and the delta onboarding wrote.

**PASS**: the build and the tests are clean and the live APK md5 matches what was built. A failed
build or test run **stops the round**.

### R1, the premise check, and the run this round exists for

**No call. No second phone. Do this before anything else and report it even if the rest is
untestable.**

Bring Self Mode up and let it settle for about 20 s, with the capture already running:

```bash
adb -s $PHONE shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity
sleep 5
adb -s $PHONE shell am start -a com.andrerinas.openheadunit.ACTION_START_SELF_MODE
```

Confirm the projection is actually the resumed activity, then take the dumps **while it is
projecting**:

```bash
adb -s $PHONE shell dumpsys activity activities | grep -E "mResumedActivity|topResumedActivity"
adb -s $PHONE shell dumpsys telecom              > r1-telecom.txt
adb -s $PHONE shell dumpsys uimode               > r1-uimode.txt
adb -s $PHONE shell cmd telecom get-default-dialer
adb -s $PHONE shell cmd telecom get-system-dialer
```

And the SIM and telephony premise:

```bash
adb -s $PHONE shell dumpsys telephony.registry | grep -iE "mServiceState|mCallState|mSimState"
adb -s $PHONE shell getprop gsm.sim.state
adb -s $PHONE shell getprop gsm.operator.alpha
```

**Report these four things, as quoted text:**

1. The whole `CarModeTracker:` block from `r1-telecom.txt`: `Current car mode apps:`, where each
   entry is either `[PROJECTION SET]` or a numeric priority, and the `Car mode history:` list of
   `enterCarMode:` and `setAutomotiveProjection:` entries.
2. The `Car Swapping ICS` block, with its `Dialer:` and `Car Mode:` sub-lines.
3. `mCarModeEnabled` from `r1-uimode.txt`.
4. Whether `com.andrerinas.headunitrevived` appears anywhere in `CarModeTracker`.
5. **Every `CAR.SYS` line from §5's projection block in the capture**, with timestamps. This is
   Android Auto's own answer to the round's question and it costs one grep:

   ```bash
   grep -anE "automotive projection|starting car mode settings connType=" rN.txt
   ```

   If `Successfully set automotive projection.` is present, Android Auto took the lock in Self Mode.
   If `starting car mode settings connType=` is present instead, it did not and fell back to car
   mode, and the `connType` value is the finding. If **neither** appears, the projection code was
   never reached at all for this session type, which is a third and equally decisive answer.

**PASS** means all four are captured while `dumpsys activity activities` shows
`com.andrerinas.headunitrevived/com.andrerinas.openheadunit.aap.AapProjectionActivity` resumed. This
run cannot FAIL on content, because **either answer is a result**:

- `com.google.android.projection.gearhead` present in `Current car mode apps` means Android Auto does
  claim projection in Self Mode.
- Absent, or `handleCarModeChange: not a valid InCallService` in the log, means it does not, and that
  is the finding.

**Expected, and stated so a surprise is visible**: `mCarModeEnabled=true`, because we set it, while
`com.andrerinas.headunitrevived` appears **nowhere** in `CarModeTracker` (F3). If our package *does*
appear there, F3 is wrong and that changes the whole picture. Say so loudly.

**If the SIM checks show no service, stop here.** R2 to R5 are **UNTESTABLE**. Record R0 and R1 and
report; that is a complete and useful round.

### R2, incoming call while projecting. The behavioural point of the round.

Self Mode projecting, capture running, screen on and unlocked. Someone calls the phone from a second
handset. Let it ring **5 s**, answer, hold **5 s**, hang up. Do not touch the phone otherwise.

**While the call is still up**, take:

```bash
adb -s $PHONE shell dumpsys telecom                 > r2-telecom-during.txt
adb -s $PHONE shell dumpsys activity activities     > r2-activities-during.txt
adb -s $PHONE shell dumpsys notification --noredact > r2-notification-during.txt
```

Then let the capture run about 30 s past the hang-up, so a late `onResume` is visible.

**PASS** means the capture contains a Dialer `START u0` line with a readable `from uid`, or contains
no Dialer `START` at all while `dumpsys activity activities` shows the projection still resumed
throughout. Both are results. The run FAILs only if the capture is unusable: no `uid` column, tag
props not set, or the buffer wrapped.

**What a PASS would look like if nothing interesting happened**: the projection stays resumed, no
Dialer `START`, no `onPause`. That is the "it worked correctly once" case the reporter describes, and
it is a real outcome, but only if the throughput numbers in §8 point 5 show the session was actually
live and receiving. Pair them; a quiet capture from a dead session proves nothing.

### R3, outgoing call placed from the projected Android Auto UI

Same setup. Place a call from the Android Auto dialer on the projected screen. Hold 5 s, hang up.

**PASS** means the capture attributes any Dialer `START u0`. F8 predicts one appears with **no**
`notification_enqueue` for the Dialer anywhere in the window. Record whether that holds, because it
separates the outgoing mechanism from the incoming one cleanly.

### R4, outgoing call placed from the phone's own Dialer

Same setup, but place the call from the phone's Dialer app instead:

```bash
adb -s $PHONE shell am start -a android.intent.action.CALL -d tel:<number>
```

This one **is** scriptable. It separates "Android Auto routed the call badly" from "the Dialer always
does this regardless of who started it".

**PASS** means attributed as above.

### R5, control: the same incoming call with us out of the picture

```bash
adb -s $PHONE shell am start -a android.intent.action.VIEW -d "headunit://exit"
sleep 3
adb -s $PHONE shell am force-stop $PKG
adb -s $PHONE shell dumpsys uimode | grep mCarModeEnabled     # must read false before proceeding
```

Fresh capture, no Open Headunit, Android Auto not projecting. Same incoming call, same 5 s, answer,
5 s, hang up.

**PASS** means captured and attributed. This is the diff baseline for R2: whatever the Dialer does
here is its normal behaviour, and only what R2 does *differently* is about us.

**The `mCarModeEnabled` check is not optional.** Car mode is enabled on every service creation and
disabled only in `onDestroy`, which a force-stop does not reliably reach, so it survives into the
control run and quietly invalidates it. One `headunit://exit` cycle clears it. If it still reads
`true`, say so and treat R5 as INCONCLUSIVE rather than reporting a contaminated control.

---

## 7. Do not re-run

Settled by F1 to F11. Spending a run on any of these buys nothing:

- Making our `enableCarMode` matter, or testing whether disabling it changes the Dialer (F3).
- Anything involving `setShowWhenLocked` or the keyguard-occluded path (F10).
- A `NotificationListenerService` cancel of the Dialer's call notification. It is `setOngoing(true)`,
  and call notifications bypass user blocking entirely while `isInManagedCall()` is true.
- Turning heads-up notifications off system-wide to "stop the popup". It backfires by design:
  removing the heads-up path sends SystemUI down a full-screen-intent branch instead, so the Dialer
  takes the *whole* screen. Do not try it, and do not recommend it.
- Any attempt to make Open Headunit the car-mode in-call UI (F6). The permission is
  `signature|privileged|role`.

---

## 8. Report back

The numbers that decide what happens next. Numbers, not adjectives.

1. **The `from uid` on every Dialer `START u0`, per run**, with the pid table, so `uid 1000` versus
   the Dialer's own uid is unambiguous.
2. **Which of the three `CAR.SYS` outcomes fired**: `Successfully set automotive projection.`,
   `starting car mode settings connType=<N>` with the number, or neither. This is the round's
   headline, and it comes from Android Auto itself.
3. **Whether `com.google.android.projection.gearhead` appears in `CarModeTracker` while projecting**,
   yes or no, with the block quoted. This is point 2's downstream confirmation; the two must agree,
   and if they disagree say so, because that is a finding in its own right.
4. **Seconds between `AapProjectionActivity: onPause` and the next `onResume`**, per call, and
   whether an `onResume` happens *at all* without anyone touching the phone.
5. **`Throughput over 5000ms: rendered=..., fed=...` across the call window, paired with the same
   line from the 30 s before the call.** The pairing is the point. Android Auto sends no video when
   nothing on screen animates, so a low count during a call means nothing on its own. Without the
   before number this measurement cannot distinguish "the call stopped the stream" from "the screen
   was idle", a confusion that has cost this project two rounds already.
6. **From `r2-notification-during.txt`**: does the Dialer's incoming-call notification carry a
   `fullScreenIntent`, and which SystemUI decision string appears in the log for it.
7. **What we announced**: the full `Media Sink Setup Request:` set, and whether
   `BT MAC Address is empty. Skip bluetooth service` fired.

Anything noticed in passing goes in the last section, as always. On this channel that section has
produced more findings than some rounds' runs.
