# Android Auto on the phone (Gearhead)

Its dialogs, cached records, data clears, string drift, GPS. Moved verbatim from `TESTING-TEMPLATE.md` §7a on 2026-09-30. **Unless an entry names a unit, it was measured on D-HU.**

- **Gearhead's own permission dialog wedges the head unit server exactly like a force-stop.** On
  `rotation-geometry-round1` it raised `RequestManifestPermissionsActivity` ("Permissions needed,
  Location") unprompted and stole foreground, after which Self Mode failed repeatedly with
  `java.net.SocketException: Broken pipe` immediately after the version request: the dev server
  accepted the TCP connection and dropped it before answering. Dismissing the dialog did not fix it.
  Recycling the "Start head unit server" developer toggle did. Add this to the triggers listed under
  `rig-dpoco-headunit-server-down`.
- **A stale `WifiNetworkSpecifier` request outlives the network it names, and the two obvious levers
  do not clear it.** `ConnectivityService` kept requesting an SSID from the previous round for
  minutes after that group had been renamed away, with Gearhead releasing and re-requesting the same
  specifier every few seconds, and every bring-up failed at "Obtaining IP address" with the head unit
  looking healthy end to end. `KEYCODE_HOME` and an `svc wifi disable`/`enable` cycle both left it in
  place; clearing Android Auto's app data cleared it. Check `dumpsys` for the old SSID before reading
  this as a candidate defect. Measured in `native-aa-wireless-round3`.
- **That data clear revokes Android Auto's notification access and raises Gearhead's first-run
  wizard**, which is the second half of the same recovery and looks nothing like the first. The
  symptom is a group churning every 15 s under a new name with the Bluetooth handshake completing
  each time and no `WirelessServer` accept ever following. `dumpsys activity activities` shows
  `TapHeadUnitActivity` in the foreground ("To continue, select Android Auto on your vehicle
  screen") and the head unit's own screen shows the other half of it. `adb shell am start -a
  android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS` opens straight to the one screen that
  fixes it, no scrolling. Measured in `native-aa-wireless-round3`.
- **Gearhead's phone-side strings drift between builds.** Three strings confirmed present in
  `17.5.663204` (`Using phone microphone`, `Not using phone mic`, `microphone timed out; no data
  received for`) did not appear at all on `17.3.662854`, on a run where the phone-side behaviour they
  describe demonstrably happened. Confirm any phone-side string against the build in front of you,
  and report the Gearhead version in Setup notes so a later reader can tell absence from drift.
- **Mocking GPS does not reach Android Auto's own navigation.** `cmd location providers
  set-test-provider-location` against the raw `gps` provider drives Google Maps on the phone itself
  (round 7 watched it compute a route and count an ETA down), but the projected session still
  reported `0 km/h` and never advanced past the first maneuver across ten minutes of fixes. Android
  Auto's nav rendering consumes Play Services' fused location, which this method does not feed. Do
  not brief a run that grades projected turn-by-turn against this lever.
- **Gearhead cannot be held down with `force-stop`.** Its own Bluetooth-triggered receiver restarts
  it and it answers the very next poke, confirmed in round 6 with the force-stop reissued every
  ~15 s. `pm disable-user` and a background force-stop loop may both be refused by the session's
  permission scope. The lever that holds is **forgetting the vehicle** in Android Auto's settings:
  one set of `uiautomator` taps, durable for the round, and the phone's Bluetooth stack still
  answers the HFP poke, which is what an unanswered-poke counter measures.
