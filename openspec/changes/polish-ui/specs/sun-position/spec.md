## MODIFIED Requirements

### Requirement: Sun information panel
The map screen SHALL permanently show a panel, without covering the crosshair or the Settings
button (settings "Settings button"), for the selected location and time. From top to bottom it
SHALL hold (user decisions, 2026-10-05 and 2026-10-06):
1. **Header:** the selected time (time-selection "Time zone of the selected time"; tapping it opens
   the clock dialog, time-selection "Exact time"), and the buttons `Date` (time-selection "Choose
   the date") and `Now` (time-selection "Return to now");
2. **Headline:** the sun periods of the selected day (point-sunshine "Sunshine in the information
   panel");
3. in `Sun hours` mode, the `Sun hours` line (sun-exposure-heatmap "Sun hours in the information
   panel");
4. **Time tape** (time-selection "Choose the time of day", "Time tape strip");
5. **Details:** a row `Details` that expands and collapses, at least 48 dp high to touch, with the
   content description `Show details` while collapsed and `Hide details` while expanded. Expanded,
   it shows the altitude (elevation-data "Altitude in the information panel"), the values below, and
   the device time zone's ID, e.g. `Europe/Zurich`.

The details SHALL be collapsed when the app is first launched, and SHALL keep the state the user
last chose across screen rotation and app launches (settings "Stored settings"). The panel SHALL
update while the map moves and whenever the selected time changes. All numbers SHALL be formatted
independently of the device locale. The values in the details:
- Azimuth: rounded to whole degrees (half up; 360 shown as 0), followed by the 8-point compass
  direction of the rounded value (N, NE, E, SE, S, SW, W, NW; each covers 45° centred on its
  direction), e.g. `173° S`.
- Elevation: one decimal (half up), e.g. `19.7°`, `-0.7°`.
- Civil dawn, sunrise, sunset, civil dusk: `HH:mm`, rounded to the nearest minute. When an
  event's UTC offset differs from the selected time's offset, the offset is appended as `UTC+H`,
  `UTC-H`, `UTC+H:MM` or `UTC-H:MM`, e.g. `07:12 UTC+2`. An absent event is shown as
  `none this day`.
- Day length: `<hours> h <minutes> min`, minutes rounded to the nearest minute, e.g. `8 h 33 min`.
- When the sun is above the horizon all day: the text `Sun above the horizon all day`; when it is
  below all day: `Sun below the horizon all day`.

#### Scenario: Azimuth and elevation formatting
- **WHEN** the details are expanded, the azimuth is 173.49° and the elevation is 19.66°
- **THEN** the details show `173° S` and `19.7°`

#### Scenario: Compass boundaries
- **WHEN** the details are expanded and the azimuth is 22.4°, 22.5°, or 359.6°
- **THEN** the details show `22° N`, `23° NE`, or `0° N` respectively

#### Scenario: Event with a different offset
- **WHEN** the details are expanded, the device time zone is Europe/Zurich, the selected time is 2025-03-30 01:00 UTC+1 and sunrise is at 07:11:45 UTC+2
- **THEN** the details show sunrise as `07:12 UTC+2`

#### Scenario: Polar night panel
- **WHEN** the details are expanded, the selected location is 78.2232° N, 15.6267° E, the device time zone is Europe/Oslo and the selected date is 2025-12-21
- **THEN** civil dawn, sunrise, sunset and civil dusk show `none this day`, the day length shows `0 h 0 min`, and the details show `Sun below the horizon all day`

#### Scenario: Panel follows the map
- **WHEN** the user pans the map
- **THEN** the panel's values change with the selected location while the map moves

#### Scenario: Details collapsed at first launch
- **WHEN** the app is launched for the first time after installing it
- **THEN** the panel shows the header, the headline, the time tape and a collapsed `Details` row, and no azimuth, elevation, twilight, day length, altitude or time zone

#### Scenario: Details kept
- **WHEN** the user expands the details, closes the app and launches it again
- **THEN** the details are expanded
