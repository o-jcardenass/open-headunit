# Draft reply for PR #943

**Status: draft, for Oscar to review and post.** Not posted by the coding session. Background and
file:line evidence for every claim below is in `ultrawide-touch-and-usb-pr943-findings.md` on this
branch.

Tone notes for whoever posts it: Sesam17 is a graphic design studio manager who said up front he is
not a developer, he has been a patient and accurate reporter across a month of back-and-forth on
#809, and he asked first whether we wanted the code rather than dumping it. He also found a real bug
we had missed. The reply should read as a genuine thank-you with a clear technical no, not as a
rejection notice.

---

## Draft

Thanks for putting this up, and for the month of testing that led to it. Two things in here are
genuinely useful and we are taking both, with credit. The branch as a whole cannot be merged, and I
want to explain exactly why so it is not a mystery.

**First, your touch misalignment is our bug, and your logs pinned it.**

Your `HUR_Log_20260904_183908_621.txt` has the answer in one line:

```
[UI_DEBUG] Normal Scale. scaleX: 1.0, scaleY: 1.5
```

At 720p on your 1920x720 screen we scale the video up 1.5x vertically before drawing it, and then we
hand the touch code the *unscaled* screen size. The touch layer never learns about that 1.5x, so
every tap is off by that factor plus the crop. That is also why "Experimental touch alignment fix"
did nothing for you: it only picks between two ways of measuring the screen, and both of them are
1920x720 on your unit, so it changes nothing about the scale.

This is not specific to your car. Our own 1440x720 test unit hits the same fault at 1.125x. It has
been invisible until now because on a normal 16:9 screen that factor is exactly 1.0.

We have a branch that fixes it properly (it adds a real fit mode: stretch, fit-with-bars, or
fill-by-cropping, and teaches the touch code which one is active). It was written a few weeks ago and
parked. Your report is what gets it finished. Interestingly, its code comment describes the failing
case as "720p on a 1440x720 panel", which is our test unit, so we had seen this and not chased it
down.

**Second, the two things from your branch we are taking.**

1. Your restructure of the resolution block in `HeadUnitScreenConfig`. You fixed a real bug that has
   nothing to do with ultra-wide screens: when the resolution is locked *and* the user is on Auto, the
   old code falls into the manual branch and renegotiates down to 800x480. Nice catch.
2. Your pixel-aspect-ratio idea. This is the right answer and I do not think anyone here had thought
   of it. Android Auto only offers five fixed 16:9 resolutions, so on a 1920x720 screen there is
   nothing correct to ask for. Telling the phone the pixels are not square makes it *lay out* for your
   real screen shape while still sending a 1280x720 picture. We are generalising the formula so it
   derives from whatever the screen and resolution actually are, rather than the 1700/1280/720
   thresholds, which means it will work on every wide screen and stay at 1.0 on normal ones.

**Why the branch itself cannot be merged.**

It is your whole private fork rather than a set of fixes: the app id, name, icons, signing config,
and the `github` build flavour renamed to `emzoom`. That is completely fine for your own build, it
just cannot come upstream.

More importantly, it was built on an older copy of the project, so the diff *removes* a lot of work
that landed since: Self Mode and everything around it, several connection policies, and three test
files. Merging it would quietly undo a few releases. That is not something you did wrong, it is just
what happens when a fork drifts.

A few specific things to fix in your own build, though:

- **Please change `RECEIVER_EXPORTED` back to `RECEIVER_NOT_EXPORTED` in `UsbLauncherManager`.** That
  one word lets any other app on your head unit send us a fake "USB permission granted" message and
  make us try to connect to a device of its choosing. It also is not needed: that broadcast comes from
  our own app, so the original setting delivers it fine.
- The `startActivity` before `requestPermission` does nothing on a modern build. Android blocks
  starting an activity from a background service since Android 12, and it only works in your build
  because it also drops targetSdk to 28. It would also close your projection screen mid-session and
  can loop, since nothing stops it asking again. Worth knowing: we already have a version of "bring
  the app forward" that handles all of this (`AapService.launchMainActivityIfNeeded`), so if we act on
  your USB report, that is the piece we will reuse.
- The accessibility service cannot ship, unfortunately. Using the accessibility API to redirect apps
  is against Play policy and would get the listing pulled, and `appops set ... SYSTEM_ALERT_WINDOW
  ignore` permanently disables another app's overlay permission with no way back. I understand what
  you were solving (the OEM launcher and dialer stealing the screen) and it is a real problem, it just
  needs a different route.
- Also worth removing: the keystore password is committed in `build.gradle.kts`. Rotate it.

Your USB "requesting permission" problem is still open and I have not forgotten it. Your diagnosis is
probably right, that on these ROMs the permission dialog opens behind whatever is on screen so you
never see it. That one needs its own fix.

**If you want to send changes again**, the way that works is one small branch per fix, started from
the current `main`, with only the files that fix change in it. So the resolution-lock fix would be one
branch with one file changed. Much easier to review, and it lands much faster. Happy to walk you
through it.

Thanks again. Genuinely, this was useful.
