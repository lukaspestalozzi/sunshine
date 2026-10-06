## MODIFIED Requirements

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
