package com.sunshine.app.map

import com.sunshine.app.R
import com.sunshine.core.GeoPoint
import com.sunshine.core.MapArea
import com.sunshine.core.SunPosition
import com.sunshine.core.SunShadeSweep
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// Notices of the sun-shade overlay (sun-shade-overlay spec, "Overlay coverage", "Overlay updates") and of
// the heatmap (sun-exposure-heatmap spec, "Heatmap updates").
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

    @Test
    fun `in sun hours, computing says sun hours are computed, whether or not a heatmap is kept`() {
        assertEquals(R.string.heatmap_computing, heatmapNotice(HeatmapUiState.Computing(kept = null)))
        assertEquals(R.string.heatmap_computing, heatmapNotice(HeatmapUiState.Computing(kept = heatmap())))
    }

    @Test
    fun `in sun hours, a ready heatmap or off shows no notice, and zoomed out asks to zoom in`() {
        assertEquals(null, heatmapNotice(heatmap()))
        assertEquals(null, heatmapNotice(HeatmapUiState.Off))
        assertEquals(R.string.overlay_zoom_in, heatmapNotice(HeatmapUiState.ZoomedOut))
    }

    @Test
    fun `the mode picks the notice, so computing sun and shade never shows in sun hours`() {
        val computing = OverlayUiState.Computing(kept = null)

        assertEquals(R.string.overlay_computing, notice(OverlayMode.SUN_AND_SHADE, computing, heatmap(), NOON))
        assertEquals(null, notice(OverlayMode.SUN_HOURS, computing, heatmap(), NOON))
        assertEquals(R.string.heatmap_computing, notice(OverlayMode.SUN_HOURS, computing, HeatmapUiState.Computing(null), NOON))
    }

    private fun heatmap(): HeatmapUiState.Ready {
        val area = MapArea(GeoPoint(46.6863, 7.8632), 12.0, 20.0, 20.0)
        val hours = SunHours(area, 20, 20, 288, ShortArray(400), ShortArray(400))
        val bands = HeatmapBands(Duration.ofHours(8))
        return HeatmapUiState.Ready(hours, NOON.toLocalDate(), bands, renderSunHours(hours, bands))
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
