# Tasks

## 1. Build skeleton

- [x] 1.1 Update the Gradle wrapper to 9.8.0 (or a newer stable, if one exists at apply time). Verify: `./gradlew --version` prints that Gradle version.
- [x] 1.2 Make cloud sessions fetch Maven Central artifacts from Google's mirror (design D11): extend `.claude/hooks/session-start.sh` to write a Gradle init script to `~/.gradle/init.d/` that rewrites Maven Central repository URLs to `https://maven-central.storage-download.googleapis.com/maven2/`. Project build files stay on `mavenCentral()`. Verify: the hook exits 0 and writes the init script; after 1.3, `./gradlew help --refresh-dependencies --info` shows downloads from the mirror and no request to `repo.maven.apache.org`.
- [x] 1.3 Rewrite `gradle/libs.versions.toml`, `settings.gradle.kts` (include `:core`, `:app`) and the root `build.gradle.kts` without detekt, using the versions in design D2 after re-checking for newer stable releases. Create `core/build.gradle.kts` (Kotlin JVM, toolchain 17, JUnit 6) and a minimal `app/build.gradle.kts` (AGP 9.4.1, compileSdk/targetSdk 37, minSdk 29, `com.sunshine.app`, versionName 0.1.0, Compose), plus a manifest (with `INTERNET` and `ACCESS_NETWORK_STATE`) and an empty `MainActivity`. Verify: `./gradlew :app:assembleDebug` succeeds with no AGP warning about compileSdk 37, and `./gradlew buildEnvironment` shows kotlin-gradle-plugin 2.4.20. If AGP rejects 37 or another Kotlin version wins, stop and ask.
- [x] 1.4 Apply the ktlint Gradle plugin to both modules. Verify: `./gradlew ktlintCheck` passes.

## 2. core: GeoPoint

- [x] 2.1 Write `GeoPointTest` first: ±90 and ±180 are accepted; NaN, ±Infinity, 90.0001, -90.0001, 180.0001 and -180.0001 are rejected with `IllegalArgumentException`; `DEFAULT_LOCATION` is 46.8182, 8.2275. Then implement `GeoPoint` (design D6). Verify: `./gradlew :core:test` passes, and `core` has no Android dependency (`./gradlew :core:dependencies --configuration runtimeClasspath` lists no `androidx`/`com.android` artifact).

## 3. app: map screen

- [x] 3.1 Write tests for `formatCoordinates` covering the three spec scenarios (Northern/Eastern, Southern/Western, de-CH locale) and one HALF_UP rounding case (46.68635 → `46.6864° N`). Then implement it (design D6). Verify: `./gradlew :app:testDebugUnitTest --tests "*CoordinateFormat*"` passes.
- [x] 3.2 Implement `NetworkMonitor` and `MapViewModel` (design D5, D8) with `MapViewModelTest`: the default camera is `DEFAULT_LOCATION` at zoom 10; a camera state saved in `SavedStateHandle` is restored; a monitor emitting "no internet" gives `isOffline = true`, and emitting "internet" gives `false`. Verify: `./gradlew :app:testDebugUnitTest --tests "*MapViewModel*"` passes.
- [x] 3.3 Implement the MapLibre map composable (design D3, D4): `AndroidView` + `MapView` with lifecycle forwarding; a style with a background layer and the OpenTopoMap raster source (`maxzoom` 17); camera zoom limits 5 to 17; MapLibre logo and attribution button disabled; camera-move events feed the ViewModel, and the saved camera is applied when the view is (re)created. Verify: `./gradlew :app:assembleDebug :app:lintDebug` passes.
- [x] 3.4 Check that MapLibre 13.6.1 still exposes the OkHttp client hook (`HttpRequestUtil`); if it does not, stop and ask. Implement the User-Agent interceptor (design D7), install it at app start, and test it with a fake `Interceptor.Chain`: the header equals `Sunshine/0.1.0 (Android; com.sunshine.app)`. Verify: `./gradlew :app:testDebugUnitTest --tests "*UserAgent*"` passes.
- [x] 3.5 Add the Compose overlay: a centre crosshair, the coordinates label, the attribution text (bottom-start, from string resources) and the offline banner (string resource). Verify: `./gradlew ktlintCheck :app:lintDebug :app:assembleDebug` passes.

## 4. CI and local tooling

- [x] 4.1 In `.github/workflows/ci.yml`: remove the temporary Gradle gate and the detekt step; run the tests as `./gradlew :core:test :app:testDebugUnitTest`; upload `app-debug.apk` as a workflow artifact on success; include `core/build/reports/` in the failure reports. Verify: `python3 -c "import yaml; yaml.safe_load(open('.github/workflows/ci.yml'))"` succeeds and `grep -c detekt .github/workflows/ci.yml` prints 0.
- [x] 4.2 Adapt `scripts/verify-local.sh` to the modules without detekt: remove the detekt steps and standalone-detekt code, and make `--quick` run ktlint only. Delete `config/detekt/detekt.yml`. Verify: `./scripts/verify-local.sh --quick` passes, and `grep -rn detekt scripts/ config/` finds nothing.
- [x] 4.3 Run Gradle directly in the cloud container. Based on what works, keep or delete `scripts/run-with-proxy.sh`, `scripts/auth-proxy.py` and `scripts/setup-offline-build.sh` (the last one pins the stale AGP 8.7.2). Verify: the remaining scripts are referenced from CLAUDE.md and run as documented.
- [x] 4.4 Align `ANDROID_PACKAGES` in `.claude/hooks/session-start.sh` with the compileSdk and build-tools the build actually uses. Verify: a hook rerun (`CLAUDE_CODE_REMOTE=true CLAUDE_PROJECT_DIR=$PWD .claude/hooks/session-start.sh`) exits 0 without installing anything, and `./gradlew assembleDebug` downloads no SDK package.
- [x] 4.5 Update the "Where things are" and "Build and verify" sections of CLAUDE.md to the real module layout and commands. Verify: every command listed in CLAUDE.md "Build and verify" runs as written.

## 5. Integration checks

- [x] 5.1 Run the full local CI simulation. Verify: `./scripts/verify-local.sh` passes every step (ktlint, Android lint, unit tests, assemble).
- [ ] 5.2 Push and check CI. Verify: the `specs` and `build` jobs are green, no Gradle step is skipped, and the `app-debug.apk` artifact is attached to the run.
- [ ] 5.3 On-device check by the user with the CI APK. Expected: launch shows the Swiss Alps at zoom 10 with `46.8182° N, 8.2275° E`; zoom stops at 5 and 17; rotation keeps centre and zoom; the attribution is visible; airplane mode shows the offline notice while pan/zoom and coordinates keep working; turning the network back on hides the notice within 5 s and loads tiles; launching in airplane mode shows a blank map with crosshair, coordinates, attribution and the notice. Record the results in the PR.
