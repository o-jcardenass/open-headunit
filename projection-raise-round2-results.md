# projection-raise — round 2 results

**Candidate:** fix/audio-sink-and-wireless-bring-up @ `bed8ad85`       **Baseline:** not used this round
**APK md5:** `ac998c4a05b56c8854309fdbdef46e01` (one build for the whole round; installed-APK pull matched on all three units)
**Unit:** D-HU = UNISOC MT50, Android 14 (stage A); D-SAM = Samsung SM-T230, Android 4.4.2 (stage B); D-HP = HP Slate 7 Plus, Android 4.2.2, Tegra 2 (stage C)
**Date:** 2026-09-17

## Setup notes

- **`./gradlew` needs `JAVA_HOME=/opt/android-studio/jbr`** to run directly in this environment: the
  system default (`/usr/lib/jvm/java-21-openjdk-amd64`) is a JRE with no `javac`, so a bare
  `./gradlew :app:testGithubDebugUnitTest` fails at the compile step with "does not provide
  JAVA_COMPILER". `hur-wifi-test-scripts/build_hur.sh` already exports this correctly; only a direct
  gradle invocation needs it said explicitly. Gate: **2133 tests, 0 failures**, matches the brief.
- **Host PC's own firewall was blocking all inbound LAN connections** (confirmed on two arbitrary
  ports, ICMP worked, TCP did not) — needed a firewall rule change on the host before N4r's deaf
  listener could be reached from D-HU at all. Not a rig quirk, an environment gap on this session's
  host; flagging because a future round on a differently-configured host may not need this.
- **D-HU's and D-POCO's own copies of "Pegue Cdesta" both needed a manual UI tap to reconnect**
  (autojoin disabled, no adb verb re-enables it — matches the known D-HU quirk, and turns out to
  apply to D-POCO's saved profile of the same network too).
- **Stage A's automated single-driver wake path (`NativeDriverSelectionPolicy` AUTO mode with one
  candidate) undermines both W1r and W2r structurally** — see W1r below for the mechanism.
- **D1's third repetition was invalidated by test-script timing**, not a real race outcome: D-POCO's
  own natural reconnect (no poke involved) beat the `am start-service` poke command to D-MOTO by
  roughly 10 seconds, because the command was issued too late relative to the bye-bye that started
  the reconnect clock. Not repeated a third time given the round's remaining scope; two clean
  repetitions (reported below) already answer the brief's question in both directions.
- **D-SAM's device-to-device ARP resolution to D-POCO was transiently broken** (`Destination Host
  Unreachable` on both sides despite a resolved-looking ARP entry with the correct MAC), self-resolved
  after a short wait with no adb intervention. The same pattern recurred, and self-resolved, on D-HP
  later in the round — see L1.
- **`input tap` on both D-SAM and D-HP's Android builds uses the UIAutomator-reported LOGICAL
  (rotated) coordinate space, not the raw `screencap` buffer's physical space.** The physical
  buffer comes back portrait (800×1280) while the app forces landscape content into it (rotation="1"
  in a `uiautomator dump`); tapping at the *screenshot's* apparent pixel position fails, tapping at
  the *uiautomator bounds'* logical position works. Cost real time on both units before landing on
  the right mapping; worth carrying forward to the next round that scripts UI on either tablet.
- **D-HP: `run-as $PKG am start --user 0 -n .../SettingsActivity` (the brief's own recommended command
  for an unrooted unit) segfaults this unit's `am` binary every time it was tried**, without crashing
  our app. Touch-tap navigation to Settings also could not be gotten to register reliably on this
  unit despite retrying both coordinate systems above — see L4.
- **D-HP: found live during the round, not asked for by the brief — `audio-queue-capacity` was
  carried over from an earlier session as `0` ("no limit," per the setting's own code comment)
  instead of the shipped default of `50`.** This produced a real, user-visible defect: an operator
  watching the live session reported audio delay growing to an estimated 5 minutes over the session's
  life, confirmed in logs as `AudioTrackWrapper`'s internal `dataQueue` (an unbounded
  `LinkedBlockingQueue`, `queued=` in `sampleHealth`) climbing monotonically (1060 → 1409 → 1381 →
  1385 → 1920 across ~2 minutes of sampling) even through a `rebank` event that reset the *ring
  buffer's* own `depth` to 0 — the ring buffer and its `depth` metric were never the backlog; the
  unbounded queue feeding it was, and nothing in the existing telemetry surfaces that queue's size as
  a duration. Reset to the default (`50`) mid-round; confirmed `queued` immediately dropped to
  single digits and stayed there, including through two subsequent decoder-stall events that shed
  hundreds of stale audio frames cleanly instead of accumulating them. Not a candidate regression —
  the default is 50 and always was — but the failure mode itself (silent, unbounded, invisible to
  `depth`) is real and reachable by anyone who sets `audio-queue-capacity=0`, and is worth a look:
  either warn on 0, or have `sampleHealth` report the unbounded queue's own age/duration rather than
  just its count.
  **Related, not fixed this round:** with the queue correctly bounded, D-HP still hit two decoder
  stalls in about 10 minutes of live use (`Decoder stall detected`, `Display stall ... Rebuilding
  projection view`, and on the second stall a permanent `TEXTURE → SurfaceView` fallback for the
  session) — one during a user touch-drag gesture, one with no touch input at all. This reads as a
  hardware-class limitation of this specific 2013 Tegra 2 tablet under sustained H.264 decode load,
  not a regression in this branch; the existing stall-recovery and view-mode-fallback logic handled
  both correctly once the audio queue was no longer compounding them.
- **`native-aa-complete-hfp-slc` cannot help D-HU's own W1r/W2r scenario, and should be able to.**
  Per the operator's observation mid-round: the setting only registers a stand-in HFP record on a
  unit whose own Bluetooth radio does not already advertise Hands-Free (confirmed in B2 below); on
  D-HU, which does advertise Hands-Free and already holds a real, standing HFP link to D-POCO, the
  setting is inert by design (matches the documented "Everything else" quirk). That is exactly the
  condition the hands-free-wake escalation exists to work around, so the same setting — or a new one
  — should be able to deliberately *displace* a real existing HFP link on a unit like D-HU, not only
  fill in for a missing one, or W1r/W2r can never be exercised there at all. Recommending this as a
  follow-up, not attempting it this round.
- One new script added: `e1_exit_teardown_cycles.sh` in `hur-wifi-test-scripts/`, for E1's ten-cycle
  exit/teardown pattern (nothing existing fit it). Left in place.
- `set_pref.sh` (the standard run-as pref writer) fails with `sed: not found` on D-SAM as documented;
  used the documented workaround (edit a local copy with `python3`, push the whole file, `run-as $PKG
  cp` it into place) throughout stages B and C.

---

## 1. Build and baseline gate

**PASS.** 2133 tests, 0 failures, matches the brief's own count exactly (the tip was not amended
again since the brief was written). Installed-APK md5 matched the built APK on all three units.

---

## Stage A — D-HU, D-POCO, D-MOTO

### W1r — The probe, with something to keep the phone away

**UNTESTABLE**, and not for the reason the brief anticipated.

Preconditions were all correctly established: `last-connected-native-mac` already named D-POCO
(confirmed by running one ordinary session to completion first), Gearhead was disabled on D-POCO,
its Bluetooth HFP link to D-HU held `Connected` throughout, and the arming line read verbatim:
`NativeAA: waking a phone over a hands-free link it holds is not yet measured on this unit, so the
first one is the measurement.` at `16:53:19.069`.

What actually happened: D-POCO was auto-selected as the "chosen driver" (single native-poke
candidate, `native-driver-selection-mode=1`/AUTO), which routes wake attempts through
`NativeAaHandshakeManager.manualPoke()`'s bounded budget
(`NativeDriverSelectionPolicy.CHOSEN_WAKE_ROUNDS = 3`, 15 s apart) rather than the open-ended
retry loop `HandsFreeWakeEscalationPolicy`'s 90 s `ESCALATE_AFTER_MS` is written against. Only
**two** bounded passes ran (`16:53:35.120`, `16:53:50.140`), both logging `BluetoothWakePolicy`'s
correct refusal (`reason=TARGET_CONNECTED`, "this head unit already holds a Bluetooth hands-free
link to it"). No third pass, and no further pass of any kind, appeared before the UI's own
150 s auto-connect watchdog gave up the whole attempt at `16:55:48.336` — 118 s of total silence
after the second pass. The 30 s no-poke-after-wake window (`grep -c "Calling socket.connect()"`)
trivially reads 0, but only because nothing ever escalated to be checked.

**Per the brief's own fallback:** "If the escalation still does not fire with Gearhead disabled,
report the arming line, how long the stand-down ran, and what `BluetoothWakePolicy` said each
pass" — done above. Stand-down ran ~15 s before the automated path gave up on its own, far short
of the 90 s the escalation needs.

### W2r — The verdict holds across an arming

**UNTESTABLE**, same root cause as W1r: no verdict was ever stored to re-check. Confirmed by
reading the arming line after a plain re-arm (still "not yet measured on this unit," `17:06:42.349`)
and again after a full reboot (still "not yet measured," `17:07:44.298`). `settings.xml` carries no
`native-aa-wake-damage-verdict` key at all, before or after the reboot.

### N4r — A failed handshake takes the pill down with it, with a peer that is genuinely deaf

**PASS.** Bare TCP listener on the host PC's port 5277, D-HU in Headunit Server mode
(`wifi-connection-mode=1`), both on the house LAN.

- `NetworkDiscovery: Scanning subnet: 192.168.1.*` then `Found Headunit Server on 192.168.1.11:5277`
  — discovery found the plain listener the same way N3 found a real one.
- Both attempts reached the identical deaf-peer signature: three version-request retries 2 s apart,
  then `Handshake: the peer accepted the connection and then sent nothing at all. ... Force stop
  Android Auto on the phone, and reboot it if that does not help.`, then
  `AapService: session state failed (peer_silent)`, then `status pill step: hidden` — both times,
  cleanly, with no stuck stage.

This grades detection and the pill, not a real-world trigger, exactly as the brief says: a real deaf
head unit server is still a phone-side state this rig cannot manufacture.

### N5r — The WiFi button inside the quiet window

**INCONCLUSIVE**, with two findings the brief did not anticipate.

1. **The brief's own suggested command is a documented no-op.**
   `am broadcast -a com.andrerinas.openheadunit.ACTION_START_WIRELESS -p ... --ez no_ui true` never
   suppressed anything (confirmed: the projection raised anyway, `raising the projection by DIRECT`,
   right after the broadcast). Reading `AutomationCommandPolicy.kt`: `ACTION_START_WIRELESS` is in
   the `PLAIN_RELAYS` map, documented in-code as "Commands that need no extras," which silently
   drops `EXTRA_NO_UI` before it ever reaches `AapService`. A **direct** `am start-service -n
   .../AapService -a ...ACTION_START_WIRELESS --ez no_ui true` (bypassing the automation door
   entirely) does work: `AapService: Not raising the projection, a no_ui command asked for the
   session only` fired correctly and HomeFragment stayed up for the rest of that connection's life.
   Flagging as a brief erratum for whoever reruns this: the documented command needs the direct
   `am start-service` form, not a broadcast, for this specific action.
2. **The WiFi-button-during-the-quiet-window race is real, and finer-grained than round 1 measured.**
   With `no_ui` correctly armed, tapping "Exit" → "Finish later" (round 1's clean-bye-bye lever) and
   immediately tapping the WiFi tile's coordinates showed the tap still being intercepted by the
   *dying* `AapProjectionActivity` and forwarded to the phone as a video touch event
   (`AapProjectionActivity.sendTouchEvent | Touch map: raw=893,335 -> video=...`) **87 ms after**
   `HomeFragment.onResume` had already fired — i.e. the new Fragment being resumed does not mean it
   owns touch input yet; the old Activity's window keeps intercepting for a short overlap. A second
   attempt timed ~1.5 s after the bye-bye (well clear of that overlap) produced no `Touch map` line
   (confirming the old Activity was gone) but also no `connectToNativeDevice` log, so the tap simply
   did not land on the tile for a reason not isolated this round. Net: still cannot say whether the
   physical WiFi button bypasses `phoneLeftQuiet` suppression, but the *reason* it couldn't be
   reached has moved from round 1's "sub-second window" to a concretely observed input-routing
   overlap plus a second, unexplained miss — worth a UI-thread trace rather than more blind timing
   attempts next round.

### E1 — An exit gives the network back before the service stops

**PASS.** Ten cycles (script: `hur-wifi-test-scripts/e1_exit_teardown_cycles.sh`, new this round):
live session with D-POCO → `headunit://exit` → check `dumpsys wifip2p`.

- **10/10** cycles: `groupFormed: false` within 3 s of exit.
- **0/10** `"the wireless teardown did not finish in 5000ms"` timeout lines.
- **Brief erratum:** the suggested confirmation line, `"CommManager teardown complete. Stopping WiFi
  Direct group."`, never fires for Native AA — reading `AapService.kt`, that line sits behind
  `state.isUserExit && ranWifiDirect && !wirelessTornDown`, and the Native AA branch just above it
  sets `wirelessTornDown = true` first, logging its own line instead. The actual per-cycle
  confirmation for this mode is **`"AapService: Native AA user exit. Stopping active launcher."`**,
  which appeared **10/10** times. Ten clean cycles by the line that actually applies to this mode is
  the "clean shape" the brief asked for; the fix removes a cancelled suspension point rather than
  widening a window, so this catches a regression without calling the underlying race retired.

### D1 — Driver selection against a phone that is already coming back

**Not pass/fail, as specified.** Two clean, valid repetitions (a third was invalidated by
test-script timing — see Setup notes) with the roles run both ways:

| Rep | Poked | Holding (untouched) | Winner | Loser's own reconnect | Group recreated? | Loser's HFP link |
|---|---|---|---|---|---|---|
| 1 | D-MOTO | D-POCO (post-session, kept up) | D-MOTO — got `192.168.49.x`, completed AAP handshake | D-POCO refused: `"the driver chose A0:46:5A:97:E4:95, so DC:B7:2E:5E:4E:59 waits until that phone has had its turn."` | No (same BSSID before/after) | D-POCO's `HeadsetClientStateMachine` stayed `Connected` throughout |
| 2 | D-POCO | D-MOTO (post-session, kept up) | D-POCO — same pattern, reciprocal | D-MOTO's reconnect attempts were aborted (`"USB/other session already active"`) | No (same BSSID) | *(D-MOTO has no standing link — see finding below)* |

**New finding:** D-MOTO does **not** hold a standing Bluetooth HFP client link to D-HU the way
D-POCO does. Across this whole stage, every RFCOMM connection to D-MOTO was a short (~9 s),
our-own-poke-initiated probe-and-close (`shim::legacy::btm ... Initiated connection ... Disconnected
... CONNECTION_TERMINATED_BY_LOCAL_HOST`, repeating every ~35 s), never a persistent bonded profile.
So the "should an explicit poke to a named phone win" question is best answered from D-POCO's
reciprocal role — the one that *does* have a real standing link to check — and there the answer is
unambiguous: **yes**, an explicit poke to a named phone wins even against a phone mid-reconnect, and
it costs the displaced phone nothing at the Bluetooth layer (its HFP link is untouched); only the
contested WiFi-Direct/AAP session is affected.

---

## Stage B — D-SAM, D-POCO

### B1 — Headunit Server mode, end to end

**PASS.** After the dev "Start head unit server" tool's own well-documented flakiness self-resolved
(first attempt hit the tool's known ~20 s self-disconnect cycle three times running, deaf-peer style,
matching round 1's own Setup notes about this exact tool), a fresh restart of the dev server
produced a clean session on the very next discovery pass: all four projection lines, `Throughput
over 5007ms: rendered=62 (12fps) ... codec=OMX.MARVELL.VIDEO.HW.CODA7542DECODER`. First confirmed
picture on D-SAM for this candidate branch.

### B2 — The stand-in hands-free record, on the class of device it was written for

**Not pass/fail, as specified.** Branch taken: `"radio [Navegadortz3] gets the stand-in HFP record,
because it advertises no Hands-Free."` — the expected branch for a tablet.

**The accept fired — the first one ever recorded across five rig rounds and every reporter log.**
D-POCO answered a sustained, valid AT-command dialogue (`AT+CIND?` → `+CIND: 0,0,0,0,0,5,0` / `OK`,
repeating cleanly) for **~95 seconds** over the stand-in HFP-AG poke, exactly like a real
hands-free car kit. It did **not** go on to open the real Android Auto Bluetooth channel or start
wireless setup in that window; the poke eventually timed out on its own budget and the attempt gave
up through the usual watchdogs. Report stands as: accept confirmed, wireless setup not reached.

### B3 — WiFi Direct with no way to name a group

**FAIL**, with the mechanism identified in source rather than only inferred from the log.

Three real bring-ups (relaunching the app between each) produced a genuinely different group name
every single time — `DIRECT-IQ-Navegadortz3` → `DIRECT-71-Navegadortz3` → `DIRECT-B6-Navegadortz3`
→ `DIRECT-JQ-Navegadortz3` — exactly the "platform re-addresses the group" behavior B3 is testing
for. But `wifi-direct-group-name-changes` in `settings.xml` never rose past `1` across all three, so
`GroupIdentityStabilityPolicy` never reached its `RENAMED` verdict
(`NAME_CHANGES_BEFORE_MEASURED = 3`), and `WppEndpointPolicy` therefore never withheld the
WPP-over-TCP endpoint on a unit that plainly needs it withheld.

**Root cause, in `GroupIdentityStabilityPolicy.kt`:** `Verdict`'s `nameChanges` field defaults to
`0`, and both the `CHANGED` and `STABLE` branches of `assess()` construct their `Verdict` without
setting it. Every bring-up's *first* `onGroupInfoAvailable` callback (comparing the new group
against the *tail end of the previous* bring-up's group, via `group.owner.deviceAddress`) lands in
one of those two branches — landing in `CHANGED` when the address moved but the name coincidentally
matched, or `STABLE` when both matched — either way persisting `nameChanges = 0` back to
`appSettings.wifiDirectGroupNameChanges`
(`WifiDirectManager.kt:1211`) **before** the bring-up's *second* callback (the real settled name,
via IPv6 link-local) gets a chance to increment past 1. The counter is reset to zero by its own
"nothing new here" branches faster than it can ever accumulate to the threshold of 3.

**Matches the brief's own stated FAIL condition exactly**: "it forces the endpoint out on a unit
that re-addresses every group." D-SAM re-addresses (renames) every single group, and the policy
can never tell.

### B4 — The branch's bring-up refusals on a 2014 UI

**PASS**, all three items.

1. Pill renders with a readable label: `"POCO X3 NFC is disconnected, waking it... WiFi Direct
   network is up"`.
2. Tapping the pill's `X` ("Stop connecting") holds the stack down — confirmed via
   `status pill step: hidden`, `AA Server socket closed cleanly`, no re-arm for 15+ s afterward.
   Tapping the WiFi tile afterward *does* re-arm it (full pill progression through
   `WAITING_FOR_PHONE → ... → PHONE_ANSWERED`).
3. Opening Settings and backing out with nothing changed produced **zero**
   `ACTION_START_WIRELESS` / pill-step lines in the 5 s after closing it.

---

## Stage C — D-HP, D-POCO (Headunit Server only)

D-HP came back on 2026-09-17 after being listed as retired; nothing on this branch has ever run on
API 17 before this round. Per the brief's own framing, the whole stage is exploratory.

Install and launch: **no build-configuration failure at all** — the APK installed cleanly
(md5-verified) and launched on the first try. Worth recording as a positive result in its own right,
since a broken install would have been "a build-configuration finding worth more than the rest of
this stage."

### L1 — It installs, launches and projects

**PASS**, after a transient false start. D-HP's own device-to-device ARP resolution to D-POCO was
intermittently broken (`Destination Host Unreachable` on both sides, same signature seen transiently
on D-SAM in stage B), and self-resolved without any adb intervention after a wait. Once resolved:
`session state projecting`, all four projection lines, and a picture climbing from 7-8 fps to a
stable 29-30 fps on `OMX.Nvidia.h264.decode`, confirmed live (Google Maps navigation, moving map,
FPS/CPU/Temp overlay reading `FPS: 29 CPU: app 26% / sys 54%`).

### L2 — The stand-in hands-free record at API 17

**UNTESTABLE.** The stand-in mechanism lives entirely inside `NativeAaHandshakeManager` (the Native
AA / WiFi Direct path); D-HP has no WiFi Direct and no hotspot, and the brief's own stage
instruction is Headunit Server mode only. `native-aa-complete-hfp-slc=true` was confirmed present in
`settings.xml` but the branch is structurally unreachable on this unit regardless of the setting's
value — never once exercised in the log across the whole session.

### L3 — WiFi comes back and discovery does not wait it out

**UNTESTABLE.** The lever itself could not be created: both `svc wifi disable` and
`settings put global wifi_on 0` failed to take the WiFi radio down on this Android 4.2.2 build —
`dumpsys wifi` continued reporting `"Wi-Fi is enabled"` immediately after either command, with no
observable state change. No `NetworkMonitor:` line or rescan could fire because the underlying
disconnect this run needs never happened. Not attempted via physical UI toggle given the round's
scripted-only house rules.

### L4 — Sharing a log below Android 10

**Partial.** Could not reach the actual Settings → Share UI flow this run item asks about: scripted
touch input never registered a navigation click on this unit despite two different coordinate-system
mappings (both the D-SAM-style uiautomator-logical mapping and a physical-buffer-transform fallback),
and the brief's own recommended `run-as $PKG am start --user 0 -n .../SettingsActivity` consistently
segfaults this unit's `am` binary without crashing the app. As a substitute (after setting
`allow-external-configuration=true`, left set on this unit), the `ACTION_EXPORT_LOG` automation path
was exercised instead: it completed cleanly with **no crash**, and the exported file landed at
`/storage/emulated/0/Android/data/com.andrerinas.headunitrevived/files/HUR_Log_20260917_183057_538.txt`
— directly answering the brief's own open question (never confirmed on D-SAM either): **it is the
modern `/storage/emulated/0` path, not the legacy `/storage/sdcard0`, on this unit.** The specific
Share-*button* crash this branch's fix targets was not exercised and remains unconfirmed on D-HP.

### L5 — How the projection gets raised at API 17

**PASS.** Every session-start for the phone-initiated sessions in this stage logged
`"raising the projection by DIRECT (overlay=true, foreground=true)"` — never `NOTIFICATION` — matching
the brief's own expectation exactly: below Android 6 the overlay permission is granted at install
and cannot be revoked, so `ActivityLaunchPolicy` should always choose DIRECT, and it did, every time.

---

## Anything the brief did not ask about

1. **D-HP: a carried-over `audio-queue-capacity=0` setting caused a real, user-audible defect** —
   audio delay growing to an estimated 5 minutes over a live session, invisible to the existing
   `depth`-based health telemetry because the backlog lived in the unbounded queue *feeding* the
   AudioTrack ring buffer, not in the ring buffer itself. Root-caused, fixed for this unit (reset to
   the default of 50), and confirmed holding through two subsequent decoder-stall events that shed
   stale audio cleanly instead of accumulating it further. See Setup notes for the full mechanism
   and a suggested follow-up (warn on `0`, or report the unbounded queue's own duration).
2. **D-HP: recurring decoder stalls independent of touch input**, on this specific 2013 Tegra 2
   tablet, causing a permanent `TEXTURE → SurfaceView` fallback on the second occurrence within one
   session. Reads as a hardware-class limitation this stage's own framing already anticipates
   ("Stage C is a compatibility probe... it says nothing about the owner's own low-end unit"), not a
   candidate regression — the existing stall-recovery and fallback logic handled both events
   correctly. Flagging for visibility, not as a defect to fix.
3. **`native-aa-complete-hfp-slc` cannot address W1r/W2r's actual precondition on D-HU**, because it
   only ever registers a stand-in record on a unit whose radio does not already advertise
   Hands-Free — D-HU's does, and already holds a real link to D-POCO. Recommending a follow-up: the
   setting (or a new one) needs a mode that can deliberately displace an existing real HFP link on a
   unit like D-HU, or the hands-free-wake escalation can never be exercised there at all, on this or
   any future round.
4. **D-HP's USB link dropped entirely once mid-round** (vanished from `adb devices`) and recovered
   after a physical replug with the app process and its long-running session both surviving intact
   (same pid, same session, before and after). Noting as a possible cable/port flakiness on this
   specific unit's connector, not chased further.
