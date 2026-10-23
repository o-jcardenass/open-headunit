# pr-1066-byebye-priority - round 1 results

**Candidate (M):** `main` @ `145a0c762f0a87386ca63b54518ea5e861b290be` plus `codex/byebye-send-priority` head `947fbc52971ba83f11d95bcb63be20a8a2e649f0` (rebased onto `main`, no merge commit), tree `a7c0ca3361f3907eed18f5df11b387e260b7e129`, the brief's expected tree   **Baseline (B):** `main` @ `145a0c762f0a87386ca63b54518ea5e861b290be`
**APK md5:** M `be2f28a73dfeec63477a056d451e54e8` / B `3100817484e47e9a6418753308e92ed1`
**Units:** Stage H: D-HP (HP Slate 7 Plus, API 17) as head unit in Headunit Server mode, phone D-POCO (POCO X3, API 35, Android Auto `17.9.664004-release`, developer head unit server on port 5277). Stage W: D-HU (API 34) as head unit, D-POCO as phone. Stage U: D-MOTO (API 34) as USB host, D-POCO as device over OTG.
**Date:** 2026-10-06
**Evidence:** release `rig-evidence-pr-1066-byebye-priority`, asset `pr-1066-byebye-priority-round1-captures.zip`, sha256 `5bead62d036b43a2dcc2152e1de4bac08fd6d87747067308506cae189be5b1ec`

**Summary (not a graded verdict).** The change does what it says on all three transports: `AapTransport: ByeBye write SENT` is on every one of M's 13 exits, `stop_to_quit_ms` falls from a median of 150 to 152 ms on B to 10, 6 and 2 ms on M (H, W, U), and no teardown counter moves (`join_fail`, `incomplete`, `teardown_err` are 0 on all 26 exits). The phone received the ByeBye on 12 of 13 M exits (`ref` 1). The one exit without it is H-M-x5: `write=SENT`, the phone read the FIN (`eos` 1) and logged no ByeBye line at all (`bye` 0) and no reset. No M exit shows the brief's loss signature (`rst` of 1 or more with `ref` 0), and every reconnect came back (15 of 15 on H and W). Every M run still reads FAIL by the brief's letter, and each fails on one condition only, the phone's `Critical error` count of 0: B shows 2 to 3 on every exit as well, so that condition cannot hold at a session end we cause (the finding already recorded in earlier rounds). The B runs read FAIL for the same reason and because the brief gives B no pass rule.

## Setup notes

- **Brief resolved from a commit that carried five briefs.** The operator chose all five in order; this file covers the fourth of the five. Quirk files read (once, for the round): `rig-quirks/topics/tooling.md`, `rig-quirks/topics/gearhead.md`, `rig-quirks/topics/wifi.md`, `rig-quirks/units/D-HP.md`, `rig-quirks/units/D-POCO.md`, `rig-quirks/units/D-MOTO.md`, `rig-quirks/units/D-HU.md`.
- **Candidate head moved.** The contributor's branch was rebased after the brief. I compared trees: head `947fbc52971b` has tree `a7c0ca3361f3907eed18f5df11b387e260b7e129`, the brief's tree, so I built the new head (two commits: `cdba5e910` "Protocol: report incomplete encrypted writes", `947fbc529` "Protocol: prioritize and await the final ByeBye write") on top of `main` @ `145a0c762f0a`. `QUERY_STATE` was not used for identity in this round, the DEX string and the installed md5 were.
- **Build gates.** B: the reused build of `main` `145a0c76`, md5 `3100817484e47e9a6418753308e92ed1` (same APK as the pr-1063 B arm). M: built with `build_hur_cool.sh` under the thermal guard in a scratch worktree (`local.properties` copied in), JVM tests 2744 run, 0 failures, 0 errors, `TEST-com.andrerinas.openheadunit.aap.FinalMessageDeliveryTest.xml` present. Identity per stage from `stage.sh`: installed md5 equals the arm's md5 on D-HP, D-HU and D-MOTO for every arm, and the count of the string `AapTransport: ByeBye write` in the pulled APK's DEX is 0 on B and 1 on M.
- **Pre-flight** (`rig_preflight.sh`, before stage H: `D_HP:wifi D_POCO:wifi`): D_HP adb ok, WiFi 1, BT 1, Awake, HFP none, A2DP none. D_POCO adb ok, WiFi 1, BT 1, Awake, HFP `XX:XX:XX:XX:33:59`, A2DP up. PREFLIGHT OK. D-POCO's Bluetooth was on with a live HFP and A2DP link, which the earlier rounds of this session did not have; I did not touch it. Before stage W: `D_HU:wifi D_POCO:wifi D_SAM:wifi` all OK (D_SAM BT 1, Asleep).
- **Host thermal.** `rig_thermal.sh watch` ran during the build (maximum 67C at the last sample) and no capture was recording during Gradle. Per-stage maximum from each run's thermal log: H-B 73C, H-M 67C, W-B 71C, W-M 68C, U-B 66C, U-M 65C, `throttle_pkg` flat at 2458 each (delta 0), so no run was voided for heat.
- **Hand steps.** H1 (D-POCO's head unit server): started by the operator before stage H, listening on port 5277 and on `:149D` in `/proc/net/tcp6`, never restarted (no `srv_restart` after the first session on H-B or H-M). H2: six USB cues in the kept U runs (`hand-steps.log`), all answered; see U-B and U-M.
- **D-SAM Bluetooth (brief, stage W).** The brief asks for D-SAM's Bluetooth off for the stage. `svc bluetooth disable` is not available on D-SAM (`svc` has no `bluetooth` subcommand there) and `bluetooth_on` stayed 1, so I could not do it by script and did not use the UI. The stage W first session formed on both arms regardless. D-SAM is unchanged.
- **Stage U wired up as the brief says.** D-MOTO (`192.168.1.5:5555`) and D-POCO (`192.168.1.8:5555`) were put on wireless adb with `adb tcpip 5555` while both were still on USB, then the operator moved D-MOTO's cable to the OTG link. D-MOTO reads `host_connected=true` and D-POCO `current_functions=ACCESSORY` on that link. The pre-stage `dumpsys usb` on D-MOTO was captured while D-MOTO was still on the PC cable (`host_connected=false`), not on the OTG link: a deviation. The brief's D-HU `stat` of `shared_prefs` returned an error (`stat -c` unsupported by that shell), so the owner was not recorded.
- **Settings.** Backups first (`settings-backup-H.xml`, `-W.xml`, `-U.xml`). Written with the brief's helpers with the app stopped and read back by `stage.sh`: stage H `wifi-connection-mode` 1, `log-level` 2, `onboarding-version` 2, `connection-modes` wifi; stage W `wifi-connection-mode` 3; stage U `wifi-connection-mode` 0, `connection-modes` usb, `view-mode` 2 on D-MOTO. Audio keys never written. Baselines are restored after approval (see the end).
- **`xt` artifact.** In the `xt` rows a `ref` of 0 prints as `ref=0` followed by `NA` on a new line, because grep's no-match exit status also ran the `|| echo NA` branch. The H-M-x5 row therefore looks cut off in `H-M.xt`; every field after it (`bye` 0, `eos` 1, `rst` 0, `crit` 3, `playing` 1) is in the next line, and I confirmed `bye`, `eos`, `rst` and `crit` for x5 with my own greps over the same window. No other row is affected.
- **U-B first attempt voided (not the operator).** The first attempt formed a USB session at 23:03:09 (device in accessory mode PID 2D01, permission granted at 23:03:08, SSL complete at 23:03:09.188) and it died 15 ms later: two handshake threads (`[252]` and `[254]`) ran on the same link, `[254]` threw `SSLException: Unable to parse TLS packet header` and `AapTransport quitting (clean=false)` followed at 23:03:09.204. Two `Found device already in accessory mode` events (23:03:05 and 23:03:08) preceded it, the dongle-style re-enumeration doubling, not an operator miss. My cue also reached the operator late (the cue was logged at 23:02:56 but showed on screen well after, so the replug at about 23:03:03 was before it was seen). My `plug()` then returned the exit status of its last `waitfor` (throughput never appeared), which printed `OPERATOR_MISSED U-B-p1` and ended the arm with no further cycles. I changed `blk-U.sh` so a session that forms but never renders returns `no_session` (continue) and not an operator miss, kept the attempt under `attempt1-U-B/`, and reran U-B from the start. That was one failed session, not an INCONCLUSIVE.
- **Grading.** Every count is from my own greps over a marker window (`<ID>-go` to `<ID>-done`; the head-unit side ends at `AapTransport quitting (clean=`). No executor was used. Discard checks over every capture: `Magic Garbage detected in header` 0, `MATCH! Starting AapService` 0, `LOAD_FAIL` 0 in every stage log, and no `SSL handshake complete` between an exit's `-go` marker and its quitting line (all 26 exits 0), so no exit was voided and no replacement was run.
- **REF.** `received ByeByeRequest`, from H-B-x1: phone line `D CAR.GAL.GAL.LITE: received ByeByeRequest` at epoch 1791344747.283, 1.157 s before the phone's `ReaderThread: end of stream received` (1791344748.440) in the same window. It is Gearhead's own receipt of our ByeByeRequest message, so `ref` is a direct receipt, not an inference from the FIN.
- **`resp` bias.** `resp` (`Byebye Response received`) is how often the head unit was still reading when the phone's reply came. M quits within 0 to 11 ms of the write, so it stops reading before the reply: `resp` is 2 of 5 (H-M), 1 of 5 (W-M) and 3 of 3 (U-M) against 5 of 5 (H-B), 5 of 5 (W-B) and 3 of 3 (U-B). It says nothing about whether the phone got the ByeBye.

## H-B

**FAIL**

Reference arm. The brief gives B no pass rule; I applied the H-M list to it for comparison. It fails because `write` is empty (B has no `ByeBye write` line, as the DEX count of 0 says) and `crit` is 2. Everything else is what B should show: the phone logged `received ByeByeRequest` on 5 of 5 exits and no reset.

| exit | disc | stop | write | quit | stop_to_quit_ms | join_fail | incomplete | teardown_err | resp | ref | bye | eos | rst | crit | reconnect |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| H-B-x1 | 1 | 1 | - | 1 | 150 | 0 | 0 | 0 | 1 | 1 | 3 | 1 | 0 | 2 | ok |
| H-B-x2 | 1 | 1 | - | 1 | 160 | 0 | 0 | 0 | 1 | 1 | 3 | 1 | 0 | 2 | ok |
| H-B-x3 | 1 | 1 | - | 1 | 150 | 0 | 0 | 0 | 1 | 1 | 3 | 1 | 0 | 2 | ok |
| H-B-x4 | 1 | 1 | - | 1 | 150 | 0 | 0 | 0 | 1 | 1 | 3 | 1 | 0 | 2 | ok |
| H-B-x5 | 1 | 1 | - | 1 | 150 | 0 | 0 | 0 | 1 | 1 | 3 | 1 | 0 | 2 | ok |

Reconnect 5 of 5 `reconnect=ok`; no `srv_restart` after the first session. First session SSL at 22:45:31.4. Median `stop_to_quit_ms` 150. Decisive lines, H-B-x1 (`H-B.logcat`, head unit side):

```
22:45:48.492 AapTransport: AapTransport stopping and sending byebye (USER_SELECTION)
22:45:48.642 AapTransport: AapTransport quitting (clean=false)
```

and the phone (`H-B.phone.logcat`): `1791344747.283 D CAR.GAL.GAL.LITE: received ByeByeRequest`, then `1791344748.440 W CAR.GAL.GAL.LITE: ReaderThread: end of stream received, dataReceived=true, isWireless=false`.

## H-M

**FAIL**

The point of the round. By the brief's letter this is a FAIL on one condition, `crit` 0 on every exit: `crit` reads 2, 4, 3, 4, 3 on M against 2 on every B exit, and the phone logs `Critical error` lines at every session end we cause. Every other condition holds: condition 1 (`write` reads `SENT`, `quit` 1, the teardown counters 0) on all 5 exits; condition 2 (exits with `rst` of 1 or more and `ref` 0, M's count no higher than B's and at most 1 of 5): M has 0 such exits (x2 and x4 show `rst` 2 but also `ref` 1, see below), B has 0; condition 3 (`ref` of 1 or more on M at least B's count minus 1): M 4, B 5, 4 is not below 4; condition 4: 5 of 5 `reconnect=ok`, no wedge, no `srv_restart` after the first session.

| exit | disc | stop | write | quit | stop_to_quit_ms | join_fail | incomplete | teardown_err | resp | ref | bye | eos | rst | crit | reconnect |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| H-M-x1 | 1 | 1 | SENT | 1 | 10 | 0 | 0 | 0 | 1 | 1 | 3 | 1 | 0 | 2 | ok |
| H-M-x2 | 1 | 1 | SENT | 1 | 10 | 0 | 0 | 0 | 0 | 1 | 3 | 1 | 2 | 4 | ok |
| H-M-x3 | 1 | 1 | SENT | 1 | 0 | 0 | 0 | 0 | 0 | 1 | 3 | 1 | 0 | 3 | ok |
| H-M-x4 | 1 | 1 | SENT | 1 | 10 | 0 | 0 | 0 | 1 | 1 | 3 | 1 | 2 | 4 | ok |
| H-M-x5 | 1 | 1 | SENT | 1 | 10 | 0 | 0 | 0 | 0 | 0 | 0 | 1 | 0 | 3 | ok |

Median `stop_to_quit_ms` 10 (B 150). First session SSL at 22:49:21.2. Decisive lines, H-M-x1 (`H-M.logcat`):

```
22:49:38.582 AapTransport: AapTransport stopping and sending byebye (USER_SELECTION)
22:49:38.592 AapTransport: AapTransport: ByeBye write SENT
22:49:38.592 AapTransport: AapTransport quitting (clean=false)
```

and the phone: `1791344977.374 D CAR.GAL.GAL.LITE: received ByeByeRequest`, then `1791344977.516 W CAR.GAL.GAL.LITE: ReaderThread: end of stream received`.

**The two exits with `rst` 2.** H-M-x2 and H-M-x4 each logged `received ByeByeRequest` first (x2: epoch 1791345009.765) and then, 56 ms later, `java.io.IOException: write failed: EPIPE (Broken pipe)` (1791345009.821): the phone replying on a socket we had already closed. That is the receipt followed by our close, not a lost ByeBye, which is why the brief's loss signature (a reset with no `ref`) does not apply.

**The one exit without a receipt.** H-M-x5: `write=SENT` at 22:51:49.262, `quit` at 22:51:49.262, the phone logged `ReaderThread: end of stream received` at 1791345108.168 and `Critical error 3 / 3 / 18` at 1791345109.183, but no `received ByeByeRequest` and no other ByeBye line (`bye` 0) and no reset (`rst` 0). Per section 6 of the brief the FIN means TCP delivered every byte before it, so the ByeBye did arrive in the byte stream, but the phone did not log it as it did on x1 to x4. Four exits in five show the line, B shows it five in five, and the brief's condition 3 allows one fewer. This is the one exit worth a second look on a larger sample.

## W-B

**FAIL**

Reference arm; the same yardstick as H-B, failing on `write` empty and `crit` 3. The phone logged `received ByeByeRequest` and `end of stream received` on 5 of 5 exits and no reset.

| exit | disc | stop | write | quit | stop_to_quit_ms | join_fail | incomplete | teardown_err | resp | ref | bye | eos | rst | crit | reconnect |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| W-B-x1 | 1 | 1 | - | 1 | 155 | 0 | 0 | 0 | 1 | 1 | 5 | 1 | 0 | 3 | ok |
| W-B-x2 | 1 | 1 | - | 1 | 152 | 0 | 0 | 0 | 1 | 1 | 5 | 1 | 0 | 3 | ok |
| W-B-x3 | 1 | 1 | - | 1 | 153 | 0 | 0 | 0 | 1 | 1 | 5 | 1 | 0 | 3 | ok |
| W-B-x4 | 1 | 1 | - | 1 | 152 | 0 | 0 | 0 | 1 | 1 | 5 | 1 | 0 | 3 | ok |
| W-B-x5 | 1 | 1 | - | 1 | 152 | 0 | 0 | 0 | 1 | 1 | 5 | 1 | 0 | 3 | ok |

Median `stop_to_quit_ms` 152. First session SSL at 22:53:17.7. Reconnect 5 of 5 `reconnect=ok`.

## W-M

**FAIL**

Conditions 1 to 3 of H-M applied to W, as the brief says. Condition 1 holds on all 5 exits (`write` `SENT`, `quit` 1, teardown counters 0). Condition 3 holds (`ref` 5 on M, 5 on B). Condition 2 fails only on `crit` 0: it reads 3, 5, 4, 4, 5 on M against 3 on every B exit. `rst` is 1 or 2 on all five M exits and `eos` is 0 on all five, but every one of them also has `ref` 1, so none is the loss signature. The pattern is the phone logging `received ByeByeRequest` and then `java.net.SocketException: Connection reset` (W-M-x1: ByeByeRequest at 1791345437.917, reset 52 ms later at 1791345437.969) because our socket was already closed. The reconnect is reported, not graded: 5 of 5 `reconnect=ok`.

| exit | disc | stop | write | quit | stop_to_quit_ms | join_fail | incomplete | teardown_err | resp | ref | bye | eos | rst | crit | reconnect |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| W-M-x1 | 1 | 1 | SENT | 1 | 11 | 0 | 0 | 0 | 0 | 1 | 5 | 0 | 1 | 3 | ok |
| W-M-x2 | 1 | 1 | SENT | 1 | 6 | 0 | 0 | 0 | 1 | 1 | 5 | 0 | 1 | 5 | ok |
| W-M-x3 | 1 | 1 | SENT | 1 | 5 | 0 | 0 | 0 | 0 | 1 | 5 | 0 | 2 | 4 | ok |
| W-M-x4 | 1 | 1 | SENT | 1 | 4 | 0 | 0 | 0 | 0 | 1 | 5 | 0 | 1 | 4 | ok |
| W-M-x5 | 1 | 1 | SENT | 1 | 6 | 0 | 0 | 0 | 0 | 1 | 5 | 0 | 2 | 5 | ok |

Median `stop_to_quit_ms` 6 (B 152). First session SSL at 22:57:02.2. Decisive lines, W-M-x1 (`W-M.logcat`):

```
22:57:18.911 AapTransport: AapTransport stopping and sending byebye (USER_SELECTION)
22:57:18.921 AapTransport: AapTransport: ByeBye write SENT
22:57:18.922 AapTransport: AapTransport quitting (clean=false)
```

## U-B

**FAIL**

Reference arm; the same yardstick, failing on `write` empty and `crit` 2. Three USB sessions formed (the brief needs 2 or more), each rendered at 24 to 29 fps with `dropped=0`; the phone logged `received ByeByeRequest` on 3 of 3 and no reset.

| exit | disc | stop | write | quit | stop_to_quit_ms | join_fail | incomplete | teardown_err | resp | ref | bye | eos | rst | crit | reconnect |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| U-B-x1 | 1 | 1 | - | 1 | 152 | 0 | 0 | 0 | 1 | 1 | 3 | 0 | 0 | 2 | - |
| U-B-x2 | 1 | 1 | - | 1 | 152 | 0 | 0 | 0 | 1 | 1 | 3 | 0 | 0 | 2 | - |
| U-B-x3 | 1 | 1 | - | 1 | 151 | 0 | 0 | 0 | 1 | 1 | 3 | 0 | 0 | 2 | - |

Median `stop_to_quit_ms` 152. The three USB cues were logged at 23:05:14, 23:06:04 and 23:06:50; each session's SSL completed after the operator's replug (23:05:30.9, 23:06:16.6, 23:06:58.7). The first attempt is voided, see Setup notes.

## U-M

**FAIL**

Condition 1 holds on all three exits and the phone logged `received ByeByeRequest` on 3 of 3 with `rst` 0 and `eos` 0; the only failed condition is `crit` 0, which reads 2 on each M exit and 2 on each B exit (on the USB side U-M-x1's phone lines include `Critical error 4 detail: 34 msg: reason:1` and `Critical error 18 detail: 54 msg: Failed to read message`). Three sessions formed, each rendering at 25 to 29 fps with `dropped=0`.

| exit | disc | stop | write | quit | stop_to_quit_ms | join_fail | incomplete | teardown_err | resp | ref | bye | eos | rst | crit | reconnect |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| U-M-x1 | 1 | 1 | SENT | 1 | 4 | 0 | 0 | 0 | 1 | 1 | 3 | 0 | 0 | 2 | - |
| U-M-x2 | 1 | 1 | SENT | 1 | 2 | 0 | 0 | 0 | 1 | 1 | 3 | 0 | 0 | 2 | - |
| U-M-x3 | 1 | 1 | SENT | 1 | 2 | 0 | 0 | 0 | 1 | 1 | 3 | 0 | 0 | 2 | - |

Median `stop_to_quit_ms` 2 (B 152). The cues were logged at 23:08:18, 23:09:01 and 23:09:57; the SSL completions were 23:08:28.5, 23:09:23.7 and 23:10:09.037. Decisive lines, U-M-x1: `AapTransport: ByeBye write SENT` then `quitting (clean=false)`, and the phone `1791346130.250 D CAR.GAL.GAL.LITE: received ByeByeRequest`.

## Anything the brief did not ask about

- **The `crit` 0 condition should be dropped or reworded.** The phone logs `Critical error` lines at every session end we cause, on both arms (B 2 to 3, M 2 to 5, USB included), so no run can pass as written. A condition on `ref` and on `rst` with no `ref` already carries the discrimination the brief wants.
- **M's `crit` and `rst` are higher on the socket transports** (H: M 2 to 4 against B 2; W: M 3 to 5 against B 3, with `rst` 1 to 2 on every M exit against 0 on B). It tracks the phone answering a socket we have already closed (`EPIPE` on H, `Connection reset` on W), after it has logged the ByeBye. It is a measurable difference between the arms and says only that M closes sooner.
- **H-M-x5 is the one exit that did not show a phone receipt** (`write=SENT`, `eos` 1, `bye` 0). A larger sample of M exits on the Headunit Server path would say whether that was a one-off.
- **`QUERY_STATE` was not used for identity** this round; the installed md5 and the DEX string were, and they agree with the arm on every stage.
- **The brief's `listening` check proves a socket, not a working server** (the pr-1063 finding stands); no wedge occurred this round.
- **Open items for the operator:** D-SAM's Bluetooth could not be turned off by script; D-POCO's Bluetooth was on with HFP and A2DP up throughout. Neither stopped a first session.
- **Evidence:** `pr-1066-byebye-priority-round1-captures.zip`, asset of release `rig-evidence-pr-1066-byebye-priority`.
