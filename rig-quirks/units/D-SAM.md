# D-SAM quirks

The SM-T230 tablet (API 19). Moved verbatim from `TESTING-TEMPLATE.md` §7a on 2026-09-30. **Unless an entry names a unit, it was measured on D-HU.**


Every one of these was measured across `t230-native-aa-bringup` rounds 1 to 4.

- **No `su`, and no `sed`, `awk`, `busybox` or `toybox`** (`cp`, `cat`, `grep`, `mv` exist). Use
  `set_pref.sh`, the `run-as` script built for unrooted units, never `set_hu_pref.sh`, which assumes
  root and defaults to D-HU. `set_pref.sh`'s pushed on-device helper still fails here with
  `sed: not found`: the method that works is to edit a local copy of `shared_prefs/settings.xml`
  with `python3` on the host, push the whole file, then
  `run-as $PKG cp <pushed file> shared_prefs/settings.xml`. A `sed`-free variant of `set_pref.sh`
  was asked for in round 1 and has not been written.
- **The USB port is data-only and does not charge it.** `dumpsys battery` reads `USB powered: true,
  AC powered: false`, and the battery sat at 9% through all four rounds, so every conclusion from
  them carries that as an uncontrolled variable. Charge it from a separate supply before any round
  that measures the radio.
- **Mount or hold it in its native portrait position**, with `screen-orientation=2` (`LANDSCAPE`).
  Neither of the app's two fixed-landscape settings gives a correct display on a landscape mount;
  `3` (`LANDSCAPE_REVERSE`) is visibly worse, tested live and reverted. This is an installation
  constraint, not a preference.
- **Video no longer needs capping by hand, as of the 2026-09-16 build.** At 1080p the phone used to
  complete the handshake, open every channel and break the TCP session with `EPIPE` about 3.5 s
  later, repeating every 5 to 6 s and never rendering a frame; 60 fps was fine, so resolution was the
  binding constraint. `archive/rounds/audio-sink-jitter-round4-results.md` N5 measured the app's own cap firing
  unasked on every session including a 40 minute one, with no `EPIPE` anywhere. Read
  `[RES_CAP] ... linkCapped=` and leave `resolutionId` alone. On a build older than that, cap it.
- **The P2P group name is random every session.** `wifi-direct-stable-identity` has no code path
  below API 29, so any run that keys off a stable SSID is untestable on this unit.
- **Build and install with** `install_and_launch.sh` and `HU=30041c35642d2200`. It wraps
  `build_hur.sh` (`assembleGithubDebug`) and installs and relaunches in one step.
- **The system clock was a fixed ~12h00m behind the host and D-POCO, and is not any more.** It was
  set by hand on 2026-09-17 and now matches the other devices, so **a round from that date on lines
  D-SAM's timestamps up against a phone's or the host's with no correction**. Confirm it anyway at
  the start of a round: `adb -s 30041c35642d2200 shell date` against the host's own `date`, one line,
  and say in Setup notes what it read. The offset was measured across `audio-sink-jitter-round4` and
  was still in force through `round7`, so **every result file up to and including round 7 carries
  offset D-SAM timestamps**: add 12h when reading one of those against a phone log. The old note that
  `date -s` silently no-ops no longer describes this unit; the method that did work is unrecorded.
- **`SettingsActivity` is not exported on this build.** `am start -n ... SettingsActivity` from a
  plain `adb shell` fails with a `SecurityException`. The substitute on an unrooted unit is
  `adb shell run-as $PKG am start --user 0 -n ...`, which launches from the app's own UID.
- **`am` rejects `-p` on 4.4.2** with a `NullPointerException` inside the `am` command itself, for
  both `am broadcast` and `am start`. Drop the flag everywhere; it is not specific to one action.
- **Never chain `svc wifi disable` and `svc wifi enable` in one `adb shell` call.** On this unit
  that produced repeated `try again in 1second` retries and then an unexpected ~18 MB
  `dumpstate`-shaped dump on stdout, probably because the WiFi service is not ready again when the
  enable fires. Issue them as separate calls with a pause between, which worked every time.
  Measured in `audio-sink-jitter-round5`.
- **It cannot host a WiFi access point, by any path found.** `ro.radio.noril=yes`: no baseband or RIL
  hardware at all, a true WiFi-only tablet. Its Settings app exposes a Tethering screen only through
  the hidden `com.android.settings/.Settings$TetherSettingsActivity` component, and that screen
  renders a header with nothing under it: no hotspot toggle of any kind. So **no hotspot-transport
  run (`native-ap-transport=1`) is possible on this unit**, and neither is anything downstream of one,
  including the WPP-over-TCP serve path. Route those to D-HU. Measured in `audio-sink-jitter-round7`,
  which lost its W1r to it after the app waited out both its budgets (30 s for the access point, 60 s
  for credentials) and failed with an explicit message, correctly.
- **Unverified here:** whether the in-app log export lands under `/storage/sdcard0` rather than
  `/storage/emulated/0`. That was measured on D-HP (Android 4.2.2) and is likely to hold on another
  Android 4.x unit, but nobody has checked it on D-SAM.

- **Its clock now matches the host.** The roughly 12 hour offset recorded through round 7 was gone
  in `projection-raise` round 3, `date` on both agreeing to the second. Check it rather than
  assuming either way, and say which in Setup notes.
- **Per-unit counters survive rounds nobody reported.** `wifi-direct-group-name-changes` read `4` at
  the start of `projection-raise` round 3, left over from an earlier session, which would have
  invalidated a run that assumed a fresh unit. Read any counter a run grades before the run and
  reset it deliberately, never assume it starts at zero.

- **Every clean Native AA session prints two `createGroup SUCCESS` and two `Handling handshake for`
  lines before SSL**, and the phone's `ConnectionStateCallback` `1-2-3-4-0` sequence twice with them.
  The P2P interface index does not move, and there is one SSL. This is how the unit settles, not
  contamination, so the second-group discard rule does not apply before SSL here. A mid-run Bluetooth
  toggle looks different: no handshake ever completes. Measured on B1 and B2 of `hold-aa-rfcomm`
  round 1 addendum 3.
- **`set_prefs_runas_host.py` writes this unit's settings unchanged**, as it does D-POCO's, because
  it never runs `sed` on the device. It replaces the manual `python3`-and-push recipe above.


### Moved from "Everything else"

- **`svc wifi disable` fails on API 19 too, and fails when issued alone.** Round 3 ran it on D-SAM
  by itself, not chained with `enable`, and got the same `"try again in 1second"` loop eight times
  over plus an unsolicited multi-megabyte `dumpstate` on stdout, with WiFi still enabled two seconds
  later. `settings put global wifi_on 0` does nothing either. Neither tablet has a working lever.

- **D-SAM as head unit fails to wake D-POCO while D-POCO is still bonded to D-HU's Bluetooth.** The
  symptom is HFP pokes answered and the Android Auto channel never opened (`SESSION_FAIL_SSL`, the
  "has never opened the Android Auto channel" warning). Unpairing D-HU's radio on D-POCO fixed it. The
  full entry, with the check and the numbers, is in `D-POCO.md`.
