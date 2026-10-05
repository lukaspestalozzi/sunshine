# Spec Delta

## MODIFIED Requirements

### Requirement: Default viewport
On launch, the map SHALL show the view chosen with `Start at` (settings "Settings page"):
- **`Last view`** (default): the map centre and zoom level the map had when the app last went to
  the background or was closed, kept across launches (settings "Stored settings"). When no view is
  stored, e.g. at the first launch after installing the app, the Alps overview SHALL be shown.
- **`Alps overview`**: centred on 46.8182° N, 8.2275° E (Swiss Alps) at zoom level 10.
- **`My location`**: the last view, as for `Last view`. Then the map SHALL move once so that its
  centre is the first fresh position (gps-location "Position dot") received after launch, keeping
  the zoom level, unless the user has moved the map before that position arrives (user decision,
  2026-10-04). Without location access, with location switched off on the device, or while no
  fresh position arrives, the map SHALL stay at the last view, without a notice and without a
  permission dialog.

The last view SHALL be stored at the latest when the app goes to the background. Only the map
centre and zoom level are kept; the selected time (time-selection "Initial selected time") and
the overlay (sun-shade-overlay "Overlay toggle") start as at every launch. While the app process
is alive, including across screen rotation and visits to the Settings and Offline pages, the
current centre and zoom level SHALL be preserved.

#### Scenario: App launch
- **WHEN** the app is launched for the first time after installing it
- **THEN** the map centre is 46.8182° N, 8.2275° E (±0.0001°) and the zoom level is 10 (±0.01)

#### Scenario: Last view
- **WHEN** `Start at` is `Last view`, the user moves the map to Interlaken, 46.6863° N, 7.8632° E, at zoom 13, closes the app and launches it again
- **THEN** the map centre is 46.6863° N, 7.8632° E (±0.0001°) and the zoom level is 13 (±0.01)

#### Scenario: Alps overview
- **WHEN** `Start at` is `Alps overview`, the user moves the map to Interlaken at zoom 13, closes the app and launches it again
- **THEN** the map centre is 46.8182° N, 8.2275° E (±0.0001°) and the zoom level is 10 (±0.01)

#### Scenario: My location
- **WHEN** `Start at` is `My location`, the last view is Bern at zoom 12, location access is allowed, the app is launched, and 5 s later the first fresh position 46.6863° N, 7.8632° E arrives while the user has not touched the map
- **THEN** the map first shows Bern at zoom 12, then its centre moves to 46.6863° N, 7.8632° E (±0.0001°) at zoom 12 (±0.01)

#### Scenario: Moved before the position arrives
- **WHEN** `Start at` is `My location`, the app is launched, and the user pans the map before the first fresh position arrives
- **THEN** the map does not move when the position arrives

#### Scenario: My location without access
- **WHEN** `Start at` is `My location`, location access has been refused, the last view is Bern at zoom 12, and the app is launched
- **THEN** the map shows Bern at zoom 12, and no notice and no permission dialog is shown

#### Scenario: Screen rotation
- **WHEN** the user has moved the map to a different centre and zoom level and then rotates the device
- **THEN** the map keeps that centre (±0.0001°) and zoom level (±0.01)

### Requirement: Selected location crosshair
The app SHALL mark the selected location with a crosshair fixed at the centre of the map. The
selected location SHALL always be the geographic centre of the map. Its coordinates SHALL be shown
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

### Requirement: Missing map tiles
When a map tile is neither stored (offline-regions "Kept tiles") nor loadable, the affected map
area SHALL stay blank; the app SHALL NOT show substitute imagery that could be mistaken for map
data. Stored tiles SHALL be shown without network. The crosshair and the selected-location
coordinates SHALL keep working. While the device has no network connection, the app SHALL show a
non-blocking notice that it is offline and map tiles may be missing.

#### Scenario: Device goes offline
- **WHEN** the device loses its network connection while the map screen is shown
- **THEN** a notice states that the device is offline and map tiles may be missing, AND the map can still be panned and zoomed, AND the selected-location coordinates keep updating

#### Scenario: Connectivity returns
- **WHEN** the network connection returns while the offline notice is shown
- **THEN** the notice disappears within 5 seconds and tiles for the visible area are loaded

#### Scenario: Launch without network
- **WHEN** the app is launched without a network connection and no map tile of the visible area is stored
- **THEN** the map area is blank, the crosshair, coordinates, Settings button, Offline button and location button are shown, and the offline notice is visible

#### Scenario: Launch without network in a stored area
- **WHEN** the app is launched without a network connection and the tiles of the visible area are stored
- **THEN** the map shows those tiles, and the offline notice is visible

### Requirement: About and attributions
The attributions SHALL NOT be shown on the map itself (user decision, 2026-09-29). They SHALL be
one tap away from the map: the Settings page, opened with the Settings button (settings "Settings
button"), SHALL show in its last section, About, below every setting (user decision, 2026-10-04):
- the app name and its version name;
- the map attribution `© OpenStreetMap contributors, SRTM | Map style: © OpenTopoMap (CC-BY-SA)`;
  tapping it SHALL open `https://www.openstreetmap.org/copyright` in the device's browser;
- the elevation attribution `Elevation: © Mapterhorn and its sources`; tapping it SHALL open
  `https://mapterhorn.com/attribution/`, which lists every source Mapterhorn's elevation data is
  built from;
- the icon credit `Icons: Material Symbols (Apache License 2.0)`.

#### Scenario: Attribution reachable
- **WHEN** the map screen is shown, at any zoom level
- **THEN** the Settings button is visible, and no attribution text covers the map

#### Scenario: About page
- **WHEN** the user taps the Settings button and scrolls to the end of the Settings page
- **THEN** the About section shows the app version, the map attribution, the elevation attribution and the icon credit, below every setting

#### Scenario: Elevation sources
- **WHEN** the user taps the elevation attribution on the Settings page
- **THEN** the browser opens `https://mapterhorn.com/attribution/`

#### Scenario: Map licence
- **WHEN** the user taps the map attribution on the Settings page
- **THEN** the browser opens `https://www.openstreetmap.org/copyright`

#### Scenario: Back to the map
- **WHEN** the user has moved the map to Interlaken at zoom 12, selected `Sun hours`, opened the Settings page to read the attributions and goes back
- **THEN** the map shows Interlaken at zoom 12 with `Sun hours` selected
