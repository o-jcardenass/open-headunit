# Bluetooth, the poke and Native AA wake

Hands-free and A2DP links, poke targets and verdicts, adapter self-reverts, the external module route. Moved verbatim from `TESTING-TEMPLATE.md` §7a on 2026-09-30. **Unless an entry names a unit, it was measured on D-HU.**

- **`auto-start-bt-macs` does not scope the poke loop, and a round read it as a defect.** The wake
  list is `native-poke-bt-macs`; `auto-start-bt-macs` gates only which device auto-launches the app
  on a Bluetooth connect. An empty wake list plus `native-poke-all-paired-devices`, which defaults
  to on, is what walks every bonded phone. The two lists were deliberately split because one list
  doing both jobs wrote itself back and undid the user's clearing. To scope a poke, set
  `native-poke-bt-macs`.
- **`native-aa-wake-damage-verdict` latches once a wake has measured the unit**, by design, and a
  latched verdict stands every later poke down. `projection-raise` round 3 hit this and had to clear
  it mid-round to reach a session at all. Clear it to `0` before any run that needs a real session
  after a wake run, and say so.
- **`native-aa-complete-hfp-slc` reading `false` is not a regression, and one round reported it as
  one.** The setting is for units that cannot make a hands-free profile of their own, where the app
  publishes a stand-in record and has to speak on it. A unit whose own stack already advertises
  Hands-Free never gets a stand-in published at all, so the setting is inert there whichever way it
  is set, which is why D-HU connects fine with it off. Do not "restore" it on D-HU. The units it is
  live on are the tablets standing in as head units, D-SAM and D-HP, and a phone doing the same.
- **Poke targets survive from earlier rounds and wake the wrong phone.** `native-poke-bt-macs` and
  `last-connected-native-mac` are per unit and persist, so a head unit left pointing at a phone from
  an unrelated round will never let the phone under test join its group, with no line saying why.
  Repoint both before the first run of any round that changes the pairing.
- **The head unit's Bluetooth re-enables itself.** `adb shell svc bluetooth disable` is silently
  reverted about 14 s later — `AdapterState` shows `OffState → … → OnState` via `USER_TURN_ON`,
  reproduced twice with no other adb activity in between. It is not the app: the only
  `ACTION_REQUEST_ENABLE` in the source is behind the Bluetooth device-picker in AutoStart settings,
  needs a tap on the system consent dialog, and cannot fire during a run. Treat head-unit Bluetooth
  as **not switchable off** on this rig.
- **A live link survives head-unit-side restarts, so reuse one once you have it.** Round 8 ran four
  consecutive `force-stop` + settings-write + relaunch cycles on one underlying Bluetooth connection
  with A2DP intact throughout, verified before and after each. The risk to the link is touching the
  **phone's** radios, not the head unit's app. Once a link-dependent session is live, sequence every
  run that needs it back-to-back with head-unit-only resets and leave the phone alone.
- **Bring the head unit up before the phone, always.** Restoring the phone's radios first lets its
  own Bluetooth reconnect race an explicit `am start` a few seconds later, and the result is two
  sessions, two SSL handshakes and two `p2p-wlan0-N` interfaces — a genuine discard-rule hit. §4's
  order already says this; the periodic-link-stall round 1 lost a capture proving it. Launch the head
  unit app while the phone is still down, let the group settle ~15 s, then bring the phone back.
- **The phone's Bluetooth self-reverts too, sometimes.** Once in round 8, `svc bluetooth disable` on
  the phone was back to `state: ON` about 45 s later with no adb command in between; a second attempt
  minutes later held. Distinct from the head unit's ~14 s revert above and not yet characterised.
  Verify a radio is actually off rather than assuming the command took.
- **Native AA cannot connect with the phone's Bluetooth off.** The handshake itself needs RFCOMM, so
  a run that requires "no Bluetooth link *at connect time*" is impossible on this rig — round 8
  confirmed no session after 50 s versus 10-50 s on every successful attempt. Dropping Bluetooth
  after the session is established gets `bluetoothMedia=false`, but the deep links
  (`headunit://disconnect` then `headunit://connect`) do **not** produce a fresh SSL handshake, so
  connect-time decisions do not re-run. Briefs should route that coverage to a JVM test instead of
  asking for it on hardware.
- **"Bluetooth is on" and "the two devices are paired" are different facts, and only the second one
  matters.** Round 9 opened with the phone and head unit completely unbonded — each side's
  bonded-device list held only an unrelated speaker — traced to a bond removal and a failed re-pair
  the previous day (`bond_state_changed BOND_STATE_BONDING → BOND_STATE_NONE` within 1 s). Native AA
  can do nothing without the bond, so the whole round was blocked until it was re-paired **by hand**:
  pairing needs a UI confirmation and adb-only tooling cannot restore it. Check both sides before
  assuming the "bring the phone's Bluetooth up once" step above is enough:
  ```bash
  adb -s <hu>    shell dumpsys bluetooth_manager | grep -iA 20 "Bonded devices"
  adb -s <phone> shell dumpsys bluetooth_manager | grep -iA 20 "Bonded devices"
  ```
- **The phone's own reconnect beats our poke, so the poke is normally never exercised.** Whenever the
  phone has recently seen the car it completes a full SSL handshake 3-6 s after the head unit's
  listeners open, while `NativeAaHandshakeManager` logs "handshake already in flight" and never calls
  `socket.connect()` at all. Round 9 ruled out the obvious levers: force-stopping
  `com.google.android.projection.gearhead` and deleting the head unit's persistent group
  (`cmd wifip2p delete-saved-group`) changed nothing, and the phone's own P2P cache cannot be
  inspected without root (`cmd wifip2p` → `SecurityException: Uid 2000 does not have access`).

  Two recipes, depending on what you need:

  - **One poke round, then a normal session** — phone Bluetooth **off**, launch the app so its RFCOMM
    listeners and P2P group come up while the phone is unreachable, wait ~8 s, phone Bluetooth back
    on. Round 9 used this for R2 and R5.
  - **The poke running indefinitely** — phone **Wi-Fi off**, phone **Bluetooth on**. No session can
    form, so the loop never reaches its `Stopping poke retry loop (… session=true)` exit and fires
    every ~15 s for as long as you leave it. Use this whenever a run needs many poke rounds.

  A brief that wants to observe poke behaviour must build one of these into its setup; "force-stop
  and relaunch" will not do it.
- **A third option, when the run is actually about `pokeDevice()`'s own guards rather than the
  automatic retry loop's timing: trigger a poke directly, scripted.** The app's manual-poke UI
  (device picker in `HomeFragment`) sends an explicit `Intent` that reaches `pokeDevice()` straight
  away, bypassing `NativeHandoffPolicy` (settling/handshake/session) entirely:
  ```bash
  adb shell am start-foreground-service -n com.andrerinas.headunitrevived/com.andrerinas.openheadunit.aap.AapService \
    -a com.andrerinas.openheadunit.ACTION_NATIVE_AA_POKE --es extra_mac "<device MAC>"
  ```
  Round 11 needed this because on this rig the automatic loop's very first `while`-check routinely
  lands on `handshake=true` before ever reaching `pokeDevice()` — the phone's own AA reconnect (over
  Bluetooth RFCOMM) rides the same fast link that keeps HFP alive, so "launch and watch" alone proves
  nothing about `pokeDevice()`'s guards; a session forms with zero pokes ever attempted. Use this
  whenever a run is specifically about what `pokeDevice()` decides (a guard, a pairing check), not
  about the retry loop's cadence or whether it starts at all.
- **`NativeAaHandshakeManager.start()` requires the head unit's own Bluetooth adapter to be enabled
  at the moment it runs, and silently no-ops (`Bluetooth adapter not available or disabled`, E-level)
  if it isn't** — toggling the adapter off *before* launching the app, even briefly, can prevent the
  whole manager from ever coming up, not just delay it. A run that needs the adapter off must let
  `start()` succeed first (app already running, listeners already open) and only then toggle it,
  never toggle-then-launch. Also: `svc bluetooth enable` completing does not mean
  `BluetoothAdapter.isEnabled` is already `true` a couple of seconds later when `start()` checks it —
  round 11 saw `start()` fail this way even after an explicit `enable` and a 2 s wait before launch.
- **Both poke targets fail on this rig, exactly as on the reporter's — usually, but not always.**
  Round 9 saw HSP-AG and HFP-AG both fail to connect every time (`read failed, socket might closed
  or timeout, read ret: -1`, ~6 s timeout). Round 10 saw the opposite in the same session: 13/13
  HFP-AG pokes connected, and a separate short run saw 3/3 HSP-AG pokes connect. Poke connectivity
  varies between sessions on this rig rather than being a fixed property of either SDP record — do
  not write a run whose PASS *or* FAIL depends on assuming a poke will or won't connect; measure it
  each time.
- **Recovering `HeadsetClientService` after a bad disconnect is not always a single Bluetooth-adapter
  cycle.** Round 9/10's documented recovery (cycle the head unit's own Bluetooth adapter off/on,
  ~14 s self-revert) worked on the first try after round 10's R2 finding. It did not work after R3's
  session-contamination episodes — that needed a *second* head-unit-side cycle plus a phone-side
  Bluetooth off/on before `curState` returned to `Connected`. If one cycle doesn't recover the link,
  try a second, then the phone's own adapter, before concluding something is actually broken.
- **Cycling the *phone's* Bluetooth radio does not produce a fresh `ACL_CONNECTED` here. Cycling the
  *head unit's own* adapter does.** `svc bluetooth disable` / `enable` on the phone was tried at a 2 s
  and a 10 s window, with up to 40 s of continuous observation after each, and no
  `android.bluetooth.device.action.ACL_CONNECTED` ever reached the head unit — confirmed absent from a
  continuous capture, not a `logcat -d` ring wrap. The working substitute is `svc bluetooth disable` on
  the **head unit**, which self-reverts in ~14 s per the entry above and raises a real system-level
  `ACL_CONNECTED` every time. Corroborate it from the phone's own
  `GH.WifiBluetoothRcvr: Connection action: ... ACL_CONNECTED` rather than from our side alone, so the
  run is not resting on our own receiver to prove our own receiver fired.
- **The external Bluetooth module route can be reached on a unit that has no module, with root.**
  `ExternalBtPolicy.detect` accepts the system property `rw.zlink.bt.type=extra` (case-insensitive)
  as evidence, alongside the `/dev/rf_serial` and `/dev/zj_bt_serial` nodes. `setprop
  rw.zlink.bt.type extra` therefore puts the app on its real detection path, which is the only way to
  see the module settings rows, the probe and the refusal dialogs on ordinary hardware.
  `BluetoothHelper.externalBtEvidence` is a `by lazy`, so the property has to be written with the app
  force-stopped and read on the next launch. Native AA is refused while it is set, so do this last in
  a round and clear it with `setprop rw.zlink.bt.type ""` plus a force-stop afterwards.
- **The settings-screen module probe and the real module route log under different prefixes, and
  never both.** `ZbtProbe` is the diagnostic behind the "Test the head unit's Bluetooth module" row
  and every line it writes is `ZbtProbe:`-prefixed; on an unreachable daemon it writes the raw
  exception (`ZbtProbe: ConnectException: ...`) and the row itself reads "Nothing is listening on
  port 3152. This unit has no vendor Bluetooth daemon to talk to." The `NativeAA: [ZBT]` lines,
  including `nothing is listening on 127.0.0.1:3152`, come from `ZbtDaemonReachability` and
  `ZbtAaCarrier`, which run only during a real Native AA handshake attempt. A brief that quotes a
  `NativeAA: [ZBT]` line for a probe run is asking for a line that cannot appear.
- **D-POCO is still bonded to D-HU and takes the poke rotation from D-MOTO.** With no preferred device
  and poke-all-paired on, D-POCO answers and `Phone reports it is still joining` repeats for minutes
  with no session. For a round that needs D-MOTO, `adb shell svc bluetooth disable` on **D-POCO**
  first and re-enable it afterwards; name the phone that actually connected in the results.
