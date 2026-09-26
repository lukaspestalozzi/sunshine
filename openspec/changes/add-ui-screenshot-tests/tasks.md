# Tasks

## 1. Test dependencies and build setup

- [x] 1.1 Add to `gradle/libs.versions.toml` (latest stable at apply time: Robolectric 4.17, Roborazzi 1.75.0 or newer): `robolectric`, `roborazzi`, `roborazzi-compose`, `roborazzi-junit-rule`, `junit4` (4.13.2), `junit-vintage-engine` (from the JUnit BOM), and the Compose BOM's `ui-test-junit4` and `ui-test-manifest`. In `app/build.gradle.kts`: add them as `testImplementation`, except `ui-test-manifest` as `debugImplementation` and the vintage engine as `testRuntimeOnly`. Set `testOptions.unitTests.isIncludeAndroidResources = true` and the test system property `roborazzi.test.record=true` (design D2, D4, D5). Verify: `./gradlew :app:testDebugUnitTest` passes with the same test count as before (62 app tests), and `./gradlew :app:dependencies --configuration releaseRuntimeClasspath` is unchanged from before the task.
- [x] 1.2 Check the vintage engine's status in JUnit 6.1.3. Verify: the test run of 1.1 shows no error from the vintage engine. If it logs a deprecation warning, note it in the PR; if it refuses to run, stop and ask.
- [ ] 1.3 Before the first Robolectric test (2.2) runs, note whether `~/.m2/repository/org/robolectric/android-all-instrumented/` already exists. After 2.2, record where the SDK 37 runtime jar came from (Robolectric log line or `~/.m2` timestamp) (design Risks). Verify: the jar `android-all-instrumented-17-robolectric-15733970-i7.jar` (or the version Robolectric 4.17+ asks for) exists under `~/.m2/repository`. If the download failed with HTTP 429, stop and ask before adding the repository-URL forwarding.

## 2. Stateless map screen and first screenshot

- [x] 2.1 Split `MapScreen` into `MapScreen` (ViewModel, MapLibre) and `MapScreenContent(…, map: @Composable (Modifier) -> Unit)`, and add the test tags `sunPanel`, `crosshair` and `attribution` (design D3). Verify: `./gradlew ktlintCheck :app:lintDebug :app:assembleDebug :app:testDebugUnitTest` passes, and the diff moves code without changing what is drawn (read the diff: same composables, same order, same modifiers apart from the tags).
- [ ] 2.2 Write `MapScreenScreenshotTest` with one case first: portrait, sun above the horizon (Interlaken, 2025-12-21 12:00 Europe/Zurich), map slot = grey `Box`, capture `map_portrait_day.png` (design D4, D5). Verify: `./gradlew :app:testDebugUnitTest --tests "*MapScreenScreenshotTest*"` passes, `app/build/outputs/roborazzi/map_portrait_day.png` exists, and on inspection it shows the panel (with the selected time `2025-12-21 12:00 UTC+1`, `173° S`, `19.7°`), crosshair, coordinates, attribution and a solid sun line pointing almost straight down.

## 3. Layout assertions and the full matrix

- [ ] 3.1 Add the layout assertion to the portrait-day case: the `sunPanel` bounds intersect neither the `crosshair` nor the `attribution` bounds (design D5). Verify: `./gradlew :app:testDebugUnitTest --tests "*MapScreenScreenshotTest*"` passes. (That the assertion can fail is shown in 3.2, where a wider panel reaches the crosshair in landscape.)
- [ ] 3.2 Add the other three cases: portrait night (02:00), landscape day and landscape night, each with the assertion and its screenshot `map_<portrait|landscape>_<day|night>.png`. Verify: the test class passes with 4 cases; the 4 PNGs exist and on inspection the night images show a dashed line towards the upper right (NE). Then temporarily set the panel to `widthIn(max = 600.dp)`: the landscape cases fail on the crosshair overlap; revert and the class passes again.

## 4. CI and documentation

- [ ] 4.1 In `.github/workflows/ci.yml`, after the unit tests, upload `app/build/outputs/roborazzi/` as artifact `ui-screenshots` with `if: always()` (design D6). Verify: `python3 -c "import yaml; yaml.safe_load(open('.github/workflows/ci.yml'))"` succeeds and `grep -n ui-screenshots .github/workflows/ci.yml` shows the step.
- [ ] 4.2 In `CLAUDE.md` "Build and verify": say that `./gradlew :app:testDebugUnitTest` also renders the map screen with Robolectric, that the screenshots go to `app/build/outputs/roborazzi/` (CI artifact `ui-screenshots`), and that layout assertions gate CI but pixels do not. Verify: the section names the path, the artifact and the test command, and the command runs as written.

## 5. Integration checks

- [ ] 5.1 Run the full local CI simulation. Verify: `./scripts/verify-local.sh` passes every step, and `openspec validate --all --strict` passes.
- [ ] 5.2 Push and check CI. Verify: the `specs` and `build` jobs are green; the run has the artifacts `app-debug-apk` and `ui-screenshots`, the latter with 4 PNGs; the `build` job's duration is noted in the PR.
- [ ] 5.3 Send the four screenshots to the user for review. Expected: the user confirms they show the screen as expected, or names what to change (a change request goes into a follow-up, not this change's gate).
