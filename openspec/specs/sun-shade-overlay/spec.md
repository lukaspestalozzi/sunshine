# sun-shade-overlay Specification

## Purpose

Shows on the map, for the whole visible area, where the sun shines and where terrain casts shade
at the selected time. Cells whose state cannot be decided are shown as unknown, never guessed.

## Requirements

### Requirement: Sunshine of a cell
The visible map area SHALL be divided into cells no larger than 2 × 2 dp. Every cell SHALL have a
sunshine state, decided at the cell's sample point, which lies within the cell. The state follows
the rule of point-sunshine "Sunshine at an instant":
- **sun:** the sun's upper edge is above the terrain horizon towards the sun;
- **shade:** it is at or below that horizon;
- **unknown:** the horizon is incomplete there and the upper edge is above its lower bound.

The sun's azimuth and apparent elevation are those of the map centre at the selected time; the
same sun position applies to every cell. The terrain horizon of a sample point is the horizon of
terrain-horizon "Horizon profile" in that one direction: the same eye height, earth curvature,
refraction and 150 km range. Only the data resolution differs:
- zoom z_v = min(14, ⌊map zoom⌋ + 2) within the visible area and up to 1.5 km beyond its edge
  towards the sun;
- then zoom 12 (or z_v if coarser) up to 6 km, zoom 11 up to 25 km and zoom 10 beyond, with
  distances measured from the edge of the visible area;
- the next coarser published zoom where a zoom is not published.

Terrain more than 6 km from a sample point may be taken from a line offset sideways by at most
d · tan(0.125°), where d is its distance. This is at most the error of the point profile's
0.25° azimuth bins.

Accuracy: at map zoom ≥ 12, for at least 99.5 % of the cells, the state SHALL equal point-sunshine
"Sunshine at an instant" evaluated at the cell's sample point with the map centre's sun position.
At the foot of cliffs, results vary within a cell. The spike measured up to p90 25 min per day of
different state between two points 6.5 m apart.

#### Scenario: Shadow of a ridge
- **WHEN** a plain at 500 m has an east–west ridge whose crest is 1000 m above the plain, and the sun's upper edge is at 20° elevation in azimuth 180°
- **THEN** the cells on the plain north of the crest are shade up to 2741 m from the crest and sun beyond it (±1 cell)

#### Scenario: Earth curvature
- **WHEN** the only terrain above a cell's eye is a peak 4000 m higher, 100 km away towards the sun
- **THEN** the cell is shade while the sun's upper edge is at 1.80° elevation and sun at 2.00°, as the horizon angle is 1.90° (±0.05°)

#### Scenario: Afternoon shade in Interlaken
- **WHEN** the map centre is 46.6863° N, 7.8632° E and the selected time is 2025-12-21 15:00 UTC+1
- **THEN** the cell containing the map centre is shade

#### Scenario: Winter midday sun in Interlaken
- **WHEN** the map centre is 46.6863° N, 7.8632° E and the selected time is 2025-12-21 12:00 UTC+1
- **THEN** the cell containing the map centre is sun

#### Scenario: Night
- **WHEN** the map centre is 46.6863° N, 7.8632° E and the selected time is 2025-12-21 02:00 UTC+1
- **THEN** every cell of the visible area is shade

#### Scenario: Agreement with the point tracer
- **WHEN** the map centre is Lauterbrunnen, 46.5935° N, 7.9091° E, the map zoom is 12, and the selected time is 2025-12-21 15:00 UTC+1
- **THEN** at least 99.5 % of the cells have the state that point-sunshine gives at their sample points

### Requirement: Unknown cells
When terrain data that a cell's horizon needs cannot be obtained, the cell's horizon SHALL be
incomplete. Its lower bound is the largest angle found, as in terrain-horizon "Incomplete
horizon". The horizon is still complete when no terrain within the missing area, however high,
could rise above the angle found. The bound is Mont Blanc's 4810 m in Europe west of the Caucasus
and 8849 m elsewhere, as for the point tracer. When the ground height at the sample point itself
cannot be obtained, the cell is unknown. The app SHALL NOT assume flat terrain, height 0 m or any
other default height for missing data.

#### Scenario: Missing tile that could matter
- **WHEN** a cell's eye is at 500 m, the terrain towards the sun rises to at most 2° within 10 km, a DEM tile 20 km towards the sun is unavailable, and the sun's upper edge is at 5°
- **THEN** the cell is unknown, because terrain 20 km away could rise to 12.09°

#### Scenario: Missing tile that cannot matter
- **WHEN** the same cell has the sun's upper edge at 20°
- **THEN** the cell is sun

#### Scenario: Sun below the lower bound
- **WHEN** the same cell has the sun's upper edge at 1.5°
- **THEN** the cell is shade

#### Scenario: Offline, never visited
- **WHEN** the device is offline and the DEM tiles of the visible area have never been loaded
- **THEN** every cell is unknown

### Requirement: Overlay coverage and zoom range
While the overlay is switched on and the map zoom is 11 or more, the app SHALL show every cell of
the visible map area. It SHALL NOT limit the number of cells or leave part of the visible area out
because of the amount of work. Below map zoom 11 the overlay SHALL NOT be shown, and a notice SHALL
read `Zoom in to see sun and shade`.

#### Scenario: Whole screen covered
- **WHEN** the overlay is on, the map zoom is 12 and the computation has finished
- **THEN** every point of the visible map area lies in a cell that is shown as sun, shade or unknown

#### Scenario: Zoomed out
- **WHEN** the overlay is on and the map zoom is 10.5
- **THEN** no overlay is drawn and the notice `Zoom in to see sun and shade` is visible

### Requirement: Overlay appearance
The overlay SHALL draw:
- shade cells with a translucent dark blue-grey tint through which the map stays readable;
- sun cells without a tint;
- unknown cells with grey diagonal hatching.

It SHALL NOT cover the crosshair, the sun direction line, the sun information panel or the map
attributions. While the overlay is on, a legend SHALL show the shade tint labelled `Shade` and
the hatching labelled `Unknown`.

#### Scenario: Legend
- **WHEN** the overlay is switched on
- **THEN** a legend with `Shade` and `Unknown` is visible

#### Scenario: Crosshair stays visible
- **WHEN** the cell under the crosshair is shade
- **THEN** the crosshair and the sun direction line are drawn above the tint

### Requirement: Overlay toggle
A map control SHALL switch the overlay on and off. The overlay SHALL be off when the app is
launched. While the app process is alive, including across screen rotation, the on/off state
SHALL be preserved. While the overlay is off, no overlay computation and no DEM tile request for
it SHALL take place.

#### Scenario: Off at launch
- **WHEN** the app is launched
- **THEN** the overlay is off and no overlay is drawn

#### Scenario: Switch on
- **WHEN** the map zoom is 12 and the user switches the overlay on
- **THEN** the overlay for the visible area is computed and drawn

#### Scenario: Screen rotation
- **WHEN** the overlay is on and the device is rotated
- **THEN** the overlay is still on

### Requirement: Overlay updates
The overlay SHALL be computed off the main thread; the map SHALL stay responsive while it is
computed. It SHALL be recomputed:
- when the camera has rested for 300 ms after a move;
- when the selected date changes to a day that is not in the cache of days ("Overlay of the
  whole day");
- when the network connection returns while some cell is unknown.

A change of the selected time within the selected day SHALL show that time's overlay if it has
already been computed ("Overlay of the whole day"); otherwise that time SHALL be computed next. A
newer trigger SHALL replace a computation still running. While a new overlay is being computed:
- **After a camera move**, the previous overlay stays on its geographic area until the new one is
  ready. Newly visible areas stay untinted meanwhile.
- **After a change of the selected time or date**, the previous overlay also stays until the new
  one is ready. Meanwhile the notice `Computing sun and shade …` SHALL be shown, because the
  overlay on screen belongs to another time.

#### Scenario: Pan
- **WHEN** the overlay is on and the user pans the map by half a screen
- **THEN** the previous overlay stays aligned with the terrain it was computed for, and the new overlay replaces it after the camera has rested for 300 ms and the computation has finished

#### Scenario: Time change to a time not yet computed
- **WHEN** the overlay is on, shows 12:00, and the user moves the time slider to 15:00 before 15:00 has been computed
- **THEN** the overlay for 12:00 stays and `Computing sun and shade …` is shown until the overlay for 15:00 replaces it

#### Scenario: Time change to a time already computed
- **WHEN** the overlay is on and the user moves the time slider to a time of the selected day whose overlay has been computed
- **THEN** that overlay is drawn within 100 ms, without the notice

#### Scenario: Date change
- **WHEN** the overlay is on and the user picks another date whose day has not been computed
- **THEN** the previous overlay stays and `Computing sun and shade …` is shown until the overlay for the new date and time replaces it

#### Scenario: Slider dragged continuously
- **WHEN** the user drags the time slider across many positions while the overlay is on
- **THEN** the overlay drawn when the drag ends is the one for the final slider position

#### Scenario: Connectivity returns
- **WHEN** some cells are unknown because the device was offline and the network connection returns
- **THEN** the overlay is recomputed without the user moving the map or changing the time

### Requirement: Overlay of the whole day
Once the overlay of the selected time is ready, the app SHALL compute in the background the overlay
of the visible area for every slider position of the selected day (5-minute steps from the start
of the day, over its actual length; time-selection "Choose the time of day"):
- **Order:** nearest to the selected time first.
- **CPU:** at most half of the device's processor cores. The overlay of the selected time itself
  may use all cores.
- **Night positions:** at a position where the sun's upper edge at the map centre is below −3.5°,
  every cell SHALL be shade where its ground height is known and unknown where it is not, without
  terrain computation. Every terrain horizon within 150 km of an eye at most 4812 m high, over
  ground at least 1000 m below sea level, lies above −2.9°, so this is exact.
- **Stop and resume:** only the day of the selected date and the visible area SHALL be computed.
  The computation SHALL stop when the overlay is switched off, another date is selected or the
  camera rests on another area. When a day is selected again, its computed positions SHALL be shown
  without being computed again, and only its missing positions SHALL be computed, the selected
  time first. A change of the selected time within the day SHALL NOT restart it; if that time has
  not been computed yet, it is computed next. Leaving the app SHALL NOT discard the computed steps;
  the computation continues in the background.
- **Cache of days:** computed days SHALL be kept by visible area and date, up to a quarter of the
  app's heap limit. Beyond it, the least recently used days SHALL be dropped, never the day being
  shown. A day with unknown cells SHALL be computed anew when it is selected while the network is
  available, and after a reconnect while it is shown.
- **Progress:** while the background computation runs, a determinate progress bar directly below
  the overlay toggle SHALL show the share of the day's slider positions already computed. It SHALL
  disappear when every position is computed or the computation stops.

#### Scenario: Scrubbing a computed day
- **WHEN** the overlay is on at Lauterbrunnen, 46.5935° N, 7.9091° E, map zoom 12, on 2025-12-21 at 12:00, and the day's computation has finished
- **THEN** moving the slider to 14:35 shows the overlay for 14:35 within 100 ms, without `Computing sun and shade …`

#### Scenario: Night positions
- **WHEN** the day of 2025-12-21 is computed for Interlaken, 46.6863° N, 7.8632° E
- **THEN** the overlay for 02:00 is shade in every cell with known ground, and no terrain beyond the visible area's own tiles is used for it

#### Scenario: A pan starts the day over
- **WHEN** the day is being computed and the user pans the map
- **THEN** after the camera has rested for 300 ms, the day is computed again for the new area, starting with the selected time

#### Scenario: Back to the app
- **WHEN** the day has been computed and the user leaves the app for a minute and returns
- **THEN** moving the slider to a time of that day shows its overlay within 100 ms, without `Computing sun and shade …`

#### Scenario: Switching back to a computed day
- **WHEN** the day of 2025-12-21 has been computed, and the user picks 2025-12-22 and then 2025-12-21 again
- **THEN** every time of 2025-12-21 is shown within 100 ms, without `Computing sun and shade …` and without computing it again

#### Scenario: Overlay switched off and on
- **WHEN** the day has been computed and the user switches the overlay off and on again
- **THEN** the overlay of the selected time is shown within 100 ms, without computing the day again

#### Scenario: A cached day with unknown cells
- **WHEN** a day was computed offline with unknown cells, another date is picked, the network returns, and that day is picked again
- **THEN** the day is computed anew

#### Scenario: Least recently used day dropped
- **WHEN** the cached days reach a quarter of the app's heap and another day is computed
- **THEN** the day used least recently is dropped, and the day being shown is kept

#### Scenario: Progress of the day
- **WHEN** the overlay is on and 72 of the day's 288 slider positions are computed
- **THEN** a progress bar below the toggle shows 25 %, and once all 288 are computed no bar is shown

#### Scenario: Responsive while computing the day
- **WHEN** the day is being computed in the background
- **THEN** the map can be panned and the time slider moved without delay
