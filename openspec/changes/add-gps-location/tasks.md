# Tasks

## 1. Device spike: MapLibre's location component

- [ ] 1.1 In a debug build, declare `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION` in `AndroidManifest.xml` with a comment (design D7), and activate the location component in `MapLibreMap` once the style is loaded and access is allowed (granted by hand in the system settings for the spike), with `LocationEngineDefault`, a 1000 ms `LocationEngineRequest` at high accuracy, `RenderMode.NORMAL`, `CameraMode.NONE`, `enableStaleState(true)`, `staleStateTimeout(30_000)`, and a stale listener that logs `stale=<true|false>` (design D1, D2). Verify on the device, by logcat and by eye:
  - at launch with a last known position, the dot is grey and `stale=true` is logged before any fresh position;
  - outdoors, a coloured dot with an accuracy circle appears and `stale=false` is logged;
  - in airplane mode with location on, a coloured dot appears from GPS alone (cold start may take minutes);
  - covering the GPS antenna (or going indoors) for > 30 s turns the dot grey and logs `stale=true`;
  - after 5 minutes in another app, back on the map: the dot is grey until a new position arrives (spec "Back to the map"). If it is coloured instead (risk of design D2), stop, record the raw observation in design D2 and ask the user how to proceed;
  - with `Sun & shade` on, the dot is drawn above the overlay;
  - panning and zooming never move the camera back to the dot.

  Keep the activation code for group 3. The stale state stays logged through `MapViewModel`'s `log` (`debugLog`, debug builds only, tag `Sunshine`), so that these checks run on the same debug APK as group 3 (one device session, user decision 2026-10-03).

## 2. app: the button's state machine

- [x] 2.1 Write `LocationButtonTest` first, one case per row of the table in design D3, plus: a fresh position while IDLE turns READY without any action (spec "Ready without a tap"); a stale event while WAITING stays WAITING; a tap with only approximate access in READY gives the centre action and the approximate notice. Run it and see it fail to compile. Then implement `LocationButton` in `app/.../map/LocationButton.kt` (pure Kotlin, no Android types). Verify: `./gradlew :app:testDebugUnitTest --tests "*LocationButtonTest*"` passes.
- [x] 2.2 Wire `LocationButton` into `MapViewModel`: a `StateFlow` of the button state, `onLocationTapped(access, locationOn)`, `onLocationPermissionAnswered(access, locationOn, dialogAvailable)`, `onLocationStale(stale)`, and a flow of one-off actions (design D3). Add to `MapViewModelTest`: a tap without a fresh position, then `onLocationStale(false)` → state READY and no centre action; a second tap → one centre action. Verify: `./gradlew :app:testDebugUnitTest --tests "*MapViewModel*"` passes.

## 3. app: button, permission, notices, centring

- [ ] 3.1 Add the Material Symbols icons `location_searching` and `my_location` as vector drawables in the style of `ic_info.xml`, and the strings `My location`, `Location access is off for Sunshine.`, `Location is switched off.`, `Approximate location only. Allow precise location for your exact position.` and `Settings`. Add the button to the top-left row in `MapLabels`, right of the Offline button, with the icon per state and the alpha pulse while waiting (design D6). Verify: `./gradlew assembleDebug` succeeds; on the device the button is shown right of Offline with the content description `My location` (TalkBack reads it).
- [ ] 3.2 In `MapScreen`, read the access level, the dialog availability and whether location is on at each tap, ask with `RequestMultiplePermissions` for fine and coarse location and replay the tap after the answer; pass `locationAllowed` to `MapLibreMap`; show the notices as snackbars (`SnackbarDuration.Long`) with `Settings` opening the app's settings or the location settings (design D4, D5). Verify on the device, after `adb shell pm clear com.sunshine.app`:
  - launch: no dialog, no dot (spec "No question at launch");
  - first tap: Android's dialog for location while in use (spec "First tap");
  - refuse twice, tap again: the notice `Location access is off for Sunshine.` with `Settings`, which opens the app's settings page (spec "Refused for good", "Open the settings");
  - allow approximate only, tap: the approximate notice with `Settings`;
  - allow precise, location switched off in quick settings, tap: `Location is switched off.` with `Settings` opening the location settings, the button stays idle;
  - each notice disappears after about 10 s or when swiped away.
- [ ] 3.3 Connect the stale listener to `onLocationStale` and the centre action to `animateCamera(CameraUpdateFactory.newLatLng(lastKnownLocation))` in `MapLibreMap` (design D1, D4). Verify on the device:
  - with a coloured dot, map at zoom 12 elsewhere: a tap moves the centre to the dot, the zoom stays 12, and the coordinates match the dot's position to 4 decimals (spec "Tap when ready");
  - with no dot, a tap starts the pulse, the map does not move; when the dot turns coloured the pulse stops and the button shows `my_location`; a second tap centres (spec "Wait for a position");
  - a tap while pulsing stops the pulse (spec "Stop waiting");
  - while waiting, rotate the device and open and close the About page: still waiting;
  - walking 200 m after centring: the dot moves, the map does not (spec "Walking after centring").
- [x] 3.4 Run `./scripts/verify-local.sh`. Verify: ktlint, Android lint, unit tests and the debug APK all pass with zero issues.

## 4. Integration checks

- [ ] 4.1 Layout on a 360 dp wide screen (emulator `-skin 720x1520` at 320 dpi, or a device with display size set to 360 dp), portrait and landscape: the ⓘ, Offline and location buttons and the overlay toggle are fully on screen and do not overlap, and the coordinates label is not covered (spec offline-regions "Narrow screen", gps-location "Location button"). Verify by screenshot.
- [ ] 4.2 Offline and battery on the device: airplane mode, location on, app launched in a stored region: the map, the crosshair, the coordinates, the ⓘ, Offline and location buttons and the offline notice are shown (spec map-view "Launch without network"); a GPS position arrives and a tap centres on it. Then switch to another app for 1 minute and check `adb shell dumpsys location | grep -A3 com.sunshine.app` shows no active request (spec "App to the background"); back on the map, open the About page for 1 minute and check the same (spec "Location updates only while visible"). Verify: all observations as stated.

## 5. Documentation

- [x] 5.1 Update `CLAUDE.md`'s `app/` row with `map/LocationButton.kt` and the location component in `MapLibreMap`, and `docs/roadmap.md`'s row #9 to "in progress" when apply starts. Verify: `openspec validate --all --strict` passes and the row and paths match the code.
