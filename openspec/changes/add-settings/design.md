# Design

## Context

See proposal.md for the motivation and the specs for the behaviour. Relevant current state:

- **Wiring.** No DI framework: `SunshineApp` builds the shared objects lazily (stores, repositories,
  `AmbientLimit`, `RegionDownloader`) and `MainActivity` switches between `MAP`, `ABOUT` and
  `OFFLINE` with a `rememberSaveable` enum, without a navigation library. `MapViewModel` belongs to
  the activity; its factory reads `SunshineApp`. `SunshineApp.onCreate` already reads the regions'
  map size with `runBlocking` before MapLibre loads a map (design D2 of add-offline-regions).
- **No preference storage.** The app has Room (`OfflineDatabase`) and nothing else. The camera,
  selected time and overlay live in `MapViewModel`'s `SavedStateHandle` only; `restoreCamera()`
  falls back to `DEFAULT_LOCATION` at zoom 10 synchronously in the constructor.
- **Resolution constants.** `SunShadeSweep.CELL_DP = 2.0` (core), `SLIDER_STEP_MINUTES = 5`
  (`map/TimeSelection.kt`, used by the slider *and* as `DayOverlay`'s default step),
  `HEATMAP_CELL_DP = 8.0` and `HEATMAP_STEP_MINUTES = 10` (`MapViewModel`). `DayOverlay` already
  takes `cellDp` and `stepMinutes`; `DayCache` keys days by `(area, date, cellDp)`.
- **Cost of a resolution.** The sweep's lines are one cell apart and the samples along a line keep
  the DEM's resolution, so a sweep costs ∝ 1/cell size (design D9 of add-sun-exposure-heatmap),
  and a day ∝ (1/cell size) × number of steps. The phone timings of #5 and #7 were never measured
  (task 12.2 of #7, accepted as unverified).
- **Opacity.** Shade and hatching are baked into the overlay bitmap at alpha 0.6
  (`SHADE_ARGB`, `UNKNOWN_ARGB` in `map/OverlayImage.kt`); the heatmap bands use the same alpha
  (`HeatmapBands.ALPHA`). Each pixel holds one colour, so drawing it opaque under a layer opacity
  of 0.6 looks the same. The legends draw their swatches opaque.
- **Browsed limits.** `AmbientLimit` sets MapLibre's ambient cache to 512 MiB plus the regions'
  size; `DemTileStore` has `browsedLimitBytes` fixed at construction and an `evict()`.
  MapLibre's `clearAmbientCache` leaves resources that overlap offline regions alone and VACUUMs
  the database when automatic packing is on (MapLibre Android API docs, checked 2026-10-04).
- **Coordinates.** `formatCoordinates(GeoPoint)` in `map/CoordinateFormat.kt` produces the decimal
  label; the Offline page uses it for region names too (offline-regions "Region list"), so region
  names follow the chosen format without a spec change.
- **Location.** MapLibre's location component draws the dot; `onLocationStale(false)` signals a
  fresh position (design D2 of add-gps-location). The map never moves by itself today.

## Goals / Non-Goals

**Goals:**
- One settings store, exposed as a `StateFlow<Settings>` that every consumer observes; no consumer
  reads storage itself.
- Every numeric rule new in this change (LV95, DMS, rounding to a step, presets, ranges) is a pure
  function unit-tested on the JVM; LV95 in `core` against swisstopo's worked example.
- No new computation path: the resolution only changes the parameters `DayOverlay` already takes.

**Non-Goals:**
- No navigation library; the Settings and `Custom resolution` pages are two more enum entries.
- No migration of existing data: settings start at their defaults on update, which are today's
  behaviour except `Start at`, whose default `Last view` has no stored view yet and so shows the
  Alps overview once.

## Decisions

### D1. The gear replaces ⓘ; About moves to the end of the Settings page (user decision)

A fourth 48 dp button does not fit: at 360 dp, the row ends at 168 dp and the overlay toggle
(168 dp wide) starts at 184 dp. The gear takes ⓘ's place; `AboutScreen`'s entries (`aboutEntries`)
are shown as the Settings page's last section, and the `ABOUT` screen is removed. The attributions
stay one tap from the map.

Alternatives considered: the gear in a second row (costs 56 dp of height, no spec change for
About); Settings inside the About page (hard to find); the location button moved to the bottom
right (changes gps-location; space not checked).

### D2. Settings in DataStore Preferences, read with `runBlocking` at start (user decisions)

A `SettingsStore` in `settings/` wraps one Preferences DataStore (`settings.preferences_pb`):
- `val settings: StateFlow<Settings>`, an immutable data class of every setting plus the last
  view, with the defaults of settings "Stored settings";
- one `suspend fun` per change (`setCoordinates`, `setStartAt`, …, `setLastView`).

Values are stored as enum names, ints and doubles. Decoding is a pure function
`decode(Preferences): Settings` that replaces each unreadable or disallowed value by its default
on its own (tested: opacity 75 → 60, unknown enum name → default, others kept). A corrupt file is
replaced by an empty one through DataStore's `ReplaceFileCorruptionHandler`, i.e. every default.

`SunshineApp.onCreate` reads the first value with `runBlocking(Dispatchers.IO)`, as it already
does for the region size, and seeds the `StateFlow` with it, so `MapViewModel`, `AmbientLimit`
and `DemTileStore` are created with the stored values.

Alternatives considered: waiting in the UI for the flow's first value (no blocking read, but the
map screen must be created after it; rejected by the user for simpler wiring); Room (a migration
of `OfflineDatabase` for ~12 values); SharedPreferences (synchronous API Android discourages).

### D3. Shade resolution as a pure table (user decisions)

`core`-free, in `app/settings/Resolution.kt`: `data class Resolution(sunShadeCellDp,
sunShadeStepMinutes, sunHoursCellDp, sunHoursStepMinutes)` and `Preset { FAST, NORMAL, DETAILED,
CUSTOM }` with the table of settings "Shade resolution". `Custom`'s values are clamped to the
ranges by one function on read and on write. `MapViewModel` replaces `SunShadeSweep.CELL_DP`,
`SLIDER_STEP_MINUTES` (as the day's step) and `HEATMAP_*` by the current `Resolution`, and adds it
to the inputs of its overlay and heatmap flows, so a change restarts them as a date change does.
`DayCache`'s key becomes `(area, date, cellDp, stepMinutes)`: days of another resolution stay
until dropped as least recently used, as sun-shade-overlay "Overlay resolution" requires.

The `Custom resolution` page edits a local copy and writes it when the page is left
(`DisposableEffect`), so dragging a value does not restart the day at every step.

Alternatives considered: one preset per mode, and all three presets editable (12 fields, preset
names no longer meaning a speed): rejected by the user for one shared preset plus `Custom`.

### D4. Slider step and rounding (user decisions)

`TimeSelection.kt` gains a step parameter: `sliderPositions(date, zone, step)` and
`sliderTime(date, zone, minutes, step)`, and a pure `roundToStep(time, step): ZonedDateTime`,
which counts steps by elapsed time from the start of the day (so DST days work as the 5-minute
slider does today), rounds half up, and clamps to the day's last step. `MapViewModel` keeps the
5-minute step unless the overlay is on in `Sun & shade`, and applies `roundToStep` when that mode
becomes shown, when the resolution changes while it is shown, and in `onNowClicked` and
`onDateSelected` while it is shown. `SunPanel`'s slider takes the step from the view model.

With `Normal` this also rounds `Now` to 5 minutes while `Sun & shade` is shown (09:47 → 09:45):
the shown time is then always a computed step, which is the user's intent; recorded in
time-selection "Return to now".

Alternatives considered: the slider snapping without rounding the selected time on a switch;
the slider keeping 5 minutes and showing the latest computed grid (panel and overlay could
disagree); the step applying with the overlay off too. The user chose rounding on switch.

### D5. Opacity as MapLibre's `raster-opacity` (user decision)

The overlay bitmaps are drawn opaque: `SHADE_ARGB`, `UNKNOWN_ARGB` and the band colours lose
their 0.6 alpha. The overlay's `RasterLayer` (`MapLibreMap.kt`) gets
`PropertyFactory.rasterOpacity(opacity)` from the setting, set on creation and on change. A
change of opacity therefore shows within one frame and neither re-renders the overlay nor
rebuilds the heatmap. `rasterSaturation` on the map tiles under the heatmap is unaffected. The
legends already draw opaque swatches; they keep doing so (user decision).

Alternative considered: baking the alpha into the bitmaps (re-render on every change).

### D6. LV95 and DMS formatting

`core/SwissGrid.kt`: `fun toLv95(point: GeoPoint): Lv95?` with swisstopo's approximate formulas
(December 2016 edition), returning `null` outside 45.81–47.81° N, 5.95–10.50° E (EPSG:2056 area
of use). Tests: swisstopo's worked example (46°02′38.87″ N, 8°43′49.79″ E → E 2 699 999.76,
N 1 099 999.97, ±0.01 m against the formula, ±1 m against the reference 2 700 000 /
1 100 000), Interlaken (2 632 479.47, 1 170 652.02, ±0.01 m), and the area's edges.

`map/CoordinateFormat.kt`: `formatCoordinates(point, format)` with the three formats; DMS
rounds to tenths of a second in integer arithmetic (tenths of a second as a `Long`) so the carry
into minutes and degrees is exact. Tests: every scenario of map-view "Selected location
crosshair".

Alternatives considered: the rigorous LV95 transformation (more code for precision the 1 m
display does not show); the national border instead of the area of use (border data to ship).

### D7. Start at

`MapViewModel.restoreCamera()` uses, in order: the `SavedStateHandle` (rotation, process
restore), then `Settings.startAt` with `Settings.lastView`, then the Alps overview. The last view
is written by `MapScreen` on `Lifecycle.Event.ON_STOP` through the view model (one write per
background, not per camera move).

For `My location`, the view model holds `pendingStartCentre = true` after a cold start. A camera
move by a gesture (MapLibre's `OnCameraMoveStartedListener` with `REASON_API_GESTURE`, forwarded
by `MapLibreMap`) clears it. The first `onLocationStale(false)` while it is set centres the map
on `locationComponent.lastKnownLocation`, through the same path as the location button, and clears
it. Without location access the component is never activated, so nothing happens (silent
fallback).

The Settings page asks for location access with the same `ActivityResultContracts` request as
`MapScreen` when `My location` is chosen and access is not allowed; it shows the note while access
is not allowed.

### D8. Keep screen on

`MapScreen` sets `LocalView.current.keepScreenOn` from the setting in a `DisposableEffect`,
cleared when it leaves the composition. As the Settings and Offline pages replace `MapScreen`,
the flag is off there; Android drops it in the background.

### D9. Browsed limit and clearing

- `AmbientLimit` takes the browsed limit as a parameter of `onRegionBytes`, or as a mutable value
  with `setBrowsedLimit(bytes)` applying at once; MapLibre evicts to the new ambient size itself.
- `DemTileStore.browsedLimitBytes` becomes a `@Volatile var` with `setBrowsedLimit(bytes)`, which
  calls `evict()`.
- `SunshineApp` observes the setting and calls both.
- `Clear browsed tiles`: `OfflineManager.clearAmbientCache` for the map tiles and a new
  `DemTileStore.clearBrowsed()` (delete the rows no region claims, then their files) for the DEM
  tiles. The button is enabled only while every region row is complete (from the DAO's region
  flow), so no region download can claim a tile while it is being removed.

### D10. Pages

`MainActivity`'s enum becomes `MAP`, `SETTINGS`, `CUSTOM_RESOLUTION`, `OFFLINE`. The Settings page
is one scrollable `Column` with the sections of settings "Settings page"; choices use radio
buttons in a dialog (`Coordinates`, `Start at`, `Shade resolution`, `Browsed tiles limit`),
`Keep screen on` a `Switch`, `Overlay opacity` a `Slider` with 7 positions. A `SettingsViewModel`
exposes the store's flow and the region state for the clear button.

## Risks / Trade-offs

- [Detailed and Custom can be slow] → Detailed is estimated at ~2× Normal per day; Custom's worst
  case (heatmap 4 dp / 5 min) ~4×. Both are unmeasured on the phone; the device check measures
  Fast, Normal and Detailed, and preset values may be adjusted with the user's OK.
- [`runBlocking` read at start] → one small file on the IO dispatcher, before the first frame;
  measured in the device check (budget below). If it exceeds the budget, D2's alternative is
  the fallback.
- [MapLibre's file may not shrink when the limit is lowered] → `setMaximumAmbientCacheSize`
  evicts rows; whether it VACUUMs is unverified. The Offline page measures the file, so its
  `Map tiles` number may lag; the device check records it. `Clear browsed tiles` VACUUMs.
- [Rounding `Now` with `Normal`] → a visible change of today's behaviour (09:47 → 09:45 while
  `Sun & shade` is shown); specified in time-selection.
- [Region names change with the format] → a Swiss region shows as LV95 when LV95 is chosen; this
  follows offline-regions "Region list" and is intended.
- [OSM attribution behind a page instead of a dedicated About page] → still one tap from the map,
  as today.

## Migration Plan

No data migration: the DataStore file is new; every setting starts at its default, which is
today's behaviour apart from `Start at`. Rollback is a revert; an older app ignores the file.

## Performance budget

| Interaction | Budget | How it is checked |
|---|---|---|
| Reading the settings at start | ≤ 50 ms on the phone | log line `Settings read in N ms`, device check |
| Opacity change to the map | ≤ 100 ms, no overlay recomputation | unit test (no flow restart) + device check |
| Coordinate format change | next frame | device check |
| Lowering the browsed limit | ≤ 10 s until both stores fit | log line, device check |
| Clearing the browsed tiles | ≤ 10 s | log line, device check |
| `Sun & shade` sweep of the selected time at zoom 12 | Fast ≤ 250 ms, Normal ≤ 500 ms (#5), Detailed ≤ 1 s | log line `Overlay … in N ms`, device check |
| The `Sun hours` day at zoom 12 | Fast ~7–10 s, Normal ~20–30 s (#7 estimate), Detailed ~40–60 s | log line `Overlay day …`, device check |
| Returning to a cached preset's day | ≤ 100 ms | unit test (cache key) |

## Open Questions

- Whether `setMaximumAmbientCacheSize` shrinks MapLibre's database file (affects only how soon
  the Offline page's `Map tiles` number drops; measured in the device check).
