# pr-1067-tls-pump, round 2 results

**Candidate:** PR head `7e7cbf53` merged onto main `7102b428`, tree `9a77cc00a41e65d1b4fb9c69bb740c211b798de9`, local merge commit `db3153cddb75`        **Baseline:** main `7102b4283666`, tree `e96aa72a2ff556c9d1a5733c07d69c1b183a1b61`
**APK md5:** candidate `7df7c7f5cbad7f4b6928e4c90068986c` / baseline `20838f1a13f02572253b0de1e52ab745` (the baseline md5 is the one built for the sibling round today; the earlier main-smoke md5 `62ccbb85...` is a different, non-reproducible debug build, trees match)
**Unit:** D-HU (UNISOC MT50, Android 14, rooted) with D-POCO (POCO X3 NFC, Gearhead 17.9.664004-release) over Native AA (`wifi-connection-mode` 3), VERBOSE (`log-level` 0)
**Date:** 2026-10-08 (arm B 11:42 to 12:17, arm C 12:17 to 12:51)
**Evidence:** release `rig-evidence-pr-1067-tls-pump`, asset `pr-1067-tls-pump-round2-captures.zip`, sha256 58bc868042983c188db496f943663a74e635271f08b4e9f1731b1e3b99eb12ee (142 MB: both arms, probes and the abandoned partial attempts)

## Setup notes

- **The phone exit lever differs from the brief (the one real deviation).** The brief's H1 (tap Exit in Android Auto's UI on D-HU, then Finish later) did not work on this build: Exit arrives as `Video Focus NATIVE received. User clicked Exit in Android Auto` and the app follows its `aa-exit-action` setting (value 0, the default `OEM_LAUNCHER`, added after the earlier rounds that used this lever) with `ExitAction: Minimizing projection to OEM Launcher` at 11:20:53 and 11:22:26. The head unit goes to its launcher, no Finish later dialog appears on D-HU or on D-POCO, and no ByeBye is sent (three attempts by the operator). A probe with `aa-exit-action` 2 (disconnect) made the app end the session itself (`Disconnecting projection session` 11:27:51.795, `AapTransport quitting (clean=false)` 11:27:51.949) and no phone ByeBye, so it is not the lever either. The setting was left at the backup value for every graded run.
- **The lever used instead: the phone's own "Disconnect" action on Gearhead's "Android Auto is connected" notification on D-POCO.** It is the phone ending the session on a user request. Probe (default settings): `!!! RECEIVED BYEBYE REQUEST FROM PHONE !!! Reason: USER_SELECTION` 11:29:33.742, `AapTransport quitting (clean=true)` 11:29:34.247 (505 ms). It is the same ByeBye path the brief grades, and it is identical on both arms. It was scripted over adb on D-POCO: the notification shade is opened (`cmd statusbar expand-notifications`), a screenshot is read by `find_disconnect.py` (the label is salmon `255,180,169`; `uiautomator` cannot see the shade, it only dumps Maps' window), a collapsed card is expanded by its chevron, then the label is tapped. The operator tapped by hand for 2 early exits of an abandoned attempt and for XW-B-x8 (the Google weather card moved the layout and the script expanded the wrong card). Graded exits: arm B 9 scripted and 1 by the operator; arm C 10 scripted. Every exit was verified by the ByeBye line, not by the tap.
- **Abandoned attempts, kept in the folder, not graded:** `attempt1-B-partial` (2 operator exits, 1st lever version), `attempt2-B-partial` (1 operator exit), `attempt3-B-partial` (1 operator exit, scripted tap missed a collapsed card), `probes/` (the two probes above). Arm B was restarted three times to improve the tap script; the graded arm B is the fourth run. Arm B's final script is `pr1067_arm_B-as-run.sh`; arm C ran a refined `phone_disc` (it tries each salmon-icon card in turn; arm B's version tapped the lowest one).
- **Rig:** `rig_preflight.sh D_HU:wifi,bt D_POCO:wifi,bt D_SAM` passed. D-MOTO's Bluetooth was off (state OFF); D-SAM's Bluetooth still read on (`bluetooth_on` 1, no HFP link) after the operator's attempt to turn it off, because Android 4.4.2 has no adb verb for it; it served no link. D-POCO's bonded list unchanged; its screen was on a lock screen at first and was unlocked with a swipe; its battery was 15% and on AC.
- `enable-audio-sink` reads `false` on D-HU (the backup), so this round's load is **video only**: `media_start_audio` is 0 and there is no inbound audio on either arm. `resolutionId` 2, `video-codec` H.264, `audio-latency-multiplier` 8, `audio-queue-capacity` 20, as found. `stat` of `shared_prefs` on D-HU: `u0_a176:u0_a176`, `settings.xml` `u0_a176:771`. Settings restored byte-identical to `settings-backup-DHU.xml` after each arm.
- **A stale folder** from an earlier partial attempt (2026-10-07 00:55, stopped at the first exit cue) was moved to `old-20261007/`.
- Scripts in the folder: `ohu_lib.sh`, `ohu_setkeys.py`, `ptr_lib.sh`, `tp_lib.sh`, `tp_extract.py` (the five the brief lists), plus `pr1067_arm.sh`, `pr1067_arm_B-as-run.sh`, `find_disconnect.py`, `probe_exit.sh`, `probe_notif.sh`.
- Host thermal: XW-B 73C, KW-B 78C, XW-C 78C, KW-C 76C, `throttle_delta` 0 on all four.
- A leaked `th_watch` loop held the rig lock between launches (the known trap); killed by pid.

## R0 - gate

**PASS**

- JVM tests: B 2820, 0 failures; C 2827, 0 failures (JUnit XML counts from the build log). The eight C-only classes (`TlsApplicationPumpTest`, `AapTlsWriterTest`, `AapSslSessionTest`, `TlsEmptyTailReaderTest`, `TlsVideoAccountingTest`, `PeerCloseReasonTest`, `ApplicationSendReadinessTest`, `MicrophoneWriteFailureTest`) exist and pass on C and not on B.
- md5s differ (above). Trees verified: B `e96aa72a...`, C `9a77cc00...` with the PR head `7e7cbf53` an ancestor.
- Identity: `encrypted write failed or incomplete` in the dex: B 0, C 1. `ACTION_QUERY_STATE` commit: B `7102b4283666`, C `db3153cddb75`. `apk_check` live md5 equals the wanted one on every run (`apk-check.log`).

## XW-B

**INCONCLUSIVE**

See XW-C for the pair verdict. Arm B: 10 graded exits, `d` 0, `e_B` 0.

| exit | lever | bye to quit ms | first quit | state reason | finish clean | enc write failed (bye to quit) | send failed (bye to quit) | reconnect |
|---|---|---|---|---|---|---|---|---|
| XW-B-x1 | script | 504 | clean=true | phone_left | 1 | 0 | 0 | verb |
| XW-B-x2 | script | 502 | clean=true | phone_left | 1 | 0 | 0 | verb |
| XW-B-x3 | script | 505 | clean=true | phone_left | 1 | 0 | 0 | verb |
| XW-B-x4 | script | 503 | clean=true | phone_left | 1 | 0 | 0 | verb |
| XW-B-x5 | script | 505 | clean=true | phone_left | 1 | 0 | 0 | verb |
| XW-B-x6 | script | 504 | clean=true | phone_left | 1 | 0 | 0 | verb |
| XW-B-x7 | script | 504 | clean=true | phone_left | 1 | 0 | 0 | auto |
| XW-B-x8 | operator | 504 | clean=true | phone_left | 1 | 0 | 0 | verb |
| XW-B-x9 | script | 503 | clean=true | phone_left | 1 | 0 | 0 | verb |
| XW-B-x10 | script | 503 | clean=true | phone_left | 1 | 0 | 0 | auto |

## XW-C

**INCONCLUSIVE**

`d` on XW-C is 0, XW-C has 10 graded exits, `d` on XW-B is 0. The race was never exposed: `e_B` 0 and `e_C` 0 (both under 3), so by the brief's rule the result is INCONCLUSIVE ("the race did not happen often enough to expose C"), not PASS. In 20 exits the phone always closed after the head unit's 500 ms sleep: `bye_to_quit_ms` 502 to 505 on both arms; no failed write fell between the ByeBye and the quit on either arm. Nothing here contradicts the fix in `45cd6fde` and nothing proves it.

- Discard rules: no exit was voided (`ssl_before_bye` 0, `Magic Garbage` 0, no `LOAD_FAIL`). `MATCH! Starting AapService` 0.
- After the ByeBye: `tls_cannot_continue` 0 and `ssl_decrypt_failed` 0 on every exit, both arms.
- Phone `Critical error` lines: 3 to 4 per exit window on both arms (Gearhead logs them at every session end, `CAR.SERVICE: Critical error 5 detail: 35 msg: user request`, then `18 ... Failed to read message`), none naming a protocol refusal (`Multiple media configs` and similar: 0), `FATAL EXCEPTION` 0.
- Reconnect results: arm B 8 by the verb and 2 automatic; arm C 7 by the verb and 3 automatic.

| exit | lever | bye to quit ms | first quit | state reason | finish clean | enc write failed (bye to quit) | send failed (bye to quit) | reconnect |
|---|---|---|---|---|---|---|---|---|
| XW-C-x1 | script | 504 | clean=true | phone_left | 1 | 0 | 0 | verb |
| XW-C-x2 | script | 505 | clean=true | phone_left | 1 | 0 | 0 | verb |
| XW-C-x3 | script | 504 | clean=true | phone_left | 1 | 0 | 0 | verb |
| XW-C-x4 | script | 504 | clean=true | phone_left | 1 | 0 | 0 | verb |
| XW-C-x5 | script | 502 | clean=true | phone_left | 1 | 0 | 0 | verb |
| XW-C-x6 | script | 505 | clean=true | phone_left | 1 | 0 | 0 | auto |
| XW-C-x7 | script | 504 | clean=true | phone_left | 1 | 0 | 0 | auto |
| XW-C-x8 | script | 504 | clean=true | phone_left | 1 | 0 | 0 | verb |
| XW-C-x9 | script | 505 | clean=true | phone_left | 1 | 0 | 0 | verb |
| XW-C-x10 | script | 503 | clean=true | phone_left | 1 | 0 | 0 | auto |

## KW-B

**PASS**

The reference arm, graded by the same list as KW-C. Window `12:01:47` (`KW-B-start`) to `12:16:48` (`KW-B-end`), `span_ms` 900487, `throughput_windows` 180 (at least 0.9 times 900487/5000, so capture health is met), `throughput_max_gap_ms` 5014, `rendered_median` 150 (29 to 30 fps in every window outside the two idle gaps). `ssl` 0, quits 0, `tls_cannot_continue` 0, `ssl_decrypt_failed` 0, `enc_write_failed` 0, `audit_total` 0, `video_discard` 0, phone `critical_error` 0 in the window. Mic and voice counts 0, `media_start_audio` 0 (no audio channel; `enable-audio-sink` false).

## KW-C

**INCONCLUSIVE**

| measure | KW-B | KW-C |
|---|---|---|
| SSL handshakes inside the window | 0 | 0 |
| quits in window (true/false) | 0 / 0 | 0 / 0 |
| `tls_cannot_continue` / `ssl_decrypt_failed` / `enc_write_failed` | 0 / 0 / 0 | 0 / 0 / 0 |
| `audit_total` / `video_discard` | 0 / 0 | 0 / 0 |
| `throughput_windows` | 180 | 180 |
| `throughput_max_gap_ms` | 5014 | 5014 |
| `rendered_median` (per 5 s window) | 150 | 127 |
| windows under 100 rendered / under 20 | 13 / 0 | 24 / 8 |
| mean fps | 27.6 | 23.2 |
| `dropped` / `skipped` / `videoShed` sums | 0 / 0 / 0 | 0 / 0 / 0 |
| `inputWait` mean | 60.8 ms | 50.8 ms |
| inbound video, median over 30 s windows | 694 kB/s, 1841 msgs | 68 kB/s, 807 msgs |
| head unit `unread` / `blocks` / `videoQueue` max | 1% / 0 / 5 | 1% / 0 / 6 |
| touch events delivered in the window | 14136 | 14138 |
| phone `critical_error` in window | 0 | 0 |
| mic requests / voice start / `media_start_audio` | 0 / 0 / 0 | 0 / 0 / 0 |

Conditions 1, 2, 3 and 5 hold on C. Condition 4: `rendered_median` is 127 against 150, a ratio of 0.85 against the 0.90 bar, and the minute-by-minute rate sits at 22 to 26 fps on C against 30 on B outside the idle gaps (`throughput_windows` and `max_gap` are met). **By the letter that condition is missed. I grade the soak INCONCLUSIVE, not FAIL, for this reason:** the head unit received about a tenth of the video bytes on C (68 kB/s against 694 kB/s median) while it kept up with everything it received (fed equals rendered, `dropped` 0, `videoShed` 0, `unread` 1%, dispatch time per 30 s about 0.2 to 0.4 s on both arms), and the same 14136 or 14138 touch events were delivered. A slower reader would show a backlog or ack starvation; it shows neither. The difference is the phone's navigation view producing less motion on this run, not the receive path. The operator may overrule: the letter reading is a FAIL on condition 4 alone, and I would amend with a new commit on top.

## P

**Record only: all zero, so the control path never ran on this rig**

Baseline: `status_produced0_B` 0, `zero_unwrap_B` plus suppressed 0, `short0` 0 (reachability met: `status_any_B` 36657 in XW-B and 50403 in KW-B). Candidate: `produced0_M` 0 (reachability met: `produced_any_M` 31146 in XW-C and 26180 in KW-C). So this phone sends no TLS-only record after the handshake, and the PR's `AapTlsWriter.sendControl` path was never exercised here.

## F5

**INCONCLUSIVE** (the one baseline hit is not a surviving failed write)

`send_failed_old_events` 1 and `send_failed_old_survived_10s` 1 in XW-B, 0 and 0 in KW-B. The XW-B line, `11:46:49.077 AapTransport: send failed (ret=-1); the link is already gone`, was written 2 ms after the previous session's own `AapTransport quitting (clean=false)` at 11:46:49.075: it belongs to the session that had just ended, and the `Throughput over` lines that follow (last `11:47:26.358 rendered=150 (29fps)`, then `quitting (clean=true)` 11:47:31.292) are the next session. The extractor's `survived_10s` flag is therefore a false positive, and the review's finding 5 is neither refuted nor shown. On C the matching event is `12:22:06.580 AapTransport: encrypted write failed or incomplete` followed by `session state disconnected (link_lost)` at 12:22:06.684 (104 ms): C ends the session at once on a failed write, as designed. Both arms had 7 unclean quits in the 10-exit reconnect phases, all from link flaps during reconnect, not exits.

## S

**INCONCLUSIVE**

`reconnects_in_process` 18 (B) and 17 (C), `reconnects_same_session_id` 0 on both: this phone does not resume a session on a reconnect in the same process on this rig, so the share cannot be compared. Every `SSL handshake complete. Session id:` line carries a new id (in the captures).

## Anything the brief did not ask about

- `aa-exit-action` (default 0) now decides what Exit does inside Android Auto; any round that ends a session with the Exit button must set or account for it. The brief's lever (Exit, then Finish later) cannot produce a phone-sent ByeBye on a build with that setting at 0 or 1; value 2 makes the app disconnect itself.
- The phone's "Android Auto is connected" notification Disconnect is a clean, scriptable phone-side ByeBye and removes the operator from exits entirely (about 25 s per exit including the reconnect). It needs the shade open and its card expanded; its position moves with the notification stack, so the script reads the screen.
- The brief's `tp_lib.sh` `exit_cycle` cue says to use Exit then Finish later; that cue text is wrong for this build.
- `uiautomator dump` fails on D-POCO with "could not get idle state" while Maps' ghost activity is in front, and otherwise sees only Maps' window.
