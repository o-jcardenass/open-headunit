# usb-reattach: round 1 addendum, Stage D (the dongle)

Same candidate, baseline, units and date as `usb-reattach-round1-results.md`, which holds Stage U, the header and the main Setup notes. This file holds Stage D only.

- Candidate: `37bbe71e`, APK md5 `4647bf675bf59119b34c5ee7dde72fc6` (md5 checked live before each run)
- Unit: D-POCO as head unit on wireless adb, the Carlinkit-class dongle on its OTG port, D-MOTO (on its PC cable, adb through its USB serial) as the dongle's phone; the dongle (`carplay_box_F96B`) is in D-MOTO's bonded list. Date: 2026-10-08

## Setup notes

1. **R4p ran 6 plugs, not 10.** D-POCO read 19% battery while it was powering the dongle over OTG, and the operator asked whether all 10 plugs were required. The brief's PASS conditions are per cycle and name no minimum, so I stopped after plug 6 to keep the battery for R4w (D-POCO read 18% at the start of R4w, 16% at the end). `R4pc7-start` is in `marks.log` and the cue for plug 7 was issued at 18:37:47, a moment before I stopped the script; it was not acted on. The capture has no `R4p-end` marker, so the cycles were summarised by hand with `csum` for `R4pc1` to `R4pc6`. Report: 6 of 6 plugs reached SSL, against 10 of 10 in the older thread.
2. **The operator's manual "USB controlled by" step** (Stage U setup note 1) did not come up in Stage D: the dongle is the host's accessory source and no hand step was reported during these runs.
3. **Stage D layout** as the brief says: D-MOTO's WiFi and Bluetooth on, stay-awake on during the stage and off after; wireless adb for D-POCO. `PH=ZY22GC3BM4` in the scripts.
4. **Hand steps** (`hand-steps.log`): R4p, plug and unplug cues at 18:33:22 to 18:37:47 (plugs 1 to 6, plus the unused plug-7 cue); R4w, a plug cue at 18:38:37 and an unplug cue at 18:43:01. No dialog cue.
5. **Closing.** D-POCO's `settings.xml` restored byte-identical; no logcat left running.

## R4p: negative control, the dongle plugged repeatedly

**PASS**

Six plugs (see setup note 1), each with a 20 s session. Every cycle: `fails=0`, `miss=0`, `extra=0`, `step1=0`, `step2=0`, `giveup=0`, `ssl=1`, `ssl_auto=1`, one `Found device already in accessory mode`, `olddead=0`, `match=0`. No verdict line of any kind (`kinds` empty), so the `PEER_SILENT` rule is not exercised. On the phone, `ph.gh` is 1310, 1496, 1201, 1520, 1545 and 1495 (Android Auto active in every session). The late-answer fix fired in 2 of the 6 plugs (`late=2` in plugs 2 and 6: `SSL Handshake: discarded a late VERSION_RESPONSE`), which is the expected dongle behaviour and shows the ladder did not act on a dongle that answers late. SSL count: 6 of 6.

## R4w: negative control, the dongle re-enumerating by itself

**PASS**

The dongle plugged once and left in; D-MOTO's WiFi was turned off for 20 s under a live session five times, so the dongle re-enumerated by itself. Cycle 0 (the first session) and all five drops reached SSL by themselves (`AUTO_SSL`). Every cycle: `fails=0`, `miss=0`, `extra=0`, `step1=0`, `step2=0`, `giveup=0`, `olddead=0`, `match=0`. `det=4` in each drop (the dongle detached and attached by itself), `late` 1 to 2 in each drop. Phone `ph.gh`: 719 (c0), 1216, 933, 1086, 903, 883.

## Anything the brief did not ask about

- Stage D shows the guards hold in the cases the brief names: no ladder step against the dongle in 11 sessions (6 plugs and 5 re-enumerations) and no failed handshake at all.
- D-POCO's battery limits long OTG sessions: it lost about 1% per 4 minutes of dongle hosting from 19%. A future round with many dongle plugs should start D-POCO charged.
- Evidence for Stage U and Stage D together: release `rig-evidence-usb-reattach`, asset `usb-reattach-round1-captures.zip` (29538728 bytes), sha256 `263ac55e36e3d1e2f09dadb0f023cab00ec7608ad36d3cdc6b9f3102bf32478a`. It holds both stages. The APKs are not in the zip; their md5s are in the header.
