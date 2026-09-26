# Proposal

## Why

The development container and CI have no Android emulator. There is no KVM here, and none can be
installed. So nothing in the automated checks looks at the rendered UI. For `add-sun-position`,
layout requirements such as "the sun panel does not cover the crosshair or the attribution" rest
entirely on a manual device check (task 6.3). JVM-rendered Compose screenshots close most of that
gap. They run in this container and in CI, they give a picture of the screen for every change,
and they let layout rules be asserted automatically.

Roadmap: off-roadmap. This is a tooling change that supports every UI change on the roadmap,
starting with #2 `add-sun-position`, whose open PR it lands in (user decision).

## What Changes

- `app` test setup: Robolectric with native graphics, Compose UI test and Roborazzi, running inside
  the existing `:app:testDebugUnitTest` task on the JUnit Platform (through the JUnit vintage
  engine, since Robolectric's runner is JUnit 4).
- `MapScreen` is split into a stateful wrapper (ViewModel, MapLibre) and a stateless content
  composable that takes the map as a slot, so tests can render the whole screen with a
  placeholder instead of the native map. No visible behavior changes.
- A screenshot test class renders the map screen for two configurations: a medium phone in
  portrait (411 × 891 dp) and in landscape (891 × 411 dp). Two states are shown: sun above the
  horizon (Interlaken, 2025-12-21 12:00) and below it (02:00).
- Layout assertions gate CI. In each configuration, the sun panel's bounds overlap neither the
  crosshair nor the attribution.
- Screenshots are written on every test run and uploaded by CI as the artifact
  `ui-screenshots`, for review only. Pixel differences do not fail the build.
- `CLAUDE.md` documents where the screenshots are and how to look at them.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None. The change adds tests for existing requirements (`sun-position`: "Sun information panel";
`map-view`: "Map attribution") and a behavior-neutral refactor. It sets `skip_specs: true`.

## Non-goals

- A real emulator, locally or in CI (instrumented tests). MapLibre rendering and map gestures
  stay manual checks.
- Golden-image comparison (pixel-diff gate) (user decision: screenshots are for review only).
- Small-phone and tablet configurations (user decision: two configurations).
- Screenshots of dialogs (date picker) or of the real map tiles.

## Impact

- **Code:** `MapScreen.kt` is split into `MapScreen` and `MapScreenContent`; there's a new test
  class in `app/src/test`.
- **Dependencies (test only):** Robolectric, Compose `ui-test-junit4` (and `ui-test-manifest` in
  debug), Roborazzi (Compose and JUnit rule), JUnit 4 and the JUnit vintage engine. There is no
  change to the APK's runtime dependencies. `ui-test-manifest` only adds an activity entry to the
  debug manifest.
- **Build:** `testOptions.unitTests.isIncludeAndroidResources = true`. On its first run
  Robolectric downloads the Android 17 runtime jar (239 MB); later runs use the cache.
- **CI:** `.github/workflows/ci.yml` uploads `app/build/outputs/roborazzi/` as `ui-screenshots`.
- **Docs:** `CLAUDE.md` (build and verify section).
