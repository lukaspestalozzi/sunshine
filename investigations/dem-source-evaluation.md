# Evaluation: elevation (DEM) sources for terrain-aware sunshine

Spike for roadmap #3 `add-elevation-data`, run before its proposal. Question: which elevation
source, format and resolution should the app use? The roadmap offered two formats of the AWS
Terrain Tiles (Terrarium PNG, Skadi HGT); this spike also looked for better alternatives.
Produced 2026-09-26; scripts in the appendix, raw outputs were not committed.

## Summary

- **Mapterhorn** ([mapterhorn.com](https://mapterhorn.com/)) is new since 2025 and fits best. It
  publishes Terrarium-encoded, lossless-WebP 512 px tiles built from national LiDAR terrain models,
  and it covers the whole Alps at 0.5–10 m source resolution. Against swissALTI3D it is 10x more
  accurate than every other candidate: RMSE 1.8 m at z12, 0.9 m at z13, vs. 17–27 m. Summits come
  within 1–15 m of their published heights; the other sources cut them by 30–290 m.
- The **AWS Terrain Tiles** (Terrarium and Skadi) are frozen: the Mapzen service was discontinued
  in 2018, and Skadi files date from 2016. They cut Alpine summits by up to 196 m (Terrarium) and
  262 m (Skadi). In Switzerland, Terrarium zooms above 12 carry no extra information.
- **Copernicus GLO-30** is a surface model (trees, buildings): in the study area it reads +6.6 m
  high on average and +17 m on slopes ≥ 45°. It also truncates summits (down to −287 m).
- For horizons, the AWS z12 tiles miss the reference horizon by up to 6.7° (RMSE up to 1.35°).
  Mapterhorn z12 misses by at most 1.0° (RMSE ≤ 0.12°). The sun climbs at most 0.172°/min at
  46.7° N, so a 1° horizon error shifts first/last sunshine by **at least 6 min**, and 6.7° by at
  least 39 min.
- **Open risk:** Mapterhorn publishes no usage policy for its tile endpoint or its downloads, and
  it is a young, grant-funded project. The maintainers have to be asked before the app depends on
  it (see "Open questions").

## Candidates

| Source | Kind | Grid / format | Alps resolution | License | Status | Verdict |
|--------|------|---------------|-----------------|---------|--------|---------|
| AWS Terrain Tiles, Terrarium | mixed (SRTM, EU-DEM, GMTED; AT 10 m) | Web-Mercator XYZ, 256 px PNG, z0–15 | ~30 m (native ≈ z12; z13–15 upsampled, measured identical) | per [joerd attribution](https://github.com/tilezen/joerd/blob/master/docs/attribution.md) | unmaintained since 2018 | measured, rejected |
| AWS Terrain Tiles, Skadi | SRTM-based | 1°x1° HGT, 1" (3601²), gzip ~14 MB | ~30 m | as above | files dated 2016 | measured, rejected |
| Copernicus GLO-30 ([AWS](https://registry.opendata.aws/copernicus-dem/)) | **DSM** (surface) | 1°x1° COG float32, ~42 MB | 30 m | Copernicus, free with attribution | 2021 release | measured, rejected |
| **Mapterhorn** | DTM mosaic of national LiDAR models | Web-Mercator XYZ, 512 px lossless WebP, Terrarium; PMTiles (planet z0–12, regional z13–18) | CH 0.5 m, AT 1 m, Bavaria 1 m, FR 1 m, SI 1 m, Aosta 2 m, Bolzano 2.5 m, Trentino/Lombardy/Piedmont 5 m, rest of IT 10 m (TINITALY); GLO-30 as global fallback | code BSD-3; data per source (mostly CC BY 4.0, swisstopo OGD, some CC0; Trentino CC BY 2.5), list in [attribution.json](https://download.mapterhorn.com/attribution.json) | active, NLnet-funded, hosted on Cloudflare R2 | **measured, recommended** |
| [Sonny's LiDAR DTMs](https://sonny.4lima.de/) | DTM from national LiDAR | 1°x1° HGT 1"/3" (AT, CH also 0.5"); Google Drive downloads per country | ~20x30 m at 1" | CC BY 4.0 | updated 2025-08 | not measured: no programmatic download (Google Drive); would have to be re-hosted |
| [swissALTI3D](https://opendata.swiss/en/dataset/swissalti3d) | DTM (LiDAR) | 1 km COG tiles, LV95, 0.5 m / 2 m (2 m ≈ 1.1 MB) | 0.5 m | swisstopo OGD | active | used as the **reference**; Switzerland only |
| [viewfinderpanoramas](https://viewfinderpanoramas.org/dem1d.html) | DEM from topo maps | HGT 1"/3" | ~30 m | "limited commercial use OK"; the author warns 1" Alps data "may be contested … on copyright grounds" | – | rejected (license risk exactly in the Alps) |
| Copernicus EEA-10 | DSM | – | 10 m | [restricted to eligible users](https://dataspace.copernicus.eu/explore-data/data-collections/copernicus-contributing-missions/collections-description/COP-DEM) | – | rejected (not public) |
| Nimbo Terrarium | copy of the Mapzen data | as AWS Terrarium | as AWS | – | – | same data as AWS, not measured |
| MapTiler / Mapbox terrain-RGB | commercial | XYZ | – | API key and commercial terms | – | not evaluated (key-based services) |
| Online elevation / horizon APIs (Open-Elevation, OpenTopography, swisstopo height API, PVGIS horizons) | – | per-point online | – | – | – | out of scope: `config.yaml` rules out per-point online APIs (offline-first) |

A related observation: [ShadeMap](https://shademap.app/help/), the best-known web shadow
simulator, "only uses the data displayed in the viewport to calculate shadows. Mountains … beyond
the borders of the map will not be shown." For roadmap #5 this is the failure mode to avoid: the
horizon needs terrain far beyond the visible area.

## Method

- **Reference:** swissALTI3D 2 m (latest year per 1 km tile, via the swisstopo STAC API),
  bilinear. Sanity check: the reference summit heights agree with published heights (Jungfrau
  4157.8 vs 4158, Eiger 3966.9 vs 3967, Finsteraarhorn 4273.3 vs 4274, Niesen 2361.9 vs 2362).
- **Point accuracy:** 300 random points in 46.55–46.72° N, 7.75–8.05° E (Interlaken, Lauterbrunnen,
  Grindelwald, Schynige Platte, lakes; seed 42). Slope comes from the reference (±15 m). Every
  source is sampled bilinearly in its own grid.
- **Summits (CH):** the reference maximum within ±250 m of the approximate peak position. For each
  source: the value at that point, and its maximum within ±60 m (what a horizon ray can hit).
  Männlichen and Faulhorn were dropped because the approximate positions missed the summit.
- **Summits (outside CH):** the maximum within ±400 m of each source vs. the commonly published
  height. These published heights were not re-verified one by one, so treat that table as
  indicative. The check is independent of the reference, because outside Switzerland Mapterhorn
  does not use swissALTI3D.
- **Horizons:** 5 observers, 0.5° azimuth steps, rays 10 m to 40 km (step max(5 m, 0.4 % of
  distance)), earth curvature with refraction coefficient k = 0.13. The observer stands 1.7 m above
  the ground height of the source being tested. The reference horizon comes from Mapterhorn z13
  (6.5 m px), whose point RMSE against swissALTI3D is 0.9 m.
- **Terrarium decoding:** `h = R·256 + G + B/256 − 32768`.

## Results

### Point accuracy vs swissALTI3D (m; source − reference)

| Source | Pixel at 46.7° N | Bias | RMSE | p95 abs | Max abs | RMSE by slope <10° / 10–30° / 30–45° / ≥45° |
|--------|------------------|------|------|---------|---------|----------------------------------------------|
| AWS Terrarium z12 | 26 m | +1.0 | 19.2 | 38.2 | 84.8 | 7.4 / 14.1 / 21.9 / 31.3 |
| AWS Terrarium z13 | 13 m | +1.1 | 19.0 | 38.8 | 85.0 | (same as z12) |
| AWS Terrarium z15 | 3.3 m | +1.1 | 19.1 | 38.9 | 85.9 | (same as z12) |
| AWS Skadi 1" | ~21x31 m | +5.3 | 26.6 | 32.4 | 347.0 | 6.1 / 9.0 / 40.3 / 35.5 |
| Copernicus GLO-30 | ~21x31 m | +6.6 | 17.1 | 30.3 | 132.3 | 3.0 / 8.0 / 19.7 / 32.6 |
| **Mapterhorn z12** | 13 m | 0.0 | **1.8** | 2.9 | 14.8 | 0.3 / 0.7 / 1.8 / 3.8 |
| **Mapterhorn z13** | 6.5 m | 0.0 | **0.9** | 1.5 | 7.6 | 0.1 / 0.5 / 0.9 / 2.0 |
| Mapterhorn z14 | 3.3 m | 0.0 | 0.4 | 0.5 | 5.0 | 0.1 / 0.2 / 0.2 / 1.0 |

n = 300 (slope classes: 49 / 118 / 92 / 41). Copernicus bias grows with slope (+1.2 / +3.5 /
+9.1 / +16.9 m), which is consistent with a surface model over forested slopes.

**Caveat:** inside Switzerland, Mapterhorn is *derived from* swissALTI3D (0.5 m). This table
therefore shows how much its tiling and resampling cost, not independent accuracy. The
out-of-Switzerland summit table below is the independent check.

### Summits in Switzerland (m; source − reference; value at the summit / maximum within ±60 m)

| Summit | Reference | AWS z12 | Skadi 1" | Copernicus | Mapterhorn z12 | Mapterhorn z13 |
|--------|-----------|---------|----------|------------|----------------|----------------|
| Jungfrau | 4157.8 | −172 / −134 | −37 / −32 | −70 / −66 | −6.3 / −4.6 | −3.1 / −1.5 |
| Mönch | 4106.5 | −55 / −45 | −28 / −26 | −14 / −10 | −7.3 / −4.7 | −2.8 / −2.8 |
| Eiger | 3966.9 | −55 / −50 | −27 / −25 | −19 / −13 | −4.9 / −4.9 | −1.7 / −1.7 |
| Schilthorn | 2968.1 | −30 / −30 | −21 / −17 | −19 / −10 | −3.3 / −2.6 | −1.5 / −0.5 |
| Niesen | 2361.9 | −49 / −38 | −33 / −17 | −13 / −9 | −2.8 / −1.1 | −0.7 / −0.2 |
| Wetterhorn | 3689.8 | −148 / −142 | −39 / −35 | −4 / +23 | −4.2 / −2.4 | −0.9 / −0.9 |
| Schreckhorn | 4077.2 | −129 / −128 | −157 / −136 | −150 / −138 | −3.9 / −3.9 | −1.8 / −1.5 |
| Finsteraarhorn | 4273.3 | −196 / −148 | −262 / −79 | −287 / −202 | −6.1 / −6.1 | −2.4 / −2.4 |

### Summits elsewhere in the Alps (m; maximum within ±400 m − published height)

| Summit | Published | Mapterhorn z13 | Mapterhorn z12 | AWS z12 | Copernicus |
|--------|-----------|----------------|----------------|---------|------------|
| Grossglockner (AT) | 3798 | −3.4 | −9.2 | −13.4 | −142.3 |
| Wildspitze (AT) | 3768 | −3.7 | −7.2 | −15.0 | −30.4 |
| Hoher Dachstein (AT) | 2995 | −2.3 | −8.2 | −7.1 | −146.0 |
| Zugspitze (DE) | 2962 | −4.1 | −7.9 | −16.3 | −13.4 |
| Watzmann (DE) | 2713 | −5.9 | −7.8 | −69.0 | −55.4 |
| Triglav (SI) | 2864 | −2.2 | −3.9 | −87.7 | −64.5 |
| Ortler (IT) | 3905 | −11.6 | −14.8 | −54.9 | −31.3 |
| Marmolada (IT) | 3343 | −1.5 | −3.2 | −60.2 | −36.1 |
| Gran Paradiso (IT) | 4061 | −6.3 | −8.4 | −79.5 | −57.9 |
| Barre des Écrins (FR) | 4102 | −4.7 | −9.5 | −87.2 | −81.3 |
| Mont Blanc (FR/IT) | 4806 | +0.3 | −1.0 | −16.5 | +4.5 |

AWS does comparatively well in Austria, where joerd used the 10 m Austrian model.

### Horizon profiles vs reference (Mapterhorn z13)

Ground: the source's height at the observer minus the reference height (m). Horizon: RMSE / max
abs difference over 720 azimuths (°).

| Observer | Reference ground | Mapterhorn z12 | Mapterhorn z11 | Mapterhorn z10 | AWS z12 | Skadi 1" | Copernicus |
|----------|------------------|----------------|----------------|----------------|---------|----------|------------|
| Interlaken | 568.1 | −0.1 m, 0.02 / 0.19° | −0.1 m, 0.07 / 0.48° | −0.1 m, 0.16 / 0.92° | −0.3 m, 0.25 / 1.05° | +1.1 m, 0.19 / 1.04° | +0.7 m, 0.14 / 0.87° |
| Lauterbrunnen | 785.6 | −0.2 m, 0.08 / 1.01° | −1.5 m, 0.19 / 2.10° | −1.4 m, 0.48 / 4.23° | +5.1 m, 0.67 / 3.41° | +1.2 m, 1.46 / 8.53° | +2.4 m, 1.22 / 8.72° |
| Grindelwald | 1048.6 | +0.1 m, 0.12 / 1.01° | +0.5 m, 0.28 / 1.70° | +1.0 m, 0.44 / 2.14° | +9.2 m, 1.35 / 6.68° | +2.7 m, 0.90 / 4.64° | +3.4 m, 0.88 / 4.32° |
| Mürren | 1634.1 | −0.1 m, 0.12 / 0.56° | −0.6 m, 0.19 / 1.08° | +0.9 m, 1.08 / 5.30° | +8.7 m, 1.22 / 4.88° | +5.6 m, 1.20 / 5.40° | +3.2 m, 1.01 / 4.19° |
| Kleine Scheidegg | 2061.3 | 0.0 m, 0.06 / 0.48° | +0.4 m, 0.21 / 1.39° | −0.2 m, 0.36 / 2.04° | −1.8 m, 0.84 / 3.12° | +1.1 m, 0.60 / 2.95° | +1.2 m, 0.45 / 1.86° |

From horizon error to time error: the sun's elevation changes at most 15°/h · cos φ =
**0.172°/min** at 46.7° N. A horizon error of Δ° therefore shifts a first/last-sunshine time by
**at least Δ / 0.172 min**:

| Horizon error | Minimum time error |
|---------------|--------------------|
| 0.12° (Mapterhorn z12, worst RMSE) | 0.7 min |
| 1.0° (Mapterhorn z12, worst case) | 5.8 min |
| 1.35° (AWS z12, worst RMSE) | 7.8 min |
| 6.7° (AWS z12, worst case) | 39 min |

The worst cases come from nearby steep terrain (cliffs within a few hundred metres, e.g.
Lauterbrunnen), where the resolution close to the observer matters most. Far terrain is less
sensitive: at Interlaken even z10 (52 m px) stays within 0.92°. **Input for #4:** use fine tiles
near the observer and coarser tiles farther out.

### Terrarium pixel registration

Mapterhorn values belong to **pixel centres**: point RMSE is 1.8 m with centre registration vs.
5.8 m with corner registration at z12. For AWS z12 the result is inconclusive (19.2 vs 17.7 m)
and was not pursued.

### Storage and transfer

Measured tile sizes (Bernese Oberland):

| Tiles | Mean size | Tile width at 46.7° N |
|-------|-----------|-----------------------|
| Mapterhorn z10 / z11 / z12 / z13 / z14 | 167 / 128 / 140 / 143 / 148 KiB | 26.8 / 13.4 / 6.7 / 3.35 / 1.68 km |
| AWS Terrarium z12 | 129 KiB | 6.7 km |
| Skadi 1°x1° (gzip) | ~13.8 MB (55.2 MB for 4 files) | – |
| Copernicus 1°x1° COG | ~42 MB (168.5 MB for 4 files) | – |

Worked example: a 100 km x 100 km area (region plus horizon margin) holds 225 Mapterhorn z12
tiles ≈ **31 MiB**, 900 z13 tiles ≈ 126 MiB, or 64 z11 tiles ≈ 8 MiB. AWS z12 needs a similar
28 MiB for half the resolution. With 1°-cell formats the same area typically spans 4 cells:
≈ 55 MB (Skadi) or ≈ 168 MB (Copernicus).

### Mapterhorn access paths (verified)

- Tile endpoint `https://tiles.mapterhorn.com/{z}/{x}/{y}.webp`
  ([TileJSON](https://tiles.mapterhorn.com/tilejson.json): `encoding: terrarium`, `tileSize: 512`).
- PMTiles: `planet.pmtiles` (z0–12, 355.6 GB) and 6/x/y regional archives (z13–18, e.g.
  `6-33-22.pmtiles` = 614.9 GB for the Bernese Oberland's z6 tile). Both are clustered, with gzip
  directories and uncompressed WebP tiles. Reading one tile over HTTP range requests took 5
  requests plus 14–17 KB of directory data, and the result was **byte-identical** to the tile
  endpoint (checked at z12, z13, z14). A mirror exists on
  [Source Coop](https://source.coop/mapterhorn/mapterhorn).
- The WebP files are lossless (`VP8L` chunk), RGB without alpha or ICC profile. Lossless is a
  requirement for Terrarium.
- Existing JVM/Android PMTiles readers: [pmtiles-reader](https://github.com/simonpoole/pmtiles-reader)
  (Maven Central, Android-compatible) and [pmtiles-kotlin](https://github.com/MinotaurG/pmtiles-kotlin)
  (JitPack). MapLibre Native Android supports `pmtiles://` for raster-dem sources
  ([docs](https://maplibre.org/maplibre-native/android/examples/data/PMTiles/)), but PMTiles
  sources do not support offline packs. None of these were tried in this spike.

## Open questions for `add-elevation-data`

These are not decided here; they go into the change's design.

1. **Source:** Mapterhorn (recommended by the numbers above) or a fallback. Fallback candidates:
   Sonny 1" (good DTM, but it would have to be re-hosted) or AWS Terrarium (frozen, 30 m).
2. **Usage permission:** there is no published usage policy for `tiles.mapterhorn.com` or the
   PMTiles downloads (an [open issue](https://github.com/mapterhorn/mapterhorn/issues/317) asks the
   same question; unanswered). Ask the maintainers whether an offline-first app may fetch tiles
   per region and cache them, and whether the static PMTiles download (range requests) or the tile
   endpoint is preferred.
3. **Zoom:** z12 (13 m, ~31 MiB per 100x100 km) looks sufficient for the observer's elevation and
   far terrain. Close to steep terrain, z13 roughly halves the horizon error. Which zooms #3
   fetches is decided by the horizon's needs (#4).
4. **Decoding location:** `core` must stay free of Android dependencies, and neither Android's
   `BitmapFactory` nor `javax.imageio` exists on both platforms. Likely split: `app` decodes the
   WebP into an RGB array, and `core` turns it into heights and interpolates. Unverified: that
   `BitmapFactory` decodes lossless WebP bit-exactly on all supported API levels. This needs an
   on-device check.
5. **Attribution:** the app must show the sources of the region in use (swisstopo, BEV, Bayern,
   Provinz Bozen, IGN, ARSO, …, Copernicus for the fallback). One static "Mapterhorn and its
   sources" line with a link may or may not satisfy CC BY; to be decided.
6. **Unknown vs. fallback data:** where no national model exists, Mapterhorn falls back to
   Copernicus GLO-30, a surface model. The spec needs to say whether that counts as "known"
   elevation.
7. **Test oracles:** Mapterhorn tiles are rebuilt (Last-Modified 2026-09-11/13), so unit tests
   should pin a few tiles as fixtures and use the reference values below with the tolerances
   measured here.

Related legacy note: `sunrise-integration-test-plan.md` §4.3 uses SRTM elevations. Given the
Skadi results above (errors up to 347 m, summits too low by up to 262 m), those values are not
suitable as oracles.

## Reference values (candidate test oracles)

swissALTI3D 2 m, bilinear (latest tile year at access, 2026-09-26):

| Point | Lat, lon | Height (m) | Slope |
|-------|----------|------------|-------|
| Interlaken (Höhematte) | 46.6863, 7.8632 | 568.0 | flat |
| Lauterbrunnen | 46.5935, 7.9091 | 785.7 | – |
| Grindelwald | 46.6242, 8.0414 | 1048.6 | – |
| Mürren | 46.5590, 7.8920 | 1634.4 | – |
| Kleine Scheidegg | 46.5853, 7.9610 | 2061.3 | – |
| random point | 46.69407, 7.93112 | 563.7 | 0° |
| random point | 46.71508, 8.00823 | 1553.5 | 12° |
| random point | 46.67394, 7.95201 | 2177.4 | 26° |
| random point | 46.69889, 7.82902 | 957.0 | 41° |
| random point | 46.63510, 7.80360 | 1699.5 | 72° |
| Jungfrau summit | 46.53679, 7.96258 | 4157.8 | summit |
| Eiger summit | 46.57755, 8.00522 | 3966.9 | summit |
| Finsteraarhorn summit | 46.53729, 8.12618 | 4273.3 | summit |

Tolerances for Mapterhorn z12 with bilinear interpolation should come from the measured error
distribution. Absolute error p95 by slope: 0.5 m (< 10°), 1.3 m (10–30°), 2.2 m (30–45°),
8.5 m (≥ 45°); single outliers reach 14.8 m. Summits read low by 0.5–7.3 m in Switzerland and up
to 14.8 m elsewhere. Oracle points belong on gentle terrain, where a tight tolerance (±1 m) holds.

Reference horizon (Mapterhorn z13; observer 1.7 m above ground; k = 0.13; rays to 40 km),
horizon angle in ° at azimuth 0°, 10°, …, 350°:

| Observer | Horizon angles |
|----------|----------------|
| Interlaken | 24.49, 21.1, 18.0, 14.54, 11.5, 5.36, 3.52, 3.07, 8.07, 10.61, 14.17, 14.98, 15.07, 16.58, 16.22, 13.27, 9.3, 10.52, 13.36, 12.23, 11.13, 11.1, 8.65, 5.88, 4.45, 5.17, 2.25, 3.9, 9.02, 10.13, 16.17, 22.0, 24.79, 26.92, 26.56, 26.63 |
| Lauterbrunnen | 12.25, 14.09, 18.26, 20.51, 23.75, 23.71, 24.65, 26.3, 30.82, 32.2, 32.78, 29.89, 27.43, 25.96, 24.3, 23.12, 21.07, 19.85, 13.83, 17.92, 23.15, 31.27, 35.48, 37.4, 39.73, 39.29, 36.62, 34.63, 30.99, 26.03, 25.42, 22.06, 15.92, 12.62, 10.13, 6.76 |
| Grindelwald | 16.93, 15.2, 14.12, 11.75, 11.04, 9.07, 10.64, 21.88, 21.78, 18.64, 26.64, 28.82, 33.39, 31.97, 24.01, 15.42, 18.44, 19.56, 28.6, 28.22, 25.4, 24.55, 13.1, 8.17, 8.81, 10.69, 8.63, 7.52, 5.07, 6.26, 9.29, 12.36, 13.54, 16.12, 18.99, 18.78 |
| Mürren | 21.39, 15.43, 12.48, 9.93, 6.73, 8.05, 6.21, 6.01, 12.65, 16.78, 20.03, 21.41, 20.62, 15.95, 17.12, 16.79, 16.03, 14.48, 11.38, 11.98, 11.17, 13.77, 12.18, 11.85, 15.46, 18.88, 20.8, 19.12, 21.13, 18.98, 19.56, 21.12, 23.69, 25.9, 26.47, 25.04 |
| Kleine Scheidegg | 6.87, 2.61, 3.49, 2.2, 2.39, 0.7, 5.38, 6.01, 8.1, 13.47, 26.08, 26.04, 24.45, 23.51, 25.82, 18.9, 17.68, 19.82, 21.03, 17.76, 14.26, 7.81, 5.75, 6.77, 9.53, 10.67, 12.92, 13.13, 14.46, 15.97, 16.73, 16.85, 13.32, 12.96, 9.75, 8.06 |

These horizon values depend on the ray parameters above. For #4 they are a plausibility check,
not an exact oracle.

## Limitations

- One study area, the Bernese Oberland, for the point and horizon tests; summits only elsewhere.
- Vertical datums were not reconciled: swissALTI3D uses LN02, Copernicus EGM2008, SRTM EGM96.
  In Switzerland the difference is on the order of a metre, small next to the 17–27 m errors of
  the 30 m sources.
- The horizon reference is itself a DEM (Mapterhorn z13), not a surveyed horizon, so its own
  error (point RMSE 0.9 m) is included in every comparison.
- Sonny's DTMs were not measured (manual Google Drive download).

## Scripts

Python 3.11 in a venv with `numpy pillow rasterio pmtiles requests` (GDAL 3.10.3 via rasterio).
Downloaded data is cached under `cache/`. Run order: `accuracy.py`, `horizon.py`,
`peaks_alps.py`, `pmt.py`.

### dem.py

```python
"""DEM spike: samplers for candidate elevation sources, each with a local download cache."""
import gzip
import io
import json
import math
import os
import time

import numpy as np
import rasterio
import requests
from PIL import Image
from rasterio.warp import transform

CACHE = os.path.join(os.path.dirname(__file__), "cache")
UA = {"User-Agent": "sunshine-dem-spike/0.1 (research; github.com/lukaspestalozzi/sunshine)"}
SESSION = requests.Session()
SESSION.headers.update(UA)
STATS = {}  # source -> [files, bytes]


def fetch(url, path):
    full = os.path.join(CACHE, path)
    if not os.path.exists(full):
        os.makedirs(os.path.dirname(full), exist_ok=True)
        for attempt in range(4):
            r = SESSION.get(url, timeout=120)
            if r.status_code == 200:
                break
            if r.status_code == 404:
                return None
            time.sleep(2 ** attempt)
        r.raise_for_status()
        tmp = f"{full}.{os.getpid()}.part"
        with open(tmp, "wb") as f:
            f.write(r.content)
        os.replace(tmp, full)
        time.sleep(0.05)
    return full


def bilinear(grid, fx, fy):
    """grid[row, col]; fx/fy are fractional column/row indices of pixel centres."""
    x0, y0 = int(math.floor(fx)), int(math.floor(fy))
    dx, dy = fx - x0, fy - y0
    h, w = grid.shape
    if not (0 <= x0 < w - 1 and 0 <= y0 < h - 1):
        return None
    q = grid[y0:y0 + 2, x0:x0 + 2].astype(np.float64)
    return float(q[0, 0] * (1 - dx) * (1 - dy) + q[0, 1] * dx * (1 - dy)
                 + q[1, 0] * (1 - dx) * dy + q[1, 1] * dx * dy)


def to_lv95(lon, lat):
    e, n = transform("EPSG:4326", "EPSG:2056", [lon], [lat])
    return e[0], n[0]


# ---------------------------------------------------------------- swissALTI3D (reference)
_alti_cache = {}


def alti_tile(ekm, nkm):
    key = (ekm, nkm)
    if key in _alti_cache:
        return _alti_cache[key]
    meta_path = os.path.join(CACHE, "alti", f"{ekm}-{nkm}.json")
    if not os.path.exists(meta_path):
        cx, cy = transform("EPSG:2056", "EPSG:4326", [ekm * 1000 + 500], [nkm * 1000 + 500])
        bbox = f"{cx[0]-0.0005},{cy[0]-0.0005},{cx[0]+0.0005},{cy[0]+0.0005}"
        url = ("https://data.geo.admin.ch/api/stac/v0.9/collections/ch.swisstopo.swissalti3d/items"
               f"?bbox={bbox}&limit=100")
        feats = SESSION.get(url, timeout=60).json()["features"]
        feats = [f for f in feats if f["id"].endswith(f"_{ekm}-{nkm}")]
        os.makedirs(os.path.dirname(meta_path), exist_ok=True)
        json.dump([f["id"] for f in feats], open(meta_path, "w"))
    ids = json.load(open(meta_path))
    if not ids:
        _alti_cache[key] = None
        return None
    latest = max(ids)  # swissalti3d_<year>_<E>-<N>
    href = f"https://data.geo.admin.ch/ch.swisstopo.swissalti3d/{latest}/{latest}_2_2056_5728.tif"
    path = fetch(href, f"alti/{latest}_2.tif")
    with rasterio.open(path) as ds:
        grid = ds.read(1).astype(np.float64)
        if ds.nodata is not None:
            grid[grid == ds.nodata] = np.nan
        _alti_cache[key] = (grid, ds.transform, latest)
    return _alti_cache[key]


def alti_lv95(e, n):
    t = alti_tile(int(e // 1000), int(n // 1000))
    if t is None:
        return None
    grid, tr, _ = t
    col, row = ~tr * (e, n)  # fractional pixel coords (corner based)
    return bilinear(grid, col - 0.5, row - 0.5)


def alti(lon, lat):
    return alti_lv95(*to_lv95(lon, lat))


# ---------------------------------------------------------------- Web-Mercator tile helpers
def merc_frac(lon, lat, z):
    n = 2 ** z
    x = (lon + 180.0) / 360.0 * n
    lr = math.radians(lat)
    y = (1.0 - math.log(math.tan(lr) + 1.0 / math.cos(lr)) / math.pi) / 2.0 * n
    return x, y


def terrarium_decode(rgb):
    rgb = rgb.astype(np.float64)
    return rgb[..., 0] * 256.0 + rgb[..., 1] + rgb[..., 2] / 256.0 - 32768.0


_tile_mem = {}


def xyz_tile(source, z, x, y):
    key = (source, z, x, y)
    if key in _tile_mem:
        return _tile_mem[key]
    if source == "aws":
        url = f"https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png"
        path = fetch(url, f"aws/{z}/{x}/{y}.png")
    else:
        url = f"https://tiles.mapterhorn.com/{z}/{x}/{y}.webp"
        path = fetch(url, f"mapterhorn/{z}/{x}/{y}.webp")
    grid = terrarium_decode(np.array(Image.open(path).convert("RGB")))
    _tile_mem[key] = grid
    return grid


def xyz_sample(source, lon, lat, z, centre_offset=0.5):
    """Bilinear sample across tile borders. centre_offset=0.5: pixel value belongs to pixel centre."""
    fx, fy = merc_frac(lon, lat, z)
    size = 256 if source == "aws" else 512
    px, py = fx * size - centre_offset, fy * size - centre_offset
    x0, y0 = int(math.floor(px)), int(math.floor(py))
    dx, dy = px - x0, py - y0
    vals = []
    for yy in (y0, y0 + 1):
        for xx in (x0, x0 + 1):
            g = xyz_tile(source, z, xx // size, yy // size)
            vals.append(g[yy % size, xx % size])
    return (vals[0] * (1 - dx) * (1 - dy) + vals[1] * dx * (1 - dy)
            + vals[2] * (1 - dx) * dy + vals[3] * dx * dy)


# ---------------------------------------------------------------- Skadi HGT (1 arcsec, 1x1 deg)
_hgt = {}


def skadi(lon, lat):
    la, lo = int(math.floor(lat)), int(math.floor(lon))
    name = f"N{la:02d}E{lo:03d}"
    if name not in _hgt:
        path = fetch(f"https://s3.amazonaws.com/elevation-tiles-prod/skadi/N{la:02d}/{name}.hgt.gz",
                     f"skadi/{name}.hgt.gz")
        raw = gzip.open(path).read()
        side = int(math.isqrt(len(raw) // 2))
        g = np.frombuffer(raw, dtype=">i2").reshape(side, side).astype(np.float64)
        g[g == -32768] = np.nan
        _hgt[name] = g
    g = _hgt[name]
    side = g.shape[0]
    fx = (lon - lo) * (side - 1)
    fy = (la + 1 - lat) * (side - 1)
    return bilinear(g, fx, fy)


# ---------------------------------------------------------------- Copernicus GLO-30 COG (DSM)
_cop = {}


def copernicus(lon, lat):
    la, lo = int(math.floor(lat)), int(math.floor(lon))
    name = f"Copernicus_DSM_COG_10_N{la:02d}_00_E{lo:03d}_00_DEM"
    if name not in _cop:
        path = fetch(f"https://copernicus-dem-30m.s3.amazonaws.com/{name}/{name}.tif", f"cop/{name}.tif")
        with rasterio.open(path) as ds:
            _cop[name] = (ds.read(1).astype(np.float64), ds.transform)
    g, tr = _cop[name]
    col, row = ~tr * (lon, lat)
    return bilinear(g, col - 0.5, row - 0.5)


def cache_stats():
    out = {}
    for root, _, files in os.walk(CACHE):
        src = os.path.relpath(root, CACHE).split(os.sep)[0]
        for f in files:
            s = out.setdefault(src, [0, 0])
            s[0] += 1
            s[1] += os.path.getsize(os.path.join(root, f))
    return out
```

### accuracy.py

```python
"""Accuracy of candidate DEMs vs swissALTI3D 2 m at random points and at summits."""
import json
import math
import random

import numpy as np

import dem

random.seed(42)
SOURCES = {
    "aws_z12": lambda lo, la: dem.xyz_sample("aws", lo, la, 12),
    "aws_z12_cornerconv": lambda lo, la: dem.xyz_sample("aws", lo, la, 12, centre_offset=0.0),
    "aws_z13": lambda lo, la: dem.xyz_sample("aws", lo, la, 13),
    "aws_z15": lambda lo, la: dem.xyz_sample("aws", lo, la, 15),
    "skadi_1as": dem.skadi,
    "copernicus_glo30": dem.copernicus,
    "mapterhorn_z12": lambda lo, la: dem.xyz_sample("mth", lo, la, 12),
    "mapterhorn_z12_cornerconv": lambda lo, la: dem.xyz_sample("mth", lo, la, 12, centre_offset=0.0),
    "mapterhorn_z13": lambda lo, la: dem.xyz_sample("mth", lo, la, 13),
    "mapterhorn_z14": lambda lo, la: dem.xyz_sample("mth", lo, la, 14),
}

# ---------------------------------------------------------------- random points
points = []
while len(points) < 300:
    lat, lon = random.uniform(46.55, 46.72), random.uniform(7.75, 8.05)
    e, n = dem.to_lv95(lon, lat)
    if min(e % 1000, 1000 - e % 1000, n % 1000, 1000 - n % 1000) < 30:
        continue
    ref = dem.alti_lv95(e, n)
    if ref is None or math.isnan(ref):
        continue
    dzdx = (dem.alti_lv95(e + 15, n) - dem.alti_lv95(e - 15, n)) / 30
    dzdy = (dem.alti_lv95(e, n + 15) - dem.alti_lv95(e, n - 15)) / 30
    slope = math.degrees(math.atan(math.hypot(dzdx, dzdy)))
    points.append({"lat": lat, "lon": lon, "ref": ref, "slope": slope})
    if len(points) % 25 == 0:
        print("points", len(points), flush=True)

for i, p in enumerate(points):
    for name, f in SOURCES.items():
        p[name] = f(p["lon"], p["lat"])
    if i % 50 == 0:
        print("sampled", i, flush=True)

json.dump(points, open("points.json", "w"))


def stats(errs):
    a = np.array(errs)
    return {"n": len(a), "bias": round(float(a.mean()), 1), "rmse": round(float(np.sqrt((a ** 2).mean())), 1),
            "p95_abs": round(float(np.percentile(abs(a), 95)), 1), "max_abs": round(float(abs(a).max()), 1)}


classes = {"all": lambda s: True, "slope<10": lambda s: s < 10, "10-30": lambda s: 10 <= s < 30,
           "30-45": lambda s: 30 <= s < 45, ">=45": lambda s: s >= 45}
report = {}
for name in SOURCES:
    report[name] = {c: stats([p[name] - p["ref"] for p in points if f(p["slope"])]) for c, f in classes.items()}
json.dump(report, open("accuracy_report.json", "w"), indent=1)

# ---------------------------------------------------------------- summits
PEAKS = {"Jungfrau": (7.9625, 46.5367), "Moench": (7.9973, 46.5585), "Eiger": (8.0053, 46.5776),
         "Schilthorn": (7.8353, 46.5579), "Niesen": (7.6513, 46.6455), "Maennlichen": (7.9406, 46.6130),
         "Wetterhorn": (8.1150, 46.6380), "Schreckhorn": (8.1181, 46.5900),
         "Finsteraarhorn": (8.1261, 46.5372), "Faulhorn": (8.0232, 46.6754)}
summits = {}
for peak, (lon, lat) in PEAKS.items():
    e0, n0 = dem.to_lv95(lon, lat)
    best = (-1e9, None)
    for de in range(-250, 251, 4):
        for dn in range(-250, 251, 4):
            v = dem.alti_lv95(e0 + de, n0 + dn)
            if v is not None and not math.isnan(v) and v > best[0]:
                best = (v, (e0 + de, n0 + dn))
    ref, (e, n) = best
    from rasterio.warp import transform
    lo, la = transform("EPSG:2056", "EPSG:4326", [e], [n])
    lo, la = lo[0], la[0]
    row = {"ref_max": round(ref, 1), "lon": round(lo, 5), "lat": round(la, 5)}
    for name, f in SOURCES.items():
        if "cornerconv" in name:
            continue
        at = f(lo, la)
        # max of the source within +-60 m of the true summit (what a horizon ray could hit)
        mx = max(f(lo + dx / (111320 * math.cos(math.radians(la))), la + dy / 111320)
                 for dx in range(-60, 61, 5) for dy in range(-60, 61, 5))
        row[name] = {"at_summit": round(at - ref, 1), "max_near": round(mx - ref, 1)}
    summits[peak] = row
    print(peak, row["ref_max"], flush=True)
json.dump(summits, open("summits_report.json", "w"), indent=1)
print(json.dumps(dem.cache_stats()))
```

### horizon.py

```python
"""Horizon profiles from different DEMs; reference = Mapterhorn z13 (6.5 m px, swissALTI3D-derived)."""
import json
import math
import sys

import numpy as np

import dem

R = 6371000.0
K = 0.13  # refraction coefficient for the terrain line of sight
OBSERVERS = {"Interlaken": (7.8632, 46.6863), "Lauterbrunnen": (7.9091, 46.5935),
             "Grindelwald": (8.0414, 46.6242), "Muerren": (7.8920, 46.5590),
             "KleineScheidegg": (7.9610, 46.5853)}
MAX_D = 40000.0
AZ_STEP = 0.5

SOURCES = {
    "ref_mapterhorn_z13": lambda lo, la: dem.xyz_sample("mth", lo, la, 13),
    "mapterhorn_z12": lambda lo, la: dem.xyz_sample("mth", lo, la, 12),
    "mapterhorn_z11": lambda lo, la: dem.xyz_sample("mth", lo, la, 11),
    "mapterhorn_z10": lambda lo, la: dem.xyz_sample("mth", lo, la, 10),
    "aws_z12": lambda lo, la: dem.xyz_sample("aws", lo, la, 12),
    "skadi_1as": dem.skadi,
    "copernicus_glo30": dem.copernicus,
}


def distances():
    d, out = 10.0, []
    while d <= MAX_D:
        out.append(d)
        d += max(5.0, d * 0.004)  # 5 m near, ~160 m at 40 km (<= ~0.25 px at z12)
    return out


DIST = distances()


def horizon(sample, lon, lat, obs_h):
    coslat = math.cos(math.radians(lat))
    out = []
    for i in range(int(360 / AZ_STEP)):
        az = math.radians(i * AZ_STEP)
        best = -90.0
        for d in DIST:
            la = lat + d * math.cos(az) / 111320.0
            lo = lon + d * math.sin(az) / (111320.0 * coslat)
            h = sample(lo, la)
            if h is None or (isinstance(h, float) and math.isnan(h)):
                continue
            drop = d * d / (2 * R) * (1 - K)
            ang = math.degrees(math.atan2(h - drop - obs_h, d))
            if ang > best:
                best = ang
        out.append(best)
    return np.array(out)


results = {}
for name, (lon, lat) in OBSERVERS.items():
    results[name] = {}
    ground = {s: f(lon, lat) for s, f in SOURCES.items()}
    ref_h = ground["ref_mapterhorn_z13"] + 1.7
    ref = horizon(SOURCES["ref_mapterhorn_z13"], lon, lat, ref_h)
    results[name]["ref_profile_every_10deg"] = [round(float(x), 2) for x in ref[:: int(10 / AZ_STEP)]]
    results[name]["ref_ground"] = round(ground["ref_mapterhorn_z13"], 1)
    results[name]["alti_ground"] = round(dem.alti(lon, lat), 1)
    for s, f in SOURCES.items():
        if s.startswith("ref"):
            continue
        prof = horizon(f, lon, lat, ground[s] + 1.7)
        diff = prof - ref
        results[name][s] = {"ground_minus_ref": round(ground[s] - ground["ref_mapterhorn_z13"], 1),
                            "bias": round(float(diff.mean()), 3),
                            "rmse": round(float(np.sqrt((diff ** 2).mean())), 3),
                            "max_abs": round(float(abs(diff).max()), 3)}
        print(name, s, results[name][s], flush=True)
    json.dump(results, open("horizon_report.json", "w"), indent=1)
print(json.dumps(dem.cache_stats()))
```

### peaks_alps.py

```python
import math, json, dem
PEAKS = {  # name: (lon, lat, published height m)
 "Grossglockner AT": (12.6941, 47.0745, 3798), "Wildspitze AT": (10.8672, 46.8853, 3768),
 "Hoher Dachstein AT": (13.6061, 47.4753, 2995), "Zugspitze DE": (10.9854, 47.4211, 2962),
 "Watzmann DE": (12.9217, 47.5547, 2713), "Triglav SI": (13.8369, 46.3785, 2864),
 "Ortler IT": (10.5448, 46.5086, 3905), "Marmolada IT": (11.8508, 46.4344, 3343),
 "Gran Paradiso IT": (7.2667, 45.5178, 4061), "Barre des Ecrins FR": (6.3597, 44.9222, 4102),
 "Mont Blanc FR/IT": (6.8652, 45.8326, 4806)}
SRC = {"mapterhorn_z13": lambda lo, la: dem.xyz_sample("mth", lo, la, 13),
       "mapterhorn_z12": lambda lo, la: dem.xyz_sample("mth", lo, la, 12),
       "aws_z12": lambda lo, la: dem.xyz_sample("aws", lo, la, 12),
       "copernicus_glo30": dem.copernicus}
out = {}
for name, (lon, lat, pub) in PEAKS.items():
    row = {"published": pub}
    for s, f in SRC.items():
        step = 8 if s.startswith("mapterhorn_z13") else 15
        best = -1e9
        for dx in range(-400, 401, step):
            for dy in range(-400, 401, step):
                v = f(lon + dx / (111320 * math.cos(math.radians(lat))), lat + dy / 111320)
                if v is not None and not math.isnan(v): best = max(best, v)
        row[s] = round(best - pub, 1)
    out[name] = row; print(name, row, flush=True)
json.dump(out, open("peaks_alps_report.json", "w"), indent=1)
```

### pmt.py

```python
import hashlib, requests
from pmtiles.reader import Reader
S = requests.Session(); S.headers["User-Agent"] = "sunshine-dem-spike/0.1 (research)"
calls = []
def http_get(url):
    def get(offset, length):
        calls.append(length)
        r = S.get(url, headers={"Range": f"bytes={offset}-{offset+length-1}"}, timeout=60)
        r.raise_for_status(); return r.content
    return get
for arch, (z, x, y) in [("planet", (12, 2137, 1445)), ("6-33-22", (13, 4274, 2891)), ("6-33-22", (14, 8548, 5782))]:
    calls.clear()
    rd = Reader(http_get(f"https://download.mapterhorn.com/{arch}.pmtiles"))
    h = rd.header()
    t = rd.get(z, x, y)
    direct = S.get(f"https://tiles.mapterhorn.com/{z}/{x}/{y}.webp", timeout=60).content
    print(arch, {k: h[k] for k in ("min_zoom", "max_zoom", "tile_type", "tile_compression", "internal_compression", "clustered", "addressed_tiles_count", "tile_contents_count")} if arch else "")
    print("  tile", z, x, y, "bytes", len(t) if t else None, "identical_to_endpoint", t is not None and hashlib.md5(t).digest() == hashlib.md5(direct).digest(), "range_requests", len(calls), "bytes_read_for_dirs", sum(calls) - (len(t) if t else 0))
    print("  metadata keys", list(rd.metadata().keys())[:10])
```
