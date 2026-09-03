# QF001 firmware teardown — results

Not a hardware round; PC-only, against `qf001-firmware-teardown-brief.md`. Findings, not PASS/FAIL.
The brief changed twice while this round was running (APK half dropped, then the port settled and
`z-link` added as a target) — this report is against the final version, commit `e6e73959`.

## Setup notes — container did not match §3

The downloaded artifact at `/home/oscar/Downloads/update/` was not one of the brief's three listed
shapes (no `system.img`, `super.img`, or `payload.bin` — it's an already-extracted OTA package
directory). Escalated per the brief's own rule instead of improvising silently; the user confirmed
proceeding. What it actually was: a **block-based OTA transfer set** —
`system.new.dat.br` (brotli-compressed, 1.61 GB) + `system.transfer.list` + an empty
`system.patch.dat`. Reconstructed with tools outside the brief's prescribed set (none needed root,
mount, or sudo):

1. `pip install --user brotli`, then decompressed `system.new.dat.br` → `system.new.dat` (2.74 GB).
2. Reimplemented the standard `sdat2img` block-copy algorithm locally (no code fetched from the
   network) — reads `system.transfer.list`'s `new` commands and copies the matching blocks from
   `system.new.dat` into a flat output file at the right offsets. `erase`/`zero` commands are no-ops
   for reconstruction since the output starts zero-filled.
3. Result: `system.raw.img`, 2.75 GB, `file` reports a valid ext4 filesystem directly — no `simg2img`
   conversion needed, this format never produces an Android sparse image.

From there, followed the brief exactly: `debugfs`, no mount, no root. Root prefix is `/system` (`/app`
does not exist, `/system/app` does — `/bin`, `/etc` etc. exist at `/` too, but only as symlinks into
`/system`).

`system.img`/`system.raw.img`/`system.new.dat*` were **not** committed (multi-GB, explicitly excluded
by the brief). The two OEM APKs were extracted and read locally (useful independent corroboration —
see the note at the end) but **not** committed either, per the brief's "don't spend the round on them"
once the second commit landed; committing them now would just be bytes nobody asked for.

## 0. Binaries pulled, `file`, and strings sweeps

```
qf001-artifacts/gocsdk_zj:          ELF 32-bit LSB, ARM, EABI5, dynamically linked, stripped
qf001-artifacts/z-link:             ELF 32-bit LSB, ARM, EABI5, dynamically linked, NOT stripped
qf001-artifacts/libzbt-main.so:     ELF 32-bit LSB, ARM, EABI5, dynamically linked, stripped
qf001-artifacts/libzbt-main-64.so:  ELF 64-bit LSB, ARM aarch64, NDK r21b, stripped (bonus, not asked)
```

`gocsdk_zj` is 32-bit as predicted. `z-link` — found at `/system/bin/z-link`, 6.9 KB — is real and
present, matches the brief's description of holding no vendor library (`readelf -d` shows only
`libc.so`/`libdl.so` as `NEEDED`), and being unstripped, its full symbol table reads directly (no
`strings`-guessing needed).

**But `z-link`'s actual job contradicts the "network client" framing.** Its complete symbol list is
process/file utilities only — `find_pid_by_name`, `check_process_by_name`, `run_process`, libc
`system()`, `opendir`/`readdir`/`readlink` — **there is no `socket`, `bind`, `connect`, `listen`,
`send`, or `recv` anywhere in its import table.** It cannot itself be the process holding
`127.0.0.1:3152` open. What it does do: `setprop rw.zlink.channel <n>`, then `dlopen("libzjL10001.so")`
— a **third** vendor library, distinct from `libzbt-main.so`, that this partition does not contain
(checked `/system/lib` — 384 entries, no match; also checked `/system/lib/hw` and `/system/app-lib` —
absent). So `z-link` is a thin launcher/supervisor: it starts or hands off to a process that loads
`libzjL10001.so`, and *that* library is almost certainly where the actual socket and frame-header
code the brief is hunting for lives. It is not recoverable from `system.img` — most likely on
`/vendor` (confirmed empty on this partition; `/vendor.new.dat.br` was never touched, out of scope
per the brief) or written to `/data` at runtime by an installer this round didn't run.

Strings sweep for `zbt|packet|head|magic|3152|port` against `z-link`: **zero matches** — consistent
with it not carrying the protocol code. Against `gocsdk_zj`: also zero *direct* hits for those terms,
because (as found in the prior pass of this round) `gocsdk_zj` doesn't link the protocol code either —
it `dlopen()`s `libzbt-main.so` at runtime the same way `z-link` reaches for `libzjL10001.so`. That
library **is** on this partition (`/system/lib/libzbt-main.so`) and is where the real find is:

```
libzbt-main: packet_zbt_head fail...
libzbt-main: libzbt_recv_head npack_zbt_head fail..
```

Frame-header struct/field names, confirmed: `packet_zbt_head` (send side), `npack_zbt_head` (receive
side). Both stripped, so no byte layout beyond the names — that needs disassembly, out of scope here.

`libzbt-main.so` is a `protobuf-c`-generated client (`PROTOBUF_C__MESSAGE_DESCRIPTOR_MAGIC` /
`..SERVICE_DESCRIPTOR_MAGIC` present) exposing a `zj__zbt__*` message family. Relevant to the AA/CarPlay
bridge: `carlink_data`, `req_link_phone`, `phone_link_state`/`phone_link_state2`, `connect_status`,
`open_status`, `close_status_send`, `request_reconn`, `request_init`, `init_info`, `link_info`/
`link_info2`, `local_bt_info`; HiCar-specific `hicar_service_register(_done)`, `hicar_service_data`,
`hicar_service_handle`, `hicar_service_link_changed`, `hicar_ble_start`; and — notable, same shape as
our own AAP input channel — `touch_data`, `multi_touch`, `hid_screen_info`.

Plain-string corroboration that this is the right code path, from `gocsdk_zj`:
```
SUPPORT_ZJ_CARPLAY, CP CONNECT / AA CONNECT / CP DISCONN / AA DISCONN
wireless android auto rfcomm connectted success
aa already connected aa_connected_addr:%s
%s enter session_uuid: %s, out_rfcomm_port: %d, out_local_bt_mac: %s
```

**Net for item 0**: the frame header's field names are settled (`packet_zbt_head` /
`npack_zbt_head`), but the binary that actually implements them turned out to be `libzbt-main.so`
loaded by `gocsdk_zj`, not `z-link` or its `libzjL10001.so` — that third library is off this
partition entirely. Getting the byte layout needs either disassembling `libzbt-main.so` (stripped,
but disassemblable — a plain follow-on task) or finding `libzjL10001.so` on `/vendor`.

## 1. Init script for the daemon

**Not found on this partition, and it's provably not going to be.** `/system/etc/init/*.rc` (77
files) has no `goc`/`zbt`/`bt`-named entry, and dumping `/init.rc` and grepping for `gocsdk`/`zbt`
also comes back empty. `/init.rc` itself explains why: it does
`import /init.${ro.hardware}.rc` and `import /vendor/etc/init/hw/init.${ro.hardware}.rc` — and
`ro.product.hardware=uis7862s_1h10` (from `build.prop`) tells us the filename, but the root directory
only has the **recovery** variant (`init.recovery.uis7862s_1h10.rc`), not `init.uis7862s_1h10.rc`
itself. That file lives in the boot ramdisk (standard Android layout) or on `/vendor` — both outside
what this brief extracts (`boot.img` was left alone per §8; `/vendor` came back empty on this
partition, confirming it's genuinely a separate image, not merged). A finding, not a blocker, per the
brief's own rule for a missing target.

## 2. `libzbt-main.so` presence and architecture

Present, `/system/lib/libzbt-main.so`, 32-bit ARM as expected (full `file` output above). Bonus:
`/system/lib/libzbt-main-64.so` also exists (64-bit, NDK r21b) — not asked for, not investigated
further.

## 3. `privapp-permissions` XML

`com.qf.bluetooth` is named in `/system/etc/permissions/privapp-permissions-platform.xml` (one entry
among many OEM packages, no dedicated `com.qf.*`-only file), granting exactly:
```xml
<privapp-permissions package="com.qf.bluetooth">
    <permission name="android.permission.BLUETOOTH_PRIVILEGED"/>
    <permission name="android.permission.MOUNT_FORMAT_FILESYSTEMS"/>
</privapp-permissions>
```
`privapp-permissions-google-system.xml` has no `com.qf.*` entries.

## 4. Firmware build id

```
ro.build.display.id=QF001.20260720.114610
ro.build.version.release=10          (SDK 29)
ro.build.version.security_patch=2020-06-05
ro.build.version.incremental=55446
```

## Note: independent read of the two APKs (not part of the final brief, kept brief)

Before the second brief update dropped this requirement, both OEM APKs were pulled and their
manifests read locally. Worth recording as a second, independent source rather than discarding:
it matches the brief's own description exactly — all three of `com.qf.bluetooth`'s services
(`TechBTService`, `BtMainService`, `TestStartBtService`) are `exported="true"` with no
`android:permission` attribute, the app defines zero `<permission>` elements of its own, and there
are zero `<receiver>` entries. `com.qf.bluetooth`'s `privapp-permissions` grant (item 3 above) is the
only gate involved, and neither of its two permissions guards component access. Not committed, per
the current brief.

## Where this leaves the open question

Manifest-level: binding `com.qf.bluetooth`'s services needs nothing special (confirmed twice now,
independently). Protocol-level: the frame header's name is known
(`packet_zbt_head`/`npack_zbt_head`) and the port is settled (`127.0.0.1:3152`, per the live capture
in the brief), but the code that actually implements the header — `libzjL10001.so`, reached via
`z-link` — is not on `system.img`. The clearest next step if this continues is disassembling
`libzbt-main.so` (present, if stripped) for the header layout, since it's the one implementation of
this protocol this round could actually get hold of.
