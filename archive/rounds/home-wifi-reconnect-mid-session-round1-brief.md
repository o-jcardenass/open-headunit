# home-wifi-reconnect-mid-session, round 1 brief

Published name on the transfer branch: `home-wifi-reconnect-mid-session-round1-brief.md`.

## 1. Build and baseline

- Candidate: branch `fix/home-wifi-reconnect-mid-session` on the fork, SHA **`9676eee4`** (`9676eee4e2ef7bdda90b995a87782c6db2f6479c`), two commits on `main` `93c065dd`:
  - `f9de3cb5` WiFi Direct: re-assert a station stand-down the platform undoes
  - `9676eee4` AapService: hand station rejoins to the stand-down
  ```bash
  git fetch fork fix/home-wifi-reconnect-mid-session
  git checkout 9676eee4
  ```
- First round of this thread. Nothing the rig has seen was rewritten.
- JVM suite on that SHA: 2467 tests, 0 failures. Run `run_unit_tests.sh` before installing; a failure there stops the round.
- **No baseline APK this round.** Every comparison is inside the candidate (a settings change, or the same session before and after an event).
- Identity: `send ACTION_QUERY_STATE` must reply with a `commit` beginning `9676eee4`. If the md5 cannot settle which APK is live, the symbol the candidate introduces is `StationStandDownReassertPolicy`:
  ```bash
  unzip -p <pulled apk> 'classes*.dex' | strings | grep -F StationStandDownReassertPolicy
  ```

## 2. What this is and why it exists

A field report: a MediaTek head unit on API 28, Native AA over WiFi Direct, `stand-down-station-mode` ALWAYS, home network on 2452 MHz.

- The app leaves the home network when the group comes up, as designed.
- The unit then rejoins that network 7.5 s later, about 1 s after the phone's session opens.
- It stays joined until the session ends.
- The reporter hears audio stutter while both are up.

The reporter's capture shows:

- **Nothing in the app rejoined it.** There is one restore line, at session end. No second bring-up, no `MATCH! Starting AapService`.
- **Nothing in the app noticed.** `StationStandDown` checked the station once, 1.5 s after the stand-down, and never again.

So ALWAYS was a one-shot: any platform rejoin during a stand-down stood for the rest of the session.

The candidate makes the stand-down hold for its whole window, from `standDown` to `restore`:

- Every WiFi join the service hears is handed to `StationStandDown.onStationJoined`. API 21 and up: `NetworkCallback.onAvailable`. Below 21: the `NETWORK_STATE_CHANGED` receiver.
- If a stand-down is in force, and the station was once read gone, and it is associated again, the app re-issues `disableNetwork(id)` + `disconnect()`. That is a **re-assertion**.
- At most 3 re-assertions per 5-minute window, at least 10 s apart. The window opens on the first re-assertion. A rejoin inside the 10 s is checked again when the 10 s are up.
- A rejoin after the third gets one "budget spent" line, and the station is left joined until that window ends. Then it is checked again: if it is still joined, it is stood down with a fresh budget (`re-assertion 1/3` again).
- The budget refills when the session reaches SSL, so rejoins during bring-up cannot spend it. The 10 s spacing carries across that refill.
- A join event read before the station finishes associating is read once more 1.5 s later. That second read prints nothing unless it re-asserts.
- Each rejoin line carries the network's `WifiConfiguration.status` (`config=`) and how long the session's WiFi lock had been held. These are instruments for which platform behaviour rejoined it:
  - **H1**, the ROM re-enables a disabled network;
  - **H2**, the session's HIGH_PERF WiFi lock provokes the rejoin;
  - **H3**, `disableNetwork` reported true but never stuck.

## 3. What is different about this round

- **Units and staging.** D-POCO (`4f4027e9`) is the phone throughout. D-MOTO is not used: leave it unplugged or with Bluetooth off.
  - **Block A: D-SAM (`30041c35642d2200`, API 19) is the head unit.** This is the reporter's class: pre-Q, so `disableNetwork` is honoured, a 2.4 GHz-only radio and a 2.4 GHz station. The `NETWORK_STATE_CHANGED` receiver is the event source. Turn D-HU's Bluetooth off for the whole block: `adb -s 27870808938846 shell svc bluetooth disable`.
  - **Block B: D-HU (`27870808938846`, Android 14, API 34) is the head unit.** The `NetworkCallback` source, with the overlay permission. Before Block B:
    1. `send ACTION_EXIT` on D-SAM, then `adb -s 30041c35642d2200 shell am force-stop com.andrerinas.headunitrevived`.
    2. Turn D-SAM's Bluetooth off: `adb -s 30041c35642d2200 shell svc bluetooth disable`. Confirm with `adb -s 30041c35642d2200 shell dumpsys bluetooth_manager | grep -m2 -aiE "enabled|state"`.
    3. If it will not go off, power D-SAM down by hand (hand step: no adb lever on that unit) and say so in Setup notes.
    4. Turn D-HU's Bluetooth back on: `adb -s 27870808938846 shell svc bluetooth enable`.

    D-POCO caches the last head unit's Bluetooth MAC (`rig-quirks/units/D-POCO.md`). If Block B's first session has no `Connection accepted from` within 120 s, read `GH.WIRELESS.BT: Creating rfcomm socket for device:` in D-POCO's logcat. If that MAC is not D-HU's, escalate: the only known fix is `pm clear com.google.android.projection.gearhead`, and that needs the operator's approval.
- **Spontaneous rejoins may never happen on this rig, and that is a result.** The reporter's ROM is ColorOS-derived and none of ours is.
  - D-HU's `WifiScanner` is broken (`rig-quirks/topics/wifi.md`), so its auto-join has no scan results to act on. Expect no spontaneous rejoin there.
  - R1 measures whether either unit rejoins on its own. An R1 with zero spontaneous rejoins is a PASS on "nothing misfired" and answers "the rig does not reproduce it". It is not a FAIL.
  - **R2 is the fix's positive control**, with the rejoin forced.
- **D-SAM's forced rejoin is a hand step**, and the reason is that no lever exists: D-SAM is unrooted, has no `cmd wifi`, and `svc wifi` fails on it (`rig-quirks/units/D-SAM.md`). The operator rejoins the home network from Android's own WiFi settings screen. That screen covers the projection, so the executor raises the projection again by verb afterwards.
  - Video may restart after the raise. This is the surface coming back, not the re-assertion, so D-SAM's R2 grades only that the session survives, and reports the video.
- **D-HU's forced rejoin is scripted**, through a pushed script running `cmd wifi connect-network`. A spaced SSID does not survive adb's argv join (`rig-quirks/topics/wifi.md`).
  - It needs the home network's passphrase. If the rig does not have it on record, ask the operator once before Block B starts.
  - If no passphrase can be had, Block B's R2 and R3 are UNTESTABLE. R1 still runs.
- **What each unit prints differently, so neither difference is read as a fault:**

  | | D-SAM (API 19) | D-HU (API 34) |
  |---|---|---|
  | `disableNetwork returned` | `true` | `false`, as in `bring-up-status-pill-and-poke-readiness` round 8 C4. So on D-HU a re-assertion is a bare `disconnect()` |
  | `config=` | `disabled`, `enabled` or `current` | always `unreadable`: `getConfiguredNetworks` is empty for an ordinary app from Q |
  | restore line | `is enabled again` | may be `the platform refused to re-enable`, as in round 8 C1. The station is then rejoined by `reconnect()` or not at all, and that is `main`'s behaviour, not this change |
  | `NetworkMonitor: Network available` | never prints: the receiver path logs nothing of its own at INFO | prints on every join |
- **`config=` on a rejoin line usually reads `current`** on a pre-Q unit, because the network is associated at that moment. The H1/H3 discriminator is the **stand-down's own read-back line**, `this unit has left its WiFi network (config=...)`:
  - `disabled` there means `disableNetwork` stuck, so H3 is refuted for that unit;
  - `enabled` there supports H3.

  The `config=` on each `left its WiFi network again` line is the same reading after a re-assertion.
- **D-SAM settles with two `createGroup SUCCESS` and two `Handling handshake for` before SSL** (`rig-quirks/units/D-SAM.md`). The second-group discard rule does not apply before SSL on D-SAM. After SSL it does.
- **D-SAM preparation:**
  - charge it from a separate supply (data-only USB port);
  - compare `adb -s 30041c35642d2200 shell date` against the host's `date` and put the offset in Setup notes;
  - write its settings with `set_prefs_runas_host.py` (`rig-quirks/units/D-SAM.md`).
- **D-HU preparation:**
  - `stat` its `shared_prefs/` and report the owner even when correct. This round grades `station-stand-down-network-id`, which the app writes. If it is `root:root`, `chown` it to the app's uid:gid first (`rig-quirks/units/D-HU.md`).
  - Confirm the overlay permission with `adb -s 27870808938846 shell appops get com.andrerinas.headunitrevived SYSTEM_ALERT_WINDOW`, which must read `allow`. Without it there is no stand-down on API 34 (round 8 C5).
- **Verify each head unit's station before its block; never assume it.**
  ```bash
  adb -s $HU shell dumpsys wifi | grep -m3 -aiE "mWifiInfo|Frequency"
  ```
  Record SSID and frequency. ALWAYS stands down on any band, so a 5 GHz station on D-HU is fine. A unit joined to nothing has no stand-down to test: that block is UNTESTABLE.
- **Log level INFO** (`log-level=2`). Every app line below prints at INFO. D-HU floods logcat, so run `adb -s $HU logcat -G 16M` before each block.
- **One adb call at a time against a unit** (template rule 8). The station is sampled from the same foreground loop that waits, never from a background poller. The logcat capture is the only stream that runs alongside.
- **Music.** Once projection is up, run `adb -s $HU shell input keyevent KEYCODE_MEDIA_PLAY`, with whatever player on D-POCO the rig used in earlier audio rounds. If no `ms: underruns=` line appears within 30 s, send it once more.

## 4. Settings

All with the app stopped, backed up first (§1). Read every key back before launching.

| Run | Key | Element |
|---|---|---|
| all | `log-level` | `<int name="log-level" value="2" />` |
| all | `wifi-connection-mode` | `<int name="wifi-connection-mode" value="3" />` |
| all | `native-ap-transport` | `<int name="native-ap-transport" value="0" />` |
| R1, R2, R3 | `stand-down-station-mode` | `<int name="stand-down-station-mode" value="1" />` (ALWAYS) |
| R4 | `stand-down-station-mode` | `<int name="stand-down-station-mode" value="2" />` (NEVER) |
| D-HU only | `wifi-direct-band` | `<int name="wifi-direct-band" value="0" />` |

Read and record, and change only as stated:

- `station-stand-down-network-id`: must be absent or `-1` before every launch.
  - If it holds an id, the previous run's restore never ran. The service will restore at start and print an `enabled again` or `refused to re-enable` line **before** this run's stand-down.
  - Record it, and grade this run's R5 only after this run's `asked this unit to leave` line.
- `video-profile-starvation-cap`: if `true`, delete the key (only the delete half of §1) and record that you did. Three fumbled bring-ups set it, and it forces AAC on, which would change R6's audio arm.
- `use-aac-audio`, `audio-latency-multiplier`, `audio-queue-capacity`: **record only, never change.** The rig's thin audio cushion is a deliberate worst case.
- `onboarding-version`: must be `2` or more, or the wizard intercepts the launch.
- If `native-ap-transport` was `1` when found, also clear `hotspot-ssid`, `hotspot-password`, `static-bssid` and `hotspot-interface` (`""`, `""`, `"0"`, `""`) and read them back (`rig-quirks/topics/wifi.md`).

## 5. Helpers and the session procedure

Define these once per block. Set `HU` to the block's head unit.

```bash
HU=30041c35642d2200          # Block A, D-SAM. Block B: HU=27870808938846 (D-HU)
PH=4f4027e9                  # D-POCO
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
send() { a=$1; shift; adb -s $HU shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; }
mark() { send ACTION_LOG_MARKER --es text "$1"; }
# One station sample into the run's poll file: host time, then the supplicant state.
sta() { echo "$(date +%H:%M:%S) $(adb -s $HU shell dumpsys wifi | grep -m1 -a 'mWifiInfo' | grep -ao 'Supplicant state: [A-Z_]*')" >> $RUN.poll.txt; }
# Hold for N seconds, sampling the station every 10 s.
hold() { for i in $(seq $(( $1 / 10 ))); do sta; sleep 9; done; }
# Wait up to N s for a literal string in the capture after line L. Samples the station every ~10 s.
# Exit 0 = seen, 1 = timed out.
waitfor() { t=$1; s=$2; from=$3; i=0; while [ $i -lt $t ]; do tail -n +$from $RUN.txt | grep -aqF "$s" && return 0; [ $((i % 10)) -eq 0 ] && sta; sleep 1; i=$((i+1)); done; return 1; }
at() { wc -l < $RUN.txt; }   # the capture's current line count, used as a "from here" anchor
```

- D-SAM's `am` rejects `-p`, and `send` does not use it.
- If `sta` writes an empty state on a unit, record `adb -s $HU shell dumpsys wifi | head -60` once in Setup notes. The station samples for that unit are then unavailable, and the conditions that use them are reported as such.

**Session procedure `S(<RUN>, <mode>, <seconds>)`**, used by every run:

1. Phone Bluetooth off: `adb -s $PH shell svc bluetooth disable`. Confirm with `adb -s $PH shell dumpsys bluetooth_manager | grep -m1 -a "state:"`.
2. Head unit: `adb -s $HU shell am force-stop $PKG`, write the run's settings (§4) and read them back.
3. Start the capture, clearing the buffer in the same command:
   ```bash
   adb -s $HU logcat -c && (stdbuf -oL adb -s $HU logcat -v time > $RUN.txt &)
   ```
4. Launch the app: `adb -s $HU shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity`. Then `sleep 20`.
5. `mark <RUN>-armed`, then phone Bluetooth on: `adb -s $PH shell svc bluetooth enable`.
6. `waitfor 120 "SSL handshake complete" 1`.
   - If it times out, run `send ACTION_EXIT` and go back to step 1, **once**.
   - A second timeout makes the run INCONCLUSIVE (no session). Say so and move to the next run.
7. `sleep 20`, start music, `mark <RUN>-session`.
8. Run the run's own steps (§7). Then `hold <seconds>`.
9. End, which is R5's sample:
   1. `mark <RUN>-end`, then `send ACTION_DISCONNECT`;
   2. `hold 60`;
   3. `mark <RUN>-after`, then `send ACTION_EXIT`, `sleep 3`, `adb -s $HU shell am force-stop $PKG`.
10. Stop the capture (`pkill -f "logcat -v time"`). Check the last timestamp against the wall clock.

Discard and re-run once (§4 of the template) on any of these:

- a `MATCH! Starting AapService` line with group churn attached;
- more than one `createGroup SUCCESS` **after** the SSL line (on D-SAM two before SSL are normal);
- a second `SSL handshake complete` before `<RUN>-end` that no run step provoked.

Report the counts either way.

## 6. The lines that decide it

Verbatim from the source on `9676eee4`. All print at INFO or WARN. `<..>` marks a value.

```
StationStandDown: asked this unit to leave its WiFi network so the group can have the radio to itself (mode=<M>, station on <F>MHz, 5GHz=<b>, group asking for <G>, disableNetwork returned <b>). It is rejoined when the wireless stack stops.
StationStandDown: this unit has left its WiFi network (config=<status>).
StationStandDown: this unit is still joined to its WiFi network 1500ms later (config=<status>), so the group will have to share that network's channel.
StationStandDown: the platform rejoined this unit's WiFi network <N>s into the stand-down (config=<status>, WifiLock held for <ms>ms | WifiLock not held); leaving it again (re-assertion <k>/3, disableNetwork returned <b>).
StationStandDown: this unit has left its WiFi network again (config=<status>).
StationStandDown: this unit is still joined to its WiFi network 1500ms later after a re-assertion (config=<status>).
StationStandDown: the platform rejoined this unit's WiFi network <ms>ms after the last re-assertion; checking again in <ms>ms.
StationStandDown: the platform has rejoined this unit's WiFi network after 3 re-assertions, so it is left joined for <N>s and then stood down again (config=<status>).
StationStandDown: could not re-assert the stand-down: <msg>
StationStandDown: the setting keeps this unit joined to its own WiFi network, so the group shares the radio with it.
StationStandDown: this unit's WiFi network is enabled again and should rejoin in a few seconds.
StationStandDown: the platform refused to re-enable this unit's WiFi network. It may have to be reconnected by hand.
AapService: Native AA user exit. Stopping active launcher.
NetworkMonitor: Network available: <id>            (D-HU only)
NetworkMonitor: Network lost: <id>                 (D-HU only)
WifiDirectManager: This unit is connected to another WiFi network on <F> MHz while hosting ...
WifiLock acquired (HIGH_PERF)
WirelessServer: Incoming connection detected from /<ip>
SSL handshake complete.                            (AapSslContext, INFO)
Throughput over <ms>ms: rendered=<N> (<fps>fps), ...
audio sink <CHANNEL> over <ms>ms: underruns=<N>, silentCycles=<N>, ... depth=<ms>ms (min <ms>ms), ...
AapTransport: inbound link quiet <N> time(s) in <ms>ms: dead=<ms>ms (<p>%), longest=<ms>ms
StationScanMonitor: station scans: <N> in <ms>ms, ...
WifiDirectManager: SUCCESS - Providing credentials to listener. ...
WifiDirectManager: the group is being removed (the platform took it down), so its credentials are no longer handed out.
AapRead: Connection closed
AutomationReceiver: <action>
AutomationMarker: <label>
```

`inbound link quiet` is composed in `LinkGapMonitor.kt` from `"inbound $subject quiet"` with `SUBJECT_LINK = "link"`, so it will not match a `grep -F` of the source. It is the right string to grep in a capture.

Every grep is `grep -a` on `<RUN>.txt`, inside the marker window the run names. Where a condition compares a station sample from `<RUN>.poll.txt` with a log line, convert with the clock offset recorded in Setup notes.

**The stand-down window** of a session runs from the `this unit has left its WiFi network (config=` line (the read-back, not the `again` one) to the restore line that follows `<RUN>-end`. If the read-back says `still joined` instead, the window has no left-seen point. That is the known residual of this build (a rejoin landing before any read of the station gone is not re-asserted), and the run is not void. Record it and grade the rest from that line.

## 7. Runs

### A0 / B0. Identity

`send ACTION_QUERY_STATE` on the block's head unit. `commit` begins `9676eee4`. Record the md5 of the live APK (template §5).

### R1. Spontaneous rejoin watch (reproduction and the H1/H2/H3 instrument)

**Setup.** ALWAYS. Station joined as verified in §3.

- **D-SAM:** three sessions, `RUN=A-R1a`, `A-R1b`, `A-R1c`, each `S(RUN, ALWAYS, 180)`, no run steps.
- **D-HU:** one session, `RUN=B-R1`, `S(B-R1, ALWAYS, 300)`.

The stop rule is the count: three D-SAM sessions and one D-HU session, whatever they show.

**Measure, per session:**

1. The `asked this unit to leave` line, with its `disableNetwork returned`.
2. The read-back line and its `config=`.
3. Every `the platform rejoined this unit's WiFi network` line between `<RUN>-session` and `<RUN>-end`, with:
   - its `N s into the stand-down`;
   - `config=`;
   - the WifiLock figure;
   - the time to the next `left its WiFi network again` line.
4. Every station sample in the window reading `Supplicant state: COMPLETED`.
5. Every `NetworkMonitor: Network available` line in the window (D-HU).

**PASS**, per session, all of:

1. one `asked this unit to leave` line, with `mode=ALWAYS`;
2. **every** station sample reading `COMPLETED` inside the stand-down window, and outside a spent budget's wait (from a `left joined for` line to the next `re-assertion 1/3` line), has a `the platform rejoined` line within 15 s either side of it. A COMPLETED sample with no such line is the defect surviving the fix, and is a **FAIL**;
3. no `the platform rejoined` line reads `0s into the stand-down` or `1s into the stand-down`. That would be the callback's own replay of the network being torn down, counted as a rejoin;
4. zero `could not re-assert the stand-down` lines.

If the session never stood down, R1 is INCONCLUSIVE for that session. Examples: no `asked this unit to leave`, or a `This unit's Android will only let the app drop` line on D-HU.

**If the change did nothing,** condition 2 still passes whenever the platform never rejoins. So report the spontaneous-rejoin count beside the verdict: `0` means R1 measured only that nothing misfired.

**Reported, not graded (the instrument):**

- the read-back `config=` per session (`disabled` vs `enabled`, H3);
- for each spontaneous rejoin, the `WifiLock held for` figure. A value within about 1500 ms on every rejoin across sessions supports H2.

### R2. Forced rejoin, the fix's positive control (**the point of the round**)

**Setup.** ALWAYS, one session per unit.

**D-SAM**, `RUN=A-R2`. Run `S(A-R2, ALWAYS, 60)` with these run steps, for `j` = 1, then 2:

1. `hold 30`.
2. `F=$(at)`, then `mark A-R2-rejoin-$j`.
3. `adb -s $HU shell am start -a android.settings.WIFI_SETTINGS`.
4. **Hand step:** the operator selects the home network in that list and chooses Connect. It reads Disabled, because the stand-down disabled it.
5. `waitfor 60 "StationStandDown: the platform rejoined this unit's WiFi network" $F`.
   - If it times out, check the samples since the marker. If none reads `COMPLETED`, the join never happened: repeat steps 2 to 5 once with the same `j`. A second miss makes this rejoin INCONCLUSIVE.
   - If a sample did read `COMPLETED` and no line came, carry on: condition 1 below fails.
6. `waitfor 10 "StationStandDown: this unit has left its WiFi network again" $F`.
7. `adb -s $HU shell input keyevent 3` (HOME, to leave the settings screen), `sleep 1`, `send ACTION_RAISE_PROJECTION`, then `mark A-R2-raised-$j`.

Step 1 of the second pass keeps the two re-assertions more than 10 s apart.

**D-HU**, `RUN=B-R2`. First, once, push the rejoin script. Write the SSID exactly as `dumpsys wifi` reported it in §3, and the passphrase from §3:

```bash
printf 'cmd wifi connect-network "%s" wpa2 "%s"\n' "<SSID>" "<PSK>" > rejoin_home.sh
adb -s 27870808938846 push rejoin_home.sh /data/local/tmp/
```

Then `S(B-R2, ALWAYS, 60)` with these run steps, for `j` = 1, then 2:

1. `hold 30`.
2. `F=$(at)`, then `mark B-R2-rejoin-$j`.
3. `adb -s $HU shell sh /data/local/tmp/rejoin_home.sh`. If it is refused with a permission error, use `adb -s $HU shell su 0 sh /data/local/tmp/rejoin_home.sh`, and say so.
4. `waitfor 30 "StationStandDown: the platform rejoined this unit's WiFi network" $F`. A timeout with no `COMPLETED` sample since the marker means the join never happened: repeat steps 2 to 4 once, and a second miss makes this rejoin INCONCLUSIVE.
5. `waitfor 10 "StationStandDown: this unit has left its WiFi network again" $F`.

**PASS**, per unit, all of:

1. **For each `rejoin-<j>` marker:**
   - a `the platform rejoined ... into the stand-down` line follows the marker, reading `re-assertion 1/3` for `j`=1 and `re-assertion 2/3` for `j`=2;
   - on D-HU it lands within 10 s of the first `NetworkMonitor: Network available` line after the marker;
   - on D-SAM, if any sample after the marker reads `COMPLETED`, it lands within 15 s of the first one. A re-assertion can be quicker than the 10 s sampling, so no such sample is not a fault.
2. Within 5 s of each such line, a `this unit has left its WiFi network again` line. Its `config=`:
   - D-SAM: must read `disabled`;
   - D-HU: reads `unreadable`.

   A `still joined ... after a re-assertion` line instead is a FAIL.
3. The station samples between each `left ... again` line and the next marker all read something other than `COMPLETED`.
4. **The session survives**, counted from `<RUN>-session` to `<RUN>-end`:
   - zero `WirelessServer: Incoming connection detected`;
   - zero `SSL handshake complete`;
   - zero `AapRead: Connection closed`;
   - zero `(the platform took it down)`.
5. D-HU only: at least one `Throughput over` line with `rendered=` above 0 inside the 30 s after each `left ... again` line.

**Report, not graded:**

- per re-assertion: rejoin-line-to-left-again-line in ms;
- per re-assertion: `AapTransport: inbound link quiet` lines within 10 s after it, with `longest=`;
- per re-assertion: `SUCCESS - Providing credentials to listener` lines within 10 s after it;
- on D-SAM, the time from `A-R2-raised-<j>` to the first `Throughput over` line with `rendered=` above 0. Video restarting after the settings screen is the surface, not this change.

### R3. Budget, spacing and refill (D-HU only)

D-SAM's hand rejoin cannot be timed inside a 10 s window, so this runs on D-HU's scripted lever only. The run takes about 7 minutes.

**Setup.** ALWAYS, `RUN=B-R3`. Run `S(B-R3, ALWAYS, 30)` with these run steps:

1. `hold 30`, `F=$(at)`, `mark B-R3-j1`, then `adb -s $HU shell sh /data/local/tmp/rejoin_home.sh`.
2. `waitfor 30 "(re-assertion 1/3" $F`. As soon as it is seen: `sleep 3`, `F=$(at)`, `mark B-R3-j2`, then run the script. This one lands inside the 10 s spacing.
3. `waitfor 30 "(re-assertion 2/3" $F`. Then `sleep 12`, `F=$(at)`, `mark B-R3-j3`, then run the script.
4. `waitfor 30 "(re-assertion 3/3" $F`. Then `sleep 12`, `F=$(at)`, `mark B-R3-j4`, then run the script.
5. `waitfor 30 "and then stood down again" $F`. Then `F=$(at)`, `mark B-R3-wait`.
6. `waitfor 330 "(re-assertion 1/3" $F`. Then `mark B-R3-refilled`.
7. `waitfor 10 "StationStandDown: this unit has left its WiFi network again" $F`.

If a step's `waitfor` times out, carry on with the next step anyway. The counts below decide the run.

**PASS**, all of, counted from `B-R3-session` to `B-R3-end`:

1. Exactly one `ms after the last re-assertion; checking again in` line, between `B-R3-j2` and `B-R3-j3`.
2. Between `B-R3-j1` and `B-R3-wait`: exactly three `leaving it again (re-assertion` lines, reading `1/3`, `2/3`, `3/3` in order. The `2/3` line lands at least 10 000 ms after the `1/3` line by their timestamps. That is the spacing, answered by the deferred check rather than by a new event.
3. Exactly one `after 3 re-assertions, so it is left joined for` line, after `B-R3-j4`. Its `<N>s` plus its own timestamp falls within 2 s of 300 s after the `1/3` line's timestamp.
4. Zero `the platform rejoined` lines between that line and the next `re-assertion` line.
5. Every station sample from that line to the next `re-assertion` line reads `COMPLETED`: left joined while the budget is spent, as designed.
6. A second `leaving it again (re-assertion 1/3` line lands between 300 s and 302 s after the first `1/3` line, followed within 5 s by `this unit has left its WiFi network again`. That is the refill.
7. The session survives, as in R2 condition 4.

### R4. Negative control, NEVER (D-SAM)

**Setup.** `RUN=A-R4`, `S(A-R4, NEVER, 180)`, music playing, no run steps.

**PASS**, all of, counted over the whole capture:

1. zero `asked this unit to leave` lines;
2. one or more `the setting keeps this unit joined to its own WiFi network` lines;
3. zero `the platform rejoined` lines;
4. every station sample from `A-R4-session` to `A-R4-end` reads `COMPLETED`;
5. zero restore lines (`enabled again` or `refused to re-enable`) after `A-R4-end`.

This run is also R6's "joined" arm.

### R5. A restore is never undone (graded on every ALWAYS session above)

Each of `A-R1a`, `A-R1b`, `A-R1c`, `A-R2`, `B-R1`, `B-R2` and `B-R3` ends with `ACTION_DISCONNECT` while the service is still alive, so its network callback is still registered.

**PASS**, per session, counted between `<RUN>-end` and `<RUN>-after`:

1. one `AapService: Native AA user exit. Stopping active launcher.` line;
2. exactly one restore line, `is enabled again` or `the platform refused to re-enable`;
3. zero `the platform rejoined` lines after the restore line;
4. **D-SAM:** a station sample reading `COMPLETED` within 15 s of the restore line, and every sample from then to `<RUN>-after` reading `COMPLETED`;
5. **D-HU:** report the first `COMPLETED` sample's delay after the restore line. Not graded when the restore line is `refused to re-enable`; graded as for D-SAM when it is `enabled again`.

R5's verdict is PASS only if every session passes. Name any session that does not.

### R6. Does the rejoin cause the stutter? (measured, not graded)

D-SAM only, comparing the ALWAYS sessions (`A-R1a` to `A-R1c`, station held down) with `A-R4` (NEVER, station joined throughout). All four had music playing. Per session, between `<RUN>-session` and `<RUN>-end`, report:

- minutes of session;
- for each `audio sink <CHANNEL>` channel: the summed `underruns=`, summed `silentCycles=`, and the lowest `min` depth;
- the count of `AapTransport: inbound link quiet` lines, with the largest `longest=`;
- the median `fps` over the `Throughput over` lines;
- the count of `StationScanMonitor: station scans:` lines.

A useful comparison needs audio sink lines in every one of the four sessions. If any lacks them, music never played there: say so, and R6 is INCONCLUSIVE.

## 8. Do not re-run

- **The stand-down mode itself on D-HU.** `bring-up-status-pill-and-poke-readiness-round8-results.md` C1 to C6 settled what each of AUTO, ALWAYS and NEVER does at bring-up, including the overlay gate. This round only adds what happens after the stand-down.
- **An unjoined station's scans on D-HU over WiFi Direct.** `link-stall-periodic-scan-round5-results.md` measured them harmless on UNISOC.

## 9. Report back

1. **R2 per unit:** for each forced rejoin, the ms from rejoin line to `left ... again` line, the `config=` it printed, and whether the session survived (the four zero counts).
2. **R1 per unit:**
   - the number of spontaneous rejoins in the stand-down windows, and the number of `COMPLETED` samples with no rejoin line (must be 0);
   - the read-back `config=` (D-SAM);
   - the `WifiLock held for` figure on every spontaneous rejoin.
3. **R6:** underruns per minute and `inbound link quiet` count, ALWAYS sessions against NEVER, on D-SAM.

Captures: `home-wifi-reconnect-mid-session-round1-captures.zip`, on release `rig-evidence-home-wifi-reconnect-mid-session`, created on this first round. Include every `<RUN>.txt` and `<RUN>.poll.txt`.

