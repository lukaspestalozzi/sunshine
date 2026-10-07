# sun-shade-overlay Specification

## Purpose

Shows on the map, for the whole visible area, where the sun shines and where terrain casts shade
at the selected time. Cells whose state cannot be decided are shown as unknown, never guessed.

## Requirements

### Requirement: Sunshine of a cell
The visible map area SHALL be divided into cells no larger than the `Sun & shade` cell size of the
shade resolution (settings "Shade resolution"): 2 × 2 dp with `Normal`, 1 × 1 dp to 8 × 8 dp in
all. Every cell SHALL have a sunshine state, decided at the cell's sample point, which lies within the cell. The state follows
the rule of point-sunshine "Sunshine at an instant":
- **sun:** the sun's upper edge is above the terrain horizon towards the sun;
- **shade:** it is at or below that horizon;
- **unknown:** the horizon is incomplete there and the upper edge is above its lower bound.

The sun's azimuth and apparent elevation are those of the centre of the computed area at the
selected time. The computed area is the visible area, or, where an earlier day is reused after a
camera move ("Overlay of the whole day"), the earlier day's area or the uncovered part a cell lies
in; one sun position applies to every cell of one computed area. Against the map centre's sun this
is equivalent to a time offset of at most 2 min (map zoom 11, the largest areas). The terrain
horizon of a sample point is the horizon of
terrain-horizon "Horizon profile" in that one direction: the same eye height, earth curvature,
refraction and 150 km range. Only the data resolution differs:
- zoom z_v = min(14, ⌊map zoom⌋ + 2), with the map zoom the computed area was computed for, within
  the computed area and up to 1.5 km beyond its edge towards the sun;
- then zoom 12 (or z_v if coarser) up to 6 km, zoom 11 up to 25 km and zoom 10 beyond, with
  distances measured from the edge of the computed area;
- the next coarser published zoom where a zoom is not published.

Terrain more than 6 km from a sample point may be taken from a line offset sideways by at most
d · tan(0.125°), where d is its distance. This is at most the error of the point profile's
0.25° azimuth bins.

Accuracy: at map zoom ≥ 12, for at least 99.5 % of the cells, the state SHALL equal point-sunshine
"Sunshine at an instant" evaluated at the cell's sample point with the sun position of its
computed area.
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

#### Scenario: Cell size of Fast
- **WHEN** the shade resolution is `Fast`, the map zoom is 12 and the visible area is 400 × 850 dp
- **THEN** the overlay's cells are at most 4 × 4 dp, and at least 99.5 % of them have the state that point-sunshine gives at their sample points

#### Scenario: Sun position of a reused part
- **WHEN** after a pan the cells of the left half of the visible area are taken from an earlier day whose area was centred half a screen further west
- **THEN** those cells are decided with the sun position of the earlier area's centre, and the other cells with that of the uncovered part's centre

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

### Requirement: Overlay coverage and zoom range
While the overlay is switched on in the mode `Sun & shade` (sun-exposure-heatmap "Overlay mode")
and the map zoom is 11 or more, the app SHALL show every cell of the visible map area. It SHALL NOT
limit the number of cells or leave part of the visible area out because of the amount of work. In
the mode `Sun hours` the heatmap is shown instead (sun-exposure-heatmap "Heatmap coverage and zoom
range"). Below map zoom 11 the overlay SHALL NOT be shown in either mode, and a notice SHALL read
`Zoom in to see sun and shade`.

#### Scenario: Whole screen covered
- **WHEN** the overlay is on in the mode `Sun & shade`, the map zoom is 12 and the computation has finished
- **THEN** every point of the visible map area lies in a cell that is shown as sun, shade or unknown

#### Scenario: Zoomed out
- **WHEN** the overlay is on and the map zoom is 10.5
- **THEN** no overlay is drawn and the notice `Zoom in to see sun and shade` is visible

#### Scenario: Heatmap mode
- **WHEN** the overlay is on in the mode `Sun hours` and the map zoom is 12
- **THEN** no sun, shade or unknown cells of the selected time are drawn

### Requirement: Overlay appearance
The overlay SHALL draw:
- shade cells with a dark blue-grey tint at the chosen `Overlay opacity` (settings "Settings
  page"; 60 % by default, 20 % to 90 %), through which the map stays readable;
- sun cells without a tint;
- unknown cells with grey diagonal hatching at the same opacity.

It SHALL NOT cover the crosshair, the sun direction line, the sun information panel or the
Settings button (settings "Settings button"). While the overlay is on in the mode `Sun & shade`, a
legend in the status card ("Overlay status card") SHALL show the shade tint labelled `Shade` and
the hatching labelled `Unknown`, drawn opaque so that they read clearly on the card, whatever the chosen opacity (user decision,
2026-10-04). In the mode
`Sun hours` the heatmap's legend is shown instead (sun-exposure-heatmap "Heatmap legend").

#### Scenario: Legend
- **WHEN** the overlay is switched on in the mode `Sun & shade`
- **THEN** a legend with `Shade` and `Unknown` is visible in the status card

#### Scenario: Legend in heatmap mode
- **WHEN** the overlay is on in the mode `Sun hours`
- **THEN** no legend entry `Shade` is visible

#### Scenario: Crosshair stays visible
- **WHEN** the cell under the crosshair is shade
- **THEN** the crosshair and the sun direction line are drawn above the tint

#### Scenario: Opacity
- **WHEN** `Overlay opacity` is 30 % and a cell is shade
- **THEN** the cell's tint is drawn at 30 % opacity (±1 %), and the `Shade` swatch in the legend is opaque

### Requirement: Overlay toggle
A three-way toggle on the map SHALL select what the overlay shows: `Off`, `Sun & shade` (the
overlay of the selected time) or `Sun hours` (the heatmap of the selected day, sun-exposure-heatmap
"Overlay mode"). Its three options SHALL be icons without text, each at least 48 × 48 dp to touch,
with the content descriptions `Overlay off`, `Sun and shade now` and `Sun hours of the day`; the
selected option SHALL be highlighted. The toggle SHALL sit in the map's top-right corner and SHALL
NOT wrap or extend beyond the screen. `Off` SHALL be selected when the app is launched. While the
app process is alive, including across screen rotation, the selection SHALL be preserved. While
`Off` is selected, no overlay computation and no DEM tile request for it SHALL take place.

#### Scenario: Off at launch
- **WHEN** the app is launched
- **THEN** `Off` is selected and no overlay is drawn

#### Scenario: Switch on
- **WHEN** the map zoom is 12 and the user selects `Sun & shade`
- **THEN** the overlay for the visible area is computed and drawn

#### Scenario: Straight to sun hours
- **WHEN** `Off` is selected and the user selects `Sun hours`
- **THEN** the day of the visible area is computed and its heatmap is shown once it is complete

#### Scenario: Screen rotation
- **WHEN** `Sun hours` is selected and the device is rotated
- **THEN** `Sun hours` is still selected

#### Scenario: Narrow screen
- **WHEN** the screen is 360 dp wide, in portrait or landscape
- **THEN** the toggle shows its three icons in one row, fully on screen

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

### Requirement: Overlay of the whole day
Once the overlay of the selected time is ready, the app SHALL compute in the background the overlay
of the visible area at every step of the selected day: every `Sun & shade` step of the shade
resolution (settings "Shade resolution"; 5 minutes with `Normal`) from the start of the day, over
its actual length. These are the time tape's steps while `Sun & shade` is shown (time-selection
"Choose the time of day"):
- **Order:** nearest to the selected time first.
- **CPU:** at most half of the device's processor cores. The overlay of the selected time itself
  may use all cores.
- **Night positions:** at a position where the sun's upper edge at the map centre is below −3.5°,
  every cell SHALL be shade where its ground height is known and unknown where it is not, without
  terrain computation. Every terrain horizon within 150 km of an eye at most 4812 m high, over
  ground at least 1000 m below sea level, lies above −2.9°, so this is exact.
- **Stop and resume:** only the day of the selected date and the visible area SHALL be computed.
  The computation SHALL stop when the overlay is switched off, another date is selected or the
  camera rests on another area. It SHALL pause while the mode `Sun hours` is shown, which computes
  its own day (sun-exposure-heatmap "Overlay mode"), and resume when `Sun & shade` is selected
  again. When a day is selected again, its computed positions SHALL be shown without being computed
  again, and only its missing positions SHALL be computed, the selected time first. A change of the selected time within the day SHALL NOT restart it; if that time has
  not been computed yet, it is computed next. Leaving the app SHALL NOT discard the computed steps;
  the computation continues in the background.
- **Reuse after a camera move:** when the camera rests on a new area, the cached day of the same
  date, cell size and step that covers the largest share of the new area SHALL be reused if it
  covers at least a quarter of it, was computed at the new map zoom or up to one level higher
  (its cells are then no larger than the cell size), and, while the network is
  available, has no unknown cells. At each daytime step the earlier day has computed, only the
  parts of the new area it does not cover SHALL be computed, and the step shows the earlier day's
  cells where it covers the new area and the new cells elsewhere. At the other steps, at night
  positions, and at steps where the earlier day's grid is itself combined from two earlier days
  already, the whole new area SHALL be computed. A reused day's grids stay in use until the new
  day is dropped; they count towards the cache of days in both days.
- **Cache of days:** computed days SHALL be kept by visible area, date, cell size and step, up to a quarter of the
  app's heap limit. Beyond it, the least recently used days SHALL be dropped, never the day being
  shown. A day with unknown cells SHALL be computed anew when it is selected while the network is
  available, and after a reconnect while it is shown.
- **Progress:** the time tape's strip (time-selection "Time tape strip") SHALL show which steps of
  the day are computed; the status card shows no progress bar ("Overlay status card").

#### Scenario: Scrubbing a computed day
- **WHEN** the overlay is on at Lauterbrunnen, 46.5935° N, 7.9091° E, map zoom 12, on 2025-12-21 at 12:00, and the day's computation has finished
- **THEN** moving the time tape to 14:35 shows the overlay for 14:35 within 100 ms, without `Computing sun and shade …`

#### Scenario: Night positions
- **WHEN** the day of 2025-12-21 is computed for Interlaken, 46.6863° N, 7.8632° E
- **THEN** the overlay for 02:00 is shade in every cell with known ground, and no terrain beyond the visible area's own tiles is used for it

#### Scenario: A pan starts the day over
- **WHEN** the day is being computed and the user pans the map
- **THEN** after the camera has rested for 300 ms, the day is computed again for the new area, starting with the selected time; where an earlier day covers part of the new area, only the rest is computed at the steps that day has

#### Scenario: Half-screen pan
- **WHEN** the day of 2025-12-21 has been computed at Lauterbrunnen, 46.5935° N, 7.9091° E, map zoom 12, and the user pans by half a screen to the east
- **THEN** at every daytime step only the eastern half of the new area is computed, and the overlay of every step covers the whole new area

#### Scenario: Pan while the day is computed
- **WHEN** 100 of the 288 steps of the day have been computed and the user pans by half a screen
- **THEN** at those 100 steps only the uncovered half of the new area is computed, and at the other daytime steps the whole new area

#### Scenario: Zoomed in
- **WHEN** the day has been computed at map zoom 12 and the user zooms in to 12.5 without panning
- **THEN** the whole day of the new area is computed, without reusing the earlier day

#### Scenario: Zoomed out
- **WHEN** the day has been computed at map zoom 12.5 and the user zooms out to 12 without panning
- **THEN** the earlier day's cells are reused where it covers the new area, and only the rest of the new area is computed

#### Scenario: Little overlap
- **WHEN** the day has been computed and the user pans by 0.8 of the screen's width, so that the earlier area covers a fifth of the new one
- **THEN** the whole day of the new area is computed

#### Scenario: Back to the app
- **WHEN** the day has been computed and the user leaves the app for a minute and returns
- **THEN** moving the time tape to a time of that day shows its overlay within 100 ms, without `Computing sun and shade …`

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
- **WHEN** the overlay is on and 72 of the day's 288 steps are computed
- **THEN** those 72 steps of the time tape's strip have their colours and the other daytime steps are not computed yet, no progress bar is shown, and once all 288 are computed every step has its colour

#### Scenario: Responsive while computing the day
- **WHEN** the day is being computed in the background
- **THEN** the map can be panned and the time tape moved without delay

#### Scenario: Day of Fast
- **WHEN** the shade resolution is `Fast` and the overlay is on in the mode `Sun & shade` on 2025-12-21 in Europe/Zurich
- **THEN** the day's overlay is computed at 144 steps, 10 minutes apart, matching the time tape's 144 steps, each of which takes its colour on the strip once computed

### Requirement: Overlay status card
While `Sun & shade` or `Sun hours` is selected, a status card SHALL be shown directly below the
overlay toggle, exactly as wide as the toggle and aligned with its right edge. From top to bottom
it SHALL hold:
1. the name of the selected mode, `Sun & shade` or `Sun hours`;
2. the mode's notice, if any: `Zoom in to see sun and shade` ("Overlay coverage and zoom range"),
   `Computing sun and shade …` ("Overlay updates") or `Computing sun hours …`
   (sun-exposure-heatmap "Heatmap updates");
3. the mode's legend ("Overlay appearance", sun-exposure-heatmap "Heatmap legend").

The card SHALL show no progress bar: the progress of the selected mode's day is shown on the time
tape (time-selection "Time tape strip"; user decision, 2026-10-05). These notices SHALL appear only
in the status card. While `Off` is selected, no status card SHALL
be shown. The card SHALL NOT cover the crosshair, the sun information panel or the Settings
button (settings "Settings button").

#### Scenario: Computing sun hours
- **WHEN** `Sun hours` is selected and 36 of the heatmap day's 144 steps are computed
- **THEN** the card shows, from top to bottom, `Sun hours`, `Computing sun hours …` and the heatmap legend, without a progress bar

#### Scenario: Day computing without a notice
- **WHEN** `Sun & shade` is selected, the overlay of the selected time is shown and the rest of the day is computed
- **THEN** the card shows `Sun & shade` and the `Shade` / `Unknown` legend, without a progress bar, while the time tape's strip fills in

#### Scenario: Zoomed out
- **WHEN** `Sun & shade` is selected and the map zoom is 10.5
- **THEN** the card shows `Sun & shade` and `Zoom in to see sun and shade`

#### Scenario: Off
- **WHEN** `Off` is selected
- **THEN** no status card is shown

### Requirement: Overlay resolution
The overlay and the heatmap SHALL be computed with the cell sizes and steps of the shade
resolution selected at the time (settings "Shade resolution"). When the shade resolution changes
while the overlay is on, the day of the shown mode SHALL be computed anew with the new values, the
selected time first, as after a change of the selected date: the previous overlay or heatmap SHALL
stay until the new one is ready, with the notice `Computing sun and shade …` ("Overlay updates")
or `Computing sun hours …` (sun-exposure-heatmap "Heatmap updates"). Days computed with other
values SHALL stay in the cache of days ("Overlay of the whole day") until they are dropped as least
recently used, and SHALL be shown again without being computed when their values are selected
again. While the overlay is off, a change of the shade resolution SHALL NOT start a computation.

#### Scenario: Preset changed
- **WHEN** the overlay is on in the mode `Sun & shade` with `Normal`, the day has been computed, and the user selects `Fast` and goes back to the map
- **THEN** the previous overlay stays with `Computing sun and shade …` until the overlay of the selected time in 4 dp cells replaces it, and the day is then computed every 10 minutes

#### Scenario: Back to a computed preset
- **WHEN** the day of the visible area was computed with `Normal`, the user selects `Fast`, and then `Normal` again before that day was dropped from the cache
- **THEN** the `Normal` overlay of every step of the day is shown within 100 ms, without being computed again

#### Scenario: Overlay off
- **WHEN** the overlay is off and the user selects `Detailed`
- **THEN** no overlay computation and no DEM tile request for it takes place
