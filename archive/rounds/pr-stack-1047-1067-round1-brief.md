# pr-stack-1047-1067, round 1 brief: the contributor's five PRs merged as they will land, one short final round, about 50 minutes

This round replaces `pr-1045-framing-round3-brief.md` and `pr-1067-tls-pump-round3-brief.md`. **Do not run either of them.** Both pin heads that the contributor has since rebased, and the #1067 code they test no longer exists.

It tests the outside contributor's open PRs once, merged in the order they will land: #1047, then #1045, #1064 and #1065, then #1067. `pr-1047-session-reconnect-round3-brief.md` stays queued on its own and tests #1047's Save paths. This round tests what the other four add on top: the framing and reassembly (#1045), the TLS pump and the quit path (#1067), and that the whole stack holds one long session and one clean exit. **The owner will merge nothing else into `main` until these PRs merge, so `main` stays at `77914f18` and this pin does not drift.**

| Stage | Units | Runs | Hand steps | Time |
|---|---|---|---|---|
| **W** | D-HU (head unit, Native AA) with D-POCO | R0, A1-S, K-S, XS | none | 35 min plus the build |
| **U** | D-POCO as head unit with the dongle, D-MOTO as its phone | US | the dongle cable | 10 min |

**Run Stage U on the same day as `pr-1047-session-reconnect` round 3, straight after its Stage U, while D-POCO is still on wireless adb with the dongle on its OTG port.** That shares the cable setup. If that round already ran and the cables are back, Stage U needs the operator for H1 at its start and end.

## 1. Build

One APK, S. No baseline arm (section 2 says why).

| Arm | Code | Identity | Gate |
|---|---|---|---|
| **S** (the stack) | PR #1067 head `e4116c17df8c505c3d3db8235d56f6cfe408805f` (it already contains #1047 `7cc36de8` and #1045 `dbe0004d` on `main` `77914f18`), with #1064 `e6b24905` and #1065 `abd732c3` merged in | tree `2c4b06fd2c77cc8a25bcb88f775b5d51ec12d1ad` | 2995 JVM tests, 0 failures |

```bash
git fetch https://github.com/andreknieriem/open-headunit.git main pull/1047/head pull/1045/head pull/1064/head pull/1065/head pull/1067/head
for h in 7cc36de8212368c4e0099bb286a36362b44cfee8 dbe0004d4794d029082994cc2e9ee5a5aa93d3c2 e6b249051a8610b717b78bf2fe6aaf95b56410cf abd732c3d12c959949f53f9df4398b690ec66fe6 e4116c17df8c505c3d3db8235d56f6cfe408805f; do git cat-file -e $h || echo "MISSING $h"; done
for n in 1047 1045 1064 1065 1067; do git ls-remote https://github.com/andreknieriem/open-headunit.git refs/pull/$n/head; done   # each MUST print the full SHA above, in the same order
git checkout -B arm-S e4116c17df8c505c3d3db8235d56f6cfe408805f
git merge --no-edit e6b249051a8610b717b78bf2fe6aaf95b56410cf    # PR 1064
git merge --no-edit abd732c3d12c959949f53f9df4398b690ec66fe6    # PR 1065
git rev-parse 'HEAD^{tree}'                                     # MUST print 2c4b06fd2c77cc8a25bcb88f775b5d51ec12d1ad
```

**If a SHA is missing, a head moved, a merge conflicts, or the tree differs, stop the round and escalate.** Both merges are clean today; nothing in this brief resolves a conflict. The merge commits stay local: do not push them. Cool the host to 70C first and build with `--max-workers=2`. No `run_unit_tests.sh` this round: we ran the suite on this exact tree (2995, 0 failures), and each PR's own CI passed its unit tests.

### 1a. Identity gate

Pull the installed APK and count each PR's symbol in the DEX. Every count must be 1 or more; `main` has 0 of each.

```bash
adb -s $HU shell pm path $PKG     # pull it to ./installed.apk, then:
for s in 'SettingsRestart: route=' AapMessageReassembler ConnectionAdmissionRejectedException AutomaticReconnect 'encrypted write failed or incomplete'; do
  printf '%s %s\n' "$(unzip -p installed.apk 'classes*.dex' | strings | grep -cF "$s")" "$s"; done
```

The five symbols are, in order, #1047, #1045, #1064, #1065 and #1067. Send `ACTION_QUERY_STATE` and record `commit` (a local merge commit). Record the APK md5 from a real `adb pull` plus a local `md5sum`.

## 2. What is open, and why there is no baseline arm

Our reviews of the rebased heads (`~/projects/ohu-project/ohu-fixes-handoff/pr-1063-1067/*-rebased.md` on the review host) found no blocker in any of the five PRs. What the rig still has to show:

| PR | Open on hardware | Run |
|---|---|---|
| #1045 | Round 2 could not read the phone's title (A1) and never reached the load (A2). | **A1-S**, **K-S** |
| #1067 | Round 2's soak, exits and write failure ran on the old `quit()` and the old readers. #1067 now sits on #1045's reassembly and #1047's lifecycle lock, and a failed write now makes the send thread call `quit()`. | **K-S**, **XS**, **US** |
| #1047 | #1080's Exit fix met #1047's own version of it in a conflict; the PR side was kept. One Exit proves the stack still ends that as a user exit. Everything else on #1047 is in its own round 3. | **XS** |
| #1064 | Nearby needs the Wireless Helper, which cannot start Android Auto 17.9 on either rig phone. | none: UNTESTABLE |
| #1065 | Its one open finding is in code: a `Connecting` state cancels the 3 s USB recheck. No run can show more than the review did. | none |

**No baseline arm.** Every condition below is absolute: the head unit renders what it is fed, the rate holds across the window, the zero lists stay at 0, and the phone sees no late acks. A second APK would measure the phone, not the stack, and would double the time.

**A low load can itself be a regression.** If the head unit returns acks late, the phone lowers its video rate. So a missed load gate is graded from the phone capture too.

## 3. What is different about this round

- **One APK.** Run ids end `-S`.
- **No hand step in Stage W.** The Exit in XS is three injected taps, the recipe `settings-defaults` rounds 1 and 2 used (`EXIT_TAPS="60,665 60,665 240,110"`: dashboard, launcher grid, Exit tile), inside the operator's 5-taps-per-run rule (`rig-quirks/topics/tooling.md`). Those targets are on Android Auto's projected picture, which `uiautomator` cannot read, so check them first on a screenshot (`adb -s $HU exec-out screencap -p > XS.pre.png`) and say in Setup notes whether they moved. `native_focus=1` proves the Exit tile was hit.
- **Logging:** A1-S at VERBOSE (`log-level` 0), K-S, XS and US at INFO (`log-level` 2).
- **The wireless TLS failure line changed.** On the socket reader it is now `AapRead: invalid framing or TLS session`; on the USB reader it is still `AapRead: TLS state cannot continue`. Round 3's #1067 brief grepped only the second. Count both (section 5).
- **Two log lines are gone, so do not look for them:** `SSL Decrypt: no application data after consuming` and `AapTransport: send incomplete`. Our review asked the contributor to put them back.
- **Pre-registered outcomes:** P (post-handshake TLS records) is expected to read zero, as in every earlier round: a record, not a failure. CR reads "not exercised for reassembler drops" when no run prints `AapRead: skipped message:`. The audio half of K-S is INCONCLUSIVE when music did not play. US is INCONCLUSIVE when no USB session forms before the unplug.

## 4. Settings and helpers

**Stage W keys:** those of `pr-1045-framing-round2-brief.md` section 4, stage A (H.265 hardware, 1080p, 60 fps, Native AA). Restore the stage backup with `pr1045_reset.sh`, write the run's keys with `set_hu_prefs.sh`, and read every key back before each launch. Delete `video-profile-starvation-cap` before each run and read it back as absent. **XS adds:** `aa-exit-action` deleted (read ABSENT), `enable-floating-button` false, `native-poke-all-paired` true, `native-driver-selection-mode` 0, as `settings-defaults-round2-brief.md` section 4 lists for its E runs.

**Read and quote, never write:** `use-aac-audio`, `audio-latency-multiplier`, `audio-queue-capacity`, `view-mode`, `wifi-direct-band`. `enable-audio-sink` must read `true`; if it reads `false`, write `true` and say so.

**Helpers.** Round 2 of `pr-1045-framing` left `pr1045r2_helpers.sh`, `pr1045r2_extract.py`, `pr1045_helpers.sh` and `pr1045_extract.py` in `hur-wifi-test-scripts/`. `pr-1045-framing` round 3 never ran, so its `pr1045r3_helpers.sh` and `load3.py` are not on disk: save them now from `pr-1045-framing-round3-brief.md` section 5, unchanged, and set `X=$HOME/prstack-captures` at the top of `pr1045r3_helpers.sh` instead of `pr1045r3-captures`. For XS, find the settings-defaults helpers with `find ~/hur-wifi-test-scripts -name sd2_lib.sh` and source `sd_lib.sh` then `sd2_lib.sh` from that folder. Add this block as `stack_lib.sh` and source it last:

```bash
# stack_lib.sh : pr-stack round 1. Source after pr1045r3_helpers.sh.
# tls_zero RID : the TLS and write failure lines in the run's head unit capture. Every count must be 0.
tls_zero() { local c="$X/$1.hu.logcat"
  for s in 'AapRead: invalid framing or TLS session' 'AapRead: TLS state cannot continue' 'SSL Decrypt failed' \
           'AapTransport: encrypted write failed or incomplete' 'AapRead: Fatal read error' 'AapVideo: discarding a '; do
    printf '%s\t%s\n' "$(grep -acF -- "$s" "$c")" "$s"; done | tee "$X/$1.tlszero.txt"; }
# quits RID : the quit lines, with their clean flag
quits() { grep -aoE 'AapTransport quitting \(clean=(true|false)\)' "$X/$1.hu.logcat" | sort | uniq -c | tee "$X/$1.quits.txt"; }
```

**Every run ends with `both RID`** (both extractors, as in `pr-1045-framing` round 2) **and `tls_zero RID` and `quits RID`.** **Contamination** (as `pr-1045-framing` round 2 section 5): `MATCH! Starting AapService` above 1, a second `createGroup SUCCESS`, `Magic Garbage detected in header` above 0, or a second INFO-level `SSL handshake complete. ` voids the run (except where a run expects a second session). Re-run a void run once, then mark it INCONCLUSIVE.

## 5. The lines that decide the runs

Each was checked with `grep -F` against `app/src/main` of the stack tree `2c4b06fd`. `AapService: session state ` is composed at run time with the state and the reason (`disconnected (user_exit)`, `(link_lost)`), so the full line prints in the log but only its prefix greps in the source.

| Line (fixed substring) | Meaning |
|---|---|
| `AapRead: invalid framing or TLS session` | **TLS or framing failure on the socket reader** (wireless) |
| `AapRead: TLS state cannot continue` | TLS failure on the USB reader |
| `SSL Decrypt failed` | a record would not decrypt |
| `AapTransport: encrypted write failed or incomplete` | **a write failed** (#1067; US expects it, the others must not) |
| `AapRead: Fatal read error` | the USB reader gave up |
| `AapVideo: discarding a ` | the framing audit found a short access unit |
| `AapRead: skipped message:` | a reassembler drop (CR) |
| `AapTransport quitting (clean=` | the transport ended, with its clean flag |
| `AapTransport: ByeBye write ` | the ByeBye's write result (#1067) |
| `SSL handshake complete. ` | a session formed (INFO form) |
| `Throughput over `, `inbound rate over ` | the picture and the inbound load |
| `Video Focus NATIVE received. User clicked Exit in Android Auto.` | the Exit tile was hit |
| `ExitAction: Disconnecting projection session` | the Exit chose to disconnect |
| `AapService: session state disconnected (user_exit)` / `(link_lost)` | what the session end was called |
| `AapService: Native AA user exit. Stopping active launcher.` | the user-exit stand-down |
| `NativeAA: Connection accepted from` | the phone came back over Bluetooth |
| `USB Intent: ` with `USB_DEVICE_DETACHED` | the dongle left |
| `Found device already in accessory mode`, `stopping the wireless stack for the duration of it` | a USB attempt, and the USB session's quiesce |
| `AapService destroying` | the service stopped (must not happen in US) |
| `MATCH! Starting AapService`, `createGroup SUCCESS`, `Magic Garbage detected in header` | discard rules |
| `AutomationReceiver: `, `AutomationMarker: ` | verb proof and markers |

Phone lines (Gearhead's, not in our tree): `Waiting for ack timeout, video frame dropped`, `VIDEO_ACK_TIMEOUT`, `Received out of order ping response`, `Critical error`.

## 6. Runs

**Before Stage W:** follow `pr-1045-framing-round3-brief.md` section 6, steps 1 to 5 ("Before the stage"): D-POCO cleared of any head unit role with `headunit://exit`, unlocked and on its home screen; D-MOTO's Bluetooth off; no leftover `th_watch`; Gearhead `versionName`, D-HU's `stat` of `shared_prefs/` and the audio keys quoted; the stage backup taken; the title check. Install S, run 1a. Before every run set `RUN=<RID>` and call `th_gate`; after it, `th_report <RID>`. Between runs: `finish` ends the session; `headunit://exit` on D-HU, `sleep 3`, force-stop, restore the backup, write the next run's keys.

### R0 Gate (once)

PASS needs: the tree as stated; the 1a counts all 1 or more; the md5 recorded; `adb install -r -d` succeeding, never an uninstall; the settings backup diffed against the file after the install, with the delta stated. Time: 3 min plus the build.

### A1-S. Album art over the new copy path, VERBOSE (about 8 min)

Exactly `pr-1045-framing-round3-brief.md` A1-C, both calls, with `A1-S` in place of `A1-C`, then `tls_zero A1-S; quits A1-S`. Grade it by that brief's A1-C reachability, PASS, FAIL and INCONCLUSIVE rules, plus: every `tls_zero` count 0, and `quits` shows only the one quit that `finish` causes.

**P, from the A1-S capture:** as `pr-1067-tls-pump-round2-brief.md` section 6 P: report `produced0_M` summed over `sessions[]`, with `all.produced_any_M` above 0 as reachability, using round 2's `tp_extract.py` on `A1-S.hu.logcat`. All zero means the phone sent no TLS-only record, so `AapTlsWriter.sendControl` did not run. Say that in those words.

### K-S. Wireless load soak, 10 minutes, behind the load gate (the point of the round; about 15 min)

Exactly `pr-1045-framing-round3-brief.md` A2-C, all three calls and the `LOAD-LOW` branch, with `K-S` in place of `A2-C`. After `both K-S`, run `tls_zero K-S; quits K-S`.

Grade it by that brief's A2-C gate rule, reachability and PASS conditions 1 to 9, unchanged, plus:

10. **#1067's pump under load:** every `tls_zero` count 0; `quits` shows only the one quit that `finish` causes; `AapTransport: ByeBye write ` appears at most once, at `finish`.

**FAIL:** any of 1 to 10 missed, except where marked INCONCLUSIVE, or a session end. One retry as A2-C allows (a rig link stall). For a TLS or write failure, quote the first such line and the 20 lines before it. **If the stack changed nothing,** this run still PASSes: it is a regression check that the five PRs together cost no frame rate, no credit and no TLS failure under load. Report `rendered_fps_median`, `fed_fps_median`, the thirds, and the phone's ack-timeout count; those decide the soak.

### CR. Credits never leak

As `pr-1045-framing-round2-brief.md` section 7 CR, over A1-S and K-S. Expected: "not exercised for reassembler drops".

### XS. One Exit in Android Auto ends the stack's session as a user exit (about 5 min, 3 injected taps)

`settings-defaults-round2-brief.md` E2b steps 1 to 6, once, with `RUN=XS`, S installed, and the XS keys from section 4. That is: phone in airplane mode; launch; phone radios on; `form_session XS-start`; `sleep 10`; `tap_exit`; `grade_exit`; `send ACTION_EXIT`. Then `tls_zero XS; quits XS`.

The run is valid only if `throughput_before` 1 or more, `phone_aa_before` 1 or more and `native_focus` 1 or more; otherwise INCONCLUSIVE.

**PASS:** `disconnect` 1 or more, `state_user_exit` 1 or more, `state_link_lost` 0, `user_exit` 1 or more, `minimize` 0, `accepted_after` 0, and `quits` shows `clean=true` for the Exit's quit. Phone (graded): `phone_byebye` 1 or more. Every `tls_zero` count 0. **What a FAIL looks like:** round 1's E2 shape on `settings-defaults`, `state_link_lost` 1 and the phone back about 1 s later (`accepted_after` 1 or more). That would mean the stack lost #1080's fix in the conflict.

### US. Unplug the dongle during a USB session: the write fails once and the stack recovers (Stage U, about 10 min)

The claim (#1067): a failed write ends the transport through `quit()` once, without a hang, and the next plug forms a session. Round 2's F5 measured the old head only.

Setup: D-POCO as head unit on wireless adb with the dongle on its OTG port and D-MOTO on its cable, as `pr-1047-session-reconnect-round3-brief.md` U0 leaves it. Install **S** on D-POCO (`adb -s $POCO_IP:5555 install -r -d <S apk>`; not the export build), run 1a there, and write that brief's `U2KEYS` (Headunit Server as wireless mode, USB auto-connect on) with `pocoput`. Use that brief's `lib1047.sh` and `lib1047r3.sh` for `r_open`, `usb_live`, `cnt`, `lno`, `dt` and `cue`.

```bash
HU=$POCO_IP:5555; PH=$MOTO; PUT=pocoput; UNIT=D-POCO; ARM=S; WANT_MD5=<S md5>
r_open US-S $U2KEYS; L=$(nl); adb -s $HU shell am start -n $MAIN >/dev/null; usb_live $L || echo "US no USB session at start"
sleep 20; P=$(nl); mark US-unplug; cue "H1: unplug the dongle from D-POCO now"
waitfor 60 'USB Intent: .*USB_DEVICE_DETACHED' $P; sleep 15; Q=$(nl); mark US-unplugged
R=$(nl); cue "H1: plug the dongle back into D-POCO"; usb_live $R && ok=1 || ok=0; E=$(nl)
echo -e "US\tsession_before=$(cnt $L $P 'SSL handshake complete')\tdetach=$(cnt $P $Q 'USB_DEVICE_DETACHED')\twrite_failed=$(cnt $P $Q 'AapTransport: encrypted write failed or incomplete')\tquit=$(cnt $P $Q 'AapTransport quitting (clean=')\tfatal_read=$(cnt $P $Q 'AapRead: Fatal read error')\tdestroy=$(cnt $P $E 'AapService destroying')\treplug_session=$ok\tt_replug_ssl=$(dt $R $(lno $R 'SSL handshake complete'))\tfatal=$(cnt $L $E 'FATAL EXCEPTION')" | tee -a $OUT/US-S.tsv
r_close US-S
```

**Valid** only if `session_before` is 1 or more and `detach` is 1 or more. **PASS:** `quit` exactly 1 between the unplug and 15 s after the detach (one transport end, no second); `destroy` 0 (the service stays up); `replug_session` 1 with `t_replug_ssl` 60000 ms or less; `fatal` 0. Report `write_failed` and `fatal_read` as numbers: which of the two ended the session depends on whether a write or a read hit the dead link first, and either is correct. Phone (D-MOTO): report `Critical error`; it is not graded. **FAIL:** `quit` 0 (the session never ended), `quit` 2 or more, `destroy` 1 or more, or no session after the replug. Then follow that brief's "Closing Stage U".

### Final step

On D-HU: force-stop the app, delete `video-profile-starvation-cap` and read it back as absent, restore the stage backup and read it back, and check `aa-exit-action` reads ABSENT. Turn D-MOTO's Bluetooth back on. Check that no logcat reader, soak loop or `th_watch` loop is left running. Quote all of it in Setup notes.

## 7. Do not run

| Run | Why not |
|---|---|
| `pr-1045-framing-round3-brief.md`, `pr-1067-tls-pump-round3-brief.md` | Replaced by this brief. |
| A baseline arm of any kind | Every condition is absolute (section 2). |
| #1045's I5, P-U, U1, U2S | Round 2 PASS or held on every graded condition; #1045's own code did not change in the rebase (one test harness stub only). A1-S proves the copy path the title check needed. |
| #1067's XW (20 phone exits) | 20 exits on round 2 never hit the race window (ByeBye to quit took 502 to 505 ms). XS checks the new quit path once; more exits on this phone add time, not evidence. |
| Nearby (#1064) | UNTESTABLE: the Wireless Helper cannot start Android Auto 17.9 on either rig phone (`pr-1064-nearby-attempts-round1-results.md`). |
| #1047's Save paths | In `pr-1047-session-reconnect-round3-brief.md`. |

## 8. Report back

1. **K-S:** both gate lines; `rendered_fps_median`, `fed_fps_median`, `dropped_sum`, `videoShed_sum`; the rendered, video and audio thirds; PSS growth; the phone's ack-timeout and `VIDEO_ACK_TIMEOUT` counts; the `tls_zero` and `quits` files. **These decide the merge question for the stack.**
2. **XS:** every `grade_exit` field and the `quits` line.
3. **US:** the `US-S.tsv` row.
4. **A1-S:** `complete_runs`, both titles (and where the phone title was read); P's `produced0_M` sum.

Results go in `pr-stack-1047-1067-round1-results.md` in the template's section 7 skeleton, one `## <RunId>` section per run with its bold verdict alone on the line under the heading, plus `## Setup notes`, `## Summary` and `## Anything the brief did not ask about`. Captures go to a new release `rig-evidence-pr-stack-1047-1067` as `pr-stack-1047-1067-round1-captures.zip`, cited with its sha256.

```decisive-strings
AapRead: invalid framing or TLS session
AapRead: TLS state cannot continue
SSL Decrypt failed
AapTransport: encrypted write failed or incomplete
AapRead: Fatal read error
AapVideo: discarding a 
AapRead: skipped message:
AapTransport quitting (clean=
AapTransport: ByeBye write 
SSL handshake complete. 
Throughput over 
inbound rate over 
Video Focus NATIVE received. User clicked Exit in Android Auto.
ExitAction: Disconnecting projection session
AapService: session state 
AapService: Native AA user exit. Stopping active launcher.
NativeAA: Connection accepted from
USB Intent: 
Found device already in accessory mode
stopping the wireless stack for the duration of it
AapService destroying
MATCH! Starting AapService
createGroup SUCCESS
Magic Garbage detected in header
AutomationReceiver: 
AutomationMarker: 
```
