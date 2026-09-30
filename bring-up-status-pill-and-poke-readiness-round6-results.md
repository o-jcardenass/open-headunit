# bring-up-status-pill-and-poke-readiness — round 6 results

**Candidate:** `testing/status-pill-aac-zbt` @ `d8198612`   **Baseline:** none needed (see brief §1)
**APK md5:** `ff7168dad1d3e3bce424aadbad290b0c` (built here, matches on both devices)
**Unit:** UNISOC MT50 (D-HU, `27870808938846`), Android 14 · Redmi/POCO X3 NFC (D-POCO, `4f4027e9`), used as
phone (A1/B/D/E/F1) and as head unit (A2/A3/F2) · Motorola edge 30 neo (D-MOTO, `ZY22GC3BM4`), used as the
Part A2/A3/C/F2 phone and as the Part C hotspot
**Date:** 2026-09-11

## Setup notes

- Scripts used: `build_hur.sh`, `run_unit_tests.sh` (both unmodified), `r6_set_hu_prefs.sh` (rooted D-HU
  writer, pre-existing). D-POCO is not rooted; its settings were written with a pushed `sed` script run
  through `run-as`, the same pattern `r6_set_hu_prefs.sh` uses on D-HU. No new script was added.
- **D-HU's `settings.xml` had drifted from every key this round assumes as a starting point**, left over
  from earlier rounds sharing the rig: `native-driver-selection-mode` was `2` (not `1`), `wifi-direct-band`
  was `1` (neither the round's `0` default nor F1's `2`), `use-aac-audio` was explicitly `false`, and
  `enable-audio-sink` was `false`. All were corrected per the affected run below; `enable-audio-sink=false`
  cost most of F1's time (see F1).
- **D-MOTO (Motorola edge 30 neo) is not rooted**: `cmd wifi start-softap` and `cmd wifi get-softap-config`
  both refuse with `SecurityException: Uid 2000 does not have access`, and there is no non-root tethering
  shell command on this build. Part C's hotspot (band toggle, SSID, password) had to be operated and read
  by hand; I could not script it. The operator toggled the band twice (2.4 GHz for C1/C3/C5, 5 GHz for
  C2/C4) and read off the SSID/password.
- **`cmd wifi connect-network` with a space in the SSID needs the quotes to survive to the on-device
  shell**, not just the local one: `adb -s $HU shell cmd wifi connect-network \"SSID With Spaces\" wpa2
  psk` (backslash-escaped so adb's own space-joining of argv reconstructs a quoted string on the far side).
  A plain `"SSID With Spaces"` quoted only for the local bash is silently mis-split into two tokens on
  device and the command fails with `Unknown network type <second word>`.
- **The device-protected auto-start mirror is `shared_prefs/settings_device_protected.xml`**, not
  `shared_prefs/settings.xml` under the same directory as the brief's wording could be read to imply.
  Checked both for A1 condition 2; the mirror file only exists once the app has written to it at least
  once.
- **Settings-screen search (E1–E4, F4) needed one UI tap** (the search field) despite everything else in
  the round being scriptable: no exported action locates a settings row by label, and per
  `TESTING-TEMPLATE.md` only nav-graph fragments are deep-linkable, not categories or search results. One
  tap plus typed text, no scrolling, screenshot taken immediately after.
- **D-HU carries two bonded phones all round** (D-POCO and D-MOTO), left there by round 5's C1 as the
  brief's §2 flagged. This is what makes A1 reachable, but it also means `HomeFragment`'s
  "more than one phone is paired" auto-clear fires on **every** D-HU launch — see B2 and D2, both of which
  needed a different lever because of it.
- **A1 was completed, B2 and D2 needed a workaround, D1/D2 came back INCONCLUSIVE**, all detailed in their
  own sections; nothing here was silently skipped.
- **F3 was not run.** Time budget: everything else in this brief plus two genuine findings (R1, A2/A3) had
  already consumed the round, and F3 needs a live turn-by-turn maneuver the brief itself caps at "no more
  than about fifteen minutes." Rather than rush it, I left it for a follow-up. Reported as **INCONCLUSIVE**
  below, not skipped silently.
- Raw logcat captures and every screenshot referenced by filename live in
  `hur-wifi-test-scripts/round-bring-up-r6/` on the rig machine (not committed here, per the standing
  convention for rounds 4 and 5). The screenshots the round's own report leans on are copied into
  `evidence/bring-up-status-pill-and-poke-readiness-round6/` in this repo.

## R0 — build, gate, install, identity

**PASS**

- `BUILD SUCCESSFUL`; `com.andrerinas.headunitrevived_3.4.0-beta2_debug.apk` (versionCode 107) built and
  copied.
- Gate: **1813 tests, 0 skipped, 0 failures, 0 errors** — matches the brief exactly.
- Installed on D-HU and D-POCO with `adb install -r`; live `md5sum` of each `pm path` base.apk:
  `ff7168dad1d3e3bce424aadbad290b0c` on both, matching the host build and each other.
- Identity: `ACTION_QUERY_STATE` reports `"commit":"d81986129f8f"` on D-HU. DEX grep on the installed base
  APK: `AaListenerRecoveryPolicy` × 5, `AudioSinkCodecPolicy` (new this commit) × 3.

## R1 — the pill, as a regression guard

**FAIL**, reproduced 2/2. This is the run the brief calls "worth more than any other in this brief," so
full detail:

Two clean cold bring-ups (D-POCO's Bluetooth off at launch, back on 18 s later, per the clean-run
protocol):

| Run | first step | last real step | ends at |
|---|---|---|---|
| r1 | `ARMED` 10:30:41.113 | `CONNECTING` 10:31:04.402 | `status pill step: hidden` 10:31:04.520 |
| r1b | `ARMED` 10:33:39.543 | `CONNECTING` 10:34:04.658 | `status pill step: hidden` 10:34:04.774 |

Both runs: at least 8 real `ConnectionStage` values logged (≥6 required), first value `ARMED`,
`WAITING_FOR_PHONE` before `WAKING_PHONE`, no illegal rank decrease. **The one condition that fails is
"last `STARTING_PROJECTION`."** In both runs the last thing the pill ever prints is `hidden`, ~116–118 ms
after `CONNECTING`, and `STARTING_PROJECTION` never appears as a pill line at all. The underlying session is
fine in both runs — `SSL handshake complete` follows within ~150 ms of `hidden`, and `AapProjectionActivity`
launches normally.

**Mechanism**, read from `MainActivity.kt`: at `CONNECTING`, the log line
`Auto-connect: a phone is answering, taking the full screen.` fires, which flips `autoConnectMode` from
`PILL_THEN_OVERLAY` to `OVERLAY` (`MainActivity.kt:374-376`). `renderStagePill` computes
`shown = if (stage == null || overlayOwnsScreen) null else stage`, and once `autoConnectMode == OVERLAY`,
`overlayOwnsScreen` is true for the rest of the attempt (`MainActivity.kt:544-548`) — so every subsequent
emission, including the internal transition to `STARTING_PROJECTION` at
`ConnectionStageTracker.report(ConnectionStage.STARTING_PROJECTION)` (`MainActivity.kt:390`), computes
`shown = null`. Because the log only fires `if (shown != renderedStage)` and `renderedStage` is already
`null` from the `hidden` line, `STARTING_PROJECTION` is silently swallowed — it happens, but nothing ever
logs it.

This candidate's own diff (AAC/narrow-band audio, `AudioSinkCodecPolicy`, `WifiBandCapability`) never
touches `MainActivity.kt`, `ConnectionStageTracker`, or the auto-connect overlay logic, so this does not
read as a regression this commit introduced — more likely a pre-existing characteristic of the
`PILL_THEN_OVERLAY` path exposed by how quickly this rig's phone answers. Whether that is the pill's real
mechanism working as designed (silencing itself once the overlay has visually taken over) or a genuine gap
in the pill's own log coverage is a design question this round can't settle; either way the brief's literal
condition fails, twice, cleanly. Discard-rule check on r1: `MATCH!`=0, `createGroup SUCCESS`=1, one `SSL
handshake complete` — clean.

## Part A — the auto-start offer

### A1 — the stored trigger is cleared even while the selector is up

**PASS.** D-HU, two paired phones, `native-driver-selection-mode=1`, `auto-start-bt-macs` set to D-POCO's
MAC, `auto-start-offer-answered-macs` deleted, both read back before launch.

- `HomeFragment: more than one phone is paired, so the Bluetooth auto-start device is cleared.` — present.
- `auto-start-bt-macs` reads back empty (`<set name="auto-start-bt-macs" />`) afterward in both
  `shared_prefs/settings.xml` and the device-protected mirror
  `shared_prefs/settings_device_protected.xml`.
- `ACTION_NATIVE_AA_PROMPT_SHOWN received` — present; the selector was genuinely up when the trigger was
  cleared.
- `driver candidates: 2 phone, 0 unknown, 0 not a phone` — confirms the two-phone pool.

### A2 — the question is asked on a later resume

**FAIL**, and a genuine finding, not a rig limit. D-POCO as head unit, one paired phone (D-MOTO),
`auto-start-bt-macs` empty, `auto-start-offer-answered-macs` absent, `last-connected-native-mac` confirmed
naming D-MOTO, D-MOTO's Bluetooth off throughout.

- Condition 1 **passes**: first launch logs `HomeFragment: Unambiguous driver (motorola edge 30 neo) -
  auto-connecting directly without prompt`, no offer dialog on the first launch.
- Condition 2 **fails**: the offer dialog never appears, not after the first Home-and-reopen cycle, and
  not after a second wait of well over a minute and a second reopen — over 3 minutes of elapsed time and
  two `am start` reopen attempts, screenshotted each time
  (`evidence/…/a2-stuck-pill-3min.png`). The screen never advances past the resting
  `"motorola edge 30 neo is disconnected, waking it..." / "Waiting for your phone to connect"` pill.

**Root cause**, read from the source: `HomeFragment.checkAutoStartOffer` only acts when
`screenIsFree = !driverCheckOwnsScreen && activeDialog == null && !MainActivity.autoConnectInProgress`
(`HomeFragment.kt:684-690`). `autoConnectInProgress` is set `true` in `beginAutoConnect`
(`MainActivity.kt:290`) and cleared only by `endAutoConnect()`. The **only** caller of `endAutoConnect()`
that fires without a successful connection is the 30-second watchdog
(`MainActivity.kt:600-608`), and that watchdog is started **only inside `showAutoConnectOverlay()`**
(`MainActivity.kt:611-617`) — i.e. only once `autoConnectMode` has been promoted from `PILL_THEN_OVERLAY`
to `OVERLAY`, which itself only happens once the phone answers
(`MainActivity.kt:374-376`, the same branch R1 analysed). D-MOTO's Bluetooth was off for the whole run by
design, so it never answers, promotion never happens, the watchdog never starts, and
`autoConnectInProgress` has no path back to `false`. Every subsequent `onResume` (including the one the
Home-and-reopen cycle produces) reads `screenIsFree = false` and returns without ever evaluating the offer.
This is a real gap, not a hardware limit: the mechanism the run's own precondition depends on
("wait for the auto-connect to give up") only exists for the overlay-promoted path, and a phone that never
answers at all has no give-up path at all.

### A3 — the dialog survives the relaunch, and a back press is remembered

**FAIL** for the identical root cause as A2 — same setup plus `screen-orientation=2` to force a relaunch.

- Two `HomeFragment.onResume` lines confirmed **0.406 s apart** (11:30:32.643 and 11:30:33.049),
  proving the forced relaunch genuinely happened.
- **No `WindowLeaked`, no `FATAL EXCEPTION`, PID unchanged** across the relaunch — round 5's C2 leak fix
  holds; that is not what's broken here.
- No dialog ever appears (`evidence/…/a3-stuck-pill-after-relaunch.png`), for the same reason as A2: the
  underlying `autoConnectInProgress` flag is stuck `true` before the relaunch even happens, and the
  relaunch itself doesn't touch it.

Conditions 3 and 4 (dialog answerable, back press remembered) could not be exercised because condition 1's
precondition — a dialog reaching the screen at all — never held.

---

## Part B — the Android Auto listener, and the veto line

### B1 — the listener comes back after the radio bounces

**PASS**, clean, on the first attempt. D-HU, WiFi on throughout, Native mode, listener confirmed armed
before the Bluetooth bounce.

1. `NativeAA: AA Server socket error: read failed, socket might closed or timeout, read ret: -1` —
   present, 10:36:37.913.
2. `NativeAA: the Android Auto listener is down, so nothing can answer the phone. Reopening it as soon as
   this unit's Bluetooth is back.` — present, 10:36:37.915.
3. `NativeAA: reopening the Android Auto listener now this unit's Bluetooth is back on.` at 10:36:49.357,
   followed immediately by a second `ACTIVELY LISTENING on Android Auto UUID` at 10:36:49.373. **Gap from
   the radio disable command to the reopen: 11.4 s** (Bluetooth self-reverted faster than the usual ~14 s
   this time).
4. The phone got a full session **without the app being relaunched**: one `SSL handshake complete` at
   10:37:02.394, 13 s after the listener reopened.
5. `giving up until the next start.` — **absent**.

Discard-rule check: `SSL handshake complete` × 1, `giving up` × 0, `MATCH!` × 0, `createGroup SUCCESS` × 1
— clean.

### B2 — a vetoed auto-start says which veto fired

**PASS**, but by a different route than the brief's default script, noted here because it matters for
whoever plans the next round. The natural trigger (a real Bluetooth arrival matching
`auto-start-bt-macs`) is unreachable while D-HU carries two bonded phones — A1 exists to prove
`auto-start-bt-macs` gets cleared on every such launch, and it does, so there is never a matching MAC left
to arrive against by the time a session is up. With a live session already established (B1's continuation),
I fired `ACTION_BT_AUTO_START` directly at the running `AapService` component
(`am start-foreground-service -n …/AapService -a com.andrerinas.openheadunit.ACTION_BT_AUTO_START`) —
the same directly-addressed-component pattern `TESTING-TEMPLATE.md` documents for `ACTION_NATIVE_AA_POKE`.
That reaches exactly the code path B2 is grading (`AapService.onStartCommand`'s `ACTION_BT_AUTO_START`
branch and `BtAutoStartRearmPolicy.vetoReason`), even though the receiver-side arrival that would normally
carry it is blocked by A1's own precondition.

- `AapService: Bluetooth auto-start: nothing to do, a session is already up.` — present, naming one of the
  four documented reasons, quoted whole.

---

## Part D — the re-dial hold-open and the decoder guard

### D1 — a re-dial while projecting sends nothing

**INCONCLUSIVE**, two attempts, both explicitly landing on "a different path" per the brief's own caveat
rather than the state under test.

- **Attempt 1** (toggle D-POCO's WiFi off/on): produced a full disconnect/reconnect instead of a soft
  redial — video throughput dropped to 0 fps for ~10 s, then a **new** `SSL handshake complete` appeared
  (a second one, not the "no new handshake" the run wants), and `WppTcpServer` never logged at all.
- **Attempt 2** (force-stop and relaunch Gearhead on D-POCO, the brief's own alternative lever): this ended
  the session outright — `ACTION_QUERY_STATE` read `connected:false, state:Disconnected` right after. The
  brief explicitly warns this is "a different path," and it was: a full session end, not a control-channel
  redial.

Neither attempt reached the `WppTcpServer: projection already up; holding the re-dialled control channel
open without a handshake` code path. Per the brief ("Do not force it by ending the session"), this is
reported INCONCLUSIVE rather than continuing to force it.

### D2 — a disconnect keeps a live session's decoders

**INCONCLUSIVE**. The scripted recipe (`headunit://disconnect` immediately followed by a fresh connect) is
structurally blocked on this rig's current state: `headunit://disconnect` triggers
`AapService: Native AA user exit. Stopping active launcher.`, which fully tears down the Native AA
listeners, and the **only** documented re-arm route (`ACTION_BT_AUTO_START` from a real Bluetooth arrival)
is the same route B2 found blocked by the two-phones-bonded precondition. A second attempt tried
`am start -n MainActivity` immediately after the disconnect as a substitute re-arm; it returned
`Warning: Activity not started, its current task has been brought to the front` (a no-op onto the existing
task, not a fresh `onCreate`) and produced no new session. Two attempts, no window in which
`AapService: a session is already connected, so its decoders are left running` could ever be reached; per
the brief's own three-attempt allowance this is INCONCLUSIVE.

---

## Part E — the external Bluetooth module route, on a unit with no module

Baseline confirmed first: `bt=internal` in an exported log before setting the property.

### E1 — the settings surface appears

**PASS.** With `rw.zlink.bt.type=extra` set and the app relaunched, all three rows appear under Wireless
Connection, found via the in-app search field (no scrolling): "Ignore the Bluetooth compatibility check",
"Connect through the head unit's Bluetooth module", "Test the head unit's Bluetooth module".
Screenshotted with description text readable: `evidence/…/e1-wireless-connection-rows.png`,
`evidence/…/e1-probe-row.png`.

### E2 — choosing Native AA is refused honestly

**PASS.** Selecting Native (from a different mode, to force a genuine transition — see Setup notes) raised
a dialog titled **"Native Wireless cannot work on this head unit"**, body naming
`rw.zlink.bt.type=extra` and that "This one was asked and did not answer, so there is no route to that
module here." Reproduced twice, identical both times.
Screenshot: `evidence/…/e2-refusal-dialog.png`.

### E3 — the probe

**PASS**, with one erratum in the brief's own §4 line list. `ZbtProbe: starting — external Bluetooth module
probe` appears, the row's own text reads "Nothing is listening on port 3152. This unit has no vendor
Bluetooth daemon to talk to.", confirmed for both "Watch only" and "Wake and watch". **The brief's quoted
`NativeAA: [ZBT] nothing is listening on 127.0.0.1:3152` line never appears from this probe** — verified by
grepping the full source tree: that exact string is only ever logged by `ZbtDaemonReachability.kt` and
`ZbtAaCarrier.kt`, both used solely during a real Native AA handshake attempt, never by `ZbtProbe.kt` (the
settings-screen diagnostic), which logs its own distinct `ZbtProbe: no ZBT daemon on 127.0.0.1:3152:
ConnectException: …` line instead. The brief's §4 conflated the two classes' log lines; the row's own text
and `ZbtProbe`'s own line are the correct evidence for this run and both confirm the same fact the run
wants.

### E4 — the export footer

**PASS.** `bt=internal` (baseline, before the property) → `bt=module:BLOCKED` (property set, transport
toggle off) → `bt=module:ZBT` (transport toggle on) → `bt=internal` again after cleanup
(`setprop rw.zlink.bt.type ""`, force-stop, relaunch). Cleanup also confirmed: a fresh search for
"Bluetooth module" in Settings returns zero rows.

---

## Part F — what is left of the AAC branch

### F1 — the cap fires on the band the session is on

**PASS on conditions 1, 2, 4, 5; condition 3 unconfirmed** — see below, and see the reproducible
instability this run surfaced, which is arguably the more important finding.

With `wifi-direct-band=2`, `use-aac-audio` absent, `narrow-band-profile-cap=true`:

1. Group formed at **2412 MHz** / **2437 MHz** across three attempts — confirmed below 4000 MHz every
   time.
2. `[ServiceDiscovery] AAC audio announced by the 2.4 GHz cap (Use AAC Audio is off)` — present, with
   `use-aac-audio` confirmed absent from the settings read-back.
3. **Not confirmed.** `AudioDecoder.start:` with `isAac=true, source=setup` never appeared under the
   forced-2.4GHz condition — see below.
4. `[ServiceDiscovery] Negotiating a profile … target=1280x720@30` and `NegotiatedResolution is: 1280x720`
   — at most 720p/30fps, confirmed.
5. `[ServiceDiscovery] This session's network is on 2.4 GHz` — present, naming the network rather than the
   radio, on a unit that does have a 5 GHz radio.

**Positive control** (`wifi-direct-band=0`): group formed cleanly at **5785 MHz**,
`HeadUnitScreenConfig.recalculate … linkCapped=none`, and all three audio channels (AUDIO, AUDIO1, AUDIO2)
announced `Media Sink Setup Request: 1` (PCM). Session was completely stable — no drops, no retries.

**Condition 3 and the instability finding.** Getting music playing long enough to see
`AudioDecoder.start` required D-HU's `enable-audio-sink` to be flipped from its inherited `false` to
`true` (see Setup notes) — with it off, the app's own log says why:
`"Audio sink is off in Settings. Skipping the media and speech audio channels - the phone will not send
audio and this is not a fault"`, and the only sink that still gets set up is the always-on "Audio2 (System
Sounds)" channel, never the media/music one. Once corrected, **every attempt with `wifi-direct-band=2`
forced destabilized the session before a real audio channel could be exercised**: three consecutive
attempts each reached `SSL handshake complete` and then, within seconds, either (a) a second, spurious
connection attempt raced in and its handshake failed (`SSL Handshake: Failed to read AAP header`, tearing
down the whole session), or (b) the phone itself reported `Connection lost / Retrying…`
(`evidence/…/f1-connection-lost-retrying.png`), reproduced on the third attempt too. This did **not** happen
on the `wifi-direct-band=0` positive control, and matches the app's own logged warning
(`WifiDirectManager.logStationCoexistence`): D-HU stays joined to its 5745 MHz station network throughout,
and forcing the WiFi Direct group onto 2.4 GHz means one radio has to retune continuously between the two —
apparently more disruptive under sustained real use than the log's own "a few hundred milliseconds at a
time" framing suggests. This looks like a rig/methodology characteristic of the **forced**-2.4GHz test
lever (which stresses the always-on 5 GHz station coexistence continuously) rather than a candidate defect
— the narrow-band cap itself only ever engages once a session is already up, so it cannot be the cause of
a handshake never completing — but it is a real, reproducible finding worth having on record.

### F2 — AAC on a second decoder vendor

**PASS**, full chain, D-POCO as head unit with D-MOTO as phone (Qualcomm decoder on D-POCO vs. UNISOC on
D-HU — a genuinely different vendor from F1/round 5's coverage). D-MOTO showed no bond to D-HU's Bluetooth
radio at all in this session, so no unbind step was needed this time.

- `Media Sink Setup Request: 2` on AUDIO2/AUDIO1/AUDIO.
- `AudioDecoder.start: … isAac=true, source=setup`.
- `AAC Decoder started for 48000 Hz, 2 channels (Async)`.
- None of `rebuilding the AAC decoder`, `no working AAC decoder`, `AAC Codec Error`, `Failed to init AAC
  decoder` appeared.
- `inbound rate over …ms: … audio=30kB/s` then `audio=62kB/s` — matches round 5's 62–63 kB/s AAC figure
  closely.
  Screenshot of live playback: `evidence/…/f2-aac-playing-on-dmoto.png`.

### F3 — the 16 kHz guidance channel, with a real maneuver

**INCONCLUSIVE — not attempted.** Time budget; see Setup notes. Queued for a follow-up round rather than
rushed within the brief's own 15-minute cap.

### F4 — the two changed descriptions

Evidence only, as the brief specifies. Both rows screenshotted together with description text readable:
"Lower video on a 2.4 GHz link" (Video settings) and "Use AAC Audio" (Audio settings), each showing its
full reworded description. `evidence/…/f4-video-and-aac-rows.png`.

## 6. Do not re-run

Unaffected by this round, per the brief: round 5's P2–P6, W2, WB1, WB1b, its D1–D3, A1 conditions 1/2/4,
A2's soft-AP wait, AAC1/AAC2/AAC4. The seven pure policy objects the brief names remain JVM-tested only;
the runs above graded wiring, not their own decisions.

## 7. Report back

1. **A1 condition 3 and condition 1 together: both hold.** The selector was on screen and the trigger was
   cleared anyway — the round 5 C1 fix is confirmed.
2. **B1 condition 4: holds.** The phone got a session after the radio bounce with no relaunch, gap 11.4 s.
3. **A3 conditions 2 and 3 partially hold**: no leaked window, PID unchanged, relaunch proven — but no
   dialog ever reaches the screen to be answerable. The relaunch/leak half of round 5's fix is solid; the
   "asked on a later resume" feature itself has a real gap when the phone never answers at all (A2/A3).
4. **F1 conditions 1, 2 and 5 hold**; condition 3 (the actual AAC decoder start under the cap) is
   unconfirmed due to reproducible session instability specific to the forced-2.4GHz test lever, not
   observed on the automatic-band positive control. F2 independently confirms the same AAC mechanism
   end-to-end on a different phone/decoder, which is strong indirect evidence the mechanism itself works;
   F1's own condition 3 just couldn't be pinned down on this rig under forced 2.4 GHz this round.
5. **R1 fails cleanly and reproducibly**, but traces to `MainActivity`'s pill-vs-overlay handoff, code this
   candidate's actual diff never touches — flag for the pill feature's owner rather than block this
   candidate's AAC/narrow-band work on it.
6. **A2/A3 are a new, real finding**: the auto-start offer's "wait for it to give up" precondition has no
   path to true when the target phone never answers at all, because the only give-up mechanism
   (`MainActivity`'s 30 s watchdog) only arms once the auto-connect overlay is shown, which itself only
   happens once the phone answers. Worth fixing before the offer feature ships as complete.

And the standing question the AAC branch is waiting on: F2 gives a full, clean, second-vendor confirmation
of the AAC mechanism end-to-end. F1 confirms the cap correctly detects the band, announces AAC, and caps
video — but couldn't confirm the AAC decoder actually starts under the forced-2.4GHz test condition
specifically, due to that condition's own reproducible instability on this rig. F3 is unrun. On that
evidence, "(Experimental)" coming off "Use AAC Audio" looks reasonable on F2's strength; the cap's AAC
default shipping should wait for F1's condition 3 and F3 in a follow-up round, ideally testing the cap's
radio-detected arm (a genuine no-5GHz unit) rather than the forced-band lever, which this round shows
stresses this rig's own 5GHz-station coexistence in a way real narrow-band hardware would not.
