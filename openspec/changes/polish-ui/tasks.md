# Tasks

Device checks (7.1–7.4) are done by the user at the end, on the debug APK of the last commit.

## 1. Roadmap and pure formatting

- [x] 1.1 Set `docs/roadmap.md` row #12 to "in progress" with the decisions of this change (headline = the day's periods; short header with the offset on clock-change days; details collapsed and kept; hint, clock dialog; user decisions, 2026-10-06). Verify: `openspec validate --all --strict` passes and the row reads "in progress".
- [x] 1.2 Write `HeaderTimeTest` first (design D2; time-selection "Time zone of the selected time"): `Sun 21 Dec · 12:00`, `Sat 21 Jun · 15:00`, `Tue 21 Dec 2027 · 12:00`, both 02:30 of 2025-10-26 with `UTC+2` and `UTC+1`, `Sun 30 Mar · 03:30 UTC+2`, Asia/Kolkata without offset, the same text under a de-CH default locale. Then add `formatHeaderTime(time, today)` in `SunFormat.kt`. Verify: `./gradlew :app:testDebugUnitTest --tests "*HeaderTime*"` passes.

## 2. The time tape's model

- [x] 2.1 Write `TapeScaleTest` first (design D3; time-selection "Choose the time of day"): 70 dp per hour; snapping to 5- and 10-minute steps, clamped to 00:00 and the last step (23:55, 23:50); a tap 36 dp right of 12:00 gives 12:30; 70 dp of drag gives one hour; 276 and 300 steps on 2025-03-30 and 2025-10-26 with the wall-clock hour labels (`2` skipped, `2` twice); 09:47 lies 2/5 between 09:45 and 09:50. Then add `map/TapeScale.kt`. Verify: `./gradlew :app:testDebugUnitTest --tests "*TapeScale*"` passes.
- [x] 2.2 Write `TapeStripTest` first (design D5; time-selection "Time tape strip"): night wins over every source state; `null` is not computed yet; sun, shade and unknown pass through; a 10-minute heatmap day maps onto 5-minute tape steps (14:10 and 14:15 take 14:10's state). Then add `map/TapeStrip.kt`. Verify: `./gradlew :app:testDebugUnitTest --tests "*TapeStrip*"` passes.

## 3. The strip and the exact time in the view model

- [x] 3.1 Extend `MapViewModelTest` first with the strip (design D5): overlay off at Interlaken on 2025-12-21 gives sun 10:10–14:50 and 15:15–15:50, shade 08:15–10:05, 14:55–15:10 and 15:55–16:40, night elsewhere (±1 step, against a horizon profile giving the tracer's periods); not computed while the horizon loads; unknown with an unknown ground height; `Sun & shade` on with 72 of 288 steps computed gives those 72 states and not computed elsewhere in the daytime; `Sun hours` maps its 10-minute day; a camera move keeps the states until the camera rests; the strip from the horizon takes ≤ 20 ms and an update after a computed step ≤ 5 ms on the JVM. Then add `tapeStrip` and the shown days' flows, and remove `dayProgress` and its tests (replaced by these). Verify: `./gradlew :app:testDebugUnitTest --tests "*MapViewModel*"` passes.
- [x] 3.2 Extend `MapViewModelTest` first with `onTimeTyped` (design D6; time-selection "Exact time"): 14:32 selected as typed; rounded to 14:30 with `Sun & shade`; 02:30 on 2025-03-30 gives 03:30 UTC+2; 02:30 on 2025-10-26 gives the UTC+2 occurrence. Then add `onTimeTyped`. Verify: `./gradlew :app:testDebugUnitTest --tests "*MapViewModel*"` passes.
- [x] 3.3 Extend the settings tests first (design D7; settings "Stored settings"): `detailsExpanded` and `hintDismissed` default to false, are stored and read back, an unreadable value falls back to false and leaves the others. Then add them to `Settings`, `decode`, `SettingsKeys` and `SettingsStore`. Verify: `./gradlew :app:testDebugUnitTest --tests "*Settings*"` passes.
- [x] 3.4 Extend `MapViewModelTest` first with the strip's progress (design D5; time-selection "Time tape strip"; user decision 2026-10-07): `0 %` while the horizon loads and `100 %` once computed with the overlay off; `25 %` with 72 of 288 `Sun & shade` steps computed and `100 %` once all are. Then make `tapeStrip` a `TapeStrip(states, percent)`. Verify: `./gradlew :app:testDebugUnitTest --tests "*MapViewModel*"` passes.

## 4. The panel

- [x] 4.1 Add `map/TimeTape.kt` (design D4): the scale drawn around the needle with hour ticks and labels, the strip's colours (`#FFD54F`, `#455A64`, `#263238`, `#9E9E9E` stripes, `#E0E0E0`), drag, fling, tap, edges, outside changes animated, and the semantics (`Time of day`, `HH:mm`, `Earlier` / `Later`). Verify: `./gradlew :app:compileDebugKotlin` and `./scripts/verify-local.sh --quick` pass; the behaviour is device check 7.1.
- [x] 4.2 Rebuild `SunPanel` (design D1; sun-position "Sun information panel", point-sunshine "Sunshine in the information panel"): header with the clickable time (`formatHeaderTime`) opening the clock dialog (design D6), `Date`, `Now`; the headline at 18 sp or more; the `Sun hours` line; the tape; the `Details` row (`Show details` / `Hide details`) with altitude, sun values, whole-day text and zone ID, stored through `detailsExpanded`. Remove the slider. Extend `SunPanelWidthTest` if the width rules change. Verify: `./gradlew :app:testDebugUnitTest` passes; the look is device check 7.2.
- [x] 4.3 Remove the progress bar from the status card (design D5; sun-shade-overlay "Overlay status card"; done with 3.1, as removing `dayProgress` removes it). No tooltips (design D8, user decision 2026-10-07). Verify: `./gradlew :app:testDebugUnitTest --tests "*Overlay*"` passes and `OverlayControl.kt` has no progress bar.
- [x] 4.4 The progress as `25 %` right of the tape, and the header's time and the `Details` row's text in the primary colour (design D1, D5; sun-position "Sun information panel"; user decision 2026-10-07). Verify: `./scripts/verify-local.sh` passes; the look is device check 7.2.

## 5. Map and pages

- [x] 5.1 Tap to centre in `MapLibreMap` (design D10; map-view "Selected location crosshair"), calling `onCameraGesture` first. Verify: `./gradlew :app:compileDebugKotlin` passes; the behaviour is device check 7.3.
- [x] 5.2 The first-run hint above the panel (design D9; map-view "First-run hint"), its texts in `strings.xml`, the toggle's icons inline, `Got it` storing `hintDismissed`. Verify: `./gradlew :app:testDebugUnitTest` passes; the look is device check 7.3.
- [x] 5.3 Top bars with the back arrow on the Settings, Custom resolution and Offline pages (design D11; settings "Settings page", "Custom resolution", offline-regions "Offline button"), the `arrow_back` icon added, `MainActivity` passing `onBack`. Verify: `./gradlew :app:testDebugUnitTest` passes; device check 7.4.

## 6. Docs and the full check

- [x] 6.1 Update `CLAUDE.md`'s `app/` row (time tape, strip, panel, hint, top bars; the slider and the progress bar gone). Verify: `./scripts/verify-local.sh` passes.

## 7. Device checks

- [ ] 7.1 On the phone, overlay off, at a place with terrain shade: drag the tape by about an hour, fling it both ways to the day's edges, tap a point of it, tap `Now`. Verify: the time follows each step, a fling stops on a step and at 00:00 / 23:55 at the latest, a tap jumps to the step under it, `Now` shows between steps; the strip shows night, sun and terrain shade where the headline's periods say so (±1 step).
- [ ] 7.2 On the phone, `Sun & shade` at zoom 12: watch the strip fill outward from the needle while the day is computed, pan half a screen and let it rest, then expand and collapse the details and relaunch the app. Verify: no progress bar on the card; the strip fills from the needle while the percentage right of the tape counts up to `100 %`; the header's time and `Details` have the colour of `Date` and `Now`; after the pan it shows the new crosshair's day; the details are collapsed at first and keep the last state after the relaunch; the headline is the largest text and wraps if long; the header reads e.g. `Fri 9 Oct · 13:15`.
- [ ] 7.3 On the phone, after `adb shell pm clear com.sunshine.app`: the hint, then `Got it` and a relaunch; tap the map 100 dp right of the crosshair; double-tap the map; tap the header's time and set 14:32. Verify: the hint shows once and not after `Got it`; the tapped point moves under the crosshair within about 300 ms; a double tap still zooms; the time is 14:32 (14:30 with `Sun & shade`).
- [ ] 7.4 On the phone: open Settings, Custom resolution and Offline and leave each with its back arrow. Verify: each returns where it came from, with the map as it was and the custom values applied.
