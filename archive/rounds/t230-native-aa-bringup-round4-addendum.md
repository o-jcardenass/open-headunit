# t230-native-aa-bringup — round 4 addendum

Two corrections/additions from the operator after round 4 was pushed, appended rather than editing
the round 4 file per this branch's append-only rule.

## Device name: this unit is now **D-T230**

New unit for the rig, replacing D-HP (the HP Slate 7 Plus). Following the existing `D-<model>`
convention (D-HP, D-POCO, D-MOTO), this Samsung SM-T230 (Galaxy Tab 4 7.0) is named **D-T230** from
this point forward. Use that name in any future round or brief touching this unit; rounds 1-4's own
files are left as written (they predate the name and are not being edited).

## Correction: 720p **@60fps** also holds the link, not only @30fps

Round 4 reported 720p/30fps as the working configuration and left open whether 30fps was necessary.
The operator has since confirmed **720p@60fps also works** — the link holds with the frame rate
left at the user's original setting, once resolution alone is capped to 720p. **Resolution is the
binding constraint on this link, not frame rate.** Round 4's framing ("dropping to 720p/30fps
fixes it") should be read as "dropping to 720p fixes it; 30fps was not required and is a stricter
cap than this link actually needs."

## Root cause found in the app's own code, not chased down in rounds 1-4

The app already has a purpose-built mechanism for exactly this situation, and this device falls
through a gap in it. `aap/NarrowBandProfilePolicy.kt`'s own doc comment states the intent plainly:

> "Two units have now produced the same failure - the phone joins, opens the video channel and
> closes the socket seconds later having sent no frame at all - and on the second the radio has no
> 5 GHz band at all, so the band is not a remedy anyone can reach. A lower profile held on the same
> access point in both cases, so the cap is now applied..."

That is exactly rounds 2-3's own EPIPE/GAL-socket signature on D-T230. The policy caps to 720p/30fps
+ AAC (`NarrowBandProfilePolicy.CAPPED_RESOLUTION` / `CAPPED_FRAME_RATE`) whenever
`runsNarrow(supports5Ghz, sessionFrequencyMhz)` is true — i.e. whenever the radio has no 5 GHz band
(`supports5Ghz == false`) **or** the live session's own frequency reads in the 2.4 GHz range
(`sessionFrequencyMhz in 1..4000`).

**Both of those signals are structurally unavailable on D-T230 (Android 4.4.2 / API 19):**

- `connection/wifi/direct/WifiBandCapability.kt:31` — `supports5Ghz()` returns `null` unconditionally
  below API 21 (`WifiManager.is5GHzBandSupported()` doesn't exist yet), before it even tries to ask.
- `WifiBandCapability.kt:52-55`'s own comment on `sessionFrequencyMhz()`: *"`WifiP2pGroup` carries it
  from API 29 only and the other transports never report one, so 0 means unreadable and must never
  be taken for 2.4 GHz."* D-T230 is API 19, nineteen levels below that, so this always reads 0.

`NarrowBandProfilePolicy.runsNarrow(null, 0)` is `(null == false) || (0 in 1..4000)` = `false ||
false` = **`false` — the cap never fires**, and the app falls through to the user's full setting
(1080p/60 by default), which is exactly the request that produced rounds 2-3's repeating EPIPE/GAL-
socket failure. This is confirmed against rounds 2-3's own captures: `WifiDirectManager` logged
*"this unit will not say whether it has a 5 GHz band (below Android 5.0, or the WiFi service would
not answer)"* and every `[RES_CAP]` line read `linkCapped=none` — the exact null/0 inputs the code
above predicts.

**Not fixed this round (scope: reporting only, per the operator's request).** A real fix needs a
narrow-band signal that exists below API 21/29 — options worth a future coding-session look:
`WifiInfo.getFrequency()` (API 21, still doesn't help API 19), a fallback that treats "band unknown
below API 21" as narrow rather than as "no signal, don't cap" (the policy's own doc already
distinguishes "no answer" from "no band" and deliberately does not act on "no answer" — that
choice would need revisiting for this class of device), or simply defaulting `narrowBandProfileCap`
users on very old API levels to the capped profile outright. Whichever way, `NarrowBandProfilePolicy`
and its unit tests are the right place, and the fix is pure/testable without a device.
