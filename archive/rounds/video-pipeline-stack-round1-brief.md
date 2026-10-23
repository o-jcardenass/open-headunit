# Video pipeline stack — round 1 brief

## 1. Build and baseline

**Two builds this round, both on the fork `o-jcardenass/open-headunit`.** Build A carries the whole
video stack; build B is one unrelated transport fix that could not be folded into it. Build A does
R1-R11, build B does R12 alone, so this is one visit rather than two.

```bash
# Build A - the video stack (R0-R11)
git fetch fork fix/video-backpressure-diagnostics
git checkout f008e3d124f7880ed0e94d886927b6236cc53b55        # short f008e3d1

# Build B - the transport fix (R12 only)
git fetch fork fix/aap-partial-read-desync
git checkout becebffa0928028da71c110c567343818d87500f        # short becebffa
```

**Copy each APK out of `apks/` into a round-specific folder as soon as it is built** — `build_hur.sh`
deletes the previous one before it builds, and this round holds two.

**No baseline APK is needed.** Every measurement is either a new log line with no counterpart on
`main`, or a number whose `main` value is a compile-time constant quoted in §5. If you build a
baseline anyway, `main` is `9f7c3b20` (release 3.2.5) — but the round is designed so you do not have
to.

**Build A is a stack of four branches.** `f008e3d1` contains all of them, and the whole thing is what
you install:

| Tip | Contains | Runs that cover it |
|---|---|---|
| `647e7428` `fix/video-reassembly-and-diagnostics` | reassembly correctness, fragment audit, fault injection, SPS reader, memory profile | R1, R2, R3, R4, R5 |
| `65ae5e0a` `fix/low-ram-pipeline-sizing` | codec input size, frame-pool budget, one SSL plaintext buffer per session | R1, R6 |
| `d89e26a2` `fix/decoder-selection-and-gles` | codec-config classification, capability report, configure ladder, GLES fixes | R1, R2, R7, R8, R9 |
| `f008e3d1` `fix/video-backpressure-diagnostics` | backpressure verdict, capability report at negotiation time | R10, R11 |

**Build B is `becebffa` `fix/aap-partial-read-desync`**, off `main` and *not* in build A: it makes a
short read on the socket end the session instead of skipping the message and desyncing the stream.
It is here because its only rig-testable question is "does this cause spurious disconnects", which is
a soak — cheap to run while you are already at the bench, and pointless as a round of its own.

History was **not** rewritten. The three lower tips were pushed on 2026-08-17 and have not moved
since; `f008e3d1` and `becebffa` were pushed on 2026-08-18.

**None of this has ever been compiled.** There is no JVM on the machine that wrote it, and no PR has
been opened, so CI has never seen it either. R0 is therefore the single most valuable run in the
round: if it fails, the compiler output *is* the result and everything after it is moot.

## 2. What this is and why it exists

Two open reports drove the work, and neither can be reproduced on a healthy rig.

**#219 — "melting"/smearing video artifacts**, open since March 2026, 69 comments, still reproducing
on 3.2.5. Reporters span a cheap Chinese unit, an Android 13 unit and a Galaxy Tab S7 FE, so it is
not only weak silicon. A code read of `AapVideo.kt` found three ways a frame could reach MediaCodec
already broken, **two of them completely silent**:

- a first fragment (flag 9) whose payload had no start code at offset 10 or offset 2 fell out of the
  dispatch with the assembly run *already armed*. The following flag 8/10 fragments appended to it,
  and the access unit was decoded missing its own head — no log, no corruption mark. #839's log
  contains three of the equivalent flag-11 case, so the no-start-code condition demonstrably occurs
  in the wild;
- a truncated run logged `Previous frame was truncated!` and asked for nothing, so the picture waited
  out the phone's fixed ~69 s keyframe cadence instead of ~2.7 s;
- a **missing middle fragment** left the run looking intact to the reassembler — first, middles, last,
  in order — so the frame was assembled with a hole in it and decoded as whole. Nothing in the app
  could see this at all. Both readers already read the 4-byte total the first fragment declares and
  threw it away; `FragmentedMessageAudit` now uses it.

**#839 — a 1 GB MediaTek unit** (API 27, `OMX.MTK.VIDEO.DECODER.HEVC`) reporting audio dropouts and
lag. Its log says `dropped=0` on every throughput line, so the decoder was keeping up with everything
the phone sent; what it also says is `ACodec: Allocating 8 buffers of size 2097152` — **16 MB of
input buffers**, because we asked for a flat 2 MB `KEY_MAX_INPUT_SIZE` — and a `Background concurrent
copying GC` every 5-10 s freeing 84-208 large objects, one of which paused 1.373 s. The large-object
churn traces to a fresh `ByteArray` allocated per *AAP message* in `AapSslContext.decrypt`.

So the four branches of build A, in one sentence each: **B1** stops assembling frames that are known
broken and makes every remaining failure mode visible; **B2** asks the codec for a buffer the size of
the picture instead of a flat 2 MB and stops allocating per message; **B3** stops telling the codec a
keyframe is only configuration, reports whether the chosen decoder can actually carry the stream, and
fixes four defects in the GLES backend including one that could black-screen it for a whole session;
**B4** says out loud when the codec is the thing losing the frames.

**B4 exists because a fifth set of logs arrived while this brief was being written**, from a #219
reporter's Galaxy Tab S7 FE. That device negotiates **2560x1440 HEVC** — the app forces H.265
whenever the resolution is 1440p, so the user cannot pick H.264 without dropping resolution — and its
four captures shed frames only in throughput windows that also spent a large share waiting for a
codec input buffer: 29 drops at `inputWait=2019ms`, 18 at 1333 ms, 11 at 804 ms, against a median of
188 ms across 288 windows. Its reassembly counters would all read zero. Both numbers have been
printed on every throughput line for a long time and nothing has ever read them together, which is
how that report was mistaken for a reassembly fault for five months. B4 reads them, and asks the
capability question at the point where the codec is actually chosen.

**Build B is a separate defect found in the same logs.** `AapReadSingleMessage` returned "carry on"
after four failure paths that leave unread bytes on the socket, and AAP over a socket cannot
resynchronise: one measured instance was followed within four seconds by 69 `WRONG FLAG` lines with
channel numbers like -120 and 63 `SSL Decrypt failed`. It now disconnects instead, which is
recoverable where a desync is not.

**The fault injector is the reason the injection runs can exist.** A healthy rig produces none of #219's
conditions — three previous decoder rounds measured `dropped=0` and never reproduced it. So the
branch ships a hidden setting that corrupts the fragment stream deliberately, in the exact shapes
above, deterministically (every Nth matching message, not a random draw). That turns "the fix works"
into something measurable here rather than inferred from a reporter's next drive. Every injected
fault is logged, so a capture taken with it on cannot be mistaken for a capture of a real fault.

## 3. What is different about this round

- **R0 is a hard gate, for both builds.** First compile of either, anywhere. A failure stops the
  round for that build — quote the compiler output verbatim, it is the deliverable.
- **R10 and R11 came from a real device, and this rig cannot show what they were built for.** They
  were added after four 3.2.5 logs from a #219 reporter's Galaxy Tab S7 FE, which negotiates
  2560x1440 HEVC and sheds frames only in throughput windows that also spent a large share waiting
  for a codec input buffer — up to 2019 ms of 5000. This rig's 1440x720 panel will never negotiate
  that profile, so R10 has to be **provoked** with round 6's CPU-burst lever and R11 will almost
  certainly come back adequate. Both are still worth running: R10's failure mode is firing on a
  healthy run, and R11 tells us what an ordinary unit's line looks like so the reporter's can be read
  against it.
- **R1 gates R3-R5.** The injection runs target *fragmented* video messages, and this rig may barely
  produce any: round 4 measured a healthy stream whose keyframes were 67-78 KB against a 1.5 KB
  median, with single-message frames scattered up to 18 KB. If `AapRead: fragment accounting
  established for VIDEO` never appears in R1's five minutes, the video stream here does not fragment,
  and R3-R5 are **INCONCLUSIVE** — say so and move on. Do not try to manufacture fragmentation. The
  JVM side of that coverage is already 40 tests (21 assembler + 11 audit + 8 injector).
- **The fault injector sits downstream of the framing audit, so it cannot exercise it.** Checked
  while writing this brief: `auditFragment()` is called in `AapReadSingleMessage.doRead` before the
  message is even decrypted, and the injector is a single call site in `AapVideo.process`, well after
  that. So an injected drop is invisible to `FragmentedMessageAudit` — every byte really did reach the
  reader. **`VideoFaultInjector.Mode`'s own KDoc claims otherwise** (it says DROP_MIDDLE_FRAGMENT
  produces `DELTA_CHANGED` and DROP_FIRST_FRAGMENT produces `ORPHANED_FRAGMENT` from the audit); that
  KDoc is wrong and is a defect to fix in the branch, not something for you to chase on the rig. The
  runs below are written to what the wiring actually does. The audit's own coverage is its 11 JVM
  tests plus the `FIRST_OBSERVATION` line R1 harvests; on real hardware it can only fire when *the
  reader itself* skips a fragment (`AapRead: Failed to read fragment total size. Skipping.`,
  `Invalid message size`), which is not scriptable here.
- **R2 may be INCONCLUSIVE by negotiation.** It asks for H.265. If the phone negotiates H.264 anyway
  — `Stream SPS (H.264)` appears, or `Codec initialized:` names an AVC component — record that as the
  finding and mark R2 INCONCLUSIVE. R8 then has no HEVC to work with either; skip it and say so.
- **R6 has deliberately weak power, stated up front so a null result is not read as a failure.** The
  forced `CONSTRAINED` profile changes exactly two things, and neither is logged: the frame pool's
  minimum slot (64 KB → 16 KB) and its total budget (unbounded → 2 MB). On a 3.8 GB rig with ~78 KB
  keyframes the difference is single-digit MB and will very likely sit inside `dumpsys meminfo`
  noise. R6's real job is **regression**: the constrained path must not cost throughput. Report the
  memory numbers, but an unchanged reading is a PASS, not a FAIL.
- **The `ACodec: Allocating N buffers of size M` line may not exist on Android 14.** #839's unit is
  API 27 and used ACodec; this rig may be on Codec2, where the equivalent is a `CCodec`/`C2` line or
  nothing at all. Grep for both. If neither appears, the app-side `Codec input buffer: requested X,
  got Y` line is the primary evidence and the framework line is a bonus — not a failed run.
- **R12 is a soak on a different APK, and a null result is the pass.** Build B only changes what
  happens when a socket read comes up short, which does not happen on a healthy link. Its
  `Disconnecting to resync.` lines should never appear; if one does on an undisturbed session, the
  policy is wrong and that is the finding of the round.
- **Two runs deliberately produce a bad picture** (R3, R5 at their stated rates). That is the positive
  control, not a defect. R4 deliberately produces a bad picture *with a clean reassembly summary* —
  that is the whole point of it.
- `log-level=2` (INFO) is enough for every line this round needs. All of them are `AppLog.i`/`.w`/`.e`
  with no `LOG_VERBOSE` guard anywhere in the four files involved — checked against the guard, not the
  call. Prefer it to VERBOSE: this unit's driver stack wraps the ring buffer.
- **Screen moving for every capture**, using the media-playback route rounds 1, 4, 5 and 6 all used.
  Round 5's lesson still applies: the media session reverts to `PAUSED` after the force-stop
  `set_hu_prefs.sh` performs, so resend the play key after each relaunch and confirm `PLAYING` via
  `dumpsys media_session` before starting a timed capture.

## 4. Settings

Types: `log-level`, `view-mode`, `software-video-decoder`, `debug-video-fault-injection`,
`debug-video-fault-rate` are **int**; `video-codec` and `debug-force-memory-profile` are **string**;
`force-software-decoding` and `debug-video-low-latency` are **boolean**. "delete" means run only the
removal half of §1's template, so the key reads as its default.

| Key | R1 | R2 | R3 | R4 | R5a | R5b | R6 | R7 | R8 | R9 | R10 | R12 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `log-level` | `2` | `2` | `2` | `2` | `2` | `2` | `2` | `2` | `2` | `2` | `2` | `2` |
| `video-codec` | `H.264` | `H.265` | `H.264` | `H.264` | `H.264` | `H.264` | `H.264` | `H.264` | `H.265` | `H.264` | `H.264` | `H.264` |
| `view-mode` | `0` | `0` | `0` | `0` | `0` | `0` | `0` | `2` | `2` | `0` | `0` | `0` |
| `force-software-decoding` | delete | delete | delete | delete | delete | delete | delete | delete | `true` | delete | delete | delete |
| `software-video-decoder` | delete | delete | delete | delete | delete | delete | delete | delete | `1` | delete | delete | delete |
| `debug-video-fault-injection` | delete | delete | `4` | `2` | `1` | `3` | delete | delete | delete | delete | delete | delete |
| `debug-video-fault-rate` | delete | delete | `2` | `3` | `2` | `2` | delete | delete | delete | delete | delete | delete |
| `debug-force-memory-profile` | delete | delete | delete | delete | delete | delete | `CONSTRAINED` | delete | delete | delete | delete | delete |
| `debug-video-low-latency` | delete | delete | delete | delete | delete | delete | delete | delete | delete | `true` | delete | delete |

R11 has no settings of its own — it is read out of R1's and R2's captures. R12 runs on **build B**,
which has none of the `debug-*` keys at all; writing them there is harmless but pointless.

`view-mode`: 0 = SURFACE, 1 = TEXTURE, 2 = GLES.
`software-video-decoder`: 0 = DEVICE_MEDIACODEC, 1 = BUNDLED_FFMPEG.
`debug-video-fault-injection`: 0 = OFF, 1 = DROP_FIRST_FRAGMENT, 2 = DROP_MIDDLE_FRAGMENT,
3 = DROP_LAST_FRAGMENT, 4 = HIDE_START_CODE.
`debug-force-memory-profile` is stored **by name**, not by ordinal: the string is literally
`CONSTRAINED`, `NORMAL` or `AMPLE`.

Use `set_hu_prefs.sh` (the multi-key sibling) for every run here — each one writes at least three
keys and `set_hu_pref.sh` would relaunch the app between them.

## 5. The lines that decide every run

All verified with `grep -F` against `f008e3d1` (build A) and `becebffa` (build B) before this brief
was written. Level is the `AppLog` call's own priority; every one of them survives `log-level=2`.

**New in this stack — these do not exist on `main` at all, so their presence also confirms which APK
is live:**

| Line | Level | Means |
|---|---|---|
| `Stream SPS (H.264): profile=… num_ref_frames=… bitstream_restriction=… num_reorder_frames=…` | I | one-shot, the phone's actual parameter set. **The deliverable of R1.** |
| `Stream SPS (H.265): profile=… max_dec_pic_buffering=… max_num_reorder_pics=…` | I | same, HEVC. **The deliverable of R2.** |
| `Stream SPS (…): could not be parsed (N bytes)` | W | the reader gave up — a finding, quote the bytes around it |
| `H.264 SPS parsed: WxH (negotiated WxH)` | I | dimensions now come from the stream on **both** codecs |
| `Decoder capability: codec=… sizeSupported=… rateSupported=… sustains=… featureLowLatency=…` | I | the component's own claim about the stream it was handed |
| `Decoder may not manage this stream: …` | W | same content, at WARN, when any coverage answer was negative |
| `Configuring decoder: <name> for WxH, max-input-size=NKB, memory=…, queue=N frames, optionalKeys=…` | I | **the B2 headline number and the ladder rung, on one line** |
| `Codec input buffer: requested NKB, got MKB per buffer` | I | what the component actually did with the request |
| `Decoder rejected optionalKeys=<label>: <msg>` | W | a ladder rung failed; the next one is tried |
| `Decoder accepted the format only with optionalKeys=<label>` | W | configure survived on a lower rung |
| `AapRead: fragment accounting established for VIDEO: channel=2 fragments=N declaredTotal=X observed=Y delta=Z` | I | the framing audit learned this channel's convention. **Gate for R3-R5.** |
| `AapRead: DELTA_CHANGED on VIDEO - channel=2 fragments=…` | W | a run's bytes did not add up to what its first fragment declared — the missing-middle detector. **Not reachable by injection** (§3); if it appears at all this round, that is a finding about the reader and worth the round on its own |
| `AapRead: ORPHANED_FRAGMENT on VIDEO - …` / `AapRead: TRUNCATED_RUN on VIDEO - …` | W | the audit's other two outcomes; capped at 10 reports each. Same note: injection cannot cause these |
| `AapRead: largest message body so far: N bytes (on VIDEO)` | I | high-water mark, printed only when it crosses a power of two |
| `AapVideo: reassembly anomalies over Nms: truncated=N, orphan=N, headless=N, overflow=N` | W | 5 s summary, emitted **only when something went wrong** |
| `AapVideo: First fragment has no start code at offset 10 or 2 (len=N, first bytes …)` | W | the silent case, now loud |
| `AapVideo: frame truncated, requesting keyframe to recover stream` | W | truncation now asks for the repair |
| `AapVideo: first fragment has no start code, requesting keyframe to recover stream` | W | so does the headless case |
| `AapVideo: FAULT INJECTION IS ON - mode=…, one in N.` | W | printed once at session start whenever injection is enabled |
| `AapVideo: FAULT INJECTED (#N): DROP on flag 9, len=…` | W | one per injected fault; **count these** |
| `GlProjectionView: direct YUV upload missed its 50ms deadline; using the staged copy for the rest of this session` | W | the direct path latched off — expected at most **once** per session |
| `VideoDecoder: the codec is the bottleneck - N windows shed frames while waiting >=10% of the window for an input buffer (WxH@F on <codec>). …` | W | **R10.** One-shot. Followed by ` It claimed it could: …` or ` It said it might not: …`, quoting the configure-time capability line |
| `[ServiceDiscovery] Negotiating a profile this device claims to carry: codec=… sizeSupported=… sustains=…` | I | **R11.** The capability question asked where the codec is actually chosen, before the phone is told anything |
| `[ServiceDiscovery] Negotiating a profile no decoder here claims to carry: … Frames shed under load and the artifacts that follow are the expected consequence.` | W | same line when the answer is negative — the finding this was built to produce |
| `[ServiceDiscovery] No decoder capability available for <mime> at WxH` | I | the query could not run; not a failure by itself |

**New in build B (`becebffa`) only — none of these exist in build A or on `main`:**

| Line | Level | Means |
|---|---|---|
| `AapRead: body read returned N of M expected - an unknown number of bytes were consumed, so the stream can no longer be framed. Disconnecting to resync.` | E | the path the fix exists for |
| `AapRead: partial header, N of 4 bytes. …Disconnecting to resync.` | E | ditto, at the header |
| `AapRead: fragment total read returned N of 4 - …Disconnecting to resync.` | E | ditto, at the 4-byte total |
| `AapRead: declared message size N is outside the M-byte buffer - the stream is no longer framed. Disconnecting to resync.` | E | ditto, on a length that cannot be real |
| `AapRead: WiFi read timeout (15000ms) - connection lost.` | W | **unchanged behaviour**, reworded. Nothing was consumed here, so this is the one short read that is not a desync |

**Pre-existing, used as sentinels:**

| Line | Level | Means |
|---|---|---|
| `Throughput over Nms: rendered=N (Nfps), fed=N (Nfps), dropped=N, skipped=N, inputWait=Nms, codec=…` | I | the global regression sentinel |
| `Codec initialized: <name>` | I | which component; should appear **once** per session |
| `Feed thread started (queue holds N frames, Mms at Ffps)` | I | queue depth; **unchanged by this stack** — expect 30 at 60 fps |
| `Configuring bundled FFmpeg HEVC decoder for WxH` | I | R8's confirmation the software path is live |
| `GlProjectionView: first YUV420 frame queued WxH strides=…` | I | the GLES YUV path produced a frame |
| `AapVideo: Dropped Flag 11 packet. len=N` | W | pre-existing and **harmless at small len** — control traffic, not lost picture |
| `Frame larger than the codec input buffer: N > M bytes. Dropping frame.` | W | **a FAIL sentinel this round** — see R1 |
| `SSL Decrypt: produced N bytes, larger than the M-byte plaintext buffer` | E | **must never appear.** The one line that would mean B2's shared-buffer change is wrong |
| `AapRead: Magic Garbage detected in header` | E | discard rule, and also the second symptom a broken shared buffer would produce |
| `Fallback to negotiated dimensions: WxH` | I | the SPS reader failed and the old path took over |
| `Input buffer full. Dropping frame.` | W | now rate-limited to one line/second with a `(N more suppressed)` suffix |

**Numbers with a known `main` value, for the runs that need a comparison without a baseline build:**

| Quantity | `main` (`9f7c3b20`) | build A at 1280x720 |
|---|---|---|
| `KEY_MAX_INPUT_SIZE`, H.264, API ≥ 28 | flat `2097152` (2048 KB) | `691200` (675 KB) |
| `KEY_MAX_INPUT_SIZE`, H.265, ≤ 1080p | flat `2097152` (2048 KB) | `691200` (675 KB) |
| `BUFFER_FLAG_CODEC_CONFIG` on this rig | never set (the chipset allowlist is Rockchip/Allwinner/Telechips, and `codecConfigured` is already true by the first fed frame) | set on any access unit that is **parameter sets and nothing else**, on every device |

That last row is the one real behaviour change this stack makes to a *working* unit, which is why R1
runs long enough to cross at least three keyframe intervals.

## 6. Runs

### R0 — build and unit-test gate, both builds

`build_hur.sh` on each SHA, then `run_unit_tests.sh` on each. Copy each APK out of `apks/` before
building the next one.

- **PASS, build A (`f008e3d1`):** it compiles, and the suite reports **422** tests — `main`'s 312
  plus 110 new: `VideoFragmentAssemblerTest` 21, `CodecConfigScannerTest` 14,
  `ParameterSetInspectorTest` 12, `FragmentedMessageAuditTest` 11, `DecoderCapabilityReportTest` 9,
  `DeviceMemoryProfileTest` 9, `CodecInputSizePolicyTest` 9, `DecoderConfigLadderTest` 9,
  `VideoBackpressurePolicyTest` 8, `VideoFaultInjectorTest` 8. All green.
- **PASS, build B (`becebffa`):** it compiles, and the suite reports **321** tests — `main`'s 312
  plus 9 in `AapReadRecoveryPolicyTest`. All green.
- **FAIL:** stops the round *for that build*; the other one is independent, so carry on with it and
  say which failed. Quote the compiler output in full — this is the first compile of either, and a
  failure here is the round's most useful possible result.

### R1 — clean hardware session, H.264, SURFACE (the point of the round)

Five minutes, screen moving, nothing provoked. This run harvests every diagnostic the later branches
were designed around and is simultaneously the regression sentinel for all three.

- **Record, as numbers:**
  - the full `Stream SPS (H.264): …` line, verbatim. **This is the deliverable.** If it says
    `num_ref_frames=1` with `num_reorder_frames=0`, the SPS-rewriting idea borrowed from Moonlight
    buys this project nothing and a planned dependency is dropped. If it says 4 or more reference
    frames, #219 gains a named, industry-validated lever. Nothing else can answer this.
  - the full `Decoder capability: …` (or `Decoder may not manage this stream: …`) line, verbatim;
  - `max-input-size=` from `Configuring decoder:`, and `requested`/`got` from `Codec input buffer:`;
  - `ACodec: Allocating N buffers of size M` if it exists, else any `CCodec`/`C2` equivalent, else
    "absent" — see §3;
  - `AapRead: largest message body so far:` — every occurrence;
  - whether `AapRead: fragment accounting established for VIDEO` appeared, and its `fragments=` and
    `delta=` values. **This is the gate for R3-R5**; note which other channels also produced one;
  - `Throughput` totals: `rendered`, `fed`, `dropped`, `skipped`, `inputWait`;
  - `dumpsys meminfo com.andrerinas.headunitrevived` at the end of the run (Java Heap alloc, Native
    Heap, Graphics), for R6 to compare against.
- **PASS:** picture renders throughout; `dropped=0`; `Codec initialized:` appears exactly once; **no**
  `reassembly anomalies` line at all; **no** `Stream SPS … could not be parsed`; **no** `Fallback to
  negotiated dimensions`; **no** `Frame larger than the codec input buffer`; **no** `SSL Decrypt:
  produced`; **no** `Magic Garbage`; `max-input-size=675KB`.
- **FAIL, and stop the round if you see either:**
  - `Frame larger than the codec input buffer` — the smaller request left the component with buffers
    too small for a real keyframe. This is B2's designed-in risk and the one thing that would sink it.
  - `SSL Decrypt: produced …` or `Magic Garbage` — the one plaintext buffer per session is being
    overrun or read stale. B2's risky commit is `65ae5e0a` and can be reverted alone.
- **FAIL, but keep going:** any `reassembly anomalies` line on an undisturbed session, or a `rendered`
  fps materially below what round 6's R1 measured on this rig.

### R2 — clean hardware session, H.265

Same as R1, five minutes, `video-codec=H.265`.

- **Record:** the full `Stream SPS (H.265): …` line, the `Decoder capability:` line, `max-input-size=`,
  `Codec input buffer:`, and `Throughput` totals.
- **PASS:** same conditions as R1, and `H.265 SPS parsed: WxH` appears — H.265 dimensions came from
  the stream rather than from negotiation, which is new in `d89e26a2`.
- **INCONCLUSIVE:** the phone negotiated H.264 anyway (see §3). Record what it did negotiate and move
  on; R8 is then also skipped.
- A `Decoder may not manage this stream:` WARN here is **not** a FAIL — it is exactly the evidence
  the line was added to collect, and would be the strongest thing this round could produce for #219.
  Quote it in full.

### R3 — HIDE_START_CODE injection (the B1 positive control)

Gated on R1 producing `fragment accounting established for VIDEO`. Five minutes, `view-mode=0`,
`debug-video-fault-injection=4`, `debug-video-fault-rate=2` — one in every two first fragments is
presented to the reassembler as having no start code at either offset. The bytes are untouched; only
what the reassembler is told about them changes.

This is the exact shape of the silent case: on `main` these frames were assembled headless and fed to
the codec with **nothing logged at all**.

- **Record:** count of `FAULT INJECTED`; count of `AapVideo: First fragment has no start code`; count
  of `requesting keyframe to recover stream`; every `reassembly anomalies` line; `Throughput` totals.
- **PASS:** `headless=` is non-zero on the summary lines and roughly tracks the `FAULT INJECTED`
  count; `first fragment has no start code, requesting keyframe` appears but **no more than once per
  second** (it is throttled); and the picture visibly repairs between faults rather than degrading
  monotonically.
- **`orphan=` will also be non-zero here, and that is correct, not a second fault.** Closing the run
  on a headless first fragment is the fix: the 8s and 10 behind it then arrive with no run open and
  are discarded as orphans instead of being assembled onto nothing. Expect roughly one `headless`
  plus one orphan per remaining fragment, per faulted frame. `truncated=` and `overflow=` should stay
  **0**.
- **INCONCLUSIVE:** fewer than 5 `FAULT INJECTED` lines in five minutes — the stream does not
  fragment often enough here.
- **FAIL:** faults injected but `headless=` stays 0, or the app crashes.

### R4 — DROP_MIDDLE_FRAGMENT injection (demonstrating the blind spot)

Five minutes, `debug-video-fault-injection=2`, `debug-video-fault-rate=3`. Flag 8 is the most frequent
of the three fragment flags inside a run, so a rate of 3 lands more faults per minute than R3's 2.

A dropped middle leaves the run looking **perfectly intact** to the reassembler: a first, some
middles, a last, in order. The frame is assembled with a hole and decoded as whole.

Read §3 first: the injector is downstream of the framing audit, so this run **cannot** make
`DELTA_CHANGED` fire. What it can do is demonstrate the blind spot itself — that the reassembler,
which is what everything before this stack relied on, sees nothing at all — and confirm that a
corrupt access unit reaching the codec does not take the session down.

- **Record:** count of `FAULT INJECTED`; every `reassembly anomalies` line (expected: none, or
  all-zero); `Throughput` totals; any decoder-side reaction —
  `VideoDecoder: dropped a reference frame`, `picture unrepaired`, `sync_stall`, `Codec initialized:`
  appearing a second time.
- **PASS:** faults were injected, **and** the reassembly summary either never appears or reads
  all-zero, **and** the session survives the five minutes without a decoder restart. The visible
  picture is expected to be bad; that is the demonstration.
- **A finding worth the whole round if it happens:** any `AapRead: DELTA_CHANGED on VIDEO` line. It
  could not have come from the injection, so it would mean the reader is skipping fragments on a
  healthy link — quote it in full with the surrounding 20 lines.
- **FAIL:** the app crashes, or the decoder permanently stops producing frames rather than carrying
  on with a corrupt picture.

### R5 — DROP_FIRST_FRAGMENT and DROP_LAST_FRAGMENT (two sub-runs, 3 minutes each)

**R5a**, `debug-video-fault-injection=1`, rate `2`. Dropping a first fragment leaves its 8s and 10
arriving with no run open.
- **PASS:** `orphan=` non-zero on the summary; `AapVideo: Orphaned fragment (Flag 8)` and/or
  `(Flag 10)` at E level; `orphaned fragment, requesting keyframe` (throttled to 1/s). `headless=`
  and `truncated=` stay 0 — this is the one mode that produces orphans *without* a headless first
  fragment, which is what separates it from R3.

**R5b**, `debug-video-fault-injection=3`, rate `2`. Dropping a last fragment leaves the run open
until the next 9 or 11 closes it as a truncation.
- **PASS:** `truncated=` non-zero; `AapVideo: Previous frame was truncated!`; **and**
  `frame truncated, requesting keyframe to recover stream` — that keyframe request is the change,
  `main` logged the truncation and asked for nothing at all. `orphan=` and `headless=` stay 0.
- **FAIL for either:** the summary counter stays 0 while faults were injected.
- No `AapRead:` audit line is expected in either sub-run, for the reason in §3.

### R6 — forced CONSTRAINED memory profile (regression, weak power by design)

Ten minutes, `debug-force-memory-profile=CONSTRAINED`, everything else as R1. Read §3 first: this run
is expected to show little or no memory difference on a 3.8 GB rig, and that is a PASS.

- **Record:** the `memory=` field of `Configuring decoder:` — it must read
  `CONSTRAINED (FORCED) (totalRam=…MB heapLimit=…MB memoryClass=…MB lowRamFlag=…)`, which also proves
  the setting took; `Throughput` totals against R1's; `dumpsys meminfo` at the end against R1's.
- **PASS:** `memory=CONSTRAINED (FORCED)` present, `rendered`/`fed` within noise of R1, `dropped=0`.
- **FAIL:** throughput materially below R1, or drops appear that R1 did not have. That would mean the
  2 MB pool budget is starving the pipeline, and the ceiling needs raising.
- Report the meminfo delta as numbers either way, even if it is zero. It is the only measurement of
  this that exists.

### R7 — GLES with hardware decoding

Five minutes, `view-mode=2`, hardware path. `d89e26a2` rewrote how the renderer chooses between its
two frame sources: a latching `hasYuvFrame` flag became a comparison of per-source sequence numbers.
On the hardware path only the external-texture source ever produces frames, so this run is purely
"did the rewrite break the ordinary case".

- **PASS:** picture renders for the full five minutes; `Throughput` comparable to R1's; **no**
  `first YUV420 frame queued`; **no** `direct YUV upload missed its 50ms deadline`; no black or
  frozen picture at any point.
- **FAIL:** black or frozen picture, or throughput materially below R1's SURFACE numbers.
- Then do one `headunit://exit` and relaunch, and confirm the second session renders too. The
  `release()` ordering fix — the Surface is now released inside the same posted callback that tells
  the decoder it is going, instead of on the next two lines after the post — is exercised by the
  teardown. Note §7a: a `KEYCODE_HOME` press does **not** tear the surface down on this unit, so use
  the deep link, and verify the teardown happened before reading anything into the result.

### R8 — GLES with the bundled FFmpeg HEVC decoder (the YUV path)

Five minutes, `view-mode=2`, `video-codec=H.265`, `force-software-decoding=true`,
`software-video-decoder=1`. Skip if R2 was INCONCLUSIVE.

This is the path whose field writes were unsynchronised — seven of them, written bare while two other
methods read exactly those fields under the renderer's monitor — and whose direct upload blocked the
decoder thread for up to 50 ms per frame and then did the staged copy anyway.

- **PASS:** `Configuring bundled FFmpeg HEVC decoder for WxH` appears; `first YUV420 frame queued`
  appears; picture renders for the full five minutes; and **if** `direct YUV upload missed its 50ms
  deadline` appears, it appears **exactly once** and the picture keeps rendering afterwards on the
  staged path.
- **FAIL:** that deadline line appearing more than once (the latch is not latching), or a torn /
  wrongly-scaled / frozen picture.
- Report `Throughput` and the count of that deadline line either way.

### R9 — the configure ladder and low-latency keys (3 minutes, log harvest)

`debug-video-low-latency=true`, otherwise R1's settings.

- **Record:** the `optionalKeys=` field of every `Configuring decoder:` line, in order, and any
  `Decoder rejected optionalKeys=` / `Decoder accepted the format only with optionalKeys=`.
- **Expected on this rig:** Android 14, so if the component advertises `FEATURE_LowLatency` you should
  see `optionalKeys=low-latency [low-latency]` and, if it configures, nothing else. If it does not
  advertise the feature, the ladder falls back to a vendor key **only** for MediaTek / Amlogic /
  Qualcomm / Exynos / HiSilicon name patterns — a UNISOC component matches none of them, so the only
  rung is `optionalKeys=none` and the run's answer is "this rig cannot exercise the ladder".
  That is a legitimate **INCONCLUSIVE** for the ladder and a PASS for the setting being inert.
- **PASS:** the session configures and renders, whichever rung it lands on, and `featureLowLatency=`
  in R1's capability line agrees with which rung was offered.
- **FAIL:** the session fails to configure at all — the ladder's last rung is byte-for-byte the format
  `main` builds, so a total failure would mean the ladder itself is broken.

### R10 — the backpressure verdict, provoked (build A)

Eight minutes, R1's settings, with round 6's CPU-burst lever running throughout. That lever is the
one method measured to produce drops on this rig; this run exists to check that when drops *do*
happen under codec pressure, the app now says so instead of leaving it to be inferred from a column
of numbers.

```bash
N=$(adb shell nproc | tr -d '\r')
for i in $(seq 40); do
  for c in $(seq $((N*2))); do adb shell "timeout 0.4 sh -c 'while :; do :; done'" & done
  wait; sleep 10
done
```

If that lever produces no drops at all, fall back to round 6's thermal-throttle lever
(`cmd thermalservice override-status 3`, released with `0`), and **put it back to 0 before leaving**.

- **Record, as numbers:** every `Throughput` line's `dropped=` and `inputWait=` together with the
  window length, so the count in the verdict can be checked by hand; the verbatim
  `the codec is the bottleneck` line if it appears, including the `It claimed it could` /
  `It said it might not` suffix.
- **PASS:** the line appears at most once, and the `N windows` it reports equals the number of
  throughput lines that had **both** `dropped>0` **and** `inputWait` at or above 10 % of the window
  length. That arithmetic is the whole check — the threshold is 10 % of `elapsed`, not a fixed
  millisecond count.
- **INCONCLUSIVE:** neither lever produces a drop. Then this rig cannot manufacture codec pressure
  today, exactly as round 6 found on its own R2; say so and move on.
- **FAIL:** the line appears during **R1** — an undisturbed five minutes with no lever. That would
  mean the threshold is too low for healthy hardware and the number needs raising. Check R1's capture
  for it explicitly before starting R10.

### R11 — the capability line at negotiation time (build A, no new run)

Read out of R1's and R2's captures. It is emitted once per connection, from
`ServiceDiscoveryResponse`, before the phone is told which codec we want.

- **Record verbatim**, for both R1 (H.264) and R2 (H.265): the
  `[ServiceDiscovery] Negotiating a profile …` line, whichever of the three forms it took.
- **PASS:** the line is present in both captures, its `target=WxH@F` matches the negotiated
  resolution the neighbouring `[ServiceDiscovery] NegotiatedResolution is:` line reports, and its
  `codec=` names a component that exists on this device.
- On this rig it should be the **claims to carry** form at INFO. A WARN here is a finding about the
  rig rather than about the branch — record it and carry on, do not treat it as a failure.
- **FAIL:** no line at all in either capture, or a `target=` that disagrees with the negotiated
  resolution.

### R12 — the transport fix does not disconnect a healthy link (build B)

Install `becebffa`. One 10-minute undisturbed session, screen moving, no levers, no fault injection.
This build changes only what happens when a socket read comes up short, which should never happen
here — so the pass is an absence.

- **Record:** count of each `Disconnecting to resync.` variant (expect **0**); count of
  `AapRead: WiFi read timeout` (expect 0 on a healthy link, and it is *not* one of the new paths);
  `Throughput` totals; whether the session survived the full ten minutes; the discard-rule check.
- **PASS:** zero `Disconnecting to resync.`, one session start, no unexpected reconnect.
- **FAIL:** any `Disconnecting to resync.` on an undisturbed link. That is the policy firing where
  nothing was actually lost, and it would make the fix worse than the bug. Attach the full capture.
- **Not testable here, and the brief says so rather than asking you to try:** the desync itself needs
  a link that stalls partway through a message and then *recovers*. Nothing on this rig can
  manufacture that. Its coverage is the 9 JVM tests in R0.

## 7. Do not re-run

- **Feed queue depth and the dropped-reference-frame recovery chain.** Settled in
  `video-dropped-frame-keyframe` rounds 5 and 6 and shipped in PR #826. Nothing in this stack changes
  `VideoFeedQueuePolicy`; the `Feed thread started (queue holds 30 frames …)` line should read exactly
  as it did in round 6, and a different number would be a bug in this stack, not a result to chase.
- **The black-screen-after-background work.** Closed at `video-black-after-background` round 8. Do not
  re-measure return-to-picture timing here.
- **Natural keyframe cadence.** Measured at ~68-69 s across rounds 4 and 5. Quote it if you need it;
  do not spend a run remeasuring it.
- **Whether a Home press tears down the projection surface.** It does not, on this unit — twelve
  scripted cycles proved it. R7 uses `headunit://exit` for that reason.
- **The CPU-burst lever's own effect on drop counts.** Round 6 already measured it against two builds.
  R10 uses it only to reach the new log line, not to compare drop rates with anything.

## 8. Report back

Six things decide what happens next, in this order:

1. **Do both builds compile, and do all their tests pass?** (R0 — 422 on build A, 321 on build B.)
   Nothing else matters if not.
2. **The verbatim `Stream SPS (H.264)` line.** (R1.) `num_ref_frames` and `num_reorder_frames` decide
   outright whether an SPS-rewrite path and a new bitstream dependency enter this project. No other
   evidence can settle it, and no reporter's log carries it.
3. **`max-input-size=675KB` in the configure line, `requested`/`got` in the buffer line, and the
   absence of `Frame larger than the codec input buffer`.** (R1.) That triple is the entire case that
   B2 is safe to ship, and the second-listed line is what tells us whether the component honoured the
   request or quietly did its own thing.
4. **The three injection signatures, as counts.** (R3, R4, R5.) `headless`+`orphan` non-zero for
   HIDE_START_CODE, `truncated` non-zero for DROP_LAST, `orphan` alone for DROP_FIRST, and **all
   counters at zero** for DROP_MIDDLE. The fourth is as important as the other three: it is the blind
   spot the framing audit exists for, measured rather than argued. Every one of these produced no log
   line whatsoever on `main`.
5. **The `Decoder capability:` / `Decoder may not manage this stream:` line for H.265** (R2) and
   the `[ServiceDiscovery] Negotiating a profile …` line for both codecs (R11). A WARN in either on a
   unit that renders fine is still worth having: it calibrates what the same line will mean when a
   #219 reporter finally posts one. The reporter these were built for negotiates 2560x1440 HEVC that
   nothing ever asked a decoder about.
6. **Zero `Disconnecting to resync.` on build B** (R12), and — separately — that
   `the codec is the bottleneck` did **not** appear in R1's undisturbed capture (R10's FAIL
   condition). Both are absences, and both are what says the two new rules are not trigger-happy.

Everything else is regression cover. If a run is INCONCLUSIVE for a reason §3 already predicted, say
so in one line and move on — those are answered in advance, not open questions.
