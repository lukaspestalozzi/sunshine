# Overlay after a pan: what computing only the uncovered part would save

Spike for the "overlay on pans" question (roadmap #10, `polish-overlay`), measured 2026-10-06 after
the user's device measurement showed about 92 s of background CPU per `Sun & shade` day after every
pan (zoom 11, `Normal`, 288 steps of which 144 at night).

## Question

After a pan by half a screen, half of the new screen is already covered by the earlier day's grids.
How much of a full recompute does computing only the uncovered half cost? And what does a margin
around the screen cost per fresh view?

## Method (FACTS)

- Real `SunShadeSweep.compute` of `core`, single-threaded, desktop JVM, fastest of 3 runs after 2
  warm-up runs; tiles built beforehand, so tile loading and planning are excluded.
- Area: a phone screen, 411 × 891 dp at map zoom 12, centred at 46.59° N, 7.91° E; `Normal` (2 dp
  cells). Height bound 4810 m (the real one), so the upwind cut is realistic.
- Terrain: synthetic Alpine-like ridges, valleys at about 700 m, crests up to about 3400 m, a few km
  apart (code below).
- Pans by half a screen (445 dp north or south, 205 dp east or west). The uncovered part is the
  half-screen rectangle that the earlier screen does not cover; it is swept as an area of its own,
  which is how a partial computation would work.
- Margin: one sweep of 1.5 × 1.5 screens (a quarter screen per side).

## Results (FACTS)

| Sun | Full screen | Towards the sun | Away from it | Across (E) | Across (W) | Margin ¼ per side |
|---|---|---|---|---|---|---|
| 180° / 20° | 105 ms | 55 % | 43 % | 51 % | 45 % | 210 % |
| 180° / 8° | 113 ms | 60 % | 59 % | 61 % | 47 % | 215 % |
| 135° / 20° | 148 ms | 55 % | 58 % | 59 % | 62 % | 181 % |
| 240° / 12° | 159 ms | 49 % | 52 % | 63 % | 53 % | 196 % |

Percentages: the uncovered half's sweep against a full sweep of the panned screen (margin: against
the plain screen). A first run of the 180° rows gave 48–72 %; repeated runs of the same full sweep
varied by up to ±15 %.

## Conclusions (THEORIES until measured on the phone)

- Computing only the uncovered half costs about **43–63 %** of a full recompute, i.e. it saves
  roughly **40–55 %** per half-screen pan, whatever the pan direction relative to the sun. The
  estimate before the spike (10–50 %, worst along the sun) was too pessimistic: the far-upwind part
  of the lines is bundled and cheap, so the visible part dominates.
- On the phone this would turn about 92 s of background CPU per pan into about 40–55 s.
- A margin of a quarter screen per side costs about **1.8–2.4×** per fresh view, for free pans of up
  to a quarter screen.
- Not measured: the cost of combining two partial days (two images, heatmap counts from two days,
  unknown cells, the cache of days), and smaller or larger pans.

## Code

```kotlin
package com.sunshine.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sinh
import org.junit.jupiter.api.Test

// Temporary measurement, not committed: what computing only the uncovered part of the screen after
// a pan would save (investigations/overlay-pan-reuse.md).
class PanReuseSpike {
    @Test
    fun measure() {
        val rows = mutableListOf<String>()
        for ((azimuth, elevation) in listOf(180.0 to 20.0, 180.0 to 8.0, 135.0 to 20.0, 240.0 to 12.0)) {
            val sun = SunPosition(azimuth, elevation, true)
            val full = time(VIEW, sun)
            rows += "sun $azimuth° / $elevation°: full screen ${"%.0f".format(full)} ms"
            for ((name, dx, dy) in PANS) {
                val panned = moved(VIEW, dx * VIEW.widthDp, dy * VIEW.heightDp)
                val newFull = time(panned, sun)
                // The uncovered half of the panned screen.
                val uncovered =
                    if (dx != 0.0) {
                        moved(VIEW.copy(widthDp = VIEW.widthDp / 2), (dx + 0.25 * Math.signum(dx)) * VIEW.widthDp, 0.0)
                    } else {
                        moved(VIEW.copy(heightDp = VIEW.heightDp / 2), 0.0, (dy + 0.25 * Math.signum(dy)) * VIEW.heightDp)
                    }
                val part = time(uncovered, sun)
                rows += "  pan $name: full ${"%.0f".format(newFull)} ms, uncovered half ${"%.0f".format(part)} ms " +
                    "(${"%.0f".format(100 * part / newFull)} %)"
            }
            val margin = time(VIEW.copy(widthDp = VIEW.widthDp * 1.5, heightDp = VIEW.heightDp * 1.5), sun)
            rows += "  margin of 1/4 screen per side: ${"%.0f".format(margin)} ms (${"%.0f".format(100 * margin / full)} %)"
        }
        println(rows.joinToString("\n", prefix = "PAN-REUSE\n"))
    }

    // Fastest of a few single-threaded sweeps, tiles built beforehand.
    private fun time(
        area: MapArea,
        sun: SunPosition,
    ): Double {
        val sweep = SunShadeSweep(area, sun)
        sweep.tiles(sweep.groundTiles().associateWith(ALPS::tile))
        val tiles = recording(ALPS, mutableSetOf())
        repeat(2) { sweep.compute(tiles) }
        return (1..3).minOf {
            val start = System.nanoTime()
            sweep.compute(tiles)
            (System.nanoTime() - start) / 1e6
        }
    }

    // [area] moved [east] and [south] dp on the Web Mercator map.
    private fun moved(
        area: MapArea,
        east: Double,
        south: Double,
    ): MapArea {
        val world = 512.0 * 2.0.pow(area.zoom)
        val sinLat = sin(Math.toRadians(area.center.latitude))
        val y = (0.5 - ln((1 + sinLat) / (1 - sinLat)) / (4 * PI)) * world + south
        val latitude = Math.toDegrees(atan(sinh(PI * (1 - 2 * y / world))))
        val longitude = area.center.longitude + east / world * 360.0
        return area.copy(center = GeoPoint(latitude, longitude))
    }

    private companion object {
        val VIEW = MapArea(GeoPoint(46.59, 7.91), zoom = 12.0, widthDp = 411.0, heightDp = 891.0)

        // Half a screen towards the sun (south), away from it, and across it.
        val PANS =
            listOf(
                Triple("towards the sun (S)", 0.0, 0.5),
                Triple("away from the sun (N)", 0.0, -0.5),
                Triple("across the sun (E)", 0.5, 0.0),
                Triple("across the sun (W)", -0.5, 0.0),
            )

        // Alpine-like: valleys at about 700 m, crests up to about 3400 m, a few km apart.
        val ALPS =
            SyntheticTerrain { lat, lon ->
                val a = abs(sin(lat * 140.0 + lon * 35.0))
                val b = abs(sin(lat * 47.0 - lon * 90.0))
                700.0 + 1800.0 * a * a * a + 900.0 * b * b
            }
    }
}
```
