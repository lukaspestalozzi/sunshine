# Proposal

## Why

On the trail, the first question is "will the sun reach me here, and when?". Today the hiker has
to find their own position on the map by hand before the sun panel and the overlay answer it.
Showing the device's GPS position, and moving the map there on request, makes the app answer for
"here" with one tap, online and offline alike.

Roadmap: implements a new entry #9, `add-gps-location`, added to `docs/roadmap.md` by this change.
GPS location was listed under the post-1.0 candidates; it is done before #8 `add-settings` by user
decision (explore session, 2026-10-02), because it is small and does not depend on settings.

## What Changes

- `app`: **position dot.** Once the user has allowed location access, the map shows the device's
  position as a dot with a circle of its reported accuracy, whenever the map screen is visible
  (user decision). The dot is drawn by MapLibre's location component, fed by MapLibre's default
  location engine, which uses Android's own location service (GPS and network positions) and no
  Google Play Services (user decisions).
- `app`: **old positions are shown as old.** A position that is not fresh (no update for 30 s, or
  the last known position from before the app started tracking) is drawn grey; the next fresh
  position turns it normal again (user decision).
- `app`: **location button.** A third button in the top-left row, right of the Offline button
  (user decision). The crosshair stays the selected location: the map never moves by itself
  (user decision).
  - With a fresh position, a tap moves the map centre to it, at the current zoom.
  - Without one, a tap starts a waiting animation on the button. It runs until a fresh position
    arrives, without a time limit (user decision); then the animation stops and the button shows
    it is ready. The map does not move; a second tap does that (user decision). A tap during the
    animation stops it.
- `app`: **permission.** Location access is asked on the first tap of the button, never at launch,
  and only while the app is in use (no background location). If the user allows only approximate
  location, the dot and its large circle are shown with a note that the position is approximate.
  When Android no longer shows the permission dialog, a tap shows a notice with a button to the
  app's system settings (user decision). When location is switched off on the device, a tap
  shows a notice with a button to the location settings.
- `app`: location updates run only while the map screen is visible.

## Non-goals

- Follow mode (the map keeping the position centred while walking). Every pan recomputes the
  overlay's day today, so following a walking hiker would recompute continuously; revisit after
  the "overlay on pans" polishing.
- The direction the device points (compass) or the direction of travel on the dot.
- Recording tracks, bookmarks of positions.
- GPS altitude. The altitude shown stays the DEM's (elevation-data).
- Google Play Services' fused location provider.
- A timeout or "no signal" message while waiting for a position (user decision: it arrives when
  it arrives).

## Capabilities

### New Capabilities

- `gps-location`: the position dot and its accuracy circle, fresh and old positions, the location
  button with its waiting animation and centring, the location permission and the notices for a
  refused permission and for location switched off, and location updates only while the map is
  visible.

### Modified Capabilities

- `map-view`: "Missing map tiles": the launch-without-network scenario lists the location button
  among the controls that are shown.
- `offline-regions`: "Offline button": the narrow-screen scenario includes the location button.

## Impact

- `app`: MapLibre's location component in `map/MapLibreMap.kt`; the button in `map/MapOverlay.kt`
  (`MapLabels`); the button's state in `MapViewModel`; the permission request and the notices in
  `MapScreen`; two Material Symbols icons; strings.
- `AndroidManifest.xml`: `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION`. The MapLibre library
  manifest already declares both, so they are merged into the app today; the app declares them
  itself so that its manifest states what it uses.
- No new dependency: MapLibre's location component and engine are part of
  `org.maplibre.gl:android-sdk` 13.6.1, which has no Google Play Services dependency (checked in
  its POM).
- `core`: unchanged.
- `docs/roadmap.md`: new row #9; GPS location removed from the post-1.0 candidates.
