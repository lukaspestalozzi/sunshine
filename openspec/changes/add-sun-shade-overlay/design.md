# Design

## Context

See proposal.md (Why) and the spec `sun-shade-overlay`. Measurements, math and literature:
`investigations/sun-shade-overlay-algorithms.md` (the spike). Starting point, as built by #1–#4:

- `core`:
  - `HorizonTracer` traces one point's horizon in distance bands at zooms 14/12/11/10. It includes
    the parabolic curvature drop `c·d²` (c = (1 − 0.13)/2R), the eye at ground + 1.7 m (ground
    bilinear at z14), exact `H_max` termination (`heightBoundAt`), and `null` tiles as missing
    data.
  - `sunshine(profile, azimuth, upperEdge)` applies the sun/shade/unknown rule; `sunPosition`
    gives azimuth and apparent elevation; `SUN_UPPER_LIMB = 0.266`.
  - `HeightTile` stores 16-bit heights; `TileKey` is an XYZ key for 512 px tiles.
- `app`:
  - `TileCache` holds 64 decoded tiles (LRU, ~32 MiB) with zoom fallback above z12, and is shared
    by altitude and horizon.
  - `DemTileFetcher` has a 100 MiB disk cache.
  - `MapViewModel` holds `camera` (centre, zoom) and `selectedTime`, and computes latest-wins with
    a 300 ms settle time (`channelFlow` + `cancelAndJoin`) on `Dispatchers.Default`.
  - `MapLibreMap` sets the style from a JSON string (OpenTopoMap raster); rotation and tilt are
    disabled (north-up).
  - MapLibre Android 13.6.1 provides `ImageSource` (bitmap + `LatLngQuad`) and `RasterLayer`.
- Verified by the spike (2026-09-27, Lauterbrunnen, 349k cells):
  - the sweep with z14 samples along the lines matches the point tracer in 99.87–100 % of cells;
  - curvature is exact by the height transform;
  - far-field bundling changes ≤ 0.014 % of cells;
  - desktop JVM: 65–88 ns per sample, 117–202 ms per instant on 3 threads at 1 dp cells.

  Phone timings were not measured.

## Goals / Non-Goals

**Goals:**
- One exact algorithm in `core`, pure and without I/O, testable against `HorizonTracer` as the
  oracle.
- Every cell of the visible area at every zoom ≥ 11, with no cap.
- Unknown only where missing data could change the result.
- Interaction stays fluid: computation is off the main thread and cancellable. A state from
  another time is only shown while its replacement is computed, and always with the
  `Computing sun and shade …` notice (revised 2026-09-27).
- Scrubbing through the selected day is instant once the day is computed (D11).

**Non-Goals:**
- Per-cell sun periods and the heatmap display (#7), although D11's day grids contain the data.
- Persisting grids, or keeping a day after the area or the date changes.
- A per-cell sun position (see D2).
- Reusing `HorizonProfile`s. The overlay needs one azimuth per cell, not 1440.

## Decisions

### D1. Convex-hull directional sweep (spike)
For the sun's azimuth A, the grid's cells lie on parallel lines pointing towards the sun. Each line
is processed from far upwind to its downwind end. For each sample:
- pop hull vertices under the segment to the new ground point;
- query the eye's tangent: walk from the top while the slope grows;
- push the ground point.

In the heights g = h − c·s², where s is the position along the line (downwind positive), the
spec's elevation tangent is exactly `(g_j − G_p)/(s_p − s_j) − 2c·s_p`, with G_p = g_p + 1.7 m.
The hull is therefore exact for the curved earth (derivation in the spike, §Math 1).

The result per cell is tan H. The state is decided by `sunshine`'s rule against the upper edge
(elevation + 0.266°).

*Alternatives (spike):*
- per-cell shadow rays: 10–100× the samples;
- per-cell horizon profiles: ~10¹² samples;
- skyline projection per block: accurate for r/D ≤ 0.08, but its per-cell near field costs about
  100× the sweep. It survives as D5;
- GPU shadow map or ray march: GL plumbing in `app`, and not testable in `core`.

### D2. Geometry: gnomonic frame, sun of the map centre, a rotated grid
- **Frame:** a gnomonic projection centred on the map centre, so straight lines are great circles
  as in `HorizonTracer`. The spike measured the direction error at the cells as ≤ 0.03°.
- **Area:** the visible area follows from centre, zoom and map size in dp (Web Mercator, north-up,
  512 px tiles).
- **Grid:** lines 2 dp apart (user decision: 2 dp cells), converted to metres at the map centre.
  Sample points every 2 dp along each line. It covers the bounding rectangle of the visible area in
  the sun frame (w across the lines, s along them).
- **Sun:** the azimuth and apparent elevation of the map centre (`sunPosition`) apply to all
  cells. At map zoom 11 (≈ 10 × 21 km) this is equivalent to a time offset of ≤ 1 min at the edges,
  and it is exact at the crosshair.

*Alternative:* one azimuth per strip. It costs one sweep per strip, and the error it removes is
below the resolution.

### D3. Data resolution along the lines (user decision)
- Samples every 0.5 px of zoom z_v = min(14, ⌊map zoom⌋ + 2) inside the visible area and up to
  1.5 km upwind.
- Then z12 (or z_v if coarser) to 6 km, z11 to 25 km and z10 to 150 km, all measured from the
  line's first sample in the visible area. These are #4's bands, relative to the area's edge.
- Positions come from exact knots every 32 samples, linear in between, as in `HorizonTracer.Knots`.
- Where a zoom is not published, `TileCache`'s fallback applies (#4 D7).

At map zoom ≥ 12 this is the point tracer's near-field data. That is where the ≥ 99.5 % agreement
of the spec comes from.

*Alternatives (asked):*
- DEM pixel = cell: 3–4× cheaper, but 0.5–2 % of cells contradict the panel at cliffs;
- always z14: hundreds of tiles at map zoom 11.

### D4. Exact upwind cut
Terrain farther than d_max cannot shade any cell, where d_max solves
`(H_max − z_min − c·d²)/d = tan(e)`:
- e: the sun's upper-edge elevation;
- z_min: the lowest height in the area's available ground tiles (a conservative lower bound of
  every eye), or −1000 m (the lowest height a `HeightTile` holds) if none is available;
- H_max: `heightBoundAt(centre)`.

So lines start at min(150 km, d_max) upwind: 12 km at 20°, 47 km at 5°. When e ≤ 0 or d_max
exceeds 150 km, 150 km applies. The cut never changes a result.

### D5. Far-field bundling (skyline projection inside the sweep)
The upwind z11 and z10 bands are sampled once per bundle of m neighbouring lines, on the bundle's
centre line. Each member line starts from a copy of the bundle's hull. The largest power of two is
chosen for m such that `m · spacing / 2 ≤ d_band_start · tan(0.125°)`. This is the spec's lateral
bound; for the z10 band at 25 km it allows 54 m. The spike measured 2.5–3× fewer samples at ≤ 0.05°
p99. Bundle hulls are small (tens of vertices), so copying is cheap.

### D6. Missing data per line
- A `null` tile makes its samples missing. Missing samples are not pushed.
- The line remembers the downwind-most missing position s_m. A cell with found tan H is complete
  iff `tan H ≥ (H_max − e_p − c·(s_p − s_m)²)/(s_p − s_m)`. The bound falls with distance, so the
  nearest gap decides. Otherwise tan H is a lower bound.
- A cell whose own ground sample is missing is unknown.
- The state follows `sunshine`'s rule: shade at or below the lower bound, unknown above it.

### D7. `core` API: plan, then compute; no I/O
The upwind cut (D4) is known up front, so a grid needs no band-by-band stepping, unlike
`HorizonTracer`:

```kotlin
class SunShadeSweep(area: MapArea, sun: SunPosition, heightBound: Double = heightBoundAt(area.center)) {
    fun groundTiles(): Set<TileKey>                             // z_v tiles of the samples inside the area
    fun tiles(ground: Map<TileKey, HeightTile?>): Set<TileKey>  // z_min from ground -> cut -> every tile read
    val lineCount: Int
    fun compute(tiles: Map<TileKey, HeightTile?>, lines: IntRange = 0 until lineCount): ShadeGridPart
    fun assemble(parts: List<ShadeGridPart>): ShadeGrid
}
data class MapArea(val center: GeoPoint, val zoom: Double, val widthDp: Double, val heightDp: Double)
class ShadeGrid(/* frame, lineCount, cellsPerLine, states: ByteArray (SUN/SHADE/UNKNOWN/OUTSIDE) */) {
    fun stateAt(point: GeoPoint): Sunshine?                     // nearest cell; null outside the area
}
```

- `z_min` for D4 comes from the area's own tiles, which `core` cannot load. So the plan has two
  calls, like `HorizonTracer`'s `start`: the app loads `groundTiles()`, then `tiles(ground)`.
  (Changed during apply: the first draft promised one `tiles()` call, which needs I/O.)
- Lines are independent, so `compute` takes a line range. The app splits the lines over cores.

*Alternative:* the band stepping of `HorizonTracer`. That is not needed without per-ray early
termination, and it would serialise the loading.

### D8. `app`: `OverlayRepository` and view-model state
- **`OverlayRepository(tile = tileCache::tile)`:**
  - loads the plan's tiles concurrently and keeps them referenced during the computation;
  - runs `compute` in `availableProcessors()` chunks on the caller's dispatcher;
  - keeps the last computation's tile map, so a time change at the same area reloads only the
    new upwind tiles.
- **`MapViewModel`:** new inputs `onMapSizeChanged(widthDp, heightDp)` and
  `onOverlayToggled()`. The toggle is kept in `SavedStateHandle`, like the camera; it defaults to
  off (user decision). New `overlay: StateFlow<OverlayUiState>`:
  - `Off`;
  - `ZoomedOut`;
  - `Computing(kept: Ready?)`. `kept` is the previous overlay, after a pan and also after a time
    or date change (user decision, revised 2026-09-27). The earlier decision was `null` after a
    time or date change, i.e. clearing the map at once. It was replaced because the map should not
    blink on every slider move. The notice `Computing sun and shade …` shows whenever `kept` is
    `null` or belongs to another time than the selected one;
  - `Ready(grid, time, image)`. The image is rendered by `renderOverlay` (D9) on the compute
    dispatcher, not in composition (changed during apply, task 5.2).
- **Triggers:** a latest-wins `channelFlow` over (area, selected time, toggle, online), as for the
  horizon.
  - A camera change waits `SETTLE_MILLIS` (300 ms); time changes start at once.
  - `conflate` drops slider positions that arrive while a computation runs.
  - A reconnect recomputes only if the grid has unknown cells.
  - A time change within the selected day is answered from the day (D11) when that time is
    computed, without calling the repository.
- **Panel consistency:** the grid and the panel's `SunshineUiState` stay independent. With D3 they
  agree at the crosshair except within one cell at cliff feet (spec tolerance).

### D9. Rendering: one north-up bitmap as a MapLibre `ImageSource`
- `renderOverlay(grid, bounds, pxPerDp = 1)` is a pure function, JVM-tested, returning ARGB
  `IntArray`:
  - it resamples the rotated grid to a north-up bitmap of the visible bounds, nearest cell per
    pixel;
  - shade `#455A64` at alpha 0.45; sun transparent; unknown with grey stripes
    (`(x + y) mod 8 < 2` dp at `#9E9E9E`, alpha 0.6) over transparency (user decision: shade
    tinted, sun clear).
- `MapLibreMap` gets `overlay: OverlayImage?` (bitmap + bounds):
  - it adds an `ImageSource` with the bounds' `LatLngQuad` and a `RasterLayer` directly above the
    `opentopomap` layer;
  - `null` removes the image.

  Compose-drawn elements (crosshair, sun line, panel, labels) stay above the map view.
- With a georeferenced north-up quad, the kept overlay stays on its terrain while panning.

*Alternative:* the rotated sun-frame raster as a rotated quad, which needs no resampling. But its
hatching would rotate with the sun, and MapLibre interpolates quads in Mercator space. Resampling
~350k pixels is a few ms.

### D10. UI (user decisions)
- **Toggle:** a Material 3 `FilterChip` labelled `Sun & shade` at the top end of the map, off at
  launch (the user decisions are the toggle, its default and its place). While on, a small legend
  below it: a shade swatch `Shade` and a hatch swatch `Unknown`.
  - *Changed during apply (task 6.1):* the draft said an icon button, but the project has no icon
    library. A labelled chip needs no new dependency and says what it switches.
  - *Alternatives:* adding `material-icons-extended` (a large dependency for one icon), or a
    hand-drawn icon, which would need a content description and a legend anyway.
- **Notices:** `Zoom in to see sun and shade` below zoom 11, and `Computing sun and shade …` while
  `Computing` with `kept` null or of another time than the selected one (D8). Both are `Label`s in `MapLabels`' top column.
- Strings live in `strings.xml`.

### D11. The whole day: selected time first, then every slider step in the background (user decisions, 2026-09-27)
- **Steps:** every slider position of the selected day: 5-minute steps from the day's start over
  its actual length (23 or 25 h on DST days, as `sliderTime`). An off-grid selected time (`Now`,
  launch) is computed too, as its own entry.
- **Order:**
  1. the selected time, on all cores, exactly as today (D8);
  2. then the other steps, nearest to the selected time first (alternating later and earlier).
  A selected step that is not yet computed jumps the queue and is computed next, on all cores.
- **Parallelism:** background steps run their chunks on
  `Dispatchers.Default.limitedParallelism(max(1, cores / 2))` (user decision: half the cores).
- **Lifetime:** one `DayOverlay` per (area, date):
  - a map from time to `ShadeGrid`, filled as steps finish;
  - its job is cancelled and the day discarded after a camera rest, a date change, a reconnect
    while some cell is unknown, or when the overlay is switched off;
  - a time change within the day keeps it;
  - leaving the screen keeps it, and the day keeps computing in the background (user decision,
    2026-09-28, after the device check found days recomputed on return). The overlay flow is
    shared eagerly for the view model's lifetime instead of stopping 5 s after the screen stops
    collecting. *Alternative:* pause while the app is not visible and resume on return, which
    needs the day kept outside the flow: more code for a cost bounded by one day per area and
    date.
- **Rendering:** only the selected time's grid is rendered (`renderOverlay`, D9). The day keeps
  grids, never bitmaps: ~1.4 MB per bitmap × 190 steps would be too much.
- **Tiles:** the ground tiles stay in the repository's kept map for the whole day. Upwind tiles
  come through the kept map and the 64-tile LRU. Consecutive steps differ by ~1–2° of azimuth, so
  their upwind tiles mostly overlap.
- *Alternatives (asked):*
  - 15-minute steps: 3× cheaper, but slider positions in between would still wait;
  - 1° azimuth steps with per-cell interpolation: feeds #7, but is not exact at slider times
    (spike E3: p90 7–16 min/day) and needs floats per cell;
  - all cores or one core for the background.

### D12. Night steps from the ground tiles alone
With the sun's upper edge below −3.5° at the map centre, `SunShadeSweep.night(ground)` gives:
- shade in every cell whose ground sample is available;
- unknown where it is not;
- no upwind tiles and no sweep.

Why this is exact: the lowest horizon a cell can have occurs when all terrain within 150 km is far
below its eye. For an eye at most 4812 m high over ground at least −1000 m, the elevation angle
`−(Δ/d + c·d)` rises with d up to √(Δ/c) ≈ 290 km. So within 150 km its maximum is at 150 km:
−2.81°. Every real horizon is higher, so the sun is blocked everywhere.

The single overlay (D8) uses the same path below −3.5°. That also removes today's most expensive
case: the sun below 0° made the lines reach the full 150 km. There is no cheaper exact rule
between −3.5° and 0°, so those steps are swept.

### D13. Packed cell states
`ShadeGrid` stores 2 bits per cell (4 cells per byte) instead of a byte: SUN, SHADE, UNKNOWN. A
day of up to ~190 steps × ~90k cells then takes ≤ ~4.3 MB instead of ~17 MB. The API is unchanged
(`stateAt`, `cellState`, `sampleCells`, `hasUnknown`).

### Performance budget
- **Triggers:** the camera at rest for 300 ms, a time or date change (slider positions conflated),
  and a reconnect with unknown cells. Only while the toggle is on and zoom ≥ 11.
- **Sweep, tiles in memory, map zoom 12, portrait phone** (~200–430 lines, 2–4 M samples):
  - budget ≤ 500 ms on a mid-range phone with all cores;
  - desktop JVM estimate 60–150 ms on 3 threads, from the spike's 65–88 ns per sample;
  - unmeasured on a phone; task 12.2 measures it.
- **Map zoom 11:** z13 along lines, 26 m cells over ~10 × 21 km, similar sample counts. Same
  budget.
- **Tiles from the disk cache:** plus decoding up to ~70 WebP tiles. Budget ≤ 3 s.
- **Cold network:** 7–10 MB, limited by the connection. The previous overlay (pan) or the
  `Computing …` notice (time) is shown meanwhile.
- **Resample and upload of the bitmap:** ≤ 50 ms.
- **The day (D11):**
  - a time already computed shows within 100 ms (render ≤ 50 ms);
  - a night step (D12) ≤ 20 ms;
  - all ~100 (21 Dec) to ~190 (21 Jun) steps take about 20–110 s of CPU on half the cores. That
    is an estimate from the desktop numbers, unmeasured on a phone; task 12.2 measures it.
- **Memory:**
  - the referenced tile map ≤ ~70 tiles × 512 KiB ≈ 35 MiB during a computation, plus the
    64-tile cache (32 MiB);
  - grid and bitmap ≤ 3 MiB;
  - the day's packed grids ≤ ~5 MiB (D13).
- **Threading:** loading on OkHttp threads; sweep and rendering on `Dispatchers.Default`.
  Everything is cancellable between line chunks.
- Debug builds log the timings (loading, sweep, rendering) with `debugLog`, as #4 does.

If the phone misses the sweep budget, tune constants, in this order; none changes the spec:
1. the chunking;
2. the knot spacing;
3. z_v one level coarser at map zoom 11.

### Verification strategy
- **`core`, synthetic terrain** (height functions turned into `HeightTile`s, as in #4's tests):
  - the ridge shadow (2741 m ±1 cell) and the curvature scenario (1.90° ±0.05°);
  - a flat plain at night: all shade; at noon: all sun;
  - the upwind cut gives the same grid as the full 150 km;
  - bundling stays within the lateral bound, and on synthetic ridges matches the unbundled grid;
  - the three missing-tile scenarios and a missing ground tile → unknown;
  - the night grid: shade where the ground is known, unknown where a ground tile is missing, no
    upwind tiles requested;
  - packed states: the same grids as before, ≤ 23 KB for 90k cells;
  - `tiles()` covers every sampled pixel and its bilinear neighbours.
- **`core`, oracle:** on two synthetic landscapes (ridges and a cirque), at 200 random cells, the
  state equals `sunshineAt(HorizonTracer profile)` with the same sun, except within one cell of a
  shadow edge. Asserted ≥ 99.5 %.
- **`app`:** `renderOverlay` (colours, hatch, north-up resampling), `OverlayRepository` (tile reuse,
  chunks), and `MapViewModel`:
  - off → no work;
  - zoomed out;
  - settle after a pan and keep the old grid;
  - a time change keeps the old grid with the notice, and a computed time shows without a
    repository call;
  - the day's order (nearest first), its parallelism (half the cores) and its restart rules;
  - conflated slider;
  - reconnect with unknown cells.
- **Device check:**
  - Interlaken 2025-12-21 at 12:00 (centre cell sun) and 15:00 (shade);
  - Lauterbrunnen at 15:00: a debug-only log of agreement with the point tracer at 200 random
    cells, ≥ 99.5 %;
  - offline, a never-visited area → hatched;
  - after switching on, the day finishes in the background, and scrubbing through it is instant;
  - the timings, including the day's total.

## Risks / Trade-offs

- [45–70 DEM tiles per new screen strain Mapterhorn and mobile data] → The overlay is off by
  default (user decision). The disk cache, the 300 ms settle, the upwind cut and bundling limit the
  load. #4's usage-policy question becomes more pressing (Open Questions).
- [Phone performance unknown; the budget is an estimate] → Task 12.2 measures it. The tuning order
  is under "Performance budget". Constants only; no spec change.
- [Memory peak of ~35 MiB of referenced tiles plus the 32 MiB cache on low-memory phones] →
  Tiles are released right after a computation, except the reuse map. That map is dropped when
  the area changes by more than half a screen.
- [North-up bitmap and `ImageSource` quad interpolation in Mercator: over 21 km the scale varies
  by ~0.2 %] → Resampling is done in Mercator pixel space of the same quad, so cells land within
  < 1 dp.
- [One sun position for the whole screen] → ≤ 1 min time-equivalent at map zoom 11, exact at the
  crosshair (spec).
- [At cliff feet the crosshair's cell can disagree with the panel] → Stated in the spec as
  within-cell variability. The panel stays authoritative for the crosshair point.
- [Offline, the overlay can be more decided than the panel] → The upwind cut (D4) never samples
  terrain that cannot rise above the sun, so a missing tile beyond the cut does not make a cell
  unknown. `HorizonTracer` has no sun-based cut, so for the crosshair the panel can say `unknown`
  where the overlay says sun. The overlay is right in that case (found during apply, task 2.3);
  aligning the panel is left to a later change.
- [Battery and heat from the day's background CPU (about 20–110 s per area and day, estimated)] →
  It uses half the cores (user decision), runs only while the overlay is on, and is cancelled by
  every new area or date. Night steps cost nothing (D12). Task 12.2 measures it. If it is too
  heavy, limiting the day to a window around the selected time would be a spec change, decided
  with the user. The nearest-first order already makes the most useful times ready first.
- [More tiles per area: the day's upwind terrain lies in every sun direction] → Up to ~50 more
  z10/z11 tiles (~5–8 MB) on a cold cache, cached on disk afterwards. The overlay is off by
  default.
- [The spike covered one area (Lauterbrunnen)] → The oracle tests use synthetic landscapes, and
  the device check covers Interlaken.

## Migration Plan

No persisted data changes. The toggle defaults to off, so existing behaviour is unchanged until the
user opts in. Rollback: revert the change's commits.

## Open Questions

- Mapterhorn's acceptable use for about 50–70 tiles per screen, carried over from #4. Ask before a
  public release; this can change caching or pacing, not the specs.
- Final tint and hatch colours after the device check. They are constants in `renderOverlay`, and
  the spec only requires "readable through the tint" and "grey hatching".
