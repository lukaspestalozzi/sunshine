# Proposal

## Why

The app answers its main question, when the sun shines here, in a small line among others, and the
time slider is hard to use: 288 positions of 5 minutes on about 330 dp are about 1 dp each, and its
track shows nothing about the day. The overlay's progress bar sits apart from the time it is about,
the Settings and Offline pages have no visible way back, the overlay toggle is three unlabelled
icons, and the crosshair's role is never explained. This is the last polishing step before v1.

Roadmap: implements entry #12, `polish-ui`, of `docs/roadmap.md`, after #10 `polish-overlay` and
#11 `overlay-pan-reuse`, which settled what the overlay's day computes and so what the time tape
shows.

## What Changes

- **Bottom panel, one redesign** (user decisions 2026-10-05 and 2026-10-06):
  - **Header:** the selected time as `Sun 21 Dec · 14:30`; the year only when it is not the
    current one, the UTC offset only on days with a clock change; `Date` and `Now` stay. Tapping
    the time opens a clock dialog for an exact time.
  - **Headline:** the sun periods of the selected day, today's `Sunshine 10:09–14:51,
    15:11–15:52` line, set large directly below the header.
  - **Time tape** replacing the slider: a horizontal strip dragged under a fixed needle, with fling,
    snapping to the step and tap to jump, about 70 dp per hour, stopping at the day's edges and
    covering its whole length. The strip is coloured per step: sun, terrain shade, night, unknown
    (hatched as on the map) and not computed yet. With the overlay on, it shows the shown mode's
    day at the crosshair cell and fills outward from the needle as the day is computed; with the
    overlay off or zoomed out, the horizon tracer's states at the crosshair.
  - **Details:** azimuth, elevation, civil dawn, sunrise, sunset, civil dusk, day length, the
    whole-day text, the altitude and the time zone move into a `Details` row, collapsed by default;
    its state is kept across launches.
- **Overlay status card:** the progress bar is removed; the tape shows the day's progress instead
  (**BREAKING** for the spec: "Overlay status card" item 3).
- **Navigation:** the Settings, Custom resolution and Offline pages get a top bar with a back
  arrow and the page's title.
- **Discoverability:** a hint card on first launch explaining the crosshair, tap to centre and the
  overlay modes, shown once. Tooltips on the toggle were dropped while applying: Material 3's tooltip
  is still an experimental API (user decision, 2026-10-07).
- **Tap to centre:** a single tap on the map moves the camera so that the tapped point is under the
  crosshair.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `time-selection`: "Time zone of the selected time" (header format), "Choose the time of day" (the
  tape replaces the slider); added "Time tape strip" and "Exact time".
- `sun-position`: "Sun information panel" (header, headline, tape and collapsible details).
- `point-sunshine`: "Sunshine in the information panel" (the periods as the headline).
- `sun-shade-overlay`: "Overlay status card" (no progress bar),
  "Overlay of the whole day" (progress on the tape).
- `sun-exposure-heatmap`: "Heatmap updates" (progress on the tape).
- `map-view`: "Selected location crosshair" (tap to centre); added "First-run hint".
- `settings`: "Settings page" (top bar), "Custom resolution" (top bar), "Stored settings" (the
  details' state and the hint).
- `offline-regions`: "Offline button" (the Offline page's top bar).

## Non-goals

- Crossing midnight on the tape, or a tape shorter than the whole day (user decisions).
- Scroll wheels or other time pickers besides the tape and the clock dialog (user decision).
- Translations: texts stay English and locale-independent, as today.
- A "now / until" sentence in the headline (user decision: the headline is the day's periods).
- Changing what is computed: the overlay's and heatmap's days, the horizon and the sun periods stay
  as they are.
- A launcher icon, a release build or a version number (after v1).

## Impact

- `app`, `map/`: a new time tape composable with its strip model (pure, unit-tested), the panel
  rebuilt around it (`SunPanel.kt`), the time header and headline formatting (`SunFormat.kt`), the
  clock dialog, the progress bar removed from `OverlayControl.kt`, the
  first-run hint, and tap to centre in `MapLibreMap.kt`. `MapViewModel` exposes the strip's states.
- `app`, `settings/` and `offline/`: top bars; two stored values (details expanded, hint shown).
- `core`: unchanged, except possibly a helper for the tracer's state at given instants if the app
  cannot use `sunshineAt` as is.
- No new dependencies and no experimental APIs (the clock dialog is the platform's; Material 3's
  time picker and tooltip are experimental in the current Compose BOM).
