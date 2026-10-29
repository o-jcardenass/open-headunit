# automation-screen-disconnect, round 4 brief: the screen verbs, the USB stand-down, and connect by name

Publish name on `transfer/rig-rounds`: `automation-screen-disconnect-round4-brief.md`. Results go in
`automation-screen-disconnect-round4-results.md`. Evidence goes in the existing release
`rig-evidence-automation-screen-disconnect` as the asset
`automation-screen-disconnect-round4-captures.zip`.

> **This brief replaces two queued briefs. Do not run either of them.**
>
> - `automation-screen-disconnect-round3-brief.md` (C `b5bd8145`). Its runs are Stages A and U here.
> - `wifi-direct-named-connect-round1-brief.md` (C `6a0089d6`). Its runs are Stage N here, renamed C1 to C5.
>
> The two branches are now one branch. A results file for either old brief is not expected. Delete both
> Queue lines when this brief is published, and point the `wifi-direct-named-connect` thread row at this file.

The `wifi-direct-named-connect` commits were cherry-picked onto `feat/automation-screen-disconnect`.
So one build carries both features. Every run of both old briefs is here, with its pass criteria
unchanged. Only the run ids of the connect runs, the marker names that follow from them, and the
shared build and install steps changed.

One candidate, one build (about 25 min). Three units: D-POCO, D-MOTO and D-HU. D-SAM and D-HP are
free, so a round on them can pair with this one (§3b). About 2 h 35 min in all (section 7 has the
sum). The operator must be present for one block of about 35 minutes: HS2, the H1 bond check, all of
Stage U, and the cable return in X. Before that block, the operator is needed only if HS0 or HS3
fires. After it, Stage N runs unattended.

| Run | What it proves | Head unit | Phone |
|---|---|---|---|
| R0 | build, APK identity on D-POCO | | |
| PA | precheck for Stages A and U | D-POCO | D-MOTO |
| V3 | **point 1:** screen-on with the grant and no session raises the home screen | D-POCO | |
| V4n | screen-on during a session raises the projection (no keyguard) | D-POCO | D-MOTO |
| R5n, R7n | screen-off after the end, then power back: a new session renders (no keyguard) | D-POCO | D-MOTO |
| **V4k** | **point 3:** screen-on during a session dismisses the swipe keyguard and the projection renders | D-POCO | D-MOTO |
| **R5k, R7k** | **point 3:** the same return as R7n, with the swipe keyguard up | D-POCO | D-MOTO |
| HS2 | operator block starts: remove the device admin grant | D-POCO | |
| NB | the bond check for Stage N (H1 if needed) | D-HU | D-POCO |
| **R11** | **point 2:** a USB session ended by power loss keeps wireless down until power back, then re-arms it | D-POCO (USB host) | D-MOTO (on OTG) |
| X | clean-up, cables back; operator block ends | all | |
| PN | precheck for Stage N, install on D-HU | D-HU | D-POCO |
| C5u | control: bare `ACTION_CONNECT` is still the USB check | D-HU | |
| **C1** | **point 4:** `ACTION_CONNECT --es device_name` on an armed, quiet stack wakes that phone and a session forms | D-HU | D-POCO |
| C3 | inside C1's session: "already connected", the poke verb takes the name, another phone's name is refused | D-HU | D-POCO |
| C5p | regression and baseline: the old `ACTION_NATIVE_AA_POKE --es extra_mac` from a stopped app | D-HU | D-POCO |
| C2 | `ACTION_CONNECT --es mac <lower case> --ez no_ui true` from a stopped app: the reporter's macro, cold | D-HU | D-POCO |
| C4, C4c | refusals: unknown name, unpaired MAC, mode not Native AA; nothing is woken | D-HU | D-POCO |
| C1d | the deep link `headunit://connect?device_name=` with a lower-case name | D-HU | D-POCO |
| XN | restore D-HU and D-MOTO | D-HU | |

Old and new names of the connect runs (scenario numbers from the plan):

| New | Old (`wifi-direct-named-connect` round 1) | Plan scenario |
|---|---|---|
| C1 | R1 | 1, by name from idle |
| C1d | R6 | 1, by name, through the deep link |
| C2 | R3 | 2, by MAC with `no_ui` |
| C3 | R2 (inside C1's session, as in the old brief) | 3, already connected |
| C4, C4c | R5 (R5a and R5b), R5c | 4, refusals |
| C5u, C5p | R7, R4 | 5, regression |

## 1. Build and baseline

| Arm | Ref | SHA | `commit` in `ACTION_QUERY_STATE` |
|---|---|---|---|
| **C** (candidate) | `fork/feat/automation-screen-disconnect` | `3101712afcd587afc6ae628855cba316d5314dda` | `3101712afcd5` (a `-dirty` suffix is expected) |

History was rewritten since round 3 was queued. C is `main` at `f9a72d48` plus four commits:

- `31dfdd3f` Auto-disconnect: end the session when external power is lost
- `cfd03140` Automation: ACTION_SCREEN_OFF and ACTION_SCREEN_ON
- `09c219a6` Native AA: wake a phone that a command names by Bluetooth name or address
- `3101712a` Automation: ACTION_CONNECT and the poke take a phone by name or MAC

The first two are round 3's `5283f9b5` and `b5bd8145`. The last two are `wifi-direct-named-connect`'s
`2151721d` and `6a0089d6`. `git range-diff` shows no changed line in any of the four patches, only
moved context. No baseline arm: round 2's captures are the "before" for V3, V4 and R7, and C5u and
C5p are the controls for the connect runs.

**gate.sh**
```bash
git fetch fork feat/automation-screen-disconnect
git rev-parse fork/feat/automation-screen-disconnect          # must print 3101712afcd587afc6ae628855cba316d5314dda
git rev-parse fork/feat/automation-screen-disconnect^{tree}   # must print 773cda5d5682e774bc595ca5bce903b654950039
```

If either line differs, stop the round.

### R0. Build and APK identity

Make `rig-data/rounds/automation-screen-disconnect-round4/` and set `OUT` to it. In one worktree,
`git checkout --detach 3101712afcd587afc6ae628855cba316d5314dda` and check the tree hash. Run
`$RIG_TOOLKIT/rig_thermal.sh wait 70`, then build with
`HUR_DIR=<wt> MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh`. Copy the APK out of `apks/` at once to
`$OUT/apk/C.apk`. Do not run the unit tests: 4096 JVM tests passed on the dev host at this SHA, with
0 failures.

**r0_identity.sh**
```bash
APK=$OUT/apk/C.apk
md5sum $APK                                                   # record it in Setup notes
for s in WiredRearm 'the stand-down decides the re-arm of wireless mode' 'screen-on asks the system to dismiss the keyguard' ScreenOnRaisePolicy 'external-power disconnect' ConnectByNameGate; do
  printf '%s %s\n' "$s" "$(unzip -p $APK 'classes*.dex' | strings | grep -cF "$s")"; done   # each 1 or more
adb -s 4f4027e9 shell am force-stop com.andrerinas.headunitrevived; sleep 0.3
adb -s 4f4027e9 shell run-as com.andrerinas.headunitrevived cat shared_prefs/settings.xml > $OUT/pre-install-D-POCO.xml
adb -s 4f4027e9 install -r -d $APK
adb -s 4f4027e9 shell run-as com.andrerinas.headunitrevived cat shared_prefs/settings.xml > $OUT/post-install-D-POCO.xml
diff $OUT/pre-install-D-POCO.xml $OUT/post-install-D-POCO.xml   # record the delta; reseed from pre-install if it differs
```

`WiredRearm`, `the stand-down decides the re-arm of wireless mode`, `screen-on asks the system to
dismiss the keyguard` and `ConnectByNameGate` are not in round 2's APK. A count of 0 means the wrong
APK. If the build fails or a symbol counts 0, stop the round. If the install fails with
`INSTALL_FAILED_UPDATE_INCOMPATIBLE`, stop and tell the operator. Do not uninstall. D-HU gets the same
APK in PN, step 8. D-SAM is not used and keeps its APK.

**PASS when:** in PA, `U=$POCO state` shows `commit` starting `3101712afcd5`, and in PN step 8
`P.state` shows the same. **A failure in R0 stops the round.**

## 2. What this is and why

### Stages A and U: external power and the screen verbs

A tablet that serves as a head unit has its own battery. When the car is switched off, the charger
loses power, and the screen and the Android Auto session stay up. The candidate ends the session
after a delay (`external-power-disconnect`), can turn the screen off after the end
(`external-power-screen-off`, with a device admin grant), and undoes both when power comes back. It
also adds the verbs `ACTION_SCREEN_OFF` and `ACTION_SCREEN_ON`. Round 2's brief, section 2, has the
full description.

Round 2 passed R1 and 12 other runs. V3, V4 and R7 failed. Three faults are fixed in C:

- **The screen-on raise never fired (V3, V4, R7 in round 2).** From API 33, `turnScreenOn` raises a
  screen with the turn-screen-on flag: the projection during a session, the home screen without
  one. `ScreenOnRaisePolicy.decide` raises nothing when the panel is already lit. In round 2,
  `turnScreenOn` took the wake lock first and read the panel after it, so the panel always read lit,
  and every line read `... by wake lock` with no screen. C reads the panel first, then takes the
  wake lock. Expected now: `AapService: turning the screen on (automation) by wake lock and the home
  screen by <strategy>` in V3, and `... and the projection by <strategy>` in V4n.
- **The wired re-arm defeated the stand-down (no run reached it).** A USB session stops the wireless
  stack while it runs, and its end re-arms the stack 1.5 s later. When power loss ended a USB
  session, that re-arm ran after the stand-down had stopped the stack. So the stack came back up
  with the car off. C lets the stand-down decide: it logs `AapService: wired session ended by an
  auto-disconnect; the stand-down decides the re-arm of wireless mode <mode>`, holds the re-arm, and
  runs it after `power is back; checking USB again.` on power back.
- **The screen-on raise stayed behind the keyguard (V4 and R7 in round 2).** `lockNow()` puts the
  keyguard up, and an activity raised under it gets no surface. C reads the keyguard with the panel
  (`KeyguardManager.isKeyguardLocked`). When a keyguard is up, from API 26, it raises a screen even
  on a lit panel, and that screen calls `KeyguardManager.requestDismissKeyguard`. A keyguard with no
  PIN, pattern or password goes away. A secure one shows its own unlock screen, so the verb still
  cannot unlock a unit. The screen-on line now ends `(keyguard=true)`, `(keyguard=false)` or
  `(keyguard=null)`, and the raised screen logs `MainActivity: screen-on asks the system to dismiss
  the keyguard` (or `AapProjectionActivity: ...`), then `...: the keyguard was dismissed`.

### Stage N: connect to a phone by name or MAC

A user runs Native AA on WiFi Direct. The user wants one automation command (Tasker, a shortcut)
that says "connect to my phone". On `main`, `ACTION_CONNECT` has two meanings. With `ip` it opens a
socket to Android Auto's head unit server. Without `ip` it runs the USB check. So a script that adds
`device_name=` gets a silent USB check. `ACTION_NATIVE_AA_POKE --es extra_mac` can wake a phone, but
it takes no name and it is not an explicit pick.

The candidate changes this:

- `ACTION_CONNECT` and `ACTION_NATIVE_AA_POKE` take `mac` (or the older `extra_mac`) and
  `device_name`. The order is `ip` (connect only), then `mac`, then `extra_mac`, then `device_name`.
  With none, `ACTION_CONNECT` is the USB check as before. A MAC on the poke verb keeps its old effect.
- The named forms go to `AapService` as a poke marked "by command". The service resolves the name
  against the bonded list (`BluetoothHelper.driverCandidates`), exact and case-insensitive after a
  trim, on the remote name and, from API 30, the alias. A MAC must be bonded and is logged in the
  bonded spelling.
- `ConnectByNameGate` decides, in this order: not Native AA, no Bluetooth permission, no match,
  more than one match, not a phone, MAC not paired, the named phone already projecting, another
  session live. A pass calls the existing `selectDriver(mac, explicit = true)`.
- Every refusal prints one WARN and returns before the old poke branch. So a refusal never falls
  into the USB check or the module WiFi-button branch.
- `native-poke-bt-macs` is not read and not written by the command.

The JVM tests cover the policy, the resolver and the gate. They cannot cover the bonded-list read on
a real adapter, the alias read, the session reads in the service, the early return, the deep-link
query parsing in `AutomationActivity`, or a real wake. Stage N covers those.

## 3. What is different about this round

### Order and units

- **Stage A, then the operator block, then Stage N.** Stage A needs D-HU's Bluetooth off; Stage N
  needs it on. Stage N needs D-POCO and D-MOTO on PC cables, which X restores. So Stage N runs last,
  unattended, after the operator leaves.
- **One install per unit.** R0 installs C on D-POCO for Stages A and U. PN installs the same APK on
  D-HU. Nothing is installed twice.
- **D-POCO changes role between Stage U and Stage N** (head unit, then phone). X sends
  `headunit://exit` to D-POCO's app before the force-stop, and PN reboots D-POCO. That clears any P2P
  group its app left (`rig-quirks/topics/lifecycle.md`, `units/D-POCO.md`).
- **The two stages use two helper libraries, and they must never share a shell.** `ascd2_lib.sh`
  and `wdnc_lib.sh` define `send`, `mark`, `put`, `hwin`, `endrun` and others differently. Stage A and
  U scripts source `ptr_lib.sh`, `ascd2_lib.sh`, `ukeys.sh` and `ascd3_lib.sh`. Stage N scripts
  source `ptr_lib.sh` and `wdnc_lib.sh` only. Each script is its own shell under `runlocked.sh`.

### Stages A and U

- **Only D-POCO runs the app.** Its fake lever works (round 2 L-POCO: `LEVER_POCO=fake`), so every
  run uses the fake lever. No cable pull.
- **D-POCO's ROM lights the panel on every plug change** (`rig-quirks/units/D-POCO.md`). So in R7n
  the panel reads lit before `turnScreenOn` runs, and with no keyguard the line reads `... by wake
  lock` with no screen. That is correct by the policy. R7n conditions 4 and 5 stay INCONCLUSIVE, as
  in round 2. In R7k the keyguard is up, so C raises the home screen on the lit panel.
- **Each session case runs twice: with the keyguard off, then with it up.** V4n and R5n/R7n run with
  D-POCO's keyguard off (`locksettings set-disable true`). They grade the raise and the return on
  their own. V4k and R5k/R7k run after "Keyguard back", with the swipe keyguard that `lockNow()`
  puts up. They grade the dismiss. PA proves D-POCO has no lock credential, so the keyguard is never
  a secure one in this round. X restores the keyguard setting that PA reads.
- **V3 runs with the keyguard as found** (round 2 measured a swipe keyguard after `lockNow()`). So
  V3 also grades the dismiss from the home screen.
- **D-POCO dozes between runs** and `am start` does not wake it. `ready` (section 5) lights the
  panel and dismisses the keyguard before each run and after each bring-up.
- **Stage U moves D-POCO and D-MOTO to wireless adb.** D-POCO hosts USB through its OTG adapter, so
  its one port is not free for the PC, and D-MOTO plugs into that adapter. Read the addresses again;
  do not assume them.
- **R11 runs in `wifi-connection-mode` 1 (Headunit Server), not 3.** The fix needs a non-manual
  wireless mode. Mode 3 can stand D-POCO's station down, which drops wireless adb. Mode 1 leaves the
  station alone.
- **Hold D-HU's Bluetooth off before every Stage A session** (`bringup` does it). D-MOTO is bonded
  to D-HU's radio, and Gearhead can dial it instead of D-POCO. NB turns it back on.
- **Expected INCONCLUSIVE:** R7n conditions 4 and 5, R7k condition 4; R11 when the USB session never
  stops the wireless stack (condition 1c); R11 condition 10 under the re-attach fault (rule in R11).

### Stage N

- **D-POCO's radios stay up between runs.** This is a deliberate deviation from §4 step 1. The
  control in C1, C4 and C1d is a 30 s quiet window on an armed stack before the command
  (`arm_quiet`, section 5). It shows that nothing else connects the phone in that state.
- **If the quiet window breaks, the script handles it.** The phone can reconnect by itself
  (`rig-quirks/topics/bt.md`, "The phone's own reconnect beats our poke"). If a session forms in the
  quiet window, `arm_quiet` sends `ACTION_DISCONNECT` (a user exit holds the stack down), waits 10 s
  and runs a second quiet window of 20 s. The command then goes out. The run records which window
  was clean.
- **No poke target is set, on purpose.** `native-poke-bt-macs` is empty and `native-poke-all-paired`
  is false (`tooling.md`), so the retry loop pokes nobody. A poke in a run can only come from the
  command under test.
- **The WiFi Direct group identity is not kept.** `wifi-direct-stable-identity` is false and the four
  group keys are deleted in every run, and PN deletes D-HU's saved P2P groups once. A persistent
  group lets a paired phone rejoin with no poke (`units/D-HU.md`). Each session is then a first join
  of a new group, which takes longer. Allow 120 s.
- **D-MOTO stays off for all of Stage N** (`svc wifi disable` and `svc bluetooth disable`, read
  back). It is bonded to D-HU and can take the poke rotation (`bt.md`). C3 uses only its bonded name
  on D-HU, to name "another phone". XN leaves it off; nothing after Stage N needs it.
- **Not a phone (`NOT_A_PHONE`) and an ambiguous name are not run.** The rig has no bonded non-phone
  whose class is known, and no two bonded devices share a name. `NamedPhonePolicyTest` covers both.
  C1 records the `driver candidates:` roll-up line, which shows that the verdicts reach the resolver.
- **A named connect during another phone's handshake or join is not run.** It needs two phones that
  answer at once. `ConnectByNameGateTest` covers it: that phone counts as live, so the command is refused.
- **The no-permission refusal is not run.** D-HU holds `BLUETOOTH_CONNECT`. `ConnectByNameGateTest`
  covers it.
- **A session that does not form is graded against C5p.** C5p uses the old poke path, which is
  unchanged, on the same rig state. If C5p forms a session and a named run does not, the named run
  FAILs. If C5p also does not form one, the named run's session condition is INCONCLUSIVE (rig or
  phone state, shared by both paths). This is "the C5p rule" below.
- **The projection raise needs the overlay permission.** These runs never launch `MainActivity`.
  So the service raises the projection itself, by the overlay route. PN confirms
  `SYSTEM_ALERT_WINDOW` is `allow` on D-HU (`topics/projection.md`).
- **`createGroup SUCCESS` is measured, not a discard rule, in Stage N.** A new group identity per
  run and a cold start can each give more than one create. C2 grades its count against C5p's.
- **Expected INCONCLUSIVE:** none, unless C5p forms no session.

### Both stages

- **Do not write the rig's audio settings.** `use-aac-audio`, `audio-latency-multiplier` and
  `audio-queue-capacity` are a deliberate worst case. `put` starts every write from the unit's own
  backup, so they keep their values. Record D-HU's once in PN.
- **`Critical error` lines on the phone follow every session end we cause.** Do not grade them.

### Hand steps

| Id | Run | Action | Why there is no verb |
|---|---|---|---|
| HS0 | PA | Only if D-MOTO shows a PIN screen: the operator unlocks D-MOTO once | adb cannot enter a PIN |
| HS3 | grant fallback | Only if `dpm set-active-admin` fails: the operator accepts the add-admin screen | a system dialog |
| HS2 | operator block | On D-POCO, the operator deactivates this app on the system's device admin screen | `dpm` refuses to remove an admin that is not test-only; the screen is a system dialog |
| H1 | NB, only if cued | The operator forgets `Navegadortz3` on D-POCO, and pairs D-POCO with D-HU (`Navegadortz2`) if that bond is missing | unpairing and pairing are system dialogs (`units/D-POCO.md`, `bt.md`) |
| CABLES | Stage U | The operator unplugs D-POCO and D-MOTO from the PC and puts the OTG adapter in D-POCO | hardware |
| OTG | R11 | The operator plugs D-MOTO's cable into the OTG adapter | hardware |
| USBOK | R11 | The operator presses OK on a USB dialog on D-POCO, and does not tick "Always" | `UsbConfirmActivity` and `UsbPermissionActivity` are system dialogs |
| H3 | R11, only if cued | On D-MOTO, the operator chooses "USB controlled by: This device" | Android's USB preferences screen has no adb lever |
| RECOVER | R11, at most once | The operator unplugs D-MOTO from the OTG adapter, waits 5 s, plugs it back in | the first USB bring-up after a cable event can fail (`Found device already in accessory mode`) |
| OTG-OUT, CABLES-BACK | X | The operator unplugs D-MOTO from the OTG adapter, removes the adapter, and plugs both phones back into the PC | hardware |

`cue` prints and beeps each step at the moment of the action and writes it to
`$OUT/hand-steps.log`. No script injects input on any unit. At the start of the operator block, tell
the operator in one message: "Cues arrive with a beep. Answer a USB dialog on D-POCO only at a USBOK
cue. Do not tick Always. Do not touch either phone without a cue."

## 4. Settings keys

Write with the app stopped, with `put`. `put` starts every write from the unit's own backup. Read
the keys back with `rd` before the launch or the first command. A key that does not read back voids
the run before it starts: write it again.

### Stages A and U (D-POCO)

`MAC` is D-MOTO's Bluetooth address. Set it before `mkbase`.

| Name | Keys |
|---|---|
| `BASE` | `mkbase` below: mode 3, poke list `$MAC` only, every auto-start off, `log-level` 2, starvation cap and wake verdict deleted |
| `A0`, `A1` | `bool:allow-external-configuration=false`, `=true` |
| `EP_OFF` | `bool:external-power-disconnect=false int:external-power-disconnect-delay-seconds=20 bool:external-power-screen-off=false` |
| `EP_ON` | `bool:external-power-disconnect=true int:external-power-disconnect-delay-seconds=20 bool:external-power-screen-off=false` |
| `EP_MIN_SCR` | `bool:external-power-disconnect=true int:external-power-disconnect-delay-seconds=2 bool:external-power-screen-off=true` |
| `UKEYS` | see below |

**ukeys.sh**
```bash
mkbase() { BASE="int:wifi-connection-mode=3 int:native-ap-transport=0 int:native-driver-selection-mode=0 bool:native-poke-all-paired=false set:native-poke-bt-macs=$MAC str:last-connected-native-mac=$MAC str:native-preferred-device-mac=$MAC set:auto-start-bt-macs= bool:auto-start-self-mode=false bool:auto-connect-last-session=false bool:auto-start-on-boot=false bool:auto-start-on-screen-on=false bool:auto-start-on-usb=false bool:kill-on-disconnect=false int:onboarding-version=2 int:log-level=2 del:native-aa-wake-damage-verdict del:video-profile-starvation-cap"; }
A0="bool:allow-external-configuration=false"
A1="bool:allow-external-configuration=true"
EP_OFF="bool:external-power-disconnect=false int:external-power-disconnect-delay-seconds=20 bool:external-power-screen-off=false"
EP_ON="bool:external-power-disconnect=true int:external-power-disconnect-delay-seconds=20 bool:external-power-screen-off=false"
EP_MIN_SCR="bool:external-power-disconnect=true int:external-power-disconnect-delay-seconds=2 bool:external-power-screen-off=true"
UKEYS="int:wifi-connection-mode=1 set:connection-modes=usb,wifi bool:auto-start-on-usb=false bool:auto-connect-single-usb=false bool:use-libusb=false set:auto-start-bt-macs= bool:auto-start-self-mode=false bool:auto-connect-last-session=false bool:auto-start-on-boot=false bool:auto-start-on-screen-on=false bool:kill-on-disconnect=false int:onboarding-version=2 int:log-level=2 del:video-profile-starvation-cap $A0 $EP_ON"
```

| Run | Keys |
|---|---|
| V3, V4n, V4k | `$BASE $A1 $EP_OFF` (`ACTION_SCREEN_OFF` is in `AutomationCommandPolicy.CONFIGURING` on C, so it needs `$A1`) |
| R5n/R7n, R5k/R7k | `$BASE $A0 $EP_MIN_SCR` (the 2 is below the floor on purpose; the log must show 5000ms) |
| R11 | `$UKEYS` |

Why the less obvious keys: every auto-start key is off, so a re-arm can only be the feature's own.
`auto-start-on-usb` false stops a plug from starting a session before the capture runs.
`connection-modes` holds `usb`, so the USB path is on. `log-level` 2 (INFO) carries every app line
in section 6. `ACTION_LOG_MARKER` is not in `AutomationCommandPolicy.CONFIGURING` on C, so markers
work with `$A0`.

### Stage N (D-HU)

**nbase.sh**
```bash
BASE="int:wifi-connection-mode=3 int:native-ap-transport=0 int:native-driver-selection-mode=0 bool:native-poke-all-paired=false set:native-poke-bt-macs= set:auto-start-bt-macs= del:last-connected-native-mac del:native-preferred-device-mac bool:auto-connect-last-session=false bool:auto-start-on-boot=false bool:auto-start-on-screen-on=false bool:auto-start-on-usb=false bool:auto-start-self-mode=false bool:kill-on-disconnect=false bool:wifi-direct-stable-identity=false del:wifi-direct-group-name del:wifi-direct-last-group-ssid del:wifi-direct-last-group-bssid del:wifi-direct-group-passphrase bool:allow-external-configuration=false int:onboarding-version=2 int:log-level=2 del:native-aa-wake-damage-verdict del:video-profile-starvation-cap"
```

| Run | Keys |
|---|---|
| C5u, C1/C3, C5p, C2, C4, C1d | `$BASE` |
| C4c | `$BASE int:wifi-connection-mode=0` (MANUAL, last word wins) |

Why the less obvious keys:

- `native-driver-selection-mode=0` turns the driver selector off, so no countdown connects a phone.
- The two MAC keys are deleted so that C3's "already connected" reads the MAC that C1's handshake
  writes, not a seeded one.
- `auto-start-bt-macs` empty and the `auto-start-*` keys false keep every other starter out.
- `video-profile-starvation-cap` and `native-aa-wake-damage-verdict` are cleared because either can
  be left latched by an earlier thread (`bt.md`).
- `log-level=2` (INFO) carries every app line in section 6. Markers work with
  `allow-external-configuration` false.

## 5. Helpers and verbs

Copy these from `rig-data/rounds/automation-screen-disconnect-round2/` into `$OUT`: `ptr_lib.sh`,
`ascd2_lib.sh`, `sx.py` and `scripts/runlocked.sh`. Save `ukeys.sh`, `ascd3_lib.sh`, `nbase.sh` and
`wdnc_lib.sh` from this brief. List every file in Setup notes. Run each script through
`runlocked.sh`: round 2 found that `th_gate` leaves a watcher that holds the rig lock, and that script
kills it. Use only `th_wait`, `th_gate` and `th_report` from `ptr_lib.sh`.

- **Stages A and U:** source `ptr_lib.sh`, then `ascd2_lib.sh`, then `ukeys.sh` and `ascd3_lib.sh`,
  so the new functions win.
- **Stage N:** source `ptr_lib.sh`, then `nbase.sh` and `wdnc_lib.sh`, so the second one wins.

**ascd3_lib.sh**
```bash
# ascd3_lib.sh : source after ptr_lib.sh, ascd2_lib.sh and ukeys.sh. Needs OUT, U, UN, PH, RUN.
# phone_off : svc only, no airplane mode (TESTING-TEMPLATE.md section 4 step 1)
phone_off() { adb -s $1 shell svc wifi disable; sleep 0.3; adb -s $1 shell svc bluetooth disable; sleep 5
  adb -s $1 shell dumpsys wifi | grep -a -m1 'Wi-Fi is'; sleep 0.3
  adb -s $1 shell dumpsys bluetooth_manager | grep -a -m1 -E 'enabled:|state:'; sleep 0.3; }
# ready TAG : light the panel and dismiss a keyguard; write the readings to $OUT/$RUN.w-TAG and kg-TAG
ready() { adb -s $U shell input keyevent 224; sleep 0.5; adb -s $U shell wm dismiss-keyguard; sleep 1
  wake | tee "$OUT/$RUN.w-$1"; kg | tee "$OUT/$RUN.kg-$1"; }
# kgset true|false : D-POCO's "no lock screen" setting, then read it back
kgset() { adb -s $U shell locksettings set-disable $1; sleep 0.5; adb -s $U shell locksettings get-disabled; sleep 0.3; }
# usbwait S : wait up to S s for a new SSL line; cue USBOK while a USB system dialog has focus. 0 = SSL seen.
usbwait() { local s=$1 b i f; b=$(wc -l < "$(SF)")
  for i in $(seq 1 $((s/3))); do
    tail -n +"$((b+1))" "$(SF)" | grep -aqF 'SSL handshake complete' && return 0
    f=$(focus)
    case "$f" in *UsbConfirmActivity*|*UsbPermissionActivity*|*UsbResolverActivity*)
      cue "USBOK: press OK on the USB dialog on D-POCO; do not tick Always"
      echo "$(date +%T) $RUN dialog: $f" >> "$OUT/hand-steps.log"; sleep 8;; esac
    sleep 2; done; return 1; }
# wr WINDOW : count the wired-session re-arm lines in WINDOW (the source line holds a dash we do not grep)
wr() { printf '%s\n' "$1" | grep -aF 'AapService: wired session ended' | grep -acF 're-arming wireless mode'; }
```

**wdnc_lib.sh**
```bash
# wdnc_lib.sh : source after ptr_lib.sh and nbase.sh. Needs OUT. RUN is set per run; PHCAP=1 captures D-POCO.
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
F=/data/data/$PKG/shared_prefs/settings.xml
DHU=27870808938846; POCO=4f4027e9; MOTO=ZY22GC3BM4
send() { local a=$1; shift; adb -s $DHU shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; sleep 0.3; }
mark() { send ACTION_LOG_MARKER --es text "$1" >/dev/null
  if [ -n "$PHCAP" ]; then adb -s $POCO shell log -t OHURIG "$1"; sleep 0.3; fi; }
HF() { echo "$OUT/$RUN.hu.txt"; }
PF() { echo "$OUT/$RUN.ph.txt"; }
cap_start() { adb -s $DHU logcat -G 16M; sleep 0.3; adb -s $DHU logcat -c; sleep 0.3
  stdbuf -oL adb -s $DHU logcat -v time > "$(HF)" & echo $! > "$OUT/$RUN.hu.pid"
  if [ -n "$PHCAP" ]; then adb -s $POCO logcat -G 16M; sleep 0.3; adb -s $POCO logcat -c; sleep 0.3
    stdbuf -oL adb -s $POCO logcat -v time > "$(PF)" & echo $! > "$OUT/$RUN.ph.pid"; fi
  sleep 1; }
cap_stop() { sleep 1
  for f in "$OUT/$RUN.hu.pid" "$OUT/$RUN.ph.pid"; do [ -f "$f" ] && kill "$(cat "$f")" 2>/dev/null; rm -f "$f"; done
  if grep -aqF "AutomationMarker: $RUN-start" "$(HF)"; then echo "wrapped=no" >> "$OUT/$RUN.summary"
  else echo "wrapped=yes" | tee -a "$OUT/$RUN.summary"; fi; }
# hwin A B : head unit lines from marker A to marker B (to the end of the file if B is absent)
hwin() { awk -v a="AutomationMarker: $1" -v b="AutomationMarker: $2" '{sub(/\r$/,"")} function at(s){i=index($0,s); return i>0 && substr($0,i+length(s))~/^[[:space:]]*$/} at(a){on=1} on{print} on&&at(b){exit}' "$(HF)"; }
# pwin A B : the same on the phone capture, by the OHURIG markers
pwin() { awk -v a="$1" -v b="$2" '{sub(/\r$/,"")} /OHURIG/ && $NF==a {on=1} on{print} on && /OHURIG/ && $NF==b {exit}' "$(PF)"; }
# n TEXT LIT [LIT2] [LIT3] : count of lines in TEXT that hold every LIT
n() { local t=$1; shift; local out; out=$(printf '%s\n' "$t")
  for l in "$@"; do out=$(printf '%s\n' "$out" | grep -aF -- "$l"); done
  if [ -z "$out" ]; then echo 0; else printf '%s\n' "$out" | grep -ac .; fi; }
rend() { printf '%s\n' "$1" | grep -acE 'Throughput over [0-9]+ms: rendered=[1-9]'; }
tms() { grep -aF -m1 -- "$1" | awk '{split($2,t,":"); printf "%d\n", (t[1]*3600+t[2]*60+t[3])*1000}'; }
# gap TEXT A B : ms from the first line holding A to the first line holding B, or NA
gap() { local a b; a=$(printf '%s\n' "$1" | tms "$2"); b=$(printf '%s\n' "$1" | tms "$3")
  if [ -n "$a" ] && [ -n "$b" ]; then echo $((b-a)); else echo NA; fi; }
# waitfor S STR : wait up to S seconds for a new head unit line holding STR. Return 0 = seen.
waitfor() { local s=$1 str=$2 b i; b=$(wc -l < "$(HF)")
  for i in $(seq 1 "$s"); do tail -n +"$((b+1))" "$(HF)" | grep -aqF -- "$str" && return 0; sleep 1; done; return 1; }
rd() { adb -s $DHU shell cat $F | grep -aoE "($1)[^/]*"; sleep 0.3; }
put() { adb -s $DHU shell am force-stop $PKG; sleep 0.3
  cp "$OUT/backup-D-HU.xml" "$OUT/w.xml"; python3 -I "$OUT/sx.py" "$OUT/w.xml" "$@"
  adb -s $DHU push "$OUT/w.xml" /data/local/tmp/ohu-w.xml >/dev/null; sleep 0.3
  adb -s $DHU shell "cp /data/local/tmp/ohu-w.xml $F && chown $OWN $F && chmod 660 $F"; sleep 0.3; }
state() { send ACTION_QUERY_STATE | grep -a 'data='; }
clean_hu() { adb -s $DHU shell am start -a android.intent.action.VIEW -d "headunit://exit" >/dev/null; sleep 3
  adb -s $DHU shell am force-stop $PKG; sleep 0.3; }
# arm_quiet : arm headless, then a quiet window before the command. Sets QA (the window's start marker).
arm_quiet() { send ACTION_START_WIRELESS > "$OUT/$RUN-arm.reply"
  waitfor 45 'NativeAA: ACTIVELY LISTENING on Android Auto UUID'; echo "listening=$?" >> "$OUT/$RUN.summary"
  mark $RUN-arm; QA=$RUN-arm; sleep 30
  local w; w=$(hwin $RUN-arm __none__)
  if [ "$(n "$w" 'SSL handshake complete')" -gt 0 ] || [ "$(n "$w" 'NativeAA: Connection accepted from ')" -gt 0 ]; then
    echo "quiet1=broken" | tee -a "$OUT/$RUN.summary"; mark $RUN-quiet-broken
    send ACTION_DISCONNECT > "$OUT/$RUN-quiet-x.reply"; sleep 10
    mark $RUN-arm2; QA=$RUN-arm2; sleep 20
  else echo "quiet1=held" >> "$OUT/$RUN.summary"; fi
  mark $RUN-go; }
# endrun : user exit, then a clean stop of the head unit app
endrun() { mark $RUN-x; send ACTION_DISCONNECT > "$OUT/$RUN-x.reply"; sleep 8
  mark $RUN-end; clean_hu; cap_stop; th_report $RUN; PHCAP=; }
```

`OWN` and the name variables (`POCO_BT`, `MOTO_BT`, `POCO_NAME`, `MOTO_NAME`, `POCO_LC`, `POCO_URL`)
come from PN. Write them to `$OUT/pn.env` in PN and source it in every Stage N script.

**Names with spaces need two layers of quotes.** `adb shell` joins its arguments and the device shell
splits them again. Write `--es device_name "'$POCO_NAME'"`. If the quotes are lost, `am` reports an
error and the `connect by` line is missing; fix the quoting and run that step again.

**Verbs and commands used. Nothing else acts on the app:**

| Action | Command | Stage |
|---|---|---|
| arm Native AA (a launch arms it; the verb only if not) | `launch_arm <marker>` | A |
| arm the stored mode headless | `send ACTION_START_WIRELESS` (inside `arm_quiet`) | N |
| user exit | `send ACTION_DISCONNECT` (inside `endrun` and `arm_quiet`) | A, U, N |
| USB button | `send ACTION_CHECK_USB` | U |
| screen off, the verb under test | `send ACTION_SCREEN_OFF` | A |
| screen on, the verb under test | `send ACTION_SCREEN_ON` | A |
| connect by name, the verb under test | `send ACTION_CONNECT --es device_name "'<name>'"` | N |
| connect by MAC, the verb under test | `send ACTION_CONNECT --es mac <mac> [--ez no_ui true]` | N |
| the poke verb, by name | `send ACTION_NATIVE_AA_POKE --es device_name "'<name>'"` | N |
| the poke verb, old form | `send ACTION_NATIVE_AA_POKE --es extra_mac <MAC>` | N |
| bare connect (the USB check) | `send ACTION_CONNECT` | N |
| bring the projection forward | `send ACTION_RAISE_PROJECTION` | N |
| deep link under test | `adb -s $DHU shell am start -a android.intent.action.VIEW -d "'headunit://connect?device_name=<name>'"` | N |
| marker | `mark <label>` (labels have no spaces) | all |
| build identity and session state | `state` | all |
| launch | `adb -s $U shell am start -n $MAIN` | A, U |
| external power lost, back | `pw_lost`, `pw_back` (fake lever: `dumpsys battery unplug`, `dumpsys battery set ac 1`) | A, U |
| grant the device admin | `adb -s $U shell dpm set-active-admin --user 0 $ADMIN` | A |
| hard stop | `clean_hu` | all |

Every verb must print `AutomationReceiver: ` in the head unit capture. A step with no such line never
landed: that step is void, not a FAIL. Save each verb's `am` output to a `.reply` file; the reply is
on its `data=` line. Never run two adb calls against one unit at the same time.

## 6. The deciding lines

Every app string below matched `git grep -F` in `app/src/main` at `3101712a`. All are INFO or WARN,
so `log-level` 2 carries them. Stage A and U lines are grepped in the D-POCO capture (`$(SF)`),
Stage N lines in the D-HU capture (`$(HF)`), each in the marker window its run names.

### Stages A and U (D-POCO capture)

| Line (grep -F) | Source | Used for |
|---|---|---|
| `AutomationReceiver: ` | `AutomationReceiver` | a verb landed |
| `AutomationMarker: ` | `AutomationEffectRunner` | markers |
| `WakeDetect: POWER_DISCONNECTED` | `AapService` | the power-lost lever landed |
| `WakeDetect: POWER_CONNECTED` | `AapService` | the power-back lever landed |
| `WakeDetect: SCREEN_OFF` | `AapService` | the panel went off |
| `WakeDetect: SCREEN_ON (screen was off for ` | `AapService` | the panel came on |
| `WakeDetect: power lost beside a screen-off; treating it as the car being switched off` | `AapService` | must stay 0 |
| `WakeDetect: re-arming the wireless bring-up held while the unit was asleep` | `AapService` | record in R7n |
| `WakeDetect: hibernate wake detected` | `AapService` | record in R7n |
| `AapService: external-power disconnect: power lost; ending the session in ` | `AapService` | A1; the delay follows, then `ms unless it comes back.` |
| `ms unless it comes back.` | `AapService` | A1 tail (`delay` reads it) |
| `AapService: external-power disconnect: power stayed away; ending the session the way the Exit button does.` | `AapService` | A6 |
| `AapService: external-power disconnect: not ending the session (enabled=` | `AapService` | must stay 0 |
| `AapService: external-power disconnect: power is back; re-arming the wireless bring-up.` | `AapService` | wireless return (R7n); must stay 0 in R11 |
| `AapService: external-power disconnect: power is back; checking USB again.` | `AapService` | USB return (R11); must stay 0 in R7n |
| `AapService: external-power disconnect: power is back while the session is still ending; the return runs once it has ended.` | `AapService` | record |
| `external-power disconnect` | `AapService` | the co-filter of `ep` |
| `keeping the wireless bring-up down until something re-arms it.` | `AapService` (after the label) | the stand-down; count with `ep` |
| `: screen off ` | `AapService` (after the label) | the stand-down screen-off; count with `ep`; ends `done.` or `refused by the system.` |
| `done.` | `AapService` | the screen-off worked |
| `refused by the system.` | `AapService` | the screen-off did not work |
| `: power came back while the session was ending; not standing down.` | `AapService` (after the label) | record |
| `AapService: external-power disconnect: the session end never reached the stand-down in ` | `AapService` | must stay 0 |
| `AapService: wired session ended by an auto-disconnect; the stand-down decides the re-arm of wireless mode` | `AapService` | R11: the hold |
| `the wired session's wireless re-arm is dropped with the stand-down.` | `AapService` (after the label) | R11: must stay 0 |
| `AapService: wired session ended` | `AapService` | co-filter of `wr` |
| `re-arming wireless mode` | `AapService` | the wired re-arm, counted with `wr` |
| `USB session established while wireless mode` | `AapService` | R11 validity: the USB session stopped the wireless stack |
| `WifiLauncher: Initializing WiFi Mode: ` | `WifiLauncherManager` | the wireless stack started |
| `Requesting USB permission for` | `UsbLauncherManager` | record the USB identities |
| `Found device already in accessory mode` | `UsbLauncherManager` | the re-attach fault (R11 condition 10) |
| `Version request send failed` | `AapTransport` | the re-attach fault (R11 condition 10) |
| `AapService: turning the screen on (` | `AapService` | screen-on ran; then `automation` or `external power back` |
| `) by wake lock and ` | `AapService` | screen-on also raised a screen; then `the home screen` or `the projection`, then ` by <DIRECT/OVERLAY/NOTIFICATION>`, then ` (keyguard=<true/false/null>)` |
| `(keyguard=` | `AapService` | the keyguard reading at the raise; the value follows, then `)` |
| `: screen-on asks the system to dismiss the keyguard` | `MainActivity` or `AapProjectionActivity` (before the colon) | the raised screen asked for the dismiss |
| `: the keyguard was dismissed` | the same | the dismiss worked |
| `: the keyguard stays up (the user did not unlock)` | the same | must stay 0 (no secure keyguard here) |
| `: the system refused to dismiss the keyguard` | the same | must stay 0 |
| `: keyguard dismiss request failed: ` | the same | must stay 0 |
| `the home screen` | `AapService` | which screen the raise named |
| `the projection` | `AapService` | which screen the raise named |
| `AapService: screen-on launch failed: ` | `AapService` | must stay 0 |
| `AapTransport stopping and sending byebye (` | `AapTransport` | our side sent the ByeBye |
| `Disconnect action received.` | `AapService` | the reference exit in `endrun` |
| `AapService: Stopping GpsLocationService and NightModeManager since connection is disconnected` | `AapService` | a session ended |
| `SSL handshake complete` | `AapSslContext` | a session formed |
| `Throughput over ` | `VideoDecoder` | picture; `rend` regex `Throughput over [0-9]+ms: rendered=[1-9]` |
| `MATCH! Starting AapService` | `AutoStartReceiver` | discard check |
| `"screenOff"` | `AutomationEffectRunner` reply key | V3, V4n, V4k: `"screenOff":true` |

### Stage N (D-HU capture)

The service builds the `connect by` lines from parts, so the runs grep the parts with `n` and check
the runtime values (name, MAC) as separate fields.

| Line or part (grep -F) | Source | Used for |
|---|---|---|
| `AutomationReceiver: ` | `AutomationReceiver` | a verb landed; with `ACTION_CONNECT` or `ACTION_NATIVE_AA_POKE` |
| `AutomationMarker: ` | `AutomationEffectRunner` | markers |
| `AutomationActivity: Received intent. Action: ` | `AutomationActivity` | C1d: the deep link landed |
| `WifiLauncher: Initializing WiFi Mode: ` | `WifiLauncherManager` | with `NATIVE`: the stack armed Native AA |
| `NativeAA: ACTIVELY LISTENING on Android Auto UUID` | `NativeAaHandshakeManager` | listeners open, before the quiet window |
| `AapService: connect by ` | `AapService.commandPokeTarget` | every command-path line starts so |
| `resolved to ` | `AapService.commandPokeTarget` | the gate passed (`Wake`) |
| `: that phone is already connected or connecting.` | `AapService.commandPokeTarget` | `AlreadyConnected` |
| `refused: another session is live.` | `AapService.commandPokeTarget` | `RefuseLiveSession` |
| `refused: ` | `AapService.commandPokeTarget` | any gate refusal; the reason follows |
| `no paired phone named` | `ConnectByNameGate` | reason `NOT_FOUND` |
| `not paired` | `ConnectByNameGate` | reason `NOT_PAIRED` |
| `not Native AA` | `ConnectByNameGate` | reason `NOT_NATIVE` |
| `BluetoothHelper: driver candidates: ` | `BluetoothHelper` | record: the verdict roll-up (prints only when it changes) |
| `NativeAA: Driver selected: ` | `NativeAaHandshakeManager.selectDriver` | with `explicit` or `automatic`: the pick |
| `AapService: Received manual Native-AA poke request for ` | `AapService` | the poke branch ran with a MAC |
| `AapService: manual Native-AA poke ignored: the wireless mode is not Native AA.` | `AapService` | must stay 0 in C4c (the gate returns first) |
| `AapService: WiFi button on the Bluetooth module route: ` | `AapService` | must stay 0 everywhere |
| `NativeAA: Attempting manual poke to ` | `NativeAaHandshakeManager` | the explicit wake ran |
| `NativeAA: Calling socket.connect() for ` | `NativeAaHandshakeManager` | the wake reached the radio |
| `NativeAA: waking a phone over a hands-free link it holds is` | `NativeAaHandshakeManager` | record when C1 condition 7 fails |
| `NativeAA: Attempting active poke to device: ` | `NativeAaHandshakeManager` | the retry loop poked; must stay 0 in quiet windows |
| `NativeAA: Connection accepted from ` | `NativeAaHandshakeManager` | the phone came back over Bluetooth |
| `SSL handshake complete` | `AapSslContext` | a session formed |
| `Throughput over ` | `VideoDecoder` | picture; `rend` matches `Throughput over [0-9]+ms: rendered=[1-9]` |
| `ms: rendered=` | `VideoDecoder` | part of the `rend` regex |
| `AapService: raising the projection by ` | `AapService` | the service raised the projection; record the route |
| `AapService: Not raising the projection, a no_ui command asked for the session only` | `AapService` | C2: `no_ui` held the raise |
| `Boot WakeLock acquired (10min timeout)` | `AapService` | prints on the first `ACTION_CHECK_USB` of a process: the USB check ran |
| `AapTransport stopping and sending byebye (` | `AapTransport` | must stay 0 in C3 (the session survives) |
| `createGroup SUCCESS` | `WifiDirectManager` | group count per run |
| `MATCH! Starting AapService` | `AutoStartReceiver` | discard check; must stay 0 |

### Not in our tree

| Line | Where | Used for |
|---|---|---|
| `received ByeByeRequest` | D-MOTO, tag `CAR.GAL.GAL.LITE` (set VERBOSE by `cap_start`); present on Gearhead 18.0 in round 2 | Stages A and U: the phone got our ByeBye |
| `Creating rfcomm socket for device: ` | D-POCO, `GH.WIRELESS.BT` | Stage N: the phone answered a wake and dialled the head unit |
| `State changed to ` | D-POCO, `GH.WIRELESS.SETUP` | Stage N: record the phone's setup states |
| `THROTTLE_LIMIT_EXCEEDED` | D-POCO, Gearhead | Stage N: phone throttle; triggers the recovery in section 7 |
| `OHURIG` | either phone, our own `log -t` markers | phone windows |
| `device wake-up` | D-POCO as head unit, `PowerManagerService` | record for every screen-on |
| `Lock credential verified successfully` | D-POCO, `cmd lock_settings verify` | PA: no lock credential |

Framework readings, not log lines: `mWakefulness=` or `mScreenOn=` from `wake` (`dark`: `Asleep`,
`Dozing` or `mScreenOn=false`; `lit`: `Awake` or `mScreenOn=true`), the keyguard from `kg`, the
focused window from `focus`, the admin count from `admin`.

## 7. Runs

Order: R0, PA, Stage A (grant, V3, keyguard off, V4n, R5n/R7n, keyguard back, V4k, R5k/R7k), the
operator block (HS2, NB, Stage U with R11, X), Stage N (PN, C5u, C1 with C3, C5p, C2, C4 and C4c,
C1d, XN).

Every run: `th_gate` first (inside `bringup` for Stage A session runs; it runs
`$RIG_TOOLKIT/rig_thermal.sh wait 75`). Stop the round after a second failed wait, per
`rig-quirks/topics/tooling.md`, and mark the rest UNTESTABLE (host thermal). Report `th_report $RUN`.
Write the results file after every run.

**Time.** Build 25 min. PA 5, Stage A 40, the operator block 35 (HS2 3, NB 2 to 7, Stage U setup 5,
R11 12, X 8), PN 15, Stage N runs 35. Thermal-gate waits about 15. Total about 2 h 35 min. Do not let
a run cross midnight: `gap` and `aftms` use the time of day.

**Stop rules (counts).**

- Stage A: if V4n and R5n/R7n both end UNTESTABLE, run "Keyguard back", skip V4k and R5k/R7k, and
  go to the operator block. The same holds if V4k ends UNTESTABLE for a session fault.
- Stage U: R11 gets one RECOVER at most; no session after it makes R11 UNTESTABLE.
- Stage N: if C1 (both tries) and C5p all give `ssl=1`, skip C2 and C1d, run C4 and C4c, and say so
  in Setup notes.

### Stage A rules

**Session runs** (V4n, R5n/R7n, V4k, R5k/R7k) set `PHCAP=1` and `K` before `bringup`. If `ssl=1`, run
`endrun` and repeat the run once. A second `ssl=1` makes that run UNTESTABLE. **Every session run is
valid only when** `hwin $RUN-arm $RUN-cut | rend` is 1 or more, `MATCH! Starting AapService` is 0 in
`hwin $RUN-start $RUN-end`, and `$RUN.summary` reads `wrapped=no`. Report all three. A run with
`wrapped=yes` is void: run it once more.

### PA. Precheck for Stages A and U, once

Apps stopped on every unit. Record every output in Setup notes.

**pa_precheck.sh**
```bash
POCO=4f4027e9; MOTO=ZY22GC3BM4; DHU=27870808938846; PKG=com.andrerinas.headunitrevived
adb -s $POCO shell am force-stop $PKG; sleep 0.3
adb -s $POCO shell run-as $PKG cat shared_prefs/settings.xml > $OUT/backup-D-POCO.xml
diff <(tr -d '\r' < rig-data/rounds/automation-screen-disconnect-round2/backup-D-POCO.xml) <(tr -d '\r' < $OUT/backup-D-POCO.xml)   # record the delta, even if zero
adb -s $POCO shell dumpsys battery | grep -a -E 'AC powered|USB powered|level'      # level 60 or more before Stage U
adb -s $MOTO shell dumpsys battery | grep -a -E 'level'
adb -s $MOTO shell dumpsys wifi | grep -a -m1 'Wi-Fi is'; sleep 0.3                  # record as MOTO_RADIOS (with the next line)
adb -s $MOTO shell dumpsys bluetooth_manager | grep -a -m1 -E 'enabled:|state:'; sleep 0.3
adb -s $POCO shell cmd lock_settings verify                 # must print "Lock credential verified successfully"
adb -s $POCO shell locksettings get-disabled                # record as KG_POCO (round 2 implies false)
adb -s $POCO shell settings get system screen_off_timeout   # record as TIMEOUT_POCO
U=$POCO admin                                               # must be 0
adb -s $POCO shell dumpsys bluetooth_manager | grep -a -A14 'Bonded devices' | grep -ac 'motorola edge 30 neo'   # 1 or more
adb -s $MOTO shell settings get secure bluetooth_address    # MOTO_MAC (round 2: A0:46:5A:97:E4:95)
adb -s $MOTO shell dumpsys package com.google.android.projection.gearhead | grep -a -m1 versionName
adb -s $MOTO shell dumpsys window | grep -a -m2 -oE '(isKeyguardShowing|mShowingLockscreen)=[a-z]+'
adb -s $MOTO shell dumpsys wifip2p | grep -a -m2 -E 'isGroupOwner|groupFormed'   # isGroupOwner false
adb -s $POCO shell settings put system screen_off_timeout 1800000                 # X restores TIMEOUT_POCO
```

Then:

1. If `cmd lock_settings verify` does not print `Lock credential verified successfully`, stop and
   tell the operator: `lockNow()` would lock D-POCO behind a credential adb cannot enter.
2. If D-MOTO's keyguard reads `true` and its screen asks for a PIN, cue HS0 once.
3. If the D-MOTO bond count is 0, the Stage A session runs are UNTESTABLE. Stage U does not need it.
4. If D-POCO's battery reads below 60, charge it before Stage U and record the level.
5. Run R0's check: `U=$POCO state`.

### Stage A. D-POCO as head unit, D-MOTO as phone, both on PC cables

**stage_a.sh**
```bash
U=$POCO; UN=D-POCO; PH=$MOTO; LEVER=fake; MAC=<MOTO_MAC from PA>; mkbase
phone_off $MOTO
```

#### Grant

**grant.sh**
```bash
adb -s $U shell dpm set-active-admin --user 0 $ADMIN; sleep 1; admin     # must read 1
```

If `dpm` fails, use HS3:

**grant_hs3.sh**
```bash
adb -s $U shell am start -a android.app.action.ADD_DEVICE_ADMIN --ecn android.app.extra.DEVICE_ADMIN $ADMIN; sleep 3
cue "HS3: accept the device admin screen for Open Headunit on D-POCO"
sleep 30; admin                                                          # must read 1
```

If `admin` still reads 0, V3, V4n, R5n/R7n, V4k and R5k/R7k are UNTESTABLE; go to the operator block.

#### V3. Point 1: screen-on with the grant, no session

**v3.sh**
```bash
RUN=V3; PHCAP=; CABLE=; adb -s $U shell am force-stop $PKG; sleep 0.3; fakeplug
K="$BASE $A1 $EP_OFF"; put $K; rd 'allow-external|external-power' | tee $OUT/$RUN.keys
th_gate; cap_start; mark V3-start; adb -s $U shell am start -n $MAIN; sleep 10
ready pre
mark V3-off; send ACTION_SCREEN_OFF > $OUT/V3-off.reply; sleep 3; wake | tee $OUT/V3.w-off; kg | tee $OUT/V3.kg-off
sleep 10; mark V3-on; send ACTION_SCREEN_ON > $OUT/V3-on.reply; sleep 5; wake | tee $OUT/V3.w-on; focus | tee $OUT/V3.focus; kg | tee $OUT/V3.kg-on
mark V3-end; clean_hu; cap_stop; th_report V3
```

`V3.w-pre` must be lit. If it is dark, the run is void: run it once more.

**PASS when all hold:**
1. `V3-off.reply` holds `"screenOff":true` and `"ok":true`; `V3.w-off` is dark; `hwin V3-off V3-on` holds 1 `WakeDetect: SCREEN_OFF`.
2. `hwin V3-off V3-on`: `WakeDetect: power lost beside a screen-off` is 0.
3. `hwin V3-on V3-end`: 1 `AapService: turning the screen on (` line, and that line holds `automation`, `) by wake lock and ` and `the home screen`. `V3.w-on` is lit. `gap "$(hwin V3-on V3-end)" 'AapService: turning the screen on (' 'WakeDetect: SCREEN_ON (screen was off for '` is 0 to 5000.
4. `AapService: screen-on launch failed: ` is 0 in the capture.
5. If `V3.kg-off` reads `isKeyguardShowing=true`: the screen-on line ends `(keyguard=true)`; `hwin V3-on V3-end` holds 1 `: screen-on asks the system to dismiss the keyguard` and 1 `: the keyguard was dismissed`, both on `MainActivity` lines; `V3.kg-on` reads `isKeyguardShowing=false`; `V3.focus` names `MainActivity`. If `V3.kg-off` reads `false`, condition 5 is INCONCLUSIVE (no keyguard to dismiss).

If the change did nothing, condition 3 would read `... by wake lock` alone, as in round 2. Record
the whole screen-on line (the strategy), the `device wake-up` lines in `hwin V3-on V3-end`, both `kg`
readings and `V3.focus`.

#### Keyguard off

**kg_off.sh**
```bash
kgset true | tee $OUT/kg-off.txt          # must print true
```

If it does not print `true`, V4n and R5n/R7n are UNTESTABLE (no keyguard-free state); go to
"Keyguard back".

#### V4n. Screen-on during a session, no keyguard

**v4n.sh**
```bash
RUN=V4n; PHCAP=1; CABLE=; K="$BASE $A1 $EP_OFF"; ready pre; bringup; ready live
sleep 20; mark V4n-cut; mark V4n-off; send ACTION_SCREEN_OFF > $OUT/V4n-off.reply; sleep 10; wake | tee $OUT/V4n.w-off; kg | tee $OUT/V4n.kg-off
mark V4n-on; send ACTION_SCREEN_ON > $OUT/V4n-on.reply; sleep 8; wake | tee $OUT/V4n.w-on; focus | tee $OUT/V4n.focus
sleep 15; mark V4n-after; endrun
```

Validity, beside the session-run rule: `V4n.kg-off` reads `isKeyguardShowing=false`. If it reads
`true`, the keyguard setting did not hold: V4n is UNTESTABLE.

**PASS when all hold:**
1. `V4n-off.reply` holds `"screenOff":true`; `V4n.w-off` is dark; `V4n.w-on` is lit.
2. `hwin V4n-off V4n-after`: `WakeDetect: power lost beside a screen-off` is 0.
3. If `hwin V4n-off V4n-on | c 'since connection is disconnected'` is 0 (the session survived): `hwin V4n-on V4n-after` holds 1 `AapService: turning the screen on (` line, and it holds `automation`, `) by wake lock and ` and `the projection`; `V4n.focus` names `AapProjectionActivity`; `hwin V4n-on V4n-after | rend` is 1 or more.
4. If that count is 1 or more (the sleep path ended the session): the screen-on line holds `the home screen`. Report it as a finding about the sleep path, not a FAIL of this change.
5. Phone: `pwin V4n-off V4n-after | c 'received ByeByeRequest'` is 0 when condition 3 applies.

With no keyguard, the projection comes back and renders even without a raise. So the screen-on line
is the evidence for the raise, and the focus and `rend` show that the raise did no harm. Record
which of 3 or 4 applied and the strategy.

#### R5n and R7n. Screen-off after the end, then power back, no keyguard

Precondition: `admin` reads 1. The panel stays dark for about 40 s, under the 60 s hibernate
threshold.

**r5n_r7n.sh**
```bash
RUN=R5n; PHCAP=1; CABLE=; K="$BASE $A0 $EP_MIN_SCR"; ready pre; bringup; ready live
sleep 20; mark R5n-cut; pw_lost; sleep 20
wake | tee $OUT/R5n.wake; kg | tee $OUT/R5n.kg
sleep 20; pw_back; mark R7n-back; sleep 5; wake | tee $OUT/R7n.wake; kg | tee $OUT/R7n.kg
waitfor 120 'SSL handshake complete'; echo "ssl2=$?" | tee -a $OUT/R5n.summary
sleep 15; focus | tee $OUT/R7n.focus; endrun
```

`ALL=$(hwin R5n-cut R5n-x-go)`, `W=$(printf '%s\n' "$ALL" | upto 'WakeDetect: POWER_CONNECTED')`,
`V=$(printf '%s\n' "$ALL" | from 'WakeDetect: POWER_CONNECTED')`.

**R5n PASS when all hold** (on `W`; a regression check, R5 passed in round 2):
1. 1 A1 line, and `delay "$W"` is 5000 (the 2 was clamped).
2. A6 1; `gap "$W" 'power lost; ending the session in ' 'power stayed away;'` is 4500 to 8000.
3. `printf '%s\n' "$W" | ep 'keeping the wireless bring-up down'` is 1; `printf '%s\n' "$W" | grep -aF ': screen off ' | grep -acF 'done.'` is 1; `refused by the system.` is 0.
4. `WakeDetect: SCREEN_OFF` 1, and `gap "$W" 'WakeDetect: POWER_DISCONNECTED' 'WakeDetect: SCREEN_OFF'` is over 5000.
5. `WakeDetect: power lost beside a screen-off` is 0.
6. `R5n.wake` is dark, and `R5n.kg` reads `isKeyguardShowing=false` (else R7n is UNTESTABLE: the keyguard setting did not hold).
7. Phone: `pwin R5n-cut R7n-back | c 'received ByeByeRequest'` is 1 or more.

**R7n PASS when all hold** (on `V`):
1. `WakeDetect: POWER_CONNECTED` 1.
2. 1 `AapService: turning the screen on (` line holding `external power back`; `gap "$V" 'WakeDetect: POWER_CONNECTED' 'AapService: turning the screen on ('` is 0 to 1000.
3. `power is back; re-arming the wireless bring-up.` 1; `gap "$V" 'AapService: turning the screen on (' 'power is back; re-arming'` is 0 or more; `power is back; checking USB again.` is 0.
4. INCONCLUSIVE by rule (the ROM lights the panel on plug): report `gap "$V" 'AapService: turning the screen on (' 'WakeDetect: SCREEN_ON (screen was off for '`.
5. INCONCLUSIVE by rule: report `R7n.wake`.
6. `ssl2=0`; `printf '%s\n' "$V" | rend` is 1 or more; `R7n.focus` names `AapProjectionActivity`.
7. `AapService: screen-on launch failed: ` is 0.

Record the whole screen-on line (expected: `... by wake lock` alone, because the ROM lit the panel
first), the `device wake-up` lines, `R7n.kg`, and the counts of `WakeDetect: re-arming the wireless
bring-up held while the unit was asleep` and `WakeDetect: hibernate wake detected` in `V`.

#### Keyguard back

**kg_back.sh**
```bash
kgset <KG_POCO from PA> | tee $OUT/kg-back.txt     # must print KG_POCO
```

If KG_POCO is `true`, D-POCO has no keyguard to dismiss: V4k and R5k/R7k are UNTESTABLE; go to the
operator block.

#### V4k. Point 3: screen-on during a session, with the swipe keyguard

**v4k.sh**
```bash
RUN=V4k; PHCAP=1; CABLE=; K="$BASE $A1 $EP_OFF"; ready pre; bringup; ready live
sleep 20; mark V4k-cut; mark V4k-off; send ACTION_SCREEN_OFF > $OUT/V4k-off.reply; sleep 10; wake | tee $OUT/V4k.w-off; kg | tee $OUT/V4k.kg-off
mark V4k-on; send ACTION_SCREEN_ON > $OUT/V4k-on.reply; sleep 8; wake | tee $OUT/V4k.w-on; kg | tee $OUT/V4k.kg-on; focus | tee $OUT/V4k.focus
sleep 15; mark V4k-after; endrun
```

Validity, beside the session-run rule: `V4k.kg-off` reads `isKeyguardShowing=true`. If it reads
`false`, `lockNow()` put up no keyguard: V4k is UNTESTABLE.

**PASS when all hold:**
1. `V4k-off.reply` holds `"screenOff":true`; `V4k.w-off` is dark; `V4k.w-on` is lit.
2. `hwin V4k-off V4k-after`: `WakeDetect: power lost beside a screen-off` is 0.
3. `hwin V4k-on V4k-after` holds 1 `AapService: turning the screen on (` line, and it holds `automation`, `) by wake lock and `, `the projection` and ends `(keyguard=true)`. If `hwin V4k-off V4k-on | c 'since connection is disconnected'` is 1 or more, it holds `the home screen` instead: report that as a finding about the sleep path, and grade conditions 4 to 6 on `MainActivity` in place of `AapProjectionActivity`.
4. `hwin V4k-on V4k-after` holds 1 `: screen-on asks the system to dismiss the keyguard` and 1 `: the keyguard was dismissed`, both on `AapProjectionActivity` lines. `: the keyguard stays up (the user did not unlock)`, `: the system refused to dismiss the keyguard` and `: keyguard dismiss request failed: ` are 0.
5. `V4k.kg-on` reads `isKeyguardShowing=false`; `V4k.focus` names `AapProjectionActivity`.
6. `hwin V4k-on V4k-after | rend` is 1 or more; `AapService: screen-on launch failed: ` is 0.
7. Phone: `pwin V4k-off V4k-after | c 'received ByeByeRequest'` is 0 when the session survived.

Round 2's V4 is the "before": focus `NotificationShade` and `rend` 0 after the screen-on. Record the
whole screen-on line, the strategy, and the gap from the screen-on line to `: the keyguard was
dismissed`.

#### R5k and R7k. Point 3: screen-off after the end, then power back, with the swipe keyguard

Precondition: `admin` reads 1. Same timing as R5n/R7n.

**r5k_r7k.sh**
```bash
RUN=R5k; PHCAP=1; CABLE=; K="$BASE $A0 $EP_MIN_SCR"; ready pre; bringup; ready live
sleep 20; mark R5k-cut; pw_lost; sleep 20
wake | tee $OUT/R5k.wake; kg | tee $OUT/R5k.kg
sleep 20; pw_back; mark R7k-back; sleep 5; wake | tee $OUT/R7k.wake; kg | tee $OUT/R7k.kg
waitfor 120 'SSL handshake complete'; echo "ssl2=$?" | tee -a $OUT/R5k.summary
sleep 15; kg | tee $OUT/R7k.kg-after; focus | tee $OUT/R7k.focus; endrun
```

`ALL=$(hwin R5k-cut R5k-x-go)`, `W=$(printf '%s\n' "$ALL" | upto 'WakeDetect: POWER_CONNECTED')`,
`V=$(printf '%s\n' "$ALL" | from 'WakeDetect: POWER_CONNECTED')`.

**R5k PASS when** R5n conditions 1 to 5 and 7 hold on this `W`, with `R5k` and `R7k` in place of
`R5n` and `R7n`, and `R5k.wake` is dark. `R5k.kg` must read `isKeyguardShowing=true`; if it reads
`false`, `lockNow()` put up no keyguard and R7k is UNTESTABLE.

**R7k PASS when all hold** (on `V`):
1. `WakeDetect: POWER_CONNECTED` 1.
2. 1 `AapService: turning the screen on (` line holding `external power back`, `) by wake lock and `, `the home screen` and ending `(keyguard=true)`; `gap "$V" 'WakeDetect: POWER_CONNECTED' 'AapService: turning the screen on ('` is 0 to 1000.
3. 1 `: screen-on asks the system to dismiss the keyguard` and 1 `: the keyguard was dismissed`, both on `MainActivity` lines. `: the keyguard stays up (the user did not unlock)`, `: the system refused to dismiss the keyguard` and `: keyguard dismiss request failed: ` are 0.
4. INCONCLUSIVE by rule (the ROM lights the panel on plug): report `R7k.wake`.
5. `power is back; re-arming the wireless bring-up.` 1; `power is back; checking USB again.` is 0.
6. `ssl2=0`; `printf '%s\n' "$V" | rend` is 1 or more; `R7k.kg-after` reads `isKeyguardShowing=false`; `R7k.focus` names `AapProjectionActivity`.
7. `AapService: screen-on launch failed: ` is 0.

Round 2's R7 is the "before": the new session's projection stayed behind the keyguard, rendered
nothing, and `ProjectionRaiseDeadlinePolicy` ended it at 16 s. Record the whole screen-on line,
`R7k.kg`, and the gap from `: the keyguard was dismissed` to `SSL handshake complete`.

### The operator block: HS2, NB, Stage U, X

The operator stays from HS2 to the end of X. Give the one-message instruction in section 3 first.

#### HS2

**hs2.sh**
```bash
adb -s $U shell am force-stop $PKG; sleep 0.3
adb -s $U shell am start -n 'com.android.settings/.Settings\$DeviceAdminSettingsActivity'; sleep 3
focus                                                                     # must name DeviceAdminSettingsActivity
cue "HS2: deactivate Open Headunit on the device admin screen of D-POCO"
sleep 30; admin                                                           # must read 0
adb -s $U shell input keyevent 3
```

The `\$` is needed: the device shell otherwise eats the `$` and opens plain Settings (round 2). If
`admin` does not read 0 after 120 s, **stop and tell the operator**: the grant blocks an uninstall.
HS2 runs even when the grant never succeeded; then `admin` already reads 0 and no cue is needed.

#### NB. The bond check for Stage N, while the cables are in

D-HU's Bluetooth comes back on here and stays on for the rest of the round. Stage U does not need it
off: D-MOTO's Bluetooth is off there.

**nb_bonds.sh**
```bash
adb -s $DHU shell svc bluetooth enable; sleep 5
adb -s $DHU shell dumpsys bluetooth_manager | grep -a -m1 -E 'enabled:|state:'; sleep 0.3      # must read on
POCO_BT=$(adb -s $POCO shell settings get secure bluetooth_address | tr -d '\r'); echo "POCO_BT=$POCO_BT"; sleep 0.3   # round history: DC:B7:2E:5E:4E:59
adb -s $POCO shell dumpsys bluetooth_manager | grep -a -A20 'Bonded devices' > $OUT/nb-poco-bonds.txt; sleep 0.3
adb -s $DHU shell dumpsys bluetooth_manager | grep -a -A20 'Bonded devices' > $OUT/nb-dhu-bonds.txt; sleep 0.3
P2=$(grep -ac Navegadortz2 $OUT/nb-poco-bonds.txt); P3=$(grep -ac Navegadortz3 $OUT/nb-poco-bonds.txt); D=$(grep -ac "$POCO_BT" $OUT/nb-dhu-bonds.txt)
echo "poco_has_dhu=$P2 poco_has_dsam=$P3 dhu_has_poco=$D" | tee $OUT/nb.txt
if [ "$P2" -eq 0 ] || [ "$P3" -ne 0 ] || [ "$D" -eq 0 ]; then
  cue "H1: on D-POCO forget Navegadortz3; if D-POCO is not paired with Navegadortz2 (D-HU), pair them"
  sleep 120
  adb -s $POCO shell dumpsys bluetooth_manager | grep -a -A20 'Bonded devices' > $OUT/nb-poco-bonds2.txt; sleep 0.3
  adb -s $DHU shell dumpsys bluetooth_manager | grep -a -A20 'Bonded devices' > $OUT/nb-dhu-bonds2.txt; sleep 0.3
  echo "after: poco_has_dhu=$(grep -ac Navegadortz2 $OUT/nb-poco-bonds2.txt) poco_has_dsam=$(grep -ac Navegadortz3 $OUT/nb-poco-bonds2.txt) dhu_has_poco=$(grep -ac "$POCO_BT" $OUT/nb-dhu-bonds2.txt)" | tee -a $OUT/nb.txt
fi
```

Round 2 found `Navegadortz3` in D-POCO's bonded list, so expect the H1 cue. PN checks the bonds again
and decides what Stage N can run.

#### Stage U. D-POCO as USB host, D-MOTO on its OTG port, both on wireless adb

D-HU stays on its cable and idle. Read both addresses while the cables are still in.

**stage_u_setup.sh**
```bash
adb -s $MOTO shell svc wifi enable; sleep 0.3; adb -s $POCO shell svc wifi enable; sleep 10
POCO_IP=$(adb -s $POCO shell ip -4 -o addr show wlan0 | awk '{print $4}' | cut -d/ -f1); sleep 0.3
MOTO_IP=$(adb -s $MOTO shell ip -4 -o addr show wlan0 | awk '{print $4}' | cut -d/ -f1); sleep 0.3
echo "POCO_IP=$POCO_IP MOTO_IP=$MOTO_IP" | tee $OUT/ips.env          # both must be non-empty
adb -s $POCO tcpip 5555; sleep 3; adb -s $MOTO tcpip 5555; sleep 3
adb connect $POCO_IP:5555; sleep 1; adb connect $MOTO_IP:5555; sleep 1; adb devices | tee $OUT/adb-devices.txt
POCO_W=$POCO_IP:5555; MOTO_W=$MOTO_IP:5555
adb -s $POCO_W shell getprop ro.product.device; sleep 0.3          # surya
adb -s $MOTO_W shell getprop ro.product.device; sleep 0.3          # miami
cue "CABLES: unplug D-POCO and D-MOTO from the PC. Put the OTG adapter in D-POCO. Do not plug D-MOTO in yet"
while adb -s $POCO get-state >/dev/null 2>&1; do sleep 1; done
adb -s $POCO_W get-state; adb -s $MOTO_W get-state                  # both "device"
adb -s $MOTO_W shell svc bluetooth disable; sleep 0.3
U=$POCO_W; UN=D-POCO; PH=$MOTO_W; LEVER=fake
```

If either address is empty after 30 s, or either `get-state` does not read `device`, R11 is
UNTESTABLE. Go to X.

#### R11. Point 2: a USB session ended by power loss

**r11.sh**
```bash
RUN=R11; PHCAP=1; CABLE=; adb -s $U shell am force-stop $PKG; sleep 0.3; fakeplug
K="$UKEYS"; put $K; rd 'external-power|wifi-connection-mode|connection-modes|auto-start-on-usb|log-level' | tee $OUT/$RUN.keys
th_gate; cap_start; mark R11-start
adb -s $U shell am start -n $MAIN; waitfor 15 'WifiLauncher: Initializing WiFi Mode: '; echo "armed=$?" | tee -a $OUT/R11.summary
sleep 10; ready pre; mark R11-plug
cue "OTG: plug D-MOTO's USB cable into the OTG adapter on D-POCO now"
usbwait 60; u=$?
if [ $u -ne 0 ]; then
  adb -s $PH shell dumpsys window | grep -a -m1 mCurrentFocus | tee $OUT/R11.moto-focus; sleep 0.3
  grep -aq 'Usb' $OUT/R11.moto-focus && cue "H3: on D-MOTO choose USB controlled by: This device"
  send ACTION_CHECK_USB > $OUT/R11-check1.reply; usbwait 60; u=$?; fi
if [ $u -ne 0 ]; then cue "RECOVER: unplug D-MOTO from the OTG adapter, wait 5 s, plug it back in"; sleep 10; usbwait 60; u=$?; fi
echo "ssl=$u" | tee -a $OUT/R11.summary
sleep 20; ready live; mark R11-cut; pw_lost; sleep 90
focus | tee $OUT/R11.focus-quiet
pw_back; mark R11-back
usbwait 90; echo "ssl2=$?" | tee -a $OUT/R11.summary
sleep 15; endrun
cue "OTG-OUT: unplug D-MOTO from the OTG adapter"
```

If `ssl=1`, R11 is UNTESTABLE: run `endrun`, then go to X. Do not answer a USB dialog that appears
between `R11-cut` and `R11-back`: a new session there would mix a second cause into the quiet
window. The script cues USBOK only outside that window.

`ALL=$(hwin R11-cut R11-x-go)`, `W1=$(printf '%s\n' "$ALL" | upto 'WakeDetect: POWER_CONNECTED')`,
`Wq=$(printf '%s\n' "$W1" | from 'power stayed away;')`,
`W3=$(printf '%s\n' "$ALL" | from 'WakeDetect: POWER_CONNECTED')`, `P1=$(pwin R11-cut R11-back)`.

**PASS when all hold:**
1. Validity:
   a. `hwin R11-start R11-plug | c 'SSL handshake complete'` is 0 (no wireless session formed before the plug), and `ssl=0`.
   b. `hwin R11-plug R11-cut | rend` is 1 or more, and `MATCH! Starting AapService` is 0 in `hwin R11-start R11-end`, and `wrapped=no`.
   c. `hwin R11-plug R11-cut | c 'USB session established while wireless mode'` is 1. If it is 0, the USB session never stopped the wireless stack, the path under test was not reached, and R11 is INCONCLUSIVE.
   d. `W1` holds 1 `WakeDetect: POWER_DISCONNECTED`; `gap "$ALL" 'power stayed away;' 'WakeDetect: POWER_CONNECTED'` is 60000 or more.
2. A1: `W1` holds 1 line with `AapService: external-power disconnect: power lost; ending the session in `; `delay "$W1"` is 20000; `gap "$W1" 'WakeDetect: POWER_DISCONNECTED' 'power lost; ending the session in '` is 0 to 1000.
3. A6: `W1` holds 1 A6 line; `gap "$W1" 'power lost; ending the session in ' 'power stayed away;'` is 19500 to 23000.
4. Exit flavour: `gap "$W1" 'power stayed away;' 'AapTransport stopping and sending byebye ('` is 0 to 2000.
5. Phone: `printf '%s\n' "$P1" | c 'received ByeByeRequest'` is 1 or more.
6. The hold: `W1` holds 1 `AapService: wired session ended by an auto-disconnect; the stand-down decides the re-arm of wireless mode` line; `gap "$W1" 'power stayed away;' 'the stand-down decides the re-arm'` is 0 to 5000; `printf '%s\n' "$W1" | ep 'keeping the wireless bring-up down until something re-arms it.'` is 1; `the wired session's wireless re-arm is dropped with the stand-down.` is 0.
7. Quiet: in `Wq` (A6 to power back), `wr "$Wq"` is 0, `WifiLauncher: Initializing WiFi Mode: ` is 0 and `SSL handshake complete` is 0.
8. Return: `W3` holds 1 `WakeDetect: POWER_CONNECTED` and 1 `power is back; checking USB again.`, with `gap "$W3" 'WakeDetect: POWER_CONNECTED' 'checking USB again.'` 0 to 1000; `wr "$W3"` is 1, with `gap "$W3" 'checking USB again.' 're-arming wireless mode'` 0 to 1000; `WifiLauncher: Initializing WiFi Mode: ` is 1 or more in `W3`, with `gap "$W3" 're-arming wireless mode' 'WifiLauncher: Initializing WiFi Mode: '` 1400 to 5000; `power is back; re-arming the wireless bring-up.` is 0 in `W3`.
9. `the session end never reached the stand-down in ` is 0 in `ALL`.
10. New USB session: `ssl2=0`, and `printf '%s\n' "$W3" | rend` is 1 or more. If `ssl2=1` and `W3` holds `Found device already in accessory mode` or `Version request send failed`, condition 10 is INCONCLUSIVE: that re-attach fault belongs to another thread. Otherwise `ssl2=1` is a FAIL.

If the fix did nothing, condition 6's hold line would be absent, and condition 7 would count 1 wired
re-arm and 1 `WifiLauncher: Initializing WiFi Mode: ` within about 2 s of A6. Condition 1c rules out
a PASS from a USB session that never stopped the stack.

Report: every count in conditions 6 to 8, the A6 to hold-line gap, the quiet window in ms, the
`checking USB again.` to `Initializing` gap, the POWER_CONNECTED to SSL gap in `W3`, every
`Requesting USB permission for` line in `W3` (the identity after `for`), `R11.focus-quiet`, and each
hand step with its time from `hand-steps.log`.

#### X. Clean-up, cables back

**x_cleanup.sh**
```bash
U=$POCO_W; UN=D-POCO; put; adb -s $U shell run-as $PKG cat shared_prefs/settings.xml > $OUT/restored-D-POCO.xml
diff <(tr -d '\r' < $OUT/backup-D-POCO.xml) <(tr -d '\r' < $OUT/restored-D-POCO.xml)   # must be empty
adb -s $U shell am start -a android.intent.action.VIEW -d "headunit://exit" >/dev/null; sleep 3   # before the role swap to phone
adb -s $U shell am force-stop $PKG; sleep 0.3
adb -s $POCO_W usb; sleep 3; adb -s $MOTO_W usb; sleep 3
cue "CABLES-BACK: remove the OTG adapter from D-POCO. Plug D-POCO and D-MOTO back into the PC"
adb -s $POCO wait-for-device; adb -s $MOTO wait-for-device; adb disconnect $POCO_W; adb disconnect $MOTO_W
for s in $POCO $MOTO; do adb -s $s shell dumpsys battery reset; sleep 0.3
  adb -s $s shell dumpsys battery | grep -a -E 'AC powered|USB powered'; sleep 0.3; done      # a real source true
adb -s $POCO shell settings put system screen_off_timeout <TIMEOUT_POCO>
adb -s $POCO shell locksettings get-disabled                                 # must print KG_POCO
U=$POCO admin                                                                # must read 0
adb -s $DHU shell dumpsys bluetooth_manager | grep -a -m1 -E 'enabled:|state:'   # must read on (NB turned it on)
```

If Stage U did not run, use `U=$POCO` for the `put`, the exit and the force-stop, and skip the
wireless lines. If `admin` does not read 0 or the keyguard setting differs from `KG_POCO`, stop and
tell the operator. The operator is free after X. D-MOTO's radios go back to `MOTO_RADIOS` in XN.

### Stage N rules

D-HU is the head unit, D-POCO the phone, D-MOTO off. All three are on PC cables.

**Every Stage N run is valid only when** its `$RUN.summary` reads `wrapped=no` and `MATCH! Starting
AapService` is 0 in `hwin $RUN-start $RUN-end`. A run with `wrapped=yes` is void: run it once more.
Report both values.

**Session check, used by C1, C5p, C2 and C1d.** After the command, `waitfor 120 'SSL handshake
complete'; ssl=$?`. Record `ssl`. If `ssl=1` and the phone capture since the command holds
`THROTTLE_LIMIT_EXCEEDED`, reboot D-POCO (PN step 5) and run the run once more.

### PN. Precheck for Stage N, once (about 15 min)

Record every output in Setup notes. Write every variable this step sets (`OWN`, `MOTO_BT`, `POCO_BT`,
`DHU_BT`, `POCO_NAME`, `MOTO_NAME`, `POCO_LC`, `POCO_URL`) to `$OUT/pn.env`.

1. The build gate is R0. Nothing to rebuild.
2. D-HU's shell must be root: `adb -s $DHU shell id -u` must print `0`. If not, stop the round (rig).
3. D-HU settings and owner:
   **pn_dhu.sh**
   ```bash
   adb -s $DHU shell am force-stop $PKG; sleep 0.3
   adb -s $DHU shell cat $F > $OUT/backup-D-HU.xml
   adb -s $DHU shell stat -c '%U:%G %a' /data/data/$PKG/shared_prefs     # record, even when correct
   OWN=$(adb -s $DHU shell stat -c '%U:%G' $F | tr -d '\r'); echo "OWN=$OWN"
   grep -aoE '(use-aac-audio|audio-latency-multiplier|audio-queue-capacity|enable-audio-sink)[^/]*' $OUT/backup-D-HU.xml
   ```
   If the `shared_prefs` directory is not owned by the app's user, `chown` it to `$OWN` and say so.
4. D-MOTO off:
   **pn_moto.sh**
   ```bash
   MOTO_BT=$(adb -s $MOTO shell settings get secure bluetooth_address | tr -d '\r'); echo "MOTO_BT=$MOTO_BT"   # round history: A0:46:5A:97:E4:95
   adb -s $MOTO shell svc wifi disable; sleep 0.3; adb -s $MOTO shell svc bluetooth disable; sleep 5
   adb -s $MOTO shell dumpsys wifi | grep -a -m1 'Wi-Fi is'; sleep 0.3                 # must read disabled
   adb -s $MOTO shell dumpsys bluetooth_manager | grep -a -m1 -E 'enabled:|state:'     # must read off; if not, svc bluetooth disable and read again
   ```
5. D-POCO, a fresh start as a phone. A reboot, not a force-stop, clears Gearhead's RFCOMM throttle
   and any P2P group D-POCO's own app left as a head unit:
   **pn_poco.sh**
   ```bash
   adb -s $POCO shell am force-stop $PKG; sleep 0.3
   adb -s $POCO reboot; adb -s $POCO wait-for-device
   for i in $(seq 1 30); do   # poll every 2 s, cap 60 s: boot done and Android Auto (Gearhead) running
     [ "$(adb -s $POCO shell getprop sys.boot_completed | tr -d '\r')" = 1 ] && [ -n "$(adb -s $POCO shell pidof com.google.android.projection.gearhead | tr -d '\r')" ] && break; sleep 2; done; echo "boot_poll=$i"   # 30 means the cap was hit
   adb -s $POCO shell am force-stop $PKG; sleep 0.3                                     # D-POCO's own app must not run as a head unit here
   adb -s $POCO shell ps -A | grep -ac gearhead                                         # 1 or more
   adb -s $POCO shell dumpsys bluetooth_manager | grep -a -m1 -E 'enabled:|state:'      # must read on; else svc bluetooth enable
   adb -s $POCO shell dumpsys wifi | grep -a -m1 'Wi-Fi is'                            # must read enabled; else svc wifi enable
   adb -s $POCO shell input keyevent KEYCODE_HOME; sleep 1
   adb -s $POCO shell dumpsys window | grep -a -m1 mCurrentFocus
   adb -s $POCO shell dumpsys wifip2p | grep -a -m2 -E 'isGroupOwner|groupFormed'        # isGroupOwner must be false
   adb -s $POCO shell dumpsys package com.google.android.projection.gearhead | grep -a -m1 versionName
   POCO_BT=$(adb -s $POCO shell settings get secure bluetooth_address | tr -d '\r'); echo "POCO_BT=$POCO_BT"   # round history: DC:B7:2E:5E:4E:59
   adb -s $POCO shell dumpsys bluetooth_manager | grep -a -A20 'Bonded devices' > $OUT/poco-bonds.txt
   grep -ac Navegadortz2 $OUT/poco-bonds.txt     # must be 1 or more
   grep -ac Navegadortz3 $OUT/poco-bonds.txt     # must be 0
   ```
   If `isGroupOwner` reads true, D-POCO's own app left a group: send
   `adb -s $POCO shell am start -a android.intent.action.VIEW -d "headunit://exit"`, wait 3 s,
   force-stop it, and read again.
6. D-HU Bluetooth, names and state:
   **pn_names.sh**
   ```bash
   adb -s $DHU shell dumpsys bluetooth_manager | grep -a -m1 -E 'enabled:|state:'      # must read on
   DHU_BT=$(adb -s $DHU shell settings get secure bluetooth_address | tr -d '\r'); echo "DHU_BT=$DHU_BT"   # round history: 11:46:03:10:33:59
   adb -s $DHU shell dumpsys bluetooth_manager | grep -a -A20 'Bonded devices' > $OUT/dhu-bonds.txt
   POCO_NAME=$(grep -a "$POCO_BT" $OUT/dhu-bonds.txt | head -1 | sed -E 's/.*\][[:space:]]*//' | tr -d '\r'); echo "POCO_NAME=[$POCO_NAME]"   # expected [POCO X3 NFC]
   MOTO_NAME=$(grep -a "$MOTO_BT" $OUT/dhu-bonds.txt | head -1 | sed -E 's/.*\][[:space:]]*//' | tr -d '\r'); echo "MOTO_NAME=[$MOTO_NAME]"   # expected [motorola edge 30 neo]
   POCO_LC=$(printf '%s' "$POCO_NAME" | tr 'A-Z' 'a-z'); POCO_URL=$(printf '%s' "$POCO_LC" | sed 's/ /%20/g'); echo "POCO_LC=[$POCO_LC] POCO_URL=[$POCO_URL]"
   adb -s $DHU shell appops get $PKG SYSTEM_ALERT_WINDOW            # must read allow
   adb -s $DHU shell cmd wifip2p init; sleep 0.3
   adb -s $DHU shell cmd wifip2p list-saved-groups                  # record; then delete-saved-group <id> for each, one call at a time
   ```
   - If `SYSTEM_ALERT_WINDOW` does not read `allow`, run `adb -s $DHU shell appops set $PKG SYSTEM_ALERT_WINDOW allow`, read it again and say so.
   - If `POCO_NAME` still holds a `:`, the bonded line has another format. Take the name from `dhu-bonds.txt` by hand, set it, and say so. Do the same for `MOTO_NAME`.
   - If `POCO_NAME` is empty, or a name holds a quote character, every session run is UNTESTABLE (H1 or a rename is needed). Run C5u and C4 only.
   - If `MOTO_NAME` is empty, C3's step c is UNTESTABLE. Run the rest.
   - If a bond check in step 5 or 6 fails after NB, every session run is UNTESTABLE. Run C5u and C4 only. The operator has left, so do not ask H1 again.
7. Record D-HU's WiFi Direct device name: `adb -s $DHU shell settings get global wifi_p2p_device_name`
   and `adb -s $DHU shell dumpsys wifip2p | grep -a -m2 -i devicename`. If either reads `OpenHU`, the
   pr-1001 round did not restore it. Record that in Setup notes and go on.
   Confirm no stale processes: `adb -s $DHU shell ps -A | grep -ac headunitrevived` reads 0 after the force-stop.
8. Install and identity:
   **pn_install.sh**
   ```bash
   adb -s $DHU install -r -d $OUT/apk/C.apk
   adb -s $DHU shell cat $F > $OUT/post-install-D-HU.xml
   diff $OUT/backup-D-HU.xml $OUT/post-install-D-HU.xml     # record; if it differs, push the backup back (put with no keys)
   RUN=P; state | tee $OUT/P.state                           # commit must start 3101712afcd5
   adb -s $DHU shell am force-stop $PKG
   ```
   `state` starts the app. That is fine here; the force-stop follows.

**A failure in step 2 or 8 stops the round.**

### C5u. Control: bare `ACTION_CONNECT` is still the USB check (about 2 min)

Headless, from a stopped app. No phone capture: the verdict does not touch the phone.

**c5u.sh**
```bash
RUN=C5u; PHCAP=; th_gate; put $BASE; rd 'wifi-connection-mode|native-poke|log-level' | tee $OUT/C5u.keys
cap_start; mark C5u-start
mark C5u-cmd; send ACTION_CONNECT > $OUT/C5u-cmd.reply; sleep 15
mark C5u-end; clean_hu; cap_stop; th_report C5u
W=$(hwin C5u-cmd C5u-end)
```

**PASS when all hold:**

1. `n "$W" 'AutomationReceiver: ' 'ACTION_CONNECT'` is 1.
2. `n "$W" 'Boot WakeLock acquired (10min timeout)'` is 1. (This proves the instrument. C1, C2,
   C4 and C1d use its absence.)
3. `n "$W" 'AapService: connect by '` is 0.
4. `n "$W" 'AapService: Received manual Native-AA poke request for '` is 0.

Record: `n "$W" 'WifiLauncher: Initializing WiFi Mode: ' 'NATIVE'` (the cold start arms Native AA)
and `n "$W" 'NativeAA: Attempting active poke to device: '` (expected 0, no poke target).

### C1. Point 4: by name on an armed, quiet stack (about 5 min with C3)

**c1.sh**
```bash
RUN=C1; PHCAP=1; th_gate; put $BASE; rd 'wifi-connection-mode|native-poke|native-driver|wifi-direct-stable|log-level' | tee $OUT/C1.keys
cap_start; mark C1-start
arm_quiet
mark C1-cmd; send ACTION_CONNECT --es device_name "'$POCO_NAME'" > $OUT/C1-cmd.reply
waitfor 120 'SSL handshake complete'; ssl=$?; echo "ssl=$ssl" | tee -a $OUT/C1.summary
sleep 20; mark C1-session; state | tee $OUT/C1.state
```

Then run C3 at once, in the same capture and shell (`RUN` stays `C1`). C3 ends the run with `endrun`.

Windows: `Q=$(hwin $QA C1-go)` (the quiet window; `QA` is `C1-arm` or `C1-arm2`),
`W=$(hwin C1-cmd C1-session)`, `PW=$(pwin C1-cmd C1-session)`, `PQ=$(pwin $QA C1-go)`.

**PASS when all hold:**

1. Preflight: `n "$(hwin C1-start C1-go)" 'WifiLauncher: Initializing WiFi Mode: ' 'NATIVE'` is 1 or more, and `listening=0`.
2. Quiet: in `Q`, `SSL handshake complete`, `NativeAA: Connection accepted from `,
   `NativeAA: Attempting manual poke to ` and `NativeAA: Attempting active poke to device: ` are
   each 0. In `PQ`, `Creating rfcomm socket for device: ` is 0.
3. `n "$W" 'AutomationReceiver: ' 'ACTION_CONNECT'` is 1.
4. `n "$W" 'AapService: connect by ' 'resolved to '` is 1, and that line holds
   `name "$POCO_NAME"` and `$POCO_BT`.
5. `n "$W" 'NativeAA: Driver selected: ' 'explicit'` is 1, and that line holds `$POCO_BT`.
6. `n "$W" 'NativeAA: Attempting manual poke to '` is 1 or more.
7. `ssl=0`, and `gap "$W" 'AutomationMarker: C1-cmd' 'SSL handshake complete'` is 120000 or less.
8. `rend "$W"` is 1 or more.
9. In `PW`, `Creating rfcomm socket for device: ` is 1 or more.
10. In `W`, `Boot WakeLock acquired (10min timeout)` and `AapService: WiFi button on the Bluetooth
    module route: ` are each 0.

**If condition 7 fails:** apply the C5p rule in section 3. Also record, from `W`:
`NativeAA: Calling socket.connect() for ` (0 means the wake guard stood the poke down) and every
`NativeAA: waking a phone over a hands-free link it holds is` line.

**If condition 2 fails in the window that was used** (both quiet windows broke), C1 is
INCONCLUSIVE for its cause. Grade conditions 3 to 10 anyway and say so. Run C1 once more from the
top. A second break: keep the better try and continue.

**What a PASS would look like if the change did nothing.** Without the change, `ACTION_CONNECT`
with `device_name` is the USB check. Condition 4 would be 0, condition 10 would read 1 for the
wake lock, and no session would form, because condition 2 shows that nothing else wakes the phone.

Record: `gap "$W" 'AutomationMarker: C1-cmd' 'NativeAA: Connection accepted from '`, the
`AapService: raising the projection by ` line, the `BluetoothHelper: driver candidates: ` line
from the whole run, `createGroup SUCCESS` count in `hwin C1-start C1-cmd` and in `W`, `quiet1=`,
the `C1-cmd.reply` `data=` line, and the `State changed to ` lines in `PW`.

### C3. Inside C1's session (no new setup)

Run only if C1 gave `ssl=0`. Otherwise C3 is UNTESTABLE; run `endrun` and go to C5p.

**c3.sh** (same shell as `c1.sh`)
```bash
mark C3-a; send ACTION_CONNECT --es device_name "'$POCO_NAME'" > $OUT/C3-a.reply; sleep 8
mark C3-b; send ACTION_NATIVE_AA_POKE --es device_name "'$POCO_NAME'" > $OUT/C3-b.reply; sleep 8
mark C3-c; send ACTION_CONNECT --es device_name "'$MOTO_NAME'" > $OUT/C3-c.reply; sleep 12
mark C3-done; state | tee $OUT/C3.state
endrun
```

Windows: `A=$(hwin C3-a C3-b)`, `B=$(hwin C3-b C3-c)`, `C=$(hwin C3-c C3-done)`,
`ALL=$(hwin C3-a C3-done)`, `PALL=$(pwin C3-a C3-done)`.

**PASS when all hold:**

1. `n "$A" 'AapService: connect by ' ': that phone is already connected or connecting.'` is 1, and that line holds `name "$POCO_NAME"`.
2. `n "$B" 'AutomationReceiver: ' 'ACTION_NATIVE_AA_POKE'` is 1, and `n "$B" 'AapService: connect by ' ': that phone is already connected or connecting.'` is 1.
3. `n "$C" 'AapService: connect by ' 'refused: another session is live.'` is 1, and that line holds `name "$MOTO_NAME"`.
4. In `ALL`: `NativeAA: Driver selected: `, `NativeAA: Attempting manual poke to `,
   `AapService: Received manual Native-AA poke request for ` and
   `AapTransport stopping and sending byebye (` are each 0.
5. `rend "$C"` is 1 or more (the picture runs after the last command).
6. `C3.state` reads `"connected":true`.
7. In `PALL`, `Creating rfcomm socket for device: ` is 0.

If `MOTO_NAME` was empty in PN, condition 3 and `C3-c` are UNTESTABLE; grade the rest.

### C5p. Regression and baseline: the old poke from a stopped app (about 4 min)

**c5p.sh**
```bash
RUN=C5p; PHCAP=1; th_gate; put $BASE; rd 'wifi-connection-mode|native-poke|log-level' | tee $OUT/C5p.keys
cap_start; mark C5p-start
mark C5p-cmd; send ACTION_NATIVE_AA_POKE --es extra_mac $POCO_BT > $OUT/C5p-cmd.reply
waitfor 120 'SSL handshake complete'; ssl=$?; echo "ssl=$ssl" | tee -a $OUT/C5p.summary
sleep 20; mark C5p-session
endrun
W=$(hwin C5p-cmd C5p-session)
```

**PASS when all hold:**

1. `n "$W" 'AapService: Received manual Native-AA poke request for '` is 1, and that line holds `$POCO_BT`.
2. `n "$W" 'NativeAA: Driver selected: ' 'automatic'` is 1.
3. `n "$W" 'AapService: connect by '` is 0.
4. `n "$W" 'AapService: WiFi button on the Bluetooth module route: '` is 0.

Record, as the baseline for C1, C2 and C1d: `ssl`, `rend "$W"`, `createGroup SUCCESS` count in `W`
(`G4`), and `Creating rfcomm socket for device: ` in `pwin C5p-cmd C5p-session`.

### C2. By MAC, lower case, `no_ui`, from a stopped app (about 4 min)

This is the reporter's case: a macro that fires while the app is not running.

**c2.sh**
```bash
RUN=C2; PHCAP=1; th_gate; put $BASE; rd 'wifi-connection-mode|native-poke|log-level' | tee $OUT/C2.keys
POCO_MAC_LC=$(printf '%s' "$POCO_BT" | tr 'A-F' 'a-f')
cap_start; mark C2-start
mark C2-cmd; send ACTION_CONNECT --es mac $POCO_MAC_LC --ez no_ui true > $OUT/C2-cmd.reply
waitfor 120 'SSL handshake complete'; ssl=$?; echo "ssl=$ssl" | tee -a $OUT/C2.summary
sleep 3; mark C2-raise; send ACTION_RAISE_PROJECTION > $OUT/C2-raise.reply
sleep 25; mark C2-session
endrun
W=$(hwin C2-cmd C2-raise); WR=$(hwin C2-raise C2-session); ALL=$(hwin C2-cmd C2-session)
```

**PASS when all hold:**

1. `n "$W" 'AutomationReceiver: ' 'ACTION_CONNECT'` is 1.
2. `n "$W" 'AapService: connect by ' 'resolved to '` is 1. That line holds `MAC $POCO_MAC_LC` and
   ends with `$POCO_BT` (the bonded spelling, upper case).
3. `n "$W" 'NativeAA: Driver selected: ' 'explicit'` is 1.
4. `ssl=0`.
5. `n "$W" 'AapService: Not raising the projection, a no_ui command asked for the session only'` is
   1 or more, and `n "$W" 'AapService: raising the projection by '` is 0.
6. `rend "$WR"` is 1 or more.
7. In `ALL`, `Boot WakeLock acquired (10min timeout)` is 0.
8. `createGroup SUCCESS` count in `ALL` is `G4` or less.
9. In `pwin C2-cmd C2-session`, `Creating rfcomm socket for device: ` is 1 or more.

If condition 4 fails, apply the C5p rule in section 3. If condition 6 fails while condition 5
passed, record whether `ssl` came more than 35 s before `C2-raise` (the phone's patience); that is a
script timing fault, not a FAIL: run C2 once more.

### C4 and C4c. Refusals: nothing is woken (about 5 min)

C4 holds the unknown name and the unpaired MAC in one capture on an armed, quiet stack. C4c is a
separate, cold capture.

**c4.sh**
```bash
RUN=C4; PHCAP=1; th_gate; put $BASE; rd 'wifi-connection-mode|native-poke|log-level' | tee $OUT/C4.keys
cap_start; mark C4-start
arm_quiet
mark C4-a; send ACTION_CONNECT --es device_name NoSuchPhone > $OUT/C4-a.reply; sleep 10
mark C4-b; send ACTION_CONNECT --es mac 02:11:22:33:44:55 > $OUT/C4-b.reply; sleep 10
mark C4-done
mark C4-end; clean_hu; cap_stop; th_report C4

RUN=C4c; PHCAP=1; th_gate; put $BASE int:wifi-connection-mode=0; rd 'wifi-connection-mode|log-level' | tee $OUT/C4c.keys
cap_start; mark C4c-start
mark C4c-cmd; send ACTION_CONNECT --es device_name "'$POCO_NAME'" > $OUT/C4c-cmd.reply; sleep 15
mark C4c-done
mark C4c-end; clean_hu; cap_stop; th_report C4c
```

Windows: `A=$(RUN=C4 hwin C4-a C4-b)`, `B=$(RUN=C4 hwin C4-b C4-done)`,
`AB=$(RUN=C4 hwin C4-a C4-done)`, `PAB=$(RUN=C4 pwin C4-a C4-done)`,
`C=$(RUN=C4c hwin C4c-cmd C4c-done)`, `PC=$(RUN=C4c pwin C4c-cmd C4c-done)`.

**PASS when all hold:**

1. `n "$A" 'AapService: connect by ' 'refused: ' 'no paired phone named'` is 1, and that line holds `name "NoSuchPhone"`.
2. `n "$B" 'AapService: connect by ' 'refused: ' 'not paired'` is 1, and that line holds `MAC 02:11:22:33:44:55`.
3. `n "$C" 'AapService: connect by ' 'refused: ' 'not Native AA'` is 1.
4. In `C`: `AapService: manual Native-AA poke ignored: the wireless mode is not Native AA.` is 0, and
   `n "$C" 'WifiLauncher: Initializing WiFi Mode: ' 'NATIVE'` is 0.
5. In each of `AB` and `C`: `NativeAA: Driver selected: `, `NativeAA: Attempting manual poke to `,
   `AapService: Received manual Native-AA poke request for `,
   `AapService: WiFi button on the Bluetooth module route: `,
   `Boot WakeLock acquired (10min timeout)` and `SSL handshake complete` are each 0.
6. In `PAB` and `PC`, `Creating rfcomm socket for device: ` is 0.

Condition 6 for `PAB` counts only if C4's quiet window held (`quiet1=held`, or the second window was
clean). If not, mark that half INCONCLUSIVE and say so.

Record the three `data=` replies. Each should read `"ok":true`: the refusals come from the service,
not from the policy, so they reach the log only.

### C1d. The deep link, lower-case name (about 5 min)

**c1d.sh**
```bash
RUN=C1d; PHCAP=1; th_gate; put $BASE; rd 'wifi-connection-mode|native-poke|log-level' | tee $OUT/C1d.keys
cap_start; mark C1d-start
arm_quiet
mark C1d-cmd; adb -s $DHU shell am start -a android.intent.action.VIEW -d "'headunit://connect?device_name=$POCO_URL'" > $OUT/C1d-cmd.reply
waitfor 120 'SSL handshake complete'; ssl=$?; echo "ssl=$ssl" | tee -a $OUT/C1d.summary
sleep 20; mark C1d-session
endrun
Q=$(hwin $QA C1d-go); W=$(hwin C1d-cmd C1d-session)
```

**PASS when all hold:**

1. Quiet: C1 condition 2, on `Q` and `pwin $QA C1d-go`.
2. `n "$W" 'AutomationActivity: Received intent. Action: '` is 1, and that line holds `device_name=$POCO_URL`.
3. `n "$W" 'AapService: connect by ' 'resolved to '` is 1, and that line holds `name "$POCO_LC"` and `$POCO_BT`.
4. `n "$W" 'NativeAA: Driver selected: ' 'explicit'` is 1.
5. `ssl=0` and `rend "$W"` is 1 or more.
6. In `W`, `Boot WakeLock acquired (10min timeout)` is 0.
7. In `pwin C1d-cmd C1d-session`, `Creating rfcomm socket for device: ` is 1 or more.

If condition 5 fails, apply the C5p rule in section 3.

### XN. Restore D-HU and D-MOTO

**xn_restore.sh**
```bash
adb -s $DHU shell am force-stop $PKG; sleep 0.3
adb -s $DHU push $OUT/backup-D-HU.xml /data/local/tmp/ohu-w.xml; sleep 0.3
adb -s $DHU shell "cp /data/local/tmp/ohu-w.xml $F && chown $OWN $F && chmod 660 $F"; sleep 0.3
adb -s $DHU shell cat $F | diff - $OUT/backup-D-HU.xml      # must be empty
adb -s $DHU shell dumpsys bluetooth_manager | grep -a -m1 -E 'enabled:|state:'   # must read on
```

Then set D-MOTO's radios back to `MOTO_RADIOS` from PA with `svc wifi enable` and `svc bluetooth
enable` as needed, and read both back. Leave the candidate installed on D-HU and D-POCO. Do not
restore D-HU's saved P2P groups.

## 8. Do not re-run

- **Unit tests.** 4096 JVM tests, 0 failures, at `3101712a` on the dev host.
- **L-MOTO, L-POCO, L-SAM.** Settled in round 2: `MOTO_FAKE=yes`, `LEVER_POCO=fake` with the ROM
  lighting the panel on both plug changes, `LEVER_SAM=cable`.
- **V1, R3, R4, R1, R2, R10, R6, S1, S2 of this thread.** PASS in round 2. C changes no line on their
  paths except the screen-on order and the keyguard reading, which V3, V4n and V4k grade. The two
  connect commits touch the poke and `ACTION_CONNECT` paths only, which Stage N grades.
- **T1.** PASS in round 2. The brief's condition 6 check counted wake-lock history; the rig graded it
  on the live wake-lock list, and that grade stands.
- **R5.** PASS in round 2. It runs again only as the first half of R5n/R7n and R5k/R7k.
- **R1s.** UNTESTABLE on D-SAM's cable lever, and that will not change. R1 on D-POCO and S1/S2 on
  D-SAM cover it.
- **A secure keyguard (PIN, pattern or password).** D-POCO must have no lock credential (PA), and
  C shows nothing over a secure keyguard by design: the system asks for the credential.
- **C0 on D-HU.** The `dumpsys battery` lever sends no `POWER_*` broadcast there.
- **The pure connect decisions:** ambiguous names, `NOT_A_PHONE`, `UNKNOWN` accepted, the permission
  refusal, precedence of `ip` over `mac` over `device_name`, blank values, and `extra_mac` on
  `ACTION_CONNECT`. The JVM tests settle them.
- **A wake when the phone already holds the hands-free link and the guard stands the poke down.**
  That is the driver selector's existing path, measured in earlier driver-selection rounds; this
  branch does not change it.

## 9. Report back

The numbers that decide shipping:

1. **V3 and V4n:** the whole screen-on line of each (it must name `the home screen` and `the
   projection`), the panel reading after the request, and `V4n.focus` with the `rend` count.
2. **R7n:** `ssl2`, the `rend` count after power back, `R7n.focus`, and the whole screen-on line.
3. **V4k and R7k:** the whole screen-on line of each, the dismiss lines and which activity logged
   them, the `kg` readings before and after, the focus, and the `rend` count.
4. **R11:** the hold line count and its gap after A6, the quiet window counts of the wired re-arm and
   `WifiLauncher: Initializing WiFi Mode: `, and the return gaps (`checking USB again.` to the re-arm,
   the re-arm to `Initializing`).
5. **C1:** did the named command take the wake path, and did a session follow? Conditions 4 to 8,
   the `C1-cmd` to SSL time in ms, and which quiet window was clean.
6. **C2 against C5p:** does the cold `mac` plus `no_ui` form work, and does it cost more groups than
   the old poke? The C2 and C5p `createGroup SUCCESS` counts side by side.
7. **C4 and C4c:** did every refusal stay out of the USB check and the module branch, with nothing
   woken?

Give each verdict in the fixed format of `TESTING-TEMPLATE.md` §7, and put every deviation in Setup
notes. Add to `rig-quirks/units/D-POCO.md` whether `locksettings set-disable true` removed the
keyguard after `lockNow()`, whether `requestDismissKeyguard` lifted the swipe keyguard, and how many
USB dialogs one OTG plug and one power return raised.

These strings are also grepped, but they are not in our tree. `received ByeByeRequest`, `Creating
rfcomm socket for device: `, `State changed to ` and `THROTTLE_LIMIT_EXCEEDED` are Android Auto lines
in a phone capture. `OHURIG` is our own phone marker tag. `device wake-up` is in the head unit's
`PowerManagerService` log. The last is shell output on D-POCO.

```external-strings
received ByeByeRequest
Creating rfcomm socket for device: 
State changed to 
THROTTLE_LIMIT_EXCEEDED
OHURIG
device wake-up
Lock credential verified successfully
```

The block below lists every app string the runs grep. Each one is in `app/src/main` at `3101712a`.

```decisive-strings
AutomationReceiver: 
AutomationMarker: 
WakeDetect: POWER_DISCONNECTED
WakeDetect: POWER_CONNECTED
WakeDetect: SCREEN_OFF
WakeDetect: SCREEN_ON (screen was off for 
WakeDetect: power lost beside a screen-off; treating it as the car being switched off
WakeDetect: re-arming the wireless bring-up held while the unit was asleep
WakeDetect: hibernate wake detected
AapService: external-power disconnect: power lost; ending the session in 
ms unless it comes back.
AapService: external-power disconnect: power stayed away; ending the session the way the Exit button does.
AapService: external-power disconnect: not ending the session (enabled=
AapService: external-power disconnect: power is back; re-arming the wireless bring-up.
AapService: external-power disconnect: power is back; checking USB again.
AapService: external-power disconnect: power is back while the session is still ending; the return runs once it has ended.
external-power disconnect
keeping the wireless bring-up down until something re-arms it.
: screen off 
done.
refused by the system.
: power came back while the session was ending; not standing down.
AapService: external-power disconnect: the session end never reached the stand-down in 
AapService: wired session ended by an auto-disconnect; the stand-down decides the re-arm of wireless mode
the stand-down decides the re-arm
the wired session's wireless re-arm is dropped with the stand-down.
AapService: wired session ended
re-arming wireless mode
USB session established while wireless mode
WifiLauncher: Initializing WiFi Mode: 
Requesting USB permission for
Found device already in accessory mode
Version request send failed
AapService: turning the screen on (
) by wake lock and 
the home screen
the projection
AapService: screen-on launch failed: 
(keyguard=
: screen-on asks the system to dismiss the keyguard
: the keyguard was dismissed
: the keyguard stays up (the user did not unlock)
: the system refused to dismiss the keyguard
: keyguard dismiss request failed: 
MainActivity
AapProjectionActivity
AapTransport stopping and sending byebye (
Disconnect action received.
AapService: Stopping GpsLocationService and NightModeManager since connection is disconnected
SSL handshake complete
Throughput over 
MATCH! Starting AapService
"screenOff"
power lost; ending the session in 
power stayed away;
power is back; re-arming
checking USB again.
keeping the wireless bring-up down
since connection is disconnected
automation
external power back
ACTION_CONNECT
ACTION_NATIVE_AA_POKE
AutomationActivity: Received intent. Action: 
NATIVE
NativeAA: ACTIVELY LISTENING on Android Auto UUID
AapService: connect by 
resolved to 
: that phone is already connected or connecting.
refused: another session is live.
refused: 
no paired phone named
not paired
not Native AA
BluetoothHelper: driver candidates: 
NativeAA: Driver selected: 
explicit
automatic
AapService: Received manual Native-AA poke request for 
AapService: manual Native-AA poke ignored: the wireless mode is not Native AA.
AapService: WiFi button on the Bluetooth module route: 
NativeAA: Attempting manual poke to 
NativeAA: Calling socket.connect() for 
NativeAA: waking a phone over a hands-free link it holds is
NativeAA: Attempting active poke to device: 
NativeAA: Connection accepted from 
ms: rendered=
AapService: raising the projection by 
AapService: Not raising the projection, a no_ui command asked for the session only
Boot WakeLock acquired (10min timeout)
createGroup SUCCESS
```
