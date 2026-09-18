# D-HP evidence, projection-raise round 2

Full verbose (`log-level=0`) `adb logcat -v time "OPENHU:V" "*:S"` captures from the D-HP (HP Slate
7 Plus, Android 4.2.2) stage, gzipped (raw total ~26MB, compressed ~1MB). All three cover the same
long-running app process/session unless noted.

- `dhp-l1-capture-1830-1836.log.gz` — L1 through the start of L4, ~18:30-18:36. Covers the initial
  connectivity false start, the L1 PASS (session reaching `projecting`, four lines, climbing to
  29-30fps), and the early L2/L3 checks.
- `dhp-l1-capture-2008-2012.log.gz` — resumed after a ~90 minute USB link drop and physical replug
  (same app pid before and after, session survived). Covers the `audio-queue-capacity=0` discovery:
  `AudioTrackWrapper.sampleHealth`'s `queued=` climbing 1060 → 1409 → 1381 → 1385 → 1920 across four
  consecutive 30s windows even through a `rebank` event that reset `depth` to 0.
- `dhp-l1-capture-audio-queue-fix-2012-2021.log.gz` — after resetting `audio-queue-capacity` to the
  default (50). Shows `queued` immediately dropping to single digits and two subsequent decoder-stall
  events (one during a touch-drag gesture, one with no touch input) shedding hundreds of stale audio
  frames cleanly (`dropped=1555`, `dropped=265`, `dropped=493`) instead of accumulating a backlog.
  Also carries the `Display stall ... Falling back to SurfaceView for this session` line.
- `dhp_audio_watch.txt` — a 5-minute scripted sample loop that straddled the USB drop; empty samples
  during the drop, kept for the timeline record rather than discarded.
- `l1-picture-maps-live.png` — screenshot of the live L1 session (Google Maps navigation, FPS/CPU/Temp
  overlay reading `FPS: 29 CPU: app 26% / sys 54%`).
- `l1-picture-after-touch-drag-stall.png` — screenshot taken during the touch-drag-triggered stall
  (`Re-centre` button visible from the pan gesture; overlay reading `sys 54%` at the moment of lag).

Cross-reference timestamps against `projection-raise-round2-results.md`'s Setup notes and Stage C
section, which quote the decisive lines directly.
