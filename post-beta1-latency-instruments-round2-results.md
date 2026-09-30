# post-beta1-latency-instruments — round 2 results

**Candidate:** `fix/post-beta1-latency-instruments` @ `3200f004` (six commits on `main` `4e5be786`)
**Baseline:** none built — instruments do not exist on `main`, every run is candidate-only (brief §1)
**APK md5:** `7340fbdce0de981911ff2d481dcfc12e` — `com.andrerinas.headunitrevived_3.3.0-beta2_debug.apk`, versionCode 101
**Units:**
- **D-HU** `27870808938846` — UNISOC `MT50_YT610E4GFPSL_U`, board `uis7861_6h10`, Android 14 / api 34, 3745 MB, projection 1920x1080. Gearhead 17.3. Component `c2.unisoc.avc.decoder`. Self Mode **legacy (AA < 17.4)** path.
- **D-POCO** `4f4027e9` — Xiaomi `M2007J20CG` (Poco X3), board `sm6150`, Android 15 / api 35, 5558 MB, 1920x1080. Gearhead `17.5.663204-release`. Component `c2.qti.avc.decoder`. Self Mode **17.4+ direct-to-`127.0.0.1:5277`** path.
- **D-MOTO** `ZY22GC3BM4` — motorola `edge 30 neo`, board `miami`, Android 14 / api 34, 7462 MB, portrait **1080x1920**. Gearhead 17.3. Component `c2.qti.avc.decoder`. Self Mode **legacy** path.
**Date:** 2026-08-27

## Summary

| Run | Verdict | One line |
|---|---|---|
| R0 | **PASS** | `assembleGithubDebug` clean; **816 tests, 0 failures, 0 errors** (77 result files). `InboundRateMonitorTest` `1000L` fix is folded into the branch. |
| R1 | **PASS** all three | 5-min sessions, `optionalKeys=none`, `presented=` + `decodeLatency=` on every window (0 unreadable), ≥8 inbound-rate lines with non-zero `video=`, no `Decoder rejected`. |
| R2 | **PASS** all three | Low-latency on. D-HU tier `realtime`, both qti devices tier `vendor`. No rejection on any component. **Latency unchanged from R1 within 0 ms on all three** — the keys are accepted and ignored. |
| R3 | **UNTESTABLE** | No `.mtk.`/`mediatek` component on the rig. `c2.unisoc.avc.decoder` (D-HU) + `c2.qti.avc.decoder` (D-POCO, D-MOTO). Confirmed from every `findBestCodec:` line. |
| R4 | **PASS** all three | `force-software-decoding=true` + `software-video-decoder=0` selects `c2.android.avc.decoder`, `preferHardware=false`, `optionalKeys=realtime [priority, operating-rate]`, **no `Decoder rejected optionalKeys=realtime`** on any device. |
| R5 | **PASS** (D-POCO) | 5 Home-press cycles. `Decoder rejected optionalKeys=` = 0 anywhere. The surface-loss path *was* reached 3× (`Decoder start aborted: the surface went away mid-configure`), each ~60 ms after an `onSurfaceDestroyed`, each followed by a clean reconfigure. Session fully recovered. |
| R6 | **PASS** (D-MOTO) | `headunit://exit` → `Hiding reconnecting overlay - the session ended` (×1, immediately). `frames resumed` = 0 after exit. `feed queue resized` never fired; `queue=30 frames` on every config line. |
| R7 | **PASS** (capture mechanism), export UI step **could not be completed unattended** | 25-min VERBOSE capture rolled through ≥3 segments, kept the 2 newest (16 MB disk bound), `--- continued from the previous log file ---` present, no `produced 0 bytes`, no `APPLOG_FILE` switch, `log-source` still `0`. The in-app **Export Logs** button ANR'd the app (see Setup notes + findings). |
| R8 | **PASS** all three | `This unit has no 5 GHz band` absent from every R1 capture. |

**Shipping question:** the low-latency ladder configures on real hardware without a single rejection on
any of the three components, and **moves neither median nor p95 `decodeLatency` on any of them**
(D-HU 20→20 ms, D-POCO 16→16 ms, D-MOTO 18→18 ms median; p95-field 26→26, 19→19, 22→21). This is the
brief's own "accepted the key and ignored it" outcome. R4 confirms `KEY_PRIORITY` and
`KEY_OPERATING_RATE` cannot fail a configure on any of the three (0 rejections on the `realtime` rung).
The `presented=` regression guard holds. Nothing here argues for removing a key; nothing here argues
for defaulting one on.

## Setup notes

**Scripts.** `hur-wifi-test-scripts/build_hur.sh` + `run_unit_tests.sh` (R0), `set_prefs_runas.sh`
(all settings writes), a pushed one-line `cp` restore script (teardown). **Added
`hur-wifi-test-scripts/round-post-beta1-latency-instruments/run_selfmode.sh`** — one Self Mode
capture on one device with the projected screen kept moving by periodic centre swipes (AA drops a
static stream, brief R1). Env flags `KILL_GH` (force-stop Gearhead first — legacy path only),
`SEND_INTENT` (fire `ACTION_START_SELF_MODE` explicitly), `SETTLE` (delay before the swipe loop
starts). `parse_throughput.py` in the same dir parses the throughput / inbound-rate / config lines.
All left in place.

**D-POCO's 17.4+ path breaks if you force-stop Gearhead.** The AA-17.4+ route connects directly to
Gearhead's developer **head-unit server on `127.0.0.1:5277`** (`DeveloperHeadUnitNetworkService`).
`run_selfmode.sh`'s default `KILL_GH=1` (correct for the legacy path, which is what round 1 and the
self-mode thread used) killed that service, and it **does not auto-restart** — 5277 stayed down through
force-stop cycles and a 2-minute idle, so every D-POCO retry failed with
`Headunit Server (127.0.0.1:5277) is NOT running` → `All launchers failed` and `NetworkDiscovery`
looped on the dummy-VPN subnet without recovering. The operator re-enabled it by hand (AA settings →
version row ×10 → Developer settings → **Start head unit server**), after which D-POCO R2 with
`KILL_GH=0 SEND_INTENT=0` formed cleanly. **For any future D-POCO Self Mode run: do not force-stop
Gearhead.** 6 failed attempts before this was understood.

**A double launch races the 17.4+ direct connect.** With `auto-start-self-mode=true` *and* an explicit
`ACTION_START_SELF_MODE`, D-POCO fired two overlapping launches; the first gave up
(`All launchers failed`) and tore down the projection while the second's socket connected, and the
loopback socket then tripped `AapService.quiesceWirelessForWiredSession | USB session established` —
the whole thing collapsed to `nothing connected within 10000ms`. `SEND_INTENT=0` (rely on auto-start
alone) fixed it. The legacy devices (D-HU, D-MOTO) tolerate the double launch.

**R7's in-app export ANR'd the app; the capture segments are the evidence instead.** After the 25-min
session ended, tapping **Settings → Export Logs** hung the app (`Open Headunit isn't responding`,
`Input dispatching timed out … SettingsActivity`). `LogExporter.saveLogToPublicFile` fell through to
its ring-buffer fallback (`Runtime.exec("logcat -d") + process.waitFor()` **on the main thread**,
`LogExporter.kt:318-324`) instead of the segment-join path, and on this ROM `logcat` is gated by the
LogAccess consent dialog, so unattended `waitFor()` never returned. It also left a stray 0-byte
`HUR_Log_20260827_141006_842.txt` (removed during teardown). Contributing: the session had been left
to bounce for ~3 min through repeated `headunit://exit` before the export was triggered, which likely
cleared `captureFile` / `capturePreviousFile`. **The R7 conditions were verified against the two
retained capture segments pulled off the device**, which is what the merged code actually produces.
See R7 below and the findings section.

**The consent dialog only appeared in R7.** R1–R6 read config from the `settings.xml` readback plus
`findBestCodec:` / `optionalKeys=` (brief §3), so no run outside R7 depended on a human. R7 needed the
operator to tap "Allow one-time access" on D-HU for the first ~8 s, then leave `logcat` alone (the
self-mode round 2 §7a recipe); done, and the capture produced valid rolled segments.

**Every device was offline the whole round** (`Active default network: none` on all three from the
first check), so every Self Mode run took the dummy-VPN path. Same as the self-mode thread. Not
changed by this session.

**No D-MOTO `CarErrorDisplay`** in any run (self-mode round 2's concern did not recur). D-MOTO Self
Mode formed and held on every run.

**Settings restored** on all three from a round-start backup — `diff` clean on every tracked key.
`wifi-connection-mode` unmoved: D-HU=3, D-POCO=1, D-MOTO=2. `READ_LOGS` was **not** granted by this
session (R7 relied on the operator's tap). Candidate APK left installed on all three per the
no-uninstall rule. The two R7 capture segments (`HUR_Log_20260827_1400*.txt`,
`…1407*.txt`) were left on D-HU.

**PC thermal.** Turbo already disabled (`no_turbo=1`). One `assembleGithubDebug` + one
`testGithubDebugUnitTest`, package temp peaked ~88 °C, no throttle, no power-cut.

## R0 — build and unit-test gate

**PASS**

- `./gradlew :app:assembleGithubDebug` — `BUILD SUCCESSFUL`. APK md5 `7340fbdce0de981911ff2d481dcfc12e`.
- `./gradlew :app:testGithubDebugUnitTest` — parsed from `app/build/test-results/testGithubDebugUnitTest/*.xml`:
  **816 tests, 0 failures, 0 errors** (77 result files). Exact match to the brief's expected 816
  (`main` 770 + 46).
- `InboundRateMonitorTest.kt:67` reads `assertEquals("…", 1000L, second.videoBytes)` on the branch —
  round 1's one-line patch is folded in. No manual patch needed. (A stale
  `stash@{0}` carrying that same 2000→1000 change is still in the working tree from round 1; harmless,
  left alone.)

## R1 — Self Mode baseline, per device

**PASS** on all three. Settings written (identical on all three, quoted from the readback):
`log-level=2`, `fps-limit=60`, `view-mode=0` (SURFACE), `video-codec=Auto`, `debug-video-low-latency`
absent, `force-software-decoding` absent, `log-capture-enabled` absent. Radio: offline, dummy-VPN
path. 5-minute sessions, centre-swipe animation throughout.

| Condition | D-HU | D-POCO | D-MOTO |
|---|---|---|---|
| readback matches, `optionalKeys=` only `none` | yes / `none` | yes / `none` | yes / `none` |
| `presented=` + `decodeLatency=` on every `Throughput over` line | 59/59 windows | 57/57 | 59/59 |
| `decodeLatency=unreadable` windows | 0 | 0 | 0 |
| `inbound rate over 30000ms` lines, `video=` non-zero | 9, all non-zero | 9, all non-zero | 9, all non-zero |
| `Decoder rejected optionalKeys=` anywhere | 0 | 0 | 0 |
| windows with `rendered=0` | 0/59 | 0/57 | 0/59 |
| `dropped` (sum over run) | 0 | 0 | 0 |

**Numbers (R1):**

| | D-HU `c2.unisoc.avc.decoder` | D-POCO `c2.qti.avc.decoder` | D-MOTO `c2.qti.avc.decoder` |
|---|---|---|---|
| `decodeLatency=` per-window median | **20 ms** | **16 ms** | **18 ms** |
| `decodeLatency=` per-window p95 (of the medians) | 21 ms | 16 ms | 22 ms |
| `p95=` field, per-window median (max) | 26 ms (62) | 19 ms (37) | 22 ms (247, first window) |
| mean `rendered`/`presented` ratio | **1.0001** | **1.0006** | **1.0021** |
| mean `fed` / `rendered` / `presented` per window | 86.7 / 86.7 / 86.7 | 243.9 / 243.8 / 243.6 | 242.9 / 242.7 / 242.5 |
| mean `inputWait` | 44.9 ms | 111.7 ms | 126.5 ms |
| mean `skipped` | 0.00 | 0.05 | 0.24 |
| `video=` inbound rate, mean | 154.6 kB/s | 505.9 kB/s | 197.1 kB/s |
| capability line | `featureLowLatency=false featureAdaptivePlayback=true`, `widths=[64,1920] heights=[64,3840]` | `…false …true`, `widths=[96,4096] heights=[96,4096]` | `…false …true`, `widths=[96,1920] heights=[96,1920]` |
| `getprop` model / board / api | `MT50_YT610E4GFPSL_U` / `uis7861_6h10` / 34 | `M2007J20CG` / `sm6150` / 35 | `motorola edge 30 neo` / `miami` / 34 |

**On the `rendered`/`presented` ratio:** ~1.00 in every window on all three, in R1 and R2, *with the
centre-swipe animation and 155–506 kB/s of video*. Per brief §3 this is a load signal: the paired
numbers (`fed` ≈ `rendered` ≈ `presented` to the frame, `inputWait` 45–127 ms, `skipped` ≈ 0) say the
output queue ran one deep and never found a second buffer ready. It is a measurement about these three
devices under this load, not a broken instrument and not a refutation of the 1.6× seen on the one
TextureView unit. The `presented=` regression guard is what this establishes on hardware.

## R2 — low latency on, per device

**PASS** on all three. Identical to R1 plus `debug-video-low-latency=true` (confirmed in each
readback). Same 5-min duration, same animation.

| | D-HU | D-POCO | D-MOTO |
|---|---|---|---|
| `Configuring decoder:` tier | **`realtime`** | **`vendor`** | **`vendor`** |
| key list (verbatim) | `[priority, operating-rate]` | `[vendor.qti-ext-dec-low-latency.enable, priority, operating-rate]` | `[vendor.qti-ext-dec-low-latency.enable, priority, operating-rate]` |
| `Decoder rejected optionalKeys=` | 0 | 0 | 0 |
| `Decoder accepted the format only with…` | 0 (configured on the first tier tried) | 0 | 0 |
| session decodes, full 5 min, watchable | yes, `dropped=0` | yes, `dropped=0` | yes, `dropped=0` |
| throughput windows | 59 | 64 | 58 |

**R1 → R2 `decodeLatency`, the A/B (six numbers):**

| Device | R1 median / p95-field-median | R2 median / p95-field-median |
|---|---|---|
| D-HU (`realtime`) | 20 ms / 26 ms | **20 ms / 26 ms** |
| D-POCO (`vendor`) | 16 ms / 19 ms | **16 ms / 19 ms** |
| D-MOTO (`vendor`) | 18 ms / 22 ms | **18 ms / 21 ms** |

**What a PASS means here:** the change did nothing to the number on any device. Every component
accepted its low-latency tier without a rejection and the measured decode delay did not move (median
identical to the millisecond, p95-field identical or 1 ms lower). That is the answer the round was run
to get. No search for a different setting was made.

**Note on D-HU's tier.** `c2.unisoc.avc.decoder` matches no vendor family in the ladder's name
matching, so its low-latency tier is `realtime` — the same AOSP-keys rung R4 targets by forcing the
software component. Both qti devices get `vendor` (`vendor.qti-ext-dec-low-latency.enable` + the two
AOSP keys). No device reached `vendor+reorder` or `low-latency`; none fell to `none`.

## R3 — the MediaTek reorder rung

**UNTESTABLE.** `findBestCodec:` on every run reads `hw=c2.unisoc.avc.decoder` (D-HU) or
`hw=c2.qti.avc.decoder` (D-POCO, D-MOTO); `sw=c2.android.avc.decoder` on all three. No `.mtk.` /
`mediatek` component anywhere on the rig. Not substituted. Covered by `DecoderConfigLadderTest` (JVM,
in R0's 816).

## R4 — positive control for the two AOSP keys, per device

**PASS** on all three. Settings: R2's plus `force-software-decoding=true`, `software-video-decoder=0`.
~2-minute runs.

| | D-HU | D-POCO | D-MOTO |
|---|---|---|---|
| `findBestCodec:` | `preferHardware=false, selected=c2.android.avc.decoder` | same | same |
| `Configuring decoder:` | `optionalKeys=realtime [priority, operating-rate]` | same | same |
| `Decoder rejected optionalKeys=realtime` after it | **0** | **0** | **0** |
| `Decoder configure abandoned` / exception-triage lines | 0 / 0 | 0 / 0 | 0 / 0 |
| session decodes (picture poor, as expected) | yes, `dropped=0` | yes | yes |
| sw-decode `decodeLatency` (context only) | ~215–670 ms | ~65–140 ms | ~46–53 ms |

The decisive line — **no rejection on the `realtime` rung on any of the three** — is the on-hardware
confirmation that `KEY_PRIORITY` and `KEY_OPERATING_RATE` cannot fail a configure, matching the AOSP
source reading in brief §2. The keys stay.

## R5 — a surface torn down mid-configure is not a rejected key (D-POCO)

**PASS.** Session live and decoding; `HOME`, wait 5 s, return to the app, ×5. Settings: R2's.

- `Decoder rejected optionalKeys=` — **0 anywhere in the capture.** The FAIL condition (a rejection
  whose message mentions the surface) did not occur.
- The surface-loss path *was* exercised: `VideoDecoder.start | Decoder start aborted: the surface went
  away mid-configure. Waiting for a new one.` fired **3×** (13:51:46.703, 13:51:57.766, 13:52:08.945),
  each **~60 ms after** an `AapProjectionActivity.onSurfaceDestroyed`, each followed by a successful
  `Configuring decoder: … optionalKeys=vendor …` on return. Session fully recovered (54 fps,
  `dropped=0`, `decodeLatency=17 ms`) after all 5 cycles.
- **String note:** the line that fired is `VideoDecoder.kt:1776` (`Decoder start aborted: … Waiting
  for a new one.`), the outer surface guard — not the ladder's `:1722`
  (`Decoder configure abandoned: … This is not a rejection of optionalKeys=<tier>.`) that brief §5
  quotes. Both are surface-loss handling; neither is a key rejection. Better than the brief's
  pre-registered "path not reached / INCONCLUSIVE".

## R6 — the overlay says why it was hidden (D-MOTO)

**PASS.** Session live (`c2.qti.avc.decoder`, 29–30 fps, `dropped=0`), then
`am start -a android.intent.action.VIEW -d "headunit://exit"`.

- `AapProjectionActivity.hideReconnectingOverlay | Hiding reconnecting overlay - the session ended` —
  **×1**, 13:33:31.019, one line after `AutomationActivity: Received intent … headunit://exit`
  (13:33:30.994).
- `Hiding reconnecting overlay - frames resumed` — **0** after the exit (0 in the whole capture).
- Secondary: `VideoDecoder: feed queue resized` — **0** this round, on every device (capacity never
  changed). `queue=30 frames` on every `Configuring decoder:` line, every device, every run — the
  sizing is derived at session start.

## R7 — a long capture keeps the tail, and still detects a ROM that refuses logcat (D-HU)

**PASS** on the three stated conditions, verified against the retained capture segments. The in-app
**Export Logs** action itself ANR'd (Setup notes; findings below), so it is reported as a deviation,
not a FAIL.

Settings: `log-level=0` (VERBOSE), `log-capture-enabled=true`, `view-mode=0`, `video-codec=Auto`,
`fps-limit=60`. Operator tapped "Allow one-time access" for the first ~8 s, then left it. 25-minute
Self Mode session, centre-swipe animation throughout.

- Session banner: `LogExporter: session | build=3.3.0-beta2 (101) github/debug | device=UNISOC
  MT50_YT610E4GFPSL_U board=uis7861_6h10 api=34 | video=codec:Auto fps:60 resId:3 view:SURFACE
  forceSw:false swDecoder:BUNDLED_FFMPEG | wifi=mode:NATIVE strategy:NEARBY_DEVICES | **logLevel=VERBOSE
  | debug=none**` (13:41:56.842).
- Session held the full 25 min: **303** `Throughput over` windows, 13:42:08 → 14:07:19, every one with
  `decodeLatency=` (0 unreadable), 0 with `rendered=0`, `dropped` sum 0, `decodeLatency` median 20 ms
  (matches R1/R2 D-HU). `optionalKeys=none` (low-latency not set for R7).
- **Segment roll (condition 2 + 3):** the capture rolled through **≥3** segments and kept the newest 2.
  On disk at session end:
  - `HUR_Log_20260827_140026_066.txt` — **8,388,827 bytes** (= `SEGMENT_BYTES`, 8 MiB), a complete
    middle segment: first line `--- continued from the previous log file ---`, last line
    `--- continues in the next log file ---`. First roll ≈ 14:00:26 (18 min of VERBOSE to fill 8 MB).
  - `HUR_Log_20260827_140721_390.txt` — the newest segment, first line
    `--- continued from the previous log file ---`, last timestamp **14:08:03** (session ended
    14:07:24 → **tail is within 2 min**, condition 1). Second roll ≈ 14:07:21.
  - the earlier segment (13:42 → 14:00) was deleted when the third was created — the two-newest
    retention, so the bound is 16 MB of disk, not 25 min of drive.
  - Joined (what the export would emit): `cat seg2 seg3` = **8.5 MB**, contains
    `--- continued from the previous log file ---` — in the 8–16 MB band.
- **`LogExporter: Logcat capture produced 0 bytes` — 0** occurrences during the run, in either segment
  or the parallel logcat capture. **No `APPLOG_FILE` / "Switching to Direct to file"** anywhere. A
  roll was **not** mistaken for a refused capture (`capturePreviousFile == null` guard held across ≥2
  rolls).
- **`log-source` still `0`** in `settings.xml` after the run.
- `Log capture process exited` — 0 (the pipe was never system-killed during the 25 min).

Neither brief FAIL condition occurred: the capture did **not** stop at ~16 MB / minute 10, and the
auto-switch did **not** fire.

## R8 — the narrow-band advice must not fire on a loopback session

**PASS** all three. `This unit has no 5 GHz band` — **absent** from every R1 capture (D-HU, D-POCO,
D-MOTO). Self Mode is a `127.0.0.1` socket (`isWirelessSession` true, no radio) and the advice
correctly does not fire.

## Report-back answers

1. **R1 vs R2 `decodeLatency` (median / p95-field-median), per device:**
   D-HU 20/26 → 20/26 ms · D-POCO 16/19 → 16/19 ms · D-MOTO 18/22 → 18/21 ms.
   **The low-latency keys move nothing on any of the three.** Keep them (they cost nothing — R4),
   do not default them on (they buy nothing here).
2. **Component + tier under R2:** D-HU `c2.unisoc.avc.decoder` → `realtime` · D-POCO
   `c2.qti.avc.decoder` → `vendor` · D-MOTO `c2.qti.avc.decoder` → `vendor`. The ladder's name matching
   covers both real families on the rig (unisoc falls to the AOSP rung, qti gets its vendor key); no
   device it was not written against was available (no MediaTek).
3. **R4 verdict, all three:** PASS — 0 rejections on the `realtime` rung. `KEY_PRIORITY` /
   `KEY_OPERATING_RATE` stay in.
4. **Mean `rendered`/`presented` ratio, with the numbers that produced it:** D-HU 1.0001
   (fed 86.7, rendered 86.7, inputWait 45 ms, skipped 0.00) · D-POCO 1.0006 (243.9 / 243.8 / 112 ms /
   0.05) · D-MOTO 1.0021 (242.9 / 242.7 / 127 ms / 0.24). Queue ran one deep the whole time on all
   three, under animation and 155–506 kB/s of video. Load signal, not a broken instrument.
5. **R7's three conditions:** (1) tail within 2 min — **yes** (last line 14:08:03, end 14:07:24);
   (2) 8–16 MB with `--- continued from the previous log file ---` — **yes** (8.5 MB joined, marker
   present, one full 8 MB segment); (3) no `produced 0 bytes`, `log-source` still 0 — **yes / yes**.
   The in-app export button itself ANR'd — see below.

**`run-as`** worked on all three (`run-as $PKG cat shared_prefs/settings.xml` returned real content
each time). **Gearhead force-stops to get a session:** 0 on D-HU and D-MOTO; on D-POCO, 6 failed
attempts caused by force-stopping Gearhead at all (it kills the 17.4+ dev server) — see Setup notes.
**Consent dialog outside R7:** none (R1–R6 read config from `settings.xml` + `findBestCodec:`).

## Anything the brief did not ask about

- **The in-app "Export Logs" button ANRs the app when `logcat` is gated and no capture segment is
  available.** `SettingsFragment` `onClick "exportLogs"` → `LogExporter.saveLogToPublicFile` →
  (when `captureFile` is null/empty) the ring-buffer fallback runs
  `Runtime.getRuntime().exec("logcat -d …")` **plus `process.waitFor()` synchronously on the main
  thread** (`LogExporter.kt:314-324`). On a ROM that gates `logcat` behind the LogAccess consent
  dialog, an unattended `waitFor()` never returns → `Open Headunit isn't responding` /
  `Input dispatching timed out … SettingsActivity`. It also leaves a stray 0-byte `HUR_Log_*.txt`
  (the `logFile.delete()` at `:326` is never reached). Reproduced once, on D-HU, this round. The
  25-min *continuous* capture (the branch's actual new code) is fine — it runs the pipe on a daemon
  thread. It is the **export**'s ring-buffer path that blocks the UI thread. Contributing on our
  side: the session had been left to bounce ~3 min via repeated `headunit://exit` before the export
  was triggered, which is likely what cleared `captureFile`/`capturePreviousFile` and forced the
  ring-buffer path; a prompt export right after the session ends would have taken the (non-blocking)
  segment-join path. Worth moving the `logcat -d` off the main thread regardless.
- **D-POCO's 17.4+ Self Mode has no way back once Gearhead's dev head-unit-server is stopped without
  a UI toggle.** `DeveloperHeadUnitNetworkService` on `:5277` does not restart on Gearhead relaunch
  (no launcher activity), on an idle wait, or via `am start-foreground-service` (not exported). Only
  the AA Developer-settings "Start head unit server" toggle brings it back. A user who force-stops
  Android Auto to fix a wireless problem could find Self Mode silently unable to connect until they
  re-toggle it. Legacy-path devices (D-HU, D-MOTO) recover on their own.
- **`auto-start-self-mode=true` + an explicit `ACTION_START_SELF_MODE` double-launches**, and on the
  17.4+ path the second launch's loopback socket trips
  `AapService.quiesceWirelessForWiredSession | AapService: USB session established while wireless
  mode AUTO/WIFI_DIRECT was armed` — a `127.0.0.1` Self Mode socket being classified as a USB/wired
  session. It collapsed the run to `nothing connected within 10000ms`. The legacy devices absorb the
  double launch. Possibly worth a guard so the auto-start and a manual trigger coalesce.
- **`decodeLatency` first-window outliers.** D-MOTO R1 window 1 `p95=247 ms` (median of the rest 22);
  D-POCO R2 window 1 `p95=99 ms`. First-GOP warm-up, settles by window 2 every time. Not a fault,
  but a reader eyeballing the raw `p95=` column will see it.
- **D-HU `inputWait` runs 45 ms vs 112–127 ms on the two phones**, at a *lower* frame rate (20 fps
  vs 48–52). The unisoc path feeds the codec with less waiting despite the slower SoC — the phones'
  `c2.qti` path spends more time blocked in `dequeueInputBuffer`. Consistent across R1 and R2.
