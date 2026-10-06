package com.sunshine.app.map

import com.sunshine.core.CombinedGrid
import com.sunshine.core.GeoPoint
import com.sunshine.core.HeightTile
import com.sunshine.core.MapArea
import com.sunshine.core.ShadeGrid
import com.sunshine.core.StepGrid
import com.sunshine.core.SunPosition
import com.sunshine.core.SunShadeSweep
import com.sunshine.core.Sunshine
import com.sunshine.core.TileKey
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sinh
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// Design D9 of add-sun-shade-overlay.
class RenderOverlayTest {
    @Test
    fun `shade is the blue-grey tint everywhere at night`() {
        val image = renderOverlay(grid(flat, SunPosition(180.0, -10.0, false)))

        assertEquals(AREA.widthDp.toInt(), image.width)
        assertEquals(AREA.heightDp.toInt(), image.height)
        assertEquals(setOf(SHADE_ARGB), image.pixels.toSet())
    }

    @Test
    fun `sun is transparent`() {
        val image = renderOverlay(grid(flat, SunPosition(180.0, 30.0, true)))

        assertEquals(setOf(0), image.pixels.toSet())
    }

    @Test
    fun `unknown is grey stripes where x + y mod 8 is below 2`() {
        val image = renderOverlay(grid(null, SunPosition(180.0, 30.0, true)))

        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val expected = if ((x + y) % 8 < 2) UNKNOWN_ARGB else 0
                assertEquals(expected, image.pixels[y * image.width + x], "pixel $x, $y")
            }
        }
    }

    @Test
    fun `a grid for a sun in the south-east lands north-up on the right pixels`() {
        // A 1500 m cliff along the area's centre latitude, high to the south; the sun at 45° in
        // azimuth 135° shades the low ground north of the edge up to 1500 · cos 45° = 1061 m.
        val cliff = { lat: Double, _: Double -> if (lat < CENTER.latitude) 2000.0 else 500.0 }
        val image = renderOverlay(grid(cliff, SunPosition(135.0, 45.0 - 0.266, true)))
        val column = image.width / 2

        for ((north, expected) in listOf(-200.0 to 0, 300.0 to SHADE_ARGB, 900.0 to SHADE_ARGB, 1250.0 to 0)) {
            assertEquals(expected, image.pixels[rowAt(north) * image.width + column], "$north m north of the edge")
        }
        // The shadow's northern edge is 1061 m north of the cliff, within a cell (2 px) and a pixel.
        val edge = rowAt(1061.0)
        assertEquals(0, image.pixels[(edge - 3) * image.width + column])
        assertEquals(SHADE_ARGB, image.pixels[(edge + 3) * image.width + column])
    }

    // Design D8 of polish-overlay: the raster in one pass gives the pixels of one lookup per pixel.
    @Test
    fun `the image equals the per-pixel reference for sun, shade and unknown`() {
        val cliff = { lat: Double, _: Double -> if (lat < CENTER.latitude) 2000.0 else 500.0 }
        for (grid in listOf(grid(cliff, SunPosition(135.0, 44.734, true)), grid(null, SunPosition(180.0, 30.0, true)))) {
            assertArrayEquals(reference(grid).pixels, renderOverlay(grid).pixels)
        }
    }

    @Test
    fun `a phone-sized image renders in at most half the time of the per-pixel reference`() {
        val grid = grid(flat, SunPosition(135.0, 20.0, true), MapArea(CENTER, 12.0, 411.0, 891.0))
        repeat(WARM_UP) {
            reference(grid)
            renderOverlay(grid)
        }

        val referenceNanos = fastest { reference(grid) }
        val nanos = fastest { renderOverlay(grid) }

        assertTrue(nanos <= referenceNanos / 2, "${nanos / 1e6} ms against ${referenceNanos / 1e6} ms")
    }

    // Design D3 of overlay-pan-reuse: an earlier grid combined with the part a pan uncovered.
    @Test
    fun `a combined grid's image equals the per-pixel reference`() {
        val cliff = { lat: Double, _: Double -> if (lat < CENTER.latitude) 2000.0 else 500.0 }
        val sun = SunPosition(135.0, 44.734, true)
        val new = AREA.eastBy(AREA.widthDp / 2)
        val part = AREA.eastBy(AREA.widthDp * 3 / 4).copy(widthDp = AREA.widthDp / 2)
        val combined = CombinedGrid(new, grid(cliff, sun), listOf(grid(null, sun, part)))

        val image = renderOverlay(combined)

        assertArrayEquals(reference(combined).pixels, image.pixels)
        assertEquals(setOf(0, SHADE_ARGB, UNKNOWN_ARGB), image.pixels.toSet())
    }

    @Test
    fun `a phone-sized combined image renders in at most one and a half times a single grid's`() {
        val old = MapArea(CENTER, 12.0, 411.0, 891.0)
        val new = old.eastBy(old.widthDp / 2)
        val sun = SunPosition(135.0, 20.0, true)
        val single = grid(flat, sun, new)
        val combined =
            CombinedGrid(
                new,
                grid(flat, sun, old),
                listOf(
                    grid(
                        flat,
                        sun,
                        old.eastBy(old.widthDp * 3 / 4).copy(
                            widthDp =
                                old.widthDp / 2,
                        ),
                    ),
                ),
            )
        repeat(WARM_UP) {
            renderOverlay(single)
            renderOverlay(combined)
        }

        val singleNanos = fastest { renderOverlay(single) }
        val nanos = fastest { renderOverlay(combined) }

        assertTrue(nanos <= 1.5 * singleNanos, "${nanos / 1e6} ms against ${singleNanos / 1e6} ms")
    }

    // The image as rendered before design D8: one lookup per pixel.
    private fun reference(grid: StepGrid): OverlayImage {
        val raster = OverlayRaster(grid.area)
        val pixels =
            IntArray(raster.width * raster.height) { i ->
                val x = i % raster.width
                val y = i / raster.width
                when (grid.stateAt(raster.latitudes[y], raster.longitudes[x])) {
                    Sunshine.SHADE -> SHADE_ARGB
                    Sunshine.UNKNOWN -> if ((x + y) % 8 < 2) UNKNOWN_ARGB else 0
                    Sunshine.SUN, null -> 0
                }
            }
        return OverlayImage(raster.width, raster.height, pixels, grid.area.corners())
    }

    private fun fastest(render: () -> Unit): Long =
        (1..RUNS).minOf {
            val start = System.nanoTime()
            render()
            System.nanoTime() - start
        }

    // Image row of the point [north] metres north of the centre latitude (north-up, 1 px per dp).
    private fun rowAt(north: Double): Int {
        val world = 512.0 * (1 shl AREA.zoom.toInt())

        fun mercatorY(lat: Double) = (0.5 - ln((1 + sin(Math.toRadians(lat))) / (1 - sin(Math.toRadians(lat)))) / (4 * PI)) * world
        val top = mercatorY(CENTER.latitude) - AREA.heightDp / 2
        return (mercatorY(CENTER.latitude + north / METRES_PER_DEGREE) - top).toInt()
    }

    private val flat = { _: Double, _: Double -> 700.0 }

    // This area moved [dp] dp east.
    private fun MapArea.eastBy(dp: Double) =
        copy(center = GeoPoint(center.latitude, center.longitude + dp * 360.0 / (512.0 * 2.0.pow(zoom))))

    private fun grid(
        height: ((Double, Double) -> Double)?,
        sun: SunPosition,
        area: MapArea = AREA,
    ): ShadeGrid {
        val sweep = SunShadeSweep(area, sun)
        val built = mutableMapOf<TileKey, HeightTile>()
        val tiles =
            object : AbstractMap<TileKey, HeightTile?>() {
                override val entries: Set<Map.Entry<TileKey, HeightTile?>> get() = throw UnsupportedOperationException()

                override fun get(key: TileKey): HeightTile? = height?.let { built.getOrPut(key) { tile(key, it) } }

                override fun containsKey(key: TileKey) = true
            }
        sweep.tiles(sweep.groundTiles().associateWith { tiles[it] })
        return sweep.assemble(listOf(sweep.compute(tiles)))
    }

    private fun tile(
        key: TileKey,
        height: (Double, Double) -> Double,
    ): HeightTile {
        val n = ((1L shl key.zoom) * 512).toDouble()
        val lons = DoubleArray(512) { c -> (key.x.toLong() * 512 + c + 0.5) / n * 360.0 - 180.0 }
        val lats = DoubleArray(512) { r -> Math.toDegrees(atan(sinh(PI * (1 - 2 * (key.y.toLong() * 512 + r + 0.5) / n)))) }
        return HeightTile.fromMetres(512, FloatArray(512 * 512) { i -> height(lats[i / 512], lons[i % 512]).toFloat() })
    }

    private companion object {
        val CENTER = GeoPoint(46.6, 7.9)
        val AREA = MapArea(CENTER, zoom = 13.0, widthDp = 80.0, heightDp = 400.0)
        val METRES_PER_DEGREE = Math.toRadians(1.0) * 6_371_000.0
        const val WARM_UP = 5
        const val RUNS = 5

        // #455A64 and #9E9E9E, opaque: the overlay layer applies the chosen opacity (design D5 of
        // add-settings; the colours are those of design D10 of add-sun-exposure-heatmap).
        const val SHADE_ARGB = 0xFF455A64.toInt()
        const val UNKNOWN_ARGB = 0xFF9E9E9E.toInt()
    }
}
