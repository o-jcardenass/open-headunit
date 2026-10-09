# Threads table rows archived on 2026-10-09

The `## Threads` rows of `README.md` for threads closed when the contributor's stacked PRs and
`settings-defaults` merged, and their files moved to `archive/rounds/`. Verbatim, in the original order.

| Thread | State | Next |
|---|---|---|
| `settings-defaults` | Round 2 reported | `settings-defaults-round2-results.md`. `77c0b1fe`: E2b, E2b-2 (Exit ends as user_exit, phone does not return), E4, R3b PASS; R1b shape 2 (window alpha capped at 0.8 on the merged build, tap passes at 0%); R2b skipped. |
| `pr-1045-framing` | Round 3 superseded | `pr-stack-1047-1067-round1-brief.md` tests the rebased head on the full stack; do not run `pr-1045-framing-round3-brief.md`. Round 2: `pr-1045-framing-round2-results.md` (no FAIL; A1, A2, U2S INCONCLUSIVE). |
| `pr-1047-session-reconnect` | Round 3 done on the stack build | `pr-1047-session-reconnect-round3-results.md`: Native Save reconnects in 6.9 and 8.4 s (N2 FAIL only on a 5000 ms t_poke ceiling, designed delay 4882 ms); ND FAIL (in-flight poke carries a session after Disconnect); U1 FAIL (normal-mode dongle return held by the open settings screen). Ruling needed. |
| `pr-1064-nearby-attempts` | Round 1 reported, round INCONCLUSIVE, no FAIL | `pr-1064-nearby-attempts-round1-results.md`. R0 PASS; N0 B 0/3 (Gearhead 17.9 refuses the helper launch), N1-N3 stopped; U1 B dongle never spent its budget, C not run. |
| `pr-1065-reconnect-timers` | Round 1 reported, U1 INCONCLUSIVE | `pr-1065-reconnect-timers-round1-results.md`. C = `de1f9848` (same tree as brief). H 10/10 and N 5/5 match B; U1 all cycles masked by dongle re-enumeration; P INCONCLUSIVE (Gearhead 17.9 refuses helper). |
| `pr-1067-tls-pump` | Round 3 superseded | `pr-stack-1047-1067-round1-brief.md` tests the reworked head on the full stack; do not run `pr-1067-tls-pump-round3-brief.md`. Round 2: `pr-1067-tls-pump-round2-results.md` (no FAIL; XW, KW-C INCONCLUSIVE). |
| `pr-stack-1047-1067` | Round 1 done, operator ruled | `pr-stack-1047-1067-round1-results.md`. Stack tree `2c4b06fd`: R0, A1-S, K-S, XS, US PASS after the operator ruling (both FAILs were brief errors: a second Gearhead logger in the Critical error count, and `clean=true` expected on a head-unit-initiated quit); CR INCONCLUSIVE. 28-29 fps under load, 0 TLS failures. The Save claims run in `pr-1047-session-reconnect` round 3. |
