# Sunshine 1.0 Release Roadmap

> **Current Version:** 0.9.0
> **Target Version:** 1.0.0
> **Last Updated:** 2025-12-30

---

## Executive Summary

The Sunshine app is **~95% complete** for a 1.0 release. All core MVP features from Phases 1-4 are implemented and tested. Milestones 1 and 2 are now complete. Remaining work focuses on QA, performance profiling, and final polish.

**Estimated effort to 1.0:** 3-5 days of focused development

---

## Current State

### What's Working

| Feature | Status | Notes |
|---------|--------|-------|
| Sun position calculation | ✅ Complete | NOAA algorithm via `SimpleSunCalculator` |
| Terrain-aware visibility | ✅ Complete | Single-point visibility with terrain occlusion |
| Elevation data caching | ✅ Complete | Room database, 30m resolution grid |
| Open-Elevation API | ✅ Complete | With retry logic and batch fetching |
| Map display | ✅ Complete | osmdroid with OpenTopoMap tiles |
| Time slider | ✅ Complete | Real-time visibility updates |
| Region downloads | ✅ Complete | WorkManager-based with progress tracking |
| Offline detection | ✅ Complete | `ConnectivityObserver` with graceful fallback |
| Test suite | ✅ Complete | 47 tests across unit/integration/UI |
| CI pipeline | ✅ Complete | ktlint, detekt, lint, tests, build |

### What's Missing for 1.0

| Feature | Priority | Effort | Status |
|---------|----------|--------|--------|
| Visibility grid overlay | Must | Medium | ✅ Complete |
| Functional offline mode toggle | Must | Low | ✅ Complete |
| Sun position indicator on map | Must | Medium | ✅ Complete |
| User-friendly error messages | Must | Low | ✅ Complete |
| Playback controls (-1h, +1h) | Should | Low | ✅ Complete |
| Sunrise/sunset time display | Should | Low | ✅ Complete |
| Date picker dialog | Should | Low | ✅ Complete |
| Performance optimization | Should | Medium | Pending (debouncing added) |

---

## Release Milestones

### Milestone 1: Core Visual Features (Must-Have) ✅ COMPLETE

**Goal:** Make the app visually complete per design spec

#### 1.1 Visibility Grid Overlay ✅
**Priority:** MUST | **Effort:** Medium (2-3 days) | **Status:** Complete

Implemented visibility grid rendering using osmdroid Polygon overlays:

- [x] Integrated grid rendering directly in `OsmMapView.kt` using `VisibilityPolygon` class
- [x] Call `calculateVisibilityGrid()` from `MapViewModel` when map bounds/time change
- [x] Render colored transparent polygons on osmdroid map
  - Yellow (`0x40FFEB3B`) for sunlit areas
  - Gray-blue (`0x404A5568`) for shaded areas
- [x] Added debouncing (500ms) to prevent excessive recalculation during pan/zoom
- [x] Resolution adjusts based on zoom level (performance optimization)

**Files modified:**
- `MapViewModel.kt` - Added `scheduleGridUpdate()` with debouncing
- `MapUiState.kt` - Added `visibilityGrid`, `isLoadingGrid`, `getVisibleBounds()`
- `OsmMapView.kt` - Added `updateVisibilityOverlay()` and `VisibilityPolygon` class

#### 1.2 Sun Position Indicator ✅
**Priority:** MUST | **Effort:** Medium (1-2 days) | **Status:** Complete

Created sun indicator at map edge showing azimuth direction:

- [x] Created `SunPositionIndicator.kt` composable
- [x] Calculate screen position from sun azimuth using trigonometry
- [x] Render sun icon (☀) at map edge in correct direction
- [x] Color-coded: gold (visible), orange (blocked), gray (below horizon)
- [x] Updates in real-time with time slider

**Files created:**
- `ui/components/SunPositionIndicator.kt`

#### 1.3 Functional Offline Mode ✅
**Priority:** MUST | **Effort:** Low (0.5 days) | **Status:** Complete

Offline mode now properly enforced in ElevationRepository:

- [x] Read `offlineModeEnabled` via `SettingsRepository` in `ElevationRepositoryImpl`
- [x] Skip API calls when offline mode enabled
- [x] Return cached data only or `OfflineModeException` if not available
- [x] Error shown via Snackbar with user-friendly message

**Files modified:**
- `ElevationRepositoryImpl.kt` - Added offline mode checks, created `OfflineModeException`
- `DataModule.kt` - Inject `SettingsRepository` into `ElevationRepositoryImpl`

#### 1.4 User-Friendly Error Messages ✅
**Priority:** MUST | **Effort:** Low (0.5 days) | **Status:** Complete

Created error message mapper for user-friendly errors:

- [x] Created `ErrorMessageMapper` utility object
- [x] Maps Ktor exceptions to user-friendly strings
- [x] Integrated in `MapViewModel` error handling

**Files created:**
- `util/ErrorMessageMapper.kt`

**Error mappings implemented:**
| Technical Error | User Message |
|-----------------|--------------|
| `UnknownHostException` | "No internet connection. Using cached data if available." |
| `SocketTimeoutException` | "Server is slow to respond. Please try again." |
| `OfflineModeException` | "Offline mode is enabled. Data not available for this location." |
| `ClientRequestException 429` | "Too many requests. Please wait a moment." |
| `ServerResponseException` | "Server is temporarily unavailable. Please try again later." |

---

### Milestone 2: UI Polish (Should-Have) ✅ COMPLETE

**Goal:** Complete the user interface per design spec

#### 2.1 Playback Controls ✅
**Priority:** SHOULD | **Effort:** Low (0.5 days) | **Status:** Complete

Added time navigation buttons alongside slider:

- [x] Add -1h and +1h buttons
- [ ] Add Play/Pause button for animation (deferred to post-1.0)
- [ ] Implement animation loop (deferred to post-1.0)

**UI Layout (implemented):**
```
[-1h]     14:30     [+1h]
|═══════════●═══════════════|
```

**Files modified:**
- `MapScreen.kt` - Added button row in `TimeControlPanel`
- `MapViewModel.kt` - Added `onAdjustTime(hours: Int)` function

#### 2.2 Sunrise/Sunset Display ✅
**Priority:** SHOULD | **Effort:** Low (0.5 days) | **Status:** Complete

Sunrise and sunset times now shown in the overlay:

- [x] Call `calculateSunrise()` and `calculateSunset()` from ViewModel
- [x] Display in sun info overlay card
- [x] Update when date or location changes

**Files modified:**
- `MapViewModel.kt` - Calculate sunrise/sunset in `updateSunPosition()`
- `MapUiState.kt` - Added `sunriseTime` and `sunsetTime` fields
- `MapScreen.kt` - Display times in `SunPositionOverlay`

#### 2.3 Date Picker Dialog ✅
**Priority:** SHOULD | **Effort:** Low (0.5 days) | **Status:** Complete

Added Material 3 date picker:

- [x] Made date row clickable to open picker
- [x] Show Material 3 DatePickerDialog on tap
- [x] Update ViewModel with selected date

**Files modified:**
- `MapScreen.kt` - Added `DatePickerDialogContent` composable

#### 2.4 Performance Optimization ⏳
**Priority:** SHOULD | **Effort:** Medium (1-2 days) | **Status:** Partial

Some optimizations implemented, profiling pending:

- [ ] Add performance logging/timing
- [ ] Implement spatial batching for elevation requests
- [x] Limit grid point count based on zoom level (resolution scales with zoom)
- [x] Added debouncing (500ms) to prevent excessive recalculation
- [ ] Cache visibility results for repeated queries

**Performance targets:**
- Grid calculation: < 500ms for visible map area
- UI responsiveness: No jank during time slider drag

---

### Milestone 3: Quality Assurance

**Goal:** Ensure release quality

#### 3.1 Database Migration Testing
**Priority:** MUST | **Effort:** Low (0.5 days)

- [ ] Test fresh install → current version
- [ ] Test v1 → v2 migration path
- [ ] Verify no data loss during upgrade

#### 3.2 Edge Case Testing
**Priority:** SHOULD | **Effort:** Medium (1 day)

- [ ] Polar regions (midnight sun, polar night)
- [ ] Equator regions (minimal seasonal variation)
- [ ] Date edge cases (DST transitions, year boundaries)
- [ ] Large region downloads (>500MB)
- [ ] Low storage conditions

#### 3.3 Device Testing
**Priority:** SHOULD | **Effort:** Medium (1 day)

- [ ] Test on various screen sizes
- [ ] Test on API 29, 33, 35
- [ ] Test in landscape orientation
- [ ] Test with system dark mode

---

## Post-1.0 Features (Phase 5)

### v1.1 - Location & Bookmarks

| Feature | Description | Effort |
|---------|-------------|--------|
| GPS Location | Center map on current position | Medium |
| Location Bookmarks | Save favorite locations | Medium |
| Quick location search | Search for places | Medium |

### v1.2 - Advanced Visualization

| Feature | Description | Effort |
|---------|-------------|--------|
| Sunshine Heatmap | Show areas with longest sun exposure | High |
| Sunrise/Sunset Animation | Animated playback of sun movement | Medium |
| Sun path arc | Draw sun trajectory on map | Medium |

### v1.3 - Widgets & Integration

| Feature | Description | Effort |
|---------|-------------|--------|
| Home Screen Widget | Next sunrise at saved location | High |
| Photo Planning Mode | Optimal photography times | Medium |
| Share location | Share sun info for a location | Low |

---

## Technical Notes

### Known Limitations

1. **Sun Calculator Accuracy**
   - Using custom NOAA implementation, not battle-tested library
   - Sufficient for hiking use case (~1 minute accuracy)
   - Consider swapping to `commons-suncalc` library for production

2. **Visibility Grid Resolution**
   - Current implementation: one coroutine per grid point
   - May need optimization for large areas
   - Consider WebGL/GPU rendering for future

3. **Elevation Data Source**
   - Open-Elevation API is free but rate-limited
   - Consider fallback sources (OpenTopoData, etc.)
   - Local DEM files for fully offline operation (post-1.0)

### Architecture Decisions for 1.0

| Decision | Rationale |
|----------|-----------|
| Single-point visibility first | Simpler, faster, covers primary use case |
| Text overlay before grid | Get core value working, iterate on visualization |
| Predefined regions only | Custom region selection adds complexity |
| WorkManager for downloads | Handles constraints and lifecycle properly |

### Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Grid rendering slow | Medium | High | Limit resolution, add loading indicator |
| API rate limiting | Low | Medium | Aggressive caching, retry logic (done) |
| Database migration failure | Low | High | Thorough testing, fallback to fresh DB |
| Memory pressure with large grids | Medium | Medium | Limit grid size, use pagination |

---

## Definition of Done for 1.0

### Functional Requirements
- [x] User can see sun visibility status for any point on map
- [x] User can change date and time with slider
- [x] User can download regions for offline use
- [x] App works fully offline with downloaded data
- [x] Visibility overlay shows sunlit/shaded areas on map
- [x] Sun position is indicated visually on map

### Non-Functional Requirements
- [ ] All CI checks pass (ktlint, detekt, lint, tests, build)
- [ ] No crash on startup or common user flows
- [ ] Visibility calculation completes in < 2 seconds
- [ ] APK size < 20MB (excluding downloaded tiles)
- [x] Supports Android 10+ (API 29+)

### Documentation
- [ ] README with screenshots
- [ ] CHANGELOG for 1.0
- [ ] Privacy policy (if publishing to Play Store)
- [ ] User-facing help/FAQ (optional)

---

## Timeline Estimate

| Milestone | Duration | Cumulative | Status |
|-----------|----------|------------|--------|
| Milestone 1 (Must-Have) | 4-5 days | 4-5 days | ✅ Complete |
| Milestone 2 (Should-Have) | 2-3 days | 6-8 days | ✅ Complete |
| Milestone 3 (QA) | 2-3 days | 8-11 days | Pending |
| Buffer & Polish | 1-2 days | 9-13 days | Pending |

**Target: 1.0 release in ~3-5 days** (QA and final polish remaining)

---

## Appendix: File Reference

### Files Created (Phase 2)
- `app/src/main/java/com/sunshine/app/ui/components/SunPositionIndicator.kt` ✅
- `app/src/main/java/com/sunshine/app/util/ErrorMessageMapper.kt` ✅

### Files Modified (Phase 2)
- `MapViewModel.kt` - Grid calculation, playback controls, sunrise/sunset ✅
- `MapScreen.kt` - UI integration, date picker, playback controls ✅
- `MapUiState.kt` - Added grid, sunrise/sunset, loading state fields ✅
- `OsmMapView.kt` - Visibility grid overlay rendering ✅
- `ElevationRepositoryImpl.kt` - Offline mode enforcement ✅
- `DataModule.kt` - Added SettingsRepository dependency ✅
- `ElevationRepositoryImplTest.kt` - Updated tests for new dependency ✅

### Files for Reference
- `CalculateSunVisibilityUseCase.kt` - Has `calculateVisibilityGrid()` ready to use
- `VisibilityGrid.kt` - Model already defined
- `SimpleSunCalculator.kt` - Has `calculateSunrise/Sunset()` functions
