# Spec Delta

## ADDED Requirements

### Requirement: Clear browsed tiles
The button `Clear browsed tiles` on the Settings page (settings "Settings page") SHALL remove every
browsed map tile and every browsed DEM tile ("Kept tiles") from the app's storage, without asking
for confirmation (user decision, 2026-10-04). Tiles of regions ("Region contents") SHALL be kept.
A removed tile is fetched again when it is needed and the network is available; without network,
the areas it covered are treated as never visited ("Offline use of a region", scenario "Outside
the region"). When the tiles are removed, the page SHALL show `Browsed tiles cleared`.

While any region is not complete ("Region list": downloading, waiting or incomplete), the button
SHALL be disabled and the page SHALL show `Not available while a region is downloading` (user
decision), so that no tile that a region download is about to claim is removed.

#### Scenario: Clear
- **WHEN** the browsed map tiles take 300 MiB, the browsed DEM tiles 200 MiB, one complete region takes 183 MiB, and the user taps `Clear browsed tiles`
- **THEN** `Browsed tiles cleared` is shown, and the Offline page then shows the region's 183 MiB only, split between `Map tiles` and `Elevation tiles`

#### Scenario: Region still works offline
- **WHEN** the browsed tiles have been cleared, the device is offline and the user browses inside a complete region
- **THEN** the region works offline as before

#### Scenario: Browsed area offline after clearing
- **WHEN** the user browsed Interlaken outside any region, cleared the browsed tiles, and restarts the app without network
- **THEN** the map area of Interlaken is blank and its altitude is unknown

#### Scenario: During a download
- **WHEN** a region shows `Downloading 20 %` and the Settings page is opened
- **THEN** `Clear browsed tiles` is disabled and `Not available while a region is downloading` is shown

## MODIFIED Requirements

### Requirement: Kept tiles
Every map tile and every DEM tile the app fetches while the user browses, for the map itself,
the altitude, the horizon or the overlay, SHALL be kept in the app's persistent storage. The
operating system does not clear that storage as it may clear a cache. A kept tile SHALL be used
without a network request when the app needs it again ("Freshness of stored tiles"), and SHALL be
used without network, across app restarts.

Tiles kept this way are browsed tiles. Their total size SHALL be limited to the chosen `Browsed tiles limit` (settings "Settings
page": 128, 256, 512, 1024 or 2048 MiB; 512 MiB by default) for map tiles, and to the same limit
for DEM tiles, counted separately (user decisions). When a limit is exceeded, the
least recently used browsed tiles of that kind SHALL be removed until the total is within the
limit. When the user lowers the limit, the least recently used browsed tiles of each kind SHALL
be removed at once, within 10 s, until each total is within the new limit (user decision,
2026-10-04). A tile that belongs to a region ("Region contents") SHALL NOT count towards the limit and
SHALL NOT be removed while a region uses it. A removed tile is fetched again when needed. For map
tiles, where regions overlap, the browsed limit MAY grow by the size of the tiles the regions
share, because the map's storage counts a shared tile once per region.

#### Scenario: Browsed area offline
- **WHEN** the user has browsed Interlaken at map zoom 13 with the overlay `Sun & shade` on, and restarts the app without network
- **THEN** the map of that area is shown, the altitude of Interlaken is `Altitude 568 m`, and the overlay at the same time is the same as before

#### Scenario: Limit reached
- **WHEN** the limit is 512 MiB, the browsed DEM tiles total 512 MiB and another DEM tile is fetched while browsing
- **THEN** the least recently used browsed DEM tiles are removed until the total is at most 512 MiB, and no region tile is removed

#### Scenario: Region tile used while browsing
- **WHEN** the user browses inside a downloaded region
- **THEN** the region's tiles are used, no request is made for them while they are fresh, and they do not count towards the browsed limits

#### Scenario: Limit lowered
- **WHEN** the browsed map tiles take 400 MiB, the browsed DEM tiles 300 MiB, and the user sets `Browsed tiles limit` to `256 MiB`
- **THEN** within 10 s the browsed map tiles take at most 256 MiB and the browsed DEM tiles at most 256 MiB, the least recently used having been removed, and no region tile is removed

#### Scenario: Limit raised
- **WHEN** the user sets `Browsed tiles limit` to `2048 MiB`
- **THEN** no tile is removed, and browsed tiles of each kind are kept up to 2048 MiB

### Requirement: Offline button
The map screen SHALL show an Offline button directly to the right of the Settings button
(settings "Settings button"), in the same row, with the content description `Offline maps`. It SHALL
be at least 48 × 48 dp to touch, and SHALL NOT cover the Settings button, the coordinates or the
overlay toggle, on screens 360 dp wide or more, in portrait and landscape. Tapping it SHALL open
the Offline page. The system back gesture or button SHALL return from the Offline page to the
map, with the camera, the selected time and the overlay as they were.

#### Scenario: Open and return
- **WHEN** the map shows Interlaken at zoom 12 with `Sun hours` selected, and the user taps the Offline button and then goes back
- **THEN** the Offline page was shown, and the map shows Interlaken at zoom 12 with `Sun hours` selected

#### Scenario: Narrow screen
- **WHEN** the screen is 360 dp wide
- **THEN** the Settings button, the Offline button, the location button (gps-location "Location button") and the overlay toggle are fully on screen and do not overlap

### Requirement: Storage usage
The Offline page SHALL show the storage used by stored tiles, regions and browsed tiles
together: `Map tiles: <n> MiB` and `Elevation tiles: <n> MiB`, rounded half up. Below them it
SHALL show `Browsed tiles are kept up to <n> MiB each for map and elevation; the least recently used are removed first.`,
where `<n>` is the chosen `Browsed tiles limit` (settings "Settings page").

#### Scenario: Usage shown
- **WHEN** the stored map tiles take 395.2 MiB and the stored DEM tiles 280.6 MiB
- **THEN** the page shows `Map tiles: 395 MiB` and `Elevation tiles: 281 MiB`

#### Scenario: Limit in the text
- **WHEN** `Browsed tiles limit` is `1024 MiB` and the Offline page is opened
- **THEN** the page shows `Browsed tiles are kept up to 1024 MiB each for map and elevation; the least recently used are removed first.`
