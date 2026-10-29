# driver-select-off-phone, round 1 results

**Candidate:** `fix/driver-select-off-phone` @ `b77936461f3f1020cf9e52e0ad144c3b8f3323af` (tree `e0acc4a99c714e77c805f14c97905e7d1fd63451`)       **Baseline:** none built (S5 is the in-round control)
**APK md5:** d53c5af87c9b3630c74a4c96b50f5356 (candidate; the APK pulled back from D-HU has the same md5) / none
**Unit:** D-HU UNISOC MT50, Android 14, rooted shell; D-POCO and D-MOTO as phones, Gearhead 17.9.664004-release on both
**Date:** 2026-10-10

## Round verdict: PASS

S5, S1, S2, S3 and S4 all PASS. S3 needed one re-run after a setup failure in the script (see Setup notes).

**Evidence:** release `rig-evidence-driver-select-off-phone`, asset `driver-select-off-phone-round1-captures.zip`, sha256 `c2b5fda98d54e604e659633a2c2b6c26187e31bf45663ad0e05a7153b2e2dc56`.

## Setup notes

- **Skill path drift.** The skill and `rig_devices.sh` callers named `hur-wifi-test-scripts/`; the folder is now `rig-toolkit/` and the old name is not a symlink here. All scripts came from `rig-toolkit/`. Rounds ran from `~/Coding/StudioProjects/rig-data/rounds/driver-select-off-phone-round1/`; the candidate was built in worktree `worktrees/driver-select-off-phone__candidate__b7793646__20261010` made with `wt-new.sh`. The transfer branch was read with `git show`, never checked out in the main checkout.
- **Pre-flight.** First pass FAILED on two idle Gradle daemons (Android Studio JVM, parent pid 1). The operator approved killing them. `rig_cleanup.sh kill` itself errored (`exec: rig_cleanup.sh: not found`), but the daemons were gone on the next pass and `rig_preflight.sh D_HU:wifi,bt D_POCO:wifi,bt D_MOTO:bt` printed PREFLIGHT OK (all three: wifi_on 1, bt 1, Awake, HFP:none A2DP:none).
- **Quirk files NOT read in full.** The brief names `topics/tooling.md`, `units/D-HU.md`, `units/D-POCO.md`, `units/D-MOTO.md`, `topics/bt.md` and `topics/gearhead.md`; I loaded them, but only their first lines reached my context (the output was truncated), and I read template section 7 in full. The round followed the brief's own commands and the checks above, and no quirk-file rule was consulted beyond the brief's quotes of them.
- **Build gate.** `build_hur_cool.sh` then `run_unit_tests.sh`: BUILD SUCCESSFUL, 3130 tests, 0 skipped, 0 failures, 0 errors (summed from the JUnit XML). Package temperature reached 91C during the build (`throttle_pkg=0` throughout); no timing run overlapped the build. Thermal gate (`rig_thermal.sh wait 75`) before every run; `throttle_pkg=0` and max 62 to 67C for every run.
- **Identity gate.** `ACTION_QUERY_STATE` returned `"commit":"b77936461f3f-dirty"`; `extra_explicit_pick` count 2, `autoScope` count 7 in the pulled APK dex.
- **Pre-flight reads.** P2: `shared_prefs` owner `u0_a176:u0_a176` mode 771, app uid 10176, no chown needed. P3: D-HU bonded POCO X3 NFC (`DC:B7:2E:5E:4E:59`), motorola edge 30 neo (`A0:46:5A:97:E4:95`), Magnetic Speaker (hidden, hands free unit). D-POCO and D-MOTO list `Navegadortz2`, neither lists `Navegadortz3`. P4: D-HU station SSID "Pegue Cdesta", RSSI -21, 433 Mbps. P5: D-MOTO Bluetooth was ON at round start, set OFF (`state: OFF`), re-enabled at the close. D-MOTO had no PIN keyguard (`isKeyguardShowing=false`), foreground app WhatsApp.
- **Settings.** Round-start backup `settings-backup-roundstart.xml` (md5 8d77a54bc54478ab9d19031230eb9f00). Delta against the last round's backup (`aa-178-protocol-levers` round 1): `last-loc-latitude/longitude`, `last-connected-native-mac` (now D-POCO, was D-MOTO), `last-connection-ip`, `wifi-direct-last-group-bssid`, and empty-string elements written as `<string ...></string>` instead of `<string ... />` (no semantic change). Left as found: `video-codec` H.264, `resolutionId` 2, `fps-limit` 60, `wifi-direct-stable-identity` true, `auto-start-bt-macs` empty set. Baseline written once with `prefs_edit.py` (the brief's `hu_prefs`), per-run keys by each run script. Final restore: `diff settings-final.xml settings-backup-roundstart.xml` empty, owner 10176:10176 mode 660.
- **S3 attempt 1 voided (setup failure, script defect).** The brief's step 1 waits for the selector line after the `S3-start` marker, but `ACTION_NATIVE_AA_PROMPT_SHOWN received` printed at 15:42:20.476, 0.35 s before the marker at 15:42:20.826 (the selector appears during launch). The wait timed out, the run was ended, and attempt 1 was archived in `attempt1-S3/`. Attempt 2 waited for the line anywhere in the capture (the capture is cleared with `logcat -c` at start, so no stale line). Brief erratum: S3 step 1 should not scope the wait to the marker.
- **Injected taps (all by `uiautomator dump`, exactly one resource-id plus text match):** S3 `pick` `deviceName` "motorola edge 30 neo" at 755,312 (attempt 2; attempt 1 made no tap); S4 `switch` `text` "Switch Phone" at 388,577; S4 `pick` `deviceName` "motorola edge 30 neo" at 755,312. Back key (`input keyevent 4`) once in S4 before the switch tap.
- **S4 marker duplicate.** `tap_node` writes a marker named `S4-switch` and the step before it writes one too, so `S4-switch` appears twice (15:47:02.638 and 15:47:08.188). `from_mark` uses the first, which only widens the window; counts are unaffected.
- **S4 condition 4 reference line.** The brief defines `P` as the run's `Driver selected:` line; S4 has two (15:45:48.256 automatic, D-POCO; 15:47:14.655 explicit, D-MOTO). I measured from the explicit pick (13.3 s). Measured from the first line it would be 99.7 s; the brief's 60 s bound only makes sense for the switch.
- **Phone clock for S4 condition 7.** D-MOTO and D-HU timestamps agree to the second in this capture, so the rfcomm line (15:47:17.159) was placed against the pick (15:47:14.655) directly.
- **Discard rules.** Every run: `createGroup SUCCESS` 1, `Magic Garbage detected in header` 0, `MATCH! Starting AapService` 0. Stale P2P group found at S5 start (a group left by the earlier identity-gate launch): removed with `headunit://exit` plus `cmd wifip2p remove-group`, `groupFormed: false` read before launch.
- **Logcat.** All captures stopped by pid; `ps aux | grep logcat` empty at the close. Windowed greps only (marker to `-end` marker); `LC_ALL=C` for grep over captures with invalid multibyte data (S3, line 27100).
- **Scripts.** Used `rig_preflight.sh`, `rig_thermal.sh`, `build_hur_cool.sh`, `run_unit_tests.sh`, `wt-new.sh`. Round scripts (in the round folder, not promoted): `lib.sh`, `prefs_edit.py`, `node_xy.py`, `run_simple.sh` (S5, S1, S2), `run_s3.sh`, `run_s4.sh`, `g.sh` (grep set).

## S5: Control, mode 0

**PASS**

- Settings written: `native-driver-selection-mode` 0, `native-poke-bt-macs` {D-POCO}, `last-connected-native-mac` D-MOTO; D-MOTO Bluetooth OFF before and after.
- Discard-rule check: clean.
- Counts: `Driver selected: ` 0, `waits until that phone has had its turn` 0, `sslc` 1, `Service Discovery Request: ` 1, `Media Sink Setup Request: ` 2, `Throughput over` 3.
- Decisive lines: `15:37:48.489 WifiDirectManager: 5GHz createGroup SUCCESS!`; `15:38:31.427 AapSslContext.performHandshake | SSL handshake complete`.
- **T5 = 42.9 s** (`createGroup SUCCESS` to SSL). **R5 = 0** (phone `Retrying connection attempt on all channels`, 15:37:44 to 15:38:31). `Calling socket.connect() for motorola` 0. First `driver candidates:` line: `2 phone, 0 unknown, 1 not a phone - hidden: Magnetic Speaker (hands free unit)` (15:38:33.284, after the SSL line).

## S1: R2's setup on the candidate

**PASS**

- Settings written: mode 1, `native-poke-bt-macs` {D-POCO}, `last-connected-native-mac` D-MOTO; D-MOTO Bluetooth OFF before and after.
- Discard-rule check: clean.
- Conditions: (1) `HomeFragment: Unambiguous driver (POCO X3 NFC)` 1, `ACTION_NATIVE_AA_PROMPT_SHOWN received` 0. (2) `Driver selected: DC:B7:2E:5E:4E:59 (automatic pick)` 1, `Driver selected: A0:46:5A:97:E4:95` 0. (3) `Calling socket.connect() for motorola` 0. (4) `waits until that phone has had its turn` 0. (5) `sslc` 1; **`dt G S` = 25.6 s** against `T5 + 10` = 52.9 s (plan bound 70 s). (6) SD 1, MS 2, TP 4. (7) **phone retries 0** against `R5 + 20` = 20.
- Decisive lines: `15:39:27.859 NativeAA: Driver selected: DC:B7:2E:5E:4E:59 (automatic pick)`; `15:39:28.736 createGroup SUCCESS!`; `15:39:54.314 SSL handshake complete`. The shipped build's figure on the same setup (earlier thread, R2) was 136.5 s.

## S2: A countdown pick of the off phone locks nothing

**PASS**

- Settings written: mode 1, `native-poke-bt-macs` {D-POCO, D-MOTO}, `last-connected-native-mac` D-MOTO; D-MOTO Bluetooth OFF before and after.
- Pre-check met: `ACTION_NATIVE_AA_PROMPT_SHOWN received` 1; `Driver selected: A0:46:5A:97:E4:95 (automatic pick)` 1 (15:41:04.381). Discard-rule check: clean.
- Conditions: (1) `(explicit pick)` 0. (2) `Calling socket.connect() for motorola` 2; both `Poke via HFP-AG to motorola edge 30 neo ... failed: read failed, socket might closed or timeout, read ret: -1` (15:41:10.905, 15:41:26.395). (3) `waits until...` 0, `did not answer a whole` 0. (4) `sslc` 1; **`dt G S` = 26.0 s** against `T5 + 20` = 62.9 s (plan bound 70 s). (5) **`dt P O` = 9.7 s** (Type 4 at 15:41:14.083), bound 60 s, shipped 120 s. (6) SD 1, MS 2, TP 3. (7) **phone retries 0**, bound 150, R2 544.
- Not graded: `Selection prompt active (target=` 0; `turned away` 0.

## S3: An explicit pick of the off phone lapses after one unanswered round

**PASS**

- Settings written: mode 1, `native-poke-bt-macs` {D-POCO, D-MOTO}, `last-connected-native-mac` deleted; D-MOTO Bluetooth OFF before and after. Attempt 1 voided (Setup notes); this is attempt 2. One injected tap (selector row).
- Discard-rule check: clean. Pre-check: `HomeFragment: Connecting to Native-AA device: motorola edge 30 neo` 1; `Calling socket.connect() for motorola` 3.
- **Gate passed:** `15:43:40.865 NativeAA: the driver chose A0:46:5A:97:E4:95, so DC:B7:2E:5E:4E:59 waits until that phone has had its turn.` (count 1; `turned away 107 connection attempts before this one.` at 15:44:15.622).
- Conditions: (1) `(explicit pick)` 1, `Driver selected: ` 1 (`15:43:27.949 ... A0:46:5A:97:E4:95 (explicit pick)`). (2) `did not answer a whole` 1 at `15:44:15.336`; **`dt P L` = 47.4 s** (plan expected 35 to 45 s). (3) **`dt P O` = 48.0 s** (Type 4 at 15:44:15.980) >= 29.5. (4) 48.0 <= max(30, 47.4) + 10 = 57.4. (5) 48.0 < 90 (shipped 120). (6) `sslc` 1 (`15:44:22.688`); SD 1, MS 2, TP 4; `WirelessServer: Incoming connection detected from /192.168.49.136` at 15:44:22.351. (7) **phone retries 214** < 400 (R2 544).
- D-MOTO pokes: HFP-AG failed 15:43:39.228, HFP-AG failed 15:43:54.775, HSP-AG failed 15:44:15.328, each `read failed ... read ret: -1`. The lapse followed the third failure by 8 ms. `dt G S` = 59.2 s (not graded).

## S4: Positive control, Switch Phone to a phone that answers

**PASS**

- Settings written: mode 1, `native-poke-bt-macs` {D-POCO, D-MOTO}, `last-connected-native-mac` D-POCO; D-MOTO Bluetooth OFF at launch, ON from `S4-moto-on` (15:46:27) until the end, then OFF, then re-enabled at the close. Two injected taps (Switch Phone, selector row) plus one Back key.
- Discard-rule check: `createGroup SUCCESS` 1, `Magic Garbage` 0, `MATCH!` 0 (including after `S4-moto-on`). Pre-check met: `Driver selected: A0:46:5A:97:E4:95` before `S4-switch` 0 (first pick was `15:45:48.256 DC:B7:2E:5E:4E:59 (automatic pick)`). Projection window held: focus named `AapProjectionActivity` without `ACTION_RAISE_PROJECTION`.
- Conditions: (1) after `S4-switch`: `AapProjectionActivity: User requested switch driver` 1; `a driver switch is starting, so DC:B7:2E:5E:4E:59 is not let straight back in.` 1. (2) after `S4-pick`: `15:47:14.655 NativeAA: Driver selected: A0:46:5A:97:E4:95 (explicit pick)` 1. (3) `did not answer a whole` 0. (4) `sslc` 2 (15:46:05.419, 15:47:27.994); **second SSL 13.3 s after the explicit pick** (<= 60). (5) last `Incoming connection detected from /192.168.49.28` (15:47:27.591) equals D-MOTO's `192.168.49.28` in `S4-moto-ip.txt`. (6) `createGroup SUCCESS` 1. (7) D-MOTO `GH.WIRELESS.BT: Creating rfcomm socket for device: 11:46:03:10:33:59 and uuid: 4de17a00-52cb-11e6-bdf4-0800200c9a66` count 1 at 15:47:17.159 (that address is the head unit's Bluetooth adapter, seen from the phone).
- Not graded: refusals after the switch: `15:47:09.867 DC:B7:2E:5E:4E:59 is the phone this switch moved away from, so it is not let back in yet.`; `15:47:14.976` and `15:47:17.881` `the driver chose A0:46:5A:97:E4:95, so DC:B7:2E:5E:4E:59 waits until that phone has had its turn.` `15:47:17.262 Successfully poked motorola edge 30 neo via HFP-AG. Holding 20000ms...`. Two `Service Discovery Request: ` lines.

## Report back (brief section 9)

1. S1: `dt G S` 25.6 s against `T5` 42.9 s; `Driver selected: A0:46:5A:97:E4:95` 0, `Calling socket.connect() for motorola` 0, `waits until that phone has had its turn` 0.
2. S2: `dt P O` 9.7 s. S3: `dt P O` 48.0 s, `dt P L` 47.4 s.
3. S4: explicit pick line `Driver selected: A0:46:5A:97:E4:95 (explicit pick)`, lapse count 0, second SSL client `192.168.49.28` = D-MOTO's address.

## Anything the brief did not ask about

- In S1 and S2 the SSL handshake came 25.6 s and 26.0 s after the group, faster than the S5 control (42.9 s). The three runs differ in when the phone's radios were switched on relative to the group (about 15 to 22 s after launch in each), and one S5 sample is not enough to say mode 0 is slower; `T5` was the bound the brief set.
- The S3 lapse line printed 8 ms after the third failed D-MOTO poke (HSP-AG), at 47.4 s after the pick, later than the plan's 35 to 45 s, because the HFP-AG attempts took about 15 s and 20 s of connect time each. The brief already reports this figure rather than grading it.
- In S4 D-POCO was refused three times after the switch with two different messages (switch-away, then chosen-driver). Both forms printed, and the answered pick kept its lock, as the brief wanted.
- A stale P2P group survived a force-stop of the app from the identity-gate launch; the common frame's step 4 (check `groupFormed`) caught it.
