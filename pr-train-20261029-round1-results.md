# pr-train-20261029, round 1 results (PARTIAL: stage W was not completed)

**Candidate:** T = fork/test/pr-train-20261029 @ e2b1264c5265ac32ad2667f889838a24664c467c (tree 13e22f6e1ddf16234344ab318c6ba88a23b96077)   **Baseline:** B = main @ a4f80ff1b6aa6726c17e7385192cf23abe0d0e9f
**APK md5:** T 2dda80522bfb9b5453ba0a6e7bdf8bcd / B 9a077f29fb3ba545285fc028f69cba0a. `NightOverlayState` in the DEX: T 30, B 0. `commit` read a4f80ff1b6aa on every B install and e2b1264c5265 on T.
**Unit:** D-HU (MT50, Android 14, root), D-POCO (API 35, head unit in stage P, phone in X and W), D-MOTO (phone in P), D-SAM (API 19)
**Date:** 2026-10-29

## Setup notes

- XB: two 'SSL handshake complete' lines are one handshake (AapSslContext and AapTransport log it); phone 'Critical error' x5 all fall within 2 s of XB-exit (18:33:01.4), excluded per brief. car_mode put returned rc 0. Spotify: media key 126 sent; rendered fps ~150 per 5s window.
- X1: L-DES is 2 on both XB (main) and X1 (T). Brief's 'L-DES is 1' PASS clause does not hold on either arm (stop then service restart by the second Exit then destroy); graded on the FAIL criteria (S-FGS 0, no FATAL) and the control match; flag for the brief.
- PB: anchor 2400x1080 (from WINDOW), seeded insets 0, mAppBounds 2400x1080: equals PB's anchor, so per the brief P1 is INCONCLUSIVE (the fix has nothing to change on this unit); P2 still grades. The brief's L-ANC string 'HeadUnitScreenConfig: anchor ' does not exist; the line is 'Honest Init | Mode: NONE | Anchor: WxH (from WINDOW) | Seeded Insets'.
- P1: graded INCONCLUSIVE by the brief's PB-anchor==mAppBounds rule, but the data contradicts the rule's premise: seeded insets L0 T77 R91 B66 exist, T logs 11 adoptCanvas lines (B logs 0) with anchors 2309x937 / 2309x1094 / 2400x923 flipping within 1.5 s of session start, steady anchor 2400x1080. The anchor oscillation at start is worth a look. D-POCO as head unit DOES carry video (screenshot: maps+Spotify), contrary to the brief's 'rig-facts' note.
- Stage W: D-HU Bluetooth had self-reverted on before WB (read enabled: true); disabled again right before each W run (dhu_off), D-MOTO WiFi and Bluetooth off for stage W. write_rot = set_prefs_runas_host.py one boolean (scripts/w-pre.sh).
- WB attempt 1: NO_SESSION. D-SAM formed its group (standard createGroup after the 5 GHz-channel request failed, SSID DIRECT-rX-Navegadortz3) and poked D-POCO: HFP service level connection established 18:44:21.426. D-POCO's Gearhead then ran 'Creating rfcomm socket for device: 11:46:03:10:33:59' 30 times (that is D-HU's address, not D-SAM) and never dialled D-SAM. Matches units/D-POCO.md (stale head unit bond). The brief's hand step (D-POCO unpair of D-HU) is needed; not retried blind. D-HU Bluetooth was off at the time of the poke.
- Stopped by the operator after the esp32 stage B (the operator asked to stop and report): stages WB, W1 and W2 were not completed; the closing (settings restores, D-MOTO stayon false, D-HU Bluetooth on, B installed on all three) was not done.

## XB Control: double Exit on main

**PASS**

L-SSL 1 handshake (two log lines, one handshake), L-STOP 2, L-TDW 0, L-DES 2, L-FGS 0, S-FGS 0, `FATAL EXCEPTION` 0, L-SMM 0. Rendered per 5 s window: 124 then 149 to 151, median 150. L-CAR: `car_mode=0 isNight=false` 18:31:19.829, `car_mode=1 isNight=true` 18:32:36.191 (the control order). Phone `Critical error` 5 lines, all within 2 s after `XB-exit` (18:33:01.4). Markers: start 18:30:09, carmode 18:32:36, exit 18:33:00, end 18:33:21.

## X1 The double Exit (the point)

**PASS**

S-FGS 0, no `FATAL EXCEPTION`, L-STOP 2, the first Exit found a session (L-SSL 1 before `X1-exit`), L-TDW 0, L-DES 2, L-FGS 0, L-SMM 0. The brief's PASS clause says L-DES is 1; it reads 2 on both arms (XB on main too), so the clause was not met on either arm. Graded on the FAIL criteria (S-FGS and FATAL both 0) and the match with the control. Markers: start 18:34:15, carmode 18:35:38, exit 18:36:01, end 18:36:22.

## X1 Default session (night overlay, mic foreground type and system insets ride along)

**PASS**

Median rendered 150.5 per 5 s window against XB's 150 (ratio 1.00 against the 0.8 bound); 0 FAIL lines before `X1-exit`. Phone `Critical error` 5 lines within 2 s after the Exit.

## X1 ILL+ (calibration)

**PASS**

`NightModeManager: CAR_SIGNAL car_mode=0 invert=true isNight=true` 18:34:22.045, then `car_mode=1 invert=true isNight=false` 18:35:38.003. On XB the same stage read `isNight=false` then `true` (control). `settings put system car_mode` returned rc 0.

## X1 Settings screen (fake speed removal)

**PASS**

Screenshot `X1-settings.png` shows the Settings screen (General, with the connection-mode row); no `FATAL EXCEPTION` after the `SettingsActivity` start; the `fake_speed` key was in `settings.xml`.

## PB Control: anchor with bars, API 35

**PASS** (control reported)

`HeadUnitScreenConfig: Honest Init | Mode: NONE | Anchor: 2400x1080 (from WINDOW) | Seeded Insets: L0 T0 R0 B0`; no `adoptCanvas` line (0); L-SMM 0; `mAppBounds=Rect(0, 0 - 2400, 1080)`; no FATAL; session formed (L-SSL 1). The brief's string `HeadUnitScreenConfig: anchor ` does not exist on B; the line above carries the anchor.

## P1 Anchor leaves the bars out (fullscreen-mode 0, T)

**INCONCLUSIVE**

By the brief's rule: PB's anchor (2400x1080) already equals `mAppBounds` (2400x1080), so the fix has nothing to change on this unit. The data does not support that rule's premise: `Seeded Insets: L0 T77 R91 B66`, and T logs 11 `HeadUnitScreenConfig.adoptCanvas` lines (B logs 0), e.g. `anchor 2400x1080 -> 2309x937, measured on the activity window, against the insets now in force` 18:39:56.609, then `2309x937 -> 2309x1094` .633, `-> 2400x1080` .653, `2400x1080 -> 2400x923` .838, `-> 2400x1080` .847, `-> 2309x937` 18:39:57.422, `-> 2400x1080` .430. The steady anchor after 18:39:57.4 is 2400x1080; the screenshot `P1.png` shows the maps and media cards clear of the status and navigation bars. L-SMM 0, no FATAL. The anchor flipping within 1.5 s of the session start is worth a look.

## P2 Immersive unchanged (fullscreen-mode 1, T)

**PASS**

`Mode: IMMERSIVE | Anchor: 2400x1080 (from WINDOW) | Seeded Insets: L0 T0 R0 B0`; `adoptCanvas` 0; `mAppBounds=Rect(0, 0 - 2400, 1080)`; L-SMM 0; no FATAL.

## WB Positive control: a pending rotation is skipped on main

**UNTESTABLE** (first attempt; not retried blind)

NO_SESSION. D-SAM formed its group (`Standard createGroup SUCCESS!` 18:44:17.442 after the 5 GHz channel request failed; SSID `DIRECT-rX-Navegadortz3`) and poked D-POCO: `hands-free service level connection established (HFP-AG poke to DC:B7:2E:5E:4E:59)` 18:44:21.426. D-POCO's Gearhead then logged `Creating rfcomm socket for device: 11:46:03:10:33:59` (D-HU's own address) 30 times and never dialled D-SAM. D-POCO was still bonded to D-HU, as `units/D-POCO.md` describes. The brief names this as a hand step (a D-POCO unpair); the operator forgot D-HU on D-POCO later, after this attempt. WB, W1 and W2 were not run after that.

## W1 Pending rotation purges and re-creates (the point)

**UNTESTABLE** (not run)

## W2 Ordinary relaunch still reads the group

**UNTESTABLE** (not run)

## Anything the brief did not ask about

- D-POCO as head unit carries video (maps and media in `P1.png`), contrary to the brief's note.
- The brief's L-DES expectation of 1 is wrong on this rig: 2 on both arms.
- A same-state check for stage W is now possible: D-HU is no longer bonded to D-POCO. Run WB, W1 and W2 from a fresh round.
- D-HU's Bluetooth switched itself back on before stage W, as `rig-quirks` records; it was disabled again before the WB run.
