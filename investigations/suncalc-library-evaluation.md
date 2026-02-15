# Evaluation: commons-suncalc vs SimpleSunCalculator

## Summary

Evaluated `commons-suncalc` (by shred; version managed in `gradle/libs.versions.toml`)
as a replacement for the custom `SimpleSunCalculator` (NOAA algorithm). Both
implementations produce very similar results for sun position. The main difference
is in sunrise/sunset timing accuracy.

**Outcome: Switched to `commons-suncalc`.** SimpleSunCalculator has been removed.

---

## Test Methodology

- 17 comparison tests across 5 locations, 3 seasons, and multiple times of day
- 120 aggregate samples (5 locations x 3 dates x 8 times each)
- Reference values from NOAA Solar Calculator and timeanddate.com
- All times UTC, all angles in degrees

## Results

### Sun Position (Elevation & Azimuth)

Both implementations produce nearly identical results:

| Metric | Average Diff | Max Diff | Where |
|--------|-------------|----------|-------|
| Elevation | 0.039 deg | 0.430 deg | Equator/Summer/18h |
| Azimuth | 0.010 deg | 0.136 deg | Equator/Equinox/0h |

**No samples had > 1 deg difference.** For the primary use case (Alps hiking), the
typical elevation difference is 0.01-0.24 deg -- both implementations are
functionally equivalent for terrain occlusion calculations.

### Sunrise/Sunset Timing

This is where `commons-suncalc` significantly outperforms:

| Scenario | Reference | Simple Error | Commons Error | Winner |
|----------|-----------|-------------|---------------|--------|
| Summer sunrise (Interlaken) | 03:34 | 6 min | 1 min | commons-suncalc |
| Summer sunset (Interlaken) | 19:25 | 6 min | 1 min | commons-suncalc |
| Winter sunrise (Interlaken) | 07:10 | 6 min | 0 min | commons-suncalc |
| Winter sunset (Interlaken) | 15:43 | 6 min | 0 min | commons-suncalc |
| Spring sunrise (Interlaken) | 05:31 | 5 min | 0 min | commons-suncalc |
| Spring sunset (Interlaken) | 17:41 | 5 min | 0 min | commons-suncalc |

`SimpleSunCalculator` has a systematic 5-6 minute offset due to its binary search
approach (integer minute resolution). `commons-suncalc` computes sunrise/sunset
analytically with sub-minute accuracy.

### Edge Cases

Both implementations agree on:
- Arctic midnight sun (70 deg N, June): both correctly show sun above horizon
- Arctic polar night (70 deg N, December): both correctly show sun below horizon
- Equator equinox noon: both show ~88 deg elevation (nearly overhead)
- Southern hemisphere: both agree to within 0.01 deg

## Comparison

| Aspect | SimpleSunCalculator | commons-suncalc |
|--------|-------------------|-----------------|
| Position accuracy | Good (within 0.5 deg) | Good (within 0.5 deg) |
| Sunrise/sunset accuracy | ~5-6 min systematic offset | Sub-minute |
| Code maintenance | ~160 lines of custom math | External library |
| Algorithm | NOAA (custom port) | Well-tested astronomical library |
| Dependencies | None | 1 external jar (~70KB) |
| Android compatibility | Yes | Yes (API 26+, our min is 29) |
| Sunrise/sunset method | Binary search (20 iterations) | Analytical computation |
| Atmospheric refraction | Not included in position | Included in `altitude` |
| Test coverage | Custom tests only | Library has its own test suite |
| Active maintenance | N/A (our code) | Yes (v3.11, June 2024) |
| License | N/A | Apache 2.0 |

## Recommendation: Switch to commons-suncalc

**Reasons to switch:**

1. **Better sunrise/sunset accuracy.** 5-6 minute errors are noticeable for hikers
   timing their approach to catch sunrise from a summit. Sub-minute accuracy is
   meaningfully better for this use case.

2. **Reduced maintenance burden.** 160 lines of trigonometry we need to maintain and
   debug vs a well-tested library with its own test suite and active maintenance.

3. **Atmospheric refraction.** The library includes refraction in `getAltitude()`,
   which is relevant for accurate horizon calculations. Our custom implementation
   handles refraction separately in `CalculateSunVisibilityUseCase`, but having it
   built into the calculator is cleaner.

4. **Drop-in replacement.** The existing `SunCalculator` interface and strategy
   pattern make switching trivial -- just change the Koin binding.

5. **Minimal cost.** ~70KB dependency, no transitive dependencies, Apache 2.0 license.

**What to watch:**

- The library's `getAltitude()` includes atmospheric refraction. The
  `CalculateSunVisibilityUseCase` also applies refraction correction (Meeus/Bennett).
  When switching, verify whether double refraction correction occurs and adjust
  accordingly (use `getTrueAltitude()` if the use case already handles refraction).

## Migration (completed)

All migration steps have been implemented:

1. Moved `commons-suncalc` from `testImplementation` to `implementation`
2. Moved `CommonsSunCalculator.kt` from test to main sources
3. Updated Koin binding in `SunCalcModule.kt` to use `CommonsSunCalculator`
4. Used `trueAltitude` (geometric, no refraction) because
   `CalculateSunVisibilityUseCase` applies its own Meeus/Bennett correction
5. Renamed `SimpleSunCalculatorTest` to `CommonsSunCalculatorTest`
6. Updated `SunriseIntegrationTest` expected times to timeanddate.com references
   (no more ~6 min offset calibration)
7. Deleted `SimpleSunCalculator.kt` and `SunCalculatorComparisonTest.kt`

## Files

- Adapter: `app/src/main/java/com/sunshine/app/suncalc/CommonsSunCalculator.kt`
- Tests: `app/src/test/java/com/sunshine/app/suncalc/CommonsSunCalculatorTest.kt`
- Dependency: `gradle/libs.versions.toml` (key: `commonsSuncalc`)
