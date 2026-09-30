# bring-up-status-pill-and-poke-readiness: round 5 brief

**Candidate:** `fork/testing/status-pill-plus-aac` @ `8c6d90e4`
**Baseline:** none needed. See §1.
**Gate:** 1643 tests / 0 failures
**Read `TESTING-TEMPLATE.md` first.** This brief only says what is different about this round.

---

## 0. What this is and why it exists

**Two branches, one APK, one round.** Round 4 passed every run on `7f439d4e`, and the status-pill
branch has moved three commits since. A second branch, the AAC audio work, has never been on
hardware at all. Both are merged into one testing branch so the round costs one build and one
install; the parts below are independent and a failure in one says nothing about the other.

**The bring-up no longer vetoes its own recovery.** `8c8cdeb3`. `startNativeAaQuietHost()` claimed
the create window *before* checking whether WiFi was on, and the claim was retaken on every pass, so
on a unit whose WiFi was off `isCreatingGroup` stayed true for as long as the app ran. Every
Bluetooth auto-start arrival then answered `the Native AA group is still being created, so it is
left to answer.` and did nothing, which is the one path that rearms the mode. Round 1 met this on
D-POCO and recorded it as a setup note: *"the app's own `WiFi is disabled but needed for Native AA.
Attempting to enable...` never took"*, and the round needed two manual `svc wifi enable` calls to
get moving. The claim is now taken after the radio is known on, the per-bring-up attempt counter
means the line is said once rather than every ten seconds, and `WifiLauncherNative` waits for this
unit's own access point to be down before asking for a group, because a single-radio chip cannot
host one while its soft AP still holds the radio.

**A wake that does nothing says so.** `688c8d0b`. `awaitPokeSlot` returned in silence when the
handshake servers were not running, so a *Wake this device* the user pressed left no line at all.
It now names the state it found.

**Auto-start is offered, not assumed.** `c5180124`. A completed handshake used to write the peer
into the auto-start trigger list, so the app began launching itself for a user who never asked.
`AutoStartOfferPolicy` replaces that: with exactly one phone paired the question is asked once, and
with two or more the stored trigger is cleared instead, because driver selection is the surface that
settles who drives. The answer is recorded when the question is *put*, so a dialog dismissed by a
rotation does not come back.

**AAC audio, the whole branch.** "Use AAC Audio" has shipped as an experimental toggle and no capture
this channel holds has ever had it on: every audio sink in every log is
`Media Sink Setup Request: 1`, which is PCM. The branch announces AAC by default for a wireless
session on a radio with no 5 GHz band, and takes four defects out of the decoder that a code review
found: compressed bytes written to the track as PCM after a failed decoder init, a codec error never
recovered from, the output format never checked against the track, and every input buffer timestamped
zero. What this round decides: whether "(Experimental)" comes off the setting's description, and
whether the default ships.

## 1. Build and baseline

```bash
git fetch fork
git checkout testing/status-pill-plus-aac   # 8c6d90e4
```

That branch is `main` at `cfafaeff` with `feat/bring-up-status-pill-and-poke-readiness`
(`c5180124`) and `fix/aac-default-on-narrow-band` (`2afd4a81`) merged in that order. It carries no
commits of its own. Both feature branches are on `fork` under those names if a part has to be
bisected afterwards.

**No baseline APK.** Every run is graded on lines `main` does not have, or on a control that is a
settings change on this same build (Part D's AAC4).

Build with `hur-wifi-test-scripts/build_hur.sh` per §5. Identity, in order of strength: the build
stamp (`"commit":"8c6d90e4..."` from `ACTION_QUERY_STATE`, `Building from commit: 8c6d90e4` in the
build log, no `-dirty`), then the DEX symbols `AutoStartOfferPolicy` and `AacDecoderRecoveryPolicy`,
neither of which any earlier build carries
(`unzip -p <apk> 'classes*.dex' | strings | grep -c AacDecoderRecoveryPolicy`), then both md5s.

The **same APK** is installed on D-HU (Parts A, B, C1, AAC1, AAC2, AAC4) and on D-POCO (C2, AAC3).

## 2. What is different about this round

**The candidate now carries `main`'s steering-key work.** `main` moved five commits since round 4's
`f5c2e986`, and the merge brings in the BYD panel and steering-wheel key support, which also moved
`carKeyManager` out of its old owner and into `AapService`. Round 4's APK had none of it. No run
here targets it; if something in the session lifecycle behaves unlike round 4, that is the first
place to look, and it belongs under "Anything the brief did not ask about" rather than in a verdict.

**Three things in this round cannot be exercised on this rig, and none of them is a failure.**

- **The 2.4 GHz-only AAC default.** The gate is
  `NarrowBandProfilePolicy.caps(supports5Ghz, wirelessSession, capEnabled)`, and `supports5Ghz`
  comes from `WifiManager.is5GHzBandSupported()`, which no setting overrides. Both rig units create
  5 GHz groups in every capture this channel holds, so `caps()` is always false here and
  `[ServiceDiscovery] AAC audio announced by the 2.4 GHz-only cap (Use AAC Audio is off)` cannot
  print. Part D forces `use-aac-audio=true` instead, which reaches the identical announcement and
  the identical decode path by the other arm of `useAac = userChoice || caps(...)`. The cap arm
  itself stays with the JVM tests, and AAC1 to AAC4 must not be read as evidence about it either way.
- **The WiFi-enable retry ladder.** `MAX_WIFI_ENABLE_ATTEMPTS = 5` and the give-up warning are on
  the below-API-29 branch only. D-HU is API 34 and D-POCO is API 30, so both take the branch that
  says once that this Android does not let an app switch WiFi on and returns. A1 grades that branch,
  which is the one the rig can reach; the ladder is JVM-covered.
- **USB host bring-up.** `host_connected=false` on D-HU, as in round 4. Nothing in this round needs
  it.

**Part D needs a scriptable audio proxy, and has one.** This rig has no speaker, so nothing is
confirmed by ear and no step here says "listen". What decides every Part D run is the agreement
between two lines: the codec type the phone names in `Media Sink Setup Request:` on a given channel,
and the `isAac=` value on that channel's `AudioDecoder.start:` line. Corroboration comes from the
phone's own `dumpsys media_session` playback state and from the `inbound rate over` byte counters.

**Music is started by a tap into the projected video, never by a media key.** Measured across a
whole round on this rig: `KEYCODE_MEDIA_PLAY_PAUSE` sent to the phone, relayed through the head
unit, and a fresh player relaunch all failed to open an audio channel, while tapping Android Auto's
own play control works every time, because that control is drawn into the video and the tap goes
back to the phone over the touch channel. On D-HU's 1440x720 layout that is
`adb -s <hu> shell input tap 272 657`. Expect the first Play tap of a cold session to flap
`PLAYING`/`PAUSED` for a few seconds; take timings after it settles.

**A role swap gets `headunit://exit` first, never a bare force-stop**, and the swap is confirmed with
`dumpsys wifip2p` showing no `GroupCreatedState` and no `192.168.49.1` on `p2p0`. Rounds 2 and 3
each lost a run to this.

## 3. Settings keys this round needs

As round 1 §3, with the differences below. All written with the app stopped, read back before
launch. Delete `native-preferred-device-mac` on both head units for the whole round.
`auto-start-bt-macs` is authoritative in `settings.xml` and re-copies itself to the device-protected
mirror on every launch, so set or clear it there and read **both** back in C1.

| Key | Type | D-HU | D-POCO | Why |
|---|---|---|---|---|
| `wifi-connection-mode` | int | `3` | `3` | Native AA |
| `log-level` | int | `2` | `2` | INFO. Every line in §4 is an unguarded `AppLog.i` or `.w` at this level. AAC1 alone raises it, see below |
| `auto-start-bt-macs` | StringSet | A1 and C1: `DC:B7:2E:5E:4E:59`; elsewhere empty | empty | the auto-start trigger, and the list the offer clears or writes. `AutoStartReceiver` reads the device-protected mirror and returns at once when it is empty, so A1 needs it set |
| `auto-start-offer-answered-macs` | StringSet | delete before C1 and C2 | delete before C2 | the once-only record; left in place the question never returns |
| `last-connected-native-mac` | String | leave | C2: `A0:46:5A:97:E4:95` | the phone the offer is about |
| `auto-enable-hotspot` | boolean | A2: leave default | leave | A2 raises its own soft AP with `cmd wifi start-softap` instead |
| `use-aac-audio` | boolean | AAC1, AAC2: `true`; AAC4: delete | AAC3: `true` | forces the AAC announcement on a 5 GHz-capable unit |
| `enable-audio-sink` | boolean | `true` | `true` | all three sinks announced |
| `static-audio-focus` | boolean | AAC1, AAC4: `false`; AAC2: `true` | AAC3: `false` | one AudioTrack per sink, against the shared mixer |
| `screen-orientation` | int | leave | C2: `2` (landscape) | forces the relaunch C2's crash guard is about |
| `audio-queue-capacity` | int | default (absent) | default (absent) | |

**AAC1 runs at `log-level` `0`**, VERBOSE, and only AAC1, for the single `RECV:` check at the end of §4.
That line is guarded by `AppLog.LOG_VERBOSE` and is simply absent at INFO; a round that greps it at
level 2 reports a clean result from a line that was never written. Everything else in Part D is read
at level 2, and AAC1's other greps are unaffected by the higher level.

## 4. The lines that decide every run

Copied from the branch and verified with `git grep -F` against `8c6d90e4`.

Bring-up, Part A:

```
WifiDirectManager: WiFi is off and this Android does not let an app switch it on.
WifiDirectManager: a Native AA group create is claimed (
WifiDirectManager: the claimed create window is released (
AapService: Bluetooth auto-start: the Native AA group is still being created, so it is left to answer.
AapService: Bluetooth auto-start: BtAutoStartActions(
WifiLauncherNative: waiting for this unit's hotspot to go down before creating the group.
WifiLauncherNative: this unit's hotspot had not gone down after 10s; creating the group anyway.
HotspotManager: the access point was still up
WifiDirectManager: startNativeAaQuietHost() requested. Removing old group if any...
status pill step:
Auto-connect: a phone is answering, taking the full screen.
```

Wake, Part B:

```
AapService: the Native AA handshake servers are not running
NativeAA: Manual poke requested for
NativeAA: not waking
```

Grep `NativeAA: not waking` as a prefix. The full line continues with the device name and the
reason, and the separator between them is a character that is easy to mistype.

Auto-start offer, Part C:

```
HomeFragment: more than one phone is paired, so the Bluetooth auto-start device is cleared.
HomeFragment: Bluetooth auto-start turned on for
```

Audio, Part D:

```bash
grep -E "Media Sink Setup Request: [0-9]+ on channel AUDIO" log.txt   # 1 = PCM, 2 = AAC-LC, 4 = AAC-LC-ADTS
grep -E "AudioDecoder.start:.*isAac=.*source=" log.txt                # source is "setup" or "setting"
grep -E "AAC Decoder started for" log.txt
grep -E "AapAudio: sink setup type" log.txt                           # a type that is not an audio codec
grep -E "Failed to init AAC decoder|AAC Codec Error" log.txt
grep -E "AudioTrackWrapper: rebuilding the AAC decoder|giving up on this sink" log.txt
grep -E "AudioTrackWrapper: no working AAC decoder, dropping this sink's frames" log.txt
grep -E "AudioTrackWrapper thread finished" log.txt                   # the drop count rides on this line
grep -E "AAC decoder output is|AAC Output Format Changed" log.txt     # the wrong-speed warning
grep -c "AAC Input Buffer timeout" log.txt
grep -E "inbound rate over" log.txt                                   # audio= kB/s, AAC1 against AAC4
grep -c "disabled due to previous underrun" log.txt
grep -oE "underrunframes=[0-9]+" log.txt                              # the API 28 substitute; expect absent here
grep -E "RECV: AUDIO[0-9]* .*type: 1 " log.txt | head                 # AAC1 only, needs log-level 0
```

`[ServiceDiscovery] could not evaluate the band audio codec:` is an `AppLog.d` and is absent at INFO
by design. It should never appear; if a Part D run looks wrong for no visible reason, re-run that one
at `log-level` `1` and check for it.

## 5. Runs

### R0: build and unit-test gate

Build, run the unit tests, install on both units with `adb install -r`. **PASS** requires all of:
1643 tests, 0 failures; the build stamp or the DEX grep proves `8c6d90e4` on both units; both
installed md5s match the one built. Report the count even when it matches, so a later reader can
tell a green gate from an unread one.

---

### Part A: bring-up, on D-HU with D-POCO as the phone

**P1: Native AA cold bring-up to projection.** As round 4, unchanged: the retreat from
`WAKING_PHONE` back to `WAITING_FOR_PHONE` is allowed, and `status pill step: hidden` is a FAIL
unless `Auto-connect: a phone is answering, taking the full screen.` came first. This is the
regression guard for the merge, not a new question. Report the ordered step list with timestamps and
the wall clock from the first step to `STARTING_PROJECTION`, as round 4 did, so the two are
comparable.

**A1: a bring-up with WiFi off does not wedge the mode.** This is the point of Part A. Write
`auto-start-bt-macs` = `DC:B7:2E:5E:4E:59` on D-HU first: `AutoStartReceiver` returns before it looks
at anything when that list is empty, and it reads the device-protected mirror, so launch once after
the write and confirm `BT Device connected:` and `MATCH! Starting AapService` can both appear.

Then the run proper. Clean-run protocol, `adb -s <hu> shell svc wifi disable`, confirm it is off,
launch `MainActivity`. Leave it 60 s. Then, with WiFi still off, raise a Bluetooth auto-start
arrival the way §7a says is the only one that works here: `adb -s <hu> shell svc bluetooth disable`
on the head unit, which self-reverts in about 14 s and produces a real `ACL_CONNECTED`. Wait for
`MATCH! Starting AapService`. Then `svc wifi enable` and leave it 60 s more.

**PASS** requires all of:

1. `WifiDirectManager: WiFi is off and this Android does not let an app switch it on.` appears, and
   appears **once per arming**, not once every ten seconds. Quote every occurrence with its
   timestamp; the count is the measurement. The pre-fix build said its own equivalent line, with
   different wording, on every credential refresh pass.
2. `WifiDirectManager: a Native AA group create is claimed` does **not** appear at any point while
   WiFi is off. Its presence there is the defect itself and a FAIL.
3. After `MATCH! Starting AapService`, the line
   `AapService: Bluetooth auto-start: the Native AA group is still being created, so it is left to
   answer.` does **not** appear. What should appear instead is
   `AapService: Bluetooth auto-start: BtAutoStartActions(` with a rearm in it. Quote the actual
   `BtAutoStartActions(...)` line in full.
4. After `svc wifi enable`, the mode recovers without a further gesture:
   `startNativeAaQuietHost() requested` followed by a `createGroup SUCCESS` line. A recovery that
   needs a second app launch is a FAIL, and say which gesture was needed.

**If the change did nothing**, condition 4 can still pass on its own, because enabling WiFi
eventually rearms through other paths. Conditions 2 and 3 are what separate the builds, so report
them even when the run passes overall.

**A2: the group waits for this unit's own access point to go down.** Clean-run protocol, then raise
a soft AP before launching:

```bash
adb -s <hu> shell cmd wifi start-softap OHU-TEST wpa2 testtest1234 -b 5
adb -s <hu> shell dumpsys wifi | grep -i SoftApInfo      # confirm it is actually up, and on which band
adb -s <hu> shell am start -n com.andrerinas.headunitrevived/com.andrerinas.openheadunit.main.MainActivity
```

**PASS** requires all of:

1. `WifiLauncherNative: waiting for this unit's hotspot to go down before creating the group.`
   appears.
2. `WifiDirectManager: startNativeAaQuietHost() requested` follows it, and a group is created.
3. `WifiLauncherNative: this unit's hotspot had not gone down after 10s; creating the group anyway.`
   does **not** appear, and neither does `HotspotManager: the access point was still up`.
4. Report the measured gap between the waiting line and `startNativeAaQuietHost() requested`. **A
   gap under about 200 ms means the teardown had already finished and the join was a no-op**, which
   is a PASS that proves nothing: say so, and re-run once with the access point raised immediately
   before the launch rather than seconds before it.

If `cmd wifi start-softap` refuses on this unit, that is UNTESTABLE, not a FAIL; record the refusal
text and move on. Restore the radio state afterwards either way.

---

### Part B: a wake that does nothing says why, on D-HU

**B1: the wake line.** The manager has to be stopped for this, and the one state that gets there is
arming with the head unit's Bluetooth off. Scripted, in one shell line, because the adapter reverts
in about 14 s:

```bash
adb -s <hu> shell am force-stop com.andrerinas.headunitrevived
adb -s <hu> shell svc bluetooth disable && \
  adb -s <hu> shell am start -n com.andrerinas.headunitrevived/com.andrerinas.openheadunit.main.MainActivity && \
  sleep 4 && \
  adb -s <hu> shell am broadcast -a com.andrerinas.openheadunit.ACTION_NATIVE_AA_POKE --es extra_mac "DC:B7:2E:5E:4E:59"
```

**PASS** requires both: `AapService: the Native AA handshake servers are not running` appears after
the broadcast, which is the poke trying to repair the manager first and failing because Bluetooth is
still off; and `NativeAA: not waking` appears after it, once, with the rest of the line naming a
state rather than being empty. Quote both lines in full.

**If the adapter had already reverted** and the handshake servers were running by the time the
broadcast landed, the log shows an ordinary poke instead and this run is **INCONCLUSIVE**, not a
FAIL. Say which it was, and give the timestamp of the `svc bluetooth disable` and of the broadcast
so the window is on the record. One retry is worth it; a second is not.

---

### Part C: the auto-start offer

**C1: two phones clear the stored trigger, on D-HU.** D-HU has D-POCO and D-MOTO bonded and both
classify as phones, so this is the rig's standing state. App stopped, write
`auto-start-bt-macs` = `DC:B7:2E:5E:4E:59`, delete `auto-start-offer-answered-macs`, read the file
back, launch, wait 30 s.

**PASS** requires all of:

1. `HomeFragment: more than one phone is paired, so the Bluetooth auto-start device is cleared.`
   appears.
2. `auto-start-bt-macs` reads back **empty** in `settings.xml`, and empty in the device-protected
   mirror as well. Read both; the mirror is written separately by the same code path.
3. No dialog appears. Take one screenshot 5 s after launch and attach it.
4. `driver candidates:` in the same capture reports 2 or more under `phone`. **If it reports 1, the
   run never reached the two-phone arm** and is INCONCLUSIVE whatever else it shows: quote that line.

**C2: one phone is asked, once, on D-POCO as head unit.** D-POCO has D-MOTO bonded as its only
phone; the other bonded devices classify as not-a-phone and unknown, which is what round 4 measured.
Send `headunit://exit` on D-HU first and confirm it holds no group. On D-POCO: app stopped, delete
`auto-start-bt-macs` and `auto-start-offer-answered-macs`, write
`last-connected-native-mac` = `A0:46:5A:97:E4:95` and `screen-orientation` = `2` (landscape), read
back, launch.

`screen-orientation` is what makes condition 2 mean anything: the relaunch under test is the one
`MainActivity.onCreate` provokes by applying a stored orientation the unit has to rotate into, and
D-POCO launches portrait. If the capture shows no second `onCreate` about a second after the first
resume, the guard was never exercised and condition 2 is **INCONCLUSIVE**, not a PASS. Say which.

**PASS** requires all of:

1. A dialog appears titled "Start automatically?" naming D-MOTO. Screenshot within 5 s of launch.
2. **No `FATAL EXCEPTION` anywhere in the capture, and the app's PID is the same before and after
   the stored-orientation relaunch that lands about a second after the first resume.** This is the
   failure that cost the driver selector two rounds: a queued dialog callback reaching for a context
   on a fragment that is already gone. Report the PID twice, taken from `pidof` at launch and 10 s
   later.
3. Tapping Yes writes the MAC: `HomeFragment: Bluetooth auto-start turned on for` appears, and
   `auto-start-bt-macs` reads back with that one address. Locate the button with
   `uiautomator dump` rather than guessing, and report the coordinate used.
4. Force-stop and launch again: the dialog does **not** come back, and
   `auto-start-offer-answered-macs` carries the MAC. Then delete that key, force-stop, launch again,
   and confirm it does **not** come back either, because `auto-start-bt-macs` is now set. Both
   halves are needed: the first proves the answered record works, the second proves the offer is not
   simply gated on it.

Restore `auto-start-bt-macs` to empty on D-POCO when Part C ends.

---

### Part D: AAC audio

Each run: connect in the usual Native pairing, then 3 minutes with music playing, one navigation
prompt (start a route on the phone before the run so guidance speaks during it), and one
notification (send a message to the phone from another device). Start the music with the tap into
the projected video, per §2. Then `headunit://disconnect`, then end the capture. Keep the exported
log with the capture.

`input tap 272 657` is D-HU's 1440x720 layout. For AAC3, where D-POCO is the head unit, the control
sits somewhere else and `uiautomator` cannot see it, because it is painted into the video rather
than being a view: take one screenshot of the projection, read the play control's centre off the
image, tap that, and report the coordinate used. If music cannot be started on D-POCO within a few
attempts, AAC3 is **INCONCLUSIVE**; the other three runs stand on their own.

| Run | Head unit / phone | `use-aac-audio` | `static-audio-focus` | What it exercises |
|---|---|---|---|---|
| AAC1 | D-HU / D-POCO | `true` | `false` | one AAC decoder per sink, three AudioTracks. `log-level` `0` |
| AAC2 | D-HU / D-POCO | `true` | `true` | the mixer: 16 kHz mono guidance decoded then resampled into the 48 kHz mix |
| AAC3 | D-POCO / D-MOTO | `true` | `false` | a second decoder vendor |
| AAC4 | D-HU / D-POCO | deleted | `false` | AAC1 again as PCM: the baseline for the byte comparison |

There is no lever to inject a decoder error on this rig, so the rebuild budget
(`MAX_REBUILDS = 3`, 10 s apart) is JVM-tested only. AAC1 to AAC3 expect **zero** rebuilds; that is a
regression guard, not a test of the budget, and the brief does not pretend otherwise.

**PASS**, for AAC1 to AAC3, requires all of:

1. **Agreement, per channel.** For every audio channel, the `isAac=` value on its
   `AudioDecoder.start:` line matches the codec type the phone named in
   `Media Sink Setup Request: <n> on channel <name>` for that same channel: type 2 or 4 means
   `isAac=true`, type 1 means `isAac=false`. Every one of those lines must read `source=setup`.
   `source=setting` on any of them means the wire was not consulted and is a FAIL. Tabulate the
   channels against the types.
2. `AAC Decoder started for 48000 Hz, 2 channels` once, and `for 16000 Hz, 1 channels` twice.
3. Zero of: `AAC Input Buffer timeout`, `AAC Codec Error`, `Failed to init AAC decoder`,
   `rebuilding the AAC decoder`, `giving up on this sink`, `no working AAC decoder`,
   `AAC decoder output is`, `AapAudio: sink setup type`.
4. `AudioTrackWrapper thread finished.` carries no drop count.
5. Zero underruns by both instruments, and the `inbound rate over ... audio=` figures reported.

**If every sink comes back type 1 despite the announcement**, the phone declined AAC. That is a
finding about the phone, not a failure of this branch: conditions 2 to 5 still apply, condition 1 is
satisfied by `isAac=false source=setup` throughout, and the run is **INCONCLUSIVE for the decode
path**. Say so plainly and quote the setup lines. Do not force a FAIL out of it, and do not re-run it
hoping for a different answer.

**AAC4 has no verdict.** It is the baseline: report its `inbound rate over ... audio=` figures beside
AAC1's so the saving is a number, and confirm its sinks are type 1 with `isAac=false`.

If the phone sent any type 1 message on an audio channel in AAC1's VERBOSE capture, quote the first one
with its size. That is a finding whatever the verdict.

## 6. Do not re-run

Round 4 settled these on `7f439d4e` and nothing in the three commits since touches them: P2 (the
pill does not cover the home controls), P3 (the retreat is the resting line), P4 (a different mode
gives a different sequence), P5 (three lines render), W2 (only a phone is poked), WB1 and WB1b (the
WiFi button is pill-first), round 4's Part C D1, D2 and D3 (the auto-disconnect grace, measured three times
at 9189, 9253 and 9613 ms against a 15000 ms constant). P6 stays UNTESTABLE: `host_connected=false`.

`AutoStartOfferPolicy`, `AacDecoderRecoveryPolicy`, `AudioSinkCodecPolicy`,
`NarrowBandProfilePolicy.useAac` and the pill rank rules are all pure and unit-tested; the runs above
are about the paths that reach them on a device, not about the decisions themselves.

## 7. Report back

The numbers that decide what ships:

- **A1 conditions 2 and 3**, as a yes or no with the lines quoted. They decide whether the bring-up
  fix goes to PR.
- **C2 condition 2**, the PID before and after the relaunch. A crash there blocks the offer.
- **Part D condition 1**, the channel-to-codec-type table for AAC1, and the `inbound rate over
  ... audio=` pair from AAC1 and AAC4. Those two decide whether "(Experimental)" comes off the setting
  and whether the default ships.
- The unit-test count from R0, and the Gearhead version on each phone, in Setup notes.
