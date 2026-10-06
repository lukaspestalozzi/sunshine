## MODIFIED Requirements

### Requirement: Sunshine at an instant
For the selected location and an instant, the sunshine state SHALL be one of:
- **sun**: the sun's upper edge is above the terrain horizon at the sun's azimuth. The upper edge
  is at the apparent elevation of sun-position "Sun position" plus 0.266°. When the horizon at
  that azimuth is incomplete (terrain-horizon "Incomplete horizon"), sun applies only if the upper
  edge is above the horizon's upper bound.
- **shade**: the upper edge is at or below the horizon angle. When the horizon at that azimuth is
  incomplete, shade applies only if the upper edge is at or below the angle's lower bound.
- **unknown**: the horizon at the sun's azimuth is incomplete, and the upper edge is above its
  lower bound and at or below its upper bound.

Between two grid azimuths of the profile, the lower bound is interpolated like the angle, and the
upper bound is the larger of the two neighbouring upper bounds. This is the same rule as for the
overlay's cells (sun-shade-overlay "Unknown cells"), so that the panel and the overlay agree when
terrain data is missing.

The apparent elevation has no refraction at or below 0° geometric elevation (sun-position "Sun
position"). Where the terrain horizon lies at or below 0°, e.g. on summits, sun therefore begins
3–5 min too late and ends 3–5 min too early. This is a known limitation.

#### Scenario: Sun above the astronomical horizon, but behind a peak
- **WHEN** the selected location is Interlaken, 46.6863° N, 7.8632° E, and the instant is 2025-12-21 15:00 UTC+1
- **THEN** the sunshine state is shade

#### Scenario: Winter midday sun in Interlaken
- **WHEN** the selected location is Interlaken and the instant is 2025-12-21 12:00 UTC+1
- **THEN** the sunshine state is sun

#### Scenario: Sun below the astronomical horizon
- **WHEN** the sun's upper edge is below 0° apparent elevation and the terrain horizon at its azimuth is higher
- **THEN** the sunshine state is shade

#### Scenario: Missing tile too far away to matter
- **WHEN** the observer's eye is at 500 m, the horizon at the sun's azimuth is incomplete with a lower bound of 2° and an upper bound of 12.09° (a tile 20 km away is unavailable), and the sun's upper edge is at 20°
- **THEN** the sunshine state is sun

#### Scenario: Missing tile that could matter
- **WHEN** the same horizon and the sun's upper edge at 5°
- **THEN** the sunshine state is unknown

#### Scenario: Sun below the lower bound
- **WHEN** the same horizon and the sun's upper edge at 1.5°
- **THEN** the sunshine state is shade

#### Scenario: Panel and overlay agree with missing data
- **WHEN** the overlay is computed at map zoom 12, for suns at 9°, 14° and 24° elevation, and every DEM tile farther than 10 km from the map centre is unavailable
- **THEN** at least 99.5 % of the overlay's cells have the state that this rule gives at their sample points
