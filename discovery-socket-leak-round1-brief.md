# Discovery socket leak on the head unit server path: brief

## 1. Build and baseline

**There is no candidate.** This round runs against the defect, not against a fix.

**Baseline (and the only build):** `origin/main` @ `f9b56737`

```bash
git fetch origin && git checkout f9b56737
```

No history was rewritten. Build with `hur-wifi-test-scripts/build_hur.sh` and install with
`adb install -r`; inventory `hur-wifi-test-scripts/` first and use what is there. Record the md5 as
usual, but there is nothing to A/B against — a single APK covers the whole round.

**R1 needs no build at all** and can be run before the APK finishes, on whatever is already
installed, as long as it is 3.1.1 or later. It tests the *phone*, not us.

## 2. What this is and why it exists

A reporter uses the Android Auto developer option **"Start head unit server"** on his phone, leaves
it running, and connects the head unit over WiFi. Since 3.1.1 he has to stop and restart that server
on the phone before every drive, or the head unit refuses to connect. 3.1.0 did not need that.

His log shows ten attempts, all identical: the TCP connection to the phone's `:5277` is established,
we send the AAP VersionRequest, and the phone answers nothing at all for 3 × 2 s.

```
d2.p | Handshake: No VERSION_RESPONSE within 2s (attempt 1), ret=0
d2.p | Handshake: Version request/response failed after 3 attempt(s). last ret: 0
d2.J | Handshake failed
```

`ret=0` is a read timeout, not EOF — `SocketAccessoryConnection.recvBlocking` maps
`SocketTimeoutException → 0` and EOF/`IOException → -1`. So: **socket open, peer silent.** The
phone's head unit server is talking to a different connection.

The hypothesis is that the other connection is also ours. `NetworkDiscovery` does not probe with a
throwaway socket — `checkPort()` returns the live `Socket`, and for port 5277 it is deliberately
handed to the listener rather than closed:

```kotlin
// NetworkDiscovery.kt, checkAndReport()
val serverSocket = checkPort(ip, 5277, timeout = 300)
if (serverSocket != null) {
    AppLog.i("NetworkDiscovery: Found Headunit Server on $ip:5277")
    reportedIps.add(ip)
    withContext(Dispatchers.Main) {
        // DO NOT CLOSE serverSocket! Pass it to the listener.
        listener.onServiceFound(ip, 5277, serverSocket)
    }
    return true
}
```

Every probe of :5277 is therefore a real, session-grade connection to the phone, and only the
listener can close it. Three things break that ownership on `f9b56737`:

1. **Two scans run at once.** `NetworkDiscovery.stop()` cancels cooperatively and immediately nulls
   `scanJob`, so the next `startScan()` walks past its own `if (scanJob?.isActive == true) return`
   guard. `AapService.registerNetworkMonitor()` does exactly `stop(); startScan()` on
   `NetworkCallback.onAvailable` — added in 3.1.1, unchanged since.
2. **A cancelled scan leaks its open socket.** The handover sits inside
   `withContext(Dispatchers.Main) { … }`. Cancel after `checkPort` connected and that throws
   `CancellationException` *before* the listener runs — nobody takes the socket, nobody closes it,
   and `scanGateways`'s `catch (e: Exception)` swallows the cancellation as
   `NetworkDiscovery: Gateway scan error`.
3. **`CommManager.connect(socket)` drops the socket** when it early-returns on state `Connecting`,
   and the listener's guard only checks `isConnected`, which excludes `Connecting`.

His log catches 1 and 2 twenty milliseconds apart:

```
13:19:34.529 [18077] Found Headunit Server on 10.170.50.59:5277   ← scan A, socket #1
13:19:34.546 [18099] Found Headunit Server on 10.170.50.59:5277   ← scan B, socket #2
13:19:34.548 [2]     Auto-connecting … (reusing socket)           ← B's socket is the one used
13:19:34.550 [18077] NetworkDiscovery: Gateway scan error
13:19:34.550 [18077] e2 was cancelled; job=e2{Cancelling}         ← socket #1 leaked
```

and again a minute later with four scan generations probing :5277 inside 240 ms.

**The one link that is not provable from his log** is the part this round exists for: whether the
phone's head unit server serves a single connection at a time, so that one orphan makes it deaf to
everything after. If it does, the whole story closes and the fix is obvious. If it multiplexes
happily, the leak is untidy but harmless and something else is wrong.

## 3. What is different about this round

**Read this section carefully — this round does not look like any previous one on this rig.**

- **It is not Native AA.** Every round on this channel so far has been `wifi-connection-mode=3`.
  This defect lives in `NetworkDiscovery`, which mode 3 never reaches — `startDiscovery()` opens
  with `if (mode == 3) return`. The round is mode 2, strategy 3.
- **The head unit must be a WiFi *client*.** Defect 1's trigger is `NetworkCallback.onAvailable`,
  which fires when the head unit **joins** a network. §7a records that this rig has no shared regular
  WiFi, so the phone's own hotspot has to be that network. **If the head unit cannot join the phone's
  hotspot, the entire round is UNTESTABLE** — report that and stop. Do not substitute mode 3; it
  cannot show this defect.
- **The phone's head unit server has to be started by hand, once.** Android Auto → three-dot menu →
  *Developer settings* → **Start head unit server**. There is no adb route without root. Leave it
  running for the whole round, and do **not** stop/restart it between runs unless a run says to —
  restarting it is the reporter's workaround and would erase the effect being measured.
- **§4's clean-run protocol does not apply literally.** Its airplane-mode dance is for the Native AA
  Bluetooth path; here the phone's hotspot and head unit server must both stay up across runs.
  Bluetooth is irrelevant to this round — leave both adapters alone.
- **`log-level=1` (DEBUG), not INFO and not VERBOSE.** Every decisive line is I/W/E except
  `Handshake: Version request sent. ret: …`, which is DEBUG and is what separates "our send failed"
  from "their reply never came". VERBOSE buys nothing here and costs ring buffer on this unit.
- R1 is expected to be quick and decisive. R2 may need two or three attempts to land the race; that
  is normal, not a failure — say how many attempts it took.

## 4. Settings keys this round needs

Written into `shared_prefs/settings.xml` with the app stopped, via
`hur-wifi-test-scripts/set_hu_prefs.sh` (multi-key, single relaunch — not `set_hu_pref.sh`). Never
through the UI.

```xml
<int name="wifi-connection-mode" value="2" />
<int name="helper-connection-strategy" value="3" />
<int name="log-level" value="1" />
```

Nothing changes between runs. `wifi-connection-mode=2` + `helper-connection-strategy=3` is the
reporter's exact configuration and the only one that reaches this code:

```kotlin
// AapService.startDiscovery()
if (mode == 3) return
// Allow discovery for Strategy 0 (NSD), 3 (Phone Hotspot) and 4 (Headunit Hotspot)
if (mode == 2 && strategy != 0 && strategy != 3 && strategy != 4) return
```

Back the file up first (§1) — this rig's usual rounds leave `wifi-connection-mode=3` in place and
you will want it back at the end.

## 5. The lines that decide every run

Verified with `grep -F` against `f9b56737`. Match on the message text after the `|`, never the
minified class name.

```
NetworkDiscovery: Starting scan...
NetworkDiscovery: Scan interrupted
NetworkDiscovery: Step 1 - Quick Gateway Scan
NetworkDiscovery: Checking suspects:
NetworkDiscovery: Found Headunit Server on
NetworkDiscovery: Gateway found service, skipping subnet scan.
NetworkDiscovery: Step 2 - Full Subnet Scan
NetworkDiscovery: Gateway scan error
Auto-connecting to Headunit Server at
Socket low-latency options: tcpNoDelay=
Start Aap transport handshake for
Handshake: Version request sent. ret:
Handshake: No VERSION_RESPONSE within 2s (attempt
Handshake: Version request/response failed after
Handshake: Version response received (ret=
Handshake failed
```

`Handshake: Version response received` is the success marker for this round. `SSL handshake
complete` is DEBUG too and will also appear on a good run, but the version exchange is the thing
under test — a run that gets that far has proved its point.

## 6. Setup, once, before any run

```bash
# phone: bring up the hotspot the head unit will join
adb -s <phone> shell cmd wifi start-softap OHU773 wpa2 testtest1234 -b 2
adb -s <phone> shell ip -4 addr            # the AP address — this is the target, call it PHONE_IP

# head unit: join it
adb -s <hu> shell cmd wifi connect-network OHU773 wpa2 testtest1234
adb -s <hu> shell ip -4 addr               # confirm an address on the same /24
```

If `cmd wifi connect-network` is refused or the head unit will not associate, that is the
UNTESTABLE stop condition from §3. Report what the command said.

Then, on the phone by hand: Android Auto → *Developer settings* → **Start head unit server**.
Confirm it is actually listening before starting R1:

```bash
adb -s <hu> shell toybox nc -v $PHONE_IP 5277 </dev/null    # should connect, then close
```

## 7. Runs

### R1: one silent connection makes the server deaf  ← **this is the point of the round**

The whole diagnosis rests on the phone's head unit server serving one connection at a time. This
run tests that directly and needs no candidate build, because it does not test our code at all.

Occupy the server with a connection that never speaks, and hold it:

```bash
adb -s <phone> shell toybox nc --help          # check availability first
adb -s <phone> shell toybox nc 127.0.0.1 5277  # connect, send nothing, leave it running
```

If the phone has no usable `nc`, do the same from the head unit against the phone
(`adb -s <hu> shell toybox nc $PHONE_IP 5277`); if neither device has one, the run is
**INCONCLUSIVE** — say so, and R2 still stands on its own.

With that connection held, start the capture (§2) and launch the app on the head unit. Give it 90 s,
which is three or four discovery cycles.

- **PASS — the diagnosis is confirmed.** The head unit reproduces the reporter's signature:
  `Auto-connecting to Headunit Server at`, then `Handshake: Version request sent. ret:` with a
  non-negative `ret`, then `Handshake: No VERSION_RESPONSE within 2s` ×3 and `Handshake failed`,
  repeating. Then **kill the `nc` and leave everything else alone** — within one or two cycles the
  next attempt must reach `Handshake: Version response received`.
- **FAIL — the diagnosis is wrong.** The head unit connects normally with the extra connection held.
  The server multiplexes, an orphaned socket is harmless, and something else explains the reporter's
  failure. **Report and stop; do not run R2.**

The second half — killing the `nc` and watching it recover — matters as much as the first. A run
where it fails with the `nc` held *and* keeps failing after it is gone has proved nothing about the
mechanism, only that the rig cannot connect.

### R2: the app produces the condition by itself

Only if R1 passed. Head unit joined to the hotspot, head unit server still running and **not**
restarted since R1, app running with §4's settings.

Force the `onAvailable` race by making the head unit rejoin the network:

```bash
adb -s <hu> shell svc wifi disable; sleep 3; adb -s <hu> shell svc wifi enable
```

`onAvailable` fires ~500 ms after the rejoin, while `startDiscovery()`'s scan is still probing. Give
it 90 s. Repeat up to three times if the first attempt does not land the overlap.

- **PASS — the defect is reproduced.** Any one of these in the capture:
  - two `NetworkDiscovery: Step 1 - Quick Gateway Scan` lines from **different tids** within the same
    second;
  - two or more `NetworkDiscovery: Found Headunit Server on` for a single
    `Auto-connecting to Headunit Server at`;
  - a `NetworkDiscovery: Gateway scan error` immediately followed by a `was cancelled` line.
- **FAIL — the race does not occur on this rig.** None of the three appears across three attempts.
  Record it: it would mean `onAvailable` does not fire on this unit's rejoin, and the reporter's
  trigger is something else.

Count the sockets directly while a handshake is failing. Port 5277 is `149D` hex; state `01` is
ESTABLISHED:

```bash
adb -s <hu> shell run-as com.andrerinas.headunitrevived cat /proc/net/tcp | grep -i 149D
```

**More than one ESTABLISHED row to the phone while a single handshake is in flight is the leak,
measured.** That is the most valuable number in the round. If `run-as … cat /proc/net/tcp` returns
nothing on this Android 14 build, say so in Setup notes and fall back to the log counts above.

Also report these four counts over the whole capture — on a healthy app the first two are equal:

```bash
grep -c "Found Headunit Server"        rN.txt
grep -c "Auto-connecting to Headunit"  rN.txt
grep -c "Gateway scan error"           rN.txt
grep -c "Handshake failed"             rN.txt
```

## 8. Do not re-run

- Anything on `wifi-connection-mode=3`. `startDiscovery()` returns before it does any work, so no
  Native AA run can show this defect, however it is set up.
- Anything about Bluetooth, A2DP, HFP or the poke. This round does not touch that path and §7a's
  link-state cautions do not apply to it.
- USB anything (no accessory path on this rig).
- The reporter's own workaround — stopping and restarting the head unit server. It is known to work;
  doing it mid-round destroys the state the round is measuring.

## 9. Report back

Three things decide what gets written next:

1. **R1's verdict.** PASS means one orphaned connection is enough to make the phone's head unit
   server deaf, and the fix is exactly "stop creating orphans". FAIL means the mechanism is wrong and
   the investigation restarts.
2. **R1's recovery half.** Whether killing the `nc` brought the very next attempt back to
   `Handshake: Version response received`.
3. **R2's socket count** from `/proc/net/tcp` — how many ESTABLISHED connections to the phone's
   :5277 exist while one handshake is failing. One means no leak on this rig; two or more is the
   defect, measured rather than inferred.

Plus, whichever way it goes: whether the head unit could join the phone's hotspot at all, since that
determines whether this rig can ever cover this code path.

Restore `wifi-connection-mode` (and the rest of the backed-up `settings.xml`) when the round is
done, so the next Native AA round does not start from this configuration.
