# ZLink teardown, part two: response to the results

Written after reading `zlink-media-usb-and-carlink-results.md` against the vendor log and the two
`libzbt-main.so` builds held on this side. The round did what it could with what it had; four of
its answers are keepers, one line is wrong, and the one number that was meant to decide the next
step is still open because the tools it needed were on the wrong machine. That is the brief's
fault, and part three fixes it.

## 1. Keepers, so no later brief asks again

- **Video does not key on the band.** No string in either build lowers resolution, frame rate or
  bitrate on `CHANNELS_24GHZ_ONLY`; `video_configs` is a list the phone picks from; AA video is
  `MEDIA_CODEC_VIDEO_H264_BP` only, no H.265 anywhere. The adaptive resolution and bitrate code is
  HiCar and mirror. Settled.
- **Nothing acts on the Bluetooth link after `AA_wait_port ok`.** No sniff, link-policy, coex,
  power-save or WifiLock string in `libzjL10001.so`; the RFCOMM link stays open as the control
  channel. The A2DP enable request lives in `libzbt-main.so` and nothing visible wires it to the
  AA session. Settled for the AA library.
- **USB.** Wired AA is libusb AOA driven by a root daemon; wired CarPlay is a USB-gadget NCM
  interface plus `libusbmuxd` lockdown; there is no Android USB host or accessory filter at all;
  and the wireless adapter's Android identity (`0525:A4A7`, `liaoyuan`, `A2A`) appears nowhere.
  So the vendor app does not know that adapter as an Android device; it would drive the Apple
  personality, as root. Nothing on that path is reachable from a non-privileged app. Settled.
- **CarLink** is a BLE-GATT link mode in `libzbt-main.so`, disjoint from `zj.AA.*`. Settled.

## 2. One correction: the vendor head unit hosts the access point

Q4 line 1 says "the phone hosts the AP, ZLink's head unit joins it", marked inferred. It is the
other way round, and it is measured in the vendor log held here (`zlink_log-8.txt`, 2026-08-05):

```
ap_info_handle: ap_ssid = Car_Wifi_5005, ap_passwd = ..., ap_band = 1, ap_NIC_name = wlan0
[getchannel] final channel=36   [getFrequcy] final freq=5180
AA_wait_wifi_ready: wifi ip---192.168.43.1---
AA_wait_port ok (port = 10980)
```

and in the library itself: `WifiCreateAP: ssid = %s, key = %s, ip = %s, channel = %d, mac = %s`.
The head unit brings up its own SoftAP, binds a TCP port, and only then runs the Bluetooth
handshake; the phone joins. This matters for the unit the brief was written for: on a 2.4 GHz-only
radio the vendor app hosts a 2.4 GHz access point too, so what it sends over that link is the
whole comparison, and the codec is the only lever left in it.

## 3. What "AAC-capable" does and does not say

`MEDIA_CODEC_AUDIO_AAC_LC` being a selectable `SinkMediaCodecType` value means the vendor sink can
announce it. It does not say that it does. The announcement is written by `send_SinkSetup` from
whatever `get_is_AA_AAC_audiotype` returns, and that function's input is not a string: there is no
`persist.*aac` key beside it the way `is_cp_use_aac` sits beside the CarPlay path. So the
hypothesis the brief carried, an Android Auto sibling of `is_cp_use_aac` in the config dump, is
retired: the vendor decides AAC in code, not in a key. Reading that code is part three.

Meanwhile Open Headunit no longer waits on it. On a wireless session over a radio with no 5 GHz
band the app now announces AAC for its audio sinks by default, beside the 720p30 cap it already
applied there, and takes the codec each sink carries from the phone's own Media Sink Setup rather
than from the setting. The vendor's choice is now a comparison, not a gate.

## 4. The tooling gap is ours

The brief said "the recipe is `extract_zbt_proto.py` in the handoff folder". That folder has never
been on the rig; the script was here. And `uvx --with capstone` has now been named in two briefs
and reported absent in two results without being bootstrapped. Part three ships both scripts on
this branch under `tools/zbt/` and makes the disassembly the round rather than an option.

## 5. Two small things

- The results left the 105-name `zj.AA.*` list in the rig's scratchpad. Part three asks for it as
  a committed text file; it is the most useful reference the round produced.
- The `-B5 -A5` window around `selected_wifi_channel_type = %d` being a logging cluster is a real
  finding, and part three's xref step is what turns "no branch string" into "no branch".
