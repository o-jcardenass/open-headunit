# External BT / ZBT: what `moriceh/open-headunit` did to the TCP link, and what we take from it

**Not a hardware round.** This is a coding-session handoff: an evidence file so whoever implements
the four changes below works from the comparison rather than re-deriving it. No APK, no rig. The rig
has none of the hardware markers this thread is about (see "Why the rig cannot test this").

## Where this came from

André Rinas forwarded a mail thread on 2026-09-06. Kevin Fensel (Xtrons head unit, Android 10, plus
a Galaxy Tab S11 Ultra) has had Native AA fail on his unit since April. The diagnosis André reached
in May was `BSSID ist bei dir immer 0`: the head unit hands the phone credentials with a masked or
absent BSSID and the phone refuses them immediately.

Three days before the mail, a third party got it working on that unit with a fork:

- **`https://github.com/moriceh/open-headunit`**
- Pinned at **`e724e171019738154c2c511b2a9a327665af5f0f`** (single branch, `main`, 2026-09-04).
- Source read from `https://codeload.github.com/moriceh/open-headunit/tar.gz/e724e171…`.

Our side is **`feat/external-bt-zbt-probe` @ `b64912805`** (8 commits over `main`).

Both solve the same problem the same way. On these units the Bluetooth is an **external module** on a
UART that `android.bluetooth` cannot transmit through, so the Android Auto handshake has to be
carried over the vendor daemon's **loopback TCP socket**. He calls it the blink/ZXW bridge; we call
it ZBT.

**Scope:** the TCP link, the settings/compatibility surface that gates it, and one soft-AP bug of his
that matches Kevin's original symptom. Everything else in his fork (Blink launcher mode, Self-ADB
hotspot, Zlink disable/restore, MS9120 cluster output, rotary controller, background service mode,
follow-system dark mode, disable-BT-during-projection) is **his to PR**. Do not port it.

---

## 1. The protocol is settled: two independent derivations agree

He derived it from a **PCAP capture** (his code comments cite "Packet 412 du PCAP"). We derived it by
**disassembling the vendor library** (`libzbt_rfcomm_data_send`, `libzbt_rfcomm_data_recv_CB_init`,
`gocsdk_zj`). Neither knew about the other. They agree byte for byte:

| Thing | His | Ours |
|---|---|---|
| Endpoint | `127.0.0.1:3152` | `ZbtByteChannel.HOST` / `.PORT`, same |
| Header | 16 bytes: `0000FFFF`, `00000101`, msgId u32 BE, bodyLen u32 BE | `ZbtFraming.encodeHeader`, same |
| Session open | msgId `0x101`, body `08 81 02 10 02` | `ZbtMessages.encodeRequestInit`, same shape |
| `enable_type` for Android Auto | `2` (the `10 02` above) | `ZbtMessages.ENABLE_TYPE_ANDROID_AUTO = 2`, same |
| Byte channel | msgId `0x105`, raw RFCOMM payload as the whole body, no protobuf, both directions | `ZbtMessages.RFCOMM_DATA`, same |
| Every other msgId | ignored as telemetry | dispatched to `onControlFrame` |
| Seam into the handshake | wraps the socket in `InputStream`/`OutputStream` adapters and feeds the unchanged WPP session | `HandshakeLink` / `ZbtLink`, same seam |

His init body decodes as `field 1 = 257 (0x101)`, `field 2 = 2`, which is our `RequestInit { id,
enable_type }` exactly.

**Treat this table as settled.** It is the one thing in this document that is not a judgement call,
and it should not be re-litigated or re-tested. What it buys us: our reverse engineering is
confirmed, and his running unit proves the route carries a real end-to-end session, something ours
has never been shown to do.

Our framing layer is strictly better than his (magic validation, `MAX_BODY_BYTES` bound, mid-frame
timeout, backlog cap, write lock, chunked writes). **Take nothing from his framing.**

---

## 2. Where he differs, four items, two taken

All his line numbers are in
`app/src/main/java/com/andrerinas/openheadunit/connection/wifi/modes/nativeaa/NativeAaHandshakeManager.kt`
at `e724e171` unless stated.

### D1. He never waits for a presence signal before speaking. **TAKING.**

His `:365-490`. He opens the socket, sends `RequestInit`, builds the two stream adapters, and at
`:465-476` calls `handleHandshakeSession(...)` **unconditionally**. Nothing gates it. The WPP
handshake just runs, and the phone answers or it does not.

Ours gates on `ZbtAttemptPolicy.shouldAttempt(phonePresent = presence?.usable == true, …)`, and
`usable` needs a `LinkInfo` (`0x104`) or `LinkInfo2` (`0x10c`) carrying
`phone_type == 1 && is_connect == 1`.

If a unit's daemon never emits those, or classifies the phone with a different `phone_type`, our
carrier pumps frames forever and never says one word. His speaks blind. The head unit is the side
that speaks first in WPP, so speaking blind costs nothing.

**This is the most likely single reason his build connects where ours would sit silent**, and it is
the highest-value item in this document.

### D2. He retransmits the opening messages while the phone is silent. **TAKING.**

His commit `8cf70d7705ef7a0235c82b13881c992c62affedf`, "Optimize Blink/ZXW handshake and Bluetooth
handling". Two loops:

- `:1429`, during the 60 s credentials wait, while `session.messagesReceived == 0`, re-send
  `WifiVersionRequest` (Type 4) every 2500 ms.
- `:1555`, after credentials, while `stage == AWAIT_INFO_REQUEST` and `messagesReceived == 0`,
  re-send `WifiStartRequest` (Type 1) every 2500 ms.

Both are gated on `localRadio == "Choiceway-Blink"`, i.e. the module route only.

His stated reason in the commit message: "a late Bluetooth pair no longer stalls the 60s credentials
wait". The mechanism matters: on this route there is **no accept event**. Our first message can go
out before the phone's RFCOMM channel exists on the module, the daemon drops it, and nobody is told.
Retransmission is the only cover for that.

**We have no retransmit at all, on either route.**

### D3. His wake is a channel re-open, not `RequestReconn`. **NOT taking.**

`restartZxwBridge()` at `:1093` closes the live `activeBlinkSocket`; the retry loop catches the
exception, reconnects, and re-sends `RequestInit(enable_type = 2)`. He reports that re-init wakes the
phone through the Feasycom/ZXW chip. He also fires it from an `ACTION_ACL_CONNECTED` receiver
(`:302`) whenever the phone reconnects and no session is up.

We send `RequestReconn` (`0x114`) instead, paced by `ZbtWakePolicy.MIN_INTERVAL_MS = 15_000`.

**Why not taken:** our `RequestReconn` path has its own hardware evidence behind it. Trading a
measured behaviour for an unmeasured one on the strength of one working unit is the wrong call.
Recorded here so a later round can revisit it **if a field build shows the wake is what fails** -
that is the trigger to come back to this item, and nothing else is.

### D4, `RequestInit` id field. **NOT taking.**

He sends field 1 = `0x101`, matching the header msgId, which is this protocol's own convention. We
send `1` (`ZbtByteChannel.REQUEST_INIT_ID`). Both are accepted by real daemons, his unit runs.

One correction worth carrying: our `REQUEST_INIT_ID` KDoc claims `1` is "what was on the wire in the
one exchange a real daemon has ever accepted". His PCAP is of the **vendor's own client** and shows
`0x101`, so that sentence is now wrong even though the value still works. Fix the comment if you are
in the file; do not change the value on its own account.

---

## 3. Not from the TCP link, but taking it anyway: the `rmnet*` soft-AP bug

His commit `db2f8cff32b73009970e1cd5df334d10da162d7c`:

> Interface selection: exclude Qualcomm cellular "rmnet\*" interfaces so the modem can no longer be
> advertised as the soft AP (was handing the phone the cellular IP and no BSSID, which phones
> refuse).

**This is exactly Kevin's original symptom**: "diese BSSID ist bei dir immer 0, anstelle der
richtigen. Und deswegen bricht das Handy sofort ab." It is a real bug, entirely independent of the
module route, and it can bite any Qualcomm head unit in hotspot transport.

Our `SoftApNetworkPolicy.EXCLUDED_PREFIXES` already carries `seth_lte` for precisely the same
failure, and its own KDoc concedes the list "has needed extending three times". `rmnet` is the
Qualcomm equivalent of that Unisoc entry: up, holding a private address the moment there is signal,
and therefore the only surviving candidate when the real access point is down, so it gets advertised
as the AP and the phone is handed a cellular IP with no BSSID.

One-line change plus a test. Take it; do not wait for his PR, and make sure it is not lost if his PR
never lands.

## 4. Explicitly not to be ported

- His `utils/ExternalBtPolicy.kt` is **byte-identical** to ours (verified with `diff -q`). It is our
  own upstream file. Nothing to merge.
- His `WppHandshakeSession.kt` is **older than our `main`**. He forked before our WPP-over-TCP work,
  so `git diff` shows our newer code as "his deletions". Those are not his changes. Do not
  reintroduce `WPP_VERSION_MAJOR/MINOR = 1/1`, do not drop `WppEvent.TcpSessionUp`, do not drop
  `WppStatus.describe`.
- Everything else in his fork, listed under Scope above.

---

## 5. The four changes, with our file paths

Branch: `feat/external-bt-zbt-probe` @ `b64912805`.

### C1. Speak-first fallback when the module reports no presence

`app/src/main/java/com/andrerinas/openheadunit/connection/wifi/modes/nativeaa/zbt/ZbtAttemptPolicy.kt`

Add a blind-attempt escape to `shouldAttempt`: once the channel has been open longer than a grace
period **and no usable `Presence` has ever been seen**, attempt anyway. Keep the presence path as the
fast path, when the module does tell us, still act on the arrival edge immediately.

- New constant `BLIND_ATTEMPT_AFTER_MS`, ~10 s. Rationale for the value: the opening state burst is
  what `ZbtProbe.HELLO_BUDGET_MS` already budgets 8 s for, so 10 s is "the burst has been and gone".
- New parameters `channelOpenForMs: Long?` and `everSawPresence: Boolean`. `phonePresent = false`
  must stop returning false outright once the grace has elapsed and nothing has ever been reported.
- Blind attempts keep the caller's existing `minIntervalMs` backoff. Do not add a second interval
  constant.

`ZbtAaCarrier.watchAndServe` already tracks `openedAt`; thread it and an `everSawPresence` flag into
`shouldAttemptNow()`.

**`serveOnce` must log the two cases differently**: "the module reports a phone connected" versus
"the module has said nothing in Ns, attempting anyway". A reporter's exported log is the only place
we will ever see which one fired, and the whole value of this change is knowing that.

Tests: extend `app/src/test/java/com/andrerinas/openheadunit/connection/wifi/modes/nativeaa/zbt/ZbtAttemptPolicyTest.kt`.

### C2. Retransmit the opening messages while the phone is silent

`app/src/main/java/com/andrerinas/openheadunit/connection/wifi/modes/nativeaa/HandshakeLink.kt`
`app/src/main/java/com/andrerinas/openheadunit/connection/wifi/modes/nativeaa/NativeAaHandshakeManager.kt`

Add a `HandshakeLink` property in the shape of the existing `persistPeerForAutoStart`:

```kotlin
/** Whether the opening messages should be repeated while the phone has said nothing. */
val retransmitsWhileSilent: Boolean
```

`false` on `BluetoothSocketLink`, because an accept proves the phone is there. `true` on `ZbtLink`, where there
is no accept, so a dropped first message is invisible. Document that difference on the property, the
way `persistPeerForAutoStart` documents its own.

Then in `handleHandshake`'s two wait loops, gated on that property **and** `session.messagesReceived
== 0`:

- credentials-wait loop → re-send `WifiVersionRequest`
- post-credentials loop, while `session.stage == WppStage.AWAIT_INFO_REQUEST` → re-send
  `WifiStartRequest`

Put the cadence in a pure `ZbtRetransmitPolicy` object rather than a bare `2500` at two call sites.
Repo convention, and these two are exactly the kind of pair that drifts apart.

Tests: new `ZbtRetransmitPolicyTest.kt` beside the other zbt tests.

### C3. Let the compatibility check say the module *can* carry us, and keep denying the ones that cannot

This is the one André's mail makes necessary, and the one with real regression risk.

Today `ExternalBtTransportPolicy.route()` takes reachability as explicitly out of scope, its KDoc
argues "Whether the daemon answers is decided by trying it, not by predicting it". The consequence is
that a unit like Kevin's is told **"Native Wireless cannot work on this head unit"** until the user
finds an Advanced toggle, runs a probe, reads the result and flips a second toggle. On a unit whose
daemon answers, that message is simply false.

**Design decision taken:** make daemon reachability a real input. A unit whose daemon answers takes
the ZBT route automatically and is told so; a unit whose daemon refuses is denied exactly as today.
`Settings.externalBtZbtTransport` stays but is demoted from a precondition to a manual override, for
a user whose daemon is slow or intermittent.

- `ExternalBtTransportPolicy.route()` gains `daemonReachable: Boolean?` (null = not probed yet).
  Precedence: no evidence → `NORMAL`; transport toggle on → `ZBT`; `ignoreExternalBt` → `NORMAL`;
  `daemonReachable == true` → `ZBT`; otherwise → `BLOCKED`. Still pure, still tested. **Rewrite the
  KDoc's argument rather than leaving it standing and quietly contradicted**, that comment is the
  reason the current design exists and a future reader will trust it.
- New reachability check next to `ZbtByteChannel`: connect, send `RequestInit`, wait briefly for any
  valid frame, close. Reuse `ZbtByteChannel.open` plus one `pumpOnce`; do not write a second socket
  path. Cache the result for the process so the settings screen and `start()` do not each dial the
  daemon.
- **Threading hazard, the one item here that can regress a path that works today.**
  `checkCompatibility` is called from `SettingsFragment.kt:4256` on the UI path.
  `ZbtByteChannel.CONNECT_TIMEOUT_MS` is 4000. A blocking dial there is an ANR on every unit,
  including the ones with no external BT at all. Either run the probe off the main thread and resolve
  the dialog when it returns, or have the UI read a cached result and kick the probe off for next
  time. Do not put a socket connect behind a synchronous UI call.
- Strings: `external_bt_nativeaa_desc` currently tells every flagged unit to go run the probe. Split
  it: a unit whose daemon answered gets the `external_bt_module_nativeaa` wording, which already
  exists; a unit whose daemon refused keeps the denial. Both need the localisation pass across every
  `values-*/strings.xml`, the way commit `f4437e13b` on this branch already did it.

Tests: extend
`app/src/test/java/com/andrerinas/openheadunit/connection/wifi/modes/nativeaa/ExternalBtTransportPolicyTest.kt`
for the new input and its precedence.

### C4. Exclude Qualcomm `rmnet*` from soft-AP interface selection

`app/src/main/java/com/andrerinas/openheadunit/connection/wifi/modes/nativeaa/SoftApNetworkPolicy.kt`

Add `"rmnet"` to `EXCLUDED_PREFIXES`, next to `seth_lte`, and extend that constant's KDoc to name it
as the Qualcomm equivalent with the same failure mode: up, holding a private address, and the only
candidate left when the real AP is down, so it is advertised as one and the phone is handed a
cellular IP with no BSSID.

Tests: extend
`app/src/test/java/com/andrerinas/openheadunit/connection/wifi/modes/nativeaa/SoftApNetworkPolicyTest.kt`.

---

## 6. Verification

### Why the rig cannot test this

The MT50 has a single internal Bluetooth radio and no `/dev/rf_serial` or `/dev/zj_bt_serial`, so
`ExternalBtPolicy.detect` returns null, the ZBT route is never taken, and no amount of setup will
make it be. Every ZBT round on this thread so far ran on a reporter's unit, and this one will too.
That is a rig limitation, not a code problem. Report it as such if asked to test C1/C2 on hardware.

### What can be run here

1. `./gradlew :app:testGithubDebugUnitTest`: extended `ZbtAttemptPolicyTest`,
   `ExternalBtTransportPolicyTest`, `SoftApNetworkPolicyTest`, plus a new `ZbtRetransmitPolicyTest`,
   alongside the existing `ZbtByteChannelTest` (493 lines), `ZbtFramingTest`, `ZbtMessagesTest`,
   `ZbtProbeTest`, `ZbtWakePolicyTest`.
2. `./gradlew :app:assembleGithubDebug`: compile gate. `playstore` still cannot compile; that is
   expected and unrelated.
3. Ordinary-route regression, by construction: `retransmitsWhileSilent` is false on
   `BluetoothSocketLink`, and `route(daemonReachable = …)` returns `NORMAL` whenever there is no
   external-BT evidence. `WppHandshakeSessionTest` and the RFCOMM path must be untouched.
4. **On the rig, the negative case is worth a run**: after C3, confirm the settings screen still
   opens and the Native compatibility dialog still behaves, with no ANR. C3 puts a socket dial behind
   a UI call, and the rig has none of the markers, which makes it exactly the hardware that would
   catch an unconditional dial.

### Field verification (reporter, not rig)

A debug build to Kevin Fensel via André. Xtrons head unit, Android 10. **Log level Verbose**. INFO
drops the lines that carry the signal. The sequence that decides it:

```
NativeAA: [ZBT] channel open to the Bluetooth module daemon on 127.0.0.1:3152
NativeAA: [ZBT] [RX] id=0x10c LinkInfo2 …          ← presence path, OR
NativeAA: [ZBT] the module has said nothing in Ns  ← the new blind path (C1)
NativeAA: [TX] Sending WifiVersionRequest (Type 4) ← plus its retransmits (C2)
NativeAA: [RX] Received Type 2
NativeAA: Successfully delivered Protobuf TYPE 3
WirelessServer: Incoming connection detected from /…   ← THE line that matters
```

If the first line never appears, this unit has the markers but no daemon and none of this can help
it. If everything appears but the last line does not, the phone never got onto the network, look at
the credentials and the BSSID, not at the TCP link.

---

## 7. For André

Three things to tell him, separately from the code:

1. The TCP-link protocol is now confirmed from **two independent derivations** (moriceh's PCAP, our
   disassembly). That is settled.
2. We are folding C1-C4 into `feat/external-bt-zbt-probe`. D3 and D4 are recorded and deliberately
   not taken.
3. The rest of moriceh's fork is his to PR, but the `rmnet*` soft-AP interface fix in it is a real
   bug that matches Kevin's original "BSSID ist immer 0" report and has nothing to do with the module
   route. It should not be lost if that PR never lands, which is why C4 is on our list.
