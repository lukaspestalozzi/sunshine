# Design

## Context

See proposal.md (Why) and the specs `elevation-data` and `map-view` (delta). Starting point, as
built by `add-app-shell` and `add-sun-position`:

- `core`: `GeoPoint`, `sunPosition`, `sunDay`. Kotlin/JVM, toolchain 17, JUnit 6. No
  image-decoding facility: `javax.imageio` does not exist on Android, and `BitmapFactory` does
  not exist on the JVM.
- `app/map`: `MapViewModel(savedState, isOnline, clock, computeDispatcher)` derives `sun` from
  `combine(camera, selectedTime)` → `conflate()` → `map` → `flowOn`. `SunPanel` shows the sun
  values in a `FlowRow`. `MapLabels` draws the attribution `Label` at the bottom start.
- `app/network`: `UserAgentInterceptor`, and `NetworkMonitor.isOnline`. `SunshineApp` builds the
  OkHttp client for MapLibre (OkHttp 4.12.0, pinned to MapLibre's version). There is no DI
  framework; `MapScreen.kt` builds the ViewModel through a factory.
- Facts checked while drafting (2026-09-26, details in `investigations/dem-source-evaluation.md`):
  - `https://tiles.mapterhorn.com/{z}/{x}/{y}.webp`: 512 × 512 px lossless WebP (`VP8L`), RGB,
    no alpha and no ICC profile, Terrarium encoding, pixel-centre registration. A z12 tile is
    ~140 KiB and 6.7 km wide at 46.7° N.
  - Responses carry `Cache-Control: public, max-age=604800` (7 days) and `Last-Modified`. A tile
    without data (open sea, 30° N 40° W) answers `404`; an invalid coordinate answers `500`.
  - Point RMSE of z12 against swissALTI3D is 1.8 m (0.3 m on slopes < 10°), and summits read
    0.5–7.3 m low in Switzerland. The spec's reference values come from this spike.
  - Mapterhorn publishes no usage policy for its tile endpoint (see Open Questions).

## Goals / Non-Goals

**Goals:**
- A point-elevation API that change 4 (`add-terrain-horizon`) can reuse for its horizon rays. The
  tile math and interpolation live in `core`; the fetching lives in `app`.
- Unknown is a value in the API, so no caller can fall back to a default height.
- Everything Mapterhorn-specific (URL, tile size, zoom, encoding, attribution link) sits in one
  small class.

**Non-Goals:**
- Region downloads and cache management (change 6).
- Prefetching tiles around the selected location, or batching for horizon rays (change 4 decides
  its access pattern).
- A DI framework (see D9).

## Decisions

### D1. Source: Mapterhorn (spike result)
Mapterhorn z12 tiles (Terrarium, lossless WebP, 512 px). The tiles are built from national LiDAR
terrain models: CH 0.5 m, AT/DE/FR/SI 1 m, northern Italy 2–5 m, TINITALY 10 m elsewhere in Italy.
*Alternatives (all measured against swissALTI3D at 300 points):*
- AWS Terrarium: RMSE 19.2 m, Jungfrau −172 m, unmaintained since 2018.
- AWS Skadi HGT: RMSE 26.6 m, errors up to 347 m.
- Copernicus GLO-30: a surface model including trees, RMSE 17.1 m, Finsteraarhorn −287 m.
- Sonny's LiDAR DTM: good data, but distributed only via Google Drive, so it would have to be
  re-hosted.

The Mapterhorn-specific values are constants of one class, `MapterhornTiles` in
`app/elevation`: base URL, tile size 512, zoom 12, attribution URL. `core` only knows "square
Terrarium tiles of size N at zoom Z", so another Terrarium source would only change that class.
No fallback source is designed (see Risks).

### D2. Zoom 12 (user decision)
13 m pixels, 1.8 m RMSE, one ~140 KiB tile per 6.7 km. z13 halves the error but needs 4× the
tiles; if change 4 needs finer data near the observer, it adds that.

### D3. On-demand fetching with OkHttp's disk cache (user decision: on demand + disk cache)
- A dedicated `OkHttpClient` for DEM tiles, created in `SunshineApp`, with:
  - `UserAgentInterceptor`, the same one the map client uses;
  - `Cache(File(cacheDir, "dem-tiles"), 100 MiB)`;
  - timeouts of 10 s connect and 20 s read.

  The map client stays separate because its settings are tuned for MapLibre.
- Tiles are cached through HTTP semantics: the server's 7-day `max-age` applies, and after that
  OkHttp revalidates with `If-Modified-Since`.
- Offline, or when a network request fails, the fetcher retries once with
  `CacheControl.FORCE_CACHE`. That serves a cached tile even when it is stale, which satisfies the
  spec's "served from that cache, even without network". Only when that also fails is the tile
  unavailable.
- Why the cache directory: you chose the disk cache. Android may clear it under storage pressure,
  which is acceptable until change 6 brings managed regions. 100 MiB holds ~700 tiles, about
  31,000 km².
*Alternatives:* permanent files without a cap (grows unbounded until change 6); memory only (more
traffic to Mapterhorn, nothing offline).

### D4. Tile endpoint, not PMTiles (user decision)
`GET https://tiles.mapterhorn.com/12/{x}/{y}.webp`: one request per tile, and no PMTiles reader
dependency. The spike verified that the bytes are identical to the PMTiles archives, so change 6
can switch to range requests without changing any values.

### D5. Decoding: `app` decodes the WebP, `core` converts pixels to heights (user decision)
- `app`: `BitmapFactory.decodeByteArray(bytes, …, Options { inPreferredConfig = ARGB_8888 })`,
  then `getPixels` into an `IntArray` (ARGB, row-major).
- `core`: `terrariumHeights(pixels: IntArray): FloatArray`, where `r·256 + g + b/256 − 32768` fits
  a `Float` exactly for this range (Terrarium resolution is 1/256 m). A tile's heights take
  1 MiB.
- Unverified assumption: `BitmapFactory` decodes lossless WebP bit-exactly (no alpha, no ICC
  profile, so no colour conversion). It can't be tested on the JVM (no Robolectric; see the
  reverted `add-ui-screenshot-tests`). The on-device check compares the panel with the reference
  values: an inexact decode shows up as errors of metres (G channel) up to hundreds of metres
  (R channel).
*Alternative:* a pure-Kotlin VP8L decoder in `core`, with end-to-end JVM tests but ~1000+ lines
or a new dependency.

### D6. Every decoded height counts as known (user decision)
Mapterhorn fills gaps in national coverage with Copernicus GLO-30 (a surface model), and the tiles
don't mark which source a pixel came from. The Alps are fully covered by national terrain models,
so any decoded value is "known". Unknown arises only from the cases listed in the spec.
*Alternative:* treat values outside a fixed Alps bounding box as unknown.

### D7. `core` API
New files in `com.sunshine.core`:

```kotlin
data class TileKey(val zoom: Int, val x: Int, val y: Int)

/** Square Terrarium height tile; heights row-major, one per pixel centre. */
class HeightTile(val size: Int, val heights: FloatArray)

fun terrariumHeights(argb: IntArray): FloatArray

/** Tiles whose samples the bilinear interpolation at [point] needs (1, 2 or 4); empty outside ±85.0511°. */
fun tilesFor(point: GeoPoint, zoom: Int, tileSize: Int): Set<TileKey>

/** Bilinear elevation in metres; every key of tilesFor(point, …) must be present in [tiles]. */
fun interpolateElevation(point: GeoPoint, zoom: Int, tileSize: Int, tiles: Map<TileKey, HeightTile>): Double
```

- The pixel coordinate is Web-Mercator `x · 2^z · size − 0.5` (pixel centres). The four samples
  are `floor` and `floor + 1` in each axis, wrapped into the neighbouring tile at the edges.
  `x` wraps around the antimeridian.
- Unknown is modelled in `app` (D8): `core` interpolates only when all tiles are present, so it
  needs no nullable result.

### D8. `app`: repository and ViewModel state
- `app/elevation/MapterhornTiles.kt`: the constants of D1 and `url(key)`.
- `app/elevation/DemTileFetcher.kt`: `suspend fun fetch(key): ByteArray?` using the DEM
  `OkHttpClient` and the D3 fallback. It returns `null` on 404, 5xx, IO error, or a cache miss
  while offline. It uses OkHttp's asynchronous `enqueue` inside `suspendCancellableCoroutine`, so
  cancelling the caller cancels the HTTP call (changed after review; planned as a blocking call
  on `Dispatchers.IO`).
- `app/elevation/ElevationRepository.kt`:
  - `suspend fun elevation(point): Elevation`, with
    `sealed interface Elevation { data class Known(val metres: Double) : Elevation; data object Unknown : Elevation }`;
  - `fun cachedElevation(point): Elevation?`, a fast path that answers only from memory.

  It keeps decoded tiles in an in-memory LRU of 8 tiles (8 MiB) and loads only the tiles
  missing from it. A tile that loads is kept even when a neighbour fails, so a retry fetches only
  the missing one. If any required tile is unavailable or does not decode, the result is
  `Unknown`. The decoder rejects images that are not 512 × 512.
- `MapViewModel` gains `elevation: StateFlow<ElevationState>`, where `ElevationState` is
  `Loading | Known(metres) | Unknown`:
  - Input: `combine(camera.map { it.center }.distinctUntilChanged(), isOnline)`, so zooming
    without moving triggers nothing.
  - For each input, emit the memory fast path if it exists. Otherwise emit `Loading`, then the
    repository result.
  - Latest wins: a `channelFlow` cancels the previous lookup (and so its HTTP call) with
    `cancelAndJoin` before starting the next. A slow tile therefore never delays the current
    location, and a previous location's value is never emitted for the new one. `mapLatest`
    would do the same but is experimental (changed after review; planned as `conflate()` +
    `transform`, which let a slow request block the next location for up to 20 s).
  - A change of `isOnline` from false to true re-evaluates the current location, which gives the
    "connectivity returns" scenario. A failed fetch is not retried until the location or
    connectivity changes; that follows from the flow being driven only by those inputs.
- The repository is created in `SunshineApp` (it owns the client) and passed into the
  ViewModel factory in `MapScreen.kt`. For tests it is an interface-free class with a fake
  fetcher: `ElevationRepository(fetch: suspend (TileKey) -> ByteArray?, decode: (ByteArray) -> IntArray?)`.
  The decoder is a function parameter, so JVM tests avoid `BitmapFactory`.

### D9. Still no DI framework
The ViewModel gets one more constructor parameter. Wiring in `SunshineApp` and `MapScreen.kt` is
about 5 lines, which still does not justify Koin (same reasoning as `add-sun-position` D8).

### D10. Panel row and attribution (user decisions)
- Panel: a `Value` in `SunValues` labelled `Altitude`. `Elevation` is already the sun's elevation,
  and `Altitude` is the word hikers use for the height of a place. `formatAltitude(state)` goes in
  `SunFormat.kt`, with `BigDecimal` `HALF_UP` and `Locale.ROOT` like the other formatters. The
  texts `…` and `unknown` are constants there, testable on the JVM.
  - The panel shows `Altitude …` while loading even if the sun values are already known, because
    the row belongs to the location, not to the time.
  - `SunValues` currently renders only once `sun != null`. The altitude row is rendered
    independently of that.
- Attribution: a second `Label` in `MapLabels` directly under the map attribution, with text
  `Elevation: © Mapterhorn and its sources`. It is clickable and opens `MapterhornTiles`'
  attribution URL through `LocalUriHandler`. Assumption: this satisfies the CC BY attribution
  terms of the underlying sources, because the page names every source.

### D11. Reference values and fixtures
- Oracles: swissALTI3D 2 m bilinear, recorded in `investigations/dem-source-evaluation.md`.
  Mapterhorn z12 at those points today: Interlaken 568.00, Kleine Scheidegg 2061.31, the
  four-tile corner at 46.55886° N, 7.91016° E 1132.35 (reference 1132.08), Jungfrau 4151.53.
- Tiles are rebuilt from time to time (Last-Modified 2026-09-11/13), so tests never fetch. `core`
  tests use fixtures holding the real 2 × 2 sample neighbourhoods around each oracle point: the
  RGB values of the needed pixels of the pinned tiles, fetched once and written to a small Kotlin
  test source. Every other pixel of a fixture tile is `NaN`, so reading a wrong pixel fails the
  test visibly.

### Performance budget
- Trigger: every camera-move event (and reconnect).
- Memory fast path, when all needed tiles are decoded in memory: tile math plus 4 samples,
  < 0.1 ms. Pans within loaded tiles therefore update the row on every frame.
- Decoding from the disk cache: estimated ≤ 50 ms per tile on a mid-range phone (a 512² WebP
  decode plus 262,144 conversions; not measured). It runs on `Dispatchers.Default` (HTTP on
  OkHttp's threads), so the UI thread is never blocked. A new position cancels the lookup of the
  previous one, so a fast pan does not queue work.
- Network: one ~140 KiB request per new tile, at most 4 per location. They are fetched
  concurrently, with the 10 s / 20 s timeouts.
- Memory: at most 8 decoded tiles (8 MiB) plus OkHttp's disk cache (100 MiB, on disk).

### Verification strategy
- `core`: JUnit tests for `terrariumHeights` (known RGB → height), `tilesFor` (1, 2 and 4 tiles;
  latitude out of range → empty; antimeridian wrap), and `interpolateElevation` against the four
  spec scenarios (one of them spans four tiles) with the spec's tolerances.
- `app`: JVM tests for `ElevationRepository` (fake fetcher/decoder: known, 404 → unknown, one of
  two tiles missing → unknown, LRU hit makes no fetch) and for `MapViewModel.elevation`
  (loading → known, unknown when offline, reload on reconnect, no stale value after a location
  change). A JVM test for `formatAltitude` covers every panel scenario. A test for
  `DemTileFetcher` uses a stub `Interceptor` to check the User-Agent and the 404 → `null`
  mapping.
- On-device check with the CI APK: altitude at Interlaken, Kleine Scheidegg and Mürren within
  ±1 m of the spec values (this also checks bit-exact decoding); `Altitude …` then a value when
  panning to a new area; `Altitude unknown` in flight mode at an unvisited location, and the
  earlier value at a visited one; the attribution link opens the browser.

## Risks / Trade-offs

- [Mapterhorn restricts or ends access] → Tiles already in the disk cache keep working. A
  replacement is designed when and if it happens. D1 keeps the source in one class, so the rest
  of the code does not depend on it.
- [No published usage policy for `tiles.mapterhorn.com`] → Requests are on demand only, cached
  for 7+ days, and identified by User-Agent (see Open Questions).
- [Tiles are rebuilt and values shift slightly] → Tests use pinned fixtures (D11). The cache
  revalidates after 7 days, and a location's value can change by decimetres after a rebuild.
- [`BitmapFactory` decoding not bit-exact on some device] → Checked on a device (D5, task 5.2,
  passed 2026-09-26); errors would be metres to hundreds of metres, so the check detects them.
- [Copernicus fallback (surface model) outside national coverage counts as known] → Accepted
  (D6); it is irrelevant in the Alps.
- [Android clears the cache directory; offline data disappears] → Accepted until change 6 brings
  managed regions (D3).
- [Summits read up to ~7 m low at z12] → Within the spec tolerance; change 4 decides whether it
  needs z13.

## Migration Plan

No data to migrate. The disk cache is new. Rollback: revert the change's commits; the leftover
`cacheDir/dem-tiles` is harmless and cleared by the OS.

## Open Questions

- Mapterhorn's acceptable use of `tiles.mapterhorn.com` by an app: ask the maintainers before a
  public release. The answer does not change this design's behaviour; at most it changes the
  endpoint (D4) or request pacing.
