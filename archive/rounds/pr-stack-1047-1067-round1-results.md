# pr-stack-1047-1067, round 1 results

**Candidate:** the merged stack, local branch `arm-S`, merge commit `4f10e12578b87465804a71af488976282f5f70d7`, tree `2c4b06fd2c77cc8a25bcb88f775b5d51ec12d1ad` (head `e4116c17` with `e6b24905` and `abd732c3` merged in, on main `77914f18`)
**Baseline:** none by design (the brief's section 2)
**APK md5:** `ed9dc2c9b9f2f17f04a6a69305c53274` (local build and a real `adb pull` of the installed APK agree, on D-HU and again on D-POCO)
**Unit:** D-HU (UNISOC MT50, Android 14) as head unit with D-POCO (POCO X3, Android 15, Gearhead `17.9.664004-release`) as phone for Stage W; D-POCO as head unit with the dongle and D-MOTO as its phone for Stage U
**Date:** 2026-10-08
**Evidence:** release `rig-evidence-pr-stack-1047-1067`, asset `pr-stack-1047-1067-round1-captures.zip`, sha256 `dbdb198b33157c78ad90b7100983c9b8193de8057afd2090b63ba7ad649b5865`

## Setup notes

**Verdicts as graded.** R0 PASS, A1-S PASS, K-S FAIL, XS FAIL, US PASS, CR not exercised. Both FAILs are one literal condition each and the behaviour the runs exist to measure is met in both; see their sections. They are left as FAIL for the operator to override, because the conditions are the brief's and the host did not rewrite them.

**Build and gate.**
- The five head SHAs were fetched and matched `ls-remote` in order; no missing object, no moved head. Both merges were clean and the tree printed `2c4b06fd2c77cc8a25bcb88f775b5d51ec12d1ad`. The merge commits stayed local.
- The scratch worktree had no `local.properties`, so the first build failed in 14 s on "SDK location not found". I copied the main worktree's file in. Second build: success.
- `build_hur.sh` has no worker flag; the brief's `--max-workers=2` was applied as `GRADLE_OPTS=-Dorg.gradle.workers.max=2`. Host was cooled to 61C first. Build thermal log: max 85C, `throttle_pkg` 0.
- No unit tests run (the brief says so). `adb install -r -d`, no uninstall.

**Identity gate (R0).** Symbol counts in the installed APK's DEX: `SettingsRestart: route=` 1, `AapMessageReassembler` 13, `ConnectionAdmissionRejectedException` 2, `AutomaticReconnect` 10, `encrypted write failed or incomplete` 1. `ACTION_QUERY_STATE` reported `commit":"4f10e12578b8"`. D-HU's `settings.xml` was diffed against the backup after the install: no difference.

**Pre-flight** (`rig_preflight.sh`, before anything touched a device; roles D_HU, D_POCO and D_MOTO, wifi and bt needed on the first two):

```
ROLE     ADB      WIFI   BT     SCREEN    BT-PROFILES
D_HU     ok       1      1      Awake     HFP:none A2DP:none
D_POCO   ok       1      1      Awake     HFP:none A2DP:none
D_MOTO   ok       1      1      Awake     HFP:11:46:03:10:33:59 A2DP:none
PREFLIGHT OK
```

**Quirk files read:** `rig-quirks/topics/tooling.md`, `rig-quirks/units/D-HU.md`, `D-POCO.md`, `D-MOTO.md`. The template without sections 7b and 8.

**Who ran what.** No `rig-executor` agent was used. The host ran every command and script in the foreground or as a watched background script, because the round is scripted and the brief's captures are large. The build ran as a background process with a thermal watch.

**Scripts.**
- Used as they were: `build_hur.sh`, `apk_identity` pattern by hand, `pr1045_reset.sh`, `set_hu_prefs.sh`, `pr1045_helpers.sh`, `pr1045r2_helpers.sh`, `pr1045_extract.py`, `pr1045r2_extract.py`, `sd_lib.sh`, `sd2_lib.sh`, `tp_extract.py` (from the pr-1067 round 2 folder), `rig_thermal.sh`, `rig_preflight.sh`, `rig_cleanup.sh`.
- Saved from the replaced round's brief, with the capture folder changed to `$HOME/prstack-captures`: `pr1045r3_helpers.sh`, `load3.py`. Saved from this brief: `stack_lib.sh`.
- Added this round: `ks_run.sh` (K-S as one background script with a heartbeat file), `xs_run.sh` and `xs_run2.sh` (XS), and the folder `pr-stack-1047-1067-round1/` with `pr_stack_stageU.sh`. The folder carries copies of the pr-1047 round 2 libs (`ohu_lib.sh`, `ohu_setkeys.py`, `ptr_lib.sh`, `lib1047.sh`, `hu_put.sh`); `lib1047r3.sh` was not needed. `ohu_setkeys.py` printed 0 for `grep -c 'if v else \[\]'`, so the one-line fix was applied to the copy (now 1).

**Stage W preparation.**
- D-POCO was cleared with `headunit://exit` and force-stop and left on its home screen; D-MOTO's Bluetooth was switched off (`dumpsys` read `enabled: false`) and switched back on at the end (`enabled: true`).
- D-HU `shared_prefs/` is `drwxrwx--x u0_a176 u0_a176`, `settings.xml` `-rw-rw---- u0_a176 u0_a176`: owner correct, no chown needed.
- Audio keys quoted before any write: `use-aac-audio` false, `audio-latency-multiplier` 8, `audio-queue-capacity` 20, `view-mode` 2, `wifi-direct-band` 1. Not written. `connection-modes` held `wifi` and `self`.
- `enable-audio-sink` read **false**; written **true** as the brief says, for every run. `sync-media-session-aa-metadata` written true. `aa-exit-action` read 0 in the stage backup.
- **Title check needed a hand step (D-POCO).** Nothing was playing (every media session `state=NONE`). I launched VLC, which showed its first-run onboarding. At your direction I switched to Spotify: one injected tap on its mini-player play button (the media keys alone left it paused), then `playing_title` printed one line. D-POCO taps this round: 1 play tap, 1 chooser tap in the first K-S run (below), and the 2 XS taps on D-HU's projected picture.

**Rig defects of my own, both found and fixed in the round.**
1. **Stray logcat readers.** After A1-S the two logcat readers (D-HU and D-POCO) were not killed by `cap_stop` and ran 20 minutes, appending into the A1-S files and loading both units during the first K-S run. I found them with `ps` after K-S, killed them by pid, and trimmed copies of the A1-S captures at 22:53:15 (`A1-S.trim.*.logcat`) reproduce every graded number: 1 quit, 2 `SSL handshake complete. ` lines (one INFO, one `Handshake:` form), 0 `invalid framing`, 0 `MATCH! Starting AapService`, 1 `createGroup SUCCESS`, 4 phone `Critical error`. The zip carries the trimmed A1-S files, not the 204 MB polluted ones. K-S was voided and rerun once; later runs used scripts that refuse to start with a stray reader and report the count afterwards (0 each time).
2. **Navigation chooser.** The helper's `google.navigation:` intent raised an "Open with Maps / Open Headunit / Waze" chooser on D-POCO. In the first K-S run I tapped "Just once". In the rerun `nav_start` was overridden with `-p com.google.android.apps.maps`, no tap needed (Maps `NavigationService` was running).

**XS target deviation.** The recipe taps `60,665 60,665 240,110` opened the Gearhead launcher and closed it again: the bottom-left icon is the launcher grid, then the dashboard toggle, so the third tap missed the Exit tile (`native_focus=0`, run 1 INCONCLUSIVE). Rerun: tap `41,680` (grid), screenshot `XS.grid.png`, then tap `252,85` on the Exit tile found in it. 2 taps in the rerun, 3 in run 1, both within the 5 per run cap. Pre-tap targets were checked on `XS.pre.png` only as far as the picture allows (the grid icon was visible; the Exit tile exists only once the grid is open).

**Stage U.** The first attempt was stopped at 23:44:40: D-POCO's `/sys/bus/usb/devices` held only the two root hubs and the capture had no `USB Intent` line, so the dongle had never enumerated (`US no USB session at start`). After a reseat `1-1` was present; the rerun is the graded one. Nothing in the first attempt is graded; its files are in `stageU/attempt1/`. D-POCO had been left in airplane mode by the XS script, so its radios were restored first (airplane off, WiFi and Bluetooth on) and wireless adb was re-established; the first cue (plug the dongle) was already satisfied at the rerun.

**Final steps, quoted.**
- D-HU: app force-stopped; `video-profile-starvation-cap` reads ABSENT; the stage backup was restored as root and `cmp` against it is identical (`settings-final.xml` vs `settings-backup-D_HU.xml`); `enable-audio-sink` reads false again; `log-level` reads 2.
- **`aa-exit-action` does not read ABSENT after the restore**: it reads `value="0"` because the original file holds it. The brief's check cannot hold on a faithful restore; XS itself ended with it ABSENT (written before the restore).
- D-POCO: `headunit://exit` and force-stop, then `pocoput` of its backup; the key sets are identical (28 keys vs 28, no value differs). The raw `diff` shows 11 lines, all the XML header and an empty-string element formatted differently. **The stack APK (`ed9dc2c9...`) is still installed on D-POCO**, replacing whatever it had; it is the build this round measured, not the export build.
- D-POCO is back on its PC cable on USB adb (wireless adb disconnected), D-MOTO Bluetooth on, no logcat or watcher left (`ps` count 0), `rig_cleanup.sh` found only a Gradle daemon from my build (killed), then `rig_preflight.sh` printed `PREFLIGHT OK`.
- Host thermal: A1-S max 67C, K-S 69C, throttle delta 0 on every run.

## R0: gate

**PASS**

- Tree `2c4b06fd2c77cc8a25bcb88f775b5d51ec12d1ad`, as stated.
- All five symbol counts 1 or more (1, 13, 2, 10, 1); `main` has 0 of each by the brief.
- md5 `ed9dc2c9b9f2f17f04a6a69305c53274` from a real pull.
- Install was `adb install -r -d`, never an uninstall. Settings delta after the install: none.

## A1-S: album art over the new copy path, VERBOSE

**PASS**

- Settings written: `log-level` 0, `enable-audio-sink` true, `sync-media-session-aa-metadata` true on top of the stage backup; `video-profile-starvation-cap` read back absent. Radios: phone airplane on then off, WiFi and Bluetooth on (clean-run protocol in `begin_wireless`).
- Discard rules: clean (`MATCH! Starting AapService` 0, one `createGroup SUCCESS`, `Magic Garbage detected in header` 0, one INFO `SSL handshake complete. `).
- Reachability: `complete_runs` **29** (need 10), `runs_with_3plus_fragments` **29** (need 1).
- `zero_list_total` **0**. Window `AapTransport quitting` 0; the one quit is `finish`'s (`quits`: 1 x `clean=false`).
- `tls_zero`: all six counts 0.
- Title check: `A1-S.titles.txt` `phone: com.spotify.music | Lonely Day, System Of A Down, Hypnotize`, `app:   Lonely Day, System Of A Down, Hypnotize`; the phone title was read from the `phone:` line.
- Phone: `Critical error` **4** (limit 1 to 4), `Waiting for ack timeout` 0, `VIDEO_ACK_TIMEOUT` 0, `Received out of order ping response` 0, no protocol refusal (`Multiple media configs` 0).
- Throughput: 47 windows, rendered 29 fps median, fed 29, dropped 0.
- **P** (round 2's `tp_extract.py` on `A1-S.hu.logcat`): `produced0_M` summed over `sessions[]` = **0**; `all.produced_any_M` = **17766**, so the path was reached. All zero means the phone sent no TLS-only record, so `AapTlsWriter.sendControl` did not run.

## K-S: wireless load soak, 10 minutes, behind the load gate

**FAIL**

The FAIL is condition 9's count alone: the phone shows 5 `Critical error` lines against the brief's cap of 4. Every condition that decides the stack's load behaviour is met.

- Settings: stage backup plus `enable-audio-sink` true, `sync-media-session-aa-metadata` true; `log-level` 2; starvation cap absent. Radios as in A1-S. Navigation on D-POCO via the Maps-package intent, music playing, continuous pans, a track skip every 40 s.
- Discard rules (rerun): clean.
- Gate: `gate1 {"throughput_windows": 13, "rendered_fps_median": 29, "fed_fps_median": 29, "video_kBps_median": 175.5}` then `LOAD-OK` at 23:12:22. No gate retry.
- Reachability: `throughput.windows` **121** (need 110); `fed_fps_median` **28** (need 25); skips **14** (need 12); `inbound.audio_nonzero_fraction` **1.0** (need 0.8).
- 1. `zero_list_total` **0**.
- 2. Window `AapTransport quitting` 0; one INFO SSL line (`file_counts` 1).
- 3. `rendered_fps_median` **28** against fed **28** (need 0.95 x fed); `dropped_sum` **0**; `dispatch.videoShed_sum` **0**.
- 4. `rendered_fps_median_by_third` **[29.0, 26.0, 27]** (third 2 is 27 against 0.90 x 29 = 26.1, met); `longest_zero_rendered_run_windows` **0**; inbound video by third [56, 42, 46], third 2 over third 0 is 0.82, above the 0.70 content-change threshold, so the first half is graded, not INCONCLUSIVE.
- 5. `TOTAL PSS` 97980 to 106003 KB: growth **8.0 MB** (limit 61 MB).
- 6. `inbound_audio_kBps_median_by_third` **[187, 187, 187]**.
- 7. `audio_timing_non_audio_channel` **0**.
- 8. Titles equal: `Solo Tú, Dueles, Diamante Eléctrico, Vicente Garcia, Buitres & Co.` on both lines.
- 9. Phone: `VIDEO_ACK_TIMEOUT` **0**, `Waiting for ack timeout, video frame dropped` **0**, `Received out of order ping response` **0**; **`Critical error` 5, over the cap of 4**, none naming a protocol refusal (`Multiple media configs` 0). The five lines, phone clock:

  ```
  23:22:29.579 W/GH.WirelessStartup(27676): Critical error encountered, attempting to handle result.
  23:22:30.951 W/CAR.SERVICE(27676): Critical error 4 detail: 34 msg: reason:1
  23:22:31.013 W/CAR.SERVICE(27676): Critical error 3 detail: 52 msg: io error
  23:22:31.013 W/CAR.SERVICE(27676): Critical error 3 detail: 50 msg: io error
  23:22:31.049 W/CAR.SERVICE(27676): Critical error 18 detail: 54 msg: Failed to read message
  ```

  The first is the phone's wireless-startup logger reporting the same teardown that the car asked for: in the voided first run the lines just before it read `received ByeByeRequest`, `Protocol error 4 (34): reason:1` and `FailedUserDecision(PROTOCOL_BYEBYE_REQUESTED_BY_CAR)` (23:06:51.629 to .640). The four `CAR.SERVICE` lines are the ones A1-S also shows. A1-S had no `GH.WirelessStartup` line, so its count is 4. Both K-S runs read 5, so the extra line is not an artefact of the stray readers.
- 10. `tls_zero` all six counts **0**; `quits` shows one quit, at `finish` (23:22:30.203, `clean=false`); `AapTransport: ByeBye write SENT` once, at `finish` (23:22:30.201).
- Soak window: `K-S-start` 23:12:22.413 to `K-S-end` 23:22:27.061; `load3.json`: 121 windows, rendered 28, fed 28, video 46 kB/s median.

**The voided first run (`K-S-run1`)**, graded VOID because two stray logcat readers ran against both units: windows 121, rendered 29, fed 29, thirds [29, 29, 28], PSS 96282 to 111038 (+14.7 MB), 15 skips, `tls_zero` 0, ack timeouts 0, `Critical error` 5. Same shape, kept in the zip.

**Merge question:** under this load the stack costs no frame rate (28 to 29 fps held), no credit (video and audio thirds do not decay beyond the 0.82 content drift), and no TLS failure. The one miss is a log-count bound that this stack reproducibly reads as 5.

## CR: credits never leak

**INCONCLUSIVE**

Not exercised for reassembler drops: `AapRead: skipped message:` printed 0 times in A1-S, K-S and K-S-run1, and `skipped_messages` is empty in each. Pre-registered by the brief; this is the expected reading, not a leak.

## XS: one Exit in Android Auto ends the stack's session as a user exit

**FAIL**

One literal miss: `quits` shows `clean=false` for the Exit's quit; the brief requires `clean=true`. Every behaviour condition is met, and so is the round-1 failure shape's absence.

- Settings: stage backup, then `aa-exit-action` deleted (`ABSENT`), `enable-floating-button` false, `native-poke-all-paired` true, `native-driver-selection-mode` 0, `enable-audio-sink` true, `sync-media-session-aa-metadata` true, `log-level` 2, `wifi-connection-mode` 3; `settings.xml` parsed as valid XML. Radios: phone airplane mode on, launch, `form_session` printed `session toggle=0` (no Bluetooth toggle needed), phone `Wi-Fi is enabled` and `enabled: true`.
- Discard rules: clean (`MATCH! Starting AapService` 0, one `createGroup SUCCESS`, `Magic Garbage detected in header` 0, one INFO SSL line).
- Valid: `throughput_before` **4**, `phone_aa_before` **1709**, `native_focus` **1**.
- `grade_exit`: `disconnect` **1**, `state_user_exit` **1**, `state_link_lost` **0**, `user_exit` **1**, `minimize` **0**, `accepted_after` **0**, `phone_byebye` **4**, `throughput_after` 3 (windows after the Exit, not a condition in this brief), `front_after` `MainActivity` (the launcher), `aa-exit-action` ABSENT afterwards.
- `tls_zero`: all six counts 0.
- Decisive lines, head unit clock:

  ```
  23:27:55.705 Video Focus NATIVE received. User clicked Exit in Android Auto.
  23:27:55.710 ExitAction: Disconnecting projection session
  23:27:55.715 AapTransport: ByeBye write SENT
  23:27:55.716 AapTransport quitting (clean=false)
  23:27:55.721 AapService: session state disconnected (user_exit)
  23:27:55.762 AapService: Native AA user exit. Stopping active launcher.
  ```

- Phone (graded): `phone_byebye` 4, with `received ByeByeRequest` at 23:27:55.052 on the phone clock.
- **The miss.** `AapTransport.quit(clean)` is called with `ret == -2` on a read loop's EOF, or `clean || peerRequestedClose` inside `quit`; a stop that this side initiates calls `quit()` with the default `false`. Here the head unit chose the disconnect and sent the ByeBye first, so `clean=false` is what this code does for it. A1-S, K-S and US also end on `clean=false` (their quits are `finish` or a detach). The host therefore reads the brief's `clean=true` as an expectation the code does not make, but it is the brief's stated condition and it did not hold, so the verdict stays FAIL until the operator overrides it.
- What the FAIL shape of the earlier round would have been (`state_link_lost` 1 and the phone back about 1 s later, `accepted_after` 1 or more) did not occur: `state_link_lost` 0, `accepted_after` 0, so the stack kept the user-exit fix.

**Run 1 (`XS-run1`) was INCONCLUSIVE**: `native_focus` 0, `disconnect` 0, `phone_byebye` 0, `throughput_after` 11 and the projection still in front (`AapProjectionActivity`). The recipe's taps did not reach the Exit tile (Setup notes). 3 taps.

## US: unplug the dongle during a USB session

**PASS**

- Settings: D-POCO `U2KEYS` through `pocoput` (`wifi-connection-mode` 1, `connection-modes` usb and wifi, `auto-connect-last-session`, `auto-start-on-usb`, `reopen-on-reconnection` true, `use-libusb` false, `log-level` 2, `onboarding-version` 2, `kill-on-disconnect` false; the starvation key did not print). D-POCO's own port table: no listener on 5277. D-MOTO Bluetooth `enabled: true state: ON`. USB permission dialog: 0 shown. Stack APK installed on D-POCO with `install -r -d`, identity counts 1, 13, 2, 10, 1, `commit":"4f10e12578b8"`.
- Discard rules: n/a for this run's evidence beyond the rows below; one session at the start, one after the replug.
- Valid: `session_before` **1**, `detach` **2** (two receivers log the one unplug).
- The row, as printed in `US-S.tsv`:

  ```
  US	session_before=1	detach=2	write_failed=0	quit=1	fatal_read=0	destroy=0	replug_session=1	t_replug_ssl=10774	fatal=0
  phone_critical=4
  ```

- `quit` **1**, between the unplug and 15 s after the detach: `23:46:27.012 USB Intent: ... USB_DEVICE_DETACHED` then `23:46:27.029 AapTransport quitting (clean=false)`, 17 ms later. `destroy` **0** (`AapService destroying` is first seen at 23:47:04, after the run's own exit). `replug_session` **1** with `t_replug_ssl` **10774 ms** (limit 60000): attach `23:46:51.265`, `SSL handshake complete` `23:46:53.939`. `fatal` **0**.
- Reported as numbers: `write_failed` **0** and `fatal_read` **0**. Neither a failed write nor a fatal read ended the session; the detach event did, through the quit path. So the claim this run was written for, that a failed write ends the transport through `quit()` once, was **not exercised**: no write failed. The graded conditions are met and say nothing more than that.
- Phone (D-MOTO, not graded): `Critical error` 4.
- Early flapping noted: in the 6 s after launch, with the dongle already on the bus, the capture shows a detach and two attaches (23:45:32 to 23:45:36) before the first session at 23:45:40, the dongle's own re-enumeration; it is before the graded window.

## Summary

| Run | Verdict |
|---|---|
| R0 | PASS |
| A1-S | PASS |
| K-S | FAIL (count only, condition 9) |
| CR | INCONCLUSIVE (not exercised, as pre-registered) |
| XS | FAIL (one literal miss: `clean=false` against the brief's `clean=true`) |
| US | PASS (write-failure path not exercised) |

For the merge question the brief names (K-S): the stack held 28 to 29 fps under pans and a track skip every 40 s for 10 minutes, 0 dropped, 0 shed, 0 phone ack timeouts, PSS +8.0 MB, and 0 TLS or write failures in every run. Both FAILs are one-line mismatches against the brief's own expectations that the host believes are the brief's, not the stack's; the operator decides.

## Anything the brief did not ask about

- **`AapTransport quitting (clean=...)` reads `clean=false` for every quit this round**, whether the head unit sent the ByeBye (XS, `finish`) or a USB detach ended the session (US). The only quit that reads `clean=true` in this code is a read EOF of `-2` or a peer-initiated close. The brief's XS condition expected `clean=true`.
- **`AapTransport: ByeBye write SENT` precedes the quit by 1 to 2 ms** on every ended session (23:27:55.715 then .716), so `ByeBye write` and `quitting` cannot be told apart by ordering.
- **The phone's Critical error count is 4 or 5 depending on whether Gearhead's wireless-startup logger also fires.** A1-S: 4 (no `GH.WirelessStartup` line). K-S: 5 twice. The brief's 1 to 4 bound was calibrated on an earlier round's single reading of 3.
- **D-POCO's Android Auto launcher layout moved**: Exit is the first tile at (252, 76) in a grid of 4 columns, and the bottom-left icon is the dashboard toggle once the grid is open. The recipe's third tap (240, 110) lands on the Spotify card on the dashboard.
- **VLC on D-POCO has never been set up** (first-run onboarding). Spotify with a queue is the working player for the title checks.
- **The `google.navigation:` helper intent raises a chooser on D-POCO** because the installed headunit app registers for that scheme. A package-pinned intent avoids it.
- **Cleanup trap in the helpers:** `cap_stop` did not kill the A1-S readers when the stage was run from the tool shell, but killed every later reader when the same functions ran inside a script. Cause not chased; the scripts now count readers before and after.
- **A Gradle daemon from the build outlives the build** and fails `rig_preflight.sh` until `rig_cleanup.sh kill` removes it.

## Operator ruling (2026-10-09)

Both FAILs are errors in the brief, not in the stack. Final verdicts: R0 PASS, A1-S PASS, **K-S PASS**, CR INCONCLUSIVE (not exercised, as pre-registered), **XS PASS**, US PASS.

- **K-S, condition 9, overruled to PASS.** All five phone `Critical error` lines fall at the finish teardown, after `K-S-end` (23:22:27). Four are the `CAR.SERVICE` lines A1-S also shows. The fifth is `GH.WirelessStartup: Critical error encountered`, a second Gearhead logger reporting the same car-requested ByeBye. The brief's grep matched that logger too, so the cap of 4 counted the pattern, not a fault. None names a protocol refusal. The soak conditions that decide the merge question all held.
- **XS, `clean=true`, overruled to PASS.** In this code only `AapControl.byebyeRequest`, the phone-initiated ByeBye, calls `quit(clean = true)`. An Exit in Android Auto ends the session from the head unit side (`ExitAction: Disconnecting projection session`, then our own `ByeBye write SENT`), so `quit()` runs with the default `false`. The same holds on `main` `77914f18`. The brief's expectation was wrong. The conditions that decide the run held: `state_user_exit` 1, `state_link_lost` 0, `accepted_after` 0, so the stack kept the user-exit fix.
- **Not measured by this round:** the failed-write path of `quit()` (US ended on the detach, `write_failed` 0), reassembler credit leaks (CR), the Nearby route (Android Auto 17.9 refuses the Wireless Helper), and every Save and Disconnect claim of the session-reconnect PR. The last one moves to `pr-1047-session-reconnect-round3-brief.md` with its addendum, on this same tree.
- **For later briefs:** the settings-defaults Exit taps no longer match D-POCO's Android Auto launcher. Pin the navigation intent to `com.google.android.apps.maps` on D-POCO.
