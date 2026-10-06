## MODIFIED Requirements

### Requirement: Selected location crosshair
The app SHALL mark the selected location with a crosshair fixed at the centre of the map. The
selected location SHALL always be the geographic centre of the map. A single tap on the map SHALL
move the camera, animated over 300 ms (±50 ms), so that the tapped point becomes the map centre,
with the zoom and orientation unchanged; a double tap SHALL zoom in as before (user decision,
2026-10-05). Its coordinates SHALL be shown
in the format chosen with `Coordinates` (settings "Settings page"), regardless of the device
locale, and SHALL update continuously while the map moves:
- **`Decimal`** (default): decimal degrees rounded to 4 decimal places (about 11 m), with a
  hemisphere letter and a dot as decimal separator, e.g. `46.6863° N, 7.8632° E`.
- **`Degrees, minutes, seconds`**: whole degrees, minutes as two digits, and seconds as two digits
  with one decimal (about 3 m), rounded half up, with a hemisphere letter, e.g.
  `46°41′10.7″ N, 7°51′47.5″ E`. Seconds that round to 60.0 carry into the minutes, and minutes
  that reach 60 into the degrees.
- **`Swiss grid LV95`**: the easting E and the northing N of the Swiss projection LV95, in whole
  metres rounded half up, with an apostrophe as thousands separator, e.g. `2'632'479, 1'170'652`.
  They SHALL be computed with swisstopo's approximate formulas for WGS84 → LV95, whose precision
  is better than 1 m everywhere in Switzerland (user decision, 2026-10-04). LV95 SHALL be shown
  only within its area of use, 45.81° to 47.81° N and 5.95° to 10.50° E inclusive (EPSG:2056,
  Switzerland and Liechtenstein); outside it, the coordinates SHALL be shown in the `Decimal`
  format instead, as the grid is not defined there (user decision).

#### Scenario: Tap to centre
- **WHEN** the map shows zoom 12 and the user taps a point 100 dp right of the crosshair
- **THEN** the camera moves within 300 ms so that this point is under the crosshair, the zoom stays 12, and the panel shows the new location's values

#### Scenario: Coordinates follow the map
- **WHEN** `Coordinates` is `Decimal` and the map centre is at latitude 46.68630, longitude 7.86320
- **THEN** the selected location reads `46.6863° N, 7.8632° E`

#### Scenario: Southern and western hemispheres
- **WHEN** `Coordinates` is `Decimal` and the map centre is at latitude -33.86880, longitude -70.64830
- **THEN** the selected location reads `33.8688° S, 70.6483° W`

#### Scenario: Locale with decimal comma
- **WHEN** the device locale is German (de-CH), `Coordinates` is `Decimal`, and the map centre is at latitude 46.68630, longitude 7.86320
- **THEN** the selected location still reads `46.6863° N, 7.8632° E`

#### Scenario: Degrees, minutes, seconds
- **WHEN** `Coordinates` is `Degrees, minutes, seconds` and the map centre is at latitude 46.8182, longitude 8.2275
- **THEN** the selected location reads `46°49′05.5″ N, 8°13′39.0″ E`

#### Scenario: Seconds carry over
- **WHEN** `Coordinates` is `Degrees, minutes, seconds` and the map centre is at latitude 46.99999, longitude -0.5
- **THEN** the selected location reads `47°00′00.0″ N, 0°30′00.0″ W`

#### Scenario: LV95 reference point
- **WHEN** `Coordinates` is `Swiss grid LV95` and the map centre is at 46°02′38.87″ N, 8°43′49.79″ E (swisstopo's worked example, whose reference is E 2 700 000 m, N 1 100 000 m)
- **THEN** the selected location reads `2'700'000, 1'100'000` (±1 m in each value)

#### Scenario: LV95 in Interlaken
- **WHEN** `Coordinates` is `Swiss grid LV95` and the map centre is at latitude 46.6863, longitude 7.8632
- **THEN** the selected location reads `2'632'479, 1'170'652` (±1 m in each value)

#### Scenario: Outside the Swiss grid
- **WHEN** `Coordinates` is `Swiss grid LV95` and the map centre is the Zugspitze, latitude 47.4211, longitude 10.9853
- **THEN** the selected location reads `47.4211° N, 10.9853° E`

## ADDED Requirements

### Requirement: First-run hint
The first time the map screen is shown after the app is installed, a hint card SHALL be shown over
the map, without covering the crosshair, the overlay toggle or the time tape (user decision,
2026-10-06). It SHALL read:
- `The crosshair marks the place the panel describes. Drag the map, or tap a point to centre it.`
- `◐ shows sun and shade at the selected time, ◔ the hours of sun over the day. Long-press an icon
  for its name.`, with the toggle's own icons in place of ◐ and ◔;

and offer the button `Got it`. The map, the toggle and the panel SHALL stay usable while the hint is
shown. `Got it` SHALL dismiss the hint; once dismissed, it SHALL NOT be shown again, also after
restarts and updates (settings "Stored settings"). Until it is dismissed, it SHALL be shown again at
every launch.

#### Scenario: First launch
- **WHEN** the app is launched for the first time after installing it
- **THEN** the hint card is shown with both texts and `Got it`, and the crosshair, the toggle and the time tape stay visible

#### Scenario: Dismissed for good
- **WHEN** the user taps `Got it`, closes the app and launches it again
- **THEN** no hint card is shown

#### Scenario: Usable meanwhile
- **WHEN** the hint card is shown and the user pans the map and selects `Sun & shade`
- **THEN** the map pans, the overlay is computed, and the hint stays until `Got it` is tapped
