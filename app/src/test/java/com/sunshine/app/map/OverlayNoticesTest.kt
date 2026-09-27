package com.sunshine.app.map

import com.sunshine.app.R
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// Notices of the sun-shade overlay (sun-shade-overlay spec, "Overlay coverage", "Overlay updates").
class OverlayNoticesTest {
    @Test
    fun `zoomed out asks to zoom in`() {
        assertEquals(R.string.overlay_zoom_in, overlayNotice(OverlayUiState.ZoomedOut))
    }

    @Test
    fun `computing without a kept overlay says so`() {
        assertEquals(R.string.overlay_computing, overlayNotice(OverlayUiState.Computing(kept = null)))
    }

    @Test
    fun `off shows no notice`() {
        assertEquals(null, overlayNotice(OverlayUiState.Off))
    }
}
