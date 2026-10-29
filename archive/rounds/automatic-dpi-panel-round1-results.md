# automatic-dpi-panel, round 1 results

**Candidate:** `fork/fix/automatic-dpi-panel-size` @ `245e8d501a81fe295851dbe09946823d0e371c34`       **Baseline:** `main` @ `947b3ae9fae9834aa8e4145ee91751ed743c2ae0`
**APK md5:** C `73cba570ca939f48962fd4e7c48be27a` / A `0166de4f28b90aad3c91c560f59c733b`
**Unit:** D-HU (UNISOC MT50, Android 14, 1440x720) and D-SAM (SM-T230, API 19, 1280x800) as head units over Native AA with D-POCO as the phone; D-POCO (POCO X3 NFC, Android 15, 2400x1080) and D-MOTO (Motorola edge 30 neo, Android 14, 2400x1080) as their own head unit in Self Mode. Android Auto on both phones: 18.1.164344-release.daily.
**Date:** 2026-10-26

## Round verdict: FAIL

The numbers are as the brief predicted on all four units. The two runs the round exists for, PO-C and MO-C, fail on one condition: the layout is still `WIDE`. The no-change control and the 720p head unit pass.

## Setup notes

- Quirk files read: `topics/tooling`, `topics/gearhead`, `units/D-HU`, `D-MOTO`, `D-POCO`, `D-SAM`, `D-SAM-and-D-HP`. Template read without 7b and 8; TOOLS.md "Pick a tool" table read.
- Opening pre-flight (`rig_preflight.sh D_HU:wifi,bt D_SAM:wifi,bt D_POCO:wifi,bt D_MOTO`): D_HU wifi 1 bt 1 Awake; D_SAM wifi 1 bt 1 Awake; D_POCO wifi 1 bt 1 Awake, HFP `..33:59`, A2DP up; D_MOTO bt 0 Asleep. `PREFLIGHT OK`. `rig_cleanup.sh list`: clean.
- Builds: one worktree, A then C, `build_hur_cool.sh` with `rig_thermal.sh wait 70` before each (66 C and 70 C). Both exit 0. R0 below.
- **Brief defects.** (1) Script blocks are headed `**name.sh**` with no backticks, so `extract_brief_blocks.py` saved nothing; the same logic was run inline to extract 7 files. (2) `ANDROID_HOME` is unset on the host, which makes `dest_id` return 0; exported `/home/oscar/Android/Sdk` (aapt2 37.0.0), dest id 2131296517 on both APKs. (3) The brief's Gearhead assumption is 18.0; both phones run 18.1.164344. (4) `readout`'s `mode=` prints `Initializing WiFi Mode: NATIVE` on Self Mode runs, because the phone's backup has `wifi-connection-mode` 3 and the launch arms the Native AA host (a `createGroup` also ran). The self route is in the same window: `SelfMode: AA 17.4+ detected. Connecting directly to Headunit Server on 127.0.0.1:5277...` (PO-A 22:38:46.503, PO-C 22:40:15.244), so condition 4 is met. (5) `pageout`'s `resumed` counts the word `SettingsActivity` in an exception or in the `Starting:` line, so `resumed=1` or `2` is a false positive.
- Precheck: Gearhead 18.1.164344 on D-POCO and D-MOTO; `:149D` count 0 on both (hand step 1 on each); D-POCO's bonded list held `Navegadortz2` (D-HU) and `Navegadortz3` (D-SAM); D-MOTO keyguard showing, `deviceLocked=1`. D-SAM `date` 22:21:12 against host 22:21:37. Audio keys on D-HU left as the rig's worst case (`audio-latency-multiplier` 8, `enable-audio-sink` false, `use-aac-audio` false, `audio-queue-capacity` 20). MAC `DC:B7:2E:5E:4E:59` from D-HU's bonded list. Full output: `precheck.txt` in the asset.
- Hand steps, all by the operator: forgot `Navegadortz2` on D-POCO before SAM-A (read back: only `Navegadortz3` left); turned on "Start head unit server" on D-POCO (tcp6 `:149D` count 1) and on D-MOTO (count 1); unlocked D-MOTO (`deviceLocked=0`). One `KEYCODE_HOME` each on D-POCO and D-MOTO. Gearhead was never force-stopped.
- Settings: written per unit from a fresh backup with the brief's `put_keys` (`dpa_keys.py`), `set_hu_settings_host.py` and `set_prefs_runas_host.py` not used. Each unit's delta is the brief's section 4 table; every session's `keys-before` shows `dpi-pixel-density` 0 and the run's `resolutionId` and `display-size-preset`. All four units restored byte-exact (`restored`).
- Scripts: the brief's seven blocks plus one-round `env_all.sh`, `seg_hu_a.sh`, `seg_hu_c.sh`, `seg_sam.sh`, `seg_poco.sh`, `seg_moto.sh` (one script per unit, run by a foreground `rig-executor` under `/tmp/ohu-rig.lock`), all in `scripts/` of the asset. `rig_thermal.sh wait 75` and `watch` per session: package temperature 59 C to 75 C, `throttle_pkg=0` throughout.
- Each session ended with a 15 s wait for `AapService: Disconnected.` that capped. That wait is after `x-go`, outside the graded window; `disconnected_in_session` is 0 in every session.
- SAM-A: no `Initializing WiFi Mode:` within 10 s, so `ACTION_START_WIRELESS_SCAN` was sent (`verbs=5`); the line printed at 22:28:50.085 and the session formed. D-SAM has no `md5sum`: identity is the `commit` reply (`947b3ae9fae9` then `245e8d501a81`). `klogctl: Operation not permitted` on D-SAM comes from the thermal tool and is harmless.
- Validity per session (brief section 7): every session has `ssl=1`, `rendered_windows` 3 or 4, `self_wake=0`, `disconnected_in_session=0`, `server_down=0`, `chose_fallback=0`, `linkCapped=none`, `verbs` 4 or 5, and a `commit` matching its arm. No run was voided or re-run.
- Evidence: release `rig-evidence-automatic-dpi-panel` on `o-jcardenass/open-headunit`, asset `automatic-dpi-panel-round1-captures.zip`, sha256 `9fb8f2c6e042346c7ba4dba0058bcd426b685d85a8a611e3fbfc280f32dcbf2a` (72336117 bytes). It holds the captures, PNGs, dumps, both APKs, `setup-notes.md` and the scripts.
- Closing: radios left as the opening table (D_HU, D_SAM, D_POCO wifi 1 bt 1; D_MOTO wifi 1 bt 0); an idle Gradle daemon was killed with `rig_cleanup.sh kill`; closing pre-flight `PREFLIGHT OK`. D_POCO's HFP link read `XX:XX:XX:XX:33:59` at the start and `none` at the end, after the operator forgot D-HU's radio on it. Build worktree removed.

## R0. Build and identity gate

**PASS**

- A: `panel-based` 0, `codec-based` 1. C: `panel-based` 1, `codec-based` 1. md5s differ (above). The `commit` in `ACTION_QUERY_STATE` matched the arm after every install (`947b3ae9fae9`, `245e8d501a81`).

## HUP-A

**PASS**

- Dump: `Uses 240 DPI for 720p, a 960x480 dp canvas.`, `checked="true"`, `fatal=0`.

## HU-A

**PASS**

- `density=240 (dp canvas 960x480, requested automatic, codec-based)` at 22:23:12.169; `ph_dpi=dpi 240`; `ph_canvas=w960dp h480dp`; `ph_real_density=0`; `ph_critical=0`. Layout `RAIL`.

## HUP-C

**PASS**

- Dump: `Uses 256 DPI for 720p, a 900x450 dp canvas.`, `checked="true"`, `fatal=0`. Equals HU-C's density.

## HU-C

**PASS**

- `density=256 (dp canvas 900x450, requested automatic, panel-based)` at 22:24:56.153; `ph_dpi=dpi 256`; `ph_canvas=w900dp h450dp`. Layout `RAIL`.

## SAM-A

**PASS**

- `density=240 (dp canvas 853x533, requested automatic, codec-based)` at 22:29:26.681; `ph_dpi=dpi 240`; `ph_canvas=w853dp h533dp`. First render 39 s after the phone's radios came on. Layout `RAIL` (screenshot is the portrait mount).

## SAM-C

**PASS**

- `density=240 (dp canvas 853x533, requested automatic, codec-based)` at 22:31:37.648, identical to SAM-A; `ph_dpi=dpi 240`; `ph_canvas=w853dp h533dp`. The fix does not move this tablet. Layout `RAIL`.

## POP-A

**UNTESTABLE**

- `am start` printed no error, but the 15 s wait for the settings marker capped, the dump holds only empty text nodes and the PNG is the launcher. `auto_desc` empty.

## PO-A

**PASS**

- `density=240 (dp canvas 1600x720, requested automatic, codec-based)` at 22:38:47.356; `ph_dpi=dpi 240`; `ph_canvas=w1600dp h720dp`; `raw=2400x1080`; `ph_real_density=0`, `ph_critical=0`. Layout `WIDE`: rail, map, and a Spotify card beside the map. This is the control, and it shows the defect.

## POP-C

**UNTESTABLE**

- `run-as $PKG am start --user 0 -n .../SettingsActivity` threw `java.lang.SecurityException: Permission Denial: package=com.android.shell does not belong to uid=10282` (Android 15). The dump and PNG are the launcher.

## PO-C

**FAIL**

| Condition | Result |
|---|---|
| 1. `density=` 384, panel-based, height 450, W as section 6 | met: `384 (dp canvas 1000x450, requested automatic, panel-based)` at 22:40:16.217 |
| 2. `ph_dpi` 384, `ph_canvas` height 449 to 451 | met: `dpi 384`, `w1000dp h450dp` |
| 3. `ph_real_density` 0, `ph_critical` 0 | met: 0 and 0 |
| 4. layout not `WIDE` | **not met: `WIDE`** (rail, map, and a Spotify media card beside the map, outside the rail) |

- Fallback offered `_1280x720 ... density 256`; the phone did not take it (`chose_fallback=0`). The brief predicted `RAIL` here on the basis of Android Auto 18.0's 1240 dp widescreen breakpoint. On 18.1.164344 the widescreen layout is still drawn at a 1000 dp width. The full capture is kept in the asset.

## MOP-A

**UNTESTABLE**

- The page did not open. The dump holds the launcher's nodes (`Spotify`, `WhatsApp`, `Slack`), `auto_desc` empty, no exception printed.

## MO-A

**PASS**

- `density=240 (dp canvas 1600x720, requested automatic, codec-based)` at 22:44:10.220; `ph_dpi=dpi 240`; `ph_canvas=w1600dp h720dp`; `raw=2300x1017`; 0 and 0 on real density and critical. Layout `WIDE` (map with an ETA card, and a Spotify card beside it).

## MOP-C

**UNTESTABLE**

- Same as MOP-A: launcher in the PNG and dump, `auto_desc` empty.

## MO-C

**FAIL**

| Condition | Result |
|---|---|
| 1. density | met: `384 (dp canvas 1000x450, requested automatic, panel-based)` at 22:45:22.262, `raw=2300x1017` |
| 2. phone side | met: `dpi 384`, `w1000dp h450dp` |
| 3. real density, critical | met: 0 and 0 inside `arm` to `x-go` (4 `Critical error` lines come after the Exit, as the brief says) |
| 4. layout not `WIDE` | **not met: `WIDE`** (map, an ETA card and a Spotify card stacked right of the map) |

- Same pattern as PO-C on a second 1080p panel and a second phone. Fallback offered density 256, not taken.

## Report back (brief section 9)

1. **PO-C and MO-C against PO-A and MO-A:**

| | `density=` | `ph_dpi=` | `ph_canvas=` | layout |
|---|---|---|---|---|
| PO-A | 240, 1600x720 codec-based | 240 | w1600 h720 | WIDE |
| PO-C | 384, 1000x450 panel-based | 384 | w1000 h450 | WIDE |
| MO-A | 240, 1600x720 codec-based | 240 | w1600 h720 | WIDE |
| MO-C | 384, 1000x450 panel-based | 384 | w1000 h450 | WIDE |

2. **SAM-A and SAM-C read the same:** yes, 240 (853x533 dp, codec-based) and `dpi 240` in both.
3. **Screenshots, A beside C per unit, with the host's layout class** (files `HU-A.png`, `HU-C.png`, `SAM-A.png`, `SAM-C.png`, `PO-A.png`, `PO-C.png`, `MO-A.png`, `MO-C.png` in the asset): D-HU `RAIL`/`RAIL`; D-SAM `RAIL`/`RAIL`; D-POCO `WIDE`/`WIDE`; D-MOTO `WIDE`/`WIDE`. The legibility verdict is the engineer's.

## Anything the brief did not ask about

- The page read the brief relies on works on D-HU (root shell) and does not open on either phone. `run-as ... am start` into the non-exported `SettingsActivity` is refused on D-POCO (Android 15), and on D-MOTO it silently leaves the launcher up. A later round needs another route to the DPI page on the phones.
- On both phones the widescreen layout survives a 1000 dp canvas on Android Auto 18.1.164344. If the intent is to leave `WIDE`, the canvas width on the phones has to fall under whatever 18.1 uses, and that number is not in this round's evidence.
- At 384 the phones still drew the 1000x450 canvas without the reduced layout, and the phone accepted density 384 unchanged (`dpi 384` in `displayDimensions`).
- D-MOTO's window was 2300x1017 (not the full 2400x1080), which gave a 1000 dp canvas by the app's own line; `round(2300*160/384)` is 958.
