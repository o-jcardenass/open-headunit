# hotspot-endpoint-poison: round 3 brief

> **Revised before any run.** The candidate is now **`29abed17`**, six commits: the sixth shortens
> all 13 connection-issue banners in every locale and changes no behaviour. A copy that says
> `24682388` is stale.

## 1. Build and baseline

- Branch `fix/hotspot-endpoint-poison` on the fork, tip **`29abed17`**, six commits on `main`
  `dd454eed`. Round 2's `30a1f6d8` plus two commits; never rewritten.
  ```bash
  git fetch fork fix/hotspot-endpoint-poison
  git checkout -B fix/hotspot-endpoint-poison fork/fix/hotspot-endpoint-poison
  git rev-parse --short HEAD   # 29abed17
  ```
- JVM gate: **2340 tests, 0 failures** on this SHA from the author's side.
- DEX identity per round 2 §1; `ACTION_QUERY_STATE`'s commit is what separates it from round 2's APK.

## 2. What round 2 found, and what changed

Round 2 (`hotspot-endpoint-poison-round2-results.md`) passed everything except R4, and its root cause
was right. The bring-up that raised `PHONE_HOLDS_STALE_ENDPOINT` also landed a Bluetooth session,
and a landing with no refused dial beside it retires that record. That rule is sound on WiFi Direct,
where a phone holding the old endpoint reaches our listener and is refused. On a hotspot it is not:
the phone dials an address nobody holds, so no refusal is ever seen and the banner was cleared the
moment it was raised. `24682388` stops a hotspot landing from retiring it. What still retires it
there is the user's dismissal, or a dial we actually serve.

Two things from round 2's notes, neither a change:

- **D-MOTO's stale record pointed at `192.168.49.1:5299`**, a WiFi Direct group owner's address that
  nothing in round 2 used. It is most likely another head-unit identity's record (D-POCO has stood
  in as the head unit), which forgetting D-HU cannot remove, so it does not show that Android Auto's
  Forget fails. `pm clear` on Gearhead stays operator-approved only.
- **ACC-on re-arms nothing unless `auto-start-on-boot=true`.** That is existing behaviour
  (`onHibernateWake`), now in template §7a.

## 3. What is different about this round

- Units, readers, keys and the bring-up recipe are round 1's §3 and §4 with round 2's §3
  corrections. **No reboot is needed this round.**
- Only R0, R4 and R7 run. Nothing else in the branch moved.

## 4. The lines that decide every run

Checked with `git grep -F` on `29abed17`:

| Line (substring) | Meaning |
|---|---|
| `NativeAA: the WPP endpoint advertised on the access point at` … `no longer matches (password)` | the banner was raised |
| `NativeAA: WiFi session landed.` | a landing, which used to clear it |
| `MainActivity: showing the connection issue banner for PHONE_HOLDS_STALE_ENDPOINT` | the banner reached the screen |
| `connection-issue-stale-endpoint` in `settings.xml` | the record: an epoch, or 0 when retired |

## 5. Runs

### R0 Build gate
SHA `29abed17`, JVM gate 2340/0, `adb install -r` on D-HU, `ACTION_QUERY_STATE` reports the commit.
PASS: all.

### R4 A moved password raises the banner, and it stays up
Exactly as round 2's R4: AP up from the shell (`OHU-HOTSPOT` / `ohutest12345`), one bring-up to a
session, force-stop, seed the four `soft-ap-advertised-*` keys from the identity line with the
64-zero `soft-ap-advertised-psk-digest`, `connection-issue-stale-endpoint=0`, read all five back,
then one bring-up to a session.

Then force-stop, read `connection-issue-stale-endpoint` (call it E), and one more bring-up to a
session. Force-stop and read it again.

PASS, all of:
- `no longer matches (password)` once, with only `password` in the brackets;
- `NativeAA: WiFi session landed.` **after** that line in the same bring-up;
- E is non-zero;
- after the second bring-up (also a landing) the key still reads **E**, unchanged, and no second
  `no longer matches` line printed.

Record, not graded: whether `showing the connection issue banner for PHONE_HOLDS_STALE_ENDPOINT`
appears when the main screen is next on top, and if a screenshot is taken, whether the text reads
"Your phone is trying an old network address for this unit." The dismiss button is a tap, so it is
not driven.

If the change did nothing, E would read 0, as it did in round 2.

### R7 WiFi Direct: the retire still works there
Revert per round 1 §4. Force-stop, write `connection-issue-stale-endpoint` = `1790000000000` and read
it back. One WiFi Direct bring-up to a session, force-stop, read it again.

PASS, all of: `WifiDirectManager: group identity ssid=` present, no `access point identity` line,
`SSL handshake complete`, `NativeAA: WiFi session landed.`, and the key reads **0** afterwards.

If the change had leaked into WiFi Direct, the key would still read `1790000000000`.

## 6. Do not re-run

R1, P1, R2, R3, R5, R6 and R8 are measured across rounds 1 and 2 and untouched by `24682388`.

## 7. Report back

1. R4: E, and the value after the second bring-up.
2. R4: whether the banner line appeared (not graded).
3. R7: the key's value after the WiFi Direct landing.
