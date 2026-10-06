# Roadmap

Ordered list of planned OpenSpec changes. Each row becomes one change under `openspec/changes/`
(proposal → review → apply → verify → archive). Open decisions are asked in the change's
`design.md` before it is applied.

| # | Change | Capabilities | Scope | Open decisions | Status |
|---|--------|--------------|-------|----------------|--------|
| 1 | `add-app-shell` | map-view | `core` + `app` Gradle modules, CI, MapLibre map with OpenTopoMap tiles, default view on the Swiss Alps, crosshair = selected location, attribution | resolved in its design.md | archived 2026-09-26 |
| 2 | `add-sun-position` | sun-position, time-selection | Sun azimuth/elevation and astronomical sunrise/sunset (commons-suncalc) for the selected location and day; date/time controls; info panel | resolved in its design.md | archived 2026-09-26 |
| 3 | `add-elevation-data` | elevation-data | DEM tiles (Mapterhorn Terrarium, zoom 12; spike: `investigations/dem-source-evaluation.md`): fetch on demand, decode, disk cache; interpolated elevation at a point; explicit "unknown"; altitude in the sun panel | resolved in its design.md | archived 2026-09-26 |
| 4 | `add-terrain-horizon` | terrain-horizon, point-sunshine | 360° horizon profile (earth curvature + refraction; spike: `investigations/terrain-horizon-algorithms.md`); sun periods of the day and sunshine now; Mapterhorn zooms 10–14 in a shared 64-tile cache; Interlaken oracles | resolved in its design.md (refraction at or below 0° kept as a known limitation by decision; year clamp dropped) | archived 2026-09-27 |
| 5 | `add-sun-shade-overlay` | sun-shade-overlay | Sun/shade/unknown for every cell of the visible area at the chosen time, map zoom ≥ 11, toggle off by default; the whole day is computed in the background, so scrubbing the slider is instant (convex-hull sweep; spike: `investigations/sun-shade-overlay-algorithms.md`) | resolved in its design.md (reuse across pans deferred to polishing) | archived 2026-09-28 |
| 6 | `add-offline-regions` | offline-regions | Every fetched map and DEM tile kept (browsed limit 512 + 512 MiB, least recently used out); download of the visible area at map zoom ≥ 11 (map tiles to z17, DEM with the full 150 km margin), rate-limited to 5 requests/s and 2 in flight per server, in the background; per-region delete; storage usage; fully offline use | resolved in its design.md (visible area instead of a region list; the user contacts OpenTopoMap and Mapterhorn separately; areas across the 180° meridian are refused) | archived 2026-10-02 |
| 7 | `add-sun-exposure-heatmap` | sun-exposure-heatmap | Hours of direct sun per cell for the selected day, as a second overlay mode counted from the day's sun-shade grids; done before #6 by user decision (2026-09-28), as #6 waits on the tile servers' bulk-download policies | resolved in its design.md (partial heatmap while computing deferred until measured on the device; the change also redesigned the overlay controls and moved the attributions to an About page) | archived 2026-09-30 |
| 8 | `add-settings` | settings (new) | A Settings page behind a gear button that replaces ⓘ (About and attributions at its end): coordinate format (decimal, DMS, Swiss LV95), keep screen on, start view (last view, my location, Alps overview), overlay opacity, shade resolution (Fast / Normal / Detailed / Custom, for both overlay modes), browsed-tile limit and clearing. The download rate limit and the region map depth stay fixed, and dark mode is dropped (user decisions, 2026-10-04) | resolved in its design.md | archived 2026-10-05 |
| 9 | `add-gps-location` | gps-location (new) | The device's position as a dot with its accuracy circle (MapLibre's location component and default engine, no Google Play Services), grey when older than 30 s; a location button that centres the map on a fresh position, or waits for one with an animation; permission asked on the first tap; online and offline alike. Done before #8 by user decision (2026-10-02) | resolved in its design.md (grey dot after a return from the background dropped by user decision, 2026-10-04) | archived 2026-10-04 |
| 10 | `polish-overlay` | sun-shade-overlay, point-sunshine, terrain-horizon, settings | Overlay on pans (see below); the panel aligned with the overlay offline (the panel says unknown where the overlay rightly says sun, from #5); the heatmap's day and counting pass measured on the device (from #7); a `Debug` section in Settings with an on-map debug box (timings, tiles, day state, agreement check), added as the measurement tool (user decision, 2026-10-05). Done before #12, as it settles what #12's time tape shows | resolved in its design.md (pans: earlier cached days fill in while the new day computes; geographic tiles and a margin rejected) | in progress |
| 11 | `overlay-pan-reuse` | sun-shade-overlay, sun-exposure-heatmap | After a pan, compute only the part of the visible area that no earlier day of the same date and resolution covers, and show the earlier day's grids and the new part together; measured on the desktop JVM at 43–63 % of a full recompute per half-screen pan (`investigations/overlay-pan-reuse.md`). Added after #10 by user decision (2026-10-06); done before #12, whose time tape shows what has been computed | how two partial days are combined (images, heatmap counts, unknown cells, cache of days), in its design.md | planned |
| 12 | `polish-ui` | time-selection, sun-position, point-sunshine, sun-shade-overlay, map-view, settings, offline-regions | One cohesive redesign, refined in its design.md: the bottom panel with the answer as its headline (e.g. `In sun until 14:51`), a readable time header (e.g. `Sat 21 Dec · 14:30`), collapsible details (azimuth, elevation, twilight, day length), and a horizontal time tape replacing the slider and the overlay's progress bar: a strip dragged under a fixed needle, with fling, snapping to the step and tap to jump, coloured sun / terrain shade / night / unknown (hatched as on the map) / not computed yet, filling outward from the needle as the day is computed; back arrows on the Settings and Offline pages; labels for the overlay toggle and a first-run hint; tap on the map to centre that point | decided while exploring (user decisions, 2026-10-05): the tape (instead of scroll wheels); the strip from the overlay's day at the crosshair cell while the overlay is on, from the horizon tracer's sun periods while it is off; the strip's states above; the tape stops at the day's edges and keeps 24 h. The rest in its design.md | planned |

v1 = the features (#1–#9) done and the app polished (#10–#12). Releasing (signed release build,
launcher icon, version 1.0.0) comes after v1 and is not planned yet.

Overlay polishing (#10):
- **Overlay on pans (from #5, deferred by user decision 2026-09-28).** Today a pan computes the
  day again for the new area, because the overlay covers exactly the visible area. Options,
  measured on the desktop JVM against one screen-sized sweep (zoom 12, 400 × 850 dp):
  - geographic tiles: ~3.4–7× CPU for a fresh view at any tile size (64–512 dp), ~1.5–2.2× for
    a typical pan, free for small pans and panning back;
  - a margin of half a screen per side: ~4× CPU per fresh view, free pans within the margin;
  - showing the previous day's grids while the new day computes: no extra CPU, scrubbing stays
    instant after a pan, the computation still restarts.

Post-1.0 candidates: bookmarks, time playback animation, home-screen widget,
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
