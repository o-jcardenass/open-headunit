# Gemini research on low-RAM optimization & decoder artifacts — fact-checked against `main`

Not a hardware round; PC-only source comparison against a Gemini research transcript the user ran
independently (`evidence/gemini-research-video-optimization.pdf`, the raw export, committed
alongside this file). Same discipline as `headunit-reloaded-decompile-findings.md`: check every
concrete, checkable claim against `main` before treating it as new information, rather than
committing a chatbot transcript as if its suggestions were verified.

## What's in the PDF

A five-exchange Gemini chat: (1) general low-RAM (1 GB) optimization ideas for open-headunit, (2)
code examples for `MediaCodec`/`SurfaceView` zero-copy handling, (3) code examples for
`AudioTrack`/PCM ring buffers, (4) a comparison against Moonlight-Android's decoder architecture
plus four proposed fixes for reported decoding artifacts, (5) a follow-up on KitKat (API 19)
compatibility and whether AAP supports requesting an IDR/keyframe. Gemini is working from the
GitHub wiki and general Android knowledge, not this project's actual current source — most of its
"fixes" describe mechanisms this project already has, several already in a more careful,
hardware-validated form than what's proposed.

## Claim-by-claim against `main`

### Already implemented — and in the one case that matters most, more carefully than proposed

- **"Force an IDR by cycling AAP Video Focus"** — Gemini's own headline finding, from exploring
  whether AAP supports a keyframe request (it correctly concludes it doesn't, and proposes
  toggling `VIDEO_FOCUS_UNFOCUSED` → `VIDEO_FOCUS_PROJECTED` instead). This project already does
  exactly this, and has for a while: `AapTransport.kt`'s `triggerFocusCycleRecovery()` sends an
  unsolicited `VideoFocusEvent(gain = true)` on frame corruption, escalating through
  `KeyframeCycleEscalationPolicy` (nudge → cycle focus, capped at `MAX_CYCLES_PER_SESSION`, its own
  lockout timing) if the picture stays broken past a threshold — not the flat "toggle on every
  dropped frame with a 1 s debounce" Gemini's code sample shows. This has already been hardware
  round-tested on the transfer branch: a plain gain-only nudge was found **inert** on its own; the
  bounded release/regain escalation is what actually repairs the picture, confirmed hardware-safe
  for a single cycle with no #755 regression (see prior round results on this branch). Gemini's
  literal proposed code (unconditional cycle, fixed 1 s lockout, no session budget) would be a
  regression if applied as written, not an improvement.
- **Explicit SPS/PPS via `csd-0`/`csd-1`** ("Fix 1"): already done —
  `VideoDecoder.kt:997,1009-1010`.
- **Avoiding `OMX.google.*`/`c2.android.*` software decoders** ("Fix 3"): already done —
  `VideoDecoder.kt:144,1478`, a `MediaCodecList` scan filters both prefixes out when picking a
  hardware decoder.
- **Pre-Lollipop (KitKat/API 19) `getInputBuffers()` legacy-array fallback**: already handled —
  `VideoDecoder.kt:1185-1186`, plus four more `SDK_INT < LOLLIPOP` gates in the same file. Worth
  taking seriously since minSdk really is 16 on the buildable `github` flavor (CLAUDE.md), so
  Gemini's KitKat warning was a legitimate thing to check — it's just already addressed.
- **"Standard Java API only, no NDK"**: half right. The hardware `MediaCodec` path is Java-API
  only, as Gemini says, but the blanket claim is wrong — `app/src/main/cpp/ffmpeg_hevc_decoder.cpp`
  is a bundled native FFmpeg software HEVC decoder path (`SoftwareVideoDecoder.BUNDLED_FFMPEG`)
  used when hardware HEVC isn't available.
- **View mode choice (SurfaceView vs. TextureView vs. GLES)**: already a first-class, user-facing
  setting (`Settings.ViewMode`: `SURFACE`/`TEXTURE`/`GLES`), not something to newly default.

### Not found — genuinely open, unverified either way, not investigated further this pass

- **`MediaFormat.KEY_LOW_LATENCY`**: no hit anywhere in `VideoDecoder.kt`. Given everything else in
  that file already gates flags per-SDK-version, adding this behind an `SDK_INT >= 30` check would
  fit the existing pattern — but this pass didn't test whether it helps, and Gemini's own caveat
  (unknown vendor keys can throw on legacy Rockchip/Allwinner even when the *Android version* gate
  passes, since the failure is vendor-specific, not version-specific) is worth taking seriously
  before adding it.
- **`AudioTrack.PERFORMANCE_MODE_LOW_LATENCY` / `AudioAttributes.FLAG_LOW_LATENCY`**: not found in
  `AudioTrackWrapper.kt`. It does already compute `AudioTrack.getMinBufferSize()` and size the
  buffer from a tunable `audioLatencyMultiplier` — a different, arguably more deliberately-tunable
  approach than hardcoding the low-latency performance mode. Not dug into further here; no
  audio-latency complaint motivated this pass.

### Actively wrong, and self-corrected within the same transcript

- **`mediaCodec.flush()` on every frame-loss** ("Fix 2"): explicitly not what this project does,
  and Gemini reverses its own advice on this the moment it's told AAP can't request an explicit
  IDR — flushing without a guaranteed incoming IDR turns transient corruption into a permanent
  freeze. `markCorruptAndRequestRecovery()` in `AapVideo.kt` already does the corrected thing:
  drop only the current frame's data (never flush the codec) and ask for a keyframe via the
  focus-cycle mechanism above.

## Net

Read the attached transcript as background, not a task list. Nearly every concrete fix it proposes
already exists in `main`, generally in a form that has already survived hardware validation on this
branch (the focus-cycle keyframe recovery, specifically, is more conservative than what's proposed
here, for a documented reason). The two open items (`KEY_LOW_LATENCY`, `AudioTrack` low-latency
performance mode) are additive, plausibly low-risk, and would fit the file's existing version-gating
pattern — but neither was tested as part of this pass. They're candidates for a future round, not a
finding.
