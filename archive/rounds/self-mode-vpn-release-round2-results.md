# self-mode-vpn-release, round 2 results

- **Candidate:** `2a91ee336f64aa37fd4f9babb35f61a842380c26` (`fork/fix/self-mode-vpn-release`), same tip as round 1.
- **Baseline:** none this round (brief section 1). Round 1 R5A measured `main`.
- **APK md5:** `50f8137b85adf64b917c8e9274c70da0`, round 1's candidate APK reused, not rebuilt. DEX count of `SelfLaunchRoutePolicy`: 4.
- **Unit:** D-POCO (R5P2, 5277 route), D-HU (R5L2, legacy route). R2M not run.
- **Date:** 2026-10-07.
- **Evidence:** release `rig-evidence-self-mode-vpn-release`, asset `self-mode-vpn-release-round2-captures.zip`, sha256 `39e277dd0495ed098b98e3e3c112dd6d7003b1a269938bb0319435fd22ee189b`.

## Round verdict: PASS

R5P2 PASS, R5L2 PASS. R2M not run (optional, needs an operator hand step).

## Setup notes

- **R0 gate: PASS.** Both installs (`adb install -r -d`) read `"commit":"2a91ee336f64"` in `ACTION_QUERY_STATE`, versionCode 116.
- **Units.** D-POCO: API 35, Gearhead `17.9.664004-release`, `ACTIVATE_VPN: ignore`, `:149D` listeners = 1 at precheck (no hand step needed). D-HU: API 34, Gearhead `17.3.662864-release`, `ACTIVATE_VPN: allow`, `shared_prefs` owner `u0_a176`. No consent dialog appeared on either unit.
- **Pre-flight table** (`rig_preflight.sh D_POCO:wifi,bt D_HU:wifi,bt`, before anything touched a device): D_POCO wifi 1, bt 1, Awake, HFP and A2DP up; D_HU wifi 1, bt 1, Awake, HFP none, A2DP none; no stray logcat, rig lock free. PREFLIGHT OK.
- **Quirk files read:** `rig-quirks/topics/tooling.md`, `rig-quirks/units/D-HU.md`, `rig-quirks/units/D-POCO.md`.
- **Settings.** Fresh backups taken first. Install did not change `settings.xml` (post-install diff empty on both). Written with `svr_mkprefs.py` and `svr_apply.sh`, read back identical. Delta against the backups:
  - D-POCO: `auto-start-self-mode` set true (was absent), `auto-connect-delay-seconds` 0, `log-level` 2, `connection-modes` replaced with `self` only (was `usb` plus others).
  - D-HU: `auto-start-self-mode` false to true, `native-poke-all-paired` true to false, `auto-connect-last-session`, `auto-connect-single-usb`, `keep-dummy-vpn-during-session` removed, `connection-modes` replaced with `self` only (was `self`, `wifi`, and others), `auto-connect-delay-seconds` 0 and `log-level` 2 kept.
  - Audio keys left untouched, read back on D-HU: `use-aac-audio` false, `audio-latency-multiplier` 8, `audio-queue-capacity` 20, `enable-audio-sink` false. D-POCO's file carries none of these keys, so it runs the defaults.
  - Both files restored from backup at the end and compared byte-identical.
- **Scripts.** `svr_lib.sh` (round 1, unchanged), `svr_apply.sh`, `svr_mkprefs.py`. New: `svr2_runs.sh` (session script, owns the pre-checks: launcher focus, online `defnet`, `tuns` 0, commit, settings read back including `connection-modes` = `self`, other unit stopped with Bluetooth off, and D-POCO offline for R5L2) and `svr2_grade.sh` (windowed counts). Prepare and gate steps were run by the host directly, not by a `rig-executor`.
- **R5P2 void.** The first attempt was void: the D-POCO capture stopped at 13:45:04.633 on `UsbDeviceManager: Setting USB config to ptp` (an adbd restart, as in round 1's void), after the `R5P2-ssl` marker and before `-probe` and `-end`. Re-run once under the stop rule, which completed with all four markers. The void capture is kept in the asset as `R5P2-void1.*`.
- **Windowing.** Every count is inside `AutomationMarker: <RUN>-start` to `-end`, the hold counts inside `-ssl` to `-end`. `Marker` lines are 4 per run.
- **Thermal.** Host `thermal_max` 72C (R5P2) and 74C (R5L2), `throttle_pkg` 690 at first and last read of both runs. A `rig_thermal.sh watch` from round 1 (pid 78277, writing `self-mode-vpn-release-round1/R1.thermal`) was still running at the end and is not from this round.
- **Pre-registered outcomes seen:** one `releasing the dummy VPN (owner=` line carrying `SELF_MODE_SESSION_LIVE` followed by `VpnControl: DummyVpnService was not running`, and 2 `SelfMode: Installed AA version: ` lines, on each unit.
- Small host-side typos in the end-of-stage cleanup (`adb` without `shell`) failed harmlessly and were redone; the final radio state is WiFi on, Bluetooth on, airplane off on both units.

## R5P2

**PASS**

D-POCO, Gearhead 17.9, 5277 route, online start. No network drop.

| Measure | Value |
|---|---|
| `tun_at_ssl` / `tun_at_mid` / `tun_at_end` | 0 / 0 / 0 |
| `P` | 12, with `uid=10277 procState=TOP effective=NONE` |
| `Device is offline. Preparing Dummy VPN` / `Device is offline; Android Auto 17.4+ ...` | 0 / 0 |
| `VpnControl: Starting DummyVpnService (GitHub Build` / `DummyVpnService: tun established` | 0 / 0 |
| `SelfMode: AA 17.4+ detected. Connecting directly ...` | 1 |
| `SSL handshake complete` | 1 |
| `AapService: Disconnected.` in hold | 0 |
| `with a live session` in hold | 0 |
| `Throughput over ` in hold | 17, `rendered` 136 first, then 150 or 151 |
| `Head unit connected` | 1 |
| `received ByeByeRequest`, `Critical error`, `hard loss of network`, `WSEM#onProjectionNetworkLost`, `Lost network. lostNetwork=`, `WiFi network is lost`, `Socket failed due to VPN connection`, `Head unit disconnected` in hold | 0 each |
| `SelfMode: nothing connected within` | 0 |
| `defnet` at end | `Active default network: 721` |
| `releasing the dummy VPN (owner=` with `SELF_MODE_SESSION_LIVE` | 1 |
| `VpnControl: DummyVpnService was not running` | 1 |
| `...NO_SOCKET_DISCONNECT` (not graded) | 0 |

Thermal: max 72C, throttle_pkg 690 to 690.

## R5L2

**PASS**

D-HU, Gearhead 17.3, legacy route, online start, D-POCO offline with Bluetooth off. No network drop.

| Measure | Value |
|---|---|
| `tun_at_ssl` / `tun_at_mid` / `tun_at_end` | 0 / 0 / 0 |
| `P` | 12, with `uid=10176 procState=TOP effective=NONE` |
| `Device is offline. Preparing Dummy VPN` / `Device is offline; Android Auto 17.4+ ...` | 0 / 0 |
| `VpnControl: Starting DummyVpnService (GitHub Build` / `DummyVpnService: tun established` | 0 / 0 |
| `SelfMode: AA < 17.4 detected. Starting WirelessServer on 5288 ...` | 1 |
| `SelfMode: Launching AA Wireless Startup via Activity...` | 1 |
| `WirelessServer: Incoming connection detected from` | 1 |
| `SSL handshake complete` | 1 |
| `AapService: Disconnected.` in hold | 0 |
| `with a live session` in hold | 0 |
| `Throughput over ` in hold | 17, `rendered` 21 first, then 130 to 151 |
| `Launch projection 127.0.0.1 5288` | 1 |
| Gearhead teardown strings in hold (`Critical error`, `hard loss of network`, `WSEM#onProjectionNetworkLost`, `Lost network. lostNetwork=`, `WiFi network is lost`, `Socket failed due to VPN connection`, `Head unit disconnected`) | 0 each |
| `received ByeByeRequest` in hold (recorded, not graded on D-HU) | 0 |
| `SelfMode: nothing connected within` | 0 |
| `defnet` at end | `Active default network: 110` |
| `releasing the dummy VPN (owner=` with `SELF_MODE_SESSION_LIVE` | 1 |
| `VpnControl: DummyVpnService was not running` | 1 |
| `...NO_SOCKET_DISCONNECT` (not graded) | 0 |

Thermal: max 74C, throttle_pkg 690 to 690.

## R2M

**UNTESTABLE**

Optional run, not attempted: it needs an operator to toggle "Start head unit server" on D-MOTO and no person was at the rig. Not a result about the candidate.

## Anything the brief did not ask about

- The first R5P2 capture died on an adbd restart of D-POCO (`Setting USB config to ptp`). Round 1 recorded the same event (`midi`). It killed the logcat pipe, and the script went on to its end, so a script that exits 0 is not proof the capture is whole: check that all four markers are in the file.
- R5L2's first `Throughput over ` line reads `rendered=21`, a partial first interval on the legacy route; every later line is 130 or more.
- D-POCO ran with `ACTIVATE_VPN: ignore` and still held 12 of 12 replies, which fits the candidate starting no VPN on an online start.
- Neither run has a baseline arm, so the PASS is a regression guard result as the brief says, not proof the candidate changed behaviour on an online start.
