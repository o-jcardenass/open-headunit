# post-beta1-latency-instruments, round 1 brief

## 1. Build and baseline

**Candidate:** `fix/post-beta1-latency-instruments` @ `42bd9820`
**Baseline:** `main` @ `71930d54`

```bash
git fetch fork
git checkout fix/post-beta1-latency-instruments
git log --oneline -6            # expect 42bd9820 at the tip, six commits above main
```

**History was rewritten.** The branch was nine commits and is now six, regrouped one commit per
component. The pre-rewrite tip is tagged `post-beta1-pre-compaction`; the trees are identical
(`git diff post-beta1-pre-compaction 42bd9820` is empty), so nothing was lost, but any local checkout
of the old branch is orphaned. Fetch and reset rather than pulling.

The six commits, and which run justifies each:

| Commit | Component | Justified by |
|---|---|---|
| `0d24328a` | Video: `presented=`, per-session pipeline sizing, overlay reason | R1, R6 |
| `b7f79401` | Video: low-latency capability read, surface-teardown attribution, settings row moved | R1, R5 |
| `e8931195` | Log capture: 8 MB roll, `debug=` banner | R1 (banner), R7 (roll) |
| `2e7286d0` | WiFi Direct: 5 GHz band capability | **nothing this round, see §3** |
| `f54ca65d` | AAP: inbound byte-rate monitor, narrow-band advice | R1, R8 |
| `42bd9820` | Video: exception triage, decode-latency instrument, ladder keys | R1, R2, R3, R4 |

## 2. What this is and why it exists

Three open issues about a picture that periodically stops being smooth have been argued for weeks
without anyone being able to say where the time goes. The reason is that the app measures almost
nothing between "a frame arrived" and "a frame was shown", and two of the numbers it does print are
misleading.

- **`rendered=` is not what the driver sees.** It counts every buffer released with `render=true`, and
  the surface holds one per pass, so it runs about 1.6x the displayed rate, measured over 619
  samples. Every fps figure quoted on three issue threads is a `rendered` figure. `presented=` is the
  real one.
- **Nothing measured the decode delay**, so no low-latency key could ever be shown to work. A
  `configure()` that does not throw proves the key cost nothing, not that the component acted on it,
  and the format cannot be read back for the answer: ACodec reports its own bookkeeping rather than
  re-asking the component, so an ignored key round-trips exactly like an honoured one. The only
  evidence available is behavioural, which is what `decodeLatency=` on the throughput line now is.
- **The decoder threw away MediaCodec's own failure classification.** `CodecException` has said since
  API 21 whether a failure is transient or recoverable; every exception went into one catch and the
  third in a row bought a full release and recreate. On a unit with one hardware decoder instance
  that is the most expensive response available, and it costs a GOP of waiting for a keyframe on top.
- **Two format keys were removed for a reason that does not hold.** `KEY_PRIORITY` and
  `KEY_OPERATING_RATE` were pulled because a rejected key takes the session. The evidence behind that
  was `codec does not support config priority (err -1010)`, which is a line ACodec logs on its way to
  returning OK: `setPriority` and `setOperatingRate` both swallow the component's refusal, and
  `configureCodec` adds a second `err = OK; // ignore error` at each call site. Verified in
  `android-9.0.0_r61`. They are back, on the ladder's optional rungs.
- **MediaTek has two more levers** (`vdec-no-record`, `use-clearmotion-mode`) that are the only things
  found anywhere that attack a component's pipeline depth below API 30, where the official
  `KEY_LOW_LATENCY` does not exist.

Almost none of this changes behaviour on its own. The round is about whether the instruments read
correctly on real hardware, and whether the keys are worth keeping.

## 3. What is different about this round

**Three devices, all in Self Mode, all on adb over USB.** That is deliberate and it is the point:
the configure ladder matches vendor keys on the **decoder component name**, so three devices means
three different components and three different rungs. One device cannot answer the question this
round exists for. Self Mode also puts the encoder and the decoder on the same SoC, which is the load
condition these instruments were written to describe.

Refer to the devices as **D-HU** (the head unit), **D-POCO** (Poco X3) and **D-MOTO** (the Motorola)
throughout. Record for each, from the session banner: `device=`, `board=`, `api=`, and the component
name from the first `Configuring decoder:` line. Those four facts are half the value of the round.

**`2e7286d0` is UNTESTABLE this round and that is expected.** The 5 GHz band capability is read in
`WifiDirectManager.createQuietGroup`, which only runs when a WiFi Direct P2P group is created. Self
Mode never creates one. Do not attempt it, do not treat its absence as a failure, and do not
substitute a run. It is covered by 9 JVM cases in `NativeGroupBandPolicyTest` and
`WifiP2pOperatingChannelPolicyTest`, which R0 runs.

**Self Mode facts that change how runs are set up:**

- Self Mode connects to Android Auto's own head unit server on `127.0.0.1:5277`. A connection to that
  port that is abandoned before the AAP version exchange **wedges the server permanently** until
  Android Auto is force-stopped. If a Self Mode attempt fails to reach a session, force-stop Android
  Auto (`com.google.android.projection.gearhead`) before retrying, and say in Setup notes how many
  times you had to.
- Because the connection is a socket, `isWirelessSession` is **true** in Self Mode even though no
  radio is involved. R8 exists because of that.
- **Android Auto ends a Self Mode session on any USB port change.** A HID device does it as surely as
  an audio dongle, and `GH.DHUService` is the author, not us. All three devices are on USB for adb,
  which is fine as long as nothing is plugged or unplugged and no USB mode is changed mid-run. If a
  session dies and the logcat shows `GH.DHUService`, that is not a FAIL of anything on this branch.
- **Car mode is never cleared by a force-stop.** `enableCarMode` runs on every service creation,
  `disableCarMode` only in `onDestroy`. The protocol force-stops between runs, so the device will sit
  in car-mode UI with no process left to undo it. Expect it, and if a device gets stuck use the deep
  link `headunit://exit` **before** the force-stop rather than after.
- **Installing the APK can wipe settings.** Onboarding then re-picks its own defaults, typically
  H.264 and TextureView. After every install, before every run, read back and record `video=` from
  the session banner on all three devices. Two devices silently differing on `view:` or `codec:` would
  make the R1/R2 comparison meaningless.
- **Verify `run-as` works on each device before planning around it.** On at least one rig in this
  channel `shared_prefs` is root-owned, and the app's own writes never reach disk while reads still
  look fine, so it fails silently in both directions. One `run-as $PKG cat shared_prefs/settings.xml`
  per device settles it. If it fails on a device, say so and report that device's runs as UNTESTABLE
  rather than changing settings through the UI.

**Runs expected to be INCONCLUSIVE, said up front:** R5 depends on a race that may simply not occur;
R3 does not exist unless one of the three devices has a MediaTek component; and any run on a device
whose component ignores the low-latency key will show no latency change, which is a **result**, not a
failure.

**Log level:** `log-level=2` (INFO) carries every line this round needs except R7's. All the decisive
lines are unguarded `AppLog.i`. Do not use VERBOSE except in R7. On Self Mode both the projecting and
the receiving side log into one buffer, so VERBOSE costs evidence rather than buying it.

## 4. Settings keys this round needs

Written into `shared_prefs/settings.xml` with the app stopped, per the template §1. Types matter.

| Key | Type | Element |
|---|---|---|
| `log-level` | int | `<int name="log-level" value="2" />` |
| `fps-limit` | int | `<int name="fps-limit" value="60" />` |
| `view-mode` | int | `<int name="view-mode" value="0" />` (SURFACE 0, TEXTURE 1, GLES 2) |
| `video-codec` | string | `<string name="video-codec">Auto</string>` |
| `debug-video-low-latency` | boolean | `<boolean name="debug-video-low-latency" value="true" />` |
| `force-software-decoding` | boolean | `<boolean name="force-software-decoding" value="true" />` |
| `software-video-decoder` | int | `<int name="software-video-decoder" value="0" />` (0 = device MediaCodec, 1 = bundled FFmpeg) |

To turn a boolean **off**, delete the element rather than writing `false`, so the default applies.

## 5. The lines that decide every run

All copied from `42bd9820` and verified with `grep -F` against the branch.

**The throughput line** (`VideoDecoder.kt:2088`), once per 5 s window, INFO:

```
Throughput over 5000ms: rendered=N (Nfps), fed=N (Nfps), dropped=N, skipped=N, concealed=N, inputWait=Nms, enqueueWait=Nms, codec=NAME, presented=N (Nfps), decodeLatency=Nms p95=Nms (N frames)
```

`decodeLatency=` and `presented=` are the two new fields and are always last, in that order. A
component that does not carry timestamps through prints `decodeLatency=unreadable (N frames)` instead.

**The configure line** (`VideoDecoder.kt:1697`):

```
Configuring decoder: <component> for WxH, max-input-size=NKB, memory=..., queue=N frames, optionalKeys=<tier>[ keys]
```

`<tier>` is exactly one of `none`, `realtime`, `vendor`, `vendor+reorder`, `low-latency`.

**The component selection line** (`VideoDecoder.kt`), which names both candidates and the choice:

```
findBestCodec: hw=<name>, sw=<name>, preferHardware=<bool>, selected=<name>
```

This is the fastest way to record each device's component, and it is what makes R4 checkable: with
`force-software-decoding=true` it must read `preferHardware=false` and select the `sw=` name.

**The ladder's three outcome lines:**

```
Decoder accepted the format only with optionalKeys=<tier>
Decoder rejected optionalKeys=<tier>: <message>
Decoder configure abandoned: the surface went away mid-configure. This is not a rejection of optionalKeys=<tier>.
```

**The capability line** (`VideoDecoder.kt:1639`, INFO, or `Decoder may not manage this stream:` at
WARN) ends with `featureLowLatency=<true|false|unknown> featureAdaptivePlayback=<bool>`.

**The exception triage line** (`VideoDecoder.kt:2421`):

```
Codec exception in output thread - the component says it was only busy: <message>
Codec exception in output thread - the component can be reconfigured: <message>
Codec exception in output thread - the component says it cannot recover: <message>
```

**The inbound rate line** (`AapTransport.kt:223`), once per 30 s window, INFO:

```
AapTransport: inbound rate over 30000ms: video=NkB/s (N msgs), audio=NkB/s (N msgs), other=NkB/s (N msgs)
```

**The session banner** (`LogExporter.kt:157`), first line of every capture:

```
LogExporter: session | build=... | device=... board=... api=... | video=codec:X fps:N resId:N view:NAME forceSw:B swDecoder:NAME | wifi=mode:N strategy:N | logLevel=NAME | debug=none
```

**The pipeline sizing line** (`VideoDecoder.kt:1254`), only when the capacity changes:

```
VideoDecoder: feed queue resized N -> M frames (Nms at Nfps)
```

**The overlay line** (`AapProjectionActivity.kt:1065`):

```
Hiding reconnecting overlay - <reason>
```

with `<reason>` one of `frames resumed`, `reconnect timed out`, `the session ended`.

**The narrow-band advice** (`ServiceDiscoveryResponse`, WARN), which should **not** appear this round:

```
[ServiceDiscovery] This unit has no 5 GHz band, ...
```

## 6. Runs

### R0: build and unit-test gate. Run first, once.

This branch has not been compiled. Nothing below is worth running until this passes.

```bash
./gradlew :app:assembleGithubDebug
./gradlew :app:testGithubDebugUnitTest
```

- **PASS:** both succeed. Report the runner's test total. The branch carries 46 more `@Test` methods
  than `main` (811 against 765), of which 15 are the two new decoder policies
  (`DecodeLatencyMonitorTest` 9, `DecoderExceptionPolicyTest` 6) and the rest are the ladder's
  restated cases plus the WiFi and AAP work.
- **FAIL:** any compile error. Quote it verbatim and stop the round, since it is more useful than any
  hardware result.

Install the same APK on all three devices and record its md5 once.

### R1: Self Mode baseline. Per device. **This is the point of the round.**

Settings: `log-level=2`, `fps-limit=60`, `view-mode=0`, `video-codec=Auto`,
`debug-video-low-latency` **absent**, `force-software-decoding` **absent**.

```bash
adb -s <dev> shell am force-stop com.andrerinas.headunitrevived
# write settings, verify by reading the file back
adb -s <dev> shell am start -n com.andrerinas.headunitrevived/com.andrerinas.openheadunit.main.MainActivity
adb -s <dev> shell am start -a com.andrerinas.openheadunit.ACTION_START_SELF_MODE
```

Run **5 minutes** with something animating on the projected screen the whole time. A parked map sends
almost no video and Android Auto drops the stream on a static screen, which would make every number
below meaningless. Keep a map moving, or scroll a list.

- **PASS**, all of:
  1. Session banner reads `debug=none`.
  2. Every `Throughput over` line carries both `presented=` and `decodeLatency=`.
  3. `optionalKeys=none` on the `Configuring decoder:` line, since the setting is off.
  4. At least 8 `inbound rate over 30000ms` lines in the 5 minutes, with `video=` non-zero.
  5. No `Decoder rejected optionalKeys=` line anywhere.
- **FAIL:** any of those absent, or `decodeLatency=` present on a line where it should not be.

**Report as numbers, per device:** median and p95 `decodeLatency` across the run; the mean
`rendered`/`presented` ratio; `video=` kB/s from the inbound rate lines; the component name; the full
capability line.

**Pair every count with its reachability number.** A window with `rendered=0` cannot evaluate
`decodeLatency`, so report `rendered` beside it. If `decodeLatency=unreadable` dominates on a device,
that is a real finding about that component and not a FAIL, so report the count.

**If the instrument did nothing, this run still passes**, because it is also the regression guard for
`presented=`. Say which of the two it was: a run where `presented` equals `rendered` in every window
means the surface never held a frame, which is a different device behaviour, not a broken instrument.

### R2: low latency on. Per device. **The A/B that decides whether the keys ship.**

Identical to R1 in every respect except `debug-video-low-latency=true`. Same duration, same content
on screen, because the comparison is worthless if the screen was doing something different.

- **PASS**, all of:
  1. Banner reads `debug=lowLatency:on`.
  2. The `Configuring decoder:` line shows a tier other than `none`, and its key list is recorded
     **verbatim**.
  3. The session decodes and the picture is watchable for the full 5 minutes.
  4. If any `Decoder rejected optionalKeys=` line appears, it is followed by a lower rung that
     configures successfully. A rejection is the ladder working, not a failure.
- **FAIL:** the session does not decode, or the ladder reaches `optionalKeys=none` and still fails, or
  the app crashes.

**Report:** median and p95 `decodeLatency` against R1 on the same device, as two numbers per device.

**Say what a PASS means here if the change did nothing.** If R2's latency matches R1's within a
millisecond or two, the component accepted the key and ignored it. That is the answer the round was
run to get, and it is worth as much as a win. Do not go looking for a different setting that moves
the number.

### R3: the MediaTek reorder rung. Only on a device whose component name contains `.mtk.` or `mediatek`.

If none of the three has one, mark **UNTESTABLE** and move on. Do not substitute.

Same settings as R2.

- **PASS:** the first `Configuring decoder:` line reads `optionalKeys=vendor+reorder` and its key list
  contains `vdec-lowlatency`, `vdec-no-record` and `use-clearmotion-mode`; the session decodes.
- **Also a PASS, and the more interesting one:** `Decoder rejected optionalKeys=vendor+reorder`
  followed by `Decoder accepted the format only with optionalKeys=vendor`. That is the rung falling
  back to the configuration that is already known to work, which is exactly what it is there for.
  Quote both lines with timestamps.
- **FAIL:** the ladder falls all the way to `none`, or the picture does not decode on the
  `vendor+reorder` rung while it did on `vendor`.

### R4: positive control for the two AOSP keys. Per device.

The control that makes the new behaviour appear by settings alone, with no second build.
`force-software-decoding=true` plus `software-video-decoder=0` selects the device's **software**
MediaCodec component, whose name matches no vendor family, so the ladder has no vendor spelling to
offer and must fall to the `realtime` rung, which did not exist before this branch.

Settings: R2's, plus `force-software-decoding=true` and `software-video-decoder=0`.

Run 2 minutes. The picture may be poor; that is not what is being measured.

- **PASS:** `Configuring decoder:` shows `optionalKeys=realtime` with a key list containing
  `priority` and `operating-rate`, and **no** `Decoder rejected optionalKeys=realtime` line follows.
  That last part is the whole point: it is the on-hardware confirmation that these two keys cannot
  fail a configure.
- **FAIL:** a rejection on the `realtime` rung. If that happens, quote the message verbatim, because it
  would refute the AOSP source reading in §2 and the keys come straight back out.

### R5: a surface torn down mid-configure is not a rejected key. Once, on D-POCO.

With a Self Mode session live and decoding, press Home, wait 5 seconds, return to the app. Repeat
five times.

- **PASS:** either no `Decoder configure abandoned` line at all, or, if one appears, it reads
  `the surface went away mid-configure` and there is **no** `Decoder rejected optionalKeys=` line
  within 200 ms of an `onSurfaceDestroyed`.
- **INCONCLUSIVE, and expected:** the race is narrow. Five Home presses producing no abandon line at
  all means the path was not reached. Say so; it is not a FAIL.
- **FAIL:** a `Decoder rejected optionalKeys=` line whose message mentions the surface.

### R6: the overlay says why it was hidden. Once, on D-MOTO.

With a session live, run `adb shell am start -a android.intent.action.VIEW -d "headunit://exit"`.

- **PASS:** the capture contains `Hiding reconnecting overlay - the session ended` and does **not**
  contain `Hiding reconnecting overlay - frames resumed` anywhere after the exit.
- **FAIL:** `frames resumed` on the teardown.

Secondary observation for the same run: record whether `VideoDecoder: feed queue resized` appeared at
all this round, and the `queue=N frames` value from each device's `Configuring decoder:` line. The
line only fires when the capacity changes, so its absence is expected; the `queue=` values are what
prove the sizing was derived at session start.

### R7: a long capture keeps the tail. Once, on D-HU.

Set `log-level=0` on this device only. Run a Self Mode session for **25 minutes** with the screen
active. Then export the log through the app's own export.

- **PASS:** the exported file contains lines timestamped within the last 2 minutes of the run, the
  file is between roughly 8 and 16 MB, and it reads as two segments joined.
- **FAIL:** the export ends at minute 10 or so, which is the pre-fix behaviour of keeping the first
  16 MB.

Restore `log-level=2` on D-HU afterwards.

### R8: the narrow-band advice must not fire on a loopback session. Per device, read from R1's capture.

No separate run. Grep each R1 capture for `[ServiceDiscovery] This unit has no 5 GHz band`.

- **PASS:** absent on all three, which is expected for any device with a 5 GHz radio.
- **Report as a finding, not a FAIL, if present on any device:** Self Mode is a socket to
  `127.0.0.1`, so `isWirelessSession` is true while no radio carries the session. Advice about a WiFi
  band on a loopback connection would be a real design gap, and this round is the first thing able to
  see it. Quote the line and name the device.

## 7. Do not re-run

- **The 5 GHz band capability work (`2e7286d0`).** No P2P group exists in Self Mode. §3 explains.
- **Anything about wireless mode 2 or 3, hotspot, or WiFi Direct.** No run in this round touches a
  radio path.
- **The audio sinks in Self Mode.** They are not announced there at all, which is settled and is not
  a fault of this branch. Ignore anything the capture says about audio sinks.
- **`#875`'s backpressure work and `#886`'s wireless attribution.** Both are settled on other branches
  and neither is under test here.

## 8. Report back

The three or four numbers that decide the shipping question:

1. **Per device: R1 and R2 median and p95 `decodeLatency`, side by side.** This decides whether the
   low-latency keys are kept, dropped, or defaulted on. Six numbers total.
2. **Per device: the component name and its `optionalKeys=` tier under R2.** This decides whether the
   ladder's name matching covers real hardware or only the two components it was written against.
3. **R4's verdict on all three devices.** A single rejection on the `realtime` rung takes
   `KEY_PRIORITY` and `KEY_OPERATING_RATE` back out.
4. **The mean `rendered`/`presented` ratio per device.** The 1.6x figure that three issue threads now
   depend on came from one unit. Three more say whether it generalises.

Also report, briefly: whether `run-as` worked on each device, how many times Android Auto had to be
force-stopped to get a Self Mode session, and anything in the captures that this brief did not ask
about. That last section has produced more real findings than some rounds' runs.
