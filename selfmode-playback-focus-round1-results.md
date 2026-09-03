# selfmode-playback-focus — round 1 results

**Candidate:** `fix/846-audio-focus-auto-trial` @ `abf9067a`  (one commit on `967cb41d` "next beta")
**Baseline:** none built — every control is a settings change on the candidate (per brief §1)
**APK md5:** `9396fd081c40b4fa4ab47bc3943c9aeb` (candidate) — installed with `adb install -r`, live-md5 verified
**Unit:** D-POCO — Redmi M2007J20CG / POCO X3 NFC, serial `4f4027e9`, Android **15**, Gearhead **17.5.663204-release**. Has a speaker; a BT speaker was paired to the phone for R1/R3.
**Date:** 2026-08-28

---

## Bottom line

**The changed code is not reachable in Self Mode on this rig, in either direction.** Across three
Self Mode sessions (AUTO ×2, NEVER ×1):

- OHU **never calls `requestAudioFocus()`** — it does not appear in `MediaFocusControl` logs or in
  `dumpsys audio`'s focus stack in any sample of any run.
- `AapAudio: … acquiring transient system audio focus` and `… leaving system audio focus alone`
  were printed **0 times** — `onAudioPlaybackStarted` never ran for an audio channel, so the
  grab/decline decision (the whole diff) never executed.
- The `AUDIO2` (SYSTEM) channel is **set up** (`Media Sink Setup Request: 1 on channel AUDIO2`) but
  **never started** — every `Media Start Request` in every run was `VIDEO`.
- The only audio-focus message the phone sent over the protocol was **`RELEASE`** (0 × `GAIN`),
  which `shouldHonourProtocolFocusRequest(isRelease = true)` passes through ungated in both the old
  and new code.
- The self-defeating latch never armed and `playback-focus-self-defeating` is **absent** from
  `settings.xml` after the full round (R5).

Per brief §8 this is a **PASS for shipping to Self Mode users**: the #846 fix cannot help or harm
them because Self Mode never starts a system-audio channel and never asks us for a focus GAIN.

### The three numbers the brief asked for

1. **Did the phone's own playback survive R1?** **Yes.** Spotify played continuously from ~15:12:28
   to the end of the run (15:16:34), through the SSL handshake and all channel setup. There was one
   pause, 15:11:56.170 → ~15:12:13, and it was **Gearhead's** (`uid 10193`,
   `callingPack=com.google.android.projection.gearhead`, `USAGE_MEDIA/CONTENT_TYPE_SPEECH`, `req=1`)
   AA-startup focus grab — it fired **1.3 s before OHU's `MainActivity` was even launched** and OHU
   issued no focus call at any point. No interruption coincided with an OHU grab because OHU
   performed none. Gap between grab and any OHU grab: **N/A — zero OHU grabs.**
2. **`Audio Focus Request: stream=` count, R1 vs R3:** **1 vs 1.** Both are the RELEASE path
   (`AapAudio.requestFocusChange | Audio Focus Request: stream=3, type=4`, immediately after
   `AapControlService … Audio Focus Request: RELEASE` and `Sending immediate AudioFocusNotification:
   STATE_LOSS (always-grant)`), not an honoured GAIN. `Audio Focus Request: GAIN`: **0 vs 0.**
3. **R4 throughput:** **49–60 fps (mean 52.9) over 126 windows / ~10 min 38 s, `dropped=0`** for the
   entire run. No window below 45 fps, no window with `dropped>0`, no disconnect.

---

## Setup notes

**Build gate.** `run_unit_tests.sh` passed. `PlaybackFocusPolicyTest`: **20 tests, 0 failures,
0 errors** (fresh run). The four new cases are present by name (`auto takes focus until the harm is
observed`, `auto and always agree until the latch trips`, `auto is the stored default so an unset
preference takes focus until it learns not to`, `auto declines once the latch has tripped`).
`build_hur.sh` produced `com.andrerinas.headunitrevived_3.3.0-beta3_debug.apk`.

**Errors / deviations from the brief:**

- **§3/§4 — AA head-unit server on `:5277` was already `LISTEN`ing** for the whole round (`ss -ltn`).
  The brief's instruction to toggle *Start head unit server* by hand "before the install step" was
  not needed and not done. Note: `ss -ltn` on this device writes `Cannot open netlink socket:
  Permission denied` to **stderr** but still returns the `LISTEN` row on stdout.
- **§5 — `Audio Focus Request: GAIN` is not "always printed" here.** Across three Self Mode
  sessions the phone (acting as its own head unit) sent only `RELEASE` over the protocol; zero
  `GAIN`. The `AapControl` GAIN line the brief quotes never appeared.
- **§5 — the honoured form `Audio Focus Request: stream=3, type=1` never appeared.** The only
  `stream=` line in any run was `type=4` on the RELEASE path, and it is emitted on **every** Self
  Mode connect. So the brief's §6 R1-INCONCLUSIVE predicate ("neither `acquiring transient system
  audio focus` nor `Audio Focus Request: stream=` appears") is met in substance but not in the
  literal text — the predicate should exclude the RELEASE-path `stream=` line (identify it by the
  preceding `Audio Focus Request: RELEASE` / `always-grant` / `type=4`).
- **Gearhead grabs audio focus on every Self Mode connect** (`USAGE_MEDIA/SPEECH` in R1,
  `USAGE_UNKNOWN` in R3/R4), pausing the phone's local player for ~15–30 s before it resumes.
  Mode-independent, and in R1 it preceded OHU's launch. This is AA-side startup behaviour, not the
  candidate — but it means "does the phone's music briefly pause when you start Self Mode" is
  answered *yes* by AA itself, independent of `playback-focus-mode`.
- **Media control on this rig:** `adb shell input keyevent 126/127` alone did not reliably
  start Spotify from cold. `adb shell cmd media_session dispatch play` + `keyevent 126` after a
  foreground launch did. The very first play of the round needed a manual tap (done by the rig
  operator); after that the scripted resume worked.
- **`spf_selfmode_run.sh` (new, added to `hur-wifi-test-scripts/`)** — Self Mode launch + full
  logcat capture + a 15/30 s sampler of Spotify playback state, `dumpsys audio` started-player
  count, and OHU-in-focus-stack, plus optional `NAV=` (fires a `google.navigation:` intent) and
  `DRIVE=` (runs `mock_drive.sh`). Its closing "fps / throughput" grep has a wrong regex and prints
  nothing; fps for this round was extracted post-hoc from the logcat. Left in place for next round;
  the sampler's `focus=[…]` field also never matched this Android's focus-stack format and should be
  fixed before reuse.

**Scripts used:** `run_unit_tests.sh`, `build_hur.sh` (gate); `set_prefs_runas.sh` (all settings
writes); `mock_drive.sh` (map movement, default Medellín→Marinilla route); `spf_selfmode_run.sh`
(new, this round).

**State handling.** `settings.xml` backed up before any write; **restored from that backup at the
end and verified byte-identical**. Phone Bluetooth was re-enabled (it had been disabled for R4) and
the A2DP link to the speaker reconnected. Pushed test tone and temp scripts removed from the device.
**The candidate APK (`9396fd08…`) is left installed** — no baseline was built and the brief did not
ask for a revert.

**Diff vs a fresh settings backup at round start:** the rig arrived already on
`playback-focus-mode=0`, `static-audio-focus=false`, `enable-audio-sink=true`,
`auto-start-self-mode=false`, `log-level=2`, `view-mode=2` (GLES), `video-codec=H.264`,
`wifi-connection-mode=1`. No `playback-focus-self-defeating` key. Each run then set `log-level=0`,
`auto-start-self-mode=true` and its `playback-focus-mode`.

**No `HUR_Log_*.txt` exported** — that is a manual in-app action; the full `adb logcat -v time`
captures (`R{1,3,4}.logcat.txt`, `log-level=0`) are the record.

---

## R1 — Self Mode with the phone's own media playing  *(the point of the round)*

### **INCONCLUSIVE** — Self Mode never reached the changed code

- **Settings written:** `log-level=0`, `auto-start-self-mode=true`, `enable-audio-sink=true`,
  `static-audio-focus=false`, `playback-focus-mode=0`. `playback-focus-self-defeating` deleted
  (was already absent).
- **Radio state:** phone + head unit BT ON, A2DP speaker connected to the phone; `:5277` listening.
  Set via the rig's normal paired state; verified with `dumpsys bluetooth_manager`.
- **Drive:** Spotify playing to the BT speaker; `google.navigation:` intent at +12 s;
  `mock_drive.sh` feeding the default route at ~18 m/s for the whole hold (~4 min 37 s).
- **Discard-rule check:** clean. `performHandshake | SSL handshake complete` ×1, `createGroup
  SUCCESS` ×0, `MATCH! Starting AapService` ×0, `Magic Garbage` ×0, no `p2p-wlan0-*` interface, no
  second SSL handshake, no `Self Mode disconnected`. Capture last timestamp `15:16:34.826` vs kill
  `15:16:34.821` — stdbuf sane.

**Decisive log lines (timestamps):**

```
15:11:56.170 MediaFocusControl: requestAudioFocus() from uid/pid 10193/4606
             AA=USAGE_MEDIA/CONTENT_TYPE_SPEECH callingPack=com.google.android.projection.gearhead req=1
15:11:56.365 MediaSessionService: … targetPackage:com.spotify.music reason:MediaSessionRecord:pause
15:11:57.432 spotify-media-session → PlaybackState {state=PAUSED(2)}
15:11:57.438 am start … MainActivity                       ← OHU launched AFTER the pause
15:12:00.196 SelfMode: AA 17.4+ detected. Connecting directly to Headunit Server on 127.0.0.1:5277
15:12:01.246 AapSslContext.performHandshake | SSL handshake complete
15:12:01.765 AapControlService.audioFocusRequest | Audio Focus Request: RELEASE
15:12:01.766 AapControlService.audioFocusRequest | Sending immediate AudioFocusNotification: STATE_LOSS (always-grant)
15:12:01.769 AapAudio.requestFocusChange | Audio Focus Request: stream=3, type=4
15:12:01.769 AapAudio.requestFocusChange | Releasing audio focus
15:12:01.789 AapControlMedia.mediaSinkSetupRequest | Media Sink Setup Request: 3 on channel VIDEO
15:12:01.832 AapControlMedia.mediaSinkSetupRequest | Media Sink Setup Request: 1 on channel AUDIO2
15:12:04.596 AapControlMedia.mediaStartRequest    | Media Start Request VIDEO: session=0, config_index=0
             (… no further Media Start Request; none for AUDIO2 …)
15:12:28.608 sample: spotify_session=PLAYING(3)  ohu_in_focus_stack=0   ← resumed, stays PLAYING to end
15:16:19.458 sample: spotify_session=PLAYING(3)  ohu_in_focus_stack=0   (last sample)
```

**Counts:** `acquiring transient system audio focus` **0** · `leaving system audio focus alone`
**0** · `Audio Focus Request: GAIN` **0** · `Audio Focus Request: stream=` **1** (RELEASE path) ·
`Media Sink Setup Request:` **2** (VIDEO, AUDIO2) · `Media Start Request` **4** (all VIDEO) ·
latch line **0** · `requestAudioFocus()` by `com.andrerinas.headunitrevived` **0**.

**fps:** 50–60, mean 53.6, `dropped=0`, `skipped=0` over 53 windows.

**Sampler (18 points, 15:11:57–15:16:19):** `ohu_in_focus_stack=0` every point; live
`dumpsys audio` focus stack at end held only `com.spotify.music` (GAIN, uid 10222).

Brief PASS conditions are all *technically* satisfied (Spotify playing at the end; no `acquiring…`
line, so nothing to sit within 5 s of; no latch line) — but that is a green that proves nothing,
because the grab decision never ran. INCONCLUSIVE is the honest verdict and R2 is why.

---

## R2 — reachability: what Self Mode actually opens  *(same capture as R1)*

- **`Media Sink Setup Request:`** — `3 on channel VIDEO` (15:12:01.789); `1 on channel AUDIO2`
  (15:12:01.832). No SPEECH (`AU1`) or MEDIA (`AUD`) channel — matches
  `ServiceDiscoveryResponse.kt:161–188` gating `ID_AU1`/`ID_AUD` behind `!isSelfMode`.
- **`Media Start Request`** — `VIDEO: session=0, config_index=0` (15:12:04.596), then three more
  `RECV: VIDEO Media Start Request` fragments. **Zero for AUDIO2.** The SYSTEM channel is configured
  and then idle for the whole session.
- **`Audio Focus Request:` (protocol, both forms, in order):**
  - 15:12:01.765 `AapControlService … Audio Focus Request: RELEASE` → `Sending immediate
    AudioFocusNotification: STATE_LOSS (always-grant)` → 15:12:01.769 `AapAudio …
    Audio Focus Request: stream=3, type=4` / `Releasing audio focus`.
  - No `GAIN` form anywhere in the capture.

So the only entry points into the changed code — an AUDIO2 channel *starting*, or a protocol GAIN
we choose to honour — neither occurred. AUDIO2 opened but never started; the phone only ever
released.

---

## R3 — positive control: `playback-focus-mode=2` (NEVER)

### **INCONCLUSIVE** — identical to R1; the NEVER decline line is as unreachable as the AUTO grab

- **Settings written:** as R1 but `playback-focus-mode=2`.
- **Radio state:** as R1 (BT on both sides, A2DP speaker on the phone, `:5277` listening).
- **Drive:** Spotify to the speaker; `google.navigation:` at +12 s; `mock_drive.sh` for the hold
  (~3 min 40 s).
- **Discard-rule check:** clean (`performHandshake` ×1, `createGroup` ×0, `MATCH` ×0,
  `Magic Garbage` ×0, no p2p interface).

**Counts:** `acquiring transient system audio focus` **0** · `leaving system audio focus alone`
**0** · `Audio Focus Request: GAIN` **0** · `Audio Focus Request: stream=` **1** (RELEASE path,
`type=4`, 15:19:31.916) · `Media Sink Setup Request:` **2** (VIDEO, AUDIO2) · `Media Start Request`
**2** (VIDEO) · latch line **0** · OHU `requestAudioFocus()` **0**.
**fps:** 50–59, mean 54.5, `dropped=0` over 44 windows.
Sampler: `ohu_in_focus_stack=0` every point; Spotify `PLAYING(3)` every point (a Gearhead-caused
pause at 15:19:34 fell between two 15 s samples and had resumed by the next).

Brief PASS condition 1 ("wherever R1 printed `acquiring…`, R3 prints `leaving…alone (mode=NEVER)`")
is vacuous — R1 printed it 0 ×. Condition 2 ("zero `Audio Focus Request: stream=`") — R3 has the
same 1 RELEASE-path line R1 had. Condition 3 ("our package not in the focus stack") — holds. This
is the matched INCONCLUSIVE pair the brief anticipated in §6: with the code path unreached, NEVER
looks exactly like AUTO.

---

## R4 — steady-state regression guard

### **PASS**

- **Settings written:** `log-level=0`, `auto-start-self-mode=true`, `enable-audio-sink=true`,
  `static-audio-focus=false`, `playback-focus-mode=0`. `playback-focus-self-defeating` absent.
- **Radio state:** **phone Bluetooth disabled** (`svc bluetooth disable`, verified
  `enabled: false` / `state: OFF`; it did not self-revert during the run) — no BT audio device
  connected to the phone, per the brief. Head-unit side unchanged; `:5277` listening.
- **Drive:** `mock_drive.sh` + `google.navigation:` for the whole 10 min 38 s hold. Spotify was
  playing on the phone's own speaker for all but the first ~30 s (a Gearhead startup pause) — not
  required by the run, left as an extra signal.
- **Discard-rule check:** clean. `performHandshake` ×1, `createGroup` ×0, `MATCH` ×0,
  `Magic Garbage` ×0, no `Self Mode disconnected`, no `p2p-wlan0-*`. Startup-only surface churn
  (`Decoder stopped: New surface`, `display id=22 disconnected`) at 15:24:09–13, nothing after.
  Capture last timestamp `15:34:43.433` vs hold end `15:34:43.431`.

**Throughput (126 windows, ~10 min 38 s):**

| metric | value |
|---|---|
| fps min / max / mean | **49 / 60 / 52.9** |
| windows below 45 fps | **0** |
| total `dropped` | **0** |
| total `skipped` | 2 |
| total `concealed` | 0 |
| disconnects | 0 |

**Counts:** `taking system audio focus is stopping the phone's own playback` **0** ·
`media stopped …ms after taking audio focus` **0** · `acquiring transient system audio focus` **0**
· `Audio Focus Request: GAIN` **0** · OHU `requestAudioFocus()` **0**.
Sampler (21 points, 15:24:05–15:34:12): `ohu_in_focus_stack=0` every point.

Both PASS conditions met: 45–60 fps with `dropped=0` for the whole run (proving the session was
actually rendering), and zero latch lines.

---

## R5 — the latch's reachability, checked

### **PASS** (expected: absent)

Read with the app force-stopped, via `run-as … cat shared_prefs/settings.xml`:

```
playback-focus-self-defeating   → ABSENT
playback-focus-mode             → 0
```

The latch never armed. This is the structural result the brief predicted: `noteStopWhileHoldingFocus`
only counts `channel == ID_AUD` (MEDIA), Self Mode never announces `ID_AUD`, and — as R2 shows —
Self Mode never even *starts* the one audio channel it does announce (AUDIO2/SYSTEM). No escalation:
the reading of the announcement guards in brief §3 holds.

---

## Anything the brief did not ask about

- **Every Self Mode connect on this rig briefly pauses the phone's local media**, via a Gearhead
  `requestAudioFocus(req=GAIN)` at session start (seen in R1, R3, R4). It resumes on its own in
  15–30 s. Nothing to do with this branch — but if a user reports "starting Self Mode stops my
  music for a moment", that is the cause, and `playback-focus-mode` will not change it.
- **The RELEASE path emits `Sending immediate AudioFocusNotification: STATE_LOSS (always-grant)`
  and then `AapAudio … stream=3, type=4` on every Self Mode connect**, before any channel carries
  audio. Harmless, but it means a brief keying "we asked the system for focus" off a bare
  `AapAudio … Audio Focus Request: stream=` match will get a false positive on this route. Match the
  honoured GAIN form (`type=1`, preceded by `Audio Focus Request: GAIN`) instead.
- **Video throughput on the D-POCO in Self Mode is steady in the low-to-mid 50s fps** (`c2.qti.avc.decoder`,
  H.264), `dropped=0`, across 25+ minutes of cumulative projection this round, with `mock_drive`
  panning Maps the whole time. No stalls, no decoder restarts after the startup surface handoff.
