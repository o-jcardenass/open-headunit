# WiFi Direct credentials — round 1 brief

Four runs, two units. The point of the round is R1. Everything the UI does is already covered by JVM
tests; this round grades only what they cannot reach.

## 1. Build and baseline

Branch `fix/native-aa-wireless` on the fork, candidate **`0c9c26e200df4710444d5c0e7aa14302e99fdcac`**
(`0c9c26e2`), **2291** JVM tests green.

```bash
git fetch fork && git checkout 0c9c26e2
```

**History was rewritten.** The work landed as two commits and was squashed into one before the push,
so a SHA quoted anywhere earlier will not resolve. Its parent `e013868a` is unchanged. Build and
install with the rig's own scripts (§5).

## 2. What this is and why it exists

Until this commit the WiFi Direct group's name and passphrase were minted at random, kept in
`settings.xml`, handed to the phone over Bluetooth, and shown to the user nowhere. They were not in
the log either. Someone whose phone could see the network but never join it had no way to read the
password or to set a known one.

Two things are new. A **read-back record** of what the group actually came up as, written on every
group-info callback, so a settings row can show the name, password and address with the stack down;
it works on every API level. And a **typed pair**, above API 29 only, stored in the same keys the
minted pair uses, so it needs no new code path in the create.

One subtle rule sits underneath: **a changed passphrase redraws the name's two-character code**,
because a name reused with a different passphrase is the one combination a phone's saved profile
cannot recover from. That rule is a pure policy with 16 unit tests and is **not** re-proved here.

## 3. What is different about this round

- **The read-back record is one of the app's own writes to `settings.xml`.** If `shared_prefs/` is
  root-owned the write silently fails while reads still look fine, and R1 and R3 would both grade a
  broken directory as a broken feature. `stat` it before R1 and **report what it read even when it is
  correct**; `chown` to the app's uid:gid if it is wrong.
- **The typed pair is set by writing the keys, never through the UI** (house rule 3). The rows,
  their validation and the redraw are JVM-tested; behaviour is what hardware adds.
- **D-HU's persistent group lets a paired phone rejoin through the P2P framework alone.** Before R2,
  clear D-HU's OS-level saved groups (`cmd wifip2p init`, then `list-saved-groups` and
  `delete-saved-group <id>` for each), or the phone may rejoin the *old* group and R2 grades the
  wrong network.
- **`wifi-direct-group-name-changes` survives rounds nobody reported.** Read it before R1 and clear
  it deliberately rather than assuming zero.
- **R3 is the only run on D-SAM**, and it grades the record only. `wifi-direct-stable-identity` has
  no code path below API 29 there, so nothing about a stable name is testable on that unit.

## 4. Settings keys this round needs

Written per §1, app stopped, verified by reading the file back.

| Key | Type | Value | Note |
|---|---|---|---|
| `wifi-connection-mode` | int | `3` | Native AA |
| `native-ap-transport` | int | `0` | WiFi Direct |
| `wifi-direct-stable-identity` | boolean | `true` | required for a kept pair |
| `log-level` | int | `2` | INFO carries every line in §5 |
| `wifi-direct-group-name` | string | `DIRECT-ZZ-RigTest` | R1, R2 |
| `wifi-direct-group-passphrase` | string | `RigPass12345` | R1; R2 changes only this |
| `wifi-direct-identity-user-set` | boolean | `true` | marks the pair as typed |

**Delete before R1** (run the delete half only): `wifi-direct-readback-name`,
`wifi-direct-readback-passphrase`, `wifi-direct-readback-bssid`, `wifi-direct-group-name-changes`.

## 5. The lines that decide every run

All are `AppLog.i`, so `log-level=2` is enough. Each verified with `grep -F` against `0c9c26e2`.

```
WifiDirectManager: standard createGroup as
WifiDirectManager: group identity ssid=
matchesRequest=
the network name and password
WifiDirectManager: SUCCESS - Providing credentials to listener. SSID=
WirelessServer: Incoming connection detected
```

`matchesRequest=` is emitted only by the group-identity read-back line and is the one field that says
whether the platform honoured the pair we asked for. `the network name and password` appears only in
the reason string a **typed** pair produces, so it separates R1 from an ordinary kept pair.

## 6. Runs

### R1 — a typed pair reaches the air and the phone joins it (D-HU, the point of the round)

Write the seven keys in §4, delete the four, launch, let the phone connect.

- **PASS**: the log carries `asking for DIRECT-ZZ-RigTest, the network name and password the user
  typed`; `standard createGroup as DIRECT-ZZ-RigTest`; `group identity ssid=DIRECT-ZZ-RigTest ...
  matchesRequest=yes`; `SUCCESS - Providing credentials to listener. SSID=DIRECT-ZZ-RigTest`; and
  `WirelessServer: Incoming connection detected`. Then, with the app stopped, `settings.xml` carries
  `wifi-direct-readback-name` = `DIRECT-ZZ-RigTest` and `wifi-direct-readback-passphrase` =
  `RigPass12345`.
- **FAIL**: `matchesRequest=no`, or the SSID on the air is not the typed one, or the three
  `wifi-direct-readback-*` keys are absent after a group came up.
- **If the change did nothing**, the group would come up under a *random* `DIRECT-xx-...` name and
  the readback keys would not exist at all. Quote the SSID, do not just report a verdict: a PASS that
  only says "connected" does not distinguish the two.

### R2 — the pair is genuinely what the phone is given, not a label (D-HU)

Clear the saved P2P groups first (§3). Change **only** `wifi-direct-group-passphrase` to
`RigPass99999`, leaving the name key as it is, then relaunch and reconnect the phone.

- **PASS**: the group comes up on the name in `settings.xml` and the phone joins with the new
  passphrase (`Incoming connection detected` again). Report the SSID and passphrase from the
  read-back keys afterwards.
- **FAIL**: the phone joins while `wifi-direct-readback-passphrase` still reads `RigPass12345`, which
  would mean the platform reinvoked a stored profile and the typed passphrase never reached the air.
- This run is the **positive control for the redraw rule**: it is the one arrangement the rule exists
  to prevent, produced deliberately by writing the key directly, which the UI would not let a user do.

### R3 — the record works below the naming API (D-SAM, API 19)

Set `wifi-connection-mode=3`, `native-ap-transport=0`, `log-level=2`. Do **not** write the pair keys.
Let a group come up.

- **PASS**: after the group forms, `settings.xml` carries all three `wifi-direct-readback-*` keys, and
  their name matches the SSID on the `onGroupInfoAvailable: SSID:` line in the same capture.
- **INCONCLUSIVE, not FAIL**, if no group forms at all on D-SAM in this round's time budget; say so
  and move on.

### R4 — the passphrase is withheld from an export (either unit, desk check)

With `allow-external-configuration` on, export the settings to a file and read it back.

```bash
adb shell am broadcast -a com.andrerinas.openheadunit.ACTION_GET_SETTINGS \
  --es path /sdcard/Download/export.json
```

- **PASS**: the JSON contains neither `wifi-direct-group-passphrase` nor
  `wifi-direct-readback-passphrase`, and the reply carries a non-zero `withheld` count.
- **FAIL**: either key appears in clear text.

## 7. Do not re-run

Nothing from `native-aa-wireless` rounds 1 to 4 or from `wpp-endpoint-depoison`. The redraw rule, the
name-shape validation and the passphrase length and charset limits are JVM-tested at `0c9c26e2` and
need no hardware. The identity-stability verdict and the endpoint gating are settled and untouched by
this commit.

## 8. Report back

Four things decide whether this ships:

1. The SSID the group actually came up as in R1, quoted.
2. Whether `matchesRequest` read `yes` or `no` in R1 and R2.
3. The three `wifi-direct-readback-*` values after R1 and after R3, quoted.
4. What `stat shared_prefs/` read on each unit, whatever it said.
