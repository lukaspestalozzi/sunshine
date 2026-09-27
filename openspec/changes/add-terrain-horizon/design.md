# Design

## Context

See proposal.md (Why) and the specs `terrain-horizon`, `point-sunshine` and `sun-position`
(delta). Measurements and literature: `investigations/terrain-horizon-algorithms.md` (the
spike). Starting point, as built by #2 and #3:

- `core`:
  - `sunPosition(point, instant)` uses commons-suncalc, whose apparent elevation has no
    refraction at or below 0° geometric elevation.
  - `sunDay` defines the day window.
  - `Elevation.kt` has `TileKey`, `HeightTile(size, heights: FloatArray)`, `tilesFor` and
    `interpolateElevation` (bilinear at pixel centres).
  - `terrariumHeights(argb)` decodes Terrarium pixels.
- `app/elevation`:
  - `MapterhornTiles` fixes zoom 12 and 512 px tiles.
  - `DemTileFetcher` uses a 100 MiB OkHttp disk cache and returns `null` on 404, on failure, and
    on an offline cache miss.
  - `ElevationRepository` keeps an 8-tile `FloatArray` LRU.
  - `decodeArgb` uses `BitmapFactory`.
- `app/map`:
  - `MapViewModel` derives `elevation` with a latest-wins `channelFlow` over
    `camera.center` and `isOnline`, and `sun` from camera and selected time.
  - `SunPanel` has an `Altitude` row; `SunLine` is dashed below the astronomical horizon.
- Verified by the spike (2026-09-26):
  - Mapterhorn does not publish every zoom everywhere; z15 returns 404 at Viganella.
  - 18–53 tiles per location with `near14_cheap` at 0.25°.
  - 2–4 M bilinear samples per profile.

## Goals / Non-Goals

**Goals:**
- A horizon profile per location, valid for every date. Time and date changes only re-evaluate
  the sun against it.
- Exact treatment of missing data: lower bounds and "unknown only if it matters", never a default
  height.
- `core` stays free of Android and I/O: the tracer asks for tiles and the app supplies them.

**Non-Goals:**
- Overlay and heatmap (#5, #7). The tracer is designed for one point.
- Refraction below 0° (user decision: keep the #2 limitation).
- Persisting profiles across app restarts. The tiles are in the disk cache anyway.

## Decisions

### D1. One horizon profile per location (spike)
Rays every 0.25° of azimuth, 1440 rays in all. Sun periods and the sunshine state are then derived
from the profile for any date.
*Alternatives (all measured in the spike):*
- Per-date shadow rays: 1–21 tiles, but a new date needs new data.
- Projecting every cell onto the view (TPPSS): cannot stop early, so it needs every tile in the
  radius.
- Equatorial transform: approximate, with 1.2–2.7 min/day disagreement.

### D2. Distance-dependent zoom `near14_cheap` (spike)
- Zoom 14 up to 1.5 km, 12 up to 6 km, 11 up to 25 km, 10 up to 150 km.
- Sampling step 0.5 px of the zoom in use; the first sample 5 m from the observer.
- The observer's ground height is bilinear at zoom 14, plus 1.7 m (spec).

In the spike this was ≤ 1.7 min from the reference at 10 locations and ≤ 0.5 min at 7. That
leaves margin for the spec's ±5 min (user decision).
*Alternatives:*
- Uniform zoom 12: up to 8.3 min at cliff bases.
- `near14`: finer, but 1.5–2× the tiles for ≤ 0.3 min gain.

### D3. Exact early termination with a regional height bound
A ray stops at distance d once `max over d' ≥ d of atan((H_max − h_eye − drop(d')) / d')` is
below the ray's running maximum.
- `H_max` is 4810 m (Mont Blanc) for locations within 35–72° N and 25° W–35° E, i.e. Europe west
  of the Caucasus. There, no higher terrain lies within 150 km: Elbrus (5642 m, 42.4° E), Kazbek
  and Ararat all lie east of 42° E, more than 500 km away.
- Everywhere else `H_max` is 8849 m (Everest), which is safe but costs more tiles.

The bound never changes the result, only the work.
*Alternative:* a max-mipmap per tile. Mapterhorn's coarser zooms are averages, not maxima, so
they would not be conservative.

### D4. Year clamp (spike, trick 4): dropped (user decision, 2026-09-27)
The spike's clamp stops a ray once the sun's yearly envelope at that azimuth can no longer change
the result. It was dropped during apply, for two measured reasons:
- **Cost:** the envelope from a year of commons-suncalc positions at 5-minute steps (105 k calls)
  takes ~125 ms per location on a desktop JVM. By the spike's sample counts, it saves only
  ~0.8 M samples (~56 ms) of tracing, so with tiles in memory it is a net loss. It saves 9–45 %
  of the tiles on the cold path.
- **Exactness:** the clamp is exact per 0.25° bin, but `angleAt` interpolates between bins, so a
  bin that stopped early can move a crossing within the neighbouring cell (≤ ~1.5 min).

Only the exact `H_max` termination (D3) remains. If task 6.2 shows the cold path over its budget,
a cheap envelope (from latitude and the declination range, with a safety margin) can be proposed
separately.

### D5. `core` API: a stepwise tracer, with no I/O
`core` cannot fetch tiles, and a profile needs different tiles depending on where rays stop. The
tracer therefore works in distance bands and asks for the tiles of each band:

```kotlin
class HorizonTracer(observer: GeoPoint, bound: Double /* H_max */) {
    fun groundTiles(): Set<TileKey>                           // zoom-14 tiles at the observer
    fun start(tiles: Map<TileKey, HeightTile?>)               // sets h_eye; null tile -> unknown
    fun nextTiles(): Set<TileKey>                             // tiles of the next band, active rays only
    fun advance(tiles: Map<TileKey, HeightTile?>)             // null = unavailable -> ray incomplete
    val isDone: Boolean
    fun profile(): HorizonProfile
}
class HorizonProfile(val angles: FloatArray /* 1440 */, val complete: BooleanArray)
enum class Sunshine { SUN, SHADE, UNKNOWN }
fun sunshineAt(profile: HorizonProfile, point: GeoPoint, instant: Instant): Sunshine
sealed interface SunPeriods { data class Known(val periods: List<SunPeriod>); data object Unknown }
fun sunPeriods(profile: HorizonProfile, point: GeoPoint, date: LocalDate, zone: ZoneId): SunPeriods
```

- **Bands:** 0–0.375, 0.375–0.75 and 0.75–1.5 km (z14); 1.5–3 and 3–6 km (z12); 6–12.5 and
  12.5–25 km (z11); 25–50, 50–100 and 100–150 km (z10). Narrow bands let early termination save
  whole tiles.
- **Missing tiles:** a `null` tile ends every ray that needs it. The ray is marked incomplete with
  its running maximum as a lower bound, unless it had already finished (spec "Incomplete
  horizon").
- **Sunshine rule:** `sunshineAt` compares `sunPosition(...).elevation + 0.266°` with the profile
  interpolated at the sun's azimuth. Incomplete bins decide shade only when the sun is at or below
  the bound, otherwise the result is unknown.
- **Periods:** `sunPeriods` evaluates at 10 s steps over the `sunDay` window, about 8,640 suncalc
  positions. It merges runs of SUN, omits runs shorter than 1 min, and is Unknown if any step with
  the upper edge above 0° is UNKNOWN.
- **Reference implementation:** the Python scripts of the spike, which the unit tests use for
  synthetic cases.

### D6. Compact `HeightTile` shared by altitude and horizon (user decision)
`HeightTile` stores heights as a `ShortArray`: `h = (s + 32768) · 0.25 m − 1000 m`, covering
−1000…15,383 m with an error ≤ 0.125 m, 512 KiB per tile.
- `HeightTile.fromMetres(size, FloatArray)` builds one; `height(row, column)` reads one.
- `interpolateElevation` reads through it.
- #3's tolerances (±1 m) and its fixture tests keep passing; the spec is unchanged.
- `ElevationRepository`'s 8-tile float LRU becomes an app-wide `TileCache`: an LRU of 64 tiles,
  about 32 MiB, keyed by `TileKey`. Point elevation and the horizon both read from it.

*Alternative:* separate caches, leaving #3 untouched but duplicating tiles near the observer.

### D7. Multi-zoom tiles with zoom fallback above zoom 12
`MapterhornTiles` gains the zooms 10–14.
- **Zooms 13 and 14:** Mapterhorn publishes these only where the source data is fine enough
  (Viganella has no z15; other regions may lack z13–z14). On a 404 at these zooms, the
  `TileCache` loads the parent (z−1, down to z12) and upsamples its quadrant bilinearly at pixel
  centres, as in the spike's `_from_parent`. It stores that as the child key, so fallback costs
  one parent fetch.
- **Zooms 10–12:** a 404, or any failure, gives `null` (unavailable), exactly as #3 specifies for
  zoom 12 ("Unknown elevation"). Mapterhorn publishes z0–12 for the whole planet, so a 404 there
  means there is no data, e.g. open sea.

#3's behaviour is unchanged.

### D8. `app`: computing on location changes; time and date are cheap
`SunshineRepository(tile = tileCache::tile)` runs the tracer on the caller's dispatcher. It fetches
each band's tiles concurrently and caches the last 4 complete profiles by location, rounded to
about 1 m.

`MapViewModel` gains `sunshine: StateFlow<SunshineUiState>`:
- **Profile:** a latest-wins `channelFlow`, as for `elevation`, over
  `camera.center.distinctUntilChanged()` and `isOnline`. Each lookup starts with `delay(300 ms)`,
  so continuous panning does not start downloads. That acts as a debounce without the
  `@FlowPreview` `debounce` operator.
- **Periods and state:** derived with `combine(profile, selectedTime)` on the compute dispatcher.
  That is cheap: a date change evaluates about 8,640 sun positions.
- **Reconnect:** when `isOnline` goes true and the current profile is incomplete, the profile is
  recomputed.

### D9. UI (user decisions)
- **Panel:** a `Sunshine` row, formatted by `formatSunshine(state, selectedTime)` in
  `SunFormat.kt` (pure and JVM-tested). Times are formatted like `formatEventTime`, with the same
  offset rule.
- **Sun line:** `SunLine(position, sunshine)` is solid for SUN, dashed for SHADE (including below
  the horizon), and dotted for UNKNOWN or not yet computed.

### Performance budget
- **Trigger:** the camera coming to rest (300 ms), a reconnect with an incomplete profile, and
  changes of date or time (periods only).
- **Horizon with tiles in the memory cache:** 2–4 M bilinear samples. Budget ≤ 300 ms on a
  mid-range phone, estimated from the sample count and not yet measured; task 6.2 measures it.
- **With tiles only in the disk cache:** plus the decoding of up to 53 WebP tiles. Budget ≤ 2 s,
  also an estimate to be measured.
- **Cold network:** 3–7 MB download, limited by the connection. The panel shows `…`.
- **Periods for a date:** ≤ 50 ms (8,640 sun positions and profile lookups).
- **Result of task 6.2 (2026-09-27):** the device check passed, but the timings were not measured
  on the phone; responsiveness was judged acceptable by feel. Desktop JVM: ~250 ms per profile in
  a valley, ~0.8 s in the worst case (flat plain, no early termination). The budgets above remain
  unverified on a phone; the debug log (`adb logcat -s Sunshine`) measures them when needed.
- **Sunshine state at a new time:** ≤ 1 ms.
- **Memory:** tile cache ≤ 32 MiB, plus 4 profiles × 1440 × 5 bytes.
- **Threading:** all computation runs on `Dispatchers.Default`. Downloads run on OkHttp's threads
  and are cancellable (#3).

### Verification strategy
- **`core`, synthetic terrain** (height functions turned into `HeightTile`s):
  - the ridge and curvature scenarios, exact to ±0.05°;
  - a flat plain: the horizon equals the curvature dip;
  - early termination gives the same profile as a run without it;
  - missing far tile behind a high ridge → complete; missing tile that could matter → incomplete
    lower bound;
  - zoom-14 ground height;
  - band tile requests exclude finished rays.
- **`core`, periods:**
  - a synthetic profile with a constant horizon of 10°: the periods equal the times at which the
    upper edge crosses 10°, checked against astral;
  - a notch profile producing two periods;
  - the unknown rules;
  - dropping periods shorter than 1 min.
- **`app`:** `TileCache` (LRU, zoom fallback via a fake fetcher), `SunshineRepository` (bands,
  missing tiles), `MapViewModel` (loading, known, reconnect, no stale value, a date change without
  recompute) and `formatSunshine`.
- **Device check (oracle scenarios):** Interlaken and Lauterbrunnen on 21 Dec and 21 Jun, and
  Viganella; plus the measured timings.

## Risks / Trade-offs

- [18–53 tiles per new location increase the load on Mapterhorn's tile server] → The disk cache,
  a 300 ms settle time before computing, and early termination limit it. The open usage-policy
  question from #3 becomes more pressing (see Open Questions).
- [Spot sensitivity at cliff bases: minutes, and weeks of sunless season, over 100 m] → Stated in
  the spec. The crosshair coordinates are shown with 4 decimals (≈ 11 m).
- [The `H_max` region is wrong for some location] → Outside the region, the Everest bound is
  used. A wrong region can only make rays stop too early, which is a correctness risk. The region
  is chosen with more than 500 km of margin to the nearest higher terrain.
- [Refraction limitation on summits: 3–5 min] → User decision; stated in the spec.
- [32 MiB tile cache on low-memory phones] → Accepted. The LRU size is one constant.
- [Performance estimates not measured on a phone] → Task 6.2 measures them and records them in
  the roadmap. If a budget is missed, the band sizes or azimuth count can be tuned without spec
  change: 0.25° is not required by the ±5 min tolerance.

## Migration Plan

No persisted data changes. `HeightTile`'s constructor changes inside the codebase only, and #3's
tests are adapted. Rollback: revert the change's commits.

## Open Questions

- Mapterhorn's acceptable use for about 50 tiles per location. Ask before a public release; this
  can change caching or pacing, not the specs.
