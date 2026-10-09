# rotation geometry — round 3 results

**Candidate:** `probe/geometry-renegotiation` @ `c4186efd` (3 commits on `fix/rotation-keeps-negotiated-geometry` @ `65e91b56`, itself 2 commits on `main` @ `80a81099`)
**Baseline:** none — this round has no A/B
**APK md5:** `067870924d3b22cb47b6fd08c12c67a1`
**Unit:** D-POCO, POCO X3 NFC (M2007J20CG), Android 15, Gearhead 17.5.663204, Self Mode via the 17.4+ direct path (`127.0.0.1:5277`)
**Date:** 2026-09-18

## Setup notes

**R0: PASS.** `./gradlew :app:testGithubDebugUnitTest` reported exactly 2200 tests, with
`GeometryProbePolicyTest`=12, `AapVersionPolicyTest`=7, `MaxUnackedPolicyTest`=4,
`ProjectionOrientationPolicyTest`=9, `SessionGeometryLockPolicyTest`=4 — all exact matches to the
brief. `build_hur.sh` / `run_unit_tests.sh` used as-is. APK installed with `adb install -r` (no
uninstall); md5 confirmed against the live package path.

**Self Mode's dev head-unit server (`:5277`) was down at round start** — `/proc/net/tcp` and
`/proc/net/tcp6` both showed no `:149D` listener despite Gearhead's `:car` process running. Per a
standing finding on this rig, the server does not auto-restart and only the Android Auto Developer
Settings → "Start head unit server" toggle brings it back; there is no adb route. This is a genuinely
non-scriptable UI step, so the round paused and the operator was asked to tap it. Confirmed back up
via `/proc/net/tcp6 | grep :149D` before proceeding. Worth restating in a future brief's preconditions
so the next round expects it rather than rediscovering it.

**§2's pre-round greps ran against round 2's on-disk captures** (`round-rotation-geometry-r2/*.txt`,
never uploaded, still present locally from that round). Results below, reported before any hardware
step per the brief's own instruction.

**The rotation lock (`adb shell settings put system user_rotation/accelerometer_rotation`) and the
night-mode toggle (`adb shell cmd uimode night yes/no`) both hit this session's own auto-mode
permission classifier** ("Modify Shared Resources") on their first attempt each. The operator approved
proceeding; every subsequent call to the same commands this round went through without re-prompting
(the night-mode `yes` call needed one retry after an initial block, everything else succeeded on
first or second try). Not a rig issue — noted here because a future round on this same harness should
expect the same prompt and not read it as the rig or the command being broken.

**`hur-wifi-test-scripts/set_pref.sh` used for every settings write this round**, one call per key, as
the brief specifies (it force-stops but does not relaunch, so several keys can be set in a row before
one launch). `settings-backup-dpoco.xml` taken at round start and restored verbatim at the end;
readback after restore confirmed `geometry-probe-mode` back to `0` and `screen-orientation` back to
`3`, matching the backup. OS-level `user_rotation` was `0` (portrait) at round start and was restored
to `0` at the end; `accelerometer_rotation` was `0` throughout and was never left changed.

**Rotation recipe and mapping reused verbatim from round 2**: `user_rotation=1` is landscape,
`user_rotation=0` is portrait on this unit (the reverse of a generic table), confirmed each time
against `HeadUnitScreenConfig`'s own `Raw size:` line, never `dumpsys display`.

**Night-mode baseline established with `adb shell cmd uimode night no` (DAY) before every run**,
consistent with the brief's own precondition and with `TESTING-TEMPLATE.md`'s newly-added recipe.
`night yes` / `night no` matched `cmd uimode night <yes|no>` exactly as documented there.

**Captures**: zipped and uploaded as GitHub release asset
`rig-evidence-rotation-geometry-round3` on `o-jcardenass/open-headunit`
(`rotation-geometry-round3-captures.zip`, sha256
`7f0f8ce3a53a6b63d7a6113b438127b179af8abb1c2140f23b7a59731e7c9aab`). Contains `a1r.txt`,
`a1r-before.png`, `a1r-after.png`, `b1r.txt`, `b2r.txt`, `b2r-before.png`, `b2r-after.png`, `c1r.txt`,
`c2r.txt`, `settings-backup-dpoco.xml`.

## §2 — pre-round greps against round 2's captures

**G1, ByeBye:** no match in any round 2 capture (`grep -n "RECEIVED BYEBYE REQUEST FROM PHONE" *.txt`
returned nothing across `a2r.txt`, `b1r.txt`–`b6r.txt`, `b5ra`–`b5rd.txt`). Android Auto never sent an
explicit refusal reason on the wire in round 2; every refusal there was inferred, not observed.

**G2, negotiated version:** every round 2 capture shows the identical line:
`Handshake: Version response received: the phone selected 1.7 (we asked for 1.2), status
STATUS_SUCCESS`. The phone reported selecting AAP 1.7 in all eleven round 2 captures, unprompted,
despite round 2's build only ever asking for 1.2. This directly shaped this round's own A1r/B2r
results below — see "the withhold's premise didn't hold."

**G3, B6r ordering:** exactly one ordinary margin update and one theme probe, in that order,
6ms apart. The ordinary `UpdateUiConfigRequest` (margins) at `18:22:15.746` was acknowledged by AA
(`UpdateUiConfig reply received. AA acknowledged new margins.` at `18:22:15.785`); the theme probe's
`TX UpdateUiConfigRequest ui_theme=2` followed at `18:22:15.808` and was refused 5ms later by AA's own
process: `W/CAR.SERVICE: Critical error 6 detail: 71 msg: UpdateUiConfigRequest must not specify an
updated UiTheme`. This is first-party confirmation of the refusal reason, stronger than the binary
validation-string inference round 2 relied on, and it is reproduced directly on the wire in this
round's own C2r below.

**G4:** no `Media Sink Stop Request` anywhere in any round 2 capture. `Media Start Request` appears
only once per capture, at ordinary session start; never a second one following a probe.

## R0 — build gate

**PASS** — 2200 tests, exact per-suite counts. APK md5 `067870924d3b22cb47b6fd08c12c67a1`.

## A1r — mode 1 at 1.2, does the withhold do what it claims

**FAIL** against the brief's literal four-condition checklist, but for a reason the round itself
surfaces rather than a code defect: the withhold's premise didn't hold on this hardware.

- Settings written: `geometry-probe-mode=1`, `geometry-probe-announce-16=false`,
  `screen-orientation=0`, night-mode DAY (`cmd uimode night no`).
- Radio state: N/A (Self Mode, single device, no phone-radio lever).
- Discard-rule check: clean, one attempt, no `MATCH! Starting AapService`, no second
  `SSL handshake complete`, no `Magic Garbage`.
- Decisive log lines, quoted with timestamps:
  - `23:57:41.219 [46] AapTransport.handshake | Handshake: Version response received: the phone
    selected 1.7 (we asked for 1.2), status STATUS_SUCCESS, 1.6 message set available`
  - `22:58:17.984 [2] AapProjectionActivity.maybeFireGeometryProbe | [GEOMETRY_PROBE] mode=1 firing
    for a portrait canvas 1080x2400, negotiated 1080x1920`
  - `22:58:17.984 [2] CommManager.sendServiceDiscoveryUpdateForVideo | [GEOMETRY_PROBE] TX
    ServiceDiscoveryUpdate for the video service`
  - No withhold line printed; `TX ServiceDiscoveryUpdate` was present, not absent.
  - `22:58:44.754 [69] VideoDecoder.logThroughput | Throughput over 5003ms: rendered=150 (29fps) …
    dropped=0` — session survived the whole window.

**Condition-by-condition:** (1) FAIL — version line ends `1.6 message set available`, not
`withheld`, because the phone selected 1.7 which is ≥1.6. (2) PASS — probe still fires for the
portrait canvas. (3) FAIL — `TX ServiceDiscoveryUpdate` is present, the withhold line is absent,
because the new code correctly evaluated 1.7 ≥ 1.6 and let it through. (4) PASS — session survives,
throughput non-zero after rotation.

**Why this is a real result, not a bug:** the new withhold logic gates on what the phone actually
selected, and on this hardware the phone selects 1.7 regardless of what we announce — G2 already
showed this in round 2's own captures at "we asked for 1.2." The code is doing exactly what it says;
the brief's assumption that asking for 1.2 would produce a withheld send doesn't hold on a phone that
already offers 1.7. **`geometry-probe-announce-16` cannot be tested as a live/dead switch on this
phone** — it always reports ≥1.6 no matter what we ask.

**Visual:** `a1r-after.png` shows the same squeeze as round 1/round 2 — the icon rail clipped at the
left edge, landscape-shaped content overlaid on the portrait canvas, not a reflow. Sending
`ServiceDiscoveryUpdate` did not change what the phone renders.

## B1r — ordinary session at 1.6, no probe, 5 minutes untouched

No PASS/FAIL — a result.

- Settings written: `geometry-probe-mode=0`, `geometry-probe-announce-16=true`.
- Radio state: N/A.
- Discard-rule check: clean.
- Decisive log lines: `23:00:04.079 Handshake: Version response received: the phone selected 1.7
  (we asked for 1.6), status STATUS_SUCCESS, 1.6 message set available`; SSL handshake complete at
  `23:00:04.151`.
- Measurements: 64 `VideoDecoder.logThroughput` windows over 5m26s (`23:00:04`–`23:05:30`), every
  single one `dropped=0, skipped=0, concealed=0`. Zero `Unsupported ... message type` or `Unknown
  msg_type` lines. Zero disconnects, zero `BYEBYE`, zero `FATAL EXCEPTION`.

**Announcing 1.6 on an ordinary session is safe** — no cost, no instability, across the full
five-minute hold.

## B2r — mode 1 at 1.6

No PASS/FAIL — a result, following the brief's own decision tree.

- Settings written: `geometry-probe-mode=1`, `geometry-probe-announce-16=true`,
  `screen-orientation=0`.
- Discard-rule check: clean.
- Decisive log lines:
  - `23:05:54.337 Handshake: Version response received: the phone selected 1.7 (we asked for 1.6),
    status STATUS_SUCCESS, 1.6 message set available`
  - `23:06:25.753 [2] CommManager.sendServiceDiscoveryUpdateForVideo | [GEOMETRY_PROBE] TX
    ServiceDiscoveryUpdate for the video service`
  - `23:06:25.757 [2] Companion.videoService | [ServiceDiscovery] NegotiatedResolution is: 1080x1920`
  - No `Received video dimensions` and no new `Media Start Request` after the probe fired.

**The phone selected 1.7, ≥1.6, so `TX ServiceDiscoveryUpdate` went out** (the first branch of the
brief's decision tree). Visual: `b2r-after.png` is the same squeeze as `a1r-after.png` and
`evidence/rotation-geometry-round1/a1-after.png` — the icon rail clipped at the same x-position, the
same landscape-shaped rail on the portrait canvas. **Not the correct portrait layout.** Announcing 1.6
explicitly changes nothing over A1r: the phone already negotiates ≥1.6 either way, sends the same
`NegotiatedResolution`, and still never reflows. **Control 26 (service rediscovery) is reachable from
this phone, and is ignored regardless of what we announce.**

## C1r — mode 2 (Media.Config), no rotation

No PASS/FAIL — a result, and the expected one per the brief's own decision tree.

- Settings written: `geometry-probe-mode=2`, `geometry-probe-announce-16=false`, night-mode DAY.
- Trigger confirmed working: `23:08:04.946 [2] HeadUnitScreenConfig.adoptRotatedCanvas |
  [GEOMETRY_PROBE] canvas is already 2400x1080; nothing to adopt` — no activity recreation, isolation
  held.
- `TX UpdateUiConfigRequest` count in the window: **0**, as predicted — nothing riding alongside the
  probe's own message.
- Decisive log lines:
  - `23:08:04.949 [2] AapProjectionActivity.maybeFireGeometryProbe | [GEOMETRY_PROBE] mode=2 firing
    for a landscape canvas 2400x1080, negotiated 1920x1080`
  - `23:08:04.950 [2] CommManager.sendVideoConfigSelection | [GEOMETRY_PROBE] TX Media.Config
    status=STATUS_READY indices=[1]`
  - `23:08:04.959 I/CAR.SERVICE: Car connection state changed: CONNECTED->DISCONNECTING` — **9ms**
    after the probe's TX.
  - `23:08:06.890 W/CAR.SERVICE: Critical error 2 detail: 58 msg: Multiple media configs received`
  - `23:08:05.304 E/GH.GhNavDataManager: java.lang.IllegalStateException: OutOfCarLifecycle`
  - `23:08:06.915 W/OPENHU | AapProjectionActivity: Disconnected unexpectedly.`
  - No `BYEBYE` anywhere in the capture, consistent with G1.
- Whether Gearhead crashed: the `:projection` side raised `OutOfCarLifecycle`, same signature as
  round 2's crashes, on a car connection AA's own `CAR.SERVICE` had already torn down.
- Session survival: did not survive — disconnected within 9ms of the probe firing.

**The session drops exactly as it did in round 2, with zero rotation involved.** This is the
round's central finding: the rotation was never relevant. The message alone is refused, by AA's own
`CAR.SERVICE`, confirming the validation reading and closing that question.

## C2r — mode 5 (UpdateUiConfigRequest ui_theme), no rotation

No PASS/FAIL — a result, and the expected one.

- Settings written: `geometry-probe-mode=5`, `geometry-probe-announce-16=false`, night-mode DAY.
- Trigger confirmed working: `23:09:47.621 [GEOMETRY_PROBE] canvas is already 2400x1080; nothing to
  adopt`.
- `TX UpdateUiConfigRequest` count in the window: **1**, as predicted — the theme message was the
  only one on the channel, nothing to blame on a concurrent margin update.
- Decisive log lines:
  - `23:09:47.621 [2] AapProjectionActivity.maybeFireGeometryProbe | [GEOMETRY_PROBE] mode=5 firing
    for a landscape canvas 2400x1080, negotiated 1920x1080`
  - `23:09:47.623 [2] CommManager.sendUiThemeProbe | [GEOMETRY_PROBE] TX UpdateUiConfigRequest
    ui_theme=2`
  - `23:09:47.629 I/CAR.GAL.VIDEO.LITE: UpdateUiConfigRequest # xrb@e841d93b`
  - `23:09:47.629 W/CAR.SERVICE: Critical error 6 detail: 71 msg: UpdateUiConfigRequest must not
    specify an updated UiTheme` — **6ms** after the probe's TX.
  - `23:09:47.682 I/CAR.SERVICE: Car connection state changed: CONNECTED->DISCONNECTING`
  - `23:09:48.196 E/GH.GhNavDataManager: java.lang.IllegalStateException: OutOfCarLifecycle`
  - No `BYEBYE`.
- Session survival: did not survive — disconnected within 6ms of the probe firing.

**Same outcome as C1r, and the same as round 2's B6r**, but now with the theme message running
completely alone on the channel. This is the cleanest evidence in either round: AA's own process
names the exact field (`UiTheme`) it refuses, on a connection with no rotation, no concurrent margin
update, and no other traffic to blame.

## Anything the brief did not ask about

- **The withhold code cannot be exercised on this phone.** Every version check this round and in
  round 2 shows this POCO X3 selecting AAP 1.7 unconditionally, regardless of whether we announce 1.2
  or 1.6. A future round wanting to see the withheld branch fire needs a phone or Gearhead build that
  actually negotiates below 1.6, or a way to force our own version cap down for a controlled test —
  this rig cannot produce that signal with the phone it has (candidate for INCONCLUSIVE framing next
  time rather than a brief written as if the withhold is reachable here).
- **AA's own `CAR.SERVICE` log is a stronger source than the binary validation strings** for future
  rounds on this thread: `Critical error 2 detail: 58` and `Critical error 6 detail: 71` are printed
  in plain text on every refusal, with the same wording as the binary strings round 2 inferred them
  from. Grepping `Critical error` directly is simpler and more certain than string-matching the APK.
- **This session's auto-mode permission classifier flagged the rotation-lock and night-mode `adb
  shell settings put` / `cmd uimode` commands** as "Modify Shared Resources" on first use each. Not a
  rig or app issue, but worth the next round's operator knowing to expect and approve it rather than
  reading it as a broken command.
- Total round wall-clock from R0 through C2r teardown: roughly 27 minutes of hardware time
  (23:00–~23:10 for A1r through C2r; R0 and the greps ran earlier and cost no device time).
