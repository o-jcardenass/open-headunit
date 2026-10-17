# Draft reply for PR #954

**Status: draft, for Oscar to review and post.** Not posted by the coding session. Background and
file:line evidence for every claim below is in `pr954-review-findings.md` on this branch.

Tone notes for whoever posts it: this is the same reporter as #809 and #943. He is a graphic design
studio manager who said up front that he is not a developer, and he has just done exactly what our
#943 reply asked for, which is one subject per branch off current `main`. Lead with that. The exit
action is a good idea and should be encouraged; the window change has to be a clear no, with the
reason spelled out so it does not come back a third time.

---

## Draft

Thanks for this, and thank you for splitting it up the way we talked about on #943. Three separate
commits off current `main`, each with its own subject, is exactly the shape that makes a review
possible. It took me an hour to read this instead of a week.

Going commit by commit.

**The exit action is a good idea and I want it.** Right now the phone's Exit button always kills the
session, and more than one person has asked for it to do something gentler. Two things stop me
taking this version as it stands.

The first is a real bug rather than a preference. `Return to App Home` starts our `MainActivity`, and
`MainActivity` has a rule in `onResume` and `onCreate` that says: if a session is connected and the
projection screen is not in front, bring the projection screen to the front. On your path the session
is still connected on purpose, so that rule fires immediately and throws the user straight back into
the screen they just exited. That branch cannot work without changing `MainActivity` too, and I would
rather not change it, because that rule is what makes the app come back after the screen has been
covered.

The second is the default. `Return to OEM Launcher` is option 0, so every existing install changes
behaviour the moment they update, with no way to have noticed. We have a written rule about this in
`Settings.kt`: the stored zero value is always the behaviour existing installs already have, so an
unset preference keeps working the way it did. `Disconnect Session` is today's behaviour, so it needs
to be the zero. Anyone who wants the new behaviour opts in.

Two smaller things while I am in there. `Return to OEM Launcher` is the same launcher intent as the
`Move to Background` entry in our own exit dialog (back button or the edge swipe inside the
projection), so it should call that rather than carry a second copy. And when Android Auto asks for
native focus we should answer it. Right now nobody tells the phone anything, which on a SurfaceView
head unit means the phone never re-runs its video setup, and coming back takes seconds instead of
tens of milliseconds. There is also a detail worth knowing: the phone sends the same "native focus"
message when its own screen simply turns off, not only when someone taps Exit. It is a separate field
on the message. With your default that would minimize the head unit to the launcher every time the
phone's screen blanks in a pocket.

None of that is a criticism of the idea. If you are up for it, the version I would take is: the
setting defaults to `Disconnect` (today's behaviour), the other choices reuse the actions the exit
dialog already has (background, picture-in-picture, show the exit dialog), the protocol code only
acts when the message says the reason was "user launched native", and it tells the phone we released
focus before doing anything. Happy to write it if you would rather not, and it would still be your
feature.

**The network window change I cannot take, and I want to explain why properly.**

`max_unacked` is the number of messages we let the phone send before it waits for us to acknowledge
them. Making it bigger does not make the link faster; it makes the phone allowed to run further ahead
of us. Three specific problems:

1. On the audio side, 30 becomes 60, but our audio queue holds 50 chunks by default and it is sized
   deliberately: the queue has to be able to hold the biggest burst the protocol allows, or we throw
   away sound that we told the phone it was allowed to send. At 60 the legal burst no longer fits.
   That is how audio stuttering reports start.
2. On the video side, the small window is load-bearing. When our decoder falls behind, we stop
   acknowledging, the phone's window closes, and the phone slows down to the speed our decoder can
   actually manage. That mechanism was built and measured on a 1920x720 screen, which is your screen
   shape. Multiplying the window by four means the phone can get four times further ahead before that
   brake starts working.
3. The switch it hangs off is the "Ultrawide" checkbox, and the network window has nothing to do with
   the screen shape. A 1920x720 panel does not send more data than a 16:9 one at the same resolution.
   It also reads that checkbox from a thread that runs before the screen configuration is
   necessarily set up, so on some runs the multiplier silently would not apply at all.

There is a fourth thing that is not your fault: we have a branch in review that removes the Ultrawide
checkbox entirely, because we ended up solving that problem a different way (the app now works out
the panel shape by itself and tells the phone the pixels are not square, which is your idea from
#943, generalised). So this commit would not compile once that lands.

**If you are seeing real stalls, I want to chase them, just not this way.** You are on 2.4 GHz
because of the channel 149 restriction, so you are the person most likely to hit a genuinely
congested link, and that is worth a proper look. What would help: two verbose logs from the same
drive, one on the current build and one with only the wireless video number changed, nothing else
different. The lines that matter are `Config response: ... (maxUnacked=N)` and the `dropped=`
counters. If the numbers say a wider window helps on a congested link, then it is a change for all
wireless users, and I will make it that way and test it.

**On the floating button service**, that one is your feature from #949 so it is mostly for Andre, but
two notes. The manifest side looks right. The branch also carries a top-level folder called
`UWfix And Features/` with a second copy of the two Kotlin files and a `.patch` file in it. That
looks like a working folder that got committed by accident; worth removing before this is merged.

Thanks again for the report and for the split-up branches. The exit action is going to happen in some
form.
