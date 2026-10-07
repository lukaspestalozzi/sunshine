# point-sunshine Specification

## Purpose

Tells the user when the sun actually shines at the selected location on the selected day, taking
terrain into account, and whether it shines there at the selected time. Unknown results are shown
as unknown.

## Requirements

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

### Requirement: Sun periods of the selected day
For the selected location and the selected date, the app SHALL determine every sun period: every
maximal interval within the selected day (the day window of sun-position "Sun events of the
selected day") in which the sunshine state is sun. The state is evaluated at least every 10 s, and
period boundaries are refined to 10 s. A day may have several periods, one, or none. Periods
shorter than 1 min are below the accuracy and are omitted.

When the sunshine state is unknown at any instant of the day at which the sun's upper edge is above
0° apparent elevation, the sun periods of that day SHALL be unknown as a whole. Otherwise they are
known, even if some horizon data is missing.

Tolerance: each period boundary SHALL be within 5 min of the value computed with the reference
resolution of `investigations/terrain-horizon-algorithms.md`. At the foot of cliffs, results depend
strongly on the data resolution and on the exact spot; the spike measured 5.5 min between 3 m and
1.6 m data at Bristen, and up to 3 weeks of sunless-season shift for a 100 m move.

#### Scenario: Two periods in Interlaken in winter
- **WHEN** the selected location is Interlaken, 46.6863° N, 7.8632° E, and the selected date is 2025-12-21 (Europe/Zurich)
- **THEN** there are two sun periods, 10:09–14:51 and 15:11–15:52 (each boundary ±5 min)

#### Scenario: Short winter sun in Lauterbrunnen
- **WHEN** the selected location is Lauterbrunnen, 46.5935° N, 7.9091° E, and the selected date is 2025-12-21 (Europe/Zurich)
- **THEN** there is one sun period, 11:47–13:13 (±5 min)

#### Scenario: Long summer day in Interlaken
- **WHEN** the selected location is Interlaken and the selected date is 2025-06-21 (Europe/Zurich)
- **THEN** there is one sun period, 06:04–20:09 UTC+2 (±5 min)

#### Scenario: Sunless season in Viganella
- **WHEN** the selected location is the church of Viganella, 46.0519° N, 8.1939° E, and the selected date is 2025-12-21
- **THEN** there is no sun period that day (documented sunless season: 11 November – 2 February)

#### Scenario: Missing data that cannot matter
- **WHEN** some horizon data is unavailable, but at every instant of the day the sun is either below the lower bound of the horizon where data is missing, or at an azimuth whose horizon is complete
- **THEN** the sun periods are known

### Requirement: Sunshine in the information panel
The sun information panel (sun-position "Sun information panel") SHALL show the sun periods of the
selected location and day as its headline: directly below the panel's header, in a larger type
than every other text of the panel (at least 18 sp; user decision, 2026-10-06: the headline is the
day's periods). Times are formatted like the sun events there: `HH:mm` rounded to the nearest
minute, with the UTC offset appended when it differs from the selected time's offset. The headline
reads:
- `Sunshine 10:09–14:51, 15:11–15:52`: all periods in chronological order;
- `Sunshine none this day`: no period;
- `Sunshine …`: while the horizon is being computed. A value from another location SHALL NOT be
  shown instead;
- `Sunshine unknown`: the periods are unknown.

A headline longer than the panel's width SHALL wrap onto further lines; it SHALL NOT be cut off.
The headline SHALL update when the location or the selected date changes, and when the network
returns after an unknown result.

#### Scenario: Several periods
- **WHEN** the sun periods are 10:08:50–14:51:10 and 15:11:20–15:52:10 UTC+1 and the selected time is in UTC+1
- **THEN** the panel's headline is `Sunshine 10:09–14:51, 15:11–15:52`

#### Scenario: No period
- **WHEN** there is no sun period on the selected day
- **THEN** the panel's headline is `Sunshine none this day`

#### Scenario: Unknown
- **WHEN** the sun periods of the selected day are unknown
- **THEN** the panel's headline is `Sunshine unknown`

#### Scenario: Moving to a new location
- **WHEN** the user pans to a location whose horizon is not yet computed
- **THEN** the headline is `Sunshine …` until it is computed, and never the previous location's periods

#### Scenario: Headline above the rest
- **WHEN** the panel is shown on a 360 dp wide screen with four sun periods
- **THEN** the headline is directly below the header, in the panel's largest type, and wraps instead of being cut off
