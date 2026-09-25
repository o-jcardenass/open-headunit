# hold-aa-rfcomm, round 1 brief

## 1. Build and baseline

- Candidate: branch `fix/hold-aa-rfcomm` on the fork, SHA **`bf091a02`** (`main` `7db89757` plus one commit). JVM suite on that SHA: 2402 tests, 0 failures.
- Control: `main` at **`7db89757`**, for R1 only.
  ```bash
  git fetch fork fix/hold-aa-rfcomm main
  git checkout bf091a02   # or 7db89757 for R1
  ```
- What the candidate changes, in the Native AA Bluetooth handshake only:
  - Once the phone's projection session lands, the app **keeps the Android Auto RFCOMM channel open** for the rest of the session and answers the phone's WiFi-projection pings (type 8 with type 9).
  - It still closes the listeners, so no second connection is accepted.
  - It releases the channel when the session ends, or earlier if the phone closes it.
  - Before this build, the app closed the channel within about a second of the session landing.

## 2. What this is and why it exists

- **The phone re-dials a closed channel.** Android Auto 17.8's own code treats that channel as the phone's to hold for the whole session, and pings over it once a second. When the head unit closes it, the phone restarts its wireless-projection setup and re-dials the channel at once. The head unit's listener is closed, so the dial is refused, and it retries every 5 s. It keeps doing this for as long as a Bluetooth profile (hands-free, media or contacts) keeps the head unit "present".
- **That costs the video its airtime.** On a head unit whose WiFi and Bluetooth share one 2.4 GHz radio, every retry takes airtime from the video. A reporter's unit stutters with the phone's Bluetooth profiles connected and is smooth with them off, while the channel handling is identical in both.
- **What this round measures:** whether the phone stops retrying once the channel is held, and whether holding breaks anything. This rig's 5 GHz link will not show the stutter itself.

## 3. What is different about this round

- **Units.** D-HU is the head unit and D-MOTO is the phone.
- **Phone logcat is the primary instrument this round.** Capture D-MOTO's logcat unfiltered for every run, from before the phone's airplane mode goes off (§4) to the end of the run, beside D-HU's capture.
- **The precondition is a hands-free link between D-MOTO and D-HU during the session.** The retry only happens while a profile keeps the head unit present. Before each run's arm, and again 60 s into the session, record:
  ```bash
  adb -s <D-HU> shell dumpsys bluetooth_manager | grep -iA3 "HeadsetClient"
  ```
  - If D-MOTO is not listed as connected there, reconnect it from D-MOTO's own Bluetooth settings for D-HU, with calls enabled. That is the phone's system settings, not the app.
  - If it will not stay connected, grade the run **INCONCLUSIVE (precondition)** and say so. Do not grade it PASS or FAIL.
- **Transport and band as found.** Record `native-ap-transport` and the session's frequency. Do not change them.
- **Log level INFO** (`log-level=2`). Every app line below prints at INFO.

## 4. Settings

With the app stopped (§1):

| Key | Element |
|---|---|
| `log-level` | `<int name="log-level" value="2" />` |
| `wifi-connection-mode` | `<int name="wifi-connection-mode" value="3" />` |

Record `native-ap-transport`, `wifi-direct-band` and `stand-down-station-mode` as found.

## 5. Runs

Each run follows the clean-run protocol (§4), with markers from `send ACTION_LOG_MARKER --es text <Rn-step>`. Once SSL is up, start music on the phone (`adb -s <D-MOTO> shell input keyevent KEYCODE_MEDIA_PLAY`).

### R0. Identity
`send ACTION_QUERY_STATE`. `commit` must begin `7db89757` for R1 and `bf091a02` for everything else.

### R1. Control, `main`, hands-free held (the retry we expect to remove)
1. Arm with `send ACTION_START_WIRELESS_SCAN`, and wait for `SSL handshake complete`.
2. Hold the session for **5 minutes**, then `send ACTION_DISCONNECT`.

### R2. Candidate, hands-free held (the point of the round)
R1's steps on `bf091a02`, for **10 minutes**.

### R3. Candidate, exit and reconnect, 3 cycles
From a live session:
1. `send ACTION_DISCONNECT`, and wait for the app's release line.
2. Re-arm with `send ACTION_START_WIRELESS_SCAN`.
3. Wait up to 90 s for `SSL handshake complete`.

Repeat 3 times.

### R4. Candidate, the phone's Bluetooth drops mid-session
1. In a live session, 60 s in: `adb -s <D-MOTO> shell cmd bluetooth_manager disable`.
2. 30 s later: `adb -s <D-MOTO> shell cmd bluetooth_manager enable`.
3. Run 2 more minutes.

If `cmd bluetooth_manager` is refused on D-MOTO, record the refusal and skip R4.

### R5. Candidate, no profile link
- Disconnect D-HU in D-MOTO's Bluetooth settings, without unpairing, so that no profile is up.
- Then run R2's steps for 3 minutes.
- Record whether the session still reached SSL. The handshake itself still runs over Bluetooth, which is expected.

## 6. The lines that decide it

App (D-HU), verbatim from `bf091a02`:
```
NativeAA: WiFi session landed. Holding the Bluetooth channel for the session and answering the phone's pings, as a head unit does.
NativeAA: [HOLD] Bluetooth channel held Ns, N pings answered.
NativeAA: the session ended; releasing the held Bluetooth channel after Ns and N pings.
NativeAA: the phone closed the held Bluetooth channel after Ns and N pings; the Android Auto listeners reopen when this session ends.
NativeAA: BT Handshake link closed.
```
On `7db89757`, the landing line reads `WiFi session landed. Handshake session ending, releasing Bluetooth connection.` instead.

Phone (D-MOTO). Count each over the window from `SSL handshake complete` on D-HU to the disconnect marker, matched case-insensitively:
```
Triggering WPP restart
Attempting to connect Bluetooth RFCOMM
Rfcomm socket connection failed
CONNECTION_CLOSED_BY_PEER
has not received the ping response
timed out on pings
PING_STATE_HEALTHY
```
Also run `grep -ciE "rfcomm|wpp"` over that window, and quote the first 20 matching lines of R1 and of R2.

## 7. What each run answers

- **R1 (control).** `Triggering WPP restart` and `Attempting to connect Bluetooth RFCOMM` recurring after SSL, a few seconds apart, confirms the mechanism on this rig. If they are absent, R2 cannot show a difference: grade R2 **INCONCLUSIVE (no retry in control)** and report R1's phone lines in full.
- **R2 PASS** needs all of these:
  - the holding line once;
  - `[HOLD]` lines with a ping count growing about 60 per minute;
  - zero `Triggering WPP restart`, `Attempting to connect Bluetooth RFCOMM` and `has not received the ping response` on the phone between SSL and the disconnect marker;
  - the session alive for the full 10 minutes;
  - the release line at the end.
- **R3 PASS:** all 3 cycles reach `SSL handshake complete` within 90 s, and each exit prints the release line.
- **R4.** Report what happened, in order:
  - the app's `phone closed the held Bluetooth channel` line;
  - whether the AAP session survived the Bluetooth drop (it should, because it runs over WiFi);
  - whether the phone retried RFCOMM after Bluetooth came back.

  **This is a measurement, not a grade.**
- **R5 PASS:** SSL is reached, and the session holds 3 minutes.
- Across all runs, report `Throughput over` fps and `inbound link quiet` counts per run, so the two builds can be compared, even though 5 GHz is not expected to stutter.

## 8. Report back

1. One row per run: build, precondition as read (HeadsetClient state before arm and at 60 s), SSL time, duration, the phone's counts from §6, the app's hold and release lines, and the verdict.
2. The first 20 phone-side `rfcomm|wpp` lines after SSL for R1 and R2, verbatim.

Captures: `hold-aa-rfcomm-round1-captures.zip` on release `rig-evidence-hold-aa-rfcomm`, with both units' logcats.
