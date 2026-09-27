package com.sunshine.core

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt

/** Builds [HeightTile]s from a height function of latitude/longitude, sampled at pixel centres. */
internal class SyntheticTerrain(
    private val tileSize: Int = 512,
    private val height: (latitude: Double, longitude: Double) -> Double,
) {
    private val built = mutableMapOf<TileKey, HeightTile>()
    val requested = mutableListOf<TileKey>()

    fun tile(key: TileKey): HeightTile {
        requested += key
        return built.getOrPut(key) {
            val n = ((1L shl key.zoom) * tileSize).toDouble()
            val lons = DoubleArray(tileSize) { c -> (key.x.toLong() * tileSize + c + 0.5) / n * 360.0 - 180.0 }
            val lats =
                DoubleArray(tileSize) { r ->
                    Math.toDegrees(atan(sinh(PI * (1 - 2 * (key.y.toLong() * tileSize + r + 0.5) / n))))
                }
            val metres = FloatArray(tileSize * tileSize) { i -> height(lats[i / tileSize], lons[i % tileSize]).toFloat() }
            HeightTile.fromMetres(tileSize, metres)
        }
    }

    /** Runs [tracer] to completion; [missing] tiles are reported as unavailable. */
    fun run(
        tracer: HorizonTracer,
        missing: (TileKey) -> Boolean = { false },
    ): HorizonProfile? {
        tracer.start(tracer.groundTiles().associateWith { if (missing(it)) null else tile(it) })
        while (!tracer.isDone) {
            tracer.advance(tracer.nextTiles().associateWith { if (missing(it)) null else tile(it) })
        }
        return tracer.profile()
    }

    companion object {
        const val EARTH_RADIUS = 6_371_000.0

        /** Great-circle distance in metres, as used by the tracer. */
        fun distance(
            lat1: Double,
            lon1: Double,
            lat2: Double,
            lon2: Double,
        ): Double {
            val p1 = Math.toRadians(lat1)
            val p2 = Math.toRadians(lat2)
            val dp = p2 - p1
            val dl = Math.toRadians(lon2 - lon1)
            val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
            return 2 * EARTH_RADIUS * asin(sqrt(a))
        }
    }
}
