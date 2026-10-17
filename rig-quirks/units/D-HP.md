# D-HP quirks

The HP Slate 7 Plus (API 17). Moved verbatim from `TESTING-TEMPLATE.md` §7a on 2026-09-30. **Unless an entry names a unit, it was measured on D-HU.**


- **The stand-in hands-free record is reachable here, and this note used to say it was not.**
  `native-aa-wireless` round 2's R21 put this unit in Native AA and read
  `NativeAA: ACTIVELY LISTENING on Android Auto UUID` and `gets the stand-in HFP record, because it
  advertises no Hands-Free` on the same arming, then no refusal anywhere in the capture. So a second
  4.2.2 tablet publishes the record first time, and the Bluetooth half of Native AA runs on a unit
  with no WiFi Direct: `WifiLauncherNative.start()` calls `handshakeManager?.start()` outside its
  transport branch. What this unit still cannot do is the WiFi half, so the group create is refused
  (`P2P_UNSUPPORTED`) and no session can form. Brief it for the record and for anything Bluetooth,
  and D-SAM for anything that needs a network. The old note read the mode this unit is usually run
  in as a property of the code, which is what it was `projection-raise` round 2 established.
- **`run-as $PKG am start --user 0 -n .../SettingsActivity` segfaults this unit's `am` binary**,
  every time it was tried, without crashing the app. Try `am start` without `run-as` and without
  `--user 0`, or `monkey -p $PKG 1` to bring the app forward, before assuming the UI is unreachable.
- **Neither `svc wifi disable` nor `settings put global wifi_on 0` takes the radio down** on this
  Android 4.2.2 build: `dumpsys wifi` keeps reporting `"Wi-Fi is enabled"` with no observable state
  change. Any run needing a WiFi disconnect here needs a physical lever, or D-SAM instead.
- **The in-app log export lands at `/storage/emulated/0`**, not the legacy `/storage/sdcard0`,
  confirmed on this unit in `projection-raise` round 2 via `ACTION_EXPORT_LOG`.
- **Its USB connector is flaky.** The link vanished from `adb devices` once mid-round and came back
  on a physical replug with the app process and its session both intact, same pid either side.
- **The Settings screen cannot be reached by script, and four routes are closed.** `run-as am start`
  segfaults with and without `--user 0`; plain `am start` is refused because the activity is not
  exported; `monkey` plus `input tap` at the Settings button's dumped bounds produces no transition;
  and neither does the same mirrored for the `rotation="1"` against `mRotation=3` disagreement the
  dump shows. Touch delivery itself works, because the neighbouring WiFi button at dumped bounds
  fires `beginAutoConnect`. Any run needing this screen needs a hand. `projection-raise` round 3.
  **Round 4 did that hand-tap** (Settings, then the log Share button): both landed cleanly, produced
  a real system `ChooserActivity`, and the app stayed alive throughout — the screen and the Share
  flow both work fine once a human reaches them, the gap is purely in scripting the route there.
- **Host-side `logcat` capture has died silently here during long runs**, twice in one session, for
  two and a half and seven minutes, with the app's own pid unchanged either side. It cost that round
  a one-shot log line that had already rotated out of the device's ring buffer. On any long run,
  write the capture to a file from before the session starts and check the process is still alive at
  the end. **Round 4 hit this again** (twice in one ~19 minute run) — a 60 s-interval liveness poll
  that restarts the capture with `>>` append on death is sufficient to not lose anything; the one-shot
  line in question landed cleanly across one of the two gaps.
- **`svc wifi enable` can take ~25-30 s to actually bring the radio up on this Android 4.2.2 build.**
  `dumpsys wifi` read `"Wi-Fi is disabled"` for four consecutive 5 s checks after the command
  returned, then `"enabled"` on the fifth. Poll rather than assuming the command's own return means
  the radio is up; a run that immediately launches the app against a WiFi-dependent mode (Headunit
  Server discovery, for instance) will otherwise read as a connectivity failure that is really just
  impatience. `projection-raise` round 4.
