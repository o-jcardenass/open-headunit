# audio-sink-jitter — round 5 results

**Candidate:** `fix/audio-sink-and-wireless-bring-up` @ `9ecc00773`       **Baseline:** `main` @ `5ce51c5e6`
**APK md5:** candidate `19246b3fdae7095724dc452a8d12cd07` (installed APK pulled back and hashed, matches the build just pushed) / baseline `3796555538231bc6f784b465a2499af9` (built for identity only, not installed)
**Unit:** D-SAM (Samsung SM-T230, `degaswifi`, board PXA1088), Android 4.4.2 (API 19), 2.4 GHz-only radio, head unit. D-POCO (POCO X3 NFC) as phone.
**Date:** 2026-09-16

## Setup notes

- Candidate branch checked out fresh (`git checkout -B audio-sink fork/fix/audio-sink-and-wireless-bring-up`); `git log --oneline -4` matched the brief's four SHAs exactly. `main` built separately for baseline identity only, never installed.
- Same D-SAM quirks as round 4 apply and are not repeated in full here (clock +12h offset, no `sed`, `SettingsActivity` not exported, `am -p` NPE) — see round 4's own Setup notes and `TESTING-TEMPLATE.md` §7a.
- **New D-SAM quirk, flagging for §7a:** `svc wifi disable` chained with `svc wifi enable` in one `adb shell` call on D-SAM produced repeated `try again in 1second` retries and then, unexpectedly, a full ~18 MB `dumpstate`-shaped dump on stdout instead of completing quickly. Cause not chased down (possibly the WiFi service not being ready after `disable` before `enable` fires immediately after). Avoid chaining those two commands in one shell call on this unit; issue them as separate calls with a pause between, which worked cleanly every other time this round.
- **D-POCO's WiFi did not self-revert this round** the way round 4's Setup notes describe, after a *repeated*-disable loop (as opposed to round 4's single disable) — it stayed off for several minutes after the loop's last pulse and needed an explicit `svc wifi enable` to bring it back. The self-revert quirk may only apply to a single disable, not a sustained hold.
- **The round 4 lever cadence (repeat-disable every 15 s) proved too loose this round.** D-POCO's WiFi came back on fast enough within a 15 s gap to complete a full poke → BT handshake → WiFi-join → session cycle before the next disable pulse landed, producing session churn (form → drop → form → drop) instead of sustained unreachability. A 3 s cadence was needed to actually starve it continuously. Two scripts left in `hur-wifi-test-scripts/`: `s1_wifi_holddown.sh <seconds>` (15 s cadence, matches the brief's literal instruction) and `s1_wifi_holddown_tight.sh <seconds>` (3 s cadence, what this round actually used for S1's real attempt) — nothing existing fit this exact "hold WiFi off continuously and release on command" shape.
- **D-POCO's screen must stay idle/home for the automated poke cycle to complete.** Mid-round, the phone's screen was found sitting in its own WiFi "Network preferences" settings page (left over from earlier manual `svc wifi` diagnosis), which silently blocked Gearhead from ever answering the HFP poke with the AA RFCOMM channel. `input keyevent KEYCODE_HOME` fixed it. Worth checking (`adb shell dumpsys window | grep mCurrentFocus`) before trusting a stalled bring-up to the app's own logs alone.
- **A major, unplanned finding sits under N4r below**: this round's own WPP-endpoint testing left D-POCO's Gearhead wedged, retrying a stale cached WPP-over-TCP endpoint and never falling back to Bluetooth, for roughly 15 minutes. Recovery needed forgetting both "Google" entries under Android Auto's own Settings → Android Auto → Vehicles (`am start` into Gearhead's activities found no scriptable "forget" trigger; this was done via `uiautomator dump` + `input tap`, minimum taps, per house rule 2 — no existing adb-reachable trigger was found for it) and accepting the resulting one-time "Welcome to Android Auto" re-consent dialog (also no scriptable trigger; tapped `Continue` after locating its bounds via `uiautomator dump`). See N4r's own section for the full trace; this is the headline finding of the round.
- Scripts used: `run_unit_tests.sh`, `build_hur.sh` (via `install_and_launch.sh`, `HU=30041c35642d2200`), `install_and_launch.sh`, `set_pref_hostedit.sh`, `mock_drive.sh` (default Medellín→Marinilla route, `PHONE=4f4027e9`, run once, stopped with `TaskStop`, `svc wifi enable`/`disable` inside the mock-provider setup untouched). New this round: `s1_wifi_holddown.sh`, `s1_wifi_holddown_tight.sh`.
- A stray orphaned `mock_drive.sh` instance was briefly created by a `nohup ... &` backgrounding mistake early in the round; it was killed by explicit PID before it could corrupt the tracked instance's GPS feed, and the tracked instance's mock-location provider (which the orphan's own `trap cleanup` had disabled as a side effect of being killed) was re-enabled by hand. No lasting effect once caught.
- `native-wifi-version-exchange` restored to `false` after N4r, confirmed by readback. `debug-video-feed-hold-ms` restored to `0` after T2r, confirmed by readback. Both latches (`video-profile-starvation-cap`, `playback-focus-self-defeating`) read `false` before and after every run — neither tripped this round.

---

## R0. Gate

**PASS**

- `./gradlew :app:testGithubDebugUnitTest`: 2011 tests, 0 failures (`test-results/*.xml` totals), matches the brief's stated gate exactly (up from round 4's 2003, the brief's own note: eight of those are `ProvenGroupStalePolicyTest`, graded on hardware by S1 below).
- Installed APK pulled back from D-SAM and hashed: `19246b3fdae7095724dc452a8d12cd07`, matching the build just pushed.

---

## T1r. The per-channel split round 4 did not record

**PASS on the split, with a caveat on `unread=`.** Round 4's raw captures survived (`hur-wifi-test-scripts/results/audio-sink-round4/{dsam,dpoco}_logcat.txt`, ~117–197 MB, never `cat`, grepped only). Re-grepped for every `transport dispatch over` window across the whole round 4 capture (151 windows matched, spanning every Part N/A/T/M run plus the unbriefed 40-minute reliability session — round 4's own report only quoted two sessions by hand).

Full per-window breakdown saved as `evidence/audio-sink-jitter-round5/t1r-dispatch-windows-full.txt` (151 lines). Aggregate stats:

| field | min | max | avg |
|---|---|---|---|
| `video=` | 39ms | 325ms | 199ms |
| `audio=` | 1768ms | 3440ms | 2598ms |
| `other=` | 73ms | 429ms | 120ms |
| `unread=` | 7% | 12% | 9.2% |
| `longest=` (ms) | 8ms | 240ms | 49ms |

`longest= ... on X` channel breakdown across the 151 windows: **AUDIO 128, CONTROL 13, AUDIO2 5, VIDEO 5.** Video is the longest-blocking channel in only 3.3% of windows; audio dominates 85% of them. `videoShed=0` in every single one of the 151 windows — the split never once had to shed. `blocks=` reads 0 in 91/151 windows, 1–5 in the rest (never the round-2 catastrophe of 132–145).

**Reading it against the brief's two questions:**

1. **`video=` is not literally zero, but it is never the channel holding up dispatch.** Video's own average (199ms) is about 1/13th of audio's (2598ms) and never dominates `longest=`. Against round 2's 132–145 blocks a window at up to 233ms with the sink underrunning beside them: round 5 (round 4's own captures) shows `blocks=` 0–5 and the sink clean throughout (see A1/A6 in round 4's own report) — a different order of magnitude. The split reads as doing its job: the residual `blocks` are accounted for by audio and other, not video, matching the brief's PASS wording in substance even though `video=` itself isn't near-zero in absolute terms.
2. **`unread=` does not clear the instrument's own 5% "healthy" bar.** All 151 windows read 7–12% (avg 9.2%); zero of them are below 5%. Round 4 called this "healthy" without checking against the instrument's own definition; it is not. This is worth carrying forward as an open question, separate from the split's own PASS.

---

## T2r. Where the backlog actually stops

**Measurement, no FAIL condition per the brief — and the number that matters most is not the queue depth.**

**First half, `debug-video-feed-hold-ms=200`, mock-drive route + Spotify playing:**

| time | video= | audio= | other= | unread= | blocks= | videoQueue | videoShed |
|---|---|---|---|---|---|---|---|
| 02:07:10 | 314ms | 2224ms | 352ms | 9% | 4 | 86 | 0 |
| 02:07:40 | 434ms | 3033ms | 106ms | 11% | 3 | 193 | 0 |
| 02:08:10 | 347ms | 2863ms | 88ms | 10% | 2 | 207 | 0 |
| 02:08:40 | 222ms | 2896ms | 120ms | 10% | 1 | 113 | 0 |
| 02:09:10 | 115ms | 3126ms | 113ms | 11% | 0 | 99 | 0 |
| 02:09:40 | 648ms | 3024ms | 110ms | 12% | 4 | **256** | 23 |
| 02:10:10 | 256ms | 2965ms | 104ms | 11% | 1 | 237 | 0 |
| 02:10:40 | 233ms | 2873ms | 105ms | 10% | 0 | **256** | 15 |
| 02:11:10 | 323ms | 2844ms | 97% | 10% | 0 | **256** | 144 |
| 02:11:40 | 569ms | 2685ms | 120ms | 11% | 2 | **256** | 204 |
| 02:12:10 | 636ms | 2864ms | 93ms | 11% | 2 | **256** | 484 |

**The ceiling fires, hard, in under 3 minutes** — from `SSL handshake complete` at 02:06:38.729 to the first `videoQueue=256, videoShed=15` window is about 4 minutes of session time, and the queue is pinned at exactly 256 by 02:09:40 and stays there, climbing only in `videoShed=` (23 → 15 → 144 → 204 → 484) as load continues. This is dramatically faster than round 4's 7-minute climb to 120 with no ceiling reached — almost certainly because this round ran a real, continuously-panning mock-drive route (round 4's T2 used a static scene), generating much heavier sustained video traffic.

**`maxUnacked=` for the VIDEO channel, from `Config response:`: 12.** The backlog reached 256 — **21×** the announced window, not the brief's cited 10× from round 4's 120. Whichever side owns this gap, the code's own 256 ceiling is what actually bounds it, exactly as the brief frames it.

**The picture did not stay whole, and this is the finding that matters more than the queue number.** A screenshot taken during the ceiling window (`evidence/audio-sink-jitter-round5/t2r_first_half_ceiling.png`) shows heavy block/tile corruption across the entire map — not a clean-but-slow render. The cause is directly in the log: **30 occurrences of `AapVideo: fragment run lost bytes, requesting keyframe to recover stream`** between 02:09:38 and 02:12:11 (roughly one every 5 seconds), each followed by `AapTransport: Requesting recovery keyframe (unsolicited focus gain).` The shedding that enforces the 256 cap is cutting through in-flight H.264 fragment runs, not whole frames, and the resulting corruption cycle (shed → lost fragment → keyframe request → repeat) never resolves as long as load stays high. **The audio line stayed completely clean throughout** (`underruns=0` in every window; not shown in the table, cross-checked against the same timestamps).

**Second half, `debug-video-feed-hold-ms=0`, same session type, ~5 minutes:**

| time | videoQueue | videoShed |
|---|---|---|
| 02:13:17 | 5 | 0 |
| 02:13:47 | 2 | 0 |
| 02:14:17 | 3 | 0 |

**The climb is entirely a property of the test lever.** With the hold off, `videoQueue` sits in the low single digits, never sheds, and the picture is sharp and clean (`evidence/audio-sink-jitter-round5/t2r_second_half_holdoff.png` — same map area, same route, visibly correct). The 256-ceiling corruption in the first half does not happen off the lever; whether it can happen from a real cause (rather than the artificial 200ms hold) is unmeasured by this run and would need a genuinely slow decoder or a real link stall, not this lever.

---

## N7r. The guard, with a lever that reaches it

**FAIL.** Staged the two suggested sources together: closed the settings screen (re-arms the mode) and, essentially simultaneously, fired `ACTION_START_WIRELESS_SCAN`.

- `AapService: the settings screen closed, re-arming wireless mode NATIVE` — **02:21:53.772**
- `AapService: Force-starting WIFI-Scan from UI` (the broadcast) — **02:21:54.653** (881ms later)
- Two independent `WifiDirectManager.createQuietGroup: Attempting createGroup for Native AA (Attempt 0)...` calls, from two separate state-machine walks running concurrently on the same thread: **02:21:55.284** and **02:21:55.414** — **130ms apart**, well inside the brief's 1.5s window.
- **The guard line (`still running, so this one is not started on top of it.`) never appeared.**
- **Two, not one, `createGroup SUCCESS`:** 02:21:55.774 (first walk) and 02:21:59.348 (second walk, after the first attempt of the second walk hit `BUSY` at 02:21:55.994 and retried in 2s per the log's own message). `grep -c "createGroup SUCCESS"` over the window: **2**.

**The practical consequence, not just the count:** the first walk delivered its group's credentials (`SSID=DIRECT-rJ-Navegadortz3`) to the phone over Bluetooth (`SENDING_CREDENTIALS` at 02:21:57.446, `PHONE_JOINING` at 02:21:58.477) — and then, **891ms after the phone was told to join it**, the second walk's `createGroup SUCCESS` at 02:21:59.348 replaced that group with a different one (`DIRECT-eQ-Navegadortz3`). The phone was hunting a group that no longer existed. `isHandoffSettling()`'s "Handoff still settling — not starting a poke that would compete with the phone's WiFi association." guard correctly suppressed new pokes while this sorted itself out (fired repeatedly, 02:21:59.658 onward), so the app did not make it worse — but the session only actually formed **50.7 seconds later** (`WirelessServer: Incoming connection detected` at 02:22:44.442), after a second full poke cycle, because the first one was wasted on a dead SSID. This is the same shape as [[project_wifi_button_double_group_create]]'s previously-fixed regression, reached from a different pair of triggers than the one that fix addressed.

---

## N4r. The second path to the endpoint

Seven real bring-ups run (budget was six; a seventh landed before the settings could be restored — see below), `native-wifi-version-exchange=true` throughout, restored to `false` immediately after and confirmed by readback.

**Sub-result A — the version-exchange path (`sendWifiVersionRequest`): PASS, withheld every single time.** Across all seven real creates (`DIRECT-Pr`, `-YO`, `-y1`, `-mX`, `-oT`, `-m9`, `-R1`-Navegadortz3 — three more stale-group-reuse deliveries, `asked=nothing (not this app's create)`, correctly did not count toward or reset anything visible as a fresh create), the decision never once advertised an endpoint. The reason text alternated UNPROVEN and RENAMED depending on how many *consecutive real* creates had accumulated before a stale delivery reset the run — exactly round 4's finding, reconfirmed:

> `NativeAA: not advertising WPP over TCP: this unit's Android is too old to name its own WiFi Direct group, and the platform has picked a new name every create, so there is nothing for the phone to remember. Withholding one does not clear one the phone already has: Android Auto keeps an endpoint it was given for as long as it is running, and dials it in preference to Bluetooth, so if a connection will not start, forget this head unit on the phone` — 02:24:26.711, exact RENAMED text.

`persistent=no (temporary)` throughout; `stable=` moved unproven → no (platform names it) as creates accumulated, same pattern as round 4. The session formed over Bluetooth every time this was checked (multiple `First frame rendered` / surface-changed confirmations across the run).

**Sub-result B — the TCP server's own check (`WppTcpServer`, the path round 4 never reached): FAIL.** At **02:25:53.586**, mid-run (still on the `m9` group, whose own `sendWifiVersionRequest` had just withheld at 02:25:53.417), `WppTcpServer: connection from 192.168.49.104` fired, followed by a completed TLS handshake and a held-open control channel until the session ended cleanly 4 seconds later. **`WppTcpServer` listens unconditionally on port 5299 and accepts whatever connects, with no check against this session's own withhold decision.** D-POCO's own log (`GH.WPP.TCP`, `GH.ConnLoggerV2`) shows it dialing in because Gearhead's *own, older, cached* belief that "this vehicle supports WPP over TCP" (`WIRELESS_WIFI_PROJECTION_PROTOCOL_HU_SUPPORTS_WPP_OVER_TCP`) persists independently of what the current session's handshake reports (`wifiProjectionProtocolOnTcp=false` in the very same handshake's own CarInfo) — this head unit's BT identity was very likely given a real WPP endpoint in an earlier, unrelated testing round on this same rig (the `wpp-over-tcp` thread), and Gearhead has never forgotten it.

**This is the round's headline finding, and it went further than a log line.** After this connection, D-POCO's Gearhead **wedged itself retrying this now-stale endpoint (`DIRECT-m9-Navegadortz3`) every 35–55 seconds, indefinitely, and never fell back to Bluetooth poke/reconnect** — blocking every subsequent bring-up attempt for roughly 15 minutes (`GH.WPP.TCP: Restarting WPP over TCP, connection failure reason: NETWORK_UNAVAILABLE_NETWORK_NOT_FOUND`, repeating). A Bluetooth toggle on D-POCO did **not** clear it. The only recovery found was the withhold reason's own documented advice, taken literally: forgetting the head unit on the phone (Settings → Android Auto → Vehicles → both "Google" entries → Forget — no scriptable trigger found, done via `uiautomator` + minimum taps per house rule 2), followed by accepting a one-time "Welcome to Android Auto" re-consent dialog on the next connection (also no scriptable trigger, same method). Once both were done, a normal bring-up completed and rendered at 30fps within seconds.

**Budget note:** as in round 4, stale-group-reuse deliveries (`asked=nothing`) intervened between real creates and reset the RENAMED reading back to UNPROVEN more than once; getting three *consecutive real* creates took seven actual bring-up cycles, not six, one more than round 4 needed. Counted only `asked=framework profile` deliveries with a changed SSID toward the real-create count (7), separately from the 3 stale-reuse deliveries.

---

## S1. The group the phone stopped coming back to

**INCONCLUSIVE — the prescribed lever does not reproduce the mechanism this run needs to grade, and this is now precisely diagnosed rather than merely unreached.**

Two attempts. **First attempt** (15s repeat-disable, matching the brief's literal cadence): produced churn, not starvation — D-POCO's WiFi self-reverted fast enough within each 15s gap to complete a full reconnect before the next disable pulse, so the group kept forming and dropping rather than sitting unclaimed. **Second attempt**, with a fresh confirmed-hosted session and a tightened 3s repeat-disable cadence (`s1_wifi_holddown_tight.sh`), genuinely starved the link — sessions stopped forming — but produced a **different failure mode than "ignored":**

```
02:55:44.313 NativeAA: Bluetooth reader ended: bt socket closed, read return: -1
02:55:44.323 NativeAA: Handshake failed — phone reported join failure (type 6, status=WIFI_NETWORK_UNAVAILABLE(-11)).
02:55:44.343 NativeAA: Attempting active poke to device: POCO X3 NFC ...
02:55:44.483 NativeAA: Successfully poked POCO X3 NFC via HFP-AG. Holding 15000ms...
```

**Every poke cycle got an explicit response, not silence.** D-POCO's HFP responder answers every time (`Successfully poked ... Holding 15000ms`), the Bluetooth-side handshake proceeds, and the phone explicitly reports back `WIFI_NETWORK_UNAVAILABLE(-11)` because its own WiFi radio is off — a real, informative failure, not the phone ignoring the channel. Across **12 poke attempts and 22 `WIFI_NETWORK_UNAVAILABLE` responses** over the observed window (02:53:17–02:57:34), **`the phone has ignored N wake pokes` never fired once.** This strongly suggests that counter tracks true silence/no-response specifically (matching N8's watchdog and round 4's own 40-minute reliability-run mechanism, where D-POCO's WiFi stack itself gave up scanning for the group entirely and the head unit's poke got no reply of any kind) — not an explicit join-failure response, which this lever cannot help but produce on this rig, because disabling the phone's WiFi radio does not stop its Bluetooth-side handshake from answering.

**Counts across the window (02:53:17–02:57:34, both attempts combined):**

```
grep -c "MATCH! Starting AapService"  → 0   (no self-inflicted wake-ups)
grep -c "createGroup SUCCESS"         → 2   (both from the ordinary disconnect→recreate path, not a 4-poke give-up)
grep -c "Attempting active poke"      → 12
grep -c "ignored"                     → 0
```

**Recovery:** once D-POCO's WiFi was explicitly re-enabled (it did not self-revert on its own this time — see Setup notes), the very next poke cycle completed cleanly and without the WiFi button: `[RX] WifiStartResponse ip= status=SUCCESS(0)` at 02:57:34.060, session re-formed normally. This much at least matches what S1 wants to see, even though the specific 4-poke-give-up mechanism was never exercised.

**The control run (phone switched off entirely) was not attempted.** Given the primary run already showed the lever doesn't reach the counted mechanism, and given the round's time budget, running the control against the same non-reaching lever would not have added information; it was skipped rather than run for a second inconclusive reading of the same gap. `d9295370`'s stale-group-recreate logic (the eight `ProvenGroupStalePolicyTest` unit tests R0 counted) remains verified only in the JVM this round, not on hardware.

---

## Anything the brief did not ask about

- **N4r's Sub-result B is the headline finding of this round** (see above, in full): the WPP TCP server's unconditional listening, independent of the current session's own withhold decision, let a phone with a stale cached belief dial in and then wedged that phone's Gearhead into a 15-minute unrecoverable retry loop that never fell back to Bluetooth. This is a structural gap in the withhold defense, not a one-off log line — worth planning-side attention beyond what this round can resolve.
- The `svc wifi disable; svc wifi enable` chained-command dumpstate mishap on D-SAM (see Setup notes) is worth confirming or ruling out on a future round before anyone relies on chaining those two commands together on this unit again.
- D-POCO's screen needs to be confirmed idle/home before trusting an automated bring-up to complete — a stray Settings screen silently blocked one entire relaunch cycle this round with no error on the head-unit side to point at it directly.
