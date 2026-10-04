# Spec Delta

## MODIFIED Requirements

### Requirement: Offline button
The map screen SHALL show an Offline button directly to the right of the ⓘ button (map-view
"About and attributions"), in the same row, with the content description `Offline maps`. It SHALL
be at least 48 × 48 dp to touch, and SHALL NOT cover the ⓘ button, the coordinates or the
overlay toggle, on screens 360 dp wide or more, in portrait and landscape. Tapping it SHALL open
the Offline page. The system back gesture or button SHALL return from the Offline page to the
map, with the camera, the selected time and the overlay as they were.

#### Scenario: Open and return
- **WHEN** the map shows Interlaken at zoom 12 with `Sun hours` selected, and the user taps the Offline button and then goes back
- **THEN** the Offline page was shown, and the map shows Interlaken at zoom 12 with `Sun hours` selected

#### Scenario: Narrow screen
- **WHEN** the screen is 360 dp wide
- **THEN** the ⓘ button, the Offline button, the location button (gps-location "Location button") and the overlay toggle are fully on screen and do not overlap
