# Proposal

## Why

Every terrain feature (horizon, first/last sunshine, overlays, heatmap) needs the height of the
ground at a point, and the app has no elevation data yet. The first implementation failed here:
it treated missing elevation as flat ground at 0 m and reported "visible" when it did not know.
The spike `investigations/dem-source-evaluation.md` measured the candidate sources. Mapterhorn is
about ten times more accurate than the AWS Terrain Tiles and Copernicus in the Alps. Those sources
put summits 30–290 m too low, which shifts first/last sunshine by up to 39 minutes.

Roadmap: implements entry #3, `add-elevation-data`, of `docs/roadmap.md`.

## What Changes

- `core`: Web-Mercator tile math, Terrarium height decoding and bilinear interpolation, which give
  the ground elevation at a point from 512 px height tiles, including points whose interpolation
  needs neighbouring tiles. Pure Kotlin, tested against swissALTI3D reference values.
- `app`: DEM tiles from Mapterhorn (zoom 12, ~13 m pixels) are fetched on demand, decoded, and
  kept in a disk cache so that locations already viewed also work offline while the cache holds
  them. Requests identify the app with the same User-Agent as map tiles.
- Missing data is explicit: when a needed tile cannot be obtained (offline and not cached, server
  error, no coverage), the elevation is *unknown*. It is never replaced by a default height.
- `app`: the sun information panel shows the altitude of the selected location
  (`Altitude 1634 m`), `Altitude …` while loading, or `Altitude unknown`.
- `app`: the map attribution adds `Elevation: © Mapterhorn and its sources`, which links to
  Mapterhorn's attribution page.

## Capabilities

### New Capabilities

- `elevation-data`: ground elevation at a point from Mapterhorn DEM tiles, fetching and caching
  those tiles, the explicit "unknown" result, and the altitude shown in the information panel.

### Modified Capabilities

- `map-view`: the "Map attribution" requirement adds the elevation attribution and its link.

## Non-goals

- Horizon, terrain occlusion, terrain-aware sunshine (roadmap #4).
- Downloading regions for guaranteed offline use, and cache management UI (roadmap #6). Until then,
  offline availability depends on what the disk cache still holds.
- Zoom levels other than 12. Whether #4 needs finer tiles near the observer is decided there.
- Distinguishing Mapterhorn's Copernicus GLO-30 fallback from national terrain models. The Alps
  are fully covered by national models.
- A fallback elevation source. If Mapterhorn restricts or ends access, a replacement is designed
  when that happens.
- Trees and buildings (surface models).

## Impact

- **Code:** new elevation types and functions in `core`. In `app`, a new `elevation` package
  (Mapterhorn tile source, fetching with cache, decoding, repository), elevation state in
  `MapViewModel`, a panel row, and the attribution link in `MapLabels`.
- **Dependencies:** none new. OkHttp (already present) provides the HTTP disk cache, and Android's
  `BitmapFactory` decodes the lossless WebP tiles.
- **Network:** requests to `tiles.mapterhorn.com`, about 140 KiB per tile. The disk cache is
  capped at 100 MiB.
- **Tests:** `core` reference-value tests against swissALTI3D (Interlaken, a point where four tiles
  meet, Kleine Scheidegg, Jungfrau). `MapViewModel` tests for loading, known, unknown and
  reconnect. Bit-exact WebP decoding is checked on a device.
- **Docs:** `docs/roadmap.md` entry #3 records the decisions; `investigations/` holds the spike.
