# hands-free-wake and proto-schema — round 1 results

**Candidate:** fork/fix/audio-sink-and-wireless-bring-up @ 940a6dab0       **Baseline:** setting off on the same APK
**APK md5:** a39cbf99c98b0706c39becd10c774374 (candidate; same APK installed on both head units, confirmed identical by device pull)
**Unit:** D-HU = UNISOC MT50, Android 14. D-SAM = Samsung SM-T230, Android 4.4.2 (API 19). Phones: D-POCO (POCO X3 NFC, Android 15/LineageOS), D-MOTO (Motorola edge 30 neo, Android 14).
**Date:** 2026-09-16

> **Superseded while this round was in progress.** The brief was revised after this round's captures
> were taken: the candidate branch was compacted a third time, tip moved to `885dff2d` (Native AA
> commit `caaceabc`, gate 2059 not 2052), and a fix landed that changes exactly what H2/H3 measured
> below — the escalation gate now also accepts a phone address stored in `last-connected-native-mac`,
> not only one that opened an Android Auto channel in the current app process, which was precisely the
> in-memory-only gap this round's H2 Setup notes traced through three discarded attempts before
> landing a clean FAIL. H2's window is also now 6 minutes (was 5), and a new H5 (reboot survival) run
> was added that is ungradable on any build before this one. **Everything below graded `940a6dab0`,
> which no longer exists on the branch.** The H2/H3 FAIL and the mechanism behind it are still real,
> useful findings — they very likely drove the fix that superseded them — but they are not a verdict on
> the current candidate. A fresh round against `885dff2d` is needed to grade the fix itself; this file
> is left as the historical record of what led to it, not re-run in place.

> **Corrected 2026-09-17, after a code review of what this round actually graded.** The block above
> says the fix changes "exactly what H2/H3 measured below". It does not, and reading it that way would
> void two findings that still stand. **H2 was well formed.** The Setup notes' own sequence made
> `everAcceptedAaConnection` genuinely true, the escalated wake fired at 90.0s, and `socket.connect()`
> succeeded. `pairingHasRunAaHere` moves *when* the escalation may fire, never what happens once it
> has fired. So H2 and H3 are verdicts on the candidate's behaviour, not on a build that could not
> reach the code. What the fix does retire is this round's **methodology** cost: the stored MAC
> survives both a process restart and the `ACTION_START_WIRELESS_SCAN` instance replacement traced
> below, so a later round flips the setting and relaunches instead of constructing a no-restart run.
>
> **H3's five minutes has a mechanism, and it is in our code rather than in the phone.**
> `MAX_ESCALATED_WAKES = 2` bounded nothing: once the escalated wake took the slot the guard had no
> hands-free link left to defer to, `handsFreeStandDownSince` was cleared, and the ordinary retry loop
> poked freely, which is the nine further pokes recorded under H2, each holding the slot 15s of every
> 30. The phone never had an uninterrupted window in which to restore its link.
>
> **Both are answered on the branch** (tip `c79904eb`, gate 2063). The setting ships **off** by default,
> which is this round's own pre-registered consequence of an H3 FAIL, and an escalated wake left
> unanswered for `HandsFreeWakeEscalationPolicy.POKES_AFTER_ESCALATION` pokes now stands that device
> down for the rest of the arming, so the slot is given back. `hands-free-wake-round2-brief.md` grades
> both, and grades the wake on **D-MOTO**, which this round never asked. Everything outside the H runs
> here is DONE and is not re-run.

## Setup notes

- Scripts used: `run_unit_tests.sh` (gate), `build_hur.sh` via `install_and_launch.sh` (build+install D-HU), `install_and_launch.sh` with `SKIP_BUILD=1` (install D-SAM), `set_hu_settings_host.py` (all D-HU settings writes, chosen over `set_hu_pref.sh`/`set_hu_prefs.sh` because it can write `<set>` keys like `native-poke-bt-macs` and scalars in one atomic pass), `s1_wifi_holddown_tight.sh` (G1).
- **D-HU's `connection-modes` set held only `{usb}`, leftover from an earlier round.** `WirelessSelectionPolicy.refusesBringUp` (`WifiLauncherManager.kt:111-116`) refuses ALL wireless bring-up, silently, when WiFi is not in that set. This is not one of the brief's listed settings, but without correcting it (`connection-modes={usb,wifi}`) nothing in this round could run at all — logged as `WifiLauncher: wireless bring-up requested, but WiFi is not one of the chosen connection modes`. Corrected once at the top of block 1 and left as `{usb,wifi}` for the rest of the round.
- **D-HU's `native-poke-bt-macs` pointed at D-MOTO's MAC (`A0:46:5A:97:E4:95`)**, leftover from a prior round's block 3, while `last-connected-native-mac` already read D-POCO's. Repointed `native-poke-bt-macs` to D-POCO (`DC:B7:2E:5E:4E:59`) before block 1, per the brief's repoint rule. D-SAM's own `native-poke-bt-macs` already read D-POCO correctly, no repoint needed there.
- D-HU `shared_prefs` directory confirmed `u0_a174:u0_a174` (not root-owned) before the first write — the root-owned gotcha from an earlier thread does not apply this round.
- Dates checked: D-HU `date` and D-SAM `date` both read 2026-09-16 21:39 local (-05 / COT, same offset) against the host — confirms the brief's note that D-SAM's clock no longer needs a correction.
- Both phones confirmed idle at their home launcher before starting (`mCurrentFocus`).
- **Evidence files in `evidence/hands-free-wake-and-proto-schema-round1/` are trimmed to `OPENHU`-
  tagged lines with the highest-volume VERBOSE media/SSL trace spam stripped out**, not raw `logcat -v
  time` dumps. The raw captures this round ran 6-78MB each (VERBOSE across a live video session), far
  outside every other evidence file on this branch (KB to low-single-digit-MB); committing them as-is
  would have permanently bloated the branch's object store. The trimmed files still carry every
  decisive line with its timestamp; nothing narrative was cut, only per-video-frame trace noise.

## R0 — Gate

**PASS**

- `./gradlew :app:testGithubDebugUnitTest` (via `run_unit_tests.sh`): **2052 tests, 0 failures, 0 errors** (summed from `app/build/test-results/**/*.xml`), matches the brief exactly.
- Built via `build_hur.sh`/`install_and_launch.sh`; installed on both D-HU and D-SAM. Pulled the installed `base.apk` back off each device and md5'd it against the local build output: both **a39cbf99c98b0706c39becd10c774374**, identical to each other and to the freshly built APK — confirms "one APK for the whole round" per the brief.

## G1 — Round 7 still holds on a branch that grew

**PASS, with a timing finding worth carrying forward**

- Cold bring-up: session formed and projected in ~18s from launch (`createGroup SUCCESS` 21:45:13.478 → `SSL handshake complete` 21:45:31.351 → Media Sink Setup Requests on VIDEO/AUDIO* channels 21:45:32.3xx).
- `s1_wifi_holddown_tight.sh` started against D-POCO's WiFi at 21:45:51. Released after 4 occurrences of `so the next wake waits` had been logged (grep count reached 4 at 21:48:41), per the brief's stop condition.
- `grep -ac "so the next wake waits"` = **4**. `grep -a "retries are slowing down"` fires **once**, at the second widening tier (21:48:23.726, "the phone has refused this network 2 times in a row") — matches the brief's "fires once at refusal 2".
- **Finding, not a FAIL:** the widening never reached round 7's reported 15s/30s/120s ladder in this capture, because the phone's own AA stack actually completed **4 distinct full sessions** during the intended hold-down window (4 unique SSL session IDs at 21:45:31, 21:46:39, 21:47:27, 21:48:57 — a fresh TCP/SSL handshake every time despite `svc wifi disable` being reissued against D-POCO every 3s). Each landed session calls `resetJoinRefusals()` (`NativeAaHandshakeManager.kt:2436`, the `WppAction.CompleteSuccess` path), which put the refusal counter back to 0 mid-ladder three times before it finally reached "2 in a row" once. So the sequence measured is 1→(reset)→1→(reset)→1→(reset)→2, not a clean climb — the *mechanism* (doubling from 15s to 30s once the count actually reaches 2) is intact and matches round 7's shape, but the 3s cadence in `s1_wifi_holddown_tight.sh` did not reliably starve this particular phone/AP pairing from completing a real join, unlike what the standing template's D-POCO note describes ("a sustained repeat-disable loop... the radio stayed off for several minutes"). Worth a note for whoever next needs a hard starve on this phone: watch for `SSL handshake complete` recurring during the hold-down and treat it as the counter resetting, not as noise.
- Credentials-to-poke gap, cold bring-up: `SUCCESS - Providing credentials` 21:45:13.830 → `Attempting active poke` 21:45:13.899, **69ms**.
- Discard-rule check: one `createGroup SUCCESS` only (no second group), one `p2p-wlan0-0` interface throughout, no `Magic Garbage`. The 4 SSL handshakes are themselves the finding above, not a contamination of this run — G1 is a bring-up + backoff check, and repeated real sessions are what the backoff is reacting to.

## H0-H4 methodology deviation (read before the results below)

The brief's step 2 for H0 ("End it from the head unit — user exit") does not produce a usable
precondition on this rig: `headunit://disconnect` tears the P2P group down (`WifiDirectManager.stop`,
"Final group removal success") and the app deliberately refuses to re-arm Native AA after a real user
exit. Section 5's own lever (cycling **D-HU's** own Bluetooth adapter) does restore the phone's HFP
link to `Connected` (confirmed via `dumpsys bluetooth_manager`), but the retry loop that would print
`Not poking` never restarts on its own — nothing re-arms it, so neither of the brief's two anticipated
H0 outcomes occurs at all.

**What was used instead, confirmed with the user first:** end the live session by disabling Gearhead
on D-POCO (`pm disable-user`) rather than a head-unit-side user exit. This produces an ordinary
`session state disconnected (link_lost)` (not `user_exit`), which does **not** set the app's
`userExitedAA` veto, so `NativeAaHandshakeManager`'s retry loop keeps running on its own, in the same
process, indefinitely — exactly the "Not poking ... hands-free" cadence the brief's PASS condition
describes. Disabling Gearhead also stops it self-healing the connection within seconds on its own
(confirmed separately: with Gearhead merely force-stopped, not disabled, it relaunched and formed a
full new session in under 3s on two separate occasions — too fast for any 90s window to be observed).

**A second, load-bearing subtlety for H2/H2' specifically:** the escalation gate
(`HandsFreeWakeEscalationPolicy.shouldEscalate`) requires `phoneEverOpenedAaChannel`
(`everAcceptedAaConnection` in `NativeAaHandshakeManager`), an **in-memory, per-instance** flag set
only when a real session has landed in *that exact manager instance*. It is not settings-backed and
does not survive the instance being recreated. Both a full app process restart (needed to flip a
setting) and `AapService`'s own `ACTION_START_WIRELESS_SCAN` handler (`setActiveFromSettings(force =
true)`, which calls `WifiLauncherMode.factory()` and replaces the active launcher/manager object even
within the same OS process) silently reset it to `false`. Getting a valid H2/H2' measurement requires,
in one unbroken sequence with **no** app restart and **no** forced re-init in between: (1) launch with
the candidate setting already written, (2) let one real session land (sets the flag), (3) end that
session via a non-user-exit disconnect (`link_lost` preserves the flag; the same manager instance's
`stop()` explicitly keeps `everAcceptedAaConnection` while resetting everything else), (4) disable
Gearhead to hold the precondition, (5) observe — with **no** settings write and **no** relaunch from
step 1 onward. The first two H2 attempts (a fresh relaunch mid-sequence; then `ACTION_START_WIRELESS_
SCAN` to avoid a relaunch) both silently measured `phoneEverOpenedAaChannel = false` and could never
have escalated regardless of the setting; this cost three discarded attempts before landing on the
sequence above. Baseline runs (H0/H1) are unaffected by this because `enabled = false` short-circuits
`shouldEscalate` before that flag is even read.

This adds `native-driver-selection-mode = 0` (DISABLED, restored after) to the settings block for all
H runs: with it left at its default (`AUTO`), a cold app launch with exactly one known phone calls
`HomeFragment.checkNativeDriverSelectionOnStartup() -> selectDriver() -> manualPoke()`, which **also**
correctly honours the hands-free guard (logs `Not poking`, matching the fix's intent) but stops
retrying after 3 cycles (~30s) instead of continuing every ~15s indefinitely — a real, separate,
minor finding on this app's cold-launch auto-poke path, but it made a plain fresh-launch H2 attempt
silently unable to ever reach the 90s mark. Filed here rather than as its own numbered run because it
was discovered as a confound, not briefed.

### H0. Reproduce the reporter's failure on this rig

**PASS (fault reproduced).** Baseline arm (`native-aa-wake-over-hands-free-link=false`). Precondition
established per the deviation above (Gearhead disabled while idle, HFP confirmed `Connected`), then a
clean 5-minute capture from a fresh stand-down start (22:07:24-22:12:24).

- `Not poking POCO X3 NFC ... already holds a Bluetooth hands-free link` fires **21 times**, ~15s
  cadence, for the full 5 minutes.
- `Connection accepted from` count: **0**. HFP still read `Connected` at the end.

### H1. The setting off changes nothing

**PASS.** Same arm, same precondition, a second independent 5-minute capture (22:13:05-22:18:05),
same process, no restart in between.

- **21** `Not poking` lines, identical ~15s cadence to H0.
- `Connection accepted from`: **0**. `despite the hands-free`: **0**, anywhere.

### H2. The escalated wake reconnects the phone

**FAIL**, on the well-formed measurement (see the methodology note above for the two discarded
attempts). Candidate arm (`native-aa-wake-over-hands-free-link=true`). Sequence: fresh launch 22:57:58
→ real session landed via one phone-BT toggle (SSL handshake 22:59:43) → session ended via Gearhead
`force-stop` + `disable-user` at 23:00:01 (`session state disconnected (link_lost)`) → HFP already
read `Connected` (no head-unit-adapter cycle needed this time) → stand-down began naturally (first
`Not poking` at 23:00:06, no re-arm action taken) → 5-minute capture to 23:05:11.

- **1 arming**, **12** `Not poking` lines before the escalation.
- The escalated wake fires at **23:01:36.726**, exactly **90.0s** after stand-down start (23:00:06) —
  the threshold logic itself is correct. Log line: `waking POCO X3 NFC ... despite the hands-free
  link — it has not started Android Auto in 90s ... Wake 1 of 2`.
- `Calling socket.connect()` follows immediately (23:01:36.739) and **succeeds** (`Successfully poked
  ... Holding 15000ms`) — the wake reaches the radio and takes the phone's hands-free slot (confirmed:
  the very next poke's own log reads "the phone holds no connection to this unit", i.e. our poke just
  took it).
- **`Connection accepted from`: 0, for the rest of the capture** (the poke repeats roughly every 30s
  afterward, 9 more times through 23:05:33, all succeeding at the socket level, none ever answered).
  Only 1 of the 2 available escalated wakes was used in the window — after the first, the guard no
  longer saw a hands-free link to defer to (we had just taken it), so later pokes went out as ordinary
  pokes rather than needing a second escalation.
- This is exactly the brief's pre-registered FAIL shape: "wakes go out, socket.connect() succeeds, and
  no Connection accepted from follows either of them. That is the filter_profile_connection_by_acl
  case." D-POCO (Android 15, LineageOS/`surya`) appears to have that Android Auto flag on, or an
  equivalent behavior: taking its hands-free slot does not make Gearhead open the AA Bluetooth
  channel.

### H3. What the wake costs the hands-free link

**FAIL.** Runs on H2's own capture and the phone state immediately after. HFP read `Disconnected` at
the very next check (a few seconds after the wake) and was **still `Disconnected` at 23:06:48** — over
**5 minutes** after the 23:01:36 wake, well past the brief's 3-minute FAIL threshold and past the
"3 to 8 minute outage" it references from an earlier round. The link did not recover on its own within
the observed window. Combined with H2's FAIL, the escalated wake as it stands costs the phone's
hands-free slot for an extended, unbounded-looking period **without** achieving its purpose on this
phone — the brief's own instruction applies: "If H2 fails, do not iterate on the wake; the next lever
is an adapter cycle."

### H4. A healthy first connect is not disturbed

**PASS.** Candidate arm, fresh app process with no prior session. Cold launch 23:07:18 (Gearhead
re-enabled) → phone-BT toggle to clear the stale `Disconnected` HFP state left over from H3 → session
formed normally, `SSL handshake complete` at 23:07:27 (9s from launch) → `Incoming connection detected`
at 23:07:27.417. `despite the hands-free` count over the whole capture: **0**. The fix does not touch
an ordinary first connect.

### S1. The stale group, with the counter that round 7 found stuck

**INCONCLUSIVE.** Followed the brief exactly (real session rendered, "this group has carried a
session" confirmed logged, vehicle forgotten on D-POCO via `uiautomator` — Settings search "Android
Auto" -> Settings -> Vehicles -> Google -> Forget, confirmed "Accepted vehicles: None" afterward — then
**one** `am force-stop` of Gearhead, 8-minute capture 23:11:31-23:19:56).

The phone answered a poke and formed a **full new session** (`Connection accepted from` at
23:11:33.243, `session state projecting` by 23:11:36.040) just **1.3 seconds** after the single
force-stop — the same self-heal behavior noted in the methodology deviation above, this time surviving
even a single `force-stop` rather than needing `disable-user`. The phone never actually ignored a poke,
so `pokesSinceLastAccept`/"has ignored N wake pokes" was never exercised in either direction. This is a
rig/phone timing limit (Gearhead's own reconnect is faster than a single force-stop can suppress it),
not evidence about the counter fix itself; round 7's original finding (a bare RFCOMM accept, not a
landed session, was zeroing the counter) is unchanged from a code read but not re-confirmed on
hardware this round.

### P3. Control: both proto settings off

**PASS.** Fresh session 23:21:12-23:21:30 (`headunit://disconnect` to end). `SSL handshake complete`
x2 (one retried sub-handshake, harmless), `Media Sink Setup Request: 3 on channel VIDEO` fired (session
projected). Neither `Asking for ping timeout` nor `WifiVersionRequest (Type 4)` appears anywhere —
confirms both settings are genuinely off before grading P1/P2.

### P1. Field 3: the version exchange on

**PASS**, three cold bring-ups, `native-wifi-version-exchange=true`, transport left on WiFi Direct
(`native-ap-transport=0`):

| Run | Reached picture | `WifiVersionResponse` |
|---|---|---|
| 1 (23:21:52) | yes | `v4.2 status=NO_SUPPORTED_WIFI_CHANNELS(-8) channelType=0` |
| 2 (23:22:36) | yes | identical |
| 3 (23:23:02) | yes | identical |

All three: `not advertising WPP over TCP: this unit gives its WiFi Direct group a new address on every
create` fires as expected on WiFi Direct (no FAIL/stop condition). `channelType=0` is **present**, not
absent, on every run (a data point for the audit, not one this round can interpret further). `status=
-8` on all three matches the brief's own note that 19/19 prior captures read `-8` on sessions that went
on to connect — "a P1 that changes nothing is a result, not a failure."

### P2. `ConnectionConfiguration`: a longer ping timeout

**PASS (reduced form, as the brief pre-registers for this rig).** `native-wifi-version-exchange=false`,
`announce-connection-configuration=true`. Session formed 23:23:52, `[ServiceDiscovery] Asking for ping
timeout 15000ms and 65536B socket buffers` fired at 23:23:53.854 (own ask, before the phone's own
`Handshake: Version response received: the phone selected 1.7 (we asked for 1.2)` — ordering matches
the brief's note that this is not a reply to it). Held **15m36s** (23:23:53-23:39:29) with
`hur-wifi-test-scripts/mock_drive.sh` feeding a real OSRM road route to D-POCO's mock GPS provider
throughout, ended with a clean `headunit://disconnect`.

- `station scans:` fired **6 times**, cadence ~160s each, throughout the hold (23:24:45, 23:27:25,
  23:30:06, 23:32:46, 23:35:26, 23:38:06).
- Session survived every one of them — no reconnect, no second `SSL handshake complete` beyond the
  initial pair, `dropped=0 skipped=0` on every `Throughput over 5000ms` sample taken across and
  between scan windows (fps varied 13-21 depending on scene motion, consistent with normal video
  content variance, not a stall).
- D-HU's own `WifiScanner` still does not join an ordinary network (unchanged from the brief's
  pre-registered caveat), so this is the "asking costs nothing" deliverable, not confirmation the
  phone honours field 16's longer timeout — that remains open per the brief's own section 7.

## Block 3: D-HU with D-MOTO

Repointed `native-poke-bt-macs` and `last-connected-native-mac` to D-MOTO's MAC (`A0:46:5A:97:E4:95`)
before the first run, read back. **D-MOTO was PIN-locked**; the user unlocked it on the physical
device (no scriptable path exists for that). Restoring D-HU's settings from the block-1/2 backup
after block 2 silently reverted `connection-modes` to `{usb}` again — the same leftover-state gotcha
from the top of block 1, refixed to `{usb,wifi}` before P1'' would run at all.

**Two more blockers, both matching patterns already seen this round:**

- **D-MOTO's Bluetooth was off** (`settings get global bluetooth_on` → `0`), causing the same
  `read failed, socket might closed or timeout` poke failures seen on D-SAM. Fixed with `svc
  bluetooth enable`.
- **The same `WIRELESS_SETUP_CANCELLED_ALREADY_STARTED` lock hit D-MOTO's Gearhead** during W1's
  first attempt. Root-caused live, at the user's prompt (who asked directly whether disabling
  `native-driver-selection-mode` was itself the cause): the first W1 attempt raised D-HU's on-screen
  driver-selection prompt (two known phones registered), and rather than tapping through it the
  session was abandoned (driver selection disabled instead, app relaunched) — leaving Gearhead's own
  wireless-setup state machine on D-MOTO mid-attempt. Confirmed via the phone's own `GH.ConnLoggerV2`
  log: the same session ID kept cancelling as `ALREADY_STARTED` across a `force-stop` of Gearhead
  (same as D-SAM); a `pm clear` on Gearhead cleared it, and the next bring-up landed cleanly. **Not**
  a real behavioral difference between the driver-selection and non-selection poke paths — confirmed
  by the fact that P1'' had already passed three clean bring-ups through the exact same non-selection
  path minutes earlier.

### P1''. Field 3 on a second Gearhead

**PASS**, three cold bring-ups (`native-driver-selection-mode=0` to skip the two-phone selector, since
D-POCO is still a known/paired candidate on D-HU), `native-wifi-version-exchange=true`, WiFi Direct:

| Run | Reached picture | `WifiVersionResponse` |
|---|---|---|
| 1 (00:33:19) | yes | `v4.2 status=NO_SUPPORTED_WIFI_CHANNELS(-8) channelType=0` |
| 2 (00:33:54) | yes | identical |
| 3 (00:34:27) | yes | identical |

All three: correct WiFi-Direct withhold line (`this unit gives its WiFi Direct group a new address on
every create`). `channelType=0` present every time — matches D-HU's own P1 pattern from block 1, a
second Gearhead giving the same shape of answer as the first (contrast with D-SAM's P1', which never
carried the field at all).

### W1. The serve path, on a unit that can host an access point

**PASS.** Hotspot started via `adb shell cmd wifi start-softap OHU-TEST wpa2 testtest1234 -b 5`
(SSID `OHU-TEST`, BSSID `00:27:15:43:06:6A`, 5785 MHz), `native-ap-transport=1`,
`native-wifi-version-exchange=true`, `auto-enable-hotspot=false`, `hotspot-ssid`/`hotspot-password`/
`static-bssid`/`hotspot-interface` (`wlan2`) all filled from the running AP, read off the unit rather
than guessed.

- `advertising WPP over TCP at 192.168.120.179:5299` fired.
- `WppTcpServer: connection from 192.168.120.235` (D-MOTO dialled the endpoint), TLS handshake
  completed (`TLSv1.2, TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256`), `handshake complete; projection
  session is up`.
- The phone joined the access point and a session formed and projected (`Media Sink Setup Request: 3
  on channel VIDEO`).
- **`WifiInfoResponse` (Type 3) went out over the standard Bluetooth path**, not the TCP one — the
  WPP-over-TCP channel on :5299 is a separate control channel the phone dials in addition to the
  normal BT-negotiated handoff, not a replacement delivery path for credentials. It reported
  `projection already up; holding the re-dialled control channel open without a handshake`, i.e. it
  arrived *after* the AAP session had already landed by the usual route and was held open rather than
  driving anything itself. This answers the brief's question but not in the direction it expected:
  round 7 could never reach a served dial at all; this round reaches one, but it is a passenger on the
  Bluetooth handoff rather than the thing that lands the session.

### W2. A stale endpoint is refused rather than served

**PASS**, on the second attempt — the first was contaminated by a leftover `static-bssid` override
from W1 (still pointing at the hotspot's BSSID) that broke the *standard* WiFi-Direct handoff outright
(`WifiConnectStatus status=WIFI_NETWORK_UNAVAILABLE(-11)` on every cycle), so the phone never got far
enough to fall back to its stored WPP endpoint at all in that first 5-minute window. Cleared
`static-bssid` back to `0` (the default "no override") and re-ran cleanly.

`native-ap-transport=0`, hotspot off, relaunched, 5-minute capture:

- The phone dialled the stale `:5299` endpoint in a rapid burst — **63** attempts in about 4.4 seconds
  (00:46:38.8–00:46:43.1) — and **every one** got `WppTcpServer: not serving this dial: this unit
  gives its WiFi Direct group a new address on every create...`, closed, never served.
- In parallel, the **ordinary Bluetooth handshake succeeded**: `session state connecting → connected →
  projecting`, one clean `SSL handshake complete`. The brief's "a Bluetooth handshake may or may not
  follow; report which" — it did, and it is what actually carried the session while the stale endpoint
  was being refused 63 times over.

### Closing block 3

D-MOTO's Gearhead vehicle list already read `Accepted vehicles: None` (a side effect of the `pm clear`
during W1's precondition fix — there was nothing to forget). Settings restored and read back:
`native-wifi-version-exchange=false`, `announce-connection-configuration=false`,
`native-ap-transport=0`, `hotspot-ssid`/`hotspot-password`/`hotspot-interface` cleared, `static-bssid=0`,
`native-driver-selection-mode=1` (default). Hotspot stopped (`cmd wifi stop-softap`).

## Anything the brief did not ask about

- **`WIRELESS_SETUP_CANCELLED_ALREADY_STARTED` is a real, repeatable Gearhead failure mode this round
  ran into on *two separate phones* (D-POCO for block 2, D-MOTO for block 3), each time traced via the
  phone's own `GH.ConnLoggerV2` log rather than anything in our app's log. It presents as our app
  correctly poking and the phone's Bluetooth accepting, but Gearhead's own wireless-setup state
  machine treats every attempt as a duplicate of one still "in progress" and cancels it instantly —
  indefinitely, surviving a Gearhead `force-stop` and, on D-POCO, a full phone **reboot**. Only
  `pm clear` on `com.google.android.projection.gearhead` cleared it, both times. Both instances trace
  to a wireless-setup attempt this round itself left dangling (once from a stuck first-boot flow on
  D-SAM, once from an abandoned on-screen selection prompt on D-MOTO) — self-inflicted, but the fact
  that neither a targeted force-stop nor a full reboot could clear it, and only wiping Gearhead's data
  could, seems worth knowing for whoever next needs to recover a phone stuck this way without wanting
  to lose its other Gearhead state.
- **D-SAM's Android version (API 19) cannot exercise the hands-free wake guard at all**, because
  `BluetoothHelper.handsFreeLinkState()` reads `BluetoothProfile.HEADSET_CLIENT`, a profile role added
  to Android's Bluetooth stack in API 24. This is a genuine, permanent scope gap between the brief
  (which names D-SAM as "the second unit for the wake... whose Android is old enough to match the
  reporter's") and what that unit can actually test: it is old enough to match the reporter's Android
  version, but too old to run the guard the fix is built around. Worth flagging back before a future
  round repeats the attempt.
- **The `connection-modes` leftover-state gotcha (block 1's Setup notes) recurred after restoring
  D-HU's settings from backup between blocks**, since the backup itself carries the same stale
  `{usb}`-only value. Anyone restoring this exact backup file for a future round will hit the same
  silent no-op bring-up unless `connection-modes` is fixed again by hand.
- **`native-driver-selection-mode=AUTO` (the default) auto-pokes and pins the sole known phone on cold
  launch via `selectDriver()`, and that pinned retry path stops retrying after 3 cycles (~30-45s)**
  instead of continuing indefinitely like the ordinary retry loop. It correctly honours the hands-free
  guard while it runs (logs `Not poking`, same as the ordinary path), so it is not itself a regression
  in the fix under test, but it silently defeats any H-run methodology that involves a plain app
  relaunch with more than one known phone in `native-poke-bt-macs`/pairing history. Setting
  `native-driver-selection-mode=0` for the duration of H-run testing avoids it; nothing else in this
  round's candidate branch touches this path.

D-SAM's `date` read `Wed Sep 16 23:41:58 COT 2026` against the host's matching local time — confirms
the brief's note that no 12h correction is needed from this date. Battery at 100% (unlike the 9% seen
in earlier rounds on this unit). `connection-modes` already included `wifi` and `native-poke-bt-macs`
already pointed at D-POCO — no repoint needed this block. `set_prefs_runas_host.py` (the multi-key
run-as sibling of `set_hu_settings_host.py`) was used for every D-SAM settings write.

**Two blocking issues, both resolved, neither anticipated by the brief:**

1. **D-POCO's Bluetooth had been left disabled** from the very end of block 1's tooling (a leftover
   `svc bluetooth disable` from an earlier diagnostic never re-enabled), causing every D-SAM poke to
   fail at the socket level (`read failed, socket might closed or timeout, read ret: -1`). Fixed with
   `svc bluetooth enable`.
2. **Gearhead refused every D-SAM setup attempt** with `WIRELESS_SETUP_CANCELLED_DIFFERENT_BLUETOOTH_
   DEVICE` (from the phone's own `GH.ConnLoggerV2` log, not our app's), even with D-HU's own radio
   disabled — a cached preference for D-HU from block 1, not a live race. **Forgetting D-HU's
   Bluetooth pairing on D-POCO** (Settings > Bluetooth > Navegadortz2 > Forget, `uiautomator`) cleared
   that specific cancellation, but exposed a second, deeper one: `WIRELESS_SETUP_CANCELLED_ALREADY_
   STARTED`, repeating on every single attempt (1000+ events accumulated) and surviving both a
   Gearhead-only `force-stop` and a full **phone reboot** — meaning the stuck lock lived outside
   Gearhead's own process, in Play Services/CDM state. Confirmed with the user first, then **`pm clear`
   on `com.google.android.projection.gearhead`** cleared it outright; the very next bring-up landed a
   full session with live video/audio.

**A third finding, this one in the app's own code, explains why H0'-H4' could not be run at all on
this unit** (confirmed with the user, who separately confirmed `native-aa-complete-hfp-slc` — "Complete
the Bluetooth connection" — was already on, which ruled that setting out as the cause):
`BluetoothHelper.handsFreeLinkState()` (`BluetoothHelper.kt:109-116`), called with
`includeGatewayRole = false` from `NativeAaHandshakeManager.pokeDevice()`, checks only
`BluetoothProfile.HEADSET_CLIENT` (int constant `16`). That profile role was not added to the public
Android Bluetooth stack until **API 24** (Nougat); D-SAM runs **API 19**. So
`adapter.getProfileConnectionState(16)` can structurally never report `STATE_CONNECTED` on this unit,
no matter how long the RFCOMM/AT-command hands-free link is actually held — confirmed live:
`dumpsys bluetooth_manager` read the HFP `StateMachine` as `Connected` while the app kept logging
`Attempting active poke` on its ordinary ~30s cadence rather than ever reaching `Not poking ...
already holds a Bluetooth hands-free link`. The guard this whole round exists to test is therefore
**unreachable on D-SAM by Android version, not by rig setup** — a real scope gap in the brief, which
names D-SAM as "the second unit for the wake" without accounting for this.

### H0'-H4'

**UNTESTABLE**, for the reason above. `BluetoothWakePolicy.wakeDecision`'s `clientRoleLink` can never
read connected on API 19, so `decision.poke` is always `true` and the retry loop never reaches the
`Not poking` branch the brief's PASS/FAIL conditions are written against, regardless of arm. This is
not a rig timing limit like S1's finding in block 1 — it is a permanent property of this unit's
Android version, worth flagging back to whoever briefs the next round naming D-SAM for hands-free
work.

### P1'. Field 3 on D-SAM

**PASS**, three cold bring-ups, `native-wifi-version-exchange=true`:

| Run | Reached picture | Withhold-line wording | `WifiVersionResponse` |
|---|---|---|---|
| 1 (00:26:17) | yes | "have not yet been seen to repeat on this unit... The next bring-up decides" | `v4.2 status=NO_SUPPORTED_WIFI_CHANNELS(-8)`, **no `channelType=` field at all** |
| 2 (00:27:08) | yes | same as run 1 | identical |
| 3 (00:27:47) | yes | **"this unit's Android is too old to name its own WiFi Direct group"** — the brief's expected graded wording | identical |

Two things worth carrying forward: the withhold line took two runs to settle into the graded wording
(the first two read a different, not-yet-seen-to-repeat variant the brief also names as acceptable on
an early bring-up); and D-SAM's `WifiVersionResponse` **never carries a `channelType=` field at all**,
unlike D-HU's `channelType=0` on every run in block 1 — a second phone/unit giving a different-shaped
absence than block 1's, which is itself the finding P1 warned to expect. P2 and S1 skipped on this
unit per the brief (no station to join, no watchdog arm to grade here).
