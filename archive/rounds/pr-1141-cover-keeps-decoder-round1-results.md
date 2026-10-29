# pr-1141-cover-keeps-decoder, round 1 results

**Candidate:** upstream pull head @ `24dd22194ce946c1340d3d85af093c1146aea8f1`   **Baseline:** upstream main @ `afe6a534811970893b7a8f745617cd84bd04a021`
**APK md5:** `e7a65be38d7a8d7db7563d58bc766ee8` (C) / `2b080a24e163c08e4020c269d1824a39` (B)
**Unit:** D-HU (UNISOC MT50, Android 14, rooted, 1440x720 screen) head unit; D-POCO phone, Gearhead 18.0.664204-release; Native AA wireless, night-mode 1
**Date:** 2026-10-23
**Evidence:** release `rig-evidence-pr-1141-cover-keeps-decoder`, asset `pr-1141-cover-keeps-decoder-round1-captures.zip`, sha256 `caeb0777d4d106142d5e204c637935b0f3c74c9f65c795f50e1a084c3c6dec74` (56895748 bytes).

## Setup notes

- The transfer branch is held by an older scratch worktree, so the primary directory could not check it out. That worktree was fast-forwarded to `b463fa74`; the primary directory stayed on `main`. `rig_sync.py sync` reported in sync, no `CLAUDE.md` change.
- Quirk files read: `topics/tooling.md`, `units/D-HU.md`, `units/D-POCO.md`, `units/D-MOTO.md`, `topics/projection.md`. Template read without 7b and 8.
- Build gate: both commits fetched by URL, trees `32ff7526` (C) and `30145c18` (B) match. B built first, C second, one worktree. DEX symbol `DecoderCoverPolicy`: 0 on B, 3 on C. md5s differ. First copy of `B.apk` took a stale APK from another round (`ls -t`); it was replaced from the B build output (built 09:01:18) before the C build reached its `rm`, and the symbol count confirmed it.
- Opening pre-flight: D_HU adb ok, wifi 1, bt 1, Awake, HFP none, A2DP none; D_POCO the same; PREFLIGHT OK. D-MOTO was plugged in and not in the pre-flight table.
- P: D-HU shell uid 0. `shared_prefs` dir `u0_a176:u0_a176 drwxrwx--x` (`stat %a` is not supported there, `ls -ld` used), `settings.xml` owner `u0_a176:u0_a176`, correct. Round-start settings: view-mode 2, night-mode 0, log-level 2, video-codec H.264, use-aac-audio false. Installed APK before the round: md5 `52a58a03` (neither arm).
- D-MOTO was put in airplane mode (Bluetooth read `enabled: false` after). Its radio state before was not read, so X re-enabled wifi and Bluetooth on the assumption they were on.
- D-POCO rebooted. The boot poll hit its cap of 30 (`sys.boot_completed` 1, Gearhead not yet running after 60 s); the brief does not gate on it and every session formed. Bluetooth enabled, Wi-Fi enabled, `groupFormed false isGroupOwner false`.
- **H1 was needed:** `Navegadortz3` was bonded on D-POCO. The operator forgot it; re-read: `Navegadortz2` 1, `Navegadortz3` 0. `SYSTEM_ALERT_WINDOW` already `allow`, not changed. The cover app is `com.android.settings.homepage.SettingsHomepageActivity`.
- C installed over the old build: `settings.xml` identical to the backup. State reported commit `24dd22194ce9`. B install in R5 reported `afe6a5348119`. X reinstalled C, restored the backup, `diff` empty.
- Files in `$OUT`: `luma.py`, `cover_lib.sh` (both unchanged from the brief), `sx.py` (cut from the screen-disconnect round 2 brief, section 5, unchanged), `ptr_lib.sh` (copied), and host wrappers in `scripts/`: `r12_run.sh` (R1 then R2 in one session), `r34_run.sh` (R3 then R4), `r5_run.sh`. Each wrapper adds only refusals (D-HU root, both Bluetooth on, view-mode reads back), a HOME press on D-POCO, and sourcing `scripts/base.sh`. Each ran under `flock /tmp/ohu-rig.lock` in the background with a monitor.
- Trap: `th_gate` leaves a `th_watch` loop running after the session script exits. It holds the lock fd, so the first R3/R4 launch failed at `flock -n` (`lock/exit=1`) and nothing ran. The loop was killed by pid and R3/R4 relaunched; the wrappers now kill `$THPID` at the end.
- `R1.keys` shows two `night-mode` matches (`value="0"` then `"1"`). The written `w.xml` has one `night-mode` key (1); the 0 is another key matched by the `rd` pattern.
- Brief erratum: the R3 table row calls GLES "a SurfaceView backend". It behaved like TEXTURE (see R3). R4 is the SurfaceView case.
- Windowed greps use `LC_ALL=C`; awk warns of invalid multibyte data in the captures, harmless. Screenshot files carry a doubled prefix (`R1-R1-c1-r1.raw`).
- **Luma limitation:** all 28 shots in R1 and R2 and all 6 in R3 read exactly 225, and one `r1` shot viewed (R1 c1) is the Android Auto day map in front. On B the TEXTURE view keeps its last frame, so luma stayed about 220 in 4 of 5 cycles even though the decoder was rebuilt and the repaired keyframe landed 2.7 s after BACK. Luma only catches a cover that blacks the surface outright.

## P

**PASS**

Build gate, root, settings backup, bonds (after H1), install and identity as above. `P.state` commit `24dd22194ce9`.

## R1

**PASS**

- Settings written: `$BASE int:view-mode=1`, read back `view-mode 1`, `night-mode 1`, `log-level 2`, `wifi-connection-mode 3`.
- Valid cycles: 5 of 5 (`stop=0`, `ret=back`). `ssl=0`, `live=2`, `wrapped=no`, `MATCH! Starting AapService` 0.
- Per cycle, identical in c1 to c5: `Decoder stopped: ` in the cover window 0; `Configuring decoder: ` in the cycle 0; `AapProjectionActivity: onResume` after BACK 1; `onStop` 1; `rend` of the cover window 2 (the hidden decoder kept rendering); `keyframe reached the codec` after return 0; `onSurfaceDestroyed` 0; luma pre / r1 / r2 = 225 / 225 / 225.
- `ALL` (`R1-live` to `R1-done`): release line 0, `SSL handshake complete` 0, byebye 0, `FATAL EXCEPTION` 0. Phone `Critical error` 0.
- The 25 s tail after the last cover held no release line, so `onStart` cancels the pending release.

A 6 s cover on C left the decoder running and no keyframe wait on return.

## R2

**PASS**

Run in R1's session (byebye 0 in the tail), two 30 s covers, both valid (`stop=0`, `ret=back`).

| Cycle | release line | `onStop` to release | `activity_stopped` | `Configuring decoder` in cover | release to configure | `First frame rendered` | rend after release | back to keyframe decoded | luma pre/r1/r2 |
|---|---|---|---|---|---|---|---|---|---|
| R2-c1 | 1 | 20002 ms | 1 | 1 | 81 ms | 0 | 0 | 2673 ms | 225/225/225 |
| R2-c2 | 1 | 20003 ms | 1 | 1 | 66 ms | 0 | 0 | 2654 ms | 225/225/225 |

Decisive lines, c1: `09:27:53.818 AapProjectionActivity: onStop`, `09:28:13.820 AapProjectionActivity: the projection stayed covered, releasing the decoder`, `09:28:13.838 Decoder stopped: activity_stopped`, `09:28:13.901 Configuring decoder: c2.unisoc.avc.decoder for 1280x720`. Window checks: `SSL handshake complete` 0, byebye 0, `FATAL EXCEPTION` 0, phone `Critical error` 0.

**Finding (the review's question, not a condition):** the release lasts only until the next frame. A new codec was configured 66 to 81 ms after the release with the projection still covered. No first frame rendered inside the cover. After BACK the picture was repaired 2.65 to 2.67 s later, about the same as on B (R5).

## R3

**PASS**

- Settings: `$BASE int:view-mode=2` (GLES), read back. `ssl=0`, `live=2`, `MATCH` 0.
- 3 valid cycles. Per cycle: `onResume` 1; `Decoder stopped: surfaceDestroyed` 0; `onSurfaceDestroyed` 0; `Decoder stopped: activity_stopped` 0; `Configuring decoder: ` 0; luma pre / r1 / r2 = 225 / 225 / 225; back to keyframe decoded NA (no rebuild).
- `ALL`: release line 0, `SSL handshake complete` 0, byebye 0, `FATAL EXCEPTION` 0. Phone `Critical error` 0.

GLES kept its surface and the decoder through the cover in all 3 cycles. The change did not need to act here.

## R4

**PASS**

- Settings: `$BASE int:view-mode=0` (SURFACE), read back. `ssl=0`, `live=2`, `MATCH` 0.
- 3 valid cycles, all alike:

| Cycle | `Decoder stopped: surfaceDestroyed` | `onSurfaceDestroyed` | `activity_stopped` | `Configuring decoder` in cycle | back to keyframe decoded | luma pre/r1/r2 |
|---|---|---|---|---|---|---|
| R4-c1 | 1 | 1 | 0 | 1 | 1191 ms | 220/0/219 |
| R4-c2 | 1 | 1 | 0 | 1 | 1192 ms | 219/0/219 |
| R4-c3 | 1 | 1 | 0 | 1 | 1177 ms | 219/0/219 |

- `ALL`: release line 0, `SSL handshake complete` 0, byebye 0, `FATAL EXCEPTION` 0. Phone `Critical error` 0.

The review's concern is confirmed. On SURFACE the surface is lost on every cover, `surfaceDestroyed` stops the decoder (reason `surfaceDestroyed`, not `activity_stopped`) and the change does not help. The picture was black (luma 0) at `r1`, about 1.1 s after BACK, and back by `r2`.

## R5

**PASS** (the defect reproduces in the log)

- Build B installed, `R5.state` commit `afe6a5348119`. Settings `$BASE int:view-mode=1`, read back. `ssl=0`, `live=2`, `MATCH` 0. 5 valid cycles.
- Per cycle: `Decoder stopped: activity_stopped` 1 in every cycle (needed at least 3); `Configuring decoder: ` 1; `First frame rendered (hardware decode)` 1; `onResume` 1; `keyframe reached the codec` 1; back to keyframe decoded 2687 / 2678 / 2667 / 2664 / 2663 ms; `rend` of the cover window 1.
- Luma pre / r1 / r2: c1 219/220/219, c2 221/221/221, **c3 221/0/0**, c4 221/221/221, c5 221/221/221.
- Luma `r1` below half of `pre`: **1 of 5** cycles on B (c3), and the same for `r2` (c3). On C (R1): **0 of 5** for both.
- `ALL`: `SSL handshake complete` 0, byebye 0, `FATAL EXCEPTION` 0. Phone `Critical error` 0.

## X

**PASS**

C reinstalled (`24dd22194ce9`, md5 `e7a65be3`), `put` with no keys, `restored-D-HU.xml` identical to `backup-D-HU.xml`. D-MOTO out of airplane mode, wifi and Bluetooth read back enabled.

## Report back

1. R1: 5 valid cycles; in all 5 `Decoder stopped: ` and `Configuring decoder: ` were 0 and luma `r1` held (225 against 225).
2. R5 against R1: luma `r1` fell below half of `pre` in 1 of 5 valid cycles on B and 0 of 5 on C. The luma gauge is weak (see Setup notes): on B the log shows a decoder rebuild in 5 of 5 and a repaired keyframe 2.66 to 2.69 s after BACK; C had none.
3. R3 and R4: GLES kept the decoder in 3 of 3 cycles. SURFACE stopped it in 3 of 3 cycles, reason `surfaceDestroyed`, with a black frame at `r1`. R2: `Configuring decoder: ` 1 per cycle, 66 to 81 ms after the release line.

## Anything the brief did not ask about

- On C with TEXTURE, a cover longer than 20 s gives no benefit: the next frame rebuilds the codec 66 to 81 ms after the release, and the repaired keyframe still lands about 2.65 s after BACK, the same as on B.
- On C with SURFACE, which is likely the common backend on real units, the change does nothing for a 6 s cover. Return to a picture took about 1.2 s there, against about 2.7 s on B with TEXTURE. These are different view modes, so they are not an A/B.
- Luma from a screenshot cannot grade the black flash the change targets: TEXTURE keeps its last frame, so a rebuilt decoder looks fine to a screenshot. A frame-timing or `keyframe decoded` gap is the better gauge.
- D-POCO's Gearhead was not running 60 s after reboot; sessions formed anyway (`ssl=0` in every run).
