# bt-helper, round 3 brief: does the Bluetooth helper forget and pair on the rig's units

Published name: `bt-helper-round3-brief.md`. Results go in `bt-helper-round3-results.md`.
Evidence: create the release `rig-evidence-bt-helper` with the asset `bt-helper-round3-captures.zip`.

**One point.** `rig-lib/bt-helper` is an instrumentation APK that borrows adb's shell permissions
(`BLUETOOTH_PRIVILEGED`, `BLUETOOTH_CONNECT`, measured on D-HU, D-POCO and D-MOTO in
`shell-bt-permission-check-results.md`) and removes, asks for and accepts a bond through the Bluetooth API.
`rig_forget.py` and `rig_pair.py` try it first and fall back to the Settings screens. Rounds 1 and 2 did not reach it
on the API 29+ units, for rig-lib defects fixed in this lib. In round 2, `build.sh` kept its signing key in `build/`,
and each round copies a fresh rig-lib. So the new helper had a new key, and `install -r` could not replace round 1's
helper on D-HU, D-POCO and D-MOTO. Also, `adb` inside restore's `while read` loop ate the loop's input. This round
measures the helper on real units: 4 forget and pair cycles with no taps, 1 cycle with D-SAM that takes one tap, and
the bonds put back as found. No app build; the app is only stopped.

Units: D-POCO and D-MOTO (phones, they ask), D-HU (head unit, accepts through the helper), D-SAM (head unit, API 19:
the helper lists and removes its bonds; it accepts a pairing with one tap).
Time: setup 4 min, L 2, CPH 6, CMH 6, CPS 5, restore 4. **About 30 min.**

```rig-requires
lib d1c40b502
unit D-HU role=headunit wifi=on bt=on
unit D-SAM role=headunit wifi=on bt=any
unit D-POCO role=phone wifi=on bt=on battery=30
unit D-MOTO role=phone wifi=on bt=on battery=30
confirm D-MOTO unlocks without a PIN (D-SAM's one tap and every fallback need an unlocked screen)
run setup cap=420 script=setup.sh grade=grade-setup.sh
run L cap=300 script=l.sh grade=grade-l.sh
run CPH cap=900 script=cph.sh grade=grade-cph.sh point=yes
run CMH cap=900 script=cmh.sh grade=grade-cmh.sh point=yes
run CPS cap=900 script=cps.sh grade=grade-cps.sh
run restore cap=900 script=restore.sh grade=grade-restore.sh always=yes
stop setup FAIL,INCONCLUSIVE,UNTESTABLE skip=all
stop L FAIL,INCONCLUSIVE,UNTESTABLE skip=CPH,CMH,CPS
```

| Run | Units | What it measures | Min |
|---|---|---|---|
| setup | all four | the helper built on the PC; the app stopped everywhere; names, addresses and bonds as found | 4 |
| L | D-HU, D-POCO, D-MOTO, D-SAM | `list` through the helper on all four units, D-SAM at API 19 included | 2 |
| **CPH** | D-POCO and D-HU | **2 cycles: forget through the API, pair through the API on both sides, no tap** | 6 |
| **CMH** | D-MOTO and D-HU | **2 cycles, as CPH** | 6 |
| CPS | D-POCO and D-SAM | 1 cycle: forget on D-POCO through the API, pair asked through the API, one tap on D-SAM | 5 |
| restore | the three pairs | each bond back to what setup found | 4 |

## 1. Build and baseline

No app build. The helper is built on the PC in `setup` by `bash "$RL_HOME/bt-helper/build.sh"` (javac, d8, aapt2,
zipalign and apksigner from the rig's SDK, no Gradle). `build.sh` finds Android Studio's JDK by itself and keeps its
signing key in `~/.cache/rig-bt-helper/`, so every round signs with the same key. `rig_bt.py` installs the helper on a
unit when the unit lacks it or holds another build (compared by md5 of a pulled copy). When `install -r` cannot
replace a helper signed by another key, it uninstalls that helper and installs again, and it prints the install
output to stderr when it still fails. The dev host ran `bash rig-lib/tests/test_rig_lib.sh` at `d1c40b502`: 96 passed,
0 failed, with the 29 simulated-device cases of `tests/test_bt_ui.py`. Run against round 2's lib `bf0df1ecd`, 3 of
the new cases fail. No rig unit tests.

## 2. What this is and why

Every forget and pair on the rig was the operator's, and the Settings-screen flow that would script it failed in 5
earlier rounds (`could not get idle state`, dumps during animations). adb's shell user holds the Bluetooth system
permissions on the three new units, so an instrumentation can call the API instead:

- `unpair`: `removeBond()` (hidden, so `am instrument --no-hidden-api-checks`), then a wait until the bond is gone.
- `pair`: `createBond(TRANSPORT_BREDR)`, and the helper accepts its own side's request.
- `accept`: on the far side, the helper accepts the request from that address only, then waits for the bond.

Two answers are in. D-HU's stack lets the helper accept (round 1, `ACCEPT_OK bonded`). `removeBond()` works on D-SAM
under the helper's own `BLUETOOTH_ADMIN` (round 2, `half bond on <D-SAM>: forgot 'POCO X3 NFC' ... through the API`).
One thing only hardware answers now: whether `--no-hidden-api-checks` lets `removeBond()` through on D-HU, D-POCO and
D-MOTO. A FAIL there is the finding this round exists for.

## 3. What is different about this round

- **The app is stopped on all four units for the whole round**, so no wake poke or auto-start reacts to a pairing.
- **The helper stays installed** (`ohu.rig.bthelper`) on all four units. It runs only when `am instrument` starts it.
  D-HU, D-POCO and D-MOTO still hold round 1's helper, signed with another key. Expect `rig_bt.py` to uninstall it
  in setup and install this round's build.
- **When L fails, CPH, CMH and CPS are skipped**: without the helper they only measure the Settings fallback.
- **Bonded lists come from the helper**, not from dumpsys (`rig_bt.py bonds`). An unreadable list prints `unknown`
  in `bonds-*.txt` and `UNREADABLE` in `names-*.txt`, and a tool stops rather than act on it. On API 35 an empty dumpsys
  section no longer reads as "no bonds": in round 2 that is how D-POCO's list read while its helper did not install.
- **setup's first bond read installs the new helper** on each unit. If it cannot, that unit's lines read `unknown` or
  `UNREADABLE`, setup FAILs, and the round stops with no bond changed. The install's output is in `setup-*.log`.
- **A fallback to the Settings screens is graded as a FAIL of the helper**, even when the fallback succeeds. The tools
  log which route they took (`verified_by=api` in each `steps.log`).
- **restore always runs.** A bond it cannot put back prints an OPERATOR line from `check --fix` at the next round.
- Read `rig-quirks/topics/tooling.md`, `topics/bt.md`, `units/D-POCO.md`, `units/D-MOTO.md`, `units/D-HU.md`,
  `units/D-SAM.md`.

## 4. Settings keys

None. The app is only stopped.

## 5. The deciding lines

The tools print one line per attempt into `$EV/<run>-attempts.txt`, prefixed with a label. A helper line from
`rig_bt.py` starts with its result word.

| Id | Line | File | Read |
|---|---|---|---|
| T-LIST | `LIST ` | `L-attempts.txt` | the helper listed the bonds through the API |
| T-UNR | `UNREADABLE the bonded list` | `names-start.txt` | a unit whose bonded list no route could read |
| T-FAPI | `FORGET_OK forgot ` and ` through the API` | `<run>-attempts.txt` | a forget through the API, read back gone |
| T-PAPI | `PAIR_OK paired through the API in 0 taps` | `<run>-attempts.txt` | a pair through the API on both sides |
| T-P1 | `PAIR_OK paired through the API in 1 taps` | `CPS-attempts.txt` | a pair through the API with one tap on D-SAM |
| T-STOP | `_STOPPED ` | `<run>-attempts.txt` | a route that did not finish |

## 6. Runs

### setup. Helper built, apps stopped, names and bonds as found

**setup.sh**
```bash
rl_step "build the helper on the PC"
bash "$LIBDIR/bt-helper/build.sh" > "$EV/setup-build.txt" 2>&1
rl_step "stop the app on every unit"
for s in "$DHU" "$SAM" "$POCO" "$MOTO"; do adb -s "$s" shell am force-stop "$PKG"; done
rl_step "names, addresses, bonds"
names > "$EV/names-start.txt"
for p in POCO:DHU MOTO:DHU POCO:SAM; do
  x=${p%:*}; y=${p#*:}
  echo "$x-$y $(bstate "$(serial_of "$x")" "$(serial_of "$y")")" >> "$EV/bonds-start.txt"
done
```

**grade-setup.sh**
```bash
rl_cond 1 ge "$(grep -c 'rig-bt-helper.apk$' "$EV/setup-build.txt")" 1 'the helper APK was built'  # not-literal: the build's last line
rl_cond 2 eq "$(grep -c '^addr=..:..:..:..:..:..$' "$EV/names-start.txt")" 4 'every unit reads its own address'  # not-literal
rl_cond 3 eq "$(grep -cE '^[A-Z]+-[A-Z]+ (yes|no)$' "$EV/bonds-start.txt")" 3 'the three bonds read as yes or no'  # not-literal
rl_cond 4 eq "$(grep -c '^UNREADABLE' "$EV/names-start.txt")" 0 'every unit reads its bonded list'  # not-literal
```

- **PASS** when all four hold. Any other verdict stops the round, and restore still runs.

### L. The helper lists bonds on each unit

**l.sh**
```bash
for u in DHU POCO MOTO SAM; do
  echo "$u $(python3 -I "$LIBDIR/rig_bt.py" list "$(serial_of "$u")")" >> "$EV/L-attempts.txt"
done
```

**grade-l.sh**
```bash
A="$EV/L-attempts.txt"
rl_cond 1 eq "$(grep -cE '^(DHU|POCO|MOTO|SAM) LIST ..:..:..:..:..:.. ' "$A")" 4 'the helper listed bonds on all four units'  # not-literal
rl_cond 2 eq "$(grep -cE '^(DHU|POCO|MOTO|SAM) FAIL' "$A")" 0 "helper failures ($(grep -m1 -E '^[A-Z]+ FAIL' "$A" | cut -c1-140))"  # not-literal
```

- **PASS** when both hold. **FAIL** when the helper fails on a unit: the line names the cause.

### CPH. D-POCO and D-HU, 2 cycles (a point)

**cph.sh**
```bash
cycles POCO DHU 2
```

**grade-cph.sh**
```bash
api_grade CPH 2 0
```

- **PASS** when 2 forgets and 2 pairs went through the API with no tap and no stop. **FAIL** otherwise; the stop line
  or the `steps.log` that fell back to Settings names the cause.

### CMH. D-MOTO and D-HU, 2 cycles (a point)

**cmh.sh**
```bash
cycles MOTO DHU 2
```

**grade-cmh.sh**
```bash
api_grade CMH 2 0
```

- **PASS** and **FAIL** as CPH.

### CPS. D-POCO and D-SAM, 1 cycle with one tap

**cps.sh**
```bash
cycles POCO SAM 1
```

**grade-cps.sh**
```bash
api_grade CPS 1 1
```

- **PASS** when the forget went through the API and the pair through the API with one tap on D-SAM. D-SAM's own half
  bond is forgotten by the helper on D-SAM first (`half bond on <D-SAM>: ... through the API` in the pair's
  `steps.log`); report its route, it is not graded here. **FAIL** otherwise.

### restore. Bonds back as found (always runs)

**restore.sh**
```bash
fail=0
while read -r pair want; do
  a=$(serial_of "${pair%-*}"); b=$(serial_of "${pair#*-}")
  if [ "$want" = yes ]; then pr "$pair-restore" "$a" "$b" || fail=1
  else
    fg "$pair-restore" "$a" "$b" || fail=1
    fg "$pair-restore-far" "$b" "$a" || fail=1
  fi
  echo "$pair want=$want now=$(bstate "$a" "$b")" >> "$EV/bonds-end.txt"
done < "$EV/bonds-start.txt"
names > "$EV/names-end.txt"
for s in "$DHU" "$SAM" "$POCO" "$MOTO"; do adb -s "$s" shell input keyevent KEYCODE_HOME; done
exit "$fail"
```

**grade-restore.sh**
```bash
rl_cond 1 eq "$(awk '{split($2,w,"=");split($3,n,"="); if (w[2]==n[2]) c++} END{print c+0}' "$EV/bonds-end.txt")" "$(grep -c . "$EV/bonds-start.txt")" 'every bond is back as setup found it'  # not-literal
```

- **PASS** when every bond is back. A FAIL lists the pair; `check --fix` then asks the operator at the next round.

## 7. Scripts

**lib.sh**
```bash
# Serials and the forget and pair wrappers for bt-helper round 3. Sourced before every script and grade.
source /home/oscar/Coding/StudioProjects/rig-toolkit/rig_devices.sh
PKG=com.andrerinas.headunitrevived
DHU=$D_HU; SAM=$D_SAM; POCO=$D_POCO; MOTO=$D_MOTO
SCR=$EV/scripts; LIBDIR=${RL_HOME:-.}

serial_of() { eval "echo \$$1"; }   # serial_of POCO: the serial of that short name
names() {   # every unit's own name and address, then its bonded list (address and the name it shows)
  local u s
  for u in DHU SAM POCO MOTO; do
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
  [ "$(bstate "$a" "$b")" = yes ] || pr "$1-$2-prepair" "$a" "$b"
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

The accept on D-HU (round 1) and `removeBond()` on D-SAM (round 2) are settled. CPH and CPS measure them again on the way.

## 9. Report back

1. CPH and CMH: forgets and pairs through the API of each run's total. For each stop or fallback, its line and the
   `api unpair:` or `api pair:` line from its `steps.log` (the helper's own result word and reason).
2. CPS: whether D-SAM's one tap landed, and how D-SAM's own half bond was forgotten (the helper's `api unpair:` line
   on D-SAM, or the Settings route).
3. L: each unit's `LIST` line, and `names-start.txt` against `names-end.txt` and `bonds-end.txt`.
