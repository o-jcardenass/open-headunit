# ZLink teardown — response to the round's results

Written after reading `zlink-wpp-channel-results.md` and its Addendum, and re-checking the two
binaries it reasons about against the copies held here.

**Short version: the round was worth running, two of its findings are keepers, and the apparent
conflict with `0x105`/TCP 3152 is a wrong-binary artifact — the same class of error the Addendum
itself catches and corrects once. Nothing here needs to change in the transport. But the "cheapest
remaining lever" it proposes would capture the wrong socket, which is the one thing worth stopping
before the next round starts.**

## 1. The port and the framing were never open questions — they are measured

This did not come across in the brief, and that is the brief's fault. It asked about the *byte
channel id* and gave the impression the whole transport was resting on static analysis. It is not.

A client built from `PROTOCOL.md` alone connected to `127.0.0.1:3152` on **the reporter's actual
head unit** and was answered, twice:

- 2026-08-11: `TX 0x101 RequestInit{id=1, enable_type=2}` → **7 frames in 17 ms** —
  `0x102 0x10c 0x104 0x109 0x115 0x10c 0x102`.
- 2026-08-12: **byte-identical** set (`HUR_Log_20260812_174617_502.txt`).

Those frames decode field-for-field against `zbt.proto`: `InitInfo` carrying the module's real MAC
and name (`CAR8032`), `LinkInfo2` naming the phone (`HONOR Magic8 Lite`, `C0572476D74F`). The unit
tests in this repo decode those exact captured bodies.

So the magic, the version, the 16-byte frame, the message ids and `enable_type = 2` are **measured on
the target hardware**, not inferred. A static read of a factory-baseline binary cannot overturn a
live exchange with the running one. Anything in the results that reads as "3152 may be wrong" should
be read as "3152 is not visible in this binary", which is a different and much weaker claim — and
§2 explains why it is not visible.

## 2. Why `gocsdk_zj` shows no TCP: the socket layer is in the library, not the daemon

Checked here against `qf001-artifacts/`:

```
$ nm -D --undefined-only libzbt-main.so | grep -E "socket|bind|listen|accept|connect|inet_addr"
    U accept        U bind        U connect     U inet_addr
    U listen        U recv        U send        U socket

$ nm -D --defined-only libzbt-main.so | grep -E "Fox"
0001472c T FoxClientConnect     000146d4 T FoxClientInit
0001464c T FoxClientRecvData    00014690 T FoxSendData2Server
000142dc T FoxServerInit        000144f4 T FoxServerWait
0001462c T FoxServerClose       0001481c T FoxClientDeinit
```

**`libzbt-main.so` owns every socket call and exports both halves — client *and* server.**
`gocsdk_zj` contains no `Fox` string at all (`strings -a gocsdk_zj | grep -oP 'Fox\w+'` is empty),
so it never names them; it `dlsym`s only the `libzbt_*` protocol API, and the library does the socket
work internally when the daemon calls `libzbt_init`.

That is exactly the pattern the Addendum already identified for `libzbt_rfcomm_data_send` —
*"it was just the wrong binary to check"* — applied one level further. Looking for `bind`/`listen`
in `gocsdk_zj` finds nothing for the same reason looking for `libzbt_rfcomm_data_send` in
`libzjL10001.so` found nothing.

Two smaller ones in the same family:

- **`grep -l '3152'` finding nothing is expected and proves nothing.** `PROTOCOL.md` already records
  that the port is a *parameter* — `FoxClientConnect(int fd, uint16_t port)`, `htons` applied inline
  — chosen by the caller, not a constant in the library. An integer literal never appears in a
  `strings` scan. The results note this caveat themselves and then still weigh the absence.
- **"Zero protobuf-c descriptors in `gocsdk_zj`" is a previously recorded finding, not a new
  conflict.** From the handoff, 2026-08-09: *"`gocsdk_zj` turns out to contain none of the protocol
  (zero protobuf-c descriptors, no `MESSAGE_ZBT_*`, no `libzbt` in `DT_NEEDED`); it `dlopen`s the
  library and drives a 24-entry API."* The daemon has no protobuf **because the library does all the
  packing**. That is corroboration.

## 3. `/dev/socket/goc_rfcom` is the CarPlay surface — do not capture it

This is the one actionable correction, and the reason this file exists rather than a note in a
future brief.

```
$ strings -a gocsdk_zj | grep -i iap
iap uart Try to receive client(%d)'s command.
accepted a iap uart client: %d
uart iap bt_iap_connect start...
is_iap_connected = %d is_aa_rfcomm_connected=%d
```

`iap` is the **iPod Accessory Protocol** — Apple/CarPlay. `goc_rfcom` and `goc_spp` are the daemon's
iAP/SPP client surface, alongside its Realtek HCI transport. They are a *different* interface from
the ZBT channel, on a daemon that plainly serves several. `is_iap_connected = %d
is_aa_rfcomm_connected=%d` is the daemon tracking both at once.

So `lsof`/`netstat -x` on `/dev/socket/goc_rfcom` during an Android Auto session would most likely
show nothing, or show CarPlay plumbing, and would be read as a negative result about Android Auto.
It would cost a round and produce a misleading answer. **`netstat -tnp | grep 3152` during a live AA
session is the equivalent capture on the right interface** — and round 1 of this investigation
already ran it, finding the vendor's own `z-link` holding `127.0.0.1:37766 → 3152` for the session's
duration.

## 4. What the round genuinely established, and it is worth having

Both of these are new and neither was reachable from what was on disk here:

1. **`gocsdk_zj` `dlsym`s the entire `libzbt-main.so` export set and handles live Android Auto RFCOMM
   connections** — `"wireless android auto rfcomm connectted success"`,
   `"aa already connected aa_connected_addr:%s"`. The Addendum's own conclusion, and it strengthens
   the hypothesis rather than weakening it.
2. **`libzjL10001.so` carries a complete, private `zj.AA.*` protocol implementation and never calls
   `libzbt-main.so`.** Genuinely new, and it settles that ZLink's AA *protocol* is its own.

### Why (2) does not disconfirm `0x105`

The results' central argument is that `libzbt_rfcomm_data_send`'s protobuf family (`zj.zbt.*`) has no
AA Wi-Fi message, so it cannot be what sends `WifiVersionRequest`.

**`0x105` carries no protobuf at all.** That is its defining property, and the reason it is the
interesting id: `libzbt_rfcomm_data_send` builds a `0x105` frame from the caller's raw pointer and
length with **no `__pack` call anywhere in the function**, and the receive dispatcher hands a `0x105`
body straight to the callback with no unpack. It is an opaque byte pipe.

So the expected architecture is exactly what the round found: `libzjL10001.so` packs
`zj.AA.WifiVersionRequest` itself, and hands the resulting **bytes** to a byte channel that neither
knows nor cares what they are. `zj.zbt.*` having no AA message is what a raw pipe looks like, not
evidence against one.

## 5. What is actually still open

Narrower than the results suggest, and unchanged by this round:

> Does ZLink hand its packed AA bytes to the ZBT channel (`0x105`), or to
> `hu_bt_data_send()` / `/dev/socket/zj_bt_socket`?

`hu_bt_data_send` is a real find and a fair second candidate. But the round's own whole-image search
weighs against it on this hardware: **`zj_bt_socket` appears exactly twice in the entire reconstructed
`system.raw.img`, both inside `libzjL10001.so` — no server for it exists anywhere in `/system`,
`/vendor` or `/product`.** A client path with no server on the platform the unit actually runs
(`-c qianfeng`, confirmed this round from `zlink5.sh`) is dead code, which the results also allow for.
Meanwhile the ZBT channel's server is present, running, and has answered us twice.

That is not proof. It is enough that the next move should not be another teardown.

## 6. The next move is already built

`feat/external-bt-zbt-probe` now carries the transport itself, not just a probe. When the reporter
runs it, the carrier logs:

```
NativeAA: [ZBT] first bytes from the phone over the module (N bytes) — the byte channel is live in this direction
```

on the first inbound `0x105`. If those bytes carry WPP framing the handshake proceeds and the
question is answered affirmatively by the session starting. If `0x105` stays silent while the module
reports a connected Android Auto phone, that is the negative — and it is the point at which
`hu_bt_data_send` becomes worth pursuing, with a specific reason to.

Either way one reporter run settles it, which is what the transport was built to do.

## 7. Brief defects to fix — thank you

Both are real and both are now fixed for the next reuse:

- **The `lib\w+\.so` regex cannot match a hyphenated name.** `\w` excludes `-`, so it silently cannot
  find `libzbt-main.so` and produces a false "never referenced" impression. Caught and worked around
  mid-round, which is exactly what Setup notes are for.
- **`xref_gocsdk.py` and `dad.py` are not on that machine.** The brief assumed the handoff folder was
  co-located and said "skip and say so if not" — which was done correctly. Any future brief needing
  them has to ship them or name a path.

The mid-round correction to `_600106_jg`, and redoing the analysis in full rather than patching the
conclusion, is the reason the inventory section is trustworthy. Noted and appreciated.
