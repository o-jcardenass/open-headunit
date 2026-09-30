# native-aa-dsam-wifi-unavailable, round 2 brief

Two parts, run in this order. **Part A** is a read-only pull and decompile of D-SAM's own framework
and needs no build. **Part B** is a hardware round on a candidate build. They answer different
questions, so a problem in one does not stop the other.

## 1. Build and baseline

- Candidate: branch `fix/native-aa-withdrawn-network` on the fork, SHA
  **`5846b249aa93574bfe6e6a23bfe07a59a0b713fa`**, one commit on `main` `d9e51925`. New branch, no
  history rewritten.
  ```bash
  git fetch fork fix/native-aa-withdrawn-network
  git checkout 5846b249aa93574bfe6e6a23bfe07a59a0b713fa
  ```
  JVM suite on that SHA: 2376 tests, 0 failures.
- Baseline: **not re-run.** Round 1's captures on `main` are the baseline
  (`native-aa-dsam-wifi-unavailable-round1-results.md`, release
  `rig-evidence-native-aa-dsam-wifi-unavailable`, asset
  `native-aa-dsam-wifi-unavailable-round1-captures.zip`).

## 2. What this is and why it exists

Round 1's captures, read on both sides, show every failed connect failing the same way:

1. The phone gets Type 3 and starts its join. Android Auto gives the **first** join 7 s
   (`WIRELESS_CONNECTING_WIFI_WITH_TIMEOUT` detail `7000`, then 12000, then 17000).
2. The phone scans cold, associates about 5.8 s in, has no IP by 7 s
   (`Failed to find network within PT7S`), releases its request and asks again. That release
   disassociates it (`reason=3 locally_generated=1` on the phone).
3. **D-SAM's own platform deletes the group the moment its only client leaves**:
   `WifiP2pService: Client list empty, remove non-persistent p2p group`, 11 ms after the
   `AP-STA-DISCONNECTED`, then `P2P-GROUP-REMOVED`. The phone's second try reached the same
   address about 0.1 s too late.
4. The app recreates the group. Below Android 10 the platform names it anew, so the phone spends
   12 + 17 s hunting the old name and then sends `WIFI_NETWORK_UNAVAILABLE(-11)`; the app then
   waits its 15 s refusal back-off. About 45 s lost per occurrence, 2 to 4 occurrences per connect.

Stock Android 4.4.2 would not do step 3: its `createGroup` asks for a persistent group
(`p2pGroupAdd(true)`) and marks it autonomous, and the removal only fires for a
**non**-autonomous group. D-SAM's groups are always temporary (no `[PERSISTENT]` on
`P2P-GROUP-STARTED`) and are removed anyway, so Samsung changed that code. **Part A reads Samsung's
actual code**, to find out whether anything the app controls keeps the group alive.

**Part B tests the candidate**, which does not stop step 3 but stops paying for it: when the group
the phone was sent is taken down while it is still joining, the app ends that handshake at once,
closes the Bluetooth link and wakes the phone again, instead of waiting out its 29 s and the 15 s
back-off. What is not known, and what this round measures, is **what Android Auto does with its
join in flight when the link closes under it.** From its bytecode it treats the drop as retryable
(`Retrying connection attempt on all channels by restarting WPP.`) and restarts its setup after a
delay; whether that also abandons the old join, or leaves it running to its 12 s / 17 s timeouts,
could not be settled from the code. If it keeps running, the candidate buys nothing.

## 3. What is different about this round

- **The trigger is probabilistic.** Step 3 only happens when the phone misses its 7 s window.
  Round 1 hit it on 3 of 5 handshakes. A connect whose first join lands in time never reaches the
  new code, and that connect is not a FAIL. Run until at least **three** connects have hit it, up
  to 10 connects.
- **Capture everything on both devices, unfiltered** (template §2). Round 1's tag filters missed
  the phone's own WiFi lines in run 1. Both logs are needed for every connect.
- D-SAM quirks that apply (§7a): no `-p` on `am`; settings are edited on the host and pushed with
  `run-as ... cp`; check the clock against the host and D-POCO at the start.
- Part A publishes nothing from the unit. **Do not upload the pulled framework files** to a
  release or anywhere else: they are Samsung's firmware. Keep them on the tester PC and put only
  the short excerpts asked for below in the results file.

## 4. Settings

**No setting changes.** D-SAM runs as it did in round 1. Record at the start, with the app stopped:

```bash
PKG=com.andrerinas.headunitrevived
adb -s 30041c35642d2200 shell am force-stop $PKG
adb -s 30041c35642d2200 shell run-as $PKG cat shared_prefs/settings.xml > dsam-settings-round2.xml
```

Put `wifi-connection-mode`, the WiFi Direct band key and `native-poke-bt-macs` in Setup notes.
Do not forget D-SAM on the phone or clear Android Auto's data between connects.

---

## Part A: Samsung's group-removal rule (PC plus one unit, no build)

### A1. Pull, read-only

```bash
S=30041c35642d2200
OUT=~/rig-private/dsam-framework-20260924      # stays on the tester PC, never uploaded
mkdir -p $OUT
adb -s $S shell getprop > $OUT/getprop.txt
adb -s $S shell ls -l /system/framework/ > $OUT/framework-ls.txt
adb -s $S pull /system/framework $OUT/framework
adb -s $S shell ls -l /system/etc/wifi/ > $OUT/etc-wifi-ls.txt
adb -s $S pull /system/etc/wifi $OUT/etc-wifi                  # may partly fail; record what came
adb -s $S shell cat /data/misc/wifi/p2p_supplicant.conf > $OUT/p2p_supplicant.conf 2>&1   # expected to be refused without root; record the answer
```

Then, with the app running and a group up (launch it, wait for `group identity ssid=` in logcat):

```bash
adb -s $S shell dumpsys wifip2p > $OUT/dumpsys-wifip2p.txt
```

### A2. Decompile

Use baksmali 2.5.2 (`https://bitbucket.org/JesusFreke/smali/downloads/baksmali-2.5.2.jar`).
On 4.4 the code sits in `.odex` files next to the jars, so deodex against the pulled directory:

```bash
cd $OUT
for f in services framework; do
  if [ -f framework/$f.odex ]; then
    java -jar baksmali-2.5.2.jar x -a 19 -d framework framework/$f.odex -o smali-$f
  else
    java -jar baksmali-2.5.2.jar d framework/$f.jar -o smali-$f
  fi
done
grep -rlF "Client list empty, remove non-persistent p2p group" smali-*    # which class holds it
```

If the string is in neither, grep every other odex/jar in `framework/` the same way and say which
one held it.

### A3. What to report, as short verbatim smali excerpts in the results file

- **A3a.** The branch that logs `Client list empty, remove non-persistent p2p group`: from the
  `AP_STA_DISCONNECTED` handling to the `p2pGroupRemove` call, with every condition in between.
  Name each field it tests (for example `mAutonomousGroup`) and any Samsung field or method that
  stock Android does not have.
- **A3b.** Every write to each field that condition tests (`iput-boolean ... mAutonomousGroup` and
  the like), each with its enclosing class and method.
- **A3c.** The `CREATE_GROUP` handling in the same service: the message is `0x2200d` (139277). Show
  how its `arg1` selects the `p2pGroupAdd` call and what argument that call gets.
- **A3d.** In `WifiP2pManager` (framework): the body of `createGroup(Channel, ActionListener)`, the
  value it sends as `arg1`, and **every** method whose name contains `Group`, `Persistent` or
  `Autonomous`, public or hidden, with its signature.
- **A3e.** Anything in the P2P service that removes a group on a timer or on idle (search the
  service's smali for `p2pGroupRemove` and list every call site with its enclosing method).
- **A3f.** From `getprop.txt`: every line matching `wifi|p2p|wlan`. From `etc-wifi`: any
  `persistent_reconnect`, `p2p_no_group_iface` or `p2p_go_` line.

No verdict is needed in Part A. The excerpts are the deliverable.

---

## Part B: the candidate on D-SAM with D-POCO

### B0. Identity

Build and install with the rig's script (template §5): `HU=30041c35642d2200 install_and_launch.sh`
at SHA `5846b249`. Then:

```bash
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
send() { a=$1; shift; adb -s 30041c35642d2200 shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; }
send ACTION_QUERY_STATE        # data= must carry commit 5846b249; otherwise stop, the run is void
```

Clock check, one line each, into Setup notes:
`date; adb -s 30041c35642d2200 shell date; adb -s 4f4027e9 shell date`.

### B1. Connect cycles (the point of the round)

Before the first cycle, on both devices: `adb -s <serial> logcat -G 16M`.

Each cycle, `N` = 1, 2, 3, ...:

```bash
# 1. Stop, clear both buffers, start both captures, then arm, in one go
adb -s 30041c35642d2200 shell am force-stop $PKG
adb -s 30041c35642d2200 logcat -c; adb -s 4f4027e9 logcat -c
stdbuf -oL adb -s 30041c35642d2200 logcat -v threadtime > dsam_c$N.logcat & P1=$!
stdbuf -oL adb -s 4f4027e9 logcat -v threadtime > dpoco_c$N.logcat & P2=$!
send ACTION_LOG_MARKER --es text C$N-start
send ACTION_START_WIRELESS_SCAN

# 2. Wait up to 240 s for the session
timeout 240 sh -c 'tail -f dsam_c$0.logcat | grep -m1 -F "SSL handshake complete"' $N \
  || send ACTION_LOG_MARKER --es text C$N-no-session-240s

# 3. Let it project 20 s, then end it the way a user does, and let the phone leave
sleep 20
send ACTION_LOG_MARKER --es text C$N-end
send ACTION_DISCONNECT
sleep 25
kill $P1 $P2
```

`ACTION_START_WIRELESS_SCAN` is the home screen's WiFi button, and `ACTION_DISCONNECT` is Exit on
the projection, which removes the group so the phone leaves it. Each must print
`AutomationReceiver: <action>`; a cycle where either did not is void, not a FAIL.

Stop after the third cycle that shows the trigger (below), or after 10 cycles.

### B2. The lines that decide each cycle

The trigger (D-SAM capture). A cycle **hit** the mechanism when this appears after a
`[TX] Wrote TYPE 3` and before that handshake ended:

```
WifiP2pService: Client list empty, remove non-persistent p2p group
```

The candidate's reaction (D-SAM, new on this build, WARN):

```
NativeAA: the network the phone was sent was taken down while it was joining, so this handshake ends now and the phone is woken for the new one.
NativeAA: Handshake failed — the network the phone was sent was taken down while it was joining.
```

What follows it on D-SAM: `NativeAA: Attempting active poke to device`, then
`NativeAA: Connection accepted from`, `NativeAA: Handling handshake for`, a new
`group identity ssid=` / `[TX] Wrote TYPE 3`, and in the end `Incoming connection detected from` and
`SSL handshake complete`. The old path's lines, which the candidate should make rare:
`WifiConnectStatus status=WIFI_NETWORK_UNAVAILABLE(-11)` and
`the phone has refused this network`.

What Android Auto does (D-POCO, verified in its 17.5 code), after the candidate's line:

```
GH.WIRELESS.SETUP: Retrying connection attempt on all channels by restarting WPP.
GH.WIRELESS.SETUP: Triggering WPP restart. Reason=
GH.WIRELESS.SETUP: Trying to start Wifi Projection Protocol with delay:
GH.WirelessNetRequest: Failed to find network within          <- PT12S / PT17S here mean the old join kept running
GH.WIRELESS.SETUP: Send WifiConnectStatus, status=            <- for the old network, after the drop
GH.WIRELESS.SETUP: State changed to
```

Also read the phone's `wpa_supplicant: wlan0: Trying to associate with SSID` lines: which name it
is hunting after the drop, and when it first tries the new one.

### B3. Verdicts

Grade only cycles that hit the trigger. Cycles that did not are reported but not graded.

- **PASS** (per hit): the candidate's line appears within 5 s of `Client list empty`, **and** the
  phone stops hunting the old name within 5 s of the drop (no further `Trying to associate with SSID
  '<old>'`, no `Failed to find network within PT12S`/`PT17S` for the old network), **and** the next
  `Connection accepted from` on D-SAM comes within 20 s of the drop.
- **FAIL** (per hit): the candidate's line appears, but the phone keeps hunting the old name to its
  own timeout (`PT12S` / `PT17S` for the old SSID) before it reconnects, so nothing was gained. Or
  the candidate's line never appears after a `Client list empty` that followed a Type 3.
- **INCONCLUSIVE**: fewer than three hits in 10 cycles. Report what the hits showed.
- A cycle that ends with no session in 240 s is reported with its last lines on both sides,
  whatever else it shows.

**The round's verdict is PASS only if every graded hit PASSes.**

## 5. Do not re-run

- Anything on `main`: round 1 is the baseline.
- The "background scan deauth" reading in round 1's results: the phone's disconnect in step 2
  is `locally_generated=1` and follows its own `Failed to find network within PT7S`. It is Android
  Auto releasing its request, not a scan.

## 6. Report back

1. Part A: the six excerpts A3a to A3f, verbatim, and which file held the service.
2. Part B, one row per cycle: hit (yes/no); on a hit, seconds from `Client list empty` to the
   candidate's line, to the phone's first association try on the **new** name, and to the next
   `Connection accepted from`; `-11` count; and seconds from `C<N>-start` to `SSL handshake complete`.
3. For the first hit, the phone's `GH.WIRELESS.SETUP` / `GH.WirelessNetRequest` lines from 5 s
   before the drop to the reconnect, verbatim.

Captures: `native-aa-dsam-wifi-unavailable-round2-captures.zip` on release
`rig-evidence-native-aa-dsam-wifi-unavailable`, logcats only (none of Part A's pulled files).
