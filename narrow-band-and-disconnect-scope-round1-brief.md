# narrow-band-and-disconnect-scope — round 1 brief

## 1. Build and baseline

**Candidate:** `fork/fix/narrow-band-and-disconnect-scope` @ `9635f8a5`, four commits on top of
`fork/feat/native-aa-wireless-and-bt-lifecycle` @ `a938ba91`.

```bash
git fetch fork
git checkout -B round-narrow-band fork/fix/narrow-band-and-disconnect-scope   # 9635f8a5
```

**Control, for R1 only:** `fork/feat/native-aa-wireless-and-bt-lifecycle` @ `a938ba91`, which is the
candidate's own parent. No history was rewritten on either branch since the last round; the wireless
branch gained two commits (`6fbbe770`, `a938ba91`) after the last one that referenced it.

The four commits:

| SHA | What |
|---|---|
| `67f4344b` | `CommManager._scope` is re-armed by `destroy()` instead of staying cancelled |
| `99214450` | `ConnectionIssue.VIDEO_LINK_TOO_SLOW` plus its banner |
| `8b3b576e` | the announced video profile is capped on a radio with no 5 GHz band |
| `9635f8a5` | hotspot bring-up waits for the radio; the give-up line stops blaming privileges |

## 2. What this is and why it exists

A reporter's head unit (Qualcomm `sm6150_au`, API 28, stock `3.3.0` vc103) runs Native AA over its
own access point. The route works end to end: the phone joins, TCP lands on 5288, SSL completes,
service discovery and media sink setup complete. Then the **phone** closes the socket 0.68–3.32 s
later, having sent not one video frame. Twenty-four times in one capture, zero decoder activity in
any of them. That is the signature this branch's own `VideoStarvationPolicy` was written for, and it
was measured before on a different unit: 2.4 GHz at 1080p/60 dies with no frame, the same access
point at 800x480/30 holds.

Three separate defects came out of reading that log.

**a) The disconnect scope, and it is the widest of the three.** `CommManager._scope` was a `val` that
`destroy()` cancelled, and `AppComponent` hands one `CommManager` to every `AapService` in the
process. `AapService.onDestroy` calls `destroy()` unconditionally. So from the **first** service
teardown onward, every `_scope.launch { doDisconnect(...) }` was a no-op: `transportedQuited`,
`disconnect()` and `disconnectForLinkLoss` all launch there, and only `destroy()` itself calls
`doDisconnect()` synchronously. The consequences are no `ByeByeRequest` on a user exit, the socket
closed only by the next `connect()`, and every diagnostic hanging off `doDisconnect` going dark. In
the reporter's capture the service was destroyed three times before the first session, which is why
the starvation advice never fired once across 24 qualifying sessions.

**b) The verdict was log-only.** `VideoStarvationPolicy`'s streak wrote one `AppLog.w` line and
nothing durable. It now also raises a `ConnectionIssue` and a main-screen banner, retired by a
session that renders a frame.

**c) The hotspot auto-enable loses to the radio, and then blames permissions.** Six auto-enable
attempts in the reporter's capture split cleanly: both attempts on a settled radio came up in under
a second, and all four that followed us either disabling WiFi (45 ms, 6 ms earlier) or tearing the
AP down ourselves (2.0 s, 8.5 s earlier) failed after 12 s. `HotspotManager` slept a fixed 500 ms
after the WiFi disable and `restart()` settled 2 s. Worse, the give-up line told a user with
`canWriteSettings=true`, on a unit that had already brought one up twice in the same process, that a
non-privileged install cannot do this and they should use system settings. They did, from then on.

## 3. What is different about this round

**The profile cap cannot fire on this rig, and that is expected, not a failure.** The cap is gated on
`WifiBandCapability.supports5Ghz(context)` returning a hard `false`, and §7a records this rig as
permanently joined to a 5500 MHz network. A radio that associates at 5500 MHz will almost certainly
answer `true`. So **R5 is written as a regression guard, not a proof**, and its expected verdict is
"nothing changed". If the pre-flight in R5 finds the rig answers `false`, say so and R5 becomes the
interesting run instead — the brief tells you what to do in both directions. The cap's decision
matrix is covered off-device by 13 JVM tests in `NarrowBandProfilePolicyTest`; only its wiring needs
hardware, and the one unit that can show it firing is the reporter's.

**R1 needs a control build.** It is the only run in the round that does, and it is unavoidable: the
defect is the *absence* of a log line, so the same sequence has to be shown producing it on the
candidate and not producing it on the parent. Both APKs are from branches that are already pushed.

**R4 changes the rig's WiFi state.** It needs the station radio switched off and on around a
connection attempt. §7a records that `svc wifi enable` does not reliably bring the station back
after `svc wifi disable` on this unit, so **run R4 last** and expect to restore the association by
hand at the end of the round. Do not re-associate to a different network.

## 4. Settings keys this round needs

Written into `shared_prefs/settings.xml` with the app stopped, per §1 and house rule 3.

| Key | Type | Value | Used by |
|---|---|---|---|
| `wifi-connection-mode` | int | `3` | all runs (Native AA) |
| `native-ap-transport` | int | `0` = WiFi Direct, `1` = hotspot | R1–R3 use `0`; R4 uses `1` |
| `exporter-log-level` | (as the rig already sets for VERBOSE) | VERBOSE | all runs |
| `connection-issue-video-starved` | long | `1755800000000` to seed, `0` to clear | R2 |
| `connection-issue-dismissed-at` | long | **delete the element** | R2, R3 |
| `narrow-band-profile-cap` | bool | `true` (the default) | R5 |
| `auto-enable-hotspot` | bool | `true` | R4 |
| `fps-limit` | int | `60` | R5 |
| `resolutionId` | int | `3` (1080p) | R5 |

Per §7a, seeding a `connection-issue-*` stamp without deleting `connection-issue-dismissed-at` is a
known false negative: the seed constant is older than any real clock reading, so a dismissal left by
an earlier run silently suppresses it. Delete the key, do not set it to 0.

Diff `settings.xml` against a fresh backup at the start and state the delta in Setup notes even if it
is zero.

## 5. The lines that decide every run

All verified with `grep -F` against `9635f8a5`. Interpolated parts are shown as `<...>`.

| String | Where it comes from |
|---|---|
| `AapTransport stopping and sending byebye` | `AapTransport.stop()`, reachable **only** from `doDisconnect(sendByeBye = true)`. This is R1's whole discriminator. |
| `Decoder stopped: CommManager: doDisconnect` | also `doDisconnect`, but suppressed when the decoder is already stopped, so corroboration only |
| `AapService destroying...` | `AapService.onDestroy`, the event that used to kill the scope |
| `showing the connection issue banner for VIDEO_LINK_TOO_SLOW` | `MainActivity.updateConnectionIssueBanner` |
| `sessions in a row ended without a single video frame arriving` | `CommManager.noteSessionEnded`, on the third consecutive starved session |
| `[RES_CAP] resolutionId=<n> ... capped=<res> changed=<bool> linkCapped=<res or none>` | `HeadUnitScreenConfig`; `linkCapped=` is new this round |
| `[ServiceDiscovery] This unit has no 5 GHz band, so this session runs over 2.4 GHz` | `NarrowBandProfilePolicy.advice`, only when the cap applies |
| `asked for at most 720p and 30 fps` | the same line, cap-on wording |
| `WifiDirectManager: this unit's WiFi radio has no 5 GHz band` | `WifiBandCapability.describe`, the rig's own answer |
| `HotspotManager: WiFi disabled before enabling hotspot.` | now printed after a real state wait, not a 500 ms sleep |
| `HotspotManager: WiFi is neither on nor off after 8s` | the wait timing out |
| `HotspotManager: The access point was still up 8s after being asked down` | `restart()`'s new wait timing out |
| `The hotspot was switched off while this start was still running` | a disable landing mid-start |
| `This unit has brought one up before, so this is the radio being busy` | the give-up line's new arm |
| `On a non-privileged install this usually cannot be done from an app` | the old arm; must now appear **only** without WRITE_SETTINGS |
| `on this API level: <ExceptionName>: <message>` | `SoftApConfigCompat`; previously always printed `null` |

## 6. Runs

### R0 — build gate

Build both APKs with `hur-wifi-test-scripts/build_hur.sh`. Unit tests via `run_unit_tests.sh`.

- **PASS**: candidate reports **1259 tests, 0 failures, 0 errors**. Record the md5 of both APKs.
- Confirm the candidate's identity from the DEX rather than the version string: the symbol
  `narrow-band-profile-cap` must be present in the candidate and absent in the control.
- **FAIL** stops the round.

### R1 — the disconnect scope. **This is the point of the round.**

Native AA, WiFi Direct (`native-ap-transport=0`). Run the identical sequence on both APKs.

1. Bring up a session and let it reach `SSL handshake complete`.
2. `adb shell am start -a android.intent.action.VIEW -d "headunit://exit"` and confirm
   `AapService destroying...` in the capture. **This is the step that used to poison the process.**
3. Cycle the phone's Bluetooth off and on to get a second session (§3: there is no deep link that
   re-arms mode 3). Wait for a second `SSL handshake complete`.
4. `adb shell am start -a android.intent.action.VIEW -d "headunit://disconnect"`.

- **PASS (candidate)**: `AapTransport stopping and sending byebye` appears **after** step 4, i.e.
  after the `AapService destroying...` from step 2.
- **PASS (control, the positive control)**: the same string is **absent** after step 4 on
  `a938ba91`, while step 2's `AapService destroying...` is present in both. That absence is the
  defect reproducing, and it is what makes the candidate's presence mean something.
- **FAIL**: the string is absent on the candidate, or present on the control.
- **What a PASS would look like if the change did nothing**: it cannot look like anything else. The
  string is emitted from exactly one place and that place is only reachable through the scope the fix
  re-arms. Report the count of `AapTransport stopping and sending byebye` and of
  `AapService destroying...` for both builds, so the pairing is on the record rather than asserted.
- If the phone's Bluetooth cycle does not produce a second session (§7a records this rig cycling the
  *phone's* radio as not producing a fresh `ACL_CONNECTED`), cycle the **head unit's** adapter
  instead, which §7a records as working, and say which you used.

### R2 — the verdict reaches the screen

Candidate only. No session needed.

1. `am force-stop`, then in `settings.xml` set `connection-issue-video-starved` to `1755800000000`
   and **delete** the `connection-issue-dismissed-at` element.
2. `wifi-connection-mode=3`, `native-ap-transport=0`.
3. Launch `MainActivity`.

- **PASS**: `showing the connection issue banner for VIDEO_LINK_TOO_SLOW`, and a screenshot showing
  the banner text about the phone giving up on the video before a frame arrived.
- Then set `native-ap-transport=1`, force-stop, relaunch: the banner must appear **again**. This
  verdict is the only one relevant to both transports, and that is the assertion.
- **FAIL**: no banner on either transport, or the banner naming a different condition.
- Note per §7a that the banner is refreshed on `onResume()` only, so every check here is a
  force-stop-and-relaunch, not a return to a foregrounded app.

### R3 — a session that renders retires it

Candidate only, straight after R2, with the stamp still seeded and the dismissal key still absent.

1. Bring up a Native AA session and let video render for at least 30 s.
2. `headunit://disconnect`.
3. `am force-stop`, relaunch `MainActivity`.

- **PASS**: no banner, and `connection-issue-video-starved` reads back as `0`.
- **FAIL**: the banner still shows, or the key is still non-zero.
- **What a PASS would look like if the change did nothing**: a banner can also be absent because the
  stamp never got seeded or a dismissal crept back in. Read the key back with `grep` before step 1
  and after step 3, and report both values, not just the screenshot.

### R4 — hotspot bring-up against a busy radio. **Run this last.**

Candidate only, `native-ap-transport=1`, `auto-enable-hotspot=true`.

1. Ensure the station radio is **on** and associated, and the head unit's hotspot is **off**.
2. Trigger a connection so `SoftApCredentialsProvider`'s auto-enable runs. Capture from before.
3. Repeat the same trigger twice more, leaving at least 60 s between attempts.

- **PASS**: at least one attempt logs `HotspotManager: WiFi disabled before enabling hotspot.`
  followed by an access point coming up, and **no** attempt in the capture logs
  `On a non-privileged install this usually cannot be done from an app` while
  `canWriteSettings=true` appears on the same `Setting hotspot enabled=true` line.
- **INCONCLUSIVE, pre-registered**: this rig can neither read nor write its SoftAP config, so
  `on this API level: <Exception>` is expected on every attempt and is **not** a failure. What
  matters is only whether an access point came up and what the give-up line said if one did not.
- **FAIL**: every attempt fails to bring an AP up *and* the give-up line blames privileges on a unit
  that reports `canWriteSettings=true`.
- Report, for each attempt: the gap in seconds between the last radio event (`WiFi disabled` or a
  `stopTethering`) and the `startTethering` that followed it, and whether an AP came up. That pairing
  is the measurement the whole change rests on; a bare pass/fail count does not distinguish "the wait
  worked" from "the radio happened to be idle".
- **Restore the station association by hand at the end.** §7a: `svc wifi enable` is unreliable here.

### R5 — the profile cap does not fire on a 5 GHz radio

Candidate only, `fps-limit=60`, `resolutionId=3`, `narrow-band-profile-cap=true`, WiFi Direct.

Pre-flight, before anything else: bring up one session and read the rig's own answer out of the
capture, `grep -F "WifiDirectManager: this unit's"`.

- **If the rig reports it has a 5 GHz band** (expected): **PASS** is `[RES_CAP] ... linkCapped=none`,
  no `[ServiceDiscovery] This unit has no 5 GHz band` line anywhere, and the announced profile
  unchanged from the settings — `resolutionId=3` announced as 1080p and 60 fps on the wire. This is a
  regression guard: it proves the cap does not fire where it should not.
- **If the rig reports it has no 5 GHz band** (unexpected, and the more valuable outcome): the cap
  should fire. **PASS** is `linkCapped=` naming 720p, the `asked for at most 720p and 30 fps` line
  present, and the `Negotiating a profile ...` probe line reporting `@30` rather than `@60`. Then
  repeat with `narrow-band-profile-cap=false` and confirm both revert and the line says
  `Nothing here has been changed for you`. Say clearly in the results which branch you took.
- **FAIL**: `linkCapped=` naming a resolution on a rig that reports a 5 GHz band, or the announced
  profile differing from settings in that case.

## 7. Do not re-run

- The cap's decision matrix. 13 JVM tests in `NarrowBandProfilePolicyTest` cover every combination of
  band answer, wireless/wired, cap on/off and starting frame rate, including that it never raises a
  value. R5 is about wiring only.
- Whether the band request reaches this rig's SoftAP. Settled: it does not, in either direction, and
  §7a and the credentials thread both record it. R4 only needs to know whether an AP came up.
- Anything about WiFi Direct group formation, the station stand-down or the poke. Untouched by all
  four commits.

## 8. Report back

Three numbers decide whether this ships:

1. **R1**: the count of `AapTransport stopping and sending byebye` after the service destroy, on the
   candidate and on the control. Expected 1 and 0.
2. **R4**: for each hotspot attempt, seconds since the last radio event, and whether an AP came up.
3. **R5**: the rig's own band answer, verbatim, and the `linkCapped=` value that followed it.

Plus the R0 test count and both md5s, and the `settings.xml` delta from §4 even if it is zero.
