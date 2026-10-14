# Spec Delta

## Purpose

Lets the user find a place or mountain by name, or go to typed coordinates, and move the map's
crosshair there, worldwide, with the places found before still searchable without network.

## ADDED Requirements

### Requirement: Search entry
The selected-location coordinates on the map (map-view "Selected location crosshair") SHALL show a
search icon (Material Symbol `search`) to the left of the coordinates and SHALL open the search page
when tapped (user decision, 2026-10-13: a fourth button does not fit beside the overlay toggle on
360 dp screens, and the coordinates row has no room for a button). The coordinates and the icon
together SHALL be one touch target at least 48 dp tall; the icon SHALL have the content
description `Search places`, and the coordinates stay readable as text. On screens 360 dp wide or more, in portrait and landscape, with `Coordinates` set to
`Decimal` or `Swiss grid LV95`, they SHALL end at least 8 dp left of the overlay toggle and its
status card. Search SHALL be available online and offline.

#### Scenario: Open search
- **WHEN** the map screen is shown and the user taps the coordinates
- **THEN** the search page opens

#### Scenario: Narrow screen
- **WHEN** the screen is 360 dp wide in portrait, `Coordinates` is `Decimal` and the map centre is at 46.6863° N, 7.8632° E
- **THEN** the search icon and `46.6863° N, 7.8632° E` are shown in one line that ends at least 8 dp left of the overlay toggle and its status card

### Requirement: Search page
The search page SHALL have a top bar with a back arrow with the content description `Back`, at
least 48 × 48 dp to touch, and the search field with the placeholder `Search places or
coordinates` (user decision, 2026-10-13: a page like Settings and Offline). Each time the page
opens, the field SHALL be empty and focused, with the keyboard shown. While the field is not empty,
a button with the content description `Clear`, at least 48 × 48 dp to touch, SHALL empty it. The
results SHALL be listed below the top bar. The back arrow and the system back gesture or button
SHALL return to the map with the camera, the selected time and the overlay as they were.

#### Scenario: Empty field on opening
- **WHEN** the user searched for `eiger`, went back to the map and opens the search page again
- **THEN** the field is empty and the keyboard is shown

#### Scenario: Back without choosing
- **WHEN** the map shows Interlaken at zoom 12 with `Sun hours` selected, the user opens the search page, types `eiger` and taps the back arrow
- **THEN** the map shows Interlaken at zoom 12 with `Sun hours` selected

### Requirement: Online search requests
While the device has a network connection, the search text SHALL be sent to the geocoder Photon
when it has at least 3 characters after trimming and has not changed for 300 ms (user decisions,
2026-10-13: komoot's public server, results while typing). No request SHALL be sent while the
text is coordinates ("Coordinates as search text") or while a saved answer can be reused ("Reuse
of saved answers"). The request SHALL be
`https://photon.komoot.io/api/?q=<text>&lat=<lat>&lon=<lon>&limit=10`, where `<text>` is the
normalized text (trimmed, every run of whitespace replaced by one space, lower case), encoded as
UTF-8 for the URL, and `<lat>` and `<lon>` are the crosshair's latitude and longitude rounded half
up to one decimal, so that results near the crosshair are preferred. No language SHALL be
requested, so that names are the local ones, as on the map (user decision, 2026-10-13). Every
request SHALL carry the same User-Agent header as the map tile requests (map-view "Tile request
identification"). Only one request SHALL be in progress: a change of the text SHALL cancel the
request for the previous text, whose answer SHALL NOT be shown. A request that has not answered
within 10 s SHALL count as failed ("Search without network or geocoder"). While a request is in
progress, the page SHALL show the notice `Searching…`; the notice SHALL disappear when the answer
arrives, when the request fails or when the text changes (user decision, 2026-10-14: Photon took
3–10 s per request when measured).

#### Scenario: Request
- **WHEN** the device is online, the crosshair is at 46.6863° N, 7.8632° E, no saved answer exists, and the user types `Eiger ` and pauses
- **THEN** 300 ms (±50 ms) later the app requests `https://photon.komoot.io/api/?q=eiger&lat=46.7&lon=7.9&limit=10` with the User-Agent `Sunshine/<versionName> (Android; com.sunshine.app)`

#### Scenario: Too short
- **WHEN** the device is online and the user types `ei`
- **THEN** no request is sent, and the saved places matching `ei` are listed

#### Scenario: Typing on
- **WHEN** the request for `eig` is in progress and the user types `e` to make `eige`
- **THEN** the request for `eig` is cancelled, its answer is not shown, and `eige` is requested 300 ms after typing pauses

#### Scenario: Waiting for the answer
- **WHEN** the device is online, `Eiger` is saved, and the request for `eiger` is in progress
- **THEN** `Eiger` is listed and the page shows `Searching…`; when Photon's answer arrives, the notice disappears and the answer's entries are listed

### Requirement: Result entries
The list SHALL show at most 10 entries, every kind of place included (peaks, huts, lakes,
settlements, stations, streets, addresses and others; user decision, 2026-10-13). Each entry SHALL
show:
- its name as the first line; for a result without a name, its street and house number (e.g.
  `Höheweg 37`). A result with neither, or with a latitude beyond ±85.0511° (the map's limit),
  SHALL NOT be shown;
- as the second line its type label, then ` · ` and its place, region and country code separated
  by `, `, each left out when the result does not have it (user decision, 2026-10-13), e.g.
  `peak · Grindelwald, Bern/Berne, CH`. The place is the result's city, or its county when it has
  no city; the region is its state; the country code is its two-letter ISO code;
- its distance from the crosshair, computed on a sphere of radius 6371 km, rounded half up: when
  it rounds to less than 1000 m, in metres to 10 m (`170 m`); else when it rounds to less than
  10.0 km, in kilometres with one decimal (`1.5 km`, and `1.0 km` for 996 m); else in whole
  kilometres (`16 km`, and `10 km` for 9.96 km), with a dot as decimal separator regardless of
  the device locale.

The type label SHALL be the result's OpenStreetMap tag value with `_` replaced by spaces (e.g.
`peak`, `alpine hut`, `station`). When the value is `yes`, the label SHALL be the tag key instead
(e.g. `mountain pass`). For a street (key `highway`, and Photon's type `street`) the label SHALL be
`street`; for a result without a name but with a house number, `address`.

#### Scenario: Peak entry
- **WHEN** the crosshair is at 46.6863° N, 7.8632° E and Photon returns the Eiger (natural=peak, at 46.57763° N, 8.00547° E, city Grindelwald, state Bern/Berne, country code CH)
- **THEN** its entry reads `Eiger`, `peak · Grindelwald, Bern/Berne, CH` and `16 km`

#### Scenario: Labels
- **WHEN** Photon returns results tagged `tourism=alpine_hut`, `mountain_pass=yes`, `railway=station` and `highway=residential` (type street)
- **THEN** their type labels are `alpine hut`, `mountain pass`, `station` and `street`

#### Scenario: Short distances
- **WHEN** the crosshair is at 46.6863° N, 7.8632° E and a result lies at 46.68377° N, 7.84393° E (1497 m away)
- **THEN** its distance reads `1.5 km`

#### Scenario: Missing parts
- **WHEN** a result has a name, the tag `natural=peak` and the country code `NP`, but no city, county or state
- **THEN** its second line reads `peak · NP`

### Requirement: Merged duplicates
Results SHALL be merged when they have the same name, ignoring case and accents, and lie within
250 m of each other (user decision, 2026-10-13). Going through the results in their order, a result
SHALL be left out when an entry already listed has the same name and lies within 250 m of it; the
listed entry keeps its own type label, position and distance.

#### Scenario: One place, five results
- **WHEN** Photon returns `Schynige Platte` five times, first as a station at 46.65226° N, 7.91117° E and then as a locality, another locality, a hotel and a food court 25 m, 169 m, 175 m and 194 m from it
- **THEN** the list shows one `Schynige Platte` entry, labelled `station`

#### Scenario: Two places of the same name
- **WHEN** Photon returns `Eigergletscher` as a station at 46.57480° N, 7.97486° E and as a glacier at 46.56846° N, 7.98820° E, 1240 m apart
- **THEN** the list shows both entries

### Requirement: Saved places
Every result Photon returns SHALL be saved in the app's persistent storage, as a saved place with
everything its entry and its choice need (name, label, place line, position, area). A place that
Photon returns again SHALL replace its saved copy, so that each OpenStreetMap object is saved once.
Every answer SHALL be saved too, with its normalized text, its rounded crosshair, the time it
arrived and its results in their order. Saved places and saved answers SHALL be kept, across app
restarts and updates, until the user clears them with `Clear saved places` ("Clear saved places").
Coordinates ("Coordinates as search text") SHALL NOT be saved.

#### Scenario: Saved across restarts
- **WHEN** the user searched `bachalpsee` online, closes the app, launches it again without network and types `bachalp` on the search page
- **THEN** `Bachalpsee` is listed

### Requirement: Saved matches while typing
After every change of the text that is not coordinates, the list SHALL at once show the saved
places whose name matches it: every word of the text begins a word of the name, ignoring case and
accents, where words are separated by anything other than letters and digits. They SHALL be sorted
by distance from the crosshair, nearest first, merged ("Merged duplicates"), at most 10. When the
online answer for the current text arrives, or its saved answer is reused, the list SHALL show that
answer's entries instead: merged going through them in Photon's order ("Merged duplicates"), at
most 10, then sorted by distance from the crosshair, nearest first (user decisions, 2026-10-13:
Photon's answer replaces the saved matches; 2026-10-14: nearest first). When that answer has no
entries, the page SHALL show `No places found`. When the field is empty, the list SHALL be empty.

#### Scenario: Words in any part of the name
- **WHEN** `Grosser Mythen` and `Restaurant Grosser Mythen` are saved and the user types `gross myth`
- **THEN** both are listed at once, before any request

#### Scenario: Accents ignored
- **WHEN** `Blüemlisalphütte` is saved and the user types `bluemlisalp`
- **THEN** `Blüemlisalphütte` is listed

#### Scenario: Answer replaces the saved matches
- **WHEN** the device is online, `Eiger` and `Eigerstrasse` are saved, and the user types `eiger`
- **THEN** both saved places are listed at once, and when Photon's answer arrives the list shows Photon's entries instead, nearest first

#### Scenario: Nearest first
- **WHEN** the crosshair is in Bern and Photon answers `eiger` with the peak `Eiger` (59 km) first and `Eigerstrasse` (1.3 km) last
- **THEN** `Eigerstrasse` is listed first and the peak `Eiger` last

### Requirement: Reuse of saved answers
When a saved answer exists for the normalized text and the rounded crosshair ("Online search
requests") that arrived less than `Reuse searches for` ago (settings "Settings page": `1 hour`,
`1 day`, `7 days`, `30 days` or `365 days`; `30 days` by default; user decision, 2026-10-13), its
entries SHALL be shown 300 ms after typing pauses, as for an online answer, without a request. An
older saved answer SHALL be replaced by a new request while the device is online.

#### Scenario: Repeated search
- **WHEN** `Reuse searches for` is `30 days`, the user searched `eiger` with the crosshair in Interlaken 3 days ago, and searches `Eiger` there again online
- **THEN** no request is sent, and the entries of the saved answer are listed

#### Scenario: Old answer
- **WHEN** `Reuse searches for` is `30 days` and the saved answer for `eiger` in Interlaken arrived 31 days ago
- **THEN** searching `eiger` there online sends a request, and its answer replaces the saved one

#### Scenario: Elsewhere
- **WHEN** the user searched `rothorn` with the crosshair at 46.7° N, 7.9° E (rounded) yesterday and searches `rothorn` with the crosshair at 46.8° N, 8.0° E online
- **THEN** a request is sent

### Requirement: Search without network or geocoder
Without a network connection, no request SHALL be sent; the list SHALL show the saved matches
("Saved matches while typing") of saved places of any age, and the page SHALL show the notice
`Offline · showing saved places`. When a request fails (no answer within 10 s, an HTTP status
other than 200, or an answer that cannot be read), the saved matches SHALL stay listed and the page
SHALL show `Search unavailable · showing saved places` until the text changes. When there is no
saved match, the page SHALL show `No places found` below the notice. The app SHALL NOT retry a
failed request by itself. When the network connection returns while the page is shown, the
notice SHALL disappear and the current text SHALL be handled as if it had just been typed.

#### Scenario: Offline
- **WHEN** the device is offline, `Eiger` is saved from a search a year ago, and the user types `eig`
- **THEN** `Eiger` is listed and the page shows `Offline · showing saved places`, and no request is sent

#### Scenario: Geocoder fails
- **WHEN** the device is online and Photon answers the request for `eiger` with HTTP 503
- **THEN** the saved matches for `eiger` stay listed and the page shows `Search unavailable · showing saved places`

#### Scenario: Nothing saved offline
- **WHEN** the device is offline, nothing is saved, and the user types `eiger`
- **THEN** the page shows `Offline · showing saved places` and `No places found`

### Requirement: Coordinates as search text
Text in one of the coordinate formats of the app (map-view "Selected location crosshair") SHALL be
recognised as coordinates, offline and online, without a request (user decision, 2026-10-13):
- **Decimal degrees:** two numbers with a dot as decimal separator, separated by a comma, spaces or
  both, each optionally followed by `°`. Either both are followed by a hemisphere letter (`N` or
  `S` for the latitude, `E` or `W` for the longitude, in either order, upper or lower case), or
  neither is; then the latitude comes first and south and west are negative.
- **Degrees, minutes, seconds:** two values of whole degrees with `°`, whole minutes with `′` or
  `'`, and seconds with `″` or `"` and an optional decimal part, each followed by a hemisphere
  letter as above; spaces between the parts are allowed.
- **Swiss grid LV95:** two numbers of 7 digits before an optional decimal part, with digit groups
  optionally separated by `'` or `’`, separated by a comma, spaces or both: the one starting with
  `2` is the easting, the one starting with `1` the northing, in either order. They SHALL be
  converted to WGS84 with swisstopo's approximate formulas for LV95 → WGS84.

Coordinates SHALL be accepted only with latitudes from −85.0511° to 85.0511° (the map's limit),
longitudes from −180° to 180°, minutes and seconds below 60, and, for LV95, a converted position
inside LV95's area of use (45.81° to 47.81° N, 5.95° to 10.50° E). Other text, including text in
these formats with values out of range, SHALL be searched as names. Recognised coordinates SHALL
be the list's only entry: the typed values in the map's format for their kind (decimal degrees to
4 decimals, seconds to one decimal, LV95 in whole metres), not converted back from the position,
with the second line `coordinates` and its distance from the crosshair.

#### Scenario: Decimal degrees
- **WHEN** the user types `46.6863, 7.8632`
- **THEN** the only entry reads `46.6863° N, 7.8632° E` with `coordinates`, and no request is sent

#### Scenario: Negative values
- **WHEN** the user types `-33.8688 -70.6483`
- **THEN** the only entry reads `33.8688° S, 70.6483° W`

#### Scenario: Letters decide the order
- **WHEN** the user types `7.8632 E 46.6863 N`
- **THEN** the only entry reads `46.6863° N, 7.8632° E`

#### Scenario: Degrees, minutes, seconds
- **WHEN** the user types `46°41'10.7" N 7°51'47.5" E`
- **THEN** the only entry reads `46°41′10.7″ N, 7°51′47.5″ E`, at 46.68631° N, 7.86319° E (±0.00001°)

#### Scenario: LV95 reference point
- **WHEN** the user types `2'700'000, 1'100'000` (swisstopo's worked example, whose reference is 46°02′38.87″ N, 8°43′49.79″ E)
- **THEN** the only entry reads `2'700'000, 1'100'000`, at 46.04413° N, 8.73050° E (±0.00002°)

#### Scenario: LV95 in Interlaken
- **WHEN** the user types `1170652 2632479`
- **THEN** the only entry reads `2'632'479, 1'170'652`, at 46.6863° N, 7.8632° E (±0.00002°)

#### Scenario: Out of range
- **WHEN** the device is online and the user types `95.0, 7.0`
- **THEN** no coordinates entry is shown, and the text is searched as a name

#### Scenario: Offline coordinates
- **WHEN** the device is offline and the user types `46.6863, 7.8632`
- **THEN** the only entry reads `46.6863° N, 7.8632° E`, and the offline notice is shown

### Requirement: Choosing a result
Tapping an entry SHALL return to the map with its position as the map centre (±0.0001°), so that
the crosshair and the panel describe it. The map SHALL be shown already there, without an
animation (user decision, 2026-10-13). The zoom SHALL be (user decision, 2026-10-13):
- for an entry with an area (Photon's extent) that does not fit into the map at zoom 13 when
  centred on the entry's position: the largest zoom at which it fits, at least 5;
- for every other entry, coordinates included: the current zoom, but at least 13 and at most 17.

The selected time and the overlay SHALL stay as they were. The search text SHALL NOT be kept.

#### Scenario: Peak from far out
- **WHEN** the map is at zoom 10 and the user chooses `Eiger` (46.57763° N, 8.00547° E)
- **THEN** the map centre is 46.57763° N, 8.00547° E (±0.0001°) and the zoom is 13 (±0.01)

#### Scenario: Closer zoom kept
- **WHEN** the map is at zoom 15 and the user chooses `Eiger`
- **THEN** the zoom stays 15 (±0.01)

#### Scenario: Large area
- **WHEN** the map is 400 × 800 dp at zoom 12 and the user chooses an entry at 46.0° N, 8.0° E whose extent is 45.5° to 46.5° N and 7.5° to 8.5° E
- **THEN** the map centre is 46.0° N, 8.0° E (±0.0001°) and the zoom is 8.14 (±0.01)

#### Scenario: Coordinates
- **WHEN** the map is at zoom 11 and the user chooses the entry of `46.6863, 7.8632`
- **THEN** the map centre is 46.6863° N, 7.8632° E (±0.0001°) and the zoom is 13 (±0.01)

### Requirement: Clear saved places
The button `Clear saved places` on the Settings page (settings "Settings page") SHALL remove every
saved place and every saved answer, without asking for confirmation, and the page SHALL then show
`Saved places cleared` (user decision, 2026-10-13). Map and elevation tiles, regions and settings
SHALL be kept.

#### Scenario: Cleared
- **WHEN** `Eiger` is saved, the user taps `Clear saved places`, and then types `eiger` on the search page without network
- **THEN** the Settings page showed `Saved places cleared`, and the search page shows `No places found`
