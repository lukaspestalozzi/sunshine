# elevation-data Specification

## Purpose

Provides the ground elevation at a location from DEM tiles. When the elevation cannot be
determined, the result is explicitly unknown, never a substituted default. Terrain features build
on this.

## Requirements

### Requirement: Elevation at a location
For a location within the Web-Mercator latitude range (±85.0511°), the app SHALL determine the
ground elevation in metres above sea level. It is the bilinear interpolation of the four nearest
height samples of the Mapterhorn DEM at zoom level 12. The samples are Terrarium-encoded values
(elevation = R × 256 + G + B / 256 − 32768) at pixel centres of 512 × 512 px tiles, about 13 m
apart in the Alps. When the four samples lie in different tiles, all of those tiles are used. The
elevation SHALL be computed off the main thread.

Tolerance: the reference is swissALTI3D 2 m (swisstopo), bilinear
(`investigations/dem-source-evaluation.md`). On gentle terrain the elevation SHALL be within
±1 m of it. On summits the DEM reads low: the result SHALL be between 10 m below and 1 m above
the reference.

#### Scenario: Valley floor
- **WHEN** the location is Interlaken, 46.6863° N, 7.8632° E
- **THEN** the elevation is 568.0 m ±1 m

#### Scenario: Mountain pass
- **WHEN** the location is Kleine Scheidegg, 46.5853° N, 7.9610° E
- **THEN** the elevation is 2061.3 m ±1 m

#### Scenario: Interpolation across tile borders
- **WHEN** the location is 46.55886° N, 7.91016° E (Lauterbrunnen valley), whose four nearest samples lie in four different zoom-12 tiles (x 2137 and 2138, y 1447 and 1448)
- **THEN** the elevation is 1132.1 m ±1 m

#### Scenario: Summit
- **WHEN** the location is the Jungfrau summit, 46.53679° N, 7.96258° E (reference 4157.8 m)
- **THEN** the elevation is between 4147.8 m and 4158.8 m

### Requirement: Unknown elevation
When any tile needed for a location cannot be obtained, the elevation of that location SHALL be
unknown. This covers: no network and the tile not in the cache, an HTTP error or timeout, a tile
that does not exist (HTTP 404, e.g. open sea), and a tile that cannot be decoded. It also covers a
location outside the Web-Mercator latitude range. The app SHALL NOT substitute a default height
(such as 0 m), the nearest available sample, or a value from another location. An unknown
elevation SHALL be determined again when the location changes or the network connection returns.

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
DEM tiles SHALL be fetched on demand, only for tiles needed for a location the app evaluates. A
fetched tile SHALL be stored in a disk cache of at most 100 MiB in the app's cache directory. It
SHALL be served from that cache, even without network, for as long as the cache holds it. The
operating system or the size limit may evict tiles; an evicted tile is fetched again when needed.
A failed fetch SHALL NOT be retried for the same location until the location changes or the
network connection returns.

#### Scenario: Previously viewed location offline
- **WHEN** the elevation of a location was loaded, the app is restarted without network, and the same location is selected
- **THEN** the elevation is known and equal to the earlier value

#### Scenario: Panning within a tile
- **WHEN** the user pans the map so that the selected location stays within tiles already loaded
- **THEN** no network request is made

### Requirement: DEM tile request identification
Every DEM tile request SHALL carry the User-Agent header
`Sunshine/<versionName> (Android; com.sunshine.app)`, the same as map tile requests.

#### Scenario: DEM tile request header
- **WHEN** the app with version name `0.1.0` requests a DEM tile
- **THEN** the request's User-Agent header is exactly `Sunshine/0.1.0 (Android; com.sunshine.app)`

### Requirement: Altitude in the information panel
The sun information panel (sun-position "Sun information panel") SHALL show the altitude of the
selected location:
- `Altitude <m> m` when it is known. The value is rounded to whole metres (half up), may be
  negative, and is formatted independently of the device locale, without a thousands separator,
  e.g. `Altitude 1634 m`.
- `Altitude …` while it is being loaded. A value from a previous location SHALL NOT be shown
  instead.
- `Altitude unknown` when it is unknown.
The value SHALL update while the map moves.

#### Scenario: Known altitude
- **WHEN** the elevation of the selected location is 1634.43 m
- **THEN** the panel shows `Altitude 1634 m`

#### Scenario: Rounding
- **WHEN** the elevation is 567.5 m, or -0.4 m
- **THEN** the panel shows `Altitude 568 m`, or `Altitude 0 m` respectively

#### Scenario: Locale with thousands separator
- **WHEN** the device locale is German (de-CH) and the elevation is 2061.31 m
- **THEN** the panel shows `Altitude 2061 m`

#### Scenario: Loading
- **WHEN** the user pans to a location whose tile is not yet loaded and the network is available
- **THEN** the panel shows `Altitude …` until the tile is loaded, then the value

#### Scenario: Unknown altitude
- **WHEN** the elevation of the selected location is unknown
- **THEN** the panel shows `Altitude unknown`
