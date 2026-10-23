# Threads table rows archived on 2026-10-17

The `## Threads` rows of `README.md` for two threads with results and no queued round, and their files moved to `archive/rounds/`. PR #1114 merged; `audio-stutter` was superseded by arm C of `pr-1114-audio-continuity`, and a new round restarts the thread. Verbatim, in the original order.

| Thread | State | Next |
|---|---|---|
| `pr-1114-audio-continuity` | Round 1 done: P REDUCED loss vs B on D-HP; C worse, H3 FAIL but D-HP battery died mid-run; F3 not reproduced on D-SAM; F1 INCONCLUSIVE on D-HU. D-HP settings restore pending (unit off). | `pr-1114-audio-continuity-round1-results.md`. |
| `audio-stutter` | Round 1 baseline recorded on `d5528b51`: 21 MIXER_STALL, loopGapMax 1263 ms, 63 heard. R2 (`d96f17c1`) not run; superseded by `pr-1114-audio-continuity` arm C. | `audio-stutter-round1-results.md`. Nothing to run here. |
