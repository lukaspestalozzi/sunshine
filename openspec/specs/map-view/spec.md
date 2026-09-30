# map-view Specification

## Purpose

Provides the interactive topographic map on which the user chooses the location to inspect. All
sun and terrain features are shown on this map, relative to its selected location.

## Requirements

### Requirement: Topographic base map
The app SHALL open to a full-screen interactive topographic map built from OpenTopoMap raster
tiles. The user SHALL be able to pan by dragging and to zoom by pinching or double-tapping. The
zoom level SHALL stay between 5 and 17 inclusive; 17 is the highest level OpenTopoMap serves.

#### Scenario: Pan the map
- **WHEN** the user drags the map
- **THEN** the map follows the gesture and tiles for the newly visible area are loaded

#### Scenario: Zoom limits
- **WHEN** the user zooms out past level 5 or in past level 17
- **THEN** the zoom level stops at 5 or 17 respectively

### Requirement: Default viewport
On launch the map SHALL be centred on 46.8182° N, 8.2275° E (Swiss Alps) at zoom level 10. While
the app process is alive, including across screen rotation, the current centre and zoom level
SHALL be preserved.

#### Scenario: App launch
- **WHEN** the app is launched
- **THEN** the map centre is 46.8182° N, 8.2275° E (±0.0001°) and the zoom level is 10 (±0.01)

#### Scenario: Screen rotation
- **WHEN** the user has moved the map to a different centre and zoom level and then rotates the device
- **THEN** the map keeps that centre (±0.0001°) and zoom level (±0.01)

### Requirement: Selected location crosshair
The app SHALL mark the selected location with a crosshair fixed at the centre of the map. The
selected location SHALL always be the geographic centre of the map. Its coordinates SHALL be shown
as decimal degrees rounded to 4 decimal places (about 11 m), with a hemisphere letter and a dot as
decimal separator regardless of the device locale. The shown coordinates SHALL update continuously
while the map moves.

#### Scenario: Coordinates follow the map
- **WHEN** the map centre is at latitude 46.68630, longitude 7.86320
- **THEN** the selected location reads `46.6863° N, 7.8632° E`

#### Scenario: Southern and western hemispheres
- **WHEN** the map centre is at latitude -33.86880, longitude -70.64830
- **THEN** the selected location reads `33.8688° S, 70.6483° W`

#### Scenario: Locale with decimal comma
- **WHEN** the device locale is German (de-CH) and the map centre is at latitude 46.68630, longitude 7.86320
- **THEN** the selected location still reads `46.6863° N, 7.8632° E`

### Requirement: Tile request identification
Every map tile request SHALL carry the User-Agent header
`Sunshine/<versionName> (Android; com.sunshine.app)`, where `<versionName>` is the app's version
name, so that tile servers can identify the app as their usage policies require.

#### Scenario: Tile request header
- **WHEN** the app with version name `0.1.0` requests a map tile
- **THEN** the request's User-Agent header is exactly `Sunshine/0.1.0 (Android; com.sunshine.app)`

### Requirement: Missing map tiles
When map tiles cannot be loaded, the affected map area SHALL stay blank; the app SHALL NOT show
substitute imagery that could be mistaken for map data. The crosshair and the selected-location
coordinates SHALL keep working. While the device has no network connection, the app SHALL show a
non-blocking notice that it is offline and map tiles may be missing.

#### Scenario: Device goes offline
- **WHEN** the device loses its network connection while the map screen is shown
- **THEN** a notice states that the device is offline and map tiles may be missing, AND the map can still be panned and zoomed, AND the selected-location coordinates keep updating

#### Scenario: Connectivity returns
- **WHEN** the network connection returns while the offline notice is shown
- **THEN** the notice disappears within 5 seconds and tiles for the visible area are loaded

#### Scenario: Launch without network
- **WHEN** the app is launched without a network connection
- **THEN** the map area is blank, the crosshair, coordinates and ⓘ button are shown, and the offline notice is visible

### Requirement: Map orientation
The map SHALL always be shown north-up and flat: bearing 0° and no tilt. Rotation and tilt
gestures SHALL have no effect, so that up on the screen is always north.

#### Scenario: Rotation gesture
- **WHEN** the user makes a two-finger rotation gesture on the map
- **THEN** the map does not rotate and north stays at the top of the screen

#### Scenario: Tilt gesture
- **WHEN** the user makes a two-finger vertical drag gesture on the map
- **THEN** the map does not tilt

### Requirement: About and attributions
The map screen SHALL show an ⓘ button in the map's top-left corner, directly above the
selected-location coordinates, with the content description `About and attributions`. It SHALL be
at least 48 × 48 dp to touch and SHALL NOT be covered by other elements. The attributions SHALL NOT
be shown on the map itself (user decision, 2026-09-29).

Tapping the ⓘ button SHALL open an About page that shows:
- the app name and its version name;
- the map attribution `© OpenStreetMap contributors, SRTM | Map style: © OpenTopoMap (CC-BY-SA)`;
  tapping it SHALL open `https://www.openstreetmap.org/copyright` in the device's browser;
- the elevation attribution `Elevation: © Mapterhorn and its sources`; tapping it SHALL open
  `https://mapterhorn.com/attribution/`, which lists every source Mapterhorn's elevation data is
  built from;
- the icon credit `Icons: Material Symbols (Apache License 2.0)`.

The system back gesture or button SHALL return from the About page to the map, with the camera,
the selected time and the overlay as they were.

#### Scenario: Attribution reachable
- **WHEN** the map screen is shown, at any zoom level
- **THEN** the ⓘ button is visible, and no attribution text covers the map

#### Scenario: About page
- **WHEN** the user taps the ⓘ button
- **THEN** the About page shows the app version, the map attribution, the elevation attribution and the icon credit

#### Scenario: Elevation sources
- **WHEN** the user taps the elevation attribution on the About page
- **THEN** the browser opens `https://mapterhorn.com/attribution/`

#### Scenario: Map licence
- **WHEN** the user taps the map attribution on the About page
- **THEN** the browser opens `https://www.openstreetmap.org/copyright`

#### Scenario: Back to the map
- **WHEN** the user has moved the map to Interlaken at zoom 12, selected `Sun hours`, opened the About page and goes back
- **THEN** the map shows Interlaken at zoom 12 with `Sun hours` selected

### Requirement: Map colours under the heatmap
While the sun-shade overlay shows `Sun hours` (sun-shade-overlay "Overlay toggle"), the map tiles
SHALL be shown in greyscale, so that the heatmap's colours stand out against the map's own greens
and blues (user decisions, 2026-09-29). While `Off` or `Sun & shade` is selected, the map tiles
SHALL be shown in their own colours. Lines, labels and contour lines SHALL stay readable in
greyscale.

#### Scenario: Heatmap
- **WHEN** the user selects `Sun hours`
- **THEN** the map tiles are shown in greyscale, with the heatmap on top

#### Scenario: Sun and shade
- **WHEN** the user selects `Sun & shade`
- **THEN** the map tiles are shown in their own colours, with the shade tint on top

#### Scenario: Overlay off
- **WHEN** the user selects `Off`
- **THEN** the map tiles are shown in their own colours
