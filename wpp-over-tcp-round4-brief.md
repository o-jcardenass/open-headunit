# wpp-over-tcp, round 4 brief: we were the wrong side of TLS

**Build:** `fork/fix/wpp-over-tcp` @ `13a43a6d`, now **four** commits on `2f07eeec` (current `main`).

```bash
git fetch fork
git rev-parse fork/fix/wpp-over-tcp     # 13a43a6d1059c107a26439569f75957781d5cb1d
git rev-parse 2f07eeec                  # the base, unchanged since round 2
```

No history was rewritten. `8ff9510d`, `894093fc` and `968573ab` are still the first three, in order,
so round 3's findings still bisect where they did. The new commit is:

| SHA | What |
|---|---|
| `13a43a6d` | The head unit answers the phone's WPP dial as the TLS **client**, not the server |

**One build for R0 to R3.** R4 is optional and needs a second build off a different branch; it is
last, and skipping it costs nothing to this thread.

---

## 1. Why this round exists

Round 3's R2 was the right measurement and it named the failure exactly: the dial arrived, the
handshake never completed, and after the logging change the line read

```
session error: SSLHandshakeException: connection closed <- EOFException: connection closed
```

with a stack into `ConscryptEngineSocket.doHandshake`. Ten seconds of nothing, then EOF, on every
dial with no per-phone variation. Both ends were waiting to read.

### What was actually wrong

The phone dials us, so we built a TLS **server** on 5299. That was wrong, and it was decoded from
Android Auto 17.5.663204's own bytecode this round rather than guessed. The method that creates this
socket, `Lpep.a`, wraps the connected socket and then calls:

```
invoke-virtual  Ljavax/net/ssl/SSLSocket;->setUseClientMode(Z)V     ; false
invoke-virtual  Ljavax/net/ssl/SSLSocket;->setNeedClientAuth(Z)V    ; true
```

So the phone is the TCP client and the TLS **server**, and it requires a client certificate. The
side that accepted the connection has to send the first handshake record and present a certificate.
We sat waiting for a ClientHello that was never coming; the phone sat waiting for our first WPP
message; its own timeout ended it about ten seconds later, and our read returned EOF. That is the
exact shape of round 3's line.

This is not an inversion of the projection session's roles, it is the **same** assignment. Android
Auto's AAP engine (`Lizu.<init>`) is built with the identical pair, `setUseClientMode(false)` and
`setNeedClientAuth(true)`, and this head unit has always been the SSL client on 5288. One socket
more, same side.

`WppTcpTls` now holds that one decision, and a JVM test drives it against a peer configured the way
the phone configures its end: server mode, client auth required. The shipped `res/raw/cert` is
accepted as a client certificate and the handshake completes.

### The wall from round 3's brief is gone, not deferred

Round 3 warned that the certificate has no CN and no subjectAltName, so hostname verification would
refuse it. That does not arise in this direction. Hostname verification is a check a TLS **client**
makes on a **server** certificate. We are the client now, and our own trust manager accepts
anything, so the phone's certificate is not checked and ours is not checked for a hostname. What is
still unmeasured is whether the phone's trust manager accepts our chain as a client certificate.
AAP's mutual handshake, which uses the same certificate in the same role, says it should.

---

## 2. What is different about this round

- **The blocker is one commit and one behaviour.** If R1 completes a TLS handshake, the branch has
  done in round 4 what it failed to do in rounds 2 and 3.
- **R1 and R2 are the hotspot arm.** That is where the endpoint is advertised at all, so it is the
  only arm where any of this is reachable.
- **One line no longer exists.** `WppTcpServer: accepted socket was not TLS; dropping` was removed
  with the SSL server socket. A `grep` returning zero is correct, not a wrong APK.
- **Two lines are unchanged and still decide runs**: `advertising WPP over TCP at <ip>:5299` and
  `WppTcpServer: connection from <ip>`. Round 3 produced both; this round needs the line after them.
- **The hotspot route still needs 5 GHz.** Below 5180 the handshake completes and the session dies
  within seconds having sent no frame. Check `SoftApInfo`'s frequency before reporting any video
  result.
- All new lines are `AppLog.i` or `.e`, so **INFO is enough**.
- **Keep `auto-enable-hotspot=false`**, exactly as round 3 ran it. On this rig the app takes the
  access point down on exit and cannot bring it back, which voided round 3's first R3 attempt. That
  defect has its own fix on its own branch and is R4 below; until then the setting is the workaround
  and it is not a deviation to report.

---

## 3. Preparation and settings

### Step 0: clear the head unit from the phone, before anything else

Android Auto settings, the stored car, forget. Round 3's R3 ended with the phone looping on a stored
hotspot endpoint, and the operator forgot the head unit before R4 of that round, so the phone should
already be clean. Confirm rather than assume, because every run below is void otherwise. On R1's
first connect the phone must say:

```
GH.WIRELESS.SETUP: No WPP on TCP configuration found in storage for the head unit
```

If it says `Trying to start WPP on TCP with configuration` on R1's **first** connect, the phone is
still carrying round 3's record. Forget it and start again.

### Settings

Written to `shared_prefs/settings.xml` with the app stopped, read back before the first run counts,
per the template and the root-owned `shared_prefs` note in §7a.

**R1 and R2 (hotspot arm):**

```xml
<int name="wifi-connection-mode" value="3" />
<int name="native-ap-transport" value="1" />
<boolean name="native-wifi-version-exchange" value="true" />
<int name="hotspot-band" value="1" />
<boolean name="auto-enable-hotspot" value="false" />
<string name="hotspot-ssid">…</string>
<string name="hotspot-password">…</string>
<string name="static-bssid">…</string>
<string name="hotspot-interface">wlan2</string>
```

**R3 (WiFi Direct regression):** the same, with `native-ap-transport=0` and `wifi-direct-band=1`.

Round 3's values worked and are the ones to reuse: SSID `Navegadortz2`, passphrase `12345678`,
BSSID `00:27:15:43:06:6a`, interface `wlan2`. Bring the access point up on 5 GHz the same way
round 3 did and record `SoftApInfo`'s frequency. Diff `settings.xml` against a fresh backup at the
start and state the delta even if zero.

---

## 4. The lines that decide every run

Verified with `grep -F` against `13a43a6d`. All are ours unless marked.

**The server came up** (once per app start, both transports, unchanged):
```
WppTcpServer: listening for Android Auto on TCP 5299
```

**The endpoint was advertised** (hotspot only, unchanged):
```
NativeAA: advertising WPP over TCP at <ip>:5299
```

**The endpoint was withheld** (WiFi Direct, unchanged, R3's verdict):
```
NativeAA: not advertising WPP over TCP: a WiFi Direct group is renamed every time it is created, and the phone would keep dialling the one it stored
```

**The dial and the handshake.** The second line is the whole round:
```
WppTcpServer: connection from <ip>
WppTcpServer: TLS handshake complete with <ip> (TLSv1.?, TLS_…)
```
**Quote the protocol and cipher verbatim.** They are the proof a certificate was presented and
accepted rather than the handshake merely getting further than last time.

**The handshake failed, with a reason** (unchanged from round 3, and still the most valuable
artifact if R1 fails):
```
WppTcpServer: session error: <ExceptionClass>: <message> <- <CauseClass>: <message>
```
Quote the entire chain including everything after each `<-`, and attach the stack that follows.

**The exchange over TCP**, in order, unchanged:
```
WppTcpServer: [TX] WifiVersionRequest (Type 4) v4.2
WppTcpServer: [RX] Type 5 (<n> bytes)
WppTcpServer: [TX] WifiStartRequest (Type 1) -> <ip>:5288
WppTcpServer: [RX] Type 2 (<n> bytes)
WppTcpServer: [TX] WifiInfoResponse (Type 3) with credentials
WppTcpServer: handshake complete; projection session is up
```

**The session actually landed** (existing line, and the one that always decides):
```
WirelessServer: Incoming connection detected from /<ip>
```

**Phone-side** (tag `GH.*`, Gearhead's own words). The first two were round 3's; the last two are
what a completed handshake should add:
```
Trying to start WPP on TCP with configuration
WPP on TCP connected to the WiFi network
WPP version: <major>.<minor>
WPP starting to listen for messages
```
`WPP on TCP connection failed to read` is round 3's failure and should not appear in a passing R1.

---

## 5. Runs

### R0: build gate

`run_unit_tests.sh` on the coding host. Four classes by name, and the counts matter:

- `WppTcpTlsTest`, **1** test (new this round)
- `WppEndpointPolicyTest`, **5** tests
- `SingleKeyKeyManagerTest`, **4** tests
- `WppMessagesTest`, **11** tests

**PASS**: all four present, whole suite **982/0**. **FAIL**: any failure or missing class; stop, do
not install.

Cleared on the coding host at `13a43a6d`: `BUILD SUCCESSFUL`, `compileGithubDebugKotlin` clean, 982
tests, 0 failures, JDK 17.

`WppTcpTlsTest` opens a loopback socket pair and `SingleKeyKeyManagerTest` reads
`src/main/res/raw/cert` and `privkey` from the module directory. If either fails on a harness
difference rather than an assertion, say which and carry on.

### R1: hotspot, first connect, and whether the TLS handshake completes

Setup: hotspot settings per §3, access point up on 5 GHz, step 0 confirmed. Clean run per the
template.

1. Bring the head unit up with the phone's Bluetooth off, let the access point and credentials
   settle, then enable the phone's Bluetooth.
2. Let the session establish and projection run a minute.

- **PASS**: `advertising WPP over TCP at <ip>:5299`, then `WppTcpServer: connection from`, then
  **`WppTcpServer: TLS handshake complete with <ip> (<protocol>, <cipher>)` at least once**. Quote
  the protocol and cipher. This is the result the last two rounds were trying to reach.
- **PARTIAL**: the handshake completes but the WPP exchange after it does not reach
  `handshake complete; projection session is up`. Quote every `[TX]` and `[RX]` line in order and
  the last stage the log shows. That would be a message-level problem, which is a different and much
  smaller thing than the last two rounds.
- **FAIL**: `TLS handshake complete with` = 0. Quote the whole `session error:` chain and the stack.
  If the cause names a **certificate** rather than an EOF, that is a new and specific finding: it
  means the phone reached our certificate and refused it, and the round has still moved the question
  forward.
- **INCONCLUSIVE**: no `advertising WPP over TCP` line, so the hotspot credentials never resolved.
  Report what `SoftApCredentialsProvider` said and stop the hotspot arm.

Also record: `SoftApInfo`'s frequency, and whether the ordinary AAP session over the hotspot formed
as it did in round 3. It should be untouched by this change.

### R2: hotspot reconnect, the thing the feature exists for

Only if R1 reached PASS. Do not clear the phone: the stored endpoint is the subject.

`headunit://exit`, `am force-stop`, relaunch `MainActivity`, and **do not touch the phone**. Keep the
access point up across the exit, which is what `auto-enable-hotspot=false` is for.

- **PASS**: `WppTcpServer: connection from`, the type 4/5/1/2/3 exchange, and
  `WirelessServer: Incoming connection detected`, **with `NativeAA: Connection accepted from` = 0**,
  meaning no Bluetooth handshake ran at all. This has never been observed and is the whole point of
  the feature.
- **FAIL**: no session forms, or the phone loops on the stored endpoint. Record how many
  `WppTcpServer: connection from` and `session error:` pairs appear and at what interval; round 3
  measured six dials and five errors about 31 s apart.
- Record the wall-clock from relaunch to `Incoming connection detected` and compare it with round
  3's RFCOMM reconnect of 7.85 s. A TCP reconnect skipping Bluetooth entirely should be faster, and
  if it is not, that is worth knowing before anyone calls this an improvement.
- If there is budget, do it a second time. A stored endpoint that works once and not twice is a
  different defect from one that never works.

### R3: WiFi Direct is unchanged

One connect and one reconnect on `native-ap-transport=0`, as round 3's R1 ran.

The accept path changed for both transports, so this is a regression check rather than a new
question. Nothing dials 5299 on WiFi Direct, and the endpoint is still withheld there.

- **PASS**: `advertising WPP over TCP` = 0, at least one
  `not advertising WPP over TCP: a WiFi Direct group is renamed…`, and a session on both the connect
  and the reconnect, as round 3 measured.
- **FAIL**: anything else, and it is a regression from this round's commit.

### R4 (optional, second build): the app stops stranding the hotspot

Round 3 found that on this rig `auto-enable-hotspot=true` lets the app switch the access point off
on exit and then fail every path to switch it back on, leaving the user with no hotspot and no way
back except adb or system settings. `AapService.onDestroy` was taking the access point down
unconditionally, which also undid the restart that the disconnect path had just performed
deliberately. It now leaves it alone.

That fix is **not** in the build above. It rides on the hotspot thread's own branch, as its third
commit:

```bash
git rev-parse fork/fix/809-five-ghz-channel-choice   # 1e265ca3dd007dcbfae242a30564c9220f82cc48
```

That branch is three commits on `2f07eeec` and the other two are the 5 GHz channel work, which is a
different thread with its own brief. Run **only** the check below against it, and read nothing into
its channel behaviour here.

Only if there is budget: build and install that branch, set `auto-enable-hotspot=true`, bring the
access point up, connect, then `headunit://exit`.

- **PASS**: `AapService: Auto-disabling hotspot...` does **not** appear, and the access point is
  still up after the exit (`dumpsys wifi` or `SoftApInfo`). The phone leaving the network is the
  disconnect path's job and is unchanged.
- **FAIL**: the line still appears, or the access point goes down.

Reinstall the round's own build before doing anything else, and say in the results which build each
run was on.

---

## 6. Do not re-run

Rounds 2 and 3 settled these.

- That WiFi Direct can no longer be poisoned. Round 3's R1 measured it: zero endpoints advertised,
  two sessions, phone stored nothing. R3 above is a regression check on one changed code path, not a
  re-litigation.
- That the kill switch works. Round 3's R4, both transports.
- That turning the flag off heals an already-affected phone. It does not. Only forgetting the head
  unit does, which is why §3 step 0 exists.
- That an ordinary Native AA session forms at parity with the flag on. It does, on both transports.
- That the phone negotiates our 4.2 announcement.

---

## 7. Report back

Three answers decide the shipping question:

1. **R1: the `TLS handshake complete with` count, and the protocol and cipher it names.** If zero,
   the full `session error:` chain verbatim, with the stack.
2. **R2: whether a session formed with `NativeAA: Connection accepted from` = 0**, and how long it
   took from relaunch. That is the feature working, and nothing else is.
3. **R3: whether the WiFi Direct arm still behaves as it did in round 3.**

Plus, in Setup notes: the hotspot SSID, BSSID and `SoftApInfo` frequency actually in use, whether
the phone was cleared before R1, and which build each run was on if R4 was attempted.
