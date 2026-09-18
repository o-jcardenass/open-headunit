# self-mode-media-session — round 1 results

**Candidate:** `fix/929-self-mode-media-session` @ `665332d8`       **Baseline:** `origin/main` @ `80a81099`
**APK md5:** candidate `28455e33b31ede223e76b84fc0a016d4` / baseline `a8d592bb75f00d2286106f5d8f9ac93e`
**Unit:** D-HU (UNISOC MT50, Android 14) for R4/R5/R6b; D-POCO (POCO X3 NFC, Android 15, Gearhead 17.8.163804-release.daily) for R1/R2/R3/R6a
**Date:** 2026-09-18

## Setup notes

- D-POCO's Android Auto dev head-unit server (127.0.0.1:5277) listens on **`/proc/net/tcp6`, not `tcp`**.
  The brief's precondition check (`grep -i :149D /proc/net/tcp`) never matches on this unit even when the
  server is up; checked `tcp6` per `project_selfmode_needs_gearhead_hu_server_tap` and confirmed the
  server had been up the whole time. Correcting this for the next brief that reuses this check.
- Scripts used: `run_unit_tests.sh`, `build_hur.sh` (candidate and baseline, apks saved as
  `candidate-665332d8.apk` / `baseline-80a81099.apk` in `apks/`), `set_prefs_runas.sh` for every
  settings write on both units (non-rooted D-POCO, and used on rooted D-HU too since `set_hu_prefs.sh`
  defaults to D-HU only and this round needed the same multi-key call shape on both).
- `log-source=1` (APPLOG_FILE) routes `AppLog` output to the app's own `HUR_Log_*.txt` under
  `/storage/emulated/0/Android/data/<pkg>/files/` **instead of logcat entirely** — confirmed by reading
  `LogExporter.kt:89-95` and `AppLog.kt` (`LOGGER` is swapped to `Logger.File`, which never calls
  `android.util.Log`). Every decisive line for this round was pulled from that file, not from
  `adb logcat`, which showed nothing for the app the whole round.
- No local music files on D-HU/D-POCO's `Music`/`Downloads` folders formed a real multi-track queue via
  VLC's single-file `VIEW` intent (queue size stayed 1, so "next" had nothing to advance to). Opening the
  containing folder itself (`file:///.../Music/` or `.../Downloads/`, `-t resource/folder`,
  `-n org.videolan.vlc/.StartActivity`) queued the whole folder and made "next" a real, checkable action.
  Used on both units for R4/R5/R1.
- **`adb install -r` briefly looked like it had wiped `settings.xml`** (a `tail -3` right after reinstall
  showed only bookkeeping keys) — false alarm, a full `cat` showed every written key intact, just
  reordered by the app's own `SharedPreferences.apply()` after its own launch. No settings were actually
  lost at any point in the round; call this out because the rig quirk this looked like is a real,
  previously-documented failure mode and it is worth being explicit that this instance was not one.
- **Self Mode hit a genuine Gearhead-side wedge on D-POCO during R6a's first session**: after a clean
  ownership-decline (`media session left to the player on this device (Self Mode)`), the handshake failed
  three times with `Handshake: the peer accepted the connection and then sent nothing at all` /
  `session state failed (peer_silent)`, and a retry (relaunching only our own app) hit the identical
  signature. The app's own log line names the fix precisely: Android Auto's car service holds the
  accepted socket with no timeout and only a force-stop of Android Auto on the phone clears it — but
  that is exactly the action `project_dpoco_selfmode_gearhead_server` warns never to do on D-POCO,
  because it also kills the `:5277` dev server until the operator manually re-toggles
  Developer settings → "Start head unit server". Escalated per the escalation rule (something
  irreversible on the rig outside the brief); operator approved force-stopping Gearhead and re-toggled
  the setting afterward. Flagging for the brief-writer: this wedge is a real, repeatable Gearhead/rig
  fault independent of this branch, and the next Self Mode brief on this unit should expect it and
  budget an operator round-trip for the toggle.
- R1's exact procedure: force-stopped the app only (never Gearhead) with the local player (VLC on the
  Downloads folder queue) already playing *before* launching Self Mode, to match the reporter's actual
  ordering (a pre-existing player, our session arriving after). An initial pass got this order backwards
  (VLC started after Self Mode was already up) and was discarded before taking any reading.

## R0 — build gate

**PASS**

- `run_unit_tests.sh`: 2168 tests, green. `MediaSessionOwnershipPolicyTest` = 2, `MediaKeyRoutingPolicyTest` = 8 (both match the brief exactly).
- `build_hur.sh` for both candidate (`28455e33b31ede223e76b84fc0a016d4`) and baseline (`a8d592bb75f00d2286106f5d8f9ac93e`) — distinct md5s confirmed live on-device before every run that needed them.

## R4 — an ordinary session is untouched, candidate

**PASS**

- Settings written (D-HU): `log-level=0`, `log-source=1`, `log-capture-enabled=true`, `media-key-routing=0`.
- Radio state: D-POCO airplane mode + explicit `svc wifi disable` before launch (both radios confirmed off), head unit launched first, phone radios restored ~18s later, D-POCO's screen woken and confirmed at Maps' AA ghost activity (not stuck on Settings).
- Discard-rule check: clean. One `MATCH! Starting AapService`, one `createGroup SUCCESS`, single P2P interface actually used this session (`p2p-wlan0-4`; a `p2p-wlan0-3` entry was a stale leftover interface enumerated once before the group formed, not a second group), one SSL handshake (two log lines, one from `AapSslContext` one from `AapTransport`, same event 5ms apart).
- Decisive lines: `CommManager: TX Key -> AA=87/88/85` each with a matching `isPress=false` release (6 lines total), `0` `Not sending media key`, `0` `media session left to the player`, one `MediaSession: State updated to PLAYING`.
- Video streaming confirmed live (`RECV: VIDEO Media Data` / ack pairs) at the time keys were sent.

## R1 — the defect on this rig, baseline

**No verdict on the branch (per brief). Rig does not clearly reproduce the reported symptom in this configuration.**

- Baseline (`80a81099`) installed and confirmed live on D-POCO. VLC opened on the Downloads folder
  queue (15 tracks) and confirmed `PLAYING` **before** Self Mode was launched. App launched via
  `auto-start-self-mode=true` + plain `MainActivity` start; connected on the AA 17.4+ direct path
  (Gearhead 17.8.163804, `127.0.0.1:5277`) without issue this attempt.
- (a) **VLC held the top session throughout**, both immediately after Self Mode connected and after the
  key press: `dumpsys media_session` → `Media button session is org.videolan.vlc/VLC/72`. Our own
  package (`com.andrerinas.headunitrevived`) was `active=true` but `state=STOPPED` the whole time; its
  `MediaSession` briefly cycled `STOPPED → PLAYING → STOPPED` at connect time (three
  `AapService.updateMediaSessionState` lines within ~1.4s of `onConnected`) and never became top.
- (b) **The track changed.** `input keyevent 87` advanced VLC's queue (`retrofunk2` →
  `XM50688_Salario Ordinario Nuevo Ingreso Término Fijo D C y M_Oscar Julián Cárdenas.doc`, item 12/16 → 16/16).
- (c) **The capture does carry `TX Key -> AA=87`** (both press and release), even though our package
  never held the top media session at the time. This shows the two mechanisms this branch touches
  are not the same signal on baseline: `AapProjectionActivity`'s foreground key capture forwarded the
  key over AAP regardless of session priority, and separately VLC's own session (top of the OS media
  stack) responded and advanced — plausibly Android Auto turning the AAP-forwarded key back into its
  own system media-button broadcast, which the OS then routed to VLC as the active session. Whichever
  the exact path, VLC ended up correctly advancing, which is not the reporter's symptom.
- Per the brief's own contingency: **R2 and R3 are marked INCONCLUSIVE below** — the rig cannot
  demonstrate a fix for something it does not reproduce here. Plausible reason (not confirmed): this
  D-POCO's Gearhead (17.8.163804) is materially newer than whatever the Roco K706 reporter ran, and AA's
  own media-session-priority handling may have shifted since the report (see the standing note that
  wireless mode 3 depends on undocumented AA behaviour and breaks/changes across releases — the same
  caveat plausibly applies to Self Mode's media routing).
- Candidate reinstalled and confirmed live (`28455e33b31ede223e76b84fc0a016d4`) immediately after.

## R2 — the candidate does not take the session

**INCONCLUSIVE** — per R1, this rig does not reproduce the baseline defect, so nothing here is decisive either way.

Observed anyway for context: both of R6a's fully-connected candidate sessions on D-POCO (below) show
`AapService: media session left to the player on this device (Self Mode)` exactly once per session at
connect, and zero `MediaSession: State updated to PLAYING` lines for either session. (A separate,
earlier candidate launch attempt showed the same decline line but then hit the Gearhead wedge described
in Setup notes before fully connecting — not used as evidence here since that attempt never reached a
live session.) Consistent with the fix working as designed, but still not decisive given R1.

## R3 — the buttons reach the player

**INCONCLUSIVE** (R3a, R3b) — same reason as R2. Not run beyond what R1/R2 already exercised, per the
brief's instruction not to spend the rig chasing a defect it cannot reproduce.

**R3c (observational, no verdict):** not captured this round — VLC was used as the local player throughout
rather than an app Android Auto lists on its own media widget, so there was no widget name to report.

## R5 — a declined key falls through instead of vanishing

**PASS**

- Settings written (D-HU): `media-key-routing=2` (NEVER); restored to `0` immediately after the run.
- Local player: VLC on D-HU, folder queue (`file:///storage/emulated/0/Music/`, 36 tracks), confirmed
  `PLAYING` before the key press.
- Decisive lines: `CommManager: Not sending media key 87 to Android Auto (routing=NEVER, selfMode=false,
  src=projection)` (press + release, ×2 across two key presses in this session), **zero**
  `TX Key -> AA=87`.
- Measurement: VLC's queue advanced from "El mareo (feat. Gustavo Cerati)" (Bajofondo, Mar Dulce) to
  "Pide Piso" (Bajofondo, Presente) on the press — the head unit's own player genuinely moved, which on
  `main` it could not have (the key reached nothing at all there).
- Discard-rule check: clean. One `MATCH! Starting AapService`, single P2P interface used
  (`p2p-wlan0-5`), one SSL handshake (again two log lines for the same event).

## R6 — ownership comes back

### R6a — end and restart a Self Mode session

**PASS** (after the Gearhead wedge in Setup notes was cleared and the operator re-toggled "Start head
unit server")

- Session 1: `AapService: media session left to the player on this device (Self Mode)` — exactly 1
  occurrence, `MediaSession: State updated to PLAYING` — 0 occurrences. SSL handshake completed cleanly.
- Session 2 (after `headunit://exit`, force-stop, relaunch): same counts — exactly 1 decline line, 0
  `PLAYING` lines. The decline count did not accumulate across the two sessions (1 per session, not 2
  read from a stale file), and no session left the media session active.

### R6b — ordinary Native AA session after a Self Mode session

**PASS**

- After exiting the second Self Mode session, D-HU (candidate, `wifi-connection-mode=3`) was launched
  with D-POCO's radios held down, then restored — same clean-run procedure as R4.
- `MediaSession: State updated to PLAYING` returned (D-HU's own AapService, ordinary Native AA session,
  not the loopback one). `CommManager: TX Key -> AA=87` forwarded with a matching release, exactly as
  in R4. The disconnect/re-arm path correctly restored ownership after a Self Mode session ended.
- Discard-rule check: clean. One `MATCH! Starting AapService`, one `createGroup SUCCESS`, single P2P
  interface (`p2p-wlan0-0`).

## Anything the brief did not ask about

- The `/proc/net/tcp` vs `/proc/net/tcp6` precondition check (Setup notes) is worth folding into
  `TESTING-TEMPLATE.md` itself rather than leaving it per-brief, since it will bite any future round that
  checks this port on D-POCO.
- The Gearhead wedge in R6a is the more important finding: it is a real, repeatable D-POCO/Gearhead fault
  (car service accepts a socket and never speaks on it) that is independent of this branch, but it makes
  every Self Mode round on this unit fragile to an operator round-trip. Worth a standing rig-quirks entry
  once reproduced a second time.
