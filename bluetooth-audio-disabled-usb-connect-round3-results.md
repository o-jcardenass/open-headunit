# bluetooth-audio-disabled-usb-connect, round 3 results

**Candidate:** `fix/bluetooth-audio-disabled-usb-connect` @ `fff96699480d878bb36bd04078d77260231b19dc`       **Baseline:** none (measurement round, no fix on trial)
**APK md5:** `234caaadff2d4731e006591a0c69bb2a` (reused `apks/candidate-fff96699.apk`, not rebuilt) / no baseline
**Unit:** Stage U: D-POCO (POCO X3, Android 15) as head unit over USB, D-MOTO (Motorola edge 30 neo, Android 14, Android Auto 17.9.664004) as phone. Stage N: D-HU (MT50, Android 14) over Native AA, D-MOTO as phone.
**Date:** 2026-10-07

## Setup notes

**Pre-flight** (`rig_preflight.sh D_HU:wifi,bt D_MOTO:wifi,bt D_POCO:wifi,bt`), PREFLIGHT OK:

| Role | adb | WiFi | BT | Screen | Profiles |
|---|---|---|---|---|---|
| D_HU | ok | 1 | 1 | Awake | HFP:none A2DP:none |
| D_MOTO | ok | 1 | 1 | Awake | HFP:none A2DP:none |
| D_POCO | ok | 1 | 1 | Awake | HFP up, A2DP up |

A stray `rig_thermal.sh watch` from an earlier thread was found and killed by pid before the round. Host package temperature 58C to 61C at start, max 73C in any run, `throttle_delta=0` in every run.

**Build and identity.** No build: `apks/candidate-fff96699.apk` existed with the expected md5. D-POCO already carried it (pulled md5 `234caaad...`). D-HU carried another APK (`5c12c485a08d480ffd006636e0bd9bbf`), so I backed up its `settings.xml`, ran `adb install -r -d`, and the post-install `settings.xml` was byte-identical to the backup. `ACTION_QUERY_STATE` on both returned `commit` `fff96699480d`. The DEX symbol check was not needed (no new APK was built).

**Settings.** Backups of both units were taken with the app stopped. Against round 2's closing backups the only differences are how empty strings are serialised (`<string name="x"></string>` against `<string name="x" />`), so the delta is zero in value terms. `shared_prefs` on D-POCO is `u0_a277` mode `drwxrwx--x`; on D-HU `u0_a176:u0_a176` mode 771. Rig audio keys read from D-HU's backup and not written: `use-aac-audio=false`, `audio-latency-multiplier=8`, `audio-queue-capacity=20`. Both restored at the end and read back with an empty diff.

**Addresses.** `POCO_BT=DC:B7:2E:5E:4E:59`, `MOTO_BT=A0:46:5A:97:E4:95`, `DHU_ANN=11:46:03:10:33:59`. All three equal round 2. Bonds: D-MOTO lists D-POCO and D-HU (`Navegadortz2`); H5 not needed.

**Scripts.** From `hur-wifi-test-scripts/`: `rig_preflight.sh`, `rig_thermal.sh`. New this round, in `bluetooth-audio-disabled-usb-connect-round3/`: `lib1008r3.sh` (the brief's lib, cut from the brief), `ohu_setkeys.py` (copied from round 2), `stageU_r3.sh` and `stageN_r3.sh` (one run per call, flock on `/tmp/ohu-rig.lock`, last log line `script exit=N`). Quirk files read: `rig-quirks/topics/tooling.md`, `topics/bt.md`, `units/D-MOTO.md`, `units/D-POCO.md`, `units/D-HU.md`.

**Deviations from the brief:**

1. **No `rig-executor` agent was used.** The host (this session) ran every command and every run in the foreground, as the project rule for scripted hardware rounds asks. Grades are from the host's own greps.
2. **H7 replaced.** The cue terminal was dropped because the operator sits at the rig PC. `cue()` in `lib1008r3.sh` was changed to also fire `notify-send -u critical` and `spd-say`, so a cue reaches the operator without a model in the path. The operator confirmed the popup worked before Stage U. The rest of `cue()` is unchanged.
3. **The `hu.verbs` threshold is one short in the harness.** `hu.verbs` counts `AutomationReceiver:` lines inside the `-start` to `-end` window, but the `-start` verb's own receiver line is logged just before its marker line and so falls outside the window. The window count is one below the number of verbs that landed. Measured on every run:

   | Run | Script window | Whole capture |
   |---|---|---|
   | U5a4 | 8 | 9 (a second `ACTION_CHECK_USB` was sent) |
   | U5a5 | 7 | 8 |
   | U5a6 | 7 | 8 |
   | U5a7 | 7 | 8 |
   | U5a9 | 7 | 8 |
   | N0p | 6 | 7 |
   | N4a1, N4b1 | 6 | 7 |

   By the script literal (item 1 of the valid-pull list, "8 or more"), only U5a4 counts among the pulls. By the whole-capture number every expected verb (five markers, `ACTION_SET_SETTINGS`, `ACTION_GET_SETTINGS`, `ACTION_CHECK_USB`) landed in all five. I report both. The U5a verdict below is PASS on either reading.
4. **USB enumerates once per plug-in.** In every pull run the first bring-up after the replug did not produce a session: D-POCO logged `Found device already in accessory mode` and, in U5a5, `javax.net.ssl.SSLException: Unable to parse TLS packet header` (16:07:05). A session formed only after a second unplug, 5 s, replug, and the USB dialog (`UsbConfirmActivity`) allowed on D-POCO. This is why each pull run took about 4 minutes. No Gearhead force-stop was needed in any Stage U run (`gh_force_stop_before_session` count: 0).
5. **H3 handled by the host with HOME**, as the brief allows. U5a4: D-MOTO's focus was `com.android.settings/Settings$UsbDetailsActivity` (the USB settings screen). U5a5: focus was Maps' `GhostActivity`, HOME was probably unnecessary. In both, the session formed only after the operator's re-enumerate, so the screen was not the blocker.
6. **N4a2 is VOID: the operator touched D-MOTO's Settings.** At 16:33:02 `com.android.settings` (pid 13534) opened `ConnectedDeviceDashboardFragment`, and set the A2DP policy for `11:46:03:10:33:59` to `100` at 16:33:25.829, 16:33:27.211 and 16:33:28.398, while Gearhead set it back to `0` in between (16:33:25.927, 16:33:27.261). The stop marker was 16:33:27.816. No cue had been issued. The operator confirmed afterwards that they opened the Settings. Reading `p.reading=OTHER`, `p.non_gh_setters_before_read=3`. The brief's one `x` re-run was used: N4a2x.
7. **Watchers.** Seven stale cue watchers replayed old cues into the chat during the round; they were stopped by task id. No effect on any capture.

**Stage U close.** `ACTION_EXIT`, `headunit://exit`, force-stop, D-POCO `settings.xml` restored and read back (empty diff), `dumpsys usb` before and after saved. D-MOTO and D-POCO moved back to PC cables, `adb usb` run on both. D-POCO's last stored policy for D-MOTO: `p.END_at_end=100` (U5a9). D-POCO was kept out of Stage N with Bluetooth off and airplane mode on (confirmed `enabled: false` and `airplane_mode_on=1`), and re-enabled after the round (`enabled: true`, `airplane_mode_on=0`).

**Stage N close.** `ACTION_EXIT`, force-stop, D-HU `settings.xml` restored (`head-unit-make` `Google`, `head-unit-model` `Desktop Head Unit`, matching the backup; empty diff). The last `Saved connectionPolicy 11:46:03:10:33:59 = 100` is at 16:42:05.245, from N4b2, so no closing H6 was needed. D-MOTO `svc power stayon false` done. `ps aux | grep logcat` shows nothing running.

## U5a (cable pulled mid-session)

**PASS**

At least two runs count and every counted run reads RESTORED. Counting rules differ, so both are shown. Round 2's U5a2 also counts (P0 `100`, RESTORED, `delay_from_gone_ms` -88).

| Run | Counted by script literal | P0 | `p.reading` | `delay_from_gone_ms` | `delay_from_stop_ms` | `hu.verbs` (window / whole) |
|---|---|---|---|---|---|---|
| U5a4 | yes | 100 | RESTORED | 460 | 4467 | 8 / 9 |
| U5a5 | no (item 1, 7) | 100 | RESTORED | -202 | 3848 | 7 / 8 |
| U5a6 | no (item 1, 7) | 100 | RESTORED | 260 | 6322 | 7 / 8 |
| U5a7 | no (item 1, 7) | 100 | RESTORED | -10 | 5546 | 7 / 8 |
| U5a9 | no (item 1, 7) | 100 | RESTORED | 278 | 5835 | 7 / 8 |

U5a8 was skipped by agreement with the operator; the brief's stop rule is a cap.

Every other validity item held in all five runs: `hu.ssl=1`, `hu.ssl_after_stop=0`, `hu.live_at_stop=1`, exactly one announce line `carAddress=DC:B7:2E:5E:4E:59 (bt-announce=real)`, `Google` and `Desktop Head Unit` in `getk`, the phone markers present, `p.disabled_by_gh_before_stop=3`, `hu.fatal=0`, no Gearhead process in `ph.fatal_processes`.

Decisive lines, U5a4: `16:05:10.682 AapService: session state disconnected (link_lost)`; the restore is `setConnectionPolicy(..., 100)` at 16:05:12.171 from pid 32069 (`com.google.android.projection.gearhead:car`), and the last stored value up to the read marker is `100`. `p.non_gh_setters_before_read=0` in all five. The same `link_lost` reason was logged in U5a5 (16:09:18.318), U5a6 (16:13:48.079), U5a7 (16:18:20.802) and U5a9 (16:21:35.865).

**Restore delay (second question, no effect on the verdict).** `delay_from_gone_ms` of the five runs plus round 2's U5a2: -88, -202, -10, 260, 278, 460. The values fall into two groups: the restore is already logged at or within 202 ms before the `-gone` marker (-202, -88, -10), or 260 to 460 ms after it (260, 278, 460). None of the six is near round 2's 8.5 s group. The `-gone` marker is written when the head unit logs the disconnect, so a negative delay means the phone side logged the restore first. Measured from the stop marker the restores are 3848 to 6322 ms.

A pulled cable gives the toggle back, as a clean end does. This is the reporter's end of the session, and in this rig it does not leave Media audio off.

## N0p (gate and primer, clean end)

**PASS**

Valid (whole-capture verbs 7, window 6, see deviation 3). `p.P0=100`, `p.reading=RESTORED`. `p.restore=16:25:29.535` from pid 32069 (`gearhead:car`), `delay_from_stop_ms=1448`. `p.disabled_by_gh_before_stop=3`. `hu.match=0`, `hu.groups=1`, `hu.listen=1 hu.poke=1 hu.poke_ok=1 hu.accept=1`, announce `carAddress=11:46:03:10:33:59 (bt-announce=real)`, `bt-announce` absent from `getk`. No H6 was needed. N0px was not needed: N0p formed a session on its first bring-up, so the recovery step in the brief never ran.

## N4a (Android Auto force-stopped, Native AA)

**PASS**

Two counted repetitions both read NOT_RESTORED.

| Run | Valid | P0 | `p.disabled_by_gh_before_stop` | `p.reading` | `p.END_at_read` |
|---|---|---|---|---|---|
| N4a1 | yes | 100 | 3 | NOT_RESTORED | 0 |
| N4a2 | VOID (operator touched Settings, deviation 6) | 100 | 5 | OTHER | 100 |
| N4a2x | yes (re-run) | 100 | 3 | NOT_RESTORED | 0 |

Both valid runs: `hu.ssl=1`, `hu.match=0`, `hu.groups=1`, `hu.fatal=0`, no non-Gearhead setter, no `setConnectionPolicy(..., 100)` after the stop. Measured: a force-stop leaves the policy at `0` on Native AA, as on USB.

## N4b (clean Native AA session from a policy left at 0)

**PASS**

| Run | Valid | P0 | `p.reading` | `p.restore` | `delay_from_stop_ms` | `p.END_at_read` |
|---|---|---|---|---|---|---|
| N4b1 | yes | 0 | RESTORED | 16:31:00.150, pid 28652 (`gearhead:car`) | 880 | 100 |
| N4b2 | yes | 0 | RESTORED | 16:42:05.240, pid 6441 (`gearhead:car`) | 1411 | 100 |

Both repetitions read the same. Both started from `0` (what N4a1 and N4a2x left) and both ended at `100` with no H6, so there is no operator "Media audio was off or on" report to give. The next clean Native AA session puts the toggle back.

## Anything the brief did not ask about

- **Unaccounted deviation in the brief itself:** item 1 of the valid-pull list is satisfiable only when the first bring-up fails and a second `ACTION_CHECK_USB` is sent. The off-by-one in `hu.verbs` (deviation 3) is a harness bug; a corrected window that starts at the `-start` verb's receiver line gives 8 for every pull. A later round should count from the line before the `-start` marker, or require 7.
- **The "USB enumerates once" behaviour decided the cost of the round.** Each pull run needed a physical re-enumerate before any session formed, which no scripted lever replaced. All five Stage U sessions formed only after it.
- **Stage N formed a session on the first attempt in every run** (N0p, N4a1, N4b1, N4a2, N4a2x, N4b2). Round 2's UNTESTABLE verdict did not repeat. Differences from round 2: D-POCO's Bluetooth was off and airplane mode was on for all of Stage N, and Stage U's recovery force-stop of Gearhead was never used.
- **Operator interaction with D-MOTO's Settings changes the policy and is invisible to every `hu.*` counter.** Only the phone-side `p.set` owner line (`com.android.settings`) shows it.
- **The Maps `GhostActivity` was in focus on D-MOTO during U5a5's first bring-up**, with no effect on the outcome.
- **Evidence:** captures are in the release asset `bluetooth-audio-disabled-usb-connect-round3-captures.zip` under `rig-evidence-bluetooth-audio-disabled-usb-connect` (sha256 `be6ea955c2116160c3aa147a3f0476e559814e28802afeeba9883327956aafff`).
