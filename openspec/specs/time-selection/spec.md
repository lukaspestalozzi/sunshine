# time-selection Specification

## Purpose

Lets the user choose the date and time for which all sun information is shown, and makes the
time zone of every shown time explicit.

## Requirements

### Requirement: Time zone of the selected time
The selected time and every time shown by the app SHALL be wall-clock times in the device's time
zone, as it is when the map screen is created. The selected time SHALL be shown as
`<date> <time> UTC<offset>`, with the date as `yyyy-MM-dd`, the time as 24-hour `HH:mm`, and the
UTC offset valid at the selected instant written as `+H`, `-H`, `+H:MM` or `-H:MM` (no offset
text for UTC itself, i.e. `UTC`). The device time zone's ID SHALL be shown next to it. Digits and
separators SHALL NOT depend on the device locale.

#### Scenario: Winter time in Switzerland
- **WHEN** the device time zone is Europe/Zurich and the selected time is 2025-12-21 12:00 local time
- **THEN** the app shows `2025-12-21 12:00 UTC+1` and `Europe/Zurich`

#### Scenario: Summer time in Switzerland
- **WHEN** the device time zone is Europe/Zurich and the selected time is 2025-06-21 15:00 local time
- **THEN** the app shows `2025-06-21 15:00 UTC+2`

#### Scenario: Offset with minutes
- **WHEN** the device time zone is Asia/Kolkata and the selected time is 2025-06-21 06:00 local time
- **THEN** the app shows `2025-06-21 06:00 UTC+5:30`

#### Scenario: Locale with other digits and separators
- **WHEN** the device locale is German (de-CH), the device time zone is Europe/Zurich and the selected time is 2025-12-21 12:00
- **THEN** the app still shows `2025-12-21 12:00 UTC+1`

### Requirement: Initial selected time
When the map screen is first created, the selected time SHALL be the current time, truncated to
the minute. While the app process is alive, including across screen rotation, the selected time
SHALL be preserved. It SHALL NOT be preserved across app launches.

#### Scenario: App launch
- **WHEN** the app is launched at 2025-12-21 09:47:31 local time
- **THEN** the selected time is 2025-12-21 09:47

#### Scenario: Screen rotation
- **WHEN** the user has selected 2025-06-21 15:00 and then rotates the device
- **THEN** the selected time is still 2025-06-21 15:00

### Requirement: Choose the date
The user SHALL be able to choose any calendar date from a date picker. Choosing a date SHALL keep
the selected wall-clock time of day. If that time does not exist on the chosen date because of a
daylight-saving transition, the time SHALL move forward by the length of the gap.

#### Scenario: Change the date
- **WHEN** the selected time is 2025-12-21 14:35 and the user picks 2025-06-21
- **THEN** the selected time is 2025-06-21 14:35

#### Scenario: Time falls into the spring-forward gap
- **WHEN** the device time zone is Europe/Zurich, the selected time is 2025-03-29 02:30 and the user picks 2025-03-30
- **THEN** the selected time is 2025-03-30 03:30 UTC+2

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
