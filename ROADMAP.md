# Sunshine 1.0 Release Roadmap

> **Current Version:** 0.1.0
> **Target Version:** 1.0.0
> **Last Updated:** 2025-12-30

---

## Executive Summary

The Sunshine app is **~75-80% complete** for a 1.0 release. All core MVP features from Phases 1-4 are implemented and tested. Remaining work focuses on visual polish, UI enhancements, and ensuring feature completeness.

**Estimated effort to 1.0:** 1-2 weeks of focused development

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
| Visibility grid overlay | Must | Medium | Not started |
| Functional offline mode toggle | Must | Low | UI exists, not enforced |
| Sun position indicator on map | Must | Medium | Not started |
| User-friendly error messages | Must | Low | Partial |
| Playback controls (-1h, +1h) | Should | Low | Not started |
| Sunrise/sunset time display | Should | Low | Functions exist, not shown |
| Date picker dialog | Should | Low | Not started |
| Performance optimization | Should | Medium | Not profiled |

---

## Release Milestones

### Milestone 1: Core Visual Features (Must-Have)

**Goal:** Make the app visually complete per design spec

#### 1.1 Visibility Grid Overlay
**Priority:** MUST | **Effort:** Medium (2-3 days)

The `VisibilityGrid` model exists but is never rendered. Need to:

- [ ] Add `VisibilityOverlay` composable in `ui/components/`
- [ ] Call `calculateVisibilityGrid()` from `MapViewModel` when map bounds change
- [ ] Render colored transparent layer on osmdroid map
  - Yellow/orange for sunlit areas
  - Blue/gray for shaded areas
- [ ] Add debouncing to prevent excessive recalculation during pan/zoom
- [ ] Consider resolution limits based on zoom level (performance)

**Files to modify:**
- `MapViewModel.kt` - Add grid calculation trigger
- `MapScreen.kt` - Integrate overlay rendering
- Create `ui/components/VisibilityOverlay.kt`

**Technical considerations:**
- `calculateVisibilityGrid()` spawns one coroutine per grid point - may need batching
- Consider caching grid results for same bounds/time
- Limit grid resolution at low zoom levels

#### 1.2 Sun Position Indicator
**Priority:** MUST | **Effort:** Medium (1-2 days)

Show where the sun is on the map edge:

- [ ] Create `SunPositionIndicator` composable
- [ ] Calculate screen position from sun azimuth
- [ ] Render sun icon at map edge in correct direction
- [ ] Update in real-time with time slider

**Design:**
```
┌─────────────────────────┐
│            ☀️           │  ← Sun icon at edge (azimuth 180° = south)
│                         │
│         [Map]           │
│                         │
└─────────────────────────┘
```

#### 1.3 Functional Offline Mode
**Priority:** MUST | **Effort:** Low (0.5 days)

The settings toggle exists but doesn't actually restrict network:

- [ ] Read `offlineModeEnabled` in `ElevationRepositoryImpl`
- [ ] Skip API calls when offline mode enabled
- [ ] Return cached data only or error if not available
- [ ] Show toast/snackbar when offline mode blocks a request

**Files to modify:**
- `ElevationRepositoryImpl.kt` - Check setting before API call
- `SettingsRepository.kt` - Ensure setting is properly exposed

#### 1.4 User-Friendly Error Messages
**Priority:** MUST | **Effort:** Low (0.5 days)

Wrap technical errors with context:

- [ ] Create `ErrorMessageMapper` utility
- [ ] Map common exceptions to user-friendly strings
- [ ] Use string resources for localization readiness

**Examples:**
| Technical Error | User Message |
|-----------------|--------------|
| `UnknownHostException` | "No internet connection. Using cached data." |
| `SocketTimeoutException` | "Server is slow. Please try again." |
| `HttpException 429` | "Too many requests. Please wait a moment." |
| Generic failure | "Something went wrong. Tap to retry." |

---

### Milestone 2: UI Polish (Should-Have)

**Goal:** Complete the user interface per design spec

#### 2.1 Playback Controls
**Priority:** SHOULD | **Effort:** Low (0.5 days)

Add time navigation buttons alongside slider:

- [ ] Add -1h and +1h buttons
- [ ] Add Play/Pause button for animation
- [ ] Implement animation loop (optional for 1.0)

**UI Layout:**
```
[⏪ -1h] [▶️ Play] [⏩ +1h]
|═══════════●═══════════════| 14:30
```

**Files to modify:**
- `MapScreen.kt` - Add button row
- `MapViewModel.kt` - Add `adjustTime(hours: Int)` function

#### 2.2 Sunrise/Sunset Display
**Priority:** SHOULD | **Effort:** Low (0.5 days)

Show sunrise and sunset times in the overlay:

- [ ] Call `calculateSunrise()` and `calculateSunset()` from ViewModel
- [ ] Display in sun info overlay card
- [ ] Update when date or location changes

**Display:**
```
☀️ Sun Position
Azimuth: 180° (South)
Elevation: 45°
─────────────────
Sunrise: 06:23
Sunset: 20:47
```

#### 2.3 Date Picker Dialog
**Priority:** SHOULD | **Effort:** Low (0.5 days)

Add proper date selection:

- [ ] Add calendar icon button next to date display
- [ ] Show Material 3 DatePicker dialog on tap
- [ ] Update ViewModel with selected date

**Files to modify:**
- `MapScreen.kt` - Add date picker trigger
- Use `androidx.compose.material3.DatePicker`

#### 2.4 Performance Optimization
**Priority:** SHOULD | **Effort:** Medium (1-2 days)

Profile and optimize grid calculation:

- [ ] Add performance logging/timing
- [ ] Implement spatial batching for elevation requests
- [ ] Limit grid point count based on zoom level
- [ ] Consider using `Dispatchers.Default` for CPU-bound work
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
- [ ] User can see sun visibility status for any point on map
- [ ] User can change date and time with slider
- [ ] User can download regions for offline use
- [ ] App works fully offline with downloaded data
- [ ] Visibility overlay shows sunlit/shaded areas on map
- [ ] Sun position is indicated visually on map

### Non-Functional Requirements
- [ ] All CI checks pass (ktlint, detekt, lint, tests, build)
- [ ] No crash on startup or common user flows
- [ ] Visibility calculation completes in < 2 seconds
- [ ] APK size < 20MB (excluding downloaded tiles)
- [ ] Supports Android 10+ (API 29+)

### Documentation
- [ ] README with screenshots
- [ ] CHANGELOG for 1.0
- [ ] Privacy policy (if publishing to Play Store)
- [ ] User-facing help/FAQ (optional)

---

## Timeline Estimate

| Milestone | Duration | Cumulative |
|-----------|----------|------------|
| Milestone 1 (Must-Have) | 4-5 days | 4-5 days |
| Milestone 2 (Should-Have) | 2-3 days | 6-8 days |
| Milestone 3 (QA) | 2-3 days | 8-11 days |
| Buffer & Polish | 2-3 days | 10-14 days |

**Target: 1.0 release in ~2 weeks**

---

## Appendix: File Reference

### Files to Create
- `app/src/main/java/com/sunshine/app/ui/components/VisibilityOverlay.kt`
- `app/src/main/java/com/sunshine/app/ui/components/SunPositionIndicator.kt`
- `app/src/main/java/com/sunshine/app/util/ErrorMessageMapper.kt`

### Files to Modify
- `MapViewModel.kt` - Grid calculation, playback controls, sunrise/sunset
- `MapScreen.kt` - UI integration for new components
- `ElevationRepositoryImpl.kt` - Offline mode enforcement
- `MapUiState.kt` - Add grid and sunrise/sunset fields

### Files for Reference
- `CalculateSunVisibilityUseCase.kt` - Has `calculateVisibilityGrid()` ready to use
- `VisibilityGrid.kt` - Model already defined
- `SimpleSunCalculator.kt` - Has `calculateSunrise/Sunset()` functions
