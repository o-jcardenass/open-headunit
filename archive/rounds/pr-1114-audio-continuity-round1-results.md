# pr-1114-audio-continuity round 1 results

**Candidate:** P `fork/review/pr-1114` @ `ccc089293644f5876b4a446c4e6db1a5aef1cf7b`; C `fork/review/pr-1114-stutter-reserve` @ `802920baf0ae657c00b6fce96dfd51df066c973b`
**Baseline:** B upstream `main` @ `d2d0a6bf71217e8658545314de63ed87c0031451`
**APK md5:** B `aeb8217fd5e0ab966a9781ee4a8b0bac` / P `a8b12a81c3730a1c663498c97d37ae01` / C `be021da83ba15dcde98e1aac19a425ce` (each installed APK pulled and hashed on its unit before use)
**Unit:** D-HP (HP Slate 7 Plus, Android 4.2.2, API 17, USB, D-MOTO as accessory); D-SAM (SM-T230, Android 4.4.2, API 19, Native AA, D-POCO); D-HU (MT50, Android 14, Native AA, D-POCO). Gearhead 18.0.164204-release.daily on both phones.
**Date:** 2026-10-16

Verdicts: R0 PASS, H1 PASS (baseline), H2 PASS, **H3 FAIL (battery-confounded, run cut short)**, S1 PASS (baseline), S2 PASS, S3 PASS, W1 PASS (baseline), W2 PASS (F1 INCONCLUSIVE), W3 PASS.

## Setup notes

Deviations, in the order they happened. Every one is also in `setup-notes.md` in the evidence bundle.

1. **Music source is Spotify, not the brief's VLC mp3** (operator request, 2026-10-16: "use my spotify music"). `com.spotify.music` 9.1.92.3058 on D-MOTO and D-POCO, on every arm of every stage. `media()` launches Spotify and sends `KEYCODE_MEDIA_PLAY`, waits 4 s and sends it again, because the first PLAY alone left the session PAUSED. The content differs from `audio-stutter` round 1, so LOSS numbers here are not like for like with that round. The mp3 was still pushed to D-POCO (it had none) and VLC was still force-stopped at each run end.
2. **H1 ran three times; only the third is graded.** Attempt 1: the brief's `loc()` assigns the variable `L`, which my session script used as the run label, so the capture paths broke at step 5 (voided, `void/`). Attempt 2: my swipe-bounds parse returned empty, so `swipe` was a no-op; I stopped it after 1.5 min (`void2/`). The dump itself was fine (`bounds="[0,0][1280,736]"`); the parse is fixed. SX1 896, SX2 384, SY 368 for H1, H2 and H3. A `pkill -f` once killed the host shell (exit 144); fixed by killing by pid.
3. **Identity.** The H1 attempt-3 reply to `ACTION_QUERY_STATE` shows commit `d2d0a6bf7121`. H2 `ccc089293644`, H3 `802920baf0ae`, S1 `d2d0a6bf7121`, S2 `ccc089293644`, S3 `802920baf0ae`, W1 `d2d0a6bf7121`, W2 and W3 `ccc089293644`. Installed-APK md5 matched the built APK for every install.
4. **H3 was cut short by D-HP's battery.** D-HP read 61% at 09:30 and 13% at 10:36:49 (about 0.73% per minute) and turned off at about 10:43:30, in the guidance segment after the `H3-g3` marker. The music segment is complete (10:32:06 to 10:41:40); G has 4 prompts (`H3-g1` to `H3-g3`, no `H3-g-end`), and `H3-af.txt` is 0 bytes. **The operator chose to take H3 as run, with no re-run** ("In H3 there were only 3 mins left, continue like that and take H3 as fully ran"). Because the battery fell across all three arms, the arms are confounded with battery level; see H3.
5. **S1 attempt 1 ended with no SSL after 150 s** (kept in `S1-attempt1/`). Bluetooth handshake completed (Type 3 written 10:51:14, `WifiStartResponse ... status=SUCCESS(0)`), the phone joined the P2P group (10:51:20), but no `Incoming connection` followed; the group was recreated at 10:51:25 and 10:51:40 (3 `createGroup SUCCESS`) and the handshake repeated. The phone's `WifiVersionResponse` at 10:51:13 carried `status=NO_SUPPORTED_WIFI_CHANNELS(-8)`. Cause not established. The brief's single retry formed SSL at 10:54:47. S2 and S3 formed SSL on the first attempt.
6. **D-SAM `screen-orientation` read 4 at the start**; the brief requires 2; written to 2. The round-start backup (`backup/D-SAM-settings-start.xml`) holds 4.
7. **D-HU's saved settings at the start differed from the brief's worst case**: `audio-latency-multiplier` 8, `enable-audio-sink` false, `use-aac-audio` false, `native-driver-selection-mode` 1, `aa-exit-action` 0, and `wifi-direct-stable-identity` true (left as found). Written to the brief's values for stage W. `shared_prefs` owner read `u0_a176:u0_a176`, which is correct. D-HP's start settings are in `backup/D-HP-settings-start.xml`; the delta against the last backup was not computed.
8. **L-PEND is 0 on P in H2 and W2** although the brief lists it. In `AapAudio.finishPcmTiming` the function returns before logging when `pcmTiming[channel]` is null; which condition held here was not chased. The Stop boundary is visible in the mixer's `PCM reserve channel=4 ... boundary=Stop` line instead.
9. **D-HP refuses `logcat -G`** (`unknown option -- G`); D-MOTO accepted it. D-HP captures were duplicated about 2x in the file (H1: 118180 raw lines, 61366 after the brief's dedup); no `capture died` note was written in any run.
10. **Pre-flight.** The first run failed on D-SAM Bluetooth off; the operator switched it on and the second run passed (D-HP wifi 1 bt 1; D-MOTO 1/1; D-SAM 1/1; D-POCO 1/1 with HFP and A2DP up; D-HU 1/1; all awake). Clock: D-SAM 09:30:53 against host 09:31:12 (19 s behind). Batteries at the start: D-HP 61, D-MOTO 60, D-SAM 100, D-POCO 94.
11. **Tools.** Used `rig_preflight.sh`, `wt-new.sh`, `build_hur_cool.sh`, `rig_thermal.sh wait|watch` (host stayed under 62C; no throttle count rose in any run), `set_prefs_runas_host.py` (D-HP, D-SAM), `set_hu_settings_host.py` (D-HU), `extract_brief_blocks.py` (wrote nothing; helpers were typed from the brief). One-round scripts in `scripts/`: `pac_lib.sh`, `heard.sh`, `hrun.sh` (stage H), `nrun.sh` (stages S and W), `analyze.py`, `phone_counts.sh`, `corr.py`, `build_all3.sh`. Quirk files read: `topics/tooling`, `topics/audio`, `units/D-HP`, `D-SAM-and-D-HP`, `D-SAM`, `D-HU`, `D-MOTO`, `D-POCO`.
12. **Counting method.** `analyze.py` dedups lines, clips D-SAM captures to the run's `-start` marker, and computes LOSS as the sum of rises in `concealedFrames` plus `staleFrames` for channels 4 and 6 (a fall counts the new value), UND from `disabled due to previous underrun`, and GAPW as mixer lines with `loopGapMax` at or above `effective/48` ms. Phone counts are between the `I/RIGMARK` lines of the same names.
13. **W3** wrote `audio-latency-multiplier` 16 (read back 16, and `AudioDecoder.start` shows `latencyMultiplier=16` for channels 4, 5 and 6). The first prompt of W3's session is the probe prompt, not a G prompt, so L-FIRST comes from it.

Closing state (settings restore, APK left on each unit, radios, closing pre-flight) is recorded under "Closing" at the end.

## R0

**PASS**

- md5s all differ (B `aeb8217f`, P `a8b12a81`, C `be021da8`). Each arm built in one worktree with `git rev-parse HEAD` checked after every checkout.
- `PcmRenderDeadline` in the dex: B 0, P 3, C 3. `FIXED_FRAMES`: B 0, P 0, C 1.
- Review branches: `fork/review/pr-1114` = `ccc08929...`, `fork/review/pr-1114-stutter-reserve` = `802920ba...` with parent `ccc08929...`, and `d2d0a6bf` is an ancestor of P.
- Pre-checks: both phones have Maps and VLC (and Spotify), location true, no keyguard. All six operator items answered yes.

## H1

**PASS**

Baseline on D-HP, arm B, USB. No PASS bar. Preflight checks held: first L-OUT `output AudioTrack requested=2400 effective=2229 minimum=2229`; channel 6 `codec=AAC_LC`, `latencyMultiplier=2`; L-ACC 1, L-SSL 1, L-WIFI 0, L-MSR6 1; L-MIX in M 64 (bar 45).

- Settings: `AKEYS` + `HKEYS` read back. `static-audio-focus` read `false`.
- Decisive line: `09:56:27.018 AudioMixer: id=3 AudioTrack effective=2229 frames ... loopGapMax=119ms mixWorkMax=10ms writeMax=119ms`.
- M (`H1-music` 09:56:58.988 to `H1-m-end` 10:05:31.038, 512.1 s): **LOSS 42169** (channel 6 40921, channel 4 1248), **UND 0**, **GAPW 31 of 64**, largest `loopGapMax` 118 ms, largest `mixWorkMax` 59 ms, L-PCM ch4 2 and ch6 14, L-OVF 14, L-MSR1 14, L-STOP1 14, L-REOPEN/L-STALL/L-THREW 0, L-FIRST `requestToPcm=34ms pcmToRender=54ms`, L-TP median `rendered=150`.
- G (217.4 s): LOSS 0, GAPW 5 of 6, L-MSR1 6, L-STOP1 6, L-FOCUS 6, L-REL 6, L-REOPEN/L-STALL/L-THREW 0, S-UND 0.
- Heard: 18 stamps, 8 matched, 10 unmatched.
- Phone: P-FC 3, P-STATE 4, P-FATAL 0, P-PERM/P-QUEUE/P-ACK/P-DROP 0.
- Reachability: B stutters (LOSS far above 4800, GAPW above 0).

## H2

**PASS**

D-HP, arm P. Preflight checks as H1; `effective=2229` (46.4 ms), same as B.

- In M (512.7 s) LOSS(H2) **10444** is not above max(42169, 4800); UND 0 is not above 0; L-REOPEN, L-THREW and L-STOPPED are 0 in M and in G; in G L-STOP1 6 equals L-MSR1 6; phone P-STATE 4, P-FATAL 0.
- **Classification P against B: REDUCED** (LOSS 10444 is below 42169 minus 4800 = 37369). Reachable (H1 had LOSS above 4800 and GAPW above 0).
- Other M numbers: GAPW 23 of 62, largest `loopGapMax` 150 ms, largest `mixWorkMax` 50 ms, L-PCM ch4 0 and ch6 21, L-STARVE ch6 1 and ch4 0, `renderWaits=` sum 0, L-RES 114 in 512 s (13.3 per minute), L-OVF 20, L-MSR1 12, L-STOP1 12. G: GAPW 5 of 6, L-RES 7, L-PEND 0 (note 8). L-FIRST `requestToPcm=63ms pcmToRender=131ms`.
- Heard: 7 stamps, 5 matched, 2 unmatched.
- Phone: P-FC 3, P-STATE 4, P-FATAL 0, all other P counts 0.

## H3

**FAIL**

D-HP, arm C. Preflight: first L-OUT `requested=2400 effective=9600` (200 ms, bar 9600 or more). **Run ended early and is confounded with battery level (setup note 4).**

- Bars, as the brief states them:
  - GAPW(H3) in M is **11** of 72 (not 0). All 11 windows at or above 200 ms had a rising `concealedFrames`/`staleFrames`, an L-PCM line or an S-UND line in the preceding 10 s, so the "report only" clause does not apply: FAIL.
  - LOSS(H3) **153893** (channel 6 151013, channel 4 2880) is above LOSS(H2) 10444 plus 4800: FAIL. UND 0, which is not above H2.
  - L-REOPEN, L-THREW, L-STOPPED 0 in M and in the G that ran: pass.
  - Phone P-STATE 4, P-FATAL 0: pass.
- **Classification C against P:** not REDUCED (LOSS and GAPW-with-loss are higher; GAPW 11 against 23 on H2 is lower, but the clause for it needs GAPW(H3) at 0). Reachable (H2 had GAPW 23).
- M numbers (574.4 s, longer than H1/H2's 512 s): largest `loopGapMax` **1740** ms, largest `mixWorkMax` 1714 ms, L-PCM ch4 1 and ch6 64, L-STARVE ch6 68, `renderWaits=` sum 262, L-RES 119 (12.4 per minute), L-OVF 0, L-MSR1 16, L-STOP1 16, phone P-PERM **14** (`MediaSourceFlowController out of permits`, 0 on H1 and H2). L-FIRST `requestToPcm=42ms pcmToRender=48ms`.
- **When the stalls happened.** Median mixer window equals H1 and H2 (gap 45 ms, work 18.5 ms against H1 54/23 and H2 44/19). Windows with `loopGapMax` of 200 ms or more were logged at 10:37:29 (565 ms), 10:37:48 (a burst of six, up to 1740 ms) and 10:40:22 to 10:40:37 (1160, 370, 229 and 776 ms). None in the first 5 minutes of M (10:32 to 10:37). D-HP read 13% at 10:36:49. A single read-only host check of the mixer lines is all that was done; D-HP's CPU frequency and battery temperature were not read before it turned off. The logs cannot separate "C is worse" from "D-HP throttled at low battery".
- G (10:41:51 to 10:43:24, partial): L-MSR1 4, L-STOP1 4, L-REOPEN/L-STALL/L-THREW/L-STOPPED 0, L-PCM 1, L-STARVE 1.
- Heard: 16 stamps in the capture, 15 inside M, 7 matched, 8 unmatched.
- The operator reported "a lot of stutters" and about 90% system usage during H3, which agrees with the mixer lines above.
- Sensory result, not part of any verdict: the brief's by-ear comparison of prompt start delay (F2) was not done.

## S1

**PASS**

D-SAM, arm B, Native AA, D-POCO. No PASS bar. Retry succeeded (setup note 5). Preflight: `WifiLauncher: Initializing WiFi Mode: NATIVE`, L-SSL 1, L-MATCH 0, two `createGroup SUCCESS` (D-SAM settles with two), first L-OUT `output AudioTrack requested=3360 effective=3343 minimum=3343`, channel 4 present.

- G (`S1-g` to `S1-g-end`, 292.6 s): L-MSR1 **9**, L-STOP1 9, L-REOPEN **0**, L-STALL **0**, L-THREW 0, L-OFAIL 0, L-EXHB 0, LOSS channel 4 **0** (channel 6 3791), L-PCM channel 4 0, S-UND 0, GAPW 4 of 38 (largest `loopGapMax` 124 ms), L-FIRST `requestToPcm=78ms pcmToRender=25ms`.
- Phone: P-FC 3, P-STATE 4, P-FATAL (Gearhead) 0. One `FATAL EXCEPTION` in the capture was the keyboard app `com.android.inputmethod.latin` at 10:54:45, before the window.

## S2

**PASS**

D-SAM, arm P. **F3 is not reproduced.** SSL at 11:02:33, `effective=3343` (same as B).

- G (292.6 s): L-STALL **0**, L-REOPEN **0**, L-THREW 0 (S1: 0, 0, 0); L-MSR1 6 equals L-STOP1 6; LOSS channel 4 0 (not above max(0, 4800)); L-PCM ch4 0 and ch6 1 (LOSS channel 6 1440); S-UND 0; GAPW 5 of 35 (largest 138 ms); L-RES 63; L-PAUSED/L-EXH/L-STARVE ch4 0; L-PEND 0 (note 8); L-FIRST `requestToPcm=38ms pcmToRender=7ms`. Only 6 of 8 navigation intents produced an `AUDIO1` start.
- Phone: P-STATE 4, P-FATAL 0, all other P 0. No Start-to-stall times to give: no stall occurred.

## S3

**PASS**

D-SAM, arm C. First L-OUT `requested=3360 effective=9600` (bar 9600 or more). SSL 11:10:22.

- G (292.4 s): L-STALL 0, L-REOPEN 0, L-THREW 0, L-OFAIL 0, L-STOPPED 0; L-MSR1 8 equals L-STOP1 8; LOSS channel 4 0; S-UND 0; GAPW **0** of 37 (largest `loopGapMax` 112 ms); L-RES 66; L-FIRST `requestToPcm=51ms pcmToRender=42ms`.
- F3's count from S2 to S3: 0 to 0.
- Phone: P-STATE 4, P-FATAL 0, all other P 0.

## W1

**PASS**

D-HU, arm B, API 34. No PASS bar. Preflight: NATIVE, L-SSL 1 (11:18:11), L-MATCH 0, channel 6 `latencyMultiplier=2`, `static-audio-focus` read back false, first L-OUT `requested=4320 effective=4320 minimum=3848 xruns=0`.

- M (243.4 s): LOSS 0, UND 0, GAPW 0 of 30, largest `loopGapMax` 23 ms, `xruns=` 0 to 0, L-MSR1 6 = L-STOP1 6.
- F (`W1-F` to `W1-F-end`, 37.6 s; `setprop ctl.stop audioserver`, 12 s, `ctl.start`, `getprop init.svc.audioserver` printed `running`): L-FOCUS 0, L-REL 0, L-FCH 0, L-REOPEN/L-THREW/L-OFAIL/L-EXHB 0, one mixer gap of **14100 ms**, LOSS channel 6 613255 (media backlog discarded), `xruns=` stayed 0 so no point where it stops rising.
- G (142.4 s): L-MSR1 3 = L-STOP1 3, L-FOCUS 3, L-REL 4, GAPW 2 of 3.
- S-UF 0. L-FIRST `requestToPcm=44ms pcmToRender=56ms`. Phone: P-STATE 4, P-FATAL 0, other P 0.

## W2

**PASS**

D-HU, arm P. Bars (the API 23+ control): M LOSS 0 is not above max(0, 4800); UND 0 is not above 0; L-REOPEN/L-THREW/L-STOPPED 0 in M and G; phone P-STATE 4, P-FATAL 0, P-ACK 0 (W1: 0).

- **F1 is INCONCLUSIVE.** In F (37.6 s) L-PAUSED, L-EXH, L-REOPEN, L-THREW and L-OFAIL are all 0 on W2, so the 12 s audioserver stop never reached the retry path. L-FOCUS in F is 0 and L-REL 0 on both W1 and W2. The outage shows as a single 14076 ms mixer gap and 613029 channel 6 frames discarded.
- M: GAPW 0 of 30 (largest 22 ms), L-RES 55 in 243 s (13.6 per minute), `renderWaits=` sum 0, L-STARVE 0. G (142.3 s): L-MSR1 4 = L-STOP1 4, L-FOCUS 4, L-REL 4, L-PEND 0.
- L-FIRST `requestToPcm=45ms pcmToRender=18ms`. S-UF 0.

## W3

**PASS**

D-HU, arm P, `audio-latency-multiplier` 16 (F5). Preflight: channel 4 `latencyMultiplier=16`.

- L-FIRST: `requestToPcm=86ms pcmToRender=179ms target=19200 depth=21024 outputBudget=4320 frames` (bar 450 ms or less). W2 at multiplier 2: `requestToPcm=45ms pcmToRender=18ms`.
- G (107.0 s): L-MSR1 4 = L-STOP1 4, L-REOPEN/L-THREW/L-STALL 0, LOSS channel 4 0, UND 0, L-FOCUS 0. Phone: P-STATE 4, P-FATAL 0.

## Report back (brief section 11)

1. **The point, D-HP** (music segment):

   | | H1 B | H2 P | H3 C |
   |---|---|---|---|
   | `effective=` | 2229 frames (46.4 ms) | 2229 frames (46.4 ms) | 9600 frames (200 ms) |
   | LOSS (frames) | 42169 | 10444 | 153893 |
   | UND | 0 | 0 | 0 |
   | GAPW | 31 of 64 | 23 of 62 | 11 of 72 |
   | heard stamps (matched) | 18 (8) | 7 (5) | 15 in M (7) |

   P against B: REDUCED. C against P: not REDUCED, worse on LOSS, with H3 cut short by a dead battery and the stalls beginning at about 11% (setup note 4, H3).
2. **F3, D-SAM:** L-STALL and L-REOPEN in G are 0 on S1, S2 and S3. F3 is not reproduced on S2 or S3.
3. **F1, D-HU:** L-FOCUS and L-REL in F are 0 and 0 on W1 and 0 and 0 on W2. F1 is INCONCLUSIVE.

Also: `GUIDE=yes` in all three stages. L-FIRST on every arm is in the run sections. W3's `pcmToRender=` is 179 ms. L-RES per minute is 13.3 (H2), 12.4 (H3), 12.9 (S2), 13.6 (W2). Every phone count is in the run sections; the only non-zero ones are P-FC 3 and P-STATE 4 per session and H3's P-PERM 14. P-ACK is 0 on every arm.

## Anything the brief did not ask about

- **S-UND reads 0 on every D-HP run, while AudioFlinger's own per-track counters are not zero.** The `dumpsys media.audio_flinger` read in G (25 s after `g3`, host reads, decides nothing) lists two AudioTrack rows with an Underruns column: H1 449 and 12, H2 30 and 536. The ROM never prints `disabled due to previous underrun`, so S-UND is not a usable instrument on API 17. H3's dump was lost with the battery.
- **A larger buffer is not free on this unit.** C's 9600-frame buffer coincided with `renderWaits=` 262 and 68 starvations on channel 6 in H3, where P had 0 and 1. Whether the buffer or the dying battery drives that is not separable from this data.
- **A 12 s audioserver stop on API 34 costs about 14 s of mixer time and discards the media backlog, with no recovery logic exercised** on either B or P (W1, W2). F1's focus-retake finding needs an outage that makes the output fail, not one that only stalls it.
- **The phone's `NO_SUPPORTED_WIFI_CHANNELS(-8)` answer** appeared in the failed S1 attempt; the successful retry's value was not compared.
- **D-HU settings carried a prior thread's worst-case differences** (setup note 7). The brief's values were written, so the arms are comparable with each other, not with the file as found.
- **Tooling.** The brief's `loc()` clobbers `L`; the brief's `extract_brief_blocks.py` call produced no files for this layout; D-SAM `logcat -c` does not clear, so every S window is marker-clipped (as the brief says); the D-HP Slate has no `logcat -G`.

## Closing

Done after the operator's approve, 2026-10-16:

- **Settings restored and read back.** D-HU's round-start `settings.xml` is back byte-identical (root copy, `chown u0_a176:u0_a176`, `chmod 660`). D-SAM's is back identical once carriage returns are stripped from both sides (`adb shell` adds them); `screen-orientation` reads 4 again. **D-HP's was not restored: the unit is off after its battery died, so its round-start file (kept in the evidence folder) still has to be written back, and `adb usb` has not been issued on D-HP.**
- **APKs.** `p.apk` (md5 `a8b12a81c3730a1c663498c97d37ae01`) is installed on D-SAM and D-HU, pulled and hashed on each. D-HP still carries `c.apk`, because it could not be reached.
- **Phones.** `svc power stayon false` on D-MOTO and D-POCO; Spotify and Maps force-stopped; `adb usb` issued on D-MOTO (it reads `offline` until the operator plugs it back into the PC).
- **Radios and services.** Bluetooth enabled on D-SAM and D-HU; `getprop init.svc.audioserver` on D-HU reads `running`. Both `bthold` loops killed by pid; the thermal watches ended with their runs; the monitors' leftover `tail` processes and an idle Gradle daemon were killed by pid.
- **Closing pre-flight, connected units** (`rig_cleanup.sh` reads `no strays, lock free`):

  | Role | adb | WiFi | BT | Screen |
  |---|---|---|---|---|
  | D_SAM | ok | 1 | 1 | Awake |
  | D_POCO | ok | 1 | 1 | Awake |
  | D_HU | ok | 1 | 1 | Awake |

  `PREFLIGHT OK`. With all five roles it fails, because **D_HP and D_MOTO are not in `adb devices`** (D-HP off, D-MOTO in USB mode awaiting the cable move). Both are reported to the operator, not left for the next round.
- **Build worktree** removed; APKs moved to `rig-data/apks/pr-1114-audio-continuity-round1/`.
- **Evidence.** Release `rig-evidence-pr-1114-audio-continuity`, asset `pr-1114-audio-continuity-round1-captures.zip`, 40402693 bytes, sha256 `52783cf29c7d7ba5f3fbb24992f192c10f29c01ff21c2cba862c00fa8a97e2da`. The bundle holds the captures, grades, analyses, one-round scripts and the voided attempts. It leaves out the settings backups (they hold the wifi-direct group passphrase), `loc.txt` and the status files (the phone's GPS fix), the `-script.log` files and the dedup copies. The phone captures still contain the phone's own location log lines.
