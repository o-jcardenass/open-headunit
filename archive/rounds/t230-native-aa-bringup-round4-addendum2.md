# t230-native-aa-bringup — round 4, second addendum: physical mounting orientation, WiFi Direct identity

Follow-up to the operator's own observations, tested/traced against the already-running session from
round 4 (D-T230, `fix/audio-sink-and-wireless-bring-up` @ 5bf505d7a, same build, not rebuilt). Not a
new numbered round: this reused the live session, one settings toggle (reverted), and a code read.

## Finding 1: physical mounting orientation

**D-T230 needs to be physically mounted/held in its native portrait position for the app's
landscape output to display correctly — unlike every other unit on this rig, which mount landscape
as expected.** The operator's own words: "the only way to get D-T230 view is in portrait mode."

## What was tested here

`Settings.screenOrientation` (`screen-orientation` pref) offers `LANDSCAPE` (2) and
`LANDSCAPE_REVERSE` (3), each mapping straight to `ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE` /
`_REVERSE_LANDSCAPE` via `requestedOrientation = screenOrientation.androidOrientation`
(`AapProjectionActivity.kt:2227`) — ordinary, unremarkable Android API usage, nothing device-specific
in the app's own code path.

- **`screen-orientation=2` (the operator's working config):** `dumpsys SurfaceFlinger` reports
  `orientation=1` (`ROTATION_90`). A live `screencap` pulled during actual Native AA projection
  (`evidence/t230-native-aa-bringup-round4-orientation/screen-orientation-2-landscape-portrait-mount.png`)
  — this is what the operator confirms looks correct when the tablet is held portrait.
- **Tried `screen-orientation=3` (`LANDSCAPE_REVERSE`) as the obvious other option, to see whether it
  would flip the requirement to genuine landscape mounting instead:** `SurfaceFlinger` now reports
  `orientation=3` (`ROTATION_270`), and a live screencap taken the same way
  (`evidence/t230-native-aa-bringup-round4-orientation/screen-orientation-3-reverse-landscape-worse.png`)
  shows text and UI elements (the search pill, a media card) rotated 90° from a normal reading
  angle — clearly worse by inspection, not a fix. **Reverted to `screen-orientation=2`
  immediately** (full settings.xml restored from a pre-change backup, app relaunched, reconnected
  and confirmed rendering — `mRotation=1`, first frame rendered — before this file was written).
- Net result: **neither of the app's two fixed-landscape settings produces a display that's correct
  when the tablet is mounted landscape.** Only the operator's own portrait-mount + `screen-orientation
  =2` combination is confirmed working.

## What this does and doesn't establish

`screencap -p`'s relationship to the true physical output on this SoC (`pxa1088`/Marvell,
`OMX.MARVELL.VIDEO.HW.CODA7542DECODER`) was not independently re-verified here — the D-HP precedent
(`hp-slate-bringup-round1-results.md`) established that `screencap` captures the pre-rotation raw
panel buffer on that unit, confirmed against operator photos of the real screen. That assumption is
carried into this addendum but **not re-confirmed with a photo of D-T230's actual physical
screen** — the conclusion above rests on the operator's own hands-on mounting test, which is the more
trustworthy source here, with the screencap comparison offered as a secondary, consistent signal for
which of the two settings is closer to correct.

Not investigated: whether this is a ROM-level rotation-mapping quirk specific to this Samsung
firmware build (`T230XXU0ANJ4`, Android 4.4.2), a HAL/driver-level physical panel mount detail on
this unit, or something particular to how this SoC's `SurfaceFlinger`/`screencap` path composes
rotated output. Distinguishing those would need either a photo of the physical screen under each
setting, or a source/config read this session didn't have reason to do (this is firmware, not app
code — nothing in `AapProjectionActivity`/`HeadUnitScreenConfig` is implicated).

### Recorded as the working configuration for D-T230

`screen-orientation=2` (`LANDSCAPE`), mounted/held in the tablet's native portrait position. Anyone
setting this unit up should expect that, not a landscape dash mount — this is a real installation
constraint for D-T230 specifically, not a preference.

## Finding 2: "persistent" WiFi Direct identity doesn't work on this device either

The operator's own observation: D-T230 gets a new P2P group name (SSID) every session despite
`wifi-direct-stable-identity=true` — confirmed by every capture in this thread. Round 1 alone shows
`DIRECT-mO-`, `DIRECT-UZ-` in one run; across rounds 1-4 the suffix was different every single time
(`-mO-`, `-UZ-`, `-pE-`, `-JB-`, `-Gr-`, and the phone independently reported `-4N-` at one point) —
never once the same name twice.

**Root cause, traced in code, and it's the same shape of bug as the `NarrowBandProfilePolicy` gap in
the first addendum.** `WifiDirectManager.kt` has exactly one mechanism for naming a group:
`WifiP2pConfig.Builder().setNetworkName(identity.networkName)`, used in both `createQuietGroup`
(:1923-1924) and `standardCreateGroup` (:2234-2235) — **both call sites are gated behind
`Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q`** (:1914, :2226), because `WifiP2pConfig.Builder`
itself does not exist before API 29. `standardCreateGroup`'s own comment on the pre-Q branch it falls
through to is explicit about the consequence: *"The two-argument overload asks for the platform's own
stored profile, so the name and passphrase are whatever it kept from the last group this unit
owned"* (:2278-2279) — i.e. below API 29, `wifiDirectStableIdentity` has **no code path that can act
on it at all**; the name is entirely up to wpa_supplicant/the platform, which on this unit's driver
regenerates a random one every time. This matches round 1's own captured verifier error confirming
the class genuinely doesn't resolve here: `Could not find class
'android.net.wifi.p2p.WifiP2pConfig$Builder', referenced from method ...WifiDirectManager
.createQuietGroup`.

D-T230 (API 19) is nineteen levels below the Q gate — same distance, same shape of gap as
`NarrowBandProfilePolicy`'s API 21/29 signals in the first addendum. **Not fixed here** (reporting
only). If a fix is ever wanted, there is no pre-Q API that sets a P2P group's network name at
all — `WifiP2pManager` simply didn't expose one before `WifiP2pConfig.Builder` — so this one has no
code-level remedy below API 29, unlike the resolution cap, which does have signals available below
that gate that the policy just isn't using yet.
