# Tasks

## 1. core: compact height tiles

- [x] 1.1 Change `HeightTile` to 16-bit storage (design D6): `h = (s + 32768) · 0.25 − 1000` m, with `fromMetres(size, FloatArray)` and `height(row, column)`. Adapt `interpolateElevation` and #3's tests to the new constructor. Write the quantisation test first: −430 m, 0 m, 568.0 m and 8849 m round-trip within 0.125 m; a value outside −1000…15,383 m is rejected. Verify: `./gradlew :core:test` passes, including `ElevationInterpolationTest` unchanged in its expectations.

## 2. core: horizon tracer

- [x] 2.1 Write `HorizonGeometryTest` first, on synthetic terrain (a helper builds `HeightTile`s from a height function at the right zoom). Cases:
  - a ridge 1000 m above the eye at 5 km → 11.29° ±0.05°;
  - a peak 4000 m above the eye at 100 km → 1.90° ±0.05°;
  - a flat plain → the curvature dip at every azimuth;
  - the eye height is the zoom-14 ground height + 1.7 m.

  Then implement the band schedule, the sampling (step ≤ 0.5 px; zooms per design D2), and `HorizonTracer` with `groundTiles`, `start`, `nextTiles`, `advance`, `isDone` and `profile` (design D5). Verify: `./gradlew :core:test --tests "*HorizonGeometryTest*"` passes.
- [x] 2.2 Write `HorizonTerminationTest` first. Early termination with `H_max` (design D3) gives a profile identical to a run without termination on three synthetic terrains. `nextTiles()` excludes tiles only reachable by finished rays. The `H_max` region rule: 4810 m at 46.7° N 7.9° E, 8849 m at 27.9° N 86.9° E. Then implement the termination and the region rule. Verify: `./gradlew :core:test --tests "*HorizonTerminationTest*"` passes.
- [ ] 2.3 Write `SunEnvelopeTest` first. For Interlaken, `s_max` at azimuth 180° is about 66.8° plus the upper-limb offset (±0.3°), and azimuths north of the sun's range have no ray. Then implement `sunEnvelope` and the year clamp in the tracer (design D4). Add a test that the clamped profile gives the same sunshine states as the unclamped one at 1000 random instants over a year on a synthetic terrain. Verify: `./gradlew :core:test --tests "*SunEnvelopeTest*"` passes.
- [x] 2.4 Write `IncompleteHorizonTest` first, with the two spec scenarios: a missing far tile behind a 30° ridge → complete at 30°; a missing tile at 20 km behind a 2° horizon → incomplete with a lower bound of 2°. Also: a missing zoom-14 ground tile → the tracer reports that the ground is unknown and produces no profile. Then implement the `null`-tile handling. Verify: `./gradlew :core:test --tests "*IncompleteHorizonTest*"` passes.

## 3. core: sunshine and sun periods

- [x] 3.1 Write `SunshineTest` first:
  - constant 10° horizon at Interlaken on 2025-12-21 → SUN when `elevation + 0.266 > 10`, SHADE otherwise, checked at 5 instants;
  - an incomplete bin → SHADE when the sun is below its bound, UNKNOWN above.

  Then implement `sunshineAt` (design D5). Verify: `./gradlew :core:test --tests "*SunshineTest*"` passes.
- [x] 3.2 Write `SunPeriodsTest` first:
  - constant 10° horizon → one period whose boundaries equal the times at which the upper edge crosses 10°, ±20 s (computed with commons-suncalc in the test, cross-checked once against astral in `investigations/terrain-horizon-algorithms.md`);
  - a synthetic notch → two periods;
  - a 40-s sunny sliver → omitted;
  - an UNKNOWN instant while the sun is up → Unknown;
  - an UNKNOWN bin the sun never reaches → Known;
  - a DST day (2025-03-30, Europe/Zurich) → the day window of `sunDay`.

  Then implement `sunPeriods`. Verify: `./gradlew :core:test --tests "*SunPeriodsTest*"` passes.

## 4. app: tiles, cache and repository

- [ ] 4.1 Replace `ElevationRepository`'s LRU with an app-wide `TileCache`: 64 tiles of `HeightTile`, zooms 10–14, and fallback above zoom 12 by upsampling the parent quadrant (design D6, D7). Write `TileCacheTest` first, with a fake fetcher:
  - LRU eviction at 65 tiles;
  - a z14 404 falls back to z13, then to z12 (one parent fetch);
  - a z12 404 → `null`;
  - the upsampled child equals bilinear sampling of the parent at 4 points (±0.125 m).

  Keep `ElevationRepositoryTest` green. Verify: `./gradlew :app:testDebugUnitTest --tests "*TileCacheTest*" --tests "*ElevationRepositoryTest*"` passes.
- [ ] 4.2 Write `SunshineRepositoryTest` first, with a fake `TileCache` built from synthetic terrain:
  - a profile for a location;
  - the bands' tiles are requested concurrently;
  - a missing tile → incomplete bins;
  - the 4-profile cache hit makes no request.

  Then implement `SunshineRepository` (design D8). Verify: `./gradlew :app:testDebugUnitTest --tests "*SunshineRepositoryTest*"` passes.

## 5. app: ViewModel, panel and sun line

- [ ] 5.1 Write the `MapViewModel` sunshine tests first (test dispatcher, fake repository):
  - `Sunshine …` then the periods;
  - a new location never shows the previous location's periods;
  - no computation starts while the camera moves more often than every 300 ms;
  - a date change recomputes the periods but not the profile;
  - reconnect with an incomplete profile recomputes;
  - the sunshine state follows the selected time.

  Then add `sunshine: StateFlow<SunshineUiState>` and wire the repository in `SunshineApp` and `MapScreen.kt` (design D8). Verify: `./gradlew :app:testDebugUnitTest --tests "*MapViewModel*"` passes.
- [ ] 5.2 Write `formatSunshine` tests first in `SunFormatTest`, with the four panel scenarios of `point-sunshine` and an offset case (a period on a DST day with a different offset → `UTC+2` appended). Then implement it and add the `Sunshine` row to `SunPanel`, with the label in `strings.xml`. Make `SunLine` terrain-aware: solid, dashed or dotted (sun-position delta). Add a `SunLineTest` case for the dash pattern per state. Verify: `./gradlew :app:testDebugUnitTest --tests "*SunFormatTest*" --tests "*SunLineTest*"` passes.
- [ ] 5.3 Add debug-only timing logs for the profile computation (tiles in memory / in the disk cache / cold) and for the periods of a date. Verify: `./gradlew :app:assembleDebug` succeeds and `adb logcat` shows the timings on a device (checked in 6.2).
- [ ] 5.4 Update `docs/roadmap.md` entry #4 (decisions resolved in this design; refraction limitation kept by decision) and `CLAUDE.md` (the `core` horizon files and the shared tile cache). Verify: `grep -n "add-terrain-horizon" docs/roadmap.md` shows "resolved in its design.md".

## 6. Integration

- [ ] 6.1 Run `./scripts/verify-local.sh` and `openspec validate --all --strict`. Verify: ktlint, Android lint, all unit tests and the debug APK pass, and validation reports no failures.
- [ ] 6.2 On-device check with the CI APK. Expected:
  - Interlaken 2025-12-21 shows `Sunshine 10:09–14:51, 15:11–15:52` (±5 min per boundary), and the sun line is dashed at 15:00 and solid at 12:00;
  - Lauterbrunnen 2025-12-21 shows one period of about 11:47–13:13;
  - Interlaken 2025-06-21 shows about 06:04–20:09;
  - the Viganella church on 2025-12-21 shows `Sunshine none this day`;
  - in flight mode, a far, never-visited location shows `Sunshine unknown` and a dotted line;
  - changing the date at the same location updates the row without `…`;
  - the logged timings are within the design's performance budget, or the misses are recorded in design.md.
