# Tasks

## 1. Build: Room and WorkManager

- [ ] 1.1 Add Room (runtime, KTX, compiler through KSP) and `androidx.sqlite:sqlite-bundled` to `gradle/libs.versions.toml` and `app/build.gradle.kts`, at the latest stable versions compatible with AGP 9.4.1 and Kotlin 2.4.20 (design D5). Write a throwaway `@Entity`/`@Dao`/`@Database` and a JUnit 6 test that inserts and reads one row through `Room.inMemoryDatabaseBuilder` with `BundledSQLiteDriver`. Verify: `./gradlew :app:testDebugUnitTest --tests "*RoomSmokeTest*"` passes and `./gradlew assembleDebug` succeeds. If either fails, record the raw error in design D5, switch D5 to the files + index file fallback with the user's sign-off, and remove the Room dependencies. Delete the throwaway classes once group 4 has real ones.
- [ ] 1.2 Add `androidx.work:work-runtime-ktx` at the latest stable version (design D7). Verify: `./gradlew assembleDebug` succeeds and `./scripts/verify-local.sh --quick` reports no ktlint issue.

## 2. core: areas and tile ranges

- [ ] 2.1 Write `OfflineAreaTest` first (design D8):
  - `tileRange` of 46.55–46.65° N, 7.85–7.95° E at z17 → 1998 tiles, and the sum over z6–z17 → 2752;
  - `regionDemTiles` of the same bounds → 63 tiles at z14, 20 at z13, 20 at z12, 30 at z11 and 169 at z10 (spec "Tiles of a fixed area");
  - `extend` by 150 km at 46.6° N moves the southern edge by 1.3490° (150 / 111.19);
  - a `MapArea` of 400 × 850 dp at 46.5935° N, 7.9091° E, zoom 11 → bounds 46.4931–46.6937° N, 7.8404–7.9778° E (±0.0001°), 7406 map tiles, 413 DEM tiles, 10.5 × 22.3 km and 25 min (spec "Phone at zoom 11").

  Then implement `GeoBounds`, `extend`, `tileRange`, `regionDemTiles`, `regionMapTileCount` and the estimate in `core/.../OfflineArea.kt`. Verify: `./gradlew :core:test --tests "*OfflineAreaTest*"` passes.

## 3. app: rate limiter, MapLibre HTTP hook and the region style

- [ ] 3.1 Write `RateLimiterTest` first, with virtual time: 20 acquisitions that each hold for 0 ms start at 0, 200, 400, … ms, and no 1-second window holds more than 5 starts; with holds of 1 s, never more than 2 are held at once; cancelling a waiting acquisition releases nothing and does not block the next one. Then implement `RateLimiter` in `app/.../network/RateLimiter.kt` (design D4). Verify: `./gradlew :app:testDebugUnitTest --tests "*RateLimiterTest*"` passes.
- [ ] 3.2 Move the style JSON out of `MapLibreMap.kt` into one shared constant, used by `fromJson` as before. Write `SunshineHttpRequestTest` first, against a fake delegate and a fake `HttpResponder`:
  - browsing requests reach the delegate at once, even while 2 region requests hold the limiter;
  - region requests (`offlineUsage = true`) wait for the host's limiter and release it on `onResponse` and on `handleFailure`;
  - `cancelRequest` on a waiting request never calls the delegate;
  - `https://sunshine.invalid/opentopomap-style.json` is answered with 200 and the style's bytes, without calling the delegate.

  Then implement `SunshineHttpRequest` and its `ModuleProvider`, and install them in `SunshineApp.onCreate` before `MapLibre.getInstance` (design D3). Verify: `./gradlew :app:testDebugUnitTest --tests "*SunshineHttpRequestTest*"` passes, and on the device the map still loads its tiles.
- [ ] 3.3 Device spike (design D3, risk 1): in a debug build, create a MapLibre offline region for 46.60–46.62° N, 7.90–7.92° E at map zooms 5–16 with the `sunshine.invalid` style URL, and log its `OfflineRegionStatus` to completion. Verify: logcat shows `isComplete = true` with `requiredResourceCount` ≥ 100, request starts to `tile.opentopomap.org` are ≥ 200 ms apart, and after airplane mode is switched on and the app restarted, the area's map shows at zoom 16 without a blank tile. If the style is not accepted, switch D3 to `putResourceWithUrl`, and record the raw error and the switch in the design.

## 4. app: the DEM store

- [ ] 4.1 Write `DemTileStoreTest` first, with an in-memory Room database (`BundledSQLiteDriver`) and a `@TempDir` (design D5):
  - a stored tile is read back byte-identical and marked `FOUND`; a 404 is stored as `MISSING`;
  - browsed tiles above 512 MiB are evicted least recently used first, until at most 512 MiB remain; region tiles are neither counted nor evicted (spec "Limit reached");
  - a tile in regions A and B loses only A's claim when A is deleted; a tile only in A becomes browsed with its old `lastUsed` (spec "Delete a region");
  - startup deletes a file without a row and a row without a file.

  Then implement the entities, the DAO, `OfflineDatabase` and `DemTileStore`. Use small byte sizes and a limit passed in the constructor, so that tests stay fast. Verify: `./gradlew :app:testDebugUnitTest --tests "*DemTileStoreTest*"` passes.
- [ ] 4.2 Write `DemTilesTest` first, against a JDK `HttpServer` as `DemTileFetcherTest` does (design D6):
  - a fresh tile makes no request (spec "Fresh tile");
  - an expired tile online sends `If-Modified-Since`, and a 304 keeps it and makes it fresh for 7 days (spec "Expired tile online");
  - a 200 replaces it and keeps its regions;
  - an expired tile with the server unreachable is returned (spec "Expired tile offline");
  - a 404 is recorded and returned as `Missing` offline (spec "Missing zoom offline");
  - `freshUntil` comes from `max-age`, else `Expires`, else 7 days;
  - a region fetch waits for the Mapterhorn limiter and adds the region's claim, and a stored tile gets the claim without a request;
  - the User-Agent test of `DemTileFetcherTest` still holds.

  Then replace `DemTileFetcher`'s cache with `DemTiles`, remove the OkHttp `Cache`, and delete `cacheDir/dem-tiles` once at start. Verify: `./gradlew :app:testDebugUnitTest --tests "*DemTiles*" --tests "*TileCacheTest*" --tests "*ElevationRepositoryTest*"` passes.
- [ ] 4.3 Wire `DemTiles` into `SunshineApp` in place of `DemTileFetcher`, and split the debug `TileLoads` log into network and store. Update `CLAUDE.md`'s `app` row: `elevation/` gains the persistent store. Verify: `./scripts/verify-local.sh` passes. On the device, browse Interlaken at zoom 13 with `Sun & shade` on, switch on airplane mode and restart the app: `Altitude 568 m` is shown and the overlay has no unknown cell there (spec "Browsed area offline", elevation-data "App cache cleared").

## 5. app: the ambient limit for map tiles

- [ ] 5.1 Write `AmbientLimitTest` first: no region → 512 MiB; regions of 183 MiB and 40 MiB → 735 MiB; the limit is set again after 16 MiB of progress, not before. Then implement it and call it in `SunshineApp.onCreate` before any map loads, using the region sizes from the database (design D2). Verify: `./gradlew :app:testDebugUnitTest --tests "*AmbientLimitTest*"` passes, and on the device logcat shows the limit set before the first map load.

## 6. app: region download

- [ ] 6.1 Write `RegionProgressTest` first: 4775 of 7819 → 61 %; not 100 % while the map part is complete and one DEM tile is missing; the status text for each state (`Downloading 63 %`, `Waiting`, `Incomplete (63 %) · waiting for network`, `Incomplete (40 %) · waiting for storage`, `2026-10-02 · 183 MiB`, with 183.4 MiB rounded half up) (spec "Progress", "Region list", "Interrupted download"). Then implement the progress and status functions. Verify: `./gradlew :app:testDebugUnitTest --tests "*RegionProgressTest*"` passes.
- [ ] 6.2 Write `RegionDownloadTest` first, with a fake map part and `DemTiles` on a fake server:
  - regions download oldest first, one at a time;
  - a region is `COMPLETE` only when both parts are;
  - a stored DEM tile is claimed without a request;
  - a 500 is retried and the region stays incomplete meanwhile;
  - a region deleted while downloading stops, and no further request is made for it (spec "Delete while downloading");
  - after a restart the download continues without refetching stored tiles (spec "App closed").

  Then implement `RegionDownloader` as plain code, independent of WorkManager (design D7). Verify: `./gradlew :app:testDebugUnitTest --tests "*RegionDownloadTest*"` passes.
- [ ] 6.3 Implement `RegionDownloadWorker` (foreground `dataSync`, notification channel `Offline downloads`, `Downloading offline map` with the progress in percent, constraints `CONNECTED` and storage not low, unique work `offline-regions` with `KEEP`), and the MapLibre map part of `RegionDownloader`. Add `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC` and `POST_NOTIFICATIONS` to the manifest. Verify: `./scripts/verify-local.sh` passes (lint clean, no new suppression). On the device: start a zoom-11 region, switch to another app and turn the screen off for 10 minutes; the notification shows progress and the download has continued (spec "App left during a download").

## 7. app: the Offline page

- [ ] 7.1 Write `OfflineViewModelTest` first:
  - the estimate text for the phone area of 2.1;
  - below zoom 11, the button is disabled and `Zoom in to map zoom 11 or more to download an area` is shown (at 10.9);
  - `No offline regions yet` with no region;
  - regions newest first, named `46.5935° N, 7.9091° E`;
  - the storage lines `Map tiles: 395 MiB` and `Elevation tiles: 281 MiB` for 395.2 and 280.6 MiB;
  - `Delete` removes the region from the list at once, and `Cancel` leaves it.

  Then implement `OfflineViewModel` (design D9, D10). Verify: `./gradlew :app:testDebugUnitTest --tests "*OfflineViewModelTest*"` passes.
- [ ] 7.2 Implement `OfflineScreen` (estimate, button, list with delete button `Delete region` and the confirmation `Delete the offline region <name>?` with `Delete`/`Cancel`, storage lines, the limit sentence), the `Screen` switch in `MainActivity`, the Offline button (`Offline maps`, 48 dp, right of ⓘ) in `MapLabels`, and the notification permission request on Android 13+. Add the strings and a Material Symbols icon as a vector drawable. Update `CLAUDE.md`'s table with the `offline/` package and `core`'s `OfflineArea.kt`. Verify: `./scripts/verify-local.sh` passes. On the device, check each of these:
  - at 360 dp, portrait and landscape, the ⓘ button, the Offline button and the toggle are fully visible and do not overlap (spec "Narrow screen");
  - Interlaken at zoom 12 with `Sun hours`, Offline page, back → unchanged (spec "Open and return");
  - deleting a region asks first, and `Cancel` keeps it.

## 8. Integration

- [ ] 8.1 Device check of a whole region (spec "Offline use of a region", "Overlapping regions", "Network lost"). Download the phone area at zoom 11 around Lauterbrunnen (46.5935° N, 7.9091° E) and note the time taken and the map and DEM sizes. Switch off the network at about 60 % and on again. Verify: the entry shows `Incomplete (… %) · waiting for network`, the download resumes and completes, and it takes at least 24.6 min. In airplane mode, at zoom 12 on 2025-12-21 at 12:00 with `Sun & shade`, the map has no blank tile, the altitude is known, and the overlay has no unknown cell; the overlay matches a screenshot taken online. Download a second region overlapping the first, delete the first, and the second still works in airplane mode. Record the sizes and times in design.md under Open Questions (answered).
