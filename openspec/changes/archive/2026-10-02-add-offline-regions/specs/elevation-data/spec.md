# Spec Delta

## MODIFIED Requirements

### Requirement: Unknown elevation
When any tile needed for a location cannot be obtained, the elevation of that location SHALL be
unknown. This covers: no network and the tile not stored (offline-regions "Kept tiles"), an HTTP
error or timeout with no stored copy, a tile that does not exist (HTTP 404, e.g. open sea), and a
tile that cannot be decoded. It also covers a location outside the Web-Mercator latitude range.
The app SHALL NOT substitute a default height (such as 0 m), the nearest available sample, or a
value from another location. An unknown elevation SHALL be determined again when the location
changes or the network connection returns.

#### Scenario: Offline, tile not cached
- **WHEN** the device has no network connection and the tile for the selected location has never been loaded
- **THEN** the elevation is unknown

#### Scenario: No tile for the location
- **WHEN** the location is 30.0° N, 40.0° W (open Atlantic) and the tile server answers HTTP 404
- **THEN** the elevation is unknown

#### Scenario: Connectivity returns
- **WHEN** the elevation of the selected location is unknown because the device was offline, and the network connection returns
- **THEN** the elevation is loaded within 5 seconds without the user moving the map

### Requirement: DEM tile loading and cache
DEM tiles SHALL be fetched on demand, only for tiles needed for a location the app evaluates or
for a region download (offline-regions "Region contents"). A fetched tile SHALL be kept in the
app's persistent storage, not in its cache directory, and SHALL be used from there, even without
network, following offline-regions "Freshness of stored tiles". Browsed DEM tiles are limited to
512 MiB, least recently used removed first; region tiles are kept while a region uses them
(offline-regions "Kept tiles"). A removed tile is fetched again when needed. A tile the server
does not publish (HTTP 404) SHALL be recorded as missing, so that without network the next
coarser published zoom is used as online. A failed fetch SHALL NOT be retried for the same
location until the location changes or the network connection returns.

#### Scenario: Previously viewed location offline
- **WHEN** the elevation of a location was loaded, the app is restarted without network, and the same location is selected
- **THEN** the elevation is known and equal to the earlier value

#### Scenario: Panning within a tile
- **WHEN** the user pans the map so that the selected location stays within tiles already loaded
- **THEN** no network request is made

#### Scenario: App cache cleared
- **WHEN** the elevation of a location was loaded, the user clears the app's cache in the system settings (not its storage), and the same location is selected without network
- **THEN** the elevation is known and equal to the earlier value

#### Scenario: Missing zoom offline
- **WHEN** a z14 tile was answered with HTTP 404 while online, and the horizon of a location in it is computed without network
- **THEN** the z12 data is used there, and the horizon equals the one computed online
