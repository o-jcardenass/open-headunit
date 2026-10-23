# pr-1119-home-redesign, round 3 results

**Candidate:** upstream `refs/pull/1119/head` @ `135131ce18430b52c773f2ce456f8c8683de9c0c` (tree `88890a53...`, verified by `build.sh`)   **Baseline:** none (round 2 measured `0d333b19`)
**APK md5:** C `18bf82fc9a7b65ef6461f524b22c3eb6` / no baseline. Build gate: `NoticeClearancePolicy` 18, `SpeedPermissionPolicy` 5 (both at least 1). `apk_identity` exit 0 and commit `135131ce1843` on D-HU, D-POCO and D-HP.
**Unit:** D-HU (MT50, Android 14, 720x1440, 240 dpi, rooted), D-MOTO as phone (Gearhead 18.0.664204-release), D-HP (Slate 7 Plus, API 17), D-POCO (Android 15, SDK 35) for the speed switch.
**Date:** 2026-10-22
**Evidence:** release `rig-evidence-pr-1119-home-redesign`, asset `pr-1119-home-redesign-round3-captures.zip`, sha256 `261cca2b1cf0d2bc1362830f280313484cdd8e7e3005b037ec7e57936820a559` (16087207 bytes; APKs left out).

## Verdicts

| Run | Verdict |
|---|---|
| HU-CD | PASS (brief's digit rule mistimed, see section) |
| HU-B5 | PASS (finished path only; first pass void, re-run once) |
| HU-W12a | PASS |
| HU-W12b | PASS |
| HU-ROW | PASS |
| HU-B1 | UNTESTABLE (void twice) |
| HU-W8 | PASS (brief's `critical` = 0 read 5, all after the car's BYEBYE) |
| HU-U1 (RECORD) | UNTESTABLE (dump failed twice) |
| HU-W10 (RECORD) | RECORD: no system screen, switch reverted |
| HP-RING (RECORD) | RECORD: clear centre, number readable |
| POCO-U3a (RECORD) | UNTESTABLE (NO_ROUTE) |
| POCO-U3b (RECORD) | UNTESTABLE (NO_ROUTE) |

## Setup notes

- **Branch and tooling.** The transfer branch was squashed on 2026-10-22; the scratch worktree that held it was reset to `1251969bc` (clean, nothing unpushed) because the main checkout could not check it out. `rig_sync.py sync`: in sync. Quirk files read: `tooling.md`, `units/D-HU.md`, `D-MOTO.md`, `D-POCO.md`, `D-HP.md`. Scripts: the brief's 14 blocks extracted with `extract_brief_blocks.py` (run as written), plus one one-round script `scripts/hu_b5_rerun.sh` (below). No toolkit script added or changed.
- **Opening pre-flight** (`rig_preflight.sh D_HU:wifi,bt D_HP:wifi D_POCO:wifi D_MOTO`): PREFLIGHT OK. D_HU wifi 1 bt 1 Awake; D_HP wifi 1 bt 1 Awake; D_POCO wifi 1 bt 1 Dozing; D_MOTO wifi 1 bt 0 Awake; no strays, lock free. The brief's own `preflight.sh` printed one FAIL line, the round's own Gradle daemon (build running beside it). Gates: D-HU uid 0, home role `com.android.launcher3`, `HU_OWNER=u0_a176:u0_a176`, VLC `MiniPlayerConfigureActivity` present (1), input usage lists `--longpress` only so `LPARGS="--longpress"`, `HU_LPT=400`, D-MOTO on NotificationShade (no hand step 1), Moto throttle lines 0. Opening state: animator scale HU 1.0 / POCO 1.0 / HP 0, HU BT true, POCO BT true, MOTO wifi enabled bt false, POCO fine/coarse granted, HU alias count 0.
- **Markers.** `ACTION_LOG_MARKER` is not in `AutomationCommandPolicy.CONFIGURING` at `135131ce`; no `allow-external-configuration` written.
- **Animations off** on D-HU, D-POCO and D-HP (brief section 3). The operator saw the "loading symbol" not move while Android Auto was starting; that is the likely effect of this setting, not a PR finding.
- **HU-W10** taps (9) accepted by the operator at the brief gate. No hand step 2 was needed.
- **HU-B5 deviation.** VLC's widget setup is a multi-step screen (cfg1 dump: `VLC widgets` / `NEXT`). In pass 1 one BACK did not close it, so the measure at 22:35:13 was taken with the setup still in front; the setup closed only on HU-W12w's BACK at 22:35:33 (after which the app ran `placeWidget`). Pass 1 measurement void; re-run once with `scripts/hu_b5_rerun.sh` (a copy of the B5 block that repeats BACK up to 3 times, reading focus after each, and dumps the bound ids before the picker). Also, the HU-W12w gestures got `NO_TARGET` twice (VLC in front); W12a/W12b then ran with the VLC widget placed by that BACK.
- **HU-CD deviation.** Dump c was taken at 22:34:10, two seconds after the countdown's own USB request (22:34:08), so it holds no countdown node; the brief's "c is 1 or more" is a timing flaw. Graded on intent (below).
- **HU-C ran twice** (pass 1 22:41 to 22:45, pass 2 22:46 to 22:50; copies in `pass1/`, `pass2/`). `uiautomator dump` ran 10 s and wrote no file while the home sat on the armed or waiting step (`avc: denied` noise, no file), twice per pass, so HU-U1 never reached its X and burned about 60 s; the 60 s USB countdown then ran out during HU-B1 (`Auto-connect: begin (auto-connect single USB, mode=OVERLAY)` at 22:42:19 and 22:48:03) and the "Android Auto is starting..." overlay covered the home. HU-U1 and HU-B1 are void twice. In pass 2 every HU-U1 file was a stale pass 1 copy (mtime 22:42:21). Brief defect: HU-U1 should not sit before HU-B1 on one 60 s countdown, and the armed home cannot be dumped. HU-W8 was clean both passes. The `taps-`/`keys-` counters for HU-U1, HU-B1, HU-W8 accumulate across the two passes; no single pass exceeded a cap.
- **POCO-U3.** `run-as am start` of `SettingsActivity` is refused on D-POCO (below).
- **Restore and closing pre-flight.** `restore.sh`: original APKs back on D-HU, D-POCO and D-HP (`apk_identity` exit 0 each), `SETTINGS_BACK` on all three, home role `com.android.launcher3`, alias 0 (opening 0), D-POCO grants fine and coarse true, animator scale HU 1.0 / POCO 1.0 / HP 0, radios as opening. The script's own closing pre-flight printed one FAIL for the idle Gradle daemon (pid 142319, left by the build); `rig_cleanup.sh kill` cleared it and the re-run read PREFLIGHT OK: D_HU wifi 1 bt 1, D_HP wifi 1 bt 1, D_POCO wifi 1 bt 1, D_MOTO wifi 1 bt 0 (all as opening). No logcat left running.
- **Host thermal.** `th_gate` passed at every block (build gate at 65 C).

## R-HU-CD

**PASS**

- Settings: `K_CD` (single-usb true, delay 25, mode 0, onboarding 2, disclaimer true).
- Reach: `focus` names `SettingsActivity`; digits a = 20.
- `fatal` = 0; `pid1` = `pid2` = 9589.
- Digits a 20 > b 5; dump c has no countdown because the USB request fired first: `22:34:08.104 HomeFragment: Requesting single-USB auto-connect via AapService` (c dump 22:34:10). `usb_req` = 1.
- `waiting` = 2 (22:33:42.166 and 22:33:50.866: the home Settings built also started a countdown), `res_after_back` = 1.

The dropped home's countdown did not crash the app (the 174a52fd failure would have shown `fatal` of 1 or more). The strict "c is 1 or more" read does not hold only because the dump came after the countdown ended; I graded the intent and say so here.

## R-HU-B5

**PASS**

- Pass 1 void (see Setup notes). Pass 2 (`HU-B5r`, 22:39): result `CONFIGURE1`, focus `org.videolan.vlc/...MiniPlayerConfigureActivity`; keys 3, taps 2.
- After one BACK the app was in front and placed id 8: `stored=[8]`, `our_ids=[3,5,6,7,8]`, `frames_after` = 1, `released` = 0, `via_host` = 0. This is the brief's PASS (finished) path.
- `leaked=[3,5,6,7]`: those ids were bound to our host before the run (present in the "before" dump; the seed erases `home-widgets` without removing host bindings, and one is the first pass's widget). Nothing new leaked.
- The cancel path (`was not set up, releasing its id`) was not exercised in either pass: VLC's setup answered OK to BACK.
- Record: provider `org.videolan.vlc/org.videolan.vlc.widget.MiniPlayerAppWidgetProvider`; the placed tile shows "Couldn't add widget." with `AppWidgetHostView: RemoteViews$ActionException ... AppCompatImageView can't use method with RemoteViews: setImageBitmap` at 22:35:33.979 (a VLC provider problem on this build).

## R-HU-W12a

**PASS**

- `walk=HERE` after 2 moves (DOWN to `usb_button`, RIGHT to `home_primary_card`), `hold_ms` 535 against `lpt` 400, `menu` = 1 (`Add a page`), `title_pages` = 1, keys 4.
- Quoted: `22:35:56.422 dispatchKeyEvent: keyCode=23, action=0` ... `22:35:56.957 ... action=1`.
- For the eye: `HU-W12a-m2.png` shows a white ring round the card; `HU-W12-k0.png` has none.

## R-HU-W12b

**PASS**

- `walk=HERE` after 4 moves (`favorite_slot_2`, `favorite_slot_1`, `apps_button`, `home_widget_frame`), `hold_ms` 531 against 400, `menu` = 1 (`Remove widget`), keys 6.
- Quoted: `22:36:37.189 ... keyCode=23, action=0`, `22:36:37.720 ... action=1`.
- The widget is VLC's (tile reads "Couldn't add widget."). `title_pages` = 0, which is not a condition for this run.

## R-HU-ROW

**PASS**

- Gesture: `long xy:row-free-space 1075 630 22:36:56`; `menu_add_page` = 1.

## R-HU-B1

**UNTESTABLE**

- Void twice. Pass 1 reach: `pokes` 1, `waking` 2; pass 2: `pokes` 2, `waking` 2. Both passes: `P2 pill` = 0, `x_inwin` = 0, `card_cancel` = 1, `NO_TARGET` for `auto_connect_pill_cancel`.
- Cause (compared with the dumps): `HU-B1-p2.xml` holds `auto_connect_loading_overlay` ("Android Auto is starting...", Cancel) because the USB countdown had fired (22:42:19, 22:48:03) before the swipe. The brief's order spent the 60 s on HU-U1 first.
- Not graded as a PR FAIL. A B1-only run (launch, scan verb at once, dump only at `WAKING_PHONE`, all inside 60 s) would measure it; the brief says a second void is UNTESTABLE, so I did not add a third attempt.

## R-HU-W8

**PASS**

- Reach (pass 1 and pass 2 identical): `ssl` 1, `mssr` 2, `rend` 2, role `com.andrerinas.headunitrevived`.
- Menu: End session stay ready 1, Stop Connection 1, Move to Background 1, Picture-in-Picture 0, Quick Settings 0, Switch Phone 1.
- `ESA activity` 1, `service` 1. `NET keep` 1, `stop` 0, `groups_after` 0: `22:44:07.422 AapService: Native AA session ended; keeping the WIFI_DIRECT network up for the phone's return.` (pass 1), `22:49:49.927` (pass 2).
- Phone: `byecar_after_pick` 1 (`PROJECTION_ENDED_BYEBYE_BY_CAR`, 22:44:08.618), `throttle` 0. `critical` read 5, all at 22:44:08 to 22:44:09 after the BYEBYE (`GH.WirelessStartup: Critical error encountered`, `CAR.SERVICE: Critical error 3 ... io error`): teardown of the ended session, none before the pick. The brief's "critical = 0" counts them; I read it as a protocol error before the end.
- Record: `RETURN` ssl_in_45s = 0 (the phone did not come back to the kept network in 45 s), `match` 0, `groups` 3.

## R-HU-U1

**UNTESTABLE**

- RECORD run. `uiautomator dump` failed twice per pass on the armed home (`22:41:19` to `22:41:29`, then no file); `NO_LISTEN` both passes. Step lines show bare `ARMED` / `WAITING_FOR_PHONE` (`listen_bare` 3 and 4), so the pill was up, but the X was never dumped or tapped. `usb_req` = 1 in pass 1 (the countdown ran out), 0 in pass 2 before the run ended. The review's prediction (`cd_stopped` = 1) was not measured.

## R-HU-W10

**RECORD**

- 9 taps (8 `Next`, 1 switch), `taps` 9, `fatal` 0. `switch_before=false`; after the flip focus stayed `OnboardingActivity` (no system screen); `switch_after=false`; role `com.android.launcher3`; `granted` 0, `reverted` 1, `role_fail` 0.
- Alias: `22:53:16.408 CarLauncherManager: set launcher alias enabled=true`, `22:53:16.883 ... enabled=false`.
- This is review N2's prediction on API 34 (tracked upstream); not a FAIL of this PR.

## R-HP-RING

**RECORD**

- `waiting` 1, `number_nodes_a` 1, `capture_restarts` 0. `HP-RING-a.png` (API 17): the ring has a clear centre in the card's orange with a readable `14`; no black disc.

## R-POCO-U3a

**UNTESTABLE**

- `NO_ROUTE`. `run-as com.andrerinas.headunitrevived am start -n .../SettingsActivity --ei extra_destination 2131296477` answered `java.lang.SecurityException: Permission Denial: package=com.android.shell does not belong to uid=10280`; focus stayed on `com.android.launcher3`. Grants were revoked before (fine and coarse both `granted=false`). No scripted way into Customization, so no switch, dialog or `blocked` line.

## R-POCO-U3b

**UNTESTABLE**

- Same `NO_ROUTE` and same refusal as POCO-U3a.

## Anything the brief did not ask about

- **Status pill and the "starting" overlay during a Native AA bring-up (operator-observed, matched in logs).** The operator saw the pill present while the selected screen was WiFi Native, the loading symbol not moving, and the end of the session looking wrong. In the capture: `status pill step: SECURING` and `STARTING_PROJECTION` shown on the pill during the bring-up (22:43:44.188 to .967); the "Android Auto is starting..." overlay with a frozen spinner (animations off on the rig) covered the home whenever the USB countdown's attempt fired during a wake.
- **End session, stay ready shows a reconnect overlay.** After the pick: `22:49:49.554 AapProjectionActivity: Disconnected unexpectedly.`, `22:49:49.567 Unexpected disconnect. Showing reconnecting overlay and waiting at least 20s for recovery.`, then the activity is destroyed, and `22:50:09.584 Reconnect gave up after 20s (BASE_EXPIRED). Finishing activity.` fires 20 s later on the destroyed activity (pass 1: 22:44:07.052/.065 and 22:44:27.095). A user-requested end is handled as an unexpected disconnect. Also `22:49:49.864 status pill step: STARTING_PROJECTION (not shown ...)` then `hidden` at 22:49:49.907.
- **A VLC widget on D-HU renders "Couldn't add widget."** (`setImageBitmap` RemoteViews error), likely the provider on this build.
- **Host bindings leak on reseed.** Seeding `home-widgets` away leaves the app widget ids bound to the app's host (ids 3, 5, 6, 7 here); not an app fault, but it makes `b5check`'s `leaked` unreliable across runs unless the "before" dump is read.
- **Brief errata for the next author.** HU-U1 must not precede HU-B1 on one 60 s countdown; the armed home cannot be dumped (use dumps only at `WAKING_PHONE` or later); HU-CD's third dump falls after the countdown ends (use a shorter gap or a 40 s delay); POCO-U3's `run-as am start` is refused (needs a hand tap into Customization or another route); B5's single BACK is not enough for VLC's two-step setup.
