# post-beta1-latency-instruments, round 2 brief

Round 1 was **BLOCKED after R0**. Three obstacles stopped the hardware runs, none of them a fault in
the instrument code. Two of the three are now fixed and **merged into `main`** as PR #898. This round
is round 1's runs, re-issued against a candidate that can actually hold a session, with the banner
dependency that caused most of the trouble removed rather than worked around.

## 1. Build and baseline

**Candidate:** `fix/post-beta1-latency-instruments` @ `3200f004`
**Baseline:** `main` @ `4e5be786`

```bash
git fetch fork
git checkout fix/post-beta1-latency-instruments
git log --oneline -6            # expect 3200f004 at the tip, six commits above main
```

**History was rewritten several times since round 1, and the branch has a new base.** It was briefly
stacked on `fix/post-beta1-self-mode-and-log-probe`; that branch is now merged into `main` as PR #898,
so the two prerequisite commits have dropped out and the branch is six commits on `main` again. Any
local checkout from round 1 or from earlier this week is orphaned; fetch and reset rather than pulling.
The superseded tips are tagged `pre-stack-latency-20260827`, `pre-main-3fbbb2d2-latency` and
`pre-main-4e5be786-latency`; the last rebase was verified tree-identical, so nothing was lost.

The six commits:

| Commit | Component | Justified by |
|---|---|---|
| `58a96337` | Video: `presented=`, per-session pipeline sizing, overlay reason | R1, R6 |
| `7ac647c9` | Video: low-latency capability read, surface-teardown attribution, settings row moved | R1, R5 |
| `e931ea47` | Log capture: 8 MB roll, `debug=` banner | R7 |
| `baf007b4` | WiFi Direct: 5 GHz band capability | **nothing this round, see §3** |
| `ea0092f3` | AAP: inbound byte-rate monitor, narrow-band advice | R1, R8 |
| `3200f004` | Video: exception triage, decode-latency instrument, ladder keys | R1, R2, R3, R4 |

**No baseline APK is needed this round.** The instruments do not exist on `main`, so every run below is
candidate-only, and R0's baseline figure is already known: `main` carries **770 tests** since PR #898,
which added `SelfLaunchTimeoutPolicyTest` (5 cases) to the 765 measured at `78689a96`. Do not spend a
build on it.

## 2. What this is and why it exists

Unchanged from round 1 and repeated here so this brief stands alone. Three open issues about a picture
that periodically stops being smooth have been argued for weeks without anyone being able to say where
the time goes, because the app measures almost nothing between "a frame arrived" and "a frame was
shown", and two of the numbers it does print are misleading.

- **`rendered=` is not what the driver sees.** It counts every buffer released with `render=true`,
  while `presented=` is counted once per output pass. See §3 for what round 1 measured about the gap,
  because round 1's brief stated it wrongly and this round should not repeat the error.
- **Nothing measured the decode delay**, so no low-latency key could ever be shown to work. A
  `configure()` that does not throw proves the key cost nothing, not that the component acted on it,
  and the format cannot be read back for the answer: ACodec reports its own bookkeeping rather than
  re-asking the component, so an ignored key round-trips exactly like an honoured one. The only
  evidence available is behavioural, which is what `decodeLatency=` on the throughput line now is.
- **The decoder threw away MediaCodec's own failure classification.** `CodecException` has said since
  API 21 whether a failure is transient or recoverable; every exception went into one catch and the
  third in a row bought a full release and recreate. On a unit with one hardware decoder instance that
  is the most expensive response available, and it costs a GOP of waiting for a keyframe on top.
- **Two format keys were removed for a reason that does not hold.** `KEY_PRIORITY` and
  `KEY_OPERATING_RATE` were pulled because a rejected key takes the session. The evidence behind that
  was `codec does not support config priority (err -1010)`, a line ACodec logs on its way to returning
  OK: `setPriority` and `setOperatingRate` both swallow the component's refusal, and `configureCodec`
  adds a second `err = OK; // ignore error` at each call site. Verified in `android-9.0.0_r61`. They
  are back, on the ladder's optional rungs.
- **MediaTek has two more levers** (`vdec-no-record`, `use-clearmotion-mode`) that are the only things
  found anywhere that attack a component's pipeline depth below API 30, where the official
  `KEY_LOW_LATENCY` does not exist.

Almost none of this changes behaviour on its own. The round is about whether the instruments read
correctly on real hardware, and whether the keys are worth keeping.

## 3. What is different about this round

### The two fixes now in `main`, and what they clear

Two of round 1's three obstacles were defects in `fix/post-beta1-self-mode-and-log-probe`, whose own
round 2 confirmed both fixed on all three of these devices. That branch merged as PR #898, so those
fixes are in the baseline now and the candidate inherits them rather than carrying them.

**Obstacle 1, the log-access consent dialog on every app launch, is gone.** `Settings.logSource`
resolved its default by spawning `logcat` and blocking, on the main thread, at every process start,
even when the stored preference already answered. Round 1 saw that dialog fire 55 times across
scripted relaunches, once take SystemUI down, and once leave the app alive with no log output for 90
seconds. The probe is removed. Measured on all three devices: **zero app-owned `logcat` with capture
off, and `MainActivity` resumes in about one second.**

**Obstacle 3, the permission trampoline, is gone.** `AAPermissionTrampolineActivity.onActivityResult`
treated any return under a second as a failure, which is exactly what a healthy permission check
produces, so legacy Self Mode bring-up failed and fell back repeatedly and kept dropping D-HU into
Native AA mode 3 mid-observation. Measured after the fix: legacy Self Mode forms in **1352 ms and
2033 ms** on the two AA-17.3 devices, with no teardown.

**Obstacle 2, D-MOTO's Gearhead error screen, did not recur.** `post-beta1-self-mode` round 2 watched
for `CarErrorDisplay` on D-MOTO across both arms and counted **zero**, with a 90 second session held
each time. D-MOTO is testable this round. That round could not fully separate the two candidate
explanations for round 1's occurrence, so if the error screen returns here, record it as an
observation against that thread rather than a FAIL of this branch.

### The banner dependency is dropped, not worked around

Round 1's R1, R2 and R7 all keyed a PASS condition off the `LogExporter: session |` banner, which
requires `log-capture-enabled=true`, which genuinely spawns `logcat` and raises the system consent
dialog. That part is **unchanged by PR #898 and will still happen**, because that fix removed the
speculative probe, not a capture the user asked for.

So this round does not use the banner outside R7. Template §0 forbids a verdict that depends on a
human being present, and tapping a consent dialog is exactly that. Everything the banner was being
read for is available without it:

| Was read from the banner | Read instead from |
|---|---|
| `debug=none` / `debug=lowLatency:on` | the `settings.xml` readback taken before launch, per template §1, quoted verbatim in the results |
| `video=codec: fps: view: forceSw:` | the same readback, plus the `findBestCodec:` line for what was actually selected |
| `device= board= api=` | `adb shell getprop ro.product.model ro.product.board ro.build.version.sdk` |

The behavioural evidence is better than the banner anyway: `optionalKeys=` on the `Configuring
decoder:` line says what the ladder did, where the banner only echoed a preference back.

`log-capture-enabled=true` is set for **R7 only**, where the app's own capture is the thing under
test. The consent dialog is expected there and is part of that run's setup, not a defect.

**A note for R7's operator, from the `post-beta1-self-mode` round 2.** On D-HU's ROM "Allow one-time
access" does not persist even for the lifetime of one `logcat` exec: the dialog re-fires roughly once
a second and each re-fire kills the capture process. Tapping Allow for the whole session produced a
truncated file with no banner. Tapping Allow only for the first few seconds and then leaving `logcat`
undisturbed produced a valid file. Do the latter.

### The 1.6x ratio: round 1's brief was wrong, and round 1's measurement was right

Round 1's brief asserted that `rendered` runs about 1.6x `presented` on a healthy unit. Round 1
measured the ratio at **about 1.00 on all three devices**. The measurement is correct and the brief
was wrong, and the reason matters for how this round reports the number.

`framesPresented` is incremented once per output pass. `framesRendered` is incremented for the same
buffer **and** for `alsoRendered`, the earlier buffers a pass releases, and that branch is only
reached when a pass finds more than one buffer ready. So a pipeline running one deep gives a ratio of
exactly 1.00 by construction. The 1.6x came from a queue running two deep, measured against a
TextureView's own displayed-frame line over 619 samples, and both numbers are real.

**So the ratio is a load signal, not a constant, and this round must not be read as confirming or
refuting 1.6x.** Report it paired with the load that produced it: `fed`, `rendered`, `inputWait` and
`skipped` from the same windows. A run whose ratio is 1.00 in every window has established that the
queue never went two deep, which is a fact about that device under that load, not a broken instrument.
Round 1's sessions were roughly 45 second smoke tests; R1 below asks for five minutes with the screen
genuinely busy, which is the condition where a second ready buffer is likelier.

### Still untestable, said up front

- **`f2ed91b7`, the 5 GHz band capability, is UNTESTABLE and expected to be.** It is read in
  `WifiDirectManager.createQuietGroup`, which only runs when a WiFi Direct P2P group is created, and
  Self Mode never creates one. Do not attempt it, do not substitute a run. It is covered by
  `NativeGroupBandPolicyTest` (19 cases) and `WifiP2pOperatingChannelPolicyTest` (18), which R0 runs.
- **R3, the MediaTek rung, is UNTESTABLE unless the rig has gained a MediaTek device.** Round 1 found
  only two distinct components across the three: `c2.unisoc.avc.decoder` on D-HU and
  `c2.qti.avc.decoder` on **both** D-POCO and D-MOTO. Check the component names again in R1 before
  concluding this, but do not substitute a run if it holds.
- **D-MOTO adds a second Gearhead version and a portrait panel, not a third component.** Keep it in
  the round for that, but do not expect it to answer the ladder-coverage question that R3 exists for.

### Self Mode facts that change how runs are set up

Carried forward from round 1, all still current.

- Self Mode connects to Android Auto's own head unit server on `127.0.0.1:5277`. A connection to that
  port abandoned before the AAP version exchange **wedges the server permanently** until Android Auto
  is force-stopped. If a Self Mode attempt fails to reach a session, force-stop
  `com.google.android.projection.gearhead` before retrying, and say in Setup notes how many times you
  had to.
- Because the connection is a socket, `isWirelessSession` is **true** in Self Mode though no radio is
  involved. R8 exists because of that.
- **Android Auto ends a Self Mode session on any USB port change.** A HID device does it as surely as
  an audio dongle, and `GH.DHUService` is the author, not us. All three devices are on USB for adb,
  which is fine as long as nothing is plugged or unplugged mid-run. A session death with `GH.DHUService`
  in the log is not a FAIL of anything here.
- **Car mode is never cleared by a force-stop.** `enableCarMode` runs on every service creation,
  `disableCarMode` only in `onDestroy`. Use `headunit://exit` **before** the force-stop, not after.
- **Installing the APK can wipe settings**, after which onboarding re-picks its own defaults, typically
  H.264 and TextureView. Round 1 did not see this happen with `adb install -r -d`, but read the
  settings back after every install regardless: two devices silently differing on `view:` or `codec:`
  would make the R1/R2 comparison meaningless.
- **Verify `run-as` works on each device.** It did on all three in round 1. One
  `run-as $PKG cat shared_prefs/settings.xml` per device settles it; if it fails on a device, report
  that device's runs UNTESTABLE rather than using the UI.

**Log level:** `log-level=2` (INFO) carries every line this round needs except R7's. All the decisive
lines are unguarded `AppLog.i` or `AppLog.w`. Do not use VERBOSE except in R7. On Self Mode both the
projecting and the receiving side log into one buffer, so VERBOSE costs evidence rather than buying it.

## 4. Settings keys this round needs

Written into `shared_prefs/settings.xml` with the app stopped, per template §1. Types matter.

| Key | Type | Element |
|---|---|---|
| `log-level` | int | `<int name="log-level" value="2" />` |
| `fps-limit` | int | `<int name="fps-limit" value="60" />` |
| `view-mode` | int | `<int name="view-mode" value="0" />` (SURFACE 0, TEXTURE 1, GLES 2) |
| `video-codec` | string | `<string name="video-codec">Auto</string>` |
| `debug-video-low-latency` | boolean | `<boolean name="debug-video-low-latency" value="true" />` |
| `force-software-decoding` | boolean | `<boolean name="force-software-decoding" value="true" />` |
| `software-video-decoder` | int | `<int name="software-video-decoder" value="0" />` (0 = device MediaCodec, 1 = bundled FFmpeg) |
| `log-capture-enabled` | boolean | `<boolean name="log-capture-enabled" value="true" />` (**R7 only**) |

To turn a boolean **off**, delete the element rather than writing `false`, so the default applies.

**Read the file back before every launch and quote it in the results.** With the banner dropped, this
readback is the only record of what each run was configured with.

## 5. The lines that decide every run

All copied from `3200f004` and verified with `grep -F` against that tip.

**The throughput line** (`VideoDecoder.kt:2089`), once per 5 s window, INFO:

```
Throughput over 5000ms: rendered=N (Nfps), fed=N (Nfps), dropped=N, skipped=N, concealed=N, inputWait=Nms, enqueueWait=Nms, codec=NAME, presented=N (Nfps), decodeLatency=Nms p95=Nms (N frames)
```

`decodeLatency=` and `presented=` are the two new fields and are always last, in that order. A
component that does not carry timestamps through prints `decodeLatency=unreadable (N frames)` instead.

**The configure line** (`VideoDecoder.kt:1696`):

```
Configuring decoder: <component> for WxH, max-input-size=NKB, memory=..., queue=N frames, optionalKeys=<tier>[ keys]
```

`<tier>` is exactly one of `none`, `realtime`, `vendor`, `vendor+reorder`, `low-latency`.

**The component selection line**, which names both candidates and the choice:

```
findBestCodec: hw=<name>, sw=<name>, preferHardware=<bool>, selected=<name>
```

This is what makes R4 checkable: with `force-software-decoding=true` it must read `preferHardware=false`
and select the `sw=` name.

**The ladder's three outcome lines** (`VideoDecoder.kt:1706`, `1722`, `1727`):

```
Decoder accepted the format only with optionalKeys=<tier>
Decoder rejected optionalKeys=<tier>: <message>
Decoder configure abandoned: the surface went away mid-configure. This is not a rejection of optionalKeys=<tier>.
```

**The capability line** ends with `featureLowLatency=<true|false|unknown> featureAdaptivePlayback=<bool>`.

**The exception triage line** (`VideoDecoder.kt:2420`), one of:

```
Codec exception in output thread - the component says it was only busy: <message>
Codec exception in output thread - the component can be reconfigured: <message>
Codec exception in output thread - the component says it cannot recover: <message>
```

**The inbound rate line**, once per **30 s** window (`InboundRateMonitor.WINDOW_MS = 30_000L`), INFO:

```
AapTransport: inbound rate over 30000ms: video=NkB/s (N msgs), audio=NkB/s (N msgs), other=NkB/s (N msgs)
```

**The pipeline sizing line**, only when the capacity changes:

```
VideoDecoder: feed queue resized N -> M frames (Nms at Nfps)
```

**The overlay line** (`AapProjectionActivity.kt`):

```
Hiding reconnecting overlay - <reason>
```

with `<reason>` one of `frames resumed`, `reconnect timed out`, `the session ended`.

**The log-capture lines**, for R7 (`LogExporter.kt:204`, `232`):

```
--- continued from the previous log file ---
LogExporter: Logcat capture produced 0 bytes (<err>). Automatically switching to Direct to file (APPLOG_FILE).
```

**The narrow-band advice** (WARN), which should **not** appear this round:

```
This unit has no 5 GHz band, so this session runs over 2.4 GHz, ...
```

## 6. Runs

### R0: build and unit-test gate. Run first, once.

```bash
./gradlew :app:assembleGithubDebug
./gradlew :app:testGithubDebugUnitTest
```

- **PASS:** both succeed with **816 tests, 0 failures**. `main` is 770 since PR #898 and these six
  commits add 46. Do not build the baseline.
- **Round 1 needed a manual one-line patch here and this round must not.** `InboundRateMonitorTest`
  asserted `2000L` where the production monitor correctly yields `1000L`. That literal is fixed on the
  candidate. If that test fails again, the branch you have is not `3200f004`.
- **FAIL:** any compile error, or any failure other than a clean 816. Quote it verbatim and stop the
  round; it is more useful than any hardware result.

Install the same APK on all three devices and record its md5 once.

### R1: Self Mode baseline. Per device. **This is the point of the round.**

Settings: `log-level=2`, `fps-limit=60`, `view-mode=0`, `video-codec=Auto`,
`debug-video-low-latency` **absent**, `force-software-decoding` **absent**, `log-capture-enabled`
**absent**. Read the file back and quote it.

```bash
adb -s <dev> shell am force-stop com.andrerinas.headunitrevived
# write settings, verify by reading the file back
adb -s <dev> shell am start -n com.andrerinas.headunitrevived/com.andrerinas.openheadunit.main.MainActivity
adb -s <dev> shell am start -a com.andrerinas.openheadunit.ACTION_START_SELF_MODE
```

Run **5 minutes with something animating on the projected screen the whole time.** A parked map sends
almost no video and Android Auto drops the stream on a static screen, which would make every number
below meaningless. Keep a map moving, or scroll a list. This is also the condition under which the
output queue can go two deep, which §3 explains is what the `rendered`/`presented` ratio depends on.

- **PASS**, all of:
  1. The settings readback matches what was written, and no `optionalKeys=` tier other than `none`
     appears on the `Configuring decoder:` line.
  2. Every `Throughput over` line carries both `presented=` and `decodeLatency=`.
  3. At least 8 `inbound rate over 30000ms` lines in the 5 minutes, with `video=` non-zero.
  4. No `Decoder rejected optionalKeys=` line anywhere.
- **FAIL:** any of those absent, or `decodeLatency=` present on a line where it should not be.

**Report as numbers, per device:** median and p95 `decodeLatency` across the run; the mean
`rendered`/`presented` ratio **with the mean `fed`, `rendered`, `inputWait` and `skipped` from the
same windows**; `video=` kB/s from the inbound rate lines; the component name from `findBestCodec:`;
the full capability line; and the three `getprop` values from §3.

**Pair every count with its reachability number.** A window with `rendered=0` cannot evaluate
`decodeLatency`, so report `rendered` beside it. If `decodeLatency=unreadable` dominates on a device,
that is a real finding about that component and not a FAIL, so report the count.

**If the instrument did nothing, this run still passes**, because it is also the regression guard for
`presented=`. Say which of the two it was, and read §3 before writing the ratio up: a run where
`presented` equals `rendered` in every window means the queue never went two deep, which is a
measurement, not a fault.

### R2: low latency on. Per device. **The A/B that decides whether the keys ship.**

Identical to R1 in every respect except `debug-video-low-latency=true`. Same duration, same content on
screen, because the comparison is worthless if the screen was doing something different.

- **PASS**, all of:
  1. The settings readback shows the key set.
  2. The `Configuring decoder:` line shows a tier other than `none`, and its key list is recorded
     **verbatim**.
  3. The session decodes and the picture is watchable for the full 5 minutes.
  4. If any `Decoder rejected optionalKeys=` line appears, it is followed by a lower rung that
     configures successfully. A rejection is the ladder working, not a failure.
- **FAIL:** the session does not decode, or the ladder reaches `optionalKeys=none` and still fails, or
  the app crashes.

**Report:** median and p95 `decodeLatency` against R1 on the same device, as two numbers per device.

**Say what a PASS means here if the change did nothing.** If R2's latency matches R1's within a
millisecond or two, the component accepted the key and ignored it. That is the answer the round was run
to get, and it is worth as much as a win. Do not go looking for a different setting that moves the
number.

### R3: the MediaTek reorder rung. Only on a device whose component name contains `.mtk.` or `mediatek`.

Round 1 found no such component on any of the three. Confirm from R1's `findBestCodec:` lines, and if
it still holds, mark **UNTESTABLE** and move on. Do not substitute.

Same settings as R2.

- **PASS:** the first `Configuring decoder:` line reads `optionalKeys=vendor+reorder` and its key list
  contains `vdec-lowlatency`, `vdec-no-record` and `use-clearmotion-mode`; the session decodes.
- **Also a PASS, and the more interesting one:** `Decoder rejected optionalKeys=vendor+reorder`
  followed by `Decoder accepted the format only with optionalKeys=vendor`. That is the rung falling
  back to the configuration already known to work, which is exactly what it is there for. Quote both
  lines with timestamps.
- **FAIL:** the ladder falls all the way to `none`, or the picture does not decode on the
  `vendor+reorder` rung while it did on `vendor`.

### R4: positive control for the two AOSP keys. Per device.

The control that makes the new behaviour appear by settings alone, with no second build.
`force-software-decoding=true` plus `software-video-decoder=0` selects the device's **software**
MediaCodec component, whose name matches no vendor family, so the ladder has no vendor spelling to
offer and must fall to the `realtime` rung, which did not exist before this branch.

Settings: R2's, plus `force-software-decoding=true` and `software-video-decoder=0`.

Run 2 minutes. The picture may be poor; that is not what is being measured.

- **PASS:** `findBestCodec:` reads `preferHardware=false` and selects the `sw=` name; `Configuring
  decoder:` shows `optionalKeys=realtime` with a key list containing `priority` and `operating-rate`;
  and **no** `Decoder rejected optionalKeys=realtime` line follows. That last part is the whole point:
  it is the on-hardware confirmation that these two keys cannot fail a configure.
- **FAIL:** a rejection on the `realtime` rung. If that happens, quote the message verbatim, because it
  would refute the AOSP source reading in §2 and the keys come straight back out.

### R5: a surface torn down mid-configure is not a rejected key. Once, on D-POCO.

With a Self Mode session live and decoding, press Home, wait 5 seconds, return to the app. Repeat five
times.

- **PASS:** either no `Decoder configure abandoned` line at all, or, if one appears, it reads `the
  surface went away mid-configure` and there is **no** `Decoder rejected optionalKeys=` line within
  200 ms of an `onSurfaceDestroyed`.
- **INCONCLUSIVE, and expected:** the race is narrow. Five Home presses producing no abandon line at
  all means the path was not reached. Say so; it is not a FAIL.
- **FAIL:** a `Decoder rejected optionalKeys=` line whose message mentions the surface.

### R6: the overlay says why it was hidden. Once, on D-MOTO.

With a session live, run `adb shell am start -a android.intent.action.VIEW -d "headunit://exit"`.

- **PASS:** the capture contains `Hiding reconnecting overlay - the session ended` and does **not**
  contain `Hiding reconnecting overlay - frames resumed` anywhere after the exit.
- **FAIL:** `frames resumed` on the teardown.

Secondary observation for the same run: record whether `VideoDecoder: feed queue resized` appeared at
all this round, and the `queue=N frames` value from each device's `Configuring decoder:` line. The line
only fires when the capacity changes, so its absence is expected; the `queue=` values are what prove
the sizing was derived at session start.

### R7: a long capture keeps the tail, and still detects a ROM that refuses logcat. Once, on D-HU.

**This run covers code that has never executed.** Two changes touch the same block of
`launchLogcatPipe`: the segment roll from this branch, and the zero-byte auto-switch that came in with
PR #898. Resolving them left a form that exists only here: the pipe restarts onto `captureFile ?: file`
rather than `file`, and the auto-switch is guarded on `capturePreviousFile == null` so a capture that
has already rolled is never mistaken for one that produced nothing. `main` carries only one half, so
this run is the only thing that exercises the join. Both halves need to be seen working.

Set `log-level=0` and `log-capture-enabled=true` on this device only. Read §3's note on the consent
dialog before starting: tap Allow for the first few seconds only, then leave `logcat` alone. Run a Self
Mode session for **25 minutes** with the screen active, then export the log through the app's own
export.

- **PASS**, all of:
  1. The exported file contains lines timestamped within the last 2 minutes of the run.
  2. The file is between roughly 8 and 16 MB and contains `--- continued from the previous log file ---`.
  3. `LogExporter: Logcat capture produced 0 bytes` does **not** appear, and `log-source` still reads 0
     in `settings.xml` after the run. A roll must not be mistaken for a refused capture.
- **FAIL:** the export ends at minute 10 or so, which is the pre-fix behaviour of keeping the first
  16 MB; or the auto-switch fires and moves the device to `APPLOG_FILE`.

Restore `log-level=2` and clear `log-capture-enabled` on D-HU afterwards.

### R8: the narrow-band advice must not fire on a loopback session. Per device, read from R1's capture.

No separate run. Grep each R1 capture for `This unit has no 5 GHz band`.

- **PASS:** absent on all three, which is expected for any device with a 5 GHz radio.
- **Report as a finding, not a FAIL, if present on any device:** Self Mode is a socket to `127.0.0.1`,
  so `isWirelessSession` is true while no radio carries the session. Advice about a WiFi band on a
  loopback connection would be a real design gap, and this round is the first thing able to see it.
  Quote the line and name the device.

## 7. Do not re-run

- **The 5 GHz band capability work (`f2ed91b7`).** No P2P group exists in Self Mode. §3 explains.
- **Anything the `post-beta1-self-mode` thread settled.** Its round 2 covered the log probe, the launch
  watchdog and the permission trampoline on all three of these devices, R0 to R8, no FAIL, and the work
  is merged. It is baseline now, not something to re-verify. If one of them regresses it will show as a
  session that will not form, which every run below would catch anyway.
- **Anything about wireless mode 2 or 3, hotspot, or WiFi Direct.** No run in this round touches a
  radio path.
- **The audio sinks in Self Mode.** They are not announced there at all, which is settled and is not a
  fault of this branch. Ignore anything the capture says about audio sinks.
- **`#875`'s backpressure work and `#886`'s wireless attribution.** Both settled on other branches and
  neither is under test here.

## 8. Report back

The numbers that decide the shipping question:

1. **Per device: R1 and R2 median and p95 `decodeLatency`, side by side.** This decides whether the
   low-latency keys are kept, dropped, or defaulted on. Six numbers total.
2. **Per device: the component name and its `optionalKeys=` tier under R2.** This decides whether the
   ladder's name matching covers real hardware or only the two components it was written against.
3. **R4's verdict on all three devices.** A single rejection on the `realtime` rung takes `KEY_PRIORITY`
   and `KEY_OPERATING_RATE` back out.
4. **Per device: the mean `rendered`/`presented` ratio, with the `fed`, `rendered`, `inputWait` and
   `skipped` that produced it.** Round 1 measured 1.00 on all three across short sessions. §3 explains
   why that is a load signal rather than a refutation, and why the paired numbers are what make it
   readable.
5. **R7's three conditions individually.** It is the only run covering the merged log-capture code.

Also report, briefly: whether `run-as` worked on each device, how many times Android Auto had to be
force-stopped to get a Self Mode session, whether the consent dialog appeared at any point outside R7,
and anything in the captures this brief did not ask about. That last section has produced more real
findings than some rounds' runs.
