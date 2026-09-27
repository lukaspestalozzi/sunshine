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
- Interaction stays fluid: computation is off the main thread and cancellable. There is never a
  state from another time.

**Non-Goals:**
- Per-cell sun periods or the day product (#7). Persisting grids.
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
- z_min: the lowest ground in the visible area, or 0 m if unknown;
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
class SunShadeSweep(area: MapArea, sun: SunPosition, mapZoom: Double, heightBound: Double = heightBoundAt(area.center)) {
    fun tiles(): Set<TileKey>                                   // every tile any line samples
    val lineCount: Int
    fun compute(tiles: Map<TileKey, HeightTile?>, lines: IntRange = 0 until lineCount): ShadeGridPart
}
data class MapArea(val center: GeoPoint, val zoom: Double, val widthDp: Double, val heightDp: Double)
class ShadeGrid(/* frame, lineCount, cellsPerLine, states: ByteArray (SUN/SHADE/UNKNOWN/OUTSIDE) */) {
    fun stateAt(point: GeoPoint): Sunshine?                     // nearest cell; null outside the area
}
fun ShadeGrid.Companion.assemble(parts: List<ShadeGridPart>): ShadeGrid
```

- `z_min` for D4 comes from the visible area's tiles. `tiles()` loads z_v first, then the upwind
  plan (`tiles()` is two-phase internally; the caller sees one set).
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
  - `Computing(kept: ShadeGrid?)`. `kept` is the previous grid after a pan, and `null` after a time
    or date change (user decision);
  - `Ready(grid, time)`.
- **Triggers:** a latest-wins `channelFlow` over (area, selected time, toggle, online), as for the
  horizon.
  - A camera change waits `SETTLE_MILLIS` (300 ms); time changes start at once.
  - `conflate` drops slider positions that arrive while a computation runs.
  - A reconnect recomputes only if the grid has unknown cells.
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
- **Toggle:** an icon button at the top end of the map, below the offline notice area, off at
  launch. While on, a small legend next to it: a shade swatch `Shade` and a hatch swatch `Unknown`.
- **Notices:** `Zoom in to see sun and shade` below zoom 11, and `Computing sun and shade …` while
  `Computing` with `kept == null`. Both are `Label`s in `MapLabels`' top column.
- Strings live in `strings.xml`.

### Performance budget
- **Triggers:** the camera at rest for 300 ms, a time or date change (slider positions conflated),
  and a reconnect with unknown cells. Only while the toggle is on and zoom ≥ 11.
- **Sweep, tiles in memory, map zoom 12, portrait phone** (~200–430 lines, 2–4 M samples):
  - budget ≤ 500 ms on a mid-range phone with all cores;
  - desktop JVM estimate 60–150 ms on 3 threads, from the spike's 65–88 ns per sample;
  - unmeasured on a phone; task 7.2 measures it.
- **Map zoom 11:** z13 along lines, 26 m cells over ~10 × 21 km, similar sample counts. Same
  budget.
- **Tiles from the disk cache:** plus decoding up to ~70 WebP tiles. Budget ≤ 3 s.
- **Cold network:** 7–10 MB, limited by the connection. The previous overlay (pan) or the
  `Computing …` notice (time) is shown meanwhile.
- **Resample and upload of the bitmap:** ≤ 50 ms.
- **Memory:**
  - the referenced tile map ≤ ~70 tiles × 512 KiB ≈ 35 MiB during a computation, plus the
    64-tile cache (32 MiB);
  - grid and bitmap ≤ 3 MiB.
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
  - `tiles()` covers every sampled pixel and its bilinear neighbours.
- **`core`, oracle:** on two synthetic landscapes (ridges and a cirque), at 200 random cells, the
  state equals `sunshineAt(HorizonTracer profile)` with the same sun, except within one cell of a
  shadow edge. Asserted ≥ 99.5 %.
- **`app`:** `renderOverlay` (colours, hatch, north-up resampling), `OverlayRepository` (tile reuse,
  chunks), and `MapViewModel`:
  - off → no work;
  - zoomed out;
  - settle after a pan and keep the old grid;
  - a time change drops the old grid;
  - conflated slider;
  - reconnect with unknown cells.
- **Device check:**
  - Interlaken 2025-12-21 at 12:00 (centre cell sun) and 15:00 (shade);
  - Lauterbrunnen at 15:00: a debug-only log of agreement with the point tracer at 200 random
    cells, ≥ 99.5 %;
  - offline, a never-visited area → hatched;
  - the timings.

## Risks / Trade-offs

- [45–70 DEM tiles per new screen strain Mapterhorn and mobile data] → The overlay is off by
  default (user decision). The disk cache, the 300 ms settle, the upwind cut and bundling limit the
  load. #4's usage-policy question becomes more pressing (Open Questions).
- [Phone performance unknown; the budget is an estimate] → Task 7.2 measures it. The tuning order
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
