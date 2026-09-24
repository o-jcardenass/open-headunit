# AAC audio, round 1: does the AAC sink path work, on both focus modes

Rig round. Build `fix/aac-default-on-narrow-band` from `fork` (tip named in the README row; confirm
with the build-SHA line in the log footer). Everything in `TESTING-TEMPLATE.md` applies: clean-run
protocol, `settings.xml` writes with the app stopped, capture with `stdbuf -oL`, fixed report format.

## 1. Why this round exists

"Use AAC Audio" has shipped as an experimental toggle since January and no log we hold has ever
had it on: every audio sink in every capture is `Media Sink Setup Request: 1` (PCM). The branch
now turns AAC on by default for a wireless session over a radio with no 5 GHz band, because on
such a link uncompressed music is the largest stream, and it fixes four defects a code review of
the decoder found (compressed bytes played as PCM after a failed decoder init, a codec error never
recovered from, the output format never checked, all input timestamps zero). None of that has run
on hardware. This round runs the AAC path on the rig with the setting forced on, on any band,
because both rig units report a 5 GHz band and the default itself cannot trigger here.

What the round decides: whether the word "(Experimental)" comes off the setting's description, and
whether the default stays.

## 2. Settings

Both units, before every run, app stopped (§1 template):

| Key | Value | Why |
|---|---|---|
| `use-aac-audio` | `true` (boolean) | forces the AAC announcement on every band |
| `enable-audio-sink` | `true` | all three sinks announced |
| `static-audio-focus` | per run | `false` = one AudioTrack per sink; `true` = the shared mixer |
| `playback-focus-mode` | default (absent) | |
| `audio-queue-capacity` | default (absent) | |

Verify with `cat shared_prefs/settings.xml` before launch, every run.

## 3. Runs

Each run: connect in the usual Native mode pairing, then 3 minutes with music playing on the
phone (start it with `KEYCODE_MEDIA_PLAY` through the head unit, §3 template), one navigation
prompt (start a route on the phone before the run, so guidance speaks during it), and one
notification (send a message to the phone from another device). Then a user exit deep link, then
the capture ends. Keep the exported log with the capture.

| Run | Head unit / phone | `static-audio-focus` | What it exercises |
|---|---|---|---|
| R1 | MT50 / POCO | `false` | one AAC decoder per sink, three AudioTracks |
| R2 | MT50 / POCO | `true` | the mixer: 16 kHz mono guidance decoded then resampled into the 48 kHz mix |
| R3 | POCO / Moto | `false` | a second decoder vendor |
| R4 | MT50 / POCO | `false` | R1 again with `use-aac-audio` absent: the PCM baseline for the byte comparison |

There is no lever to inject a decoder error; the recovery path is JVM-tested only and the round
does not pretend otherwise.

## 4. What to read

Per run, all from the exported log:

```bash
grep -E "Media Sink Setup Request: [0-9]+ on channel AUDIO" log.txt        # expect 2 on all three in R1-R3, 1 in R4
grep -E "AudioDecoder.start:.*isAac=.*source=" log.txt                     # isAac=true source=setup in R1-R3
grep -E "AAC Decoder started|Failed to init AAC|AAC Codec Error|rebuilding the AAC decoder|giving up on this sink|no working AAC decoder" log.txt
grep -c "AAC Input Buffer timeout" log.txt                                  # expect 0
grep -E "AAC decoder output is|AAC Output Format Changed" log.txt           # the wrong-speed warning, expect none
grep -E "inbound rate over" log.txt                                         # audio= kB/s, compare R1 with R4
grep -c "disabled due to previous underrun" log.txt                         # expect 0
grep -oE "underrunframes=[0-9]+" log.txt                                    # API 28 ROMs; expect 0 or absent
grep -E "AudioTrackWrapper thread finished" log.txt                         # the drop count, if any, is on this line
grep -E "RECV: AUDIO[0-9]* .*type: 1 " log.txt | head                       # codec-config messages on an audio channel: count them
```

And listen. A run passes only if music, the guidance prompt and the notification tone were all
heard clearly with no crackle, gap or pitch shift; say what was heard in the Setup notes, in the
observer's words.

## 5. PASS and FAIL

Per run:

- **PASS**: every audio sink set up with type 2 (R1 to R3); `isAac=true source=setup` on every
  `AudioDecoder.start`; `AAC Decoder started for 48000 Hz, 2 channels` and twice `for 16000 Hz, 1
  channels`; zero input-buffer timeouts, zero codec errors, zero rebuilds, zero wrong-speed lines;
  zero underruns by either instrument; all three streams heard clean; the wrapper's finished line
  carries no drop count.
- **FAIL**: anything else, with the line that failed it quoted.
- R4 is the baseline and has no verdict; report its `inbound rate over … audio=` figures beside
  R1's so the saving is a number.

Anything in the log the brief did not ask about goes under its own heading, as usual. If the phone
sent any type-1 message on an audio channel, quote the first one with its size; that is a finding
whatever the verdict.
