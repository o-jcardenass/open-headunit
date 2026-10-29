# pr-1119-home-redesign, round 1 results (with addendum 1)

**Candidate:** `fork/review/pr-1119` @ `11a2c37fd244024444c06e78c447ca3a2190a041` (tree `fbb9eb36b917968e49705165c03e824a8426ba8b`)       **Baseline:** none; each unit's own installed build is the upgrade baseline (see HU-U, SAM-U, HP-U, POCO-U)
**APK md5:** `7219f83f3f82adb5ded4b5a8adf6e805` (candidate, same file on all four head units; `HomePagesHintPolicy` in the DEX 35 times; `ACTION_QUERY_STATE` commit `11a2c37fd244` on each)
**Unit:** D-HU (MT50, Android 14, 1440x720 landscape window, density 240), D-SAM (SM-T230, API 19, 1280x800, density 213), D-HP (HP Slate 7 Plus, API 17, 1280x736), D-POCO as head unit (POCO X3 NFC, API 35, 1080x2400, density 440). Phones: D-MOTO (Android 14) and D-POCO. Gearhead 18.0.664204 on both phones.
**Date:** 2026-10-21 (stages A to D from 19:22 to 20:50, about 1 h 30 min of runs after a 20 min build)

**Evidence:** release `rig-evidence-pr-1119-home-redesign` on `o-jcardenass/open-headunit`, asset `pr-1119-home-redesign-round1-captures.zip` (62 MB), sha256 `207e0d0cde0e9a1f2ccdc9226338f39594f1652c84b4b5439b8ce7eef2212f8a`. Everything a verdict rests on is quoted in this file; the asset is for re-examining.

## Verdicts

| Run | Verdict | Run | Verdict |
|---|---|---|---|
| HU-U | PASS | SAM-C1 | PASS |
| HU-CUST | INCONCLUSIVE | SAM-C2 | PASS |
| HU-LAY-L | FAIL | HP-U | PASS |
| HU-LAY-R | FAIL | HP-LAY-L | FAIL |
| HU-INFO | FAIL | HP-LAY-R | FAIL |
| HU-C2a | PASS | HP-LAY-P | PASS |
| HU-L1 | FAIL | HP-INFO | FAIL |
| HU-L2 | PASS | HP-LN | PASS |
| HU-L3 | PASS | HP-F1 | FAIL |
| HU-W1 | FAIL | HP-C2 | UNTESTABLE |
| HU-C1 | FAIL | POCO-U | PASS |
| HU-F1 | FAIL | POCO-CUST | INCONCLUSIVE |
| HU-C2b | PASS | POCO-LAY-P | PASS |
| HU-C3 | PASS | POCO-LAY-PR | PASS |
| SAM-U | PASS | POCO-LAY-W | FAIL |
| SAM-CUST | INCONCLUSIVE | POCO-INFO | FAIL |
| SAM-LAY-L | FAIL | POCO-L1 | FAIL |
| SAM-LAY-R | FAIL | POCO-L2 | PASS |
| SAM-LAY-P | PASS | POCO-L3 | PASS |
| SAM-INFO | FAIL | POCO-S1 | PASS |
| SAM-LN | PASS | POCO-S2 | PASS |

Totals over 42 runs: PASS 21, FAIL 17, INCONCLUSIVE 3, UNTESTABLE 1. HU-LAY-P is N/A (D-HU is not rotated, operator instruction) and has no run.

What ran per unit:

- **D-HU (stage A):** upgrade, Customization, layout left and right-hand drive, info, the launcher pages (hint, add page, Home key, swipe, widget add and remove, page remove), the widget size addendum (HU-W1), Cancel during a wake with two pages (HU-C1, HU-F1), and three sessions with Move to Background (HU-C2a with D-MOTO in home mode, HU-C2b with D-MOTO and HU-C3 with D-POCO on the launcher home with two pages). Portrait N/A.
- **D-SAM (stage B):** upgrade, Customization, layout in landscape, right-hand drive and portrait, info, launcher home without the role, Cancel during a wake (SAM-C1) and one session from D-POCO (SAM-C2). Pages, widgets, Home-key, Move to Background to this app's home, Connecting tile: N/A (API 19).
- **D-HP (stage C):** upgrade, layout in three cases, info, launcher home, Cancel while discovery runs (HP-F1). HP-C2 UNTESTABLE (no LAN path to D-POCO). Same N/A set as D-SAM.
- **D-POCO (stage D):** upgrade, Customization, layout portrait, portrait right-hand drive and landscape, info, launcher pages in portrait, and Self Mode (POCO-S1 launcher home with Move to Background, POCO-S2 PiP row).

## Setup notes

**Opening pre-flight (rig_preflight.sh, 2026-10-21):**

| Role | WiFi | BT | Screen |
|---|---|---|---|
| D_HU | 1 | 1 | Awake |
| D_SAM | 1 | 1 | Awake |
| D_HP | 1 | 1 | Awake |
| D_POCO | 1 | 1 | Awake |
| D_MOTO | 1 | 0 | Awake |

The pre-flight printed FAIL for D_MOTO (Bluetooth off); the operator said to go ahead, since the runs switch the phones' radios themselves. The closing pre-flight is pasted in at approve.

Quirk files read: topics/tooling, topics/projection, units D-HU, D-SAM, D-HP, D-SAM-and-D-HP, D-POCO, D-MOTO. Tools used: rig_devices.sh, rig_preflight.sh, rig_cleanup.sh, rig_thermal.sh (via build_hur_cool.sh), wt-new.sh, build_hur_cool.sh, set_hu_settings_host.py, set_prefs_runas_host.py. One-round scripts (the brief's blocks extracted, plus my wrappers) are in `rig-data/rounds/pr-1119-home-redesign-round1/scripts/`; no toolkit script was added or changed. The host ran the brief's shell functions itself in the foreground, no rig-executor.

- Resolved from SHA cc4839ca (queue commit) to thread pr-1119-home-redesign, round 1. Brief read in full at transfer tip cc4839ca.
- Transfer branch is held by worktree worktrees/rig-sync__scratch__8228cb07__20261015 (clean); fast-forwarded it 1c307e7c -> cc4839ca and worked there; main checkout stayed on main. rig_sync.py: in sync, no RESTART, CLAUDE.md unchanged.
- Quirk files read: topics/tooling, topics/projection, units D-HU, D-SAM, D-HP, D-SAM-and-D-HP, D-POCO, D-MOTO.
- Injected gestures (cap 5 per run) are what the brief and the standing operator instruction allow; listed per run in gestures.log.
- Opening pre-flight 2026-10-21 19:1x: D_HU wifi1 bt1; D_SAM wifi1 bt1; D_HP wifi1 bt1; D_POCO wifi1 bt1; D_MOTO wifi1 bt0 -> PREFLIGHT FAILED (D_MOTO bluetooth off). Radios to restore at the end = this table.
- Other gates read: battery D-SAM 100, D-HP 100, D-POCO 100, D-MOTO 93; D-HU dumpsys battery level 0 (head unit, no battery, record only). D-POCO :149D listeners 1 (head unit server up). D-POCO THROTTLE_LIMIT_EXCEEDED count 7219 in logcat -d (brief: reboot first). D-MOTO count 0, focus Chrome (not locked). Gearhead procs: MOTO 1, POCO 4. fork/review/pr-1119 = 11a2c37f (matches brief). Host 60C, throttle 0. rig_cleanup: clean.
- STOPPED before any device change or build: waiting on operator for D-MOTO BT and D-POCO throttle.
- Operator said "go ahead" to both blockers: proceeding with D-MOTO BT off (runs' ph_on enables it; restore to off at the end); D-POCO not rebooted unless a connection run shows a live THROTTLE_LIMIT_EXCEEDED.
- Markers: ACTION_LOG_MARKER not in CONFIGURING at 11a2c37f, no allow-external-configuration needed.
- APK md5 7219f83f3f82adb5ded4b5a8adf6e805, HomePagesHintPolicy count 35, built from tree fbb9eb36 by build_hur_cool.sh (exit 0). Brief's extract_brief_blocks.py did not match the brief's bold names; used one-round tools/extract.py.
- D-HU date reads Wed Jun 17 08:08:31 -05 2026 vs host Wed Oct 21 19:21:49 -05 2026: clock months behind; runs graded by marker windows only.
- Brief errata: lib.sh pull_settings used 'su -c' for D-HU; D-HU adbd is root and has no su binary (first backup was 0 bytes). Changed to plain adb shell cat. D-HU shared_prefs stat read: drwxrwx--x u0_a176:u0_a176 (correct, no chown needed). HU declares PiP (count 1), locale en-US, API 34, wm 720x1440 portrait physical, density 240; animator_duration_scale was 1.0 (set to 0); HOME holder com.android.launcher3.
- Operator asked to fix D-HU times. D-HU date was Wed Jun 17 08:09 2026 (auto_time 0, tz America/Bogota). Set with `date MMDDhhmmYYYY.ss` as root to the host time; read back Wed Oct 21 19:22:45 2026 vs host 19:22:45 (0 s). A-hu.txt capture started before the fix, so its timestamps jump from June to October once; all windows are by marker text. The clock may revert after a reboot (no auto time).
- D-POCO bonded list includes Navegadortz2 (D-HU's radio) and, per quirk, may block D-SAM in stage B; check before SAM-C2.
- Phones: D-MOTO and D-POCO wifi/bt disabled and read back; D-POCO WiFi hold loop (hold.sh on) running, pid in hold.pid. Gearhead 18.0.664204 on both. D-POCO throttle count in a fresh logcat: 0.
- Addendum 1 (transfer commit 1ab00e2e, pr-1119-home-redesign-round1-addendum.md) added to this round: HU-L2 density/appwidget captures, new run HU-W1 after HU-L3 (before HU-C1). Same APK, no rebuild.
- Addendum deviation: it assumes D-HU at 320 dpi (d=2.0), but pre-flight read `wm density` 240 (d=1.5). Its B2 thresholds (>=1.6x CONFIRMED, <=1.25x NOT REPRODUCED, "at d=2") are scaled to d=1.5 when graded: the bug asks minW*d, so expected ratio inner/minW is 1.5 if present and 1.0 if absent; thresholds used will be stated in the HU-W1 section.
- Host ran the brief's shell functions itself in the foreground via scripts/runA.sh (no rig-executor): the brief's helpers are bash functions with shared state and the host owns grading.
- HU-U PASS. Old build 3.5.0-beta4 fc3469823c69. Card was in armed Wireless state (mode 3), showed Connect over WiFi + Cancel while idle. Exit tile dumped 84 px tall vs Settings 120 px at 1440x720 (E1 false), see HU-LAY-L.
- HU-CUST: record only, id:home_color_preview not in dump (not on first screen of Customization at 1440x720); screenshot ui/HU-CUST.png. NAV id for customizationFragment 0x7f0900dd = 2131296477.
- Brief errata: `launch; sleep 2; mark <run>-start` put the start marker after HomeFragment's onResume line (19:27:02.034 vs marker 19:27:02.470), so waitn "onResume" always ran its full 40 s and the window missed it. From HU-INFO on the run copies mark before launch (scripts/*.run.sh). HU-U, HU-CUST, HU-LAY-L and HU-LAY-R ran with the old order; their verdicts do not depend on the marker.
- HU-LAY-L and HU-LAY-R: FAIL on E1 (Exit tile squeezed to 84 px of 120, ratio 0.70). Exit tile still whole, on screen and labelled; the brief's PASS rule is the 0.90 ratio. L-FIT lines: 0 in the whole A-hu capture so far.
- HU-INFO: pm revoke of location refused on D-HU ('not a changeable permission type'), I1 INCONCLUSIVE; location grants unchanged (still granted), nothing to restore. I3 fails only through the E1 Exit-tile squeeze.
- HU-C2a: first launch attempt started twice by host error (one copy with empty phone serial); both killed within 5 s, before HU-C2a-start marker and before the phone was switched on. Clean single re-run follows.
- HU-C2a attempt 1 (19:31 to 19:33): NO_SESSION, void. D-HU poked only POCO X3 NFC (3 poke cycles, HFP-AG and HSP-AG read -1, D-POCO's radios off) and never D-MOTO: the backup's `native-poke-bt-macs` holds only D-POCO's MAC (DC:B7:2E:5E:4E:59) and the brief's K_CONN does not set it, so `native-poke-all-paired` true is not enough with a non-empty list. Brief gap. Fix for the D-MOTO runs: also write `native-poke-bt-macs` = D-MOTO's MAC A0:46:5A:97:E4:95 (K_POKE_MOTO); D-POCO runs use DC:B7:2E:5E:4E:59 (K_POKE_POCO). Attempt 2 is the one allowed retry. MOTO focus before was NotificationShade (no keyguard); HOME key sent before the retry.
- Brief errata (HU-C2a): (1) in conn_home the Move to Background tap and its L-BG line come before `mark $R-bg`, so P4's bg-to-ret span misses L-BG; graded over the wider window from the tap. (2) Phone string drift on Gearhead 18.0.664204: the literal `ByeByeRequest` never appears; the end of session is `GH.ConnLoggerV2 ... PROJECTION_ENDED_BYEBYE_BY_CAR`. P7/P8 graded on that line, and the pre-disc bullets still look for both strings (0). Phone clock is about 2 s ahead of D-HU in these logs.
- HU-C2a PASS (retry, attempt 2 only counts). Windows use the last HU-C2a-start marker.
- HU-L1 attempt 1 void: the pages hint printed at 19:37:02.851, before `HU-L1-start` (19:37:03.325), because lblock's role_on and thermal gate sit between launch and the marker; pages1 then saw NO_HINT, skipped BACK, and the first long press landed on the open hint (closed with reason LEFT, no page added). Fix: lblock now writes `<run>-start` before the launch and pages1 no longer writes it again (scripts/pages.run.sh). Attempt 2 is a re-run for a brief timing defect, not a retry for a failure; gesture budget counted afresh (attempt 1 spent 2).
- HU-L1 attempt 2 (19:38:21 to 19:39:04): hint showed once (19:38:25.181); BACK was delivered (`MainActivity.dispatchKeyEvent keyCode=4` 19:38:31.987) but the hint stayed up and only closed at 19:38:48.607 `pages hint closed (LEFT)` when the next gesture hit it. So G1's `(BACK)` close is not met on D-HU, and the long press on the title was swallowed by the hint (no "Add a page" menu). Attempt 3 adds one tap on `home_pages_hint_got_it` after BACK when the hint is still in a dump (extra gesture, 4 of 5), so G2 to G6 can still be read.
- HU-L1 FAIL (G1: BACK does not close the pages hint on D-HU). G4 literal log line is absent because the Home key rebuilds the pager on page 1 without a setPage log; the dump shows page 1. Attempt 3's gestures: tap got_it, long title, tap Add a page, swipe = 4.
- pages2 page selection changed from the last L-PAGE log line to a dump check (home_primary_card absent = on the added page), because the log line does not follow resumes that reset to page 1.
- HU-L2 PASS. In HU-L2-appwidget.txt the provider min=(WxH) values are encoded dimensions, not px (46081x38401 = 0xB401 x 0x9601 = 180 dip x 150 dip for Chrome bookmarks), so the addendum's step 2 ('px') needs decoding: (v>>8) with unit in the low nibble.
- HU-W1 (addendum): the addendum's step 1 ("first widget_option_label of HU-W1-pick.xml") did not name the bound widget: the picker list scrolled between the dump and the tap (screenshot shows Maps 'Quickly find places nearby' on top; dump said Chrome bookmarks). The provider was taken from `dumpsys appwidget` (Widgets: id=3). Its `min=` is the server's raw encoded dimension (value>>8 = dp, low nibble 1 = dip), not px, so minW px = dp x 1.5.
- Addendum density: pre-flight and HU-W1-density.txt read `Physical density: 240` (d=1.5), not 320. B2 thresholds scaled: CONFIRMED when inner/minW >= 1.3 (bug 1.5, fix ~1.05); NOT REPRODUCED <= 1.1; here 1.445.
- HU-W1 verdict line is FAIL (a defect the addendum asks to confirm is present), with B2 CONFIRMED in the text.
- HU-C1 first launch void: host called hu_cblock, which is a script not a function (command not found), so the block did not run and cancel_wake ran against the old state (HU-C1-start 19:44:58 in capture, no gesture sent, killed within 3 min). Wrapped the block as a function and re-ran. Markers HU-C1-start now appear twice; windows use the last one.
- HU-C1: brief ordering again: the Cancel tap is before `mark $R-cancel` (tap 19:49:42.4, marker 19:49:44.5), so W2/W3 were read from the tap time. Poke list for the block = both phone MACs (K_POKE_BOTH) so the head unit pokes D-MOTO in HU-C2b and D-POCO in HU-C3. W1 literal FAIL comes from fitCardText on page 1 (card above the favorites row); the same flow lands there in the brief as written.
- HU-C2b PASS (Warn 8: projection activity onDestroy isFinishing=true on Move to Background to this app's home, session and return still fine).
- HU-C3 PASS. D-POCO throttle lines in the stage A capture: 0.
- Stage B pre-flight D-SAM: API 19, wm 800x1280 physical (landscape mount via screen-orientation 2), density 213, locale en, PiP 0, shared_prefs drwxrwx--x u0_a158 (app owner, no chown needed), animator_duration_scale was already 0.0 (left at 0.0, nothing to restore), location permissions install-time (no pm revoke). `logcat -G` is unsupported on API 19 (printed usage, capture still runs, small buffer). D-SAM backup holds screen-orientation 4, the brief says D-SAM keeps 2, so the unit base for D-SAM writes screen-orientation 2 (UB_SAM). D-SAM date 19:57:30 COT matches the host. D-SAM battery 100. D-HU Bluetooth had self-reverted to enabled by 19:57; disabled before each stage B connection run.
- SAM-U PASS
- SAM-CUST: record only, home_color_preview not in dump; screenshot ui/SAM-CUST.png
- SAM-LAY-L and -R FAIL on E1 (Exit tile 85 of 128 px, ratio 0.66), SAM-LAY-P PASS (portrait). Same squeeze as D-HU landscape.
- SAM-LN first launch void: lnr was missing from the host's function list (command not found); added and re-run.
- SAM-LN PASS, SAM-INFO FAIL by rule (E1 only).
- SAM-C1 PASS. D-SAM device clock reads about 21 s behind the host (19:57:30 vs 19:57:51); grading by markers within the D-SAM log only.
- SAM-C2 attempt 1 (20:07:3x to 20:09:5x D-SAM clock): NO_SESSION at the 120 s cap. D-SAM poked POCO X3 NFC: HFP-AG and HSP-AG read -1 first, `Successfully poked ... via HFP-AG` at 20:08:21 and 20:08:52, the app warned at 20:09:38.866 "answered 3 wake pokes but has never opened the Android Auto channel on radio [Navegadortz3]", then `Connection accepted from POCO X3 NFC on local radio [Navegadortz3]` at 20:09:55.162, just as the script gave up and switched the phone off. D-POCO bonded list read: moto, Magnetic Speaker, DR-82645, BC8-Android, Navegadortz2 (D-HU), Navegadortz3 (D-SAM). Because a Connection accepted line appears, the brief's forget-D-HU contingency (no accepted line) does not apply; the stop rule's one retry is used.
- SAM-C2 PASS on attempt 2 (stop rule's one retry). Between Move to Background and the return the decoder logged 'Decoder stopped: restart: sync_stall' once; no AapProjectionActivity onDestroy on D-SAM.
- Stage C pre-flight D-HP: API 17, no wm binary (size from dumpsys window), locale en, PiP 0, shared_prefs drwxrwx--x u0_a71 (app owner), animator_duration_scale already 0.0, location install-time, logcat -G unsupported (usage printed, capture runs), date matches host (20:13:48). D-HP battery 100. Stage C needs no cable change: D-HP was already on adb alongside the other four units. Capture watcher on C-hp.txt.
- HP-U PASS. New home dump on D-HP lists only Settings and Exit as bar text.
- D-HP layout grading deviation: in text mode uiq.py's E1 compares the bounds of the 'Exit' and 'Settings' text nodes (46x29 vs 89x29 px), so its 0.9 width rule is false for every window and it cannot see a squeezed tile (the label keeps its size). I graded E1 from the clickable LinearLayout tile containers in the same dumps, which carry bounds on API 17. Result: landscape Exit tile 128x85 vs 128x128 (0.66, FAIL, same squeeze as D-HU and D-SAM), portrait 153x128 vs 153x128 (PASS).
- HP-INFO FAIL (same Exit tile squeeze), HP-LN PASS with manual E3 (uiq text mode counts the favorite 'Settings' label as a second Settings tile).
- HP-F1 FAIL (predicted): SEARCHING stage up, main button shows the USB card, no Cancel and no pill on screen.
- HP-C2 attempt 1 (20:21:48 to 20:23:55 D-HP clock): NO_SESSION. D-HP (192.168.1.27) ran NetworkDiscovery sweeps of 192.168.1.* every ~11 s (253 probed, 0 responded on 5289, which only counts the helper port) and logged no 'Found Headunit Server on ...:5277'; D-POCO was on the same subnet (192.168.1.20) with a listener on :149D (5277) in /proc/net/tcp6 before and after; D-POCO's capture has no line naming D-HP or 5277; THROTTLE_LIMIT_EXCEEDED 0. Stop rule: one retry.
- HP-C2 attempt 2 (20:25 to 20:27 D-HP clock): NO_SESSION again, same sweeps, no 'Found Headunit Server'. Network check during it: D-HP and D-POCO both ping the router (192.168.1.1) but not each other; D-POCO 'ip neigh' shows 192.168.1.27 FAILED, D-HP ping to 192.168.1.20 100% loss, D-POCO to router 88 to 302 ms (weak link). Matches the D-SAM-and-D-HP quirk about device-to-device ARP breaking.
- HP-C2 UNTESTABLE (rig): no LAN path between D-HP and D-POCO (ARP FAILED, 100% ping loss both ways, 4 probes over 32 s), both attempts used. A hand step (operator re-joins D-HP and D-POCO to the same WiFi) would let it run.
- Stage D pre-flight D-POCO (head unit in Self Mode): API 35, 1080x2400 density 440, locale en-AU-u-mu-celsius (English labels), PiP feature count 1, HOME holder com.android.launcher3 (written to home-role-POCO.txt), appwidget grantbind rc 0, animator_duration_scale was 1.0 (set to 0, restore to 1.0), location granted (both), shared_prefs not listable by plain shell (run-as ls below), :5277 listener 1. Gearhead was in front (mCurrentFocus) at start, HOME key sent. D-POCO date 20:29:15 vs host 20:29:16. D-HU Bluetooth disabled again, D-MOTO off. D-POCO backup holds screen-orientation 3, so the portrait runs write K_PORT (4) and POCO-LAY-W writes K_LAND.
- POCO-U PASS (old build was 4.0.0-alpha 4c5960c7856e). POCO-CUST record only: home_color_preview not in the dump of the first screen; ui/POCO-CUST.png.
- POCO layout and info: portrait runs wrote K_PORT because the backup holds screen-orientation 3; a custom `layp` waits 12 s after the launch for the rotation. POCO-INFO ran in the backup's landscape. With ACCESS_FINE and COARSE_LOCATION revoked the app asked for them at launch and the permission dialog hid the home, so I1's dump half could not be read; the log half is present. After the run the script granted both again.
- POCO-L1/L2/L3 first pass void for L2 and L3: on D-POCO the home is compact (2 pages before the add, 3 after: card, favorites with the widget row, added page), so the page-2 test 'home_primary_card absent' in my pages2 loop matched the favorites page; L2 added a row widget there (settings home-widgets=5, home-widget-pages 'p' without a number) instead of a widget on the added page, and L3 found no 'Remove widget'. Fixed the loop to require card and favorites both absent, then re-ran L1 to L3 from a clean settings state (the first L1 reading is kept in this note: hint shown once, BACK did not close it, closed GOT_IT; 3 pages after add, 'showing page 3 of 3', swipe 'showing page 2 of 3'; no 'showing page 1 of 3' after the Home key, dump shows page 1).
- POCO-L2 second pass also void: the two swipes in pages2's loop (target home_pager centre) never changed the page on page 2 (no 'HomePager: showing page' line after them), because the pager centre on the favorites page is the horizontal widget row, which consumed the swipe; L2 again put a row widget on page 2 (home-widgets=6). Loop now swipes on `home_favorites` when that row is on screen (gesture count for L2: up to 2 swipes, tap tile, pick = 4). Third pass follows from a clean restore.
- POCO-L1 FAIL (G1 BACK does not close the hint), POCO-L2 PASS, POCO-L3 PASS on the third pass.
- D-POCO capture in stage D holds 828 THROTTLE_LIMIT_EXCEEDED lines, all `GH.ConnLoggerV2 ... WIRELESS_RFCOMM_CONNECTION_...  throttle THROTTLE_LIMIT_EXCEEDED` (Gearhead's own wireless RFCOMM start attempts, about 66 a minute from 20:31 once D-POCO's Bluetooth was on; B-poco.txt had 28). Self Mode does not use that path; the brief's reboot rule is applied only if POCO-S1 forms no session.
- POCO-S1 attempt 1 (20:43:31 to 20:44:40): NO_SESSION. The tap on home_primary_action ('Start Self Mode', ready dump 20:43:38) at 20:43:40 opened Android's overlay permission screen (`ACTION_MANAGE_OVERLAY_PERMISSION` Settings$OverlaySettingsActivity 20:43:44.5, `AppOpsPermissionControl isAllowed: false`), i.e. SYSTEM_ALERT_WINDOW was not granted to the app at that moment. At 20:43:49 a back edge-swipe (`Gesture Monitor edge-swipe ... device 4`, `onGestureFinished mTriggerBack == true`) closed that screen; no script of mine sent it, so a person touched the screen. At 20:44:52 POCO-S2 logs `raising the projection by DIRECT (overlay=true ...)`, and `appops get` now reads `SYSTEM_ALERT_WINDOW: allow`. I did not change the permission; baseline appop was not captured in pre-flight (brief errata: add it). S1 retried once with the permission allowed.
- POCO-S1 PASS on attempt 2, POCO-S2 PASS. Focus reads of S1 after Move to Background and after the return were `mCurrentFocus=null` (window transition on D-POCO); graded on logs and the home dump instead.
- Observation (not graded): on the return from Move to Background D-POCO showed no picture for about 5.6 s before the app's keyframe recovery ('relaunched surface has no picture after 4950ms - cycling video focus' then 'keyframe decoded'), against 0.85 s on D-HU.

## HU-U

**PASS**

U1 old classic home 1 line; U2 md5 7219f83f.. + HomePagesHintPolicy 35 + commit 11a2c37fd244 (old fc3469823c69 3.5.0-beta4); U3 empty; U4 card 1, classic 0, focus MainActivity; U5 removed/changed empty, added home-widget-host-id, 3 colours -1754827; U6 MATCH e53935. Info: layout E1,E2 false on the Wireless card (Exit tile 84 px tall vs Settings 120 px), graded in HU-LAY.

## HU-CUST

**INCONCLUSIVE**

Record only, not graded. Customization opened by deep link on D-HU 1440x720; id:home_color_preview is not in the first-screen dump (the screen starts with the Background Image preview), so the colour preview could not be recorded. Screenshot ui/HU-CUST.png.

## HU-LAY-L

**FAIL**

layout lhd: E2..E5 true, E1 false: Exit tile bounds [36,600,156,684] is 120x84 px against Settings 120x120 (ratio 0.70, rule needs 0.90); bar of 4 tiles needs 570..690 on a 720 window whose card ends at 684. Action 'Connect over WiFi' [234,537,684,651] whole inside card. info_count 0, pill_count 0, L-FIT 0 lines.

## HU-LAY-R

**FAIL**

layout rhd: E2..E5 true, E1 false: Exit tile bounds [1284,600,1404,684] is 120x84 px against Settings 120x120 (ratio 0.70, rule needs 0.90); bar of 4 tiles needs 570..690 on a 720 window whose card ends at 684. Action 'Connect over WiFi' [234,537,684,651] whole inside card. info_count 0, pill_count 0, L-FIT 0 lines.

## HU-INFO

**FAIL**

I1 INCONCLUSIVE: pm revoke ACCESS_FINE/COARSE_LOCATION refused on D-HU (SecurityException not a changeable permission type), so permission stayed granted and the placeholder-with-no-permission line was never reachable (0 lines in both windows). I2 PASS: placeholder lines 0, clock '7:29 PM' (device 19:29), date 'Oct 21, 2026', speed '--', SPDX 0. I3 false by rule: layout PASS false only from E1 (Exit 84 of 120 px, same as HU-LAY); E2 true, action 'Connect over WiFi' whole in card, info_count 1.

## HU-C2a

**PASS**

Retry (attempt 1 void: wake list lacked D-MOTO). P1 SSL 1, MSSR 2, rendered=151 before bg; P2 seven connecting steps on home line; P3 full menu with Move to Background, Stop Connection, Quick Settings, and Picture-in-Picture (D-HU declares PiP, API 34); P4 L-BG '=false' at 19:35:10.767 (printed before the -bg marker), focus com.android.launcher3/com.zqc.launcher.HomeActivity = factory home; P5 L-DISC 0, no phone Critical error before disc; P6 L-RAISE 1, focus AapProjectionActivity, rendered=111 after ret; P7 phone shows PROJECTION_ENDED_BYEBYE_BY_CAR at 19:35:45.468 (literal ByeByeRequest absent, string drift). L-PDEST 0, L-DEC 1 (activity_stopped 19:35:11.083). Discard rules clean.

## HU-L1

**FAIL**

Attempt 3 of 3 counts (attempt 1 void for marker order; attempt 2 showed the BACK result). G1 FAIL: hint shown once (19:39:4x) but BACK (keyCode 4 delivered to MainActivity) did not close it and the close reason was GOT_IT after an extra tap on home_pages_hint_got_it; attempt 2 closed with LEFT. G2 PASS (E1..E5 true, favorites clear, no Exit tile). G3 PASS: menu text Add a page, L-PADD 1, 'the home has 2 page(s)', 'showing page 2 of 2'. G4: no 'showing page 1 of 2' line after the Home key (HomeFragment.onResume 19:40:15.136 then renderPages 2 pages); dump HU-L1-p1.xml after the key shows card, favorites and widget tile, i.e. page 1 on screen, so behaviour met but the logged evidence is absent. G5 PASS: 'showing page 2 of 2' 19:40:22.671 after the swipe. G6 PASS: L-ROLE 0, home-pages-hint-shown true in settings. Gestures 4 of 5. L-FIT 1 line (fitWidgetStrip row 139 px of 139 px).

## HU-L2

**PASS**

Page 2 of 2 on screen before the tap (card absent in nav1 dump, last L-PAGE 'showing page 2 of 2'); picked 'Chrome bookmarks' (first widget_option_label); settings home-widget-pages = p2 (matches p[0-9]+); L-WNO 0; widget frame bounds [192,18,798,669] (606x651 px); widget_add_tile lines remaining 1; focus CarLauncherAlias so no bind dialog step needed (0 extra gestures, gestures used 2 of 5). Addendum B3 for L2: no options in dumpsys appwidget (no appWidgetMinWidth or options= lines); density 240.

## HU-L3

**PASS**

wmenu holds 'Remove widget' (1); after it widget_add_tile still present (p2b 1 line); pmenu holds 'Remove this page'; L-PREM 'removed added home page 1' at 19:42:50.078 and 'the home has 1 page(s)' (N0=1); settings home-widget-pages is an empty string (no p value). Gestures 4 of 5 (cell long press at 513,343, Remove widget, long add tile, Remove this page).

## HU-W1

**FAIL**

Addendum run. B2 CONFIRMED (scaled thresholds, see Setup notes): bound provider (dumpsys appwidget, id=3) = com.google.android.apps.maps TrafficWidgetProvider ('Quickly find places nearby'), min=(56321x25601) raw = 220x100 dp = 330x150 px at d=1.5; row frame home_widget_frame bounds [192,530,687,702] so FW=495 px = minW x d exactly; inner = FW-18 = 477, ratio inner/minW 1.445, FW/minW 1.500 (bug predicts 1.5, fix about 1.05). Overflow: add tile right edge 849 of 1440, none. Row height 172 px (fitWidgetStrip 'out of 172px of room') vs 139 before; home_favorites_area 396..702 (306) in HU-L1 vs 368..702 (334) here, card bottom 378 -> 350. B3: no options in dumpsys appwidget (record only). Gestures 2 of 5.

## HU-C1

**FAIL**

W1 literal FAIL: the last wake dump (wakeB, page 1 after HOME) has progress 1, Cancel 1 at [234,378,411,474] inside the window, pill 0, bar tile text 'Connecting', but home_primary_subtitle is absent: 'HomeFragment.fitCardText: the main button is 373px tall, hiding 3 line(s) so its action fits' (19:49:35.769). The earlier dump wake1 has subtitle 'Waking your phone', progress 1, Cancel 1, tile 'Connecting'. W2 PASS: 'MainActivity.cancelBringUp: home Cancel pressed, stopping the bring-up' 19:49:42.410 and 'AapService: the status pill's X stopped the wireless bring-up' 19:49:42.429 (tap at 19:49:42, marker HU-C1-cancel 19:49:44.478). W3 PASS: 0 pokes and 0 WAKING_PHONE lines from 19:49:49 to end 19:50:54.063; step 'hidden' 19:49:42.491; after.xml progress 0 and cancel 0. Wake steps: WAITING_FOR_PHONE 19:49:05, WAKING_PHONE 19:49:20 and 19:49:35. Gestures 2 of 5.

## HU-F1

**FAIL**

Predicted. HU-C1-page2.xml (page 2 of 2, WAKING_PHONE up, last step line 19:49:20.407) has home_primary_cancel 0 lines and auto_connect_pill 0 lines: no Cancel and no pill anywhere while the stage is up (pill is hidden by homeShowsBringUp).

## HU-C2b

**PASS**

P1 SSL 1, MSSR 2, rendered=150 before bg; P2 ten steps on home line (ARMED..STARTING_PROJECTION); P2b 10 of 11 up dumps with wifi_button 'Connecting'; P3 short menu (BG 1, Stop 1, PiP 0, QS 0); P4 L-BG '=true' 19:53:09.450, L-RAISE 0, focus CarLauncherAlias (this app's home) not com.android.launcher3; P5 wifi_button 'Connected', pill 0, clear overlaps 0, action 'Back to Android Auto'; P6 L-DISC 0, SSL 1, phone BYEBYE 0 / Critical error 0 before disc; P7 focus AapProjectionActivity after the tile tap (19:53:40), rendered=106 after ret; P8 phone PROJECTION_ENDED_BYEBYE after disc 1. Record: L-PDEST 'AapProjectionActivity.onDestroy called. isFinishing=true' 19:53:10.589 (Warn 8 seen), L-DEC 'activity_stopped' 19:53:10.514 and 'surfaceDestroyed' 19:53:10.583. Discard: MATCH 0, createGroup 1. Gestures 2 of 5.

## HU-C3

**PASS**

D-POCO as phone, return by 'Back to Android Auto' (home_primary_action tap 19:56:35). P1 SSL 1, MSSR 2, rendered=151; P2 ten steps on home line; P2b 8 of 9 up dumps 'Connecting'; P3 short menu (PiP 0, QS 0); P4 L-BG '=true' 19:56:05.047, L-RAISE 0, focus CarLauncherAlias; P5 wifi_button 'Connected', pill 0, overlaps 0, action 'Back to Android Auto'; P6 L-DISC 0, SSL 1, phone BYEBYE 0, Critical error 0; P7 focus AapProjectionActivity, rendered=104 after ret; P8 phone BYEBYE after disc 1. L-PDEST isFinishing=true 19:56:06.173; L-DEC activity_stopped, surfaceDestroyed. Discard clean (MATCH 0, createGroup 1, POCO THROTTLE 0). Gestures 2 of 5.

## SAM-U

**PASS**

U1 classic home before (main_buttons_layout 1 line); old build 3.5.0-beta5 commit 8db52cebe6bf; U2 live md5 7219f83f.. , HomePagesHintPolicy 35, commit 11a2c37fd244; U3 removed/changed/added all empty; U4 home_primary_card 1, classic 0, focus MainActivity; U5 all empty, three custom colours -1754827; U6 N/A (API 19). Info: layout E1,E2 false in the new dump (Exit tile 85 px tall vs Settings 128), action_count 0 (state card), graded in SAM-LAY.

## SAM-CUST

**INCONCLUSIVE**

Record only, not graded. Customization opened by deep link on D-SAM 1280x800; id:home_color_preview is not in the first-screen dump (the screen starts with the Background Image preview), so the colour preview could not be recorded. Screenshot ui/SAM-CUST.png.

## SAM-LAY-L

**FAIL**

1280x800: E2..E5 true, E1 false: Exit tile [54,661,182,746] is 128x85 px against Settings 128x128 (ratio 0.66, rule 0.90). Action 'Connect over WiFi' [289,565,822,703] whole. Bar left of the card. info 0, pill 0, L-FIT 0.

## SAM-LAY-R

**FAIL**

1280x800 RHD: E1 false: Exit [1098,661,1226,746] 128x85 vs Settings 128x128 (0.66); E2..E5 true, bar right of the card. info 0, pill 0, L-FIT 0.

## SAM-LAY-P

**PASS**

800x1280 portrait (screen-orientation 4): E1..E5 true; Exit [593,1098,746,1226] 153x128 vs Settings 153x128 (ratio 1.0); bar along the bottom; action 'Connect over WiFi' whole; info 0, pill 0, L-FIT 0.

## SAM-INFO

**FAIL**

I1 N/A (API 19, install-time permission). I2 PASS: placeholder line 0, SPDX 0, clock '8:03 PM' (device 20:04), date '21/10/2026', speed '--'. I3 false by rule: layout PASS false only through E1 (Exit [54,661,182,746] 128x85 vs Settings 128x128, same as SAM-LAY-L); E2 true, action 'Connect over WiFi' whole, info_count 1.

## SAM-LN

**PASS**

Launcher home without the role at 1280x800: layout PASS true (E1 true = no Exit tile, exit_count 0; E2 action 'Connect over WiFi' whole), favorites_inside_and_clear true, apps_button 1, favorite_slot_1..4 present, first favorite_label 'Settings' (not 'Add app'), home_widgets_scroll 0, pages hint 0, L-FIT 0.

## SAM-C1

**PASS**

W1: dump wake1 has home_primary_progress 1, home_primary_cancel 1 at [289,618,452,703] inside 1280x800, subtitle 'Waking your phone', pill 0 (tile N/A single page). W2: 'MainActivity: home Cancel pressed, stopping the bring-up' 20:05:36.460 and 'AapService: the status pill's X stopped the wireless bring-up' 20:05:36.530 (D-SAM clock; marker SAM-C1-cancel 20:05:39.423). W3: 0 pokes and 0 WAKING_PHONE after the cancel through SAM-C1-end 20:06:50.672; step 'hidden' 20:05:36.590; after.xml progress 0, cancel 0. Wake: WAKING_PHONE 20:05:30.504, poke to POCO X3 NFC 20:05:30.574. L-FIT 0. Gestures 1 of 5.

## SAM-C2

**PASS**

Attempt 2 counts (attempt 1 NO_SESSION at the 120 s cap, connect-back landed at 20:09:55 just after). P1 SSL 1, MSSR 4, rendered=150 before bg; P2 seven connecting steps on the home line; P3 full menu (Move to Background, Stop Connection, Quick Settings present; Picture-in-Picture 0 on API 19); P4 L-BG '=false' 20:11:39.885, focus com.sec.android.app.launcher (not this app); P5 L-DISC 0, phone BYEBYE 0, Critical error 0 before disc; P6 L-RAISE 1, focus AapProjectionActivity, rendered=97 after ret; P7 phone PROJECTION_ENDED_BYEBYE after disc 1. Record: L-DEC 'activity_stopped' 20:11:40.735 and 'restart: sync_stall' 20:11:56.771 between bg and ret; L-PDEST 0 lines. Discard: MATCH 0, createGroup 0 in window. Gestures 1 of 5 (D-SAM clock about 21 s behind host).

## HP-U

**PASS**

By text (API 17, no ids): U1 old dump texts Self Mode, USB, WiFi, Settings, Exit and no main-action text; old build 3.5.0-beta4 commit 802920baf0ae. U2 live md5 7219f83f.. , HomePagesHintPolicy 35, commit 11a2c37fd244. U3 and U5 removed/changed/added all empty, three custom colours -1754827. U4 new dump holds 'Connect over USB' (action list) plus Settings, Exit, focus MainActivity. U6 N/A. Note: the new dump lists only Settings and Exit as bar text (no USB/WiFi/Self Mode tile labels).

## HP-LAY-L

**FAIL**

By text, window 1280x736. uiq layout reports E1 false only because it compares the widths of the 'Exit' and 'Settings' text boxes (46 vs 89 px), which cannot reach 0.9; graded on the clickable tile LinearLayouts instead: Exit [54,597,182,682] 128x85 vs Settings [54,364,182,492] 128x128, ratio 0.66 (rule 0.90) so E1 false. E2 true ('Connect over USB' [289,501,822,639] whole), E3 true (bar left of the action). L-FIT 0.

## HP-LAY-R

**FAIL**

Window 1280x736 RHD. Tile containers: Exit [1098,597,1226,682] 128x85 vs Settings [1098,364,1226,492] 128x128 (0.66), E1 false (text-box width check is an artifact, see HP-LAY-L). E2 true ('Connect over USB' [458,501,991,639]), E3 true (bar right). L-FIT 0.

## HP-LAY-P

**PASS**

Window 800x1216 portrait. Tile containers: Exit [593,1034,746,1162] 153x128 vs Settings [413,1034,566,1162] 153x128 (ratio 1.0), inside the window. E2 true ('Connect over USB' [80,772,613,910]), E3 true (bar along the bottom). uiq's E1 false is the text-box width artifact. L-FIT 0.

## HP-INFO

**FAIL**

I1 N/A (API 17). I2 PASS by text: placeholder line 0, SPDX 0, clock '8:17 PM' (device 20:18), speed '--' with 'km/h', date '21/10/2026'. I3: action 'Connect over USB' [289,501,822,639] whole inside 1280x736 so the date block does not push it off, but the tile check is false as in HP-LAY-L: Exit tile [54, 597, 182, 682] vs Settings [54, 364, 182, 492] (ratio 0.66). Graded on tile containers, see Setup notes.

## HP-LN

**PASS**

By text, 1280x736 launcher home without the role: no 'Exit' text (exit_count 0); action 'Connect over USB' [289,341,822,479] whole; favorites row inside the window at y 650-679 with 'All' (apps button), 'Settings', 'Add app' x3 (four slots, first label 'Settings' not 'Add app'); no widget row on API 17; pages hint 0; L-FIT 0. uiq's PASS false is a text-mode artifact: the favorite labelled 'Settings' (K_LN) makes settings_count 2, so no bar box is built and E3 reads false; by hand the Settings bar tile (x 73-162) is left of the card (x 289).

## HP-F1

**FAIL**

Predicted. Mode 1 on D-HP, 30 s after launch: last step line 'MainActivity.renderStagePill: status pill step: SEARCHING (not shown, the home's main button tells it)' 20:20:41.236 (D-HP clock); HP-F1.xml has no 'Cancel' text (HP-F1-cancel.txt 0 bytes) and no pill; the card on screen is the USB card ('Cable', 'Plug in your phone', 'Over USB cable', 'Connect over USB'), so the wireless search that is running has no Cancel anywhere. No tap or hold check possible.

## HP-C2

**UNTESTABLE**

Rig network: two attempts (20:21:48 and 20:25:01 D-HP clock, retry rule used) ended NO_SESSION. D-HP swept 192.168.1.* every ~11 s and logged no 'Found Headunit Server on ...:5277'. D-HP (192.168.1.27) and D-POCO (192.168.1.20) both reach the router 192.168.1.1 (D-HP 3 to 7 ms, D-POCO 88 to 302 ms) but not each other: ping 100% loss both ways, 'ip neigh' on D-POCO shows 192.168.1.27 FAILED, still 0 received in four probes over 32 s with both WiFi radios on. D-POCO's :149D (5277) listener was present throughout and its capture shows no connection from D-HP. The PR was not reached: no session formed, so P1 to P8 could not be read. D-HP has no working WiFi lever (rig-quirks), so the link was not changed.

## POCO-U

**PASS**

U1 classic home before (main_buttons_layout 1 line); old build 4.0.0-alpha commit 4c5960c7856e (an earlier test build, not a release APK). U2 live md5 7219f83f.. , HomePagesHintPolicy 35, commit 11a2c37fd244. U3 empty. U4 home_primary_card 1, classic 0, focus MainActivity. U5 removed/changed empty, added home-widget-host-id, three colours -1754827. U6 MATCH e53935 (raw 2400x1080, screen 2400x1080, same orientation). Gestures 0.

## POCO-CUST

**INCONCLUSIVE**

Record only, not graded. Customization opened by deep link on D-POCO 2400x1080; id:home_color_preview is not in the first-screen dump (the screen starts with the Background Image preview), so the colour preview could not be recorded. Screenshot ui/POCO-CUST.png.

## POCO-LAY-P

**PASS**

1080x2400 native portrait (screen-orientation 4 written, backup holds 3): E1..E5 true; Exit [801,2114,1014,2334] 213x220 vs Settings [556,2114,768,2334] 212x220 (ratio 1.0); bar along the bottom; action 'Connect over WiFi' whole; info 0, pill 0, L-FIT 0.

## POCO-LAY-PR

**PASS**

Portrait right-hand drive: E1..E5 true; Exit [66,2114,278,2334] 212x220 vs Settings 212x220 (1.0); bar along the bottom, mirrored order (Exit leftmost); action whole; info 0, pill 0, L-FIT 0.

## POCO-LAY-W

**FAIL**

2400x1080 landscape (screen-orientation 2, waited 12 s after launch, window wider than tall): E1 false: Exit [66,860,286,1014] 220x154 px vs Settings [66,572,286,792] 220x220 (height ratio 0.70, rule 0.90); E2..E5 true, action 'Connect over WiFi' whole; info 0, pill 0, L-FIT 0.

## POCO-INFO

**FAIL**

Landscape (backup orientation 3). I1: log half met (placeholder line 'the speed readout has no location permission, showing the placeholder' 2 times in the revoked window); dump half not readable: the app requested the permissions on launch and the system permission dialog ('Allow Open Headunit to access this device's location?') covered the home, so home_info_speed was not in POCO-INFO-a.xml. I2 PASS: placeholder 0 in b, SPDX 0, clock '8:33 pm' (device 20:33), date '21 Oct 2026', speed '--'. I3 false by rule: layout PASS false only through E1 (Exit [66, 860, 286, 1014] vs Settings [66, 572, 286, 792], the landscape squeeze); E2 true, action whole, info_count 1. Location grants restored to granted (dumpsys).

## POCO-L1

**FAIL**

Pass 3 of 3 counts (passes 1 and 2 void for the L2/L3 page-selection errors only; L1 read the same all three times). Portrait 1080x2400, compact home (N0=2 pages). G1 FAIL: hint shown once; BACK (keyCode 4 delivered) did not close it and the close reason was GOT_IT after an extra tap on home_pages_hint_got_it (BACK_DID_NOT_CLOSE_HINT in marks). G2 PASS: launcher layout E1..E5 true, no Exit tile, action whole. G3 PASS: menu holds 'Add a page', L-PADD 1, 'the home has 3 page(s), compact=true', 'showing page 3 of 3'. G4: no 'showing page 1 of 3' line after the Home key; the dump after it holds home_primary_card (page 1). G5 PASS: 'showing page 2 of 3' after the swipe. G6 PASS: L-ROLE 0, home-pages-hint-shown true. L-FIT 0. Gestures 4 of 5.

## POCO-L2

**PASS**

Added page on screen (nav2 dump: no card, no favorites, widget_add_tile [33,33,1047,1954]); last L-PAGE before the tap 'showing page 3 of 3' (20:41:16); picked first picker label 'Alarmy'; settings home-widget-pages = p7 (matches p[0-9]+); L-WNO 0; widget frame home_widget_frame [33,33,1047,977] (1014x944 px); widget_add_tile lines remaining 1; focus CarLauncherAlias so no bind dialog step. Gestures 3 of 5 (one swipe on home_favorites, tile, pick). Addendum B3 for POCO-L2: no appWidgetMinWidth or options= lines in dumpsys appwidget; density 440.

## POCO-L3

**PASS**

wmenu holds 'Remove widget' (1); after it the added page still has widget_add_tile (p2b 1 line); pmenu holds 'Remove this page' (1); L-PREM 'removed added home page 1' 20:42:19.969 then 'the home has 2 page(s), compact=true' (N0=2); settings home-widget-pages is an empty string (no p). Gestures 4 of 5 (cell long press, Remove widget, long add tile, Remove this page).

## POCO-S1

**PASS**

Attempt 2 counts (attempt 1 NO_SESSION: SYSTEM_ALERT_WINDOW not granted when the main action was tapped, the app opened the overlay settings screen; see Setup notes). ready.xml action 'Start Self Mode'. P1 SSL 1, MSSR 2, rendered=150 before bg. P2 N/A. P3 launcher menu: Move to Background 1, Stop Connection 1, Picture-in-Picture 0, Quick Settings 0. P4 L-BG '=true' 20:47:40.059, L-RAISE 0 between bg and ret; the focus read after the tap was 'mCurrentFocus=null' (transition), so the focus half is unreadable, graded on the home dump taken after it showing this app's home (self_mode_button 'Connected', action 'Back to Android Auto'). P5 self_mode_button 'Connected', pill 0, overlaps 0. P6 L-DISC 0, SSL 1, BYEBYE 0, Critical error 0 before disc. P7 focus read again null; AapProjectionActivity.onCreate and onResume 20:48:10.113/.120 after the tile tap, rendered=150 after ret. P8 BYEBYE after disc 1. Record: L-PDEST isFinishing=true 20:47:40.869, L-DEC activity_stopped and surfaceDestroyed 20:47:40.80/.86; after the return 'first frame rendered ... no keyframe has decoded yet' 20:48:10.329, 'relaunched surface has no picture after 4950ms - cycling video focus' 20:48:15.124, 'keyframe decoded - the picture is repaired' 20:48:15.924 (about 5.6 s; D-HU HU-C2b 0.85 s). Gestures 3 of 5.

## POCO-S2

**PASS**

Home mode, full menu, via ACTION_START_SELF_MODE: SSL 1 (20:44:51.734 POCO clock); POCO-S2-exit.xml holds Quick Settings 1, Picture-in-Picture 1 (pm feature count 1, API 35), Move to Background 1, Stop Connection 1; BYEBYE 0 and Critical error 0 before disc, BYEBYE 1 after (20:45:14.9 to 20:45:25.3). Overlay: 'raising the projection by DIRECT (overlay=true' 20:44:52.018. Gestures 0.

## Report back (brief section 10)

1. **Cancel.** The home's Cancel stops the wake, and the stack stays down: HU-C1 W2 and W3 (home Cancel pressed 19:49:42.410, hold line 19 ms later, then 0 pokes and 0 `WAKING_PHONE` lines through 19:50:54, progress bar and Cancel gone) and SAM-C1 (all of W1 to W3). HU-C1 is FAIL only on W1's subtitle: on page 1 the card sits above the favorites row, `fitCardText` hides 3 lines ("the main button is 373px tall, hiding 3 line(s)") and "Waking your phone" is not on screen, though progress, Cancel and the Connecting tile are. **F1 is a FAIL, as predicted:** HU-F1 (page 2, `WAKING_PHONE` up) has no Cancel and no pill; HP-F1 (`SEARCHING` up on D-HP, card showing the USB connection) has no Cancel and no pill either.
2. **Move to Background.**

| Run | L-BG | Focus after | L-DISC / L-SSL | Phone BYEBYE / Critical before disc | Focus after return | L-PDEST |
|---|---|---|---|---|---|---|
| HU-C2a | false | factory launcher | 0 / 1 | 0 / 0 | AapProjectionActivity (L-RAISE 1) | none |
| HU-C2b | true | this app's home (CarLauncherAlias) | 0 / 1 | 0 / 0 | AapProjectionActivity | isFinishing=true 19:53:10.589 |
| HU-C3 | true | this app's home | 0 / 1 | 0 / 0 | AapProjectionActivity | isFinishing=true 19:56:06.173 |
| SAM-C2 | false | Samsung launcher | 0 / 1 | 0 / 0 | AapProjectionActivity (L-RAISE 1) | none (decoder 'restart: sync_stall' once) |
| HP-C2 | UNTESTABLE | | | | | |
| POCO-S1 | true | read null (transition), home dump shows this app's home | 0 / 1 | 0 / 0 | read null; onCreate and onResume 20:48:10 | isFinishing=true 20:47:40.869 |

   The session survived in every run that formed one. The projection activity is destroyed and recreated when Move to Background goes to this app's home (review Warn 8 seen, but harmless to the session). The literal phone string `ByeByeRequest` does not exist in Gearhead 18.0.664204; the phone logs `PROJECTION_ENDED_BYEBYE_BY_CAR`, which every run used for the disconnect bullets.
3. **Layout.** E2 to E5 are true on every unit and case (the action is whole inside the card, the bar sits on the driver's side, no overlap, title inside the card). **E1 fails in every landscape case:** the Exit tile is squeezed to 70 percent of the Settings tile's height because the bar of four tiles needs more room than the window gives. D-HU 1440x720: Exit `[36,600,156,684]` 120x84 vs Settings 120x120 (left and right-hand drive). D-SAM 1280x800: Exit `[54,661,182,746]` 128x85 vs 128x128 (left and right). D-HP 1280x736: Exit tile 128x85 vs 128x128 (left and right, measured on the tile containers). D-POCO landscape 2400x1080: Exit `[66,860,286,1014]` 220x154 vs 220x220. In portrait E1 is true: D-SAM Exit `[593,1098,746,1226]` 153x128 equal to Settings; D-HP 153x128; D-POCO 213x220 vs 212x220, also in right-hand drive. L-FIT lines: 1 in all runs (HU-L1 fitWidgetStrip, HU-C1 fitCardText), none in the layout runs.
4. **Upgrade.** D-HU: old 3.5.0-beta4 `fc3469823c69`, classic home before, removed/changed empty, colours kept, card `e53935` MATCH. D-SAM: old 3.5.0-beta5 `8db52cebe6bf`, same results (U6 N/A). D-HP: old 3.5.0-beta4 `802920baf0ae`, same by text. D-POCO: old 4.0.0-alpha `4c5960c7856e` (an earlier test build, not a release), same, `e53935` MATCH. The only added key is `home-widget-host-id` (D-HU, D-POCO).
5. **Launcher.** G1 FAILS on D-HU and D-POCO: the hint shows once but BACK does not close it (the key reaches `MainActivity.dispatchKeyEvent`); it closes on a touch (`LEFT`) or on "Got it" (`GOT_IT`). While it is up it swallows the next gesture. G2, G3, G5 and G6 pass on both; after the Home key the page-1 log line is absent on both (the pager is rebuilt on page 1; the dump shows it). Widget on the added page: D-HU HU-L2 frame `[192,18,798,669]` (606x651 px), D-POCO POCO-L2 frame `[33,33,1047,977]` (1014x944 px); both removed with the page in HU-L3 and POCO-L3. HU-W1 (addendum): B2 CONFIRMED, the row widget's frame is exactly minimum width times density (495 px for a 330 px minimum at 1.5); B3 not measurable (no options in `dumpsys appwidget`).
6. **PiP.**

| Unit | pm feature count | PiP row in exit menu |
|---|---|---|
| D-HU (API 34) | 1 | present in the full menu (HU-C2a), absent in the short launcher menu (HU-C2b, HU-C3) by design |
| D-SAM (API 19) | 0 | absent (SAM-C2) |
| D-HP (API 17) | 0 | not read (HP-C2 UNTESTABLE) |
| D-POCO (API 35) | 1 | present in the full menu (POCO-S2), absent in the launcher menu (POCO-S1) |

## Anything the brief did not ask about

- **The Exit tile squeeze is the main finding of the layout runs.** It is the same 0.66 to 0.70 ratio on four different screens in landscape, and is absent in portrait. It is whole and labelled, so it is a squeeze, not a drop-off.
- **BACK does not close the pages hint**, and an open hint takes the first touch. A user driving by keys or a wheel cannot dismiss it without a touch.
- **D-POCO's Self Mode main button opens the overlay permission screen when SYSTEM_ALERT_WINDOW is off**, instead of starting Self Mode (POCO-S1 attempt 1, `AppOpsPermissionControl isAllowed: false`). At 20:43:49 a back edge-swipe closed that screen; none of my scripts sent it, so a person touched the screen. The permission read `allow` afterwards and I did not change it; I did not capture its baseline in the pre-flight. Please say whether the operator granted it.
- **D-HP's home names USB as the main connection** (no WiFi history, so the first chosen mode wins), and the bar shows only Settings and Exit text; WiFi mode 1 still started by itself and swept the LAN.
- **D-HP and D-POCO had no LAN path to each other** on the house WiFi (ARP FAILED, 100 percent ping loss both ways, both reach the router; D-POCO to the router 88 to 302 ms). That is a rig fault, and HP-C2 should be re-run once the two can see each other.
- **After the return from Move to Background D-POCO showed no picture for about 5.6 s** until the app's own keyframe recovery fired ('relaunched surface has no picture after 4950ms' then 'keyframe decoded'); D-HU took 0.85 s.
- **The wake list gap:** the brief's `K_CONN` does not set `native-poke-bt-macs`, and the unit's backup held only D-POCO, so D-MOTO was never poked until I wrote its MAC (HU-C2a attempt 1). The brief should write the wake list.
- **Brief errata found:** (1) `su -c` for D-HU's settings pull (the unit is root over adb, no `su`); (2) the start marker after the launch loses the first `onResume` and the pages hint line, and the Move to Background, Cancel and widget taps precede their markers; (3) the extractor script expects backticked names the brief does not use; (4) `hu_cblock.sh` is a script, not a function; (5) the appwidget `min=` values are encoded dimensions, not px, and the addendum assumes 320 dpi while D-HU reads 240; (6) the pages loop needs a dump check that also covers a compact home (the favorites page has no card either) and a swipe on the favorites row, because the widget row eats a swipe at the pager centre; (7) uiq.py text mode cannot grade E1 (it compares text boxes) or E3 when a favorite is also named Settings.
- **D-HU's clock read June 2026** (the operator asked me to fix it; set to the host time, 0 s apart) and D-SAM's clock runs about 21 s behind the host.
- **D-POCO is bonded to both D-HU's and D-SAM's radios**; SAM-C2 needed the one retry (first connect-back landed 3 s after the 120 s cap), without the forget-car contingency.
- **D-HU's Bluetooth self-reverts on** within minutes of `svc bluetooth disable`; I re-disabled it before each stage B connection run.
- **D-SAM looped between the searching and looking cards and never connected (found by the operator after the runs, Headunit Server mode, D-POCO as server).** About every 14 s: `Found Headunit Server on 192.168.1.20:5277`, `Auto-connecting`, `SECURING`, three `No VERSION_RESPONSE` attempts, `the peer accepted the connection and then sent nothing at all ... Android Auto's own side`, `session state failed (peer_silent)`, `Restarting discovery loop in 2s`. The app stamped `connection-issue-headunit-server-deaf` at 21:08:08 but the banner was not on the home for the whole loop and appeared only after a HOME press and relaunch (resume refresh). The card also flips between a Cancel layout ("Looking for your phone") and a Search again layout ("Searching for your phone..."). The write-up with the questions for the coding agents is `pr-1119-home-redesign-round1-d-sam-loop-findings.md` on this branch; the logs, screenshots and dumps are in the round's captures asset under `for-coding-agents-d-sam-loop/`. Whether D-POCO's server is wedged, and whether the classic home loops the same way, is not known.
- **Update on the D-SAM loop:** it connected by itself at 21:11:17 on D-SAM's clock and has streamed since (same app process, no change to the app). The first session was reset by the peer 3 s after SSL (`ECONNRESET`), the second held. The connection and AAP code is unchanged between the base this build was cut from and the PR head (only `AapProjectionActivity` and a new `AudioModeWatcher`); the new home does change who starts connections (auto-connect countdown, resume). Reporter reports older builds did not loop in this setup; no older-build run exists. An A/B is proposed in the findings file.
