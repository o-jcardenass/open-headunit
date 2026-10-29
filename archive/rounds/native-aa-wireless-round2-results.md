# native-aa-wireless — round 2 results

**Candidate:** `fork/fix/native-aa-wireless` @ `3e7237d3`, three commits on `main` (`80a81099`)
**Baseline:** `main` @ `80a81099` (R19 only, per brief)
**APK md5:** candidate `e9c644fa733f6f17f3e3e16d145c6402` / baseline `5c3eb2de7bbc544090a05d6f9ef11352`
**Unit:** D-SAM (SM-T230, Android 4.4.2 / API 19) for R18-R20, phone D-POCO (POCO X3 NFC, Android 15,
Gearhead `17.8.163804-release.daily`). **D-HP** (HP Slate 7 Plus, Android 4.2.2 / API 17) for R21,
phone D-POCO, versionCode 110 / `3.4.0` installed over the prior `3.4.0-beta3` with no uninstall.
**Date:** 2026-09-21
**Evidence:** `rig-evidence-native-aa-wireless-round2` (release asset on `fork`), sha256
`d62beb4679e1db97cc37e3ef676cd95b2ae55475982c60406f14556165ddfb1b` — captures for R18 (log +
7 screenshots + 2 uiautomator dumps), R19 baseline/candidate logs, R20 log, R21 log, and both units'
pre-round `settings.xml` backups.

## Setup notes

- **Build gate run though the brief names none.** `assembleGithubDebug` and `testGithubDebugUnitTest`
  both clean via `build_hur.sh` / `run_unit_tests.sh`. **2261 tests, 0 failures/errors/skipped**,
  summed from `testGithubDebugUnitTest`'s per-suite XML reports — matches the brief's stated count.
  Candidate APK pulled back from D-SAM and diffed byte-for-byte (md5) against the host build before
  any run.
- **D-SAM's clock matches the host** (`00:52:54 COT` vs host `00:53:00 -05`, ~6s drift, not the
  historical ~12h offset some earlier rounds carried) — no correction needed this round.
- **D-SAM's `wifi-direct-group-name-changes` started at 15**, not 0 or 8 — left over from earlier
  testing on this unit (same pattern round 1's Setup notes flagged for R16). The counter's absolute
  value is not what R18/R20 grade; only whether it moves across a given sequence.
- **R18's own arming auto-connects the phone**, exactly as round 1's R16 setup note found: D-SAM
  already has a Native AA WiFi Direct setup targeting D-POCO and auto-connects unattended. This
  means a launch used to test the banner also races toward a real session landing (record
  registers and the stamp clears within ~10-40s of launch), so the banner window is narrow. The
  first banner screenshot attempt (5s wait) landed cleanly; a follow-up attempt to isolate the
  dismiss-tap's effect needed the wait tightened to 3s before the tap, and even then the earlier of
  two attempts mistimed the screenshot before the banner had rendered (blue splash icon only) —
  redone at 6s wait, which is the timing quoted below.
- **`forget_car_gearhead.sh` failed on D-POCO** (`ERROR: could not find node with text="Additional
  settings in the app"`) because `APPLICATION_DETAILS_SETTINGS` for Gearhead landed on the
  `Settings$ConnectedDeviceDashboardActivity` instead of the app's own info page — the intent was
  "delivered to currently running top-most instance" (a stuck Bluetooth-devices task from a prior
  round). `input keyevent KEYCODE_HOME` alone did not clear it; `am force-stop com.android.settings`
  did. Once cleared, the script's own tap sequence was reproduced by hand (Android Auto app info →
  Additional settings in the app → Vehicles → forget both listed "Google" entries — there were two,
  not one) since the script itself was not re-run after the manual unstick. Worth a fix to
  `forget_car_gearhead.sh` for the next round that needs it: force-stop `com.android.settings`
  before the first dump, not just `KEYCODE_HOME`.
- **R19's instrument (`adb -s <phone> shell cmd wifi list-networks | grep -i DIRECT`) never showed a
  signal, on baseline or candidate.** Confirmed by a third check outside the instrument: every WiFi
  Direct network config the phone created for this SSID across both baseline and candidate runs
  (`dumpsys wifi`, configs 29-33) is tagged `ephemeral`, and Android's `cmd wifi list-networks`
  excludes ephemeral configs from its listing by design — this is not a refusal of the command and
  not a repeated group name (baseline's group name changed on every one of its four bring-ups,
  confirming the verdict was never `STABLE`), it is the instrument structurally unable to see the
  class of network being measured on this phone/Android-15 build. See R19 below for the full
  reasoning and evidence.
- **R20's "exit through the app" needed a UI tap, not the `headunit://exit` deep link.** Scripted
  `am start -a android.intent.action.VIEW -d "headunit://exit"` reaches `AutomationActivity` and
  fires `AapService.ACTION_STOP_SERVICE` as documented, and `WifiDirectManager.stop()` does run —
  but on this unit, with `wifi-connection-mode=3` still set, the app immediately re-arms
  `startNativeAaQuietHost()` within the same process before the group can be observed torn down;
  confirmed twice, including a run where nothing else touched the device for a clean 10s poll of
  `dumpsys wifip2p` afterward and the same group was still up throughout. Falling back to the
  minimum UI action (tapping the home screen's Exit button, bounds read fresh via `uiautomator
  dump` each time) did tear the network down within 1s and a following bring-up created a new
  group. This differs from round 1's R13 on D-HU, where the same deep link did tear the group down
  (`groupFormed: false` within 3s) — worth flagging for whoever writes the next brief touching this
  deep link on D-SAM specifically.
- **R21 contradicts a standing note in `TESTING-TEMPLATE.md` §7a** ("`native-aa-complete-hfp-slc` is
  unreachable here... D-HP has no WiFi Direct and no hotspot, so it runs Headunit Server only").
  Step 1 below found the Bluetooth handshake listeners open normally on this unit in Native AA mode,
  confirming the code-read the brief cites (`WifiLauncherNative.start()` calls
  `handshakeManager?.start()` outside the transport branch) rather than the template's stated
  reason. The note should be corrected, not restated, in the next planning pass touching this file.
- **D-HP install: no uninstall needed.** `adb install -r` accepted the candidate directly over the
  prior `3.4.0-beta3` build (same signing key); `versionCode` read back as 110 / `3.4.0`, md5
  confirmed against the host build by pulling the installed APK. No settings were reset.
- **Scripts used:** `build_hur.sh`, `run_unit_tests.sh`, `install_and_launch.sh` (`SKIP_BUILD=1`
  after the first build of each APK), `set_prefs_runas_host.py` (every settings write this round,
  both D-SAM and D-HP are unrooted). `forget_car_gearhead.sh` was invoked but did not complete (see
  above); no script was added to `hur-wifi-test-scripts/`.
- Settings backed up before the first write: `round-native-aa-wireless-r2/settings-backup-dsam.xml`
  (D-SAM) and `.../settings-backup-dhp.xml` (D-HP), both in the evidence zip.
  End-of-round state — D-SAM: `wifi-connection-mode=3`, `native-ap-transport=0`,
  `wifi-direct-stable-identity=true`, `wifi-direct-group-name-changes=20`,
  `wifi-direct-last-identity-verdict=RENAMED`, `connection-issue-hands-free-record-refused=0`.
  D-HP: `wifi-connection-mode=1` (restored to its pre-round value).
  D-POCO: both "Google" vehicle entries forgotten in Android Auto during R19 setup and not
  re-added by hand; every subsequent bring-up re-paired and reached a full session on its own, so
  no user action is needed before the next round, but the vehicle list starts empty rather than
  populated.

## R18: the record registers, and the banner it would raise — **PASS**

D-SAM.

1. Over the whole round, with the stamp absent at the start (confirmed: `grep` for
   `connection-issue-hands-free-record-refused` in the pre-round settings backup found nothing).
   Every arming logged the clean registration and none logged a refusal:

```
00:53:48.936 NativeAaHandshakeManager.shouldRegisterDummyHfp | NativeAA: radio [Navegadortz3] gets
  the stand-in HFP record, because it advertises no Hands-Free.
00:53:58.816 AapSslContext.performHandshake | SSL handshake complete.
```

   `grep -c "refused the stand-in\|would not publish a Hands-Free record"` = 0 across the whole
   round's D-SAM capture. `connection-issue-hands-free-record-refused` stayed absent/`0` after every
   clean registration.

2. The banner, driven by hand (app stopped, `connection-issue-hands-free-record-refused` set to a
   fresh epoch-ms value via `set_prefs_runas_host.py`, launched, mode 3 already selected). At a 6s
   wait the banner was up:

```
"This unit's Bluetooth would not publish a hands-free record, which the app needs so your phone
sees a hands-free profile here. Android Auto will not start a wireless connection unless the head
unit's Bluetooth is connected with a profile, so it may never call back after being woken. The app
asks for the record again on every connection attempt. Connecting by cable, or over Wi-Fi in one of
the other wireless modes, does not need it."
```

   Screenshotted (`r18_banner.png`, `r18_c_before.png`). Tapping the banner body
   (`connection_issue_banner`, bounds `[21,69][1259,179]`, tapped at 640,124) left
   `mResumedActivity` on `MainActivity` — opened nothing. Tapping the dismiss control
   (`connection_issue_dismiss`, content-desc "Dismiss", bounds `[1197,102][1240,145]`, tapped at
   1218,123) hid the banner immediately, confirmed by an immediate before/after screenshot pair
   (`r18_c_before.png` / `r18_c_after.png`) with `mResumedActivity` still `MainActivity` throughout
   — no navigation.

3. With the stamp still set, the same arming that showed the banner went on to register the record
   cleanly and reached a full session (`SSL handshake complete` `00:55:17.132`). Polled
   `settings.xml` after: `connection-issue-hands-free-record-refused` read `0` within one 2s poll
   interval of the handshake landing. Reproduced a second time (a fresh stamp write, dismiss-tap
   isolation run) with the same result: stamp back to `0` within one poll after that session
   settled too.

## R19: what the phone keeps, on a unit that renames its network — **INCONCLUSIVE**

D-SAM and D-POCO. Both vehicle entries ("Google", there were two) forgotten in Android Auto on
D-POCO and confirmed zero `DIRECT-` entries in `cmd wifi list-networks` before starting (full list:
FAMILIA CARDENAS, Hotspotcito Chingon, Navegadortz2, Pegue Cdesta, Untrusted Network, ZEHN — none
`DIRECT-`).

1. Baseline (`main` @ `80a81099`, md5 `5c3eb2de7bbc544090a05d6f9ef11352`): four force-stop bring-ups,
   each reaching a full session (`SSL handshake complete` confirmed each cycle; the WirelessServer
   TCP accept from the phone's own IP confirmed a genuine WiFi Direct join, not a Bluetooth-carried
   fallback — e.g. `WirelessServer: Incoming connection detected from /192.168.49.106`). Every cycle
   delivered full credentials over Bluetooth (`Wrote TYPE 3 (size 57) to Bluetooth`). Group names
   walked forward each time (never repeated): `DIRECT-l4-Navegadortz3` → `DIRECT-0I-Navegadortz3` →
   `DIRECT-Eo-Navegadortz3` (nameChanges 17→18→19), confirming the verdict was never `STABLE` on
   baseline for this sequence. `cmd wifi list-networks | grep -i DIRECT` read empty after every one
   of the four cycles.

2. Candidate (`3e7237d3`, md5 `e9c644fa733f6f17f3e3e16d145c6402`): same four-cycle protocol. All
   four reached `SSL handshake complete`. `cmd wifi list-networks | grep -i DIRECT` read empty after
   every one of these four too.

**Root cause, confirmed independently of the instrument named in the brief:** `dumpsys wifi`
recorded every WiFi Direct network configuration the phone created for this SSID across both
baseline and candidate runs (`config = 29` through `config = 33`) carrying the `ephemeral` flag,
e.g.:

```
Candidate { config = 32, bssid = e6:58:e7:0e:de:1e, ..., ephemeral, trusted, secure }
```

and a phone-side connect attempt against a now-superseded group name failed with
`ASSOCIATION_REJECTION_EVENT ... statusCode: 12` — evidence the phone genuinely tracks these as
short-lived configs, not saved networks. `cmd wifi list-networks` excludes ephemeral configs from
its listing on this Android 15 build; this is neither of the brief's two anticipated INCONCLUSIVE
reasons ("the phone refuses the command" or "the group name happens to repeat") but is the same
substance — the instrument cannot produce the signal R19 needs, on baseline or candidate, regardless
of what field 5 (`AccessPointType`) actually carries. No AccessPointType difference between STATIC
and DYNAMIC could have been observed this way even in principle on this phone. This is a finding
about the instrument, not evidence for or against field 5's behavior either way.

## R20: a surviving group is read rather than recreated, below Q — **PASS**

D-SAM, candidate.

1. Bring Native AA up, note the group name, then force-stop and relaunch three times, no exit
   between. Starting `wifi-direct-group-name-changes=19`.

```
01:07:32.159 WifiDirectManager: a group named DIRECT-Eo-Navegadortz3 is already up from before this
  bring-up; reading it instead of tearing it down.
01:07:32.300 WifiDirectManager: group identity ssid=DIRECT-Eo-Navegadortz3 ... bssid=E6:58:E7:0E:DE:1E
  ... stable=yes nameChanges=19/3 (this group was already up and was read rather than created, so
  nothing new was measured) source=IPv6 link-local
01:07:47.534 WifiDirectManager: a group named DIRECT-Eo-Navegadortz3 is already up from before this
  bring-up; reading it instead of tearing it down.
01:07:47.194 WifiDirectManager: group identity ssid=DIRECT-Eo-Navegadortz3 ... nameChanges=19/3 (same
  name and same BSSID as the last group) source=IPv6 link-local
01:08:43.569 WifiDirectManager: a group named DIRECT-Eo-Navegadortz3 is already up from before this
  bring-up; reading it instead of tearing it down.
01:08:43.289 WifiDirectManager: group identity ssid=DIRECT-Eo-Navegadortz3 ... nameChanges=19/3 (same
  name and same BSSID as the last group) source=IPv6 link-local
```

   Same SSID (`DIRECT-Eo-Navegadortz3`) and same BSSID (`E6:58:E7:0E:DE:1E`) across all three.
   `wifi-direct-group-name-changes` read `19` before and `19` after (unchanged).

2. Exit through the app, then bring it up once more. **Deviation (see Setup notes): the scripted
   `headunit://exit` deep link stopped the service but the app immediately re-armed and re-read the
   same surviving group before any teardown could be observed** — confirmed with a clean 10s poll of
   `dumpsys wifip2p` showing `DIRECT-Eo-Navegadortz3` present throughout with no relaunch or other
   interaction in between. Fell back to the minimum UI action: force-stopped, relaunched to the home
   screen, tapped the Exit button (bounds `[1142,715][1259,779]`, read fresh via `uiautomator dump`).
   `dumpsys wifip2p`'s `mGroup network:` line went from present to absent within 1s of the tap and
   stayed absent for a clean 10s poll. A following bring-up created a genuinely new group:

```
01:12:12.233 WifiDirectManager: group identity ssid=DIRECT-9v-Navegadortz3 ... stable=no (the
  platform names it) nameChanges=20/3 (this platform names the group itself and has picked a
  different name on 20 creates, ...) source=IPv6 link-local
```

   `wifi-direct-group-name-changes` read `20` afterward — moved by exactly one, matching a single
   genuine create. New SSID, new name-changes count: removing the network on a user exit is
   confirmed still deliberate and intact on the candidate.

## R21: does a second Android 4.2.2 tablet publish the stand-in record at all — **PASS, and the more likely answer**

D-HP, phone D-POCO. App stopped, `wifi-connection-mode` set to `3` via `set_prefs_runas_host.py`,
launched.

1. The run is live — **this refutes the standing note in `TESTING-TEMPLATE.md` §7a**, confirming the
   brief's own code-read:

```
01:13:59.559 NativeAaHandshakeManager.start | NativeAA: Starting Bluetooth Handshake Servers
  (primary radio [HP Slate 7 Plus])...
01:13:59.579 1.invokeSuspend | NativeAA: ACTIVELY LISTENING on Android Auto UUID
  (4de17a00-52cb-11e6-bdf4-0800200c9a66) on radio [HP Slate 7 Plus]... Waiting for phone to connect
  back!
```

2. The stand-in HFP record, read immediately after (same arming):

```
01:13:59.569 NativeAaHandshakeManager.shouldRegisterDummyHfp | NativeAA: radio [HP Slate 7 Plus]
  gets the stand-in HFP record, because it advertises no Hands-Free.
```

   **PASS, and the more likely answer**: no refusal line anywhere in the capture
   (`grep -c "refused the stand-in\|would not publish a Hands-Free record"` = 0), and
   `connection-issue-hands-free-record-refused` absent from `settings.xml` at the end of the run. A
   second Android 4.2.2 tablet, on different hardware from D-SAM, publishes the stand-in record on
   the first attempt. No `FATAL EXCEPTION` or crash anywhere in the capture
   (`grep -c "FATAL EXCEPTION"` = 0); the process stayed alive through the group-create refusal that
   naturally follows on a unit with no WiFi Direct (`WifiDirectManager: Native AA removeGroup before
   recreate failed (reason=P2P_UNSUPPORTED)`, `the unit refused the group`) and on into a harmless
   Bluetooth poke of the paired Motorola phone, neither of which this run grades.

   Together with D-SAM's clean registration in R18, this points the one reporter's refusal at that
   unit's own Bluetooth stack rather than something common to Android 4.2.2 head units generally —
   the retry-and-banner mechanism this round covers is aimed at a rare quirk, not a routine one.

3. `wifi-connection-mode` restored to `1` (its pre-round value), app stopped.

## Anything the brief did not ask about

Nothing else observed beyond the sections above and what is already logged in Setup notes
(`forget_car_gearhead.sh`'s stuck-task failure mode, and the D-SAM-specific `headunit://exit`
re-arm behavior).
