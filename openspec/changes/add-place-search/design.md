# Design

## Context

See proposal.md for the motivation and specs/ for the behaviour. Relevant current state:

- **Map screen.** The coordinates label is a plain `Label` in `MapLabels` (`map/MapOverlay.kt`),
  stacked below the row of the Settings, Offline and location buttons. At 360 dp the overlay
  toggle column (168 dp wide, toggle and status card) starts at 184 dp, leaving 168 dp left of it
  including the 8 dp gap (design D9 of add-settings); the decimal label is ~130 dp (estimate of
  design D8 of add-sun-exposure-heatmap). The degrees-minutes-seconds label is wider and unmeasured.
- **Screens.** `MainActivity` switches `Screen.MAP / SETTINGS / CUSTOM_RESOLUTION / OFFLINE`
  without a navigation library and passes the visible `MapArea` to the Offline page. `MapScreen`
  creates a new `MapView` each time it is shown, starting at the activity-scoped `MapViewModel`'s
  `camera` (`initialCamera`). Setting that camera before showing the map therefore places the map
  without any animation code.
- **Network.** `SunshineApp` builds MapLibre's `OkHttpClient` with `UserAgentInterceptor`;
  `NetworkMonitor.isOnline` is a `Flow<Boolean>` of validated internet access. No JSON library is
  in the app.
- **Storage.** `OfflineDatabase` (Room, `offline.db`, version 2) holds the DEM tile index; JVM
  tests run Room on the bundled SQLite driver (`TestDatabase`). Settings live in a Preferences
  DataStore, decoded by `decode` with a default per unreadable or disallowed value.
- **Core.** `Lv95.kt` converts WGS84 → LV95 only; `EARTH_RADIUS` is 6 371 000 m; `MAP_TILE_DP`
  is 512 (the world is 512 · 2^zoom dp wide).

Photon, checked on 2026-10-13 from this project's cloud container (8 test queries):

| Fact | Value |
|------|-------|
| Endpoint | `https://photon.komoot.io/api/?q=…&lat=…&lon=…&limit=…` |
| Terms | "please be fair - extensive usage will be throttled"; no availability guarantee; no published limits |
| Languages | `default`, `de`, `en`, `fr`; others answer HTTP 400 |
| Caching headers | `Cache-Control: max-age=3600`; no `ETag`, no `Last-Modified` |
| Latency | 1.2–1.7 s per request (through the container's proxy; not measured from a phone) |
| Properties | `name`, `osm_type`, `osm_id`, `osm_key`, `osm_value`, `type`, `street`, `housenumber`, `city`, `county`, `district`, `state`, `country`, `countrycode`, `postcode`, `extent` |
| `extent` order | west, north, east, south (Bachalpsee: `[8.018276, 46.671057, 8.026351, 46.6668713]`) |
| Bias | `eig` near Interlaken → Eiger first; without bias → two Finnish regions first |
| Coverage | found Bachalpsee, Blüemlisalphütte and Grosser Mythen, which GeoNames lacks |
| Addresses | no `name`; `street` + `housenumber`, `osm_key=place`, `osm_value=house` |

## Goals / Non-Goals

**Goals:**
- Every rule with numbers (coordinate parsing, LV95 → WGS84, distance, merging, matching,
  zoom) is a pure function in `core`, tested on the JVM against concrete values.
- The search timing (pause, minimum length, cancellation, reuse, offline, failure) lives in one
  view model, tested with virtual time, a fake fetch function and an in-memory database.

**Non-Goals:**
- No geocoder abstraction for other providers: there is one source.
- No full-text index (FTS); no paging of results.

## Decisions

### D1. Photon's public server, results while typing (user decisions, 2026-10-13)

Search is worldwide and online first; offline, it works on what was found before (saved places).
Photon is komoot's public geocoder on OpenStreetMap data, with typo tolerance, results while
typing and a location bias.

Alternatives considered (explore session, 2026-10-13):
- *Nominatim (OSMF):* forbids autocomplete, allows 1 request/s for the whole app, and requires
  that an app can be switched off at OSMF's request without a software update, which needs a
  server the app does not have.
- *swisstopo SearchServer:* excellent names, but Switzerland only.
- *Overpass for region names:* its policy names apps relying on the public instances as a backend
  as problematic.
- *A bundled gazetteer:* measured on GeoNames' worldwide dump: 7.4 M rows, 92 MB compressed,
  300–551 MB as an SQLite index; mountains only 1.09 M rows, 15 MB, 47–85 MB. OSM extracts are
  estimated larger (~7.9 M named features). Kept for a later change as an optional download.
- *Paid geocoders* need an API key in the APK.

### D2. The request: normalized text, crosshair rounded to 0.1°

`q` is the normalized text (trimmed, whitespace runs collapsed, lower case); `lat`/`lon` are the
crosshair rounded half up to one decimal (`floor(x · 10 + 0.5) / 10`), kept as integers × 10;
`limit=10`; no `lang`. The request is then fully determined by (text, lat × 10, lon × 10), which
is also the key of a saved answer (D5): no fuzzy cache matching. A 0.1° cell is ~11 × 8 km in the
Alps, within which Photon's default bias radius (zoom 12) ranks results the same.

Alternatives considered: the exact crosshair (every pan misses the saved answer); no bias (the
Finnish regions above); passing the map's zoom as Photon's `zoom` (a third key part for little gain).

### D3. Local names; the country code in the second line (user decisions, 2026-10-13)

Without `lang`, names are the OSM `name` tags, which OpenTopoMap's labels show too. Photon's
`country` is then e.g. `Schweiz/Suisse/Svizzera/Svizra`, so the second line uses `countrycode`.
Alternatives considered: `lang=en`, matching the app's English UI (`Switzerland`, `Lake Geneva`);
the device language (`de`, `en` or `fr`, else English); the full country name; place only.

### D4. When to request: 3 characters, 300 ms, latest only (user decision, 2026-10-13)

The view model combines the text with `NetworkMonitor.isOnline` and handles each combination
with `collectLatest`, so a newer text (or a change of connectivity) cancels the work of the older
one, including its HTTP call, and the connectivity return re-runs the current text. Saved
matches are shown at once (D7); the request waits for the 300 ms pause. Alternatives considered:
from 2 characters (more requests); a 500 ms pause (results feel slower on top of 1.2–1.7 s).

### D5. Saved places and saved answers in a separate database (user decision, 2026-10-13)

A new Room database `places.db` (`search/PlacesDatabase.kt`), version 1, with:
- `place`: key `<osm_type><osm_id>` (e.g. `N31664302`), name, street, house number, the
  normalized match text (name, else street and number; D7), label, place, region, country code,
  latitude, longitude, the extent (four nullable columns), received time. Upserted by key, so a
  place returned again replaces its copy.
- `answer`: primary key (text, lat × 10, lon × 10), received time, and the place keys in
  Photon's order as one comma-separated string (OSM keys contain no comma); a missing key is
  skipped when shown.

`Clear saved places` deletes both tables in one transaction.

Alternatives considered: two tables in `offline.db` with a migration 2 → 3 (one file, but a
migration of the tile index for unrelated data); OkHttp's HTTP cache (keyed by URL and bound to
Photon's 1-hour `max-age`, and it cannot be searched by name offline).

### D6. `Reuse searches for` in Storage, stored in hours (user decisions, 2026-10-13)

`Settings.reuseSearchesHours` with the allowed values 1, 24, 168, 720 (default) and 8760, under
the DataStore key `reuse_searches_hours`, decoded like `Browsed tiles limit`. The view model
reads the current settings at each search, so a change applies at once. A saved answer is
reused while `now − received < reuse`; the clock is injected for tests.

Alternatives considered: Photon's `max-age` of 1 hour (saves few requests); until cleared (new
places never appear); a new `Advanced` section (a section with one entry; user chose Storage).

### D7. Matching saved places without a full-text index

`core/Places.kt`: `normalizeName` lowercases (`Locale.ROOT`) and strips accents (NFD, then
removing combining marks); words are runs of letters and digits. A saved place matches when
every word of the text begins a word of its normalized name. The DAO selects candidates with
`LIKE '%' || :longestWord || '%'` on the normalized column; Kotlin then applies the word rule,
sorts by distance from the crosshair, merges (D9) and keeps 10. At ~200 bytes per place, 20 000
places are ~4 MB.

Alternatives considered: SQLite FTS4 through Room (`@Fts4`, the only FTS Room supports) with
`unicode61 remove_diacritics`: prefix queries by index, but a second table to keep in sync and a
tokenizer configuration to verify on the bundled driver, for a table of thousands of rows.

### D8. Saved matches first, replaced by the answer (user decision, 2026-10-13)

The list shows the saved matches after every text change; when the answer for the current text
arrives (online or reused), its entries replace them. Alternative considered: saved matches on
top with Photon's other results appended below.

### D9. Merging: same name within 250 m (user decision, 2026-10-13)

`core/Places.kt`: `merge(entries)` walks the entries in order and drops one when a kept entry has
the same normalized name and lies within 250 m (great-circle distance on `EARTH_RADIUS`).
Measured on Photon's answers: the five `Schynige Platte` results lie 25–194 m from the first; the
`Eigergletscher` station and glacier 1240 m apart. Alternatives considered: 100 m (Schynige
Platte → 3 entries); 1 km (swallows distinct nearby features of one name).

### D10. Entries: labels, second line and distance

Parsing (`search/PhotonAnswer.kt`, `org.json`, D15) turns each feature into a place:
- title: `name`, else `street housenumber`; skipped with neither, or beyond ±85.0511° latitude;
- label: `osm_value` with `_` → space; `yes` → `osm_key` likewise; `highway` with `type=street`
  → `street`; no name but a house number → `address`. This implements the user's "type labels"
  (2026-10-13) without a translation table, which would need curating for thousands of tag values;
- second line: label, ` · `, then `city` (else `county`), `state`, `countrycode`, each left out
  when missing.

The distance (`core/Places.kt`, `formatDistance`) is formatted with `Locale.ROOT`: `170 m`,
`1.5 km`, `16 km`.

### D11. Coordinates as search text in `core` (user decision, 2026-10-13)

`core/Coordinates.kt`: `parseCoordinates(text): Coordinates?` with the point and the typed
kind (decimal, DMS, LV95) and, for LV95, the typed easting and northing, which the entry shows
instead of a round trip (LV95 → WGS84 → LV95 moves Interlaken's northing by −0.57 m, which would
display `1'170'651` for a typed `1'170'652`). `Lv95.kt` gets `fromLv95(Lv95): GeoPoint` by
swisstopo's approximate formulas for LV95 → WGS84:

```
y = (E − 2 600 000) / 1 000 000          x = (N − 1 200 000) / 1 000 000
λ' = 2.6779094 + 4.728982 y + 0.791484 y x + 0.1306 y x² − 0.0436 y³
φ' = 16.9023892 + 3.238272 x − 0.270978 y² − 0.002528 x² − 0.0447 y² x − 0.0140 x³
λ = λ' · 100 / 36,  φ = φ' · 100 / 36   (degrees)
```

Verified on 2026-10-13: swisstopo's worked example 2 700 000 / 1 100 000 gives 46.044127° N,
8.730499° E, 0.42 m and 0.16 m from the reference 46°02′38.87″ N, 8°43′49.79″ E; Interlaken
2 632 479 / 1 170 652 gives 46.686295° N, 7.863194° E, within 0.6 m of 46.6863° N, 7.8632° E.
Display reuses `map/CoordinateFormat.kt`, with the LV95 formatting of a given `Lv95` exposed.

Alternative considered: leaving coordinates out (a parser and its tests for three formats);
the user chose to include them.

### D12. The coordinates open search (user decision, 2026-10-13)

The coordinates `Label` becomes a clickable `Surface` (one touch target, at least 48 dp tall)
with a 20 dp `search` icon (Material Symbols, Apache 2.0, already credited) before the text.
Estimated width at 360 dp with `Decimal`: ~130 + 20 + 8 = ~158 of 168 dp; the device check of
task 7.3 measures it in all three formats.

Alternatives considered: a search button top-right with the toggle column moved down 56 dp; a
third row on the left below the coordinates; a button in the coordinates row (does not fit:
~130 + 56 = ~186 dp of 168).

### D13. Search is a page (user decision, 2026-10-13)

`Screen.SEARCH` in `MainActivity`, with `BackHandler`, `PageTopBar`'s pattern (back arrow, then
an `OutlinedTextField` in the bar's place of the title), and the visible `MapArea` passed like the
Offline page's, for the crosshair and the map size. `search/SearchScreen.kt` and
`search/SearchViewModel.kt` (factory over `SunshineApp`'s graph). The field requests focus when
the page is composed. Alternative considered: a field and list dropping down over the map (more
layout code around the toggle, the panel and the insets).

### D14. Choosing: the camera is set before the map is shown (user decisions, 2026-10-13)

`core/Places.kt`: `fitZoom(center, extent, widthDp, heightDp)` is the largest zoom at which the
extent fits when centred on `center` in Web Mercator with `MAP_TILE_DP`:
`min(log2(w / 2 / (512 · dx)), log2(h / 2 / (512 · dy)))` with dx, dy the largest normalized
distances from the centre to the extent's edges. `chosenZoom` applies the spec's rule: with an
extent whose fit zoom is below 13, the fit zoom (at least 5); otherwise the current zoom
clamped to 13–17. Example: centre 46.0° N, 8.0° E, extent 45.5–46.5° N, 7.5–8.5° E, 400 × 800 dp →
8.14.

`MainActivity` obtains the activity-scoped `MapViewModel` (its factory becomes `internal`) and,
on a choice, calls `MapViewModel.onCameraChosen(camera)`, then switches to the map. It sets the
camera like `onCameraMoved` and cancels the pending start centring of `Start at: My location`,
as a gesture does (map-view "Default viewport": the map does not move once the user has moved it).

Alternatives considered: an animated flight after showing the map (long jumps over unloaded
tiles); always zoom 13; keeping the zoom (the overlay needs zoom 11 or more).

### D15. JSON with Android's `org.json` (user decision, 2026-10-13)

Parsing uses `org.json.JSONObject` at runtime, which Android provides, so the app gets no new
dependency. JVM unit tests add `org.json:json` (latest stable at implementation time, in
`libs.versions.toml`) as `testImplementation`, since the `android.jar` copy throws in tests.
Alternative considered: kotlinx.serialization (a Gradle plugin and a runtime library).

### D16. The HTTP client for Photon

`search/PhotonClient.kt`: its own `OkHttpClient` with `UserAgentInterceptor` and a 10 s
`callTimeout`, without retries; a call is enqueued and cancelled when its coroutine is
cancelled (D4). It returns the body of an HTTP 200 answer, or fails. MapLibre's client keeps its
tile-tuned dispatcher. The view model receives the fetch as a function, so tests pass a fake.

### Performance budget

Measured on the device in task 7.3 (debug log timings), on a mid-range phone:
- saved matches after a text change, with 20 000 saved places: ≤ 50 ms to the list (query,
  word rule, sort, merge), off the main thread;
- an answer of 10 results: parse, save and list ≤ 50 ms, off the main thread;
- coordinates: parse and format ≤ 1 ms;
- choosing: computing the camera ≤ 1 ms; the map's first frame is at the chosen place.

Network time is not budgeted (Photon took 1.2–1.7 s from the container).

## Risks / Trade-offs

- [komoot throttles, changes terms or switches the public server off] → requests fail and search
  shows saved places with `Search unavailable`; another server needs an app update. The pause,
  the 3-character minimum and the reuse of saved answers keep requests low; the User-Agent names
  the app.
- [The DMS label may already reach under the toggle column at 360 dp today] → the spec's width
  rule covers `Decimal` and `Swiss grid LV95`; task 7.3 measures all three formats and reports a
  DMS overlap rather than widening this change.
- [The decimal label's margin is thin, ~10 dp by estimate] → task 7.3 measures it; a smaller
  icon (18 dp) is the fallback.
- [Privacy: the text and the crosshair rounded to 0.1° go to komoot; saved places reveal past
  searches on the device] → `Clear saved places`.
- [OpenStreetMap data (ODbL) is stored on the device] → a cache for the user's own use, not
  redistributed; attribution in About.
- [After choosing typed LV95 coordinates, the map's LV95 label may differ by 1 m from the typed
  value (round trip)] → within the 1 m precision the map-view spec states for LV95.
- [Saved places lose freshness: a renamed place stays as it was until Photon returns it again]
  → it is replaced whenever it is returned again; offline, old data beats none.

## Migration Plan

`places.db` is created on first use; nothing existing is migrated. The new setting has a default
for installs without it. Rolling back to an older version leaves `places.db` and the unknown
DataStore key unused.
