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

### Requirement: Map attribution
The app SHALL permanently show the attribution text
`© OpenStreetMap contributors, SRTM | Map style: © OpenTopoMap (CC-BY-SA)` on the map screen. It
SHALL be visible without any user interaction and SHALL NOT be covered by other elements.

#### Scenario: Attribution visible
- **WHEN** the map screen is shown, at any zoom level
- **THEN** the attribution text is visible on screen without tapping anything

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
- **THEN** the map area is blank, the crosshair, coordinates and attribution are shown, and the offline notice is visible

### Requirement: Map orientation
The map SHALL always be shown north-up and flat: bearing 0° and no tilt. Rotation and tilt
gestures SHALL have no effect, so that up on the screen is always north.

#### Scenario: Rotation gesture
- **WHEN** the user makes a two-finger rotation gesture on the map
- **THEN** the map does not rotate and north stays at the top of the screen

#### Scenario: Tilt gesture
- **WHEN** the user makes a two-finger vertical drag gesture on the map
- **THEN** the map does not tilt
