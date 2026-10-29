# WiFi Direct credentials — round 2 brief

Two runs, one unit, both on D-HU. R2r is the point of the round; C1 exists only to prove the fix did
not buy its correctness by disabling the thing it guards. Round 1's R1, R3 and R4 are settled and are
not repeated.

## 1. Build and baseline

Branch `fix/native-aa-wireless` on the fork, candidate **`29ef268e`**
("WiFi Direct: show the group's name and password, and let the user set them"), **2294** JVM tests
green.

```bash
git fetch fork && git checkout 29ef268e
```

**History was rewritten, and round 1's candidate no longer resolves.** `0c9c26e2`, which round 1 was
briefed and reported against, has been squashed together with this round's fix into the single commit
above; the fork no longer carries it. Its parent `e013868a` is unchanged, and the tree at `29ef268e`
is `0c9c26e2`'s plus the fix. A checkout of `0c9c26e2` will fail unless the tester's own
`testing/native-aa-wireless-and-auto-start-loading` still holds the merge from last round.

**No baseline build is needed**: round 1 already measured the defect, and its capture is the baseline.

If this round is again run as a merge into `testing/native-aa-wireless-and-auto-start-loading` rather
than a bare checkout, say so in Setup notes as round 1 did, and report that branch's own test count.

## 2. What this is and why it exists

Round 1's R2 FAILed. With only the passphrase changed and the name deliberately held, the bring-up
found R1's group still live at the OS level and read it rather than recreating it, so the new
passphrase never reached the air and the phone joined on the old one. The decisive lines were
`a group named DIRECT-ZZ-RigTest is already up from before this bring-up; reading it instead of
tearing it down` followed by `asked=nothing (not this app's create)` where `matchesRequest` belonged,
and `wifi-direct-readback-passphrase` still reading the old value afterwards.

The cause was `P2pIdentityRotationPolicy.readsExistingGroup` matching a survivor on the network
**name alone**. It now compares the passphrase as well, so a survivor is read only when the whole
pair matches. Round 1's diagnosis was right and this brief is built on it.

**Why it was worth fixing even though the settings screen cannot produce it.** The edit dialog
redraws the name whenever the passphrase moves, so a user cannot reach the failing state that way.
A settings *restore* can, because the pair is in the backup's import map.

## 3. What is different about this round

- **The live group is the whole setup, and clearing saved profiles does not create it.** Round 1
  found this the hard way: `cmd wifip2p delete-saved-group` removes *stored* profiles, while the
  group R1 left running is a separate OS object that survives an `am force-stop`. R2r needs that live
  group present, so **do not clear anything between the setup run and R2r**, and confirm the group is
  still up with `dumpsys wifip2p` before starting R2r.
- **A null live passphrase now means recreate.** If D-HU's `WifiP2pGroup` ever reports no passphrase,
  the survivor is refused and a create runs. That is deliberate. If a run shows a recreate where the
  pair looks identical, quote `dumpsys wifip2p`'s passphrase field before calling it a FAIL.
- **`wifi-direct-group-name-changes` survives rounds** and read 47 on D-SAM last time. D-SAM is not
  used this round, but read the counter on D-HU before the setup run and say what it was.
- **`stat shared_prefs/` again**, since both runs are graded partly on the app's own writes. Round 1
  read `drwxrwx--x u0_a176 u0_a176`, correct; report it either way.
- No D-SAM and no phone-side work beyond letting D-POCO join. R3's below-Q coverage is settled.

## 4. Settings keys this round needs

Written per §1, app stopped, verified by reading the file back.

| Key | Type | Value | Note |
|---|---|---|---|
| `wifi-connection-mode` | int | `3` | Native AA |
| `native-ap-transport` | int | `0` | WiFi Direct |
| `wifi-direct-stable-identity` | boolean | `true` | required for the survivor read to be reachable at all |
| `log-level` | int | `2` | INFO carries every line in §5 |
| `wifi-direct-group-name` | string | `DIRECT-ZZ-RigTest` | set once in the setup run, never changed after |
| `wifi-direct-group-passphrase` | string | `RigPass12345` then `RigPass99999` | the only key R2r changes |
| `wifi-direct-identity-user-set` | boolean | `true` | marks the pair as typed |

**Delete before the setup run**: `wifi-direct-readback-name`, `wifi-direct-readback-passphrase`,
`wifi-direct-readback-bssid`.

## 5. The lines that decide every run

All `AppLog.i`, so `log-level=2` carries them. Each verified with `grep -F` against `29ef268e`.

```
WifiDirectManager: a group named
is already up from before this bring-up; reading it instead of tearing it down
WifiDirectManager: group identity ssid=
matchesRequest=
WifiDirectManager: SUCCESS - Providing credentials to listener. SSID=
WirelessServer: Incoming connection detected
```

The survivor line and `matchesRequest=` are mutually exclusive by construction: a group that is read
was not created, so it is graded `asked=nothing (not this app's create)` instead. **Which of the two
appears is the whole measurement.**

## 6. Runs

### Setup run (not graded)

Write the seven keys in §4 with `RigPass12345`, delete the three read-back keys, launch, let D-POCO
join, confirm a session forms. Then `am force-stop` the app and **leave the group up**. This
reproduces round 1's R1 end state, which is R2r's starting condition. Record the SSID and confirm via
`dumpsys wifip2p` that the group is still open.

### R2r — a changed passphrase reaches the air even with the old group still live (the point)

With the group from the setup run still up, change **only** `wifi-direct-group-passphrase` to
`RigPass99999`. Relaunch and let the phone reconnect.

- **PASS**: the survivor line is **absent**; the log carries `group identity ssid=DIRECT-ZZ-RigTest
  ... matchesRequest=yes`; `SUCCESS - Providing credentials to listener` names the same SSID; the
  phone joins (`WirelessServer: Incoming connection detected`); and with the app stopped afterwards
  `wifi-direct-readback-passphrase` reads **`RigPass99999`**.
- **FAIL**: the survivor line appears, or `matchesRequest` is missing or `no`, or the read-back
  passphrase still reads `RigPass12345`.
- **If the fix did nothing**, this run reproduces round 1's R2 exactly, so the two are directly
  comparable. Quote the read-back passphrase rather than reporting a verdict alone.
- Note the phone may need a moment longer than round 1 here, because the group is genuinely recreated
  rather than reused. That is expected and is not a FAIL; report the time to
  `Incoming connection detected` so C1 has something to sit against.

### C1 — an unchanged pair still takes the fast path (the control)

Immediately after R2r, `am force-stop` the app, change **nothing**, and relaunch.

- **PASS**: the survivor line **is** present, and no `createGroup` runs for it.
- **FAIL**: the group is recreated although neither half changed. That would mean the fix bought R2r
  by disabling the survivor read outright, which costs every ordinary reconnect the measured 1.1 s
  path and would be a worse regression than the bug.
- Report the time from relaunch to `Incoming connection detected` beside R2r's, so the two paths can
  be compared on this unit.

## 7. Do not re-run

Round 1's R1 (typed pair reaches the air), R3 (the read-back record below the naming API on D-SAM) and
R4 (the export withholds the passphrase) are all settled and unaffected by this change; they were
measured on the code now squashed into `29ef268e`.
Nothing from `native-aa-wireless` or `wpp-endpoint-depoison`. The pair-matching rule itself is
JVM-tested at `29ef268e` and needs no hardware.

## 8. Report back

1. Whether the survivor line appeared in R2r, and whether it appeared in C1. These must differ.
2. `wifi-direct-readback-passphrase` after R2r, quoted.
3. Whether `matchesRequest` read `yes` in R2r.
4. Relaunch to `Incoming connection detected` for R2r and for C1, so the cost of a recreate against a
   survivor read is on the record for this unit.
