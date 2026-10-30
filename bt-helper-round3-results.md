# bt-helper, round 3 results

- Candidate: no app build. rig-lib `d1c40b502`, helper `ohu.rig.bthelper` built on the PC by `bt-helper/build.sh`.
- Baseline: none.
- APK md5s: none (no app APK built or installed).
- Units: D-HU, D-POCO, D-MOTO, D-SAM. Date: 2026-10-30.
- Operator interventions: 1 (the opening yes; C1 D-MOTO unlocks without a PIN).
- Round verdict: FAIL (CPS and restore), both points of the round PASS.

## Setup notes

- Pre-flight: OK. D-HU, D-SAM, D-POCO and D-MOTO had adb up and WiFi and Bluetooth on.
- Quirk files read: `topics/bt.md`, `units/D-MOTO.md`. The brief also names `topics/tooling.md`, `units/D-POCO.md`, `units/D-HU.md` and `units/D-SAM.md`; those were not read before the run.
- The first status monitor exited early because its `DONE` pattern matched the words "until DONE" in the REQUIREMENTS line. It was replaced; the session itself was unaffected.
- Clocks: D-HU +750 ms, D-MOTO -500 ms, D-POCO -325 ms, D-SAM -26734 ms (left, no root shell). Subtract D-SAM's offset from its timestamps.
- Device files: 0 copied and removed, 0 left.
- D-SAM's one tap was not requested: the session printed no OPERATOR line. The Settings fallback ran on D-POCO, not D-SAM.

## Rsetup

**PASS**

4/4 conditions: helper APK built, 4 addresses read, 3 bonds read as yes or no, 0 unreadable lists.

## RL

**PASS**

2/2: the helper listed bonds on all four units (4 eq 4), 0 helper failures.

## RCPH

**PASS**

3/3 (D-POCO and D-HU): 2 of 2 forgets through the API, 2 of 2 pairs through the API with 0 taps, 0 stops.

## RCMH

**PASS**

3/3 (D-MOTO and D-HU): 2 of 2 forgets through the API, 2 of 2 pairs through the API with 0 taps, 0 stops.

## RCPS

**FAIL**

COND 1 PASS: forget through the API, 1 of 1 (`POCO-SAM-forget1 FORGET_OK forgot 'Navegadortz3' (E4:58:E7:0E:5E:1D) through the API`).
COND 2 FAIL: pair through the API with 1 tap, 0 of 1. COND 3 FAIL: 1 stop.

`pair/.../steps.log` for D-POCO to D-SAM, in D-POCO time:
```
17:24:06 api pair: initiator FAIL not bonded after 45000 ms; target screen
17:24:22 the API did not pair; trying the Settings screens
17:24:36 tap 1 pair new device at 389,1182 on 4f4027e9
17:25:57 STOP scan: 0 rows read 'Navegadortz3' (need exactly 1)
```
The API pair from D-POCO did not bond D-SAM in 45 s, and the Settings fallback then found 0 rows named `Navegadortz3` in its scan. Why D-SAM was not found is unexplained: this round holds no reading of D-SAM's discoverability, and the round 2 half-bond path on D-SAM was not exercised the same way.

## Rrestore

**FAIL**

1 condition, FAIL: bonds back as found, 2 of 3. `POCO-DHU` and `MOTO-DHU` were already paired. `POCO-SAM-restore PAIR_STOPPED scan: 0 rows read 'Navegadortz3' (need exactly 1)`.
`bonds-end.txt`: `POCO-SAM want=yes now=no`. The D-POCO and D-SAM bond is left removed.

## Anything the brief did not ask about

- The two rows that passed show the helper working through the API on D-HU, D-POCO and D-MOTO with no tap, so `--no-hidden-api-checks` lets `removeBond()` through on all three.
- CPH took 30 s and CMH 30 s for 2 cycles each, against 6 min in the brief's table.
- The D-POCO bond to D-SAM needs re-pairing before the next round that uses it; `check --fix` will print an OPERATOR line for it.

Evidence: release `rig-evidence-bt-helper`, asset `bt-helper-round3-captures.zip`, sha256 `ea9d0655c99747d0ae016c4d30836add4e4ea142a12df459ea26a5e57f07a47a`.
Closing pre-flight: OK. D-HU, D-SAM, D-POCO and D-MOTO adb ok, WiFi and BT on; `rig_cleanup.sh list` clean, lock free; baseline radios read back OK.
