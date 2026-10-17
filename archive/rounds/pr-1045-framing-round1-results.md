# pr-1045-framing, round 1 results

**Candidate:** emotionbug/open-headunit pr/split-aap-framing @ dfb0d6f9653c544d9daddfe6c3d5b3cf6c947d97       **Baseline:** main @ 7e9d813d66493f1cc978c0f6a259bdb613874bd1
**APK md5:** candidate 9ebe094575265e61c4e5274e835b9857 (`cand-dfb0d6f9.apk`) / baseline 9516b641ac68b2e89033dca1a947c9cd (`base-7e9d813d.apk`), pulled and hashed locally from the installed package on D-HU, D-SAM and D-HP
**Unit:** D-HU (UNISOC MT50, Android 14, 1440x720, rooted shell) / D-POCO (Android 12+, Gearhead 17.9.664004, non-debuggable app build) and D-MOTO (Gearhead 17.9.664004) as phones / D-SAM (SM-T230, API 19, 1280x800) / D-HP (HP Slate 7 Plus, API 17). No USB host pairing was set up.
**Date:** 2026-10-04 to 2026-10-05

## Setup notes

**Quirk files read:** `rig-quirks/topics/tooling.md`, `topics/wifi.md`, `topics/bt.md`, `units/D-HU.md`, `units/D-POCO.md`, `units/D-SAM.md`, `units/D-MOTO.md`, `units/D-HP.md`, `units/D-SAM-and-D-HP.md`.

**Pre-flight (first run, before any device was touched):**

| Role | ADB | WiFi | BT | Screen | BT profiles |
|---|---|---|---|---|---|
| D_HU | ok | 1 | 1 | Awake | HFP none, A2DP none |
| D_POCO | ok | 1 | 1 | Awake | HFP linked, A2DP up |
| D_SAM | ok | 1 | 1 | Awake | none |
| D_MOTO | ok | 1 | 0 | Awake | none |
| D_HP | MISSING (no free USB port; connected later by the operator) | | | | |

**Deviations from the brief, in the order they mattered:**

1. **Run lengths were cut at the operator's request** after the first sweep. V1-B, V2-B, V2n-B, V3-B ran 10 min (120 windows). Everything after V3-B ran 5 min (60 windows): V4, V5, M, O1, V1-C, V2-C, V3-C and the K soak at 10 min (121 windows) instead of 30. The candidate V1, V2, V3 runs are graded against the **first 300 s of the matching baseline window** (the extractor got a `SLICE_SECS` option, `*.5min.json`). Window-count thresholds were scaled to the shorter runs (60 windows for 5 min). Phone-side counts of the sliced baselines are whole-file (10 min), so the out-of-order ping comparison is 63 against 0 and not like for like in magnitude.
2. **V5 and V5-C:** V5-B read `capped=_1920x1080`, equal to V2, so V5 is the pre-registered INCONCLUSIVE duplicate and V5-C was not run.
3. **Helper changes:** `cap_stop` and `swipe_stop` used `pkill -f`; replaced with kill-by-pid. The between-run reset (`pr1045_reset.sh`) restores `settings.xml` from the unit's backup as root. Its first version used `adb push` without `-s`, which failed silently and restored a stale 09-23 file with `native-ap-transport=1` and the hotspot keys; that run came up in hotspot mode. The first C5-B attempt (bring-up failure) was also run on that bad file, and I wrongly told the operator the cause was D-HU's WiFi. Fixed, and the live file is diffed against the backup after every reset (only the run's own keys differ). Both attempts are void; C5-B reran clean.
4. **D-SAM `connection-modes` was `{usb}`.** The app logged `wireless bring-up requested, but WiFi is not one of the chosen connection modes. Not arming it.` I wrote `{wifi}` with `set_prefs_runas_host.py set:connection-modes:wifi`. That is a setting the brief did not name. The session script now fails if the set lacks `wifi`.
5. **Arm check:** every graded capture was checked for `Initializing WiFi Mode: NATIVE`, 0 hotspot lines and `WirelessServer: Incoming connection`. All Stage A and B captures pass it.
6. **Phone identity:** the first V1-C session formed with D-MOTO because its Bluetooth was left on after M-B (void, reran with D-MOTO off). The first M-C attempts failed at bring-up (D-HU poked D-POCO, whose Bluetooth was off), at the precheck (Instagram in the foreground on D-MOTO), and a clean-looking attempt carried two `createGroup SUCCESS` and was voided under the contamination rule after I had briefly graded it PASS. The last M-C attempt is the graded one.
7. **D-SAM:** its host-side capture died about 20 s after starting; replaced by a tag-filtered `-s OPENHU:V` reader in a restart loop, deduplicated afterwards. D-SAM needs up to 3.5 min to form a session (first O1-B attempt gave up at 150 s while the session formed at 23:31); bring-up wait raised to 330 s. Counts are windowed by marker. `date` on D-SAM read 13 s behind the host (23:24:23 against 23:24:36). Battery 100, `USB powered: true`, `AC powered: false`.
8. **F3 injector rates (first pass, superseded):** the candidates-per-second figure swings between runs (22.3 at C3-B, 2.06 at the first I3-B), so the calibrated rate gave 0 faults on I3-B (void, reran at 150). Every burst run landed its faults 2 to 35 s apart, inside the 60 s focus-cycle cooldown, so the verdict was taken from a second pass: 12 sessions with one fault each (budget 1, rate 150 for mode 3 and 100 for mode 5, 3 sessions per mode per build, candidate sweep then baseline sweep, D-POCO's Open Headunit app stopped and D-MOTO Bluetooth off before each).
9. **V3 `fed` metric:** the software HEVC path reports `fed=0` and `inputWait=0`; rendered median stands in for the brief's `fed >= 15` reachability and F1 condition 3 cannot be measured there.
10. **V4 SSL count:** at VERBOSE the DEBUG line `Handshake: SSL handshake complete` also matches the extractor's pattern, so `file_counts` reads 2 for one handshake (one `Session id`).
11. **`maxUnacked`:** 12 on most runs, 6 on software HEVC, 30 on M-B, M-C and K-C. K-B read 12. Same phone, unexplained; reported, not graded.
12. **Audio settings (quoted, never written):** D-HU `use-aac-audio=false` (the brief describes it as on), `audio-latency-multiplier=8`, `audio-queue-capacity=20`, `view-mode=2`, `wifi-direct-band=1`. D-SAM `false / 16 / 50`. D-HP `true / 16 / 50`.
13. **D-HU:** `shared_prefs/` is `u0_a176:u0_a176 drwxrwx--x`, uptime 6:10 at the end with no reboot. Settings delta against the backup: only the keys each run wrote. `allow-external-configuration` was already true.
14. **Settings written:** D-HU `onboarding-version 2`, `wifi-connection-mode 3`, `video-codec`, `force-software-decoding`, `software-video-decoder`, `resolutionId 3` (5 on V5-B), `fps-limit 60`, `log-level 2` (0 on V4), injector keys on I and C runs; latches deleted before each run. D-SAM `screen-orientation 2` (was 3), `connection-modes {wifi}`, codec H.264, resolution left at `0`. D-HP `wifi-connection-mode 1`, H.264, `log-level 2` (was 0), resolution left at `1`, `fps-limit 30` as found.
15. **Extractor:** ran on every capture. It takes about 2 min on a 36 MB capture with many faults (the per-fault scan is quadratic).

15. **Stage D setup:** D-MOTO had no Open Headunit installed; I installed the baseline then the candidate (and the old commit for U1) with `adb install -r -d` and wrote its settings with `set_prefs_runas_host.py`. D-MOTO and D-POCO were put on wireless adb with `adb tcpip 5555` so their USB-C ports were free. D-POCO has a loose USB port (operator), so moving the cable re-enumerates it and the first U1 pass is void. `dumpsys usb` before the stage listed only the manifest filter for our app (no grant). Session 1 of each U run is a cold plug by the operator with the permission prompts accepted once, never "Always". Run lengths: U1 sessions 20 s, U3 about 4 min, U2 60 s.

**Operator actions:** rejoined D-HU's WiFi (turned out not to be the cause of that failure), unlocked D-MOTO twice (PIN, no adb route), started the Android Auto head unit server on D-MOTO four times (once per build pair, after each force-stop of Android Auto), connected D-HP.

## R0 - gate
**PASS**
- Candidate 2673 tests, 0 failures, 0 errors; baseline 2658, 0, 0. `AapMessageReassemblerTest` (11), `AapReadPlaintextAuditTest` (6), `TlsUnwrapLoopTest` (5), `VideoTransportBoundaryTest` (2) pass on the candidate and are absent on the baseline.
- `AapMessageReassembler` count in the installed dex: candidate 11, baseline 0 (`TlsUnwrapLoop`: 4 and 0). md5s above differ. `ident cand` and `ident base` printed `IDENT-OK` on D-HU; the D-SAM and D-HP installs were hashed the same way.

## C3, C5 - calibration
**PASS** (C3-B and C5-B)
- C3-B: mode 3, rate 100, 13 summaries, 41 faults in 184.1 s, 4106 candidates, 22.301 per s, `rate3=1673`. C5-B: mode 5, rate 100, 12 summaries, 4 faults in 184.1 s, 456 candidates, 2.477 per s, `rate5=186`.
- Both clean: 1 SSL, 1 `createGroup SUCCESS`, 0 `MATCH`, 0 `Magic Garbage`. The rates did not hold up (see Setup note 8): I3 ran at 150 and 200, I5 at 186 and 50.

## V1
**PASS** (V1-C against the first 300 s of V1-B)
- Settings: H.264, hardware, 1080p, 60 fps, INFO. Radio: Native AA over WiFi Direct, phone D-POCO (192.168.49.136).
- Reachability: `Media Sink Setup Request: 3`, `maxUnacked=12`, 120 windows (B), 61 (C), fed median 29.
- Discard-rule check: 1 SSL, 1 `createGroup SUCCESS`, 0 `MATCH`, 0 `Magic Garbage`, F2 zero list 0 on C.

| measure | B (first 300 s) | C |
|---|---|---|
| rendered median | 29 | 29 |
| rendered p10 | 29 | 26 |
| inputWait mean ms | 65.5 | 63.5 |
| dropped sum | 0 | 0 |
| videoShed sum | 0 | 0 |
| videoQueue max | 4 | 4 |
| inbound video kB/s median | 308.5 | 273.5 |
| phone ack-timeout | 0 | 0 |
| phone out-of-order pings | 63 (whole file) | 0 |

The 10 min baseline alone read p10 24 and inputWait 60.8 ms.

## V2 and V2n
**PASS** (V2-C against the first 300 s of V2-B; V2-B and V2n-B PASS as baselines)
- H.265, sink codec 7, `maxUnacked=12`. Noise: V2-B and V2n-B rendered median 29 and 29 (0 percent), so V2-C is gradable for F1.

| measure | V2-B (300 s) | V2n-B (600 s) | V2-C |
|---|---|---|---|
| windows | 60 | 121 | 61 |
| rendered median | 29 | 29 | 29 |
| rendered p10 | 29 | 26 | 26 |
| inputWait mean ms | 60.9 | 61.5 | 65.2 |
| dropped / shed | 0 / 0 | 0 / 0 | 0 / 0 |
| phone out-of-order pings | 62 | 63 | 0 |

## V3
**PASS** (V3-C against the first 300 s of V3-B)
- Software HEVC, `ffmpeg-hevc`, `maxUnacked=6` (the narrow window the review flagged), no D-HU reboot (uptime 4:17 at the end of V3-C).

| measure | B (300 s) | C |
|---|---|---|
| rendered median | 29 | 29 |
| rendered p10 | 25 | 28 |
| dropped / videoShed | 0 / 0 | 0 / 0 |
| videoQueue max | 91 (600 s) | 70 |
| phone ack-timeout | 0 | 0 |
| phone out-of-order pings | 63 | 0 |

`fed` and `inputWait` read 0 on this path (Setup note 9), so F1 condition 3 is not measurable here; rendered median stands in.

## V4
**PASS** (V4-B and V4-C)
- VERBOSE, 20 skips with swipes. Reachability: B 44 metadata messages (38 first-fragment flags 9, 6 single), C 28 (27 and 1); video flags 9 and 10 present (491 each on B, 369 each on C); the track changed (Rail De Musique to DYWTYLM on B, Smooth Criminal to Heaven Can Wait on C).
- C: F2 zero list 0, `AapMediaPlayback: Failed to parse` 0, 0 `AapTransport quitting` inside the window (the `quitting (clean=false)` line comes after `V4-x-end`).

## V5
**INCONCLUSIVE**
- `[RES_CAP] resolutionId=5 ... chosen=_3840x2160 capped=_1920x1080`, equal to V2's `capped`. The unit's ceiling is already 1080p, so V5 duplicates V2. V5-B itself was clean (61 windows, fed 29, 1 SSL, 0 quit). V5-C not run.

## K - soak
**PASS** (K-B and K-C, 10 min instead of 30)
- B: 121 windows, longest zero-rendered run 0, 1 SSL, 0 quit, TOTAL PSS 96138 to 109182 KB (+13044). C: 121 windows, zero run 0, 1 SSL, 0 quit, F2 zero list 0, PSS 93449 to 92710 KB (-739). C rendered median 28, p10 15 (B 29 and 9).

## M - D-MOTO as the phone
**PASS** (M-B and M-C)
- D-MOTO (192.168.49.28), D-POCO Bluetooth off, `maxUnacked=30` on both. M-C is the last of several attempts (Setup note 6): 1 `createGroup SUCCESS`, 1 SSL, 0 quit.

| measure | M-B | M-C |
|---|---|---|
| windows | 60 | 60 |
| rendered median | 29 | 29 |
| rendered p10 | 29 | 25 (0.86 of B, limit 0.85) |
| inputWait mean ms | 64.7 | 65.5 |
| dropped / shed | 0 / 0 | 0 / 0 |
| phone out-of-order pings | 34 | 0 |

## I3
**PASS** (I3-C against I3-B, 3 independent single-fault sessions per build; one candidate session ended on a rig link stall, see below)
- Mode 3 (`DROP_LAST_FRAGMENT`), rate 150, budget 1, one fresh session per sample so no fault sits in another's cooldown. The earlier burst runs (3 faults 2 to 35 s apart, INCONCLUSIVE) are kept as evidence in the captures; this set replaces them for the verdict.

| sample | build | truncated ms | cycle ms | repair ms | retake repair ms | quit in window |
|---|---|---|---|---|---|---|
| s1 | B | 30 | 2038 | 2741 | 301 | 0 |
| s2 | B | 31 | 2038 | 2749 | 309 | 0 |
| s3 | B | 28 | 2036 | 2768 | 331 | 0 |
| s1 | C | 27 | 2035 | 2915 | 477 | 1 |
| s2 | C | 32 | 2041 | 2753 | 310 | 0 |
| s3 | C | 25 | 2032 | 2740 | 306 | 0 |

- Median repair B 2749 ms, C 2753 ms (limit 1.5 x 2749 + 1000 = 5124). Audit silent (0 `DELTA_CHANGED`) on all six, every repair non-null, 1 SSL each, `Got mismatch session id in ack` 0, `VIDEO_ACK_TIMEOUT` 0.
- **I3-C-s1 ended its session 63 s after the fault** (`AapRead: WiFi read timeout (15000ms) - connection lost` at 11:27:47.558, preceded by `picture idle for 11161ms and the link has been silent for 11173ms` at 11:27:43.716; no frames displayed after 11:27:29). The fault had been repaired at 11:26:44.114, so this is not the fault's recovery failing; D-POCO's WifiNetworkFactory was handling network requests at 11:27:28 to 11:27:30, which matches the known periodic link stall on that phone. By the letter of the brief (`AapTransport quitting` must be 0) that sample fails; I excluded the session end as a rig event and graded on the fault evidence. A reviewer who reads the rule literally should treat I3-C as one failing sample in three. The other candidate sessions, and all baseline sessions, ran clean.

## I5
**PASS** (I5-C against I5-B, 3 independent single-fault sessions per build)
- Mode 5 (`DROP_MIDDLE_FRAGMENT_IN_READER`), rate 100, budget 1. The fault lands in the first second after SSL, before the early start marker, so these runs were extracted from the first `SSL handshake complete. ` line (the extractor got a `START_PAT` option) and the fault and its recovery are inside the window.

| sample | build | discard ms | audit ms | cycle ms | repair ms | retake repair ms |
|---|---|---|---|---|---|---|
| s1 | B | 14 | 6 | 2015 | 2723 | 306 |
| s2 | B | 11 | 7 | 2010 | 2691 | 280 |
| s3 | B | 13 | 8 | 2013 | 2702 | 287 |
| s1 | C | 13 | 7 | 2013 | 2711 | 295 |
| s2 | C | 16 | 9 | 2018 | 2645 | 226 |
| s3 | C | 13 | 7 | 2015 | 2703 | 285 |

- Detection on every fault (discard at most 16 ms, audit at most 9 ms), median repair B 2702 ms, C 2703 ms (limit 3.7 s), 0 `AapRead: invalid framing or TLS session`, 0 quit, 1 SSL, 1 `DELTA_CHANGED` each, `VIDEO_ACK_TIMEOUT` 0.

## O1 - D-SAM, API 19
**PASS** (O1-B and O1-C, 5 min)
- `connection-modes {wifi}` (Setup note 4), Marvell CODA7542 H.264, `[RES_CAP] ... linkCapped=_1280x720`, `maxUnacked=12`, phone D-POCO (192.168.49.251).

| measure | O1-B | O1-C |
|---|---|---|
| windows | 60 | 60 |
| rendered median | 29 | 26.5 (0.91 of B) |
| rendered p10 | 15 | 13 |
| inputWait mean ms | 386.5 | 116.6 |
| dropped / shed | 0 / 0 | 0 / 0 |
| SSL / quit in window | 1 / 0 | 1 / 0 |
| phone out-of-order pings | 40 | 6 |

- Baseline `unwrap produced no application data` count 0 (the S1 trigger was not present). The candidate printed 11 `Audio transport read channel=` lines (new diagnostic, not in the zero list). F2 zero list 0 on C.

## O2 - D-HP, Headunit Server
**PASS** (O2-BASE and O2-CAND, 5 min each, two runs per build)
- D-HP (API 17, debuggable) head unit, mode 1 (`Initializing WiFi Mode: AUTO`), `[RES_CAP]`/codec H.264 on `OMX.Nvidia.h264.decode`, phone D-MOTO at 192.168.1.5 with the Android Auto head unit server on `:5277` (the app connected by itself, no explicit connect verb). Each build ran twice back to back on one fresh server start (the second run with no restart).

| measure | BASE-1 | BASE-2 | CAND-1 | CAND-2 | old e9a030de4 (1, 2) |
|---|---|---|---|---|---|
| windows | 61 | 61 | 60 | 60 | 61, 60 |
| rendered median | 29 | 29 | 29 | 29 | 29, 29 |
| rendered p10 | 29 | 29 | 29 | 29 | 29, 29 |
| inputWait mean ms | 198.6 | 190.7 | 225.0 | 207.3 | 210.0, 204.5 |
| dropped / shed | 0 / 0 | 0 / 0 | 0 / 0 | 0 / 0 | 0 / 0 |
| SSL / quit in window | 1 / 0 | 1 / 0 | 1 / 0 | 1 / 0 | 1 / 0 |

- F2 zero list 0 on both candidate runs; the candidate printed `Audio transport read channel=` 12 and 4 times (new diagnostic, not in the list). Candidate rendered median is 1.00 of baseline.
- **The earlier failures were my harness, not the phone or the build.** The brief's bring-up sends `ACTION_CONNECT` 20 s after launch, but in mode AUTO the app auto-connects to the server on its own within about a second. The verb then preempts that socket (`ConnectionArbiter: ... (USER) preempts the socket from 192.168.1.5 (WIRELESS_HANDSHAKE)`), the server is still tearing the first connection down, and the new connect times out after 5000 ms or reads `the peer accepted the connection and then sent nothing`. That reproduced on both builds (baseline 27 failed cycles in 6 min) and on the first night's attempt. With the verb removed (sent only if nothing connects in 45 s) every run connected first time and held 5 min. An older commit (`e9a030de4`, 11 commits behind the baseline) behaves the same way, so there is no regression in this path.
- Needing a server restart: only after a force-stop of Android Auto on the phone (used to clear the wedged state); two back-to-back runs per build needed no restart.

## U1 - USB, standard path, reconnect after a session
**INCONCLUSIVE**
- Host D-MOTO (Android 14, `host_connected=true`, wireless adb at its LAN address), device D-POCO over OTG (`18D1:4E11`, accessory `18D1:2D01`), mode 0, H.265, 1080p, `use-libusb` false, INFO. Our APK had to be installed on D-MOTO (it had none); settings written with `set_prefs_runas_host.py`, `connection-modes {usb}`.
- **First pass (10 sessions, cable handled by the operator between sessions, so void for comparison):** baseline 6 of 10 sessions formed within 90 s, candidate (first attempt) 1 of 1 then the run was stopped. The sessions that formed coincide with USB re-enumerations (D-POCO's port re-enumerates when the cable is moved).
- **Second pass, hands off the cable (U1-B-h, U1-C-h, U1-OLD-h; 1 cold-start state plus 3 scripted reconnects each):** D-POCO sat in accessory mode `18D1:2D01` from the earlier sessions and nothing re-enumerated it. All three builds behave identically: 3 connect attempts, 3 `Handshake: Version request/response failed after 3 attempt(s). last ret: -1` (first write fails, `ConnectionArbiter: ... ended with no session`), 0 SSL, 0 USB attach or detach events, 0 re-switches to accessory mode. The builds are the baseline `7e9d813d`, the candidate `dfb0d6f9` and the older `e9a030de4` (11 commits behind the baseline), so there is no regression between them.
- The operator also pressed the in-app USB button twice on the baseline in that state: the log shows `USB button: Single device found ... PID: 2D01, auto-connecting`, `Found device already in accessory mode`, then the same three failed handshake writes. I could not see from these logs why D-POCO's accessory side stays unresponsive; only a re-enumeration (a fresh plug) recovers it.
- No verdict on build differences can come from this: S(B) depends on whether the phone re-enumerated, not on the build.

## U2 - USB, libusb path, cold plug
**PASS** (U2-B and U2-C, one session each after a cold plug, 60 s hold)
- `use-libusb` true, `maxUnacked=16`, H.265 on `c2.qti.hevc.decoder`, 11 windows, rendered median 29 and p10 29 on both, inputWait 95.7 (B) against 101.7 ms (C), 0 dropped, 1 SSL each. F2 zero list 0 on C. Permission requests: 1 each, identity `18D1:2D01` (the post-switch identity; the pre-switch `4E11` prompt did not appear on these plugs, so the template's two-prompt count was not reproduced). Only one session per build, not the brief's ten, so this speaks to the libusb path forming a session and nothing about reconnect rates.

## U3 - USB, standard path, 20 track skips
**PASS** (U3-B and U3-C, one cold-plug session each, about 4 min, INFO)
- 42 windows on both, rendered median 29, p10 29, inputWait 97.3 (B) against 100.9 ms (C), 0 dropped, 1 SSL, `maxUnacked=16`, F2 zero list 0 on C, `AapMediaPlayback: Failed to parse` 0 on C. **The baseline printed `AapMediaPlayback: Failed to parse` 1 and `Failed to parse metadata (` 1** in the same window (the old spelling), which the candidate did not.
- The metadata path is only partly confirmed: at INFO there are no `RECV` lines, so the fragment counts the brief asks for are not measured, and the playing track changed once per run (B: Blood on the Dance Floor to Get on the Floor; C: Get on the Floor to Tabloid Junkie), not 20 times, so most of the 20 skips did not land. The zero list is graded; the metadata-fragment half is INCONCLUSIVE.

## D1, D2 - USB with the wireless AA dongle
**INCONCLUSIVE** (D1-B on libusb and D2-B on the standard path; candidate arms not run, per the brief's stop rule)
- Host D-POCO (head unit, wireless adb), dongle (`Pixel 4`, `18D1:4EE1` idle, `18D1:2D00` accessory) plugged and unplugged by the operator on cue, D-MOTO paired and in reach, mode 0, H.265, `connection-modes {usb}`, INFO. Our debug APK had to replace D-POCO's release build (installed through Obtainium, different signature): the operator chose export-then-uninstall; D-POCO's settings were exported with `ACTION_GET_SETTINGS` (79 keys, 9 credential-bearing keys withheld by the app) to `d-poco-original/d-poco-settings-export.json`, then the app was uninstalled and the baseline installed. The release build and its data still need restoring by the operator.
- **D1-B (libusb), 5 plugs:** 5 of 5 formed a session (SSL after 5 to 49 s), `maxUnacked=16`, 29 fps rendered, **0 plugs printed `SSL Handshake: discarded a late VERSION_RESPONSE` and 0 printed `still receiving late VERSION_RESPONSEs at the deadline`**. Permission requests: `4EE1` 5 times (once per plug) and `2D00` once.
- **D2-B (standard), 5 plugs:** 5 of 5 formed a session (plug 4 formed at 13:55:03, but the script's own counter read NO-SSL because the operator plugged a few seconds before the cue), 29 fps, 0 and 0 on both late-response lines.
- The brief's pre-registered outcome applies to both paths: the baseline's first 5 plugs print neither line, so the behaviour is not exercised on this dongle and rig, and the run stops for that path. The candidate was not run. Three D1-B windows show an `AapRead: Connection lost` that is the previous plug's session ending about 40 s after its unplug, landing inside the next plug's window because the next start marker precedes it; not a defect.
- D-POCO's Bluetooth turned itself off about 40 s after every enable (`disable(com.android.systemui, true)` in its system log); it is the head unit here and its Bluetooth is not part of this path (operator), so the brief's "Bluetooth stays on" was not met and did not matter.

## F-summary

| F | arm(s) | verdict | the deciding number |
|---|---|---|---|
| F1 | V1, V2, V3 | PASS | rendered median 29 on both builds in all three; software HEVC (window 6) p10 28 against 25; phone ack-timeouts 0 |
| F2 | all healthy runs run | PASS | zero list 0 on V1-C to V4-C, K-C, M-C, O1-C; 1 SSL each; 0 session ends in window |
| F3 | I3, I5 | PASS | 3 independent samples per arm; median repair 2753 against 2749 ms (mode 3), 2703 against 2702 ms (mode 5); one candidate session ended on a link stall, see I3 |
| F4 | I3 | PASS | retake repair 477, 310, 306 ms (3 sessions) and 307 and 299 ms (burst run, session 1 then 2); 0 id mismatches, 0 `VIDEO_ACK_TIMEOUT`, longest zero-rendered run 2 windows |
| F5 | O1, O2 | PASS | O1 rendered 26.5 against 29, 1 SSL, 0 quit; O2 rendered 29 on both builds, 1 SSL, 0 quit |
| F6 | U1, U2, U3, D1, D2 | U1 INCONCLUSIVE, U2 and U3 PASS, D1 and D2 INCONCLUSIVE | U2 and U3 formed a session on a cold plug and matched the baseline (29 fps, 1 SSL); U1 reconnects fail identically on baseline, candidate and the old commit |
| soak | K | PASS | 0 session ends in 10 min; PSS -739 KB against +13044 KB on baseline |

## Round verdict: INCONCLUSIVE

No run failed. F1, F2, F3, F4, F5, U2, U3 and the soak passed on every arm that ran; F6 is only partly decided (U1 inconclusive; D1 and D2 not exercised on the baseline, so the candidate arms were not run), and one candidate session in F3 ended on a rig link stall that I excluded (see I3).

## Anything the brief did not ask about

- The candidate's ping responses vanish on the phone side as expected: out-of-order pings were 0 on every candidate run against 34 to 63 on the baseline, and the phone's `Waiting for ack timeout` count was 0 on both builds in every run, so the narrower ack window did not show up as ack starvation at these frame rates.
- The injector's candidate rate moves by an order of magnitude between runs and its faults arrive in bursts, which makes a 60 s cooldown comparison with independent samples hard to reach; a future brief should fix the rate and take the first fault of each of several short sessions.
- A fresh `settings.xml` restore from a stale file in `/data/local/tmp` is a silent failure mode for any reset script that does not name the device on every `adb` call.
- `cmd statusbar collapse` does not clear a PIN keyguard on D-MOTO; the brief's lock-state grep reads `isKeyguardShowing`.
- The Android Auto head unit server on a phone can bind IPv6 only; the brief's IPv4 `/dev/tcp` probe then reads DOWN while the app's own connect times out.
- The brief's Stage C bring-up (explicit `ACTION_CONNECT` after launch) races the app's own auto-connect in mode AUTO and makes the first O2 attempts fail on both builds; a future brief should wait for the auto-connect and send the verb only as a fallback.
- Captures: asset `pr-1045-framing-round1-captures.zip` (233 MB, 456 files) of the release `rig-evidence-pr-1045-framing` on `o-jcardenass/open-headunit`, sha256 `169bcc701fcc2894a98fb47f110347792ba5711246549998b6fd452c331b8a7c`. The settings backups and the D-POCO settings export are not in it (they carry location and device identifiers).
