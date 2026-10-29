# usb-version-retry: round 1 brief

This round grades a new fix and also finishes `pause-auto-connect` round 1's two open legs.
One build carries both.

## 1. Build and baseline

- Candidate: **`fix/usb-auto-connect`** on the fork, tip **`0933a348`**: `fix/pause-auto-connect-all-modes`
  (`1984b1ad`, unchanged since round 1) renamed, plus the handshake fix on top. Three commits on
  `main` `dd454eed`. The old branch name and `testing/usb-version-retry-plus-pause-auto-connect` are
  deleted from the fork.
  ```bash
  git fetch fork fix/usb-auto-connect
  git checkout -B fix/usb-auto-connect fork/fix/usb-auto-connect
  git rev-parse --short HEAD   # 0933a348
  ```
- JVM gate: **2335 tests, 0 failures** on this SHA from the author's side.
- DEX identity: both `HandshakeMessagePolicy` and `AutoConnectHoldPolicy` must be in the APK
  (template §5's symbol check).
- Baseline for the A/B in R2: round 1's APK (`1984b1ad`, md5 `c482906e095a181a01bff17f0040d719`). If
  it is gone, rebuild `1984b1ad` (`git checkout 1984b1ad`; the old branch name is gone).

## 2. What this is and why it exists

Round 1 blamed the dongle's connect storm on "the rig's marginal link, #800 signature". It was our
handshake. Every handshake in round 1's 16 captures, on both the libusb and the standard route, falls
into one of these shapes:

| Shape | Sequence in the log | Outcome on `1984b1ad` |
|---|---|---|
| OK | version response inside 2 s | SSL complete about 45 ms later |
| A | `No VERSION_RESPONSE within 2s (attempt 1)`, then a response 0.2 to 0.9 s later | `Unable to parse TLS packet header`, 20 of 20 |
| B | the reconnect 3 s after an A: `Drained 2353 bytes of stale USB data`, then version OK | `SSL Handshake: Failed to read AAP header` |
| C | three `No VERSION_RESPONSE`, giving up at 6.6 s | the next attempt drains `36 bytes` and succeeds |

The dongle relays to the phone over WiFi, so its first answer takes 1.8 to 2.9 s. At 2 s we resend,
the phone answers **both**, and the second answer went to the TLS engine as if it were TLS. The 2353
bytes are the phone's own TLS reply, so the phone was fine and only we failed. The 36 bytes in C are
three 12-byte version answers that came after we gave up.

The fix: the TLS reader skips a late version response (at most 3) and prints
`SSL Handshake: discarded a late VERSION_RESPONSE`, and the third request waits out the 10 s budget
instead of stopping at 6.6 s.

## 3. What is different about this round

- **Stage A only, same rig as round 1**: D-POCO as head unit and USB host over OTG, on wireless adb,
  the Carlinkit-class dongle (idle `18d1:4ee1`, accessory `18d1:2d00`), D-MOTO paired with the dongle.
  Every rule in round 1's §3 about wireless adb, the two readers (`capture.sh` in
  `round-pause-auto-connect-r1/`) and hand replugs still applies.
- **Clear D-POCO's leftover `wifi-connection-mode=3` first** (set it to 0 in `settings.xml` with the
  app stopped). It filled round 1's captures with poke and group churn. Put the before and after
  values in Setup notes.
- **Any `logcat | grep -m1` watcher needs `stdbuf -oL`** in front of `adb`, as round 1's addendum
  found. Without it, the broadcast lands minutes late.
- **Keep the in-app USB list closed** in every run. Round 1 saw it change the odds, and it must not
  hide whether the fix did.
- No taps. Every action is template §3's `send`.

## 4. Settings keys

On D-POCO, with `set_prefs_runas.sh`, and back up `settings.xml` first.

| Key | Type | Value | Why |
|---|---|---|---|
| `wifi-connection-mode` | int | `0` | quiet capture (see §3) |
| `auto-start-on-usb` | boolean | `true` | as round 1; read the device-protected mirror back after a force-stop and relaunch |
| `reopen-on-reconnection` | boolean | `true` | as round 1 |
| `auto-connect-last-session` | boolean | `true` | as round 1 |
| `kill-on-disconnect` | boolean | `false` | as round 1 |
| `use-libusb` | boolean | per run | R1 and R2 run both routes |

## 5. The lines that decide every run

Checked with `git grep -F` on `0933a348`.

| Line (substring) | Meaning |
|---|---|
| `Handshake: No VERSION_RESPONSE within 2s` | the dongle was slow and we resent (the trigger) |
| `SSL Handshake: discarded a late VERSION_RESPONSE` | the fix skipped the duplicate (new) |
| `No VERSION_RESPONSE within the handshake budget` | the third request waited the full 10 s and still got nothing (new wording) |
| `Unable to parse TLS packet header` | shape A, the bug |
| `SSL Handshake: Failed to read AAP header` | shape B |
| `Drained ` … ` bytes of stale USB data` | leftovers from an earlier attempt; the byte count says which shape left them |
| `Switching USB device to accessory mode` / `Found device already in accessory mode` | a connect started |
| `SSL handshake complete` | a session formed (match without the `Handshake:` prefix) |

## 6. Runs

### R0 Build gate
SHA `0933a348`, DEX carries both symbols, JVM gate 2335/0, `adb install -r` on D-POCO. PASS: all four.

### R1 Cold plugs on the candidate, both routes (the point of the round)
Home screen in front, no session. For each route (`use-libusb` = `true`, then `false`, force-stop and
relaunch between), do **10 cold plugs**: unplug, wait 5 s, plug, and wait until `SSL handshake
complete` or 90 s. After each session forms, `send ACTION_DISCONNECT` and wait 10 s before the next
unplug.

For each plug, record: the number of accessory-mode lines before SSL, whether `No VERSION_RESPONSE
within 2s` printed, whether `discarded a late VERSION_RESPONSE` printed, and the time from the first
accessory-mode line to `SSL handshake complete`.

PASS, per route, all of:
- **zero** `Unable to parse TLS packet header` and **zero** `SSL Handshake: Failed to read AAP header`;
- every handshake with `No VERSION_RESPONSE within 2s` followed by a response carries a
  `discarded a late VERSION_RESPONSE` and then `SSL handshake complete` **on that same handshake**;
- SSL on the first handshake after the plug in at least **9 of 10** plugs.

If no plug in a route prints `No VERSION_RESPONSE within 2s`, the dongle was fast the whole time and
the fix was never exercised on that route: grade it INCONCLUSIVE, not PASS, and add 10 more plugs.

### R2 A/B against round 1's build (libusb route)
Install the baseline APK (§1), same settings, and do 10 cold plugs on `use-libusb=true` exactly as R1.
Then reinstall the candidate. This checks the "marginal link" claim directly on the same day and cable.
Report the same per-plug table. There is no PASS condition for the baseline itself; the round needs its
shape A count to compare against R1's zero. If the baseline shows **no** shape A in 10 plugs, the
dongle was fast that day, and R1's PASS rests only on the handshakes where the trigger line printed.

### R3 pause-auto-connect R1 again: the SSL leg
Round 1's R1 on this candidate, exactly as written in `pause-auto-connect-round1-brief.md` §6 R1,
with `use-libusb=true`. Round 1 proved the hold and the replay; what it could not capture was
`checking USB now.` followed by `SSL handshake complete` in one unbroken capture.
PASS: round 1's R1 conditions, all of them, in a single capture. Report both gaps (BACK to
`checking USB now.`, and that line to `SSL handshake complete`).

### R4 pause-auto-connect R4 again: the before-SSL X
Round 1's R4 with the watcher keyed on `Found device already in accessory mode`, now with
`stdbuf -oL`:
```bash
stdbuf -oL adb logcat -T 1 OPENHU:V '*:S' | grep -m1 -F 'Found device already in accessory mode' >/dev/null \
  && send ACTION_CANCEL_WIRELESS
```
PASS: round 1's R4 conditions, with no `SSL handshake complete` between `Found device already in
accessory mode` and `the status pill's X stopped the USB attempt.`. With the fix in, SSL may now come
about 45 ms after the version response, so if the X lands after SSL on 3 tries, report it as
INCONCLUSIVE and give the gap from the accessory line to the X's `AutomationReceiver` line. The
after-SSL case already PASSed and is not repeated.

## 7. Do not re-run

Round 1's R0, R2, R3, R5 and R6, and R4's after-SSL case, all PASSed on `1984b1ad`, which this
candidate carries unchanged. Stage B (D-HU, Bluetooth) is not in this round.

## 8. Report back

1. **R1:** the per-plug table for both routes, and the counts of the trigger line, the discard line,
   shape A and shape B.
2. **R2:** the baseline's per-plug table and its shape A and B counts.
3. **R3 and R4:** the verdicts and the gaps named above.

Captures: new release `rig-evidence-usb-version-retry`, asset `usb-version-retry-round1-captures.zip`,
cited with its sha256 (template §7).
