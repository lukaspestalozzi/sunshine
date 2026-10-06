# Design

## Context

See proposal.md for the motivation and the specs for the behaviour. Relevant current state:

- **Panel.** `map/SunPanel.kt`: a `Surface` with a header row (`formatSelectedTime` and the zone
  ID, `Date`, `Now`), a Material `Slider` over the day's minutes (`sliderMinutes`, `sliderTime`,
  `sliderPositions` in `map/TimeSelection.kt`), then `Altitude`, `Sunshine`, `Sun hours` and the
  sun values in a `FlowRow`, all `bodySmall`. It sits in `MapLabels`' `bottomPanel` slot, bottom
  start, its width capped by `sunPanelMaxWidth` (left of the crosshair in landscape).
- **Time.** `MapViewModel.onSliderMoved(minutes)` snaps to the step of `stepFor(isSunAndShadeShown())`
  through `sliderTime`; `select` rounds to the `Sun & shade` step while it is shown.
- **Sunshine.** `HorizonState.Computed(point, profile)` holds the crosshair's horizon after the
  camera rests; `SunshineUiState.Ready` holds the periods and the state at the selected time.
  `sunshineAt(profile, point, instant)` in `core` gives one state; `sunPosition` the sun.
- **Days.** `DayOverlay` per mode (`Sun & shade` 2 dp / 5 min, `Sun hours` 8 dp / 10 min with
  `Normal`), `computed: StateFlow<Int>`, `gridAt(step): StepGrid?`, `isNight(step)`; the view model
  holds the shown day of each mode inside its flows. `dayProgress` feeds the status card's
  `LinearProgressIndicator` (`map/OverlayControl.kt`).
- **Pages.** `SettingsScreen`, `CustomResolutionScreen` (own `BackHandler` storing its values) and
  `OfflineScreen` have a `headlineSmall` title and no top bar; `MainActivity` switches screens and
  installs `BackHandler`s.
- **Settings.** `Settings` (data class, `decode` with defaults) in a Preferences DataStore,
  `SettingsStore` setters, read once at start.
- **Tests.** No Compose UI tests: behaviour is tested through pure functions and the view model on
  the JVM (JUnit 6); look and gestures are checked on the device. Material 3 is 1.4.0 (Compose BOM
  2026.09.00): `TimePicker`, `TimePickerDialog`, `TooltipBox` and `TopAppBar` are available.

## Goals / Non-Goals

**Goals:**
- One panel whose order matches the user's questions: when (header, tape), sun or not (headline,
  strip), then the details on demand.
- The tape's geometry, the strip's states and the header text as pure, unit-tested functions; the
  composables only draw and forward gestures.
- No change to what is computed (days, horizon, periods).

**Non-Goals:**
- Compose UI test infrastructure (Robolectric or instrumented tests); gestures and look are device
  checks, as before.
- Theming, dark mode or typography beyond the headline's size.

## Decisions

### D1. Panel layout (user decisions, 2026-10-05 and 2026-10-06)
`SunPanel` becomes: header row (the time as a clickable text, `Date`, `Now`), the headline
(`formatSunshine`, unchanged text, `titleMedium` at 18 sp or more), the `Sun hours` line when
shown, the tape, and a `Details` row toggling an `AnimatedVisibility` with altitude, the sun values
(`FlowRow` as today), the whole-day text and the zone ID. The panel keeps its width rules.

*Alternatives (asked):* a "now / until" sentence or a countdown as the headline (user chose the
day's periods); expanded details by default; collapsing the whole panel to a handle (would hide
the tape).

### D2. Header text: a pure formatter
`formatHeaderTime(time, today)` in `SunFormat.kt`: `EEE d MMM` with `Locale.ENGLISH`, the year when
`time.year != today.year`, `HH:mm`, and `UTC…` (the existing `formatUtcOffset`) only when the
offsets at the day's start and the next day's start differ. `today` comes from the view model's
clock, so tests fix it.

*Alternatives (asked):* always the offset; keep ISO and drop only the zone ID.

### D3. Tape geometry: a pure scale
`TapeScale(date, zone, step)` in `map/TapeScale.kt`: minutes since the start of the day on the
instant timeline (as `sliderMinutes`), `DP_PER_MINUTE = 70 / 60`, the last step's minute, snapping
(`snap(minutes)` to the nearest step, clamped to the day), `minutesAt(xDp, needleMinutes)` for a tap
at `xDp` from the needle, and the hour ticks with their wall-clock labels (a 25-hour day labels
`2` twice, a 23-hour day skips `2`). It replaces `sliderPositions`/`sliderTime`/`sliderMinutes`'
uses in the panel; those functions stay for the view model and the days.

### D4. Tape interaction
`map/TimeTape.kt`: a `Canvas` drawing only the visible span around an `Animatable` holding the
minutes under the needle.
- Drag: `detectHorizontalDragGestures` moves the value by `-dx / DP_PER_MINUTE`, clamped to the day;
  each change of `snap(value)` calls `onMinutes(snap(value))`, i.e. `MapViewModel.onSliderMoved`.
- Fling: on release, the `VelocityTracker`'s velocity gives the decay target
  (`exponentialDecay().calculateTargetValue`), which is snapped and clamped; `animateTo` it,
  reporting crossed steps as during a drag. The edges stop it (time-selection "Edges").
- Tap: `detectTapGestures` animates to `snap(minutesAt(x))`.
- Outside changes (`Now`, date, clock dialog, rounding on mode change): while no gesture runs, the
  value animates to `sliderMinutes(selectedTime)`; an exact time stays between steps until the
  next drag.
- Semantics: `contentDescription = "Time of day"`, `stateDescription = HH:mm`, custom actions
  `Earlier` / `Later` moving one step.

*Alternatives:* a `LazyRow` with a snap fling behaviour (snapping per item, edges via content
padding) — harder to keep exact (off-grid) times and continuous step reporting; scroll wheels and
the slider (asked, rejected).

### D5. Strip states: a pure model fed by the view model
`map/TapeStrip.kt`: `enum StripState { NIGHT, SUN, SHADE, UNKNOWN, NOT_COMPUTED }` and
`tapeStrip(steps, night: BooleanArray, state: (Int) -> Sunshine?)`: night wins; else the source's
state, `null` → `NOT_COMPUTED`. The view model exposes `tapeStrip: StateFlow<List<StripState>>`,
built on its compute dispatcher from:
- the **rested** crosshair (`HorizonState`'s point), so the strip keeps its states while the camera
  moves (time-selection "Time tape strip");
- the tape's steps (selected date and `stepFor`), night per step from
  `sunPosition(point, step).elevation + SUN_UPPER_LIMB <= 0`;
- the source by mode (user decision, 2026-10-05):
  - `Sun & shade` on, zoom ≥ 11: the shown `Sun & shade` day if its area's centre is the rested
    crosshair, `gridAt(step)?.stateAt(point)`, recomputed at each emission of its `computed`;
  - `Sun hours` on, zoom ≥ 11: the heatmap's day likewise, each tape step mapped to the heatmap
    step at or before it (`floor(minutes / heatmapStep)`);
  - otherwise: `sunshineAt(profile, point, step)` per step, all `null` while the horizon is
    `Loading`, `UNKNOWN` where the profile is `null` (ground unknown).

The view model publishes the shown day of each mode in a `MutableStateFlow<DayOverlay?>` for this.
`dayProgress` and the status card's progress bar are removed (spec: "Overlay status card");
the strip shows the progress.

*Alternatives (asked, 2026-10-05):* only the overlay's day (neutral with the overlay off); only the
tracer (no progress).

### D6. Exact time: Material clock dialog
Tapping the header's time opens `TimePickerDialog` with a 24-hour `TimePicker` at the selected
hour and minute. `OK` calls `MapViewModel.onTimeTyped(hour, minute)`, which selects
`ZonedDateTime.of(date, LocalTime.of(hour, minute), zone)`: `ZonedDateTime.of` already moves a gap
time forward by the gap and takes the earlier offset in an overlap, as the spec asks; `select`
rounds it while `Sun & shade` is shown.

### D7. Two stored UI values
`Settings` gains `detailsExpanded = false` and `hintDismissed = false`, with DataStore keys,
`decode` defaults and `SettingsStore` setters; they are not shown on the Settings page (settings
"Stored settings"). The map screen reads them from `settings` and writes through the view model.

### D8. Tooltips on the toggle (user decision, 2026-10-06)
Each `SegmentedButton`'s icon is wrapped in a `TooltipBox` with a `PlainTooltip` of its content
description; a long press shows it without selecting (the tooltip state consumes the long press,
the button keeps the tap).

### D9. First-run hint (user decision, 2026-10-06)
A `Surface` card in `MapLabels`' bottom-start column, directly above the panel and as wide: so it
never covers the crosshair (portrait: below it; landscape: left of it), the toggle (top end) or
the tape (in the panel). The second text uses `InlineTextContent` with the toggle's own icons.
Shown while `!settings.hintDismissed`; `Got it` stores `hintDismissed = true`. Not modal.

### D10. Tap to centre (user decision, 2026-10-05)
`MapLibreMap` adds `addOnMapClickListener`: it calls `onCameraGesture()` (as a drag does, so a
location follow stops) and `animateCamera(newLatLng(point), 300 ms)`, returning `true`. MapLibre
reports a single tap only when it is not part of a double tap, so double-tap zoom stays.

### D11. Top bars
`SettingsScreen`, `CustomResolutionScreen` and `OfflineScreen` get a `Scaffold` with a
`TopAppBar` (title, `navigationIcon` = `IconButton` with a Material Symbols `arrow_back` vector,
content description `Back`) and an `onBack` parameter; `MainActivity` passes the same action as
its `BackHandler`s; `CustomResolutionScreen`'s arrow calls the function its `BackHandler` calls,
so the values are stored either way. The `headlineSmall` titles go.

## Performance budget

| Computation (user-triggered) | Budget | Check |
|---|---|---|
| Strip from the horizon (288 steps: sun position and `sunshineAt`) after the camera rests | ≤ 20 ms on the JVM | unit test |
| Strip update after a computed step of a day (288 `stateAt`) | ≤ 5 ms on the JVM | unit test |
| Tape drag and fling | no dropped frames visible, the overlay following each step as today | device check |
| Tap to centre | camera at the point within 300 ms (±50 ms) | device check |
| Expanding or collapsing the details | ≤ 300 ms animation | device check |

## Risks / Trade-offs

- [The tape's feel (fling speed, snapping) is hard to judge on the JVM] → Its geometry is tested;
  the feel is a device check, with the decay's friction as the one tuning knob.
- [`TooltipBox` around a `SegmentedButton` may swallow taps or show on tap] → Device check; fall
  back to `onLongClick` on the button with the same tooltip state.
- [The hint above the panel may be squeezed in short landscape windows] → It scrolls with the
  panel's column; it is shown once and dismissed with one tap.
- [The strip's day must belong to the crosshair] → Only a day whose area's centre is the rested
  crosshair is used; else the tracer is not substituted, the strip stays `NOT_COMPUTED` until
  that day exists (it starts with the selected time within 300 ms of the rest).
- [Colours] → The strip's colours are spec values chosen to match the map (shade, unknown); sun
  yellow and night blue-grey may be tuned on the device within this change.

## Migration Plan

Two new stored values with defaults; older stored data reads unchanged. Rollback: revert the
change's commits.

## Open Questions

None blocking. The fling's friction and the colours are tuned during the device checks.
