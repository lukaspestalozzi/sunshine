# Sunshine

Android app that shows where and when the sun actually shines, taking terrain occlusion into
account. Target users: hikers in the Alps. Offline-first.

**Status:** from-scratch rewrite, driven by OpenSpec. The first change, `add-app-shell`, provides
the app skeleton: the map screen and the build (see `docs/roadmap.md`).

## Where things are

| Path | What |
|------|------|
| `openspec/config.yaml` | Project context and rules: constraints, decided stack, module boundary, quality bar. Read first. |
| `openspec/specs/` | Current truth: what the app does. Grows as changes are archived. |
| `openspec/changes/` | In-flight changes: proposal, spec deltas, design, tasks. |
| `docs/roadmap.md` | Ordered list of planned changes, their status, and legacy failure modes to avoid. |
| `investigations/` | Verified reference values (test oracles). |
| `core/` | Pure Kotlin/JVM module (no Android dependencies): domain types and computations, e.g. `GeoPoint`; `Horizon.kt` (horizon tracer, no I/O), `Sunshine.kt` (sunshine at an instant, sun periods of a day) and `SunShade.kt` (sun/shade grid of the visible area by a convex-hull sweep, no I/O; cells of the shade resolution, with `Normal` 2 dp for the overlay and 8 dp for the heatmap; `StepGrid`, the grid of one step that every user reads, is a `ShadeGrid` or a `CombinedGrid` of an earlier day's grid and the grids of the parts a pan uncovered, each with the sun of its own area's centre) and `OfflineArea.kt` (an offline region's bounds, map and DEM tile ranges, download estimate) and `Lv95.kt` (WGS84 → Swiss grid LV95 by swisstopo's approximate formulas, `null` outside LV95's area of use). |
| `app/` | Android app (Compose, MapLibre): map screen, ViewModel, network code, elevation tiles (`elevation/`: `DemTiles`, Mapterhorn fetching and revalidation over the persistent store, decoding, and `TileCache`, the in-memory tile cache shared by altitude, horizon and overlay), the offline storage and regions (`offline/`: `DemTileStore`, every fetched DEM tile kept as a file with a Room index in `OfflineDatabase`, browsed tiles limited by the `Browsed tiles limit` setting (512 MiB by default), region tiles kept; `AmbientLimit`, MapLibre's limit for browsed map tiles; `RegionDownloader` with `MapLibreRegionPart` run by `RegionDownloadWorker` (WorkManager) with `DownloadNotification`; `RegionDeleter`; the Offline page `OfflineScreen`/`OfflineViewModel`, opened from the Offline button right of the gear), MapLibre's requests (`map/SunshineHttpRequest.kt`: region downloads paced by `network/RateLimiter.kt`, the region style served locally; the style JSON in `map/MapStyle.kt`), horizon profiles and sun-shade grids (`sunshine/`: `SunshineRepository`, `OverlayRepository`), the overlay's rendering (`map/OverlayImage.kt`) and controls (`map/OverlayControl.kt`: the three-way toggle Off / Sun & shade / Sun hours and the status card below it with notice and legend; the day's progress is on the time tape), the bottom panel (`map/SunPanel.kt`: the header `Sun 21 Dec · 12:00` from `formatHeaderTime`, whose tap opens the platform's clock dialog, the day's sun periods as headline, the time tape with its progress percentage and the `Details` row; `map/TimeTape.kt` draws the tape and handles drag, fling and tap with the pure geometry of `map/TapeScale.kt`; its strip comes from `MapViewModel.tapeStrip` via `map/TapeStrip.kt`: the shown mode's day at the rested crosshair, else the horizon, with the source's progress), the first-run hint (`map/FirstRunHint.kt`), tap to centre (in `map/MapLibreMap.kt`), the pages' top bar with the back arrow (`ui/PageTopBar.kt`; Material 3's `TopAppBar`, `TooltipBox` and `TimePicker` are experimental and not used), the About section with the attributions (`about/AboutEntries.kt`, the last section of the Settings page `settings/SettingsScreen.kt`, opened from the gear button in `MapLabels`; the map itself shows no attributions), the device's position (`map/LocationButton.kt`: the location button's states idle / waiting / ready as pure functions; the dot with its accuracy circle, grey after 30 s, is MapLibre's location component in `map/MapLibreMap.kt`; the permission request and the notices are in `MapScreen`), and `DayOverlay` (`map/DayOverlay.kt`: the overlay of every step of the selected day, computed in the background; each mode uses the cells and steps of the shade resolution (`settings/Resolution.kt`; with `Normal`, `Sun & shade` 2 dp cells every 5 minutes, `Sun hours` its own day of 8 dp cells every 10 minutes), and only the shown mode's day runs) with `DayCache` (`map/DayCache.kt`: computed days by area, date, cell size and step, least recently used dropped; `reusable` picks the earlier day a new day reuses after a camera move: same date, cell size and step, same zoom or up to one level out, the largest covered share and at least a quarter). A day with such a base computes, at each daytime step the base has, only the rectangles `uncovered` (`map/Uncovered.kt`) and combines them with the base's grid (`Reused <p> %` in the debug box). The overlay's second mode, `Sun hours`, is the heatmap of a day: `map/SunHours.kt` counts a complete day's sun and unknown steps per cell (8 dp with `Normal`) and draws them at one pixel per dp, `map/HeatmapBands.kt` holds its 30-minute colour bands. Under the heatmap, the map tiles are greyscale (`mapSaturation` in `map/MapLibreMap.kt`). The settings (`settings/`: `Settings` with its defaults and `decode`, which replaces each unreadable or disallowed value by its default; `Resolution` and `Preset`, the cell sizes and steps of both overlay modes; `SettingsStore`, a Preferences DataStore read once with `runBlocking` in `SunshineApp.onCreate`; the Settings page `SettingsScreen`/`SettingsViewModel`, opened from the gear button, and its sub-page `CustomResolutionScreen`, which stores its values when it is left). The debug box (`sunshine/DebugInfo.kt`: `DebugInfo` collects timings, tiles by source, day state and the agreement check in every build, `debugLines` formats them; the box is drawn in `MapLabels` while a switch of the Settings page's `Debug` section is on). Depends on `core`. |
| `gradle/libs.versions.toml` | All dependency and plugin versions. |
| `scripts/verify-local.sh` | Local CI simulation. |
| `.claude/hooks/session-start.sh` | Web sessions only: installs the OpenSpec CLI and the Android SDK, and routes Maven Central through Google's mirror. |

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

- `./scripts/verify-local.sh`: full CI simulation (ktlint, Android lint, unit tests, debug APK).
  Run before every push.
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
