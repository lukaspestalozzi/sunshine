package com.sunshine.app.settings

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir

// The Settings page's clearing and the Custom resolution draft (design D3, D9 of add-settings).
@OptIn(ExperimentalCoroutinesApi::class)
@Timeout(value = 10, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class SettingsViewModelTest {
    @TempDir
    lateinit var directory: File

    private val job = Job()

    // Cleared before Main is reset, so that no view model coroutine resumes on Main afterwards
    // (a DataStore write resuming late failed a later test, 2026-10-04).
    private val viewModels = ViewModelStore()

    private fun viewModel(clearBrowsed: suspend () -> Boolean = { true }): SettingsViewModel =
        ViewModelProvider.create(
            viewModels,
            viewModelFactory { initializer { SettingsViewModel(store, clearBrowsed = clearBrowsed) } },
        )[SettingsViewModel::class.java.name + clearBrowsed.hashCode(), SettingsViewModel::class]

    private val store by lazy { SettingsStore.open(File(directory, "settings.preferences_pb"), CoroutineScope(Dispatchers.IO + job)) }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() =
        runBlocking {
            viewModels.clear()
            Dispatchers.resetMain()
            job.cancelAndJoin()
        }

    @Test
    fun `clearing reports whether every browsed tile was removed`() {
        val results = mutableListOf<Boolean>()

        viewModel(clearBrowsed = { true }).onClearBrowsed { results += it }
        viewModel(clearBrowsed = { false }).onClearBrowsed { results += it }

        assertEquals(listOf(true, false), results)
    }

    @Test
    fun `the custom draft survives the page being recreated and is stored only when the page is left`() =
        runBlocking {
            val viewModel = viewModel()
            viewModel.onCustomOpened()
            viewModel.onCustomEdited(Resolution(3, 10, 8, 10))

            // A rotation recreates the page, which opens it again.
            viewModel.onCustomOpened()

            assertEquals(Resolution(3, 10, 8, 10), viewModel.customDraft.value)
            assertEquals(Resolution.NORMAL, store.settings.value.custom)

            viewModel.onCustomClosed()

            assertEquals(Resolution(3, 10, 8, 10), store.settings.first { it.custom != Resolution.NORMAL }.custom)
            assertNull(viewModel.customDraft.value)
        }

    @Test
    fun `a debug switch is stored`() =
        runBlocking {
            viewModel().onDebug(DebugSwitches(tiles = true))

            assertEquals(DebugSwitches(tiles = true), store.settings.first { it.debug.tiles }.debug)
        }

    @Test
    fun `opening the page starts the draft from the stored values`() {
        val viewModel = viewModel()

        viewModel.onCustomOpened()

        assertEquals(Resolution.NORMAL, viewModel.customDraft.value)
    }
}
