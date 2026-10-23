# D-SAM and D-HP quirks

Read with either tablet. Moved verbatim from `TESTING-TEMPLATE.md` §7a on 2026-09-30. **Unless an entry names a unit, it was measured on D-HU.**


- **Only for a hand step a brief names (§3): `input tap` takes the UIAutomator logical coordinate
  space, not `screencap`'s physical one.**
  The physical buffer comes back portrait (800x1280) while the app forces landscape content into it
  (`rotation="1"` in a `uiautomator dump`). Tapping the screenshot's apparent pixel position fails;
  tapping the `uiautomator` bounds works. Cost real time on both units in `projection-raise` round 2
  before the right mapping was found. Dump, then tap the dump's bounds, on either tablet.
- **Device-to-device ARP resolution to a phone breaks transiently**, `Destination Host Unreachable`
  on both sides despite an ARP entry with the correct MAC, and self-resolves after a short wait with
  no adb intervention. Seen on both tablets in the same round. Wait it out before chasing it.
