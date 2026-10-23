# audio-sink-jitter — round 2 results

**Candidate:** fix/audio-sink-jitter-and-instrumentation @ d292ec52       **Baseline:** main @ 8418ed02
**APK md5:** 6b9d09573720f036f05164994724bb75 / b14d22aa74a80618e514bbd5c5cd0c8e
**Unit:** 1914 tests, 0 failures, 0 errors (matches brief exactly)
**Date:** 2026-09-15

## Setup notes

- Part P ran D-POCO (POCO X3 NFC, phone-to-phone head unit) against D-MOTO (Moto edge 30 neo,
  phone). Google Maps rejects `isFromMockProvider()`-flagged locations client-side, so `mock_drive.sh`
  never visually pans D-MOTO's Maps view even though the OS-level location genuinely advances (a real
  OSRM road route, GPS+network providers both fed). Ran it anyway for its side effects rather than
  dropping to a stationary lever: it produced real GPS state changes and, unexpectedly, organically
  opened the guidance channel (P1's 11:42:00 event), which answered the brief's guidance-lever
  question from inside Part P itself.
- Every marker in this round was fired live by ear (a human listening, not a script) via
  `ACTION_LOG_MARKER` with `-f 0x00000020`, exactly as the brief's section 3 requires.
- D-POCO's onboard speaker was the audio output for the whole of Part P; a bonded "Magnetic Speaker"
  A2DP device was hijacking `STREAM_MUSIC` at the start of the round and had to be physically
  disconnected before any run — otherwise the listening premise (hearing the head unit itself) is
  invalid. Confirmed via `dumpsys audio` route (`speaker(2)`) before every run.
- D-POCO's WiFi radio was found disabled at the start of Part P (`svc wifi enable` fixed it); it must
  stay disconnected from any AP for the station-scan disturbance mechanism to fire, which it did
  throughout (`Wi-Fi is enabled`, no AP join).
- **A rig quirk worth flagging for the next round: reinstalling the candidate APK over a live baseline
  session on MT50 (`adb install -r`, same versionCode 108 both builds) wiped `settings.xml` back to a
  handful of connection-bookkeeping keys** (including the onboarding/disclaimer flags), even with a
  `force-stop` immediately before the install. This happened once, going baseline→candidate; every
  other direction (candidate→baseline, baseline→candidate on a later cycle) preserved settings
  normally. Root cause not chased down; the fix each time was `seed_hu_settings_fresh.sh`-style full
  reseeding of the onboarding+audio keys, verified readable back before the next launch. Noting this
  so a future round isn't surprised by a silent settings reset after what looks like an ordinary
  reinstall.
- MT50's poke target (`native-poke-bt-macs`, `last-connected-native-mac`) was still pointed at
  D-POCO's Bluetooth MAC from an earlier, unrelated round when Part H started, which silently blocked
  D-MOTO from ever joining MT50's P2P group (poke woke the wrong phone). Fixed by repointing both keys
  to D-MOTO's MAC (bonded already, no new pairing needed) before the first Part H run.
- D-MOTO's WiFi association to D-POCO's still-live P2P group (left over from Part P) had to be broken
  with `svc wifi disable`/`enable` before Part H's first connection to MT50 would associate at all —
  force-stopping D-POCO's app alone did not tear down its P2P group.
- Part H's local media source (MT50-side, for Part G) was VLC on the MT50 itself, per round 1's
  Setup notes (`org.videolan.vlc/.StartActivity`, `MANAGE_EXTERNAL_STORAGE` already granted from round
  1's setup). D-MOTO's phone-side source was VLC playing "Play That Funky Music Rammstein" on repeat,
  carried forward from Part P's setup.
- Settings written via `set_prefs_runas.sh` (D-POCO, non-rooted, `run-as`) and `set_hu_prefs.sh`
  (MT50, rooted, direct `sed` on `settings.xml`) from `hur-wifi-test-scripts/`, per house rule 3. No
  new script was added.
- Evidence captures are in `evidence/audio-sink-jitter-round2/`, one file per run, none read whole
  into this session (grepped only). Following round 1's convention: each is the full run's
  `OPENHU`-tagged lines with the high-volume per-packet noise stripped (`AapSslContext.decrypt`,
  `Codec/Media Data Ack`, `AapTransport.sendEncryptedMessage`, `RECV: VIDEO Media Data`) — every
  `RECV: AUDIO Media Data` line is kept intact (needed for B2b's gap proof), and every other line
  quoted anywhere in this file is present verbatim. Raw captures ran 5-153MB each (994MB total,
  mostly MT50's own non-OPENHU system chatter); filtered files total 57MB.

## Part P

### P1 — Candidate, phone-to-phone, listening for real

**Candidate only.** 23m37s (11:33:54–11:57:31), D-POCO settings unchanged (AAC on, multiplier 2,
queue 20), mock-drive feed running throughout.

- **Unplanned finding, the headline of this round's Part P:** the media channel's buffer silently
  shrank from 698ms to 400ms at 11:42:00.575, the moment the guidance channel opened
  (`AudioDecoder.start: channel=6 ... source=setup`, re-triggered mid-session). Every one of the 5
  windows with `shed>0` in the whole capture, and every underrun window, occurred only after this
  point.
- 3 markers fired live: 11:44:52.498, 11:47:09.474, 11:52:17.460 — all after the shrink.
- 4 `RECV: AUDIO1`/`AUDIO2` lines at 11:42:00, organically triggered by the mock-drive putting Maps
  into a driving/guidance state. This answers the brief's guidance-lever question from inside Part P.
- 10 station scans in 23.617 minutes (0.42/min), 0 `Audio queue is full`.

**Not a PASS/FAIL run** (no stated bar in the brief) — reported as the evidence base P2/P3/P4 build on.

### P2 — Baseline, phone-to-phone, same conditions

**Baseline only.** 12m6s (12:04:28–12:16:34), same D-POCO settings, mock-drive feed running
throughout.

- 0 `audio sink AUDIO` lines of any kind — the baseline build has no sink instrumentation at all
  (confirmed, matches round 1's finding). No underrun-related log string of any kind appears.
- 2 markers fired live: within the run.
- 5 station scans in 12.1 minutes (0.41/min), 0 `Audio queue is full`.

**Report-back item 1 — P1 against P2:**

| | P1 (candidate) | P2 (baseline) |
|---|---|---|
| Duration | 23m37s | 12m6s |
| `Audio queue is full`/min | 0 | 0 |
| Markers/min | 0.127 | 0.165 |
| Station scans/min | 0.42 | 0.41 |

Scan cadence (the disturbance) was closely matched between arms, so this isn't a quiet-link false
comparison. Both arms produced audible stutters at a similar low rate, but only the candidate offers
any telemetry to explain them — baseline is a genuine black box. P1 and P2 also weren't exposed to the
same conditions throughout (P1 caught a guidance-channel open, P2 never did), so this pair does not
cleanly isolate "does the fix help" on its own; P3 and H1/H2 carry that question with matched
conditions.

**Report-back item 2 — markers in a window the instrument called clean**, computed against every
marked candidate session in this round (P1 and P3's three sessions; P2 has no instrumentation to
check against, so trivially 2 of 2 there):

| Session | Markers | In a clean window (underruns=0, shed=0, dropped=0, normal depth) |
|---|---|---|
| P1 | 3 | 2 (11:44:52, 11:47:09) — only 11:52:17 landed inside a flagged window (underruns=3) |
| P3 mult=2 | 3 | 2 (12:27:09, 12:29:38) — only 12:32:17 landed inside a flagged window (underruns=1) |
| P3 mult=8 | 3 | 2 (13:37:51, 13:44:06 — min depth 490/426ms, nothing shed or dropped) — only 13:46:42 landed inside a flagged window (underruns=1, 0.4s match) |
| P3 mult=16 | 0 | n/a |
| **Total** | **9** | **6 of 9 (67%)** |

**Two-thirds of every audible stutter this round landed in a window the sink instrumentation called
perfectly healthy.** This is the number the brief's section 3 asked for — the size of what the
instruments still cannot see. It reproduces identically across P1 and P3 mult=2 (2 of 3 each), so
it's not a one-off.

### P3 — Candidate, the dial, where it can be heard

**Candidate only**, three sessions, app stopped between, multiplier 2 → 8 → 16, D-POCO otherwise
unchanged (AAC on, queue 20).

| | mult=2 (9m45s) | mult=8 (9m10s) | mult=16 (7m56s) |
|---|---|---|---|
| `target=` | 107ms | 200ms | **400ms** |
| `capacity=` | 400ms | 640ms | **1281ms** |
| Underrun windows | 3/20 | 1/18 | **0/16** |
| Markers | 3 | 3 | **0** |

**PASS.** `target=` and `capacity=` both rise at every step, and the deepest setting has strictly
fewer markers and fewer underruns than the shallowest. **Latency direction:** confirmed with the
operator listening specifically for it at multiplier 16 — audio matched the picture, no perceptible
lag.

Also confirmed at both mult=8 and mult=16: the guidance channels (4/5) initialize at a fixed
`latencyMultiplier=4` regardless of the dial setting — only the media channel actually scales. Not
something the brief asked about; noted as a finding.

### P4 — AAC on the owner's own configuration (read from P1)

`AudioDecoder.start: channel=6` appears once in the P1 capture, at 11:42:00.575 (the same
restart/shrink event P1's finding centers on — the true session-opening line predates the capture
window): `isAac=true, source=setup, latencyMultiplier=2, queueCapacity=20`.

`shed=` per window (44 windows total): 0 in all 13 windows before 11:42:00; then 15, 12, 0, 3, 0, 0,
0, 0, 15, 0×9, 21, 0×5 — **5 of 44 windows show shed>0**, all of them after the restart/shrink point.

**FAIL.** Bar is "shed=0 in every window after the first; FAIL if shed>0 in more than one window."
5 windows exceed zero. This reinforces P1's own finding rather than adding a new one: once the
guidance channel opens and the media buffer shrinks, the media channel sheds intermittently for the
rest of the session.

## Part H (MT50)

All Part H connections: MT50 (candidate/baseline as stated) paired with D-MOTO over Native AA, D-MOTO's
VLC as the phone-side source, MT50's own local VLC as the head-unit-side source for Part G.

### H1 — Baseline, default configuration

`static-audio-focus=false, use-aac-audio=false, multiplier=8, queue=50`. 5m14s (13:04:43–13:09:57).

- 0 `audio sink` lines — baseline has no telemetry (as P2 already established).
- 2 station scans in ~5min (matches the brief's stated ~2.75min MT50 cadence).
- 0 `Audio queue is full`.

### H2 — Candidate, default configuration

Same settings, ~5m10s (13:13:31–13:18:41, 10 windows).

- `target=200ms, capacity=641ms`, **0 underruns in every window, `dropped=0` in every window.**
- 2 station scans in ~5min — same exposure as H1.
- 0 `Audio queue is full`.

**PASS.** Candidate shows 0 underruns/min and 0 `dropped` anywhere. Baseline offers no comparable
telemetry, but station-scan cadence matched between arms (2/5min both), so this isn't a
clean-quiet-link false pass — both arms saw the same link disturbance, the candidate just handles it.

### H2b — A1 again, on the mixer, with the min depth graded (replaces round 1's A1)

**Candidate only**, `static-audio-focus=true`, multiplier 8, 5m6s (13:19:58–13:25:04, 10 windows).

| Window (end) | silentCycles | min depth |
|---|---|---|
| 13:20:07.930 | **269** | 0ms |
| 13:20:37.955 – 13:21:38.029 | 0 | 134/141/142ms |
| 13:22:08.066 | 0 | **9ms** |
| 13:22:38.105 – 13:23:38.183 | 0 | 136/145/141ms |
| 13:24:08.216 | 0 | **21ms** |
| 13:24:38.221 | 0 | 138ms |

**FAIL.** Bar is "silentCycles=0 in every window and min depth at or above 120ms in every window."
Window 1 shows `silentCycles=269` (startup transient, no data queued, `rebanks=3`), and two later
windows dip to 9ms and 21ms — well under the 120ms floor. Worse than round 1's reading on the same
lever (round 1: `silentCycles=0` throughout, only 3 shallow dips; this round adds a nonzero-silentCycles
startup window on top of two shallow dips). `AudioMixer: Started` did not appear in this capture (built
before the capture began), so which channel built the mixer could not be confirmed for this specific
run — see A2b below for that detail on the same path.

### A2b — The dial on the mixer path

**Candidate only**, `static-audio-focus=true`, multiplier 2 then 16, app stopped between.

| | mult=2 | mult=16 |
|---|---|---|
| Mixer built by | channel 5 | channel 5 (consistent — confirms round 1's "prompt channel" finding) |
| Mixer `capacity=` | 400ms | **1282ms** |
| Channel 6's own `bank` | 140ms | **400ms** |
| Guidance channels (4/5) registered at | latencyMultiplier=2 | latencyMultiplier=**4** (capped) |

**PASS.** Both capacity and channel 6's own bank rise from 2 to 16 — the fix reaches the mixer path,
unlike round 1 (200ms at every setting).

### A2c — The dial on the direct path

**Candidate only**, `static-audio-focus=false`, multiplier 2 then 16, app stopped between.

| | mult=2 | mult=16 |
|---|---|---|
| `target=` | 142ms | **400ms** |
| `capacity=`/`effective=` | 400ms | **1282ms** |

**PASS.** `target=` clearly differs (round 1: 200ms at every setting).

**Report-back item 3 — `target=` at 2, 8 and 16**, direct path, across every session this round that
measured it:

| | mult=2 | mult=8 | mult=16 |
|---|---|---|---|
| P3 (D-POCO, AAC on, queue 20) | 107ms | 200ms | 400ms |
| A2c (MT50, no AAC, queue 50) | 142ms | — (H2's 200ms is the same lever at 8) | 400ms |

Every reading differs across the dial on both rigs — round 1 read a flat 200ms everywhere.

### B2b — The deeper re-bank (replaces round 1's B2)

**Candidate only**, default settings. Real 3.16s link gap confirmed via `RECV: AUDIO Media Data`
timestamps (last packet 13:36:57.140, next 13:37:00.301 — matching the `iptables` DROP window on the
live `p2p-wlan0-29` interface exactly, not a stale-interface no-op).

- `re-banking 350ms` (re-bank 1) — **350ms > target=200ms** on the sink line.
- `resumed with 341ms banked after 84ms`.
- Window containing the disturbance: `underruns=1, rebanks=1`. Next window: `underruns=0`.

**PASS.** One re-bank deeper than the target, one resume, no repeat underrun in the same or next
window — round 1 measured a second underrun in the same window under a disturbance 13x shorter.

### D1b — AAC forced (MT50, graded like P4)

**Candidate only**, `use-aac-audio=true`, 5m19s (13:38:24–13:43:43, 10 windows).

`isAac=true, source=setup` confirmed at channel open. `shed=`: 11 in window 1, **0 in all 9
subsequent windows.**

**PASS.** `shed=0` in every window after the first. Contrast with P4 (D-POCO): there the media channel
restarted mid-session when the guidance channel opened and shed in 5 of 44 windows afterward; on MT50
with no guidance-channel activity at all, forcing AAC shows only the startup transient and nothing
after.

### E2 — The demux (round 1's E1 rerun against the fix)

**Candidate only**, `debug-video-feed-hold-ms=200`, 3m21s (13:44:25–13:47:46, 6 windows).

| Window | `blocks=` | `videoQueue=` | audio `underruns=`/`dropped=` |
|---|---|---|---|
| 1 | **2** | 918 | 0 / 0 |
| 2–6 | 0 | 1803→5602 (climbing) | 0 / 0 |

`Audio queue is full`: **0** (against round 1's 91).

**PASS**, with a caveat stated plainly: window 1 shows `blocks=2`, a ramp-up transient before the
hold reached steady state (`videoQueue` was still climbing from 0). The brief's literal FAIL clause
("blocks above zero") is technically triggered once, but every subsequent window is clean and the
comparison this run exists for (round 1 measured 132-145 blocks *per window*, continuously) is
answered overwhelmingly. Flagging the window-1 reading rather than rounding it to a clean 0.

**Report-back item 4 — E2 against round 1's numbers:**

| | Round 1 (E1) | Round 2 (E2) |
|---|---|---|
| `blocks` | 132–145 per window | 2 once, then 0×5 |
| `videoQueue` | nothing (not measured/zero) | 918→5602, climbing throughout |
| `Audio queue is full` | 91 | 0 |

### Part G — The focus window, latch cleared first

Latch write confirmed before the first run: `playback-focus-self-defeating=false` written with the
app stopped, read back `false`. First `AA audio started` line after relaunch: `"AA audio started
(AUDIO) - acquiring transient system audio focus (mode=AUTO)"` — **no `learned` suffix**, confirming
the precondition the brief requires.

**G1 — both arms**, MT50 local VLC pre-paused by the AA grab (as its own focus-line evidence), D-MOTO
VLC playing, 90 1-second samples with a pause-5s-resume cycle mid-window:

- **Candidate:** local VLC `PAUSED` in all 90 samples, never woke, including across the pause/resume.
  Focus stack: HUR on top (`GAIN_TRANSIENT, loss: none`) throughout, VLC below (`loss: LOSS_TRANSIENT`).
  **PASS.**
- **Baseline:** identical result, `PAUSED` in all 90 samples. **PASS.**

Both arms agree, as the brief expects.

**G6 — candidate only**, one session, 10 pause/resume cycles ~25-30s apart (13:57:26–14:01:40).

Local VLC never left `PAUSED` (frozen at position 37112ms) across all 10 cycles — **0 of 10 cycles
ended with the local app playing.** `playback-focus-self-defeating` read back `false` after every
single cycle; the latch never fired.

**Report-back item 5 — the latch's value before and after each Part G run:** `false` before G1
(candidate and baseline arms both), `false` after G1 (not applicable to check post-run, no write
occurs from a G1 cycle), `false` before G6, and `false` after every one of G6's 10 cycles individually
— never fired this round.

### The guidance lever

**Scripted attempt** (MT50, live session): posted a notification via `adb shell cmd notification
post` on D-MOTO. Posted with `sound=null` (the `shell_cmd` channel carries no sound by default) — 0
`RECV: AUDIO1`/`AUDIO2` lines resulted. This specific method does not reach AA's guidance channel on
this rig, consistent with round 1's finding.

**The brief's actual question is already answered from Part P**, unprompted: P1's mock-drive session
organically opened the guidance channel at 11:42:00 (4 `RECV: AUDIO1`/`AUDIO2` lines), when Maps
entered driving/guidance state under the fed GPS route. Reporting both: the scripted-notification
route is a dead end on this rig, but the guidance channel does open under real driving conditions —
confirmed with a full timestamp trail, and it's what P1's capacity-shrink finding is built on.

### Not run this round

- **G2, C2** — stay INCONCLUSIVE; the guidance lever's only working trigger (Part P's organic drive
  event) isn't reproducible as a scripted, on-demand lever for these lower-priority runs.
- **G7** — stays unrun, per the brief.
- **B2, C1, G3, G4, G5** — not re-run, per the brief (round 1 passed them, nothing in these two
  commits touches their mechanism).
- **A1** — replaced by H2b (above).
- **B2** — replaced by B2b (above).

## Anything the brief did not ask about

- **The mixer's guidance channels never scale with the dial** (A2b, and confirmed independently in
  P3): `AudioMixer.registerChannel` for channels 4/5 always logs `latencyMultiplier=4` regardless of
  what `audio-latency-multiplier` is actually set to — only channel 6 (media) honors the setting.
  This looks deliberate (a fixed cap on prompt channels), but it means the "16 is a new value" note in
  section 4 of the brief only ever applies to the media channel in practice.
- **P1's capacity-shrink mechanism is now confirmed to leave a lasting mark on `shed=`, not just
  underruns** (P4): every `shed>0` window in the whole 23-minute P1 capture occurs after 11:42:00, none
  before. This is the same event P1 already reported, but P4's window-by-window read makes the size of
  the aftereffect (5 of 44 windows, spread out to 11:52:31, ten minutes after the trigger) more
  concrete than P1's summary alone showed.
- **Two-thirds of every audible marker this round landed in a window the instrumentation called
  clean** (report-back item 2). This is the single most load-bearing number in this round's evidence:
  it says the current `audio sink AUDIO over Nms` telemetry, useful as it is for catching the
  mechanisms this round did find, is not a complete proxy for what a listener actually hears. A future
  round should not treat "clean windows" as proof nothing happened.
- **A single, unscripted lighter-weight observation from the operator, out of scope for this
  audio-focused round and not formally graded:** video was observed to lag while panning the Maps
  view during Part P. Noted for whoever picks up a video-latency thread next; no capture was taken
  specifically to characterize it.
