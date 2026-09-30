# Audio, media and the assistant

Focus latches, VLC, AudioFlinger, media keys, the audio sink, Gemini sessions. Moved verbatim from `TESTING-TEMPLATE.md` §7a on 2026-09-30. **Unless an entry names a unit, it was measured on D-HU.**

- **`playback-focus-self-defeating` is a latch the app writes, and it survives everything.** Two media
  channels closing within 5 s of a focus grab set it, and once set the app never takes system audio
  focus again, on this or any later session. Only re-picking the focus mode in the settings UI clears
  it from inside the app; from adb, write `false` with the app stopped. A whole round of focus runs
  measured nothing because it was already set: every decline read `learned` whatever the mode was.
  Read it back before and after any run that is about audio focus.
- **Driving VLC as the phone-side audio source takes three fixes that are not obvious.** It needs
  `appops set org.videolan.vlc MANAGE_EXTERNAL_STORAGE allow`, because that is an app-op and
  `pm grant` does not set it; a leftover playback item makes it reopen the old path whatever the new
  intent says, until `pm clear`, which then costs a first-run storage scan of about 15 s; and the
  activity to target is `org.videolan.vlc/.StartActivity`, not the player activity, which accepts the
  VIEW intent silently and never becomes the resumed activity. Launch with
  `am start -a android.intent.action.VIEW -d "file://<path>" -t audio/mpeg -n org.videolan.vlc/.StartActivity`.
  A keep-alive watchdog that restarts it on any non-PLAYING state will contaminate any run that
  deliberately pauses playback; kill the watchdog first.
- **A bonded A2DP speaker invalidates any run a person is listening to.** A paired "Magnetic
  Speaker" was holding `STREAM_MUSIC` on the head unit at the start of a listening round, so the
  audio a tester would have graded by ear was never coming out of the unit under test. Confirm
  `adb shell dumpsys audio` reports the route as `speaker(2)` before every run that uses a person as
  an instrument, and physically disconnect anything bonded that can take the stream.
- **What AudioFlinger thinks needs no root.** `dumpsys media.audio_flinger` and
  `dumpsys media.metrics` both work from the shell user on the rig's units, which is the only way to
  see an underrun below our own `AudioTrack`. The framework's per-session figure is the
  `MediaAnalyticsItem ... android.media.audiotrack.underrunframes=N` line at teardown: divide by the
  sample rate for seconds of real underrun, and prefer it over any counter of ours when the two
  disagree.
- **To drop the A2DP link, switch off the *phone's* Bluetooth instead.** It takes
  `A2dpSinkStateMachine` to `STATE_DISCONNECTED` while leaving an already-established Native AA TCP
  session on port 5288 untouched — the handshake's own RFCOMM socket is already closed by the time
  the WiFi handoff completes. Verify with `netstat` that the session survived.
- **The A2DP link comes and goes on its own schedule, and nothing visible controls it.** Three rounds,
  three different behaviours: round 6 it reconnected the instant the phone's Bluetooth came back;
  round 7 two cycles including a full 8 s off never returned `A2dpSinkStateMachine` to `Connected`;
  round 8 it refused to come up **at all** for ~15 minutes across the prescribed method and several
  substitutes (adapter enable, full disable/enable cycle, forcing playback to provoke an on-demand
  profile connect), with `A2dpSinkService` stuck at `Active Device = null` — then came up
  unprompted during unrelated setup, with no new technique applied.

  So: **confirm the link immediately before every link-dependent run** and never infer it from the
  last one. If it is down, do not spend the round trying to force it — no technique has ever been
  shown to work. Run the link-free runs, then re-check. Runs that never got a link are
  **INCONCLUSIVE**: rig flakiness, not a finding about the branch.

  ```bash
  adb shell dumpsys bluetooth_manager | grep -iE "a2dp|avrcp|Connected|Active Device"
  ```
- **Media keys alone do not open a fresh audio channel.** Focus is re-evaluated when the channel
  opens, not per track, so a run needing a fresh decision must restart the media app on the phone:
  ```bash
  adb -s <phone> shell am force-stop com.spotify.music
  adb -s <phone> shell monkey -p com.spotify.music -c android.intent.category.LAUNCHER 1
  ```
- **Media keys do not open or resume an AAP audio channel here. A tap on the projected media widget
  does.** Measured across a whole round: `KEYCODE_MEDIA_PLAY_PAUSE` sent to the phone
  (`adb -s <phone> shell input keyevent`), relayed through the head unit's `CommManager`
  (`adb -s <hu> shell input keyevent 85`), and a genuinely fresh Spotify relaunch all failed to reopen
  a channel once it had closed. What works every time is tapping Android Auto's own play/pause
  control, which is drawn *into the projected video* rather than being a native view, so the tap goes
  back to the phone over the app's own touch channel: `adb -s <hu> shell input tap 272 657` on this
  rig's 1440×720 layout. Plan any run that needs music playing around the tap, not the key. One side
  effect to expect: the first Play tap of a cold session flaps `PLAYING`/`PAUSED` every 200-800 ms for
  several seconds before settling, with `MediaSession: Processing transport control action =
  KEYCODE_MEDIA_PAUSE` repeating, so take timings after it settles.
- **This rig has no speaker and no 3.5 mm output.** Nothing can be checked by ear, which suits
  §0's rule that a verdict must never depend on a human being present, but it means a brief step
  phrased "confirm audio audible" has no way to run. Every audio brief must name its scriptable proxy
  up front; the ones already used are the phone's own `dumpsys media_session` playback state and the
  app's `Media Start Request AUDIO` and `AapMediaPlayback` status lines. A round that discovers this
  mid-run loses the corroboration on every step that assumed it.
- **`pm revoke android.permission.RECORD_AUDIO` does not work on this ROM**, which answers with
  `SecurityException: ... is not a changeable permission type`. `appops set <pkg> RECORD_AUDIO ignore`
  (and `allow` to restore) does work. **They are not the same test.** The app-op denial leaves the
  runtime grant at `granted=true`, so any code that reads the *permission* rather than the *op* still
  sees it held, and a run that needs a genuine revoke cannot be done here at all. Say which of the two
  a run used. The pre-round default on this rig is `foreground`, not `allow`; restore it to that.
- **`KEYCODE_BACK` does not cancel an in-flight assistant session.** Sent 1.5 s into one, the session
  ran its full natural length regardless. What does cancel it is a second trigger sent shortly after
  the first. Prove the cut from the session's own uplink summary rather than from the clock: an
  interrupted session measured 1.09 s and 10 frames against a natural 3 to 10 s and 23 to 82 frames
  elsewhere in the same round.
- **One trigger can produce several assistant sessions.** Gemini is multi-turn, so a single broadcast
  was observed producing two or three `Voice Session Notification: START`/`STOP` pairs with no
  further action. Any brief that says "four assistant sessions" means four START/STOP pairs; count
  sessions, never triggers, and do not assume a 1:1 mapping holds.
- **`enable-audio-sink` gates the media and speech channels, and a `false` left by an earlier round
  looks like a broken build.** With it off the app logs "Audio sink is off in Settings. Skipping the
  media and speech audio channels - the phone will not send audio and this is not a fault", and the
  only channel set up is the always-on Audio2 (System Sounds) one, so no `AudioDecoder.start:` ever
  appears for music. Read it back `true` before any run that grades an audio channel.
