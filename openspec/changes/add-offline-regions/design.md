# Design

## Context

See proposal.md for the motivation and specs/ for the behaviour. What exists today:

- **Map tiles.** `MapLibreMap` loads the OpenTopoMap style from an inline JSON string
  (`Style.Builder().fromJson`), a raster source with 256 px tiles and `maxzoom` 17.
  `SunshineApp` hands MapLibre an OkHttp client with the User-Agent interceptor
  (`HttpRequestUtil.setOkHttpClient`). MapLibre keeps fetched tiles in its SQLite database
  (`mbgl-offline.db`) in `filesDir`: the *ambient cache*, 50 MB by default.
- **DEM tiles.** `DemTileFetcher` fetches Mapterhorn tiles through an OkHttp client with a
  100 MiB `Cache` in `cacheDir/dem-tiles`. After a network failure it serves a stale cached copy
  (`FORCE_CACHE`). `TileCache` keeps 64 decoded tiles in memory and upsamples a tile answered 404
  from its parent. The elevation, horizon and overlay code all read tiles through `TileCache.tile`.
- **Screens.** `MainActivity` switches between the map and the About page with a
  `rememberSaveable` flag, without a navigation library. `MapLabels` holds the ⓘ button alone in
  the top-left column.
- **Visible area.** `MapViewModel` knows the camera and the map size in dp. `MapArea.corners()`
  (`core`) turns them into the four corners.
- **No database, no background work, no DI framework.** Dependencies are wired by hand in
  `SunshineApp`, and tests are JUnit 6 on the plain JVM only.

Verified in the MapLibre Android 13.6.1 sources (tag `android-v13.6.1`) and against the servers on
2026-09-30:

- `coveringZoomLevel` = `round(zoom + log2(512 / tileSize))` for raster sources, so a 256 px
  source at map zoom z loads tiles of zoom z + 1.
- `OfflineManager.createOfflineRegion(OfflineTilePyramidRegionDefinition(styleURL, bounds,
  minZoom, maxZoom, pixelRatio), metadata, …)`. A download skips resources already in the
  database (`hasRegionResource` → `markUsedResources`), and skips tiles answered 404.
- `deleteRegion` removes the region's links. Tiles that no region links any more become ambient,
  and `evict(0)` trims the ambient cache to its maximum.
- `setMaximumAmbientCacheSize`: "the ambient cache will always try to not exceed the maximum
  size defined, taking into account the current size for the offline regions". It should be
  called before a map is loaded.
- `OnlineFileSource::canRequest` refuses `asset://` and `file://` URLs, so an offline region
  cannot download a style from an asset.
- `MapLibre.setModuleProvider(ModuleProvider)` supplies the `HttpRequest` implementation. Its
  `executeRequest(responder, nativePtr, url, dataRange, etag, modified, offlineUsage)` receives
  `offlineUsage = true` for every region download request (`Resource::Usage::Offline`).
- Headers: Mapterhorn answers 200 and 404 with `cache-control: public, max-age=604800` and a
  `last-modified`, without an ETag. OpenTopoMap answers with `max-age=604800` and an `etag`.

## Goals / Non-Goals

**Goals:**
- One model for both kinds of tile: a stored tile is browsed or claimed by one or more regions.
  Only browsed tiles count towards a limit and are evicted.
- Throttle region downloads without slowing down browsing.
- Nothing is marked complete or known unless it is: a region is complete only when every tile
  is stored or known missing.

**Non-Goals:**
- Replacing MapLibre's storage for map tiles.
- Changing `TileCache`, the horizon or the overlay algorithms. They keep reading tiles through
  `TileCache.tile`, and only the fetch behind it changes.
- A DI framework or a navigation library.

## Decisions

### D1. Map tiles stay in MapLibre's database; DEM tiles get a store with the same model
MapLibre's ambient cache already is "browsed tiles, least recently used first out". Its offline
regions already are reference-counted pinned tiles (verified, see Context). Map tiles therefore
stay there: browsed = ambient, region = offline region. DEM tiles get their own store (D5) that
mirrors the model: a tile has a set of owning regions; a tile without an owner is browsed.
*Alternatives:* downloading OpenTopoMap tiles ourselves into our own store and serving them to
MapLibre through the custom `HttpRequest` (D3). One store for both, but it re-implements what
MapLibre has, including its revalidation. Rejected.

### D2. The ambient limit is 512 MiB plus the regions' size
MapLibre's ambient maximum covers the regions too (verified), so the app sets
`setMaximumAmbientCacheSize(512 MiB + Σ region map sizes)`:
- at start in `SunshineApp.onCreate`, before any map loads, from the region sizes recorded in the
  region table (D6);
- after each 16 MiB of region progress, and after a region completes or is deleted.

The region sizes come from `OfflineRegionStatus.completedResourceSize`, which counts shared tiles
once per region. Where regions overlap, the ambient share can therefore exceed 512 MiB by what
they share. The spec states this ("Kept tiles"). *Alternatives:* reading MapLibre's SQLite
schema to count unique tiles, which is fragile across versions and was rejected; a fixed maximum
without the regions' size, which would shrink the browsed share as regions grow and was
rejected.

### D3. A custom `HttpRequest` throttles region downloads and serves the region style
`SunshineApp` calls `MapLibre.setModuleProvider` before `MapLibre.getInstance`, with a provider
whose `createHttpRequest()` returns `SunshineHttpRequest`, a wrapper around MapLibre's
`HttpRequestImpl`:
- `offlineUsage == false` (browsing): delegate at once. Browsing is never delayed.
- `offlineUsage == true` (region download): a coroutine on an app-wide scope acquires the
  `RateLimiter` of the URL's host (D4), then delegates. The wrapped `HttpResponder` releases the
  limiter on `onResponse` or `handleFailure`. `cancelRequest` cancels a request still waiting for
  the limiter, or else the delegate. `executeRequest` never blocks MapLibre's calling thread.
- URL `https://sunshine.invalid/opentopomap-style.json`: answered locally (200) with the style
  JSON, never sent. The region definitions use this URL as their `styleURL`, because MapLibre's
  offline download cannot read `asset://` (verified). `.invalid` is reserved (RFC 2606) and never
  resolves. The map keeps loading the same JSON with `fromJson`, so it does not depend on this
  URL when offline at first launch. The JSON moves from `MapLibreMap.kt` into one shared constant.
  Tiles are stored by tile URL, so region tiles and browsed tiles are the same entries.

*Alternatives:* throttling in an OkHttp interceptor, which cannot tell region requests from
browsing (OkHttp sees no `offlineUsage`); throttling all map requests while a download runs,
which slows down browsing; storing the style in the database with
`OfflineManager.putResourceWithUrl` before each download, which would also work but relies on the
ambient entry not being evicted before the download reads it. Unverified on the device: the
download completing with the locally answered style. That is task 2's spike, with
`putResourceWithUrl` as the fallback.

### D4. Rate limiter: 200 ms between starts and 2 in flight, per host
`RateLimiter(minInterval = 200 ms, maxInFlight = 2)`: a `Semaphore(2)` plus a mutex-guarded
"next start not before" time. Starts spaced at least 200 ms apart put at most 5 starts into any
1-second window, which is exactly the spec's limit. One limiter per host
(`tile.opentopomap.org`, `tiles.mapterhorn.com`) lives in `SunshineApp` and is shared by D3 and
the DEM region download (D7). It is tested with virtual time (`kotlinx-coroutines-test`).
*Alternative:* a token bucket of 5, which allows bursts of 5 at once and makes the in-flight cap
do all the work. The fixed spacing is simpler to reason about.

### D5. The DEM store: tile files plus a Room index (user decision: Room, files + index file as fallback)
- **Files:** `filesDir/dem/{z}/{x}/{y}.webp`, written to a temporary file and renamed, so a
  file either is complete or does not exist.
- **Room database `offline.db`** with the tables:
  - `dem_tile(z, x, y, state FOUND|MISSING, bytes, etag, lastModified, freshUntil, lastUsed)`,
    primary key (z, x, y);
  - `region(id, centreLat, centreLon, south, west, north, east, createdAt, completedAt,
    mapRegionId, mapBytes, state)`;
  - `region_dem_tile(regionId, z, x, y)`, primary key of all four.
- **Browsed** = a `dem_tile` without a `region_dem_tile` row. Browsed size = the `bytes` of those
  rows.
- **Eviction** runs after each insert of a browsed tile when the browsed size exceeds 512 MiB. It
  deletes browsed rows in `lastUsed` order, then their files, until the size is within the limit.
- **Use.** `lastUsed` is updated in memory on every read and written in batches, at most every
  5 s. After a crash, a tile loses at most 5 s of recency, which is harmless.
- **Startup.** A file without a row is deleted, and a row without its file is deleted. This
  covers a crash between the file and the row.
- **Room on the JVM.** Tests use Room's `BundledSQLiteDriver` (Room ≥ 2.7). Task 1 verifies that
  Room and KSP build with AGP 9.4.1 / Kotlin 2.4.20 and that a DAO test runs under JUnit 6. If
  they don't, the fallback is the same model in a single index file: in memory, rewritten
  atomically at most every 2 s and at the end of each region step. It is fully specified by the
  same tests.

*Alternatives (asked, with the comparison table from the explore session):* files plus an index
file (fallback), and framework SQLite (untestable on the JVM here).

### D6. `DemTiles` replaces `DemTileFetcher`'s cache
`DemTiles.fetch(key, forRegion: Long?)` returns `DemTile` as today, so `TileCache` stays
unchanged:
1. Stored and fresh (`now < freshUntil`): return it, `Found` or `Missing`, without a request.
2. Stored and expired, online: a conditional GET (`If-None-Match` or `If-Modified-Since`).
   - 304: refresh `freshUntil`.
   - 200: replace the file and the validators; owners are unchanged.
   - 404: set `MISSING`.
   - Failure: return the stored tile whatever its age.
3. Not stored: a GET. 200 stores `FOUND`, 404 stores `MISSING`, and a failure returns
   `Unavailable` (not stored).

`freshUntil` = response time + `max-age`, else `Expires`, else 7 days. With `forRegion`, the
request goes through the Mapterhorn `RateLimiter`, and the tile gets a `region_dem_tile` row in
the same transaction. The OkHttp `Cache` is removed from the DEM client. The old
`cacheDir/dem-tiles` is deleted once at start; it is a cache, so nothing is migrated. Recording
404s makes `TileCache`'s upsampling work offline (spec "Zoom not published").

### D7. Region download: one WorkManager worker for all regions, oldest first
- **Start.** `Download visible area` inserts a `region` row with state `QUEUED` and enqueues the
  unique work `offline-regions` with `ExistingWorkPolicy.KEEP`.
  - Constraints: `NetworkType.CONNECTED` (user decision: any network) and
    `setRequiresStorageNotLow(true)`.
- **Worker.** `RegionDownloadWorker` is a `CoroutineWorker` that calls `setForeground` with a
  `dataSync` foreground service type and the progress notification. It takes the oldest region
  that is not complete, downloads it, and repeats until none is left. So only one region
  downloads at a time, and the queue survives process death (spec "Background download").
- **Per region, two parallel parts:**
  - **Map:** create the MapLibre region (bounds, `minZoom` 5, `maxZoom` 16, `pixelRatio` 1, style
    URL from D3) or look up the existing one by `mapRegionId`. Set it `STATE_ACTIVE` and follow
    its `OfflineRegionStatus`.
  - **DEM:** the tile list from `core` (D8). For each tile: when it is not stored, call
    `DemTiles.fetch(key, forRegion = id)`; when it is stored, add only the `region_dem_tile` row,
    without a request. A failure other than 404 is retried after 5 s, doubling up to 5 min.
- **Progress** = (map `completedResourceCount` + DEM tiles obtained) / (map
  `requiredResourceCount` + DEM tile count), rounded down, and never 100 before both parts are
  complete. It is written to the `region` row and the notification at most once per second.
- **Completion.** When MapLibre reports `isComplete` and every DEM tile is obtained: set the
  state `COMPLETE`, `completedAt`, `mapBytes`, then update the ambient maximum (D2).
- **Interruption.** When WorkManager stops the worker (constraint lost), the MapLibre region is
  set `STATE_INACTIVE`, and the worker returns. WorkManager starts it again when the constraints
  hold, also after a process death or a reboot. A write error for lack of space returns
  `Result.retry()`.
- **Status texts.** The Offline page derives them from the row and `WorkInfo`:
  - `Downloading <p> %` for the region being downloaded while the work is running;
  - `Waiting` for a queued region behind it;
  - when the work is enqueued but blocked: `waiting for network` if `NetworkMonitor` reports
    offline, else `waiting for storage`.

*Alternatives:* a plain foreground `Service` (the resume after network, storage, process death
and reboot would all be ours to write); one work request per region (cancelling or ordering them
is harder than one queue).

### D8. Area and tile ranges in `core`
New `OfflineArea.kt`:
- `GeoBounds(south, west, north, east)`, from `MapArea.corners()`;
- `extend(bounds, marginMetres)` with 111.19 km per degree (EARTH_RADIUS) and cos φ at the
  extended edge farther from the equator;
- `tileRange(bounds, zoom)`: the x/y range of the Web-Mercator tiles intersecting the bounds;
- `regionDemTiles(bounds)`: z14, z13 at 1.5 km, z12 at 6 km, z11 at 25 km, z10 at 150 km;
- `regionMapTileCount(bounds)`: tile zooms 6 to 17.

These are pure functions, unit-tested against the spec's counts. The page's estimate uses them.
MapLibre covers the bounds with its own code, which is why the spec allows ±2 % for edge tiles.
Width, height and the minutes for the estimate are computed there too.

### D9. The Offline page is a third screen of `MainActivity`
- **Navigation.** `MainActivity`'s flag becomes `Screen { MAP, ABOUT, OFFLINE }`, saved the same
  way. `MapScreen(onOfflineClicked = { area -> … })` passes the `MapArea` of the moment the button
  was tapped. That is the spec's "visible when the page was opened", so the page never follows
  a moving map.
- **Page.** `OfflineScreen` + `OfflineViewModel`. It shows:
  - the estimate (D8);
  - `Download visible area`, disabled below zoom 11;
  - the region list, a `Flow` from Room combined with `WorkInfo` and `NetworkMonitor`;
  - the storage lines: map = size of `mbgl-offline.db` in `filesDir`, DEM = Σ `dem_tile.bytes`
    (user decision: totals per kind, since MapLibre does not report the ambient size);
  - the delete dialog.
- **Button.** `MapLabels`' top-left column gets a `Row` holding the ⓘ button and the Offline
  button, each 48 dp, with 8 dp between them. The column's widest element (the coordinates label)
  is wider than the row, so the column does not grow toward the overlay toggle. The 360 dp check
  of #7 (D8) is repeated.
- **Permission.** On Android 13+, tapping `Download visible area` asks for
  `POST_NOTIFICATIONS` first, when it is not yet decided. The download starts whatever the
  answer (spec "Background download").

### D10. Deleting a region
In one Room transaction: set the region's state `DELETED` and delete its `region_dem_tile`
rows. Tiles left without an owner become browsed with their old `lastUsed`, so the eviction trims
them oldest first (user decision: they become browsed tiles).

Then, off the transaction:
- if the worker is downloading this region, it notices the state and moves on;
- `OfflineRegion.delete` on the map region (its unshared tiles become ambient; verified);
- update the ambient maximum (D2);
- run the DEM eviction;
- delete the `region` row.

On a restart, rows in state `DELETED` finish these steps.

### D11. Performance budgets
- **Opening the Offline page:** the estimate is a sum over 12 + 5 tile ranges, well under 1 ms;
  the first frame is ≤ 100 ms. The region list and storage sizes load off the main thread
  (≤ 200 ms for 20 regions).
- **Deleting a region:** the entry disappears at once, and the rest runs in the background. Budget
  ≤ 5 s until its space shows as freed, measured on the device.
- **Browsing with a stored DEM tile:** reading it from the file (~150 KB) plus the index lookup,
  ≤ 5 ms per tile, comparable to today's OkHttp disk cache. Logged with the existing
  `TileLoads` counters, split into network and store.
- **Evicting 512 MiB of browsed DEM tiles:** never on the main thread; ≤ 1 s per 100 tiles.

## Risks / Trade-offs

- [The download may not complete with the locally answered style (D3)] → task 2 is a device
  spike before anything builds on it; `putResourceWithUrl` is the fallback.
- [Room or KSP may not build with AGP 9.4 / Kotlin 2.4, or may not run under JUnit 6] → task 1
  checks this first; the files + index file fallback of D5 changes only the store's internals.
- [Android 14+ restricts `dataSync` foreground services (Android 15: 6 h per day)] → a region
  takes about 25 minutes, so the limit is far away. If it is hit, WorkManager reschedules the
  work.
- [The map region and the DEM tiles can drift apart after a crash between the two parts] → the
  worker looks up both by region id on every start and treats the region as complete only when
  both are.
- [Storage estimates are based on average tile sizes] → the device check measures the real
  sizes of a zoom-11 region; the proposal's numbers are only estimates.
- [Overlapping regions can make the browsed map share exceed 512 MiB (D2)] → documented in the
  spec; bounded by the overlap.
- [OpenTopoMap or Mapterhorn may object to bulk downloads] → the rate limit; the user contacts
  both. The limit is one constant, and becomes configurable in #8.
- [MapLibre's `offlineUsage` flag is also set for style and source requests of a region] → they
  are 1–2 requests per region; the style is answered locally anyway.

## Migration Plan

- **First start after the update:** delete `cacheDir/dem-tiles` (the old DEM HTTP cache, not
  migrated), create `offline.db`, and set the ambient maximum to 512 MiB (no regions yet).
  Browsed map tiles already in MapLibre's database stay.
- **Rollback:** an older app version ignores `offline.db`, `filesDir/dem` and the MapLibre regions,
  which then only take space. Deleting the app's storage clears them.

## Open Questions

- The real sizes of map tiles at z16–z17 and of DEM tiles at z10–z14 in the Alps. They affect only
  the proposal's estimates, not the design; the device check records them.
