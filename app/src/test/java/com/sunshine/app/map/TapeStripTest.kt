package com.sunshine.app.map

import com.sunshine.core.Sunshine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// The states of the time tape's strip (time-selection spec, "Time tape strip"; design D5 of polish-ui).
class TapeStripTest {
    @Test
    fun `night wins over the source, a missing state is not computed yet`() {
        val night = booleanArrayOf(true, true, false, false, false, false)
        val source = listOf(Sunshine.SUN, null, Sunshine.SUN, Sunshine.SHADE, Sunshine.UNKNOWN, null)

        val strip = tapeStrip(night) { source[it] }

        assertEquals(
            listOf(
                StripState.NIGHT,
                StripState.NIGHT,
                StripState.SUN,
                StripState.SHADE,
                StripState.UNKNOWN,
                StripState.NOT_COMPUTED,
            ),
            strip,
        )
    }

    @Test
    fun `a 10-minute day maps onto 5-minute tape steps`() {
        // 14:10 and 14:15 take the state of the heatmap's step at 14:10.
        assertEquals(85, sourceStep(tapeMinutes = 850f, sourceStepMinutes = 10))
        assertEquals(85, sourceStep(tapeMinutes = 855f, sourceStepMinutes = 10))
        assertEquals(86, sourceStep(tapeMinutes = 860f, sourceStepMinutes = 10))
        assertEquals(0, sourceStep(tapeMinutes = 0f, sourceStepMinutes = 10))
    }
}
