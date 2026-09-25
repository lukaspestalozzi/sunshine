# Sunshine - Design Document

> **LEGACY — not maintained.** This is the design of the discarded first implementation, kept only as
> source material for OpenSpec changes. The current truth lives in `openspec/specs/` and `openspec/changes/`.

> **Working Title:** Sunshine
> **Version:** 0.1 (MVP)
> **Last Updated:** 2025-12-28

## 1. Project Overview

### 1.1 Vision
Sunshine is an Android app that shows users where the sun actually shines at any given time, considering the real skyline (mountains, hills). Unlike simple sunrise/sunset apps, Sunshine accounts for terrain occlusion - a valley might not see sunlight until hours after the astronomical sunrise.

### 1.2 Target Users
- **Primary:** Hikers planning trips in mountainous regions
- **Secondary:** Casual users curious about sun exposure at specific locations
- **Region Focus:** Europe, specifically the Alps

### 1.3 Core Value Proposition
"Know exactly when and where the sun will shine, even in the mountains."

---

## 2. MVP Features

### 2.1 Included in MVP
| Feature | Description | Priority |
|---------|-------------|----------|
| Interactive Map | OSM-based map with pan/zoom | Must Have |
| Date/Time Slider | Select any date and time to visualize | Must Have |
| Sun Visibility Overlay | Visual layer showing sunlit vs shaded areas | Must Have |
| Offline Maps | Download map regions for offline use | Must Have |
| Offline Elevation Data | Cached terrain data for shadow calculations | Must Have |

### 2.2 Post-MVP Features (v1.1+)
| Feature | Description |
|---------|-------------|
| Sunshine Heatmap | Show areas with longest sun exposure for a given day |
| GPS Location | Center map on current position |
| Location Bookmarks | Save favorite locations |
| Sunrise/Sunset Animations | Animated playback of sun movement |
| Widget | Home screen widget showing next sunrise at location |
| Photo Planning Mode | Optimal times for photography at a location |

---

## 3. Technical Stack

### 3.1 Core Technologies
| Component | Choice | Rationale |
|-----------|--------|-----------|
| **Language** | Kotlin | Modern, null-safe, Google-preferred |
| **Min SDK** | API 29 (Android 10) | 85% device coverage, modern APIs |
| **Target SDK** | API 34 (Android 14) | Latest stable |
| **UI Framework** | Jetpack Compose | Declarative, modern, less boilerplate |
| **Architecture** | MVVM | Clear separation, testable, Google-recommended |

### 3.2 Key Libraries
| Category | Library | Purpose |
|----------|---------|---------|
| **Maps** | osmdroid | OpenStreetMap for Android, offline capable |
| **DI** | Koin | Lightweight dependency injection |
| **Database** | Room | Local SQLite with type safety |
| **Preferences** | DataStore | Modern key-value storage |
| **Async** | Kotlin Coroutines + Flow | Reactive data streams |
| **HTTP** | Ktor Client or Retrofit | API calls for elevation data |
| **Image Loading** | Coil | Kotlin-first image loading |
| **Sun Calculation** | *Pluggable* (see section 5) | Astronomy calculations |

### 3.3 Build & Tooling
| Tool | Purpose |
|------|---------|
| Gradle (Kotlin DSL) | Build system |
| Version Catalogs | Dependency management |
| ktlint | Code formatting |
| Detekt | Static analysis |

---

## 4. Architecture

### 4.1 High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        UI Layer                              │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │  Composables │  │  Screens    │  │  Navigation         │  │
│  └──────┬──────┘  └──────┬──────┘  └──────────┬──────────┘  │
│         │                │                     │             │
│         └────────────────┼─────────────────────┘             │
│                          ▼                                   │
│  ┌───────────────────────────────────────────────────────┐  │
│  │                    ViewModels                          │  │
│  │   MapViewModel  │  SettingsViewModel  │  etc.         │  │
│  └───────────────────────────┬───────────────────────────┘  │
└──────────────────────────────┼───────────────────────────────┘
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                      Domain Layer                            │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────┐  │
│  │   Use Cases     │  │   Domain Models │  │ Interfaces  │  │
│  │                 │  │                 │  │ (Ports)     │  │
│  │ CalculateSun    │  │ SunPosition     │  │             │  │
│  │ VisibilityUseCase│ │ TerrainPoint    │  │ SunCalculator│ │
│  │                 │  │ VisibilityMap   │  │ ElevationSvc │ │
│  └────────┬────────┘  └─────────────────┘  └──────┬──────┘  │
└───────────┼───────────────────────────────────────┼──────────┘
            │                                       │
            ▼                                       ▼
┌─────────────────────────────────────────────────────────────┐
│                       Data Layer                             │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────┐  │
│  │  Repositories   │  │  Data Sources   │  │   APIs      │  │
│  │                 │  │                 │  │             │  │
│  │ ElevationRepo   │  │ Room Database   │  │ OpenElevAPI │  │
│  │ MapTileRepo     │  │ File Cache      │  │ PeakFinder? │  │
│  │ SettingsRepo    │  │ DataStore       │  │             │  │
│  └─────────────────┘  └─────────────────┘  └─────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### 4.2 Package Structure

```
com.sunshine.app/
├── SunshineApp.kt                 # Application class
├── MainActivity.kt                # Single activity
│
├── ui/                            # UI Layer
│   ├── theme/                     # Compose theme, colors, typography
│   ├── components/                # Reusable composables
│   │   ├── TimeSlider.kt
│   │   ├── DatePicker.kt
│   │   └── SunOverlay.kt
│   ├── screens/                   # Screen composables
│   │   ├── map/
│   │   │   ├── MapScreen.kt
│   │   │   └── MapViewModel.kt
│   │   ├── settings/
│   │   │   ├── SettingsScreen.kt
│   │   │   └── SettingsViewModel.kt
│   │   └── download/
│   │       ├── DownloadScreen.kt
│   │       └── DownloadViewModel.kt
│   └── navigation/
│       └── NavGraph.kt
│
├── domain/                        # Domain Layer
│   ├── model/                     # Domain models
│   │   ├── SunPosition.kt
│   │   ├── GeoPoint.kt
│   │   ├── TerrainProfile.kt
│   │   └── VisibilityResult.kt
│   ├── usecase/                   # Business logic
│   │   ├── CalculateSunVisibilityUseCase.kt
│   │   ├── GetTerrainProfileUseCase.kt
│   │   └── DownloadRegionUseCase.kt
│   └── repository/                # Repository interfaces
│       ├── ElevationRepository.kt
│       ├── MapTileRepository.kt
│       └── SettingsRepository.kt
│
├── data/                          # Data Layer
│   ├── repository/                # Repository implementations
│   │   ├── ElevationRepositoryImpl.kt
│   │   ├── MapTileRepositoryImpl.kt
│   │   └── SettingsRepositoryImpl.kt
│   ├── local/                     # Local data sources
│   │   ├── database/
│   │   │   ├── SunshineDatabase.kt
│   │   │   ├── ElevationDao.kt
│   │   │   └── entities/
│   │   ├── datastore/
│   │   │   └── SettingsDataStore.kt
│   │   └── cache/
│   │       └── TileCache.kt
│   ├── remote/                    # Remote data sources
│   │   ├── elevation/
│   │   │   ├── ElevationApi.kt
│   │   │   └── OpenElevationService.kt
│   │   └── tiles/
│   │       └── OsmTileSource.kt
│   └── mapper/                    # DTO <-> Domain mappers
│       └── ElevationMapper.kt
│
├── suncalc/                       # Sun Calculation Module (Pluggable)
│   ├── SunCalculator.kt           # Interface
│   ├── SunCalcLibAdapter.kt       # Implementation using library
│   └── PeakFinderAdapter.kt       # Alternative implementation
│
└── di/                            # Dependency Injection
    ├── AppModule.kt
    ├── DataModule.kt
    ├── DomainModule.kt
    └── SunCalcModule.kt
```

### 4.3 Key Design Decisions

#### 4.3.1 Pluggable Sun Calculator
The sun calculation component is designed as a **Strategy Pattern** to allow experimentation:

```kotlin
interface SunCalculator {
    /**
     * Calculate sun position (azimuth, elevation) for a given location and time
     */
    suspend fun calculateSunPosition(
        latitude: Double,
        longitude: Double,
        dateTime: LocalDateTime
    ): SunPosition

    /**
     * Calculate if sun is visible from a point, considering terrain
     */
    suspend fun isSunVisible(
        observerLocation: GeoPoint,
        observerElevation: Double,
        terrainProfile: TerrainProfile,
        dateTime: LocalDateTime
    ): Boolean
}
```

**Potential Implementations:**
| Implementation | Notes |
|----------------|-------|
| `LocalSunCalcAdapter` | Uses local library (ca.rmen:lib-sunrise-sunset or similar) |
| `PeakFinderAdapter` | Uses PeakFinder API (if they provide suitable endpoints) |
| `CustomCalculator` | Our own implementation if needed |

Switching implementations via Koin:
```kotlin
// In SunCalcModule.kt
single<SunCalculator> { LocalSunCalcAdapter() }  // Easy to swap
```

#### 4.3.2 Offline-First Architecture
All data flows follow offline-first pattern:

```
Request → Check Local Cache → [Hit] → Return cached data
                            → [Miss] → Fetch Remote → Cache → Return
```

#### 4.3.3 State Management
Using Kotlin StateFlow in ViewModels:

```kotlin
class MapViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()
}

data class MapUiState(
    val selectedDateTime: LocalDateTime = LocalDateTime.now(),
    val mapCenter: GeoPoint = GeoPoint(46.8182, 8.2275), // Swiss Alps default
    val zoomLevel: Double = 10.0,
    val visibilityOverlay: VisibilityMap? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)
```

---

## 5. Sun Calculation Strategy

### 5.1 The Problem
Calculating "where the sun shines" requires:
1. **Sun position:** Azimuth (compass direction) and elevation angle for a given time/location
2. **Terrain profile:** Elevation data in the direction of the sun
3. **Visibility check:** Does terrain occlude the sun from observer's position?

### 5.2 Approach Options

| Approach | Description | Pros | Cons |
|----------|-------------|------|------|
| **A: Per-pixel calculation** | For each map pixel, calculate if sun is visible | Accurate | Computationally expensive |
| **B: Sample grid** | Calculate visibility for grid points, interpolate | Faster | Less accurate at boundaries |
| **C: Shadow casting** | Ray-trace from sun, project shadows | Natural visualization | Complex implementation |
| **D: External API** | Use service like PeakFinder | Offloads complexity | Requires internet; cost? |

**Recommended for MVP:** Start with **Approach B (Sample Grid)** - calculate visibility for a grid of points and render as overlay. This balances accuracy with performance.

### 5.3 PeakFinder API Investigation (December 2025)

#### What PeakFinder Offers
- **Embed API** (not a data API): Allows embedding panoramic mountain views via iFrame or JavaScript Canvas
- **Sun/Moon display**: Can show sun ecliptic and return sun times (sunrise/sunset)
- **Horizon visualization**: Renders 360° panoramas with peak labels
- **WebAssembly-based**: Runs in browser, not native

#### API Capabilities
| Feature | Available | Notes |
|---------|-----------|-------|
| Embed panorama view | ✅ Yes | iFrame or Canvas/JS |
| Sun position display | ✅ Yes | Visual overlay on panorama |
| Sun times (rise/set) | ✅ Yes | Returns JSON: `{"sun":{"rise":"...","set":"..."}}` |
| Date/time control | ✅ Yes | `panel.astro.currentDateTime(year, month, day, hour, min)` |
| Raw horizon profile data | ❌ No | No endpoint to extract elevation angles |
| Raw elevation data | ❌ No | Cannot get DEM data |
| Offline capability | ❌ No | Requires internet for API |
| Bulk/batch queries | ❌ No | Interactive embed only |
| Commercial licensing | ❓ Unclear | No public pricing; contact required |

#### Why PeakFinder is NOT Suitable for Sunshine

1. **Embed-only, not data API**: PeakFinder provides visualization embeds, not raw horizon/elevation data we can process
2. **No offline support**: The API requires internet; our core requirement is offline functionality
3. **WebAssembly limitation**: Cannot run natively on Android; would require WebView wrapper
4. **No bulk processing**: Cannot efficiently calculate visibility for a grid of points
5. **Unclear licensing**: No public commercial API terms; would need custom agreement
6. **Wrong data format**: Returns rendered panoramas, not the terrain profile data we need for shadow calculations

#### Verdict: ❌ Not recommended for this project

### 5.4 Recommended Alternatives

#### For Sun Position Calculation
| Option | Type | Recommendation |
|--------|------|----------------|
| [commons-suncalc](https://github.com/shred/commons-suncalc) | Java Library | ✅ **Recommended** - Comprehensive, well-tested, Android-compatible |
| [ca.rmen:lib-sunrise-sunset](https://github.com/caarmen/lib-sunrise-sunset) | Java Library | ✅ Good alternative |
| Custom NOAA implementation | Code | Fallback if libraries don't fit |

#### For Terrain/Horizon Calculation
| Option | Type | Notes |
|--------|------|-------|
| **Local DEM processing** | Offline | ✅ **Recommended** - Use SRTM/NASADEM data locally |
| [HORAYZON](https://github.com/ChristianSteger/HORAYZON) | Python/Cython | Excellent reference implementation for horizon/shadow algorithms |
| [PVGIS Horizon API](https://re.jrc.ec.europa.eu/pvg_tools) | Online API | 90m resolution, good for supplementary data |
| [Open-Elevation API](https://open-elevation.com/) | Online API | For on-demand elevation queries |

### 5.5 Recommended Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     Sun Visibility Calculation                   │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────────┐     ┌──────────────────────────────────┐  │
│  │  Sun Position    │     │  Terrain/Horizon Module          │  │
│  │  Calculator      │     │                                  │  │
│  │                  │     │  ┌─────────────┐ ┌────────────┐  │  │
│  │  commons-suncalc │     │  │ DEM Data    │ │ Horizon    │  │  │
│  │  or equivalent   │     │  │ (SRTM/      │ │ Calculator │  │  │
│  │                  │     │  │  NASADEM)   │ │            │  │  │
│  │  Input:          │     │  └──────┬──────┘ └─────┬──────┘  │  │
│  │  - lat/lon       │     │         │              │         │  │
│  │  - datetime      │     │         ▼              ▼         │  │
│  │                  │     │  ┌─────────────────────────────┐ │  │
│  │  Output:         │     │  │ Ray-casting / Line-of-sight │ │  │
│  │  - azimuth       │     │  │ (inspired by HORAYZON)      │ │  │
│  │  - elevation     │     │  └─────────────────────────────┘ │  │
│  └────────┬─────────┘     └──────────────────┬───────────────┘  │
│           │                                   │                  │
│           └───────────────┬───────────────────┘                  │
│                           ▼                                      │
│              ┌─────────────────────────┐                         │
│              │  Visibility Result      │                         │
│              │  - isSunVisible: Bool   │                         │
│              │  - shadowSource: Peak?  │                         │
│              │  - nextSunTime: Time?   │                         │
│              └─────────────────────────┘                         │
└─────────────────────────────────────────────────────────────────┘
```

### 5.6 Implementation Strategy

**Phase 1: Basic sun position** (MVP)
- Use `commons-suncalc` for astronomical calculations
- Ignore terrain initially (flat horizon assumption)

**Phase 2: Terrain-aware visibility**
- Download/bundle SRTM elevation data for Alps region
- Implement horizon profile calculation (port HORAYZON concepts to Kotlin)
- Ray-cast from observer toward sun to check for terrain occlusion

**Phase 3: Optimization**
- Pre-compute horizon profiles for downloaded regions
- Cache results in Room database
- Use spatial indexing for efficient lookups

### 5.7 Data Requirements

| Data | Source | Resolution | Storage (Alps) |
|------|--------|------------|----------------|
| Elevation (DEM) | SRTM v3 / NASADEM | 30m (~1 arc-second) | ~500MB raw, ~100MB compressed |
| Horizon profiles | Pre-computed | Per 100m grid point | ~50MB |
| Sun positions | Calculated on-demand | N/A | N/A |

**Decision:** Use local DEM data with `commons-suncalc` for sun positions. Implement custom horizon calculation inspired by HORAYZON algorithms.

---

## 6. Data Sources

### 6.1 Map Tiles
**Source:** OpenStreetMap tile servers

| Tile Server | URL Pattern | Notes |
|-------------|-------------|-------|
| OSM Standard | `https://tile.openstreetmap.org/{z}/{x}/{y}.png` | Free, rate limited |
| OpenTopoMap | `https://tile.opentopomap.org/{z}/{x}/{y}.png` | Topographic, good for hiking |

**Offline Strategy:**
- User selects region + zoom levels to download
- Tiles stored in app's private storage
- Estimated size: ~50-200MB for Swiss Alps at useful zoom levels

### 6.2 Elevation Data
**Primary Source:** Open-Elevation API (https://open-elevation.com/)
- Free, open-source
- Returns elevation for lat/lon points
- Rate limits apply

**Alternative Sources:**
| Source | Resolution | Access |
|--------|------------|--------|
| Open-Elevation | ~30m (SRTM) | Free API |
| Mapbox Terrain | ~30m | API key, free tier |
| OpenTopography | Variable | API, research-focused |
| Local DEM files | 10-30m | Bundled/downloaded |

**Offline Strategy:**
- Cache elevation queries in Room database
- Pre-fetch elevation grid for downloaded map regions
- Store as spatial grid (lat/lon → elevation)

### 6.3 Data Models

```kotlin
// Cached elevation point
@Entity(tableName = "elevation_cache")
data class ElevationEntity(
    @PrimaryKey val id: String,  // "${lat}_${lon}" truncated to ~30m grid
    val latitude: Double,
    val longitude: Double,
    val elevation: Double,       // meters
    val source: String,          // "open-elevation", "mapbox", etc.
    val fetchedAt: Long          // timestamp
)

// Downloaded region metadata
@Entity(tableName = "downloaded_regions")
data class DownloadedRegion(
    @PrimaryKey val id: String,
    val name: String,            // "Swiss Alps", "Dolomites"
    val bounds: String,          // JSON: {north, south, east, west}
    val minZoom: Int,
    val maxZoom: Int,
    val tileCount: Int,
    val elevationPointCount: Int,
    val downloadedAt: Long,
    val sizeBytes: Long
)
```

---

## 7. User Interface Design

### 7.1 Screen Flow

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   Splash    │────▶│    Map      │────▶│  Settings   │
│   Screen    │     │   Screen    │     │   Screen    │
└─────────────┘     └──────┬──────┘     └─────────────┘
                           │
                           ▼
                    ┌─────────────┐
                    │  Download   │
                    │   Screen    │
                    └─────────────┘
```

### 7.2 Map Screen (Main Screen)

```
┌────────────────────────────────────────┐
│ ≡  Sunshine              ⚙️  📥       │  ← App bar with menu, settings, download
├────────────────────────────────────────┤
│                                        │
│                                        │
│           ┌────────────────┐           │
│           │                │           │
│           │   MAP VIEW     │           │
│           │   with sun     │           │
│           │   overlay      │           │
│           │                │           │
│           └────────────────┘           │
│                                        │
│                                        │
├────────────────────────────────────────┤
│  📅 Dec 26, 2024          ☀️ Sunrise   │  ← Date picker, quick actions
├────────────────────────────────────────┤
│  ◀ ━━━━━━━━●━━━━━━━━━━━━━━━━━━━━━━ ▶  │  ← Time slider (00:00 - 23:59)
│            08:30                       │
├────────────────────────────────────────┤
│  ▶ Play   │ ⏪ -1h │ ⏩ +1h │ 🔄 Now   │  ← Playback controls
└────────────────────────────────────────┘
```

### 7.3 Visual Language

| Element | Description |
|---------|-------------|
| **Sunlit areas** | Light yellow/orange transparent overlay |
| **Shaded areas** | Blue/gray transparent overlay |
| **Sun position** | Small sun icon at edge of map indicating direction |
| **Time slider** | Warm gradient (night=dark blue, day=yellow) |

### 7.4 Interaction Patterns

| Gesture | Action |
|---------|--------|
| Drag map | Pan |
| Pinch | Zoom |
| Drag time slider | Change time, update overlay |
| Tap date | Open date picker |
| Long-press on map | Show point details (elevation, sunrise/sunset times) |
| Tap Play | Animate through day |

---

## 8. Offline Strategy

### 8.1 Download Manager

```kotlin
data class DownloadRequest(
    val regionName: String,
    val bounds: BoundingBox,      // north, south, east, west
    val zoomLevels: IntRange,     // e.g., 8..14
    val includeElevation: Boolean // pre-fetch elevation grid
)

data class DownloadProgress(
    val phase: DownloadPhase,     // TILES, ELEVATION, COMPLETE
    val current: Int,
    val total: Int,
    val bytesDownloaded: Long
)
```

### 8.2 Storage Estimates (Alps Region)

| Content | Zoom Levels | Approx. Size |
|---------|-------------|--------------|
| Map tiles (Swiss Alps) | 8-12 | ~100 MB |
| Map tiles (Swiss Alps) | 8-14 | ~400 MB |
| Elevation grid (30m res) | - | ~50-100 MB |
| **Total (reasonable)** | 8-13 | **~200 MB** |

### 8.3 Offline Detection

```kotlin
class ConnectivityObserver(context: Context) {
    val isOnline: StateFlow<Boolean>

    // Used to show/hide "offline mode" indicator
    // Switch data sources between remote and cache-only
}
```

---

## 9. Testing Strategy

### 9.1 Test Types

| Type | Coverage Target | Tools |
|------|-----------------|-------|
| Unit Tests | Domain logic, ViewModels | JUnit5, MockK, Turbine (Flow testing) |
| Integration Tests | Repository + Database | Robolectric, Room in-memory |
| UI Tests | Critical user flows | Compose UI Testing |

### 9.2 Key Test Scenarios

- [ ] Sun position calculation accuracy (compare with known sources)
- [ ] Visibility calculation with various terrain profiles
- [ ] Offline mode data access
- [ ] Download progress and resumption
- [ ] Time slider updates overlay correctly

---

## 10. Development Phases

### Phase 1: Foundation ✅ Complete
- [x] Project setup (Gradle, dependencies)
- [x] Basic MVVM architecture
- [x] Koin DI setup
- [x] Map display with osmdroid
- [x] Basic navigation

### Phase 2: Core Features ✅ Complete
- [x] Time/date slider UI
- [x] Sun position calculation (pluggable) - SimpleSunCalculator using NOAA algorithm
- [x] Elevation data fetching - Open-Elevation API with Room caching
- [x] Basic visibility overlay - Terrain-aware sun visibility in overlay card

**Phase 2 Implementation Details:**
- Room database (`SunshineDatabase`) with elevation caching (`ElevationEntity`, `ElevationDao`)
- Open-Elevation API client (`ElevationApi`) with Ktor
- Offline-first `ElevationRepositoryImpl` with grid-based caching
- `TerrainProfile` and `VisibilityResult` domain models
- `CalculateSunVisibilityUseCase` combining sun position with terrain occlusion
- Enhanced `MapUiState` and `MapViewModel` for visibility data
- UI overlay showing: sun visible/blocked by terrain, horizon angle, degrees until visible

### Phase 3: Offline Capability ✅ Complete
- [x] Tile download manager - WorkManager-based background downloads
- [x] Elevation data caching (implemented in Phase 2)
- [x] Offline mode detection - ConnectivityObserver with Flow
- [x] Download UI screen - Region selection with progress tracking

**Phase 3 Implementation Details:**
- `ConnectivityObserver` for network state monitoring via Flow
- OpenTopoMap tile source (topographic, hiking-optimized)
- `DownloadableRegion` model with tile count/size estimation
- `DefaultRegionProvider` with 10 predefined European regions (configurable)
- `DownloadedRegionEntity` and `DownloadedRegionDao` for tracking downloads
- `TileDownloadWorker` using WorkManager for background tile fetching
- `TileDownloadRepositoryImpl` for download orchestration
- `DownloadViewModel` and enhanced `DownloadScreen` UI
- Room database migration (v1 → v2) for downloaded_regions table
- Automatic tile caching during normal map usage
- Transparent offline mode (cached tiles shown when offline)

### Phase 4: Polish ✅ Complete
- [x] Performance optimization
- [x] UI refinements (partial - core functionality complete)
- [x] Error handling
- [x] Testing

**Phase 4 Implementation Details:**
- **Performance Optimization:**
  - Batch elevation fetching in `ElevationRepository.getElevations()` - reduces API calls from 9 sequential to 1 batch request per visibility check
  - Parallel grid calculations in `CalculateSunVisibilityUseCase.calculateVisibilityGrid()` using `coroutineScope` and `async`
  - Optimized terrain profile building with `getTerrainProfileBatch()`

- **Error Handling:**
  - Retry logic with exponential backoff in `ElevationApi` (3 retries, 1s/2s/4s delays)
  - Snackbar error display in `MapScreen` for user-friendly error messages
  - Graceful degradation - continues with default elevation when API fails
  - Offline mode banner in `DownloadScreen`

- **Testing (Comprehensive):**
  - Unit tests: `CalculateSunVisibilityUseCaseTest` (6 tests), `ElevationRepositoryImplTest` (11 tests), `MapViewModelTest` (14 tests)
  - Integration tests: `ElevationDaoTest` (8 tests), `DownloadedRegionDaoTest` (12 tests) - Room in-memory database
  - UI tests: `MapScreenTest` (12 tests), `DownloadScreenTest` (15 tests) - Compose testing framework

### Phase 5: Extended Features
- [ ] Sunshine heatmap
- [ ] GPS integration
- [ ] Bookmarks
- [ ] Animation playback

---

## 11. Open Questions

| # | Question | Status |
|---|----------|--------|
| 1 | Which sun calculation library to use? | ✅ Resolved: `commons-suncalc` recommended |
| 2 | PeakFinder API availability and terms? | ✅ Resolved: Not suitable (see section 5.3) |
| 3 | Optimal elevation grid resolution for Alps? | 30m (SRTM) sufficient for hiking use case |
| 4 | Tile server usage policy for bulk downloads? | To check |
| 5 | Target overlay update rate (performance)? | To benchmark |
| 6 | SRTM data licensing for bundled distribution? | To verify (public domain expected) |
| 7 | Horizon calculation performance on mobile? | To prototype and benchmark |

---

## 12. References

### Core Libraries
- [osmdroid Wiki](https://github.com/osmdroid/osmdroid/wiki)
- [commons-suncalc](https://github.com/shred/commons-suncalc) - Recommended sun position library
- [Android Compose Documentation](https://developer.android.com/jetpack/compose)
- [Room Persistence Library](https://developer.android.com/training/data-storage/room)

### Sun & Terrain Calculation
- [NOAA Solar Calculator](https://gml.noaa.gov/grad/solcalc/) - Reference algorithms
- [HORAYZON](https://github.com/ChristianSteger/HORAYZON) - Horizon/shadow calculation reference (Python)
- [PVGIS Horizon Tool](https://re.jrc.ec.europa.eu/pvg_tools) - EU horizon profile service

### Elevation Data Sources
- [Open-Elevation API](https://open-elevation.com/)
- [SRTM Data (NASA)](https://www2.jpl.nasa.gov/srtm/) - 30m global elevation
- [OpenTopography](https://opentopography.org/) - High-resolution DEM data

### Investigated but Not Used
- [PeakFinder API](https://www.peakfinder.com/about/resources/api/) - Embed-only, not suitable (see section 5.3)

---

## Appendix A: Glossary

| Term | Definition |
|------|------------|
| **Azimuth** | Compass direction of the sun (0°=North, 90°=East, etc.) |
| **Elevation Angle** | Angle of sun above horizon (0°=horizon, 90°=directly overhead) |
| **DEM** | Digital Elevation Model - grid of terrain heights |
| **SRTM** | Shuttle Radar Topography Mission - source of global elevation data |
| **Terrain Profile** | Elevation data along a line from observer toward sun |
