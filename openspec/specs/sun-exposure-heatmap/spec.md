# sun-exposure-heatmap Specification

## Purpose

Shows on the map, for the whole visible area, how many hours of direct sun each place gets on the
selected day, taking terrain into account. Sun that cannot be decided is marked, never guessed.

## Requirements

### Requirement: Overlay mode
While the overlay is on, it SHALL show one of two modes, selected with the overlay toggle
(sun-shade-overlay "Overlay toggle"):
- `Sun & shade` shows the overlay of the selected time (sun-shade-overlay).
- `Sun hours` shows the heatmap of the selected day (this capability).

Each mode computes its own day: `Sun & shade` the day of sun-shade-overlay "Overlay of the whole
day", `Sun hours` the coarser day of "Sun hours of a cell" (user decision, 2026-09-29, after the
device check found the heatmap far too slow on the shared day). Only the day of the mode shown
SHALL be computed; the other SHALL pause. Switching modes SHALL NOT discard the computed steps of
either day; switching back SHALL resume a day where it paused. Once the heatmap of the visible area
and selected day has been built and the overlay of the selected time is known, switching to either
mode SHALL show that mode within 100 ms.

#### Scenario: Switch back and forth
- **WHEN** the heatmap of the visible area and selected day has been built, the overlay of the selected time is known, and the user switches to `Sun & shade` and back to `Sun hours`
- **THEN** each mode is shown within 100 ms, and neither day is computed again

#### Scenario: Switch while computing
- **WHEN** the `Sun & shade` day is being computed and the user switches to `Sun hours`
- **THEN** the `Sun & shade` day pauses, the heatmap's day is computed, and the heatmap is shown once that day is complete

#### Scenario: Resume after switching back
- **WHEN** the `Sun & shade` day paused at 100 of 288 slider positions, and the user switches back from `Sun hours` to `Sun & shade`
- **THEN** the 100 computed positions are not computed again, and only the other 188 are

### Requirement: Sun hours of a cell
The heatmap SHALL divide the visible map area into cells no larger than the `Sun hours` cell size of the
shade resolution (settings "Shade resolution"): 8 × 8 dp with `Normal` (about 105 m at map zoom
12 in the Alps), 4 × 4 dp to 32 × 32 dp in all. Each cell's state at an instant (sun, shade or unknown) follows the rules
of sun-shade-overlay "Sunshine of a cell" and "Unknown cells", with these cells instead of the
overlay's cells. The sun hours SHALL be counted over the heatmap's steps: every `Sun hours`
step of the shade resolution (10 minutes with `Normal`) from the start of the selected day, over
its actual length. For each cell:
- **sun hours** = the number of steps at which the cell is sun, × the step;
- **unknown time** = the number of steps at which the cell is unknown, × the step.

The sun hours are therefore a lower bound: the true value lies between the sun hours and the sun
hours plus the unknown time. A time that is not one of these steps (e.g. after `Now`) SHALL NOT be
counted.

Accuracy: each start and end of a sun period is counted to the nearest step, so where the whole day
is known, the sun hours SHALL be within one step × the number of period boundaries of the sum of the
cell's sun periods (point-sunshine "Sun periods of the selected day", evaluated at the cell's
sample point with the sun position of its computed area at each step, sun-shade-overlay "Sunshine
of a cell"). The heatmap's day reuses an earlier day after a camera move like the overlay's day
(sun-shade-overlay "Overlay of the whole day").

#### Scenario: Counting steps
- **WHEN** the shade resolution is `Normal` and a cell is sun at 29 steps, unknown at 1 step and shade at the other 114 steps of a 24-hour day (144 steps)
- **THEN** its sun hours are 4 h 50 min and its unknown time is 10 min

#### Scenario: Winter day in Interlaken
- **WHEN** the shade resolution is `Normal`, the map centre is 46.6863° N, 7.8632° E, the map zoom is 12, the selected date is 2025-12-21, and the whole day is known
- **THEN** the sun hours of the cell containing the map centre are 5 h 23 min ± 40 min (the tracer's periods 10:09–14:51 and 15:11–15:52 have 4 boundaries)

#### Scenario: Short day
- **WHEN** the shade resolution is `Normal` and the selected date is 2025-03-30 in Europe/Zurich (138 steps) and a cell is sun at every step
- **THEN** its sun hours are 23 h 0 min

#### Scenario: Cell size
- **WHEN** the shade resolution is `Normal`, the map zoom is 12 and the visible area is 400 × 850 dp
- **THEN** the heatmap's cells are at most 8 × 8 dp: about 50 × 107 cells, plus at most one cell of margin on each side

#### Scenario: Ground height unknown
- **WHEN** the ground height at a cell's sample point cannot be obtained
- **THEN** the cell is unknown at every step: its sun hours are 0 h 0 min and its unknown time is the whole day

#### Scenario: Counting steps of Fast
- **WHEN** the shade resolution is `Fast` and a cell is sun at 20 steps and shade at the other 76 steps of a 24-hour day (96 steps)
- **THEN** its sun hours are 5 h 0 min and its unknown time is 0 min

#### Scenario: Winter day in Interlaken with Fast
- **WHEN** the shade resolution is `Fast`, the map centre is 46.6863° N, 7.8632° E, the map zoom is 12, the selected date is 2025-12-21, and the whole day is known
- **THEN** the sun hours of the cell containing the map centre are 5 h 23 min ± 60 min (4 boundaries of 15 min)

#### Scenario: Heatmap after a half-screen pan
- **WHEN** the heatmap of 2025-12-21 has been built and the user pans by half a screen
- **THEN** only the uncovered half of the new area is computed at the daytime steps, and the new heatmap covers the whole new area

### Requirement: Heatmap coverage and zoom range
In `Sun hours` mode, while the overlay is on and the map zoom is 11 or more, the app SHALL show
the heatmap over every cell of the visible map area once it has been built. It SHALL NOT limit the
number of cells or leave part of the visible area out because of the amount of work. Below map
zoom 11, no heatmap SHALL be shown, and the notice of sun-shade-overlay "Overlay coverage and zoom
range" (`Zoom in to see sun and shade`) SHALL be shown.

#### Scenario: Whole screen covered
- **WHEN** the mode is `Sun hours`, the map zoom is 12 and the heatmap has been built
- **THEN** every point of the visible map area lies in a cell that is coloured, hatched, or both

#### Scenario: Zoomed out
- **WHEN** the mode is `Sun hours` and the map zoom is 10.5
- **THEN** no heatmap is drawn and the notice `Zoom in to see sun and shade` is visible

### Requirement: Colour scale
The heatmap SHALL colour each cell by its sun hours, in bands of 30 minutes: band i holds the sun
hours from i × 30 min up to, but not including, (i + 1) × 30 min. The scale SHALL run from 0 h to
the day's possible sun, which is the day length at the map centre on the selected date (sun-position
"Day length"). It therefore has n = max(1, ⌈day length / 30 min⌉) bands. A cell whose sun hours
reach or exceed the day length SHALL be in the last band.

The colours SHALL:
- run through four stops, from dark slate grey for the first band (the shade tint of
  sun-shade-overlay "Overlay appearance") through blue and yellow to light amber for the last band;
- rise strictly in OKLab lightness from each band to the next;
- be translucent, all with the chosen `Overlay opacity` (sun-shade-overlay "Overlay appearance"),
  so that the topographic map, its paths, labels and
  contour lines stay readable through them.

Every cell with at least one step not unknown SHALL be coloured; no part of the scale SHALL be left
clear.

#### Scenario: Bands on a winter day
- **WHEN** the day length at the map centre is 8 h 33 min
- **THEN** the scale has 18 bands, a cell with 5 h 20 min of sun is in band 10 (5 h 0 min to 5 h 30 min), and a cell with 8 h 40 min is in band 17, the last

#### Scenario: Bands on a summer day
- **WHEN** the day length at the map centre is 15 h 51 min
- **THEN** the scale has 32 bands

#### Scenario: No sun at all
- **WHEN** a cell's sun hours are 0 h 0 min and not every step is unknown
- **THEN** it is coloured with the first band's colour, dark slate grey

#### Scenario: Polar night
- **WHEN** the day length at the map centre is 0 h 0 min
- **THEN** the scale has one band, and every cell whose ground height is known is coloured with it

#### Scenario: Lightness rises
- **WHEN** the scale has 32 bands
- **THEN** each band's colour has a higher OKLab lightness than the band before it

#### Scenario: Opacity of the bands
- **WHEN** `Overlay opacity` is 80 %
- **THEN** every band colour on the map is drawn at 80 % opacity (±1 %), and the legend's band colours stay opaque

### Requirement: Unknown time in the heatmap
A cell with unknown time SHALL be drawn with the grey diagonal hatching of sun-shade-overlay
"Overlay appearance" on top of the colour of its sun hours. A cell that is unknown at every step
SHALL be drawn with the hatching only, without a colour. The app SHALL NOT colour a cell by any
estimate of its unknown steps.

#### Scenario: Some steps unknown
- **WHEN** a cell's sun hours are 4 h 50 min and its unknown time is 10 min
- **THEN** it is drawn in the colour of band 9 with the hatching on top

#### Scenario: Every step unknown
- **WHEN** the device is offline and the DEM tiles of the visible area have never been loaded
- **THEN** every cell is drawn with the hatching only

### Requirement: Heatmap legend
In `Sun hours` mode, while the overlay is on, the legend in the status card (sun-shade-overlay
"Overlay status card") SHALL show:
- the colour scale from 0 h to the day's possible sun, its band colours drawn opaque;
- below it, labels every whole 2 hours from 0 at the band where each begins, as numbers, with the
  unit `h` after the last one only (e.g. `0`, `2`, `4`, `6`, `8 h` for a day length of 8 h 33 min),
  so that no labels overlap;
- the hatching labelled `Unknown`.

#### Scenario: Winter legend
- **WHEN** the mode is `Sun hours` and the day length at the map centre is 8 h 33 min
- **THEN** the legend shows the scale with the labels `0`, `2`, `4`, `6` and `8 h`, and `Unknown` with the hatching

#### Scenario: Summer legend
- **WHEN** the mode is `Sun hours` and the day length at the map centre is 15 h 51 min
- **THEN** the scale's labels are `0`, `2`, `4`, `6`, `8`, `10`, `12` and `14 h`, and none of them overlap

#### Scenario: Opaque colours
- **WHEN** the legend is shown
- **THEN** every band colour in it is fully opaque

### Requirement: Heatmap updates
The heatmap's day SHALL be computed in the background, like the day of sun-shade-overlay "Overlay
of the whole day" (the same CPU limit, stop and resume, and cache of days, kept apart from the
`Sun & shade` day). The heatmap SHALL be built off the main thread once every step of its day has
been computed; the map SHALL stay responsive meanwhile.
No heatmap of an area and date SHALL be shown before it is built; the app SHALL NOT show a partial
heatmap.

While the heatmap of the visible area and selected date is not built, in `Sun hours` mode:
- the notice `Computing sun hours …` SHALL be shown in the status card (sun-shade-overlay "Overlay
  status card"), with the progress of the heatmap's day directly below it;
- after a camera move, the previous heatmap SHALL stay on its geographic area until the new one is
  built; newly visible areas stay untinted meanwhile;
- after a change of the selected date, the previous heatmap SHALL stay until the new one is built.

A change of the selected time within the day SHALL NOT change the heatmap. When a day is computed
anew (sun-shade-overlay "Overlay of the whole day", "Cache of days"), its heatmap SHALL be built
again once the day is complete. A built heatmap SHALL be kept with its day in the cache of days.

#### Scenario: Switching on in heatmap mode
- **WHEN** the mode is `Sun hours`, the user switches the overlay on, and 36 of the heatmap day's 144 steps are computed
- **THEN** no heatmap is drawn, the status card shows `Computing sun hours …` and directly below it the progress bar at 25 %

#### Scenario: Day complete
- **WHEN** the last step of the heatmap's day has been computed
- **THEN** the heatmap is built and drawn, and the notice and the progress bar disappear

#### Scenario: Time change
- **WHEN** the heatmap is shown and the user moves the time slider
- **THEN** the heatmap stays unchanged, without a notice

#### Scenario: Pan
- **WHEN** the heatmap is shown and the user pans the map by half a screen
- **THEN** the previous heatmap stays aligned with the terrain it was built for, and `Computing sun hours …` is shown until the heatmap of the new area replaces it

#### Scenario: Date change
- **WHEN** the heatmap is shown and the user picks another date whose day has not been computed
- **THEN** the previous heatmap stays and `Computing sun hours …` is shown until the heatmap of the new date replaces it

#### Scenario: Switching back to a computed day
- **WHEN** the heatmap of 2025-12-21 has been built, and the user picks 2025-12-22 and then 2025-12-21 again
- **THEN** the heatmap of 2025-12-21 is shown within 100 ms, without `Computing sun hours …` and without computing the day again

### Requirement: Sun hours in the information panel
In `Sun hours` mode, while the overlay is on and the map zoom is 11 or more, the sun information
panel (sun-position "Sun information panel") SHALL show a `Sun hours` line directly below the
`Sunshine` line (point-sunshine "Sunshine in the information panel"). It gives the value of the
cell under the crosshair from the heatmap of the visible area and selected date:
- `≈ <duration>` when the cell's unknown time is 0, e.g. `≈ 5 h 20 min`;
- `at least <duration> (<duration> unknown)` when the cell has unknown time but is not unknown at
  every step, e.g. `at least 5 h 20 min (10 min unknown)`;
- `unknown` when the cell is unknown at every step;
- `…` while that heatmap is not built.

A duration is written `<h> h <m> min`, e.g. `5 h 20 min` or `0 h 0 min`, except an unknown time
under one hour, which is written `<m> min`, e.g. `10 min`. Numbers SHALL be formatted independently
of the device locale. In `Sun & shade` mode, while the overlay is off or below map zoom 11, the
line SHALL NOT be shown.

#### Scenario: Fully known
- **WHEN** the cell under the crosshair has sun hours 5 h 20 min and no unknown time
- **THEN** the panel shows `Sun hours ≈ 5 h 20 min`

#### Scenario: Partly unknown
- **WHEN** the cell under the crosshair has sun hours 5 h 20 min and an unknown time of 10 min
- **THEN** the panel shows `Sun hours at least 5 h 20 min (10 min unknown)`

#### Scenario: Long unknown time
- **WHEN** the cell under the crosshair has sun hours 2 h 0 min and an unknown time of 1 h 20 min
- **THEN** the panel shows `Sun hours at least 2 h 0 min (1 h 20 min unknown)`

#### Scenario: Wholly unknown
- **WHEN** the cell under the crosshair is unknown at every step
- **THEN** the panel shows `Sun hours unknown`

#### Scenario: Still computing
- **WHEN** the heatmap of the visible area and selected date is not built yet
- **THEN** the panel shows `Sun hours …`

#### Scenario: Other mode
- **WHEN** the mode is `Sun & shade`
- **THEN** the panel shows no `Sun hours` line
