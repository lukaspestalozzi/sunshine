# Spec Delta

## ADDED Requirements

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

## MODIFIED Requirements

### Requirement: Sunshine of a cell
The visible map area SHALL be divided into cells no larger than the `Sun & shade` cell size of the
shade resolution (settings "Shade resolution"): 2 × 2 dp with `Normal`, 1 × 1 dp to 8 × 8 dp in
all. Every cell SHALL have a sunshine state, decided at the cell's sample point, which lies within the cell. The state follows
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

#### Scenario: Cell size of Fast
- **WHEN** the shade resolution is `Fast`, the map zoom is 12 and the visible area is 400 × 850 dp
- **THEN** the overlay's cells are at most 4 × 4 dp, and at least 99.5 % of them have the state that point-sunshine gives at their sample points

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

### Requirement: Overlay of the whole day
Once the overlay of the selected time is ready, the app SHALL compute in the background the overlay
of the visible area at every step of the selected day: every `Sun & shade` step of the shade
resolution (settings "Shade resolution"; 5 minutes with `Normal`) from the start of the day, over
its actual length. These are the slider's positions while `Sun & shade` is shown (time-selection
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
- **Cache of days:** computed days SHALL be kept by visible area, date, cell size and step, up to a quarter of the
  app's heap limit. Beyond it, the least recently used days SHALL be dropped, never the day being
  shown. A day with unknown cells SHALL be computed anew when it is selected while the network is
  available, and after a reconnect while it is shown.
- **Progress:** while the background computation runs, a determinate progress bar in the status
  card ("Overlay status card") SHALL show the share of the day's slider positions already
  computed. It SHALL disappear when every position is computed or the computation stops.

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
- **THEN** a progress bar in the status card shows 25 %, and once all 288 are computed no bar is shown

#### Scenario: Responsive while computing the day
- **WHEN** the day is being computed in the background
- **THEN** the map can be panned and the time slider moved without delay

#### Scenario: Day of Fast
- **WHEN** the shade resolution is `Fast` and the overlay is on in the mode `Sun & shade` on 2025-12-21 in Europe/Zurich
- **THEN** the day's overlay is computed at 144 steps, 10 minutes apart, and the progress bar shows 25 % once 36 of them are computed

### Requirement: Overlay status card
While `Sun & shade` or `Sun hours` is selected, a status card SHALL be shown directly below the
overlay toggle, exactly as wide as the toggle and aligned with its right edge. From top to bottom
it SHALL hold:
1. the name of the selected mode, `Sun & shade` or `Sun hours`;
2. the mode's notice, if any: `Zoom in to see sun and shade` ("Overlay coverage and zoom range"),
   `Computing sun and shade …` ("Overlay updates") or `Computing sun hours …`
   (sun-exposure-heatmap "Heatmap updates");
3. while the day of the selected mode is computed ("Overlay of the whole day", sun-exposure-heatmap
   "Heatmap updates"), its progress bar, directly below the notice, or directly below the mode's
   name when there is no notice;
4. the mode's legend ("Overlay appearance", sun-exposure-heatmap "Heatmap legend").

These notices SHALL appear only in the status card. While `Off` is selected, no status card SHALL
be shown. The card SHALL NOT cover the crosshair, the sun information panel or the Settings
button (settings "Settings button").

#### Scenario: Computing sun hours
- **WHEN** `Sun hours` is selected and 72 of the day's 288 slider positions are computed
- **THEN** the card shows, from top to bottom, `Sun hours`, `Computing sun hours …`, a progress bar at 25 %, and the heatmap legend

#### Scenario: Day computing without a notice
- **WHEN** `Sun & shade` is selected, the overlay of the selected time is shown and the rest of the day is computed
- **THEN** the card shows `Sun & shade`, directly below it the progress bar, and the `Shade` / `Unknown` legend

#### Scenario: Zoomed out
- **WHEN** `Sun & shade` is selected and the map zoom is 10.5
- **THEN** the card shows `Sun & shade` and `Zoom in to see sun and shade`

#### Scenario: Off
- **WHEN** `Off` is selected
- **THEN** no status card is shown
