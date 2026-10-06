## MODIFIED Requirements

### Requirement: Unknown cells
When terrain data that a cell's horizon needs cannot be obtained, the cell's horizon SHALL be
incomplete, with a lower and an upper bound as in terrain-horizon "Incomplete horizon": the lower
bound is the largest angle found, including terrain beyond the missing data; the upper bound is
the largest angle at which terrain at the height bound could appear at the distance of the
nearest missing data or beyond. The bound is Mont Blanc's 4810 m in Europe west of the Caucasus
and 8849 m elsewhere, as for the point tracer. The horizon is still complete when no terrain
within the missing area, however high, could rise above the angle found. The cell's state follows
point-sunshine "Sunshine at an instant": sun above the upper bound, shade at or below the lower
bound, unknown in between. When the ground height at the sample point itself cannot be obtained,
the cell is unknown. The app SHALL NOT assume flat terrain, height 0 m or any other default height
for missing data.

#### Scenario: Missing tile that could matter
- **WHEN** a cell's eye is at 500 m, the terrain towards the sun rises to at most 2° within 10 km, a DEM tile 20 km towards the sun is unavailable, and the sun's upper edge is at 5°
- **THEN** the cell is unknown, because terrain 20 km away could rise to 12.09°

#### Scenario: Missing tile that cannot matter
- **WHEN** the same cell has the sun's upper edge at 20°
- **THEN** the cell is sun

#### Scenario: Sun below the lower bound
- **WHEN** the same cell has the sun's upper edge at 1.5°
- **THEN** the cell is shade

#### Scenario: Higher eye, same gap
- **WHEN** a cell's eye is at 2500 m, the terrain towards the sun rises to at most 2° within 10 km, a DEM tile 20 km towards the sun is unavailable, and the sun's upper edge is at 8°
- **THEN** the cell is sun, because terrain 20 km away could rise to at most 6.51° (±0.05°) as seen from it

#### Scenario: Offline, never visited
- **WHEN** the device is offline and the DEM tiles of the visible area have never been loaded
- **THEN** every cell is unknown

### Requirement: Overlay updates
The overlay SHALL be computed off the main thread; the map SHALL stay responsive while it is
computed. It SHALL be recomputed, in either mode:
- when the camera has rested for 300 ms after a move;
- when the selected date changes to a day that is not in the cache of days ("Overlay of the
  whole day");
- when the network connection returns while some cell is unknown.

A change of the selected time within the selected day SHALL show that time's overlay if it has
already been computed ("Overlay of the whole day"); otherwise that time SHALL be computed next. A
newer trigger SHALL replace a computation still running. In the mode `Sun & shade`, while a new
overlay is being computed:
- **After a camera move**, the previous overlay stays on its geographic area until the new one is
  ready. Newly visible areas stay untinted meanwhile.
- **After a change of the selected time or date**, if the day of the visible area has not computed
  that time yet, but an earlier computed day of the same date, cell size and step whose area
  overlaps the visible area has it (an earlier day in the cache of days, e.g. the day before a
  pan), that day's overlay of the selected time SHALL be shown on its own geographic area within
  100 ms, without a notice, until the visible area's own overlay of that time replaces it. Parts
  of the visible area outside that day's area stay untinted meanwhile. Of several such days, the
  one used most recently is shown.
- **Otherwise, after a change of the selected time or date**, the previous overlay also stays until
  the new one is ready. Meanwhile the notice `Computing sun and shade …` SHALL be shown, because
  the overlay on screen belongs to another time.

In the mode `Sun hours`, the heatmap's own update rules and notice apply instead
(sun-exposure-heatmap "Heatmap updates"); `Computing sun and shade …` SHALL NOT be shown.

#### Scenario: Pan
- **WHEN** the overlay is on in the mode `Sun & shade` and the user pans the map by half a screen
- **THEN** the previous overlay stays aligned with the terrain it was computed for, and the new overlay replaces it after the camera has rested for 300 ms and the computation has finished

#### Scenario: Scrubbing after a pan
- **WHEN** the overlay is on in the mode `Sun & shade` at map zoom 12, the day of 2025-12-21 has been computed, the user pans the map by half a screen, and right after the camera has rested moves the time slider to 14:35, which the new area's day has not computed yet
- **THEN** the overlay of 14:35 of the day before the pan is drawn on its own terrain within 100 ms, without `Computing sun and shade …`, and the overlay of the new area at 14:35 replaces it once computed

#### Scenario: No earlier day covers the view
- **WHEN** the overlay is on in the mode `Sun & shade`, the user pans to an area that overlaps no computed day of the selected date, and moves the time slider to a time the new area's day has not computed yet
- **THEN** the previous overlay stays and `Computing sun and shade …` is shown until the overlay of that time replaces it

#### Scenario: Time change to a time not yet computed
- **WHEN** the overlay is on in the mode `Sun & shade`, shows 12:00, no earlier day of that date overlaps the visible area, and the user moves the time slider to 15:00 before 15:00 has been computed
- **THEN** the overlay for 12:00 stays and `Computing sun and shade …` is shown until the overlay for 15:00 replaces it

#### Scenario: Time change to a time already computed
- **WHEN** the overlay is on in the mode `Sun & shade` and the user moves the time slider to a time of the selected day whose overlay has been computed
- **THEN** that overlay is drawn within 100 ms, without the notice

#### Scenario: Date change
- **WHEN** the overlay is on in the mode `Sun & shade` and the user picks another date whose day has not been computed for any area overlapping the visible area
- **THEN** the previous overlay stays and `Computing sun and shade …` is shown until the overlay for the new date and time replaces it

#### Scenario: Slider dragged continuously
- **WHEN** the user drags the time slider across many positions while the overlay is on in the mode `Sun & shade`
- **THEN** the overlay drawn when the drag ends is the one for the final slider position

#### Scenario: Connectivity returns
- **WHEN** some cells are unknown because the device was offline and the network connection returns
- **THEN** the overlay is recomputed without the user moving the map or changing the time

#### Scenario: Time change in heatmap mode
- **WHEN** the overlay is on in the mode `Sun hours` and the user moves the time slider to a time not yet computed
- **THEN** `Computing sun and shade …` is not shown
