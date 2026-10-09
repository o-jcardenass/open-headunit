# session-reconnect, round 1 addendum results

**Candidate K:** pr/split-session-reconnect @ ac741d298d825149618f6529e40b741a53818136   **Combo X tree:** 8eb30402c3498a0ad30b363c3d71e091c066265c   **Baseline B:** main @ 7e9d813d66493
**APK md5:** B 9516b641ac68b2e89033dca1a947c9cd / K c3911202758f2c36544be31f063de113 / X 97f14afb030c0e3b17c318f42a84aacf (live APK md5 checked before every run)
**Units:** D-HU (UNISOC MT50, Android 14) with D-POCO. **Date:** 2026-10-06
**Evidence:** release `rig-evidence-pr-1047-session-reconnect`, asset `pr-1047-session-reconnect-round1-addendum-captures.zip`, sha256 e88fa2e2b6d1aa9273afac020ed7b418c7e071d101bf6db65b2a74f1c49e1689

## Setup notes

- Follow-up to `pr-1047-session-reconnect-round1-results.md`, no new brief: it closes C3's 8-cycle minimum and splits the C7 sequence that lost video on X. Settings as in round 1 (H.265, GLES view mode 2, mode 3). Host max temp 71C, throttle counter 0 on every run.
- Variants of C7 with one leg each: CV = three cover and return cycles (5 s, 10 s, 5 s holds) then the audio save; SK = six media skips on D-POCO's Spotify then the save; ALL = skips, covers, save (the round 1 C7 order). Video counted as carried when 3 more `Throughput over` windows with `rendered` above 0 follow the reconnect handshake.
- Each variant ran once per arm (ALL and CV on B and K, all three on X). One run per cell is not a rate, so only the X ALL result is repeated (it also failed in 2 of 2 full C7 runs in round 1).
- The system launcher (`com.android.launcher3`) threw one `ConcurrentModificationException` in each of the C3x-X and C3x-K runs. It is a different process from ours and is outside this grading.

## R-C3x (10 cycles, K and X)

**PASS** for K and X. 10 of 10 cycles each: `groupFormed: false` at +3 s and +8 s, `timeout` 0, `cleanupfail` 0, `joinfail` 0, one user-exit line per cycle, no fatal in our process. `T_destroy` K 281 to 300 ms, X 275 to 348 ms (round 1 baseline median 1234 ms). This replaces round 1's INCONCLUSIVE on the cycle count.

## R-SK (X)

**PASS.** Video rendering before the save (`rendered=150` in the last 3 windows) and after (`video2=yes`).

## R-CV

**FAIL** on X, PASS on K and B.

| Arm | rendered before save | after save | note |
|---|---|---|---|
| B | 150, 150, 150 | no reconnect (`ssl2=no`, expected) | |
| K | 150, 151, 150 | `video2=yes` | |
| X | 0, 0, 0 (`fed` 150 per window) | `video2=NO` | 13 windows with `rendered=0`; decoder fed, nothing displayed |

On X the picture was already dead when the save was applied, so this run does not show an audio-save fault. It is the cover-return loss, the pr-1046 finding, seen on the combo. After the reconnect `AapVideo: Dropped Flag 11 packet` appears and no frame is rendered.

## R-ALL

**FAIL** on X, PASS on K, B not comparable.

| Arm | rendered before save | after save |
|---|---|---|
| B | 104, 114, 96 | no reconnect (`ssl2=no`) |
| K | 150, 150, 150 | `video2=yes` |
| X | 150, 150, 150 | `video2=NO` |

X reproduces round 1's C7 result for the third time with video rendering right up to the save. K alone keeps video, so the loss needs the combination of the pr-1046 changes and the pr-1047 reconnect, after cover cycles. Skips alone (R-SK) and the save alone (round 1 C7P4) pass on X.

## Anything the brief did not ask about

- Two different X failures sit under one symptom: CV loses the picture during the covers, ALL loses it after the save. Whether the second is the first arriving late is not shown by these runs.
- Not run: Stages B, N, C and D (need the server toggle, the Wireless Helper app, the dongle with the operator, the tablets); album art still unproven.
