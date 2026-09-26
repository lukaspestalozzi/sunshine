package com.sunshine.app.map

import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class SunPanelWidthTest {
    // In landscape the panel (bottom-start, 8 dp padding) must end left of the 32 dp centre crosshair,
    // with an 8 dp gap: at most width / 2 - start inset - 32 dp.
    @ParameterizedTest(name = "{0} x {1} dp, start inset {2} dp")
    @CsvSource(
        "411, 891,  0, 360", // portrait: full cap, the panel sits below the crosshair
        "891, 411,  0, 360", // wide landscape: the cap already ends left of the centre
        "640, 360,  0, 288", // small landscape
        "640, 360, 48, 240", // small landscape with a navigation bar on the left
        " 60,  50, 48,   0", // absurdly small window: no negative width
    )
    fun `panel ends left of the crosshair in landscape`(
        width: Float,
        height: Float,
        startInset: Float,
        expected: Float,
    ) {
        assertEquals(expected.dp, sunPanelMaxWidth(width.dp, height.dp, startInset.dp))
    }
}
