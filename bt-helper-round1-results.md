# bt-helper round 1 results

- Candidate: no app build; rig-lib pinned at `49bb0e945` (helper APK built on the PC in `setup`)
- Baseline: none (tooling round)
- APK md5s: none installed by the host
- Units: D-HU, D-POCO, D-MOTO, D-SAM
- Date: 2026-10-30
- Operator interventions: 0 (the opening yes and done only)

## Setup notes

- The helper build needed `javac`; the PATH JDK has none, so the session ran with `/opt/android-studio/jbr/bin` on PATH.
- D-SAM: the operator said it charges through the PC's USB and reads 100%; the brief says its USB port does not charge it. Answered done, run went on.
- Clock offsets: D-HU +748 ms, D-MOTO -492 ms, D-POCO -320 ms, D-SAM -26705 ms (left, no root shell). All runs are graded per unit, none compares across units.
- Cause of the four FAILs, from the logs: CPH, CMH and restore each end in `ModuleNotFoundError: No module named 'rig_bt'` at `rig_forget.py` line 144 (`import rig_bt` inside `Forget.__init__`). The scripts call it as `python3 -I rig_forget.py`, and `-I` leaves the script folder off `sys.path`. `rig_pair.py` line 27 inserts its own folder; `rig_forget.py` has no `sys.path` line, at the pin and at the branch tip. This is a rig-lib defect, not an app one.
- Because of that crash no forget ran in any run, so the units' bonds were never changed: `names-start.txt` and `names-end.txt` are identical.
- Not explained: the pairing steps' own stops (CPH `POCO-DHU-prepair PAIR_STOPPED`, no pairing dialog on either screen; CPS `POCO-SAM-prepair PAIR_STOPPED`, 0 scan rows read "Navegadortz3"). They may be behind the same crash or separate; I did not investigate.
- Quirk files and `TESTING-TEMPLATE.md` sections were not read in full this session; the grading rests on the session's COND lines and the three logs above.

## Rig state

Bonds as found: POCO-DHU no, MOTO-DHU yes, POCO-SAM no. Unchanged at the end by name lists. Restore itself failed (its forget crashed the same way) but had nothing to restore.

## Rsetup
**PASS**

3/3 conditions: helper APK built, 4 of 4 addresses read, 3 of 3 bonds read.

## RL
**PASS**

3/3: the helper listed bonds on D-HU, D-POCO and D-MOTO (3 eq 3); D-SAM refused as below API 29 (1 eq 1); helper failures 0.

## RCPH
**FAIL**

Forgets through the API 0 of 2; pairs with 0 taps 0 of 2; one `PAIR_STOPPED` (no pairing dialog on either screen). Log shows the `rig_bt` import crash.

## RCMH
**FAIL**

Forgets 0 of 2; pairs 0 of 2; no stops. Same `rig_bt` import crash.

## RCPS
**FAIL**

Forgets 0 of 1; pairs 0 of 1; `POCO-SAM-prepair PAIR_STOPPED`, 0 rows read "Navegadortz3".

## Rrestore
**FAIL**

Bonds back as setup found: 1 eq 3. Same import crash; the name lists show no bond was changed.

## Round verdict: FAIL

Neither point run (CPH, CMH) reached the helper. The Bluetooth helper's forget and pair through the API are untested by this round.

## Anything the brief did not ask about

The fix is one line in `rig-lib/rig_forget.py` (insert its own folder into `sys.path` as `rig_pair.py` does). I did not make it: the round's scope is to run the pinned lib. The next round needs that fix and a new lib pin.

## Evidence

Release asset `rig-evidence-bt-helper`, file `bt-helper-round1-captures.zip`, sha256 `c0d3c71482ee8bb20a7b5314c16b0a2b28b597f2c06e5a03f4f7bfb8c45da25a`.

## Closing pre-flight

```
ROLE     ADB      WIFI   BT     SCREEN    BT-PROFILES
D_HU     ok       1      1      Awake     HFP:none A2DP:none
D_POCO   ok       1      1      Awake     HFP:none A2DP:none
D_MOTO   ok       1      1      Awake     HFP:none A2DP:none
D_SAM    ok       1      1      Awake     HFP:none A2DP:none
PREFLIGHT OK
```
