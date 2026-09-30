# Audio focus — round 9 results

**Candidate:** `fix/744-call-audio-hfp` @ `d64d7802`, stacked on `fix/audio-focus-pauses-bt-source`
@ `26032e65`       **Baseline:** none (control is a one-key pref write on the candidate, per brief)
**APK md5:** `9e0f9dee4259ccbd150bf485cbf37316`, confirmed identical to the installed package.
**Unit:** headunit `27870808938846` (UNISOC MT50_YT610E4GFPSL_U, Android 14) / phone `4f4027e9`
(Redmi M2007J20CG, POCO X3 NFC)
**Date:** 2026-08-09

## Setup notes

- History reset per the brief: `git fetch fork --prune --prune-tags` then
  `git checkout -B fix/744-call-audio-hfp fork/fix/744-call-audio-hfp`. `git log --oneline -4`
  showed exactly `d64d7802, 26032e65, a2381b46, d2dff1df` as the brief predicted.
- **Blocking rig problem found before any run could start: the phone and head unit were not
  Bluetooth-paired.** `dumpsys bluetooth_manager`'s bonded-device lists on both sides showed only an
  unrelated speaker ("Magnetic Speaker") — no entry for each other. The head unit's own Bluetooth
  event log showed a bond removal on the phone's address followed by a failed re-pair attempt the
  day before (2026-08-08 17:13, `bond_state_changed BOND_STATE_BONDING` → `BOND_STATE_NONE` within
  1 s), never restored. Native AA cannot do anything without this bond, so the whole round was
  blocked. This is not something adb-only tooling can fix without a UI confirmation step, so re-paired
  manually (user did it directly, not scripted) before continuing. Confirmed after: both sides list
  each other bonded (`DC:B7:2E:5E:4E:59 [ DUAL ] POCO X3 NFC` on the head unit,
  `XX:XX:XX:XX:33:59 [ DUAL ] Navegadortz2` on the phone), A2DP `A2dpSinkService` showed
  `Active Device = XX:XX:XX:XX:4E:59`, state `Connected`. Flagging this for anyone touching this rig's
  Bluetooth state in future rounds — worth a "confirm bonded, not just BT-on" check before assuming
  §7a's "bring the phone's Bluetooth up once" step is sufficient.
- **The phone's own reconnect beats the poke on this rig whenever it has recently seen the car.**
  R1's very first attempt found the phone reconnecting and completing a full SSL handshake within
  ~3-6 s of the head unit's listeners opening, via a path that involves neither `NativeAaHandshakeManager`'s
  poke (which logged "handshake already in flight, not restarting" and never called
  `socket.connect()`) nor the visible Gearhead process (force-stopping
  `com.google.android.projection.gearhead` and deleting the head unit's own persistent P2P group via
  `cmd wifip2p delete-saved-group` made no difference — reconnect was still ~5-6 s). No saved regular
  WiFi network for the P2P SSID existed on the phone (`cmd wifi list-networks` clean), and the phone's
  own `cmd wifip2p` shell is blocked by `SecurityException: Uid 2000 does not have access` (no root on
  the phone), so its P2P-layer cache could not be inspected or cleared directly. **The one technique
  that reliably forced the poke to actually run: turn the phone's Bluetooth off, launch the head unit
  app so its RFCOMM listeners and P2P group come up while the phone is still unreachable, wait ~8 s,
  then turn the phone's Bluetooth back on.** Used for R2 and R5. Worth carrying forward as the
  standard "force a poke-dependent connect" recipe for this rig in future rounds. This is a
  *behavioral* finding rather than a directory inventory, so it's captured here rather than in
  `code-researchs/hur-wifi-test-scripts-inventory.md` — a future brief should reference this file.
- Scripts used: `run_unit_tests.sh`, `build_hur.sh`, `install_and_launch.sh` (`SKIP_BUILD=1`), all
  unchanged. Settings written with a **new script, `set_hu_prefs.sh`**, added to
  `hur-wifi-test-scripts/` this round — a multi-key sibling of the existing `set_hu_pref.sh` that
  writes/deletes several keys in one pass on this (rooted) head unit without relaunching after every
  single key, unlike `set_hu_pref.sh` which relaunches on every call and so is unsafe to stack for a
  run needing several keys set together atomically (each intermediate relaunch would start
  `AapService` on a partial settings set). Full writeup of both scripts, plus an inventory of the rest
  of `hur-wifi-test-scripts/`, is now at
  `code-researchs/hur-wifi-test-scripts-inventory.md` so a future round does not have to
  rediscover the directory from scratch.
- `bluetooth-wake-mode` written as an `int` element per the brief's table throughout; `del` used for
  R5 (element removed entirely, confirmed absent by reading the file back, not just set to `0`).
- Log level DEBUG (`log-level=1`) throughout R1/R2/R5 per brief §3.
- **A safe number to call became available mid-round (the coordinator's own second line), so R3 ran
  a real call** rather than being marked UNTESTABLE as first drafted. R4 still did not run, but for a
  different reason than "no safe number" — see R4 below.

## R0 — build gate

**PASS.** `PlaybackFocusPolicyTest`: 20/20 green (`tests="20" failures="0" errors="0"` in the JUnit
XML). `BluetoothWakePolicyTest`: 9/9 green (`tests="9" failures="0" errors="0"`), present as the
brief predicted. Full `testGithubDebugUnitTest` suite: 219/219 tests green across 25 test classes,
`failures="0" errors="0"` summed from every `TEST-*.xml`, `BUILD SUCCESSFUL`. APK md5
`9e0f9dee4259ccbd150bf485cbf37316`, confirmed identical to
`/data/app/~~.../com.andrerinas.headunitrevived-.../base.apk` on the head unit. This is the first
compile this branch has had anywhere (fork branch pushes don't trigger CI) — it compiled clean.

## R1 — does the phone attach its hands-free link to us at all?

**Measurement, no PASS/FAIL, per brief.** Settings: `wifi-connection-mode=3`,
`bluetooth-wake-mode` absent (default 0), `log-level=1`.

- Launched with a pre-existing P2P group still on disk (not yet cleared — this was the very first
  run of the round, before the fast-reconnect behavior above was understood). Group reused, SSL
  handshake complete at `12:53:14.054`, ~3.5 s after listeners opened at `12:53:10.568`.
- At `12:53:36.209` cycled the **phone's** Bluetooth off (`svc bluetooth disable`), confirmed
  `bluetooth_on` read back `0`, then on again at `12:53:41.333`. Watched 60 s (window ended
  `12:54:41.390`).
- **`HFP connection accepted` did not appear anywhere in the window, and neither did `HFP responder
  active` or any `HFP RX:` line.** `grep -c` across the full capture: 0 for all three.
- The AA WiFi/TCP session itself was undisturbed by the Bluetooth cycle: one `createGroup SUCCESS`,
  one `Incoming connection detected`, one SSL handshake (the two `grep` hits for "SSL handshake
  complete" are the same event logged from two classes, confirmed by line numbers 5s apart in the
  same second — not two handshakes). Classic ACL went `state:3→0` at `12:53:36.671` and
  `state:1→2` (connecting→connected) at `12:53:42.287`, consistent with a normal BT off/on cycle;
  AVRCP/media resumed automatically afterward.
- **Answer: no accept, across one clean Bluetooth-connect window.** Consistent with the brief's own
  framing that this "closes the question for now" rather than proving the stub record is never
  reachable — only this one window was tested.

## R2 — the setting keeps the poke off the hands-free record

**PASS.** Settings: `wifi-connection-mode=3`, `bluetooth-wake-mode=1`, `log-level=1`. Forced a
poke-dependent connect via the phone-Bluetooth-off recipe (Setup notes): phone BT off, head unit's
persistent P2P group deleted (`cmd wifip2p delete-saved-group 0 true`), app launched with phone BT
still off, waited 8 s, then phone BT back on.

- **No poke line of any kind named HFP-AG or `0000111f` anywhere in the capture** —
  `grep -c -iE "0000111f|HFP-AG"` on the full run: 0.
- **The single poke attempt named HSP-AG only:**
  ```
  13:05:21.283  NativeAA: Calling socket.connect() for POCO X3 NFC via HSP-AG (00001112-...)...
  13:05:27.318  NativeAA: Poke via HSP-AG to POCO X3 NFC (...) failed: read failed, socket might closed or timeout, read ret: -1
  ```
- **Which target succeeded: neither — the HSP-AG poke failed**, same failure text and similar ~6 s
  timeout as the reporter's own log excerpt in the brief. On this rig HSP-AG does **not** connect
  either, unlike the brief's framing that expected this rig might differ from the reporter's by
  having HSP-AG succeed. It did not.
- **Android Auto still connected anyway**, via the phone's own reconnect (Setup notes) rather than
  a successful poke: `Incoming connection detected` at `13:05:30.119`, SSL handshake complete at
  `13:05:30.335`, ~3.04 s after the phone's Bluetooth was re-enabled at `13:05:27.294`.
- Discard-rule check: one `createGroup SUCCESS`, one P2P interface (`p2p-wlan0-5`, no bump within
  this capture), no `MATCH! Starting AapService`, no `Magic Garbage`, one handshake. Clean.

**Session did not fail to form** — the important negative result the brief called out (HFP-AG being
load-bearing for the wake, meaning the default must never change) did not happen here.

## R3 — baseline call at the defaults

**PASS (audio came out of the car; rig does not reproduce the fault at the defaults).** Settings:
`wifi-connection-mode=3`, `bluetooth-wake-mode` absent (default 0), `log-level=1`. AA session live
and projecting (Spotify channel open from the earlier setup), then a real call was placed to the
connected phone (POCO X3 NFC) from a number judged safe to call, answered, held, hung up.

- **Where the audio came out: the car.** Checked live, mid-call, via `adb shell dumpsys audio` on the
  head unit:
  ```
  Audio mode:
    - Mode: MODE_IN_CALL
    Active communication device: AudioDeviceAttributes: role:output type:speaker addr: name:MT50_YT610E4GFPSL_U ...
  ```
  The active communication device is the head unit's own built-in speaker — not the phone.
- **The call routed through the head unit's own native OS-level Bluetooth HFP client
  (`com.android.bluetooth.hfpclient.HfpClientConnectionService`), a completely separate mechanism
  from HUR's Android Auto WiFi session and from the stub `0000111e` hands-free record this round's
  `bluetooth-wake-mode` setting targets.** This is a distinct, always-on classic-Bluetooth profile
  connection between phone and head unit; it exists independent of whether Native AA is even
  connected. Call lifecycle, from the head unit's own `Telecom`:
  ```
  13:15:40.277  CallsManager: setCallState NEW -> RINGING, call: [Call id=TC@2, ... tpac=...HfpClientConnectionService, handle=tel:...]
  13:15:43.913  CallsManager: setCallState RINGING -> ACTIVE, call: [Call id=TC@2, ...]
  13:17:05.214  Audio mode back to MODE_NORMAL, active Telecom call count 0 (hangup)
  ```
  Total call duration ~81 s (longer than the brief's suggested ~15 s hold — a live human call, not
  scripted timing).
- **`HFP connection accepted` / `HFP responder active` / `HFP RX:` — zero occurrences anywhere in the
  capture** (`grep -c` = 0). Confirms the stub hands-free record HUR itself publishes was not what
  carried this call; a completely separate, standard Android Bluetooth HFP client connection did.
- After hangup, AA media (the Spotify channel, `AapAudio: AA audio started (AUDIO) - leaving system
  audio focus alone (mode=AUTO, bluetoothMedia=true, latched=false)`) resumed automatically at
  `13:17:05.540` with no manual intervention — consistent with round 8's fix.
- Also noted: a first, much shorter call (`Call id=TC@1`) rang and went active within ~1.2 s
  (`13:14:46.583` → `13:14:47.737`) before this main run — not analyzed for audio routing, superseded
  by TC@2.
- **Per the brief: since the audio came out of the car at the defaults, this rig does not reproduce
  the reported fault (#744: calls heard on the phone instead of the car).** The mechanism the
  reporter describes is specific to their hardware/software combination, not general to Native AA +
  this candidate branch. Per the brief's own instruction, R4 is **not** run on this basis — see below.

## R4 — the same call with the setting on

**INCONCLUSIVE, not run — per the brief's own gate.** Brief: "Only if R3's audio came out of the
phone." R3's audio came out of the car, so R4 does not apply here; running it would test nothing the
brief asked for. This is the brief's own precedent from round 8-style gating, not a rig limitation —
the call infrastructure worked fine (R3 proves that), the *precondition* for running R4 simply wasn't
met.

## R5 — the default is a no-op

**PASS.** Settings: `wifi-connection-mode=3`, `bluetooth-wake-mode` **deleted from settings.xml**
(confirmed absent by reading the file back — not present, not set to `0`), `log-level=1`. Forced a
poke-dependent connect the same way as R2.

- **Poke order preserved: HFP-AG tried first, then HSP-AG**, both while the phone's Bluetooth was
  still off (both fail immediately for that reason — not a target-connect measurement, just an
  order check):
  ```
  13:08:27.828  NativeAA: Calling socket.connect() for POCO X3 NFC via HFP-AG (0000111f-...)...
  13:08:32.863  NativeAA: Poke via HFP-AG ... failed: read failed, socket might closed or timeout, read ret: -1
  13:08:32.869  NativeAA: Calling socket.connect() for POCO X3 NFC via HSP-AG (00001112-...)...
  13:08:33.266  NativeAA: Poke via HSP-AG ... failed: read failed, socket might closed or timeout, read ret: -1
  ```
- Session formed after phone BT came back on: `Incoming connection detected` and SSL handshake
  complete both at `13:08:42.5xx`, ~9 s after phone Bluetooth re-enabled at `13:08:33.740` (this
  reconnect took longer than R2's ~3 s; not investigated further, both are well inside the 90 s
  clean-run window).
- Round 8's R1 decisive line, byte-for-byte: `AapAudio: AA audio started (AUDIO) - leaving system
  audio focus alone (mode=AUTO, bluetoothMedia=true, latched=false)` at `13:09:39.987`, after
  driving Spotify to `PLAYING` via `adb shell input keyevent 126` on the head unit (relayed to the
  phone over AAP) — Spotify was force-stopped and relaunched first to guarantee a fresh channel per
  §7a.
- **No AVRCP PAUSE (opcode 70) in the 60+ s window following the audio-start line** (window measured
  13:09:39.987 to 13:10:54.403, ~74 s): `grep -c` for the pause pattern in that span = 0.
  `btavrcp_play_position_changed_callback` fired 42 times over the same span with no gaps —
  continuous playback, not an absence-of-pause inference.
- No `AA audio channel stopped` anywhere in the span.

## Anything the brief did not ask about

- **Call audio on this rig does not route through anything HUR controls at all.** R3 found the whole
  call handled by the head unit's own stock Android Bluetooth HFP *client* profile
  (`com.android.bluetooth.hfpclient.HfpClientConnectionService`) connecting to the phone as an audio
  gateway — a standard OS-level classic-Bluetooth mechanism that exists whether or not Native AA is
  even running, and is entirely distinct from both AAP's `AUDIO`/`AUDIO1`/`AUDIO2` channels and from
  the stub `0000111e` hands-free record `NativeAaHandshakeManager` publishes. This means: (a) on this
  rig, `bluetooth-wake-mode` has no visible lever over where call audio goes, because the poke targets
  are irrelevant to a mechanism that isn't the poke's job to control; (b) issue #744's "screen says
  Bluetooth is not connected while phone says it is, audio comes out of the phone" symptom, if it's
  real, most likely means *this specific HFP client connection* is failing to form or stay up on the
  reporter's unit — worth checking whether their head unit even *has* a working HFP client profile,
  independent of anything in this app. This reframes the round's whole premise: the fix under test
  changes what HUR's own poke targets, but the actual call-audio path this round measured is a layer
  below anything HUR's code touches.
- **The Bluetooth-pairing loss described in Setup notes is the most consequential finding of the
  round**, even though it isn't part of the brief's own mechanism. If a future round's phone/head
  unit come up unpaired again, check `dumpsys bluetooth_manager`'s bonded-device list on *both*
  sides before assuming §7a's "bring the phone's Bluetooth up once" is enough — "Bluetooth is on"
  and "Bluetooth is paired with the head unit" are different facts, and only the second one matters
  for Native AA.
- **On this rig, in the state most rounds will find it (recently paired, phone has seen the car
  before), the poke mechanism this whole round is about essentially never gets exercised in a normal
  reconnect** — the phone's own background reconnect wins the race in 3-6 s regardless of
  `bluetooth-wake-mode`, `Gearhead` process state, or the head unit's persistent P2P group. The only
  way found to force a poke-dependent connect was to have the phone's Bluetooth fully off while the
  head unit's listeners come up, then turn it back on — see Setup notes' recipe. A future brief
  wanting to observe poke behavior on this rig should build that into its run setup rather than a
  plain "force-stop and relaunch."
- `set_hu_prefs.sh` (new script, Setup notes) and `code-researchs/hur-wifi-test-scripts-inventory.md`
  (new persistent reference doc, not on this branch) are both left in place for future rounds.
