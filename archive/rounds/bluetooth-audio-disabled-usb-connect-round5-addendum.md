# bluetooth-audio-disabled-usb-connect, round 5 addendum

Addendum to `bluetooth-audio-disabled-usb-connect-round5-results.md`. No verdict changes.

## H3 was performed by the operator and not logged

Round 4's H3 (kept by the round 5 brief) says: if D-MOTO shows its USB preferences, choose "USB controlled by: This device". D-MOTO as the phone on D-POCO's OTG port needs that choice by hand before it will connect. The reverse direction (D-POCO as the phone, D-MOTO as the head unit) needs no such step.

The round 5 host never cued H3 and never wrote it to `hand-steps.log`, and the results file lists no H3. The operator confirmed afterwards that they set it at the plug-in of each session. So every session in R0, R0x, R1 and S1 to S4 included one unlogged hand step on D-MOTO's Settings, at the moment of the plug-in.

## What this changes in the results

- **The `UsbDetailsActivity` read failures.** The results file attributes the `UNREAD` readings in R0 and R0x to D-MOTO's Settings sitting on `Settings$UsbDetailsActivity` after the USB attach, and says the clear-task flag added to `ui_open` fixed it. The screen is that of the same USB preferences H3 is done on. The flag works around the symptom; the cause is that the USB preferences screen opens on attach and stays in front of the Bluetooth screen until it is handled.
- **R0 and the second session.** R0's second session came after a late unplug. It does not depend on H3.
- **Verdicts.** None of the graded parts depends on H3 timing: sessions formed in every run, `hu.ssl` was 1 in every run except the voided R0, and no H1 recovery was needed. The round verdict stays INCONCLUSIVE.
- **Input budget.** The operator's choice is not an injected input, so `inputs.log` is unchanged (23 lines, at most 4 per run).

## For the next brief

H3 should be a cued hand step with a log line, once per plug-in, with the cue timed after the attach, so that the operator's choice is part of the record and the Bluetooth screen is not left behind it.
