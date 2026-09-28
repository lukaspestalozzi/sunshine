# Proposal

## Why

The app tells the user when the sun shines at one point, the crosshair (#4). Hikers plan routes,
not points, so they need to see at a glance which slopes, valleys and paths lie in the sun at the
chosen time. The spike `investigations/sun-shade-overlay-algorithms.md` found an approach that is
exact to the DEM and fast enough to recompute on every pan or time change. It is a convex-hull
sweep along lines parallel to the sun direction:
- it agrees with the point tracer in ≥ 99.87 % of cells;
- one instant of 350k cells takes 117–202 ms on 3 desktop JVM threads;
- it avoids the legacy failure mode of an overlay silently capped at 500 points (about 13 % of
  the view).

Roadmap: implements entry #5, `add-sun-shade-overlay`, of `docs/roadmap.md`.

## What Changes

- `core`: a sun-shade grid for a rectangular area and one sun position. Every cell is sun, shade
  or unknown, decided against the same terrain horizon as the point tracer:
  - earth curvature and refraction;
  - terrain up to 150 km away, with distance-dependent zoom;
  - lower bounds where data is missing.

  It is computed by a convex-hull sweep along great-circle lines towards the sun. It uses an exact
  upwind cut from the sun's elevation and shares far-field hulls between neighbouring lines.
- `app`: a map overlay of that grid over the whole visible area at map zoom ≥ 11. There is no cap
  on the number of cells. Shade is tinted, sun is left clear, and unknown is hatched.
- `app`: a toggle button (off by default) with a legend, and a notice when the overlay is on but
  the map is zoomed out below 11.
- `app`: the overlay is recomputed when the camera rests, when the selected date changes, and when
  the network returns with cells still unknown. After a pan, the previous overlay stays on its area.
  After a time or date change, the previous overlay also stays until the new one is ready, and a
  "computing" notice says that it belongs to another time (user decision, revised 2026-09-27).
- `app`: the whole selected day is computed (user decision, 2026-09-27).
  - The selected time comes first, on all cores.
  - Then every 5-minute slider step of the day follows in the background, on half the cores,
    nearest to the selected time first.
  - Steps with the sun far below every horizon need no terrain work.
  - Moving the slider to a time already computed shows its overlay at once.
  - A thin progress bar under the toggle shows how much of the day is computed (user decision,
    2026-09-28).

## Capabilities

### New Capabilities

- `sun-shade-overlay`: sun, shade or unknown for every cell of the visible map area at the
  selected time, together with its accuracy, missing-data handling, zoom range, appearance, toggle
  and update behaviour.

### Modified Capabilities

(none — the overlay reuses the horizon definition of `terrain-horizon` and the sunshine rule of
`point-sunshine` without changing them)

## Non-goals

- Showing hours of sun per cell (heatmap, roadmap #7), although the day's overlays now exist.
- Keeping a computed day across app restarts or after the visible area or the date changes.
- Downloading regions for offline use (roadmap #6). Offline, the overlay depends on the DEM disk
  cache and shows unknown where tiles are missing.
- The overlay below map zoom 11 (user decision).
- A per-cell sun azimuth. The whole visible area uses the sun position of the map centre. At map
  zoom 11 this is equivalent to shifting the time by ≤ 1 min at the screen edges.
- Trees, buildings and holes in rock (a DEM is a height field), as in #4.
- Refraction below 0° geometric sun elevation. The #2/#4 limitation stays.
- GPU rendering or computation. The spike found the CPU sweep fast enough.

## Impact

- **Code:**
  - `core`: new sweep: line geometry in a gnomonic frame, the hull, the upwind cut, far-field
    bundling, and the tile requests of the grid.
  - `app`: an overlay repository fed by the shared `TileCache`; overlay state in `MapViewModel`;
    a MapLibre `ImageSource` layer in `MapLibreMap`; the toggle, legend and notices in the map UI.
- **Network and memory:**
  - A new screen at map zoom 12 needs about 45–70 DEM tiles (about 7–10 MB on a cold cache).
    Most of that is z14 in the visible area; the rest lies upwind.
  - While a grid is computed, its tiles stay referenced (≤ about 35 MB), in addition to the
    64-tile cache.
  - The day adds up to about 5 MB of packed cell states, and the upwind tiles of every sun
    direction of the day: up to about 50 more z10/z11 tiles (about 5–8 MB) on a cold cache.
  - Off by default, so users who don't turn it on pay nothing.
- **CPU and battery:** about 20–110 s of background CPU per area and day on half the cores (an
  estimate from desktop measurements; the phone is unmeasured). It runs only while the overlay is
  on, and every new area or date cancels it.
- **Dependencies:** none new. MapLibre 13.6.1 already has `ImageSource`.
- **Tests:**
  - `core` tests on synthetic terrain with exact shadow geometry, curvature, missing tiles and the
    upwind cut;
  - a `core` test of the sweep against `HorizonTracer` at sample cells;
  - `core` tests for the night grid and the packed states;
  - `MapViewModel` tests for update and staleness rules and for the day's order, parallelism and
    restarts;
  - a device check at Lauterbrunnen and Interlaken.
- **Docs:** `docs/roadmap.md` entry #5 and `CLAUDE.md` (new `core` file). `investigations/`
  already holds the spike.
