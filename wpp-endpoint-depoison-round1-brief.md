# wpp-endpoint-depoison, round 1 brief: can the head unit make a phone let go of a stale endpoint

**Build:** `fork/fix/wpp-endpoint-depoison` @ `020c6904`, **two** commits on `80a81099` (current `main`).

```bash
git fetch fork
git rev-parse fork/fix/wpp-endpoint-depoison    # 020c6904...
git rev-parse 80a81099                          # the base
```

| SHA | What |
|---|---|
| `6da23cf0` | `wireless.proto` models the rejection, the real setup info and the access point, regenerated with protoc 25.1 |
| `020c6904` | A WPP TCP dial we refuse is answered with a rejection instead of a bare close |

---

## 1. Why this round exists

A phone that has been given a WPP-over-TCP endpoint stores it against this head unit's Bluetooth
address and dials it in preference to the Bluetooth handshake, forever, with no fallback of its own
when the network behind it is gone. Every round that has met this cleared it by forgetting the head
unit on the phone, because nothing the head unit sent could.

Reading Android Auto's own code changed that. Two facts from 17.5.663204 and 17.8.163744:

- **The refusal we have been sending is the worst one available.** A bare socket close reads to the
  phone as `TCP_SOCKET_CONNECTION_FAILED`, which is not in its clearing set and *is* retryable, so it
  re-dials the dead endpoint on a backoff with no bound. The fallback that would have rescued it,
  `WirelessProjectionInGearhead__fallback_to_rfcomm_on_t_minus`, defaults to false in both builds.
- **There is a message that reaches its clear path.** `ConnectionRejection`, message type 10, field 1
  an enum verified against `{0,1,2}`. Only `MOBILE_DEVICE_ID_NOT_FOUND`(1) and
  `INVALID_SETUP_TOKEN`(2) are sendable, only over TCP, and the phone throws an
  `IllegalStateException` on anything else, including its own `UNKNOWN`(0) on any transport. The
  branch sends `INVALID_SETUP_TOKEN` and nothing else, and cannot express the others.

`020c6904` sends one on every dial it would otherwise have dropped.

**What this round measures is whether that actually clears the record**, and it is genuinely open.
One decompiler pass read the clear as removing both the endpoint and the credentials entry for this
head unit's MAC; a second pass, reaching the same call from the rejection branches, decoded its 17.8
body as a timestamp update and could not find a deletion at all. 17.8 carries a flag-gated storage
backend that 17.5 does not, which is the likeliest reason the two readings differ. **The 17.5 reading
is the better evidenced one, and neither has ever been seen on hardware.**

So R2 is the round. Everything else protects it.

---

## 2. What is different about this round

- **The phone has to be poisoned on purpose first.** R1 exists to create the stale endpoint that R2
  then tries to clear. This is the opposite ordering to `wpp-over-tcp` round 5, which went to great
  lengths to keep a phone clean; here a clean phone makes R2 unscoreable.
- **Record the phone's Gearhead version, exactly, before the round and in the results.**
  `adb shell dumpsys package com.google.android.projection.gearhead | grep versionName`. The clear
  was read on 17.5 and not reproduced on 17.8. A result without the version attached cannot be
  attributed to either reading.
- **A refused dial now completes TLS before it is refused**, because telling the phone anything
  requires a channel. So `TLS handshake complete with <ip>` on a dial that is then refused is
  correct and expected, not a regression from earlier rounds where the socket closed first.
- **The hotspot route still needs 5 GHz.** Below 5180 the session dies within seconds having sent no
  frame, and that is the band, not this branch.
- INFO is enough for every run here.

---

## 3. Preparation

Standing method as ever: `TESTING-TEMPLATE.md`, settings written to `shared_prefs/settings.xml` with
the app stopped, never through the UI. Diff `settings.xml` against a fresh backup at the start of
each part and state the delta even if zero.

**R1 and R3 (hotspot, endpoint advertised):** `wifi-connection-mode=3`, `native-ap-transport=1`,
`native-wifi-version-exchange=true`, `hotspot-band=1`, `auto-enable-hotspot=false`, and this rig's
known-working hotspot values from the `wpp-over-tcp` thread. Bring the access point up on 5 GHz and
record `SoftApInfo`'s frequency.

**R2 and R4 (the same phone, the head unit moved off that network):** `native-ap-transport=0` for
WiFi Direct, everything else unchanged. Do **not** clear the phone between R1 and R2: the whole
point is that it still holds R1's endpoint.

**R4 additionally:** the head unit's own Bluetooth adapter off, so the RFCOMM listeners are down.

Between R2 and R3, forget the head unit on the phone and confirm the clean state, because R2 either
cleared the record or proved it cannot, and R3 must start from neither.

---

## 4. The lines that decide every run

Head unit side, verified with `grep -F` against `020c6904`.

**The endpoint went out (R1):**
```
NativeAA: advertising WPP over TCP at <ip>:5299
WppTcpServer: listening for Android Auto on TCP 5299
```

**The dial was refused and the phone was told so.** New, and the point of R2:
```
WppTcpServer: rejecting this dial so the phone drops our endpoint and goes back to Bluetooth:
WppTcpServer: [TX] wrote type 10 (2 bytes)
```

**The refusal was swallowed because there was no route back.** New, and the point of R4:
```
WppTcpServer: not serving this dial, and not withdrawing the endpoint because the Bluetooth listeners are not open for the phone to fall back to:
```

**Unchanged and still decisive:**
```
WppTcpServer: connection from <ip>
WppTcpServer: TLS handshake complete with <ip>
WirelessServer: Incoming connection detected from /<ip>
NativeAA: Connection accepted from
```

**Should not appear at all:**
```
WppTcpServer: session error:
```

Phone side, tag `GH.*`:
```
Trying to start WPP on TCP with configuration          <- the record is live
No WPP on TCP configuration found in storage for the head unit   <- the record is gone
Handling WifiConnectionRejection with reason           <- our type 10 arrived and parsed
Retrying connection attempt on all channels by restarting WPP
Attempting to connect Bluetooth RFCOMM                 <- the fallback this round is chasing
```
`Handling WifiConnectionRejection with reason` is the single most valuable line in the round. Quote
it with its reason, and quote the ten lines after it whatever they say.

---

## 5. Runs

### R0: build gate

`run_unit_tests.sh` on the coding host. Counts at `020c6904`:

- `WppMessagesTest` **17** (was 14), `WppTcpServePolicyTest` **9** (was 6)
- `WppEndpointPolicyTest` 10 and `WppHandshakeSessionTest` 30, both unchanged
- whole suite **2170 / 0**

Cleared on the coding host at `020c6904`: `BUILD SUCCESSFUL`, `compileGithubDebugKotlin` clean,
2170 tests, 0 failures, JDK 17.

### R1: poison the phone deliberately

Settings per §3, access point up on 5 GHz, phone cleared of this head unit first so the record R2
works on is the one this run created.

1. Connect, let projection run a minute.
2. `headunit://exit`, `am force-stop`, relaunch `MainActivity`, phone untouched.
3. The reconnect must happen **without the phone opening RFCOMM**, which is what proves it is
   dialling the stored endpoint.

- **PASS**: `advertising WPP over TCP at <ip>:5299` at least once, a session on the first connect,
  and on the reconnect `WppTcpServer: connection from` with no `NativeAA: Connection accepted from`
  before it, plus the phone logging `Trying to start WPP on TCP with configuration`.
- **FAIL**: no endpoint advertised, or the reconnect runs over Bluetooth. Either means there is no
  stale record and **R2 cannot be scored**; say so and stop rather than running R2 anyway.

This run is setup, but it is graded, because a wrong R1 silently invalidates everything after it.

### R2: the measurement

Do not touch the phone. Switch the head unit to WiFi Direct per §3 and restart it. The group's
identity is unproven on a first bring-up, so the endpoint is withheld and any dial is refused.

1. Bring the head unit up and leave it for three full dial attempts, about two minutes.
2. Quote every `WppTcpServer:` line in order, and the phone's `GH.*` lines across the same window.

- **PASS**: the rejection goes out, the phone logs `Handling WifiConnectionRejection with reason`,
  and then `No WPP on TCP configuration found in storage for the head unit` followed by an RFCOMM
  attempt and an ordinary session. The 17.5 reading is correct and the repair works.
- **PARTIAL**: the rejection goes out and the phone parses it, but it keeps dialling 5299 and never
  logs the missing-configuration line. **This is the 17.8 reading and it is a real result**, not a
  failed run: it means the reason reaches the handler but the clear does not happen on this build.
  Quote the version and everything between the two, and the round has still answered its question.
- **FAIL**: no `rejecting this dial` line at all, or `session error:` in its place. That is the code,
  not the phone.
- **INCONCLUSIVE**: the phone never dials 5299 in the window. R1's record did not survive; redo R1.

### R3: the regression that matters

Forget the head unit on the phone, confirm clean, and put the head unit back on the hotspot settings
of R1.

- **PASS**: an ordinary connect and reconnect, projection both times, and **zero**
  `rejecting this dial` lines in the whole run. A dial that would be served must never be rejected.
- **FAIL**: any rejection line, or a reconnect that no longer forms a session. Either is a
  regression this branch introduced.

### R4: the stranding guard

Poison the phone again by repeating R1, then switch to WiFi Direct **and turn the head unit's own
Bluetooth adapter off** before bringing it up, so the RFCOMM listeners never open.

- **PASS**: the dial is refused, the `not withdrawing the endpoint because the Bluetooth listeners
  are not open` line appears, and **no** `[TX] wrote type 10`. Withdrawing an endpoint from a phone
  whose only other route is shut would leave it nothing at all, and the guard exists to stop that.
- **FAIL**: a type 10 goes out anyway.

### R5: the banner, optional and desk-gradeable

After R2, on the head unit's main screen.

- **PASS**: one banner saying the phone keeps trying a network address this unit no longer uses,
  appearing once rather than once per dial, and gone after R3's first served dial.
- Not a blocking verdict. Report what was on screen.

---

## 6. What a result here decides

A PASS makes the head unit able to repair a phone it poisoned, which no release has ever been able
to do, and retires "forget the head unit" as the only remedy. A PARTIAL still closes the question
the two decompiler passes left open, in the direction that says the clear is version-dependent, and
the code costs nothing either way because the refusal it replaces was strictly worse. Only a FAIL
sends anything back to the coding host.
