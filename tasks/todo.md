# Sunshine Heatmap Feature

## Goal
Show total hours of sun exposure across a grid for a given day, rendered as a color gradient overlay on the map.

## Design Decisions

### Approach
For each grid point, scan the day from astronomical sunrise to sunset in time steps, checking terrain-aware visibility at each step. Accumulate visible minutes per point. Render as a color gradient from cool (low exposure) to warm (high exposure).

### Key parameters
- **Time step**: 30 minutes (balance between accuracy and performance)
- **Grid resolution**: Reuse existing zoom-adaptive resolution (same as visibility grid)
- **Max grid points**: 200 (reduced from 500 — each point now requires ~30 visibility checks instead of 1)
- **Concurrency**: Reuse existing semaphore (8 concurrent)

### Performance estimate
- 200 grid points × ~30 time steps (sunrise-sunset, 30min each) = ~6,000 visibility calculations
- Each visibility check: 1 sun position calc + 1 batch elevation fetch (cached after first)
- Elevation data is heavily cached — after the first time step, most elevation lookups hit Room cache
- Heatmap is triggered by explicit user action (toggle button), not on every pan/zoom

### Rendering
- Color gradient: deep blue (0h) → cyan → green → yellow → orange → red (max hours)
- Alpha: 60% for heatmap cells
- Replaces the normal visibility grid while active (they're mutually exclusive)

## Implementation Plan

### 1. Domain model: `SunExposureGrid`
- **File**: `domain/model/SunExposureGrid.kt` (new)
- Data class with `bounds: BoundingBox`, `resolution: Double`, `date: LocalDate`, `points: Map<GeoPoint, Double>` (hours of exposure)
- `maxExposure` computed property for color scaling
- Companion with `DEFAULT_TIME_STEP_MINUTES = 30`

### 2. Use case: `calculateSunExposureGrid()` method
- **File**: `domain/usecase/CalculateSunVisibilityUseCase.kt` (edit)
- New public method: `calculateSunExposureGrid(bounds, date, resolution, timeStepMinutes) -> Result<SunExposureGrid>`
- Algorithm:
  1. Get astronomical sunrise/sunset for center of bounds
  2. Generate grid points (reuse `generateGridPoints`, cap at `MAX_HEATMAP_GRID_POINTS = 200`)
  3. For each grid point (parallel, semaphore-bounded):
     - Scan from sunrise to sunset in `timeStepMinutes` steps
     - At each step, call `isTerrainVisible(point, dateTime)`
     - Accumulate visible steps × timeStepMinutes → hours
  4. Return `SunExposureGrid`

### 3. UI state: add heatmap fields
- **File**: `ui/screens/map/MapUiState.kt` (edit)
- Add: `sunExposureGrid: SunExposureGrid? = null`
- Add: `isHeatmapMode: Boolean = false`
- Add: `isLoadingHeatmap: Boolean = false`
- Computed: `showHeatmapOverlay: Boolean` (heatmapMode && grid != null)
- Update `showGridOverlay` to exclude heatmap mode

### 4. ViewModel: heatmap toggle and calculation
- **File**: `ui/screens/map/MapViewModel.kt` (edit)
- Add `heatmapJob: Job?`
- Add `onToggleHeatmap()` — toggles `isHeatmapMode`, triggers calculation or clears
- Add `updateHeatmap()` — calls use case, updates state
- Heatmap recalculates on date or location change (debounced, 1s)
- When heatmap mode is off, clear the grid

### 5. Map overlay: heatmap rendering
- **File**: `ui/components/OsmMapView.kt` (edit)
- Add `sunExposureGrid: SunExposureGrid?` parameter
- New `SunExposureOverlay` class (similar to `VisibilityGridOverlay`)
- Color interpolation function: hours → ARGB color
- Gradient: 0h=blue, mid=green/yellow, max=orange/red
- Add new `LaunchedEffect` to update heatmap overlay

### 6. MapScreen: toggle button
- **File**: `ui/screens/map/MapScreen.kt` (edit)
- Add heatmap toggle button in the top bar (or floating action button)
- Pass `sunExposureGrid` to `OsmMapView`
- Show loading indicator when calculating
- Show legend card when heatmap is active

### 7. String resources
- **File**: `app/src/main/res/values/strings.xml` (edit)
- Add: heatmap toggle label, loading text, legend labels

### 8. Color constants
- **File**: `ui/theme/Color.kt` (edit)
- Add heatmap gradient colors

### 9. Tests
- **File**: `domain/usecase/CalculateSunVisibilityUseCaseTest.kt` (edit)
- Test: exposure grid returns correct hours for fully sunlit flat terrain
- Test: exposure grid returns 0 hours when sun never rises
- Test: exposure grid returns reduced hours with blocking terrain
- Test: exposure grid respects max grid points cap

- **File**: `ui/screens/map/MapViewModelTest.kt` (edit)
- Test: toggle heatmap sets isHeatmapMode
- Test: heatmap calculation is triggered when mode is enabled
- Test: heatmap is cleared when mode is disabled

## Verification
- [ ] `./scripts/verify-local.sh --quick` passes (ktlint + detekt)
- [ ] Unit tests pass
- [ ] Full `./scripts/verify-local.sh` passes before push
