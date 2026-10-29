# hus-version-retry, round 1 results

**Candidate (F):** `fork/fix/hus-single-version-request` @ `68c2bbb500aa545d4354717e4a3e31425d926487`   **Baseline (B):** its parent, upstream main @ `27c3e48b727a72d8caf1302e6fc4b388510ac8e1`
**APK md5:** F `e56ac3e779bf6b4e646d58b1afc59167` / B `026416cd91be36bfacf810d6b9318dc3` (build gate: md5s differ, `versionRequests` 1 in F, 0 in B; both identity checks matched, `ACTION_QUERY_STATE` commit `68c2bbb500aa` / `27c3e48b727a`)
**Unit:** D-HU (UNISOC MT50, Android 14, rooted adbd, station on the house WiFi, `HU_IP` 192.168.1.6, `wlan0`) against D-POCO (Android Auto head unit server on :5277, Gearhead `18.0.664204-release`, `POCO_IP` 192.168.1.20)
**Date:** 2026-10-21

## Setup notes

- Transfer branch was already checked out in `worktrees/rig-sync__scratch__8228cb07__20261015` (clean); fast-forwarded there. `rig_sync.py sync`: in sync, no restart needed.
- Quirk files read: `topics/tooling.md`, `units/D-HU.md`, `units/D-POCO.md`, `topics/gearhead.md`. Template read except 7b and 8.
- Script defects, all worked around, none changed what a run measured:
  - `extract_brief_blocks.py`, `set_hu_settings_host.py` and `set_prefs_runas_host.py` are not executable; ran with `python3 -I` (`lib.sh` `seed` edited to call them that way).
  - `build.sh` picks `ls -d worktrees/* | head -1`, an unrelated worktree; replaced by a `wt-new.sh` worktree, same checkout, tree gate and build steps (`scripts/build_host.sh`).
  - The scripts expect `$OUT/F.apk` and `$OUT/B.apk`; the build copies were in `$OUT/apk/`. The first CF1 attempt failed on this and the `seed` defect, launched the pre-round build (commit `11a2c37f`, wifi mode 3), and was discarded (no data); CF1 below is the second attempt.
  - `preflight.sh`'s `stat -c '%U:%G %a %n'` prints errors for `%a` and `%n` on this toybox; owners read `u0_a176:u0_a176` for both `shared_prefs` and `settings.xml` (correct).
  - Running `preflight.sh` twice overwrote `POCO_STAYON` with its own earlier change (`2`); corrected `env.sh` to the opening value `0`.
- Opening pre-flight (`rig_preflight.sh D_HU:wifi D_POCO:wifi`): D_HU wifi 1, bt 1, Awake; D_POCO wifi 1, bt 0, Awake. One idle Gradle daemon was a stray after the build and was killed with `rig_cleanup.sh kill`.
- `preflight.sh` run 1 failed two gates: D-HU station not on the house WiFi (SSID unknown, DISCONNECTED) and D-POCO server listeners 0. Both were hand steps 1 and 2, done by the operator. D-POCO also had Open Headunit running (pid 15715), stopped by the script. Run 2: SSID "Pegue Cdesta", link 4/4 replies (64 to 344 ms), listeners 1, netem ping 2669 ms and 2589 ms (gate 2400+), `NETEM=yes`, qdisc baseline back (baseline shows a `clsact` line beside `pfifo_fast`). D-POCO had Gearhead settings in front (the operator's dev settings); the script sent HOME. Hand step 3 (other head units off the WiFi) was covered by the operator's confirmation; every run's `peers` list showed only `HU_IP` besides the listener.
- D-HU baseline settings (restored at the end): `wifi-connection-mode` 3, `log-level` 2; before-APK md5 `7219f83f3f82adb5ded4b5a8adf6e805`, `versionName 4.0.0-alpha`, commit `11a2c37f`.
- Settings written per run (`seed`): the section 4 keys, `wifi-connection-mode` 1 in C runs and 0 in D runs, `log-level` 1; both read back before each launch. `ACTION_LOG_MARKER` needed no `allow-external-configuration` (not in `CONFIGURING` on either arm).
- Hand step 4 (main round): RS1 `restarted=1` after 34 s, RS2 `restarted=1` after 71 s; listener only in `peers` afterwards both times.
- Thermal: `th_gate` before every run, host 64 to 65 C, `throttle_pkg=0` in all `thermal-*.log` I read.
- Phone-side strings printed at the level captured (`hu_connected` was non-zero in healthy windows), so the phone conditions are graded, not record-only.
- No run was voided after the first CF1 attempt. No lever re-run was needed (every D run's gate held first time).
- Restore and closing pre-flight: D-HU APK back to md5 `7219f83f...` (identity MATCH), `settings.xml` diff clean (`HU_SETTINGS_BACK`), qdisc baseline back; D-HP APK back (identity MATCH) and `settings.xml` diff clean (`HP_SETTINGS_BACK`); D-POCO `stay_on_while_plugged_in` back to 0. D-POCO's Bluetooth read `enabled: true` after the restore script although it was off at the opening and the script never enables it (cause unknown, possibly a self-revert); disabled again with `svc bluetooth disable`, read back `false`. Closing `rig_preflight.sh D_HU:wifi D_POCO:wifi D_HP:wifi`: D_HU wifi 1 bt 1, D_POCO wifi 1 bt 0, D_HP wifi 1 bt 1, all Awake, PREFLIGHT OK; `rig_cleanup.sh list` clean. D-POCO's server was left running (listeners 1).
- One `pkill -f` slipped into the host shell during the D-HP addendum (house rule 9); it only ended the waiting script, and the captures and thermal watch were stopped by pid.

## CF1

**PASS**

- Settings: mode 1, log-level 1. Conditions: `init_auto` 1, `found` 1, `ssl` 1, `tput` 1, `failed` 0, `vreq` 1, `within2s` 0, phone `hu_connected` 1.
- `gap_ms` 2267 (Version request sent to the phone selected): F waited past 2 s on a normal link and did not resend. Phone `car_requests` 1.

## DF1

**PASS**

- Mode 0, netem 2500 ms, `NM_ON_OK`, `NM_OFF_OK`. Gate: `selected` 1, `gap_ms` 2575.
- n0: `vreq` 1, `within2s` 0, `ssl` 1, `tput` 1, `failed` 0. n1: `ssl` 1, `tput` 1, `failed` 0. Phone n0 `car_requests` 1, `late` 0; n1 `hu_connected` 1.
- `22:24:19.472 Handshake: Version request sent ... attempt: 1`, `22:24:22.047 the phone selected 1.7`, `22:24:30.300 SSL handshake complete` (the delay was removed at 22:24:25), `22:24:37.062 Throughput over 5009ms: rendered=113`.

## DF2

**PASS**

- Same settings. Gate: `gap_ms` 2584. n0: `vreq` 1, `within2s` 0, `ssl` 1, `tput` 1, `failed` 0. n1: `ssl` 1, `tput` 1, `failed` 0. Phone n0 `car_requests` 1; n1 `hu_connected` 1.

## CB1

**FAIL**

- Mode 1, no lever. `vreq` 2, `within2s` 1 (rule: 1 and 0), `late` 1, phone `car_requests` 2, `gap_ms` 2667. The slow first answer reproduced the resend with no lever.
- The session still formed: `ssl` 1, `tput` 1, `failed` 0, `silent` 0. The server was not deaf afterwards, so no restart was called. A resend with two requests answered did not wedge on its own here.

## DB1

**FAIL**

- Mode 0, netem 2500 ms, `NM_ON_OK`, `NM_OFF_OK`. Gate met: `within2s` 1, `gap_ms` 2604.
- n0: `vreq` 2, `selected` 1, `late` 1, `ssl` **1**, `tput` 0, `failed` 0. Phone `car_requests` 2.
- n1: `vreq` 3, `budget` 1, `failed` 1, `silent` 1, `silent_line` 1, `ssl` 0; phone `hu_connected` 0. n2: same counts, phone `hu_connected` 0.
- `peers` after: `0A 0.0.0.0` plus two sockets in state `08` from D-HU (ports 38228 and 56592).
- Why FAIL: the PASS clause needs n0 `ssl` = 0 and the FAIL clause names n0 `ssl` = 1. n0 did complete TLS, but 10.3 s after the late answer: `22:27:18.566 Version request sent (attempt 1)`, `22:27:20.570 No VERSION_RESPONSE within 2s`, `22:27:20.771 Version request sent (attempt 2)`, `22:27:21.170 the phone selected 1.7`, `22:27:23.760 SSL Handshake: discarded a late VERSION_RESPONSE`, `22:27:31.510 SSL handshake complete`; state reached `projecting` at `22:27:32.188` with no `Throughput over` in 30 s, then the clean bye at `22:28:04`. Every later connection (n1, n2) was accepted by the kernel and never served. By the effect that matters (server deaf after the duplicate request) the wedge reproduced; by the brief's n0 clause it did not.

## RS1

Not graded. `restarted=1` after 34 s, listeners 1. After the restart `peers` showed only the listener (the two parked sockets were gone).

## CB2

**FAIL**

- Cold server after RS1, mode 1. Counts: `vreq` 2, `ssl` 2, `found` 2, `scan` 6, `within2s` 0, `failed` 0, `silent` 0, `tput` 1 (rule: `ssl` 1, `vreq` 1). Two sessions formed in the window (mode 1 rediscovery); both answered inside 2 s. First `gap_ms` 1068. Phone `car_requests` 2, `hu_connected` 4. The server was healthy.

## DB2

**FAIL**

- Same as RDB1. Gate met: `within2s` 1, `gap_ms` 2601. n0: `vreq` 2, `late` 1, `ssl` 1, `tput` 0, `failed` 0, phone `car_requests` 2. n1: `vreq` 3, `budget` 1, `silent` 1, `ssl` 0, phone `hu_connected` 0. n2: same, phone `hu_connected` 0. `peers` after: two `08` sockets from D-HU (40840, 35264).
- n2 shows `init_manual` 1 inside its window (the service re-initialised between n1 and n2); not examined.

## RS2

Not graded. `restarted=1` after 71 s, listeners 1.

## CF2

**FAIL**

- Cold server after RS2, mode 1. `vreq` 2, `ssl` 2, `found` 2, `scan` 2, `within2s` 0, `failed` 0, `silent` 0, `tput` 1. First `gap_ms` 452. Two sessions again: `22:34:55.326 SSL handshake complete`, then a second scan at `22:34:58.885` and a second `SSL handshake complete` at `22:35:01.179`, before the bye at `22:35:11`. Phone `car_requests` 2, `hu_connected` 4. The server was healthy.

## Report back (section 10)

1. **DB:** 2 of 2 DB runs passed the gate. By effect the wedge reproduced in both (n0 `vreq` 2; n1 and n2 `silent` 1 each; phone `hu_connected` 0; two parked sockets). By the brief's letter neither is a PASS, because n0 `ssl` was 1 (TLS finished about 10 s after the late answer, no video) in both.
2. **DF:** 2 of 2 passed the gate and passed: n0 `vreq` 1 and `ssl` 1, n1 `ssl` 1; `gap_ms` 2575 and 2584.
3. **Controls:** 1 of 4 passed (CF1). CB1 failed on a resend (`vreq` 2, no wedge); CB2 and CF2 failed on a second session from mode 1 rediscovery (`ssl` 2, `vreq` 2, no resend, no failure).

Per D run, phone n0 `car_requests` / `late`: DF1 1 / 0, DF2 1 / 0, DB1 2 / 1, DB2 2 / 1. After each wedge `peers` listed two `08` sockets from `HU_IP`, cleared by the restart.

## Addendum: D-HP (operator request, link speed)

D-HP (HP Slate 7 Plus, Android 4.2.2, shell uid not root, 192.168.1.27 on the house WiFi) was run after the main round because the operator suspected link speed. It has no root, so no netem lever; the section 7 fallback was used with its own link as the delay. D-HP backup: `hp-backup.xml` (5994 bytes), before-APK md5 `7219f83f3f82adb5ded4b5a8adf6e805`. `run-as` settings writes, `ACTION_QUERY_STATE` and the `ACTION_LOG_MARKER` verb all worked on API 17. Ping to D-POCO from D-HP: 28 to 109 ms (6/6); from D-HU at the same time: 35 to 145 ms (6/6), so the two units' round-trip times were alike.

## FBF1

**INCONCLUSIVE**

- F, mode 0, cold server after a hand restart. Gate (`gap_ms` 2000+) missed: `gap_ms` 50. n0: `vreq` 1, `within2s` 0, `selected` 1, `ssl` 1, `tput` 1, `failed` 0. Phone `car_requests` 1.

## FBB1

**INCONCLUSIVE**

- B, mode 0, cold server after a hand restart. `gap_ms` 130, `vreq` 1, `within2s` 0 (B never resent), `ssl` 1, `tput` 1, `failed` 0. Phone `car_requests` 1.

## FWF2

**INCONCLUSIVE**

- Deviation: FBF2 was started but the operator said to go without the hand restart, so it was abandoned (partial files in `abandoned/`) and run as a warm-server run with `scripts/run_warm.sh` (`run_cold.sh` minus the restart wait). F, `gap_ms` 80, `vreq` 1, `ssl` 1, `tput` 1, `failed` 0.

## FWB2

**INCONCLUSIVE**

- Same warm-server deviation. B, `gap_ms` 90, `vreq` 1, `within2s` 0, `ssl` 1, `tput` 1, `failed` 0. With FBB1 that is two B runs that missed the gate, so the D-HP fallback stops as INCONCLUSIVE (section 7).

On D-HP the first answer came in 50 to 130 ms on both builds, cold or warm, so B never resent and nothing could wedge. The slow-answer condition is not produced by D-HP's link. Its round-trip times matched D-HU's, so round-trip time alone does not explain D-HU's 2267 to 2667 ms answers either; D-HU's slow answers came in mode 1 on a server that had been up for a while, while D-HP's were mode 0.

## Anything the brief did not ask about

- The parked sockets after a wedge are in state `08` (CLOSE_WAIT), not `01` as `peers.py` documents. D-HU had closed them; D-POCO's server never read them.
- Server age seems to matter for answer time: `gap_ms` was 2267 to 2667 on the three C and D runs that followed the operator's first server start, and 1068 and 452 on the first sessions after a restart. Not tested; recorded as a correlation only.
- CB1 shows B resending (two requests, both answered) without a wedge, and DB1 n0 shows the same resend followed by a deaf server. The difference between the two runs is the delay lever, so the duplicate request alone is not shown to wedge; the order VR, VR, ClientHello with the late first answer is what these runs have in common with the wedge.
- Mode 1 formed two sessions per run in CB2 and CF2 but one in CF1 and CB1; cause not examined (the controls' `ssl` = 1 rule assumes one).
- Hypotheses 2 and 4 were not contradicted: the cold-server first sessions (CB2, CF2) connected, and F under the same delay (DF1, DF2) connected twice.

Evidence: release `rig-evidence-hus-version-retry`, asset `hus-version-retry-round1-captures.zip`, sha256 f7858e65a9dfc25a4241f5220fe3c3a5b35ee8f76576eb162777fcee468637c9.
