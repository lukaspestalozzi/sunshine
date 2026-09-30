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

- `app`: a second mode of the sun-shade overlay (user decision). A three-way icon toggle selects
  `Off`, `Sun & shade` (today's overlay at the selected time) or `Sun hours` (the heatmap of the
  selected day). It replaces the on/off chip (user decision, 2026-09-29, after the first device
  screenshot showed the chip, a segmented control, the progress bar, the legend and the notice
  crowding and overlapping each other).
- `app`: below the toggle, one status card of the same width holds the mode's name, its notice
  (`Computing …`, `Zoom in …`), directly below it the day's progress bar, and the mode's legend in
  opaque colours (user decisions, 2026-09-29).
- `app`: `Sun hours` computes its own, coarser day: cells of at most 8 × 8 dp and steps every 10
  minutes, instead of waiting for the 2 dp / 5-minute `Sun & shade` day (user decision,
  2026-09-29, after the device check found the heatmap far too slow). Only the shown mode's day is
  computed; the other pauses and resumes.
- `app`: under the heatmap (`Sun hours`), the map is shown in greyscale, and the overlay is more opaque
  (0.6 instead of 0.45), so that it no longer blends into the map's colours (user decision,
  2026-09-29).
- `app`: the attributions leave the map. An ⓘ button in the map's top-left corner opens an About
  page with the map and elevation attributions, their links and the app version (user decision,
  2026-09-29).
- `app`: the heatmap colours every cell of the visible area by its hours of sun on the selected
  day: the number of 10-minute steps at which the cell is sun, times 10 minutes (since the revision of 2026-09-29; 5-minute slider steps before). It is built from the
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
  mode only, because the heatmap mode has its own legend, notice and update rules. The on/off
  control becomes the three-way toggle ("Overlay toggle"), the progress bar moves into the new
  status card ("Overlay of the whole day"), and "Overlay status card" is added.
- `map-view`: "Map attribution" is replaced by "About and attributions" (the ⓘ button and the
  About page), and "Missing map tiles" checks for the ⓘ button instead of the attribution.
  "Map colours under the heatmap" is added (greyscale map in `Sun hours`).
- `sun-position`: "Sun information panel" must not cover the ⓘ button instead of the attribution.

## Non-goals

- A partial or preliminary heatmap while the day is computed (user decision: revisit only if the
  device measurement finds the day too slow).
- Hours of sun over several days, a season or a year.
- A heatmap for a region beyond the visible area, or below map zoom 11.
- Reusing work across pans (the polishing item from #5). A pan still computes the day again for
  the new area.
- Keeping computed heatmaps across app restarts.
- Downloading regions for offline use (roadmap #6).
- Accuracy finer than the heatmap's 10-minute steps and 8 dp cells. The panel's tracer line stays
  the precise value; `Sun & shade` keeps 2 dp cells and 5-minute steps.
- Showing the attributions on the map at launch before collapsing them (user decision,
  2026-09-29). The OpenStreetMap Foundation's attribution guidelines allow collapsed attribution
  reachable from an "(i)" button or an About option; their collapse options (dismiss, map
  interaction, after five seconds) suggest an initial display. That risk is accepted.

## Impact

- **Code:**
  - `app`: heatmap raster and image (sun and unknown counts per pixel, the colour bands), built
    from a finished `DayOverlay` and kept with it; overlay mode and heatmap state in
    `MapViewModel`; the three-way toggle, the status card and the legends in `OverlayControl`; the
    `Sun hours` line in `SunPanel`; the ⓘ button in `MapLabels` and a new About screen, switched in
    `MainActivity` without a navigation library; icons as vector drawables; strings.
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
