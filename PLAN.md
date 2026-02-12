# Implementation Plan: Area 1 - Terrain Occlusion Algorithm Correctness

## Overview

Five issues identified in REVIEW.md Area 1, ordered by implementation dependency:

| # | Issue | Impact | Complexity | Dependencies |
|---|-------|--------|------------|--------------|
| 1.4 | `runBlocking` in sunrise/sunset | ANR risk | Low | None |
| 1.5 | No atmospheric refraction | ~0.57deg error at horizon | Low | None |
| 1.1 | Earth curvature ignored | ~1min error at 50km | Low | None |
| 1.3 | Silent fallback to 0m elevation | Silently wrong results | Medium | None |
| 1.2 | Sparse terrain sampling | Misses narrow ridges | Medium | 1.1 (curvature must work first) |

---

## Step 1: Remove `runBlocking` from `SimpleSunCalculator` (Issue 1.4)

**Files:** `SimpleSunCalculator.kt`

**Problem:** `calculateSunEvent` (line 120) calls `runBlocking { calculateSunPosition(...) }` inside a loop (20 iterations). `calculateSunPosition` is a pure CPU function — no I/O, no coroutines needed — but is marked `suspend`. The `runBlocking` is a workaround for calling a `suspend` function from a non-suspend context.

**Fix:**
1. Extract the pure computation from `calculateSunPosition` into a **regular (non-suspend) function** `calculateSunPositionSync(location, dateTime): SunPosition`.
2. Have the `suspend` override delegate to the sync version: `override suspend fun calculateSunPosition(...) = calculateSunPositionSync(...)`.
3. Replace `runBlocking { calculateSunPosition(...) }` in `calculateSunEvent` with a direct call to `calculateSunPositionSync(...)`.

**Why not just remove `suspend` from the interface?** The `SunCalculator` interface declares `suspend fun calculateSunPosition(...)`. Other implementations (future API-based calculators) may genuinely need suspension. Keeping the interface `suspend` is correct. The fix is internal to `SimpleSunCalculator`.

**Tests:**
- Existing `SimpleSunCalculatorTest` tests should all still pass (behavioral equivalence).
- No new tests needed — this is a pure refactor with no behavioral change.

**Verification:** `./scripts/run-with-proxy.sh testDebugUnitTest --tests "*.SimpleSunCalculatorTest"`

---

## Step 2: Add atmospheric refraction correction (Issue 1.5)

**Files:** `TerrainProfile.kt` (model), `CalculateSunVisibilityUseCase.kt` (usage)

**Problem:** When the sun is near the horizon, atmospheric refraction bends light and makes the sun appear ~0.57deg higher than its geometric position. The current `blocksSun` check compares raw geometric sun elevation against the terrain horizon angle without this correction. This means the app reports "sun blocked" for about 2-4 extra minutes near sunrise/sunset.

**Fix:**
Add a refraction-corrected sun elevation when comparing against horizon angle. The standard atmospheric refraction approximation for low angles:

```
refraction_degrees ≈ 1.02 / tan(toRadians(elevation + 10.3 / (elevation + 5.11))) / 60
```

This is the Meeus/Bennett formula used by NOAA. For simplicity and clarity, apply the correction at the **comparison point** in `CalculateSunVisibilityUseCase.calculateVisibility()` (line 56), not inside the sun calculator itself (which calculates geometric position, which is correct behavior).

**Changes:**
1. Add a top-level or companion function in `CalculateSunVisibilityUseCase`:
   ```kotlin
   private fun refractionCorrection(geometricElevationDeg: Double): Double
   ```
   Returns the refraction offset in degrees (always positive, ~0.57deg at horizon, ~0deg at zenith).
2. In `calculateVisibility()` line 56, change:
   ```kotlin
   val isSunVisible = sunPosition.elevation > horizonAngle
   ```
   to:
   ```kotlin
   val apparentElevation = sunPosition.elevation + refractionCorrection(sunPosition.elevation)
   val isSunVisible = apparentElevation > horizonAngle
   ```
3. Update `degreesUntilVisible` calculation similarly (line 61).

**Tests (new in `CalculateSunVisibilityUseCaseTest`):**
- `refraction makes sun visible when geometric elevation is slightly below horizon angle`: Set sun geometric elevation to, say, 2.0deg, horizon angle to 2.3deg. Without refraction, sun is blocked. With refraction (~0.3deg correction at 2deg), sun should be visible.
- `refraction correction is negligible at high elevation`: Sun at 60deg, verify result unchanged vs. a no-refraction scenario (correction < 0.01deg).
- `refraction does not apply when sun is well below horizon`: Sun at -10deg, result should be belowHorizon regardless.

**Verification:** `./scripts/run-with-proxy.sh testDebugUnitTest --tests "*.CalculateSunVisibilityUseCaseTest"`

---

## Step 3: Add Earth curvature correction to horizon angle (Issue 1.1)

**Files:** `TerrainPoint.kt` (in `TerrainProfile.kt`)

**Problem:** `TerrainPoint.angleFromObserver()` uses flat-earth geometry: `atan2(heightDiff, distance)`. At 50km distance, Earth curvature drops a terrain point by ~196m (`d^2 / (2 * R)` where R = 6,371,000m). This makes distant terrain appear taller than it is, causing false "sun blocked" reports.

**Fix:**
Apply curvature correction to the height difference before computing the angle:

```kotlin
fun angleFromObserver(observerElevation: Double): Double {
    if (distance <= 0) return 0.0
    val curvatureDrop = (distance * distance) / (2.0 * EARTH_RADIUS_METERS)
    val correctedHeightDiff = elevation - observerElevation - curvatureDrop
    return Math.toDegrees(kotlin.math.atan2(correctedHeightDiff, distance))
}
```

Where `EARTH_RADIUS_METERS = 6_371_000.0` is defined as a companion constant.

Optionally add atmospheric refraction to the curvature model (standard practice: effective Earth radius = 7/6 * R, meaning refraction offsets ~14% of the curvature drop). This is separate from the sun-position refraction in Step 2 — this corrects how far we can "see" around the curve. Use the standard `k = 7/6` factor:

```kotlin
val effectiveRadius = EARTH_RADIUS_METERS * REFRACTION_FACTOR  // 7.0/6.0
val curvatureDrop = (distance * distance) / (2.0 * effectiveRadius)
```

**Tests (new `TerrainProfileTest.kt`):**
- `curvature correction reduces horizon angle at 50km`: Create a TerrainPoint at 50km, 3000m elevation, observer at 1500m. Compare angle with and without curvature. The corrected angle should be lower (distant terrain appears shorter).
- `curvature correction is negligible at short distance`: TerrainPoint at 100m. Angle should be nearly identical with or without correction (curvatureDrop < 0.001m).
- `curvature correction magnitude at 50km`: Verify curvatureDrop ≈ 196m (flat Earth) or ≈ 168m (with k=7/6 refraction).
- `horizon angle is negative for observer above distant same-height terrain`: Observer at 1500m, terrain at 1500m, 50km away. Without curvature the angle is 0. With curvature correction, angle should be negative (terrain appears below observer due to Earth's curve).
- `calculateHorizonAngle uses corrected angles`: Full TerrainProfile test with multiple points verifying the max is correctly computed with curvature.

**Verification:** `./scripts/run-with-proxy.sh testDebugUnitTest --tests "*.TerrainProfileTest"`

---

## Step 4: Propagate elevation errors instead of silent fallback (Issue 1.3)

**Files:** `CalculateSunVisibilityUseCase.kt`, `VisibilityResult.kt`, `MapViewModel.kt`, `MapUiState.kt`

**Problem:** Two silent fallbacks:
1. Observer elevation defaults to 0.0 when lookup fails (line 47-49)
2. Missing terrain elevations inherit observer elevation (line 154)

In the Alps (500-4000m), 0m is always wrong and produces meaningless results.

**Fix — phased approach:**

### 4a. Add data quality flag to VisibilityResult

Add a field to `VisibilityResult` to signal degraded data:

```kotlin
data class VisibilityResult(
    ...
    val isElevationDegraded: Boolean = false,
)
```

Update factory methods (`visible`, `blocked`, `belowHorizon`) to accept this parameter.

### 4b. Track and propagate elevation failures in the use case

In `calculateVisibility()`:
1. Track whether observer elevation lookup failed:
   ```kotlin
   val observerElevationResult = elevationRepository.getElevation(location)
   val elevationDegraded = observerElevationResult.isFailure
   val observerElevation = observerElevationResult.getOrElse { DEFAULT_OBSERVER_ELEVATION }
   ```
2. Track whether terrain batch elevation failed:
   ```kotlin
   val elevationsResult = elevationRepository.getElevations(pointsList)
   val terrainDegraded = elevationsResult.isFailure
   val elevations = elevationsResult.getOrElse { emptyMap() }
   ```
3. Pass `isElevationDegraded = elevationDegraded || terrainDegraded` to the `VisibilityResult` factories.

### 4c. Surface degraded state in UI

In `MapUiState`, add:
```kotlin
val isElevationDegraded: Boolean get() = visibility?.isElevationDegraded ?: false
```

In `MapViewModel.updateVisibility()`, the flag is already propagated through `VisibilityResult`.

The actual UI rendering (showing a warning banner) is a UI concern — we just need the data to flow correctly. The UI layer can check `state.isElevationDegraded` to show an indicator.

**Why keep the fallback instead of failing?** Failing entirely would show nothing — worse than approximate data with a warning. The user still gets sun position (which doesn't need elevation), plus a degraded terrain check with a clear indicator. This matches the "errors should never pass silently" principle: the error doesn't pass silently because the UI shows it.

**Tests:**
- `visibility result has degraded flag when observer elevation fails`: Mock elevation to fail, verify `isElevationDegraded = true`.
- `visibility result has degraded flag when terrain elevation fails`: Mock batch elevation to fail, verify `isElevationDegraded = true`.
- `visibility result is not degraded when all elevation lookups succeed`: Verify `isElevationDegraded = false` in normal case.
- Existing tests should still pass (factory methods with default `false`).

**Verification:** `./scripts/run-with-proxy.sh testDebugUnitTest --tests "*.CalculateSunVisibilityUseCaseTest"`

---

## Step 5: Adaptive terrain sampling to catch narrow ridges (Issue 1.2)

**Files:** `CalculateSunVisibilityUseCase.kt`

**Problem:** The 9 fixed sample points at logarithmic distances (100m to 50km) leave gaps where narrow ridges can hide. Between 2km and 5km there's a 3km gap. An Alpine ridge at 3.5km is invisible to the algorithm.

**Fix:** After the initial 9-point profile, do one refinement pass. For each pair of consecutive terrain points, if the elevation gradient between them is steep (suggesting interesting terrain), insert a midpoint and re-query its elevation.

```kotlin
private suspend fun refineTerrainProfile(
    observer: GeoPoint,
    azimuth: Double,
    initialProfile: TerrainProfile,
): TerrainProfile {
    val refinedPoints = mutableListOf<TerrainPoint>()
    val pointsToQuery = mutableListOf<Pair<Double, GeoPoint>>()

    for (i in 0 until initialProfile.points.size - 1) {
        val current = initialProfile.points[i]
        val next = initialProfile.points[i + 1]
        refinedPoints.add(current)

        val elevationDiff = kotlin.math.abs(current.elevation - next.elevation)
        val distanceGap = next.distance - current.distance

        // Refine if: large elevation change OR large gap with non-trivial terrain
        if (elevationDiff > REFINEMENT_ELEVATION_THRESHOLD ||
            (distanceGap > REFINEMENT_DISTANCE_THRESHOLD && elevationDiff > REFINEMENT_MIN_ELEVATION_DIFF)
        ) {
            val midDistance = (current.distance + next.distance) / 2.0
            val midPoint = projectPoint(observer, azimuth, midDistance)
            pointsToQuery.add(midDistance to midPoint)
        }
    }
    // Add last point
    refinedPoints.add(initialProfile.points.last())

    if (pointsToQuery.isEmpty()) return initialProfile

    // Batch fetch the additional midpoints
    val midElevations = elevationRepository.getElevations(pointsToQuery.map { it.second })
        .getOrElse { emptyMap() }

    // Insert midpoints into the profile
    // ... (merge sorted by distance)
}
```

**Constants:**
```kotlin
private const val REFINEMENT_ELEVATION_THRESHOLD = 200.0  // meters
private const val REFINEMENT_DISTANCE_THRESHOLD = 2000.0  // meters
private const val REFINEMENT_MIN_ELEVATION_DIFF = 50.0    // meters
```

**Integration:** Call `refineTerrainProfile()` after `getTerrainProfileBatch()` in `calculateVisibility()`:

```kotlin
val initialProfile = getTerrainProfileBatch(location, observerElevation, sunPosition.azimuth)
val terrainProfile = refineTerrainProfile(location, sunPosition.azimuth, initialProfile)
```

**Cost:** At most 8 additional elevation queries (one per gap), batched into a single API call. Negligible compared to the existing 9-point batch.

**Tests:**
- `refinement adds midpoints for large elevation gaps`: Mock initial profile with 500m elevation difference between consecutive points. Verify extra query is made.
- `refinement does not add midpoints for flat terrain`: Mock initial profile with <50m differences. Verify no extra queries.
- `refined profile catches ridge between sample points`: Set up a scenario where a ridge exists at the midpoint between two samples. Without refinement, sun is visible. With refinement, the ridge blocks it.
- `refinement preserves existing points`: All original 9 points should remain in the refined profile.

**Verification:** `./scripts/run-with-proxy.sh testDebugUnitTest --tests "*.CalculateSunVisibilityUseCaseTest"`

---

## Step 6: Full integration verification

After all fixes:

1. Run full test suite: `./scripts/verify-local.sh`
2. Verify all existing tests still pass (no regressions)
3. Verify all new tests pass
4. Commit each step separately with descriptive messages
5. Update REVIEW.md to mark Area 1 as RESOLVED

---

## Execution Order Summary

```
Step 1: Remove runBlocking          → SimpleSunCalculator.kt (refactor, no behavior change)
Step 2: Atmospheric refraction      → CalculateSunVisibilityUseCase.kt (new logic + tests)
Step 3: Earth curvature correction  → TerrainProfile.kt (new logic + new test file)
Step 4: Elevation error propagation → VisibilityResult.kt, UseCase, ViewModel, UiState (data flow)
Step 5: Adaptive terrain sampling   → CalculateSunVisibilityUseCase.kt (new logic + tests)
Step 6: Full verification           → verify-local.sh, REVIEW.md update
```

Each step is independently committable and testable. Steps 1-3 can be done in any order. Step 4 is independent. Step 5 depends on Step 3 (curvature correction should be in place before adding more sample points at long distances). Step 6 is always last.
