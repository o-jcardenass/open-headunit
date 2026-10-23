# wifi-direct-named-connect, round 1 brief: connect to a phone by name or MAC

> **Revised 2026-10-22: the candidate was rebased onto `main` `e9377ff9`.** C moved from `c22f5c56` to
> `6a0089d6`, and its tree hash changed with it. The two commits are the same patches. Only the base and the
> translations main reworded moved. Re-read section 1 and rebuild C. No run, string or verdict rule changed.
> A copy that still names `c22f5c56` is stale.

> **Revised 2026-10-17: the candidate was rebased onto `main` `28af450d`.** C moved from `a8da0159` to
> `c22f5c56`. The two commits are the same patches; only the base moved. Re-read section 1 and run P's
> identity check against the new SHA. No run, string or verdict rule changed. A copy that still names
> `a8da0159` is stale.

Publish name on `transfer/rig-rounds`: `wifi-direct-named-connect-round1-brief.md`. Results go in
`wifi-direct-named-connect-round1-results.md`. Evidence goes in a new release
`rig-evidence-wifi-direct-named-connect` as the asset `wifi-direct-named-connect-round1-captures.zip`.

One candidate build, no baseline build. Units: D-HU (head unit), D-POCO (phone), D-MOTO (held in
airplane mode, used only as a bonded name). About 50 minutes. No hand step is expected (section 3).

| Run | What it proves | Session |
|---|---|---|
| P | build gate, APK identity, rig state, names and addresses | no |
| R7 | control: bare `ACTION_CONNECT` is still the USB check, and the USB-check instrument works | no |
| **R1** | **the point of the round: `ACTION_CONNECT --es device_name` on an armed, quiet stack wakes that phone and a session forms** | yes |
| R2 | inside R1's session: the same name says "already connected", the poke verb takes the name, another phone's name is refused, the session survives | (R1's) |
| R4 | regression and baseline: the old `ACTION_NATIVE_AA_POKE --es extra_mac` from a stopped app | yes |
| R3 | `ACTION_CONNECT --es mac <lower case> --ez no_ui true` from a stopped app: the reporter's macro, cold | yes |
| R5 | refusals: unknown name, unpaired MAC, mode not Native AA; nothing is woken | no |
| R6 | the deep link `headunit://connect?device_name=` with a lower-case name | yes |
| X | restore | no |

## 1. Build and baseline

| Arm | Ref | SHA | `commit` in `ACTION_QUERY_STATE` |
|---|---|---|---|
| **C** (candidate) | `fork/feat/wifi-direct-named-connect` | `6a0089d61daf60676c3bd4fde36078d070fd2116` | starts `6a0089d6` (a `-dirty` suffix is allowed) |

This is the first round of the thread. C is `main` at `e9377ff9` plus
`2151721d` (Native AA: wake a phone that a command names by Bluetooth name or address) and
`6a0089d6` (Automation: ACTION_CONNECT and the poke take a phone by name or MAC). No baseline APK:
the feature does not exist on `main`, and R4 and R7 are the controls on the candidate.

```bash
git fetch fork feat/wifi-direct-named-connect
git rev-parse fork/feat/wifi-direct-named-connect          # must print 6a0089d61daf60676c3bd4fde36078d070fd2116
git rev-parse fork/feat/wifi-direct-named-connect^{tree}   # must print 082721134d14786c064cef237330a54ea25b8692
git checkout -B wdnc-r1 6a0089d61daf60676c3bd4fde36078d070fd2116
```

If either line differs, stop the round.

- Build with `build_hur.sh` (`MAX_WORKERS=2`, host below 70C). Copy the APK at once to
  `$OUT/apk/C.apk` and record its md5.
- The rig runs no unit tests. The candidate's 3522 JVM tests passed on the dev host at this SHA. The
  rig checks APK identity only. **A build failure stops the round.**
- If `import-settings-unsaved-changes` runs in the same session, build this candidate right after it
  in the same worktree (both sit on `e9377ff9`, so the build cache is warm), and copy the APK out at
  once.
- DEX identity symbol: `unzip -p $OUT/apk/C.apk 'classes*.dex' | strings | grep -acF ConnectByNameGate`
  must print 1 or more.
- Install in P, step 8.

## 2. What this is and why

A user runs Native AA on WiFi Direct. The user wants one automation command (Tasker, a shortcut)
that says "connect to my phone". Today `ACTION_CONNECT` has two meanings. With `ip` it opens a
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

27 new JVM tests cover the policy, the resolver and the gate (3268 in all). They cannot cover the
bonded-list read on a real adapter, the alias read, the session reads in the service, the early
return, the deep-link query parsing in `AutomationActivity`, or a real wake. This round covers those.

## 3. What is different about this round

- **The phone's radios stay up between runs.** This is a deliberate deviation from §4 step 1. The
  control in R1, R5 and R6 is a 30 s quiet window on an armed stack before the command (`arm_quiet`,
  section 5). It shows that nothing else connects the phone in that state.
- **If the quiet window breaks, the script handles it.** The phone can reconnect by itself
  (`rig-quirks/topics/bt.md`, "The phone's own reconnect beats our poke"). If a session forms in the
  quiet window, `arm_quiet` sends `ACTION_DISCONNECT` (a user exit holds the stack down), waits 10 s
  and runs a second quiet window of 20 s. The command then goes out. The run records which window
  was clean.
- **No poke target is set, on purpose.** `native-poke-bt-macs` is empty and `native-poke-all-paired`
  is false (`tooling.md`), so the retry loop pokes nobody. A poke in a run can only come from the
  command under test.
- **The WiFi Direct group identity is not kept.** `wifi-direct-stable-identity` is false and the four
  group keys are deleted in every run, and P deletes D-HU's saved P2P groups once. A persistent group
  lets a paired phone rejoin with no poke (`units/D-HU.md`). Each session is then a first join of a
  new group, which takes longer. Allow 120 s.
- **D-MOTO stays in airplane mode for the whole round.** It is bonded to D-HU and can take the poke
  rotation (`bt.md`). R2 uses only its bonded name on D-HU, to name "another phone". X restores it.
- **Not a phone (`NOT_A_PHONE`) and an ambiguous name are not run.** The rig has no bonded non-phone
  whose class is known, and no two bonded devices share a name. `NamedPhonePolicyTest` covers both.
  R1 records the `driver candidates:` roll-up line, which shows that the verdicts reach the resolver.
- **A named connect during another phone's handshake or join is not run.** It needs two phones that
  answer at once. `ConnectByNameGateTest` covers it: that phone counts as live, so the command is refused.
- **The no-permission refusal is not run.** D-HU holds `BLUETOOTH_CONNECT`. `ConnectByNameGateTest`
  covers it.
- **A session that does not form is graded against R4.** R4 uses the old poke path, which is
  unchanged, on the same rig state. If R4 forms a session and a named run does not, the named run
  FAILs. If R4 also does not form one, the named run's session condition is INCONCLUSIVE (rig or
  phone state, shared by both paths).
- **The projection raise needs the overlay permission.** These runs never launch `MainActivity`.
  So the service raises the projection itself, by the overlay route. P confirms
  `SYSTEM_ALERT_WINDOW` is `allow` on D-HU (`topics/projection.md`).
- **Do not write the rig's audio settings.** `use-aac-audio`, `audio-latency-multiplier` and
  `audio-queue-capacity` are a deliberate worst case. Every write starts from D-HU's own backup, so
  they keep their values. Record them once in P.
- **`Critical error` lines on the phone follow every session end we cause.** Do not grade them.
- **`createGroup SUCCESS` is measured, not a discard rule, in this round.** A new group identity per
  run and a cold start can each give more than one create. R3 grades its count against R4's.
- **Expected INCONCLUSIVE:** none, unless R4 forms no session.

### Hand steps

None is expected. P batches the only possible ask into one request before R7:

| Id | When | Action | Why there is no verb |
|---|---|---|---|
| H1 | P finds `Navegadortz3` in D-POCO's bonded list, or `POCO X3 NFC` (D-POCO's address) missing from D-HU's | The operator forgets `Navegadortz3` on D-POCO, and pairs D-POCO with D-HU (`Navegadortz2`) if the bond is missing | unpairing and pairing are system dialogs (`units/D-POCO.md`, `bt.md`) |

If H1 does not fix the bond, every session run is UNTESTABLE. Run R7 and R5 only.

## 4. Settings keys

Every run writes `$BASE` plus its own keys with `put` (section 5). `put` starts from D-HU's
round-start backup, so each run carries exactly the backup plus these keys. Read every key back with
`rd` before the run's first command. A key that does not read back with its value voids the run
before it starts: write it again.

```bash
BASE="int:wifi-connection-mode=3 int:native-ap-transport=0 int:native-driver-selection-mode=0 bool:native-poke-all-paired=false set:native-poke-bt-macs= set:auto-start-bt-macs= del:last-connected-native-mac del:native-preferred-device-mac bool:auto-connect-last-session=false bool:auto-start-on-boot=false bool:auto-start-on-screen-on=false bool:auto-start-on-usb=false bool:auto-start-self-mode=false bool:kill-on-disconnect=false bool:wifi-direct-stable-identity=false del:wifi-direct-group-name del:wifi-direct-last-group-ssid del:wifi-direct-last-group-bssid del:wifi-direct-group-passphrase bool:allow-external-configuration=false int:onboarding-version=2 int:log-level=2 del:native-aa-wake-damage-verdict del:video-profile-starvation-cap"
```

| Run | Keys |
|---|---|
| R7, R1/R2, R4, R3, R5a, R5b, R6 | `$BASE` |
| R5c | `$BASE int:wifi-connection-mode=0` (MANUAL, last word wins) |

Why the less obvious keys:

- `native-driver-selection-mode=0` turns the driver selector off, so no countdown connects a phone.
- The two MAC keys are deleted so that R2's "already connected" reads the MAC that R1's handshake
  writes, not a seeded one.
- `auto-start-bt-macs` empty and the `auto-start-*` keys false keep every other starter out.
- `video-profile-starvation-cap` and `native-aa-wake-damage-verdict` are cleared because either can
  be left latched by an earlier thread (`bt.md`).
- `log-level=2` (INFO) carries every app line in section 6. `ACTION_LOG_MARKER` is not in
  `AutomationCommandPolicy.CONFIGURING` on C, so markers work with `allow-external-configuration`
  false.

## 5. Harness and verbs

Make `rig-data/rounds/wifi-direct-named-connect-round1/` and set `OUT` to it. Copy `ptr_lib.sh` from
`rig-data/rounds/automation-screen-disconnect-round1/` into `$OUT` (if it is missing, cut it from
`archive/rounds/projection-teardown-and-relays-round1-brief.md`). Use only `th_gate` and
`th_report` from it. Source `ptr_lib.sh`, then `$OUT/wdnc_lib.sh`, so the second one wins. Save
`sx.py` from `automation-screen-disconnect-round2-brief.md` section 5 into `$OUT` unchanged (it
writes `set:key=` as an empty set). List every file in Setup notes.

```bash
# wdnc_lib.sh : source after ptr_lib.sh. Needs OUT. RUN is set per run; PHCAP=1 captures D-POCO.
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

**Names with spaces need two layers of quotes.** `adb shell` joins its arguments and the device shell
splits them again. Write `--es device_name "'$POCO_NAME'"`. If the quotes are lost, `am` reports an
error and the `connect by` line is missing; fix the quoting and run that step again.

**Verbs and commands used. Nothing else acts on the app:**

| Action | Command |
|---|---|
| arm the stored mode headless | `send ACTION_START_WIRELESS` |
| the verb under test, by name | `send ACTION_CONNECT --es device_name "'<name>'"` |
| the verb under test, by MAC | `send ACTION_CONNECT --es mac <mac> [--ez no_ui true]` |
| the poke verb, by name | `send ACTION_NATIVE_AA_POKE --es device_name "'<name>'"` |
| the poke verb, old form | `send ACTION_NATIVE_AA_POKE --es extra_mac <MAC>` |
| bare connect (the USB check) | `send ACTION_CONNECT` |
| bring the projection forward | `send ACTION_RAISE_PROJECTION` |
| user exit | `send ACTION_DISCONNECT` |
| marker | `mark <label>` (labels have no spaces) |
| build identity and session state | `state` |
| deep link under test | `adb -s $DHU shell am start -a android.intent.action.VIEW -d "'headunit://connect?device_name=<name>'"` |
| hard stop | `clean_hu` |

Every `send` must give an `AutomationReceiver: ` line in the head unit capture. A step with no such
line never landed: that step is void, not a FAIL. Save each `am` output to a `.reply` file; the
reply is on its `data=` line. Never run two adb calls against one unit at the same time.

## 6. The deciding lines

Every app string below matched `grep -F -r` in `app/src/main` at `6a0089d6`. Each is INFO or WARN,
so `log-level` 2 carries it. The service builds the `connect by` lines from parts, so the runs grep
the parts with `n` and check the runtime values (name, MAC) as separate fields.

| Line or part (grep -F) | Source | Used for |
|---|---|---|
| `AutomationReceiver: ` | `AutomationReceiver` | a verb landed; with `ACTION_CONNECT` or `ACTION_NATIVE_AA_POKE` |
| `AutomationMarker: ` | `AutomationEffectRunner` | markers |
| `AutomationActivity: Received intent. Action: ` | `AutomationActivity` | R6: the deep link landed |
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
| `AapService: manual Native-AA poke ignored: the wireless mode is not Native AA.` | `AapService` | must stay 0 in R5c (the gate returns first) |
| `AapService: WiFi button on the Bluetooth module route: ` | `AapService` | must stay 0 everywhere |
| `NativeAA: Attempting manual poke to ` | `NativeAaHandshakeManager` | the explicit wake ran |
| `NativeAA: Calling socket.connect() for ` | `NativeAaHandshakeManager` | the wake reached the radio |
| `NativeAA: Attempting active poke to device: ` | `NativeAaHandshakeManager` | the retry loop poked; must stay 0 in quiet windows |
| `NativeAA: Connection accepted from ` | `NativeAaHandshakeManager` | the phone came back over Bluetooth |
| `SSL handshake complete` | `AapSslContext` | a session formed |
| `Throughput over ` | `VideoDecoder` | picture; `rend` matches `Throughput over [0-9]+ms: rendered=[1-9]` |
| `ms: rendered=` | `VideoDecoder` | part of the `rend` regex |
| `AapService: raising the projection by ` | `AapService` | the service raised the projection; record the route |
| `AapService: Not raising the projection, a no_ui command asked for the session only` | `AapService` | R3: `no_ui` held the raise |
| `Boot WakeLock acquired (10min timeout)` | `AapService` | prints on the first `ACTION_CHECK_USB` of a process: the USB check ran |
| `AapTransport stopping and sending byebye (` | `AapTransport` | must stay 0 in R2 (the session survives) |
| `createGroup SUCCESS` | `WifiDirectManager` | group count per run |
| `MATCH! Starting AapService` | `AutoStartReceiver` | discard check; must stay 0 |

Phone lines (D-POCO, Gearhead; checked in the 17.8 decompile, report the installed version):

| Line | Source | Used for |
|---|---|---|
| `Creating rfcomm socket for device: ` | `GH.WIRELESS.BT` | the phone answered a wake and dialled the head unit |
| `State changed to ` | `GH.WIRELESS.SETUP` | record: the phone's setup states |
| `THROTTLE_LIMIT_EXCEEDED` | Gearhead | phone throttle; triggers the recovery in section 7 |
| `OHURIG` | our own `log -t` tag | phone windows |

## 7. Runs

Order: P, R7, R1 with R2, R4, R3, R5, R6, X. Every run: `th_gate` first. Report `th_report $RUN` in
each section. Write the results file after every run.

**Every run is valid only when** its `$RUN.summary` reads `wrapped=no` and `MATCH! Starting
AapService` is 0 in `hwin $RUN-start $RUN-end`. A run with `wrapped=yes` is void: run it once more.
Report both values.

**Session check, used by R1, R4, R3 and R6.** After the command, `waitfor 120 'SSL handshake
complete'; ssl=$?`. Record `ssl`. If `ssl=1` and the phone capture since the command holds
`THROTTLE_LIMIT_EXCEEDED`, reboot D-POCO (P step 5), and run the run once more.

**Stop rule:** if R1 (both tries) and R4 all give `ssl=1`, skip R3 and R6, run R5, and say so in
Setup notes.

`gap` uses the time of day. Do not let a run cross midnight.

### P. Precheck, once (about 15 min)

Record every output in Setup notes. Save `sx.py` and `wdnc_lib.sh` into `$OUT`, copy `ptr_lib.sh`
in, and source `ptr_lib.sh`, then `wdnc_lib.sh`.

1. Run the build gate in section 1.
2. D-HU's shell must be root: `adb -s $DHU shell id -u` must print `0`. If not, stop the round (rig).
3. D-HU settings and owner:
   ```bash
   adb -s $DHU shell am force-stop $PKG; sleep 0.3
   adb -s $DHU shell cat $F > $OUT/backup-D-HU.xml
   adb -s $DHU shell stat -c '%U:%G %a' /data/data/$PKG/shared_prefs     # record, even when correct
   OWN=$(adb -s $DHU shell stat -c '%U:%G' $F | tr -d '\r'); echo "OWN=$OWN"
   grep -aoE '(use-aac-audio|audio-latency-multiplier|audio-queue-capacity|enable-audio-sink)[^/]*' $OUT/backup-D-HU.xml
   ```
   If the `shared_prefs` directory is not owned by the app's user, `chown` it to `$OWN` and say so.
4. D-MOTO:
   ```bash
   MOTO_BT=$(adb -s $MOTO shell settings get secure bluetooth_address | tr -d '\r'); echo "MOTO_BT=$MOTO_BT"   # round history: A0:46:5A:97:E4:95
   adb -s $MOTO shell cmd connectivity airplane-mode enable; sleep 5
   adb -s $MOTO shell dumpsys bluetooth_manager | grep -a -m1 -E 'enabled:|state:'   # must read off; if not, svc bluetooth disable and read again
   ```
5. D-POCO, a fresh start. A reboot, not a force-stop, clears Gearhead's RFCOMM throttle:
   ```bash
   adb -s $POCO shell am force-stop $PKG; sleep 0.3
   adb -s $POCO reboot; adb -s $POCO wait-for-device
   for i in $(seq 1 30); do   # poll every 2 s, cap 60 s: boot done and Android Auto (Gearhead) running
     [ "$(adb -s $POCO shell getprop sys.boot_completed | tr -d '\r')" = 1 ] && [ -n "$(adb -s $POCO shell pidof com.google.android.projection.gearhead | tr -d '\r')" ] && break; sleep 2; done; echo "boot_poll=$i"   # 30 means the cap was hit
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
   - If `POCO_NAME` is empty, or a name holds a quote character, stop and report: H1 or a rename is needed.
   - If `MOTO_NAME` is empty, R2's step c is UNTESTABLE. Run the rest.
   - If step 5 or 6 fails a bond check, ask H1 once. Then read both lists again.
7. Record D-HU's WiFi Direct device name: `adb -s $DHU shell settings get global wifi_p2p_device_name`
   and `adb -s $DHU shell dumpsys wifip2p | grep -a -m2 -i devicename`. If either reads `OpenHU`, the
   pr-1001 round did not restore it. Record that in Setup notes and go on.
   Confirm no stale processes: `adb -s $DHU shell ps -A | grep -ac headunitrevived` reads 0 after the force-stop.
8. Install and identity:
   ```bash
   adb -s $DHU install -r $OUT/apk/C.apk
   adb -s $DHU shell cat $F > $OUT/post-install-D-HU.xml
   diff $OUT/backup-D-HU.xml $OUT/post-install-D-HU.xml     # record; if it differs, push the backup back (put with no keys)
   RUN=P; state | tee $OUT/P.state                           # commit must start 6a0089d6
   adb -s $DHU shell am force-stop $PKG
   ```
   `state` starts the app. That is fine here; the force-stop follows.

**A failure in step 1, 2 or 8 stops the round.**

### R7. Control: bare `ACTION_CONNECT` is still the USB check (about 2 min)

Headless, from a stopped app. No phone capture: the verdict does not touch the phone.

```bash
RUN=R7; PHCAP=; th_gate; put $BASE; rd 'wifi-connection-mode|native-poke|log-level' | tee $OUT/R7.keys
cap_start; mark R7-start
mark R7-cmd; send ACTION_CONNECT > $OUT/R7-cmd.reply; sleep 15
mark R7-end; clean_hu; cap_stop; th_report R7
W=$(hwin R7-cmd R7-end)
```

**PASS when all hold:**

1. `n "$W" 'AutomationReceiver: ' 'ACTION_CONNECT'` is 1.
2. `n "$W" 'Boot WakeLock acquired (10min timeout)'` is 1. (This proves the instrument. R1, R3,
   R5 and R6 use its absence.)
3. `n "$W" 'AapService: connect by '` is 0.
4. `n "$W" 'AapService: Received manual Native-AA poke request for '` is 0.

Record: `n "$W" 'WifiLauncher: Initializing WiFi Mode: ' 'NATIVE'` (the cold start arms Native AA)
and `n "$W" 'NativeAA: Attempting active poke to device: '` (expected 0, no poke target).

### R1. By name on an armed, quiet stack (the point of the round, about 5 min with R2)

```bash
RUN=R1; PHCAP=1; th_gate; put $BASE; rd 'wifi-connection-mode|native-poke|native-driver|wifi-direct-stable|log-level' | tee $OUT/R1.keys
cap_start; mark R1-start
arm_quiet
mark R1-cmd; send ACTION_CONNECT --es device_name "'$POCO_NAME'" > $OUT/R1-cmd.reply
waitfor 120 'SSL handshake complete'; ssl=$?; echo "ssl=$ssl" | tee -a $OUT/R1.summary
sleep 20; mark R1-session; state | tee $OUT/R1.state
```

Then run R2 at once, in the same capture. R2 ends the run with `endrun`.

Windows: `Q=$(hwin $QA R1-go)` (the quiet window; `QA` is `R1-arm` or `R1-arm2`),
`W=$(hwin R1-cmd R1-session)`, `PW=$(pwin R1-cmd R1-session)`, `PQ=$(pwin $QA R1-go)`.

**PASS when all hold:**

1. Preflight: `n "$(hwin R1-start R1-go)" 'WifiLauncher: Initializing WiFi Mode: ' 'NATIVE'` is 1 or more, and `listening=0`.
2. Quiet: in `Q`, `SSL handshake complete`, `NativeAA: Connection accepted from `,
   `NativeAA: Attempting manual poke to ` and `NativeAA: Attempting active poke to device: ` are
   each 0. In `PQ`, `Creating rfcomm socket for device: ` is 0.
3. `n "$W" 'AutomationReceiver: ' 'ACTION_CONNECT'` is 1.
4. `n "$W" 'AapService: connect by ' 'resolved to '` is 1, and that line holds
   `name "$POCO_NAME"` and `$POCO_BT`.
5. `n "$W" 'NativeAA: Driver selected: ' 'explicit'` is 1, and that line holds `$POCO_BT`.
6. `n "$W" 'NativeAA: Attempting manual poke to '` is 1 or more.
7. `ssl=0`, and `gap "$W" 'AutomationMarker: R1-cmd' 'SSL handshake complete'` is 120000 or less.
8. `rend "$W"` is 1 or more.
9. In `PW`, `Creating rfcomm socket for device: ` is 1 or more.
10. In `W`, `Boot WakeLock acquired (10min timeout)` and `AapService: WiFi button on the Bluetooth
    module route: ` are each 0.

**If condition 7 fails:** apply the R4 rule in section 3. Also record, from `W`:
`NativeAA: Calling socket.connect() for ` (0 means the wake guard stood the poke down) and every
`NativeAA: waking a phone over a hands-free link it holds is` line.

**If condition 2 fails in the window that was used** (both quiet windows broke), R1 is
INCONCLUSIVE for its cause. Grade conditions 3 to 10 anyway and say so. Run R1 once more from the
top. A second break: keep the better try and continue.

**What a PASS would look like if the change did nothing.** Without the change, `ACTION_CONNECT`
with `device_name` is the USB check. Condition 4 would be 0, condition 10 would read 1 for the
wake lock, and no session would form, because condition 2 shows that nothing else wakes the phone.

Record: `gap "$W" 'AutomationMarker: R1-cmd' 'NativeAA: Connection accepted from '`, the
`AapService: raising the projection by ` line, the `BluetoothHelper: driver candidates: ` line
from the whole run, `createGroup SUCCESS` count in `hwin R1-start R1-cmd` and in `W`, `quiet1=`,
the `R1-cmd.reply` `data=` line, and the `State changed to ` lines in `PW`.

### R2. Inside R1's session (no new setup)

Run only if R1 gave `ssl=0`. Otherwise R2 is UNTESTABLE; run `endrun` and go to R4.

```bash
mark R2-a; send ACTION_CONNECT --es device_name "'$POCO_NAME'" > $OUT/R2-a.reply; sleep 8
mark R2-b; send ACTION_NATIVE_AA_POKE --es device_name "'$POCO_NAME'" > $OUT/R2-b.reply; sleep 8
mark R2-c; send ACTION_CONNECT --es device_name "'$MOTO_NAME'" > $OUT/R2-c.reply; sleep 12
mark R2-done; state | tee $OUT/R2.state
endrun
```

Windows: `A=$(hwin R2-a R2-b)`, `B=$(hwin R2-b R2-c)`, `C=$(hwin R2-c R2-done)`,
`ALL=$(hwin R2-a R2-done)`, `PALL=$(pwin R2-a R2-done)`.

**PASS when all hold:**

1. `n "$A" 'AapService: connect by ' ': that phone is already connected or connecting.'` is 1, and that line holds `name "$POCO_NAME"`.
2. `n "$B" 'AutomationReceiver: ' 'ACTION_NATIVE_AA_POKE'` is 1, and `n "$B" 'AapService: connect by ' ': that phone is already connected or connecting.'` is 1.
3. `n "$C" 'AapService: connect by ' 'refused: another session is live.'` is 1, and that line holds `name "$MOTO_NAME"`.
4. In `ALL`: `NativeAA: Driver selected: `, `NativeAA: Attempting manual poke to `,
   `AapService: Received manual Native-AA poke request for ` and
   `AapTransport stopping and sending byebye (` are each 0.
5. `rend "$C"` is 1 or more (the picture runs after the last command).
6. `R2.state` reads `"connected":true`.
7. In `PALL`, `Creating rfcomm socket for device: ` is 0.

If `MOTO_NAME` was empty in P, conditions 3 and `R2-c` are UNTESTABLE; grade the rest.

### R4. Regression and baseline: the old poke from a stopped app (about 4 min)

```bash
RUN=R4; PHCAP=1; th_gate; put $BASE; rd 'wifi-connection-mode|native-poke|log-level' | tee $OUT/R4.keys
cap_start; mark R4-start
mark R4-cmd; send ACTION_NATIVE_AA_POKE --es extra_mac $POCO_BT > $OUT/R4-cmd.reply
waitfor 120 'SSL handshake complete'; ssl=$?; echo "ssl=$ssl" | tee -a $OUT/R4.summary
sleep 20; mark R4-session
endrun
W=$(hwin R4-cmd R4-session)
```

**PASS when all hold:**

1. `n "$W" 'AapService: Received manual Native-AA poke request for '` is 1, and that line holds `$POCO_BT`.
2. `n "$W" 'NativeAA: Driver selected: ' 'automatic'` is 1.
3. `n "$W" 'AapService: connect by '` is 0.
4. `n "$W" 'AapService: WiFi button on the Bluetooth module route: '` is 0.

Record, as the baseline for R1, R3 and R6: `ssl`, `rend "$W"`, `createGroup SUCCESS` count in `W`
(`G4`), and `Creating rfcomm socket for device: ` in `pwin R4-cmd R4-session`.

### R3. By MAC, lower case, `no_ui`, from a stopped app (about 4 min)

This is the reporter's case: a macro that fires while the app is not running.

```bash
RUN=R3; PHCAP=1; th_gate; put $BASE; rd 'wifi-connection-mode|native-poke|log-level' | tee $OUT/R3.keys
POCO_MAC_LC=$(printf '%s' "$POCO_BT" | tr 'A-F' 'a-f')
cap_start; mark R3-start
mark R3-cmd; send ACTION_CONNECT --es mac $POCO_MAC_LC --ez no_ui true > $OUT/R3-cmd.reply
waitfor 120 'SSL handshake complete'; ssl=$?; echo "ssl=$ssl" | tee -a $OUT/R3.summary
sleep 3; mark R3-raise; send ACTION_RAISE_PROJECTION > $OUT/R3-raise.reply
sleep 25; mark R3-session
endrun
W=$(hwin R3-cmd R3-raise); WR=$(hwin R3-raise R3-session); ALL=$(hwin R3-cmd R3-session)
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
9. In `pwin R3-cmd R3-session`, `Creating rfcomm socket for device: ` is 1 or more.

If condition 4 fails, apply the R4 rule in section 3. If condition 6 fails while condition 5
passed, record whether `ssl` came more than 35 s before `R3-raise` (the phone's patience); that is a
script timing fault, not a FAIL: run R3 once more.

### R5. Refusals: nothing is woken (about 5 min)

R5a and R5b share one capture on an armed, quiet stack. R5c is a separate, cold capture.

```bash
RUN=R5; PHCAP=1; th_gate; put $BASE; rd 'wifi-connection-mode|native-poke|log-level' | tee $OUT/R5.keys
cap_start; mark R5-start
arm_quiet
mark R5-a; send ACTION_CONNECT --es device_name NoSuchPhone > $OUT/R5-a.reply; sleep 10
mark R5-b; send ACTION_CONNECT --es mac 02:11:22:33:44:55 > $OUT/R5-b.reply; sleep 10
mark R5-done
mark R5-end; clean_hu; cap_stop; th_report R5

RUN=R5c; PHCAP=1; th_gate; put $BASE int:wifi-connection-mode=0; rd 'wifi-connection-mode|log-level' | tee $OUT/R5c.keys
cap_start; mark R5c-start
mark R5c-cmd; send ACTION_CONNECT --es device_name "'$POCO_NAME'" > $OUT/R5c-cmd.reply; sleep 15
mark R5c-done
mark R5c-end; clean_hu; cap_stop; th_report R5c
```

Windows: `A=$(RUN=R5 hwin R5-a R5-b)`, `B=$(RUN=R5 hwin R5-b R5-done)`,
`AB=$(RUN=R5 hwin R5-a R5-done)`, `PAB=$(RUN=R5 pwin R5-a R5-done)`,
`C=$(RUN=R5c hwin R5c-cmd R5c-done)`, `PC=$(RUN=R5c pwin R5c-cmd R5c-done)`.

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

Condition 6 for `PAB` counts only if R5's quiet window held (`quiet1=held`, or the second window was
clean). If not, mark that half INCONCLUSIVE and say so.

Record the three `data=` replies. Each should read `"ok":true`: the refusals come from the service,
not from the policy, so they reach the log only.

### R6. The deep link, lower-case name (about 5 min)

```bash
RUN=R6; PHCAP=1; th_gate; put $BASE; rd 'wifi-connection-mode|native-poke|log-level' | tee $OUT/R6.keys
cap_start; mark R6-start
arm_quiet
mark R6-cmd; adb -s $DHU shell am start -a android.intent.action.VIEW -d "'headunit://connect?device_name=$POCO_URL'" > $OUT/R6-cmd.reply
waitfor 120 'SSL handshake complete'; ssl=$?; echo "ssl=$ssl" | tee -a $OUT/R6.summary
sleep 20; mark R6-session
endrun
Q=$(hwin $QA R6-go); W=$(hwin R6-cmd R6-session)
```

**PASS when all hold:**

1. Quiet: R1 condition 2, on `Q` and `pwin $QA R6-go`.
2. `n "$W" 'AutomationActivity: Received intent. Action: '` is 1, and that line holds `device_name=$POCO_URL`.
3. `n "$W" 'AapService: connect by ' 'resolved to '` is 1, and that line holds `name "$POCO_LC"` and `$POCO_BT`.
4. `n "$W" 'NativeAA: Driver selected: ' 'explicit'` is 1.
5. `ssl=0` and `rend "$W"` is 1 or more.
6. In `W`, `Boot WakeLock acquired (10min timeout)` is 0.
7. In `pwin R6-cmd R6-session`, `Creating rfcomm socket for device: ` is 1 or more.

If condition 5 fails, apply the R4 rule in section 3.

### X. Restore

```bash
adb -s $DHU shell am force-stop $PKG; sleep 0.3
adb -s $DHU push $OUT/backup-D-HU.xml /data/local/tmp/ohu-w.xml; sleep 0.3
adb -s $DHU shell "cp /data/local/tmp/ohu-w.xml $F && chown $OWN $F && chmod 660 $F"; sleep 0.3
adb -s $DHU shell cat $F | diff - $OUT/backup-D-HU.xml      # must be empty
adb -s $MOTO shell cmd connectivity airplane-mode disable; sleep 5
adb -s $MOTO shell svc bluetooth enable; sleep 0.3; adb -s $MOTO shell svc wifi enable
adb -s $MOTO shell dumpsys bluetooth_manager | grep -a -m1 -E 'enabled:|state:'
```

Leave the candidate installed. Do not restore D-HU's saved P2P groups.

## 8. Do not re-run

- The pure decisions: ambiguous names, `NOT_A_PHONE`, `UNKNOWN` accepted, the permission refusal,
  precedence of `ip` over `mac` over `device_name`, blank values, and `extra_mac` on
  `ACTION_CONNECT`. The 27 JVM tests settle them.
- A wake when the phone already holds the hands-free link and the guard stands the poke down. That
  is the driver selector's existing path, measured in earlier driver-selection rounds; this branch
  does not change it.

## 9. Report back

1. **R1: did the named command take the wake path, and did a session follow?** Report conditions 4
   to 8, the `R1-cmd` to SSL time in ms, and which quiet window was clean.
2. **R3: does the cold `mac` + `no_ui` form work, and does it cost more groups than the old poke?**
   Report the R3 and R4 `createGroup SUCCESS` counts side by side.
