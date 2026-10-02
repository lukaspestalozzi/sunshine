# Proposal

## Why

Hikers need the app where there is no network: on the trail, in huts, in valleys without
reception. Today the map tiles live in MapLibre's 50 MB ambient cache and the DEM tiles in a
100 MiB HTTP cache in the app's cache directory. The operating system may clear both at any time, and
nothing can be prepared for a trip. The project's hard constraint says that once a region is
downloaded, every feature works without network. This change delivers it, and it avoids two
failure modes of the first implementation: a download that stored map tiles but no elevation,
and deleting one region deleting the tiles of all regions.

Roadmap: implements entry #6, `add-offline-regions`, of `docs/roadmap.md`. Its open decisions
(fixed region list vs. custom area, the tile servers' bulk-download policies) were settled in the
explore session of 2026-09-30: the region is the visible area, and downloads are rate-limited
while the user asks OpenTopoMap and Mapterhorn separately (user decision). The change also adds
entry #8, `add-settings`, to the roadmap (user decision, 2026-09-30): a settings page that makes
the limits fixed here configurable.

## What Changes

- `app`: **every tile is kept.** Every map and DEM tile the app fetches while browsing is stored
  in the app's persistent storage, which the OS doesn't clear, not in a cache (user decision). Browsed tiles
  have a size limit of 512 MiB for map tiles and 512 MiB for DEM tiles; above it, the least
  recently used browsed tiles are removed first (user decision). The DEM tile cache of
  elevation-data (100 MiB in the cache directory) is replaced by this store.
- `app`: **download the visible area.** An Offline page, opened from a new button next to the ⓘ
  button, downloads the area the map showed when the page was opened (user decisions):
  - only at map zoom 11 or more (about 10.5 × 22 km on a 400 × 850 dp phone);
  - map tiles for map zooms 5 to 16, i.e. OpenTopoMap tiles z6 to z17, the full detail
    (user decision, after the correction that a 256 px raster tile is one zoom above the map
    zoom);
  - every DEM tile the horizon and the overlay read for any location in the area: z14 and z13
    within 1.5 km, z12 within 6 km, z11 within 25 km and z10 within 150 km of the area (user
    decision: the full 150 km, so that offline results equal online ones).
- `app`: **rate limit.** Region downloads start at most 5 requests per second and keep at most 2
  in flight per tile server (user decision). Browsing, the horizon and the overlay are not
  limited. A region at map zoom 11 takes about 25 minutes.
- `app`: **download in the background.** The download continues when the app is left or the
  screen is off, with a progress notification. It uses any network (user decision). An
  interrupted download shows `Incomplete (63 %)` and resumes by itself when the network or
  storage returns, without fetching stored tiles again.
- `app`: **regions are pinned.** Region tiles don't count towards the browsed limit and are never
  removed while a region uses them. The Offline page lists the regions with the centre's
  coordinates, the download date and the size, and deletes a region after a confirmation. Tiles
  that another region uses stay. The others become browsed tiles, subject to the limit.
- `app`: **freshness.** When online, stored tiles follow the servers' caching headers: an expired
  tile is revalidated when it is used. Offline, a stored tile is used whatever its age (user
  decision).
- `app`: the Offline page shows how much of each browsed limit is used.
- `app`: a DEM tile the server does not publish (HTTP 404) is recorded as missing, so offline the
  coarser zoom is used there exactly as online, instead of the result becoming unknown.

## Capabilities

### New Capabilities

- `offline-regions`: kept (browsed) tiles and their limits, the Offline button and page, region
  download (area, zooms, DEM margins), rate limit, background download and interruption, region
  list and deletion, freshness of stored tiles, and use of stored tiles without network.

### Modified Capabilities

- `elevation-data`: "DEM tile loading and cache" is replaced: DEM tiles are kept in persistent
  storage with a 512 MiB limit for browsed tiles, and a missing tile (404) is recorded.
  "Unknown elevation" refers to the tile store instead of the cache.
- `map-view`: "Missing map tiles": without network, stored tiles are shown and only areas
  without stored tiles stay blank. The ⓘ button's requirement is unchanged; the Offline button
  sits to its right (specified in `offline-regions`).

## Non-goals

- Fixed or curated region lists, drawing a custom area, or regions larger than the visible area.
- Renaming regions and showing region outlines on the map (user decisions).
- Configuring the size limits, the region depth or the rate limit (roadmap #8, `add-settings`).
- A button to clear the browsed tiles (candidate for #8).
- An "Update region" action that downloads a region again; tiles are refreshed only when used
  online.
- Wi-Fi-only downloads or asking before using mobile data (user decision: any network).
- Asking the tile servers' maintainers for permission (the user handles that separately).
- Offline place search, GPS, or any feature beyond map, elevation, horizon and overlay.
- Reusing overlay work across pans (polishing item from #5).

## Impact

- **Code (`app`):** a persistent DEM tile store (tile files plus an index of owners, last use and
  validators) replacing the OkHttp cache in `DemTileFetcher`; region model and region download
  (map tiles through MapLibre's offline regions, DEM tiles through the store); a rate-limited
  HTTP path for region downloads, including a custom MapLibre `ModuleProvider`/`HttpRequest` that
  throttles requests flagged `offlineUsage`; the OpenTopoMap style moved to an asset so that
  offline regions can reference it by URL; MapLibre's ambient cache limit set to 512 MiB plus the
  regions' size; a background worker with a notification; the Offline page and its button in
  `MapLabels`; strings and an icon.
- **Code (`core`):** tile ranges of an area with a margin (pure geometry), unit-tested.
- **Dependencies:** WorkManager; Room with KSP and the bundled SQLite driver for JVM tests (user
  decision: Room, with a files-plus-index-file store as fallback if Room does not build with AGP
  9.4 / Kotlin 2.4 or does not run under JUnit 6).
- **Permissions:** `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `POST_NOTIFICATIONS`
  (asked on the first download on Android 13+).
- **Storage:** up to 1 GiB of browsed tiles, plus about 180 MiB of map tiles and 50–60 MiB of DEM
  tiles per zoom-11 region (estimates from average tile sizes, to be measured).
- **Network:** region downloads of about 7,400 map and 400 DEM tile requests at ≤ 5 requests/s
  per server; fewer requests while browsing, because kept tiles are not fetched again.
- **Tests:** JVM unit tests for the tile ranges, the store (owners, eviction, missing tiles,
  freshness), the rate limiter, region state and progress, and the Offline page's texts; a
  device check of a full region download in airplane mode.
- **Docs:** `docs/roadmap.md` (entry #6 status, new entry #8 `add-settings`) and `CLAUDE.md`
  (new `app` files).
