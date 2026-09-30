# Video latency after a link stall: brief

## 1. Build and baseline

**Candidate:** `fork/fix/video-catchup-after-link-stall` @ `8cf13352`
**Baseline:** `origin/main` @ `e900de78` (release 3.2.3)

```bash
git fetch fork && git checkout 8cf13352      # candidate
git fetch origin && git checkout e900de78    # baseline
```

**History was rewritten, and the branch was renamed, after round 1 ran.** It was
`fix/755-wireless-video-latency` @ `13408d98`, six commits; it is now
`fix/video-catchup-after-link-stall` @ `8cf13352`, three. The old name no longer exists on the
fork.

**Round 1's results still stand and do not need re-running.** The six commits were three pairs,
each an original plus its correction, and each pair collapsed into one. Nothing executable changed
between `13408d98` and `8cf13352`: `git diff 13408d98 8cf13352` is twelve lines of comment wording
plus three log strings, and no statement, constant or condition among them.

Two of those log strings are the coexistence line's tail, which now reads `same channel, the two
networks share airtime` and `different channels, the radio has to switch between them` where round
1 captured them with a dash. The part any run greps for is unchanged. Round 1's results file quotes
the older wording and has been left as it was written.

Build both with `hur-wifi-test-scripts/build_hur.sh`, and run `run_unit_tests.sh` on the candidate
as the build gate. Inventory `hur-wifi-test-scripts/` first and use what is there.

## 2. What this is and why it exists

A reporter's head unit freezes for a few hundred milliseconds at a time over wireless, with the
sound drifting behind the picture, while the same unit over USB is perfectly clean. Measured from
his logs, same drive, 40 minutes apart:

| | USB, 704 s | WiFi Direct |
|---|---|---|
| gaps where nothing arrives at all | 0 | 128, each 200–460 ms |
| worst delay on a 1 s heartbeat | +40 ms | +2923 ms |
| `inputWait` mean / max per 5 s | 40 ms / 83 ms | 2017 ms / 4410 ms |
| dropped frames | 0 | 0 |

The gaps are the radio, not the app. His unit is joined to a dashcam access point while also
hosting the WiFi Direct group, so one radio serves two networks. That part is not fixable in
software and this branch does not try to.

What the branch fixes is what the app did *afterwards*. Nothing in the pipeline ever shed a late
frame, so when the link went quiet and then delivered the backlog in one burst, every frame of it
was rendered in turn: the picture crawled forward in slow motion and the audio, queued without
any limit, played further and further behind. So:

- the decoder now discards decoded frames it is behind on and shows the newest,
- audio has a bounded queue by default instead of an unbounded one,
- feeding the decoder moved off the thread that reads the network, so a busy codec no longer
  stalls audio and control with it.

**The round's job is not to prove the freezing is gone.** This rig is not the reporter's unit and
does not have his dashcam. It is to prove the changes are safe on a healthy link, because two of
them can degrade a working stream if the thresholds are wrong.

## 3. What is different about this round

Rig constraints from §7a that shape the runs, so none of these read as failures:

- **There is no USB accessory path on this rig.** The reporter's decisive comparison cannot be
  reproduced here. Do not attempt a USB run; the coverage it would give is not available and is
  not asked for below.
- **`wifi-connection-mode=3` (Native AA) is the only usable transport**, so every run uses it. The
  reporter is on mode 2 / strategy 1, but everything under test is in the decoder and the audio
  thread, which are shared by both.
- **No CPU stress, ever.** This unit hard-reboots under sustained multi-core spin load. R4 induces
  decoder load with settings only.
- **Logcat floods here**, so `log-level=2` (INFO) is used throughout. Every line the round needs is
  INFO or above; verified below.
- R5 depends on the head unit being able to join another WiFi network. If there is no access point
  it can join, **R5 is INCONCLUSIVE**: say so and move on, do not go looking for one.

## 4. Settings keys this round needs

Written into `shared_prefs/settings.xml` with the app stopped, via
`hur-wifi-test-scripts/set_pref.sh <key> <type> <value>`. Never through the UI.

```xml
<int name="wifi-connection-mode" value="3" />
<int name="log-level" value="2" />
<int name="fps-limit" value="60" />
<int name="resolutionId" value="2" />
<int name="audio-queue-capacity" value="50" />
<string name="video-codec">Auto</string>
<boolean name="force-software-decoding" value="false" />
```

`audio-queue-capacity` is the only one that changes between runs. `50` is the new default; `0` is
the old unbounded behaviour and is the positive control in R3.

## 5. The lines that decide every run

Verified with `grep -F` against the branch (`8cf13352`, identical at `13408d98`). Match on the
message text after the `|`, never the minified class name.

```
Throughput over 5000ms: rendered=280 (55fps), fed=287 (57fps), dropped=0, skipped=0, inputWait=1030ms, codec=OMX...
Feed thread started
Feed thread stopped
Output thread started
Input buffer full. Dropping frame.
Audio queue is full, dropping audio frame to prevent stalling
AudioDecoder.start: channel=6, stream=3, ... queueCapacity=50, ...
Config response: ... (maxUnacked=12)
This unit is connected to another WiFi network while hosting the WiFi Direct group
Decoder stopped:
```

`skipped=` is new in the throughput line, so its absence means the baseline APK is installed.

## 6. Runs

### R1: healthy stream is untouched  ← **this is the point of the round**

The catch-up discards decoded frames when it thinks the pipeline is behind. If that threshold is
wrong it will discard on a perfectly healthy stream and halve the frame rate, and the log would
blame the link. This run is the one that can veto the branch.

Candidate APK, settings as §4, connect normally, leave a moving map on screen for **5 minutes**.

- **PASS**: across all `Throughput` lines after the first, `rendered` fps is within 10% of `fed`
  fps, and `skipped` is 0 in at least 80% of windows and never exceeds 2 in any window.
- **FAIL**: any sustained stretch where `rendered` is below 60% of `fed`, or `skipped` regularly
  above 2 per window.

Report the full list of throughput lines, not a summary.

### R2: baseline comparison, same conditions

Baseline APK, identical settings and the same 5 minutes on a moving map.

- Report the same numbers. There is no PASS/FAIL: this is the reference R1 is judged against.
- **The comparison that matters**: candidate `rendered` fps should be no lower than baseline
  `rendered` fps. If it is materially lower, R1 has failed even if it passed its own thresholds.

### R3: audio bound, and the positive control

Candidate APK. Two connections, changing only `audio-queue-capacity` between them:

- **R3a** `audio-queue-capacity=50`, confirm `queueCapacity=50` appears in the `AudioDecoder.start`
  line for every channel. Play music for 3 minutes.
  - **PASS**: no `Audio queue is full` line at any point.
  - **FAIL**: any occurrence. That would mean 50 is still below what ordinary jitter needs here,
    which is exactly what this run exists to find out.
- **R3b** `audio-queue-capacity=0`, confirm `queueCapacity=0`. Play music for 3 minutes.
  - This is the **positive control**: it restores the old unbounded queue. Expect no drops either
    (an unbounded queue cannot drop). What it proves is that the setting reaches the audio thread,
    which is what makes R3a's result meaningful.
  - Report whether audio drifts audibly behind the picture in either half.

### R4: the discard path actually engages under load

Force the decoder behind so the catch-up has something to do, using settings only.

Candidate APK, `resolutionId` raised to the highest the unit accepts and `fps-limit=60`. Connect and
drive the UI hard for 3 minutes, scrolling a map continuously.

- **PASS**: `skipped` is non-zero in at least one window, **and** `rendered` fps stays above 20,
  **and** the picture stays coherent (no green blocks, no smearing). Non-zero `skipped` here is the
  fix working, not a fault.
- **INCONCLUSIVE**: if `skipped` stays 0 throughout, this rig's decoder is simply fast enough. Say
  so; do not raise settings beyond what the unit accepts to force it.
- **FAIL**: visible corruption, or `rendered` collapsing below 20 fps while `skipped` climbs.

### R5: coexistence warning, if an access point is available

Candidate APK. Join the head unit to any WiFi network, then connect Native AA so a group comes up.

- **PASS**: the `This unit is connected to another WiFi network while hosting the WiFi Direct group`
  line appears, once per group, with both frequencies quoted.
- **INCONCLUSIVE**: no joinable access point exists on this rig. Expected; not a failure.
- Also report: does it appear when the projection activity is **not** in the foreground? That is
  the case the warning was rewritten for.

### R6: thread lifecycle across restarts

The feed thread is new and must not leak. Candidate APK.

Connect and disconnect **ten times** using the deep links (`headunit://connect`,
`headunit://disconnect`), leaving ~15 s connected each time.

- **PASS**: `Feed thread started` and `Feed thread stopped` counts are equal at the end, and equal
  to the `Output thread started` count. No `Input buffer full` lines during ordinary disconnects.
- **FAIL**: counts diverge, which means a thread outlived its session.

Then, still on the same install, connect once more and confirm video renders normally. A leaked
feed thread would show up as corruption on a later session, not the one that leaked it.

## 7. Do not re-run

- Anything about the reporter's dashcam, radio contention, or the 200–460 ms gaps. That is settled
  from his logs and cannot be reproduced on this rig.
- USB anything.
- `max_unacked`: it was changed and then changed back within this branch. It is 12 on wireless,
  same as `main`. `Config response: ... (maxUnacked=12)` is the expected value, not a finding.

## 8. Report back

The three numbers that decide whether this ships:

1. **R1 vs R2 `rendered` fps.** Candidate must not be below baseline. This is the veto.
2. **R1 `skipped` distribution.** Near-zero on a healthy link, or the threshold is wrong.
3. **R3a `Audio queue is full` count.** Must be zero, or the default bound is too low.

Plus, from R6, whether the thread counts balanced.

If R1 fails, stop and report. R4 onwards is not worth running against a branch that degrades a
healthy stream.
