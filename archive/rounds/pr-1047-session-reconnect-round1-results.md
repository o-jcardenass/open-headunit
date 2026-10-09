# session-reconnect, round 1 results

**Candidate K:** pr/split-session-reconnect @ ac741d298d825149618f6529e40b741a53818136   **Combo X tree:** 8eb30402c3498a0ad30b363c3d71e091c066265c (printed hash equals the brief's)   **Baseline B:** main @ 7e9d813d66493f1cc978c0f6a259bdb613874bd1
**APK md5:** B 9516b641ac68b2e89033dca1a947c9cd / K c3911202758f2c36544be31f063de113 / X 97f14afb030c0e3b17c318f42a84aacf
**Units:** D-HU (UNISOC MT50, Android 14, adb shell is root) with D-POCO (Android 15); D-MOTO, D-SAM and D-HP were not used
**Date:** 2026-10-05
**Evidence:** release `rig-evidence-pr-1047-session-reconnect`, asset `pr-1047-session-reconnect-round1-captures.zip`, sha256 452ecac0df31723bca2052f3c891c44de4e1be4d60e2329c3477a28609054816

## Round verdict

**FAIL.** Stage A only was run. The audio-save change works as designed and reproduces S1 and S2 on every Stage A path; the exit loop and the user-connect serialisation are at least as good as the baseline. The combo arm X fails C7: after the audio-save reconnect, on H.265 with GLES, no video returned in 90 s (twice in two full runs), and one cover-and-return took 5.7 s against the baseline's 1.8 s. The same save-and-reconnect passes in isolation on K and X, so the trigger is the cover cycles before it. Stages B, N, C and D were not run.

| Question | Answer |
|---|---|
| S1: audio save ends the session as a lost link | Reproduced on K and X, Native AA: `audio_changed` 1, `link_lost` 1, `user_exit` 0, session ended 12 to 41 ms after the apply, second SSL 2.0 to 6.6 s later |
| S1: projection raised over Settings | Not reproduced: with Settings open, `Not raising the projection, the settings screen is open` 1, raises 0, Settings in front at +10 s, +30 s and +60 s (K and X) |
| S2: restart-audio after a drifted setting ends the session | Reproduced: K `audio_changed` 1 and a re-formed session; B restarts tracks only. The verb `ACTION_RESTART_AUDIO` is inert on both builds |
| Starvation cap (C1X) | K: 3 consecutive early saves, 0 starvation lines, no cap written; B analog identical |
| Exit loop (C3) | K and X 5 of 5 cycles clean, `T_destroy` 254 to 316 ms; B 10 of 10, 573 to 1269 ms |
| User connect over a live session (C6a) | Identical on B, K, X |
| Three branches coexist on hardware (C7) | **No**: X loses video after the audio-save reconnect in the full C7 (2 of 2) |

## Setup notes

- **Scope actually run:** R0 and Stage A (D-HU with D-POCO): C3, C1N, C1S, C2, C6a, C7, C1X, plus an added isolation run C7P4. Not run: Stage B (C1H, C1M, needs the Gearhead server toggle), Stage N (C5: no Wireless Helper app on D-POCO), Stage C (C4a-d, C1U, C6b, C7U: the dongle and an operator at the rig), Stage D (C8: D-SAM and D-HP). Reported below as INCONCLUSIVE with the reason.
- **Cuts to meet the operator's time limit (each a deviation):** C3 on K and X is 5 cycles (the brief wants at least 8), so those two grade INCONCLUSIVE by the letter even though all 5 are clean; B ran 10. C7 is about 7 minutes (the brief's 20): P1 has 6 media-next keys 10 s apart, covers hold 5, 10 and 5 s, P3 20 s, P5 30 s. C1X stops at 3 consecutive early attempts.
- **R0:** unit tests B 2658, K 2658, X 2683, all 0 failures and 0 errors (JUnit XML). The three md5s differ. Identity (dex strings): `transportLifecycleLock` B 0, K 1, X 1; `AapMessageReassembler` B 0, K 0, X 11; `outputPublicationLock` B 0, K 0, X 1. X's tree hash printed `8eb30402c3498a0ad30b363c3d71e091c066265c`. Before every run the driver pulled the installed APK and checked its md5 against the arm. I did not read `commit` from `ACTION_QUERY_STATE`. The build peaked at 91C (package throttle count stayed at 6, all from the previous round's build); every run was started below 75C and the hottest run was 67C with no throttling.
- **Rig facts:** D-HU's adb shell is root with no `su` binary, so the brief's `su -c` calls were replaced by direct root commands; `shared_prefs` is `u0_a176:771`. D-POCO has no `su`. `restart_audio` landed through the plain shell broadcast on D-HU in every run (the verb never landed, the root route was not needed). D-HU's rig audio keys were left as found (`use-aac-audio` false, `audio-latency-multiplier` 8, `audio-queue-capacity` 20).
- **Harness defects found and fixed (the first full B arm was voided and re-run, kept in `out/void1`, `out/void2`):**
  1. The brief's frame started the capture twice (once in the run frame, once in `bringup`), leaving an orphan `logcat` writing to the same file: spliced lines (1220 broken lines in one capture), finished captures kept growing, and sizes were 7 times too big. Fixed to one capture; every capture in this report is from the corrected harness (2 untimestamped header lines each).
  2. `watch` (the brief's logcat pipe) never fires because adb ignores SIGPIPE; it now reads the capture file. The first C1X attempts did not fire for that reason and were discarded.
  3. `send` overwrote its caller's loop variable `a`; renamed.
  4. `mResumedActivity` does not exist on Android 14; `topResumedActivity` is used. The first C1S-K run printed `settings not resumed` for that reason and was re-run (`out/c1s-void`).
  5. `put_prefs` takes `KEY=TYPE:VALUE`; my first call had it reversed (no settings were written, the run was stopped).
  6. `pkill -f` is never used; watchers are killed by pid.
- **Phone:** D-POCO Bluetooth and Wi-Fi switched on with `svc` and read back with `dumpsys` before every run; Spotify is on D-POCO (not VLC; no music files on the phone). C7 starts it with `KEYCODE_MEDIA_PLAY` and the log shows `playing=1`. The album-art leg is INCONCLUSIVE regardless: 0 `AapRead: largest message body so far` lines on B and X.
- **C7 return timing:** `monkey` takes about 35 s to inject here, so a return is read from `AapProjectionActivity: onResume` to `First frame rendered`; in 2 of 3 cycles on both builds there is no `First frame rendered` after the resume (H.265 hardware path), so those read NA.
- **Settings:** the run settings were written from a fresh backup before each run; at the end the backup was restored and `video-profile-starvation-cap` and `allow-external-configuration` read back absent (0 matches). D-HU is left with the baseline APK reinstalled last (C7P4-X ran last: the X APK is installed).

## R0 Gate

**PASS**

| Arm | md5 | identity symbols | JVM count |
|---|---|---|---|
| B | 9516b641ac68b2e89033dca1a947c9cd | 0 / 0 / 0 | 2658, 0 failures |
| K | c3911202758f2c36544be31f063de113 | 1 / 0 / 0 | 2658, 0 failures |
| X | 97f14afb030c0e3b17c318f42a84aacf | 1 / 11 / 1 | 2683, 0 failures |

## C3-B

**PASS**

Reference: 10 of 10 cycles, `groupFormed: false` at +3 s and +8 s on every cycle, `T_destroy` 573, 583, 1231, 1232, 1234, 1235, 1238, 1242, 1242, 1249 ms (median 1234), timeout lines 0, one `Native AA user exit` line per cycle, `FATAL EXCEPTION` 0, `Failed to join threads` 0.

## C3-K

**INCONCLUSIVE**

5 of 5 cycles clean (below the brief's minimum of 8): `groupFormed: false` at +3 s and +8 s, `T_destroy` 279 to 316 ms (median 283), timeout lines 0, one user-exit line per cycle, `FATAL EXCEPTION` 0, `AapTransport cleanup` failures 0. Every condition holds on the cycles run.

## C3-X

**INCONCLUSIVE**

5 of 5 cycles clean, same reading as K: `T_destroy` 283 to 294 ms (median 284), group gone at +3 s and +8 s, no timeout, no fatal.

## C1N-B

**PASS**

B control: `restart_audio` landed (plain broadcast), `AapAudio: Restarting all audio tracks` 1, `audio_changed` 0, no disconnect, `connected` true at +30 s.

## C1N-K

**PASS**

`audio_changed` 1, `stop_action` 0, `user_exit` 0, `link_lost` 1, `T_disc` 35 ms, second SSL `T_ssl2` 2100 ms, rendered video after it, pokes 0, connects 0, `Native AA session ended; keeping the` 1, raises 1, `FATAL EXCEPTION` 0, `Magic Garbage` 0. Control restart afterwards: tracks restarted once, new `audio settings changed` 0, `connected` true. **S1 reproduced** (`link_lost` 1; no wake poke because the poke was not needed within 60 s).

## C1N-X

**PASS**

Same shape as K: `audio_changed` 1, `link_lost` 1, `user_exit` 0, `T_disc` 34 ms, `T_ssl2` 2170 ms, video rendered after the second SSL, control restart clean. S1 reproduced.

## C1S-K

**PASS**

Settings screen in front at +10 s, +30 s and +60 s; `audio_changed` 1, `link_lost` 1, `T_disc` 12 ms, `T_ssl2` 1979 ms; `Not raising the projection, the settings screen is open` 1, raises 0; after BACK the projection came to the front. The first run of this id (field-name defect) is voided and re-run.

## C1S-X

**PASS**

Same as K: Settings in front at all three samples, raises 0, `Not raising` 1, `T_disc` 13 ms, `T_ssl2` 5802 ms, projection back after BACK.

## C2-B

**PASS**

C2.0: the verb line was logged, receiver lines 0, tracks 0 (the verb is inert). C2.1: control, tracks restarted once, session alive. C2.2: setting changed then `restart_audio`: tracks restarted, `audio_changed` 0, no second SSL, session alive. C2.3: import alone, `connected` true at +30 s, 0 disconnects.

## C2-K

**PASS**

C2.0: identical to B (receiver 0, tracks 0). C2.1: tracks restarted once, session alive. C2.2: `audio_changed` 1, `link_lost` 1, `T_disc` 32 ms, second SSL 5421 ms later: **S2 reproduced** (B does not end the session). C2.3: import alone, still connected at +30 s, `audio_changed` 0.

## C6a-B

**PASS**

Reference: arbiter `stood the wireless stack down until it ends` then `ended with no session; giving back wireless` 6534 ms after the verb, second SSL 18137 ms after, 1 SSL in the window, `FATAL EXCEPTION` 0, `Magic Garbage` 0, cleanup failures 0.

## C6a-K

**PASS**

Give-back 6536 ms, second SSL 18283 ms, 1 SSL in the window, no fatal, no garbage, no cleanup failure, `Connect already in progress` 0.

## C6a-X

**PASS**

Give-back 6535 ms, second SSL 18488 ms, 1 SSL in the window, clean.

## C7-B

**PASS**

Reference, GLES with H.265 (`Media Sink Setup Request: 7`), Spotify playing: P1, P3 and P5 discard counts all 0 (`bigbody` 0, so the album-art claim is unproven), median 29 fps, `rendered=0` windows 0; covers: 3 of 3 tore the surface down, `cycling video focus` 0, onResume to first frame 1786 ms in cycle 2 (NA for 1 and 3), onResume 35.2 to 35.3 s after the `monkey` call; audio save: tracks restarted, session alive, no second SSL (B control); exit `T_destroy` 1249 ms, group gone at +3 s and +8 s; `Forcing restart (` 3; `FATAL EXCEPTION` 0. A first run without playback is kept in `out/c7-noart-B` with the same numbers.

## C7-X

**FAIL**

- P4: after the audio save the session ended as a lost link (`T_disc` 31 ms) and a second SSL formed 6397 ms later (first run 1977 ms), the projection was raised (`onResume` at the second SSL), the phone sent `Media Sink Setup Request: 7`, then `AapVideo: Dropped Flag 11 packet. len=4`, and **no `Codec initialized:`, no `Throughput over` and no `First frame rendered` for the next 90 s** (P5 had 0 throughput windows). Reproduced in the second full run (same lines, P5 windows 0). The brief requires rendered video within 30 s of the second SSL.
- P2: cycle 2 onResume to first frame 5698 ms (second run 5685 ms) against B's 1786 ms, over the allowed `max(5000, B + 1500)`. Cycles 1 and 3 have no first-frame line after resume on either build. `cycling video focus` 0 on both builds; teardown proof yes on all three.
- P1, P3, P5 discard counts all 0, `bigbody` 0 (album-art leg INCONCLUSIVE), median 29 fps, `rendered=0` windows 0. P6 exit `T_destroy` 254 ms, group gone at +3 s and +8 s. `FATAL EXCEPTION` 0. `Forcing restart (` 3 against B's 3.
- Isolation: the same save-and-reconnect without the cover cycles (run C7P4: H.265, GLES, 15 s session, then the save) formed a second SSL and rendered video within 60 s on K (T_ssl2 2258 ms), on X twice (the second run overwrote the first summary file; the kept one reads T_ssl2 6572 ms), and the baseline's own session dropped 31 s after the save for an unrelated reason. So the lost video needs the earlier GLES cover and return cycles.

## C1X-B

**PASS**

Analog characterisation: 3 consecutive early attempts (no frame rendered before the apply), starvation lines 0, cap not written, cleaned and read back absent.

## C1X-K

**PASS**

3 consecutive early attempts, `sessions in a row ended without a single video` 0, cap not reached; K is no worse than B. Cap read back absent after cleanup.

## C7P4-K, C7P4-X, C7P4-B

**PASS**

Isolation runs (not in the brief): H.265, GLES, a 15 s session, then the audio save. K and X: second SSL and rendered video within 60 s, `audio_changed` 1, `link_lost` 1. B: tracks restarted, `audio_changed` 0; its session ended 31 s later on its own (`pokes` 1, `connects` 1) and re-formed.

## C1H, C1M, C5, C4a, C4b, C4c, C4d, C1U, C6b, C7U, C8

**INCONCLUSIVE**

Not run in this session. C1H and C1M need a head unit server listening (a hand step on a phone); C5 needs the Wireless Helper app, which is not installed on D-POCO (UNTESTABLE as it stands); C4, C1U, C6b and C7U need the dongle and an operator at the rig; C8 needs D-SAM and D-HP. No data.

## Anything the brief did not ask about

- The audio-save session end is much faster on K and X than B's exit: `T_destroy` of the exit loop is 0.25 to 0.32 s on K and X against 0.57 to 1.27 s on B.
- `AapRead: largest message body so far` never printed on any arm while Spotify played, so album art may not reach the head unit on this phone, or the line needs a larger body than Spotify's art.
- The brief's `shot`-style and watcher helpers, `su` use, the double `cap_start` and `mResumedActivity` all need fixes before the next round of this thread (Setup notes).
- D-HU is left running the X APK; D-POCO still carries our debug build and Spotify is paused.
