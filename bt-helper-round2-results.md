# bt-helper round 2 results

- Candidate: no app build; rig-lib pinned at `bf0df1ecd` (helper APK built on the PC in `setup`, md5 `a210ed6b53b259e11054a78e61bd32a9`)
- Baseline: none (tooling round)
- Units: D-HU (API 34), D-POCO (API 35), D-MOTO (API 34), D-SAM (API 19)
- Date: 2026-10-30
- Operator interventions: 1 (the opening yes to "D-MOTO unlocks without a PIN"); no OPERATOR line was raised
- Round verdict: **FAIL** (5 of 6 runs FAIL; the helper never ran on D-HU, D-POCO or D-MOTO)

## Setup notes

- The session ran `check --fix`, then every run through `rig_run.sh`. Pre-flight before the session: all four units adb ok, WiFi 1, BT 1, screens awake, no stray logcat, no rig lock.
- Quirk files and the template were skimmed rather than read in full (the host read the trimmed template output only in part); grading rests on the session's COND lines and the greps below.
- Clock offsets: D-HU +0.7 s, D-MOTO -0.5 s, D-POCO -0.3 s, D-SAM -26.7 s (left, no root shell). Every verdict here is per unit; none compares timestamps across units.
- The local checkout held an untracked, divergent `rig-lib/` that blocked the branch checkout; it was moved to `rig-data/scratch/rig-lib-local-20261030/` at the operator's request.
- **Observed, cause unexplained:** `rig_bt.py ensure` returns False ("the helper did not build or install") on D-HU, D-POCO and D-MOTO. After the round, those three units all hold a helper APK with md5 `6d609496a860463a20c3a3efc6b5f137`; D-SAM holds `eebdbcb395ed268ef263689799793248`; the APK built in `setup` is `a210ed6b53b259e11054a78e61bd32a9`. So no unit holds the build `setup` made. `ensure` discards the output of `adb install -r -t`, so the reason (a rejected install, or a build whose md5 differs per run) is not in any log. Not tested by hand after DONE, to avoid changing the units.
- The helper does work when called later: `rig_bt.py bonds` listed bonds on D-MOTO, D-SAM, D-HU and D-POCO after the round. Only `ensure`'s md5 comparison fails.

## Rsetup

**PASS**

- 4/4 conditions: APK built, 4 addresses read, 3 bonds read as yes or no, 0 unreadable lists.
- Bonds as found by name: POCO-DHU no, MOTO-DHU yes, POCO-SAM no.

## RL

**FAIL**

- COND 1 FAIL helper listed bonds on all four units: 1 eq 4. COND 2 FAIL helper failures: 3 eq 0.
- `L-attempts.txt`: `DHU FAIL the helper did not build or install`, same for POCO and MOTO. D-SAM printed `SAM LIST E4:58:E7:0E:5E:1D 2 | DC:B7:2E:5E:4E:59 POCO X3 NFC | A0:46:5A:97:E4:95 motorola edge 30 neo`.
- D-SAM, the unit the brief worried about, listed through the helper.

## RCPH (point of the round)

**FAIL**

- COND 1 FAIL forgets through the API: 0 of 2. COND 2 FAIL pairs through the API with 0 taps: 0 of 2. COND 3 FAIL stops: 2 eq 0.
- Both forgets: `RESULT not bonded` (no bond to forget on D-POCO, 0 bonds listed).
- Both pairs fell back to the Settings screen: `16:48:15 tap 1 pair new device`, `16:48:21 tap 2 row 'Navegadortz2'`, then `16:49:16 STOP pairing dialogs: initiator none of [...] on screen, target none of [...] on screen`. Second cycle the same, `16:50:33 STOP`.
- A fallback to the Settings screens is a FAIL of the helper by the brief's rule, so the API path (`removeBond` through `--no-hidden-api-checks`) was never exercised on D-POCO or D-HU.

## RCMH (point of the round)

**FAIL**

- COND 1 FAIL forgets through the API: 0 of 2. COND 2 FAIL pairs through the API with 0 taps: 0 of 2. COND 3 FAIL stops: 1 eq 0.
- Cycle 1 ran entirely on screen taps: forget `16:50:46 tap 2 Forget` ... `16:50:53 RESULT forgotten verified_by=address taps=3`; pair `16:51:46 RESULT paired codes=681872/681872 taps=4`.
- Cycle 2 stopped: `16:51:54 STOP 0 rows read 'Navegadortz2' on the Bluetooth screen (need exactly 1) (screen locked, or the row is below the fold)`.

## RCPS

**FAIL**

- COND 1 FAIL forgets through the API: 0 of 1. COND 2 FAIL pairs with 1 tap: 0 of 1. COND 3 FAIL stops: 2 eq 0.
- The D-SAM half-bond was removed through the API: `16:52:16 half bond on 30041c35642d2200: forgot 'POCO X3 NFC' (DC:B7:2E:5E:4E:59) through the API`. This answers one of the brief's two open questions: `removeBond()` works on D-SAM under the helper's own `BLUETOOTH_ADMIN`.
- Both pairing attempts then stopped at the scan: `16:53:50 STOP scan: 0 rows read 'Navegadortz3' (need exactly 1)` and `16:55:33` the same. No tap was asked of the operator.

## Rrestore

**FAIL**

- COND 1 FAIL every bond is back as setup found it: 1 eq 3.
- `bonds-end.txt` has one line, `POCO-DHU want=no now=no`; the restore wrote no line for MOTO-DHU or POCO-SAM, so the grade cannot pass. Why those two lines are absent is unexplained.
- Read-only check after DONE through the helper: D-MOTO lists `Navegadortz2` (D-HU) as bonded and D-HU lists D-MOTO, so MOTO-DHU is bonded as found. D-POCO lists neither D-HU nor D-SAM, as found. D-SAM no longer lists D-POCO (its half-bond was forgotten in CPS; at `L` it did list `POCO X3 NFC`).

## Anything the brief did not ask about

- Open question 2 (does `removeBond()` work on D-SAM under `BLUETOOTH_ADMIN`): yes, in `pair/4f4027e9-30041c35642d2200-1/steps.log`. Open question 1 (does `--no-hidden-api-checks` let `removeBond()` through on the API 34 and 35 units): not answered, since the helper never got that far there.
- `bonds-start.txt` says POCO-SAM no while D-SAM's own list held D-POCO at `L`: a half bond on D-SAM's side only. The pair tools handled it.
- Captures: `bt-helper-round2-captures.zip` (rig-lib excluded), to be uploaded as an asset `bt-helper-round2-captures.zip` of release `rig-evidence-bt-helper`, sha256 `b61a0f45acb73af2d919537b2399fff59d7c28549f827d9e6b1919bd73768e9b`.
