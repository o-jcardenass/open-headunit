# samsung-driver-native, round 1 addendum: reconnects

Published name: `samsung-driver-native-round1-addendum.md`. Results go into a section
`## Addendum` of `samsung-driver-native-round1-results.md`. Same build (`main` `71375a68`), same
helpers (brief section 5) and same S24 rules (brief section 0) as the round 1 brief. **Every rule in
brief section 0 applies here without change.** Estimated time: 30 min.

## Why

Round 1 tests first connections only. A different fault shows only on a **reconnect**: the phone
stores a WPP-over-TCP endpoint (the group IP and port) with the group's name and BSSID. If the next
group comes up at another IP, the phone dials a subnet that is gone, no dial reaches the head unit,
and only "forget this car" in Android Auto clears it.

A field unit (QUALCOMM Bengal, Android 11) was measured giving its WiFi Direct group a new random
subnet on every create, while its name and BSSID repeated. The identity verdict grades name and
BSSID only on WiFi Direct, so it said `stable=yes` and the endpoint went out each time. D-HU holds
`192.168.49.1` and cannot show this. **Nobody has measured D-POCO as head unit.** On Android 15 it may
move its group IP, its BSSID, both or neither. A0 answers that, and A1 shows what the S24 does
with it.

A phone head unit is also the field case: one reporter drives a Samsung into a Nothing Phone 1.

## Settings (D-POCO, app stopped)

The brief's section 4 keys, plus one read before A0. Do not write it:

```bash
adb -s 4f4027e9 shell run-as com.andrerinas.headunitrevived cat shared_prefs/settings.xml \
  | grep -E 'wifi-direct-last-identity-verdict|wifi-direct-last-group-bssid|wifi-direct-stable-identity'
```

Record the verdict and whether a last BSSID exists (yes or no, never the value).

## The deciding lines

**Head unit capture**, each checked with `grep -F` on `71375a68`:

| Line | Record |
|---|---|
| `WifiDirectManager: Group formed. Owner: true, GO IP:` | the group IP, as `<IP-n>` tokens that stay consistent across the addendum |
| `group identity ssid=` | `stable=` and the reason in brackets; SSID and BSSID as `<SSID-n>` / `<BSSID-n>` tokens |
| `NativeAA: advertising WPP over TCP at` | endpoint sent; IP as its token |
| `not advertising WPP over TCP:` | endpoint withheld, with the reason text |
| `WppTcpServer: connection from` | the phone dialled the endpoint |
| `Native AA user exit` | the exit removed the group |
| `needs this head unit forgotten` | the app saw its own advertised address move |
| `Connection accepted from` | the phone came back over Bluetooth |
| `SSL handshake complete`, `Service Discovery Response` | session formed |

**Phone capture.** Gearhead strings, so they drift. A zero means absent or renamed:

```bash
grep -a -c "No WPP on TCP configuration found in storage" $P
grep -a -c "Trying to start WPP on TCP with configuration" $P
grep -a -o "NETWORK_NOT_FOUND\|TCP_SOCKET_CONNECTION_FAILED\|BSSID_MISMATCH" $P | sort | uniq -c
grep -a -c "Restarting WPP over TCP" $P
grep -a -o "WIRELESS_SETUP_[A-Z_]*" $P | sort | uniq -c
grep -a -c "THROTTLE_LIMIT_EXCEEDED" $P
```

## Runs (Stage B, D-POCO as head unit; S24 bonded to D-POCO only; D-MOTO Bluetooth off)

Run A0 and A1 after R1 and before R2/R3, while the S24 is still paired with D-POCO.

**A0 - does D-POCO move its group, and does it advertise? (6 min, no S24 needed in the grade)**
Three creates. The S24 stays as it is, but the wake list is **empty** and
`native-poke-all-paired` is `false`, so nothing pokes it. For each of `A0-1`, `A0-2`, `A0-3`:
force-stop, start the head unit capture, launch, wait 40 s, `headunit://exit`, wait 5 s, stop the
capture. Grade:

- the three `GO IP:` tokens, the three `BSSID-n` tokens, and the three `stable=` values;
- **IP-moves:** the three IP tokens are not all the same;
- **BSSID-moves:** the three BSSID tokens are not all the same.

No PASS or FAIL. A0 classifies D-POCO into one of four cells. Report the cell:

| | BSSID holds | BSSID moves |
|---|---|---|
| **IP holds** | safe unit, like D-HU | verdict withholds: safe |
| **IP moves** | **the field fault's shape**: A1 should poison | verdict withholds: safe |

If no `GO IP:` line prints (it may print only once a client joins), read the IP from
`adb -s 4f4027e9 shell ip -4 addr show | grep -A2 p2p` taken at +30 s in each create, and say so.

**A1 - S24 reconnect after a user exit, 3 cycles (12 min). The point of the addendum.**
Wake list back to `S24_MAC`. One capture pair per cycle `A1-c` (c = 1, 2, 3), with the brief's
`bringup` window, except the end of the window:

1. `bringup`'s first steps: force-stop, captures, launch, `A1-c-start` marker.
2. Wait for the session: poll the head unit capture every 5 s for `SSL handshake complete`, at most
   150 s. No session in 150 s: mark the cycle `NO-SESSION` and go to step 5.
3. Wait 20 s with the session up.
4. `send ACTION_DISCONNECT`. This is the user exit, which removes the group.
5. `A1-c-end` marker 10 s later, `headunit://exit`, stop both captures.

Cycle 1 forms the record on the phone. Cycles 2 and 3 are the reconnects. Grade each reconnect cycle:

- **RECONNECT-OK:** session formed.
- **POISONED:** no session in 150 s, AND the phone has `Trying to start WPP on TCP with configuration` >= 1
  or `NETWORK_NOT_FOUND` / `TCP_SOCKET_CONNECTION_FAILED` >= 1, AND the head unit has
  `WppTcpServer: connection from` = 0.
- **OTHER FAIL:** no session, and not POISONED. Report the brief's gate (H0, H1, H2) from the same
  greps as R1.

Stop rule: stop A1 at the first POISONED cycle. Then do the recovery below before anything else.

**PASS for A1:** 2 of 2 reconnect cycles RECONNECT-OK. **FAIL:** any POISONED or OTHER FAIL cycle.
Pair the verdict with A0's cell, because a PASS on a "safe" cell proves only that the safe path works.

**Recovery after a POISONED cycle (hand step, operator):** Android Auto > Settings > Previously
connected cars > forget **only** D-POCO's entry. Then one more `bringup A1-r $S24`. Expect a session.
Record whether a first-run screen appeared. Do not reboot or clear the S24.

**A2 - the same cycle on D-HU (4 min). Only if A1 was POISONED.** D-HU holds `192.168.49.1`, so it
is the negative control: the S24 should reconnect where it failed on D-POCO. Runs in Stage C with
the S24 paired to D-HU only. Two cycles as A1. PASS: cycle 2 is RECONNECT-OK.

## Report back

1. A0's cell for D-POCO (IP holds or moves, BSSID holds or moves), with the three `stable=` values.
2. A1: per reconnect cycle, RECONNECT-OK, POISONED or OTHER FAIL, with the endpoint lines that went out
   before it.
3. A2 (if run): RECONNECT-OK or not.

If A0 says D-POCO moves its IP and holds its BSSID, say so first: it is the first rig unit that
shows the field fault, and the WiFi Direct group-IP fix on `fix/forget-car-every-connection` can then be
proved on it.
