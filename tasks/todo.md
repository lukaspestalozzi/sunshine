# Sunshine Heatmap Feature

## Status: COMPLETE

All implementation steps finished. Feature committed and pushed to `claude/summarize-project-state-Wdjnk`.

## Summary

Show total hours of sun exposure across a grid for a given day, rendered as a color gradient overlay on the map. Users toggle heatmap mode via a Layers icon in the top bar.

## What was built

### Files created
- `domain/model/SunExposureGrid.kt` — Data class: bounds, resolution, date, points (hours per GeoPoint), maxExposure

### Files modified
| File | Changes |
|------|---------|
| `CalculateSunVisibilityUseCase.kt` | Added `calculateSunExposureGrid()` — scans sunrise→sunset in 30min steps per grid point, parallel with semaphore, max 200 points |
| `MapUiState.kt` | Added `isHeatmapMode`, `sunExposureGrid`, `isLoadingHeatmap`, `showHeatmapOverlay`; updated `showGridOverlay` to exclude heatmap mode |
| `MapViewModel.kt` | Added `onToggleHeatmap()`, `scheduleHeatmapUpdate()` (1s debounce), `updateHeatmap()` with coarser resolution; triggers on date/location/zoom change |
| `OsmMapView.kt` | Added `sunExposureGrid` param, `SunExposureOverlay` class, `hoursToColor()` gradient (blue→cyan→green→yellow→red at 60% alpha) |
| `MapScreen.kt` | Layers toggle button in top bar, gradient legend bar, loading indicator |
| `strings.xml` | 4 new strings: toggle_heatmap, loading_heatmap, heatmap_legend_low, heatmap_legend_high |
| `CalculateSunVisibilityUseCaseTest.kt` | 4 new tests: sunlit flat terrain, no sunrise, blocking terrain, date match |
| `MapViewModelTest.kt` | 3 new tests: toggle on, toggle off + clear, calculation at sufficient zoom |

## Design decisions
- **30-minute time steps** — balance between accuracy and performance
- **Max 200 grid points** (vs 500 for real-time grid) — each point scans ~30 time steps
- **Coarser resolution** per zoom level than real-time grid (0.001/0.002/0.004 vs 0.0005/0.001/0.002)
- **Mutually exclusive** with real-time visibility grid overlay
- **1s debounce** on heatmap updates (vs 500ms for real-time grid)
- **Explicit toggle** — heatmap only calculates when user enables it
- Elevation data heavily cached after first time step — subsequent steps are fast

## Verification
- [x] Code style reviewed against detekt config (line length ≤140, function counts within limits)
- [x] `@Suppress` annotations added where needed (LongMethod, TooManyFunctions, MagicNumber)
- [x] 7 new unit tests written
- [x] Committed and pushed
