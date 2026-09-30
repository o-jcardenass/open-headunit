# Projection, rotation and the banner

Proving projection, the status pill, rotation and night mode, fault injection, the connection-issue banner. Moved verbatim from `TESTING-TEMPLATE.md` §7a on 2026-09-30. **Unless an entry names a unit, it was measured on D-HU.**

- **A run that denies `SYSTEM_ALERT_WINDOW` must give it back in the same run.** Without the
  permission to draw over other apps, a session the phone starts by itself reaches the handshake and
  then hangs: the projection screen is raised by a full-screen-intent notification that Android 14
  demotes, and until somebody taps it nothing reads the socket. `appops` is not in `settings.xml`, so
  a settings restore does not undo it. One round denied it in its fourth run and read the next six
  sessions as a general defect in the candidate.
- **Projection is proved by four lines, and `TransportStarted` is not one of them.** It is a state
  name the app never logs, so a grep for it reports a perfectly healthy session as never having
  projected. Use `Service Discovery Response`, `Channel Open Response`,
  `Media Sink Setup Request: N on channel VIDEO` and `Throughput over …ms: rendered=`.
- **The status pill's own line says whether the projection was raised and by whom.**
  `status pill step: STARTING_PROJECTION` means the SSL handshake finished, nothing more. The suffix
  `(not shown, the overlay owns the screen)` means an auto-connect attempt was in flight, which
  raises the screen itself; a bare `STARTING_PROJECTION` with no `AapProjectionActivity.onCreate`
  after it is a session with nothing reading it.
- **A configuration change without a rotation is a night-mode toggle.** `adb shell cmd uimode night
  yes` (and `no` to restore) delivers `onConfigurationChanged` to the projection activity, because
  `uiMode` is in its manifest `configChanges` while `density` is not, so `wm density` recreates the
  activity instead and is not a substitute. The value of this is isolation: with no rotation there is
  no surface callback, so `reannounceMargins()` never runs and nothing of the app's own goes out on
  the video channel alongside whatever is under test. Set `night-mode` to `1` (DAY) first, because
  `0` is AUTO and `4` is LIGHT_SENSOR and either can put a sensor message on the wire mid-window.
  Confirm the trigger landed by finding the configuration-change work in the log rather than assuming
  it; if the activity was recreated instead, the ROM is not honouring `configChanges` and the run is
  UNTESTABLE. First used in `rotation-geometry-round3`.
- **D-HU is not rotated, on the operator's instruction**, so any round whose lever needs a genuine
  canvas flip is Self Mode on D-POCO only. Say UNTESTABLE rather than carrying a two-device arm that
  will be skipped.
- **A projection surface is not torn down by `KEYCODE_HOME` on this unit.** Twelve scripted cycles in
  the video-black round 1 — two builds, TEXTURE and GLES, holds from 3 s to 120 s — never produced a
  single `Decoder stopped: surfaceDestroyed`, and video throughput ran uninterrupted at 29-50 fps
  straight through a 120 s hold. Any run whose subject is the surface lifecycle, decoder restart or
  activity backgrounding must **verify the teardown actually happened** before measuring anything
  downstream of it, and must not assume a Home press provides one. Note also that
  `AapBroadcastReceiver` relaunches `AapProjectionActivity` with `FLAG_ACTIVITY_NEW_TASK` when the
  phone re-runs media-sink setup on the video channel, so **the app can return itself to the
  foreground** with no command from the rig.
- **Video fault injection does nothing at its default rate.** `debug-video-fault-injection` selects
  the mode, but `debug-video-fault-rate` defaults to 300 (one in three hundred candidate fragments),
  and at 720p a five-minute capture offers only about thirty candidates for a mode like
  `DROP_MIDDLE_FRAGMENT`. The media-gap round 1's injection run came back INCONCLUSIVE having injected
  nothing at all. A brief that asks for injection **must set the rate explicitly and state the
  expected number of injections**; if that number is not comfortably above one, the run is not worth
  scheduling. Candidate scarcity, not the rate alone, is the binding constraint.
- **Seeding a `connection-issue-*` stamp means clearing `connection-issue-dismissed-at` too.** The
  banner compares the two directly, and the seed constants briefs use (`1755800000000` and its
  neighbours) are *older* than any real on-device clock reading. So a dismissal left behind by an
  earlier run in the same session silently suppresses a stamp seeded afterwards, and the run reads
  as "no banner" when the banner logic is working exactly as designed. This cost a real false
  negative once. Delete the key alongside every seed unless the run is specifically about dismissal.
- **The banner is refreshed on `onResume()` and nowhere else.** Nothing re-checks while the app stays
  foregrounded, so a condition that raises mid-session does not appear on a screen that is already
  up. Any step phrased as "let the condition raise by itself and confirm the banner" therefore means
  force-stop and relaunch, whatever else it says. Two runs in one round have needed this.
