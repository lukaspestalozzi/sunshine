# Tasks

## 1. Reference fixtures

- [x] 1.1 Create the `core` test fixture of design D11. For each oracle point of `specs/elevation-data/spec.md` (Interlaken, Kleine Scheidegg, the four-tile corner, Jungfrau), fetch the needed zoom-12 tiles once from `https://tiles.mapterhorn.com`, extract the RGB values of the 2 × 2 samples around the point (pixel centres, `x · 2^12 · 512 − 0.5`), and write them to `core/src/test/kotlin/com/sunshine/core/ElevationFixtures.kt` with the tile keys, fetch date and source URL in a comment. Add a short "Fixture extraction" note with the script used to `investigations/dem-source-evaluation.md`. Verify: the fixture holds 16 samples (the corner point's four lie in four tiles), and decoding them with the Terrarium formula and interpolating gives Interlaken 568.0, Kleine Scheidegg 2061.3, corner 1132.35 and Jungfrau 4151.5 (±0.1 m, the values in design D11).

## 2. core: tiles, decoding, interpolation

- [x] 2.1 Write `TerrariumTest` first: `terrariumHeights` maps ARGB `0xFF80_0000` to 0.0 m, `0xFF82_3800` to 568.0 m and `0xFF7F_FF80` to −0.5 m. Run it and see it fail, then implement `terrariumHeights` (design D5, D7). Verify: `./gradlew :core:test --tests "*TerrariumTest*"` passes.
- [x] 2.2 Write `ElevationTilesTest` first for `tilesFor`: Interlaken (a single tile, 12/2137/1445); 46.55886° N, 7.91016° E (4 tiles: 12/2137–2138/1447–1448); 46.6863° N, 7.91016° E (2 horizontally adjacent tiles: 12/2137–2138/1445); latitude 85.1° (empty set); longitude 180.0° (wraps to x 0; 179.9999° is still more than half a pixel from the antimeridian). Run it and see it fail, then implement `TileKey`, `HeightTile` and `tilesFor`. Verify: `./gradlew :core:test --tests "*ElevationTilesTest*"` passes.
- [x] 2.3 Write `ElevationInterpolationTest` first: the four spec scenarios on the fixture of 1.1 (Interlaken 568.0 ±1, Kleine Scheidegg 2061.3 ±1, corner 1132.1 ±1, Jungfrau within [4147.8, 4158.8]), plus a synthetic 2 × 2 tile case with exact expected values at a pixel centre and midway between centres. Run it and see it fail, then implement `interpolateElevation`. Verify: `./gradlew :core:test --tests "*ElevationInterpolationTest*"` passes.

## 3. app: fetching and repository

- [x] 3.1 Add `app/elevation/MapterhornTiles.kt` (design D1: base URL, tile size 512, zoom 12, attribution URL, `url(key)`) and `DemTileFetcher` (design D3, D8: DEM `OkHttpClient` with `UserAgentInterceptor`, 100 MiB `Cache` in `cacheDir/dem-tiles`, 10 s / 20 s timeouts, `FORCE_CACHE` retry, `null` on 404/5xx/IO failure). Write `DemTileFetcherTest` first, with a stub `Interceptor` answering 200, 404 and 500 and throwing `IOException`: check the User-Agent `Sunshine/0.1.0 (Android; com.sunshine.app)`, the URL `https://tiles.mapterhorn.com/12/2137/1445.webp`, and `null` for 404/500/IO. Verify: `./gradlew :app:testDebugUnitTest --tests "*DemTileFetcherTest*"` passes.
- [x] 3.2 Write `ElevationRepositoryTest` first (fake fetch and decode functions): known elevation from fixture tiles; one of two required tiles missing → `Unknown`; decode failure → `Unknown`; a second call for a point in the same tile makes no fetch; `cachedElevation` is `null` before loading and `Known` after. Then implement `ElevationRepository` with the 8-tile LRU (design D8). Verify: `./gradlew :app:testDebugUnitTest --tests "*ElevationRepositoryTest*"` passes.
- [x] 3.3 Implement the Android decode function (`BitmapFactory`, `ARGB_8888`, `getPixels`; `null` if decoding fails) and wire the DEM client and repository in `SunshineApp` (design D5, D8, D9). Verify: `./gradlew :app:assembleDebug` succeeds; bit-exactness is checked on a device in 5.2.

## 4. app: ViewModel, panel and attribution

- [x] 4.1 Write the `MapViewModel` elevation tests first (test dispatcher, fake repository functions): initial `Loading` then `Known`; the memory fast path emits `Known` without `Loading`; offline with an uncached tile gives `Unknown`; `isOnline` false → true reloads within the test's virtual 5 s; after a camera move the previous location's value is never emitted for the new one. Then add `elevation: StateFlow<ElevationState>` and the repository parameter (design D8), and pass the repository through the factory in `MapScreen.kt`. Verify: `./gradlew :app:testDebugUnitTest --tests "*MapViewModel*"` passes.
- [x] 4.2 Write `formatAltitude` tests first in `SunFormatTest` (it returns the value; the label `Altitude` comes from `strings.xml` like the other panel labels): 1634.43 → `1634 m`; 567.5 → `568 m`; −0.4 → `0 m`; 2061.31 under default locale de-CH → `2061 m`; `Loading` → `…`; `Unknown` → `unknown`. Then implement it in `SunFormat.kt` and show the row in `SunPanel`, independently of `sun != null` (design D10). Verify: `./gradlew :app:testDebugUnitTest --tests "*SunFormatTest*"` passes.
- [x] 4.3 Add the elevation attribution `Label` under the map attribution in `MapLabels`, text `Elevation: © Mapterhorn and its sources` in `strings.xml`, opening `https://mapterhorn.com/attribution/` via `LocalUriHandler` (design D10). Verify: `./gradlew :app:assembleDebug` succeeds; visibility and the link are checked on a device in 5.2.
- [ ] 4.4 Update `docs/roadmap.md` entry #3: its open decisions are resolved in this change's design.md (source Mapterhorn z12, tile endpoint, on-demand disk cache). Update `CLAUDE.md` where it describes the `app` module if the new `elevation` package needs a mention. Verify: `grep -n "add-elevation-data" docs/roadmap.md` shows the row with "resolved in its design.md".

## 5. Integration

- [ ] 5.1 Run `./scripts/verify-local.sh` and `openspec validate --all --strict`. Verify: ktlint, Android lint, all unit tests and the debug APK build pass, and validation reports no failures.
- [ ] 5.2 On-device check with the CI APK (design Verification strategy). Expected:
  - The panel shows `Altitude 568 m` at Interlaken, `Altitude 2061 m` at Kleine Scheidegg and `Altitude 1634 m` at Mürren (±1 m); this also checks bit-exact decoding.
  - Panning to a new area shows `Altitude …`, then a value.
  - In flight mode, an unvisited location shows `Altitude unknown`, and a visited one keeps its value.
  - Turning flight mode off loads the unknown value within 5 s.
  - Both attributions are visible, and tapping the elevation attribution opens `https://mapterhorn.com/attribution/`.
