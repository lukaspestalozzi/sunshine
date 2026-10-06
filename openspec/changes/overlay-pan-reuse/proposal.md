# Proposal

## Why

Every pan computes the whole day of the new screen again, although after a half-screen pan half of
it is already computed: about 84–92 s of background CPU per pan with `Sun & shade` on the user's
phone (design.md of `polish-overlay`, device rounds 2 and 3). A spike measured that computing only
the uncovered half costs 43–63 % of a full recompute, whatever the pan direction relative to the
sun (`investigations/overlay-pan-reuse.md`).

Roadmap: implements entry #11, `overlay-pan-reuse`, of `docs/roadmap.md`, added after #10 by user
decision (2026-10-06) and done before #12, whose time tape shows what has been computed.

## What Changes

- `app`: **Reuse after a camera move.** When the camera rests on a new area, the cached day of the
  same date, cell size and step that covers the largest share of it is reused, if it covers at
  least a quarter of the new area and was computed at the new zoom or up to one level higher (the
  user zoomed out by up to one level; its cells are then no larger than the cell size, and its
  terrain data at least as fine). At each step that day has, only the uncovered part of the new area (one to four
  rectangles) is computed and combined with it; at steps it lacks, the whole new area is computed
  (user decisions, 2026-10-06: zoom rule, one day, per step).
- `core`: **Combined grids.** A step's grid of the new area can be made of the earlier day's grid
  and the grids of the uncovered parts; looking up a point, drawing the image and counting the
  heatmap work on it as on one grid.
- **Sun position per part.** Each part keeps the sun position of the centre of the area it was
  computed for; the spec stated one sun position, the map centre's, for every cell. After a pan by
  half a screen this differs by less than 1 min of sun time (user decision: accept and state it).
- Both overlay modes: the heatmap's own day is reused the same way.
- `app`, debug box: the `Day state` group shows the share of the shown day's area taken from an
  earlier day.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `sun-shade-overlay`: "Sunshine of a cell" (sun position and data resolution of the area a cell was
  computed for) and "Overlay of the whole day" (reuse after a camera move).
- `sun-exposure-heatmap`: "Sun hours of a cell" (the accuracy uses the sun position of the area a
  cell was computed for).
- `settings`: "Debug info" (a `Reused` line in `Day state`).

## Non-goals

- Reusing several cached days for one area, or reusing after zooming in (user decisions).
- Reusing work within a step: a part's lines are swept from their own upwind start, as for any area.
- Geographic tiles or a margin around the screen (rejected in #10).
- Keeping computed days across app restarts.
- Any UI change beyond the debug box line (roadmap #12, `polish-ui`).

## Impact

- `core`: a grid interface implemented by `ShadeGrid` and a combined grid (`SunShade.kt`), with
  `stateAt`, `statesAt`, `sampleCells`, `hasUnknown` and `stateBytes`.
- `app`: the rectangles of an area that another does not cover (`map/`), the choice of the earlier
  day in `DayCache`, per-step composition in `DayOverlay`, `MapViewModel` passing the earlier day,
  rendering and counting over the grid interface, the `Reused` value in `DebugInfo`.
- No new dependencies, no stored data.
