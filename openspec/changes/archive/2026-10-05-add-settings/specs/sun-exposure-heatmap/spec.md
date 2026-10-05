# Spec Delta

## MODIFIED Requirements

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
sample point with the map centre's sun position).

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
