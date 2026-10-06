# Tasks

Device checks (6.1–6.3) are done by the user at the end, on the debug APK of the last commit.

## 1. Combined grids (core)

- [x] 1.1 Set `docs/roadmap.md` row #11 to "in progress" with the decisions of design D1. Verify: `openspec validate --all --strict` passes and the row reads "in progress".
- [x] 1.2 Write `CombinedGridTest` first (design D3): on synthetic terrain, a base grid of an area and part grids of the rectangles a half-screen pan uncovers; `stateAt` equals the base's inside the base's bounds and the parts' elsewhere, `null` outside the new area's grids; `statesAt` over a raster reaching past the area equals `stateAt` at every point; `sampleCells` draws from base and parts in proportion to their share (±10 % over 1000 samples); `hasUnknown` is any part's; `stateBytes` the sum. See it fail to compile, then add the `StepGrid` interface (implemented by `ShadeGrid`) and `CombinedGrid` in `SunShade.kt`. Verify: `./gradlew :core:test` passes.
- [x] 1.3 Extend `SunShadeOracleTest` first: a combined grid of a half-screen pan, each cell against the point tracer with the sun of its computed area (sun-shade-overlay "Sunshine of a cell", "Sun position of a reused part"), ≥ 99.5 % agreement. Verify: `./gradlew :core:test --tests "*SunShadeOracle*"` passes.

## 2. Geometry and choice of the earlier day (app)

- [x] 2.1 Write `UncoveredTest` first (design D2): pans by half a screen in the four directions give one rectangle of half the area; a diagonal pan gives two; zooming out by 0.5 level around the same centre gives four strips around the old area; no overlap gives the whole area and share 0; a sliver narrower than one cell is widened to one cell; the covered share of a half-screen pan is 0.5 (±0.01). Then implement `uncovered(new, old, cellDp)` in `map/`. Verify: `./gradlew :app:testDebugUnitTest --tests "*Uncovered*"` passes.
- [x] 2.2 Extend `DayCacheTest` first with `reusable` (design D1): same date, cell size and step; zoom rule (12 → 12.5 refused, 12.5 → 12 accepted, 13.1 → 12 refused); share ≥ ¼ (0.8-screen pan refused); a day with unknown cells refused online and accepted offline; the largest share wins; 50 days searched in ≤ 1 ms. Then implement it. Verify: `./gradlew :app:testDebugUnitTest --tests "*DayCache*"` passes.

## 3. Days that reuse an earlier day (app)

- [ ] 3.1 Extend `DayOverlayTest` first (design D1, D3): with a complete base, every daytime step requests only the uncovered parts' areas and stores a combined grid; night steps request the whole area; with a base lacking 188 of 288 steps, those steps request the whole area; a step whose base grid is combined twice requests the whole area; `reusedShare` is the covered share; `bytes` include the base's grids. Then add `base` to `DayOverlay` and let the grid functions return `StepGrid`. Verify: `./gradlew :app:testDebugUnitTest --tests "*DayOverlay*"` passes.
- [ ] 3.2 Extend `MapViewModelTest` first with sun-shade-overlay "Half-screen pan", "Pan while the day is computed", "Zoomed in", "Zoomed out", "Little overlap", and sun-exposure-heatmap "Heatmap after a half-screen pan": the areas requested by the grid function after the camera rests. Keep the existing pan, cache and reconnect tests passing. Then pass `DayCache.reusable` as the base when a camera rest creates a new day, in both overlay modes. Verify: `./gradlew :app:testDebugUnitTest --tests "*MapViewModel*"` passes.
- [ ] 3.3 Keep tiles across parts (design D5): extend `OverlayRepositoryTest` first — after a grid of an area, a grid of an intersecting part at the same zoom requests no tile it has kept. Then change the "near" rule to intersecting bounds. Verify: `./gradlew :app:testDebugUnitTest --tests "*OverlayRepository*"` passes.

## 4. Drawing, counting and checks over combined grids (app)

- [ ] 4.1 Let `renderOverlay`, `countSunHours`, the agreement check and the earlier-day fallback take `StepGrid`; switch `countSunHours` to `statesAt`. Extend `RenderOverlayTest` and `SunHoursTest` first: a combined grid's image and counts equal the per-pixel reference with `stateAt`; a combined grid's image renders in at most 1.5× the time of a single grid of the same area after warm-up. Verify: `./gradlew :app:testDebugUnitTest --tests "*RenderOverlay*" --tests "*SunHours*"` passes.

## 5. Debug box (app)

- [ ] 5.1 Extend `DebugLinesTest` first: `Reused 50 %` after `Area`, `Reused 0 %` without a base, `Reused –` before a first day (settings "Debug info", "Reuse shown"). Then record `reusedShare` in `DayState`. Update `CLAUDE.md`'s `app/` and `core/` rows for `StepGrid`, `CombinedGrid` and the reuse. Verify: `./gradlew :app:testDebugUnitTest --tests "*DebugLines*"` passes and `./scripts/verify-local.sh` passes.

## 6. Device checks

- [ ] 6.1 On the phone with `Timings` and `Day state` on, `Normal`, map zoom 12 at Lauterbrunnen: let a `Sun & shade` day finish and note its `Day` line; pan by half a screen east, let the new day finish. Verify: `Reused` shows about 50 %, the new `Day` line is at most 70 % of the first one (design "Performance budget"), and the overlay covers the whole screen at every step. Record both lines in design.md.
- [ ] 6.2 On the phone: scrub through the reused day and look along the seam between the reused and the new part. Verify: no gap and no doubled tint; shadow edges crossing the seam stay continuous to within a cell; the `Grid … image` line stays ≤ 100 ms.
- [ ] 6.3 On the phone: zoom in by half a level (no reuse, `Reused 0 %`), zoom back out (reuse), and repeat 6.1 in `Sun hours`. Verify: `Reused` as expected, the heatmap covers the whole screen, and `Sun hours <t>` stays ≤ 2 s.
