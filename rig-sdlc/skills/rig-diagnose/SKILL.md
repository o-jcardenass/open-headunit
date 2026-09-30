---
name: rig-diagnose
description: DRAFT. What to do when a rig run FAILs unexpectedly or looks wrong mid-round, adapted from the ohu-diagnose loop discipline for a tester who cannot change the brief's scope.
---

# Diagnosing a surprising run on the rig

The coding side owns hypotheses and fixes; the rig owns **a trustworthy observation**. Adapted from
Matt Pocock's `diagnosing-bugs`, cut down to what a round may do.

1. **Is it the user's failure or a neighbour?** Check the §7a quirks first: an orphaned P2P group
   from a force-stop, a phone that was a head unit and still holds `192.168.49.1`, a stale
   pairing, Gearhead's RFCOMM throttle, the identity verdict carried over from the last arm, a
   starvation cap from earlier fumbles. A quirk hit is a discard and a re-run (§4), not a FAIL.
2. **Raise the reproduction rate, not the cleanliness.** If the failure is intermittent, repeat the
   run's trigger within the brief's stop rule and report the rate (3 of 5), never "sometimes".
3. **Instrument with markers only.** Add `rig_marker <serial> <tag>` markers (from `rig_devices.sh`, no spaces in the tag) around the
   suspect window so the next grep is bounded. Do not add app log lines
   unless the brief allows a code change (rig CLAUDE.md, Modifying app code).
4. **Capture, do not theorise.** Quote the decisive lines with timestamps, keep the full capture
   for a FAIL, and write what you saw in Setup notes and "Anything the brief did not ask about".
   A hypothesis is welcome there as a hypothesis, marked as one.
5. **Choose the honest verdict.** Code path never reached on this hardware: INCONCLUSIVE. Cannot be
   set up: UNTESTABLE. Do not invent a substitute run to turn either into something (§6).
