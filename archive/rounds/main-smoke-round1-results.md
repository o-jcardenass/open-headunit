# main-smoke, round 1 results

**Candidate:** upstream `main` @ `7102b4283666ffcc7802e49402735e3958cd51a0` (3.5.0-beta4, versionCode 117)   **Baseline:** none (smoke round)
**APK md5:** `62ccbb85dfed879a1151dea9610df817` (all three units)
**Unit:** D-HU (UNISOC MT50, Android 14, API 34), D-POCO (POCO X3 NFC, API 35), D-HP (API 17). D-SAM not used. D-MOTO silenced by the operator, untouched by the agent (see Setup notes).
**Date:** 2026-10-07

| Run | Verdict | `SSL handshake complete` | `mrend` windows in hold | Our crash count | Underruns (`enable-audio-sink`) |
|---|---|---|---|---|---|
| S0 | PASS | n/a | n/a | n/a | n/a |
| S1 | UNTESTABLE | n/a | n/a | n/a | n/a |
| S2 | PASS | 1 | 22 | 0 | 0 (false) |
| S5 | FAIL (brief condition unreachable, see R S5) | 1 | 11 | 0 | 0 (false) |
| S4 | PASS | 1 | 22 | 0 | 0 (false) |
| S3 | UNTESTABLE | n/a | n/a | n/a | n/a |

S2: `S2-start` to SSL 13.8 s (18:18:21.054 to 18:18:34.849). `p2p_true_live=1`, `p2p_true_after=0`, `p2p_false_after=1`.
S0: JUnit total 2820 tests, 0 failures, 0 errors, 0 skipped.

## Setup notes

- **Branch/host:** brief read from `fork/transfer/rig-rounds` @ `b83630ee` through a detached scratch worktree; the working checkout stayed on `main`. Build tree: scratch worktree of `7102b428`. Quirk files read: `topics/tooling.md`, `units/D-HU.md`, `units/D-POCO.md`, `units/D-HP.md`.
- **Pre-flight table:** D_HU wifi 1 bt 1 Awake; D_POCO wifi 1 bt 1 Awake; PREFLIGHT OK. Section 0 reads: 3 devices present; phone BT address `DC:B7:2E:5E:4E:59`; bond count 1; both `shared_prefs` dirs `u0_a176:u0_a176`; `host_connected=false` count 1; Gearhead versionName D-POCO `17.9.664004-release`, D-HU `17.3.662864-release`; `:149D` count 1 before S4, 2 after S4 (`port_after=2`); our app not running on D-POCO or D-HP at start. API levels: D-HU 34, D-POCO 35, D-HP 17.
- **D-MOTO silence set by the operator, not by me.** D-MOTO was plugged in. The auto-mode classifier denied both `airplane-mode enable` and a read-only `dumpsys bluetooth_manager` on it, so I never touched it or read its state. The operator states D-MOTO was in airplane mode (silent) for all runs; I could not verify that from the rig, and no D-MOTO traffic appeared in the S2 and S5 logs. The brief's end-of-round undo (`airplane-mode disable`, `svc bluetooth enable`) was not run for the same classifier reason, so D-MOTO is still in airplane mode.
- **Build:** the first build failed (`SDK location not found`) because the scratch worktree lacked the untracked `local.properties`; copied from the main checkout, no code change, rebuilt. `build_hur_cool.sh` stopped the tree once at 87C (18:12:33) and resumed at 67C; host package max 89C was during the build. Run windows peaked at 72C, `throttle_pkg` constant at 690 throughout (delta 0).
- **`ms_lib.sh` `th_pkg` defect:** `rig_thermal.sh status` prints two `pkg=` matches here, so `th_pkg` returned `64\n690` and `th_gate` / `th_report` did not gate (`[: integer expression expected`, `thermal_max=690C`). I gated each run myself with `rig_thermal.sh wait 70` (62C, 62C, 57C before S2, S5, S4) and used the `rig_thermal.sh watch` log instead of `th_report`.
- **Settings:** D-HU keys from section 4 written with `ms_pref.sh`, every `hu_has` line 1, `hu_not` lines 0, `</map>` 1; `connection-modes` holds `wifi` and `self`, no write needed. D-HU backup delta against `launcher-auto-connect-crash-round3/settings-backup-HU.xml`: zero (empty diff of sorted elements). D-POCO keys written and read back; the `run-as` form with the script in `/data/local/tmp` worked (no stdin form needed). Install did not wipe `settings.xml`. Audio keys (never changed), all three heads: D-HU `audio-latency-multiplier=8 enable-audio-sink=false use-aac-audio=false audio-queue-capacity=20`; D-POCO: `maudio` printed no audio keys (all four absent from its `settings.xml`, so defaults). D-HP: audio keys not read (no `maudio` run against it, S3 not run).
- **Restore:** D-HU `settings.xml` and D-POCO `settings.xml` both match their backups byte for byte (`cmp` clean). Candidate left installed on all three units. D-HU Bluetooth ends ON; D-POCO WiFi and Bluetooth ON. D-HU's Bluetooth self-reverted to ON after my `svc bluetooth disable` before S4 (known quirk); its app was stopped.
- **S5 first attempt void for setup:** `ph_bt disable` read `ON` and the script refused (UNTESTABLE branch). A control with the D-HU app stopped read `OFF` for 24 s, so the first read was a transient (a `BLE_TURNING_ON` was seen, the phone's own or the running HU app's doing; not isolated). The brief's single re-run of a setup-void run was used (log `S5-attempt1.log`).
- **Brief defect, S5:** the condition "`WakeDetect: SCREEN_OFF` 1 or more in `S5-off` to `S5-wake`" cannot hold in the shape the brief itself requires. The service starts in the dark (`AapService.onCreate` 18:24:11.561 comes after the screen went off at S5-off), and the `ACTION_SCREEN_OFF` receiver lives in `AapService` (`AapService.kt:651,1920`), so no `SCREEN_OFF` line can print. Per the rules I did not improvise: S5 is graded FAIL on the stated condition, with the mechanism below. Every other S5 condition passed.
- **S3:** UNTESTABLE per section 4. D-HP reads `log-level=0` (VERBOSE), the table requires `2`; API 17 has no `sed`, so the key could not be written with `ms_pref.sh`. Values recorded: `wifi-connection-mode=1`, `log-level=0`, `onboarding-version=2`, `connection-modes={usb, wifi}`, no `video-profile-starvation-cap`. Same LAN was true (`192.168.1.` both), D-HP `Wi-Fi is enabled`, `:149D` 2. Nothing else blocked S3; a host-side edit (`set_pref_hostedit.sh`) would have allowed it but the brief decides this case.
- **Scripts used:** `rig_preflight.sh`, `rig_thermal.sh`, `build_hur_cool.sh` (wrapping `build_hur.sh`), `run_unit_tests.sh`, `rig_devices.sh`. Added in `hur-wifi-test-scripts/main-smoke-round1/`: `ms_lib.sh`, `ms_pref.sh` (verbatim from the brief), `s2.sh`, `s4.sh`, `s5.sh` (session scripts with refusal pre-checks).
- **Verdict method:** all counts are my own windowed greps over the captures, marker counts checked (S2 4/4, S5 6/6, S4 4/4 per capture).

## S0 build, unit tests, identity

**PASS**

- Build succeeded: `com.andrerinas.headunitrevived_3.5.0-beta4_debug.apk`, md5 `62ccbb85dfed879a1151dea9610df817`.
- JUnit XML: 2820 tests, 0 failures, 0 errors, 0 skipped.
- Live md5 after install equals that md5 on D-HU, D-POCO and D-HP.
- `ACTION_QUERY_STATE` on all three: `"commit":"7102b4283666"`, `versionName 3.5.0-beta4`, `versionCode 117`, `flavor github`, no `-dirty`.

## S1 USB, D-POCO into D-HU

**UNTESTABLE**

Pre-registered. `dumpsys usb | grep -c host_connected=false` read 1 (no USB host path on D-HU).

## S2 Native AA, D-HU and D-POCO

**PASS**

- Settings: D-HU section 4 keys (mode 3, log-level 2, etc.). Phone WiFi and BT enabled, launcher in front.
- `S2-start` to `S2-ssl`: `ACTIVELY LISTENING` 1, `Incoming connection detected` 1, `SSL handshake complete` 1.
- `S2-ssl` to `S2-exit`: `mrend` 22, second SSL 0, `AapService: Disconnected.` 0. First/last `rendered=` 79 / 99.
- `p2p_true_live=1`; after exit `p2p_true_after=0`, `p2p_false_after=1`.
- `S2-exit` to `S2-end`: `AutomationReceiver: ` 1, user exit line 1, `the wireless teardown did not finish in` 0.
- Phone: `CAR.(SERVICE|SETUP|PROJECTION)` 125, `Critical error` 0. Crash gate both captures: `Process:` 0, `ANR in` 0, `FATAL EXCEPTION` 0.
- Decisive lines: `18:18:34.576 WirelessServer: Incoming connection detected from /192.168.49.136`; `18:18:34.849 AapSslContext.performHandshake | SSL handshake complete`; `18:20:32.673 AapService: Native AA user exit. Stopping active launcher.`
- Recorded: `createGroup SUCCESS!` 1, `MATCH! Starting AapService via Bluetooth Auto-start...` 1, `Connection accepted from ` 1, `Media Sink Setup Request: 3 on channel VIDEO` (18:18:35.813), underruns 0, start to SSL 13.8 s.

## S5 a wake replays the bring-up held in the dark

**FAIL**

Failed condition: `S5-off` to `S5-wake` `WakeDetect: SCREEN_OFF` 1 or more; measured 0. See Setup notes: the service was created after the screen went off, so the line cannot exist; the behaviour the run exists to show happened.

- Pre: `mirror=1`, phone BT `OFF`, `svc=0`, `hu_sleep` `mWakefulness=Dozing`, `ph_bt enable` `ON`, `arrival_wait=0`.
- Dark window (`S5-off` to `S5-wake`): `MATCH!` 1, `WifiLauncher: wireless bring-up held while the screen is off. ` 1 (18:24:11.706), `Initializing WiFi Mode: ` 0, `createGroup SUCCESS!` 0, `SSL handshake complete` 0, `SCREEN_OFF` 0. The "hold did nothing" signature (init >= 1, no held line) is absent.
- `S5-wake` to `S5-exit`: `SCREEN_ON (screen was off for ` 1 (18:24:46.201, text says `-1s`), re-arm line 1 with `force=true` 1 (18:24:46.202), `Initializing WiFi Mode: ` 1, `SSL handshake complete` 1 (18:24:57.822), `mrend` 11.
- `S5-exit` to `S5-end`: user exit line 1. Phone `CAR.*` 131. Crash gate both captures 0 / 0 / 0.
- `AapService: Bluetooth auto-start: BtAutoStartActions(clearUserExit=true, forceRearmWireless=true, armWirelessIfIdle=false)` at 18:24:11.754 (one line in the run).
- `S5-wake` to SSL 12.6 s (18:24:45.237 to 18:24:57.822). Underruns 0. A `WakeDetect: SCREEN_ON (screen was off for -1s)` value is odd (negative) because the service never saw the off event; noted for the maintainer.

## S4 Self Mode on D-POCO, the 5277 route

**PASS**

- Settings: section 4 D-POCO keys, all read back; `tun_before=0`.
- `S4-start` to `S4-ssl`: `AA 17.4+ detected. Connecting directly...` 1, `is NOT running.` 0, SSL 1 (18:27:42.484, 3.05 s after `S4-start` 18:27:39.437).
- VPN: `tun established` 0, `Starting DummyVpnService` 0; `tun_at_ssl=0`, `tun_at_end=0`.
- Hold: `mrend` 22 (first/last `rendered=` 141 / 150), `Disconnected.` 0, `Critical error` 0. Phone side `Head unit connected` 1.
- Exit window: `Disconnected.` 1, `received ByeByeRequest` 1. `nothing connected within` 0. `port_after=2`.
- Crash gate: 0 / 0 / 0. Underruns 0. `Media Sink Setup Request: 7 on channel VIDEO`.
- D-POCO `settings.xml` restored, `RESTORED_POCO`.

## S3 Headunit Server mode, D-HP and D-POCO

**UNTESTABLE**

D-HP `log-level` reads 0, the brief requires 2, and D-HP cannot be written with `ms_pref.sh`. See Setup notes for the recorded values.

## Anything the brief did not ask about

- `ms_lib.sh` `th_pkg` needs fixing (two `pkg=` matches); the brief's thermal gate silently never gates on this host.
- S5's `SCREEN_OFF` condition should be dropped or moved to a run where the service is already alive; the `(screen was off for -1s)` text is what the app prints when it never saw the off event.
- The first `ph_bt disable` in S5 reverted within 5 s once and held for 24 s the next two times; cause not isolated.
- Evidence: release asset `main-smoke-round1-captures.zip` under `rig-evidence-main-smoke`, sha256 `5b9ac8859cdac25a48e1134aecbe072fb6e059b44767e2153e8a7321ece49480`.
