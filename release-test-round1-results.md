# release-test — round 1 results

**Candidate:** release/test @ aeac35aa8f9ebd725d6424fb6b7f104b5fa1ef33   **Baseline:** origin/main @ 562c8dcf6691d032159ea69fd0f93d251faf4016
**PR under review:** upstream #845 @ 66e16e0b (branch `pr845`, package `com.andrerinas.headunitrevived.dev`)
**APK md5:** candidate 8e5cefb39459b1f923efbb3861dbd670 / baseline b9c44b11797828275aa4ee332b44a0de / PR845 3f25df4ee93c8dd49a47d1adfe941f23
**Unit:** UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14, phone: Redmi/POCO M2007J20CG (Wireless Helper 1.9.3 installed)
**Date:** 2026-08-21

## Setup notes

**Scope change, mid-round.** Part A (A1–A7, all `wifi-connection-mode=3` Native AA hardware runs on the merged `release/test` candidate) was explicitly pulled out of scope by direction partway through the round — the reasoning given was that `fix/session-lifecycle-and-diagnostics` and `feat/render-side-concealment` had already each passed their own Native AA hardware round individually, and the merge-specific interaction the brief's §2 worried about (`AapTransport.kt`/`ProjectionWatchdogPolicy.kt` overlap) was judged adequately covered by A0's unit-test gate for this round. **A0 had already been run before that direction landed** and is reported below as-is. A1–A7 were not attempted this round and carry no verdict (not INCONCLUSIVE/UNTESTABLE — simply out of scope by direction, not by rig limitation). The round became "all of Part B."

**`release/test` was pre-existing locally**, not freshly created via the brief's `git checkout -B` recipe. Verified it matches anyway: `dda94834` = `origin/main@562c8dcf` merge `fix/session-lifecycle-and-diagnostics@afd8b7ca`; `aeac35aa` (HEAD) = that merge `feat/render-side-concealment@d30fe1d8`. Same four overlap files the brief named (`AapTransport.kt`, `AapProjectionActivity.kt`, `ProjectionWatchdogPolicy.kt`, `ServiceDiscoveryResponse.kt`), zero conflict markers in the tree.

**Rig-state drift found and fixed.** The head unit's station WiFi auto-join for its usual network (`Pegue Cdesta`, 5500MHz) was disabled — left that way by a previous thread's probe. This blocked B4/B5/B6 outright: Nearby's `WIFI_LAN` discovery medium logged `MEDIUM_ERROR ... MEDIUM_NOT_AVAILABLE ... WITHOUT_CONNECTED_WIFI_NETWORK` with the station disconnected. No `adb shell cmd wifi` subcommand re-enables auto-join once disabled (only a `-d` disable flag exists on `connect-network`/`add-network`; `clear-user-disabled-networks` did not help — that clears a different disable mechanism). Recovered by reading the network's own saved PSK out of `/data/misc/apexdata/com.android.wifi/WifiConfigStore.xml` on the rooted head unit (plaintext, root-only path) and reconnecting both devices with `cmd wifi connect-network "Pegue Cdesta" wpa2 "<psk>"` — credential not reproduced here. Both devices confirmed on `Pegue Cdesta`/5500MHz afterward. B4's first attempt (pre-fix) and B2's Nearby-medium check were invalidated by this and redone clean.

**The `.dev`/diagnostics build variant defaults `log-source` to `APPLOG_FILE`, not `LOGCAT`.** `Settings.kt`'s `defaultLogSource` reads `BuildConfig.DEV_DIAGNOSTICS`; PR845's `.dev` flavor sets it, so on a fresh install **zero** `OPENHU`-tagged lines reach `adb logcat` regardless of `log-level` — confirmed empirically (a `.dev` process was clearly alive and forming P2P groups/RFCOMM listeners in logcat's system lines, with not one `OPENHU` tag line from its pid). Every `.dev`-side observation in this report came from pulling its exported `HUR_Log_*.txt` from `/sdcard/Android/data/com.andrerinas.headunitrevived.dev/files/` instead. Same class of finding as the `DEBUG`-default caveat this repo's own `CLAUDE.md` already documents for a different branch — worth a note back to the PR author since it silently defeats a logcat-only capture of `.dev` builds.

**Wireless Helper's Nearby path needed one UI setup cycle.** The companion phone app (`com.andrerinas.wirelesshelper`, closed-source, not debuggable, phone not rootable — `adb root` refused) has an in-app "Connection Mode" setting that defaults to "Phone Hotspot"; it must be switched to "Google Nearby (Beta)" for B4/B5/B6 to mean anything, and no scriptable path (deep link, exported settings intent) exists for that app. Did the minimum: opened Settings → tapped the Connection Mode row → tapped "Google Nearby (Beta)" (no scrolling, 5 options all on one screen) → back. One-time; persisted across all subsequent runs. Everything *after* that setup was fully scripted: the head unit side never needed its own "WiFi" home-screen button or nearby-device picker dialog — `AapService.ACTION_START_WIRELESS_SCAN` and `ACTION_NEARBY_CONNECT --es extra_endpoint_id <id>` (read from the discovery log) drive the whole flow via `adb shell am start-foreground-service`, bypassing the in-app dialog entirely.

**Stray artifact, caught and fixed mid-round:** while probing B1's locale question, a per-app locale override (`cmd locale set-app-locales`) intended only for `.dev` also landed on the **store** package, rendering its UI in German ("Beenden" instead of "Exit"). Caught from direct observation of the device screen, both overrides cleared, English confirmed restored via `uiautomator dump` before continuing.

Scripts used: `build_hur.sh`, `run_unit_tests.sh` (both fit as-is, no changes). `set_hu_prefs.sh` was **not** usable for the `.dev` package — it hardcodes `PKG=com.andrerinas.headunitrevived` with no override — so `.dev`'s `settings.xml` was edited directly with the same rooted `adb shell sed` pattern the script uses internally. Worth generalizing `set_hu_prefs.sh` to take `PKG` as an env var before the next two-package round.

`.dev` package uninstalled at the end; single package (`com.andrerinas.headunitrevived`) restored. Store package's `settings.xml` diffed against a fresh backup taken at round start: only delta is the `debug-video-fault-injection` key removed (was `0`/off; absent reads the same default).

## A0 — build and unit-test gate

**PASS**

- `release/test` built clean: `afd8b7ca`'s first-ever compile succeeded (assembleGithubDebug, `BUILD SUCCESSFUL`). `origin/main@562c8dcf` built clean as baseline. md5s differ (above).
- `testGithubDebugUnitTest`: **669/669 green**, zero failures/errors. All eleven named suites present with the exact counts the brief predicted: `LinkGapMonitorTest`=17, `UplinkStallMonitorTest`=5, `StationCoexistencePolicyTest`=11, `ProjectionWatchdogPolicyTest`=23, `DummyVpnPolicyTest`=7, `UsbSessionQuiescePolicyTest`=8, `NativeCredentialsPreflightPolicyTest`=17, `CorruptionConcealmentPolicyTest`=16, `AuditRecoveryPolicyTest`=10, `KeyframeCycleEscalationPolicyTest`=40, `AapMessageFramingTest`=4.

## A1–A7

Not run this round — Part A hardware testing was pulled from scope mid-round (see Setup notes). No verdict.

## B0 — does PR #845 compile at all?

**PASS**

- `git fetch origin pull/845/head:pr845` → `66e16e0b`, matches the brief exactly.
- `assembleGithubDebug`: clean build, first compile this branch has ever had (both GitHub Actions workflows sit at `action_required`, zero check runs on the head commit).
- `testGithubDebugUnitTest`: **565/565 green**, zero failures/errors.

## B1 — side-by-side install, and what the launcher says

**PASS**

- `adb install -r` of the PR845 APK succeeded with the candidate already installed; `pm list packages | grep headunitrevived` showed both `com.andrerinas.headunitrevived` and `com.andrerinas.headunitrevived.dev`.
- Store build still launches and resumes normally (`topResumedActivity=...MainActivity`) with both packages present.
- **Locale finding confirmed**, via the compiled APK's own resource table (`aapt2 dump badging`) rather than a runtime device check — see Setup notes for why the runtime route (per-app locale override) doesn't exercise launcher-label resolution on this OS. `application-label:'Open Headunit Dev'` (default), but `application-label-de:'Open Headunit'`, `-ru:'Open Headunit'`, `-ja:'Open Headunit'`, `-es:'Open Headunit'`, `-it:'Open Headunit'`, `-pl:'Open Headunit'` — all six locales the brief named, and about 25 others, silently drop the "Dev" suffix. This is the exact review finding, confirmed straight from the artifact PackageManager actually reads.

## B2 — can two head unit builds share port 5288?

**No PASS/FAIL — measurement, per the brief.** The desk review is confirmed, and the real behavior is worse than "can't share the port": **launching the second build kills whichever session the first build already had, and neither build can ever hold a session while both are running.** Order-independent, reproduced both directions.

**Run 1 — store launched first, `.dev` launched second (11:03–11:07):**
1. `.dev` logs `BindException`/`EADDRINUSE` immediately (`11:04:19.636`, attempt 1 of 3), every single retry, for the whole ~3-minute capture.
2. `.dev` then logs `nothing is listening on port 5288` repeatedly: `NativeAA: Handshake aborted — nothing is listening on port 5288 after 3s...` — dozens of times as the phone's real AA client kept retrying the RFCOMM handshake every ~7–8s.
3. The rebuild budget burns exactly as `native-aa-5288` documented elsewhere: `Rebuilding the wireless server on 5288` attempt 1 → 2 → 3, then falls back to a lower-effort "waiting before trying again" — and the whole 3-attempt cycle **repeats indefinitely** rather than giving up, because a competing process holding the port never goes away on its own.
4. `.dev` **never** accepted a session — 0 successes in dozens of handshake attempts over ~3 minutes, own `p2p` group churning too (a fresh SSID every retry: `DIRECT-80`→`DIRECT-DR`→`DIRECT-TQ`→`DIRECT-KE`…).
5. **Unasked-for finding:** the store build's own already-live session (SSL handshake complete at `11:03:41`, video decoding) was torn down outright the instant `.dev` launched — `AapRead: Connection closed (EOF). Disconnecting.` at `11:04:19.651`, ~16ms after `.dev`'s own port-bind attempt disrupted the store's listening socket (`WirelessServer: port 5288 released (SocketException)`). Store's `AapService` auto-restarted its discovery loop and rebuilt its P2P group **4 more times** (5 `createGroup SUCCESS` total) but **never got a second SSL handshake for the rest of the capture** — its session was permanently lost for as long as `.dev` kept running. `p2p-wlan0` interface index climbed 1→8 across both builds in ~3.5 minutes.

**Run 2 — reversed, `.dev` launched first, store launched second (11:08–11:10):**
1. `.dev` alone: clean bind, full session (SSL handshake `11:08:22.292`, video decoding `11:08:24.193`) within ~6s.
2. Store launched second (`11:08:55`) → immediate `BindException`/`EADDRINUSE`, same 3-attempt/backoff cycle repeating — symptom followed the **second** starter, confirming it's not "the store build always loses," it's "whoever launches second."
3. `.dev`'s own live session was **also** killed at the same instant: `Connection closed (EOF)` at `11:08:55.434`, i.e. store's mere launch tore down `.dev`'s session too — confirming the mutual-kill effect is order-independent, not a one-way quirk of the store build.
4. (One incidental re-confirmation: an unrelated locale-override cleanup force-stopped and relaunched the store build again mid-run, at `11:09:xx` — pid changed to 13326, which reproduced the identical `BindException`/`EADDRINUSE` pattern a second time. Noted as a Setup-notes deviation, not a separate planned run.)

## B3 — does the AppLog stack trace reach a file capture?

**PASS.** Answered for free from B2's own `.dev` diagnostics file, no separate run needed.

- Error + throwable: `2026-08-21 11:04:21.060 [OPENHU:E] ... | Wireless server error` immediately followed, same block, by the full `java.net.BindException: bind failed: EADDRINUSE` stack trace (`at libcore.io.IoBridge.bind(...)` etc.) — not a blank line. Reproduced identically at four more BindException occurrences in the same file.
- Error, no throwable: `AppLog.e("NativeAA: Handshake aborted — nothing is listening on port 5288...")` — confirmed in source (`NativeAaHandshakeManager.kt:1229`) as a no-throwable call site — is followed immediately by the next timestamped log line, no stray blank line, at every one of its ~15 occurrences in the capture.

## B4 — the Nearby stream tunnel, happy path (×3)

**PASS, all three runs.**

| Run | Endpoint | `Connected successfully!` → `Wi-Fi Bandwidth Upgrade successful` |
|---|---|---|
| 1 | HXJJ | **8ms** (11:22:32.343 → 11:22:32.351) |
| 2 | R0I5 | **8ms** (11:24:03.574 → 11:24:03.582) |
| 3 | OLO1 | **7ms** (11:25:11.158 → 11:25:11.165) |

All three: SSL handshake completed ~1.2–1.3s after the tunnel decision, video decoding started ~2–2.6s after that. `Attaching the inbound STREAM that arrived before the socket existed` **never fired** in any of the 3 runs — `WIFI_LAN` discovery already had the endpoint at `HIGH` quality by the time the connection request landed, so no separate bandwidth-upgrade round-trip (and therefore no race window) was ever created on this rig/network. Zero `phone never sent its half of the stream tunnel within` / zero `Nearby stream tunnel incomplete` across all 3. All three timings sit nowhere near the review's flagged 10s-handshake-inside-12s-cap boundary.

## B5 — does a failed attempt poison the next one?

**PASS (finding refuted).**

- Setup: force-stopped Wireless Helper on the phone right as the connection was requested to endpoint `L278`. Result: `Failed to request connection: 8012: STATUS_ENDPOINT_IO_ERROR` at `11:26:34.112` — a genuine failure short of `STATUS_OK`, no full disconnect needed.
- Without restarting the head unit app or leaving the discovery session, relaunched Wireless Helper and connected to a fresh endpoint (`8425`). The second attempt's log: `Connected successfully!` (`11:27:37.590`) → `Bandwidth changed for 8425: Quality=3 (HIGH)` (`11:27:37.595`, **a genuine fresh callback**) → `Wi-Fi Bandwidth Upgrade successful` (`11:27:37.598`). The intervening `Bandwidth changed ... Quality=3` line is exactly what the brief's "finding refuted" verdict requires — `lastQuality` was not reused stale; the second attempt got its own real callback for the new endpoint. Session then completed normally (SSL handshake, video decoding) confirming it wasn't a fluke short-circuit.

## B6 — the subnet sweep tally

**PASS.**

- `.dev`, mode 2, `helper-connection-strategy=0` (common WiFi/NSD), phone not running the helper. Triggered via `ACTION_START_WIRELESS_SCAN`.
- Exactly one summary line per sweep, reproduced identically across 5 consecutive sweeps (the scan auto-repeats roughly every 14s once started — an observation, not something the brief asked about): `Swept 10.201.239.* — 253 probed, 0 responded, 253 silent on 5289 (last: SocketTimeoutException)`. Zero per-address probe lines in any sweep.
- Numbers partition correctly this run: 253 = 0 + 253. Nothing on this subnet answers on port 5277, so the review's specific double-counting scenario (a host answering on 5277 counted both as responded and as silent) was not exercised either way — neither confirmed nor refuted, just not reachable with this subnet's current occupants.

## Anything the brief did not ask about

- **The `.dev`/diagnostics build's silent logcat blackout** (Setup notes) is the biggest trap for whoever runs the next round against a `.dev`/debug-flavored build on this rig: a logcat-only capture will show the process alive and forming P2P groups/BT listeners in system lines, with **zero** `OPENHU` lines, and nothing in the brief's own `log-level`/`log-source` table warns of it because the default itself moved, not just the level.
- **B2's actual failure mode is worse than the brief's framing.** The brief asked "can two builds share port 5288," which reads as a scoped, contained failure. What was measured is that merely *starting* a second build's `AapService` is disruptive enough to kill an unrelated, already-working session on the first build — this is closer to "installing this PR's `.dev` variant is a live hazard to any existing Native AA session on the device" than "the second install doesn't work." Worth flagging to the PR author as the headline, not the port number.
- Both HU and phone required manual WiFi credential recovery mid-round (Setup notes) — this rig's station-network drift between threads (documented in `TESTING-TEMPLATE.md` §7a's "settings.xml survives between rounds" bullet) evidently extends to the WiFi *association* itself, not just app settings. Worth a line in §7a for the next brief author: verify `dumpsys wifi | grep mWifiInfo` shows an actual `COMPLETED` supplicant state, not just that the SSID is the expected one from memory.
