# wpp-over-tcp, round 1 brief: does the phone dial us back over TCP

**Build:** `fork/fix/wpp-over-tcp` @ `8ff9510d`, one commit on `2f07eeec` (current `main`).

**The branch has been rewritten twice since round 1, and both old SHAs are gone.** `8d369ac5` failed
R0: the schema change shipped without the hand-committed `Wireless.java` that this repo regenerates
by hand, so nothing compiled. That file was regenerated with `protoc` 25.1 and committed alongside
the schema, giving `64fffe32`. The branch was then rebased onto current `main`, giving `8ff9510d`.
The rebase changed no content: `git show 64fffe32` and `git show 8ff9510d` are identical apart from
the commit header and one hunk header in `Settings.kt`, which moved with main's own drift. Fetch
before checking out. Nothing else about the round changed, and every run below stands as written.

```bash
git fetch fork
git rev-parse fork/fix/wpp-over-tcp     # 8ff9510d95c94966ee33325e84d71f113e9e88a3
git rev-parse 2f07eeec                  # the base, current main
```

**R0 has already been cleared on the coding host** and is reproduced below for the record, so a FAIL
there is a new fact rather than a repeat of round 1. See §5.

One build, one install. Every run below is a settings change on that build, including the
positive control.

---

## 1. Why this round exists

Android Auto 17.4 removed the entry point a phone-side companion app used to start a wireless
session, which is what killed the Wireless Helper. A teardown of Gearhead **17.5.663204** found
what replaced it: the head unit tells the phone, during the Bluetooth handshake, an **IP and port
to reach it on over TCP**. The phone stores that endpoint against the head unit's Bluetooth
address and, on later connections, dials it directly instead of opening our RFCOMM channel again.

We already send the message that carries it, `WifiVersionRequest` (type 4), and it has never
worked, for two reasons found in the dex:

- The endpoint rides on **field 6** of that message, and our `wireless.proto` had no such field.
- The phone ignores field 6 below protocol **4.1**, logging `Skip handling
  WifiProjectionProtocolInfo as the protocol version is too low.` We announced **1.1**. At exactly
  4.1 it additionally requires the head unit's make to be on an allowlist we are not on, so the
  branch announces **4.2**, which clears both. A version above the phone's own is negotiated down,
  not refused.

So `native-wifi-version-exchange` has been shipping as an opt-in that could not have helped anyone:
it announced a version below the gate and omitted the payload the message exists to carry. It now
defaults **on**, and this round is the first measurement of what it does.

The branch also adds `WppTcpServer`, which answers the dial. Note the SSL roles invert relative to
AAP: there the head unit is the client, here the phone connects to us and we are the **server**.
Same certificate, which is a v1 cert with no extended key usage, so nothing in it restricts which
end presents it. **Whether the phone accepts it in that direction has never been observed, and R2
is the run that finds out.**

### The honest limit, stated first

All of the above was read out of a dex, not measured. Two things could each end the round early and
both are useful results:

- The phone may reject or ignore our 4.2 announcement, in which case nothing dials us and R2 is a
  clean FAIL with a specific cause.
- The TLS handshake on 5299 may fail, which would show as a connection that arrives and then
  errors. That is a different finding from never arriving at all, and §5 separates them.

**R1 is also a regression check and it matters more than the new feature.** Turning the version
exchange on is the one change that alters what a working unit puts on the wire. If R1 cannot form a
session at all, stop, report it, and the setting in §3 is the immediate mitigation.

---

## 2. What is different about this round

- **The decisive evidence is in our own logcat, not the phone's.** If the phone dials us, our
  server prints `WppTcpServer: connection from <ip>`. No phone-side capture is needed for a verdict,
  though §8 asks for one if the round has budget.
- **The payoff is on the second connection, not the first.** R1 advertises the endpoint; the phone
  stores it; **R2 reconnects and is the point of the round.** A brief that only connected once
  would measure nothing.
- **All new lines are `AppLog.i` or `.w`**, so INFO is enough. No verbose capture needed.
- **This is a Native AA round**, so §7a's standing constraints apply: the head unit's own Bluetooth
  adapter must be enabled *before* the app launches or `NativeAaHandshakeManager.start()` no-ops,
  and the phone's Bluetooth must be on. Do not toggle-then-launch.
- **Port 5299 is new.** Nothing else in the app binds it, and it is deliberately distinct from 5288
  (the projection session) and 5277 (Android Auto's own head unit server, which mode 1 dials
  outward). If something else on the rig holds it, `WppTcpServer: could not listen on 5299` prints
  at E level and the whole round is INCONCLUSIVE; say so and stop.

---

## 3. Settings keys this round needs

Written to `shared_prefs/settings.xml` with the app stopped, per §1. Read all four back before the
first run counts, per §7a's root-owned `shared_prefs` note.

```xml
<int name="wifi-connection-mode" value="3" />
<int name="native-ap-transport" value="0" />
<boolean name="native-wifi-version-exchange" value="true" />
```

| Key | Type | Value | Why |
|---|---|---|---|
| `wifi-connection-mode` | int | `3` | Native AA. The only mode this round touches. |
| `native-ap-transport` | int | `0` | WiFi Direct, the default. `1` is the hotspot transport; see R4. |
| `native-wifi-version-exchange` | bool | `true` | Now the default. Set explicitly so the file is unambiguous, and flipped to `false` for the R3 control. |

**Diff `settings.xml` against a fresh backup at the start and state the delta in Setup notes**, even
if zero. Another thread's log level or view mode carrying over has cost rounds before.

---

## 4. The lines that decide every run

Verified with `grep -F` against `8ff9510d`. All are ours unless marked.

**The server came up** (once per app start):
```
WppTcpServer: listening for Android Auto on TCP 5299
```

**What we advertised** (once per Bluetooth handshake):
```
NativeAA: advertising WPP over TCP at <ip>:5299
```
An address of `0.0.0.0` here is **correct, not a bug**. The version request goes out before the
credentials resolve, and that is the phone's documented "dial the gateway" form; on a network we
host, the gateway is us. Record which form appeared.

**The failure to advertise**, which voids R2 if it appears:
```
NativeAA: WPP TCP server is not listening; advertising no endpoint this handshake
```

**The phone dialled us.** This is the line the round exists to produce:
```
WppTcpServer: connection from <ip>
WppTcpServer: TLS handshake complete with <ip>
```

**The exchange over TCP**, in order:
```
WppTcpServer: [TX] WifiVersionRequest (Type 4) v4.2
WppTcpServer: [RX] Type 5 (<n> bytes)
WppTcpServer: [TX] WifiStartRequest (Type 1) -> <ip>:5288
WppTcpServer: [RX] Type 2 (<n> bytes)
WppTcpServer: [TX] WifiInfoResponse (Type 3) with credentials
WppTcpServer: handshake complete; projection session is up
```

**The session actually landed** (existing line, unchanged, and the one that always decides):
```
WirelessServer: Incoming connection detected from /192.168.49.x
```

**Phone-side, if a capture is taken** (tag `GH.*`, Gearhead's own words):
```
Skip handling WifiProjectionProtocolInfo as the protocol version is too low.
No WPP on TCP configuration found in storage for the head unit
Trying to start WPP on TCP with configuration
Received WPP over TCP for 4.1 HU which is not allowlisted for SPARK
```
The first two mean our endpoint was not taken. The third means it was. The fourth should not appear
at 4.2 and would be a finding if it did.

---

## 5. Runs

### R0: build gate

`run_unit_tests.sh`. Two new classes must be present **by name**, and the count matters because a
missing generated proto field would fail them rather than silently pass:

- `WppMessagesTest`, **10** tests
- `ProjectionDeepLinkTest`, **8** tests

**PASS**: both classes present, 18/0. **FAIL**: any failure, or either class absent. A FAIL here
stops the round; do not install.

**This gate was run on the coding host at `8ff9510d` before the round was queued, and it passed.**
`./gradlew :app:testGithubDebugUnitTest` built clean, including `compileGithubDebugKotlin`, the task
that failed round 1. `WppMessagesTest` 10/0 and `ProjectionDeepLinkTest` 8/0, whole suite 971/0
across 97 classes. Identical on JDK 17 and on JDK 21, the version CI uses, so the result is not
specific to one toolchain. Run it anyway, since the count is cheap, but a FAIL here now means
something changed rather than that the branch was never compiled.

### R1: a Native AA connection still works, with the endpoint advertised

The regression check, and the more important half of the round.

Setup: settings per §3, head unit Bluetooth on before launch, phone Bluetooth on. Clean run per §4.
Connect normally and let the session establish.

- **PASS**: `WirelessServer: Incoming connection detected` appears and projection runs, exactly as
  it does today. `WppTcpServer: listening for Android Auto on TCP 5299` appeared at startup, and
  `NativeAA: advertising WPP over TCP at <ip>:5299` appeared during the handshake.
- **FAIL**: no session forms, or one forms materially slower than this rig's norm. **This is the
  outcome that stops the round** and the branch is not shippable as it stands.
- Record: the advertised address form (`0.0.0.0` or a real one), and the wall-clock from
  `createGroup SUCCESS` to `Incoming connection detected`, so R2 has something to compare against.

**If the change did nothing, R1 still passes.** That is why R2 exists, and why R1 asks for the
advertised-endpoint line rather than only for a working session.

### R2: the point of the round. Reconnect, and see whether the phone dials TCP

Setup: immediately after a PASS in R1, on the same install and the same settings, disconnect and
reconnect. Prefer the app's own exit deep link and then a fresh connect (§3 of the template) over
anything physical.

Do this **three times**, because the first reconnect after provisioning is the one most likely to
differ, and one observation cannot separate "never" from "not yet".

- **PASS**: `WppTcpServer: connection from <ip>` appears on at least one reconnect, followed by
  `TLS handshake complete with`. Report on how many of the three.
- **PARTIAL, and a distinct finding**: `connection from` appears but `TLS handshake complete` never
  does, or is followed by `WppTcpServer: session error:`. That means the phone dialled us and our
  TLS server was not acceptable to it. **Capture the full error text**; it is the answer to the one
  question static analysis could not settle.
- **FAIL**: `connection from` never appears across all three reconnects, while the RFCOMM handshake
  runs each time as in R1. The endpoint was advertised and not taken.
- Pair the count with: did each reconnect actually re-run the Bluetooth handshake (an
  `advertising WPP over TCP` line per reconnect)? A reconnect that never handshook proves nothing
  either way and should be redone.

### R3: positive control. Turn the endpoint off and the TCP dial must stop

Only run this if **R2 passed**. It proves the dial follows our advertisement rather than something
else on the rig.

Setup: app stopped, set `native-wifi-version-exchange` to `false`, read it back, relaunch, connect,
then reconnect twice as in R2.

- **PASS**: no `NativeAA: advertising WPP over TCP` line at all, and no `WppTcpServer: connection
  from` on either reconnect. Sessions still form over the old path.
- **FAIL**: the phone still dials 5299. That means it is using a stored endpoint from R2 rather than
  the current advertisement, which is worth knowing and is not a defect in the branch, but it makes
  R2's attribution weaker. Note it and move on.

Restore the key to `true` afterwards.

### R4: the hotspot transport, if the round has time

Setup: app stopped, `native-ap-transport` to `1`, read back, relaunch, then R1 and one reconnect.

- **PASS**: same as R1 and R2 on the hotspot network.
- **INCONCLUSIVE** is an acceptable result here; this transport is experimental and R4 is coverage,
  not a gate. Do not spend the round on it.

---

## 6. Do not re-run

- Anything about whether Native AA works at all on this rig. It does; that is R1's baseline, not a
  question.
- Poke connectivity. §7a records it varying between sessions, and nothing in this round depends on
  a poke connecting.
- Any attempt to suppress session formation by turning the phone's WiFi off. §7a records that it
  does not work on this phone and one round lost 68 minutes to it.

---

## 7. Not in this round

The branch also contains `ProjectionDeepLink`, which builds Android Auto's own wireless-setup QR
URL. That is the provisioning route for head units whose Bluetooth the phone cannot reach, and it
is covered by JVM tests only. It needs a separate round with its own setup and is deliberately not
mixed in here. There is no UI for it yet.

---

## 8. Report back

The three things that decide whether this ships:

1. **R1 verdict.** Does a Native AA session still form with the version exchange on. If not, nothing
   else matters.
2. **R2: how many of three reconnects produced `WppTcpServer: connection from`**, and if any did,
   whether TLS completed. If TLS failed, the exact error text.
3. **The advertised address form** from R1 (`0.0.0.0` or a real address), since it decides whether
   the gateway fallback is doing the work or the resolved address is.

Plus, as always: Setup notes with the `settings.xml` delta, and anything the brief did not ask
about that looked wrong.
