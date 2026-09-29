package com.sunshine.app.map

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// The map's colours under the overlay (map-view spec, "Map colours under the heatmap"; design D10 of
// add-sun-exposure-heatmap).
class MapColoursTest {
    @Test
    fun `the map is greyscale under the heatmap`() {
        assertEquals(-1f, mapSaturation(OverlayOption.SUN_HOURS))
    }

    @Test
    fun `the map keeps its colours under sun and shade and while the overlay is off`() {
        assertEquals(0f, mapSaturation(OverlayOption.SUN_AND_SHADE))
        assertEquals(0f, mapSaturation(OverlayOption.OFF))
    }
}
