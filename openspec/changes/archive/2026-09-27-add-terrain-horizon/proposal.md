# Proposal

## Why

The app knows where the sun is (#2) and how high the ground is (#3), but it does not know
whether the sun actually reaches the selected location. The core question of hikers in the Alps is
"when does the sun shine here?", and mountains shift that by hours. The spike
`investigations/terrain-horizon-algorithms.md` measured how to compute this accurately and
cheaply:
- a horizon profile per location that is valid for every date;
- distance-dependent tile zoom with exact early termination;
- results checked against documented sunless seasons (Viganella, Bristen, Vicosoprano,
  Rattenberg, Rjukan).

This change also avoids two failure modes of the first implementation: horizon rays with only
9 samples, and first/last sunshine interpolated across large gaps of azimuth.

Roadmap: implements entry #4, `add-terrain-horizon`, of `docs/roadmap.md`.

## What Changes

- `core`: a horizon profile for a location, as the horizon elevation angle every 0.25° of
  azimuth. It is traced by rays with distance-dependent zoom (3 m near the observer, coarser
  farther out, up to 150 km) and exact early termination. It accounts for earth curvature and
  terrain refraction. Where tiles are missing, the profile records a lower bound instead of a
  value.
- `core`: the sun periods of a day at a location: every interval in which the sun's upper edge is
  above the terrain horizon, including several periods per day. Also the sunshine state at an
  instant: sun, shade or unknown. The result is unknown only where missing data could change it.
- `app`: DEM tiles at zooms 10–14, falling back to the finest published zoom where a zoom does
  not exist. One shared, compact in-memory tile cache (16-bit heights) serves both the altitude
  (#3) and the horizon.
- `app`: the sun panel shows the day's sunshine periods, e.g. `Sunshine 10:09–14:51, 15:11–15:52`.
  The sun direction line becomes terrain-aware: solid when the sun shines at the location, dashed
  when it is in shade, dotted when unknown.

## Capabilities

### New Capabilities

- `terrain-horizon`: the horizon profile of a location, including its accuracy, data needs,
  missing-data handling and limits.
- `point-sunshine`: sun periods of the selected day and the sunshine state at the selected time,
  and their display in the sun panel.

### Modified Capabilities

- `sun-position`: "Sun direction line" is now drawn by terrain-aware sunshine (sun / shade /
  unknown) instead of by the astronomical horizon.

## Non-goals

- Shade and sunshine for the whole visible area (overlay, roadmap #5) and hours of sun per cell
  (heatmap, roadmap #7).
- Downloading regions for offline use (roadmap #6). Offline results depend on the disk cache.
- Refraction below 0° geometric sun elevation (user decision). The known limitation of #2 stays:
  where the terrain horizon is at or below 0°, e.g. on summits, first sunshine comes out 3–5 min
  late and last sunshine 3–5 min early.
- Trees, buildings and holes in rock (a DEM is a height field).
- A horizon graphic (skyline and sun path) in the UI.
- A yearly calendar or sunless-season display.

## Impact

- **Code:** in `core`, new horizon tracing and sun-period computation. In `app/elevation`:
  multi-zoom tiles, zoom fallback, and a shared compact tile cache replacing the 8-tile float LRU
  of #3. In `app/map`: sunshine state in `MapViewModel`, a panel row, and a terrain-aware
  `SunLine`.
- **Network and memory:** 18–53 tiles (about 3–7 MB) per new location, from the disk cache when
  available. The in-memory tile cache holds up to 64 tiles, about 32 MB.
- **Dependencies:** none new.
- **Tests:** `core` tests on synthetic terrain with exact expected angles and periods, and
  missing-tile cases. `MapViewModel` tests. The documented oracle locations are checked on a
  device.
- **Docs:** `docs/roadmap.md` entry #4. `investigations/` already holds the spike.
