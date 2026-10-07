# Sunshine

Android app that shows where and when the sun actually shines, taking terrain occlusion into
account. Target users: hikers in the Alps. Offline-first.

**Status:** from-scratch rewrite, driven by OpenSpec. The v1 features and polish (changes #1–#12
of `docs/roadmap.md`) are archived; releasing (signed build, launcher icon, 1.0.0) is not planned
yet. `README.md` is the human-facing overview.

## Where things are

| Path | What |
|------|------|
| `openspec/config.yaml` | Project context and rules: constraints, decided stack, module boundary, quality bar. Read first. |
| `openspec/specs/` | Current truth: what the app does. Grows as changes are archived. |
| `openspec/changes/` | In-flight changes: proposal, spec deltas, design, tasks. |
| `docs/roadmap.md` | Ordered list of planned changes, their status, and legacy failure modes to avoid. |
| `investigations/` | Verified reference values (test oracles). |
| `core/` | Pure Kotlin/JVM module (no Android dependencies): domain types and computations. See "Core files" below. |
| `app/` | Android app (Compose, MapLibre). Depends on `core`. See "App packages" below. |
| `gradle/libs.versions.toml` | All dependency and plugin versions. |
| `scripts/verify-local.sh` | Local CI simulation. |
| `.claude/hooks/session-start.sh` | Web sessions only: installs the OpenSpec CLI and the Android SDK, and routes Maven Central through Google's mirror. |

### Core files (`core/src/main/kotlin/com/sunshine/core/`)

| File | What |
|------|------|
| `GeoPoint.kt` | `GeoPoint` (WGS84) and the default location. |
| `SunPosition.kt`, `SunDay.kt` | Sun azimuth and elevation, and the day's astronomical events (commons-suncalc). |
| `Terrarium.kt`, `Elevation.kt` | Terrarium decoding, `TileKey`, the compact `HeightTile`, bilinear elevation at a point, the Web-Mercator latitude limit. |
| `Horizon.kt` | Horizon tracer, no I/O; `HorizonProfile` with upper bounds where data is missing. |
| `Sunshine.kt` | Sunshine at an instant, sun periods of a day. |
| `SunShade.kt` | `MapArea` and `MAP_TILE_DP`; the sun/shade grid of the visible area by a convex-hull sweep, no I/O; cells of the shade resolution, with `Normal` 2 dp for the overlay and 8 dp for the heatmap. `StepGrid`, the grid of one step that every user reads, is a `ShadeGrid` or a `CombinedGrid` of an earlier day's grid and the grids of the parts a pan uncovered, each with the sun of its own area's centre. |
| `OfflineArea.kt` | An offline region's bounds, map and DEM tile ranges, download estimate. |
| `Lv95.kt` | WGS84 → Swiss grid LV95 by swisstopo's approximate formulas, `null` outside LV95's area of use. |

### App packages (`app/src/main/java/com/sunshine/app/`)

| Package | What |
|---------|------|
| (root) | `SunshineApp` holds the object graph (no DI framework); `MainActivity` switches the screens without a navigation library. |
| `elevation/` | `DemTiles`: Mapterhorn fetching and revalidation over the persistent store; decoding; `TileCache`, the in-memory tile cache shared by altitude, horizon and overlay. |
| `offline/` | `DemTileStore`: every fetched DEM tile kept as a file with a Room index in `OfflineDatabase`, browsed tiles limited by the `Browsed tiles limit` setting (512 MiB by default), region tiles kept. `AmbientLimit`, MapLibre's limit for browsed map tiles. `RegionDownloader` with `MapLibreRegionPart`, run by `RegionDownloadWorker` (WorkManager) with `DownloadNotification`. `RegionDeleter`. The Offline page `OfflineScreen`/`OfflineViewModel`, opened from the Offline button right of the gear. |
| `network/` | `NetworkMonitor`, `UserAgentInterceptor`, and `RateLimiter`, which paces region downloads. |
| `sunshine/` | `SunshineRepository` (horizon profiles) and `OverlayRepository` (sun-shade grids). The debug box: `DebugInfo` collects timings, tiles by source, day state and the agreement check in every build, and `debugLines` formats them; the box is drawn in `MapLabels` while a switch of the Settings page's `Debug` section is on. |
| `map/` | The map screen; see "Map screen" below. |
| `settings/` | `Settings` with its defaults and `decode`, which replaces each unreadable or disallowed value by its default. `Resolution` and `Preset`, the cell sizes and steps of both overlay modes. `SettingsStore`, a Preferences DataStore read once with `runBlocking` in `SunshineApp.onCreate`. The Settings page `SettingsScreen`/`SettingsViewModel`, opened from the gear button in `MapLabels`, and its sub-page `CustomResolutionScreen`, which stores its values when it is left. |
| `about/` | `AboutEntries.kt`: the About section with the attributions, the last section of the Settings page. The map itself shows no attributions. |
| `ui/` | `PageTopBar.kt`: the pages' top bar with the back arrow. Material 3's `TopAppBar`, `TooltipBox` and `TimePicker` are experimental and not used. |

### Map screen (`map/`)

| File | What |
|------|------|
| `MapScreen.kt`, `MapViewModel.kt` | The screen and its state. The location permission request and notices are in `MapScreen`. The view model's `cachedDay`, `newDay` and `runDay` hold the day lifecycle shared by both overlay modes. |
| `MapLibreMap.kt` | The map: tap to centre; the device's dot with its accuracy circle, grey after 30 s (MapLibre's location component); greyscale tiles under the heatmap (`mapSaturation`). |
| `MapStyle.kt`, `SunshineHttpRequest.kt` | The style JSON; MapLibre's requests, with region downloads paced by `network/RateLimiter.kt` and the region style served locally. |
| `SunPanel.kt` | The bottom panel: the header `Sun 21 Dec · 12:00` from `formatHeaderTime`, whose tap opens the platform's clock dialog; the day's sun periods as headline; the time tape with its progress percentage; the `Details` row. |
| `TimeTape.kt`, `TapeScale.kt`, `TapeStrip.kt` | The tape: drawing, drag, fling and tap, with the pure geometry of `TapeScale`. Its strip comes from `MapViewModel.tapeStrip`: the shown mode's day at the rested crosshair, else the horizon, with the source's progress. |
| `OverlayControl.kt`, `OverlayImage.kt` | The three-way toggle Off / Sun & shade / Sun hours and the status card below it with notice and legend (the day's progress is on the time tape); the overlay's rendering. |
| `DayOverlay.kt` | The overlay of every step of the selected day, computed in the background. Each mode uses the cells and steps of the shade resolution (`settings/Resolution.kt`; with `Normal`, `Sun & shade` uses 2 dp cells every 5 minutes, and `Sun hours` its own day of 8 dp cells every 10 minutes). Only the shown mode's day runs. |
| `DayCache.kt`, `Uncovered.kt` | Computed days by area, date, cell size and step, least recently used dropped. `reusable` picks the earlier day a new day reuses after a camera move: same date, cell size and step, same zoom or up to one level out, the largest covered share and at least a quarter. A day with such a base computes, at each daytime step the base has, only the rectangles `uncovered` and combines them with the base's grid (`Reused <p> %` in the debug box). |
| `SunHours.kt`, `HeatmapBands.kt` | The `Sun hours` heatmap: a complete day's sun and unknown steps counted per cell (8 dp with `Normal`) and drawn at one pixel per dp, in 30-minute colour bands. |
| `LocationButton.kt` | The location button's states idle / waiting / ready as pure functions. |
| `FirstRunHint.kt`, `SunLine.kt`, `MapOverlay.kt` | The first-run hint; the line toward the sun; the crosshair, and the labels, buttons and debug box over the map (`MapLabels`). |
| `TimeSelection.kt`, `SunFormat.kt`, `CoordinateFormat.kt` | Time steps of a day, and the panel's and crosshair's text formats. |

## Workflow: no code without an approved change

1. `/opsx:explore` (optional): think an idea through.
2. `/opsx:propose <change-name>`: creates `openspec/changes/<change-name>/` with proposal,
   spec deltas, design and tasks.
3. Decisions that shape the change are asked while proposing. The user reviews the whole change
   before it is applied.
4. `/opsx:apply`: implement the tasks in order; each task has its own verification step.
5. `/opsx:verify`: check the implementation against the change.
6. `/opsx:archive`: merge the spec deltas into `openspec/specs/`; update `docs/roadmap.md`.

Useful CLI: `openspec list`, `openspec show <name>`, `openspec validate --all --strict`
(also run by CI).

## Build and verify

- `./scripts/verify-local.sh`: the same checks as CI (OpenSpec validation, ktlint, Android lint,
  unit tests, debug APK). Run before every push.
- `./scripts/verify-local.sh --quick`: ktlint only.
- `./gradlew :core:test` / `./gradlew :app:testDebugUnitTest --tests "*MapViewModel*"`: unit tests
  (JUnit 6).
- `./gradlew ktlintFormat`: fix formatting.
- `./gradlew assembleDebug`: debug APK at `app/build/outputs/apk/debug/app-debug.apk`.
- CI (`.github/workflows/ci.yml`): `specs` job (OpenSpec validation) and `build` job (ktlint,
  Android lint, unit tests, debug APK uploaded as artifact `app-debug-apk`).
- Web sessions: the SessionStart hook installs the SDK at `~/android-sdk`, writes
  `local.properties`, and adds a Gradle init script that fetches Maven Central artifacts from
  Google's mirror (Maven Central answers HTTP 429 there). Gradle runs directly; no proxy wrapper
  is needed.

## Conventions

- Conventional commits: `type(scope): description`. Types: feat, fix, refactor, test, docs,
  chore, style, ci.
- Stage files individually (never `git add .`); review the diff before committing.
- ktlint with zero issues and Android lint clean; no suppressions without a written
  justification. detekt is deferred until detekt 2.0 is stable.
- Numeric behavior is tested in `core` against reference values with explicit tolerances.
