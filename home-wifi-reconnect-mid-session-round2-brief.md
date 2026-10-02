# home-wifi-reconnect-mid-session, round 2 brief

Published name on the transfer branch: `home-wifi-reconnect-mid-session-round2-brief.md`.

## 1. Build and baseline

- Candidate: branch `fix/home-wifi-reconnect-mid-session` on the fork, SHA **`9676eee4`** (`9676eee4e2ef7bdda90b995a87782c6db2f6479c`). **The same build as round 1.** Nothing was added or rewritten: round 1's three FAILs were read against the captures and none is a defect in the candidate (section 2). This round re-measures D-SAM with corrected grading.
  ```bash
  git fetch fork fix/home-wifi-reconnect-mid-session
  git checkout 9676eee4
  ```
- Identity on D-SAM: `send ACTION_QUERY_STATE` must reply with a `commit` beginning `9676eee4`, and the live APK's md5 should read `afc30e6d1b94d3284dad8a8fd8ea0db0`, as in round 1.
  - If both match, do not rebuild or reinstall.
  - If either differs, build from `9676eee4` with `build_hur.sh`, run `run_unit_tests.sh` (2467 tests, 0 failures; a failure stops the round), install with `adb install -r`, and check again. The dex symbol is `StationStandDownReassertPolicy`.
- **No baseline APK.** Every comparison is inside the candidate.

## 2. What this is and why it exists

A field report: a MediaTek head unit on API 28, Native AA over WiFi Direct, `stand-down-station-mode` ALWAYS. The app leaves the home network when the group comes up, as designed. The platform then rejoins that network about 1 s after the phone's session opens, and it stays joined until the session ends. The reporter hears audio stutter while both are up. Nothing in the app rejoined it and nothing noticed: the stand-down checked the station once, 1.5 s after leaving, and never again.

The candidate holds the stand-down for its whole window. Every WiFi join the service hears goes to `StationStandDown.onStationJoined`. If a stand-down is in force, the station was once read gone, and it is associated again, the app re-issues `disableNetwork(id)` + `disconnect()` (a **re-assertion**). At most 3 per 5-minute window, at least 10 s apart; a join inside the 10 s is checked again when they are up (`checking again in`); the budget refills at SSL; after the third, one "budget spent" line and the station is left joined until the window ends.

**What round 1 measured** (`home-wifi-reconnect-mid-session-round1-results.md`):

- D-HU passed everything: the forced rejoin (B-R2), the budget, spacing and refill (B-R3), and the spontaneous watch (B-R1). It is not re-run.
- D-SAM's forced rejoin (A-R2) FAILed on three conditions, and on reading the capture none of them is the candidate:
  1. *Rejoin 2 read `3/3`, not `2/3`.* The operator's one Connect produced **two** platform joins. KitKat associated again 4.2 s after the first re-assertion, with no app command in between (`Trying to associate` at 11:56:16.236), on a network that had read `config=disabled` 2.7 s earlier. The candidate counted two joins as two. The brief's one-join-per-Connect premise was wrong.
  2. *A COMPLETED poll sample after a left-again line.* That was the second platform join, answered by `checking again in 4245ms` and then `re-assertion 2/3`. The poll sample was a host-clock read every ~10 s, compared with device-clock lines.
  3. *The session was lost on rejoin 2.* The platform removed the P2P group (`P2P-GROUP-REMOVED` 11:57:00.870) 1.8 s **before** the station's `Trying to associate` (11:57:02.701), and before any join event could reach the app. The group sat on 2412 MHz and the station joined 2462 MHz, on one radio.
- R5 FAILed on two D-SAM sessions, neither the candidate: A-R1b's process was killed 20 ms after the user exit by a `Fatal signal 11 (SIGSEGV) ... (MediaCodec_loop)` in the Marvell decoder teardown (the same crash hit A-R4 under NEVER, where no stand-down ran), and the restarted process restored the network first thing, as built. A-R1c's "20.5 s" mixed a device-clock restore line with a host-clock poll sample; on the device clock the restore to `CTRL-EVENT-CONNECTED` was 4.9 s.

**The limit this round has to respect.** On a single-radio unit whose station and group sit on different channels, the platform's association can take the group down before any join event reaches the app. No reaction after the join can protect that, and no public API below Q stops the association. So on D-SAM this round grades **the re-assertion mechanism** (every station join answered, every re-assertion leaving, the budget and spacing obeyed, and our own disconnect never taking the group), and **reports** session survival rather than grading it.

## 3. What is different about this round

- **D-SAM only** (`30041c35642d2200`, API 19) as head unit; D-POCO (`4f4027e9`) as phone. D-HU and D-MOTO are not used.
  - On D-HU: `adb -s 27870808938846 shell am force-stop com.andrerinas.headunitrevived`, then `adb -s 27870808938846 shell svc bluetooth disable`. Its Bluetooth self-reverts to on within seconds (round 1 Block A ran with that and was not affected); with the app stopped it opens no Android Auto listener. D-MOTO unplugged or Bluetooth off.
  - D-SAM's Bluetooth must be **on**. Round 1 turned it off by hand before Block B (`svc` has no `bluetooth` on API 19), so it is a **hand step** to turn it back on from Android's own Settings, and the reason is that no adb lever exists. Confirm with `adb -s 30041c35642d2200 shell dumpsys bluetooth_manager | grep -m2 -aiE "enabled|state"`.
- **D-POCO caches the last head unit's Bluetooth MAC** (`rig-quirks/units/D-POCO.md`), and round 1 ended on D-HU. If A-R5's session has no `Connection accepted from` within 120 s of the phone's Bluetooth coming on, read `GH.WIRELESS.BT: Creating rfcomm socket for device:` in `adb -s 4f4027e9 logcat -d`. If that MAC is not D-SAM's (`adb -s 30041c35642d2200 shell settings get secure bluetooth_address`), escalate: the only known fix is `pm clear com.google.android.projection.gearhead`, which needs the operator's approval.
- **Grading moves to the device clock and to the platform's own WiFi lines.** D-SAM's logcat carries `wpa_supplicant` at INFO, and every graded timing below compares two lines from the same capture. The host-clock station samples (`sta`) are still taken, and are reported only.
  - **A station join** is a line `CTRL-EVENT-CONNECTED - Connection to <B> completed` where `<B>` is a station BSSID. The group's own start prints the same line with the group's address, so `<B>` must be one of the addresses that appear in a `Trying to associate with <B>` line of the same capture (the group never prints that line). Round 1 saw two station BSSIDs on the home network: `F4.8D.4F` (2462 MHz) and `F4.8D.4E` (5500 MHz).
  - **Our disconnect** is `CTRL-EVENT-DISCONNECTED bssid=<B> reason=3 locally_generated=1`, landing about 20 ms before the app's re-assertion line.
  - **The platform taking the group** is `P2P-GROUP-REMOVED`, followed about 0.6 s to 2.7 s later by the app's `the group is being removed (the platform took it down)`.
- **One Connect can produce two re-assertions**, and a second Connect inside the budget can reach `3/3` and the budget-spent line. That is the design: the counter is per platform join, not per operator action.
- **Session loss on a forced rejoin is expected on this unit** (section 2) and is reported, not graded. A lost session that re-forms is fine: the stand-down stays in force, the budget refills at the new SSL, and the run carries on.
- **D-SAM's forced rejoin is a hand step**, for the reason round 1 gave: no lever exists (unrooted, no `cmd wifi`, `svc wifi` fails, `rig-quirks/units/D-SAM.md`). The operator selects the home network in Android's WiFi settings and chooses Connect. That screen covers the projection, so the executor sends HOME and raises the projection by verb afterwards.
- **`logcat -c` is refused on D-SAM** and older buffer lines open every capture. Each run records the capture's line count just before launch (`L0`) and every grep runs on the window from there to the run's `-after` marker.
- **`connection-modes` must hold `wifi`** (section 4). Round 1 found `{usb}` on D-SAM, which logs `WiFi is not one of the chosen connection modes. Not arming it` at launch.
- **D-SAM settles with two `createGroup SUCCESS` and two `Handling handshake for` before SSL**, and in round 1 up to four groups before SSL (A-R1c). Those are not contamination before SSL.
- **The Marvell codec teardown crash may recur** at any session end. R5 has a branch for it (condition b). It is reported, not attributed to the candidate.
- **Preparation:** charge D-SAM from a separate supply (data-only USB port); compare `adb -s 30041c35642d2200 shell date` against the host's `date` and put both in Setup notes; check D-POCO's focus is the launcher (`adb -s 4f4027e9 shell dumpsys window | grep mCurrentFocus`, fix with `adb -s 4f4027e9 shell input keyevent KEYCODE_HOME`).
- **Before every session, the station must be joined:** `adb -s 30041c35642d2200 shell dumpsys wifi | grep -m1 -a mWifiInfo` must show `Supplicant state: COMPLETED`. If not, sample it every 10 s for 90 s. Still not joined: **hand step**, the operator joins the home network from Android's WiFi settings, then `adb -s 30041c35642d2200 shell input keyevent 3`. Record which happened. A unit joined to nothing has nothing to stand down.
- **Log level INFO** (`log-level=2`). Every app line below prints at INFO or WARN; the `wpa_supplicant`, `libc` and `ActivityManager` lines are system lines and print regardless.
- **One adb call at a time against a unit** (house rule 8). The logcat capture is the only stream that runs alongside.
- **Music.** Once projection is up: `adb -s 30041c35642d2200 shell input keyevent KEYCODE_MEDIA_PLAY`, with the player on D-POCO the rig used in round 1. If no `ms: underruns=` line appears within 40 s, send it once more.

## 4. Settings

Write with `set_prefs_runas_host.py` (D-SAM has no `sed`; `rig-quirks/units/D-SAM.md`), the app stopped, backed up first (§1). Read every key back before launching.

| Run | Key | Element |
|---|---|---|
| all | `log-level` | `<int name="log-level" value="2" />` |
| all | `wifi-connection-mode` | `<int name="wifi-connection-mode" value="3" />` |
| all | `native-ap-transport` | `<int name="native-ap-transport" value="0" />` |
| all | `connection-modes` | `<set name="connection-modes"><string>usb</string><string>wifi</string></set>` (a `<set>`: rebuild the file on the host, never a line-scoped delete) |
| A-R5, A-R2a, A-R2b | `stand-down-station-mode` | `<int name="stand-down-station-mode" value="1" />` (ALWAYS) |
| A-R4b | `stand-down-station-mode` | `<int name="stand-down-station-mode" value="2" />` (NEVER) |

Read and record, change only as stated:

- `station-stand-down-network-id`: must be absent or `-1` before every launch. If it holds an id, the previous run's restore never ran: record it. The service restores at start and prints `enabled again` or `refused to re-enable` **before** this run's stand-down; grade this run's R5 only on lines after `<RUN>-end`.
- `video-profile-starvation-cap`: if present, delete it (only the delete half of §1) and record that you did.
- `use-aac-audio`, `audio-latency-multiplier`, `audio-queue-capacity`: **record only, never change.** The rig's thin audio cushion is deliberate. Round 1 read them on D-SAM as `false`, `16`, `50`.
- `onboarding-version`: must be `2` or more.
- `allow-external-configuration`: record only. `ACTION_LOG_MARKER` is not gated on this candidate.

## 5. Helpers and the session procedure

```bash
HU=30041c35642d2200          # D-SAM
PH=4f4027e9                  # D-POCO
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
send() { a=$1; shift; adb -s $HU shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; }
mark() { send ACTION_LOG_MARKER --es text "$1"; }
sta() { echo "$(date +%H:%M:%S) $(adb -s $HU shell dumpsys wifi | grep -m1 -a 'mWifiInfo' | grep -ao 'Supplicant state: [A-Z_]*')" >> $RUN.poll.txt; }
hold() { for i in $(seq $(( $1 / 10 ))); do sta; sleep 9; done; }
# Wait up to N s for a literal string in the capture after line L. Exit 0 = seen, 1 = timed out.
waitfor() { t=$1; s=$2; from=$3; i=0; while [ $i -lt $t ]; do tail -n +$from $RUN.txt | grep -aqF "$s" && return 0; [ $((i % 10)) -eq 0 ] && sta; sleep 1; i=$((i+1)); done; return 1; }
at() { wc -l < $RUN.txt; }
```

`am` on D-SAM rejects `-p`; `send` does not use it. The rig's `home_wifi_reconnect_session.sh` implements the procedure below with round 1's pre-checks; use it if it performs these steps in this order, add the `L0` record if it does not, and say which in Setup notes.

**Session procedure `S(<RUN>, <mode>, <seconds>)`:**

1. Phone Bluetooth off: `adb -s $PH shell svc bluetooth disable`, confirm with `adb -s $PH shell dumpsys bluetooth_manager | grep -m1 -a "state:"`.
2. `adb -s $HU shell am force-stop $PKG`, write the run's settings (section 4), read them back. Check the station is joined (section 3).
3. Start the capture: `(stdbuf -oL adb -s $HU logcat -v time > $RUN.txt &)`, then `sleep 5`.
4. `at > $RUN.L0`, then launch: `adb -s $HU shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity`. `sleep 20`.
5. `mark <RUN>-armed`, then phone Bluetooth on: `adb -s $PH shell svc bluetooth enable`.
6. `waitfor 120 "SSL handshake complete" $(cat $RUN.L0)`. On a timeout: `send ACTION_EXIT`, `sleep 3`, and go back to step 1 **once**, with `RUN` suffixed `-retry`. A second timeout makes the run INCONCLUSIVE (no session).
7. `sleep 20`, start music, `mark <RUN>-session`.
8. The run's own steps (section 7), then `hold <seconds>`.
9. End: `mark <RUN>-end`, `send ACTION_DISCONNECT`, `hold 60`, `mark <RUN>-after`, `send ACTION_EXIT`, `sleep 3`, `adb -s $HU shell am force-stop $PKG`.
10. Stop the capture (`pkill -f "logcat -v time"`); check its last timestamp against D-SAM's `date`.

**Extraction, after every run** (on the host; these files are what the host grades from):

```bash
# The run's window: from the launch to the -after marker.
tail -n +$(cat $RUN.L0) $RUN.txt | sed -n "1,/AutomationMarker: $RUN-after/p" > $RUN.win.txt
# Station BSSIDs: only the station prints this line.
grep -aoE "Trying to associate with [0-9A-Fa-f.:]+" $RUN.win.txt | awk '{print $4}' | sort -u > $RUN.sta-bssid.txt
# The timeline.
grep -aE "Trying to associate with|CTRL-EVENT-CONNECTED|CTRL-EVENT-DISCONNECTED|P2P-GROUP-STARTED|P2P-GROUP-REMOVED|Fatal signal|has died|StationStandDown: |the platform took it down|SSL handshake complete\.|Incoming connection detected|AapRead: |Native AA user exit|AutomationMarker: |inbound link quiet" $RUN.win.txt | cut -c1-260 > $RUN.timeline.txt
# The app's pids, in order of appearance.
grep -aoE "OPENHU *\( *[0-9]+\)" $RUN.win.txt | tr -dc '0-9\n' | uniq > $RUN.pids.txt
```

Return `$RUN.timeline.txt`, `$RUN.sta-bssid.txt` and `$RUN.pids.txt` whole in the run's JSON block (a timeline ran to about 60 lines a session in round 1). A **station join** below is a timeline line containing `CTRL-EVENT-CONNECTED - Connection to <B> completed` with `<B>` listed in `$RUN.sta-bssid.txt`.

Discard and re-run once on: a `MATCH! Starting AapService` line; more than one `createGroup SUCCESS` after the first SSL **when no station join or `P2P-GROUP-REMOVED` precedes it**; a second `SSL handshake complete` before `<RUN>-end` in A-R5 or A-R4b. In A-R2a and A-R2b a second SSL after a forced rejoin is a measurement, not contamination. Report the counts either way.

## 6. The lines that decide it

App lines, verbatim from the source on `9676eee4`, all at INFO or WARN. `<..>` marks a value.

```
StationStandDown: asked this unit to leave its WiFi network so the group can have the radio to itself (mode=<M>, ..., disableNetwork returned <b>). It is rejoined when the wireless stack stops.
StationStandDown: this unit has left its WiFi network (config=<status>).
StationStandDown: this unit has left its WiFi network again (config=<status>).
StationStandDown: this unit is still joined to its WiFi network 1500ms later (config=<status>), so the group will have to share that network's channel.
StationStandDown: this unit is still joined to its WiFi network 1500ms later after a re-assertion (config=<status>).
StationStandDown: the platform rejoined this unit's WiFi network <N>s into the stand-down (config=<status>, WifiLock held for <ms>ms | WifiLock not held); leaving it again (re-assertion <k>/3, disableNetwork returned <b>).
StationStandDown: the platform rejoined this unit's WiFi network <ms>ms after the last re-assertion; checking again in <ms>ms.
StationStandDown: the platform has rejoined this unit's WiFi network after 3 re-assertions, so it is left joined for <N>s and then stood down again (config=<status>).
StationStandDown: could not re-assert the stand-down: <msg>
StationStandDown: the setting keeps this unit joined to its own WiFi network, so the group shares the radio with it.
StationStandDown: this unit's WiFi network is enabled again and should rejoin in a few seconds.
StationStandDown: the platform refused to re-enable this unit's WiFi network. It may have to be reconnected by hand.
AapService: Native AA user exit. Stopping active launcher.
WifiDirectManager: the group is being removed (the platform took it down), so its credentials are no longer handed out.
WirelessServer: Incoming connection detected from /<ip>
SSL handshake complete.                     (AapSslContext, INFO)
AapRead: Connection closed
AapRead: WiFi read timeout (<ms>ms) - connection lost.
Throughput over <ms>ms: rendered=<N> (<fps>fps), ...
audio sink <CHANNEL> over <ms>ms: underruns=<N>, silentCycles=<N>, ... depth=<ms>ms (min <ms>ms), ...
AapTransport: inbound link quiet <N> time(s) in <ms>ms: dead=<ms>ms (<p>%), longest=<ms>ms
StationScanMonitor: station scans: <N> in <ms>ms, ...
WifiLauncher: wireless bring-up requested, but WiFi is not one of the chosen connection modes. Not arming it; the WiFi button still works.
WifiLauncher: Initializing WiFi Mode: <mode>
AutomationReceiver: <action>
AutomationMarker: <label>
```

Composed in source, so the runs grep a piece that exists as one literal and then read the rest of the matched line: the re-assertion's leave line is a `StationStandDown: this unit has left its WiFi network` line whose next words are ` again (config=` (the `again` is a conditional piece); the FAIL line is any line carrying `after a re-assertion` (a conditional piece; the deferral line reads `after the last re-assertion` and does not match it); `inbound link quiet` (`"inbound $subject quiet"` in `LinkGapMonitor.kt`), `station scans: ` (`StationScanCadencePolicy`, printed after `StationScanMonitor: `).

System lines (not in the app's source; quoted from round 1's D-SAM captures, all at INFO or F):

```
I/wpa_supplicant( <pid>): Trying to associate with <B> (SSID='<hex>' freq=<F> MHz)
I/wpa_supplicant( <pid>): CTRL-EVENT-CONNECTED - Connection to <B> completed (auth) [id=<n> id_str=]
I/wpa_supplicant( <pid>): CTRL-EVENT-DISCONNECTED bssid=<B> reason=3 locally_generated=1
I/wpa_supplicant( <pid>): P2P-GROUP-STARTED p2p-wlan0-0 GO  "<name>" freq=<F> passphrase=...
I/wpa_supplicant( <pid>): P2P-GROUP-REMOVED p2p-wlan0-0 GO reason=REQUESTED
F/libc    ( <pid>): Fatal signal 11 (SIGSEGV) at 0xdeadbaad (code=1), thread <tid> (MediaCodec_loop)
I/ActivityManager( <pid>): Process com.andrerinas.headunitrevived (pid <pid>) (adj <n>) has died.
```

Two strings the runs grep never appeared in round 1's D-SAM captures, and that is expected: `locally_generated=0` (a station leaving on its own; only `=1` was seen, 8 times) and `after a re-assertion` (the FAIL line). `GH.WIRELESS.BT: Creating rfcomm socket for device:` is Gearhead's, in D-POCO's logcat (`rig-quirks/units/D-POCO.md`).

Every grep is `grep -a`. Every timing below compares two lines of the same capture by their device timestamps.

**The stand-down window** of a session runs from the first `this unit has left its WiFi network (config=` line (the read-back, without `again`) to the first restore line after `<RUN>-end`. If the read-back says `still joined` instead, nothing is re-asserted until the station is read gone once (the known residual); record it, and start the window at the first station `CTRL-EVENT-DISCONNECTED` after that line.

**A spent-budget wait** runs from a `left joined for` line to the next `leaving it again (re-assertion` line.

## 7. Runs

### A0. Identity

`send ACTION_QUERY_STATE`: `commit` begins `9676eee4`. Record the live APK's md5 (template §5).

### A-R5. Plain ALWAYS session (R5 sample, R6's stood-down arm)

`RUN=A-R5`, `S(A-R5, ALWAYS, 180)`, music playing, no run steps.

Graded only under R5 (below). Report: the number of station joins inside the stand-down window (round 1 saw 0 spontaneous joins on D-SAM in about 9.5 minutes), the read-back's `config=`, the group's `P2P-GROUP-STARTED ... freq=` and the station's `Trying to associate ... freq=` before the stand-down, and R6's numbers.

### A-R2a, A-R2b. Forced rejoin, the re-assertion mechanism (**the point of the round**)

Two sessions, identical. For `RUN=A-R2a` then `RUN=A-R2b`: `S(RUN, ALWAYS, 60)` with these run steps, for `j` = 1, then 2:

1. `hold 30`.
2. `F=$(at)`, then `mark $RUN-rejoin-$j`.
3. `adb -s $HU shell am start -a android.settings.WIFI_SETTINGS`.
4. **Hand step:** the operator selects the home network and chooses Connect. It reads Disabled, because the stand-down disabled it.
5. `waitfor 90 "Trying to associate with" $F`. On a timeout: `adb -s $HU shell input keyevent 3`, then repeat steps 2 to 5 once with the marker `$RUN-rejoin-$j-retry`. A second timeout makes this rejoin INCONCLUSIVE; go to step 7.
6. `sleep 15`. This lets a second platform join, its deferred check and any group loss land before the screen changes.
7. `adb -s $HU shell input keyevent 3`, `sleep 1`, `send ACTION_RAISE_PROJECTION`, `mark $RUN-raised-$j`.
8. If a `the platform took it down` line landed after `$RUN-rejoin-$j`: `waitfor 90 "SSL handshake complete" $F`. On a timeout, skip any remaining rejoin in this session and go straight to the end of `S`; say so.

The stop rule is the count: two sessions, two Connects each, whatever they show.

**PASS, per session.** Every condition is over the timeline, between the start of the stand-down window and `<RUN>-end`:

1. **Reachability.** At least one station join after each `$RUN-rejoin-<j>` marker that was not INCONCLUSIVE. Across the two sessions, at least two station joins in total; fewer makes R2 INCONCLUSIVE, not FAIL.
2. **Every station join is answered.** Within 5000 ms after each station join there is exactly one of: a `leaving it again (re-assertion` line, a `checking again in` line, or a `left joined for` line. Two exemptions, each to be named in the report:
   - a join inside a spent-budget wait;
   - a join followed within 1500 ms by a `CTRL-EVENT-DISCONNECTED bssid=<B>` line that reads `locally_generated=0` (the station left on its own).
   A station join with no answer is the defect surviving the fix, and is a **FAIL**.
3. **Every deferral is resolved.** Each `checking again in <D>ms` line at time T is followed, no later than T + D + 3000 ms, by a `leaving it again (re-assertion` line, a `left joined for` line, or a `CTRL-EVENT-DISCONNECTED bssid=<B>` line for a station BSSID.
4. **Every re-assertion leaves.** For each `leaving it again (re-assertion` line:
   - a `CTRL-EVENT-DISCONNECTED bssid=<B> reason=3 locally_generated=1` line for a station BSSID within 2000 ms either side of it;
   - a `StationStandDown: this unit has left its WiFi network` line within 5000 ms after it whose next words are ` again (config=disabled).`;
   - and zero lines carrying `after a re-assertion` anywhere in the window.
5. **Budget and spacing.** The `re-assertion <k>/3` values run 1, 2, 3 in order, and restart at 1 only after an `SSL handshake complete.` line or at least 300 s after the window's `1/3` line. Each consecutive pair of `leaving it again (re-assertion` lines is at least 9950 ms apart by timestamp, across an SSL as well. At most one `left joined for` line per budget window, and only after a `3/3` line.
6. Zero `could not re-assert the stand-down` lines.
7. **Our disconnect never takes the group.** A `P2P-GROUP-REMOVED` line within 10 s after a `leaving it again (re-assertion` line is a FAIL unless a station `Trying to associate with` line lies within 5 s before or after that removal. That is the platform's own association taking the group, which section 2 explains and this condition does not grade.

**If the change did nothing,** condition 1 still holds (the operator makes the join) and condition 2 fails on every join, since no answer line exists without the candidate. So a PASS here is only reachable through the mechanism.

**Reported, not graded:**

- per station join: the ms from the join to its answer line, and from the join to our `CTRL-EVENT-DISCONNECTED ... locally_generated=1` (how long the station shared the radio); the station's `freq=` from the preceding `Trying to associate` line, and the group's current `P2P-GROUP-STARTED ... freq=`;
- per operator Connect: how many station joins it produced;
- per `P2P-GROUP-REMOVED` in the session: the ms to the nearest station `Trying to associate with` line and to the nearest re-assertion line, either side;
- session survival from `<RUN>-session` to `<RUN>-end`: counts of `Incoming connection detected`, `SSL handshake complete.`, `AapRead: Connection closed`, `AapRead: WiFi read timeout`, `the platform took it down`;
- `AapTransport: inbound link quiet` lines within 10 s after each re-assertion, with `longest=`;
- each `<RUN>-raised-<j>` to the first `Throughput over` line with `rendered=` above 0, in ms;
- the `WifiLock held for` figure on each re-assertion line.

### A-R4b. NEVER, R6's joined arm (D-SAM)

`RUN=A-R4b`, `S(A-R4b, NEVER, 180)`, music playing, no run steps. R4's own conditions passed in round 1 and are not re-graded.

- **Gate:** one `the setting keeps this unit joined to its own WiFi network` line and zero `asked this unit to leave` lines. Otherwise the setting did not take: void, fix the settings, re-run once.
- **Stop rule:** if the session is lost before 150 s after `A-R4b-session` (an `AapRead: Connection closed`, `AapRead: WiFi read timeout` or `Fatal signal` line), run it once more as `A-R4c`. A second loss makes R6 INCONCLUSIVE. Report the loss and any `Fatal signal` line either way.

### R5. A restore is never undone (graded on A-R5, A-R2a, A-R2b)

Per session, over the timeline between `<RUN>-end` and `<RUN>-after`, and `$RUN.pids.txt`:

- **(a) The process lived.** No `Fatal signal` or `has died` line for the app's pid between the user exit line and the restore line. PASS, all of:
  1. one `AapService: Native AA user exit. Stopping active launcher.` line;
  2. exactly one restore line (`is enabled again` or `refused to re-enable`), from the same pid, within 1000 ms after it;
  3. zero `the platform rejoined` lines after the restore line;
  4. a station join within 15 000 ms after the restore line, by device timestamps;
  5. no `CTRL-EVENT-DISCONNECTED bssid=<B> ... locally_generated=1` line for a station BSSID from that join to `<RUN>-after`.
- **(b) The process died** between the user exit and any restore (a `Fatal signal` or `has died` line for the old pid, then a new pid in `$RUN.pids.txt`). PASS when the new pid's first `StationStandDown:` line is a restore line (`is enabled again` or `refused to re-enable`). Report the station's first join after it, not graded: the restarted service re-arms the stack, which is `main`'s behaviour after a crash restart. Quote the `Fatal signal` line.

R5 passes only if every session passes. Name any that does not, with the branch it took.

### R6. Does the rejoin cause the stutter? (measured, not graded)

Compare A-R5 (station stood down) with A-R4b or A-R4c (station joined throughout). Per session, between `<RUN>-session` and `<RUN>-end`:

- minutes of session;
- for each `audio sink <CHANNEL>`: summed `underruns=`, summed `silentCycles=`, lowest `min` depth after the first sample;
- the count of `inbound link quiet` lines, with the largest `longest=`;
- the median `fps` over the `Throughput over` lines;
- the count of `StationScanMonitor: station scans:` lines;
- the station's `freq=` (A-R4b: from the `Trying to associate` line nearest before launch, or from `dumpsys wifi` at step 2) and the group's `P2P-GROUP-STARTED ... freq=`.

R6 needs audio sink lines in both sessions and a NEVER session that was not lost; otherwise INCONCLUSIVE, with the reason.

## 8. Do not re-run

- **Anything on D-HU.** B-R1, B-R2 and B-R3 passed in round 1: forced rejoin answered in 71 and 91 ms after `Network available`, the session intact; spacing 10 029 ms; budget-spent wait 256 s; refill 300.014 s after the first re-assertion.
- **D-SAM's spontaneous watch (A-R1a to A-R1c).** Three sessions, 0 spontaneous joins, read-back `config=disabled` every time (`disableNetwork` sticks on D-SAM). A-R5 reports its own count, which is enough.
- **R4's five conditions under NEVER.** Passed in round 1; A-R4b only gates on them.
- **The stand-down at bring-up on D-HU** (`bring-up-status-pill-and-poke-readiness-round8-results.md` C1 to C6).

## 9. Report back

1. **R2:** across A-R2a and A-R2b, the number of station joins, the number answered within 5000 ms (must equal the joins less the named exemptions), each join-to-answer ms, and each join-to-disconnect ms. Any `P2P-GROUP-REMOVED` within 10 s after a re-assertion with no station association near it.
2. **R5:** per session, which branch, and the restore line to station join in ms on the device clock.
3. **R6:** underruns per minute and `inbound link quiet` count, A-R5 against the NEVER session, with both sessions' station and group frequencies.

Captures: asset `home-wifi-reconnect-mid-session-round2-captures.zip`, added to the existing release `rig-evidence-home-wifi-reconnect-mid-session`. Include every `<RUN>.txt`, `.poll.txt`, `.win.txt`, `.timeline.txt`, `.sta-bssid.txt`, `.pids.txt` and `.L0`.
