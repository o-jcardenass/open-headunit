# t230-native-aa-bringup — round 4 results

**Candidate:** `fix/audio-sink-and-wireless-bring-up` @ 5bf505d7a (same build as rounds 2-3, not
rebuilt this round)
**Baseline:** round 3's result — `t230-native-aa-bringup-round3-results.md`
**APK md5:** de16ab3541b35257130de8c07dde67b6 (unchanged)
**Unit:** Samsung SM-T230 (head unit) + POCO X3 NFC (phone), same pair as rounds 1-3
**Date:** 2026-09-15/16

## Setup notes

- **Not a scripted round.** The operator changed the resolution and frame-rate settings through the
  app's own Settings UI directly on the device and reconnected by hand, then reported success. This
  session only did read-only verification afterward (`adb shell run-as ... cat settings.xml`,
  `adb logcat -d` against the already-buffered log, `dumpsys activity services`) — no settings were
  written, nothing was force-stopped, and the live session was not disturbed. House rule 2 ("script
  it, don't drive the UI") is why this wasn't done by adb instead: the operator had already done it
  by the time this was picked up, and re-doing it by script to re-prove the same point would have
  cost the operator's already-working session for no benefit.
- Operator's own words: "I was able to connect, I had to lower this settings 720p@30 fps with AAC."
  Read-back of the device's settings and of the successful session's own negotiation log shows the
  resolution and fps change is real and is what the session negotiated (see below), but
  **`use-aac-audio` reads `false`** on the device right now, and the successful session's own audio
  log lines all show `isAac=false` throughout (PCM, not AAC) — so the audio codec did not actually
  change from what rounds 1-3 already had; crediting the fix to "resolution + fps" rather than "+ AAC"
  matches what the device actually negotiated. Worth mentioning back to the operator in case AAC was
  toggled and reverted, or was never actually applied through the UI.
- Battery still not checked/charged this round (last read 9% in round 3); not re-read here since it
  would have required touching the live session's device to query, and the throughput numbers below
  make the point regardless of battery state.

## R1 — Native AA at 1280x720 / 30fps (operator-driven), confirms round 3's bandwidth hypothesis

**PASS.**

- Settings changed (by the operator, via the in-app Settings UI): `resolutionId` 3 → **2** (1080p →
  720p), `fps-limit` 60 → **30**. `use-aac-audio` unchanged at `false`.
- Decisive log lines:
  ```
  12:00:51.151 [RES_CAP] resolutionId=2 ... chosen=_1280x720 capped=_1280x720
  12:00:51.472 [ServiceDiscovery] NegotiatedResolution is: 1280x720
  12:00:55.716 VideoDecoder.applyStreamDimensions | H.264 SPS parsed: 1280x720 (negotiated 1280x720)
  12:00:55.736 VideoDecoder.findBestCodec | selected=OMX.MARVELL.VIDEO.HW.CODA7542DECODER
  12:00:55.756 I/OMXClient: Using client-side OMX mux.
  12:00:55.816 VideoDecoder.start | Codec initialized: OMX.MARVELL.VIDEO.HW.CODA7542DECODER
  ```
  `OMXClient: Using client-side OMX mux` — the landmark that **never once appeared** in rounds 1-3 —
  fires here on the first attempt at 720p/30fps. This is the first time this device has rendered any
  video at all in this thread.
- Measured over the following ~110s and still running when checked (same PID, `AapService` and
  `GpsLocationService` both still alive): **27 consecutive `VideoDecoder.logThroughput` samples, all
  `dropped=0, skipped=0, concealed=0`**, steady at 29-30fps, `decodeLatency` 46-55ms (p95 72-107ms).
  Audio: `AudioTrackWrapper.sampleHealth` for AUDIO/AUDIO1/AUDIO2 all show `underruns=0,
  silentCycles=0, rebanks=0, dropped=0` across three consecutive 30s windows.
- Measured sustained bitrate (`AapTransport: inbound rate`), four consecutive 30s windows:
  `video=113kB/s audio=129kB/s`, `video=48kB/s audio=187kB/s`, `video=291kB/s audio=187kB/s`,
  `video=37kB/s audio=187kB/s` — i.e. roughly **1-3 Mbit/s combined**, varying with on-screen content
  complexity, comfortably inside whatever throughput ceiling was breaking the link at 1080p/60fps in
  rounds 2-3.

**This closes round 3's open question in the direction its own evidence already pointed.** Round 3
measured the phone's own WiFi throughput declining from 47025 to 9405 kbps across a run that kept
failing at the 1920x1080 negotiated resolution; round 4 shows that a resolution/fps combination whose
own measured bitrate never exceeds a few hundred kB/s holds the link with zero drops for as long as
it's been watched. The mechanism is very likely a **link-throughput ceiling on this tablet's WiFi
chip** (a 2014 budget Samsung Tab 4 SoC/radio) that the default 1080p/60fps target exceeds, not a
protocol bug, a group-lifetime bug, or (per round 3's frequency reading) a band/channel issue. It
does not rule out that the ceiling itself is partly power-state-related (battery was 9%
un-controlled through all four rounds) — only a charged re-test at 1080p/60 would separate "this
tablet's radio caps out around a few Mbit/s regardless" from "it caps out lower when the battery is
low."

## Anything the brief did not ask about

- This is a workable, real outcome for anyone bringing up Native AA on similarly low-end/old
  hardware: **cap resolution and fps rather than chase protocol-level fixes** when a Native AA link
  completes the handshake repeatedly but never stabilizes. Worth a line in whatever eventually
  documents this device, or a generalized note if other low-end units show the same GAL-socket/EPIPE
  signature from round 2-3.
- Not evaluated this round: whether 1080p/60 now also holds if retried (to see whether 720p/30 was
  necessary or merely sufficient — e.g. 1080p/30 or 720p/60 might also work), and whether the
  connection remains stable over a longer session (this round only observed ~2 minutes). Both are
  natural next steps if this thread continues, but are not blocking — the operator already has a
  working configuration.
