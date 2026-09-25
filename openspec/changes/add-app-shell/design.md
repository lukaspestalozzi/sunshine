# Design

## Context

See proposal.md (Why) and specs/map-view/spec.md (requirements). Starting point:

- The repository has no Gradle modules. The root Gradle files, version catalog and wrapper
  (Gradle 8.13) are leftovers of the discarded build. CI skips all Gradle steps until
  `app/build.gradle.kts` exists.
- The SessionStart hook installs `platforms;android-37.0` and `build-tools;37.0.0`. The cloud
  container has JDK 21 and no `/dev/kvm`, so no emulator; CI uses JDK 17.
- Latest stable versions, checked 2026-09-25 against Maven/Google metadata: AGP 9.4.1, Gradle 9.8.0,
  Kotlin 2.4.20, Compose BOM 2026.09.00, MapLibre Android 13.6.1, ktlint-gradle 14.2.0.

## Goals / Non-Goals

**Goals:**
- A build in which `core` cannot use Android APIs, and the build enforces this rather than
  convention.
- A green CI that runs every quality gate on both modules.
- Map-screen logic (coordinate formatting, User-Agent, offline state, viewport state) testable on
  the plain JVM.

**Non-Goals:**
- A DI framework, navigation, persistence. Each arrives with the first change that needs it.
- Instrumented/UI tests on CI. There is no emulator in CI or the cloud container.
- Release configuration (signing, minification, ABI splits).

## Decisions

### D1. Module layout: `core` = Kotlin JVM, `app` = Android application
`core` applies `org.jetbrains.kotlin.jvm` (JVM toolchain 17). No Android artifact is on its
classpath, so Android APIs are impossible there. `app` applies `com.android.application` and
depends on `project(":core")`.
*Alternatives:* a single module was rejected by the user. An Android-library `core` would allow
Android APIs to creep in.

### D2. Toolchain and identity
- AGP 9.4.1 with built-in Kotlin support; Gradle wrapper 9.8.0; Kotlin 2.4.20 plus the Compose
  compiler plugin; Compose BOM 2026.09.00 (Material 3).
- `compileSdk` 37 and `targetSdk` 37, matching the SDK platform the hook installs. `minSdk` 29
  (user decision). JVM target 17.
- `applicationId` and `namespace` `com.sunshine.app` (user decision); `versionName` `0.1.0`.
- All versions live in `gradle/libs.versions.toml`. At apply time, re-check for newer stable
  versions and take them if they exist.

### D3. MapLibre embedded via `AndroidView` + `MapView` (user decision)
The official MapLibre Android SDK 13.6.1 goes in a small composable. `onCreate`, `onStart`,
`onResume`, `onPause`, `onStop` and `onDestroy` are forwarded from the Compose
`LifecycleOwner`; `onLowMemory` is forwarded as well.
*Alternative:* `maplibre-compose` 0.18. Rejected because it is pre-1.0 and its API churns; later
overlays need MapLibre's full imperative API (image and GeoJSON sources) anyway.

### D4. Map style and blank tiles
The style JSON is built in code. It has a background layer (neutral light grey) and one raster
source, `https://tile.opentopomap.org/{z}/{x}/{y}.png`, with `tileSize` 256, `maxzoom` 17 and a
raster layer. Tiles that fail to load simply show the background, which gives the "blank, no
substitute imagery" behavior. Camera min/max zoom is 5/17.
MapLibre's own logo and (i) attribution button are disabled. The required attribution is a
Compose `Text` anchored bottom-start, above the map and outside any other overlay.

### D5. Screen state in a `MapViewModel` with `SavedStateHandle`, no DI framework yet
The ViewModel holds the camera state (centre `GeoPoint` and zoom) and `isOffline`.
- The camera state is written on every MapLibre camera-move event and stored in
  `SavedStateHandle`, so it survives rotation and process death. It is applied to the `MapView`
  when that is (re)created.
- The ViewModel is created with a `viewModelFactory { initializer { … } }` that passes its two
  dependencies explicitly (`SavedStateHandle`, `NetworkMonitor`).
*Alternative:* Koin now. Rejected: a two-dependency graph does not justify it (project context:
no speculative abstractions); Koin is introduced by the first change with a real object graph.

### D6. `GeoPoint` and coordinate formatting
- `core`: `data class GeoPoint(latitude, longitude)` validates finite values,
  latitude ∈ [-90, 90] and longitude ∈ [-180, 180], and throws `IllegalArgumentException`
  otherwise. It also defines `DEFAULT_LOCATION` = 46.8182, 8.2275.
- `app` (UI): a pure `formatCoordinates(GeoPoint): String` using `Locale.ROOT`, 4 decimals and
  N/S/E/W letters.
- Rounding mode: `HALF_UP`, applied to the absolute value.

### D7. User-Agent via MapLibre's OkHttp client
The app gives MapLibre an `OkHttpClient` with an interceptor that sets
`Sunshine/<BuildConfig.VERSION_NAME> (Android; com.sunshine.app)`, through `HttpRequestUtil`. The
interceptor is a small class, unit-tested with a fake `Interceptor.Chain`.

### D8. Offline detection
`NetworkMonitor` wraps `ConnectivityManager.registerDefaultNetworkCallback` as a `Flow<Boolean>`
("has validated internet"). The ViewModel maps it to `isOffline`. The notice is a non-blocking
banner at the top of the map. On reconnect, MapLibre is expected to retry failed tiles through its
own reachability handling (unverified). The on-device check in task 5.3 confirms this. If tiles do
not come back, stop and ask.

### D9. Quality tooling (detekt dropped, user decision)
- ktlint (`org.jlleitschuh.gradle.ktlint` 14.2.0) runs on both modules. Android lint `lintDebug`
  runs on `app`.
- detekt 1.23.8 is not used: it is built for Kotlin 2.0 and we use 2.4, and 2.0 is still alpha.
  This change removes the detekt CI step, the detekt steps of `scripts/verify-local.sh` and
  `config/detekt/detekt.yml`. It is revisited when detekt 2.0 is stable (noted in
  `docs/roadmap.md`).
- Tests use JUnit 5 (Jupiter) in both modules; its parameterized tests suit the oracle tables of
  later changes. `app` runs them with `useJUnitPlatform()`; the ViewModel is tested with
  `kotlinx-coroutines-test`.

### D10. CI and local verification
- CI: remove the temporary Gradle gate. Steps: ktlint, Android lint, unit tests
  (`:core:test :app:testDebugUnitTest`), `assembleDebug`. On success, upload the debug APK as a
  workflow artifact so the reviewer can install it on a device.
- `scripts/verify-local.sh` runs the same steps.
- First try Gradle directly in the cloud container (JAVA_TOOL_OPTIONS already routes Java through
  the proxy). Keep or delete `scripts/run-with-proxy.sh` / `auth-proxy.py` /
  `setup-offline-build.sh` based on what actually works, and say which in CLAUDE.md.

### Performance budget
Each camera-move event does only O(1) work on the main thread: a state update plus string
formatting, well under 1 ms. Tile loading and rendering are MapLibre's, off the main thread. No
blocking I/O on the main thread.

### Verification strategy
- JVM unit tests: `GeoPoint` validation, coordinate formatting (every formatting scenario in the
  spec), the User-Agent interceptor, and ViewModel state (default viewport, saved-state restore,
  offline mapping).
- On-device check by the user with the CI APK: pan/zoom limits, rotation, attribution, the offline
  notice and recovery. The emulator gap is why.

## Risks / Trade-offs

- [AGP 9.4.1 may not accept `compileSdk` 37] → Task 1.2 builds first. If AGP rejects or warns,
  stop and ask; the fallback is 36 plus aligning the hook.
- [MapLibre 13.x may have changed the `HttpRequestUtil` OkHttp hook] → Verify against the 13.6.1
  API in task 3.4 before relying on it. If it is gone, stop and ask.
- [APK size: MapLibre ships native libraries for 4 ABIs, tens of MB] → Accepted for debug builds;
  ABI splits are a release concern.
- [No detekt: no complexity or size checks] → Keep classes small by review. Revisit with
  detekt 2.0.
- [Maven Central returned HTTP 429 once from the cloud container] → Gradle's cache plus a single
  retry. A persistent failure is reported, not worked around.
- [No emulator: UI behavior is not verified automatically] → JVM tests for the logic plus a manual
  device check with the CI APK.

## Migration Plan

Greenfield; nothing to migrate. Rollback: revert all of the change's commits together. That also
restores the CI gate.

## Open Questions

- ABI filtering or splits to reduce APK size. Deferrable to release preparation; it does not
  affect this change's specs or tasks.
