# ultrawide-touch-alignment — round 1 results

**Candidate:** `fix/video-fit-and-ultrawide-touch` @ `371477b61dbd1fafb42b6c14a0a938bfcdb83293`
**Baseline:** `main` @ `6cd703995f68e04740ef5c2bff1bab05dc9e0c3a`
**APK md5:** candidate `55e5469502a2c68dce3a9f7348cad012` / baseline `ba0037d902039adece0931868841a70d`
**Unit:** UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14, panel 1440x720 (aspect 2.0)
**Date:** 2026-09-07

## Setup notes

**This round resumes one left mid-flight from an earlier session.** The baseline APK had already
been built successfully (`BUILD SUCCESSFUL in 4m 8s`) and staged in `round-ultrawide-touch-r1/`,
but its own `run_unit_tests.sh` output was truncated mid-task with no final `BUILD SUCCESSFUL` /
`BUILD FAILED` line — consistent with the reported PC thermal interruption. CPU temps were nominal
(58°C) at the start of this session; the candidate's build+test cycle later peaked at ~84°C
(`sensors`, `high=87°C`), below the throttle point, and both gates completed cleanly this time. No
`dmesg` thermal/throttle entries were present. Re-ran the baseline's unit tests from scratch rather
than trust the truncated log.

`hur-wifi-test-scripts/` inventory: used `build_hur.sh` and `run_unit_tests.sh` for both arms
(builds whatever is checked out in the real repo checkout), and `set_hu_prefs.sh` (multi-key,
no-relaunch settings writer) for every settings change. No new script needed.

Both APKs installed with `adb install -r`, md5-verified against the host build before every arm's
first run. `ACTION_QUERY_STATE`'s `commit` field (`6cd703995f68` baseline, `371477b61dbd`
candidate) additionally confirmed the live APK before every run, per the brief.

**`ACTION_LOG_MARKER` quoting bug — mine, not the app's.** Sending
`adb -s $HU shell am broadcast ... --es text "R1 baseline 720p start" ...` loses its quoting when
adb rejoins a multi-arg shell invocation on-device: the word after the first space ("baseline")
gets parsed by `am broadcast` as a bare trailing `PACKAGE` argument, producing `pkg=baseline` and
matching 0 receivers — confirmed from `ActivityManager`'s own `for 0 receivers` line, and the
receiver's `ACTION_LOG_MARKER` filter entry is present and correctly declared. Did not affect the
round: `ACTION_QUERY_STATE` (no embedded spaces) landed correctly every time and gave the real
commit fingerprint for every run, so runs were delimited by wall-clock timestamp and log content
instead. Fix for next time: wrap the whole remote command in one quoted string, e.g.
`adb shell "am broadcast ... --es text 'my text' ..."`.

R1's capture legitimately contains two `Handshake: SSL handshake complete` events, because the
brief's own R1 step asks for a second (1080p) capture on the same baseline arm without a separate
build. Noted here so it is not misread against the discard rule. R2 through R6 each show exactly
one `createGroup SUCCESS` and one `Handshake: SSL handshake complete` — clean single-session runs.

`ACTION_GET_SETTINGS` during R2 confirmed neither `pixel-aspect-ratio-e4` nor `video-fit-mode` were
present as stored overrides, so the auto-derive and default-FILL code paths were genuinely
exercised rather than short-circuited by a leftover value from an earlier thread.

Phone Bluetooth needed one manual `svc bluetooth enable` at the very start of R1 (it was off from a
previous thread); it stayed up and re-associated within ~10 s on every subsequent force-stop +
settings-write + relaunch cycle (R2 through R6), so no further radio toggling was needed. HU and
phone were already bonded going in.

`settings.xml` restored to its pre-round backup at the end of the round; diff against the backup is
empty. `wifi-connection-mode=3` (Native AA) was already the pre-existing value from a prior thread
and was not itself part of this round's changes.

## R0 — build gate

**PASS**

- Candidate `testGithubDebugUnitTest`: **1445 / 0** (skipped 0, failures 0, errors 0)
- Baseline `testGithubDebugUnitTest`: **1414 / 0** (skipped 0, failures 0, errors 0)
- Both exactly match the brief's expected counts.

## R1 — baseline at 720p

**PASS**

- Settings written: `resolutionId=2`, `log-level=0` (verbose), on baseline APK.
- Radio state: HU Bluetooth already on; phone Bluetooth brought up via `svc bluetooth enable`
  (was off) — confirmed `state: ON` before launch.
- Discard-rule check: clean for a single connect; the capture holds two connects (720p then 1080p)
  by the brief's own design, see Setup notes — not contamination.
- Decisive log lines, 720p leg:
  ```
  11:21:28.188 [ServiceDiscovery] NegotiatedResolution is: 1280x720
  11:21:28.195 [ServiceDiscovery] Margins are: 0x0
  11:21:29.160 [UI_DEBUG] Normal Scale. scaleX: 1.0, scaleY: 1.125
  ```
- Decisive log lines, 1080p leg (captured for R4's comparison, same arm, same capture):
  ```
  11:23:51.993 [ServiceDiscovery] NegotiatedResolution is: 1920x1080
  11:23:52.000 [ServiceDiscovery] Margins are: 480x360
  11:23:52.918 [UI_DEBUG] Normal Scale. scaleX: 1.3333334, scaleY: 1.5
  ```
- Measurement: `scaleY: 1.125` exactly, matching the brief's predicted value for this panel. The
  fault arms on this rig precisely as described in the findings doc.

## R2 — candidate at 720p

**PASS**

- Settings written: `resolutionId=2`; `video-fit-mode` and `pixel-aspect-ratio-e4` both absent
  (confirmed via `ACTION_GET_SETTINGS`, neither key present).
- Decisive log lines:
  ```
  11:25:49.383 [ServiceDiscovery] NegotiatedResolution is: 1280x720
  11:25:49.389 [ServiceDiscovery] Margins are: 0x0
  11:25:49.390 [ServiceDiscovery] PixelAspectRatioE4 is: 11250 (10000 = square)
  11:25:50.216 [UI_DEBUG] Normal Scale. scaleX: 1.0, scaleY: 1.0
  ```
- Both halves of the fix confirmed independently: the scale fix (`scaleY` restored to 1.0) and the
  PAR derivation (`11250`, exactly the value the findings doc calculated for this panel) both land.

## R3 — touch alignment (the point of the round)

**PASS**

Candidate, still 720p, FILL. Five taps via `adb shell input tap`, read back from
`[UI_DEBUG] Touch map:`:

| tap (raw) | video (measured) | video (expected) |
|---|---|---|
| 40,40 | 36,40 | ~36,40 |
| 1400,40 | 1244,40 | ~1244,40 |
| 40,680 | 36,680 | ~36,680 |
| 1400,680 | 1244,680 | ~1244,680 |
| 720,360 | 640,360 | ~640,360 |

Every point matches the predicted table exactly. **y comes back equal to raw y in all five cases**,
and x scales by exactly 1280/1440 = 0.8889 throughout. This is the failure mode the round exists to
catch, and it does not occur on the candidate.

## R4 — regression guard at 1080p

**PASS**

- Settings written: `resolutionId=3`, candidate APK (no other keys touched).
- Decisive log lines:
  ```
  11:27:11.689 [ServiceDiscovery] NegotiatedResolution is: 1920x1080
  11:27:11.696 [ServiceDiscovery] Margins are: 480x360
  11:27:11.698 [ServiceDiscovery] PixelAspectRatioE4 is: 10000 (10000 = square)
  11:27:12.610 [UI_DEBUG] Normal Scale. scaleX: 1.3333334, scaleY: 1.5
  ```
- Margins (`480x360`) and PAR (`10000`) both match the brief's expectation, and the scale line is
  **bit-identical** to R1's 1080p capture (`1.3333334` / `1.5`). No regression at the rig's normal
  mode.

## R5 — CONTAIN

**PASS**

- Settings written: `resolutionId=2`, `video-fit-mode=1`.
- Decisive log line: `11:27:58.360 [UI_DEBUG] Normal Scale. scaleX: 0.8888889, scaleY: 1.0` —
  matches the ~0.889/1.0 prediction exactly.
- Taps: `raw=40,40 -> video=0,40`, `raw=1400,40 -> video=1280,40`, `raw=40,680 -> video=0,680`,
  `raw=1400,680 -> video=1280,680`, `raw=720,360 -> video=640,360`, all tagged `fit=CONTAIN`.
  With scaleX 0.889 the picture spans screen x=[80,1360] (pillarbox 80px each side); the two edge
  taps at x=40 and x=1400 fall inside the bars and correctly clamp to the picture's left/right
  edge (video x=0 / 1280) rather than reporting a coordinate outside the visible picture; the
  center tap maps proportionally. No distortion.

## R6 — COVER

**PASS**

- Settings written: `video-fit-mode=2` (720p carried over from R5).
- Decisive log line: `11:29:05.013 [UI_DEBUG] Normal Scale. scaleX: 1.0, scaleY: 1.125` — matches
  exactly.
- Taps: `raw=40,40 -> video=36,76`, `raw=1400,40 -> video=1244,76`, `raw=40,680 -> video=36,644`,
  `raw=1400,680 -> video=1244,644`, `raw=720,360 -> video=640,360`, all tagged `fit=COVER`. With
  scaleY 1.125 the picture is enlarged to height 810 and centred, cropping 45px top and bottom
  (`(810-720)/2`); every measured y matches `(raw_y + 45) / 1.125` exactly (e.g. `(40+45)/1.125 =
  76`, `(680+45)/1.125 = 644`). x is unaffected (scaleX 1.0). No distortion, crop accounted for
  correctly in the touch math.

## Anything the brief did not ask about

R6's numbers are worth flagging beyond a bare PASS: the findings doc's §2 "Gaps neither the branch
nor main closes" warns generally that every renderer scales about the view centre while the margin
Android Auto honours is an edge crop, and that this disagreement is *not* fixed by the branch. That
gap is real for the margin-vs-crop case it describes, but R6 shows the **fit-mode crop specifically**
(as opposed to a negotiated-margin crop) is threaded correctly end-to-end here: the mapper's
`fit=COVER` branch already knows about and compensates for the same 45px crop the view is applying.
Worth keeping in mind as a "this specific corner is fine" data point rather than assuming the
general gap applies to every crop in the app.

The two-log-lines-per-handshake pattern (`AapSslContext.performHandshake` at I-level and
`AapTransport.handshake` at D-level, both containing "SSL handshake complete" but only the second
saying `Handshake:`) is worth remembering for future discard-rule greps on this branch — a plain
`grep -c "SSL handshake complete"` double-counts every session; use the `Handshake:`-prefixed line
specifically.
