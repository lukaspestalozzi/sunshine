# Spec Delta

## MODIFIED Requirements

### Requirement: Map attribution
The app SHALL permanently show the attribution text
`© OpenStreetMap contributors, SRTM | Map style: © OpenTopoMap (CC-BY-SA)` on the map screen,
and next to it the elevation attribution `Elevation: © Mapterhorn and its sources`. Both SHALL be
visible without any user interaction and SHALL NOT be covered by other elements. Tapping the
elevation attribution SHALL open `https://mapterhorn.com/attribution/` in the device's browser;
the page lists every source Mapterhorn's elevation data is built from.

#### Scenario: Attribution visible
- **WHEN** the map screen is shown, at any zoom level
- **THEN** the map attribution text and the elevation attribution are visible on screen without tapping anything

#### Scenario: Elevation sources
- **WHEN** the user taps the elevation attribution
- **THEN** the browser opens `https://mapterhorn.com/attribution/`
