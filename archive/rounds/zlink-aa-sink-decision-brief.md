# ZLink teardown, part three: the sink decision, read from the code

PC-only. No build, no APK, no head unit, no phone. Same extractions as part two, same two builds of
`libzjL10001.so`. The clean-run protocol and PASS/FAIL vocabulary in `TESTING-TEMPLATE.md` do not
apply; report facts. House rule 5 (inventory `hur-wifi-test-scripts/` first, leave scripts behind)
and the binaries rule (vendor binaries stay on the rig; commit text only) still apply.

## 1. Why this round exists

Part two settled everything it could from `strings` and `nm`. Its results file names the one
number that decides the next step and could not answer it: which codec the vendor's Android Auto
audio sink actually announces. The value names are there (`MEDIA_CODEC_AUDIO_PCM`,
`MEDIA_CODEC_AUDIO_AAC_LC`, `MEDIA_CODEC_AUDIO_AAC_LC_ADTS`), the function that decides is there
(`get_is_AA_AAC_audiotype`, called from `send_SinkSetup`), and nothing else is, because two tools
were missing on the rig. Both are now on this branch under `tools/zbt/`. This round is the
descriptor decode and the disassembly, nothing else.

Read `zlink-media-usb-and-carlink-response.md` first; it says which questions are closed so they
are not re-derived here.

## 2. Tools, and they are not optional this time

Two previous results files reported `capstone` as absent and skipped every disassembly step. Do not
skip it. Bootstrap one of these before anything else and put the outcome in the first line of the
results file:

```bash
python3 -m pip install --user capstone && python3 -c 'import capstone; print(capstone.__version__)'
# or, if pip cannot reach the network but uv can:
curl -LsSf https://astral.sh/uv/install.sh | sh && uvx --with capstone python3 -c 'import capstone'
```

If neither reaches the network, say so in the first line of the results file and stop; there is no
substitute for the disassembly, and a `strings`-only repeat of part two is not wanted.

The two scripts, copied from this side and known to run here:

```bash
cd hur-wifi-test-scripts
cp <this branch>/tools/zbt/extract_zbt_proto.py <this branch>/tools/zbt/xref_gocsdk.py .
python3 extract_zbt_proto.py --help
uvx --with capstone python3 xref_gocsdk.py --help   # or plain python3 once capstone is installed
```

`extract_zbt_proto.py` walks the protobuf-c descriptor tables an ELF carries and writes a `.proto`
with field names, numbers, labels and enum values. It was written for `libzbt-main.so` and is not
specific to it. `xref_gocsdk.py` finds every code reference to a string or an address and
disassembles a window around each, annotating PC-relative loads with the text they point at.

## 3. Inventory

```bash
cd hur-wifi-test-scripts/extracted
sha256sum */apktool_out/lib/armeabi-v7a/libzjL10001.so */jadx_out/resources/lib/armeabi-v7a/libzjL10001.so 2>/dev/null
```

Expect `d67893fb…` (A, `_600102`) and `abd80569…` (B, `_600106_jg`). Run every step against both
and say which build each finding is from.

## 4. Q1: the descriptors

```bash
python3 extract_zbt_proto.py <A>/libzjL10001.so -o zlink-aa-recovered-600102.proto
python3 extract_zbt_proto.py <B>/libzjL10001.so -o zlink-aa-recovered-600106.proto
diff zlink-aa-recovered-600102.proto zlink-aa-recovered-600106.proto
```

Commit both `.proto` files to this branch (text). Report:

- the integer values of every `SinkMediaCodecType` and `MediaCodecType` entry;
- `MediaSinkService`: field numbers and labels of `available_type`, `audio_type`,
  `audio_configs`, `video_configs`, and whether `available_type` has a default;
- `AudioConfiguration` and `VideoConfiguration` in full;
- `SinkConfig` in full (part two saw `status, max_unacked, configuration_indices`);
- the diff between the builds, or that there is none.

If the script finds zero messages in `libzjL10001.so`, say so with the exact output and move on to
Q2; the enum names from part two are then the record.

## 5. Q2: the AAC decision

```bash
python3 xref_gocsdk.py <A>/libzjL10001.so --string "get_is_AA_AAC_audiotype" --before 40 --after 120
python3 xref_gocsdk.py <A>/libzjL10001.so --string "send_SinkSetup" --before 20 --after 200
```

`--string` on a symbol name may resolve nothing if the name is only in `.dynsym`; then take the
address from `nm -D --defined-only <A>/libzjL10001.so | grep get_is_AA_AAC_audiotype` and use
`--addr`. Quote the windows. What we want:

1. What `get_is_AA_AAC_audiotype` reads: a `getprop` key (quote it), a field of the init message,
   a global set by another function (name it and find its writer), or a constant.
2. What `send_SinkSetup` writes into the codec field on each branch, as the integer from Q1.
3. Whether the same value goes on every sink or the media sink alone. Open Headunit uses one value
   for all three sinks (media, speech, system); if the vendor sends AAC on media and PCM on the two
   16 kHz mono sinks, that is the shape to copy.

The one rule from the previous ZLink brief, restated because it cost a round: **a register is often
set several instructions above the load that reads it; disassemble the store, not just the load.**

## 6. Q3: `max_unacked`

```bash
python3 xref_gocsdk.py <A>/libzjL10001.so --string "zj__aa__sink_config__pack" --before 60 --after 20
```

Report the literal stored into `max_unacked` per channel, if it differs by channel. Open Headunit
answers 12 on video and 30 on each audio sink.

## 7. Q4: the offered video list, and the band

```bash
python3 xref_gocsdk.py <A>/libzjL10001.so --string "zj__aa__media_sink_service__pack" --before 200 --after 20
python3 xref_gocsdk.py <A>/libzjL10001.so --string "selected_wifi_channel_type" --before 30 --after 60
```

Report which `VideoCodecResolutionType` / `VideoFrameRateType` pairs are pushed into
`video_configs`, in order, and whether any reader of `selected_wifi_channel_type` exists beyond the
log line at `WifiVersionResponse_handle`. Part two found no branch string; this is the step that
says whether there is no branch.

## 8. Also commit

The 105-name `zj.AA.*` list part two left in the rig's scratchpad, as `zlink-aa-namespace.txt`
(one name per line, sorted). Regenerate if the scratchpad is gone:

```bash
strings -a -n 4 <A>/libzjL10001.so | grep -E '^zj\.AA' | sort -u > zlink-aa-namespace.txt
```

## 9. Reporting back

`zlink-aa-sink-decision-results.md`, part-two shape: Inventory, Q1 to Q4 with the evidence windows
quoted, Anything the brief did not ask about. The three numbers that decide the next step: the
codec integer `send_SinkSetup` writes on its default branch, the `max_unacked` literal, and the
offered video list. Absences stay findings; a step that could not run says why in one line.
