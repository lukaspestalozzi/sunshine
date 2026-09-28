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
- [x] 2.4 Write `SunShadeOracleTest` first: on two synthetic landscapes (parallel ridges; a cirque), at 200 random cells and three sun positions, compare the state with `sunshineAt` of a `HorizonTracer` profile at the cell's sample point with the same sun. Expect ≥ 99.5 % agreement; disagreements only within one cell of a shadow edge. Then implement `compute(tiles, lines)`, `ShadeGrid.assemble` and `ShadeGrid.stateAt` (design D7), fixing any mismatch. Verify: `./gradlew :core:test --tests "*SunShadeOracleTest*"` passes.
- [x] 2.5 Add `SunShade.kt` to the `core` row of `CLAUDE.md`'s "Where things are" table (sun-shade grid by hull sweep, no I/O). Verify: `grep -n "SunShade" CLAUDE.md` shows the entry.

## 3. app: overlay repository

- [x] 3.1 Write `OverlayRepositoryTest` first, with a fake tile function:
  - all planned tiles are requested concurrently, once each;
  - the lines are computed in `availableProcessors()` chunks and assembled into the same grid as one chunk;
  - a second computation at the same area and another time requests only tiles not in the kept map;
  - an area moved by more than half a screen drops the kept map;
  - a cancelled computation keeps nothing (chunks check for cancellation before they start).

  Then implement `OverlayRepository(tile = tileCache::tile)` (design D8) and wire it in `SunshineApp`. Verify: `./gradlew :app:testDebugUnitTest --tests "*OverlayRepositoryTest*"` passes.

## 4. app: view-model state

- [x] 4.1 Write the `MapViewModel` overlay tests first (test dispatcher, fake repository):
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

- [x] 5.1 Write `RenderOverlayTest` first:
  - a grid of all SHADE → every pixel `#455A64` at alpha 0.45;
  - all SUN → transparent;
  - UNKNOWN → stripes where `(x + y) mod 8 < 2` dp;
  - a grid for a sun at azimuth 135° resamples to north-up: the shadow edge 1061 m north of a cliff lands on its pixel row (±1 cell).

  ("Pixels outside the grid" was dropped during apply: the image covers exactly the grid's own area, which the grid covers with a cell of margin.)

  Then implement `renderOverlay` (design D9). Verify: `./gradlew :app:testDebugUnitTest --tests "*RenderOverlayTest*"` passes.
- [ ] 5.2 Add the `overlay: OverlayImage?` parameter to `MapLibreMap`: an `ImageSource` with the bounds' `LatLngQuad` and a `RasterLayer` directly above `opentopomap`; `null` removes it; a new image replaces it. Pass the state from `MapScreen`. Verify: `./gradlew :app:assembleDebug` succeeds. On an emulator or device, with a debug grid of all SHADE, the tint covers exactly the map area and stays on the terrain while panning.

  Status (apply): code done, `assembleDebug` and unit tests pass; the device part is open (no emulator in the cloud session) and is checked in 12.2.

## 6. app: UI and debug checks

- [ ] 6.1 Add the toggle icon button (top end), the legend (`Shade`, `Unknown` swatches) while on, and the notices `Zoom in to see sun and shade` and `Computing sun and shade …` in `MapLabels`' top column. Strings go in `strings.xml`; report the map size in dp from `MapScreen` to `onMapSizeChanged` (design D10). Verify: `./gradlew :app:testDebugUnitTest` and `./gradlew :app:lintDebug` pass. On a device, the toggle, legend and both notices appear as specified, and none covers the crosshair, sun line, panel or attributions.

  Status (apply): code done (toggle as a `FilterChip` "Sun & shade", no icon library), `overlayNotice` unit-tested, unit tests and lint pass; the device part is open and is checked in 12.2.
- [ ] 6.2 Add debug-only logs:
  - the overlay timings (tile loading, sweep, rendering; tiles in memory / disk / network);
  - an agreement check that, in debug builds, evaluates `HorizonTracer` + `sunshineAt` at 200 random cells of a finished grid and logs the percentage of agreement.

  Verify: `./gradlew :app:assembleDebug` succeeds. `adb logcat -s Sunshine` shows both on a device (checked in 12.2).

  Status (apply): code done — `OverlayRepository` logs tiles (kept / from disk / from network, counted by `DemTileFetcher.loads()` / rest in memory or unavailable) and sweep time, `MapViewModel` logs grid and image time, and in debug builds the agreement over 200 cells once an overlay has stayed 3 s (unit-tested); `assembleDebug` passes. The logcat part is open until 12.2.
- [x] 6.3 Update `docs/roadmap.md` entry #5 (decisions resolved in its design.md, spike reference) and the `app` row of `CLAUDE.md` (overlay repository, rendering). Verify: `grep -n "add-sun-shade-overlay" docs/roadmap.md` shows "resolved in its design.md".

## 7. Integration

- [x] 7.1 Run `./scripts/verify-local.sh` and `openspec validate --all --strict`. Verify: ktlint, Android lint, all unit tests and the debug APK pass, and validation reports no failures.
7.2 (the on-device check) moved to 12.2, after the revision of 2026-09-27.

## 8. core: night grid and packed states (revision 2026-09-27)

- [x] 8.1 Write `SunShadeNightTest` first:
  - with the sun's upper edge at −3.6°, `night(ground)` gives SHADE in every cell whose ground tile is available and UNKNOWN where it is missing;
  - it needs only `groundTiles()` (no upwind tile is read, checked with a recording tile map);
  - on two synthetic landscapes, it equals the full sweep at −3.6°, the sweep being made to reach 150 km.

  Then implement `SunShadeSweep.night(ground)` (design D12). Verify: `./gradlew :core:test --tests "*SunShadeNightTest*"` passes.
- [x] 8.2 Write the packing test first: 90k cells take ≤ 23 KB of states, and a grid round-trips every state. Then store `ShadeGrid` states at 2 bits per cell (design D13). Verify: `./gradlew :core:test` passes, all existing sun-shade tests unchanged.

## 9. app: keep the overlay on time changes (revision 2026-09-27)

- [x] 9.1 Change the `MapViewModel` tests first:
  - a time or date change → `Computing(kept = previous)`;
  - the notice shows while `kept` belongs to another time (`overlayNotice` gets the selected time);
  - `OverlayRepository.grid` uses the night grid (D12) below −3.5°.

  Then implement it (design D8, D12). Verify: `./gradlew :app:testDebugUnitTest --tests "*MapViewModel*" --tests "*OverlayNotices*" --tests "*OverlayRepository*"` passes.

## 10. app: the whole day (revision 2026-09-27)

- [x] 10.1 Write `DayOverlayTest` first (test dispatcher, fake grid function):
  - the steps of 2025-12-21 and of the DST days 2025-03-30 (276) and 2025-10-26 (300) come from `sliderTime`;
  - the order is selected time, then nearest first, alternating later/earlier;
  - background steps run on a dispatcher limited to half the cores (at least 1);
  - night steps use the night grid;
  - a selected step not yet computed jumps the queue;
  - cancelling stops between steps.

  Then implement `DayOverlay` (design D11). Verify: `./gradlew :app:testDebugUnitTest --tests "*DayOverlayTest*"` passes.
- [x] 10.2 Write the `MapViewModel` day tests first:
  - after the selected time is ready, the day continues in the background;
  - a time change to a computed step gives `Ready` without calling the repository;
  - a camera rest, a date change or a reconnect with unknown cells starts the day over;
  - switching off stops it.

  Then wire `DayOverlay` into `MapViewModel` (design D8, D11). Verify: `./gradlew :app:testDebugUnitTest --tests "*MapViewModel*"` passes, including the existing tests.
- [x] 10.3 Add a debug log of the day: steps done out of total, night steps, and total time when finished. Verify: a `MapViewModel` test sees the log line, and `./gradlew :app:assembleDebug` succeeds.
- [x] 10.4 Keep the day when the app leaves the screen (device check, 2026-09-28: days were computed again on return, because the overlay flow stopped 5 s after the screen stopped collecting). Write the `MapViewModel` test first: a computed day, the collector gone for 10 s, then back → no grid computed again. Then share the overlay flow eagerly (design D11, user decision: keep computing). Verify: `./gradlew :app:testDebugUnitTest --tests "*MapViewModel*"` passes.

## 11. Docs (revision 2026-09-27)

- [x] 11.1 Update `docs/roadmap.md` entry #5 (whole day in the background) and the `app` row of `CLAUDE.md` (`DayOverlay`). Verify: `grep -n "DayOverlay" CLAUDE.md` and `grep -n "whole day" docs/roadmap.md` show the entries.

## 12. Integration (after the revision)

- [x] 12.1 Run `./scripts/verify-local.sh` and `openspec validate --all --strict`. Verify: ktlint, Android lint, all unit tests and the debug APK pass, and validation reports no failures.
- [ ] 12.2 On-device check with the CI APK. Expected:
  - off at launch;
  - at Interlaken, zoom 12, 2025-12-21 12:00 the crosshair's cell is untinted (sun) and at 15:00 tinted (shade);
  - at Lauterbrunnen, zoom 12, 2025-12-21 15:00 the debug agreement log shows ≥ 99.5 %;
  - at 02:00 the whole map is tinted;
  - in flight mode over a never-visited area the overlay is hatched;
  - zoom 10.5 shows `Zoom in to see sun and shade`;
  - dragging the slider ends with the overlay of the final position;
  - moving the slider to a time not yet computed keeps the previous overlay with `Computing sun and shade …`;
  - after the day's log reports it finished, scrubbing shows each overlay at once, without the notice;
  - the logged timings (selected time, day total, night steps) are within the design's performance budget, or the misses are recorded in design.md;
  - while the day computes, a bar below the `Sun & shade` chip fills, and it disappears when the day is finished;
  - after picking another date and then the first one again, or switching the overlay off and on, the computed day shows at once, without the notice and without a new `Overlay day` log line;
  - the device parts of 5.2, 6.1 and 6.2 (placement and panning, controls, logcat).

## 13. Progress of the day (revision 2026-09-28)

- [x] 13.1 Write the `DayOverlayTest` cases first: `computed` starts at 0, rises by one per finished slider step, and ignores an off-grid selected time. Then add `DayOverlay.computed` (design D11). Verify: `./gradlew :app:testDebugUnitTest --tests "*DayOverlayTest*"` passes.
- [x] 13.2 Write the `MapViewModel` tests first: `dayProgress` is `null` before the day starts, computed / total while it runs, and `null` when it has finished, when the overlay is switched off, and between a restart's cancel and its new day. Then add `MapViewModel.dayProgress` (design D11). Verify: `./gradlew :app:testDebugUnitTest --tests "*MapViewModel*"` passes.
- [x] 13.3 Show `dayProgress` as a determinate bar below the chip in `OverlayControl`, wired in `MapScreen` (design D10). Verify: `./gradlew :app:assembleDebug :app:lintDebug` succeeds; the look is checked in 12.2.

  Device check, 2026-09-28: the bar was wider than the chip, because `IntrinsicSize.Max` took the bar's default width of 240 dp. A small layout now measures the chip first and gives the bar exactly its width.
- [x] 13.4 Run `./scripts/verify-local.sh` and `openspec validate --all --strict`. Verify: everything passes.

## 14. Cache of days (revision 2026-09-28)

- [x] 14.1 Write the `DayOverlayTest` cases first:
  - `bytes` is the sum of the grids' `stateBytes`, and `hasUnknown` is true once a grid has unknown cells;
  - after `computeRest` is cancelled and started again, only the missing steps are computed, and a selected time that is not yet known comes first.

  Then make `ShadeGrid.stateBytes` public, and add `DayOverlay.bytes` and `hasUnknown`. `computeRest` waits until the selected time is known (design D14). Verify: `./gradlew :core:test :app:testDebugUnitTest --tests "*DayOverlayTest*"` passes.
- [x] 14.2 Write `DayCacheTest` first:
  - get and put by (area, date);
  - access order;
  - `trim` drops the least recently used days while the bytes exceed the budget, never the given day.

  Then implement `DayCache` (design D14). Verify: `./gradlew :app:testDebugUnitTest --tests "*DayCacheTest*"` passes.
- [x] 14.3 Write the `MapViewModel` tests first:
  - picking another date and then the first one again, or switching the overlay off and on, gives `Ready` without computing again;
  - only the missing steps of a partly computed day are computed;
  - a cached day with unknown cells is computed anew when picked online;
  - a reconnect with unknown cells still starts the day over;
  - a complete cached day shows no progress;
  - with a small budget, the least recently used day is dropped.

  Then use `DayCache` in `MapViewModel`, with a budget of a quarter of `ActivityManager.memoryClass` in `MapScreen` (design D14). Verify: `./gradlew :app:testDebugUnitTest --tests "*MapViewModel*"` passes.
- [ ] 14.4 Run `./scripts/verify-local.sh` and `openspec validate --all --strict`. Verify: everything passes.
