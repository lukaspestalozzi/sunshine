# Spec Delta

## Purpose

Shows the device's own position on the map and moves the map there on request, so that the
selected location, and with it every sun and terrain feature, can be "where I am", online and
offline.

## ADDED Requirements

### Requirement: Location button
The map screen SHALL show a location button directly to the right of the Offline button
(offline-regions "Offline button"), in the same row, with the content description `My location`.
It SHALL be at least 48 × 48 dp to touch and SHALL NOT cover the ⓘ button, the Offline button,
the coordinates or the overlay toggle, on screens 360 dp wide or more, in portrait and landscape.
The button SHALL show which of these states it is in:
- **idle**: no fresh position is known and the user is not waiting for one;
- **waiting**: the user tapped the button without a fresh position; the button's icon is animated
  continuously;
- **ready**: a fresh position ("Position dot") is known.

#### Scenario: Button present
- **WHEN** the map screen is shown
- **THEN** the location button is shown right of the Offline button, with the content description `My location`

#### Scenario: Ready without a tap
- **WHEN** location access is allowed and a fresh position arrives while the user has not tapped the button
- **THEN** the button shows the ready state, and the map does not move

### Requirement: Location permission
The app SHALL ask for location access only when the user taps the location button and access is
not allowed yet; it SHALL NOT ask at launch. It SHALL ask for precise and approximate access
while the app is in use; it SHALL NOT ask for background location access. After the user's answer:
- precise or approximate access allowed: the tap is handled as described in "Centre on the
  position" (with a fresh position) or "Wait for a position" (without one);
- access refused, and Android would show the dialog again: nothing else happens; the next tap asks
  again;
- access refused, and Android no longer shows the dialog: a notice reads `Location access is off
  for Sunshine.` with a button `Settings` that opens the app's page in the system settings.

While access is refused, every tap that Android answers without a dialog SHALL show that notice
again. The button SHALL stay idle. A notice SHALL NOT block the map and SHALL disappear when its
button is tapped, when it is swiped away, or after 10 s.

#### Scenario: First tap
- **WHEN** the user taps the location button for the first time after installing the app
- **THEN** Android's dialog asks for location access while the app is in use

#### Scenario: No question at launch
- **WHEN** the app is launched and location access has never been asked
- **THEN** no location dialog is shown and no position dot is drawn

#### Scenario: Refused for good
- **WHEN** location access is refused and Android no longer shows the dialog, and the user taps the location button
- **THEN** the notice `Location access is off for Sunshine.` with the button `Settings` is shown, and the location button stays idle

#### Scenario: Open the settings
- **WHEN** the user taps `Settings` in that notice
- **THEN** the system settings page of the app opens

### Requirement: Approximate location
When only approximate location access is allowed, the dot and its accuracy circle SHALL be shown
as for any position, with the accuracy the device reports. Each tap of the location button SHALL
then also show the notice `Approximate location only. Allow precise location for your exact
position.` with the button `Settings` that opens the app's page in the system settings.

#### Scenario: Approximate access
- **WHEN** only approximate location access is allowed, a fresh position is known and the user taps the location button
- **THEN** the map centre moves to the position, and the approximate-location notice with `Settings` is shown

### Requirement: Location switched off
When location is switched off on the device and the user taps the location button, the app SHALL
show the notice `Location is switched off.` with the button `Settings` that opens the device's
location settings, and the button SHALL stay idle (no waiting animation that could never end).
While location is switched off, no fresh position arrives; a dot already shown turns old
("Position dot").

#### Scenario: Tap with location off
- **WHEN** location access is allowed, location is switched off on the device, and the user taps the location button
- **THEN** the notice `Location is switched off.` with `Settings` is shown, the button stays idle, and the map does not move

### Requirement: Position dot
While location access is allowed and the map screen is visible, the map SHALL show the device's
latest position as a dot above the map tiles and the sun-shade overlay, with a translucent circle
around it whose radius is the position's reported horizontal accuracy (±10 %). A position is
**fresh** when it was received while the map screen is visible, at most 30 s (±1 s) ago. A
position that is not fresh is **old**: the device's last known position from before the map
screen became visible, or a position received more than 30 s ago. An old position SHALL be drawn in
grey, a fresh one in colour; the next fresh position SHALL turn the dot back to colour. Before any
position is known, no dot SHALL be drawn. A position whose accuracy is not reported SHALL be drawn
without a circle.

#### Scenario: Fresh position
- **WHEN** location access is allowed and the device reports a position 46.6863° N, 7.8632° E with an accuracy of 8 m
- **THEN** a coloured dot is drawn at 46.6863° N, 7.8632° E (±0.0001°) with a circle of 8 m radius (±10 %)

#### Scenario: Signal lost
- **WHEN** the dot is coloured and no new position arrives for 31 s
- **THEN** the dot is grey at the last position, and the location button leaves the ready state

#### Scenario: Signal returns
- **WHEN** the dot is grey and a new position arrives
- **THEN** the dot is coloured at the new position, and the location button shows the ready state

#### Scenario: Last known position at start
- **WHEN** the app is opened, location access is allowed, and the device knows a position from earlier but no new position has arrived yet
- **THEN** the dot is drawn grey at that earlier position, and the location button is idle

#### Scenario: Without network
- **WHEN** the device has no network connection and the GPS reports a position
- **THEN** the dot is drawn at that position as with a network connection

### Requirement: Centre on the position
When the user taps the location button in the ready state, the map SHALL move so that its centre,
and with it the selected location (map-view "Selected location crosshair"), is the fresh position
(±0.0001°), keeping the current zoom level (±0.01). The map SHALL move only on such a tap; a new
position SHALL never move the map by itself. After the move the map can be panned as usual.

#### Scenario: Tap when ready
- **WHEN** the map shows Bern at zoom 12, the fresh position is 46.6863° N, 7.8632° E, and the user taps the location button
- **THEN** the map centre is 46.6863° N, 7.8632° E (±0.0001°), the zoom level is 12 (±0.01), and the coordinates read `46.6863° N, 7.8632° E`

#### Scenario: Walking after centring
- **WHEN** the user has centred the map on the position and then walks 200 m, with fresh positions arriving
- **THEN** the dot moves with the positions, and the map centre stays where it was

### Requirement: Wait for a position
When the user taps the location button while no fresh position is known (no dot, or an old one),
location access is allowed and location is switched on, the button SHALL enter the waiting state.
It SHALL stay waiting, without a time limit, until a fresh position arrives; then it SHALL show the
ready state and the map SHALL NOT move. A tap while waiting SHALL end the waiting state (idle);
position updates continue and the dot is still drawn. The waiting state SHALL survive a screen
rotation and a visit to the About or Offline page.

#### Scenario: Tap without a position
- **WHEN** location access is allowed, no fresh position is known, and the user taps the location button
- **THEN** the button's icon is animated, and the map does not move

#### Scenario: Position arrives while waiting
- **WHEN** the button is waiting and a fresh position arrives
- **THEN** the animation stops, the button shows the ready state, the dot is drawn, and the map does not move

#### Scenario: Second tap after waiting
- **WHEN** the button turned ready after waiting and the user taps it
- **THEN** the map centre moves to the fresh position (±0.0001°)

#### Scenario: Stop waiting
- **WHEN** the button is waiting and the user taps it
- **THEN** the animation stops, the button is idle, and the map does not move

#### Scenario: Old dot is not a position to centre on
- **WHEN** the dot is grey and the user taps the location button
- **THEN** the button is waiting, and the map does not move

### Requirement: Location updates only while visible
The app SHALL receive position updates only while the map screen is visible, at most one per
second, and SHALL stop them when the app goes to the background, the screen turns off, or the
About or Offline page is opened. When the map screen becomes visible again, updates SHALL resume;
until the first fresh position arrives the dot is old.

#### Scenario: App to the background
- **WHEN** the dot is shown and the user switches to another app
- **THEN** the app receives no position updates until the map screen is visible again

#### Scenario: Back to the map
- **WHEN** the user returns to the map after 5 minutes in another app
- **THEN** the dot is grey at the last position until a fresh position arrives
