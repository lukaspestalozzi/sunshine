# Design

## Context

See proposal.md (Why) and the specs `sun-position`, `time-selection` and `map-view` (delta).
Starting point, as built by `add-app-shell`:

- `core` holds only `GeoPoint` and `DEFAULT_LOCATION`. It is a Kotlin/JVM module (toolchain 17),
  tested with JUnit 6.
- `app/map`: `MapViewModel(savedState, isOnline)` holds `CameraState(center, zoom)` in a
  `SavedStateHandle` and is built by a `viewModelFactory` in `MapScreen.kt`; no DI framework.
  `MapLabels` draws the coordinates (top), the offline notice and the attribution (bottom-start)
  over the `MapLibreMap` composable. Rotation and tilt gestures are MapLibre's defaults (enabled).
- Facts checked while drafting (2026-09-26):
  - commons-suncalc: latest release 3.11 (2024-06). Pure Java, ~57 kB, no runtime dependencies
    (spotbugs annotations optional, the rest test-scoped).
  - Its `SunTimes` with `on(<local midnight>)` and `limit(<length of that day>)` returns the
    events within that day. With `twilight(VISUAL)` (the default) the Interlaken reference times in
    `investigations/` come out within 1 min 11 s. Without a limit it searches up to a year ahead
    (for Svalbard on 2025-12-21 it returns a "sunrise" on 2026-02-15).
  - Its `SunPosition.getAltitude()` adds refraction only while the geometric elevation is above
    0°, where it matches the Sæmundsson formula (0.467° vs 0.466° at +0.12°). At or below 0° it
    returns the geometric value, so the apparent elevation jumps by ~0.47° at 0°.
    `height()` changes rise and set times but not the position.
  - An independent implementation (Python `astral` 3.2, NOAA algorithm) agrees with
    commons-suncalc within 36 s on every event and within 0.02° on position, except where the
    refraction gap above applies.
  - MapLibre 13.6.1 `MapLibreMapOptions` has `rotateGesturesEnabled(Boolean)` and
    `tiltGesturesEnabled(Boolean)`.

## Goals / Non-Goals

**Goals:**
- A small sun API in `core` that change 4 (`add-terrain-horizon`) can reuse unchanged: position
  at an instant, events of a local day.
- Absent events as explicit values in the API, so the UI cannot fall back to an invented time.
- All time arithmetic (day windows, DST days, slider grid, formatting) is pure code, tested on the
  JVM.

**Non-Goals:**
- Caching sun results. A full recomputation takes well under a millisecond (see performance
  budget).
- A DI framework (still no real object graph; see D8).
- Instrumented UI tests (no emulator, as in `add-app-shell`).

## Decisions

### D1. Times use the device time zone, always labelled (user decision)
The zone is taken from a `java.time.Clock` given to the ViewModel (`Clock.systemDefaultZone()` in
production), when the ViewModel is created. The selected time is shown with its UTC offset and the
zone ID (spec: time-selection). The legacy failure "device time zone" is avoided by the label and
by computing each day as the window [local midnight, next local midnight), never from a UTC anchor.
*Alternatives:*
- Fixed Europe/Zurich: correct across the Alps, wrong for users who plan elsewhere.
- The location's zone via timeshape 2026b.29: correct everywhere, but a 23 MB jar plus
  `zstd-jni` native code with unverified Android support.
- timezonemap 4.5: its boundary data is from 2021.

### D2. Refraction: use the library's apparent elevation (user decision)
The shown elevation is `SunPosition.getAltitude()`. "Above the horizon" uses the geometric
elevation `getTrueAltitude() >= -0.833°`. That is the same criterion as the library's `VISUAL`
sunrise and sunset, so the two agree to within seconds.
Known limitation: no refraction at or below 0° geometric elevation. For terrain visibility
(change 4) this is exact wherever the terrain horizon is above ~+0.5°, which covers valleys and
slopes. Where the horizon in the sun's direction is at or below 0° (summits, ridges), first
sunshine comes out 3–5 min too late and last sunshine too early. The numbers: the missing
refraction is 0.48–0.65°, and the sun climbs 0.14–0.17°/min near the horizon at Interlaken.
For scale, real refraction near the horizon varies by about ±0.2° with the weather (±1.5 min).
`docs/roadmap.md` entry #4 records the limitation.
*Alternatives:*
- Own continuous Sæmundsson refraction in `core`: no gap, about 15 more lines.
- Geometric elevation only, deferring refraction to change 4.

### D3. Time controls: date picker, 5-minute slider, static "Now" (user decision)
- Date: Material 3 `DatePickerDialog`. `DatePickerState` works in UTC-midnight milliseconds.
  A small, tested function converts between those and `LocalDate`, so the device zone cannot
  shift the picked date.
- Time: a continuous Material 3 `Slider`. Its value is minutes since the start of the selected
  day, and the ViewModel snaps it to the 5-minute grid. A stepped slider would draw up to 299 tick
  marks and could not show an off-grid "Now" time. The slider length is the real length of the
  day, `Duration.between(start of day, start of next day)`: 23 or 25 hours on most DST days
  (22 or 26 at Antarctica/Troll). Wall times repeated on the fall-back day are distinct
  positions.
- "Now": `Instant.now(clock)`, truncated to the minute, set once.
- State: the selected instant (epoch milliseconds) in `SavedStateHandle`. It survives rotation
  and process death, and a new launch starts at now.

*Alternatives:* "Now" that keeps following the clock; a 15-minute grid.

### D4. Panel extras: sun direction line, day length, civil twilight (user decision)
Solar noon (with its elevation) was offered and not chosen. Civil twilight uses the library's
`Twilight.CIVIL` (sun centre at -6°).

### D5. North-up, flat map (user decision)
`MapLibreMapOptions.rotateGesturesEnabled(false)` and `.tiltGesturesEnabled(false)`. The sun line
is then a Compose `Canvas` overlay centred on the crosshair, at angle = azimuth
(`dx = sin az`, `dy = -cos az`). Its length is 30 % of the map's shorter side. It is solid when the
sun is above the horizon and uses a dash `PathEffect` below. It is drawn above the map and below
the crosshair.
*Alternatives:*
- Bearing-aware overlay with tilt disabled: rotation stays available, and bearing goes into the
  camera state.
- A MapLibre GeoJSON line layer: correct under rotation and tilt, but its geometry must be rebuilt
  on every camera move.

### D6. `core` API
New package content in `com.sunshine.core`:

```kotlin
data class SunPosition(val azimuth: Double, val elevation: Double, val isAboveHorizon: Boolean)
fun sunPosition(point: GeoPoint, instant: Instant): SunPosition

enum class WholeDay { ABOVE_HORIZON, BELOW_HORIZON }
data class SunDay(
    val civilDawn: ZonedDateTime?,   // null = does not occur within the day
    val sunrise: ZonedDateTime?,
    val sunset: ZonedDateTime?,
    val civilDusk: ZonedDateTime?,
    val dayLength: Duration,
    val wholeDay: WholeDay?,         // null = at least one sunrise or sunset within the day
)
fun sunDay(point: GeoPoint, date: LocalDate, zone: ZoneId): SunDay
```

- `sunDay` runs two `SunTimes` computations (`VISUAL` and `CIVIL`) over the window
  `on(date.atStartOfDay(zone)).limit(Duration.between(start, next start))`.
- `wholeDay` comes from `isAlwaysUp` / `isAlwaysDown` of the `VISUAL` computation.
- Day length follows the spec. With both events it is `set − rise` when rise < set, otherwise
  `(set − start) + (end − rise)`. With a rise only it is `end − rise`, with a set only
  `set − start`. With no event it is the whole day or zero, depending on `wholeDay`.
- `null` is the explicit "absent" value (spec: "reported as absent"). It is documented in KDoc,
  and the formatter maps it to `none this day`. A sealed type was considered and rejected: four
  optional fields with a single meaning of absence need no more than a nullable type.
- The library is used only inside these two functions, so it can be replaced without touching
  callers.

### D7. `app`: state in `MapViewModel`, logic in pure files
- `MapViewModel(savedState, isOnline, clock, computeDispatcher)` adds:
  - `selectedTime: StateFlow<ZonedDateTime>`, with `onDateSelected(LocalDate)`,
    `onSliderMoved(minutesSinceStartOfDay: Float)` and `onNowClicked()`;
  - `sun: StateFlow<SunInfo?>`, which is `combine(camera, selectedTime)` →
    `mapLatest { sunPosition + sunDay }` → `flowOn(computeDispatcher)` → `stateIn`. `null` only
    covers the moment before the first result.
- Pure, JVM-tested files next to `CoordinateFormat.kt`:
  - `TimeSelection.kt`: day window, slider length and snapping, the date change with the DST gap
    rule, and the picker millisecond conversion.
  - `SunFormat.kt`: selected-time label, UTC offset, azimuth with compass point, elevation, event
    times with conditional offset, day length. Built on `BigDecimal` / `Locale.ROOT` like
    `formatCoordinates`.
- Composables: `SunPanel` (bottom, above the attribution, holding the values, a date button,
  "Now" and the slider) and `SunLine`, both in `app/map`. New strings go to `strings.xml`.

### D8. Still no DI framework
The factory in `MapScreen.kt` passes `Clock.systemDefaultZone()` and `Dispatchers.Default`, and
tests pass `Clock.fixed(…)` and a test dispatcher. Four constructor parameters and no shared
object graph don't justify Koin yet (same reasoning as add-app-shell D5).

### D9. Reference values
- Sunrise, sunset and day length at Interlaken: timeanddate.com, via
  `investigations/sunrise-integration-test-plan.md`.
- Civil twilight, positions, DST days and Tokyo: no published table could be fetched (timeanddate
  answers 403 to automated requests). They are values on which two independent implementations
  (commons-suncalc 3.11 and astral 3.2 / NOAA) agree within 36 s and 0.02°. They are recorded with
  both implementations' raw outputs in `investigations/sun-position-references.md` (task 1.1).
- The Tromsø and Svalbard scenarios are structural (which events exist and in what order), not
  exact times, because near-polar events are too sensitive to model differences to serve as
  oracles.

### Performance budget
- Trigger: every camera-move event and every change of the selected time.
- Work: one `SunPosition` and two `SunTimes` computations. Measured at 0.06–0.3 ms per
  recomputation on the desktop JVM.
- Budget: ≤ 5 ms per recomputation on a mid-range phone, on `Dispatchers.Default`. `mapLatest`
  drops stale inputs, so a fast pan never queues work. The main thread only formats strings,
  which is O(1).

### Verification strategy
- `core`: parameterized JUnit tests for every numeric scenario of `sun-position`, with the spec's
  tolerances.
- `app`: JVM tests for `TimeSelection`, `SunFormat` (every formatting scenario) and `MapViewModel`
  (initial time, "Now", date change, slider snapping, rotation restore, sun info following the
  camera).
- On-device check with the CI APK: slider on a normal day, date picker, "Now", the direction and
  dash of the sun line, rotation/tilt gestures having no effect, panel not covering the crosshair
  or attribution.

## Risks / Trade-offs

- [Refraction gap ≤ 0° geometric: 3–5 min pessimistic on summits in change 4] → Accepted (D2);
  recorded in the roadmap for entry #4.
- [commons-suncalc has had no release since 2024-06] → Pure math with no dependencies; used only
  inside `sunPosition`/`sunDay`, so it is replaceable.
- [The device zone differs from the location's zone, e.g. planning a Tokyo trip from Zurich] →
  Times are correct instants and always labelled with offset and zone ID (D1).
- [The device zone changes while the app runs] → The zone is read when the ViewModel is created;
  the label shows which zone is in use.
- [The shown elevation is negative while "above the horizon" (between sunrise and ~08:14 in the
  winter example)] → Stated in the spec; it follows from the standard sunrise definition.
- [Material 3 `DatePicker` UTC-millis pitfalls shift the date by one in zones west of UTC] →
  Explicit, tested conversion (D3).
- [No emulator: the panel layout and gestures are not tested automatically] → On-device check.

## Migration Plan

No data to migrate. Disabling rotation and tilt affects only live gestures, because the saved
camera state has no bearing or tilt. Rollback: revert the change's commits together.
