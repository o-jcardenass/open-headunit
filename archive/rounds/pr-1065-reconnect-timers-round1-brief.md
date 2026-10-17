# reconnect-timers, round 1 brief: one automatic reconnect timer per service, and the USB retry it can drop

This round tests an outside contributor's change to the three automatic reconnect timers in `AapService.onDisconnected`. It needs no other round first.

## 1. Build and baseline

Two APKs. Build each with `build_hur.sh` and copy it out of `apks/` the moment it is built, because the script deletes the previous APK (template section 7a, tooling). Name each file by its SHA or tree.

| Arm | Code | Identity | Gate |
|---|---|---|---|
| **B** (baseline) | `main` | commit `145a0c762f0a87386ca63b54518ea5e861b290be` | `run_unit_tests.sh`: 0 failures; record the count |
| **C** (candidate) | the PR head `096f57daebb62dba899b9d2b7631b8285cb09f3f` merged onto B, locally | tree `26758ff3181d78185f8883f40b593bb2504ec980` | 0 failures, and exactly 3 tests more than B (`AutomaticReconnectTest`) |

```bash
git fetch https://github.com/emotionbug/open-headunit.git codex/handshake-retry-pacing
git checkout -B arm-B 145a0c762f0a87386ca63b54518ea5e861b290be
git checkout -B arm-C 145a0c762f0a87386ca63b54518ea5e861b290be
git merge --no-edit 096f57daebb62dba899b9d2b7631b8285cb09f3f
git rev-parse 'HEAD^{tree}'      # MUST print 26758ff3181d78185f8883f40b593bb2504ec980
```

**If C's tree differs, do not build C, and every C run is UNTESTABLE.** Record the tree that printed. The merge commit's SHA differs on every machine because it carries a timestamp, so the tree is the identity. This merge stays local: do not push it.

**Why C is the merge and not the PR head.** The PR sits on `2ca3b1f1`, 8 commits behind `main`, and it merges onto `main` with no conflict. Two of those 8 commits touch the code this round drives: `a70ba7e2` changes how Headunit Server discovery hands its socket to `CommManager` (run H uses that path), and `4c75aba3` changes `AapService`'s start commands. With B on `main` and C as `main` plus the PR, the arms differ by the PR's one commit and nothing else, and C is the code that would ship.

### 1a. Identity gate, per arm, before any run on that arm

```bash
adb -s $DEV shell pm path $PKG                 # pull that apk to ./installed.apk, then:
unzip -p installed.apk 'classes*.dex' | strings | grep -cF '<symbol>'
```

| Symbol | B | C |
|---|---|---|
| `AutomaticReconnect` | 0 | 1 or more |
| `HeldServerSocket` | 1 or more | 1 or more |

The source trees agree: `AutomaticReconnect` is in 0 files on B and 2 on C; `HeldServerSocket` is in 4 files on both, which proves both APKs are built on `main`. Also send `ACTION_QUERY_STATE` and record `commit`: B's must start `145a0c76`; C's is the local merge commit (record it). Record both APK md5s from a real `adb pull` and a local `md5sum`; they must differ.

## 2. What this is and why it exists

When a session ends without a user exit, `AapService.scheduleReconnectIfNeeded` arms one of three timers:

- **the discovery restart**, 2 s, in the modes with a local discovery loop (Headunit Server, and Wireless Helper with NSD or a hotspot): `AapService: Disconnected. Restarting discovery loop in 2s...`;
- **the USB recheck**, 3 s (`USB_RECONNECT_DELAY_MS`), after a USB session: `AapService: USB disconnect. Scheduling reconnect check in 3000ms...`;
- **the Auto mode one-shot scan**, 2 s: `AapService: Unclean WiFi disconnect in Auto Mode. Retrying discovery in 2s...`.

On B each timer is a free coroutine that fires if nothing is connected when it wakes. So two session ends in a row leave two live timers, and an old timer can dial after a newer session connected and ended. The PR keeps one timer per service (`AutomaticReconnect`). The connection-state collector calls `cancel()` on **every** state it sees, and the timer fires only if the state is still the same `Disconnected` object that armed it.

Our review found no blocking defect and two should-fix gaps. Both are retries that run on B and are dropped on C with nothing in their place.

- **Finding 1, the point of this round: a wireless dial cancels the USB recheck.** A USB session ends unclean while the unit's wireless mode is armed. The 3 s USB timer starts. `rearmWirelessAfterWiredSession` re-arms wireless after 1.5 s, and its discovery dials at about 2 s. On B the timer wakes regardless: if the dial is still `Connecting`, `UsbLauncherManager.checkAlreadyConnected` calls `ConnectionArbiter.holdUsbCheck()` (`ConnectionArbiter: a USB check waits for ...`) and USB comes back when the dial ends; if the dial has already failed, the check finds the dongle at once. On C the dial's first state cancels the timer. When the dial fails, `onDisconnected` takes the wireless branch and returns before the USB branch, so nothing retries USB. USB stays down until a re-plug or a USB attach broadcast.
- **Finding 2: a failed attempt hidden by conflation drops the retry.** `connectionState` is a conflated `StateFlow` and `Disconnected` is a data class. If an attempt goes `Connecting` then `Disconnected()` before the collector runs, the collector sees a value equal to the last one and does nothing, and the old timer then finds a different object and does not fire. It is a race. **A short round will probably not hit it.** This brief counts the cycles where its shape appears and grades it by count.
- **Note 3: an `Error` with no `Disconnected` after it cancels the retry for good.** This round records any `AapService: session state failed` line; it does not build a run for it.
- **Note 5: the Auto mode one-shot scan does nothing on either arm** (it runs only when the launcher is inactive, and the scan then returns at once). No run measures it.

The second half of the round is that **the ordinary retries still fire** on C at the same counts as on B: the Headunit Server and Helper discovery restarts, the USB recheck with no dial in the way, and Native AA unchanged (it schedules no timer at all).

## 3. What is different about this round

### 3.1 Stages

| Stage | Plugged into the test PC | Runs |
|---|---|---|
| **U** | D-MOTO on a cable (the dongle's phone; phone capture); D-POCO as head unit and USB host on wireless adb, with the dongle on its OTG port; the host runs a fake head unit server (`fake5277.py`) | R0, U0, U2, U1 |
| **W** | D-HU (head unit) and D-POCO (phone) on cables, both on the house network | H, N, P |

**Run Stage U first: U1 is the point, and it needs an operator** for the cable moves (H1). If no operator is present at the start, run Stage W first and Stage U when one arrives. The round is not complete without U1. Round 1 of `pr-1047-session-reconnect` ran no USB stage at all, because the dongle needs an operator at the rig.

### 3.2 How the rig reaches finding 1

D-HU cannot host USB (`host_connected=false`, `rig-quirks/topics/tooling.md`), so D-POCO is the head unit and the USB host, as in the `usb-version-retry` rounds. The dongle proxies D-MOTO's Android Auto over its own radio and answers AAP only while D-MOTO is associated over Bluetooth (`usb-aoa-dongle-findings`). D-MOTO's Bluetooth stays on in this stage.

Three problems, and how the brief solves each:

- **A USB session must end unclean without a cable pull.** `send ACTION_END_SESSION_STAY_ARMED` ends a session with `isUserExit = false`, and our own disconnect is never clean, so it reaches the USB branch of `scheduleReconnectIfNeeded` exactly like a cable glitch. The dongle stays plugged in.
- **The wireless dial must come from the re-armed stack, not from a verb.** A user connect verb is the wrong lever: its claim owner stands the wireless stack down, and then its failure takes the USB branch again. So D-POCO runs Headunit Server mode (`wifi-connection-mode=1`), and the host PC runs `fake5277.py`, a TCP listener on port 5277 that accepts and closes at once. The re-armed discovery finds it (`NetworkDiscovery: Found Headunit Server on <PC>:5277`), hands the open socket to `CommManager`, and the AAP handshake fails in a few milliseconds. That is the review's "the dial fails", made on purpose. The listener is ours and no Android Auto server is involved, so the rule against touching a phone's port 5277 does not apply.
- **Why the listener closes at once and does not hold.** `CommManager.connectSocket` goes from `Connecting` to `Connected` as soon as it wraps a socket that is already open, and the AAP handshake then runs while `isConnected` is true. A listener that held the socket would keep B in `Connected` at the 3 s mark, and B's timer would skip its check too (`if (!commManager.isConnected)`), so both arms would lose USB and the run would not separate them. With an instant close, the dial has failed long before 3 s on both arms. B's timer then wakes, finds nothing connected, and checks USB. C's timer was cancelled by the dial's first state, or (if the collector missed the dial's states because they were conflated) finds a different `Disconnected` object and does not fire: the second path is finding 2. So on this route B's recovery shows as a `Found device already in accessory mode` line about 3000 ms after the scheduling line, not as `a USB check waits for`, which needs a dial still in `Connecting`.
- **The dial must land inside the 3 s.** Discovery runs a gateway scan and then a subnet sweep, so the dial can land before or after the timer. Each cycle is classified from its own timestamps (section 7), and only cycles where the `Found Headunit Server` line came less than 3000 ms after the scheduling line are graded.

**The dongle may rescue USB on its own.** After a session end it can re-enumerate, which raises a USB attach and a fresh USB attempt on both arms (`usb-version-retry` round 1 saw it after `ACTION_DISCONNECT`). A cycle where a re-enumeration line comes before USB's next SSL is reported as masked and is not graded. If every hit cycle is masked on both arms, U1 is INCONCLUSIVE ("the dongle re-enumerates after every session end, so the 3 s recheck is never the only way back on this rig"), and the mechanism half of the result (`a USB check waits for` on B, none on C) is still reported.

### 3.3 Other rig facts that change the runs

- **D-POCO (API 35) refuses `ACTION_START_WIRELESS_SCAN` right after a force-stop.** Start our app on D-POCO with `am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity`.
- **D-POCO's own Android Auto head unit server must be off in Stage U.** If it listens, D-POCO's discovery can find and dial itself. U0 checks the port table.
- **The host firewall can block a listener** (`rig-quirks/units/D-HU.md`). U0 proves D-POCO reaches `fake5277.py` before anything is graded.
- **D-POCO stalled USB enumeration during a 5 GHz WiFi Direct session** in `usb-version-retry` round 3. Stage U runs Headunit Server mode, which forms no group.
- **Every watcher reads the capture file.** A `logcat | grep` pipe never fired on this rig (`pr-1047` round 1).
- **D-POCO's Gearhead head unit server in Stage W is a hand step** (H3), as in `projection-teardown-and-relays` round 3.
- **The rig audio settings are a deliberate worst case.** Do not change them. Delete `video-profile-starvation-cap` before every run.
- **No tap on our app anywhere.**
- **Pre-registered INCONCLUSIVE outcomes:** U1 when no cycle is a graded hit in its budget, or every hit is masked; H when the phone's server cannot be brought up; P when the Wireless Helper cannot be installed or forms no session on B.

### 3.4 Hand steps (the only ones in the round)

| Id | Step | Reason no verb exists |
|---|---|---|
| H1 | Move D-POCO from its PC cable to wireless adb and plug the dongle into its OTG port; at the end of Stage U, the reverse. Also any dongle re-plug the recovery step asks for. | A cable is hardware. |
| H2 | Allow the system USB permission dialog on D-POCO with "always", only if one appears | It is a system dialog, not our app. Record `dumpsys usb` before and after. |
| H3 | Start or stop Android Auto's head unit server in its developer settings on D-POCO | No adb command starts it (`projection-teardown-and-relays` round 3). |
| H4 | Open TCP port 5277 on the host firewall, only if U0 shows the listener is unreachable | Host configuration. |
| H5 | Only for run P, and only if `exec-in` cannot write the helper's settings: pick "Common WiFi (NSD)" under Connection Mode in the helper | The helper has no settings intent. |

The cue for each is a line on the executor's console that starts `OPERATOR:`, a terminal bell, and a timestamped line in `$OUT/hand-steps.log`. The executor then waits on a log line, never on a clock.

## 4. Settings keys

Write with the app stopped. D-HU: `hu_put` (root). D-POCO: `pocoput` (section 6). Read back before every launch. Record the rig audio keys as found; do not write them.

| Key | Type | U (D-POCO) | H (D-HU) | N (D-HU) | P (D-HU) |
|---|---|---|---|---|---|
| `wifi-connection-mode` | int | `1` | `1` | `3` | `2` |
| `helper-connection-strategy` | int | | | | `0` (common WiFi, NSD) |
| `log-level` | int | `2` | `2` | `2` | `2` |
| `onboarding-version` | int | `2` | `2` | `2` | `2` |
| `kill-on-disconnect` | boolean | `false` | `false` | `false` | `false` |
| `auto-connect-last-session` | boolean | `true` (the USB timer needs it) | | | |
| `auto-start-on-usb` | boolean | `true` | | | |
| `reopen-on-reconnection` | boolean | `true` | | | |
| `use-libusb` | boolean | `false` | | | |
| `connection-modes` | string set | `usb,wifi` | | | |
| `video-profile-starvation-cap`, `native-aa-wireless`, `wifi-launcher-mode` | | delete | delete | delete | delete |

Name the D-POCO backup `settings_backup_pr1065.xml` on the host side and never `*.bak` on the device: SharedPreferences treats a `.bak` as an aborted write.

## 5. The lines that decide the runs

Each was checked with `grep -rF` against `app/src/main` of B (`145a0c76`) and of C's merged tree. All are on both arms and all print at INFO. `AapService: session state <state>` is composed at run time from `connecting`, `connected`, `failed` or `disconnected (<reason>)`.

| Line (fixed substring) | File | Meaning |
|---|---|---|
| `AutomationReceiver: ` | unit | a verb landed, with its full action |
| `AutomationMarker: ` | unit | step brackets |
| `AapService: ACTION_END_SESSION_STAY_ARMED received` | unit | **the session end landed** (cycle anchor) |
| `AapService: USB disconnect. Scheduling reconnect check in ` | unit | **the USB timer was armed** (prints before `schedule()` on C, so it does not prove the timer survived) |
| `AapService: Disconnected. Restarting discovery loop in 2s...` | unit | the discovery timer was armed (same caveat) |
| `AapService: Unclean WiFi disconnect in Auto Mode. Retrying discovery in 2s...` | unit | the Auto one-shot timer was armed |
| `re-arming wireless mode ` | unit | the wired-session re-arm; the line also carries text this brief does not quote |
| `stopping the wireless stack for the duration of it` | unit | the wired-session quiesce at SSL (the stack was armed) |
| `AapService: session state ` | unit | `connecting` is the emission that cancels C's timer |
| `NetworkDiscovery: Found Headunit Server on ` | unit | discovery found a 5277 listener |
| `NetworkDiscovery: Starting scan...`, `NetworkDiscovery: in-flight scan promoted to continuous`, `AapService: Discovery not started ` | unit | **a discovery restart ran** (any one of the three) |
| `NetworkDiscovery: Found Wifi Launcher on ` | unit | P: the helper answered on 5289 |
| `ConnectionArbiter: a USB check waits for ` | unit | **B's recheck met the dial and was held** |
| `ConnectionArbiter: ` with ` ended with no session; giving back ` | unit | the arbiter gave something back; finding 1 needs one naming `USB` |
| `Found device already in accessory mode`, `Found known USB device with permission: ` | unit | **a USB check ran and found the dongle** |
| `Switching USB device to accessory mode` | unit | a USB attach started a switch (re-enumeration) |
| `stood the wireless stack down until it ends`, ` preempts `, ` refused while ` | unit | arbiter decisions |
| `AapService: Native AA session ended; keeping the ` | unit | N: the Native session end kept the network |
| `NativeAA: Attempting active poke to device` | unit | N: the wake |
| `SSL handshake complete` | unit | a session formed (INFO form; never prefix `Handshake:`) |
| `Throughput over ` with `rendered=` | unit | video reached the decoder |
| `MATCH! Starting AapService`, `createGroup SUCCESS`, `AapRead: Magic Garbage detected in header` | unit | discard rules |
| `Added device UsbDevice[` | unit (system) | the platform saw a USB device arrive (template section 7b; not in our source) |
| `Critical error` | phone (system) | Android Auto ended a session with a critical error (Gearhead's `CAR.SERVICE` line) |
| `Network server running on port` | phone (system) | Gearhead's head unit server started (H3 proof; `projection-teardown-and-relays` round 3) |
| `AA is now flowing through proxy` | phone (helper) | P: Android Auto on the phone carries the session |
| `FATAL EXCEPTION` | both (system) | a crash |

## 6. Shell setup and every action as a verb

Make the folder `hur-wifi-test-scripts/pr-1065-reconnect-timers-round1/`. Copy `ohu_lib.sh`, `ohu_setkeys.py`, `ohu_returns.py` and `ptr_lib.sh` into it from `hur-wifi-test-scripts/projection-teardown-and-relays-round3/` (`ohu_returns.py` from `hur-wifi-test-scripts/pr-1046-round1/` if it is not there; `run_close` calls it), then save `lib1065.sh` and `fake5277.py` below beside them. Source in that order. From those files this brief uses `send`, `mark`, `nl`, `waitfor`, `hu_put` (`ohu_lib.sh`) and `apk_check`, `th_report`, `cap_start`, `cap_stop`, `pcap_start`, `pcap_stop`, `clockcheck`, `listening`, `srv_restart` (`ptr_lib.sh`). The thermal rules in `rig-quirks/topics/tooling.md` apply: `apk_check` calls `th_gate` before every run. Run each stage under `flock /tmp/ohu-rig.lock`.

```bash
HU=27870808938846; PH=4f4027e9; MOTO=ZY22GC3BM4; DHU=$HU; POCO=$PH; OTHER=
OUT=~/hur-wifi-test-scripts/pr-1065-reconnect-timers-round1; mkdir -p $OUT; cd $OUT
source ./ohu_lib.sh; source ./ptr_lib.sh; source ./lib1065.sh
ARM=B          # set to C after the candidate is installed on the unit
```

**`lib1065.sh`**:

```bash
# lib1065.sh : source after ohu_lib.sh and ptr_lib.sh. Needs HU, PH, OUT, CAP, PCAP.
# ms <HH:MM:SS.mmm> : milliseconds since midnight; NA stays NA
ms() { [ "$1" = NA ] && { echo NA; return; }; echo "$1" | awk -F'[:.]' '{print (($1*60+$2)*60+$3)*1000+$4}'; }
# lno <from-line> <fixed-string> : absolute line number of the first match at or after <from-line>; 0 if none or from is 0
lno() { local n; [ "$1" -gt 0 ] || { echo 0; return; }; n=$(tail -n +"$1" "$CAP" | grep -anF -m1 -- "$2" | cut -d: -f1); [ -n "$n" ] && echo $(( $1 + n - 1 )) || echo 0; }
# lnoE <from-line> <ERE> : the same with an extended regex
lnoE() { local n; [ "$1" -gt 0 ] || { echo 0; return; }; n=$(tail -n +"$1" "$CAP" | grep -anE -m1 -- "$2" | cut -d: -f1); [ -n "$n" ] && echo $(( $1 + n - 1 )) || echo 0; }
tat() { if [ "$1" -gt 0 ]; then sed -n "$1p" "$CAP" | awk '{print $2}'; else echo NA; fi; }
# dt <line-a> <line-b> : ms from line a to line b; NA if either is 0
dt() { local a b; a=$(ms "$(tat $1)"); b=$(ms "$(tat $2)"); [ "$a" = NA ] || [ "$b" = NA ] && { echo NA; return; }; echo $((b-a)); }
cnt() { sed -n "$1,$2p" "$CAP" | grep -acF -- "$3"; }
pcnt() { grep -acF -- "$1" "$PCAP"; }
cue() { printf '\a'; echo "OPERATOR: $1 NOW"; echo "$(date +%T) $1" >> "$OUT/hand-steps.log"; }
# pocoput <base.xml> <spec...> : D-POCO as head unit (not rooted). App stopped. Host-side edit, written through run-as on stdin, read back.
pocoput() { python3 ohu_setkeys.py "$1" new.xml "${@:2}" || return 1
  adb -s "$HU" exec-in "run-as $PKG sh -c 'cat > shared_prefs/settings.xml'" < new.xml
  adb -s "$HU" shell run-as $PKG cat shared_prefs/settings.xml | grep -aoE '(wifi-connection-mode|log-level|auto-connect-last-session|auto-start-on-usb|use-libusb|video-profile-starvation-cap)[^/]*'; }   # the starvation key must NOT print
# usb_live <from-line> : 0 when a USB session with video follows <from-line> (90 s for SSL, 30 s for a rendered window)
usb_live() { waitfor 90 'SSL handshake complete' "$1" && waitfor 30 'Throughput over [0-9]+ms: rendered=[1-9]' "$1"; }
# u_cycle <label> : from a live USB session, ends it with the verb, watches 60 s, appends one row to $OUT/$RUN.tsv
u_cycle() { local S E l0 lq lr ld lc lh lf lsw lad ls lg lfail dq dc conn dh df dssl hit masked
  S=$(nl); mark "$1-go"; send ACTION_END_SESSION_STAY_ARMED >/dev/null; sleep 60; mark "$1-end"; E=$(nl)
  l0=$(lno $S 'AapService: ACTION_END_SESSION_STAY_ARMED received')
  lq=$(lno $S 'AapService: USB disconnect. Scheduling reconnect check in ')
  lr=$(lno $S 're-arming wireless mode ')
  ld=$(lno $lq 'NetworkDiscovery: Found Headunit Server on '); lc=$(lno $lq 'AapService: session state connecting')
  lh=$(lno $lq 'ConnectionArbiter: a USB check waits for ')
  lf=$(lnoE $lq 'Found device already in accessory mode|Found known USB device with permission: ')
  lsw=$(lno $S 'Switching USB device to accessory mode'); lad=$(lno $S 'Added device UsbDevice[')
  ls=$(lno $l0 'SSL handshake complete'); lfail=$(cnt $S $E 'AapService: session state failed')
  lg=$(sed -n "$S,${E}p" "$CAP" | grep -aF ' ended with no session; giving back ' | grep -acF 'USB')
  dq=$(dt $lq $ld); dc=$(dt $lq $lc); dh=$(dt $lq $lh); df=$(dt $lq $lf); dssl=$(dt $l0 $ls)
  hit=0; [ "$dq" != NA ] && [ "$dq" -lt 3000 ] && hit=1
  conn=0; [ "$dc" != NA ] && [ "$dc" -lt 3000 ] && conn=1
  masked=0; for x in $lsw $lad; do [ "$x" -gt 0 ] && { [ "$ls" -eq 0 ] || [ "$x" -lt "$ls" ]; } && masked=1; done
  echo -e "$1\t$( [ $lr -gt 0 ] && echo 1 || echo 0)\t$dq\t$hit\t$conn\t$dh\t$df\t$lg\t$masked\t$dssl\t$lfail\t$(cnt $S $E 'NetworkDiscovery: Found Headunit Server on ')\t$(cnt $S $E 'FATAL EXCEPTION')" | tee -a "$OUT/$RUN.tsv"
  LAST_SSL=$ls; LAST_HIT=$hit; LAST_MASKED=$masked; }
# u_restore <label> : if the cycle left USB down, ask for it as the user, then by hand. Appends the route to $OUT/$RUN.tsv
u_restore() { local L; [ "$LAST_SSL" -gt 0 ] && return 0
  L=$(nl); send ACTION_CHECK_USB >/dev/null; usb_live "$L" && { echo -e "$1\trestored-by=ACTION_CHECK_USB" | tee -a "$OUT/$RUN.tsv"; return 0; }
  cue "H1: unplug the dongle from D-POCO, wait 5 s, plug it back"; L=$(nl)
  usb_live "$L" && { echo -e "$1\trestored-by=replug" | tee -a "$OUT/$RUN.tsv"; return 0; }
  echo -e "$1\trestored-by=NONE" | tee -a "$OUT/$RUN.tsv"; return 1; }
# w_cycle <label> <ssl-wait-s> : from a live wireless session, ends it with the verb, waits for the next SSL. One row to $OUT/$RUN.tsv
w_cycle() { local S E lq lc lf ls ok fired dc
  S=$(nl); mark "$1-go"; send ACTION_END_SESSION_STAY_ARMED >/dev/null
  if waitfor "$2" 'SSL handshake complete' "$S"; then ok=1; else ok=0; fi; sleep 2; mark "$1-end"; E=$(nl)
  lq=$(lno $S 'AapService: Disconnected. Restarting discovery loop in 2s...')
  lc=$(lno $lq 'AapService: session state connecting'); dc=$(dt $lq $lc)
  lf=$(lnoE $lq 'NetworkDiscovery: Starting scan\.\.\.|NetworkDiscovery: in-flight scan promoted to continuous|AapService: Discovery not started ')
  fired=0; f=$(dt $lq $lf); [ "$f" != NA ] && [ "$f" -ge 1800 ] && [ "$f" -le 3500 ] && fired=1
  ls=$(lno $S 'SSL handshake complete')
  echo -e "$1\t$ok\t$(dt $S $ls)\t$( [ $lq -gt 0 ] && echo 1 || echo 0)\t$dc\t$fired\t$(cnt $S $E 'AapService: USB disconnect. Scheduling reconnect check in ')\t$(cnt $S $E 'AapService: Unclean WiFi disconnect in Auto Mode')\t$(cnt $S $E 'AapService: Native AA session ended; keeping the ')\t$(cnt $S $E 'AapService: session state failed')\t$(cnt $S $E 'FATAL EXCEPTION')" | tee -a "$OUT/$RUN.tsv"
  LAST_OK=$ok; LAST_DIAL=$lc; LAST_FIRED=$fired; }
```

**`fake5277.py`**:

```python
#!/usr/bin/env python3
"""fake5277.py [hold_s] : listens on TCP 0.0.0.0:5277. Each connection is accepted, sent nothing, held hold_s
seconds (default 0) and closed. One line per accept and close, host epoch ms. A stand-in for a head unit
server whose AAP exchange fails, so the head unit's own discovery dials it and fails."""
import socket, sys, threading, time
hold = float(sys.argv[1]) if len(sys.argv) > 1 else 0.0
srv = socket.socket(); srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
srv.bind(("0.0.0.0", 5277)); srv.listen(8)
def serve(c, a):
    print("%d accept %s:%d" % (time.time() * 1000, a[0], a[1]), flush=True)
    time.sleep(hold); c.close()
    print("%d close %s:%d" % (time.time() * 1000, a[0], a[1]), flush=True)
while True:
    c, a = srv.accept(); threading.Thread(target=serve, args=(c, a), daemon=True).start()
```

Start it with `python3 -I fake5277.py 0 >> $OUT/fake5277.log 2>&1 & FAKEPID=$!`; stop it with `kill $FAKEPID`. Use `0` in every run of this round (section 3.2 says why).

Every action in this round:

| Action | Command | Notes |
|---|---|---|
| End a session, keep the stack (not a user exit) | `send ACTION_END_SESSION_STAY_ARMED` | the lever of every cycle |
| The USB button | `send ACTION_CHECK_USB` | only in `u_restore`; it lifts holds as a user request |
| The WiFi button | `send ACTION_START_WIRELESS_SCAN` | D-HU only (D-POCO refuses it after a force-stop) |
| Stop the service | `send ACTION_EXIT` | only to a running app |
| Mark a step | `mark <label>` | |
| Read the build | `send ACTION_QUERY_STATE` | |
| Launch our app on D-POCO | `adb -s $HU shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity` | not a verb; template section 3 |

Every `send` must print `AutomationReceiver: <action>` in the capture. A cycle whose verb line is missing is void, not a FAIL.

## 7. Runs

**The point of the round is U1.** U2 is its control. H, N and P are the "ordinary retries unchanged" gates.

**Per-run rules.** Run each run on B, then install C with `adb install -r` and run it again on the same unit. Before each arm: the identity gate (1a) and a settings read-back. The discard rules of template section 4 apply per cycle: `MATCH! Starting AapService`, `AapRead: Magic Garbage detected in header`, or (in N) a second `createGroup SUCCESS` inside one cycle voids that cycle. Every count is over the cycle's own lines.

### R0 Gate (once, both arms)

PASS needs: both APK md5s recorded and different; C's tree as stated; the 1a table matching; `commit` recorded; `run_unit_tests.sh` with 0 failures on both arms from the JUnit XML and C's count exactly B's plus 3 (a failure stops the round for that arm); `adb install -r` succeeding; the settings backup diffed against the file after each install, with the delta stated; D-POCO's and D-MOTO's Gearhead versionName.

### U0 Stage U setup and gate (once, before U2)

```bash
adb -s $POCO tcpip 5555; sleep 3
POCO_IP=$(adb -s $POCO shell ip -4 addr show wlan0 | awk '/inet /{print $2}' | cut -d/ -f1)
adb connect $POCO_IP:5555; HU=$POCO_IP:5555; PH=$MOTO; PUT=pocoput; BASEXML=$OUT/settings_backup_pr1065.xml
cue "H1: unplug D-POCO from the PC cable; do not plug the dongle yet"
adb -s $HU shell getprop ro.product.model                          # must answer over wireless adb
adb -s $HU shell am force-stop $PKG; adb -s $HU shell run-as $PKG cat shared_prefs/settings.xml > $BASEXML
PCIP=$(ip -4 route get $POCO_IP | awk '{for(i=1;i<=NF;i++) if($i=="src") print $(i+1)}'); echo "host $PCIP"
adb -s $HU shell cat /proc/net/tcp /proc/net/tcp6 | grep -aiE ':149D [0-9A-F]+:0000 0A' && echo "D-POCO SERVES 5277"
adb -s $MOTO shell dumpsys bluetooth_manager | grep -a -m2 -iE '^ *(enabled|state):'     # must read enabled / ON
adb -s $HU shell dumpsys usb | grep -i -A4 headunitrevived > $OUT/dumpsys-usb-before.txt
UKEYS="int:wifi-connection-mode=1 int:log-level=2 int:onboarding-version=2 bool:kill-on-disconnect=false bool:auto-connect-last-session=true bool:auto-start-on-usb=true bool:reopen-on-reconnection=true bool:use-libusb=false set:connection-modes=usb,wifi del:video-profile-starvation-cap del:native-aa-wireless del:wifi-launcher-mode"
```

1. If the port table printed `D-POCO SERVES 5277`, use H3 to stop D-POCO's head unit server and read the table again; it must print nothing.
2. **Listener reachable:** `RUN=U0-$ARM; CAP=$OUT/$RUN.logcat; apk_check; $PUT $BASEXML $UKEYS; cap_start; pcap_start $RUN`. Start `fake5277.py`. Launch our app. Wait 90 s for `NetworkDiscovery: Found Headunit Server on $PCIP:5277`, and check that `$OUT/fake5277.log` has an `accept $POCO_IP` line. Neither: H4, then try once more. Still neither: Stage U is UNTESTABLE ("the head unit cannot reach the host listener"). Record the time from the launch to the Found line. Stop `fake5277.py`.
3. **USB live:** `L=$(nl); cue "H1: plug the dongle into D-POCO's OTG port"; usb_live $L`. Then check that `stopping the wireless stack for the duration of it` is after `L`: the quiesce proves the wireless stack was armed when USB formed. If a USB permission dialog shows (`dumpsys activity activities | grep -a UsbPermissionActivity`), use H2.

PASS: both checks, on each arm (the listener check repeats per arm because the APK changed). The dongle stays plugged until the end of Stage U. Between arms: `send ACTION_EXIT; sleep 3; adb -s $HU shell am force-stop $PKG`, install the other APK, `$PUT $BASEXML $UKEYS`, launch, and wait for `usb_live`.

### U2 The USB recheck with no dial in the way (control, 3 cycles per arm)

`fake5277.py` is **stopped**, so the re-armed discovery has nothing to dial.

```bash
RUN=U2-$ARM; CAP=$OUT/$RUN.logcat; apk_check; pcap_start $RUN; cap_start
L=$(nl); adb -s $HU shell am force-stop $PKG; $PUT $BASEXML $UKEYS; adb -s $HU shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity >/dev/null
usb_live $L || echo "U2 no USB session at start"
for c in 1 2 3; do u_cycle U2-$c; u_restore U2-$c; done
mark U2-end; pcap_stop; cap_stop; th_report $RUN
```

The `u_cycle` columns are: label, `rearm` (the wired-session re-arm printed, else the cycle is void), `dq` (ms from the scheduling line to the first `Found Headunit Server` line, the dial), `hit` (`dq` under 3000), `conn` (a `session state connecting` line under 3000 ms after the scheduling line: the collector saw the dial), `dh` (to `a USB check waits for`), `df` (to the first `Found device ...` line), `gb` (give-back lines naming USB), `masked`, `dssl` (ms from the end verb to the next SSL), `failed`, `found` (listener Found lines), `fatal`.

**PASS (each arm):** on every non-void cycle: `dq` is NA (nothing dialled); `df` between 2500 and 4500 ms (the 3 s recheck ran and found the dongle) or `masked=1`; `dssl` of 20000 ms or less; `fatal` 0. Phone: the D-MOTO capture has 0 `Critical error` lines in the run. **C must match B:** the count of cycles with `df` in range is the same on both arms. A C cycle with `df` NA and `masked=0` is a FAIL: the plain USB recheck was lost with no dial at all.

### U1 A wireless dial inside the 3 s window (the point of the round)

`fake5277.py` is **running**, so the re-armed discovery dials it and fails.

```bash
python3 -I fake5277.py 0 >> $OUT/fake5277.log 2>&1 & FAKEPID=$!
RUN=U1-$ARM; CAP=$OUT/$RUN.logcat; apk_check; pcap_start $RUN; cap_start
L=$(nl); adb -s $HU shell am force-stop $PKG; $PUT $BASEXML $UKEYS; adb -s $HU shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity >/dev/null
usb_live $L || echo "U1 no USB session at start"
graded=0
for c in $(seq 1 12); do
  u_cycle U1-$c
  [ "$LAST_HIT" = 1 ] && [ "$LAST_MASKED" = 0 ] && graded=$((graded+1))
  u_restore U1-$c || break
  [ $graded -ge 3 ] && break
done
mark U1-end; pcap_stop; cap_stop; kill $FAKEPID; th_report $RUN
```

**A graded cycle** has `rearm=1`, `hit=1` (the dial came less than 3000 ms after the scheduling line) and `masked=0` (no re-enumeration line before USB's next SSL). A cycle with `hit=0` is reported but not graded: on both arms the timer fired before the dial, so it measures nothing about the PR.

**Stop rule:** stop an arm at its 3rd graded cycle, or after 12 cycles, or when `u_restore` prints `restored-by=NONE`.

**Expected, from the code:**
- **B, graded cycle:** `df` between 2500 and 4500 ms (the timer woke after the failed dial, found nothing connected and checked USB), and `dssl` within 60000 ms with no `u_restore` route used. `dh` is expected NA on this route (section 3.2); a `dh` value means a dial was still in `Connecting` at 3 s, so record it.
- **C, graded cycle:** `df` NA, `dh` NA, `dssl` NA at 60 s, and `u_restore` names a route. With `conn=1` the dial's `connecting` cancelled the timer (finding 1). With `conn=0` the collector never saw the dial, and the timer found a newer `Disconnected` and did not fire (finding 2's mechanism).

**Verdict for U1:**
- Write **`finding 1 reproduced`** when 2 or more of C's graded cycles have `dssl` NA and `df` NA, while B's graded cycles reached a USB SSL. Then **U1 is FAIL**: the PR drops a USB retry that B performs. Say how many of C's lost cycles had `conn=1` and how many `conn=0`.
- Write **`finding 1 not reproduced`** when every graded C cycle reaches a USB SSL within 60 s. Then U1 is PASS, and the results say which line brought USB back (`df`, `dh` or the attach).
- Fewer than 2 graded cycles on C, or fewer than 1 on B: **INCONCLUSIVE**. Report the `dq` values of every cycle, so the next round knows how far the dial lands from the 3 s mark.

**What a PASS would look like if the change did nothing:** a C cycle with `hit=0` passes on any build, because the timer fires before the dial. That is why only `hit=1` cycles are graded. Also report, as a mechanism count over all `hit=1` cycles including masked ones, the number with `df` between 2500 and 4500 ms on B and on C: B above 0 and C at 0 shows the lost timer even when a re-enumeration hid its effect.

**Phone (D-MOTO capture):** 0 `Critical error` lines on both arms. Record `failed` (note 3's `Error` shape) on every cycle.

**Closing Stage U:** `send ACTION_EXIT` while the app runs; `adb -s $HU shell am start -a android.intent.action.VIEW -d headunit://exit`; `sleep 3`; `adb -s $HU shell am force-stop $PKG`; restore `$BASEXML` with `pocoput`; `cue "H1: unplug the dongle; put D-POCO back on its PC cable"`; `adb -s $POCO_IP:5555 usb`; `HU=$DHU; PH=$POCO`. D-POCO is a phone from here, which is why it got `headunit://exit` first (`rig-quirks/topics/wifi.md`).

### H Headunit Server: the 2 s discovery restart, 10 cycles per arm (Stage W)

D-HU in Headunit Server mode, D-POCO's Android Auto head unit server on the house network. Both must be on the same network: read `dumpsys wifi | grep -iE "mWifiInfo"` on both and record the SSID, frequency and addresses.

```bash
PUT=hu_put; BASEXML=$OUT/settings-backup-DHU.xml
adb -s $HU shell am force-stop $PKG; adb -s $HU shell cat /data/data/$PKG/shared_prefs/settings.xml > $BASEXML
HKEYS="int:wifi-connection-mode=1 int:log-level=2 int:onboarding-version=2 bool:kill-on-disconnect=false del:video-profile-starvation-cap del:native-aa-wireless del:wifi-launcher-mode"
RUN=H-$ARM; CAP=$OUT/$RUN.logcat; apk_check; pcap_start $RUN
listening || srv_restart $RUN-pre || { echo "H UNTESTABLE: server"; }
adb -s $HU shell am force-stop $PKG; cap_start; $PUT $BASEXML $HKEYS
L=$(nl); send ACTION_START_WIRELESS_SCAN >/dev/null
waitfor 90 'SSL handshake complete' $L && waitfor 30 'Throughput over [0-9]+ms: rendered=[1-9]' $L || echo "H first session failed"
for c in $(seq 1 10); do
  sleep 20; w_cycle H-$c 60
  if [ "$LAST_OK" = 0 ]; then
    if listening && [ "$LAST_DIAL" -eq 0 ] && [ "$LAST_FIRED" = 0 ]; then echo -e "H-$c\tF2-shape" | tee -a $OUT/$RUN.tsv
    else echo -e "H-$c\tvoid-server" | tee -a $OUT/$RUN.tsv; fi
    srv_restart H-$c; L=$(nl); send ACTION_START_WIRELESS_SCAN >/dev/null; waitfor 90 'SSL handshake complete' $L || echo "H-$c no session after restart"
  fi
done
mark H-end; send ACTION_EXIT >/dev/null; sleep 3; pcap_stop; cap_stop; th_report $RUN
```

The `w_cycle` columns are: label, `ok` (an SSL within the wait), `tssl` (ms from the end verb to it), `sched` (the discovery restart line printed), `dc` (ms from that line to the first `connecting`), `fired` (a discovery restart line 1800 to 3500 ms after it), `usb`, `auto`, `keep` (counts of the other two timer lines and of the Native keep line), `failed`, `fatal`.

A cycle with no SSL is one of two shapes. If the phone's server stopped listening, or the unit dialled it and failed (`dc` set), the server went down or deaf: the cycle is **void**, H3 restarts the server, and the cycle is not replaced. If the server still listens and the unit **never dialled and never restarted discovery** (`dc` NA, `fired` 0), that is **finding 2's shape**, and it is counted.

**PASS (C):** `ok` on 9 or more of the non-void cycles, and C's `ok` count at least B's minus 1; `sched` 1 on every cycle on both arms; on every C cycle where `dc` is NA or 2000 ms or more, `fired` is 1 (the 2 s restart ran when no dial had cancelled it); `usb` 0, `keep` 0, `fatal` 0. Phone: 0 `Critical error` lines in the run. **Finding 2:** report the F2-shape count per arm. It is expected to be 0; a C count above B's is a FAIL.

### N Native AA: unchanged, 5 cycles per arm (Stage W)

Native AA schedules no timer, so C must behave exactly like B. D-HU and D-POCO, the standard Native bring-up from `ohu_lib.sh`.

```bash
NKEYS3="int:wifi-connection-mode=3 int:log-level=2 int:onboarding-version=2 bool:kill-on-disconnect=false del:video-profile-starvation-cap del:native-aa-wireless del:wifi-launcher-mode"
RUN=N-$ARM; apk_check; pcap_start $RUN
run_open N-$ARM $NKEYS3 || echo "N first session failed"
for c in $(seq 1 5); do sleep 20; w_cycle N-$c 120; done
run_close N-$ARM; pcap_stop; th_report $RUN
```

`run_open` follows the clean-run protocol (phone airplane mode on, captures, settings, launch, airplane off, session up). Before `run_open`, check D-POCO's screen is idle (`dumpsys window | grep mCurrentFocus`; `input keyevent KEYCODE_HOME` if not), as `rig-quirks/units/D-POCO.md` requires.

**PASS (C):** `ok` on 5 of 5, or on at least as many cycles as B; `sched`, `usb` and `auto` 0 on every cycle on both arms (no timer line in Native AA); `keep` 1 on every cycle; the median `tssl` of C at most B's median plus 3000 ms; `fatal` 0. Phone: 0 `Critical error` lines in the run.

### P Wireless Helper over the shared network: the same restart, 5 cycles per arm (Stage W, optional)

Run P only if the Wireless Helper debug build is on D-POCO, or can be built and installed now. The build: the debug variant of upstream `andreknieriem/wireless-helper` at `8ac36c9bcc80731b78949c04fdc63178b5904f2f` (package `com.andrerinas.wirelesshelper.debug`) with `hur-wifi-test-scripts/build_wireless_helper.sh`; `git rev-parse HEAD` must print that SHA. Do not build the fork's `main`: it advertises an old Nearby service id. If neither is possible, P is UNTESTABLE.

Set the helper to Common WiFi (NSD), mode 0 in its own code, and grant its permissions:

```bash
HPKG=com.andrerinas.wirelesshelper.debug
for p in ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION BLUETOOTH_CONNECT BLUETOOTH_ADVERTISE NEARBY_WIFI_DEVICES POST_NOTIFICATIONS; do
  adb -s $PH shell pm grant $HPKG android.permission.$p; done
adb -s $PH shell appops set $HPKG SYSTEM_ALERT_WINDOW allow; adb -s $PH shell am force-stop $HPKG
printf '%s\n' "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>" '<map><int name="connection_mode" value="0" /></map>' > hprefs.xml
adb -s $PH exec-in "run-as $HPKG sh -c 'mkdir -p shared_prefs; cat > shared_prefs/WirelessHelperPrefs.xml'" < hprefs.xml
adb -s $PH shell run-as $HPKG cat shared_prefs/WirelessHelperPrefs.xml        # must show connection_mode value="0"; else H5
```

```bash
PKEYS="int:wifi-connection-mode=2 int:helper-connection-strategy=0 int:log-level=2 int:onboarding-version=2 bool:kill-on-disconnect=false del:video-profile-starvation-cap del:native-aa-wireless del:wifi-launcher-mode"
RUN=P-$ARM; CAP=$OUT/$RUN.logcat; apk_check; pcap_start $RUN
adb -s $HU shell am force-stop $PKG; adb -s $PH shell am force-stop $PKG; cap_start; $PUT $BASEXML $PKEYS
adb -s $PH shell am start -a android.intent.action.VIEW -d wirelesshelper://start -n $HPKG/com.andrerinas.wirelesshelper.MainActivity >/dev/null
L=$(nl); send ACTION_START_WIRELESS_SCAN >/dev/null
waitfor 90 'SSL handshake complete' $L || echo "P first session failed"
for c in $(seq 1 5); do sleep 20; p0=$(pcnt 'AA is now flowing through proxy'); w_cycle P-$c 60
  echo -e "P-$c\tproxy=$(( $(pcnt 'AA is now flowing through proxy') - p0 ))" | tee -a $OUT/$RUN.tsv; done
mark P-end; send ACTION_EXIT >/dev/null; sleep 3; adb -s $PH shell am force-stop $HPKG; pcap_stop; cap_stop; th_report $RUN
```

**If B forms no first session, P is INCONCLUSIVE** ("the helper's NSD path does not form a session on this phone"); record the last 30 `HUREV_` lines of the phone capture. **PASS (C):** C's `ok` count at least B's minus 1; `sched` 1 on every cycle; on every C cycle where `dc` is NA or 2000 ms or more, `fired` is 1; `fatal` 0. Phone: on every `ok` cycle, `proxy` is 1 or more. Count finding 2's shape the same way as in H (the helper has no server to restart, so a cycle with no SSL, no dial and no restart is the shape).

### Closing step

Restore each unit's round backup of `settings.xml`, read it back, and record the diff (it must be empty). Leave D-POCO's head unit server in the state it was found, and say which. Kill every capture, watcher and `fake5277.py`: `ps aux | grep -c "[l]ogcat"` and `pgrep -fc fake5277.py` must both print 0.

## 8. Do not re-run

Nothing from this thread has run before. Settled elsewhere and not repeated: the arbiter tiers, the 8 s USB quiet window and the 60 s budget (`usb-version-retry` rounds 1 to 3); the Native AA exit and group give-back (`pr-1047-session-reconnect` round 1, C3); the Headunit Server join and adopt paths of `a70ba7e2` (`projection-teardown-and-relays` round 3).

## 9. Report back

1. **U1:** `finding 1 reproduced` or not, with the graded cycles' `conn`, `df`, `dh`, `dssl` and restore route on each arm, and every cycle's `dq`.
2. **The ordinary retries:** U2's `df` count per arm (out of 3), H's `ok` and `fired` counts per arm (out of 10), N's `ok` and median `tssl` per arm, and P's `ok` per arm.
3. **Finding 2:** the F2-shape count per arm in H and P, and the number of cycles graded. A count of 0 over about 30 cycles is the expected result and does not clear the race.

### Results skeleton (copy into `pr-1065-reconnect-timers-round1-results.md`)

```markdown
# reconnect-timers, round 1 results

**Candidate C:** codex/handshake-retry-pacing @ 096f57daebb62dba899b9d2b7631b8285cb09f3f merged onto main, tree <printed vs 26758ff3181d78185f8883f40b593bb2504ec980>   **Baseline B:** main @ 145a0c762f0a87386ca63b54518ea5e861b290be
**APK md5:** B <md5> / C <md5>
**Units:** D-POCO <API> as head unit in Stage U, D-MOTO <API, Gearhead>, D-HU <API>, D-POCO <Gearhead> as phone in Stage W
**Date:** <yyyy-mm-dd>
**Evidence:** release `rig-evidence-pr-1065-reconnect-timers`, asset `pr-1065-reconnect-timers-round1-captures.zip`, sha256 <hash>

## Setup notes
Deviations; scripts used and added; hand steps with times; the host IP and whether H4 was needed; dumpsys usb before and after; the network of each unit; void cycles and why; any string that did not match.

## R0 Gate
| Arm | md5 | AutomaticReconnect / HeldServerSocket | commit or tree | JVM count |
|---|---|---|---|---|

## U0
| Arm | Found line to host (s after launch) | listener accept | quiesce line |
|---|---|---|---|

## U2 (control)
| Arm | cycle | rearm | dq | df | masked | dssl | fatal |
|---|---|---|---|---|---|---|---|

## U1 (the point)
| Arm | cycle | rearm | dq | hit | conn | dh | df | gb | masked | dssl | failed | restore route | graded |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
Finding 1: reproduced / not reproduced / inconclusive.

## H
| Arm | cycle | ok | tssl | sched | dc | fired | usb | keep | shape |
|---|---|---|---|---|---|---|---|---|---|

## N
| Arm | cycle | ok | tssl | sched | usb | auto | keep |
|---|---|---|---|---|---|---|---|

## P
| Arm | cycle | ok | tssl | sched | dc | fired | proxy |
|---|---|---|---|---|---|---|---|

## Verdict table
| Run | B | C | Notes |
|---|---|---|---|

## Anything the brief did not ask about
```

Evidence goes to a release asset (template section 7): zip the round folder, `sha256sum` it, and create the release `rig-evidence-pr-1065-reconnect-timers` with that asset (this is the thread's first round).

## Decisive strings

Unit lines are from `app/src/main` of B and of C's merged tree; each is on both arms. `Added device UsbDevice[`, `Critical error`, `Network server running on port` and `FATAL EXCEPTION` are system or Android Auto lines and are not in our source. `AA is now flowing through proxy` is from the helper at `8ac36c9`.

```decisive-strings
AutomationReceiver: 
AutomationMarker: 
AapService: ACTION_END_SESSION_STAY_ARMED received
AapService: USB disconnect. Scheduling reconnect check in 
AapService: Disconnected. Restarting discovery loop in 2s...
AapService: Unclean WiFi disconnect in Auto Mode. Retrying discovery in 2s...
AapService: session state 
re-arming wireless mode 
stopping the wireless stack for the duration of it
NetworkDiscovery: Found Headunit Server on 
NetworkDiscovery: Starting scan...
NetworkDiscovery: in-flight scan promoted to continuous
AapService: Discovery not started 
NetworkDiscovery: Found Wifi Launcher on 
ConnectionArbiter: a USB check waits for 
ConnectionArbiter: 
 ended with no session; giving back 
Found device already in accessory mode
Found known USB device with permission: 
Switching USB device to accessory mode
stood the wireless stack down until it ends
 preempts 
 refused while 
AapService: Native AA session ended; keeping the 
NativeAA: Attempting active poke to device
SSL handshake complete
Throughput over 
MATCH! Starting AapService
createGroup SUCCESS
AapRead: Magic Garbage detected in header
Added device UsbDevice[
Critical error
Network server running on port
AA is now flowing through proxy
FATAL EXCEPTION
```
