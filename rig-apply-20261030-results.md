# Rig apply: the 2026-10-30 changes on the tester PC

1. Fast-forward and sync: transfer/rig-rounds at 8020dbebe (merged ff-only from fork); `rig_sync.py sync --prefer branch` ran, `status` printed "in sync", exit=0. No skill or agent was copied, so no restart is needed.
2. rig-lib tests: `96 passed, 0 failed`.
3. Bluetooth helper: first run failed (`javac: command not found`, the PATH JDK has no compiler). Re-run with `/opt/android-studio/jbr/bin` on PATH succeeded; last line `rig-lib/bt-helper/build/rig-bt-helper.apk`. Not installed.
4. Role names were not yet applied. No line in rig-toolkit or `.claude` printed a spelling warning (grep "underscore" found none); the failure was `rig_preflight.sh` line 25, `D-HU: invalid variable name`, which exited 0 with PREFLIGHT OK. Changed two commands with `role=${role//-/_}`: `rig_preflight.sh` (before the serial lookup) and `rig_check_devices` in `rig_devices.sh`. Added `Accepts D-HU or D_HU.` under both in TOOLS.md.
   - `rig_preflight.sh D-HU D_POCO` and `D_HU D-POCO`: identical table, a row for each unit, exit=0.
   - `rig_preflight.sh D-FOO`: `D_FOO MISSING`, FAIL, exit=1.
   - `rig_check_devices D-HU D_POCO` returns 0; `D-FOO` prints `MISSING D_FOO=` and returns 1.
   Other tools in TOOLS.md that take a role were not audited beyond these two (only these two take a role name as an argument in the sources read).
