# Threads table rows archived on 2026-10-13

The `## Threads` rows of `README.md` for threads closed when their PRs (1108, 1042 and 1076) merged,
plus `dsam-widget-layout-dpi` and `main-beta5-regression`, closed by the owner, and their files moved to `archive/rounds/`. Verbatim, in the original order.

| Thread | State | Next |
|---|---|---|
| `pr-1076-text-keycodes` | Superseded, never run | Its candidate `3b112e5b` was replaced by `902cc281`. The same question is now R3 of `pre-350-fixes-round1-brief.md`. Do not run this brief. |
| `pr-1042-disabled-home-buttons` | Round 1 reported | `pr-1042-disabled-home-buttons-round1-results.md`. `3404e4e4`: R4 FAIL as predicted (only WiFi ticked, session live in picture-in-picture: Self Mode button reads enabled=no, press does nothing); R1, R2, R3, R5 PASS; R2 shows the legacy single choice greys Self Mode and USB. |
| `wizard-dpi-layout` | Round 2 PASS | `wizard-dpi-layout-round2-results.md`. Candidate `70271831`: W1 and W2 draw the widescreen layout at 103 and 165, C1, A1, P1, P1-A pass. Next: PR-ready. |
| `dsam-widget-layout-dpi` | Round 1 reported, no brief | `dsam-widget-layout-dpi-round1-results.md`. D-SAM shows the portrait widget layout from the announced density, not orientation: 1280x720 landscape at 175 dpi (about 658 dp tall) shows the weather widget, at 198 it does not. Auto orientation also pins a portrait start (R1 FAIL). dpi left at 198. |
| `main-beta5-regression` | Round 1 done on `ec9d9c33`: R0, W2, W4, U1, H1 PASS; W1 FAIL (6 fps static map, no overlay in dumps); W3 UNTESTABLE (stale taps) | `main-beta5-regression-round1-results.md`. Fix brief errata (L-SDR string, overlay, taps, no `nc -z`) before any round 2. |
