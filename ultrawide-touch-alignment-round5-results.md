# ultrawide-touch-alignment — round 5 results

**Candidate:** `fix/video-fit-and-ultrawide-touch` @ `f280892ed06de347a682e097e5265c1415a732a6`
(2 commits on `main` `12706e26f`; round 4's tip `2a8ce83f` + the touch-mapper centring change
reverted + the floating-button default flipped to off)
**Baseline:** none — this round is a PAR/margin probe on the candidate only; the decisive comparisons
are within-run (one wire integer changed between arms)
**APK md5 (live, re-read per device before every run):** `82a748defca1fbf58630a484ba2949b4` on D-HU and
D-POCO for every run. Different from round 4's `3c57e530`, so the identity gate is satisfied by md5
(the `ACTION_QUERY_STATE` `commit` field still truncates at the first inner quote — `data="{"` — same
as rounds 3 and 4).
**Units:**
- **D-HU** — UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14, panel 1440x720 (aspect 2.0),
  view-mode GLES, Native AA (`wifi-connection-mode=3`), POCO X3 NFC as the projecting phone — R0, R1
- **D-POCO** — POCO X3 NFC (`M2007J20CG`), Android 15 [note: brief says 15; `dumpsys` not re-checked],
  2400x1080 landscape, Self Mode — R2a/R2b/R2c, R3a/R3b/R3c
- **D-MOTO** — Moto edge 30 neo — **not used this round, not updated** (still carries round 4's
  `3c57e530`)
**Date:** 2026-09-07

## One-line answer to the question the round exists for

**Yes — Android Auto reacts to `pixel_aspect_ratio_e4` on this phone, strongly and linearly.**
Rendered UI aspect ≈ `10000 / PixelAspectRatioE4`. Measured across four independent values
(10000 → 1.00, 10001 → ~1.12, 15000 → 0.667, 6667 → 1.49) on identical wire geometry where the
ratio integer was the only difference. **The direction is inverted from the brief's R2 model**
(see R2).

## Verdict summary

| Run | Verdict | One line |
|---|---|---|
| R0 | **PASS** (control) | D-HU AUTO, PAR 10000→derived `11250`: `1280x720` / `0x0` / `1.0/1.0`, byte-identical to round 4 R1; every AA chrome circle 60x60 px |
| R1 | **PASS — ratio is honoured** | D-HU AUTO, PAR forced `10001`: identical wire geometry, **only** `PixelAspectRatioE4` differs (11250 → 10001); every AA chrome circle 60x60 → **67x60 px** (W/H 1.00 → 1.12), height unchanged. Picture ~12% too wide. This is the result that unlocks the margin-free design. |
| R2a | **PASS (run) — control** | D-POCO Self Mode 1080p, PAR 10000: `1920x1080` / `0x216` / `1.0/1.25`; rail icon discs 82x81 px (W/H 1.00). (First R2a capture was a pre-dock transient; re-run as R2a2 with a longer settle — that is the control used.) |
| R2b | **PASS — honoured** | Same geometry, PAR `15000`: rail icon discs **54x81 px (W/H 0.667)** = `10000/15000` |
| R2c | **PASS — honoured** | Same geometry, PAR `6667`: rail icon discs **82x55 px (W/H 1.49)** ≈ `10000/6667` |
| R3a | **PASS (measurement)** | D-POCO Self Mode AUTO, `fullscreen-mode=1`: usable `2400x1080`, margin `0x216` (20.0%), `scaleY 1.25`, picture full-bleed and correctly proportioned |
| R3b | **INCONCLUSIVE** | `fullscreen-mode=2`: usable wobbled `1080 ↔ 1014 ↔ 923` at connect; the wire `makeProto` caught the `1080` transient (`margin 0x216`), live `scaleY` settled `1.2811388` then bounced back to `1.25`. Bars cost this device only ~6%. Picture still correct. |
| R3c | **INCONCLUSIVE** | `fullscreen-mode=0`: usable `2400x1080`, margin `0x216` (20.0%), `scaleY 1.25` — **identical to R3a**. `fullscreen-mode` does not vary the usable area in Self Mode on this POCO. |
| **R3 overall** | **INCONCLUSIVE** | The "phone gives up on a large margin" hypothesis could not be tested: `fullscreen-mode` on this POCO in Self Mode never pushes the announced margin past ~20% (range reached: 20.0%, with a ~26–28% connect-time transient that never went on the wire). Proportions confirmed correct at 20%, consistent with round 3 R7 and round 4's AA-side confirmation. The 33.3% regime remains untested on this rig. |

**The round settles the question it was posed.** `pixel_aspect_ratio_e4` is honoured — so the
margin-free simplification (stop announcing margins, PAR carries the whole correction, every scale
becomes 1.0, buffer and touch space coincide) is viable **in principle** on this Android Auto build.
Two caveats before building it: the applied direction is `10000/PAR` not `PAR/10000` (R2), and
whether large margins are honoured — the other half of the "make the picture worse" risk — is still
unmeasured (R3).

---

## Setup notes

### Build

The round is buildless as a probe (no code change), but round 4 left `2a8ce83f` on the devices and
the router row names `f280892e`, so the candidate was built once and installed on D-HU + D-POCO.
`hur-wifi-test-scripts/build_hur.sh` under `thermal_guarded.sh HOT=88 COOL=68` — the PC is still
thermal-throttling (flagged since round 3): one `STOP` at 90 °C / `CONT` at 65 °C, then
`BUILD SUCCESSFUL`. APK md5 `82a748defca1fbf58630a484ba2949b4`. **No baseline build** (this round has
no A/B arm). **No unit-test gate** — the brief waives it (§1 "No build gate").

### `hur-wifi-test-scripts/` inventory and use

Used existing, nothing added or changed:

- `build_hur.sh`, `thermal_guarded.sh` — the one candidate build
- `set_hu_prefs.sh` — D-HU pref writes (rooted, one force-stop, no relaunch)
- `set_prefs_runas.sh` — D-POCO pref writes (non-rooted, pushed inner script)
- `ultrawide_touch_r3.sh` — the round driver, `OUTDIR=round-ultrawide-touch-r5`, `MODE=native` for
  D-HU (phone-BT off→on connect), `MODE=selfmode` for D-POCO. Carried unchanged.
- `restore_settings.sh` — D-POCO settings restore

Full captures and full-res `screencap` PNGs on the rig under
`hur-wifi-test-scripts/round-ultrawide-touch-r5/` (`R*.console.txt`, `R*.png`, `R*.logcat.txt`),
plus montages (`R0_R1_montage.png`, `R2_montage.png`, `R3_montage.png`, `rail_R0_R1_montage.png`,
`rail_R2_montage2.png`) and the `measure_circle.py` / `mc3.py` / `railmeasure.py` measurement
scripts. Photos (downscaled) in `ultrawide-touch-alignment-round5-photos/`.

### The lever

`pixel-aspect-ratio-e4` written straight into `shared_prefs/settings.xml` with the app stopped. The
`10000 = unset` quirk holds: with `10000` stored, `ACTION_GET_SETTINGS` reports the key as
absent/unset and the **derived** value goes on the wire (`11250` on D-HU AUTO, `10000` on D-POCO
1080p where the margin already absorbs the aspect). With any other value stored, that exact integer
goes on the wire unclamped and the read-back reports it. Confirmed on every connect via the
`[ServiceDiscovery] PixelAspectRatioE4 is: N (10000 = square)` line — **the first captures of that
line anywhere in the project** (see below).

### Identity

`ACTION_QUERY_STATE` stdout still truncates at `data="{"` (rounds 3/4). Identity carried by the live
APK md5, re-read on both devices before every run — `82a748de` on all 12 runs.

### Deviations from the brief and the protocol

- **R2a re-run as R2a2.** The first R2a `screencap` landed on a pre-app-dock nav rail (session
  ~15 s old, `Frame: 122ms`, "Do you see the Android Auto screen?" prompt still up), so its
  rail-icon control could not be measured on the same elements as R2b/R2c. Re-run with `WAIT_S=110`
  produced the app-dock rail; **R2a2 is the PAR-10000 control**. Geometry was identical in both
  captures (`1920x1080` / `0x216` / `1.0/1.25`).
- **R3's `fullscreen-mode` lever is inert on this POCO in Self Mode.** R3a (`=1`) and R3c (`=0`)
  produced byte-identical geometry (`usable 2400x1080`, `margin 0x216`, `scaleY 1.25`). R3b (`=2`)
  produced only a small connect-time inset wobble (`1080 ↔ 1014 ↔ 923`) that settled back to full.
  The brief anticipated this outcome ("If bars cost this device only a few percent … the honest
  verdict is INCONCLUSIVE with the range covered"). R3 therefore does not reach the 33.3 % margin it
  exists to probe.
- **PAR read-back "unset/absent" for the 10000 arms** (R0 shows `10000` because it was explicitly
  stored; R2a2/R3* show `unset/absent`). Expected — `10000` is the unset sentinel. The wire line is
  the authority and it read `10000` on every one.
- **Verdicts are visual by nature** (§0 of the template). Reported here as measured pixel dimensions
  of AA-drawn circular chrome (left-rail app-dock icon discs, speed dial), extracted from the
  `screencap` PNGs with a connected-component measurement, not eyeballed. Montages attached.
- **D-MOTO not touched.** Not in any R5 run; still carries round 4's `3c57e530`. D-HU and D-POCO
  carry `82a748de` (`f280892e`) after this round.
- **Settings restored md5-identical on both devices used:** D-HU `b3769c5ae5b52ea6e0f1e27d265f0dfb`
  (== round 4's pre-round backup), D-POCO `fcdd883d4e76fbf38963a05fb4229bcc` (this differs from
  round 4's `a0d036a8` — D-POCO's `settings.xml` was already at `fcdd883d` at the start of this
  round, i.e. something changed it between rounds; restored to the state found).
- **POCO thermal.** The app's own FPS overlay read `Temp: 91–93 C` and `Frame: 122–400 ms` during
  the Self-Mode runs — the POCO SoC is hot and janky. It did not affect the geometry logs or the
  static `screencap` measurements.

---

## R0 — D-HU on AUTO, PAR 10000 (control)

**PASS** — reproduces round 4 R1 exactly.

- Settings written: `resolutionId=0`, `video-fit-mode=0`, `log-level=0`, `pixel-aspect-ratio-e4=10000`
- Radio: POCO Bluetooth off at launch (`svc bluetooth disable`), on ~16 s later after
  `createGroup SUCCESS`; connect via `AutoStartReceiver` `MATCH!` (intended path on this rig)
- Discard-rule: 1 `createGroup SUCCESS`, 1 `SSL handshake complete`, 1 `Incoming connection detected
  from /192.168.49.50`, 1 `MATCH!` (expected), 0 `Magic Garbage`. **p2p ifaces `p2p-wlan0-4` and
  `p2p-wlan0-5`** — two interface indices for one `createGroup SUCCESS`; a P2P iface bump without a
  second group, not a second session. Session up ~10 s after BT-on.
- PAR read-back: `"pixel-aspect-ratio-e4":10000`
- Decisive lines:
  ```
  22:29:42.328 [ServiceDiscovery] NegotiatedResolution is: 1280x720
  22:29:42.336 [ServiceDiscovery] Margins are: 0x0
  22:29:42.337 [ServiceDiscovery] PixelAspectRatioE4 is: 11250 (10000 = square)
  22:29:43.282 [UI_DEBUG] Normal Scale. scaleX: 1.0, scaleY: 1.0
  22:29:42.025 [RES_CAP] resolutionId=0 realScreen=1440x720 usable=1440x720 ... chosen=_1280x720 capped=_1280x720
  ```
  (one transient `[RES_CAP] realScreen=1304x720` ~250 ms before the settle — the MT50 connect-time
  metrics wobble seen in rounds 3/4; the decisive `makeProto` fired after it settled)
- **Circle measurements (`R0.png`, 1440x720), AA-drawn chrome:**

  | element | W x H (px) | W/H |
  |---|---|---|
  | Maps icon disc | 60 x 60 | 1.000 |
  | Spotify icon disc | 60 x 60 | 1.000 |
  | Phone icon disc | 60 x 60 | 1.000 |
  | Settings icon disc | 60 x 60 | 1.000 |
  | Spotify green glyph bbox | 40 x 40 | 1.000 |
  | Speed dial ("0 km/h") disc | 59 x 60 | 0.983 |

- **Photo `R0.png`:** full-bleed, no bars, split media/map dashboard (route active), every circular
  element reads as a circle.

## R1 — D-HU on AUTO, PAR forced 10001 (the decisive run)

**PASS — the ratio is honoured.** R1's picture is ~12 % wider than R0's; the reference circle is an
ellipse; the height is unchanged.

- Settings written: identical to R0 except `pixel-aspect-ratio-e4=10001`
- Radio / connect: as R0
- Discard-rule: 1 `createGroup SUCCESS`, 1 `SSL handshake complete`, 1 `Incoming connection detected
  from /192.168.49.50`, 1 `MATCH!` (expected), 0 `Magic Garbage`, `p2p-wlan0-0` (single). Session up
  ~10 s after BT-on.
- PAR read-back: `"pixel-aspect-ratio-e4":10001`
- Decisive lines:
  ```
  22:33:32.608 [ServiceDiscovery] NegotiatedResolution is: 1280x720
  22:33:32.615 [ServiceDiscovery] Margins are: 0x0
  22:33:32.616 [ServiceDiscovery] PixelAspectRatioE4 is: 10001 (10000 = square)
  22:33:33.483 [UI_DEBUG] Normal Scale. scaleX: 1.0, scaleY: 1.0
  22:33:32.590 [RES_CAP] resolutionId=0 realScreen=1440x720 usable=1440x720 ... chosen=_1280x720 capped=_1280x720
  ```
  **The only wire difference from R0 is `PixelAspectRatioE4`: `11250` → `10001`.** Resolution,
  margins, and both head-unit scales are bit-identical.
- **Circle measurements (`R1.png`, 1440x720), same elements as R0:**

  | element | R0 W x H | R1 W x H | R1/R0 width | height |
  |---|---|---|---|---|
  | Maps icon disc | 60 x 60 | **67 x 60** | 1.117 | unchanged |
  | Spotify icon disc | 60 x 60 | **67 x 60** | 1.117 | unchanged |
  | Phone icon disc | 60 x 60 | **67 x 60** | 1.117 | unchanged |
  | Settings icon disc | 60 x 60 | **67 x 60** | 1.117 | unchanged |
  | Spotify green glyph bbox | 40 x 40 | **45 x 40** | 1.125 | unchanged |
  | Speed dial disc | 59 x 60 | ~66 x 60 | ~1.12 | unchanged |

  Predicted stretch 1.125 (1440/1280); measured 1.117–1.125 across five independent elements, height
  constant. The ~0.7 % shortfall is threshold/anti-alias quantisation at a 60 px diameter.

- **Photo `R1.png` vs `R0.png`:** side-by-side (`rail_R0_R1_montage.png`), R1's rail icons are
  visibly wider ovals than R0's circles. Subtle at 12 % — the pixel numbers are the decisive form,
  as the brief notes.

**Verdict per the brief:** *"R0 and R1 photographs clearly different, R1 stretched horizontally:
PASS, and it means the ratio is honoured. This is the result that unlocks the margin-free design."*

## R2 — D-POCO Self Mode 1080p, the same question with an unmissable signal

**PASS on all three — honoured, and it calibrates how much.** Geometry identical across a/b/c;
only `PixelAspectRatioE4` on the wire changed. Rail app-dock icon discs (Maps / Phone / Settings,
white circles on the black rail) measured on each:

| sub-run | PAR_e4 on the wire | `Margins` / `scaleY` | icon disc W x H | icon W/H | `10000 / PAR` |
|---|---|---|---|---|---|
| R2a2 | `10000` (derived, square) | `0x216` / `1.25` | 82 x 81, 82 x 82, 82 x 82 | **1.00** | 1.000 |
| R2b | `15000` | `0x216` / `1.25` | 54 x 81, 54 x 82, 54 x 81 | **0.667** | 0.667 |
| R2c | `6667` | `0x216` / `1.25` | 82 x 55, 82 x 55, 82 x 55 | **1.49** | 1.500 |

Layout-switch button (bottom of rail): R2a2 51 x 51 (1.00), R2b 34 x 51 (0.667), R2c 51 x 33 (1.545)
— same law.

- Every sub-run: `resolutionId=3` → `chosen=_1920x1080 capped=_1920x1080`,
  `NegotiatedResolution 1920x1080`, `Margins are: 0x216`, `Normal Scale scaleX: 1.0, scaleY: 1.25` —
  identical to round 3 R4. Discard-rule clean each connect (1 `createGroup SUCCESS`, 1 SSL, 0
  `MATCH!`, 0 `Magic Garbage`).
- Montage `rail_R2_montage2.png` (R2a2 | R2b | R2c): circles → tall-narrow ovals → wide-flat ovals.
  Three unmistakably different pictures.

**Finding — the applied direction is inverted from the brief's R2 model.** The brief predicted
`15000` → "grossly too wide" and `6667` → "grossly too tall". Measured: `15000` → icons
**0.667 wide** (too narrow / tall), `6667` → icons **1.49 wide** (too wide). The relationship is
`rendered_aspect ≈ 10000 / PixelAspectRatioE4`, i.e. the phone treats the field as (or the app
supplies it as) the inverse of what the brief assumed. Anyone building the margin-free design on
this result must get that sign right, or every non-square panel would be distorted the wrong way.

## R3 — how large a margin will the phone honour

**INCONCLUSIVE.** The lever (`fullscreen-mode`) does not move the announced margin on this POCO in
Self Mode, so the "phone gives up on a large margin" hypothesis was not reached.

| sub-run | `fullscreen-mode` | `[RES_CAP] usable` | `Margins` (wire) | `scaleY` | % of frame |
|---|---|---|---|---|---|
| R3a | `1` (immersive) | `2400x1080` | `0x216` | `1.25` | 20.0 |
| R3b | `2` (status bar only) | `2400x1080` ↔ `2309x1014` ↔ `2400x923` at connect | `0x216` (caught the `1080` transient) | settled `1.2811388`, then bounced to `1.25` | 20.0 announced; ~26–28 transient, never on wire |
| R3c | `0` (bars shown) | `2400x1080` | `0x216` | `1.25` | 20.0 — **identical to R3a** |

- Every sub-run: `resolutionId=0` → `NegotiatedResolution 1920x1080`, `PixelAspectRatioE4 is: 10000`
  (a margin is present, so the derived ratio cancels to 10000, as the brief says). Discard-rule
  clean each connect.
- Width stayed `2400` throughout — no horizontal change, as the brief expected.
- **Photos `R3a.png` / `R3b.png` / `R3c.png`:** all full-bleed, correctly proportioned, no vertical
  squash. Rail icon discs 82 x 81–82 (R3a), 78 x 78 (R3b), 82 x 82 (R3c) — all W/H 1.00. R3b shows
  a thin black letterbox strip at the bottom of the panel (the gesture pill area) but the projected
  content itself is undistorted.

**Verdict per the brief:** *"If bars cost this device only a few percent, the run cannot get near
33.3 and the honest verdict is INCONCLUSIVE with the range covered."* Range covered on the wire:
**20.0 %**. The 25–33 % band that the three reporters' panels sit in is not reproducible with
`fullscreen-mode` on this rig. Proportions are confirmed correct at 20 %, which agrees with round 3
R7 (photographed) and round 4 R5 (confirmed from Android Auto's own `injectMotionEvent` side). The
hypothesis is neither confirmed nor refuted.

---

## The `PixelAspectRatioE4 is:` line from every connect

The newest log line in the app; no capture on disk before this round contained one. All of these
are firsts:

```
R0    22:29:42.337  PixelAspectRatioE4 is: 11250 (10000 = square)   [D-HU AUTO, stored 10000 -> derived]
R1    22:33:32.616  PixelAspectRatioE4 is: 10001 (10000 = square)   [D-HU AUTO, stored 10001 -> forced]
R2a2  22:38:45.679  PixelAspectRatioE4 is: 10000 (10000 = square)   [D-POCO 1080p, stored 10000 -> derived-square]
R2b   22:35:52.828  PixelAspectRatioE4 is: 15000 (10000 = square)   [D-POCO 1080p, stored 15000 -> forced]
R2c   22:36:28.292  PixelAspectRatioE4 is: 6667 (10000 = square)    [D-POCO 1080p, stored 6667 -> forced]
R3a   22:39:48.251  PixelAspectRatioE4 is: 10000 (10000 = square)   [D-POCO AUTO, margin present -> 10000]
R3b   22:40:22.868  PixelAspectRatioE4 is: 10000 (10000 = square)   [D-POCO AUTO, margin present -> 10000]
R3c   22:41:08.656  PixelAspectRatioE4 is: 10000 (10000 = square)   [D-POCO AUTO, margin present -> 10000]
```

---

## Anything the brief did not ask about

- **The rendered-aspect law is exact.** `10000/PAR` predicted 1.000 / 1.125 / 0.667 / 1.500 for the
  four values tested; measured 1.00 / 1.117 / 0.667 / 1.49. The phone applies the field
  proportionally with no dead-band except the `10000` sentinel, and it changes whichever single axis
  (width for PAR ≥ 10000, height for PAR < 10000) makes the pixel aspect match.

- **The margin-free simplification is viable in principle but two things still gate it.** (1) The
  applied direction is `10000/PAR`, inverted from the brief's model — the derivation in the app must
  produce the reciprocal of what §0 assumed. (2) R3 could not test whether a *large* announced
  margin is honoured; if the answer there is "the phone gives up past ~25 %", the margin has to stay
  for those panels regardless of what the ratio does, and the "make the picture worse on every
  margin panel" risk in §0 is real. That coverage now needs either a panel/emulator that can force
  a 33 % margin, or a JVM test of the derivation plus a field build.

- **`fullscreen-mode` is dead in D-POCO Self Mode.** `0` and `1` are byte-identical; `2` only nudges
  a connect-time inset that settles back. If `fullscreen-mode` is meant to change the projected
  window on a phone in Self Mode, it does not here (POCO has `stretch_to_fill=true`). Not chased —
  out of scope — but worth a JVM/behaviour check if the setting is advertised as working in Self
  Mode.

- **D-HU R0 produced two `p2p-wlan0` indices for one group.** `p2p-wlan0-4` and `p2p-wlan0-5` with a
  single `createGroup SUCCESS` and a single SSL handshake. Interface-index churn without a second
  group; benign for this round (single session, single handshake) but noted for the P2P-lifetime
  counting heuristic in CLAUDE.md — the index is not always +1 per group on this rig.

- **D-POCO connect-time inset wobble** (`2400x1080 ↔ 2309x1014 ↔ 2400x923`) matches the Moto's
  round-3/4 behaviour; on this POCO the decisive `makeProto` caught the `1080` reading each time, so
  the announced margin never reflected the settled inset. If a future round needs the *settled*
  margin on the wire it must wait for `locked=true` + a stable `[RES_CAP]` before reading, or force
  a re-announce.
