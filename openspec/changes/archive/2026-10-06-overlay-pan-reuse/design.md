# Design

## Context

See proposal.md for the motivation and the specs for the behaviour. Relevant current state:

- **Days.** `DayOverlay` (app) holds one `ShadeGrid` per step of one `MapArea`, computed by the
  `grid(area, sun, cellDp)` function (`OverlayRepository.grid`) with the sun of `area.center`;
  `compute(time)` serves the selected time, `computeRest`/`computeAll` the other steps on the day
  dispatcher, nearest first. Night steps use `sweep.night` (no terrain). Both overlay modes use it.
- **Cache.** `DayCache` keeps days by exact `(area, date, cellDp, stepMinutes)`, least recently
  used dropped; `overlapping(area, time, …)` (from #10) finds an earlier day for the display
  fallback after a pan. A camera rest stops the old day; it is not resumed unless selected again.
- **Users of a grid.** `renderOverlay` (`statesAt` over the raster), `countSunHours` (`stateAt` per
  pixel and step), the agreement check (`sampleCells`), `DayCache` budgets (`stateBytes`), the
  reconnect rule (`hasUnknown`), the debug box. All take `ShadeGrid`, a final class in `core`.
- **Tiles.** `OverlayRepository` keeps the last grid's tiles while the next area is "near" (same
  zoom, centre moved by at most half the smaller side). Parts of a panned area have other centres.
- **Spike** (`investigations/overlay-pan-reuse.md`): the uncovered half after a half-screen pan
  costs 43–63 % of a full sweep on the desktop JVM, whatever the pan direction.

## Goals / Non-Goals

**Goals:**
- A step's grid of the new area is a combination that every user of a grid reads as one grid;
  drawing and counting stay within their budgets.
- Each part is an ordinary sweep of an ordinary `MapArea`; no change to `SunShadeSweep`.
- Pure, unit-tested geometry for the uncovered rectangles and the choice of the earlier day.

**Non-Goals:**
- Several reused days per area, reuse after zooming in (user decisions).
- Sharing upwind work between parts or with the earlier day.

## Decisions

### D1. Reuse at most one earlier day, per step (user decisions, 2026-10-06)
When the camera rests, `DayCache.reusable(area, date, cellDp, stepMinutes, online)` picks among the
cached days other than the new one: same date, cell size and step; map zoom `z_old` with
`z_new ≤ z_old ≤ z_new + 1`; no unknown cells while online; the largest covered share of the new
area, which must be at least ¼. The new `DayOverlay` gets it as `base`. At each step:
- base has a grid there and the step is not a night step → compute the uncovered parts only and
  store a combined grid;
- otherwise → compute the whole area, as today.

The decision is taken when the step is computed, so a base still lacking steps (the pan came while
it was computing) is used wherever it can be. The new day keeps the base's usable grids (daytime,
not combined twice), not the base day itself, so that days do not hold on to each other; they count
in its bytes until combined into a step, and are released once the day is complete (review
finding). `reusable` makes the chosen day the most recently used.

*Alternatives (asked):* same zoom only (any pinch recomputes all); any zoom (cells larger than the
cell size after zooming in); several days (rectangle cover, more grids per step); complete days only
(no reuse after the common "pan while computing").

### D2. The uncovered parts: up to four rectangles
In the new area's Web Mercator dp frame (north-up, its zoom), the old area's bounds give a
rectangle; the new area minus it is split into at most four rectangles: a full-width strip above, a
full-width strip below, and left and right pieces in the middle band. Each is a `MapArea` (its own
centre, the new zoom, its width and height in dp); a piece narrower than one cell is widened to one
cell into the covered part (overlap is harmless). `uncovered(new, old)` is a pure function with the
covered share as a by-product (`Reused <p> %` in the debug box, and the ¼ threshold).

### D3. A grid interface and a combined grid in `core`
`ShadeGrid` implements a new interface `StepGrid` (`area`, `stateAt`, `statesAt`, `sampleCells`,
`hasUnknown`, `stateBytes`). `CombinedGrid(area, base: StepGrid, parts: List<ShadeGrid>)`:
- `stateAt(p)`: `base.stateAt(p)` if `p` lies within the base's area bounds, else the first part
  that has a state there;
- `statesAt(latitudes, longitudes)`: the raster is north-up, so the base's bounds select a
  sub-range of rows and columns; the base and each part are asked only for their own sub-ranges
  and the results merged, so drawing costs about one pass over the raster, not one per grid;
- `sampleCells(n)`: from the base and the parts in proportion to their share of the area;
- `hasUnknown`: any; `stateBytes`: the parts' plus the base's (the base's grids count in both days,
  as the spec says).

A base grid may itself be combined (pan after pan). To bound the depth, a step whose base grid is
already combined twice is computed whole. The users in `app` take `StepGrid` instead of
`ShadeGrid`; `countSunHours` switches to `statesAt` per step (faster, same counts).

*Alternative:* one bitmap per part on the map: several MapLibre image sources, and the heatmap
counts would still need one lookup per pixel.

### D4. Sun position per computed area (user decision)
Each part is swept with the sun of its own centre, the base keeps its own. The spec states this; at
map zoom 11 with a reused day up to ¾ of a screen away, it is equivalent to at most 2 min of time.

### D5. Tiles kept across parts
`OverlayRepository` keeps the last grid's tiles while the next area's bounds intersect the kept
area's bounds at the same zoom (instead of "centre moved by at most half the smaller side"), so
consecutive parts and the next step reuse the tiles already in memory.

### D6. Debug and logging
`DayOverlay.reusedShare` (0 without a base) feeds `Reused <p> %` in the `Day state` group. The parts
of one step are computed within a `GridTileTally` coroutine context element, so `Grid tiles` counts
the tiles of the whole combined step; the agreement check traces its cells with `checkProfile`, which
neither replaces the selected location's `Horizon` lines nor caches its profiles (review findings). The
`Overlay day` log line and the `Day` timing line are unchanged, so a pan's day duration compares
directly with a fresh day's.

## Performance budget

| Computation (user-triggered) | Budget | Check |
|---|---|---|
| A `Sun & shade` day after a half-screen pan, map zoom 12 | ≤ 70 % of a fresh day of the same area (spike: 43–63 %) | `Day` line in the debug box, device check |
| Choosing the earlier day | ≤ 1 ms for 50 cached days | unit test |
| Overlay image of a combined grid | ≤ 100 ms on the phone, as for one grid | `Grid … image` line, device check; JVM test: at most 1.5× a single grid's image |
| Heatmap counting over a combined day | ≤ 2 s on the phone | `Sun hours` line, device check |
| A time change to a computed step of a combined day | ≤ 100 ms | existing spec scenario, device check |

### Device results (user's phone, 2026-10-06; `Normal`, map zoom 12.0, 411×891 dp, day 2026-10-09)

| Day | Centre | `Reused` | `Day` line | `Grid … image` |
|---|---|---|---|---|
| Fresh | 46.6778° N, 8.1365° E | 0 % | 288 steps (146 night) 235.2 s | 441 ms, 62 ms |
| After a pan | 46.6552° N, 8.0944° E | 33 % | 288 steps (146 night) 47.6 s | 493 ms, 85 ms |

- The pan was 245 dp west and 192 dp south (0.6 and 0.2 of the screen), not half a screen east:
  the earlier area covers (411 − 245) · (891 − 192) / (411 · 891) = 32 % of the new one, as shown.
- The reused day took 20 % of the fresh one. The fresh day loaded its tiles from the store (the
  first day there), so this overstates the saving; against round 3's fresh day of polish-overlay
  (83.8 s, another area) it is 57 %. Both are within the 70 % budget. An earlier pan near the first
  centre: `Reused 35 %`, `Day` 53.0 s, image 72 ms.
- The overlay covered the whole screen; the image stays within 100 ms.
- The debug box showed `Day 287/288 steps` for the finished day: the progress stopped before it
  saw the last step. Fixed: the day state is recorded once more when a day finishes.

## Risks / Trade-offs

- [A visible seam between the base and a part] → Both sides are correct to their sun position (≤ 2
  min) and cell grid; a seam can show where a shadow edge crosses it. Checked on the device.
- [Memory: a base's grids stay referenced after the base leaves the cache] → They count in both
  days' bytes, so the budget is conservative; depth is bounded (D3).
- [Strips are expensive per area: each part sweeps its own upwind lines] → The ¼ threshold keeps
  reuse to cases where it pays; the device check measures the half-screen case.
- [Agreement check on combined grids] → `sampleCells` draws from all grids; the check stays valid
  per cell with its own sun position.

## Migration Plan

No stored data. Rollback: revert the change's commits.
