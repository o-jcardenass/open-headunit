# t230-native-aa-bringup — round 2 results

**Candidate:** `fix/audio-sink-and-wireless-bring-up` @ 5bf505d7a (rebuilt this round, includes
`61143283f`)
**Baseline:** round 1's result (installed v.3.4.0-beta3 @ 8418ed025, pre-`61143283f`) —
`t230-native-aa-bringup-round1-results.md`
**APK md5:** de16ab3541b35257130de8c07dde67b6 (`assembleGithubDebug`)
**Unit:** Samsung SM-T230 (Galaxy Tab 4 7.0, "degaswifi"), Android 4.4.2 (API 19), same unit as
round 1
**Date:** 2026-09-15

## Setup notes

- Built with `hur-wifi-test-scripts/install_and_launch.sh` (`HU=30041c35642d2200`), which wraps
  `build_hur.sh` (`assembleGithubDebug`) and installs+relaunches in one step. This is the right
  tool for "rebuild and retest on a named device" — no new script needed.
- `adb install -r` did **not** wipe `shared_prefs/settings.xml` this time (§7a's own caution about
  this on other units) — `wifi-connection-mode=3`, `native-poke-bt-macs` and
  `last-connected-native-mac` all read back correctly after install. Re-set `log-level=0` (Verbose)
  the same local-edit-and-push way as round 1 (this device still has no `sed`).
- **Battery was still at 9% for this round too** (`USB powered: true`, `AC powered: false` — a
  data-only cable/port is not charging it). Round 1's caveat stands: this has not been re-run on a
  charged unit. Given round 2's actual failure signature turned out to be WiFi-link-layer, not
  power-save-shaped (constant ~5-6s cycle regardless of anything battery-related), it's a weaker
  suspect than it looked after round 1, but still uncontrolled.
- One clean capture, ~112s (11:45:15-11:47:05), `force-stop` → `logcat -c` → stream → `am start`.
  Full capture: `evidence/t230-native-aa-bringup-round2/hu_native_aa_attempt2_logcat.txt` (10847
  lines).
- A `NullPointerException` in a background finalizer thread (same shape as round 1's) appears twice
  more this round, always right at the point a fresh `AapTransport` starts — still looks like
  harmless GC noise (no stack trace naming app code), not chased further.

## R1 — Native AA bring-up on rebuilt branch tip, D-POCO already bonded

**FAIL** (but materially further than round 1 — see below)

- Settings written: `log-level=0` (int) only; connection settings survived the reinstall.
- Radio state: D-POCO Bluetooth on, already bonded; screen on throughout.
- **Round 1's signature #1 (group torn down and recreated) is gone.** One `createGroup SUCCESS` and
  one `P2P_GROUP_REMOVE` for the *entire* 112s capture (vs. 2 and 2 in round 1's 150s). Every one of
  15 credential-refresh cycles logged `refresh: the group DIRECT-pE-Navegadortz3 is up, so its
  credentials are read again rather than the group remade` — the exact behavior `61143283f` was
  written to produce, confirmed on real (and previously failing) hardware.
- **A new, different failure signature replaces it.** The app now reaches full AAP session
  negotiation repeatedly — SSL handshake, `ServiceDiscoveryRequest`/`Response`, every channel opened
  (VIDEO, INPUT, SENSOR, AUDIO/AUDIO1/AUDIO2, MIC, BLUETOOTH, MUSIC_PLAYBACK,
  NAVIGATION_DIRECTIONS), sensor start, `AudioTrackWrapper` created for AUDIO2 — then the
  **WiFi-Direct TCP socket itself breaks with `EPIPE`**, roughly 3.5s after each SSL handshake
  completes, always mid-negotiation (never once reaching a rendered video frame; `OMXClient` never
  appears in the whole capture). Decisive lines, quoted:
  ```
  11:45:30.303 Handshake: SSL handshake complete. TS: 880054293
  11:45:30.703 Companion.decrypt | RECV: CONTROL Service Discovery Request ...
  11:45:31.094 RECV: MUSIC_PLAYBACK Channel Open Request ... / MUSIC_PLAYBACK Channel Open Response
  11:45:33.796 AapAudio.startAudioTrack | AudioDecoder.start: channel=5 ...
  11:45:33.836 SocketProjectionConnection.handleBrokenTransport | Socket broken (server disconnected ungracefully?)
  11:45:33.836 java.net.SocketException: sendto failed: EPIPE (Broken pipe)
      at com.andrerinas.openheadunit.connection.projection.SocketProjectionConnection.sendBlocking(SocketProjectionConnection.kt:221)
  11:45:33.846 AapReadSingleMessage.doRead | AapRead: Connection closed (EOF). Disconnecting.
  11:45:33.846 AapService: session state disconnected (link_lost)
  ```
  The whole cycle (BT wake poke → HFP SLC → Type1-3 handshake → TCP accept → SSL handshake → full
  channel negotiation → EPIPE → `link_lost`) then **repeats every ~5-6s, 13 times** across the
  capture (13x `Incoming connection detected`, 13x `session state disconnected (link_lost)`), never
  once stabilizing long enough to render a frame.
- **This is a WiFi data-link problem, not a Bluetooth one.** Only the *first* of the 13 breaks
  (11:45:33.786) lines up with a Bluetooth `ACL_DISCONNECTED` for the phone; the other 12 breaks
  (11:46:09 through 11:47:04, every ~5-6s) have **no** corresponding BT ACL event anywhere nearby —
  the BT link is quiet and stable for the rest of the capture (only 4 ACL events total across
  112s). The thing dying on a ~5-6s cadence is the WiFi-Direct TCP session (port 5288) carrying the
  AAP data, independent of Bluetooth.
- `WifiDirectManager: "SUCCESS - Providing credentials"` fires once per refresh throughout (15x),
  always for the same never-recreated SSID (`DIRECT-pE-Navegadortz3`) — confirms the group itself
  stayed up and stable the whole time; it's specifically the phone's *association* to it that keeps
  dropping.

`SocketProjectionConnection.handleBrokenTransport`'s own log line — "server disconnected
ungracefully" — says the write failed because the far end (the phone) closed or dropped the
connection, not something this app tore down. Leading hypothesis, unverified: the phone's own WiFi
client periodically re-scans or re-evaluates its P2P association (a mechanism this repo's own
[[project_periodic_link_stall_investigation]]-shaped work has looked at before, albeit for a
different symptom) and that disrupts an active association to this specific tablet's GO faster than
it would for other head units on this rig — possibly a signal-quality or driver-compatibility
difference specific to this budget tablet's WiFi chip. Not confirmed from this capture alone.

## Anything the brief did not ask about

- This round answers the "next round" recommendation from round 1's results: it confirms
  `61143283f` (already merged to this branch) does fix this device's group-churn signature. That
  part of round 1's open question is closed.
- The remaining blocker is new and more specific than round 1's framing suggested: it is not a
  group-lifetime or band/channel mismatch problem (round 1's `WIFI_NETWORK_UNAVAILABLE` hypothesis)
  — the phone *does* associate and get all the way through full AAP channel negotiation every single
  time. It just can't hold the link past ~3.5s of real two-way traffic. A DFS/band hypothesis from
  round 1 is now a weaker fit; a periodic phone-side WiFi re-scan or an RF/antenna limitation on
  this specific tablet's WiFi chip is the better-supported explanation given the ~5-6s regularity
  and the BT link's own stability.
- Next round should: capture the phone's own WiFi logs (`adb -s <POCO> logcat` filtered on
  `wpa_supplicant`/`WifiP2pService`) in parallel with the head unit's, to see what the phone thinks
  is happening to the association at the moment of each EPIPE; and re-run once the tablet is
  charged, to rule that variable out for good.
