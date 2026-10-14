# Proposal

## Why

To ask "when does the sun reach the Eiger summit tomorrow?", the hiker today has to find the
Eiger on the map by panning and zooming. Searching for places and mountains by name, or by
coordinates copied from a guidebook, brings the crosshair there directly, worldwide, as the app
is meant to grow beyond the Alps (user decision, explore session, 2026-10-13).

Roadmap: off-roadmap. The v1 changes #1–#12 are archived and place search was not among the
post-1.0 candidates; it is a new user request (2026-10-13). This change adds it to
`docs/roadmap.md` as row #13.

## What Changes

- `app`: **search entry.** The selected-location coordinates on the map get a search icon and
  open the search page when tapped (user decision: a fourth button does not fit beside the overlay
  toggle at 360 dp, and the coordinates row has no room for a button).
- `app`: **search page.** A page like Settings and Offline: a top bar with the back arrow and the
  search field, the results below. Results appear while typing.
- `app`: **places from Photon.** From 3 characters, 300 ms after typing pauses, the text is sent
  to komoot's public Photon geocoder (OpenStreetMap data, worldwide), with the crosshair, rounded
  to 0.1°, as the point results near it are preferred. Every kind of result is shown (peaks, huts,
  lakes, villages, stations, streets, addresses…) with a type label, its place, region and country
  code, and its distance from the crosshair. Names are the local ones, as on the map. Results of
  the same name within 250 m of each other are merged into one (user decisions).
- `app`: **saved places.** Every place Photon returns is saved on the device. Saved places that
  match the text are shown at once while typing; Photon's answer replaces them when it arrives,
  and while it is awaited the page shows `Searching…` (user decision, 2026-10-14). Without network, or when Photon fails, the saved places are searched instead, nearest first,
  with a notice. A repeated search (same text, same rounded crosshair) is answered from the saved
  answer without a request for as long as `Reuse searches for` allows: 1 hour, 1 day, 7 days,
  30 days (default) or 365 days (user decisions).
- `app`: **coordinates as search text.** Text in one of the three coordinate formats the app
  shows (decimal degrees, degrees-minutes-seconds, Swiss LV95) becomes the first result, offline
  as online, without a request (user decision).
- `app`: **choosing a result** returns to the map centred on it, at once, at map zoom 13 or
  closer; results covering an area larger than zoom 13 shows are fitted to the view instead
  (user decision).
- `app`: **Settings → Storage** gets `Reuse searches for` and the button `Clear saved places`.
- `app`: **About** gets the attribution `Search: Photon by komoot, © OpenStreetMap contributors`.
- `core`: parsing typed coordinates, LV95 → WGS84 (swisstopo's approximate formulas), merging
  duplicate results, and the zoom that fits an area around a point.

## Non-goals

- Names for downloaded offline regions (GeoNames country files or own name tiles), so that places
  never searched before are found offline: a later change (user decision, 2026-10-13).
- A bundled offline gazetteer: measured at ~92 MB compressed and 300–550 MB installed worldwide
  (GeoNames), or ~15 MB for mountains only (explore session, 2026-10-13).
- Nominatim, Overpass, swisstopo's SearchServer or a paid geocoder; an own Photon server; a way to
  switch the server without an app update.
- Reverse geocoding (the name of the place under the crosshair), a marker for the chosen result,
  a list of recent searches while the field is empty, favourites or bookmarks.
- Filtering results by kind, and translated names (`lang`; user decision: local names).
- Coordinate formats the app does not show: degrees and decimal minutes, LV03, UTM, MGRS,
  decimal commas.
- Mentioning search in the first-run hint; the search icon on the coordinates is the cue.

## Capabilities

### New Capabilities

- `place-search`: the search entry on the coordinates, the search page, Photon requests and their
  identification, result labels, distances and merging, saved places and saved answers with
  `Reuse searches for` and `Clear saved places`, behaviour without network and on failure,
  coordinates as search text, and the camera move on choosing a result.

### Modified Capabilities

- `settings`: "Settings page": the Storage section lists `Reuse searches for` and `Clear saved
  places`; "Stored settings": the default `30 days`.
- `map-view`: "About and attributions": the search attribution.

## Impact

- `app`: a new package `search/` (Photon client, saved places in a new Room database `places.db`,
  the page and its view model); the coordinates label in `map/MapOverlay.kt` (`MapLabels`); a
  camera request in `map/MapLibreMap.kt` and `MapViewModel`; `MainActivity` gets the search
  screen; `SunshineApp` wires the client and the database; `settings/` gets the new setting and
  button; `about/AboutEntries.kt` the attribution; a Material Symbols `search` icon; strings.
- Network: requests to `photon.komoot.io` with the app's User-Agent. komoot gives no availability
  guarantee; when Photon is unavailable, search falls back to saved places.
- Dependencies: none new at runtime (Android's `org.json`); `org.json:json` as a test-only
  dependency of `app`, as Android's copy is a stub in JVM unit tests.
- `core`: new `Coordinates.kt` (parsing) and `Places.kt` (merging, fit zoom); `Lv95.kt` gets the
  inverse transformation.
- `docs/roadmap.md`: new row #13; `CLAUDE.md` and `README.md`: the new package and core files.
