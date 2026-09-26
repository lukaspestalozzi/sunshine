# Spec Delta

## ADDED Requirements

### Requirement: Map orientation
The map SHALL always be shown north-up and flat: bearing 0° and no tilt. Rotation and tilt
gestures SHALL have no effect, so that up on the screen is always north.

#### Scenario: Rotation gesture
- **WHEN** the user makes a two-finger rotation gesture on the map
- **THEN** the map does not rotate and north stays at the top of the screen

#### Scenario: Tilt gesture
- **WHEN** the user makes a two-finger vertical drag gesture on the map
- **THEN** the map does not tilt
