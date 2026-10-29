# pr-1119-home-redesign, round 2 results

**Candidate:** `fork/review/pr-1119-r2` @ `0d333b19bef3349313a3c0d233256a408317d06c` (tree `3135b119...`, verified by `build.sh`)   **Baseline:** none (round 1 measured `11a2c37f`)
**APK md5:** C `65bac2634085a1c71c7301ff3dee75ad` / no baseline. Build gate: `ProjectionExitMenuPolicy` 6, `HomeCardFitPolicy` 5 (both at least 1).
**Unit:** D-HU (MT50, Android 14, 720x1440, density 1.5, rooted), D-HP (Slate 7 Plus, API 17), D-POCO as head unit (Android 15, density 2.75), D-MOTO as phone (Gearhead 18.0.664204), D-SAM (API 19) for the optional run.
**Date:** 2026-10-22
**Evidence:** release `rig-evidence-pr-1119-home-redesign`, asset `pr-1119-home-redesign-round2-captures.zip`, sha256 `993eb8b90db2067be8f8e7ef64658b2875b0b9a83f2ae589c949d57db074f092` (10940284 bytes; APKs left out, the md5 above identifies the build).

## Verdicts

| Run | Verdict |
|---|---|
| HU-B6 (B6) | PASS |
| HU-W1 (B2) | PASS |
| HU-W1 (B5) | UNTESTABLE |
| HU-B4 (B4) | PASS |
| HU-F1 (B1, the point) | PASS |
| HU-W16 | PASS |
| HU-W8 | PASS |
| HP-W12 (W12) | PASS |
| HP-F1 (B1) | PASS |
| POCO-B6 (B6) | PASS |
| POCO-W1 (B2) | PASS |
| SAM-HS (optional) | PASS (second attempt; the first stopped at a ping gate, see its section) |

## Setup notes

- **Resolving the argument.** Two SHAs were given. `7527c84f0` adds `pr-1119-home-redesign-round2-brief.md`; `b0f379900` is a README-only commit after it. Round resolved to `pr-1119-home-redesign` 2.
- **Transfer branch.** The main checkout cannot hold `transfer/rig-rounds` (another worktree has it), so the scratch worktree `rig-sync__scratch__8228cb07__20261015` was fast-forwarded and used. `rig_sync.py` carried two live hook files into the branch; they were committed and pushed as `468c307e1` ("Rig SDLC: add rig-kill-guard hook"). Second sync: in sync. `CLAUDE.md` unchanged, no restart needed.
- **Pre-flight.** Skill pre-flight with D-MOTO needing Bluetooth failed (D-MOTO bluetooth off). The brief's scripts switch D-MOTO's radios themselves (`preflight.sh` turns them off, `hu_c.sh` turns them on) and name only wifi for the units, so it was re-run without D-MOTO needs:

  ```
  ROLE     ADB      WIFI   BT     SCREEN    BT-PROFILES
  D_HU     ok       1      1      Awake     HFP:none A2DP:none
  D_HP     ok       1      1      Awake     HFP:none A2DP:none
  D_POCO   ok       1      0      Dozing    HFP:none A2DP:none
  PREFLIGHT OK    rig clean: no strays, lock free
  ```

  Opening state recorded by the brief's `preflight.sh`: animations HU 1.0, POCO 1.0, HP 0; HU bt true, POCO bt false, POCO stay-on 0; D-MOTO wifi enabled, bt false; home role holder `com.android.launcher3` on D-HU and D-POCO; D-MOTO not on a lock screen (WhatsApp in front; HOME sent before HU-C); Gearhead 18.0.664204-release on D-MOTO and D-POCO; D-MOTO throttle lines 0; D-HU `uid=0`, `shared_prefs` owner `u0_a176:u0_a176`, density 240 dpi (1.5); D-POCO 440 dpi (2.75). Exported set-up activities on D-HU: `com.android.systemui/.people.PeopleSpaceActivity`, `org.videolan.vlc/.widget.MiniPlayerConfigureActivity`.
- **Quirk files read:** `topics/tooling.md`, `topics/lifecycle.md`, `units/D-HU.md`, `units/D-HP.md`, `units/D-POCO.md`, `units/D-MOTO.md`.
- **Brief erratum, fixed in the extracted scripts.** Line 1 of `hu_l.sh`, `hu_c.sh`, `hp.sh`, `poco_l.sh` and `sam_hs.sh` set `H=$D_xx` before `lib.sh` defined `D_xx`, so `H` was empty and every `adb -s $H` call failed (`adb: unknown command am` / `cmd`). `role_on`, `role_off`, `send`, `launch` and markers did nothing. The first HU-L attempt was voided within one minute (app never launched, no gesture, no verb); seeding had used the explicit serial and was correct. The scripts now source `lib.sh` first and then set `H`. HU-L was re-run once (the allowed re-run), all other blocks ran first time. The void attempt's marks are in `marks-void1.txt`. The brief's next revision should apply the same one-line change.
- **Scripts used.** The brief's own blocks, extracted with `extract_brief_blocks.py`; toolkit tools `rig_preflight.sh`, `rig_cleanup.sh`, `rig_thermal.sh`, `build_hur_cool.sh`, `wt-new.sh`, `apk_identity.sh`, `set_hu_settings_host.py`, `set_prefs_runas_host.py`. No script added to `rig-toolkit/`.
- **Build.** Worktree `pr-1119-home-redesign__candidate__e9377ff9__20261022`, one APK. No unit tests run on the rig (the brief says not to).
- **Installs.** D-HU, D-POCO, D-HP (and D-SAM later): `apk_identity ... exit 0`, commit `0d333b19bef3`, no `-dirty` suffix reported.
- **Gestures.** HU-L: HU-B6 1 long press; HU-W1 4 taps ("+" and a picker entry, twice), each within its run's budget of 5; HU-C: 2 swipes and the pill's X; HP: 1 tap (`desc:Stop connecting`); POCO-L: 1 long press, 1 swipe, 2 taps. No `NO_TARGET`, `BUDGET_SPENT` or `DUMP_FAILED`. Gestures in `gestures-*.log`.
- **Discard rules.** `MATCH! Starting AapService` count 0 in HU-L and HU-C. HU-C shows two `createGroup SUCCESS` (12:47:10 and 12:48:59, interfaces `p2p-wlan0-0` and `-1`): the second follows the HU-W8 `ACTION_START_WIRELESS_SCAN`, which re-armed the stack after the pill's X had stopped the first group. Not a contamination.
- **Thermal.** `th_gate` passed before each block; host package temperature peaked at 68C, throttle counter 0 in every `thermal-*.log`. A thermal watch left over from the voided HU-L attempt (pid 16766) kept running until it was killed by pid after SAM-HS; its log `thermal-HU-L.log` therefore spans to 13:00.
- **Capture exit code.** `hp.sh` printed `script exit=1`: its last line is `grep -c 'capture restarted' marks.txt`, which prints 0 and exits 1. Not a failure; no capture restart occurred.
- **Restore and closing pre-flight.** `restore.sh` put back each unit's opening APK (md5 `7219f83f...`, `apk_identity exit 0` on D-HU, D-POCO, D-HP, D-SAM) and settings (`SETTINGS_BACK` on all four), the home role (`com.android.launcher3` on D-HU and D-POCO), D-HU bluetooth on, D-POCO bluetooth off and wifi on, D-POCO stay-on 0, D-MOTO wifi on and bluetooth off, animations (HU 1.0, POCO 1.0, HP 0). One idle Gradle daemon (pid 12759) was cleared with `rig_cleanup.sh kill`, as the skill allows. Closing pre-flight:

  ```
  ROLE     ADB      WIFI   BT     SCREEN    BT-PROFILES
  D_HU     ok       1      1      Awake     HFP:none A2DP:none
  D_HP     ok       1      1      Awake     HFP:none A2DP:none
  D_POCO   ok       1      0      Awake     HFP:none A2DP:none
  PREFLIGHT OK
  D-MOTO: wifi Wi-Fi is enabled bt   enabled: false
  27870808938846 anim 1.0
  4f4027e9 anim 1.0
  CNU350BGBJ anim 0
  poco stayon 0
  ```
- **SAM-HS** was optional and the operator chose to run it. The first attempt stopped at the script's ping gate (D-SAM not on the network); after the operator rejoined D-SAM it ran in full with one server-restart hand step. See its section.

## HU-B6

**PASS**

- Settings: `K_LAUNCH` (launcher mode, favorites row, hint and widget keys deleted).
- Counts (`HU-B6-counts.txt`): `hint=1 hint_dump=1 key4=2 closed_back=1 after_back_hint=0 menu_add_page=1 closed_left=0 closed_gotit=0`.
- Decisive line: `12:44:34.226 HomeFragment: pages hint closed (BACK)`. The long press after BACK opened the page menu (`Add a page` present), so the next touch was not swallowed. Own grep of `closed_back` over the capture agrees (1).

## HU-W1 (B2)

**PASS**

- Provider `com.android.chrome/.../BookmarkThumbnailWidgetProvider`, `min_raw=46081` (dim1 = 180 dp), `min_px=270`, density 1.5, add tile 144 px.
- `frame1`: `expected=270 bug=405 actual=270 ok=1 discriminating=1 clipped=0`. The fix gives 270 px; the round 1 behaviour would have given 405 px.
- Second widget (`QuickActionSearchWidgetProviderDino`) actual 165 px; the `placed2` and `after` dumps hold no "+" tile once two widgets fill the row, so `expected=NA` there. Graded on `placed1` as the brief says.

## HU-W1 (B5)

**UNTESTABLE**

- Both picker entries tried (Chrome bookmarks, Chrome quick search) were placed without a set-up screen: `attempt1=PLACED attempt2=PLACED`, `released=0 via_host=0`. The brief grades B5 only when an attempt reads `CONFIGURE`. D-HU's exported set-up activities were `PeopleSpaceActivity` and VLC `MiniPlayerConfigureActivity`; neither was among the first two entries.

## HU-B4

**PASS**

- Reach: `HU-B4-push.txt` `SETTINGS_BACK`; before value `com.android.settings,com.android.settings|rig_b4_probe,com.rig.b4.absent,`; role holder `com.android.launcher3`.
- `keep=1 gone=0 absent_freed=1`.
- Decisive lines (12:45:56): `HomeFragment: favorite shortcut com.android.settings/rig_b4_probe cannot be read now, keeping its slot` and `HomeFragment: favorite com.rig.b4.absent is no longer installed, showing its slot as free`.
- Stored value after the launch: `home-favorite-apps=com.android.settings,com.android.settings|rig_b4_probe,,` (exact match).

## HU-F1 (B1, the point of the round)

**PASS**

- Settings: `K_HUC` (mode 3, launcher, wake list = D-MOTO's MAC, `home-widget-pages=p`, hint shown). Radios: D-HU bt true, D-POCO bt false and wifi held off, D-MOTO wifi and bt off until HU-W8.
- Reach: `pokes=1 waking=4` before the X.
- Page 2 (`F1 p2`): `pill=1 x_inwin=1 card_cancel=0`; page 2 on the first swipe (`p2a`) the same. The pill's X was on screen on page 2, which round 1 lacked.
- `STEP bare_after_sw1=1 bare_after_sw2=2`: `12:47:35.314 MainActivity: status pill step: WAKING_PHONE` (no "not shown" suffix) and `12:47:41.291 ... WAITING_FOR_PHONE`.
- Cancel: `12:47:45.752 MainActivity: status pill X pressed, stopping the bring-up` and `12:47:45.768 AapService: the status pill's X stopped the wireless bring-up.` (`x_line=1 hold_line=1`).
- After the X for 60 s: `pokes=0 waking=0 after_x=0`.

## HU-W16

**PASS**

- Both dumps: `subtitle=[Waking your phone] cancel_inwin=1 progress=1`, including `wakeB`, the page 1 view after HOME that round 1 failed.
- `FIT` lines (record): `12:47:10.332 the main button is 332px tall, hiding 3 line(s) so its action fits` (twice) and `12:47:27.674 ... 373px tall, hiding 3 line(s) ...`. The step line stayed.

## HU-W8

**PASS**

- Reach: `ssl=1 mssr=2 rend=2`; role holder names the app. Lines: `12:49:14.465 Media Sink Setup Request: 3 on channel VIDEO`, `12:49:20.763 Throughput over 5010ms: rendered=67`, then 30 fps.
- Exit menu: `End session, stay ready` 1, `Stop Connection` 1, `Move to Background` 1; `Picture-in-Picture` 0, `Quick Settings` 0. Record: `Switch Phone` 1.
- Phone: `critical=0`, `byecar_after_disc=1` (`12:49:39.313 ... PROJECTION_ENDED_BYEBYE_BY_CAR`), `throttle=0`.
- Record: `match=0`, `groups=2` (see Setup notes).

## HP-W12

**PASS**

- `k1 DPAD_DOWN`: focus on the WiFi button (`is_card false`). `k2 DPAD_RIGHT`: `"is_card": true`, label `Cable | Plug in your phone | Over USB cable | Connect over USB`, bounds [236,27,1253,682]. `keys_reached=1`.
- For the eye only: `HP-W12-k2.png` shows a thin white outline around the card that `HP-W12-k0.png` does not.

## HP-F1

**PASS**

- Arm a (`last-connection-type=usb`): `first_search_step=[SEARCHING]` with no suffix, `pill_x=1 card_cancel=0 card_title=[1]` (title `Plug in your phone`), `scans_before=2`; `12:51:35.128 MainActivity: status pill X pressed, stopping the bring-up`, `12:51:35.138 AapService: the status pill's X stopped the wireless bring-up.`; `x_line=1 hold_line=1 scans_after=0` over 30 s.
- Arm b (`wifi`): four dumps `d1` to `d4`, each `step=[SEARCHING (not shown, the home's main button tells it)] looking=1 cancel=1 searching_title=0 search_again=0`; `scans=6 found=0`. No flip.

## POCO-B6

**PASS**

- `hint=1 hint_dump=1 key4=2 closed_back=1 after_back_hint=0 menu_add_page=1 closed_left=0 closed_gotit=0`; `12:54:06.416 HomeFragment: pages hint closed (BACK)`.

## POCO-W1 (B2)

**PASS**

- Provider `droom.sleepIfUCan/.../AnalogAppWidgetProvider`, `min_raw=28161` (110 dp), density 2.75, `min_px=303`, ceiling 717, add tile 264 px.
- `frame1`: `expected=303 bug=717 actual=303 ok=1 discriminating=1 clipped=0`. No `CONFIGURE` attempt (B5 is not graded on D-POCO).

## SAM-HS (optional)

**PASS**

- First attempt (13:00): `UNTESTABLE`, no cycle ran. D-SAM was not associated to the network (`SCANNING`, `Net ID -1`, empty address) so the script's ping gate failed twice and it exited 3 before any capture.
- Second attempt (13:25), after the operator rejoined D-SAM to `Pegue Cdesta` (`192.168.1.22`, `COMPLETED`, RSSI -34, 2437 MHz): ping gate `PING_OK` both ways (round trip 1.3 to 3.3 s average 2.3 s one way and 0.2 to 2.2 s the other, high for a LAN, recorded), `apk_identity exit 0`, commit `0d333b19bef3`, server listener present, hand step 2 done by the operator (`restarted after 24s (log line)`). `SAM-HS stop: none last=c3`.
- Per cycle (`SAM-HS-counts.txt`), HU side then phone side:

  | Cycle | vstart / vreq | within2s | selected | ssl | rend | silent / silent_line | scan_rx | gap_ms | phone hu_connected | phone critical | byecar |
  |---|---|---|---|---|---|---|---|---|---|---|---|
  | c0 (cold) | 1 / 1 | 0 | 1 | 1 | 2 | 0 / 0 | 0 (cold, not required) | 250 | 1 | 4 | 1 |
  | c1 | 1 / 1 | 0 | 1 | 1 | 2 | 0 / 0 | 1 | 240 | 1 | 4 | 1 |
  | c2 | 1 / 1 | 0 | 1 | 1 | 2 | 0 / 0 | 1 | 190 | 1 | 4 | 1 |
  | c3 | 1 / 1 | 0 | 1 | 1 | 2 | 0 / 0 | 1 | 320 | 1 | 4 | 1 |

  `budget=0` and `reset=0` in every cycle. All four cycles meet every per-cycle condition, so the run PASSES.
- No version answer took 2000 ms or more (largest 320 ms), so this shows the merged build connects cold and across three warm cycles; it does not show that it survives a slow answer.
- Phone `critical=4` in every cycle: the same count each time, it is not a PASS condition in the brief and was recorded only. The cycles ended with `PROJECTION_ENDED_BYEBYE_BY_CAR` (`byecar=1`).
- `peers` after the run: only the listener (`0A 0.0.0.0`), no `CLOSE_WAIT` sockets left on D-POCO's server.

## Addendum: D-HP against D-POCO's server (not in the brief, not graded)

Asked for by the operator: D-HP had connected to D-POCO's head unit server on an older build the day before, and the brief's "no LAN path" claim looked wrong. Run with the one-round scripts `hp_lan.sh`, `hp_lan2.sh`, `hp_lan3.sh`, `hp_lan4.sh` (in the captures), candidate C on D-HP, `wifi-connection-mode` 1. Two operator hand steps (stop, then start the head unit server on D-POCO) were used.

- **The LAN path exists.** D-HP `192.168.1.27` and D-POCO `192.168.1.20` are both on `Pegue Cdesta` (`/24`); the host reaches D-POCO's `:5277` over TCP. ICMP between D-HP and D-POCO fails both ways (4 of 4 lost each way), which is what made the brief's ping check read "no LAN path".
- **Attempt 1** (server not restarted, `ACTION_CONNECT --es ip 192.168.1.20`): TCP connected (`13:11:49.748 session state connected`), then `13:12:00.098 No VERSION_RESPONSE within the handshake budget (attempt 1)` and `13:12:00.328 session state failed (peer_silent)`. D-POCO's server accepted and stayed silent.
- **Attempt 2** (after a server restart, launch plus `ACTION_CONNECT`): D-HP's own discovery found the server and the session formed unaided (`13:15:54.868 the phone selected 1.7`, `13:15:55.478 SSL handshake complete`, `13:15:55.538 session state projecting`). The `ACTION_CONNECT` verb then arrived at `13:15:55.548`, the arbiter logged `192.168.1.20:5277 (USER) stood the wireless stack down`, and the socket closed at `13:15:55.558`. That was the test's own verb cutting a live session, a mistake in how I ran the attempt. The later reconnects were `peer_silent` (5 times).
- **Attempt 3** (no restart, launch only): `Found Headunit Server on 192.168.1.20:5277` three times, each followed by `peer_silent`; D-POCO's server held 7 sockets in `CLOSE_WAIT` (state `08`). So the server stopped answering after the earlier session, which had ended with the socket cut.
- **Attempt 4** (restart, launch only, no connect verb): `13:21:44.738 Found Headunit Server on 192.168.1.20:5277`, `13:21:46.318 the phone selected 1.7`, `13:21:46.918 SSL handshake complete`, `13:21:47.488 session state projecting`. D-POCO closed that first connection `13:21:47.928` (`AapRead: Connection closed (EOF)`, `link_lost`) right after Gearhead's own startup handoff; D-HP found the server again at `13:21:50.288` and reconnected. The second session rendered: `13:21:59.268 Throughput over 5008ms: rendered=120 (23fps)`, then `rendered=150 (29fps)` at `13:22:14.268`. Counts over the window: `ssl=2 mssr=4 rend=2 silent=0 discovery_found=2`, no connect verb sent.
- **Reading.** D-HP connects and projects against D-POCO's server on this candidate, with video at 23 to 29 fps, once the server has been freshly restarted. The brief's rule against re-running HP-C2 rested on a ping check that does not measure the TCP path. The server went silent after a session that ended abruptly (attempts 1 to 3); the SAM-HS run later took four clean sessions on one restart, so the silence follows an unclean end, not a count of sessions.
- Not compared against the older build the operator mentioned; this round has no baseline APK.

## Anything the brief did not ask about

- The `H` ordering error in the brief's script headers (Setup notes) would void every block on the first try; the next brief should carry the fix.
- HU-W1 picked Chrome widgets on D-HU and a sleep-alarm widget on D-POCO, so each run measured a different provider; both are discriminating (frame at the provider's pixel minimum, not at density times it). A set-up widget never came up in the first two picker entries, so B5 still has no hardware result.
- On D-HP the focus path is DPAD_DOWN to the WiFi button, then DPAD_RIGHT to the card; the card took focus on the second key. The brief's remaining four keys were not needed.
- D-SAM did not hold a network connection when SAM-HS started (SCANNING, no network id), so SAM-HS stopped on D-SAM's association, not on a missing LAN.
- The brief's LAN check (ICMP ping) is unreliable on this rig; a TCP probe to `:5277` (the host's `/dev/tcp/192.168.1.20/5277`) answered while ping did not. Round 1's "no LAN path from D-HP to D-POCO" should be reread with that in mind.
- `ACTION_CONNECT --es ip` sent while the app's own discovery has a session up tears that session down (`ConnectionArbiter ... (USER) stood the wireless stack down`). Not a bug here, but a brief that sends the verb after launch in mode 1 will cut a session that formed by itself.
- Restore state: D-POCO's WiFi was turned on and stay-on set to `usb` by `sam_hs.sh`; `restore.sh` returns both from the opening values. D-POCO's head unit server is left running, as the brief says.
