# Audio focus regression + logging rewrite — round 7 results

No formal brief existed for this round; run at direct request after the transfer branch flagged
that `fix/audio-focus-pauses-bt-source` had been rebased onto 3.2.3 and picked up one new commit
(`bcf265ba`, the logging rewrite) since round 6.

**Candidate:** `fix/audio-focus-pauses-bt-source` @ `bcf265ba`       **Baseline:** `origin/main` @ `e900de78`
**APK md5:** `a20b1bf26929ed265376523a8ff0b696`, confirmed identical to the installed package.
**Unit:** headunit `27870808938846` (UNISOC MT50_YT610E4GFPSL_U, Android 14) / phone `4f4027e9`
(Redmi M2007J20CG, POCO X3 NFC)
**Date:** 2026-08-08

## Setup notes

- Sanity-checked the maintainer's rewrite claim before testing anything: diffed the audio-relevant
  files (`AapAudio.kt`, `PlaybackFocusPolicy.kt`, `BluetoothHelper.kt`, `SettingsFragment.kt`,
  `Settings.kt`, `strings.xml`, `PlaybackFocusPolicyTest.kt`) between the old range
  (`c318b4e4..fork/archive/audio-focus-round6-c0f3ec12`) and the new range (`e900de78..bcf265ba`)
  — patch content is **byte-identical**. Only the base (now includes the merged hotspot PR #799)
  and the commit boundaries moved, plus the one new logging commit. This means round 6's audio
  results still stand on their own merits; this round only needed to spot-check that nothing about
  the *new* base or the logging commit broke them, and to actually exercise what's new.
- Local `fix/audio-focus-pauses-bt-source` was `git reset --hard` onto the new
  `fork/fix/audio-focus-pauses-bt-source` tip; working tree was clean beforehand.
- **This rig's Bluetooth auto-reconnect is not reliable run-to-run.** Round 6 had the phone's A2DP
  link come up automatically as soon as its Bluetooth was enabled; this round, cycling the phone's
  Bluetooth off/on (twice, including one full 8s-off cycle) never brought `A2dpSinkStateMachine`
  back to `Connected`. This is test-rig Bluetooth-stack flakiness, unrelated to anything in this
  branch — round 6 already established the head unit's own Bluetooth can't be used as the control
  lever (self-reverts in ~14s), and the phone-side lever isn't fully deterministic either. Worth
  flagging for whoever plans the next round that needs a live A2DP link.
- Two distinct logging backends exist and both were touched by `bcf265ba` — tested both:
  `log-source=1` (`AppLog.Logger.File`, the per-line bounded-queue writer) and `log-source=0` with
  `log-capture-enabled=true` (`LogExporter`, the raw-logcat-to-file capture with the new 16 MB cap).
  They are separate code paths; a round that only exercises one is not covering the other.
- Settings backed up before any change and restored at the end via the same `run-as` + pushed-script
  method as round 6. This time `run-as $PKG sh -c 'cp ...'` worked where it hadn't in round 6 (no
  explanation found; the file-based `sh <script>` method used everywhere else remains the reliable
  one and is what round 6 already put in the template).
- Deleted the three multi-MB `HUR_Log_*.txt` files this round generated from the device's app
  external storage afterward — storage was never tight (26% used, 38 GB free) but no reason to leave
  them.

## A0 — build and unit-test gate (re-run against `bcf265ba`)

**PASS.** `run_unit_tests.sh` and `build_hur.sh` both green. APK md5 `a20b1bf2...`, confirmed
identical on-device. Version string changed from `3.2.3-alpha` (round 6, built off `c318b4e4`) to
plain `3.2.3` (this round, built off the `e900de78` release tag) — expected, not a defect.

## Audio-focus regression spot-check

Not a full A0-A6 re-run — round 6 already has that, and the diff confirms nothing audio-relevant
changed. This was specifically to confirm the new base + new logging commit didn't disturb it.

- **Acquire path (Automatic, no link — A3's scenario), unintentionally re-exercised** because the
  A2DP link would not come up this round: `AapAudio: AA audio started (AUDIO) - acquiring transient
  system audio focus (mode=AUTO, bluetoothMedia=false)`, matched by `last AA audio channel stopped -
  releasing transient system audio focus` on close. **PASS**, unchanged from round 6.
- **A4a (Always mode) acquire-regardless-of-probe confirmed**: `acquiring transient system audio
  focus (mode=ALWAYS, bluetoothMedia=false)` fired correctly. **The churn itself could not be
  re-demonstrated** — the AVRCP-pause loop that produces the churn needs a live A2DP sink
  connection as its mechanism, which wasn't available this round. Not treating this as a finding
  against the branch: the mechanism (Always always acquires) is confirmed, only the downstream
  consequence (the phone pausing itself) couldn't be observed without the link.
- **A2 (Automatic + link up, "leave alone") was not re-exercised** — same reason. Round 6's result
  for this run stands unchallenged given the patch-identical diff.

## Log-level / `AppLog` rewrite verification (the new commit)

**`log-source=1` (AppLog.Logger.File, the bounded-queue per-line writer) — PASS.**

Connected at `log-level=0` (VERBOSE) and streamed video + BT-less audio for ~3 minutes:

- 35,165 lines / 4.12 MB written, including 8,804 `SSL Decrypt Status` lines (the line moved outside
  the `synchronized(this)` block in `AapSslContext.decrypt()` by this commit) — format unchanged,
  content correct (`SSL Decrypt Status: OK, Produced: N, Consumed: N`).
- **Zero out-of-order timestamps** across every genuinely-timestamped line (checked
  programmatically, not by eye) — confirms the caller-side timestamping (vs. writer-thread
  timestamping) does what the commit message claims: order is preserved even though formatting and
  writing now happen off the calling thread on a queue.
- **Zero `lines dropped` markers** — the bounded queue (capacity 4096) never filled even under
  sustained VERBOSE load with heavy SSL/video traffic.
- `VideoDecoder.logThroughput` stayed at a steady 43-53 fps with `dropped=0` for the entire window,
  including while ~50 SSL lines/sec were being generated — no sign of the file-writer contending
  with the AAP/video threads it used to share a lock with.

**`log-source=0` + `log-capture-enabled=true` (LogExporter, the 16 MB cap) — PASS.**

Left running under the same VERBOSE + active session load (full-device logcat capture, not just the
app's own lines, so it fills much faster — matches the commit's own note that this is where the
16 MB budget matters):

- File grew from 0 to the cap in under 5 minutes.
- Stopped at **16,777,282 bytes** — 66 bytes over the 16 MiB (`16 * 1024 * 1024` =
  `16,777,216`) threshold, i.e. it stopped on the very next check after crossing it, as designed.
- Trailing line present verbatim: `--- capture stopped: reached 16 MB ---`.
- **No restart loop**: confirmed no new `HUR_Log_*.txt` file was created afterward and the logcat
  subprocess was gone (`ps` came back empty) — the cap path correctly avoids the
  `MAX_RESTARTS`-triggering "process exited" branch, exactly as the commit intends.
- App process and the AAP TCP session (port 5288) were both still healthy after the cap tripped —
  no crash, no dropped connection.

**Cached log level** — not re-derivable from a live UI change under this channel's no-UI-driving
rule, so verified at the code level instead: every place `Settings.exporterLogLevel` (or
`logSource`) can change already calls `AppLog.init()` — `App.kt` (2 places), `AapService.kt`
(2 places), `SettingsFragment.kt` (6 places), `QuickSettingsFragment.kt` (1 place) — confirming the
cache in `AppLog.LOG_LEVEL` can't go stale, matching the commit's own claim.

## Summary

| # | Result |
|---|---|
| Audio commits, round 6 vs round 7 base | Byte-identical patch content (confirmed by diff) |
| A0 build/unit-test gate | PASS |
| Audio-focus acquire/release + Always-mode-acquires | PASS (unchanged) |
| Audio-focus full churn reproduction | Not re-exercised — no live A2DP link this round |
| `AppLog.Logger.File` bounded-queue writer | PASS — ordering, no drops, no throughput cost |
| `LogExporter` 16 MB cap | PASS — stops within 66 bytes of the threshold, no restart loop |
| Cached log level | Verified at the code level (every mutation site calls `AppLog.init()`) |

No crashes, no ANRs, no FATAL EXCEPTION in any capture this round.
