## MODIFIED Requirements

### Requirement: Offline button
The map screen SHALL show an Offline button directly to the right of the Settings button
(settings "Settings button"), in the same row, with the content description `Offline maps`. It SHALL
be at least 48 × 48 dp to touch, and SHALL NOT cover the Settings button, the coordinates or the
overlay toggle, on screens 360 dp wide or more, in portrait and landscape. Tapping it SHALL open
the Offline page. The Offline page SHALL have a top bar with the title `Offline maps` and a back
arrow with the content description `Back`, at least 48 × 48 dp to touch (user decision,
2026-10-05). The back arrow and the system back gesture or button SHALL return from the Offline
page to the map, with the camera, the selected time and the overlay as they were.

#### Scenario: Open and return
- **WHEN** the map shows Interlaken at zoom 12 with `Sun hours` selected, and the user taps the Offline button and then goes back
- **THEN** the Offline page was shown, and the map shows Interlaken at zoom 12 with `Sun hours` selected

#### Scenario: Back arrow
- **WHEN** the user opens the Offline page from Interlaken at zoom 12 and taps the back arrow
- **THEN** the map shows Interlaken at zoom 12, and a download started on the page continues

#### Scenario: Narrow screen
- **WHEN** the screen is 360 dp wide
- **THEN** the Settings button, the Offline button, the location button (gps-location "Location button") and the overlay toggle are fully on screen and do not overlap
