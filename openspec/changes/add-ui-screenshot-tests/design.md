# Design

## Context

See proposal.md (Why). Facts checked while drafting (2026-09-26):

- **Container:** no `/dev/kvm`, no `vmx`/`svm` CPU flags (Docker on a virtual machine), x86_64,
  4 cores, 15 GB RAM. No emulator package or system image in `~/android-sdk`.
- **App:** `MapScreen` builds its `MapViewModel` through a factory, collects its state and draws
  `MapLibreMap`, `SunLine`, `Crosshair` and `MapLabels`, with the `SunPanel` in its bottom slot.
  `SunshineApp.onCreate` calls `MapLibre.getInstance` (native code). Tests run on the JUnit
  Platform with Jupiter (JUnit 6.1.3).
- **Versions:**
  - Robolectric 4.17 (2026-09-10) supports SDK 37 (Android 17, runtime jar
    `android-all-instrumented:17-robolectric-15733970-i7`, 239 MB, also on Google's Maven Central
    mirror).
  - Roborazzi 1.75.0 (2026-09-21).
  - JUnit vintage engine 6.1.3 exists.
- **Network:** from this container, `repo1.maven.org` answered 200 today (it answered 429 for
  hours in September; see add-app-shell D11).

## Goals / Non-Goals

**Goals:**
- Render the real map-screen composables (all but the native map) on the JVM, in this container
  and in CI.
- Assert, not just picture, that the sun panel leaves the crosshair and the attribution free.
- Make the screenshots visible without extra steps: written by every unit-test run, uploaded by CI.

**Non-Goals:**
- Instrumented tests, golden images, more configurations (see proposal Non-goals).
- Testing the ViewModel through the UI. The ViewModel keeps its JVM tests.

## Decisions

### D1. Robolectric + Roborazzi
Robolectric 4.17 runs the real Android framework on the JVM. With native graphics
(`@GraphicsMode(NATIVE)`) it renders Compose to real pixels, and Roborazzi 1.75.0 saves them as
PNG. Compose UI test (`createComposeRule`, from the Compose BOM) gives semantics queries and node
bounds for the layout assertions.
*Alternatives:*
- Paparazzi (layoutlib): no real framework, no activities, awkward for the bounds assertions.
- An emulator: impossible here without KVM. In CI (GitHub runners have KVM) it's possible, but it
  was left for later.

### D2. JUnit 4 tests through the vintage engine
Robolectric's runner is JUnit 4. `junit:junit` 4.13.2 and `junit-vintage-engine` (JUnit BOM,
6.1.3) run those tests on the same JUnit Platform, in the same `:app:testDebugUnitTest` task, next
to the Jupiter tests. No separate task or CI step is needed.
*Alternative:* a community JUnit 5 extension for Robolectric. Unofficial; rejected.

### D3. Split `MapScreen` into a stateful wrapper and a stateless content
`MapScreenContent(camera, isOffline, selectedTime, sun, onDateSelected, onSliderMoved,
onNowClicked, map: @Composable (Modifier) -> Unit)` draws everything `MapScreen` draws today, with
the map as a slot. `MapScreen` keeps the ViewModel and passes `MapLibreMap` into the slot. Tests
pass a plain grey `Box` (`#E0E0E0`, the colour missing tiles show) and state built directly.
The panel, crosshair and attribution get `Modifier.testTag`s (`sunPanel`, `crosshair`,
`attribution`) for the assertions. What the app draws does not change.
*Alternative:* render `MapScreen` with a fake ViewModel and a mocked MapLibre. That needs a mock
framework and still runs the native map constructor. Rejected.

### D4. Test runtime configuration
- `@Config(sdk = [37], application = Application::class)`: a plain `Application`, so
  `SunshineApp`'s MapLibre initialisation (native) does not run.
- Configurations by Robolectric qualifiers (user decision: two):
  `w411dp-h891dp-port-xxhdpi` and `w891dp-h411dp-land-xxhdpi`.
- States (fixed, independent of the test machine's clock and zone):
  - Interlaken 46.6863° N, 7.8632° E, 2025-12-21 12:00 Europe/Zurich: sun above the horizon.
  - Same place and day at 02:00: sun below the horizon, dashed line.
  - `SunInfo` is computed with the real `sunPosition`/`sunDay`.
- `testOptions.unitTests.isIncludeAndroidResources = true`, needed for resources and the theme
  under Robolectric.

### D5. Layout assertions gate, screenshots are for review (user decision)
For each of the 2 × 2 cases, the test reads the bounds in the root of `sunPanel`, `crosshair` and
`attribution`. It asserts that the panel's rectangle intersects neither of the other two. A
failure fails `:app:testDebugUnitTest`, and so CI.
Each case also captures the root as `app/build/outputs/roborazzi/map_<portrait|landscape>_<day|night>.png`.
Unit-test tasks get the system property `roborazzi.test.record=true`, so every run writes the
images. No image is compared.
*Alternatives:* golden images with a pixel-diff gate; assertions without screenshots.

### D6. Where screenshots are seen
- **CI:** a new upload step publishes `app/build/outputs/roborazzi/` as artifact `ui-screenshots`
  with `if: always()`, so a failing layout assertion still comes with its pictures.
- **Cloud sessions:** Claude opens the PNGs directly and sends them to the user when a UI change is
  made. `CLAUDE.md` names the location.

### Performance budget
No computation is triggered by user interaction in this change. Test-time budget: the four
screenshot cases add at most 60 s to `:app:testDebugUnitTest` once Robolectric's runtime jar is
cached. The first run downloads that jar (239 MB) into `~/.m2/repository`.

### Verification strategy
- The existing unit tests still pass after the vintage engine is added and after the refactor.
- The new test passes in both configurations and states; each PNG exists and has been looked at.
- A temporary mutation (panel `widthIn(max = 600.dp)`) makes the landscape assertion fail. This
  proves the assertion can fail.
- CI green with artifact `ui-screenshots` holding 4 PNGs.

## Risks / Trade-offs

- [Robolectric downloads its runtime jar from Maven Central itself, bypassing Gradle and the cloud
  sessions' mirror init script. If Maven Central answers 429 again, the tests fail in cloud
  sessions.] → Task 1.3 records where the jar came from. If the download fails, stop and ask.
  The prepared fix: forward a Gradle property to the `robolectric.dependency.repo.url` system
  property, set to the mirror by the SessionStart hook. It isn't built unless needed.
- [Robolectric's rendering is not a device's: fonts, anti-aliasing, and window insets of zero, so
  no status or navigation bar.] → The screenshots and assertions show the layout logic, not
  pixel-exact device output. The on-device check remains for final visual approval. Zero insets
  put the panel slightly lower than on a device with a navigation bar; the portrait and landscape
  margins to the crosshair are well above a bar's height (checked when the assertions first run).
- [The JUnit vintage engine may be marked deprecated in JUnit 6] → Accepted if it only warns;
  Robolectric has no official non-JUnit-4 runner. If it fails to run, stop and ask.
- [CI downloads the 239 MB runtime jar on every run, since only the Gradle cache is kept] →
  Accepted (seconds on GitHub runners); a cache step can be added later if it proves slow.
- [Test classpath grows (Robolectric, Compose UI test)] → Test-only. Only `ui-test-manifest`
  reaches the debug APK, adding one test activity to its manifest; release is unaffected.

## Migration Plan

No data. Rollback: revert the change's commits. The split of `MapScreen` is behavior-neutral, so
reverting it alone is also safe.
