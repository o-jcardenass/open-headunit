# pr-1119-home-redesign round 1 - addendum 1: widget size

Applies to `pr-1119-home-redesign-round1-brief.md`. Same build (`review/pr-1119` @ `11a2c37f`), same stage A on D-HU. It adds one capture to HU-L2 and one run, HU-W1, after HU-L3. Everything else in the brief stays as written. Extra time: about 6 minutes.

## Why

The review found two unit errors in `HomeWidgets`:

- **B2.** `minWidthPx`/`minHeightPx` multiply `AppWidgetProviderInfo.minWidth`/`minHeight` by the screen density. The framework already gives those values in px. So on a 2.0-density unit a widget in the row under the favorites asks for about twice its width. Only that row (`widgetTile`) uses these sizes. A widget on a page (`pageWidgetCell`) fills its cell, so HU-L2 cannot show B2.
- **B3.** `tellSize` passes px to `AppWidgetHostView.updateAppWidgetSize`, which takes dp. Both the row and the page cells call it.

D-HU runs at 320 dpi (density 2.0), so both errors show there at their full size.

## Change to HU-L2

After `pages2 HU-L2` returns, and before `pages3 HU-L3`, run:

```bash
adb -s $D shell wm density > $OUT/HU-L2-density.txt
adb -s $D shell dumpsys appwidget > $OUT/HU-L2-appwidget.txt
```

These are captures only. HU-L2's own PASS rule does not change.

## New run HU-W1 (after HU-L3, before HU-C1)

Gestures: 3 or 4 (cap 5). The run adds one widget to the row under the favorites, then measures it.

**widget_size.sh**

```bash
wsize() { local R=$1 i; th_gate $R || return 1; mark $R-start
  adb -s $D shell input keyevent 3; sleep 3            # the HOME key: page 1, not a gesture
  uidump $R-p1
  if ! python3 -I $UIQ find $OUT/ui/$R-p1.xml id:widget_add_tile | grep -q bounds; then
    gest $R next id:home_pager $R-p1; sleep 2; uidump $R-p1   # a compact home keeps the row on page 2
  fi
  gest $R tap id:widget_add_tile $R-p1; sleep 2; uidump $R-pick
  gest $R tap idfirst:widget_option_label $R-pick; sleep 3
  focus > $OUT/$R-focus.txt
  grep -qi 'MainActivity' $OUT/$R-focus.txt || { uidump $R-bind; gest $R tap id:button1 $R-bind; sleep 3; }
  sleep 2; uidump $R-after                            # uidump also saves ui/$R-after.png
  adb -s $D shell wm density > $OUT/$R-density.txt
  adb -s $D shell wm size > $OUT/$R-size.txt
  adb -s $D shell dumpsys appwidget > $OUT/$R-appwidget.txt
  pull_settings $OUT/$R-settings.xml
  mark $R-end; th_end; }
wsize HU-W1
```

### How to read it

1. **The widget.** The picked provider is the first `widget_option_label` in `HU-W1-pick.xml`. Record its text.
2. **What it asks for, in px.** In `HU-W1-appwidget.txt`, find the provider block for that widget (its package name follows `provider=` or `ComponentInfo{`). Read `min=(WxH)` in that block. These values are px. Call them `minW` and `minH`. If the block has no `min=(` line, record that and grade B2 INCONCLUSIVE.
3. **What the app gave it.** In `HU-W1-after.xml`, find the node with `resource-id` ending `home_widget_frame` that is NOT on the added page (HU-L3 removed that page, so expect exactly one). Read its `bounds` and compute the frame width `FW` in px.
4. **The density.** `d` = the `Physical density` (or `Override density`, if present) in `HU-W1-density.txt`, divided by 160.

### Verdict for B2 (widget asks for density times too much)

The padding is 6 dp each side, which is 24 px at `d` = 2. Let `inner` = `FW` - 24.

| Result | Condition |
|---|---|
| **B2 CONFIRMED** | `inner` >= 1.6 x `minW` (the code's `minW` x `d`, at `d` = 2) |
| **B2 NOT REPRODUCED** | `inner` <= 1.25 x `minW` |
| **INCONCLUSIVE** | anything between, or no `min=(` line, or no widget was placed |

Also record:

- whether the right edge of any `widget_add_tile` or second widget in `HU-W1-after.xml` lies past the screen width in `HU-W1-size.txt` (the row overflowing);
- the height of the node `home_favorites_area` against its height in `HU-L1`'s dumps, if both are present (the row taking card height).

### B3 (size told in px instead of dp), record only

In `HU-W1-appwidget.txt` and `HU-L2-appwidget.txt`, search each widget block for `appWidgetMinWidth` or `options=`. If present, record `appWidgetMinWidth` and `appWidgetMaxWidth` beside that widget's frame width in px from the matching `after` dump. The values are meant to be dp, so a correct app reports about `inner` / `d`. A value close to `inner` itself means B3 is real. If the dump carries no options, write "B3: no options in dumpsys" and move on. B3 never fails the run.

### Clean-up

Nothing extra. The closing settings restore in the brief removes the row widget. The widget id stays allocated in the system until the app is cleared. That is expected and is not graded.

## Results file

Add an `HU-W1` section to `pr-1119-home-redesign-round1-results.md` in the same format as the other runs: the verdict line, the provider name, `minW`/`minH`, `FW`, `d`, `inner`, the overflow and height notes, and the B3 line. Put the HU-L2 B3 line under HU-L2.
