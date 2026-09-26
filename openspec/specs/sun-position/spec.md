# sun-position Specification

## Purpose

Tells the user where the sun is at the selected location and time, and when it rises and sets on
the selected day, ignoring terrain. It is the astronomical basis for all terrain-aware features.

## Requirements

### Requirement: Sun position
For the selected location and the selected time, the app SHALL determine the sun's azimuth
(compass direction of the sun's centre: 0° = north, clockwise, in [0°, 360°)) and its apparent
elevation (angle of the sun's centre above the astronomical horizon). The apparent elevation
SHALL include standard atmospheric refraction while the geometric elevation is above 0°; at or
below 0° geometric elevation it SHALL equal the geometric elevation (no refraction applied).
Tolerances: azimuth ±0.2°, elevation ±0.1°.

#### Scenario: Winter noon in Interlaken
- **WHEN** the selected location is 46.6863° N, 7.8632° E and the selected time is 2025-12-21 12:00 UTC+1
- **THEN** the azimuth is 173.5° (±0.2°) and the elevation is 19.7° (±0.1°)

#### Scenario: Summer afternoon in Interlaken
- **WHEN** the selected location is 46.6863° N, 7.8632° E and the selected time is 2025-06-21 15:00 UTC+2
- **THEN** the azimuth is 225.4° (±0.2°) and the elevation is 60.6° (±0.1°)

#### Scenario: Refraction just above the horizon
- **WHEN** the selected location is 46.6863° N, 7.8632° E and the selected time is 2025-12-21 08:20 UTC+1 (geometric elevation 0.53°)
- **THEN** the elevation is 0.9° (±0.1°)

#### Scenario: No refraction at or below 0° geometric elevation
- **WHEN** the selected location is 46.6863° N, 7.8632° E and the selected time is 2025-12-21 08:11 UTC+1
- **THEN** the elevation is -0.7° (±0.1°), equal to the geometric elevation

#### Scenario: Night
- **WHEN** the selected location is 46.6863° N, 7.8632° E and the selected time is 2025-12-21 02:00 UTC+1
- **THEN** the elevation is -60.1° (±0.1°)

### Requirement: Sun above the horizon
The sun SHALL count as above the horizon when the geometric elevation of its centre is at or above
-0.833°. This is the criterion that defines sunrise and sunset (upper edge of the sun on a
sea-level mathematical horizon, with standard refraction), so "above the horizon" changes exactly
at sunrise and sunset.

#### Scenario: Just after sunrise
- **WHEN** the selected location is 46.6863° N, 7.8632° E and the selected time is 2025-12-21 08:11 UTC+1 (one minute after sunrise)
- **THEN** the sun is above the horizon, although its shown elevation is negative

#### Scenario: Before sunrise
- **WHEN** the selected location is 46.6863° N, 7.8632° E and the selected time is 2025-12-21 08:05 UTC+1
- **THEN** the sun is below the horizon

### Requirement: Sun events of the selected day
For the selected location and the selected date, the app SHALL determine the first occurrence
within that day (from its start at 00:00 local time up to, but not including, the start of the next
day) of: civil dawn (sun centre rising through -6° geometric elevation), sunrise, sunset and civil
dusk (sun centre setting through -6°). Sunrise and sunset use the criterion of "Sun above the
horizon". Events are astronomical: sea-level observer, no terrain. Tolerance: ±2 minutes.
An event that does not occur within the selected day SHALL be reported as absent; it SHALL NOT be
replaced by an event of another day or by an estimate.

#### Scenario: Summer solstice in Interlaken
- **WHEN** the device time zone is Europe/Zurich, the selected location is 46.6863° N, 7.8632° E and the selected date is 2025-06-21
- **THEN** civil dawn is 04:55, sunrise 05:34, sunset 21:25 and civil dusk 22:06 (each ±2 min)

#### Scenario: Winter solstice in Interlaken
- **WHEN** the device time zone is Europe/Zurich, the selected location is 46.6863° N, 7.8632° E and the selected date is 2025-12-21
- **THEN** civil dawn is 07:35, sunrise 08:10, sunset 16:43 and civil dusk 17:19 (each ±2 min)

#### Scenario: Equinoxes in Interlaken
- **WHEN** the device time zone is Europe/Zurich, the selected location is 46.6863° N, 7.8632° E and the selected date is 2025-03-20, or 2025-09-22
- **THEN** sunrise is 06:31 and sunset 18:41, or sunrise 07:15 and sunset 19:25 respectively (each ±2 min)

#### Scenario: Day of the spring-forward transition
- **WHEN** the device time zone is Europe/Zurich, the selected location is 46.6863° N, 7.8632° E and the selected date is 2025-03-30
- **THEN** sunrise is 07:12 UTC+2 and sunset 19:55 UTC+2 (each ±2 min)

#### Scenario: Day of the fall-back transition
- **WHEN** the device time zone is Europe/Zurich, the selected location is 46.6863° N, 7.8632° E and the selected date is 2025-10-26
- **THEN** sunrise is 07:02 UTC+1 and sunset 17:22 UTC+1 (each ±2 min)

#### Scenario: Time zone far east of UTC
- **WHEN** the device time zone is Asia/Tokyo, the selected location is 35.6762° N, 139.6503° E and the selected date is 2025-06-21
- **THEN** sunrise is 04:26 and sunset 19:00 on 2025-06-21 (each ±2 min), not the events of another day

#### Scenario: Polar day
- **WHEN** the device time zone is Europe/Oslo, the selected location is 78.2232° N, 15.6267° E and the selected date is 2025-06-21
- **THEN** civil dawn, sunrise, sunset and civil dusk are all absent, and the sun is above the horizon for the whole day

#### Scenario: Polar night
- **WHEN** the device time zone is Europe/Oslo, the selected location is 78.2232° N, 15.6267° E and the selected date is 2025-12-21
- **THEN** civil dawn, sunrise, sunset and civil dusk are all absent, and the sun is below the horizon for the whole day

#### Scenario: Sunrise without sunset
- **WHEN** the device time zone is Europe/Oslo, the selected location is 69.6492° N, 18.9553° E and the selected date is 2025-05-16
- **THEN** sunrise is present, sunset is absent (the sun next sets after midnight)

#### Scenario: Sunset before sunrise
- **WHEN** the device time zone is Europe/Oslo, the selected location is 69.6492° N, 18.9553° E and the selected date is 2025-05-17
- **THEN** both sunset and sunrise are present and sunset is earlier in the day than sunrise

### Requirement: Day length
The day length SHALL be the total time within the selected day during which the sun is above the
horizon. When the sun is above the horizon all day it SHALL equal the actual length of that day
(24 h, or e.g. 23 h / 25 h on daylight-saving transition days); when it is below all day it SHALL
be 0.
Tolerance: ±2 minutes.

#### Scenario: Interlaken solstices
- **WHEN** the selected location is 46.6863° N, 7.8632° E and the selected date is 2025-06-21, or 2025-12-21 (device time zone Europe/Zurich)
- **THEN** the day length is 15 h 51 min, or 8 h 33 min respectively (±2 min)

#### Scenario: Polar day and night
- **WHEN** the selected location is 78.2232° N, 15.6267° E, the device time zone is Europe/Oslo and the selected date is 2025-06-21, or 2025-12-21
- **THEN** the day length is 24 h 0 min, or 0 h 0 min respectively

#### Scenario: Sunset before sunrise
- **WHEN** the selected location is 69.6492° N, 18.9553° E, the device time zone is Europe/Oslo and the selected date is 2025-05-17
- **THEN** the day length is (sunset − 00:00) + (24:00 − sunrise)

#### Scenario: Sunrise without sunset
- **WHEN** the selected location is 69.6492° N, 18.9553° E, the device time zone is Europe/Oslo and the selected date is 2025-05-16
- **THEN** the day length is 24:00 − sunrise

### Requirement: Sun information panel
The map screen SHALL permanently show a panel, without covering the crosshair or the map
attribution, with the following values for the selected location and time. It SHALL update while
the map moves and whenever the selected time changes. All numbers SHALL be formatted independently
of the device locale.
- Azimuth: rounded to whole degrees (half up; 360 shown as 0), followed by the 8-point compass
  direction of the rounded value (N, NE, E, SE, S, SW, W, NW; each covers 45° centred on its
  direction), e.g. `173° S`.
- Elevation: one decimal (half up), e.g. `19.7°`, `-0.7°`.
- Civil dawn, sunrise, sunset, civil dusk: `HH:mm`, rounded to the nearest minute. When an
  event's UTC offset differs from the selected time's offset, the offset is appended as in the
  selected time, e.g. `07:12 UTC+2`. An absent event is shown as `none this day`.
- Day length: `<hours> h <minutes> min`, minutes rounded to the nearest minute, e.g. `8 h 33 min`.
- When the sun is above the horizon all day: the text `Sun above the horizon all day`; when it is
  below all day: `Sun below the horizon all day`.

#### Scenario: Azimuth and elevation formatting
- **WHEN** the azimuth is 173.49° and the elevation is 19.66°
- **THEN** the panel shows `173° S` and `19.7°`

#### Scenario: Compass boundaries
- **WHEN** the azimuth is 22.4°, 22.5°, or 359.6°
- **THEN** the panel shows `22° N`, `23° NE`, or `0° N` respectively

#### Scenario: Event with a different offset
- **WHEN** the device time zone is Europe/Zurich, the selected time is 2025-03-30 01:00 UTC+1 and sunrise is at 07:11:45 UTC+2
- **THEN** the panel shows sunrise as `07:12 UTC+2`

#### Scenario: Polar night panel
- **WHEN** the selected location is 78.2232° N, 15.6267° E, the device time zone is Europe/Oslo and the selected date is 2025-12-21
- **THEN** civil dawn, sunrise, sunset and civil dusk show `none this day`, the day length shows `0 h 0 min`, and the panel shows `Sun below the horizon all day`

#### Scenario: Panel follows the map
- **WHEN** the user pans the map
- **THEN** the panel's values change with the selected location while the map moves

### Requirement: Sun direction line
The map SHALL show a line from the crosshair centre in the direction of the sun's azimuth, where
up on the screen is north (see map-view "Map orientation"). Its length SHALL be 30 % of the
shorter side of the map area. It SHALL be drawn solid while the sun is above the horizon and
dashed while it is below.

#### Scenario: Sun in the east
- **WHEN** the sun's azimuth is 90°
- **THEN** the line points horizontally from the crosshair towards the right edge of the screen (±1°)

#### Scenario: Sun below the horizon
- **WHEN** the selected time is 2025-12-21 02:00 UTC+1 at 46.6863° N, 7.8632° E
- **THEN** the line is dashed and points towards azimuth 46.9° (north-east, ±1°)
