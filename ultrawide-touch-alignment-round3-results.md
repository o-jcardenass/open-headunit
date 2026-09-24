# ultrawide-touch-alignment — round 3 results

**Candidate:** `fix/video-fit-and-ultrawide-touch` @ `0b6497b41bd32af5631b1a1811af46b5f1a6efd3`
**Baseline:** `main` @ `6cd703995f68e04740ef5c2bff1bab05dc9e0c3a`
**APK md5:** candidate `9aa3b0d450dfe2258d8ec8a1351750c1` / baseline `081c9e831f49c4d40548151629cad5bf`
**Live `commit` fingerprint:** candidate `0b6497b41bd3` (`ACTION_QUERY_STATE` on the MT50), baseline
verified by md5 only (installed on the POCO for R4, live md5 `081c9e83…` matched the host build).
**Units:**
- UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14, panel 1440x720 (aspect 2.0), view-mode GLES,
  Native AA (`wifi-connection-mode=3`), POCO X3 NFC as the projecting phone — R0/R1/R2/R3
- POCO X3 NFC (`M2007J20CG`), Android 15, physical 2400x1080 landscape, Self Mode — R4/R5/R6/R7
- Moto edge 30 neo (`motorola_edge_30_neo`), Android 14, physical 2400x1080 landscape, Self Mode —
  R8/R9
**Date:** 2026-09-07

## Setup notes

### Build gate and the thermal warning

The PC thermal-throttles under sustained compile load right now (flagged in the brief). Measured
this session: idle package temp sat at 66-73 °C and would not fall below ~66 even after 10 min idle
(poor cooling day, not a spike). The **candidate assemble** was run first from 66 °C and peaked at
**86 °C**; its unit-test cycle stayed at 66 °C (mostly up-to-date). The **baseline assemble** peaked
at **95 °C** — past the 87 °C "high" mark, though `assembleGithubDebug` still returned
`BUILD SUCCESSFUL`. Before the baseline **unit-test** cycle a new guard script was added (below) and
the run completed under it at a capped 85 °C. No `BUILD FAILED`, no truncated gradle log, no `dmesg`
throttle entries. Both gates match the brief's counts exactly.

### `hur-wifi-test-scripts/` inventory and additions

Used existing: `build_hur.sh`, `run_unit_tests.sh` (both arms), `set_hu_prefs.sh` (MT50 multi-key
settings), `set_prefs_runas.sh` (POCO/Moto multi-key settings on the non-rooted phones).

Added two scripts, left in the folder for next time:

- **`thermal_guarded.sh`** — `HOT=<c> COOL=<c> thermal_guarded.sh <cmd …>`. Runs the command; when
  the CPU package hits `HOT` it `SIGSTOP`s the whole process tree until it falls to `COOL`, then
  `SIGCONT`s. Gradle tolerates STOP/CONT of its tree. Added for the throttle days; used for the
  baseline unit-test cycle.
- **`ultrawide_touch_r3.sh`** — `MODE=native|selfmode RUN=<id> …` one-connect driver: starts the
  capture, connects (Native AA via a phone-Bluetooth off→on cycle, or Self Mode via
  `ACTION_START_SELF_MODE`), reads `pixel-aspect-ratio-e4` back, drives an optional tap list,
  pulls a `screencap`, greps the decisive lines and runs the discard-rule check. `TAGFILTER` env
  passes an `adb logcat` filter (used `"OPENHU:V *:S"` on the Moto per the known ROM-spam quirk).

### Deviations and quirks hit

- **`ACTION_QUERY_STATE` reply is not cleanly greppable from `am broadcast` stdout.** The ordered
  broadcast prints `… data="{"action":"…","commit":"…",…}"` and any `data="[^"]*"` pattern stops at
  the first inner quote. Recorded the `commit` from a clean `tr ',' '\n'` split of one live query
  instead; the APK md5 is the load-bearing identity check for every run and it matched the host
  build every time.
- **Self Mode still logs `5GHz createGroup SUCCESS!`** on the POCO (not the Moto). One per run, not
  a discard-rule hit (the rule is a *second* group in a run).
- **The MT50 Native AA connect goes through one `MATCH! Starting AapService`** — that is the
  intended connect path on this rig (phone Bluetooth off at launch, on after the group forms;
  TESTING-TEMPLATE §7a). One per run. The stray `p2p-wlan0-10` in R1's capture is the *previous*
  session's group being torn down as the capture opened (`P2P-GROUP-REMOVED … reason=REQUESTED` at
  +1 s, then `p2p-wlan0-11` created once for the run). Every MT50 run: exactly one real
  `createGroup SUCCESS`, one `Handshake: SSL handshake complete`.
- **Moto Self Mode dropped its first R8 session** (`session state disconnected (link_lost)` ~0.8 s
  after `projecting`, no `Normal Scale` line ever emitted). Re-ran clean (`R8-R5b`) and it held.
  Kept both captures; the verdict is on the clean re-run. Intermittent Self-Mode loopback flakiness
  on this device, not a candidate defect (the drop is `link_lost` on the 127.0.0.1:5277 socket).
- **`resolutionId` landed where asked on every run** (`[RES_CAP] chosen=` matched: `_1280x720` for
  id 2, `_1920x1080` for id 3).
- **`pixel-aspect-ratio-e4` read-back was `10000` before every run** (stored value; the candidate
  treats 10000 as "unset" and still lets the derived value through — confirmed by R1 deriving
  11250 with the key present at 10000).
- Settings restored on all three devices; value-key diffs against the pre-round backups are empty
  apart from app-churned runtime keys (`cached-surface-*`, `station-stand-down-network-id`,
  `last-loc-*`, `connection-issue-hotspot-off`). Candidate APK left installed on all three (MT50,
  POCO, Moto) — an upgrade from the `371477b6` build rounds 1-2 left there.

### Photographs

`ultrawide-touch-alignment-round3-photos/` (downscaled to 1400 px wide, JPEG q82; full-res
`screencap` PNGs and full `adb logcat` captures kept on the rig under
`hur-wifi-test-scripts/round-ultrawide-touch-r3/`). Every photo is a `screencap` of the live
projection surface (GLES on all three units — `screencap` captures it), taken right after the run's
taps.

---

## R0 — build gate

**PASS**

- Candidate `testGithubDebugUnitTest`: **1453 / 0** (skipped 0, failures 0, errors 0)
- Baseline `testGithubDebugUnitTest`: **1414 / 0** (skipped 0, failures 0, errors 0)
- Both exactly the brief's expected counts. Candidate `assembleGithubDebug` and
  `testGithubDebugUnitTest` both `BUILD SUCCESSFUL`; baseline the same (assemble peaked 95 °C, see
  Setup notes).

## R1 — MT50 at 720p, candidate

**PASS**

- Settings written: `resolutionId=2`, `log-level=0`, `video-fit-mode` deleted,
  `pixel-aspect-ratio-e4` left at `10000`.
- Radio: POCO Bluetooth off at launch (`svc bluetooth disable`), on after `createGroup SUCCESS`
  (`svc bluetooth enable`); connect via `AutoStartReceiver` `MATCH!`.
- Discard-rule check: clean — 1 `createGroup SUCCESS`, 1 `Handshake: SSL handshake complete`, 1
  `MATCH!`, 0 `Magic Garbage`. (`p2p-wlan0-10`/`-11`: `-10` is the prior group torn down as the
  capture opened, `-11` is this run's single group.)
- Decisive log lines:
  ```
  13:55:33.152 [ServiceDiscovery] NegotiatedResolution is: 1280x720
  13:55:33.158 [ServiceDiscovery] Margins are: 0x0
  13:55:33.160 [ServiceDiscovery] PixelAspectRatioE4 is: 11250 (10000 = square)
  13:55:34.133 [UI_DEBUG] Normal Scale. scaleX: 1.0, scaleY: 1.0
  13:55:32.839 [UI_DEBUG] CarScreen isSmallScreen: true, scaleFactor: 1.0, portraitScaled: false, margins: w=0, h=0
  ```
- Taps (read back from `[UI_DEBUG] Touch map:`, all `margin=0x0 fit=FILL`):

  | tap (raw) | video (measured) | video (brief) |
  |---|---|---|
  | 40,40 | 36,40 | 36,40 |
  | 1400,40 | 1244,40 | 1244,40 |
  | 40,680 | 36,680 | 36,680 |
  | 1400,680 | 1244,680 | 1244,680 |
  | 720,360 | 640,360 | 640,360 |

- Photo `R3.jpg` … (see R1 photo) full-bleed edge-to-edge, no bars, split media/map UI undistorted.
  Every number and every tap identical to round 1's R1/R3. Nothing moved.

## R2 — MT50 at 1080p, candidate

**PASS**

- Settings written: `resolutionId=3`, `log-level=0`, `video-fit-mode` deleted.
- Radio/connect: as R1. Discard-rule: clean (1 group `p2p-wlan0-12`, 1 SSL, 1 `MATCH!`, 0 garbage).
- Decisive log lines:
  ```
  13:57:01.347 [ServiceDiscovery] NegotiatedResolution is: 1920x1080
  13:57:01.353 [ServiceDiscovery] Margins are: 480x360
  13:57:01.354 [ServiceDiscovery] PixelAspectRatioE4 is: 10000 (10000 = square)
  13:57:02.257 [UI_DEBUG] Normal Scale. scaleX: 1.3333334, scaleY: 1.5
  ```
- `scaleX 1.3333334 / scaleY 1.5` and `Margins 480x360` — **bit-identical to round 1's R4**.
  `PAR 10000`. Photo `R2.jpg`: full picture with the announced top/bottom letterbox, undistorted.
- Transient noted: the first `[RES_CAP]` of the run reads `realScreen=1304x720` (margins w=616)
  before settling to `1440x720` (w=480) ~230 ms later; the decisive `makeProto` line fired after
  the settle. Same class of device-metrics wobble the phones show, milder.

## R3 — MT50 at 720p in CONTAIN, candidate

**PASS**

- Settings written: `resolutionId=2`, `video-fit-mode=1`, `log-level=0`.
- Radio/connect: as R1. Discard-rule: clean (1 group `p2p-wlan0-13`, 1 SSL, 1 `MATCH!`, 0 garbage).
- Decisive log lines:
  ```
  13:58:00.723 [ServiceDiscovery] NegotiatedResolution is: 1280x720
  13:58:00.730 [ServiceDiscovery] Margins are: 0x0
  13:58:00.731 [ServiceDiscovery] PixelAspectRatioE4 is: 10000 (10000 = square)
  13:58:01.646 [UI_DEBUG] Normal Scale. scaleX: 0.8888889, scaleY: 1.0
  ```
- **`PixelAspectRatioE4 is: 10000`** — the one MT50 number this round changes. Round 1 would have
  announced `11250` here; the candidate announces square pixels because a non-FILL fit no longer
  claims a pre-stretch. `scaleX 0.8888889 / scaleY 1.0` unchanged from round 1's R5.
- Taps (all `margin=0x0 fit=CONTAIN`), identical to round 1's R5:

  | tap (raw) | video (measured) |
  |---|---|
  | 40,40 | 0,40 |
  | 1400,40 | 1280,40 |
  | 40,680 | 0,680 |
  | 1400,680 | 1280,680 |
  | 720,360 | 640,360 |

- Photo `R3.jpg`: clean pillarbox (picture spans roughly the centre ~1184 px of the 1440-wide
  panel, black bar each side), **no horizontal squeeze** — circular UI elements (compass button,
  the km/h dial, roundabouts on the map) read as circles, map labels normally proportioned. The
  12 % squeeze the brief warned an unaided eye would miss is not present; announcing square pixels
  removed it.

## R4 — POCO on the baseline at 1080p

**Measurement (not PASS/FAIL). Reference for R5/R6.**

- Baseline APK (live md5 `081c9e83…`). Settings: `resolutionId=3`, `log-level=0`,
  `allow-external-configuration=true`, `video-fit-mode` absent. Self Mode.
- Discard-rule: clean (1 `createGroup SUCCESS`, 1 SSL, 0 `MATCH!`, 0 garbage).
- Decisive log lines:
  ```
  14:02:12.409 [ServiceDiscovery] NegotiatedResolution is: 1920x1080
  14:02:12.413 [ServiceDiscovery] Margins are: 0x216
  14:02:15.469 [UI_DEBUG] Normal Scale. scaleX: 1.0, scaleY: 1.25
  14:02:12.195 [UI_DEBUG] CarScreen isSmallScreen: false, scaleFactor: 1.25, margins: w=0, h=216
  ```
  (`isSmallScreen` line has **no** `portraitScaled:` field — the baseline tell; the candidate adds
  that field.) No `PixelAspectRatioE4 is:` line — baseline does not emit it.
- `Margins 0x216`, `scaleY 1.25` — exactly the brief's expectation. Metrics settled cleanly at
  `realScreen=2400x1080` (no wobble this connect).
- Photo `R4.jpg`: full-bleed edge-to-edge, no bars. The `0x216` margin makes the visible canvas
  1920x864, whose aspect is exactly 2400/1080, so `scaleY 1.25` is a uniform upscale, not a
  stretch — the picture is undistorted (car icon, km/h dial, text all proportioned). This is the
  reference the candidate's R5/R6 must match.

## R5 — POCO on the candidate at 720p, FILL

**PASS**

- Candidate APK (live md5 `9aa3b0d4…`). Settings: `resolutionId=2`, `log-level=0`,
  `allow-external-configuration=true`, `video-fit-mode` absent. Self Mode.
- Discard-rule: clean (1 `createGroup SUCCESS`, 1 SSL, 0 `MATCH!`, 0 garbage).
- Decisive log lines:
  ```
  14:03:15.770 [ServiceDiscovery] NegotiatedResolution is: 1280x720
  14:03:15.773 [ServiceDiscovery] Margins are: 0x144
  14:03:15.774 [ServiceDiscovery] PixelAspectRatioE4 is: 10000 (10000 = square)
  14:03:18.797 [UI_DEBUG] Normal Scale. scaleX: 1.0, scaleY: 1.25
  14:03:15.558 [UI_DEBUG] CarScreen isSmallScreen: false, scaleFactor: 1.875, portraitScaled: false, margins: w=0, h=144
  ```
- **`scaleY: 1.25`** — the whole point of the run. Round 2 measured `1.0` here and passed it; the
  brief says PASS needs `1.25`, and that is what the candidate now produces. `Margins 0x144`,
  `scaleX 1.0`, `PAR 10000` all as the brief asked. `portraitScaled` field present (candidate tell).
- Taps (all `margin=0x144 fit=FILL`), identical to round 2's R3:

  | tap (raw) | video (measured) |
  |---|---|
  | 40,40 | 21,21 |
  | 2360,40 | 1259,21 |
  | 40,1040 | 21,555 |
  | 2360,1040 | 1259,555 |
  | 1200,540 | 640,288 |

- Photo `R5.jpg`, next to `R4.jpg`: **same proportions** — both full-bleed edge-to-edge,
  undistorted. No vertical squash (the km/h dial and compass buttons are circular; round 2's
  `scaleY 1.0` build squashed this ~1/5 vertically). The picture agrees with the numbers.

## R6 — POCO on the candidate at 1080p, FILL

**PASS**

- Candidate APK. Settings: `resolutionId=3` (720p→1080p carried the rest from R5). Self Mode.
- Discard-rule: clean (1 `createGroup SUCCESS`, 1 SSL, 0 `MATCH!`, 0 garbage).
- Decisive log lines:
  ```
  14:04:05.252 [ServiceDiscovery] NegotiatedResolution is: 1920x1080
  14:04:05.259 [ServiceDiscovery] Margins are: 0x216
  14:04:05.259 [ServiceDiscovery] PixelAspectRatioE4 is: 10000 (10000 = square)
  14:04:08.313 [UI_DEBUG] Normal Scale. scaleX: 1.0, scaleY: 1.25
  14:04:05.093 [UI_DEBUG] CarScreen isSmallScreen: false, scaleFactor: 1.25, portraitScaled: false, margins: w=0, h=216
  ```
- `Margins 0x216`, `scaleX 1.0`, `scaleY 1.25`, `PAR 10000` — all as the brief asked, and the
  scale line is **identical to R4's baseline** (1080p on this panel was never broken; the fix
  changed only 720p). Photo `R6.jpg` matches `R4.jpg`: full-bleed, undistorted.

## R7 — POCO on the candidate, CONTAIN then COVER at 720p

**PASS** (both sub-runs; one connect each)

Settings: `resolutionId=2`; `video-fit-mode=1` then `=2`. Candidate APK, Self Mode. Each sub-run:
1 `createGroup SUCCESS`, 1 SSL, 0 `MATCH!`, 0 garbage.

### R7 CONTAIN
```
14:04:43.253 [ServiceDiscovery] NegotiatedResolution is: 1280x720
14:04:43.256 [ServiceDiscovery] Margins are: 0x144
14:04:43.257 [ServiceDiscovery] PixelAspectRatioE4 is: 10000 (10000 = square)
14:04:46.360 [UI_DEBUG] Normal Scale. scaleX: 1.0, scaleY: 1.25
```
Taps (all `margin=0x144 fit=CONTAIN`): `21,21` / `1259,21` / `21,555` / `1259,555` / `640,288` —
identical to R5 (FILL).

### R7 COVER
```
14:05:28.170 [ServiceDiscovery] PixelAspectRatioE4 is: 10000 (10000 = square)
14:05:31.217 [UI_DEBUG] Normal Scale. scaleX: 1.0, scaleY: 1.25
```
(`Margins are: 0x144`, `NegotiatedResolution 1280x720` also present.) Taps (all
`margin=0x144 fit=COVER`): `21,21` / `1259,21` / `21,555` / `1259,555` / `640,288` — identical to
R5 and R7-CONTAIN.

**Verdict reasoning:** the brief's warning — "identical taps *with a bar still on screen* is the
original defect and a FAIL". Photos `R7-CONTAIN.jpg` and `R7-COVER.jpg`, compared to `R5.jpg`
(FILL): **no head-unit letterbox and no crop** in either — all three are full-bleed edge-to-edge,
the same dark left strip in all of them is Android Auto's own control rail (present in the FILL
shot too), not a pillarbox. Every route-card / Start-button / Google-logo / km/h-dail element is
fully on screen in COVER (nothing cropped). Identical taps + screen agrees = PASS. This is the
inversion of round 2's Finding 1: the taps matching FILL is now correct because the `0x144` margin
has already shaped the canvas to the panel, leaving nothing to letterbox.

## R8 — Moto, abbreviated (R5 + R7, one tap each)

**PASS**

Candidate APK (live md5 `9aa3b0d4…`), Self Mode, `TAGFILTER="OPENHU:V *:S"`. First R5 attempt
dropped (`link_lost`, no scale line) — re-ran clean as `R8-R5b`; verdict on the clean runs.

| sub-run | `Margins are:` | `PixelAspectRatioE4` | `Normal Scale` | tap `1200,540 →` |
|---|---|---|---|---|
| R5 (FILL) | `0x144` | `10000` | `scaleX 1.0, scaleY 1.25` | `640,288` `fit=FILL` |
| R7 CONTAIN | `0x102` announced | `10000` | `scaleX 1.0, scaleY 1.25` | `640,288` `fit=CONTAIN` |
| R7 COVER | `0x102` announced | `10000` | `scaleX 1.0, scaleY 1.25` | `640,288` `fit=COVER` |

All three: `scaleX 1.0 / scaleY 1.25`, `PAR 10000`, the confirmation tap identical to the POCO's
R5 table (`1200,540 → 640,288`, mapped with the settled `margin=0x144`). Photos `R8-R5b.jpg`,
`R8-R7-CONTAIN.jpg`, `R8-R7-COVER.jpg`: full-bleed, no bar, no crop — matches the POCO. The
`0x102`-vs-`0x144` split on the CONTAIN/COVER sub-runs is the connect-time inset wobble handled in
R9, not a fit-mode effect. Cross-device behaviour is identical to the POCO.

## R9 — the margin that moves after it is announced

**PASS** (reproduced; the fix reaches `sendUpdateUiConfigRequest` on every drift)

Candidate, Moto, Self Mode. Deliberate provocation attempted (`settings put global policy_control
immersive.full='*'` mid-session to drop the system bars) — **inert**: the projection surface on
this phone is already effectively fullscreen and no drift followed. But the Moto's own
window-inset settling produced the exact signal on connect, reliably (also seen spontaneously in
R8's first attempt):

```
14:12:18.276 [ServiceDiscovery] Margins are: 0x102                        (announced at connect)
14:12:18.807 [UI_DEBUG] CarScreen: margins drifted from the announced 0x102 to 0x154
14:12:18.811 [UI_DEBUG_FIX] AA is already running, send corrected via sendUpdateUiConfigRequest
14:12:18.842 [UI_DEBUG] CarScreen: margins drifted from the announced 0x154 to 0x144
14:12:18.843 [UI_DEBUG_FIX] AA is already running, send corrected via sendUpdateUiConfigRequest
14:12:18.844 [UI_DEBUG_FIX] Recalculated: usable=2400x1080, margins: w=0, h=144 …
14:12:18.845 [UI_DEBUG_FIX] AA is already running, send corrected via sendUpdateUiConfigRequest
```

- Connect-time margin (`0x102`, from a transient `2237x1080` reading) **differs** from the settled
  margin (`0x144`). ✓
- **Every `margins drifted` line is followed by a `sendUpdateUiConfigRequest`.** The FAIL shape
  (a drift with no re-announce while a session is live) did not occur. This is round 2's Finding 2,
  now fixed and reaching the re-announce path.
- Also visible: `updateSurfaceDimensions` catching `Usable: 2237x1080 → Actual surface 2400x1080`
  and `Usable: 2300x1017 → 2400x1080` and re-announcing on each — the same fix, second trigger.

---

## Anything the brief did not ask about

- **The connect-time settling wobble is on all three units, not just the phones.** The MT50's R2
  showed one `[RES_CAP]` at `realScreen=1304x720` (margins w=616) before the `1440x720` (w=480)
  settle, ~230 ms apart. It is milder there (the decisive `makeProto` always fired after the
  settle in R1/R2/R3), but the same mechanism the phones show more dramatically. If the
  re-announce fix (R9) were ever to regress, the MT50 would be a weaker but real repro.
- **The Moto Self-Mode `link_lost` drop** (R8 first attempt) is worth a note for future phone
  rounds on this device: the 127.0.0.1:5277 loopback session can drop within ~1 s of `projecting`
  with `Not restarting` (Self Mode by design does not auto-reconnect), leaving the projection
  activity showing its "reconnecting overlay" while still processing (meaningless) touch events
  against stale geometry. A round that reads a `Normal Scale` line as the pass condition must
  confirm the session actually stayed up, not just that a tap logged.
- **`portraitScaled` as an arm tell works cleanly.** Baseline: `CarScreen isSmallScreen: …,
  scaleFactor: …, margins: …`. Candidate: `… scaleFactor: …, portraitScaled: false, margins: …`.
  Every candidate run showed the field, R4's baseline run did not — a one-line arm check that does
  not need the md5.
- **1080p on a 2400x1080 panel is bit-identical between the arms** (`scaleX 1.0 / scaleY 1.25`,
  R4 baseline vs R6 candidate). The fix is confined to the 720p / non-FILL cases; the 1080p path
  was already correct and is unchanged. Good news for regression risk.
