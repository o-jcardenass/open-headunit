# mic-uplink — round 2 results

**Candidate:** `fork/fix/mic-uplink` @ `42302130`       **Baseline:** `origin/main` @ `ea7aa7e0`
**APK md5:** `5bef73b831be7b2b1495f1c29177ed58` (candidate) / `4371919f98d7b14037f65150cc58c4e0` (baseline)
**Unit:** UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14 — same rig as round 1; new phone this round: Motorola edge 30 neo, Android, Gearhead `17.3.662854-release`
**Date:** 2026-08-25

## Setup notes

- `origin/main` had advanced to `bae47b23` since round 1; checked out `ea7aa7e0` explicitly by SHA per the brief, exactly as round 1 did. `fork/fix/mic-uplink` was unchanged at `42302130`.
- **G0 reused, not rebuilt.** Both SHAs are byte-identical to round 1's build (confirmed: `round-mic-uplink/arm-a-ea7aa7e0.apk` and `arm-c-42302130.apk` md5s match round 1's recorded values exactly, and the candidate APK was still the one live-installed on the rig from round 1's end). Since no code changed and the SHAs are identical, round 1's 765/833 unit-test counts and the eight named-suite counts are carried forward rather than re-run. Deviation from the brief's literal G0 step, done to save a full double-build/test cycle with zero information gain; flag if a future round wants a hard re-verify.
- **`set_hu_pref.sh` is unsafe for arm switching and was abandoned mid-round.** It relaunches via `install_and_launch.sh SKIP_BUILD=1`, which installs whatever is newest in the shared `apks/` folder — not necessarily the arm you're currently testing. It silently reinstalled the candidate over what was supposed to be an arm-A run for M2 (caught immediately via md5 check before any capture was taken, no contamination reached a result). Switched to direct `adb shell sed` edits against `settings.xml` for the rest of the round, and explicit `adb install -r <specific-apk>` for every arm switch, verified by md5 every time.
- **New phone means new device-specific settings.** `auto-start-bt-macs`/`auto-start-bt-name` still held round 1's POCO X3 NFC identity; the app was found actively poking the old, absent phone. Updated both to the Motorola's MAC (`A0:46:5A:97:E4:95`) via direct `adb shell sed` (these are a `<set>` and a `<string>`, outside `set_hu_pref.sh`'s int/boolean/string scope).
- **A new native-AA guard (`noteHandsFreePokeSkip`) skips the poke whenever an active BT hands-free link already exists**, to avoid disrupting it. On this phone, once paired and HFP-connected, the link re-establishes within ~2s of any Bluetooth cycle — faster than the poke retry loop reaches it — so the automatic *and* the manual (`HomeFragment` picker) poke were both skipped every time, and no session formed via any BT-cycle recipe from `TESTING-TEMPLATE.md` §7a. Root cause was one level up: this being a brand-new phone/head-unit pairing, Gearhead had never associated this specific car and would not auto-launch on ACL_CONNECTED alone (confirmed: ACL_CONNECTED fires cleanly on this phone from an HU-side BT cycle, unlike the round-1 quirk on the old phone — but no Gearhead/CarService activity followed it). **User performed a one-time manual first-run of Android Auto on the phone** (no scriptable trigger existed: Gearhead has no resolvable `LAUNCHER` activity — `VnLaunchPadActivity` reports "does not exist" — so this could not be scripted). After that one manual step, every subsequent BT-cycle-based reconnect completed cleanly and automatically for the rest of the round.
- Three G1 attempts were discarded before a clean run: wrong device targeted (old auto-start MAC), a 3-`createGroup SUCCESS` contamination from an exit/relaunch cycle, and the HFP-guard skip described above. Kept as `g1-*-discarded-*.txt`.
- **KEYCODE_BACK does not cancel an in-flight assistant mic session on this build.** Sent 1.5s after triggering, the session ran its full natural length (~9.7s) regardless. Not documented anywhere in the template; worth adding. The brief's alternate method — a second trigger sent shortly after the first — did work: the interrupted session's own summary showed 1.09s / 10 frames against a natural range of 3-10s / 23-82 frames elsewhere in the same round, i.e. a real early cut, not a coincidence.
- Gemini's own multi-turn behaviour means **one external trigger can produce several `Voice Session Notification: START`/`STOP` pairs** with no further action from the rig (observed 2-3 per single broadcast in G1/M1/M4). The brief's "four assistant sessions" was read as four START/STOP pairs regardless of how many external triggers produced them, once it became clear a fixed 1:1 trigger-to-session mapping doesn't hold on this phone.
- M4's VERBOSE-only line (`MIC Mic Response type: 32774`) could not be observed: the brief's own settings table pins `log-level=1` (DEBUG) for M4, but §4 lists that line under the VERBOSE (`log-level=0`) bucket. Not re-run at VERBOSE to avoid re-doing the round's most time-consuming manual-trigger sequence for one line; `acks=` from the `mic uplink |` summaries answers the same underlying flow-control question directly (see M4 below).
- `settings.xml` restored byte-for-byte to the round-start backup at the end of the round (diffed, exact match). Candidate APK left installed, matching how round 1 ended.
- Scripts used: `set_hu_prefs.sh` for the initial multi-key G1 write; direct `adb shell sed` for every subsequent single-key/arm-specific change (see above); no new script needed. `hur-wifi-test-scripts/round-mic-uplink/` holds all raw captures, named `<run>-<arm>[-phone].txt`, discards suffixed `-discarded-<reason>`.
- One filename collision: `m1-c.txt` was already used by round 1's own capture in the same directory; round 2 overwrote it with a fresh capture. Round 1's own results are already fully recorded in `mic-uplink-round1-results.md`, so no round-1 finding depends on that raw file surviving — flagged here so it isn't mistaken for lost round-1 evidence.

## G1 — does this phone open the microphone?

**PASS — PROCEED.** Assistant on this phone is **Gemini** (Gearhead `17.3.662854-release`; confirmed via `GH.CsatSurvey: Sending trigger broadcast for survey GEMINI` / `USED_GEMINI` after a completed session, not just the request path). Default network during setup: `MOBILE[LTE]`, `IS_VALIDATED` (network `248`, transport `CELLULAR`).

- `Mic request: open: true`: fired on both of the two assistant sessions that formed inside the gate window.
- `MIC Media Data`: 44 (non-zero).
- `mic uplink started`: 2.
- Gemini's own post-drive survey fired (`GEMINI`), confirming a real, working round trip on this phone — unlike round 1's POCO, which never got past `Cannot connect to Gemini`.

## M1 — the message carries its type, and the phone understands it

**PASS on the frame.** Candidate: **494/494** `MIC Media Data` lines at `dataOffset: 6`, **0** at `dataOffset: 2`. Baseline: **622/622** at `dataOffset: 2`, **0** at `dataOffset: 6`. Both arms' `Mic request: open: true` fired repeatedly (10 sessions on candidate, 8 on baseline, across the four external triggers — see Setup notes on Gemini's multi-turn behaviour).

**Pre-registered INCONCLUSIVE on the phone, exactly as the brief anticipated arm A transcribing correctly too:** both arms correctly transcribed and acted on "navigate to the nearest petrol station" (both started navigation correctly). `Received message with invalid type header` stayed at **0** on the phone throughout arm A's run, matching the brief's own explanation — arm A's timestamp high bytes happen to be zero, so the phone silently accepts the misaligned frame as type 0. The verdict rests on the frame counts above (clean PASS) and M2 (below), per the brief.

Candidate per-session `mic uplink |` summaries (10 sessions, in order): frames/bytes/%-of-expected/peak —
`71f/290816B/101%/pk3365`, `68f/278528B/102%/pk2074`, `42f/172032B/103%/pk3112`, `34f/139264B/105%/pk2346`, `70f/286720B/102%/pk2912`, `23f/94208B/107%/pk3529`, `36f/147456B/104%/pk4136`, `78f/319488B/102%/pk690`, `44f/180224B/104%/pk5050`, `28f/114688B/103%/pk3345`.
Peaks ranged 690-5050 across every session, "silent" ones included — this room was never actually silent (ambient conversation/noise), so the brief's "does peak move between silent and spoken" check is not usable as recorded; every session shows real, moving signal.

## M2 — the rate picker no longer reaches the wire

**PASS, and the strongest single result this round produced.**

| Check | Arm A | Arm C |
|---|---|---|
| `Initializing AudioRecord ... SampleRate:` | **48000** (all 6 opens in the session) | **16000** (all 3 opens) |
| announced microphone rate | 16000 (picker unchanged from M1) | 16000 |
| `capture summary ... rate=` | n/a (baseline has no capture-summary line) | 16000 (×3) |
| transcription | **no response at all** | matches M1 (correct) |

Arm A opened 48kHz capture under a 16kHz announcement exactly as predicted, and this time — unlike M1, where the same phone transcribed correctly despite the frame defect — **the assistant produced no response whatsoever**. Same phone, same sentence, same session type, only the capture rate changed. Arm A's M2 transcription differs from its own M1 transcription (correct → none), which the brief flagged as the strongest possible outcome if produced.

Arm C ignored the picker entirely: `SampleRate: 16000` every time, `capture summary rate=16000` every time, zero fallback lines (`16000 Hz capture is unavailable`, `will not open 16000 Hz mono capture`: both 0), transcription unaffected.

## M3 — whole 2048-frame messages

**PASS**, using M1-C's own captures (both sessions ran full utterances, no extra session needed).

- `largest=4096B` and `smallest=4096B` on all 10 sessions, no exception.
- `discarded=` per session: 2304, 3072, 768, 256, 0, 512, 1024, 512, 256, 1792 bytes — all strictly between 0 and 4095, never accumulating across sessions.
- VERBOSE capture: **494/494** `MIC Media Data` lines read `size: 4110`, no other size present.
- Latency question: not answered — the operator didn't compare response speed closely enough between arms to say either way.

## M4 — the phone's request is answered, and its stop is honoured

**The three optional fields are present on this Gearhead build — a first for any log seen on this project:**

```
anc_enabled: false
ec_enabled: false
max_unacked: 2
```

Session 1 (let run to natural end): opened, ran 10.33s, closed with `mic uplink | 82 frames, 335872 B in 10327ms (101% of expected), peak=2345/32767, ..., acks=82, discarded=2048B`. Summary appeared at the moment of close, not just at the round's final disconnect.

Cancel attempts: **`KEYCODE_BACK` sent 1.5s into a session had no effect** — the session ran its full natural length (~9.7s) regardless, so this is not a working cancel path on this build. **A second trigger sent ~1.5s after the first did cancel it**: the resulting session closed after only 1.09s / 10 frames (`mic uplink | 10 frames, 40960 B in 1094ms (117% of expected), peak=161/32767, acks=10, discarded=1280B`), against a natural range of 3-10s / 23-82 frames seen everywhere else in the round — a genuine early cut, with its summary appearing at the moment of the cancel.

`MIC Mic Response type: 32774` could not be checked — that line is VERBOSE-only and this run's own settings table pins `log-level=1`. See Setup notes.

**`acks=` measurement (no PASS condition, as specified):** across every session this round, candidate or baseline-adjacent capture, `acks` tracked `frames` almost exactly 1:1 — `82/82`, `30/30`, `43/42`, `74/73`, `10/10` here, and `71/70, 68/68, 42/41, 34/34, 70/70, 23/22, 36/36, 78/78, 44/44, 28/28` from M1. Gearhead acknowledges essentially every frame sent; `max_unacked: 2` never appears to create any real backlog on this rig/phone combination.

## M5 — the head unit microphone off, and what the phone does about it

**PASS on the head unit side.** Session established and stayed up the whole run (`AapProjectionActivity` still resumed at the end). `AapTransport: not taking the microphone (setting useHeadUnitMicrophone=false, available=true)` fired exactly **once**, at session start. `Mic request: the head unit microphone is off in Settings...` fired on **every one of 12 requests** across two spoken triggers. `Initializing AudioRecord`, `mic uplink started`, `capture summary`: all **zero** throughout. No mic-attributable `Byebye Request`.

**Phone side — no PASS condition, reported as specified:** both spoken attempts **timed out with no response**, rather than the phone falling back to its own microphone. `GH.AssistantEdProvider: getNextEducationToShow opportunity is USED_GEMINI` confirms Gemini genuinely engaged both times (this isn't "the trigger never landed"), it simply never produced an answer. The specific phone-side strings the brief expected (`Using phone microphone`, `Not using phone mic`, `microphone timed out; no data received for %d`) — confirmed present in Gearhead `17.5.663204` — **did not appear verbatim anywhere in this capture**, on a phone running the lower `17.3.662854-release`. This looks like the same kind of phone-build string drift already known from other threads (`project_gearhead_17_4_broadcast_disabled`), not a missing capture; worth flagging for whoever next revises §4b's string list.

## Anything the brief did not ask about

- The `noteHandsFreePokeSkip` guard (from the merged `wifi-launcher-parity` work) is a real behavioural change worth its own note for a first-time pairing: on a brand-new phone/head-unit combination, it can leave the app permanently unable to poke (HFP always wins the race) with no automatic fallback, until the phone has been manually walked through Android Auto's own first-run once. Not a regression in this branch, but worth documenting somewhere reachable — reporters pairing a new phone to a long-running head unit could hit exactly this and read it as "the app can't connect."
- `bt-address` stayed blank before the round and after every launch this round too, on this phone as well as round 1's — third data point for `BluetoothAddressSeedPolicy`'s empty-string branch getting no hardware exercise on this rig across two different phones.

---

## Answering §7's five deliverables

1. **Assistant + G1:** Gemini; G1 **PASSED** (mic opened, bytes flowed, Gemini completed a real round trip).
2. **M1:** candidate 494/494 at `dataOffset: 6` (0 at `dataOffset: 2`); baseline 622/622 at `dataOffset: 2` (0 at `dataOffset: 6`). Both arms transcribed "navigate to the nearest petrol station" correctly and started navigation.
3. **M2:** arm A `SampleRate: 48000` under a 16kHz announcement; arm A's transcription **did** move — correct in M1, **no response at all** in M2. Arm C unaffected by the picker in both runs.
4. **M4:** `anc_enabled: false`, `ec_enabled: false`, `max_unacked: 2`; `acks` tracked `frames` at ~1:1 across every session measured.
5. **M5's phone side:** timed out both times; did not fall back to the phone's own microphone.
