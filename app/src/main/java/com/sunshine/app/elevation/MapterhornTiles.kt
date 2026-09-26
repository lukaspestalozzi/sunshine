package com.sunshine.app.elevation

import com.sunshine.core.TileKey

/**
 * Everything specific to the elevation source, Mapterhorn (design D1): Terrarium-encoded lossless
 * WebP tiles of 512 px, used at zoom 12 (~13 m pixels in the Alps).
 */
object MapterhornTiles {
    const val ZOOM = 12
    const val TILE_SIZE = 512
    const val ATTRIBUTION_URL = "https://mapterhorn.com/attribution/"

    fun url(key: TileKey): String = "https://tiles.mapterhorn.com/${key.zoom}/${key.x}/${key.y}.webp"
}
