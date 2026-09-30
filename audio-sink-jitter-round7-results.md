# audio-sink-jitter — round 7 results

**Candidate:** `fix/audio-sink-and-wireless-bring-up` @ `de44dc18c`       **Baseline:** `main` @ `5ce51c5e6`
**APK md5:** candidate `d44161a579ed961528fab1fd14220c8b` (installed APK pulled back with a real `adb pull` + `md5sum` and matches the build just pushed; local build artifact hashes identically)
**Unit:** D-SAM (Samsung SM-T230, `degaswifi`), Android 4.4.2 (API 19), 2.4 GHz-only radio, head unit. D-POCO (POCO X3 NFC) as phone.
**Date:** 2026-09-16

## Setup notes

- Candidate branch checked out fresh (`git checkout -B audio-sink fork/fix/audio-sink-and-wireless-bring-up`); `git log --oneline -7` matched the brief's seven SHAs exactly, `de44dc18c` on top of round 6's six unchanged commits.
- Scripts used: `run_unit_tests.sh`, `build_hur.sh` (via `install_and_launch.sh`, `HU=30041c35642d2200`), `set_pref_hostedit.sh` for every settings.xml edit, `s1_wifi_holddown_tight.sh` for J1r's lever. No new script was needed.
- Round 4's baseline settings table (log-level, audio keys, resolutionId, etc.) was already live on the unit from a prior round and needed **no writes** before R0 — every key read back matching the table on the first check.
- **Order run: R0, R1, J1r, S1r2, W1r**, per the brief.
- **D-SAM's system clock reads roughly 12 hours off real time** (`date` returned `07:55 COT` when the real time was `19:55 COT` — same calendar day, AM/PM effectively flipped). Not seen or flagged in any earlier round's Setup notes; worth carrying into `TESTING-TEMPLATE.md` §7a. All timestamps below are quoted verbatim from D-SAM's own log and are self-consistent for gap arithmetic within this report; they are not wall-clock-comparable to D-POCO's log or to the host's own `date -u` without the offset in mind.
- **A major, unplanned finding sits under R1 below.** The first three bring-up attempts (a cold launch, a second cold launch, and a third after bouncing D-POCO's Bluetooth) all failed to land: the poke and HFP hold-down succeeded every time, but D-POCO's Gearhead decided the head unit's Bluetooth had disappeared before WSEM every time (`WIRELESS_SETUP_CAR_BLUETOOTH_DISAPPEAR_BEFORE_WSEM`), and its own log showed it retrying a **stale, cached WPP-over-TCP endpoint** (`GH.WIRELESS.SETUP: Retrying connection attempt on all channels by restarting WPP`, `WIRELESS_WIFI_PROJECTION_PROTOCOL_TCP_NETWORK_UNAVAILABLE`) left over from round 6's own W1 arming step. Round 6's own Setup notes already named this exact mechanism ("the belief was cached regardless of the rejected response") but round 7 had not re-armed WPP at all yet — the contamination survived the round boundary untouched by anything either round's app-side settings did. Recovery followed the documented, only-known fix (`TESTING-TEMPLATE.md` §7a, "measured in `audio-sink-jitter round 5`"): forgot the vehicle via `uiautomator` + minimum taps (Gearhead's own `DefaultSettingsActivity` → Vehicles → Google → Forget — no scriptable trigger exists), then accepted the resulting one-time "Welcome to Android Auto" re-consent dialog on the very next poke cycle, also via `uiautomator` + tap. Once done, R1 landed cleanly on both of its required bring-ups. See R1's own section for the full trace and the exact taps used.
- **A second major, unplanned finding sits under S1r2.** The forget-vehicle lever that round 5 found "durable for the round" was **not** durable here: on the very next poke cycle after forgetting, with no re-consent dialog shown at all this time, Gearhead reconnected and rendered a complete session. Reading `NativeAaHandshakeManager.kt` afterward found why: `pokesSinceLastAccept` (what the stale-group watchdog reads as `unansweredPokeCount()`) is reset to `0` the instant `handleHandshake()` is entered (line 2139), i.e. on every RFCOMM accept, before any handshake byte is exchanged. Gearhead still briefly opens+closes that RFCOMM socket each cycle even with the vehicle forgotten, which is enough to keep resetting the counter. S1r2's own scenario is not reachable with this lever on this phone. Reported BLOCKED per the brief's own contingency instruction, not retried a third way.
- **A third, structural finding sits under W1r.** D-SAM cannot host a real system WiFi access point at all: `ro.radio.noril=yes` (no baseband/RIL hardware whatsoever — a true WiFi-only tablet), and its Settings app's Tethering/Hotspot screen (reachable only via the hidden `com.android.settings/.Settings$TetherSettingsActivity` component — the standard `android.settings.TETHER_SETTINGS` intent is not resolvable on this build) renders with a header and no content under it, no "Portable Wi-Fi hotspot" toggle anywhere. `de44dc18`'s stricter credential-wait gating (from `8441d0445`, this round's other new-ish commit) requires real AP credentials before completing the handshake and fails cleanly with an explicit message when none exist. W1r never got past the arming step. This is a permanent fact about this physical unit, not a rig-session or Bluetooth-flakiness limitation like round 6's blocker — worth recording in `TESTING-TEMPLATE.md` §7a alongside "no USB accessory path" as a standing capability boundary for D-SAM specifically.
- Both latches (`video-profile-starvation-cap`, `playback-focus-self-defeating`) read `false` throughout; neither tripped this round.
- All settings restored to the pre-round backup byte-for-byte, confirmed by readback: `native-wifi-version-exchange=false`, `native-ap-transport=0`, all four hotspot keys empty/`"0"`.

---

## R0. Gate

**PASS**

- `./gradlew :app:testGithubDebugUnitTest`: **2022** tests, 0 failures (summed from every `test-results/testGithubDebugUnitTest/*.xml`), matching the brief's stated gate exactly (up from round 6's 2017; the brief's own note: five of those are `JoinRefusalPolicy.remainingDelayMs` cases, graded on hardware by J1r).
- Installed APK pulled back from D-SAM and hashed with a real `pull` + `md5sum`: `d44161a579ed961528fab1fd14220c8b`, matching the build just pushed.

---

## R1. A healthy wake is not slowed down

**PASS**, on the fourth attempt, after a real environmental blocker was found and cleared (see Setup notes).

**Attempts 1–3 (blocked, not counted toward the run):** cold launch, a second cold launch, and a third after bouncing D-POCO's Bluetooth all ended in `MainActivity.endAutoConnectIfExpired | Auto-connect: nothing answered this attempt` roughly 90 s–2.5 min in, with the poke and HFP hold-down succeeding every time but no `NativeAA: Connection accepted from` (attempts 1–2) or an accept with no handshake progress (attempt 3) — D-POCO's own log showed `WIRELESS_SETUP_CAR_BLUETOOTH_DISAPPEAR_BEFORE_WSEM` in every case. Resolved by forgetting the vehicle in Gearhead's own settings and accepting the one-time "Welcome to Android Auto" re-consent dialog on the next poke cycle (both via `uiautomator dump` + `input tap`, minimum taps, no scriptable trigger for either exists).

**Attempt 4 — first bring-up (cold launch):**

- **No `NativeAA: the phone has refused this network N times in a row, so the next wake waits` line anywhere in this run** (0 matches across both required bring-ups combined).
- `NativeAA: SUCCESS - Providing credentials` at 08:05:47.473 → `NativeAA: Attempting manual poke to POCO X3 NFC...` at 08:05:47.563 — **90 ms**.
- `NativeAA: Connection accepted from POCO X3 NFC` at 08:05:48.664.
- Launch (`ConscryptInitializer.initialize`, 08:05:43.519) → `WirelessServer: Incoming connection detected` at 08:05:56.531 — **13.01 s**.
- `Handshake: SSL handshake complete` at 08:05:57.002; `First frame rendered (hardware decode)` at 08:06:02.417 — **18.9 s** launch-to-render.

Ended cleanly (`headunit://disconnect`, confirmed `Final group removal success`), then force-stopped and relaunched fresh for the second bring-up (PID changed 22017 → 22206, confirming a genuinely new process rather than the same task resumed).

**Second bring-up (fresh relaunch):**

- **No wait line again** (same 0-count capture covers both).
- Credentials (08:07:11.174) → poke attempt (08:07:11.294) — **120 ms**.
- Launch (08:07:06.059) → `Incoming connection detected` (08:07:19.452) — **13.39 s**.
- `SSL handshake complete` 08:07:19.963; `First frame rendered` 08:07:24.567.

**On the comparison the brief asks for:** round 6's own 9.67 s baseline was measured from a re-arm *trigger* event mid-session (a settings-screen close), not from a cold app launch, so the 13.0 s / 13.4 s figures above are not on the same starting line as that number and are reported as their own measurement, not as a regression against it.

---

## J1r. The refusal widening, now waited where it can be reached

**PASS, all four conditions.**

Lever: `s1_wifi_holddown_tight.sh` (D-POCO's WiFi disabled on a 3 s cadence), the same lever round 6 used, started right after a fresh launch.

- **`NativeAA: the phone has refused this network 2 times in a row, so retries are slowing down` fired exactly once**, at 08:08:56.937.
- **First `so the next wake waits <M>ms` line, quoted exactly:**
  > `NativeAA: the phone has refused this network 1 times in a row, so the next wake waits 14996ms.` — 08:08:32.614
- **Gaps of at least 30 s from the second refusal:** 39.3–39.4 s for refusals 2–4 (well past the 30 s floor).
- **Gaps of at least 120 s from the fifth:** 129.9–159.1 s for refusals 5–11 (well past the 120 s floor).

**Full widening sequence** (11 refusals observed, one-shot notice at #2 only, every subsequent line is the new per-wake wait line):

| refusal # | declared wait |
|---|---|
| 1 | 14996ms |
| 2 | 29996ms |
| 3 | 29995ms |
| 4 | 29997ms |
| 5 | 119995ms |
| 6 | 119996ms |
| 7 | 119996ms |
| 8 | 119997ms |
| 9 | 119997ms |
| 10 | 119994ms |
| 11 | 119997ms |

**Poke-to-poke gap table** (`Attempting manual/active poke` timestamps, in order):

| # | timestamp | gap to previous |
|---|---|---|
| 1 | 08:08:20.342 | — |
| 2 | 08:08:47.628 | 27.286s |
| 3 | 08:09:26.967 | 39.339s |
| 4 | 08:10:06.365 | 39.398s |
| 5 | 08:10:45.764 | 39.399s |
| 6 | 08:12:55.680 | 129.916s |
| 7 | 08:15:06.338 | 130.658s |
| 8 | 08:17:17.526 | 131.188s |
| 9 | 08:19:56.601 | 159.075s |
| 10 | 08:22:35.696 | 159.095s |
| 11 | 08:25:14.331 | 158.635s |
| 12 | 08:27:53.406 | 159.075s |
| 13 | 08:28:07.250 | 13.844s |

(Pokes 9–12's ~159 s gaps are inflated by the post-release recovery lag below, not further widening past the 120 s ceiling — the declared wait stayed flat at ~119996ms through refusal 11.)

Measured on request:

```
grep -c "Attempting active poke to device" j1r.txt   # 11 (round 6: 15; different capture window/duration)
grep -c "refused this network"             j1r.txt   # 12 (round 6: 1)
grep -c "so the next wake waits"           j1r.txt   # 11 (round 6: line did not exist)
```

The `refused this network` count of 12 is the one one-shot "retries are slowing down" notice plus the 11 new per-wake `so the next wake waits` lines, which also contain that substring — not 12 separate refusal events beyond what the widening table above already lists.

**Release and recovery:** lever released (script killed, `svc wifi enable` on D-POCO) once refusal 7 was well past. The refusal count kept climbing for several more cycles after release (8 → 11): `dumpsys wifip2p` on D-POCO read `curState=P2pDisabledState` for minutes after the release while station WiFi itself reported `WifiState 1` (enabled) — the same stale-`dumpsys`-read artifact round 6 documented after a rapid WiFi bounce, not a real stuck radio. No session formed on the existing (stuck) group even after ~4 minutes of waiting. A fresh force-stop + relaunch (not part of the existing group's own retry loop) landed a full session within 49 s, satisfying the brief's own loose reading: "if a session forms at all after the release, that is enough."

---

## S1r2. The stale-group recreate, with a lever that holds

**BLOCKED — the lever does not hold on this phone, and this is now precisely diagnosed rather than merely unreached.** Reported per the brief's own contingency ("if the lever still does not hold, report it blocked and stop") rather than retried a third way.

1. **Arming confirmed:** a real session was brought up and rendered normally, then `WifiDirectManager: this group has carried a session, so the join watchdog will not recreate it if the phone leaves` fired at 08:29:10.391 — the branch under test was armed before anything else happened.
2. Forgot the vehicle on D-POCO (single "Google" entry this time, not two — round 5's second stale entry is gone). Left both radios alone. Left the live session running.
3. Ended the live session with **one** `force-stop` of Gearhead on D-POCO (not repeated — the point was to end the current session, not hold Gearhead down) at 08:35:04.977. The head unit detected a clean EOF disconnect at 08:35:35.587 and did **not** remove its own group.
4. **The very next poke cycle answered the phone in full.** Poke succeeded at 08:35:41.793; `NativeAA: Connection accepted from POCO X3 NFC` fired 290 ms later (08:35:42.083) — contradicting the brief's expected "no Connection accepted from" signature for a forgotten vehicle. The RFCOMM accept was followed by a complete handshake (`WifiStartRequest` → `Received Type 2` → `TYPE 3`), a formed session, and streaming video/audio within 15 s (`First frame rendered` 08:35:57.589). No "Welcome to Android Auto" dialog appeared this time — `uiautomator dump` at that moment returned empty (no dialog text of any kind).

**Root cause, from the code, not just the log:** `NativeAaHandshakeManager.kt:2138-2139` resets `pokesSinceLastAccept` (`unansweredPokeCount()`, what `WifiDirectManager`'s stale-group watchdog reads) to `0` the instant `handleHandshake()` is entered — i.e. on every RFCOMM accept, before any handshake byte is exchanged or confirmed. Since Gearhead still opens (and, when actually forgotten, sometimes abandons) that RFCOMM socket each poke cycle, the counter is reset every time it would otherwise climb. Whether the vehicle is fully trusted (this run) or freshly forgotten (the accept-then-abandon pattern seen briefly in this same run before the recreate at 08:35:37.529 — see raw capture), the RFCOMM accept alone is enough to zero the counter. S1r2's "ignored N wake pokes" scenario needs a phone that never touches the RFCOMM socket at all, which this lever does not reliably produce on this phone.

No data toward the run's own PASS/FAIL checklist was produced; this is a blocked run, not a graded one.

---

## W1r. The serve path, on the arm a user can actually reach

**UNTESTABLE — this physical unit cannot host a system WiFi access point at all.** More fundamental than round 6's Bluetooth-flakiness blocker: round 6 got past the version-exchange (the phone answered with a real rejection code) before hitting its wall; this round could not get that far.

**Arming, steps 1–2:** settings written and confirmed by readback (`native-ap-transport=1`, `native-wifi-version-exchange=true`, `hotspot-ssid=Navegadortz2`, `hotspot-password=12345678`, `static-bssid=00:27:15:43:06:6a`, `hotspot-interface=wlan2`, `hotspot-band=1`, `auto-enable-hotspot=false`). Bring-up produced:

```
NativeAA: [TX] Sending WifiVersionRequest (Type 4) v4.2
NativeAA: advertising WPP over TCP at 0.0.0.0:5299
```

— an endpoint went out, satisfying the brief's own stop-here-if-not condition for that one line.

**It went no further.** `de44dc18`'s stricter credential-wait path (from this round's `8441d0445`) then required real AP credentials before completing the handshake:

```
SoftApCredentials: No interface named 'wlan2'. Present: p2p0, sit0, lo, wlan0, ip6gre0, ip6tnl0. Falling back to automatic selection.
SoftApCredentials: No usable access point after 30s. Turn this device's hotspot on before connecting — 5 GHz is strongly recommended, Android Auto video is poor over 2.4 GHz — or switch the Android Auto network transport back to WiFi Direct.
NativeAA: Handshake failed - No WiFi credentials available after 60s wait.
```

**Investigated why, rather than assuming a config typo:** D-SAM returns `ro.radio.noril=yes` — no baseband/RIL hardware at all, a true WiFi-only tablet. Its Settings app exposes a "Tethering and Wi-Fi hotspot" screen only via the hidden `com.android.settings/.Settings$TetherSettingsActivity` component (the standard `android.settings.TETHER_SETTINGS` intent does not resolve on this build), and that screen renders with its header and **nothing else** — no "Portable Wi-Fi hotspot" toggle, no content of any kind under it. No UI path, scriptable or otherwise, was found to bring up a real access point on this unit. `auto-enable-hotspot=false` (the brief's own deliberate setting, so the app does not fight a real hotspot's config) means the app never tries to start one itself either.

The real test (§ "Then run it") was never attempted: there was no live network at any point to cache an endpoint against. **Whether `WifiInfoResponse` went out on the TCP path: no, this was never reached.**

Settings restored afterward (`native-wifi-version-exchange=false`, `native-ap-transport=0`, all four hotspot keys cleared), confirmed by readback.

**Correction to section 5 for future briefs on this unit:** it is not only the withhold-then-dial case that has no path on D-SAM/D-POCO — no hotspot-transport scenario at all can be exercised end-to-end here, because the head unit side cannot host a real access point through any path found. Worth pre-registering as a standing D-SAM capability boundary in `TESTING-TEMPLATE.md` §7a, not re-discovering per round.

---

## Anything the brief did not ask about

- **D-SAM's system clock reads ~12 hours off real time** (AM/PM effectively flipped, same calendar date) — not previously flagged in any round on this unit. All gap arithmetic in this report uses D-SAM's own clock consistently, but a future round correlating D-SAM's log against D-POCO's or the host's wall clock should account for the offset explicitly.
- **`pokesSinceLastAccept` resets on any RFCOMM accept, not on a completed handshake** (`NativeAaHandshakeManager.kt:2139`). This is a plain finding from reading the code to explain S1r2's result, not a proposed fix — the thread should decide whether that reset point is intentional (an accept is itself evidence the phone is reachable) or too early (an accept that never completes a handshake arguably isn't "the phone answering" for the stale-group watchdog's purposes).
- **D-SAM cannot host a WiFi access point through any path found** (`ro.radio.noril=yes`, empty Tethering/Hotspot screen). This closes out any future brief's hotspot-arm step on this specific unit before it starts; the `wpp-over-tcp` thread's own SoftApInfo/5765 MHz reading that supplied round 5's "known-working" hotspot values almost certainly came from a different physical head unit, not this one.
- Both `uiautomator`-driven recipes used this round (forget-the-vehicle, accept-the-re-consent-dialog) are the same ones round 5 first worked out; this round found the *bounds* on Gearhead's Material3 settings UI are stable across a round boundary (same taps worked both times), but the *behavior* after forgetting is not (see S1r2) — worth noting for whichever thread next relies on this lever.
