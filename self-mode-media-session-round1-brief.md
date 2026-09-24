# Round 1 brief, Self Mode media session and media buttons

Read `TESTING-TEMPLATE.md` first. Two things in it decide this round: §3's "Media transport" block
is **wrong for the candidate build** and §7a's D-POCO entries.

This round grades a change that is deliberately confined to **Self Mode**. Half of it is therefore a
regression guard on an ordinary session, and that half is the half to run first.

---

## 1. Build

**Candidate:** `fix/929-self-mode-media-session` @ `665332d8` on `fork`, one commit on `origin/main`
@ `80a81099` (`v.3.4.0`).
**Baseline (R1 only):** `origin/main` @ `80a81099`.

### R0, build gate

`run_unit_tests.sh`, then `build_hur.sh`.

- Full suite **2168** tests, green.
- `MediaSessionOwnershipPolicyTest` must exist and report **2**. If the class is missing, the wrong
  commit is checked out.
- `MediaKeyRoutingPolicyTest` **8**, up from 6.

Record the APK md5 and confirm it is live per §5. **If R0 fails, stop and report.**

---

## 2. What this is

A reporter on a Roco K706 running Self Mode sees the Android Auto media widget name **Open Headunit**
instead of his player, and his physical next and previous stop working whenever a navigation app has
the main pane. The full read is `ohu-fixes-handoff/929-analysis.md`; the two facts that matter here:

**The app holds an active, playing `MediaSession` even in Self Mode**, where it announces no media
sink and so never carries the audio at all. The player is a local app with a real session of its own
on the same device, and ours is simply the more recent one, so it wins the framework's media-button
routing and whatever Gearhead binds its media card to.

**Every media key the app receives goes out on the AAP input channel**, which Android Auto delivers
by input focus. So the key that our session just took is handed back to whichever app is on screen,
and dies there unless that app is the player.

The candidate does three things:

1. A loopback session no longer holds an active media session. Ownership is restored on disconnect,
   so any other transport is untouched.
2. `MediaKeyRoutingPolicy` declines media keys for a loopback session, ahead of every mode arm,
   `ALWAYS` included.
3. A media key the policy declines now falls through to the system instead of being swallowed by
   `AapProjectionActivity.dispatchKeyEvent`, which returned `true` unconditionally. Without this,
   holding a key back only ever meant the button did nothing at all.

---

## 3. Preconditions and what is different about this round

**Self Mode needs Android Auto's "Start head unit server" developer toggle on**, and it is UI only.
Check it before the round, ahead of the install step, and escalate once if it is off:

```bash
adb -s <serial> shell cat /proc/net/tcp | grep -i :149D     # 0x149D == 5277
```

See `rig-dpoco-headunit-server-down` in §7a: on D-POCO this is one unit's own state after a
force-stop, not a Gearhead version fact, and the toggle is the only way back.

**The unit for the Self Mode runs is D-POCO**, which is both the phone and the head unit there. The
non-loopback runs (R4, R5, R6b) are an ordinary Native AA session, D-HU as head unit with D-POCO as
the phone.

**A local player with a real media session has to be playing** for R1 to R3. Any player that
publishes a `MediaSession` will do for the session-ownership and key runs; only the widget
observation in R3c needs a player Android Auto itself lists, and that observation is optional.

**Set VERBOSE.** `MediaSession: State updated to ...` is `AppLog.d` and is absent at INFO, which is
what R2 and R6 are counted on. The two new lines are INFO and survive either way.

| Key | Type | Value |
|---|---|---|
| `log-level` | int | `0` (VERBOSE) |
| `log-source` | int | `1` (APPLOG_FILE) |
| `log-capture-enabled` | boolean | `true` |
| `media-key-routing` | int | `0` (ALWAYS) except in R5 |

**Check `key-codes` is absent in `settings.xml` before the round**, or an injected code arrives
remapped and every count below is wrong.

**`adb shell input keyevent 87` is the right injector here and it is not a stand-in.** It goes
through the framework's media-button routing to whichever session wins, which is exactly the stage a
steering-wheel press reaches. That makes it the instrument for the whole question, not a substitute
for one.

**§3 of the template is now wrong for this build and says so nowhere.** It states that the app holds
an active `MediaSession` and relays `input keyevent` to the phone over AAP. On the candidate that
stops being true in Self Mode, which is the point of the change. Any later round that uses
`input keyevent` to drive a player through a Self Mode session has to drive the player directly
instead.

---

## 4. The lines that decide every run

| Meaning | Level | Line |
|---|---|---|
| ownership declined for this session | I | `AapService: media session left to the player on this device (Self Mode)` |
| media key held back | I | `CommManager: Not sending media key 87 to Android Auto (routing=ALWAYS, selfMode=true, src=projection)` |
| media key forwarded | I | `CommManager: TX Key -> AA=87 (isPress=true) src=projection` |
| our session claims playback | **D** | `MediaSession: State updated to PLAYING, positionMs=...` |

Standing counts:

```bash
grep -c 'media session left to the player' rN.txt
grep -c 'Not sending media key'            rN.txt
grep -c 'TX Key -> AA=8[578]'              rN.txt
grep -n  'MediaSession: State updated to'  rN.txt
```

And, on the device, during a live session:

```bash
adb shell dumpsys media_session | grep -iE "package|active|state=" | head -20
```

---

## 5. Runs

Run **R4 first**. It is the regression guard, and if it fails nothing else about this branch matters.

### R4, an ordinary session is untouched, candidate

Native AA, D-HU as head unit, D-POCO as the phone, projection in the foreground, a track playing.
Press 87, then 88, then 85, about 1 s apart.

**PASS:** three `TX Key -> AA=` pairs with matching releases, **zero** `Not sending media key`,
**zero** `media session left to the player`, and at least one
`MediaSession: State updated to PLAYING`.
**FAIL:** any of those four counts moves. This is the run that says the change stayed inside Self
Mode.

### R1, the defect on this rig, baseline

`origin/main` @ `80a81099` installed on D-POCO. Self Mode up, projection in the foreground, a local
player playing, a navigation app given the main pane inside Android Auto.

```bash
adb shell dumpsys media_session | grep -iE "package|active|state=" | head -20
adb shell input keyevent 87
```

Report: (a) which package holds the top active session; (b) whether the track changed; (c) whether
the capture carries `TX Key -> AA=87`.

**This run has no verdict on the branch.** It establishes that the rig can show the fault. If the
top session is already the player on the baseline, say so and mark R2 and R3 **INCONCLUSIVE**: the
rig cannot demonstrate a fix for something it does not reproduce. Restore the candidate APK
afterwards and re-confirm the md5.

### R2, the candidate does not take the session

Candidate on D-POCO, same setup as R1.

**PASS:** `media session left to the player on this device (Self Mode)` appears exactly once per
session, `dumpsys media_session` names the local player rather than
`com.andrerinas.headunitrevived`, and **no** `MediaSession: State updated to PLAYING` appears for the
whole Self Mode session.
**FAIL:** any PLAYING line, or our package still on top.

### R3, the buttons reach the player

Candidate, Self Mode, a **navigation app in the main pane** and the player in the background. This is
the reporter's failing configuration.

**R3a.** `input keyevent 87`, then 88, then 85, 2 s apart.
**PASS:** the track changes on each, and the capture carries `Not sending media key 87 ...
selfMode=true` with **no** `TX Key -> AA=87`.
**FAIL:** the track does not change, or the key is forwarded.

**R3b, the control.** Bring the player to the main pane and repeat. **PASS:** the track still
changes. A fix that only works with the navigation app focused is not a fix.

**R3c, observational.** If Android Auto lists the player, say what its media widget names, before and
after. No verdict rests on this; give the name, not an adjective.

### R5, a declined key falls through instead of vanishing

Ordinary Native AA session, D-HU, `media-key-routing=2` (NEVER). Start a local player on the head
unit and confirm it is playing. Press 87.

**PASS:** no `TX Key -> AA=87`, a `Not sending media key 87 ... (routing=NEVER, selfMode=false,...)`
line, **and the head unit's own player advances a track**. That last clause is the whole run: on
`main` the key reached nothing at all.
**FAIL:** the local player does not move.
**INCONCLUSIVE:** nothing on D-HU publishes a media session to receive it. Say so; that is a rig
fact, not a branch defect.

Set `media-key-routing` back to `0` afterwards.

### R6, ownership comes back

**R6a.** End the Self Mode session with `headunit://exit`, then start it again.
**PASS:** the declined-ownership line appears once per session, not accumulating, and no PLAYING line
in either.

**R6b.** After a Self Mode session has run and been exited, bring up an ordinary Native AA session on
the same install.
**PASS:** `MediaSession: State updated to PLAYING` returns and 87 forwards as in R4. **FAIL:** the
session stays deactivated, which would mean the disconnect path did not restore it.

---

## 6. Do not re-run

- Anything about the poke, the P2P group, the hotspot, audio focus or video. Nothing on this branch
  touches them. A change there is a rig variable and belongs under "Anything the brief did not ask
  about".
- The policy transition tables. `MediaSessionOwnershipPolicy` and `MediaKeyRoutingPolicy` are pure
  and R0 runs their tests on the JVM.

---

## 7. Report back

Four answers decide whether this ships:

1. **R4**, the four counts, unchanged from `main`.
2. **R1(a) against R2**, which package held the top session before and after.
3. **R3a**, whether the track changed with a navigation app in the main pane.
4. **R5**, whether the head unit's own player moved, or why nothing could receive the key.
