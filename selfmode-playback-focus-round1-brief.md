# selfmode-playback-focus — round 1 brief

**Type: regression hunt on a fix aimed elsewhere.** The change fixes silence on a factory head unit
(#846). Self Mode is the mode most likely to be hurt by it, and the automatic backstop that protects
every other mode is structurally unreachable there. This round decides whether the change is safe to
ship for Self Mode users, not whether it fixes #846 — that unit is not on the rig.

## 1. Build and baseline

```
git fetch fork
git checkout -B fix/846-audio-focus-auto-trial fork/fix/846-audio-focus-auto-trial
git log --oneline -2      # expect abf9067a, then 967cb41d "next beta"
```

One commit on top of `main`. No history was rewritten; nothing else is stacked on it.

**No baseline APK is needed.** Every control this round needs is a settings change on the candidate
itself: `playback-focus-mode=2` reproduces the old behaviour exactly, because the branch's whole
change is what AUTO does and NEVER is untouched.

**Build gate:** `run_unit_tests.sh`, then `build_hur.sh`. `PlaybackFocusPolicyTest` must report
**20 tests, all green** — the count is unchanged from the standing canary but four of the cases are
new, so a green run at 20 is still the right gate. A red gate is an escalation, not a run.

## 2. What this is and why it exists

Since 3.2.5 the app decides whether to ask Android for system audio focus. Asking can backfire when
the head unit is also the phone's Bluetooth A2DP sink: Android answers our focus grab with an AVRCP
pause aimed at the phone feeding us, so the stream we are trying to play stops. `AUTO` therefore
declined the grab whenever an A2DP link was connected.

That veto is a guess, and it is too wide. Any paired phone has a connected link, so on a factory head
unit that routes its amplifier from the focus request, `AUTO` asked for focus **zero** times and the
unit played nothing at all. Four reporter captures show zero `requestAudioFocus` against three
`abandonAudioFocus`, on a vendor Android that logs both.

The change: `AUTO` now takes focus and stops only once the self-defeating detector has actually seen
the phone cut its own playback twice within 5 s of a grab. That verdict is written to settings, so a
unit that behaves that way pays for the trial once rather than at every connect. Static mode keeps
the veto, because it grabs once outside any channel's lifetime and has nothing to observe.

**Why Self Mode is the exposure.** In Self Mode the phone is both the head unit and the source, so a
focus grab lands on the same device that is playing. That is the harm the veto was written for, at
its most direct. And the detector cannot save it there: `countsAsSelfDefeating` only counts the MEDIA
channel, and Self Mode never announces MEDIA.

## 3. What is different about this round

- **Self Mode announces only the SYSTEM audio channel.** `ServiceDiscoveryResponse.kt:162-188` puts
  `ID_AU1` (SPEECH) and `ID_AUD` (MEDIA) behind `!isSelfMode`; `ID_AU2` (SYSTEM) is announced
  unconditionally. So in Self Mode the only ways into the new code are an AUDIO2 channel opening and
  the phone asking for focus over the protocol. **R2 exists to establish which of those is reachable
  at all**, and R1 is INCONCLUSIVE rather than PASS if neither fires.
- **The latch cannot arm in Self Mode, by construction.** `countsAsSelfDefeating(isMediaChannel =
  channel == ID_AUD, …)` and `ID_AUD` is never announced here. This is an expected finding to
  confirm, not a defect to chase: report whether `playback-focus-self-defeating` is still absent or
  `false` in `settings.xml` at the end of the round. If the harm in R1 is real, the shipping answer
  is a code change, not a retest.
- **The rig is D-POCO** (Redmi M2007J20CG / POCO X3 NFC, serial `4f4027e9`), not the MT50. §7a is
  written for the MT50 and most of it does not apply.
- **Android Auto's dev head-unit server on `127.0.0.1:5277` is down on this unit** and does not come
  back on its own. Toggle **Start head unit server** by hand in AA Developer settings *before the
  install step*, and confirm something is listening before treating any Self Mode connect failure as
  a finding. A failed connect with nothing on that port is rig state.
- **Do not force-stop Gearhead** at any point. It is what dropped that server, and it also clears
  `external_keyboard_last_open_state`, which a different thread depends on.
- **This unit has a speaker.** Unlike the MT50, audible checks are possible here and R1 wants one:
  whether the phone's own music actually stops is the question, and the log line only tells you
  whether we asked.

## 4. Settings keys

Written to `shared_prefs/settings.xml` with the app stopped, per the template.

```xml
<int name="log-level" value="0" />
<boolean name="auto-start-self-mode" value="true" />
<boolean name="enable-audio-sink" value="true" />
<boolean name="static-audio-focus" value="false" />
<int name="playback-focus-mode" value="0" />
```

`playback-focus-mode`: `0` AUTO (the change), `1` ALWAYS, `2` NEVER (the old AUTO behaviour on this
rig). `static-audio-focus` must be **false** — with it true the whole dynamic path is skipped and
every run in this round is INCONCLUSIVE.

`playback-focus-self-defeating` is the new key. **Do not write it.** Read it back at the end of the
round; a stale `true` from an earlier run would silently disable the thing under test, so if it is
present at the start of a run, delete it with the app stopped and say so.

## 5. The lines that decide every run

Verified with `grep -F` against `abf9067a`.

**The grab, and its absence:**

```
AapAudio: AA audio started (AUDIO2) - acquiring transient system audio focus (mode=AUTO)
AapAudio: AA audio started (AUDIO2) - leaving system audio focus alone (mode=NEVER)
AapAudio: Playback transient focus request result: GRANTED
AapAudio: last AA audio channel stopped - releasing transient system audio focus
```

The decline line's parenthesis is new and names the gate that said no. The four forms are
`mode=<MODE>`, `taking it stops this phone's own playback (mode=AUTO, learned)`, `static audio focus
holds it instead` and `the audio sink is off`. Quote whichever appears verbatim.

**The protocol path — these two are the pair that matters:**

```
Audio Focus Request: GAIN                      <- AapControl, the phone asked; always printed
Audio Focus Request: stream=3, type=1          <- AapAudio, printed only if we honoured it
AapAudio: phone asked for audio focus - leaving system audio focus alone
Sending immediate AudioFocusNotification: STATE_GAIN (always-grant)
```

The second line's presence is the whole instrument for "did the request reach the system". The
always-grant reply is unchanged by this branch and is not evidence either way.

**The latch, which should never appear in Self Mode:**

```
AapAudio: media stopped <N>ms after taking audio focus (<n>/2)      <- debug level
AapAudio: taking system audio focus is stopping the phone's own playback
```

**Reachability, from the session's own setup:**

```
Media Sink Setup Request: <N> on channel AUDIO2
Media Start Request AUDIO2: session=<N>, config_index=<N>
```

**System-side, outside the OPENHU tag:**

```
adb -s 4f4027e9 shell dumpsys audio | sed -n '/Audio Focus stack/,/^$/p'
```

Our package appearing in that stack is the independent confirmation that the grab landed; our own
log line only says we asked.

## 6. Runs

### R1 — Self Mode with the phone's own media playing. **This is the point of the round.**

`playback-focus-mode=0`. Start a local music player on the phone and let it play audibly. Connect
Self Mode, let the session settle, then drive the projection for at least 3 minutes: open the media
app inside Android Auto, start and stop playback twice, and let a navigation prompt fire if one will.

**PASS** requires all three:
- The phone's audio is still playing at the end of the run, and was not interrupted at any point that
  coincides with a grab. Say how you checked — this unit has a speaker.
- If `acquiring transient system audio focus` appears, no audio channel closes within 5 s of it.
- No `taking system audio focus is stopping the phone's own playback`.

**FAIL** is the phone's playback stopping or ducking within a few seconds of a grab. That is the #846
fix reaching a case it should not, and it is the finding this round exists to get. Quote the grab
line, the next audio line, and the gap between them in milliseconds.

**INCONCLUSIVE** if neither `acquiring transient system audio focus` nor `Audio Focus Request:
stream=` appears anywhere in the capture. That means Self Mode never reached the changed code, which
is a real and useful answer — see R2, which is what tells the two apart.

### R2 — reachability: what does Self Mode actually open?

Same capture as R1, no separate run. Report, as counts:
- every `Media Sink Setup Request:` line, with its channel;
- every `Media Start Request` line, with its channel;
- every `Audio Focus Request:` line of both forms, in order, with timestamps.

This is the measurement that makes R1's verdict mean something. A clean R1 on a session that never
opened an audio channel and never received a focus request is a green that proves nothing, and this
run is how that is caught rather than assumed.

### R3 — the positive control: `playback-focus-mode=2`

Repeat R1 with `playback-focus-mode=2` (NEVER), app stopped for the write.

**PASS** requires:
- Wherever R1 printed `acquiring transient system audio focus`, this run prints `leaving system audio
  focus alone (mode=NEVER)` instead.
- Zero `Audio Focus Request: stream=` lines.
- Our package does not appear in the `dumpsys audio` focus stack.

If R1 found harm, this run is what proves the harm is the grab and not something else in the session,
and NEVER is the documented escape for affected users. If R1 was INCONCLUSIVE for lack of any audio
channel, this run will be too, and that pair is itself the answer.

### R4 — steady-state regression guard

Ten minutes of ordinary Self Mode projection on `playback-focus-mode=0`, driving the map, with no
Bluetooth audio device connected to the phone.

**PASS** requires both:
- Throughput **45-60 fps with `dropped=0`** across the whole run, and no disconnects.
- Zero `taking system audio focus is stopping the phone's own playback`.

The throughput is not decoration: a zero count on a session that was not rendering proves nothing,
so the two are reported together or the run is INCONCLUSIVE.

### R5 — the latch's reachability, stated and checked

At the end of the round, with the app stopped, read `playback-focus-self-defeating` back out of
`settings.xml` by the template's §1 method — not by any route invented here, since this rig's
`shared_prefs` ownership has bitten a round before.

**Expected: absent.** Report it either way. Present and `true` after a Self Mode-only round would
mean the MEDIA channel opened after all and §3's reading of the announcement guards is wrong, which
changes the shipping answer and is worth an escalation rather than a quiet note.

## 7. Do not re-run

- Whether Self Mode routes audio through us at all. `self-mode-bt-audio` round 1 settled it: four
  arms, all PASS, no audio `Media Start Request` anywhere. R2 here is a count against this specific
  build, not a re-litigation of that.
- Anything about #846's own unit. It is a Neusoft/Geely API 18 board and is not on the rig; the
  reporter tests that half.
- The keyboard, the call-raise and the view-mode paths. Untouched by this branch.

## 8. Report back

Three numbers decide whether this ships as-is:

1. Did the phone's own playback survive R1, yes or no, and if no, the gap in milliseconds between the
   grab and the interruption.
2. The count of `Audio Focus Request: stream=` lines in R1 versus R3.
3. R4's fps and `dropped=` across the ten minutes.

If R1 and R2 come back saying Self Mode never opens an audio channel and never receives a focus
request, say so plainly — that is a PASS for shipping and it also means Self Mode users are unaffected
by this change in either direction, which is worth knowing before the next audio round is written.
