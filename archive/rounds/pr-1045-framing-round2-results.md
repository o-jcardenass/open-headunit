# pr-1045-framing, round 2 results

- **Candidate:** local merge `8d7da0e94fd5` (PR head merged onto main `7102b428`), APK `C1045-8d7da0e94.apk`, md5 633a1fec957223cfb98991aa9c6cba2e
- **Baseline:** main `7102b4283666`, APK `B-7102b4283.apk`, md5 20838f1a13f02572253b0de1e52ab745 (the brief's baseline md5 is a different, non-reproducible debug build; trees match)
- **Unit:** stage A: D-HU (UNISOC MT50, rooted) as head unit with D-POCO (POCO X3 NFC, Gearhead 17.9.664004-release) as phone, Native AA (wifi-connection-mode 3). Stage D: D-MOTO (motorola edge 30 neo) host with D-POCO device over OTG
- **Date:** 2026-10-08 (stage A 07:55 to 09:16, stage D 10:03 to 11:05)
- **Evidence:** release `rig-evidence-pr-1045-framing`, asset `pr-1045-framing-round2-captures.zip` sha256 4b353b5365e03566f589e4b8267f66f50dd26b35a7212655d3a3ba46cee3c46b (121 MB; stage A captures and stage D captures)

## Setup notes

- Round 2 was started the evening before; a first baseline attempt was stopped by the operator mid-A2 and is void. Its captures were moved aside and the whole stage was rerun from the baseline arm.
- Two environment slips cost two relaunches (D-MOTO Bluetooth had come back on after a power cycle; the helper env vars HU, PH, PKG were unset). Neither produced data.
- D-MOTO Bluetooth off (verified by `dumpsys bluetooth_manager` state OFF) before each arm. D-POCO bonded list quoted at preflight: includes Navegadortz2 and Navegadortz3; left alone.
- **Phone title not readable.** `playing_title $PH` printed nothing in every run (`*.before.txt` empty, `titles` shows `phone:` blank), although media was playing (the app side shows a title and A1 counts 20 music-playback runs). So the parse-proof condition (app title equals phone title) cannot be graded on either arm. Said per run below.
- **A2 did not reach its load.** `fed_fps_median` is 13 (B) and 9 (C) against the brief's reachability floor of 25, and `audio_nonzero_fraction` is 0.525 and 0.5 against 0.8. The C arm also carried far less video than B (inbound video third medians 30, 18, 10.5 kB/s against 257, 258, 253), so the two arms did not show the same phone content. This is phone-side navigation state, not a measurement of the candidate.
- `THROTTLE_LIMIT_EXCEEDED` appears in A1, A2 and I5 captures. Every hit is Gearhead connection-logger metadata (`GH.ConnLoggerV2`) at setup (A1-B 07:58:35 to 07:58:40; A2-B 08:04:08 to 08:04:22) or just after the window ends (A2-B 08:25:31); none inside a session window. Sessions were not interrupted, so no phone reboot or restart was done.
- D-HU settings were restored byte-identical to the stage backup after the stage (owner u0_a176:771). `ps aux | grep logcat` = 0; rig lock free.
- Valid I5 samples: arm B s1, s2, s3 (s3 on the 4th attempt, first attempt injected no fault); arm C s1, s2 (3rd attempt), s3 (4th attempt). Void attempts kept in the captures, not graded.
- **Stage D wiring.** D-MOTO and D-POCO were switched to wireless adb (`adb tcpip 5555`, 192.168.1.5 and 192.168.1.8) so both USB ports were free; D-POCO plugged into D-MOTO's USB-C port over the OTG adapter, cable untouched during each arm except for the two requested cold plugs (U1-B s1, U1-C s1). D-MOTO was not PIN-locked (`isKeyguardShowing=false`). The app was not installed on D-MOTO at the start; the baseline was installed, run once and `settings.xml` backed up, then the candidate installed over it. At the end D-MOTO's settings were restored byte-identical to that backup; the candidate APK was left installed.
- **Helper bugs found in stage D (host scripts, not the app).** Round 1's `wait_more` and my `usb_dialog` wrote the caller's shell variable `f`, so `u1_session` printed `NO-SSL` for U1-B sessions 2 to 5 although each session formed (SSL handshakes at 10:10:05, 10:12:18, 10:14:49, 10:17:27) and then sent a late `ACTION_CHECK_USB` (10:10:30, 10:12:37, 10:15:40, 10:18:17), always after the session had already formed and never inside the 30 s window the brief allows. U1-B sessions 2 to 5 are graded from the capture (all SSL-AUTO). Session 6 and the whole of U1-C and U2S ran with the fixed `wait_more` (`pr1045r2_helpers.sh`) and sent no verb. The stray verb did not end any session.
- **U1 window artefact.** `u1_session` sends `ACTION_DISCONNECT` at the start of each scripted session, so each session window (s2 to s6) holds the previous session's end: `AapTransport quitting (clean=false)` about 1.3 s after the start marker and `AapRead: Connection lost` about 5 s later, on both arms (B s2 10:09:45, C s2 10:41:17). Those two hits per window are excluded from the zero-list; no session ended inside its own 45 s.
- **Stage D phone title.** `phone:` is blank in U2S-B and U2S-C titles too (same helper), so the parse proof is ungradable on stage D as well.
- D-MOTO Bluetooth was turned back on at the end (state ON); D-HU and D-POCO apps force-stopped; no logcat left running; rig lock free.
- Gearhead `versionName` on D-POCO: 17.9.664004-release.
- Scripts added (in `hur-wifi-test-scripts/`): `pr_r2_build_all.sh`, `extract_brief_blocks.py`, `pr1045r2_helpers.sh`, `pr1045r2_extract.py`, `pr1045r2_stageA_arm.sh`.
- Thermal: host max 73C over all runs, `throttle_delta=0` on every `th_report`.

## R0 - gate

**PASS**

- Candidate 2855 tests, 0 failures; baseline 2820, 0 (JUnit XML counts from the build log).
- The five named candidate tests are present in the candidate's XML with 0 failures.
- `ident2 base`: `commit":"7102b4283666"`, `drop-credit-symbol-count=0`, IDENT-OK. `ident2 cand`: `commit":"8d7da0e94fd5"`, `drop-credit-symbol-count=1`, IDENT-OK.

## A1

**INCONCLUSIVE**

| | A1-B | A1-C |
|---|---|---|
| window | 07:59:49 to 08:03:36 | 08:38:54 to 08:42:41 |
| `complete_runs` | 20 | 20 |
| `singles` | 58 | 55 |
| `max_fragments` | 11 | 10 |
| `max_run_bytes` | 162988 | 153180 |
| `runs_with_3plus_fragments` | 20 | 19 |
| `orphan_fragments` | 0 | 0 |
| `zero_list_total` | 1 (`AapMediaPlayback: Failed to parse`) | 0 |
| baseline-only `Failed to parse metadata (` | 1 | 0 |
| `AapRead: skipped message: ` | 0 | 0 |
| `AapTransport quitting` in window | 0 | 0 |
| rendered fps median | 29 | 29 |
| phone `Critical error` / `VIDEO_ACK_TIMEOUT` | 3 / 0 | 3 / 0 |

Reachability met: 19 runs of 3 or more fragments on the candidate (floor 1), 20 complete runs (floor 10). Conditions 1, 2 and 4 hold. The baseline lost one metadata message to a parse failure that the candidate does not show (an improvement worth stating). Condition 3 (parse proof) is **ungradable**: `A1-B.titles.txt` and `A1-C.titles.txt` have an empty `phone:` line. App titles: B `Inside the Cage, Juliette & The Licks, Four on the Floor`; C `Manuel Santillán, el León (Versión Reggae) - Remasterizado 2008, Los Fabulosos Cadillacs, El León`. Graded INCONCLUSIVE for that reason alone; every other condition is met. thermal 59C, delta 0.

## A2

**INCONCLUSIVE**

| | A2-B | A2-C |
|---|---|---|
| window | 08:05:22 to 08:25:27 | 08:44:27 to 09:04:32 |
| windows | 241 | 239 |
| `fed_fps_median` | 13 | 9 |
| rendered fps median | 13 | 9 |
| rendered by third | 14, 13, 11 | 10, 9, 7 |
| `longest_zero_rendered_run_windows` | 2 | 2 |
| `dropped_sum` / `videoShed_sum` | 0 / 0 | 0 / 0 |
| `inputWait_mean_ms` | 27.9 | 21.0 |
| inbound video kB/s by third | 257, 258, 253 | 30, 18, 10.5 |
| inbound audio kB/s by third | 14, 1, 4.5 | 13, 0, 5.5 |
| `audio_zero_lines` | 19 | 20 |
| `audio_nonzero_fraction` | 0.525 | 0.5 |
| `zero_list_total` | 1 (the baseline parse failure) | 0 |
| `audio_timing_non_audio_channel` | 0 | 0 |
| `AapRead: skipped message: ` | 0 | 0 |
| skips in `skips.log` | 17 | 17 |
| TOTAL PSS start / end (kB) | 93933 / 105188 | 91596 / 103933 |
| PSS growth | 11255 kB | 12337 kB |
| phone out-of-order pings | 125 | 0 |
| phone `Waiting for ack timeout` / `VIDEO_ACK_TIMEOUT` / mismatch ack | 0 / 0 / 0 | 0 / 0 / 0 |
| phone `Critical error` | 3 | 3 |

Reachability failed on both arms (`fed_fps_median` below 25; audio fraction below 0.8), so the load the soak was meant to apply was not applied and conditions 3, 4, 6 and 9 cannot be read as a regression test. Taken at face value the candidate's rendered fps is 0.69 of B's (9 against 13), under the 0.90 bar, but the arms carried different video content (above), so I do not attribute it to the candidate. Conditions that are independent of load hold on C: zero list 0, no session end, PSS growth within B plus 50 MB, no non-audio-channel timing line, no ack timeouts. The parse proof is ungradable (empty `phone:` line). App titles: B `Set Fazers, Skindred, Smile`; C `21 Guns, Green Day, 21st Century Breakdown`. A rerun of both arms with the phone's navigation view confirmed on screen first is the way to turn this into a verdict.

## I5

**PASS**

| Run | fault at | discard_ms | audit_ms | cycle_ms | repair_ms | last-third fps | `AapRead: skipped message` | ack timeouts / mismatch / VIDEO_ACK_TIMEOUT |
|---|---|---|---|---|---|---|---|---|
| I5-B-s1 | 08:27:04 | 14 | 9 | 2014 | 2858 | 21.5 | 0 | 0 / 0 / 0 |
| I5-B-s2 | 08:29:32 | 10 | 5 | 2012 | 2833 | 29.0 | 0 | 0 / 0 / 0 |
| I5-B-s3-a4 | 08:35:41 | 7 | 3 | 2008 | 2827 | 29.0 | 0 | 0 / 0 / 0 |
| I5-C-s1 | 09:06:06 | 12 | 7 | 2013 | 2783 | 29.5 | 0 | 0 / 0 / 0 |
| I5-C-s2-a3 | 09:12:30 | 21 | 15 | 2022 | 2858 | 29.0 | 0 | 0 / 0 / 0 |
| I5-C-s3-a4 | 09:14:36 | 61 | 54 | 2061 | 2962 | 29.5 | 0 | 0 / 0 / 0 |

Each valid session has exactly one `FAULT INJECTED`. Detection on every candidate fault is at most 61 ms (bar 1500). Repair on every candidate fault: median 2858 ms against baseline median 2833 ms (bar 1.5 times plus 1000). No `invalid framing or TLS session` and no session end inside any candidate window. The stream continues after the drop (last third at least 29 fps on all three candidate sessions). The in-session zero-list hits are the injected fault's own `AapVideo: discarding a` and `AapRead: DELTA_CHANGED on`, one of each per session on both arms. Phone `Critical error` 3 on every session, both arms.

## CR

**PASS-EXEMPT: not exercised for reassembler drops** (the brief's pre-registered outcome; graded as no FAIL). `skipped_messages` is empty on every candidate run (A1-C, A2-C, I5-C-s1, s2, s3). The credit-evidence for this round is therefore A2 and I5, with A2 inconclusive for load as above.

## P-U

**PASS**

- With the app stopped on D-MOTO and a `UsbHostManager` reader running, `adb -s $PH shell svc usb resetUsbGadget` produced a fresh `Added device` within 20 s (`PU-ENUM`): `10-08 10:03:27.307 Added device UsbDevice[mName=/dev/bus/usb/002/002,mVendorId=6353,mProductId=19985,...mManufacturerName=Xiaomi,mProductName=POCO X3 NFC`. So `RESET` is `svc usb resetUsbGadget` for U1 and U2S.

## U1

**PASS**

Settings: `use-libusb` false, `wifi-connection-mode` 0, `connection-modes` {usb}, `log-level` 2, `screen-orientation` 2, `video-codec` H.265, `sync-media-session-aa-metadata` true; `video-profile-starvation-cap`, `native-aa-wireless`, `wifi-launcher-mode` and the three `debug-video-fault-*` keys absent (read back).

| Session | U1-B | U1-C |
|---|---|---|
| s1 (operator cold plug) | SSL 10:08:06.106 | SSL (formed, `s1 SSL`) |
| s2 | ENUM, SSL-AUTO 10:10:05.842 | ENUM, SSL-AUTO |
| s3 | ENUM, SSL-AUTO 10:12:18.025 | ENUM, SSL-AUTO |
| s4 | ENUM, SSL-AUTO 10:14:49.934 | ENUM, SSL-AUTO |
| s5 | ENUM, SSL-AUTO 10:17:27.614 | ENUM, SSL-AUTO |
| s6 | ENUM, SSL-AUTO | ENUM, SSL-AUTO |

`S(B)` = 6, `S(C)` = 6, `E` = 5 on each arm (all 5 scripted sessions re-enumerated). `context.maxUnacked` is 16 on all 12 sessions; rendered fps median 29 on every one. Candidate zero-list per session: 0 in s1; in s2 to s6 only the previous-session disconnect pair described in Setup notes (B has the same pair). `AapRead: Error in processBulk`, `AapRead: FIFO overflow` and `AapRead: skipped message: ` are 0 on every candidate session. No formed session ended inside its 45 s. Permission requests per session: `Requesting USB permission for Xiaomi POCO X3 NFC (VID: 18D1 PID: 4E11)` then `(VID: 18D1 PID: 2D01)` (B s1: PID 2D01 at 10:08:04.648); the USB dialog cue fired once in B s2 and needed no tap. thermal max 68C (B) and 67C (C), delta 0.

## U2S

**INCONCLUSIVE**

Settings: as U1 with `use-libusb` true. Reachability met on both arms: 183 and 184 throughput windows (floor 165), `maxUnacked` 16, `skips.log` 20 lines on each, B `audio_nonzero_fraction` 1.0.

| measure | U2S-B | U2S-C |
|---|---|---|
| window | 10:23:06 to 10:38:22 | 10:49:29 to 11:04:49 |
| rendered fps median | 29 | 29 |
| rendered by third | 29, 29, 29 | 29, 29, 29 |
| `dropped_sum` | 0 | 0 |
| `inputWait_mean_ms` | 132.1 | 127.0 |
| `longest_zero_rendered_run_windows` | 0 | 0 |
| inbound audio kB/s by thirds | 187, 187, 187 | 187, 187, 187 |
| `audio_zero_lines` | 0 | 0 |
| `zero_list_total` | 1 (baseline-only `Failed to parse metadata (`) | 0 |
| `AapRead: skipped message: ` | 0 | 0 |
| `AapTransport quitting` in window | 0 | 0 |
| SSL handshakes in file | 1 | 1 |
| `audio_timing_non_audio_channel` | 0 | 0 |
| TOTAL PSS start / end (kB) | 230737 / 266872 | 230456 / 260769 |
| PSS growth | 36135 kB | 30313 kB |
| phone `Critical error` | 2 | 2 |
| phone `Waiting for ack timeout` / `VIDEO_ACK_TIMEOUT` / mismatch ack | 0 / 0 / 0 | 0 / 0 / 0 |
| phone out-of-order pings | 0 | 0 |

Conditions 1 to 5 and 7 are met. USB carries no ping, so the phone-side credit control is unconfirmed (zero on both arms, as the brief expects to report). Condition 6, the parse proof, is ungradable: the `phone:` line is empty (`U2S-B.titles.txt`, `U2S-C.titles.txt`); app titles B `Another Life, Motionless In White, Disguise`, C `Something About Us, Daft Punk, Discovery`. Graded INCONCLUSIVE for that reason alone; the candidate shows no regression on any measured number.

## Summary

| question | runs | verdict | the deciding number |
|---|---|---|---|
| copy path carries album art | A1 | INCONCLUSIVE | 19 runs of 3 or more fragments, zero list 0, 0 parse failures (baseline had 1); phone title unreadable |
| no regression under load | A2, U2S | INCONCLUSIVE | A2 load not reached (fed fps 13 and 9); U2S 29 fps both, PSS growth 30.3 vs 36.1 MB, parse proof ungradable |
| credits never leak | CR, A2 4/6/9, I5 4/6, U2S 4/7 | not exercised | 0 `skipped message` entries on every candidate run; I5 4/6 and U2S 4/7 met |
| reader-stage repair | I5 | PASS | candidate repair median 2858 ms vs baseline 2833 ms, detection at most 61 ms |
| USB reconnect | U1 | PASS | S(B)=6, S(C)=6, E=5 |

## Round verdict: INCONCLUSIVE

No run failed a stated condition. A1, A2 and U2S are INCONCLUSIVE because the phone-side title could not be read (all three) and, for A2, because the heavy-video load was not reached on either arm.

## Anything the brief did not ask about

- Phone-side `Received out of order ping response` was live on the baseline arm (27 in A1, 125 in A2, 8 to 9 per I5 session) and 0 on every candidate run. Same phone, same Gearhead version; the arm order (baseline first) is the only difference I can name. Not graded; noted for the next round on this phone.
- `playing_title` returning nothing on D-POCO while media plays looks like a helper issue on this phone's `dumpsys media_session` format, not an app issue. Worth fixing before the next round that needs a parse proof.
