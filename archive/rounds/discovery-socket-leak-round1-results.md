# Discovery socket leak on the head unit server path, round 1 results

**Candidate:** none (round tests the defect on baseline, not a fix)
**Baseline:** `origin/main` @ `f9b56737`, substituted with `fix/bluetooth-handsfree-link-state` @ `f449557d`
**APK md5:** `5798e770fd9dbf7fe640d67b717edd32`
**Unit:** UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14, serial `27870808938846`. Phone: Redmi
M2007J20CG (`surya_eea`, MIUI, Android 15), serial `4f4027e9`.
**Date:** 2026-08-10

## Setup notes

**Baseline substitution.** `f9b56737` alone does not compile: `NativeAaHandshakeManager.kt:578`
references `handsFreeLinkState`, which is only defined by `f449557d` ("Restore the hands-free link
read the wake poke checks") on branch `fix/bluetooth-handsfree-link-state` (`f9b56737` plus that one
commit). Confirmed the diff between the two is scoped to `BluetoothHelper.kt` only (50 insertions,
0 deletions), nowhere near `NetworkDiscovery.kt`, `CommManager.kt` or `AapTransport.kt`, so it does
not contaminate this round. Built and ran `fix/bluetooth-handsfree-link-state` @ `f449557d`
throughout.

**Scripts used:** `build_hur.sh` (build), `install_and_launch.sh SKIP_BUILD=1` (install/launch),
`set_hu_prefs.sh` (multi-key settings write). All fit as-is; nothing new added.

**Phone-side hotspot could not be started via adb**, contrary to the brief's §6 script
(`adb shell cmd wifi start-softap`). The phone's adb shell is unprivileged (`whoami` returns
`shell`, no root) and MIUI refuses the command outright:
`SecurityException: Uid 2000 does not have access to start-softap wifi command`. This is the same
MIUI-hardening pattern seen before on this phone family, blocking third-party/shell hotspot control.
**User started the hotspot manually** (Settings, one tap): came up as SSID `Navegadortz` on
`wlan1`, `192.168.41.113/24`, WPA2, password supplied by the user out of band. Head unit joined via
`adb shell cmd wifi connect-network Navegadortz wpa2 <password>`, succeeded first try, landed on
`192.168.41.52/24`. **The head unit can join a phone-hosted hotspot on this rig**; this had never
been established before on this channel.

**"Start head unit server" was also started manually by the user** (Android Auto, Developer
settings), as the brief said it must be (no adb route, and this phone has no root).

**R2's stated precondition, "head unit server still running and not restarted since R1", could
not be honored.** R1's second half required restarting the phone's head unit server to recover it
(see below); there is no other known way to clear the deafness on this rig. R2 therefore started
from the freshly-restarted (working) server rather than R1's still-deafened one. This does not
weaken R2: R2 tests whether the *app itself* can independently produce the same condition, which
needs a working starting point to observe the transition into failure, not a pre-broken one.

**Two manual, one-time phone-side actions were needed this round** (hotspot on, head unit server
on) plus **one more mid-round** (head unit server restart, to recover from R1). All three were
communicated to and performed by the user; nothing was faked or skipped to avoid them.

## R1, one silent connection makes the server deaf (the point of the round)

**PASS** (diagnosis confirmed), **with an important correction to the brief's recovery prediction.**

- Settings written: `wifi-connection-mode=2`, `helper-connection-strategy=3`, `log-level=1`
- Radio state: head unit joined phone hotspot `Navegadortz` (WPA2) at `192.168.41.52/24`; phone
  hotspot and head unit server both left running throughout, per brief §3
- Discard-rule check: clean, no `MATCH! Starting AapService`, no `AapRead: Magic Garbage`, single
  continuous capture
- Method: held a silent connection to the phone's own `:5277` from the phone itself
  (`tail -f /dev/null | toybox nc 127.0.0.1 5277`, confirmed `ESTABLISHED` via `netstat`), then
  launched the app on the head unit and captured for the full failure window

**Decisive log lines** (all times 2026-08-10):

```
13:05:15.934  NetworkDiscovery: Found Headunit Server on 192.168.41.113:5277
13:05:15.993  Auto-connecting to Headunit Server at 192.168.41.113:5277 (reusing socket)
13:05:16.244  Handshake: Version request sent. ret: 10. attempt: 1
13:05:18.248  Handshake: No VERSION_RESPONSE within 2s (attempt 1), ret=0
   ... (3 attempts, repeating every ~11s) ...
13:05:22.860  Handshake: Version request/response failed after 3 attempt(s). last ret: 0
13:05:22.869  Handshake failed
```

This is the reporter's exact signature, reproduced on the first cycle and every cycle after:
**8 of 8 cycles failed identically** before the nc connection was killed.

**nc killed** at approximately 13:07:02 (shell wall-clock; confirmed via `netstat` transitioning
`ESTABLISHED` to `FIN_WAIT2` on the phone). The brief's predicted recovery, "within one or two
cycles the next attempt must reach `Handshake: Version response received`", **did not happen.** The
head unit kept failing on the same signature for a further **34 total cycles, about 5m45s from
launch**, through **13:10:56.229** (last `Handshake failed` before recovery). Two independent
recovery levers were tried and both failed:

- **Cycling the phone's hotspot off/on:** no effect (user-confirmed).
- **Simply waiting** (about 4 minutes past the kill, well beyond any plausible protocol timeout): no
  effect.

**Recovery happened only when the user manually stopped and restarted the phone's "head unit
server" developer option**, the reporter's own documented workaround. The very next discovery cycle
succeeded:

```
13:11:00.828  Auto-connecting to Headunit Server at 192.168.41.113:5277 (reusing socket)
13:11:01.010  Handshake: Version response received (ret=12, attempt=1)
13:11:01.123  SSL handshake complete. Session id: 4EhVd4mjU2KaF9+CXTfNWDyRk3c/2g49Ds5840ksoyA=
13:11:04.119  Handshake: Version response received (ret=12, attempt=1)   (2nd cycle, confirms stable)
```

**What this means for the diagnosis:** the core theory (one orphaned connection makes the phone's
head unit server deaf) is confirmed, and more strongly than the brief modeled: the deafness is not
transient and does not clear on its own once the offending TCP connection is closed. It persists
until the server process is explicitly restarted. This is a tighter match to the reporter's actual
symptom (he restarts the server before every drive; simply not having an active competing connection
at connect time would not be enough on its own) and matters for how any fix is framed. **Stopping
HUR from leaking the socket prevents new deafenings, but does not explain or fix recovery**; that is
entirely up to the phone-side server, outside HUR's control. Once deafened (by us or by anything
else), only the user's/reporter's restart clears it.

## R2, does the app produce the condition by itself

**PASS on mechanism, not on the brief's literal log signature.**

- Settings: same as R1, app freshly launched (not carried over from R1's deafened state, see Setup
  notes) and confirmed to connect normally first (`Handshake: Version response received` at
  13:13:06.804, `SSL handshake complete` at 13:13:06.963)
- Trigger: `adb shell svc wifi disable` (13:13:34), `sleep 3`, `svc wifi enable` (13:13:37)
- Attempts: 1 of the brief's allowed 3, stopped here by user decision after attempt 1 landed the
  mechanism-level result (see below); a 2nd/3rd attempt would each need another manual phone-side
  server restart

**What happened:** the WiFi rejoin put the head unit into the identical deaf-server pattern as R1,
entirely from the app's own handling, no external nc, no manual interference:

```
13:13:36.240  [tid 61] NetworkDiscovery: Step 1 - Quick Gateway Scan     (pre-toggle scan, stale subnet)
13:13:36.245  [tid 61] Checking suspects: [10.243.202.1]                (wrong/stale subnet; joined net is 192.168.41.0/24)
13:13:36.849  [tid 61] NetworkDiscovery: Step 2 - Full Subnet Scan
13:13:36.855  [tid 61] Scanning subnet: 10.243.202.*
13:13:39.144  [tid 61] 10.243.202.253:5289 probe failed (last log from this scan job)
13:13:40.767  [tid 68] NetworkMonitor: Network available: 105           (onAvailable fires)
13:13:41.273  [tid 142] NetworkDiscovery: Step 1 - Quick Gateway Scan   (fresh scan, correct subnet)
13:13:41.300  [tid 142] Found Headunit Server on 192.168.41.113:5277
13:13:41.302  [tid 2]   Auto-connecting to Headunit Server (reusing socket)
13:13:47.999  Handshake failed                                          (deafness begins)
```

From 13:13:47.999 to end of capture (13:18:52, about 5m05s), **32 of 32** subsequent handshakes
failed identically; the app never recovered on its own (no further restart was performed in R2, by
design, to see how long it would persist unaided, see "Anything the brief did not ask about").

**Against the brief's 3 literal PASS bullets:**

| Signature | Observed |
|---|---|
| Two `Step 1` lines from different tids, within the same second | Two different tids (61, 142) seen, but 5.0s apart, not the same second. Not a literal match. |
| Two or more `Found Headunit Server` for one `Auto-connecting` | Not observed; each cycle was 1:1 |
| `Gateway scan error` immediately followed by `was cancelled` | Absent entirely. `grep -c "Gateway scan error"` = 0 for the whole R2 capture |

None of the three literal bullets matched. What is clearly established: a stale scan job (tid 61,
holding pre-toggle subnet info for `10.243.202.0/24`, not the network the head unit was actually on)
kept running for several seconds into the post-toggle window, overlapping in wall-clock time with a
second, independently-triggered scan (tid 142) launched by `NetworkCallback.onAvailable`. That is
defect #1 from the brief's write-up (`AapService.registerNetworkMonitor()`'s `stop(); startScan()`
racing against a scan that `stop()` did not actually kill) actually occurring, just not producing
the exact textual footprint predicted. Tid 61's scan never itself succeeded (wrong subnet, every
probe timed out), so it could not directly demonstrate a second orphaned *connection*, only a second
orphaned *scan job*.

**Socket count** (`/proc/net/tcp6`, since all sockets here are IPv4-mapped IPv6; the brief's
`/proc/net/tcp` was empty, used `/proc/net/tcp6` instead, filtering port `149D`), 3 samples taken 3s
apart while a handshake was actively failing (13:16:26 / 29 / 32): **at most 1 row in state `01`
(ESTABLISHED) at any single sample**, alongside 6-7 rows in state `05` (FIN_WAIT2), dying
connections from earlier failed cycles that the head unit has closed its side of but which have not
been fully reaped. **Did not catch more than 1 simultaneous ESTABLISHED row**; the brief's
most-wanted measurement came back as 1, not 2 or more, in these particular samples.

**Counts over the full R2 capture:**

```
Found Headunit Server:            33
Auto-connecting to Headunit:      32
Gateway scan error:                0
Handshake failed:                 32
Handshake: Version response received: 1   (the pre-race baseline connect)
```

## Anything the brief did not ask about

- **The stale-subnet scan (tid 61) used `10.243.202.0/24`**, which matches neither the phone's
  hotspot subnet (`192.168.41.0/24`) nor any other interface seen on either device during this
  round. Whatever cached gateway/subnet state `NetworkDiscovery` was carrying into the WiFi toggle
  was already wrong before the toggle even completed, a second, distinct minor finding from the
  main leak: subnet detection can be stale across a network transition, independent of the socket
  leak itself.
- **The FIN_WAIT2 (head unit side) / CLOSE_WAIT (phone side) pileup is large and monotonically
  growing.** By the end of R1, the phone had accumulated about 24 distinct CLOSE_WAIT sockets to
  `:5277` from 24 distinct source ports, one per failed cycle, none ever cleaned up by either side
  while the deafness persisted. This is consistent with the app's own retry loop continuously
  creating *new* orphans once the *first* one has deafened the server, not just the original
  triggering connection sitting there alone. Whatever fix comes out of this needs to stop the retry
  loop itself from manufacturing fresh orphans while deaf, not just fix the original two-scan race.
- **Restoring the phone's head unit server (R1's recovery lever) also ended R1's own leaked-socket
  pileup on the phone.** After the restart, the CLOSE_WAIT sockets from before were gone (new
  process, new listener). This is expected and matches the reporter's real-world workaround exactly:
  restart clears everything, not just the deafness.
