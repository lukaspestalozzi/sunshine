# Tasks

## 1. app: counting the day's sun hours

- [x] 1.1 Move the pixel geometry of `renderOverlay` (pixel centre → latitude, longitude over the grid's area, one pixel per dp) into a shared helper in `map/OverlayImage.kt`, without changing its output (design D2). Verify: `./gradlew :app:testDebugUnitTest --tests "*RenderOverlayTest*"` passes unchanged.
- [x] 1.2 Write `SunHoursTest` first, with a `DayOverlay` whose grid function returns whole-area grids built through `SunShadeSweep` on flat or missing tiles, as `DayOverlayTest` does:
  - a 24-hour day with a daytime cell sun at 57 steps, unknown at 2 and shade at the rest → `sun` 57 and `unknown` 2 at its pixels (spec "Counting steps");
  - an off-grid selected time (e.g. 12:03) is not counted;
  - night steps without unknown cells are skipped, and night steps with unknown cells are counted;
  - all tiles missing → `unknown == steps` at every pixel;
  - 2025-03-30 in Europe/Zurich → `steps` 276.

  Then implement `SunHours` and `sunHours(day)` in `map/SunHours.kt`, with the counts stored on the `DayOverlay` and counted in its `bytes` (design D2). Verify: `./gradlew :app:testDebugUnitTest --tests "*SunHoursTest*"` passes.
- [x] 1.3 Add a test to `DayCacheTest` first: a day with counts reports 4 bytes per pixel more, and the cache trims by it. Then make it pass. Verify: `./gradlew :app:testDebugUnitTest --tests "*DayCacheTest*"` passes.

## 2. app: colour scale and rendering

- [x] 2.1 Write `HeatmapColourTest` first:
  - day length 8 h 33 min → 18 bands; 5 h 25 min → band 10; 8 h 35 min → band 17;
  - 15 h 51 min → 32 bands; 0 h 0 min → 1 band;
  - band 0 has the colour `#455A64`, the last band `#FFE0A3`, and every colour's alpha is `0x73`;
  - for 32 bands, the OKLab lightness rises strictly from each band to the next.

  Then implement the bands and the OKLab interpolation of the four stops (design D3). Verify: `./gradlew :app:testDebugUnitTest --tests "*HeatmapColourTest*"` passes.
- [x] 2.2 Write `RenderSunHoursTest` first:
  - `unknown == steps` → only the hatching (`UNKNOWN_ARGB` on stripes, transparent between them);
  - `0 < unknown < steps` → `UNKNOWN_ARGB` on stripes and the band colour between them;
  - `unknown == 0` → the band colour everywhere;
  - the image has the grid's corners and size.

  Then implement `renderSunHours` (design D4). Verify: `./gradlew :app:testDebugUnitTest --tests "*RenderSunHoursTest*"` passes.

## 3. app: mode and heatmap state in the view model

- [x] 3.1 Write `MapViewModelTest` cases first for the mode:
  - `SUN_AND_SHADE` by default;
  - saved in `SavedStateHandle` and restored;
  - switching the mode neither cancels nor restarts the day (the grid function's call count is unchanged).

  Then add `OverlayMode` and `onOverlayModeSelected` (design D1). Verify: `./gradlew :app:testDebugUnitTest --tests "*MapViewModel*"` passes.
- [x] 3.2 Write `MapViewModelTest` cases first for `heatmap`:
  - `Off` while the overlay is off or in `Sun & shade`, and `ZoomedOut` at zoom 10.5;
  - `Computing(kept = null)` while the day is incomplete, and `Ready` once it is complete, with counts equal to `sunHours(day)`;
  - a time change within the day leaves `Ready` unchanged and counts nothing again;
  - after a pan or a date change, `Computing(kept = previous Ready)` until the new day is complete;
  - switching back to a complete cached day gives `Ready` without computing a grid;
  - switching to `Sun hours` with a complete day without counts builds them once;
  - a day computed anew after a reconnect gets new counts.

  Then expose `currentDay` from the overlay flow and implement `heatmap` (design D5). Log `Sun hours <w>×<h> dp, <steps> steps in <n> ms` after the pass and its rendering. Verify: `./gradlew :app:testDebugUnitTest --tests "*MapViewModel*"` passes.

## 4. app: UI

- [x] 4.1 Extend `OverlayNoticesTest` first:
  - in `Sun hours`, `Computing` → `Computing sun hours …`, and `Ready` → none;
  - zoomed out → `Zoom in to see sun and shade` in both modes;
  - `Computing sun and shade …` never in `Sun hours`.

  Then make `overlayNotice` take the mode and add the strings (design D6). Verify: `./gradlew :app:testDebugUnitTest --tests "*OverlayNoticesTest*"` passes.
- [x] 4.2 Add cases to `SunFormatTest` first, one per spec scenario of "Sun hours in the information panel":
  - `≈ 5 h 20 min`;
  - `at least 5 h 20 min (10 min unknown)`;
  - `at least 2 h 0 min (1 h 15 min unknown)`;
  - `unknown`;
  - `…` while computing, and `…` for a `Ready` heatmap of another area;
  - the same text under the de-CH locale.

  Then implement `formatSunHours` (design D6). Verify: `./gradlew :app:testDebugUnitTest --tests "*SunFormatTest*"` passes.
- [x] 4.3 Add a unit test for the legend's labels first: 8 h 33 min → `0 h`, `2 h`, `4 h`, `6 h`, `8 h`; 15 h 51 min → up to `14 h`; 0 h → `0 h`. Then add to `OverlayControl`:
  - the segmented control, shown while the overlay is on;
  - the heatmap legend in `Sun hours`, whose band bar and `Unknown` row replace the `Shade` / `Unknown` legend.

  Verify: `./gradlew :app:testDebugUnitTest --tests "*Heatmap*"` passes, and `./gradlew :app:lintDebug` reports no new issues.
- [x] 4.4 Wire `MapScreen`:
  - the image of the mode goes to `MapLibreMap`;
  - the mode control's callbacks;
  - the `Sun hours` line in `SunPanel` below `Sunshine`, only in `Sun hours` at zoom ≥ 11.

  Verify: `./gradlew assembleDebug` succeeds, and `./gradlew :app:testDebugUnitTest` passes.

## 5. Docs

- [x] 5.1 Update `CLAUDE.md` (the `app` row: `map/SunHours.kt`, the heatmap mode) and `docs/roadmap.md` (#7 in progress; the note that #7 goes before #6 by user decision, 2026-09-28). Verify: `openspec validate --all --strict` passes, and both files name `SunHours.kt` and the order.

## 6. Integration

- [x] 6.1 Run `./scripts/verify-local.sh` and `openspec validate --all --strict`. Verify: ktlint, Android lint, all unit tests and the debug APK pass, and validation reports no failures.
6.2 (the on-device check) moved to 8.3, after the redesign of the controls and the About page (user decisions, 2026-09-29).

## 7. app: one toggle, one status card, About page (design D7, D8)

- [ ] 7.1 Write `AboutEntriesTest` first:
  - the entries are the app name with version name `0.1.0`, the map attribution `© OpenStreetMap contributors, SRTM | Map style: © OpenTopoMap (CC-BY-SA)` with `https://www.openstreetmap.org/copyright`, the elevation attribution `Elevation: © Mapterhorn and its sources` with `https://mapterhorn.com/attribution/`, and `Icons: Material Symbols (Apache License 2.0)` without a link.

  Then implement the entries, `AboutScreen` in `app/.../about/AboutScreen.kt`, and the switch in `MainActivity` (`rememberSaveable` flag, `BackHandler`), with the `info` vector drawable (design D8). Verify: `./gradlew :app:testDebugUnitTest --tests "*AboutEntriesTest*"` passes and `./gradlew assembleDebug` succeeds.
- [ ] 7.2 Remove the attribution labels from `MapLabels` and add the ⓘ `IconButton` (content description `About and attributions`) directly left of the coordinates in the top-start corner; the sun panel moves down to the bottom (design D8). Verify: `./gradlew assembleDebug` succeeds and `grep -rn "map_attribution" app/src/main/java` finds it only in the About entries.
- [ ] 7.3 Write `OverlayToggleTest` first:
  - `isOverlayOn` false → `Off`; true with `SUN_AND_SHADE` → `Sun & shade`; true with `SUN_HOURS` → `Sun hours`;
  - selecting `Off` switches the overlay off; selecting a mode sets it and switches the overlay on only if it was off;
  - selecting `Sun hours` from `Off` gives `isOverlayOn` true and `SUN_HOURS` on a `MapViewModel` (spec "Straight to sun hours").

  Then replace the chip and the segmented control with the three-way icon toggle, 3 × 56 dp, without the check icon, and the `layers_clear`, `contrast` and `timelapse` vector drawables (design D7). Verify: `./gradlew :app:testDebugUnitTest --tests "*OverlayToggleTest*"` passes.
- [ ] 7.4 Update `HeatmapLegendTest` first: labels `0`, `2`, `4`, `6`, `8 h` for 8 h 33 min; `0` … `14 h` for 15 h 51 min; `0 h` for a day length of 0; every legend colour opaque (alpha 0xFF). Then build the status card of the toggle's width: the mode's name, the notice, directly below it the progress bar (below the name when there is no notice), and the mode's legend in opaque colours; move the notices out of `MapLabels`, and give every floating element the one surface style (design D7). Verify: `./gradlew :app:testDebugUnitTest --tests "*Heatmap*" --tests "*OverlayNoticesTest*"` passes and `./gradlew :app:lintDebug` reports no new issues.

## 8. Docs and integration after the redesign

- [ ] 8.1 Update `CLAUDE.md` (the `app` row: `about/AboutScreen.kt`, the toggle and status card in `map/OverlayControl.kt`, no attributions in `MapLabels`). Verify: `openspec validate --all --strict` passes and `CLAUDE.md` names `AboutScreen.kt`.
- [ ] 8.2 Run `./scripts/verify-local.sh` and `openspec validate --all --strict`. Verify: ktlint, Android lint, all unit tests and the debug APK pass, and validation reports no failures.
- [ ] 8.3 On-device check with the CI APK. Expected:
  - at launch the toggle shows `Off` selected and no status card; the ⓘ button sits left of the coordinates, and no attribution covers the map;
  - at 360 dp width, in portrait and landscape, the toggle's three icons stay in one row on screen, the status card has the toggle's width and right edge, and nothing overlaps;
  - at Interlaken (46.6863° N, 7.8632° E), zoom 12, 2025-12-21, `Sun hours`: the card shows `Sun hours`, `Computing sun hours …` and directly below it the progress bar until the day is complete, then the heatmap over the whole screen, and the panel reads `Sun hours ≈` 5 h 23 min ± 20 min;
  - the legend reads `0` … `8 h` with `Unknown` in opaque colours; the heatmap's bands rise from slate to light amber, and paths, labels and contour lines stay readable (tune alpha and stops in design D3 if not, keeping the lightness rising);
  - moving the slider leaves the heatmap unchanged; switching between `Sun & shade` and `Sun hours` shows each at once;
  - a pan keeps the previous heatmap on its terrain with `Computing sun hours …`;
  - picking 2025-12-22 and then 2025-12-21 shows the first heatmap at once;
  - in flight mode over a never-visited area, the heatmap is hatched only, and the panel reads `Sun hours unknown`;
  - zoom 10.5 shows `Zoom in to see sun and shade` in the card;
  - ⓘ opens the About page with the version and both attributions; each attribution opens its page in the browser; back returns to the same map, time and mode;
  - record in design.md, "Performance budget": the logged `Overlay day` total at zoom 12 and the `Sun hours` pass and rendering times, against the 2 s and 100 ms budgets. If the pass exceeds 2 s, ask the user about incremental counting; if the day is too slow, ask about a partial heatmap (proposal, Non-goals).
