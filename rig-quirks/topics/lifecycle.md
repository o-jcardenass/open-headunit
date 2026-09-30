# App lifecycle, auto-start and boot

Force-stop and receivers, exit and disconnect intents, auto-start mirrors, reboot and ACC wake. Moved verbatim from `TESTING-TEMPLATE.md` §7a on 2026-09-30. **Unless an entry names a unit, it was measured on D-HU.**

- **A force-stopped app's manifest receivers do not fire until it is explicitly relaunched.** §1
  requires the app stopped to write settings; after that, waiting for the phone's Bluetooth to
  reconnect and trigger `AutoStartReceiver` does **nothing**. Measured in the periodic-link-stall
  round 1: two full minutes with the phone's Bluetooth confirmed `state: ON` and idle, and
  `dumpsys activity services` showing no `AapService` the whole time; the session formed
  immediately once `MainActivity` was launched explicitly. Never plan a run that skips the explicit
  launch.
- **`headunit://exit` does nothing to an app that is already force-stopped.** Sent to a dead process
  it cold-launches the app through `AutomationActivity`, which then runs its own auto-connect and can
  form a brand new group: the opposite of the "confirm no group" the exit was for. Send it to a
  *running* app, then confirm with `dumpsys wifip2p`.
- **The device-protected auto-start mirror is `shared_prefs/settings_device_protected.xml`**, under
  `/data/user_de/0/com.andrerinas.headunitrevived/`, and it exists only once the app has written it
  at least once. Check both it and the host `settings.xml` whenever a run grades an auto-start key.
- **Clearing the auto-start device and testing a Bluetooth arrival are mutually exclusive on one
  unit.** A run that proves `auto-start-bt-macs` is cleared when two phones are paired removes the
  MAC a real arrival would have to match, so any run downstream of it that needs `ACTION_BT_AUTO_START`
  from a real arrival has nothing left to arrive against. Put the two on different units, or fire the
  action at the service component directly and say in the results that the receiver-side arrival was
  not the trigger.
- **`headunit://disconnect` stops the Native AA launcher, and a Bluetooth arrival is not the only way
  back, but on a build before the round 8 candidate `ACTION_START_WIRELESS` is not the lever.**
  `wifiLauncherManager.stop()` leaves the stopped launcher in place, so `setActive` answers "WiFi
  Mode NATIVE.mode with same start-configuration is already initialized" and arms nothing. Measured
  in round 7's D2, where it produced no session at all, and the refusal was at DEBUG so an INFO
  capture showed nothing whatever. `ACTION_NATIVE_AA_POKE` on a stopped manager does start it and
  logs why it had not started, but it costs a full RFCOMM handshake, and a `headunit://disconnect`
  issued just before will tear the reopened listeners down about 1.3 s later. From the round 8
  candidate on, the guard asks whether the launcher is still running and `ACTION_START_WIRELESS`
  re-arms; the refusal is at INFO, so a brief can grade its absence.
- **`pm disable-user` does not kill a package's already-running processes, and `dumpsys package`'s
  `enabled=0` does not mean disabled.** The mirror image of the entry above: disabling Gearhead
  while its `:projection`/`:shared`/`:car` processes are already alive from earlier testing leaves
  it fully functional until those processes are separately killed (`am force-stop` or a natural
  exit) — a session can still complete normally after a "successful" disable. Verify with
  `ps -A | grep <pkg>`, not just the disable command's own exit status. Separately, the per-user
  `enabled=` field in `dumpsys package <pkg>` reads `0` for the **default** (enabled) state, not
  disabled — `pm list packages -d | grep <pkg>` (present = disabled) is the only reliable check.
  Misreading `enabled=0` as "disabled" cost `projection-raise` round 4 two discarded captures.
- **`am force-stop` before `adb reboot` blocks the boot receiver.** It leaves the package stopped
  (`dumpsys package com.andrerinas.headunitrevived | grep stopped=`), so `BOOT_COMPLETED` is never
  delivered. Stop the app with `ACTION_STOP_SERVICE` before a reboot whose run needs auto-start.
- **ACC-on re-arms nothing unless `auto-start-on-boot=true`.** `onHibernateWake` is gated on it, so a
  run that sends `xy.android.acc.on` (or any wake action) needs that key set first.
