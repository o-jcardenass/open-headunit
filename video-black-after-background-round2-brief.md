# Round 2 brief — black screen after backgrounding: find a method that tears the surface down

Read `video-black-after-background-round1-brief.md` §2 for the three mechanisms; it is not repeated
here. Read `TESTING-TEMPLATE.md` §7a as always — it has **two new entries from round 1** (the
versionCode downgrade and `build_hur.sh` overwriting the previous APK) that this round depends on.

Round 1 answered a question nobody asked. Twelve scripted `KEYCODE_HOME` cycles across two builds,
two view modes and holds from 3 s to 120 s **never once produced `Decoder stopped: surfaceDestroyed`**
— so M1, M2 and M3 all sit behind a gate that never opened, and R3 could not isolate `9f98afd1`. That
is a fault in round 1's brief, not in the rig or the analysis: it assumed a Home press destroys the
projection surface and never asked anyone to check.

**This round is method-first.** R1 is the whole point; R2–R6 only run if R1 finds a method that opens
the gate. Do not spend the round on measurements taken behind a closed gate a second time.

---

## 1. Build

**No new builds needed** if round 1's APKs survive in `apks/round1-video-black/`:

| Build | Tag | SHA | md5 |
|---|---|---|---|
| **A** | `v.3.2.4` | `c9556803` | `5a19bdb1696d95ba2bc224de853e29da` |
| **B** | `v.3.2.3` | `e900de78` | `6489a53c822676c217b6a6adf0a1da70` |

Round 1 left **A installed**. Confirm with the md5 check (§5) rather than assuming. If the APKs are
gone, rebuild both per round 1 §1 and mind the two new §7a entries.

### R0 — gate check

Confirm A is live by md5. No unit-test run needed this round — nothing was rebuilt, and round 1
already recorded 244/244 with `DecoderStopPolicyTest` 6/6 on this SHA.

---

## 2. What this is and why it exists

The reporter's symptom is a black projection after leaving the app and coming back, **with audio still
playing**, recoverable only by fully closing the app. Every mechanism the source analysis found sits
behind one line — `Decoder stopped: surfaceDestroyed`. M1 needs `setSurface()` to call `stop()`, M2's
race is *inside* `stop()`, and M3 is about what fails to retry afterwards. No teardown, no defect.

So the question this round answers is narrow and entirely mechanical: **what makes this unit actually
destroy the projection surface?**

Two candidate explanations for round 1's null result, and the first is free to check:

- **The Home press never backgrounded the app at all.** `AapProjectionActivity: onPause` is logged at
  INFO, so round 1's own captures already say. Step 0 of R1 is to grep them — no rig time.
- **It backgrounded but stayed visible.** A head unit launcher that shows a dock or a translucent
  overlay pauses the activity without stopping it, so the window — and the surface — survive. That
  fits round 1's throughput continuing uninterrupted at 29–50 fps straight through a 120 s hold.

There is also a confound worth knowing about before it wastes a run. `AapBroadcastReceiver` relaunches
`AapProjectionActivity` with `FLAG_ACTIVITY_NEW_TASK` whenever a `ProjectionActivityRequest` broadcast
arrives and the state is `TransportStarted`, and `AapTransport.gainVideoFocus()` sends exactly that
broadcast when the phone re-runs media-sink setup on the video channel. **The app can pull itself back
to the foreground on its own**, without anybody touching the rig. If a cycle shows `onPause` followed
within a second or two by `onResume` that nobody asked for, that is this path, not a failed command —
record it, because it is a finding in its own right.

---

## 3. What is different about this round

**Media must be playing for every cycle.** The reporter is explicit that audio keeps working while
the picture is black, and that is the single most discriminating fact in the whole report: it says the
transport and the session are alive and only the video path died. A run with silent audio cannot tell
that apart from a dropped session, so a black screen observed without playing media is worth much less
than one observed with it. §7a: media keys alone do not open a fresh audio channel — restart the media
app on the phone (§5).

**The verdict logic is inverted from round 1.** There, a PASS meant the picture came back. Here, R1's
job is to open a gate, so R1 passes when **at least one method produces `Decoder stopped:
surfaceDestroyed`**. A method that does not is a recorded negative, not a failure.

**Re-arm the logcat capture after any USB drop.** Round 1 lost the framework stream when the unplanned
reboot cycled the transport, and it was not noticed until afterwards. Check the capture is still
advancing (`tail -1` timestamps moving) at the start of every run, not just at the start of the round.

**One method changes a global developer setting.** M-c sets `always_finish_activities`. Put it back to
`0` before the round ends and confirm — leaving it on would silently corrupt every future round on
this rig.

---

## 4. Settings keys this round needs

App preferences, unchanged from round 1:

| Key | Type | Values |
|---|---|---|
| `log-level` | int | `2` INFO |
| `log-source` | int | `1` APPLOG_FILE |
| `log-capture-enabled` | boolean | `true` |
| `view-mode` | int | `1` TEXTURE · `2` GLES in R4 |
| `video-codec` | string | `Auto` · `H.264` in R5 |

One **global** setting, for M-c only:

```bash
adb shell settings put global always_finish_activities 1     # destroy activities when backgrounded
adb shell settings get global always_finish_activities       # verify
adb shell settings put global always_finish_activities 0     # MUST be restored before the round ends
```

---

## 5. Driving it

### Start media before every cycle

Per §7a, focus is re-evaluated when the channel opens, not per track, so a media key alone will not
open one:

```bash
adb -s <phone> shell am force-stop com.spotify.music
adb -s <phone> shell monkey -p com.spotify.music -c android.intent.category.LAUNCHER 1
sleep 5
adb shell input keyevent KEYCODE_MEDIA_PLAY          # 126, through the head unit
```

Confirm audio is actually running before starting the cycle — `AapAudio: AA audio started (` must be
in the log, and `dumpsys audio` should show an active player. **Do not start a cycle without it.**

### The backgrounding methods

```bash
PKG=com.andrerinas.headunitrevived
ACT=$PKG/com.andrerinas.openheadunit.aap.AapProjectionActivity
MAIN=$PKG/com.andrerinas.openheadunit.main.MainActivity
```

| Id | Method | Command |
|---|---|---|
| **M-a** | opaque third-party activity on top | `adb shell am start -a android.settings.SETTINGS` |
| **M-b** | the app's own settings UI, same task | `adb shell am start -n $MAIN` |
| **M-c** | force destruction while backgrounded | `settings put global always_finish_activities 1`, then `adb shell input keyevent KEYCODE_HOME` |
| **M-d** | round 1's method, as negative control | `adb shell input keyevent KEYCODE_HOME` |

Return in every case with `adb shell am start -n "$ACT"`, then observe 60 s.

M-a is closest to the reporter's own words ("switch to another app"). M-c is closest to what a long
background does on a memory-constrained unit, and is the only method that guarantees the activity is
destroyed rather than merely stopped — which is also the variant that shows the "Android Auto is
starting…" overlay, because a recreated activity starts with it visible.

### Record the actual state, not the intent

For each method, capture what the system thinks happened:

```bash
adb shell dumpsys activity activities | grep -iE "mResumedActivity|topResumedActivity|mFocusedApp"
```

before the background command, ~3 s after it, and after the relaunch.

---

## 6. The lines that decide every run

All verified with `grep -F` against the source; all INFO or above, so `log-level=2` carries them.

| Meaning | Level | Line |
|---|---|---|
| **the gate — the whole round turns on this** | I | `Decoder stopped: surfaceDestroyed` |
| the activity was backgrounded | I | `AapProjectionActivity: onPause` |
| the activity came back | I | `AapProjectionActivity: onResume` |
| a new surface arrived | I | `New surface set: ` |
| the decoder was rebuilt | I | `Configuring decoder: ` |
| the picture came back | I | `First frame rendered (hardware decode)` |
| sustained video rate | I | `Throughput over ` |
| **audio channel opened** | I | `AapAudio: AA audio started (` |
| **audio stopped — absence is the pass** | I | `last AA audio channel stopped` |
| M1 — the codec-type flip | E | `Falling back to ` |
| M1 — the latch | E | `Giving up to avoid an infinite restart loop` |
| M2 | E | `Error feeding input buffer` |
| M2 | E | `Failed to start decoder` |
| M2 — thread-publication race | I | `Feed thread started` immediately followed by `Feed thread stopped` |
| M3 — absence is the signal | W | `Watchdog: No video received yet` |

From logcat rather than the app file, and only when chasing M2: any `ACodec` / `OMX` error, or
`createByCodecName` failing on the return.

---

## 7. Runs

### R1 — method probe — **this is the point of the round**

**Step 0, free, no rig time:** grep round 1's saved captures for `AapProjectionActivity: onPause`.
Report whether it is there. If it is absent, round 1's Home press never backgrounded the app and M-d
is confirmed inert; if it is present, the activity paused and the surface survived anyway, which is a
different and more interesting fact. Either way, continue to the cycles below.

Build A, `view-mode=1`, `video-codec=Auto`, media playing. One cycle per method, `HOLD=20`, in the
order **M-a, M-b, M-c, M-d**, resetting to a known state between them (§3a).

**PASS: at least one method produces `Decoder stopped: surfaceDestroyed`.**
**FAIL: none of the four does.**

For each method record, as a table: whether `onPause` fired, whether the gate line fired, whether an
unrequested `onResume` followed within 5 s (the self-relaunch confound in §2), what
`dumpsys activity activities` showed, and whether audio continued.

**Stop condition.** If R1 fails — no method opens the gate — **stop the round there and report**. Do
not run R2–R6; they would repeat round 1 exactly. That is this brief answering the question in
advance, not an invitation to ask.

Call the method that opens the gate **M-win**. If more than one does, prefer M-a (closest to the
report); note the others.

### R2 — graduated holds, 3.2.4, TEXTURE, using M-win

Build A, `view-mode=1`, `video-codec=Auto`, media playing. One cycle each at `HOLD=10`, `HOLD=30`,
`HOLD=90`.

**PASS:** `First frame rendered (hardware decode)` within 60 s of each relaunch.
**FAIL:** any cycle without it. **Stop at the first failure**, keep both captures — later cycles
overwrite the evidence of which mechanism fired first, which is the only thing this round decides.

Record for every cycle: seconds from `New surface set:` to `First frame rendered`, and **whether audio
continued** (absence of `last AA audio channel stopped`). If audio stops too, say so loudly — the
reporter's symptom keeps audio, so a run that loses both is a *different* fault and the analysis in
round 1 §2 does not apply to it.

### R3 — the same holds on 3.2.3, using M-win — the A/B

Build B (`adb install -r -d`, see §7a). Everything else identical.

- **R2 fails where R3 passes** → M2 implicated, `9f98afd1`'s threading gets fixed.
- **R2 and R3 fail identically** → M2 is not the cause; the 3.2.4 decoder work is left alone and the
  fix targets M1 and M3.

### R4 — GLES, 3.2.4, using M-win

Build A, `view-mode=2`. `HOLD=45`, then `HOLD=120`.

Round 1 found GLES held a steady ~50 fps through a full 120 s Home hold with no teardown at all —
the opposite of that brief's prediction. Worth re-testing under a method that actually backgrounds
the app, because GLES only reports surface destruction from `onDetachedFromWindow`, and M-c destroys
the activity outright where a Home press does not.

### R5 — codec-flip probe, 3.2.4, TEXTURE, using M-win

Build A, `view-mode=1`, **`video-codec=H.264`**. One cycle at `HOLD=30`, observe 90 s.

**PASS:** no `Falling back to ` anywhere in the capture.

Round 1 confirmed the explicit choice is honoured at session start
(`selected=c2.unisoc.avc.decoder`), so if a fallback line appears now it is the restart path
overriding a user's explicit setting — M1 caught in the act, and a second defect confirmed.

### R6 — latch probe — only if R2, R4 or R5 produced a black screen

Immediately after that failure, without restarting app or session: one more cycle with M-win at
`HOLD=10`, observe 60 s.

- **Picture returns** → nothing latched; the failure is transient.
- **Never returns, and `Giving up to avoid an infinite restart loop` is in the capture** → **M1
  confirmed.**
- **Never returns, that line absent** → something is wedged below us; check logcat for `ACodec` /
  `OMX` / `createByCodecName`, which points at M2.

Then force-stop and relaunch and confirm the picture returns — the reporter's stated workaround. If
it does not, that is a bigger finding than anything else here.

---

## 8. Do not re-run

- **Plain Home-press holds.** Round 1 settled it across 12 cycles, both builds, both view modes,
  3 s to 120 s. M-d appears in R1 once, as a control, and nowhere else.
- **USB.** §7a: no accessory path on this rig.
- **Reaching renderer or codec settings through the UI.** Both are `settings.xml` keys.
- **Unit tests.** Nothing is rebuilt this round.

---

## 9. Report back

1. **Which method opened the gate**, and the R1 table. If none did, that is the answer and the round
   stops — say so plainly, it is the most useful negative result available.
2. **Did R2 fail where R3 passed?** One sentence. This decides whether `VideoDecoder`'s threading is
   touched at all.
3. **Did audio survive every cycle?** Specifically, did `last AA audio channel stopped` ever appear
   alongside a black screen.
4. **The recovery times in seconds**, every cycle of R2 and R3, passes included.
5. Which mechanism's lines appeared, R6's verdict, and whether R5 printed `Falling back to `.
6. Whether `always_finish_activities` was restored to `0`.

And anything noticed in passing — round 1's headline finding came out of that section.
