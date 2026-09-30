# QF001 firmware teardown — extracting the OEM Bluetooth app

Not a hardware round. No build, no APK install, no head unit involved. This is a file-extraction and
static-analysis task against a stock firmware image already downloaded on the rig PC, and everything
in it runs on the PC alone.

**The whole point is to be scoped.** The archive is ~2.3 GB. Do not unpack it wholesale and do not
mount anything. `debugfs` reads named files straight out of an ext4 image without mounting it, and
that is the only extraction technique this brief uses. Expected disk cost is a few hundred MB for one
intermediate image, not another copy of the firmware.

## 1. What this is and why it exists

A user's head unit (ROCO K706 / QF001, Unisoc `ums512`, Android 10) cannot do Bluetooth-based
wireless Android Auto with our app. The reason is settled and is not a defect in our code: the phone
pairs with a **second, OEM Bluetooth module** (`CAR8032`) that Android does not expose to apps. A
btsnoop capture on that unit shows our RFCOMM channel opening with credits granted and then carrying
zero payload bytes in either direction — the head unit's own stack never puts our writes on air. The
vendor's own app sidesteps this by not using `android.bluetooth` at all on this hardware.

We already detect these units and say so instead of retrying forever (`aap/ExternalBtPolicy`, on
`main`). The open question is whether we can go further and *drive* that OEM module ourselves.

On-device probing of the user's unit found the likely owner of that module: **`com.qf.bluetooth`**,
a system-uid app running since boot that holds no network socket at all. On Android, a system service
with no socket is reached over Binder. If it exposes a bindable interface we may have a route; if it
is locked behind a signature permission we do not, and the answer for that hardware is "USB or a
WiFi mode that needs no Bluetooth handshake", which the app already tells the user.

**That single question is what this teardown answers.** There is no public documentation for any
`com.qf.*` service anywhere, the vendor publishes nothing, and the one community project that
integrates with QF system apps works around them rather than calling them. The APK is the only
source of truth, and firmware on the rig is the only copy reachable without asking the reporter for
more.

## 2. What is different about this round

- Nothing here touches the head unit or the phone. The clean-run protocol, capture rules and verdict
  vocabulary in `TESTING-TEMPLATE.md` do not apply. Report findings as facts, not PASS/FAIL.
- Escalate rather than improvise if the container turns out not to be one of the three shapes in §3.
  Unisoc firmware also ships as a `.pac` for the vendor download tool, which needs a separate
  extractor and is out of scope for this brief.
- **Check free space before starting**: `df -h .` — you need roughly the size of `system.img`
  (typically 2 to 4 GB unsparsed) plus a little. If that is not available, stop and say so.

## 3. Identify the container first, then stop

Do this much and look at the output before extracting anything. The rest of the brief branches on it.

```bash
ls -l <the downloaded file>
file <the downloaded file>
unzip -l <the downloaded file> | sort -k1 -n -r | head -30
```

Three shapes are likely. The listing tells you which:

| What you see in the listing | What it is | What to do |
|---|---|---|
| `system.img` (large), maybe `vendor.img`, `boot.img` | classic image set | §4, path A |
| `super.img` (very large), no `system.img` | dynamic partitions | §4, path B |
| `payload.bin` | A/B OTA payload | §4, path C |
| none of the above, or a `.pac` | Unisoc download-tool package | **stop and report** |

Extract only the one entry you need, never the whole archive:

```bash
unzip -o <archive> system.img -d ./fw          # or super.img / payload.bin
```

## 4. Get a raw ext4 `system.img`

**Path A — sparse image.** `file` will say "Android sparse image". Convert it:

```bash
simg2img ./fw/system.img ./fw/system.raw.img     # apt: android-sdk-libsparse-utils
file ./fw/system.raw.img                         # want: "Linux rev 1.0 ext2/ext3/ext4 filesystem"
```

If `file` already says ext4, it is not sparse — skip the conversion and use it directly.

**Path B — `super.img`.** Unsparse it first if needed, then split out the system partition:

```bash
simg2img ./fw/super.img ./fw/super.raw.img
lpunpack --partition=system ./fw/super.raw.img ./fw     # from android-sdk-libsparse-utils / lpunpack
```

**Path C — `payload.bin`.** Extract only the system partition:

```bash
payload-dumper-go -partitions system -output ./fw ./fw/payload.bin
```

If none of the tools above are installed and cannot be installed, say so and stop — do not
substitute a mount, a loop device or `sudo`.

## 5. The scoped extraction — `debugfs`, not mount

`debugfs` is in `e2fsprogs` and is almost certainly already present. It reads the image read-only and
touches only what you name. **Nothing below needs root.**

The image's root may be `/` = `/system` (older layout) or contain a `system/` directory
(system-as-root). Find out which, then use whichever prefix works for every command after:

```bash
IMG=./fw/system.raw.img
debugfs -R "ls -l /" "$IMG"
debugfs -R "ls -l /app" "$IMG"          # if this works, prefix is empty
debugfs -R "ls -l /system/app" "$IMG"   # if this works instead, prefix is /system
```

**Find the two apps.** They may be in `app/` (ordinary) or `priv-app/` (privileged), and **which one
matters to the conclusion** — record it either way:

```bash
debugfs -R "ls -l <prefix>/app" "$IMG"      | grep -i -E "qf|blue"
debugfs -R "ls -l <prefix>/priv-app" "$IMG" | grep -i -E "qf|blue"
```

**The daemon binary is now the priority target.** On-device probing since this brief was written
found the process that actually drives the external Bluetooth module: a root daemon
`/system/bin/gocsdk_zj`. It is the only process on the unit that has the vendor library mapped, it
holds `/dev/ttyS2` (the module's serial line), and it listens on TCP ports 3152 and 57677. Talking to
it over a socket would need no root, no system install and no signature, so **it outranks the APKs**.
Get these three first:

```bash
mkdir -p qf001-artifacts

debugfs -R "dump <prefix>/bin/gocsdk_zj      qf001-artifacts/gocsdk_zj"      "$IMG"
debugfs -R "dump <prefix>/lib/libzbt-main.so qf001-artifacts/libzbt-main.so" "$IMG"
debugfs -R "ls -l <prefix>/lib" "$IMG" | grep -i -E "zbt|goc"   # dump any others this names
```

**Get the client as well, and prefer it.** A live capture since confirmed that a second binary,
`z-link`, is the thing that actually talks to the daemon on port 3152, and that it does so
**without the vendor library loaded** — it holds nothing but a socket. That makes it proof the wire
format is implementable by an ordinary process, and a client implementation is usually a much easier
read than a server. Its path is not known; it is not necessarily under `bin/`, and the vendor's
library search path mentions a `/usr/z-loader` tree, so look in both places:

```bash
debugfs -R "ls -l <prefix>/bin" "$IMG" | grep -i -E "z-link|z-mdnsd|z-loader|goc"
debugfs -R "ls -l /usr/z-loader/bin" "$IMG"     # may not exist; not an error if it does not
debugfs -R "dump <prefix>/bin/z-link qf001-artifacts/z-link" "$IMG"
```

If `z-link` is not on this partition it may live on `vendor` or `oem`, which this brief does not
unpack. Say so rather than going after it.

**Skip the two APKs.** The reporter pulled `QF_Bluetooth.apk` and `QF_Framework.apk` off his own unit
and they have already been read here. Their manifests are settled: all three services are
`exported="true"` with no `permission` attribute and no custom permissions declared, so binding them
needs nothing special — but the interface behind them is a hands-free phone API (dial, hang up,
contacts, call history, module name and state) with no route for arbitrary bytes, and the dex
contains no `rfcomm`, no `spp` and no Android Auto RFCOMM UUID. Nothing further is needed from them,
so do not spend the round re-extracting or decompiling them.

**And the permissions XML.** Lower value now that the APKs are read, but cheap. List the directory
first and dump every file whose name contains `privapp`:

```bash
debugfs -R "ls -l <prefix>/etc/permissions" "$IMG"
debugfs -R "dump <prefix>/etc/permissions/<name>.xml qf001-artifacts/<name>.xml" "$IMG"
```

If any single one of these is missing, that is a finding rather than a blocker. Carry on with the
rest and say which was absent.

## 6. Read the manifest — `aapt2`, no JDK needed

The rig already has the Android SDK, so `aapt2` is under `build-tools/<version>/`. This is the
fastest route and needs nothing installed:

```bash
AAPT2=$(ls -d "$ANDROID_HOME"/build-tools/*/aapt2 2>/dev/null | tail -1)
"$AAPT2" dump xmltree --file AndroidManifest.xml qf001-artifacts/QfBluetooth.apk > qf001-artifacts/QfBluetooth-manifest.txt
"$AAPT2" dump permissions qf001-artifacts/QfBluetooth.apk
```

If `aapt2` cannot be found, `apkanalyzer manifest print <apk>` or androguard
(`uvx --with androguard python -c "..."`) will do the same job. Say which you used.

## 7. What to report back

**Superseded — do not commit vendor binaries to this branch.** This instruction was followed once
and the extracted files sat in `qf001-artifacts/` on a public AGPL repo for four days. They are
Shenzhen Jitu and Google firmware content that neither of us has any right to redistribute, and they
were removed on 2026-08-12 by rewriting this branch. Nothing was lost: every file is held in
`~/ohu-fixes-handoff/qf001-artifacts/` on the analysis machine, verified byte-identical before the
rewrite, with a second copy and checksums in `qf001-artifacts-backup-20260812/`.

For any future round that needs firmware content: **report what the analysis needs — `file` output,
`strings` greps, `readelf` tables, checksums — rather than the binaries themselves**, and hand the
files over out of band if the raw bytes are genuinely required. A checksum is usually the whole
answer, because it establishes whether the unit's copy matches one already held.

Still true and unchanged: **do not commit `system.img`, `super.img` or the firmware archive.**

Then answer these, in a `qf001-firmware-teardown-results.md`:

0. **`file`** on every binary you pulled, and a strings sweep of each:

   ```bash
   file qf001-artifacts/*
   strings -a qf001-artifacts/gocsdk_zj | grep -iE "zbt|packet|head|magic|3152|port" | head -60
   strings -a qf001-artifacts/z-link    | grep -iE "zbt|packet|head|magic|3152|port" | head -60
   ```

   `gocsdk_zj` should be a 32-bit ARM ELF, since it loads a 32-bit library. **What we are hunting is
   the frame header that wraps each protobuf message on port 3152** — a magic value, a length field,
   a message-id field, and their order. The port itself is already settled: a live projection session
   showed `z-link` holding `127.0.0.1:3152` open, with 57677 unused, so no more port hunting is
   needed. Anything resembling `packet_zbt_head`, `libzbt_package_send`, `libzbt_recv_head` or
   `libzbt_recv_body` is worth quoting in full with its surrounding lines. **This is the highest-value
   answer in the round.**

1. **Any init script that starts the daemon**, which names its arguments and therefore often its
   port. Search the image for it:

   ```bash
   debugfs -R "ls -l <prefix>/etc/init" "$IMG" | grep -iE "goc|zbt|bt"
   ```

   Dump anything that matches and quote the `service` block verbatim.

2. **Whether `libzbt-main.so` was in the image**, and `file qf001-artifacts/libzbt-main.so` output.
   We expect 32-bit ARM; confirming from a second source closes that question.

3. **Whether a `privapp-permissions` XML names `com.qf.bluetooth`**, and if so which permissions it
   grants. Low priority.

4. The firmware's build id: `debugfs -R "dump <prefix>/build.prop -" "$IMG" | grep -E "ro.build.(display|version)"`.

## 8. Do not do

- Do not mount the image, use a loop device, or run any of this under `sudo`.
- Do not unpack the full archive or the full `system.img`.
- Do not flash anything to the head unit. Nothing in this brief goes near the device.
- Do not decompile beyond the manifest. If the manifest shows a bindable service, the next round
  pulls its AIDL surface, and that is a separate brief.
