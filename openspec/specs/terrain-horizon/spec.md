# terrain-horizon Specification

## Purpose

Describes the terrain horizon seen from a location: how high the skyline rises in each compass
direction. Terrain-aware sunshine is decided against it. Where data is missing, the horizon is
explicitly incomplete instead of assumed flat.

## Requirements

### Requirement: Horizon profile
For a location, the app SHALL determine a horizon profile: for every azimuth on a 0.25° grid
(0°, 0.25°, …, 359.75°), the horizon angle, which is the largest elevation angle of the terrain
seen from the observer. Between grid azimuths, the angle is interpolated linearly between the two
neighbouring grid values.

The observer's eye is 1.7 m above the ground elevation at the location. The elevation angle of a
terrain point at ground distance d and height h is `atan((h − h_eye − d²·(1 − k) / (2R)) / d)`,
with R = 6,371 km and terrain refraction coefficient k = 0.13. Terrain is considered up to 150 km
away. It is sampled along each azimuth at steps of at most half a pixel of the data used there.

The data resolution depends on distance:
- 3.3 m pixels (zoom 14) up to 1.5 km;
- 13 m (zoom 12) up to 6 km;
- 26 m (zoom 11) up to 25 km;
- 52 m (zoom 10) beyond.

Where a zoom is not published for an area, the next coarser published zoom is used there.

The profile SHALL be exact wherever it can decide the sun's visibility on some day of the year. At
azimuths or angles the sun never reaches, the profile may stop early and record only that the sun
is always blocked there, or that terrain cannot block it there. The profile does not depend on the
date or time.

#### Scenario: Single ridge
- **WHEN** the only terrain above the observer's eye is a ridge 1000 m higher than the eye, 5 km away at azimuth 180°
- **THEN** the horizon angle at azimuth 180° is 11.29° (±0.05°)

#### Scenario: Earth curvature
- **WHEN** the only terrain above the observer's eye is a peak 4000 m higher than the eye, 100 km away at azimuth 90°
- **THEN** the horizon angle at azimuth 90° is 1.90° (±0.05°), not the 2.29° a flat earth would give

#### Scenario: Profile reused across dates
- **WHEN** the selected date changes and the selected location does not
- **THEN** no terrain data is read again, and the sunshine for the new date is derived from the existing profile

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

### Requirement: Horizon limits
The horizon SHALL be derived from a height field (DEM), which describes the bare ground.
Overhangs and holes in rock, trees and buildings are not part of it.

#### Scenario: Hole in a ridge
- **WHEN** a ridge has a natural hole through which the sun shines on certain days (e.g. the Martinsloch above Elm)
- **THEN** the ridge counts as solid, and the app shows shade at those times
