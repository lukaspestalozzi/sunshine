# Roadmap

Ordered list of planned OpenSpec changes. Each row becomes one change under `openspec/changes/`
(proposal → review → apply → verify → archive). Open decisions are asked in the change's
`design.md` before it is applied.

| # | Change | Capabilities | Scope | Open decisions | Status |
|---|--------|--------------|-------|----------------|--------|
| 1 | `add-app-shell` | map-view | `core` + `app` Gradle modules, CI, MapLibre map with OpenTopoMap tiles, default view on the Swiss Alps, crosshair = selected location, attribution | resolved in its design.md | archived 2026-09-26 |
| 2 | `add-sun-position` | sun-position, time-selection | Sun azimuth/elevation and astronomical sunrise/sunset (commons-suncalc) for the selected location and day; date/time controls; info panel | resolved in its design.md | archived 2026-09-26 |
| 3 | `add-elevation-data` | elevation-data | DEM tiles (Mapterhorn Terrarium, zoom 12; spike: `investigations/dem-source-evaluation.md`): fetch on demand, decode, disk cache; interpolated elevation at a point; explicit "unknown"; altitude in the sun panel | resolved in its design.md | in progress |
| 4 | `add-terrain-horizon` | terrain-horizon, point-sunshine | 360° horizon profile (earth curvature + refraction); sun visible now; terrain-aware first/last sunshine; Interlaken oracles | sampling strategy; performance budget; refraction at or below 0° (the sun elevation from #2 has none there, so first/last sunshine is 3–5 min pessimistic where the terrain horizon is ≤ 0°, e.g. summits) | planned |
| 5 | `add-sun-shade-overlay` | sun-shade-overlay | Sunlit/shaded state for the whole visible area at the chosen time | algorithm (per-cell horizon vs. sweep); rendering | planned |
| 6 | `add-offline-regions` | offline-regions | Download map + DEM tiles per region, per-region delete, storage usage, fully offline use | fixed region list vs. custom area; OpenTopoMap bulk-download policy | planned |
| 7 | `add-sun-exposure-heatmap` | sun-exposure-heatmap | Hours of direct sun per cell for a day, reusing horizon data | — | planned |

Post-1.0 candidates: GPS location, bookmarks, time playback animation, home-screen widget,
photo planning mode.

Tooling follow-up: reintroduce detekt once detekt 2.0 is stable (1.23.x does not support
Kotlin 2.4).

## Failure modes of the first implementation (must not repeat)

Found while cataloguing the discarded code; the relevant change should specify against them.

- Missing elevation was silently treated as flat ground / 0 m, so the app reported "visible"
  when it did not know (→ changes 3, 4).
- Horizon rays used only 9 samples (100 m … 50 km, a 30 km gap at the end) (→ 4).
- First/last sunshine interpolated horizon angles linearly across gaps of up to ~100° of
  azimuth (→ 4).
- Overlays stopped at a hard cap of 500 points, i.e. ~13 % of the view at zoom 12, without
  telling the user (→ 5).
- The heatmap recomputed a full terrain ray per cell for every 30-minute step (→ 7).
- Downloading a region stored map tiles only, no elevation, so it did not work offline (→ 6).
- Deleting one region deleted the tiles of all regions (→ 6).
- Times used the device time zone and sunrise was searched from a 12:00 UTC anchor (→ 2).
