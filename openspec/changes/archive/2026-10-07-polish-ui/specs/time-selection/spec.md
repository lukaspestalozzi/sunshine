## MODIFIED Requirements

### Requirement: Time zone of the selected time
The selected time and every time shown by the app SHALL be wall-clock times in the device's time
zone, as it is when the map screen is created. The selected time SHALL be shown in the header of
the sun information panel (sun-position "Sun information panel") as
`<weekday> <day> <month>[ <year>] · <time>[ UTC<offset>]` (user decision, 2026-10-06):
- the weekday and the month as English three-letter names (`Mon` … `Sun`, `Jan` … `Dec`), the day
  of the month without a leading zero;
- the year only when it differs from the current year in the device's time zone;
- the time as 24-hour `HH:mm`;
- the UTC offset valid at the selected instant only on a day whose UTC offset changes (a
  daylight-saving transition day), where a wall-clock time can occur twice or not at all. It is
  written `UTC` for offset zero, else as `+H`, `-H`, `+H:MM` or `-H:MM` after `UTC`.

The device time zone's ID SHALL be shown in the panel's details. Names, digits and separators
SHALL NOT depend on the device locale.

#### Scenario: Winter time in Switzerland
- **WHEN** the device time zone is Europe/Zurich, the current date is 2025-12-21 and the selected time is 2025-12-21 12:00 local time
- **THEN** the header shows `Sun 21 Dec · 12:00`, and the details show `Europe/Zurich`

#### Scenario: Summer time in Switzerland
- **WHEN** the device time zone is Europe/Zurich, the current date is in 2025 and the selected time is 2025-06-21 15:00 local time
- **THEN** the header shows `Sat 21 Jun · 15:00`

#### Scenario: Another year
- **WHEN** the device time zone is Europe/Zurich, the current date is in 2025 and the selected time is 2027-12-21 12:00
- **THEN** the header shows `Tue 21 Dec 2027 · 12:00`

#### Scenario: Fall-back day
- **WHEN** the device time zone is Europe/Zurich and the selected time is the first 02:30 of 2025-10-26, before the clocks go back
- **THEN** the header shows `Sun 26 Oct · 02:30 UTC+2`, and the second 02:30 of that day shows `Sun 26 Oct · 02:30 UTC+1`

#### Scenario: Offset with minutes
- **WHEN** the device time zone is Asia/Kolkata, the current date is in 2025 and the selected time is 2025-06-21 06:00 local time
- **THEN** the header shows `Sat 21 Jun · 06:00`, without an offset, as the day has no clock change

#### Scenario: Locale with other digits and separators
- **WHEN** the device locale is German (de-CH), the device time zone is Europe/Zurich, the current date is in 2025 and the selected time is 2025-12-21 12:00
- **THEN** the header still shows `Sun 21 Dec · 12:00`

### Requirement: Choose the time of day
The user SHALL be able to choose the time of day on a time tape (user decision, 2026-10-05): a
horizontal scale of the selected day that moves under a fixed needle at its centre. The selected
time is the time under the needle. The tape covers the selected day from its start (00:00 local
time) up to, but not including, the start of the next day, in steps counted from the start of the
day. On daylight-saving transition days the tape SHALL cover the actual length of the day (e.g. 23
or 25 hours), so every instant of that day can be chosen and no instant of another day can. The
step SHALL be:
- while the overlay shows `Sun & shade` (sun-shade-overlay "Overlay toggle"): the `Sun & shade`
  step of the shade resolution (settings "Shade resolution"; 5 minutes with `Normal`);
- otherwise: 5 minutes.

The tape SHALL be drawn at 70 dp per hour (±1 dp), with a tick and the wall-clock hour (`0` …
`23`) at the start of every hour, and SHALL be at least 48 dp high to touch. It SHALL be operated
like this:
- **Drag:** the scale follows the finger; the selected time changes at every step the needle
  crosses.
- **Fling:** after a fling the scale keeps moving and slows down; when it stops, the needle rests
  on a step.
- **Tap:** a tap on the scale selects the step nearest to the tapped point.
- **Edges:** the needle SHALL reach the day's first and last step and stop there (user decision,
  2026-10-05); the tape never shows another day.
- **Exact times:** a selected time between two steps (from `Now`, a date change or the clock
  dialog, "Exact time") SHALL be shown at its exact position; the next drag moves it onto the
  steps.
- **Accessibility:** the tape SHALL have the content description `Time of day`, state the selected
  time as `HH:mm`, and offer the actions `Earlier` and `Later`, which move one step.

Requirements that refer to the time slider, its positions or moving it mean this tape, its steps
and moving it.

While `Sun & shade` is shown, the selected time SHALL always be one of the steps, so that the
overlay shown belongs to the selected time (user decision, 2026-10-04). Whenever `Sun & shade`
becomes shown, the shade resolution changes while it is shown, or the selected time is set by
`Now` ("Return to now"), a date change ("Choose the date") or the clock dialog ("Exact time")
while it is shown, the selected time SHALL be rounded to the nearest step of its day; a time
exactly halfway between two steps SHALL round to the later one, and a time after the day's last
step SHALL round to the last step. Leaving `Sun & shade` SHALL keep the rounded time.

#### Scenario: Slider range on a normal day
- **WHEN** the selected date is 2025-12-21 in Europe/Zurich and `Sun & shade` is not shown
- **THEN** the tape's first step is 00:00 and its last step is 23:55

#### Scenario: Short day
- **WHEN** the selected date is 2025-03-30 in Europe/Zurich and `Sun & shade` is not shown
- **THEN** the tape has 276 steps (23 hours), and the step after 01:55 UTC+1 is 03:00 UTC+2

#### Scenario: Long day
- **WHEN** the selected date is 2025-10-26 in Europe/Zurich and `Sun & shade` is not shown
- **THEN** the tape has 300 steps (25 hours), and the local times 02:00 to 02:55 appear twice, first with UTC+2 and then with UTC+1

#### Scenario: Slider with a 10-minute step
- **WHEN** the shade resolution is `Fast`, `Sun & shade` is shown, and the selected date is 2025-12-21 in Europe/Zurich
- **THEN** the tape has 144 steps, from 00:00 to 23:50, 10 minutes apart

#### Scenario: Short day with a 10-minute step
- **WHEN** the shade resolution is `Fast`, `Sun & shade` is shown, and the selected date is 2025-03-30 in Europe/Zurich
- **THEN** the tape has 138 steps, and the step after 01:50 UTC+1 is 03:00 UTC+2

#### Scenario: Rounded when Sun & shade is selected
- **WHEN** the shade resolution is `Fast`, the overlay is off, the selected time is 2025-12-21 14:35, and the user selects `Sun & shade`
- **THEN** the selected time is 2025-12-21 14:40

#### Scenario: Rounded down
- **WHEN** the shade resolution is `Fast`, the overlay is off, the selected time is 2025-12-21 14:34, and the user selects `Sun & shade`
- **THEN** the selected time is 2025-12-21 14:30

#### Scenario: End of the day
- **WHEN** the shade resolution is `Fast`, the overlay is off, the selected time is 2025-12-21 23:57, and the user selects `Sun & shade`
- **THEN** the selected time is 2025-12-21 23:50

#### Scenario: Time kept when leaving Sun & shade
- **WHEN** the shade resolution is `Fast`, `Sun & shade` is shown at 14:40, and the user selects `Off`
- **THEN** the selected time stays 14:40, and the tape moves in 5-minute steps again

#### Scenario: Drag by an hour
- **WHEN** `Sun & shade` is not shown, the selected time is 2025-12-21 12:00 and the user drags the tape 70 dp to the left
- **THEN** the selected time is 13:00, having passed 12:05, 12:10 and every step in between

#### Scenario: Tap to jump
- **WHEN** `Sun & shade` is not shown, the selected time is 2025-12-21 12:00 and the user taps the tape 36 dp right of the needle
- **THEN** the selected time is 12:30, the step nearest to 12:30:51

#### Scenario: Fling stops at the day's edge
- **WHEN** the selected time is 2025-12-21 02:00 and the user flings the tape towards earlier times
- **THEN** the tape stops with 00:00 under the needle, and the selected time is 2025-12-21 00:00

#### Scenario: Exact time on the tape
- **WHEN** `Sun & shade` is not shown and the user taps `Now` at 09:47:31
- **THEN** the needle points between 09:45 and 09:50, 2/5 of the way, and dragging moves it onto the steps

## ADDED Requirements

### Requirement: Time tape strip
The time tape SHALL show a strip along its scale, coloured per step of the tape at the crosshair
(map-view "Selected location crosshair") on the selected date (user decisions, 2026-10-05):
- **night** (dark blue-grey, `#263238`): the sun's upper edge at the crosshair is at or below the
  astronomical horizon (sun-position "Sun above the horizon"); known at once, whatever the source
  below;
- **sun** (warm yellow, `#FFD54F`): the source says sun;
- **shade** (the overlay's shade colour, `#455A64`, sun-shade-overlay "Overlay appearance"): the
  source says shade while the sun is above the astronomical horizon, i.e. terrain shade;
- **unknown** (the overlay's grey stripes, `#9E9E9E`, over the not-computed colour): the source
  says unknown;
- **not computed yet** (light grey, `#E0E0E0`): the source has no state for the step yet.

The source SHALL be (user decision, 2026-10-05):
- while `Sun & shade` is shown and the map zoom is 11 or more: the `Sun & shade` day of the visible
  area (sun-shade-overlay "Overlay of the whole day"), the state of the cell under the crosshair at
  each computed step; a step it has not computed is not computed yet;
- while `Sun hours` is shown and the map zoom is 11 or more: the heatmap's day of the visible area
  (sun-exposure-heatmap "Heatmap updates"); each tape step takes the state of the heatmap's step
  at or before it;
- otherwise: the state of point-sunshine "Sunshine at an instant" at the crosshair at each step,
  from its horizon profile; every step is not computed yet while the horizon is computed, and
  unknown where the ground height is unknown.

While the camera moves, the strip SHALL keep its states; when it rests, they SHALL be those of the
new crosshair and its day. Each step SHALL take its colour within 100 ms of its state becoming
known, so that the strip fills outward from the needle as a day is computed, nearest to the
selected time first. The strip replaces the progress bar of the overlay's day (sun-shade-overlay
"Overlay status card").

Right of the tape, the panel SHALL always show the strip's progress as a whole percentage, e.g.
`25 %` (user decision, 2026-10-07): while the strip comes from a day, the share of that day's steps
computed, rounded down, so that `100 %` means the whole day; otherwise `0 %` while the crosshair's
horizon is computed and `100 %` once it is.

#### Scenario: Overlay off in Interlaken
- **WHEN** the overlay is off, the crosshair is at 46.6863° N, 7.8632° E, the selected date is 2025-12-21 in Europe/Zurich and the horizon is computed
- **THEN** the steps from 10:10 to 14:50 and from 15:15 to 15:50 are sun, the steps from 08:15 to 10:05, 14:55 to 15:10 and 15:55 to 16:40 are shade, and the steps up to 08:05 and from 16:45 are night (each boundary ±1 step), and the progress shows `100 %`

#### Scenario: Horizon not yet computed
- **WHEN** the overlay is off and the horizon of a new crosshair is being computed
- **THEN** every daytime step is not computed yet, the night steps are night, and the progress shows `0 %`

#### Scenario: Day partly computed
- **WHEN** `Sun & shade` is shown at zoom 12 and 72 of the day's 288 steps are computed, nearest to 12:00 first
- **THEN** the 72 steps around 12:00 under the needle have their colours, the other daytime steps are not computed yet, and the progress shows `25 %`

#### Scenario: Missing terrain
- **WHEN** the overlay is off, offline, and the crosshair's horizon is unknown towards the sun between 14:00 and 15:00
- **THEN** those steps are hatched as unknown

#### Scenario: Sun hours
- **WHEN** `Sun hours` is shown at zoom 12 with the `Normal` resolution and the heatmap's step at 14:10 is sun at the crosshair cell
- **THEN** the tape's steps 14:10 and 14:15 are sun

### Requirement: Exact time
Tapping the selected time in the panel's header SHALL open a 24-hour clock dialog showing the
selected hour and minute. Confirming it with `OK` SHALL set the selected time to that wall-clock
time on the selected date, rounded to the step while `Sun & shade` is shown ("Choose the time of
day"). A time in a spring-forward gap SHALL move forward by the length of the gap; a time that
occurs twice on a fall-back day SHALL be its first occurrence, before the clocks go back.
`Cancel`, back, or a tap outside the dialog SHALL leave the selected time unchanged.

#### Scenario: Type a time
- **WHEN** `Sun & shade` is not shown, the selected time is 2025-12-21 09:47, and the user taps the time, sets 14:32 and confirms
- **THEN** the selected time is 2025-12-21 14:32

#### Scenario: Rounded with Sun & shade
- **WHEN** `Sun & shade` is shown with the `Normal` resolution and the user sets 14:32 in the clock dialog
- **THEN** the selected time is 14:30

#### Scenario: Time in the spring-forward gap
- **WHEN** the device time zone is Europe/Zurich, the selected date is 2025-03-30, and the user sets 02:30
- **THEN** the selected time is 2025-03-30 03:30 UTC+2

#### Scenario: Cancelled
- **WHEN** the user opens the clock dialog, changes the hour and taps `Cancel`
- **THEN** the selected time is unchanged
