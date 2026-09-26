# Proposal

## Why

The first implementation was discarded, so there is currently no app at all. Every planned feature
draws on a map and on the agreed `core`/`app` split, and the two biggest unknowns of the new stack
are the map library (MapLibre Native replaces the archived osmdroid) and the toolchain (AGP 9,
Kotlin 2.4). A small walking skeleton that proves both, with every CI quality gate running from
day one, removes that risk before any sun or terrain logic is written.

Roadmap: implements entry #1, `add-app-shell`, of `docs/roadmap.md`.

## What Changes

- New Gradle multi-module build with current stable versions: `core` (pure Kotlin/JVM) and `app`
  (Android, Jetpack Compose). Root Gradle files, version catalog and wrapper are rewritten.
- `core`: a validated `GeoPoint` value type and the default location. It is the first type shared
  by all later features.
- `app`: single-activity Compose app with a full-screen MapLibre map showing OpenTopoMap raster
  tiles, pan and zoom, a default viewport on the Swiss Alps, a centre crosshair that marks the
  selected location with its coordinates, and visible attribution.
- Tile requests identify the app with a descriptive User-Agent.
- CI runs the full pipeline for both modules: the temporary Gradle gate is removed, and
  ktlint, detekt, Android lint, unit tests and the APK build all run. `scripts/verify-local.sh`
  is adapted to the module layout.
- The SessionStart hook's Android SDK packages are aligned with the chosen `compileSdk`.

## Capabilities

### New Capabilities

- `map-view`: the interactive topographic base map, its default viewport, the selected-location
  crosshair and the map attribution.

### Modified Capabilities

None. There are no existing specs.

## Non-goals

- Sun position, time selection, elevation, horizon or visibility features (roadmap #2 to #7).
- Offline map download or any tile caching beyond the map library's defaults (roadmap #6).
- GPS / current location, remembering the last viewport between launches, a settings screen.
- Visual design beyond Material 3 defaults (custom theme, launcher icon artwork).

## Impact

- **Code:** new `core/` and `app/` modules; rewritten `settings.gradle.kts`, `build.gradle.kts`,
  `gradle/libs.versions.toml` and Gradle wrapper.
- **Dependencies:** MapLibre Native Android, Jetpack Compose (Material 3), JUnit for `core` tests.
  Koin, Room and the other stack members are added later, by the change that first needs them.
- **Network:** `INTERNET` permission; tile requests to `tile.opentopomap.org`.
- **Tooling:** `.github/workflows/ci.yml`, `scripts/verify-local.sh`,
  `.claude/hooks/session-start.sh` (SDK package versions).
