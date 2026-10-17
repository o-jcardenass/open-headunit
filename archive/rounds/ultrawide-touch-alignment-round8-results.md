# ultrawide-touch-alignment — round 8 results

**Candidate:** `fork/testing/recovery-plus-video-plus-usb` @ `694af3e1a` (**not** the brief's
`fix/video-fit-and-ultrawide-touch` @ `89093db4` — see Setup notes; the fix branch is merged into
this testing branch at `2e35411ad`, so `94efd6df` and `89093db4` are both in the tree)
**Baseline (R1 arm B only):** `409056f4` (round 7's candidate)
**R6 arm:** `c70ed1b40` = `694af3e1a` with `89093db4` reverted
**APK md5:** candidate `8da1475bc9c449ce2d72fcf5f61dc505` / baseline `e9581d3ac9638096af87f1ab2392ba86` /
R6 arm `9cadf6f90c519f669120f89fd2f75ac8`
**Units:** D-HU UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14, 1440x720, 240 dpi, OEM bar right 136 px,
Native AA, view-mode GLES · D-POCO POCO X3 NFC (projecting phone) · D-MOTO Moto edge 30 neo (R5/R6)
**Date:** 2026-09-10

---

## Setup notes

**Deviation 1, and it changes the identity of every arm: the candidate was built from the testing
branch, not the fix branch.** The round was run on instruction to build
`fork/testing/recovery-plus-video-plus-usb`, which merges `fix/video-fit-and-ultrawide-touch` at
`89093db4` (merge `2e35411ad`) on top of the native-AA recovery stack and the USB diagnostics stack.
Both screen-config commits the round is about are in the tree unmodified; what is additionally
present, and was not present in round 7, is everything else on that testing branch. Consequences:

- The unit-test gate number cannot be the brief's **1499**. It is **1592 / 0 failures**, the extra
  93 belonging to the other stacks. The four class counts the brief names are exact, and they are
  the real gate: `ScreenOrientationPolicyTest` **6**, `ScreenSettingsHashTest` **5**,
  `ProjectionCanvasPolicyTest` **14**, `TouchCoordinateMapperTest` **7**.
- **R6's arm had to be constructed rather than checked out.** The brief asks for a build of
  `94efd6df`, i.e. the candidate with `89093db4` reverted. On the testing branch the equivalent is
  the testing tip with `89093db4` reverted, which is what was built (`c70ed1b40`). `git revert`
  applied clean; nothing between `89093db4` and the testing tip touches
  `HeadUnitScreenConfig.kt`, `ScreenSettingsHash.kt` or `ScreenSettingsHashTest.kt`
  (`git log 89093db42..fork/testing/... -- <those files>` is empty). So the A/B still isolates that
  one commit, on a base that differs from the brief's in ways neither commit touches.

**Deviation 2: the baseline APK md5 is not round 7's `32b52a15`.** `409056f4` was rebuilt here and
came out `e9581d3a`; these builds are not byte-reproducible across sessions. Identity was carried by
the symbol matrix the brief specifies instead, which separates all three arms cleanly:

| arm | `ProjectionCanvasPolicy` | `ScreenOrientationPolicy` | `ScreenSettingsHash` |
|---|---|---|---|
| baseline `409056f4` | yes | — | — |
| R6 arm `c70ed1b40` | yes | yes | — |
| candidate `694af3e1a` | yes | yes | yes |

**Deviation 3: R4's `pm clear` needed a new script.** `r6_set_hu_prefs.sh` seds an existing
`settings.xml`, and `pm clear` removes `shared_prefs` entirely, so there is nothing to sed. Added
`hur-wifi-test-scripts/seed_hu_settings_fresh.sh`, which recreates the directory and writes a
settings file from nothing as root, then restores the app's uid/gid. **One thing it gets wrong that
the next round should know:** `restorecon` gave the new file `app_data_file:s0` **without** the app's
SELinux categories, while the directory got them; it was corrected by hand with an explicit
`chcon u:object_r:app_data_file:s0:c174,c256,c512,c768`. Left in the script; verify the label with
`ls -laZ` after seeding rather than trusting `restorecon`.

**Deviation 4: R4 was given two keys §2 does not list**, `wifi-connection-mode=3` and `view-mode=2`.
`pm clear` wipes them along with everything else, and without `wifi-connection-mode=3` the rig has no
transport at all (§7a: Native AA is the only one), so the run could not have formed a session. The
brief's step 2 says "write the standing settings from §2 into the now-empty `settings.xml`", and §2's
table alone is not enough after a clear.

**Deviation 5: R7's two rail taps are the horizontal rail's ends, not "bottom-most" and "top-most".**
Android Auto draws its facet bar along the **bottom** of the canvas on this rig at this resolution
(a 1280x120 `GhFacetBar` virtual display, confirmed from the phone's own
`DisplayDeviceInfo{"GhFacetBar" ... 1280 x 120}`), so there is no top-most or bottom-most rail icon
to aim at. Substituted the Maps icon (`496,660`) and the Phone icon (`704,660`) — two ends of the app
cluster, each with an unambiguous on-screen outcome. Verdict scored from which app opened, per §9.

**Deviation 6: the brief's §2 "which framework figure is the canvas" table asks for three rectangles
and only two are obtainable here.** `dumpsys window windows` on this ROM prints `mBounds` and
`mAppBounds` inside `winConfig={...}` for the projection activity, but no `Frames: frame=` line
appears anywhere in the `AapProjectionActivity` window block at any grep depth tried (`-A 14`,
`-A 22`, and a full undecorated `dumpsys window windows` dump kept as `R4.windows.txt`). Both figures
that decide a verdict are present: `mBounds=Rect(0, 0 - 1440, 720)` (the display) and
`mAppBounds=Rect(0, 0 - 1304, 720)` (the content area). Where a run below says "the window layout
frame", it is scored against `mBounds`.

**Deviation 7, and it is an erratum in the brief worth carrying forward: §7's precondition check
reads the wrong table.** `cat /proc/net/tcp | grep -i :149D` on D-MOTO returns nothing even when the
head unit server is running, because Gearhead binds the **IPv6** wildcard. The listener is in
`/proc/net/tcp6`:

```
23: 00000000000000000000000000000000:149D 000...0000:0000 0A ... uid 10161
```

Check both tables, or check neither and use
`cat /proc/net/tcp /proc/net/tcp6 | awk '$4=="0A"{split($2,a,":"); print a[2]}'`, which lists every
listening port in hex. This round held R5/R6 for an operator escalation on the strength of the
IPv4-only check; the operator did tap the toggle, and this write-up cannot say retroactively whether
the socket was already open at the two earlier checks (00:50 and 01:01), because only
`/proc/net/tcp` was read then. Either way the check as the brief states it cannot answer the
question.

**Erratum applied, not re-discovered:** the round 7 SSL-string erratum is already fixed inside
`ultrawide_touch_r7.sh`, which greps `SSL handshake complete|Self Mode session established` without
the `Handshake:` prefix. No change needed.

**Scripts used.** `thermal_guarded.sh` (all three builds; it fired on every one, 88-89 °C, longest
pause 95 s), `build_hur.sh`, `run_unit_tests.sh`, `r6_set_hu_prefs.sh` (every D-HU settings write),
`set_prefs_runas.sh` (every D-MOTO settings write), `ultrawide_touch_r7.sh` (every run, all three
phases, with `TAGFILTER="OPENHU:V *:S"` on D-MOTO). **Added:** `seed_hu_settings_fresh.sh`.
Each APK was copied out of `apks/` into `round-ultrawide-r8/` immediately after its build, per §7a's
note that `build_hur.sh` deletes the previous one.

**Pre-round settings delta: zero on both devices.** D-HU's `settings.xml` came into the round at md5
`06fb1e9478f045a01e59f5c4c1da9987`, exactly what round 7 restored. `use_measured_touch_surface=false`
present on both D-HU and D-MOTO, so neither file had been rewritten between rounds.

**One `sleep`-based scheduling attempt failed silently** and is worth recording: a backgrounded
`sleep 40; ultrawide_touch_r7.sh PHASE=end` produced a zero-byte output file and did not run the end
phase. It was re-run in the foreground with no loss (the R1b session was still up and the capture
still attached). Every later run drives its holds from inside the driver instead.

---

## R0 — build and unit-test gate

**PASS**

- Candidate `694af3e1a` `assembleGithubDebug` clean under `thermal_guarded.sh`.
- **1592 tests / 0 failures.** Not the brief's 1499; see Setup notes deviation 1.
- Brief-named classes, exact: `ScreenOrientationPolicyTest` 6, `ScreenSettingsHashTest` 5,
  `ProjectionCanvasPolicyTest` 14, `TouchCoordinateMapperTest` 7.
- Three APKs built, three distinct md5s, symbol matrix as tabulated above.

---

## R1 — D-HU, STATUS_ONLY, the decisive A/B

**PASS (candidate arm)**

- Settings written: `resolutionId=2`, `video-fit-mode=0`, `log-level=2`, `fullscreen-mode=2`.
- Radios: phone Bluetooth off before launch, on 16 s after (`ultrawide_touch_r7.sh` connect phase);
  §4's airplane-mode step is not available on this phone (§7a) and `svc bluetooth` is the lever.
- Discard-rule check, both arms: `createGroup SUCCESS` **1**, `SSL handshake complete` **1**,
  `Magic Garbage` **0**, one p2p interface each (`p2p-wlan0-0` baseline, `p2p-wlan0-1` candidate).
  One `MATCH! Starting AapService` on each arm with **zero group churn attached** — §7a's
  phone-own-Bluetooth-reconnect pattern, not contamination.

### Candidate arm (`8da1475b`), 00:53:28

```
00:54:02.967  Honest Init | Mode: STATUS_ONLY | Anchor: 1304x720 (from WINDOW) | Seeded Insets: L0 T0 R0 B0
00:54:03.126  [RES_CAP] resolutionId=2 realScreen=1304x720 usable=1304x720 ... chosen=_1280x720
00:54:03.130  CarScreen isSmallScreen: true, scaleFactor: 1.0, shape=PAR, margins: w=0, h=0
00:54:03.195  anchor 1304x720 -> 1440x720, measured on the activity window, against the insets now in force
00:54:03.198  [RES_CAP] realScreen=1440x720 usable=1304x720 ...
00:54:03.389  [ServiceDiscovery] NegotiatedResolution is: 1280x720
00:54:03.394  [ServiceDiscovery] Margins are: 0x0
00:54:03.396  [ServiceDiscovery] PixelAspectRatioE4 is: 10000 (10000 = square)
00:54:03.468  anchor 1440x720 -> 1304x720, measured on the activity window, against the insets now in force
00:54:03.471  [RES_CAP] realScreen=1304x720 usable=1304x720 ...
00:54:03.484  anchor 1304x720 -> 1440x720, measured on the activity window, against the insets now in force
00:54:03.488  [RES_CAP] realScreen=1440x720 usable=1304x720 ...
00:54:04.291  Normal Scale. scaleX: 1.0, scaleY: 1.0
```

Against the four PASS conditions:

1. **`usable=1168x720` occurs 0 times. `shape=MARGIN` occurs 0 times.** Every one of the five
   `CarScreen isSmallScreen` lines reads `shape=PAR, margins: w=0, h=0`.
2. Final `Anchor` **1440x720** vs `mBounds` **1440x720** — **0 px**. Steady-state `usable`
   **1304x720** vs `mAppBounds` **1304x720** — **0 px**.
3. `PixelAspectRatioE4 is: 10000`. `1304/1280 = 1.01875`, 1.88 % from square, inside the 3 % dead
   band. Screencap `R1c.png`: picture fills the area inside the bar, no letterbox.
4. **One** `Honest Init`.

Re-derivation lines: **3**, quoted in full above, at the brief's "more than three is worth
reporting" boundary rather than over it. They are the anchor tracking the inset going on
(1304→1440), off (1440→1304) and on again (1304→1440) within **289 ms**, while `usable` stays
1304x720 across all three — which is the invariant the fix is for. `canvas moved from the announced`
**0**, `canvas returned to the announced` **0**, `Using cached surface dimensions` **0**,
`Cache invalidated` **0**.

### Baseline arm (`e9581d3a`), 00:51:28 — measurement, no PASS condition

The round 7 signature reproduces exactly:

```
00:52:01.349  Honest Init | Mode: STATUS_ONLY | Anchor: 1304x720 (from WINDOW) | Seeded Insets: L0 T0 R0 B0
00:52:01.507  [RES_CAP] realScreen=1304x720 usable=1304x720 ... locked=false
00:52:01.578  [RES_CAP] realScreen=1304x720 usable=1168x720 ... locked=true      <-- the double-shrink
00:52:01.582  CarScreen isSmallScreen: true ... shape=MARGIN, margins: w=112, h=0
00:52:01.709  anchor 1304x720 -> 1440x720, measured on the projection surface     <-- corrected here
00:52:01.712  [RES_CAP] realScreen=1440x720 usable=1304x720 ...
00:52:01.776  [ServiceDiscovery] NegotiatedResolution is: 1280x720
00:52:01.782  [ServiceDiscovery] Margins are: 0x0
00:52:01.783  [ServiceDiscovery] PixelAspectRatioE4 is: 10000
00:52:01.845  CarScreen: canvas moved from the announced 1304x720 to 1440x720; the pixel shape cannot be re-sent
```

- `usable=1168x720` **1 occurrence**; `shape=MARGIN, w=112` **1 occurrence**.
- **Duration of the wrong state: 131 ms** (00:52:01.578 → 00:52:01.709). Round 7 measured 140 ms.
- It is corrected **by the projection surface** (`measured on the projection surface`), 67 ms before
  `makeProto` at 00:52:01.776, so nothing wrong reached the wire on this unit — the same race round 7
  described, won again.
- `canvas moved from the announced` fires once and **latches**: no retraction line exists on this
  build, and the anchor was in fact back at the announced value 1304 within the same second.

**So the answer to the brief's first report-back question: `usable=1168x720` occurred on the
baseline arm only, once, for 131 ms, and zero times on the candidate.**

---

## R2 — D-HU, Screen mode Normal, regression guard

**PASS** — bit-identical to round 7 R2 on all seven figures.

- Settings written: `fullscreen-mode=0` (others unchanged from R1).
- Discard-rule check: `createGroup SUCCESS` 1, `SSL handshake complete` 1, `Magic Garbage` 0,
  `p2p-wlan0-2`. One `MATCH!`, no churn.

```
00:55:16.619  Honest Init | Mode: NONE | Anchor: 1304x720 (from WINDOW) | Seeded Insets: L0 T0 R0 B0
00:55:16.777  [RES_CAP] resolutionId=2 realScreen=1304x720 usable=1304x720 ... chosen=_1280x720
00:55:16.781  CarScreen isSmallScreen: true, scaleFactor: 1.0, shape=PAR, margins: w=0, h=0
00:55:17.031  [ServiceDiscovery] NegotiatedResolution is: 1280x720
00:55:17.039  [ServiceDiscovery] Margins are: 0x0
00:55:17.041  [ServiceDiscovery] PixelAspectRatioE4 is: 10000 (10000 = square)
00:55:18.009  Normal Scale. scaleX: 1.0, scaleY: 1.0
```

`Honest Init` 1, `shape=MARGIN` 0, `measured on the activity window` 0,
`canvas moved from the announced` 0. In this mode the OEM bar reduces the window itself, so no inset
is ever in force and the anchor never needs re-deriving — which is why the re-derivation line that
appears three times in R1 appears zero times here.

---

## R3 — D-HU, Immersive, regression guard

**PASS** — bit-identical to round 7 R4.

- Settings written: `fullscreen-mode=1`.
- Discard-rule check: `createGroup SUCCESS` 1, `SSL handshake complete` 1, `Magic Garbage` 0,
  `p2p-wlan0-3`. One `MATCH!`, no churn.

```
00:56:20.295  Honest Init | Mode: IMMERSIVE | Anchor: 1440x720 (from WINDOW) | Seeded Insets: L0 T0 R0 B0
00:56:20.461  [RES_CAP] resolutionId=2 realScreen=1440x720 usable=1440x720 ... chosen=_1280x720
00:56:20.465  CarScreen isSmallScreen: true, scaleFactor: 1.0, shape=PAR, margins: w=0, h=0
00:56:20.749  [ServiceDiscovery] NegotiatedResolution is: 1280x720
00:56:20.755  [ServiceDiscovery] Margins are: 0x0
00:56:20.757  [ServiceDiscovery] PixelAspectRatioE4 is: 11250 (10000 = square)
00:56:21.617  Normal Scale. scaleX: 1.0, scaleY: 1.0
```

`Honest Init` 1, `shape=MARGIN` 0, re-derivation 0, `canvas moved` 0.

---

## R4 — D-HU, Screen mode Normal, from nothing measured

**PASS on all three stated conditions — but the fresh-install display fallback was not the path
taken.** Stated plainly because the brief asks for exactly this distinction.

- Setup: `pm clear com.andrerinas.headunitrevived`, then `seed_hu_settings_fresh.sh` wrote
  `resolutionId=2 video-fit-mode=0 log-level=2 fullscreen-mode=0 wifi-connection-mode=3 view-mode=2`
  into a settings file created from nothing, app stopped throughout. Read back before the run:
  those six keys and nothing else, `cached-surface-*` absent.
- Discard-rule check: `createGroup SUCCESS` 1, `SSL handshake complete` 1, `Magic Garbage` 0,
  `p2p-wlan0-4`, and `MATCH! Starting AapService` **0** (a cleared install has no
  `auto-start-bt-macs` yet).

```
00:58:02.478  Honest Init | Mode: NONE | Anchor: 1304x720 (from WINDOW) | Seeded Insets: L0 T0 R0 B0
00:58:02.481  [RES_CAP] resolutionId=2 realScreen=1304x720 usable=1304x720 portrait=false locked=false chosen=_1280x720
00:58:02.484  CarScreen isSmallScreen: true, scaleFactor: 1.0, shape=PAR, margins: w=0, h=0
00:58:02.965  [ServiceDiscovery] NegotiatedResolution is: 1280x720
00:58:02.971  [ServiceDiscovery] Margins are: 0x0
00:58:02.974  [ServiceDiscovery] PixelAspectRatioE4 is: 10000 (10000 = square)
00:58:03.680  Normal Scale. scaleX: 1.0, scaleY: 1.0
```

1. First (and only) `Honest Init` announces **1304x720**, the content area `mAppBounds`, **0 px**
   from it. In Screen mode Normal that is the drawn canvas; `mBounds` is 1440x720 and the 136 px
   difference is the OEM bar, which in this mode reduces the window rather than sitting on it as an
   inset.
2. First `[RES_CAP]` reads `usable=1304x720` — **0 px** from the drawn content area, and **not**
   `1168x720`. The double-shrink this run exists for did not happen.
3. `PixelAspectRatioE4 10000`, derived from that `usable` by R2's arithmetic (`1304/1280` inside the
   3 % dead band).

**The caveat, and it is the brief's second report-back question.** The first `Honest Init` says
**`(from WINDOW)`**, and `(from DISPLAY)` appears **0 times** anywhere in the capture. A MainActivity
window layout beat the display fallback, so the last-resort window-metrics reading this run was
written to exercise **was never reached on this rig**. What R4 does prove is that a genuinely empty
`cached-surface-*` set produces a correct first announcement; what it does not prove is the
inset add-back on the display-sourced path, which remains covered only by
`ProjectionCanvasPolicyTest` on the JVM.

**The wizard did not write anything.** `resolutionId` read back `2` after the run, unchanged, and no
second `Honest Init` appeared. The post-run file had grown the expected session keys
(`cached-surface-width=1304`, `cached-surface-height=720`, `wifi-direct-*`, `last-connection-*`) and
kept all six seeded values.

---

## R7 — D-HU touch in STATUS_ONLY

**PASS on all three conditions.**

- Settings written: `fullscreen-mode=2`, **`log-level=0`** (VERBOSE — `Touch map` is `AppLog.v`).
- Discard-rule check: `createGroup SUCCESS` **1**, `Magic Garbage` **0**, `p2p-wlan0-5`,
  `MATCH!` **0**. `SSL handshake complete` counts **2**, which is round 7's documented VERBOSE
  artifact (the DEBUG-level `Handshake:` line joins the INFO one at `log-level=0`), not a second
  session — one `createGroup`, one p2p interface, one continuous session across all five taps.
- Taps, and the substitution: see Setup notes deviation 5. Targets read off the live `R7.png`.

| # | panel tap | `Touch map` | phone `GhFacetBar injectMotionEvent` | outcome |
|---|---|---|---|---|
| 1 | `496,660` Maps icon | `raw=496,660 -> video=487,660 view=1304x720 video=1280x720 margin=0x0 fit=FILL` | `x=487.0, y=60.0` | Maps dashboard/split opened (`R7_t1.png`) |
| 2 | `704,660` Phone icon | `raw=704,660 -> video=691,660 view=1304x720` | `x=691.0, y=60.0` | Phone opened — Favourites / Recent / Contacts / Dial a number (`R7_t2.png`) |
| 3 | `652,360` centre | `raw=652,360 -> video=640,360 view=1304x720` | — (map surface) | map, no control |
| 4 | `20,360` left edge | `raw=20,360 -> video=20,360 view=1304x720` | — | map, no control |
| 5 | `1284,360` §2's target | `raw=1284,360 -> video=1260,360 view=1304x720` | — | inside the picture, **not** the OEM bar; session survived |

1. **Every `Touch map` line reads `view=1304x720`.** `grep -ao "view=[0-9]*x[0-9]*"` over the whole
   capture returns `10 view=1304x720` and nothing else — the drawn content area, never the 1440
   display.
2. No mapped `video=` Y equals the buffer height. Y is unscaled throughout (`scaleY 1.0`): 660→660,
   360→360. X is exactly proportional with no offset and no clamp: `496 × 1280/1304 = 486.6 → 487`,
   `704 → 691`, `652 → 640`, `20 → 20`, `1284 → 1260`.
3. **Both rail taps opened the control they were aimed at**, from the screencaps.

The phone-side coordinates corroborate the mapping directly rather than by inference: the facet bar
is its own 1280x120 virtual display, so a `video=487,660` in a 1280x720 buffer arrives as
`x=487.0, y=60.0` in that bar — same X to the pixel, Y offset by the bar's own origin at row 600.
The ~88 px leftward shift the brief warns about did not occur here.

**One thing R7 saw that R1 did not: this session took two `Honest Init`s**, the second at
`Anchor: 1440x720 (from SURFACE) | Seeded Insets: L0 T0 R136 B0` 0.46 s after the first. It is the
surface arriving and confirming what the window had already re-derived — `usable` was already
1304x720 and stayed there, `shape=PAR` throughout, `usable=1168x720` and `shape=MARGIN` both **0** —
so it changes nothing about the announcement. Worth noting only because R1's PASS condition 4 caps
`Honest Init` at one, and the same mode on the same build produced two here. The difference between
the two runs is the log level, which suggests the second init is timing-sensitive rather than
mode-determined.

---

## R5 — D-MOTO, Self Mode, Immersive

**PASS on all four conditions.**

- Settings written: `resolutionId=3`, `video-fit-mode=0`, `log-level=2`, `fullscreen-mode=1`
  (`set_prefs_runas.sh`, app force-stopped). `wifi-connection-mode=3` and `view-mode=2` left as found.
- Precondition: see the erratum below — the head unit server **was** listening, on `tcp6`.
- Discard-rule check: `createGroup SUCCESS` **1**, `SSL handshake complete` **1**, `Magic Garbage`
  **0**, `MATCH!` **0**, no p2p interface. (The single `createGroup` fires 0.8 s *after* the Self
  Mode session is already up: it is the Native AA quiet host arming itself because
  `wifi-connection-mode=3`, not a transport for this session.)
- Capture was tag-filtered `OPENHU:V *:S`. On this phone an unfiltered `adb logcat` loses every OHU
  line in ROM spam, so the framework lines §2 asks for are not in this capture; the R5/R6 conditions
  are all OHU lines.

```
01:07:30.235  Honest Init | Mode: IMMERSIVE | Anchor: 2400x1080 (from WINDOW) | Seeded Insets: L0 T0 R0 B0
01:07:30.284  [RES_CAP] resolutionId=3 realScreen=2400x1080 usable=2400x1080 portrait=false locked=false chosen=_1920x1080
01:07:30.286  CarScreen isSmallScreen: false, scaleFactor: 1.25, portraitScaled: false, shape=PAR, margins: w=0, h=0
01:07:30.494  [ServiceDiscovery] NegotiatedResolution is: 1920x1080
01:07:30.497  [ServiceDiscovery] Margins are: 0x0
01:07:30.498  [ServiceDiscovery] PixelAspectRatioE4 is: 12500 (10000 = square)
01:07:31.539  Normal Scale. scaleX: 1.0, scaleY: 1.0
```

1. **One** `Honest Init` for the session.
2. Source **`(from WINDOW)`**. `(from DISPLAY)` **0 times** anywhere in the capture.
3. **No portrait reading adopted.** `anchor ... -> 1080x2400` **0 times**, `chosen=_1080x1920`
   **0 times**. `1080x2400` does occur three times, and all three are benign: two are the
   pre-normalisation diagnostic `Raw size: 1080x2237, usable: 1080x2400, orientation setting:
   LANDSCAPE, configOrientation: 1` and one is `GlProjectionView: onSurfaceChanged: 1080x2400`, the
   GL surface's own raw dimensions. The announced anchor is `2400x1080` in the same millisecond, so
   `ScreenOrientationPolicy` normalised the portrait reading rather than adopting it — round 7's
   61 ms portrait excursion does not happen.
4. `margins drifted from the announced` **0**, `[UI_DEBUG_FIX] TX UpdateUiConfigRequest` **0**
   (round 7 had two of each).

**Condition 4 is met but not exercised, and that matters for how much it is worth.** The brief asks
to compare the settled `Margins are:` and `Normal Scale` with round 7's `0x216` / `scaleY 1.25`.
They are **not** the same: this run settles at `Margins 0x0`, `Normal Scale 1.0/1.0`, `PAR 12500`.
Round 7's D-MOTO figures came from its **CONTAIN** run (R7); this brief specifies
`video-fit-mode=0` (FILL), and on a 2.222-aspect panel `MarginStrategyPolicy` puts FILL on the PAR
path, where the margin is 0 by construction. So there was never a margin to drift, and the drift and
re-announce machinery condition 4 counts was never engaged. The excursion through `0x231` /
`1.2720848` is absent because this path has no margin at all, not because the fix suppressed it.

**Taps.** Targets read off the live `R5.png`; on D-MOTO the rail *is* vertical, so "top-most" and
"bottom-most" are literal here (unlike R7 on D-HU).

| # | panel tap | outcome |
|---|---|---|
| 1 | `62,400` top-most rail icon (Maps) | split/dashboard collapsed to **full-width Maps** (`R5_t1.png`) |
| 2 | `62,1020` bottom-most rail icon (split-view toggle) | returned to the **split/dashboard** view (`R5_t2.png`) |

Both taps reached the control at that rail position and not a neighbour. No `Touch map` line exists
for either, as expected: R5 runs at `log-level=2` and `Touch map` is `AppLog.v`. The verdict is from
the screencaps, per §7 of the brief.

---

## R6 — the A/B the last commit earns

**PASS — the two arms separated cleanly, and `89093db4` is proven on this rig.**

- Arm: `c70ed1b40` (md5 `9cadf6f9`), `ScreenSettingsHash` absent from its DEX (grep count 0) while
  `ScreenOrientationPolicy` and `ProjectionCanvasPolicy` are present — the intended one-commit delta.
- Same settings, same Self Mode Immersive setup as R5. Discard-rule check: `createGroup SUCCESS`
  **1**, `SSL handshake complete` **1**, `Magic Garbage` **0**, `MATCH!` **0**.

This arm reproduces round 7's finding exactly, including the hash delta the brief predicted:

```
01:09:47.789  Honest Init | Mode: IMMERSIVE | Anchor: 2400x1080 (from WINDOW) | Seeded Insets: L0 T0 R0 B0
01:09:47.993  [ServiceDiscovery] NegotiatedResolution is: 1920x1080
01:09:47.995  [ServiceDiscovery] Margins are: 0x0
01:09:47.996  [ServiceDiscovery] PixelAspectRatioE4 is: 12500
01:09:48.595  Settings changed (-1320151468 -> -1320149578). Unlocking resolution.
01:09:48.595  Honest Init | Mode: IMMERSIVE | Anchor: 2300x1017 (from DISPLAY) | Seeded Insets: L0 T0 R0 B0
01:09:48.596  [RES_CAP] resolutionId=3 realScreen=2300x1017 usable=2300x1017 ... locked=false
01:09:48.597  CarScreen: canvas moved from the announced 2400x1080 to 2300x1017; the pixel shape cannot be re-sent
01:09:48.606  anchor 2300x1017 -> 2400x1080, measured on the activity window
01:09:48.608  CarScreen: canvas returned to the announced 2400x1080
```

**The hash delta is `-1320151468 -> -1320149578` = +1890**, the exact figure the brief derived from
round 7's capture, and solving it puts the first reading at the same 63 px decoration counted on a
different axis. The second `Honest Init` reads `(from DISPLAY)` at `2300x1017`, 806 ms after the
first and 599 ms after `makeProto`.

Side by side, over the whole capture:

| | R5 (candidate `694af3e1a`) | R6 (`c70ed1b40`, hash commit reverted) |
|---|---|---|
| `Honest Init` | **1** | **2** |
| `(from DISPLAY)` | **0** | **1** |
| `Settings changed (` | **0** | **1** |
| `Unlocking resolution` | 1 | 2 |
| `canvas moved from the announced` | **0** | **1** |
| `canvas returned to the announced` | 0 | 1 |
| `anchor ... -> 1080x2400` | 0 | 0 |
| `chosen=_1080x1920` | 0 | 0 |
| `margins drifted` / `UpdateUiConfigRequest` | 0 / 0 | 0 / 0 |

**So the answer to the brief's third report-back question: R6 did separate the two arms.** The
`(from DISPLAY)` fall-through, the spurious second init and the spurious resolution unlock are all
`89093db4`'s to remove, and it removes them. This is the round's strongest result.

Two things the A/B shows in passing:

- **The `canvas moved` / `canvas returned` pair reads honestly on both builds that have `94efd6df`.**
  On this arm the retraction lands **11 ms** after the excursion, so the latch round 7 complained
  about is gone independently of the hash commit. Contrast R1's baseline arm (`409056f4`, neither
  commit), where `canvas moved` fires and no retraction line exists at all.
- **`94efd6df`'s re-derivation already limits the damage on this arm.** `anchor 2300x1017 ->
  2400x1080, measured on the activity window` corrects the bad reading 11 ms later, so even the
  reverted arm never announces `2300x1017` — the cost of the missing hash fix here is a spurious
  init, a spurious unlock and a 11 ms wrong canvas, not a wrong wire announcement. The
  two commits are complementary rather than redundant.

**Taps** repeated at the same two coordinates for parity (`R6_t1.png`, `R6_t2.png`); both reached
their rail control, as on R5.

---

## Restoration

- **D-HU:** `settings.xml` restored md5-identical to its pre-round value,
  `06fb1e9478f045a01e59f5c4c1da9987`, with owner `u0_a174:u0_a174` and the app's full SELinux label
  re-applied by hand after the `pm clear` (`ls -laZ` verified). Carrying the **candidate** APK
  `8da1475b`.
- **D-POCO:** untouched; it was only ever the projecting phone. Still carrying `7b5a8bb1`.
- **D-MOTO:** `settings.xml` restored md5-identical to `007bead8f256954ffee292e42e1422b5` (pushed
  script, not inline `run-as sh -c cp`, per §7a). Left carrying the **candidate** `8da1475b` — the
  R6 arm was installed only for that run and replaced afterwards, so no device is left holding a
  deliberately-reverted build.

---

## Anything the brief did not ask about

**The `[RES_CAP] locked=` flag tracks the double-shrink exactly, on both arms.** On the baseline the
bad line is the first `locked=true` one; the preceding `locked=false` line is correct and the
following one, after the surface correction, is correct again. On the candidate every `locked=true`
line already reads `usable=1304x720`. If a later round wants a one-line grep for this fault rather
than a two-line one, `locked=true` paired with a `usable` narrower than the content area is it.

**The candidate's three re-derivations land inside 289 ms and the middle one is a full retreat**
(1440 → 1304 → 1440). The insets genuinely flap on this ROM; the fix rides it correctly, but the
anchor is not monotonic and any future code that latches on the anchor's first settled value will
pick the wrong one about a third of the time on this unit. `usable` is the stable quantity, not the
anchor.

**R4's `pm clear` cost nothing but is not free in general.** The session that followed reached SSL in
28.57 s from launch to SSL versus 28.67 / 28.50 / 29.96 s for R1c / R2 / R3, i.e. no first-run penalty on this
rig, and the first-time-setup wizard never wrote a resolution. That is worth knowing before the next
round budgets time for a cleared install.

**The brief's R5 comparison target does not exist under its own settings.** §7 asks to compare the
settled margin and scale against round 7's `0x216` / `scaleY 1.25`, but round 7 reached those under
CONTAIN and §7 specifies `video-fit-mode=0` (FILL), which on this panel is the PAR path with no
margin at all. A future D-MOTO round that wants the margin machinery under test has to set
`video-fit-mode` to CONTAIN explicitly, or accept that `margins drifted` and `UpdateUiConfigRequest`
counts of zero are unexercised rather than earned. This is the same `MarginStrategyPolicy` clamp
question round 6's R4 raised and round 7 left open.

**Incidental, not attributed:** Spotify playback on D-MOTO went from paused to playing between R5's
two taps, with neither tap anywhere near the transport controls (`62,1020` versus the play button at
roughly `232,1000`). Most likely the app's own quick-reconnect media auto-resume. Noted because it
changes what `R5_t2.png` shows, not because anything depends on it.
