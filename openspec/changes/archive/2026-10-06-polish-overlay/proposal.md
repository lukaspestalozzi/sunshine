# Proposal

## Why

Three rough edges of the overlay remain from #5 and #7. After a pan, every selected time the user
scrubs to waits for its own sweep, although the days computed before the pan still hold that time for most
of the screen. Offline, the panel says `unknown` where the overlay rightly says sun, because the
point tracer counts missing terrain even where it is too far away to rise above the sun. And the
heatmap's speed on the phone was accepted but never measured, so the decision on a partial heatmap
still has no numbers. Measuring such things today needs a cable and logcat; an on-screen debug
mode lets the user check timings and data on any installed build.

Roadmap: implements entry #10, `polish-overlay`, of `docs/roadmap.md`. Its scope grows by user
decision (propose session, 2026-10-05): the debug mode is added, as the tool for this change's
device measurements. The pan option is decided: show the earlier days' grids while the new day is
computed (no geographic tiles, no margin).

## What Changes

- `app`: **Overlay after a pan.** While the day of the new area is computed, a selected time it
  has not reached yet is shown from an earlier day of the same date and shade resolution whose
  area overlaps the screen, on that day's own terrain, instead of waiting with
  `Computing sun and shade …`. The new day replaces it as soon as it has that time. A pan still
  starts the new area's day, selected time first; the CPU used per pan is unchanged (user
  decision: "keep previous day").
- `core`: **Upper bound of an incomplete horizon.** Where terrain data is missing, the horizon
  also records the highest angle that terrain beyond the gap could reach (the Mont Blanc bound at
  the gap's distance). The point tracer no longer stops at missing data; like the sweep, it keeps
  the terrain found beyond it (user decision: "upper bound").
- `core`: **One sunshine rule for panel and overlay.** Sun when the sun's upper edge is above the
  upper bound, shade when it is at or below the angle found, unknown only in between. The panel's
  `Sunshine` row and the overlay's cells then agree offline.
- `app`: **Debug info** (Settings, new section `Debug`, before About): four switches, each off by
  default and in every build: `Timings`, `Tiles`, `Day state` and `Agreement check` (user
  decisions). While any is on, a box on the map shows those values. The agreement check, today
  logged in debug builds only, runs only while its switch is on.
- Device measurement: the heatmap's day and counting pass, and the overlay's day, are measured on
  the phone with the debug box and recorded in design.md. If the counting pass exceeds its 2 s
  budget, the work stops and the user decides on incremental counting or a partial heatmap
  (user decision: "record, then ask").

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `sun-shade-overlay`: "Overlay updates" shows an earlier day's grid of the selected time after a
  pan; "Unknown cells" decides sun above the upper bound of missing terrain.
- `point-sunshine`: "Sunshine at an instant" uses the upper bound, so that it agrees with the
  overlay.
- `terrain-horizon`: "Incomplete horizon" records the upper bound and keeps terrain found beyond
  missing data.
- `settings`: "Settings page" and "Stored settings" gain the `Debug` section with its four
  switches; a new requirement "Debug info" specifies the box on the map.

## Non-goals

- Geographic tiles or a margin around the view (user decision: rejected for v1). A pan still
  computes the new area's day, also after panning back to an earlier area; its earlier day only
  fills in meanwhile.
- Changes to the heatmap after a pan. It already stays on its terrain until the new one is built.
- Incremental counting or a partial heatmap. They are decided after the measurement, in a
  follow-up, only if needed.
- The time tape and every other UI redesign (roadmap #12, `polish-ui`).
- GPS follow mode.
- Exporting or sharing debug data, a log file, or debug values beyond the four groups.
- Removing the logcat lines of debug builds.

## Impact

- `core`: `HorizonProfile` (upper bounds), `HorizonTracer` (continues past gaps), `Sunshine.kt`
  (`sunshine` rule), `SunShadeSweep` (cell state). Their tests gain upper-bound cases.
- `app`: `MapViewModel` (fallback grid after a pan, debug values, agreement check by setting),
  `DayCache` (lookup of overlapping days), `OverlayRepository` and `SunshineRepository` (tile and
  timing values), a new `DebugInfo` collector and `DebugBox` composable, `Settings` /
  `SettingsStore` / `SettingsScreen` / `SettingsViewModel` (four switches), `MapScreen`
  (the box).
- No new dependencies. No migration: missing stored values take their defaults (off).
