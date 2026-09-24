# connection-failure-banner round 1 results

Candidate `fix/connection-failure-banner` @ `6f9c4158`, baseline `afd8b7ca`. R0-R6 all run. Verdicts:
**R0 PASS, R1 PASS (both arms), R2a/R2b PASS, R2c MIXED (one step INCONCLUSIVE — rig contamination,
not a candidate defect), R2d PASS, R2e PASS, R3 PASS (all three), R4a/R4b PASS, R5a landscape PASS /
portrait UNTESTABLE (rig limitation), R5b PASS, R5c PASS, R6 PASS.** No FAILs. Four design-gap
findings surfaced beyond the brief's own checklist (below) — none block, all worth a look.

## The four numbers the brief asked for

1. **R0**: 657/657 tests, 0 failures/errors/skipped. Named suites exactly as predicted:
   `ConnectionIssuesTest`=8, `ConnectionIssueBannerPolicyTest`=11, `CredentialsHandoffTest`=8,
   `WppHandshakeSessionTest`=27. **Deviation**: total was 657, not the predicted 697 — the
   `@Test`-annotation count (657) equalled the reported total exactly; no 40-test parameterised-suite
   expansion showed up this time.
2. **R1**: `Received WiFi credentials from manager` — baseline **0**, candidate **1**. Millisecond
   gap (`Native AA on the head unit hotspot` → `SUCCESS - Providing credentials`) — baseline **52ms**,
   candidate **58ms**. Both comfortably under the ~1s reachability threshold; the race window was
   open on both arms.
3. **R1, the load-bearing-fix question**: `the access point resolved before anything was listening
   for it` on the candidate arm = **0**. The listener-reordering fix alone closed the window on this
   run; the `CredentialsHandoff` latch was never exercised. (Doesn't rule out the latch mattering
   under different timing — just that this measured run didn't need it.)
4. **R6**: three zeroes across every kept capture. The one non-zero hit
   (`r1-baseline-attempt1-config-unreadable.txt`, 1x `The access point on`) is the explicitly
   discarded pre-fix R1 attempt (see R1 setup deviation below), not counted as this round's data.

## Setup deviation that applies to the whole round

`hur-wifi-test-scripts` R1's settings table doesn't include a hotspot-ssid/hotspot-password manual
override. On the first R1 attempt (no override), this rig's known `getSoftApConfiguration()`
reflection block fired — `SoftApCredentials: The access point on wlan2 is up, but this device will
not let apps read its name...` — and the resolve loop errored out before ever reaching a SUCCESS
line: zero credentials delivery on either arm, no race exercised at all. This matches this repo's
own project memory (the rig cannot read its own hotspot config via reflection). The code's own log
message directs setting `hotspot-ssid`/`hotspot-password` manually, so that's what every run after
this one did (`OHU-TEST` / `testtest1234`, matching a `cmd wifi start-softap` AP started for the
round). Kept the discarded first attempt's capture rather than deleting it.

## Rig-state finding: shared_prefs directory is root-owned, blocking the app's own writes

`/data/data/<pkg>/shared_prefs/` on this test unit is owned `root:root` (mode 775). The app runs as
uid/gid 10168, which is neither the directory's owner nor in its owning group, so it has no write
permission on the *directory* — reading the existing `settings.xml` works fine (needs only file-level
permission), but `SharedPreferences.apply()`'s atomic temp-file-then-rename commit needs directory
write permission, which is missing. Right now, **the app's own settings writes update the in-memory
copy correctly but never reach disk**, for any key, not just this branch's.

Confirmed directly: after tapping the banner's dismiss button, `connectionIssueDismissedAtEpochMs`
took effect in-memory immediately (the banner stayed hidden across a Home-then-relaunch within the
same process), but `settings.xml`'s mtime never advanced across a 7+ minute wait, with or without an
intervening force-stop attempt. SELinux is in permissive mode on this rig (checked via dmesg/logcat
avc denials, all `permissive=1`), ruled out as the cause. Most likely explanation: contamination from
an early round on this shared rig — a root-shell settings write reaching this path before the app
ever created its own directory, so the directory inherited root ownership instead of the app's.

The user was asked and declined an on-the-spot `chown` fix, so this was worked around rather than
fixed: R2c's disk-persistence step is reported INCONCLUSIVE, and R4's clear-path checks were
verified in-process (ending the live session without force-stopping, so the running process's
in-memory Settings cache — proven correct by the R2c finding — could be read directly) rather than
via the brief's prescribed force-stop+read-settings.xml.

**Recommend**: whoever runs the next round on this rig should `stat` the shared_prefs directory
owner early and `chown` it to the app's uid:gid if it's still root:root. This will silently mask any
round that assumes the app's own settings writes reach disk.

## Runs

### R0 — build and unit gate: PASS
Candidate `6f9c4158`: clean compile (first ever compile of any of these three commits), 657/657
unit tests, 0 failures/errors/skipped. Suite counts exactly as predicted (see above). md5s differ
(candidate `036ab497205536a9f099324ac8dda101`, baseline `b2d3e0ddcd71017ad9f4a3f56b381400`).

### R1 — the hotspot credential handover race, A/B: PASS (both arms), the point of the round
See "the four numbers" above and the setup deviation. Both arms passed comfortably, with the
reachability gap (52ms/58ms) proving the race window was genuinely open on both.

### R2 — the banner, from a seeded record

**R2a: PASS.** Seeded `connection-issue-bssid` only. Banner visible below the top of the screen,
text begins "This unit could not read its own WiFi address", exactly one log line naming
`BSSID_UNAVAILABLE`, close button reachable.

**R2b: PASS.** Seeded all three (hotspot-config largest, bssid second, bt-silent smallest). Exactly
one banner on screen, text begins "This unit will not tell the app its hotspot name and password",
log names `HOTSPOT_CONFIG_UNREADABLE` and nothing else. No stacking.

**R2c: MIXED**, one step blocked by the rig contamination above, not a candidate defect.
- Step 1 (banner disappears immediately on tap): **PASS**.
- Step 2 (dismissed-at grows on disk, readable via force-stop+cat): **INCONCLUSIVE** — blocked by
  the directory-ownership issue. The in-memory equivalent passes (see below).
- Step 3 (relaunch: no banner, no new log line): **PASS**, tested via Home-then-relaunch *without* a
  force-stop (a force-stop's read would only show the stale on-disk copy) — banner stayed hidden, no
  new `showing the connection issue banner` line, across two separate relaunches in the same process.
- Step 4 (a newer raise after dismissal brings the banner back): **not run** — its premise (a
  persisted dismissed-at on disk) doesn't hold while disk-persistence is blocked.
  `ConnectionIssueBannerPolicyTest`'s 11 cases are the coverage for this comparison.

**R2d: PASS.** With the banner standing (all three stamps seeded), `adb shell svc power reboot`,
waited for `sys.boot_completed=1`, relaunched. Same banner (`HOTSPOT_CONFIG_UNREADABLE`), exactly
one log line. (These stamps were seeded directly via the test script, not by the app, so their
survival doesn't depend on the app's own write path — only reads were exercised here.)

**R2e: PASS.** Formed a real Native AA WiFi Direct session (SSL handshake complete in ~10s).
`KEYCODE_HOME` on this rig lands on the OEM system launcher, not `MainActivity` — reaching our own
main screen while the session stayed live needed the in-session exit dialog's **Picture-in-Picture**
option (a legitimate mechanism the code explicitly branches on via `App.isPiPActive`), not Home or
"Move to Background" (both land on the OEM launcher same as Home). With `MainActivity` foregrounded
and the session running in PiP: no banner, confirmed visually and in the log — the one
`showing the connection issue banner` line was from the cold launch *before* the session existed
(`isConnected=false`); zero such lines across three later `isConnected=true` resumes while the
session was live.

### R3 — tap lands on the row that fixes it, in Basic mode: PASS, all three
Basic mode throughout (never switched to Advanced). For each seeded stamp, tapped the banner body
(not the close X):

| Stamp | Search box | Row shown |
|---|---|---|
| connection-issue-bssid | "Static BSSID" | Static BSSID / Auto |
| connection-issue-hotspot-config | "Hotspot name (manual)" | Hotspot name (manual) / OHU-TEST |
| connection-issue-bt-silent | "Wireless Mode" | Wireless Mode segmented control, Native selected |

Extra-consumption check (bssid case): focused the search field, typed "XYZ" (confirmed appended:
"Static BSSIDXYZ"), then rotated the device 90° and back. The edited query survived unchanged.

**Setup-notes timing quirk (not a candidate defect):** a tap on the banner within ~2s of a cold
`am start` on this loaded rig sometimes lands on `MainActivity` with no effect (click listener
presumably not attached yet under this rig's background CPU load). A second tap ~2s later, or
waiting 4s before the first tap, always worked.

### R4 — the clear paths, on a real session: PASS (both), verified in-process — see rig contamination note
Since the app's own disk writes are blocked (same root cause as R2c), verification used an
in-process check instead of the brief's force-stop+read: end the live session (Stop Connection, not
force-stop, so the process and its in-memory Settings cache survive) and read the banner's
post-disconnect state on the same running process.

**R4a (WiFi Direct): PASS.** Seeded all three stamps, formed a real session (SSL handshake complete),
reached `MainActivity` via the PiP route while live (banner correctly absent per R2e), then ended the
session. Only `HOTSPOT_CONFIG_UNREADABLE` was shown afterward — bssid and bt-silent were both
cleared by the WiFi Direct handshake; hotspot-config was correctly left untouched (this route never
looks at it).

**R4b (hotspot transport): PASS.** Seeded hotspot-config only. Banner showed on launch, then
`SUCCESS - Providing credentials` 811ms later via the manual override. The phone's Bluetooth
happened to still be on, so a full real session formed over the hotspot transport too (unscripted
bonus — confirms the hotspot path completes end-to-end with a real phone). After ending that
session, the banner did not reappear.

### R5 — layout on this panel, and the absent control

**R5a landscape: PASS.** Banner fully on screen, not clipped, close button reachable, doesn't cover
home tiles or Exit.

**R5a portrait: UNTESTABLE** (rig limitation, not a candidate defect). This panel's
`DisplayDeviceInfo` reports a native 720×1440 (portrait) panel presented as 1440×720 landscape via
an OS-level rotation/install compensation. `settings put system user_rotation 1` (with
`accelerometer_rotation 0`) produced zero visible change across two separate attempts — identical
screenshot, identical layout. No way found to drive this rig into a genuinely different on-screen
orientation via adb.

**R5b: PASS.** The `wifi_direct_info` pill needs more than `wifi-connection-mode=2` alone — it's
driven by `AapService.wifiDirectName`, populated only once a WiFi Direct P2P group actually forms,
which per this rig's `WifiModePolicy` also needs `helper-connection-strategy=1` (not the default
2/Nearby). With both set, the pill ("WiFi Direct: Navegadortz2") and the banner were both fully
readable with no overlap.

**R5c: PASS.** All four `connection-issue-*` keys deleted: no banner, no gap, no padding, no stray
divider — pixel-consistent with every other "no condition standing" screenshot from this round.

### R6 — round-wide invariant: PASS
See "the four numbers" above.

## Design-gap findings (not scored as FAILs — no PASS criterion in this brief covers them)

**1. The banner doesn't check whether a manual override is already on file before raising.**
Observed during R5a's re-launch: the soft AP was stopped (for layout testing) but
`hotspot-ssid`/`hotspot-password` overrides were already correctly set from earlier runs. The banner
still raised `HOTSPOT_CONFIG_UNREADABLE` — its own call to action reads "Tap to enter them by hand,"
but they'd already been entered by hand. The condition is driven purely by the outcome of the next
connection attempt (no AP currently reachable), not by checking `settings.hotspotSsid`/
`hotspotPassword` are already non-empty. Normally invisible (self-heals in under a second once a real
AP is up — R4b measured 811ms), but if the AP is briefly down/out of range at a cold launch, a user
who's already fixed it sees a banner telling them to do something they've done.

**2. HOTSPOT_CONFIG_UNREADABLE's tap-through only surfaces the name field, not the password.**
`MainActivity.openRemedyFor()` seeds the Settings search with `R.string.hotspot_ssid_override`
("Hotspot name (manual)") only — confirmed in R3, the filtered list showed exactly that one row, not
"Hotspot password (manual)" alongside it. Both are required together for the condition to actually
resolve (per the password field's own description: "The phone will be given this password to join").
A user following the tap-through sets the name, has no on-screen indication the password also needs
setting, and may hit the same failure again without knowing why.

**3. BSSID_UNAVAILABLE's wording ("WiFi address") is vague where "MAC Address (BSSID)" would be
precise.** The banner text (R2a) reads "This unit could not read its own WiFi address" — a BSSID is
specifically the access point's MAC address, and "WiFi address" isn't standard terminology a user
(or a search for it) would recognize. Worth wording it "MAC Address (BSSID)" to match the terminology
the Settings row itself already uses ("Static BSSID") and to be unambiguous about what's missing.

**4. The banner doesn't consider which transport is currently selected.**
Observed during R4a (WiFi Direct active): with hotspot-config seeded alongside bssid/bt-silent, the
`HOTSPOT_CONFIG_UNREADABLE` banner showed on cold launch even though the app's current transport
(WiFi Direct) never consults hotspot-config readability at all. The brief's own R5b flagged the
parallel case one level up (a Native AA record showing while in Helper *mode*, called intentional)
— this is the same design one level down, scoped to *transport* rather than *mode*. Whether this is
desired (the record describes what the hardware did, independent of today's transport choice) or
worth narrowing is a product call, not a defect.

## Do not re-run (per brief §6, unaffected by anything above)
`afd8b7ca`'s own compile/test gate, the preflight dialog, whether a poke connects, and the three
raise paths remain out of scope for hardware verification on this rig, as the brief specified.
