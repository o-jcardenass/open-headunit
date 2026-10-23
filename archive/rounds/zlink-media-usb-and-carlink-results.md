# ZLink teardown, part two: results

**Date:** 2026-09-10
**Extraction:** `hur-wifi-test-scripts/extracted/`, the three sibling ZLink extractions already on
the PC. Two distinct native builds of the load-bearing library:

| Tag in this file | Directory | `libzjL10001.so` sha256 / md5 |
|---|---|---|
| **A / `_600102`** | `com.zjinnova.zlink_600102_20260805_080829/` | `d67893fb993734879d1b8bfcf2ac007f29cb23f0dc17b5c2dd89b8840f97ec9a` / `e291fdfa61a2fabe43be0fc9b4565dbe` |
| **B / `_600106_jg`** | `com.zjinnova.zlink_600106_20260806_jg/` | `abd805697d271d345f7642049f94c7de381df8a679e37e24d20c030edef150a4` / `dfbe7929d6e0b7f07a3af0093ff3ae95` |
| (C / `_600106`) | `com.zjinnova.zlink_600106_20260806/` | byte-identical to B (same sha256), not a third build |

`_600102` carries `apktool_out/` (manifest, `res/`, `smali`) plus its own APK `CarZhiJian.apk`;
`_600106_jg` and `_600106` are `jadx_out/` only (the `.so` set under
`jadx_out/resources/lib/armeabi-v7a/`, no APK). Every family below was run against **both** A and B
`libzjL10001.so`; hits are identical in content across the two builds except where noted (line
numbers differ by a few throughout). `libzbt-main.so`, `libzbt-main-64.so`, `libzbt_core.so`,
`libusbmuxd.so`, `libzjcarlife.so`, `libhicar.so`, `libcast_core.so` are byte-identical across A
and B, so a single run covers both.

Per §2, an absence is a finding: greps that matched nothing are shown with the command that
produced the empty result.

## Inventory

| File | Path (A) | Size | sha256 | Present in B? |
|---|---|---|---|---|
| `libzjL10001.so` | `apktool_out/lib/armeabi-v7a/` (+ dup under `jadx_out/`) | 3 305 192 | `d67893fb…` (A) | yes, `abd80569…` — different build |
| `libzbt-main.so` | `apktool_out/lib/armeabi-v7a/` | 141 292 | `d947c08c059c06967fa91ecaed8c3838fa800b8a55bd9af4e02c0c6fc9d6c0e8` | yes, identical |
| `libzbt-main-64.so` | `apktool_out/lib/armeabi-v7a/` | 162 952 | `808d8b4926e156ef28befc05afaa3944f7adb339672401ef562da37d4c56c2c7` | yes, identical |
| `libzbt_core.so` | `apktool_out/lib/armeabi-v7a/` | 213 536 | `d07c0186594d003865a56033bd3d91e2956b0d16392854ada07280160f777f72` | yes, identical |
| `libusbmuxd.so` | `apktool_out/lib/armeabi-v7a/` | 2 153 976 | `25e904ea096a9476b6d8ef9d1ae6d9698decd75630c50b351630217d894285e4` | yes, identical |
| `libzjcarlife.so` | `apktool_out/lib/armeabi-v7a/` | 2 870 372 | `ed332d5ffac065a428c2ed3abf73ab7cadc7de631ef540f376698cc8ac639587` | yes, identical |
| `libhicar.so` | `apktool_out/lib/armeabi-v7a/` | 54 368 | `3be02d6a4fb815bf87133a1c199d3ad507a2f6777883866875221257a717bd1f` | yes, identical |
| `libAirPlay.so` | `apktool_out/lib/armeabi-v7a/` | 272 620 | `30d9c72545a5bc1890ecd8fb34ca4a28854225ee9c5d24e031472116cab02187` | yes, `f1bdad07…` — different build |
| `libzjAirPlay.so` | `apktool_out/lib/armeabi-v7a/` | 1 488 140 | `c9d4f3ca1c7682e1da761662382be369926aa5b3290acbfea341c1323f6416de` | yes, `35ab2f0f…` — different build |
| `libcast_core.so` | `apktool_out/lib/armeabi-v7a/` | 157 388 | `1eac96d1b1d4192d38201e09ab038fae228fe6b0e1ac1702d06b6997c2f6caf5` | yes, identical |

**Absent, expected by the brief / needed by a step:**

- `native_strings/libzjL10001.so.strings.txt` exists for A only. B's `native_strings/` directory is
  **empty** (0 files). Regenerated for this round: `strings -a -n 6 <B>/libzjL10001.so >
  B_libzjL10001.strings.txt` (20 710 lines).
- `extract_zbt_proto.py` and the `zbt/` handoff directory: **not on this machine.**
  `find /home/oscar -maxdepth 4 -iname '*extract_zbt*'` → empty. Protobuf-c descriptor tables were
  **not** decoded out of the ELF; every enum below is reported from `strings` symbol names only
  (`ZJ__AA__…__<VALUE>` tokens the protobuf-c generator emits), which give the value *names* and
  their message but not the assigned integers or which is the default.
- The handoff folder (`~/ohu-fixes-handoff/`, "the link-not-audio analysis of 2026-09-10", "the
  deobfuscated Java"): **not on this machine.** `ls ~/ohu-fixes-handoff` → No such file or
  directory. The "vendor log we hold" (`is_cp_use_aac`, `===wired AA===`, `AA_wait_port ok`) is not
  a separate file here either — every one of those tokens is a **string literal inside
  `libzjL10001.so`**, quoted from there below.
- Deobfuscated Java / `ZBTService`: not recoverable. `CarZhiJian.apk` (in A) is shielded
  (`libshell-super.com.zjinnova.zlink.so`, `libshella-4.6.2.2.so` in its `lib/`); jadx sees only
  `MyWrapperProxyApplication.java`. B has no APK at all. Same state the WPP round reported.
- `capstone` for the local Python: absent (WPP round). No ARM disassembly was done; call-flow
  claims below are string-proximity only and are marked *inferred*.

## Q1 media and Bluetooth

**Four one-line answers:**

1. **Does the app announce AAC for the media sink?** Yes — it *can*. `zj.AA.SinkMediaCodecType`
   carries `MEDIA_CODEC_AUDIO_AAC_LC` and `MEDIA_CODEC_AUDIO_AAC_LC_ADTS` as selectable sink values
   (not just source values), and a dedicated function `get_is_AA_AAC_audiotype` is called inside
   `send_SinkSetup` / `zj__aa__sink_setup__pack`. Whether it announces AAC or PCM on a given run is
   a runtime decision made by that function; its input could not be recovered from strings (no
   `persist.*aac` / `getprop`-style string is attached to it, unlike the CarPlay flag which has
   `not set is_cp_use_aac` / `cp is_cp_use_aac %d`). So: AAC is a *policy choice the app makes per
   session*, and the lever is `get_is_AA_AAC_audiotype`, source unknown from static strings.
2. **Does any video branch key on the 2.4 GHz channel type?** No branch is visible. The app reads
   the phone's answer (`WifiVersionResponse_handle: selected_wifi_channel_type = %d`) and logs it,
   but there is no string evidence of a resolution / fps / bitrate branch keyed on
   `CHANNELS_24GHZ_ONLY`. `zj.AA.MediaSinkService.video_configs` is a **repeated** field — the app
   offers a *list* of `zj.AA.VideoConfiguration` and the phone selects; the list is not shown to
   change with the band. `adaptive_resolution_mode` / `check_resolution_change` /
   `platform_zhijia_adaptive_resolution_mode` exist but sit in the HiCar / p2p-mirror / QuickTime
   symbol cluster (`MirrorInitInfo`), not the `AA_*` path. Reported as an **absence**: no
   band-conditional video string found in either build.
3. **Does anything act on the Bluetooth link after the session is up?** Not from `libzjL10001.so`.
   After `AA_wait_port ok (port = %d)` the code path is `AA_bt_loop` keeping the **BT RFCOMM
   channel open** as the control channel (`bt_aa_data_recv`, `send_WifiVersionRequest`,
   `AA_WIFI_*_RESPONSE`). No `sniff`, `setLinkPolicy`, `link_policy`, `a2dp`, `coex`, `PTA`,
   `power_save`, `WifiLock`, `WIFI_MODE_FULL` or `wpa_cli` string exists anywhere in
   `libzjL10001.so` (grep empty, both builds). The only post-session BT verbs are explicit
   disconnects (`zbt_BT_disconnect_cmd`, `apk_bt_disconnect_request`, `[DT]BT_DISCONNECT`). **The
   A2DP lever exists but lives in `libzbt-main.so`**, not the AA library — see the evidence block.
4. **Does `is_cp_use_aac` have an Android Auto sibling key?** Not as a `zj.control.InitInfo` field.
   `InitInfo` has `is_cp_to_aac` (CarPlay) and no `is_aa_to_aac`. The AA equivalent is the
   **function** `get_is_AA_AAC_audiotype`, not a control-plane bool. `is_use_phone_audio` /
   `platform_is_use_phone_audio` (a `getprop`) is the closest AA-relevant audio-routing key: when
   set, AA/HiCar audio plays through the phone's own path instead of the projected PCM/AAC channel.

### 4a. Audio codec announced and decoded

`grep -n -i -E 'aac|mp4a|MEDIA_CODEC_AUDIO|AudioConfig|sample_rate|48000|16000|number_of_bits|AudioStream|AudioTrack|apm_player|pcm'` — key hits (A build line numbers):

```
145  zlink_get_cp_use_aac
441  zj_AAC_decoder_init        442 NeAACDecOpen  443 zj_AAC_decode  445 NeAACDecDecode
1372 get_is_AA_AAC_audiotype    <- sits between zj__aa__sink_setup__pack and SinkSetup_handle
1590 (B ~9590) is_cp_to_aac     <- a zj.control.InitInfo field
8596 AAC Decode: samplerate %d, channels %d
9373 not set is_cp_use_aac
9374 cp is_cp_use_aac %d
10378 aa_main_audio_info: id = %d, sample_rate = %d, channels = %d, bits = %d
10380 aa_system_audio_info: ...   10382 aa_navi_audio_info: ...   10384 aa_tel_audio_info: ...
10461 SinkSetup_handle: sink type error, only support video(H.264) or audio(PCM)...
10695 zj.AA.AudioConfiguration   (fields: sampling_rate, number_of_bits, number_of_channels)
10963 MEDIA_CODEC_AUDIO_PCM      / ZJ__AA__MEDIA_CODEC_TYPE__MEDIA_CODEC_AUDIO_PCM
10965 MEDIA_CODEC_AUDIO_AAC_LC   / ZJ__AA__MEDIA_CODEC_TYPE__MEDIA_CODEC_AUDIO_AAC_LC
10969 MEDIA_CODEC_AUDIO_AAC_LC_ADTS
11355-11362 the same four values under ZJ__AA__SINK_MEDIA_CODEC_TYPE__…   <- sink-side enum
11373 / 11511 max_unacked      (fields of zj.AA.SinkConfig: status, max_unacked, configuration_indices)
```

`strings -a -n 4 libzjL10001.so | grep -E '^zj\.AA' | grep -i -E 'audio|media|codec' | sort -u`:

```
zj.AA.AudioConfiguration
zj.AA.AudioFocusNotification / AudioFocusRequestNotification / AudioFocusRequestType / AudioFocusStateType
zj.AA.AudioStreamType
zj.AA.MediaBrowserService / MediaCodecType / MediaPlaybackMetadata / MediaPlaybackStatus
zj.AA.MediaPlaybackStatusService / MediaSinkService / MediaSourceService
zj.AA.SinkMediaCodecType
```

- **`zj.AA.MediaCodecType`** value names: `MEDIA_CODEC_AUDIO_PCM`, `MEDIA_CODEC_AUDIO_AAC_LC`,
  `MEDIA_CODEC_AUDIO_AAC_LC_ADTS`, `MEDIA_CODEC_VIDEO_H264_BP`. No `H265`/`HEVC`/`H264_MP`/`H264_HP`
  (`grep -c -i 'h265\|hevc\|H264_MP\|H264_HP'` → 0, both builds). AA video is H.264 Baseline only.
- **`zj.AA.SinkMediaCodecType`** (what the head unit's sink announces): the same four names. So
  `MEDIA_CODEC_AUDIO_AAC_LC` and `MEDIA_CODEC_AUDIO_AAC_LC_ADTS` are **selectable sink values**, not
  a fixed PCM-only sink. (`SinkSetup_handle: … only support video(H.264) or audio(PCM)` is a
  *different* sink — it is in the `wfd_rtp_loop` / Miracast cluster, not the `zj.AA.*` one.)
- AAC decode is FAAD2 (`NeAACDec*` = libfaad; `zj_AAC_decoder_init` wraps it). Encoders
  `libfdk-aac.so` + `libfdk_aac.so` are shipped (for the mic uplink / CarPlay).
- Per-stream audio params are logged from `aa_main_audio_info` / `aa_system_audio_info` /
  `aa_navi_audio_info` / `aa_tel_audio_info` with `sample_rate, channels, bits` — values are
  runtime, not in strings.
- **Descriptor recovery (the enum integers / default value): NOT DONE** — `extract_zbt_proto.py`
  absent (see Inventory). Only the value *names* above.

### 4b. Video profile, and whether it keys on the band

`grep -n -E 'CHANNELS_24GHZ_ONLY|selected_wifi_channel_type|VideoConfig|codec_resolution|_800x480|_1280x720|_1920x1080|H264|H265|VideoFocus|VIDEO_FPS'` — key hits:

```
10483 WifiVersionResponse_handle: selected_wifi_channel_type = %d      <- read + logged, no branch string
10698 codec_resolution   10699 frame_rate   10700 width_margin   10701 height_margin
10707 zj.AA.VideoConfiguration
       fields: codec_resolution, frame_rate, width_margin, height_margin, density,
       decoder_additional_depth, viewing_distance, pixel_aspect_ratio_e4, real_density
10967 MEDIA_CODEC_VIDEO_H264_BP
10996 VIDEO_800x480    10998 VIDEO_1280x720   11000 VIDEO_1920x1080      (zj.AA.VideoCodecResolutionType)
11017 VIDEO_FPS_60     11019 VIDEO_FPS_30                                (zj.AA.VideoFrameRateType)
11826 CHANNELS_5GHZ_ONLY  11828 CHANNELS_24GHZ_ONLY  11830 CHANNELS_DUAL_BAND   (zj.AA.WifiChannelType)
11852-11857 same three under ZJ__AA__WIFI__WIFI_CHANNEL_TYPE__…
```

Context around `CHANNELS_24GHZ_ONLY` (`-B5 -A5`, A build 11821-11835) is only the enum block
itself — the three value names, their `ZJ__AA__WIFI_CHANNEL_TYPE__…` twins, and the neighbouring
enum `zj.AA.wifi.WifiChannelType`. No format string, no resolution literal, no fps token within
the window in either build.

Context around `selected_wifi_channel_type = %d` (A build 10473-10498) is a run of sibling
`*_handle` log format strings (`WifiStartResponse_handle: ip_address`, `WifiConnectStatus_handle`,
`InstrumentClusterInput_handle`, …) — a logging cluster, no branch.

`zj.AA.MediaSinkService` fields: `available_type, audio_type, audio_configs, video_configs,
available_while_in_call`. `video_configs` and `audio_configs` are plural/repeated → the app offers
a set and the phone picks. **No string ties the offered set to the band.** Reported as an absence.

`adaptive_resolution_mode`, `check_resolution_change`, `platform_zhijia_adaptive_resolution_mode:
%d`, `platform_get_hicar_bitrate_level: %d`, `persist.zj.hiCarbitrate` — all in the
HiCar / `MirrorInitInfo` / QuickTime cluster (line-adjacent to `hicar_loop_start`,
`quick_time_loop_start`, `aoa_mirror_loop_start`), **not** the `AA_bt_loop` / `wireless_AA_loop`
path. So ZLink *does* do adaptive resolution + bitrate stepping — for HiCar and screen-mirror, not
for Android Auto.

### 4c. Flow control and buffering

`grep -n -i -E 'max_unacked|MediaAck|ack|jitter|buffer_size|drop|latency|render'` — key hits:

```
11373 / 11511  max_unacked   -> field of zj.AA.SinkConfig  {status, max_unacked, configuration_indices}
               and of zj.AA.MicrophoneRequest {anc_enabled, ec_enabled, max_unacked}
1450-1455      send + init + pack + unpack for zj.AA.SinkAck   (zj__aa__sink_ack__*)
10477          zj__aa__sink_ack__unpack fail...
8469           /system/bin/iptables -I OUTPUT -p tcp --dport %d -d 127.0.0.1 -j DROP   (socket "protect", not media)
12803          prerendered
```

- ZLink's head unit sends `zj.AA.SinkConfig` with a **`max_unacked`** field and handles
  `zj.AA.SinkAck` (`send_` + `_handle`). It implements the AAP media-ack / unacked-frame
  flow-control window. The *value* of `max_unacked` is set in code — not in strings, and not
  recoverable without the descriptor tables or disassembly.
- No `jitter`, `buffer_size`, `bufsize` string. `prerendered` appears once (likely an AV-sync
  field name). No explicit media-drop counter string.
- `iptables … --dport %d … -j DROP` + `add_iptables_protect` is a firewall rule the daemon inserts
  to stop other processes reaching its loopback listeners — not media pacing.

### 4d. The Bluetooth link after the WiFi session is up, and coexistence

`grep -n -i -E 'sniff|setLinkPolicy|link_policy|hci|hfp|a2dp|disconnect|coex|PTA|power_save|ps_mode|wpa_cli|WifiLock|WIFI_MODE_FULL|HIGH_PERF|LOW_LATENCY'` **against `libzjL10001.so`** (both builds):

```
524  zbt_BT_disconnect_cmd   525 apk_bt_disconnect_request   526 serial_bt_disconnect
8910 [DT]BT_DISCONNECT        8947 serial_bt_disconnect
8971 -------apk BT---MESSAGE_BT_DISCONNECTED     9032 ------zbt----MESSAGE_BT_DISCONNECTED
8525 / 13722  persist.zj.hicar.enablehfp
(no sniff / setLinkPolicy / link_policy / a2dp / coex / PTA / power_save / WifiLock / wpa_cli hit)
```

Same families against **`libzbt-main.so`** (the peripheral-BT module):

```
7    libzbt_a2dp_enable_cb
96   libzbt_phone_hfp_link_state         (logs: phone_type: %d, is_hfp_connect:%d, is_pair:%d)
108  libzbt_request_a2dp_enable_CB_init
294-298  zj__zbt__request_a2dp__{init,get_packed_size,descriptor,pack,pack_to_buffer}
384  libzbt-main: bt_a2dp__unpack fail..
409  libzbt-main: MESSAGE_a2dp_state_handle
810  proto/zbt_hfp_link_info.pb-c.c       (zj.zbt.hfplink_info message)
```

`libzbt_core.so`: `libzbt_phone_hfp_link_state` (dlsym target, `libzbt_init: dlsym … fail..`).
`libzjcarlife.so`: `MSG_CMD_BT_HFP_REQUEST`, `MSG_CMD_BT_HFP_STATUS_REQUEST` (CarLife, not AA).

**Reading:** there **is** an A2DP coexistence lever — `zj.zbt.request_a2dp` + `libzbt_a2dp_enable_cb`
+ `libzbt_request_a2dp_enable_CB_init` — a message that asks the phone to enable/disable A2DP, plus
HFP-link-state reporting (`is_hfp_connect`, `is_pair`). It is **entirely in `libzbt-main.so`**, the
BLE/HID/HiCar peripheral module that the WPP round established is driven by `gocsdk_zj` / the `Zbt`
JNI, **not** by `libzjL10001.so`'s AA session code. `libzjL10001.so` has no `a2dp` string at all.
So: the vendor app *can* toggle the phone's A2DP, but that path is not wired to the AA WiFi session
inside the library that owns it — it would be an app-level (Java `ZBTService`, unrecoverable here)
or `gocsdk_zj` decision. No sniff-mode, link-policy, WiFi-coex-property or WiFi-power-save call in
any of the four libraries checked.

### 4e. The WiFi Direct path

`grep -n -E 'ip_link_wait_client|p2p_link_socket_fd|p2p|DIRECT-|GroupOwner|createGroup|WifiP2p'` (A):

```
12398 _zj_p2pmirror._tcp
12418-12423 zlink ip_link_wait_client: select fail../ timeout../ Got the ip link socket /
            ip link got a client / Got the p2p link socket / p2p link got a client
12425 zlink ip_link_pthread: FoxServerInit p2p_link_socket_fd error...
```

It is **not an Android Auto transport.** The mDNS service name is `_zj_p2pmirror._tcp` and the
socket is handed to `FoxServerInit` (`FoxServerInit` / `FoxClientConnect` / `FoxSendData2Server`
are exported by `libzbt-main.so` — ZLink's own "Fox" screen-mirror/cast protocol, also used by
`libcast_core.so`). `ip_link_wait_client` accepts an "ip link" client and a "p2p link" client for
the **mirror** path. AA's own Wi-Fi bring-up is the separate `wireless_AA_loop` / `AA_wait_wifi_ready`
/ `AA_wait_port` sequence, which uses the phone-hosted AP + TCP, not a ZLink-owned P2P group.

## Q2 USB

**Four one-line answers:**

1. **Does the app have an AOAP path, and what does it send first?** Yes. `libzjL10001.so` links
   **libusb** directly (`libusb_open_device_with_vid_pid`, `libusb_control_transfer`,
   `libusb_claim_interface`, `libusb_bulk_transfer`, `libusb_detach_kernel_driver`,
   `libusb_hotplug_register_callback`, …) and drives AOA itself: `AndroidAutoStart`,
   `is_aoa_device` / `is_going_aoa_device`, `is_going_aoa_device protocol = %d`,
   `is_AOA_Device: device is audio-only`, `AOA_Endpoint_Check`. The first exchange is an AOA
   **get-protocol control transfer** (`is_going_aoa_device protocol = %d` logs its result) followed
   by the accessory string sends; there is no `18d1`/`2d0x` VID *string literal* (those would be
   integer literals in code), and it compares the device's `iProduct` against `"Android"` /
   `"Android Auto"`.
2. **Does it have a CDC-ACM / USB-ethernet path a dongle would answer on?** Yes, but only a
   **USB-ethernet / NCM** one, for Apple: `platform_iap_ncm_init`, `g_ncm_netname`,
   `ncm_down_up_networkcard`, `apple_usb0`, `ifconfig apple_usb0 inet 10.0.1.100`,
   `/config/usb_gadget/g1/functions/ncm.gs0`, `enable_iap_ncm_old` / `enable_iap_ncm_new`,
   `iap,ncm` / `iapzj,ncm`, `/dev/zjinnova_iap2` (a custom iAP2 kernel driver). There is **no
   `ttyACM` / `ttyUSB` / CDC-ACM serial path** — `grep -n -E '/dev/tty|ttyUSB|ttyACM|ttyGS'`
   returns a single bare `/dev/tty`. No `ecm` / `usbnet` / `rndis` consumer path either.
3. **Which of the dongle's two identities would its device filter match?** **Neither, by an Android
   `device_filter.xml`** — ZLink has no USB host / accessory `<intent-filter>` at all (see the xml
   block below; it is a privileged `/system/app` daemon doing raw `/dev/bus/usb` via libusb). By
   *behaviour*, it would drive the **`05AC:12A8` Apple personality** (via `libusbmuxd` lockdown +
   `platform_iap_ncm_init` NCM), and would **not** engage the `0525:A4A7` `liaoyuan A2A` CDC-ACM
   personality — `grep -rn -i -E 'liaoyuan|A2A|0525|a4a7|PIA3'` across every native-strings file
   and the jadx sources of both builds is **empty**. Nothing in ZLink references that identity.
4. **Does `===wired AA===` map to one of those paths?** Yes — to the AOA path. The link-mode dump
   `=================zlink link mode(0x%x)===` enumerates `wired carplay / wireless carplay /
   wired AA / wireless AA / wired hiCar / wireless hiCar / QuickTime / AirPlay / AOA link / IP Link
   / wired CarLife / wireless CarLife / USB NetShare / wireless DLNA / wireless VIDEO_STREAM`.
   `wired AA` = `AndroidAutoStart` over libusb-AOA (`message_recv_loop: aoa_recv fail..`,
   `AA_main_audio_loop: aoa_recv fail..` are the wired-AA data loops). `wired carplay` =
   `wire_carplay_loop_start` → `platform_switch_device` → `platform_iap_ncm_init` → `CarPlay_Start`
   + `zj_iap_start` (+ `tools_restart_mdnsd`). `QuickTime` = `QuickTimeModeStart` /
   `quick_time_loop_start` (legacy iOS QuickTime screen stream). `AOA link` and `AOA mirror`
   (`aoa_mirror_loop_start`) are the generic phone-as-accessory mirror modes.

### Evidence — libzjL10001.so USB symbols (A, dynsym order)

```
39   platform_iap_ncm_init          242-280  libusb_* (control_transfer, reset_device,
252  is_going_aoa_device                      get_device_descriptor, hotplug_register_callback,
253  libusb_open_device_with_vid_pid          open_device_with_vid_pid, kernel_driver_active,
258  is_aoa_device                            detach_kernel_driver, claim_interface, close,
259  is_going_carplay_device                  bulk_transfer, set_configuration, init,
260  get_aa_usb_info                          get_string_descriptor_ascii, get_config_descriptor)
261  AndroidAutoStart               264  CarLifeModeStart   265 QuickTimeModeStart
267  CarPlayModeStart               268  aoa_mirror_start   288 carlife_aoa_check_watch_dog_start
486  g_ncm_netname   487 ncm_down_up_networkcard
503  platform_zhijia_get_vid   504 platform_zhijia_get_pid   505 platform_zhijia_get_sn
2321-2326  ReadFromiAPPort / Send2iAPPort / CheckiAPPort (+ _old variants)
2330 pack_wired_CarPlayStartSession   2331 pack_wireless_CarPlayStartSession
2434 carlife_pack_AoaData   2435 carlife_Unpack_AoaHead   2436 carlife_aoa_send
2475 iphone_network_share_start
```

Data-loop / state strings:

```
8021 wire_carplay_loop_start: platform_iap_ncm_init      8023 Wire carplay platform_iap_ncm_init fail
8074 [usb main loop]netshare is on, so wire carplay cannot link!
8319 maybe iphone is disconnect.......
8328 bNumEndpoints = %d   8329 using EP_in 0x%02x and EP_out 0x%02x, interface_number = %d
8330 is_going_aoa_device protocol = %d      8331 is_AOA_Device: device is audio-only
8332 AndroidAutoStart: libusb_open_device_with_vid_pid fail
8335 Android    8336 Android Auto    8338 CarLife
8343 CarPlayModeStart: libusb_open_device_with_vid_pid fail
8344 CarPlayModeStart: libusb_control_transfer fail
8351 aap_device_open: AOA_Endpoint_Check fail
8369 got a apple device
8422 am broadcast -a com.zjinnova.zlink.iPhone.Disassociate --es connect_type carplay_wireless -f 0x10000000
8438 =================zlink link mode(0x%x)===================   (+ the 15 mode names, 8439-8453)
8447 ===AOA link===   8441 ===wired AA===   8442 ===wireless AA===
8639 iap,ncm    8647 iapzj,ncm    8658 apple_usb0   8659 ifconfig apple_usb0 inet 10.0.1.100
8710 /config/usb_gadget/g1/functions/ncm.gs0
8726 enable_iap_ncm new    8727 enable_iap_ncm_new fail...
10320 message_recv_loop: aoa_recv fail..    10574 AA_main_audio_loop: aoa_recv fail..
12294 +++++++aoa_mirror_loop_start+++++++    12295 aoa_mirror_loop_start: aap_device_open fail...
```

The wired path is heavily SoC-specific: dozens of OTG-host-mode switch commands keyed on
`ro.product.board` (`mt8163`, `sp7731e`, `ums9620`, `rk3576`, `lahaina`, `bengal`, …) and USB-gadget
`configfs` teardown/setup (`sys.usb.configfs`, `/config/usb_gadget/g1/UDC`, `iap.gs0` / `iap2.gs0` /
`ncm.gs0` function symlinks). None of this is reachable from a non-privileged app.

### Evidence — libusbmuxd.so (identical A/B, a `libimobiledevice` / carbit fork)

```
1585 usbmuxd    1588 com.apple.mobile.lockdown    1600 com.apple.mobile.insecure_notification_proxy
1602 com.apple.mobile.lockdown.request_pair       1603 com.apple.mobile.lockdown.request_host_buid
1290-1301 [UsbMux]createSocket/forward: local=%d, remote=%d / no connected phone
1352-1356 [UsbMux]connect_to_device: available device num=%d / successfully connect to device(%d)
1620 carbitusbmuxdokopen    1767 com.carbit.usbmuxd    1769 org.libimobiledevice.usbmuxd
1377 /data/local/tmp/usbmuxd.pid    1947 com.apple.mobile.iTunes    2012 com.apple.mobile.notification_proxy
1747 usbmuxd_read_buid    1749 usbmuxd_read_pair_record    1751 usbmuxd_save_pair_record
```

This is the USB iPhone lockdown / pairing-record / port-forward layer for wired CarPlay: it speaks
USB bulk to the iPhone's usbmux interface, does the lockdown pair, then forwards TCP ports over the
mux channel. `grep -n -i -E 'ncm|ethernet|carplay'` against it is **empty** — it does the
mux/lockdown half; the IP-over-USB half is `platform_iap_ncm_init` in `libzjL10001.so`.

### The device-filter xml (verbatim)

`_600102/apktool_out/res/xml/` contains exactly two files, **neither a USB filter**:

```
$ ls com.zjinnova.zlink_600102_20260805_080829/apktool_out/res/xml/
file_paths.xml
network_security_config.xml

$ grep -n -i -E 'usb|accessory|host' com.zjinnova.zlink_600102_20260805_080829/apktool_out/AndroidManifest.xml
(no output)

$ find com.zjinnova.zlink_600106_20260806_jg/jadx_out -iname AndroidManifest.xml \
    -exec grep -n -i -E 'usb|accessory|host|DEVICE_ATTACHED' {} \;
(no output — 516-line manifest, no match)
```

There is **no `android.hardware.usb.action.USB_DEVICE_ATTACHED` filter, no
`<usb-device>` / `<usb-accessory>` `res/xml`, no `USB_PERMISSION` receiver** in either build's
manifest. ZLink does not use the Android USB host / accessory framework. It runs as
`/system/app/zlink5/` (WPP-round addendum) with a rooted `z-link` daemon and opens `/dev/bus/usb`
through libusb, and reconfigures the *device-side* USB gadget through `configfs` directly.

## Q3 CarLink

**What it is:** a **BLE-GATT control transport**, and a peer projection mode at the same level as
CarPlay / AA / HiCar / CarLife (`zj.obex.OBEX_LINK_MODE` = `LINK_MODE_NONE, LINK_MODE_CARPLAY,
LINK_MODE_AA, LINK_MODE_HICAR, LINK_MODE_DEX, LINK_MODE_CARLIFE, LINK_MODE_CARLINK`; and
`phone_type` = `BT_DEVICE_CARPLAY / BT_DEVICE_AA / BT_DEVICE_CARLIFE / BT_DEVICE_HICAR /
BT_DEVICE_CARLINK`). Not "Carlinkit" and not `com.syu.carlink` — `grep -i carlinkit` across all
three libs is empty.

**Which transport it rides:** BLE GATT, via the external `zbt` Bluetooth module. `libzbt-main.so`
exports (`nm -D --defined-only`):

```
0000bde0 T libzbt_carlink_StartAdvertise_CB_init
0000be60 T libzbt_carlink_StopAdvertise_CB_init
0000bee0 T libzbt_carlink_BleGattSend_CB_init
0000bf60 T libzbt_carlink_BleGattReceived
         zj__zbt__carlink_data__{init,get_packed_size,pack,pack_to_buffer,unpack,free_unpacked}
0x22ac8  D zj__zbt__carlink_data__descriptor
```

`strings` shows the message family: `proto/zbt_carlink_data.pb-c.c` (`zj.zbt.carlink_data`,
`Zj__Zbt__CarlinkData`), plus `zbt_carlink_open_status.pb-c.c`, `zbt_carlink_connect_status.pb-c.c`,
`zbt_carlink_ble_uuid_setup.pb-c.c`, `zbt_carlink_gatt_uuid_setup.pb-c.c`,
`zbt_carlink_start_advertise.pb-c.c`. Log tag `(carlink)`. `libzbt_core.so` `dlsym`s all four
`libzbt_carlink_*` functions (`libzbt_init: dlsym libzbt_carlink_… fail..`). So: the head unit
**BLE-advertises** (`StartAdvertise`), exchanges service/characteristic UUIDs
(`ble_uuid_setup` / `gatt_uuid_setup`), then carries `CarlinkData` over **GATT
notifications/writes** (`BleGattSend` / `BleGattReceived`), with `open_status` / `connect_status`
state messages. No RFCOMM, no USB, no Wi-Fi in this family.

**No UUID string** — `grep -n -B3 -A3` around every `carlink` hit in all three libs shows no
`xxxxxxxx-xxxx-…` literal; the UUIDs are set up dynamically via `*_uuid_setup` messages, values
runtime.

**Shared with the AA session?** No shared message family or code path. `carlink_*` is entirely in
`libzbt-main.so` under the `zj.zbt.*` namespace; the AA session is `zj.AA.*` entirely in
`libzjL10001.so` (the WPP round's load-bearing finding). The only coupling is that
`libzjL10001.so` dispatches on `phone_type == BT_DEVICE_CARLINK` / `LINK_MODE_CARLINK`
(`zlink: zbt link info message->phone_type:BT_DEVICE_CARLINK`) — a mode selector, not a shared
transport. In practice CarLink looks like ZLink's **BLE bootstrap/pairing channel for an external
projection dongle or companion**, structurally a sibling of the HiCar BLE-start path
(`libzbt_hicar_ble_start_CB_init`) which is built the same way.

## Q4 the media path

Five lines for the analysis file (each marked how it was read):

1. **AA bytes in:** the phone hosts the AP, ZLink's head unit joins it and opens a TCP session;
   `libzjL10001.so` owns the socket end to end — `AA_wait_wifi_ready` → `AA_wait_port ok (port =
   %d)` → `wireless_AA_loop` — and the whole `zj.AA.*` protobuf protocol (SSL, framing, channels)
   is private to that one library. *(read from strings + WPP-round symbol table — inferred for
   "phone hosts AP", string for the rest)*
2. **Wired AA bytes in:** `AndroidAutoStart` over libusb AOA bulk endpoints
   (`message_recv_loop: aoa_recv`, `AA_main_audio_loop: aoa_recv`); wired CarPlay instead brings up
   a USB-gadget NCM interface (`apple_usb0` @ `10.0.1.100`) and runs CarPlay/iAP2 over IP. *(string
   + symbol)*
3. **Video decode:** not `MediaCodec` from Java and not a Java surface path — `libzjL10001.so`
   carries its own H.264 bitstream handling (`h264_new` / `h264_free`, an SPS/PPS/slice-header
   parser: `======= SPS =======`, `Coded slice of an IDR picture`, `bit_rate_scale`), AA video is
   `MEDIA_CODEC_VIDEO_H264_BP` only (no H.265). Whether it hands NAL units to a platform OMX
   decoder or renders itself was not resolvable from strings. *(string — inferred for the
   handoff-to-decoder step)*
4. **Audio play:** native. `libAudioStream.so` + `libapm.so` (`apm_player.pcm`) + FAAD2
   (`NeAACDec*` via `zj_AAC_decoder_init`) for decode; per-stream configs from `aa_main_audio_info`
   / `aa_navi_audio_info` / `aa_tel_audio_info`. AAC-LC / AAC-LC-ADTS or PCM depending on
   `get_is_AA_AAC_audiotype`. WebRTC APM (`libwebrtc_apm_plus.so`, `USE_WEBRTC_APM`) + `libblinkAEC.so`
   for the mic path. *(string + symbol)*
5. **Pacing between:** the AAP unacked-frame window — head unit sends `zj.AA.SinkConfig{max_unacked}`
   and handles `zj.AA.SinkAck`; no separate jitter-buffer or drop-counter string. A non-privileged
   app could copy items 1, 3, 4, 5 (all userspace protobuf + decode + `AudioTrack`); item 2's wired
   paths need root (`/dev/bus/usb`, gadget `configfs`, per-SoC OTG sysfs) and are out of reach.
   *(string + inferred)*

## The three numbers that decide the next step

- **Audio codec announced for the media sink:** *undetermined from static strings.* AAC-LC and
  AAC-LC-ADTS are selectable `SinkMediaCodecType` values (not PCM-only), and the choice is made at
  runtime by `get_is_AA_AAC_audiotype` inside `send_SinkSetup`. Needs the protobuf-c descriptor
  dump (`extract_zbt_proto.py`, absent) or a live capture of the SinkSetup message.
- **Does the video profile change on `CHANNELS_24GHZ_ONLY`:** *no evidence of it.* The app reads
  and logs `selected_wifi_channel_type` but no resolution/fps/bitrate branch is keyed on it in
  either build. AA video config is a static repeated `video_configs` list the phone selects from.
- **Does the app touch the Bluetooth link after `AA_wait_port ok`:** *no*, not from
  `libzjL10001.so` — the BT RFCOMM link stays open as the control channel and nothing calls sniff,
  link-policy, power-save or A2DP-toggle from that library. The A2DP-enable request message exists
  only in `libzbt-main.so` (driven by `gocsdk_zj` / the `Zbt` JNI), not wired to the AA session in
  the code visible here.

## Setup notes

- **`extract_zbt_proto.py` / the `zbt/` handoff dir are not on this machine.** No protobuf-c
  `ProtobufCMessageDescriptor` decode was done. Every enum in this file is value-*names* from
  `strings` (the `ZJ__AA__…__NAME` tokens), not the wire integers and not the default value. Any
  question that needs "which value does it announce" or "what is the default" is unanswered by this
  round and needs either that script on a build with the descriptor tables, or a live capture.
- **`~/ohu-fixes-handoff/` is not on this machine** (same as the WPP round). "The link-not-audio
  analysis of 2026-09-10", "the deobfuscated Java", and "the vendor log we hold" referenced by the
  brief were not available. The brief's log tokens (`is_cp_use_aac`, `===wired AA===`,
  `AA_wait_port ok`) turned out to be **string literals in `libzjL10001.so`** and are quoted from
  there; if a separate vendor logcat exists it was not in the extraction tree or `results/`.
- **No ARM disassembler** (no `capstone`, `objdump` is x86 on this host). Every statement about
  call flow ("`get_is_AA_AAC_audiotype` is called inside `send_SinkSetup`", "the BT link stays the
  control channel") is **string / symbol adjacency**, not a verified call graph. Marked *inferred*
  where it matters.
- **B's `native_strings/` was empty**; `strings -a -n 6` was re-run for B's `libzjL10001.so`,
  `libzbt-main.so`, `libzbt_core.so`, `libusbmuxd.so`, `libzjcarlife.so` into the scratchpad. The
  `_600106` (no `_jg`) directory's `libzjL10001.so` is byte-identical to `_600106_jg`'s
  (sha256 `abd80569…`), so "both builds" in this file means A (`d67893fb…`) and B (`abd80569…`).
- **The brief's grep in §4b matched a lot of `video` noise** because `VID` / `PID` as bare tokens
  hit `video` / (nothing). Re-ran the USB families with `libusb`, `aoa`, `ncm`, `iap` anchored
  instead. Noted for reuse.
- Two families the brief listed produced **clean absences**, reported as findings: no
  band-conditional video string near `CHANNELS_24GHZ_ONLY`; no `liaoyuan` / `A2A` / `0525` /
  `a4a7` / `PIA3` anywhere in either build (native strings or jadx sources).
- No script was added to `hur-wifi-test-scripts/`; the round is `strings` / `nm` / `readelf` /
  `grep` one-liners only. `extract_zlink.sh` (the existing extractor) was not re-run — the
  extractions were already present.

## Anything the brief did not ask about

- **`libzjL10001.so` is a full multi-protocol projection stack, not just AA.** Its link-mode dump
  lists 15 modes; it ships and drives: Android Auto (`zj.AA.*`, wireless + wired-AOA), CarPlay
  (wireless + wired, AirPlay-based — `libAirPlay.so` / `libzjAirPlay.so`, `libusbmuxd.so`, iAP2 via
  `/dev/zjinnova_iap2`, NCM), HiCar (`libhicar.so`, `zj.zbt.hicar_*`), Baidu CarLife
  (`libzjcarlife.so`, `com.baidu.carlife.protobuf` C++ protobuf), Samsung DeX (`LINK_MODE_DEX`),
  AirPlay receiver, DLNA (`libzj_dlna.so`), Miracast/WFD (`wfd_rtp_loop`), a "Fox" screen-mirror
  cast path (`FoxServerInit`, `_zj_p2pmirror._tcp`, `libcast_core.so`), and USB NetShare / tethering.
- **CarPlay audio is richer than AA's**: `libAirPlay.so` advertises `AAC-LC/44100/2`,
  `AAC-LC/48000/2`, and AAC-ELD at 16k/24k/32k/44.1k/48k mono+stereo; `CarPlayGetAAC_2_apk` /
  `CarPlaySetAAC_2_apk` bridge it to the app. CarPlay video is H.264 AVCC via `ScreenStreamSetAVCC`
  / `ScreenStreamSetWidthHeight`. `is_cp_use_aac` / `is_cp_to_aac` / `is_carplay_mic_force_8k` are
  the CarPlay audio levers; `is_carplay_IOS26_close_Intelligent_zoom` suggests they track iOS
  releases closely (an "iOS 26" string is already present).
- **The wired-CarPlay dongle route is real and detailed** if OHU ever wants it: `platform_iap_ncm_init`
  sets up a USB-gadget NCM function, `ifconfig apple_usb0 inet 10.0.1.100`, runs mDNS + iAP2 +
  `libusbmuxd` lockdown pairing, then CarPlay over IP-over-USB. It also has a legacy QuickTime
  screen-stream mode (`QuickTimeModeStart`) for older iOS. All of it needs root and a
  per-SoC OTG/gadget sysfs map, which the library carries for ~40 boards.
- **`get_aa_usb_info` / `platform_zhijia_get_vid` / `platform_zhijia_get_pid`** — the wired path
  can be told a target VID/PID via `getprop` (`platform_zhijia_get_vid: %s`), i.e. an OEM can point
  it at a specific accessory/dongle identity without a code change. That is the closest thing to a
  "device filter" ZLink has, and it is a system property, not a manifest entry.
- **`am broadcast -a com.zjinnova.zlink.iPhone.Disassociate --es connect_type carplay_wireless`** —
  the app drives its own state machine with broadcast intents; there may be more of these
  (an exported-intent automation surface like the one this project's automation rounds built).
  Not enumerated this round.
- **`zj.AA.*` namespace, full list** (105 message/enum names) is in the scratchpad; it is a
  complete AAP reimplementation and matches this repo's `aap/protocol/proto/` message-for-message
  (`ChannelOpenRequest`, `SensorBatch`, `InputReport`, `MediaSinkService`, `VideoConfiguration`,
  `AudioFocusRequestNotification`, `SinkAck`, `BluetoothPairingRequest`, …), plus a few this repo
  does not implement: `zj.AA.RadioService` / `RadioProperties` / `RdsType`, `zj.AA.DrivingStatusData`,
  `zj.AA.GenericNotificationService`, `zj.AA.VendorExtensionService`, `zj.AA.MediaBrowserService`.
  Worth keeping as a reference if a future round needs a message OHU hasn't built.
