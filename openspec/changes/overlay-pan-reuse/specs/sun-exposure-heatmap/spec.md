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
