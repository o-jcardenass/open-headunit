# bt-helper, round 4 results

- Candidate: no app build. rig-lib `0f6b5a7a1`, helper `ohu.rig.bthelper` built on the PC by `bt-helper/build.sh` (apk md5 `9fc23be0268e0e637e24a555fe8008fa`).
- Baseline: none.
- APK md5s: none (no app APK built or installed).
- Units: D-POCO (phone, POCO X3 NFC), D-SAM (head unit role, below API 29). Date: 2026-10-30.
- Operator interventions: 0 beyond the opening yes (the plan message had no B or C items).
- Round verdict: PASS (setup, L, CPS, restore).

## Setup notes

- Pre-flight: OK. D_SAM adb ok, WiFi 1, BT 1, screen Asleep; D_POCO adb ok, WiFi 1, BT 1, screen Awake. No BT profiles on either.
- Session: one `rig_session.py run`, order from `rig_requires.py order --live` (one brief). No build, no install by the host.
- Clocks (`clock-offsets.txt`): D-POCO offset_ms=-327 action=none; D-SAM offset_ms=-26742 action=left (no root shell). The timestamps quoted below are written by the PC-side scripts, so they need no D-SAM correction.
- Monitor: the first status monitor matched the word OPERATOR inside the REQUIREMENTS line and ended at once. It was re-armed with an anchored pattern. No effect on the session.
- Bond as found: `POCO-SAM no` (`bonds-start.txt`), so CPS opened with its pre-pair step, as the brief describes.
- Device files: 0 copied and removed, 0 left (`device-files.txt`).
- Quirk files read: none beyond the brief; the session ran from its grade scripts.

## Rsetup

**PASS**

4/4 conditions: helper APK built, both units read their own address (D-SAM `E4:58:E7:0E:5E:1D`, D-POCO `DC:B7:2E:5E:4E:59`), bond read as no, both bonded lists read. Graded 17:55:06.

## RL

**PASS**

2/2 conditions: `list` through the helper worked on both units, 0 helper failures. Graded 17:55:11.

## RCPS

**PASS**

4/4 conditions, graded 17:56:51. The cycle log (`CPS-attempts.txt`):

```
POCO-SAM-prepair PAIR_OK paired through the API in 1 taps
POCO-SAM-forget1 FORGET_OK forgot 'Navegadortz3' (E4:58:E7:0E:5E:1D) through the API
POCO-SAM-pair1 PAIR_OK paired through the API in 1 taps
POCO-SAM-forget2 FORGET_OK forgot 'Navegadortz3' (E4:58:E7:0E:5E:1D) through the API
POCO-SAM-pair2 PAIR_OK paired through the API in 1 taps
```

| Step | Time | Result |
|---|---|---|
| prepair | 17:55:29 tap 1, 17:55:35 | paired verified_by=api taps=1 |
| forget 1 | 17:55:39 | forgotten verified_by=api taps=0 |
| pair 1 | 17:56:06 tap 1, 17:56:12 | paired verified_by=api taps=1 |
| forget 2 | 17:56:17 | forgotten verified_by=api taps=0 |
| pair 2 | 17:56:43 tap 1, 17:56:50 | paired verified_by=api taps=1 |

The tap on D-SAM was `Pair on the target (below API 29) at 840,509`, the OK-named dialog button that round 3 never tapped. Forgets: 2 of 2 through the API; pairs: 2 of 2 through the API with 1 tap each; stops: 0. The round-3 failure is fixed by `0f6b5a7a1`.

## Rrestore

**PASS**

1/1 condition: `bonds-end.txt` reads `POCO-SAM want=yes now=yes`. D-POCO and D-SAM are left bonded. Graded 17:57:04.

## Anything the brief did not ask about

- The whole session took 2 min 19 s (17:54:47 to 17:57:06).
- Each pair cycle took about 6 to 7 s from the tap to the API confirming the bond.
- Evidence: `bt-helper-round4-captures.zip` on release `rig-evidence-bt-helper`, sha256 `7b40601b4d7e85045324e6309a1769b3984cc1e041fd160a8cb3158d491bb179`.
