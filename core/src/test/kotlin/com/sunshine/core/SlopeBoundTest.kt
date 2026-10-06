package com.sunshine.core

import kotlin.math.sqrt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// The largest slope missing terrain could have, shared by the point tracer and the sweep
// (point-sunshine spec, "Panel and overlay agree with missing data").
class SlopeBoundTest {
    @Test
    fun `below the height bound the slope bound is that of the gap's distance`() {
        val d = 3000.0

        assertEquals((4810.0 - 2000.0 - CURVATURE * d * d) / d, slopeBound(d, eyeHeight = 2000.0, heightBound = 4810.0), 1e-12)
    }

    @Test
    fun `above the height bound the slope bound rises up to its peak`() {
        val eye = 4812.0
        val peak = sqrt((eye - 4810.0) / CURVATURE)

        val near = slopeBound(1000.0, eye, 4810.0)

        assertEquals((4810.0 - eye - CURVATURE * peak * peak) / peak, near, 1e-12)
        assertTrue(near > (4810.0 - eye - CURVATURE * 1000.0 * 1000.0) / 1000.0, "terrain beyond the gap could rise higher")
        val far = 2 * peak
        assertEquals((4810.0 - eye - CURVATURE * far * far) / far, slopeBound(far, eye, 4810.0), 1e-12)
    }
}
