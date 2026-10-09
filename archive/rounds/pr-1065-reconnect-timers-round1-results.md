# pr-1065-reconnect-timers - round 1 results

**Candidate C:** `codex/handshake-retry-pacing` head `de1f98488d81` (rebased onto `main`, no merge), tree `26758ff3181d78185f8883f40b593bb2504ec980`, the brief's expected tree   **Baseline B:** `main` @ `145a0c762f0a87386ca63b54518ea5e861b290be`
**APK md5:** B `3100817484e47e9a6418753308e92ed1` / C `c95e8b932c6829ad76cad4277d6f3c7a`
**Units:** D-POCO (API 35) as head unit and USB host in Stage U, D-MOTO (API 34, Gearhead `17.9.664004-release`) as the dongle's phone; D-HU (API 34) as head unit and D-POCO (Gearhead `17.9.664004-release`) as phone in Stage W
**Date:** 2026-10-06
**Evidence:** release `rig-evidence-pr-1065-reconnect-timers`, asset `pr-1065-reconnect-timers-round1-captures.zip`, sha256 `70d4bae6af6223cfecdb57dbd21c2d11fb15ab232598e3a2fcf79bec9e30bdbe`

**Summary (not a graded verdict).** The point of the round, U1, is INCONCLUSIVE: on both arms every cycle in which the wireless dial landed inside the 3 s window was also masked by the dongle re-enumerating itself, so finding 1 could not be tested. The ordinary retries are unchanged on C: H 10 of 10 on both arms with the discovery restart firing in all 10, and N 5 of 5 on both with no timer lines. Four runs below read FAIL, and each one fails only on conditions that cannot hold on this rig: the phone's `Critical error` count (a session end we cause, on both arms alike) and, in U2 on B, one cycle whose recovery waited on a permission dialog. Each section says which.

## Setup notes

- **Brief resolved from a commit that carried five briefs;** the operator chose all five in order and this is the third. Quirk files read: `rig-quirks/topics/tooling.md`, `rig-quirks/topics/gearhead.md`, `rig-quirks/topics/wifi.md` (house network), `rig-quirks/units/D-POCO.md`, `rig-quirks/units/D-HU.md`, `rig-quirks/units/D-MOTO.md`.
- **C identity, the PR head moved.** `096f57da` in the brief no longer exists upstream; the branch now holds `de1f98488d81`, one commit directly on `145a0c76`. Its tree is exactly the brief's `26758ff3…`, so I built it directly and made no merge. The on-device `commit` reads `de1f98488d81`.
- **B reused from the earlier round.** B is the same commit `145a0c76` I built and tested for the restart-audio round (md5 `3100817484e47e9a6418753308e92ed1`, 2738 JVM tests, 0 failures); I reinstalled that APK and did not rebuild.
- **Builds and tests.** C with `build_hur_cool.sh`, JVM tests with `run_unit_tests.sh`: 2741 tests, 0 failures, 0 errors, `AutomaticReconnectTest` present, exactly B's 2738 plus 3. The new worktree needed `local.properties` copied in, as before.
- **Stage U rig:** D-POCO on wireless adb (`192.168.1.8:5555`) with the dongle on its OTG port, host `192.168.1.11` on the same `Pegue Cdesta` network (5745 MHz), D-MOTO on a cable with Bluetooth on, `fake5277.py 0` on the host. D-POCO's head unit server was off in Stage U (checked, `D-POCO SERVES 5277` read false). Both units in Stage W read `Pegue Cdesta`, 5745 MHz, D-HU `192.168.1.4`, D-POCO `192.168.1.8`.
- **Deviation 1, first start of arm B in Stage U voided.** The plug cue printed at 20:59:28 and the dongle was not plugged inside the 90 s wait; I stopped the script and kept `attempt1-u-B/`. The restart (cue 21:02:27, the dongle attached at 21:03:32, 65 s later and inside the 90 s wait) is the arm's data.
- **Deviation 2, `connection-modes` kept as the brief lists it.** The brief's key list has `connection-modes usb,wifi`; I wrote it as given. (An omitted `connection-modes` on D-POCO cost the previous round its discovery, so I checked the read-back each run.)
- **Deviation 3, arm C's U1 run twice.** The first U1-C (kept in `armC-first-U1/`) ended with no graded cycle: its first cycle was void (no USB session at start), `ACTION_CHECK_USB` at 21:33:16.9 left no USB line for 25 s (a discovery dial had put the connection into `connected` 0.95 s earlier), and the replug cue at 21:34:48 produced no detach or attach in the next 90 s, so `u_restore` printed `restored-by=NONE` and the stop rule ended the arm. B's comparable first cycle was restored by `ACTION_CHECK_USB` (21:11:21, `Found device already in accessory mode`). With the operator's agreement I reran U1-C once with `u1_only.sh`; the rerun's first cycle was valid. The cause on C's first run is my inference from timestamps, not proven.
- **Deviation 4, an uncued replug in U2-B.** After the first void cycle I told the operator to expect a replug cue before one existed; the operator unplugged at 21:06:45 and replugged at 21:06:48. The script then credited the restore to `ACTION_CHECK_USB`. The true route was the physical replug; the row `U2-1 restored-by=ACTION_CHECK_USB` for B is mislabelled, and that cycle is void either way. From then on the operator acted only on printed cues.
- **Deviation 5, permission dialog.** After every session end the dongle detaches, re-attaches as `PID 4EE1`, and Android asks for USB permission. The operator tapped allow on that dialog without "Always" up to cycle U1-4 of arm B and ticked "Always" at 21:16:06 (hand step H2, recorded in `hand-steps.log`). The time from the end verb to the first `Found device` line on B fell from 11.4 s (U1-2) to 4.6 to 5.6 s from U1-7 on, which fits the prompt leaving. Arm C ran entirely after the tick.
- **Deviation 6, run H baseline backup once.** The brief re-takes D-HU's settings backup at the start of every H run, which would make the second arm's backup carry the first arm's keys. I took one backup (`settings-backup-DHU.xml`, equal to the earlier round's) in `w_prep.sh` and removed that line from the H block.
- **Deviation 7, run order in Stage W.** To avoid re-installing the arms, I ran B's H, N then C's H, N, then P on C then B, with the arm install done once per group. The D-HU installs were slow on some starts (one took about 150 s with `system_server` at 93% and 1.1 GB of swap in use).
- **Deviation 8, P skips its cycle loop.** When the first session never formed, P goes straight to its end marker; the brief's loop would only have produced void cycles.
- **Void and rerun runs, kept in the asset:** `voided-P-B-nohpkg/` (the P run mapped to the wrong block, then `HPKG` unset), `voided-P-B-devserver-on/` (P-B, sessions formed through D-POCO's own head unit server, `Found Headunit Server on 192.168.1.8:5277`, not the helper), `voided-H-C-wedged-server/` (first H-C: the server was wedged after I killed a P run mid-session; discovery swept 253 addresses and found nothing). In each case I stopped the script, the operator restarted or switched the server as needed, and the run was redone from its start.
- **A false first-session failure in N-B.** The Native AA session formed 7 s after launch (21:59:24) inside `run_open`'s 20 s sleep, so `session_up` waiting for an SSL after that sleep printed `SESSION_FAIL`. The session was live throughout; the five cycles are valid. N-C formed normally.
- **Settings.** D-HU baseline backup taken before Stage W and restored at the end (read back equal). D-POCO backup `settings_backup_pr1065.xml` restored after Stage U (read back equal, after an `exec-in` write race on the first read). D-HU's audio keys found as `use-aac-audio` false, `audio-latency-multiplier` 8, `audio-queue-capacity` 20 and not changed. Settings deltas shown after each install are residue of earlier runs' keys; every run rewrites the file from the baseline plus its own keys.
- **D-POCO's head unit server** was off at the start of Stage W, on for H and N (hand step H3, restarted once after it wedged), and off for P and at the end, as found.
- **Hand steps** (`hand-steps.log`): H1 unplug D-POCO from the PC cable 20:37:44 (the first attempt timed out at 5 minutes and was redone); H1 dongle plugs 20:59:28 and 21:02:27 (first voided), and 21:25:00 for arm C; H1 unplug 21:23:43; H1 replug cue 21:34:48; H2 "Always" tick 21:16:06; closing H1 21:51:20; H3 server on, restart, off, over Stage W.
- **Crash in U0, outside the graded cycles.** Both arms' U0 captures carry one `FATAL EXCEPTION: main`: `BootCompleteReceiver` failing with `ForegroundServiceStartNotAllowedException`, at 21:02:22 (B) and in the voided 20:59:23 start, and again in C's U0, each as the app process starts after a force-stop. It is on `main` and on C alike. Every graded cycle's `fatal` column is 0.
- **The phone's `Critical error` count.** Every session we end with `ACTION_END_SESSION_STAY_ARMED` makes the phone log about one: `Failed to read message` (detail 54), `io error` (details 50 and 52) and `reason:1` (detail 34). The brief's phone condition of 0 cannot be met under this lever. Counts per run are in each section.
- **Grading.** Every count comes from my own greps over the cycle windows; no executor ran and there was no second count to compare.

## R0 Gate

**PASS**

| Arm | md5 | AutomaticReconnect / HeldServerSocket | commit | JVM tests |
|---|---|---|---|---|
| B | `3100817484e47e9a6418753308e92ed1` | 0 / 12 | `145a0c762f0a` | 2738, 0 failures (earlier build of the same commit) |
| C | `c95e8b932c6829ad76cad4277d6f3c7a` | 10 / 12 | `de1f98488d81` | 2741, 0 failures, B plus 3 |

The two md5s differ, C's tree is as stated, the symbol table matches, both installs used `adb install -r`, and Gearhead is `17.9.664004-release` on D-POCO and D-MOTO.

## U0

**PASS**

| Arm | Found line after launch | listener accepts | USB live | quiesce lines |
|---|---|---|---|---|
| B | 2 s | 1 | yes | 1 |
| C | 2 s | 1 | yes | 1 |

On each arm D-POCO's discovery found `192.168.1.11:5277` 2 s after the launch, the listener logged one accept from `192.168.1.8`, and a USB session with video formed after the plug cue, with `stopping the wireless stack for the duration of it` printed once. Both arms: `commit` as in R0. No H4 was needed.

## U2 (control)

**FAIL**

Both arms fail only on conditions that do not discriminate, spelled out below. Columns: cycle, `rearm`, `dq`, `df`, `masked`, `dssl` (ms), `failed`, `fatal`, phone `Critical error` count over the run.

| Arm | cycle | rearm | dq | df | masked | dssl | failed | fatal |
|---|---|---|---|---|---|---|---|---|
| B | 1 | 0 (void: no session at start) | NA | NA | 0 | NA | 0 | 0 |
| B | 2 | 1 | NA | 11395 | 1 | 12120 | 0 | 0 |
| B | 3 | 1 | NA | 29491 | 1 | 33356 | 0 | 0 |
| C | 1 | 0 (void: no session at start) | NA | NA | 0 | NA | 0 | 0 |
| C | 2 | 1 | NA | 5841 | 1 | 14658 | 0 | 0 |
| C | 3 | 1 | NA | 5078 | 1 | 9850 | 0 | 0 |

Phone `Critical error`: B 8, C 7. B fails the brief's `dssl` of 20000 ms or less in cycle 3 (33356 ms, a cycle that waited on the operator's allow tap before "Always" was ticked, deviation 5) and the phone's 0; C fails the phone's 0 only. The conditions that could separate the arms agree: `dq` is NA on every valid cycle (nothing dialled), both arms are masked, and the count of cycles with `df` in 2500 to 4500 ms is 0 on both.

## U1 (the point of the round)

**INCONCLUSIVE**

Finding 1 is not reproduced and not cleared. The brief's pre-registered outcome applies: "If every hit cycle is masked on both arms, U1 is INCONCLUSIVE ('the dongle re-enumerates after every session end, so the 3 s recheck is never the only way back on this rig')". Columns: cycle, `dq`, `hit`, `conn`, `df`, `masked`, `dssl` (ms), `failed`, `found`.

| Arm | cycle | dq | hit | conn | df | masked | dssl | failed | found |
|---|---|---|---|---|---|---|---|---|---|
| B | 1 | NA | 0 | 0 | NA | 0 | NA | 0 | 6 (void: `rearm` 0) |
| B | 2 | 2878 | 1 | 1 | 11444 | 1 | 16308 | 0 | 2 |
| B | 3 | 3012 | 0 | 0 | 8396 | 1 | 13420 | 0 | 2 |
| B | 4 | 2858 | 1 | 1 | 11904 | 1 | 12604 | 1 | 2 |
| B | 5 | 2862 | 1 | 1 | 7683 | 1 | 12694 | 1 | 2 |
| B | 6 | 2854 | 1 | 1 | 10538 | 1 | 11235 | 0 | 2 |
| B | 7 | 2851 | 1 | 1 | 5581 | 1 | 9929 | 0 | 1 |
| B | 8 | 2881 | 1 | 1 | 4938 | 1 | 9769 | 0 | 1 |
| B | 9 | 2891 | 1 | 0 | 5272 | 1 | 9889 | 0 | 1 |
| B | 10 | 2878 | 1 | 1 | 5596 | 1 | 9826 | 0 | 1 |
| B | 11 | 2905 | 1 | 0 | 4638 | 1 | 9841 | 0 | 1 |
| B | 12 | 2919 | 1 | 0 | 5044 | 1 | 9765 | 0 | 1 |
| C | 1 | 2908 | 1 | 1 | 6311 | 1 | 9782 | 0 | 1 |
| C | 2 | 2872 | 1 | 1 | 5416 | 1 | 9801 | 0 | 1 |
| C | 3 | 2877 | 1 | 1 | 4643 | 1 | 10049 | 0 | 1 |
| C | 4 | 2850 | 1 | 1 | 5162 | 1 | 9593 | 1 | 1 |
| C | 5 | 2882 | 1 | 0 | 5369 | 1 | 9885 | 0 | 1 |
| C | 6 | 2934 | 1 | 0 | 5836 | 1 | 9790 | 0 | 1 |
| C | 7 | 2873 | 1 | 1 | 5085 | 1 | 9650 | 0 | 1 |
| C | 8 | 2860 | 1 | 1 | 5583 | 1 | 8816 | 0 | 1 |
| C | 9 | 2861 | 1 | 1 | 5831 | 1 | 9063 | 0 | 1 |
| C | 10 | 2846 | 1 | 1 | 6462 | 1 | 8824 | 0 | 1 |
| C | 11 | 2876 | 1 | 1 | 5327 | 1 | 8877 | 0 | 1 |
| C | 12 | 2873 | 1 | 1 | 5754 | 1 | 8525 | 0 | 1 |

(`dh`, `gb` and `u_restore` routes: `dh` NA and `gb` 0 in every cycle on both arms; no cycle needed a restore route after the first.) Hits (`hit=1`): B 10, C 12; graded (hit and not masked): 0 on both. The mechanism tally over hit cycles, `df` between 2500 and 4500 ms: B 0, C 0, so the lost-timer signature (B above 0, C at 0) did not show. Where the collector missed the dial's `connecting` (`conn=0` on a hit, finding 2's precondition): B 3 of 10 hits, C 2 of 12. `session state failed` (note 3's shape): B in 2 cycles, C in 1. Phone `Critical error`: B 25, C 24. No `FATAL EXCEPTION` in any cycle.

What the capture shows about the dongle on each cycle, from `U1-B.logcat`: 1.6 s after the end verb the dongle detaches, 4.5 s later it re-attaches as the normal-mode device (`PID 4EE1`), the permission request follows, then it switches to accessory mode (`PID 2D00`) and the session forms about 10 to 12 s after the end. The dial lands at 2.85 to 3.01 s after the schedule line on both arms, driven by the app's own discovery against `fake5277.py`, independent of the permission dialog (which appears about 8 s after the end verb).

## H

**FAIL**

C fails only the brief's phone condition of 0 `Critical error` lines (24 on C, 22 on B; both sets are the session ends we cause). Everything the PR can affect passes. Columns per cycle: `ok`, `tssl` (ms from the end verb to the next SSL), `sched`, `dc` (ms from the schedule line to the first `connecting`), `fired`, then `usb`, `auto`, `keep`, `failed`, `fatal`, all 0 in every cycle.

| Arm | ok | tssl, cycles 1 to 10 (ms) | dc, cycles 1 to 10 (ms) | fired |
|---|---|---|---|---|
| B | 10 of 10 | 4280, 4379, 4249, 4743, 3304, 4389, 4215, 4357, 4565, 4479 | 2526, 2439, 2427, 2426, 2401, 2477, 2388, 2414, 2412, 2443 | 10 |
| C | 10 of 10 | 5080, 4156, 3311, 5091, 4963, 4049, 4020, 4369, 4315, 5617 | 2480, 2380, 2399, 2685, 3361, 2420, 2390, 3340, 2971, 3040 | 10 |

`sched` was 1 on every cycle on both arms. C's `ok` is 10 (at least 9 and at least B's 10 minus 1). On every C cycle with `dc` of 2000 ms or more, `fired` is 1 (all 10). F2-shape cycles (no SSL, no dial, no restart, server still listening): 0 on both arms, and no cycle was void. Those are 20 cycles; as the brief says, a count of 0 over about 30 does not clear the race.

## N

**FAIL**

C fails only the phone condition of 0 `Critical error` lines (18 on C, 20 on B). Columns per cycle: `ok`, `tssl` (ms), `sched`, `usb`, `auto`, `keep`, all as listed.

| Arm | ok | tssl, cycles 1 to 5 (ms) | sched / usb / auto | keep |
|---|---|---|---|---|
| B | 5 of 5 | 3830, 3712, 3794, 3485, 3672 (median 3712) | 0 / 0 / 0 on all five | 1 on all five |
| C | 5 of 5 | 3923, 3262, 3456, 3408, 3640 (median 3456) | 0 / 0 / 0 on all five | 1 on all five |

C's median `tssl` is below B's, well inside B plus 3000 ms; no `failed` states, no `createGroup SUCCESS` inside a cycle, no `FATAL EXCEPTION`. Native AA schedules no timer on either arm, as the brief states.

## P

**INCONCLUSIVE**

B formed no first session, so the brief's rule applies ("If B forms no first session, P is INCONCLUSIVE"). C formed none either. The cause is on the phone and is the same on both arms. The helper (debug build at `8ac36c9`, mode 0, read back `connection_mode` 0) found an `AAWireless` service over NSD, resolved it at `192.168.1.22` (not D-HU, which is `192.168.1.4`; I did not identify the device behind that address) and started its proxy, then Gearhead refused its launch:

```
HUREV_TRIGGER: Activity launch failed (Permission Denial: starting Intent { flg=0x34000000
  cmp=com.google.android.projection.gearhead/com.google.android.apps.auto.wireless.setup.service.impl.WirelessStartupActivity ...
  from ProcessRecord{...:com.andrerinas.wirelesshelper.debug/...} ... not exported from uid ...). Attempting Broadcast fallback...
HUREV_TRIGGER: Broadcast fallback 1 (WirelessStartupReceiver) sent.
HUREV_TRIGGER: Broadcast fallback 2 (WifiBluetoothReceiver START_WIRELESS_PROJECTION ...) sent.
```

`Activity launch failed` 7 times on B and 6 on C, `AA is now flowing through proxy` 0 on both, `PFIRST=0` on both. This is the same refusal as the Nearby round before this one. The operator reported that the helper became unusable after Android Auto 17.3; the capture is consistent with that, and I have seen sessions through the helper only on Gearhead 17.5 and helper 1.9.3 in an earlier round, not on `17.9.664004`. No cycle ran. The last 30 `HUREV_` lines of each arm are in `P-B.last30-HUREV.txt` and `P-C.last30-HUREV.txt`. An earlier P run on B did form sessions, but through D-POCO's own head unit server (`Found Headunit Server on 192.168.1.8:5277`), not the helper; that run is voided and kept in `voided-P-B-devserver-on/`.

## Anything the brief did not ask about

- **Brief fixes worth making.** The phone's "0 `Critical error`" condition cannot hold when the lever is a session end by verb; U2's `dssl` limit and the start of U1 need to expect that the first USB attempt after an app restart fails on a stale dongle session (every Stage U start on both arms logged `Drained 1303808 bytes of stale USB data ... SSLException: Unable to parse TLS packet header`, then retried); and P cannot run on a Gearhead that refuses the helper's launch.
- **The dongle re-enumerates itself after every session end** on this rig, which masks the 3 s recheck in all 23 valid U1 cycles. A rig change (a dongle that does not re-enumerate, or a lever that ends the session without the dongle seeing the link close) is what U1 needs. This round measured the dial's timing precisely: 2846 to 3012 ms after the schedule line, so it lands inside the 3 s window in 22 of 23 valid cycles.
- **A permission-dialog effect on timing** (deviation 5): arm B's cycles before the "Always" tick took longer to recover; this is operator timing, and arm C was unaffected.
- **A stale `AAWireless` NSD service at `192.168.1.22`** answered the helper's NSD query; I did not identify the device behind it.
- **A start-up crash on `main`.** `BootCompleteReceiver` raises `ForegroundServiceStartNotAllowedException` as the app process starts after a force-stop on D-POCO (API 35), in both arms' U0 captures. It is not part of this PR.
- **Evidence:** `pr-1065-reconnect-timers-round1-captures.zip`, asset of release `rig-evidence-pr-1065-reconnect-timers`.
