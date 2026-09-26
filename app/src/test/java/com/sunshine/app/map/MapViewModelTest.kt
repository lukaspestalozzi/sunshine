package com.sunshine.app.map

import androidx.lifecycle.SavedStateHandle
import com.sunshine.core.DEFAULT_LOCATION
import com.sunshine.core.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {
    private val isOnline = MutableStateFlow(true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `starts at the default location with zoom 10`() {
        val viewModel = MapViewModel(SavedStateHandle(), isOnline)

        assertEquals(CameraState(center = DEFAULT_LOCATION, zoom = 10.0), viewModel.camera.value)
    }

    @Test
    fun `a moved camera is restored from saved state, as after a rotation`() {
        val savedState = SavedStateHandle()
        val moved = CameraState(center = GeoPoint(46.6863, 7.8632), zoom = 13.5)
        MapViewModel(savedState, isOnline).onCameraMoved(moved)

        val recreated = MapViewModel(savedState, isOnline)

        assertEquals(moved, recreated.camera.value)
    }

    @Test
    fun `isOffline follows the network monitor`() =
        runTest {
            val viewModel = MapViewModel(SavedStateHandle(), isOnline)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.isOffline.collect {} }

            isOnline.value = false
            assertTrue(viewModel.isOffline.value)

            isOnline.value = true
            assertFalse(viewModel.isOffline.value)
        }
}
