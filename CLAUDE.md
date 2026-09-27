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
| `docs/legacy-design.md` | Design of the discarded first implementation. Source material only. |
| `investigations/` | Verified reference values (test oracles). |
| `core/` | Pure Kotlin/JVM module (no Android dependencies): domain types and computations, e.g. `GeoPoint`; `Horizon.kt` (horizon tracer, no I/O), `Sunshine.kt` (sunshine at an instant, sun periods of a day) and `SunShade.kt` (sun/shade grid of the visible area by a convex-hull sweep, no I/O). |
| `app/` | Android app (Compose, MapLibre): map screen, ViewModel, network code, elevation tiles (`elevation/`: Mapterhorn fetching, decoding, and `TileCache`, the tile cache shared by altitude, horizon and overlay), horizon profiles and sun-shade grids (`sunshine/`: `SunshineRepository`, `OverlayRepository`), the overlay's rendering and controls (`map/OverlayImage.kt`, `map/OverlayControl.kt`), and `DayOverlay` (`map/DayOverlay.kt`: the overlay of every slider step of the selected day, computed in the background). Depends on `core`. |
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
