# D-POCO quirks

The POCO phone, and when it stands in as the head unit. Moved verbatim from `TESTING-TEMPLATE.md` §7a on 2026-09-30. **Unless an entry names a unit, it was measured on D-HU.**

- **D-POCO's WiFi adapter self-reverts back on** a few seconds after `svc wifi disable`, the same way
  the documented Bluetooth self-reverts do on other units. A single disable will not hold the phone
  off a network: use a repeat-disable loop for any run that needs the link starved rather than merely
  interrupted. Measured in `audio-sink-jitter-round4`. **Round 5 refined both halves of this.** The
  self-revert appears to follow a single disable only: after a sustained repeat-disable loop the
  radio stayed off for several minutes and needed an explicit `svc wifi enable`. And a 15 s cadence is
  too loose, because the radio comes back fast enough inside the gap to complete a whole poke,
  handshake, join and session cycle, which produces churn rather than starvation. Use 3 s.
  `s1_wifi_holddown.sh` and `s1_wifi_holddown_tight.sh` in `hur-wifi-test-scripts/` are the two
  cadences.
- **Rotating D-POCO from adb has a recipe, and three traps that each cost a discarded run.** Write
  `accelerometer_rotation 0` and the target `user_rotation` together, wait several seconds, and only
  then force-stop and relaunch the app: set during the launch race, the app negotiates the wrong
  starting orientation. The rotation itself then lands with a lag of up to ~15 s. `dumpsys display`'s
  `rotation` field is not a live check on this unit and read `0` through genuine rotations, so trust
  the app's own `HeadUnitScreenConfig: Raw size:` line or a screenshot. The launcher is
  portrait-locked whatever any of these settings say, so it is never a rotation control and an early
  diagnosis against the home screen wrongly concluded rotation was broken outright. And `adb` cannot
  turn a window that is in `SCREEN_ORIENTATION_SENSOR`, which is what the app's Auto orientation
  setting resolves to, so an Auto run has to be turned by hand. Measured in
  `rotation-geometry-round1`.
- **Confirm D-POCO's screen is idle or at home before trusting an automated bring-up.** A stray
  Settings screen left open from earlier diagnosis silently stopped Gearhead answering the HFP poke
  with the Android Auto channel, with nothing on the head-unit side to point at it.
  `adb shell dumpsys window | grep mCurrentFocus` is the check; `input keyevent KEYCODE_HOME` is the
  fix. Measured in `audio-sink-jitter-round5`.
- **Forgetting the head unit on D-POCO has no adb-reachable trigger, and neither does the consent
  dialog that follows.** Settings > Android Auto > Vehicles > each entry > Forget is UI only;
  `am start` into Gearhead's activities finds nothing scriptable. The next connection then raises a
  one-time "Welcome to Android Auto" dialog whose `Continue` is also UI only. Both are done with
  `uiautomator dump` plus minimum taps. This is the documented and so far only recovery from a phone
  wedged on a stale WPP-over-TCP endpoint. Measured in `audio-sink-jitter-round5`.
- **That forget is not durable, and round 5's claim that it held for a round is refuted.** Round 7
  forgot the vehicle and the very next poke cycle was answered in full: RFCOMM accepted 290 ms after
  the poke, a complete handshake, a session and video within 15 s, with no consent dialog shown.
  Gearhead keeps opening that socket whether or not the vehicle is remembered. So a forget clears a
  stale *endpoint*; it does not make the phone ignore the head unit, and any run whose lever is "the
  phone stops answering" needs a different one. Measured in `audio-sink-jitter-round7`.
- **Gearhead caches the head unit it last talked to as a raw Bluetooth MAC, and it is not the
  Vehicles list.** With a third rig device attached and idle but still bonded to D-POCO, the phone
  kept opening RFCOMM to *that* unit's real MAC while the unit under test sat unanswered: its own
  listeners opened and its pokes went out, and nothing ever came back. Check it early on any round
  that moves D-POCO between head units, with `dumpsys bluetooth_manager | grep -A1 mActiveDevice`
  and the phone's own `GH.WIRELESS.BT: Creating rfcomm socket for device: <mac>`, and compare that
  MAC against the unit under test rather than against the two round 3 signatures above. Toggling
  either device's Bluetooth does not fix it (the idle unit's radio self-reverts on within 15 to
  20 s, and D-POCO's toggle moves the live HFP pointer without moving Gearhead's cached target),
  and `forget_car_gearhead.sh` does not either. What does is
  `pm clear com.google.android.projection.gearhead`, after which the very next bring-up connected
  on its first RFCOMM attempt. Notification access survived that clear, unlike the entry above. Measured in
  `native-aa-wireless-round4`.
- **D-POCO's `dumpsys wifip2p` reads `P2pDisabledState` for minutes after a rapid WiFi bounce while
  the radio is fine.** Seen twice in `audio-sink-jitter` round 6 after `svc wifi disable`/`enable`
  cycling, with station WiFi itself reading `Wi-Fi is enabled` and a real P2P join completing
  minutes later. Treat it as a stale read, not a disabled radio, and do not gate a run on it.
- **D-POCO stays bonded to every head unit it has projected to, and a stale bond can stop it waking another one.**
  With D-HU's Bluetooth radio (`Navegadortz2`) still in D-POCO's paired list beside D-SAM's
  (`Navegadortz3`), D-SAM as head unit got the HFP poke answered and the service level connection
  established 15 times in 3 minutes, but the phone never opened the Android Auto channel: no
  connect-back, no `Incoming connection`, `SESSION_FAIL_SSL` after 150 s, on 3 attempts. The app's own
  warning appeared 3 times per attempt ("has never opened the Android Auto channel ... most likely
  bound to a different Bluetooth device that also advertises the Android Auto service"). After the
  operator unpaired `Navegadortz2` on D-POCO, the next two bring-ups formed a session and ran 5 cycles
  each. Corroborated, not proven: no control run was made with the bond present against a passing
  one. There is no adb verb to unpair, so it is an operator step: before a round that moves D-POCO
  between head units, check `adb -s 4f4027e9 shell dumpsys bluetooth_manager | grep -a -A8 'Bonded
  devices'` and ask for the other head unit's radio to be forgotten. Switching the other unit's
  Bluetooth off is not enough. Measured in `projection-teardown-and-relays` round 1.
