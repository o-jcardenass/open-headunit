# pr-merge-train, round 4 results: the audio renderer on the two slowest units

**Candidate (A):** `fork/test/pr-merge-train` @ `3ad7c78c8860d7da3f4a2429157931a36a03129f`   **Baseline (B):** `main` @ `f9a72d480d51105870f62df6f0a40a1276e98a34`
**APK md5:** A `f365e15aa2dbaf31972c68d09859d278` / B `7dbddf19ee63fa0d0772dcf32878b337` (round 1's two APKs, no build; `NativeTimeStretchEngine` count 6 on A in both units' installed APK, 0 expected on B and not re-measured)
**Unit:** stage S: D-SAM (SM-T230, Android 4.4.2) as head unit, Native wireless, D-POCO as phone. Stage H: D-HP (HP Slate 7 Plus, Android 4.2.2) as head unit, **Headunit Server over the LAN**, D-MOTO as phone (operator deviation, see Setup notes).
**Date:** 2026-10-26
**Evidence:** release `rig-evidence-pr-merge-train`, asset `pr-merge-train-round4-captures.zip`, sha256 `fd2f968f84402221babfb49affcd8a8af7895c08e850999d251bb75418a96fe7`

## Setup notes

**Operator deviation, stage H.** The operator asked for D-HP in Headunit Server mode instead of USB, and Spotify on D-MOTO instead of VLC with `ohu-stutter-long.mp3`. Consequences: `connection-modes=wifi`, `wifi-connection-mode=1`; no cable move, no OTG, no replug; both tablets stayed on USB adb; the connect step is `ACTION_CONNECT --es ip 192.168.1.13` (D-HP 192.168.1.5, D-MOTO 192.168.1.13, same LAN) and only if the app's own Auto discovery has not formed a session in 30 s; L-ACC does not apply. Stage H therefore compares Headunit Server with itself, not USB, and its result says nothing about the USB path.

**Gates waived or deviated.** D-HP battery read 46% at the start and 54% (AC powered) at its runs, against a 60% gate; the operator said all was done and I proceeded. D-SAM read 100% on AC. D-MOTO read 58% and 57%.

**Brief omission, both units: `narrow-band-profile-cap=false` written.** With the default `true`, a 2.4 GHz wireless session announces AAC to the phone even with `use-aac-audio=false` (log: `AAC audio announced by the 2.4 GHz cap`). D-SAM is 2.4 GHz only (P2P channel 6, 2437 MHz) and its first full run showed `codec=AAC_LC` on every channel, which fails the brief's session gate (L-DEC not AAC). I wrote the key on D-SAM and, for the same reason, on D-HP. All four graded runs show `codec=PCM` on the media channel.

**Stage S needed five voided attempts before SB (14:32 to 14:49).** Captures kept as `attempt1-` to `attempt5-`.
1. D-SAM auto-picked D-MOTO as driver, and D-POCO's Gearhead dialled D-HU's radio (`GH.WIRELESS.BT Creating rfcomm socket for device 11:46:03:10:33:59`, A2DP `Connecting to` the same address at 14:32:30). The app's `never opened the Android Auto channel` warning did not print. The operator unpaired D-HU on D-POCO (the brief's section 7 path). **D-POCO must be paired with D-HU again before the next D-HU round.**
2. The `native-preferred-device-mac`, `native-mac` and `last-connected-native-mac` keys (stale from an earlier thread, holding D-MOTO's address) were set to D-POCO's address; D-SAM still picked D-MOTO.
3. `native-driver-selection-mode=0`: only D-MOTO was poked. D-POCO never appeared, which showed a one-sided bond; the operator re-paired D-POCO from D-SAM.
4. Same result with the bond fixed. The cause was the **stale set `native-poke-bt-macs`**, which held D-MOTO's address. Rewritten as a set holding D-POCO's address only (`DC:B7:2E:5E:4E:59`). D-POCO was then poked (`Successfully poked POCO X3 NFC via HFP-AG`, 14:42:42) and a session formed (SSL 14:42:48).
5. That session ran 5 minutes and failed the AAC gate (above), so it was not graded.

These keys are not in the brief's table: `native-poke-bt-macs` (set), `native-driver-selection-mode=0`, `native-preferred-device-mac`, `native-mac`, `last-connected-native-mac`, `narrow-band-profile-cap=false`. They were left identical between SB and SA (settings.xml diffed identical after the A install on both units).

**Stage H needed four voided attempts before HB (15:05 to 15:19).** Captures kept as `attempt1-` to `attempt4-`.
1. Key 126 left Spotify paused, so no `Media Start Request AUDIO` until a manual key; silence sat inside the window.
2. In Auto mode the app connected by itself at 15:07:11 and the script's `ACTION_CONNECT` at 15:07:13 broke that session (`Socket broken`). Three further handshakes got `the peer accepted the connection and then sent nothing at all` (D-MOTO's Gearhead head unit server wedged for about 90 s; `GH FATAL EXCEPTION Thread-2` at 15:08:01). The window had 20 mixer lines, phone `Critical error` 2 and Gearhead FATAL 3, so it failed the gate. `stage-h.sh` now marks first, launches, waits 30 s for the automatic session and sends `ACTION_CONNECT` only if none formed.
3 and 4. Spotify paused itself or flapped (`Media Start Request AUDIO` session=0 to 4 in 42 s) from repeated 126 and 85 keys; Spotify's cold start takes longer than a 4 s poll.

**Known defect in my script, identical in HB and HA.** `spst()` greps `state=[A-Z]*` and matches `state=P` of `state=PlaybackState`, so it never reads PLAYING. Each graded stage H run therefore sent key 126 at 5 s after the session, key 85 after 20 s (which paused the music), and key 126 again at the music mark. Marks `KEY126_NOT_ENOUGH` and `PAUSED_AT_MUSIC_MARK` in `marks.txt` for both. In HB, `Media Start Request AUDIO` session=1 came at 15:20:06 and `HB-music` at 15:20:11; in HA, 15:28:11 and 15:28:16. The graded window starts after the final resume in both, so the arms match; I did not fix the script mid-round.

**Operator listening.** `heard.sh` was not run; HEARD stamps are 0 in both stage H runs, so the heard comparison is empty.

**Grader.** `scripts/grade.py` (one-round script in the evidence folder) reads each capture with duplicate lines removed, takes the window from the `<run>-music` marker to `<run>-end`, and sums increases between consecutive channel 6 lines (a fall adds the new value). A slow window is an L-MIX line whose `loopGapMax` in ms exceeds `effective=`/48. No capture was restarted by `alive` (0 `capture restarted` marks). Counts were made with the script, not read from a capture whole.

**Other.** Quirk files read: tooling, audio, D-SAM, D-HP, D-SAM-and-D-HP, D-POCO, D-MOTO. Tools used: `rig_preflight.sh`, `rig_cleanup.sh`, `set_prefs_runas_host.py`, `apk_identity.sh`, `rig_thermal.sh`. Host thermal: package max 61C (SB), 62C (SA), 65C (HB), 66C (HA); throttle counter 0 in all four. D-HU's Bluetooth read ON at 14:37, 6 minutes after `svc bluetooth disable` at 14:31 (known self-revert); its app was not running; the bond with D-POCO was already removed. L-TC (`Audio time correction: selected=`) printed 0 times in SA and HA: on `3ad7c78c` that line appears only on the fallback path, so the engine in force is the default `ohu`. Prior builds: D-SAM `892812235b9ea3154cd7a86357cdefbe`, D-HP `63404eac1bef5f3a23593712775cd0bf`. A stale `com.brave.browser` media session on D-MOTO read PLAYING with position -1 throughout; it was not touched.

Opening pre-flight (`preflight-open.txt`): D_SAM wifi 1 bt 1, D_POCO 1/1, D_HP 1/1, D_MOTO wifi 1 bt 0, D_HU 1/1; PREFLIGHT OK, no strays.

## SB

**PASS**

- Control, D-SAM, arm B, Native wireless, graded attempt 6 (14:49:52 to 14:56:04, D-SAM clock). Settings: brief table plus the keys above.
- Gate: L-SSL 1, L-MUS 1, L-DEC media `codec=PCM`, `latencyMultiplier=2`, `queueCapacity=20`, L-MIX 30, FATAL 0, UnsatisfiedLinkError 0, phone `Critical error` 0, Gearhead FATAL 0, L-WARN 0.
- Measured (music to end window): damage **25179** (concealed 4320, stale 0, compressed 20859); slow windows 0; largest `loopGapMax` 49 ms; arrivalGapMax 1517 ms; L-OVF 0; L-QF 0; S-UND 0; median rendered 142.
- Reachability: damage 25179 is above 4800, so the fault is reachable on this unit.
- Discard rules: no `MATCH! Starting AapService`, no `Magic Garbage`, one SSL, two `createGroup SUCCESS` (D-SAM's normal settle before SSL), P2P interface `p2p-wlan0-0`.

## SA

**PASS**

- D-SAM, arm A (md5 `f365e15a`, installed 14:56:20), graded run 14:57:31 to 15:03:42.
- Gate met: L-SSL 1, L-MUS 1, PCM, L-MIX 30, FATAL 0, phone clean.
- Measured: damage **29665** (concealed 20555, stale 0, compressed 9110); slow windows 0; largest `loopGapMax` 56 ms; arrivalGapMax 974 ms; L-OVF 0; L-QF 0; S-UND 0; median rendered 136.
- Against SB: 29665 is at most 1.5 x 25179 = 37768 (and above 4800); slow windows 0 is at most 0 + 3; L-OVF and L-QF are at most B's + 1; no FAIL lines. **No worse.**
- **Not better:** damage 29665 is 1.18 x B's, not at most half. The mix of damage differs (A conceals 20555 and compresses 9110; B conceals 4320 and compresses 20859) and the sum is within run noise on one run each.

## HB

**PASS**

- Control, D-HP, arm B (md5 `7dbddf19`, installed 15:04:43), Headunit Server over LAN, graded attempt 5 (15:18 to 15:25:12, window from 15:20:11).
- Gate met: L-SSL 1 (the app's own discovery), L-MUS 2, PCM, L-MIX 30, FATAL 0, phone `Critical error` 0, Gearhead FATAL 0.
- Measured: damage **29397** (concealed 0, stale 0, compressed 29397); slow windows **22**; largest `loopGapMax` 99 ms; arrivalGapMax 209 ms; L-OVF 0; L-QF 0; S-UND 0; median rendered 150; HEARD 0.
- Reachability: damage above 4800 and 22 slow windows, so the fault is reachable here.

## HA

**PASS**

- D-HP, arm A (md5 `f365e15a`, installed 15:26:14), Headunit Server over LAN, run 15:27:07 to 15:33:17 (window from 15:28:16).
- Gate met: L-SSL 1, L-MUS 2, PCM, L-MIX 30, FATAL 0, phone clean.
- Measured: damage **8441** (concealed 1335, stale 0, compressed 7106); slow windows **19**; largest `loopGapMax` 125 ms; arrivalGapMax 189 ms; L-OVF 0; L-QF 0; S-UND 0; median rendered 150; HEARD 0.
- Against HB: 8441 is at most 1.5 x 29397; slow windows 19 is at most 22 + 3; L-OVF and L-QF are 0; no FAIL lines. **No worse.**
- **Better:** damage 8441 is 0.29 x B's (limit 0.5) and slow windows 19 are at most B's 22. The largest single `loopGapMax` was higher on A (125 ms against 99 ms), a point against it that the count does not show.

## Report back (brief section 9)

1. Per unit, B against A (damage = concealed + stale + compressed):

| Unit | Arm | Damage | Concealed | Stale | Compressed | Slow windows | Largest loopGapMax | L-OVF | L-QF |
|---|---|---|---|---|---|---|---|---|---|
| D-SAM | B | 25179 | 4320 | 0 | 20859 | 0 | 49 ms | 0 | 0 |
| D-SAM | A | 29665 | 20555 | 0 | 9110 | 0 | 56 ms | 0 | 0 |
| D-HP | B | 29397 | 0 | 0 | 29397 | 22 | 99 ms | 0 | 0 |
| D-HP | A | 8441 | 1335 | 0 | 7106 | 19 | 125 ms | 0 | 0 |

2. Reachability: both units show the fault in B (damage above 4800). A is no worse on both. A is better on D-HP (Headunit Server, 0.29 x), not better on D-SAM (1.18 x, one run each).
3. Stage H heard counts: 0 and 0 (no stamps taken). No `FATAL EXCEPTION` naming `com.andrerinas` and no `UnsatisfiedLinkError` in any graded window. The voided HB attempt 2 had a Gearhead `FATAL EXCEPTION: Thread-2` on D-MOTO at 15:08:01 (not our process).

## Anything the brief did not ask about

- **The 2.4 GHz AAC cap hides the PCM case.** On any 2.4 GHz-only head unit in a wireless session, the default `narrow-band-profile-cap=true` announces AAC regardless of `use-aac-audio=false`. A PCM brief for such a unit has to write the key, or it measures AAC. The brief's key table should carry it.
- **Stale poke and driver keys carried between threads.** `native-poke-bt-macs` (a set), `native-mac`, `last-connected-native-mac` held D-MOTO's address and made D-SAM wake the wrong phone for three attempts. A fresh-backup diff at the start of the round would have caught it.
- **In Headunit Server Auto mode a script must not send `ACTION_CONNECT` first.** The app connects by itself within about 3 s of launch; an explicit connect 2 s later breaks the session and wedged D-MOTO's Gearhead server for about 90 s.
- **Spotify's cold start on D-MOTO exceeds 4 s, and its keys are asymmetric.** Key 126 starts it from cold; key 85 toggles. Poll the `PlaybackState` token `state=PLAYING(3)` correctly (the grep in this round's script was wrong, see Setup notes).
- **Compression versus concealment trade.** On D-SAM, A shifted damage from compression to concealment (20555 concealed against 4320). The sum is what the brief grades, but concealed frames are the audible kind; this deserves a second run before the PR text claims anything about D-SAM.
- **D-HP is on a 54% battery and AC power** throughout; D-SAM's clock read 25 s behind the host at the start.
- **Evidence:** `pr-merge-train-round4-captures.zip`, asset of release `rig-evidence-pr-merge-train`, sha256 `fd2f968f84402221babfb49affcd8a8af7895c08e850999d251bb75418a96fe7` (APKs excluded; they are round 1's).
