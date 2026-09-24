# Round 2 brief, Self Mode stands down what a loopback session cannot use

Read `TESTING-TEMPLATE.md` first. Read `self-mode-media-session-round1-results.md` too, all of it,
because this round repeats exactly one of round 1's runs, retires three of them outright, and every
omission below is deliberate.

Round 1 graded one commit and passed it. This round grades the three that followed, which take that
commit's own reasoning and apply it to three more places. The four together are one idea, and round 1
already measured the first quarter of it.

---

## 1. Build

**Candidate:** `fix/929-self-mode-media-session` @ `d5d463d5` on `fork`, three commits on round 1's
`665332d8`.

**Baseline, for the control arms only:** the same branch @ `665332d8`, which is **round 1's
candidate**. The rig already has it as `candidate-665332d8.apk`, md5
`28455e33b31ede223e76b84fc0a016d4`, so nothing needs rebuilding for it. `origin/main` is deliberately
not the baseline this round: against `main` every control arm would measure round 1's change as well
as these three, and round 1 already settled that one.

`build_hur.sh` deletes the previous APK before it builds. Copy the new one out of `apks/` before
anything else runs, or the round 1 APK goes with it.

### R0, build gate

`run_unit_tests.sh`, then `build_hur.sh`.

- Full suite **2178** tests, green. Round 1 read 2168.
- `LocationSourceAnnouncementPolicyTest` **3** and `SelfModeWirelessPausePolicyTest` **4** must exist.
  If either class is missing, the wrong commit is checked out.
- `PlaybackFocusPolicyTest` **23**, up from 20.
- `MediaSessionOwnershipPolicyTest` **2** and `MediaKeyRoutingPolicyTest` **8**, unchanged from round 1.

Record the APK md5 and confirm which is live per §5. **If R0 fails, stop and report.**

---

## 2. What this is

Round 1's commit rests on a single fact: in Self Mode the app announces no media and no speech audio
sink, so it is not in the audio path at all, and a media session it holds can therefore only outrank
the real player sitting on the same device. Three other places in the app were still claiming a role
the local Android Auto already fills. Each is one commit.

**Audio focus, `4dfa66a0`.** Every audio-focus request the phone sent was answered twice: once on the
wire, which is correct and unchanged, and once by asking Android for real system audio focus, which is
not. In Self Mode that second half takes `AUDIOFOCUS_GAIN` with `USAGE_MEDIA` away from the local
player, for a session that carries no music. The automatic guard could never catch it: the
self-defeating detector counts only the media channel, and in Self Mode that channel is never
announced, so it never opens and the latch can never arm. The gate is now on both entry points, so the
protocol path, the playback path on the system-sounds channel, and static mode's two permanent grabs
all stand down together. The always-grant reply on the wire is untouched.

**The location sensor, `233b8f3e`.** This one is not a hardware conflict and it is worth saying so,
because that is the obvious guess: Android multiplexes every `GPS_PROVIDER` client onto one GNSS
session, so a second subscriber costs a callback, not a second fix engine. The problem is staleness.
The app read this device's fix, broadcast it, wrapped it and handed it back to Android Auto on the same
device, which prefers the car's GPS over its own. So Android Auto was moved off a live read onto a copy
a round trip staler, and the resend backstop then re-stamps a fix up to a minute old with the current
time. Self Mode no longer announces the sensor at all.

**The wireless stack, `d5d463d5`.** Starting Self Mode never stood the wireless stack down, and the
existing refusals did not cover it: the USB one asks whether the session is wireless, and a loopback
session is a socket session, so it passes. On a unit that has WiFi among its connection modes, a Self
Mode session therefore ran with a P2P group up and, in Native mode, a poke loop dialling paired phones
every fifteen seconds, for a session that needs no radio at all. The stack is now stood down when a
launch is accepted, held down while Self Mode runs, and given back when it ends, fails or times out.

---

## 3. What is different about this round

**Every grep below runs against the app's own `HUR_Log_*.txt`, not against a logcat capture.** Round 1
established that `log-source=1` routes `AppLog` output to that file *instead of* logcat entirely, so
`adb logcat` shows nothing for the app for the whole round. The file lands under
`/storage/emulated/0/Android/data/<pkg>/files/`. This is not yet in the template's vocabulary, and
neither is `log-capture-enabled`; do not go looking for them there.

**The head unit server precondition check is `/proc/net/tcp6`, not `/proc/net/tcp`.** Round 1's brief
had this wrong and it never matched on D-POCO even with the server up and working:

```bash
adb -s <serial> shell cat /proc/net/tcp6 | grep -i :149D     # 0x149D == 5277
```

**Budget an operator round-trip for the Gearhead wedge.** Round 1 hit it once and it is repeatable:
Android Auto's car service accepts the socket and then never speaks on it, giving
`the peer accepted the connection and then sent nothing at all` and `session state failed
(peer_silent)` on every retry. Only force-stopping Android Auto clears it, and that also kills the
`5277` dev server until the operator re-toggles "Start head unit server" by hand. It is a rig and
Gearhead fault, independent of this branch. Expect it, escalate once, do not spend runs on it.

**Round 1's R1, R2 and R3 are retired and must not be repeated.** R1 established that this rig's
D-POCO does not reproduce the reporter's media-session symptom at all: the local player held the top
session on the baseline build, so there was nothing for a fix to improve. That is a fact about
Gearhead 17.8.163804 being far newer than the reporter's, not about the branch, and re-running it
costs a round and settles nothing.

**The unit split.** Self Mode runs on **D-POCO**, which is both the phone and the head unit there. The
ordinary Native AA runs, and the one deliberate exception in C3, are **D-HU** as head unit with D-POCO
as the phone.

**One thing not to chase in check C.** `WifiDirectManager` also auto-creates its group from a
`WIFI_P2P_STATE_CHANGED` broadcast, independently of the launcher, which would look like the fix
failing. It does not apply here: the stand-down runs the launcher's full stop, which unregisters that
receiver and removes the group before Self Mode's launchers run. If a group does appear during a
candidate Self Mode session, that is a real FAIL and not this path.

---

## 4. Settings keys this round needs

Written into `shared_prefs/settings.xml` with the app stopped, never through the UI. Inventory
`hur-wifi-test-scripts/` first and use whatever writer fits the unit: round 1 used
`set_prefs_runas.sh`, and the template documents `set_hu_prefs.sh` for a rooted multi-key write and
`set_pref.sh` for the run-as path.

| Key | Type | Value |
|---|---|---|
| `log-level` | int | `0` (VERBOSE) |
| `log-source` | int | `1` (APPLOG_FILE) |
| `log-capture-enabled` | boolean | `true` |
| `gps-navigation` | boolean | `true`, every run |
| `wifi-connection-mode` | int | `3` (Native AA), on both units |
| `static-audio-focus` | boolean | `false`, except A2 |

**Check `connection-modes` is absent, or contains `wifi`.** If it names only Self Mode, the wireless
stack already refuses to arm for a different reason and check C measures nothing at all.

**Check `gps-navigation` really is `true`.** It is the default, but the onboarding screen asks which
GPS to use and a previous round may have answered. With it off, B1's decisive line never prints and
the run is INCONCLUSIVE rather than a PASS.

---

## 5. The lines that decide every run

Copied from the candidate at `d5d463d5`. All are `AppLog.i` and survive any log level.

| Meaning | Line |
|---|---|
| focus grab declined, Self Mode | `AapAudio: phone asked for audio focus - leaving system audio focus alone (the player is on this device (Self Mode))` |
| same, on the system-sounds channel | `AapAudio: AA audio started (AUDIO2) - leaving system audio focus alone (the player is on this device (Self Mode))` |
| static mode's permanent grab declined | `AapService: Static Audio Focus - leaving system audio focus alone (mode=..., bluetoothMedia=..., selfMode=true)` |
| **we asked Android for focus** | `Audio focus request result: GRANTED` (or `FAILED`, or the `(legacy)` spelling below API 26) |
| the phone asked us, on the wire | `Audio Focus Request: GAIN` |
| the wire reply, unchanged | `Sending immediate AudioFocusNotification: STATE_GAIN (always-grant)` |
| GPS sensor withheld | `Self Mode is projecting this device to itself, so Android Auto reads this device's location directly and the head unit GPS sensor is not announced - this is not a fault` |
| GPS sensor announced and asked for | `LOCATION sensor requested. Sending current fix immediately.` or `LOCATION sensor requested. No recent GPS fix to prime with.` |
| wireless stood down | `SelfMode: standing the wireless stack down; a loopback session needs no network` |
| wireless given back | `SelfMode: the Self Mode session ended; letting the wireless stack arm again` |
| same, after a failed launch | `SelfMode: the Self Mode launch failed; letting the wireless stack arm again` |
| an automatic bring-up refused | `WifiLauncher: wireless bring-up requested while Self Mode is running.` |

**`Self Mode is projecting this device to itself` is also two different lines.** The other one is the
pre-existing audio-sink skip, which Self Mode has always printed. Grep the GPS one on `head unit GPS
sensor is not announced`, which is unique, never on the shared opening.

**`Audio Focus Request:` is two different lines and they must not be confused.** `Audio Focus Request:
GAIN` is the phone asking us over the protocol. `Audio Focus Request: stream=3, type=1` is us calling
Android. Always grep the one with `stream=` when you mean ours. Note a RELEASE is never gated, so
`Audio Focus Request: stream=` still appears for releases on the candidate; the line that means we
took focus is `Audio focus request result:`, which prints only on a grab.

Standing counts:

```bash
grep -ac 'leaving system audio focus alone'   rN.txt
grep -ac 'Audio focus request result'         rN.txt
grep -ac 'head unit GPS sensor is not announced' rN.txt
grep -ac 'LOCATION sensor requested'          rN.txt
grep -ac 'standing the wireless stack down'   rN.txt
grep -ac 'letting the wireless stack arm again' rN.txt
grep -ac 'createGroup SUCCESS'                rN.txt
grep -ac 'Attempting active poke to device'   rN.txt
```

---

## 6. Runs

Run **A3 and B2 first.** They are the regression guards on an ordinary session, and if either fails
nothing else about these three commits matters.

### A3, an ordinary session still takes focus, candidate

Native AA, D-HU as head unit, D-POCO as the phone, projection in the foreground, a track playing
through Android Auto.

**PASS:** at least one `Audio focus request result:` line, and **zero**
`leaving system audio focus alone`.
**FAIL:** either count moves. This is the run that says the change stayed inside Self Mode.

### B2, an ordinary session still offers its GPS, candidate

Same session as A3, `gps-navigation` = `true`.

**PASS:** at least one `LOCATION sensor requested`, in either of its two spellings. Which spelling
appears depends only on whether the unit has a fix, so **no GPS lock is needed for this run** and
indoors is fine.
**FAIL:** zero. That would mean the sensor was withheld from a session that is not Self Mode.
**INCONCLUSIVE:** the phone never opened the sensor channel at all, which also gives zero. Say which by
reporting whether any other sensor was requested in the same capture.

### A1, Self Mode does not take focus from the local player

Candidate on D-POCO. Start a local player and confirm it is playing **before** launching Self Mode, to
match the ordering a real user has. Let the session run a minute with audio arriving.

**PASS:** at least one `leaving system audio focus alone (the player is on this device (Self Mode))`,
**zero** `Audio focus request result:`, and `Sending immediate AudioFocusNotification` still present.
**FAIL:** any `Audio focus request result:` line, or the `AudioFocusNotification` reply missing. That
reply is deliberately unchanged, and losing it would be a worse regression than the bug this fixes.
**INCONCLUSIVE:** both the decline count and the result count are zero, which means the phone never
asked for focus at all and the run measured nothing. Say so rather than reading it as a PASS.

Report separately, as an observation with no verdict: whether the local player kept playing for the
whole session.

### A1c, the control

Same setup, `665332d8` installed. Confirm the md5 is live first.

**Expected:** at least one `Audio focus request result: GRANTED` and zero decline lines. This is the
positive control, so if it does not appear, A1 proves less than it looks and both should be reported
together. Note that the player pausing audibly is *not* required here: round 1 refuted the
media-session half of this defect on this rig, and audio focus is a different mechanism, so the log is
the instrument and the loudspeaker is not.

Restore the candidate and re-confirm the md5 afterwards.

### A2, static audio focus, candidate

D-POCO, Self Mode, `static-audio-focus` = `true`.

**PASS:** `AapService: Static Audio Focus - leaving system audio focus alone` carrying `selfMode=true`,
and zero `Audio focus request result:`.
**FAIL:** a permanent grab happens anyway. On this path the harm is worse than the dynamic one: the
grab is a permanent loss for the local player, which stops once and never resumes.

Set `static-audio-focus` back to `false` afterwards.

### B1, Self Mode withholds the GPS sensor

Candidate on D-POCO, Self Mode, `gps-navigation` = `true`.

**PASS:** the withheld line present, and **zero** `LOCATION sensor requested`. The withheld line is its
own reachability proof: it prints only when the setting is on and service discovery was reached.
**FAIL:** any `LOCATION sensor requested`, or the withheld line absent while the setting reads `true`.

Report as an observation with no verdict: open a navigation app inside Android Auto and say whether it
shows the right position. That is the question the commit is really betting on, and a log cannot
answer it.

### B1c, the control

`665332d8` on D-POCO, same setup. **Expected:** `LOCATION sensor requested`, in either spelling, and no
withheld line.

### C1, Self Mode runs no wireless stack

Candidate on D-POCO, `wifi-connection-mode` = `3`, `connection-modes` absent or naming wifi. Launch
Self Mode and let the session run two minutes.

**PASS:** `standing the wireless stack down` exactly once, and **zero** `createGroup SUCCESS`, **zero**
`Attempting active poke to device`, **zero** `MATCH! Starting AapService`.
**FAIL:** any of those three counts is non-zero.

**§4's discard rule is inverted for this run and C2 only, deliberately.** Those same lines normally
mean a contaminated capture that must be re-run. Here their absence *is* the measurement, so a capture
with zero of them is the result rather than a clean-run problem. What makes the zero mean something is
C1c below, not the discard rule.

### C1c, the control

`665332d8` on D-POCO, identical settings. **Expected:** at least one `createGroup SUCCESS` during the
Self Mode session, which is the defect. Poke lines are a bonus and depend on D-POCO having a paired
phone; the group is the decisive one. If the control shows no group either, C1 is **INCONCLUSIVE** and
the reason is almost certainly `connection-modes` or `wifi-connection-mode` not being what §4 asks.

### C2, the stack comes back when Self Mode ends

Candidate, straight after C1. End the session with `headunit://exit`.

**PASS:** `the Self Mode session ended; letting the wireless stack arm again`, followed by
`WifiLauncher: Initializing WiFi Mode: NATIVE` and a `createGroup SUCCESS`.
**FAIL:** the re-arm line is absent, or it appears and no mode arms after it. This is the run that says
the fix does not cost the user their wireless for the rest of the process.

### C3, the stack comes back when Self Mode *fails*

**On D-HU, precisely because Self Mode cannot succeed there.** D-HU has no Android Auto head unit
server, so the launchers fail fast and the failure path is reached without touching D-POCO's Gearhead
toggle at all. Set `wifi-connection-mode` = `3`, then start Self Mode on D-HU and wait for it to give
up.

**PASS:** `standing the wireless stack down`, then `the Self Mode launch failed; letting the wireless
stack arm again` (or the `timed out` wording, either counts), then the mode arming.
**FAIL:** the stand-down line with no re-arm line after it. That would mean tapping Self Mode once and
having it fail costs the unit its wireless until the app is restarted, which is worse than the bug
this commit fixes.

This is the highest-value run in the round. It is the one path round 1 could not have exercised.

### C4, the WiFi button still works

Candidate, D-POCO, during a live Self Mode session. Press the WiFi button on the home screen.

**PASS:** the stack arms. Automatic attempts during the same session log
`wireless bring-up requested while Self Mode is running`, and the button's does not.
**INCONCLUSIVE:** the button cannot be reached because the projection is in front. Say so; it is a rig
fact, not a defect.

### D1, round 1's R6, repeated

`d5d463d5` edits the same disconnect branch that round 1's R6b graded, so that run does not carry over
and is repeated verbatim. End a Self Mode session and start another, then bring up an ordinary Native
AA session on the same install.

**PASS:** `media session left to the player on this device (Self Mode)` exactly once per Self Mode
session, not accumulating across the two, and `MediaSession: State updated to PLAYING` returning on the
ordinary session afterwards.
**FAIL:** the count accumulates, or the ordinary session never gets its media session back.

---

## 7. Do not re-run

- **Round 1's R1, R2 and R3.** Retired in §3. This rig does not reproduce the media-session symptom
  and no amount of rig time will change that.
- **Round 1's R4 and R5**, the media-key runs. None of these three commits touches
  `MediaKeyRoutingPolicy`, `CommManager.sendKey` or the projection activity's key dispatch. D1 is the
  only part of round 1 that is repeated, and only because a disconnect path moved underneath it.
- **The policy transition tables.** `PlaybackFocusPolicy`, `LocationSourceAnnouncementPolicy` and
  `SelfModeWirelessPausePolicy` are pure, and R0 runs their tests on the JVM.
- **Video, the hotspot, the decoder, the poke cadence.** Nothing here touches them. A change in any of
  them is a rig variable and belongs under "Anything the brief did not ask about".

Evidence goes in `evidence/self-mode-media-session-round2/`.

---

## 8. Report back

Six answers decide whether this ships:

1. **A3 and B2**, the two regression guards, both counts unchanged from an ordinary session.
2. **A1 against A1c**, whether the candidate declined the grab where the control took it.
3. **A2**, whether static mode's permanent grab stood down.
4. **B1 against B1c**, whether the sensor was withheld where the control announced it, and whether
   navigation still showed the right position.
5. **C1 against C1c**, whether a group formed on the control and not on the candidate.
6. **C2 and C3**, whether the wireless stack came back after a session ended and after a launch
   failed. C3 is the one to report even if the round runs short.
