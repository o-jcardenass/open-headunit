# usb-reattach: round 2 results

- Candidate: `fix/usb-reattach` `37bbe71e` (`commit` reply `37bbe71effa8`), APK md5 `4647bf675bf59119b34c5ee7dde72fc6` (round 1's, reused)
- Baseline: `main` `7102b428` (`commit` reply `7102b4283666`), APK md5 `dd8653ee2c5397c691645eae323d28cb` (round 1's, reused)
- Unit: D-POCO (Android 15, unrooted) as the head unit on wireless adb, D-MOTO (Android 14, Android Auto `17.9.664004-release`) as the phone on D-POCO's OTG port. D-HU and the dongle took no part.
- Date: 2026-10-08

## Setup notes

1. **D-POCO's battery was 82% at the start, not the brief's 90%.** The operator decided that 80% is enough. Levels at the run starts: RC5 81, RCK 72, RC1 66, R2K 62, RG 59, 58 at the end. No run stopped on the 25% gate.
2. **The operator's hand steps were the one batched request only** (unplug both phones from the PC, plug D-MOTO into D-POCO's OTG port, set "USB controlled by" to "This device"). After it, `hand-steps.log` has 0 lines and `dialogs.log` 0 lines: no cue and no USB dialog appeared in the round. No `REC=CABLE`.
3. **The recovery between cycles was the phone gadget every time.** `REC=PHONE_GADGET` ran 15 times (RC5 c0 to c5, RCK c0 to c3, RC1 c0 to c3, RGc1). `REC=GEARHEAD_STOP` formed no session in any of them (the step ran first, then the gadget). On a recovery that counts as a hand recovery for the grades, as the brief says.
4. **Prepare.** Baseline and candidate md5 matched the brief. D-POCO's backup equals round 1's `settings_backup_poco.xml` (empty delta). The baseline install (`-r -d`) left `settings.xml` unchanged; so did the candidate install. D-MOTO: Gearhead enabled (0 disabled packages), battery 100, `svc usb getFunctions` printed an empty line, keyguard off after a wake. No "Always" USB grant before or after (`dumpsys-usb-grants-before/after.txt` diff empty). D-HU's app was force-stopped.
5. **`ohu_setkeys.py` and the libs.** `lib1008r3.sh`, `libreattach1.sh` and `ohu_setkeys.py` were copied from `usb-reattach-round1/`; `libreattach2.sh` is the brief's, unchanged, and passed `bash -n`. The empty-set fix was applied to the round 1 copy first, and a write of `set:connection-modes=` read back as an empty `<set />`. The round's own key set has no empty set.
6. **Close.** Gearhead enabled (`gh_disabled_final=0`), app exited, D-POCO's `settings.xml` restored from the backup (only the empty-element serialization differs), no new USB grant, no `adb logcat` left running. **D-POCO now runs the candidate (versionCode 117)**, the last build the round installed; it was on the baseline at the start. Putting both phones back on PC cables and `adb usb` are done after this file is written.
7. Unit tests were not run (the brief: round 1 R0 ran them on this SHA).

## RC5: control, plain retry on `main`, form A, pause about 5.6 s

**PASS** (the control measured something: 5 counted cycles)

Counted cycles c1 to c5 (`fauto` 2, `retries_ran` 1). `nfail` and the phone captures are in the asset. Result: **0 of 5** counted cycles formed a session after the retry (`retry_ssl=0`). Each retry ran (a handshake began within 3 s) and failed again: `fails_after_retry=2` in every cycle, so the retry and the handshake after it both failed. Every cycle then needed `REC=PHONE_GADGET` and ended `HAND_SSL`.

```
cycle  fails fauto retries retries_ran fails_after_retry retry_ssl pause_ms ph.enter_auto REC
c0     2     1     0       0           0                 0         -        0             PHONE_GADGET
c1     3     2     1       1           2                 0         5921     0             PHONE_GADGET
c2     3     2     1       1           2                 0         5980     0             PHONE_GADGET
c3     3     2     1       1           2                 0         5961     0             PHONE_GADGET
c4     3     2     1       1           2                 0         6097     0             PHONE_GADGET
c5     3     2     1       1           2                 0         6083     0             PHONE_GADGET
```

`kinds` were not set (no ladder on `main`); `vfail` 9 and `tls` 0 in c1 to c5 (the `TRANSPORT_ERROR` form), `ph.enter_auto=0` in every cycle (the phone did not re-enter accessory mode by itself). `misses.log` lists the baseline's failed handshakes that `main` did nothing about; that is expected on `main`. What this decides, from the brief: round 1's candidate recovered 5 of 5 form A cycles with its re-switch plus retry. Here the plain retry recovered 0 of 5 with the phone not moving, so **something in the candidate's re-switch matters even though no re-enumeration was seen** (the brief's "1 or fewer" branch).

## RCK: control, plain retries on `main`, form K, up to two retries

**PASS** (3 counted cycles, c1 to c3)

Result: **0 of 3** counted cycles formed a session after a retry (`retry_ssl=0`). Each had two retries that ran and a third failure (`fails_after_retry=3`); the failure form was `SSL` (`tls` 3, `vfail` 3 in each).

```
cycle  fails fauto retries retries_ran fails_after_retry retry_ssl pause_ms     ph.enter_auto REC
c0     2     1     0       0           0                 0         -            0             PHONE_GADGET
c1     4     3     2       2           3                 0         5921,5939    0             PHONE_GADGET
c2     4     3     2       2           3                 0         6064,5917    0             PHONE_GADGET
c3     4     3     2       2           3                 0         5970,5959    0             PHONE_GADGET
```

Plain retries cleared the `SSL` form in none of the 3 counted cycles. The reset is the only lever of the candidate that did (R2K).

## RC1: control, plain retry on `main`, form A, pause about 1.3 s

**PASS** (3 counted cycles, c1 to c3)

Battery was 66% before it (60% or more needed). Result: **0 of 3** counted cycles formed a session after the retry. `retries_ran` 1 in each, `fails_after_retry=2`, `retry_ssl=0`, `ph.enter_auto=0`, pause 1808, 1772 and 1656 ms. c0 needed `REC=PHONE_GADGET` as well. The pause does not matter here: RC5 (about 5.6 s) and RC1 (about 1.7 s) both gave 0.

## R2K: the candidate, form K: USB reset samples

**PASS**

All five items hold:

1. `nfail R2K` is 6 (c0 to c5 each had 2 failures, `kinds=SSL,SSL`).
2. Every cycle ends `AUTO_SSL` in `summary.tsv`: no `REC=...`, no `HAND_SSL`.
3. `miss=0` and `extra=0` in every cycle.
4. Each `USB_RESET` step line is followed by `UsbAccessoryMode: USB reset issued to ` and exactly one observation line for `USB_RESET`: step to issued 1622, 736, 1198, 1619, 929 and 935 ms (all under 3 s), step to observation 1928, 1448, 1504, 1925, 1234 and 1244 ms (all under 20 s), 1 observation line each. `rstok+rstno` equals `step2` (1 in every cycle), and `re1+nc1+re2+nc2+left` equals `step1+step2` (2 in every cycle).
5. `ph.enter_auto=1` in every cycle with `re2=1`: the phone's own log shows its gadget re-entered accessory mode.

```
cycle  step1 re1 nc1 step2 re2 nc2 left kinds  reset re-enum  dt_ms  ph.enter_auto avc unative
c0     1     0   1   1     1   0   0    SSL,SSL 306 ms       9860   1             0   2
c1     1     0   1   1     1   0   0    SSL,SSL 306 ms       9413   1             0   2
c2     1     0   1   1     1   0   0    SSL,SSL 712 ms       9490   1             0   2
c3     1     0   1   1     1   0   0    SSL,SSL 305 ms       9922   1             0   2
c4     1     0   1   1     1   0   0    SSL,SSL 306 ms       9177   1             0   2
c5     1     0   1   1     1   0   0    SSL,SSL 309 ms       9190   1             0   2
```

Result: **USB reset re-enumerations 6 of 6 steps (R2K), 8 of 8 with RG's c0 and c1** (305 to 712 ms, median 306 ms); **re-switch re-enumerations 0 of 6** (`re1`). `dt_ms` (from the first failure to the session) 9.2 to 9.9 s per failing cycle. `unative=2` per cycle with a step 2 (`I/UsbNative: libusb initialized successfully ...` and `libusb wrapped system device fd=130 ...`, 12 lines in the capture, the reset's own libusb setup); `avc` 0 (no `avc: denied` in either capture). The reset on an unrooted D-POCO needs no hand step.

## RG: the candidate: give-up, bound, banner and clearing

**PASS**

Gearhead was disabled on D-MOTO: `disable: Package com.google.android.projection.gearhead new state: disabled-user`, `gh_disabled=1`, `gh_procs=0`.

1. `gh_disabled=1`, `gh_procs=0`, `ph_gh_while_down=0`: held.
2. In `RGc1` before the `RGc1-hand` marker: one `RESWITCH` step (22:23:37.592, `no re-enumeration within 5035ms of RESWITCH; trying the handshake once more`), one `USB_RESET` step (22:23:50.820, issued 22:23:52.237, `re-enumerated the phone in 306ms`), then `recovery used both steps; a replug is needed` at 22:24:00.848: held. From the first `Handshake failed` (22:23:37.590) to the first give-up line the time was 23.3 s.
3. The bound held: `giveup=3` (the first, the `RGc1-bound` try at 22:24:04.7 with its failure at 22:24:12.36, and the hand-recovery check at 22:24:25.8 with its failure at 22:24:33.48), `steps_after_giveup=0`: held.
4. `key_after_giveup=1791516240848` (a stamp) and `banner=1` (`MainActivity: showing the connection issue banner for STALE_USB_ACCESSORY`, 22:24:16.9): held.
5. The recovery after Gearhead came back: `REC=PHONE_GADGET` (22:25:10.97 `svc usb setFunctions mtp`, SSL handshake complete 22:25:13.827), `HAND_SSL`, and `key_at_end=0`: the record cleared on its own SSL: held. (The `RGc1-hand` check, a Gearhead stop and `ACTION_CHECK_USB`, formed no session: the latch held, giving `recovery used both steps` again until the device left the bus.)
6. `gh_disabled_after=0` and `gh_disabled_final=0`: held.

Report counts: `kinds=TRANSPORT_ERROR,TRANSPORT_ERROR,none,none,none` (the three `none` are the post-give-up tries, which the ladder does not act on), `step1=1 re1=0 nc1=1 step2=1 re2=1 nc2=0 left=0 giveup=3 steps_after_giveup=0 banner=1 leftgu=0`, `RGc0` (a form K start) `step2=1 re2=1`, reset 305 ms, `AUTO_SSL`. No H2 or H3 cue.

## Round answers (the brief's section 10)

1. **The control arm:** a plain retry on `main` recovered **0 of 5** (RC5) and **0 of 3** (RC1) counted cycles with `ph.enter_auto=0`, against round 1's 5 of 5 on the candidate.
2. **The reset:** USB reset re-enumerations **8 of 8** over R2K and RG (305 to 712 ms), `avc` 0, no hand step; plain retries cleared the `SSL` form in **0 of 3** (RCK).
3. **The give-up path:** RG items 2 to 5 all held, with `key_after_giveup=1791516240848` and `key_at_end=0`.

Every `REC=` line: 15 `REC=PHONE_GADGET` (all on the baseline cycles and in RGc1, none on the 6 R2K cycles); 0 cues.

## Anything the brief did not ask about

- **Gearhead stop never recovered a cycle.** In all 15 recoveries `am force-stop` of Gearhead plus `ACTION_CHECK_USB` formed no session; only `svc usb setFunctions mtp` did. If a later round needs a cable-free recovery, start with the gadget.
- **Recovery times.** The candidate's own recovery took 9.2 to 9.9 s in R2K (`dt_ms`), through one re-switch step that does nothing visible (5 s wait) and one reset step that re-enumerates the phone in about 0.3 s.
- **What recovered the form K cycles.** In R2K every cycle recovered after the `USB_RESET` step re-enumerated the phone (6 of 6), and the re-switch step never re-enumerated it (0 of 6). A plain retry on `main` recovered none of the 5 form A (RC5), 3 form K (RCK) or 3 short-pause form A (RC1) cycles. The form A cycles of the candidate were not re-run this round (round 1 had them, 5 of 5), so how much of that 5 of 5 came from the re-switch and how much from the retry after it is still open.
- **Evidence:** the asset `usb-reattach-round2-captures.zip` is added to the existing release `rig-evidence-usb-reattach` (22042106 bytes), sha256 `34a1b01f0cdb8679acb73c61460c7e730e7f39320b0412f80c3c623467bd52da`.
