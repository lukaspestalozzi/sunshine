# Design

## Context

See proposal.md for the motivation and the specs for the behaviour. Relevant current state:

- **Overlay flow.** `MapViewModel.overlay` holds one `DayOverlay` for the visible area, date, cell
  size and step. A camera rest on another area starts a new day (`DayCache.get` by exact area,
  otherwise a new `DayOverlay`), cancels the old day's background job and computes the selected
  time first. A time the day has not reached is sent as `OverlayUiState.Computing(kept = current)`
  and computed next. Each `OverlayUiState.Ready` holds its grid, whose `area` places the image
  (`OverlayImage.corners`) on its own terrain. `DayCache` keeps days by `(area, date, cellDp,
  stepMinutes)` in access order, up to a quarter of the heap.
- **Heatmap after a pan.** `HeatmapUiState.Computing(kept)` already keeps the previous heatmap on
  its terrain (sun-exposure-heatmap "Heatmap updates"); nothing changes there.
- **Incomplete horizons.** `HorizonTracer.advance` ends a ray at its first missing sample
  (`complete[ray] = false`, `active[ray] = false`). `HorizonProfile` holds `angles` and
  `complete`; `sunshine()` returns `UNKNOWN` for any upper edge above an incomplete angle. The
  sweep (`SunShadeSweep.state`) keeps sampling past gaps (missing samples are not pushed, the line
  remembers the nearest gap) and decides `complete = tan ≥ bound(gap)`, independent of the sun.
  The overlay looks less unknown than the panel only because its upwind cut (D4 of
  add-sun-shade-overlay) never reaches gaps beyond `d_max`; cells with eyes above the area's
  lowest ground can still be unknown where the sun is certain.
- **Diagnostics.** Timings go to logcat through `debugLog` (debug builds only) from
  `SunshineRepository` (horizon), `OverlayRepository` (tiles, sweep), `MapViewModel` (grid, image,
  day, sun hours, sun periods). Tile sources come from `DemTileFetcher.loads()` (store and network
  counters); memory and unavailable tiles are lumped together. The agreement check runs 3 s after
  each `Ready` overlay on 200 sampled cells, only when `checkOverlayAgreement = BuildConfig.DEBUG`.
- **Settings.** `Settings` with `decode`, `SettingsStore` (Preferences DataStore, read once in
  `SunshineApp.onCreate`), `SettingsScreen` sections Display, Map, Calculation, Storage, About.

## Goals / Non-Goals

**Goals:**
- One sunshine rule in `core` with a lower and an upper bound, used by the point tracer's states
  and by the sweep's cells; unit-tested with the spec's synthetic values.
- The fallback after a pan reuses the cache of days; no new cache and no new computation.
- Debug values are typed and cheap: counters and durations recorded where the logcat lines are
  today, published as one `StateFlow`.

**Non-Goals:**
- No change to `DayOverlay`'s computation, order, CPU share or the cache's budget.
- No second image source: at most one overlay image is drawn at a time, as today.
- The logcat lines stay as they are (debug builds).

## Decisions

### D1. After a pan: earlier days fill in (user decision, 2026-10-05)
While the visible area's day lacks the selected time, the overlay shows that time from an earlier
cached day whose area overlaps the screen (sun-shade-overlay "Overlay updates"). The new day still
starts at the camera rest, selected time first; the fallback only changes what is drawn meanwhile.
No notice is shown: the drawn overlay belongs to the selected time, and it lies on its own
terrain, which is correct there.

*Alternatives (asked):*
- a margin of half a screen per side: free pans within it, ~4× CPU and time for every fresh view;
- geographic tiles: ~3.4–7× CPU for a fresh view, ~1.5–2.2× per typical pan, free small pans and
  pans back; tiled grids, rendering and heatmaps, the largest refactor;
- measuring first and deciding later: postpones the improvement without changing the options.

### D2. Which earlier day
`DayCache.overlapping(area, date, cellDp, stepMinutes, time)` returns, among the cached days other
than the current one with the same date, cell size (dp) and step, whose geographic bounds intersect
the visible area's and that have a grid at `time`, the most recently used one. The map zoom may
differ: after a zoom, the earlier grid is drawn scaled on its own terrain, as the kept overlay is
today after a camera move. `OverlayUiState.Ready` gains `source` (`OWN_DAY`, `EARLIER_DAY`) for the
debug box; `Computing(kept)` stays the third case (`previous overlay`).

The lookup runs where `selectedDay.gridAt(time)` returns `null`: if an earlier day has the grid, it
is rendered and sent as `Ready(source = EARLIER_DAY)`; the selected time is still computed next for
the own day (`immediate`), which replaces it. The early-return `unchanged` check compares
`current.grid.area == area`, so an earlier-day `Ready` never counts as unchanged.

*Alternatives:* only the day shown before the pan (lost after two quick pans, when the previous
day has few steps); a mosaic of several days (several image sources and renderings per time
change, beyond the 100 ms budget).

### D3. Upper bound and one rule (user decision, 2026-10-05)
- `HorizonProfile` gains `upper: DoubleArray` (degrees). For a complete bin it equals the angle.
  `complete` is kept for the existing callers (`SunshineRepository` caches only complete
  profiles); a bin is complete exactly where its upper bound equals its angle.
- `HorizonTracer` records the distance of each ray's first missing sample and **keeps sampling**
  past it; missing samples only mark the gap. The ray still ends at `cannotRise`. At the end, a ray
  with a gap at distance d has `upper = atan(max over d' ≥ d of bound(d'))`, with
  `bound(d) = (heightBound − eye − drop(d)) / d`; bound falls with d when the eye is below the
  height bound, so this is bound(d), and otherwise its maximum beyond d (the tracer's existing
  comment on `cannotRise`). It is clamped to be at least the lower bound.
- `sunshine(profile, azimuth, upperEdge)`: shade if `upperEdge ≤ angleAt(azimuth)`; sun if
  `upperEdge > upperAt(azimuth)`, where `upperAt` is the larger of the two neighbouring bins'
  upper bounds (conservative between bins); unknown otherwise.
- `SunShadeSweep.state`: `complete = noGap || tan ≥ bound(gap) || tanUpperEdge > bound(gap)`,
  with `tanUpperEdge` computed once per sweep. Shade stays `upperEdge ≤ atan(tan)`.

The upwind cut stays; it is now only a saving, not the source of the panel/overlay difference.

*Alternatives:* keep the tracer stopping at gaps (its lower bound would stay below the sweep's
where terrain lies beyond the gap, so panel and overlay would still differ); interpolate the
upper bound like the angle (could call sun where one neighbouring bin cannot exclude shade).
*Asked:* leaving the difference as a known limitation (rejected).

### D4. Debug values: a typed collector
`DebugInfo` (app, `sunshine/DebugInfo.kt`) holds a `MutableStateFlow<DebugValues>`, an immutable
data class with one nullable field per spec line (durations, tile counts by source, day state,
shown source, cache size, agreement). Producers call typed methods (`horizon(total, tiles,
sources)`, `grid(...)`, `day(...)`, …) at the places that log today; each is a `update { copy }`.
`SunshineApp` builds one instance and passes it to the repositories and `MapViewModel` beside the
existing `log`. Tile sources: `OverlayRepository` and `SunshineRepository` count `kept` and
`unavailable` (null tiles) themselves, disk and network from `loads()` deltas, and memory as the
rest; the spec's caveat applies (concurrent loads may shift counts between disk/network and
memory). `TileCache` exposes `size` for `Memory <n> of <max>`.

The box (`map/DebugBox.kt`) collects `DebugInfo.values` and the four switches and formats lines
with pure functions (`debugLines(values, switches)`), unit-tested; it sits in `MapLabels`' left
column below the offline notice. The values are recorded always, the box only reads them, so
switching a group on shows the last values at once.

*Alternatives:* parse the log strings (fragile); a master switch with groups (user decision:
individual switches only); debug builds only (user decision: every build, off by default).

### D5. Agreement check by setting
The `init` block's agreement collector becomes `combine(overlay, settings.agreementCheck)` with
`collectLatest`: it runs only while the switch is on (any build), as before 3 s after a `Ready`
overlay, on 200 cells, on `computeDispatcher`. Results go to `DebugInfo` (and to logcat in debug
builds). `checkOverlayAgreement` is removed from the constructor.

### D6. Settings
`Settings` gains `debug: DebugSwitches(timings, tiles, dayState, agreementCheck)`, all `false` by
default, decoded per key like the other values (an unreadable value → `false`). `SettingsScreen`
adds the section `Debug` with four switch rows between Storage and About.

### D7. Device measurement (user decision: record, then ask)
With `Timings` and `Day state` on, on the user's phone, `Normal`, map zoom 12, Lauterbrunnen
(46.5935° N, 7.9091° E), on 2025-12-21 and 2025-06-21: record the `Grid`, both `Day` lines (`Sun &
shade` and `Sun hours`), `Sun hours` counting and image, and one `Agreement` result, in the
"Performance budget" table below. If the counting pass exceeds 2 s or the image 100 ms, the work
stops and the user decides on incremental counting or a partial heatmap, in a follow-up change.
The day durations are shown to the user either way.

### D8. Images by rows and columns (user decision after the first device measurement, 2026-10-06)
The first device round measured 256 ms for the `Sun & shade` image and 139 ms for the heatmap
image, against 100 ms each; every scrub to a computed time redraws the overlay image. Both are
sped up without changing a pixel:
- **Overlay:** `renderOverlay` asked `ShadeGrid.stateAt` for each pixel, i.e. four trigonometric
  functions and two allocations per pixel of the gnomonic projection. `ShadeGrid.statesAt(latitudes,
  longitudes)` projects the raster in one pass with the sines and cosines computed once per row
  and once per column (`GnomonicFrame.forwardGrid`), with the same arithmetic per point as
  `forward`, so every state equals `stateAt`'s.
- **Heatmap:** `renderSunHours` computes each count's colour once (band or transparent) and each
  column's and row's count index once; per pixel only the hatching remains.

*Alternatives (asked):* deferring to `polish-ui`; relaxing the spec's 100 ms; accepting 139 ms for
the heatmap, which is drawn once per day (user decision: optimize both).

## Performance budget

| Computation (user-triggered) | Budget | Check |
|---|---|---|
| Time change after a pan, shown from an earlier day | ≤ 100 ms incl. rendering | unit test (no sweep runs); device check |
| `DayCache.overlapping` lookup | ≤ 1 ms for ≤ 50 cached days | unit test |
| Point tracer offline, continuing past gaps | ≤ the online duration of the same location (it reads at most the same tiles) | `Horizon` timing in the debug box, device check |
| Upper-bound rule in the sweep | no measurable change (one comparison per incomplete cell) | existing sweep timing test |
| Recording debug values | ≤ 1 % of each computation (one `StateFlow.update` per computation) | review; `Grid` timing with and without boxes |
| Debug box update | ≤ 1 s after a new value | device check |
| Agreement check (only while on) | 200 profiles in the background; map and slider stay responsive | device check |
| Heatmap counting pass | ≤ 2 s on the phone at zoom 12 (from #7) | device measurement, D7 |
| Heatmap image | ≤ 100 ms on the phone (from #7) | device measurement, D7 |

Measured values (task 5.1, first round, user's phone, 2026-10-06, from a screenshot of the debug
box): Lauterbrunnen area (46.5921° N, 7.9081° E), map zoom 11.2, area 411 × 891 dp, `Normal`,
2026-10-06 13:15 UTC+2, online. Not yet the protocol of D7 (zoom 12, 2025-12-21 and 2025-06-21),
and the `Day` line shows the `Sun & shade` day, not the heatmap's.

| Value | Measured | Budget | |
|---|---|---|---|
| Horizon of the crosshair | 1961 ms (tiles 632 ms; 80 tiles: 43 memory, 33 disk, 4 network) | – | |
| Sun periods | 146 ms | – | |
| `Sun & shade` grid of the selected time | 776 ms (30 tiles: 28 kept, 2 disk) | – | |
| `Sun & shade` image | 256 ms | ≤ 100 ms for a computed time (sun-shade-overlay "Overlay of the whole day") | **over** |
| `Sun & shade` day, 288 steps (144 at night) | 92.4 s | ~20–110 s estimated (#5) | within |
| Heatmap counting pass | 394 ms | ≤ 2 s | within |
| Heatmap image | 139 ms | ≤ 100 ms | **over** |
| Memory tiles | 64 of 64 | – | full |
| Cache of days | 5 days, 20 of 64 MiB | – | |
| Agreement | still `…` when taken | – | |

Second round (task 5.1, 2026-10-06, after design D8, from two screenshots): same area at map zoom
11.0, 2026-10-06 12:10 UTC+2, `Normal`, online. Zoom and date deviate from D7 again; the image
times depend on the area in dp, not on the zoom, so they compare with the budgets as they are.

| Value | Measured | Budget | |
|---|---|---|---|
| `Sun & shade` image | 75 ms (round 1: 256 ms) | ≤ 100 ms | within |
| Heatmap image | 65 ms (round 1: 139 ms) | ≤ 100 ms | within |
| Heatmap counting pass | 452 ms | ≤ 2 s | within |
| `Sun & shade` grid of the selected time | 725 ms | – | |
| `Sun & shade` day, 288 steps (144 at night) | 91.8 s | – | |
| Heatmap day, 144 steps (72 at night) | 16.6 s | ~20–30 s estimated (#7) | within |
| Horizon of the crosshair | 1646–1846 ms | – | |

Found in this round: a cancelled agreement check left `Agreement …` in the box for good; fixed
(the check now clears its `…` when cancelled). One check takes 200 horizons of about 1.7 s each,
about 6 minutes on the phone. A pan costs a whole `Sun & shade` day again, about 92 s of background
CPU; `investigations/overlay-pan-reuse.md` measures what computing only the uncovered part would save.

## Risks / Trade-offs

- [The earlier day's grid is only partly on screen after a long pan] → It is correct where drawn;
  the rest stays untinted, as after a camera move today. The own day replaces it within one sweep.
- [The tracer reads more tiles offline beyond gaps] → Only tiles in the cache are read offline;
  missing ones fail fast. Online, a server error beyond a gap no longer ends the ray early, which
  can cost a few more requests per incomplete ray.
- [Sun periods become known more often offline] → Intended: the rule only calls sun where no
  terrain at the height bound could block it.
- [The agreement check costs 200 horizon profiles] → Only while its switch is on; it runs on
  `computeDispatcher` and is cancelled by the next overlay.
- [Tile counts by source shift under concurrent loads] → Documented in the box's meaning (D4);
  they are diagnostics, not a spec'd accuracy.
- [The box covers part of the map's left side] → Only while a switch is on; lower lines are cut
  rather than covering the panel.

## Migration Plan

No data migration: the debug switches start off when no value is stored. Rollback: revert the
change's commits; stored debug keys are then ignored.
