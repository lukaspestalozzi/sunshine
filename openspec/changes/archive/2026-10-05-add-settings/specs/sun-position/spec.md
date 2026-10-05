# Spec Delta

## MODIFIED Requirements

### Requirement: Sun information panel
The map screen SHALL permanently show a panel, without covering the crosshair or the Settings
button (settings "Settings button"), with the following values for the selected location and time.
It SHALL update while the map moves and whenever the selected time changes. All numbers SHALL be
formatted independently of the device locale.
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
