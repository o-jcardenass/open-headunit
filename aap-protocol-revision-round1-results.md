# aap-protocol-revision, round 1 results

**Candidate:** T = fork/fix/touch-cancel-release @ a9988ed85aae, U = fork/chore/aap-unread-fields @ 97d1ee0656a3, A* = main with versionName 3.4.0   **Baseline:** B = main @ 947b3ae9fae9
**APK md5:** B 429f16d81ed5adc41183e69f6e4e4981 / A* a0fd89c80398101cbe4bf44dad3d7bcb / T ad1d83bbd708c596d6191ef0b75ac296 / U 1ac06f08f6fbadc70ba5a636ff2aa863
**Unit:** D-HU (head unit), D-POCO (phone), D-SAM (R6-SAM)   **Date:** 2026-10-29   **Operator interventions:** 2 (the opening yes, and the D-POCO / D-HU pairing before the session)

## Setup notes

- Four arms built in one worktree by scripts/build_arms.sh per the brief's Prepare block; all four md5s differ. Gradle stopped after.
- Session run by rig_session.py (lib 871881d26). The session graded S-B, S-T, S-U1 and S-U2 INCONCLUSIVE "HOST READS SHOTS" by design; the host read the shots (montages in mont/) and grades below are the host's.
- R6-HU and R6-SAM were graded FAIL by the grade script. Overridden to INCONCLUSIVE by the host: R6-HU-runs.log says `ROW_NOT_FOUND` for c1 and c2 and R6-HU-c1-list.xml holds an empty UI dump, so the update dialog was never opened (taps=0). R6-SAM-runs.log says `ROW_NOT_FOUND` for c1; c2 did open the dialog and R6-SAM-c2-buttons.txt reads title `Update Available`, button1 `Download APK`, button3 `Open GitHub Releases`, which equal the strings file. The FAIL conditions on the dialog text therefore reflect the c1 cycle that never ran, not the build.
- R2 ran no shots: pre-navmode.txt reads 0, not 2, so S-T skipped R2.
- Operator pairing at 22:49:35 was needed because D-POCO held no D-HU bond after the previous round.

## R1 Touch cancel at the bar (T against B)
**INCONCLUSIVE**

By the brief's own rule a B cycle with no held highlight in `c` makes R1 INCONCLUSIVE. B c1 and c2 (R1-B-c1-c, R1-B-c2-c) show the plain map, with only the faint grey pointer circle near the Search pill that every shot carries; no held highlight on the bar. Log half holds: B tm_down 1, tm_cancel 1 on both cycles; T tm_down 1, tm_cancel 0 on both, Critical error 0.
Observation the brief's FAIL rule may apply to, not graded: on T, shot `c` of both cycles (R1-T-c1-c, R1-T-c2-c) still shows the launcher grid, and `d` shows the Spotify screen ("No items"), while B `c` is back on the map. Shot `b` shows the grid on both arms. Cause unexplained; a maintainer reading of these shots is worth having.

## R2 Edge-back gesture over the bar (T)
**UNTESTABLE**

`settings get secure navigation_mode` read 0, not 2 (pre-navmode.txt). No gesture navigation on D-HU; the brief says not to change it.

## R3 Hidden clock stays hidden (U)
**PASS**

S-B-clock: no clock (hide-clock true). S-T-clock: no clock. S-U1-clock: no clock (hide-clock true). S-U2-clock: clock `10:59` at the top left of the bar (hide-clock false). Control B shows no clock, as U1.

## R4 Rotary focus (U against B)
**INCONCLUSIVE**

Log half: rx 12 ge 12, right 3, left 3 on both arms. Shots s1 and s3: R4-B-s1 and s3 both show the focus ring on the bottom-left app-grid button; R4-U-s1 and s3 both show it on the bottom-left layout button. The focus did not move between s1 and s3 on B either, so by the brief the rotary input moves no focus on this phone on either build. s2 and s4 were not read.

## R5 A session forms and renders (U, with B and T)
**PASS**

S-B, S-T, S-U2 each: one discovery, a video sink, Throughput lines 19 / 14 / 13, rendered 151 per window (S-U1's own lines pass in its grade file). Critical error 0 on every session.

## R6-HU Update dialog on D-HU
**INCONCLUSIVE**

Script graded FAIL; overridden, see Setup notes. The dialog was never reached (ROW_NOT_FOUND, empty UI dump). Strings: title `Update Available`, apk `Download APK`, rel `Open GitHub Releases`.

## R6-SAM Update dialog on D-SAM
**INCONCLUSIVE**

Script graded FAIL; overridden. c1 ROW_NOT_FOUND. c2 reached the dialog with the correct title and buttons (A newer version (4.0.0-beta1) is available). Tapping Open GitHub Releases brought up the system chooser "Complete action using" with Chrome, Firefox and Internet (R6-SAM-c2-after.png); the grade counted 0 for the release page opening.

## Rcleanup
**PASS**

2/2.

## Anything the brief did not ask about

- The R1 observation on T above (launcher grid still shown in `c`).
- R4 and R3 control behaviour show D-HU's phone side did not move rotary focus on either arm.

- Evidence: release `rig-evidence-aap-protocol-revision`, asset `aap-protocol-revision-round1-captures.zip` (6853898 bytes), sha256 `cc1619212ca53ab9ef8237b921d512189302acc43d617ccc4042fda75b18054d` (moved from `rig-evidence-aap-protocol-revision-round1` on 2026-10-30).
