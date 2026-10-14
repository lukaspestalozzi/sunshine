# Spec Delta

## MODIFIED Requirements

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
- the search attribution `Search: Photon by komoot, © OpenStreetMap contributors`; tapping it SHALL
  open `https://photon.komoot.io/` (place-search "Online search requests"; added 2026-10-13);
- the icon credit `Icons: Material Symbols (Apache License 2.0)`.

#### Scenario: Attribution reachable
- **WHEN** the map screen is shown, at any zoom level
- **THEN** the Settings button is visible, and no attribution text covers the map

#### Scenario: About page
- **WHEN** the user taps the Settings button and scrolls to the end of the Settings page
- **THEN** the About section shows the app version, the map attribution, the elevation attribution, the search attribution and the icon credit, below every setting

#### Scenario: Elevation sources
- **WHEN** the user taps the elevation attribution on the Settings page
- **THEN** the browser opens `https://mapterhorn.com/attribution/`

#### Scenario: Map licence
- **WHEN** the user taps the map attribution on the Settings page
- **THEN** the browser opens `https://www.openstreetmap.org/copyright`

#### Scenario: Search attribution
- **WHEN** the user taps the search attribution on the Settings page
- **THEN** the browser opens `https://photon.komoot.io/`

#### Scenario: Back to the map
- **WHEN** the user has moved the map to Interlaken at zoom 12, selected `Sun hours`, opened the Settings page to read the attributions and goes back
- **THEN** the map shows Interlaken at zoom 12 with `Sun hours` selected
