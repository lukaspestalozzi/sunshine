package com.sunshine.app.map

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// The three-way overlay toggle (sun-shade-overlay spec, "Overlay toggle"; design D7 of
// add-sun-exposure-heatmap).
class OverlayToggleTest {
    @Test
    fun `the toggle shows off while the overlay is off, whatever the mode`() {
        assertEquals(OverlayOption.OFF, overlayOption(isOn = false, mode = OverlayMode.SUN_AND_SHADE))
        assertEquals(OverlayOption.OFF, overlayOption(isOn = false, mode = OverlayMode.SUN_HOURS))
    }

    @Test
    fun `the toggle shows the mode while the overlay is on`() {
        assertEquals(OverlayOption.SUN_AND_SHADE, overlayOption(isOn = true, mode = OverlayMode.SUN_AND_SHADE))
        assertEquals(OverlayOption.SUN_HOURS, overlayOption(isOn = true, mode = OverlayMode.SUN_HOURS))
    }
}
