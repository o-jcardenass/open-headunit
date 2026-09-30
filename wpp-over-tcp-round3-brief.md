# wpp-over-tcp, round 3 brief: the endpoint is hotspot-only now, and the TLS server has a certificate

**Build:** `fork/fix/wpp-over-tcp` @ `968573ab`, now **three** commits on `2f07eeec` (current `main`).

```bash
git fetch fork
git rev-parse fork/fix/wpp-over-tcp     # 968573abd3abcd6f7f275c927ebe375146e625f3
git rev-parse 2f07eeec                  # the base, unchanged since round 2
```

No history was rewritten. `8ff9510d`, the commit round 2 measured, is still the first of the three,
so a bisect against round 2's findings still lands where it did. The two new commits are:

| SHA | What |
|---|---|
| `894093fc` | The TCP endpoint is advertised only on the hotspot transport, and `native-wifi-version-exchange` goes back to off by default |
| `968573ab` | The TLS server presents its certificate, and a handshake failure names its own cause |

**One build. No baseline APK is needed** — every run below is a settings change on the candidate,
and round 2 already measured the baseline on this rig.

---

## 1. Why this round exists

Round 2 found two independent defects. Both are addressed, and this round measures each separately.

### The endpoint bricked the phone

The endpoint we advertise carries no network name of its own. The phone stores it **together with
the SSID and BSSID** we hand over in the type-3 credentials, as one record, and from then on it
joins that network *instead of* running the Bluetooth handshake again. It has no fallback when the
network is gone: round 2 watched it loop `NETWORK_NOT_FOUND` -> `Restarting WPP over TCP` and form
no session at all until the head unit was forgotten on the phone.

On WiFi Direct that record is stale within the minute, and this app cannot make it otherwise. The
group name is `DIRECT-<two random characters>-<device name>` regenerated on every create, the
passphrase with it, and the group owner interface takes a new random MAC so the BSSID goes too.
Creates happen on every service start, every credential refresh, every 60 s `recoverNativeGroup`,
and every band retry. A persistent group would fix it and is not available: `deletePersistentGroup()`
is rejected on-device for every netId, and reusing a group is what the PROV-DISC retry storm came
from.

So the endpoint is now the **hotspot transport's alone**, where the name, the passphrase and the
BSSID come from the user's own access point or the device's stored configuration and nothing in this
app recycles them. `WppEndpointPolicy` holds that rule and says in the log why it withheld one.

### The TLS server never sent a certificate

Round 2 saw 21 dials to 5299, 20 `session error: connection closed`, and zero completed handshakes,
with `SSL error code 1 / net_error -101` on the phone. `SingleKeyKeyManager` overrode
`chooseEngineClientAlias` but not `chooseEngineServerAlias`. `X509ExtendedKeyManager` defaults both
engine hooks to null and does **not** fall back to the socket variants, and Conscrypt's
engine-backed sockets, which is what `accept()` returns, ask the engine hook. So in server mode no
alias was chosen, no certificate was sent, and the handshake aborted before either end said why.
That is why it failed on every connection with no variation. The AAP path was unaffected because it
is the client half, and that override was already there.

The alias itself is now read back from the keystore rather than assumed, because a PKCS12 store
lowercases aliases where a BKS store does not, and that fails the same silent way. A JVM unit test
covers both hooks against the shipped `res/raw` material.

### The honest limit, stated first

The certificate carries **no CN and no subjectAltName**. If Gearhead's WPP client does ordinary
hostname verification, the handshake will still fail after this change. That is a *different*
failure from round 2's, it would now arrive as a **certificate** error rather than a protocol one,
and the new logging exists to tell them apart. **R2 producing a named certificate error is a useful
result, not a wasted run.** Do not report it as "still broken"; report the line.

---

## 2. What is different about this round

- **R1 is the important half again.** It proves the default transport can no longer be poisoned.
  R2 and R3 are the feature, and the feature has never once worked.
- **One line from round 2 no longer exists.** `NativeAA: WPP TCP server is not listening; advertising
  no endpoint this handshake` was folded into the policy. Its replacement is in §4. A `grep` for the
  old text returning zero is correct, not a missing build.
- **R2 and R3 need the hotspot transport**, which this rig has never run for this thread. §3 and §7a
  carry what that costs: this unit can neither read nor write its own SoftAP config, so the
  credentials must be written by hand, and the band is ACS's choice whatever we ask for.
- **The hotspot route needs 5 GHz.** On 2.4 GHz the handshake completes and the session dies within
  seconds with no frame. If `SoftApInfo`'s frequency is below 5180, that is the cause of any R2/R3
  video failure and not this branch. Check the frequency before reporting either.
- All new lines are `AppLog.i` or `.e`, so **INFO is enough**. No verbose capture needed.
- Port 5299 is unchanged. If something else on the rig holds it,
  `WppTcpServer: could not listen on 5299` prints at E and the whole round is INCONCLUSIVE.

---

## 3. Preparation, settings, and one step that is not optional

### Step 0: clear the head unit from the phone, before anything else

Android Auto settings -> the stored car -> forget. Round 2 ended with the phone clean **and had also
started** carrying a stale `port=5299` record from testing that predated it, which ate an hour before
it was found. Nothing on the head unit can clear it. Confirm it is gone by the phone logging, on the
first connect of R1:

```
GH.WIRELESS.SETUP: No WPP on TCP configuration found in storage for the head unit
```

If instead the phone says `Trying to start WPP on TCP with configuration` on R1's first connect,
**stop**: the clear did not take and every run below is measuring the old record.

### Settings

Written to `shared_prefs/settings.xml` with the app stopped, per §1, and read back before the first
run counts, per §7a's root-owned `shared_prefs` note.

**R1 (WiFi Direct arm):**

```xml
<int name="wifi-connection-mode" value="3" />
<int name="native-ap-transport" value="0" />
<boolean name="native-wifi-version-exchange" value="true" />
<int name="wifi-direct-band" value="1" />
```

**R2 and R3 (hotspot arm)** — same, with:

```xml
<int name="native-ap-transport" value="1" />
<int name="hotspot-band" value="1" />
<string name="hotspot-ssid">…</string>
<string name="hotspot-password">…</string>
<string name="static-bssid">…</string>
```

| Key | Type | Value | Why |
|---|---|---|---|
| `wifi-connection-mode` | int | `3` | Native AA. The only mode this round touches. |
| `native-ap-transport` | int | `0` / `1` | The arm. `0` WiFi Direct, `1` this head unit's own hotspot. |
| `native-wifi-version-exchange` | bool | `true` | **Now off by default**, so it must be set explicitly for every run except R4. |
| `wifi-direct-band` | int | `1` | FORCE_5GHZ, as round 2 carried. R1 only. |
| `hotspot-band` | int | `1` | FORCE_5GHZ. The request is refused on this unit, so this is a statement of intent; `SoftApInfo`'s frequency is the only band truth. |
| `hotspot-ssid` / `hotspot-password` | string | the AP's real ones | **Required on this rig.** It cannot read its own SoftAP config, so without these the app has no credentials to hand over and R2/R3 cannot start. |
| `static-bssid` | string | the AP interface's real MAC | Gearhead joins with a `WifiNetworkSpecifier` matching SSID **and** BSSID under a full mask. Read it from `dumpsys wifi`. A wrong or absent one is `Failed to find network within PT7S`, not a branch defect. |

Bring the access point up and **persist** it before the round, not with a transient
`cmd wifi start-softap`: a transient AP is not the one a restart brings back, and round-8 of the
hotspot thread measured a phone making zero association attempts against an SSID that no longer
existed. Record the SSID, the passphrase, the BSSID and `SoftApInfo`'s frequency in Setup notes.

Diff `settings.xml` against a fresh backup at the start and state the delta, even if zero.

---

## 4. The lines that decide every run

Verified with `grep -F` against `968573ab`. All are ours unless marked.

**The server came up** (once per app start, both transports, unchanged):
```
WppTcpServer: listening for Android Auto on TCP 5299
```

**The endpoint was withheld** — the R1 verdict, new this round. Two forms, and R1 wants the first:
```
NativeAA: not advertising WPP over TCP: a WiFi Direct group is renamed every time it is created, and the phone would keep dialling the one it stored
NativeAA: not advertising WPP over TCP: the WPP TCP server is not listening
```
The second voids R2 and R3 if it appears there, exactly as the old "not listening" line did.

**The endpoint was advertised** — the R2 precondition, text unchanged from round 2:
```
NativeAA: advertising WPP over TCP at <ip>:5299
```

**The phone dialled us, and the handshake finished** — the line round 2 never once produced:
```
WppTcpServer: connection from <ip>
WppTcpServer: TLS handshake complete with <ip> (TLSv1.?, TLS_…)
```
The protocol and cipher in brackets are new. **Quote them verbatim in the results**; they are the
proof a certificate was actually presented rather than the handshake merely getting further.

**The handshake failed, with a reason** — new, and the whole point of the second commit:
```
WppTcpServer: session error: <ExceptionClass>: <message> <- <CauseClass>: <message>
```
Round 2's version of this line said only `session error: connection closed`. Quote the **entire**
line including everything after the `<-` arrows, and attach the stack trace that follows it. This is
the single most valuable artifact of the round if R2 fails.

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

**Phone-side** (tag `GH.*`, Gearhead's own words):
```
No WPP on TCP configuration found in storage for the head unit
Trying to start WPP on TCP with configuration
Connecting to the WiFi network for WPP
GH.WIRELESS.SETUP: State changed to CONNECTING_RFCOMM
WIRELESS_WIFI_SCAN_RESULTS_NETWORK_NOT_FOUND
```

---

## 5. Runs

### R0: build gate

`run_unit_tests.sh`. Three classes by name, and the counts matter:

- `WppEndpointPolicyTest`, **5** tests (new)
- `SingleKeyKeyManagerTest`, **4** tests (new)
- `WppMessagesTest`, **11** tests (was 10; one added for the withheld endpoint)

**PASS**: all three present, whole suite **981/0**. **FAIL**: any failure or missing class; stop, do
not install.

Cleared on the coding host at `968573ab` before the round was queued: `BUILD SUCCESSFUL`,
`compileGithubDebugKotlin` clean, 981 tests across the suite, 0 failures, JDK 17.

`SingleKeyKeyManagerTest` reads `src/main/res/raw/cert` and `privkey` from the module directory. If
it fails with a file-not-found on this rig's Gradle invocation, that is a harness difference and not
a code defect: say so and carry on.

### R1: WiFi Direct can no longer poison the phone — **the point of the round**

Setup: R1 settings per §3. Step 0 done. Head unit Bluetooth on **before** the app launches, phone
Bluetooth on. Clean run per §4.

1. Connect normally, let the session establish, let projection run a minute.
2. `headunit://exit`, `am force-stop`, relaunch `MainActivity`.
3. Let it reconnect, without touching the phone.

- **PASS**: `advertising WPP over TCP` = **0**; at least one
  `not advertising WPP over TCP: a WiFi Direct group is renamed…`; the phone logs
  `No WPP on TCP configuration found in storage`; **and a session forms on both the first connect
  and the reconnect** (`Incoming connection detected` ×2, `SSL handshake complete` ×2).
- **FAIL**: `advertising WPP over TCP` appears at all, or the reconnect produces no session.

The reconnect is the half that directly refutes round 2's R2, where the same sequence produced no
session at all. **Record the wall-clock `createGroup SUCCESS` -> `Incoming connection detected` for
both connects**, so this is comparable with round 2's 5.50 s and not just a count.

**What a PASS would look like if the change did nothing:** it would not. Round 2 ran this exact
sequence with the same settings on `8ff9510d` and got zero sessions on the reconnect. The two states
are distinguishable by the session count alone.

### R2: hotspot, first connect — does the TLS handshake complete now

Setup: hotspot settings per §3, access point up and persisted on 5 GHz, credentials and BSSID
written by hand and read back. **Clear the head unit from the phone again first** — R1 leaves the
phone holding a WiFi Direct record, and a stored SSID that no longer exists is round 2's own trap.

- **PASS**: `advertising WPP over TCP at <ip>:5299` appears, `WppTcpServer: connection from` appears,
  and `WppTcpServer: TLS handshake complete with <ip> (<protocol>, <cipher>)` appears **at least
  once**. Quote the protocol and cipher.
- **PARTIAL, and still valuable**: the dial arrives and the handshake fails, but
  `WppTcpServer: session error:` now names a class and a cause. Quote the whole chain and the stack.
  If the cause names a **certificate**, that is the second wall from §1 and a distinct, useful
  finding.
- **FAIL**: no `connection from` at all, or `session error: connection closed` with no cause chain,
  which would mean the logging change did not land and the APK is wrong.
- **INCONCLUSIVE**: no `advertising WPP over TCP` line, which means the hotspot credentials never
  resolved; report what `SoftApCredentialsProvider` said and stop the hotspot arm here.

Also record: `SoftApInfo`'s frequency, and whether a projection session formed at all. A session that
forms and dies within seconds on a sub-5180 frequency is the band, not this branch.

### R3: hotspot, reconnect — the thing the feature exists for

Only if R2 reached at least PARTIAL with a completed TLS handshake. Do not clear the phone this time:
the stored endpoint is the subject.

`headunit://exit`, `am force-stop`, relaunch, and **do not touch the phone**.

- **PASS**: `WppTcpServer: connection from` and the type 4/5/1/2/3 exchange appear with
  **`NativeAA: Connection accepted from` = 0** — no Bluetooth handshake — and
  `WirelessServer: Incoming connection detected` follows. This has never been observed and is the
  whole point of the feature.
- **FAIL**: no session forms, or the phone loops on the stored network. If it loops, record the SSID
  in the phone's `Trying to start WPP on TCP with configuration` line and whether it matches the
  access point that is currently up. A mismatch on the **hotspot** transport would be a real finding
  and would mean the AP's identity is not as stable as this change assumes.
- Also record whether a second, later reconnect behaves the same, if there is budget.

### R4: kill switch, both transports

`native-wifi-version-exchange=false`, one connect on each transport.

- **PASS**: `advertising WPP over TCP` = 0 and `WppTcpServer: connection from` = 0 on both arms, and
  a session still forms on both.

---

## 6. Do not re-run

Round 2 settled these; the round should not be spent re-proving them.

- That a Native AA session forms at parity with baseline while the version exchange is on. It does:
  5.50 s candidate against 5.26 s baseline, projection 48-50 fps, `dropped=0` every window.
- That the flag off plus a cleared phone equals baseline. It does (round 2 R3b).
- That turning the flag off heals an already-affected phone. It does not (round 2 R3a). Only
  forgetting the head unit on the phone does, which is why §3 step 0 exists.
- That the phone negotiates our 4.2 announcement. It does.

---

## 7. Report back

Three numbers decide the shipping question:

1. **R1: how many sessions formed across the connect and the reconnect**, and how many
   `advertising WPP over TCP` lines appeared. Two and zero is the answer that says the default
   transport is safe.
2. **R2: the `TLS handshake complete with` count, and the protocol and cipher it names.** If zero,
   the full `session error:` chain instead, verbatim.
3. **R3: whether a session formed with `NativeAA: Connection accepted from` = 0.** That is the
   feature working, and nothing else is.

Plus, in Setup notes: the hotspot SSID, BSSID and `SoftApInfo` frequency actually in use, and
whether the phone was successfully cleared before R1 and before R2.
