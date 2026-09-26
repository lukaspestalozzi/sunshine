# Tasks

## 1. Reference values and dependency

- [ ] 1.1 Write `investigations/sun-position-references.md` (design D9). For every numeric scenario of the `sun-position` spec it records the commons-suncalc 3.11 output, the astral 3.2 (NOAA) output, the timeanddate.com value where one exists (from `investigations/sunrise-integration-test-plan.md`), and the scripts used to produce them. It also records the refraction gap measured below 0° (Interlaken, 2025-12-21, 07:50–08:35). Verify: every expected value in `specs/sun-position/spec.md` appears in the file, and both implementations lie within the spec tolerance of it (the 08:11 elevation is the documented exception for astral).
- [ ] 1.2 Add `org.shredzone.commons:commons-suncalc` to `gradle/libs.versions.toml` (3.11, or a newer stable at apply time) and as an `implementation` dependency of `core`. Verify: `./gradlew :core:dependencies --configuration runtimeClasspath` lists commons-suncalc and no other new artifact, and `./gradlew :core:build` passes.

## 2. core: sun position

- [ ] 2.1 Write `SunPositionTest` first, as a parameterized test over the five `Sun position` scenarios (Interlaken 2025-12-21 12:00 +1, 2025-06-21 15:00 +2, 08:20 refraction, 08:11 no refraction, 02:00 night; azimuth ±0.2°, elevation ±0.1°). Run it and see it fail, then implement `SunPosition` and `sunPosition(point, instant)` (design D2, D6). Verify: `./gradlew :core:test --tests "*SunPositionTest*"` passes.
- [ ] 2.2 Add the `Sun above the horizon` cases to `SunPositionTest`: 2025-12-21 08:11 +1 is above, 08:05 +1 is below. Verify: `./gradlew :core:test --tests "*SunPositionTest*"` passes.
- [ ] 2.3 Record the refraction limitation in `docs/roadmap.md`, entry #4 (design D2: no refraction at or below 0° geometric; 3–5 min pessimistic where the terrain horizon is ≤ 0°). Mark entry #2's open decisions as resolved in its design.md. Verify: `grep -n "refraction" docs/roadmap.md` shows the note in the row for entry #4.

## 3. core: sun events of a day

- [ ] 3.1 Write `SunDayTest` first, with the event scenarios that have exact times: Interlaken solstices (civil dawn, sunrise, sunset, civil dusk), equinoxes, 2025-03-30 and 2025-10-26 (Europe/Zurich), and Tokyo 2025-06-21 (Asia/Tokyo), each ±2 min and with the event's local date equal to the selected date. Run it and see it fail, then implement `SunDay` and `sunDay(point, date, zone)` with the day window of design D6. Verify: `./gradlew :core:test --tests "*SunDayTest*"` passes.
- [ ] 3.2 Add the absent-event cases to `SunDayTest`: Svalbard 2025-06-21 (all four absent, `wholeDay = ABOVE_HORIZON`) and 2025-12-21 (all four absent, `BELOW_HORIZON`); Tromsø 2025-05-16 (sunrise present, sunset absent, `wholeDay = null`) and 2025-05-17 (both present, sunset before sunrise). Verify: `./gradlew :core:test --tests "*SunDayTest*"` passes.
- [ ] 3.3 Add the day-length cases to `SunDayTest`: Interlaken 15 h 51 min and 8 h 33 min (±2 min); Svalbard 24 h and 0; Tromsø 2025-05-17 = (sunset − start of day) + (end of day − sunrise); Tromsø 2025-05-16 = end of day − sunrise. (No real place has polar day or night on a DST transition day, checked for Svalbard, Tromsø, McMurdo, Troll and Qaanaaq in 2025–2026, so the "actual length of the day" rule is covered by the 24 h case and the day-window code of 3.1.) Verify: `./gradlew :core:test --tests "*SunDayTest*"` passes.

## 4. app: time selection and formatting logic

- [ ] 4.1 Write `TimeSelectionTest` first, then implement `TimeSelection.kt` (design D3, D7). Cases, in Europe/Zurich unless stated:
  - Day length and slider range: 288 positions (00:00…23:55) on 2025-12-21, 276 on 2025-03-30 and 300 on 2025-10-26.
  - Snapping to the 5-minute grid.
  - On 2025-03-30 the position after 01:55 +1 is 03:00 +2.
  - On 2025-10-26, 02:00–02:55 appear twice, +2 then +1.
  - A date change keeps the wall time (2025-12-21 14:35 → 2025-06-21 14:35).
  - The DST gap: 2025-03-29 02:30 → 2025-03-30 03:30 +2.
  - The date-picker UTC-millisecond conversion, round-tripping 2025-12-21 in America/Los_Angeles and in Pacific/Auckland.

  Verify: `./gradlew :app:testDebugUnitTest --tests "*TimeSelection*"` passes.
- [ ] 4.2 Write `SunFormatTest` first, then implement `SunFormat.kt` (design D7). Cases:
  - Selected-time labels: `2025-12-21 12:00 UTC+1`, `2025-06-21 15:00 UTC+2`, `UTC+5:30` (Asia/Kolkata), `UTC` (Etc/UTC), and the de-CH locale.
  - Azimuth: 173.49 → `173° S`; 22.4 → `22° N`; 22.5 → `23° NE`; 359.6 → `0° N`.
  - Elevation: 19.66 → `19.7°`; -0.7246 → `-0.7°`.
  - Event times: rounding to the nearest minute (07:11:45 → `07:12`, 17:22:29 → `17:22`); an event whose offset differs from the selected time's (`07:12 UTC+2`); an absent event → `none this day`.
  - Day length: `8 h 33 min`, `0 h 0 min`, `24 h 0 min`.
  - The two whole-day texts.

  Verify: `./gradlew :app:testDebugUnitTest --tests "*SunFormat*"` passes.
- [ ] 4.3 Extend `MapViewModel` with `Clock` and a compute dispatcher, `selectedTime`, `onDateSelected`, `onSliderMoved`, `onNowClicked` and the `sun` flow (design D7, D8), and update the factory in `MapScreen.kt`. Extend `MapViewModelTest`, one case at a time, with a fixed clock at 2025-12-21 09:47:31 Europe/Zurich and a test dispatcher:
  - The initial time is 09:47.
  - The selected time is restored from `SavedStateHandle`.
  - "Now" after a date change returns to 09:47, and no update follows while the clock advances.
  - A date change keeps the wall time.
  - The slider snaps.
  - `sun` for camera 46.6863, 7.8632 at 12:00 has azimuth 173.5° ±0.2°, and changes after `onCameraMoved`.

  Verify: `./gradlew :app:testDebugUnitTest --tests "*MapViewModel*"` passes.

## 5. app: UI

- [ ] 5.1 Make the map north-up and flat: `rotateGesturesEnabled(false)` and `tiltGesturesEnabled(false)` in `mapOptions` (design D5). Verify: `./gradlew :app:assembleDebug :app:lintDebug` passes (gesture behavior is checked on the device in 6.3).
- [ ] 5.2 Add `SunPanel`: the selected-time label with the zone ID, a date button opening a `DatePickerDialog`, "Now", the continuous slider, azimuth, elevation, civil dawn, sunrise, sunset, civil dusk, day length and the whole-day text. Place it at the bottom above the attribution, without covering the crosshair. New texts go to `strings.xml`. Verify: `./gradlew ktlintCheck :app:lintDebug :app:assembleDebug` passes.
- [ ] 5.3 Add `SunLine`, a `Canvas` overlay from the crosshair centre at the azimuth angle, 30 % of the shorter side of the map, solid above and dashed below the horizon, drawn between the map and the crosshair (design D5). Verify: `./gradlew ktlintCheck :app:lintDebug :app:assembleDebug` passes.

## 6. Integration checks

- [ ] 6.1 Run the full local CI simulation. Verify: `./scripts/verify-local.sh` passes every step (ktlint, Android lint, unit tests, assemble), and `openspec validate --all --strict` passes.
- [ ] 6.2 Push and check CI. Verify: the `specs` and `build` jobs are green and the `app-debug.apk` artifact is attached to the run.
- [ ] 6.3 On-device check by the user with the CI APK. Expected:
  - At launch, the panel shows the current time with `UTC+1`/`UTC+2` and `Europe/Zurich`.
  - With the map at `46.6863° N, 7.8632° E`, choosing 2025-12-21 shows sunrise `08:10` ±2 and sunset `16:43` ±2. Moving the slider to 12:00 shows about `173° S` / `19.7°` and a solid line pointing almost straight down; 02:00 shows a dashed line towards the upper right (NE).
  - "Now" returns to the current time.
  - Rotation and tilt gestures do nothing.
  - The panel covers neither the crosshair nor the attribution.
  - Screen rotation keeps the selected time.

  Record the results in the PR.
