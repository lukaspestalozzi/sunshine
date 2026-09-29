# Spec Delta

## MODIFIED Requirements

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
- shade cells with a translucent dark blue-grey tint through which the map stays readable;
- sun cells without a tint;
- unknown cells with grey diagonal hatching.

It SHALL NOT cover the crosshair, the sun direction line, the sun information panel or the map
attributions. While the overlay is on in the mode `Sun & shade`, a legend SHALL show the shade
tint labelled `Shade` and the hatching labelled `Unknown`. In the mode `Sun hours` the heatmap's
legend is shown instead (sun-exposure-heatmap "Heatmap legend").

#### Scenario: Legend
- **WHEN** the overlay is switched on in the mode `Sun & shade`
- **THEN** a legend with `Shade` and `Unknown` is visible

#### Scenario: Legend in heatmap mode
- **WHEN** the overlay is on in the mode `Sun hours`
- **THEN** no legend entry `Shade` is visible

#### Scenario: Crosshair stays visible
- **WHEN** the cell under the crosshair is shade
- **THEN** the crosshair and the sun direction line are drawn above the tint

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
- **After a change of the selected time or date**, the previous overlay also stays until the new
  one is ready. Meanwhile the notice `Computing sun and shade …` SHALL be shown, because the
  overlay on screen belongs to another time.

In the mode `Sun hours`, the heatmap's own update rules and notice apply instead
(sun-exposure-heatmap "Heatmap updates"); `Computing sun and shade …` SHALL NOT be shown.

#### Scenario: Pan
- **WHEN** the overlay is on in the mode `Sun & shade` and the user pans the map by half a screen
- **THEN** the previous overlay stays aligned with the terrain it was computed for, and the new overlay replaces it after the camera has rested for 300 ms and the computation has finished

#### Scenario: Time change to a time not yet computed
- **WHEN** the overlay is on in the mode `Sun & shade`, shows 12:00, and the user moves the time slider to 15:00 before 15:00 has been computed
- **THEN** the overlay for 12:00 stays and `Computing sun and shade …` is shown until the overlay for 15:00 replaces it

#### Scenario: Time change to a time already computed
- **WHEN** the overlay is on in the mode `Sun & shade` and the user moves the time slider to a time of the selected day whose overlay has been computed
- **THEN** that overlay is drawn within 100 ms, without the notice

#### Scenario: Date change
- **WHEN** the overlay is on in the mode `Sun & shade` and the user picks another date whose day has not been computed
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
