# self-mode-call-foreground — round 1 results

**Candidate:** `origin/main` @ `ea7aa7e0` (`measure/3.3.0-beta1`)       **Baseline:** none (measurement round)
**APK md5:** `5a5a16bc00ab5539dbb9cb145f07cd40`
**Unit:** Redmi M2007J20CG / POCO X3 NFC (`surya_eea`), serial `4f4027e9`. Android Auto `17.5.663204-release`.
**Date:** 2026-08-25

## Setup notes

- `hur-wifi-test-scripts/` inventory: used `build_hur.sh` (builds + copies APK) and `run_unit_tests.sh`
  unchanged. `install_and_launch.sh` is hardcoded to the head-unit rig's serial (`HU=27870808938846`)
  and has no role in this thread, so R0's install used a plain `adb -s $PHONE install -r` instead —
  nothing to add to the directory for that, it's a one-line substitution of the brief's own command.
  Used `set_pref.sh` (phone-side, non-rooted) for `log-level=2`, no changes needed.
- The app was already installed at `versionName=3.3.0-beta1` from an earlier session today; `install -r`
  over it preserved `shared_prefs/settings.xml` byte-for-byte (diffed before/after, zero delta) — no
  onboarding rewrite, confirming `-r` (not uninstall/reinstall) was the right call here.
- `shared_prefs` is app-owned (`Uid: 10268/u0_a268`), same as `self-mode-bt-audio-round1`, unlike the
  head-unit rig's root-owned directory.
- **The one unscriptable setup step (§0) was genuinely needed**: the first attempt at R1 found
  `SelfMode: Headunit Server (127.0.0.1:5277) is NOT running.` and no session ever formed — the phone's
  Android Auto developer-settings toggle was not on despite being the visible foreground screen. Stopped,
  asked the user to tap "Start head unit server" by hand, then re-ran cleanly. Recorded here so the next
  round in this thread budgets for it rather than assuming a visible settings screen means the toggle is on.
- **Default dialer on this phone is `com.android.dialer` (AOSP Dialer), not `com.google.android.dialer`**
  the brief assumed from the Pixel reporters. Confirmed via `cmd telecom get-default-dialer` /
  `get-system-dialer`, both `com.android.dialer`. No `START u0` for either package appears in R1 (no call
  was placed), so this only matters for R2-R5's grep, not for R1's verdict.
- **No `Car Swapping ICS` block appears anywhere in this Android build's `dumpsys telecom` output.**
  Grepped for `swap` (case-insensitive) and `Dialer:` directly; zero hits. The brief's item 2 in §8
  could not be answered from this dump — likely a section that exists on the reporters' newer Android
  version/Telecom build and not on this one. Not a run blocker; §5's other three checks (`CarModeTracker`,
  `mCarModeEnabled`, the `CAR.SYS` line) all landed and independently confirm the same answer.
- **`am_on_top_resumed_gained/lost_called` never appears in the capture**, despite other `am_` EventLog
  tags (`am_proc_start`, 4 occurrences) confirming the event buffer itself was captured correctly
  (`-b all` did include it). Genuine absence on this OEM/Android-14 build, not a capture-format miss —
  worth knowing before a future round in this thread leans on that line as its foreground-handoff signal.

## R0 — gate

**PASS**

- Build: `assembleGithubDebug` clean, `com.andrerinas.headunitrevived_3.3.0-beta1_debug.apk`,
  md5 `5a5a16bc00ab5539dbb9cb145f07cd40`.
- Unit tests: **765/765**, 0 failures, 0 ignored, 100% (`testGithubDebugUnitTest`).
- Live APK on phone matches the built md5 exactly (`adb shell md5sum` on the installed base.apk).
- `dumpsys package com.google.android.projection.gearhead | grep versionName` →
  `17.5.663204-release` — same build the brief's teardown was done against.
- `run-as $PKG stat shared_prefs` → `Uid: (10268/u0_a268)`, app-owned.
- `settings.xml` before/after install: **zero delta** (diff empty).

## R1 — the premise check, and the run this round exists for

**PASS.** No call, no second phone, as specified. This is the round's headline result.

- Settings written: `log-level=2` (int), verified by readback.
- Radio state: not applicable, phone's own radios untouched (Self Mode is loopback).
- Discard-rule check (the one that applies to Self Mode): clean — `MATCH! Starting AapService`=0,
  a second `Handshake: SSL handshake complete`=0. One clean session, no re-run needed.
- `dumpsys activity activities` confirmed `com.andrerinas.headunitrevived/…AapProjectionActivity` as
  `topResumedActivity` while the dumps below were taken.

**The four things asked for, verbatim:**

1. **`CarModeTracker` block** (`r1-telecom.txt`):
   ```
   Current car mode apps:
     [PROJECTION SET] com.google.android.projection.gearhead
   Car mode history:
     ...
     2026-08-25T09:25:16.322313 - setAutomotiveProjection: packageName=com.google.android.projection.gearhead
   ```
   Only entries for `com.google.android.projection.gearhead`, alternating `setAutomotiveProjection` /
   `releaseAutomotiveProjection` across this and earlier sessions today. `com.andrerinas.headunitrevived`
   appears **nowhere** in this block or anywhere else in the telecom dump (`grep -n andrerinas
   r1-telecom.txt` → zero hits) — exactly as F3 predicted.

2. **`Car Swapping ICS` block**: absent from this build's `dumpsys telecom` entirely (see Setup notes).
   Not answerable this round; the other three checks below cover the same ground independently.

3. **`mCarModeEnabled`** (`r1-uimode.txt`): `mCarModeEnabled=true (carModeApps=0:com.andrerinas.headunitrevived`
   — **true, and our own package is the one holding it**, exactly as predicted (we call
   `enableCarMode(0)` unconditionally at `AapService.kt:828-838`).

4. **`com.andrerinas.headunitrevived` in `CarModeTracker`**: **no**, confirmed above.

5. **`CAR.SYS` projection verdict**, the round's actual question:
   ```
   08-25 09:25:16.315693 10193 17020 17020 I CAR.SYS : Successfully set automotive projection.
   ```
   Fires once, 6.5ms before Telecom's own `CarModeTracker: handleSetAutomotiveProjection:` line
   (09:25:16.322148) and `InCallController: handleSetAutomotiveProjection:` (09:25:16.316544) — all
   three agree on the same event within 7ms. `starting car mode settings connType=` (the fallback
   path) never appears. **The headline answer: Android Auto claims automotive projection for a local
   head-unit-server (Self Mode) session, the same as it would for a real wireless or USB head unit.**

**SIM/telephony premise** (checked, not part of R1's own PASS condition but gates R2-R5):
`mCallState=0` on both subscriptions; slot 2 (`gsm.sim.state=ABSENT,LOADED`, `gsm.operator.alpha=,Movistar`)
reads `mServiceState=IN_SERVICE`, `getRilVoiceRadioTechnology=14(LTE)`, `availableServices=[VOICE,SMS,VIDEO]`.
**A usable line exists.** R2-R5 are not UNTESTABLE on SIM grounds; they still need a second handset to
place/receive the actual call, which was not arranged for this pass of the round.

**Corroborating evidence the session was live, not just resumed:**
```
Media Sink Setup Request: 3 on channel VIDEO
Media Sink Setup Request: 1 on channel AUDIO2
BT MAC Address is empty. Skip bluetooth service
Throughput over 5005ms: rendered=205 (40fps), fed=207 (41fps), dropped=0 ... codec=c2.qti.avc.decoder
Throughput over 5003ms: rendered=151 (30fps), fed=151 (30fps), dropped=0 ...
Throughput over 5001ms: rendered=151 (30fps), fed=150 (29fps), dropped=0 ...
Throughput over 5001ms: rendered=150 (29fps), fed=150 (29fps), dropped=0 ...
Throughput over 5000ms: rendered=149 (29fps), fed=150 (30fps), dropped=0 ...
```
Steady 29-41fps for 25s post-handshake, `dropped=0` throughout, matching the `3 VIDEO + 1 AUDIO2`
sink set the `self-mode-bt-audio-round1` thread already documented for Self Mode.

**What this means for the round's central question (§1):** since Android Auto *does* claim automotive
projection here, F5's swap mechanism should be active and the Dialer's own `InCallService` should be
unbound during a call — no Dialer takeover should occur through the Telecom chain the brief describes.
If R2-R5 subsequently show the Dialer taking the foreground anyway, that appearance is coming from
**outside** the mechanism this brief investigated (F1-F11), and would be the round's real finding. This
makes R2 (the behavioural point of the round) more valuable now than before R1 ran, not less — R1
narrowed the search rather than closing it.

## R2 — incoming call while projecting, the behavioural point of the round

**PASS, and the round's real finding.** A real incoming call was placed from a second handset while
Self Mode's session from R1 was still live (no fresh SSL handshake — the same session throughout).

- Settings written: none (reused R1's `log-level=2`).
- Discard-rule check: clean — single session throughout, no reconnect.
- Pre-call baseline throughput (paired per §8 point 5), all `dropped=0`:
  ```
  Throughput over 5010ms: rendered=150 (29fps), fed=151 (30fps) ...
  Throughput over 5008ms: rendered=151 (30fps), fed=150 (29fps) ...
  Throughput over 5002ms: rendered=150 (29fps), fed=150 (29fps) ...
  ```
- `dumpsys telephony.registry` before the call: `mCallState=0` on both subscriptions.

**The Dialer did take the foreground, reproducing the reporters' bug exactly:**
```
09:30:46.235278  ActivityTaskManager: START u0 {cmp=com.android.dialer/com.android.incallui.InCallActivity} ... from uid 10126 (BAL_ALLOW_SAW_PERMISSION)
09:30:46.258442  AapProjectionActivity: onPause
```
`uid 10126` is `com.android.dialer` itself (confirmed via `cmd package list packages --uid 10126`) — the
Dialer started its own activity, the same shape F8 predicted for outgoing calls, now confirmed for
incoming too.

**Root cause, found live in Telecom's own log, and it is more specific than anything F1-F11 predicted.**
At the moment the call is added (09:30:29.386-.452), Telecom enumerates in-call-service candidates:
```
InCallController: found:ComponentInfo{.../CarProjectionInCallServiceImpl} isRequestedtype:true isEnabled:false ignoreDisabled:true hasCrossProfilePerm:false
```
`isRequestedtype:true` — Telecom **does** identify `CarProjectionInCallServiceImpl` (the car-mode-UI
component, F6) as the correct one to swap in. But `isEnabled:false` — **PackageManager reports the
component itself disabled** at this exact moment, despite R1 having just confirmed, on the same live
session, that Android Auto held automotive projection (`CAR.SYS: Successfully set automotive
projection.`). The swap Telecom performs is therefore never attempted; Telecom instead:
1. Binds `com.google.android.gms/...BankScamCallDetectionService` (spam-call detection, unrelated).
2. Binds gearhead's **`NonCarInCallServiceImpl`** (`isEnabled` — the non-car-mode variant, confirmed
   bound and receiving `onCallAdded` via `GH.LocalInCallService`/`GH.ICarCall`/`GH.CallManager`, which
   goes on to track the call's state and audio route internally — this is what puts Android Auto's own
   incoming-call UI on the projected screen, the reporters' "first popup").
3. Binds `com.android.dialer/...InCallServiceImpl` and explicitly marks it
   `trackCallingUserInterfaceStarted: com.android.dialer is now calling UX` (09:30:29.660) — the Dialer
   becomes Telecom's designated foreground UI **15 lines and under 300ms after ringing starts**, well
   before the user answers.

`CarProjectionInCallServiceImpl` never appears bound anywhere else in the capture (4 total mentions, all
four are this same `found: ... isEnabled:false` enumeration line, never an `onConnected`).
`NonCarInCallServiceImpl` appears 25 times, fully participating for the call's duration.

**So the "two popups" the reporters describe are two independent, uncoordinated UI paths, not one bug
with one cause:**
- Android Auto's own in-projection call UI comes from `GH.CallManager` tracking the call via
  `NonCarInCallServiceImpl` — that binding **does** succeed regardless of car-mode status, because
  `INCLUDE_SELF_MANAGED_CALLS` lets Gearhead observe any call without needing the exclusive swap.
- The phone's native Dialer takes the *entire physical screen* because Telecom's swap — the one and only
  mechanism that would have kept it suppressed (F5) — never triggers, because the specific component
  that would have been swapped in, `CarProjectionInCallServiceImpl`, is disabled at the OS/PackageManager
  level at call time, in this Self Mode session, despite Android Auto holding automotive projection.

**This is squarely on Android Auto's side, not fixable from this repo.** F1-F4 already ruled out
anything in this app's own code; R1 confirmed the projection lock is genuinely held; R2 now shows the
specific component-disabled state that breaks the swap regardless. There is no permission, API or
setting available to a third-party app that enables another app's own manifest component.

**The projection recovers on its own, with no involvement from this app's code — a genuinely positive
finding.** At hangup, the Dialer's own `InCallActivity` finishes itself:
```
09:31:10.223292  wm_finish_activity: [...InCallActivity, finish-activity]
09:31:10.224459  wm_pause_activity: [...InCallActivity, userLeaving=false, finish]
09:31:10.243118  wm_set_resumed_activity: [...AapProjectionActivity, resumeTopActivity - onActivityStateChanged]
09:31:10.299465  AapProjectionActivity: onResume
```
17ms between the Dialer's own pause and our activity resuming, with **no `START u0` for our package
anywhere in this window** — this is Android's normal back-stack semantics revealing the still-paused
(never destroyed, never removed) `AapProjectionActivity` the instant the covering activity finishes, not
a re-raise our code performs. F4 ("nothing re-raises the projection") is accurate about our code and
incomplete about the outcome: we don't need to re-raise it, because we were never evicted from the task
stack in the first place. Telecom's own ICS-unbind bookkeeping (`ICSBC#disconnect: unbinding after 42615
ms`) lags about 1.8s behind this, confirming the UI transition is driven by the activity stack, not by
Telecom's call-teardown timing.

- Discard-rule / contamination check: clean, one session throughout both R1 and R2.
- `dumpsys notification --noredact` during ring: the Dialer's notification carries
  `fullscreenIntent=PendingIntent{...com.android.dialer startActivity...}` — F9 confirmed. No
  `notification_enqueue` for the Dialer during the *ring* phase reached the point of a full takeover
  before the user actually answered (`InCallActivity` didn't launch until `RINGING -> ANSWERED`,
  09:30:46, roughly matching how long the ring itself was held this run) — the full-screen takeover is
  gated on answer, not on ring, in this capture.
- What we announced: unchanged from R1, `3 VIDEO + 1 AUDIO2`, no BT MAC.

## R4 — outgoing call placed from the phone's own Dialer

**PASS.** Confirms F8 and separates "Android Auto routed the call badly" from "the Dialer always does
this regardless of who started it" — it's the latter, same mechanism as R2, direction-independent.

**Deviation from the brief, noted rather than silently absorbed**: this was not triggered via `adb shell
am start -a android.intent.action.CALL -d tel:<number>` as written — no number for the second handset
was available, so the user placed the call by hand from the phone's own Dialer UI instead. This is
arguably a closer match to F8's actual claim (`InCallPresenter.startActivity(InCallActivity...)`
firing for a *user-placed* outgoing call) than a synthetic intent would have been, but it does mean the
run wasn't scripted as written. The call was already `DIALING` (`dumpsys telecom`:
`[Call id=TC@2, state=DIALING, ... handle=tel:********92]`) by the time this was noticed, so the capture
was started with `logcat -T 200` (recent history) rather than from a clean `-T 1`, and the 200-line
window did not reach back far enough through Gearhead's own log volume to catch the `onCallAdded`
enumeration sequence live — the same `isEnabled:false` line from R2 was **not** re-captured in this run's
logcat. The finding below is established from live `dumpsys` snapshots instead, which is not affected by
logcat buffer depth and is if anything more direct evidence of the bound-service *state* than a
historical log line would be.

**The Dialer took the foreground for the outgoing call too:**
```
topResumedActivity=ActivityRecord{263228192 u0 com.android.dialer/com.android.incallui.InCallActivity t4351}
```
(`r4-activities-during.txt`, taken while the call was up.)

**`dumpsys telecom`'s live "InCalls bound" list (`r4-telecom-during.txt`) shows exactly the same two
services as R2, and no third:**
```
ServiceConnections (InCalls bound):
  com.android.dialer/com.android.incallui.InCallServiceImpl              type: 2 (external)
  com.google.android.projection.gearhead/...NonCarInCallServiceImpl      type: 4 (self-managed)
```
`CarProjectionInCallServiceImpl` does not appear anywhere in this dump — zero hits for the string in the
whole file. Same shape as R2's binding sequence, reached independently (a live bound-service snapshot,
not a historical enumeration line), for an outgoing rather than incoming call. This is what the round
needed from R4: the `isEnabled:false` mechanism is not specific to incoming calls.

**Recovery, again with zero code from this app:**
```
09:41:42.029265  AapProjectionActivity: onResume
```
`topResumedActivity` 20s after hangup confirmed `AapProjectionActivity` back in front, same shape as R2's
recovery (Dialer's own activity finishing exposes the still-paused projection underneath).

## R3, R5 — not run, round closed here

R3 (outgoing from the projected AA UI, needs a screen tap on the projection rather than a pure adb
action) would most likely reproduce the same `isEnabled:false` mechanism R2 and R4 both already show
holds regardless of call direction. R5 (control, app absent) is the diff baseline for confirming the
Dialer's *unmodified* behaviour, which R2/R4 already establish indirectly — the bound-service list is
entirely Telecom/Dialer/Gearhead-internal and never touches our package either time. Given R2 and R4
between them identify and explain the mechanism precisely for both call directions, these were judged
lower-value than the rest of the round and the round was closed by user direction after R4, rather than
run for full completeness. Queued for a future pass if that completeness is ever wanted.

Self Mode was torn down cleanly at the end of the round (`headunit://exit`, then `force-stop`),
confirmed via `dumpsys uimode | grep mCarModeEnabled` → `mCarModeEnabled=false (carModeApps=` — no
residual car-mode state left on the phone.

## Round summary

The reporters' bug (discussion #883) is fully reproduced and root-caused, on this phone, for both call
directions. **Android Auto's `CarProjectionInCallServiceImpl` — the one component that would keep the
phone's Dialer suppressed during a call — reports `isEnabled:false` to Telecom at call time, even though
Android Auto genuinely holds automotive projection for the Self Mode session (proven independently in
R1).** This is an Android-Auto-side defect or intentional restriction; nothing in this repo's permissions,
settings or code can enable another app's disabled manifest component, so there is no fix available here.
The one actionable, positive result for the reporters: the projection recovers on its own within
milliseconds of hangup, with no user action needed and no code in this app doing it — Android's normal
back-stack semantics reveal the still-paused `AapProjectionActivity` the instant the Dialer's own activity
finishes itself.

## Anything the brief did not ask about

- The phone's `CarModeTracker` history in `r1-telecom.txt` shows nine prior
  `setAutomotiveProjection`/`releaseAutomotiveProjection` pairs for `gearhead` going back to
  2026-08-24T21:43, all from earlier sessions today — consistent with repeated Self Mode connect/exit
  cycles during setup, not a fault.
- `notification_enqueue` shows Android Auto's own `CAR_MODE` channel notification firing from
  `system_server` (uid 1000) independently of our own `headunit_service_v2` foreground notification —
  two separate ongoing notifications are live simultaneously during a Self Mode session, worth knowing
  if a future round in this thread instruments notification counts.
