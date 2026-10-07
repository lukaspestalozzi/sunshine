# Sunshine

An Android app that shows where and when the sun actually shines, with mountains and valleys
taken into account. It is built for hikers planning trips in the Alps, and it works offline.

Most sun apps give the astronomical sunrise, which assumes a flat horizon. In a valley the sun
may appear hours later. Sunshine traces the real terrain horizon from elevation data and tells you
when the sun clears it.

## Features

- **Sun at a point.** Move the crosshair over a topographic map to get the day's sun periods with
  terrain (e.g. `Sunshine 10:09–14:51, 15:11–15:52`), the sun's azimuth and elevation, sunrise,
  sunset, twilight, day length and altitude.
- **Time tape.** Drag a 24-hour tape to choose the time. Its strip shows sun, terrain shade, night
  and unknown at the crosshair over the whole day.
- **Sun & shade overlay.** Sun and terrain shade across the whole visible area at the selected
  time, from map zoom 11. The whole day is computed in the background, so scrubbing through it is
  instant.
- **Sun hours heatmap.** Hours of direct sun per place for the selected day.
- **Offline maps.** Every map and elevation tile the app fetches is kept. You can download the
  visible area as a region, and then everything works offline there.
- **My location.** The device's position and accuracy circle, without Google Play Services.
- **Settings.** Coordinate format (decimal, DMS or Swiss LV95), start view, keep screen on,
  overlay opacity and resolution, storage limits, and a debug box with timings.

Unknown is never guessed. Where elevation data is missing, the app says "unknown" instead of
assuming flat ground.

## Status

Version 0.1.0, debug builds only. The v1 feature set is done (`docs/roadmap.md`, changes #1–#12).
A signed release build, a launcher icon and version 1.0.0 come next and are not planned yet.

## Building

Requirements: JDK 17 or newer, and the Android SDK with platform 37 (`ANDROID_HOME` set, or
`sdk.dir` in `local.properties`). The app runs on Android 10 (API 29) and newer.

```sh
./gradlew assembleDebug                         # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew :core:test :app:testDebugUnitTest     # unit tests (JUnit 6, plain JVM)
./gradlew ktlintFormat                          # fix formatting
./scripts/verify-local.sh                       # everything CI checks; run before pushing
```

`verify-local.sh` also validates the OpenSpec specs and needs the OpenSpec CLI
(`npm install -g @fission-ai/openspec`). CI (`.github/workflows/ci.yml`) runs the same checks and
uploads the debug APK as the artifact `app-debug-apk`.

## How it is built

| Module | What |
|--------|------|
| `core` | Pure Kotlin/JVM, no Android: sun position (commons-suncalc), DEM tile decoding and interpolation, the horizon tracer, terrain sunshine and sun periods, the sun-shade sweep of a visible area, offline region geometry, Swiss LV95. Tested against reference values in `investigations/`. |
| `app` | Android: Jetpack Compose (Material 3), MVVM with `StateFlow`, MapLibre Native with OpenTopoMap tiles, OkHttp, Room, WorkManager and DataStore. Dependencies are wired by hand in `SunshineApp`. |

All versions are in `gradle/libs.versions.toml`. `CLAUDE.md` maps the code file by file.

The project is developed spec-first with [OpenSpec](https://github.com/Fission-AI/OpenSpec):

| Path | What |
|------|------|
| `openspec/specs/` | What the app does, one spec per capability. |
| `openspec/changes/` | Changes in progress; finished ones are under `archive/`, with their proposal, design decisions and tasks. |
| `openspec/config.yaml` | Project context: constraints, stack and quality bar. |
| `docs/roadmap.md` | Planned changes and their status. |
| `investigations/` | Spikes and verified reference values used as test oracles. |

## Data sources

- Map: © [OpenStreetMap](https://www.openstreetmap.org/copyright) contributors, SRTM | Map style:
  © [OpenTopoMap](https://opentopomap.org) (CC-BY-SA).
- Elevation: © [Mapterhorn and its sources](https://mapterhorn.com/attribution/). In the Alps
  these are the national terrain models (swisstopo, BEV, Bavaria, IGN, ARSO, Italian regions and
  provinces), with Copernicus GLO-30 elsewhere.
- Icons: Material Symbols (Apache License 2.0).

The app identifies itself to the tile servers with its own User-Agent. Region downloads are
limited to 5 requests per second per server.

## Licence

No licence has been chosen yet, so all rights are reserved.
