# WiFi, WiFi Direct and hotspot

Station radio, P2P groups, the hotspot transport, WPP status lines, airplane mode. Moved verbatim from `TESTING-TEMPLATE.md` §7a on 2026-09-30. **Unless an entry names a unit, it was measured on D-HU.**

- **`ACTION_START_WIRELESS_SCAN` is refused on D-POCO right after a force-stop**, with
  `startForegroundService() not allowed due to mAllowStartForeground false`. That is Android 12+
  blocking a background foreground-service start. D-HU accepted the verb in the same thread,
  probably because of its overlay permission. On an API 31+ unit without that permission, arm with
  `adb shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity`, which arms the stored
  mode on launch, and say so in Setup notes. Measured in `hold-aa-rfcomm` round 1 addendum 3.
- **`WifiDirectManager: operating channel` never prints at API 29 and above.** It is the pre-Q
  reflection path. Read the group's frequency from `adb shell dumpsys wifip2p | grep -i frequency`
  instead.

- **A run that sets `native-ap-transport=1` arms the phone, permanently, and this is the rule that
  orders a multi-phone round.** On its own access point a head unit advertises a WPP-over-TCP endpoint
  unconditionally; the phone stores it for the life of its Android Auto process and dials it in
  preference to Bluetooth, and **nothing in the protocol or in the app can retract one**. Round 7 lost
  its first three bring-ups to an endpoint left over from round 6, across a round boundary, with
  nothing either round's settings did able to clear it. On WiFi Direct neither head unit can advertise
  one (D-SAM's Android cannot name its own P2P group, D-HU re-addresses its group on every create), so
  only hotspot-transport arms carry this cost. Put every such arm last, on a phone no later block
  needs clean, and forget the vehicle on it before it is used again.
- **A P2P group outlives `am force-stop` on the unit that hosted it.** The previous head unit keeps
  its group up, and a phone still associated to it will not join anything else. Bouncing the phone's
  WiFi with `svc wifi disable` then `enable` clears it; stopping the old head unit's app does not.
- **This head unit's `WifiScanner` service does not work, by any route.** `cmd wifi start-scan`
  returns exit=0 and `list-scan-results` says "No scan results", with
  `E/WifiScanRequestProxy: Failed to retrieve wifiscanner` right after `I/WifiService: startScan`;
  the `WIFI_SETTINGS` screen hits the same broken backend. Reproduced twice. Any run needing a
  head-unit-side scan is UNTESTABLE here — do not spend time on it. The phone's own
  `cmd wifi start-scan` works cleanly and returns real results.
- **`cmd connectivity airplane-mode enable|disable` *does* work on this phone**, unlike the
  `am broadcast` route in the bullet below. It reliably drops Bluetooth and usually WiFi, but coming
  back it restores **neither** radio reliably. `svc wifi enable` was already documented here; the
  media-gap round 2 found the same on the other radio, where `disable` brought WiFi back and left
  Bluetooth off, needing an explicit `svc bluetooth enable`. Run both nudges after every
  `airplane-mode disable` and verify both, rather than treating either as automatic. Native AA needs
  Bluetooth for the handshake, so a missed nudge here costs the whole session, not one run.
- **Airplane mode cannot be toggled from adb on this phone.**
  `am broadcast -a android.intent.action.AIRPLANE_MODE` is refused with
  `SecurityException: Permission Denial: not allowed to send broadcast … uid=2000`, and
  `settings put global airplane_mode_on` alone changes the flag without moving the radios. §4's
  clean-run protocol therefore cannot be followed literally here — use the phone's Bluetooth adapter
  (`svc bluetooth enable|disable`) as the lever for link-state changes, and say so in Setup notes.
- **Phone Wi-Fi off does not prevent a session from forming, because Wi-Fi Direct/P2P is not gated
  by the Wi-Fi station-mode toggle on this phone.** Round 10 needed a "no session can ever form"
  precondition to run the poke loop indefinitely and could not get one: across three attempts (adb
  `svc wifi disable` twice, the phone's own UI once), `dumpsys wifi` confirmed Wi-Fi disabled
  throughout, yet a full Android Auto session formed within 35 s every time. One of those attempts
  ran unsupervised for ~68 minutes as a result, undetected until someone checked. A brief that needs
  to suppress session formation for an extended window cannot rely on this lever on this phone; no
  working alternative is known yet.
- **`svc wifi enable` does not reliably bring the station radio back after `svc wifi disable`.** Hit
  in rounds 3 and 4; in round 4 all three runs that toggled WiFi needed help, one of them still
  reading `wifi_on=0` five and a half minutes later, and the app's own internal re-enable attempt
  (for P2P group creation) did not flip it either. The nudges that work are
  `cmd wifi connect-network "<ssid>" wpa2 "<psk>"` or a second manual `svc wifi enable`. Budget for
  it in any run that disables WiFi, verify `settings get global wifi_on` rather than assuming the
  command took, and **never read the resulting stall as a candidate defect** — round 4's R9 lost 5.5
  minutes to it on a path that has no nudge script of its own.
- **This rig is permanently joined to a WiFi network** (`Pegue Cdesta`, 5500 MHz), and has been for
  every round on record. Any run whose premise is an *unjoined* head unit is **UNTESTABLE** here, and
  authorization to change the rig's own network association has never been given. The media-gap round
  1 lost a run to a brief that asserted the opposite without checking. **Verify a rig-state premise
  with a command before writing it into a brief**, not from memory of an earlier round:

  ```bash
  adb shell dumpsys wifi | grep -iE "mWifiInfo|SSID|Frequency" | head
  ```
- **A run that needs D-HU on a 2.4 GHz network needs the operator's hands, twice.** D-MOTO is not
  rooted, so `cmd wifi start-softap` refuses (`SecurityException: Uid 2000 does not have access`) and
  its hotspot is hand-started; and D-HU roams straight back to its saved 5 GHz network the moment the
  app exits, so every run after the first needs the join redone. `cmd wifi connect-network` is not
  the way back: it accepts neither a bare saved network id nor this rig's spaced SSID
  (`Unknown network type Chingon`). Round 8 ran C1, C3 and C5 hours after the rest of Part C for this
  reason. **Order a brief's runs so every hand-operated-hotspot run is adjacent**, and put the
  NEVER-mode run between them where it can be, since NEVER never leaves the network.
- **The discard rule is "a *second* `createGroup SUCCESS`", not any sign of churn.** Two benign
  patterns keep tripping the broader reading, and both have now been seen in two independent threads:
  a `p2p-wlan0-N` index bump that happens **before** the first `createGroup SUCCESS` is a stale group
  from a previous round being torn down at launch, and a lone `MATCH! Starting AapService` with **zero
  group churn attached** is the phone's own Bluetooth reconnect. Neither is contamination. Count the
  thing that actually matters:

  ```bash
  grep -c "createGroup SUCCESS" capture.txt   # more than 1 in one run is the discard
  ```

  A second SSL handshake in one run is the corroborating signal. Report the counts either way, so a
  clean run is on the record as clean rather than merely unremarked.
- **A phone can be left hosting its own WiFi Direct group from an earlier round.** A unit that played
  head unit keeps `isGroupOwner: true` on a `DIRECT-` network, and while that stands a phone-role
  connection to the real head unit stalls forever at `PHONE_JOINING`: the RFCOMM handshake completes,
  credentials go out, and `WirelessServer: Incoming connection detected` never appears. Check every
  phone with `dumpsys wifip2p | grep isGroupOwner` before the round starts. On a rooted unit
  `cmd wifip2p remove-group` clears it; on an unrooted one that call is refused with a
  `SecurityException` and a `svc wifi disable`/`enable` cycle is the lever.
- **A spaced SSID needs its quotes escaped so they survive adb's argv join.** `adb -s $HU shell cmd
  wifi connect-network \"SSID With Spaces\" wpa2 psk` works; a plain locally-quoted `"SSID With
  Spaces"` is re-split on the far side and fails with `Unknown network type <second word>`. Round 7
  found even the escaped form unreliable from some shells and added
  `hur-wifi-test-scripts/connect_hotspot.sh`, pushed to `/data/local/tmp` and run on the unit, which
  sidesteps the join entirely. Prefer the script for any run that joins a named hotspot.
- **A hotspot-arm-then-revert recipe has to clear four keys, not flip one.** Setting
  `native-ap-transport=0` while `hotspot-ssid`, `hotspot-password`, `static-bssid` and
  `hotspot-interface` still carry the hotspot arm's values makes the next WiFi Direct group form on
  the hotspot's own static BSSID and read `identity stable=yes`, which is a false reading and voids
  anything graded on it. Clear all four (`""`, `""`, `"0"`, `""`) and read them back. Caught in
  `wpp-over-tcp` round 4 and again in `audio-sink-jitter` round 6, on a different thread's brief.
  **Its signature is a phone that cannot join at all**, and `wpp-endpoint-depoison` round 1 lost two
  runs to it while reading it as a radio fault: head unit `groupFormed: true isGroupOwner: true`,
  phone `groupFormed: false`, `CONNECTING_WIFI` then `ABORTED_WIFI` about 36 s later with
  `STATUS_WIFI_NETWORK_UNAVAILABLE`. The phone joins on name **and** address, so an announced BSSID
  the group does not carry is a network it can never find. The one line that names it is
  `onGroupInfoAvailable: ... (source=static override)`, and the source dump above it carries the
  group's real address. From `8b3e15f3` the two settings are separate (`static-p2p-bssid` for the
  group) and a hand-typed address answers only where no rung read one, so on that build and later
  the hotspot's value cannot reach a group over an address the hardware reported.
- **`WifiVersionResponse ... status=NO_SUPPORTED_WIFI_CHANNELS(-8)` is inert.** It appears on the
  phone's channel-negotiation reply on sessions that go on to connect, nothing in the head unit
  branches on it, and a version rejection would be `-1`. Do not spend a run on it.
- **D-MOTO can hold a WPP-over-TCP record for another head-unit identity**, such as
  `192.168.49.1:5299` from D-POCO standing in as the head unit. Forgetting D-HU in Android Auto does
  not remove it. `pm clear com.google.android.projection.gearhead` does, and only with the
  operator's approval, since it wipes Android Auto's data on the phone.
