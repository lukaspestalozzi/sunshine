package com.sunshine.app.map

import com.sunshine.app.R
import com.sunshine.core.GeoPoint
import com.sunshine.core.MapArea
import com.sunshine.core.SunPosition
import com.sunshine.core.SunShadeSweep
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// Notices of the sun-shade overlay (sun-shade-overlay spec, "Overlay coverage", "Overlay updates").
class OverlayNoticesTest {
    @Test
    fun `zoomed out asks to zoom in`() {
        assertEquals(R.string.overlay_zoom_in, overlayNotice(OverlayUiState.ZoomedOut, NOON))
    }

    @Test
    fun `computing without a kept overlay says so`() {
        assertEquals(R.string.overlay_computing, overlayNotice(OverlayUiState.Computing(kept = null), NOON))
    }

    @Test
    fun `computing while keeping another time's overlay says so`() {
        assertEquals(R.string.overlay_computing, overlayNotice(OverlayUiState.Computing(kept = ready(NOON)), NOON.plusHours(3)))
    }

    @Test
    fun `computing while keeping the selected time's overlay, after a pan, shows no notice`() {
        assertEquals(null, overlayNotice(OverlayUiState.Computing(kept = ready(NOON)), NOON))
    }

    @Test
    fun `off shows no notice`() {
        assertEquals(null, overlayNotice(OverlayUiState.Off, NOON))
    }

    private fun ready(time: ZonedDateTime): OverlayUiState.Ready {
        val sweep = SunShadeSweep(MapArea(GeoPoint(46.6863, 7.8632), 12.0, 20.0, 20.0), SunPosition(180.0, -10.0, false))
        val grid = sweep.night(sweep.groundTiles().associateWith { null })
        return OverlayUiState.Ready(grid, time, renderOverlay(grid))
    }

    private companion object {
        val NOON: ZonedDateTime = ZonedDateTime.of(2025, 12, 21, 12, 0, 0, 0, ZoneId.of("Europe/Zurich"))
    }
}
