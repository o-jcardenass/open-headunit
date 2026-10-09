# wpp-over-tcp, round 5 brief: finish the exchange, and clear the regression check properly

**Build:** `fork/fix/wpp-over-tcp` @ `ddf8b198`, now **six** commits on `2f07eeec` (current `main`).

```bash
git fetch fork
git rev-parse fork/fix/wpp-over-tcp     # ddf8b198...
git rev-parse 2f07eeec                  # the base, unchanged since round 2
```

No history was rewritten. The first four commits are the ones rounds 2 to 4 measured, in order. The
two new ones:

| SHA | What |
|---|---|
| `a62cc22b` | The session machine finishes an exchange with a phone that needs no credentials |
| `ddf8b198` | The WPP control channel is held open after the handshake, answering the phone's pings |

**One build for R1 to R3.** R4 is the optional second build and is unchanged from round 4's R4,
which was not run.

---

## 1. Why this round exists

Round 4 got the feature working: TLS completed on every dial, and the hotspot reconnect formed a
full session with no Bluetooth handshake at all, twice. Two things were left.

### The exchange never finished

R1 measured the phone sending `4 -> 5 -> 1 -> 7 -> 6` and stopping. It never sends type 2, because
on the hotspot it dialled us **from inside the network it is already on** and has nothing to ask us
for. The session machine sat in `AWAIT_INFO_REQUEST` waiting for that type 2, so
`handshake complete; projection session is up` was never logged, and the caller fed it a
session-is-up event every tick that nothing consumed. Projection was up and stable throughout, so
the only visible cost was a phone-side read timeout ten seconds later on a channel nothing was
driving. The round's reading of the source was exactly right.

`a62cc22b` makes a successful `WifiConnectStatus` mean what it says when it arrives before any
credential request, and makes the projection session landing complete the handshake from any stage
rather than only from settling. That second half is also why a stage with no handler could spin at
all: the caller feeds that event on every tick once the session is up, so a stage that ignored it
never reached its own timeout either.

### Finishing it exposed a second question, which is why there is a second commit

A completed exchange used to close the socket. That would have been wrong. Gearhead pings this
channel for the life of the projection session and counts the answers: `WPP_PING_TIMEOUT` and
`WPP_SOCKET_CLOSED_BY_PEER` are both connection-failure reasons in its own code, and
`restart_wpp_on_ping_timeout` is a flag. So `ddf8b198` holds the channel open and answers pings
until the phone closes it or the session ends.

**This is the one thing in the round that could regress what round 4 measured**, because round 4's
sessions ran with this channel dead. If projection is less stable this round than last, this commit
is the first suspect and R1's throughput windows are how it would show.

### The regression check was contaminated, not failed

Round 4's R3 reconnect failed on a WPP-TCP config the **phone** had cached during that round's own
hotspot runs, in a Gearhead process that was never restarted. Nothing on the branch touches the WiFi
Direct association path. The round's own analysis said so and asked for a clean re-run, and §3 makes
the ordering that guarantees one a numbered step.

`ddf8b198`'s sibling change also gives the withheld-endpoint line a tail saying how to clear such a
config, since withholding one has never been able to.

---

## 2. What is different about this round

- **Run order is not free this time.** R1 (WiFi Direct) comes **first**, on a phone that has done no
  hotspot WPP-TCP session in the life of its current Gearhead process. Doing the hotspot arm first
  is what made round 4's R3 unscoreable. This is step 0.
- **Two new lines to look for**, both `AppLog.i`, both in §4: the handshake completing, and the
  control channel being held.
- **One line changes text.** The WiFi Direct withhold now continues past "the one it stored" with a
  sentence about forgetting the head unit on the phone. A `grep -F` on the old text still matches
  the start of it.
- **INFO is enough**, as in rounds 3 and 4. Pings are answered without a log line on purpose, so a
  quiet control channel during projection is correct and not evidence of anything.
- The hotspot route still needs **5 GHz**; below 5180 the session dies within seconds having sent no
  frame, and that is the band, not this branch.
- Keep `auto-enable-hotspot=false` for R1 to R3, as round 4 did. It is the workaround for a defect
  whose fix is R4.

---

## 3. Preparation

### Step 0: a clean phone, and the WiFi Direct arm before the hotspot arm

Forget the head unit in the phone's Android Auto settings. Then run **R1 first**, before any hotspot
run. Confirm on R1's first connect:

```
GH.WIRELESS.SETUP: No WPP on TCP configuration found in storage for the head unit
```

If the phone says `Trying to start WPP on TCP with configuration` anywhere in R1, its Gearhead
process is still carrying a cached endpoint. Force-stopping Gearhead clears it, but prefer the
forget plus the run order: a force-stop has cost another thread's rig state before, and R1 is the
run that most needs to be above suspicion.

### Settings

Written to `shared_prefs/settings.xml` with the app stopped, read back before the first run counts.

**R1 (WiFi Direct, first):**

```xml
<int name="wifi-connection-mode" value="3" />
<int name="native-ap-transport" value="0" />
<boolean name="native-wifi-version-exchange" value="true" />
<int name="wifi-direct-band" value="1" />
<string name="hotspot-ssid"></string>
<string name="hotspot-password"></string>
<string name="static-bssid">0</string>
<string name="hotspot-interface"></string>
```

The four empty ones are not decoration. Round 4's first R3 attempt was void because `static-bssid`
and `hotspot-ssid` were still carrying the hotspot arm's values, which forced a P2P group onto the
hotspot's BSSID and the phone could not associate.

**R2 and R3 (hotspot):** `native-ap-transport=1`, `hotspot-band=1`,
`auto-enable-hotspot=false`, and round 4's working values, `hotspot-ssid=Navegadortz2`,
`hotspot-password=12345678`, `static-bssid=00:27:15:43:06:6a`, `hotspot-interface=wlan2`. Bring the
access point up on 5 GHz the same way and record `SoftApInfo`'s frequency.

Diff `settings.xml` against a fresh backup at the start and state the delta even if zero.

---

## 4. The lines that decide every run

Verified with `grep -F` against `ddf8b198`.

**The exchange completes.** New, and the point of R2:
```
WppTcpServer: handshake complete; projection session is up
```

**The channel is then held.** New, once per completed handshake:
```
WppTcpServer: holding the control channel open for the session
```
and one of these when it ends:
```
WppTcpServer: the phone closed the control channel after <n> pings
WppTcpServer: the session ended; closing the control channel after <n> pings
```
**Quote the ping count.** It is the first measurement anyone has of whether the phone pings this
channel at all, and it decides whether `ddf8b198` was necessary or merely harmless.

**The endpoint was withheld.** Text extended; R1's verdict:
```
NativeAA: not advertising WPP over TCP: a WiFi Direct group is renamed every time it is created, and the phone would keep dialling the one it stored. Withholding one does not clear one the phone already has: ...
```

**Unchanged, and still decisive:**
```
NativeAA: advertising WPP over TCP at <ip>:5299
WppTcpServer: connection from <ip>
WppTcpServer: TLS handshake complete with <ip> (TLSv1.2, TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256)
WppTcpServer: [TX] WifiVersionRequest (Type 4) v4.2
WppTcpServer: [RX] Type 5 / 7 / 6
WirelessServer: Incoming connection detected from /<ip>
```

**Should not appear at all this round:**
```
WppTcpServer: session error:
WppTcpServer: phone closed the socket in stage
```

**Phone-side** (tag `GH.*`):
```
No WPP on TCP configuration found in storage for the head unit
Trying to start WPP on TCP with configuration
WPP on TCP connected to the WiFi network
WPP on TCP connection failed to read          <- round 4 got this ~10 s after every connect; it should be gone
WPP on TCP connection was closed by the peer  <- acceptable at the end of a session, not during one
```

---

## 5. Runs

### R0: build gate

`run_unit_tests.sh` on the coding host. Counts that changed:

- `WppHandshakeSessionTest`, **30** tests (was 27; three added for the credential-free shape)
- `WppEndpointPolicyTest`, **6** tests (was 5)
- `WppTcpTlsTest` 1, `SingleKeyKeyManagerTest` 4, `WppMessagesTest` 11, all unchanged
- whole suite **986/0**

Cleared on the coding host at `ddf8b198`: `BUILD SUCCESSFUL`, `compileGithubDebugKotlin` clean,
986 tests, 0 failures, JDK 17.

### R1: WiFi Direct, on a clean phone, connect and reconnect

**First run of the round.** Settings per §3, step 0 done.

1. Connect, let the session establish and projection run a minute.
2. `headunit://exit`, `am force-stop`, relaunch `MainActivity`, phone untouched.

- **PASS**: `advertising WPP over TCP` = **0**, at least one `not advertising WPP over TCP: a WiFi
  Direct group is renamed…`, the phone never logs `Trying to start WPP on TCP`, and **a session
  forms on both the connect and the reconnect**.
- **FAIL**: no session on the reconnect *and* the phone logs no `Trying to start WPP on TCP`. That
  would be a real regression, since the contamination round 4 hit announces itself in that line.
- **INCONCLUSIVE**: the phone logs `Trying to start WPP on TCP` anywhere. Step 0 did not take;
  redo it before reading anything into the result.

This is round 4's R3 with the ordering fixed, and it is the formally open regression check.

### R2: hotspot, first connect, and the exchange finishing

Settings per §3, access point up on 5 GHz. Clear the head unit from the phone again first, since R1
leaves it holding a WiFi Direct record.

- **PASS**: the type 4/5/7/6 exchange as in round 4, **then**
  `WppTcpServer: handshake complete; projection session is up`, **then**
  `WppTcpServer: holding the control channel open for the session`. Projection forms and runs.
- **PARTIAL**: TLS and the exchange run as in round 4 but `handshake complete` never appears. Quote
  every `[TX]` and `[RX]` line in order; that would mean the phone ends the exchange in a third
  shape neither round has seen.
- **FAIL**: TLS does not complete, or `session error:` appears. That would be a regression from
  round 4.

Record, and this is the half that matters most beyond the pass:

- **Throughput windows for at least two minutes**, and compare them with round 4's 50 to 51 fps,
  `dropped=0` in every window. `ddf8b198` puts traffic on a channel that was silent when round 4
  measured that, so any difference here is attributable and worth knowing.
- The **ping count** on the line that closes the channel, and whether
  `WPP on TCP connection failed to read` still appears on the phone. Round 4 got it about ten
  seconds after every connect.

### R3: hotspot reconnect

Do not clear the phone: the stored endpoint is the subject. `headunit://exit`, `am force-stop`,
relaunch, phone untouched, access point kept up.

- **PASS**: a session forms with `NativeAA: Connection accepted from` = **0**, as round 4 measured
  twice, **and** the exchange now reaches `handshake complete`. Record relaunch to
  `Incoming connection detected` and compare with round 4's 7.4 s and 5.5 s.
- **FAIL**: no session, or the phone loops on the endpoint. Record the dial and error counts and the
  interval.
- Twice if there is budget, as in round 4.

### R4 (optional, second build): the app stops stranding the hotspot

Unchanged from round 4's R4, which was not run. One commit, now the third on the hotspot thread's
own branch:

```bash
git rev-parse fork/fix/809-five-ghz-channel-choice   # 1e265ca3dd007dcbfae242a30564c9220f82cc48
```

The branch's other two commits are the 5 GHz channel thread and are not part of this round; read
nothing into its channel behaviour here.

Build and install it, set `auto-enable-hotspot=true`, bring the access point up, connect, then
`headunit://exit`.

- **PASS**: `AapService: Auto-disabling hotspot...` does **not** appear and the access point is still
  up after the exit.
- **FAIL**: the line appears, or the access point goes down.

Reinstall the round's own build afterwards and say which build each run was on.

---

## 6. Do not re-run

- That TLS completes on the hotspot dial. Round 4: every dial,
  `TLSv1.2, TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256`, `session error` = 0.
- That the hotspot reconnect forms a session with no Bluetooth handshake. Round 4 R2, twice. R3
  above re-runs it only because this round changes what happens after the handshake.
- That WiFi Direct never advertises an endpoint. Rounds 3 and 4 both measured zero.
- That the kill switch works, and that turning the flag off does not heal an affected phone.

---

## 7. Report back

1. **R1: did a session form on both the connect and the reconnect**, and did the phone log
   `Trying to start WPP on TCP` at any point. That closes the regression check round 4 could not.
2. **R2: does `handshake complete; projection session is up` appear**, and what do the throughput
   windows look like next to round 4's.
3. **The ping count** on the control-channel closing line, from any run that has one.
4. **R3: relaunch to session, with `Connection accepted from` = 0.**

Plus the usual setup notes: hotspot SSID, BSSID and `SoftApInfo` frequency, whether the phone was
cleared before R1 and before R2, and which build each run was on.
