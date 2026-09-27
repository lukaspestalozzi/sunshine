# Spec Delta

## MODIFIED Requirements

### Requirement: Sun direction line
The map SHALL show a line from the crosshair centre in the direction of the sun's azimuth, where
up on the screen is north (see map-view "Map orientation"). Its length SHALL be 30 % of the
shorter side of the map area. It SHALL be drawn according to the sunshine state at the selected
location and time (point-sunshine "Sunshine at an instant"):
- solid while it is sun;
- dashed while it is shade, which includes every time the sun is below the horizon;
- dotted while it is unknown or not yet computed.

#### Scenario: Sun in the east
- **WHEN** the sun's azimuth is 90°
- **THEN** the line points horizontally from the crosshair towards the right edge of the screen (±1°)

#### Scenario: Sun below the horizon
- **WHEN** the selected time is 2025-12-21 02:00 UTC+1 at 46.6863° N, 7.8632° E
- **THEN** the line is dashed and points towards azimuth 46.9° (north-east, ±1°)

#### Scenario: Sun behind a mountain
- **WHEN** the selected time is 2025-12-21 15:00 UTC+1 at 46.6863° N, 7.8632° E (sun above the astronomical horizon, but behind a peak)
- **THEN** the line is dashed

#### Scenario: Sunshine unknown
- **WHEN** the sunshine state at the selected time is unknown
- **THEN** the line is dotted
