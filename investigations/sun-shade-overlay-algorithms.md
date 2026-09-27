# Evaluation: sun and shade for the whole visible area

Spike for roadmap #5 `add-sun-shade-overlay` (and, in passing, #7 `add-sun-exposure-heatmap`),
run before its proposal. Question: how can the app decide sun/shade for **every point of the
visible map**, not just the crosshair, accurately and cheaply? The spike surveys the literature,
works out the math of several approaches (including "project a horizon onto nearby points"),
and measures them on real Mapterhorn data against the point tracer of `add-terrain-horizon`.
Produced 2026-09-27; scripts in the appendix, raw outputs were not committed.

## Summary

- **The horizon-profile idea scales to every point, if it is computed in the other loop
  order.** #4 computes one point's horizon for all azimuths (point-major). A *directional sweep*
  computes the horizon in one azimuth for all points at once (azimuth-major). The data is the
  same, H(p, A). Along each line parallel to the sun direction, an upper convex hull of the terrain
  profile gives the exact horizon angle of every sample in amortised O(1) (Dozier et al. 1981;
  Timonen & Westerholm 2010). Every terrain sample is shared by all observers downwind of it.
  That is what makes it about 10^4 times cheaper than per-point rays.
- **Earth curvature is exact in the sweep, at no cost.** With g = h − c·s² (c = (1−k)/2R, s =
  position along the line), the spec's curved-earth elevation tangent becomes a flat-earth slope
  in (s, g) minus 2c·s_p. The hull runs on g unchanged (derivation below; checked to 1e-13).
- **Accuracy vs the spec point tracer** (Lauterbrunnen, 6 × 10 km viewport, 349k cells of 13 m,
  5 instants):

  | Sweep variant | Median / p95 of \|ΔH\| | Cells with another sun state |
  |---------------|-------------------------|------------------------------|
  | Samples along lines at z14 (the spec's near-field resolution) | 0.005–0.009° / 0.09–0.12° | **0.00–0.13 %** |
  | Samples at z12 only | – | 0.7–2.1 %, a resolution effect |

- **Cost of one instant:**
  - numba: 28–110 ms with z12 lines and 110–200 ms with z14 lines (350k cells, one core).
  - Desktop JVM, one thread: 73–260 ms (z12 lines) and 310–530 ms (z14 lines). With 3 threads:
    29–100 ms and 117–202 ms.
  - The terrain upwind of the viewport dominates, up to 150 km at low sun.
  - Two exact or near-exact cuts help:
    - the sun-elevation bound cuts the upwind length to 12–64 km for daytime sun;
    - far-field bundling (below) shares far hulls between neighbouring lines, for 2.5–3× fewer
      samples at ≤ 0.05° p99.
- **Skyline projection (idea from the explore session), measured.** A block centre's far skyline is projected onto nearby
  observers, and each observer adds its own exact near field.
  - It is accurate: with a 5 km near field and observers up to 400 m from the centre, boundary
    errors are p90 ≤ 0.3 min and max ≤ 4.7 min (E4). It breaks down beyond r/D ≈ 0.08.
  - As an all-cell method it is not competitive, because the per-cell near field costs about
    100× a sweep.
  - Its natural home is inside the sweep as **far-field bundling**, the same idea with the same
    error bound, where it saves 60 % of the work.
- **Per-cell sun periods for a whole day** (#7's data, and instant time scrubbing) need one sweep
  per azimuth step. At 1° that is 117 sweeps (21 Dec) to 259 (21 Jun).
  - Against the point truth, the per-cell result disagrees by p90 6.7 min/day (Dec) and 16 min/day
    (Jun) with z14 lines.
  - That is **inside the resolution limit**: moving the point by half a cell (6.5 m) already
    changes the point truth by p90 14 min/day (Dec) and 25 min/day (Jun).
- **Unknown data is handled exactly.** Per line, the nearest missing sample upwind gives a bound.
  With the 4810 m height bound, the cell is complete if its horizon already exceeds what the gap
  could hide, otherwise incomplete with a lower bound. This is the same rule as the point tracer.
- **Phone timings were not measured.** The JVM numbers are a desktop proxy.

## The problem in numbers

- Viewport at map zoom z (MapLibre, 512 px tiles) ≈ one Mapterhorn pixel of zoom z per dp.
- A 400 × 850 dp screen therefore has **~350k cells at any zoom**: 13 m cells over 5 × 11 km at
  z12, 52 m cells over 21 × 44 km at z10.
- The terrain that can shade the viewport extends upwind by
  d_max(e) = (−tan e + sqrt(tan²e + 4c(H_max − z_min))) / 2c:

  | Sun elevation | 1° | 2° | 3° | 5° | 8° | 12° | 20° | 30° | 45° |
  |---------------|----|----|----|----|----|-----|-----|-----|-----|
  | d_max (km) | 152 | 102 | 74 | 47 | 30 | 20 | 12 | 7.4 | 4.2 |

  (H_max = 4810 m, z_min = 560 m.) Anything that ignores terrain outside the viewport (ShadeMap)
  is wrong at low sun.

## Approaches

Cost per sun position (instant), N ≈ 350k cells, L = upwind samples per line.

| # | Approach | Idea | Cost / instant | Exact? | Verdict |
|---|----------|------|----------------|--------|---------|
| A | Per-point horizon profiles (#4 per cell) | 1440 rays × 2–4 M samples per cell | ~10¹² | yes | infeasible |
| B | Per-cell shadow ray towards the sun (ShadeMap on CPU) | march from each cell to the sun | N × L ≈ 10⁸–10⁹ | yes | 10–100× a sweep |
| B' | B with max-mipmaps (Tevs 2008) | skip empty space with our own max pyramid | ~N × log L | yes | complex, still > sweep |
| C | Shadow-height recursion along lines | running "shadow top" S ← max(S − Δs·tan e, h) | O(N + L) | flat earth only; lit/unlit only | superseded by D |
| **D** | **Convex-hull sweep** (Dozier 1981, Timonen 2010) | per line an upper hull; tangent query per sample | **O(N + L·lines)** | **yes, incl. curvature** | **recommended core** |
| D+ | D with far-field bundling | far hulls shared by m neighbouring lines | 2.5–3× fewer samples | error ≤ lateral offset / d | recommended |
| E | Skyline projection | far skyline of a block centre projected onto nearby cells + exact near field per cell | per cell ~10⁵–10⁶ (near field) | lower bound; error bound derivable | accurate but ~100× D; lives on as D+ |
| F | Stewart 1998 rotational sweep | horizon in s sectors at all points | ~s·N·log N | sector max | subsumed by D per direction |
| G | Horizon maps (per-cell H stored, Fourier compressed; Max 1988, HPG 2025) | store H(p, A) per cell | storage | lossy | representation only; needs D to build |
| H | Equatorial form δ_min(p, h) | horizon in hour angle / declination; any date = one comparison | build from D | same as D | nice for "sunless season" maps; post-1.0 |
| I | Hour-angle-plane sweep (new) | one sweep in tilted planes through the polar axis gives δ_min for all dates at one hour angle | O(N) per hour angle | yes | traces are curves, not lines; no gain over D + transform |
| J | GPU shadow map (ortho depth render from the sun) | rasterise terrain mesh | O(N) on GPU | aliasing / acne | GL plumbing in app, not testable in core |
| K | GPU per-pixel ray march (ShadeMap) | fragment shader marches heightmap | GPU N × L | needs upwind texture | same as J |
| L | Server-side precomputed shade tiles | download per region | 0 on device | – | no backend in this project; offline size |
| M | Vector shadows (extrude ridge lines along the sun) | polygons | – | – | crisp at any zoom, fragile topology; can be derived from D by contouring |

## Math

### 1. Curved-earth horizon along a line is a flat hull problem

The spec's angle from observer p (eye e_p) to terrain j at distance d = s_p − s_j along a line:

    tan θ_pj = (h_j − e_p − c d²) / d,         c = (1 − k) / (2R) = 6.83e-8 1/m

Expand −c(s_p − s_j)² and set g_j = h_j − c s_j², G_p = e_p − c s_p²:

    h_j − e_p − c (s_p − s_j)² = (g_j − G_p) − 2c s_p (s_p − s_j)
    ⇒ tan θ_pj = (g_j − G_p) / (s_p − s_j) − 2c·s_p

The second term does not depend on j. So max_j tan θ_pj is the steepest slope from the point
(s_p, G_p) back to the points (s_j, g_j): the tangent to their upper convex hull, minus a constant.
The parabolic drop of the spec is therefore handled **exactly** by transforming heights once per
sample. Checked numerically: |direct − transformed| ≤ 8.5e-14 over 10⁵ random cases. Ignoring
curvature instead changes angles by up to 0.06–0.10° and flips 0–0.13 % of cells.

### 2. The sweep (per line, amortised O(1) per sample)

```
 upwind (sun side)                                          downwind
   s -->   j1      j2   j3        j4                p
           *.......*....*..........*................o eye (s_p, G_p)
            upper hull of (s_j, g_j)  <-- tangent from the eye = horizon of p
 for each sample p in order of s:
   pop hull vertices under the segment (top-1, p)       # insert the ground point
   walk from the top while the slope from the eye grows # eye tangent (0-1 steps: eye is 1.7 m up)
   tanH(p) = best slope - 2c s_p
   push (s_p, g_p)
```

The vertices popped for the ground point can never be the eye's tangent point: they lie under the
segment from the tangent point to p, and the eye is above p. Lines are straight in a gnomonic
projection centred on the viewport. Gnomonic straight lines are great circles, as in the spec
tracer; the measured direction mismatch at the observers is ≤ 0.03°.

### 3. Missing data

Let d_m be the distance from p to the nearest missing sample upwind on its line. Unseen terrain
can raise p's horizon to at most (H_max − e_p − c d²)/d for d ≥ d_m. For e_p < H_max this bound
falls with d, so its maximum is at d_m:

    complete(p)  ⇔  tanH(p) ≥ (H_max − e_p − c d_m²) / d_m

Otherwise tanH(p) is a lower bound. This is the spec's "Incomplete horizon" rule, evaluated per cell.

### 4. Projecting a horizon to nearby observers

Split p's horizon into near (d ≤ D) and far (d > D) terrain: H_p = max(H_p^near, H_p^far).
Take a block centre c and record, per azimuth bin, the 3-D skyline point q*(A) of its far
horizon. For an observer p within r of c:

    Ĥ_p(A) = max( H_p^near(A),  max over skyline points q projected from p into bin A of θ_pq )

- **Lower bound.** Every term is a real terrain angle seen from p, so Ĥ_p ≤ H_p (up to the
  interpolation between neighbouring skyline points). The approximation can only err towards sun.
- **Upper bound.** For a far point at distance d ≥ D, moving the observer horizontally by r and
  vertically by Δz changes its angle by at most

      β ≈ cos²θ · (|Δz| + r·tanθ) / D

  and its azimuth by at most α ≈ r / D. So p's far skyline lies within the c-skyline dilated by
  α in azimuth and β in angle. That gives a guaranteed interval
  [Ĥ, max(H^near, dilate(H_c^far) + β)]. Only cells whose sun lies inside the interval need an
  exact computation.

  | D | r | Δz | β | α |
  |---|---|----|---|---|
  | 2 km | 25 m | 10 m | 0.7° | 0.7° |
  | 2 km | 100 m | 50 m | 3.1° | 2.9° |
  | 5 km | 100 m | 50 m | 1.2° | 1.1° |
  | 10 km | 250 m | 50 m | 1.1° | 1.4° |

  The guaranteed bounds are loose. The measured errors are far smaller (table below).
- **Cost.** One far horizon per block, plus a near field per cell of 1440 rays × D/step
  (≈ 0.9 M samples at z14 for D = 1 km). For 350k cells that is ~3·10¹¹ samples, or ~5·10¹⁰ for
  1° steps over the sun's sector. That is 100–1000× the sweep, because the sweep shares each
  near-field sample among all observers downwind of it.
- **Where it does pay off:**
  1. **Inside the sweep, as far-field bundling (D+).** A far hull is computed once per bundle of m
     neighbouring lines and copied into each line. The error is that of projecting the far
     skyline across a lateral offset of ≤ m·cell/2, which must stay below the azimuth resolution:
     m·cell/2 ≤ d·tan(0.125°). This allows 2 lines at 6 km and 8 lines at 25 km.
  2. **Faster crosshair updates.** After a small pan, reuse the far skyline and recompute only the
     near field.

### 5. Equatorial form: one curve per cell for every date

A horizon point at azimuth A and altitude a maps to hour angle h and declination δ:

    sin δ = sin φ sin a + cos φ cos a cos A
    sin h = −sin A cos a / cos δ,   cos h = (sin a − sin φ sin δ) / (cos φ cos δ)

On any date the sun moves along δ = δ_sun, a horizontal line in the (h, δ) plane. The horizon
becomes one curve δ_min(p, h) per cell:
- sun at hour angle h on a date ⇔ δ_sun > δ_min(p, h);
- the **sunless season** of a cell is one number, δ*(p) = min_h δ_min(p, h).

This is exact up to the rare multi-crossing rows noted in `terrain-horizon-algorithms.md`. It
needs the same sweeps over the whole solar azimuth range (~250 at 1°), plus ~250 bytes per cell
if stored. That is too much to keep for a viewport, but it is a candidate for a post-1.0
"days without sun" map.

A novel variant, not prototyped: for a fixed hour angle, every sun direction of every date lies
in one plane containing the polar axis. Sweeping parallel planes of that orientation yields
δ_min(p, h) for all cells in O(N). But the planes cut the terrain along curves (level sets of a
sheared height field), not straight lines, so it is not simpler than D followed by the transform.

## Measurements

Viewport 6 km (E–W) × 10 km (N–S) centred on Lauterbrunnen (46.5935 N, 7.9091 E). z12 cells of
13.1 m give 349k observers. Mapterhorn tiles z14/z12/z11/z10: 291 tiles, 41 MB for everything
the experiments touched.

### E1: one instant, sweep vs spec rays at 3000 random observers (same azimuth)

| Instant | Sun | Shade | z12 sweep vs z12 rays: state diff | Curvature ignored: max ΔH / state diff | z12 vs spec: p95 ΔH / state diff | **z14-line sweep vs spec: p95 ΔH / state diff** |
|---------|-----|-------|------------------------------|-------------------------|-------------------------|--------------------------|
| 21 Dec 12:00 | 173.5° / 20.0° | 49 % | 0.33 % | 0.06° / 0.00 % | 5.2° / 2.00 % | 0.12° / 0.07 % |
| 21 Dec 15:00 | 215.6° / 12.2° | 74 % | 0.30 % | 0.07° / 0.03 % | 4.5° / 0.70 % | 0.10° / 0.10 % |
| 21 Dec 10:00 | 145.9° / 12.9° | 87 % | 0.20 % | 0.05° / 0.13 % | 4.2° / 0.50 % | 0.11° / 0.13 % |
| 21 Jun 07:30 | 73.4° / 17.1° | 50 % | 0.27 % | 0.10° / 0.03 % | 3.5° / 1.43 % | 0.09° / 0.00 % |
| 21 Mar 16:30 | 246.5° / 21.7° | 54 % | 0.20 % | 0.05° / 0.00 % | 4.1° / 1.17 % | 0.09° / 0.03 % |

The z12-sweep vs z12-ray differences come from where the nearest samples fall on steep slopes
(sweep: every 13 m on the line; rays: from 5 m, every 6.5 m), not from the algorithm.

### E2 / E5: time per instant (one core)

| Instant (upwind cut) | z12 lines, full 150 km | z12, cut | z12, cut + bundling 8/2 | z14 lines, cut | JVM z12 cut | JVM z14 cut |
|----------------------|------------------------|----------|-------------------------|----------------|-------------|-------------|
| 21 Dec 12:00 (12 km) | 4.2 M / 111 ms | 1.07 M / 28 ms | 0.96 M | 3.9 M / 112 ms | 73 ms | 315 ms |
| 21 Dec 15:00 (20 km) | 6.2 M / 160 ms | 1.94 M / 53 ms | 1.52 M | 4.9 M / 144 ms | 135 ms | 434 ms |
| 21 Jun 07:30 (14 km) | 6.5 M / 156 ms | 1.65 M / 41 ms | 1.39 M | 4.7 M / 136 ms | 104 ms | 318 ms |
| 21 Dec 16:15 (64 km) | 6.6 M / 165 ms | 3.73 M / 110 ms | 1.95 M | 6.8 M / 200 ms | 240 ms | 531 ms |

Samples / numba time. JVM: 65–88 ns per sample on this machine, a straight port without tuning.
With 3 JVM threads: 29–100 ms (z12 lines) and 117–202 ms (z14 lines); lines are independent, so
the work scales almost linearly with cores.
Bundling with the full 150 km (the complete horizon, valid for every date at that azimuth) takes
1.6–2.3 M samples instead of 4.2–6.6 M. Against unbundled: p99 0.03–0.05°, max 0.09–0.26°, and
≤ 0.014 % of cells with another state.

### E3: per-cell sun periods of a day (120 random cells) vs the spec point tracer

Metric: minutes per day in which the cell's sun state differs from the spec point profile at the
cell centre.

| Method | 21 Dec median / p90 / max | 21 Jun median / p90 / max |
|--------|---------------------------|---------------------------|
| Spec itself, 6.5 m away (resolution baseline) | 0.5 / 14.2 / 106 | 2.7 / 24.9 / 115 |
| Sweeps z12 lines, 1°, bilinear | 0.5 / 8.3 / 286 | 2.5 / 32.5 / 342 |
| Sweeps z14 lines, 1°, bilinear | 0.7 / 6.7 / 59 | 1.2 / 16.4 / 119 |
| Sweeps z14 lines, 0.5°, bilinear | 0.5 / 5.7 / 57 | 1.1 / 20.8 / 106 |

- Sweeps per day at 1°: 117 on 21 Dec and 259 on 21 Jun. That is 18–35 s in numba with z14 lines
  without bundling.
- Nearest-sample resampling, rather than bilinear, makes spurious short periods: 2–3× more cells
  with another number of periods.
- Period boundaries (same number of periods): median 0.3–0.8 min, p90 1.5–3 min with z14 lines.

### E4: skyline projection (block far skyline + exact near field), vs full spec profiles

96 observers per row: 8 block centres × 12 observers at distance r. Angle error = estimate − truth
over azimuths 50–310° (negative = too low, i.e. too sunny). Period boundary error in minutes over
cells with the same number of periods. "count" = cells (of 96) with another number of periods.

| D (near field) | r (distance from block centre) | Angle error p1 / p99 | 21 Dec count / p90 / max | 21 Mar | 21 Jun |
|----------------|-------------------------------|----------------------|--------------------------|--------|--------|
| 1 km | 50 m | −0.12° / 0.13° | 0 / 0.3 / 26.3 | 0 / 0.3 / 1.7 | 0 / 0.3 / 1.3 |
| 1 km | 150 m | −2.5° / 0.14° | 3 / 0.5 / 17.7 | 2 / 0.5 / 40.0 | 3 / 1.0 / 15.8 |
| 1 km | 400 m | −7.9° / 0.14° | 7 / 3.4 / 9.8 | 9 / 12.3 / 68.8 | 8 / 13.7 / 74.3 |
| 1 km | 1000 m | −27° / 0.11° | 30 / 64 / 136 | 24 / 25 / 54 | 14 / 73 / 146 |
| 2 km | 50 m | −0.12° / 0.11° | 0 / 0.3 / 0.8 | 1 / 0.2 / 1.7 | 3 / 0.3 / 3.2 |
| 2 km | 150 m | −0.21° / 0.12° | 2 / 0.6 / 1.5 | 0 / 0.2 / 1.2 | 3 / 0.2 / 1.0 |
| 2 km | 400 m | −2.4° / 0.12° | 1 / 1.5 / 12.2 | 3 / 0.5 / 13.8 | 6 / 2.5 / 43.3 |
| 2 km | 1000 m | −16° / 0.10° | 19 / 17 / 37 | 10 / 8.4 / 160 | 15 / 18 / 33 |
| **5 km** | **50 m** | −0.06° / 0.10° | 0 / 0.3 / 0.5 | 3 / 0.2 / 7.5 | 0 / 0.0 / 0.2 |
| **5 km** | **150 m** | −0.06° / 0.08° | 1 / 0.3 / 2.3 | 2 / 0.2 / 0.3 | 0 / 0.2 / 0.3 |
| **5 km** | **400 m** | −0.11° / 0.08° | 1 / 0.3 / 1.0 | 0 / 0.2 / 4.7 | 0 / 0.0 / 2.0 |
| 5 km | 1000 m | −1.6° / 0.09° | 10 / 1.0 / 20.0 | 1 / 0.2 / 9.3 | 4 / 3.8 / 20.2 |

Rule of thumb from the data: the projected far skyline is as good as the reference when
**r/D ≤ ~0.08** (e.g. 400 m blocks with a 5 km near field). Beyond that, a ridge that the centre
sees behind another one takes over for the observer (the "skyline switch"), and the lower-bound
error grows as expected from the bound β ≈ (|Δz| + r·tanθ)/D. The positive tail (p99 ≈ +0.1°) is
the linear interpolation between neighbouring skyline points.

## Implications for `add-sun-shade-overlay`

To be decided in the proposal; these are recommendations, not decisions.

1. **Algorithm:** convex-hull sweep along great-circle lines parallel to the sun direction, with
   the curvature transform, the upwind cut from the sun elevation, far-field bundling, and gap
   bounds for unknown data. It lives in `core`, pure and testable against the point tracer
   (`HorizonTracer`) as the oracle.
2. **Resolution:** samples along lines at the spec's near zoom (z14 capped, i.e. map zoom + 2)
   keep the overlay consistent with the panel (≤ 0.13 % of cells differ). z12 lines are 3–4×
   cheaper but differ in 0.5–2 % of cells, concentrated at cliffs. This is a decision for the proposal.
3. **Output:** a raster in the rotated sun frame, rendered as a MapLibre `ImageSource` whose
   `LatLngQuad` is the rotated rectangle. No resampling is needed for the instant overlay.
4. **Spec tolerance:** per cell, results are only as stable as the DEM within one cell (p90
   14–25 min/day at 13 m). The overlay spec should state agreement with the point tracer at the
   cells' sample points, not the "±5 min" of the point spec for arbitrary positions.
5. **Time slider:** one sweep per frame (tens to hundreds of ms), or a background day product
   (100–260 sweeps) that makes scrubbing free and is exactly #7's data.
6. **Data:** z14 along lines needs the z14 tiles of the whole viewport (54 tiles, 8.9 MB for this
   6 × 10 km viewport plus a 1.7 km margin, at map zoom 12) plus the upwind fan. This feeds into
   #6 (offline regions).
7. **Measure on a phone first:** the budgets above are desktop numbers.

## Limitations

- One test area (Lauterbrunnen: deep U-valley with cliffs, a hard case). Flat or open terrain
  will look better.
- The reference is the spec point tracer on Mapterhorn data, not observations.
- No phone measurements; JVM numbers come from an untuned port on a 4-core cloud VM, partly run
  while other experiments were running.
- GPU approaches (J, K) and the hour-angle-plane sweep (I) were analysed, not prototyped.

## Sources

- Dozier, Bruno, Downey (1981): A faster solution to the horizon problem, Computers &
  Geosciences 7(2). https://escholarship.org/uc/item/3g89m045
- Stewart (1998): Fast horizon computation at all points of a terrain with visibility and shading
  applications, IEEE TVCG 4(1). https://research.cs.queensu.ca/home/jstewart/papers/tvcg97.html
- Timonen, Westerholm (2010): Scalable height field self-shadowing, CGF 29(2).
  https://www.researchgate.net/publication/37910639_Scalable_Height_Field_Self-Shadowing
- Steger, Steger, Schär (2022): HORAYZON v1.2, GMD 15. https://gmd.copernicus.org/articles/15/6817/2022/
- Fast planetary shadows using Fourier-compressed horizon maps, HPG 2025.
  https://highperformancegraphics.org/untracked/2025/presentations/Pa5_2_Fast%20Planetary%20Shadows%20using%20Fourier-Compressed%20Horizon%20Maps.pdf
- Optimally fast soft shadows on curved terrain with dynamic programming and maximum mipmaps.
  https://arxiv.org/abs/2005.06671
- ShadeMap: https://shademap.app/about/ ; GRASS r.horizon: https://grass.osgeo.org/grass-stable/manuals/r.horizon.html

## Scripts

Python 3.11 with numpy, numba, pillow and requests; `sun.py` is listed in
`terrain-horizon-algorithms.md`. Run order: `setup.py` (fetches and caches the tiles on first use),
`exp_instant.py` (E1), `exp_speed.py` (E2), `exp_day.py` and `exp_day2.py` (E3), `exp_proj.py`
(E4), `exp_bundle.py` (E5). `SweepBench.java` times the sweep on the JVM; it reads the mosaics exported by:

```python
import numpy as np, setup as S
mos, ox, oy, oz = S.load()
with open("jvm/meta.txt", "w") as meta:
    for i, z in enumerate(S.ZOOMS):
        np.nan_to_num(mos[i], nan=-9999).astype(">f4").tofile(f"jvm/z{z}.bin")
        print(z, mos[i].shape[0], mos[i].shape[1], ox[i], oy[i], file=meta)
```

### tiles.py

```python
"""Mapterhorn Terrarium tiles: disk cache, decoding, and per-zoom mosaics covering a lat/lon box."""
import io
import math
import os
import time

import numpy as np
import requests
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
CACHE = os.path.join(HERE, "cache")
TILE = 512
SESSION = requests.Session()
SESSION.headers.update({"User-Agent": "sunshine-overlay-spike/0.1 (research; github.com/lukaspestalozzi/sunshine)"})
FETCHED = {"files": 0, "bytes": 0}


def fetch(z, x, y):
    """Path of the cached WebP, or None when the server has no such tile (404)."""
    path = os.path.join(CACHE, str(z), str(x), f"{y}.webp")
    miss = path + ".404"
    if os.path.exists(path):
        return path
    if os.path.exists(miss):
        return None
    os.makedirs(os.path.dirname(path), exist_ok=True)
    url = f"https://tiles.mapterhorn.com/{z}/{x}/{y}.webp"
    for attempt in range(5):
        r = SESSION.get(url, timeout=120)
        if r.status_code == 200:
            break
        if r.status_code == 404:
            open(miss, "w").close()
            return None
        time.sleep(2 ** attempt)
    r.raise_for_status()
    with open(path + ".part", "wb") as f:
        f.write(r.content)
    os.replace(path + ".part", path)
    FETCHED["files"] += 1
    FETCHED["bytes"] += len(r.content)
    time.sleep(0.05)
    return path


def decode(path):
    rgb = np.asarray(Image.open(path).convert("RGB"), dtype=np.float32)
    return rgb[:, :, 0] * 256.0 + rgb[:, :, 1] + rgb[:, :, 2] / 256.0 - 32768.0


def tile_xy(lat, lon, z):
    """Global pixel coordinates (pixel centres at integers) at zoom z."""
    n = (2 ** z) * TILE
    x = (lon + 180.0) / 360.0 * n - 0.5
    lr = math.radians(lat)
    y = (1.0 - math.log(math.tan(lr) + 1.0 / math.cos(lr)) / math.pi) / 2.0 * n - 0.5
    return x, y


class Mosaic:
    """All tiles of zoom z covering a lat/lon box, as one float32 array (NaN where no tile).
    Global pixel (gx, gy) is at array [gy - y0, gx - x0]."""

    def __init__(self, z, lat_min, lat_max, lon_min, lon_max):
        self.z = z
        gx0, gy1 = tile_xy(lat_min, lon_min, z)
        gx1, gy0 = tile_xy(lat_max, lon_max, z)
        tx0, tx1 = int(gx0 // TILE), int(gx1 // TILE)
        ty0, ty1 = int(gy0 // TILE), int(gy1 // TILE)
        self.x0, self.y0 = tx0 * TILE, ty0 * TILE
        self.a = np.full(((ty1 - ty0 + 1) * TILE, (tx1 - tx0 + 1) * TILE), np.nan, dtype=np.float32)
        self.tiles = 0
        self.bytes = 0
        for ty in range(ty0, ty1 + 1):
            for tx in range(tx0, tx1 + 1):
                p = fetch(z, tx, ty)
                if p is None:
                    continue
                self.tiles += 1
                self.bytes += os.path.getsize(p)
                self.a[(ty - ty0) * TILE:(ty - ty0 + 1) * TILE, (tx - tx0) * TILE:(tx - tx0 + 1) * TILE] = decode(p)

    def __repr__(self):
        return f"Mosaic(z{self.z}, {self.a.shape}, {self.tiles} tiles, {self.bytes / 1e6:.1f} MB)"
```

### setup.py

```python
"""Shared setup: viewport, mosaics (z14, z13, z12, z11, z10 -> index 0..4), sun helpers, band schedules."""
import math
import os
import pickle
from datetime import datetime, timezone, timedelta

import numpy as np

import sun
import tiles

HERE = os.path.dirname(os.path.abspath(__file__))
LAT0, LON0 = 46.5935, 7.9091  # Lauterbrunnen
HALF_EW, HALF_NS = 3000.0, 5000.0
ZOOMS = (14, 13, 12, 11, 10)
MARGINS = (1.7, 3.2, 6.2, 25.5, 152)


def box(lat0, lon0, mkm_ns, mkm_ew):
    dlat = mkm_ns / 111.2
    dlon = mkm_ew / (111.32 * math.cos(math.radians(lat0)))
    return lat0 - dlat, lat0 + dlat, lon0 - dlon, lon0 + dlon


def load(lat0=LAT0, lon0=LON0):
    pk = os.path.join(HERE, "cache", f"mos_{lat0}_{lon0}.pkl")
    if os.path.exists(pk):
        return pickle.load(open(pk, "rb"))
    ms = [tiles.Mosaic(z, *box(lat0, lon0, 5 + m, 3 + m)) for z, m in zip(ZOOMS, MARGINS)]
    mos = tuple(m.a for m in ms)
    ox = np.array([m.x0 for m in ms], dtype=np.int64)
    oy = np.array([m.y0 for m in ms], dtype=np.int64)
    oz = np.array(ZOOMS, dtype=np.int64)
    out = (mos, ox, oy, oz)
    pickle.dump(out, open(pk, "wb"), protocol=4)
    return out


def cell_m(z=12, lat=LAT0):
    return 2 * math.pi * 6371000.0 * math.cos(math.radians(lat)) / ((2 ** z) * 512)


# Spec schedule (point tracer): z14 to 1.5 km, z12 to 6 km, z11 to 25 km, z10 to 150 km.
SPEC_BANDS = (np.array([5.0, 1500.0, 6000.0, 25000.0]), np.array([1500.0, 6000.0, 25000.0, 150000.0]),
              np.array([0, 2, 3, 4], dtype=np.int64))
# Same without z14: z12 from 5 m.
Z12_BANDS = (np.array([5.0, 6000.0, 25000.0]), np.array([6000.0, 25000.0, 150000.0]),
             np.array([2, 3, 4], dtype=np.int64))


def far_bands(near_zi=2, near_end=6000.0):
    """Upwind bands for the sweep, distances before the line's first viewport sample."""
    st = [0.0, near_end, 25000.0]
    en = [near_end, 25000.0, 150000.0]
    zi = [near_zi, 3, 4]
    step = [0.5 * cell_m(ZOOMS[z]) for z in zi]
    return np.array(st), np.array(en), np.array(zi, dtype=np.int64), np.array(step)


def sun_at(local_iso, tz_hours, lat=LAT0, lon=LON0):
    """(azimuth, apparent upper-limb elevation) for a local wall time with a fixed UTC offset."""
    dt = datetime.fromisoformat(local_iso).replace(tzinfo=timezone(timedelta(hours=tz_hours)))
    u = np.array([dt.timestamp()])
    az, el, _, _ = sun.sun_position(u, lat, lon)
    el = float(el[0])
    app = el + (float(sun.refraction(np.array([el]))[0]) if el > 0 else 0.0)
    return float(az[0]), app + 0.266


def sun_track(unix, lat=LAT0, lon=LON0):
    az, el, decl, ha = sun.sun_position(unix, lat, lon)
    app = np.where(el > 0, el + sun.refraction(el), el) + 0.266
    return az, app, decl, ha
```

### kern.py

```python
"""Numba kernels: point rays (spec reference), directional hull sweep, and helpers.

Conventions: azimuth A in degrees from north clockwise, pointing TOWARDS the sun / the horizon.
Heights in metres. c = (1 - k) / (2 R) is the curvature drop coefficient of the spec.
"""
import math

import numpy as np
from numba import njit

R = 6371000.0
K = 0.13
C = (1.0 - K) / (2.0 * R)
TILE = 512
EYE = 1.7
HMAX = 4810.0


@njit(cache=True)
def merc_px(lat, lon, z):
    n = (2.0 ** z) * TILE
    x = (lon + 180.0) / 360.0 * n - 0.5
    s = math.sin(math.radians(lat))
    y = (1.0 - 0.5 * math.log((1.0 + s) / (1.0 - s)) / math.pi) / 2.0 * n - 0.5
    return x, y


@njit(cache=True)
def bilinear(a, x0, y0, gx, gy):
    fx0 = math.floor(gx)
    fy0 = math.floor(gy)
    c = int(fx0) - x0
    r = int(fy0) - y0
    if r < 0 or c < 0 or r + 1 >= a.shape[0] or c + 1 >= a.shape[1]:
        return np.nan
    fx = gx - fx0
    fy = gy - fy0
    n = a[r, c] + (a[r, c + 1] - a[r, c]) * fx
    s = a[r + 1, c] + (a[r + 1, c + 1] - a[r + 1, c]) * fx
    return n + (s - n) * fy


@njit(cache=True)
def height(mos, ox, oy, oz, zi, lat, lon):
    gx, gy = merc_px(lat, lon, oz[zi])
    return bilinear(mos[zi], ox[zi], oy[zi], gx, gy)


@njit(cache=True)
def gc_dest(lat, lon, az, d):
    p1 = math.radians(lat)
    t = math.radians(az)
    dr = d / R
    sp2 = math.sin(p1) * math.cos(dr) + math.cos(p1) * math.sin(dr) * math.cos(t)
    lon2 = math.radians(lon) + math.atan2(math.sin(t) * math.sin(dr) * math.cos(p1), math.cos(dr) - math.sin(p1) * sp2)
    return math.degrees(math.asin(sp2)), math.degrees(lon2)


@njit(cache=True)
def pixel_m(z, lat):
    return 2.0 * math.pi * R * math.cos(math.radians(lat)) / ((2.0 ** z) * TILE)


@njit(cache=True)
def ray(mos, ox, oy, oz, lat, lon, az, eye_h, b_start, b_end, b_zi, stop_tan):
    """Spec ray: max tan(elevation) along the great circle from (lat, lon) at azimuth az.
    Samples every half pixel of the band's zoom. Ends at 150 km, at exact height-bound termination,
    or once the max reaches stop_tan (pass +inf for the exact horizon).
    Returns (tanH, complete, d_at_max, h_at_max, lat_at_max, lon_at_max)."""
    best = -np.inf
    bd = 0.0
    bh = 0.0
    blat = 0.0
    blon = 0.0
    for b in range(b_start.shape[0]):
        zi = b_zi[b]
        step = 0.5 * pixel_m(oz[zi], lat)
        d = b_start[b]
        while d < b_end[b]:
            if (HMAX - eye_h - C * d * d) / d < best:
                return best, True, bd, bh, blat, blon
            la, lo = gc_dest(lat, lon, az, d)
            h = height(mos, ox, oy, oz, zi, la, lo)
            if np.isnan(h):
                return best, False, bd, bh, blat, blon
            t = (h - eye_h - C * d * d) / d
            if t > best:
                best = t
                bd = d
                bh = h
                blat = la
                blon = lo
                if best >= stop_tan:
                    return best, True, bd, bh, blat, blon
            d += step
    return best, True, bd, bh, blat, blon


@njit(cache=True)
def ground(mos, ox, oy, oz, zi, lat, lon):
    return height(mos, ox, oy, oz, zi, lat, lon) + EYE


# ---------------------------------------------------------------- gnomonic frame


@njit(cache=True)
def inv_gnomonic(x, y, lat0, lon0):
    rho = math.sqrt(x * x + y * y)
    if rho < 1e-9:
        return lat0, lon0
    cc = math.atan(rho / R)
    p0 = math.radians(lat0)
    lat = math.asin(math.cos(cc) * math.sin(p0) + y * math.sin(cc) * math.cos(p0) / rho)
    lon = math.radians(lon0) + math.atan2(x * math.sin(cc), rho * math.cos(p0) * math.cos(cc) - y * math.sin(p0) * math.sin(cc))
    return math.degrees(lat), math.degrees(lon)


@njit(cache=True)
def sweep(mos, ox, oy, oz, lat0, lon0, half_ew, half_ns, cell, az, far_len,
          vp_zi, vp_step, f_start, f_end, f_zi, f_step,
          out_tan, out_ok, out_lat, out_lon, out_h, flat):
    """Directional convex-hull sweep (Dozier 1981 / Timonen 2010) in a gnomonic frame centred at
    (lat0, lon0), where straight lines are great circles. Lines run towards azimuth az (the sun);
    s grows downwind (away from the sun). The viewport is |x| <= half_ew, |y| <= half_ns (metres).

    Curvature is exact for the spec's parabolic drop: with g = h - C s^2, the elevation tangent from
    observer p to occluder j is (g_j - G_p) / (s_p - s_j) - 2 C s_p, where G_p = g_p + EYE.
    flat=True drops the curvature (C = 0) to measure its effect.

    Upwind samples: bands [f_start, f_end) of distance before the line's first viewport sample,
    zoom index f_zi, step f_step. Viewport samples: every vp_step metres on zoom vp_zi; observers
    every `cell` metres (vp_step must divide cell).
    Outputs per line (row) and observer index (col): tan of horizon, completeness, position, ground.
    Returns number of samples processed."""
    cc = 0.0 if flat else C
    ta = math.radians(az)
    ux = math.sin(ta)
    uy = math.cos(ta)  # unit vector towards the sun
    # frame: s = -(x ux + y uy) (downwind), w = x uy - y ux
    corners_x = np.array([-half_ew, half_ew, half_ew, -half_ew])
    corners_y = np.array([-half_ns, -half_ns, half_ns, half_ns])
    wmin = 1e18
    wmax = -1e18
    for i in range(4):
        w = corners_x[i] * uy - corners_y[i] * ux
        wmin = min(wmin, w)
        wmax = max(wmax, w)
    nlines = out_tan.shape[0]
    nobs = out_tan.shape[1]
    hs = np.empty(200000)
    hg = np.empty(200000)
    total = 0
    sub = int(round(cell / vp_step))
    for li in range(nlines):
        w = wmin + (li + 0.5) * cell
        if w > wmax:
            break
        # s-interval of the line inside the viewport rectangle
        smin = -1e18
        smax = 1e18
        # x = -s ux + w uy ; y = -s uy - w ux
        for axis in range(2):
            if axis == 0:
                a = -ux
                b = w * uy
                lo = -half_ew
                hi = half_ew
            else:
                a = -uy
                b = -w * ux
                lo = -half_ns
                hi = half_ns
            if abs(a) < 1e-12:
                if b < lo or b > hi:
                    smin = 1e18
                continue
            s1 = (lo - b) / a
            s2 = (hi - b) / a
            smin = max(smin, min(s1, s2))
            smax = min(smax, max(s1, s2))
        if smin >= smax:
            continue
        n = 0
        last_gap = -1e18
        # upwind bands, farthest first
        for bi in range(f_start.shape[0] - 1, -1, -1):
            dd = f_end[bi]
            if dd > far_len:
                dd = far_len
            while dd > f_start[bi]:
                s = smin - dd
                x = -s * ux + w * uy
                y = -s * uy - w * ux
                la, lo_ = inv_gnomonic(x, y, lat0, lon0)
                h = height(mos, ox, oy, oz, f_zi[bi], la, lo_)
                total += 1
                if np.isnan(h):
                    last_gap = s
                else:
                    g = h - cc * s * s
                    while n >= 2 and (hg[n - 1] - hg[n - 2]) * (s - hs[n - 2]) <= (g - hg[n - 2]) * (hs[n - 1] - hs[n - 2]):
                        n -= 1
                    hs[n] = s
                    hg[n] = g
                    n += 1
                dd -= f_step[bi]
        # viewport samples
        k = 0
        s = smin
        oi = 0
        while s <= smax:
            x = -s * ux + w * uy
            y = -s * uy - w * ux
            la, lo_ = inv_gnomonic(x, y, lat0, lon0)
            h = height(mos, ox, oy, oz, vp_zi, la, lo_)
            total += 1
            if np.isnan(h):
                last_gap = s
                if k % sub == 0 and oi < nobs:
                    out_tan[li, oi] = np.nan
                    out_ok[li, oi] = False
                    out_lat[li, oi] = la
                    out_lon[li, oi] = lo_
                    out_h[li, oi] = np.nan
                    oi += 1
            else:
                g = h - cc * s * s
                while n >= 2 and (hg[n - 1] - hg[n - 2]) * (s - hs[n - 2]) <= (g - hg[n - 2]) * (hs[n - 1] - hs[n - 2]):
                    n -= 1
                if k % sub == 0 and oi < nobs:
                    G = g + EYE
                    best = -np.inf
                    if n > 0:
                        j = n - 1
                        best = (hg[j] - G) / (s - hs[j])
                        while j > 0:
                            t = (hg[j - 1] - G) / (s - hs[j - 1])
                            if t >= best:
                                best = t
                                j -= 1
                            else:
                                break
                    tanh = best - 2.0 * cc * s
                    ok = True
                    if last_gap > -1e17:
                        dm = s - last_gap
                        ok = tanh >= (HMAX - (h + EYE) - C * dm * dm) / dm
                    out_tan[li, oi] = tanh
                    out_ok[li, oi] = ok
                    out_lat[li, oi] = la
                    out_lon[li, oi] = lo_
                    out_h[li, oi] = h
                    oi += 1
                hs[n] = s
                hg[n] = g
                n += 1
            k += 1
            s += vp_step
    return total


# ---------------------------------------------------------------- optimized sweep (knots)

KNOT = 32


@njit(cache=True)
def gnomonic_to_px(x, y, lat0, lon0, z):
    la, lo = inv_gnomonic(x, y, lat0, lon0)
    return merc_px(la, lo, z)


@njit(cache=True)
def sample_segment(mos, ox, oy, oz, zi, lat0, lon0, w, ux, uy, s_a, step, count, out_s, out_h, base):
    """Heights at s = s_a + i*step (i < count) along the line (w, direction u). Exact positions at
    knots every KNOT samples, linear in between (no trig per sample)."""
    z = oz[zi]
    a = mos[zi]
    x0 = ox[zi]
    y0 = oy[zi]
    m = 0
    while m * KNOT < count:
        i0 = m * KNOT
        i1 = min(i0 + KNOT, count - 1)
        sa = s_a + i0 * step
        sb = s_a + i1 * step
        gxa, gya = gnomonic_to_px(-sa * ux + w * uy, -sa * uy - w * ux, lat0, lon0, z)
        gxb, gyb = gnomonic_to_px(-sb * ux + w * uy, -sb * uy - w * ux, lat0, lon0, z)
        span = i1 - i0
        stop = min(i0 + KNOT, count)
        for k in range(i0, stop):
            f = (k - i0) / span if span > 0 else 0.0
            out_s[base + k] = s_a + k * step
            out_h[base + k] = bilinear(a, x0, y0, gxa + (gxb - gxa) * f, gya + (gyb - gya) * f)
        m += 1
    return base + count


@njit(cache=True)
def line_extent(w, ux, uy, half_ew, half_ns):
    smin = -1e18
    smax = 1e18
    for axis in range(2):
        if axis == 0:
            a = -ux
            b = w * uy
            lo = -half_ew
            hi = half_ew
        else:
            a = -uy
            b = -w * ux
            lo = -half_ns
            hi = half_ns
        if abs(a) < 1e-12:
            if b < lo or b > hi:
                return 1.0, -1.0
            continue
        s1 = (lo - b) / a
        s2 = (hi - b) / a
        smin = max(smin, min(s1, s2))
        smax = min(smax, max(s1, s2))
    return smin, smax


@njit(cache=True)
def hull_pass(ss, hh, n_all, first_obs, sub, cc, out_tan, out_ok, out_h, row):
    """Convex-hull sweep over samples (ss ascending downwind). Observers are samples first_obs,
    first_obs+sub, ... Returns number of observers written."""
    hs = np.empty(n_all)
    hg = np.empty(n_all)
    n = 0
    last_gap = -1e18
    oi = 0
    for i in range(n_all):
        s = ss[i]
        h = hh[i]
        is_obs = i >= first_obs and (i - first_obs) % sub == 0
        if np.isnan(h):
            last_gap = s
            if is_obs and oi < out_tan.shape[1]:
                out_tan[row, oi] = np.nan
                out_ok[row, oi] = False
                out_h[row, oi] = np.nan
                oi += 1
            continue
        g = h - cc * s * s
        while n >= 2 and (hg[n - 1] - hg[n - 2]) * (s - hs[n - 2]) <= (g - hg[n - 2]) * (hs[n - 1] - hs[n - 2]):
            n -= 1
        if is_obs and oi < out_tan.shape[1]:
            G = g + EYE
            best = -np.inf
            if n > 0:
                j = n - 1
                best = (hg[j] - G) / (s - hs[j])
                while j > 0:
                    t = (hg[j - 1] - G) / (s - hs[j - 1])
                    if t >= best:
                        best = t
                        j -= 1
                    else:
                        break
            tanh = best - 2.0 * cc * s
            ok = True
            if last_gap > -1e17:
                dm = s - last_gap
                ok = tanh >= (HMAX - (h + EYE) - C * dm * dm) / dm
            out_tan[row, oi] = tanh
            out_ok[row, oi] = ok
            out_h[row, oi] = h
            oi += 1
        hs[n] = s
        hg[n] = g
        n += 1
    return oi


@njit(cache=True)
def sweep2(mos, ox, oy, oz, lat0, lon0, half_ew, half_ns, cell, az, far_len,
           vp_zi, vp_step, f_start, f_end, f_zi, f_step, out_tan, out_ok, out_h, flat):
    """Same result as sweep(), but samples via knots (no trig per sample). far_len cuts the upwind
    field (e.g. from the sun-elevation bound). Returns (samples, lines)."""
    cc = 0.0 if flat else C
    ta = math.radians(az)
    ux = math.sin(ta)
    uy = math.cos(ta)
    wmin = 1e18
    wmax = -1e18
    for cx in (-half_ew, half_ew):
        for cy in (-half_ns, half_ns):
            w = cx * uy - cy * ux
            wmin = min(wmin, w)
            wmax = max(wmax, w)
    sub = int(round(cell / vp_step))
    cap = 0
    for bi in range(f_start.shape[0]):
        if f_start[bi] < far_len:
            cap += int((min(f_end[bi], far_len) - f_start[bi]) / f_step[bi]) + 2
    cap += int(2.0 * math.sqrt(half_ew * half_ew + half_ns * half_ns) / vp_step) + 4
    ss = np.empty(cap)
    hh = np.empty(cap)
    total = 0
    lines = 0
    for li in range(out_tan.shape[0]):
        w = wmin + (li + 0.5) * cell
        if w > wmax:
            break
        smin, smax = line_extent(w, ux, uy, half_ew, half_ns)
        if smin >= smax:
            continue
        base = 0
        for bi in range(f_start.shape[0] - 1, -1, -1):
            if f_start[bi] >= far_len:
                continue
            end = min(f_end[bi], far_len)
            cnt = int((end - f_start[bi]) / f_step[bi])
            if cnt <= 0:
                continue
            base = sample_segment(mos, ox, oy, oz, f_zi[bi], lat0, lon0, w, ux, uy,
                                  smin - f_start[bi] - cnt * f_step[bi], f_step[bi], cnt, ss, hh, base)
        first = base
        cnt = int((smax - smin) / vp_step) + 1
        base = sample_segment(mos, ox, oy, oz, vp_zi, lat0, lon0, w, ux, uy, smin, vp_step, cnt, ss, hh, base)
        hull_pass(ss, hh, base, first, sub, cc, out_tan, out_ok, out_h, li)
        total += base
        lines += 1
    return total, lines


# ---------------------------------------------------------------- far-field bundling


@njit(cache=True)
def _push(hs, hg, n, s, h, cc):
    g = h - cc * s * s
    while n >= 2 and (hg[n - 1] - hg[n - 2]) * (s - hs[n - 2]) <= (g - hg[n - 2]) * (hs[n - 1] - hs[n - 2]):
        n -= 1
    hs[n] = s
    hg[n] = g
    return n + 1


@njit(cache=True)
def _band_into_hull(mos, ox, oy, oz, zi, lat0, lon0, w, ux, uy, s_from, s_to, step, hs, hg, n, cc, buf_s, buf_h):
    cnt = int((s_to - s_from) / step)
    if cnt <= 0:
        return n, 0
    sample_segment(mos, ox, oy, oz, zi, lat0, lon0, w, ux, uy, s_to - cnt * step, step, cnt, buf_s, buf_h, 0)
    for i in range(cnt):
        if not np.isnan(buf_h[i]):
            n = _push(hs, hg, n, buf_s[i], buf_h[i], cc)
    return n, cnt


@njit(cache=True)
def sweep_bundled(mos, ox, oy, oz, lat0, lon0, half_ew, half_ns, cell, az, far_len, vp_zi, vp_step,
                  m10, m11, out_tan):
    """Like sweep2 with bands z12 0-6 km, z11 6-25 km, z10 25-150 km (cut at far_len), but the z10 band
    is sampled once per bundle of m10 lines and the z11 band once per m11 lines; member lines start
    from a copy of the bundle's hull (the far skyline 'projected' laterally). Missing data ignored."""
    cc = C
    ta = math.radians(az)
    ux = math.sin(ta)
    uy = math.cos(ta)
    wmin = 1e18
    wmax = -1e18
    for cx in (-half_ew, half_ew):
        for cy in (-half_ns, half_ns):
            w = cx * uy - cy * ux
            wmin = min(wmin, w)
            wmax = max(wmax, w)
    nl = int((wmax - wmin) / cell)
    st10 = 0.5 * pixel_m(oz[4], lat0)
    st11 = 0.5 * pixel_m(oz[3], lat0)
    st12 = 0.5 * pixel_m(oz[2], lat0)
    buf_s = np.empty(20000)
    buf_h = np.empty(20000)
    A_s = np.empty(20000)
    A_g = np.empty(20000)
    B_s = np.empty(20000)
    B_g = np.empty(20000)
    hs = np.empty(40000)
    hg = np.empty(40000)
    smins = np.full(nl, 1e18)
    for li in range(nl):
        a, b = line_extent(wmin + (li + 0.5) * cell, ux, uy, half_ew, half_ns)
        if a < b:
            smins[li] = a
    total = 0
    for b10 in range(0, nl, m10):
        e10 = min(b10 + m10, nl)
        S0 = smins[b10:e10].min()
        if S0 > 1e17:
            continue
        wc = wmin + (0.5 * (b10 + e10 - 1) + 0.5) * cell
        na = 0
        if far_len > 25000.0:
            na, c = _band_into_hull(mos, ox, oy, oz, 4, lat0, lon0, wc, ux, uy, S0 - far_len, S0 - 25000.0, st10, A_s, A_g, 0, cc, buf_s, buf_h)
            total += c
        for b11 in range(b10, e10, m11):
            e11 = min(b11 + m11, e10)
            S1 = smins[b11:e11].min()
            if S1 > 1e17:
                continue
            wc1 = wmin + (0.5 * (b11 + e11 - 1) + 0.5) * cell
            nb = na
            B_s[:na] = A_s[:na]
            B_g[:na] = A_g[:na]
            if far_len > 6000.0:
                nb, c = _band_into_hull(mos, ox, oy, oz, 3, lat0, lon0, wc1, ux, uy, S0 - min(far_len, 25000.0), S1 - 6000.0, st11, B_s, B_g, nb, cc, buf_s, buf_h)
                total += c
            for li in range(b11, e11):
                smin = smins[li]
                if smin > 1e17:
                    continue
                w = wmin + (li + 0.5) * cell
                n = nb
                hs[:nb] = B_s[:nb]
                hg[:nb] = B_g[:nb]
                n, c = _band_into_hull(mos, ox, oy, oz, 2, lat0, lon0, w, ux, uy, S1 - min(far_len, 6000.0), smin, st12, hs, hg, n, cc, buf_s, buf_h)
                total += c
                _, smax = line_extent(w, ux, uy, half_ew, half_ns)
                cnt = int((smax - smin) / vp_step) + 1
                sample_segment(mos, ox, oy, oz, vp_zi, lat0, lon0, w, ux, uy, smin, vp_step, cnt, buf_s, buf_h, 0)
                total += cnt
                sub = int(round(cell / vp_step))
                oi = 0
                for i in range(cnt):
                    s = buf_s[i]
                    h = buf_h[i]
                    if np.isnan(h):
                        continue
                    if i % sub == 0 and oi < out_tan.shape[1]:
                        g = h - cc * s * s
                        # pops for the ground point first (as in hull_pass)
                        while n >= 2 and (hg[n - 1] - hg[n - 2]) * (s - hs[n - 2]) <= (g - hg[n - 2]) * (hs[n - 1] - hs[n - 2]):
                            n -= 1
                        G = g + EYE
                        best = -np.inf
                        if n > 0:
                            j = n - 1
                            best = (hg[j] - G) / (s - hs[j])
                            while j > 0:
                                t = (hg[j - 1] - G) / (s - hs[j - 1])
                                if t >= best:
                                    best = t
                                    j -= 1
                                else:
                                    break
                        out_tan[li, oi] = best - 2.0 * cc * s
                        oi += 1
                    n = _push(hs, hg, n, s, h, cc)
    return total
```

### exp_instant.py

```python
"""E1: one sun direction. Sweep variants vs per-point spec rays at the same observers and azimuths."""
import math
import sys
import time

import numpy as np

import kern
import setup as S

mos, ox, oy, oz = S.load()
CELL = S.cell_m(12)


def bands(spec):
    st = np.array([b[0] for b in spec], dtype=np.float64)
    en = np.array([b[1] for b in spec], dtype=np.float64)
    zi = np.array([b[2] for b in spec], dtype=np.int64)
    step = np.array([0.5 * S.cell_m(S.ZOOMS[z]) for z in zi])
    return st, en, zi, step


FAR_Z12 = bands([(0, 6000, 2), (6000, 25000, 3), (25000, 150000, 4)])
FAR_Z14 = bands([(0, 1500, 0), (1500, 6000, 2), (6000, 25000, 3), (25000, 150000, 4)])


def run_sweep(az, far, vp_zi, vp_step, flat=False, far_len=150000.0):
    shape = (900, 900)
    out = [np.full(shape, np.nan), np.zeros(shape, dtype=np.bool_), np.full(shape, np.nan), np.full(shape, np.nan), np.full(shape, np.nan)]
    t = time.perf_counter()
    n = kern.sweep(mos, ox, oy, oz, S.LAT0, S.LON0, S.HALF_EW, S.HALF_NS, CELL, az, far_len,
                   vp_zi, vp_step, far[0], far[1], far[2], far[3], *out, flat)
    dt = time.perf_counter() - t
    return out, n, dt


def bearing(lat1, lon1, lat2, lon2):
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dl = math.radians(lon2 - lon1)
    return math.degrees(math.atan2(math.sin(dl) * math.cos(p2), math.cos(p1) * math.sin(p2) - math.sin(p1) * math.cos(p2) * math.cos(dl))) % 360


def line_azimuth(lat, lon, az):
    """True azimuth of the sweep line (a gnomonic straight line of direction az) at an observer."""
    # observer back to gnomonic: invert numerically via forward gnomonic
    p0, l0 = math.radians(S.LAT0), math.radians(S.LON0)
    p, l = math.radians(lat), math.radians(lon)
    cosc = math.sin(p0) * math.sin(p) + math.cos(p0) * math.cos(p) * math.cos(l - l0)
    x = kern.R * math.cos(p) * math.sin(l - l0) / cosc
    y = kern.R * (math.cos(p0) * math.sin(p) - math.sin(p0) * math.cos(p) * math.cos(l - l0)) / cosc
    ta = math.radians(az)
    la2, lo2 = kern.inv_gnomonic(x + 200 * math.sin(ta), y + 200 * math.cos(ta), S.LAT0, S.LON0)
    return bearing(lat, lon, la2, lo2)


def main(local_iso, tz):
    az, el = S.sun_at(local_iso, tz)
    print(f"\n=== {local_iso} UTC{tz:+d}: sun azimuth {az:.2f}, upper limb {el:.2f} deg")
    res = {}
    for name, far, zi, step, flat in [("sweep z12", FAR_Z12, 2, CELL, False),
                                      ("sweep z12 flat", FAR_Z12, 2, CELL, True),
                                      ("sweep z14 along lines", FAR_Z14, 0, CELL / 8, False)]:
        run_sweep(az, far, zi, step, flat)  # compile / warm
        out, n, dt = run_sweep(az, far, zi, step, flat)
        valid = ~np.isnan(out[4])
        print(f"{name:24s} observers {valid.sum():7d}  samples {n / 1e6:6.2f} M  time {dt * 1000:7.1f} ms  "
              f"incomplete {np.sum(valid & ~out[1])}")
        res[name] = out
    # per-point rays at a random subset of observers
    out = res["sweep z12"]
    idx = np.argwhere(~np.isnan(out[4]))
    rng = np.random.default_rng(1)
    pick = idx[rng.choice(len(idx), size=3000, replace=False)]
    spec_b = S.SPEC_BANDS
    z12_b = S.Z12_BANDS
    rows = []
    t = time.perf_counter()
    for (i, j) in pick:
        lat, lon = out[2][i, j], out[3][i, j]
        a = line_azimuth(lat, lon, az)
        e14 = kern.ground(mos, ox, oy, oz, 0, lat, lon)
        e12 = kern.ground(mos, ox, oy, oz, 2, lat, lon)
        r14 = kern.ray(mos, ox, oy, oz, lat, lon, a, e14, spec_b[0], spec_b[1], spec_b[2], np.inf)
        r12 = kern.ray(mos, ox, oy, oz, lat, lon, a, e12, z12_b[0], z12_b[1], z12_b[2], np.inf)
        rows.append((a - az, math.degrees(math.atan(r14[0])), math.degrees(math.atan(r12[0])),
                     math.degrees(math.atan(out[0][i, j])),
                     math.degrees(math.atan(res["sweep z12 flat"][0][i, j])) if not np.isnan(res["sweep z12 flat"][0][i, j]) else np.nan,
                     r14[1], r12[1], out[1][i, j]))
    print(f"3000 point rays x2: {time.perf_counter() - t:.1f} s")
    r = np.array(rows, dtype=np.float64)
    daz, h14, h12, hsw, hfl = r[:, 0], r[:, 1], r[:, 2], r[:, 3], r[:, 4]
    print(f"line azimuth vs sun azimuth at observers: max |d| {np.max(np.abs((daz + 180) % 360 - 180)):.3f} deg")

    def stats(label, a, b):
        d = a - b
        ad = np.abs(d)
        sa = (a < el)
        sb = (b < el)
        print(f"  {label:34s} median |d| {np.median(ad):.3f}  p95 {np.percentile(ad, 95):.3f}  p99 {np.percentile(ad, 99):.3f}  "
              f"max {ad.max():.2f} deg   sun-state differs {np.mean(sa != sb) * 100:5.2f} %")

    print(f"  shade fraction (spec rays): {np.mean(h14 >= el) * 100:.1f} %")
    stats("sweep z12      vs rays z12 (algo)", hsw, h12)
    stats("sweep z12 flat vs sweep z12 (curv)", hfl, hsw)
    stats("rays z12       vs rays spec (res.)", h12, h14)
    stats("sweep z12      vs rays spec (total)", hsw, h14)
    # z14 along lines: same observers? observers coincide (same lines, same cell grid)
    o4 = res["sweep z14 along lines"]
    h4 = np.array([math.degrees(math.atan(o4[0][i, j])) for (i, j) in pick])
    stats("sweep z14-lines vs rays spec", h4, h14)
    return res


if __name__ == "__main__":
    for iso, tz in [("2025-12-21T12:00", 1), ("2025-12-21T15:00", 1), ("2025-12-21T10:00", 1),
                    ("2025-06-21T07:30", 2), ("2025-03-21T16:30", 1)]:
        main(iso, tz)
```

### exp_speed.py

```python
"""E2: optimized sweep: equality with the slow sweep, time, and the effect of the upwind cut."""
import math, time
import numpy as np
import kern, setup as S
from exp_instant import FAR_Z12, FAR_Z14, CELL, mos, ox, oy, oz, run_sweep

def run2(az, far, zi, step, far_len=150000.0, flat=False):
    shape = (900, 900)
    t_ = np.full(shape, np.nan); ok = np.zeros(shape, dtype=np.bool_); h = np.full(shape, np.nan)
    t = time.perf_counter()
    n, lines = kern.sweep2(mos, ox, oy, oz, S.LAT0, S.LON0, S.HALF_EW, S.HALF_NS, CELL, az, far_len,
                           zi, step, far[0], far[1], far[2], far[3], t_, ok, h, flat)
    return (t_, ok, h), n, time.perf_counter() - t

def dmax(el_deg, eye_min):
    ta = math.tan(math.radians(el_deg)); c = kern.C; a = kern.HMAX - eye_min
    return (-ta + math.sqrt(ta * ta + 4 * c * a)) / (2 * c)

eye_min = float(np.nanmin(mos[2])) # conservative lower bound of any eye in the region
for iso, tz in [("2025-12-21T12:00", 1), ("2025-12-21T15:00", 1), ("2025-06-21T07:30", 2), ("2025-12-21T16:15", 1)]:
    az, el = S.sun_at(iso, tz)
    ref, _, _ = run_sweep(az, FAR_Z12, 2, CELL)
    run2(az, FAR_Z12, 2, CELL)
    o, n, dt = run2(az, FAR_Z12, 2, CELL)
    a = np.degrees(np.arctan(ref[0])); b = np.degrees(np.arctan(o[0]))
    m = ~np.isnan(a) & ~np.isnan(b)
    L = min(150000.0, dmax(el, 560.0))
    o2, n2, dt2 = run2(az, FAR_Z12, 2, CELL, far_len=L)
    s1 = np.degrees(np.arctan(o[0])) < el; s2 = np.degrees(np.arctan(o2[0])) < el
    m2 = ~np.isnan(o[0]) & ~np.isnan(o2[0])
    o3, n3, dt3 = run2(az, FAR_Z14, 0, CELL / 8, far_len=L)
    o4, n4, dt4 = run2(az, FAR_Z14, 0, CELL / 2, far_len=L)
    s4 = np.degrees(np.arctan(o4[0])) < el; s3 = np.degrees(np.arctan(o3[0])) < el
    m3 = ~np.isnan(o3[0]) & ~np.isnan(o4[0])
    print(f"{iso} az {az:.1f} el {el:.2f}: knots vs trig max|d| {np.max(np.abs(a[m]-b[m])):.4f} deg; "
          f"full 150 km: {n/1e6:.2f} M samples {dt*1000:.0f} ms | cut at {L/1000:.0f} km: {n2/1e6:.2f} M {dt2*1000:.0f} ms, "
          f"state diff {np.sum(s1[m2]!=s2[m2])} | z14 half-px lines: {n3/1e6:.2f} M {dt3*1000:.0f} ms | "
          f"z14 every 4th px: {n4/1e6:.2f} M {dt4*1000:.0f} ms state diff vs half-px {np.mean(s3[m3]!=s4[m3])*100:.3f} %")
```

### exp_day.py

```python
"""E3: per-cell horizons for ALL cells from azimuth sweeps -> sun periods per cell, vs the spec point tracer.

For N sample cells (fixed map positions), compare the sun periods of a day computed from
 (a) the spec point profile (0.25 deg, z14 near, great-circle rays) at the cell centre,
 (b) sweeps at azimuth steps dA over the day's sun azimuth range, nearest sweep observer per cell,
     horizon linearly interpolated between sweep azimuths,
 (c) the spec point profile at a point displaced by half a cell (within-cell variability baseline).
"""
import math
import sys
import time
from datetime import datetime, timedelta, timezone

import numpy as np
from numba import njit

import kern
import setup as S
from exp_instant import CELL, FAR_Z12, FAR_Z14, mos, ox, oy, oz

AZ = np.arange(0.0, 360.0, 0.25)


@njit(cache=True)
def spec_profile(mos, ox, oy, oz, lat, lon, az_lo, az_hi, b0, b1, b2):
    out = np.full(1440, np.nan)
    e = kern.ground(mos, ox, oy, oz, 0, lat, lon)
    for i in range(1440):
        a = i * 0.25
        if a < az_lo or a > az_hi:
            continue
        out[i] = kern.ray(mos, ox, oy, oz, lat, lon, a, e, b0, b1, b2, np.inf)[0]
    return out


def day_track(date, lat, lon, tz=1):
    t0 = datetime.fromisoformat(date).replace(tzinfo=timezone(timedelta(hours=tz))).timestamp()
    u = t0 + np.arange(0, 86400, 10.0)
    az, app, _, _ = S.sun_track(u, lat, lon)
    return u, az, app


def periods(u, sun_az, sun_el, grid_az, grid_tan):
    """Sun periods [(start, end)] in seconds from the horizon given on an azimuth grid."""
    h = np.degrees(np.arctan(np.interp(sun_az, grid_az, grid_tan)))
    lit = sun_el > h
    out = []
    i = 0
    n = len(lit)
    while i < n:
        if lit[i]:
            j = i
            while j < n and lit[j]:
                j += 1
            a = u[i] - 5 if i > 0 else u[0]
            b = u[j] - 5 if j < n else u[-1] + 10
            if b - a >= 60:
                out.append((a, b))
            i = j
        else:
            i += 1
    return out


def compare(p, q):
    if len(p) != len(q):
        return None
    if not p:
        return 0.0
    return max(max(abs(a[0] - b[0]), abs(a[1] - b[1])) for a, b in zip(p, q)) / 60.0


def fwd_gnomonic(lat, lon):
    p0, l0 = math.radians(S.LAT0), math.radians(S.LON0)
    p, l = math.radians(lat), math.radians(lon)
    cosc = math.sin(p0) * math.sin(p) + math.cos(p0) * math.cos(p) * math.cos(l - l0)
    return (kern.R * math.cos(p) * math.sin(l - l0) / cosc,
            kern.R * (math.cos(p0) * math.sin(p) - math.sin(p0) * math.cos(p) * math.cos(l - l0)) / cosc)


def sweep_at_cells(az, cells_xy, far, zi, step, far_len):
    shape = (900, 900)
    t_ = np.full(shape, np.nan)
    ok = np.zeros(shape, dtype=np.bool_)
    h = np.full(shape, np.nan)
    kern.sweep2(mos, ox, oy, oz, S.LAT0, S.LON0, S.HALF_EW, S.HALF_NS, CELL, az, far_len,
                zi, step, far[0], far[1], far[2], far[3], t_, ok, h, False)
    ta = math.radians(az)
    ux, uy = math.sin(ta), math.cos(ta)
    wmin = min(cx * uy - cy * ux for cx in (-S.HALF_EW, S.HALF_EW) for cy in (-S.HALF_NS, S.HALF_NS))
    res = np.empty(len(cells_xy))
    for k, (x, y) in enumerate(cells_xy):
        s = -(x * ux + y * uy)
        w = x * uy - y * ux
        li = int(round((w - wmin) / CELL - 0.5))
        wl = wmin + (li + 0.5) * CELL
        smin, smax = kern.line_extent(wl, ux, uy, S.HALF_EW, S.HALF_NS)
        oi = int(round((s - smin) / CELL))
        res[k] = t_[li, min(max(oi, 0), 899)]
    return res


def dmax(el_deg, eye_min=560.0):
    if el_deg <= 0.3:
        return 150000.0
    ta = math.tan(math.radians(el_deg))
    return min(150000.0, (-ta + math.sqrt(ta * ta + 4 * kern.C * (kern.HMAX - eye_min))) / (2 * kern.C))


def main(n_cells=120, dates=("2025-12-21", "2025-03-21", "2025-06-21"), steps=(0.25, 0.5, 1.0, 2.0)):
    rng = np.random.default_rng(7)
    cells_xy = np.column_stack([rng.uniform(-S.HALF_EW + 50, S.HALF_EW - 50, n_cells),
                                rng.uniform(-S.HALF_NS + 50, S.HALF_NS - 50, n_cells)])
    cells_ll = [kern.inv_gnomonic(x, y, S.LAT0, S.LON0) for x, y in cells_xy]
    ang = rng.uniform(0, 2 * math.pi, n_cells)
    half = CELL / 2
    disp_ll = [kern.inv_gnomonic(x + half * math.cos(a), y + half * math.sin(a), S.LAT0, S.LON0)
               for (x, y), a in zip(cells_xy, ang)]
    t = time.perf_counter()
    prof = np.array([spec_profile(mos, ox, oy, oz, la, lo, 40.0, 320.0, *S.SPEC_BANDS) for la, lo in cells_ll])
    prof_d = np.array([spec_profile(mos, ox, oy, oz, la, lo, 40.0, 320.0, *S.SPEC_BANDS) for la, lo in disp_ll])
    print(f"spec profiles: {2 * n_cells} x ~1100 rays in {time.perf_counter() - t:.1f} s", flush=True)
    for date in dates:
        u, saz, sel = day_track(date, S.LAT0, S.LON0)
        up = sel > -1.0
        a_lo, a_hi = math.floor(saz[up].min()) - 1, math.ceil(saz[up].max()) + 1
        ref = []
        for k, (la, lo) in enumerate(cells_ll):
            uu, az_k, el_k = day_track(date, la, lo)
            ref.append((uu, az_k, el_k, periods(uu, az_k, el_k, AZ, prof[k])))
        base = []
        for k in range(n_cells):
            uu, az_k, el_k, p = ref[k]
            base.append(compare(p, periods(uu, az_k, el_k, AZ, prof_d[k])))
        report(f"{date} half-cell displaced spec", base)
        for variant, far, zi, step in (("z12 lines", FAR_Z12, 2, CELL), ("z14 lines", FAR_Z14, 0, CELL / 8)):
            for dA in steps:
                grid = np.arange(a_lo, a_hi + dA / 2, dA)
                t = time.perf_counter()
                H = np.empty((len(grid), n_cells))
                n_samples = 0
                for gi, a in enumerate(grid):
                    # lowest sun elevation at this azimuth today -> upwind cut
                    near = np.abs(((saz - a + 180) % 360) - 180) < dA
                    e_min = sel[near].min() if near.any() else 0.0
                    H[gi] = sweep_at_cells(a, cells_xy, far, zi, step, dmax(e_min))
                dt = time.perf_counter() - t
                errs = []
                for k in range(n_cells):
                    uu, az_k, el_k, p = ref[k]
                    errs.append(compare(p, periods(uu, az_k, el_k, grid, H[:, k])))
                report(f"{date} {variant} dA={dA}: {len(grid)} sweeps {dt:.1f} s", errs)
        sys.stdout.flush()


def report(label, errs):
    known = [e for e in errs if e is not None]
    miss = len(errs) - len(known)
    e = np.array(known)
    print(f"  {label:48s} cells with other period count {miss:3d}/{len(errs)} | boundary error min: "
          f"median {np.median(e):.2f} p90 {np.percentile(e, 90):.2f} max {e.max():.2f}", flush=True)


if __name__ == "__main__":
    main()
```

### exp_day2.py

```python
"""E3b: per-cell day product - resampling (nearest vs bilinear) and a robust metric:
minutes per day in which the cell's state differs from the spec point truth."""
import math, os, time
import numpy as np
import kern, setup as S
from exp_day import AZ, day_track, spec_profile, dmax
from exp_instant import CELL, FAR_Z12, FAR_Z14, mos, ox, oy, oz

rng = np.random.default_rng(7)
N = 120
cells_xy = np.column_stack([rng.uniform(-S.HALF_EW + 50, S.HALF_EW - 50, N), rng.uniform(-S.HALF_NS + 50, S.HALF_NS - 50, N)])
cells_ll = [kern.inv_gnomonic(x, y, S.LAT0, S.LON0) for x, y in cells_xy]
ang = rng.uniform(0, 2 * math.pi, N)
disp_ll = [kern.inv_gnomonic(x + CELL / 2 * math.cos(a), y + CELL / 2 * math.sin(a), S.LAT0, S.LON0) for (x, y), a in zip(cells_xy, ang)]
pf = "cache/prof.npz"
if os.path.exists(pf):
    z = np.load(pf); prof, prof_d = z["a"], z["b"]
else:
    prof = np.array([spec_profile(mos, ox, oy, oz, la, lo, 40.0, 320.0, *S.SPEC_BANDS) for la, lo in cells_ll])
    prof_d = np.array([spec_profile(mos, ox, oy, oz, la, lo, 40.0, 320.0, *S.SPEC_BANDS) for la, lo in disp_ll])
    np.savez(pf, a=prof, b=prof_d)

def sweep_cells(az, far, zi, step, far_len, mode):
    shape = (900, 900)
    t_ = np.full(shape, np.nan); ok = np.zeros(shape, dtype=np.bool_); h = np.full(shape, np.nan)
    kern.sweep2(mos, ox, oy, oz, S.LAT0, S.LON0, S.HALF_EW, S.HALF_NS, CELL, az, far_len, zi, step, far[0], far[1], far[2], far[3], t_, ok, h, False)
    ta = math.radians(az); ux, uy = math.sin(ta), math.cos(ta)
    wmin = min(cx * uy - cy * ux for cx in (-S.HALF_EW, S.HALF_EW) for cy in (-S.HALF_NS, S.HALF_NS))
    res = np.empty(N)
    for k, (x, y) in enumerate(cells_xy):
        s = -(x * ux + y * uy); w = x * uy - y * ux
        fl = (w - wmin) / CELL - 0.5
        if mode == "nearest":
            li = int(round(fl)); smin, _ = kern.line_extent(wmin + (li + .5) * CELL, ux, uy, S.HALF_EW, S.HALF_NS)
            res[k] = t_[li, int(round((s - smin) / CELL))]
        else:  # bilinear over 2 lines x 2 samples (angle space)
            l0 = int(math.floor(fl)); fw = fl - l0; acc = 0.0; wsum = 0.0
            for li, wl in ((l0, 1 - fw), (l0 + 1, fw)):
                smin, _ = kern.line_extent(wmin + (li + .5) * CELL, ux, uy, S.HALF_EW, S.HALF_NS)
                fs = (s - smin) / CELL; o0 = int(math.floor(fs)); f = fs - o0
                for oi, wo in ((o0, 1 - f), (o0 + 1, f)):
                    v = t_[li, min(max(oi, 0), 899)]
                    if not np.isnan(v): acc += wl * wo * math.atan(v); wsum += wl * wo
            res[k] = math.tan(acc / wsum)
    return res

def lit(u, saz, sel, grid, tan):
    return sel > np.degrees(np.arctan(np.interp(saz, grid, tan)))

def nper(l):
    return int(np.sum(np.diff(l.astype(int)) == 1) + l[0])

for date in ("2025-12-21", "2025-06-21"):
    u, saz, sel = day_track(date, S.LAT0, S.LON0)
    up = sel > -1.0
    a_lo, a_hi = math.floor(saz[up].min()) - 1, math.ceil(saz[up].max()) + 1
    tracks = [day_track(date, la, lo) for la, lo in cells_ll]
    ref = [lit(*tracks[k], AZ, prof[k]) for k in range(N)]
    def rep(label, lits):
        dis = np.array([np.sum(a != b) * 10 / 60 for a, b in zip(ref, lits)])
        cnt = np.array([nper(a) != nper(b) for a, b in zip(ref, lits)])
        tot = np.array([abs(np.sum(a) - np.sum(b)) * 10 / 60 for a, b in zip(ref, lits)])
        print(f"  {label:36s} disagreement min/day: median {np.median(dis):5.1f} p90 {np.percentile(dis,90):5.1f} max {dis.max():6.1f} | "
              f"|sun-hours diff| median {np.median(tot):4.1f} p90 {np.percentile(tot,90):5.1f} min | raw period-count diff {cnt.sum()}/{N}", flush=True)
    print(date)
    rep("half-cell displaced spec", [lit(*tracks[k], AZ, prof_d[k]) for k in range(N)])
    for variant, far, zi, step in (("z12", FAR_Z12, 2, CELL), ("z14", FAR_Z14, 0, CELL / 8)):
        for dA in (0.5, 1.0):
            grid = np.arange(a_lo, a_hi + dA / 2, dA)
            for mode in ("nearest", "bilinear"):
                H = np.empty((len(grid), N))
                for gi, a in enumerate(grid):
                    near = np.abs(((saz - a + 180) % 360) - 180) < dA
                    H[gi] = sweep_cells(a, far, zi, step, dmax(sel[near].min() if near.any() else 0.0), mode)
                rep(f"{variant} lines dA={dA} {mode}", [lit(*tracks[k], grid, H[:, k]) for k in range(N)])
```

### exp_proj.py

```python
"""E4: Q's idea generalized - compute a horizon at a block centre, project its far skyline onto
nearby observers, combine with an exact per-observer near field.

H_est(p, A) = max( near_p(A; d <= D),  max over skyline points q of c's far field (d > D) projected to p )
The projected part is a lower bound of p's true far horizon (a max over a subset of terrain points,
up to the polyline interpolation between neighbouring skyline points).
"""
import math
import time

import numpy as np
from numba import njit

import kern
import setup as S
from exp_day import AZ, compare, day_track, periods, spec_profile
from exp_instant import mos, ox, oy, oz


@njit(cache=True)
def far_skyline(mos, ox, oy, oz, lat, lon, D, b0, b1, b2):
    """Far horizon (d > D) of the block centre: per 0.25 deg bin the skyline point (lat, lon, h)."""
    e = kern.ground(mos, ox, oy, oz, 0, lat, lon)
    n = b0.shape[0]
    s0 = np.empty(n)
    s1 = np.empty(n)
    for i in range(n):
        s0[i] = max(b0[i], D)
        s1[i] = b1[i]
    pts = np.full((1440, 3), np.nan)
    for i in range(1440):
        a = i * 0.25
        r = kern.ray(mos, ox, oy, oz, lat, lon, a, e, s0, s1, b2, np.inf)
        if r[0] > -np.inf and r[2] > 0:
            pts[i, 0] = r[4]
            pts[i, 1] = r[5]
            pts[i, 2] = r[3]
    return pts


@njit(cache=True)
def near_profile(mos, ox, oy, oz, lat, lon, D, b0, b1, b2):
    e = kern.ground(mos, ox, oy, oz, 0, lat, lon)
    n = b0.shape[0]
    s0 = np.empty(n)
    s1 = np.empty(n)
    for i in range(n):
        s0[i] = b0[i]
        s1[i] = min(b1[i], D)
    out = np.empty(1440)
    samples = 0
    for i in range(1440):
        out[i] = kern.ray(mos, ox, oy, oz, lat, lon, i * 0.25, e, s0, s1, b2, np.inf)[0]
    return out, e


@njit(cache=True)
def project(lat, lon, eye, pts):
    """Project skyline points (a closed polyline over the centre's bins) to observer (lat, lon, eye):
    per 0.25 deg bin the max tan over the polyline, segments interpolated linearly in (A, tan)."""
    out = np.full(1440, -np.inf)
    p1 = math.radians(lat)
    n = pts.shape[0]
    A = np.full(n, np.nan)
    T = np.full(n, np.nan)
    for i in range(n):
        if np.isnan(pts[i, 0]):
            continue
        p2 = math.radians(pts[i, 0])
        dl = math.radians(pts[i, 1] - lon)
        # great-circle distance and initial bearing
        a = math.sin((p2 - p1) / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
        d = 2 * kern.R * math.asin(math.sqrt(a))
        brg = math.degrees(math.atan2(math.sin(dl) * math.cos(p2), math.cos(p1) * math.sin(p2) - math.sin(p1) * math.cos(p2) * math.cos(dl))) % 360
        A[i] = brg
        T[i] = (pts[i, 2] - eye - kern.C * d * d) / d
    for i in range(n):
        j = (i + 1) % n
        if np.isnan(A[i]) or np.isnan(A[j]):
            continue
        a0 = A[i]
        a1 = A[j]
        da = ((a1 - a0 + 180.0) % 360.0) - 180.0
        if abs(da) > 5.0:  # not neighbours any more (skyline jumped between ridges): no segment
            for (aa, tt) in ((a0, T[i]), (a1, T[j])):
                b = int(round(aa / 0.25)) % 1440
                out[b] = max(out[b], tt)
            continue
        steps = int(abs(da) / 0.25) + 1
        for k in range(steps + 1):
            f = k / steps
            aa = a0 + da * f
            b = int(round(aa / 0.25)) % 1440
            out[b] = max(out[b], T[i] + (T[j] - T[i]) * f)
    return out


def main(n_centres=8, per_centre=12):
    rng = np.random.default_rng(3)
    dates = ("2025-12-21", "2025-03-21", "2025-06-21")
    for D in (1000.0, 2000.0, 5000.0):
        for r in (50.0, 150.0, 400.0, 1000.0):
            ang_err = []
            per_err = {d: [] for d in dates}
            for ci in range(n_centres):
                cx = rng.uniform(-S.HALF_EW + 1500, S.HALF_EW - 1500)
                cy = rng.uniform(-S.HALF_NS + 1500, S.HALF_NS - 1500)
                clat, clon = kern.inv_gnomonic(cx, cy, S.LAT0, S.LON0)
                pts = far_skyline(mos, ox, oy, oz, clat, clon, D, *S.SPEC_BANDS)
                for _ in range(per_centre):
                    a = rng.uniform(0, 2 * math.pi)
                    plat, plon = kern.inv_gnomonic(cx + r * math.cos(a), cy + r * math.sin(a), S.LAT0, S.LON0)
                    near, eye = near_profile(mos, ox, oy, oz, plat, plon, D, *S.SPEC_BANDS)
                    est = np.maximum(near, project(plat, plon, eye, pts))
                    truth = spec_profile(mos, ox, oy, oz, plat, plon, 0.0, 360.0, *S.SPEC_BANDS)
                    sel = (AZ >= 50) & (AZ <= 310)
                    ang_err.append(np.degrees(np.arctan(est[sel])) - np.degrees(np.arctan(truth[sel])))
                    for d in dates:
                        uu, az_k, el_k = day_track(d, plat, plon)
                        per_err[d].append(compare(periods(uu, az_k, el_k, AZ, truth), periods(uu, az_k, el_k, AZ, est)))
            e = np.concatenate(ang_err)
            line = (f"D={D / 1000:.0f} km r={r:5.0f} m: angle err (est - true) p1 {np.percentile(e, 1):6.2f} "
                    f"median {np.median(e):5.2f} p99 {np.percentile(e, 99):5.2f} deg; ")
            for d in dates:
                k = [x for x in per_err[d] if x is not None]
                line += f"{d[5:]}: {len(per_err[d]) - len(k)} count diff, max {max(k):.1f} p90 {np.percentile(k, 90):.1f} min | "
            print(line, flush=True)


if __name__ == "__main__":
    main()
```

### exp_bundle.py

```python
"""E5: far-field bundling - share far hulls across neighbouring lines. Accuracy vs unbundled, samples, time."""
import math, time
import numpy as np
import kern, setup as S
from exp_instant import CELL, mos, ox, oy, oz
from exp_day import dmax

for iso, tz in [("2025-12-21T12:00", 1), ("2025-12-21T15:00", 1), ("2025-12-21T16:15", 1), ("2025-06-21T07:30", 2)]:
    az, el = S.sun_at(iso, tz)
    for L in (dmax(el), 150000.0):
        res = {}
        for m10, m11 in ((1, 1), (4, 2), (8, 2), (16, 4), (32, 8)):
            out = np.full((900, 900), np.nan)
            kern.sweep_bundled(mos, ox, oy, oz, S.LAT0, S.LON0, S.HALF_EW, S.HALF_NS, CELL, az, L, 2, CELL, m10, m11, out)
            out[:] = np.nan
            t = time.perf_counter()
            n = kern.sweep_bundled(mos, ox, oy, oz, S.LAT0, S.LON0, S.HALF_EW, S.HALF_NS, CELL, az, L, 2, CELL, m10, m11, out)
            dt = time.perf_counter() - t
            res[(m10, m11)] = (np.degrees(np.arctan(out)), n, dt)
        ref = res[(1, 1)][0]
        line = f"{iso} el {el:5.2f} cut {L/1000:4.0f} km:"
        for key, (h, n, dt) in res.items():
            m = ~np.isnan(ref) & ~np.isnan(h)
            d = np.abs(h[m] - ref[m])
            diff = np.mean((h[m] < el) != (ref[m] < el)) * 100
            line += f" | {key[0]}/{key[1]}: {n/1e6:.2f}M {dt*1000:.0f}ms p99 {np.percentile(d,99):.3f} max {d.max():.2f} state {diff:.3f}%"
        print(line, flush=True)
```

### SweepBench.java

```java
import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** JVM port of kern.sweep2 (hull sweep with knots) for timing. Heights as float[] per zoom. */
public class SweepBench {
    static final double R = 6371000.0, C = (1 - 0.13) / (2 * R), EYE = 1.7, HMAX = 4810.0;
    static final int TILE = 512, KNOT = 32;
    static float[][] mos = new float[5][];
    static int[] w = new int[5], h = new int[5], z = new int[5];
    static long[] ox = new long[5], oy = new long[5];
    static final double LAT0 = 46.5935, LON0 = 7.9091, HALF_EW = 3000, HALF_NS = 5000;

    public static void main(String[] a) throws Exception {
        List<String> meta = Files.readAllLines(Paths.get("meta.txt"));
        for (int i = 0; i < 5; i++) {
            String[] p = meta.get(i).trim().split("\\s+");
            z[i] = Integer.parseInt(p[0]); h[i] = Integer.parseInt(p[1]); w[i] = Integer.parseInt(p[2]);
            ox[i] = Long.parseLong(p[3]); oy[i] = Long.parseLong(p[4]);
            try (FileChannel fc = FileChannel.open(Paths.get("z" + z[i] + ".bin"))) {
                FloatBuffer fb = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size()).order(ByteOrder.BIG_ENDIAN).asFloatBuffer();
                mos[i] = new float[fb.remaining()]; fb.get(mos[i]);
            }
        }
        double cell = cellM(12);
        double[][] cases = {{173.5, 20.02}, {215.6, 12.2}, {73.4, 17.09}, {230.5, 3.56}};
        for (int threads : new int[]{1, 3}) {
            ExecutorService ex = Executors.newFixedThreadPool(threads);
            for (double[] cs : cases) {
                double L = dmax(cs[1]);
                for (String v : new String[]{"z12", "z14"}) {
                    int vpZi = v.equals("z12") ? 2 : 0; double vpStep = v.equals("z12") ? cell : cell / 8;
                    double[][] far = v.equals("z12")
                        ? new double[][]{{0, 6000, 2}, {6000, 25000, 3}, {25000, 150000, 4}}
                        : new double[][]{{0, 1500, 0}, {1500, 6000, 2}, {6000, 25000, 3}, {25000, 150000, 4}};
                    long best = Long.MAX_VALUE; long samples = 0;
                    for (int rep = 0; rep < 6; rep++) {
                        long t = System.nanoTime();
                        samples = sweep(ex, threads, cs[0], L, vpZi, vpStep, far, cell);
                        best = Math.min(best, System.nanoTime() - t);
                    }
                    System.out.printf("threads %d az %6.1f el %5.2f cut %5.0f km %s: %5.2f M samples, best %6.1f ms (%.1f ns/sample/thread)%n",
                        threads, cs[0], cs[1], L / 1000, v, samples / 1e6, best / 1e6, best * threads / (double) samples);
                }
            }
            ex.shutdown();
        }
    }

    static double cellM(int zz) { return 2 * Math.PI * R * Math.cos(Math.toRadians(LAT0)) / ((1L << zz) * TILE); }

    static double dmax(double el) {
        double ta = Math.tan(Math.toRadians(el)), aa = HMAX - 560;
        return Math.min(150000, (-ta + Math.sqrt(ta * ta + 4 * C * aa)) / (2 * C));
    }

    static long sweep(ExecutorService ex, int threads, double az, double farLen, int vpZi, double vpStep, double[][] far, double cell) throws Exception {
        double ta = Math.toRadians(az), ux = Math.sin(ta), uy = Math.cos(ta);
        double wmin = 1e18, wmax = -1e18;
        for (double cx : new double[]{-HALF_EW, HALF_EW}) for (double cy : new double[]{-HALF_NS, HALF_NS}) {
            double ww = cx * uy - cy * ux; wmin = Math.min(wmin, ww); wmax = Math.max(wmax, ww);
        }
        int nlines = (int) ((wmax - wmin) / cell);
        final double fwmin = wmin;
        List<Future<Long>> fs = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            final int t0 = t;
            fs.add(ex.submit(() -> {
                long tot = 0; int sub = (int) Math.round(cell / vpStep);
                double[] ss = new double[40000], hh = new double[40000], hs = new double[40000], hg = new double[40000];
                float[] outTan = new float[2000];
                for (int li = t0; li < nlines; li += threads) {
                    double ww = fwmin + (li + 0.5) * cell;
                    double[] ext = extent(ww, ux, uy);
                    if (ext[0] >= ext[1]) continue;
                    int n = 0;
                    for (int b = far.length - 1; b >= 0; b--) {
                        if (far[b][0] >= farLen) continue;
                        int zi = (int) far[b][2]; double step = 0.5 * cellM(z[zi]);
                        int cnt = (int) ((Math.min(far[b][1], farLen) - far[b][0]) / step);
                        n = segment(zi, ww, ux, uy, ext[0] - far[b][0] - cnt * step, step, cnt, ss, hh, n);
                    }
                    int first = n;
                    n = segment(vpZi, ww, ux, uy, ext[0], vpStep, (int) ((ext[1] - ext[0]) / vpStep) + 1, ss, hh, n);
                    hull(ss, hh, n, first, sub, hs, hg, outTan);
                    tot += n;
                }
                return tot;
            }));
        }
        long total = 0;
        for (Future<Long> f : fs) total += f.get();
        return total;
    }

    static double[] extent(double ww, double ux, double uy) {
        double smin = -1e18, smax = 1e18;
        double[][] ax = {{-ux, ww * uy, -HALF_EW, HALF_EW}, {-uy, -ww * ux, -HALF_NS, HALF_NS}};
        for (double[] q : ax) {
            if (Math.abs(q[0]) < 1e-12) { if (q[1] < q[2] || q[1] > q[3]) return new double[]{1, -1}; continue; }
            double s1 = (q[2] - q[1]) / q[0], s2 = (q[3] - q[1]) / q[0];
            smin = Math.max(smin, Math.min(s1, s2)); smax = Math.min(smax, Math.max(s1, s2));
        }
        return new double[]{smin, smax};
    }

    static void px(double x, double y, int zi, double[] out) {
        double rho = Math.sqrt(x * x + y * y), lat = LAT0, lon = LON0;
        if (rho > 1e-9) {
            double cc = Math.atan(rho / R), p0 = Math.toRadians(LAT0);
            lat = Math.toDegrees(Math.asin(Math.cos(cc) * Math.sin(p0) + y * Math.sin(cc) * Math.cos(p0) / rho));
            lon = LON0 + Math.toDegrees(Math.atan2(x * Math.sin(cc), rho * Math.cos(p0) * Math.cos(cc) - y * Math.sin(p0) * Math.sin(cc)));
        }
        double n = (double) (1L << z[zi]) * TILE, s = Math.sin(Math.toRadians(lat));
        out[0] = (lon + 180) / 360 * n - 0.5 - ox[zi];
        out[1] = (1 - 0.5 * Math.log((1 + s) / (1 - s)) / Math.PI) / 2 * n - 0.5 - oy[zi];
    }

    static int segment(int zi, double ww, double ux, double uy, double sa, double step, int count, double[] ss, double[] hh, int base) {
        float[] m = mos[zi]; int W = w[zi], H = h[zi];
        double[] a = new double[2], b = new double[2];
        for (int i0 = 0; i0 < count; i0 += KNOT) {
            int i1 = Math.min(i0 + KNOT, count - 1);
            double s0 = sa + i0 * step, s1 = sa + i1 * step;
            px(-s0 * ux + ww * uy, -s0 * uy - ww * ux, zi, a);
            px(-s1 * ux + ww * uy, -s1 * uy - ww * ux, zi, b);
            int span = i1 - i0, stop = Math.min(i0 + KNOT, count);
            double dx = span > 0 ? (b[0] - a[0]) / span : 0, dy = span > 0 ? (b[1] - a[1]) / span : 0;
            for (int k = i0; k < stop; k++) {
                double gx = a[0] + dx * (k - i0), gy = a[1] + dy * (k - i0);
                int c = (int) Math.floor(gx), r = (int) Math.floor(gy);
                double v = Double.NaN;
                if (r >= 0 && c >= 0 && r + 1 < H && c + 1 < W) {
                    double fx = gx - c, fy = gy - r; int o = r * W + c;
                    double nw = m[o], ne = m[o + 1], sw = m[o + W], se = m[o + W + 1];
                    double north = nw + (ne - nw) * fx, south = sw + (se - sw) * fx;
                    v = north + (south - north) * fy;
                    if (nw < -9000 || ne < -9000 || sw < -9000 || se < -9000) v = Double.NaN;
                }
                ss[base + k] = sa + k * step; hh[base + k] = v;
            }
        }
        return base + count;
    }

    static void hull(double[] ss, double[] hh, int nAll, int first, int sub, double[] hs, double[] hg, float[] out) {
        int n = 0, oi = 0; double lastGap = -1e18;
        for (int i = 0; i < nAll; i++) {
            double s = ss[i], hv = hh[i];
            boolean obs = i >= first && (i - first) % sub == 0;
            if (Double.isNaN(hv)) { lastGap = s; if (obs) out[oi++] = Float.NaN; continue; }
            double g = hv - C * s * s;
            while (n >= 2 && (hg[n - 1] - hg[n - 2]) * (s - hs[n - 2]) <= (g - hg[n - 2]) * (hs[n - 1] - hs[n - 2])) n--;
            if (obs) {
                double G = g + EYE, best = Double.NEGATIVE_INFINITY;
                if (n > 0) {
                    int j = n - 1; best = (hg[j] - G) / (s - hs[j]);
                    while (j > 0) { double t = (hg[j - 1] - G) / (s - hs[j - 1]); if (t >= best) { best = t; j--; } else break; }
                }
                double tanh = best - 2 * C * s;
                if (lastGap > -1e17) { double dm = s - lastGap; if (tanh < (HMAX - (hv + EYE) - C * dm * dm) / dm) tanh = Double.NaN; }
                out[oi++] = (float) tanh;
            }
            hs[n] = s; hg[n] = g; n++;
        }
    }
}
```
