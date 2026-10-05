# Spec Delta

## MODIFIED Requirements

### Requirement: Choose the time of day
The user SHALL be able to choose the time of day with a slider that covers the selected day from
its start (00:00 local time) up to, but not including, the start of the next day, in steps
counted from the start of the day. On daylight-saving transition days the slider SHALL cover the
actual length of the day (e.g. 23 or 25 hours), so every instant of that day can be chosen and no
instant of another day can. The step SHALL be:
- while the overlay shows `Sun & shade` (sun-shade-overlay "Overlay toggle"): the `Sun & shade`
  step of the shade resolution (settings "Shade resolution"; 5 minutes with `Normal`);
- otherwise: 5 minutes.

While `Sun & shade` is shown, the selected time SHALL always be one of the steps, so that the
overlay shown belongs to the selected time (user decision, 2026-10-04). Whenever `Sun & shade`
becomes shown, the shade resolution changes while it is shown, or the selected time is set by
`Now` ("Return to now") or a date change ("Choose the date") while it is shown, the selected time
SHALL be rounded to the nearest step of its day; a time exactly halfway between two steps SHALL
round to the later one, and a time after the day's last step SHALL round to the last step. Leaving
`Sun & shade` SHALL keep the rounded time.

#### Scenario: Slider range on a normal day
- **WHEN** the selected date is 2025-12-21 in Europe/Zurich and `Sun & shade` is not shown
- **THEN** the slider's first position is 00:00 and its last position is 23:55

#### Scenario: Short day
- **WHEN** the selected date is 2025-03-30 in Europe/Zurich and `Sun & shade` is not shown
- **THEN** the slider has 276 positions (23 hours), and the position after 01:55 UTC+1 is 03:00 UTC+2

#### Scenario: Long day
- **WHEN** the selected date is 2025-10-26 in Europe/Zurich and `Sun & shade` is not shown
- **THEN** the slider has 300 positions (25 hours), and the local times 02:00 to 02:55 appear twice, first with UTC+2 and then with UTC+1

#### Scenario: Slider with a 10-minute step
- **WHEN** the shade resolution is `Fast`, `Sun & shade` is shown, and the selected date is 2025-12-21 in Europe/Zurich
- **THEN** the slider has 144 positions, from 00:00 to 23:50, 10 minutes apart

#### Scenario: Short day with a 10-minute step
- **WHEN** the shade resolution is `Fast`, `Sun & shade` is shown, and the selected date is 2025-03-30 in Europe/Zurich
- **THEN** the slider has 138 positions, and the position after 01:50 UTC+1 is 03:00 UTC+2

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
- **THEN** the selected time stays 14:40, and the slider moves in 5-minute steps again

### Requirement: Return to now
A "Now" control SHALL set the selected time to the current time, truncated to the minute; while
`Sun & shade` is shown, the time SHALL then be rounded to the nearest step ("Choose the time of
day"). The selected time SHALL NOT follow the clock afterwards.

#### Scenario: Now after browsing another date
- **WHEN** `Sun & shade` is not shown, the selected time is 2025-06-21 15:00 and the user taps "Now" at 2025-12-21 09:47:31
- **THEN** the selected time is 2025-12-21 09:47

#### Scenario: Now with Sun & shade
- **WHEN** the shade resolution is `Normal`, `Sun & shade` is shown, and the user taps "Now" at 2025-12-21 09:47:31
- **THEN** the selected time is 2025-12-21 09:45

#### Scenario: Time does not follow the clock
- **WHEN** the user taps "Now" at 09:47 and waits until 09:52 without touching any control
- **THEN** the selected time is still 09:47
