# bring-up-status-pill-and-poke-readiness: round 7 brief

**Candidate:** `fork/testing/status-pill-aac-zbt` @ `ec9352d5`
**Baseline:** none needed. See §1.
**Gate:** 1818 tests / 0 failures
**Read `TESTING-TEMPLATE.md` first.** This brief only says what is different about this round.

---

## 0. What this is and why it exists

Round 6 was a good round. It passed A1, B1, B2, E1 to E4 and F2, it found a real defect the brief
did not ask about, it caught an erratum in the brief's own line list, and it refused to force two
runs the brief had told it not to force. Three of its verdicts need work, and two of the three are
the brief's fault rather than the build's.

**R1 was not a regression, and the reason it differed from round 5 is worth knowing.**
`MainActivity.kt` and the stage tracker are **byte-identical** between round 5's candidate
(`8c6d90e4`) and round 6's (`d8198612`); `git diff` between them on those files is empty. What moved
was the unit. Round 5's P1 ran the clean-run protocol on a `pm clear`ed D-HU, so there was no
connection history, no auto-connect started, and the pill rendered and logged every step through
`STARTING_PROJECTION`. Round 6 wrote keys into a unit that kept its history, so the unambiguous
driver branch fired, an auto-connect was in flight, and once the phone answered at `CONNECTING` the
overlay took the screen and the pill went quiet. Round 6's own mechanism for that is exactly right.

The pill hiding under the overlay is correct. Losing the record is not: that line is the only thing
that says what the user was told, and two sessions differing only in whether an auto-connect
happened to be running left completely different traces of the same bring-up. A step the overlay
hides is now named with the reason instead of being dropped.

**A2 and A3 are one defect, it is real, and the last round's own fix is what exposed it.**
`MainActivity.autoConnectInProgress` was cleared only by a user tap, a connection-state change, or a
30 second watchdog that was armed **only** inside the full-screen overlay's show-method. A wireless
attempt raises the pill, not the overlay, and is promoted to the overlay only once the phone
answers. So an attempt aimed at a phone that never answers held that flag for the life of the
process. Round 5's fix had put that flag into the auto-start offer's own gate, so the question was
suppressed for good; round 6 waited over three minutes and two reopens and never saw it. The offer's
gate now asks whether the full-screen overlay owns the screen, which is the honest question, and
every mode bounds its own attempt so no attempt can outlive its UI.

That flag also short-circuits `beginAutoConnect`, so while it was stuck **every later attempt was a
no-op**, including the USB auto-start overlay. Nothing has ever graded that, and A4 below does.

**B2 passed on the wrong veto, and A1 is why.** The veto that motivated the line is
`attemptInFlight`. Round 6 could not reach it: A1 exists to prove `auto-start-bt-macs` gets cleared
when two phones are paired, and clearing it removes the MAC a real Bluetooth arrival has to match.
Round 6 fired the action at the service component instead and got the `a session is already up`
arm, which is the most trivial of the four. This round puts A1 and B2 on different units.

**D2 had a lever the brief never gave it.** Round 6 read `ACTION_BT_AUTO_START` from a real
Bluetooth arrival as the only way to re-arm after `headunit://disconnect`. It is not:
`ACTION_START_WIRELESS` re-arms the stack directly, and a poke at a stopped manager starts it and
says why. D2's INCONCLUSIVE is this brief's fault, and §3a names the levers now.

**Part C has no evidence.** The round 6 results file carries no Part C section at all. It is re-run
here in full, unchanged.

**F1 found something bigger than the condition it failed.** Condition 3 is unconfirmed because every
attempt with the group forced to 2.4 GHz destabilized within seconds of `SSL handshake complete`,
three times, while the automatic-band control at 5785 MHz was completely stable. D-HU stayed joined
to its own 5745 MHz station throughout, so one radio was being asked to serve a 2.4 GHz group and a
5 GHz station at once. That is the first time this rig has measured that split costing a session,
and it needs a discriminator before the cap can be blamed or cleared: F1b runs the same forced band
with the cap **off**.

**One thing round 6 argued rather than measured.** The results say the cap "cannot be the cause of a
handshake never completing" because it engages only once a session is up. The handshakes did
complete; the sessions tore down afterwards, with the cap engaged. The conclusion is almost
certainly right, but F1b is what makes it a measurement.

## 1. Build and baseline

```bash
git fetch fork
git checkout testing/status-pill-aac-zbt   # ec9352d5
```

That branch is `main` with `feat/bring-up-status-pill-and-poke-readiness`,
`feat/external-bt-zbt-probe` (which carries the pill branch under it) and
`fix/aac-default-on-narrow-band` merged. It carries no commits of its own and is rebuilt from `main`
whenever a part of it moves, so do not expect its SHA to match any earlier round's.

**No baseline APK.** Every run is either a line only this candidate can print, or a positive control
reached by a settings change on the candidate itself.

## 2. What is different about this round

Two commits are new since `d8198612`, both on the pill branch:

| Commit | What |
|---|---|
| `97c291cd` | A pill step the overlay hides is named in the log instead of dropped |
| `61c1dd82` | Every auto-connect mode bounds its own attempt; the offer's gate asks about the overlay, not the flag |
| `0b8624f0` | Amended, not new: the two module dialogs reworded. Same subject and same file set as before, strings only. See Part E |

`AutoConnectAttemptPolicy` is the new pure object. Its bound for a pill is **150 s**, three wake
passes at the poke's 30 s hold plus 15 s gap; the overlay keeps its own 30 s. A pill timeout
deliberately does **not** rewind the status pill: the connection stack is still running and still
reporting, so its step is the honest line.

**The behaviour change to watch for.** A wireless attempt now ends on its own after 150 s where
before it ran until the process died. If a phone answers *after* that, the pill carries the session
to the end instead of the full-screen overlay taking over. That is not a failure; note it if you see
it. The projection still opens, because `AapService` launches it on `HandshakeComplete` itself.

**Standing rig state, unchanged from round 6.** D-MOTO is bonded to D-HU, so D-HU reports two paired
phones and driver selection acts before anything else on its home screen. D-MOTO is not rooted, so
its hotspot is hand-operated. `am broadcast` at the app's own receivers needs
`-p com.andrerinas.headunitrevived`.

## 3. Settings keys this round needs

Written in `shared_prefs/settings.xml` with the app stopped, never in the UI. D-HU is rooted;
D-POCO goes through `run-as`. **Read every one of them back before launching and report the
read-back.** Round 6 lost most of an F run to an `enable-audio-sink` left `false` by an earlier
round, and three other keys had drifted.

### D-HU

| Key | Type | Value | Used by |
|---|---|---|---|
| `wifi-connection-mode` | int | `3` | all |
| `native-driver-selection-mode` | int | `1` (AUTO, the standing value) | R1, A1 |
| `auto-start-bt-macs` | set of string | `DC:B7:2E:5E:4E:59` | A1 |
| `auto-start-offer-answered-macs` | set | **delete the element** | A1 |
| `last-connected-native-mac` | string | must be **non-empty** and name a paired phone | R1 |
| `stand-down-station-mode` | int | `0` AUTO, `1` ALWAYS, `2` NEVER | C1 to C5 |
| `enable-audio-sink` | bool | **`true`** | F1, F1b, F3 |
| `wifi-direct-band` | int | `0` automatic, `2` force 2.4 GHz | F1, F1b, S1 |
| `use-aac-audio` | bool | delete for F1, `true` for F3 | F1, F3 |
| `narrow-band-profile-cap` | bool | `true` for F1, **`false`** for F1b | F1, F1b |
| `log-level` | int | `0` only where a run says so | F3 |

### D-POCO, as head unit

| Key | Type | Value | Used by |
|---|---|---|---|
| `wifi-connection-mode` | int | `3` | A2, A3, A4, B2 |
| `native-driver-selection-mode` | int | `1` (AUTO) | A2, A3, A4, B2 |
| `auto-start-bt-macs` | set | **delete** for A2/A3/A4; **D-MOTO's MAC** for B2 | A2 to A4, B2 |
| `auto-start-offer-answered-macs` | set | **delete the element** between every attempt | A2, A3 |
| `screen-orientation` | int | `2` (landscape) | A3 |
| `enable-audio-sink` | bool | `true` | F2 if re-run |

**D-POCO carries exactly one paired phone (D-MOTO) for Part A and B2.** If it has picked up a second
bond, unpair it first and say so: two phones changes what the offer decides and what B2 can reach.

## 3a. Re-arming the stack without waiting for a phone

Round 6 could not run D2 because it thought a real Bluetooth arrival was the only way back after
`headunit://disconnect`. Either of these re-arms it, addressed at the service component the way
`TESTING-TEMPLATE.md` §3 documents for a poke:

```bash
am start-foreground-service -n com.andrerinas.headunitrevived/com.andrerinas.openheadunit.aap.AapService \
  -a com.andrerinas.openheadunit.ACTION_START_WIRELESS
```

and a poke at a stopped manager, which starts it and logs the reason it had not started:

```bash
am start-foreground-service -n com.andrerinas.headunitrevived/com.andrerinas.openheadunit.aap.AapService \
  -a com.andrerinas.openheadunit.ACTION_NATIVE_AA_POKE --es mac <PHONE_MAC>
```

## 4. The lines that decide every run

All checked with `git grep -F` against `77df7f6b`, except where marked **assembled**, which means
the line is built at runtime from several literals and only its fragments can be grepped. Round 6
caught this brief quoting a line for the wrong class; the marks below are the fix.

| Line | Where it decides |
|---|---|
| `MainActivity: status pill step: ` | R1 |
| `(not shown, the overlay owns the screen)` | R1, the new half |
| `Auto-connect: a phone is answering, taking the full screen.` | R1, marks the promotion |
| `HomeFragment: more than one phone is paired, so the Bluetooth auto-start device is cleared.` | A1 |
| `ACTION_NATIVE_AA_PROMPT_SHOWN received` | A1 condition 3 |
| `driver candidates: ` | A1, A2 |
| `HomeFragment: Unambiguous driver` | A2, A3 |
| `Auto-connect: nothing answered this attempt (mode=` | A2, A3, A4 |
| `HomeFragment: Bluetooth auto-start turned on for` | A2 condition 4 |
| `Auto-connect: begin (` | A4 |
| `AapService: Bluetooth auto-start: nothing to do, ` | B2 |
| `a handshake attempt is already in flight` | B2, the veto that matters |
| `StationStandDown: asked this unit to leave` | C1, C4 |
| `the setting keeps this unit joined to its own WiFi network` | C3 |
| `5 GHz station is the state measured to run clean` | C2 |
| `StationStandDown: this unit has left its WiFi network.` | C1, C4 |
| `WppTcpServer: projection already up; holding the re-dialled control channel open without a handshake` | D1 |
| `AapService: a session is already connected, so its decoders are left running` | D2 |
| `[ServiceDiscovery] AAC audio announced by the 2.4 GHz cap (Use AAC Audio is off)` | F1 |
| `[ServiceDiscovery] This session's network is on 2.4 GHz` | **assembled**, F1 |
| `[ServiceDiscovery] NegotiatedResolution is: ` | F1 |
| `AudioDecoder.start:` | F1 condition 3, F3 |
| `AAC Decoder started for` | F3, its rate and channel count are interpolated |

Two lines this brief deliberately does not quote as greppable:

- `StationStandDown: $why` is assembled from `StationStandDownPolicy.describeSkipped`, so C2, C3 and
  C5 grade on the **fragment** named above, never on a whole line.
- `NativeAA: reopening the Android Auto listener $why.` is assembled; B1 is not re-run this round, so
  it does not matter here, but do not copy it forward as a literal.

## 5. Runs

### R0 - build, gate, install, identity

Unchanged from round 6. The gate is **1818 / 0**. DEX grep the installed base APK for
`AutoConnectAttemptPolicy`, which is new this commit and is absent from every earlier build.

### R1 - the pill, with an auto-connect in flight

**Do not `pm clear` D-HU**, and confirm `last-connected-native-mac` is non-empty before launching.
That is the whole difference from round 6, and it is the state that made the round 6 FAIL: an
auto-connect must be in flight so the overlay takes the screen part-way through.

Run the clean-run protocol otherwise, and collect the ordered `status pill step:` list.

**PASS requires:**

1. At least six steps, first `ARMED`, `WAITING_FOR_PHONE` before `WAKING_PHONE`, and any rank
   decrease being a `WAKING_PHONE` to `WAITING_FOR_PHONE` retreat or the one that follows a create
   request. Unchanged.
2. `Auto-connect: a phone is answering, taking the full screen.` appears, proving the promotion
   happened and the run reached the state round 6 failed in. **If it is absent the run did not reach
   that state and the result is INCONCLUSIVE**, not a PASS: re-check `last-connected-native-mac`.
3. The last step is `STARTING_PROJECTION`, and after the promotion the steps read as
   `<NAME> (not shown, the overlay owns the screen)`. That suffix on the tail of the list is the fix.
4. `hidden` appears at most once, at the end, when the stack goes down.

Report the wall clock from the first step to `STARTING_PROJECTION`.

---

### Part A - the auto-start offer

**A1, the stored trigger is cleared even while the selector is up.** Unchanged from round 6, which
passed it. Kept as the guard that the offer's gate change did not break the RESET arm. D-HU, two
paired phones, `native-driver-selection-mode` = `1`, `auto-start-bt-macs` set to D-POCO's MAC,
`auto-start-offer-answered-macs` deleted, both read back.

**PASS requires:** the clear line; `auto-start-bt-macs` empty afterwards in both `settings.xml` and
`settings_device_protected.xml`; `ACTION_NATIVE_AA_PROMPT_SHOWN` present; `driver candidates: 2
phone`. If the prompt line is absent the run is INCONCLUSIVE rather than PASS.

**A2, the question is asked on a later resume.** This is the round. D-POCO as head unit, **one**
paired phone (D-MOTO), `auto-start-bt-macs` deleted, `auto-start-offer-answered-macs` deleted,
`last-connected-native-mac` confirmed naming D-MOTO, **D-MOTO's Bluetooth off throughout**.

Launch. The first resume auto-connects and puts nothing up, which is correct. Then wait for the
attempt to give up: **the new bound is 150 s**, so wait at least three minutes from launch before
concluding anything. Then press Home and reopen the app.

**PASS requires:**

1. The first launch logs `HomeFragment: Unambiguous driver` and no offer dialog appears.
2. `Auto-connect: nothing answered this attempt (mode=PILL_THEN_OVERLAY)` appears, and the time from
   `Auto-connect: begin (` to it is about 150 s. **This is the new give-up path**; if it never
   appears, quote what did and FAIL, because everything below depends on it.
3. After the reopen, the offer dialog is on screen. Screenshot it.
4. `auto-start-offer-answered-macs` is still absent while the dialog is up and unanswered.
5. Answering Yes writes the MAC, logs `HomeFragment: Bluetooth auto-start turned on for`, and the
   question does not return on the next launch.

If the dialog never appears **and** condition 2 did hold, that is a clean FAIL of the offer's gate
rather than of the bound, and saying which is the most useful thing the round can report.

**A3, the dialog survives the relaunch, and a back press is remembered.** A2's setup plus
`screen-orientation` = `2` on D-POCO. Delete `auto-start-offer-answered-macs` before each attempt,
and give the attempt the same three minutes before reopening.

**PASS requires:**

1. Two `HomeFragment.onResume` lines about half a second to a second apart, confirming the relaunch.
   Round 6 measured 0.406 s and that half is already proven.
2. No `WindowLeaked` anywhere in the capture, no `FATAL EXCEPTION`, PID unchanged.
3. A dialog on screen and answerable at +5 s after the reopen. Screenshot it.
4. Dismiss it with `input keyevent KEYCODE_BACK`, relaunch, and confirm the question does **not**
   come back. Report `auto-start-offer-answered-macs` after each step.

**A4, a stuck wireless attempt no longer blocks a USB connection.** New, and it grades the wider half
of the A2 defect that nothing has ever measured. D-POCO as head unit is the wrong shape here, so run
this on **D-HU** with its USB port: the rig's USB arrangement in `TESTING-TEMPLATE.md` §7b applies.

Start a wireless attempt aimed at a phone whose Bluetooth is off, so an attempt is in flight and
unanswered. **Within the first 60 s**, while the attempt is still live, attach the USB phone.

**PASS requires:** `Auto-connect: begin (USB auto-start` appears and the full-screen overlay comes
up. On the pre-fix build the flag was already held and that call returned silently, so this run is
the whole point. If this rig cannot host USB at all, say so and report **UNTESTABLE**; do not
substitute a different trigger.

---

### Part B - the veto that matters

**B2, a vetoed auto-start names the veto that was actually firing.** Round 6 reached only the
`a session is already up` arm, because A1's own clearing made a real arrival impossible on D-HU.
Run this on **D-POCO**, where `auto-start-bt-macs` is set to D-MOTO's MAC and survives, and where
one paired phone means nothing clears it.

Put D-POCO into the state round 5's A1 was in: **WiFi off on D-POCO**, Native mode, so a handshake
attempt opens and cannot complete because no credentials are coming. Then bring D-MOTO's Bluetooth
up so it dials in and a real `ACTION_BT_AUTO_START` arrives while that attempt is held.

**PASS requires:** `AapService: Bluetooth auto-start: nothing to do, ` followed by
`a handshake attempt is already in flight`, quoted whole.

If the arrival instead finds no attempt in flight and the line names a different veto, that is still
a PASS of "the line prints and names one of the four", but say which arm it was, because the
`attemptInFlight` arm is the one still ungraded after two rounds. If **no** line appears at all on a
vetoed arrival, that is a FAIL.

---

### Part C - the station stand-down mode

Re-run in full and unchanged: the round 6 results carry no evidence for any of C1 to C5.

D-HU must be **joined to a WiFi network**, which the stand-down is about leaving. D-MOTO hosts it:
2.4 GHz for C1, C3 and C5, 5 GHz for C2 and C4. **D-MOTO is not rooted**, so that hotspot is
hand-operated; §7a says so. Confirm the band D-HU actually joined with `dumpsys wifi | grep -i freq`
before each run, because the policy reads the station's own frequency and not the hotspot's setting.
A spaced SSID needs its quotes escaped, per §7a.

D-HU is API 34, so the feature needs the overlay permission. Grant it with
`appops set com.andrerinas.headunitrevived SYSTEM_ALERT_WINDOW allow` for C1 to C4 and revoke it with
`deny` for C5. Re-grant afterwards and say so.

- **C1, AUTO stands down from a 2.4 GHz station.** `stand-down-station-mode` = `0`, overlay granted,
  D-HU on the 2.4 GHz network. **PASS:** `StationStandDown: asked this unit to leave` quoted whole so
  `mode=`, the station frequency and `5GHz=` can be read; then
  `StationStandDown: this unit has left its WiFi network.`; then, after the session ends,
  `this unit's WiFi network is enabled again and should rejoin`.
- **C2, AUTO stays joined on a 5 GHz station.** Same with D-HU on the 5 GHz network. **PASS:** no
  `asked this unit to leave`, and a line carrying
  `5 GHz station is the state measured to run clean`.
- **C3, NEVER stays joined.** `stand-down-station-mode` = `2`, D-HU on 2.4 GHz, overlay granted.
  **PASS:** no `asked this unit to leave`, and a line carrying
  `the setting keeps this unit joined to its own WiFi network`.
- **C4, ALWAYS stands down regardless of band.** `stand-down-station-mode` = `1`, D-HU on the
  **5 GHz** network. **PASS:** `asked this unit to leave` with `mode=ALWAYS` in it. This is C2's
  positive control: the same station state, the opposite outcome, from one settings change.
- **C5, the permission gate.** `stand-down-station-mode` = `1`, overlay **revoked**. **PASS:** no
  stand-down, and a line carrying `will only let the app drop its own WiFi connection while the app`.

If D-HU cannot be joined to a network at all, every run reports
`there is nothing to stand down before creating the group` and the part is INCONCLUSIVE, not failed.
Say which network each run was joined to.

---

### Part D - the re-dial hold-open and the decoder guard

Both came back INCONCLUSIVE in round 6. D1's was honest; D2's was this brief's fault.

**D1, a re-dial while projecting sends nothing.** A live Native AA session on D-HU with D-POCO. Once
the picture is up, make the phone re-dial the control channel without ending the session.

Round 6 tried a WiFi toggle (which produced a full disconnect and a second handshake) and a Gearhead
force-stop (which ended the session). Both are the path the brief warns against. A third lever to try
first: put D-POCO into airplane mode for **under two seconds** and back out, which is short enough
that the TCP session often survives while the control channel is re-dialled.

**PASS requires:** the `WppTcpServer: projection already up; ...` line, **and** the projection
surviving it: no new `SSL handshake complete`, no gap in the `Throughput over ...ms: rendered=`
lines. If the phone will not re-dial without ending the session after three attempts across at least
two levers, report **INCONCLUSIVE** and say which levers were tried. Do not force it.

**D2, a disconnect keeps a live session's decoders.** `headunit://disconnect` followed at once by a
fresh connect, so the new session is up before the old one's disconnect is handled. **The re-arm is
the thing round 6 lacked:** after the disconnect, fire `ACTION_START_WIRELESS` at the service (§3a)
rather than waiting for a Bluetooth arrival, then connect.

**PASS requires:** `AapService: a session is already connected, so its decoders are left running`
**and** the new session showing a picture. This is a race, so three attempts without the line is
**INCONCLUSIVE, not FAIL**. Report the attempts and the gap between disconnect and reconnect.

---

### Part E - the module dialogs, reworded

Round 6 passed E1 to E4 and nothing about the route has changed. What changed is the copy: round 6's
screenshots were read, and the two dialogs a user meets when they pick Native were too long, carried
em dashes, asked the owner to export a log, and let somebody with any external Bluetooth module read
the route as theirs. It only ever works on units running a ZJ/ZLink service on `127.0.0.1:3152`; the
rest of that hardware class reach their module over Binder and cannot use it at all. One of the two
could also state something untrue, claiming the module's service "answered when this app asked" on a
path where it was never asked.

**E5, evidence only, no verdict.** Set the property and relaunch exactly as round 6's Part E did
(`setprop rw.zlink.bt.type extra` with the app force-stopped; §7a has the whole lever, and it must be
cleared afterwards the same way). Then screenshot both dialogs with their body text readable:

1. With `external-bt-zbt-transport` **off**, choose Native. Expect the title
   `Native Wireless cannot work on this head unit` and a two-paragraph body that names the evidence,
   says this app can only reach the module through a ZJ/ZLink service, and says this unit is not
   running one.
2. With `external-bt-zbt-transport` **on**, choose Native. Expect the title
   `Native Wireless through the ZJ/ZLink module` and a body that says the handshake **will be sent**
   through the module's ZJ/ZLink service. **It must not say the service answered or was asked**,
   because on this rig it never was: the toggle short-circuits the dial. If it still claims the
   service answered, say so, and that is the one thing in this run worth reporting as wrong.

Neither dialog should contain an em dash or ask you to export a log. Report the two screenshots and
whether the wording matches. Everything past the dialogs stays UNTESTABLE on a unit with no module,
as it was in round 6.

---

### Part F - the AAC branch's remainder

**F1, the cap fires on the band the session is on.** Round 6 passed conditions 1, 2, 4 and 5 and
could not confirm 3. Redone with `enable-audio-sink` read back **`true` first**, which is what cost
round 6 the run. D-HU: `wifi-direct-band` = `2`, `use-aac-audio` **deleted**, `narrow-band-profile-cap`
left true. Connect D-POCO and play music through the tap into the projected video.

**PASS requires:**

1. The group came up below 4000 MHz. Round 6 measured 2412 and 2437; if it comes up on 5 GHz the run
   never reached the state under test and is INCONCLUSIVE.
2. `[ServiceDiscovery] AAC audio announced by the 2.4 GHz cap (Use AAC Audio is off)`, with
   `use-aac-audio` confirmed absent from the read-back.
3. `Media Sink Setup Request: 2` on the media audio channel, and `AudioDecoder.start:` reporting
   `isAac=true, source=setup`. **This is the condition round 6 could not reach.**
4. The announced configuration is at most 720p and 30 fps. Quote the line.
5. `[ServiceDiscovery] This session's network is on 2.4 GHz` (assembled).

If the session destabilizes before condition 3 the way round 6's three attempts did, **report how
many attempts and what tore them down**, then go straight to F1b, which is what separates the causes.

**F1b, the cap is not what is destabilizing the session.** New, and the more important of the two.
Identical to F1 except `narrow-band-profile-cap` = `false`, so the group is still forced onto
2.4 GHz beside D-HU's own 5 GHz station but the cap announces nothing.

**Report, no PASS or FAIL:** how many of three sessions survived two minutes of playback, and what
ended the ones that did not. Quote the `WifiDirectManager` coexistence line and the station's
frequency from `dumpsys wifi | grep -i freq`.

The reading this decides: if the sessions are just as unstable with the cap off, the instability is
the cross-band radio split and the cap is cleared. If they are stable with the cap off and unstable
with it on, that is a genuine candidate defect and the most important thing in the round.

**S1, the stand-down question the coexistence finding raises.** New, and it is a measurement taken
before any code moves, not a grade. With `wifi-direct-band` = `2` and D-HU joined to D-MOTO's
**5 GHz** hotspot:

1. `stand-down-station-mode` = `0` (AUTO). Report the whole `StationStandDown:` line. AUTO is
   expected to leave the station joined, saying a group beside a 5 GHz station runs clean, which
   round 6's F1 suggests is only true when the group is on 5 GHz too.
2. `stand-down-station-mode` = `1` (ALWAYS), same everything else. Report whether the session is
   stable for two minutes of playback where AUTO's was not.

No verdict. If ALWAYS is stable and AUTO is not, the policy has a case it does not cover and the
next change is written against these numbers.

**F3, the 16 kHz guidance channel, with a real maneuver.** Round 6 did not attempt it. D-HU with
D-POCO, `use-aac-audio` = `true`, `enable-audio-sink` = `true`, `log-level` = `0`,
`wifi-direct-band` = `0`. Start turn-by-turn navigation on D-POCO with the phone unlocked and Maps
already permitted, and let it speak at least one route maneuver.

**PASS requires:** `AAC Decoder started for 16000 Hz, 1 channels` **twice** in one session: once at
connection setup and once for the maneuver. **Assembled**: the rate and the channel count are
interpolated and `(Async)` follows, as round 6's F2 saw at 48000 Hz, so grep
`AAC Decoder started for` and read the numbers off. Round 5 got only the first. If no maneuver speaks within
a few attempts, report how many and mark it INCONCLUSIVE. Do not spend more than about fifteen
minutes.

**Run F3 before Part C and before F1b if time is tight.** It has now been carried over twice.

## 6. Do not re-run

- Everything round 6 passed and nothing here touches: B1, E1 to E4, F2, F4. The listener reopen, the
  module route's whole settings surface, and the second decoder vendor are settled. E5 above is the
  one exception, and it grades wording rather than the route.
- Round 5's P2 to P6, W2, WB1, WB1b, its D1 to D3, A1 conditions 1/2/4, A2's soft-AP wait, and
  AAC1/AAC2/AAC4.
- `AutoStartOfferPolicy`, `AutoConnectAttemptPolicy`, `AaListenerRecoveryPolicy`,
  `BtAutoStartRearmPolicy`, `NarrowBandProfilePolicy`, `StationStandDownPolicy`,
  `AudioSinkCodecPolicy` and `AacDecoderRecoveryPolicy` are pure and JVM-tested. Every run above
  grades the wiring around them, never the decisions themselves.

## 7. Report back

1. **R1 conditions 2 and 3.** Did the promotion happen, and did the suppressed steps carry their
   reason. That pair is what the pill fix is for.
2. **A2 condition 2.** Did the attempt give up on its own, and how long did it take. Everything else
   in Part A depends on it.
3. **A2 condition 3 and A3 condition 3.** Did a dialog reach the screen. The offer's ASK arm has
   never been reached on hardware in six rounds.
4. **A4.** Did the USB overlay come up while a wireless attempt was in flight.
5. **B2.** Which of the four vetoes the line named.
6. **F1 condition 3, and F1b.** Whether the AAC decoder starts under the cap, and whether the
   instability survives the cap being turned off.
7. **S1.** What AUTO says and whether ALWAYS is the stable one.

And the standing question: with F2 already clean end-to-end on a second decoder vendor, F1 condition
3 and F3 are the last two things between the AAC work and losing its "(Experimental)" label.
