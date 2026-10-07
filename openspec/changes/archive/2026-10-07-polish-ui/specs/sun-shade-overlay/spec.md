## MODIFIED Requirements

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
