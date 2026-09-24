# mic-uplink — round 1 results

**Candidate:** fork/fix/mic-uplink @ `42302130`       **Baseline:** origin/main @ `ea7aa7e0`
**APK md5:** `5bef73b831be7b2b1495f1c29177ed58` (candidate) / `4371919f98d7b14037f65150cc58c4e0` (baseline)
**Unit:** UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14, 1440x720 usable / negotiated 1920x1080. Paired
phone: POCO X3 NFC (Redmi M2007J20CG), Gearhead `17.5.663204-release`.
**Date:** 2026-08-25

## Setup notes

**Scripts used:** `build_hur.sh` and `run_unit_tests.sh` for both arms (unmodified). `set_hu_prefs.sh`
for every multi-key settings write (no relaunch between keys). No new script was needed this round.

**Branch rewrite:** fetched fresh per the brief; `origin/main` had already advanced to `bae47b23`
(a later, unrelated commit) by the time this round ran, so `main-a` was checked out at the pinned
`ea7aa7e0` explicitly rather than at the branch tip. `mic-uplink` checked out cleanly at `42302130`.

**`settings.xml` delta at round start:** `wifi-connection-mode=3`, `enable-audio-sink=true`,
`mic-input-source=0` were already at the values this round needed. `key-codes` was absent (confirmed
before every run). `bt-address` was blank (`""`) before the round and stayed blank throughout — see
M6b. `log-level` started at `2` (INFO); raised to `0` for M1/M6a-adjacent VERBOSE work is not what
happened — M1 ran at `0` per the brief, M6a/M6b/M7 ran at `1` (DEBUG) per the brief's own table.

**§5a trigger: all three routes work at the delivery layer, none open the microphone.** Route 1
(`RemoteControlReceiver` + `--es command voice`) and Route 2 (Microntek down/up pair) were both
confirmed to reach the app (`RemoteControlReceiver received:` / `CarKeyReceiver: Handling intent
action:`) and to produce a genuine `Voice Session Notification: START` on the phone's Android Auto
session, closing with `STOP` 3.1-3.5s later. Route 3 (reversed pair) also reached the app and
produced the same START/STOP pattern. **No route ever produced a `MicrophoneRequest`.** Traced to the
phone side: Gearhead's assistant on this build is Gemini, not classic Google Assistant, and its own
logs show `#setFinalRecognizedText [finalText: Cannot connect to Gemini]`, `not opening mic early for
auto, gemini micboost!`, and `Assistant.Controller` staying `LIVE_INACTIVE` on every attempt — this
despite the phone holding a validated LTE data connection and a validated WiFi (P2P) connection at
the time (`dumpsys connectivity` showed both `EVER_VALIDATED&IS_VALIDATED`). This is a phone-side
Gemini backend/account dependency, not a delivery or code defect, and it blocks every run in this
brief that needs the phone to actually request the head unit's microphone. Per the brief's own
pre-registered fallback (§5a, last paragraph), M1 through M5 are reported UNTESTABLE below, and M6a
/ M7 (independent of the microphone opening) were run in full.

**Two new rig quirks found this round, worth adding to `TESTING-TEMPLATE.md` §7a:**
- **Cycling the *phone's* Bluetooth radio does not reproduce a fresh `ACL_CONNECTED` on this rig.**
  M6a's own recipe (`svc bluetooth disable` / `enable` on the phone) was tried twice — a 2s and a
  10s disable window, up to 40s combined observation — and neither produced a new
  `android.bluetooth.device.action.ACL_CONNECTED` broadcast on the head unit side (confirmed absent
  from the continuous capture, not just a `logcat -d` ring-buffer wrap). The working substitute:
  cycling the **head unit's own** Bluetooth adapter (`svc bluetooth disable`, which self-reverts on
  this rig in ~14s per the existing §7a entry) reliably produced a real system-level
  `ACL_CONNECTED`, confirmed independently from the phone's own `GH.WifiBluetoothRcvr: Connection
  action: ... ACL_CONNECTED` log line, not just the head unit's side. Used for all 5 of M6a's cycles.
- **`pm revoke android.permission.RECORD_AUDIO` fails on this ROM** with
  `SecurityException: ... is not a changeable permission type`. Substituted
  `adb shell appops set $PKG RECORD_AUDIO ignore` / `allow`, which is exactly the ROM-app-op-level
  denial the brief itself anticipated as distinct from a real permission revoke (§brief M6b). The
  runtime permission grant stayed `granted=true` throughout — only the app-op flipped — so
  `foregroundServiceTypeMask()`'s permission check (which reads the runtime grant, not the app-op)
  still saw RECORD_AUDIO as granted.

`RECORD_AUDIO` app-op was restored to `foreground` (the pre-round default) at the end of M6b.

Discard-rule note: M1's first attempt was discarded after force-stopping the phone's Gearhead
process (to raise §5b's diagnostic tags) tore down and re-formed the live session mid-capture (a
second `createGroup SUCCESS` and a second SSL handshake) — raising those tags disturbs a live
session on this rig and must be done **before** launching, not during. The second attempt was
discarded after the phone's own reconnect stalled and the poke-retry loop churned a second P2P group
(`createGroup SUCCESS`=2) — the #760 self-wake pattern this repo's own `CLAUDE.md` already documents.
The third attempt was clean (1 group, 1 handshake) and is the capture M1's findings above are drawn
from.

## R M0 — Gate

**PASS**

- Both arms: first-ever compile of `42302130` on this machine; both compiled clean.
- Arm A (`ea7aa7e0`): 765/765 tests.
- Arm C (`42302130`): 833/833 tests, exactly matching the brief's prediction.
- All eight named suites present at the exact predicted counts:

| Suite | Expected | Actual |
|---|---|---|
| `MicUplinkFrameTest` | 9 | 9 |
| `MicUplinkMonitorTest` | 10 | 10 |
| `MicChunkAccumulatorTest` | 10 | 10 |
| `MicrophonePolicyTest` | 4 | 4 |
| `ForegroundServiceTypePolicyTest` | 5 | 5 |
| `BluetoothAddressSeedPolicyTest` | 3 | 3 |
| `MicCaptureRatePolicyTest` | 7 | 7 |
| `MicPcmDecimatorTest` | 7 | 7 |

APK md5s differ (confirmed above); both installed with `adb install -r`.

## R M1 — The point of the round: the message carries its type, and the phone understands it

**UNTESTABLE** — pre-registered fallback, §5a's own contingency.

- Settings written: `log-level=0`, `log-source=0`, `mic-sample-rate=16000`,
  `use-head-unit-microphone=true`, `mic-input-source=0`, `mic-noise-suppressor=false`,
  `mic-auto-gain-control=false`, `mic-echo-canceler=false`, `enable-audio-sink=true`,
  `wifi-connection-mode=3`.
- Radio state: both radios on both sides throughout; verified via `dumpsys` before launch.
- Discard-rule check: 2 re-runs (see Setup notes); third run clean (`createGroup SUCCESS`=1, one SSL
  handshake).
- Decisive log lines: all three trigger routes produced `Voice Session Notification: START` followed
  by `STOP` 3.1-3.5s later (route 1: `03:41:09.397` → `03:41:12.639`; route 2:
  `03:42:23.905` → `03:42:26.992`; route 3: `03:42:57.776` → `03:43:00.875`, head-unit clock). **No
  `Mic request:` line ever appeared on either arm.** No `MIC Media Data` message of any kind was ever
  seen on the wire, so the `dataOffset: 6` vs `dataOffset: 2` comparison this run exists to produce
  could not be made.
- Measurements: n/a — no microphone traffic was ever generated to measure.

Cannot report the frame-layout comparison, the transcription comparison, or the `peak=` silent-vs-
spoken comparison the brief asks for, because the phone's own Gemini backend never requested the
microphone in any of three attempts across two separate broadcast-delivery mechanisms. The delivery
mechanism itself is proven working (see Setup notes); the block is entirely on the phone side.

## R M2 — Positive control: the rate picker no longer reaches the wire

**UNTESTABLE**, same root cause as M1. Not run in full: since M1 established the microphone channel
never opens on this rig regardless of trigger route, a `mic-sample-rate=48000` run would produce the
identical zero-mic-traffic result and cost a settings write and relaunch for no new information. No
fallback line (§5) appeared in any capture across the round; reported as zero, but not corroborating
anything since the rate-dependent code path was never reached either.

## R M3 — Whole 2048-frame messages

**UNTESTABLE**, same root cause. No `mic uplink |` line ever appeared in any capture this round.

## R M4 — The phone's request is answered, and its stop is honoured

**UNTESTABLE**, same root cause. `anc_enabled`, `ec_enabled`, `max_unacked` and `acks=` — the
deliverables this run exists to produce regardless of PASS/FAIL — were never observed, because no
`MicrophoneRequest` (0x8006) ever arrived from the phone in any run this round.

## R M5 — The head unit microphone off, and what the phone does about it

**UNTESTABLE** on the phone-side question (same root cause: no assistant session ever reaches the
microphone-negotiation stage regardless of the `use-head-unit-microphone` setting, so there is nothing
to observe about the phone's fallback behaviour). The head-unit-side half was exercised incidentally
during M6b below with the setting still at its default (`true`), not specifically at `false`; not
re-run separately given the same blocking condition would apply.

## R M6 — The service still starts

### M6a. A background start that claims the microphone type

**FAIL — 5/5 cycles, blocks the branch.**

- Settings: `use-head-unit-microphone=true`, `RECORD_AUDIO` granted (confirmed via
  `dumpsys package` and `appops get` before the run).
- Trigger substituted per Setup notes: cycling the phone's Bluetooth radio (the brief's literal
  recipe) produced no `ACL_CONNECTED` on this rig after two attempts; cycling the **head unit's own**
  Bluetooth adapter (self-revert, ~14s) did, confirmed via the phone's own
  `GH.WifiBluetoothRcvr: Connection action: ... ACL_CONNECTED` line each time — a genuine
  system-broadcast event, not an app-initiated one.
- Discard-rule check: n/a (this run's subject *is* the repeated background restart; each cycle starts
  from `headunit://exit` + confirmed-gone service, per the brief).
- Decisive log lines, every one of 5 cycles, verbatim:

  ```
  AutoStartReceiver.onReceive | MATCH! Starting AapService via Bluetooth Auto-start...
  AapService.onCreate | ForegroundServiceStartNotAllowedException/Exception caught in onCreate:
    Starting FGS with type microphone callerApp=ProcessRecord{... com.andrerinas.headunitrevived/u0a168}
    targetSDK=36 requires permissions: all of the permissions allOf=true
    [android.permission.FOREGROUND_SERVICE_MICROPHONE] any of the permissions allOf=false
    [CAPTURE_AUDIO_HOTWORD, CAPTURE_AUDIO_OUTPUT, CAPTURE_MEDIA_OUTPUT, CAPTURE_TUNER_AUDIO_INPUT,
    CAPTURE_VOICE_COMMUNICATION_OUTPUT, RECORD_AUDIO] and the app must be in the eligible
    state/exemptions to access the foreground only permission
  java.lang.SecurityException: <same message>
      at com.andrerinas.openheadunit.aap.AapService.onCreate(AapService.kt:819)
  ```

  This fires **despite `RECORD_AUDIO` being granted** — the failure is the "eligible state/exemptions"
  clause, not the permission list. `AapService.kt:817-827`'s catch block calls `stopSelf(); return`
  immediately after logging this, which skips `fillBluetoothAddressIfUnset()`, `setupCarMode()`,
  `setupNightMode()`, `observeConnectionState()` and `registerReceivers()` for the rest of `onCreate`.
  **But `onStartCommand()` — already queued as part of the same `startForegroundService()` call —
  runs anyway** (`AapService: Bluetooth auto-start — Native AA handshake manager was stopped,
  re-arming.`) and goes on to form a complete session every single time: `WifiDirectManager: 5GHz
  createGroup SUCCESS!` and `Handshake: SSL handshake complete` both followed in all 5 cycles, despite
  the service having called `stopSelf()` moments earlier.

- Measurements — first-frame time (SSL handshake complete) after `MATCH!`, all 5 cycles:

  | Cycle | MATCH! → SecurityException | MATCH! → SSL handshake complete (first frame) |
  |---|---|---|
  | 1 | 146ms | 6627ms |
  | 2 | 165ms | 8970ms |
  | 3 | 125ms | 7984ms |
  | 4 | 95ms | 9141ms |
  | 5 | 105ms | 7360ms |

- Same process pid (`7019` in cycles 1-5; a different pid on the initial explicit-launch baseline)
  persisted across all 5 `headunit://exit` cycles — the app process itself was never killed by any
  cycle, only the AAP session was torn down and reformed each time.

**A FAIL here blocks the branch, exactly as the brief says.** It also means every background-started
session on Android 14 with the microphone enabled currently runs as an unpromoted (non-foreground)
service for its whole lifetime, and never runs `registerReceivers()` / `setupCarMode()` /
`setupNightMode()` / `observeConnectionState()` for that instance — a wider blast radius than "the
microphone doesn't work," since those are the same setup steps every other autostart path depends on.

### M6b. The permission revoked

**Partial: session-forms half PASS; microphone-response half UNTESTABLE (same root cause as M1-M5).**

- `pm revoke android.permission.RECORD_AUDIO` failed on this ROM
  (`SecurityException: ... is not a changeable permission type`) — substituted
  `appops set $PKG RECORD_AUDIO ignore` (see Setup notes). The runtime permission grant itself stayed
  `granted=true` the whole time; only the app-op layer was denied.
- One session, arm C: `createGroup SUCCESS`=1, `Handshake: SSL handshake complete` 7.8s after launch
  — the service starts and a session forms cleanly with the microphone app-op denied, matching the
  brief's PASS condition for that half.
- One assistant trigger (route 1): broadcast reached `RemoteControlReceiver` (confirmed
  `RemoteControlReceiver received: com.android.music.musicservicecommand`), but **no
  `Voice Session Notification` followed at all this time** — inconsistent with the 3-for-3 success
  rate seen in M1, i.e. the phone-side Gemini flakiness is itself variable, not merely "always fails
  the same way." Not scored; consistent with the same blocking condition.
- Because no `MicrophoneRequest` ever arrived, `MicRecorder: RECORD_AUDIO is granted but this ROM has
  revoked the...`, `Mic request: capture did not start (code -3)`, and a `MIC Mic Response` for this
  case were never observed — the actual distinguishing behaviour this run exists to check remains
  untested, for the same reason as M1-M5.
- `RECORD_AUDIO` app-op restored to `foreground` (default) at the end; confirmed via `appops get`.
- `bt-address`: read as blank (`""`) before the round, and still blank (`""`) after every arm-C
  launch across the whole round (M1 baseline, all 5 M6a cycles, M6b, M7). The
  `AapService: filled in this device's Bluetooth address` line **never fired**, and neither did the
  `could not read this device's Bluetooth address` warning — `BluetoothHelper.getBluetoothMacAddress()`
  is returning an empty string on this device (not the classic `02:00:00:00:00:00` masked value),
  which `BluetoothAddressSeedPolicy.seed()` correctly leaves alone (`if (seeded.isNotEmpty())` guards
  both the write and the log line). This is the intended "nothing detected, leave blank" behaviour,
  not a defect — but it also means neither branch of `BluetoothAddressSeedPolicy` that matters (fill
  from blank, or preserve a hand-typed value) was exercised on real hardware this round.

## R M7 — Clean control

**PASS.**

- Settings: `log-level=1`, all other keys unchanged from the round's baseline.
- One uninterrupted session, arm C: `Handshake: SSL handshake complete` at launch through capture end
  = **11m53s**, comfortably over the required 10 minutes.
- Discard-rule check: clean, `createGroup SUCCESS`=1.
- Music: Spotify confirmed `PLAYING` via the phone's own `dumpsys media_session` at the start of the
  window and again at the end (active item id advanced from 17 to 20 over the window, i.e. a track
  actually changed — genuine continuous playback, not a stalled position). Media keys did **not**
  resume playback here (confirms the existing rig quirk); a tap on the projected media widget
  (`input tap 272 657` at 1440x720) did.
- All six required zero-counts, every one exactly 0:

  ```
  mic uplink started:                0
  Initializing AudioRecord:          0
  capture summary:                   0
  Mic request:                       0
  Audio queue is full:               0
  disabled due to previous underrun: 0
  ```

- `createGroup SUCCESS` = 1. `Media Start Request AUDIO` = 2.
- Throughput: 142 windows over the session, `dropped=0` in every one of the 142. fps ranged 16-54
  (median-ish 19-24) — lower than other threads' active-navigation sessions report, but consistent
  with a mostly-static "Now Playing" screen rather than a regression; `VideoDecoder.logThroughput`
  never showed `skipped` or `concealed` above 0 in any window either.

Nothing in seven commits of microphone work touched a session that never opened the microphone.

## Report back — the four things that decide whether this ships

1. **M1:** UNTESTABLE. No `dataOffset: 6` vs `dataOffset: 2` comparison could be made — no microphone
   traffic was ever generated on this rig, on either arm, because Android Auto's Gemini backend never
   requested the microphone regardless of which of the three trigger routes was used. Transcription
   was never attempted for the same reason.
2. **M2:** UNTESTABLE, same root cause. `SampleRate:` was never observed on either arm.
3. **M4:** UNTESTABLE, same root cause. `anc_enabled`, `ec_enabled`, `max_unacked` and `acks=` were
   never observed — no `MicrophoneRequest` ever arrived in any run this round.
4. **M6a:** **FAIL, 5/5 background starts.** Every cycle threw
   `SecurityException: Starting FGS with type microphone ... the app must be in the eligible
   state/exemptions`, despite `RECORD_AUDIO` being granted. `onCreate()`'s catch block calls
   `stopSelf(); return`, but `onStartCommand()` runs anyway and forms a full session regardless — the
   service ends up running unpromoted (non-foreground) for the whole session, having skipped receiver
   registration, car mode and night mode setup. **This blocks the branch, per the brief's own
   criterion.**

**M5** (what the phone does when the head unit declines): UNTESTABLE, same Gemini-backend root cause
as M1-M4 — no assistant session on this rig ever reaches the point of negotiating which microphone to
use, with the setting at either value.

## Anything the brief did not ask about

- **Gemini, not classic Google Assistant, is what Android Auto invokes for voice on this phone**
  (`Gearhead 17.5.663204-release`), and it cannot complete a connection to its own backend on this
  rig in any of 4 attempts across the round (`Cannot connect to Gemini`), despite the phone holding a
  validated LTE and a validated WiFi connection at the time. This is outside the app's control, but it
  is now a standing limitation for this channel: **no round can currently exercise any
  microphone-open behaviour on this paired phone** until this is resolved on the phone side (account,
  region, or Gemini-service issue) — worth its own line in `CLAUDE.md` alongside the existing
  hotspot-config and Gearhead-17.4-broadcast entries, since it blocks an entire class of future
  audio-input rounds, not just this one.
- **M6a's finding is broader than "the microphone foreground-service type throws on Android 14."**
  Because the catch-and-`stopSelf()` in `onCreate()` does not prevent the already-queued
  `onStartCommand()` from proceeding, the practical effect on this rig is that a background
  Bluetooth-triggered restart with the microphone enabled **always** ends up running a full AAP
  session on a service that is not actually promoted to the foreground and that skipped its own
  receiver registration, car-mode and night-mode setup for that instance. Whether that produces
  visible symptoms (a receiver silently never firing again until the next restart, night mode not
  re-applying) would need a dedicated round; not investigated further here since M6a's own FAIL
  already blocks the branch.
- Two rig quirks worth folding into `TESTING-TEMPLATE.md` §7a for future rounds (detailed in Setup
  notes above): cycling the *phone's* Bluetooth radio does not reproduce a fresh `ACL_CONNECTED` on
  this rig, but cycling the *head unit's own* adapter does; and `pm revoke` is not usable for
  `RECORD_AUDIO` on this ROM, but `appops set ... ignore/allow` is a working substitute for testing
  ROM-level microphone denial distinct from a real permission revoke.
