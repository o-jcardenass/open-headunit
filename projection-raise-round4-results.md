# projection-raise — round 4 results

**Candidate:** fix/audio-sink-and-wireless-bring-up @ 952a0ffc       **Baseline:** none this round
**APK md5:** 756c578653c34552de038dd56724f581
**Unit:** D-HU (UNISOC MT50, Android 14), D-SAM (Galaxy Tab 4 7.0, Android 4.4.2 / API 19), D-HP (HP Slate 7 Plus, Android 4.2.2 / API 17), D-MOTO (Motorola edge 30 neo, Android 14), D-POCO (phone, Gearhead as installed)
**Date:** 2026-09-18

## Setup notes

- Gate: `./gradlew :app:testGithubDebugUnitTest` -> 2153 tests, 0 failures, matching the brief exactly (JUnit XML summed directly, not just the gradle console line).
- Scripts used: `build_hur.sh`, `run_unit_tests.sh`, `install_and_launch.sh` (SKIP_BUILD=1) for build/install; `set_pref_hostedit.sh` (D-SAM, D-HU, D-HP) and `set_hu_pref.sh`/`set_autostart_btmac.sh`/`set_prefs_runas.sh` (D-HU, rooted) for settings; no new script added.
- shared_prefs ownership checked before each stage: D-HU `u0_a174:u0_a174` (771), D-SAM `u0_a154:u0_a154` (771), D-HP `u0_a71:u0_a71` (771) - all correctly owned, matching round 3.
- **W3r deviation, not in the brief:** the precondition-building recipe (bounce D-HU's BT, let the phone reconnect its own profile) produced two contaminated captures before a clean one, for two distinct reasons, both worth recording:
  1. D-HU had `wifi-direct-stable-identity=true` with a persistent WiFi Direct group (netId 19, `DIRECT-G3-Navegadortz2`). D-POCO reconnected to it via WiFi Direct in ~6s, independent of Bluetooth/Gearhead state, completing a full AA session before the wake sequence could run. Fixed by setting `wifi-direct-stable-identity=false`, clearing `wifi-direct-group-name`/`wifi-direct-last-group-ssid`/`wifi-direct-last-group-bssid`/`wifi-direct-group-passphrase`, and deleting all of D-HU's OS-level saved P2P groups (`cmd wifip2p delete-saved-group <10..19>`, all owned by `Navegadortz2`).
  2. Even with a fresh group, D-MOTO's own Gearhead - which the brief asks to be disabled for this run - was not actually disabled the first two times: `dumpsys package`'s per-user `enabled=0` field means the DEFAULT (enabled) state, not disabled, and this was misread as "disabled" twice before catching it. The correct check is `pm list packages -d | grep <pkg>` (present = disabled). Separately, **`pm disable-user` does not kill a package's already-running processes** - Gearhead's `:projection`/`:shared`/`:car` processes stayed alive across the first disable attempt (from earlier testing) and kept the phone fully capable of completing a session; only a live process actually blocks one. `pm disable-user` was blocked by this session's own permission classifier (as round 3 found for D-POCO) and needed the operator's `!`; `pm enable` (restoring it afterward) was not blocked.
  - D-HU's settings were restored to their pre-round backup afterward, with `native-aa-wake-damage-verdict` re-cleared to `0` per the brief's own explicit post-run instruction (the backup itself carried the original stale `1`, which is what the round found at the very start too - see the "Anything the brief did not ask about" section).
- **D-HP's WiFi radio needed ~25-30s after `svc wifi enable` to actually come up** (`dumpsys wifi` read "Wi-Fi is disabled" through 4 consecutive 5s checks, then "enabled" on the 5th) - not documented before now, consistent with this Android 4.2.2 unit's already-known unreliable radio toggling. Round 3 likely inherited an already-joined radio; this round's D-HP had WiFi fully off at launch.
- D-POCO's Android Auto dev "Start head unit server" was already running on `:5277` from earlier setup, so no manual tap was needed this round (unlike round 3).
- **The host-side logcat capture died on D-HP twice during A1r/A2's ~19-minute capacity=0 run**, matching round 3's own documented risk exactly - caught each time by a liveness check polling every 60s and restarted with `>>` append, so no lines were lost (confirmed by the one-shot warning line landing cleanly with a coherent before/after trend around it).
- **A1r and A2 were run as one combined capture** rather than two separate sessions: both need `audio-queue-capacity=0` with the capture armed before the session starts, and A2's one-shot warning is a condition A1r's own capacity=0 half can reach on its own. This halved the rig time against running them as the brief's two separately-described setups.
- **No raw captures are attached to this round.** All five runs PASSed, every decisive line is quoted verbatim with its timestamp below, and round 3 set the precedent of not attaching bulk evidence for a narrative results file. The full captures (several 20-60MB logcat dumps, this rig's driver stack floods logcat) remain in the round's own working folder (`hur-wifi-test-scripts/round-projection-raise-r4/` on the test-rig machine) rather than bloating this branch; ask if a specific one is needed.
- Between-run resets used a full `settings.xml` restore from a pre-round backup on each touched unit (D-HU, D-SAM, D-HP), rather than clearing individual keys, to avoid leaving any unintended residue.

## Stage A — D-HU, D-MOTO (D-POCO present)

### W3r — The wake verdict, on a second unit

**PASS.**

- Settings written: `native-aa-wake-damage-verdict` cleared to `0` beforehand (repeatedly, see Setup notes); `last-connected-native-mac=A0:46:5A:97:E4:95`; `auto-start-bt-macs=A0:46:5A:97:E4:95` used transiently to build the HFP precondition, then cleared; `wifi-direct-stable-identity=false` and the four `wifi-direct-*` group-identity keys cleared as an unplanned deviation (see Setup notes) to stop D-POCO's persistent-group rejoin.
- Radio state: D-HU<->D-MOTO HFP link independently confirmed `Connected` (`dumpsys bluetooth_manager`, masked `XX:XX:XX:XX:E4:95`) immediately before arming; Gearhead confirmed disabled on D-MOTO (`pm list packages -d`) and its processes confirmed not running (`ps -A`) before arming; D-POCO confirmed holding no competing HFP client slot.
- Discard-rule check: two prior captures discarded for cause (see Setup notes), final capture clean - single arming through to a single escalation and a single poke, no unintended reconnect, no second `createGroup SUCCESS`.
- Decisive log lines, quoted with timestamps:
  - Arming, `00:21:17.467`: *"waking a phone over a hands-free link it holds is not yet measured on this unit, so the first one is the measurement."*
  - Refusal passes (`noteHandsFreePokeSkip`), six of them, ~15.02s apart, each costing zero budget: `00:21:18.503`, `00:21:33.526`, `00:21:48.550`, `00:22:03.573`, `00:22:18.594`, `00:22:33.618` - all: *"Not poking motorola edge 30 neo (A0:46:5A:97:E4:95) - this head unit already holds a Bluetooth hands-free link to it, which a poke would take over and leave disconnected. Waiting for the phone to start Android Auto itself."*
  - Escalation, `00:22:48.642`, **91.175s after arming** (brief expected "about 90s"): *"waking motorola edge 30 neo (A0:46:5A:97:E4:95) despite the hands-free link - it has not started Android Auto in 90s, and a link that never changes raises no event for the phone to notice. Wake 1 of 2; the link is left alone for 30s to come back."*
  - Poke sequence: HFP-AG `socket.connect()` at `00:22:48.656`, succeeded `00:22:49.083` -> *"Successfully poked motorola edge 30 neo via HFP-AG. Holding 20000ms..."*
  - `grep -c "Calling socket.connect()"` across the whole capture: **1** (the escalation poke itself; zero additional pokes in the give-back window).
  - Verdict, `00:23:18.648`, **29.99s after the poke's `socket.connect()`** (matches the brief's "~30s" window): *"the hands-free link came back within 30s of the wake, so waking over it costs this unit a blip and will keep being used."*
- Measurements: arm->escalation 91.175s; refusal cadence 15.02-15.03s x 6; zero pokes in the 30s post-wake window; verdict at +29.99s from the poke.
- Independent `dumpsys bluetooth_manager` samples (masked `XX:XX:XX:XX:E4:95`, HeadsetClientStateMachine): `00:24:30` (~+102s from wake) Connected; `00:24:55` (~+127s) Connected; `00:27:52` (~+304s, ~+5min) Connected. Agrees with the app's own verdict at every sample.

The verdict wording is a **third variant** from round 3's two (D-HU's own "will not be used again" and D-HU's other own "measured safe... came back on its own"): this one names the poke's own disruption-then-recovery explicitly. The brief anticipated exactly this shape of outcome ("do not read either wording as a failure") and this reading is consistent with it - the link recovered specifically because the poke's HFP-AG takeover disrupted it and D-MOTO's own stack immediately re-established it, which is what "costs this unit a blip and will keep being used" means.

## Stage B — D-SAM, D-POCO

### B3r2 — The rename counter, third time, and what it actually costs

**PASS.**

- Settings written: `wifi-direct-group-name-changes` reset to `0`; `wifi-connection-mode=3` (was left at `1` from round 3's L3r, per round 3's own Setup notes - reset to `3` here, matching round 3's original B3r setup).
- shared_prefs ownership confirmed `u0_a154:u0_a154` before the first bring-up.
- Four full bring-ups (force-stop + relaunch between each), log read-back line and `settings.xml` both, every time:

  | Bring-up | First (ephemeral) callback | Settled SSID / line | Log `nameChanges` | `settings.xml` |
  |---|---|---|---|---|
  | 1 | - (fresh baseline) | `23:50:33.294` DIRECT-Ts-Navegadortz3 | `1/3` | `1` |
  | 2 | `23:51:23.883` DIRECT-Ts (CHANGED, BSSID moved) `1/3` | `23:51:24.854` DIRECT-IT-Navegadortz3 | `2/3` | `2` |
  | 3 | `23:52:01.510` DIRECT-IT (CHANGED) `2/3` | `23:52:02.781` DIRECT-iP-Navegadortz3, **stable=no (the platform names it)** | `3/3` | `3` |
  | 4 | `23:52:39.717` DIRECT-iP (CHANGED) `3/3` | `23:52:40.718` DIRECT-6q-Navegadortz3, stable=no (the platform names it) | `4/3` | `4` |

- By the third bring-up's settled callback the count reads `3/3` exactly as the brief's PASS condition states, the label reads `stable=no (the platform names it)`, and the reason names the platform rather than promising the next bring-up decides: *"this platform names the group itself and has picked a different name on 3 creates, so the kept identity cannot apply here and no setting reaches it."*
- The ephemeral (first, CHANGED-branch) callback now correctly **carries `nameChanges` forward** instead of zeroing it (bring-up 2's ephemeral read `1/3`, not `0/3` as round 3 measured pre-fix) - this is the actual fix, confirmed directly from the log's own printed value each time, not inferred.
- The ordinary Bluetooth WPP path connected on every bring-up: *"hands-free service level connection established (HFP-AG poke to DC:B7:2E:5E:4E:59)"* fired once per bring-up, confirming round 3's correction (withholding the endpoint on an `UNPROVEN`/`RENAMED` verdict is the correct outcome here, not a degradation - nothing was ever at risk).
- Discard-rule check: **deviation** - all four bring-ups' captures were taken with a single `adb logcat` process that was never killed between bring-ups, so `bringup1.txt` through `bringup4.txt` ended up as nested supersets of the same session rather than four independent files. Content is unaffected: each grep still isolated the correct per-bring-up lines by timestamp, cross-checked against the `createGroup SUCCESS` counts across the four files, which read 4/3/2/1 - consistent with the capture-overlap explanation (one long-running process, progressively fewer bring-ups left to happen after each successive file started), not with any real extra group churn. All four stray processes were killed once noticed.
- D-SAM restored to its pre-round settings backup afterward (`wifi-connection-mode` back to `1`, `wifi-direct-group-name-changes` back to `1`).

### B2r2, B1r, B4r

Not run, per the brief (B2r2: round 3's B2r answered it completely; B1r/B4r: PASSed in round 2).

## Stage C — D-HP, D-POCO (Headunit Server only)

### A1r — The audio queue, in the right units this time

**PASS.**

- Settings written: `audio-queue-capacity=0` for the first half (~19 minutes, extended past the brief's 10-minute floor to let a real backlog develop - see below), then `audio-queue-capacity=50` for the second half (~7 minutes), restored to `50` afterward via full settings restore (matches its pre-round value).
- Codec recorded before the session, per the brief's own requirement: `use-aac-audio=true`, confirmed live at session start - `AudioDecoder.start: channel=6, stream=3, ... sampleRate=48000, numberOfBits=16, numberOfChannels=2, isAac=true, ... queueCapacity=0` (and `isAac=true` on the two AUDIO1/AUDIO2 channels too, at 16000 Hz mono). This is the AAC path the fix specifically targets.
- **Capacity=0 half.** The sink line format now reads `queued=N/unbounded (Xms)` exactly as the brief describes. For most of the run the queue stayed small and healthy - `queued=4/unbounded (84ms)` up to `queued=13/unbounded (273ms)`, zero underruns, zero drops - implying a real per-chunk duration of ~21ms, consistent with AAC-LC's 1024-sample frame at 48 kHz (~21.3ms). This is a completely different picture from round 3's own (bugged) numbers, where a "7ms" chunk was reported - the fix is visibly measuring a real, plausible chunk size now. Around the 18-minute mark a genuine backlog developed on its own (not injected): `queued=112/unbounded (2352ms)` at `00:49:31.184` with 1 underrun and 1 rebank, growing to `queued=267/unbounded (5607ms)` at `00:50:31.194` with 2 underruns - real, unbounded growth of the kind round 3 also saw, this time with duration figures that track the chunk count at a consistent, plausible rate rather than being scaled down by the AAC compression ratio.
- **Capacity=50 half.** The ceiling printed beside the depth reads the literal configured `50`, not an inflated floor value: `queued=5/50 (105ms)` at rest, climbing to `queued=50/50 (1050ms)` exactly at the ceiling during one rough patch (`00:54:34.734`, with 65 underruns and 627 dropped chunks in that window - real shedding, not a stall), then recovering to a `19-20/50` steady state afterward. **This is a different (and now-explicable) shape from round 3's "142-143 plateau" finding**, which round 3's own brief mis-described as the expected "single digits." `SinkQueueOverflowPolicy.MIN_QUEUE_MS = 1_000L` and `capacityChunks()`'s `ceil(MIN_QUEUE_MS / chunkDurationMs)` floor, checked directly against the source: with the corrected ~21ms chunk duration, that floor computes to `ceil(1000/21) = 48`, which is *below* the configured `50` - so `capacityChunks()` returns the configured value unmodified this time, rather than inflating it the way round 3's ~7ms (bugged) chunk size did (`ceil(1000/7) = 143`, exactly round 3's own measured plateau). **Round 3's "142-143 plateau" was itself a symptom of the same units bug A1r exists to fix, not an independent finding about the setting.**
- Operator-audible confirmation was not attempted: this rig has no speaker and no 3.5mm output (a standing rig limitation, not specific to this run), so the brief's "whether an operator listening can hear the offset" is unconfirmed and reported as such rather than assumed either way. The verdict rests entirely on the log-based evidence above, which is unambiguous on its own.
- PASS condition met on both counts the brief states: the duration figure is now consistent with the AAC codec in use (was not, pre-fix), and the ceiling is printed beside the depth so a queue at its ceiling reads as one (`50/50` observed directly, not inferred).

### A2 — The one-shot unbounded-queue warning

**PASS.**

- Settings: `audio-queue-capacity=0` (same session as A1r's first half - see Setup notes for why these were combined).
- Capture armed before the session started (`logcat -c` then a background `logcat -v threadtime > file &` predating `am start`), and verified still alive throughout via a 60s-interval liveness poll (it died and was auto-restarted twice - see Setup notes).
- The line, verbatim with its timestamp:

  `09-18 00:49:06.934 ... W AudioTrackWrapper.warnIfUnboundedQueueIsBehind | Audio queue capacity is 0, so nothing sheds and this sink is 2058ms behind on 98 queued chunks. Set "Audio queue capacity" to bound it.`

- Fired **exactly once** across the whole ~19-minute capacity=0 capture (`grep -c` = 1).
- The figure agrees with the periodic `audio sink` lines bracketing it: `queued=11/unbounded (231ms)` at `00:48:31.124` (before), climbing to `queued=112/unbounded (2352ms)` at `00:49:31.184` (after) - the warning's own `98 queued chunks, 2058ms` sits exactly between those two samples in both chunk count and duration, and the per-chunk rate implied (2058ms/98 ≈ 21ms) matches every other measurement in this round.

### L4r2 — The Share button below Android 10, by hand

**PASS.**

- Every scripted route was already closed by round 3 (see the brief's own text); this run was two physical taps by the operator - Settings, then the log Share button - with a capture running throughout, started before the taps.
- Settings screen reached: confirmed from the app's own log, not just the tap - `AapService: the settings screen is open, so the wireless stack stops until it closes` at `00:59:28.304`, and `ActivityManager: Displayed .../SettingsActivity: +2s759ms`.
- Share produced a real system chooser, not a silent failure: `ActivityManager: START ... act=android.intent.action.CHOOSER ... cmp=android/com.android.internal.app.ChooserActivity` at `00:59:53.364`, `Displayed ... ChooserActivity: +199ms`. (A second Settings-then-Share cycle happened a few seconds later in the same capture, `01:00:07.994`, with an identical clean chooser launch - not asked for, but corroborating.)
- `grep "LogExporter: could not share"` over the whole capture: **zero matches**.
- App confirmed still running afterward: `ps` on-device shows `com.andrerinas.headunitrevived` alive at the same pid throughout.
- All three PASS conditions met: a chooser appeared, the app stayed alive, and no `could not share` line.

### L1r, L2, L3r, L5r

Not run, per the brief (L1/L5 PASSed in round 2, L2 dropped as structurally unreachable, L3 closed in round 3).

## 3. What this round cannot settle

Unchanged from the brief's own section 3 - the below-Lollipop WiFi rejoin, whether the escalated wake achieves a connect (by design, W3r's Gearhead-disabled setup prevents this), and whether a tablet can be provisioned as a wireless car. Nothing this round's runs touched changes any of those.

## Anything the brief did not ask about

- **`pm list packages -d` is the only reliable way to check whether a package is disabled; `dumpsys package`'s per-user `enabled=0` field means the DEFAULT (enabled) state, not disabled.** Misreading this cost two discarded W3r captures this round (see Setup notes) - worth adding to `TESTING-TEMPLATE.md` since it is a generically useful correction, not specific to this thread.
- **`pm disable-user` does not kill an already-running process.** A package disabled while its process is already alive keeps functioning until that process is separately killed (`am force-stop` or a natural exit) - also worth a `TESTING-TEMPLATE.md` line, since the existing "Gearhead cannot be held down with force-stop" quirk is the mirror image of this one and the two are easy to conflate.
- **D-HU's `wifi-direct-stable-identity=true` plus a persistent WiFi Direct group let an unintended phone rejoin in ~6s**, independent of Bluetooth state or app-level poke logic - a genuinely different mechanism from the already-documented "the phone's own reconnect beats our poke" quirk (that one is about Bluetooth/RFCOMM racing the poke; this one is a pure WiFi-Direct-framework-level rejoin that doesn't touch Bluetooth or the app's poke code at all). Any future round arming a *specific* phone for a wake/poke test on D-HU should clear `wifi-direct-stable-identity` and the saved P2P groups first if a second phone is present and paired.
- D-HP's WiFi radio needing ~25-30s to come up after `svc wifi enable` (see Setup notes) is worth folding into `TESTING-TEMPLATE.md`'s existing D-HP section alongside its other slow/unreliable-radio quirks.
- `native-aa-wake-damage-verdict` on D-HU was still reading its round-3-latched `1` at the very start of this round, before anything in this round touched it - the same "read any counter a run grades before the run" caution `TESTING-TEMPLATE.md` already carries for other counters applies here too; this round's own restore-then-re-clear sequence (see Setup notes) is the concrete case that caution is meant to catch.
- **The operator separately observed D-SAM unable to win D-POCO's hands-free (HFP) connection away from D-HU while D-POCO was already HFP-connected to D-HU**, a live/manual observation made in parallel with the scripted runs above, not itself a scripted run. This is consistent with the same single-active-slot mechanism this round's own W3r hit in script (D-HU's `HeadsetClientService` reports `mMaxHeadsetConnections: 1` / `Max Connected Devices = 1`, confirmed directly via `dumpsys bluetooth_manager` during W3r's setup, where D-POCO's own reconnect had to be cleared out of the way by disabling its Bluetooth radio before D-MOTO could take the slot). The mechanism here is the same shape but the other direction - two *head units* (D-SAM and D-HU) competing for one *phone's* hands-free slot, rather than two phones competing for one head unit's slot - and was not independently re-verified in script this round. Worth a scripted follow-up in a future round if it matters to a thread that depends on multi-head-unit HFP contention.
