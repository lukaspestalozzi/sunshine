# Sunshine - Project Review

## Executive Summary

Sunshine is a well-structured Android app with clean MVVM architecture, good layer separation, and a modern tech stack. The offline-first strategy, pluggable sun calculator, and Koin-based DI are sound architectural choices. The codebase is consistent in style, follows Kotlin conventions, and has meaningful documentation.

That said, two areas stand out where focused investment would significantly improve the product:

1. **Terrain occlusion algorithm correctness** - the core feature of the app (showing where the sun *actually* shines, considering terrain) has physics modeling gaps that produce incorrect results in realistic Alpine scenarios.
2. ~~**Test coverage for critical paths**~~ **RESOLVED** - 56 tests added across 6 files. See [Area 2 Status](#area-2-test-coverage-for-critical-paths-resolved) below.

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

## Area 2: Test Coverage for Critical Paths (RESOLVED)

> **Status: RESOLVED.** 56 tests added across 6 files (3 new, 3 extended). All pass CI.

The original review identified that 5 test files existed but left the most failure-prone components entirely untested. This has been addressed:

### 2.1 Components with zero test coverage — FIXED

| Component | Status | Tests Added | Test File |
|-----------|--------|-------------|-----------|
| `DownloadViewModel` | **Covered** | 10 tests | `DownloadViewModelTest.kt` (new) |
| `ConnectivityObserver` | **Covered** | 10 tests | `ConnectivityObserverTest.kt` (new) |
| `ErrorMessageMapper` | **Covered** | 10 tests | `ErrorMessageMapperTest.kt` (new) |
| `SettingsViewModel` | Remaining | — | Simple enough to defer |
| `TileDownloadRepositoryImpl` | Remaining | — | WorkManager integration; would need instrumented tests |

**DownloadViewModelTest** covers: initial state, download progress flow updates, completed state detection, storage tracking, online/offline reflection, delegation to repository, `formatStorageSize` formatting, and `RegionWithStatus.statusText` for all 6 download states.

**ConnectivityObserverTest** covers: initial flow emission (online/offline), `onAvailable`/`onLost`/`onCapabilitiesChanged` callback behavior, callback registration verification, and `hasActiveConnection()` for all three cases (internet capability present, absent, no network). Uses `mockkConstructor` to handle `NetworkRequest.Builder` in pure JVM tests.

**ErrorMessageMapperTest** covers: `UnknownHostException`, `SocketTimeoutException`, `OfflineModeException`, `ClientRequestException` (TooManyRequests, NotFound, other status), `ServerResponseException`, unknown exceptions with/without messages, and `IOException`.

### 2.2 Existing tests lack edge cases for the core algorithm — FIXED

`SimpleSunCalculatorTest` expanded from 5 to 17 tests (+12):

| Category | Tests Added | What They Verify |
|----------|-------------|------------------|
| Numerical accuracy | 3 | Summer/winter solstice elevation within 3deg of NOAA-derived values; azimuth ~180deg at solar noon |
| Polar regions | 2 | Midnight sun (70degN, June 21, midnight UTC: sun above horizon); polar night (70degN, Dec 21, noon UTC: sun below horizon) |
| Sunrise/sunset | 4 | Reasonable hour ranges, sunrise < sunset ordering, summer day longer than winter |
| Continuity | 1 | Elevation monotonically increases from morning to noon |
| Southern hemisphere | 1 | December elevation > June elevation at Cape Town |

**Remaining gap**: Timezone contract between `MapViewModel` (local time) and `SimpleSunCalculator` (assumes UTC) is not tested. This is an architectural issue that belongs to Area 1 fixes.

### 2.3 Visibility grid calculation untested — FIXED

`CalculateSunVisibilityUseCaseTest` expanded with 6 grid tests:

- Grid returns correct bounds and resolution
- Grid produces correct point count (3x3 = 9 for 0.02deg box at 0.01 resolution)
- All points visible with flat terrain and high sun
- All points false when sun below horizon
- Intermittent elevation failures don't corrupt the grid (defaults to false)
- Grid covers all four corners of the bounding box

**Remaining gap**: No large-scale performance test (thousands of points). This is better suited for an instrumented test or benchmark.

### 2.4 No integration test for the offline-first data flow — REMAINING

This was not addressed. The existing `ElevationRepositoryImplTest` still tests cache and API independently. A full round-trip integration test (miss -> fetch -> cache -> hit) would require an in-memory Room database, which is better suited for an Android instrumented test.

### 2.5 MapViewModel event handling under rapid input — FIXED

`MapViewModelTest` expanded with 8 tests:

- Rapid `onMapCenterChanged` calls settle to final value
- Grid update debounced: zoom changes within 300ms don't trigger recalculation
- `onAdjustTime` advances correctly; handles day boundary (23:30 + 1h = 00:30 next day)
- Visibility calculation failure is non-critical (no error in UI state)
- Sunrise/sunset times propagated to UI state
- Grid null when sun below horizon
- Grid null at low zoom levels (below threshold)

### Remaining test gaps (lower priority)

| Gap | Risk | Reason Deferred |
|-----|------|-----------------|
| `SettingsViewModel` | Low | Thin wrapper around DataStore; low failure risk |
| `TileDownloadRepositoryImpl` | Medium | WorkManager integration needs instrumented test environment |
| Offline-first integration test | Medium | Needs in-memory Room DB (instrumented test) |
| Large-scale grid performance | Low | Better as a benchmark, not a unit test |

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
