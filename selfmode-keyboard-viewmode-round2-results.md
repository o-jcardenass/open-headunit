# selfmode-keyboard-viewmode — round 2 results

**Candidate:** `fix/883-self-mode-call-raise` @ `6411eaef` (built from source this round)   **Baseline:** none
**APK md5:** `35dc0b84142577fedd14adb91273e725` (`com.andrerinas.headunitrevived` 3.3.0-beta3, versionCode 102)
**Unit:** D-POCO — Redmi M2007J20CG / POCO X3 NFC, `surya`, Android 15 / SDK 35, Snapdragon 732G (`sm6150`), decoder `c2.qti.avc.decoder`. Android Auto Self Mode, AA 17.4+ loopback route (`127.0.0.1:5277`). Negotiated 1920x1080, H.264, ~69 s GOP, fps-limit 60.
**Date:** 2026-08-28
**Type:** fix verification.

## Verdicts

| Run | Verdict | Headline number |
|---|---|---|
| R1 — keyboard stays (SURFACE, phone-key) | **PASS** | keyboard held ~23 s, dismissed by operator, no bounce |
| R2 — Home press positive control | **PASS** | `onResume` → first frame **359 ms** |
| R3 — the return after the keyboard | **PASS** | `onResume` → repaired picture **1643 ms** |
| R4 — live view-mode switch ×3 | **PASS** | gray→picture **1648 / 1634 / 1555 ms** (was 11.0 / ≥32.4 / 66.5 s) |
| R5 — 10-minute steady-state guard | **PASS** | 127 windows, 49–60 fps, `dropped=0`, zero new lines |
| R6 — two-minute hold + typing | **PASS** | 108 s hold, session never dropped, typing worked |
| R5d — R5 re-run, WiFi off / offline VPN route (added on request) | **PASS** | 10.4 min, 124 windows, 49–59 fps, `dropped=0`, zero gate lines, zero disconnects |

The three numbers the brief said decide shipping:

1. **R1** — keyboard stayed, ~23 s (operator ended it, not a bounce).
2. **R3** — `onResume` → repaired picture: **1643 ms** (R6's longer-hold return: 1615 ms; R4's three: 1.56–1.65 s). This is the cost of the fix.
3. **R2** — `onResume` → first frame: **359 ms**. The cost was not charged to the general path.

## Setup notes

- **Built from source.** `git fetch fork` / `git checkout fix/883-self-mode-call-raise` / `git reset --hard 6411eaef`; `git log --oneline -5` = `6411eaef c38ae725 04dc6eab 1fffad01 47290f01`, matches the brief. `run_unit_tests.sh` (`testGithubDebugUnitTest`): **863 tests, 0 failures, 0 errors**; `VideoFocusReleasePolicyTest` (8) and `PictureCredibilityPolicyTest` (9) both present and green. APK built with `build_hur.sh`, installed with `adb -s 4f4027e9 install -r` (the shared `install_and_launch.sh` hardcodes the UNISOC serial, so it was not used); live-APK md5 on device = `35dc0b84142577fedd14adb91273e725`, matches the build.
- **Settings** written after install with the app force-stopped, via `hur-wifi-test-scripts/set_pref.sh` (`DEVICE=4f4027e9`): `log-level` 2→0, `view-mode` 1→0, `auto-start-self-mode` false→true. `raise-projection-over-keyboard` (stale, value `true`) deleted with a one-line pushed `sed` script. Readback confirmed all four; XML validated with `xml.dom.minidom`. The reinstall was an upgrade so onboarding did not re-run (`onboarding-version=2`, `has-accepted-disclaimer=true` survived) — `view-mode` was still read back after install per the brief and held.
- **Operator in the loop, by design** (same as round 1). The phone-keyboard-key tap (R1, R3, R6) and the Quick Settings view-mode switch (R4) have no scriptable trigger. The operator performed those on cue and reported what was on screen; every decisive line below is from the device log with timestamps. R2 (Home press) and R5 (mock-drive + media key) were fully scripted.
- **Gearhead not force-stopped during any run.** The session formed once at 10:20:23 (`AA 17.4+ detected` → `SSL handshake complete` → `First frame rendered` at 10:20:27.763, SurfaceView, `c2.qti.avc.decoder`, 1920x1080) and was reused across R1–R6 with no re-handshake. No `LogAccessDialog`.
- **Captures:** `hur-wifi-test-scripts/round-882-keyboard-viewmode/R2round-{R1R3,R2,R5,R6}.txt` (full `stdbuf -oL adb logcat -v time`), `R2round-R4-{allbuf,detail}.txt` (see deviation 1). Not committed here.

### Deviations (both after all six runs' data was captured)

1. **R4 had no dedicated streaming capture.** The capture was stopped after R2 and not restarted before the operator ran R4. Every decisive line for R4 is at `I`/`W` level and survived in the `-b all` ring buffer; it was pulled with `adb logcat -d -b all` immediately after, with full timestamps, into `R2round-R4-detail.txt`. All three transitions are fully accounted for (recreate → new line once → focus cycle → keyframe → repaired) so the run is decisive, but a live capture would have carried the per-frame throughput lines too.
2. **The teardown script force-stopped Gearhead.** The `restore` step ran `am force-stop com.google.android.projection.gearhead` (copied from `selfmode_session_probe.sh`'s stop-both pattern), which the brief §3 forbids. All six runs were already complete. Confirmed afterward: nothing listens on `:5277`, so D-POCO's dev head-unit server is down and needs a manual "Start head unit server" toggle in AA Developer settings before the next Self Mode round on this unit. `external_keyboard_last_open_state` was not touched (no Gearhead settings were changed), but the force-stop itself is the rule break.

---

## R1 — SURFACE, phone-key keyboard: does it stay? — PASS

Settings: `view-mode=0`, `log-level=0`, `raise-projection-over-keyboard` absent. One operator cycle: opened the AA keyboard, tapped the phone-keyboard key, left it untouched, dismissed after well over 10 s. Operator: "keyboard stayed up, picture's back."

```
10:20:56.577 ATM     START u0 {cmp=.../externalkeyboard/phone/PhoneKeyboardActivity} LAUNCH_SINGLE_TASK from uid 10193 (BAL_ALLOW_PERMISSION)
10:20:56.582 OPENHU  AapProjectionActivity: onPause
10:20:57.175 ATM     Displayed .../PhoneKeyboardActivity for user 0: +583ms
10:20:57.276 OPENHU  ProjectionView.surfaceDestroyed
10:20:57.420 OPENHU  VideoDecoder.stop | Decoder stopped: surfaceDestroyed
10:20:57.421 OPENHU  SurfaceCallback: onSurfaceDestroyed. Surface: ...@0xaf4c69b
10:20:57.430 OPENHU  AapProjectionActivity: the surface went away 1352ms after a touch - holding video focus so Android Auto keeps its keyboard up
    ... keyboard on screen, untouched, ~23 s. NO focus notification, NO detach in this window ...
10:21:20.034 CoreBackPreview  startBackNavigation ... PhoneKeyboardActivity     (operator dismisses)
10:21:20.805 OPENHU  AapProjectionActivity: onResume
10:21:20.846 OPENHU  AapTransport.send | VIDEO Video Focus Notification         (first one since the hold began)
10:21:21.426 VRI[PhoneKeyboardActivity]  visibilityChanged newVisibility=false
```

**All four PASS conditions met:**
- `SurfaceCallback: onSurfaceDestroyed.` **present** (10:20:57.421) — reachability guard satisfied.
- `the surface went away ... - holding video focus ...` present **once** (10:20:57.430).
- **No** `VIDEO Video Focus Notification` between that line (10:20:57.430) and the keyboard being dismissed. The next one is 10:21:20.846, after `onResume`.
- **No** `Asked by projected IME to detach` anywhere in the capture (grep count 0). Keyboard on screen from `Displayed` (~10:20:57.16) to back-nav (10:21:20.03) ≈ **23 s**, ended by the operator.

Decoder: 3× `Decoder stopped` total in the capture (1 startup `New surface` + 2 `surfaceDestroyed` for the one cover). 0 `sync_stall`, 0 `decoderPermanentlyFailed`, 0 `ByeBye`. Throughput 49–52 fps `dropped=0` in every window before and after the cover (none logged during it — decoder was stopped).

Aside: Gearhead logged `maybeStartExternalKeyboard Keyboard is locked due to car being in motion` at 10:20:56.533, then `External keyboard is already running` — the phone keyboard launched and held regardless.

## R2 — SURFACE, Home press: the positive control — PASS

Same live session. Scripted: `input keyevent KEYCODE_HOME`, wait 5 s, `am start ...AapProjectionActivity`.

```
10:22:18.286 (host)  HOME
10:22:18.355 OPENHU  AapProjectionActivity: onPause
10:22:19.524 OPENHU  ProjectionView.surfaceDestroyed
10:22:19.696 OPENHU  SurfaceCallback: onSurfaceDestroyed. Surface: ...@0xaf4c69b
10:22:19.696 OPENHU  AapTransport.send | VIDEO Video Focus Notification            <- sent immediately
10:22:19.743 OPENHU  VideoDecoder.start | Decoder start aborted: the surface went away mid-configure. Waiting for a new one.
10:22:23.368 (host)  return
10:22:23.440 OPENHU  AapProjectionActivity: onResume
10:22:23.465 OPENHU  AapTransport.send | VIDEO Video Focus Notification
10:22:23.731 OPENHU  VideoDecoder: keyframe reached the codec (8200 bytes)
10:22:23.799 OPENHU  VideoDecoder: keyframe decoded - the picture is repaired
10:22:23.799 OPENHU  First frame rendered (hardware decode)
```

**All PASS conditions met:**
- `VIDEO Video Focus Notification` **is** sent right after `onSurfaceDestroyed` — same millisecond (10:22:19.696).
- **No** `holding video focus` line (grep count 0).
- `onResume` (10:22:23.440) → `First frame rendered` (10:22:23.799) = **359 ms** (< 500 ms). The phone re-ran sink setup on the release and delivered a keyframe inside that window (`picture is repaired` at the same 10:22:23.799).

This is the run that proves the R1 gate is narrow: an ordinary teardown still releases focus and still takes the fast path.

## R3 — the return after the keyboard, measured — PASS

Continues from R1's dismiss (same capture).

```
10:21:20.805 OPENHU  AapProjectionActivity: onResume
10:21:20.873 OPENHU  VideoDecoder.start | Configuring decoder: c2.qti.avc.decoder for 1920x1080 ... optionalKeys=none
10:21:20.923 OPENHU  Codec initialized: c2.qti.avc.decoder
10:21:20.996 OPENHU  First frame rendered (hardware decode)          <- gray P-frame, 191 ms
10:21:21.695 OPENHU  frames are rendering but no keyframe has decoded since the codec started - counting this surface as having no picture
10:21:21.699 OPENHU  AapTransport.send | VIDEO Video Focus Notification
10:21:21.700 OPENHU  relaunched surface has no picture after 850ms - cycling video focus
10:21:22.101 OPENHU  AapTransport.send | VIDEO Video Focus Notification
10:21:22.437 OPENHU  VideoDecoder: keyframe reached the codec (8200 bytes)
10:21:22.448 OPENHU  VideoDecoder: keyframe decoded - the picture is repaired
```

**PASS:** `relaunched surface has no picture after 850ms - cycling video focus` appears, followed by `keyframe reached the codec`, and `onResume` (10:21:20.805) → `keyframe decoded - the picture is repaired` (10:21:22.448) = **1643 ms** (< 4000 ms; brief expected 1.7–3.2 s, came in just below).

The two commits are coupled exactly as the brief describes: `First frame rendered` fires at 191 ms on a gray P-frame, and the **new** credible-picture gate does not stand down on it — it counts the surface as having no picture and drives the focus cycle, which pulls the IDR. Only the `AapProjectionActivity: relaunched surface has no picture` variant fired; the `AapTransport: picture unrepaired for` lever did not appear.

## R4 — live Quick Settings view-mode switch, all three transitions — PASS

Settings: started `view-mode=0`. Operator switched View Mode in OHU Quick Settings three times, leaving each in place until the picture returned. Operator: "the gray was about 1 second in each transition." (See deviation 1 — lines are from `adb logcat -d -b all` pulled right after.)

| Transition | recreate | `no keyframe` line | `no picture ... cycling` | `keyframe reached` | `picture is repaired` | recreate → repaired |
|---|---|---|---|---|---|---|
| SURFACE→TEXTURE | 10:23:16.033 | 10:23:16.461 (×1) | 10:23:16.997 (859 ms) | 10:23:17.670 | 10:23:17.681 | **1648 ms** |
| TEXTURE→GLES | 10:23:29.861 | 10:23:30.463 (×1) | 10:23:30.794 (851 ms) | 10:23:31.477 | 10:23:31.495 | **1634 ms** |
| GLES→SURFACE | 10:23:43.295 | 10:23:44.178 (×1) | 10:23:44.179 (850 ms) | 10:23:44.837 | 10:23:44.850 | **1555 ms** |

**PASS:** on every transition the new line appears exactly once, followed by the cycle and `keyframe reached the codec`, gray-to-picture **1.56–1.65 s** (< 5000 ms). Round 1 measured 11.0 s, ≥32.4 s and 66.5 s for the same three. No `sync_stall`, no `decoderPermanentlyFailed`, no fallback. `First frame rendered` still fires ~150–250 ms into each transition on the gray P-frame (10:23:16.274, 10:23:30.053, 10:23:43.459) — the gate now correctly ignores it. The operator's perceived "~1 s" is the ~0.85 s the gate deliberately waits plus the ~0.7 s focus-cycle round trip to the keyframe.

## R5 — steady-state regression guard — PASS

Settings unchanged (`view-mode=0`). Scripted: fresh capture, `mock_drive.sh 600` feeding the mock GPS provider so Maps kept panning, `input keyevent 126` for media (confirmed `PlaybackState {state=PLAYING(3)}`), projection left uncovered. Capture 10:25:29 → 10:36:08 (~10.6 min), throughput windows 10:25:33 → 10:36:04.

**Both PASS conditions met:**
- **Zero** `frames are rendering but no keyframe has decoded` lines, **zero** `relaunched surface has no picture` lines. Also zero `Recreating projection view`, zero `onSurfaceDestroyed`, zero `holding video focus`, zero `Requesting Keyframe`, zero `sync_stall`, zero `decoderPermanentlyFailed`, zero disconnects.
- **127** throughput windows, every one `dropped=0`. `presented` fps range **49–60** (distribution: 51 fps ×25, 52 fps ×29, 57 fps ×13, 59 fps ×14; one 49 fps window). `decodeLatency` 15–16 ms.

## R6 — a long hold — PASS

As R1 but the operator held the keyboard for ~2 minutes and typed into the field. Operator: "typing worked, no banner."

```
10:37:30.289 OPENHU  AapProjectionActivity: onPause
10:37:30.596 ATM     Displayed .../PhoneKeyboardActivity for user 0: +329ms
10:37:31.155 OPENHU  SurfaceCallback: onSurfaceDestroyed
10:37:31.155 OPENHU  the surface went away 974ms after a touch - holding video focus so Android Auto keeps its keyboard up
    ... 108 s, keyboard up, operator typing. In this whole window:
        VIDEO Video Focus Notification = 0
        Asked by projected IME to detach = 0
        Decoder stopped / any decoder activity = 0
        ByeBye / disconnect / reconnecting overlay / unacked = 0 ...
10:39:18.779 OPENHU  AapProjectionActivity: onResume
10:39:19.663 OPENHU  frames are rendering but no keyframe has decoded ...
10:39:19.665 OPENHU  relaunched surface has no picture after 850ms - cycling video focus
10:39:20.383 OPENHU  VideoDecoder: keyframe reached the codec (8200 bytes)
10:39:20.394 OPENHU  VideoDecoder: keyframe decoded - the picture is repaired
```

**PASS:**
- Keyboard held **108 s** (10:37:30.6 → 10:39:18.8). Typing worked (operator). No banner (operator).
- Session still live at dismiss: **no** `ByeBye`, **no** `Self Mode disconnected`, **no** reconnecting overlay, **no** SSL re-handshake, **no** unacked-window stall anywhere in the capture. The only `CAR.WM doTearDown windows` lines (10:39:19.69/.74) are Gearhead's window teardown during the return focus cycle, not a session teardown.
- Return as R3: `onResume` → `picture is repaired` = **1615 ms**.
- After the return the session ran healthy for a further ~25 min in the same capture: **21** natural-GOP `picture is repaired` events (keyframes every ~69 s, 72–140 KB), **308** throughput windows, all `dropped=0`. A second brief cover at 11:04:03 reproduced the same `holding video focus` behaviour (11:04:04.056, "surface went away 1044ms after a touch"); capture was stopped mid-return.

What R6 produced that R1's shorter hold did not: nothing adverse. The phone tolerated a 108 s hold of video focus with the decoder stopped — bytes discarded on the invalid surface, acks continuing — exactly as the commit message predicted.

---

## R5d — R5 re-run, WiFi off, offline dummy-VPN route — PASS (added after the round on request)

Same candidate, same unit, later the same day. Two changes from R5: the phone's WiFi was **off** for the whole run, so Self Mode connected over the offline dummy-VPN path (`HomeFragment: Device is offline. Preparing Dummy VPN` → `DummyVpnService: tun established` → `:5277`) rather than the plain loopback; and the mock drive followed a real OSRM road route (La Maquila S.A.S → Marinilla, over the Autopista Medellín–Bogotá) rather than R5's synthetic straight line.

Prior attempts at this re-run (R5b/R5c, not written up) were **INCONCLUSIVE** — the dev head-unit server had been left down by round 2's teardown deviation, a mid-run WiFi toggle dropped the session, and two `mock_drive` processes were briefly competing for the GPS test provider. None of that was a code fault. R5d is the clean run once those were fixed: one capture, one mock-drive process, one uninterrupted hold.

- Capture span **12:06:49 → 12:17:12, 10.4 min continuous**. Throughput windows 12:06:52 → 12:17:08.
- **Zero** on every gate/health line: `frames are rendering but no keyframe`, `relaunched surface has no picture`, `sync_stall`, `Recreating projection view`, `onSurfaceDestroyed`, `holding video focus`, `decoderPermanentlyFailed`, `Requesting Keyframe`.
- **Zero** disconnects: no `ByeBye`, no `Unexpected disconnect`, no reconnecting overlay, **0** `SSL handshake complete` in the window — one session, stable start to finish.
- **124** throughput windows, **every one `dropped=0`**. `presented` fps **49–59** (51 fps ×16, 52 ×17, 59 ×25; two 49 fps windows).

The credible-picture gate does not misfire in steady state on the offline route either, and the coupled fixes cost nothing on a path neither was aimed at. `settings.xml` restored byte-identical, test providers removed.

---

## Anything the brief did not ask about

- **`First frame rendered` still fires on the gray P-frame on every rebuild path** — R3 (191 ms), R4 (×3), R6. It is now cosmetic: the credible-picture gate is what gates recovery, and it correctly treats that event as "not a picture." Worth knowing that the log line no longer means what it says on a warm rebuild.
- **The return keyframe is consistently 8200 bytes** on R2/R3/R4/R6 when it comes from the focus cycle (a forced IDR), versus 72–140 KB for the phone's natural-GOP keyframes in the same session. The cycle is pulling a genuine on-demand IDR, not waiting for the GOP.
- **The `holding video focus` gate fired cleanly on a second, unplanned cover** 25 minutes into an idle session (R6, 11:04) — the 3 s-of-a-touch window and the opaque/live-session checks still resolved correctly that far from session start.
- **`Decoder start aborted: the surface went away mid-configure`** appeared on the R2 and the second-R6 returns (10:22:19.743, 11:04:04.114) — the known surface race, handled (waited for the new surface, reconfigured clean, no stall). Same signature the post-beta1-latency round caught on Home cycles.
- **Gearhead's "car in motion" keyboard lock** (`maybeStartExternalKeyboard Keyboard is locked due to car being in motion`, R1) did not prevent `PhoneKeyboardActivity` launching. If a repro ever needs the phone keyboard *not* to appear, that lock is a lever, but it did not block anything here.
