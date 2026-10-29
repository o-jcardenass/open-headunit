# usb-dialog, round 1 results

**Candidate:** `main` @ `947b3ae9fae9834aa8e4145ee91751ed743c2ae0` (tree `e7112fbfdd820cf4834a8a9a5a47f5b62e3ea165`, built in R0 because `main-smoke` round 1's APK was not on disk)    **Baseline:** none (one arm)
**APK md5:** `77ecb9b400f8a23eedbf93f07f53a869` (`apk-M.apk`; live APK on D-HP and D-POCO matched after `install -r -d`)
**Unit:** stage H: D-HP (HP Slate 7 Plus, Android 4.2.2, API 17) as USB host, D-MOTO (Motorola edge 30 neo, API 34, USB debugging on, Gearhead 18.1.164344) on its OTG port. Stage S not run (D-SAM has no USB host). Stage P: D-POCO (POCO X3 NFC, Android 15, API 35) as USB host, same D-MOTO.
**Date:** 2026-10-27 (stage H 02:21 to 03:02, stage P 08:04 to 08:37)

**Evidence:** release `rig-evidence-usb-dialog`, asset `usb-dialog-round1-captures.zip`, sha256 `82f56b5cc0756f2066ce0ec9e3e93785cdd0f32881a1f30874da5b14f4eec138`.

## Setup notes

- Resolved SHA 9a30d468 -> usb-dialog round 1 (brief-only commit). Came from main; transfer branch fast-forwarded to 2551b40e.
- rig_sync: in sync, no RESTART.
- Read: TESTING-TEMPLATE (without 8), quirks tooling, D-HP, D-SAM, D-SAM-and-D-HP, D-POCO, D-MOTO, TOOLS pick table.
- Pre-flight (D_HP/D_SAM/D_POCO/D_MOTO wifi needed; brief wants D-MOTO bt off): PREFLIGHT OK. Opening table:
  D_HP wifi1 bt1 Awake; D_SAM wifi1 bt1 Asleep; D_POCO wifi1 bt1 Dozing; D_MOTO wifi1 bt0 Asleep. Batteries HP100 SAM100 POCO80 MOTO88. SDK HP17 SAM19 POCO35 MOTO34.
- Brief used role names with hyphens; rig_preflight.sh needs underscores (D_HP).
- main-smoke-round1/apk-M.apk does not exist, so the APK is built here (get_apk.sh else-branch).
- Operator answers: stage P = YES (accepts persistent USB grant on D-POCO); operator present now.
- APK built in /home/oscar/Coding/StudioProjects/worktrees/usb-dialog__scratch__947b3ae9__20261027 (tree e7112fbf verified), md5 recorded in prepare.json
- Prepare 3: D-SAM `pm list features | grep -ac android.hardware.usb.host` = 0 (only usb.accessory listed) -> stage S UNTESTABLE (no USB host), per brief. Its wireless adb skipped.
- D-SAM clock 25 s behind host at 02:17 (D-SAM 02:17:12 vs host 02:17:37 at readout, not simultaneous; recorded only).
- Pre-existing USB state before install: D-HP 5 device preferences naming our package (vendors 6353 and 8888). D-POCO has persistent grants (user_package entries) for motorola edge 30 neo serial ZY22GC3BM4 (3 entries). So on D-POCO our dialog may not stack (brief section 8 variant).
- Prepare: identity check on D-HP and D-POCO: ACTION_QUERY_STATE commit=947b3ae9fae9, pulled live APK md5 77ecb9b400f8a23eedbf93f07f53a869 = apk-M.apk (installed with install -r -d; live md5 had differed on both). Never uninstalled.
- Wireless adb: HP 192.168.1.5:5555, POCO 192.168.1.20:5555, MOTO 192.168.1.13:5555 (wireless.env). D-SAM skipped (UNTESTABLE).
- D-MOTO: bluetooth off read back (enabled: false, state: OFF); stayon true; logcat -G 16M; Gearhead versionName 18.1.164344-release.daily; `svc usb getFunctions` printed nothing, sys.usb.config=adb sys.usb.state=adb. Clock offset <1 s.
- Scripts extracted with extract_brief_blocks.py to scripts/. Host-side patches to the brief scripts: OUT made absolute, wireless.env and rig_devices.sh sourced, placeholders in stage.sh/setlisten.sh implemented with set_prefs_runas_host.py (listed below).
- Prepare step 7: usb-dialog-instrument.sh copied to $EV and chmod +x. Tool for prefs: set_prefs_runas_host.py (writeprefs in lib.sh); empty `set:allow-devices:` wrote `<set name="allow-devices">\n</set>` (no string child). Test write on D-HP read back clean (log-level 0, onboarding 2, allow-external-configuration true, mode 0, connection-modes usb, 3 auto-connect keys false, listen false). usb-blacklist and enable-car-launcher keys absent in D-HP file (defaults).
- Executors not used: the host ran Prepare itself (short foreground calls, no device change beyond the brief), to avoid a delegation round per step.
- H attempt 0 (02:21-02:26) VOIDED by a script defect, kept in attempt0-voided/. cycle() waits for `wins` to change after a DLG cue, but if the operator answers before the 5 s snapshot sleep ends, wins is already empty, "before" equals "after", and the loop waits 150 s and marks NO_ANSWER with a cue about a dialog that no longer exists (the 02:22:50 cue and the 02:24:37 "again" cue were spurious; d2 had windows=none). That would also burn the stop rule's two retries. Fix: cycle.sh skips the wait when no USB window is present after the snapshot; the operator is told to wait for the cue before answering. Stage script killed by pid 352728, capture by H-hu.pid.
- Finding kept from attempt 0 (not graded): D-HP phase A dialog d1 had windows=UsbPermissionActivity+, checkbox=1, text "Use by default for this USB device" with listen-for-usb-devices=false (comp=disabled). H1 says 0 checkboxes. Needs a clean cycle to grade.
- cycle.sh patched (answered-before-snapshot-ended guard).
- Cue monitor b7e2hmgwj (tail -F | grep) stopped delivering after cues.log was truncated; replaced by poll-based bwn3u7pdh. DLG cue 02:28:43 reached the operator late (about 90 s).
- H-R2-p1 (02:36-02:38) VOIDED as an R2 cycle: the operator ticked "Use by default" on Android's UsbConfirmActivity (phase B) although the cue said not to. That stores a default for B and would make p2/p3 unreadable. Stage script stopped by pid (357918), capture by H-hu.pid. Cycle kept as evidence: phase A d1 at 02:36:50 showed windows=UsbPermissionActivity+UsbConfirmActivity+ top=UsbPermissionActivity checkbox=1 (stacked pair on API 17); phase B d2 at 02:37:38 showed windows=UsbConfirmActivity+ only, top=other. R2 is redone from the start after another Clear defaults, with a resume script stage-R2.sh (R1 is not re-run: it graded 3 cycles).
- H-R2 redo: p1 and p2 both ended NO_ANSWER in the script (hidden UsbConfirmActivity/UsbPermissionActivity windows stay in `dumpsys window windows` behind the projection, so the "wait for the window list to change" loop timed out after 150 s + 60 s) and the stop rule ended R2; graded from the raw dialogs.tsv, defaults.tsv, operator-counts.txt instead.
- R3 p1 also ended NO_ANSWER for the same reason (the operator hurried: 3 dialogs, all ticked, projection up). Script stopped (stage-R2.sh by pid) and R3 p2-p4 run with stage-R3b.sh, which detects a dialog only when mCurrentFocus names a Usb*Activity. R3 p1 graded from its own dialogs.tsv rows.
- Round stopped by the operator after stage H (sleepy, asked to stop): stage P not run. Stage S UNTESTABLE (no usb.host).
- Pre-existing defaults: `defaults.tsv` H-start lists vendors 6353 and 8888 (3 lines) before R1; Prepare did not clear them (the brief says Prepare clears them but lists no step). R1 therefore ran with defaults already stored; it was not cleared before R1. Cleared before R2 and R3 and at the end.
- Late plug markers: on D-HP the `-plug` marker landed up to 12 s after the real attach (adb slow), so cycles.tsv counts for H-R3-p3 and H-R3-p4 read ssl=0 and the script raised false PH cues; those cycles are graded from absolute log times (windows in Results).
- svc power stayon true was run on D-MOTO (Prepare) and D-HP (stage.sh) as the brief's scripts say; the previous values were not read first. D-MOTO now reads stay_on_while_plugged_in=15. D-HP unreachable at close. Reported to the operator for a decision.
- Stray rig_thermal.sh watch processes (3, from killed stage scripts) killed by pid. No logcat left running.
- D-HP settings restored from backup-HP.xml (diff clean, CRs stripped). D-POCO and D-HP keep the candidate APK installed (md5 77ecb9b4...). Wireless adb (tcpip 5555) left on for D-HP, D-POCO, D-MOTO.
- Results draft written; not published (waiting for operator approve).
- Closing pre-flight (after restore): D_HP wifi1 bt1; D_SAM wifi1 bt1; D_POCO wifi1 bt1 (it read bt0 at close, re-enabled with svc bluetooth enable, read back state ON); D_MOTO bt0 (it had come back on, disabled again, read back OFF). The adb server restarted at close, so the wireless connections dropped and the units re-appeared on USB. Build worktree removed. Stay-on: D-HP reads stay_on_while_plugged_in=7, D-MOTO 15 (prior values unknown).
- Operator resumed the round after the close-out: stage P is run (operator said 'the round is not done yet, continue'). wireless.env rewritten (adb server had restarted).
- stage-P.sh = stage.sh with focus-based wins() (same fix as stage-R3b.sh).
- Stage P attempt 1 (08:04-08:10) abandoned: on D-POCO the focus-based wins() read a UsbPermissionActivity that the operator did not see (stale mCurrentFocus), so the script sent no DLG cue and the first plug timed out; request at 08:05:52 for VID 22B8 PID 2E81 was logged (L-ASKA, L-FALL) with no grant. Replaced by scripts/stage-P2.sh: fixed answer rule per run told to the operator up front, a 2 s `dumpsys window windows` timeline of Usb*Activity windows (P-<run>-q<k>-timeline.txt) instead of uiautomator snapshots, operator report after each plug. Checkbox counts therefore come from the operator for stage P, not from uiautomator.
- Stage P ran after the operator reversed the stop (second session part, 08:04 to 08:37). Method changed to stage-P2.sh (see above); P-R1-p1 (08:05:52 request, no snapshot, no answer) is a voided first attempt, kept as evidence. D-POCO plug markers also landed 3 to 20 s late, so counts for P-R3-q3 and P-R3-q1 use absolute-time windows (attach 08:27:42 for q3).
- Stage P stay-on: stage-P2.sh ran `svc power stayon true` on D-POCO too; D-POCO reads stay_on_while_plugged_in=15, prior value unknown (not read first).
- D-POCO settings restored from backup-POCO.xml (diff clean). Closing pre-flight identical to the opening table for radios: D_HP wifi1 bt1, D_SAM wifi1 bt1, D_POCO wifi1 bt1, D_MOTO wifi1 bt0. No strays; one leftover rig_thermal watch killed by pid.

## R-H1 (stage H, listen off)

**FAIL**

H1 predicts 2 dialogs per plug, all `UsbPermissionActivity`, **0 checkboxes**. The two dialogs and the pure `UsbPermissionActivity` pair held; the checkbox did not.

| Cycle | Snapshots (`dialogs.tsv`) | Checkbox | Script row | Log, absolute-time window |
|---|---|---|---|---|
| p1 | d1 phase=A 02:28:43, d2 phase=B 02:30:42, both `windows=UsbPermissionActivity+` | 1, 1 | dialogs=2 req=2 act_intent=0 stale=0 | L-FALL 1, L-ASKA 1, L-ASKB 1, req 2, SSL 2 |
| p2 | d1 phase=A 02:31:46, d2 phase=B 02:32:04, same windows | 1, 1 | dialogs=2 req=2 act_intent=0 stale=0 | same counts |
| p3 | d1 phase=A 02:33:04 (second dialog answered before the snapshot, see Setup notes) | 1 | dialogs=1 req=2 act_intent=0 stale=0 | same counts |

- 0 `UsbConfirmActivity` and 0 `UsbResolverActivity` in any R1 snapshot; `comp=disabled` before p1 (`marks.txt`: `H listen=false comp=disabled`).
- Decisive snapshot text (02:28:43): `"Allow the app Open Headunit to access the USB device?"`, `"Use by default for this USB device"`, `Cancel`, `OK`, with `listen-for-usb-devices` false.
- Phone: `entering USB accessory mode` 1 in each of the three `-phone.txt`.
- Operator column: "2" dialogs per plug (checkbox count not stated).
- Caveat: R1 started with defaults for 6353 and 8888 already stored (`defaults.tsv` H-start). The pre-existing state is the reporter's situation too, but it was not cleared first.

## R-H2 (stage H, listen on, no default): the point of the round

**FAIL**

The stated FAIL conditions are met (`<P>-after` lists 6353 on both graded cycles; p2 has no `phase=B` snapshot), but the evidence is weaker than the verdict reads, see the caveats below. p3 was not run.

| Cycle | Stacked B pair | `-after` vendors | Notes |
|---|---|---|---|
| p1 (voided first try 02:36) | n/a | n/a | Operator ticked Android's confirm dialog against the cue; a default for 6353 was stored; cleared and redone. Phase A d1 02:36:50: `windows=UsbPermissionActivity+UsbConfirmActivity+`, top ours, checkbox 1. |
| p1 (redo) | 02:40:19 `phase=B windows=UsbPermissionActivity+UsbConfirmActivity+` top ours | `6353 8888` (02:45:34; before: none) | L-ASKB 1, L-ASKA 1, req 2, SSL 2. Operator: both dialogs, ticked ours, left Android's unticked. |
| p2 | no `phase=B` row; d1 02:46:34 `phase=A windows=UsbPermissionActivity+UsbPermissionActivity+UsbConfirmActivity+` | `6353 8888` (02:49:29; before: `8888`) | L-ASKB 2, req 2, L-SW 1, activity created 1. Operator: 3 dialogs, Android's then ours then ours, ticked only ours. |
| p3 | not run | | script stop rule (two NO_ANSWER cycles) |

- Half 1 of H2 (our dialog stacked on Android's confirm dialog for B) held once: p1 redo, 02:40:19.
- Half 2 (no default of ours for B afterwards) did not: 6353 is listed after the operator ticked ours and pressed OK without a tick on Android's.
- Caveats: the dialog-wait loop marked both cycles `NO_ANSWER` (hidden `UsbConfirmActivity` windows stay in `dumpsys window windows`), the order in which the operator answered the stacked pair is from memory, and p2 started with a default for 8888 left by p1. R3 held (below), so the control does not void this reading.
- Record-audio warning (H3): not seen in any snapshot text.

## R-H3 (stage H, tick everything, positive control)

**PASS**

p1 cycle row says `NO_ANSWER` (same hidden-window artifact). Graded from `dialogs.tsv` and the log:

- p1: 02:50:31 d1 `phase=A windows=UsbPermissionActivity+UsbConfirmActivity+` checkbox 1; 02:51:03 d2 `phase=B windows=UsbConfirmActivity+`. Operator: 3 dialogs, 1 Android's and 2 ours, all ticked, projection up. Defaults at 02:55:22: `6353 8888` (before p1: none).
- p2, p3, p4 (windows by absolute time because the plug markers landed late): 0 dialogs in `dialogs.tsv` (focus never named a USB activity), req 0, L-ACTASK 0, L-ACTOK 1, L-SW 1, SSL 2, L-MS 4, `Throughput` rendered=151, 152, 153, stale 0.
  - p2 02:55:27 to 02:56:05; p3 02:56:09 to 02:58:55 (attach 02:56:14, SSL 02:56:16); p4 02:59:16 to 03:02:08 (attach 02:59:27, SSL 02:59:28).
- Phone p2 to p4: `entering USB accessory mode` 1, `USB_ACCESSORY_ATTACHED` 7 each; `Critical error` 3 each (every unplug).
- Final Clear defaults removed all defaults: `defaults.tsv` 03:02:32 `none`.

## R-P1 (stage P, listen off)

**PASS**

Graded from the operator's counts, the 2 s window timeline (`P-R1-q<k>-timeline.txt`) and the log, not from `uiautomator` snapshots (see Setup notes). Three graded cycles (q1 to q3; the first attempt is voided).

| Cycle | Operator | Timeline windows | Log (plug to unplug) |
|---|---|---|---|
| q1 | 2 dialogs, no checkbox | only `UsbPermissionActivity` (15 samples), never `UsbConfirmActivity` | req 2, L-ASKA 1, L-ASKB 1, L-FALL 1, grants 2, SSL 2, L-MS 4, stale 0 |
| q2 | 2 dialogs, no checkbox | only `UsbPermissionActivity` | same counts |
| q3 | 2 dialogs, no checkbox | only `UsbPermissionActivity` | same counts |

- Phone: `entering USB accessory mode` 1 and `USB_ACCESSORY_ATTACHED` 7 in all three dumps.
- So H1 holds on API 35 where it failed on API 17: no checkbox on the permission dialogs. Caveat: D-POCO already held 3 persistent entries for D-MOTO's serial (`prep-POCO-usb.txt`), which is the state the brief warned about.

## R-P2 (stage P, listen on, no default)

**FAIL**

| Cycle | Operator | Timeline | After (raw `dumpsys usb`) |
|---|---|---|---|
| q1 | at least 3 dialogs, 1 Android's and 2 ours, stacked; ours had a checkbox that could not be ticked | `UsbConfirmActivity`+`UsbPermissionActivity` together for 19 samples, then `UsbConfirmActivity` alone for 15 | no new entry (1 line before, 1 after) |
| q2 | 3 dialogs, ours, Android's, ours; ticked the first of ours after OK on Android's | ours, then the pair for 3 samples | new entries: `vendor_id=6353 product_id=11521 serial=ZY22GC3BM4` (B) and `vendor_id=8888 product_id=11905` (A) with our package |
| q3 | 1 dialog, ours | 2 to 3 `UsbPermissionActivity` windows | the entry for 8888 changed shape (raw diff); the 6353 entry stays |

- The stacked pair for the accessory identity held on q1 and q2 (H2 first half). A 6353 entry appeared after the answers (H2 second half refuted by the brief's rule: any `after` listing 6353 is a FAIL).
- Caveats: the 6353 entry is the permission record our own tick writes, not necessarily a default left by Android's dialog; q1 did not follow the rule (could not tick ours); `vendors=` could not be parsed in `defaults.tsv` on API 35, so the raw dumps were read; D-POCO's pre-existing entries (3 for D-MOTO's serial) are not the clean start the brief asked for.

## R-P3 (stage P, positive control)

**PASS**

- q1: 3 dialogs (1 Android's, 2 ours), ticked Android's and one of ours; the other of ours was behind the projection. req 1, SSL 2.
- q2, q3, q4: operator saw 0 dialogs; req 0, L-ACTASK 0, SSL 2, L-MS 4, stale 0; q3 and q4 each show L-SW 1 and L-ACTOK 1 (A went to our activity by its default, B arrived with Android's grant); q2 shows neither. Windows by absolute time: q3 attach 08:27:42, q4 08:33:03. The timeline still lists an Android `UsbConfirmActivity` for the whole of q3 (128 samples) and q4 (73), which the operator did not see; the same hidden-window pattern as stage H.
- Phone: `entering USB accessory mode` 1, `USB_ACCESSORY_ATTACHED` 7 in each of the four dumps. L-TP `rendered=` not graded on stage P (brief).
- What stage P left in D-POCO's `dumpsys usb`: before the stage 4 lines naming our package and 3 naming D-MOTO's serial; after the final clear 1 line naming our package and 0 naming D-MOTO's serial (`P-end-clear-post-usb.txt`). The brief expected the persistent grant to survive; the dump shows it gone.

## R-S (stage S, D-SAM)

**UNTESTABLE**

`pm list features | grep -ac android.hardware.usb.host` printed 0 on D-SAM (only `android.hardware.usb.accessory`). No USB host, as the brief says to treat it.

## Anything the brief did not ask about

- Guard: 0 `UsbLauncher: stale accessory` and 0 `UsbLauncher: targeted recovery`, 0 `FATAL EXCEPTION` naming the app, 0 `ANR in com.andrerinas`, 0 `MATCH! Starting AapService`, in the whole stage H capture.
- The script defects cost about 40 minutes: a snapshot delay the operator beat, a window-list wait that never saw hidden dialog windows, plug markers 3 to 12 s late on D-HP. All three are in Setup notes; the next brief should key dialog presence on `mCurrentFocus` and write the plug marker from `logcat`, not from the `sysfs` poll.
- On API 17 the system dialog our own `requestPermission()` raises has a "Use by default for this USB device" checkbox even with the listener off; the first reporter's "checkbox went away" is not reproduced here.
- Stage P method: after the scripted dialog gating failed on API 35 (stale `mCurrentFocus`), the operator followed a fixed rule per run and the host graded from windows, counts and the log. Checkbox counts on stage P are the operator's, not `uiautomator`'s.
- 10 plugs on stage P: 0 `UsbLauncher: stale accessory`, 0 `UsbLauncher: targeted recovery`, 0 `FATAL EXCEPTION`, 0 `ANR`, 0 `MATCH! Starting AapService`.
