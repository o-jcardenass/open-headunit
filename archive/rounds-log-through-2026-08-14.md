# Transfer branch — not part of the app

This branch carries test briefs and results between the session that writes the code and the agent
that runs it on hardware. Orphan branch: no shared history with `main` or any feature branch, and
nothing here is ever merged.

Its name still says `hotspot-unreadable-config` because that is the round it was created for. It is
now the general channel; the name is left alone so existing links keep working.

## Standing method

- **`TESTING-TEMPLATE.md`** — read this first, and once. Capture rules, how to read and write
  `settings.xml`, the app's automation surface (deep links, actions, media keys), the clean-run
  protocol and discard rules, install discipline, the four verdicts, and the format results must come
  back in. It also has a section on writing the next brief.

Three rules from it are worth repeating here, because breaking any of them invalidates a round:

- **Use the rig's existing scripts.** `hur-wifi-test-scripts/` already has `build_hur.sh`,
  `run_unit_tests.sh` (added in round 6) and others. Inventory the folder at the start of every
  round, use what fits, and only add a script when nothing does — leaving it there for next time.
- **Settings are changed in `shared_prefs/settings.xml` with the app stopped — never through the
  UI, and the settings list is never scrolled with adb.**
- **Run the whole round unattended.** Moving between runs, restoring state and deciding that a gated
  run is INCONCLUSIVE are all yours. Escalate only for a failed build gate, a genuinely ambiguous
  fork the brief did not cover, something destructive, or a broken rig.

## Outbound — what to run next

**`video-dropped-frame-keyframe-round7-brief.md`** — the decision round 6 asked for, and it is short:
**R3's rise does not block the branch and cannot be the queue.** Read §0 of the brief for the
reasoning; the one-line version is that `dropped = arrived − fed` is an identity with queue depth
nowhere in it, the number is the 1–2% residual of two counts near 27,000, and four builds carrying the
*identical* 12-frame queue produced 36.7 / 51.2 / 92.7 / 106.6 per minute — a **2.9x spread with the
queue held constant**, which round 6's 67.0 sits inside. Round 6's own fps (19–52, against round 5's
42–51) says the decoder was slower, which is the direction that raises drops and the one thing the
queue cannot cause.

So the round is cheap and nothing waits on it. **R1 is a desk check with no rig time** — sum `fed=` and
`dropped=` across round 5's and round 6's retained captures to say *which* term moved; a deleted log is
a legitimate INCONCLUSIVE. **R2 is optional (~30 min)** and is not about #830 at all: three back-to-back
5-minute captures of the *same* build, to put a number on the repeatability of the sustained-overload
drop rate. Five rounds have quoted that rate as evidence and none has ever measured whether it means
anything. Either result is a finding.

History below. Round 6 (see Inbound) closed the *avoidance* half that round 5 left open. Under an
identical transient CPU-burst lever, the deeper queue (`VideoFeedQueuePolicy`, `d4f42814`) shed
**zero** reference frames against the old 12-frame queue's 4 (both of which escalated into a full
recovery cycle), with `skipped=` rising to compensate (110 vs 95): the input-drop-becomes-output-skip
trade, measured directly rather than argued from first principles. The thread's first A/B between two
builds, and its first run against a *transient* stall rather than sustained overload. Its R3 raised
the sustained-overload drop-rate question that round 7 §0 answers.

History below. Round 5 (see Inbound) closed out the recovery half. The retuned
escalation (`62889f29`) measured **2.672-2.678 s** from a shed frame to
`keyframe reached the codec` on both fully-captured cycles, against the ~35 s median / up to ~69 s the
same drop cost before it. R1 confirmed the new trigger is completely silent on a healthy
stream (zero escalation lines with `dropped=0`). R2's provocation produced 3 cycles this session
(capped correctly at the coded budget, 8 further attempts explicitly refused and correctly deferred to
the phone's own keyframe instead), spaced 65.3 s apart (over the 60 s minimum), with no #755 signal
across the whole capture. **The branch is ready for a PR.**

History below. Round 4 (see Inbound) closed the lever-probe
question. None of the three cheaper candidates (a no-op `UpdateUiConfigRequest`, a 1px-changed
`UpdateUiConfigRequest`, or a `VIDEO_FOCUS_NATIVE_TRANSIENT` release) beats the known-good `NATIVE`
release/regain cycle from round 3, and `NATIVE_TRANSIENT` turned out not to be cheaper either: every
fire produced the same full `Media Sink Stop Request: VIDEO` / `Media Start Request VIDEO` cycle as
`NATIVE`, with an incrementing session number each time. **The fix ships on the L4 (`NATIVE`) cycle;
there is no free lever.** The round's other deliverable, `VideoKeyframeScanner`, is confirmed
trustworthy against the old frame-size method (exact agreement on count, timing within 21 ms, and
byte size within 10 bytes) and is written to ship as the evidence source for a request-until-answered
latch.

History below. Round 2 tested candidate `ec0a2d28`
(`KeyframeCycleEscalationPolicy`, escalating the inert gain-only nudge to a real release/regain focus
cycle once a drop episode has stayed unbroken for 150s) and reconfirmed round 1's findings, but could
not exercise the escalation itself: this rig's own drop episodes never sustain past ~52s even under
the most aggressive lever available, a third of the 150s threshold. **Round 3 (see Inbound) closed
that gap with a TEST-ONLY branch** (`test/830-lowered-escalation-threshold` @ `a2e0268e`, never to be
merged, lowers the one constant to 5s) and got a clean answer: the escalation fired, the phone
answered the release with a real `Media Sink Stop Request: VIDEO` and rebuilt the stream on the
regain (a new session, `session=1`), the picture fully recovered within ~29.5s under sustained
worst-case overload, fps stayed healthy for the following four minutes with no #755-style permanent
freeze, and the once-per-session cap held exactly as coded. **The shipping `ec0a2d28` candidate's
core safety property is now confirmed on real hardware, on this unit, for a single cycle.** What
remains open (not blocking, just unverified by this channel): whether repeated cycles are safe
(structurally untestable without further modifying the test build, and deliberately not attempted,
since even one worst-case failure must be the shipping ceiling), whether the ~30s transition cost
generalizes to #830's much lighter real-world drop pattern rather than this round's continuous
overload, and whether a different phone/SoC combination reproduces #755 where this one didn't.

Nothing pending on `discovery-socket-leak`; round 7 (see Inbound) closed out the fourth commit — the
one thing round 6 couldn't see, since it post-dated that round and had never compiled or run anywhere
before this one. Link-loss dormancy holds clean through both a 60 s window and an unplanned 318 s one,
the two new "why the loop stopped" log lines fire exactly where the source says they should, and
round 6's own R19 (INCONCLUSIVE, provoked the wrong way — force-stop cycles against a process-scoped
guard field) is now resolved: provoked in-process this time, the guard fired on the first attempt.

The `link-stall-periodic-scan` thread's round 2 is answered (see Inbound) — both
bands, both interference levers, singly and combined, all measured clean on this rig. Settling it
further needs the reporter's own hardware; see that thread's own note below.

Separately, the audit behind that thread found a real defect that is **deliberately not a run**:
`WifiDirectManager.discoveryRunnable` re-arms every 10 s on `!isClientConnected`, which never becomes
true on the Native AA path, and nothing on that path calls `removeCallbacks` or `stopPeerDiscovery()`.
Arming it needs a runtime mode switch that cannot be scripted, so that coverage goes to a JVM test and
a code fix instead.

Nothing pending on `video-black-after-background`; round 8 (see Inbound) closed it out. GLES/TEXTURE
return-to-picture dropped further to a flat 1.9-2.1 s (from round 7's 3.0-3.2 s), the escalation
window measured at 850-859 ms as designed, SURFACE stayed at 0.6-0.8 s with zero escalations, and the
race round 7's R3 traced recurred twice this round and was caught cleanly both times with no restart
loop and no side effect on timing — round 7's one slow-SURFACE-cycle finding does not recur anywhere.
**The PR to upstream is unblocked.**

The only other still-open thread from that investigation is the link-stall-catch-up half of R6 (the
run, from round 3's own numbering, not the round-6 brief), parked until a sub-disconnect RF-blip
method exists — `git diff fc04147e..75334e3c` (pre-rewrite) / `d2cafa27..1192daa5` (post-rewrite)
shows none of that candidate's commits touch that path, so it was never in scope for rounds 3-6 and
stays parked rather than becoming its own round.

The external-Bluetooth-module (ZLink) thread's own next step needs no further hardware round on
this rig either — it needs one capture on the *reporter's* unit against the now-built transport
(`feat/external-bt-zbt-probe`), per its own section below.

### periodic link stall — round 2 answered; needs the reporter's own hardware next

Read `link-stall-periodic-scan-round2-results.md` (and round 1's, for the band-confound history)
before proposing a round 3. **Round 1's two FAILs were measured on the wrong band** — this rig is
API 29+ and creates its group on 5 GHz, while the reporter's API 27 unit falls through to a 2.4 GHz
social channel — so round 2 repeated R3 and R4 with the group forced onto 2.4 GHz
(`test/p2p-force-2ghz-band` @ `fdb4df27`, test-only, never shipped) at the reporter's own 720p
resolution. **The result is unchanged, now on the right band.** R0 PASS (244/244 unit tests). R1
PASS: `Freq: 2462 MHz (2.4GHz, channel 11)`, `NegotiatedResolution: 1280x720`, a clean 6+ minute
control (0 stalls in 428.4 s) — the band alone does not degrade this rig's link. **R2 and R3 FAIL
outright this time, not INCONCLUSIVE**: 18 phone-initiated scans (confirmed on the phone's own log,
closing round 1's one evidentiary gap) and 6+ minutes of the phone hosting its own SoftAP —
confirmed via `SoftApInfo` to be on the *identical* 2462 MHz channel as our own P2P group — both
left the RECV profile identical to the clean baseline. **R4 (both levers combined, run because R2
and R3 both failed) found no threshold effect either** — 0 stalls across a 1220 s window with the
hotspot on and scanning simultaneously. Between the two rounds, this rig has now ruled out band
mismatch, phone-scan interference and same-channel AP coexistence as reproducible mechanisms, on
both 5 GHz and 2.4 GHz, singly and combined — only the head-unit-side scan lever remains untested
(this unit's own `WifiScanner` is broken, confirmed twice across two rounds), and nothing left to
try on this rig actually approximates the reporter's Spreadtrum/API-27 stack. **Settling this
further needs a capture on the reporter's own hardware, not a round 3 here.**

**A refined hypothesis raised after round 2, checked against data already in hand rather than a new
run**: does the reporter's unit have a *saved* WiFi network in range (a phone hotspot, a home
network)? If so, Android's own `WifiConnectivityManager` can run periodic background scans looking
for it while station WiFi sits disconnected — which it does for the whole of a P2P-only session
(R5 confirmed `DISCONNECTED` throughout). That's the OS's own autonomous scanning, a different code
path than the explicit `cmd wifi start-scan` both rounds tested. This rig already has the
precondition (`cmd wifi list-networks` lists three saved networks, one plausibly a former hotspot),
yet R1's clean baseline ran with them present in both rounds and showed 0 stalls — consistent with
R0's finding that this rig's whole `WifiScanner` service goes unavailable the instant a P2P group is
up, which plausibly suppresses the framework's own autonomous scanning too, not just explicit calls.
A sharper, more specific version of the same Android-14-vs-8.1 divergence caveat, not a new round:
the useful next check is whether the reporter's own unit has a saved network in range and whether
his Android 8.1 build's `WifiConnectivityManager`/PNO logs show periodic scanning during his P2P
session. See the results file's own section on this.

Two brief-level fixes from round 1 both held cleanly in round 2 and are now in
`TESTING-TEMPLATE.md`: `log-level=0` (not 1) for `RECV:` visibility, and launching the head unit
app before bringing the phone's radios back online (reversing that order caused a real, confirmed
group-churn discard in round 1).

### video-black-after-background — closed as of round 8; history below

**Round 8 tunes the constants round 7 measured and fixes the one race round 7 found.** R0 gate PASS
(261/261, new `WarmRelaunchKeyframePolicyTest` 9/9). `2ccfa641` drops the escalation window from
2000 ms to 850 ms; measured on-device at **850-859 ms** across all eight GLES/TEXTURE cycles, exactly
as designed. R1 (GLES) and R2 (TEXTURE) both PASS: every cycle now recovers in **1.9-2.1 s** (down
from round 7's flat 3.0-3.2 s), all eight fired the escalation and were followed by `Media Start
Request VIDEO`, retake→first-frame gaps stayed comfortably positive (189-301 ms) confirming nothing
fired on a surface that was already about to render. `a304bf14` makes a surface that dies mid-configure
bail and wait rather than schedule a doomed restart; R3 (SURFACE, regression guard) PASS with **zero**
escalations across all four cycles (0.6-0.8 s each) — and the `decoder_start_failed: Surface not
valid` race round 7's own R3 traced recurred twice this round (more often than round 7's once) but was
caught cleanly both times (`Decoder start aborted:` 3-4 ms after `Failed to start decoder`, zero
`decoder_start_failed` restarts), with no unsolicited extra request and no timing cost — round 7's one
slow-SURFACE outlier does not recur anywhere. See `video-black-after-background-round8-results.md`.

**Round 7 confirms `fix/warm-relaunch-keyframe` fixes what round 6 diagnosed.** R0 gate PASS
(260/260, new `WarmRelaunchKeyframePolicyTest` 8/8). R1 (GLES) and R2 (TEXTURE), 4 cycles each on the
candidate: every cycle recovered in 3.0-3.2 s, all four fired the new focus-cycle escalation and were
followed by `Media Start Request VIDEO` every time, zero forbidden lines, zero `Forcing restart (`.
R4, the same-day baseline A/B on GLES: 30.8 s, 74.8 s, one cycle that never rendered before being
superseded (still mid-restart at 133 s when the next cover fired), and 49.3 s — the exact contrast the
brief asked for. R3 (SURFACE, regression guard): three of four cycles held at their usual 0.68-0.80 s
with zero escalations; the fourth hit the new mechanism once, traced in full to a pre-existing,
intermittent `decoder_start_failed: Surface not valid` race already present in the baseline (confirmed
absent from round 5's own SURFACE capture) — not a regression, and the new escalation recovered from
it cleanly rather than leaving the session stuck. R5 (5× rapid GLES cover/3 s hold/return): only 2 of 5
covers ever stuck long enough to claim a surface (the other 3 silently superseded, as round 5's own R4
established), and both survivors needed and got the escalation, recovering in ~2.5 s each. Neither of
the brief's two "most likely to be wrong" concerns materialized: the 400 ms release→retake gap never
left a `Media Start Request VIDEO` unanswered, and the 2 s window never fired on a surface that would
have rendered on its own. See `video-black-after-background-round7-results.md`.

**Round 6 explains round 5's own TEXTURE/GLES-vs-SURFACE asymmetry, with no device time.** Splitting
each return into three legs (relaunch, codec creation, wait-for-picture) shows leg C — waiting for a
decodable picture after the codec is already configured — is 98-99% of every slow GLES/TEXTURE
return and never the bottleneck on SURFACE, where it's 8%; leg A (the relaunch itself) never exceeds
~510 ms on any backend, refuting the competing "the relaunch is slow" explanation directly. `Media
Start Request VIDEO` appears after 4/4 SURFACE returns and 0/10 GLES/TEXTURE returns, exactly as
predicted. Dozens of gain-only keyframe requests go out per slow cycle and none of them is what
brings the picture forward — recovery instead tracks the decoder's own restart cadence. See
`video-black-after-background-round6-results.md`.

**Round 3 confirms the fix.** Every time the stale-surface ownership gate was actually exercised — R1
(clean, audio off), R2 (8× rapid trigger), and the one clean R3-SURFACE run — the picture returned and
stayed, with the `stale surface - ignoring` line firing directly in the SURFACE case. R5's reconnecting
watchdog and its throttle are confirmed working (`connected but no frames` at ~2 s spacing, overlay
clears on resume) and R7 confirms the misleading `restart suppressed` line is gone on an idle stream (0
occurrences over a clean 3-minute window). Full detail, including the new decoder-permanent-failure
finding that blocked clean signal on several other runs, is in
`video-black-after-background-round3-results.md`.

Round 2's own findings, for reference: it **reproduced the reporter's exact symptom live** (the user
watching the rig confirmed the black screen visually) and confirmed the reporter's own
force-stop/relaunch workaround fixes it — on both builds, by two different mechanisms. Round 1's
Home-press method never tore the surface down; `am start -n <MainActivity>` on a live session does.
Two named things needed fixing, **both predating `9f98afd1`**: `MainActivity.onResume()`'s "Active
session detected, bringing projection to front" branch, which recreates a live `AapProjectionActivity`;
and the **stale `onSurfaceDestroyed` callback** that follows it, firing ~250-650 ms after the new
instance had already reconfigured the singleton decoder for a *different* surface. Round 2 also traced
the misleading `restart suppressed (N/4 used, …)` line to `VideoDecoder.outputThreadLoop()` printing it
outside the idle-vs-stall branch — round 3 confirms the fix for that too (R7, above).

### external Bluetooth module — the ZLink teardown round is answered

Read `zlink-wpp-channel-response.md` before acting on its results. The round produced two real
findings, but its apparent conflict with the `0x105`/TCP-3152 transport is a wrong-binary artifact:
the socket layer lives in `libzbt-main.so` (which owns `socket`/`bind`/`listen`/`accept` and exports
`FoxServerInit`/`FoxClientConnect`), not in `gocsdk_zj`, which only `dlsym`s the `libzbt_*` protocol
API. Port and framing are in any case **measured live on the reporter's unit, twice** — a client
built from the spec was answered with seven decodable frames — so they were never resting on static
analysis.

**Do not run the proposed `/dev/socket/goc_rfcom` capture.** That is the daemon's **iAP/CarPlay**
surface (`accepted a iap uart client`, `is_iap_connected=%d is_aa_rfcomm_connected=%d`), not the
Android Auto one, so it would return a misleading negative. The equivalent capture on the right
interface is `netstat -tnp | grep 3152`, and round 1 already ran it.

The next move needs no teardown: the transport is now built
(`feat/external-bt-zbt-probe`), and it logs `[ZBT] first bytes from the phone over the module` on
the first inbound `0x105`. One reporter run settles what three static rounds could not.

### Answered earlier — history, not work

Nothing below is actionable. Kept so a round is not re-proposed after it has already been answered.

Round 1 of the media-key-routing investigation is answered (see Inbound):
every run that could be meaningfully exercised on this rig passed, including the two runs that
mattered most (R4, the AUTO setting against a real fluctuating A2DP link; R6, the non-media-key
regression check). R9 and R10 both came back with genuine, specific gaps rather than clean PASSes,
neither a finding against the branch.

Round 6 of the discovery-socket-leak investigation is answered (see
Inbound), the sixth/seventh/eighth commits fix the reporter's own double-probe defect cleanly (R15
15/15, R16 10/10, both on the reporter's own settings), with no Native AA regression (R17) and no
refused-handover sockets anywhere (R20). Two runs remain formally unverified on this rig for reasons
unrelated to the fix itself: R18 (USB regression) because this rig has no USB accessory path at all,
and R19 (the cross-instance timing guard) because an `adb`-driven relaunch loop couldn't be timed into
the race window across two tries. Both are open gaps, not failures.

The video-latency round (below) came back clean: R1's veto did not trigger, and
R4-R6 all resolved (one INCONCLUSIVE for a legitimate rig-speed reason, not a branch defect). The one
actionable finding is a pre-existing bug unrelated to this branch — see Inbound — which is a candidate
for its own brief if someone wants to chase it, but nothing here blocks the video-latency branch.

Round 11 answered R4 (the open question) and confirmed R2/R3/R5/R6 — see Inbound.
**R7 came back INCONCLUSIVE**, not from a rig limitation in the usual sense but from a live-timing
race that four different attempts could not land (see the results file). If a future round wants to
settle it, it needs either a phone/rig where the adapter-off window is wider than this one's, or a
way to hold `NativeAaHandshakeManager.start()`'s adapter-enabled check open longer than a plain
`svc bluetooth enable` → `am start` sequence reliably provides — the results file details exactly
what four attempts tried and where each one failed, so as not to repeat them blind.

Round 9's rig findings are in **`TESTING-TEMPLATE.md` §7a**: bonded is not the same as Bluetooth-on
and adb cannot fix it, the phone's own reconnect beating the poke plus both recipes for forcing one,
and HSP-AG failing here exactly as it does on the reporter's unit (round 10 found HSP-AG *can*
connect on this rig in some sessions, see its own §7a addition). `set_hu_prefs.sh` and the
relaunch-per-key hazard that motivated it are in §5, with a pointer to the rig-side
`code-researchs/hur-wifi-test-scripts-inventory.md`.

Round 8 is answered — R5's headline prediction (one pause, no cycle) is confirmed, R8's newly-added
backup-key coverage is confirmed, and three runs came back INCONCLUSIVE or UNTESTABLE for
hardware/rig reasons rather than branch defects (see `audio-focus-round8-results.md` for what to
weigh before deciding whether any of those three need a dedicated retry). None of those three is
retried in round 9, which is about a different mechanism entirely: R7's connect-with-Bluetooth-off
remains impossible here per §7a, R9's settings category is still not deep-linkable, and R6's
protocol-driven focus request is still worth a dedicated round rather than a corner of this one.

Round 8's rig findings are now in **`TESTING-TEMPLATE.md` §7a** — the A2DP link's third and worst
failure mode, the fact that a live link survives head-unit restarts so it should be reused once
obtained, adb airplane mode being blocked on this phone, the phone's own Bluetooth self-reverting,
Native AA needing Bluetooth to connect at all, and settings categories not being deep-linkable. Two
of those made runs in this very brief impossible to perform, so **§8 now also says to check a run is
physically possible on this rig before writing it** — R7 and R9 were dead on arrival and cost the
tester real time to prove so.

> **The round 6 and round 7 archive refs have been deleted.** `audio-focus-round6-results.md` and
> `audio-focus-round7-results.md` name commits (`c0f3ec12`, `bcf265ba`) that are no longer reachable,
> so those exact builds cannot be rebuilt or diffed against. The results stay as the record of what
> was observed; treat the SHAs in them as historical labels, not as things to check out.

## Inbound — results from the rig

- **`video-dropped-frame-keyframe-round6-results.md`**: **R2 (the point of the round) is a clean PASS;
  R3 surfaced a rise that needs a decision, not a re-run.** Candidate `fix/830-request-keyframe-on-dropped-frame`
  @ `d4f42814`, baseline round 5's `62889f29`. R0 PASS: 292/292 unit tests (new `VideoFeedQueuePolicyTest`
  6/6). R1 PASS: silent on a healthy stream, feed-thread line confirms the new 30-frame/500ms depth is
  live. **R2, the A/B**: under an identical CPU-burst transient-stall lever, the candidate shed
  **zero** reference frames (`dropped=0`, `skipped=110`) while the baseline shed 4 (`dropped=4`,
  `skipped=95`, both escalating into full recovery cycles at 2.568 s and 2.689 s), the
  input-drop-becomes-output-skip trade, measured directly. **R3, candidate-only sustained overload**:
  10.43 minutes combined (a mid-round capture-duration slip was caught and corrected with a second
  segment rather than discarded; the resulting 30.2 s gap between segments is disclosed precisely and
  excluded from every affected table) measured **67.0 drops/min against round 5's 36.7/min on the
  identical settings and duration, roughly 1.8x**. The recovery chain itself reproduces round 5 closely
  (3 cycles, all 2.669-2.695 s T0-to-repair, budget and cooldown both held exactly as coded, zero
  silent self-clears matching round 5's own finding), and no #755 signal appeared (fps 19-52,
  `Codec initialized:` zero mid-session). But the rise itself is real against a real historical number,
  not noise, and R3 was scoped candidate-only per the brief, so no same-session A/B baseline exists to
  rule out a confound. Also unanswered, and flagged precisely as such rather than guessed at: whether a
  drop can clear inside the 2 s window without escalating remains unobserved by any round in this
  thread, since every provocation tried either produces zero drops or produces them densely enough to
  always reach the 2 s check. Full detail, including the exact source-level explanation for a 4:1
  throttle ratio between the `dropped a reference frame` log line and the actual drop count (not a
  defect, a rate-limited log line vs an unconditional counter), is in
  `video-dropped-frame-keyframe-round6-results.md`.

- **`video-dropped-frame-keyframe-round5-results.md`**: **the fix works as designed and the branch is
  ready for a PR.** Candidate `fix/830-request-keyframe-on-dropped-frame` @ `62889f29`, the shipping
  branch. R0 PASS: 286/286 unit tests (10 rewritten `KeyframeCycleEscalationPolicyTest` cases). R1
  PASS (regression guard): completely silent on a healthy 5-minute hardware-decoding stream, zero
  escalation lines with `dropped=0`, natural keyframe cadence reproduced round 4's fixed-GOP finding
  (67.8-68.6 s this round vs 69.448 s median there). **R2 is the headline**: the same
  force-software-decoding provocation rounds 1-3 ran (which produced 46-242 drop triggers and zero
  cycles between them) now produced **3 escalation cycles this session**, capped correctly at the
  coded budget with 8 further attempts explicitly and correctly refused rather than firing a fourth.
  Both fully-captured cycles (session 2 and session 3; the first cycle fired in the gap before capture
  started, corroborated three independent ways and disclosed precisely in Setup notes) measured
  **T0 to `keyframe reached the codec` at 2.672-2.678 s**, against the ~35 s median / up to ~69 s the
  identical drop cost under rounds 1-3's old escalation. Spacing between the two captured cycles held
  at 65.3 s, over the 60 s minimum. The 8 refused runs correctly deferred to the phone's own natural
  keyframe, taking 51.7-69.1 s each, consistent with R1's own measured cadence. No #755 signal: fps
  held 42-51 throughout, and `Codec initialized:` never fired mid-session across either cycle,
  matching round 4's finding that the decoder component survives repeated cycles intact. One notable
  side finding: under this round's *sustained* heavy overload, zero drop episodes self-cleared inside
  the 2 s window without either escalating or being refused, a property of the stress-test provocation
  specifically rather than necessarily of the lighter single-lost-frame case #830 actually reports.
  Full detail, including the per-cycle chain tables and the capture-boundary gap around the unseen
  first cycle, is in `video-dropped-frame-keyframe-round5-results.md`.

- **`video-dropped-frame-keyframe-round4-results.md`**: **none of the three cheaper levers beats
  the known-good NATIVE release/regain cycle, and NATIVE_TRANSIENT turned out not to be cheaper
  either.** Candidate `test/830-keyframe-lever-probe` @ `1dc7e6ec` (TEST ONLY, never to be merged).
  R0 PASS: 284/284 unit tests, new `VideoKeyframeScannerTest` 12/12 as predicted. R1 PASS (gate for
  R2): the new NAL-type-based `VideoKeyframeScanner` agrees exactly with the old frame-size method on
  all 3 keyframes in a 3-minute undisturbed capture (timing within 21 ms, byte size within 10 bytes),
  confirming it as the trustworthy instrument going forward, and explaining why the old size-only
  method was ambiguous (large P-frames right after a scene cut can exceed 10x the median size without
  being keyframes; only the fragmentation pattern, not raw size, reliably distinguishes them). **R2
  is the headline**: one continuous ~16-minute capture ran L1 (no-op `UpdateUiConfigRequest`, 8
  fires), L2 (1px-changed `UpdateUiConfigRequest`, 8 fires), L3 (`VIDEO_FOCUS_NATIVE_TRANSIENT`
  release/regain, 5 fires) and L4 (`VIDEO_FOCUS_NATIVE` release/regain, 2 fires, the control) against
  a natural keyframe cadence measured at a strikingly tight 67.9-69.7 s (median 69.45 s) across 8
  clean gaps. L1 and L2 are confirmed inert a third time: both medians (35.96 s and 33.11 s) land at
  almost exactly half the natural period, the arithmetic signature of a random observer waiting out a
  periodic process, with the phone acknowledging every single fire (16/16 `UpdateUiConfig reply
  received`) but never once answering with a keyframe. **L3 is the round's real finding**: contrary
  to the brief's best-case hope, `NATIVE_TRANSIENT` did not hold the session, every one of its 5 fires
  produced a full `Media Sink Stop Request: VIDEO` / `Media Start Request VIDEO` cycle with an
  incrementing session number, exactly like `NATIVE`, and the "keyframe" that follows both L3 and L4
  (within 0.5-0.8 s of release) is not a nudge working, it is the fresh session's own tiny 8,200-byte
  startup frame, dwarfed by the 51,000-80,000 byte natural keyframes measured in the same capture.
  L3 costs exactly what L4 costs; there is no cheaper lever than a full release. No #755 signal
  anywhere: `dropped=0` for the entire capture, and the codec component was never re-initialized
  across all seven sink-stop/start cycles (5xL3 + 2xL4) despite more disruption in under 5 minutes
  than round 3's single cycle. Full detail, including the per-lever (T0, delta) tables and the
  fragmentation-pattern method that resolves round 2's own size-method ambiguity, is in
  `video-dropped-frame-keyframe-round4-results.md`.

- **`video-dropped-frame-keyframe-round3-results.md`** — **TEST ONLY branch forces the escalation for
  the first time on real hardware, and it survives cleanly.** Candidate
  `test/830-lowered-escalation-threshold` @ `a2e0268e` (never to be merged), four commits on the real
  `ec0a2d28`, with `ESCALATE_AFTER_EPISODE_MS` lowered from 150s to 5s so the escalation fires within
  the first sustained drop burst instead of never (round 2 measured this rig's episodes topping out
  around 52s). `MAX_CYCLES_PER_SESSION` stayed at 1, unchanged, so the real mechanism ran under real
  timing. R0 PASS: 272 tests run, 271 passed, 1 intentionally `@Ignore`d (the production-margin
  pinning test, which the lowered constant deliberately violates). **R1 is the whole round**: the
  cycle fired once (`corruption sustained 5035ms - cycling video focus (1/1)`), the phone answered
  with `Media Sink Stop Request: VIDEO` 53ms later, the regain went out 403ms after the release
  (matching `FOCUS_CYCLE_GAP_MS`), and the phone rebuilt the stream with a **new session**
  (`session=1`, up from `session=0`) 14ms after that — proof the release/regain is a real,
  effective mechanism on this hardware, not a no-op. fps dipped to a low of 41 (from a healthy 50-52)
  before fully recovering to `dropped=0` at 52fps **~29.5s after the release**; the tester's own live
  observation of ~15s of visible corruption sits inside that recovery window. **No #755 regression**:
  fps held 45-52 across the following four minutes and 39 `Throughput` windows, with zero further
  cycles despite 50 total drop triggers and continued drop activity, the cap holding exactly as coded.
  No audio-channel disruption (`Media Start Request AUDIO` fired once, at session start, never again).
  **Net position**: on this hardware, for a single cycle, the release/regain escalation does what it
  was designed to do and does not reproduce the permanent-freeze regression — the core safety question
  behind `KeyframeCycleEscalationPolicy` is now answered, not just argued from first principles.
  Repeated-cycle safety, the transition cost's generalization to #830's lighter real-world drop
  pattern, and cross-hardware reproduction of #755 all remain open, deliberately out of this round's
  scope. Full detail in `video-dropped-frame-keyframe-round3-results.md`.

- **`video-dropped-frame-keyframe-round2-results.md`** — **new candidate `ec0a2d28` reconfirms round
  1 almost number-for-number, and the one new question it adds (is the release/regain escalation
  safe on this hardware) stays open.** R0 PASS: builds clean, 272/272 unit tests (round 1's 264 plus
  the new `KeyframeCycleEscalationPolicyTest` 8/8). R1 PASS: `dropped=0` over a clean 5-minute census,
  identical to round 1. R2 PASS: `dropped=433`, 46 fix-line firings, all attributable to the drop
  path, zero escalations (longest unbroken drop episode this run: 13.7s); the tester directly watched
  visible smearing/wash corruption on screen during this run, the human confirmation the log-only
  round 1 didn't have. R2b PASS, clean positive control. **R3, deliberately extended to ~10.65
  minutes (from the brief's 5) specifically to give the new 150s escalation threshold a chance to
  fire: it never did** — the longest unbroken drop episode this rig produced under sustained
  software-decoder overload was 52.1s, a third of the threshold, so `CYCLE_FOCUS` fired zero times in
  242 triggers. The nudge itself reconfirms round 1 almost exactly (median Δ 27.9s vs round 1's 30.4s,
  natural cadence range 9.0-69.9s vs round 1's 7.5-70.1s, both medians sitting inside the natural
  range rather than beating it), and the #755 fps check stays clean (47-52fps throughout, one 33fps
  startup transient, same shape as round 1) — but since the escalation path never ran, that check only
  re-confirms the nudge's own safety, not the new release/regain path's. **Net position**: the new
  candidate's behavior is confirmed identical to `563ae013` everywhere the nudge is exercised, but its
  actual new mechanism, the bounded focus cycle, remains completely unverified on real hardware — this
  rig's own drop rhythm cannot sustain an episode long enough to trigger it, even under the most
  aggressive lever available. Settling whether the release/regain survives a live stream on real
  hardware (the #755 question this whole design exists to answer safely) needs either a reporter or a
  dedicated lowered-threshold test build, neither in scope here. Full detail, including the
  episode-continuity analysis and the frame-size-distribution note (this capture's gap before the
  keyframe cluster was far less clean than round 1's), is in
  `video-dropped-frame-keyframe-round2-results.md`.

- **`video-dropped-frame-keyframe-round1-results.md`** — **first build of `563ae013` anywhere, and
  the gain-only nudge is confirmed inert on a live stream.** R0 PASS: builds clean, 264/264 unit
  tests, `VideoRecoveryPolicyTest` 6/6. **R1 (Q1, hardware decoding): `dropped=0`** over a clean
  5-minute census on `c2.unisoc.avc.decoder` at 49-54 fps with real moving AA traffic (phone media
  playback via the head unit's own relay, substituted for live navigation which could not be started
  this round) — this rig does not shed frames under hardware decoding at all. **R2 PASS**: forcing
  software decoding (`c2.android.avc.decoder`) produced `dropped=20` and 5 fix-line
  (`VideoDecoder: dropped a reference frame, requesting keyframe`) firings, each paired 1:1 with an
  unsolicited-focus-gain nudge. **R2b PASS**, the positive control: removing the lever returned drops
  and the fix line to 0, confirming the lever caused R2's drops, not the session. **R3 is the
  headline, gated on R2 = PASS**: 177 nudges fired over ~5.5 minutes (`dropped=510` total), every one
  attributable to the fix's own path (0 from `AapVideo: Frame corrupted`); of 156 nudges with a
  measurable follow-up, **median time to the next keyframe-sized frame was 30.391 s** (mean 28.9 s,
  p90 56.9 s), sitting inside the phone's own natural keyframe cadence (six isolated steady-state
  gaps measured at 7.5-70.1 s) rather than clearly shorter than it. Only 8/177 nudges had a sub-1s
  follow-up, and all 8 trace to either the connection-startup keyframe burst or ordinary proximity to
  the natural cadence (nudges fire roughly every 1.5 s throughout, so some nudge is always within a
  second of any keyframe by sheer density). **The #755 fear did not materialize**: rendered fps held
  at 49-51 in all 65 throughput windows regardless of drop or nudge activity. Net position: `563ae013`
  is safe (no regression, `Frame larger than the codec input buffer:` never fired) but the mechanism
  it relies on does not work on this hardware — the fix needs to become a request-until-answered
  design rather than a tuning pass on the gain-only nudge. Full detail, including the per-nudge
  (T0, Δ) table's construction and the frame-assembly recipe's one documented log-format quirk
  (continuation fragments log as `RECV: VIDEO Unknown (N)`, not `Media Data`), is in
  `video-dropped-frame-keyframe-round1-results.md`.

- **`link-stall-periodic-scan-round2-results.md`** — **the band confound closed, and the answer is
  unchanged: still nothing reproduces on this rig.** R0 PASS (builds clean, 244/244 unit tests).
  R1 PASS: `Freq: 2462 MHz (2.4GHz, channel 11)`, `NegotiatedResolution: 1280x720`, **0 stalls over
  428.4 s**, 100.1% audio — a genuine 2.4 GHz control this time, not round 1's 5 GHz mismatch.
  **R2 FAIL**: 18 phone scans, this time confirmed on the phone's own log (18/18
  `WifiService: startScan uid=2000`), 0 change in the profile. **R3 FAIL, with the frequency round 1
  couldn't get**: the phone's SoftAP measured at **2462 MHz — the identical channel** as our own P2P
  group, held idle for 6m13s, 0 stalls throughout. **R4 (bonus, both levers combined) FAIL**: 0
  stalls across a 1220 s window with the hotspot on and the scan loop running inside it
  simultaneously. R5 freebie PASS (0 `Discovery active`/`Discovery failed`, re-confirmed on this
  round's own session). One session served R1 through R4 with zero reconnects across 25+ minutes.
  Between rounds 1 and 2, band mismatch, phone-scan interference and same-channel AP coexistence are
  all ruled out as reproducible mechanisms on this hardware, singly and combined, on both bands —
  only the head unit's own broken `WifiScanner` (confirmed twice, unrelated to the reporter's issue)
  remains untested. Settling the reporter's report further needs a capture on his own hardware.

- **`link-stall-periodic-scan-round1-results.md`** — **neither run the round exists for reproduced
  the reporter's waveform, and this rig's own baseline is clean.** R0 PASS (phone's `start-scan`
  lever works cleanly; the head unit's own is accepted but inert on this device, by both the direct
  command and its Settings-screen fallback — `WifiScanRequestProxy: Failed to retrieve
  wifiscanner`, confirmed twice). R1 PASS: **0 stalls > 1.2 s over 432.1 s**, 100.0% real-time audio
  delivery — this rig is a valid control. R2 INCONCLUSIVE per the brief's own stop condition (no
  working head-unit lever). **R3 and R4 FAIL, the headline**: 18 phone-initiated scans (R3) and
  5m39s of the phone hosting its own idle SoftAP while joined to our P2P group (R4) both left the
  RECV gap profile identical to R1's baseline — 0 stalls, 100.0% audio delivery, in both. R5 PASS:
  zero `Discovery active`/`Discovery failed`, zero coexistence line, station WiFi disconnected
  throughout, matching the reporter's own unit. Two brief-level defects found and corrected
  mid-round: `log-level=1` per the brief's own §3 table produces zero `RECV:` lines (the real guard
  is `AppLog.LOG_VERBOSE`, needing `log-level=0`), and a force-stopped app's broadcast receivers do
  not fire until the app is explicitly relaunched, so `AutoStartReceiver` cannot be relied on to
  self-trigger. Per the brief's own framing this is a fact about this Android-14 rig, not a
  refutation of the diagnosis; settling it further needs the reporter's own hardware.

- **`video-black-after-background-round8-results.md`** — **the tuning round, and it closes the
  investigation for good.** R0 gate PASS (261/261 unit tests, `WarmRelaunchKeyframePolicyTest` 9/9,
  up from round 7's 8). Candidate `fix/warm-relaunch-keyframe` @ `a304bf14`, no baseline build needed
  (round 7's own figures are the comparison). R1 (GLES) and R2 (TEXTURE): every one of eight real
  cycles now recovers in **1.9-2.1 s**, down from round 7's flat 3.0-3.2 s and well inside the 2.5 s
  target — the escalation window measured **850-859 ms** on-device across all eight, exactly matching
  the 850 ms constant `2ccfa641` sets (round 7's own build showed 2000-2001 ms), and release→retake
  held at the unchanged 401-403 ms. Retake→first-frame gaps (189-301 ms) rule out the escalation firing
  on a surface that was already about to render — round 7's own concern, checked and not found. R3
  (SURFACE, regression guard) PASS with genuinely **zero** `cycling video focus` occurrences across all
  four cycles (0.6-0.8 s each, at or better than round 5/7's historical range) — and the
  `decoder_start_failed: Surface not valid` race round 7's R3 traced fired *twice* this round (more
  than round 7's once) but `a304bf14` caught both cleanly: `Decoder start aborted: the surface went
  away mid-configure. Waiting for a new one.` landed 3-4 ms after each `Failed to start decoder`, zero
  `Decoder restart requested: decoder_start_failed` anywhere, and — because the abort happens before
  the phone is ever told anything — no unsolicited extra `Media Start Request VIDEO` went out either
  time, so both race-affected cycles recovered in their own normal 0.6-0.7 s rather than round 7's
  3.2 s outlier. Every logcat capture process was killed by its own pid immediately after its run
  finished this time (round 7's own recorded process-hygiene lapse, fixed), confirmed via `ps aux`
  after every kill and again at the end. Closes the video-black-after-background investigation; the PR
  to upstream is unblocked.

- **`video-black-after-background-round7-results.md`** — **the fix works, confirmed by a clean
  same-day A/B.** R0 gate PASS (260/260 unit tests, new `WarmRelaunchKeyframePolicyTest` 8/8).
  Candidate `fix/warm-relaunch-keyframe` @ `eb4bc8e7`: R1 (GLES) and R2 (TEXTURE), 4 cycles each,
  every single cycle recovered in **3.0-3.2 s** (down from round 5's 6.8-116.4 s / 5.4-52.5 s), all
  four fired the new focus-cycle escalation and were followed by `Media Start Request VIDEO` every
  time, zero `Forcing restart (`, zero forbidden lines. R4, the baseline (`1192daa5`, same rig, same
  day) on the identical four GLES holds: **30.8 s, 74.8 s, one cycle that never rendered at all before
  being superseded 133 s in**, and 49.3 s — the exact contrast that makes the fix's effect
  unambiguous. R3 (SURFACE, regression guard): three of four cycles held at 0.68-0.80 s with zero
  escalations as expected; the fourth hit the new mechanism once, traced precisely to a pre-existing
  `decoder_start_failed: Surface not valid` race already present in the baseline and confirmed absent
  from round 5's own SURFACE capture — reported as a finding per the brief's own instruction, not
  scored as a regression, since the escalation recovered from it cleanly rather than leaving the
  session stuck the way GLES/TEXTURE used to. R5 (5× rapid GLES cover/3 s hold/return): only 2 of 5
  covers ever stuck long enough to claim a surface, and both survivors needed and got the escalation,
  recovering in ~2.5 s each with zero forbidden lines. Neither of the brief's two "most likely to be
  wrong" concerns materialized: the 400 ms release→retake gap never left a `Media Start Request VIDEO`
  unanswered anywhere across 14 real cycles, and the 2 s window never fired on a surface that would
  have rendered on its own. One process-hygiene lapse recorded precisely in Setup notes (logcat
  processes from earlier runs weren't killed before later runs started, so several capture files grew
  well past their own round's window) with no effect on any figure in the report — every number was
  either captured before the contamination could exist or re-verified filtered to its own PID. Closes
  the video-black-after-background investigation; no further hardware round needed.

- **`video-black-after-background-round6-results.md`** — **post-hoc analysis of round 5's own
  captures, no device time, and it settles which layer the fix belongs in.** Splitting each of the 12
  A1-A3 returns into three legs (A: return→new surface, B: new surface→codec configure, C: codec
  configure→first frame) and reproducing round 5's own totals to within a few ms confirms leg C is
  98.9% of GLES's total return time and 98.0% of TEXTURE's, against 8.3% on SURFACE — leg A never
  exceeds 507 ms on any backend in any of the 12 cycles, directly refuting "the relaunch itself is
  slow on GLES/TEXTURE" as the competing explanation. `Media Start Request VIDEO: session=` divides
  the backends exactly as predicted: 4/4 after SURFACE returns (always 211-291 ms before `First frame
  rendered`), 0/10 after every GLES/TEXTURE/rapid return measured, each of those captures showing the
  line exactly once — the one-time initial session setup, not a stray count. On the keyframe-request
  question: 13-35 gain-only `Requesting Keyframe (Unsolicited Focus)` lines fire per slow cycle, one
  roughly every 1.5 s, and the picture's arrival tracks the ~10 s codec-restart cadence instead (the
  last `Configuring decoder: ` attempt in every slow cycle lands 0.4-4.8 s before the picture, tighter
  than the restart-to-restart gap, while individual keyframe requests before it go unanswered at the
  same firing rate) — the gain-only request is established as ineffective on this phone, and the fix
  belongs in the video-focus path, not the activity lifecycle. New reusable artifact:
  `hur-wifi-test-scripts/round6-video-black/legs.sh` + `legs_analyze.py`, which reduce a multi-hundred-
  MB capture to per-cycle leg timings in well under a second.

- **`video-black-after-background-round5-results.md`** — **the user route PASSes on all three
  backends, and the round's real find is a new rig fact rather than a defect.** R1 (GLES) and R2
  (TEXTURE), 4 cycles each (5/45/180/5s holds): zero forbidden lines, cover-by-real-app never tears
  the surface down on either backend (extends round 1's Home-press finding), every cycle recovered
  with the session alive at run end — R1's one 5s-hold cycle took 116.4s to recover (the round's
  only per-cycle budget miss) via the exact `(4/4)`-restart-then-reset-then-recover path round 4
  already confirmed non-escalating. R3 (SURFACE, first dedicated coverage ever) is the headline:
  cover genuinely tears the surface down every cycle (`onSurfaceDestroyed` → two idempotent
  `Decoder stopped: surfaceDestroyed` safety-net stops, confirmed harmless → `Media Sink Stop
  Request: VIDEO`, exactly the legitimate-background path the fix claims to leave alone), and
  recovery on return was **under 1 second every cycle** — faster than GLES/TEXTURE precisely
  because it gets a clean rebuild instead of outlasting an idle-stall watchdog. R4 (5× rapid
  GLES cover/3s-hold/return, back-to-back) reproduced round 4's own "fresh surface supersedes a
  still-recovering codec" deviation case through the real user route and it did not escalate
  either. Zero self-foreground events, zero `Media Sink Stop Request: VIDEO` after any return, zero
  discard-rule hits, across all 17 cycles. **The actual surprise**: `monkey -c
  android.intent.category.LAUNCHER` takes an almost perfectly constant ~26.1s (26.10-26.11s,
  17/17 measurements) from process start to actually injecting the event on this rig, most likely
  because its own wait-for-idle heuristic never resolves early against this unit's permanently
  busy logcat — every latency number in the results file is computed from the device-log
  injection timestamp, not the host-side command-issue time, for exactly that reason. Closes the
  video-black-after-background investigation; nothing further pending on this rig for it.

- **`video-black-after-background-round4-results.md`** — **all three cascade-fix commits confirmed;
  round 3's finding is closed.** R1 (GLES, the point of the round) survived 10 relaunch cycles with
  zero codec flips and zero latches, including four separate cycles that hit the exact 4/4-restart
  threshold that broke round 3's build — `Forcing restart (` per cycle: 2, 3, 4, 4, 4, 0, 1, 2, 3, 4.
  R2 (TEXTURE, 5 cycles) and R3 (Home-press background, 3/30/90s holds) both PASS clean, zero
  forbidden lines, including R2's confirmation that three *consecutive* interrupted-recovery cycles
  in a row still never escalated. R4 (cold-start regression guard) PASS, zero startup stalls in three
  fresh sessions, 51-53ms `Configuring decoder:`→`First frame rendered` gaps. R5 (latch-recovery
  backstop) UNTESTABLE as expected — nothing latched anywhere to exercise it. R6 (idle hygiene)
  PASS, zero `restart suppressed (` over a clean 2-minute window. Across R1-R3 combined,
  `AapProjectionActivity.onDestroy` fired 18 times with a clean recreate every single time — the
  round's real weight of evidence. Two host-side false alarms during the round, both resolved by
  cross-referencing rather than reported as findings: the cycle-runner scripts' own real-time
  frame-detection is unreliable on this rig's flooded logcat stream (verdicts came from post-hoc
  device-timestamp analysis instead, not the runners' own wait/soak calls), and R3's apparent new
  teardown-during-backgrounding turned out to be device/host clock skew misreading the same
  return-trigger mechanism R1/R2 already exercise, not a new defect. Full detail in the results file.

- **`video-black-after-background-round3-results.md`** — **the fix in `fix/822-stale-surface-callback`
  works.** Every run that actually exercised the stale-surface ownership gate (R1 clean, R2's 8× rapid
  trigger, the one clean R3-SURFACE run) recovered and stayed up; R5 confirms the reconnecting watchdog
  and its throttle; R7 confirms the misleading `restart suppressed` line is gone. **New finding, more
  serious and pre-existing (not part of this branch):** the video decoder can permanently give up for
  the rest of a session with no way to recover — not even via a legitimate relaunch — root-caused to
  two lines in `VideoDecoder.kt` (`SYNC_STALL_THRESHOLD_MS` too tight for this SoC's post-relaunch
  codec warm-up, and `decoderPermanentlyFailed` never resets because `setSurface()` skips `stop()` once
  the codec is already released). Confirmed via a same-conditions control test **not to be
  GLES-specific** (SurfaceView failed identically once triggered) and **not to require audio** (one
  occurrence reproduced with `enable-audio-sink=false`). This blocked clean signal on R3-GLES, R4, and
  R6's background/foreground half — see the results file and the Outbound note above before writing a
  round 4 brief.

- **`zlink-wpp-channel-results.md`** — PC-only, static analysis, no head unit or phone touched.
  **The working hypothesis (message `0x105` / `libzbt_rfcomm_data_send` carries Android Auto's
  Bluetooth bytes) is not confirmed, and the evidence found points away from it.** `libzjL10001.so`
  neither links nor gives any sign of `dlsym`-ing a `libzbt_*` symbol from `libzbt-main.so` — no
  `NEEDED` entry, no undefined import, no dlsym'd symbol string. More decisively: the full Android
  Auto Wi-Fi handshake protocol (`zj.AA.WifiVersionRequest`, `WifiStartRequest`, `WifiInfoRespond`,
  and a complete AA protobuf reimplementation alongside them) is defined and packed entirely inside
  `libzjL10001.so` itself, while `libzbt-main.so`'s own ~90 exported `libzbt_*` functions all belong
  to a disjoint `zj.zbt.*` protobuf family — HID touch/screen, BLE, CarLink, HiCar service
  registration — with no AA Wi-Fi message anywhere in it. `libzbt_core.so`'s 12 JNI entry points are
  the same story from the Java side: all HiCar/BLE/phone-link shaped, none Wi-Fi- or AA-shaped. The
  better-supported (but still disassembly-unconfirmed) candidate is a separate local pathway,
  `hu_bt_data_send()` over a Unix domain socket at `/dev/socket/zj_bt_socket`. Analysis was redone in
  full against `com.zjinnova.zlink_600106_20260806_jg` after the reporter identified it mid-round as
  their actual build (`libzjL10001.so` differs by hash from the `_600102` build checked first; the
  other three libraries are identical across every extraction and match checksums already held,
  confirming these are the right files). No disassembly ran — `xref_gocsdk.py` and `capstone` are
  both absent from this machine — so the actual call target of `send_WifiVersionRequest` is still
  not directly observed; that's the concrete next step if this is chased further, ahead of shipping
  the transport on the `0x105` assumption. Also recorded: the brief's own `lib\w+\.so` regex cannot
  match hyphenated filenames like `libzbt-main.so` (false negative, not a real absence — worth fixing
  in the brief), and §7 (Java decompile) can't be done from either extraction: `_600106_jg` has no
  APK at all, and `_600102`'s APK is shielded by a commercial packer so jadx only recovers a stub
  loader, not `ZBTService`.

  **Same-day addendum, complicates the above rather than closing it.** Went looking for
  `libzjL10001.so` in the QF001 firmware download itself (`/home/oscar/Downloads/update/`) and found
  it — `/system/app/zlink5/lib/arm/`, a third distinct build, bundled with `zlink6-qianfeng-…_jg.apk`.
  `vendor.new.dat.br`/`product.new.dat.br`/`socko.new.dat.br` were also reconstructed and are clean.
  The boot script `zlink5.sh` sets `LD_LIBRARY_PATH=/data/data/com.zjinnova.zlink/lib` before
  launching `z-link -c qianfeng -ll`, confirming `z-link` actually loads the **installed app's own
  copy** at runtime, not this system-baked one — so the two live-pulled copies this round already
  analysed remain the relevant ones, and `-c qianfeng` confirms which of the four internal OEM
  platforms this unit runs. More importantly, `gocsdk_zj` (and its sibling `gocsdk_lt`) turned out to
  be sitting in the same firmware, so it got checked directly instead of only reasoned about: it
  `dlsym`s every `libzbt-main.so` export and its own strings directly describe managing live AA/
  CarPlay RFCOMM connections (`"wireless android auto rfcomm connectted success"`,
  `aa_connected_addr`, session/port tracking) — so `libzbt_rfcomm_data_send` **is** real, used-for-AA
  infrastructure, just called by `gocsdk_zj`, not by `libzjL10001.so` as this report assumed. But
  `gocsdk_zj` binds **Unix domain sockets** (`/dev/socket/goc_rfcom`, `/dev/socket/goc_spp`), not a
  TCP socket, has zero protobuf-c message descriptors of its own, and `"3152"` appears nowhere across
  all 444 binaries pulled from this firmware's `/system/bin` — a real conflict with the specific
  `0x105`/TCP-3152/protobuf-framing claim, not just an absence of support for it. Neither side
  references the other by name anywhere in the firmware (no `goc_rfcom` in `libzjL10001.so`/`z-link`,
  no `zj_bt_socket` in `gocsdk_zj`/`gocsdk_lt`), so the actual client of `gocsdk_zj`'s sockets is
  still unidentified — static analysis of what's in hand is now exhausted. Full addendum, including
  the exact `nm`/`strings` commands and output, is in `zlink-wpp-channel-results.md`.

- **`video-black-after-background-round2-results.md`**, diagnostic round, still no candidate
  branch — this round's job was finding a reproduction, not fixing one, and it did. **R1 PASS,
  this is the headline of the whole investigation**: of four backgrounding methods, launching the
  app's own `MainActivity` while a session is live (**M-win**) opens the gate
  (`Decoder stopped: surfaceDestroyed`) that round 1 never reached; the other three (an opaque
  Settings app, `always_finish_activities=1`, plain Home) do not. The reporter's exact symptom
  reproduced live and was **visually confirmed by the user watching the rig** — black screen,
  stuck for 4+ minutes, stall watchdog firing every ~10s and reporting
  `restart suppressed (0/4 used, 8000ms cooldown)` on every tick without ever actually restarting.
  **The reporter's own stated workaround (force-stop + relaunch) fixed it**, confirmed twice.
  **R2 vs R3 (the A/B) landed on a third outcome the brief didn't anticipate**: both `v.3.2.4` and
  `v.3.2.3` fail under the identical trigger, but not identically — 3.2.4 fails cleanly (a
  well-logged stop/reconfigure/stop ending in the stuck watchdog), while 3.2.3 **hangs the app's
  own main thread outright** (its log stops mid-line and never resumes, though the OS and the
  native codec layer both stay healthy — confirmed via the framework logcat capture, zero
  `ACodec`/`OMX` errors). This points the root cause at `MainActivity`'s unsafe self-relaunch and
  a stale `onSurfaceDestroyed` callback, both of which **predate `9f98afd1`** — the commit may
  change how the failure looks rather than whether it happens. **R4 (GLES) failed identically to
  R2/TEXTURE** under M-win, the opposite of round 1's Home-press finding — confirming round 1's
  GLES result was an artifact of the method, not a property of the view mode. **R5 PASS**: no
  `Falling back to ` — the codec-flip path never runs because the restart it lives inside is what's
  perpetually suppressed. **R6**: never self-recovers; force-stop/relaunch does, every time.
  Audio could not be confirmed active this round despite three attempts at the documented recipe
  (rig-side A2DP unpredictability, not a regression) — every run is video-only. Next step is a
  code fix in `MainActivity.onResume()`'s "Active session detected" branch and
  `AapProjectionActivity`'s surface-callback ownership handling, not another hardware round.

- **`video-black-after-background-round1-results.md`**, diagnostic round, no candidate branch (none
  written yet — this round decided whether one is needed). **R0 PASS**: 244/244 unit tests,
  `DecoderStopPolicyTest` 6/6, both `v.3.2.4`/`v.3.2.3` APKs built from source with different md5s.
  **R1/R2/R4/R5 all PASS, but not the way the brief expected**: across 12 scripted Home-press cycles
  (holds from 3s to 120s, both TEXTURE and GLES, both builds, one with `video-codec=H.264` forced),
  `Decoder stopped: surfaceDestroyed` never fired once. **R3, the point of the round, is a wash**: R2
  and R3 are identical (both clean, both by continuity), so `9f98afd1`'s threading change cannot be
  isolated — the code path it touches never ran. **R4 (GLES) inverted the brief's own prediction**:
  expected to be the worst run (decoder silently feeding a dead `SurfaceTexture`), it instead held
  steady at ~50fps through a full 120s hold with zero interruption. **R6 UNTESTABLE** by its own gate
  (needs a black screen to probe against; none occurred anywhere in the round). **Headline**: this
  rig's projection surface is simply never torn down by a plain `KEYCODE_HOME` press in this Android
  14 / UNISOC MT50 environment, so the round could not exercise M1/M2/M3 at all — a gap in what this
  method can reproduce here, not a clean bill of health for the three defects the brief documented in
  the source (all still present, still unverified). One unplanned interruption mid-round: the head
  unit lost power (`ro.boot.bootreason=shutdown,,charging`) during R2's third cycle; the affected data
  was discarded and the cycle re-run cleanly after the user connected an external battery. Results
  file has a concrete suggestion for round 2 if this is worth chasing further: swap the reproduction
  trigger from Home to a task-swipe or forced kill, since Home alone doesn't touch the surface here.

- **`media-key-routing-round1-results.md`**, answered, candidate
  `fix/803-media-key-double-skip` @ `f9b1ca73` on `fork`. **R0 PASS**: 244/244 unit tests,
  `KeyDebouncePolicyTest` 13/13, `MediaKeyRoutingPolicyTest` 6/6. **R1** (no verdict, rig capability):
  A2DP sink link is genuinely intermittent (down then up on its own, mid-round, matching §7a), this
  unit does publish its own `BluetoothMediaBrowserService`, `cmd media_session dispatch next` exists,
  and the Microntek broadcast lands and produces the expected double delivery. **R4/R5 PASS, this is
  the headline**: with the A2DP link confirmed up, AUTO held all three media keys back
  (`Bluetooth media link state for key routing: true`, zero forwards); with the link confirmed down,
  all three forwarded cleanly (`false`, 6/6 forwarded). The probe never read `null` on this hardware,
  so AUTO is a live, working mode here, not decorative. **R2/R3 PASS**: ALWAYS forwards everything,
  NEVER holds everything back, no Bluetooth state needed for NEVER. **R6 PASS, 18/18**: non-media keys
  (19/20/21/22/66/4) forwarded in all three modes, zero suppressed anywhere, including the AUTO trial
  with the link up. **R7 PASS functionally** (de-dup rewrite: one physical press produces exactly one
  forwarded key despite two delivery paths, a stuck key self-clears after 2s and lets the next press
  through, a normal 1s hold isn't mistaken for stuck) **but two exact log strings differ from the
  brief's predictions** (drop reason reads "the key is already held down" not "duplicate <N>ms...",
  and R7b's raw press/release counts are 4-vs-3 rather than equal, though zero orphan releases appear
  anywhere, which is the run's own literal criterion), reported precisely rather than force-fit.
  **R8 PASS**: two presses ~100ms apart both go through now (identity-based dedup, not the old 600ms
  window). **R11 PASS, all three checks**: absent key reads ALWAYS, persists across force-stop,
  out-of-range value falls back to ALWAYS with no crash. **R9 (optional, run) and R10 both surfaced
  real gaps, neither a finding against the branch**: R9's exact single-press repro did not reproduce
  the historical `DOWN, UP, UP` defect on baseline (`main` @ `64f07228`, rebuilt and confirmed by
  source SHA), so that fix is confirmed by code reading and the reporter's own logs rather than
  independently demonstrated here. R10 could not demonstrate the actual two-consumer double-skip:
  this rig's only forced-fan-out method (`cmd media_session dispatch next`) routes to OHU's own local
  session rather than the phone's `BluetoothMediaBrowserService`, so every mode measured 1 track skip
  instead of the predicted 2/1/1, key routing itself was independently confirmed correct in every
  mode via the log regardless. **Net position**: every run that could actually be exercised on this
  rig and this injection method passed; the two gaps are both about what this specific rig can
  demonstrate, not about the branch.

- **`discovery-socket-leak-round7-results.md`** — **the fourth commit's first compile and first run
  anywhere, and it holds clean on every measure.** R0 gate PASS: **264/264** (round 6's 245 plus the
  rebase's `KeyDebouncePolicyTest` 13 and `MediaKeyRoutingPolicyTest` 6), APK md5
  `e18f493694a38b461f361e87d7cc3d8e`. **R21 (link-loss dormancy, the point of the round) PASS on both
  passes**: teardown closes the session in 201-240 ms, `leaving discovery down until a network comes
  back` fires ~2.1 s later both times, and zero `Starting scan...`/`Scanning subnet:` lines appear in
  either dormancy window — 60 s on pass 1, an unplanned **318.3 s** on pass 2 after `svc wifi enable`
  needed the standard nudge (a known rig quirk, not a defect) — both times self-reconnecting with no
  manual server restart once the network actually returned. **R22a PASS**: the two new "why the loop
  stopped" lines fire exactly where the source says (`Discovery not started` twice from one re-init,
  traced to two independent `startDiscovery()` calls both hitting the busy gate), with zero effect on
  the live session — same activity instance, no second handshake, video uninterrupted. **R22b harvest**:
  `Discovery loop ends — a connection is live` fired naturally in every capture (2/1/1/5/0), `— the
  wireless server is gone` was 0 everywhere as predicted. **R23 PASS, 5/5**, the exact clean 1-and-0
  per-instance shape round 6 measured, confirming the rebase changed nothing observable. **R24 PASS on
  the first attempt** — round 6's own R19 (INCONCLUSIVE, because a force-stop loop can never provoke a
  guard whose field is process-scoped) is now resolved: provoked in-process this time,
  `waiting for an in-flight probe before scanning` fired on attempt 1 of up to 5 budgeted. **R20 PASS,
  zero refused-handovers across all five captures.** Two runs (R22a, R24) needed the WiFi button's own
  intent (`ACTION_START_WIRELESS_SCAN`) sent directly rather than a literal tap — traced to source,
  `HomeFragment`'s click handler is a no-op in both of the exact states these runs need to exercise
  (`commManager.isConnected`, and separately `AapService.scanningState.value`), true on any build
  carrying this code, not a rig-specific workaround. **Net position**: round 6's fix needed no
  re-verification (range-diff/tree-hash carries that verdict, and R23 confirms it empirically); the
  fourth commit, exercised here for the first time anywhere, works exactly as designed.

- **`discovery-socket-leak-round6-results.md`**, answered, candidate
  `fork/fix/773-headunit-server-socket-leak` @ `5f193d30` (eight commits; brief and SHA were revised
  mid-flight from `2246e9a2` after a code review found three holes in commit 6 plus a USB-teardown
  regression, followed correctly). **R0 PASS**: 245/245 unit tests, `DiscoveryModePolicyTest` 5/5,
  `LinkLossTeardownPolicyTest` 7/7. **R15 PASS, this is the headline: 15/15 clean.** Every one of 15
  force-stop/relaunch cycles landed on the brief's exact "Fixed" shape (1 `Starting scan...`, 0
  `Scan interrupted`, checked per-instance not just as a file total), `found` equalled `handedover` at
  15-15, and zero manual server restarts across all 15. **R16 PASS, 10/10**, the reporter's own
  settings and topology, one clean scan per WiFi-button press, zero excess, zero restarts. Needed a
  methodology fix mid-run (the first attempt tapped the same coordinates 10 times without realizing
  the first successful connect moves the app to a different screen, so taps 2-10 landed on nothing;
  re-run with a disconnect between each tap). **R17 PASS**: zero `Starting scan...` in Native AA mode,
  no regression. **R20 PASS, zero everywhere**: no capture this round shows a refused, handed-over
  socket. **R18 UNTESTABLE**: this rig has no USB host/accessory path at all (re-confirmed via
  `dumpsys usb`, not assumed from memory), so the USB-teardown regression the eighth commit targets
  cannot be exercised here. **R19 INCONCLUSIVE after two tries**: the cross-instance guard's own log
  line never fired across 60 combined service-start events (two cadences tried, `sleep 3` and
  `sleep 5`, per the brief's own retry instruction), though the counts stayed clean throughout (no
  regression signal, just an untimed race). **R15b, run for free against the retained round 4/5
  captures, turned up a genuine surprise**: the invariant held in all six retained files, but the only
  two with a valid single-instance boundary to measure (both `wifi-connection-mode=2`/`strategy=3`
  fresh launches) showed a clean 1-and-0, not the predicted broken 2-and-1 those builds should carry.
  Reported as-measured, double-checked against raw context, and not forced to fit the prediction.
  **Net position**: the branch's core fix (reusing the discovery instance) is confirmed clean under
  every run that could actually be exercised on this hardware; two runs (R18, R19) remain open gaps
  in coverage for rig-specific reasons unrelated to the fix, not evidence against it.

- **`discovery-socket-leak-round5-results.md`**, answered, candidate
  `fork/fix/773-headunit-server-socket-leak` @ `646441c4`, unchanged from round 4 (no rebuild, R0
  skipped, md5 confirmed identical). **R10 PASS, and it closes round 4's only open question.**
  `adb reboot` never broadcasts `ACTION_SHUTDOWN` at all (it sets `sys.powerctl` and lets `init`
  reboot directly, bypassing `ShutdownThread` entirely), so round 4's R8 could not have tested
  anything regardless of care taken; that was a wrong trigger, not a fact about the unit. With the
  corrected trigger (`svc power reboot`), every decisive line fired in order: `WakeDetect: SHUTDOWN`,
  `DEVICE_SHUTDOWN with a live session — closing it now`, byebye sent, no `send failed`, teardown
  finished in 273ms (higher than round 4's WiFi-toggle figures of 181/198ms but still well inside the
  1500ms budget), and the app reconnected on its own after the reboot with **zero manual server
  restarts** the entire round. **R11 not run** (the brief's own gate: only needed if `WakeDetect:
  SHUTDOWN` were absent, and it wasn't). R12 (physical power-cycle) skipped, no ACC-line rig capability
  exists and the brief said not to force it. **Net position on the branch, now complete across three
  rounds**: R7 (WiFi toggle), R9 (Native AA correctly excluded), and R10 (framework reboot) all PASS.
  The only remaining gap is the WiFi-rejoin-without-a-FIN mechanism from round 3's R6, which this
  branch never claimed to fix and remains open by design.

- **`discovery-socket-leak-round4-results.md`**, answered, candidate
  `fork/fix/773-headunit-server-socket-leak` @ `646441c4` (round 3's `766546a3` plus a fifth commit).
  **R0 PASS**: builds clean, 238/238 unit tests (233 + the new 5), `LinkLossTeardownPolicyTest` 5/5,
  `UnresponsivePeerPolicyTest` still 8/8. **R7 PASS, both trials, this is the headline and directly
  reverses round 3's R6.** `WIFI_STATION_DISABLING with a live session` fired both times, the byebye
  reached the wire cleanly (no `send failed`), teardown finished in 181ms and 198ms (both near the
  expected sub-200ms figure, nowhere near the 1500ms budget), and the app reconnected on its own both
  times with **no manual server restart**, against round 3's 0-for-2. **R8 INCONCLUSIVE on the
  mechanism**: none of the eight decisive lines from the brief's §4 appeared before `adb reboot`; the
  app instead saw a raw `Connection closed (EOF)` with `clean=false`, meaning `ACTION_SHUTDOWN` either
  never arrived on this unit or arrived after the network layer was already gone. The server was not
  left wedged regardless (clean reconnect after boot, no manual restart), but that outcome can't be
  credited to the fifth commit since its shutdown path never ran, the reporter's actual case (a real
  power-off) is still untested rather than confirmed fixed. **R9 PASS**: the exact line the brief
  asked for (`WIFI_STATION_DISABLING, but this session does not ride that link; leaving it alone`)
  fired for the mode-3 session and no teardown-specific lines followed, confirming the new hook is
  correctly scoped. The session did drop anyway (this chipset tears down its own P2P interface as a
  side effect of a station-WiFi toggle, which the brief already flagged as an acceptable outcome) and
  took 5.5 minutes to recover, but that stall traced to the head unit's own WiFi radio not coming back
  on by itself, the same rig quirk hit in R7 and R8, not a candidate regression; a manual
  `svc wifi enable` unblocked it and a fresh Native AA handshake completed within 20s. **Only 1 manual
  server restart used all round**, against a budget of 2-3, itself a sign of how well R7 held.

- **`discovery-socket-leak-round3-results.md`**, answered, candidate
  `fork/fix/773-headunit-server-socket-leak` @ `766546a3` (round 2's three commits plus one). **R0
  PASS**: builds clean, 233/233 unit tests, `UnresponsivePeerPolicyTest` still 8/8. **R3b PASS,
  cleanly, on all three conditions**: the `Slowing discovery` line fired exactly once (round 2 had
  0), `Handshake failed` came in at 10 over 6 minutes (round 2 had 36), and the cadence itself
  widened, measured directly, ~10.7s for the first four cycles then a consistent ~60.7s for the rest.
  **Phone-side socket count: 1**, against round 2's 28. The fix that failed round 2 now works exactly
  as the brief predicted. **R6 FAIL**: 0 of the 2 rejoins attempted held on their own; both deafened
  the server and needed a manual restart (the third was not run, per the brief's own stop condition
  once two independent trials had already confirmed it). This is exactly the WiFi-rejoin-kills-the-
  session-without-a-FIN mechanism the round 3 brief flagged in advance as a known gap this branch
  does not cover, now confirmed twice rather than assumed from round 2's single lucky trial. One
  operational trap for whoever reads this capture cold: a delayed recovery in the log (rejoin #1's
  reconnect landed about 100s after the rejoin, with no obvious marker) looked like spontaneous
  self-healing until the user confirmed both recoveries were manual restarts, there is no log line
  that distinguishes the two, so don't assume recovery-eventually means recovery-on-its-own. Also
  new: an `SSLException: Unable to parse TLS packet header` failure during rejoin #2, a version
  exchange that succeeded followed by immediate TLS corruption, a distinct failure mode from the
  "peer accepted and sent nothing" timeout this whole investigation has otherwise tracked, not
  covered by any run and reported as a standalone observation. **Net position on the branch**: the
  backoff/hammering defect round 2 found is fixed and verified; the WiFi-rejoin deafening gap is real,
  repeatable, and already known to be out of scope, whether to ship with it open is a product
  decision, not something this channel can settle.

- **`discovery-socket-leak-round2-results.md`**, answered, candidate
  `fork/fix/773-headunit-server-socket-leak` @ `bb614110` (see the results file's Setup notes for why
  testing this SHA instead of the tip's later `69fad750` still applies: the diff between them is a
  comment and a log-message split with the exact same trigger condition and, for a `:5277` endpoint,
  the exact same text; every run here targeted `:5277`), baseline `f449557d`. **R0 PASS**: builds
  clean, 233/233 unit tests, the new `UnresponsivePeerPolicyTest` 8/8. **R1 PASS** (the positive
  control that must still fail): 20/20 cycles failed against a held orphan, now with the candidate's
  new "the peer accepted the connection and then sent nothing at all" line, and stayed failed through
  the kill, re-confirming round 1's own finding cheaply. **R2 PASS**: the WiFi-rejoin race that
  deafened the server in round 1 now reconnects cleanly (2/2 successes, 0 `Gateway scan error`,
  session held and kept decoding video for 3+ minutes afterward), though the literal scan-overlap
  timing evidence is inconclusive since both this run and round 1's showed the same ~2.1s gap between
  the stale scan's last line and the fresh scan's start; the verdict rests on the outcome metrics, not
  that timing. **R3 FAIL, this is the one that matters**: the cadence never widened (36
  `Handshake failed` in 6m10s at a steady ~10-11s, against a predicted 6-9), `Slowing discovery to one
  attempt every` never fired once, and the phone accumulated **28 CLOSE_WAIT sockets**, more than
  round 1's ~24 on unfixed baseline, not fewer. The backoff is not reaching the retry loop that
  creates the orphans. **R4 PASS**: a Native AA session formed and projected video, though slower
  than usual (3m08s against the rig's usual 45-90s) from one P2P group re-formation and one failed
  WiFi-association retry, both in code the candidate does not touch; the `CommManager`/`AapTransport`
  path it does touch completed in under 300ms once WiFi was actually up. **R5 PASS**: the manual
  network list (reached via a long-press on the home screen's WiFi button, since no deep link exists
  for it) populated on open and both re-entries, no sign of the `stop()`/`scanJob` regression the
  brief was watching for. **Bottom line**: R2's race fix works; R3's hammering fix does not reach the
  loop it needs to gate, so this branch is not ready to ship as-is. One operational note for whoever
  runs the next round on this defect: a plain connectivity probe to port 5277 (even one that closes
  immediately) is enough to claim the server exactly like the leak itself; don't pre-check the port
  once a restart is meant to have left it clean, let the app's own connection be the first thing that
  touches it.

- **`discovery-socket-leak-round1-results.md`**, answered, against `origin/main` @ `f9b56737`
  (substituted with `fix/bluetooth-handsfree-link-state` @ `f449557d`; `f9b56737` alone does not
  compile, see the results file's Setup notes). **R1 PASS, and the diagnosis is stronger than
  hypothesized**: holding one silent connection to the phone's `:5277` reproduced the reporter's
  exact signature (8/8 cycles). Killing that connection did not recover it within the brief's
  predicted 1-2 cycles; it stayed deaf for 34 cycles / ~5m45s, survived both a hotspot off/on cycle
  and ~4 minutes of just waiting, and only cleared the instant the phone's head unit server was
  manually restarted (the reporter's own workaround). **R2 PASS on mechanism**: forcing a WiFi
  rejoin on the head unit reproduced the same deaf-server condition entirely from the app's own
  code (a stale scan job on a leftover subnet overlapped a fresh one triggered by
  `NetworkCallback.onAvailable`), though none of the brief's three literal log signatures matched
  exactly, see the results file for the caveat. Also newly established: the head unit **can** join a
  phone-hosted hotspot on this rig via `cmd wifi connect-network` (the phone's own
  `cmd wifi start-softap` is blocked by MIUI's `SecurityException` for the unprivileged adb shell, so
  the hotspot itself has to be started by hand). **Implication for a fix**: stopping the socket leak
  prevents new deafenings but cannot fix recovery, since that is entirely the phone-side server's
  behavior; the app's own retry loop also keeps manufacturing fresh orphans once already deaf, so a
  fix needs to stop that too, not just the original two-scan race. No candidate exists yet; a second
  brief once one does would need to restart the phone's head unit server between runs same as this
  one did, not assume a single restart carries the whole round.

- **`video-latency-round1-results.md`** — answered, against what was then
  `fix/755-wireless-video-latency` @ `13408d98`, baseline `origin/main` @ `e900de78`. That branch
  has since been squashed and renamed to `fix/video-catchup-after-link-stall` @ `8cf13352`. Nothing
  executable changed, only comment wording and three log strings, so **these results still apply to
  the current tip** and nothing here needs re-running; the brief says which strings moved. **R1 PASS, the veto that matters most**:
  `skipped=0` in all 56/56 throughput windows over 5 minutes on a healthy link, `rendered` tracking
  `fed` almost exactly throughout. **R2** (baseline reference) came in with a *lower* raw mean
  (45.4fps vs candidate's 49.8fps), but only because its last ~40s hit a genuine source-side slowdown
  (`rendered==fed` moved together, not a decoder fault) that R1's run didn't happen to hit — excluding
  that stretch, the two are statistically identical (49.4 vs 49.8). Candidate is not below baseline;
  the veto does not trigger. **R3a PASS**: `queueCapacity=50` confirmed, zero `Audio queue is full`
  once the channel stabilized. **R3b PASS** as positive control: `queueCapacity=0` confirmed, zero
  drops, channel never needed to restart. **R4 INCONCLUSIVE**: at the max negotiable resolution
  (`_1920x1080`, capped from the requested 4K) under ~2 minutes of continuous scripted touch load
  (`CPU: app 97%/sys 48%`), `skipped` stayed 0 throughout — this rig's decoder is simply fast enough,
  exactly the brief's own anticipated outcome. **R5 PASS**: the coexistence warning fired once per
  group with both frequencies quoted, and — confirmed by reading `logStationCoexistence()`'s own code
  comment, not just by observing timing — the check runs at the `WifiDirectManager`/service level,
  structurally independent of `AapProjectionActivity`'s foreground state. **R6 PASS**: every feed
  thread that finished a session had exactly one matching stop, `Output thread started` tracked `Feed
  thread started` throughout, zero `Input buffer full` lines, and a post-loop reconnect rendered
  cleanly with no corruption. Rig-timing note for whoever writes a future brief targeting this unit:
  Native AA reconnects here run 45-90s, so a 15s connect hold (as this brief specified) mostly
  exercises "cancel mid-handshake" rather than full teardown/rebuild — only 1 of 10 cycles this round
  completed a full reconnect before its disconnect fired, though that didn't affect the verdict since
  nothing leaked either way.

  **The actionable finding, outside this brief's scope**: a self-inflicted audio pause loop, confirmed
  present on `main` too (`AapAudio.kt` has zero diff between the two). `AapService`'s own
  `MediaSessionCompat.Callback.onPause()` fires almost immediately after `AapAudio` grants itself local
  transient audio focus, forwarding a spurious `KEYCODE_MEDIA_PAUSE` back to the phone — reproduced 8
  times in a row on the first channel-open attempt this round before a fresh reconnect broke the cycle.
  Matches what the user watching the rig described live, unprompted: "needed to press play twice or
  more" / "spotify plays and pauses instantly." Worth its own brief if someone wants to chase it.

- **`native-aa-poke-hardening-results.md`** — answered, against `fix/native-aa-poke-hardening` @
  `203d6dc7`. R0 PASS (first compile of this exact combination, 21/21 `BluetoothWakePolicyTest`,
  full suite green). **R2 PASS, R3 PASS, R5 PASS, R6 PASS** — the guard fires when the hands-free
  link is up, Android Auto still connects in ~6.6 s with the poke suppressed when it's not needed,
  the manual poke still works when there's nothing to protect, and it correctly refuses an unpaired
  device with no pairing dialog. R2's literal "launch and watch" recipe does not exercise the guard
  on this rig (the automatic retry loop never gets a live iteration before the phone's own AA
  reconnect wins the race) — R2/R5/R6 were run instead via the app's manual-poke `Intent`
  (`ACTION_NATIVE_AA_POKE`), which reaches `pokeDevice()` directly; worth remembering for future
  rounds. **R4 (genuinely unknown going in): HSP-AG-only also looks destructive** — after a
  successful HSP-AG poke, the classic hands-free link never established across 3 minutes despite a
  healthy, active AA session (and R2/R3 in this same round proved that combination is otherwise
  normal on this rig) — though the causal chain is weaker than round 10's HFP-AG finding since the
  link started down rather than being visibly torn down; both service records now look destructive,
  so the guard (not the setting) is what's actually carrying the fix. **R7 INCONCLUSIVE** — four
  distinct attempts to catch the automatic loop reading bond state while the head unit's own adapter
  was genuinely disabled all failed for different reasons (loop settles after one success and never
  loops again; pre-disabling breaks `NativeAaHandshakeManager.start()`'s own adapter-enabled check).
  Direct code reading confirms the wiring is structurally correct (`adapter.isEnabled` is checked
  before `bondState`, no path for a false `NOT_BONDED`), and the decision itself is unit-tested; only
  the specific live race is unconfirmed. Round run directly by the coordinating session rather than
  a background agent, per the standing instruction adopted after round 10.

- **`audio-focus-round10-results.md`** — answered, against `fix/744-call-audio-hfp` @ `d64d7802`,
  unchanged since round 9 (R0 md5 gate PASS, no rebuild). **R2 is the headline result of the whole
  investigation: FAIL, hypothesis confirmed.** With the poke loop running against the phone's
  HFP-AG record at the app's default settings, the head unit's own stock `HfpClientConnectionService`
  disconnected within 4 ms of the poke's `socket.connect()` succeeding — not a timing correlation,
  the OS's own log shows `HfpClientConnService: Disconnecting from <peer>` immediately after, on the
  very first poke round. All 13 poke rounds across the run succeeded connecting to HFP-AG (0
  failures, unlike round 9 where every attempt failed) — poke *success*, not merely poke *attempts*,
  looks like the operative variable. The link did not self-recover (still down 8 minutes later) and
  needed a manual Bluetooth-adapter cycle to restore. R1 baseline PASS (link up with the app not
  running at all). R4 PASS (no accept — the stub hands-free record was never attached to across any
  capture this round). **R3 (the HSP-AG-only control) is UNTESTABLE on this rig**: its precondition,
  "phone Wi-Fi off prevents any session from forming so the poke retries indefinitely," does not
  hold on this phone — Wi-Fi Direct/P2P formed a full session within 35 seconds across three
  separate attempts despite Wi-Fi being confirmed off each time (adb twice, the phone's own UI
  once). One of those attempts ran unsupervised for ~68 minutes after the background agent running
  it died mid-round, which is also why hardware rounds are no longer delegated to a background
  agent — see the results file's Setup notes. Also recorded: recovering the head unit's hands-free
  link after a bad disconnect isn't reliably a single adapter cycle — the R3 contamination episodes
  needed a second head-unit-side cycle plus a phone-side Bluetooth toggle. Given R2 alone is a clean,
  causally-demonstrated result, this investigation's mechanism is considered closed; whether the
  branch merges is now a code decision.

- **`audio-focus-round9-results.md`** — answered, against `fix/744-call-audio-hfp` @ `d64d7802` /
  `fix/audio-focus-pauses-bt-source` @ `26032e65`. R0 build gate PASS (first compile this branch has
  had anywhere — 219/219 unit tests green, including the new 9/9 `BluetoothWakePolicyTest` and the
  20/20 `PlaybackFocusPolicyTest` regression canary). R2 PASS: `bluetooth-wake-mode=1` kept every
  poke line to HSP-AG only across the whole run (zero HFP-AG mentions), and Android Auto still
  connected via the phone's own reconnect even though the HSP-AG poke itself failed. R1: the phone
  never attached its hands-free link to HUR's stub `0000111e` record across a clean Bluetooth-reconnect
  window (a measurement, not a verdict). R5 PASS: with the key absent, poke order defaults back to
  HFP-AG-then-HSP-AG, and round 8's audio-focus fix still holds (no AVRCP pause across 74 s of
  playback). **R3 ran a real call** (a safe number became available mid-round) and is the round's
  actual headline: **at the defaults, call audio came out of the car, not the phone** — this rig does
  not reproduce issue #744 — and the call was carried entirely by the head unit's own stock Android
  Bluetooth HFP *client* profile, a mechanism neither `bluetooth-wake-mode` nor HUR's stub hands-free
  record has any part in. R4 correctly did not run per the brief's own gate (only runs if R3's audio
  came out of the phone).

  **One correction, which is what round 10 is for.** R3 could not have tested the poke mechanism:
  `NativeHandoffPolicy.shouldPoke` stops the loop the moment a session connects, so the poke was not
  running during the call. What R3 does show — and this is still worth having — is that the *one*
  round of failed HFP-AG attempts R5 fired at 13:08:27, seven minutes before the call, did not break
  the hands-free link. The reporter's loop is unbounded, so one round and dozens are different
  experiments. Round 10 runs the second one.

  Also recorded: the phone and head unit were found completely unpaired at
  the start of the round (a rig state, not a branch defect) and needed manual re-pairing before
  anything could proceed; and the phone's own background AA reconnect beats HUR's poke by several
  seconds whenever it has recently seen the car, so forcing a genuinely poke-dependent connect needs
  the phone's Bluetooth to be off *before* the head unit's listeners come up (recipe in the results
  file, used for R2 and R5). New reusable artifacts left behind: `set_hu_prefs.sh` (multi-key,
  non-relaunching settings writer) in `hur-wifi-test-scripts/`, and
  `code-researchs/hur-wifi-test-scripts-inventory.md` documenting that whole directory.

- **`qf001-firmware-teardown-results.md`** — answered, and the extracted artifacts turned
  out to carry more than the report claimed. **The headline: `libzbt-main-64.so` is a full 64-bit
  build of the vendor library.** It was pulled as a "bonus, not asked" item; it is in fact the answer
  to the whole question. It exports the **identical 90-symbol API** as the 32-bit copy and needs only
  `libc`, `libdl` and `liblog`, so a 64-bit app can `dlopen` it directly. That removes the frame
  header from the critical path entirely — no protocol to reverse, no 32-bit helper, no root.

  The round's own conclusion (disassemble `libzbt-main.so` for the header layout) is therefore **not**
  the next step, and neither is chasing `libzjL10001.so` onto `/vendor`. Both were sound reasoning
  from what the brief asked; the brief was aimed one level lower than it needed to be.

  Also confirmed here and worth keeping: the container was a block-based OTA
  (`system.new.dat.br` + `system.transfer.list`), which §3 did not list — escalating rather than
  improvising was the right call, and the reconstruction method is written up in the results. The
  `z-link` finding corrected a claim of mine directly: it is a 6.9 KB launcher with no socket calls
  in its import table, not the protocol client I described it as.

  No follow-up round is needed on this rig. Remaining verification is a one-line `ls` for the
  reporter, to confirm the 64-bit library ships on real units rather than only in this
  `QF001.20260720` build.

- **`qf001-firmware-teardown-results.md`** — PC-only, against `qf001-firmware-teardown-brief.md`
  (final version, `e6e73959`). The downloaded firmware wasn't one of the brief's three container
  shapes — it was a block-based OTA transfer set (`system.new.dat.br` + `system.transfer.list`),
  escalated per the brief's own rule and reconstructed with `brotli` + a locally-written `sdat2img`
  equivalent (no code fetched), user confirmed before proceeding. Frame header field names are
  settled: `packet_zbt_head` / `npack_zbt_head`, found in `/system/lib/libzbt-main.so`, which
  `/system/bin/gocsdk_zj` `dlopen()`s at runtime rather than linking. The brief's `z-link` lead turned
  out to be a dead end at the binary level: `z-link` is present and unstripped, but its own symbol
  table has no socket/bind/connect/send/recv at all — it's a process launcher that `dlopen()`s a
  *third* library, `libzjL10001.so`, which isn't on `system.img` (not in `/system/lib`, `/system/lib/hw`,
  or `/system/app-lib` — likely on `/vendor`, which came back empty on this partition and is out of
  this brief's scope). So the header's byte layout is still open; `libzbt-main.so` (present, stripped)
  is the clearest next read since it's the one implementation this round could actually get hold of.
  The daemon's init script (service args often carry a port) also isn't reachable from `system.img` —
  traced to `/init.uis7862s_1h10.rc`, which lives in the boot ramdisk or on `/vendor`, both outside
  scope, confirmed rather than assumed. Manifest-level finding from before the brief dropped the APK
  half: independently re-derived and matches exactly — all three of `com.qf.bluetooth`'s services
  exported, none permission-guarded, package declares no permissions of its own. Artifacts
  (`gocsdk_zj`, `z-link`, `libzbt-main.so` + the unrequested-but-present `-64` sibling,
  `privapp-permissions-platform.xml`, `build.prop`) came back through this branch, 2.1 MB total, and
  were **removed from it on 2026-08-12** — vendor firmware content has no business on a public repo.
  They are held on the analysis machine in `~/ohu-fixes-handoff/qf001-artifacts/`; see the teardown
  brief's section 7 for what to send instead next time. The two OEM APKs were read locally but never
  committed, per the brief's final instruction not to spend the round on them.

- **`audio-focus-round8-results.md`** — `fix/audio-focus-pauses-bt-source` @ `26032e65`, no baseline
  (positive controls were pref writes on the candidate). **R5, the round's headline: confirmed.**
  Static Audio Focus's permanent grab produced exactly one AVRCP PAUSE and no repeat over a 3m9s
  capture (well past the 90 s asked for) — a genuine `AUDIOFOCUS_LOSS` (not `LOSS_TRANSIENT`), read
  off the `AudioManager` dispatch directly. R1-R4 all PASS, re-confirming round 6's mechanism holds
  after the rebuild. R8 (new: `playback-focus-mode` added to the settings-backup key lists) PASS,
  round-tripped through a real export/import via the app's own UI. R2/R3 (#802-style repro) came back
  clean but pointed the other way from what the trigger name suggests: Automatic mode never
  reproduces it on this rig (the AA audio channel doesn't even close on phone lock/unlock here),
  Always mode does reproduce a matching symptom but via the same per-channel churn round 6 already
  found — not something lock/unlock specifically triggers.
  R6 (path 3 / protocol-driven grab) INCONCLUSIVE — never got the phone to send a GAIN-type
  `AudioFocusRequestNotification` despite five different triggers tried; the RELEASE-never-gated
  sub-check did PASS. R7 (static grab without Bluetooth) INCONCLUSIVE — Native AA's handshake itself
  requires Bluetooth on this rig, so the "phone BT off before connect" scenario the brief asked for
  can't be constructed at all; the equivalent logic is unit-tested, just not observed live. R9 (UI
  control placement) UNTESTABLE per the brief's own no-scroll rule — the Audio category sits several
  screens below the settings deep-link's landing point. Also notable: the A2DP link would not come up
  through any tried method for the first ~15 minutes of the round, then came up on its own during an
  unrelated reconnect — worse than round 7's "drops and won't return," this was "won't come up at all,
  for a while, for no visible reason." Worth escalating past "known rig quirk" if a future round's
  results depend on the link being reliable.

- **`audio-focus-round7-results.md`** — no brief, run directly against `bcf265ba` (three commits
  over `origin/main` @ `e900de78`) to close out what round 6 flagged as new/untested. Confirmed by
  diff that the audio-relevant commits are byte-identical to round 6's — this round only needed to
  spot-check the new base and actually exercise the new logging commit. Both logging backends
  PASS: the `AppLog.Logger.File` bounded-queue writer held 35k lines / 4.1 MB with zero ordering
  issues and zero drops under sustained VERBOSE load, and `LogExporter`'s new 16 MB cap tripped
  cleanly (stopped within 66 bytes of the threshold, no restart loop, app stayed healthy after).
  The audio-focus acquire/release path and the Always-mode "always acquires" behavior were both
  re-confirmed; the full churn reproduction (A4a) couldn't be re-demonstrated because this rig's
  A2DP link wouldn't come up this round — flagged as test-rig Bluetooth flakiness, not a branch
  issue, since round 6 already proved the mechanism with a working link.

- **`audio-focus-round6-results.md`** — round 6, against `fix/audio-focus-pauses-bt-source` @
  `c0f3ec12`. A0-A4 all PASS, including the A4a positive control reproducing #744's exact churn
  pattern on purpose and A3/A4b confirming the negative (#658) path is intact. A5 UNTESTABLE on
  this rig (probe works cleanly here, so the blind-probe latch condition can't be constructed).
  A6 mostly PASS, but the navigation/assistant channel check is INCONCLUSIVE: turn-by-turn guidance
  was confirmed live and projected (screenshot + `GhostActivity` in the foreground), but
  `AapControlMedia` never logged a `Media Start Request` for `AUDIO1`/`AUDIO2` — only the one-time
  channel setup at connect. Worth a closer look in a future round if that channel matters.
  The rig facts this round turned up are now in `TESTING-TEMPLATE.md` **§7a** — the self-reverting
  head-unit Bluetooth, the phone-side workaround, the absent USB accessory path, media keys not
  opening a fresh channel, and the adb `sh -c` quoting problem. Read §7a before planning a round.

  One correction to the grep behind the Bluetooth finding: the app does contain one
  `BluetoothAdapter.ACTION_REQUEST_ENABLE`, at `main/AutoStartFragment.kt:449`. It sits behind the
  Bluetooth device-picker in AutoStart settings and needs a tap on the system consent dialog, so it
  cannot fire during a run and the conclusion is unchanged — the ~14 s re-enable is not ours.

## History

Rounds 1-5 covered `fix/hotspot-unreadable-config` and are finished — both fixes verified on the
UNISOC MT50 rig, H3 recorded as untestable there, PR body written. Those files were removed from the
tip once the work landed; they are still in this branch's history at `fe57572b` if anything needs
looking up:

```bash
git show fe57572b --stat
git show fe57572b:hotspot-unreadable-config-round5-results.md
git show fe57572b:hotspot-unreadable-config-test-protocol.md   # the source of TESTING-TEMPLATE.md
```

`TESTING-TEMPLATE.md` is the distillation of that protocol: everything in it that was not specific to
the hotspot work, generalised, plus the automation surface it did not cover.
