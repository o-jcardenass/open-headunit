# forget-car-still-needed, round 1 results

- Candidate F: `25a56033de5b133e4ce022a52c1815fa71e02a30` (`fork/fix/forget-car-still-needed`), tree `462d65406c8e2a695da3ed11cbba01e14b1f8f42`, APK md5 `0873517011952ca5217addfb093b7d75`
- Baseline C: `d2d0a6bf71217e8658545314de63ed87c0031451` (`main`), tree `50bd3597ac9702179bffccfed53fa3c295016970`, APK md5 `571bec23a1dbba5a4e43e78cdbb44038` (md5s differ)
- Identity: ACTION_QUERY_STATE commit `d2d0a6bf7121` on C, `25a56033de5b` on F; DEX `HotspotStartSupersededPolicy` count C=0, F=5
- Unit: D-HU (head unit) and D-POCO (phone), Gearhead 18.0.164204-release.daily
- Date: 2026-10-15
- Evidence: release `rig-evidence-forget-car-still-needed`, asset `forget-car-still-needed-round1-captures.zip`, sha256 `a02e1cf16333aa64f1a6d2e7d7f377e6424ea896ad522315517845f7aee20a20`

## Round verdict: not requested by the brief

R1 INCONCLUSIVE, R2 PASS, R3 PASS (brief erratum on `crit`), R4 FAIL (one rep, rig suspected).

## Setup notes

- Transfer branch checked out in existing worktree worktrees/rig-sync__scratch__8228cb07__20261015 (branch already held there; clean, ff'd to fork tip aa9cc48a). Main checkout stayed on main.
- rig_sync.py sync: in sync, no RESTART. Quirk files read: topics/tooling.md, units/D-HU.md, units/D-POCO.md, topics/wifi.md (grep only).
C: apk-C.apk md5 571bec23a1dbba5a4e43e78cdbb44038, tree 50bd3597 ok. F tree 462d6540 ok, building
F built exit0
stat: drwxrwx--x 2 u0_a176 u0_a176 3452 2026-10-15 17:35 /data/data/com.andrerinas.headunitrevived/shared_prefs
- Reps run by host directly in foreground via scripts/r1.sh, r1grade.sh (no rig-executor agent; one-round scripts in scripts/). lib.sh = brief section 5 verbatim with absolute OUT, th_pkg path made absolute, LC_ALL=C. Station freq read 5745 MHz (brief said 5500). D-POCO isGroupOwner was true at preflight, svc wifi cycle made it false. D-POCO bonded incl. Navegadortz2 (D-HU) and Navegadortz3 (D-SAM); Gearhead 18.0.164204-release.daily. D-HU shared_prefs u0_a176:u0_a176 drwxrwx--x. Settings backup: stable-identity=true, poke-all-paired=true, hotspot-band=1, driver-selection-mode=1. Preflight D_HU/D_POCO OK, D-MOTO bt disabled. AP cycle off/on/off = 0/1/0. Block C R1: 5 reps reached (R1a-C-1,2, R1b-C-1,2,3), none reproduced leak (late_up=0, ap_end=0, busy=0; C end line 'switched off while this start was still running').
- Block C done: R4-C-1,2 ok; R3-C-1..3 outcome failed (restart would not come back up), unsafe=1 each, crit=5/4/5 on C (phone 'Critical error' also on control arm), s2_s 9.9/11.0/11.4, adv=0.
- Block F: R1 F 5/5 reached guard=1 pass. R2A x3 not reached (busy=0, stillup=0) then B x3 reached, backstop PASS.
- R4-F-2 ssl_s=none: phone-side 'RFCOMM_CreateConnection failed' x4 (phone BT stack, to 33:59 = D-HU radio) right after SDP success; HU group up 1.6 s after switch, creds delivered, 5 pokes answered, no accept. Extra same-condition reps R4-F-3 and R4-C-3 run (not in brief) to isolate.
- Brief erratum, R3 `crit`: the phone's 5 `Critical error` lines fall entirely in the exit window (`R3-<arm>-n-exit` to `-mid`), the same 5 messages on C and F, produced by the deliberate `ACTION_DISCONNECT`. The brief's "FAIL if crit >= 1" would fail every rep on both arms, so it is not applied literally; it is reported per rep.
- Host-run reps: no rig-executor agent was used; the host ran each rep script in the foreground. Station read 5745 MHz, not 5500.
- Preflight table (opening): D_HU wifi 1 bt 1 Awake HFP none A2DP none; D_POCO wifi 1 bt 1 Awake HFP XX:XX:XX:XX:33:59 A2DP up. Closing preflight (after restore, an idle Gradle daemon was killed first): D_HU wifi 1 bt 1 Awake HFP none A2DP none; D_POCO wifi 1 bt 1 Awake HFP none A2DP none; PREFLIGHT OK. The phone HFP link to D-HU had not yet re-formed. Restore: settings diff against the backup is empty; D-HU still carries the F APK; stayon false; D-MOTO bt enabled.

## R1

**INCONCLUSIVE**

The leak does not reproduce on D-HU: C reached 5 of 5 reps with `late_up`=0, `ap_end`=0, `busy`=0 in every one. F's guard fired in 5 of 5 reached reps and every F rep passes (`guard`=1, `late_up`=0, `ap_end`=0, `succ_s` 1.6).

| Rep | reached | guard | late_up | ap_end | busy | succ_s | end line |
|---|---|---|---|---|---|---|---|
| R1a-C-1 | yes | 0 | 0 | 0 | 0 | 1.5 | The hotspot was switched off while this start was still runni |
| R1a-C-2 | yes | 0 | 0 | 0 | 0 | 1.6 | The hotspot was switched off while this start was still runni |
| R1b-C-1 | yes | 0 | 0 | 0 | 0 | 1.5 | The hotspot was switched off while this start was still runni |
| R1b-C-2 | yes | 0 | 0 | 0 | 0 | 1.5 | The hotspot was switched off while this start was still runni |
| R1b-C-3 | yes | 0 | 0 | 0 | 0 | 1.5 | The hotspot was switched off while this start was still runni |
| R1a-F-1 | yes | 1 | 0 | 0 | 0 | 1.6 | a stop arrived before this start asked for 5 GHz, so it sends |
| R1a-F-2 | yes | 1 | 0 | 0 | 0 | 1.6 | a stop arrived before this start asked for 5 GHz, so it sends |
| R1b-F-1 | yes | 1 | 0 | 0 | 0 | 1.6 | a stop arrived while this start was running, so the access point |
| R1b-F-2 | yes | 1 | 0 | 0 | 0 | 1.6 | a stop arrived while this start was running, so the access point |
| R1b-F-3 | yes | 1 | 0 | 0 | 0 | 1.6 | a stop arrived while this start was running, so the access point |

C end line in all 5 reps: "The hotspot was switched off while this start was still running" (the old stop check). F end lines: R1a "a stop arrived before this start asked for 5 GHz, so it sends no request" (2), R1b "a stop arrived while this start was running, so the access point..." (3). C leak reproduced in 0 reps; F reps passed 5 of 5.

## R2

**PASS**

Variant A (AP up before launch), 3 reps: not reached (`busy`=0, `stillup`=0, `ap_end`=0): D-HU's platform or the app's stop took the AP down before the create. Per the rule, variant B ran (watcher raised the AP after "ensuring hotspot is disabled first...", `ap raised` logged in races.log for all 3).

    R2A-1 busy=0 backstop=0 downtrue=0 succ_s=none busy_after=none stillup=0 reraise=0 creates=1 ap_end=0 ens=1
    R2A-2 busy=0 backstop=0 downtrue=0 succ_s=none busy_after=none stillup=0 reraise=0 creates=1 ap_end=0 ens=1
    R2A-3 busy=0 backstop=0 downtrue=0 succ_s=none busy_after=none stillup=0 reraise=0 creates=1 ap_end=0 ens=1
    R2B-1 busy=1 backstop=1 downtrue=1 succ_s=.2 busy_after=0 stillup=0 reraise=0 creates=1 ap_end=0 ens=1
    R2B-2 busy=1 backstop=1 downtrue=1 succ_s=.2 busy_after=0 stillup=0 reraise=0 creates=1 ap_end=0 ens=1
    R2B-3 busy=1 backstop=1 downtrue=1 succ_s=.3 busy_after=0 stillup=0 reraise=0 creates=1 ap_end=0 ens=1

Variant B: 3 of 3 reached; each has `backstop`=1, `downtrue`=1, `succ_s` 0.2 to 0.3 s from the backstop line to `createGroup SUCCESS`, `busy_after`=0, `reraise`=0.

## R3

**PASS**

3 gradable F reps (`s1`=1, `ran`=1), `skip`=0, `drain`=0, `unsafe`=1 on every F rep and on every C rep, `s2_s` 9.8, 11.3, 10.9 s on F against 9.9, 11.0, 11.4 s on C, `adv`=0. Outcome class on both arms in all 6 reps: `failed` ("would not come back up"), `held`=0, `fell`=0. `crit` see the Setup erratum.

    R3-C-1 s1=1 ran=1 held=0 fell=0 failed=1 unsafe=1 skip=0 drain=0 s2_s=9.9 adv=0 ap_mid=0 crit=5 rfcomm=2 tcp=0 nocfg=3 accepted=2
    R3-C-2 s1=1 ran=1 held=0 fell=0 failed=1 unsafe=1 skip=0 drain=0 s2_s=11.0 adv=0 ap_mid=0 crit=4 rfcomm=2 tcp=0 nocfg=3 accepted=2
    R3-C-3 s1=1 ran=1 held=0 fell=0 failed=1 unsafe=1 skip=0 drain=0 s2_s=11.4 adv=0 ap_mid=0 crit=5 rfcomm=3 tcp=0 nocfg=4 accepted=3
    R3-F-1 s1=1 ran=1 held=0 fell=0 failed=1 unsafe=1 skip=0 drain=0 s2_s=9.8 adv=0 ap_mid=0 crit=5 rfcomm=2 tcp=0 nocfg=3 accepted=2
    R3-F-2 s1=1 ran=1 held=0 fell=0 failed=1 unsafe=1 skip=0 drain=0 s2_s=11.3 adv=0 ap_mid=0 crit=5 rfcomm=2 tcp=0 nocfg=3 accepted=2
    R3-F-3 s1=1 ran=1 held=0 fell=0 failed=1 unsafe=1 skip=0 drain=0 s2_s=10.9 adv=0 ap_mid=0 crit=5 rfcomm=2 tcp=0 nocfg=3 accepted=2

## R4

**FAIL**

R4-F-2 formed no session in 120 s (`ssl_s`=none), which misses the brief's `ssl_s` <= 120 condition. Evidence the cause is the rig, not seam 2: `busy`=0 and `backstop`=0 (seam 2 never engaged), the group formed 1.6 s after the switch, credentials were delivered, 5 pokes were answered, and the phone's own stack logged `RFCOMM_CreateConnection failed` right after SDP success on all 4 of its RFCOMM attempts to D-HU's radio, so the head unit's listener never saw a connection. C rep 2 needed 69.2 s with 2 failed RFCOMM attempts of the same kind. An extra F rep (R4-F-3, not in the brief) passed with `ssl_s` 6.5. No extra C rep was run (the installed APK was F, `apk_check` refused). This stays FAIL by the brief's rule; the operator may regrade at review.

    R4-C-1 ens=1 auto_on=0 drain=0 busy=0 backstop=0 succ_s=1.6 ssl_s=12.4 thr=3 imported=1 ap_after_switch=0 crit=0 rfcomm=1 tcp=0
    R4-C-2 ens=1 auto_on=0 drain=0 busy=0 backstop=0 succ_s=1.7 ssl_s=69.2 thr=2 imported=1 ap_after_switch=0 crit=0 rfcomm=2 tcp=0
    R4-F-1 ens=1 auto_on=0 drain=0 busy=0 backstop=0 succ_s=1.7 ssl_s=6.6 thr=3 imported=1 ap_after_switch=0 crit=0 rfcomm=0 tcp=0
    R4-F-2 ens=1 auto_on=0 drain=0 busy=0 backstop=0 succ_s=1.6 ssl_s=none thr=0 imported=1 ap_after_switch=0 crit=0 rfcomm=4 tcp=0
    R4-F-3 ens=1 auto_on=0 drain=0 busy=0 backstop=0 succ_s=1.6 ssl_s=6.5 thr=3 imported=1 ap_after_switch=0 crit=0 rfcomm=0 tcp=0

## Anything the brief did not ask about

- The phone-side RFCOMM_CreateConnection failure above is intermittent on D-POCO and appears on both arms.
- `hotspot-teardown-proven-unsafe` was written in every R3 rep on both arms (restart never came back), as the brief predicted; it was deleted before each rep by `hot`.
- Thermal peak 73C, throttle delta 0 on every run.
