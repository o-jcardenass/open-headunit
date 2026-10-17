# ZLink teardown — WPP byte channel results

**Date:** 2026-08-12
**Extraction:** `hur-wifi-test-scripts/extracted/`, three sibling ZLink extractions already on the PC
(no build/install this round). Per the reporter, `com.zjinnova.zlink_600106_20260806_jg` is the
build that matches their actual head unit, so that one is the primary subject; the round started
against `com.zjinnova.zlink_600102_20260805_080829` before that correction arrived mid-run and was
redone against `_jg` in full. `com.zjinnova.zlink_600106_20260806` (no `_jg` suffix) was not opened;
it looks like a duplicate of one of the other two by name.

## Inventory

| File | Present in `_600102`? | Present in `_600106_jg`? | Same file across both? |
|---|---|---|---|
| `libzjL10001.so` | yes, `apktool_out/lib/armeabi-v7a/` and a duplicate under its own `jadx_out/` | yes, `jadx_out/resources/lib/armeabi-v7a/` | **no** — different build, see below |
| `libzbt_core.so` | yes | yes | yes, identical |
| `libzbt-main.so` | yes | yes | yes, identical, and matches the checksum already held |
| `libzbt-main-64.so` | yes | yes | yes, identical, and matches the checksum already held |
| `mapping.txt` | not found anywhere in either extraction | — | — |
| APK | `CarZhiJian.apk` present in `_600102` only | **not present** in `_600106_jg` | — |

Checksums, `_600106_jg/jadx_out/resources/lib/armeabi-v7a/`:

```
libzjL10001.so    sha256 abd805697d271d345f7642049f94c7de381df8a679e37e24d20c030edef150a4
                  md5    dfbe7929d6e0b7f07a3af0093ff3ae95
                  ELF 32-bit LSB, ARM, EABI5, stripped

libzbt_core.so    sha256 d07c0186594d003865a56033bd3d91e2956b0d16392854ada07280160f777f72
                  md5    a00307b8c771742862fd3550982525d3
                  (identical to _600102's copy)

libzbt-main.so    sha256 d947c08c059c06967fa91ecaed8c3838fa800b8a55bd9af4e02c0c6fc9d6c0e8
                  md5    1652e42b2abbebf287bd7859c26cfacd  <- matches what we already held
                  (identical to _600102's copy)

libzbt-main-64.so sha256 808d8b4926e156ef28befc05afaa3944f7adb339672401ef562da37d4c56c2c7
                  md5    8981b42b10540d886885c2255afb517f  <- matches what we already held
                  (identical to _600102's copy)
```

`libzjL10001.so` in `_600102` has a **different** hash (sha256 `d67893fb...`, md5 `e291fdfa...`) —
a different build of the one file that actually matters here. Both were analysed; findings below are
consistent across both, so the version difference does not change the answer.

## The answer

**Could not be determined with certainty, and the evidence that could be gathered points away from
the working hypothesis rather than confirming it.** `libzjL10001.so` does not statically link, and
gives no sign of dynamically resolving, any `libzbt_*` symbol from `libzbt-main.so`. More directly:
the Android Auto Wi-Fi handshake messages (`zj.AA.WifiVersionRequest`, `zj.AA.WifiStartRequest`,
`zj.AA.WifiInfoRespond`, and the full rest of a complete AA protocol reimplementation under the
`zj.AA.*` protobuf namespace) are defined and packed entirely inside `libzjL10001.so` itself.
`libzbt-main.so`'s own protobuf message set lives under a **different, disjoint namespace**,
`zj.zbt.*`, and every one of its ~90 exported `libzbt_*` functions is for HID/touch-event forwarding,
BLE, CarLink, HiCar service registration, or phone Bluetooth-pairing info — nothing named or shaped
like a Wi-Fi/AA handshake call. That is strong indirect evidence that whatever function sends the AA
`WifiVersionRequest` bytes is **not** `libzbt_rfcomm_data_send`, because that function's own protocol
family has no AA Wi-Fi messages to send in the first place.

The better-supported candidate, found by inventorying every Bluetooth-shaped symbol in
`libzjL10001.so` rather than assuming the `libzbt_*` prefix: a **second, separate local-socket
pathway**, `hu_bt_data_send()`, talking to a Unix domain socket at `/dev/socket/zj_bt_socket` (also
seen as the bare path `/dev/zj_bt_socket`), managed by `Start_HU_BT_pthread()` /
`disconnect_hu_bt_socket()` / `is_old_hu_BT_on()` / `is_new_hu_BT_on()`. This is unconfirmed by
disassembly (no `capstone` on this machine, see Setup notes), but two circumstantial points favor it
over `zbt_rfcomm_data_send`: the `hu_bt_*` function group's naming (`hu` reads as "head unit") is a
plausible fit for an AA-specific channel, distinct from ZLink's generic `zbt` peripheral protocol; and
`bt_aa_data_recv` sits at address `0x97cc4`, about 0x2000 bytes from `hu_bt_data_send` (`0x9ce94`) and
`Start_HU_BT_pthread` (`0x9ca54`), versus roughly 0x9000 bytes from `zbt_rfcomm_data_send`
(`0xa0ebc`) — functions from the same source file often land near each other, which is weak evidence,
not proof.

**If the transport client is rebuilt on the `0x105`/`libzbt_rfcomm_data_send` assumption as currently
planned, that assumption should be treated as unconfirmed, and likely wrong**, pending either a
disassembly pass on `send_WifiVersionRequest` (`0xc6118`) and `AA_bt_loop`, or independent
confirmation via a live capture of what `hu_bt_data_send` actually carries.

**Update, same day — see the Addendum at the end.** A follow-up pass found `libzjL10001.so` baked
into the downloaded QF001 firmware itself and, from there, went looking at `gocsdk_zj` directly
instead of reasoning about it secondhand. That complicates the picture above rather than closing it:
`libzbt_rfcomm_data_send` turns out to be real, used-for-AA infrastructure after all — just called by
`gocsdk_zj`, not by `libzjL10001.so` as this report assumed. But the specific `0x105`/TCP-3152/
protobuf-framing claim doesn't match what `gocsdk_zj`'s own strings show. Read the Addendum before
acting on anything above as a settled negative.

## Imported symbols

**`nm -D --undefined-only libzjL10001.so | grep -i zbt`** — empty, both builds. `libzjL10001.so`
imports no `zbt`-named symbol from anywhere.

**`readelf -d libzjL10001.so | grep -i needed`** — 32 entries, `libc`/`libdl`/`liblog`/`libm` plus 28
ZLink component libraries (`libAirPlay.so`, `libhicar.so`, `libnearby.so`, `libScreenStream.so`,
etc., full list is identical between both builds). **`libzbt-main.so` and `libzbt_core.so` are not
in this list.**

**`strings -a libzjL10001.so | grep -oP 'dlsym\s*\K\w+'`** — empty, both builds (the literal word
`dlopen` appears once as a standalone string, but no adjacent symbol name was recoverable this way).

**`strings -a libzjL10001.so | grep -oP 'lib\w+\.so'`** — 37 hyphen-free library filenames (all of
the `readelf NEEDED` set plus a few more referenced only as strings, e.g. `libdhcpc.so`,
`libhostapd_cli.so`, `libplatform_tools.so`, `libusbmuxd.so`, `libznetshare.so`). **This regex
structurally cannot match `libzbt-main.so` or `libzbt-main-64.so`** — the hyphen breaks `\w+` — which
is a scan bug in the brief's own command, not a real absence; see Setup notes. A targeted follow-up
grep for `libzbt-main` found it: `libzjL10001.so` does reference the literal paths
`/data/data/com.zjinnova.zlink/lib/libzbt-main.so`, `/data/local/tmp/libzbt-main.so`,
`/system/lib/libzbt-main.so` (and the `-64` equivalents), inside a function whose log tag is
`setup_libzbtmain_file`, which compares the installed copy against `/data/local/tmp/libzbt-main.so`
and `cp`s + `chmod 755`s a fresh copy over if they differ. This is a **copy-then-presumably-dlopen**
pattern, consistent with the brief's own prediction that ZLink might not link this library normally.
No dlsym'd symbol name was recovered, though: the only `libzbt_`-prefixed strings anywhere in
`libzjL10001.so` are two log fragments, `"zbt_recv_head: libzbt_recv_head fail..."` and
`"zbt_recv_body: libzbt_recv_body fail..."` — and neither `libzbt_recv_head` nor `libzbt_recv_body`
is among `libzbt-main.so`'s ~90 exported symbols, so these read as descriptive log text, not literal
`dlsym()` targets.

Checking the four candidate symbols from the brief directly against `libzbt-main.so`'s own export
table (`nm -D --defined-only libzbt-main.so`):

| Symbol | Exported by `libzbt-main.so`? | What its protobuf family carries |
|---|---|---|
| `libzbt_rfcomm_data_send` | yes, at `0000a744` | `zj.zbt.*` — HID touch/screen/control, BLE, phone-link state, CarLink, HiCar service registration/data. **No AA Wi-Fi message anywhere in this family.** |
| `libzbt_rfcomm_data_recv_CB_init` | yes, at `0000b198` | same family as above |
| `libzbt_zj_rfcomm_data_send` | yes, at `0000a9e8` | same family as above |
| `libzbt_hicar_rfcomm_data_send` | yes, at `0000bc34` | HiCar-specific subset of the same family |

All four exist in `libzbt-main.so` exactly as the brief predicted. What's missing is any evidence
`libzjL10001.so` actually calls them — no import, no dlsym string, and the message types they'd carry
don't include the ones `AA_bt_loop` is built around.

`libzjL10001.so` **defines its own, separate, similarly-named local functions** —
`zbt_rfcomm_data_send` / `zbt_rfcomm_data_recv` (no `lib` prefix, different addresses, `000a0ebc` /
`000a0e34` in the `_jg` build) — plus the rest of a `zbt_*`-prefixed group (`zbt_hid_touch_event_send`,
`zbt_multi_touch_event`, `zbt_hicar_service_*_send`, `zj__zbt__*_pack/unpack`). These are a **local
reimplementation of the same `zj.zbt` protocol**, not calls into `libzbt-main.so` — this library
packs and unpacks `zj.zbt.*` protobuf messages itself, symbol-identical in name pattern to
`libzbt-main.so`'s exports but a distinct, separately-compiled copy. This is worth flagging precisely
because it is easy to misread `zbt_rfcomm_data_send`'s presence in `nm -D --defined-only
libzjL10001.so` as confirmation of a call into `libzbt-main.so`, when it is not.

## Handshake strings

Both builds, same line numbers within one string apart (`_jg` build shown; `_600102` is off by
about -1 to -6 lines throughout, content identical):

```
537/550:  bt_aa_data_recv
1491/1507: send_WifiStartRequest
1485/1501: send_WifiVersionRequest
9794/9808: ---------------------wireless_AA_loop start
12364/12415: AA_wait_wifi_ready: wifi ip---%s---
12365/12416: AA_wait_wifi_ready: fail..
12367/12418: AA_wait_port
12368/12419: AA_wait_port ok (port = %d)
12370/12421: AA_bt_loop: AA_wait_wifi_ready fail...
12371/12422: AA_bt_loop ipaddr %s
12372/12423: AA_bt_loop: send_WifiVersionRequest
12373/12424: AA_bt_loop: bt_aa_data_recv  recevie data fail!
13797/13848: AA/AA-proto/WifiStartRequest.pb-c.c
13801/13852: zj.AA.WifiStartRequest
13802/13853: WifiStartRequest
13803/13854: Zj__AA__WifiStartRequest
```

All present, exactly as the brief predicted. `bt_aa_data_recv` and `send_WifiVersionRequest` are both
**defined** (`T`) symbols in the dynamic symbol table, not just log strings:
`bt_aa_data_recv` = `0x97cc4`, `send_WifiVersionRequest` = `0xc6118` (both from the `_jg` build).

The full `zj.AA.*` protobuf package list (recovered via `strings | grep '^zj\.AA'`) is a complete
Android Auto protocol reimplementation: `WifiVersionRequest`, `WifiStartRequest`, `WifiInfoRespond`,
`ChannelOpenRequest/Response`, `InputReport`, `SensorBatch`, `AudioFocusRequestNotification`,
`MediaPlaybackStatus`, `NavigationNextTurnEvent`, `BluetoothPairingRequest/Response`,
`BluetoothAuthenticationData/Result`, and more — dozens of message types, all under `zj.AA` or its
sub-packages (`zj.AA.bt`, `zj.AA.wifi`, `zj.AA.start`, `zj.AA.con`, `zj.AA.ps`, `zj.AA.nav`,
`zj.AA.sensor`). This is the load-bearing fact behind "the answer" above: this whole protocol family
is private to `libzjL10001.so`. It does not exist anywhere in `libzbt-main.so`.

## Cross-reference

**Did not run.** `xref_gocsdk.py` is not present anywhere under `/home/oscar` on this machine, and
`capstone` is not installed for the local Python (`ModuleNotFoundError`). Per the brief's own
instruction this step is skipped rather than faked. `objdump` and `readelf` are present but the brief
warns `objdump` on this host is x86-only and unusable on ARM; that warning was not tested further
since capstone was already unavailable and objdump was not attempted for disassembly.

The one thing done in place of it: symbol-address proximity (reported under "The answer") as a weak,
non-disassembly substitute. That is explicitly not a call-graph confirmation and shouldn't be read as
one.

## `libzbt_core.so` / Java classes

**`libzbt_core.so` NEEDED**: only `liblog.so`, `libm.so`, `libdl.so`, `libc.so` — like
`libzjL10001.so`, it does **not** link `libzbt-main.so` directly either, consistent with the whole
library being loaded via the same copy-then-dlopen pattern from a different call site.

**`nm -D --undefined-only libzbt_core.so | grep -i zbt`** — empty (82 total undefined symbols, none
`zbt`-named).

**`nm -D --defined-only libzbt_core.so | grep Java_`** — 12 JNI entry points, all under
`com.zjinnova.jni.Zbt`:

```
Java_com_zjinnova_jni_Zbt_btInfo
Java_com_zjinnova_jni_Zbt_deinit
Java_com_zjinnova_jni_Zbt_hiCarSendHuData
Java_com_zjinnova_jni_Zbt_hiCarSendPhoneData
Java_com_zjinnova_jni_Zbt_initHiCarServiceCallBack
Java_com_zjinnova_jni_Zbt_initZbt
Java_com_zjinnova_jni_Zbt_phoneLinkState
Java_com_zjinnova_jni_Zbt_requestInitBleStart
Java_com_zjinnova_jni_Zbt_requestInitBleStop
Java_com_zjinnova_jni_Zbt_requestInitBtEnableCallBack
Java_com_zjinnova_jni_Zbt_requestInitHiCarBtStatues
Java_com_zjinnova_jni_Zbt_sendHiCarRfcommState
```

Every one of these names is HiCar/BLE/generic-phone-link shaped — none is Wi-Fi- or
Android-Auto-shaped — which is a second, independent line of evidence for the same conclusion as
"The answer": this whole `Zbt`/`libzbt-main.so` stack appears to be ZLink's peripheral-Bluetooth path
(HID, BLE, HiCar), and Android Auto's Wi-Fi handshake is handled by a different code path entirely.

**Java classes (§7): not attempted.** `_600106_jg` has no APK in this extraction at all — only the
`.so` files under `jadx_out/resources/lib/`. `_600102` does have `CarZhiJian.apk`, but its `jadx_out`
decompile is close to empty: `com/zjinnova/zlink/` contains exactly one file,
`MyWrapperProxyApplication.java`, and `com/zjinnova/jni/` doesn't exist in the decompiled sources at
all. The APK is shielded (`libshell-super.com.zjinnova.zlink.so` and `libshella-4.6.2.2.so` are both
present in its `lib/` folder — a commercial Android app-hardening packer), so the real DEX is decrypted
at runtime and jadx's static pass only sees the shell loader stub. `ZBTService` and its seven inner
callback classes are not recoverable from either extraction as they stand; the earlier decompile that
did produce them (referenced in the brief) must have started from an unpacked/de-shielded APK this
round doesn't have a copy of. `dad.py` also isn't present on this machine, so this was going to be
skipped regardless.

## Setup notes

- **The brief's own `lib\w+\.so` regex cannot match `libzbt-main.so` or `libzbt-main-64.so`** — the
  hyphen isn't a word character, so `\w+` stops at `zbt`, and the pattern then requires `.so`
  immediately after, which fails. Running it literally (as done first, before noticing) produces a
  false "this library is never referenced" impression. A targeted `grep -i 'libzbt-main'` is needed
  alongside it. Worth fixing in the brief if this command is reused.
- **The mid-round correction mattered**: analysis started against `_600102` (the only extraction with
  the checksums to compare against what was already held), then was redone in full against
  `_600106_jg` once told that build is the reporter's actual one. `libzjL10001.so` differs by hash
  between the two (`libzbt_core.so`/`libzbt-main.so`/`libzbt-main-64.so` are identical across all
  three extractions, by hash). Findings are consistent between the two `libzjL10001.so` builds
  checked, but only those two — `_600106` (no `_jg` suffix) was not opened.
- Neither `xref_gocsdk.py` nor `dad.py` exist on this machine; no attempt was made to install
  `capstone` or `androguard` since the brief frames both tools as optional and this machine isn't
  described as "the rig" that's expected to carry the handoff folder.
- No `mapping.txt` in either extraction (both are raw APK/lib pulls, not build-zip outputs), so no
  R8-deobfuscation cross-check was possible — moot anyway since the natives are all stripped, not
  obfuscated Java.
- `_600106_jg` additionally ships a `native_strings/` directory alongside `jadx_out/`; it was empty
  (0 files) and not useful.

## Anything the brief did not ask about

- **The whole `zj.AA.*` namespace is a complete, independent reimplementation of the Android Auto
  protocol**, not a thin wrapper around anything from `libzbt-main.so`. Every message type AAP/OHU
  itself defines under `aap/protocol/proto/` in this repo has a same-shaped counterpart here
  (`ChannelOpenRequest`, `SensorBatch`, `InputReport`, `AudioFocusRequestNotification`,
  `MediaPlaybackStatus`, the works) — worth knowing if this round's client ever needs a reference
  implementation for a message this repo hasn't needed yet.
- `libzjL10001.so` supports at least four named OEM Bluetooth-hardware platforms internally
  (`Platform_jintaiyi`, `Platform_hengchangtong`, `Platform_qianfeng_ums512` / `Platform_qianfeng`,
  `Platform_nuoweida-dq-T5`), selected by string branches around the `zbt link info` handling. Which
  one is active on the reporter's actual unit is unknown from static analysis and would need a live
  log or a build-config string not found in this pass.
- Both `libzjL10001.so` builds carry an `ip_link_wait_client` / `p2p_link_socket_fd` path
  (`zlink ip_link_wait_client: Got the p2p link socket`) alongside the Bluetooth one — a second,
  Wi-Fi-Direct-based bring-up mechanism coexisting with `AA_bt_loop`, not investigated further since
  it's outside this brief's question but potentially relevant to `WifiDirectManager` if a future round
  compares ZLink's P2P handling to this repo's.

## Addendum (2026-08-12, same day) — found in the QF001 firmware, and a direct look at `gocsdk_zj`

Not a new brief, a follow-up on this same question, PC-only, against the firmware download at
`/home/oscar/Downloads/update/` (the same download `qf001-firmware-teardown-results.md` used).
Two parts: first, whether `libzjL10001.so` exists anywhere in that download outside the ZLink app
extractions above; second, once `gocsdk_zj` turned out to be reachable too, a direct look at it
rather than reasoning about it only from the original brief's description.

### Part 1 — `libzjL10001.so` is in the firmware, but it's not the runtime copy

The original firmware-teardown round only reconstructed `system.new.dat.br` and left
`vendor.new.dat.br` untouched ("out of scope"). Neither reconstruction survived on this machine
(that round's artifacts lived under `~/ohu-fixes-handoff/` on a different machine), so both were
rebuilt from scratch this round — brotli decompress + a local `sdat2img` reimplementation, same
method as the original round, no root/mount. `product.new.dat.br` and `socko.new.dat.br` were also
reconstructed for completeness, since the question was "is it anywhere in the download," not just
"is it on system or vendor."

**Found**: `/system/app/zlink5/lib/arm/libzjL10001.so`, bundled alongside the system app itself,
`zlink6-qianfeng-release-v6.1.06-2be5e6d6e_jg.apk`. Byte-identical to the copy inside that same APK's
own `lib/armeabi-v7a/` (sanity-checked). **It's a third, distinct build** — sha256 `4ca793ac…` / md5
`e13ab130…` — different from both `_600102` (`d67893fb…`) and `_600106_jg` (`abd80569…`) above.
Everything else in the download is clean: `vendor.new.dat.br` (stock UNISOC vendor blob — camera/
sensor/RIL HALs, five unrelated OEM apps), `product.new.dat.br` (Google apps, `AndroidAutoStub`,
one `fvcardvr` app), `socko.new.dat.br` (kernel `.ko` modules only), `boot.img`/`dtbo.img`/the
`.bin` files (no string hits for `zjL10001`/`zlink`/`zbt-main` anywhere) — nothing ZLink-related in
any of them.

**Why the hash differs, and why it doesn't retroactively invalidate anything above**: found the
actual boot script, `/system/bin/zlink5.sh`. It does `export
LD_LIBRARY_PATH=/data/data/com.zjinnova.zlink/lib` before looping `z-link -c qianfeng -ll`. So
`z-link`'s `dlopen("libzjL10001.so")` never resolves the `/system/app/zlink5/` copy at runtime at
all — it resolves out of the **installed app's own data directory**, which is exactly what the two
live-pulled extractions this report is built on already captured (at two different points after the
app had self-updated past this factory-shipped baseline). The `-c qianfeng` platform flag also
directly confirms the platform: this device runs ZLink's `Platform_qianfeng` code path (one of the
four named internally; see "Anything the brief did not ask about" above), settling something that
section flagged as unknown.

### Part 2 — `gocsdk_zj` is in the same firmware; looking at it directly complicates the answer above

`gocsdk_zj` (and a sibling, `gocsdk_lt` — same socket names, presumably a different chipset vendor)
sit in the same `/system/bin` this round already had open. Worth checking directly rather than only
reasoning about it through the original brief's secondhand description.

**`libzbt_rfcomm_data_send` is real, used-for-AA infrastructure — just not called by
`libzjL10001.so`.** `gocsdk_zj` `dlsym()`s *every one* of `libzbt-main.so`'s ~90 exported functions
(full fallback-logged list: `libzbt_init`, `libzbt_rfcomm_data_send`, `libzbt_rfcomm_data_recv_CB_init`,
`libzbt_hicar_rfcomm_data_send`, `libzbt_hicar_rfcomm_state_send`, and the rest — each with an
`"libzbt_init: dlsym <name> fail.."` fallback string), and its own strings directly describe managing
live AA/CarPlay Bluetooth connections: `"wireless android auto rfcomm connectted success"`,
`"aa already connected aa_connected_addr:%s"`, `"aa connected.., handle:%x."`,
`"SUPPORT_ZJ_CARPLAY, AA CONNECT"` / `AA DISCONN`, `"%s enter session_uuid: %s, out_rfcomm_port: %d,
out_local_bt_mac: %s"`. It also implements a full Realtek HCI transport (`transport_h5*`,
`rtk_handle_vender_mailbox_*`) and links `libbinder.so`/`libaudioclient.so`/`libmedia.so` (Android's
audio framework, matching its own `/data/goc/a2dp.pcm`, `apm_player.pcm` strings). This is a real
Bluetooth-profile daemon that owns the external module's serial port and demonstrably handles AA
connections — this part of the original hypothesis holds up better under direct inspection than "The
answer" above gives it credit for. **The earlier finding that `libzjL10001.so` itself never touches
`libzbt_rfcomm_data_send` is still correct** — it was just the wrong binary to check. `gocsdk_zj` is
the one that does.

**But the specific `0x105` / TCP `127.0.0.1:3152` / 16-byte-protobuf-frame claim does not match what
this `gocsdk_zj` build shows.** Its own strings reveal `bind`/`listen`/`accept`/`socket` as undefined
imports, and the paths it binds are **Unix domain sockets** — `/dev/socket/goc_rfcom`,
`/dev/socket/goc_spp`, plus a generic `/dev/socket/%s` — not a TCP socket. It has **zero protobuf-c
message descriptors of its own** (`nm -D --defined-only gocsdk_zj | grep -i protobuf` is empty),
unlike `libzbt-main.so` and `libzjL10001.so`, which both clearly are protobuf-c. `grep -l '3152'`
across **all 444 binaries** dumped from this firmware's `/system/bin` returns nothing. The one
`%d`-suffixed "port" string that exists (`out_rfcomm_port: %d`) is a Bluetooth RFCOMM channel number
(dynamically assigned per SPP session, single-digit range), not a TCP port — a different meaning of
"port" than the hypothesis's. None of this proves 3152/`0x105` is wrong — a hardcoded port used only
as an integer literal (e.g. `htons(3152)`) wouldn't show up in a `strings` scan regardless — but it's
a real discrepancy against what this exact binary statically shows, not merely an absence of
supporting evidence.

**The missing link — who actually connects to `gocsdk_zj`'s sockets — is still open, and this
firmware doesn't answer it.** Neither `libzjL10001.so` nor `z-link` reference `goc_rfcom`, `goc_spp`,
or `gocsdk` anywhere in their strings; `gocsdk_zj`/`gocsdk_lt` never reference `libzjL10001.so`'s own
`zj_bt_socket` path either. No custom Binder service registration was found in `gocsdk_zj` despite the
`libbinder` link (only a standard `android.bluetooth.IBluetoothPan` reference — the platform BT
stack, not a custom IPC surface to ZLink). A whole-image string search for `zj_bt_socket` across the
entire reconstructed `system.raw.img` returns exactly two hits, both inside `libzjL10001.so` itself —
its server-side counterpart isn't present anywhere in this firmware's `/system`, `/vendor`, or
`/product`. Given `libzjL10001.so` supports four OEM BT-module platforms internally and this exact
firmware runs `-c qianfeng`, the `hu_bt_data_send`/`zj_bt_socket` path flagged as the better-supported
candidate in "The answer" above may be **dead code for a different platform's hardware**, not active
on this unit — which would mean neither candidate this investigation has produced is actually
confirmed live on this hardware.

**Net position**: the general shape of the hypothesis (`libzbt_rfcomm_data_send` carries real AA
Bluetooth traffic) is now better supported than this report's main body suggests, via `gocsdk_zj`
rather than `libzjL10001.so`. The specific transport detail (`0x105`, TCP 3152, protobuf framing)
is not corroborated by this exact `gocsdk_zj` build and conflicts with what it statically shows
(Unix domain sockets, no protobuf-c). Static analysis of the files in hand is now exhausted — settling
either point needs one of: disassembly of `gocsdk_zj`'s socket dispatch loop (still blocked, no
`capstone` on this machine); a live capture on the actual rig (`lsof`/`netstat -x` on
`/dev/socket/goc_rfcom` while a real AA session connects would show the client process directly, no
disassembly required — the highest-value next step and a cheap one); or checking whether the original
`3152`/`0x105`/25-entry-message-table finding came from this same `gocsdk_zj` build or a different one
(e.g. pulled live off the reporter's device, which may be newer than this factory firmware baseline —
the same pattern already seen twice with `libzjL10001.so` itself).
