# bt-helper, round 4 brief: does the helper pair D-POCO with D-SAM after the OK-button fix

Published name: `bt-helper-round4-brief.md`. Results go in `bt-helper-round4-results.md`.
Evidence: add the asset `bt-helper-round4-captures.zip` to the release `rig-evidence-bt-helper`.

**One point.** Round 3 passed CPH and CMH: the helper forgets and pairs through the Bluetooth API with no tap on D-HU,
D-POCO and D-MOTO. CPS and restore failed on the D-POCO and D-SAM pair. The screen dumps show why: D-POCO's request
reached D-SAM, and D-SAM showed "Confirm passkey is 811995 to pair with POCO X3 NFC" with the buttons **Cancel** and
**OK**. `rig_pair.py` looked only for a button named Pair, so it never tapped, and the request timed out after 45 s.
rig-lib `0f6b5a7a1` taps OK (or Aceptar) when the dialog has no Pair button and shows a 6-digit code. This round
measures that fix on D-POCO and D-SAM, and it leaves the two units bonded. No app build; the app is only stopped.

Units: D-POCO (phone, API 35, asks through the helper) and D-SAM (head unit, API 19, one tap on its dialog).
Time: setup 3 min, L 1, CPS 6, restore 3. **About 13 min.**

```rig-requires
lib 0f6b5a7a1
unit D-SAM role=headunit wifi=on bt=any
unit D-POCO role=phone wifi=on bt=on battery=30
run setup cap=420 script=setup.sh grade=grade-setup.sh
run L cap=300 script=l.sh grade=grade-l.sh
run CPS cap=900 script=cps.sh grade=grade-cps.sh point=yes
run restore cap=900 script=restore.sh grade=grade-restore.sh always=yes
stop setup FAIL,INCONCLUSIVE,UNTESTABLE skip=all
stop L FAIL,INCONCLUSIVE,UNTESTABLE skip=CPS
```

| Run | Units | What it measures | Min |
|---|---|---|---|
| setup | both | the helper built on the PC; the app stopped; names, addresses and the bond as found | 3 |
| L | D-POCO, D-SAM | `list` through the helper on both units | 1 |
| **CPS** | D-POCO and D-SAM | **a pair first when the bond is gone, then 2 cycles: forget on D-POCO through the API, pair through the API with one tap on D-SAM** | 6 |
| restore | D-POCO and D-SAM | the two units left bonded | 3 |

## 1. Build and baseline

No app build. The helper is built on the PC in `setup` by `bash "$RL_HOME/bt-helper/build.sh"`, with the same
signing key as round 3 (`~/.cache/rig-bt-helper/`), so round 3's helper stays installed unless its md5 differs. The
helper source did not change in `0f6b5a7a1`; only `rig_pair.py` did. The dev host ran
`bash rig-lib/tests/test_rig_lib.sh` at `0f6b5a7a1`: 96 passed, 0 failed, with the 31 simulated-device cases of
`tests/test_bt_ui.py`. Two cases are new: a Samsung 4.4 dialog whose button reads OK gets one tap, and an OK dialog with
no pairing code gets none. Against round 3's lib `d1c40b502`, the first of them fails. No rig unit tests.

## 2. What this is and why

Round 3's CPS `steps.log`, in D-POCO time:

```
17:24:06 api pair: initiator FAIL not bonded after 45000 ms; target screen
17:24:22 the API did not pair; trying the Settings screens
17:25:57 STOP scan: 0 rows read 'Navegadortz3' (need exactly 1)
```

`api-dialog-t-5.xml` on D-SAM holds `Bluetooth pairing request`, `Confirm passkey is 811995 to pair with POCO X3 NFC.`,
and two clickable buttons, `Cancel` and `OK`. No dump holds a node named Pair. So the request reached D-SAM and only the
tap was missing. The Settings fallback then could not find D-SAM in D-POCO's scan. Android 4.4 is discoverable only
while its "visible" switch is on, not while its Bluetooth screen is open, so that fallback cannot find D-SAM. This
round does not fix the fallback; a fallback is graded as a FAIL.

## 3. What is different about this round

- **Only D-POCO and D-SAM.** D-HU and D-MOTO are not touched. Their bonds were left as found in round 3.
- **The D-POCO and D-SAM bond is gone at the start** (round 3's `bonds-end.txt`: `POCO-SAM want=yes now=no`). CPS
  pairs first (`POCO-SAM-prepair`), and that pair is graded too. If the operator re-paired them since, there is no
  prepair line, and CPS still runs its 2 cycles.
- **restore always leaves the two units bonded**, whatever setup found, because round 3 left them unbonded by mistake.
- **D-SAM's screen must wake and show the dialog.** The tool sends `KEYCODE_WAKEUP` and opens Bluetooth settings on
  D-SAM before it asks; in round 3 the dialog came up on the launcher and stayed for about 45 s.
- **The app is stopped on both units for the whole round**, so no wake poke or auto-start reacts to a pairing.
- Read `rig-quirks/topics/tooling.md`, `topics/bt.md`, `units/D-POCO.md` and `units/D-SAM.md`.

## 4. Settings keys

None. The app is only stopped.

## 5. The deciding lines

The tools print one line per attempt into `$EV/<run>-attempts.txt`, prefixed with a label. A helper line from
`rig_bt.py` starts with its result word.

| Id | Line | File | Read |
|---|---|---|---|
| T-LIST | `LIST ` | `L-attempts.txt` | the helper listed the bonds through the API |
| T-UNR | `UNREADABLE the bonded list` | `names-start.txt` | a unit whose bonded list no route could read |
| T-FAPI | `FORGET_OK forgot ` and ` through the API` | `CPS-attempts.txt` | a forget through the API, read back gone |
| T-P1 | `PAIR_OK paired through the API in 1 taps` | `CPS-attempts.txt` | a pair through the API with one tap on D-SAM |
| T-STOP | `_STOPPED ` | `<run>-attempts.txt` | a route that did not finish |
| T-TAP | `Pair on the target (below API 29)` | `pair/*/steps.log` | the tap on D-SAM's dialog |

## 6. Runs

### setup. Helper built, apps stopped, names and the bond as found

**setup.sh**
```bash
rl_step "build the helper on the PC"
bash "$LIBDIR/bt-helper/build.sh" > "$EV/setup-build.txt" 2>&1
rl_step "stop the app on both units"
for s in "$SAM" "$POCO"; do adb -s "$s" shell am force-stop "$PKG"; done
rl_step "names, addresses, bond"
names > "$EV/names-start.txt"
echo "POCO-SAM $(bstate "$POCO" "$SAM")" >> "$EV/bonds-start.txt"
```

**grade-setup.sh**
```bash
rl_cond 1 ge "$(grep -c 'rig-bt-helper.apk$' "$EV/setup-build.txt")" 1 'the helper APK was built'  # not-literal: the build's last line
rl_cond 2 eq "$(grep -c '^addr=..:..:..:..:..:..$' "$EV/names-start.txt")" 2 'both units read their own address'  # not-literal
rl_cond 3 eq "$(grep -cE '^POCO-SAM (yes|no)$' "$EV/bonds-start.txt")" 1 'the bond reads as yes or no'  # not-literal
rl_cond 4 eq "$(grep -c '^UNREADABLE' "$EV/names-start.txt")" 0 'both units read their bonded list'  # not-literal
```

- **PASS** when all four hold. Any other verdict stops the round, and restore still runs.

### L. The helper lists bonds on both units

**l.sh**
```bash
for u in POCO SAM; do
  echo "$u $(python3 -I "$LIBDIR/rig_bt.py" list "$(serial_of "$u")")" >> "$EV/L-attempts.txt"
done
```

**grade-l.sh**
```bash
A="$EV/L-attempts.txt"
rl_cond 1 eq "$(grep -cE '^(POCO|SAM) LIST ..:..:..:..:..:.. ' "$A")" 2 'the helper listed bonds on both units'  # not-literal
rl_cond 2 eq "$(grep -cE '^(POCO|SAM) FAIL' "$A")" 0 "helper failures ($(grep -m1 -E '^[A-Z]+ FAIL' "$A" | cut -c1-140))"  # not-literal
```

- **PASS** when both hold. **FAIL** when the helper fails on a unit: the line names the cause.

### CPS. D-POCO and D-SAM, a pair if needed, then 2 cycles (the point)

**cps.sh**
```bash
cycles POCO SAM 2
```

**grade-cps.sh**
```bash
A="$EV/CPS-attempts.txt"
api_grade CPS 2 1
rl_cond 4 eq "$(grep -a -- '-prepair ' "$A" | grep -acvF 'paired through the API in 1 taps')" 0 "prepair not through the API with 1 tap ($(grep -a -m1 -- '-prepair ' "$A" | cut -c1-140))"  # not-literal
```

- **PASS** when the prepair (if any) and both pairs went through the API with one tap on D-SAM, both forgets went
  through the API, and nothing stopped. **FAIL** otherwise; the stop line or the `steps.log` that fell back to the
  Settings screens names the cause. Any half bond on D-SAM is forgotten by the helper on D-SAM first
  (`half bond on <D-SAM>:` in the pair's `steps.log`); report its route, it is not graded.

### restore. The two units left bonded (always runs)

**restore.sh**
```bash
fail=0
[ "$(bstate "$POCO" "$SAM")" = yes ] || pr POCO-SAM-restore "$POCO" "$SAM" || fail=1
echo "POCO-SAM want=yes now=$(bstate "$POCO" "$SAM")" >> "$EV/bonds-end.txt"
names > "$EV/names-end.txt"
for s in "$SAM" "$POCO"; do adb -s "$s" shell input keyevent KEYCODE_HOME; done
exit "$fail"
```

**grade-restore.sh**
```bash
rl_cond 1 eq "$(grep -c '^POCO-SAM want=yes now=yes$' "$EV/bonds-end.txt")" 1 'D-POCO and D-SAM are bonded'  # not-literal
```

- **PASS** when D-POCO lists D-SAM at the end. A FAIL means the next round's `check --fix` asks the operator.

## 7. Scripts

**lib.sh**
```bash
# Serials and the forget and pair wrappers for bt-helper round 4. Sourced before every script and grade.
source /home/oscar/Coding/StudioProjects/rig-toolkit/rig_devices.sh
PKG=com.andrerinas.headunitrevived
SAM=$D_SAM; POCO=$D_POCO
SCR=$EV/scripts; LIBDIR=${RL_HOME:-.}

serial_of() { eval "echo \$$1"; }   # serial_of POCO: the serial of that short name
names() {   # each unit's own name and address, then its bonded list (address and the name it shows)
  local u s
  for u in SAM POCO; do
    s=$(serial_of "$u")
    echo "== $u $s"
    echo "name=$(adb -s "$s" shell settings get secure bluetooth_name | tr -d '\r')"
    echo "addr=$(python3 -I "$SCR/own_addr.py" "$LIBDIR" "$s")"
    python3 -I "$LIBDIR/rig_forget.py" names "$s"
  done
}
bstate() { python3 -I "$SCR/bstate.py" "$LIBDIR" "$1" "$2"; }   # bstate A B: yes when A lists B's address
fg() {   # fg LABEL A B: forget B on A (the API first, Settings after); one line to $EV/$RUN-attempts.txt
  local out rc
  out=$(python3 -I "$LIBDIR/rig_forget.py" forget --ev "$EV" "$2" "$3"); rc=$?
  echo "$1 $out" >> "$EV/$RUN-attempts.txt"; return "$rc"
}
pr() {   # pr LABEL A B: pair A (asks) with B (accepts); one line to $EV/$RUN-attempts.txt
  local out rc
  out=$(python3 -I "$LIBDIR/rig_pair.py" pair --ev "$EV" "$2" "$3"); rc=$?
  echo "$1 $out" >> "$EV/$RUN-attempts.txt"; return "$rc"
}
cycles() {   # cycles PHONE HU N: make sure the two are paired, then N times a forget on the phone and a pair
  local a b n
  a=$(serial_of "$1"); b=$(serial_of "$2")
  [ "$(bstate "$a" "$b")" = yes ] || pr "$1-$2-prepair" "$a" "$b" || return 1
  for n in $(seq 1 "$3"); do
    rl_step "$1-$2 cycle $n"
    fg "$1-$2-forget$n" "$a" "$b" || break
    pr "$1-$2-pair$n" "$a" "$b" || break
  done
}
api_grade() {   # api_grade RUN N TAPS: N forgets and N pairs through the API, each pair with TAPS taps, no stop
  local A="$EV/$1-attempts.txt"
  rl_cond 1 eq "$(grep -a -- '-forget[0-9]* FORGET_OK forgot ' "$A" | grep -ac ' through the API')" "$2" "forgets through the API, of $2"  # not-literal: a tool line
  rl_cond 2 eq "$(grep -a -- '-pair[0-9]* PAIR_OK' "$A" | grep -acF "paired through the API in $3 taps")" "$2" "pairs through the API with $3 taps, of $2"  # not-literal
  rl_cond 3 eq "$(grep -ac '_STOPPED ' "$A")" 0 "stops ($(grep -a -m1 '_STOPPED ' "$A" | cut -c1-140))"  # not-literal
}
```

**own_addr.py**
```python
# own_addr.py LIBDIR SERIAL: the unit's own Bluetooth address, the way rig_forget.py reads it, or "none".
import sys
sys.path.insert(0, sys.argv[1])
from rig_forget import own_address, run_adb
s = sys.argv[2]
print(own_address(run_adb(s, "shell", "settings", "get", "secure", "bluetooth_address"),
                  run_adb(s, "shell", "dumpsys", "bluetooth_manager")) or "none")
```

**bstate.py**
```python
# bstate.py LIBDIR A B: "yes" when A's bonded list holds B's own address, "no" when not, "unknown" when unreadable.
import sys
sys.path.insert(0, sys.argv[1])
import rig_bt
from rig_forget import own_address, run_adb
a, b = sys.argv[2], sys.argv[3]
addr = own_address(run_adb(b, "shell", "settings", "get", "secure", "bluetooth_address"),
                   run_adb(b, "shell", "dumpsys", "bluetooth_manager"))
listed = rig_bt.bonds(a)
if not addr or listed is None:
    print("unknown"); sys.exit(0)
print("yes" if any(x == addr for x, _ in listed) else "no")
```

## 8. Do not re-run

CPH and CMH (round 3, both PASS): the helper forgets and pairs with no tap on D-HU, D-POCO and D-MOTO. The accept on
D-HU (round 1) and `removeBond()` on D-SAM (round 2) are settled.

## 9. Report back

1. CPS: forgets and pairs through the API of each total, and the prepair line if there is one. For each stop or
   fallback, its line and the `api pair:` line from its `steps.log`.
2. Whether each `steps.log` holds `Pair on the target (below API 29)`, and how D-SAM's own half bond was forgotten if
   there was one.
3. restore: `bonds-end.txt`, and `names-start.txt` against `names-end.txt`.
