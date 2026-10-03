package com.sunshine.app.map

import com.sunshine.app.map.LocationAccess.APPROXIMATE
import com.sunshine.app.map.LocationAccess.NONE
import com.sunshine.app.map.LocationAccess.PRECISE
import com.sunshine.app.map.LocationAction.AskPermission
import com.sunshine.app.map.LocationAction.Centre
import com.sunshine.app.map.LocationButtonState.IDLE
import com.sunshine.app.map.LocationButtonState.READY
import com.sunshine.app.map.LocationButtonState.WAITING
import com.sunshine.app.map.LocationNotice.ACCESS_OFF
import com.sunshine.app.map.LocationNotice.APPROXIMATE_ONLY
import com.sunshine.app.map.LocationNotice.SWITCHED_OFF
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

// The table of design D3 of add-gps-location, one case per row.
class LocationButtonTest {
    @ParameterizedTest
    @EnumSource(LocationButtonState::class)
    fun `a tap without access asks for it and keeps the state`(state: LocationButtonState) {
        assertEquals(LocationStep(state, listOf(AskPermission)), state.onTap(NONE, locationOn = true))
    }

    @ParameterizedTest
    @EnumSource(LocationButtonState::class)
    fun `access refused for good shows the access notice and goes idle`(state: LocationButtonState) {
        assertEquals(
            LocationStep(IDLE, listOf(LocationAction.Notice(ACCESS_OFF))),
            state.onPermissionAnswer(NONE, locationOn = true, dialogAvailable = false),
        )
    }

    @ParameterizedTest
    @EnumSource(LocationButtonState::class)
    fun `access refused while Android would ask again changes nothing`(state: LocationButtonState) {
        assertEquals(LocationStep(state, emptyList()), state.onPermissionAnswer(NONE, locationOn = true, dialogAvailable = true))
    }

    @Test
    fun `access allowed in the dialog handles the tap`() {
        assertEquals(LocationStep(WAITING, emptyList()), IDLE.onPermissionAnswer(PRECISE, locationOn = true, dialogAvailable = true))
    }

    @ParameterizedTest
    @EnumSource(LocationButtonState::class)
    fun `a tap with location switched off shows the notice and goes idle`(state: LocationButtonState) {
        assertEquals(LocationStep(IDLE, listOf(LocationAction.Notice(SWITCHED_OFF))), state.onTap(PRECISE, locationOn = false))
    }

    @Test
    fun `a tap when ready centres`() {
        assertEquals(LocationStep(READY, listOf(Centre)), READY.onTap(PRECISE, locationOn = true))
    }

    @Test
    fun `a tap when idle starts waiting`() {
        assertEquals(LocationStep(WAITING, emptyList()), IDLE.onTap(PRECISE, locationOn = true))
    }

    @Test
    fun `a tap while waiting stops waiting`() {
        assertEquals(LocationStep(IDLE, emptyList()), WAITING.onTap(PRECISE, locationOn = true))
    }

    @Test
    fun `a fresh position makes idle and waiting ready`() {
        assertEquals(READY, IDLE.onStale(false))
        assertEquals(READY, WAITING.onStale(false))
        assertEquals(READY, READY.onStale(false))
    }

    @Test
    fun `a stale position makes ready idle`() {
        assertEquals(IDLE, READY.onStale(true))
        assertEquals(IDLE, IDLE.onStale(true))
    }

    @Test
    fun `a stale position while waiting keeps waiting`() {
        assertEquals(WAITING, WAITING.onStale(true))
    }

    @Test
    fun `with approximate access a tap when ready centres and shows the approximate notice`() {
        assertEquals(
            LocationStep(READY, listOf(Centre, LocationAction.Notice(APPROXIMATE_ONLY))),
            READY.onTap(APPROXIMATE, locationOn = true),
        )
    }

    @Test
    fun `with approximate access a tap when idle waits and shows the approximate notice`() {
        assertEquals(
            LocationStep(WAITING, listOf(LocationAction.Notice(APPROXIMATE_ONLY))),
            IDLE.onTap(APPROXIMATE, locationOn = true),
        )
    }
}
