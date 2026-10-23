# pr-1119-home-redesign round 1, finding: D-SAM loops between "Searching" and "Looking for your phone" and never connects (pr-1119 build)

Gathered 2026-10-21 21:06 to 21:12 (D-SAM clock, COT, about 21 s behind the host) on `11a2c37fd244` (4.0.0-alpha, versionCode 120, `fork/review/pr-1119`). Observed facts and hypotheses are kept apart below.

## What was observed

**Unit and state** (`d-sam-query-state.txt`, `d-sam-settings-at-loop.xml`): SM-T230, Android 4.4.2 (API 19), 1280x800 landscape. App in front on the new home. `wifi-connection-mode` = 1 (Headunit Server discovery); my own restore at the end of the earlier run left it at 3, so it was changed after that (not by my scripts). D-SAM is joined to the house WiFi (SSID "Pegue Cdesta", 192.168.1.22, 2.4 GHz); it was not joined during my earlier runs. The head unit server is on D-POCO (192.168.1.20, Android Auto 18.0.664204, listener on :5277).

**What the home showed** (`ui/loop1..8.png|xml`, one dump about every 10 s):

| Time | Card text |
|---|---|
| 21:07:11 | Connect wirelessly / Looking for your phone / Cancel |
| 21:07:21 | Searching for your phone... / Open Android Auto on the phone if it does not show up / Search again |
| 21:07:31 | Connect wirelessly / Looking for your phone (no Cancel) |
| 21:07:41 | Connect wirelessly / Looking for your phone / Cancel |
| 21:07:50 | Connect wirelessly / Over WiFi (ready card) |
| 21:08:00, 21:08:10 | Connecting to your phone / Securing the connection / Cancel |
| 21:08:20 | Connect wirelessly / Over WiFi (ready card) |

**What the log shows for each cycle** (`window.txt`, 21:07:11 to 21:08:12, a cycle is about 14 s). First cycle, from `d-sam-live.txt`:
1. `NetworkDiscovery: Starting scan` then `Found Headunit Server on 192.168.1.20:5277` (four times in the window: 21:07:20.093, 21:07:29.502, 21:07:43.396, 21:07:57.330).
2. `Auto-connecting to Headunit Server ... (reusing socket)`, steps `PHONE_ANSWERED`, `CONNECTING`, `SECURING`.
3. `Handshake: No VERSION_RESPONSE within 2s (attempt 1)`, `(attempt 2)`, `within the handshake budget (attempt 3)`.
4. `Handshake: the peer accepted the connection and then sent nothing at all. Our link is fine: every read timed out rather than failing. On the head unit server path this is Android Auto's own side.`
5. `session state failed (peer_silent)`, `AapTransport quitting (clean=false)`, step `hidden`, then `AapService: Disconnected. Restarting discovery loop in 2s...` and the scan starts again.

Zero `SSL handshake complete` in the window. No NativeAA lines: it is the Headunit Server path, not Native AA.

**The app's own diagnosis:** it stamped `connection-issue-headunit-server-deaf` = 1792634888732 (21:08:08 on D-SAM) during the loop. Its banner text is "Android Auto's head unit server on your phone has stopped answering. On the phone, stop and start it again in Android Auto's developer settings."

**The banner was not on screen during the loop.** None of `ui/loop1..8` nor `ui/now.xml` (21:12) has `id:connection_issue_banner`. After one HOME press and a relaunch of `MainActivity` (21:10:27) it appears (`ui/after-resume.png|xml`, log `MainActivity.updateConnectionIssueBanner: showing the connection issue banner for HEADUNIT_SERVER_NOT_ANSWERING`, file `resume-d-sam.txt`). `connection-issue-dismissed-at` (1789970221116) is older than the stamp, so it was not dismissed.

## Not known

- Whether D-POCO's head unit server really is wedged. D-POCO's side was captured for 45 s (`pair-d-poco.txt`) but D-POCO was being used by hand at the time (a popup window was in front, WiFi re-associated at 21:09:22) and nothing in that capture names the server. The listener on :5277 stayed in state LISTEN (0A).
- Whether the previous (classic home) build loops the same way against a deaf server. The reconnect timing (2 s, scan every ~14 s) and the handshake lines look like connection code, not home code, but this was not run on another build.
- What stopped it before: D-POCO's server answered D-SAM in the earlier run (SAM-C2 session) when D-POCO was the phone, not the server.

## Questions for the coding agents

1. Is a silent server meant to be retried forever at about 14 s with no backoff? Each retry is a fresh connection to a server that, per the app's own comments, can be wedged by connections nobody follows through.
2. The home flips between two layouts for one state: the card with `Cancel` ("Looking for your phone", stage text) and the card with `Search again` ("Searching for your phone...", status title). Two strings and two layouts for SEARCHING makes the loop look like a fault to the user. Is that intended?
3. The connection-issue banner only refreshes on `onResume()`, so it is absent for the whole loop and appears only after leaving and returning to the home. Should the home refresh it when the stamp changes?

## Suggested checks

- Operator on D-POCO: Android Auto developer settings, stop and start "Start head unit server", then watch whether D-SAM connects within one cycle. If it does, the loop is the deaf server plus the missing banner, not a regression.
- Repeat the loop on the build before the PR with the same deaf server (a known good control).

## Files

In the captures asset under `for-coding-agents-d-sam-loop/`: `d-sam-live.txt` (full logcat from 21:06:42, 3326 lines), `window.txt` (the 21:07:11 to 21:08:12 slice), `pair-d-sam.txt` and `pair-d-poco.txt` (45 s from both units at 21:08:50), `resume-d-sam.txt`, `ui/` (screenshots and dumps), `d-sam-settings-at-loop.xml`, `d-sam-query-state.txt`.

What I did to D-SAM: captured logs, ran `uiautomator dump`/`screencap`, read its settings, one HOME key and one `am start MainActivity` (the resume). I changed no setting. I did not touch D-POCO's server.

## Update 21:12 (D-SAM clock): it connected by itself, and what the code diff says

**Recovery timeline** (`recovery-d-sam-21-10-to-21-12.txt`, same app process, pid 12394, no reinstall or restart of the app):
- 21:10:25.885 last silent handshake (`peer_silent`).
- 21:10:27.977 and 21:10:55.553 scans: `Swept 192.168.1.* - 253 probed`, **no** `Found Headunit Server`. Nothing was accepting on :5277 for about 48 s.
- 21:11:15.983 `Found Headunit Server on 192.168.1.20:5277`; 21:11:17.345 `Version response received: the phone selected 1.7`; 21:11:17.795 `SSL handshake complete`.
- 21:11:20.858 `SocketException: recvfrom failed: ECONNRESET (Connection reset by peer)` about 3 s later (the phone side reset it during service discovery), then `Restarting discovery loop`.
- 21:11:40.868 found again, 21:11:42.760 `SSL handshake complete`, then `projecting`, rendered about 150 frames per 5 s (30 fps) through the last line at 21:12:25. Stable.
The only things done to D-SAM in that window were my HOME press and relaunch at 21:10:25 to 21:10:27 (the banner test). D-POCO was being used by hand (popup in front at 21:09, WiFi re-association at 21:09:22). The gap with no server found, followed by a server that answers, looks like the server being stopped and started on the phone, as the banner says. This is an inference; D-POCO's own log does not show it.

**Code diff, base to this build** (`git diff fbeb46fa 11a2c37fd244`): under `connection/` and `aap/` (excluding generated proto) only `AapProjectionActivity.kt` (91 lines) and a new `AudioModeWatcher.kt` changed. `NetworkDiscovery`, `AapService` (reconnect scheduling, "Restarting discovery loop in 2s"), `AapTransport` (handshake, 3 attempts) and the socket connection code are unchanged. Under `main/` the home was rewritten (new `HomeFragment`, `HomePrimaryPolicy`, a reconnect countdown for auto-connect, widgets); the diff has no change to discovery, to the wireless-scan trigger or to the handshake.

**What this does and does not show.** The loop's own code is the same as before, so a silent server should loop the same way on the base. It does not rule out that the new home changes when or how often a connection is started (for example the auto-connect countdown or the resume behaviour) and so makes a server deaf that the old home would not. The reporter's observation that older versions did not loop with this setup is not contradicted by anything here, and not confirmed either: no run on the older build exists.

**Suggested A/B (about 40 min on the rig, not run):** build `fbeb46fa` once, install it with `adb install -r -d` over the PR build on D-SAM (same debug signature as the 3.5.0 builds that were on the unit), make D-POCO's server deaf the same way each time (hold one bare TCP connection to :5277 from D-SAM's shell, which the app's own comments say wedges it), and watch both builds for 3 minutes each; restart the server on D-POCO between arms (a hand step).
