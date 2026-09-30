# hands-free-wake-verdict — round 1 results

**Candidate:** `fix/992-hands-free-wake` @ `945e1b20`       **Baseline:** none (per brief; every
graded condition is decided on the candidate alone via settings)
**APK md5:** `353861f8a54e26baf77ffd75eba2a14f`
**Unit:** D-HU (UNISOC MT50, Android 14 / SDK 34, single BT radio) as the head unit for V1-V4;
D-POCO (POCO X3 NFC, Gearhead 17.5) as the phone throughout; D-SAM/D-T230 (Samsung SM-T230,
Android 4.4.2 / API 19, Dalvik) as the head unit for V5, added to the rig partway through the round
**Date:** 2026-09-19

## Setup notes

Inventoried `hur-wifi-test-scripts/` first. Used `run_unit_tests.sh` and `build_hur.sh` unmodified
for R0, and `set_hu_settings_host.py` (host-side, handles `<set>` string-sets on the rooted D-HU)
for every settings write. No new script was needed.

**Gearhead on D-POCO needed a manual step.** The brief calls for `pm disable-user` then
`am force-stop` against Gearhead on D-POCO for every V1/V2 arming. The automation sandbox this
round ran in denied both commands outright ("Interfere With Workloads"). The operator ran them
manually; re-enabling at teardown (`pm enable`) was not blocked. Whoever runs this thread's next
round from a similar sandboxed context should expect the same denial.

**D-HU's hands-free link to D-POCO is not a standing state on this rig.** It was down
(`mActiveDevice: null` on D-POCO's `HeadsetService`) at the start of the round and again after
arming B's poke took it (expected — that is what the DESTRUCTIVE verdict measures). It was
re-established before arming A and again before V2 by cycling **D-HU's own** Bluetooth adapter
(`svc bluetooth disable; sleep 5; svc bluetooth enable`, ~15s settle), confirmed via
`dumpsys bluetooth_manager | grep -A3 "Profile: HeadsetService"` on D-POCO before each arming.
Toggling the phone's own radio does not do this (see prior rig notes on the same mechanism).

**Two log-line errata against the brief, neither changes a verdict:**

- **L13 (`AapService: Native AA user exit. Stopping active launcher.`) never printed**, in either
  arming A or arming B, despite `headunit://exit` being used correctly every time. Traced in the
  candidate's source: that line lives in `AapService.onDisconnected()`, reached only when
  `CommManager` transitions from an active comm session to `Disconnected` — which cannot happen in
  a run where no phone connection was ever accepted. The exit path actually taken is
  `AapService.onDestroy()` calling `wifiLauncherManager.stop(WifiLauncherStopSequence.LAST)`
  directly (visible as `WifiDirectManager: Stopping and cleaning up...` /
  `WifiDirectManager: Final group removal success`), which still reaches
  `NativeAaHandshakeManager.stop()` — confirmed separately, because the counter moved exactly as
  the brief predicted in every arming. The counter was read directly from `settings.xml` instead of
  waiting on L13.
- **L8 (`"... so the hands-free link this unit took from it has a chance to come back."`) never
  printed** during arming B's 30s give-back window, though the brief listed it as expected. The
  retry loop's ~15s cadence plus the poke's own 20s hold left only one possible check inside the
  window (about 9s before the verdict fired), and it did not land. This is not one of the brief's
  formal PASS/FAIL conditions for V1 (those check only L5 counts, the L7 count, and the counter),
  so it does not change the verdict, but the window is evidently tight on this rig.

**V4** (not graded): the default settings destination (`extra_destination 0`) lands on "General",
not the Native AA / Advanced section the "Re-measure the Bluetooth wake" row lives in. It was not
visible in a `uiautomator dump` of that screen. Per the brief, not scrolled to find it; no
screenshot taken.

**V5**: no D-SAM/D-T230 was connected to this machine for the round — **UNTESTABLE**, not run.
`candidate-665332d8.apk` (md5 `28455e33b31ede223e76b84fc0a016d4`), named in the brief as the
optional baseline arm, was confirmed present and its md5 matched; it went unused since the primary
run never happened.

Settings restored from a pre-round backup and Gearhead re-enabled on D-POCO at teardown.
`native-aa-wake-damage-verdict` and `native-aa-wake-armings-without-session` are both **absent**
in the restored file (their pre-round state), which reads as unmeasured/0 by default.

Captures: `rig-evidence-hands-free-wake-verdict-round1` release asset on `fork`, zip sha256
`09afd90ef148b594d6b43992d5d8930bee9ffae636056e14af393ff0f8bf55d4`.

## R0 — build gate

**PASS**

- Full suite: **2184** tests, green.
- `BluetoothRadioCyclePolicyTest` **17**, `NativeAaWakeDamagePolicyTest` **11**,
  `HandsFreeWakeEscalationPolicyTest` **23**, `BluetoothWakePolicyTest` **26** — all match the
  brief exactly.
- Candidate APK md5 `353861f8a54e26baf77ffd75eba2a14f`, confirmed live on D-HU via
  `pm path` + `md5sum` before every run.

## V1 — a latched refusal lifts once enough armings have got nowhere

**PASS**

- Settings written: `native-aa-wake-damage-verdict=2`, `native-aa-wake-armings-without-session=4`,
  `native-aa-radio-cycle-verdict=0`, `wifi-connection-mode=3`, `connection-modes` contains `wifi`,
  `native-poke-bt-macs={D-POCO MAC}`, `native-poke-all-paired=false`,
  `last-connected-native-mac={D-POCO MAC}`, `log-level=0` (VERBOSE).
- Radio state: D-HU's own Bluetooth cycled off/on before arming A to force the HFP link up;
  confirmed via `dumpsys bluetooth_manager` before each arming. Gearhead disabled and confirmed
  dead (`pidof` empty) on D-POCO throughout.
- Discard-rule check: clean on both armings (`createGroup SUCCESS`=1, single `p2p-wlan0-0`, no
  `MATCH! Starting AapService`, no SSL handshake, no Magic Garbage) — arming B's check re-verified
  against a capture trimmed to before V2's process started, since its logcat capture was
  accidentally left running through V2 (see below).

**Arming A** (120s, `20:38:22` local launch):
- L1/L2 once at `20:38:24.496`, reading `(4 so far)`.
- L4 (`Not poking`) ×8.
- **L5 = 0.** L7 absent.
- Counter read back afterward: **5** (was 4).

**Arming B** (180s, `21:01:05` local launch):
- L1/L2 once at `21:01:07.747`, reading `(5 so far)`.
- First L4 at `21:01:08.798`.
- **L7 once**, at `21:02:38.930` — **90.132s** after the first L4 (91.183s after the arming line).
- **L5 = 2**: `21:02:38.939` (via HFP-AG), `21:02:39.523` (via HSP-AG, the fallback target) — both
  within 1s of L7, none afterward inside the 30s window.
- L6 at `21:02:39.696`: `Successfully poked POCO X3 NFC via HSP-AG. Holding 20000ms...`
- Verdict (**L10**, DESTRUCTIVE) at `21:03:08.944` — **30.014s** after L7.
  `native-aa-wake-damage-verdict` = **2** afterward.
- Counter read back afterward: **1** (the probe resolving reset it to 0 per
  `NativeAaHandshakeManager.armWakeProbe`, then the same arming's own `stop()` incremented it back
  to 1, since `standDownReachedThisArming` was already true from the pre-escalation `Not poking`
  calls). Not a brief-stated number, included for completeness.

**Operational note, not a finding:** arming B's logcat capture was not killed before V2 was
started, so `v1-arming-b.txt` kept collecting device log through V2's entire run. The tallies above
were computed before V2 started (uncontaminated), and re-verified against a copy of the capture
trimmed to end at the line immediately before V2's process (`pid 20018`) first appears — same
counts both times. The archived capture is the trimmed version.

## V2 — the counter moves on its own, and says so

**PASS**

- Settings written: as V1 except `native-aa-wake-armings-without-session=0`.
- Radio state: D-HU's Bluetooth cycled again before this arming (arming B's poke had dropped the
  HFP link, confirmed via `dumpsys` showing `mActiveDevice: null` beforehand).
- Discard-rule check: clean (`createGroup SUCCESS`=1, single `p2p-wlan0-0`, no self-wake, no SSL
  handshake).
- L2 at `21:08:20.688` reads `(0 so far)`.
- L4 ×8, **L5 = 0**, L7 absent.
- Counter read back afterward: **1** (was 0).

## V3 — the radio cycle stays out of the way on Android 13 and above

**PASS**

- **L11 = 0 and L12 = 0** across all three armings (arming A, arming B, V2).
- **L7 = 1**, from arming B — proves the stand-down path was reached and the cycle was evaluated
  and refused on the SDK gate rather than never running.
- D-HU `ro.build.version.sdk` = **34**.

## V4 — the re-measure row (optional, not a verdict)

**Not reached.** Deep-linked `SettingsActivity` with `extra_destination 0`, which lands on
"General"; `uiautomator dump` of that screen found no "Re-measure the Bluetooth wake" text. Per
the brief, not scrolled to search further; no screenshot taken.

## V5 — the API 31 listener resolves on an old Android

**Addendum: D-SAM/D-T230 was connected partway through this round** (not available for the R0-V4
work above) and V5 was run against it, including its optional baseline arm.

**INCONCLUSIVE — the check itself cannot discriminate on this unit, both ways cited below.**

- Candidate (`945e1b20`, md5 `353861f8a54e26baf77ffd75eba2a14f`) installed and launched; auto-connect
  to D-POCO was attempted (`NativeAA: Attempting manual poke to POCO X3 NFC...`) but never answered
  (`MainActivity: Auto-connect: nothing answered this attempt (mode=PILL_THEN_OVERLAY), ending it`,
  ~50s after the poke). D-POCO's `HeadsetService.mActiveDevice` was still latched to D-HU's address
  throughout (checked via `dumpsys bluetooth_manager`) — D-POCO was bonded/paired with D-HU from
  V1-V4 earlier in this same round, which is the likely reason Gearhead never woke for D-SAM's poke.
  This is a rig pairing-contention artifact of running both head units against the same phone in one
  round, not a candidate defect.
- **`grep -c "OnModeChangedListener"` on the candidate capture: 4**, not the brief's expected 0.
  All four are Dalvik verifier lines at app startup (`21:27:31`, one verification pass):
  `Failed resolving ...ExternalSyntheticLambda11; interface ... 'AudioManager$OnModeChangedListener'`
  and `Could not find class 'android.media.AudioManager$OnModeChangedListener', referenced from
  method ...AapProjectionActivity.unregisterAudioModeListener`. **No `NoClassDefFoundError` and no
  `FATAL EXCEPTION` anywhere in the capture**; the app process (pid 3855) kept running normally for
  5+ minutes afterward.
- **The baseline arm (`candidate-665332d8.apk`, md5 `28455e33b31ede223e76b84fc0a016d4`) was run on
  the same device immediately after, same launch, and produced the identical result**:
  `grep -c "OnModeChangedListener"` = **4**, same lines, same single verification pass at startup,
  also **no crash**. The same class also throws identical Dalvik VFY chatter for two entirely
  unrelated newer-API symbols in the same file
  (`android.app.PictureInPictureParams$Builder` from `enterPiP`, and `AapProjectionActivity.getDisplay`
  from `applyVirtualDisplayFix`) on **both** builds, which is not something either commit in this
  candidate touches.
- **Reading:** on this unit's Dalvik runtime (unlike the field reporter's Android 8/API 27, which is
  ART), referencing `AudioManager.OnModeChangedListener` anywhere in a class produces the same lazy
  verifier chatter whether the field's own declared type is fixed or not, and neither produces a
  crash here. The brief's literal grep threshold (0 = PASS, any hit = FAIL) is not a valid
  discriminator on this specific hardware: baseline and candidate are indistinguishable by it. The
  defect itself is still evidenced independently on the field reporter's real API 27/ART device
  (cited in the brief), and nothing here contradicts that — this unit simply cannot reproduce the
  crash either way, so it cannot confirm or refute the fix.
- Captures for both arms are in the `v5-captures.zip` addendum uploaded to the same release asset,
  sha256 `b2b21573a7c2b6edd04f4371336725e2781944d1cae84c0e88112975b446efd7`.
- D-SAM left on the candidate build (`945e1b20`) at the end of this addendum.

## Report back (§8 numbers)

1. **L5**: arming A = 0, arming B = 2 (both within the same escalated wake, one per SDP profile
   tried). Matches the expected `0` then `>= 1`.
2. Counter after V1 arming A = **5** (expected 5); after V2 = **1** (expected 1).
3. **L7** count across the round = **1**. Gap: 90.132s after the first L4 in arming B, 91.183s
   after the arming's own L1/L2 line.
4. **L11** count across the round = **0**, alongside D-HU `ro.build.version.sdk` = **34**.
5. V5's `OnModeChangedListener` count: **4 on the candidate, 4 on the baseline** (identical Dalvik
   VFY chatter, no crash on either) — see the V5 addendum for why this doesn't discriminate the fix
   on this unit.

Verdict values left in `settings.xml` at end of round: restored to the pre-round backup, in which
both `native-aa-wake-damage-verdict` and `native-aa-wake-armings-without-session` are absent
(reads as unmeasured/0).

## Anything the brief did not ask about

- The DESTRUCTIVE verdict measured in arming B is a real, reproducible property of this D-HU
  unit's own Bluetooth stack: its hands-free client link to D-POCO did not recover on its own after
  the wake, confirmed by `dumpsys` staying at `mActiveDevice: null` until manually re-cycled. This
  is exactly the failure mode the round exists to fix a permanent refusal for, and V1/V2 show the
  fix working as designed on the unit that actually exhibits it.
- No contamination reached the graded numbers, but the stray capture process (see V1's operational
  note) is worth a specific callout in this thread's next round brief: kill each arming's logcat
  pid before starting the next one, not just at the very end.
