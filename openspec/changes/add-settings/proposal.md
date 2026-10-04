# Proposal

## Why

The app fixes everything a hiker might want to adjust: the overlay's resolution, how strongly it
tints the map, the coordinate format, where the map opens and how much storage browsed tiles may
take. A hiker with a slower phone cannot trade detail for speed, a Swiss hiker cannot read the
coordinates in the grid of their paper map, and every launch starts over the Swiss Alps instead of
where they left off. A settings page lets each hiker adjust these.

Roadmap: implements entry #8, `add-settings`, of `docs/roadmap.md`. Its scope changes by user
decision (explore session, 2026-10-04): the download rate limit stays fixed, because it protects
the tile servers; the region map depth is deferred, because each region would have to record its
own depth; renaming regions stays with the Offline page. The resolution of the shade calculation,
the coordinate format, the start view, the overlay opacity and keeping the screen on are added.

## What Changes

- `app`: **Settings page.** A gear button replaces the ⓘ button in the map's top-left row, as a
  fourth button does not fit beside the overlay toggle on 360 dp screens (user decision). It
  opens the Settings page, with the sections Display, Map, Calculation and Storage. The About
  content (version and attributions) moves to the bottom of the Settings page (user decision).
- `app`: **Coordinates** (Display): `Decimal` (today's format), `Degrees, minutes, seconds` to
  0.1″, or `Swiss grid LV95` (user decisions). LV95 is shown inside the LV95 perimeter; outside,
  the decimal format is shown instead.
- `core`: the WGS84 → LV95 conversion by swisstopo's approximate formulas (better than 1 m in
  Switzerland), tested against swisstopo's worked example.
- `app`: **Keep screen on** (Display): off by default; while on, the screen does not turn off
  while the map screen is in front (user decision).
- `app`: **Start at** (Map): `Last view` (default), `My location` or `Alps overview` (today's
  view) (user decisions). The last view, i.e. map centre and zoom, is kept across launches.
  `My location` asks for location access when it is chosen and centres the map on the first fresh
  position after launch, unless the user has moved the map already; otherwise the map stays at
  the last view.
- `app`: **Overlay opacity** (Map): 20–90 % in steps of 10, default 60 % (today's tint), for both
  overlay modes. The legends stay opaque (user decisions).
- `app`: **Shade resolution** (Calculation): one preset for both modes, `Fast`, `Normal`
  (default, today's values), `Detailed` or `Custom`, setting each mode's cell size and time step.
  `Custom` is edited on a sub-page within fixed ranges (user decisions).
- `app`: **the time slider follows the overlay's step.** While `Sun & shade` is shown, the slider
  moves in that mode's step, and the selected time is rounded to the nearest step when
  `Sun & shade` is selected, when `Now` is tapped and when the preset changes, so that the shown
  time is always a computed step (user decision).
- `app`: **Browsed tiles** (Storage): one limit for map tiles and DEM tiles each: 128, 256, 512
  (default), 1024 or 2048 MiB. Lowering it removes the least recently used browsed tiles at once
  (user decisions). **Clear browsed tiles** removes every browsed tile without a dialog; region
  tiles are kept. It is disabled while a region downloads (user decisions).
- `app`: settings stored with DataStore Preferences (user decision), read before the map screen
  is shown.

## Non-goals

- Dark mode and Material You colours (user decision: dropped from the scope).
- A configurable download rate limit or region map depth; renaming regions.
- Units (feet), a 12-hour clock, a colour-blind palette, the eye height, the time zone, the text
  size, the language.
- Keeping the selected time or the overlay mode across launches: only the map centre and zoom
  are kept.
- Coordinate systems other than WGS84 and LV95 (e.g. UTM); LV95 beyond its perimeter; the
  rigorous LV95 transformation.
- A confirmation dialog for clearing browsed tiles (user decision).
- Settings for the About content; the About page as a separate page.

## Capabilities

### New Capabilities

- `settings`: the Settings page and its button, its sections and the stored settings: keep
  screen on, the shade resolution presets and `Custom`, and their defaults and persistence.

### Modified Capabilities

- `map-view`: "Default viewport" follows `Start at`; "Selected location crosshair" shows the
  chosen coordinate format; "About and attributions" moves to the bottom of the Settings page and
  the gear button replaces ⓘ; "Missing map tiles" lists the gear button.
- `time-selection`: "Choose the time of day" moves in the `Sun & shade` step while that mode is
  shown; "Return to now" rounds to it.
- `sun-shade-overlay`: "Sunshine of a cell" and "Overlay of the whole day" use the preset's cell
  size and step; "Overlay appearance" uses the chosen opacity and avoids the gear button instead
  of ⓘ; "Overlay status card" likewise.
- `sun-exposure-heatmap`: "Sun hours of a cell" uses the preset's cell size and step; "Colour
  scale" uses the chosen opacity.
- `offline-regions`: "Kept tiles" uses the chosen limit and gains clearing; "Offline button" sits
  right of the gear button; "Storage usage" states the chosen limit.
- `gps-location`: "Location permission" is also asked when `My location` is chosen; "Centre on
  the position" allows the one move at launch of `Start at` `My location`; "Location button"
  avoids the gear button instead of ⓘ; "Position dot", "Wait for a position" and "Location updates
  only while visible" name the Settings page instead of the About page.
- `sun-position`: "Sun information panel" avoids the gear button instead of ⓘ.

## Impact

- `app`: new `settings/` package (Settings page and view model, the settings store, the `Custom`
  sub-page); `MainActivity` (the Settings page replaces the About page, screen-on flag);
  `about/AboutScreen.kt` (its entries move into the Settings page); `map/MapOverlay.kt` (gear
  button); `map/CoordinateFormat.kt`; `map/MapViewModel.kt` (start view, presets, slider step,
  rounding); `map/DayOverlay.kt`, `map/DayCache.kt` (step in the cache key); `map/OverlayImage.kt`,
  `map/HeatmapBands.kt` (opacity); `map/TimeSelection.kt`, `map/SunPanel.kt` (slider step);
  `offline/AmbientLimit.kt`, `offline/DemTileStore.kt` (configurable limit, clearing);
  `offline/OfflineViewModel.kt` (the limit's text); `SunshineApp.kt` (wiring); strings and one
  Material Symbols icon (settings gear).
- `core`: new `SwissGrid.kt` (WGS84 → LV95); `OfflineArea.kt` unchanged.
- Dependency: `androidx.datastore:datastore-preferences` (new).
- `docs/roadmap.md`: entry #8's scope and status; `CLAUDE.md`: the new files.
