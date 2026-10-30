# Check on the tester PC, 2026-10-30: does adb's shell user hold the Bluetooth system permission

**For the operator:** start Claude Code in this worktree and type one line:

```
Run git fetch fork, then follow rig-sdlc/CHECK-2026-10-30-shell-bt-permission.md as it is on fork/transfer/rig-rounds
```

No build, no app, no hand step. Every command only reads. It takes about 2 minutes.

## Why

The rig's Bluetooth forget and pair can skip the Settings screens if adb's shell user (`com.android.shell`) holds
`BLUETOOTH_PRIVILEGED` and `BLUETOOTH_CONNECT`. Then a small test APK started with `am instrument` can borrow those
permissions (`UiAutomation.adoptShellPermissionIdentity()`, Android 10 and up) and call the Bluetooth API directly.
AOSP's main branch grants both to the Shell app. This check reads whether our units' own builds do.

## What to do

1. `source /home/oscar/Coding/StudioProjects/rig-toolkit/rig_devices.sh`
2. For each of `$D_HU`, `$D_POCO`, `$D_MOTO`, `$D_SAM`, `$D_HP` that `adb devices` shows, run these and keep the
   full output. Skip a unit that is not on adb and say so.

   ```
   adb -s <serial> shell getprop ro.build.version.sdk
   adb -s <serial> shell getprop ro.build.fingerprint
   adb -s <serial> shell dumpsys package com.android.shell | grep -E "BLUETOOTH(_PRIVILEGED|_CONNECT|_ADMIN)?[:\" ]"
   adb -s <serial> shell settings get global hidden_api_policy
   adb -s <serial> shell id
   ```

3. Write `shell-bt-permission-check-results.md` in the worktree root with:
   - one table row per unit: unit, SDK, `BLUETOOTH_PRIVILEGED` granted (true, false or absent), `BLUETOOTH_CONNECT`
     granted (true, false or absent), `hidden_api_policy` value, and the `id` line's uid;
   - below the table, each unit's raw output from step 2, in a code block;
   - one line per unit that is not on adb.

   Read "granted" only from a line that names the permission with `granted=true` or `granted=false`. A permission
   that no line names is "absent". Do not infer it from the SDK level.
4. Commit only that file (`git add shell-bt-permission-check-results.md`), with the message
   `Rig check: shell Bluetooth permissions on each unit`, and push to fork.

Change nothing on any unit: no `settings put`, no install, no radio switch.
