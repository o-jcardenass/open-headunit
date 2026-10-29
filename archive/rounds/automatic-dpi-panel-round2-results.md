# automatic-dpi-panel, round 2 results

**Candidate:** `fork/fix/automatic-dpi-panel-size` @ `677f118e10634123f5083c6790b89332d3dd9eaf`       **Baseline:** `main` @ `947b3ae9fae9834aa8e4145ee91751ed743c2ae0`
**APK md5:** C `da3299a84b3bb8baba6e4c706745e63b` / A `0166de4f28b90aad3c91c560f59c733b`
**Unit:** D-POCO (POCO X3 NFC, Android 15, 2400x1080) and D-MOTO (Motorola edge 30 neo, Android 14, 2400x1080), each its own head unit in Self Mode. Android Auto 18.1.164344-release.daily on both.
**Date:** 2026-10-26

## Round verdict: PASS

Every session on both phones sent the expected density and got the expected canvas. C on Automatic now sends 309, and Android Auto keeps the widescreen layout (`GH.WidescreenIM` printed in PO-C, MO-C and MO2-C). The card stays on a map touch in PO-C and MO2-C, and the K arm retracts it in PO-K and MO2-K. D-MOTO was run twice: the first set (MO-*) used the brief's touch points, which land on the media card there, so it is kept as superseded context; the second set (MO2-*) used map-side touch points and is the graded D-MOTO result.

## Setup notes

- Closing pre-flight (after one idle Gradle daemon was cleared with `rig_cleanup.sh kill`): D_POCO wifi 1, bt 1, Awake; D_MOTO wifi 1, bt 0, Awake; PREFLIGHT OK. Radios were never changed, so they match the opening table. Both phones' `settings.xml` read back identical to the backups. Build worktree removed; logcat none left; monitors all finished.
- Pre-flight (`rig_preflight.sh D_POCO D_MOTO`, no radios named: Self Mode needs none): D_POCO wifi 1, bt 1, Awake; D_MOTO wifi 1, bt 0, Awake; HFP and A2DP none; no strays, lock free. The phones' backups hold `wifi-connection-mode` 3, so the launch also arms the Native AA host, as in round 1. Left as it is.
- `rig_devices.sh` exports `D_POCO` and `D_MOTO`; the brief says `POCO` and `MOTO`. The session script sets `POCO=$D_POCO MOTO=$D_MOTO`.
- Quirk files read: `tooling`, `D-POCO`, `D-MOTO`, `gearhead`. Template read without 7b and 8; TOOLS.md "Pick a tool" table.
- Scripts: round 1's `dpa_keys.py` and `dpa_lib.sh` copied; `r2_env.sh`, `r2_lib.sh`, `r2_precheck.sh`, `r2_unit.sh`, `r2_build.sh` extracted verbatim from the brief. `r2_build.sh` was run by hand step by step (its `$WT` is unset as written). One-round wrapper `scripts/unit_run.sh` runs `r2_unit.sh` with refusal pre-checks (lock, `:149D`, launcher focus, `deviceLocked=0`); it was run in the background with a monitor, one unit at a time. Nothing added to `rig-toolkit/`.
- Build: worktree `dpa2__scratch__677f118e__20261026`, `HEAD` read back as `677f118e10634123f5083c6790b89332d3dd9eaf`, `rig_thermal.sh wait 70` read 62C, `build_hur_cool.sh` exit 0. Host max 65C, `throttle_pkg=0` in all six session thermal logs.
- Precheck: `:149D` count 0 on D-POCO (hand step done by the operator, then read back as 1), 1 on D-MOTO. D-POCO had a Gearhead settings activity in focus; one `KEYCODE_HOME` cleared it. D-MOTO was unlocked (`deviceLocked=0`), so no PIN step.
- **Brief defect, D-MOTO:** the touch points (tap 1700,540; swipe 1800 to 1500 at y 540) were written for a layout with the card in the left 800 px. On D-MOTO in A, C and K the map is on the left and the media card on the right, so the tap lands on the card and opens the Spotify player (full-screen in K). On D-POCO the layout matches the brief. The operator had D-MOTO's layout mirrored in Android Auto settings and asked for a re-run without grading INCONCLUSIVE for that. MO2-A, MO2-C and MO2-K re-ran it with the touch points moved to the map side (tap 700,540; swipe 900,540 to 600,540, `scripts/unit_run_moto2.sh`). The first set (MO-A, MO-C, MO-K) is superseded; its numbers matched the second set exactly and its card pictures are in the asset.
- `AapService: Disconnected.` never appeared after `ACTION_EXIT` in any of the six sessions (0 matches in each whole capture); the post-exit wait hit its 15 s cap each time and `x-done` was written. The in-session count (arm to x-go) is 0, which is what validity needs. The exit itself is therefore not confirmed by that line.
- `tap_marker` and `drag_marker` read 1 on D-POCO and 2 on D-MOTO (condition: 1 or more). Cause not chased.
- DPI page not read (brief says not to). Settings: backups taken per phone, keys written per `r2_env.sh`, backups restored and diffed (`restored` printed for both).
- Cards were graded by the host from the PNGs, not by script.

## R0 Build and identity gate

**PASS**

- A md5 `0166de4f28b90aad3c91c560f59c733b` (copied from round 1). C md5 `da3299a84b3bb8baba6e4c706745e63b`, differs from round 1's C `73cba570ca939f48962fd4e7c48be27a`.
- `panel-based` / `dp widescreen line` string counts in the dex: C 2 / 1, A 0 / 0.
- `ACTION_QUERY_STATE` `commit` after each install: `947b3ae9fae9` (A), `677f118e1063` (C), on both phones.

## R PO-A

**PASS**

- Keys: `dpi-pixel-density` 0. `density=240 (dp canvas 1600x720, requested automatic, codec-based)`, `ph_dpi=dpi 240`, `ph_canvas=w1600dp h720dp`, `ph_widescreen=1`.
- `10-26 23:33:25.903 GH.WidescreenIM: Using widescreen interaction manager`.
- Card: `PO-A.png` CARD, `.tap` CARD, `.drag` CARD. **STAYS.**
- Validity: verbs 7, ssl 1, rendered_windows 6, self_wake 0, disconnected_in_session 0, chose_fallback 0, `linkCapped=none`, `NegotiatedResolution is: 1920x1080`, ph_critical 0.

## R PO-C

**PASS**

- Keys: `dpi-pixel-density` 0. `density=309 (dp canvas 1242x559, requested automatic, panel-based, held at the 1240 dp widescreen line)`, `ph_dpi=dpi 309`, `ph_canvas=w1243dp h559dp`, `ph_widescreen=1`, `ph_critical=0`.
- `10-26 23:34:32.286 [ServiceDiscovery] Density is: 309 (...held at the 1240 dp widescreen line)`; `10-26 23:34:33.147 GH.WidescreenIM: Using widescreen interaction manager`.
- Card: `PO-C.png` CARD, `.tap` CARD, `.drag` CARD. **STAYS.**
- Validity as PO-A; rendered_windows 6.

## R PO-K

**PASS**

- Keys: `dpi-pixel-density` 384. `density=384 (dp canvas 1000x450, requested 384, within the HUIG floor)`, `ph_dpi=dpi 384`, `ph_canvas=w1000dp h450dp`, `ph_widescreen=0` (no `GH.WidescreenIM` line from `arm` to `x-go`).
- Card: `PO-K.png` CARD; `.tap` NO CARD (map full width, rail only); `.drag` NO CARD. **RETRACTS.** The probe can see a card retract on D-POCO.
- Validity as PO-A; rendered_windows 6.

## R MO-A (first set, superseded by MO2-A)

Numbers as MO2-A; the tap landed on the media card and opened the Spotify player, so the card was not graded from this set. See Setup notes.

## R MO-C (first set, superseded by MO2-C)

Numbers as MO2-C (309, `w1243dp h559dp`, `ph_widescreen=1`); card not graded from this set, same reason.

## R MO-K (first set, superseded by MO2-K)

Numbers as MO2-K (384, `w1000dp h450dp`, `ph_widescreen=0`); card not graded from this set, same reason.

## R MO2-A

**PASS**

- Keys: `dpi-pixel-density` 0. `density=240 (dp canvas 1600x720, requested automatic, codec-based)`, `ph_dpi=dpi 240`, `ph_canvas=w1600dp h720dp`, `ph_widescreen=1`, `ph_critical=0`.
- Card (right of the map on D-MOTO): `MO2-A.png` CARD, `.tap` CARD (a volume mixer overlay is drawn over the card, the card stays), `.drag` CARD. **STAYS.**
- Validity: verbs 7, ssl 1, rendered_windows 5, self_wake 0, disconnected_in_session 0, chose_fallback 0, `linkCapped=none`, commit `947b3ae9fae9`.

## R MO2-C

**PASS**

- Keys: `dpi-pixel-density` 0. `density=309 (dp canvas 1242x559, requested automatic, panel-based, held at the 1240 dp widescreen line)`, `ph_dpi=dpi 309`, `ph_canvas=w1243dp h559dp`, `ph_widescreen=1`, `ph_critical=0`.
- Card: `MO2-C.png` CARD, `.tap` CARD, `.drag` CARD (map panned, Re-center shown, card unchanged). **STAYS.**
- Validity as MO2-A; commit `677f118e1063`, rendered_windows 5.

## R MO2-K

**PASS**

- Keys: `dpi-pixel-density` 384. `density=384 (dp canvas 1000x450, requested 384, within the HUIG floor)`, `ph_dpi=dpi 384`, `ph_canvas=w1000dp h450dp`, `ph_widescreen=0`.
- Card: `MO2-K.png` CARD. `.tap` and `.drag` show a "Choose driving avatar" dialog over the whole display, so no card is visible. Per section 6 the card is graded from that picture: NO CARD, **RETRACTS**. Caveat: the dialog covers the area, so the pictures alone do not show a retraction. Corroboration: the rail's bottom icon changes from the app grid (`MO2-K.png`) to the layout toggle after the tap, the same change seen after D-POCO's retraction in `PO-K.tap.png`.
- Validity as MO2-A; commit `677f118e1063`, rendered_windows 5.

## Anything the brief did not ask about

- D-MOTO draws the widescreen layout mirrored (map left, card right) against D-POCO's (card left, map right). Any later touch probe has to be placed per unit, or on the map side of each. A tap near x=500 would land on the map on D-MOTO.
- On D-MOTO in K, a tap at 700,540 landed on the driving avatar and raised a dialog that covers the display; a touch probe can open overlays that hide the card for reasons other than layout.
- At 309 the D-POCO canvas keeps the rail, the card and the map as in A (240), with larger text, which is what the engineer wanted to judge. The nine PNGs per phone are in the evidence asset.
- Evidence asset: `automatic-dpi-panel-round2-captures.zip` in release `rig-evidence-automatic-dpi-panel`, sha256 `b1b2db09774ad6858ce45ee8d0e970ccf2e7b3db70b4a8e82c27c3c1d8ac5edb`.
