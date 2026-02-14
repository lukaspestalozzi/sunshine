# Integration Test Plan: Sunrise End-to-End with Real Terrain Data

## 1. Overview

### Goal
Write an end-to-end integration test that verifies the full sunrise/visibility
computation pipeline using **real SRTM elevation data** for the Interlaken area.
Only phone external connections (HTTP network) are mocked; everything else runs
with real implementations.

### What the test proves
- `SimpleSunCalculator` (NOAA algorithm) produces astronomically correct
  sunrise/sunset times.
- `CalculateSunVisibilityUseCase` correctly combines sun position with real
  terrain profiles to determine visibility.
- `ElevationRepositoryImpl` caching and batching logic works correctly in the
  data flow.
- Terrain around Interlaken (mountains to the S/SE, flat valley NE) correctly
  delays visible sunrise relative to astronomical sunrise.

### Test type
JVM unit test (`src/test/`). Fast, no emulator required.

---

## 2. Test Architecture

### Mocking boundary

```
                          MOCKED
                       ┌───────────┐
                       │ HTTP      │
                       │ Network   │
                       └─────┬─────┘
                             │
                    ┌────────▼────────┐
                    │ MockElevationApi│  ← returns real SRTM data from fixture
                    └────────┬────────┘
                             │
 ── ALL REAL BELOW ──────────┼──────────────────────────────────────
                             │
                    ┌────────▼─────────────┐
                    │ ElevationRepositoryImpl│  ← real caching/batching logic
                    └──┬─────────────────┬─┘
                       │                 │
              ┌────────▼──────┐   ┌──────▼──────────┐
              │MockElevationDao│   │MockSettingsRepo │
              │ (HashMap)     │   │ (offline=false)  │
              └───────────────┘   └─────────────────┘
                       │
              ┌────────▼──────────────────────┐
              │ CalculateSunVisibilityUseCase  │  ← real terrain profile logic
              └────────┬──────────────────────┘
                       │
              ┌────────▼──────────────────┐
              │ SimpleSunCalculator       │  ← real NOAA algorithm
              └───────────────────────────┘
```

### Why MockElevationDao instead of Room

Room requires Android context (instrumented test). Using a simple
HashMap-backed mock for the DAO lets us run as a fast JVM test while still
exercising the real `ElevationRepositoryImpl` caching and batch logic. The DAO
SQL itself is already covered by `ElevationDaoTest` (androidTest).

### Components

| Component | Real/Mock | Rationale |
|-----------|-----------|-----------|
| `SimpleSunCalculator` | **Real** | Core algorithm under test |
| `CalculateSunVisibilityUseCase` | **Real** | Orchestration under test |
| `ElevationRepositoryImpl` | **Real** | Caching/batching under test |
| `ElevationApi` | **Mock** | External HTTP connection — the mock boundary |
| `ElevationDao` | **Mock** | HashMap-backed; avoids Room/Android dependency |
| `SettingsRepository` | **Mock** | Returns `offlineModeEnabled = false` |

---

## 3. Test Locations (Points Around Interlaken)

Five observer points with different terrain surroundings, all with real
SRTM-verified elevations.

| # | Name | Lat | Lon | Elev (m) | Terrain character |
|---|------|-----|-----|----------|-------------------|
| 1 | **Interlaken center** | 46.6863 | 7.8632 | 570 | Valley floor between lakes |
| 2 | **Unterseen** | 46.6847 | 7.8489 | 566 | West side, near Lake Thun outflow |
| 3 | **Boenigen** | 46.6883 | 7.9025 | 567 | East, Lake Brienz shore |
| 4 | **Wilderswil** | 46.6600 | 7.8600 | 637 | South, foothills — closer to mountains |
| 5 | **Matten** | 46.6778 | 7.8700 | 575 | South of center, slightly elevated |

### Surrounding terrain context

```
                N
         Niederhorn        Harder Kulm
          (1892m)            (893m)
              ╲              ╱
    Lake       ╲   Valley  ╱       Lake
    Thun        ╲  568m   ╱        Brienz
                 ╲       ╱
                  Interlaken
                 ╱       ╲
    Schynige    ╱ slopes  ╲   Lauterbrunnen
    Platte     ╱   rise    ╲     valley
    (1912m)   ╱    steeply  ╲
             S               Jungfrau (4158m)
```

---

## 4. Reference Data

### 4.1 Astronomical Sunrise/Sunset Reference Times

Source: [timeanddate.com/sun/@2660253](https://www.timeanddate.com/sun/@2660253),
[sunrise.maplogs.com](https://sunrise.maplogs.com/interlaken_switzerland.30284.html)

Coordinates used by references: 46.6855°N, 7.8585°E (Interlaken municipality).
Our test coordinates (46.6863, 7.8632) differ by ~0.5 km — negligible effect on
sunrise time (<1 second difference).

**All times converted to UTC** (the calculator operates in UTC internally):

| Date | Event | Local time | UTC | Day length |
|------|-------|-----------|-----|------------|
| **2025-06-21** (summer solstice) | Sunrise | 05:34 CEST | **03:34** | 15h 51m |
| | Sunset | 21:25 CEST | **19:25** | |
| **2025-12-21** (winter solstice) | Sunrise | 08:10 CET | **07:10** | 8h 33m |
| | Sunset | 16:43 CET | **15:43** | |
| **2025-03-20** (spring equinox) | Sunrise | 06:31 CET | **05:31** | 12h 10m |
| | Sunset | 18:41 CET | **17:41** | |
| **2025-09-22** (autumn equinox) | Sunrise | 07:15 CEST | **05:15** | 12h 10m |
| | Sunset | 19:25 CEST | **17:25** | |

**Precision note**: `SimpleSunCalculator` uses binary search returning
`LocalTime` with minute precision. Expected accuracy: **+/- 2 minutes** vs
reference. The NOAA algorithm itself is accurate to ~1 minute at this latitude.

### 4.2 Sun Azimuth at Sunrise (determines terrain direction)

Approximate sunrise azimuth (compass bearing) from timeanddate.com:

| Date | Sunrise azimuth | Direction | Terrain encountered |
|------|----------------|-----------|---------------------|
| June 21 | ~54° | NE | Flat valley, Lake Brienz shore |
| Dec 21 | ~125° | SE | **Mountain wall** (Schynige Platte area, 1903m at 5km) |
| Mar 20 | ~89° | E | Mixed: flat then rising |
| Sep 22 | ~89° | E | Mixed: flat then rising |

**Key insight for test design**: In winter, the sunrise direction (SE) faces
directly into the steep mountain wall south of Interlaken. This means
terrain-adjusted sunrise should be **significantly later** than astronomical
sunrise in winter. In summer, sunrise is from the NE across the flat valley,
so terrain delay should be **minimal**.

### 4.3 Real SRTM Elevation Data

All elevations verified via Open-Elevation API (SRTM v3, 30m resolution).

#### Observer point elevations

| Point | Lat | Lon | Elevation (m) |
|-------|-----|-----|---------------|
| Interlaken center | 46.6863 | 7.8632 | 570 |
| Unterseen | 46.6847 | 7.8489 | 566 |
| Boenigen | 46.6883 | 7.9025 | 567 |
| Wilderswil | 46.6600 | 7.8600 | 637 |
| Matten | 46.6778 | 7.8700 | 575 |

#### Terrain profiles (real SRTM elevations)

**NE direction (azimuth ~55°, summer sunrise)** from Interlaken center:

| Distance | Lat | Lon | Elevation (m) | Character |
|----------|-----|-----|---------------|-----------|
| 1 km | 46.6915 | 7.8740 | 566 | Flat valley floor |
| 5 km | 46.7121 | 7.9170 | 595 | Flat, near Lake Brienz |
| 10 km | 46.7378 | 7.9708 | 567 | Lake/valley |
| 20 km | 46.7635 | 8.0247 | 847 | Foothills |
| 50 km | 46.8150 | 8.1325 | 1368 | Pre-alpine hills |

Horizon angle from 570m observer: at 50km, (1368-570)/50000 ≈ 0.9°. Sun at
sunrise (0° geometric + 0.57° refraction) likely **clears** this terrain.
**Prediction: summer sunrise at Interlaken is close to astronomical sunrise.**

**SE direction (azimuth ~125°, winter sunrise)** from Interlaken center:

| Distance | Lat | Lon | Elevation (m) | Character |
|----------|-----|-----|---------------|-----------|
| 1 km | 46.6811 | 7.8740 | 571 | Flat |
| 5 km | 46.6605 | 7.9170 | **1903** | **Mountain wall!** |
| 10 km | 46.6348 | 7.9708 | 930 | Valley behind ridge |
| 20 km | 46.6091 | 8.0247 | 1215 | Alpine slopes |
| 50 km | 46.5577 | 8.1325 | 3140 | High Alps |

Horizon angle from 570m at 5km: atan2(1903-570, 5000) ≈ **14.9°**!
The sun must climb to ~15° elevation before clearing this obstacle.
At 50km: atan2(3140-570-168curvature, 50000) ≈ **2.7°** (curvature helps at
this distance). The 5km ridge dominates.

**Prediction: winter sunrise in Interlaken is delayed by a massive
amount — the sun doesn't clear the SE mountain wall until ~15° elevation,
which occurs hours after astronomical sunrise.**

**East direction (azimuth ~90°, equinox sunrise)** from Interlaken center:

| Distance | Lat | Lon | Elevation (m) | Character |
|----------|-----|-----|---------------|-----------|
| 100m | 46.6863 | 7.8645 | 570 | Flat |
| 500m | 46.6863 | 7.8699 | 569 | Flat |
| 1 km | 46.6863 | 7.8767 | 567 | Flat |
| 2 km | 46.6863 | 7.8901 | 568 | Flat (lake/valley) |
| 5 km | 46.6863 | 7.9305 | 896 | Rising |
| 10 km | 46.6863 | 7.9978 | 2020 | **Mountain** |

Horizon angle at 5km: atan2(896-570, 5000) ≈ 3.7°
Horizon angle at 10km: atan2(2020-570-~7m_curvature, 10000) ≈ 8.2°
**Prediction: equinox sunrise delayed moderately — sun must reach ~8° to
clear the eastern mountains at 10km.**

**South direction (azimuth ~180°)** from Interlaken center:

| Distance | Lat | Lon | Elevation (m) | Character |
|----------|-----|-----|---------------|-----------|
| 1 km | 46.6773 | 7.8632 | 600 | Gentle rise |
| 5 km | 46.6413 | 7.8632 | 1267 | Mountain slopes |
| 10 km | 46.5963 | 7.8632 | 1764 | Alpine terrain |
| 20 km | 46.5063 | 7.8632 | 2098 | High mountains |

---

## 5. Test Scenarios

### Scenario 1: Astronomical Sunrise/Sunset Accuracy

**What**: Verify `SimpleSunCalculator.calculateSunrise/Sunset` against
reference values for 4 dates at Interlaken coordinates.

**Why**: Validates the core NOAA algorithm implementation. No terrain or
elevation data involved.

**Setup**: Just `SimpleSunCalculator` (no mocks needed).

**Test cases**:

| Test | Location | Date | Expected sunrise (UTC) | Expected sunset (UTC) | Tolerance |
|------|----------|------|----------------------|---------------------|-----------|
| Summer solstice | 46.6863, 7.8632 | 2025-06-21 | 03:34 | 19:25 | ±2 min |
| Winter solstice | 46.6863, 7.8632 | 2025-12-21 | 07:10 | 15:43 | ±2 min |
| Spring equinox | 46.6863, 7.8632 | 2025-03-20 | 05:31 | 17:41 | ±2 min |
| Autumn equinox | 46.6863, 7.8632 | 2025-09-22 | 05:15 | 17:25 | ±2 min |
| Day length summer | 46.6863, 7.8632 | 2025-06-21 | >900 min | - | - |
| Day length winter | 46.6863, 7.8632 | 2025-12-21 | <540 min | - | - |

**Assertions**:
- Sunrise minute is within ±2 of reference
- Sunset minute is within ±2 of reference
- Summer day > 15h, Winter day < 9h
- Sunrise is before sunset on all dates

### Scenario 2: Midday Visibility (Sun High, No Terrain Blocking)

**What**: At solar noon on the summer solstice, verify all 5 points report sun
as visible.

**Why**: Sanity check — at ~66° elevation, no terrain around Interlaken can
block the sun. Confirms the full pipeline returns the right answer for the
obvious case.

**Setup**: Full pipeline with elevation fixtures.

**Test cases**:
- Time: 2025-06-21T11:30 UTC (approximate solar noon)
- All 5 observer points → `isSunVisible == true`
- Sun elevation should be ~60-67° at all points

### Scenario 3: Night Time (Sun Below Horizon)

**What**: At midnight, verify all points report sun below horizon.

**Why**: Confirms the pipeline short-circuits correctly when sun is below
horizon (no terrain check needed).

**Test cases**:
- Time: 2025-06-21T01:00 UTC (03:00 CEST, well before sunrise)
- All 5 observer points → `isSunVisible == false`
- Sun position elevation < 0

### Scenario 4: Terrain-Blocked Sunrise (Winter Solstice SE Mountain Wall)

**What**: At astronomical sunrise time on the winter solstice, verify
terrain blocks the sun at Interlaken center. Then find the time when the
sun finally clears the terrain.

**Why**: This is the core value proposition of the Sunshine app — telling
hikers when they'll actually get sun, not just astronomical sunrise. The
SE mountain wall at 5km (1903m) creates a ~15° horizon angle, massively
delaying visible sunrise.

**Setup**: Full pipeline with real SE terrain elevation data (via mocks).

**Test cases**:
- At 07:10 UTC (astronomical sunrise): sun at 0° elevation, terrain
  horizon at ~15° → `isSunVisible == false`
- At 07:30 UTC: sun at ~4° elevation → still blocked
- At 08:00 UTC: sun at ~8° elevation → still blocked
- At 08:30 UTC: sun at ~12° → still blocked
- At some point between ~09:00-10:00 UTC: sun clears 15° → `isSunVisible == true`

**Key assertion**: There should be a transition from blocked to visible
somewhere after astronomical sunrise. The exact time depends on the full
terrain profile computation, but it should be **at least 1 hour** after
astronomical sunrise given the 15° horizon angle.

### Scenario 5: Terrain-Unblocked Sunrise (Summer Solstice NE Flat Valley)

**What**: Shortly after astronomical sunrise on summer solstice, verify the
sun is visible from Interlaken center.

**Why**: Summer sunrise is from the NE across the flat Interlaken valley. The
maximum terrain horizon angle in this direction is <1° (at 50km). The sun
should be visible within minutes of astronomical sunrise.

**Setup**: Full pipeline with real NE terrain elevation data (via mocks).

**Test cases**:
- At 03:34 UTC (astronomical sunrise): sun at ~0° + 0.57° refraction ≈ 0.57°
  vs terrain horizon ~0.9° → likely blocked marginally
- At 03:45 UTC: sun at ~2-3° → should clear the low terrain → `isSunVisible == true`

**Key assertion**: Visible sunrise is within ~15 minutes of astronomical
sunrise (compared to hours in winter).

### Scenario 6: Cross-Point Comparison (Wilderswil vs Interlaken)

**What**: Compare terrain-adjusted sunrise between Wilderswil (closer to
southern mountains) and Interlaken center.

**Why**: Validates that terrain differences between nearby points produce
different visibility results. Wilderswil (46.66, 7.86, 637m) is 3km closer to
the mountain wall than Interlaken center, so the horizon angle should be even
steeper.

**Test cases**:
- On the winter solstice, at a time when Interlaken center gets sun,
  Wilderswil may still be in shadow (higher horizon angle to the SE).
- OR on any date, compute visibility at the same time for both points and
  verify the horizon angle at Wilderswil >= horizon angle at Interlaken center
  (for the same sun direction).

### Scenario 7: Visibility Grid Spot Check

**What**: Calculate a small visibility grid around Interlaken at a specific
time and verify a mix of visible/blocked points.

**Why**: Tests the parallel grid calculation with real data. Verifies that
points in the open valley are visible while points near mountain bases are
blocked.

**Setup**: Full pipeline with elevation fixtures covering the grid area.

**Test cases**:
- Bounds: small box around Interlaken (46.66-46.70, 7.84-7.92)
- Time: winter solstice at ~08:30 UTC (sun at ~12° from SE)
- Resolution: 0.01° (~1km)
- Valley-floor points should be blocked (SE mountain wall)
- Check that not all points are identical (some variation exists)

### Scenario 8: Seasonal Sunrise Direction Changes Visibility

**What**: At the same point (Interlaken center), compare visibility at the
same sun elevation angle but from different seasonal azimuths.

**Why**: Demonstrates the app's key feature — terrain matters, and the same
elevation angle can be visible or blocked depending on which direction the
sun is coming from.

**Test cases**:
- Find times in summer and winter when the sun is at exactly 5° elevation
- Summer (azimuth ~75°, roughly E-NE): terrain horizon likely <3° → visible
- Winter (azimuth ~135°, roughly SE): terrain horizon ~15° → blocked
- Same elevation, different direction, different result

---

## 6. Implementation Details

### 6.1 File Organization

```
app/src/test/java/com/sunshine/app/integration/
├── SunriseIntegrationTest.kt          # Main test class
├── fixtures/
│   └── InterlakenElevationFixture.kt  # Real SRTM elevation data
├── mocks/
│   ├── MockElevationDao.kt            # HashMap-backed DAO
│   └── MockSettingsRepository.kt      # Returns offline=false
```

### 6.2 MockElevationApi

Since `ElevationApi` is a concrete class (not an interface), we use MockK
to mock it. The mock's `answers` block looks up real elevation data from
the fixture, providing realistic SRTM responses without HTTP calls.

```kotlin
elevationApi = mockk()
coEvery { elevationApi.getElevations(any()) } answers {
    val points = firstArg<List<GeoPoint>>()
    val fixture = InterlakenElevationFixture.getAllElevations()
    val results = points.map { point ->
        val elevation = fixture.findNearest(point) ?: 570.0
        ElevationResult(point.latitude, point.longitude, elevation)
    }
    Result.success(results)
}
```

### 6.3 MockElevationDao

```kotlin
class MockElevationDao : ElevationDao {
    private val store = mutableMapOf<Pair<Double, Double>, ElevationEntity>()

    override suspend fun getElevation(gridLat: Double, gridLon: Double): ElevationEntity? =
        store[gridLat to gridLon]

    override suspend fun insert(entity: ElevationEntity) {
        store[entity.gridLat to entity.gridLon] = entity
    }

    override suspend fun insertAll(entities: List<ElevationEntity>) {
        entities.forEach { insert(it) }
    }

    // ... other methods with simple in-memory implementations
}
```

**Note**: `ElevationDao` is a Room `@Dao` interface. Implementing it
directly works for a mock in JVM tests (Room annotations are just markers;
the interface itself is plain Kotlin).

### 6.4 MockSettingsRepository

```kotlin
class MockSettingsRepository : SettingsRepository {
    override val offlineModeEnabled: Flow<Boolean> = flowOf(false)
    override suspend fun setOfflineModeEnabled(enabled: Boolean) { /* no-op */ }
}
```

### 6.5 InterlakenElevationFixture

A Kotlin object containing real SRTM elevation data for the Interlaken area.
Data was obtained from the Open-Elevation API (SRTM v3, 30m resolution).

The fixture provides elevation data in two ways:
1. **Point map**: Exact coordinates → elevation for observer points and
   known terrain features.
2. **Area interpolation**: For arbitrary requested points, find the nearest
   point in the fixture data. This handles the terrain profile sample points
   that the use case computes at runtime.

Structure:
```kotlin
object InterlakenElevationFixture {
    // Observer points
    val INTERLAKEN_CENTER = GeoPoint(46.6863, 7.8632) to 570.0
    val UNTERSEEN = GeoPoint(46.6847, 7.8489) to 566.0
    val BOENIGEN = GeoPoint(46.6883, 7.9025) to 567.0
    val WILDERSWIL = GeoPoint(46.6600, 7.8600) to 637.0
    val MATTEN = GeoPoint(46.6778, 7.8700) to 575.0

    // Terrain data along NE direction (azimuth ~55°, summer sunrise)
    val NE_TERRAIN = mapOf(
        GeoPoint(46.6915, 7.8740) to 566.0,
        GeoPoint(46.7121, 7.9170) to 595.0,
        GeoPoint(46.7378, 7.9708) to 567.0,
        GeoPoint(46.7635, 8.0247) to 847.0,
        GeoPoint(46.8150, 8.1325) to 1368.0,
    )

    // Terrain data along SE direction (azimuth ~125°, winter sunrise)
    val SE_TERRAIN = mapOf(
        GeoPoint(46.6811, 7.8740) to 571.0,
        GeoPoint(46.6605, 7.9170) to 1903.0,  // Mountain wall!
        GeoPoint(46.6348, 7.9708) to 930.0,
        GeoPoint(46.6091, 8.0247) to 1215.0,
        GeoPoint(46.5577, 8.1325) to 3140.0,  // High Alps
    )

    // Terrain data along E direction (azimuth ~90°, equinox)
    val E_TERRAIN = mapOf(
        GeoPoint(46.6863, 7.8645) to 570.0,
        GeoPoint(46.6863, 7.8699) to 569.0,
        GeoPoint(46.6863, 7.8767) to 567.0,
        GeoPoint(46.6863, 7.8901) to 568.0,
        GeoPoint(46.6863, 7.9305) to 896.0,
        GeoPoint(46.6863, 7.9978) to 2020.0,
    )

    // Terrain data along S direction (azimuth ~180°)
    val S_TERRAIN = mapOf(
        GeoPoint(46.6773, 7.8632) to 600.0,
        GeoPoint(46.6413, 7.8632) to 1267.0,
        GeoPoint(46.5963, 7.8632) to 1764.0,
        GeoPoint(46.5063, 7.8632) to 2098.0,
    )

    // Combined elevation lookup for any requested point
    // Uses nearest-neighbor matching within grid tolerance
    fun getAllElevations(): Map<GeoPoint, Double> = buildMap {
        put(INTERLAKEN_CENTER.first, INTERLAKEN_CENTER.second)
        put(UNTERSEEN.first, UNTERSEEN.second)
        // ... all data merged
        putAll(NE_TERRAIN)
        putAll(SE_TERRAIN)
        putAll(E_TERRAIN)
        putAll(S_TERRAIN)
    }
}
```

**Populating fixture data at test implementation time**: The exact sample point
coordinates requested by `CalculateSunVisibilityUseCase.projectPoint()` depend
on the sun's azimuth at runtime. During test implementation:

1. Run the `SimpleSunCalculator` for each test date/time to get the sun azimuth.
2. Use the same `projectPoint()` formula to compute the 9 sample coordinates.
3. Look up real SRTM elevations for those exact coordinates via the
   Open-Elevation API.
4. Add them to the fixture.

This two-phase approach (compute coordinates → look up elevations) ensures the
fixture matches exactly what the use case will request.

### 6.6 Test Wiring

```kotlin
class SunriseIntegrationTest {
    private lateinit var sunCalculator: SimpleSunCalculator
    private lateinit var elevationApi: ElevationApi  // MockK mock
    private lateinit var elevationDao: MockElevationDao
    private lateinit var settingsRepository: MockSettingsRepository
    private lateinit var elevationRepository: ElevationRepositoryImpl
    private lateinit var visibilityUseCase: CalculateSunVisibilityUseCase

    @Before
    fun setup() {
        sunCalculator = SimpleSunCalculator()
        elevationDao = MockElevationDao()
        settingsRepository = MockSettingsRepository()

        // Mock ElevationApi with real SRTM data
        elevationApi = mockk()
        coEvery { elevationApi.getElevations(any()) } answers {
            val points = firstArg<List<GeoPoint>>()
            val fixture = InterlakenElevationFixture.getAllElevations()
            val results = points.map { point ->
                val elevation = fixture.findNearest(point) ?: 570.0
                ElevationResult(point.latitude, point.longitude, elevation)
            }
            Result.success(results)
        }
        coEvery { elevationApi.getElevation(any()) } answers {
            val point = firstArg<GeoPoint>()
            val fixture = InterlakenElevationFixture.getAllElevations()
            val elevation = fixture.findNearest(point) ?: 570.0
            Result.success(elevation)
        }

        // Wire real repository with mocks
        elevationRepository = ElevationRepositoryImpl(
            elevationDao = elevationDao,
            elevationApi = elevationApi,
            settingsRepository = settingsRepository,
        )

        // Wire real use case
        visibilityUseCase = CalculateSunVisibilityUseCase(
            sunCalculator = sunCalculator,
            elevationRepository = elevationRepository,
        )
    }
}
```

### 6.7 Nearest-Neighbor Elevation Lookup

For arbitrary points requested by the use case, find the closest point in
our fixture data:

```kotlin
fun Map<GeoPoint, Double>.findNearest(target: GeoPoint): Double? {
    if (isEmpty()) return null
    // Grid tolerance: ~30m (0.0003°), the app's grid resolution
    val gridTolerance = 0.005  // ~500m, generous for fixture matching
    return entries
        .filter { (point, _) ->
            abs(point.latitude - target.latitude) < gridTolerance &&
            abs(point.longitude - target.longitude) < gridTolerance
        }
        .minByOrNull { (point, _) ->
            haversineDistance(point, target)
        }?.value
}
```

**Alternative**: Pre-compute exact sample point coordinates for each test
scenario and add them directly to the fixture. This avoids nearest-neighbor
ambiguity but couples the fixture to `projectPoint()` implementation.

---

## 7. Verification Strategy

### Tolerances

| Measurement | Tolerance | Rationale |
|-------------|-----------|-----------|
| Sunrise/sunset time | ±2 minutes | Binary search precision (1 min) + NOAA algorithm accuracy |
| Sun elevation angle | ±3° | SimpleSunCalculator test tolerance (NOAA reference) |
| Sun azimuth | ±10° | Wider tolerance; azimuth less critical for sunrise test |
| Horizon angle | ±2° | Depends on terrain resolution (30m SRTM) |
| Terrain-adjusted sunrise | ±15 minutes | Depends on horizon angle precision + sun rate of climb |

### Validation approach

1. **Astronomical tests**: Compare directly to reference times from
   timeanddate.com.
2. **Visibility tests**: Assert boolean visible/blocked status at specific
   times. The exact transition time is secondary; what matters is that:
   - At astronomical sunrise: blocked (in directions with high terrain)
   - At noon: visible (sun too high for any terrain to block)
   - Transition happens between these extremes
3. **Cross-point tests**: Assert relative ordering (Wilderswil blocked longer
   than Interlaken center).
4. **Seasonal tests**: Assert summer terrain-adjusted sunrise is closer to
   astronomical sunrise than winter.

### Confidence levels

| Scenario | Confidence | Notes |
|----------|-----------|-------|
| Astronomical sunrise accuracy | **High** | Pure algorithm test, well-defined reference values |
| Midday visibility | **High** | Sun at 66°, impossible to block |
| Night non-visibility | **High** | Sun below horizon, trivial |
| Winter terrain blocking | **High** | 15° horizon angle is massive; clear blocking |
| Summer minimal delay | **Medium** | Depends on exact terrain at computed sample points |
| Cross-point comparison | **Medium** | Depends on terrain profile differences |
| Grid spot check | **Medium** | Multiple terrain lookups, cumulative tolerance |

---

## 8. Dependencies and Prerequisites

### Test dependencies (already in build.gradle.kts)

- `junit` (4.13.2) — test runner
- `mockk` (1.13.13) — for mocking `ElevationApi`
- `kotlinx-coroutines-test` (1.9.0) — for `runBlocking` / `runTest`

### No new dependencies needed

All required test infrastructure already exists in the project.

### Fixture data collection

Before writing tests, collect elevation data for the exact sample coordinates
the use case will request. Process:

1. For each (test_point, test_datetime):
   a. Compute sun position: `SimpleSunCalculator().calculateSunPositionSync(point, dateTime)`
   b. Use azimuth + `projectPoint()` formula to get 9 sample coordinates
   c. Query `https://api.open-elevation.com/api/v1/lookup?locations=...`
   d. Add results to `InterlakenElevationFixture`

2. For refinement midpoints (if triggered):
   a. Run the test once with logging to see which midpoints are requested
   b. Look up those elevations too
   c. Add to fixture

This iterative fixture-building process ensures the test has all the data it
needs without requiring a massive DEM.

---

## 9. Implementation Steps

1. **Create the fixture data file** (`InterlakenElevationFixture.kt`)
   - Compute sample point coordinates for each test scenario
   - Query Open-Elevation API for real SRTM elevations
   - Store as a Kotlin object with maps

2. **Create the mock classes**
   - `MockElevationDao` — HashMap-backed DAO
   - `MockSettingsRepository` — returns offline=false
   - (ElevationApi is mocked via MockK on the existing class)

3. **Write the test class** (`SunriseIntegrationTest.kt`)
   - Setup method wires all real + mock components
   - Implement scenarios 1-8 as individual @Test methods
   - Use `runBlocking` for coroutine tests

4. **Iterate on fixture data**
   - Run tests, observe which coordinates are requested
   - Fill in missing elevation data from the API
   - Re-run until all tests have complete fixture coverage

5. **Verify against reference data**
   - Confirm astronomical sunrise times match timeanddate.com ±2 min
   - Confirm terrain blocking/visibility matches physical reality

---

## 10. Open Questions and Risks

### Open questions

1. **Refinement midpoints**: The terrain refinement algorithm adds midpoints
   between samples with large elevation gaps. For the SE winter terrain
   (1903m at 5km), this will trigger refinement. We need fixture data for
   those midpoints too. Resolution: iterative fixture building (step 4 above).

2. **Grid test coverage**: For Scenario 7 (visibility grid), we need
   elevation data for all grid points AND their terrain profiles. This could
   be a lot of data. Resolution: use a very coarse grid (0.01° = ~1km) with
   only 4-6 points, or use a simpler assertion (just check that the grid
   computation doesn't crash and returns non-empty results).

3. **ElevationApi is a concrete class**: MockK can mock concrete classes but
   needs the `open` modifier or MockK's `mockk()` with relaxed mode. The
   current `ElevationApi` class is final (Kotlin default). Resolution: use
   `mockk<ElevationApi>(relaxed = true)` or add `@MockK` annotation. MockK
   handles Kotlin final classes via its agent.

### Risks

- **Fixture staleness**: If `projectPoint()` or `SAMPLE_DISTANCES` change,
  the fixture data may no longer match requested coordinates. Mitigation:
  the nearest-neighbor lookup provides some tolerance.
- **SRTM resolution**: 30m SRTM may not capture narrow ridges that a 1m DEM
  would. The test uses the same data source as the production app, so this
  is consistent but means the test validates the app's *model* of terrain,
  not ground truth.
- **Binary search minute precision**: The calculator returns times with
  1-minute precision. Tests comparing times need ±2 min tolerance to avoid
  flakiness.

---

## 11. Sources

- Sunrise/sunset reference times: [timeanddate.com — Interlaken](https://www.timeanddate.com/sun/@2660253)
- Additional sunrise data: [sunrise.maplogs.com — Interlaken](https://sunrise.maplogs.com/interlaken_switzerland.30284.html)
- Elevation data: [Open-Elevation API](https://open-elevation.com/) (SRTM v3)
- NOAA solar calculation reference: [NOAA Solar Calculator](https://gml.noaa.gov/grad/solcalc/)
- NOAA equations: [Solar Equations PDF](https://gml.noaa.gov/grad/solcalc/solareqns.PDF)
- Interlaken geography: [Wikipedia — Interlaken](https://en.wikipedia.org/wiki/Interlaken)
- Jungfrau elevation: [Britannica — Jungfrau](https://www.britannica.com/place/Jungfrau-mountain-Switzerland)
- Interlaken topography: [topographic-map.com — Interlaken](https://en-gb.topographic-map.com/map-r8m4s/Interlaken/)
