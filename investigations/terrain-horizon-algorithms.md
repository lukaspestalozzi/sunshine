# Evaluation: computing terrain-aware sun periods efficiently

Spike for roadmap #4 `add-terrain-horizon`, run before its proposal. Question: how should the app
compute **all sun periods of a day** at the selected location (outcome (a) of the explore session),
accurately and cheaply in data, memory and CPU? The spike surveys the literature, tries several
mathematical shortcuts, measures them against a high-resolution reference at 10 locations, and
checks the results against independent oracles (places with documented sunless seasons).
Produced 2026-09-26; scripts in the appendix, raw outputs were not committed.

## Summary

- **Compute a horizon profile once per location, then derive sun periods for any date.** The
  profile (skyline angle per azimuth) does not depend on the date. Sun periods then come from
  comparing the sun's path with the profile, which costs about 8,640 cheap comparisons per day at
  10 s steps. Changing the time or date needs no new terrain work.
- **Near field decides the accuracy, far field decides the data volume.**
  - With z14 (3.3 m pixels) within 1.5 km, the errors of the other 1-D sampling choices (step
    size, azimuth resolution) stay below a minute.
  - Without it, cliff-foot locations err by up to 8.3 min (Bristen) and 4 min (Lauterbrunnen).
  - Far terrain can be coarse. Using zoom 12 → 11 → 10 beyond 6 / 25 km costs < 0.5 min.
- **Early termination is exact and removes most of the data.** A ray stops as soon as even a
  4810 m peak could not rise above the current horizon angle. With the distance-dependent zoom
  this needs 3–7 MB of tiles per new location instead of 20–27 MB, out to 150 km.
- **Recommended schedule `near14_cheap`:** z14 to 1.5 km, z12 to 6 km, z11 to 25 km, z10 to
  150 km, 0.25° azimuth resolution, early termination.
  - Error vs reference: ≤ 0.5 min at 7 of 10 locations, ≤ 1.7 min everywhere.
  - Cost per location: 18–53 tiles (2.8–7.1 MB), 2.1–3.8 M samples.
- **Independent oracles match.** The computed sunless seasons agree with documented ones:
  - Viganella: returns 2 Feb, as documented.
  - Bristen: 30 Oct – 11 Feb vs documented 30 Oct – 13 Feb.
  - Rjukan: 29 Sep – 13 Mar vs "late September to mid March".
  - Vicosoprano and Rattenberg: consistent.

  The dates are very sensitive to the exact spot: 100 m moves them by up to 3 weeks.
- **Multiple periods per day are real.**
  - Interlaken, 21 Dec: two periods (the sun hides behind a peak for 20 min).
  - Grindelwald, 21 Dec: two short periods, 21 min of sun in total.

## Approaches surveyed

| Approach | Idea | Cost for one location | Fit |
|----------|------|-----------------------|-----|
| **Sector rays** ([Dozier et al. 1981/1990](https://gmd.copernicus.org/articles/15/6817/2022/)) | For each azimuth, march along a line and keep the maximum elevation angle | azimuths × samples | Simple. Combines with every trick below. **Measured.** |
| **Projection / upper envelope** ([TPPSS](https://pypi.org/project/tppss)) | Project every DEM cell onto the observer's view (azimuth, elevation angle) and keep the maximum per azimuth bin. This is "projecting the horizon to the crosshair". | all cells within the radius | Exact per cell, but cannot stop early, so it needs every tile in the radius. At the reference resolution that is about 68 M cells. TPPSS reports only first and last sunshine. |
| **BVH ray tracing** ([HORAYZON](https://gmd.copernicus.org/articles/15/6817/2022/), Embree) | Triangle mesh with a bounding volume hierarchy. Binary or neighbour-guided search for the horizon angle. Outer terrain simplified with a bounded error `α ≈ 2·atan(Δh / 2d)`. | ~100× faster than sector rays for many points | Built for whole grids (#5, #7), not for one phone-side point. Its error bound for distant terrain is the basis of the distance-dependent zoom used here. |
| **Rotational sweep** ([Stewart 1998](https://www.dgp.toronto.edu/public_user/JamesStewart/papers/tvcg97.html)) | Horizons at all points in O(s·N^½) | – | For #5/#7 (all visible points), not for one point |
| **Maximum mipmaps** ([Tevs, Ihrke, Seidel 2008](https://pure.mpg.de/rest/items/item_1325622_5/component/file_3590464/content)) | Hierarchical max pyramid; rays skip empty space | fewer samples | Needs *max* pyramids. Mapterhorn's coarser zooms are averages, so they are not conservative. The single global bound (4810 m) used here is conservative and already very effective. |
| **Per-pixel sun rays** ([ShadeMap](https://github.com/ted-piotrowski/leaflet-shadow-simulator)) | For each screen pixel, march towards the sun (GPU) | per pixel and time | Right for the overlay (#5). ShadeMap ignores terrain outside the viewport ("Mountains … beyond the borders of the map will not be shown"), which is the failure mode to avoid there. |
| **Horizon maps + Fourier compression** ([Max 1988](https://highperformancegraphics.org/untracked/2025/presentations/Pa5_2_Fast%20Planetary%20Shadows%20using%20Fourier-Compressed%20Horizon%20Maps.pdf); Fritsch et al., HPG 2025) | Store a horizon per cell as 16 complex Fourier coefficients | storage | For #7 (per-cell horizons over an area). Not needed for one point. |

## Mathematical shortcuts tried

1. **The horizon is date-independent.** One profile per location serves every date and time.
   Sun periods: sample the sun's apparent upper limb every 10 s, interpolate the profile at the
   sun's azimuth, and find the sign changes. The legacy failure (linear interpolation across up to
   100° gaps) cannot occur with 0.25° bins.
2. **Distance-dependent zoom.** An elevation error Δh at distance d changes the horizon angle by
   about Δh/d, so the tolerable pixel size grows linearly with distance. Halving the resolution at
   each doubling of distance keeps the number of tiles per distance ring roughly constant: the
   total grows with log(radius), not radius².
3. **Exact early termination.** At distance d, no terrain can appear higher than
   `atan((H_max − h_obs − drop(d)) / d)`, where H_max = 4810 m (Mont Blanc) and
   `drop = d²/2R·(1−k)`. Once the running maximum exceeds the largest such bound for all further
   d, the ray stops. The result is identical; only work is skipped.
4. **Year-clamped horizon.** At each azimuth, the sun's elevation over the whole year lies within
   [s_min(A), s_max(A)]. The horizon only has to be known inside that interval. A ray therefore
   also stops once its maximum reaches s_max(A) (the sun is always blocked there) or once no
   further terrain can rise above max(running max, s_min(A)). This saves a further 9–45 % of
   tiles (table below) and stays valid for every date.
5. **Shadow rays for one date.** For a single date, each sun position needs only a yes/no ray
   that stops when blocked or when the terrain bound falls below the sun. This needs only 1–21
   tiles, but the result is valid for that date only. A date change needs new data, which is bad
   for offline use and for #7.
6. **Equatorial transform.** Convert the horizon into the sun's own coordinates, hour angle H and
   declination δ. At these latitudes the sun's altitude rises strictly with δ at fixed H (because
   `tan δ · cos H < tan φ`), so visibility is roughly "δ > δ_min(H)". Consequences:
   - The **sunless season is one number per location**, δ* = min_H δ_min(H). The sun returns on
     the date the solar declination exceeds δ*. Viganella: δ* = −16.62°, Bristen −13.70°,
     Rjukan −2.48°.
   - It is only approximate: 1–33 of 7,200 hour-angle rows per location have several crossings,
     for example notches seen at grazing angles. Periods derived from δ_min differ from the direct
     method by up to 1.2–2.7 min per day. Exact use needs a list of intervals per H.

   Useful for explaining seasons, and possibly for #7. Not needed for #4.
7. **Holes are not representable.** A DEM is a height field (2.5-D), so arches and holes such as
   the Martinsloch in Elm, where the sun shines through a rock window, cannot be modelled. The
   Martinsloch is therefore not usable as an oracle.

## Method

- **Data:** Mapterhorn Terrarium tiles, bilinear at pixel centres, with the `dem.fetch` disk cache
  from the DEM spike. Where a zoom is not published (Viganella and other 5 m sources have no z15;
  the server answers 404), the next coarser zoom is used. The app will need the same fallback.
- **Observer:** DEM height at z14 + 1.7 m. Earth curvature with refraction coefficient k = 0.13
  for the terrain line of sight.
- **Sun:** NOAA formulas, identical to astral 3.2 within 1e-13°. The sun counts as visible when
  its apparent **upper limb** clears the horizon: geometric elevation + refraction + 0.266°.
  Refraction is continuous, including below 0°: `1.02 / tan(h + 10.3/(h + 5.11))` arcmin, with h
  clamped at −1.9°.
- **Reference ("truth"):** z14 to 3 km, z13 to 10 km, z12 to 30 km, z11 to 80 km, z10 to 150 km;
  0.1° azimuth; step 0.5 px of the zoom in use; azimuths 30–330° (the sun never leaves this range
  above the horizon at 46–60° N; asserted per run).
- **Convergence check:** adding z15 within 1 km changes the periods by ≤ 0.83 min everywhere
  except **Bristen (5.5 min; horizon 1.32° off)**, where a steep wall close to the spot is
  resolution-sensitive even at 3 m.
- **Metric:** sun periods on the 21st of each month 2025 at 10 s resolution. Reported:
  - the maximum boundary error in minutes over the days with the same number of periods;
  - the number of days where the number of periods differs (a short period appears or vanishes);
  - tiles and MB needed with early termination, and samples as a CPU proxy.
- **Locations:** Interlaken, Lauterbrunnen, Grindelwald, Mürren, Kleine Scheidegg (Bernese
  Oberland); Viganella (IT), Bristen (UR), Vicosoprano (GR), Rattenberg (AT); Rjukan (NO, 59.9° N,
  to test latitude).

## Results

### Accuracy and data per schedule

Cell = max error (min) / days with a different number of periods / tiles and MB needed (with
early termination). Azimuth resolution 0.1° unless marked.

| Observer | flat z12, 40 km | flat z12, 150 km | lod_coarse | lod_mid @0.25° | lod_fine | **near14_cheap @0.25°** | near14 @0.25° | z15 vs reference |
|----------|-----------------|------------------|------------|----------------|----------|--------------------------|---------------|------------------|
| Interlaken | 0.33 / 0 / 68t 9.7MB | 0.33 / 0 / 118t 15.9MB | 0.83 / 0 / 35t 5.3MB | 0.5 / 0 / 44t 6.1MB | 0.17 / 0 / 94t 12.5MB | **0.5 / 0 / 44t 6.1MB** | 0.33 / 0 / 80t 10.9MB | 0.17 min |
| Lauterbrunnen | 4.0 / 0 / 16t 2.5MB | 4.0 / 0 / 16t 2.5MB | 1.83 / 0 / 17t 2.7MB | 1.83 / 0 / 20t 3.1MB | 0.17 / 0 / 43t 6.8MB | **0.33 / 0 / 18t 2.8MB** | 0.17 / 0 / 39t 6.1MB | 0.33 min |
| Grindelwald | 3.5 / 0 / 34t 5.0MB | 3.5 / 0 / 34t 5.0MB | 2.33 / 0 / 23t 3.6MB | 2.33 / 1 / 26t 3.8MB | 1.17 / 0 / 61t 9.3MB | **1.67 / 0 / 27t 4.1MB** | 0.67 / 1 / 49t 7.4MB | 0.83 min |
| Mürren | 0.67 / 1 / 27t 4.3MB | 0.67 / 1 / 27t 4.3MB | 1.33 / 1 / 14t 2.3MB | 1.0 / 1 / 24t 3.8MB | 0.67 / 0 / 56t 9.0MB | **1.17 / 1 / 21t 3.3MB** | 0.67 / 0 / 44t 7.1MB | 0.0 min |
| Kleine Scheidegg | 1.17 / 1 / 40t 6.2MB | 1.17 / 1 / 120t 16.5MB | 2.67 / 0 / 28t 4.6MB | 0.83 / 2 / 41t 6.3MB | 0.17 / 2 / 76t 11.6MB | **0.83 / 2 / 44t 6.9MB** | 0.33 / 2 / 66t 10.3MB | 0.17 min (1 day) |
| Viganella | 0.5 / 0 / 26t 4.0MB | 0.5 / 0 / 27t 4.1MB | 0.67 / 0 / 16t 2.6MB | 0.33 / 0 / 24t 3.6MB | 0.17 / 0 / 54t 7.9MB | **0.33 / 0 / 24t 3.6MB** | 0.17 / 0 / 41t 6.2MB | 0.0 min |
| Bristen | 8.33 / 0 / 19t 3.1MB | 8.33 / 0 / 19t 3.1MB | 8.33 / 0 / 19t 3.2MB | 8.33 / 0 / 20t 3.3MB | 0.17 / 0 / 48t 8.1MB | **0.33 / 0 / 19t 3.1MB** | 0.17 / 0 / 40t 6.7MB | **5.5 min** |
| Vicosoprano | 0.33 / 0 / 34t 5.3MB | 0.33 / 0 / 53t 8.1MB | 1.0 / 0 / 23t 3.9MB | 0.33 / 0 / 27t 4.3MB | 0.17 / 0 / 60t 9.5MB | **0.33 / 0 / 30t 4.9MB** | 0.5 / 0 / 52t 8.3MB | 0.17 min |
| Rattenberg | 6.0 / 0 / 64t 8.8MB | 2.17 / 0 / 192t 23.7MB | 0.67 / 0 / 34t 5.2MB | 0.67 / 0 / 54t 7.3MB | 0.17 / 0 / 102t 13.4MB | **0.5 / 0 / 53t 7.1MB** | 0.17 / 0 / 83t 10.9MB | 0.33 min |
| Rjukan | 1.17 / 0 / 63t 5.9MB | 1.17 / 0 / 71t 6.7MB | 0.67 / 0 / 25t 2.6MB | 0.17 / 0 / 44t 4.5MB | 0.0 / 0 / 88t 9.2MB | **0.17 / 0 / 42t 4.5MB** | 0.17 / 0 / 76t 8.0MB | 0.0 min |

Schedules (distance limit in km → zoom):
- `lod_coarse`: 1 → 13, 4 → 12, 15 → 11, 60 → 10, 150 → 9
- `lod_mid`: 2 → 13, 8 → 12, 30 → 11, 150 → 10
- `lod_fine`: 2 → 14, 6 → 13, 20 → 12, 60 → 11, 150 → 10
- `near14`: 1.5 → 14, 5 → 13, 15 → 12, 40 → 11, 150 → 10
- `near14_cheap`: 1.5 → 14, 6 → 12, 25 → 11, 150 → 10

The period-count differences at Kleine Scheidegg and Mürren are grazing features (a sliver of
sun lasting well under a minute). The z15 check shows one such difference too, so they are below
the resolution of any practical schedule.

### Azimuth resolution (`lod_mid`; max error min / days with a different number of periods)

| Observer | 0.1° | 0.25° | 0.5° | 1.0° |
|----------|------|-------|------|------|
| Interlaken | 0.33 / 0 | 0.5 / 0 | 0.83 / 0 | 1.67 / 0 |
| Mürren | 1.17 / 2 | 1.0 / 1 | 2.17 / 1 | 3.33 / 2 |
| Vicosoprano | 0.33 / 0 | 0.33 / 0 | 0.5 / 0 | 1.17 / 0 |
| Rjukan | 0.5 / 0 | 0.17 / 0 | 0.33 / 1 | 0.83 / 1 |

At 0.25° the azimuth error stays below the zoom error. Coarser bins add up to 2–3 min.

### Work and data per location

| Observer | Reference samples | `lod_mid` @0.25° samples, all → needed | Tiles all → needed (MB) | Year-clamped @0.25° | Shadow rays 21 Dec / 21 Mar / 21 Jun |
|----------|-------------------|----------------------------------------|--------------------------|---------------------|----------------------------------------|
| Interlaken | 40.5 M | 9.3 M → 3.5 M | 141 → 44 (19.9 → 6.1) | 40t 5.6MB 2.7 M | 15t / 19t / 17t |
| Lauterbrunnen | 40.4 M | 9.3 M → 1.8 M | 152 → 20 (21.9 → 3.1) | 11t 1.8MB 1.0 M | 10t / 8t / 6t |
| Grindelwald | 40.5 M | 9.3 M → 2.5 M | 146 → 26 (21.1 → 3.8) | 21t 3.1MB 1.8 M | 9t / 12t / 14t |
| Mürren | 40.4 M | 9.3 M → 2.3 M | 149 → 24 (21.6 → 3.8) | 21t 3.4MB 1.6 M | 9t / 8t / 16t |
| Kleine Scheidegg | 40.4 M | 9.3 M → 2.6 M | 143 → 41 (20.8 → 6.3) | 32t 5.0MB 1.8 M | 10t / 6t / 20t |
| Viganella | 40.0 M | 9.2 M → 2.3 M | 142 → 24 (20.6 → 3.6) | 21t 3.2MB 1.7 M | 1t / 12t / 15t |
| Bristen | 40.6 M | 9.4 M → 2.0 M | 143 → 20 (21.7 → 3.3) | 16t 2.6MB 1.5 M | 1t / 13t / 11t |
| Vicosoprano | 40.3 M | 9.3 M → 2.3 M | 141 → 27 (20.7 → 4.3) | 22t 3.6MB 1.7 M | 5t / 13t / 10t |
| Rattenberg | 41.1 M | 9.5 M → 3.5 M | 144 → 54 (20.9 → 7.3) | 42t 6.0MB 2.6 M | 1t / 12t / 20t |
| Rjukan | 55.4 M | 12.8 M → 3.5 M | 254 → 44 (27.1 → 4.5) | 38t 3.9MB 2.8 M | 1t / 12t / 21t |

The year-clamped and shadow-ray columns use the `lod_mid` schedule. Sample counts are a proxy
only; Kotlin on a phone was not measured. At an assumed 20–50 M bilinear samples per second on
one core, 2–4 M samples would take 50–200 ms.

### Oracles: sunless seasons (winter 2025/26, reference schedule)

| Place (spot) | Computed | Documented | Source |
|--------------|----------|------------|--------|
| Viganella (church, 46.0519 N 8.1939 E) | 8 Nov – 2 Feb (87 d) | 11 Nov – 2 Feb (83 d); Candlemas marks the sun's return | [Wikipedia](https://en.wikipedia.org/wiki/Viganella), [Life in Italy](https://lifeinitaly.com/the-sun-shines-through-a-mirror-in-viganella/) |
| Bristen (hamlet point, 46.7694 N 8.6918 E) | 30 Oct – 11 Feb (105 d) | village centre: 30 Oct – 13 Feb | [SRF](https://www.srf.ch/meteo/meteo-stories/lange-zeit-ohne-sonne-das-sind-die-schattenloecher-der-schweiz) (computed by solartopo.com) |
| Bristen, 100 m south (`lod_mid`) | 9 Oct – 3 Mar (146 d) | village square: 10 Oct – early March | [SRF](https://www.srf.ch/meteo/meteo-stories/dunkle-wintermonate-wenn-die-sonne-monatelang-nicht-mehr-aufgeht) |
| Vicosoprano (village, 46.3507 N 9.6216 E) | 18 Nov – 23 Jan (67 d) | mid-November – end of January | [SRF](https://www.srf.ch/meteo/meteo-stories/dunkle-wintermonate-wenn-die-sonne-monatelang-nicht-mehr-aufgeht) |
| Rattenberg (centre, 47.4399 N 11.8941 E) | 28 Nov – 13 Jan (47 d); 100 m south: 31 Oct – 10 Feb | "November to February", parts of the town | [ORF](https://oe1.orf.at/artikel/202639/Ein-ganzes-Dorf-im-Schatten) |
| Rjukan (town, 59.8787 N 8.5942 E) | 29 Sep – 13 Mar (166 d) | late September – mid March | [Wikipedia](https://en.wikipedia.org/wiki/Rjukan) |

Spot sensitivity: moving 100 m N/S/E/W changes the season by −18 to +26 days at Viganella and
−26 to +41 days at Bristen. Oracles therefore need exact spots. The Viganella church and the
Bristen centre match to within 3 days. Coordinates are from OpenStreetMap (Nominatim).

## Implications for `add-terrain-horizon`

To be decided in the proposal; these are recommendations, not decisions.

1. **Algorithm:** a sector-ray horizon profile per location with distance-dependent zoom and exact
   early termination (optionally year-clamped). Sun periods of the selected date come from the
   profile at 10 s steps, with boundaries refined by bisection.
2. **Schedule:** `near14_cheap` at 0.25°. Use the finest published zoom where z14 is missing.
   Radius 150 km with early termination.
3. **Data volume:** 3–7 MB (18–53 tiles) per new location instead of the ~1 tile of point
   elevation. The in-memory 8-tile LRU of #3 is too small, since a profile needs all its tiles
   at once. Options: stream tiles per distance ring, or store heights more compactly
   (Terrarium has 1/256 m resolution, so a `ShortArray` in 0.25 m steps halves memory).
4. **Accuracy target:** within 1 min of the reference is realistic in open terrain. At the foot
   of cliffs, results are resolution-sensitive (Bristen: 5.5 min between z14 and z15) and very
   sensitive to the exact spot. The spec tolerance should say so.
5. **Sun model:** continuous refraction below 0° removes the 3–5 min summit bias noted in the
   roadmap; the sun elevation from #2 currently has no refraction there. Visibility of the upper
   limb (+0.266°) matches the sunrise definition.
6. **Not needed for #4:** the equatorial transform and Fourier horizon maps. They are candidates
   for #7 (heatmap) and the overlay (#5).

## Limitations

- The reference is itself a DEM (Mapterhorn at z14–z10). It is converged to within 1 min except
  at cliff bases.
- No observed first-sunshine times with minute precision were found. The oracles are seasonal
  (days).
- Trees and buildings are not modelled (terrain model only). In a forest, a sunny result can
  still mean shade at ground level.
- Phone-side CPU time and memory were not measured; sample counts are a proxy.

## Scripts

Python 3.11 in the venv of `dem-source-evaluation.md` (plus `astral`). `dem.py` is listed there.
Run order: `exp.py`, `exp_extra.py`, `decl.py`, `sunray.py`, `yearclamp.py`, `season.py`.

### sun.py

```python
"""Vectorised NOAA sun position (azimuth from north clockwise, geometric elevation) and refraction."""
import numpy as np


def sun_position(unix_seconds, lat, lon):
    """unix_seconds: array; returns (azimuth deg, geometric elevation deg, declination deg, hour angle deg)."""
    jd = unix_seconds / 86400.0 + 2440587.5
    jc = (jd - 2451545.0) / 36525.0
    l0 = (280.46646 + jc * (36000.76983 + jc * 0.0003032)) % 360
    m = 357.52911 + jc * (35999.05029 - 0.0001537 * jc)
    e = 0.016708634 - jc * (0.000042037 + 0.0000001267 * jc)
    mr = np.radians(m)
    c = (np.sin(mr) * (1.914602 - jc * (0.004817 + 0.000014 * jc)) + np.sin(2 * mr) * (0.019993 - 0.000101 * jc)
         + np.sin(3 * mr) * 0.000289)
    true_long = l0 + c
    omega = 125.04 - 1934.136 * jc
    app_long = true_long - 0.00569 - 0.00478 * np.sin(np.radians(omega))
    mean_obl = 23 + (26 + ((21.448 - jc * (46.815 + jc * (0.00059 - jc * 0.001813)))) / 60) / 60
    obl = mean_obl + 0.00256 * np.cos(np.radians(omega))
    decl = np.degrees(np.arcsin(np.sin(np.radians(obl)) * np.sin(np.radians(app_long))))
    y = np.tan(np.radians(obl / 2)) ** 2
    l0r = np.radians(l0)
    eqtime = 4 * np.degrees(y * np.sin(2 * l0r) - 2 * e * np.sin(mr) + 4 * e * y * np.sin(mr) * np.cos(2 * l0r)
                            - 0.5 * y * y * np.sin(4 * l0r) - 1.25 * e * e * np.sin(2 * mr))
    minutes_utc = (unix_seconds % 86400) / 60.0
    true_solar = (minutes_utc + eqtime + 4 * lon) % 1440
    ha = true_solar / 4 - 180  # degrees, 0 at local solar noon
    latr, dr, har = np.radians(lat), np.radians(decl), np.radians(ha)
    cos_zen = np.sin(latr) * np.sin(dr) + np.cos(latr) * np.cos(dr) * np.cos(har)
    elev = np.degrees(np.arcsin(np.clip(cos_zen, -1, 1)))
    az = (np.degrees(np.arctan2(np.sin(har), np.cos(har) * np.sin(latr) - np.tan(dr) * np.cos(latr))) + 180) % 360
    return az, elev, decl, ha


def refraction(geometric_elev):
    """Saemundsson/Bennett-style refraction in degrees, continuous also below 0 deg (standard atmosphere)."""
    h = np.maximum(geometric_elev, -1.9)
    return 1.02 / np.tan(np.radians(h + 10.3 / (h + 5.11))) / 60.0
```

### hz.py

```python
"""Horizon spike engine: vectorised ray marching over Mapterhorn Terrarium tiles with distance-dependent zoom."""
import math
import os

import numpy as np

import dem

R = 6371000.0
K = 0.13  # refraction coefficient of the terrain line of sight
TILE = 512
H_MAX_ALPS = 4810.0  # conservative bound for early termination (Mont Blanc 4806 m)


class TileStore:
    """Decoded Mapterhorn tiles (float32 heights), fetched through dem.fetch's disk cache; counts use.
    Keeps at most MAX_TILES decoded tiles in memory (least recently used out)."""

    MAX_TILES = 400

    def __init__(self):
        self.tiles = {}
        self.used = set()
        self.missing = set()

    def get(self, z, x, y):
        key = (z, x, y)
        self.used.add(key)
        if key in self.tiles:
            self.tiles[key] = self.tiles.pop(key)  # most recently used last
        else:
            while len(self.tiles) >= self.MAX_TILES:
                self.tiles.pop(next(iter(self.tiles)))
            path = dem.fetch(f"https://tiles.mapterhorn.com/{z}/{x}/{y}.webp", f"mapterhorn/{z}/{x}/{y}.webp")
            if path is None:  # zoom not published here (source coarser): upsample the parent quadrant
                self.missing.add(key)
                self.tiles[key] = self._from_parent(z, x, y)
            else:
                self.tiles[key] = dem.xyz_tile("mth", z, x, y).astype(np.float32)
        return self.tiles[key]

    def _from_parent(self, z, x, y):
        parent = self.get(z - 1, x // 2, y // 2)
        c = (np.arange(TILE) + 0.5) / 2 - 0.5  # child pixel centres in parent pixel units
        cx, cy = c + (x % 2) * TILE / 2, c + (y % 2) * TILE / 2
        x0 = np.clip(np.floor(cx).astype(int), 0, TILE - 2); y0 = np.clip(np.floor(cy).astype(int), 0, TILE - 2)
        fx = np.clip(cx - x0, 0, 1)[None, :]; fy = np.clip(cy - y0, 0, 1)[:, None]
        p = parent
        top = p[np.ix_(y0, x0)] * (1 - fx) + p[np.ix_(y0, x0 + 1)] * fx
        bot = p[np.ix_(y0 + 1, x0)] * (1 - fx) + p[np.ix_(y0 + 1, x0 + 1)] * fx
        return (top * (1 - fy) + bot * fy).astype(np.float32)

    def bytes_used(self, keys=None):
        total = 0
        for (z, x, y) in (keys if keys is not None else self.used):
            p = os.path.join(dem.CACHE, "mapterhorn", str(z), str(x), f"{y}.webp")
            if os.path.exists(p):
                total += os.path.getsize(p)
        return total


STORE = TileStore()


def merc_pixels(lat, lon, z):
    n = (2 ** z) * TILE
    x = (lon + 180.0) / 360.0 * n - 0.5
    lr = np.radians(lat)
    y = (1.0 - np.log(np.tan(lr) + 1.0 / np.cos(lr)) / math.pi) / 2.0 * n - 0.5
    return x, y


def sample(lat, lon, z, store=STORE):
    """Bilinear heights at arrays lat/lon from zoom z (pixel centres)."""
    shape = lat.shape
    lat, lon = lat.ravel(), lon.ravel()
    x, y = merc_pixels(lat, lon, z)
    x0 = np.floor(x).astype(np.int64)
    y0 = np.floor(y).astype(np.int64)
    fx, fy = x - x0, y - y0
    out = np.zeros(lat.shape, dtype=np.float64)
    for dx, dy, w in ((0, 0, (1 - fx) * (1 - fy)), (1, 0, fx * (1 - fy)), (0, 1, (1 - fx) * fy), (1, 1, fx * fy)):
        gx, gy = x0 + dx, y0 + dy
        tx, ty = gx // TILE, gy // TILE
        key = tx * 1_000_000 + ty
        uniq, inv = np.unique(key, return_inverse=True)
        vals = np.empty(lat.shape, dtype=np.float64)
        order = np.argsort(inv, kind="stable")
        bounds = np.searchsorted(inv[order], np.arange(len(uniq) + 1))
        for i, k in enumerate(uniq):
            idx = order[bounds[i]:bounds[i + 1]]
            t = store.get(z, int(k // 1_000_000), int(k % 1_000_000))
            vals[idx] = t[gy[idx] % TILE, gx[idx] % TILE]
        out += w * vals
    return out.reshape(shape)


def pixel_size(z, lat):
    return 40075016.686 * math.cos(math.radians(lat)) / (2 ** z * TILE)


def schedule_zoom(schedule, d):
    """schedule: list of (max_distance_m, zoom); returns zoom per distance array (-1 beyond)."""
    z = np.full(d.shape, -1, dtype=int)
    lo = 0.0
    for dmax, zz in schedule:
        z[(d >= lo) & (d < dmax)] = zz
        lo = dmax
    return z


def distances(schedule, lat, step_px=0.5, d0=5.0):
    """Sample distances: step = step_px * pixel size of the zoom used there."""
    out, d, lo = [], d0, 0.0
    for dmax, z in schedule:
        step = step_px * pixel_size(z, lat)
        d = max(d, lo)
        n = int(math.ceil((dmax - d) / step))
        out.append(d + step * np.arange(n))
        d, lo = d + step * n, dmax
    return np.concatenate(out)


def destination(lat, lon, az_deg, d):
    """Great-circle destination points: az (A,), d (D,) -> lat, lon (A, D)."""
    la1, lo1 = math.radians(lat), math.radians(lon)
    az = np.radians(az_deg)[:, None]
    ang = (d / R)[None, :]
    la2 = np.arcsin(math.sin(la1) * np.cos(ang) + math.cos(la1) * np.sin(ang) * np.cos(az))
    lo2 = lo1 + np.arctan2(np.sin(az) * np.sin(ang) * math.cos(la1), np.cos(ang) - math.sin(la1) * np.sin(la2))
    return np.degrees(la2), np.degrees(lo2)


def horizon(lat, lon, azimuths, schedule, obs_height=1.7, step_px=0.5, ground_zoom=None, store=STORE,
            chunk=90, h_max=H_MAX_ALPS):
    """Horizon angle (deg) per azimuth. Returns (angles, stats)."""
    gz = ground_zoom if ground_zoom is not None else schedule[0][1]
    ground = sample(np.array([lat]), np.array([lon]), gz, store)[0]
    h_obs = ground + obs_height
    d = distances(schedule, lat, step_px)
    zooms = schedule_zoom(schedule, d)
    drop = d * d / (2 * R) * (1 - K)
    angles = np.full(len(azimuths), -90.0)
    needed = 0  # samples needed with exact early termination
    keys_full, keys_needed = set(), set()
    for s in range(0, len(azimuths), chunk):
        az = azimuths[s:s + chunk]
        la, lo = destination(lat, lon, az, d)
        h = np.empty(la.shape)
        for z in np.unique(zooms):
            m = zooms == z
            h[:, m] = sample(la[:, m], lo[:, m], int(z), store)
        ang = np.degrees(np.arctan2(h - drop[None, :] - h_obs, d[None, :]))
        run = np.maximum.accumulate(ang, axis=1)
        angles[s:s + chunk] = run[:, -1]
        # early termination: stop at first sample where no terrain beyond can exceed the running max
        bound = np.degrees(np.arctan2(h_max - drop - h_obs, d))  # max possible angle at distance d
        # bound is decreasing for d beyond a few km; stopping index = first i with max(bound[i:]) < run[i]
        tail_max = np.maximum.accumulate(bound[::-1])[::-1]
        stop = np.argmax(tail_max[None, :] < run, axis=1)
        stop[(tail_max[None, :] < run).sum(axis=1) == 0] = len(d)
        needed += int(stop.sum())
        keep = np.arange(len(d))[None, :] < stop[:, None]
        for z in np.unique(zooms):
            m = zooms == z
            x, y = merc_pixels(la[:, m], lo[:, m], int(z))
            tx = np.floor(x).astype(np.int64) // TILE
            ty = np.floor(y).astype(np.int64) // TILE
            k = set(zip([int(z)] * tx.size, tx.ravel().tolist(), ty.ravel().tolist()))
            keys_full |= k
            km = keep[:, m]
            keys_needed |= set(zip([int(z)] * int(km.sum()), tx[km].tolist(), ty[km].tolist()))
    stats = {"samples_full": len(d) * len(azimuths), "samples_needed": needed, "ground": ground,
             "per_ray": len(d), "tiles_full": keys_full, "tiles_needed": keys_needed}
    return angles, stats
```

### exp.py

```python
"""Sun-period experiment: reference horizon vs cheaper variants; errors in minutes, tiles, samples."""
import datetime as dt
import json
import os
import sys
import time

import numpy as np

import hz
import sun

OBSERVERS = {
    "Interlaken": (46.6863, 7.8632), "Lauterbrunnen": (46.5935, 7.9091), "Grindelwald": (46.6242, 8.0414),
    "Muerren": (46.5590, 7.8920), "KleineScheidegg": (46.5853, 7.9610),
    "Viganella": (46.05187, 8.19395), "Bristen": (46.76943, 8.69181), "Vicosoprano": (46.35074, 9.62165),
    "Rattenberg": (47.43988, 11.89411), "Rjukan": (59.8787, 8.5942),
}
REF = [(3000, 14), (10000, 13), (30000, 12), (80000, 11), (150000, 10)]
VARIANTS = {
    "flat_z12_40km": [(40000, 12)],
    "flat_z12_150km": [(150000, 12)],
    "lod_fine": [(2000, 14), (6000, 13), (20000, 12), (60000, 11), (150000, 10)],
    "lod_mid": [(2000, 13), (8000, 12), (30000, 11), (150000, 10)],
    "lod_coarse": [(1000, 13), (4000, 12), (15000, 11), (60000, 10), (150000, 9)],
}
AZ_MIN, AZ_MAX = 30.0, 330.0  # the sun never leaves this range at these latitudes (checked below)
UPPER_LIMB = 0.266  # deg; sunshine starts when the sun's upper edge clears the horizon
DATES = [dt.date(2025, m, 21) for m in range(1, 13)]
TZ_OFFSET = {"Rjukan": 1}  # all others CET too; periods are compared in UTC seconds anyway


def horizon_cached(name, schedule_name, schedule, az_step):
    fn = f"hzcache/{name}_{schedule_name}_{az_step}.npz"
    if os.path.exists(fn):
        f = np.load(fn, allow_pickle=True)
        return f["az"], f["ang"], f["stats"].item()
    lat, lon = OBSERVERS[name]
    az = np.arange(AZ_MIN, AZ_MAX + 1e-9, az_step)
    t = time.time()
    ang, st = hz.horizon(lat, lon, az, schedule, ground_zoom=14)
    st["seconds"] = time.time() - t
    st["tiles_full_n"] = len(st["tiles_full"])
    st["tiles_needed_n"] = len(st["tiles_needed"])
    st["bytes_full"] = hz.STORE.bytes_used(st["tiles_full"])
    st["bytes_needed"] = hz.STORE.bytes_used(st["tiles_needed"])
    st.pop("tiles_full"), st.pop("tiles_needed")
    os.makedirs("hzcache", exist_ok=True)
    np.savez(fn, az=az, ang=ang, stats=np.array(st, dtype=object))
    return az, ang, st


def periods(name, az, ang, date, step=10):
    """Sun periods (list of (start, end) unix seconds) on a UTC day window around local day."""
    lat, lon = OBSERVERS[name]
    t0 = dt.datetime(date.year, date.month, date.day, tzinfo=dt.timezone.utc).timestamp() - 3600
    t = t0 + np.arange(0, 86400, step)
    saz, sel, _, _ = sun.sun_position(t, lat, lon)
    app = sel + sun.refraction(sel) + UPPER_LIMB
    assert saz.min() >= AZ_MIN or app[saz < AZ_MIN].max() < 0, "sun above horizon outside azimuth range"
    hor = np.interp(saz, az, ang, left=90, right=90)
    vis = app > hor
    edges = np.flatnonzero(np.diff(vis.astype(int)))
    out, start = [], (t[0] if vis[0] else None)
    for e in edges:
        if vis[e + 1]:
            start = t[e + 1]
        else:
            out.append((start, t[e]))
    if vis[-1]:
        out.append((start, t[-1]))
    return out


def compare(ref_p, var_p):
    """Max boundary error (minutes) when the period structure matches, else mismatch flag."""
    if len(ref_p) != len(var_p):
        return None
    if not ref_p:
        return 0.0
    return max(max(abs(a[0] - b[0]), abs(a[1] - b[1])) for a, b in zip(ref_p, var_p)) / 60.0


if __name__ == "__main__":
    names = sys.argv[1:] or list(OBSERVERS)
    report = {}
    for name in names:
        az_r, ang_r, st_r = horizon_cached(name, "ref", REF, 0.1)
        ref_periods = {d: periods(name, az_r, ang_r, d) for d in DATES}
        rows = {"ref": {k: v for k, v in st_r.items() if k != "ground"} | {"ground": round(float(st_r["ground"]), 1)}}
        rows["ref"]["periods"] = {d.isoformat(): [(dt.datetime.fromtimestamp(a, dt.timezone.utc).strftime("%H:%M:%S"),
                                                   dt.datetime.fromtimestamp(b, dt.timezone.utc).strftime("%H:%M:%S"))
                                                  for a, b in p] for d, p in ref_periods.items()}
        for vname, sched in VARIANTS.items():
            for az_step in ([0.1, 0.25, 0.5, 1.0] if vname == "lod_mid" else [0.1]):
                az, ang, st = horizon_cached(name, vname, sched, az_step)
                errs = [compare(ref_periods[d], periods(name, az, ang, d)) for d in DATES]
                mism = sum(e is None for e in errs)
                good = [e for e in errs if e is not None]
                rows[f"{vname}@{az_step}"] = {
                    "max_err_min": round(max(good), 2) if good else None,
                    "mean_err_min": round(float(np.mean(good)), 2) if good else None,
                    "structure_mismatch_days": mism,
                    "tiles_full": st["tiles_full_n"], "tiles_needed": st["tiles_needed_n"],
                    "MB_full": round(st["bytes_full"] / 1e6, 1), "MB_needed": round(st["bytes_needed"] / 1e6, 1),
                    "samples_full": st["samples_full"], "samples_needed": st["samples_needed"],
                    "ground_minus_ref": round(float(st["ground"] - st_r["ground"]), 2),
                }
                print(name, vname, az_step, rows[f"{vname}@{az_step}"], flush=True)
        report[name] = rows
        json.dump(report, open(f"exp_report_{'_'.join(names)}.json", "w"), indent=1, default=str)
```

### exp_extra.py

```python
"""Extra variants: z14 near field with fast coarsening, and a z15 reference-convergence check."""
import json, sys
import numpy as np
import exp

EXTRA = {
    "near14": [(1500, 14), (5000, 13), (15000, 12), (40000, 11), (150000, 10)],
    "near14_cheap": [(1500, 14), (6000, 12), (25000, 11), (150000, 10)],
    "ref_z15": [(1000, 15)] + exp.REF,
}
out = {}
for name in sys.argv[1:]:
    az_r, ang_r, st_r = exp.horizon_cached(name, "ref", exp.REF, 0.1)
    ref_p = {d: exp.periods(name, az_r, ang_r, d) for d in exp.DATES}
    out[name] = {}
    for vname, sched in EXTRA.items():
        step = 0.1 if vname == "ref_z15" else 0.25
        az, ang, st = exp.horizon_cached(name, vname, sched, step)
        errs = [exp.compare(ref_p[d], exp.periods(name, az, ang, d)) for d in exp.DATES]
        good = [e for e in errs if e is not None]
        out[name][vname] = {"max_err_min": round(max(good), 2) if good else None,
                            "mismatch_days": sum(e is None for e in errs),
                            "tiles_needed": st["tiles_needed_n"], "MB_needed": round(st["bytes_needed"] / 1e6, 1),
                            "samples_needed": st["samples_needed"],
                            "max_horizon_diff_deg": round(float(np.max(np.abs(np.interp(az_r, az, ang) - ang_r))), 2)}
        print(name, vname, out[name][vname], flush=True)
json.dump(out, open("exp_extra_report.json", "w"), indent=1)
```

### decl.py

```python
"""Horizon in equatorial coordinates: minimum sun declination for visibility per hour angle.

For each hour angle H (0.05 deg grid) find delta_min(H): the sun at (H, delta) is visible iff
delta > delta_min(H), where visibility uses the same criterion as exp.periods (apparent upper limb
above the horizon). Checks the single-threshold assumption against direct periods for every day of
2025, and derives the sunless season from one number: min_H delta_min(H).
"""
import datetime as dt
import json
import sys

import numpy as np

import exp
import sun

DELTAS = np.arange(-23.6, 23.61, 0.01)


def alt_az(lat, H, dec):
    """H, dec in deg (broadcast) -> azimuth (from north, clockwise), geometric altitude."""
    la, h, d = np.radians(lat), np.radians(H), np.radians(dec)
    alt = np.degrees(np.arcsin(np.sin(la) * np.sin(d) + np.cos(la) * np.cos(d) * np.cos(h)))
    az = (np.degrees(np.arctan2(np.sin(h), np.cos(h) * np.sin(la) - np.tan(d) * np.cos(la))) + 180) % 360
    return az, alt


def visibility_grid(lat, az_h, ang_h, hours):
    az, alt = alt_az(lat, hours[:, None], DELTAS[None, :])
    app = alt + sun.refraction(alt) + exp.UPPER_LIMB
    hor = np.interp(az, az_h, ang_h, left=90, right=90)
    return app > hor  # (H, delta)


def analyse(name):
    lat, lon = exp.OBSERVERS[name]
    az_h, ang_h, _ = exp.horizon_cached(name, "ref", exp.REF, 0.1)
    hours = np.arange(-180, 180, 0.05)
    vis = visibility_grid(lat, az_h, ang_h, hours)
    # single threshold per H <=> each row is False...False True...True
    changes = np.abs(np.diff(vis.astype(int), axis=1)).sum(axis=1)
    multi = int((changes > 1).sum())
    first_true = np.where(vis.any(axis=1), vis.argmax(axis=1), len(DELTAS))
    dmin = np.where(first_true < len(DELTAS), DELTAS[np.minimum(first_true, len(DELTAS) - 1)], np.inf)
    dstar = float(dmin.min())
    # every day of 2025: periods from delta_min vs direct periods
    worst, mism, sunless = 0.0, 0, []
    for day in range(365):
        date = dt.date(2025, 1, 1) + dt.timedelta(days=day)
        t0 = dt.datetime(date.year, date.month, date.day, tzinfo=dt.timezone.utc).timestamp() - 3600
        t = t0 + np.arange(0, 86400, 10)
        _, _, dec, ha = sun.sun_position(t, lat, lon)
        vis_d = dec > np.interp(((ha + 180) % 360) - 180, hours, dmin)
        direct = exp.periods(name, az_h, ang_h, date)
        vis_direct = np.zeros_like(vis_d)
        for a, b in direct:
            vis_direct |= (t >= a) & (t <= b)
        diff = int((vis_d != vis_direct).sum())
        worst = max(worst, diff * 10 / 60)
        if not direct:
            sunless.append(date.isoformat())
    return {"rows_with_multiple_crossings": multi, "delta_star": round(dstar, 2),
            "max_daily_disagreement_min": round(worst, 2),
            "sunless_days": len(sunless), "first_sunless": sunless[0] if sunless else None,
            "last_sunless": sunless[-1] if sunless else None,
            "sunless_list_edges": sunless[:3] + sunless[-3:]}


if __name__ == "__main__":
    out = {}
    for n in sys.argv[1:] or list(exp.OBSERVERS):
        out[n] = analyse(n)
        print(n, out[n], flush=True)
    json.dump(out, open("decl_report.json", "w"), indent=1)
```

### sunray.py

```python
"""Tiles/samples needed when only the sun's path of one date matters (shadow-ray early exit).

A ray at azimuth A matters only for the sun elevation s(A) at the moment the sun is at A. It can stop at
the first sample that blocks (angle >= s) or once no terrain further out can reach s (bound < s).
Periods are exact under this exit rule as long as we answer 'blocked?' per time step, so only the data
cost is measured here. Sun path sampled every 60 s (azimuth steps < 0.25 deg at these latitudes).
"""
import datetime as dt
import json
import sys

import numpy as np

import exp
import hz
import sun

SCHED = exp.VARIANTS["lod_mid"]


def analyse(name, date):
    lat, lon = exp.OBSERVERS[name]
    t0 = dt.datetime(date.year, date.month, date.day, tzinfo=dt.timezone.utc).timestamp() - 3600
    t = t0 + np.arange(0, 86400, 60)
    saz, sel, _, _ = sun.sun_position(t, lat, lon)
    app = sel + sun.refraction(sel) + exp.UPPER_LIMB
    up = app > -2.0  # candidate times (horizon can be below 0 deg on summits)
    az, s_alt = saz[up], app[up]
    ground = hz.sample(np.array([lat]), np.array([lon]), 14)[0]
    h_obs = ground + 1.7
    d = hz.distances(SCHED, lat)
    zooms = hz.schedule_zoom(SCHED, d)
    drop = d * d / (2 * hz.R) * (1 - hz.K)
    bound = np.degrees(np.arctan2(hz.H_MAX_ALPS - drop - h_obs, d))
    tail = np.maximum.accumulate(bound[::-1])[::-1]
    keys, samples = set(), 0
    for s in range(0, len(az), 60):
        a, sa = az[s:s + 60], s_alt[s:s + 60]
        la, lo = hz.destination(lat, lon, a, d)
        h = np.empty(la.shape)
        for z in np.unique(zooms):
            m = zooms == z
            h[:, m] = hz.sample(la[:, m], lo[:, m], int(z))
        ang = np.degrees(np.arctan2(h - drop[None, :] - h_obs, d[None, :]))
        stop_blocked = np.where((ang >= sa[:, None]).any(axis=1), (ang >= sa[:, None]).argmax(axis=1), len(d))
        clear = tail[None, :] < sa[:, None]
        stop_clear = np.where(clear.any(axis=1), clear.argmax(axis=1), len(d))
        stop = np.minimum(stop_blocked, stop_clear) + 1
        samples += int(stop.sum())
        keep = np.arange(len(d))[None, :] < stop[:, None]
        for z in np.unique(zooms):
            m = zooms == z
            x, y = hz.merc_pixels(la[:, m], lo[:, m], int(z))
            km = keep[:, m]
            tx = (np.floor(x).astype(np.int64) // hz.TILE)[km]
            ty = (np.floor(y).astype(np.int64) // hz.TILE)[km]
            keys |= set(zip([int(z)] * len(tx), tx.tolist(), ty.tolist()))
    return {"rays": int(len(az)), "tiles": len(keys), "MB": round(hz.STORE.bytes_used(keys) / 1e6, 1),
            "samples": samples}


if __name__ == "__main__":
    out = {}
    for name in sys.argv[1:] or list(exp.OBSERVERS):
        out[name] = {}
        for date in (dt.date(2025, 12, 21), dt.date(2025, 3, 21), dt.date(2025, 6, 21)):
            out[name][date.isoformat()] = analyse(name, date)
            print(name, date, out[name][date.isoformat()], flush=True)
    json.dump(out, open("sunray_report.json", "w"), indent=1)
```

### yearclamp.py

```python
"""Year-clamped horizon: rays stop once the answer cannot matter for any date (see sunray.py)."""
import datetime as dt, json, sys
import numpy as np
import exp, hz, sun

SCHED = exp.VARIANTS["lod_mid"]
AZ_STEP = 0.25


def sun_envelope(lat, lon, az_bins):
    t0 = dt.datetime(2025, 1, 1, tzinfo=dt.timezone.utc).timestamp()
    t = t0 + np.arange(0, 365 * 86400, 300)
    saz, sel, _, _ = sun.sun_position(t, lat, lon)
    app = sel + sun.refraction(sel) + exp.UPPER_LIMB
    idx = np.clip(np.round((saz - az_bins[0]) / AZ_STEP).astype(int), 0, len(az_bins) - 1)
    smin = np.full(len(az_bins), np.inf); smax = np.full(len(az_bins), -np.inf)
    np.minimum.at(smin, idx, app); np.maximum.at(smax, idx, app)
    return smin, smax


def analyse(name):
    lat, lon = exp.OBSERVERS[name]
    az = np.arange(exp.AZ_MIN, exp.AZ_MAX + 1e-9, AZ_STEP)
    smin, smax = sun_envelope(lat, lon, az)
    live = np.isfinite(smin) & (smax > -2)
    az, smin, smax = az[live], np.maximum(smin[live], -2), smax[live]
    ground = hz.sample(np.array([lat]), np.array([lon]), 14)[0]
    h_obs = ground + 1.7
    d = hz.distances(SCHED, lat); zooms = hz.schedule_zoom(SCHED, d)
    drop = d * d / (2 * hz.R) * (1 - hz.K)
    tail = np.maximum.accumulate(np.degrees(np.arctan2(hz.H_MAX_ALPS - drop - h_obs, d))[::-1])[::-1]
    keys, samples = set(), 0
    for s in range(0, len(az), 60):
        a, lo_, hi_ = az[s:s + 60], smin[s:s + 60], smax[s:s + 60]
        la, lo = hz.destination(lat, lon, a, d)
        h = np.empty(la.shape)
        for z in np.unique(zooms):
            m = zooms == z
            h[:, m] = hz.sample(la[:, m], lo[:, m], int(z))
        run = np.maximum.accumulate(np.degrees(np.arctan2(h - drop[None, :] - h_obs, d[None, :])), axis=1)
        blocked = run >= hi_[:, None]
        irrelevant = tail[None, :] < np.maximum(run, lo_[:, None])
        done = blocked | irrelevant
        stop = np.where(done.any(axis=1), done.argmax(axis=1), len(d)) + 1
        samples += int(stop.sum())
        keep = np.arange(len(d))[None, :] < stop[:, None]
        for z in np.unique(zooms):
            m = zooms == z
            x, y = hz.merc_pixels(la[:, m], lo[:, m], int(z)); km = keep[:, m]
            keys |= set(zip([int(z)] * int(km.sum()), (np.floor(x).astype(np.int64) // hz.TILE)[km].tolist(),
                            (np.floor(y).astype(np.int64) // hz.TILE)[km].tolist()))
    return {"rays": int(len(az)), "tiles": len(keys), "MB": round(hz.STORE.bytes_used(keys) / 1e6, 1), "samples": samples}


out = {}
for n in sys.argv[1:]:
    out[n] = analyse(n); print(n, out[n], flush=True)
json.dump(out, open("yearclamp_report.json", "w"), indent=1)
```

### season.py

```python
"""Sunless season edges (2025/26 winter) at the oracle spots, plus sensitivity to the exact spot (+-100 m)."""
import datetime as dt, json, sys
import numpy as np
import exp, hz

SCHED = exp.VARIANTS["lod_mid"]


def season(name, az, ang):
    days = [dt.date(2025, 7, 1) + dt.timedelta(days=i) for i in range(365)]
    sunless = [d for d in days if not exp.periods(name, az, ang, d)]
    if not sunless:
        return None
    return sunless[0].isoformat(), sunless[-1].isoformat(), len(sunless)


out = {}
for name in sys.argv[1:]:
    lat, lon = exp.OBSERVERS[name]
    az_r, ang_r, _ = exp.horizon_cached(name, "ref", exp.REF, 0.1)
    res = {"ref_spot": season(name, az_r, ang_r), "nearby_lod_mid": []}
    for dy, dx in [(100, 0), (-100, 0), (0, 100), (0, -100)]:
        la = lat + dy / 111320
        lo = lon + dx / (111320 * np.cos(np.radians(lat)))
        exp.OBSERVERS["_tmp"] = (la, lo)
        az = np.arange(exp.AZ_MIN, exp.AZ_MAX + 1e-9, 0.25)
        ang, _ = hz.horizon(la, lo, az, SCHED, ground_zoom=14)
        res["nearby_lod_mid"].append({"offset_m": (dy, dx), "season": season("_tmp", az, ang)})
    out[name] = res
    print(name, res, flush=True)
json.dump(out, open("season_report.json", "w"), indent=1)
```
