# Tasks

## 1. core: sweep geometry and hull

- [x] 1.1 Write `SunShadeGeometryTest` first:
  - `MapArea(centre, zoom, widthDp, heightDp)` gives the visible bounds of a north-up Web-Mercator map (512 px tiles): at 46.5935° N, 7.9091° E, zoom 12, 400 × 850 dp, the width is 5.24 km ±1 %;
  - the gnomonic frame maps the centre to (0, 0) and back within 1e-9°;
  - lines are 2 dp apart, point at the sun's azimuth from the centre (±0.001°), and are great circles (three points of a line 5 km off-centre lie in one plane through the earth's centre);
  - the grid covers every corner of the visible area.

  Then implement `MapArea`, the frame and the line layout (design D2) in `core/.../SunShade.kt`. Verify: `./gradlew :core:test --tests "*SunShadeGeometryTest*"` passes.
- [x] 1.2 Write `SunShadeHullTest` first, on synthetic terrain (helper building `HeightTile`s from a height function, as in #4's tests):
  - a 1000 m east–west ridge with the sun at 20° in azimuth 180° → shade up to 2741 m north of the crest, sun beyond (±1 cell);
  - a peak 4000 m above the eye at 100 km → shade at 1.80°, sun at 2.00°;
  - a flat plain → all sun at 30°, all shade at −10°;
  - the hull result equals brute force (max over all earlier samples of the curved-earth tangent) within 1e-9 on random profiles.

  Then implement the curvature-transformed convex-hull pass with the eye query (design D1) and the sampling along lines at z_v and #4's upwind bands with knots (design D3). Verify: `./gradlew :core:test --tests "*SunShadeHullTest*"` passes.

## 2. core: tile plan, upwind cut, bundling, missing data

- [x] 2.1 Write `SunShadePlanTest` first:
  - `tiles(ground)` contains every tile the computation reads (recorded on a small area, four azimuths) and the ground tiles;
  - with the sun at 20° the plan reaches ≤ 12 km upwind (d_max), and at −5° it reaches 150 km;
  - a grid computed with the cut equals one computed over the full 150 km on two synthetic landscapes.

  Then implement `SunShadeSweep.groundTiles()` and `tiles(ground)` with the exact upwind cut (design D4, D7). Verify: `./gradlew :core:test --tests "*SunShadePlanTest*"` passes.
- [x] 2.2 Write `SunShadeBundlingTest` first:
  - the chosen bundle size m satisfies `m · spacing / 2 ≤ d_band_start · tan(0.125°)` for spacings of 13, 26 and 52 m;
  - ridges 10–40 km upwind that do not vary sideways give exactly the unbundled grid;
  - with sideways variation, bundling moves a cell's horizon by at most (largest offset from the bundle's centre line) × (sideways slope) / 25 km. A cell that is sun (shade) bundled is sun (shade) unbundled with the sun δ higher (lower);
  - computing the lines in `chunks` gives the same grid as all at once.

  (A fixed "≥ 99.9 % equal" was dropped during apply: the share depends on how many cells lie within δ of the sun; 0.014 % on the spike's real data, 3.8 % on a synthetic valley.) Then implement far-field bundling (design D5). Verify: `./gradlew :core:test --tests "*SunShadeBundlingTest*"` passes.
- [x] 2.3 Write `SunShadeUnknownTest` first, with the spec's three scenarios (eye 500 m, local horizon 2°, tile 20 km upwind missing):
  - sun at 5° → UNKNOWN;
  - 20° → SUN;
  - 1.5° → SHADE;
  - a missing ground tile → UNKNOWN;
  - no tiles at all → every cell UNKNOWN.

  Then implement the gap bound per line (design D6). Verify: `./gradlew :core:test --tests "*SunShadeUnknownTest*"` passes.
- [ ] 2.4 Write `SunShadeOracleTest` first: on two synthetic landscapes (parallel ridges; a cirque), at 200 random cells and three sun positions, compare the state with `sunshineAt` of a `HorizonTracer` profile at the cell's sample point with the same sun. Expect ≥ 99.5 % agreement; disagreements only within one cell of a shadow edge. Then implement `compute(tiles, lines)`, `ShadeGrid.assemble` and `ShadeGrid.stateAt` (design D7), fixing any mismatch. Verify: `./gradlew :core:test --tests "*SunShadeOracleTest*"` passes.
- [ ] 2.5 Add `SunShade.kt` to the `core` row of `CLAUDE.md`'s "Where things are" table (sun-shade grid by hull sweep, no I/O). Verify: `grep -n "SunShade" CLAUDE.md` shows the entry.

## 3. app: overlay repository

- [ ] 3.1 Write `OverlayRepositoryTest` first, with a fake tile function:
  - all planned tiles are requested concurrently, once each;
  - the lines are computed in `availableProcessors()` chunks and assembled into the same grid as one chunk;
  - a second computation at the same area and another time requests only tiles not in the kept map;
  - an area moved by more than half a screen drops the kept map;
  - cancelling stops between chunks.

  Then implement `OverlayRepository(tile = tileCache::tile)` (design D8) and wire it in `SunshineApp`. Verify: `./gradlew :app:testDebugUnitTest --tests "*OverlayRepositoryTest*"` passes.

## 4. app: view-model state

- [ ] 4.1 Write the `MapViewModel` overlay tests first (test dispatcher, fake repository):
  - off at start → `Off` and no repository call;
  - toggle on at zoom 10.5 → `ZoomedOut`, no call;
  - toggle on at zoom 12 → `Computing(null)`, then `Ready`;
  - a pan → `Computing(kept = previous)` and no call before 300 ms of rest;
  - a time change → `Computing(null)` immediately;
  - slider positions arriving during a computation are conflated, and the last one is computed;
  - a reconnect recomputes only if the grid has unknown cells;
  - the toggle survives recreation from `SavedStateHandle`.

  Then add `onMapSizeChanged`, `onOverlayToggled` and `overlay: StateFlow<OverlayUiState>` (design D8). Verify: `./gradlew :app:testDebugUnitTest --tests "*MapViewModel*"` passes, including the existing tests.

## 5. app: rendering

- [ ] 5.1 Write `RenderOverlayTest` first:
  - a grid of all SHADE → every pixel `#455A64` at alpha 0.45;
  - all SUN → transparent;
  - UNKNOWN → stripes where `(x + y) mod 8 < 2` dp;
  - a grid rotated for azimuth 135° resamples to north-up, with a known cell landing on the right pixel (±1 px);
  - pixels outside the grid are transparent.

  Then implement `renderOverlay` (design D9). Verify: `./gradlew :app:testDebugUnitTest --tests "*RenderOverlayTest*"` passes.
- [ ] 5.2 Add the `overlay: OverlayImage?` parameter to `MapLibreMap`: an `ImageSource` with the bounds' `LatLngQuad` and a `RasterLayer` directly above `opentopomap`; `null` removes it; a new image replaces it. Pass the state from `MapScreen`. Verify: `./gradlew :app:assembleDebug` succeeds. On an emulator or device, with a debug grid of all SHADE, the tint covers exactly the map area and stays on the terrain while panning.

## 6. app: UI and debug checks

- [ ] 6.1 Add the toggle icon button (top end), the legend (`Shade`, `Unknown` swatches) while on, and the notices `Zoom in to see sun and shade` and `Computing sun and shade …` in `MapLabels`' top column. Strings go in `strings.xml`; report the map size in dp from `MapScreen` to `onMapSizeChanged` (design D10). Verify: `./gradlew :app:testDebugUnitTest` and `./gradlew :app:lintDebug` pass. On a device, the toggle, legend and both notices appear as specified, and none covers the crosshair, sun line, panel or attributions.
- [ ] 6.2 Add debug-only logs:
  - the overlay timings (tile loading, sweep, rendering; tiles in memory / disk / network);
  - an agreement check that, in debug builds, evaluates `HorizonTracer` + `sunshineAt` at 200 random cells of a finished grid and logs the percentage of agreement.

  Verify: `./gradlew :app:assembleDebug` succeeds. `adb logcat -s Sunshine` shows both on a device (checked in 7.2).
- [ ] 6.3 Update `docs/roadmap.md` entry #5 (decisions resolved in its design.md, spike reference) and the `app` row of `CLAUDE.md` (overlay repository, rendering). Verify: `grep -n "add-sun-shade-overlay" docs/roadmap.md` shows "resolved in its design.md".

## 7. Integration

- [ ] 7.1 Run `./scripts/verify-local.sh` and `openspec validate --all --strict`. Verify: ktlint, Android lint, all unit tests and the debug APK pass, and validation reports no failures.
- [ ] 7.2 On-device check with the CI APK. Expected:
  - off at launch;
  - at Interlaken, zoom 12, 2025-12-21 12:00 the crosshair's cell is untinted (sun) and at 15:00 tinted (shade);
  - at Lauterbrunnen, zoom 12, 2025-12-21 15:00 the debug agreement log shows ≥ 99.5 %;
  - at 02:00 the whole map is tinted;
  - in flight mode over a never-visited area the overlay is hatched;
  - zoom 10.5 shows `Zoom in to see sun and shade`;
  - dragging the slider ends with the overlay of the final position;
  - the logged timings are within the design's performance budget, or the misses are recorded in design.md.
