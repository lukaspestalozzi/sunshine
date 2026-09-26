# Proposal

## Why

The app shows a map but nothing about the sun yet. Every later feature (terrain horizon,
first/last sunshine, overlays, heatmap) needs the sun's direction and height for a location and
an instant, and a way for the user to choose that instant. This change delivers both. It is also
where the time-handling failure modes of the first implementation must be avoided: an unlabelled
device time zone, and sunrise searched from a 12:00 UTC anchor.

Roadmap: implements entry #2, `add-sun-position`, of `docs/roadmap.md`.

## What Changes

- `core`: sun position (azimuth, apparent elevation) for a location and an instant, and the sun
  events of a calendar day (civil dawn, sunrise, sunset, civil dusk, day length), computed with
  commons-suncalc. Days without some of these events (polar day and night, days where the sun
  sets but does not rise) have an explicit result; no event is ever invented.
- `app`: a selected time (date + time of day), in the device time zone and labelled with it.
  It starts at the current time. Controls: a date picker, a time slider over the selected day in
  5-minute steps, and a "Now" button.
- `app`: an info panel with sun azimuth and elevation at the selected time and location, the
  day's civil dawn, sunrise, sunset, civil dusk and day length.
- `app`: a line from the crosshair pointing in the sun's azimuth, drawn differently when the sun
  is below the horizon.
- The map becomes north-up and flat: rotation and tilt gestures are disabled, so that screen
  "up" is always north and the sun line's angle equals the azimuth.

## Capabilities

### New Capabilities

- `sun-position`: sun azimuth and elevation at the selected location and time, the day's sun
  events and day length, their display in the info panel, and the sun direction line.
- `time-selection`: the selected date and time, its time zone and labelling, and the controls to
  change it.

### Modified Capabilities

- `map-view`: new requirement that the map is always north-up and never tilted.

## Non-goals

- Terrain: elevation, horizon, terrain-aware sunshine (roadmap #3, #4). Sun events here are
  astronomical: sea-level observer, mathematical horizon.
- The time zone of the selected location. Times use the device time zone, always labelled.
- A clock-following "live" mode: "Now" sets the time once.
- Golden hour, nautical/astronomical twilight, solar noon, moon.
- Time playback animation (post-1.0 candidate).
- Remembering the selected time across app launches.

## Impact

- **Code:** new sun types and functions in `core`; `app/map` gains time state in `MapViewModel`,
  time controls, info panel and sun line composables; `MapLibreMap` disables rotation and tilt.
- **Dependencies:** `org.shredzone.commons:commons-suncalc` 3.11 in `core` (pure Java, ~57 kB,
  no transitive dependencies).
- **Tests:** `core` reference-value tests (Interlaken oracles from `investigations/`, plus polar
  and DST days); `MapViewModel` tests for time selection.
- **Docs:** `docs/roadmap.md` records the decisions for entry #2 and the refraction limitation
  handed to entry #4.
