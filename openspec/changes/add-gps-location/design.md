# Design

## Context

See proposal.md for the motivation. Relevant current state:

- The map is a MapLibre `MapView` wrapped by the composable `MapLibreMap` (`map/MapLibreMap.kt`).
  It is created when `MapScreen` enters the composition and forwards the lifecycle events to the
  `MapView`. `MainActivity` swaps the screens without a navigation library, so opening the About or
  Offline page removes `MapScreen` and its `MapView`; going back creates a new one. `MapViewModel`
  belongs to the activity and survives both, and screen rotation.
- The selected location is always the map centre (map-view "Selected location crosshair").
  Every camera move, by gesture or by code, goes through `onCameraMoved` and recomputes what
  depends on the centre and the visible area, the overlay's day included.
- The top-left of the map screen holds a row with the ⓘ and Offline buttons (48 dp each, 8 dp
  apart) above the coordinates label (`MapLabels` in `map/MapOverlay.kt`).
- The app has no location code. MapLibre Android 13.6.1, already a dependency, contains a
  location component and location engines (checked in its AAR, 2026-10-02):
  - `LocationEngineDefault.getDefaultLocationEngine(context)` returns
    `MapLibreFusedLocationEngineImpl`, built on Android's `LocationManager`: it uses the GPS
    provider and starts the network provider as well. The library's POM has no Google Play
    Services dependency.
  - The library manifest declares `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION`.
  - `LocationComponentOptions` has `enableStaleState(boolean)` and `staleStateTimeout(long)`;
    `LocationComponent.addOnLocationStaleListener` reports the stale state. The stale state starts
    as stale. Every location from the engine's updates resets the timeout (`updateLatestLocationTime`),
    but the engine's last known location, shown when the component starts, does not: an old
    position is drawn stale until a fresh one arrives.

## Goals / Non-Goals

**Goals:**
- All position handling (engine, dot, accuracy circle, stale state) stays inside MapLibre's
  location component (user decision); the app adds only the button, its states, the permission
  flow and the notices.
- The button's behaviour is a small pure state machine, unit-tested on the JVM.

**Non-Goals:**
- No position in `core` and no position-dependent computation: centring is an ordinary camera
  move, and everything after it is the existing behaviour for a moved map.
- No custom drawing of the dot; MapLibre's default dot, accuracy circle and stale colours are used.

## Decisions

### D1. MapLibre's location component with its default engine (user decision)

`MapLibreMap` activates the location component once the style is loaded and location access is
allowed, with `LocationEngineDefault`, a `LocationEngineRequest` of 1000 ms interval and fastest
interval and high-accuracy priority, `RenderMode.NORMAL` (no bearing arrow) and
`CameraMode.NONE` (the component never moves the camera). The rotation and tilt gestures stay
disabled as they are today.

Alternatives considered:
- *Our own `LocationManager` listener* feeding the component through `forceLocationUpdate`, with
  the fix/no-fix state in the ViewModel. More testable and avoids the engine's last known
  position; rejected by the user in favour of less code. The last known position turned out to be
  drawn stale (Context), so the concern is covered by D2.
- *Google Play Services' fused provider*: a proprietary dependency that fails on phones without
  Google services, and gives no gain offline, where both fall back to GPS (user decision).
- *Drawing the dot ourselves* (a GeoJSON source and circle layer): more code for what the
  component already draws.

### D2. Fresh and old positions are MapLibre's stale state, 30 s (user decision)

`enableStaleState(true)` and `staleStateTimeout(30_000)`. The component draws a stale position in
its grey stale colours. The app listens with `addOnLocationStaleListener` and passes the state to
the ViewModel: not stale means a fresh position is known (ready); stale means not.

Alternatives considered:
- *Keep the last dot as it is* (no timer): simpler, but the dot can lag behind a walking user
  without showing it, against the project's "unknown is shown as unknown" constraint.
- *Our own timer on the position's age*: duplicates what the component already does.

Whether the component goes stale after the app returns from the background (onStop/onStart without
a new `MapView`) is not visible in the bytecode alone: `onStop` cancels the pending stale timeout,
and `onStart` posts it again, so a fresh dot could stay coloured for up to 30 s after a long pause.
Task 1 checks this on the device first (see Risks).

### D3. The button's states are a pure state machine in `map/LocationButton.kt`

A small class without Android dependencies holds the button state (`IDLE`, `WAITING`, `READY`) and
turns events into a new state and at most one action:

| Event | From | To | Action |
|---|---|---|---|
| tap, access not asked or Android would ask again | any | unchanged | ask for access |
| tap, access refused and no dialog any more | any | IDLE | notice "access off" |
| tap, location switched off | any | IDLE | notice "switched off" |
| tap | READY | READY | centre (+ notice "approximate" if only approximate access) |
| tap | IDLE | WAITING | (+ notice "approximate" if only approximate access) |
| tap | WAITING | IDLE | none |
| fresh position (not stale) | IDLE, WAITING | READY | none |
| stale | READY | IDLE | none |

After a permission answer, `MapScreen` replays the tap with the new access. `MapViewModel` owns the
instance, so the state survives rotation and the About and Offline pages (spec "Wait for a
position"); it exposes the state as a `StateFlow` and the actions as a `Channel`-backed flow of
one-off events, which `MapScreen` handles. The table is unit-tested in
`LocationButtonTest`.

Alternatives considered: the logic inside `MapViewModel` directly (it is already over 600 lines,
and the table is easier to test alone); a `rememberSaveable` state in Compose (lost when the
screen is swapped).

### D4. Android-facing parts live in `MapScreen`

`MapScreen` reads what the state machine needs at each tap: the access level
(`ContextCompat.checkSelfPermission` for fine and coarse), whether Android would still show the
dialog (`shouldShowRequestPermissionRationale` after a refusal, the standard way to tell "refused"
from "refused for good"), and whether location is on (`LocationManager.isLocationEnabled`, API 28,
below minSdk 29). It asks with `rememberLauncherForActivityResult(RequestMultiplePermissions)` for
`ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION`; Android 12+ then offers "precise" or
"approximate". It passes `locationAllowed` to `MapLibreMap`, which activates and enables the
component only then (the engine throws a `SecurityException` without access).

Centring: on the action, `MapLibreMap` reads `locationComponent.lastKnownLocation` and calls
`animateCamera(CameraUpdateFactory.newLatLng(...))`, which keeps the zoom. The camera then
reports through `onCameraMoved` as for a pan.

### D5. Notices are snackbars with a `Settings` action

A `SnackbarHost` in `MapScreen`, `SnackbarDuration.Long` (10 s in Material 3, the spec's 10 s),
swipe to dismiss. `Settings` opens `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` for the app or
`Settings.ACTION_LOCATION_SOURCE_SETTINGS`. The offline notice stays a label, as it describes a
lasting state; these notices answer a tap.

Alternative considered: labels below the coordinates like the offline notice. They would need
their own dismiss rule and crowd the top-left column on narrow screens.

### D6. The button: third in the top-left row, icons and animation

The button sits right of the Offline button in the same `Row` (user decision). Three 48 dp buttons
with two 8 dp gaps are 160 dp; the coordinates label below is about 180 dp for
`46.6863° N, 10.1234° E`, so the column does not get wider and the 360 dp layout of
offline-regions keeps its room. Task 4 checks it on a 360 dp screen.

Icons from Material Symbols (already credited on the About page): `location_searching` for idle
and waiting, `my_location` for ready. Waiting pulses the icon's alpha between 0.3 and 1.0 with a
1 s period (`rememberInfiniteTransition`); a pulse reads as "looking" and does not suggest
progress. This detail does not change the specs and can be revised on the device.

### D7. Permissions in the app's manifest

`ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION` are added to `AndroidManifest.xml` with a
comment. They are merged from MapLibre's manifest today; declaring them makes the app's own
manifest say what it uses. No background location permission.

### D8. Performance budget

- A tap on the location button: the state change and the camera animation start within one frame
  (16 ms) on the UI thread; nothing else runs on it.
- Position updates: at most one per second, handled by MapLibre on the main looper; the app's
  share is the stale listener, O(1).
- Centring is a camera move like a pan: the overlay's day is recomputed under the existing budgets
  of sun-shade-overlay; nothing new is computed.

## Risks / Trade-offs

- [The dot stays coloured for up to 30 s after a return from the background (D2)] → Task 1 checks
  it first. If it happens, implementation stops and the user decides (for example restarting the
  component on `ON_START`, or accepting it as a known limitation).
- [The engine starts the network provider as well] → Online it gives a quick first position with a
  large accuracy circle, which is honest. Offline it gives nothing and GPS alone answers.
- [GPS at 1 Hz drains the battery while the map is open] → Updates run only while the map screen
  is visible (spec "Location updates only while visible"); MapLibre's `MapView` lifecycle stops
  the engine in `onStop`. Checked on the device in task 5.
- [Approximate access offline gives no position at all] (the network provider needs network and
  GPS needs precise access) → The button waits without a limit, as decided; the approximate notice
  on each tap points to the setting that fixes it.
- [A cold GPS start without network can take minutes] → Accepted (user decision): the waiting
  animation shows it is looking; the old dot, if any, is grey meanwhile.
- [MapLibre's default dot colours] → Accepted; revisit only if the dot is hard to see on the
  heatmap's colours.

## Open Questions

- Whether the default stale colours stand out enough from the greyscale map under `Sun hours`.
  It can be tuned with `foregroundStaleTintColor` without changing the specs.
