# ZLink teardown — which call carries Android Auto's Bluetooth bytes

Not a hardware round. No build, no APK install, no head unit, no phone. This is static analysis of a
ZLink extraction **already on the rig PC**, at `hur-wifi-test-scripts/extracted`. Everything runs on
the PC.

The clean-run protocol, capture rules and PASS/FAIL vocabulary in `TESTING-TEMPLATE.md` do not apply.
Report findings as facts. The house rule that does still apply is §5: inventory
`hur-wifi-test-scripts/` first and leave any script you write behind in it.

## 1. Why this round exists

A user's head unit (ROCO K706 / QF001) cannot do wireless Android Auto with our app, because the
phone pairs with a **second Bluetooth module** that Android never exposes. Our RFCOMM writes reach
the local stack and never reach the air — proven by a btsnoop capture with zero payload bytes in
either direction.

We have since worked out the route around it. A root daemon, `gocsdk_zj`, owns that module's serial
port and listens on `127.0.0.1:3152` speaking a 16-byte-framed protobuf protocol. We have the frame
layout and a 25-entry message table, recovered by disassembling the vendor library, and we are
building a client that speaks it so the Android Auto handshake can go over the module.

**One load-bearing assumption in that build has never been directly observed.** We believe message
id **`0x105`** is the channel that carries Android Auto's Bluetooth bytes. The evidence is strong but
all circumstantial: the vendor library builds a `0x105` frame from a raw pointer with no protobuf
packing, the daemon resolves both the send and the receive half of that one id, and on this hardware
ZLink's normal Bluetooth path is disabled yet the handshake still happens — so by elimination the
bytes went through the module. What we have never seen is the code that actually *sends* them.

**That code is in `libzjL10001.so`, and the rig has a copy.** It is ZLink's native core — the thing
`z-link` loads at boot — and it is the reference implementation of exactly the client role we are
writing. Its log strings (`AA_bt_loop`, `send_WifiVersionRequest`) appear in vendor logs we hold, but
we have never had the binary. The vendor build server we used to pull it from has since been put
behind a login, so this extraction is now the only copy we can reach.

**The question:** when ZLink sends `WifiVersionRequest` to a phone over the external module, which
library call does it go out through? If it is `libzbt_rfcomm_data_send`, our `0x105` is right and the
transport we are building is on the correct id. If it is something else, we are building the wrong
thing and need to know what the right thing is before it ships to the reporter.

## 2. What is different about this round

- Nothing touches the head unit or the phone. No verdicts; report facts.
- **Vendor binaries stay on the rig.** Do not commit `.so`, `.apk`, `.zip` or `.dex` files to this
  branch or any other. This repo's fork is public and a previous round had to have seven such files
  rewritten out of its history. Commit **text only**: symbol lists, `strings` output, checksums,
  and your notes. That rule is already in `qf001-firmware-teardown-brief.md` §7 and it applies here.
- If the extraction turns out not to contain `libzjL10001.so`, say so early — §3 tells you what else
  is worth having and §7 is still worth doing.

## 3. Inventory first, then stop and look

Do this much before extracting or analysing anything.

```bash
cd hur-wifi-test-scripts/extracted
ls -la
find . -maxdepth 4 -iname '*.so' | head -50
find . -maxdepth 4 -iname '*.apk' -o -maxdepth 4 -iname 'mapping.txt' | head
```

Four things are worth having. Record which are present, their paths, sizes, `file` output and
`sha256sum`:

| File | Why |
|---|---|
| **`libzjL10001.so`** | **the target.** ZLink's native core; holds the WPP handshake |
| `libzbt_core.so` | the JNI bridge; maps Java method names onto the C API |
| `libzbt-main.so` / `libzbt-main-64.so` | the protocol library. We already hold both — checksum only, to confirm this extraction matches |
| `mapping.txt` | R8 deobfuscation map, if the extraction came from a build zip rather than a device |

If the extraction is an unexploded APK or zip, list it before unpacking and pull out only what you
need — `unzip -l`, then `unzip -o <archive> 'lib/*' -d ./lib`. Native libraries live under
`lib/armeabi-v7a/` or `lib/arm64-v8a/`; take whichever is present, and note if both are.

Checksums we already hold, for comparison:

```
8981b42b10540d886885c2255afb517f  libzbt-main-64.so   (md5)
1652e42b2abbebf287bd7859c26cfacd  libzbt-main.so      (md5)
```

## 4. The decisive check — imported symbols

This is the single most valuable output of the round, and it is three commands.

```bash
cd <wherever libzjL10001.so is>

# Everything it needs from elsewhere. The libzbt_* names are the ones that matter.
nm -D --undefined-only libzjL10001.so | grep -i zbt

# What it links against at load time
readelf -d libzjL10001.so | grep -i needed

# What it resolves at runtime instead of linking
strings -a libzjL10001.so | grep -oP 'dlsym\s*\K\w+' | sort -u
strings -a libzjL10001.so | grep -oP 'lib\w+\.so' | sort -u
```

**Report the full `libzbt_*` list verbatim.** Then answer the question directly by looking for these
four names in it:

| Symbol | What its presence means |
|---|---|
| `libzbt_rfcomm_data_send` | **the answer we expect.** Message `0x105`, outbound. Our build is on the right id |
| `libzbt_rfcomm_data_recv_CB_init` | `0x105` inbound. Expect this alongside the above — the pipe is symmetric |
| `libzbt_zj_rfcomm_data_send` | message `0x121`, a *different* vendor channel on port 57677. If the AA bytes go here instead, our whole transport is aimed at the wrong port and id |
| `libzbt_hicar_rfcomm_data_send` | the HiCar family. Presence alone is fine — ZLink does HiCar too. It only matters if it turns out to be the one carrying Android Auto |

Note that ZLink may `dlopen` the library rather than link it, in which case `nm -D` shows nothing and
the `dlsym` string scan above is where the names appear. Run both; report both, including the empty
one.

## 5. Tie the send call to the Android Auto handshake

§4 says which channels ZLink *can* use. This step says which one Android Auto's handshake actually
goes down.

```bash
strings -a libzjL10001.so | grep -nE 'AA_bt_loop|send_WifiVersionRequest|bt_aa_data_recv|AA_wait_port|AA_wait_wifi_ready|wireless_AA_loop|WifiStartRequest|WifiInfoResponse'
```

Those strings are log lines from a real ZLink run we hold, so they should be present. Report what
comes back.

Then cross-reference — find what code references `send_WifiVersionRequest` and disassemble around it,
looking for a call to whichever symbol §4 turned up:

```bash
uvx --with capstone python3 <path>/xref_gocsdk.py libzjL10001.so --string "send_WifiVersionRequest"
uvx --with capstone python3 <path>/xref_gocsdk.py libzjL10001.so --string "AA_bt_loop"
```

`xref_gocsdk.py` is our own tool — it finds what references a string in a stripped ARM ELF and
disassembles around it, annotating PC-relative addresses with the text they point at. It is in the
handoff folder, not this repo; **if you cannot find it on the rig, say so and skip this step** — §4
alone answers most of the question. It was written for 32-bit ARM; if the library you have is 64-bit
it may need adjusting or may simply not work. That is an acceptable outcome, reported as such.

Two warnings, both learned the hard way on this hardware:

- **The rig's `objdump` is almost certainly x86-only and cannot disassemble ARM at all.** It will
  produce confident garbage rather than an error. Do not use it. `nm`, `readelf` and `strings` read
  ARM binaries fine — it is only disassembly that needs `capstone`.
- **A `mov` that sets a register is often several instructions above the `ldr` that reads it.**
  Reading the load without looking for the store is precisely the mistake that cost this
  investigation an entire reporter round: the message id was written into a stack slot four
  instructions into the function and loaded much later, and we concluded the senders carried no ids
  at all. If you find a call site, quote a generous window around it, not two lines.

## 6. What `libzbt_core.so` adds, if present

Smaller job, worth doing if the file is there. It is the JNI bridge, so its exported names map ZLink's
Java methods onto the C API and confirm which Java call reaches which message:

```bash
nm -D --defined-only libzbt_core.so | grep Java_
nm -D --undefined-only libzbt_core.so | grep -i zbt
```

Report both lists whole. We have inferred this mapping from the Java side alone and would like it
confirmed from the native side.

## 7. The Java half, if the APK is there

We decompiled ZLink's `ZBTService` in a previous round but the decompiler was never re-run for its
seven inner callback classes, and the APK it came from is gone. Those seven are where the actual
work happens — the byte pump, the reconnect handler, the service registration — and we have only
their names.

If the extraction includes the APK:

```bash
uvx --with androguard python3 <path>/dad.py <the.apk> ./deob 'ZBTService' 'com\.zjinnova\.jni\.Zbt'
```

`dad.py` is in the handoff folder alongside `xref_gocsdk.py`; same caveat, skip and say so if it is
not on the rig. What we want out of it are the seven classes named
`ZBTService$registerLiveData$1` through `$7` (they land as files with `_` in place of `$`). Commit
the resulting `.java` files as text — those are decompiler output, not vendor binaries, and the
previous round already committed the same kind of file.

## 8. Reporting back

`zlink-wpp-channel-results.md`, on this branch, alongside this brief. Not the template's PASS/FAIL
format — use this shape:

```markdown
# ZLink teardown — WPP byte channel results

**Date:** <yyyy-mm-dd>
**Extraction:** <path, and where it originally came from if you know>

## Inventory
<the §3 table: file, path, size, `file` output, sha256. Say plainly which of the four are absent.>

## The answer
<One sentence: which libzbt_* call carries Android Auto's Bluetooth bytes, or "could not be
determined" and why.>

## Imported symbols
<the full libzbt_* list from nm -D and the dlsym scan, verbatim, including empty results>

## Handshake strings
<§5 grep output>

## Cross-reference
<§5 disassembly if it ran, with a generous window around any call site; or why it did not>

## libzbt_core.so / Java classes
<§6 and §7 if they applied>

## Setup notes
<Every deviation from this brief, every command that did not exist or work on the rig, every error
in the brief itself. This section has been worth more than some rounds' actual runs.>

## Anything the brief did not ask about
<Things noticed in passing.>
```

**Absence is a real result here.** "The extraction has no `libzjL10001.so`" and "`nm -D` returns
nothing because it is dlopened" are both useful answers, reported plainly, and neither is a failed
round. What is not useful is a guess presented as a finding — if the disassembly is unreadable or the
tool would not run, say that instead of inferring from the strings alone.
