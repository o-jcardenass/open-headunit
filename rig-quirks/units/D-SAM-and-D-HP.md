# D-SAM and D-HP quirks

Read with either tablet. Moved verbatim from `TESTING-TEMPLATE.md` §7a on 2026-09-30. **Unless an entry names a unit, it was measured on D-HU.**


- **Only for a hand step a brief names (§3): `input tap` takes the UIAutomator logical coordinate
  space, not `screencap`'s physical one.**
  The physical buffer comes back portrait (800x1280) while the app forces landscape content into it
  (`rotation="1"` in a `uiautomator dump`). Tapping the screenshot's apparent pixel position fails;
  tapping the `uiautomator` bounds works. Cost real time on both units in `projection-raise` round 2
  before the right mapping was found. Dump, then tap the dump's bounds, on either tablet.
- **`input swipe` with a duration fails on D-HP and is accepted on D-SAM.** On D-HP (API 17) it failed in two rounds:
  `input swipe x1 y1 x2 y2 300` printed `Error: Invalid arguments for command: swipe` (`audio-stutter` round 1), and the
  `input` usage text at every swipe (`pr-1090-audio-transitions` round 1). A swipe without the duration does work there.
  On D-SAM (API 19), `input swipe 3 400 3 400 500` printed nothing (`home-widgets-opacity` round 2, S-100 probe), so the
  command takes the duration. That probe touched the bare screen edge, so a hold that reaches a view is still unproven on
  D-SAM. On D-HP, no brief can hold a press or make a long press.
- **To show a pressed state below Android 5, use a D-pad focus, not a hold.** On these versions a button's focused state and
  its pressed state are usually the same drawable, so `input keyevent 19` to `22` (UP, DOWN, LEFT, RIGHT) put the pressed look
  on screen with no touch. On D-SAM the first key (LEFT) already moved focus onto a bar tile (`home-widgets-opacity` round 2). Read which node has
  focus from `focused="true"` in a `uiautomator dump`, after each key, and stop at the node the run needs. This does not
  replace a long press: no adb route to one on these units is known without root.
- **Device-to-device ARP resolution to a phone breaks transiently**, `Destination Host Unreachable`
  on both sides despite an ARP entry with the correct MAC, and self-resolves after a short wait with
  no adb intervention. Seen on both tablets in the same round. Wait it out before chasing it.
