# Spec Delta

## MODIFIED Requirements

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
- **THEN** the map area is blank, the crosshair, coordinates, ⓘ button, Offline button and location button are shown, and the offline notice is visible

#### Scenario: Launch without network in a stored area
- **WHEN** the app is launched without a network connection and the tiles of the visible area are stored
- **THEN** the map shows those tiles, and the offline notice is visible
