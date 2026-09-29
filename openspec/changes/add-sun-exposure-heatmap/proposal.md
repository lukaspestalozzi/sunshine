# Proposal

## Why

The overlay (#5) shows where the sun shines at one time. A hiker picking a route, a picnic spot or
a campsite also wants to know how long each place gets sun over the whole day, e.g. which side of
a valley stays sunny longest in December. The overlay already computes the grid of every 5-minute
slider step of the selected day in the background. Adding those grids up gives the hours of sun
per cell, without tracing any terrain again. That avoids the legacy failure mode of a heatmap that
recomputed a full terrain ray per cell for every 30-minute step.

Roadmap: implements entry #7, `add-sun-exposure-heatmap`, of `docs/roadmap.md`. It is done before
#6 (`add-offline-regions`) by user decision (2026-09-28): #6 still has open questions about the
tile servers' bulk-download policies, and #7 does not depend on it.

## What Changes

- `app`: a second mode of the sun-shade overlay (user decision). While the overlay is on, a
  segmented control switches between `Sun & shade` (today's overlay at the selected time) and
  `Sun hours` (the heatmap of the selected day).
- `app`: the heatmap colours every cell of the visible area by its hours of sun on the selected
  day: the number of slider steps at which the cell is sun, times 5 minutes. It is built from the
  day's grids once the whole day is computed.
  - The colour scale runs from 0 h to the day's possible sun (the day length at the map centre)
    in 30-minute bands (user decisions).
  - Four colour stops, dark slate grey → blue → yellow → light amber, with brightness rising with
    the hours. The whole visible area is tinted, translucent enough to keep the map readable
    (user decision).
  - A legend shows the scale with labels every 2 h.
- `app`: unknown steps (user decision): a cell's colour counts only the steps known to be sun, so
  it is a lower bound. A cell with at least one unknown step is hatched on top of its colour. A
  cell with every step unknown is hatched without colour.
- `app`: the sun panel gets a `Sun hours` line in heatmap mode for the crosshair's cell, below the
  tracer's `Sunshine` line (user decisions): `≈ 5 h 20 min` when fully known, `at least 5 h 20 min
  (10 min unknown)` otherwise.
- `app`: while the day is still being computed, no heatmap of that day is shown. The notice
  `Computing sun hours …` and the existing progress bar are shown instead (user decision; a
  partial or preliminary heatmap is deferred until the device measurement shows that one is
  needed).

## Capabilities

### New Capabilities

- `sun-exposure-heatmap`: the hours of sun per cell of the visible area on the selected day, the
  mode control, the colour scale and legend, unknown steps, the panel line, and the behaviour while
  the day is computed.

### Modified Capabilities

- `sun-shade-overlay`: "Overlay appearance" and "Overlay updates" now apply to the `Sun & shade`
  mode only, because the heatmap mode has its own legend, notice and update rules.

## Non-goals

- A partial or preliminary heatmap while the day is computed (user decision: revisit only if the
  device measurement finds the day too slow).
- Hours of sun over several days, a season or a year.
- A heatmap for a region beyond the visible area, or below map zoom 11.
- Reusing work across pans (the polishing item from #5). A pan still computes the day again for
  the new area.
- Keeping computed heatmaps across app restarts.
- Downloading regions for offline use (roadmap #6).
- Accuracy finer than the 5-minute slider steps. The panel's tracer line stays the precise value.

## Impact

- **Code:**
  - `app`: heatmap raster and image (sun and unknown counts per pixel, the colour bands), built
    from a finished `DayOverlay` and kept with it; overlay mode and heatmap state in
    `MapViewModel`; the segmented control and heatmap legend in `OverlayControl`; the `Sun hours`
    line in `SunPanel`; strings.
  - `core`: none expected. The heatmap reuses `ShadeGrid.stateAt` and `SunDay.dayLength`.
- **Memory:** about 1.4 MB per cached day for the counts at map zoom 12 on a phone (two 16-bit
  counters per pixel), counted in the existing day-cache budget.
- **CPU:** one pass over the day's grids after its last step. Unmeasured on the phone; the budget
  is ≤ 2 s (design). No extra terrain work.
- **Network:** none beyond the overlay's day.
- **Dependencies:** none new.
- **Tests:** JVM unit tests for the counting, the bands and colours, the panel text and the view
  model's mode and notice rules; a device check with a measurement of the whole day and of the
  raster pass.
- **Docs:** `docs/roadmap.md` (entry #7 and the order of #6 and #7) and `CLAUDE.md` (new `app`
  file).
