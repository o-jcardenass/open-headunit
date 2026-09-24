# ZLink teardown, part two: its media path, its USB path, and CarLink

Not a hardware round. No build, no APK install, no head unit, no phone. This is static analysis of
the ZLink extractions **already on the rig PC** at `hur-wifi-test-scripts/extracted`, the same
three that `zlink-wpp-channel-brief.md` used. Everything runs on the PC.

The clean-run protocol, capture rules and PASS/FAIL vocabulary in `TESTING-TEMPLATE.md` do not
apply. Report findings as facts. Two house rules still apply: §5, inventory `hur-wifi-test-scripts/`
first and leave any script you write behind in it; and the binaries rule from the last ZLink brief,
**vendor binaries stay on the rig**, commit text only (symbol lists, `strings` output, checksums,
recovered descriptors, notes). Never commit a `.so`, `.apk`, `.zip` or `.dex`.

## 1. Why this round exists

Three reporters say the vendor app does something on their units that ours does not, and the
answers are in a library only the rig holds.

- **Music stutters on 2.4 GHz-only head units.** On an MT8163 unit with the head-unit hotspot on
  2.4 GHz and Bluetooth connected to the phone, our logs show the link delivering 169 to 177 kB/s
  of PCM music against the 192 kB/s it needs, the phone throttling video to protect it, and the
  audio track underrunning 3.7 s in a 93 s session. The reporter says the vendor app does not
  stutter on that unit with Bluetooth connected. On a Spreadtrum sp7731e unit over WiFi Direct the
  head unit's own WiFi scans every 6 to 22 s and the phone stops the video 82% of the time. On every
  vendor-app log we hold the app puts its access point on 5 GHz channel 36, which the MT8163 cannot
  do, so on that unit the difference has to be what it sends over the link: the audio codec it
  announces, the video profile it asks for on a 2.4 GHz-only radio, and what it does with the
  Bluetooth link once the WiFi session is up. Full read: the link-not-audio analysis of 2026-09-10 in the
  handoff folder.
- **A wireless CarPlay/Android Auto dongle works on the vendor app over USB and not on ours.** The
  adapter is an onn `PIA3-1007-SIL`. On our app it stalls AOAP request 51 (`ACC_REQ_GET_PROTOCOL`)
  on every attempt, 68 of 68, on both its USB identities and on two hosts. It alternates two
  identities every 20 to 80 s: `05AC:12A8`, an Apple iPhone clone whose config 4 is `Apple USB
  Ethernet` (the CarPlay personality), and `0525:A4A7`, CDC-ACM control plus CDC-Data with the
  product string `liaoyuan A2A`. It never presents `18D1:2D0x` or an `FF/FF/00` interface. The
  reporter says the vendor app drives it over USB and it is stuck at 30 fps there. So the question
  is which of those two personalities the vendor app talks to, and how.
- **CarLink.** `libzbt-main.so` exports a `CarLink` family beside HiCar (the last round's symbol
  table). We do not know what the vendor app means by it, which transport it rides, or whether it
  shares anything with the Android Auto session. A different vendor's `com.syu.carlink` is an
  unrelated product, so do not conflate the two.

## 2. What is different about this round

- Nothing touches a device. No verdicts; report facts, with the string and its context.
- The previous round settled the WPP byte channel and the `zj.AA.*` namespace (see §9). Do not
  re-derive those.
- Every grep below was run once against the vendor log and the deobfuscated Java in the handoff
  folder so its syntax is known good; if a family matches nothing in either build, say so with the
  command that produced the empty result. An absence is a finding here.
- `strings` output has no code context. Where a hit needs its neighbours, use `grep -n -B3 -A3` on
  the `strings` dump, and quote the window, not the line.

## 3. Inventory first

```bash
cd hur-wifi-test-scripts/extracted
ls -la
find . -maxdepth 5 -iname 'libzjL10001.so' -o -maxdepth 5 -iname 'libzbt*.so' -o -maxdepth 5 -iname 'libusbmuxd.so' | xargs -r sha256sum
find . -maxdepth 5 -iname 'AndroidManifest.xml' -o -maxdepth 5 -ipath '*res/xml/*' | head -40
find . -maxdepth 5 -iname '*.strings.txt' | head
```

Expected from the last round: two builds of `libzjL10001.so` (`_600102`, sha256 `d67893fb...`;
`_600106_jg`, sha256 `abd80569...`), `libzbt-main.so`, `libzbt_core.so`, `libusbmuxd.so`, and under
`_600102` an `apktool_out/` with the manifest and `res/xml/`. A `native_strings/` directory with
`libzjL10001.so.strings.txt` may already exist from an earlier extraction; if so use it, otherwise
make one per build:

```bash
strings -a -n 6 libzjL10001.so > libzjL10001.strings.txt
```

Run every family below against **both** builds and say which build each hit is from.

## 4. Q1: WiFi video and audio, and the Bluetooth link after the handoff

The question that matters most. Four sub-families.

**4a. Audio codec announced and decoded.**

```bash
grep -n -i -E 'aac|mp4a|MEDIA_CODEC_AUDIO|AudioConfig|sample_rate|samplerate|48000|16000|number_of_bits|AudioStream|AudioTrack|apm_player|pcm' libzjL10001.strings.txt
strings -a -n 4 libzjL10001.so | grep -E '^zj\.AA' | grep -i -E 'audio|media|codec' | sort -u
```

Then recover the protobuf-c descriptors for every `zj.AA.*` message containing `Audio`, `Media` or
`Codec`. The recipe is `extract_zbt_proto.py` in the handoff folder's `zbt/` directory (it reads the
embedded `ProtobufCMessageDescriptor` tables out of an ELF); if it is not on the rig, say so and
report the `strings` hits only. What we want is the enum next to the audio sink configuration: the
value the app announces for the media sink, and whether `AAC` appears as a selectable value or a
fixed one.

**4b. Video profile, and whether it keys on the band.**

```bash
grep -n -E 'CHANNELS_24GHZ_ONLY|CHANNELS_5GHZ_ONLY|CHANNELS_DUAL_BAND|selected_wifi_channel_type|VideoConfig|codec_resolution|video_fps|_800x480|_1280x720|_1920x1080|bitrate|bit_rate|max_bitrate|H264|H265|HEVC|MediaCodec|OMX|IDR|keyframe|VideoFocus|hu_fps|hu_AA_width' libzjL10001.strings.txt
```

Report every distinct string near `CHANNELS_24GHZ_ONLY` and `selected_wifi_channel_type` with a
`-B5 -A5` window. What we want to know is whether any branch lowers the resolution, frame rate or
bitrate when the phone answers 2.4 GHz only, or whether the profile is the same on every band.

**4c. Flow control and buffering.**

```bash
grep -n -i -E 'max_unacked|MediaAck|[^a-z]ack[^a-z]|jitter|buffer_size|bufsize|drop|latency|render' libzjL10001.strings.txt
```

**4d. The Bluetooth link after the WiFi session is up, and coexistence.**

```bash
grep -n -i -E 'sniff|setLinkPolicy|link_policy|[^a-z]hci[^a-z]|hfp|a2dp|disconnect|bt_disconnect|coex|[^a-z]PTA[^a-z]|wifi\.coex|setprop|persist\.|power_save|PowerSave|ps_mode|[^a-z]iw |wpa_cli|WifiLock|WIFI_MODE_FULL|HIGH_PERF|LOW_LATENCY|[^a-z]mtk' libzjL10001.strings.txt
```

Also `libzbt-main.so` and `libzbt_core.so` for the same family. Report whether anything acts on the
Bluetooth link once `AA_wait_port ok` has printed (that string is the vendor app's "session is up"
landmark), and whether any string names a coexistence property, a link-policy call, or a power-save
call on the WiFi interface.

**4e. The WiFi Direct path the last round noticed and did not follow.**

```bash
grep -n -E 'ip_link_wait_client|p2p_link_socket_fd|p2p|DIRECT-|GroupOwner|createGroup|WifiP2p' libzjL10001.strings.txt
```

Is it an Android Auto transport, or another product's (CarLife, HiCar, AirPlay)? The strings next to
it will say which log family it belongs to.

Report for Q1, as four one-line answers plus the evidence: whether the app announces AAC for the
media sink; whether any video branch keys on the 2.4 GHz channel type; whether anything acts on the
Bluetooth link after the session is up; and whether `is_cp_use_aac` (the CarPlay flag in the vendor
log we hold) has an Android Auto sibling key.

## 5. Q2: USB phones, and the dongle

```bash
grep -n -i -E '18d1|2d00|2d01|2d02|2d03|2d04|2d05|[^a-z]AOA|accessory|ACC_REQ|Android Open Accessory|get_protocol|05ac|12a8|0525|a4a7|liaoyuan|[^a-z]A2A[^a-z]|[^a-z]cdc|[^a-z]ACM|[^a-z]NCM|[^a-z]ECM|ttyACM|usbnet|rndis|/dev/bus/usb|libusb|usbfs|USBDEVFS|bulk|[^a-z]PTP|[^a-z]MTP|iAP|iap2|usbmuxd|AA_usb|usb_aa|aa_usb|wired|USB_DEVICE_ATTACHED' libzjL10001.strings.txt
strings -a -n 6 libusbmuxd.so | grep -n -i -E 'iphone|ipod|apple|05ac|12a8|carplay|ethernet|ncm' | head -40
```

And the Java side, which the shielding hides in the dex but not in the manifest and resources:

```bash
cd <the _600102 apktool_out>
grep -n -i -E 'usb|accessory' AndroidManifest.xml
ls res/xml/ | grep -i -E 'usb|device|accessory|filter'
cat res/xml/*device_filter*.xml res/xml/*accessory_filter*.xml 2>/dev/null
```

Report: whether the app has an AOAP path at all and what it sends first (a `get_protocol` or a
`0x33`/`51` request string, a `18d1` VID compare); whether it has a CDC-ACM or USB-ethernet path a
dongle would answer on (`ttyACM`, `ncm`, `ecm`, `usbnet`, or `libusbmuxd` for the Apple
personality); which of the dongle's two identities its device filter would match (`05AC:12A8` with
class `FF/FD/01` and `FF/FE/02`, or `0525:A4A7` with class `02/02/01` and `0A/00/00`); and whether
`wired AA` in the vendor log's config dump (`===wired AA===`) maps to one of those paths.

## 6. Q3: CarLink

```bash
for f in libzjL10001.so libzbt-main.so libzbt_core.so; do echo "== $f"; strings -a -n 6 $f | grep -n -i -E 'carlink|car_link|carlinkit' ; done
nm -D --defined-only libzbt-main.so | grep -i -E 'carlink|car_link'
strings -a -n 4 libzbt-main.so | grep -E '^zj\.zbt' | grep -i carlink | sort -u
```

Around each hit, quote the `-B3 -A3` window and any UUID (`xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`)
within it. Report what the app means by CarLink (a phone-side app, a dongle protocol, an OEM link
to the car), which transport it rides (Bluetooth RFCOMM, BLE, USB, WiFi), and whether it shares any
code path or message family with the Android Auto session.

## 7. Q4: the whole media path in five lines

From what the four families above show, write five lines for the analysis file: how the Android
Auto bytes come in (TCP over the access point, and which library owns the socket), where video is
decoded (`MediaCodec` from Java, `OMX` from native, or a vendor decoder), where audio is played
(`AudioTrack`, `libaudioclient`, or a vendor sink), what pacing or buffering sits between, and which
of those a non-privileged app could copy. Mark each line as read from a string, a symbol, or
inferred.

## 8. Reporting back

`zlink-media-usb-and-carlink-results.md`, on this branch, alongside this brief. Not the template's
PASS/FAIL format; use this shape:

```markdown
# ZLink teardown, part two: results

**Date:** <yyyy-mm-dd>
**Extraction:** <paths; which build each hit is from>

## Inventory
<the §3 table: file, path, size, sha256; which expected files are absent>

## Q1 media and Bluetooth
<four one-line answers, then the evidence per sub-family, hits with windows, empties named>

## Q2 USB
<four one-line answers, then the evidence, including the device filter xml verbatim>

## Q3 CarLink
<what it is, which transport, shared with AA or not, evidence>

## Q4 the media path
<five lines, each marked string / symbol / inferred>

## Anything the brief did not ask about
```

The three numbers that decide the next step: the audio codec announced for the media sink, whether
the video profile changes on `CHANNELS_24GHZ_ONLY`, and whether the app touches the Bluetooth link
after `AA_wait_port ok`.

## 9. Do not re-run

Settled by `zlink-wpp-channel-results.md` and its addendum: the `zj.AA.*` namespace is a complete
Android Auto protocol reimplementation private to `libzjL10001.so`; `libzbt-main.so`'s exports are
HID, BLE, CarLink, HiCar and phone-link state, with no WiFi handshake message; `gocsdk_zj` is the
daemon that `dlsym`s them and owns the module's serial port. Cite those rather than re-deriving.
