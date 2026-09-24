# usb-version-retry: round 1 results

**Candidate:** fork `fix/usb-auto-connect` @ `0933a3480` (`fix/pause-auto-connect-all-modes` `1984b1ad4`
unchanged, plus the handshake fix on top). Three commits on `main` `dd454eed1`.
**Baseline:** `1984b1ad4` (round 1's own APK was gone from disk; rebuilt per the brief's own fallback).
**APK md5:** candidate `81b98f3cd54436b2bfe559f227109757` (R0, R1 both routes) — the file was overwritten
by the baseline rebuild between R1 and R2, so R3/R4 and the clean libusb re-run ran a second candidate
build from the identical source, `731da0e9074ac8f2109b2bc790ad9f9f` (non-reproducible debug-build
metadata only; DEX/JVM identity re-verified is not repeated per run, see Setup notes). Baseline
`8ef35d4cab03ef4ddaf7b096512c41eb` (round 1's cited `c482906e095a181a01bff17f0040d719` no longer on
disk).
**Unit:** Stage A — D-POCO (POCO X3 NFC, Android 15) as USB host over OTG, wireless adb at
`192.168.1.4:5555`, Carlinkit-class AA dongle (idle `18d1:4ee1`, accessory `18d1:2d00`), paired phone
Motorola edge 30 neo (D-MOTO).
**Date:** 2026-09-23

## Setup notes

- **A leftover `native-aa-wireless=true` legacy key contaminated the first libusb-route pass.**
  `Settings.kt:904-920`'s one-time migration reads that boolean, forces `wifi-connection-mode` to `3`
  (Native AA) on every read, and deletes the key — so an explicit `set_prefs_runas.sh` write of
  `wifi-connection-mode=0` does not stick past the next process start if that legacy key is still
  present anywhere in the device's SharedPreferences history. This is pre-existing migration code, not
  part of the candidate branch (identical on `1984b1ad4`). It fired mid-round: `wifi-connection-mode`
  read back as `3` when switching routes, and `WifiDirectManager`/`NativeAaHandshakeManager` activity
  is present in `r1_libusb_ohu.txt` from `16:18:07.882` onward (273 matching lines across the file) —
  i.e. essentially all of that route's plugs 2-10 ran with Native AA P2P group churn and HFP poke
  activity in the background. Cleared properly with an explicit `DEL native-aa-wireless` (and
  `DEL wifi-launcher-mode` for the sibling migration) alongside the `SET`, confirmed held across a
  relaunch. **The libusb route was re-run in full afterward with the leak confirmed absent throughout
  (`r1_libusb_clean_*`, 10 more plugs) — see R1 below.** Flag for future rounds on this device: always
  explicitly `DEL native-aa-wireless` and `DEL wifi-launcher-mode`, not just `SET wifi-connection-mode`.
- **`am start` cannot launch `SettingsActivity` from this adb shell context on this device right now**
  (`SecurityException: ... not exported from uid 10275`), even though `pause-auto-connect` round 1 did
  this successfully on the same device earlier the same day with the same command. The manifest entry
  is unchanged between the candidate and the baseline SHA, so this is not a branch regression; cause
  not resolved (not a compileSdk/targetSdk change either, both SHAs are on the same `main`). Worked
  around for R3 by having the operator tap into Settings manually *before* the disconnect broadcast
  (instead of the brief's literal disconnect-then-`am start` race) — this still exercises the same
  hold-check code path, just via a different ordering. Noted as a deviation, not silently absorbed.
- **`build_hur.sh` and `install_and_launch.sh` both target `HU=27870808938846` (D-HU) by default and
  overwrite the single `apks/com.andrerinas.headunitrevived_*.apk` output on every build.** Rebuilding
  the baseline for R2 silently destroyed the R0-verified candidate APK file; the candidate had to be
  rebuilt a second time (from identical source) for R3/R4, producing a different md5 from pure
  debug-build non-determinism. Both DEX symbol and JVM-gate checks were only run once, against the
  first build; not re-run against the second build since the source is byte-identical (same git SHA,
  no working-tree changes between the two builds). Copied both APKs to distinctly-named files in
  `apks/` afterward (`candidate-0933a3480-r2.apk`, `baseline-1984b1ad4.apk`) so this does not repeat.
- D-POCO's wireless adb address was `192.168.1.4:5555`, same as `pause-auto-connect` round 1.
- Settings backed up before any write (`round-usb-version-retry-r1/settings.xml.backup`, 8 keys) and
  restored at the end of the round, confirmed by reading it back.
- Scripts used: `build_hur.sh`, `set_prefs_runas.sh` (all pre-existing). New this round, left in
  `round-usb-version-retry-r1/`: `capture.sh` (copied from `round-pause-auto-connect-r1/`, same
  start/stop logcat-reader pair, unmodified).
- **Observation not asked for by the brief:** on every dongle session start, audio stutters briefly
  then normalizes. Not measured with numbers this round (no brief section covers it); worth a
  dedicated look if audio-start quality becomes a tracked thread.

## R0 — Build gate

**PASS**

- SHA: `git rev-parse --short HEAD` → `0933a3480`, matches the brief.
- DEX: `HandshakeMessagePolicy` (3 hits) and `AutoConnectHoldPolicy` (5 hits) both present via
  `strings` on the merged `classes*.dex`.
- JVM gate: 2335 tests, 0 failures (summed from `testGithubDebugUnitTest`'s per-class XML reports),
  matches the brief exactly.
- `adb install -r` succeeded on D-POCO.

## R1 — Cold plugs on the candidate, both routes (the point of the round)

**Libusb route: FAIL first pass (contaminated), PASS on a clean re-run.** **Standard route: PASS.**

### Libusb route, first pass — contaminated, reported for completeness only

Native AA background activity ran throughout plugs 2-10 (see Setup notes). Per-plug outcomes:

| Plug | Outcome | Accessory→SSL gap | Notes |
|---|---|---|---|
| 1 | clean, first handshake | 7.09 s | 2× `No VERSION_RESPONSE`, 2× discard, SSL on same handshake |
| 2 | clean, recovered on 2nd handshake | 2.60 s (from re-attach) | 1st handshake gave up (3 attempts); 3× discard on 2nd, SSL |
| 3 | **shape-B FAIL**, recovered on 4th internal attempt | 33.4 s total | 1st gave up; 2nd hit `more late VERSION_RESPONSEs than version requests sent`; 3rd drained 2365 B then `Failed to read AAP header`; 4th (fresh accessory re-attach) succeeded, 1 discard |
| 4 | clean, first handshake | 4.4 s | 1× timeout, 1× discard, SSL |
| 5 | clean, recovered via pre-existing drain path (shape C) | — | two full give-up cycles (3 timeouts each), 3rd attempt drained 36 B, SSL — no discard mechanism involved |
| 6 | **shape-B FAIL**, recovered on 3rd internal attempt | 52.7 s total | 1st gave up; 2nd hit `more late VERSION_RESPONSEs...` then drained 2365 B then `Failed to read AAP header`; 3rd (fresh accessory re-attach) succeeded, 2× discard |
| 7 | clean, first handshake, no trigger | 2.65 s | fast, no timeout |
| 8 | clean, first handshake | 10.98 s | 2× timeout, 2× discard, SSL |
| 9 | clean, first handshake, no trigger | 2.55 s | fast |
| 10 | clean, first handshake, no trigger | 2.72 s | fast |

Shape A (`Unable to parse TLS packet header`): 0 occurrences. Shape B (`Failed to read AAP header`):
**2 occurrences** (plugs 3, 6), plus a related new failure mode not named in the brief's shape table —
`SSL Handshake: more late VERSION_RESPONSEs than version requests sent` (the discard cap of 3 tripped
because 4 late responses arrived for only 3 requests sent), also 2 occurrences (plugs 3, 6, same
plugs). First-handshake SSL: 6/10 clean outright (1,4,7,8,9,10), 2/10 recovered without a forbidden
line (2,5), 2/10 hit an explicit forbidden line before recovering (3,6) — below the brief's "9 of 10"
bar and a violation of "zero ... Failed to read AAP header" as measured. **Given the concurrent Native
AA background activity, this pass alone does not settle whether the fix has a residual defect — see
the clean re-run below.**

### Libusb route, clean re-run — `wifi-connection-mode` confirmed `0` and no Native AA activity throughout

10 more cold plugs, same route, same candidate build, contamination cleared and re-verified after
every relaunch.

| Plug | Outcome | Accessory→SSL gap | Notes |
|---|---|---|---|
| 1 | clean, first handshake | ~1.8 s (dongle already attached at capture start) | 1× timeout, 1× discard, SSL |
| 2 | clean, first handshake | 9.4 s | 2× timeout, 2× discard, SSL |
| 3 | clean, first handshake | 5.6 s | 2× timeout, 2× discard, SSL |
| 4 | clean, first handshake, no trigger | 2.58 s | fast |
| 5 | clean, first handshake, no trigger | 2.68 s | fast |
| 6 | clean, first handshake | 7.77 s | 2× timeout, 2× discard, SSL |
| 7 | clean, first handshake, no trigger | 2.58 s | fast |
| 8 | clean, first handshake, no trigger | 2.80 s | fast |
| 9 | clean, first handshake, no trigger | 2.89 s | fast |
| 10 | clean, first handshake, no trigger | 2.75 s | fast |

**PASS, all of the brief's conditions:** zero shape A, zero shape B, zero cap-trips; every handshake
with `No VERSION_RESPONSE within 2s` followed by a response carries a `discarded a late
VERSION_RESPONSE` and `SSL handshake complete` on that same handshake (plugs 1, 2, 3, 6 — 4/10
genuinely exercised the fix); SSL on the first handshake in 10/10 plugs.

**Reading together:** the clean re-run's 10/10 result strongly suggests the two shape-B failures in
the contaminated pass were caused or amplified by the concurrent WiFi Direct/Native AA P2P activity —
plausibly CPU/radio contention on this device delaying the dongle-relayed USB reads past the point
where a 4th late response could still arrive within the discard cap's window — rather than a pure
defect in the fix's own logic. This is not proven from a single 10-plug clean sample; a repeat under
deliberately reintroduced Native AA load would confirm the causal link, but was out of this round's
scope once the contamination was found and corrected.

### Standard route — PASS

Fresh app process throughout (relaunched with `use-libusb=false`).

| Plug | Outcome | Accessory→SSL gap | Notes |
|---|---|---|---|
| 1 | clean, first handshake | 5.92 s | 2× timeout, 2× discard, SSL |
| 2 | clean, first handshake, no trigger | 2.85 s | fast |
| 3 | clean, first handshake | 9.62 s | 2× timeout, 2× discard, SSL |
| 4 | clean, first handshake, no trigger | 2.99 s | fast |
| 5 | clean, first handshake | 3.12 s | 1× timeout, 1× discard, SSL |
| 6 | clean, first handshake, no trigger | 2.67 s | fast |
| 7 | clean, first handshake, no trigger | 2.58 s | fast |
| 8 | clean, first handshake, no trigger | 3.05 s | fast |
| 9 | clean, first handshake, no trigger | 2.79 s | fast |
| 10 | clean, first handshake, no trigger | 2.70 s | fast |

**PASS, all conditions:** zero shape A, zero shape B; every triggered handshake completes with a
discard on the same handshake; SSL on the first handshake in 10/10 plugs.

## R2 — A/B against round 1's build (libusb route)

**Baseline demonstrates the pre-fix bug; candidate (clean re-run above) does not.**

Baseline `1984b1ad4`, 10 cold plugs, `use-libusb=true`, settings otherwise identical to R1.

| Plug | Outcome | Accessory→SSL gap | Notes |
|---|---|---|---|
| 1 | clean | 2.61 s | fast |
| 2 | clean | 2.74 s | fast |
| 3 | clean | 2.72 s | fast |
| 4 | clean | 2.47 s | fast |
| 5 | **shape A → shape B, then recovered** | 16.9 s | 2× timeout → `Unable to parse TLS packet header`; retry drained 2365 B → `Failed to read AAP header`; 3rd attempt (fresh accessory line) succeeded |
| 6 | **shape A → shape B → shape C, then recovered** | 26.0 s | 2× timeout → `Unable to parse TLS packet header`; retry drained 2365 B → `Failed to read AAP header`; 3rd attempt gave up after 3 timeouts; 4th (fresh accessory line) drained 36 B, succeeded |
| 7 | clean | 2.72 s | fast |
| 8 | clean | 2.51 s | fast |
| 9 | **shape A → shape B, then recovered** | 16.0 s | same chain as plug 5 |
| 10 | clean | 2.54 s | fast |

Baseline shape A count: **3** (plugs 5, 6, 9). Shape B count: **3** (same plugs, each immediately
following its shape A). First-handshake SSL: 7/10. This is the "marginal link" signature `pause-auto-
connect` round 1 misattributed to rig conditions — reproduced cleanly here on the same rig, same day,
same cable, confirming it is the baseline's own handshake bug, not environment. The candidate's clean
libusb re-run (R1 above) shows zero of this signature over an equal-sized sample.

## R3 — pause-auto-connect round 1's R1 again: the SSL leg

**PASS** — a clean, unbroken capture, closing the gap round 1's marginal link left open.

- Settings: `auto-start-on-usb=true`, `reopen-on-reconnection=true`, `auto-connect-last-session=true`,
  `kill-on-disconnect=false`, `use-libusb=true`, `wifi-connection-mode=0` (confirmed held).
- Deviation from the brief's literal script: `am start` could not launch `SettingsActivity` from this
  shell session (see Setup notes); the operator tapped into Settings manually *before* the disconnect
  broadcast rather than racing to open it after. This still exercises the same "is settings visible"
  hold check the brief is testing, just via the opposite ordering.
- The dongle did not self-re-enumerate inside the first 30 s settings-open window (it had been
  actively claimed by the just-ended session and did not self-cycle) — zero `Added device` in that
  window. Per the brief's own contingency for R1 ("a PASS with zero Added device proves nothing"), a
  physical replug was done, which produced the graded result below.
- After the replug: `Reopen on reconnection: not raised over settings or after the status pill's X`,
  then 2× `USB auto-connect held while the settings screen is open`. Front activity confirmed
  `SettingsActivity`. Zero of `Found device already in accessory mode`, `Switching USB device to
  accessory mode`, `Reopen on reconnection: launching MainActivity` in the held window.
- BACK sent `16:56:35.354`. `16:56:37.225 AapService: the settings screen closed with a USB
  auto-connect held behind it, checking USB now.` — gap **1.87 s** (within the brief's 1.3-2.5 s
  window).
- `16:56:38.466 SSL handshake complete.` — gap from replay to SSL: **1.24 s**.

## R4 — pause-auto-connect round 1's R4 again: the before-SSL X

**INCONCLUSIVE** for the before-SSL case, exactly as the brief anticipated ("if the X lands after SSL
on 3 tries, report it as INCONCLUSIVE"). After-SSL case not repeated (already PASS,
`pause-auto-connect-round1-results-addendum.md`).

Three attempts, each with a fresh `stdbuf -oL adb logcat -T 1 OPENHU:V '*:S' | grep -m1 -F 'Found
device already in accessory mode' && send ACTION_CANCEL_WIRELESS` watcher armed before a physical
replug, home screen, no session:

| Attempt | Accessory→SSL gap | Result |
|---|---|---|
| 1 | 2.24 s | SSL completed before the watcher's broadcast landed |
| 2 | 2.30 s | same |
| 3 | 2.21 s | same |

All three misses land in a narrow, consistent 2.2-2.3 s band — the fix's own faster clean-connect path
(SSL now often completing single-digit seconds or less after a fast dongle answer) has narrowed the
before-SSL window further than round 1 already found it to be. The mechanism itself (watch, broadcast,
`the status pill's X stopped the USB attempt.`) was already confirmed fast (66-109 ms end to end) in
round 1's addendum; nothing here suggests it needs more time, only a luckier (slower) dongle answer or
a different trigger point to land the watcher ahead of SSL.

## Do not re-run

Round 1's R0, R2, R3, R5, R6, and R4's after-SSL case all still stand as PASSed on `1984b1ad4`, which
this candidate carries unchanged. Stage B (D-HU, Bluetooth) was not in this round.

## Report back (brief §8)

1. **R1:** libusb route — first pass FAIL (2/10 shape-B, contamination-confounded), clean re-run PASS
   (10/10, zero shape A/B, trigger line + discard + same-handshake SSL in 4/10 plugs). Standard route —
   PASS (10/10, zero shape A/B). Trigger-line, discard-line, shape-A, shape-B counts are broken out per
   pass in the tables above.
2. **R2:** baseline per-plug table above; shape A count **3**, shape B count **3**, both confined to
   plugs 5, 6, 9, matching the exact bug signature the fix targets.
3. **R3 and R4:** R3 PASS (BACK→replay 1.87 s, replay→SSL 1.24 s). R4 before-SSL INCONCLUSIVE (3/3
   misses, 2.2-2.3 s accessory→SSL each); after-SSL not repeated (already PASS).

Captures: `rig-evidence-usb-version-retry` release, asset `usb-version-retry-round1-captures.zip`,
sha256 `76c5e33057ea3299706b5bc1e1060c6856a91250b8069d5cb0d6008b88a15687`.

## Anything the brief did not ask about

- **Audio stutters briefly on every dongle session start, then normalizes.** Observed across the round
  but not measured with numbers (no brief section covers audio-start quality); worth a dedicated look
  if this becomes a tracked thread.
- The `native-aa-wireless` legacy-migration leak (Setup notes) is worth a standing note for every
  future round on D-POCO: `set_prefs_runas.sh SET wifi-connection-mode:int:0` alone is not sufficient
  if that key or `wifi-launcher-mode` is present anywhere in the device's settings history — both must
  be explicitly `DEL`eted.
- `am start -n .../SettingsActivity` failing with a non-exported `SecurityException` on this shell
  session, when it worked earlier the same day for `pause-auto-connect` round 1 on the same device and
  the manifest is unchanged between both SHAs, is unresolved and worth a follow-up if a future round
  needs to script Settings navigation on D-POCO again.
- `build_hur.sh`/`install_and_launch.sh` both hardcode `HU=27870808938846` (D-HU) as the default target
  and both overwrite the single `apks/com.andrerinas.headunitrevived_*.apk` path on every build with no
  per-branch naming — costly when a round alternates between two branches (candidate/baseline) on a
  non-default device, as this one did. Worth a script fix (parameterize the output filename by SHA) if
  this pattern recurs.
