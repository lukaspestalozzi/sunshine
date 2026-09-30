# Design

## Context

See proposal.md for the motivation and specs/ for the behaviour. The heatmap builds on what #5
left in place:

- `DayOverlay` (`app/map/DayOverlay.kt`) holds a `ShadeGrid` per slider step of one visible area
  and date. It computes the selected time first, then the rest on `dayDispatcher()` (half the
  cores). `computed: StateFlow<Int>` counts finished slider steps, and `nightSteps` counts steps
  that need no terrain work (design D12 of add-sun-shade-overlay).
- `DayCache` keeps days by area and date up to a quarter of the heap. `DayOverlay.bytes` is what
  it budgets.
- A `ShadeGrid`'s cells lie on sweep lines parallel to the sun's azimuth, so the cell layout
  differs from step to step. The only common frame is geographic: `ShadeGrid.stateAt(lat, lon)`.
- `renderOverlay(grid)` (`app/map/OverlayImage.kt`) turns one grid into a north-up ARGB
  `OverlayImage` at one pixel per dp, and `MapLibreMap` shows one `OverlayImage` as a MapLibre
  `ImageSource`.
- The overlay flow in `MapViewModel` owns the current day (its local `day` variable), cancels and
  resumes it, and publishes `dayProgress`.
- `SunDay.dayLength` (`core`) is the sun-position "Day length" of a point and date.
- No device timing exists for a whole day. The #5 device check confirmed only that it works.

## Goals / Non-Goals

**Goals:**
- The heatmap reuses the day's grids; it adds no terrain work and no network requests.
- One pass after the day is complete builds the counts (user decision). Its cost is measured on
  the device.
- The heatmap and the overlay of the selected time share one `ImageSource` and one set of notices,
  selected by the mode.

**Non-Goals:**
- Changing how or in which order the day is computed.
- A `core` API for the heatmap. The counting is resampling in screen space, which lives in `app`
  like `renderOverlay`.

## Decisions

### D1. The heatmap is a mode of the overlay (user decision, 2026-09-28)
`OverlayMode { SUN_AND_SHADE, SUN_HOURS }` in `MapViewModel`, saved in `SavedStateHandle` under
`overlay_mode` like `overlay_on`, and `SUN_AND_SHADE` by default. The mode only selects what is
shown. The day's computation, its cache and its progress bar are shared and unaware of the mode.
*Alternatives (asked):* a separate heatmap layer with its own computation (duplicates
`DayOverlay`), or a region heatmap at low zoom (needs the DEM halo of #6).

### D2. Counting: one pass over the day's grids once the day is complete (user decision, 2026-09-28; the day and the raster are coarser since D9)
`SunHours` (new, `app/map/SunHours.kt`) holds, for the north-up raster of the day's area at one
pixel per dp (the raster of `renderOverlay`):
- `sun: ShortArray`: steps at which the pixel's cell is sun;
- `unknown: ShortArray`: steps at which it is unknown;
- `steps: Int`: the slider steps counted (≤ 300, so 16 bits suffice).

`sunHours(day)` walks every slider step's grid (never an off-grid selected time) and, for each
pixel, adds the state of the cell under the pixel centre. It skips night steps whose grid has no
unknown cell: every cell is shade there (D12 of #5), so they add nothing. The pixel geometry
(pixel centre → latitude, longitude) moves out of `renderOverlay` into a shared helper, so both use
the same pixels.

The pass runs on the day's background dispatcher once `computed == steps.size`. Its result is
stored on the `DayOverlay` (`sunHours`, volatile) and counted in `DayOverlay.bytes` (4 bytes per
pixel, ~1.4 MB at 400 × 850 dp), so `DayCache` budgets it. A day computed anew is a new
`DayOverlay`, so its old counts go with it.

The pass runs only when the mode is `Sun hours` (on completion, or on switching to that mode with
a complete day that has no counts yet). Users of the overlay who never open the heatmap pay
nothing for it. The price is a wait of one pass on the first switch to `Sun hours` for a day that
is already complete.

*Alternatives:*
- incremental counting as each step is stored (asked; user chose one pass). It would make the
  heatmap ready the moment the last step finishes, at the cost of counts for every day, including
  those never shown in `Sun hours`. It is the fallback if the pass exceeds its budget;
- counting in each grid's own cell layout: impossible to add up, because layouts differ per step;
- always building the counts on completion: simpler state, but it costs CPU for every day.

### D3. Colour scale: 30-minute bands up to the day length at the map centre (user decisions, 2026-09-28)
- n = max(1, ⌈dayLength / 30 min⌉), with `dayLength` from `sunDay(area.center, date, zone)`.
- The band of a pixel is min(⌊sun × 5 min / 30 min⌋, n − 1) = min(sun / 6, n − 1).
- The four colour stops are evenly spaced over t ∈ [0, 1], with band i at t = i / (n − 1)
  (t = 0 when n = 1):

  | Stop | Colour | OKLab L |
  |---|---|---|
  | 0 h (slate, the shade tint) | `#455A64` | 0.454 |
  | blue | `#3F7FBF` | 0.584 |
  | yellow | `#D9C84A` | 0.824 |
  | day length (light amber) | `#FFE0A3` | 0.918 |

- Interpolation is in OKLab. Lightness is then linear between stops, and it rises strictly
  because the stops' L does. That is the spec's testable property.
- Every band has the alpha of `SHADE_ARGB`: 0.45 (`0x73`) at first, 0.6 (`0x99`) since D10, so 0 h looks like the shade of
  `Sun & shade`. The device check tunes the alpha and the stops. A change is acceptable as long as
  the lightness still rises.
- Brightness rising with hours keeps the scale readable for colour-blind users and in sunlight.
  Blue–yellow is the hue axis that the common colour-vision deficiencies preserve best.

*Alternatives (asked):* a single-hue alpha ramp of the shade tint (neighbouring bands differ by
only 0.02–0.035 alpha, so band edges barely show); a warm single-hue ramp with 0 h clear (inverts
the meaning of "clear" between the modes). A green stop was left out, because it vanishes over
forest.

### D4. Rendering and unknown
`renderSunHours(hours, bands)` produces an `OverlayImage` over the same corners as the day's grids:
- a pixel with `unknown == steps` (unknown at every step) gets only the hatching;
- a pixel with `unknown > 0` gets `UNKNOWN_ARGB` on the hatching's stripes (the stripe test of
  `renderOverlay`) and its band colour between them;
- every other pixel gets its band colour.

The hatching therefore reads as the same pattern in both modes.

The image is rendered when a `Ready` heatmap state is created (off the main thread, like the
overlay's). It is not stored in the cache, because the counts suffice to render it again (~340k
pixels, cheap next to the pass).

### D5. View-model state (the heatmap's own day since D9)
- The overlay flow publishes its current day as `currentDay: StateFlow<DayOverlay?>`. This
  replaces nothing, because its local `day` stays the owner.
- `heatmap: StateFlow<HeatmapUiState>` has the states `Off`, `ZoomedOut`, `Computing(kept: Ready?)`
  and `Ready(area, date, hours, image, bands)`. It combines `currentDay`, the day's `computed`,
  the mode and the overlay's on/zoom state:
  - The state is `Ready` when the mode is `Sun hours` and the current day is complete with counts.
  - The state is `Computing(kept = the last Ready)` while the current day is incomplete, or while
    its counts are being built. After a pan or date change, the kept heatmap stays on its own
    corners (spec "Heatmap updates").
- A time change within the day changes neither `currentDay` nor its completion, so the heatmap
  stays.
- `MapScreen` passes MapLibre the image of the mode: `overlay.image()` in `Sun & shade`, and the
  heatmap's `Ready` or kept image in `Sun hours`.

### D6. UI (user decisions; controls and legend layout replaced by D7 on 2026-09-29)
- **Mode control (replaced by D7):** a Material 3 `SingleChoiceSegmentedButtonRow` with
  `Sun & shade` and `Sun hours`, below the toggle chip and its progress bar and above the legend,
  shown only while the overlay is on. The first device screenshot showed it wrapping, running off
  the screen and overlapping the notice.
- **Legend:** in `Sun hours` mode, a horizontal bar of the n band colours on the legend's surface,
  with the labels `0 h`, `2 h`, … under the band where each whole 2 h begins. Its bands come from
  the sun panel's day length (map centre, selected date), so the scale shows while the day is
  still computed (apply, 2026-09-29). Below it is the `Unknown` hatching row, which it
  shares with the overlay's legend.
- **Notices:** `notice(mode, …)` picks `overlayNotice` or `heatmapNotice`. In `Sun hours` it shows
  `Computing sun hours …` while the heatmap is `Computing`, and in both modes it shows
  `Zoom in to see sun and shade` when zoomed out. Since D7 they show in the status card.
- **Panel:** `formatSunHours(state, center)` gives the `Sun hours` value text. It gives `…` unless
  a `Ready` heatmap's area is the current visible area. Otherwise it reads the pixel under the map
  centre, which is the raster's centre pixel. Durations use the existing `formatDayLength` style
  (`5 h 20 min`); an unknown time under an hour is `10 min`.
- Strings live in `strings.xml`: `heatmap_mode_sun_and_shade`, `heatmap_mode_sun_hours`,
  `heatmap_computing`, `sun_panel_sun_hours`, and the value templates.

### D7. One toggle and one status card (user decisions, 2026-09-29)
The first device screenshot showed five floating pieces at the top right (chip, progress bar,
segmented control, legend, notice) with different widths and edges, a wrapped `Sun & shade`
label, a control running off the screen, and a washed-out legend. They become one column at the
top right:

```
+--------------------------------------------+
| (i)                        [ o | (*) | ## ] |   toggle: 3 x 56 dp = 168 dp
| [46.8010 N, 8.2176 E]      +--------------+ |
|                            | Sun hours    | |   mode name
|                            | Computing .. | |   notice
|                            | [=====-----] | |   progress, directly below the notice
|                            | [slate-amber]| |   legend, opaque
|                            | 0 2 4 6 8 h  | |
|                            | // Unknown   | |
|                            +--------------+ |
+--------------------------------------------+
```

- **Toggle:** a `SingleChoiceSegmentedButtonRow` of three icon-only `SegmentedButton`s, 56 dp
  wide each (168 dp in all), with content descriptions `Overlay off`, `Sun and shade now`,
  `Sun hours of the day`. The icons are Material Symbols, copied as vector drawables (see D8):
  `layers_clear` (off), `contrast` (a half-filled circle: sun and shade), `timelapse` (hours of
  the day). The selected button's check icon is turned off, so the width stays fixed.
- **State:** the view model keeps `isOverlayOn` and `overlayMode`. The toggle shows `Off` when
  `isOverlayOn` is false, else the `overlayMode` (`overlayOption`, a pure function tested on the
  JVM). `MapViewModel.onOverlaySelected` takes the selection: `Off` switches the overlay off; a mode
  is set first and the overlay then switched on if it is off, so the other mode never shows for a
  moment. (Planned as a UI-only mapping; moved into the view model during apply, 2026-09-29, so that
  it is tested with the view model.)
- **Status card:** a `Surface` of exactly the toggle's width, directly below it, shown while the
  overlay is on. Top to bottom: the mode's name (`labelLarge`); the notice from `notice(mode, …)`
  (`labelMedium`), if any; the `LinearProgressIndicator` of `dayProgress` directly below the
  notice, or below the name when there is none; the mode's legend. The notices leave
  `MapLabels`' top column, which keeps the coordinates and the offline notice and moves from the
  top centre to the top-left corner, below the ⓘ button (D8).
- **Legend:** band and shade swatches are drawn opaque (alpha 1). The overlay's translucency is
  right on the map but made the legend's colours barely visible on the card. The labels are numbers
  every 2 h with the unit once, after the last label (`0 2 4 6 8 h`): at 168 dp a band of 2 h is
  about 21 dp, too narrow for `10 h` or `14 h`.
- **One surface style** for all floating elements (coordinates, offline notice, toggle
  background, status card, sun panel): `colorScheme.surface` at alpha 0.85, `shapes.medium` (12 dp)
  corners, 8 dp inner padding, 8 dp gaps, and the same 8 dp margin from the safe-drawing insets.
  The top-right column aligns to one right edge.
- *Alternatives (asked):* a layers button with a menu and a progress ring (least crowded, but two
  taps to switch and the mode hidden); keeping the chip plus a narrower segmented control (still
  two controls with `Sun & shade` twice).

### D8. Attributions on an About page (user decision, 2026-09-29)
- **ⓘ button:** an `IconButton` (48 dp) with the Material Symbol `info`, alone in `MapLabels`'
  top-start corner, with the coordinates label and the offline notice stacked below it (user
  decision, 2026-09-29, during apply). In one row with the coordinates it did not fit next to the
  168 dp toggle on a ~352 dp phone: 8 + 48 + ~130 + 8 + 168 + 8 ≈ 370 dp. Stacked, only the
  ~130 dp label shares its height with the toggle column (322 dp). *Alternatives (asked):* the
  coordinates as the sun panel's first line; a 144 dp toggle, which fits ~352 dp only barely. The map's attribution labels and
  their `Column` at the bottom are removed; the sun panel then sits directly at the bottom.
- **About page:** a Compose screen (`AboutScreen`, new `app/.../about/AboutScreen.kt`) with the app
  name and version (`BuildConfig.VERSION_NAME`), the map attribution (opens
  `https://www.openstreetmap.org/copyright`), the elevation attribution (opens
  `MapterhornTiles.ATTRIBUTION_URL`), and the icon credit. Its entries (text and URL) come from a
  pure function, testable on the JVM.
- **Navigation:** `MainActivity` switches between `MapScreen` and `AboutScreen` on a
  `rememberSaveable` flag; `BackHandler` returns to the map. The map's view model is scoped to the
  activity, so camera, time and overlay survive the round trip. No navigation library for two
  screens.
- **Icons:** the four Material Symbols (`info`, `layers_clear`, `contrast`, `timelapse`) are copied
  as vector drawables into `res/drawable`, instead of adding the large
  `material-icons-extended` dependency. They are Apache License 2.0, hence the credit on the About
  page.
- *Alternative (asked):* show the attributions on the map at launch and collapse them on the first
  map interaction or after 5 s, which follows the collapse options of the OpenStreetMap
  Foundation's guidelines literally. The user chose the About page alone (risk below).

### D9. The heatmap's own coarse day (user decisions, 2026-09-29)
The first device check found the heatmap far too slow: it waited for the `Sun & shade` day (288
steps at 2 dp cells), then counted ~340k pixels per step. `Sun hours` now computes its own day:
- **Cells:** `SunShadeSweep` takes the cell size as a parameter (`cellDp`, default 2 dp for
  `Sun & shade`, 8 dp for the heatmap). The lines are `cellDp` apart, so an 8 dp grid has ¼ of the
  lines. Samples along a line keep the DEM's resolution, so each line costs about the same; the
  sweep is therefore about 4× cheaper. `OverlayRepository.grid` passes the cell size through.
- **Steps:** `DayOverlay` takes the step length (5 min for `Sun & shade`, 10 min for the heatmap):
  144 steps on a normal day, 138 or 150 on DST days. Night steps stay free (D12 of #5).
- **Which day runs:** the view model keeps one `DayOverlay` per mode for the visible area and
  date. Only the shown mode's day runs; the other's job is cancelled and resumes from its computed
  steps when its mode is shown again. In `Sun hours` the overlay flow computes no grid of the
  selected time either, as nothing shows it.
- **Cache:** `DayCache` keys days by area, date and cell size, under the same budget.
- **Counting:** the raster is one pixel per 8 dp (≈ 50 × 107 at 400 × 850 dp), about 64× fewer
  pixels than before. `renderSunHours` draws the image at one pixel per dp, reading the count of
  the raster pixel under each image pixel, so the hatching keeps its 2 dp stripes every 8 dp.
- **Estimate:** the day ~4× (cells) × 2× (steps) ≈ 8× faster than the 2 dp / 5-minute day, and
  the counting pass ~100× faster (64× fewer pixels, half the steps). Not measured; the device
  check records it.
- *Alternatives (asked):* measure first; 8 dp cells alone (~4×); all cores in `Sun hours`; a
  coarser counting raster only.

### D10. A greyscale map under the heatmap (user decisions, 2026-09-29)
- The OpenTopoMap `RasterLayer` gets `raster-saturation` −1 while the overlay shows `Sun hours`,
  and 0 while `Sun & shade` or `Off` (`MapLibreMap` takes the saturation; `mapSaturation` maps the
  toggle's option to it, tested on the JVM). First built for both modes; then the user decided
  that only the heatmap gets the greyscale map, and `Sun & shade` keeps the colours.
- The overlay's alpha rises from 0.45 to 0.6: `SHADE_ARGB` becomes `0x99455A64`, and the heatmap's
  bands take the same alpha (D3). Tuned in the device check, keeping band 0 equal to the shade tint.
- *Alternatives (asked):* greyscale in both modes (built first, then changed); always greyscale.

### Performance budget
| Interaction | Budget | How it is checked |
|---|---|---|
| Switching modes with a built heatmap | ≤ 100 ms to the new image | unit test (no computation) + device check |
| The heatmap's day (8 dp, 10 min) at map zoom 12 | ~20–30 s on the phone (estimate, D9) | log line `Overlay day …` of the heatmap's day, device check |
| Counting pass after the day's last step | ≤ 2 s on the phone at map zoom 12 | log line `Sun hours ... in N ms`, device check |
| Rendering the heatmap image | ≤ 100 ms on the phone | the same log line |
| Time slider in `Sun hours` mode | no heatmap work | unit test |
| Opening the About page and going back | ≤ 100 ms, map state kept | device check |

If the pass exceeds 2 s, the fallback is incremental counting (D2), which would be a user decision
in a follow-up. The whole day's duration at zoom 12 is also logged (the existing `Overlay day`
line) and recorded in the device check. It is what decides whether a partial heatmap is needed
later (proposal, Non-goals).

Device check (2026-09-30): confirmed by the user ("Now it is ok. phone check done") after the
coarse heatmap day (D9) and the greyscale map under the heatmap only (D10). The logcat timings
were not reported, so the day's duration and the counting pass remain unmeasured; the heatmap's
speed was accepted as it is on the device. A partial heatmap and incremental counting stay
unneeded (proposal, Non-goals).

### Verification strategy
- `SunHoursTest` (JVM, app): `ShadeGrid`s built through `SunShadeSweep` on flat or missing tiles,
  as `RenderOverlayTest` and `DayOverlayTest` already do (the `ShadeGrid` constructor is internal
  to `core`). It checks the counts per pixel (the spec's 57 / 2 / 229 example), that the
  off-grid time is excluded, that night steps are skipped, and that "every step unknown" holds.
- `HeatmapColourTest`: the band counts for 8 h 33 min and 15 h 51 min, band indices, the clamp at
  the last band, n = 1, strictly rising OKLab L for 32 bands, and alpha.
- `RenderSunHoursTest`: hatching only, hatching over colour, and colour.
- `SunFormatTest`: every `Sun hours` text of the spec.
- `MapViewModelTest`: the mode's default and saved state; no heatmap before completion; `Ready`
  after it; a time change does not change it; a pan or date change keeps the previous heatmap
  with `Computing`; switching back to a cached complete day gives `Ready` without computing the
  day again; switching modes does not restart the day.
- `OverlayNoticesTest`: the notices per mode.
- `OverlayToggleTest` (D7): `Off` / `Sun & shade` / `Sun hours` from `isOverlayOn` and
  `overlayMode`, and the actions each selection takes.
- `HeatmapLegendTest` (D7): the number labels with the unit once; opaque legend colours.
- `AboutEntriesTest` (D8): the attribution texts, their URLs and the version entry.
- `SunShadeGeometryTest` (core, D9): lines `cellDp` apart for 8 dp; the ridge's shadow edge within
  ±1 cell at 8 dp.
- `MapViewModelTest` (D9): `Sun hours` computes 144 steps at 8 dp and no 2 dp grid; switching back
  resumes the paused `Sun & shade` day; the progress 36 of 144 is 25 %; the cache keeps both days.
- `SunHoursTest`, `RenderSunHoursTest` (D9): counts on the 8 dp raster, the image at 1 px/dp.
- `MapColoursTest` (D10): greyscale for `Sun hours`, colour for `Sun & shade` and `Off`.
- Device check: Interlaken on 2025-12-21 at zoom 12 gives `Sun hours ≈` 5 h 23 min ± 20 min at
  the centre, and the timings of the budget.

## Risks / Trade-offs

- [8 dp cells blur the heatmap at cliffs and narrow ridges; 10-minute steps widen the tolerance to
  ±10 min per period boundary] → The panel keeps the tracer's exact periods; `Sun & shade` keeps
  2 dp and 5 minutes.
- [Two days per area and date in the cache] → The heatmap's day is ~1/32 of the other's size (1/16
  the cells, half the steps), so it barely touches the budget.
- [Greyscale hides the map's colour cues (water, forest) under the heatmap] → Only while it
  is on; `Off` restores the colours.

- [The counting pass is slow on the phone: ~100–190 daytime grids × ~340k `stateAt` calls] →
  Night steps are skipped, the time is logged and the budget is checked on the device; incremental
  counting is the prepared fallback.
- [The whole day takes minutes on the phone, so the heatmap feels unavailable] → The progress bar
  shows progress. The measurement decides whether to add a partial or preliminary heatmap (user
  decision: revisit only then) or reuse across pans (roadmap polishing).
- [Translucent colours mix with the map's own colours (forest green, glacier white, lakes)] → The
  exact value comes from the panel line. Alpha and stops are tuned on the device.
- [30-minute bands in June are 32 colours, and neighbouring bands look alike] → Labels every 2 h,
  and the panel gives the exact value. Band edges still act as contour lines.
- [The sun position of the map centre applies to every cell] → The same approximation as #5
  (≤ 1 min at the screen edges at zoom 11), far below the 5-minute steps.
- [A day of ~340k pixels adds ~1.4 MB to the cache per heatmap] → It is counted in the existing
  quarter-of-heap budget, so it costs cached days, not memory safety.

- [Attributions only behind the ⓘ button, never shown on the map (user decision)] → The
  OpenStreetMap Foundation's guidelines accept collapsed attribution reachable from an "(i)"
  button or an About option. Their collapse options (dismiss, first map interaction, after five
  seconds) suggest an initial display. If that is required, the D8 alternative adds it without
  changing the About page.
- [Icons copied into the app] → Four small vector drawables, credited on the About page; no new
  dependency to update.

## Migration Plan

None: a new mode that is off by default. Rollback is to revert the change; no stored data is
involved.
