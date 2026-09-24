# native-aa-dsam-wifi-unavailable, round 3 brief

Part B only. Part A is done (round 2) and is not re-run.

## 1. Build and baseline

- Candidate: branch `fix/native-aa-withdrawn-network` on the fork, SHA
  **`675a0827e88987f5e044790c8cf005867927933e`**. It is round 2's `5846b249` plus one commit; no
  history was rewritten.
  ```bash
  git fetch fork fix/native-aa-withdrawn-network
  git checkout 675a0827e88987f5e044790c8cf005867927933e
  ```
  JVM suite on that SHA: 2380 tests, 0 failures.
- Baseline: round 2's cycle C2 (`native-aa-dsam-wifi-unavailable-round2-results.md`, asset
  `native-aa-dsam-wifi-unavailable-round2-captures.zip`), the hit the candidate missed. Not re-run.

## 2. What changed since round 2, and why

Round 2 showed the candidate cuts the phone's 7/12/17 s join ladder short whenever it notices the
group is gone, and that it noticed only by luck: nothing tied it to the removal itself. D-SAM's
platform announces the removal with `WifiP2pService: sending p2p connection changed broadcast:
DISCONNECTED`, about 3 s after its `Client list empty` line (C2: 15:27:07.571; C5: 15:36:50.420).
The app's handler for that broadcast reset its state and never dropped the credentials. In C5 an
unrelated refresh happened to land 20 ms later; in C2 nothing did, so the phone ran its full ladder
and the app then **re-sent the dead `DIRECT-HU` credentials three more times**.

The new commit drops the Native group's credentials in that broadcast handler, when this unit owned
the group. Everything after it is round 2's code, unchanged: the handshake ends on its next tick,
the phone is woken, and the wake finds no credentials and asks for a new group.

## 3. What is different about this round

- **Stop rule: at least five hits, or 12 cycles**, whichever first. A hit is defined as in round 2.
- Everything round 2's Setup notes learned about D-SAM still applies. Its clock is about 8.5 s behind
  D-POCO's; measure it again at the start and correct every cross-device comparison. Its
  `logcat -c` leaves stale lines, so anchor each cycle on its own `C<N>-start` / `C<N>-end` markers,
  never on a whole-file grep. `-G` is not supported on D-SAM.
- One run at a time: round 2 lost a first attempt to two copies of the cycle script racing.

## 4. Settings

No changes. Record `wifi-connection-mode`, `wifi-direct-band` and `native-poke-bt-macs` from
`settings.xml` at the start, with the app stopped, as in round 2.

## 5. Runs

### R0. Identity

Build and install at `675a0827` with `HU=30041c35642d2200 install_and_launch.sh`, then
`send ACTION_QUERY_STATE` (round 2's `send` helper, §B0 of its brief). `commit` must begin
`675a0827`; a `-dirty` suffix is fine only if `git status` shows no tracked change, as in round 2.

### R1. Connect cycles (the point of the round)

Exactly round 2's §B1 cycle: `partB_cycles.sh` from round 2 is fine if it still does that. Arm with
`send ACTION_START_WIRELESS_SCAN`, wait up to 240 s for `SSL handshake complete`, project 20 s, end
with `send ACTION_DISCONNECT`, wait 25 s. Unfiltered `logcat -v threadtime` on both devices, started
before the arm.

### R2. Optional control on D-HU

One cycle of R1 on D-HU (Android 14) with D-POCO, same build. Its platform keeps the group when the
phone leaves, so the new line must not appear. Skip if D-HU is busy, and say so.

## 6. The lines that decide it

On D-SAM, in order, for a **hit**:

```
WifiP2pService: Client list empty, remove non-persistent p2p group
WifiP2pService: sending p2p connection changed broadcast: DISCONNECTED
WifiDirectManager: the group is being removed (the platform took it down), so its credentials are no longer handed out.
NativeAA: the network the phone was sent was taken down while it was joining, so this handshake ends now and the phone is woken for the new one.
```

Then a new group (`group identity ssid=`), `NativeAA: Connection accepted from`, `NativeAA: Handling
handshake for`, `> Target SSID:` naming the **new** group, and in the end `SSL handshake complete`.

The dead-credential check uses `> Target SSID:`, which prints the name every handshake sends.

## 7. Verdicts

**Per hit**, PASS needs all four:
1. The `(the platform took it down)` line within **0.2 s** after the `DISCONNECTED` broadcast.
2. The `taken down while it was joining` line within **1 s** after that.
3. The phone stops trying the old name within **5 s** of the `DISCONNECTED` broadcast (last
   `wpa_supplicant: wlan0: Trying to associate with SSID '<old>'`, skew-corrected), with no
   `Failed to find network within PT12S` or `PT17S` for the old name.
4. The next `Connection accepted from` within **20 s** of the broadcast.

A hit where line 1 is missing is FAIL, whatever else happens: that is round 2's C2 again.

**Per cycle**, hit or not, PASS needs both:
- No `> Target SSID:` naming a group that already had its `DISCONNECTED` broadcast. That is the C2
  resend.
- The `ACTION_DISCONNECT` exit does **not** print `(the platform took it down)`. The app stops
  listening before it removes its own group, so the line belongs to removals it did not make.

**Round:** PASS only if every hit and every cycle PASSes. INCONCLUSIVE with fewer than five hits in
12 cycles; report the hits there were.

R2, if run: PASS if `(the platform took it down)` never appears on D-HU outside the exit.

## 8. Do not re-run

- Part A, and anything on `main`.
- Round 2's finding that Android Auto runs the full 7/12/17 s ladder when the link stays open. It is
  settled (C2), and this round only needs the link-closed side.

## 9. Report back

1. One row per cycle: hit (yes/no); on a hit, seconds from `DISCONNECTED` to each of lines 1 and 2,
   to the phone's last try on the old name, and to the next `Connection accepted from`; seconds from
   `C<N>-start` to `SSL handshake complete`; `-11` count; any dead-name `> Target SSID:`.
2. Whether any `ACTION_DISCONNECT` exit printed the new line.
3. For the first hit, D-SAM's lines from `Client list empty` to the next `Connection accepted from`,
   verbatim.

Captures: `native-aa-dsam-wifi-unavailable-round3-captures.zip` on release
`rig-evidence-native-aa-dsam-wifi-unavailable`, logcats only.
