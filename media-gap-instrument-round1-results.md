# media-gap-instrument — round 1 results

**Candidate:** `fix/media-gap-instrument-and-attribution` @ `3398c8cc61cf1a9de2767451291d6503cd211217`
**Baseline:** `main` @ `e7a3b3ad` (no A/B needed for this round; not built)
**APK md5:** `0c498512761523f084272fccb22c63d1` (device-installed and local build agree)
**Unit:** UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14, 3.8GB RAM
**Date:** 2026-08-20

## Setup notes

**1. Signature mismatch at round start.** The device had a release-signed (non-debuggable) build
installed from an earlier round — `versionCode=98`, `versionName=3.2.6`,
`firstInstallTime=lastUpdateTime=2026-08-20 08:56:18` (installed that same morning, most likely
left over from the `release-next` thread's round 6 A/B work). `adb install -r` of this round's
debug build failed with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Recovery: backed up `settings.xml`
(80 lines) via `adb shell cat > file`, uninstalled the release build, installed this round's debug
APK, pushed the backed-up `settings.xml` back via root `adb push` + `chown` **before** ever
launching the newly-installed app (preserved config including `has-completed-setup-wizard=true`,
so the setup wizard never ran and resolution/DPI/video-codec were not reset), then confirmed
`run-as` now works (package debuggable).

**2. That restored settings.xml carried forward a stale `enable-audio-sink=false`**, which caused
R3's first attempt to come back inconclusive for the wrong reason (see R3 below). Restoring a
settings.xml backup captured under a *different* round/thread's build silently reimports that
build's non-default settings — worth flagging generally, not just for this round.

**3. Settings applied via `set_hu_prefs.sh`** (multi-key, single relaunch):
`log-level=2`, `wifi-connection-mode=3`, `view-mode=1`, `debug-video-fault-injection=0` (baseline),
later `enable-audio-sink=true` (fix, see R3), and `debug-video-fault-injection=2` /`0` around R5
via `set_hu_pref.sh` (single-key).

**4. `hur-wifi-test-scripts/` scripts used:** `build_hur.sh`, `run_unit_tests.sh` (R0),
`install_and_launch.sh SKIP_BUILD=1` (initial install), `set_hu_prefs.sh` (baseline settings +
the audio-sink fix), `set_hu_pref.sh` (single-key toggles for R5). No new script needed.

**5. First R5 attempt (`media-gap-r5.txt`) is void, not reported as a run.** `set_hu_pref.sh`
force-stops, edits, and relaunches in one call; the capture was started and cleared *after* that
relaunch had already formed a group and logged `FAULT INJECTION IS ON`, so the capture missed the
announcement and all of R1-style discard-check evidence (no `createGroup`, no handshake line at
all in that capture, despite live video). Discarded per the discard rules; redone as R5(b) with the
capture started before the relaunch.

## R0 — build and unit tests. Gate.

**PASS**

- First-ever compile of this branch: clean, no errors.
- `588` total tests, `0` failures/errors across all 60 test classes.
- Named classes: `LinkGapMonitorTest`=12, `UplinkStallMonitorTest`=5, `StationCoexistencePolicyTest`=7,
  `ProjectionWatchdogPolicyTest`=23 — all match the brief exactly.
- Both named regression tests present and passing:
  - `LinkGapMonitorTest > the ping masks a total media outage from the link series`
  - `ProjectionWatchdogPolicyTest > a collapsed consumer still fills the window`

## R1 — clean session, 10 minutes. The point of the round.

**PASS**

- Settings: `log-level=2`, `wifi-connection-mode=3`, `view-mode=1`, `debug-video-fault-injection=0`.
- Radio state: phone airplane mode ON before head unit launch, OFF (then explicit `svc bluetooth
  enable`) ~20s after settle, per the clean-run protocol.
- Discard-rule check: clean. Single `createGroup SUCCESS`, single `p2p-wlan0-0` interface, single
  `SSL handshake complete`. One benign `MATCH! Starting AapService` from the phone's own Bluetooth
  reconnect (zero group churn associated with it) — per the `native-aa-5288` round 1 precedent,
  this specific shape is not a discard-rule hit.
- Decisive lines, all four zero:

  ```
  grep -ac "inbound link quiet"   r1.txt   -> 0
  grep -ac "inbound video quiet"  r1.txt   -> 0
  grep -ac "inbound audio quiet"  r1.txt   -> 0
  grep -ac "uplink blocked on"    r1.txt   -> 0
  ```

- Context: 114 `Throughput over` lines, fps range 45-55, codec `c2.unisoc.hevc.decoder` (H.265).

## R2 — idle screen, 3 minutes. The false-positive guard.

**FAIL — headline finding.**

Reused the live R1 session, brought Android Auto to a stationary Google Maps screen with no
navigation running, left it untouched for 3 minutes exactly as specified.

- `inbound video quiet` fired **4 times**, not 0:

  ```
  08-20 11:09:27.389 AapTransport: inbound video quiet 5 times in 31430ms: dead=29921ms (95%), longest=14945ms, period~3319ms
  08-20 11:09:59.279 AapTransport: inbound video quiet 2 times in 31890ms: dead=31830ms (99%), longest=17872ms, period~17883ms
  08-20 11:10:34.675 AapTransport: inbound video quiet 4 times in 35396ms: dead=34255ms (96%), longest=16385ms, period~9042ms
  08-20 11:11:15.771 AapTransport: inbound video quiet 3 times in 41097ms: dead=40984ms (99%), longest=24586ms, period~13155ms
  ```

This is **not** the false positive the brief pre-registered as likely ("one continuous silence,
suppressed by the `MIN_GAPS_MEDIA=2` floor"). Source review of `LinkGapMonitor.kt` shows why: video
packets keep trickling in every few seconds (periods measured 3.3s-17.9s apart) even on a screen
that is visually static — a location-accuracy pulse, a clock tick, or AA's own periodic refresh —
and each arrival closes the current gap and opens a new one. With `dead=95-99%` per window, the
screen genuinely produced almost no video, but in enough separate isolated arrivals that ≥2
qualifying (>1200ms) gaps landed in every single 30-41s window. The `MIN_GAPS_MEDIA=2` floor does
not suppress this pattern; it only suppresses a *single* uninterrupted silence, which is not what a
"stationary map, no navigation" screen actually produces on this rig.

This is exactly the risk the brief's own framing said the round exists to catch ("an instrument
that speaks on a good session gets ignored on a bad one... that already happened on this project
once"). Here the direction is reversed but the risk is the same: the instrument speaks on a
genuinely healthy, intentionally-idle session, which is the false alarm this round was written to
rule out.

## R3 — audio pause/play cycling. Positive control, best effort.

**Two attempts. The first is void; the second is a genuine PASS.**

**R3-initial** (`media-gap-r3.txt`): no `inbound audio quiet` line at all, `Media Sink Stop
Request: AUDIO` = 0. On its face this matches the brief's pre-registered INCONCLUSIVE ("AA kept the
channel fed through the pause"), but that reading is wrong here: `enable-audio-sink=false` (carried
in from the restored settings.xml, Setup note 2) means `ServiceDiscoveryResponse.kt:159` never
declares the AU1/AUD media-audio-sink services to the phone at all. The audio series was
structurally never fed a single message — nothing about Android Auto's pause behaviour was being
measured. Confirmed by grepping both `r1.txt` and this capture for any AUD-channel service-discovery
or media-sink line: zero matches in either.

**Fix:** set `enable-audio-sink=true` via `set_hu_prefs.sh`, force-stopped and relaunched a fresh
session (single `createGroup`, single handshake — confirmed clean by the discard check).

**R3-final** (`media-gap-r3b.txt`): **PASS**.

- Service discovery now negotiates the real sinks: `Media Sink Setup Request: 1 on channel AUDIO1`
  and `... on channel AUDIO` both appear, where the initial attempt had neither.
- Six pause/play cycles run (`adb shell input keyevent KEYCODE_MEDIA_PAUSE` / `PLAY`, 4s/6s), with
  Spotify relaunched fresh on the phone beforehand to get a genuinely new channel-open decision.
- `inbound audio quiet` fired twice, both with `gaps >= 2`:

  ```
  08-20 11:21:16.837 AapTransport: inbound audio quiet 2 times in 30010ms: dead=3504ms (11%), longest=1755ms, period~10204ms
  08-20 11:21:46.840 AapTransport: inbound audio quiet 3 times in 30004ms: dead=5405ms (18%), longest=1840ms, period~10152ms
  ```

- `inbound link quiet` stayed **0** throughout — the real point of the run: the link stayed up
  while a media channel went quiet, the exact distinction the whole change exists to make.
- `Media Sink Stop Request: AUDIO` fired exactly **6** times, matching the 6 scripted pause cycles
  one-for-one.
- Discard-rule check clean: single `createGroup SUCCESS`, single live P2P interface
  (`p2p-wlan0-1` persisted the whole run; a `p2p-wlan0-0` reference in the first 0.2s of the
  capture was the *previous* session's interface tearing down at this relaunch, not mid-run churn),
  single SSL handshake, no Magic Garbage.

## R4 — the coexistence line.

**Required (unjoined) arm: UNTESTABLE. Optional (joined) arm: PASS, twice.**

The brief's premise — "the rig has no station association" — does not hold on this rig right now.
`dumpsys wifi` confirms the head unit has been associated to a real WiFi network ("Pegue Cdesta",
5500 MHz, WPA2-PSK, net id 2, IP 192.168.1.2) continuously since 08:54 this morning, independent of
anything this round did. Both R1's and R3-final's sessions therefore naturally exercised the
**joined** arm rather than the assumed-absent one:

```
08-20 10:57:25.211 W/OPENHU WifiDirectManager: This unit is connected to another WiFi network on 5500 MHz
  while hosting the WiFi Direct group on 5240 MHz. One radio has to retune between the two, which can
  stall projected video and audio together for a few hundred milliseconds at a time. Disconnecting the
  other network, or using the head unit hotspot instead, removes the switching.

08-20 11:20:06.187 W/OPENHU WifiDirectManager: This unit is connected to another WiFi network on 5500 MHz
  while hosting the WiFi Direct group on 5220 MHz. [... same message ...]
```

Both frequencies are known and differ in both cases, so a single combined **W-level** line
carrying both the descriptive and prescriptive phrases is exactly the brief's own predicted-correct
branch for that condition ("if they are and they differ, a W-level line with `Disconnecting` in it
is also correct"). **PASS** for the optional joined arm, reproduced on two independent group
formations.

Attempted to manufacture a genuine unjoined arm for the required condition: `svc wifi disable`
drops the station, but it also disables the WiFi radio entirely, which broke P2P group formation
too (`NativeAaHandshakeManager` needs the radio on even with no station association — confirmed via
a `BluetoothAdapter` reflection exception path and zero `createGroup` lines the whole attempt). A
genuine disconnect-while-radio-stays-on would need `cmd wifi forget-network 2`, which was not run:
it needs the WPA2 passphrase (not known) to restore afterward, and this exact association may be
shared state the `link-stall-periodic-scan` thread's rounds depend on (its round 5 entry explicitly
tests an "associated to 5GHz" arm). Judged too risky and hard-to-reverse for this round's scope.
WiFi was re-enabled and the station reconnected to "Pegue Cdesta" on its own — nothing was lost or
left in a different state than found.

**Verdict: required unjoined arm is UNTESTABLE on this rig at this time**, for a reason outside this
round's authorization to fix. The optional joined arm carries the round instead, and it passed.

## R5 — the recovery ladder still works.

**First attempt (`media-gap-r5.txt`) is void, not counted** — see Setup note 5.

**R5(b)** (`media-gap-r5b.txt`): **INCONCLUSIVE**, as the brief itself allows for.

- Settings: `view-mode=1` (confirmed before the run), `debug-video-fault-injection=2`
  (`DROP_MIDDLE_FRAGMENT`).
- `FAULT INJECTION IS ON` confirmed present this time, at construction:
  `AapVideo: FAULT INJECTION IS ON - DROP_MIDDLE_FRAGMENT 1-in-300, 0 candidates seen, 0 injected,
  no budget.` — the setting genuinely took.
- Over the full 5-minute run, the periodic summary line never moved past
  `DROP_MIDDLE_FRAGMENT 1-in-300, 30 candidates seen, 0 injected, no budget`. At a 1-in-300 rate,
  30 candidates producing 0 injected faults is the statistically expected outcome, not a sign the
  injector is broken.
- `grep -ac "FAULT INJECTED"` → `0`. Per the brief: a run with zero faults is INCONCLUSIVE, not
  PASS.
- No wedge: `rendered=` never dropped to 0 across any window (`rendered=230-234` range sampled
  through the run), fps stayed 45-51 throughout, `Display stall (` count 0, `cycling video focus`
  count 0 (nothing to recover from).
- `keyframe reached the codec` / `keyframe decoded` both fired 4 times — ordinary session-start
  keyframes, not recovery-triggered ones (no focus-cycling preceded them).

## What ships from this round

Three numbers, per the brief's own framing:

1. **R0**: compiles clean, 588/588 tests green, both named regression tests pass. ✅
2. **R1**: the four zeroes, all zero. ✅
3. **R2**: `inbound video quiet` on a genuinely idle screen is **4**, not 0. ❌ **This is the
   round's central negative result** — the false-positive risk the round was written to rule out
   is real on this rig's exact "stationary Maps, no navigation" scenario, because the screen keeps
   emitting sparse, isolated video packets rather than going fully silent.

R3 and R4's joined arm are bonuses and both passed, once a settings-contamination bug (R3) and a
premise mismatch about this rig's WiFi state (R4) were each run down and corrected mid-round. R5 is
insurance and came back inconclusive on its own predicted grounds, with no sign of a regression.
