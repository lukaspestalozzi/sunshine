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

### D2. Counting: one pass over the day's grids once the day is complete (user decision, 2026-09-28)
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
- Every band has alpha 0.45 (`0x73`, as `SHADE_ARGB`), so 0 h looks like the shade of
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

### D5. View-model state
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

### D6. UI (user decisions)
- **Mode control:** a Material 3 `SingleChoiceSegmentedButtonRow` with `Sun & shade` and
  `Sun hours`. It sits below the toggle chip and its progress bar and above the legend, and is
  shown only while the overlay is on. The chip keeps its label and its on/off meaning.
- **Legend:** in `Sun hours` mode, a horizontal bar of the n band colours on the legend's surface,
  with the labels `0 h`, `2 h`, … under the band where each whole 2 h begins. Its bands come from
  the sun panel's day length (map centre, selected date), so the scale shows while the day is
  still computed (apply, 2026-09-29). Below it is the `Unknown` hatching row, which it
  shares with the overlay's legend.
- **Notices:** `overlayNotice` takes the mode. In `Sun hours` it shows `Computing sun hours …`
  while the heatmap is `Computing`, and in both modes it shows `Zoom in to see sun and shade` when
  zoomed out.
- **Panel:** `formatSunHours(state, center)` gives the `Sun hours` value text. It gives `…` unless
  a `Ready` heatmap's area is the current visible area. Otherwise it reads the pixel under the map
  centre, which is the raster's centre pixel. Durations use the existing `formatDayLength` style
  (`5 h 20 min`); an unknown time under an hour is `10 min`.
- Strings live in `strings.xml`: `heatmap_mode_sun_and_shade`, `heatmap_mode_sun_hours`,
  `heatmap_computing`, `sun_panel_sun_hours`, and the value templates.

### Performance budget
| Interaction | Budget | How it is checked |
|---|---|---|
| Switching modes with a built heatmap | ≤ 100 ms to the new image | unit test (no computation) + device check |
| Counting pass after the day's last step | ≤ 2 s on the phone at map zoom 12 | log line `Sun hours ... in N ms`, device check |
| Rendering the heatmap image | ≤ 100 ms on the phone | the same log line |
| Time slider in `Sun hours` mode | no heatmap work | unit test |

If the pass exceeds 2 s, the fallback is incremental counting (D2), which would be a user decision
in a follow-up. The whole day's duration at zoom 12 is also logged (the existing `Overlay day`
line) and recorded in the device check. It is what decides whether a partial heatmap is needed
later (proposal, Non-goals).

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
- Device check: Interlaken on 2025-12-21 at zoom 12 gives `Sun hours ≈` 5 h 23 min ± 20 min at
  the centre, and the timings of the budget.

## Risks / Trade-offs

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

## Migration Plan

None: a new mode that is off by default. Rollback is to revert the change; no stored data is
involved.
