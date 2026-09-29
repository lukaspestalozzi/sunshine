# Spec Delta

## ADDED Requirements

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

## MODIFIED Requirements

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

## REMOVED Requirements

### Requirement: Map attribution
**Reason**: The attributions crowded the map screen (device screenshot, 2026-09-29). By user decision they move to an About page reached from an ⓘ button in a corner of the map, as the OpenStreetMap Foundation's attribution guidelines allow for collapsed attribution.
**Migration**: See "About and attributions". The elevation attribution keeps its link to `https://mapterhorn.com/attribution/`.
