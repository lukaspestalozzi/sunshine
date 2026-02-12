# Sunshine - Project Review

## Executive Summary

Sunshine is a well-structured Android app with clean MVVM architecture, good layer separation, and a modern tech stack. The offline-first strategy, pluggable sun calculator, and Koin-based DI are sound architectural choices. The codebase is consistent in style, follows Kotlin conventions, and has meaningful documentation.

That said, two areas stand out where focused investment would significantly improve the product:

1. **Terrain occlusion algorithm correctness** - the core feature of the app (showing where the sun *actually* shines, considering terrain) has physics modeling gaps that produce incorrect results in realistic Alpine scenarios.
2. **Test coverage for critical paths** - the existing tests verify basic behaviors but leave the most failure-prone components entirely untested, creating a blind spot for regressions and production bugs.

---

## Area 1: Terrain Occlusion Algorithm Correctness

The central promise of Sunshine is terrain-aware sun visibility. Unlike a simple sunrise/sunset app, it answers: "Does a mountain block the sun at this exact spot and time?" This depends on three linked calculations, each of which has issues.

### 1.1 Earth curvature ignored in horizon angle calculation

`TerrainProfile.kt:51-57` computes the angle from observer to terrain point using flat-earth geometry:

```kotlin
fun angleFromObserver(observerElevation: Double): Double {
    if (distance <= 0) return 0.0
    val heightDiff = elevation - observerElevation
    return Math.toDegrees(kotlin.math.atan2(heightDiff, distance))
}
```

This is `atan2(height_diff, distance)` - a flat plane model. It works at short distances (<5 km), but `SAMPLE_DISTANCES` in `CalculateSunVisibilityUseCase.kt:204-215` goes out to **50 km**. At 50 km, Earth curvature drops the apparent height of a distant peak by approximately **196 meters** (`d^2 / 2R` where R = 6371 km). For a 3000 m Alpine peak viewed from a 1500 m valley, this 196 m error changes the horizon angle by roughly 0.22 degrees. Since sun elevation near sunrise/sunset changes at ~1 degree per 4 minutes, this translates to a **~1 minute error in predicted visibility time** - noticeable for a hiking planning tool.

**Impact**: At long distances, terrain appears taller than it actually is (from the observer's perspective), causing the app to predict the sun is blocked when it isn't. Users in valleys would be told "sun blocked by terrain" when the sun is actually visible.

**Fix**: Apply Earth curvature correction to the height difference:
```
corrected_height = elevation - observerElevation - (distance^2 / (2 * R))
```
where R = 6,371,000 m (Earth radius). Optionally add atmospheric refraction correction (lifts apparent horizon by ~0.13 * curvature_drop).

### 1.2 Sparse terrain sampling misses narrow ridges

The terrain profile uses 9 fixed sample points at logarithmic distances (100m, 200m, 500m, ... 50km) from `CalculateSunVisibilityUseCase.kt:204-215`. Between 2 km and 5 km there is a 3 km gap. In the Alps, a narrow ridge (say 50m wide at 3.5 km distance) sits entirely between two sample points and is invisible to the algorithm.

The horizon angle calculation (`TerrainProfile.calculateHorizonAngle()`) takes the **maximum** angle across all sample points. If the actual maximum is at an unsampled ridge, the algorithm underestimates the horizon angle and reports "sun visible" when it is actually blocked.

**Impact**: False positives. A hiker plans to reach a viewpoint expecting sunlight, but a ridge between sample points blocks the sun. This is the exact scenario the app is designed to prevent.

**Fix**: Adaptive refinement. After the initial 9-point profile, check if consecutive points have a large elevation gradient. If the elevation difference between two consecutive points exceeds a threshold (e.g., 200m), insert additional sample points between them and re-query elevations. This is cheap (a few extra API calls per profile) and catches the narrow ridge case.

### 1.3 Silent fallback to 0m elevation masks errors

When elevation lookup fails, `CalculateSunVisibilityUseCase.kt:47-49` falls back silently:

```kotlin
val observerElevation =
    elevationRepository.getElevation(location)
        .getOrElse { DEFAULT_OBSERVER_ELEVATION }  // 0.0
```

And similarly for terrain points at line 154:
```kotlin
elevation = elevations[point] ?: observerElevation
```

If the elevation API is down or the cache is empty, the observer is placed at sea level (0m) and missing terrain points inherit the observer's (also wrong) elevation. In the Swiss Alps, where typical elevations are 500-4000m, using 0m produces horizon angles that are wildly off. The UI gives no indication that the data is degraded.

**Impact**: Silently incorrect results. A hiker at 2500m elevation is told the sun is blocked by a "mountain" that's actually below them, because both observer and terrain are at 0m. This contradicts the CLAUDE.md principle: "Errors should never pass silently."

**Fix**: Propagate the error or at minimum flag it. Return `Result.failure` when observer elevation is unknown rather than guessing 0m. In the UI, show a degraded-data indicator (e.g., "Elevation data unavailable - results may be inaccurate").

### 1.4 `runBlocking` in sunrise/sunset calculation

`SimpleSunCalculator.kt:120` calls `runBlocking` inside `calculateSunEvent`:

```kotlin
val position = kotlinx.coroutines.runBlocking { calculateSunPosition(location, testTime) }
```

This is called from `calculateSunrise`/`calculateSunset`, which are themselves `suspend` functions invoked from `MapViewModel.updateSunPosition()` on the main dispatcher. `runBlocking` blocks the calling thread (20 iterations of binary search = 20 blocking calls). Since `calculateSunPosition` is a pure CPU computation (no I/O), the `suspend` modifier on it is misleading and the `runBlocking` is unnecessary - it should be a regular function call.

**Impact**: Potential ANR (Application Not Responding) if called on the main thread, and inefficient coroutine usage even off the main thread.

**Fix**: Make `calculateSunPosition` a regular (non-suspend) function since it's pure computation, then call it directly from `calculateSunEvent` without `runBlocking`.

### 1.5 No atmospheric refraction correction

The sun position calculation doesn't account for atmospheric refraction, which bends light and makes the sun appear approximately 0.57 degrees higher than its geometric position when near the horizon. For terrain occlusion at low sun angles (the exact scenario hikers care about most), this is the difference between "sun visible" and "sun blocked."

---

## Area 2: Test Coverage for Critical Paths

The project has 5 test files with reasonable tests for the code paths they cover. But several components that are most likely to fail in production have **zero tests**. The testing gap is not in quantity but in what is left out.

### 2.1 Components with zero test coverage

| Component | Risk Level | Why It Matters |
|-----------|-----------|----------------|
| `DownloadViewModel` | High | Manages offline region downloads; users depend on progress tracking |
| `SettingsViewModel` | Medium | Stores offline mode preference; wrong state = wrong app behavior |
| `ConnectivityObserver` | High | Determines online/offline behavior; race conditions in callbacks |
| `ErrorMessageMapper` | Medium | User-facing error messages; unmapped exceptions show "Something went wrong" |
| `TileDownloadRepositoryImpl` | High | WorkManager integration for background downloads |

`ConnectivityObserver` in particular has race conditions (e.g., `onLost` calls `hasActiveConnection()` which could return true if a different network came online between the callback and the check) that are only discoverable through testing.

### 2.2 Existing tests lack edge cases for the core algorithm

`SimpleSunCalculatorTest` has 5 tests that verify basic properties (sun below horizon at midnight, above at noon, higher in summer). These are necessary but insufficient. Missing test cases:

- **Polar regions** (latitude > 66.5 deg): midnight sun and polar night. The binary search in `calculateSunEvent` assumes the sun rises and sets every day, which is false at high latitudes. The function should return `null` for these cases, but this is untested.
- **Accuracy validation against reference data**: No test compares calculated azimuth/elevation against known-good values (e.g., NOAA Solar Calculator output for a specific location/time). The current tests only check directional properties ("sun is above horizon at noon") but not numeric accuracy. A bug that returns 45 deg elevation instead of 66.5 deg would pass all current tests.
- **Timezone handling**: `SimpleSunCalculator` converts `LocalDateTime` to Julian date via `ZoneOffset.UTC`. If the caller passes local time instead of UTC (which `MapViewModel` does - it uses `LocalDateTime.of(state.selectedDate, state.selectedTime)` with no timezone conversion), all results are off by the timezone offset. For CET (UTC+1), this is a 1-hour error in sunrise/sunset times.

### 2.3 Visibility grid calculation untested

`CalculateSunVisibilityUseCase.calculateVisibilityGrid()` generates a grid of points, launches parallel coroutines for each, and collects results. This is the most computationally expensive operation in the app and the most visible to users (it renders the map overlay). Yet:

- No test verifies that grid generation produces the correct number of points for a given bounding box and resolution.
- No test checks behavior when the grid contains thousands of points (performance).
- No test verifies that a failed visibility calculation for one grid point (line 91: `.getOrNull() ?: false`) doesn't corrupt the entire grid.
- The floating-point loop in `generateGridPoints` (line 117-124, using `lat += resolution`) accumulates precision errors over many iterations. No test verifies that the generated grid covers the full bounding box without gaps.

### 2.4 No integration test for the offline-first data flow

The `ElevationRepositoryImplTest` tests cache hits and API calls independently, but there is no test for the full offline-first sequence: check cache, miss, fetch from API, cache result, subsequent lookup returns cached value. This is the fundamental data flow of the app and a regression here breaks the offline experience.

### 2.5 MapViewModel event handling under rapid input

`MapViewModel.onMapCenterChanged()` is called on every map pan event (potentially 60 times/second). Each call launches a new coroutine via `updateSunPosition()`, which in turn launches visibility and grid calculations. While the grid is debounced (500ms), the sun position and visibility calculations are not. No test verifies behavior under rapid sequential calls - whether old coroutines are properly cancelled, whether state updates are consistent, or whether resource consumption is bounded.

---

## Summary of Recommended Actions

### For Area 1 (Algorithm Correctness):

| Priority | Action | Files Affected |
|----------|--------|---------------|
| High | Add Earth curvature correction to `TerrainPoint.angleFromObserver()` | `TerrainProfile.kt` |
| High | Propagate elevation errors instead of silent 0m fallback | `CalculateSunVisibilityUseCase.kt` |
| High | Remove `runBlocking` from `calculateSunEvent` | `SimpleSunCalculator.kt` |
| Medium | Add adaptive terrain refinement between sparse samples | `CalculateSunVisibilityUseCase.kt` |
| Medium | Add atmospheric refraction correction | `SimpleSunCalculator.kt` |
| Medium | Clarify/enforce UTC timezone contract in `SunCalculator` | `SunCalculator.kt`, `MapViewModel.kt` |

### For Area 2 (Test Coverage):

| Priority | Action | Target |
|----------|--------|--------|
| High | Add accuracy tests for `SimpleSunCalculator` against NOAA reference values | `SimpleSunCalculatorTest.kt` |
| High | Test `CalculateSunVisibilityUseCase.calculateVisibilityGrid()` | New test file |
| High | Add tests for `ConnectivityObserver` including race conditions | New test file |
| High | Add tests for `DownloadViewModel` state transitions | New test file |
| Medium | Test polar region handling (midnight sun / polar night) | `SimpleSunCalculatorTest.kt` |
| Medium | Test `MapViewModel` under rapid `onMapCenterChanged` calls | `MapViewModelTest.kt` |
| Medium | Add integration test for offline-first cache flow | `ElevationRepositoryImplTest.kt` |
| Low | Test `ErrorMessageMapper` for all Ktor exception types | New test file |
