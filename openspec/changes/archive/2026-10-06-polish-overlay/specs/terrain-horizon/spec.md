## MODIFIED Requirements

### Requirement: Incomplete horizon
When terrain data that a ray needs cannot be obtained, the horizon at that azimuth SHALL be
recorded as incomplete, with two bounds:
- **Lower bound:** the largest angle found along the ray, including terrain found beyond the
  missing data, since unseen terrain can only raise the horizon. The ray SHALL continue past
  missing data.
- **Upper bound:** the largest angle at which terrain could appear at the distance of the nearest
  missing data or beyond, if it rose to the height bound there. The height bound is Mont Blanc's
  4810 m in Europe west of the Caucasus and 8849 m elsewhere; the earth curvature and refraction
  are those of "Horizon profile". The upper bound is never below the lower bound.

The horizon is still complete when no terrain within the missing area, however high, could rise
above the angle found. A complete horizon's upper bound equals its angle. The data cannot be
obtained when the location's DEM tiles are not in the cache without network, when the server
returns an error, or when no tile exists at any published zoom. The app SHALL NOT assume flat
terrain, height 0 m, or any other default height in place of missing data.

#### Scenario: Missing far tile behind a high near ridge
- **WHEN** at azimuth 180° a near ridge gives a horizon angle of 30°, and a tile 40 km away in that direction is unavailable
- **THEN** the horizon at azimuth 180° is complete with 30°, because no terrain 40 km away can rise above 30°

#### Scenario: Missing tile that could matter
- **WHEN** the observer's eye is at 500 m, at azimuth 90° the horizon found within 10 km is 2°, and a tile 20 km away in that direction is unavailable
- **THEN** the horizon at azimuth 90° is incomplete with a lower bound of 2° and an upper bound of 12.09° (±0.05°)

#### Scenario: Terrain beyond the missing data
- **WHEN** the observer's eye is at 500 m, at azimuth 90° the horizon found within 10 km is 2°, a tile 20 km away in that direction is unavailable, and terrain 30 km away in that direction appears at 6°
- **THEN** the horizon at azimuth 90° is incomplete with a lower bound of 6° and an upper bound of 12.09° (±0.05°)
