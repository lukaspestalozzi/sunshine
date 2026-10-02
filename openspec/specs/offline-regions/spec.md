# offline-regions Specification

## Purpose

Makes the app work without network: every tile the app fetches is kept, and the user can download
the visible area as a region, so that map, altitude, horizon and overlay work offline there. What
the app does not have stays blank or unknown, as everywhere else.

## Requirements

### Requirement: Kept tiles
Every map tile and every DEM tile the app fetches while the user browses, for the map itself,
the altitude, the horizon or the overlay, SHALL be kept in the app's persistent storage. The
operating system does not clear that storage as it may clear a cache. A kept tile SHALL be used
without a network request when the app needs it again ("Freshness of stored tiles"), and SHALL be
used without network, across app restarts.

Tiles kept this way are browsed tiles. Their total size SHALL be limited to 512 MiB for map tiles
and 512 MiB for DEM tiles, counted separately (user decision). When a limit is exceeded, the
least recently used browsed tiles of that kind SHALL be removed until the total is within the
limit. A tile that belongs to a region ("Region contents") SHALL NOT count towards the limit and
SHALL NOT be removed while a region uses it. A removed tile is fetched again when needed. For map
tiles, where regions overlap, the browsed limit MAY grow by the size of the tiles the regions
share, because the map's storage counts a shared tile once per region.

#### Scenario: Browsed area offline
- **WHEN** the user has browsed Interlaken at map zoom 13 with the overlay `Sun & shade` on, and restarts the app without network
- **THEN** the map of that area is shown, the altitude of Interlaken is `Altitude 568 m`, and the overlay at the same time is the same as before

#### Scenario: Limit reached
- **WHEN** the browsed DEM tiles total 512 MiB and another DEM tile is fetched while browsing
- **THEN** the least recently used browsed DEM tiles are removed until the total is at most 512 MiB, and no region tile is removed

#### Scenario: Region tile used while browsing
- **WHEN** the user browses inside a downloaded region
- **THEN** the region's tiles are used, no request is made for them while they are fresh, and they do not count towards the browsed limits

### Requirement: Offline button
The map screen SHALL show an Offline button directly to the right of the ⓘ button (map-view
"About and attributions"), in the same row, with the content description `Offline maps`. It SHALL
be at least 48 × 48 dp to touch, and SHALL NOT cover the ⓘ button, the coordinates or the
overlay toggle, on screens 360 dp wide or more, in portrait and landscape. Tapping it SHALL open
the Offline page. The system back gesture or button SHALL return from the Offline page to the
map, with the camera, the selected time and the overlay as they were.

#### Scenario: Open and return
- **WHEN** the map shows Interlaken at zoom 12 with `Sun hours` selected, and the user taps the Offline button and then goes back
- **THEN** the Offline page was shown, and the map shows Interlaken at zoom 12 with `Sun hours` selected

#### Scenario: Narrow screen
- **WHEN** the screen is 360 dp wide
- **THEN** the ⓘ button, the Offline button and the overlay toggle are fully on screen and do not overlap

### Requirement: Download the visible area
The Offline page SHALL offer the button `Download visible area`. The area is the map area that
was visible when the Offline page was opened, as a latitude/longitude rectangle. Below the button
the page SHALL show the area's size and what the download takes: `<w> × <h> km, <m> map tiles and
<d> elevation tiles, about <t> min`. Here `<w>` and `<h>` are the area's width at its centre
latitude and its height, in km with one decimal; `<m>` and `<d>` are the numbers of tiles of the
region ("Region contents"), stored or not; `<t>` is the larger of `<m>` and `<d>` divided by 5
requests per second ("Download rate limit"), in minutes rounded up. Tiles already stored are not
fetched again, so the download can be shorter. Numbers SHALL be formatted independently of the
device locale, without a thousands separator.

When the map zoom was below 11 when the page was opened, the button SHALL be disabled and the
page SHALL show `Zoom in to map zoom 11 or more to download an area` instead (user decision).
When the visible area crosses the 180° meridian, the button SHALL be disabled and the page SHALL
show `Areas across the 180° meridian cannot be downloaded` instead (user decision, 2026-10-01,
after the review of PR #31: its wrapped bounds would span almost 360°).
Tapping the enabled button SHALL start the download of the region and add it to the region list.

#### Scenario: Phone at zoom 11
- **WHEN** the map on a 400 × 850 dp screen is centred on 46.5935° N, 7.9091° E at zoom 11 and the Offline page is opened
- **THEN** the page shows `10.5 × 22.3 km, 7406 map tiles and 413 elevation tiles, about 25 min` (each tile count ±2 %)

#### Scenario: Zoomed out
- **WHEN** the Offline page is opened while the map zoom is 10.9
- **THEN** `Download visible area` is disabled and `Zoom in to map zoom 11 or more to download an area` is shown

#### Scenario: Across the 180° meridian
- **WHEN** the map on a 400 × 850 dp screen is centred on 17.0° S, 179.99° E at zoom 12 and the Offline page is opened
- **THEN** `Download visible area` is disabled and `Areas across the 180° meridian cannot be downloaded` is shown

### Requirement: Region contents
A region SHALL consist of the following tiles:
- **Map tiles:** every OpenTopoMap tile that the map shows for the area at map zooms 5 to 16 (user
  decision). With 256 px tiles these are tile zooms 6 to 17. Map zoom 17 uses the z17 tiles
  enlarged, offline as online.
- **DEM tiles:** every Mapterhorn tile that intersects the area extended on every side by a
  margin: z14 and z13 with a 1.5 km margin, z12 with 6 km, z11 with 25 km, and z10 with 150 km
  (user decision: the full range of the horizon and the overlay). The margin extends latitude by
  `m / 111.19 km` degrees, and longitude by `m / (111.19 km · cos φ)` degrees, where φ is the
  extended rectangle's latitude farthest from the equator.

A region is complete when every tile of it is stored, or known not to be published (HTTP 404).
A DEM tile that is not published SHALL be recorded as missing. Without network, the next coarser
published zoom SHALL then be used, as online (terrain-horizon "Horizon profile"). A region SHALL
NOT be complete while any of its tiles could not be obtained for another reason.

#### Scenario: Tiles of a fixed area
- **WHEN** the area is 46.55–46.65° N, 7.85–7.95° E
- **THEN** the region contains 1998 map tiles at z17 and 2752 map tiles in total, and 63 DEM tiles at z14, 20 at z13, 20 at z12, 30 at z11 and 169 at z10 (each ±2 %, for tiles touching the area's edges)

#### Scenario: Zoom not published
- **WHEN** Mapterhorn answers HTTP 404 for a z14 tile of the region during the download
- **THEN** the tile is recorded as missing, the region can still become complete, and offline the horizon uses z12 data there, as online

### Requirement: Download rate limit
Requests made to download a region SHALL be limited per tile server (tile.opentopomap.org and
tiles.mapterhorn.com): at most 5 requests SHALL start in any 1-second window, and at most 2 SHALL
be in progress at the same time (user decision). Requests made for browsing, the altitude, the
horizon or the overlay SHALL NOT be limited or delayed by region downloads.

#### Scenario: Request pace
- **WHEN** a region of 7406 map tiles, none of them stored, is downloaded
- **THEN** no 1-second window contains more than 5 region requests to tile.opentopomap.org, never more than 2 are in progress, and the map tiles take at least 24.6 minutes

#### Scenario: Browsing during a download
- **WHEN** a region is being downloaded and the user pans the map to an area without stored tiles
- **THEN** the map tiles of the newly visible area are requested at once, not queued behind the region's requests

### Requirement: Background download
A region download SHALL continue while the app is in the background or the screen is off, and
SHALL use any network connection, mobile data included (user decision). While it runs, a
notification SHALL show `Downloading offline map` and the progress in percent. On Android 13 or
later, the app SHALL ask for permission to show notifications when the user starts the first
download; if the user refuses, the download SHALL still run. The progress is the number of the
region's tiles that are stored or known missing, divided by the number of its tiles, in percent,
rounded down. It SHALL show 100 % only when the region is complete. Only one region SHALL download
at a time; a region started while another one downloads SHALL wait and start after it.

#### Scenario: App left during a download
- **WHEN** the user starts a download, switches to another app and turns the screen off for 10 minutes
- **THEN** the download has continued, and the notification shows its progress

#### Scenario: Progress
- **WHEN** 4775 of a region's 7819 tiles are stored or known missing
- **THEN** the progress is 61 %

### Requirement: Interrupted download
A download interrupted by a lost network connection, low storage, the app being closed or the
device restarting SHALL resume by itself, without user action, once the network is connected and
storage is no longer low. Tiles already stored SHALL NOT be fetched again. A server error other
than HTTP 404 SHALL be retried later; the tile stays unobtained meanwhile. A region SHALL NOT be
shown or used as complete before every tile of it is obtained ("Region contents"). Where tiles of
an incomplete region are missing offline, the map stays blank and the altitude, the horizon and
the overlay are unknown there, as for any missing tile.

#### Scenario: Network lost
- **WHEN** the network connection is lost at 63 % of a download
- **THEN** the region shows `Incomplete (63 %) · waiting for network`, and when the network returns the download continues from the tiles it has

#### Scenario: Low storage
- **WHEN** the device's storage becomes low at 40 % of a download
- **THEN** the region shows `Incomplete (40 %) · waiting for storage`, and the download continues once storage is no longer low

#### Scenario: App closed
- **WHEN** the app's process is ended at 30 % of a download while the network is connected
- **THEN** the download resumes by itself without refetching stored tiles

### Requirement: Region list
The Offline page SHALL list the regions, newest first. Each entry SHALL show the region's name,
which is its centre's coordinates in the format of the selected location (map-view "Selected
location crosshair"), e.g. `46.5935° N, 7.9091° E`, and one status line:
- `<date> · <size> MiB` for a complete region. `<date>` is the day the download completed in the
  device's time zone, as `yyyy-MM-dd`. `<size>` is the size of all its tiles in MiB, rounded half
  up, counting tiles it shares with other regions or browsing in full.
- `Downloading <p> %` while it downloads.
- `Waiting` while another region downloads first.
- `Incomplete (<p> %) · waiting for network` or `Incomplete (<p> %) · waiting for storage` while
  interrupted.

When there is no region, the page SHALL show `No offline regions yet`. Regions cannot be renamed,
and are not outlined on the map (user decisions).

#### Scenario: Complete region
- **WHEN** a region centred on 46.5935° N, 7.9091° E completed on 2026-10-02 and its tiles total 183.4 MiB
- **THEN** its entry reads `46.5935° N, 7.9091° E` and `2026-10-02 · 183 MiB`

#### Scenario: No regions
- **WHEN** no region has been downloaded
- **THEN** the Offline page shows `No offline regions yet`

### Requirement: Delete a region
Each region entry SHALL have a delete button with the content description `Delete region`. It
SHALL ask for confirmation with `Delete the offline region <name>?` and the buttons `Delete` and
`Cancel`. Deleting SHALL remove the region from the list and stop its download if it is running.
Tiles that another region uses SHALL stay in that region. The region's other tiles SHALL become
browsed tiles ("Kept tiles"), which the browsed limits then trim, least recently used first.
Deleting a region SHALL NOT remove any tile of another region.

#### Scenario: Overlapping regions
- **WHEN** regions A and B overlap, both are complete, and A is deleted
- **THEN** without network, every tile of B is still used, and B's area works offline as before ("Offline use of a region")

#### Scenario: Cancel
- **WHEN** the user taps `Delete region` and then `Cancel`
- **THEN** the region stays unchanged

#### Scenario: Delete while downloading
- **WHEN** a region is at `Downloading 20 %` and the user deletes it
- **THEN** the download stops, the region disappears from the list, and no further request is made for it

### Requirement: Freshness of stored tiles
A stored tile, browsed or of a region, SHALL be fresh for as long as the server's caching headers
of its last response allow (`Cache-Control: max-age`, else `Expires`), and for 7 days if the
response had neither. Both servers currently send `max-age=604800`, i.e. 7 days. While a tile is
fresh, it SHALL be used without a request. When an expired tile is needed and the network is
connected, it SHALL be revalidated with a conditional request (its `ETag` or `Last-Modified`).
If it did not change, the stored tile SHALL be used and becomes fresh again; if it changed, the
new tile SHALL replace it and keep its region memberships. Without network, or when the
revalidation fails, a stored tile SHALL be used whatever its age (user decision). A tile recorded
as missing (HTTP 404) SHALL follow the same rules.

#### Scenario: Fresh tile
- **WHEN** a map tile was stored 3 days ago with `max-age=604800` and is shown again online
- **THEN** no request is made for it

#### Scenario: Expired tile online
- **WHEN** a region's map tile was stored 10 days ago and is shown online, and the server answers `304 Not Modified`
- **THEN** the stored tile is shown, it stays in the region, and it is fresh for another 7 days

#### Scenario: Expired tile offline
- **WHEN** a region's DEM tile was stored 200 days ago and is needed without network
- **THEN** it is used, and the result is not unknown because of its age

### Requirement: Storage usage
The Offline page SHALL show the storage used by stored tiles, regions and browsed tiles
together: `Map tiles: <n> MiB` and `Elevation tiles: <n> MiB`, rounded half up. Below them it
SHALL show `Browsed tiles are kept up to 512 MiB each for map and elevation; the least recently used are removed first.`

#### Scenario: Usage shown
- **WHEN** the stored map tiles take 395.2 MiB and the stored DEM tiles 280.6 MiB
- **THEN** the page shows `Map tiles: 395 MiB` and `Elevation tiles: 281 MiB`

### Requirement: Offline use of a region
Without network, inside the area of a complete region, the app SHALL work as online: the map
SHALL show no blank tile at map zooms 5 to 17. The altitude SHALL be known wherever it is known
online. Horizon profiles SHALL be complete wherever they are complete online. The overlay and the
heatmap SHALL show the same state for every cell as online at map zoom 11 or more.

#### Scenario: Airplane mode in a region
- **WHEN** a region with the area 46.4931–46.6937° N, 7.8404–7.9778° E is complete, the device is in airplane mode, and the overlay `Sun & shade` is on at Lauterbrunnen, 46.5935° N, 7.9091° E, map zoom 12, on 2025-12-21 at 12:00
- **THEN** the map shows no blank tile, the altitude is known, and every cell of the overlay has the same state as online, with no unknown cell

#### Scenario: Outside the region
- **WHEN** the device is offline and the user pans to an area where no tile was ever stored
- **THEN** the map area there is blank and the altitude, the horizon and the overlay are unknown there, as specified in map-view, elevation-data, terrain-horizon and sun-shade-overlay
