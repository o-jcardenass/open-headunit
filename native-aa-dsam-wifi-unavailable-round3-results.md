# native-aa-dsam-wifi-unavailable — round 3 results

**Candidate:** `fix/native-aa-withdrawn-network` @ `675a0827e88987f5e044790c8cf005867927933e`
**Baseline:** round 2's cycle C2 (not re-run) — `native-aa-dsam-wifi-unavailable-round2-results.md`
**APK md5:** `055db51d5a20b45bf449063ac06253e7` (`com.andrerinas.headunitrevived_3.5.0-alpha_debug.apk`, versionCode 113)
**Unit:** D-SAM (Samsung SM-T230, Android 4.4.2 / API 19, 2.4GHz-only WiFi Direct band, not rooted) as head unit; D-POCO (POCO X3 NFC, Android 15, Gearhead `17.5.663204`) as phone; D-HU (UNISOC MT50, Android 14) for R2 only
**Date:** 2026-09-24

## Setup notes

- **Build denied by this session's own auto-mode classifier again**, same as round 2
  (`./gradlew assembleGithubDebug` via `build_hur.sh` flagged "Interfere With Workloads" /
  "Modify Shared Resources"). Handed the operator a self-contained script; they ran `build_hur.sh`
  directly and it built cleanly on the first try. Not a rig limitation.
- Clock check at round start: host `16:39:16 -05`, D-SAM `16:39:07 COT` (~9s behind, coarse),
  D-POCO `16:39:16 -05` (matches host, no skew). For grading, a precise per-cycle skew was computed
  from 9 matched D-SAM `[TX] Wrote TYPE 3` / D-POCO `Info response received` pairs across C2-C5:
  **8.514s-8.558s, a 44ms spread** — this precise value was used for every cross-device delta below,
  not the coarse ~9s.
- Settings recorded on D-SAM, app stopped: `wifi-connection-mode=3`, `wifi-direct-band=2`,
  `native-poke-bt-macs=[DC:B7:2E:5E:4E:59]` (D-POCO's MAC — present this round; round 2 found this
  set empty on the same unit, not something this round changed).
- **D-SAM's `logcat -c` still does not reliably clear its ring buffer** (round 2's finding holds).
  Every cycle's D-SAM capture carried forward every earlier cycle's `Client list empty` line. This
  produced a false positive in the automation script's own naive hit-counter: it flagged cycle 1 as
  a "hit" solely because a stale line from 37s before `C1-start` was sitting in the file. Verified
  cycle 1 genuinely has **zero** hits — zero `[TX] Wrote TYPE 3` lines appear anywhere in its window,
  and the app's own log explains why: `NativeAA: the phone's session landed while Type 3 was pending,
  so no credentials are sent` — cycle 1 succeeded through a fast reconnect path that never reaches
  the code under test. Every hit and cycle below is graded from marker-anchored extraction
  (`C<N>-start`/`C<N>-end`), never a raw whole-file grep.
- One run at a time: this session ran R1 solo with no concurrent script instance, so round 2's
  racing-script corruption did not recur.
- R2 was run after R1 finished, once D-HU was connected mid-round at the user's offer — a single
  cycle, not the full R1 loop.
- Round 3's stop rule (≥5 hits or 12 cycles) was satisfied at exactly 5 hits by the end of cycle 5
  (2 hits in C2, 1 each in C3/C4/C5).
- Scripts used: `hur-wifi-test-scripts/build_hur.sh` (via the operator) and `install_and_launch.sh`
  (`SKIP_BUILD=1`) unmodified. The connect-cycle loop and the R2 control cycle were new scripts
  (`~/rig-round3-scripts/partB_cycles_r3.sh`, `r2_control_cycle.sh`), adapted from round 2's
  `partB_cycles.sh` for this round's stop rule and candidate SHA; left in place alongside it.

## R0 — Identity

**PASS**

- `ACTION_QUERY_STATE` → `"commit":"675a0827e889"`, matching the candidate SHA's short form.
- `git status --short` clean of tracked changes at build time — no `-dirty` suffix.

## R1 — Connect cycles

**PASS**

- Settings: unchanged, see Setup notes.
- Discard-rule check: clean, no re-run.
- Per-hit decisive lines (D-SAM clock; deltas skew-corrected where cross-device):

  | Hit | Drop (`Client list empty`) | `DISCONNECTED` broadcast | `(platform took it down)` Δ | `taken down while joining` Δ | `Connection accepted` Δ | Phone's last old-SSID try Δ (skew-corr.) | `PT12S`/`PT17S` for old SSID? |
  |---|---|---|---|---|---|---|---|
  | C2 h1 | 16:41:14.620 | 16:41:16.602 | 0.020s | 0.080s | 5.205s | -0.644s | no |
  | C2 h2 | 16:41:30.385 | 16:41:33.348 | 0.020s | 0.240s | 2.582s | -0.234s | no |
  | C3 h1 | 16:42:56.109 | 16:42:58.061 | 0.000s | 0.100s | 2.762s | +0.584s | no |
  | C4 h1 | 16:44:20.911 | 16:44:23.874 | 0.010s | 0.190s | 2.953s | -0.324s | no |
  | C5 h1 | 16:45:37.526 | 16:45:40.439 | 0.010s | 0.250s | 5.675s | -0.224s | no |

  All 5 hits meet all four PASS conditions (thresholds 0.2s / 1s / 20s / 5s respectively), with wide
  margin: worst-case platform-line lag 0.02s, worst-case handshake-failed lag 0.25s, worst-case
  reconnect 5.68s, worst-case old-SSID-abandon skew-corrected delta 0.65s absolute. `PT12S`/`PT17S`
  never appear anywhere in any of the 5 captures — only `Failed to find network within PT7S`
  (1-3 times per cycle), timed essentially coincident with the drop itself rather than after it.

- Per-cycle checks:

  | Cycle | Hit count | Dead-SSID resend? | `(platform took it down)` after `ACTION_DISCONNECT`? | `-11`/`UNAVAILABLE` count | `C<N>-start` → final `SSL handshake complete` |
  |---|---|---|---|---|---|
  | C1 | 0 (stale line predates start; see Setup notes) | no | no | 0 | 15.035s |
  | C2 | 2 | no | no | 0 | 49.889s |
  | C3 | 1 | no | no | 0 | 33.503s |
  | C4 | 1 | no | no | 0 | 24.524s |
  | C5 | 1 | no | no | 0 | 27.376s |

  The plain `sending p2p connection changed broadcast: DISCONNECTED` line does appear after every
  `ACTION_DISCONNECT` (expected — the app's own exit removes its own group); the disqualifying
  `(the platform took it down)` line does not, in any cycle.

- First hit (C2 h1), D-SAM verbatim from `Client list empty` through `Connection accepted from`:

  ```
  09-24 16:41:14.620   563   683 D WifiP2pService: Client list empty, remove non-persistent p2p group
  09-24 16:41:16.451   563   683 D WifiP2pService: GroupCreatedState ap sta disconnected
  09-24 16:41:16.451   563   683 D WifiP2pService: GroupCreatedState{ what=147486 }
  09-24 16:41:16.451   563   683 D WifiP2pService: GroupCreatedState group removed
  09-24 16:41:16.562   563   683 D WifiP2pService: Stopped Dhcp server
  09-24 16:41:16.602   563   683 D WifiP2pService: =========== Exit GroupCreatedState
  09-24 16:41:16.602   563   683 D WifiP2pService: sending p2p connection changed broadcast: DISCONNECTED
  09-24 16:41:16.612   563   683 D WifiP2pService: InactiveState
  09-24 16:41:16.622 18293 18293 I OPENHU  : [1] WifiDirectManager.invalidateNativeGroupCredentials | WifiDirectManager: the group is being removed (the platform took it down), so its credentials are no longer handed out.
  09-24 16:41:16.702 18293 18367 W OPENHU  : [1043] 2.invokeSuspend$tick | NativeAA: the network the phone was sent was taken down while it was joining, so this handshake ends now and the phone is woken for the new one.
  09-24 16:41:16.702 18293 18367 W OPENHU  : [1043] 2.invokeSuspend$runAction | NativeAA: Handshake failed — the network the phone was sent was taken down while it was joining.
  09-24 16:41:16.702 18293 18367 D OPENHU  : [1043] 2.invokeSuspend$feed | NativeAA: Handshake stage SETTLING -> FAILED
  09-24 16:41:16.702 18293 18362 D OPENHU  : [1042] 2.invokeSuspend | NativeAA: wake poke starting (listeners ready after 0ms).
  09-24 16:41:16.712 18293 18367 I OPENHU  : [1043] 2.invokeSuspend | NativeAA: BT Handshake link closed.
  09-24 16:41:16.722 18293 18362 I OPENHU  : [1042] 2.invokeSuspend | NativeAA: WiFi credentials not ready before poke. Requesting WiFi refresh...
  09-24 16:41:16.722 18293 18293 I OPENHU  : [1] WifiDirectManager.refreshNativeCredentials$lambda$26 | WifiDirectManager: refresh: no group is up, so one is created.
  09-24 16:41:16.722 18293 18293 I OPENHU  : [1] WifiDirectManager.startNativeAaQuietHost | WifiDirectManager: startNativeAaQuietHost() requested. Removing old group if any...
  09-24 16:41:16.732 18293 18293 I OPENHU  : [1] WifiDirectManager.chooseNativeGroupIdentity | WifiDirectManager: group identity: asking for the kept network DIRECT-90-HeadUnit again, so a phone that saved it can rejoin without being set up for a new one.
  09-24 16:41:16.732 18293 18293 I OPENHU  : [1] 1.onFailure | WifiDirectManager: Native AA removeGroup before recreate failed (reason=BUSY (System is busy, retry needed)); expected if no group existed
  09-24 16:41:17.242 18293 18293 I OPENHU  : [1] WifiDirectManager.createQuietGroup | WifiDirectManager: Attempting createGroup for Native AA (Attempt 0)...
  09-24 16:41:17.242 18293 18293 I OPENHU  : [1] WifiDirectManager.createQuietGroup | WifiDirectManager: Band preference is 2.4 GHz only, set by the user.
  09-24 16:41:17.242 18293 18293 W OPENHU  : [1] WifiDirectManager.createQuietGroup | WifiDirectManager: every operating channel this unit was offered (6 (2437 MHz)) has been tried, so the band goes back to being the driver's choice.
  09-24 16:41:17.683 18293 18293 I OPENHU  : [1] WifiDirectManager.onStandardCreateSucceeded | WifiDirectManager: Standard createGroup SUCCESS!
  09-24 16:41:17.793   563   683 D WifiP2pService: sending p2p connection changed broadcast: CONNECTED
  09-24 16:41:17.873 18293 18293 I OPENHU  : [1] WifiDirectManager.onGroupInfoAvailable | WifiDirectManager: group identity ssid=DIRECT-NU-Navegadortz3 persistent=no (temporary) asked=framework profile bssid=E6:58:E7:0E:DE:1E address=generated stable=no (the platform names it) nameChanges=88/3 (this platform names the group itself and has picked a different name on 88 creates, so the kept identity cannot apply here and no setting reaches it) source=IPv6 link-local
  09-24 16:41:17.873 18293 18406 I OPENHU  : [1047] WifiDirectManager.onGroupInfoAvailable$lambda$12 | WifiDirectManager: SUCCESS - Providing credentials to listener. SSID=DIRECT-NU-Navegadortz3, IP=192.168.49.1, BSSID=E6:58:E7:0E:DE:1E, identity stable=no (the platform names it)
  09-24 16:41:17.873 18293 18406 I OPENHU  : [1047] NativeAaHandshakeManager.updateWifiCredentials | NativeAA: Credentials updated. SSID=DIRECT-NU-Navegadortz3, IP=192.168.49.1, BSSID=E6:58:E7:0E:DE:1E, identity stable=no (the platform names it)
  09-24 16:41:17.933 18293 18329 I OPENHU  : [1035] 2.invokeSuspend | NativeAA: Attempting active poke to device: POCO X3 NFC (DC:B7:2E:5E:4E:59)...
  09-24 16:41:17.953 18293 18329 I OPENHU  : [1035] NativeAaHandshakeManager.pokeDevice | NativeAA: Calling socket.connect() for POCO X3 NFC via HFP-AG (0000111f-0000-1000-8000-00805f9b34fb)...
  09-24 16:41:21.576 18293 18329 I OPENHU  : [1035] NativeAaHandshakeManager.pokeDevice | NativeAA: Successfully poked POCO X3 NFC via HFP-AG. Holding 15000ms...
  09-24 16:41:21.807 18293 18322 I OPENHU  : [1029] 1.invokeSuspend | NativeAA: Connection accepted from POCO X3 NFC (DC:B7:2E:5E:4E:59) on local radio [Navegadortz3]
  ```

  (Trimmed of repeated `WifiP2pService: GroupCreatedState{ what=... }` state-machine churn lines and
  duplicate credential-broadcast fan-out to multiple listeners, which carry no additional information;
  full raw capture is in the uploaded asset.)

## R2 — Optional control on D-HU

**PASS**

- Single cycle: `R2-start` 16:52:24.573, `SSL handshake complete` 16:52:35.494 (~10.9s), 20s
  projection, `R2-end` 16:52:55.595, clean `ACTION_DISCONNECT`.
- `grep -c "platform took it down" dhu_r2.logcat` → 0; `grep -c "taken down while it was joining"
  dhu_r2.logcat` → 0. The new mechanism never fires anywhere in the capture, as expected — D-HU's
  Android 14 platform does not tear the P2P group down on client-list-empty the way D-SAM's does.

## Round verdict: PASS

Every graded hit (5/5) and every cycle (5/5) PASSes per the brief's §7 rule. R2's control also
PASSes. The round's own stop rule (≥5 hits or 12 cycles) was reached cleanly at cycle 5.

## Anything the brief did not ask about

- The brief's stop condition anticipated the phone's join reaching a `PT12S`/`PT17S` timeout before
  the fix's recovery could beat it. This round's recovery is fast enough (sub-second platform-line
  lag, single-digit-second reconnects) that the phone's own `PT7S` timer fires essentially coincident
  with the drop itself, and `PT12S`/`PT17S` never appear in any of the 5 captures. A stronger result
  than the brief's own worst-case premise, not a gap.
- Cycle 1's zero-hit path is new information worth keeping: `NativeAA: the phone's session landed
  while Type 3 was pending, so no credentials are sent` — a fast reconnect path that bypasses Type 3
  entirely and therefore never reaches the code under test. A cycle can legitimately show 0 hits for
  this reason, not just because the trigger didn't fire.
- Cycle 2 had two genuine hits back-to-back (two handshake attempts, each killed and recovered within
  ~17s of each other) — the first time in this thread's testing that multiple real hits landed inside
  one cycle window under the *new* detection (round 2's C7 had 4 hits in one cycle, but under the old,
  unreliable incidental-refresh detection). Both of C2's hits here recovered cleanly, showing the fix
  holds up under back-to-back removals, not just an isolated one.
- `native-poke-bt-macs` was populated on D-SAM this round; round 2 found it empty on the same unit.
  Not something this round changed — noting it in case a future round assumes round 2's
  empty/default baseline still holds.

Captures: `native-aa-dsam-wifi-unavailable-round3-captures.zip` on release
`rig-evidence-native-aa-dsam-wifi-unavailable`, logcats only.
sha256: `154e8512ea23be1876a1956b182160cd10c6290102edd9d9359b69f7c4f9290a`
