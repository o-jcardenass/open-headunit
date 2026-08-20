# media-gap-instrument — round 2 brief

## 1. Build and baseline

| | |
|---|---|
| Branch | `fix/media-gap-instrument-and-attribution` |
| SHA | `93354419` (round 1 tested `3398c8cc`; this is one commit on top) |
| Base | `main` @ `e7a3b3ad` |
| History rewritten? | No. `3398c8cc` is still the parent, so a fetch and fast-forward is enough. |

```bash
git fetch fork fix/media-gap-instrument-and-attribution
git checkout -B media-gap-round2 fork/fix/media-gap-instrument-and-attribution
git log --oneline -2     # expect 93354419 on top of 3398c8cc
```

Not compiled since the change. R0 is a gate again.

## 2. What this is and why it exists

**Round 1's R2 was right and it changed the design.** The floor that was supposed to keep the video
series quiet on an idle screen assumed the screen goes *silent* — one long gap per window, suppressed
by requiring two. Your capture showed what really happens: a stationary Maps screen trickles. Four
windows, 2-5 gaps in each, `dead=95%`, `99%`, `96%`, `99%`, intervals scattered 3.3 s to 17.9 s. Every
isolated arrival closed one gap and opened the next, so the floor suppressed nothing and the
instrument shouted through three minutes nobody was touching.

The discriminator is not how many gaps there are, it is how much picture runs between them:

| | dead per window |
|---|---|
| reporter's fault, H.265 unjoined | ~55-60% |
| reporter's fault, H.264 unjoined (milder) | ~22% |
| **your idle Maps screen** | **95-99%** |

So the media series now also require a window to be **no more than 85% dead**
(`LinkGapMonitor.MAX_DEAD_PERCENT_MEDIA`). Past that it is a *stopped* picture rather than a
stuttering one, and a stopped picture already has instruments — the projection watchdog and the
decoder's throughput line. The link series keeps no ceiling, because a link that quiet really is dead.

Your R4 also retired the last prescription from the coexistence warning. It fired on your rig with
both frequencies known and 260 MHz apart, through ten minutes at 45-55 fps with no gap on any
channel. That is two units where the warned-about state was fine or better, and none where acting on
the advice helped, so the line now describes and stops. `Disconnecting the other network` no longer
exists in the source.

## 3. What is different about this round

**Short round. Two runs decide it, and one of them is R2 again.**

Three things from your round 1 to carry forward, all of them yours:

- **Check the installed build before anything else.** Round 1 opened on a release-signed
  `versionCode=98` build from another thread and lost time to `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.
  Same recovery if it recurs: back up `settings.xml`, uninstall, install the debug APK, push the
  backup back **before first launch**.
- **`enable-audio-sink` must be `true`.** Your restored `settings.xml` carried `false` from another
  round's build, which meant `ServiceDiscoveryResponse` never declared the audio sinks and the audio
  series was structurally never fed. Verify the key, not just the round's own settings list. Your
  finding, and it generalises: a `settings.xml` restored across threads reimports that thread's
  non-defaults.
- **The station stays joined.** Do not try to manufacture the unjoined arm. Your judgement not to run
  `cmd wifi forget-network 2` was right — the passphrase is unknown and the
  `link-stall-periodic-scan` thread depends on that association. The unjoined arm is now covered by a
  JVM test instead and is not asked for here.

No positive control is asked for on hardware this round. R3's audio waveform from round 1 is replayed
as a unit test at its measured numbers, so the "does it still fire when it should" half is covered in
R0 and does not need rig time.

## 4. Settings keys this round needs

Via `hur-wifi-test-scripts/set_hu_prefs.sh`, app stopped, single relaunch.

| Key | Type | Value | Why |
|---|---|---|---|
| `log-level` | int | `2` | INFO |
| `wifi-connection-mode` | int | `3` | Native AA |
| `view-mode` | int | `1` | TextureView |
| `enable-audio-sink` | bool | `true` | see §3 — round 1 lost a run to this being `false` |
| `debug-video-fault-injection` | int | `0` | no injection this round |

## 5. The lines that decide every run

Verified with `grep -F` against `93354419`.

| Grep for | Source | Means |
|---|---|---|
| `inbound video quiet ` | `LinkGapMonitor.kt` | the video channel went quiet, recurrently, in a window that still carried picture |
| `inbound audio quiet ` | same | the same for the three audio sinks |
| `inbound link quiet ` | same | every channel at once |
| `AapTransport: uplink blocked on ` | `UplinkStallMonitor.kt` | one of our writes held the send thread over 250 ms |
| `Disconnecting the other network` | — | **must not exist anywhere.** It was deleted from the source. |
| `This unit is connected to another WiFi network` | `StationCoexistencePolicy.kt` | the station is associated while the group is up |

`grep -a` on everything, as always — this round's headline result is again a count of an absent
pattern, and a refused count on a long line reads exactly like zero.

## 6. Runs

### R0 — build and unit tests. Gate.

```bash
./gradlew :app:assembleGithubDebug
./gradlew :app:testGithubDebugUnitTest
```

**PASS**: builds, all green. `LinkGapMonitorTest` is now **17** (was 12) and
`StationCoexistencePolicyTest` **8** (was 7); total should be **594** if nothing else on `main` moved.

Name these in the results:

- `LinkGapMonitorTest > a trickling idle screen says nothing either` — **your R2 capture, replayed at
  its measured numbers.** This test fails against `3398c8cc` and passes here. It is the whole round.
- `LinkGapMonitorTest > the reporter's own waveform still prints against the new ceiling` — the
  regression guard on the other side: the fault must still be reported.
- `LinkGapMonitorTest > the audio waveform a rig measured still prints` — your R3-final numbers
  (`dead=11%` and `18%`), replayed.
- `LinkGapMonitorTest > the link series keeps no ceiling`
- `StationCoexistencePolicyTest > no branch prescribes anything`

### R2 — idle screen, 3 minutes. **The point of the round.**

Repeat round 1's R2 exactly: a live session, Android Auto on a stationary Google Maps screen, no
navigation, untouched for three full minutes. Same screen, same conditions — this is a re-run, and
its value depends on it being the same test.

**PASS**: `grep -ac "inbound video quiet" r2.txt` is `0`.

**FAIL**: any line. Quote it in full — `dead=` is the field that decides whether the ceiling is merely
in the wrong place or the whole approach is wrong, and those need different answers. If `dead` is
below 85% on a screen you did not touch, say so loudly; that would mean an idle screen can look like
the fault and the discriminator does not exist.

Report `grep -ac "Throughput over" r2.txt` and the fps range alongside, so the round records what the
picture was actually doing while the instrument stayed quiet.

### R1 — clean session, 10 minutes. Regression.

Same as round 1's R1, which passed. Re-run because the reporting gate changed and the four zeroes are
the property that must survive it.

**PASS**, all four zero:

```bash
grep -ac "inbound link quiet"   r1.txt
grep -ac "inbound video quiet"  r1.txt
grep -ac "inbound audio quiet"  r1.txt
grep -ac "uplink blocked on"    r1.txt
```

Run this **before** R2 and reuse the session for R2 (§7a: reuse a live link), so both come off one
setup.

### R4 — the coexistence line, one grep.

No setup. Over either capture:

```bash
grep -ac "Disconnecting the other network"                r1.txt   # must be 0
grep -ac "This unit is connected to another WiFi network" r1.txt   # expect >= 1, W level
```

**PASS**: the first is `0` and the second still appears. Quote the line once — it should now end at
"read this as context for a report rather than as something to change."

**FAIL**: `Disconnecting` appears at all. That would mean the build is stale, not that the policy is
wrong; check the SHA before reporting it.

## 7. Do not re-run

- **R3.** It passed once the audio sink was enabled, and its waveform is now a unit test at the
  measured numbers. No rig time.
- **R5.** Inconclusive on its own predicted grounds at 1-in-300, and nothing in this commit touches
  the recovery ladder. Skip it entirely.
- **R4's unjoined arm.** Untestable here for reasons outside this round's authorization, and now
  covered by `StationCoexistencePolicyTest`.
- Anything from `link-stall-periodic-scan`.

## 8. Report back

Two numbers:

1. **R0**: green, with `LinkGapMonitorTest`=17 and the named tests listed.
2. **R2**: `inbound video quiet` on a stationary Maps screen. Must be `0`. If it is not, the `dead=`
   value in the line is the next thing to look at, so quote the line rather than just the count.

R1's four zeroes and R4's one zero are the regression guards. Everything else can wait.
