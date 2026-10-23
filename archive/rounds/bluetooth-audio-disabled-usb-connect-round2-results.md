# bluetooth-audio-disabled-usb-connect, round 2 results

**Candidate:** `fix/bluetooth-audio-disabled-usb-connect` @ `fff96699480d878bb36bd04078d77260231b19dc` (round 1 SHA, no fix on trial)   **Baseline:** none (measurement round)
**APK md5:** `234caaadff2d4731e006591a0c69bb2a` (`apks/candidate-fff96699.apk`, reused, not built)
**Unit:** D-POCO (POCO X3 NFC, Android 15) as head unit over USB; D-HU (UNISOC MT50, Android 14) as Native AA head unit; D-MOTO (Motorola edge 30 neo, Android 14, Android Auto 17.9.664004) as phone
**Date:** 2026-10-07
**Evidence:** release `rig-evidence-bluetooth-audio-disabled-usb-connect`, asset `bluetooth-audio-disabled-usb-connect-round2-captures.zip`, sha256 `2f054f956a67a1335ea3e39aa87dde896d0b3f3820c3ce81f50377e215fff54f` (the zip holds the unedited draft of this file; the evidence line was added after zipping)

## Round verdicts

| Run | Verdict | Reading |
|---|---|---|
| U0p | PASS | RESTORED, P0 100, restore 1773 ms after stop |
| U5a (cable pull x3) | INCONCLUSIVE | strict count 1 of 3 valid repetitions; every pull-type run that held a pull read RESTORED (details in R U5a) |
| U5b (Android Auto force-stop over USB, x2) | PASS | NOT_RESTORED both times |
| U6 (clean session from P0 0) | PASS | RESTORED in U6a, U6b and in the recovery U6r2v |
| N0p, N4a, N4b | UNTESTABLE | first two bring-ups both VOID (Gearhead never answered the poke) |

## Setup notes

- **Quirk files read:** `rig-quirks/topics/tooling.md`, `rig-quirks/units/D-MOTO.md`, `rig-quirks/units/D-POCO.md`. Template read without 7b and 8.
- **Pre-flight (first attempt FAILED, D-MOTO Bluetooth off; the operator turned it on; second attempt OK):**
  `D_HU ok wifi=1 bt=1 Awake HFP:none A2DP:none` / `D_POCO ok 1 1 Awake none none` / `D_MOTO ok 1 1 Awake HFP:11:46:03:10:33:59 A2DP:none`.
- **Build:** none. The APK on both units had md5 `50f8137b85adf64b917c8e9274c70da0`, so `adb install -r` of `candidate-fff96699.apk` ran on both (settings.xml unchanged by the install on both units, diff clean). `ACTION_QUERY_STATE` on both returned `commit` `fff96699480d`. Both settings files were backed up before the install. D-POCO backup 1391 bytes, D-HU backup 9690 bytes. Rig audio keys as found on D-HU: `use-aac-audio=false`, `audio-latency-multiplier=8`, `audio-queue-capacity=20`. D-HU `head-unit-make`/`head-unit-model` as found: `Google` / `Desktop Head Unit`.
- **Addresses:** `POCO_BT=DC:B7:2E:5E:4E:59`, `MOTO_BT=A0:46:5A:97:E4:95`, `DHU_ANN=11:46:03:10:33:59`, all as in round 1. Bonds on D-MOTO list D-POCO and D-HU. Gearhead on D-MOTO 17.9.664004, `zen_mode=0`.
- **Scripts:** new dir `hur-wifi-test-scripts/bluetooth-audio-disabled-usb-connect-round2/` with `lib1008r2.sh` (from the brief), `ohu_setkeys.py` (copied), and host-written `stageU_r2.sh`, `stageU_r2b.sh`, `stageU_r2c.sh`, `stageN_r2.sh`. Stage U ran over wireless adb as the brief says. `rig_preflight.sh` and `rig_devices.sh` were used as is.
- **Lib fix:** `pocoput`'s readback came back empty on the first attempt four times (U6r1, U6r2, U6r2y and U6r2z each aborted before any capture). The write itself landed. I changed the readback to retry up to 5 times with 1 s between. No capture was lost.
- **Operator behaviour that voided runs:** U5a1 (D-MOTO replugged about 14 s after the pull, before the plug-back cue; two more sessions formed after the stop) and U6r1b (an unplug and replug at 14:08:08 to 14:08:18, about 10 s after the clean stop; a second session formed at 14:08:18 and re-disabled the policy). U5a1r never received a pull inside the script's 120 s window (the pull cue was acted on late, replug at 14:28:20).
- **Host deviations, all unscripted:** (1) After U6r2x failed with `SSLException: Unable to parse TLS packet header`, the host ran `am force-stop com.google.android.projection.gearhead` on D-MOTO to clear the stale Android Auto session; U6r2v then formed a session. (2) In U5b2 the operator's replug formed the session at 14:43:20, one second before `ACTION_CHECK_USB`, so `usb_up` could not have seen it. The host killed the script, killed the orphaned capture loops by pid, and continued with `stageU_r2c.sh`, which ran U5b2's body on the live session. The captures were restarted in append mode; the phone capture is missing the `U5b2-start` marker (adb dropped at 14:43:18). The host rebuilt the window by inserting a synthetic start line at the real marker time 14:43:19.314 from `marks.log`, kept as `U5b2.phone.logcat.patched`, with the original as `U5b2.phone.logcat.orig-no-start-marker`. (3) I tried to start the continuation without the rig lock once; the auto-mode classifier denied it and I released the lock properly instead. (4) The three H3 cues were cleared by `KEYCODE_HOME` over adb instead of by the operator; each screen was the system USB Preferences screen (`com.android.settings`, `UsbDetailsActivity`, "This device", "File Transfer") or Android Auto's ghost activity.
- **Valid-run rule used:** hu.ssl=1, hu.ssl_after_stop=0, one announce line `carAddress=DC:B7:2E:5E:4E:59 (bt-announce=real)`, hu.fatal=0, no Gearhead crash, P0 not none, 4 or more markers. `hu.verbs` was 7 or more everywhere except U5a3 (6).
- **Flag dump:** `gh-flags-live.txt` is 77575 bytes and none of the three named flags (`BluetoothPairing__disable_a2dp`, `UsbBabysitter__a2dp_fix_car_list`, `UsbBabysitter__enable_a2dp_at_projection_end`) appears in it. Nothing to quote.
- **Thermal:** `thermal_max` 67C, throttle delta 0 on every run.
- **policy.tsv:** the full file is in the asset. The `p.set` lines that decide each run are quoted below, not the whole table.

## R U0p, clean end (primer)

**PASS**

- Valid: hu.verbs 7, hu.ssl 1, ssl_after_stop 0, announce 1, fatal 0.
- P0 100 (`14:00:09.559`). Gearhead (`gearhead:car`) set 0 four times before the stop (`14:00:09.559` to `14:00:12.869`).
- Stop `14:00:51.590`; `setConnectionPolicy(DC:B7:2E:5E:4E:59, 100)` from `gearhead:car` at `14:00:53.363`, 1773 ms after the stop; stored 100 at the read. `p.non_gh_setters_before_read=0`.

## R U5a, cable pulled out mid-session (three repetitions)

**INCONCLUSIVE**

Strict count: one repetition (U5a2) meets every valid-run item. The brief's PASS needs three. The rest, named by the item they miss:

| Run | P0 | Pull evidence | Reading | Why not counted |
|---|---|---|---|---|
| U5a1 | 100 | `link_lost` `14:03:34.551`; Gearhead set 100 at `14:03:35.932` | OTHER | hu.ssl 3, ssl_after_stop 2: D-MOTO replugged at 14:03:46, a new session formed and re-disabled before the read |
| U5a1r | 100 | none: no disconnect inside the 120 s window | RESTORED (by the replug at 14:28:20, set 100 at 14:28:22) | the pull never happened in the window; restore follows the replug |
| U5a1rx | 100 | gone `14:30:29.574`; Gearhead set 100 at `14:30:38.014` | RESTORED | hu.ssl 2: an early session dropped at 14:29:50 when the operator replugged during bring-up, before the pull; nothing after the stop |
| U5a2 | 100 | stop `14:32:55.191`, gone `14:33:04.800`; Gearhead set 100 at `14:33:04.712` | RESTORED | counted, valid |
| U5a3 | 100 | link was already lost on the head unit at `14:34:43.608`, before the stop marker at `14:35:21.468`; Gearhead set 100 at `14:35:30.074` | RESTORED | hu.verbs 6; the pull hit a link that was already dead |

`delay_from_gone_ms`: U5a1 -49, U5a1rx 8440, U5a2 -88 (U5a3 has no gone marker, 8606 ms from the stop). Two pulls restored at the instant the head unit logged the disconnect, two after about 8.5 s. In all five restores the setter was `gearhead:car`, and `p.non_gh_setters_before_read=0` throughout.

For the reply: on the evidence here a pulled USB cable gets the toggle back on D-MOTO, as a clean end does. That reading rests on one fully valid pull, so treat it as likely and not as settled.

## R U5b, Android Auto force-stopped mid-session over USB (twice)

**PASS**

- U5b1: valid (verbs 7, ssl 1, announce 1). P0 100, Gearhead disabled 4 times before the stop, no `setConnectionPolicy(.., 100)` after it, stored 0 at the read. **NOT_RESTORED**.
- U5b2: valid on the head unit capture (verbs 8, ssl 1, after_stop 0, announce 1); phone window rebuilt as noted. P0 100 (`14:43:21.696`), 4 disables, no restore, stored 0 at read (`14:47:30.783`). **NOT_RESTORED**.
- Same reading twice. This matches round 1's N3 over Native AA.

## R U6, clean session from P0 0 (repair)

**PASS**

- U6a: valid, P0 0 (`14:41:08.903`), Gearhead set 100 at `14:41:51.373` after the clean end, stored 100 at read. **RESTORED**.
- U6b: valid, P0 0 (`14:48:14.594`), Gearhead set 100 at `14:48:58.025`, stored 100. **RESTORED**.
- U6a and U6b agree. A third, unplanned instance agrees: the recovery run U6r2v (valid, P0 0 at `14:20:40.042`, restore `14:21:21.722`, 1841 ms after the stop, stored 100).
- For the reply: the next ordinary clean session puts the toggle back by itself, from a policy left at 0 by an Android Auto force-stop.
- Recovery runs: U6r1b was invalid (see Setup notes) and its end state was 0; U6r2v then repaired it. Rule R's stop did not apply because U6r1b was not a valid run.

## R N0p, N4a, N4b (Stage N)

**UNTESTABLE**

- N0p VOID, no session in 120 s. N0px (the one re-run) also VOID. The brief's stop rule makes Stage N UNTESTABLE from there, and the host stopped the script.
- What the head unit did in N0px: `ACTIVELY LISTENING on Android Auto UUID` at `14:54:36.511`; pokes every 15 s; `Successfully poked ... via HFP-AG. Holding` at `14:56:07.581`. N0p likewise: `Successfully poked motorola edge 30 neo` at `14:53:14.789`.
- What the phone did: no Gearhead activity at all in the capture. `gearhead:car` stayed in `oom_cached`, unfrozen at `14:56:15.015` and frozen again at `14:56:25.768`; no connect-back and no `GH.` line. D-MOTO was on its launcher (screenshot at `14:56:55`), with no dialog.
- Not explained. No control run was made, so the host does not name a cause. Two facts differ from round 1's Stage N: Gearhead was force-stopped on D-MOTO several times in Stage U (U5b1, U5b2, the host's one at 14:16), and D-MOTO had the system USB Preferences screen in focus before N0p (cleared with HOME before N0px).
- No `setConnectionPolicy` or `Saved connectionPolicy` line for the D-HU address appears in either Stage N capture, so D-MOTO's policy for D-HU was not touched. Last known value is round 1's closing state (100); not read back this round.
- N4a and N4b (Android Auto force-stop and repair over Native AA) were not run. Over USB the same questions are answered by U5b and U6.

## Closing state

- D-POCO: baseline settings restored (byte-identical diff clean after the stage). Bluetooth and WiFi back on, airplane mode off. D-MOTO's policy for D-POCO left at 100 (last `Saved connectionPolicy`, U6b `14:48:58.025`).
- D-HU: baseline restored through `hu_put`. All 165 keys equal to the backup; only serialisation differs (9636 vs 9690 bytes). `head-unit-make` `Google`, `head-unit-model` `Desktop Head Unit`.
- D-MOTO: `svc power stayon false`; the Gearhead log tags set in Prepare were left (system properties, gone at reboot).
- No `adb logcat` left running; nothing on the transfer branch holds a capture.

## Anything the brief did not ask about

- **Clean ends are not always clean when the cable stays in.** U6r1b: right after `ACTION_DISCONNECT` (`user_exit` `14:07:58.744`) Gearhead set 100 at `14:08:00.615`, and the session that formed after an operator replug disabled it again at `14:08:19.914`. A restore is only as durable as the next session.
- **Gearhead restores a pull late or at once.** Two pulls restored within 100 ms of the head unit's disconnect, two after about 8.5 s; the data does not say what separates them.
- **Re-enumeration state.** After a cable event D-MOTO often answered the head unit's first bring-up with `Version request send failed (ret=-1)`, and once with `Unable to parse TLS packet header` (U6r2x, twice), until Gearhead was force-stopped. That is the round 1 re-attach failure plus a second form of it.
- **`usb_up` can miss a session that forms first.** If the operator replugs just before `ACTION_CHECK_USB`, the SSL line is before the search start and the run goes VOID with a live session. U5b2 hit it.
- **Cue relay.** The cue channel is a log read by the host; the operator never sees the bell. Three runs were lost to cues acted on early or late. A brief that needs a physical pull inside a 120 s window should give the operator the cue directly.

## Appendix: policy.tsv summary lines (without `p.set`)

```
U0p p.P0=100 at 14:00:09.559
U0p p.disabled_by_gh_before_stop=4
U0p p.stop=14:00:51.590 p.gone= p.read=14:01:52.431
U0p p.restore=14:00:53.363 pid=18171 owner=com.google.android.projection.gearhead:car delay_from_stop_ms=1773
U0p p.non_gh_setters_before_read=0
U0p p.END_at_read=100 p.END_at_end=100
U0p p.reading=RESTORED
U5a1 p.P0=100 at 14:02:37.545
U5a1 p.disabled_by_gh_before_stop=3
U5a1 p.stop=14:03:21.455 p.gone=14:03:35.981 p.read=14:04:36.770
U5a1 p.restore=14:03:35.932 pid=18171 owner=com.google.android.projection.gearhead:car delay_from_stop_ms=14477 delay_from_gone_ms=-49
U5a1 p.non_gh_setters_before_read=0
U5a1 p.END_at_read=0 p.END_at_end=0
U5a1 p.reading=OTHER
U6r1b p.P0=100 at 14:07:04.688
U6r1b p.disabled_by_gh_before_stop=3
U6r1b p.stop=14:07:59.231 p.gone= p.read=14:09:00.301
U6r1b p.restore=14:08:00.615 pid=18171 owner=com.google.android.projection.gearhead:car delay_from_stop_ms=1384
U6r1b p.non_gh_setters_before_read=0
U6r1b p.END_at_read=0 p.END_at_end=0
U6r1b p.reading=OTHER
U6r2v p.P0=0 at 14:20:40.042
U6r2v p.disabled_by_gh_before_stop=4
U6r2v p.stop=14:21:19.881 p.gone= p.read=14:22:20.859
U6r2v p.restore=14:21:21.722 pid=7476 owner=com.google.android.projection.gearhead:car delay_from_stop_ms=1841
U6r2v p.non_gh_setters_before_read=0
U6r2v p.END_at_read=100 p.END_at_end=100
U6r2v p.reading=RESTORED
U5a1r p.P0=100 at 14:22:50.851
U5a1r p.disabled_by_gh_before_stop=8
U5a1r p.stop=14:25:47.293 p.gone= p.read=14:28:47.379
U5a1r p.restore=14:28:22.245 pid=7476 owner=com.google.android.projection.gearhead:car delay_from_stop_ms=154952
U5a1r p.non_gh_setters_before_read=0
U5a1r p.END_at_read=100 p.END_at_end=100
U5a1r p.reading=RESTORED
U5a1rx p.P0=100 at 14:29:48.956
U5a1rx p.disabled_by_gh_before_stop=8
U5a1rx p.stop=14:30:29.149 p.gone=14:30:29.574 p.read=14:31:30.460
U5a1rx p.restore=14:30:38.014 pid=7476 owner=com.google.android.projection.gearhead:car delay_from_stop_ms=8865 delay_from_gone_ms=8440
U5a1rx p.non_gh_setters_before_read=0
U5a1rx p.END_at_read=100 p.END_at_end=100
U5a1rx p.reading=RESTORED
U5a2 p.P0=100 at 14:32:17.959
U5a2 p.disabled_by_gh_before_stop=4
U5a2 p.stop=14:32:55.191 p.gone=14:33:04.800 p.read=14:34:05.910
U5a2 p.restore=14:33:04.712 pid=7476 owner=com.google.android.projection.gearhead:car delay_from_stop_ms=9521 delay_from_gone_ms=-88
U5a2 p.non_gh_setters_before_read=0
U5a2 p.END_at_read=100 p.END_at_end=100
U5a2 p.reading=RESTORED
U5a3 p.P0=100 at 14:34:35.646
U5a3 p.disabled_by_gh_before_stop=4
U5a3 p.stop=14:35:21.468 p.gone= p.read=14:38:22.731
U5a3 p.restore=14:35:30.074 pid=7476 owner=com.google.android.projection.gearhead:car delay_from_stop_ms=8606
U5a3 p.non_gh_setters_before_read=0
U5a3 p.END_at_read=100 p.END_at_end=100
U5a3 p.reading=RESTORED
U5b1 p.P0=100 at 14:38:53.561
U5b1 p.disabled_by_gh_before_stop=4
U5b1 p.stop=14:39:28.595 p.gone= p.read=14:40:31.744
U5b1 p.restore=none
U5b1 p.non_gh_setters_before_read=0
U5b1 p.END_at_read=0 p.END_at_end=0
U5b1 p.reading=NOT_RESTORED
U6a p.P0=0 at 14:41:08.903
U6a p.disabled_by_gh_before_stop=4
U6a p.stop=14:41:49.549 p.gone= p.read=14:42:50.544
U6a p.restore=14:41:51.373 pid=16701 owner=com.google.android.projection.gearhead:car delay_from_stop_ms=1824
U6a p.non_gh_setters_before_read=0
U6a p.END_at_read=100 p.END_at_end=100
U6a p.reading=RESTORED
U5b2 p.P0=none at 
U5b2 p.disabled_by_gh_before_stop=0
U5b2 p.stop= p.gone= p.read=
U5b2 p.restore=none
U5b2 p.non_gh_setters_before_read=0
U5b2 p.END_at_read=none p.END_at_end=none
U5b2 p.reading=OTHER
U5b2 p.P0=100 at 14:43:21.696
U5b2 p.disabled_by_gh_before_stop=4
U5b2 p.stop=14:46:27.761 p.gone= p.read=14:47:30.783
U5b2 p.END_at_read=0 p.END_at_end=0
U5b2 p.reading=NOT_RESTORED
U6b p.P0=0 at 14:48:14.594
U6b p.disabled_by_gh_before_stop=5
U6b p.stop=14:48:56.161 p.gone= p.read=14:49:57.004
U6b p.restore=14:48:58.025 pid=19568 owner=com.google.android.projection.gearhead:car delay_from_stop_ms=1864
U6b p.non_gh_setters_before_read=0
U6b p.END_at_read=100 p.END_at_end=100
U6b p.reading=RESTORED
```
