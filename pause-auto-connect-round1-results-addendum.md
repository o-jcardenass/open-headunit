# pause-auto-connect: round 1 results — addendum

**Corrects R4 in `pause-auto-connect-round1-results.md`.** The brief was revised mid-round
(`fc90e3b24`, landing while R0–R3 were already running) to replace R4's UI tap with an
`AutomationReceiver` broadcast fired from a log watcher. The original results file graded R4
against the pre-revision tap method only and reported it INCONCLUSIVE. Re-run against the revised
method below; R4's verdict changes to **PASS** for the harder of its two cases. Nothing else in the
original results file is affected — R1's `send ACTION_DISCONNECT` and R6's broadcasts are the same
underlying calls the original run already used, just renamed in the brief's prose.

## Setup notes for this addendum

- **A pipeline bug, not a rig limitation, caused the first three corrected attempts to also miss.**
  `adb logcat -T 1 ... | grep -m1 ...` without `stdbuf -oL` block-buffers `adb`'s stdout when piped,
  so `grep` did not see the target line until a buffer's worth of further output had accumulated —
  in practice, minutes late. `capture.sh` already uses `stdbuf -oL` and was unaffected; the ad-hoc
  watcher command for R4 was not, until this addendum's runs. Worth folding into the template's `send`
  section: any `logcat | grep -m1` trigger pattern needs `stdbuf -oL`.
- The three late (pre-fix) broadcasts eventually landed on their own once the underlying `adb`
  buffers flushed — one at match+657 s, one immediately after (same buffer flush), one at match+223 s
  on a later capture. All three actually reached the app; the delay is why they missed the pre-SSL
  window they were aimed at. That accidental timing is what produced the after-SSL evidence below.
- Both `AutomationReceiver: ACTION_CANCEL_WIRELESS` broadcasts sent within the same ~50 ms window
  (from two of the three stale processes finally flushing together) show the service correctly
  routing to different branches back to back: the first hit the live USB session and printed
  `stopped the USB attempt.`; the second and third, arriving after that session had already torn
  down and D-POCO's own leftover `wifi-connection-mode=3` had re-armed wireless in response, printed
  `stopped the wireless bring-up.` instead. Both are correct for the state each one actually landed
  in — not a discrepancy.
- Captures: same `rig-evidence-pause-auto-connect` release, new asset
  `pause-auto-connect-round1-captures-addendum.zip` (r4_run4 through r4_run6), sha256
  `1a780eb2003271408d46132f45b18ccfd8da4806511f6bf56199b63e8936217e`.

## R4 — The pill's X on a USB attempt latches USB off (revised method, re-run)

**PASS** for the after-SSL case (the harder one — "the case the second commit fixes," per the
brief). The precise before-SSL trigger (X timed at `Found device already in accessory mode`, before
the session forms) still was not caught cleanly even with the corrected, `stdbuf`-fixed pipeline —
see below.

**Before-SSL case: still INCONCLUSIVE.** One clean attempt with the fixed, correctly-line-buffered
pipeline (`r4_run6`) ran for the Bash tool's full timeout without the dongle producing another
`Found device already in accessory mode` / `Switching USB device to accessory mode` line for it to
catch — the dongle happened to be in a stable, already-connected stretch for that window. The
mechanism itself (watch, then broadcast) is now confirmed fast: 66–109 ms from match to
`Broadcast completed` across the three delayed sends once they actually reached grep, so a future
round's clean before-SSL attempt should be well inside the brief's timing needs. Not proven again
this round for lack of a fresh window, not because the method is too slow.

**After-SSL case: PASS**, all of it observed in `r4_run5_ohu.txt`:

- Session live and streaming cleanly for several minutes (`SSL handshake complete` at
  `14:24:21.111`, video 0 dropped/skipped/concealed at ~29 fps through the window).
- `14:27:35.166 AutomationReceiver: com.andrerinas.openheadunit.ACTION_CANCEL_WIRELESS`
- `14:27:35.172 AapService: the status pill's X stopped the USB attempt. USB stays down until the
  USB button asks for it.` — **not** `stopped the wireless bring-up`, correctly identifying an
  established USB session (not merely an in-progress attempt) as the USB branch, 3 min 14 s after
  SSL completed.
- Session disconnected cleanly (`AapTransport stopping and sending byebye (USER_SELECTION)`,
  `session state disconnected (user_exit)`).
- Dongle self-cycled and re-attached at `14:27:40.141`; every subsequent attach was refused:
  `14:27:40.207 UsbLauncher: USB auto-connect refused: the status pill's X holds it until the USB
  button.` (repeated on further re-attaches, not just once).
- `14:27:40.180 AapService: Reopen on reconnection: not raised over settings or after the status
  pill's X (USB normal attach ...)` — zero `Reopen on reconnection: launching MainActivity` in this
  window, matching the brief's PASS condition.
- Step 2 — `send ACTION_CHECK_USB`, sent separately in `r4_run6` after resetting the capture:
  `14:31:03.638 UsbLauncher: a USB connection was asked for, so the stop from the status pill is
  lifted.`, then (after the same marginal-link retries seen everywhere else in this round)
  `14:32:15.842 SSL handshake complete.`

Both named PASS conditions for the after-SSL leg are met: the X took the USB branch and held it
across a replug, and the USB verb lifted it and a session re-formed.

## Updated round verdict

R0/R2/R3/R5/R6 unchanged (PASS, see the original results file). **R4 is now PASS** for the after-SSL
case and **INCONCLUSIVE** for the before-SSL case — not a clean FAIL or a clean PASS on its own, so
the run as a whole is best read as PASS-with-a-gap rather than either extreme. R1 is unchanged
(INCONCLUSIVE on the SSL-confirmation leg only, rig-blocked; the hold-and-replay mechanism itself
proven correct — see the original file).
