# link-stall-periodic-scan — round 3 results

**Candidate:** `fork/fix/video-stack` @ `9a1257ca` (pinned per brief; branch tip has since moved to
`b57eae3a`, not used here) **Baseline:** none (single-APK A/B via settings only), plus an optional
downgrade build of `v.3.1.1` @ `15b2a0d2` for R4.
**APK md5:** candidate `cc822ea4cdcd0402c0eda2c9227b28b3` / v.3.1.1 `70713eeff6169bee9848b576fe33048f`
**Unit:** UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14, panel 1440x720 landscape (720x1440
physical), `ro.hardware=uis7861_6h10`, `ro.soc.manufacturer=Spreadtrum`
**Date:** 2026-08-19

## Setup notes

- Scripts inventoried at round start: `build_hur.sh`, `run_unit_tests.sh`, `install_and_launch.sh`,
  `set_hu_pref.sh` / `set_hu_prefs.sh`, `test_native_aa.sh`, `refresh_apks.sh`, `build_all.sh` all
  present and used as documented in `code-researchs/hur-wifi-test-scripts-inventory.md`. Used
  `build_hur.sh` + `run_unit_tests.sh` for R0, `set_hu_prefs.sh` for every multi-key settings write,
  `install_and_launch.sh SKIP_BUILD=1` for the candidate install.
- **New script added, as directed:** `hur-wifi-test-scripts/wire_bitrate.py`, exactly as specified
  in the brief.
- The candidate's own working-tree checkout doubles as the doc branch's checkout (see repo's
  `.claude/CLAUDE.md` on this quirk): `app/`/`contract`/etc. show as untracked on the transfer
  branch and are not part of its history. Built by checking `headunit-revived` itself out to the
  pinned SHA (detached HEAD), building there, then switching back.
- **R2's first attempt is void and superseded by "R2-redo".** After the R1→R2 transition
  (`headunit://exit` + force-stop + relaunch), Spotify had silently paused — the exit/relaunch cycle
  dropped media focus and nothing resumed it. The first R2 capture (`link-stall-round3-R2-h264.txt`,
  kept for the record) shows 0 bytes on every AUDIO channel for its whole 342s span: video-only
  metrics in it are real, but its audio-underrun number is vacuous, not evidence. Caught by checking
  `wire_bitrate.py`'s per-channel breakdown against R1's. Fixed by explicitly checking
  `dumpsys media_session` for `state=PLAYING` before starting the real R2 capture, and rechecking it
  every 4th swipe (`spotify check:` lines in the swipe-loop log) for R2-redo and R4. **All numbers in
  this report for "R2" are R2-redo's.**
- **Every run's capture contains one `MATCH! Starting AapService` and, in R1 and R4, two
  `p2p-wlan0-N` values.** Investigated per the discard-rule check (§4) rather than reflexively
  discarding: in every case `AapService.onCreate` and `WifiDirectManager...createGroup SUCCESS!`
  each appear exactly once, so no second service instance and no second group ever actually formed.
  The `MATCH!` line fires because the protocol's own sequencing (head unit launched first, then the
  phone's Bluetooth brought up ~15-25s later) makes the phone's real ACL reconnect land while
  `AapService` is already running; `AutoStartReceiver` logs the match unconditionally but starting an
  already-running service is a no-op. The second `p2p-wlan0-N` value is the previous run's stale
  interface being torn down in the same capture window as the new one being created, both within the
  first ~2s after launch, before any real group activity for the run being measured. Treated as
  benign and kept; a literal re-run would reproduce the identical pattern every time under this
  protocol, so re-running would not have removed it.
- `svc bluetooth enable` on the phone was used as the only radio lever (per §7a; airplane mode is
  broken via adb on this phone). Head unit's own Bluetooth was left alone throughout (per §7a, it
  cannot be reliably switched off on this rig).
- One live Bluetooth/A2DP link, and one P2P group per real session, was reused/rebuilt across R1→R2
  as the brief asked ("head-unit-only resets"); R2→R4 needed a full app reinstall (different
  versionCode) so that session was rebuilt from scratch, still on the same underlying phone-side
  Bluetooth pairing.
- R4 (`v.3.1.1`) predates `AppLog`'s `Throughput over...rendered=` instrumentation, so no
  frames-rendered/dropped counter exists in that build's log; fps and bitrate for R4 come from
  `wire_bitrate.py`'s frame-start count instead, which both builds support.

## R0 — gate and preconditions

**PASS**

- `build_hur.sh` on `fix/video-stack` @ `9a1257ca` (checked out via detached HEAD): **BUILD
  SUCCESSFUL**. APK: `com.andrerinas.headunitrevived_3.2.5_debug.apk`, md5
  `cc822ea4cdcd0402c0eda2c9227b28b3`, verified identical on-device after install.
- `run_unit_tests.sh`: **BUILD SUCCESSFUL**, **525 tests** (summed from
  `app/build/test-results/**/*.xml`'s `tests="N"` attributes) — matches the brief's expectation
  exactly.
- With `force-software-decoding=false`, a real session's decoder configure logged:
  `findBestCodec: hw=c2.unisoc.hevc.decoder, sw=c2.android.hevc.decoder, preferHardware=true,
  selected=c2.unisoc.hevc.decoder` and `Configuring decoder: c2.unisoc.hevc.decoder for 1920x1080,
  ...`. **This rig has a real hardware HEVC decoder** (`c2.unisoc.hevc.decoder`), not a software
  fallback — R1/R2 proceeded on hardware decode for both arms.

## R1 — H.265 arm

**PASS**

- Settings written: `video-codec=H.265`, `force-software-decoding=false`, `log-level=0`,
  `wifi-connection-mode=3`, `view-mode=0`, `fps-limit=60`.
- Negotiation: `Media Sink Setup Request: 7 on channel VIDEO` at 19:10:58.838 — matches the setting.
- Radio state: head unit launched 19:10:25; phone Bluetooth brought up (`svc bluetooth enable`) at
  19:10:52, ~15s after `WifiDirectManager: 5GHz createGroup SUCCESS!` (19:10:27); session (SSL
  handshake) completed 19:10:57.897, ~6s after the phone's Bluetooth came up.
- Discard-rule check: see Setup notes — one benign `MATCH!`, one benign interface-index bump
  (`p2p-wlan0-6` teardown / `p2p-wlan0-7` created), `createGroup SUCCESS` count 1, no
  `Magic Garbage`, one real handshake. Treated as clean.
- Duration: 307.8s span (RECV-derived), 10 swipes issued (1 at +20s, 9 more every 25s).
- `recv_gaps.py`: **0 stalls > 1.2s, 0.0% dead time**, audio delivered 192.1 kB/s = **100.1%** of
  real time.
- `wire_bitrate.py`: **VIDEO 0.688 Mbit/s**, AUDIO 1.200 Mbit/s, **15825 frame starts = 51.4 fps**,
  **1674 bytes/frame**.
- `LinkGapMonitor` `inbound link quiet` count: **0** — agrees with `recv_gaps.py`'s 0 stalls.
- `disabled due to previous underrun`: **0**.
- Throughput log (61 five-second windows): **avg 51.6 fps rendered, 0 dropped, 0 skipped**,
  throughout — `codec=c2.unisoc.hevc.decoder` on every line.

## R2 — H.264 arm (R2-redo; see Setup notes)

**PASS**

- Settings: identical to R1 except `video-codec=H.264`.
- Negotiation: `Media Sink Setup Request: 3 on channel VIDEO` at 19:18:33.653 (from the original R2
  session start, which R2-redo continues without a new negotiation — confirmed 0
  `Media Sink Setup Request` lines in the redo capture, i.e. no session churn between the two
  windows).
- Radio state: same live Bluetooth link and P2P group carried over from R1 (head-unit-only reset:
  `headunit://exit`, force-stop, settings rewrite, relaunch).
- Discard-rule check on the redo capture (the one these numbers are from): **all zero** —
  `MATCH!` 0, `createGroup SUCCESS` 0, one `p2p-wlan0-8` value throughout, no `Magic Garbage`, no
  handshake line (expected: no new handshake occurred in this window, it's a mid-session capture).
  Cleanest capture of the round.
- Duration: 322.9s span, 12 swipes issued every 25s. Spotify confirmed `state=PLAYING` at 3
  checkpoints spanning the run (19:27:03, 19:28:45, 19:30:26).
- `recv_gaps.py`: **0 stalls > 1.2s, 0.0% dead time**, audio delivered 192.0 kB/s = **100.0%** of
  real time.
- `wire_bitrate.py`: **VIDEO 0.798 Mbit/s**, AUDIO 1.538 Mbit/s, **16290 frame starts = 50.5 fps**,
  **1977 bytes/frame**.
- `LinkGapMonitor` `inbound link quiet` count: **0** — agrees with `recv_gaps.py`'s 0 stalls.
- `disabled due to previous underrun`: **0**.
- Throughput log (64 five-second windows): **avg 50.5 fps rendered, 0 dropped, 0 skipped**.

**The two bitrate ratios (R2/R1):** VIDEO Mbit/s **1.160×** (0.798/0.688), bytes-per-frame
**1.181×** (1977/1674), at comparable fps (50.5 vs 51.4/51.6). Neither arm reproduced any stall,
exactly as the brief predicted for this rig.

## R3 — the desk check

**PASS**

**(i) `SystemOptimizer.calculateOptimalSettings`, this rig's real panel (1440x720 landscape):**
`panelCeiling(1440, 720, hasH265=true)` → `longSide=1440 > 1280` → falls to
`Settings.Resolution._1920x1080`, so **`panelCeil.width = 1920`**. `hasH265` (from
`VideoDecoder.isHevcSupported()`) is **true** on this rig (R0 confirmed a real hardware HEVC
decoder). `recommendedCodec = hasH265 && panelCeil.width > 1920` — **1920 is not `> 1920`**, so the
condition is **false** regardless of `hasH265`. **A fresh first-time setup on this rig's own panel
would persist `"H.264"`** — the same outcome as #839's sub-1080p unit, but by a different path:
#839's panel is genuinely sub-1080p, this rig's panel rounds up to exactly the 1080p ceiling, one
pixel short of the `> 1920` threshold either way.

**(ii) `VideoDecoder.isHevcReliable()` chipset allowlist**, this rig's actual property values:
`ro.hardware=uis7861_6h10`, `ro.soc.manufacturer=Spreadtrum`. Neither starts with/contains
`qcom`/`msm`/`exynos`/`gs`/`google`/`mt68`/`mt69` — **`isHevcReliable()` returns `false` on this
rig**, exactly as the brief anticipated. This is the second data point against the predicate: this
rig decoded HEVC at hardware speed with zero stalls and zero dropped/skipped frames over 307.8s
(R1), while the allowlist that gates `hevcAvailableForHighResolution` and Auto-mode 4K H.265 would
reject it, same as it rejects #839's MediaTek `ac8227l` (which is the one unit in the evidence set
where H.265 measured cleanly, 58 fps over USB).

**(iii) Confirmed both readings, against the pinned candidate source, line-and-verse:**
- `ServiceDiscoveryResponse.kt:63-68` (video service builder, `codecToRequest`): on
  `settings.videoCodec == "H.265"`, announces `MEDIA_CODEC_VIDEO_H265` only if
  `hevcAvailableForUserChoice` (`isHevcSupported() || explicitSoftwareHevc`) is true, else falls back
  to `MEDIA_CODEC_VIDEO_H264_BP`. **Capability-gated, as the brief read it.**
- `VideoDecoder.kt:750-761` (`decode()`'s first-packet init): `requestedType` is derived straight
  from `codecName` (i.e. `settings.videoCodec`, forwarded from `AapVideo.kt:246`) — `if
  (requestedType == CodecType.H265) CodecType.H265 else (detectedType ?: requestedType)`. **On
  `"H.265"` this unconditionally returns `H265` and never consults `detectedType`, discarding
  `detectCodecType()`'s answer exactly as the brief read it.** Confirmed on the exact pinned SHA.
  Notably, the *next* commit on the fork's `fix/video-stack` branch beyond the pinned SHA,
  `b57eae3a`, is titled *"Video: the codec setting is a preference to the phone and was a command to
  the decoder"* — strongly suggesting this exact gap is what that commit fixes, one commit later
  than what this round tested.
- **Could not be reproduced on this rig**, as expected and per the brief's own instruction not to
  spend rig time forcing it: `isHevcSupported()` is genuinely `true` here (R0), so the
  mismatch state (`"H.265"` selected while HEVC is undetected) cannot occur naturally. Left as a
  source-only confirmation; a JVM test is the right place for this one, as the brief anticipated.

## R4 — optional, v.3.1.1 residual-version check

**PASS** (built and ran on the first attempt; no `UNTESTABLE` needed)

- `git checkout v.3.1.1` (`15b2a0d2`) in the same repo checkout, `build_hur.sh`: **BUILD
  SUCCESSFUL** first try, APK `com.andrerinas.headunitrevived_3.1.1_debug.apk`
  (versionCode 84 vs candidate's 98 — installed with `adb install -r -d` for the downgrade;
  `set_hu_prefs.sh`/`install_and_launch.sh` don't support `-d`, so this one step used raw `adb
  install` directly). Package id unchanged (`com.andrerinas.headunitrevived`) even though this
  predates the Kotlin-package rename, so `settings.xml` and the pairing/bond state survived the
  reinstall intact.
- Settings: same as R2 (`video-codec=H.264` already in place from R2, confirmed unchanged after the
  downgrade install).
- Negotiation: `Media Sink Setup Request: 3 on channel VIDEO` at 19:36:19.067.
- Discard-rule check: `AapService.onCreate` and `createGroup SUCCESS!` each exactly once
  (19:36:01.286 / 19:36:10.097); the one `MATCH!` (19:36:14.613, ~4.5s after group creation) is the
  same benign phone-BT-reconnect pattern as R1/R2, confirmed causing no second `onCreate`.
- Duration: 366.6s span, 12 swipes every 25s, Spotify `state=PLAYING` confirmed at 3 checkpoints.
- `recv_gaps.py`: **0 stalls > 1.2s, 0.0% dead time**, audio delivered 192.1 kB/s = **100.0%**.
- `wire_bitrate.py`: **VIDEO 0.712 Mbit/s**, AUDIO 1.362 Mbit/s, **18401 frame starts = 50.2 fps**,
  **1773 bytes/frame**.
- `disabled due to previous underrun`: **0**. (No per-window rendered/dropped counters exist in this
  build — see Setup notes.)

**v.3.1.1 vs the candidate, both H.264, both clean:** VIDEO Mbit/s 0.712 vs 0.798 (candidate **12%
higher**), bytes/frame 1773 vs 1977 (candidate **11.5% higher**), fps 50.2 vs 50.5 (comparable). Both
zero stalls, zero underruns, ~100% audio delivery. **No residual version effect found on this rig**:
the reporter's suggestive 3.1.1-vs-3.2.4 gap (30fps-for-34s vs 0-20fps) does not reproduce here in
either direction — if anything the candidate carries marginally *more* wire cost than 3.1.1, the
opposite of what would explain a regression, and both are well within the noise this round's own R1
vs R2 spread already showed (~16-18%).

## Report back (per the brief's §5 checklist)

1. **Bitrate ratios, R2/R1:** VIDEO Mbit/s **1.160×**, bytes/frame **1.181×**, at comparable fps
   (50.5 vs 51.4-51.6). This is a real, moderate difference, not noise, but it does not clear the
   brief's own "≥1.4×" bar for "the bandwidth explanation holds" and it is well outside the "within
   ~10%" bar for "the bandwidth explanation is wrong" either. **It lands in the gap between the two
   pre-registered outcomes.** ~16-18% more wire cost for H.264 is real but modest — nowhere near
   "roughly twice the bits" (the brief's own framing of the naive story), and on its own is not an
   obvious explanation for an 8-10/min audio-underrun rate on the reporter's unit. The mechanism
   remains open; see R3(iii) for a concrete, source-confirmed alternative (a real behavioral bug, not
   speculation) that a JVM test should cover.
2. **Negotiation followed the setting in both arms:** yes — sink codec 7 in R1, sink codec 3 in R2
   (and again 3 in R4).
3. **Stalls, dead time, underruns per arm:** all zero in R1, R2, and R4. Exactly the expected result
   per the brief.
4. **`LinkGapMonitor` vs `recv_gaps.py`:** agreed at 0 stalls in both R1 and R2.
5. **R3: first-time setup on this rig's panel would persist `H.264`**, with `hasH265=true` and
   `panelCeil.width=1920` (the `>1920` threshold excludes this rig by exactly one pixel-bucket, not
   by lacking real HEVC hardware).
6. **HEVC hardware decoder was real:** `c2.unisoc.hevc.decoder`, confirmed via `findBestCodec` and
   `Configuring decoder` at session start, and by a full clean R1 run.
7. **Anything the brief did not ask about:**
   - R4 (v.3.1.1) built and ran cleanly on the very first attempt — the brief flagged this as
     "expected to be awkward" given the pre-rename toolchain, but it was not, at least for
     `assembleGithubDebug` on this checkout.
   - Every capture this round carried one `MATCH! Starting AapService` line and, in two of the four
     captures, two distinct `p2p-wlan0-N` values in the same window — both investigated in depth (see
     Setup notes) and found structurally unavoidable under this protocol's own "head unit up before
     phone" sequencing, not evidence of the group-churn feedback loop `CLAUDE.md` warns about. Worth
     recording as a standing quirk of this launch method rather than re-litigating it every round: a
     `MATCH!`/interface-index hit is not automatically a discard if `AapService.onCreate` and
     `createGroup SUCCESS!` each verify to exactly one occurrence.
   - R2's silent audio-focus loss after an `exit`+relaunch cycle (Setup notes) is itself worth a
     one-line flag for whoever owns the media-key-routing/audio-focus threads: nothing in the app
     resumes playback after a user-initiated exit and reconnect, so any brief that assumes music
     "stays playing" across a `headunit://exit` needs an explicit re-check, not an assumption.
